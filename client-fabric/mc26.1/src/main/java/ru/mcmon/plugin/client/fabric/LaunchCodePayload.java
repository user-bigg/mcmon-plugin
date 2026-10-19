package ru.mcmon.plugin.client.fabric;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;

public final class LaunchCodePayload implements CustomQueryAnswerPayload {

    private final byte[] bytes;

    public LaunchCodePayload(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        // The Netty method is the same in every version; the Minecraft overload of writeBytes may come and go.
        ByteBuf netty = buffer;
        netty.writeBytes(bytes);
    }
}
