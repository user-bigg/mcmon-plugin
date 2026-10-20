package ru.mcmon.plugin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import ru.mcmon.plugin.fakesite.FakeSite;
import ru.mcmon.plugin.fakesite.RecordedRequest;
import ru.mcmon.plugin.fakesite.Reply;
import ru.mcmon.plugin.platform.MinecraftLogin.JoinResult;
import ru.mcmon.plugin.platform.MinecraftLogin.LaunchCodeReply;

class PluginJoinTest {

    private static final String KEY = "platform-test-key-424242";
    private static final int TIMEOUT_MS = 2000;
    private static final String CONFIRMED = "Steve_Miner";
    private static final String GUEST = "Guest_One";
    private static final String SLOW = "Slow_Joe";
    private static final String BANNED = "Banned_Bob";
    private static final String CACHED = "Cached_Carl";
    private static final String CACHED_ID = "0123456789abcdef";
    private static final Reply.BanEntry CACHED_BAN = new Reply.BanEntry(CACHED_ID, "saved reason", null);
    private static final Reply BANS = Reply.bans("2026-10-13T10:00:00Z", true, CACHED_BAN);
    private static final String BAN_REASON = "grief at spawn";
    private static final String PLAYER_ID = "7f3a91c202be4d10";
    private static final String STRANGER = "Stranger_Sam";
    private static final String SILENT = "Silent_Sid";
    private static final String LATE = "Late_Larry";
    private static final String OLD_GAME = "Old_Olly";
    private static final int PROTOCOL_1_12_2 = 340;
    private static final String VIA_PLUGINS =
            "https://cdn.modrinth.com/data/P1OZGk5p/versions/5WgnasGG/ViaVersion-5.12.1.jar,"
                    + "https://cdn.modrinth.com/data/NpvuJQoq/versions/dWw67APC/ViaBackwards-5.12.1.jar";
    private static final String CODE = "Ab3dEf6hIj9kLm2nOp5qRs8tUv1wXy4z";
    private static final String FOREIGN_CODE = "Zz9yXx8wVv7uTt6sRr5qPp4oNn3mLl2k";
    private static final LaunchCodeReply MOD = LaunchCodeReply.code(CODE);
    private static final LaunchCodeReply NO_MOD = LaunchCodeReply.NOT_UNDERSTOOD;

    private static FakeSite site;
    private static volatile boolean cachedCarlSeen;

    @BeforeAll
    static void startSite() throws IOException {
        site = FakeSite.start(KEY, 0, InetAddress.getByName("0.0.0.0"));
        Testcontainers.exposeHostPorts(site.port());
        site.replyWith(request -> !CODE.equals(request.code())
                ? Reply.notConfirmed()
                : switch (request.nick()) {
                    case CONFIRMED -> Reply.confirmed(PLAYER_ID, CONFIRMED);
                    case SLOW -> Reply.confirmed(PLAYER_ID, SLOW).delayed(Duration.ofSeconds(10));
                    case BANNED ->
                        Reply.banned(PLAYER_ID, BANNED, "§c" + BAN_REASON, Instant.parse("2099-01-01T00:00:00Z"));
                    case CACHED -> cachedCarlSeen ? Reply.notConfirmed() : Reply.confirmed(CACHED_ID, CACHED);
                    default -> Reply.notConfirmed();
                });
    }

    @BeforeEach
    void requireLauncherOnTheSite() {
        site.replyToBansWith(BANS);
    }

    @AfterAll
    static void stopSite() {
        site.close();
    }

    @Test
    void paper26() throws Exception {
        assumeSelected("paper26");
        try (ServerContainer server = ServerContainer.paper("26.3", "178", "java25", pluginJar(), config())) {
            exercise(server);
        }
    }

    @Test
    void paper1_21() throws Exception {
        assumeSelected("paper1.21");
        try (ServerContainer server = ServerContainer.paper("1.21.11", "132", "java21", pluginJar(), config())) {
            exercise(server);
        }
    }

    @Test
    void paper1_18() throws Exception {
        assumeSelected("paper1.18");
        try (ServerContainer server = ServerContainer.paper("1.18.2", "388", "java17", pluginJar(), config())) {
            exercise(server);
        }
    }

    // A late answer reaches a server that has already switched compression on; the player must not be dropped.
    // ViaBackwards answers the query itself for a 1.12.2 game, after the network splitter.
    @Test
    void paper26LateAnswerAndOldGame() throws Exception {
        assumeSelected("paper26-late");
        try (ServerContainer server = ServerContainer.paper("26.3", "178", "java25", pluginJar(), config())
                .withEnv("PLUGINS", VIA_PLUGINS)) {
            site.replyToBansWith(Reply.bans("2026-10-13T10:00:00Z", false, CACHED_BAN));
            startAndAwaitBans(server);
            int protocol = protocolVersion(server);

            JoinResult late = join(
                    server, LATE, protocol, LaunchCodeReply.late(CODE, Duration.ofMillis(TIMEOUT_MS + 500)), false);

            assertTrue(
                    server.awaitLog(
                            log -> log.contains(
                                    LATE + ": not confirmed (no launch code from the game) -> let in as usual"),
                            Duration.ofSeconds(20)),
                    server.logs());
            assertEquals(MinecraftLogin.Outcome.LOGIN_OK, late.outcome(), "late answer dropped the player: " + late);

            server.waitOutConnectionThrottle();
            JoinResult old = join(server, OLD_GAME, PROTOCOL_1_12_2, NO_MOD);
            assertTrue(
                    server.awaitLog(
                            log -> log.contains(
                                    OLD_GAME + ": not confirmed (no launch code from the game) -> let in as usual"),
                            Duration.ofSeconds(20)),
                    server.logs());
            assertEquals(MinecraftLogin.Outcome.LOGIN_OK, old.outcome(), "1.12.2 game dropped: " + old);
            assertTrue(site.lookups().isEmpty(), "lookups sent to the site");
        }
    }

    @Test
    void paper26BehindProxy() throws Exception {
        assumeSelected("paper26-proxy");
        try (ServerContainer server = ServerContainer.paper("26.3", "178", "java25", pluginJar(), config())
                .withServerFile("spigot.yml", "settings:\n  bungeecord: true\n")) {
            startAndAwaitBans(server);
            int protocol = protocolVersion(server);

            JoinResult guest = join(server, GUEST, protocol, NO_MOD, true);

            assertTrue(
                    server.awaitLog(
                            log -> log.contains(GUEST + ": not confirmed (behind a proxy, launch codes are asked by"
                                    + " the proxy) -> let in as usual"),
                            Duration.ofSeconds(20)),
                    server.logs());
            assertEquals(MinecraftLogin.Outcome.LOGIN_OK, guest.outcome(), "player not let in: " + guest);
            assertEquals(0, guest.codeRequests(), "the backend asked for a launch code");
            assertTrue(site.lookups().isEmpty(), "lookups sent to the site");
        }
    }

    @Test
    void folia1_21() throws Exception {
        assumeSelected("folia");
        try (ServerContainer server = ServerContainer.folia("1.21.11", "14", "java21", pluginJar(), config())) {
            exercise(server);
            assertTrue(server.logs().contains("running Folia"), server.logs());
            assertFalse(server.logs().contains("UnsupportedOperationException"), server.logs());
        }
    }

    @Test
    void arclight1_21() throws Exception {
        assumeSelected("arclight1.21");
        try (ServerContainer server = ServerContainer.arclight("1.21.1", "NEOFORGE", "java21", pluginJar(), config())) {
            exercise(server);
        }
    }

    @Test
    void arclight1_20_1() throws Exception {
        assumeSelected("arclight1.20.1");
        try (ServerContainer server = ServerContainer.arclightJar(
                "1.20.1",
                "https://github.com/IzzelAliz/Arclight/releases/download/Trials/1.0.6/arclight-forge-1.20.1-1.0.6.jar",
                "java17",
                pluginJar(),
                config())) {
            exercise(server);
        }
    }

    @Test
    void velocity4() throws Exception {
        assumeSelected("velocity4");
        try (ServerContainer server = ServerContainer.velocity("4.2.0", "30", "java25", pluginJar(), config())) {
            exercise(server);
        }
    }

    @Test
    void velocity3() throws Exception {
        assumeSelected("velocity3");
        try (ServerContainer server = ServerContainer.velocity("3.5.1", "615", "java21", pluginJar(), config())) {
            exercise(server);
        }
    }

    @Test
    void bungee() throws Exception {
        assumeSelected("bungee");
        try (ServerContainer server = ServerContainer.bungee("2104", "java17", pluginJar(), config())) {
            exercise(server);
        }
    }

    private static void startAndAwaitBans(ServerContainer server) throws InterruptedException {
        site.clearRequests();
        server.start();
        assertTrue(
                server.awaitLog(log -> log.contains("connected to the site: 1 ban as of"), Duration.ofSeconds(20)),
                server.logs());
    }

    private static void exercise(ServerContainer server) throws Exception {
        startAndAwaitBans(server);

        int protocol = protocolVersion(server);

        JoinResult confirmedResult = join(server, CONFIRMED, protocol, MOD);
        assertTrue(
                server.awaitLog(
                        log -> log.contains(CONFIRMED + ": confirmed, player number " + PLAYER_ID + " -> let in"),
                        Duration.ofSeconds(20)),
                server.logs());
        assertLetIn(server, confirmedResult);

        server.waitOutConnectionThrottle();
        JoinResult banned = join(server, BANNED, protocol, MOD);
        assertTrue(
                server.awaitLog(
                        log -> log.contains(BANNED + ": confirmed, player number " + PLAYER_ID
                                + ", banned until 2099-01-01T00:00:00Z: " + BAN_REASON + " -> kept out, banned"),
                        Duration.ofSeconds(20)),
                server.logs());
        assertTrue(banned.reason().contains("Reason: " + BAN_REASON), "banned player saw: " + banned);
        assertFalse(banned.reason().contains("§"), "colour code reached the player: " + banned);

        server.waitOutConnectionThrottle();
        JoinResult guest = join(server, GUEST, protocol, NO_MOD);
        assertTrue(
                server.awaitLog(
                        log -> log.contains(GUEST + ": not confirmed (no launch code from the game)"
                                + " -> kept out, launcher-only mode is on"),
                        Duration.ofSeconds(20)),
                server.logs());
        assertTrue(guest.reason().contains("MC Monitor launcher"), "guest saw: " + guest);

        server.waitOutConnectionThrottle();
        JoinResult stranger = join(server, STRANGER, protocol, LaunchCodeReply.code(FOREIGN_CODE));
        assertTrue(
                server.awaitLog(
                        log -> log.contains(STRANGER + ": not confirmed -> kept out, launcher-only mode is on"),
                        Duration.ofSeconds(20)),
                server.logs());
        assertTrue(stranger.reason().contains("MC Monitor launcher"), "player with a foreign code saw: " + stranger);

        cachedCarlSeen = false;
        server.waitOutConnectionThrottle();
        assertLetIn(server, join(server, CACHED, protocol, MOD));
        assertTrue(
                server.awaitLog(
                        log -> log.contains(CACHED + ": confirmed, player number " + CACHED_ID + " -> let in"),
                        Duration.ofSeconds(20)),
                server.logs());
        cachedCarlSeen = true;
        server.waitOutConnectionThrottle();
        JoinResult cached = join(server, CACHED, protocol, NO_MOD);
        assertTrue(
                server.awaitLog(
                        log -> log.contains(CACHED
                                + ": not confirmed (no launch code from the game) -> kept out, banned, saved ban"),
                        Duration.ofSeconds(20)),
                server.logs());
        assertTrue(cached.reason().contains("saved reason"), "cached-banned player saw: " + cached);

        server.waitOutConnectionThrottle();
        long started = System.nanoTime();
        JoinResult slow = join(server, SLOW, protocol, MOD);
        assertTrue(
                server.awaitLog(
                        log -> log.contains(SLOW + ": not confirmed (no answer within timeout-ms) -> let in as usual"),
                        Duration.ofSeconds(20)),
                server.logs());
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        assertTrue(elapsedMillis < 4000, "slow site held the login for " + elapsedMillis + " ms");
        assertLetIn(server, slow);

        server.waitOutConnectionThrottle();
        JoinResult silent = join(server, SILENT, protocol, LaunchCodeReply.SILENT);
        if (server.holdsUnansweredLogin()) {
            assertFalse(silent.outcome() == MinecraftLogin.Outcome.LOGIN_OK, "silent client got in: " + silent);
            assertFalse(server.logs().contains(SILENT + ":"), server.logs());
        } else {
            assertTrue(
                    server.awaitLog(
                            log -> log.contains(SILENT + ": not confirmed (no launch code from the game)"
                                    + " -> kept out, launcher-only mode is on"),
                            Duration.ofSeconds(20)),
                    server.logs());
            assertTrue(silent.millis() < TIMEOUT_MS + 1000, "silent client waited " + silent.millis() + " ms");
            assertTrue(silent.reason().contains("MC Monitor launcher"), "silent client saw: " + silent);
        }

        String log = server.logs();
        assertFalse(log.contains(KEY), "server key leaked into the log");
        assertFalse(log.contains(CODE) || log.contains(FOREIGN_CODE), "launch code leaked into the log");
        assertEquals(
                List.of(CONFIRMED, BANNED, STRANGER, CACHED, SLOW),
                site.lookups().stream().map(r -> r.nick()).toList(),
                "lookups sent to the site");
        for (RecordedRequest lookup : site.lookups()) {
            assertEquals(Set.of("code", "nick"), lookup.json().keySet(), "lookup body");
        }
    }

    // The published port accepts connections a moment before the server inside listens; the first ping may hit that
    // gap.
    private static int protocolVersion(ServerContainer server) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                return MinecraftLogin.protocolVersion(server.host(), server.port());
            } catch (IOException e) {
                last = e;
                Thread.sleep(1000);
            }
        }
        throw last;
    }

    private static JoinResult join(ServerContainer server, String nick, int protocol, LaunchCodeReply reply)
            throws IOException {
        return join(server, nick, protocol, reply, false);
    }

    private static JoinResult join(
            ServerContainer server, String nick, int protocol, LaunchCodeReply reply, boolean bungeeForwarding)
            throws IOException {
        return MinecraftLogin.join(server.host(), server.port(), nick, protocol, reply, bungeeForwarding);
    }

    // A server must send Login Success; a proxy without a backend kicks for its own reasons, as long as the reason
    // is not ours.
    private static void assertLetIn(ServerContainer server, JoinResult result) {
        if (server.proxy()) {
            String text = result.reason();
            assertFalse(text.contains("banned") || text.contains("launcher"), "unexpected kick: " + result);
        } else {
            assertEquals(MinecraftLogin.Outcome.LOGIN_OK, result.outcome(), "player not let in: " + result);
        }
    }

    private static String config() {
        return """
                server-key: "%s"
                site-url: "http://host.testcontainers.internal:%d"
                timeout-ms: %d
                language: en
                """.formatted(KEY, site.port(), TIMEOUT_MS);
    }

    private static Path pluginJar() {
        return Path.of(System.getProperty("mcmonitor.pluginJar"));
    }

    private static void assumeSelected(String name) {
        String selected = System.getProperty("mcmonitor.platform", "");
        boolean all = selected.isBlank() || selected.equals("all") || selected.equals("true");
        assumeTrue(all || List.of(selected.split(",")).contains(name), "platform " + name + " not selected");
    }
}
