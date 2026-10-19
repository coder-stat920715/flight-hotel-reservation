package com.architecture.flighthotelquery.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.architecture.flighthotelquery.config.TwoLevelCacheConfig;
import com.architecture.flighthotelquery.dto.TravelBudgetResponse;
import com.architecture.flighthotelquery.repository.TravelBudgetRepository;

@Service
@Transactional(readOnly = true)
public class TravelQueryService {

    private final TravelBudgetRepository budgetRepository;
    private final JdbcTemplate jdbc;

    public TravelQueryService(TravelBudgetRepository budgetRepository, JdbcTemplate jdbc) {
        this.budgetRepository = budgetRepository;
        this.jdbc = jdbc;
    }

    /** Cached in L1 (10 s) + L2 Redis (10 min). Null results are not cached. */
    @Cacheable(cacheNames = TwoLevelCacheConfig.BUDGET_CACHE,
               key = "#source + ':' + #dest + ':' + #nights",
               unless = "#result == null")
    public TravelBudgetResponse estimateMinimumBudget(String source, String dest, int nights) {
        return budgetRepository.findMinimumBudget(source, dest, nights)
            .map(p -> TravelBudgetResponse.from(p, nights))
            .orElse(null);
    }

    public List<Map<String, Object>> searchFlights(String source, String dest) {
        return jdbc.queryForList("""
            SELECT f.flight_number, al.airline_name, src.airport_code AS source_code,
                   dest.airport_code AS dest_code, f.flight_type, f.departure_time,
                   ff.fare_class, ff.total_fare, ff.departure_date, ff.seats_available
            FROM flights f
            JOIN airlines al ON al.airline_id = f.airline_id
            JOIN airports src ON src.airport_id = f.source_airport_id
            JOIN airports dest ON dest.airport_id = f.destination_airport_id
            JOIN flight_fares ff ON ff.flight_id = f.flight_id
            WHERE src.airport_code = ? AND dest.airport_code = ? AND ff.departure_date >= CURRENT_DATE
            ORDER BY ff.total_fare ASC
            LIMIT 50
            """, source, dest);
    }

    public List<Map<String, Object>> findHotels(String cityName) {
        return jdbc.queryForList("""
            SELECT h.hotel_name, h.star_rating, hp.room_type, hp.price_per_night, hp.currency
            FROM hotels h
            JOIN cities c ON c.city_id = h.city_id
            JOIN hotel_pricing hp ON hp.hotel_id = h.hotel_id
            WHERE c.city_name ILIKE ? AND hp.is_available = TRUE
            ORDER BY hp.price_per_night ASC
            LIMIT 50
            """, cityName);
    }

    public Optional<Map<String, Object>> findBooking(UUID bookingId) {
        return jdbc.queryForList("SELECT * FROM booking_read_model WHERE booking_id = ?", bookingId)
            .stream().findFirst();
    }
}
