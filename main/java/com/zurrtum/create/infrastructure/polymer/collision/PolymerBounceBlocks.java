package com.zurrtum.create.infrastructure.polymer.collision;

import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public final class PolymerBounceBlocks {
    public static void register() {
        BlockWithElementHolder.registerOverlay(Blocks.SLIME_BLOCK, new BlockWithElementHolder() {
            @Override
            public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                PolymerLitModelHolder holder = new PolymerLitModelHolder(level, pos) {};
                ItemDisplayElement skin =
                        PolymerDisplays.item(new ItemStack(Items.SLIME_BLOCK), ItemDisplayContext.NONE);
                skin.setTransformation(PolymerBlockParts.displayTransform(new Matrix4f()));
                holder.addElement(skin);
                return holder;
            }

            @Override
            public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return true;
            }
        });
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder -> builder.addStringData(
                "assets/minecraft/blockstates/slime_block.json",
                "{\"variants\":{\"\":{\"model\":\"create:block/polymer/native_slime_empty\"}}}"));
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder -> builder.addStringData(
                "assets/create/models/block/polymer/native_slime_empty.json",
                "{\"textures\":{\"particle\":\"minecraft:block/slime_block\"},\"elements\":[]}"));
    }
}
