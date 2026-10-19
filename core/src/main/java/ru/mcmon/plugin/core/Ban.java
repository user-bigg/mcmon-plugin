package ru.mcmon.plugin.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record Ban(String reason, Optional<Instant> until) {

    public static final int MAX_REASON_LENGTH = 200;

    public Ban {
        reason = PlainText.sanitize(reason, MAX_REASON_LENGTH);
        Objects.requireNonNull(until);
    }

    public boolean permanent() {
        return until.isEmpty();
    }

    public boolean activeAt(Instant now) {
        return until.map(end -> end.isAfter(now)).orElse(true);
    }
}
