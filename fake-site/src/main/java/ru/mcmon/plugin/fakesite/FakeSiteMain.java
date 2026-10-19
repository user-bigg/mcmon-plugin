package ru.mcmon.plugin.fakesite;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

public final class FakeSiteMain {

    private FakeSiteMain() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8089;
        String key = System.getenv().getOrDefault("FAKE_SITE_KEY", "dev-key");
        FakeSite site = FakeSite.start(key, port, InetAddress.getByName("0.0.0.0"));
        site.replyWith(FakeSiteMain::reply);
        System.out.println("fake MC Monitor site on port " + port + ", server key: " + key);
        System.out.println(
                "nicks: Guest* -> not confirmed, Banned* -> banned, Slow* -> answer after 5 s, others -> confirmed");
        Thread.currentThread().join();
    }

    static Reply reply(RecordedRequest request) {
        String nick = request.nick();
        System.out.println("lookup " + nick);
        String lower = nick.toLowerCase(Locale.ROOT);
        if (lower.startsWith("guest")) {
            return Reply.notConfirmed();
        }
        if (lower.startsWith("banned")) {
            return Reply.banned(
                    playerId(nick), nick, "fake ban for testing", Instant.now().plus(Duration.ofDays(7)));
        }
        if (lower.startsWith("slow")) {
            return Reply.confirmed(playerId(nick), nick).delayed(Duration.ofSeconds(5));
        }
        return Reply.confirmed(playerId(nick), nick);
    }

    static String playerId(String nick) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(nick.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
