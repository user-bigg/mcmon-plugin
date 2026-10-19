package ru.mcmon.plugin.core;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Optional;

public final class PluginRuntime {

    private final Settings settings;
    private final SiteClient site;
    private final BanSync bans;
    private final PlayerStore players;
    private final Messages messages;
    private final JoinGate gate;

    private PluginRuntime(
            Settings settings, SiteClient site, BanSync bans, PlayerStore players, Messages messages, JoinGate gate) {
        this.settings = settings;
        this.site = site;
        this.bans = bans;
        this.players = players;
        this.messages = messages;
        this.gate = gate;
    }

    public static Optional<PluginRuntime> start(Path dataFolder, String configPath, String userAgentVersion, Log log) {
        return start(dataFolder, configPath, userAgentVersion, log, false);
    }

    // Behind a proxy the backend never gets a launch code, so require-launcher would keep everyone out.
    public static Optional<PluginRuntime> start(
            Path dataFolder, String configPath, String userAgentVersion, Log log, boolean behindProxy) {
        Settings settings;
        try {
            settings = SettingsFile.load(dataFolder.resolve("config.yml"));
        } catch (IOException e) {
            log.warn(configPath + " could not be read (" + e.getClass().getSimpleName()
                    + "), check that it is UTF-8. MC Monitor is off until you fix it and restart the server");
            return Optional.empty();
        } catch (SettingsException e) {
            log.warn(
                    configPath + ": " + e.getMessage() + ". MC Monitor is off until you fix it and restart the server");
            return Optional.empty();
        }
        if (behindProxy) {
            settings = settings.withRequireLauncher(false);
        }
        if (!settings.hasServerKey()) {
            log.warn("server-key is empty in " + configPath
                    + ". Copy the key from the MC Monitor dashboard and restart the"
                    + " server; until then MC Monitor does not contact the site");
        }
        if (settings.insecureSite()) {
            log.warn("site-url uses http, so the server key is sent unencrypted; use https");
        }
        SiteClient site;
        try {
            site = new HttpSiteClient(settings, userAgentVersion);
        } catch (SettingsException e) {
            log.warn(
                    configPath + ": " + e.getMessage() + ". MC Monitor is off until you fix it and restart the server");
            return Optional.empty();
        }
        BanSync bans = BanSync.open(site, dataFolder.resolve("bans.json"), log);
        PlayerStore players = PlayerStore.open(dataFolder.resolve("players.json"), log);
        Messages messages = Messages.load(dataFolder, settings.language(), ZoneId.systemDefault(), log);
        JoinGate gate = new JoinGate(
                new PlayerJoinCheck(settings, site, log), new JoinPolicy(settings), players, bans, messages, log);
        if (settings.hasServerKey()) {
            bans.start();
        }
        log.info("ready: site-url " + settings.siteUrl() + ", timeout-ms "
                + settings.timeout().toMillis()
                + ", require-launcher " + settings.requireLauncher()
                + ", language " + settings.language());
        return Optional.of(new PluginRuntime(settings, site, bans, players, messages, gate));
    }

    public Settings settings() {
        return settings;
    }

    public PlayerStore players() {
        return players;
    }

    public Messages messages() {
        return messages;
    }

    public JoinGate gate() {
        return gate;
    }

    public void shutdown() {
        bans.stop();
        site.shutdown();
    }
}
