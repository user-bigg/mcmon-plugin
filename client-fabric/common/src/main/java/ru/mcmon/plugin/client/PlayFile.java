package ru.mcmon.plugin.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;

final class PlayFile {

    static final int MAX_BYTES = 4096;
    private static final int MAX_HOST_LENGTH = 255;
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9]{32}");

    private final PlayTicket ticket;
    private final Refusal refusal;

    private PlayFile(PlayTicket ticket, Refusal refusal) {
        this.ticket = ticket;
        this.refusal = refusal;
    }

    static PlayFile read(Path file) {
        byte[] bytes;
        try {
            bytes = readAtMost(file, MAX_BYTES + 1);
        } catch (NoSuchFileException e) {
            return refused(Refusal.NO_FILE);
        } catch (IOException e) {
            return refused(Refusal.UNREADABLE);
        }
        if (bytes.length > MAX_BYTES) {
            return refused(Refusal.TOO_LARGE);
        }
        try {
            return parse(decode(bytes));
        } catch (CharacterCodingException | RuntimeException e) {
            return refused(Refusal.MALFORMED);
        }
    }

    PlayTicket ticket() {
        return ticket;
    }

    Refusal refusal() {
        return refusal;
    }

    private static PlayFile refused(Refusal refusal) {
        return new PlayFile(null, refusal);
    }

    private static byte[] readAtMost(Path file, int limit) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[limit];
            int total = 0;
            int read;
            while (total < limit && (read = in.read(buffer, total, limit - total)) > 0) {
                total += read;
            }
            return Arrays.copyOf(buffer, total);
        }
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static PlayFile parse(String text) {
        JsonObject json = new Gson().fromJson(text, JsonObject.class);
        if (json == null) {
            return refused(Refusal.MALFORMED);
        }
        JsonPrimitive version = primitive(json, "v");
        if (version == null || !version.isNumber() || !"1".equals(version.getAsString())) {
            return refused(Refusal.UNKNOWN_VERSION);
        }
        String nick = string(json, "nick");
        String host = string(json, "host");
        if (nick == null || host == null || host.isEmpty() || host.length() > MAX_HOST_LENGTH) {
            return refused(Refusal.MALFORMED);
        }
        String code = string(json, "code");
        if (code == null || !CODE.matcher(code).matches()) {
            return refused(Refusal.INVALID_CODE);
        }
        JsonElement portElement = json.get("port");
        Integer port = null;
        if (portElement != null && !portElement.isJsonNull()) {
            port = port(portElement);
            if (port == null) {
                return refused(Refusal.MALFORMED);
            }
        }
        return new PlayFile(new PlayTicket(nick, code, host, port), null);
    }

    private static JsonPrimitive primitive(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
    }

    private static String string(JsonObject json, String key) {
        JsonPrimitive value = primitive(json, key);
        return value != null && value.isString() ? value.getAsString() : null;
    }

    private static Integer port(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            int port = Integer.parseInt(element.getAsString());
            return port >= 1 && port <= 65535 ? port : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
