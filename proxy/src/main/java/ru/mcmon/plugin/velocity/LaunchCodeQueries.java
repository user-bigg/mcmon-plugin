package ru.mcmon.plugin.velocity;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.LoginPhaseConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.mcmon.plugin.core.CodeAnswer;
import ru.mcmon.plugin.core.LaunchCode;
import ru.mcmon.plugin.core.Log;

// PreLoginEvent and LoginEvent share nothing but the connection's address: IP and port of one TCP connection.
final class LaunchCodeQueries {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create(LaunchCode.CHANNEL_NAMESPACE, LaunchCode.CHANNEL_PATH);
    // Only for logins that never reach LoginEvent or DisconnectEvent; Velocity's read-timeout ends them sooner.
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(2);

    private final Log log;
    private final Map<InetSocketAddress, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean warned = new AtomicBoolean();

    LaunchCodeQueries(Log log) {
        this.log = log;
    }

    void ask(InboundConnection connection) {
        InetSocketAddress address = connection.getRemoteAddress();
        if (address == null) {
            return;
        }
        CompletableFuture<CodeAnswer> answer = new CompletableFuture<>();
        Pending entry = new Pending(answer, System.nanoTime());
        // Two live logins from one address and port can only come through a forged PROXY header; neither gets a
        // code then, so one can never receive the other's.
        Pending conflict = new Pending(CompletableFuture.completedFuture(CodeAnswer.none()), entry.createdNanos);
        if (!entry.equals(pending.merge(address, entry, (existing, added) -> conflict))) {
            return;
        }
        try {
            if (!(connection instanceof LoginPhaseConnection login)) {
                cannotAsk();
                answer.complete(CodeAnswer.notAsked());
            } else if (connection.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_13) < 0) {
                answer.complete(CodeAnswer.none());
            } else {
                login.sendLoginPluginMessage(CHANNEL, new byte[0], body -> answer.complete(CodeAnswer.answered(body)));
            }
        } catch (RuntimeException | LinkageError e) {
            cannotAsk();
            answer.complete(CodeAnswer.notAsked());
        }
    }

    CompletableFuture<CodeAnswer> answer(InetSocketAddress address) {
        Pending entry = address == null ? null : pending.remove(address);
        return entry == null ? CompletableFuture.completedFuture(CodeAnswer.none()) : entry.answer;
    }

    void forget(InetSocketAddress address) {
        if (address != null) {
            pending.remove(address);
        }
    }

    void forgetAbandoned() {
        long now = System.nanoTime();
        pending.values().removeIf(entry -> now - entry.createdNanos > ABANDONED_AFTER.toNanos());
    }

    private void cannotAsk() {
        if (warned.compareAndSet(false, true)) {
            log.warn("launch codes cannot be requested on this proxy build; launcher-only mode has no effect."
                    + " Players join as if the plugin were not installed; saved bans still apply. Please report your Velocity"
                    + " version");
        }
    }

    private record Pending(CompletableFuture<CodeAnswer> answer, long createdNanos) {}
}
