package ru.mcmon.plugin.core;

import java.util.Objects;
import java.util.Optional;

public final class BansFetch {

    private final Optional<BanList> list;
    private final LookupOutcome failure;

    private BansFetch(Optional<BanList> list, LookupOutcome failure) {
        this.list = list;
        this.failure = failure;
    }

    public static BansFetch ok(BanList list) {
        return new BansFetch(Optional.of(list), LookupOutcome.CONFIRMED);
    }

    public static BansFetch failed(LookupOutcome failure) {
        return new BansFetch(Optional.empty(), failure);
    }

    public Optional<BanList> list() {
        return list;
    }

    public LookupOutcome failure() {
        return failure;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BansFetch that)) {
            return false;
        }
        return list.equals(that.list) && failure == that.failure;
    }

    @Override
    public int hashCode() {
        return Objects.hash(list, failure);
    }

    @Override
    public String toString() {
        return "BansFetch[list=" + list + ", failure=" + failure + "]";
    }
}
