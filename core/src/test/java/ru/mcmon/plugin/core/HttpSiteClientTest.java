package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mcmon.plugin.fakesite.FakeSite;
import ru.mcmon.plugin.fakesite.RecordedRequest;
import ru.mcmon.plugin.fakesite.Reply;

class HttpSiteClientTest {

    private static final String KEY = "secret-server-key-1234";
    private static final Duration TIMEOUT = Duration.ofMillis(400);

    private FakeSite site;
    private HttpSiteClient client;

    @BeforeEach
    void start() throws IOException {
        site = FakeSite.start(KEY);
        client = new HttpSiteClient(
                Settings.defaults()
                        .withServerKey(KEY)
                        .withSiteUrl(site.baseUrl())
                        .withTimeout(TIMEOUT),
                "test");
    }

    @AfterEach
    void stop() {
        client.shutdown();
        site.close();
    }

    @Test
    void sendsOnlyNickAndCodeWithKey() {
        site.replyWith(Reply.confirmed("7f3a91c202be4d10", "Steve_Miner"));

        Lookup lookup = client.lookup("Steve_Miner", Codes.code()).join();

        RecordedRequest request = site.lastRequest();
        assertEquals("Bearer " + KEY, request.authorization());
        assertEquals("Steve_Miner", request.nick());
        assertEquals(Codes.CODE, request.code());
        assertEquals(
                List.of("code", "nick"),
                request.json().keySet().stream().sorted().toList());
        assertTrue(lookup.confirmed());
        assertEquals("7f3a91c202be4d10", lookup.player().orElseThrow().id().hex());
    }

    @Test
    void garbageAndRedirectAreBadResponse() {
        site.replyWith(Reply.garbage());
        assertEquals(LookupOutcome.BAD_RESPONSE, lookup().outcome());

        site.replyWith(Reply.redirect(site.baseUrl() + HttpSiteClient.BANS_PATH));
        int before = site.requests().size();
        assertEquals(LookupOutcome.BAD_RESPONSE, lookup().outcome());
        assertEquals(before + 1, site.requests().size(), "the client followed the redirect");
    }

    @Test
    void hugeBodyIsCutOffAsBadResponse() {
        site.replyWith(Reply.huge(HttpSiteClient.MAX_BODY_BYTES * 4));

        assertEquals(LookupOutcome.BAD_RESPONSE, lookup().outcome());
    }

    @Test
    void slowSiteIsTimeoutWithinLimit() {
        site.replyWith(Reply.confirmed("7f3a91c202be4d10", "Steve_Miner").delayed(Duration.ofSeconds(5)));

        long started = System.nanoTime();
        Lookup lookup = lookup();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(LookupOutcome.TIMEOUT, lookup.outcome());
        assertTrue(elapsedMillis < TIMEOUT.toMillis() + HttpSiteClient.GRACE.toMillis() + 500, "took " + elapsedMillis);
    }

    @Test
    void rateLimitPausesRequestsForRetryAfter() {
        site.replyWith(Reply.rateLimited(30));
        assertEquals(LookupOutcome.RATE_LIMITED, lookup().outcome());
        site.replyWith(Reply.notConfirmed());

        assertEquals(LookupOutcome.RATE_LIMITED, lookup().outcome());
        assertEquals(1, site.lookups().size());
    }

    @Test
    void repeatedFailuresPauseRequests() {
        site.replyWith(Reply.closedConnection());
        for (int i = 0; i < SiteBackoff.FAILURES_BEFORE_PAUSE; i++) {
            assertEquals(LookupOutcome.UNREACHABLE, lookup().outcome());
        }
        site.replyWith(Reply.notConfirmed());

        assertEquals(LookupOutcome.UNREACHABLE, lookup().outcome());
        assertEquals(SiteBackoff.FAILURES_BEFORE_PAUSE, site.lookups().size());
    }

    private Lookup lookup() {
        return client.lookup("Steve_Miner", Codes.code()).join();
    }
}
