package ru.mcmon.plugin.bukkit.login;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

final class Frames {

    private static final int MAX_VARINT_BYTES = 5;

    private Frames() {}

    static int readVarInt(ByteBuf in) {
        int value = 0;
        for (int i = 0; i < MAX_VARINT_BYTES; i++) {
            byte b = in.readByte();
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("varint is too long");
    }

    static String readString(ByteBuf in, int maxBytes) {
        int length = readVarInt(in);
        if (length < 0 || length > maxBytes || length > in.readableBytes()) {
            throw new IllegalArgumentException("string length out of range");
        }
        String value = in.toString(in.readerIndex(), length, StandardCharsets.UTF_8);
        in.skipBytes(length);
        return value;
    }

    static void writeVarInt(ByteBuf out, int value) {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            out.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte(remaining);
    }

    static void writeString(ByteBuf out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }
}
