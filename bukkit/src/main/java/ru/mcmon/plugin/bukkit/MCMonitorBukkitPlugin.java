package ru.mcmon.plugin.bukkit;

import java.util.Optional;
import org.bukkit.plugin.java.JavaPlugin;
import ru.mcmon.plugin.bukkit.login.LaunchCodeQueries;
import ru.mcmon.plugin.core.Log;
import ru.mcmon.plugin.core.PluginRuntime;

public final class MCMonitorBukkitPlugin extends JavaPlugin {

    private PluginRuntime runtime;
    private LaunchCodeQueries codes;

    @Override
    public void onEnable() {
        Log log = new BukkitLog(getLogger());
        NetworkSetup network = NetworkSetup.of(getServer());
        Optional<PluginRuntime> started = PluginRuntime.start(
                getDataFolder().toPath(),
                "plugins/MCMonitor/config.yml",
                getDescription().getVersion(),
                log,
                network.behindProxy());
        if (started.isEmpty()) {
            return;
        }
        runtime = started.get();
        if (network.behindProxy()) {
            log.warn("behind a proxy: install the proxy jar on the proxy; this server does not ask for launch codes"
                    + " and does not apply require-launcher. Saved bans still apply");
            codes = LaunchCodeQueries.behindProxy(log);
        } else {
            codes = LaunchCodeQueries.install(
                    getServer(), network.proxyProtocol(), getServer().getOnlineMode(), log);
        }
        getServer().getPluginManager().registerEvents(new JoinListener(runtime.gate(), codes), this);
    }

    @Override
    public void onDisable() {
        if (codes != null) {
            codes.uninstall();
            codes = null;
        }
        if (runtime != null) {
            runtime.shutdown();
            runtime = null;
        }
    }
}
