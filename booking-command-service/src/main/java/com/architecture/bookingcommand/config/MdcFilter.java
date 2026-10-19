package com.architecture.bookingcommand.config;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Injects userId and tenantId into the SLF4J MDC (traceId/spanId are added by Micrometer Tracing). */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            String user = "anonymous";
            String tenant = req.getHeader("X-Tenant-Id");
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                user = jwt.getSubject();
                String t = jwt.getClaimAsString("tenant_id");
                if (t == null) {
                    t = jwt.getClaimAsString("custom:tenant_id");
                }
                if (t != null) {
                    tenant = t;
                }
            }
            MDC.put("userId", user);
            MDC.put("tenantId", tenant == null ? "unknown" : tenant);
            chain.doFilter(req, res);
        } finally {
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }
}
