package ru.mcmon.plugin.core;

import java.util.Objects;
import java.util.Optional;

public record ConfirmedPlayer(PlayerId id, String nick, Optional<Ban> ban) {

    public ConfirmedPlayer {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nick);
        Objects.requireNonNull(ban);
    }
}
