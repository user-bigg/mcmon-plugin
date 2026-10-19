package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsFileTest {

    @Test
    void writesTemplateAndReturnsDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plugins/MCMonitor/config.yml");

        Settings settings = SettingsFile.load(file);

        assertEquals(Settings.defaults(), settings);
        assertEquals(SettingsFile.TEMPLATE, Files.readString(file));
    }

    @Test
    void readsValuesWithQuotesAndComments() {
        Settings settings = SettingsFile.parse("""
                \ufeff# comment
                server-key: "abc:def" # trailing
                site-url: http://localhost:8089/   # dev
                timeout-ms: '500'
                language: EN
                unknown: whatever
                """);

        assertEquals("abc:def", settings.serverKey());
        assertEquals(URI.create("http://localhost:8089"), settings.siteUrl());
        assertEquals(Duration.ofMillis(500), settings.timeout());
        assertEquals("en", settings.language());
    }

    @Test
    void rejectsBadValuesByName() {
        assertTrue(assertThrows(SettingsException.class, () -> SettingsFile.parse("timeout-ms: soon"))
                .getMessage()
                .startsWith("timeout-ms"));
        assertTrue(assertThrows(SettingsException.class, () -> SettingsFile.parse("timeout-ms: 50"))
                .getMessage()
                .startsWith("timeout-ms"));
        assertTrue(assertThrows(SettingsException.class, () -> SettingsFile.parse("site-url: mc-mon.ru"))
                .getMessage()
                .startsWith("site-url"));
        assertTrue(assertThrows(SettingsException.class, () -> SettingsFile.parse("language: fr"))
                .getMessage()
                .startsWith("language"));
        assertTrue(
                assertThrows(SettingsException.class, () -> SettingsFile.parse("site-url: https://u:p@mc-mon.ru/?x=1"))
                        .getMessage()
                        .startsWith("site-url"));
        String keyError = assertThrows(SettingsException.class, () -> SettingsFile.parse("server-key: ключ сервера"))
                .getMessage();
        assertTrue(keyError.startsWith("server-key") && !keyError.contains("ключ"), keyError);
    }

    @Test
    void toStringHidesKey() {
        Settings settings = Settings.defaults().withServerKey("top-secret");

        assertFalse(settings.toString().contains("top-secret"));
    }
}
