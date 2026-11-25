package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.contraptions.actors.seat.SeatBlock;
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.PolymerGhostModels;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCopycatParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerLadders;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

final class PolymerSchematicPreview {
    private static final Set<Block> WARNED = new HashSet<>();

    private PolymerSchematicPreview() {}

    static void add(
            ElementHolder holder, ServerPlayer viewer, Vec3 origin, BlockPos pos, BlockState state, BlockEntity be) {
        if (state.isAir()) return;
        BlockPos colorPos = viewer.level().hasChunkAt(pos) ? pos : viewer.blockPosition();
        var ladder = PolymerLadders.movingItemModel(state);
        var kinetic = PolymerKineticVisuals.movingItemModel(state);
        var variant = PolymerGhostModels.variant(state);
        var model = PolymerBlockProjections.movingDisplayModel(state);
        if (be instanceof CopycatBlockEntity copycat) {
            item(
                    holder,
                    origin,
                    pos,
                    PolymerCopycatParts.displayItem(state, copycat.getMaterial(), viewer.level(), colorPos),
                    new Quaternionf());
        } else if (state.getBlock() instanceof SeatBlock) {
            var mesh = CreateIds.id(
                    "block/" + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
            item(holder, origin, pos, PolymerBlockParts.displayItem(mesh), new Quaternionf());
        } else if (ladder != null) {
            item(
                    holder,
                    origin,
                    pos,
                    PolymerKineticVisuals.rotatingDisplayItem(ladder),
                    PolymerLadders.movingRotation(state));
        } else if (variant != null) {
            item(holder, origin, pos, variant.stack(), variant.rotation());
        } else if (kinetic != null) {
            item(
                    holder,
                    origin,
                    pos,
                    PolymerKineticVisuals.rotatingDisplayItem(kinetic),
                    PolymerKineticVisuals.staticAxisRotation(state, pos));
        } else if (model != null) {
            item(
                    holder,
                    origin,
                    pos,
                    PolymerKineticVisuals.rotatingDisplayItem(
                            PolymerBlockProjections.displayItemModelId(model.model())),
                    new Quaternionf()
                            .rotateYXZ((float) Math.toRadians(-model.y()), (float) Math.toRadians(-model.x()), 0));
        } else {
            var block = new BlockDisplayElement(state);
            block.setOffset(Vec3.atLowerCornerOf(pos).subtract(origin));
            holder.addElement(block);
        }
        if (be != null)
            try {
                for (var part : PolymerBlockParts.modelParts(be, 0)) {
                    var display = new ItemDisplayElement(PolymerBlockParts.displayItem(part.model()));
                    display.setItemDisplayContext(ItemDisplayContext.NONE);
                    display.setOffset(Vec3.atCenterOf(pos).subtract(origin));
                    display.setTransformation(PolymerBlockParts.displayTransform(part.transform()));
                    holder.addElement(display);
                }
            } catch (RuntimeException error) {
                if (WARNED.add(state.getBlock()))
                    Create.LOGGER.warn(
                            "Could not render schematic parts for {}",
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                            error);
            }
    }

    private static void item(ElementHolder holder, Vec3 origin, BlockPos pos, ItemStack stack, Quaternionf rotation) {
        var item = new ItemDisplayElement(stack);
        item.setItemDisplayContext(ItemDisplayContext.NONE);
        item.setOffset(Vec3.atCenterOf(pos).subtract(origin));
        item.setLeftRotation(rotation);
        item.setRightRotation(new Quaternionf().rotationY((float) Math.PI));
        holder.addElement(item);
    }
}
