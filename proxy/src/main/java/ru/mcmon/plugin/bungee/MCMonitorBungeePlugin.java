package ru.mcmon.plugin.bungee;

import java.nio.file.Path;
import java.util.Optional;
import net.md_5.bungee.api.plugin.Plugin;
import ru.mcmon.plugin.core.JoinGate;
import ru.mcmon.plugin.core.Log;
import ru.mcmon.plugin.core.PluginRuntime;

public final class MCMonitorBungeePlugin extends Plugin {

    private PluginRuntime runtime;

    @Override
    public void onEnable() {
        Log log = new BungeeLog(getLogger());
        Path dataFolder = getDataFolder().toPath();
        Optional<PluginRuntime> started = PluginRuntime.start(
                dataFolder, "plugins/MCMonitor/config.yml", getDescription().getVersion(), log);
        if (started.isEmpty()) {
            return;
        }
        runtime = started.get();
        JoinGate gate = runtime.gate();
        LaunchCodeQuery codes = new LaunchCodeQuery(log);
        codes.checkSupported();
        getProxy().getPluginManager().registerListener(this, new JoinListener(this, gate, codes));
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.shutdown();
            runtime = null;
        }
    }
}
