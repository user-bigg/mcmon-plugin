package ru.mcmon.plugin.core;

import java.util.regex.Pattern;

public final class Nick {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private Nick() {}

    public static boolean isValid(String nick) {
        return nick != null && VALID.matcher(nick).matches();
    }
}
