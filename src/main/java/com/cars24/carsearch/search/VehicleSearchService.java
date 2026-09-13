package com.cars24.carsearch.search;

import com.cars24.carsearch.catalogue.VehicleRepository;
import com.cars24.carsearch.nlq.ParsedQuery;
import com.cars24.carsearch.nlq.QueryParser;
import com.cars24.carsearch.validation.NoSearchableConditionsException;
import com.cars24.carsearch.validation.ParsedQueryValidator;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pipeline, in four lines.
 *
 * <p>interpret -&gt; validate -&gt; build -&gt; execute. Each step hands the next a type it cannot
 * skip: the parser produces {@code ParsedQuery}, which only the validator accepts; the validator
 * produces an {@code Interpretation} whose {@code SearchCriteria} is the only thing the builder
 * accepts. The ordering is enforced by the types rather than by everyone remembering it.
 *
 * <p>Note what this class does not do. It does not know the query came in over HTTP, it does not
 * know an LLM is involved, and it does not know how results will be rendered.
 */
@Service
public class VehicleSearchService {

    private static final Logger log = LoggerFactory.getLogger(VehicleSearchService.class);

    private final QueryParser queryParser;
    private final ParsedQueryValidator validator;
    private final VehicleSpecificationBuilder specificationBuilder;
    private final VehicleRepository vehicleRepository;

    public VehicleSearchService(QueryParser queryParser,
                                ParsedQueryValidator validator,
                                VehicleSpecificationBuilder specificationBuilder,
                                VehicleRepository vehicleRepository) {
        this.queryParser = queryParser;
        this.validator = validator;
        this.specificationBuilder = specificationBuilder;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public SearchResult search(String query, Pageable pageable) {
        ParsedQuery parsed = queryParser.parse(query);

        Interpretation interpretation;
        try {
            interpretation = validator.validate(parsed);
        } catch (NoSearchableConditionsException e) {
            // The failure path drops conditions too, and it is the one that matters most: a parser
            // that broke every condition at once is exactly what the drift alarm exists to catch.
            // Without this, those drops are the only ones that never reach a log.
            logDrops(query, e.ignoredFilters(), e.ignoredFeatures());
            throw e;
        }

        logDrops(query, interpretation.ignoredFilters(), interpretation.ignoredFeatures());

        log.debug("Query '{}' resolved to {} filter(s) and {} feature(s)",
                query, interpretation.criteria().filters().size(),
                interpretation.criteria().features().size());

        // Mapping happens inside the transaction, so feature tags are loaded here rather than
        // lazily during serialisation.
        return new SearchResult(
                interpretation,
                vehicleRepository.findAll(specificationBuilder.build(interpretation.criteria()), pageable)
                        .map(VehicleSummary::from));
    }

    /**
     * Two streams, because these two things mean opposite kinds of thing.
     *
     * <p>An unsupported request is a shopper wanting something the catalogue does not hold. It is
     * expected, its baseline is whatever people happen to ask for, and read in aggregate it is a
     * product signal -- the list of what we keep being asked for and do not offer. INFO.
     *
     * <p>Parser drift is the model breaking its contract on a field it was handed the rules for.
     * Nothing a shopper types causes it, so its baseline is zero and any of it is actionable. WARN,
     * and this is the line worth alerting on now that dropped conditions return 200 instead of 502.
     */
    private void logDrops(String query,
                          List<DroppedFilter> ignoredFilters,
                          List<DroppedFeature> ignoredFeatures) {
        List<Object> unsupported = new ArrayList<>();
        List<Object> drift = new ArrayList<>();

        for (DroppedFilter dropped : ignoredFilters) {
            (dropped.category() == DropCategory.PARSER_DRIFT ? drift : unsupported).add(dropped);
        }
        for (DroppedFeature dropped : ignoredFeatures) {
            (dropped.category() == DropCategory.PARSER_DRIFT ? drift : unsupported).add(dropped);
        }

        if (!unsupported.isEmpty()) {
            log.info("Query '{}' asked for {} thing(s) the catalogue cannot express: {}",
                    query, unsupported.size(), unsupported);
        }
        if (!drift.isEmpty()) {
            log.warn("PARSER DRIFT on query '{}': {} condition(s) broke the contract on a known "
                    + "field: {}", query, drift.size(), drift);
        }
    }
}
