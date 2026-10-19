package ru.mcmon.plugin.client.fabric.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class ConnectScreenMixins implements IMixinConfigPlugin {

    private static final String LEGACY = ConnectScreenMixins.class.getPackage().getName() + ".ConnectScreenLegacyMixin";
    private static final String MODERN = ConnectScreenMixins.class.getPackage().getName() + ".ConnectScreenMixin";

    private boolean modern;

    @Override
    public void onLoad(String mixinPackage) {
        modern = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(ConnectScreenMixins::isModern)
                .orElse(false);
    }

    private static boolean isModern(ModContainer minecraft) {
        try {
            return VersionPredicate.parse(">=1.17-")
                    .test(minecraft.getMetadata().getVersion());
        } catch (VersionParsingException e) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(LEGACY)) {
            return !modern;
        }
        if (mixinClassName.equals(MODERN)) {
            return modern;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
