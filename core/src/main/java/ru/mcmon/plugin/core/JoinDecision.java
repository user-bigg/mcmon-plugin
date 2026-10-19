package ru.mcmon.plugin.core;

import java.util.Objects;
import java.util.Optional;

public final class JoinDecision {

    public enum Kind {
        ALLOW("let in"),
        PASS("let in as usual"),
        DENY_BANNED("kept out, banned"),
        DENY_UNCONFIRMED("kept out, require-launcher is on");

        private final String description;

        Kind(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private final Kind kind;
    private final Optional<Ban> ban;

    private JoinDecision(Kind kind, Optional<Ban> ban) {
        this.kind = kind;
        this.ban = ban;
    }

    public static JoinDecision allow() {
        return new JoinDecision(Kind.ALLOW, Optional.empty());
    }

    public static JoinDecision pass() {
        return new JoinDecision(Kind.PASS, Optional.empty());
    }

    public static JoinDecision banned(Ban ban) {
        return new JoinDecision(Kind.DENY_BANNED, Optional.of(ban));
    }

    public static JoinDecision unconfirmed() {
        return new JoinDecision(Kind.DENY_UNCONFIRMED, Optional.empty());
    }

    public Kind kind() {
        return kind;
    }

    public Optional<Ban> ban() {
        return ban;
    }

    public boolean denied() {
        return kind == Kind.DENY_BANNED || kind == Kind.DENY_UNCONFIRMED;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JoinDecision that)) {
            return false;
        }
        return kind == that.kind && ban.equals(that.ban);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, ban);
    }

    @Override
    public String toString() {
        return "JoinDecision[kind=" + kind + ", ban=" + ban + "]";
    }
}
