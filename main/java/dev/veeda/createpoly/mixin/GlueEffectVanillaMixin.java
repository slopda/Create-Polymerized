package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.content.contraptions.glue.SuperGlueHandler;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SuperGlueHandler.class)
public abstract class GlueEffectVanillaMixin {
    @Inject(
            method = "glueListensForBlockPlacement",
            require = 0,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerChunkCache;sendToTrackingPlayersAndSelf(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/Packet;)V"))
    private static void polymerVanillaGlue(
            ServerLevel world, Player player, BlockPos pos, CallbackInfo ci, @Local Direction direction) {
        Vec3 face = Vec3.atCenterOf(pos)
                .add(Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(.5));
        double spread = .5;
        PolymerVanillaEffects.particles(
                world,
                new ItemParticleOption(ParticleTypes.ITEM, Items.SLIME_BALL),
                face,
                40,
                direction.getAxis() == Direction.Axis.X ? 0 : spread,
                direction.getAxis() == Direction.Axis.Y ? 0 : spread,
                direction.getAxis() == Direction.Axis.Z ? 0 : spread,
                .05);
    }
}
