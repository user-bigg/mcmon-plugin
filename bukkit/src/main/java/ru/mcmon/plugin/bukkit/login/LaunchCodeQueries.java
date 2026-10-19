package ru.mcmon.plugin.bukkit.login;

import io.netty.channel.Channel;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.mcmon.plugin.core.CodeAnswer;
import ru.mcmon.plugin.core.Log;

public final class LaunchCodeQueries {

    private final Log log;
    private final boolean proxyProtocol;
    private final boolean onlineMode;
    private final boolean behindProxy;
    private final Map<String, Set<LoginWatcher>> loggingIn = new ConcurrentHashMap<>();
    private final Set<LoginWatcher> watchers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean degraded = new AtomicBoolean();
    private volatile boolean uninstalled;
    private volatile List<Channel> serverChannels = List.of();

    LaunchCodeQueries(boolean proxyProtocol, boolean onlineMode, Log log) {
        this(proxyProtocol, onlineMode, false, log);
    }

    private LaunchCodeQueries(boolean proxyProtocol, boolean onlineMode, boolean behindProxy, Log log) {
        this.proxyProtocol = proxyProtocol;
        this.onlineMode = onlineMode;
        this.behindProxy = behindProxy;
        this.log = log;
    }

    // With proxy-protocol the server sees the player's address from the PROXY header, the channel only the
    // address of the load balancer, so the two cannot be compared.
    public static LaunchCodeQueries install(Object bukkitServer, boolean proxyProtocol, boolean onlineMode, Log log) {
        LaunchCodeQueries queries = new LaunchCodeQueries(proxyProtocol, onlineMode, log);
        List<Channel> channels = List.of();
        try {
            channels = ServerChannels.of(bukkitServer);
            for (Channel channel : channels) {
                channel.pipeline().addFirst(AcceptHook.NAME, new AcceptHook(queries));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            channels = List.of();
        }
        queries.serverChannels = channels;
        if (channels.isEmpty()) {
            queries.cannotWatch();
        }
        return queries;
    }

    public static LaunchCodeQueries behindProxy(Log log) {
        return new LaunchCodeQueries(false, false, true, log);
    }

    public CompletableFuture<CodeAnswer> ask(String nick, InetAddress address) {
        if (behindProxy) {
            return CompletableFuture.completedFuture(CodeAnswer.behindProxy());
        }
        Set<LoginWatcher> sameNick = loggingIn.getOrDefault(key(nick), Set.of());
        if (sameNick.isEmpty()) {
            return CompletableFuture.completedFuture(degraded.get() ? CodeAnswer.notAsked() : CodeAnswer.none());
        }
        List<LoginWatcher> candidates = sameNick.stream()
                .filter(watcher -> isThisLogin(watcher, address))
                .toList();
        if (candidates.size() != 1) {
            return CompletableFuture.completedFuture(CodeAnswer.none());
        }
        LoginWatcher watcher = candidates.get(0);
        try {
            return watcher.ask();
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(CodeAnswer.notAsked());
        }
    }

    // A stranger may send Hello with someone else's nick and stay silent; such a login must not cost the real
    // player the code. By the time of AsyncPlayerPreLoginEvent an online-mode login is already encrypted.
    private boolean isThisLogin(LoginWatcher watcher, InetAddress address) {
        return (proxyProtocol || watcher.connectsFrom(address)) && (!onlineMode || watcher.encrypted());
    }

    public void uninstall() {
        uninstalled = true;
        for (Channel channel : serverChannels) {
            try {
                channel.pipeline().remove(AcceptHook.NAME);
            } catch (RuntimeException e) {
                // The listener is already closed.
            }
        }
        serverChannels = List.of();
        for (LoginWatcher watcher : watchers) {
            try {
                watcher.detachLater();
            } catch (RuntimeException e) {
                // The channel's event loop is already shut down.
            }
        }
    }

    boolean uninstalled() {
        return uninstalled;
    }

    boolean watching(LoginWatcher watcher) {
        watchers.add(watcher);
        return !uninstalled;
    }

    void loggingIn(String nick, LoginWatcher watcher) {
        loggingIn.compute(key(nick), (key, current) -> {
            Set<LoginWatcher> next = current == null ? new HashSet<>() : new HashSet<>(current);
            next.add(watcher);
            return Set.copyOf(next);
        });
    }

    void done(LoginWatcher watcher, String nick) {
        watchers.remove(watcher);
        if (nick != null) {
            loggingIn.computeIfPresent(key(nick), (key, current) -> {
                Set<LoginWatcher> next = new HashSet<>(current);
                next.remove(watcher);
                return next.isEmpty() ? null : Set.copyOf(next);
            });
        }
    }

    void cannotWatch() {
        if (degraded.compareAndSet(false, true)) {
            log.warn("launch codes cannot be requested on this server build; require-launcher has no effect."
                    + " Players join as if the plugin were not installed; saved bans still apply. Please report your server"
                    + " version");
        }
    }

    private static String key(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }
}
