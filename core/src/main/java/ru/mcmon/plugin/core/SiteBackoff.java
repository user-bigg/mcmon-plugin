package ru.mcmon.plugin.core;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;

public final class SiteBackoff {

    static final int FAILURES_BEFORE_PAUSE = 3;
    static final Duration FAILURE_PAUSE = Duration.ofSeconds(30);
    static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(10);
    static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(5);

    private final LongSupplier nanoTime;
    private final Object lock = new Object();
    private long pausedUntil;
    private LookupOutcome pausedOutcome;
    private int failures;

    public SiteBackoff() {
        this(System::nanoTime);
    }

    SiteBackoff(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public Optional<LookupOutcome> paused() {
        synchronized (lock) {
            if (pausedOutcome != null && nanoTime.getAsLong() - pausedUntil < 0) {
                return Optional.of(pausedOutcome);
            }
            pausedOutcome = null;
            return Optional.empty();
        }
    }

    public Lookup record(Lookup lookup, Optional<Duration> retryAfter) {
        synchronized (lock) {
            if (lookup.outcome() == LookupOutcome.RATE_LIMITED) {
                pause(LookupOutcome.RATE_LIMITED, clamp(retryAfter.orElse(DEFAULT_RETRY_AFTER)));
            } else if (lookup.outcome().siteFailure()) {
                failures++;
                if (failures >= FAILURES_BEFORE_PAUSE) {
                    pause(lookup.outcome(), FAILURE_PAUSE);
                }
            } else {
                failures = 0;
            }
        }
        return lookup;
    }

    private void pause(LookupOutcome outcome, Duration duration) {
        pausedOutcome = outcome;
        pausedUntil = nanoTime.getAsLong() + duration.toNanos();
        failures = 0;
    }

    private static Duration clamp(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return DEFAULT_RETRY_AFTER;
        }
        return duration.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : duration;
    }
}
