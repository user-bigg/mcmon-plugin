package ru.mcmon.plugin.core;

import java.util.regex.Pattern;

public record PlayerId(String hex) {

    private static final Pattern VALID = Pattern.compile("[0-9a-f]{16}");

    public PlayerId {
        if (!isValid(hex)) {
            throw new IllegalArgumentException("player number must be 16 lowercase hex digits");
        }
    }

    public static boolean isValid(String hex) {
        return hex != null && VALID.matcher(hex).matches();
    }

    @Override
    public String toString() {
        return hex;
    }
}
