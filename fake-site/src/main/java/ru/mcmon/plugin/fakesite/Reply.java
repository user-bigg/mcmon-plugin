package ru.mcmon.plugin.fakesite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record Reply(
        int status,
        String body,
        String contentType,
        Map<String, String> headers,
        Duration delay,
        boolean dropConnection) {

    public static Reply status(int status, String body) {
        return new Reply(status, body, "application/json", Map.of(), Duration.ZERO, false);
    }

    public static Reply confirmed(String playerId, String nick) {
        return status(200, lookup(playerId, nick, null));
    }

    public static Reply banned(String playerId, String nick, String reason, Instant until) {
        JsonObject ban = new JsonObject();
        ban.addProperty("reason", reason);
        if (until == null) {
            ban.add("until", null);
        } else {
            ban.addProperty("until", until.toString());
        }
        return status(200, lookup(playerId, nick, ban));
    }

    public static Reply redirect(String location) {
        return new Reply(302, "", "text/plain", Map.of("Location", location), Duration.ZERO, false);
    }

    public static Reply notConfirmed() {
        return status(200, "{\"confirmed\":false}");
    }

    public static Reply rateLimited(int retryAfterSeconds) {
        return new Reply(
                429,
                "{\"error\":\"too_many_attempts\"}",
                "application/json",
                Map.of("Retry-After", Integer.toString(retryAfterSeconds)),
                Duration.ZERO,
                false);
    }

    public record BanEntry(String player, String reason, Instant until) {}

    public static Reply bans(String updatedAt, BanEntry... entries) {
        return bans(updatedAt, false, entries);
    }

    public static Reply bans(String updatedAt, boolean requireLauncher, BanEntry... entries) {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (BanEntry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("player", entry.player());
            object.addProperty("reason", entry.reason());
            if (entry.until() == null) {
                object.add("until", null);
            } else {
                object.addProperty("until", entry.until().toString());
            }
            array.add(object);
        }
        root.add("bans", array);
        root.addProperty("updated_at", updatedAt);
        root.addProperty("require_launcher", requireLauncher);
        return status(200, root.toString());
    }

    public static Reply huge(int bytes) {
        return status(200, "{\"confirmed\":false,\"padding\":\"" + "x".repeat(bytes) + "\"}");
    }

    public static Reply garbage() {
        return new Reply(200, "<html>not json", "text/html", Map.of(), Duration.ZERO, false);
    }

    public static Reply closedConnection() {
        return new Reply(0, "", "", Map.of(), Duration.ZERO, true);
    }

    public Reply delayed(Duration delay) {
        return new Reply(status, body, contentType, headers, delay, dropConnection);
    }

    private static String lookup(String playerId, String nick, JsonObject ban) {
        JsonObject object = new JsonObject();
        object.addProperty("confirmed", true);
        object.addProperty("player", playerId);
        object.addProperty("nick", nick);
        object.add("ban", ban);
        return object.toString();
    }
}
