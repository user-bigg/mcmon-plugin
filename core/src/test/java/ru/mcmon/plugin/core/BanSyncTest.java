package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mcmon.plugin.fakesite.FakeSite;
import ru.mcmon.plugin.fakesite.Reply;

class BanSyncTest {

    private static final String KEY = "bans-key-0000000000";
    private static final PlayerId CARL = new PlayerId("0123456789abcdef");

    private FakeSite site;
    private HttpSiteClient client;
    private RecordingLog log;
    private Path file;

    @BeforeEach
    void start(@TempDir Path dir) throws IOException {
        site = FakeSite.start(KEY);
        client = new HttpSiteClient(
                Settings.defaults()
                        .withServerKey(KEY)
                        .withSiteUrl(site.baseUrl())
                        .withTimeout(Duration.ofMillis(400)),
                "test");
        log = new RecordingLog();
        file = dir.resolve("bans.json");
    }

    @AfterEach
    void stop() {
        client.shutdown();
        site.close();
    }

    @Test
    void savesFetchedListAndReadsItBackWhenSiteIsGone() {
        site.replyToBansWith(Reply.bans("2026-10-13T10:00:00Z", new Reply.BanEntry(CARL.hex(), "grief", null)));
        BanSync sync = BanSync.open(client, file, log);

        sync.refresh().join();

        assertEquals("grief", sync.find(CARL).orElseThrow().reason());

        site.close();
        BanSync reopened = BanSync.open(client, file, log);
        reopened.refresh().join();
        assertEquals("grief", reopened.find(CARL).orElseThrow().reason());
        assertTrue(log.text().contains("WARN could not update bans"), log.text());
        assertFalse(log.text().contains(KEY));
    }

    @Test
    void keepsTheSitesRequireLauncherAcrossRestartWithoutSite() {
        site.replyToBansWith(Reply.bans("2026-10-13T10:00:00Z", true));
        BanSync sync = BanSync.open(client, file, log);

        sync.refresh().join();

        assertTrue(sync.current().requireLauncher());
        assertTrue(log.text().contains("INFO launcher-only mode turned on by the site"), log.text());
        site.close();
        assertTrue(BanSync.open(client, file, log).current().requireLauncher());
    }

    @Test
    void appliesAnyValidListKeepsSavedOneOnBrokenAnswersAndWarnsOnce() {
        site.replyToBansWith(Reply.bans("2026-10-13T10:00:00Z", new Reply.BanEntry(CARL.hex(), "grief", null)));
        BanSync sync = BanSync.open(client, file, log);
        sync.refresh().join();
        assertTrue(log.text().contains("INFO connected to the site: 1 ban as of"), log.text());

        site.replyToBansWith(Reply.bans("2026-10-13T09:00:00Z"));
        sync.refresh().join();
        assertTrue(sync.find(CARL).isEmpty(), "an older but valid list must still apply after a clock rollback");
        assertTrue(log.text().contains("sent an older ban list"), log.text());
        site.replyToBansWith(Reply.bans("2026-10-13T10:00:00Z", new Reply.BanEntry(CARL.hex(), "grief", null)));
        sync.refresh().join();

        site.replyToBansWith(Reply.garbage());
        sync.refresh().join();
        site.replyToBansWith(Reply.status(401, "{}"));
        sync.refresh().join();
        sync.refresh().join();
        assertTrue(sync.find(CARL).isPresent(), "broken answer dropped the saved list");
        assertEquals(
                3, log.lines.stream().filter(line -> line.startsWith("WARN")).count(), log.text());

        site.replyToBansWith(Reply.bans("2026-10-13T11:00:00Z"));
        sync.refresh().join();
        assertTrue(sync.find(CARL).isEmpty(), "unban was not applied");
        assertTrue(log.lines.get(log.lines.size() - 1).startsWith("INFO ban list updated: 0 bans"), log.text());
    }
}
