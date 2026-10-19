package com.architecture.flighthotelquery.dto;

import java.math.BigDecimal;

/**
 * Closed interface projection for the minimum-budget CTE query. Getter names map 1:1 to the
 * quoted camelCase column aliases in {@code TravelBudgetRepository}.
 */
public interface TravelBudgetProjection {
    String getSourceCode();
    String getDestinationCity();
    String getFlightNumber();
    String getAirlineName();
    BigDecimal getFlightFare();
    String getRecommendedHotel();
    String getRoomType();
    BigDecimal getPricePerNight();
    BigDecimal getTotalHotelCost();
    Long getTotalAttractionsPlanned();
    BigDecimal getTotalSightseeingCost();
    BigDecimal getTotalEstimatedTripBudget();
}
