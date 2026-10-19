package ru.mcmon.plugin.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class FlatYaml {

    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\r|\n");

    private FlatYaml() {}

    static Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : LINE_BREAK.split(text, -1)) {
            String line = PlainText.strip(rawLine);
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = PlainText.strip(line.substring(0, colon));
            String value = unquote(PlainText.strip(line.substring(colon + 1)));
            values.put(key, value);
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") || value.startsWith("'"))) {
            char quote = value.charAt(0);
            int end = value.indexOf(quote, 1);
            if (end > 0) {
                String inner = value.substring(1, end);
                return quote == '"' ? inner.replace("\\n", "\n") : inner;
            }
        }
        int comment = value.indexOf(" #");
        return comment >= 0 ? PlainText.strip(value.substring(0, comment)) : value;
    }
}
