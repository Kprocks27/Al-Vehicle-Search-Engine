package com.cars24.carsearch.search;

import org.springframework.data.domain.Page;

/**
 * A search, and the full account of how the sentence was read.
 *
 * <p>The interpretation travels back alongside the results deliberately. Natural-language search
 * fails in a way keyword search does not: it can misunderstand you and still return a confident
 * page of cars. Now that unusable conditions are dropped rather than fatal, that matters more, not
 * less -- a shopper who asked for four things and got three has results that look perfectly
 * normal, and the only thing standing between them and a wrong answer is the service saying which
 * of the four it ignored.
 */
public record SearchResult(Interpretation interpretation, Page<VehicleSummary> page) {
}
