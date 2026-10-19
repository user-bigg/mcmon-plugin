package ru.mcmon.plugin.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record KnownPlayer(List<PlayerId> seen, Instant seenAt) {

    public static final int MAX_SEEN = 5;

    public KnownPlayer {
        seen = List.copyOf(seen);
        Objects.requireNonNull(seenAt);
    }

    public PlayerId lastSeen() {
        return seen.get(0);
    }

    public KnownPlayer withSeen(PlayerId id, Instant now) {
        List<PlayerId> updated = new ArrayList<>();
        updated.add(id);
        for (PlayerId previous : seen) {
            if (!previous.equals(id) && updated.size() < MAX_SEEN) {
                updated.add(previous);
            }
        }
        return new KnownPlayer(updated, now);
    }
}
