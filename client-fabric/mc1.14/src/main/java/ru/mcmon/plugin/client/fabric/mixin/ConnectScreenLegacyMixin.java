package ru.mcmon.plugin.client.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mcmon.plugin.client.LaunchCode;
import ru.mcmon.plugin.client.ServerTarget;

// Up to 1.16.5 the server list entry is resolved through SRV right in the constructor, so the address written in
// the list is taken before connect() receives the resolved one.
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenLegacyMixin {

    @Inject(
            method = "<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;"
                    + "Lnet/minecraft/client/multiplayer/ServerData;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/screens/ConnectScreen;connect(Ljava/lang/String;I)V"))
    private void mcmonitor$rememberListEntry(
            Screen parent, Minecraft minecraft, ServerData server, CallbackInfo callback) {
        LaunchCode.connecting(ServerTarget.parseServerListEntry(server.ip));
    }

    @Inject(
            method =
                    "<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/screens/ConnectScreen;connect(Ljava/lang/String;I)V"))
    private void mcmonitor$rememberAddress(
            Screen parent, Minecraft minecraft, String host, int port, CallbackInfo callback) {
        LaunchCode.connecting(ServerTarget.of(host, port));
    }
}
