package com.cars24.carsearch.search;

import com.cars24.carsearch.catalogue.Vehicle;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a search returns for one car.
 *
 * <p>A projection rather than the entity itself, so results can be assembled inside the read
 * transaction and handed out as immutable data. That is what lets {@code open-in-view} stay off:
 * nothing lazy escapes into the HTTP layer, so no rendering code can trigger a surprise query.
 *
 * <p>This doubles as the JSON shape. A separate API-layer view would be the textbook answer, but
 * today it would be a field-for-field copy with no differences to justify it. The moment the wire
 * format needs to diverge from what the application produces, that view goes in front of this.
 */
public record VehicleSummary(
        Long id,
        String make,
        String model,
        String variant,
        Integer year,
        BigDecimal price,
        Integer kilometres,
        String fuelType,
        String transmission,
        String bodyType,
        Integer seats,
        Integer ownerCount,
        Integer safetyRating,
        String conditionGrade,
        String accidentHistory,
        String city,
        String colour,
        Integer airbags,
        Set<String> features) {

    public static VehicleSummary from(Vehicle vehicle) {
        return new VehicleSummary(
                vehicle.getId(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getVariant(),
                vehicle.getYear(),
                vehicle.getPrice(),
                vehicle.getKilometres(),
                vehicle.getFuelType().name(),
                vehicle.getTransmission().name(),
                vehicle.getBodyType().name(),
                vehicle.getSeats(),
                vehicle.getOwnerCount(),
                vehicle.getSafetyRating(),
                vehicle.getConditionGrade().name(),
                vehicle.getAccidentHistory().name(),
                vehicle.getCity(),
                vehicle.getColour(),
                vehicle.getAirbags(),
                // Sorted so the payload is stable between calls; a Set from Hibernate has no
                // guaranteed order and unstable JSON makes responses awkward to diff in tests.
                new TreeSet<>(vehicle.getFeatures()));
    }
}
