package ru.mcmon.plugin.bukkit.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mcmon.plugin.core.CodeAnswer;
import ru.mcmon.plugin.core.Log;

class LoginWatcherTest {

    private static final String CODE = "Ab3dEf6hIj9kLm2nOp5qRs8tUv1wXy4z";
    private static final InetSocketAddress PLAYER = new InetSocketAddress("203.0.113.7", 50123);

    private final List<String> warnings = new ArrayList<>();
    private final List<Integer> decoded = new ArrayList<>();
    private Log log;
    private LaunchCodeQueries queries;
    private EmbeddedChannel channel;

    @BeforeEach
    void start() {
        log = new Log() {
            @Override
            public void info(String message) {}

            @Override
            public void warn(String message) {
                warnings.add(message);
            }
        };
        queries = new LaunchCodeQueries(false, false, log);
        channel = connection(PLAYER);
    }

    @Test
    void asksTheLoggingInPlayerAndTakesOnlyOurAnswer() {
        login(channel, "Steve_Miner");

        CompletableFuture<CodeAnswer> answer = queries.ask("steve_miner", PLAYER.getAddress());
        int queryId = readQuery(channel);
        channel.writeInbound(framed(answerFrame(queryId + 1, null)));
        assertFalse(answer.isDone());
        channel.writeInbound(framed(answerFrame(queryId, codeBody())));

        assertTrue(answer.join().toString().contains("code"), answer.join().toString());
        assertEquals(List.of(0x00, 0x00, 0x02), decoded);
        assertNull(channel.pipeline().get(LoginWatcher.NAME));
        assertNull(channel.pipeline().get(AnswerCatcher.NAME));
    }

    @Test
    void lateAnswerAfterCompressionIsOnIsTakenAwayFromTheServer() {
        login(channel, "Steve_Miner");
        CompletableFuture<CodeAnswer> answer = queries.ask("Steve_Miner", PLAYER.getAddress());
        int queryId = readQuery(channel);
        channel.pipeline().addAfter(LoginWatcher.SPLITTER, "decompress", new RejectsUncompressedFrames());

        ByteBuf late = framed(answerFrame(queryId, codeBody()));
        int split = late.readableBytes() / 2;
        channel.writeInbound(late.readRetainedSlice(split));
        channel.writeInbound(late);
        channel.writeInbound(framed(Unpooled.wrappedBuffer(new byte[] {0x00, 0x03})));

        assertTrue(answer.isDone());
        assertTrue(channel.isActive());
        assertEquals(List.of(0x00, 0x00, 0x03), decoded);
        assertNull(channel.pipeline().get(LoginWatcher.NAME));
    }

    @Test
    void strangerWithTheSameNickFromAnotherAddressDoesNotGetInTheWay() {
        EmbeddedChannel stranger = connection(new InetSocketAddress("198.51.100.9", 40000));
        login(stranger, "steve_miner");
        login(channel, "Steve_Miner");

        CompletableFuture<CodeAnswer> answer = queries.ask("Steve_Miner", PLAYER.getAddress());

        readQuery(channel);
        stranger.runPendingTasks();
        assertNull(stranger.readOutbound());
        assertFalse(answer.isDone());
    }

    @Test
    void twoLoginsWithOneNickFromOneAddressAreNotAsked() {
        EmbeddedChannel other = connection(new InetSocketAddress(PLAYER.getAddress(), 40000));
        login(channel, "Steve_Miner");
        login(other, "steve_miner");

        assertEquals(
                CodeAnswer.none(),
                queries.ask("Steve_Miner", PLAYER.getAddress()).join());
        channel.runPendingTasks();
        other.runPendingTasks();
        assertNull(channel.readOutbound());
        assertNull(other.readOutbound());
    }

    @Test
    void onlineModeAsksOnlyTheEncryptedLogin() {
        queries = new LaunchCodeQueries(false, true, log);
        EmbeddedChannel unencrypted = connection(new InetSocketAddress(PLAYER.getAddress(), 40000));
        login(unencrypted, "Steve_Miner");
        channel = connection(PLAYER);
        login(channel, "Steve_Miner");
        channel.pipeline().addBefore(LoginWatcher.SPLITTER, "decrypt", new ChannelInboundHandlerAdapter());

        CompletableFuture<CodeAnswer> answer = queries.ask("Steve_Miner", PLAYER.getAddress());

        readQuery(channel);
        unencrypted.runPendingTasks();
        assertNull(unencrypted.readOutbound());
        assertFalse(answer.isDone());
    }

    @Test
    void framesAfterLoginAcknowledgedKeepTheirOrder() {
        login(channel, "Steve_Miner");
        var unusedAnswer = queries.ask("Steve_Miner", PLAYER.getAddress());
        readQuery(channel);

        ByteBuf read = Unpooled.buffer();
        read.writeBytes(framed(Unpooled.wrappedBuffer(new byte[] {0x03})));
        read.writeBytes(framed(Unpooled.wrappedBuffer(new byte[] {0x00, 1, 2, 3})));
        read.writeBytes(framed(Unpooled.wrappedBuffer(new byte[] {0x02, 9, 9})));
        channel.writeInbound(read);

        assertEquals(List.of(0x00, 0x00, 0x03, 0x00, 0x02), decoded);
        assertNull(channel.pipeline().get(LoginWatcher.NAME));
        assertNull(channel.pipeline().get(AnswerCatcher.NAME));
    }

    @Test
    void unreadableFrameLetsTheWatcherGoWithoutReorderingTheStream() {
        login(channel, "Steve_Miner");
        var unusedAnswer = queries.ask("Steve_Miner", PLAYER.getAddress());
        readQuery(channel);

        ByteBuf read = Unpooled.buffer();
        read.writeBytes(framed(Unpooled.wrappedBuffer(new byte[] {(byte) 0x80})));
        read.writeBytes(framed(Unpooled.wrappedBuffer(new byte[] {0x00, 1})));
        channel.writeInbound(read);

        assertEquals(List.of(0x00, 0x00, 0x80, 0x00), decoded);
        assertNull(channel.pipeline().get(LoginWatcher.NAME));
    }

    @Test
    void beforeOneTwentyTwoPacketThreeDoesNotEndTheLogin() {
        login(channel, "Steve_Miner", 758);

        channel.writeInbound(framed(Unpooled.wrappedBuffer(new byte[] {0x03, 0x00})));

        assertNotNull(channel.pipeline().get(LoginWatcher.NAME));
    }

    @Test
    void answerMadeUpInsideTheServerIsTakenAfterTheSplitter() {
        login(channel, "Steve_Miner");
        CompletableFuture<CodeAnswer> answer = queries.ask("Steve_Miner", PLAYER.getAddress());
        int queryId = readQuery(channel);

        channel.pipeline().context(LoginWatcher.SPLITTER).fireChannelRead(answerFrame(queryId, null));

        assertEquals(CodeAnswer.none(), answer.join());
        assertEquals(List.of(0x00, 0x00), decoded);
        assertNull(channel.pipeline().get(LoginWatcher.NAME));
    }

    @Test
    void gameOlderThanOneThirteenIsNotAsked() {
        login(channel, "Steve_Miner", 340);

        assertEquals(
                CodeAnswer.none(),
                queries.ask("Steve_Miner", PLAYER.getAddress()).join());
        assertNull(channel.readOutbound());
    }

    @Test
    void bigFrameIsPassedOnAsItArrives() {
        login(channel, "Steve_Miner");
        var unusedAnswer = queries.ask("Steve_Miner", PLAYER.getAddress());
        readQuery(channel);

        ByteBuf header = Unpooled.buffer();
        Frames.writeVarInt(header, 100_000);
        channel.writeInbound(header);
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[1000]));

        assertEquals(3 + 1000, splitterBytes(channel));
    }

    @Test
    void loginFromAnotherAddressIsNotAsked() throws Exception {
        login(channel, "Steve_Miner");

        CompletableFuture<CodeAnswer> answer = queries.ask("Steve_Miner", InetAddress.getByName("198.51.100.9"));

        assertEquals(CodeAnswer.none(), answer.join());
        channel.runPendingTasks();
        assertNull(channel.readOutbound());
    }

    @Test
    void brokenStreamGoesOnToTheServerUntouched() {
        login(channel, "Steve_Miner");
        var unusedAnswer = queries.ask("Steve_Miner", PLAYER.getAddress());
        readQuery(channel);

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x01}));

        assertNull(channel.pipeline().get(AnswerCatcher.NAME));
        assertTrue(channel.isActive());
        assertEquals(4, splitterBytes(channel));
    }

    @Test
    void watcherLeavesAfterTheLoginPhaseEvenIfNeverAsked() {
        login(channel, "Steve_Miner");

        channel.advanceTimeBy(LoginWatcher.LOGIN_PHASE.toMillis() + 1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();

        assertNull(channel.pipeline().get(LoginWatcher.NAME));
        assertEquals(
                CodeAnswer.none(),
                queries.ask("Steve_Miner", PLAYER.getAddress()).join());
    }

    @Test
    void uninstallRemovesWatchersAndNewConnectionsGetNone() {
        login(channel, "Steve_Miner");

        queries.uninstall();
        channel.runPendingTasks();
        EmbeddedChannel later = connection(PLAYER);

        assertNull(channel.pipeline().get(LoginWatcher.NAME));
        assertNull(later.pipeline().get(LoginWatcher.NAME));
    }

    @Test
    void statusPingIsLeftAloneAndUnknownNickHasNoCode() {
        channel.writeInbound(framed(handshake(1, 774)));

        assertNull(channel.pipeline().get(LoginWatcher.NAME));
        assertEquals(
                CodeAnswer.none(),
                queries.ask("Steve_Miner", PLAYER.getAddress()).join());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void pipelineWithoutDecoderTurnsQueriesOffWithOneWarning() {
        EmbeddedChannel foreign = new EmbeddedChannel();
        foreign.pipeline().addLast(ChildHook.NAME, new ChildHook(queries));
        foreign.pipeline().fireChannelRegistered();
        foreign.pipeline().fireChannelRegistered();

        assertEquals(
                CodeAnswer.notAsked(),
                queries.ask("Steve_Miner", PLAYER.getAddress()).join());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    private EmbeddedChannel connection(InetSocketAddress remote) {
        EmbeddedChannel connection = new EmbeddedChannel() {
            @Override
            protected SocketAddress remoteAddress0() {
                return remote;
            }
        };
        connection.pipeline().addLast(ChildHook.NAME, new ChildHook(queries));
        connection.pipeline().addLast(LoginWatcher.SPLITTER, new Splitter());
        connection.pipeline().addLast("decoder", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ByteBuf frame = (ByteBuf) msg;
                decoded.add(frame.getByte(frame.readerIndex()) & 0xFF);
                frame.release();
            }
        });
        connection.pipeline().fireChannelRegistered();
        return connection;
    }

    private static void login(EmbeddedChannel channel, String nick) {
        login(channel, nick, 774);
    }

    private static void login(EmbeddedChannel channel, String nick, int protocol) {
        channel.writeInbound(framed(handshake(2, protocol)));
        ByteBuf hello = Unpooled.buffer();
        Frames.writeVarInt(hello, 0x00);
        Frames.writeString(hello, nick);
        hello.writeLong(0).writeLong(0);
        channel.writeInbound(framed(hello));
    }

    private static int readQuery(EmbeddedChannel channel) {
        channel.runPendingTasks();
        ByteBuf request = channel.readOutbound();
        assertEquals(0x04, Frames.readVarInt(request));
        int queryId = Frames.readVarInt(request);
        assertEquals("mcmonitor:play", Frames.readString(request, 64));
        assertEquals(0, request.readableBytes());
        request.release();
        return queryId;
    }

    private static int splitterBytes(EmbeddedChannel channel) {
        return ((Splitter) channel.pipeline().get(LoginWatcher.SPLITTER)).pendingBytes();
    }

    private static ByteBuf framed(ByteBuf payload) {
        ByteBuf frame = Unpooled.buffer();
        Frames.writeVarInt(frame, payload.readableBytes());
        frame.writeBytes(payload);
        payload.release();
        return frame;
    }

    private static ByteBuf handshake(int intent, int protocol) {
        ByteBuf frame = Unpooled.buffer();
        Frames.writeVarInt(frame, 0x00);
        Frames.writeVarInt(frame, protocol);
        Frames.writeString(frame, "play.example.ru");
        frame.writeShort(25565);
        Frames.writeVarInt(frame, intent);
        return frame;
    }

    private static ByteBuf answerFrame(int queryId, byte[] body) {
        ByteBuf frame = Unpooled.buffer();
        Frames.writeVarInt(frame, 0x02);
        Frames.writeVarInt(frame, queryId);
        frame.writeBoolean(body != null);
        if (body != null) {
            frame.writeBytes(body);
        }
        return frame;
    }

    private static byte[] codeBody() {
        byte[] body = new byte[33];
        body[0] = 1;
        System.arraycopy(CODE.getBytes(StandardCharsets.US_ASCII), 0, body, 1, 32);
        return body;
    }

    // Splits the stream by the length prefix, like the vanilla "splitter".
    private static final class Splitter extends ChannelInboundHandlerAdapter {

        private final ByteBuf stream = Unpooled.buffer();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf in = (ByteBuf) msg;
            stream.writeBytes(in);
            in.release();
            while (stream.isReadable()) {
                int start = stream.readerIndex();
                int length;
                try {
                    length = Frames.readVarInt(stream);
                } catch (RuntimeException e) {
                    stream.readerIndex(start);
                    return;
                }
                if (stream.readableBytes() < length) {
                    stream.readerIndex(start);
                    return;
                }
                ctx.fireChannelRead(stream.readRetainedSlice(length));
            }
        }

        int pendingBytes() {
            return stream.readableBytes();
        }
    }

    // Like vanilla "decompress": an uncompressed frame below the threshold ends the connection.
    private static final class RejectsUncompressedFrames extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf frame = (ByteBuf) msg;
            if (Frames.readVarInt(frame) != 0) {
                frame.release();
                var unused = ctx.close();
                return;
            }
            ctx.fireChannelRead(frame);
        }
    }
}
