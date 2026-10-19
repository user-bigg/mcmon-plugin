package ru.mcmon.plugin.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

final class Json {

    private Json() {}

    static JsonElement parse(String text) {
        JsonElement element = JsonParser.parseString(text);
        if (element.isJsonNull()) {
            throw new JsonParseException("empty document");
        }
        return element;
    }
}
