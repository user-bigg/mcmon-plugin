package ru.mcmon.plugin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import ru.mcmon.plugin.core.CodeAnswer;
import ru.mcmon.plugin.core.JoinGate;
import ru.mcmon.plugin.core.JoinVerdict;
import ru.mcmon.plugin.core.Log;
import ru.mcmon.plugin.core.Nick;
import ru.mcmon.plugin.core.PluginRuntime;

@Plugin(
        id = "mcmonitor",
        name = "MCMonitor",
        version = BuildConstants.VERSION,
        url = "https://mc-mon.ru",
        authors = {"MC Monitor"},
        description =
                "Confirms players who join through the MC Monitor launcher and keeps out players banned in the server dashboard.")
public final class MCMonitorVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private volatile PluginRuntime runtime;
    private volatile JoinGate gate;
    private volatile LaunchCodeQueries codes;

    @Inject
    public MCMonitorVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Log log = new VelocityLog(logger);
        LaunchCodeQueries queries = new LaunchCodeQueries(log);
        codes = queries;
        proxy.getScheduler()
                .buildTask(this, queries::forgetAbandoned)
                .repeat(LaunchCodeQueries.ABANDONED_AFTER)
                .schedule();
        Optional<PluginRuntime> started =
                PluginRuntime.start(dataDirectory, "plugins/mcmonitor/config.yml", BuildConstants.VERSION, log);
        if (started.isEmpty()) {
            return;
        }
        runtime = started.get();
        gate = runtime.gate();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        PluginRuntime current = runtime;
        if (current != null) {
            current.shutdown();
            runtime = null;
        }
    }

    // Velocity sends login plugin messages queued during PreLoginEvent and holds the login until the client answers;
    // LoginEvent comes after that, so the answer is already there.
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        PluginRuntime current = runtime;
        if (current == null
                || !current.settings().hasServerKey()
                || !Nick.isValid(event.getUsername())
                || !event.getResult().isAllowed()) {
            return;
        }
        codes.ask(event.getConnection());
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        JoinGate current = gate;
        if (current == null || !event.getResult().isAllowed()) {
            return null;
        }
        Player player = event.getPlayer();
        CompletableFuture<CodeAnswer> answer = codes.answer(player.getRemoteAddress());
        return EventTask.resumeWhenComplete(
                current.check(player.getUsername(), () -> answer).thenAccept(verdict -> apply(event, verdict)));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        LaunchCodeQueries current = codes;
        if (current != null) {
            current.forget(event.getPlayer().getRemoteAddress());
        }
    }

    private void apply(LoginEvent event, JoinVerdict verdict) {
        if (verdict.denied()) {
            event.setResult(ResultedEvent.ComponentResult.denied(
                    Component.text(verdict.kickMessage().orElse(""))));
        }
    }
}
