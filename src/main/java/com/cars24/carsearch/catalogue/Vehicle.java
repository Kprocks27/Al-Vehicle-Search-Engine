package com.cars24.carsearch.catalogue;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;

/**
 * A single used-car listing in the catalogue.
 *
 * <p>Deliberately read-only: this service only ever queries inventory, so the entity exposes
 * getters and no setters. Hibernate populates fields directly (field access is implied by
 * annotating the id field). Listings are created by the procurement systems, not by us.
 *
 * <p>Everything a buyer can filter on is a column, with one exception: {@link #features} is a
 * tag set in a side table. Features are open-ended -- the marketing list grows every quarter --
 * and adding "ventilated seats" should not mean an ALTER TABLE on a wide, hot table.
 */
@Entity
@Table(
        name = "vehicles",
        indexes = {
                @Index(name = "idx_vehicles_price", columnList = "price"),
                @Index(name = "idx_vehicles_kilometres", columnList = "kilometres"),
                @Index(name = "idx_vehicles_body_type", columnList = "body_type"),
                @Index(name = "idx_vehicles_fuel_type", columnList = "fuel_type")
        })
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    /** Trim level, e.g. "SX(O)". Display only -- not searchable, see VehicleField. */
    @Column(nullable = false)
    private String variant;

    @Column(name = "model_year", nullable = false)
    private Integer year;

    /** Asking price in whole rupees. NUMERIC, not double: money never goes in a float. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Odometer reading in kilometres. */
    @Column(nullable = false)
    private Integer kilometres;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 16)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Transmission transmission;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", nullable = false, length = 16)
    private BodyType bodyType;

    @Column(nullable = false)
    private Integer seats;

    /** Number of previous owners, 1 = first owner. */
    @Column(name = "owner_count", nullable = false)
    private Integer ownerCount;

    /** Global NCAP crash test rating, 0-5 stars. */
    @Column(name = "safety_rating", nullable = false)
    private Integer safetyRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_grade", nullable = false, length = 16)
    private ConditionGrade conditionGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "accident_history", nullable = false, length = 16)
    private AccidentHistory accidentHistory;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String colour;

    @Column(nullable = false)
    private Integer airbags;

    /**
     * Feature tags, e.g. "sunroof". Lazy because most callers page through results and the
     * search predicates use an EXISTS subquery rather than a join, so we never need the
     * collection to filter. BatchSize collapses the per-row lookups we do need for the
     * response body into one extra query per page instead of one per vehicle; 50 matches the
     * maximum page size the API allows, so a full page never costs more than two round trips.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "vehicle_features", joinColumns = @JoinColumn(name = "vehicle_id"))
    @Column(name = "feature", nullable = false, length = 64)
    @BatchSize(size = 50)
    private Set<String> features;

    protected Vehicle() {
        // for Hibernate
    }

    public Long getId() {
        return id;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getVariant() {
        return variant;
    }

    public Integer getYear() {
        return year;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getKilometres() {
        return kilometres;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public Transmission getTransmission() {
        return transmission;
    }

    public BodyType getBodyType() {
        return bodyType;
    }

    public Integer getSeats() {
        return seats;
    }

    public Integer getOwnerCount() {
        return ownerCount;
    }

    public Integer getSafetyRating() {
        return safetyRating;
    }

    public ConditionGrade getConditionGrade() {
        return conditionGrade;
    }

    public AccidentHistory getAccidentHistory() {
        return accidentHistory;
    }

    public String getCity() {
        return city;
    }

    public String getColour() {
        return colour;
    }

    public Integer getAirbags() {
        return airbags;
    }

    public Set<String> getFeatures() {
        return features == null ? Collections.emptySet() : Collections.unmodifiableSet(features);
    }
}
