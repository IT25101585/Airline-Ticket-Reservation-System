package com.skylanka.air.seat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoldRateLimiterTest {

    @Test
    void allowsUpToTheLimitThenBlocksWithinTheWindow() {
        var limiter = new HoldRateLimiter();
        long now = 1_000_000L;
        for (int i = 0; i < HoldRateLimiter.MAX_REQUESTS; i++) {
            assertTrue(limiter.allow("10.0.0.1", now + i), "request " + i + " should be allowed");
        }
        assertFalse(limiter.allow("10.0.0.1", now + HoldRateLimiter.MAX_REQUESTS));
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        var limiter = new HoldRateLimiter();
        long now = 1_000_000L;
        for (int i = 0; i < HoldRateLimiter.MAX_REQUESTS; i++) {
            limiter.allow("10.0.0.1", now);
        }
        assertFalse(limiter.allow("10.0.0.1", now + 1));
        assertTrue(limiter.allow("10.0.0.1", now + HoldRateLimiter.WINDOW_MILLIS + 1));
    }

    @Test
    void clientsAreThrottledIndependently() {
        var limiter = new HoldRateLimiter();
        long now = 1_000_000L;
        for (int i = 0; i < HoldRateLimiter.MAX_REQUESTS; i++) {
            limiter.allow("10.0.0.1", now);
        }
        assertFalse(limiter.allow("10.0.0.1", now));
        assertTrue(limiter.allow("10.0.0.2", now));
    }
}
