package com.cars24.carsearch.api;

import com.cars24.carsearch.api.dto.SearchResponse;
import com.cars24.carsearch.search.SearchResult;
import com.cars24.carsearch.search.VehicleSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint.
 *
 * <p>A GET, because this is a read with no side effects: it is cacheable by intermediaries,
 * retryable, and a shopper can bookmark or share the URL. The obvious objection is that a long
 * sentence has to be URL-encoded; capping it at 300 characters keeps it well inside every
 * practical URL limit, and no genuine car search runs longer than that.
 *
 * <p>The controller does three things and no more: bind and bound-check the request, call the
 * service, shape the response. Every decision about what the sentence means happens below it.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class SearchController {

    private static final int MAX_PAGE_SIZE = 50;

    private final VehicleSearchService searchService;

    public SearchController(VehicleSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam("q")
            @NotBlank(message = "query must not be blank")
            @Size(max = 300, message = "query must be at most 300 characters")
            String query,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "size must be at most " + MAX_PAGE_SIZE)
            int size) {

        // Cheapest first. Price is the axis shoppers actually sort a used-car list by, and a
        // fixed order keeps paging stable -- an unordered page 2 can repeat rows from page 1.
        SearchResult result = searchService.search(
                query, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "price", "id")));

        return SearchResponse.from(query, result);
    }
}
