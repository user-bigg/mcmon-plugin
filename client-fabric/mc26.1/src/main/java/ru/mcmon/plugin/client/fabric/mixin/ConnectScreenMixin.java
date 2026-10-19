package ru.mcmon.plugin.client.fabric.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.mcmon.plugin.client.LaunchCode;
import ru.mcmon.plugin.client.ServerTarget;

// connect() takes the address before SRV resolution. The argument is picked by type, as in the jars for
// obfuscated versions, so a parameter added in a later 26.x release does not break the target.
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {

    @ModifyVariable(method = "connect", at = @At("HEAD"), argsOnly = true)
    private ServerAddress mcmonitor$rememberAddress(ServerAddress address) {
        LaunchCode.connecting(ServerTarget.of(address.getHost(), address.getPort()));
        return address;
    }
}
