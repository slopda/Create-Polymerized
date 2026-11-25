package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class PolymerBracketParts {
    private static final Map<BlockState, Variant> VARIANTS = new HashMap<>();

    private PolymerBracketParts() {}

    static void register() {
        load(AllBlocks.WOODEN_BRACKET);
        load(AllBlocks.METAL_BRACKET);
    }

    private static void load(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        JsonObject root = PolymerBlockProjections.readAssetJson("/assets/create/blockstates/" + id.getPath() + ".json");
        if (root == null || !root.has("variants")) return;
        JsonObject variants = root.getAsJsonObject("variants");
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            String selector = PolymerBlockProjections.selectMatchingVariant(variants, state);
            if (selector == null) continue;
            JsonElement raw = variants.get(selector);
            if (raw == null || !raw.isJsonObject()) continue;
            JsonObject value = raw.getAsJsonObject();
            if (!value.has("model")) continue;
            Identifier model = Identifier.parse(value.get("model").getAsString());
            PolymerBlockParts.model(model);
            VARIANTS.put(
                    state,
                    new Variant(
                            model,
                            value.has("x") ? value.get("x").getAsFloat() : 0,
                            value.has("y") ? value.get("y").getAsFloat() : 0));
        }
    }

    static void collect(BlockEntity blockEntity, List<PolymerBlockParts.Part> out) {
        if (!(blockEntity instanceof SmartBlockEntity smart) || smart.getLevel() == null) return;
        BracketedBlockEntityBehaviour behaviour =
                BlockEntityBehaviour.get(smart.getLevel(), smart.getBlockPos(), BracketedBlockEntityBehaviour.TYPE);
        if (behaviour == null) return;
        BlockState bracket = behaviour.getBracket();
        if (bracket == null) return;
        Variant variant = VARIANTS.get(bracket);
        if (variant == null) return;

        Quaternionf rotation =
                new Quaternionf().rotateYXZ((float) Math.toRadians(-variant.y), (float) Math.toRadians(-variant.x), 0);
        Matrix4f pose = PolymerBlockParts.rotateCentered(rotation);
        out.add(PolymerBlockParts.part(variant.model, pose).identified("bracket"));
    }

    private record Variant(Identifier model, float x, float y) {}
}
