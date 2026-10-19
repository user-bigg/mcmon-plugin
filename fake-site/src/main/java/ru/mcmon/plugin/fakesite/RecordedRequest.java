package ru.mcmon.plugin.fakesite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public record RecordedRequest(
        String method, String path, String authorization, Map<String, String> headers, String body, JsonObject json) {

    static RecordedRequest of(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, String.join(",", values)));
        String body = FakeSite.readBody(exchange);
        return new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                headers.get("Authorization"),
                headers,
                body,
                parse(body));
    }

    public String nick() {
        return field("nick");
    }

    public String code() {
        return field("code");
    }

    private String field(String name) {
        if (json == null) {
            return null;
        }
        JsonElement value = json.get(name);
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static JsonObject parse(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }
}
