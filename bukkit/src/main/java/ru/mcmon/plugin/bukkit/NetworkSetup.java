package ru.mcmon.plugin.bukkit;

import java.io.File;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;

// Read from the server's own settings files without server internals: spigot.yml through the Spigot API, Paper's
// files directly (config/paper-global.yml on 1.19+, paper.yml on 1.18). Paper opens them relative to the working
// directory, as done here; the world container may lie elsewhere.
record NetworkSetup(boolean behindProxy, boolean proxyProtocol) {

    static NetworkSetup of(Server server) {
        YamlConfiguration spigot = spigotConfig(server);
        File paperGlobalFile = new File("config/paper-global.yml");
        YamlConfiguration paperGlobal = load(paperGlobalFile);
        YamlConfiguration paperLegacy =
                paperGlobalFile.isFile() ? new YamlConfiguration() : load(new File("paper.yml"));
        boolean behindProxy = spigot.getBoolean("settings.bungeecord")
                || paperGlobal.getBoolean("proxies.velocity.enabled")
                || paperLegacy.getBoolean("settings.velocity-support.enabled");
        boolean proxyProtocol =
                paperGlobal.getBoolean("proxies.proxy-protocol") || paperLegacy.getBoolean("settings.proxy-protocol");
        return new NetworkSetup(behindProxy, proxyProtocol);
    }

    private static YamlConfiguration spigotConfig(Server server) {
        try {
            YamlConfiguration config = server.spigot().getConfig();
            if (config != null) {
                return config;
            }
        } catch (RuntimeException | LinkageError e) {
            // Not a Spigot-based server, or one that keeps the file elsewhere: read it from the working directory.
        }
        return load(new File("spigot.yml"));
    }

    private static YamlConfiguration load(File file) {
        return file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }
}
