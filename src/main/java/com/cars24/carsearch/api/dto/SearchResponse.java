package com.cars24.carsearch.api.dto;

import com.cars24.carsearch.search.SearchResult;
import com.cars24.carsearch.search.VehicleSummary;

import java.util.List;

/**
 * The search response envelope.
 *
 * @param query          echoed back, so a cached or logged response is self-contained
 * @param interpretation what was applied, and what was asked for and ignored
 * @param page           paging metadata
 * @param results        the matching cars
 */
public record SearchResponse(String query,
                             InterpretationView interpretation,
                             PageView page,
                             List<VehicleSummary> results) {

    public static SearchResponse from(String query, SearchResult result) {
        return new SearchResponse(
                query,
                InterpretationView.from(result.interpretation()),
                PageView.from(result.page()),
                result.page().getContent());
    }
}
