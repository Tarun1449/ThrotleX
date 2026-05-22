package com.throttlex.cache.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for adding TTL jitter to cache entries.
 *
 * Why:
 * If many cache keys expire at the exact same time, traffic can suddenly fall back
 * to the database. This is called cache avalanche.
 *
 * Important:
 * Calling this once in RedisCacheConfiguration.entryTtl(...) creates one randomized
 * TTL for the whole cache configuration at startup.
 *
 * For strongest protection, call this per cache write:
 *
 * redisTemplate.opsForValue().set(key, value, CacheTtlUtils.withJitter(BASE_TTL));
 */
public final class CacheTtlUtils {

    private static final double DEFAULT_JITTER_PERCENT = 0.20;
    private static final double SMALL_JITTER_PERCENT = 0.10;
    private static final double LARGE_JITTER_PERCENT = 0.30;

    private static final Duration MIN_TTL = Duration.ofSeconds(1);

    private CacheTtlUtils() {
    }

    public static Duration withJitter(Duration baseTtl) {
        return withJitter(baseTtl, DEFAULT_JITTER_PERCENT);
    }

    public static Duration withSmallJitter(Duration baseTtl) {
        return withJitter(baseTtl, SMALL_JITTER_PERCENT);
    }

    public static Duration withLargeJitter(Duration baseTtl) {
        return withJitter(baseTtl, LARGE_JITTER_PERCENT);
    }

    public static Duration positiveCacheTtl(Duration baseTtl) {
        return withJitter(baseTtl, DEFAULT_JITTER_PERCENT);
    }

    public static Duration negativeCacheTtl(Duration baseTtl) {
        return withSmallJitter(baseTtl);
    }

    public static Duration withJitter(Duration baseTtl, double jitterPercent) {
        validate(baseTtl, jitterPercent);

        long baseMillis = Math.max(baseTtl.toMillis(), MIN_TTL.toMillis());
        long jitterMillis = (long) (baseMillis * jitterPercent);

        if (jitterMillis <= 0) {
            return Duration.ofMillis(baseMillis);
        }

        long randomDelta = ThreadLocalRandom.current()
                .nextLong(-jitterMillis, jitterMillis + 1);

        long finalMillis = Math.max(baseMillis + randomDelta, MIN_TTL.toMillis());

        return Duration.ofMillis(finalMillis);
    }

    private static void validate(Duration baseTtl, double jitterPercent) {
        Objects.requireNonNull(baseTtl, "baseTtl must not be null");

        if (baseTtl.isZero() || baseTtl.isNegative()) {
            throw new IllegalArgumentException("baseTtl must be positive");
        }

        if (Double.isNaN(jitterPercent) || jitterPercent < 0.0 || jitterPercent > 1.0) {
            throw new IllegalArgumentException("jitterPercent must be between 0.0 and 1.0");
        }
    }
}