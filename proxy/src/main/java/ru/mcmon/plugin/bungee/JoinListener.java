package ru.mcmon.plugin.bungee;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import ru.mcmon.plugin.core.JoinGate;
import ru.mcmon.plugin.core.JoinVerdict;

public final class JoinListener implements Listener {

    private final Plugin plugin;
    private final JoinGate gate;
    private final LaunchCodeQuery codes;

    JoinListener(Plugin plugin, JoinGate gate, LaunchCodeQuery codes) {
        this.plugin = plugin;
        this.gate = gate;
        this.codes = codes;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(LoginEvent event) {
        if (event.isCancelled()) {
            return;
        }
        event.registerIntent(plugin);
        try {
            gate.check(
                    event.getConnection().getName(),
                    () -> codes.ask(event.getConnection()),
                    verdict -> finish(event, verdict));
        } catch (RuntimeException e) {
            event.completeIntent(plugin);
        }
    }

    private void finish(LoginEvent event, JoinVerdict verdict) {
        try {
            if (verdict.denied()) {
                event.setCancelled(true);
                event.setReason(new TextComponent(verdict.kickMessage().orElse("")));
            }
        } finally {
            event.completeIntent(plugin);
        }
    }
}
