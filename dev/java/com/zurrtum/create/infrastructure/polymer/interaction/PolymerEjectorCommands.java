package com.zurrtum.create.infrastructure.polymer.interaction;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.logistics.depot.EjectorBlock;
import com.zurrtum.create.content.logistics.depot.EjectorBlockEntity;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerDialogService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class PolymerEjectorCommands {
    private PolymerEjectorCommands() {}

    public static int setEjectorTarget(CommandSourceStack source, BlockPos destination) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        BlockHitResult hit = PolymerDialogService.raycast(player);
        if (hit.getType() != HitResult.Type.BLOCK
                || player.isSpectator()
                || !player.mayBuild()
                || !PolymerDialogService.canReach(player, level, hit.getBlockPos())) {
            return PolymerDialogService.fail(source, "create.polymer.ejector.error.no_target");
        }

        BlockPos ejectorPos = hit.getBlockPos();
        if (!level.isInWorldBounds(destination)
                || !level.getWorldBorder().isWithinBounds(destination)
                || !level.hasChunkAt(destination)) {
            return PolymerDialogService.fail(source, "create.polymer.ejector.error.destination_unavailable");
        }
        BlockEntity current = level.getBlockEntity(ejectorPos);
        if (!(current instanceof EjectorBlockEntity ejector)
                || !level.getBlockState(ejectorPos).is(AllBlocks.WEIGHTED_EJECTOR)) {
            return PolymerDialogService.fail(source, "create.polymer.ejector.error.no_target");
        }

        int dx = destination.getX() - ejectorPos.getX();
        int dz = destination.getZ() - ejectorPos.getZ();
        if ((dx == 0) == (dz == 0)) {
            return PolymerDialogService.fail(source, "create.polymer.ejector.error.axis");
        }
        int horizontal = Math.abs(dx) + Math.abs(dz);
        int vertical = destination.getY() - ejectorPos.getY();
        int maximum = AllConfigs.server().kinetics.maxEjectorDistance.get();
        if (horizontal > maximum || Math.abs(vertical) > maximum) {
            return PolymerDialogService.fail(source, "create.polymer.ejector.error.distance", maximum);
        }

        Direction facing = dx != 0
                ? Direction.get(dx < 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE, Axis.X)
                : Direction.get(dz < 0 ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE, Axis.Z);
        ejector.setTarget(horizontal, vertical);
        level.setBlockAndUpdate(
                ejectorPos, level.getBlockState(ejectorPos).setValue(EjectorBlock.HORIZONTAL_FACING, facing));
        player.sendSystemMessage(Component.translatable(
                "create.polymer.ejector.target_set",
                destination.getX(),
                destination.getY(),
                destination.getZ(),
                horizontal,
                vertical,
                facing.getName()));
        return 1;
    }

    public static int showEjectorHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("create.polymer.ejector.inventory_help"), false);
        return 1;
    }
}
