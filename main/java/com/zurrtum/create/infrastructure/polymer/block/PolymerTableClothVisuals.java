package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlock;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerLoadedBlocks;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public final class PolymerTableClothVisuals {
    private static final Direction[] SIDES = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private static volatile Map<Block, Identifier> models = Map.of();
    private static boolean nativeHostRegistered;

    public static BlockState carrier() {
        return Blocks.CARPET.lime().defaultBlockState();
    }

    private static void registerNativeHost() {
        if (nativeHostRegistered) return;
        nativeHostRegistered = true;
        BlockWithElementHolder.registerOverlay(Blocks.CARPET.lime(), new BlockWithElementHolder() {
            @Override
            public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                var holder = new PolymerLitModelHolder(level, pos) {};
                var display = PolymerDisplays.item(
                        PolymerKineticVisuals.rotatingDisplayItem(CreateIds.id("polymer_part/native_lime_carpet")),
                        ItemDisplayContext.NONE);
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

    public static void register(Block block) {
        registerNativeHost();
        var copy = new HashMap<>(models);
        copy.put(block, BuiltInRegistries.BLOCK.getKey(block));
        models = Map.copyOf(copy);
        BlockWithElementHolder.registerOverlay(block, new BlockWithElementHolder() {
            @Override
            public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return new ClothHolder(level, pos, state);
            }

            @Override
            public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return true;
            }
        });
    }

    static int visibility(BlockGetter world, BlockPos pos, BlockState state) {
        int mask = 0;
        for (int i = 0; i < SIDES.length; i++) {
            if (Block.shouldRenderFace(state, PolymerLoadedBlocks.stateOrAir(world, pos.relative(SIDES[i])), SIDES[i]))
                mask |= 1 << i;
        }
        if (Block.shouldRenderFace(state, PolymerLoadedBlocks.stateOrAir(world, pos.below()), Direction.DOWN))
            mask |= 16;
        return mask;
    }

    private static Identifier item(Identifier block, int mask) {
        return CreateIds.id("polymer_block/create/block/polymer/table_cloth/" + block.getPath() + "/" + mask);
    }

    private static final class ClothHolder extends PolymerLitModelHolder {
        private final ServerLevel level;
        private final BlockPos pos;
        private final ItemDisplayElement display = new ItemDisplayElement();
        private Identifier shown;

        ClothHolder(ServerLevel level, BlockPos pos, BlockState state) {
            super(level, pos);
            this.level = level;
            this.pos = pos.immutable();
            display.setItemDisplayContext(ItemDisplayContext.NONE);
            display.setTransformation(PolymerBlockParts.displayTransform(new Matrix4f()));
            show(state, 31);
            addElement(display);
        }

        private void show(BlockState state, int mask) {
            if (!(state.getBlock() instanceof TableClothBlock)) return;
            staticCollision(state);
            Identifier block = models.get(state.getBlock());
            if (block == null) return;
            Identifier next = item(block, mask);
            if (!next.equals(shown)) {
                shown = next;
                display.setItem(PolymerKineticVisuals.rotatingDisplayItem(next));
            }
        }

        @Override
        protected void onTick() {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) return;
            super.onTick();
            BlockState state = chunk.getBlockState(pos);
            show(state, visibility(level, pos, state));
        }
    }

    static JsonObject variant(JsonObject template, Map<String, JsonObject> corners, int mask) {
        JsonObject result = template.deepCopy();
        JsonArray elements = new JsonArray();
        for (var value : template.getAsJsonArray("elements")) {
            JsonObject element = value.getAsJsonObject().deepCopy();
            JsonObject faces = element.getAsJsonObject("faces");
            for (String name : List.copyOf(faces.keySet())) {
                var face = faces.getAsJsonObject(name);
                if (!face.has("cullface")) continue;
                String direction = face.get("cullface").getAsString();
                int bit = switch (direction) {
                    case "south" -> 1;
                    case "west" -> 2;
                    case "north" -> 4;
                    case "east" -> 8;
                    case "down" -> 16;
                    default -> 0;
                };
                if (bit != 0 && (mask & bit) == 0) faces.remove(name);
            }
            if (!faces.isEmpty()) elements.add(element);
        }
        // Same pair masks as TableClothModel.addPartsWithInfo, including dyed corners. Fixed order: Map.of
        // iteration differs per JVM run, which changed the pack hash on every restart.
        for (var corner : List.of(
                Map.entry("south_west", 3),
                Map.entry("north_west", 6),
                Map.entry("north_east", 12),
                Map.entry("south_east", 9))) {
            if ((mask & corner.getValue()) == corner.getValue()) {
                for (var element : corners.get(corner.getKey()).getAsJsonArray("elements"))
                    elements.add(element.deepCopy());
            }
        }
        result.add("elements", elements);
        result.remove("groups");
        return result;
    }

    static void addAssets(ResourcePackBuilder builder) {
        builder.addStringData(
                "assets/minecraft/models/block/lime_carpet.json",
                "{\"parent\":\"minecraft:block/block\",\"textures\":{\"particle\":\"minecraft:block/lime_wool\"},\"elements\":[]}");
        builder.addStringData(
                "assets/create/models/block/polymer/native_lime_carpet.json",
                "{\"parent\":\"minecraft:block/carpet\",\"textures\":{\"wool\":\"minecraft:block/lime_wool\"}}");
        builder.addStringData(
                "assets/create/items/polymer_part/native_lime_carpet.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:block/polymer/native_lime_carpet\"}}");
        builder.addStringData(
                "assets/minecraft/items/lime_carpet.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:block/polymer/native_lime_carpet\"}}");
        JsonObject template =
                PolymerBlockProjections.readAssetJson("/assets/create/models/block/table_cloth/block.json");
        if (template == null) throw new IllegalStateException("Missing source cloth model");
        var corners = new HashMap<String, JsonObject>();
        for (String corner : List.of("south_west", "north_west", "north_east", "south_east")) {
            JsonObject model = PolymerBlockProjections.readAssetJson(
                    "/assets/create/models/block/table_cloth/" + corner + ".json");
            if (model == null) throw new IllegalStateException("Missing cloth corner " + corner);
            corners.put(corner, model);
        }
        for (Identifier block : models.values()) {
            JsonObject source =
                    PolymerBlockProjections.readAssetJson("/assets/create/models/block/" + block.getPath() + ".json");
            if (source == null) throw new IllegalStateException("Missing cloth color " + block);
            for (int mask = 0; mask < 32; mask++) {
                JsonObject model = variant(template, corners, mask);
                JsonObject slots = model.getAsJsonObject("textures");
                for (var texture : source.getAsJsonObject("textures").entrySet())
                    slots.add(texture.getKey(), texture.getValue().deepCopy());
                Identifier mesh = CreateIds.id("block/polymer/table_cloth/" + block.getPath() + "/" + mask);
                builder.addStringData("assets/create/models/" + mesh.getPath() + ".json", model.toString());
                var definition = new JsonObject();
                var inner = new JsonObject();
                inner.addProperty("type", "minecraft:model");
                inner.addProperty("model", mesh.toString());
                definition.add("model", inner);
                builder.addStringData(
                        "assets/create/items/" + item(block, mask).getPath() + ".json", definition.toString());
            }
        }
    }
}
