package ru.mcmon.plugin.client.fabric.mixin;

import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundCustomQueryPacket.class)
public interface CustomQueryPacketAccessor {

    @Accessor("transactionId")
    int mcmonitor$transactionId();

    @Accessor("identifier")
    ResourceLocation mcmonitor$channel();
}
