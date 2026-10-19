package ru.mcmon.plugin.core;

import java.util.Objects;
import java.util.Optional;

public final class Lookup {

    private final LookupOutcome outcome;
    private final Optional<ConfirmedPlayer> player;

    private Lookup(LookupOutcome outcome, Optional<ConfirmedPlayer> player) {
        this.outcome = outcome;
        this.player = player;
    }

    public static Lookup confirmed(ConfirmedPlayer player) {
        return new Lookup(LookupOutcome.CONFIRMED, Optional.of(player));
    }

    public static Lookup of(LookupOutcome outcome) {
        if (outcome == LookupOutcome.CONFIRMED) {
            throw new IllegalArgumentException("confirmed lookup needs a player");
        }
        return new Lookup(outcome, Optional.empty());
    }

    public LookupOutcome outcome() {
        return outcome;
    }

    public Optional<ConfirmedPlayer> player() {
        return player;
    }

    public boolean confirmed() {
        return outcome == LookupOutcome.CONFIRMED;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Lookup that)) {
            return false;
        }
        return outcome == that.outcome && player.equals(that.player);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcome, player);
    }

    @Override
    public String toString() {
        return "Lookup[outcome=" + outcome + ", player=" + player + "]";
    }
}
