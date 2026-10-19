package ru.mcmon.plugin.bukkit.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.function.Consumer;

// Sits right before the vanilla "splitter" and frames the stream itself: the game sends its answer uncompressed,
// but a late answer may reach a server that has already switched compression on, and vanilla puts "decompress"
// right after "splitter", where an uncompressed frame ends the connection.
final class AnswerCatcher extends ChannelInboundHandlerAdapter {

    static final String NAME = "mcmonitor_answer";
    static final int MAX_ANSWER_BYTES = 64;
    private static final int CUSTOM_QUERY_ANSWER = 0x02;
    private static final int MAX_FRAME_LENGTH_BYTES = 3;
    // Packet id, query id, the "understood" flag and the body: no longer frame can be our answer.
    private static final int MAX_ANSWER_FRAME = 1 + 5 + 1 + MAX_ANSWER_BYTES;

    private final int queryId;
    private final Consumer<byte[]> onAnswer;
    private final Runnable onBrokenStream;
    private ByteBuf pending;
    private int passingThrough;
    private boolean done;
    private boolean reading;
    private boolean removed;

    AnswerCatcher(int queryId, Consumer<byte[]> onAnswer, Runnable onBrokenStream) {
        this.queryId = queryId;
        this.onAnswer = onAnswer;
        this.onBrokenStream = onBrokenStream;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (done || !(msg instanceof ByteBuf in)) {
            ctx.fireChannelRead(msg);
            return;
        }
        reading = true;
        try {
            ByteBuf previous = pending;
            pending = null;
            pending = previous == null ? in : joined(ctx, previous, in);
            forwardFrames(ctx);
        } catch (RuntimeException | LinkageError e) {
            done = true;
            forwardPending(ctx);
            onBrokenStream.run();
        } finally {
            reading = false;
        }
    }

    // A removal while a frame of ours is on its way down (the watcher leaves on Login Acknowledged) must not push
    // the rest of the stream past that frame; channelRead forwards it in order once the frame is through.
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        removed = true;
        if (reading) {
            return;
        }
        if (ctx.channel().isActive()) {
            forwardPending(ctx);
        } else if (pending != null) {
            pending.release();
            pending = null;
        }
    }

    private void forwardFrames(ChannelHandlerContext ctx) {
        while (pending != null && pending.isReadable()) {
            if (done || removed) {
                forwardPending(ctx);
                return;
            }
            if (passingThrough > 0) {
                int part = Math.min(passingThrough, pending.readableBytes());
                passingThrough -= part;
                ctx.fireChannelRead(pending.readRetainedSlice(part));
                continue;
            }
            int start = pending.readerIndex();
            int header = lengthBytes(pending);
            if (header < 0) {
                break;
            }
            int length = frameLength(pending, header);
            if (length > MAX_ANSWER_FRAME) {
                passingThrough = header + length;
                continue;
            }
            if (pending.readableBytes() < header + length) {
                break;
            }
            ByteBuf payload = pending.slice(start + header, length);
            if (isOurs(payload)) {
                byte[] body = body(payload);
                pending.skipBytes(header + length);
                done = true;
                forwardPending(ctx);
                onAnswer.accept(body);
                return;
            }
            ctx.fireChannelRead(pending.readRetainedSlice(header + length));
        }
        if (pending != null && !pending.isReadable()) {
            pending.release();
            pending = null;
        }
    }

    private void forwardPending(ChannelHandlerContext ctx) {
        ByteBuf rest = pending;
        pending = null;
        if (rest == null) {
            return;
        }
        if (rest.isReadable()) {
            ctx.fireChannelRead(rest);
        } else {
            rest.release();
        }
    }

    private static int lengthBytes(ByteBuf in) {
        for (int i = 0; i < MAX_FRAME_LENGTH_BYTES; i++) {
            if (in.readableBytes() <= i) {
                return -1;
            }
            if ((in.getByte(in.readerIndex() + i) & 0x80) == 0) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("frame length is too long");
    }

    private static int frameLength(ByteBuf in, int lengthBytes) {
        int value = 0;
        for (int i = 0; i < lengthBytes; i++) {
            value |= (in.getByte(in.readerIndex() + i) & 0x7F) << (7 * i);
        }
        return value;
    }

    static byte[] body(ByteBuf payload) {
        if (!payload.isReadable() || !payload.readBoolean() || payload.readableBytes() > MAX_ANSWER_BYTES) {
            return null;
        }
        byte[] body = new byte[payload.readableBytes()];
        payload.readBytes(body);
        return body;
    }

    private boolean isOurs(ByteBuf payload) {
        try {
            return Frames.readVarInt(payload) == CUSTOM_QUERY_ANSWER && Frames.readVarInt(payload) == queryId;
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return false;
        }
    }

    // Only a frame of at most MAX_ANSWER_FRAME bytes is ever held back, so a slowly arriving big frame costs no
    // repeated copying.
    private static ByteBuf joined(ChannelHandlerContext ctx, ByteBuf first, ByteBuf second) {
        try {
            ByteBuf joined = ctx.alloc().buffer(first.readableBytes() + second.readableBytes());
            joined.writeBytes(first).writeBytes(second);
            return joined;
        } finally {
            first.release();
            second.release();
        }
    }
}
