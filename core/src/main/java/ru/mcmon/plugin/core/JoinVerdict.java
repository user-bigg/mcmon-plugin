package ru.mcmon.plugin.core;

import java.util.Objects;
import java.util.Optional;

public record JoinVerdict(Lookup lookup, JoinDecision decision, Optional<String> kickMessage) {

    public JoinVerdict {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(kickMessage);
    }

    public boolean denied() {
        return decision.denied();
    }
}
