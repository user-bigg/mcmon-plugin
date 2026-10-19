package ru.mcmon.plugin.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PlayerStore {

    static final Duration KEEP = Duration.ofDays(180);

    private final Path file;
    private final Log log;
    private final Clock clock;
    private final Map<String, KnownPlayer> players = new LinkedHashMap<>();

    private PlayerStore(Path file, Log log, Clock clock) {
        this.file = file;
        this.log = log;
        this.clock = clock;
    }

    public static PlayerStore open(Path file, Log log) {
        return open(file, log, Clock.systemUTC());
    }

    static PlayerStore open(Path file, Log log, Clock clock) {
        PlayerStore store = new PlayerStore(file, log, clock);
        store.read();
        return store;
    }

    public synchronized Optional<KnownPlayer> find(String nick) {
        return Optional.ofNullable(players.get(key(nick)));
    }

    public synchronized void rememberSeen(String nick, PlayerId id) {
        KnownPlayer known = players.get(key(nick));
        Instant now = clock.instant();
        KnownPlayer updated = known == null ? new KnownPlayer(List.of(id), now) : known.withSeen(id, now);
        if (known == null || !updated.seen().equals(known.seen())) {
            players.put(key(nick), updated);
            write();
        }
    }

    public synchronized int size() {
        return players.size();
    }

    private static String key(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }

    private void read() {
        if (Files.notExists(file)) {
            return;
        }
        try {
            JsonElement root = Json.parse(Utf8Files.read(file));
            Instant oldest = clock.instant().minus(KEEP);
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                KnownPlayer player = parse(entry.getValue());
                if (player != null
                        && Nick.isValid(entry.getKey())
                        && player.seenAt().isAfter(oldest)) {
                    players.put(key(entry.getKey()), player);
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            log.warn(file.getFileName() + " could not be read (" + e.getClass().getSimpleName()
                    + "), starting with an empty list;"
                    + " restore it from a backup if you have one, otherwise saved bans do not reach players"
                    + " who join without the launcher until they come through it again");
            players.clear();
        }
    }

    private static KnownPlayer parse(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        List<PlayerId> seen = new ArrayList<>();
        JsonElement seenElement = object.get("seen");
        if (seenElement != null && seenElement.isJsonArray()) {
            for (JsonElement id : seenElement.getAsJsonArray()) {
                String hex = text(id);
                if (PlayerId.isValid(hex) && seen.size() < KnownPlayer.MAX_SEEN) {
                    seen.add(new PlayerId(hex));
                }
            }
        }
        if (seen.isEmpty()) {
            return null;
        }
        Instant seenAt;
        try {
            String at = text(object.get("at"));
            seenAt = at == null ? Instant.EPOCH : Instant.parse(at);
        } catch (DateTimeParseException e) {
            return null;
        }
        return new KnownPlayer(seen, seenAt);
    }

    private static String text(JsonElement element) {
        return element != null
                        && element.isJsonPrimitive()
                        && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }

    private void write() {
        JsonObject root = new JsonObject();
        players.forEach((nick, player) -> {
            JsonObject object = new JsonObject();
            JsonArray seen = new JsonArray();
            player.seen().forEach(id -> seen.add(id.hex()));
            object.add("seen", seen);
            object.addProperty("at", player.seenAt().toString());
            root.add(nick, object);
        });
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Utf8Files.write(temp, root.toString());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn(file.getFileName() + " could not be saved (" + e.getClass().getSimpleName()
                    + "); check disk space and file" + " permissions in the MC Monitor folder");
        }
    }
}
