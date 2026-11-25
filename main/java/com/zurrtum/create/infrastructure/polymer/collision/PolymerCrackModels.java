package com.zurrtum.create.infrastructure.polymer.collision;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.mixin.BlockResourceCreatorAccessor;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class PolymerCrackModels {
    private static final String CLEAR = "polymer:block/empty";
    private static final int MAX_BOXES = 16;
    private static final Map<Identifier, JsonObject> MODELS = new LinkedHashMap<>();

    public static final Identifier CUBE = model(Shapes.block());

    private PolymerCrackModels() {}

    public static synchronized Identifier model(VoxelShape shape) {
        List<AABB> boxes = shape.toAabbs();
        if (boxes.isEmpty() || boxes.size() > MAX_BOXES)
            boxes = List.of(shape.isEmpty() ? Shapes.block().bounds() : shape.bounds());
        JsonArray elements = new JsonArray();
        for (AABB box : boxes) {
            double[] from = {clamp(box.minX), clamp(box.minY), clamp(box.minZ)};
            double[] to = {clamp(box.maxX), clamp(box.maxY), clamp(box.maxZ)};
            JsonObject element = new JsonObject();
            element.add("from", vector(from));
            element.add("to", vector(to));
            JsonObject faces = new JsonObject();
            for (Direction direction : Direction.values()) {
                JsonObject face = new JsonObject();
                face.addProperty("texture", "#clear");
                faces.add(direction.getSerializedName(), face);
            }
            element.add("faces", faces);
            elements.add(element);
        }
        Identifier id = CreateIds.id(
                "block/polymer/crack/" + Integer.toHexString(elements.toString().hashCode()));
        MODELS.computeIfAbsent(id, key -> {
            JsonObject textures = new JsonObject();
            textures.addProperty("clear", CLEAR);
            textures.addProperty("particle", CLEAR);
            JsonObject model = new JsonObject();
            model.add("textures", textures);
            model.add("elements", elements);
            return model;
        });
        return id;
    }

    public static @Nullable BlockState request(BlockModelType type, VoxelShape shape) {
        PolymerBlockModel crack = PolymerBlockModel.of(model(shape));
        BlockState state = type.name().startsWith("TRAPDOOR_")
                ? BlockResourceCreatorAccessor.polymerpatch$getSharedCreator()
                        .requestBlock(type, s -> s.is(Blocks.IRON_TRAPDOOR), crack)
                : null;
        if (state == null) state = PolymerBlockResourceUtils.requestBlock(type, crack);
        return state != null ? state : PolymerBlockResourceUtils.requestEmpty(type);
    }

    public static synchronized void addAssets(ResourcePackBuilder builder) {
        MODELS.forEach((id, model) -> builder.addStringData(
                "assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json", model.toString()));
    }

    private static double clamp(double value) {
        return Math.max(-16, Math.min(32, value * 16));
    }

    private static JsonArray vector(double[] values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(value);
        return array;
    }
}
