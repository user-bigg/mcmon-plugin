package ru.mcmon.plugin.client;

final class PlayTicket {

    private final String nick;
    private final String code;
    private final String host;
    private final Integer port;

    PlayTicket(String nick, String code, String host, Integer port) {
        this.nick = nick;
        this.code = code;
        this.host = host;
        this.port = port;
    }

    String nick() {
        return nick;
    }

    String code() {
        return code;
    }

    boolean isFor(ServerTarget target) {
        return HostNames.same(host, target.host()) && expectedPort() == target.port();
    }

    private int expectedPort() {
        return port == null ? ServerTarget.DEFAULT_PORT : port;
    }
}
