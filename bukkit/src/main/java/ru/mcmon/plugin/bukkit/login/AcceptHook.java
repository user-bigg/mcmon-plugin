package ru.mcmon.plugin.bukkit.login;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@ChannelHandler.Sharable
final class AcceptHook extends ChannelInboundHandlerAdapter {

    static final String NAME = "mcmonitor_accept";

    private final LaunchCodeQueries queries;

    AcceptHook(LaunchCodeQueries queries) {
        this.queries = queries;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Channel child) {
            try {
                if (!queries.uninstalled()) {
                    child.pipeline().addLast(ChildHook.NAME, new ChildHook(queries));
                }
            } catch (RuntimeException | LinkageError e) {
                queries.cannotWatch();
            }
        }
        ctx.fireChannelRead(msg);
    }
}
