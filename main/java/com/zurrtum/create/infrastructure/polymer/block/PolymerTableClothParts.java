package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.content.logistics.tableCloth.TableClothBlock;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PolymerTableClothParts {
    private static final Identifier PRICE_TOP = PolymerBlockParts.model("table_cloth/price_tag_top");
    private static final Identifier PRICE_SIDE = PolymerBlockParts.model("table_cloth/price_tag_side");

    private PolymerTableClothParts() {}

    static void registerAll() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof TableClothBlock) {
                PolymerBlockParts.<TableClothBlockEntity>register(PolymerTableClothParts::collect, block);
                PolymerBlockParts.still(block);
            }
        }
    }

    private static void collect(
            TableClothBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        float yaw = Mth.DEG_TO_RAD * (180 - be.facing.toYRot());
        Matrix4f orientation = PolymerBlockParts.rotateCentered(new Quaternionf().rotationY(yaw));
        List<ItemStack> stacks;
        if (be.isShop()) {
            out.add(PolymerBlockParts.part(be.sideOccluded ? PRICE_TOP : PRICE_SIDE, new Matrix4f(orientation))
                    .identified("price:tag"));
            payment(be, out);
            stacks = be.requestData.encodedRequest().stacks().stream()
                    .map(entry -> entry.stack)
                    .limit(4)
                    .toList();
        } else {
            stacks = be.manuallyAddedItems;
        }
        int size = stacks.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            Matrix4f pose = new Matrix4f(orientation).translate(0.5f, 0.1875f, 0.5f);
            if (size > 1) {
                float angle = Mth.DEG_TO_RAD * (i * (360f / size) + 45);
                pose.rotateY(angle)
                        .translate(0, i % 2 == 0 ? -0.005f : 0, 0.3125f)
                        .rotateY(-angle);
            }
            if (stack.getItem() instanceof BlockItem) {
                pose.translate(0, 0.0625f, 0).scale(0.755f);
            } else {
                pose.translate(0, 0.09375f, -0.0625f).scale(0.5f);
            }
            out.add(PolymerBlockParts.itemPart(stack, pose, ItemDisplayContext.FIXED)
                    .identified("stock:" + i));
        }
    }

    private static void payment(TableClothBlockEntity be, List<PolymerBlockParts.Part> out) {
        ItemStack stack = be.getPaymentItem();
        if (stack.isEmpty()) {
            return;
        }
        Vector3f pos = be.sideOccluded
                ? new Vector3f(8 / 16f, 0.75f / 16, 15.25f / 16)
                : new Vector3f(12 / 16f, -2.75f / 16, 16.75f / 16);
        PolymerBlockParts.rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * -be.facing.toYRot()))
                .transformPosition(pos);
        boolean blockItem = stack.getItem() instanceof BlockItem;
        Matrix4f pose = new Matrix4f()
                .translate(pos)
                .rotateY(Mth.DEG_TO_RAD * (180 - be.facing.toYRot()))
                .rotateX(be.sideOccluded ? Mth.HALF_PI : 0)
                .scale(0.5f * ((blockItem ? 1 : 0.5f) + 1 / 64f))
                .translate(0, 0, blockItem ? 0 : -0.15f);
        out.add(PolymerBlockParts.itemPart(stack, pose).identified("price:item"));
    }
}
