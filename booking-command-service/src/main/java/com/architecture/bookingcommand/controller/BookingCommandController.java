package com.architecture.bookingcommand.controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.architecture.bookingcommand.concurrency.VirtualThreadBookingProcessor;
import com.architecture.bookingcommand.dto.BookingResponse;
import com.architecture.bookingcommand.dto.CreateBookingRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingCommandController {

    private final VirtualThreadBookingProcessor processor;

    public BookingCommandController(VirtualThreadBookingProcessor processor) {
        this.processor = processor;
    }

    /** RBAC (role) + ABAC (tenant + userLocation claims) evaluated before the command is accepted. */
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN') and @abac.allow(authentication, #request.tenantId())")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        BookingResponse body = await(processor.submitCreate(request, jwt.getSubject()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{bookingId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<BookingResponse> cancel(@PathVariable UUID bookingId,
                                                  @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        boolean admin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return ResponseEntity.ok(await(processor.submitCancel(bookingId, jwt.getSubject(), admin)));
    }

    @GetMapping("/processor-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> stats() {
        return processor.stats();
    }

    // Tomcat already runs request threads as virtual threads (spring.threads.virtual.enabled),
    // so blocking on the future here is cheap and keeps the security context intact.
    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
