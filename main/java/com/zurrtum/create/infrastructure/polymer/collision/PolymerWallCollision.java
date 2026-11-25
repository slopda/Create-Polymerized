package com.zurrtum.create.infrastructure.polymer.collision;

import com.google.gson.JsonObject;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.block.PolymerMultipartBlocks;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public final class PolymerWallCollision {
    private static final JsonObject DATA =
            PolymerBlockProjections.readAssetJson("/data/create/polymer/wall_collision_models.json");
    private static final Map<BlockState, BlockState> CARRIERS = new HashMap<>();

    public static void register() {
        if (DATA == null) throw new IllegalStateException("Missing native wall preservation assets");
        PolymerMultipartBlocks.registerNativeWall(Blocks.POLISHED_TUFF_WALL, DATA.getAsJsonObject("blockstate"));
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null || !id.getNamespace().equals("create") || !(block instanceof WallBlock)) continue;
            for (BlockState source : block.getStateDefinition().getPossibleStates()) {
                BlockState target = copyProperties(source, Blocks.POLISHED_TUFF_WALL.defaultBlockState());
                if (PolymerBlockCollision.sameShape(
                        PolymerBlockCollision.shape(source), PolymerBlockCollision.shape(target)))
                    CARRIERS.put(source, target);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProperties(BlockState source, BlockState target) {
        for (Property<?> property : source.getProperties()) {
            Property nativeProperty = target.getBlock().getStateDefinition().getProperty(property.getName());
            if (nativeProperty != null && nativeProperty.getPossibleValues().contains(source.getValue(property)))
                target = target.setValue(nativeProperty, source.getValue(property));
        }
        return target;
    }

    static @Nullable BlockState carrier(BlockState state) {
        return CARRIERS.get(state);
    }

    public static @Nullable Identifier movingModel(BlockState state) {
        if (!state.is(Blocks.POLISHED_TUFF_WALL)) return null;
        String key = state.getProperties().stream()
                .sorted(Comparator.comparing(Property::getName))
                .map(property -> property.getName() + "=" + valueName(state, property))
                .collect(Collectors.joining(","));
        var entry = DATA.getAsJsonObject("items").get(key);
        if (entry == null) throw new IllegalStateException("Missing native wall state mesh " + key);
        return itemModel(Identifier.parse(entry.getAsString()));
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static Identifier itemModel(Identifier model) {
        return CreateIds.id("polymer_wall/" + model.getPath());
    }

    private static void crackModel(ResourcePackBuilder builder, String kind, String from, String to) {
        StringBuilder faces = new StringBuilder();
        for (String face : new String[] {"north", "east", "south", "west", "up", "down"})
            faces.append(faces.isEmpty() ? "" : ",").append("\"").append(face).append("\":{\"texture\":\"#clear\"}");
        builder.addStringData(
                "assets/create/models/block/polymer/native_wall_" + kind + ".json",
                "{\"textures\":{\"clear\":\"polymer:block/empty\",\"particle\":\"minecraft:block/polished_tuff\"},"
                        + "\"elements\":[{\"from\":" + from + ",\"to\":" + to + ",\"faces\":{" + faces + "}}]}");
    }

    public static void addAssets(ResourcePackBuilder builder) {
        JsonObject crackState = DATA.getAsJsonObject("blockstate").deepCopy();
        for (var part : crackState.getAsJsonArray("multipart")) {
            JsonObject apply = part.getAsJsonObject().getAsJsonObject("apply");
            String model = apply.get("model").getAsString();
            String kind = model.endsWith("_post") ? "post" : model.endsWith("_side_tall") ? "side_tall" : "side";
            apply.addProperty("model", "create:block/polymer/native_wall_" + kind);
        }
        builder.addStringData("assets/minecraft/blockstates/polished_tuff_wall.json", crackState.toString());
        crackModel(builder, "post", "[4,0,4]", "[12,16,12]");
        crackModel(builder, "side", "[5,0,0]", "[11,14,8]");
        crackModel(builder, "side_tall", "[5,0,0]", "[11,16,8]");
        for (var entry : DATA.getAsJsonObject("meshes").entrySet()) {
            Identifier model = Identifier.parse(entry.getKey()), item = itemModel(model);
            builder.addStringData(
                    "assets/create/models/" + model.getPath() + ".json",
                    entry.getValue().toString());
            builder.addStringData(
                    "assets/create/items/" + item.getPath() + ".json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"}}");
        }
    }
}
