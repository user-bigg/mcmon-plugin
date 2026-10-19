package ru.mcmon.plugin.core;

public final class PlainText {

    private PlainText() {}

    public static String sanitize(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("\\s+", " ").replaceAll("\\p{C}", "");
        while (clean.indexOf('§') >= 0) {
            clean = clean.replaceAll("§.?", "");
        }
        return strip(truncate(strip(clean), maxLength));
    }

    static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return text.substring(0, end);
    }
}
