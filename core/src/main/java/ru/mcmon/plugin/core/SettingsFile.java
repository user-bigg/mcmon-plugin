package ru.mcmon.plugin.core;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SettingsFile {

    public static final String TEMPLATE = """
            # MC Monitor settings. Restart the server after editing.

            # Server key from the MC Monitor dashboard. Keep it secret.
            server-key: ""

            # MC Monitor site address. Change it only for testing.
            site-url: "https://mc-mon.ru"

            # How long to wait for the site, in milliseconds (100-5000). If the site is late, the player is handled by the server as usual; saved bans still apply.
            timeout-ms: 2000

            # Language of player messages: ru, en, es, pt, de. Texts are in the messages folder; the console is always in English.
            language: ru
            """;

    private static final Set<String> LANGUAGES = Set.of("ru", "en", "es", "pt", "de");
    private static final Duration MIN_TIMEOUT = Duration.ofMillis(100);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern KEY = Pattern.compile("[\\x21-\\x7E]{1,256}");
    private static final String BOM = "﻿";

    private SettingsFile() {}

    public static Settings load(Path file) throws IOException {
        if (Files.notExists(file)) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Utf8Files.write(file, TEMPLATE);
        }
        return parse(Utf8Files.read(file));
    }

    public static Settings parse(String text) {
        Map<String, String> values = FlatYaml.parse(text.startsWith(BOM) ? text.substring(BOM.length()) : text);
        Settings defaults = Settings.defaults();
        return new Settings(
                serverKey(values.getOrDefault("server-key", defaults.serverKey())),
                siteUrl(values.getOrDefault("site-url", defaults.siteUrl().toString())),
                timeout(values.getOrDefault(
                        "timeout-ms", Long.toString(defaults.timeout().toMillis()))),
                language(values.getOrDefault("language", defaults.language())));
    }

    private static String serverKey(String value) {
        String key = PlainText.strip(value);
        if (!key.isEmpty() && !KEY.matcher(key).matches()) {
            throw new SettingsException("server-key looks wrong, copy it from the MC Monitor dashboard again");
        }
        return key;
    }

    private static URI siteUrl(String value) {
        try {
            URI url = new URI(PlainText.strip(value));
            if (url.getHost() == null || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
                throw new SettingsException("site-url must be a full address like https://mc-mon.ru");
            }
            if (url.getUserInfo() != null || url.getRawQuery() != null || url.getRawFragment() != null) {
                throw new SettingsException(
                        "site-url must be a plain address like https://mc-mon.ru, without a login, ? or #");
            }
            String text = url.toString();
            return URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
        } catch (URISyntaxException e) {
            throw new SettingsException("site-url is not a valid address, use https://mc-mon.ru");
        }
    }

    private static Duration timeout(String value) {
        try {
            Duration timeout = Duration.ofMillis(Long.parseLong(PlainText.strip(value)));
            if (timeout.compareTo(MIN_TIMEOUT) < 0 || timeout.compareTo(MAX_TIMEOUT) > 0) {
                throw new SettingsException("timeout-ms must be between 100 and 5000");
            }
            return timeout;
        } catch (NumberFormatException e) {
            throw new SettingsException("timeout-ms must be a whole number, for example 2000");
        }
    }

    private static String language(String value) {
        String language = PlainText.strip(value).toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(language)) {
            throw new SettingsException("language must be one of: ru, en, es, pt, de");
        }
        return language;
    }
}
