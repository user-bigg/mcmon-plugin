package ru.mcmon.plugin.platform;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

final class MinecraftLogin {

    enum Outcome {
        LOGIN_OK,
        DISCONNECTED,
        CLOSED,
        TIMEOUT
    }

    // What the client does when the server asks for the launch code, like the MC Monitor mod or a game without it.
    record LaunchCodeReply(String code, boolean silent, Duration delay) {

        static final LaunchCodeReply NOT_UNDERSTOOD = new LaunchCodeReply(null, false, Duration.ZERO);
        static final LaunchCodeReply SILENT = new LaunchCodeReply(null, true, Duration.ZERO);

        static LaunchCodeReply code(String code) {
            return new LaunchCodeReply(code, false, Duration.ZERO);
        }

        // Like a game on a slow link: the answer reaches the server after it has stopped waiting.
        static LaunchCodeReply late(String code, Duration delay) {
            return new LaunchCodeReply(code, false, delay);
        }

        boolean late() {
            return !delay.isZero();
        }
    }

    record JoinResult(Outcome outcome, String reason, long millis, int codeRequests) {

        static JoinResult of(Outcome outcome, int codeRequests) {
            return new JoinResult(outcome, "", 0, codeRequests);
        }

        JoinResult timed(long started) {
            return new JoinResult(outcome, reason, (System.nanoTime() - started) / 1_000_000, codeRequests);
        }

        boolean disconnected() {
            return outcome == Outcome.DISCONNECTED;
        }
    }

    private static final int HANDSHAKE = 0x00;
    private static final int DISCONNECT = 0x00;
    private static final int SET_COMPRESSION = 0x03;
    private static final int LOGIN_PLUGIN_REQUEST = 0x04;
    private static final int LOGIN_PLUGIN_RESPONSE = 0x02;
    private static final String LAUNCH_CODE_CHANNEL = "mcmonitor:play";
    private static final int STATUS = 1;
    private static final int LOGIN = 2;
    private static final int PROTOCOL_1_19 = 759;
    private static final int PROTOCOL_1_19_1 = 760;
    private static final int PROTOCOL_1_19_3 = 761;
    private static final int PROTOCOL_1_20_2 = 764;
    private static final int STAYS_CONNECTED_MS = 2000;

    private MinecraftLogin() {}

    static int protocolVersion(String host, int port) throws IOException {
        try (Socket socket = connect(host, port)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            writePacket(out, HANDSHAKE, handshake(-1, host, port, STATUS));
            writePacket(out, 0x00, new byte[0]);
            byte[] response = readPacket(in);
            DataInputStream payload = new DataInputStream(new ByteArrayInputStream(response));
            readVarInt(payload);
            String json = readString(payload);
            return JsonParser.parseString(json)
                    .getAsJsonObject()
                    .getAsJsonObject("version")
                    .get("protocol")
                    .getAsInt();
        }
    }

    static JoinResult join(String host, int port, String nick, int protocol, LaunchCodeReply reply) throws IOException {
        return join(host, port, nick, protocol, reply, false);
    }

    // With BungeeCord forwarding the handshake carries the player's address and UUID, as BungeeCord writes them.
    static JoinResult join(
            String host, int port, String nick, int protocol, LaunchCodeReply reply, boolean bungeeForwarding)
            throws IOException {
        long started = System.nanoTime();
        String handshakeHost = bungeeForwarding
                ? host + "\0" + "203.0.113.7" + "\0"
                        + offlineUuid(nick).toString().replace("-", "")
                : host;
        return joinNow(host, port, handshakeHost, nick, protocol, reply).timed(started);
    }

    private static JoinResult joinNow(
            String host, int port, String handshakeHost, String nick, int protocol, LaunchCodeReply reply)
            throws IOException {
        try (Socket socket = connect(host, port)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            writePacket(out, HANDSHAKE, handshake(protocol, handshakeHost, port, LOGIN));
            writePacket(out, 0x00, loginStart(nick, protocol));
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            boolean compressed = false;
            int codeRequests = 0;
            try {
                for (int i = 0; i < 16; i++) {
                    DataInputStream payload = new DataInputStream(new ByteArrayInputStream(readPacket(in, compressed)));
                    int id = readVarInt(payload);
                    if (id == DISCONNECT) {
                        return new JoinResult(Outcome.DISCONNECTED, readString(payload), 0, codeRequests);
                    }
                    if (id == SET_COMPRESSION) {
                        compressed = true;
                        continue;
                    }
                    if (id == LOGIN_PLUGIN_REQUEST) {
                        int messageId = readVarInt(payload);
                        boolean ours = LAUNCH_CODE_CHANNEL.equals(readString(payload));
                        if (ours) {
                            codeRequests++;
                        }
                        if (ours && reply.silent()) {
                            continue;
                        }
                        if (ours && reply.late()) {
                            sleep(reply.delay());
                        }
                        // Arclight runs on Forge or NeoForge, which negotiate with modded clients here; a vanilla
                        // client answers "not understood".
                        byte[] answer = ours && reply.code() != null
                                ? pluginAnswer(messageId, reply.code())
                                : pluginNotUnderstood(messageId);
                        writePacket(out, LOGIN_PLUGIN_RESPONSE, answer, compressed);
                        continue;
                    }
                    return reply.late()
                            ? staysConnected(socket, in, compressed, codeRequests)
                            : JoinResult.of(Outcome.LOGIN_OK, codeRequests);
                }
                return JoinResult.of(Outcome.LOGIN_OK, codeRequests);
            } catch (SocketTimeoutException e) {
                return JoinResult.of(Outcome.TIMEOUT, codeRequests);
            } catch (IOException e) {
                // The server may close the connection first; the login event has fired by then.
                return JoinResult.of(Outcome.CLOSED, codeRequests);
            }
        }
    }

    // After Login Success a 1.20.2+ server waits for Login Acknowledged and sends nothing; a server that choked on
    // the late answer disconnects instead.
    private static JoinResult staysConnected(Socket socket, DataInputStream in, boolean compressed, int codeRequests)
            throws IOException {
        socket.setSoTimeout(STAYS_CONNECTED_MS);
        try {
            DataInputStream payload = new DataInputStream(new ByteArrayInputStream(readPacket(in, compressed)));
            return readVarInt(payload) == DISCONNECT
                    ? new JoinResult(Outcome.DISCONNECTED, readString(payload), 0, codeRequests)
                    : JoinResult.of(Outcome.LOGIN_OK, codeRequests);
        } catch (SocketTimeoutException e) {
            return JoinResult.of(Outcome.LOGIN_OK, codeRequests);
        }
    }

    private static void sleep(Duration delay) throws IOException {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static byte[] readPacket(DataInputStream in, boolean compressed) throws IOException {
        if (!compressed) {
            return readPacket(in);
        }
        int packetLength = readVarInt(in);
        byte[] frame = new byte[packetLength];
        in.readFully(frame);
        DataInputStream framed = new DataInputStream(new ByteArrayInputStream(frame));
        int dataLength = readVarInt(framed);
        byte[] rest = framed.readAllBytes();
        if (dataLength == 0) {
            return rest;
        }
        Inflater inflater = new Inflater();
        inflater.setInput(rest);
        byte[] data = new byte[dataLength];
        try {
            int done = 0;
            while (done < dataLength) {
                int n = inflater.inflate(data, done, dataLength - done);
                if (n == 0 && (inflater.finished() || inflater.needsInput())) {
                    throw new IOException("truncated compressed packet");
                }
                done += n;
            }
        } catch (DataFormatException e) {
            throw new IOException(e);
        } finally {
            inflater.end();
        }
        return data;
    }

    private static Socket connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(10000);
        return socket;
    }

    private static byte[] handshake(int protocol, String host, int port, int nextState) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        writeVarInt(data, protocol);
        writeString(data, host);
        data.writeShort(port);
        writeVarInt(data, nextState);
        return bytes.toByteArray();
    }

    private static byte[] loginStart(String nick, int protocol) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        writeString(data, nick);
        if (protocol >= PROTOCOL_1_20_2) {
            writeUuid(data, offlineUuid(nick));
        } else if (protocol >= PROTOCOL_1_19_3) {
            data.writeBoolean(false);
        } else if (protocol >= PROTOCOL_1_19_1) {
            data.writeBoolean(false);
            data.writeBoolean(false);
        } else if (protocol >= PROTOCOL_1_19) {
            data.writeBoolean(false);
        }
        return bytes.toByteArray();
    }

    private static byte[] pluginNotUnderstood(int messageId) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        writeVarInt(data, messageId);
        data.writeBoolean(false);
        return bytes.toByteArray();
    }

    private static byte[] pluginAnswer(int messageId, String code) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        writeVarInt(data, messageId);
        data.writeBoolean(true);
        data.writeByte(1);
        data.write(code.getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    static UUID offlineUuid(String nick) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static void writePacket(DataOutputStream out, int id, byte[] payload) throws IOException {
        writePacket(out, id, payload, false);
    }

    private static void writePacket(DataOutputStream out, int id, byte[] payload, boolean compressed)
            throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(packet);
        if (compressed) {
            writeVarInt(data, 0);
        }
        writeVarInt(data, id);
        data.write(payload);
        writeVarInt(out, packet.size());
        out.write(packet.toByteArray());
        out.flush();
    }

    private static byte[] readPacket(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] payload = new byte[length];
        in.readFully(payload);
        return payload;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > in.available()) {
            throw new IOException("bad string length " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            out.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte(remaining);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("varint too long");
            }
        }
    }
}
