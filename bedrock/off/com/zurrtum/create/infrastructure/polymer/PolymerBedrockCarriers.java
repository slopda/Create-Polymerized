package com.zurrtum.create.infrastructure.polymer;

import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import java.util.UUID;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class PolymerBedrockCarriers {
    public static boolean SKIP_PAPER_DISPLAYS = false;
    public static boolean ENABLED = false;

    private PolymerBedrockCarriers() {}

    public static boolean bedrockNative(ServerPlayer player, BlockState state) {
        return false;
    }

    public static boolean skipPaperDisplays(ServerGamePacketListenerImpl listener) {
        return false;
    }

    static void candidate(BlockState source, @Nullable PolymerBlockModel model) {}

    static void allocate(BiFunction<PolymerBlockModel, BlockState, BlockState> request, ExportSink export) {}

    public static String stateKey(BlockState state) {
        return state.toString();
    }

    interface ExportSink {
        void accept(BlockState carrier, BlockState source, PolymerBlockModel model);
    }

    static boolean isBedrock(@Nullable PacketContext context) {
        return false;
    }

    public static boolean isBedrock(@Nullable UUID id) {
        return false;
    }
}
