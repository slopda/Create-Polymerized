package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.infrastructure.polymer.PolymerPartItems;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBoxOutline;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PolymerSchematicOutline {
    static void addAssets(ResourcePackBuilder builder) {
        byte[] checker = builder.getDataOrSource("assets/create/textures/special/checkerboard.png");
        if (checker == null) throw new IllegalStateException("Missing original quill checkerboard texture");
        builder.addData("assets/create/textures/block/polymer/quill_checker.png", checker);
        builder.addStringData(
                "assets/create/models/block/polymer/quill_face.json",
                "{\"textures\":{\"all\":\"create:block/polymer/quill_checker\"},\"elements\":[{\"from\":[0,0,8],\"to\":[16,16,8],\"faces\":{\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#all\",\"tintindex\":0},\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#all\",\"tintindex\":0}}}]}");
        builder.addStringData(
                "assets/create/items/polymer_part/quill_face.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:block/polymer/quill_face\",\"tints\":[{\"type\":\"minecraft:custom_model_data\",\"index\":0,\"default\":-9935163}]}}");
    }

    static ElementHolder show(ServerPlayer player, AABB box) {
        ElementHolder holder = PolymerBoxOutline.show(player, box, 0x6886c5);
        Vec3 origin = new Vec3(box.minX, box.minY, box.minZ);
        double x = box.getXsize(), y = box.getYsize(), z = box.getZsize();
        for (int axis = 0; axis < 3; axis++)
            for (int side = 0; side < 2; side++) {
                ItemStack stack = new ItemStack(Items.PAPER);
                PolymerPartItems.setItemModel(stack, CreateIds.id("polymer_part/quill_face"));
                PolymerPartItems.setColors(stack, List.of(0xff6886c5));
                var item = new ItemDisplayElement(stack);
                item.setItemDisplayContext(ItemDisplayContext.NONE);
                Vec3 center = box.getCenter();
                if (axis == 0) center = new Vec3(side == 0 ? box.minX - .003 : box.maxX + .003, center.y, center.z);
                if (axis == 1) center = new Vec3(center.x, side == 0 ? box.minY - .003 : box.maxY + .003, center.z);
                if (axis == 2) center = new Vec3(center.x, center.y, side == 0 ? box.minZ - .003 : box.maxZ + .003);
                item.setOffset(center.subtract(origin));
                item.setLeftRotation(
                        axis == 0
                                ? new Quaternionf().rotateY((float) Math.PI / 2)
                                : axis == 1 ? new Quaternionf().rotateX((float) Math.PI / 2) : new Quaternionf());
                item.setScale(
                        axis == 0
                                ? new Vector3f((float) z, (float) y, 1)
                                : axis == 1
                                        ? new Vector3f((float) x, (float) z, 1)
                                        : new Vector3f((float) x, (float) y, 1));
                holder.addElement(item);
            }
        holder.tick();
        return holder;
    }
}
