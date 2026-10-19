package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessagesTest {

    private static final Ban BAN = new Ban("grief at spawn", Optional.of(Instant.parse("2026-10-20T00:00:00Z")));

    @Test
    void everyLanguageIsCompleteWithPlaceholdersAndWithoutMarkup() {
        Map<String, String> placeholders = Map.of(
                "banned", "{reason}",
                "banned-until", "{until}",
                "banned-until-no-reason", "{until}");
        for (String language : Messages.LANGUAGES) {
            Messages messages = Messages.load(language, ZoneId.of("UTC"));
            for (String key : Messages.KEYS) {
                assertTrue(messages.has(key), language + " lacks " + key);
                assertFalse(messages.plain(key).contains("§"), language + " " + key + " has colour codes");
            }
            placeholders.forEach((key, placeholder) -> assertTrue(
                    messages.plain(key).contains(placeholder), language + " " + key + " lacks " + placeholder));
            assertTrue(messages.plain("banned-until").contains("{reason}"), language);
            String banned = messages.banned(BAN);
            assertTrue(banned.contains("grief at spawn") && banned.contains("2026"), language + ": " + banned);
        }
    }

    @Test
    void banMessageCarriesReasonAndLocalTimeInTheLanguage() {
        assertEquals(
                "You are banned from this server.\nReason: grief at spawn\nYour ban will be removed on 20 October 2026 at 03:00 MSK",
                Messages.load("en", ZoneId.of("Europe/Moscow")).banned(BAN));
        assertEquals(
                "Вы заблокированы на этом сервере.\nПричина: grief at spawn\nВаша блокировка будет снята 20 октября 2026 в 03:00 MSK",
                Messages.load("ru", ZoneId.of("Europe/Moscow")).banned(BAN));
    }

    @Test
    void ownerFileOverridesTextsAndMissingKeysFallBack(@TempDir Path dir) throws Exception {
        RecordingLog log = new RecordingLog();
        Messages.load(dir, "en", ZoneId.of("UTC"), log);
        for (String language : Messages.LANGUAGES) {
            assertTrue(Files.exists(dir.resolve("messages/" + language + ".yml")), language);
        }

        Files.writeString(
                dir.resolve("messages/en.yml"),
                "launcher-required: \"Launcher only!\"\nunknown-key: \"x\"\ndate-format: \"not a ]] pattern\"\n");
        Messages edited = Messages.load(dir, "en", ZoneId.of("UTC"), log);

        assertEquals("Launcher only!", edited.launcherRequired());
        assertEquals("You are banned from this server.", edited.banned(new Ban("", Optional.empty())));
        assertFalse(edited.has("unknown-key"));
        assertTrue(edited.banned(BAN).contains("20 October 2026"), edited.banned(BAN));
        assertTrue(log.lines.isEmpty(), log.text());
    }
}
