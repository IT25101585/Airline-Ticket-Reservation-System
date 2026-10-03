package com.skylanka.air.seat.service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory sliding-window throttle for the public seat-hold endpoint, so an anonymous client
 * cannot hammer it (or churn through sessions to lock many seats). Keyed by client address.
 * Single-instance only - a clustered deployment would move this to a shared store or a gateway.
 */
@Component
public class HoldRateLimiter {
    static final int MAX_REQUESTS = 30;
    static final long WINDOW_MILLIS = 60_000L;
    private static final int PRUNE_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public boolean allow(String key) {
        return allow(key, System.currentTimeMillis());
    }

    boolean allow(String key, long now) {
        if (hits.size() > PRUNE_THRESHOLD) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Long newest = e.getValue().peekLast();
                    return newest == null || now - newest > WINDOW_MILLIS;
                }
            });
        }
        Deque<Long> window = hits.computeIfAbsent(key == null ? "unknown" : key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= MAX_REQUESTS) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
