package com.architecture.payment.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.architecture.payment.dto.PaymentResult;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayClient.class);

    private final RestClient restClient;
    private final boolean simulate;

    public PaymentGatewayClient(RestClient restClient, @Value("${payment.gateway.simulate:true}") boolean simulate) {
        this.restClient = restClient;
        this.simulate = simulate;
    }

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "chargeFallback")
    @Retry(name = "paymentGateway")
    public PaymentResult charge(UUID bookingId, BigDecimal amount) {
        if (simulate) {
            return PaymentResult.approved("SIM-" + bookingId);
        }
        PaymentResult result = restClient.post()
            .uri("/v1/charges")
            .body(Map.of("reference", bookingId.toString(), "amount", amount))
            .retrieve()
            .body(PaymentResult.class);
        return result == null ? PaymentResult.declined("EMPTY_RESPONSE") : result;
    }

    /** Fallback when retries are exhausted or the circuit is open: outcome unknown -> DEFERRED. */
    PaymentResult chargeFallback(UUID bookingId, BigDecimal amount, Throwable t) {
        log.warn("Payment gateway fallback for booking {}: {}", bookingId, t.toString());
        return PaymentResult.deferred(t.getClass().getSimpleName());
    }
}
