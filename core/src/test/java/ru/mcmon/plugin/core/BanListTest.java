package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BanListTest {

    @Test
    void keepsValidEntriesAndSkipsBrokenOnes() {
        BanList list = BanList.parse("""
                {"bans": [
                  {"player": "7F3A91C202BE4D10", "reason": "§cgrief", "until": "2026-10-20T00:00:00Z"},
                  {"player": "0123456789abcdef", "reason": null, "until": null},
                  {"player": "short", "reason": "x", "until": null},
                  {"player": "fedcba9876543210", "reason": "x", "until": 5},
                  {"player": "fedcba9876543211", "reason": "x", "until": "soon"},
                  "junk"
                ], "updated_at": "2026-10-13T10:00:00Z"}
                """).orElseThrow();

        assertEquals(2, list.size());
        assertEquals(
                new Ban("grief", Optional.of(Instant.parse("2026-10-20T00:00:00Z"))),
                list.find(new PlayerId("7f3a91c202be4d10")).orElseThrow());
        assertTrue(list.find(new PlayerId("0123456789abcdef")).orElseThrow().permanent());
        assertEquals(list, BanList.parse(list.toJson()).orElseThrow());
    }

    @Test
    void rejectsMalformedList() {
        assertTrue(BanList.parse("[]").isEmpty());
        assertTrue(BanList.parse("{\"bans\": {}, \"updated_at\": \"2026-10-13T10:00:00Z\"}")
                .isEmpty());
        assertTrue(
                BanList.parse("{\"bans\": [], \"updated_at\": \"yesterday\"}").isEmpty());
        assertTrue(BanList.parse("{\"bans\": []}").isEmpty());
        assertTrue(BanList.parse("not json").isEmpty());
    }
}
