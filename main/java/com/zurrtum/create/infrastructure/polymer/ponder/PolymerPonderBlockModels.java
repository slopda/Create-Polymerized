package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zurrtum.create.infrastructure.polymer.block.PolymerScrollAnimations;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderScene.Block;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderScene.ModelRef;
import dev.veeda.createpoly.assets.PackJson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

final class PolymerPonderBlockModels {
    private PolymerPonderBlockModels() {}

    private static final class Defaults {
        static final JsonObject BLOCKS = new JsonObject(), VALUES = new JsonObject();

        static {
            var sorted = new TreeMap<String, net.minecraft.world.level.block.Block>();
            for (var block : BuiltInRegistries.BLOCK) {
                var id = BuiltInRegistries.BLOCK.getKey(block);
                if (id.getNamespace().equals("minecraft") || id.getNamespace().equals("create"))
                    sorted.put(id.toString(), block);
            }
            sorted.forEach((id, block) -> {
                var state = block.defaultBlockState();
                JsonObject properties = new JsonObject(), values = new JsonObject();
                for (var property : state.getProperties()) {
                    properties.addProperty(property.getName(), valueName(state, property));
                    if (property instanceof BooleanProperty) continue;
                    JsonArray order = new JsonArray();
                    for (var value : property.getPossibleValues()) order.add(nameOf(property, value));
                    values.add(property.getName(), order);
                }
                BLOCKS.add(id, properties);
                if (!values.isEmpty()) VALUES.add(id, values);
            });
        }

        private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
            return property.getName(state.getValue(property));
        }

        @SuppressWarnings("unchecked")
        private static <T extends Comparable<T>> String nameOf(Property<T> property, Object value) {
            return property.getName((T) value);
        }
    }

    static List<String> valueOrder(String block, String property) {
        JsonObject properties = Defaults.VALUES.getAsJsonObject(block);
        if (properties == null || !properties.has(property)) return List.of();
        List<String> order = new ArrayList<>();
        for (var value : properties.getAsJsonArray(property)) order.add(value.getAsString());
        return order;
    }

    static Map<String, String> defaultState(String name) {
        JsonObject state = Defaults.BLOCKS.getAsJsonObject(name);
        if (state == null) throw new IllegalArgumentException("Uncaptured block defaults: " + name);
        Map<String, String> properties = new LinkedHashMap<>();
        for (var entry : state.entrySet())
            properties.put(entry.getKey(), entry.getValue().getAsString());
        return properties;
    }

    static CompoundTag schematicNbt(Function<String, byte[]> resources, String id) throws IOException {
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "create",
                path = id.substring(id.indexOf(':') + 1);
        byte[] data = resources.apply("assets/" + ns + "/ponder/" + path + ".nbt");
        if (data == null) throw new IOException("Missing Ponder schematic " + id);
        return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
    }

    static List<Block> schematic(Function<String, byte[]> resources, String id) throws IOException {
        CompoundTag root = schematicNbt(resources, id);
        ListTag palette = root.getListOrEmpty("palette"), entries = root.getListOrEmpty("blocks");
        List<Block> blocks = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            CompoundTag state = palette.getCompoundOrEmpty(entry.getIntOr("state", 0));
            String name =
                    state.getString("id").or(() -> state.getString("Name")).orElse("minecraft:air");
            if (name.endsWith(":air") || !Defaults.BLOCKS.has(name)) name = "minecraft:air";
            Map<String, String> properties = new LinkedHashMap<>(defaultState(name));
            CompoundTag props = state.contains("properties")
                    ? state.getCompoundOrEmpty("properties")
                    : state.getCompoundOrEmpty("Properties");
            if (!name.equals("minecraft:air"))
                for (String key : props.keySet())
                    properties.put(key, props.getString(key).orElse(""));
            ListTag pos = entry.getListOrEmpty("pos");
            float speed = entry.getCompoundOrEmpty("nbt").getFloat("Speed").orElse(0f);
            String dye = name.equals(BELT)
                    ? entry.getCompoundOrEmpty("nbt").getString("Dye").orElse(null)
                    : null;
            Block block = new Block(
                    pos.getIntOr(0, 0),
                    pos.getIntOr(1, 0),
                    pos.getIntOr(2, 0),
                    name,
                    Map.copyOf(properties),
                    speed,
                    dye);
            if (placed.add(PolymerPonderScene.position(block.x(), block.y(), block.z()))) blocks.add(block);
        }
        ListTag size = root.getListOrEmpty("size");
        for (int x = 0; x < size.getIntOr(0, 0); x++)
            for (int y = 0; y < size.getIntOr(1, 0); y++)
                for (int z = 0; z < size.getIntOr(2, 0); z++)
                    if (placed.add(PolymerPonderScene.position(x, y, z)))
                        blocks.add(new Block(x, y, z, "minecraft:air", Map.copyOf(defaultState("minecraft:air")), 0));
        return blocks;
    }

    static List<ModelRef> models(Function<String, byte[]> resources, Block block) throws IOException {
        String ns = block.name().substring(0, block.name().indexOf(':')),
                path = block.name().substring(block.name().indexOf(':') + 1);
        byte[] data = resources.apply("assets/" + ns + "/blockstates/" + path + ".json");
        if (data == null) throw new IOException("Missing blockstate " + block.name());
        JsonObject json = PackJson.object(data);
        List<ModelRef> result = new ArrayList<>();
        if (json.has("variants")) {
            for (var variant : json.getAsJsonObject("variants").entrySet())
                if (matches(variant.getKey(), block.properties())) {
                    result.add(ref(PackJson.firstVariant(variant.getValue())));
                    break;
                }
        } else if (json.has("multipart")) {
            for (JsonElement part : json.getAsJsonArray("multipart")) {
                JsonObject object = part.getAsJsonObject();
                if (!object.has("when") || when(object.get("when").getAsJsonObject(), block.properties()))
                    result.add(ref(PackJson.firstVariant(object.get("apply"))));
            }
        }
        if (result.isEmpty()) throw new IOException("No model for " + block.name() + block.properties());
        return result;
    }

    private static boolean matches(String key, Map<String, String> properties) {
        if (key.isEmpty()) return true;
        for (String pair : key.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2 || !kv[1].equals(properties.get(kv[0]))) return false;
        }
        return true;
    }

    private static boolean when(JsonObject when, Map<String, String> properties) {
        if (when.has("OR")) {
            for (JsonElement option : when.getAsJsonArray("OR"))
                if (when(option.getAsJsonObject(), properties)) return true;
            return false;
        }
        if (when.has("AND")) {
            for (JsonElement option : when.getAsJsonArray("AND"))
                if (!when(option.getAsJsonObject(), properties)) return false;
            return true;
        }
        for (var entry : when.entrySet()) {
            boolean any = false;
            for (String value : entry.getValue().getAsString().split("\\|"))
                any |= value.equals(properties.get(entry.getKey()));
            if (!any) return false;
        }
        return true;
    }

    private static ModelRef ref(JsonObject variant) {
        String model = variant.get("model").getAsString();
        if (!model.contains(":")) model = "minecraft:" + model;
        return new ModelRef(
                model,
                variant.has("x") ? variant.get("x").getAsInt() : 0,
                variant.has("y") ? variant.get("y").getAsInt() : 0);
    }

    static final String BELT = "create:belt";

    static List<ModelRef> beltSurfaces(Block block, float speed) {
        String slope = block.properties().getOrDefault("slope", "horizontal"),
                part = block.properties().getOrDefault("part", "middle");
        String facing = block.properties().getOrDefault("facing", "north");
        boolean downward = slope.equals("downward"), upward = slope.equals("upward"), diagonal = downward || upward;
        boolean sideways = slope.equals("sideways"), start = part.equals("start"), end = part.equals("end");
        int step = facing.equals("south") || facing.equals("east") ? 1 : -1;
        if (downward || slope.equals("vertical") && step > 0) {
            boolean b = start;
            start = end;
            end = b;
        }
        float horizontal = switch (facing) {
            case "north" -> 180;
            case "west" -> -90;
            case "east" -> -270;
            default -> 0;
        };
        int degrees = Math.round(horizontal + (upward ? 180 : 0) + (sideways ? 270 + 90 : 0));
        int rx = !diagonal && !sideways && !slope.equals("horizontal") ? -90 : 0, rz = sideways ? 90 : 0;
        float scroll = speed * step;
        boolean alongX = facing.equals("east") || facing.equals("west");
        if (diagonal && downward ^ alongX || !sideways && !diagonal && alongX || sideways && step < 0) scroll = -scroll;
        String middle = start ? "start" : end ? "end" : "middle";
        int color = block.dye() == null
                ? 0
                : DyeColor.byName(block.dye(), DyeColor.WHITE).ordinal() + 1;
        List<ModelRef> result = new ArrayList<>();
        result.add(new ModelRef(
                beltModel(diagonal ? "diagonal_" + middle : middle, diagonal, scroll, color), rx, -degrees, rz));
        if (!diagonal) result.add(new ModelRef(beltModel(middle + "_bottom", false, scroll, color), rx, -degrees, rz));
        return List.copyOf(result);
    }

    static final String BELT_ANIMATION = "create:block/polymer/belt_anim/";

    private static String beltModel(String partial, boolean diagonal, float speed, int color) {
        int period = diagonal ? 12 : 16;
        if (speed == 0)
            return color == 0
                    ? "create:block/belt/" + partial
                    : "create:block/polymer/belt_frame/" + partial + "_" + color + "_"
                            + (partial.endsWith("bottom") ? period / 2 : 0);
        int bucket =
                PolymerScrollAnimations.bucket(Math.abs(speed) * period / 504.0 * PolymerScrollAnimations.BELT_SCALE);
        return "create:block/" + PolymerScrollAnimations.beltModelPath(partial, color, bucket, speed > 0);
    }

    static Object[] beltAnimation(String model) {
        if (!model.startsWith(BELT_ANIMATION)) return null;
        String[] tokens = model.substring(BELT_ANIMATION.length()).split("_");
        int n = tokens.length;
        String partial = String.join("_", Arrays.copyOf(tokens, n - 3));
        return new Object[] {
            partial, Integer.parseInt(tokens[n - 3]), Integer.parseInt(tokens[n - 2]), tokens[n - 1].equals("f")
        };
    }

    static ModelRef pulleyAxle(Block block) {
        String facing = block.properties().getOrDefault("facing", "north");
        if (block.properties().getOrDefault("slope", "horizontal").equals("sideways"))
            return new ModelRef("create:block/belt_pulley", 0, 0);
        return facing.equals("north") || facing.equals("south")
                ? new ModelRef("create:block/belt_pulley", 90, 90)
                : new ModelRef("create:block/belt_pulley", 90, 0);
    }

    static Map<String, Float> blockEntityFloats(Function<String, byte[]> resources, String id, String block, String key)
            throws IOException {
        CompoundTag root = schematicNbt(resources, id);
        ListTag palette = root.getListOrEmpty("palette"), entries = root.getListOrEmpty("blocks");
        Map<String, Float> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            CompoundTag paletteState = palette.getCompoundOrEmpty(entry.getIntOr("state", 0));
            if (!paletteState
                    .getString("id")
                    .or(() -> paletteState.getString("Name"))
                    .orElse("")
                    .equals(block)) continue;
            var value = entry.getCompoundOrEmpty("nbt").getFloat(key);
            ListTag pos = entry.getListOrEmpty("pos");
            if (value.isPresent())
                result.put(
                        PolymerPonderScene.position(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)),
                        value.get());
        }
        return result;
    }

    static boolean isBearing(Block block) {
        return block.name().equals("create:mechanical_bearing")
                || block.name().equals("create:windmill_bearing")
                || block.name().equals("create:clockwork_bearing");
    }

    static String bearingTrack(Block block) {
        return "bearing@" + PolymerPonderScene.position(block.x(), block.y(), block.z());
    }

    static ModelRef bearingTop(Block block) {
        String facing = block.properties().getOrDefault("facing", "up"), opposite = opposite(facing);
        float horizontal = switch (opposite) {
            case "north" -> 180;
            case "west" -> -90;
            case "east" -> -270;
            default -> 0;
        };
        float vertical = facing.equals("up") ? -90 : facing.equals("down") ? 90 : 0;
        boolean yAxis = facing.equals("up") || facing.equals("down");
        String model = block.name().equals("create:windmill_bearing")
                ? "create:block/bearing/top_wooden"
                : "create:block/bearing/top";
        return new ModelRef(
                model, Math.round(90 + vertical), yAxis ? 0 : Math.round(-horizontal), 0, bearingTrack(block));
    }

    static String opposite(String facing) {
        return switch (facing) {
            case "up" -> "down";
            case "down" -> "up";
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    static ModelRef facingSouthModel(String model, String facing) {
        return switch (facing) {
            case "north" -> new ModelRef(model, 0, -180);
            case "west" -> new ModelRef(model, 0, 90);
            case "east" -> new ModelRef(model, 0, 270);
            case "up" -> new ModelRef(model, 90, 0);
            case "down" -> new ModelRef(model, -90, 0);
            default -> new ModelRef(model, 0, 0);
        };
    }

    static boolean cogOffset(Block block) {
        String axis = block.properties().getOrDefault("axis", "y");
        int sum = (axis.equals("x") ? 0 : block.x())
                + (axis.equals("y") ? 0 : block.y())
                + (axis.equals("z") ? 0 : block.z());
        return Math.floorMod(sum, 2) == 0;
    }
}
