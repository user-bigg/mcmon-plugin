package ru.mcmon.plugin.core;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class LaunchCode {

    public static final String CHANNEL_NAMESPACE = "mcmonitor";
    public static final String CHANNEL_PATH = "play";
    public static final String CHANNEL = CHANNEL_NAMESPACE + ":" + CHANNEL_PATH;
    static final int LENGTH = 32;
    private static final byte ANSWER_VERSION = 1;

    private final String value;

    private LaunchCode(String value) {
        this.value = value;
    }

    public static Optional<LaunchCode> fromAnswer(byte[] answer) {
        if (answer == null || answer.length != LENGTH + 1 || answer[0] != ANSWER_VERSION) {
            return Optional.empty();
        }
        for (int i = 1; i < answer.length; i++) {
            if (!isCodeChar(answer[i])) {
                return Optional.empty();
            }
        }
        return Optional.of(new LaunchCode(new String(answer, 1, LENGTH, StandardCharsets.US_ASCII)));
    }

    private static boolean isCodeChar(byte b) {
        return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9');
    }

    String value() {
        return value;
    }

    @Override
    public String toString() {
        return "LaunchCode[hidden]";
    }
}
