package com.zurrtum.create.infrastructure.polymer.equipment;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit.image;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zurrtum.create.Create;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class PolymerToolSpin {
    private static final int WRENCH_COPIES = 45, WRENCH_TICKS = 2;
    private static final int WAND_COPIES = 120, WAND_TICKS = 6, WAND_BOB_PERIOD = 120;
    private static final float WAND_BOB_PX = 0.05f * 16;

    private PolymerToolSpin() {}

    public static void addAssets(ResourcePackBuilder builder) {
        try {
            wrench(builder);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Wrench cog animation unavailable; static wrench kept", exception);
        }
        try {
            wand(builder);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Symmetry wand animation unavailable; static wand kept", exception);
        }
        try {
            cannon(builder);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Potato cannon cog animation unavailable", exception);
        }
        try {
            grip(builder);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Extendo grip model unavailable", exception);
        }
        try {
            controller(builder);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Linked controller models unavailable", exception);
        }
        try {
            worldshaper(builder);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Worldshaper accelerator animation unavailable", exception);
        }
    }

    private record Part(JsonArray elements, JsonObject textures, JsonObject display) {}

    private static Part part(ResourcePackBuilder builder, String id) throws IOException {
        JsonArray elements = null;
        JsonObject textures = new JsonObject(), display = null;
        String start = id;
        for (int depth = 0; id != null && depth < 8; depth++) {
            String[] key = (id.contains(":") ? id : "minecraft:" + id).split(":", 2);
            byte[] data = builder.getDataOrSource("assets/" + key[0] + "/models/" + key[1] + ".json");
            if (data == null) throw new IOException("Missing model " + id);
            JsonObject model = PackJson.object(data);
            if (elements == null && model.has("elements"))
                elements = model.getAsJsonArray("elements").deepCopy();
            if (display == null && model.has("display"))
                display = model.getAsJsonObject("display").deepCopy();
            if (model.has("textures"))
                for (var entry : model.getAsJsonObject("textures").entrySet())
                    if (!textures.has(entry.getKey())) textures.add(entry.getKey(), entry.getValue());
            id = model.has("parent") ? model.get("parent").getAsString() : null;
        }
        if (elements == null) throw new IOException("No elements in " + start);
        return new Part(elements, textures, display);
    }

    private static void place(JsonObject target, Part part, String prefix, Matrix4d m) {
        JsonObject textures = target.getAsJsonObject("textures");
        for (var entry : part.textures().entrySet()) {
            String value = entry.getValue().getAsString();
            textures.addProperty(
                    prefix + entry.getKey(), value.startsWith("#") ? "#" + prefix + value.substring(1) : value);
        }
        Matrix3d rotation = new Matrix3d();
        m.get3x3(rotation);
        for (int axis = 0; axis < 3; axis++)
            rotation.setColumn(axis, rotation.getColumn(axis, new Vector3d()).normalize());
        for (JsonElement source : part.elements()) {
            JsonObject element = source.getAsJsonObject().deepCopy();
            JsonObject old = element.getAsJsonObject("rotation");
            Vector3d origin =
                    old != null && old.has("origin") ? vec(old.getAsJsonArray("origin")) : new Vector3d(8, 8, 8);
            Matrix3d own = old == null
                    ? new Matrix3d()
                    : old.has("axis")
                            ? new Matrix3d()
                                    .rotation(
                                            Math.toRadians(old.get("angle").getAsDouble()),
                                            switch (old.get("axis").getAsString()) {
                                                case "x" -> new Vector3d(1, 0, 0);
                                                case "y" -> new Vector3d(0, 1, 0);
                                                default -> new Vector3d(0, 0, 1);
                                            })
                            : new Matrix3d()
                                    .rotationZYX(
                                            Math.toRadians(old.get("z").getAsDouble()),
                                            Math.toRadians(old.get("y").getAsDouble()),
                                            Math.toRadians(old.get("x").getAsDouble()));
            Vector3d moved = m.transformPosition(new Vector3d(origin).div(16)).mul(16);
            Vector3d shift = new Vector3d(moved).sub(origin);
            Vector3d from = vec(element.getAsJsonArray("from")).add(shift),
                    to = vec(element.getAsJsonArray("to")).add(shift);
            if (Math.min(Math.min(from.x, from.y), from.z) < -16
                    || Math.min(Math.min(to.x, to.y), to.z) < -16
                    || Math.max(Math.max(from.x, from.y), from.z) > 32
                    || Math.max(Math.max(to.x, to.y), to.z) > 32)
                throw new IllegalArgumentException(
                        "Composed tool part exceeds vanilla model bounds: " + prefix + " " + from + " .. " + to);
            element.add("from", array(new Vector3f((float) from.x, (float) from.y, (float) from.z)));
            element.add("to", array(new Vector3f((float) to.x, (float) to.y, (float) to.z)));
            float[] angles = eulerZYX(new Matrix3d(rotation).mul(own));
            element.add(
                    "rotation",
                    euler(
                            new float[] {(float) moved.x, (float) moved.y, (float) moved.z},
                            angles[0],
                            angles[1],
                            angles[2]));
            element.remove("neoforge_data");
            for (var face : element.getAsJsonObject("faces").entrySet()) {
                JsonObject f = face.getValue().getAsJsonObject();
                if (f.has("texture"))
                    f.addProperty(
                            "texture",
                            "#" + prefix + f.get("texture").getAsString().replace("#", ""));
                f.remove("cullface");
            }
            target.getAsJsonArray("elements").add(element);
        }
    }

    private static Vector3d vec(JsonArray array) {
        return new Vector3d(
                array.get(0).getAsDouble(),
                array.get(1).getAsDouble(),
                array.get(2).getAsDouble());
    }

    private static JsonObject model(JsonObject display) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", new JsonObject());
        model.add("elements", new JsonArray());
        if (display != null) model.add("display", display);
        return model;
    }

    private static void flipbook(
            ResourcePackBuilder builder,
            JsonObject target,
            Part part,
            String name,
            Vector3d axis,
            Vector3d pivot,
            double degPerCopy,
            int copies,
            int ticks)
            throws IOException {
        for (int k = 0; k < copies; k++) {
            Matrix4d spin = new Matrix4d()
                    .translate(pivot)
                    .rotate(Math.toRadians(k * degPerCopy), axis)
                    .translate(new Vector3d(pivot).negate());
            String prefix = name + k + "_";
            place(target, part, prefix, spin);
            JsonObject textures = target.getAsJsonObject("textures");
            for (var entry : part.textures().entrySet()) {
                String value = entry.getValue().getAsString();
                if (value.startsWith("#")) continue;
                Identifier tex = Identifier.parse(value);
                BufferedImage image =
                        image(builder, "assets/" + tex.getNamespace() + "/textures/" + tex.getPath() + ".png");
                String key = entry.getKey().replaceAll("[^a-z0-9_]", "");
                textures.addProperty(
                        prefix + entry.getKey(), sprite(builder, name + "_" + key + "_" + k, image, k, copies, ticks));
            }
        }
    }

    private static void cannon(ResourcePackBuilder builder) throws IOException {
        Part item = part(builder, "create:item/potato_cannon/item"),
                cog = part(builder, "create:item/potato_cannon/cog");
        JsonObject model = model(item.display());
        place(model, item, "i", new Matrix4d());
        flipbook(builder, model, cog, "cannon_cog", new Vector3d(0, 0, 1), new Vector3d(.5, .53125, .5), -2.5, 18, 1);
        oneAtlas(builder, model);
        builder.addStringData("assets/create/models/item/potato_cannon/item.json", model.toString());
    }

    private static void worldshaper(ResourcePackBuilder builder) throws IOException {
        Part item = part(builder, "create:item/handheld_worldshaper/item");
        Part core = part(builder, "create:item/handheld_worldshaper/core");
        Part accelerator = part(builder, "create:item/handheld_worldshaper/accelerator");
        JsonObject model = model(item.display());
        place(model, item, "i", new Matrix4d());
        place(model, core, "c", new Matrix4d());
        flipbook(
                builder,
                model,
                accelerator,
                "shaper_acc",
                new Vector3d(0, 0, 1),
                new Vector3d(.5, .345, .5),
                -2.5,
                18,
                2);
        oneAtlas(builder, model);
        builder.addStringData("assets/create/models/item/handheld_worldshaper/item.json", model.toString());
    }

    private static void grip(ResourcePackBuilder builder) throws IOException {
        double a = 0.015625, extension = 24 + (156 - 24) * a, opposite = 180 - extension, half = extension / 2;
        Part item = part(builder, "create:item/extendo_grip/item");
        JsonObject model = model(item.display());
        place(model, item, "i", new Matrix4d());
        Matrix4d base = new Matrix4d().translate(0, .5625, .0625);
        Matrix4d wideLong1 = new Matrix4d(base).rotateX(Math.toRadians(-half));
        Matrix4d thinShort2 = new Matrix4d(wideLong1)
                .translate(0, .34375, 0)
                .rotateX(Math.toRadians(-opposite))
                .translate(0, .6875, 0)
                .rotateX(Math.toRadians(opposite))
                .translate(0, .03125, 0);
        Matrix4d wideShort1 = new Matrix4d(base).rotateX(Math.toRadians(half - 180));
        Matrix4d thinLong1 = new Matrix4d(wideShort1).translate(0, .34375, 0).rotateX(Math.toRadians(opposite));
        Matrix4d wideShort2 = new Matrix4d(thinLong1)
                .translate(0, .6875, 0)
                .rotateX(Math.toRadians(-opposite))
                .translate(0, .03125, 0);
        Matrix4d hand = new Matrix4d(wideShort2)
                .translate(0, .34375, 0)
                .rotateX(Math.toRadians(180 - half))
                .rotateY(Math.PI)
                .translate(0, 0, -.25)
                .translate(-1, -.5, -.5);
        Part thinShort = part(builder, "create:item/extendo_grip/thin_short");
        Part wideShort = part(builder, "create:item/extendo_grip/wide_short");
        place(model, thinShort, "ts1", base);
        place(model, thinShort, "ts2", thinShort2);
        place(model, part(builder, "create:item/extendo_grip/wide_long"), "wl", wideLong1);
        place(model, wideShort, "ws1", wideShort1);
        place(model, wideShort, "ws2", wideShort2);
        place(model, part(builder, "create:item/extendo_grip/thin_long"), "tl", thinLong1);
        place(model, part(builder, "create:block/deployer/hand_pointing"), "h", hand);
        flipbook(
                builder,
                model,
                part(builder, "create:item/extendo_grip/cog"),
                "grip_cog",
                new Vector3d(0, 0, 1),
                new Vector3d(.5, .5625, .5),
                -2,
                45,
                1);
        oneAtlas(builder, model);
        builder.addStringData("assets/create/models/item/polymer/extendo_grip.json", model.toString());
    }

    private static void controller(ResourcePackBuilder builder) throws IOException {
        Part item = part(builder, "create:item/linked_controller/item");
        JsonObject idle = model(item.display());
        place(idle, item, "i", new Matrix4d());
        place(idle, part(builder, "create:item/linked_controller/torch_off"), "t", new Matrix4d());
        JsonObject active = model(item.display());
        place(active, part(builder, "create:item/linked_controller/powered"), "p", new Matrix4d());
        place(active, part(builder, "create:item/linked_controller/torch"), "t", new Matrix4d());
        Part button = part(builder, "create:item/linked_controller/button");
        int[][] offsets = {{2, 8}, {6, 8}, {4, 10}, {4, 6}, {7, 9}, {9, 9}};
        for (int i = 0; i < offsets.length; i++)
            place(active, button, "b" + i, new Matrix4d().translate(offsets[i][0] / 16.0, 0, offsets[i][1] / 16.0));
        oneAtlas(builder, idle);
        builder.addStringData("assets/create/models/item/polymer/linked_controller_idle.json", idle.toString());
        oneAtlas(builder, active);
        builder.addStringData("assets/create/models/item/polymer/linked_controller_active.json", active.toString());
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:custom_model_data");
        select.addProperty("index", 0);
        JsonArray cases = new JsonArray();
        JsonObject when = new JsonObject();
        when.addProperty("when", "active");
        when.add("model", plain("create:item/polymer/linked_controller_active"));
        cases.add(when);
        select.add("cases", cases);
        select.add("fallback", plain("create:item/polymer/linked_controller_idle"));
        JsonObject definition = new JsonObject();
        definition.add("model", select);
        builder.addStringData("assets/create/items/linked_controller.json", definition.toString());
    }

    private static void oneAtlas(ResourcePackBuilder builder, JsonObject model) {
        if (!model.has("textures")) return;
        JsonObject textures = model.getAsJsonObject("textures");
        boolean blocks = false, items = false;
        for (var entry : textures.entrySet()) {
            String value = entry.getValue().getAsString();
            if (value.startsWith("#")) continue;
            String path = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
            if (path.startsWith("item/")) items = true;
            else blocks = true;
        }
        if (!blocks || !items) return;
        for (var entry : textures.entrySet()) {
            String value = entry.getValue().getAsString();
            if (value.startsWith("#")) continue;
            String[] key = (value.contains(":") ? value : "minecraft:" + value).split(":", 2);
            if (!key[1].startsWith("item/")) continue;
            String copy = "polymer/item_atlas/" + key[0] + "/" + key[1].substring("item/".length());
            String from = "assets/" + key[0] + "/textures/" + key[1] + ".png";
            String to = "assets/create/textures/block/" + copy + ".png";
            byte[] png = builder.getDataOrSource(from);
            if (png == null) {
                Create.LOGGER.warn("Item model texture {} unavailable; model keeps two atlases", value);
                continue;
            }
            builder.addData(to, png);
            byte[] meta = builder.getDataOrSource(from + ".mcmeta");
            if (meta != null) builder.addData(to + ".mcmeta", meta);
            entry.setValue(new JsonPrimitive("create:block/" + copy));
        }
        if (!textures.has("particle")) {
            for (var entry : textures.entrySet()) {
                if (!entry.getValue().getAsString().startsWith("#")) {
                    textures.addProperty("particle", entry.getValue().getAsString());
                    break;
                }
            }
        }
    }

    private static JsonObject plain(String model) {
        JsonObject object = new JsonObject();
        object.addProperty("type", "minecraft:model");
        object.addProperty("model", model);
        return object;
    }

    private static void wrench(ResourcePackBuilder builder) throws IOException {
        JsonObject item = json(builder, "assets/create/models/item/wrench/item.json");
        JsonObject gear = json(builder, "assets/create/models/item/wrench/gear.json");
        BufferedImage texture = image(builder, "assets/create/textures/item/wrench.png");
        JsonArray elements = item.getAsJsonArray("elements");
        JsonObject textures = item.getAsJsonObject("textures");
        for (int k = 0; k < WRENCH_COPIES; k++) {
            String key = "spin" + k;
            textures.addProperty(key, sprite(builder, "wrench_" + k, texture, k, WRENCH_COPIES, WRENCH_TICKS));
            float turn = k * WRENCH_TICKS * 0.5f;
            for (JsonElement source : gear.getAsJsonArray("elements")) {
                JsonObject element = source.getAsJsonObject().deepCopy();
                JsonObject rotation = element.getAsJsonObject("rotation");
                float base = rotation == null ? 0 : rotation.get("angle").getAsFloat();
                element.add("rotation", euler(new float[] {9, 7, 8}, 0, base + turn, 0));
                element.remove("neoforge_data");
                retexture(element, key);
                elements.add(element);
            }
        }
        oneAtlas(builder, item);
        builder.addStringData("assets/create/models/item/wrench/item.json", item.toString());
    }

    private static void wand(ResourcePackBuilder builder) throws IOException {
        JsonObject item = json(builder, "assets/create/models/item/wand_of_symmetry/item.json");
        JsonObject core = json(builder, "assets/create/models/item/wand_of_symmetry/core.json");
        JsonObject glow = json(builder, "assets/create/models/item/wand_of_symmetry/core_glow.json");
        JsonObject bits = json(builder, "assets/create/models/item/wand_of_symmetry/bits.json");
        JsonArray elements = item.getAsJsonArray("elements");
        JsonObject textures = item.getAsJsonObject("textures");
        textures.add("core", core.getAsJsonObject("textures").get("5"));
        textures.add("glow", glow.getAsJsonObject("textures").get("3"));
        for (JsonElement source : core.getAsJsonArray("elements"))
            elements.add(glowing(source.getAsJsonObject().deepCopy(), "core"));
        for (JsonElement source : glow.getAsJsonArray("elements"))
            elements.add(glowing(source.getAsJsonObject().deepCopy(), "glow"));

        String glass = bits.getAsJsonObject("textures").get("3").getAsString();
        BufferedImage texture = image(builder, "assets/minecraft/textures/" + glass.replace("minecraft:", "") + ".png");
        for (int k = 0; k < WAND_COPIES; k++) {
            String key = "bits" + k;
            textures.addProperty(key, sprite(builder, "wand_bits_" + k, texture, k, WAND_COPIES, WAND_TICKS));
            int ticks = k * WAND_TICKS;
            float turn = -0.5f * ticks;
            float bob = WAND_BOB_PX * (float) Math.sin(2 * Math.PI * ticks / WAND_BOB_PERIOD);
            Matrix3d spin = new Matrix3d().rotationY(Math.toRadians(turn));
            for (JsonElement source : bits.getAsJsonArray("elements")) {
                JsonObject element = source.getAsJsonObject().deepCopy();
                JsonObject rotation = element.getAsJsonObject("rotation");
                Vector3f origin = PackJson.vector3f(rotation.getAsJsonArray("origin"));
                Matrix3d own = new Matrix3d()
                        .rotation(
                                Math.toRadians(rotation.get("angle").getAsFloat()),
                                switch (rotation.get("axis").getAsString()) {
                                    case "x" -> new Vector3d(1, 0, 0);
                                    case "y" -> new Vector3d(0, 1, 0);
                                    default -> new Vector3d(0, 0, 1);
                                });
                Matrix3d total = new Matrix3d(spin).mul(own);
                Vector3d turned = spin.transform(new Vector3d(origin.x - 8, origin.y, origin.z - 8));
                Vector3f moved = new Vector3f((float) (turned.x + 8), (float) turned.y + bob, (float) (turned.z + 8));
                Vector3f shift = new Vector3f(moved).sub(origin);
                element.add(
                        "from",
                        array(PackJson.vector3f(element.getAsJsonArray("from")).add(shift)));
                element.add(
                        "to",
                        array(PackJson.vector3f(element.getAsJsonArray("to")).add(shift)));
                float[] angles = eulerZYX(total);
                element.add(
                        "rotation", euler(new float[] {moved.x, moved.y, moved.z}, angles[0], angles[1], angles[2]));
                element.remove("neoforge_data");
                retexture(element, key);
                glowing(element, null);
                elements.add(element);
            }
        }
        oneAtlas(builder, item);
        builder.addStringData("assets/create/models/item/wand_of_symmetry/item.json", item.toString());
    }

    private static float[] eulerZYX(Matrix3d m) {
        double sinY = Math.clamp(-m.m02, -1, 1);
        double y = Math.asin(sinY), x, z;
        if (Math.abs(Math.cos(y)) > 1e-5) {
            x = Math.atan2(m.m12, m.m22);
            z = Math.atan2(m.m01, m.m00);
        } else {
            x = 0;
            z = Math.atan2(-m.m10, m.m11);
        }
        return new float[] {(float) Math.toDegrees(x), (float) Math.toDegrees(y), (float) Math.toDegrees(z)};
    }

    private static JsonObject glowing(JsonObject element, String texture) {
        element.addProperty("light_emission", 15);
        if (texture != null) retexture(element, texture);
        return element;
    }

    private static void retexture(JsonObject element, String key) {
        for (var face : element.getAsJsonObject("faces").entrySet())
            face.getValue().getAsJsonObject().addProperty("texture", "#" + key);
    }

    private static String sprite(
            ResourcePackBuilder builder, String name, BufferedImage texture, int slot, int slots, int ticks)
            throws IOException {
        int w = texture.getWidth(), h = texture.getWidth();
        BufferedImage strip = new BufferedImage(w, h * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < Math.min(h, texture.getHeight()); y++)
            for (int x = 0; x < w; x++) strip.setRGB(x, y, texture.getRGB(x, y));
        JsonArray frames = new JsonArray();
        if (slot > 0) frames.add(PackJson.frame(1, slot * ticks));
        frames.add(PackJson.frame(0, ticks));
        if (slot < slots - 1) frames.add(PackJson.frame(1, (slots - 1 - slot) * ticks));
        JsonObject animation = new JsonObject();
        animation.add("frames", frames);
        JsonObject meta = new JsonObject();
        meta.add("animation", animation);
        String path = "assets/create/textures/item/polymer/spin/" + name + ".png";
        builder.addData(path, PngCodec.encode(strip));
        builder.addStringData(path + ".mcmeta", meta.toString());
        return "create:item/polymer/spin/" + name;
    }

    private static JsonObject euler(float[] origin, float x, float y, float z) {
        JsonObject rotation = new JsonObject();
        rotation.add("origin", array(new Vector3f(origin[0], origin[1], origin[2])));
        rotation.addProperty("x", round(x));
        rotation.addProperty("y", round(y));
        rotation.addProperty("z", round(z));
        return rotation;
    }

    private static JsonArray array(Vector3f vector) {
        JsonArray array = new JsonArray();
        array.add(round(vector.x));
        array.add(round(vector.y));
        array.add(round(vector.z));
        return array;
    }

    private static float round(float value) {
        return Math.round(value * 1e5f) / 1e5f;
    }

    private static JsonObject json(ResourcePackBuilder builder, String path) throws IOException {
        byte[] data = builder.getDataOrSource(path);
        if (data == null) throw new IOException("Missing " + path);
        return PackJson.object(data);
    }
}
