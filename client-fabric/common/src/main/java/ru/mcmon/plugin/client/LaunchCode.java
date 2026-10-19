package ru.mcmon.plugin.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class LaunchCode {

    public static final String CHANNEL_NAMESPACE = "mcmonitor";
    public static final String CHANNEL_PATH = "play";
    private static final byte ANSWER_VERSION = 1;

    private static final AtomicReference<ServerTarget> connecting = new AtomicReference<>();

    private LaunchCode() {}

    public static boolean isChannel(String namespace, String path) {
        return CHANNEL_NAMESPACE.equals(namespace) && CHANNEL_PATH.equals(path);
    }

    public static void connecting(ServerTarget target) {
        connecting.set(target);
    }

    public static ServerTarget takeConnecting() {
        return connecting.getAndSet(null);
    }

    public static Answer answer(Path gameDir, String sessionNick, ServerTarget target) {
        return answerFrom(gameDir.resolve("mcmonitor").resolve("play.json"), sessionNick, target);
    }

    static Answer answerFrom(Path playFile, String sessionNick, ServerTarget target) {
        PlayFile file = PlayFile.read(playFile);
        if (file.refusal() != null) {
            return Answer.refused(file.refusal());
        }
        PlayTicket ticket = file.ticket();
        if (!ticket.nick().equals(sessionNick)) {
            return Answer.refused(Refusal.OTHER_NICK);
        }
        if (target == null) {
            return Answer.refused(Refusal.NO_ADDRESS);
        }
        if (!ticket.isFor(target)) {
            return Answer.refused(Refusal.OTHER_SERVER);
        }
        byte[] code = ticket.code().getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[code.length + 1];
        payload[0] = ANSWER_VERSION;
        System.arraycopy(code, 0, payload, 1, code.length);
        return Answer.sent(payload);
    }
}
