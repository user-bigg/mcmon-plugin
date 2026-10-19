package ru.mcmon.plugin.core;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JoinGate {

    private final PlayerJoinCheck check;
    private final JoinPolicy policy;
    private final PlayerStore players;
    private final BanSync bans;
    private final Messages messages;
    private final Log log;
    private final Clock clock;

    public JoinGate(
            PlayerJoinCheck check, JoinPolicy policy, PlayerStore players, BanSync bans, Messages messages, Log log) {
        this(check, policy, players, bans, messages, log, Clock.systemUTC());
    }

    JoinGate(
            PlayerJoinCheck check,
            JoinPolicy policy,
            PlayerStore players,
            BanSync bans,
            Messages messages,
            Log log,
            Clock clock) {
        this.check = check;
        this.policy = policy;
        this.players = players;
        this.bans = bans;
        this.messages = messages;
        this.log = log;
        this.clock = clock;
    }

    public CompletableFuture<JoinVerdict> check(String nick, Supplier<CompletableFuture<CodeAnswer>> askCode) {
        return check.check(nick, askCode)
                .thenApply(lookup -> decide(nick, lookup))
                .exceptionally(this::failure);
    }

    public void check(String nick, Supplier<CompletableFuture<CodeAnswer>> askCode, Consumer<JoinVerdict> onVerdict) {
        // Network threads of proxies and mods cannot wait for the verdict; the chain already turns every failure
        // into a pass, so nothing is lost by dropping the derived future.
        CompletableFuture<JoinVerdict> unused = check(nick, askCode).whenComplete((verdict, error) -> {
            try {
                onVerdict.accept(verdict != null ? verdict : failure(error));
            } catch (RuntimeException | LinkageError e) {
                log.warn(nick + ": applying the join verdict failed ("
                        + e.getClass().getName() + ")");
            }
        });
    }

    public JoinVerdict await(String nick, Supplier<CompletableFuture<CodeAnswer>> askCode) {
        try {
            return check(nick, askCode).join();
        } catch (RuntimeException e) {
            return failure(e);
        }
    }

    private JoinVerdict decide(String nick, Lookup lookup) {
        try {
            lookup.player().ifPresent(player -> players.rememberSeen(nick, player.id()));
            JoinDecision decision = policy.decide(lookup, activeSavedBan(nick), clock.instant());
            String source =
                    decision.kind() == JoinDecision.Kind.DENY_BANNED && !lookup.confirmed() ? ", saved ban" : "";
            log.info(LookupLogLine.format(nick, lookup) + " -> "
                    + decision.kind().description() + source);
            return new JoinVerdict(lookup, decision, kickMessage(decision));
        } catch (RuntimeException e) {
            return failure(e);
        }
    }

    private Optional<Ban> activeSavedBan(String nick) {
        Optional<KnownPlayer> known = players.find(nick);
        if (!known.isPresent()) {
            return Optional.empty();
        }
        for (PlayerId id : known.get().seen()) {
            Optional<Ban> ban = bans.find(id);
            if (ban.isPresent() && ban.get().activeAt(clock.instant())) {
                return ban;
            }
        }
        return Optional.empty();
    }

    private Optional<String> kickMessage(JoinDecision decision) {
        try {
            return switch (decision.kind()) {
                case DENY_BANNED -> Optional.of(messages.banned(decision.ban().get()));
                case DENY_UNCONFIRMED -> Optional.of(messages.launcherRequired());
                default -> Optional.empty();
            };
        } catch (RuntimeException e) {
            log.warn("could not build the kick message, showed the built-in English text ("
                    + e.getClass().getName() + ")");
            return switch (decision.kind()) {
                case DENY_BANNED -> Optional.of("You are banned from this server.");
                case DENY_UNCONFIRMED -> Optional.of("You can only join this server through the MC Monitor launcher.");
                default -> Optional.empty();
            };
        }
    }

    private JoinVerdict failure(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        log.warn("join check failed, player let in as usual; please report this ("
                + cause.getClass().getName() + ")");
        return new JoinVerdict(Lookup.of(LookupOutcome.PLUGIN_ERROR), JoinDecision.pass(), Optional.empty());
    }
}
