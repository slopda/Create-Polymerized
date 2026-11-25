package com.zurrtum.create.infrastructure.polymer.gui;

import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.Create;
import dev.veeda.createpoly.assets.PackAssetId;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;

final class PolymerGuiModels {
    private PolymerGuiModels() {}

    static JsonObject quad(PackAssetId texture, JsonArray from, JsonArray to, JsonArray uv, float scale) {
        return quad(texture, from, to, uv, scale, false);
    }

    static JsonObject quad(
            PackAssetId texture, JsonArray from, JsonArray to, JsonArray uv, float scale, boolean tinted) {
        if (!Float.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("GUI scale must be finite and positive");
        JsonObject model = new JsonObject();
        model.addProperty("gui_light", "front");
        JsonObject textures = new JsonObject();
        textures.addProperty("panel", texture.reference());
        model.add("textures", textures);
        JsonObject element = new JsonObject();
        element.add("from", from);
        element.add("to", to);
        element.addProperty("shade", false);
        JsonObject face = new JsonObject();
        face.add("uv", uv);
        face.addProperty("texture", "#panel");
        if (tinted) face.addProperty("tintindex", 0);
        JsonObject faces = new JsonObject();
        faces.add("south", face);
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        model.add("elements", elements);
        JsonObject gui = new JsonObject();
        gui.add("rotation", array(0, 0, 0));
        gui.add("translation", array(0, 0, 0));
        gui.add("scale", array(scale, scale, 1));
        JsonObject display = new JsonObject();
        display.add("gui", gui);
        model.add("display", display);
        return model;
    }

    static JsonObject select(int index, JsonArray cases) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:select");
        o.addProperty("property", "minecraft:custom_model_data");
        o.addProperty("index", index);
        o.add("cases", cases);
        JsonObject empty = new JsonObject();
        empty.addProperty("type", "minecraft:empty");
        o.add("fallback", empty);
        return o;
    }

    static JsonObject caseOf(String when, JsonObject model) {
        JsonObject o = new JsonObject();
        o.addProperty("when", when);
        o.add("model", model);
        return o;
    }

    static JsonObject translation(float x, float y, float z) {
        JsonObject o = new JsonObject();
        o.add("translation", array(x, y, z));
        o.add("left_rotation", array(0, 0, 0, 1));
        o.add("scale", array(1, 1, 1));
        o.add("right_rotation", array(0, 0, 0, 1));
        return o;
    }

    record Folder(String folder, int itemCx, int itemCy, boolean tinted) {
        void png(ResourcePackBuilder builder, String name, BufferedImage image) throws IOException {
            builder.addData("assets/create/textures/item/ui/" + folder + "/" + name + ".png", PngCodec.encode(image));
        }

        void quad(ResourcePackBuilder builder, String name, int w, int h, float scale) {
            float hw = w / scale / 2, hh = h / scale / 2;
            quad(builder, name, array(8 - hw, 8 - hh, 8), array(8 + hw, 8 + hh, 8), scale);
        }

        void quad(ResourcePackBuilder builder, String name, JsonArray from, JsonArray to, float scale) {
            JsonObject model = PolymerGuiModels.quad(
                    new PackAssetId(Create.MOD_ID, "item/ui/" + folder + "/" + name),
                    from,
                    to,
                    array(0, 0, 16, 16),
                    scale,
                    tinted);
            builder.addStringData("assets/create/models/item/ui/" + folder + "/" + name + ".json", model.toString());
        }

        JsonObject centred(float x, float y, float z, float scale) {
            return translation((x - itemCx) / 16f / scale, (itemCy - y) / 16f / scale, z / scale);
        }

        JsonObject model(String name) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "minecraft:model");
            o.addProperty("model", "create:item/ui/" + folder + "/" + name);
            return o;
        }
    }
}
