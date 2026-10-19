package ru.mcmon.plugin.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Messages {

    public static final List<String> LANGUAGES = List.of("ru", "en", "es", "pt", "de");
    public static final Set<String> KEYS = Set.of(
            "date-format", "banned", "banned-until", "banned-no-reason", "banned-until-no-reason", "launcher-required");

    private final Map<String, String> texts;
    private final Locale locale;
    private final ZoneId zone;

    private Messages(Map<String, String> texts, Locale locale, ZoneId zone) {
        this.texts = texts;
        this.locale = locale;
        this.zone = zone;
    }

    public static Messages load(String language, ZoneId zone) {
        return new Messages(builtIn(language), Locale.forLanguageTag(language), zone);
    }

    public static Messages load(Path dataFolder, String language, ZoneId zone, Log log) {
        Path folder = dataFolder.resolve("messages");
        for (String each : LANGUAGES) {
            copyIfMissing(folder.resolve(each + ".yml"), each, log);
        }
        Map<String, String> texts = new HashMap<>(builtIn(Settings.DEFAULT_LANGUAGE));
        texts.putAll(builtIn(language));
        Path file = folder.resolve(language + ".yml");
        try {
            if (Files.exists(file)) {
                Map<String, String> owner = FlatYaml.parse(Utf8Files.read(file));
                owner.keySet().retainAll(KEYS);
                texts.putAll(owner);
            }
        } catch (IOException e) {
            log.warn("messages/" + language + ".yml could not be read ("
                    + e.getClass().getSimpleName() + "), using built-in texts;" + " check that the file is UTF-8");
        }
        return new Messages(texts, Locale.forLanguageTag(language), zone);
    }

    static Messages of(Map<String, String> texts, String language, ZoneId zone) {
        return new Messages(texts, Locale.forLanguageTag(language), zone);
    }

    public String banned(Ban ban) {
        String key = ban.reason().isEmpty()
                ? (ban.permanent() ? "banned-no-reason" : "banned-until-no-reason")
                : (ban.permanent() ? "banned" : "banned-until");
        return text(key)
                .replace("{reason}", ban.reason())
                .replace("{until}", ban.until().map(this::format).orElse(""));
    }

    public String launcherRequired() {
        return text("launcher-required");
    }

    public String plain(String key) {
        return text(key);
    }

    public boolean has(String key) {
        return texts.containsKey(key);
    }

    private String text(String key) {
        String value = texts.get(key);
        if (value == null) {
            throw new IllegalStateException("message " + key + " is missing");
        }
        return value;
    }

    private String format(Instant until) {
        String pattern = texts.getOrDefault("date-format", "d MMMM yyyy, HH:mm z");
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern, locale);
        } catch (IllegalArgumentException e) {
            formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm z", locale);
        }
        return formatter.withZone(zone).format(until);
    }

    private static Map<String, String> builtIn(String language) {
        return FlatYaml.parse(resource(language));
    }

    private static void copyIfMissing(Path file, String language, Log log) {
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Utf8Files.write(file, resource(language));
        } catch (IOException e) {
            log.warn("could not create messages/" + language + ".yml ("
                    + e.getClass().getSimpleName() + "), using built-in texts");
        }
    }

    private static String resource(String language) {
        String path = "/messages/" + language + ".yml";
        try (InputStream in = Messages.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("messages for language " + language + " are missing");
            }
            return Utf8Files.read(in);
        } catch (IOException e) {
            throw new IllegalStateException("messages for language " + language + " could not be read", e);
        }
    }
}
