package ru.mcmon.plugin.platform;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Predicate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

final class ServerContainer implements AutoCloseable {

    static final Duration START_TIMEOUT = Duration.ofMinutes(5);

    private final GenericContainer<?> container;
    private final int gamePort;
    private final boolean throttlesReconnects;
    private final boolean proxy;
    private final boolean holdsUnansweredLogin;

    private ServerContainer(GenericContainer<?> container, int gamePort, boolean throttlesReconnects) {
        this(container, gamePort, throttlesReconnects, false, false);
    }

    private ServerContainer(
            GenericContainer<?> container,
            int gamePort,
            boolean throttlesReconnects,
            boolean proxy,
            boolean holdsUnansweredLogin) {
        this.container = container;
        this.gamePort = gamePort;
        this.throttlesReconnects = throttlesReconnects;
        this.proxy = proxy;
        this.holdsUnansweredLogin = holdsUnansweredLogin;
    }

    // Velocity itself waits for every login plugin answer and has no API to stop waiting; only its read-timeout
    // ends such a login.
    boolean holdsUnansweredLogin() {
        return holdsUnansweredLogin;
    }

    // A proxy without a backend kicks every player it lets through; a server finishes the login itself.
    boolean proxy() {
        return proxy;
    }

    void waitOutConnectionThrottle() throws InterruptedException {
        if (throttlesReconnects) {
            Thread.sleep(4500);
        }
    }

    static ServerContainer paper(String version, String build, String javaTag, Path pluginJar, String config) {
        GenericContainer<?> container = new GenericContainer<>(
                        DockerImageName.parse("itzg/minecraft-server:" + javaTag))
                .withEnv("EULA", "TRUE")
                .withEnv("PAPER_BUILD", build)
                .withEnv("UID", "0")
                .withEnv("GID", "0")
                .withEnv("TYPE", "PAPER")
                .withEnv("VERSION", version)
                .withEnv("ONLINE_MODE", "FALSE")
                .withEnv("MEMORY", "1G")
                .withEnv("LEVEL_TYPE", "flat")
                .withEnv("VIEW_DISTANCE", "2")
                .withEnv("SPAWN_PROTECTION", "0")
                .withEnv("GUI", "FALSE")
                .withCopyFileToContainer(MountableFile.forHostPath(pluginJar), "/data/plugins/mcmonitor.jar")
                .withCopyToContainer(
                        org.testcontainers.images.builder.Transferable.of(config), "/data/plugins/MCMonitor/config.yml")
                .withExposedPorts(25565)
                .waitingFor(Wait.forLogMessage(".*Done \\(.*", 1).withStartupTimeout(START_TIMEOUT));
        return new ServerContainer(container, 25565, true);
    }

    // Arclight is a Forge or NeoForge server with the Bukkit API on top; the plugin jar goes into plugins/.
    // Its first start also runs the Forge installer, which takes longer than the other servers.
    static ServerContainer arclight(String version, String loader, String javaTag, Path pluginJar, String config) {
        GenericContainer<?> container = new GenericContainer<>(
                        DockerImageName.parse("itzg/minecraft-server:" + javaTag))
                .withEnv("EULA", "TRUE")
                .withEnv("UID", "0")
                .withEnv("GID", "0")
                .withEnv("TYPE", "ARCLIGHT")
                .withEnv("ARCLIGHT_TYPE", loader)
                .withEnv("VERSION", version)
                .withEnv("ONLINE_MODE", "FALSE")
                .withEnv("MEMORY", "2G")
                .withEnv("LEVEL_TYPE", "flat")
                .withEnv("VIEW_DISTANCE", "2")
                .withEnv("SPAWN_PROTECTION", "0")
                .withEnv("GUI", "FALSE")
                .withCopyFileToContainer(MountableFile.forHostPath(pluginJar), "/data/plugins/mcmonitor.jar")
                .withCopyToContainer(
                        org.testcontainers.images.builder.Transferable.of(config), "/data/plugins/MCMonitor/config.yml")
                .withExposedPorts(25565)
                .waitingFor(Wait.forLogMessage(".*Done \\(.*", 1).withStartupTimeout(START_TIMEOUT.multipliedBy(2)));
        return new ServerContainer(container, 25565, true);
    }

    // The image installs Arclight only from its latest release, which has no 1.20.1; that one runs from its jar.
    static ServerContainer arclightJar(String version, String jarUrl, String javaTag, Path pluginJar, String config) {
        GenericContainer<?> container = new GenericContainer<>(
                        DockerImageName.parse("itzg/minecraft-server:" + javaTag))
                .withEnv("EULA", "TRUE")
                .withEnv("UID", "0")
                .withEnv("GID", "0")
                .withEnv("TYPE", "CUSTOM")
                .withEnv("CUSTOM_SERVER", jarUrl)
                .withEnv("VERSION", version)
                .withEnv("ONLINE_MODE", "FALSE")
                .withEnv("MEMORY", "2G")
                .withEnv("LEVEL_TYPE", "flat")
                .withEnv("VIEW_DISTANCE", "2")
                .withEnv("SPAWN_PROTECTION", "0")
                .withEnv("GUI", "FALSE")
                .withCopyFileToContainer(MountableFile.forHostPath(pluginJar), "/data/plugins/mcmonitor.jar")
                .withCopyToContainer(
                        org.testcontainers.images.builder.Transferable.of(config), "/data/plugins/MCMonitor/config.yml")
                .withExposedPorts(25565)
                .waitingFor(Wait.forLogMessage(".*Done \\(.*", 1).withStartupTimeout(START_TIMEOUT.multipliedBy(2)));
        return new ServerContainer(container, 25565, true);
    }

    static ServerContainer folia(String version, String build, String javaTag, Path pluginJar, String config) {
        GenericContainer<?> container = new GenericContainer<>(
                        DockerImageName.parse("itzg/minecraft-server:" + javaTag))
                .withEnv("EULA", "TRUE")
                .withEnv("FOLIA_BUILD", build)
                .withEnv("UID", "0")
                .withEnv("GID", "0")
                .withEnv("TYPE", "FOLIA")
                .withEnv("VERSION", version)
                .withEnv("ONLINE_MODE", "FALSE")
                .withEnv("MEMORY", "1G")
                .withEnv("LEVEL_TYPE", "flat")
                .withEnv("VIEW_DISTANCE", "2")
                .withEnv("SPAWN_PROTECTION", "0")
                .withEnv("GUI", "FALSE")
                .withCopyFileToContainer(MountableFile.forHostPath(pluginJar), "/data/plugins/mcmonitor.jar")
                .withCopyToContainer(
                        org.testcontainers.images.builder.Transferable.of(config), "/data/plugins/MCMonitor/config.yml")
                .withExposedPorts(25565)
                .waitingFor(Wait.forLogMessage(".*Done \\(.*", 1).withStartupTimeout(START_TIMEOUT));
        return new ServerContainer(container, 25565, true);
    }

    static ServerContainer velocity(String version, String build, String javaTag, Path pluginJar, String config) {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("itzg/mc-proxy:" + javaTag))
                .withEnv("VELOCITY_BUILD_ID", build)
                .withEnv("PUID", "0")
                .withEnv("PGID", "0")
                .withEnv("TYPE", "VELOCITY")
                .withEnv("VELOCITY_VERSION", version)
                .withEnv("MEMORY", "512m")
                .withCopyFileToContainer(MountableFile.forHostPath(pluginJar), "/server/plugins/mcmonitor.jar")
                .withCopyToContainer(
                        org.testcontainers.images.builder.Transferable.of(config),
                        "/server/plugins/mcmonitor/config.yml")
                .withCopyToContainer(
                        org.testcontainers.images.builder.Transferable.of(VELOCITY_TOML), "/config/velocity.toml")
                .withExposedPorts(25577)
                .waitingFor(Wait.forLogMessage(".*Done \\(.*", 1).withStartupTimeout(START_TIMEOUT));
        return new ServerContainer(container, 25577, false, true, true);
    }

    static ServerContainer bungee(String build, String javaTag, Path pluginJar, String config) {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("itzg/mc-proxy:" + javaTag))
                .withEnv("BUNGEE_JOB_ID", build)
                .withEnv("PUID", "0")
                .withEnv("PGID", "0")
                .withEnv("TYPE", "BUNGEECORD")
                .withEnv("MEMORY", "512m")
                .withCopyFileToContainer(MountableFile.forHostPath(pluginJar), "/server/plugins/mcmonitor.jar")
                .withCopyToContainer(
                        org.testcontainers.images.builder.Transferable.of(config),
                        "/server/plugins/MCMonitor/config.yml")
                .withCopyToContainer(
                        org.testcontainers.images.builder.Transferable.of(BUNGEE_CONFIG), "/config/config.yml")
                .withExposedPorts(25577)
                .waitingFor(Wait.forLogMessage(".*Listening on .*", 1).withStartupTimeout(START_TIMEOUT));
        return new ServerContainer(container, 25577, false, true, false);
    }

    ServerContainer withEnv(String name, String value) {
        container.withEnv(name, value);
        return this;
    }

    ServerContainer withServerFile(String path, String content) {
        container.withCopyToContainer(org.testcontainers.images.builder.Transferable.of(content), "/data/" + path);
        return this;
    }

    void start() {
        container.start();
    }

    String host() {
        return container.getHost();
    }

    int port() {
        return container.getMappedPort(gamePort);
    }

    String logs() {
        return container.getLogs();
    }

    boolean awaitLog(Predicate<String> condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.test(logs())) {
                return true;
            }
            Thread.sleep(500);
        }
        return condition.test(logs());
    }

    @Override
    public void close() {
        container.stop();
    }

    static final String VELOCITY_TOML = """
            config-version = "2.7"
            bind = "0.0.0.0:25577"
            motd = "MCMonitor test"
            show-max-players = 10
            online-mode = false
            force-key-authentication = false
            prevent-client-proxy-connections = false
            player-info-forwarding-mode = "NONE"
            forwarding-secret-file = "forwarding.secret"
            announce-forge = false
            kick-existing-players = false
            ping-passthrough = "DISABLED"
            enable-player-address-logging = true

            [servers]
            try = []

            [forced-hosts]

            [advanced]
            compression-threshold = 256
            compression-level = -1
            login-ratelimit = 0
            connection-timeout = 5000
            read-timeout = 30000
            haproxy-protocol = false
            tcp-fast-open = false
            bungee-plugin-message-channel = true
            show-ping-requests = false
            failover-on-unexpected-server-disconnect = true
            announce-proxy-commands = true
            log-command-executions = false
            log-player-connections = true
            accepts-transfers = false

            [query]
            enabled = false
            port = 25577
            map = "Velocity"
            show-plugins = false
            """;

    static final String BUNGEE_CONFIG = """
            online_mode: false
            ip_forward: false
            network_compression_threshold: 256
            connection_throttle: -1
            listeners:
            - host: 0.0.0.0:25577
              max_players: 10
              motd: MCMonitor test
              priorities:
              - lobby
              force_default_server: false
              bind_local_address: true
              proxy_protocol: false
              ping_passthrough: false
              query_enabled: false
            servers:
              lobby:
                address: 127.0.0.1:1
                restricted: false
            """;
}
