package com.zurrtum.create.infrastructure.polymer.block;

import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.redstone.diodes.BrassDiodeBlockEntity;
import com.zurrtum.create.infrastructure.polymer.PolymerPartItems;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public final class PolymerDiodeIndicators {
    private static final Identifier MODEL = CreateIds.id("block/polymer/diode_indicator");
    private static final Identifier ITEM = CreateIds.id("polymer_part/block/polymer/diode_indicator");
    private static final Matrix4f POSE = new Matrix4f().translate(0.5f, 0.5f, 0.5f);

    private PolymerDiodeIndicators() {}

    static void register() {
        PolymerBlockParts.<BrassDiodeBlockEntity>register(
                PolymerDiodeIndicators::indicator,
                AllBlocks.PULSE_REPEATER,
                AllBlocks.PULSE_EXTENDER,
                AllBlocks.PULSE_TIMER);
    }

    private static void indicator(
            BrassDiodeBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        ItemStack stack = PolymerKineticVisuals.rotatingDisplayItem(ITEM);
        PolymerPartItems.setColors(stack, List.of(color(be.getProgress())));
        out.add(PolymerBlockParts.itemPart(stack, new Matrix4f(POSE), ItemDisplayContext.NONE)
                .identified("diode:indicator"));
    }

    static int color(float progress) {
        int from = 0x2C0300, to = 0xCD0000;
        int r = (int) Mth.lerp(progress, from >> 16 & 255, to >> 16 & 255);
        int g = (int) Mth.lerp(progress, from >> 8 & 255, to >> 8 & 255);
        int b = (int) Mth.lerp(progress, from & 255, to & 255);
        return r << 16 | g << 8 | b;
    }

    public static void addAssets(ResourcePackBuilder builder) {
        JsonObject model = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.addProperty("flexpeater_off", "create:block/diodes/pulse_repeater/idle");
        textures.addProperty("particle", "create:block/diodes/pulse_repeater/idle");
        model.add("textures", textures);
        JsonObject element = new JsonObject();
        element.add("from", array(3.96875f, 1.96875f, 3.96875f));
        element.add("to", array(12.03125f, 3, 12.03125f));
        JsonObject faces = new JsonObject();
        faces.add("north", face(array(4, 4, 12, 5), 180));
        faces.add("east", face(array(11, 4, 12, 12), 90));
        faces.add("south", face(array(4, 11, 12, 12), 0));
        faces.add("west", face(array(4, 12, 5, 4), 90));
        faces.add("up", face(array(4, 4, 12, 12), 0));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        model.add("elements", elements);
        builder.addStringData(
                "assets/" + MODEL.getNamespace() + "/models/" + MODEL.getPath() + ".json", model.toString());

        JsonObject inner = new JsonObject();
        inner.addProperty("type", "minecraft:model");
        inner.addProperty("model", MODEL.toString());
        JsonObject tint = new JsonObject();
        tint.addProperty("type", "minecraft:custom_model_data");
        tint.addProperty("index", 0);
        tint.addProperty("default", 0x2C0300);
        JsonArray tints = new JsonArray();
        tints.add(tint);
        inner.add("tints", tints);
        JsonObject definition = new JsonObject();
        definition.add("model", inner);
        builder.addStringData(
                "assets/" + ITEM.getNamespace() + "/items/" + ITEM.getPath() + ".json", definition.toString());
    }

    private static JsonObject face(JsonArray uv, int rotation) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", "#flexpeater_off");
        face.add("uv", uv);
        if (rotation != 0) face.addProperty("rotation", rotation);
        face.addProperty("tintindex", 0);
        return face;
    }
}
