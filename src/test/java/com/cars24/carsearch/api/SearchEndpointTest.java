package com.cars24.carsearch.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over the real pipeline and the real seed data: HTTP in, parsed, validated, queried,
 * JSON out. The stub parser is what makes this runnable in CI with no API key.
 *
 * <p>Assertions are about properties, not counts. An exact count was a useful regression check
 * when the catalogue was 47 hand-written rows; at 186 generated ones it only records whatever the
 * generator last produced, and any catalogue tweak breaks every test for no real reason. What
 * matters is that every returned car actually satisfies the query and that something came back at
 * all -- a query returning the right number of wrong cars still fails here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Show SUVs under 15L")
    void filtersOnBodyTypeAndPrice() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "Show SUVs under 15L").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                .andExpect(jsonPath("$.results[*].bodyType", everyItem(is("SUV"))))
                .andExpect(jsonPath("$.results[*].price", everyItem(lessThanOrEqualTo(1500000.0))))
                // The interpretation is part of the contract: a shopper has to be able to see
                // what the sentence was read as.
                .andExpect(jsonPath("$.interpretation.filters", hasSize(2)))
                .andExpect(jsonPath("$.interpretation.filters[?(@.field == 'price')].comparison",
                        hasItem("UNDER")));
    }

    @Test
    @DisplayName("Diesel automatic cars below 80k km")
    void combinesThreeConditions() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "Diesel automatic cars below 80k km").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                .andExpect(jsonPath("$.results[*].fuelType", everyItem(is("DIESEL"))))
                .andExpect(jsonPath("$.results[*].transmission", everyItem(is("AUTOMATIC"))))
                .andExpect(jsonPath("$.results[*].kilometres", everyItem(lessThanOrEqualTo(80000))));
    }

    @Test
    @DisplayName("Family cars with high safety ratings")
    void appliesAFuzzyThresholdAndAFeatureTag() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "Family cars with high safety ratings").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                .andExpect(jsonPath("$.results[*].seats", everyItem(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$.results[*].safetyRating", everyItem(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.results[*].features", everyItem(hasItem("isofix"))))
                // HIGH became >= 4 in our code, not in the parser, and the response says so.
                .andExpect(jsonPath("$.interpretation.filters[?(@.field == 'safetyRating')].comparison",
                        hasItem("OVER")))
                .andExpect(jsonPath("$.interpretation.filters[?(@.field == 'safetyRating')].values[0]",
                        hasItem("4")))
                .andExpect(jsonPath("$.interpretation.features", hasItem("isofix")));
    }

    @Test
    void andsFeatureTagsTogether() throws Exception {
        // Three seven-seat diesel SUVs have a 360 camera and cruise control; the Scorpio-N has
        // cruise control but no 360 camera and is correctly left out.
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "Seven seater diesel SUVs with a 360 degree camera and cruise control"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                .andExpect(jsonPath("$.results[*].features", everyItem(hasItem("camera_360"))))
                .andExpect(jsonPath("$.results[*].features", everyItem(hasItem("cruise_control"))));
    }

    @Test
    void appliesBothEndsOfARange() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "First owner cars between 5 and 10 lakh").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].ownerCount", everyItem(is(1))))
                .andExpect(jsonPath("$.results[*].price", everyItem(greaterThanOrEqualTo(500000.0))))
                .andExpect(jsonPath("$.results[*].price", everyItem(lessThanOrEqualTo(1000000.0))));
    }

    @Test
    void returnsResultsCheapestFirst() throws Exception {
        // Asserted as an ordering rather than a first price: the cheapest SUV in the catalogue is
        // a detail of the seed, but the order is the contract.
        String body = mockMvc.perform(get("/api/v1/search")
                        .param("q", "Show SUVs under 15L").param("size", "10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Double> prices = JsonPath.read(body, "$.results[*].price");
        assertThat(prices).hasSizeGreaterThan(1).isSorted();
    }

    @Test
    void rejectsQueriesItCannotInterpret() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "something nobody taught it"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title", is("Query not understood")));
    }

    @Test
    void runsTheUsablePartOfAQueryAndReportsTheRest() throws Exception {
        // The stub returns a filter on a column the catalogue does not have, exactly as a real
        // model does when asked about something we do not record. The other three conditions are
        // fine, so they run -- and the dropped one comes back named, never silently discarded.
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "Red SUVs with a panoramic sunroof and mileage over 20 kmpl"))
                .andExpect(status().isOk())
                // What this test is about is the drop, not the match count -- whether any red SUV
                // with a panoramic roof happens to be in stock is a property of the catalogue.
                .andExpect(jsonPath("$.results[*].colour", everyItem(is("Red"))))
                .andExpect(jsonPath("$.results[*].bodyType", everyItem(is("SUV"))))
                // What ran.
                .andExpect(jsonPath("$.interpretation.filters", hasSize(2)))
                .andExpect(jsonPath("$.interpretation.features", contains("panoramic_sunroof")))
                // What did not, and why.
                .andExpect(jsonPath("$.interpretation.ignoredFilters", hasSize(1)))
                .andExpect(jsonPath("$.interpretation.ignoredFilters[0].field", is("mileage")))
                .andExpect(jsonPath("$.interpretation.ignoredFilters[0].comparison", is("OVER")))
                .andExpect(jsonPath("$.interpretation.ignoredFilters[0].values[0]", is("20")))
                .andExpect(jsonPath("$.interpretation.ignoredFilters[0].reason",
                        is("unknown field 'mileage'")))
                .andExpect(jsonPath("$.interpretation.ignoredFeatures", hasSize(0)));
    }

    @Test
    void reportsEmptyIgnoredListsWhenNothingWasDropped() throws Exception {
        // Always present, never absent -- a client asking "was anything dropped" should not also
        // have to handle a missing key.
        mockMvc.perform(get("/api/v1/search").param("q", "Show SUVs under 15L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interpretation.ignoredFilters", hasSize(0)))
                .andExpect(jsonPath("$.interpretation.ignoredFeatures", hasSize(0)));
    }

    @Test
    void refusesAQueryWhereEverythingWasDropped() throws Exception {
        // Nothing usable survived, so there is no query to run -- but the 422 still has to say
        // what was thrown away, or the drop is silent in the case that matters most.
        mockMvc.perform(get("/api/v1/search").param("q", "Cars with mileage over 20 kmpl"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title", is("Query not understood")))
                .andExpect(jsonPath("$.ignoredFilters", hasSize(1)))
                .andExpect(jsonPath("$.ignoredFilters[0].reason", is("unknown field 'mileage'")));
    }

    @Test
    void reportsDroppedFiltersAndFeaturesInSeparateLists() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "Any colour except white with teleport mode"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.ignoredFilters", hasSize(1)))
                .andExpect(jsonPath("$.ignoredFilters[0].comparison", is("NOT_EQUALS")))
                .andExpect(jsonPath("$.ignoredFeatures", hasSize(1)))
                .andExpect(jsonPath("$.ignoredFeatures[0].tag", is("teleport_mode")))
                .andExpect(jsonPath("$.ignoredFeatures[0].reason", is("unknown feature tag")));
    }

    @Test
    void rejectsABlankQuery() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid request")));
    }

    @Test
    void rejectsAnOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "Show SUVs under 15L").param("size", "500"))
                .andExpect(status().isBadRequest());
    }
}
