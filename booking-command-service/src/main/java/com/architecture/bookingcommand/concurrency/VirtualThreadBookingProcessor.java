package com.architecture.bookingcommand.concurrency;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Component;

import com.architecture.bookingcommand.dto.BookingResponse;
import com.architecture.bookingcommand.dto.CreateBookingRequest;
import com.architecture.bookingcommand.service.BookingCommandService;

import jakarta.annotation.PreDestroy;

/**
 * Runs booking commands on Java 21 virtual threads. Counters use atomics, and a bounded in-memory
 * status snapshot is guarded by a ReentrantReadWriteLock (many readers, rare writers).
 */
@Component
public class VirtualThreadBookingProcessor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final BookingCommandService service;

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicReference<Instant> lastProcessedAt = new AtomicReference<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<UUID, String> recentStatus = new LinkedHashMap<UUID, String>(1024, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
            return size() > 10_000;
        }
    };

    public VirtualThreadBookingProcessor(BookingCommandService service) {
        this.service = service;
    }

    public CompletableFuture<BookingResponse> submitCreate(CreateBookingRequest request, String userId) {
        submitted.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> service.createBooking(request, userId), executor)
            .whenComplete(this::record);
    }

    public CompletableFuture<BookingResponse> submitCancel(UUID bookingId, String userId, boolean admin) {
        submitted.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> service.cancelBooking(bookingId, userId, admin), executor)
            .whenComplete(this::record);
    }

    private void record(BookingResponse response, Throwable error) {
        if (error != null) {
            failed.incrementAndGet();
            return;
        }
        succeeded.incrementAndGet();
        lastProcessedAt.set(Instant.now());
        lock.writeLock().lock();
        try {
            recentStatus.put(response.bookingId(), response.status());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<String> recentStatus(UUID bookingId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(recentStatus.get(bookingId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("submitted", submitted.get());
        m.put("succeeded", succeeded.get());
        m.put("failed", failed.get());
        m.put("lastProcessedAt", lastProcessedAt.get());
        return m;
    }

    @PreDestroy
    void shutdown() {
        executor.close();
    }
}
