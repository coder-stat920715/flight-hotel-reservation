package com.architecture.bookingcommand.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Attribute-Based Access Control, referenced from SpEL as {@code @abac.allow(authentication, #req.tenantId())}.
 * Evaluates the caller's tenant (JWT claim) against the requested tenant and checks that
 * the caller's userLocation claim is in the allowed set. ADMINs bypass the attribute check.
 */
@Component("abac")
public class AbacPermissionEvaluator {

    private final List<String> allowedLocations;

    public AbacPermissionEvaluator(@Value("${abac.allowed-locations:IN,TH,MY,VN,AE}") List<String> allowedLocations) {
        this.allowedLocations = allowedLocations.stream().map(String::trim).map(String::toUpperCase).toList();
    }

    public boolean allow(Authentication auth, String requestedTenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (admin) {
            return true;
        }
        String tenant = firstNonNull(jwt.getClaimAsString("tenant_id"), jwt.getClaimAsString("custom:tenant_id"));
        String location = firstNonNull(jwt.getClaimAsString("user_location"), jwt.getClaimAsString("custom:user_location"));
        return tenant != null && tenant.equals(requestedTenantId)
            && location != null && allowedLocations.contains(location.toUpperCase());
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
