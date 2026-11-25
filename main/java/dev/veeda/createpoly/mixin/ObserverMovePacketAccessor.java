package dev.veeda.createpoly.mixin;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundMoveEntityPacket.class)
public interface ObserverMovePacketAccessor {
    @Accessor("entityId")
    int polymerpatch$observerEntityId();
}
