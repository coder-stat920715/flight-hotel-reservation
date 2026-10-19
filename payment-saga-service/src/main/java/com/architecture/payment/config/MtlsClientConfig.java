package com.architecture.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.client.RestClientSsl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/** Outbound HTTP client for the payment gateway. With the "mtls" profile it presents the pod's client certificate. */
@Configuration
public class MtlsClientConfig {

    @Bean
    @Profile("mtls")
    RestClient paymentRestClientMtls(RestClient.Builder builder, RestClientSsl ssl,
                                     @Value("${payment.gateway.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).apply(ssl.fromBundle("mtls")).build();
    }

    @Bean
    @Profile("!mtls")
    RestClient paymentRestClientPlain(RestClient.Builder builder, @Value("${payment.gateway.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
