package ru.mcmon.plugin.bukkit.login;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

// Finds the listening channels by the type of what the fields hold, never by field or class names: those differ
// between Spigot and Mojang mappings and from one version to the next.
final class ServerChannels {

    private ServerChannels() {}

    static List<Channel> of(Object bukkitServer) throws ReflectiveOperationException {
        Object minecraftServer = bukkitServer.getClass().getMethod("getServer").invoke(bukkitServer);
        for (Object holder : fieldValues(minecraftServer)) {
            List<Channel> channels = listeningChannels(holder);
            if (!channels.isEmpty()) {
                return channels;
            }
        }
        return List.of();
    }

    private static List<Channel> listeningChannels(Object holder) {
        List<Channel> channels = new ArrayList<>();
        for (Object value : fieldValues(holder)) {
            if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ChannelFuture) {
                for (Object element : list.toArray()) {
                    if (element instanceof ChannelFuture future) {
                        channels.add(future.channel());
                    }
                }
            }
        }
        return channels;
    }

    private static List<Object> fieldValues(Object owner) {
        List<Object> values = new ArrayList<>();
        for (Class<?> type = owner.getClass(); type != null && !isJdk(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value != null) {
                        values.add(value);
                    }
                } catch (RuntimeException | IllegalAccessException e) {
                    // A field this JVM does not let us read cannot hold the server's network listener.
                }
            }
        }
        return values;
    }

    // Forge and NeoForge load the game as a named module, so only the JDK's own modules are skipped.
    private static boolean isJdk(Class<?> type) {
        return ModuleLayer.boot().modules().contains(type.getModule());
    }
}
