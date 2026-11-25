package com.zurrtum.create.infrastructure.polymer.block;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class PolymerBedrockCopycats {
    private PolymerBedrockCopycats() {}

    public static final class Skin {
        public void update(
                ServerLevel level, BlockPos pos, Identifier model, Collection<ServerGamePacketListenerImpl> watching) {}

        public void stopWatching(ServerGamePacketListenerImpl listener) {}

        public void destroy() {}
    }
}
