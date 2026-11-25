package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zurrtum.create.Create;
import com.zurrtum.create.catnip.placement.IPlacementHelper;
import com.zurrtum.create.catnip.placement.PlacementHelpers;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Quaternionf;

public final class PolymerGhostModels {
    private static final int FRAMES = 8, FRAMETIME = 3;
    private static final Map<Item, ItemStack> GHOSTS = new ConcurrentHashMap<>();
    private static final Map<BlockState, Variant> VARIANTS = new ConcurrentHashMap<>();

    private static final Map<String, ItemStack> MODEL_GHOSTS = new ConcurrentHashMap<>();
    private static volatile ItemStack copycatCube;

    public static ItemStack copycatCube() {
        ItemStack cube = copycatCube;
        return cube == null ? null : cube.copy();
    }

    public record Variant(ItemStack stack, int x, int y) {
        public Quaternionf rotation() {
            return new Quaternionf().rotateYXZ((float) Math.toRadians(-y), (float) Math.toRadians(-x), 0);
        }
    }

    public static Variant variant(BlockState state) {
        Variant variant = VARIANTS.get(state);
        return variant == null ? null : new Variant(variant.stack().copy(), variant.x(), variant.y());
    }

    private PolymerGhostModels() {}

    public static ItemStack ghost(Item item) {
        ItemStack stack = GHOSTS.get(item);
        return stack == null ? null : stack.copy();
    }

    public static void addAssets(ResourcePackBuilder builder) {
        Map<String, String> ghostTextures = new HashMap<>();
        int count = 0;
        String copycat = ghostTextures.computeIfAbsent("create:block/copycat_base", t -> ghostTexture(builder, t));
        if (copycat != null) {
            StringBuilder faces = new StringBuilder();
            for (String face : new String[] {"north", "south", "east", "west", "up", "down"})
                faces.append(faces.isEmpty() ? "" : ",")
                        .append("\"")
                        .append(face)
                        .append("\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}");
            builder.addStringData(
                    "assets/create/models/block/polymer/ghost/copycat_cube.json",
                    "{\"textures\":{\"all\":\"" + copycat + "\",\"particle\":\"" + copycat + "\"},"
                            + "\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{" + faces + "}}]}");
            builder.addStringData(
                    "assets/create/items/polymer/ghost/copycat_cube.json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:block/polymer/ghost/copycat_cube\"}}");
            ItemStack cube = new ItemStack(Items.PAPER);
            cube.set(DataComponents.ITEM_MODEL, CreateIds.id("polymer/ghost/copycat_cube"));
            copycatCube = cube;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (!itemId.getNamespace().equals(Create.MOD_ID) || !(item instanceof BlockItem)) continue;
            ItemStack stack = new ItemStack(item);
            boolean helper = false;
            for (IPlacementHelper placement : PlacementHelpers.getHelpersView())
                if (placement.getItemPredicate().test(stack)) {
                    helper = true;
                    break;
                }
            if (!helper) continue;
            try {
                addVariants(builder, ((BlockItem) item).getBlock(), ghostTextures);
            } catch (IOException | RuntimeException exception) {
                Create.LOGGER.warn("No variant ghosts for {}", itemId, exception);
            }
            try {
                String model = itemModel(builder, itemId);
                if (model == null) continue;
                Map<String, String> textures = new LinkedHashMap<>();
                collectTextures(builder, model, textures, 0);
                if (textures.isEmpty()) continue;
                JsonObject overrides = new JsonObject();
                for (var entry : textures.entrySet()) {
                    String texture = resolve(entry.getValue(), textures, 0);
                    if (texture == null) continue;
                    String ghost = ghostTextures.computeIfAbsent(texture, t -> ghostTexture(builder, t));
                    if (ghost != null) overrides.addProperty(entry.getKey(), ghost);
                }
                if (overrides.isEmpty()) continue;
                String path = "block/polymer/ghost/" + itemId.getPath();
                JsonObject ghostModel = new JsonObject();
                ghostModel.addProperty("parent", model);
                ghostModel.add("textures", overrides);
                builder.addStringData("assets/create/models/" + path + ".json", ghostModel.toString());
                builder.addStringData(
                        "assets/create/items/polymer/ghost/" + itemId.getPath() + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:" + path + "\"}}");
                ItemStack display = new ItemStack(Items.PAPER);
                display.set(DataComponents.ITEM_MODEL, CreateIds.id("polymer/ghost/" + itemId.getPath()));
                GHOSTS.put(item, display);
                count++;
            } catch (IOException | RuntimeException exception) {
                Create.LOGGER.warn("No translucent ghost for {}", itemId, exception);
            }
        }
        Create.LOGGER.info(
                "Generated {} translucent placement ghost models, {} exact state variants ({} textures)",
                count + MODEL_GHOSTS.size(),
                VARIANTS.size(),
                ghostTextures.size());
    }

    private static void addVariants(ResourcePackBuilder builder, Block block, Map<String, String> ghostTextures)
            throws IOException {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        byte[] data = builder.getDataOrSource(
                "assets/" + blockId.getNamespace() + "/blockstates/" + blockId.getPath() + ".json");
        if (data == null) return;
        JsonObject root = PackJson.object(data);
        if (!root.has("variants")) return;
        JsonObject variants = root.getAsJsonObject("variants");
        for (var state : block.getStateDefinition().getPossibleStates()) {
            for (var entry : variants.entrySet()) {
                if (!matches(state, entry.getKey())) continue;
                JsonElement value = entry.getValue();
                JsonObject variant =
                        value.isJsonArray() ? value.getAsJsonArray().get(0).getAsJsonObject() : value.getAsJsonObject();
                String model = PolymerBlockProjections.resolveModelAlias(
                                Identifier.parse(variant.get("model").getAsString()))
                        .toString();
                ItemStack stack = modelGhost(builder, model, ghostTextures);
                if (stack != null)
                    VARIANTS.put(
                            state,
                            new Variant(
                                    stack,
                                    variant.has("x") ? variant.get("x").getAsInt() : 0,
                                    variant.has("y") ? variant.get("y").getAsInt() : 0));
                break;
            }
        }
    }

    private static boolean matches(BlockState state, String key) {
        if (key.isEmpty()) return true;
        for (String condition : key.split(",")) {
            String[] pair = condition.split("=", 2);
            var property = state.getBlock().getStateDefinition().getProperty(pair[0]);
            if (property == null || pair.length < 2) return false;
            if (!name(state, property).equals(pair[1])) return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static ItemStack modelGhost(ResourcePackBuilder builder, String model, Map<String, String> ghostTextures)
            throws IOException {
        ItemStack existing = MODEL_GHOSTS.get(model);
        if (existing != null) return existing;
        Map<String, String> textures = new LinkedHashMap<>();
        collectTextures(builder, model, textures, 0);
        JsonObject overrides = new JsonObject();
        for (var entry : textures.entrySet()) {
            String texture = resolve(entry.getValue(), textures, 0);
            if (texture == null) continue;
            String ghost = ghostTextures.computeIfAbsent(texture, t -> ghostTexture(builder, t));
            if (ghost != null) overrides.addProperty(entry.getKey(), ghost);
        }
        if (overrides.isEmpty()) return null;
        Identifier modelId = Identifier.parse(model);
        String path = "block/polymer/ghost/variant/" + modelId.getNamespace() + "/"
                + modelId.getPath().replace('/', '_');
        JsonObject ghostModel = new JsonObject();
        ghostModel.addProperty("parent", model);
        ghostModel.add("textures", overrides);
        builder.addStringData("assets/create/models/" + path + ".json", ghostModel.toString());
        String itemPath = path.substring("block/".length());
        builder.addStringData(
                "assets/create/items/" + itemPath + ".json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:" + path + "\"}}");
        ItemStack display = new ItemStack(Items.PAPER);
        display.set(DataComponents.ITEM_MODEL, CreateIds.id(itemPath));
        MODEL_GHOSTS.put(model, display);
        return display;
    }

    private static String itemModel(ResourcePackBuilder builder, Identifier itemId) {
        byte[] data =
                builder.getDataOrSource("assets/" + itemId.getNamespace() + "/items/" + itemId.getPath() + ".json");
        if (data == null) return null;
        JsonObject model = PackJson.object(data).getAsJsonObject("model");
        if (model == null || !"minecraft:model".equals(model.get("type").getAsString())) return null;
        return model.get("model").getAsString();
    }

    private static void collectTextures(
            ResourcePackBuilder builder, String model, Map<String, String> textures, int depth) throws IOException {
        if (depth > 16 || model.startsWith("builtin/") || model.startsWith("minecraft:builtin/")) return;
        Identifier id = Identifier.parse(model);
        byte[] data = builder.getDataOrSource("assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json");
        if (data == null) throw new IOException("Missing model " + model);
        JsonObject json = PackJson.object(data);
        if (json.has("textures"))
            for (var entry : json.getAsJsonObject("textures").entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) textures.putIfAbsent(entry.getKey(), value.getAsString());
                else if (value.isJsonObject() && value.getAsJsonObject().has("sprite"))
                    textures.putIfAbsent(
                            entry.getKey(),
                            value.getAsJsonObject().get("sprite").getAsString());
            }
        if (json.has("parent")) collectTextures(builder, json.get("parent").getAsString(), textures, depth + 1);
    }

    private static String resolve(String value, Map<String, String> textures, int depth) {
        if (depth > 16) return null;
        if (value.startsWith("#")) {
            String next = textures.get(value.substring(1));
            return next == null ? null : resolve(next, textures, depth + 1);
        }
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static String ghostTexture(ResourcePackBuilder builder, String texture) {
        Identifier id = Identifier.parse(texture);
        byte[] png = builder.getDataOrSource("assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png");
        if (png == null) return null;
        try {
            BufferedImage source = PngCodec.decode(png);
            int size = source.getWidth(), height = Math.min(source.getHeight(), size);
            BufferedImage strip = new BufferedImage(size, height * FRAMES, BufferedImage.TYPE_INT_ARGB);
            for (int frame = 0; frame < FRAMES; frame++) {
                double alpha = 0.45 + 0.2 * Math.sin(2 * Math.PI * frame / FRAMES);
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < size; x++) {
                        int argb = source.getRGB(x, y);
                        int a = (int) Math.round((argb >>> 24) * alpha);
                        strip.setRGB(x, frame * height + y, a << 24 | argb & 0xFFFFFF);
                    }
            }
            String path = "block/polymer/ghost_tex/" + id.getNamespace() + "/"
                    + id.getPath().replace('/', '_');
            builder.addData("assets/create/textures/" + path + ".png", PngCodec.encode(strip));
            builder.addStringData(
                    "assets/create/textures/" + path + ".png.mcmeta",
                    "{\"animation\":{\"frametime\":" + FRAMETIME + ",\"interpolate\":true,\"width\":" + size
                            + ",\"height\":" + height + "}}");
            return "create:" + path;
        } catch (IOException exception) {
            return null;
        }
    }
}
