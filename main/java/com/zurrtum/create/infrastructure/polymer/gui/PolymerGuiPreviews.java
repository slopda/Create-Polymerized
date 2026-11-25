package com.zurrtum.create.infrastructure.polymer.gui;

import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.*;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerHudRadial;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerRadialWrench;
import dev.veeda.createpoly.assets.PackJson;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.io.IOException;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerGuiPreviews implements ModInitializer {
    static final int VARIANT = 255;
    private static final float SIDE_CLEARANCE = 16;
    private static final String[] COLORS = {
        "white",
        "orange",
        "magenta",
        "light_blue",
        "yellow",
        "lime",
        "pink",
        "gray",
        "light_gray",
        "cyan",
        "purple",
        "blue",
        "brown",
        "green",
        "red",
        "black"
    };

    record Preview(
            String host,
            String item,
            int anchorX,
            int anchorY,
            float x,
            float y,
            float scale,
            float rx,
            float ry,
            float rz,
            String variants) {}

    static final List<String> CONFIGURABLE = List.of(
            "white_table_cloth",
            "orange_table_cloth",
            "magenta_table_cloth",
            "light_blue_table_cloth",
            "yellow_table_cloth",
            "lime_table_cloth",
            "pink_table_cloth",
            "gray_table_cloth",
            "light_gray_table_cloth",
            "cyan_table_cloth",
            "purple_table_cloth",
            "blue_table_cloth",
            "brown_table_cloth",
            "green_table_cloth",
            "red_table_cloth",
            "black_table_cloth",
            "andesite_table_cloth",
            "brass_table_cloth",
            "copper_table_cloth",
            "redstone_requester");

    static final List<Preview> PREVIEWS = List.of(
            new Preview("schematic_table_foreground", "schematic_table", 16, 26, 225, 64, 3, 0, 0, 0, ""),
            new Preview("schematicannon_foreground", "schematicannon", 34, 26, 234, 132, 5, 0, 0, 0, ""),
            new Preview("display_link_foreground", "display_link", 88, 26, 230, 139, 4, 50, 207, -14, ""),
            new Preview("threshold_foreground", "stockpile_switch", 88, 26, 240, 92, 5, 0, 0, 0, ""),
            new Preview("sequencer_foreground", "sequenced_gearshift", 88, 26, 247, 162, 5, 0, 0, 0, ""),
            new Preview("station_foreground", "track_station", 88, 26, 219.5f, 109.5f, 2.5f, -22, 63, 0, ""),
            new Preview("symmetry_foreground", "wand_of_symmetry", 88, 26, 204, 82, 4, -70, 20, 20, ""),
            new Preview("quill_prompt_foreground", "schematic_and_quill", 16, 26, 237, 64, 3, 0, 0, 0, ""),
            new Preview("attribute_filter_foreground", "attribute_filter", 52, 26, 265, 57, 4, 0, 0, 0, ""),
            new Preview("package_filter_foreground", "package_filter", 52, 26, 242, 54, 4, 0, 0, 0, ""),
            new Preview("elevator_foreground", "elevator_contact", 52, 26, 265, 71, 5, 0, 0, 0, ""),
            new Preview("controller_host", "linked_controller", 160, 26, 216, 77, 5, 0, 0, 0, ""),
            new Preview("factory_item_host", "factory_gauge", 16, 26, 192, 62, 3, 0, 0, 0, ""),
            new Preview("blueprint_foreground", "crafting_blueprint", 16, 26, 211, 76, 2.5f, 0, 0, 0, ""),
            new Preview("gui_filter_patch_20", "filter", 52, 62, 208, 74, 4, 0, 0, 0, ""),
            new Preview("gui_toolbox_patch_0", "brown_toolbox", 16, 26, 215, 106, 3.125f, 0, 0, 0, "toolbox"),
            new Preview("port_label_cover", "package_frogport", 88, 98, 232, 77, 4, 0, 0, 0, "postbox"),
            new Preview("preview_stock_category", "stock_ticker", 16, 98, 223, 108, 3, 0, 0, 0, ""),
            new Preview("redstone_requester_foreground", "redstone_requester", 16, 116, 218, 94, 3, 0, 0, 0, ""),
            new Preview(
                    "stock_patch_80", "", 160, 205, 209, 180, 3.5f, 0, 0, 0, "list:" + String.join(",", CONFIGURABLE)));

    @Override
    public void onInitialize() {
        Identifier phase = Identifier.parse("createpoly:gui_previews");
        var event = PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT;
        event.addPhaseOrdering(Event.DEFAULT_PHASE, phase);
        event.register(phase, PolymerGuiPreviews::addAssets);
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            for (Preview p : PREVIEWS) {
                String path = "assets/create/items/ui/" + p.host + ".json";
                JsonObject definition = p.host.equals("preview_stock_category")
                        ? JsonParser.parseString("{\"model\":{\"type\":\"minecraft:empty\"}}")
                                .getAsJsonObject()
                        : json(builder, path);
                boolean listed = p.variants.startsWith("list:");
                JsonObject visual = listed
                        ? JsonParser.parseString("{\"type\":\"minecraft:empty\"}")
                                .getAsJsonObject()
                        : p.host.equals("station_foreground")
                                ? station(builder, p)
                                : p.host.equals("blueprint_foreground")
                                        ? blueprint(builder, p)
                                        : p.host.equals("symmetry_foreground")
                                                ? symmetry(builder, p)
                                                : item(builder, p, p.item);
                if (listed) {
                    JsonObject select = new JsonObject();
                    select.addProperty("type", "minecraft:select");
                    select.addProperty("property", "minecraft:custom_model_data");
                    select.addProperty("index", VARIANT);
                    JsonArray cases = new JsonArray();
                    for (String id : p.variants.substring(5).split(",")) {
                        JsonObject c = new JsonObject();
                        c.addProperty("when", "create:" + id);
                        JsonObject model = item(builder, p, id);
                        offsetX(model, SIDE_CLEARANCE);
                        c.add("model", model);
                        cases.add(c);
                    }
                    select.add("cases", cases);
                    select.add("fallback", visual);
                    visual = select;
                } else if (!p.variants.isEmpty()) {
                    JsonObject select = new JsonObject();
                    select.addProperty("type", "minecraft:select");
                    select.addProperty("property", "minecraft:custom_model_data");
                    select.addProperty("index", VARIANT);
                    JsonArray cases = new JsonArray();
                    for (String color : COLORS) {
                        String id = color + "_" + p.variants;
                        JsonObject c = new JsonObject();
                        c.addProperty("when", "create:" + id);
                        c.add("model", item(builder, p, id));
                        cases.add(c);
                    }
                    select.add("cases", cases);
                    select.add("fallback", visual);
                    visual = select;
                }
                if (!listed && !p.host.equals("schematicannon_foreground") && !p.host.equals("symmetry_foreground"))
                    offsetX(visual, SIDE_CLEARANCE);
                JsonArray children = new JsonArray();
                children.add(definition.get("model"));
                children.add(visual);
                JsonObject composite = new JsonObject();
                composite.addProperty("type", "minecraft:composite");
                composite.add("models", children);
                definition.add("model", composite);
                definition.addProperty("oversized_in_gui", true);
                builder.addStringData(path, definition.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build source GUI item previews", e);
        }
        PolymerHudRadial.addAssets(builder, PolymerRadialWrench::wrenchable);
    }

    private static void offsetX(JsonObject visual, float pixels) {
        JsonElement value = visual.get("transformation");
        if (value != null && value.isJsonArray()) {
            JsonArray matrix = value.getAsJsonArray();
            matrix.set(3, new JsonPrimitive(matrix.get(3).getAsFloat() + pixels / 16));
            return;
        }
        JsonObject transform = value == null
                ? JsonParser.parseString(
                                "{\"translation\":[0,0,0],\"left_rotation\":[0,0,0,1],\"scale\":[1,1,1],\"right_rotation\":[0,0,0,1]}")
                        .getAsJsonObject()
                : value.getAsJsonObject();
        JsonArray translation = transform.has("translation") ? transform.getAsJsonArray("translation") : array(0, 0, 0);
        translation.set(0, new JsonPrimitive(translation.get(0).getAsFloat() + pixels / 16));
        transform.add("translation", translation);
        visual.add("transformation", transform);
    }

    private static JsonObject item(ResourcePackBuilder b, Preview p, String item) throws IOException {
        JsonObject root = json(b, "assets/create/items/" + item + ".json")
                .getAsJsonObject("model")
                .deepCopy();
        transform(b, root, p, item, new int[] {0});
        return root;
    }

    private static JsonObject station(ResourcePackBuilder b, Preview p) throws IOException {
        JsonArray cases = new JsonArray();
        for (boolean assembly : new boolean[] {false, true}) {
            float y = p.y + (assembly ? 41 : 0);
            Matrix4f block = new Matrix4f()
                    .translation((p.x - p.anchorX) / 16f, (p.anchorY - y) / 16f, 1)
                    .scale(p.scale)
                    .rotateX((float) Math.toRadians(-p.rx))
                    .rotateY((float) Math.toRadians(p.ry));
            JsonArray parts = new JsonArray();
            parts.add(
                    raw(b, p.host + (assembly ? "_assembly" : "_station"), "create:block/track_station/block", block));
            Matrix4f flag = new Matrix4f()
                    .translation((213 - p.anchorX) / 16f, (p.anchorY - (assembly ? 106 : 65)) / 16f, 1)
                    .scale(2.5f)
                    .rotateX((float) Math.toRadians(24))
                    .rotateY((float) Math.toRadians(-210))
                    .translate(-.12f, -.81f, 0)
                    .rotateY((float) Math.toRadians(90))
                    .rotateX((float) Math.toRadians(assembly ? 360 : 270))
                    .translate(.5f, .5f, .5f);
            parts.add(raw(
                    b,
                    p.host + (assembly ? "_assembly_flag" : "_station_flag"),
                    "create:block/track_station/flag_" + (assembly ? "assemble" : "off"),
                    flag));
            JsonObject composite = new JsonObject();
            composite.addProperty("type", "minecraft:composite");
            composite.add("models", parts);
            JsonObject choice = new JsonObject();
            choice.addProperty("when", assembly ? "assembly" : "station");
            choice.add("model", composite);
            cases.add(choice);
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "minecraft:select");
        result.addProperty("property", "minecraft:custom_model_data");
        result.addProperty("index", 0);
        result.add("cases", cases);
        result.add("fallback", JsonParser.parseString("{\"type\":\"minecraft:empty\"}"));
        return result;
    }

    static JsonObject raw(ResourcePackBuilder b, String name, String parent, Matrix4f matrix) throws IOException {
        String model = "create:item/ui/preview/" + name;
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("parent", parent);
        wrapper.add(
                "display",
                JsonParser.parseString("{\"gui\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}}"));
        b.addStringData("assets/create/models/" + model.substring(7) + ".json", wrapper.toString());
        JsonObject node = new JsonObject();
        node.addProperty("type", "minecraft:model");
        node.addProperty("model", model);
        node.add("transformation", array(new Matrix4f(matrix).transpose().get(new float[16])));
        return node;
    }

    private static JsonObject blueprint(ResourcePackBuilder b, Preview p) throws IOException {
        Matrix4f matrix = new Matrix4f()
                .translation((185 - p.anchorX) / 16f, (p.anchorY - 72) / 16f, 1)
                .scale(2.5f)
                .scale(1, -1, 1)
                .translate(.48f, .04f, 0)
                .scale(1, -1, 1)
                .rotateZ((float) Math.toRadians(22.5))
                .rotateX((float) Math.toRadians(45))
                .rotateY((float) Math.toRadians(-45))
                .translate(.5f, .5f, .5f);
        return raw(b, p.host, "create:entity/crafting_blueprint_small", matrix);
    }

    private static JsonObject symmetry(ResourcePackBuilder b, Preview p) throws IOException {
        float ox = 26 - 6, oy = 39 - 3;
        JsonArray cases = new JsonArray();
        String[][] shapes = {
            {"0_0", "plane", "0"},
            {"0_1", "plane", "90"},
            {"1_0", "crossplane", "0"},
            {"1_1", "crossplane", "45"},
            {"2_0", "tripleplane", "0"}
        };
        for (String[] shape : shapes) {
            Matrix4f matrix = new Matrix4f()
                    .translation((ox - p.anchorX) / 16f, (p.anchorY - oy) / 16f, 1)
                    .rotate((float) Math.toRadians(22.5), new Vector3f(.3f, -1, 0).normalize())
                    .translate(.5f, .5f, .5f)
                    .rotateY((float) Math.toRadians(Float.parseFloat(shape[2])))
                    .translate(-.5f, -.5f, -.5f)
                    .translate(.5f, .5f, .5f);
            JsonObject c = new JsonObject();
            c.addProperty("when", shape[0]);
            c.add("model", raw(b, "symmetry_mirror_" + shape[0], "create:block/symmetry_effect/" + shape[1], matrix));
            cases.add(c);
        }
        JsonObject mirror = new JsonObject();
        mirror.addProperty("type", "minecraft:select");
        mirror.addProperty("property", "minecraft:custom_model_data");
        mirror.addProperty("index", 1);
        mirror.add("cases", cases);
        mirror.add("fallback", JsonParser.parseString("{\"type\":\"minecraft:empty\"}"));
        JsonArray parts = new JsonArray();
        parts.add(item(b, p, p.item));
        parts.add(mirror);
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", parts);
        return composite;
    }

    private static void transform(ResourcePackBuilder b, JsonElement element, Preview p, String item, int[] index)
            throws IOException {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) transform(b, child, p, item, index);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject node = element.getAsJsonObject();
        if (node.has("type") && node.get("type").getAsString().equals("minecraft:model")) {
            String model = node.get("model").getAsString();
            JsonObject gui = gui(b, model, new HashSet<>());
            Vector3f rotation = vector(gui, "rotation", new Vector3f());
            Vector3f scale = vector(gui, "scale", new Vector3f(1));
            Vector3f translation = vector(gui, "translation", new Vector3f()).div(16);
            Quaternionf extra = new Quaternionf()
                    .rotationZ((float) Math.toRadians(p.rz))
                    .rotateX((float) Math.toRadians(p.rx))
                    .rotateY((float) Math.toRadians(p.ry));
            extra.transform(translation.mul(p.scale));
            translation.add((p.x - p.anchorX) / 16f, (p.anchorY - p.y) / 16f, 1);
            Quaternionf q = new Quaternionf(extra)
                    .mul(new Quaternionf()
                            .rotationXYZ((float) Math.toRadians(rotation.x), (float) Math.toRadians(rotation.y), (float)
                                    Math.toRadians(rotation.z)));
            String wrapper = "create:item/ui/preview/" + p.host + "/" + item + "_" + index[0]++;
            JsonObject clone = new JsonObject();
            clone.addProperty("parent", model);
            JsonObject display = new JsonObject();
            display.add(
                    "gui", JsonParser.parseString("{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}"));
            clone.add("display", display);
            b.addStringData("assets/create/models/" + wrapper.substring(7) + ".json", clone.toString());
            node.addProperty("model", wrapper);
            JsonObject t = new JsonObject();
            t.add("translation", array(translation.x, translation.y, translation.z));
            t.add("left_rotation", array(q.x, q.y, q.z, q.w));
            t.add("right_rotation", array(0, 0, 0, 1));
            scale.mul(p.scale);
            t.add("scale", array(scale.x, scale.y, scale.z));
            node.add("transformation", t);
            return;
        }
        for (JsonElement child : node.asMap().values())
            if (child.isJsonObject() || child.isJsonArray()) transform(b, child, p, item, index);
    }

    private static JsonObject gui(ResourcePackBuilder b, String id, Set<String> seen) throws IOException {
        if (!seen.add(id)) throw new IOException("GUI model parent cycle: " + id);
        Identifier key = Identifier.parse(id);
        if (key.getNamespace().equals("minecraft")
                && (key.getPath().equals("builtin/generated") || key.getPath().equals("builtin/entity")))
            return new JsonObject();
        JsonObject model = json(b, "assets/" + key.getNamespace() + "/models/" + key.getPath() + ".json");
        if (model.has("display") && model.getAsJsonObject("display").has("gui"))
            return model.getAsJsonObject("display").getAsJsonObject("gui");
        return model.has("parent") ? gui(b, model.get("parent").getAsString(), seen) : new JsonObject();
    }

    private static Vector3f vector(JsonObject o, String key, Vector3f fallback) {
        if (!o.has(key)) return fallback;
        JsonArray a = o.getAsJsonArray(key);
        return new Vector3f(
                a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static JsonObject json(ResourcePackBuilder b, String path) throws IOException {
        byte[] bytes = b.getDataOrSource(path);
        if (bytes == null) throw new IOException("Missing GUI preview dependency: " + path);
        return PackJson.object(bytes);
    }

    static ItemStack variant(ItemStack decorated, ItemStack source) {
        CustomModelData old = decorated.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        List<String> strings = new ArrayList<>(old.strings());
        while (strings.size() <= VARIANT) strings.add("");
        strings.set(VARIANT, BuiltInRegistries.ITEM.getKey(source.getItem()).toString());
        decorated.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(old.floats(), old.flags(), List.copyOf(strings), old.colors()));
        return decorated;
    }

    static Holder<Dialog> dialog(Holder<Dialog> holder) {
        Dialog d = holder.value();
        List<ActionButton> actions = d instanceof NoticeDialog n
                ? List.of(n.action())
                : d instanceof MultiActionDialog m ? m.actions() : List.of();
        String item = null;
        for (ActionButton button : actions)
            if (button.action().orElse(null) instanceof CustomAll action) {
                String id = action.id().toString();
                String match = switch (id) {
                    case "createpoly:quill" -> "schematic_and_quill";
                    case "createpoly:schematic_tools" -> "schematic";
                    case "create:polymer_worldshaper_screen" -> "handheld_worldshaper";
                    case "create:polymer_elevator_contact" -> "elevator_contact";
                    case "create:polymer_factory_panel" -> "factory_gauge";
                    case "create:polymer_attribute_filter" -> "attribute_filter";
                    case "create:polymer_symmetry_wand" -> "wand_of_symmetry";
                    default -> null;
                };
                if (match != null) {
                    item = match;
                    break;
                }
            }
        if (item == null) return holder;
        ItemStack icon = new ItemStack(Items.PAPER);
        icon.set(DataComponents.ITEM_MODEL, Identifier.parse("create:" + item));
        var bodies = new ArrayList<>(d.common().body());
        bodies.add(new ItemBody(ItemStackTemplate.fromNonEmptyStack(icon), Optional.empty(), false, false, 48, 48));
        var c = d.common();
        var common = new CommonDialogData(
                c.title(), c.externalTitle(), c.canCloseWithEscape(), c.pause(), c.afterAction(), bodies, c.inputs());
        if (d instanceof NoticeDialog n) return Holder.direct(new NoticeDialog(common, n.action()));
        if (d instanceof MultiActionDialog m)
            return Holder.direct(new MultiActionDialog(common, m.actions(), m.exitAction(), m.columns()));
        return holder;
    }
}
