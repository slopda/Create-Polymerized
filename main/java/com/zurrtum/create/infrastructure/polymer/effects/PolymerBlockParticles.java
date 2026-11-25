package com.zurrtum.create.infrastructure.polymer.effects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllFluids;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.decoration.copycat.CopycatBlock;
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity;
import com.zurrtum.create.infrastructure.particle.FluidParticleData;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.block.PolymerMultipartBlocks;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerProxyMining;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class PolymerBlockParticles {
    private static final Map<BlockState, BlockState> STATES = new HashMap<>();
    private static final Map<Identifier, Identifier> MODELS = new LinkedHashMap<>();
    private static final Map<String, BlockState> FLUID_PARTICLES = new HashMap<>();
    private static final Map<Identifier, JsonObject> SOURCE_MODELS = new HashMap<>();
    private static final JsonObject VANILLA =
            PolymerBlockProjections.readAssetJson("/assets/create/polymer/particle_model_parents.json");

    public static BlockState get(BlockState state) {
        return STATES.get(state);
    }

    private static final Set<BlockState> PROXIES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<Boolean> CREATE_PARTICLES = new ThreadLocal<>();

    public static boolean isProxy(BlockState state) {
        return PROXIES.contains(state);
    }

    public static boolean encodingCreateParticles() {
        return Boolean.TRUE.equals(CREATE_PARTICLES.get());
    }

    public static void enterCreateParticles(boolean create) {
        if (create) CREATE_PARTICLES.set(Boolean.TRUE);
    }

    public static void exitCreateParticles() {
        CREATE_PARTICLES.remove();
    }

    public static BlockState material(ServerLevel level, BlockPos pos, BlockState source) {
        if (source.getBlock() instanceof CopycatBlock
                && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat) return copycat.getMaterial();
        return source;
    }

    public static int breakEvent(ServerLevel level, BlockPos pos, int data) {
        BlockState source = Block.stateById(data);
        BlockState material = material(level, pos, source);
        return Block.getId(STATES.getOrDefault(material, material));
    }

    public static void mining(ServerPlayer player, ServerLevel level, BlockPos pos, boolean destroying) {
        if (!destroying || level.getGameTime() % 4 != 0 || !level.hasChunkAt(pos)) return;
        BlockState source = level.getBlockState(pos);
        if (!BuiltInRegistries.BLOCK.getKey(source.getBlock()).getNamespace().equals(Create.MOD_ID)) return;
        BlockHitResult hit = PolymerProxyMining.hit(player, level, pos);
        if (hit == null) return;
        BlockState inherited = material(level, pos, source);
        var point = hit.getLocation().add(hit.getDirection().getUnitVec3().scale(.02));
        PolymerVanillaEffects.sourceParticles(
                level,
                new BlockParticleOption(ParticleTypes.BLOCK, inherited),
                point.x,
                point.y,
                point.z,
                2,
                .035,
                .035,
                .035,
                .02);
    }

    public static ParticleOptions project(ParticleOptions option) {
        if (option instanceof FluidParticleData fluid) {
            var id = BuiltInRegistries.FLUID.getKey(fluid.fluid());
            if (fluid.fluid().isSame(AllFluids.POTION)) {
                var contents = fluid.components().get(DataComponentMap.EMPTY, DataComponents.POTION_CONTENTS);
                int color = (contents == null ? PotionContents.EMPTY : contents).getColor();
                return new DustParticleOptions(color, .5f);
            }
            if (fluid.fluid().isSame(Fluids.LAVA)) return ParticleTypes.FALLING_LAVA;
            if (id.getNamespace().equals(Create.MOD_ID)) {
                BlockState proxy = FLUID_PARTICLES.get(id.getPath().replaceFirst("^flowing_", ""));
                if (proxy != null) return new BlockParticleOption(ParticleTypes.BLOCK, proxy);
            }
        }
        if (option instanceof BlockParticleOption block && block.getType() != ParticleTypes.BLOCK_MARKER) {
            BlockState proxy = STATES.get(block.getState());
            if (proxy != null) return new BlockParticleOption(block.getType(), proxy);
        }
        return option;
    }

    public static void register() {
        for (String fluid : new String[] {"honey", "chocolate", "milk", "tea"}) {
            Identifier texture = CreateIds.id("block/" + fluid + "_still");
            Identifier model = CreateIds.id("block/polymer/particle/fluid/" + fluid);
            BlockState proxy =
                    PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, PolymerBlockModel.of(model));
            if (proxy != null) {
                FLUID_PARTICLES.put(fluid, proxy);
                MODELS.put(model, texture);
                PROXIES.add(proxy);
            }
        }
        Map<Identifier, BlockState> textures = new HashMap<>();
        int missing = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(Create.MOD_ID) || block instanceof LiquidBlock) continue;
            JsonObject blockstates =
                    PolymerBlockProjections.readAssetJson("/assets/create/blockstates/" + id.getPath() + ".json");
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                Identifier texture = particle(blockstates, state);
                if (texture == null) {
                    missing++;
                    continue;
                }
                if (!textures.containsKey(texture)) {
                    Identifier model =
                            CreateIds.id("block/polymer/particle/" + texture.getNamespace() + "/" + texture.getPath());
                    BlockState proxy = PolymerBlockResourceUtils.requestBlock(
                            BlockModelType.FULL_BLOCK, PolymerBlockModel.of(model));
                    textures.put(texture, proxy);
                    if (proxy != null) {
                        MODELS.put(model, texture);
                        PROXIES.add(proxy);
                    }
                }
                BlockState proxy = textures.get(texture);
                if (proxy != null) STATES.put(state, proxy);
            }
        }
        long unavailable = textures.values().stream().filter(Objects::isNull).count();
        Create.LOGGER.info(
                "Create break-particle projections: {} source states, {} original textures; {} unmapped states, {} unavailable texture slots",
                STATES.size(),
                MODELS.size(),
                missing,
                unavailable);
    }

    public static @Nullable Identifier particleModel(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Identifier texture = particle(
                PolymerBlockProjections.readAssetJson("/assets/create/blockstates/" + id.getPath() + ".json"), state);
        if (texture == null) return null;
        Identifier model = CreateIds.id("block/polymer/particle/" + texture.getNamespace() + "/" + texture.getPath());
        MODELS.put(model, texture);
        return model;
    }

    private static Identifier particle(JsonObject blockstates, BlockState state) {
        if (blockstates == null) return null;
        if (blockstates.has("variants")) {
            JsonObject variants = blockstates.getAsJsonObject("variants");
            String key = PolymerBlockProjections.selectMatchingVariant(variants, state);
            if (key != null) return variantParticle(variants.get(key));
        }
        if (blockstates.has("multipart")) {
            var parts = blockstates.getAsJsonArray("multipart");
            if (!parts.isEmpty()) {
                Identifier first =
                        variantParticle(parts.get(0).getAsJsonObject().get("apply"));
                if (first != null) return first;
            }
            for (JsonElement value : parts) {
                JsonObject part = value.getAsJsonObject();
                if (part.has("when") && !PolymerMultipartBlocks.matches(part.getAsJsonObject("when"), state)) continue;
                Identifier particle = variantParticle(part.get("apply"));
                if (particle != null) return particle;
            }
        }
        return null;
    }

    private static Identifier variantParticle(JsonElement value) {
        if (value == null) return null;
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                Identifier found = variantParticle(entry);
                if (found != null) return found;
            }
            return null;
        }
        JsonObject variant = value.getAsJsonObject();
        if (!variant.has("model")) return null;
        Map<String, String> textures = new HashMap<>();
        textureSlots(Identifier.parse(variant.get("model").getAsString()), textures, 0);
        String particle = textures.get("particle");
        for (int depth = 0; particle != null && particle.startsWith("#") && depth < 32; depth++)
            particle = textures.get(particle.substring(1));
        if (particle == null || particle.startsWith("#")) return null;
        Identifier texture = Identifier.parse(particle);
        return texture.equals(Identifier.withDefaultNamespace("missingno")) ? null : texture;
    }

    private static void textureSlots(Identifier id, Map<String, String> textures, int depth) {
        if (depth >= 32) return;
        JsonObject model;
        if (id.getNamespace().equals("minecraft"))
            model = VANILLA == null ? null : VANILLA.getAsJsonObject(id.toString());
        else {
            if (!SOURCE_MODELS.containsKey(id))
                SOURCE_MODELS.put(
                        id,
                        PolymerBlockProjections.readAssetJson(
                                "/assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json"));
            model = SOURCE_MODELS.get(id);
        }
        if (model == null) return;
        if (model.has("parent"))
            textureSlots(Identifier.parse(model.get("parent").getAsString()), textures, depth + 1);
        if (model.has("textures"))
            model.getAsJsonObject("textures").entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive())
                    textures.put(entry.getKey(), entry.getValue().getAsString());
            });
    }

    public static void addAssets(ResourcePackBuilder builder) {
        MODELS.forEach((id, texture) -> {
            JsonObject definition = new JsonObject(), textures = new JsonObject();
            textures.addProperty("particle", texture.toString());
            definition.add("textures", textures);
            definition.add("elements", new JsonArray());
            builder.addStringData("assets/create/models/" + id.getPath() + ".json", definition.toString());
        });
    }
}
