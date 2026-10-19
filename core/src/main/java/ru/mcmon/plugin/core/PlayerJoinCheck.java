package ru.mcmon.plugin.core;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class PlayerJoinCheck {

    private static final Duration HARD_LIMIT_GRACE = Duration.ofSeconds(1);

    private final Settings settings;
    private final SiteClient site;
    private final Log log;
    private volatile boolean keyRejected;
    private volatile boolean keyHintGiven;

    public PlayerJoinCheck(Settings settings, SiteClient site, Log log) {
        this.settings = settings;
        this.site = site;
        this.log = log;
    }

    public CompletableFuture<Lookup> check(String nick, Supplier<CompletableFuture<CodeAnswer>> askCode) {
        return lookup(nick, askCode)
                .exceptionally(this::pluginError)
                .thenApplyAsync(this::hintAboutKey, site.executor())
                .exceptionally(this::pluginError);
    }

    private Lookup hintAboutKey(Lookup lookup) {
        if (lookup.outcome() == LookupOutcome.INVALID_KEY) {
            keyRejected = true;
        } else if (lookup.outcome() == LookupOutcome.CONFIRMED || lookup.outcome() == LookupOutcome.NOT_CONFIRMED) {
            keyRejected = false;
            keyHintGiven = false;
        }
        boolean suspicious = lookup.outcome() == LookupOutcome.INVALID_KEY
                || (lookup.outcome() == LookupOutcome.RATE_LIMITED && keyRejected);
        if (suspicious && !keyHintGiven) {
            keyHintGiven = true;
            log.warn("the site rejects the server key. Copy the key from the MC Monitor dashboard into server-key and"
                    + " restart the server (a new server owner gets a new key). Until then players join as usual;"
                    + " saved bans still apply");
        }
        return lookup;
    }

    private CompletableFuture<Lookup> lookup(String nick, Supplier<CompletableFuture<CodeAnswer>> askCode) {
        if (!settings.hasServerKey()) {
            return CompletableFuture.completedFuture(Lookup.of(LookupOutcome.NO_SERVER_KEY));
        }
        if (!Nick.isValid(nick)) {
            return CompletableFuture.completedFuture(Lookup.of(LookupOutcome.INVALID_NICK));
        }
        // The only wait limit for the game's answer on every platform: an answer that comes later is dropped by
        // the platform, never treated as a code.
        return ask(askCode)
                .exceptionally(error -> CodeAnswer.notAsked())
                .completeOnTimeout(CodeAnswer.none(), settings.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .thenCompose(received -> lookup(nick, received));
    }

    private static CompletableFuture<CodeAnswer> ask(Supplier<CompletableFuture<CodeAnswer>> askCode) {
        try {
            CompletableFuture<CodeAnswer> answer = askCode.get();
            return answer != null ? answer : CompletableFuture.completedFuture(CodeAnswer.notAsked());
        } catch (RuntimeException | LinkageError e) {
            return CompletableFuture.completedFuture(CodeAnswer.notAsked());
        }
    }

    private CompletableFuture<Lookup> lookup(String nick, CodeAnswer answer) {
        Optional<LaunchCode> code = answer.code();
        if (code.isEmpty()) {
            return CompletableFuture.completedFuture(Lookup.of(answer.withoutCode()));
        }
        Duration hardLimit = settings.timeout().plus(HARD_LIMIT_GRACE);
        try {
            return site.lookup(nick, code.get())
                    .completeOnTimeout(Lookup.of(LookupOutcome.TIMEOUT), hardLimit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Lookup pluginError(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        log.warn("join check failed, player let in as usual; please report this ("
                + cause.getClass().getName() + ")");
        return Lookup.of(LookupOutcome.PLUGIN_ERROR);
    }
}
