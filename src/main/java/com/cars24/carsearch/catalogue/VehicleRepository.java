package com.cars24.carsearch.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Catalogue access.
 *
 * <p>There are no query methods here on purpose. Every search this service runs is assembled at
 * runtime from a validated set of conditions, so the repository only needs to accept a
 * {@link org.springframework.data.jpa.domain.Specification} and page it. Derived query methods
 * would mean one method per shape of question, which is exactly what natural-language search
 * makes impossible to enumerate.
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {
}
