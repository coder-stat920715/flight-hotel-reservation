package com.architecture.gateway.config;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.time.Duration;

import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import reactor.core.publisher.Mono;

/**
 * Rate limiting uses Spring Cloud Gateway's Redis-backed RequestRateLimiter, which implements the
 * Token Bucket algorithm (replenishRate / burstCapacity / requestedTokens, see application.yml).
 * Resilience4j is used for the gateway circuit breakers + time limiters.
 */
@Configuration
public class RateLimiterConfig {

    /** One bucket per authenticated subject, falling back to X-Forwarded-For / remote address. */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(Principal::getName)
            .switchIfEmpty(Mono.fromSupplier(() -> {
                String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    return xff.split(",")[0].trim();
                }
                InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
                return addr == null ? "anonymous" : addr.getAddress().getHostAddress();
            }));
    }

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowSize(50)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .build())
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build())
            .build());
    }
}
