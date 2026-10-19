package ru.mcmon.plugin.bukkit.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import ru.mcmon.plugin.core.CodeAnswer;
import ru.mcmon.plugin.core.LaunchCode;

// Sits right before the vanilla "decoder" and reads the handshake and the nick. The game's answer is taken by
// AnswerCatcher, which it puts before "splitter" when the plugin asks; an answer made up inside the server
// (ViaBackwards answers for games older than 1.13) enters the pipeline after "splitter" and is taken here.
final class LoginWatcher extends ChannelInboundHandlerAdapter {

    static final String NAME = "mcmonitor_play";
    static final String SPLITTER = "splitter";
    // Vanilla drops a login that takes longer than 600 ticks, so nothing of the login phase comes later than this.
    static final Duration LOGIN_PHASE = Duration.ofSeconds(30);
    private static final int HANDSHAKE = 0x00;
    private static final int HELLO = 0x00;
    private static final int CUSTOM_QUERY = 0x04;
    private static final int CUSTOM_QUERY_ANSWER = 0x02;
    private static final int LOGIN_ACKNOWLEDGED = 0x03;
    private static final int PROTOCOL_1_13 = 393;
    private static final int PROTOCOL_1_20_2 = 764;
    private static final String DECRYPT = "decrypt";
    private static final int UNCOMPRESSED = 0x00;
    private static final int INTENT_LOGIN = 2;
    private static final int INTENT_TRANSFER = 3;
    private static final int MAX_HOST_BYTES = 255 * 4;
    private static final int MAX_NICK_BYTES = 16 * 4;
    // A five-byte varint, so that no short packet of the stream can pass for the answer by chance.
    private static final int MIN_QUERY_ID = 1 << 28;

    private enum Stage {
        HANDSHAKE,
        HELLO,
        LOGGING_IN
    }

    private final LaunchCodeQueries queries;
    private final Duration loginPhase;
    private ChannelHandlerContext context;
    private Stage stage = Stage.HANDSHAKE;
    private volatile int protocol;
    private String nick;
    private int queryId;
    private boolean leaving;
    private CompletableFuture<CodeAnswer> answer;
    private ScheduledFuture<?> loginPhaseEnd;

    LoginWatcher(LaunchCodeQueries queries) {
        this(queries, LOGIN_PHASE);
    }

    LoginWatcher(LaunchCodeQueries queries, Duration loginPhase) {
        this.queries = queries;
        this.loginPhase = loginPhase;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        context = ctx;
        if (!queries.watching(this)) {
            ctx.executor().execute(this::detach);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (loginPhaseEnd != null) {
            loginPhaseEnd.cancel(false);
        }
        queries.done(this, nick);
        removeCatcher();
        settle(CodeAnswer.none());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        boolean consumed = false;
        if (msg instanceof ByteBuf frame) {
            try {
                consumed = read(frame.duplicate());
            } catch (RuntimeException | LinkageError e) {
                leaving = true;
            }
        }
        if (consumed) {
            ReferenceCountUtil.release(msg);
        } else {
            ctx.fireChannelRead(msg);
        }
        // Only after the frame has gone down: leaving earlier would let AnswerCatcher hand the rest of the stream
        // to "splitter" ahead of this frame.
        if (leaving) {
            detachQuietly();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        detach();
        ctx.fireChannelInactive();
    }

    CompletableFuture<CodeAnswer> ask() {
        if (protocol < PROTOCOL_1_13) {
            return CompletableFuture.completedFuture(CodeAnswer.none());
        }
        CompletableFuture<CodeAnswer> result = new CompletableFuture<>();
        context.executor().execute(() -> send(result));
        return result;
    }

    boolean encrypted() {
        return context.pipeline().get(DECRYPT) != null;
    }

    boolean connectsFrom(InetAddress address) {
        return !(context.channel().remoteAddress() instanceof InetSocketAddress remote)
                || remote.getAddress() == null
                || remote.getAddress().equals(address);
    }

    void detach() {
        if (context == null || context.isRemoved()) {
            return;
        }
        try {
            context.pipeline().remove(this);
        } catch (NoSuchElementException alreadyRemoved) {
            // Removed on the channel's own thread in the meantime.
        }
    }

    // After the plugin is unloaded its class loader may fail to load a class; the player must not pay for that.
    private void detachQuietly() {
        try {
            detach();
        } catch (RuntimeException | LinkageError e) {
            // The frame has already gone on to the server; the watcher just stays until the channel closes.
        }
    }

    void detachLater() {
        if (context != null) {
            context.executor().execute(this::detach);
        }
    }

    private void send(CompletableFuture<CodeAnswer> result) {
        ChannelPipeline pipeline = context.pipeline();
        if (context.isRemoved() || !context.channel().isActive() || answer != null) {
            result.complete(CodeAnswer.none());
            return;
        }
        if (pipeline.get(SPLITTER) == null) {
            result.complete(CodeAnswer.notAsked());
            return;
        }
        answer = result;
        queryId = ThreadLocalRandom.current().nextInt(MIN_QUERY_ID, Integer.MAX_VALUE);
        pipeline.addBefore(
                SPLITTER, AnswerCatcher.NAME, new AnswerCatcher(queryId, this::answered, this::removeCatcher));
        ByteBuf request = context.alloc().buffer();
        Frames.writeVarInt(request, CUSTOM_QUERY);
        Frames.writeVarInt(request, queryId);
        Frames.writeString(request, LaunchCode.CHANNEL);
        ChannelFuture unusedWrite = context.channel().writeAndFlush(request).addListener(this::onSent);
    }

    private void onSent(Future<? super Void> sent) {
        if (!sent.isSuccess()) {
            detach();
        }
    }

    private void answered(byte[] body) {
        settle(CodeAnswer.answered(body));
        detach();
    }

    private void removeCatcher() {
        try {
            if (context.pipeline().get(AnswerCatcher.NAME) != null) {
                context.pipeline().remove(AnswerCatcher.NAME);
            }
        } catch (RuntimeException alreadyRemoved) {
            // Removed on the channel's own thread in the meantime, or the channel is being torn down.
        }
    }

    private boolean read(ByteBuf frame) {
        switch (stage) {
            case HANDSHAKE -> readHandshake(frame);
            case HELLO -> readHello(frame);
            case LOGGING_IN -> {
                return readLogin(frame);
            }
        }
        return false;
    }

    private void readHandshake(ByteBuf frame) {
        if (Frames.readVarInt(frame) != HANDSHAKE) {
            throw new IllegalArgumentException("not a handshake");
        }
        protocol = Frames.readVarInt(frame);
        Frames.readString(frame, MAX_HOST_BYTES);
        frame.skipBytes(Short.BYTES);
        int intent = Frames.readVarInt(frame);
        if (intent != INTENT_LOGIN && intent != INTENT_TRANSFER) {
            throw new IllegalArgumentException("not a login");
        }
        stage = Stage.HELLO;
    }

    private void readHello(ByteBuf frame) {
        if (Frames.readVarInt(frame) != HELLO) {
            throw new IllegalArgumentException("not a login start");
        }
        nick = Frames.readString(frame, MAX_NICK_BYTES);
        stage = Stage.LOGGING_IN;
        loginPhaseEnd = context.executor().schedule(this::detach, loginPhase.toMillis(), TimeUnit.MILLISECONDS);
        queries.loggingIn(nick, this);
    }

    // Login Acknowledged ends the login phase on 1.20.2+; the frame may come with or without the compression header.
    // Older games have no such packet and play packets reuse its id, so there the watcher leaves by time.
    private boolean readLogin(ByteBuf frame) {
        int id = Frames.readVarInt(frame);
        if (id == UNCOMPRESSED && frame.isReadable()) {
            id = Frames.readVarInt(frame);
        }
        if (id == LOGIN_ACKNOWLEDGED && protocol >= PROTOCOL_1_20_2) {
            leaving = true;
        } else if (id == CUSTOM_QUERY_ANSWER && answer != null && Frames.readVarInt(frame) == queryId) {
            settle(CodeAnswer.answered(AnswerCatcher.body(frame)));
            leaving = true;
            return true;
        }
        return false;
    }

    private void settle(CodeAnswer value) {
        if (answer != null) {
            answer.complete(value);
        }
    }
}
