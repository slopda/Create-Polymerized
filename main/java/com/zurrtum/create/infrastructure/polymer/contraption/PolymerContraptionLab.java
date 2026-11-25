package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PolymerContraptionLab {
    public interface Hooks {
        boolean prepare(AbstractContraptionEntity owner);

        void updated(AbstractContraptionEntity owner);

        void tick(MinecraftServer server);

        boolean covers(AbstractContraptionEntity owner, BlockPos position);

        boolean controls(ServerPlayer player);

        boolean active(AbstractContraptionEntity owner);

        int nativeHelperCount(MinecraftServer server);

        void display(AbstractContraptionEntity owner, DisplayElement display);

        void removed(AbstractContraptionEntity owner);

        void stopping();
    }

    private static Hooks hooks;

    private PolymerContraptionLab() {}

    public static void install(Hooks installed) {
        if (hooks != null) throw new IllegalStateException("Contraption lab hooks already installed");
        hooks = installed;
    }

    static boolean prepare(AbstractContraptionEntity owner) {
        return hooks != null && hooks.prepare(owner);
    }

    static void updated(AbstractContraptionEntity owner) {
        if (hooks != null) hooks.updated(owner);
    }

    static void tick(MinecraftServer server) {
        if (hooks != null) hooks.tick(server);
    }

    static boolean covers(AbstractContraptionEntity owner, BlockPos position) {
        return hooks != null && hooks.covers(owner, position);
    }

    static boolean controls(ServerPlayer player) {
        return hooks != null && hooks.controls(player);
    }

    static boolean active(AbstractContraptionEntity owner) {
        return hooks != null && hooks.active(owner);
    }

    static int nativeHelperCount(MinecraftServer server) {
        return hooks == null ? 0 : hooks.nativeHelperCount(server);
    }

    static void display(AbstractContraptionEntity owner, DisplayElement display) {
        if (hooks != null) hooks.display(owner, display);
    }

    static void removed(AbstractContraptionEntity owner) {
        if (hooks != null) hooks.removed(owner);
    }

    static void stopping() {
        if (hooks != null) hooks.stopping();
    }
}
