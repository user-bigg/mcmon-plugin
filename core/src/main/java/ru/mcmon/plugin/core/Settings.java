package ru.mcmon.plugin.core;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public final class Settings {

    public static final URI DEFAULT_SITE_URL = URI.create("https://mc-mon.ru");
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    public static final String DEFAULT_LANGUAGE = "ru";

    private final String serverKey;
    private final URI siteUrl;
    private final Duration timeout;
    private final String language;
    private final boolean requireLauncher;

    public Settings(String serverKey, URI siteUrl, Duration timeout, String language, boolean requireLauncher) {
        this.serverKey = Objects.requireNonNull(serverKey);
        this.siteUrl = Objects.requireNonNull(siteUrl);
        this.timeout = Objects.requireNonNull(timeout);
        this.language = Objects.requireNonNull(language);
        this.requireLauncher = requireLauncher;
    }

    public static Settings defaults() {
        return new Settings("", DEFAULT_SITE_URL, DEFAULT_TIMEOUT, DEFAULT_LANGUAGE, false);
    }

    public String serverKey() {
        return serverKey;
    }

    public URI siteUrl() {
        return siteUrl;
    }

    public Duration timeout() {
        return timeout;
    }

    public String language() {
        return language;
    }

    public boolean requireLauncher() {
        return requireLauncher;
    }

    public boolean hasServerKey() {
        return !serverKey.trim().isEmpty();
    }

    public boolean insecureSite() {
        return "http".equalsIgnoreCase(siteUrl.getScheme());
    }

    public Settings withServerKey(String key) {
        return new Settings(key, siteUrl, timeout, language, requireLauncher);
    }

    public Settings withSiteUrl(URI url) {
        return new Settings(serverKey, url, timeout, language, requireLauncher);
    }

    public Settings withTimeout(Duration value) {
        return new Settings(serverKey, siteUrl, value, language, requireLauncher);
    }

    public Settings withLanguage(String value) {
        return new Settings(serverKey, siteUrl, timeout, value, requireLauncher);
    }

    public Settings withRequireLauncher(boolean value) {
        return new Settings(serverKey, siteUrl, timeout, language, value);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Settings that)) {
            return false;
        }
        return serverKey.equals(that.serverKey)
                && siteUrl.equals(that.siteUrl)
                && timeout.equals(that.timeout)
                && language.equals(that.language)
                && requireLauncher == that.requireLauncher;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverKey, siteUrl, timeout, language, requireLauncher);
    }

    @Override
    public String toString() {
        return "Settings[siteUrl=" + siteUrl + ", timeout=" + timeout.toMillis() + "ms, language=" + language
                + ", requireLauncher=" + requireLauncher
                + ", serverKey=" + (hasServerKey() ? "set" : "empty") + "]";
    }
}
