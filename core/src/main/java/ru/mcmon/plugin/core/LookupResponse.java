package ru.mcmon.plugin.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

public final class LookupResponse {

    static final int MAX_BODY_LENGTH = 64 * 1024;

    private LookupResponse() {}

    public static Lookup parse(int status, String body, String requestedNick) {
        return switch (status) {
            case 200 -> parseBody(body, requestedNick);
            case 401 -> Lookup.of(LookupOutcome.INVALID_KEY);
            case 422 -> Lookup.of(LookupOutcome.REJECTED);
            case 429 -> Lookup.of(LookupOutcome.RATE_LIMITED);
            default -> Lookup.of(status >= 500 ? LookupOutcome.SITE_ERROR : LookupOutcome.BAD_RESPONSE);
        };
    }

    static Lookup parseBody(String body, String requestedNick) {
        if (body == null || body.length() > MAX_BODY_LENGTH) {
            return Lookup.of(LookupOutcome.BAD_RESPONSE);
        }
        try {
            JsonElement root = Json.parse(body);
            if (!root.isJsonObject()) {
                return Lookup.of(LookupOutcome.BAD_RESPONSE);
            }
            return fromObject(root.getAsJsonObject(), requestedNick);
        } catch (JsonParseException | IllegalStateException | DateTimeParseException e) {
            return Lookup.of(LookupOutcome.BAD_RESPONSE);
        }
    }

    private static Lookup fromObject(JsonObject object, String requestedNick) {
        Boolean confirmed = bool(object.get("confirmed"));
        if (confirmed == null) {
            return Lookup.of(LookupOutcome.BAD_RESPONSE);
        }
        if (!confirmed) {
            return Lookup.of(LookupOutcome.NOT_CONFIRMED);
        }
        String id = string(object.get("player"));
        String nick = string(object.get("nick"));
        if (id == null || !PlayerId.isValid(id.toLowerCase(Locale.ROOT)) || !Nick.isValid(nick)) {
            return Lookup.of(LookupOutcome.BAD_RESPONSE);
        }
        if (!nick.equalsIgnoreCase(requestedNick)) {
            return Lookup.of(LookupOutcome.BAD_RESPONSE);
        }
        return Lookup.confirmed(
                new ConfirmedPlayer(new PlayerId(id.toLowerCase(Locale.ROOT)), nick, ban(object.get("ban"))));
    }

    private static Optional<Ban> ban(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonObject()) {
            throw new IllegalStateException("ban must be an object or null");
        }
        JsonObject ban = element.getAsJsonObject();
        String reason = string(ban.get("reason"));
        JsonElement until = ban.get("until");
        Optional<Instant> end = until == null || until.isJsonNull()
                ? Optional.empty()
                : Optional.of(Instant.parse(requireString(until)));
        return Optional.of(new Ban(reason == null ? "" : reason, end));
    }

    private static Boolean bool(JsonElement element) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        return element.getAsBoolean();
    }

    private static String string(JsonElement element) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static String requireString(JsonElement element) {
        String value = string(element);
        if (value == null) {
            throw new IllegalStateException("expected a string");
        }
        return value;
    }
}
