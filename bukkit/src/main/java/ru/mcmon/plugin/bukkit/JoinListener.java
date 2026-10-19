package ru.mcmon.plugin.bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import ru.mcmon.plugin.bukkit.login.LaunchCodeQueries;
import ru.mcmon.plugin.core.JoinDecision;
import ru.mcmon.plugin.core.JoinGate;
import ru.mcmon.plugin.core.JoinVerdict;

public final class JoinListener implements Listener {

    private final JoinGate gate;
    private final LaunchCodeQueries codes;

    public JoinListener(JoinGate gate, LaunchCodeQueries codes) {
        this.gate = gate;
        this.codes = codes;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        JoinVerdict verdict = gate.await(event.getName(), () -> codes.ask(event.getName(), event.getAddress()));
        if (verdict.denied()) {
            AsyncPlayerPreLoginEvent.Result result = verdict.decision().kind() == JoinDecision.Kind.DENY_BANNED
                    ? AsyncPlayerPreLoginEvent.Result.KICK_BANNED
                    : AsyncPlayerPreLoginEvent.Result.KICK_OTHER;
            event.disallow(result, verdict.kickMessage().orElse(""));
        }
    }
}
