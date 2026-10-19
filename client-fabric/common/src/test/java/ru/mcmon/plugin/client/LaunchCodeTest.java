package ru.mcmon.plugin.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LaunchCodeTest {

    private static final String CODE = "AbCdEfGhIjKlMnOpQrStUvWxYz012345";

    @TempDir
    Path dir;

    @Test
    void answersVersionByteAndCodeForTheServerInTheFile() throws IOException {
        Answer answer =
                answer(file("\"Play.Example.RU.\"", "25565"), "Test_User", ServerTarget.of("play.example.ru", 25565));

        byte[] expected = new byte[33];
        expected[0] = 1;
        System.arraycopy(CODE.getBytes(StandardCharsets.US_ASCII), 0, expected, 1, 32);
        assertTrue(answer.isSent());
        assertArrayEquals(expected, answer.payload());
    }

    @Test
    void comparesInternationalNamesAsPunycode() throws IOException {
        assertSent(file("\"Сервер.РФ\"", "null"), ServerTarget.of("xn--b1afb6bcb.xn--p1ai", 25565));
        assertSent(file("\"xn--b1afb6bcb.xn--p1ai\"", "null"), ServerTarget.parseServerListEntry("сервер.рф"));
    }

    @Test
    void comparesIpv6WithoutBracketsAndInAnyNotation() throws IOException {
        assertSent(file("\"2001:db8::1\"", "25570"), ServerTarget.parseServerListEntry("[2001:DB8:0:0::1]:25570"));
    }

    @Test
    void portNullMeansTheDefaultPortAndAGivenPortMustMatch() throws IOException {
        Path withoutPort = file("\"play.example.ru\"", "null");
        assertSent(withoutPort, ServerTarget.of("play.example.ru", 25565));
        assertRefused(Refusal.OTHER_SERVER, withoutPort, "Test_User", ServerTarget.of("play.example.ru", 25570));
        assertRefused(
                Refusal.OTHER_SERVER,
                file("\"play.example.ru\"", "25565"),
                "Test_User",
                ServerTarget.of("play.example.ru", 25566));
    }

    @Test
    void refusesAnotherServerAnotherNickAndAnUnknownAddress() throws IOException {
        Path file = file("\"play.example.ru\"", "null");
        assertRefused(Refusal.OTHER_SERVER, file, "Test_User", ServerTarget.of("play.example.ru.evil.net", 25565));
        assertRefused(Refusal.OTHER_NICK, file, "test_user", ServerTarget.of("play.example.ru", 25565));
        assertRefused(Refusal.NO_ADDRESS, file, "Test_User", null);
    }

    @Test
    void anAddressGoesToTheFirstConnectionOnlyEvenIfItNeverAsks() throws IOException {
        LaunchCode.takeConnecting();
        LaunchCode.connecting(ServerTarget.of("play.example.ru", 25565));
        ServerTarget first = LaunchCode.takeConnecting();
        ServerTarget second = LaunchCode.takeConnecting();

        Path file = file("\"play.example.ru\"", "null");
        assertSent(file, first);
        assertRefused(Refusal.NO_ADDRESS, file, "Test_User", second);
    }

    @Test
    void refusesMissingBrokenAndOversizedFiles() throws IOException {
        ServerTarget target = ServerTarget.of("play.example.ru", 25565);
        assertRefused(Refusal.NO_FILE, dir.resolve("absent.json"), "Test_User", target);
        assertRefused(Refusal.MALFORMED, write("{\"v\": 1, \"nick\": "), "Test_User", target);
        assertRefused(Refusal.MALFORMED, write(new byte[] {'{', (byte) 0xC3, '}'}), "Test_User", target);
        assertRefused(Refusal.MALFORMED, file("\"play.example.ru\"", "70000"), "Test_User", target);
        byte[] large = new byte[PlayFile.MAX_BYTES + 1];
        Arrays.fill(large, (byte) ' ');
        assertRefused(Refusal.TOO_LARGE, write(large), "Test_User", target);
    }

    @Test
    void refusesUnknownVersionAndInvalidCode() throws IOException {
        ServerTarget target = ServerTarget.of("play.example.ru", 25565);
        assertRefused(Refusal.UNKNOWN_VERSION, write(json("2", "\"" + CODE + "\"")), "Test_User", target);
        assertRefused(Refusal.UNKNOWN_VERSION, write(json("\"1\"", "\"" + CODE + "\"")), "Test_User", target);
        assertRefused(Refusal.INVALID_CODE, write(json("1", "\"" + CODE.substring(1) + "!\"")), "Test_User", target);
        assertRefused(Refusal.INVALID_CODE, write(json("1", "\"" + CODE + "0\"")), "Test_User", target);
    }

    @Test
    void readsServerListEntriesLikeTheGame() {
        assertTarget("play.example.ru", 25565, ServerTarget.parseServerListEntry(" play.example.ru "));
        assertTarget("play.example.ru", 25566, ServerTarget.parseServerListEntry("play.example.ru:25566"));
        assertTarget("play.example.ru", 25565, ServerTarget.parseServerListEntry("play.example.ru:port"));
        assertTarget("::1", 25566, ServerTarget.parseServerListEntry("[::1]:25566"));
        assertTarget("2001:db8::1", 25565, ServerTarget.parseServerListEntry("2001:db8::1"));
    }

    private void assertSent(Path file, ServerTarget target) {
        assertTrue(answer(file, "Test_User", target).isSent());
    }

    private static void assertRefused(Refusal expected, Path file, String nick, ServerTarget target) {
        assertEquals(expected, answer(file, nick, target).refusal());
    }

    private static void assertTarget(String host, int port, ServerTarget target) {
        assertEquals(host, target.host());
        assertEquals(port, target.port());
    }

    private static Answer answer(Path file, String nick, ServerTarget target) {
        return LaunchCode.answerFrom(file, nick, target);
    }

    private Path file(String host, String port) throws IOException {
        return write("{\"v\": 1, \"server_id\": 68, \"nick\": \"Test_User\", \"code\": \"" + CODE + "\", \"host\": "
                + host + ", \"port\": " + port + "}");
    }

    private static String json(String version, String code) {
        return "{\"v\": " + version + ", \"nick\": \"Test_User\", \"code\": " + code
                + ", \"host\": \"play.example.ru\", \"port\": null}";
    }

    private Path write(String text) throws IOException {
        return write(text.getBytes(StandardCharsets.UTF_8));
    }

    private Path write(byte[] bytes) throws IOException {
        Path file = Files.createTempFile(dir, "play", ".json");
        Files.write(file, bytes);
        return file;
    }
}
