package com.architecture.flighthotelquery.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.architecture.flighthotelquery.dto.TravelBudgetResponse;
import com.architecture.flighthotelquery.service.TravelQueryService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/travel")
@PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
public class TravelQueryController {

    private final TravelQueryService service;

    public TravelQueryController(TravelQueryService service) {
        this.service = service;
    }

    /** GET /api/v1/travel/estimate-budget?source=CCU&destination=BKK&nights=4 */
    @GetMapping("/estimate-budget")
    public ResponseEntity<TravelBudgetResponse> estimateBudget(
            @RequestParam("source") @Pattern(regexp = "[A-Za-z]{3}") String source,
            @RequestParam("destination") @Pattern(regexp = "[A-Za-z]{3}") String destination,
            @RequestParam(value = "nights", defaultValue = "3") @Min(1) @Max(60) int nights) {
        TravelBudgetResponse result =
            service.estimateMinimumBudget(source.toUpperCase(), destination.toUpperCase(), nights);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/flights")
    public List<Map<String, Object>> flights(
            @RequestParam("source") @Pattern(regexp = "[A-Za-z]{3}") String source,
            @RequestParam("destination") @Pattern(regexp = "[A-Za-z]{3}") String destination) {
        return service.searchFlights(source.toUpperCase(), destination.toUpperCase());
    }

    @GetMapping("/hotels")
    public List<Map<String, Object>> hotels(@RequestParam("city") String city) {
        return service.findHotels(city);
    }

    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<Map<String, Object>> booking(@PathVariable UUID bookingId) {
        return service.findBooking(bookingId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
