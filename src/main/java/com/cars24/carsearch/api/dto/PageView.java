package com.cars24.carsearch.api.dto;

import org.springframework.data.domain.Page;

/**
 * Paging metadata, spelled out.
 *
 * <p>Spring's {@code Page} serialises to a sprawling object whose shape is tied to the version of
 * Spring Data on the classpath -- fine internally, not something to publish as an API contract.
 */
public record PageView(int number, int size, long totalElements, int totalPages) {

    public static PageView from(Page<?> page) {
        return new PageView(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
