package ru.mcmon.plugin.client.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mcmon.plugin.client.Answer;
import ru.mcmon.plugin.client.LaunchCode;
import ru.mcmon.plugin.client.ServerTarget;
import ru.mcmon.plugin.client.fabric.LaunchCodeAnswers;
import ru.mcmon.plugin.client.fabric.LaunchCodePayload;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {

    @Shadow
    @Final
    private Connection connection;

    @Unique
    private ServerTarget mcmonitor$server;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mcmonitor$takeAddress(CallbackInfo callback) {
        mcmonitor$server = LaunchCode.takeConnecting();
    }

    @Inject(method = "handleCustomQuery", at = @At("HEAD"), cancellable = true)
    private void mcmonitor$answerLaunchCode(ClientboundCustomQueryPacket packet, CallbackInfo callback) {
        ServerboundCustomQueryAnswerPacket reply;
        Connection target;
        // On a game version this jar was not built for a mixin can be half applied; the resulting LinkageError
        // must not break the login, since every login query of the server passes through here. Once the query
        // is cancelled nothing is caught: the game must not send a second answer with the same transaction id.
        try {
            reply = mcmonitor$reply(packet);
            target = connection;
        } catch (RuntimeException | LinkageError e) {
            LaunchCodeAnswers.failed(e);
            return;
        }
        if (reply == null) {
            return;
        }
        callback.cancel();
        target.send(reply);
    }

    @Unique
    private ServerboundCustomQueryAnswerPacket mcmonitor$reply(ClientboundCustomQueryPacket packet) {
        ResourceLocation channel = packet.payload().id();
        if (!LaunchCode.isChannel(channel.getNamespace(), channel.getPath())) {
            return null;
        }
        Answer answer =
                LaunchCodeAnswers.answer(Minecraft.getInstance().getUser().getName(), mcmonitor$server);
        if (!answer.isSent()) {
            return null;
        }
        return new ServerboundCustomQueryAnswerPacket(packet.transactionId(), new LaunchCodePayload(answer.payload()));
    }
}
