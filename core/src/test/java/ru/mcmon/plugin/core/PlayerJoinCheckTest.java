package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mcmon.plugin.fakesite.FakeSite;
import ru.mcmon.plugin.fakesite.Reply;

class PlayerJoinCheckTest {

    private static final String KEY = "very-secret-key-98765";

    private FakeSite site;
    private HttpSiteClient client;
    private RecordingLog log;
    private PlayerJoinCheck check;

    @BeforeEach
    void start() throws IOException {
        site = FakeSite.start(KEY);
        Settings settings = Settings.defaults()
                .withServerKey(KEY)
                .withSiteUrl(site.baseUrl())
                .withTimeout(Duration.ofMillis(300));
        client = new HttpSiteClient(settings, "test");
        log = new RecordingLog();
        check = new PlayerJoinCheck(settings, client, log);
    }

    @AfterEach
    void stop() {
        client.shutdown();
        site.close();
    }

    @Test
    void neitherTheGameNorTheSiteIsAskedWithoutServerKeyOrForInvalidNick() {
        PlayerJoinCheck noKey = new PlayerJoinCheck(Settings.defaults().withSiteUrl(site.baseUrl()), client, log);
        AtomicInteger asked = new AtomicInteger();
        Supplier<CompletableFuture<CodeAnswer>> askCode = () -> {
            asked.incrementAndGet();
            return Codes.answered().get();
        };

        assertEquals(
                LookupOutcome.NO_SERVER_KEY,
                noKey.check("Steve_Miner", askCode).join().outcome());
        assertEquals(
                LookupOutcome.INVALID_NICK,
                check.check("bad nick!", askCode).join().outcome());
        assertEquals(
                LookupOutcome.INVALID_NICK,
                check.check("too_long_nick_name_17", askCode).join().outcome());
        assertEquals(0, asked.get());
        assertTrue(site.requests().isEmpty());
    }

    @Test
    void codeGoesToTheSite() {
        site.replyWith(Reply.confirmed("7f3a91c202be4d10", "Steve_Miner"));

        Lookup lookup = check.check("Steve_Miner", Codes.answered()).join();

        assertTrue(lookup.confirmed());
        assertEquals(Codes.CODE, site.lastRequest().code());
    }

    @Test
    void withoutValidCodeTheSiteIsNotAsked() {
        assertEquals(
                LookupOutcome.NO_CODE,
                check.check("Steve_Miner", Codes.none()).join().outcome());
        assertEquals(
                LookupOutcome.NO_CODE,
                check.check("Steve_Miner", answered(Codes.answer("short")))
                        .join()
                        .outcome());
        assertEquals(
                LookupOutcome.CODE_UNAVAILABLE,
                check.check("Steve_Miner", () -> CompletableFuture.failedFuture(new NoSuchMethodError()))
                        .join()
                        .outcome());
        assertTrue(site.requests().isEmpty());
    }

    @Test
    void silentClientIsNoCodeAfterTimeout() {
        long started = System.nanoTime();
        Lookup lookup = check.check("Steve_Miner", CompletableFuture::new).join();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(LookupOutcome.NO_CODE, lookup.outcome());
        assertTrue(elapsedMillis >= 300 && elapsedMillis < 1000, "took " + elapsedMillis);
        assertTrue(site.requests().isEmpty());
    }

    @Test
    void hangingClientIsCutByHardLimit() {
        SiteClient hanging = (nick, code) -> new CompletableFuture<>();
        PlayerJoinCheck slow = new PlayerJoinCheck(
                Settings.defaults().withServerKey(KEY).withTimeout(Duration.ofMillis(100)), hanging, log);

        long started = System.nanoTime();
        Lookup lookup = slow.check("Steve_Miner", Codes.answered()).join();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(LookupOutcome.TIMEOUT, lookup.outcome());
        assertTrue(elapsedMillis < 2000, "took " + elapsedMillis);
    }

    @Test
    void clientFailuresBecomePluginErrorWithoutDetailsInLog() {
        SiteClient failingLater = (nick, code) -> CompletableFuture.failedFuture(new IllegalStateException(KEY));
        SiteClient failingNow = (nick, code) -> {
            throw new IllegalArgumentException("invalid header value: Bearer " + KEY + " " + code.value());
        };
        Settings settings = Settings.defaults().withServerKey(KEY);

        assertEquals(
                LookupOutcome.PLUGIN_ERROR,
                new PlayerJoinCheck(settings, failingLater, log)
                        .check("Steve_Miner", Codes.answered())
                        .join()
                        .outcome());
        assertEquals(
                LookupOutcome.PLUGIN_ERROR,
                new PlayerJoinCheck(settings, failingNow, log)
                        .check("Steve_Miner", Codes.answered())
                        .join()
                        .outcome());
        assertFalse(log.text().contains(KEY), log.text());
        assertFalse(log.text().contains(Codes.CODE), log.text());
    }

    private static Supplier<CompletableFuture<CodeAnswer>> answered(byte[] answer) {
        return Codes.given(CodeAnswer.answered(answer));
    }
}
