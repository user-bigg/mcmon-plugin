package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LookupResponseTest {

    private static final String PLAYER = "7f3a91c202be4d10";
    private static final String NICK = "Steve_Miner";

    @Test
    void rejectsMalformedPlayerAndNick() {
        assertEquals(
                LookupOutcome.BAD_RESPONSE, body("{\"confirmed\":true,\"player\":\"7f3a\",\"nick\":\"" + NICK + "\"}"));
        assertEquals(
                LookupOutcome.BAD_RESPONSE,
                body("{\"confirmed\":true,\"player\":\"" + PLAYER + "\",\"nick\":\"Other\"}"));
        assertEquals(LookupOutcome.BAD_RESPONSE, body("{\"confirmed\":true,\"player\":\"" + PLAYER + "\"}"));
        assertEquals(LookupOutcome.BAD_RESPONSE, body("{\"confirmed\":\"true\"}"));
        assertEquals(LookupOutcome.BAD_RESPONSE, body("[]"));
    }

    @Test
    void acceptsUpperCasePlayerIdAndNickCase() {
        Lookup lookup = LookupResponse.parseBody(
                "{\"confirmed\":true,\"player\":\"7F3A91C202BE4D10\",\"nick\":\"steve_miner\",\"ban\":null}", NICK);

        assertEquals(PLAYER, lookup.player().orElseThrow().id().hex());
    }

    @Test
    void rejectsMalformedBan() {
        assertEquals(LookupOutcome.BAD_RESPONSE, confirmed("\"ban\":\"yes\""));
        assertEquals(LookupOutcome.BAD_RESPONSE, confirmed("\"ban\":{\"reason\":\"x\",\"until\":\"tomorrow\"}"));
        assertEquals(LookupOutcome.BAD_RESPONSE, confirmed("\"ban\":{\"reason\":\"x\",\"until\":5}"));
    }

    @Test
    void acceptsBanWithoutReasonOrUntil() {
        Lookup lookup = LookupResponse.parseBody(
                "{\"confirmed\":true,\"player\":\"" + PLAYER + "\",\"nick\":\"" + NICK + "\",\"ban\":{\"until\":null}}",
                NICK);

        Ban ban = lookup.player().orElseThrow().ban().orElseThrow();
        assertTrue(ban.permanent());
        assertEquals("", ban.reason());
    }

    @Test
    void banReasonIsPlainTextCutTo200() {
        String reason = "§4§l" + "a".repeat(150) + "\\n" + "a".repeat(150);

        Lookup lookup = LookupResponse.parseBody(
                "{\"confirmed\":true,\"player\":\"" + PLAYER + "\",\"nick\":\"" + NICK + "\",\"ban\":{\"reason\":\""
                        + reason + "\",\"until\":null}}",
                NICK);

        assertEquals(
                "a".repeat(150) + " " + "a".repeat(49),
                lookup.player().orElseThrow().ban().orElseThrow().reason());
    }

    @Test
    void mapsStatusCodes() {
        assertEquals(
                LookupOutcome.INVALID_KEY, LookupResponse.parse(401, "", NICK).outcome());
        assertEquals(LookupOutcome.REJECTED, LookupResponse.parse(422, "", NICK).outcome());
        assertEquals(
                LookupOutcome.RATE_LIMITED, LookupResponse.parse(429, "", NICK).outcome());
        assertEquals(
                LookupOutcome.SITE_ERROR, LookupResponse.parse(502, "", NICK).outcome());
        assertEquals(
                LookupOutcome.BAD_RESPONSE, LookupResponse.parse(404, "", NICK).outcome());
    }

    private static LookupOutcome body(String json) {
        return LookupResponse.parseBody(json, NICK).outcome();
    }

    private static LookupOutcome confirmed(String banField) {
        return body("{\"confirmed\":true,\"player\":\"" + PLAYER + "\",\"nick\":\"" + NICK + "\"," + banField + "}");
    }
}
