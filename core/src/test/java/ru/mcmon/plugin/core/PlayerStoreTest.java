package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerStoreTest {

    private static final PlayerId FIRST = new PlayerId("7f3a91c202be4d10");
    private static final PlayerId SECOND = new PlayerId("0123456789abcdef");

    @Test
    void survivesRestartKeepsRecentNumbersAndIgnoresNickCase(@TempDir Path dir) {
        Path file = dir.resolve("players.json");
        RecordingLog log = new RecordingLog();
        PlayerStore store = PlayerStore.open(file, log);
        store.rememberSeen("Steve_Miner", FIRST);
        store.rememberSeen("steve_miner", SECOND);
        store.rememberSeen("steve_miner", FIRST);

        KnownPlayer known = PlayerStore.open(file, log).find("Steve_Miner").orElseThrow();

        assertEquals(List.of(FIRST, SECOND), known.seen());
        assertTrue(log.lines.isEmpty(), log.text());
    }

    @Test
    void entriesOlderThanRetentionAreDropped(@TempDir Path dir) {
        Path file = dir.resolve("players.json");
        RecordingLog log = new RecordingLog();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PlayerStore.open(file, log, Clock.fixed(start, ZoneOffset.UTC)).rememberSeen("Old_Timer", FIRST);

        PlayerStore later = PlayerStore.open(
                file, log, Clock.fixed(start.plus(PlayerStore.KEEP).plusSeconds(1), ZoneOffset.UTC));

        assertEquals(0, later.size());
    }

    @Test
    void corruptedFileStartsEmptyWithWarning(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("players.json");
        Files.writeString(file, "{\"steve_miner\": {\"seen\": [\"nope\"]}, \"x\": 5, broken");
        RecordingLog log = new RecordingLog();

        PlayerStore store = PlayerStore.open(file, log);

        assertEquals(0, store.size());
        assertEquals(1, log.lines.size());
        assertTrue(log.lines.get(0).startsWith("WARN players.json"), log.text());
    }
}
