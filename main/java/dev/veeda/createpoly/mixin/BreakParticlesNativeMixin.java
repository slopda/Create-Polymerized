package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zurrtum.create.content.decoration.copycat.CopycatBlock;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
public abstract class BreakParticlesNativeMixin {

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(
            method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V")
    private void copycatBreakMaterial(Entity excluded, int eventId, BlockPos pos, int data, Operation<Void> original) {
        boolean create = eventId == LevelEvent.PARTICLES_DESTROY_BLOCK
                && BuiltInRegistries.BLOCK
                        .getKey(Block.stateById(data).getBlock())
                        .getNamespace()
                        .equals("create");
        boolean copycat = create && Block.stateById(data).getBlock() instanceof CopycatBlock;
        int material = create ? PolymerBlockParticles.breakEvent((ServerLevel) (Object) this, pos, data) : data;
        int rawMaterial = copycat
                ? Block.getId(PolymerBlockParticles.material((ServerLevel) (Object) this, pos, Block.stateById(data)))
                : data;
        original.call(excluded, eventId, pos, rawMaterial);
        if (create && excluded instanceof ServerPlayer player && !PolymerNativeClients.isNative(player))
            player.connection.send(new ClientboundLevelEventPacket(eventId, pos, material, false));
    }
}
