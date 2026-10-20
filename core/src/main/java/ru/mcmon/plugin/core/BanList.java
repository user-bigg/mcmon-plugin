package ru.mcmon.plugin.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BanList {

    public static final BanList EMPTY = new BanList(Collections.emptyMap(), Instant.EPOCH, false);

    private final Map<PlayerId, Ban> bans;
    private final Instant updatedAt;
    private final boolean requireLauncher;

    public BanList(Map<PlayerId, Ban> bans, Instant updatedAt, boolean requireLauncher) {
        this.bans = Collections.unmodifiableMap(new LinkedHashMap<>(bans));
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.requireLauncher = requireLauncher;
    }

    public Map<PlayerId, Ban> bans() {
        return bans;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean requireLauncher() {
        return requireLauncher;
    }

    public Optional<Ban> find(PlayerId id) {
        return Optional.ofNullable(bans.get(id));
    }

    public int size() {
        return bans.size();
    }

    public boolean newerOrSameAs(BanList other) {
        return !updatedAt.isBefore(other.updatedAt);
    }

    public static Optional<BanList> parse(String json) {
        try {
            JsonElement root = Json.parse(json);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement bans = object.get("bans");
            JsonElement updatedAt = object.get("updated_at");
            if (bans == null || !bans.isJsonArray() || !isString(updatedAt)) {
                return Optional.empty();
            }
            return Optional.of(new BanList(
                    entries(bans.getAsJsonArray()),
                    Instant.parse(updatedAt.getAsString()),
                    isTrue(object.get("require_launcher"))));
        } catch (JsonParseException | IllegalStateException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Map<PlayerId, Ban> entries(JsonArray array) {
        Map<PlayerId, Ban> bans = new LinkedHashMap<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement player = entry.get("player");
            JsonElement until = entry.get("until");
            if (!isString(player) || !PlayerId.isValid(player.getAsString().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Optional<Instant> end;
            try {
                if (until == null || until.isJsonNull()) {
                    end = Optional.empty();
                } else if (isString(until)) {
                    end = Optional.of(Instant.parse(until.getAsString()));
                } else {
                    continue;
                }
            } catch (DateTimeParseException e) {
                continue;
            }
            JsonElement reason = entry.get("reason");
            bans.put(
                    new PlayerId(player.getAsString().toLowerCase(Locale.ROOT)),
                    new Ban(isString(reason) ? reason.getAsString() : "", end));
        }
        return bans;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        bans.forEach((id, ban) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("player", id.hex());
            entry.addProperty("reason", ban.reason());
            entry.addProperty("until", ban.until().map(Instant::toString).orElse(null));
            array.add(entry);
        });
        root.add("bans", array);
        root.addProperty("updated_at", updatedAt.toString());
        root.addProperty("require_launcher", requireLauncher);
        return root.toString();
    }

    private static boolean isTrue(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static boolean isString(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BanList that)) {
            return false;
        }
        return bans.equals(that.bans) && updatedAt.equals(that.updatedAt) && requireLauncher == that.requireLauncher;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bans, updatedAt, requireLauncher);
    }

    @Override
    public String toString() {
        return "BanList[" + bans.size() + " bans as of " + updatedAt + ", requireLauncher=" + requireLauncher + "]";
    }
}
