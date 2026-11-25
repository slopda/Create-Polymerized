package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.infrastructure.polymer.PolymerCreateBlock;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDecorativeCulling;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerCrackModels;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.mixin.BlockResourceCreatorAccessor;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

public final class PolymerSeatCarriers {
    private static final Identifier EMPTY = Identifier.parse("create:block/polymer/seat_carrier");
    private static volatile BlockState dry, wet;
    private static final VoxelShape SLAB = Shapes.box(0, 0, 0, 1, .5, 1);

    public static void register(Block block) {
        if (dry == null) {
            var creator = BlockResourceCreatorAccessor.polymerpatch$getSharedCreator();
            dry = PolymerCrackModels.request(BlockModelType.SLAB_BOTTOM, SLAB);
            wet = PolymerCrackModels.request(BlockModelType.SLAB_BOTTOM_WATERLOGGED, SLAB);
            if (dry == null || wet == null) {
                dry = creator.requestBlock(
                        BlockModelType.TRAPDOOR_BOTTOM,
                        state -> state.is(Blocks.IRON_TRAPDOOR),
                        PolymerBlockModel.of(EMPTY));
                wet = creator.requestBlock(
                        BlockModelType.TRAPDOOR_BOTTOM_WATERLOGGED,
                        state -> state.is(Blocks.IRON_TRAPDOOR),
                        PolymerBlockModel.of(EMPTY));
            }
            if (dry == null || wet == null)
                throw new IllegalStateException("No noninteractive native seat collision states available");
        }
        Identifier mesh = PolymerBlockParts.model(
                CreateIds.id("block/" + BuiltInRegistries.BLOCK.getKey(block).getPath()));
        PolymerBlock.registerOverlay(block, new Carrier());
        BlockWithElementHolder.registerOverlay(block, new BlockWithElementHolder() {
            @Override
            public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                var holder = new PolymerLitModelHolder(level, pos) {};
                var display = PolymerDisplays.item(PolymerBlockParts.displayItem(mesh), ItemDisplayContext.NONE);
                display.setTransformation(PolymerBlockParts.displayTransform(new Matrix4f()));
                PolymerDecorativeCulling.inBlock(display);
                holder.addElement(display);
                return holder;
            }

            @Override
            public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return true;
            }
        });
    }

    private record Carrier() implements PolymerCreateBlock, PolymerTexturedBlock {
        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            return state.getFluidState().isEmpty() ? dry : wet;
        }
    }

    public static void addAssets(ResourcePackBuilder builder) {
        builder.addStringData(
                "assets/create/models/block/polymer/seat_carrier.json",
                "{\"textures\":{\"particle\":\"minecraft:block/iron_block\"},\"elements\":[]}");
    }
}
