package ru.mcmon.plugin.client;

public enum Refusal {
    NO_FILE("there is no play file from the launcher"),
    UNREADABLE("the play file cannot be read"),
    TOO_LARGE("the play file is larger than 4 KB"),
    MALFORMED("the play file is not valid"),
    UNKNOWN_VERSION("the play file has an unknown version"),
    INVALID_CODE("the play file holds no valid code"),
    OTHER_NICK("the play file is for another nickname"),
    NO_ADDRESS("the server address is unknown"),
    OTHER_SERVER("the play file is for another server");

    private final String reason;

    Refusal(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
