package ru.mcmon.plugin.bungee;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.md_5.bungee.api.connection.PendingConnection;
import ru.mcmon.plugin.core.CodeAnswer;
import ru.mcmon.plugin.core.LaunchCode;
import ru.mcmon.plugin.core.Log;

final class LaunchCodeQuery {

    private static final int PROTOCOL_1_13 = 393;

    private final Log log;
    private final AtomicBoolean warned = new AtomicBoolean();

    LaunchCodeQuery(Log log) {
        this.log = log;
    }

    // PendingConnection#sendData came with BungeeCord builds of September 2024; Waterfall and older builds lack it.
    void checkSupported() {
        try {
            PendingConnection.class.getMethod("sendData", String.class, byte[].class);
        } catch (NoSuchMethodException | LinkageError e) {
            cannotAsk();
        }
    }

    CompletableFuture<CodeAnswer> ask(PendingConnection connection) {
        if (connection.getVersion() < PROTOCOL_1_13) {
            return CompletableFuture.completedFuture(CodeAnswer.none());
        }
        try {
            return connection.sendData(LaunchCode.CHANNEL, new byte[0]).thenApply(CodeAnswer::answered);
        } catch (RuntimeException | LinkageError e) {
            cannotAsk();
            return CompletableFuture.completedFuture(CodeAnswer.notAsked());
        }
    }

    private void cannotAsk() {
        if (warned.compareAndSet(false, true)) {
            log.warn("launch codes cannot be requested on this proxy build; launcher-only mode has no effect."
                    + " Players join as if the plugin were not installed; saved bans still apply. Update BungeeCord to a build"
                    + " from September 2024 or newer");
        }
    }
}
