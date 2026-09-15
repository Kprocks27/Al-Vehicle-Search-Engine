package com.cars24.carsearch.search;

import com.cars24.carsearch.catalogue.Vehicle;
import com.cars24.carsearch.vocabulary.Comparison;
import com.cars24.carsearch.vocabulary.VehicleField;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns validated criteria into one parameterised query.
 *
 * <p>No string ever becomes SQL here. Column names are resolved through the Criteria API from a
 * {@link VehicleField} enum constant, and values become JDBC bind parameters. Even if the
 * validator were bypassed and a field name of {@code "price; DROP TABLE vehicles"} arrived, there
 * is no concatenation for it to reach -- {@code root.get(...)} would fail to resolve an
 * attribute. The safety is structural rather than a matter of remembering to escape.
 *
 * <p>Filters and features are all ANDed. Features use {@code isMember}, which Hibernate renders
 * as a correlated subquery against the tag table, one per tag:
 * {@code ? in (select feature from vehicle_features where vehicle_id = v.id)}. The alternative --
 * joining the tag table once per feature -- multiplies rows and forces a DISTINCT, which breaks
 * accurate paging. A subquery keeps one row per vehicle no matter how many tags are asked for.
 */
@Component
public class VehicleSpecificationBuilder {

    public Specification<Vehicle> build(SearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (FieldCriterion criterion : criteria.filters()) {
                predicates.add(toPredicate(criterion, root, cb));
            }
            for (String feature : criteria.features()) {
                predicates.add(cb.isMember(feature, root.<Set<String>>get("features")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate toPredicate(FieldCriterion criterion, Root<Vehicle> root, CriteriaBuilder cb) {
        return switch (criterion.field().valueType()) {
            case INTEGER, MONEY -> numeric(criterion, root, cb);
            case TEXT -> text(criterion, root, cb);
            case ENUM -> cb.equal(root.get(criterion.field().attribute()), exactValue(criterion));
        };
    }

    /**
     * BETWEEN is built as two bounds rather than {@code cb.between}. It reads the same in SQL,
     * it keeps every bound in this class inclusive by construction, and it sidesteps the raw
     * Comparable casting that {@code between} would otherwise force on us -- both ordered value
     * types are Numbers, and the Number overloads of ge/le are type-safe.
     */
    private Predicate numeric(FieldCriterion criterion, Root<Vehicle> root, CriteriaBuilder cb) {
        Path<Number> path = root.get(criterion.field().attribute());
        return switch (criterion.comparison()) {
            case UNDER -> cb.le(path, (Number) criterion.single());
            case OVER -> cb.ge(path, (Number) criterion.single());
            case EQUALS -> cb.equal(path, criterion.single());
            case BETWEEN -> cb.and(
                    cb.ge(path, (Number) criterion.lower()),
                    cb.le(path, (Number) criterion.upper()));
        };
    }

    /**
     * Text matches ignore case: these values come out of a sentence, where "bengaluru" and
     * "Bengaluru" are the same city. LOWER() on the column does cost the plain index -- at
     * catalogue scale the right fix is a functional index or a normalised lookup column, which
     * is a change to make against a real query plan rather than on a hunch.
     */
    private Predicate text(FieldCriterion criterion, Root<Vehicle> root, CriteriaBuilder cb) {
        Path<String> path = root.get(criterion.field().attribute());
        String value = (String) exactValue(criterion);
        return cb.equal(cb.lower(path), value.toLowerCase(Locale.ROOT));
    }

    /**
     * Defence in depth. The validator already guarantees that unordered fields only carry EQUALS;
     * this makes the assumption explicit so that adding an ordered text field later fails loudly
     * here instead of quietly building the wrong predicate.
     */
    private Object exactValue(FieldCriterion criterion) {
        if (criterion.comparison() != Comparison.EQUALS) {
            throw new IllegalStateException(
                    "validation should have rejected " + criterion.comparison()
                            + " on unordered field " + criterion.field().wireName());
        }
        return criterion.single();
    }
}
