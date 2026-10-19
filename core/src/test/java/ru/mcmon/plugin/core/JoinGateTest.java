package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcmon.plugin.fakesite.FakeSite;
import ru.mcmon.plugin.fakesite.Reply;

class JoinGateTest {

    private static final String KEY = "very-secret-key-98765";
    private static final String PLAYER = "7f3a91c202be4d10";

    private FakeSite site;
    private HttpSiteClient client;
    private RecordingLog log;
    private PlayerStore players;
    private BanSync bans;
    private Settings settings;

    @BeforeEach
    void start(@TempDir Path dir) throws IOException {
        site = FakeSite.start(KEY);
        settings = Settings.defaults()
                .withServerKey(KEY)
                .withSiteUrl(site.baseUrl())
                .withTimeout(Duration.ofMillis(300))
                .withLanguage("en")
                .withRequireLauncher(true);
        client = new HttpSiteClient(settings, "test");
        log = new RecordingLog();
        players = PlayerStore.open(dir.resolve("players.json"), log);
        bans = BanSync.open(client, dir.resolve("bans.json"), log);
    }

    @AfterEach
    void stop() {
        client.shutdown();
        site.close();
    }

    @Test
    void bannedPlayerIsDeniedWithPlainReasonAndRemembered() {
        site.replyWith(
                Reply.banned(PLAYER, "Steve_Miner", "§cgrief §lat spawn", Instant.parse("2099-01-01T00:00:00Z")));

        JoinVerdict verdict = gate().await("Steve_Miner", Codes.answered());

        assertEquals(JoinDecision.Kind.DENY_BANNED, verdict.decision().kind());
        assertTrue(
                verdict.kickMessage().orElseThrow().contains("Reason: grief at spawn"),
                verdict.kickMessage().toString());
        assertEquals(
                PLAYER, players.find("steve_miner").orElseThrow().lastSeen().hex());
    }

    @Test
    void guestIsDeniedWhenLauncherRequiredButSlowSitePasses() {
        site.replyWith(Reply.notConfirmed());
        JoinVerdict guest = gate().await("Guest_One", Codes.answered());
        assertEquals(JoinDecision.Kind.DENY_UNCONFIRMED, guest.decision().kind());
        assertTrue(guest.kickMessage().orElseThrow().contains("launcher"));

        site.replyWith(Reply.notConfirmed().delayed(Duration.ofSeconds(2)));
        JoinVerdict slow = gate().await("Guest_One", Codes.answered());
        assertEquals(JoinDecision.Kind.PASS, slow.decision().kind());
        assertTrue(slow.kickMessage().isEmpty());
    }

    @Test
    void neverLogsKeyOrCodeForAnyAnswer() {
        List<Reply> answers = List.of(
                Reply.confirmed(PLAYER, "Steve_Miner"),
                Reply.notConfirmed(),
                Reply.status(401, "{\"error\":\"unauthenticated\"}"),
                Reply.rateLimited(10),
                Reply.status(422, "{\"error\":\"invalid\"}"),
                Reply.status(500, "key " + KEY + " code " + Codes.CODE),
                Reply.garbage(),
                Reply.closedConnection(),
                Reply.notConfirmed().delayed(Duration.ofSeconds(2)));
        JoinGate gate = gate();
        for (Reply answer : answers) {
            site.replyWith(answer);
            gate.await("Steve_Miner", Codes.answered());
        }

        assertEquals(
                answers.size(),
                log.lines.stream().filter(line -> line.startsWith("INFO")).count());
        assertTrue(log.text().contains("WARN the site rejects the server key"), log.text());
        assertFalse(log.text().contains(KEY), log.text());
        assertFalse(log.text().contains(Codes.CODE), log.text());
    }

    @Test
    void brokenMessagesStillDenyWithBuiltInText() {
        Messages broken = Messages.of(java.util.Map.of(), "en", ZoneId.of("UTC"));
        JoinGate gate = new JoinGate(
                new PlayerJoinCheck(settings, client, log), new JoinPolicy(settings), players, bans, broken, log);
        site.replyWith(Reply.banned(PLAYER, "Steve_Miner", "grief", null));

        JoinVerdict verdict = gate.await("Steve_Miner", Codes.answered());

        assertEquals(JoinDecision.Kind.DENY_BANNED, verdict.decision().kind());
        assertEquals("You are banned from this server.", verdict.kickMessage().orElseThrow());
        assertTrue(log.text().contains("WARN could not build the kick message"), log.text());
    }

    private JoinGate gate() {
        return new JoinGate(
                new PlayerJoinCheck(settings, client, log),
                new JoinPolicy(settings),
                players,
                bans,
                Messages.load("en", ZoneId.of("UTC")),
                log);
    }

    @Test
    void savedBanCatchesKnownPlayerWithoutLauncherOrSite() {
        site.replyWith(Reply.confirmed(PLAYER, "Cached_Carl"));
        site.replyToBansWith(Reply.bans("2026-10-13T10:00:00Z", new Reply.BanEntry(PLAYER, "saved reason", null)));
        JoinGate gate = gate();
        assertEquals(
                JoinDecision.Kind.ALLOW,
                gate.await("Cached_Carl", Codes.answered()).decision().kind());
        bans.refresh().join();

        site.replyWith(Reply.notConfirmed());
        JoinVerdict withoutLauncher = gate.await("Cached_Carl", Codes.answered());
        site.replyWith(Reply.closedConnection());
        JoinVerdict siteDown = gate.await("Cached_Carl", Codes.answered());
        JoinVerdict stranger = gate.await("Someone_Else", Codes.answered());

        assertEquals(JoinDecision.Kind.DENY_BANNED, withoutLauncher.decision().kind());
        assertEquals(JoinDecision.Kind.DENY_BANNED, siteDown.decision().kind());
        assertEquals(JoinDecision.Kind.PASS, stranger.decision().kind());
        assertTrue(withoutLauncher.kickMessage().orElseThrow().contains("saved reason"));
    }
}
