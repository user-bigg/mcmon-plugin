package ru.mcmon.plugin.core;

import java.time.Instant;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class JoinPolicy {

    private final BooleanSupplier requireLauncher;

    public JoinPolicy(BooleanSupplier requireLauncher) {
        this.requireLauncher = requireLauncher;
    }

    public JoinDecision decide(Lookup lookup, Optional<Ban> knownBan, Instant now) {
        Optional<Ban> activeKnownBan = knownBan.filter(ban -> ban.activeAt(now));
        return switch (lookup.outcome()) {
            case CONFIRMED ->
                lookup.player()
                        .get()
                        .ban()
                        .filter(ban -> ban.activeAt(now))
                        .map(JoinDecision::banned)
                        .orElseGet(JoinDecision::allow);
            case NOT_CONFIRMED, NO_CODE, INVALID_NICK ->
                activeKnownBan
                        .map(JoinDecision::banned)
                        .orElseGet(() ->
                                requireLauncher.getAsBoolean() ? JoinDecision.unconfirmed() : JoinDecision.pass());
            default -> activeKnownBan.map(JoinDecision::banned).orElseGet(JoinDecision::pass);
        };
    }
}
