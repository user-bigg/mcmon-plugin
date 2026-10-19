package ru.mcmon.plugin.bukkit.login;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

// Added to an accepted channel before the server's own initializer; by channelRegistered that initializer has built
// the pipeline, so the watcher can go right before "decoder".
final class ChildHook extends ChannelInboundHandlerAdapter {

    static final String NAME = "mcmonitor_hook";
    private static final String DECODER = "decoder";

    private final LaunchCodeQueries queries;

    ChildHook(LaunchCodeQueries queries) {
        this.queries = queries;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        try {
            if (!queries.uninstalled()) {
                watch(pipeline);
            }
        } catch (RuntimeException | LinkageError e) {
            queries.cannotWatch();
        }
        ctx.fireChannelRegistered();
        pipeline.remove(this);
    }

    private void watch(ChannelPipeline pipeline) {
        if (pipeline.get(DECODER) != null && pipeline.get(LoginWatcher.SPLITTER) != null) {
            pipeline.addBefore(DECODER, LoginWatcher.NAME, new LoginWatcher(queries));
        } else {
            queries.cannotWatch();
        }
    }
}
