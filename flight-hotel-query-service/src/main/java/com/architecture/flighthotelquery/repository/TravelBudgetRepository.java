package com.architecture.flighthotelquery.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.architecture.flighthotelquery.domain.Airport;
import com.architecture.flighthotelquery.dto.TravelBudgetProjection;

public interface TravelBudgetRepository extends Repository<Airport, Long> {

    /**
     * Minimum Estimated Budget = cheapest flight + cheapest available hotel x nights + all sightseeing fees.
     * NOTE: unlike a bare CROSS JOIN, the cheapest hotel is joined on the flight's DESTINATION city
     * (otherwise a hotel from any city could be paired with the flight).
     */
    @Query(value = """
        WITH CheapestFlight AS (
            SELECT f.flight_id, f.flight_number, al.airline_name,
                   src.airport_code AS source_code, dest.airport_code AS dest_code,
                   dest_city.city_id AS dest_city_id, dest_city.city_name AS destination_city, ff.total_fare,
                   ROW_NUMBER() OVER (PARTITION BY f.source_airport_id, f.destination_airport_id
                                      ORDER BY ff.total_fare ASC) AS rank_num
            FROM flights f
            JOIN airlines al ON f.airline_id = al.airline_id
            JOIN airports src ON f.source_airport_id = src.airport_id
            JOIN airports dest ON f.destination_airport_id = dest.airport_id
            JOIN cities dest_city ON dest.city_id = dest_city.city_id
            JOIN flight_fares ff ON f.flight_id = ff.flight_id
            WHERE src.airport_code = :sourceCode AND dest.airport_code = :destCode
        ),
        CheapestHotel AS (
            SELECT h.city_id, h.hotel_name, hp.room_type, hp.price_per_night,
                   ROW_NUMBER() OVER (PARTITION BY h.city_id ORDER BY hp.price_per_night ASC) AS rank_num
            FROM hotels h
            JOIN hotel_pricing hp ON h.hotel_id = hp.hotel_id
            WHERE hp.is_available = TRUE
        ),
        TotalSightseeingCost AS (
            SELECT city_id, COUNT(attraction_id) AS total_places, SUM(entry_fee_per_person) AS total_entry_fee
            FROM attractions GROUP BY city_id
        )
        SELECT cf.source_code AS "sourceCode",
               cf.destination_city AS "destinationCity",
               cf.flight_number AS "flightNumber",
               cf.airline_name AS "airlineName",
               cf.total_fare AS "flightFare",
               ch.hotel_name AS "recommendedHotel",
               ch.room_type AS "roomType",
               ch.price_per_night AS "pricePerNight",
               (ch.price_per_night * CAST(:stayNights AS INTEGER)) AS "totalHotelCost",
               COALESCE(ts.total_places, 0) AS "totalAttractionsPlanned",
               COALESCE(ts.total_entry_fee, 0.00) AS "totalSightseeingCost",
               (cf.total_fare + (ch.price_per_night * CAST(:stayNights AS INTEGER))
                   + COALESCE(ts.total_entry_fee, 0.00)) AS "totalEstimatedTripBudget"
        FROM CheapestFlight cf
        JOIN CheapestHotel ch ON ch.city_id = cf.dest_city_id AND ch.rank_num = 1
        LEFT JOIN TotalSightseeingCost ts ON ts.city_id = cf.dest_city_id
        WHERE cf.rank_num = 1
        LIMIT 1
        """, nativeQuery = true)
    Optional<TravelBudgetProjection> findMinimumBudget(@Param("sourceCode") String sourceCode,
                                                       @Param("destCode") String destCode,
                                                       @Param("stayNights") int stayNights);
}
