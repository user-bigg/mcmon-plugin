package ru.mcmon.plugin.client;

public final class ServerTarget {

    static final int DEFAULT_PORT = 25565;

    private final String host;
    private final int port;

    private ServerTarget(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static ServerTarget of(String host, int port) {
        return new ServerTarget(host, port);
    }

    public static ServerTarget parseServerListEntry(String address) {
        String text = address.trim();
        String[] parts = text.split(":");
        if (text.startsWith("[")) {
            int end = text.indexOf(']');
            if (end > 0) {
                String rest = text.substring(end + 1).trim();
                parts = rest.startsWith(":")
                        ? new String[] {text.substring(1, end), rest.substring(1)}
                        : new String[] {text.substring(1, end)};
            }
        }
        if (parts.length > 2) {
            parts = new String[] {text};
        }
        int port = parts.length > 1 ? parsePort(parts[1]) : DEFAULT_PORT;
        return new ServerTarget(parts[0], port);
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }
}
