package ru.mcmon.plugin.core;

public enum LookupOutcome {
    CONFIRMED("confirmed"),
    NOT_CONFIRMED("not confirmed"),
    NO_SERVER_KEY("server-key is empty"),
    INVALID_NICK("nickname is not a valid Minecraft name"),
    NO_CODE("no launch code from the game"),
    CODE_UNAVAILABLE("the server could not ask the game for a launch code"),
    BEHIND_PROXY("behind a proxy, launch codes are asked by the proxy"),
    INVALID_KEY("site rejected the server key"),
    RATE_LIMITED("site asked to slow down"),
    REJECTED("site refused the request"),
    TIMEOUT("no answer within timeout-ms"),
    UNREACHABLE("site unreachable"),
    CERTIFICATE_REJECTED("Java cannot verify the site's certificate, update Java"),
    SITE_ERROR("site error"),
    BAD_RESPONSE("unreadable answer from the site"),
    PLUGIN_ERROR("internal error, see the warning above");

    private final String description;

    LookupOutcome(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public boolean siteFailure() {
        return this == TIMEOUT || this == UNREACHABLE || this == CERTIFICATE_REJECTED || this == SITE_ERROR;
    }
}
