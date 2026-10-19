package ru.mcmon.plugin.client.fabric.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.mcmon.plugin.client.LaunchCode;
import ru.mcmon.plugin.client.ServerTarget;

// From 1.17 connect() takes the address before SRV resolution, but its signature grows across versions
// (ServerData in 1.19.3, CookieStorage in 1.20.5): the target is the name alone and the argument is picked by type.
// Up to 1.16.5 the same method takes the resolved host and port, so ConnectScreenMixins turns this mixin off there.
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {

    @ModifyVariable(method = "connect", at = @At("HEAD"), argsOnly = true)
    private ServerAddress mcmonitor$rememberAddress(ServerAddress address) {
        LaunchCode.connecting(ServerTarget.of(address.getHost(), address.getPort()));
        return address;
    }
}
