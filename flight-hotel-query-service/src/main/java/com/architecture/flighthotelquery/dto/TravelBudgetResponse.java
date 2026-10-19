package com.architecture.flighthotelquery.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** Serialisable view of {@link TravelBudgetProjection}; this is what the L1/L2 caches store. */
public record TravelBudgetResponse(
        String sourceCode,
        String destinationCity,
        String flightNumber,
        String airlineName,
        BigDecimal flightFare,
        String recommendedHotel,
        String roomType,
        BigDecimal pricePerNight,
        int stayNights,
        BigDecimal totalHotelCost,
        long totalAttractionsPlanned,
        BigDecimal totalSightseeingCost,
        BigDecimal totalEstimatedTripBudget) implements Serializable {

    public static TravelBudgetResponse from(TravelBudgetProjection p, int stayNights) {
        return new TravelBudgetResponse(
            p.getSourceCode(), p.getDestinationCity(), p.getFlightNumber(), p.getAirlineName(),
            p.getFlightFare(), p.getRecommendedHotel(), p.getRoomType(), p.getPricePerNight(),
            stayNights, p.getTotalHotelCost(),
            p.getTotalAttractionsPlanned() == null ? 0L : p.getTotalAttractionsPlanned(),
            p.getTotalSightseeingCost(), p.getTotalEstimatedTripBudget());
    }
}
