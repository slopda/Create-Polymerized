package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.contraptions.actors.seat.SeatBlock;
import com.zurrtum.create.content.contraptions.chassis.StickerBlock;
import com.zurrtum.create.content.decoration.MetalLadderBlock;
import com.zurrtum.create.content.decoration.TrainTrapdoorBlock;
import com.zurrtum.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.zurrtum.create.content.equipment.clipboard.ClipboardBlock;
import com.zurrtum.create.content.kinetics.crank.ValveHandleBlock;
import com.zurrtum.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlock;
import com.zurrtum.create.content.logistics.vault.ItemVaultBlock;
import com.zurrtum.create.content.trains.track.TrackBlock;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDecorativeCulling;
import com.zurrtum.create.infrastructure.polymer.block.PolymerFluidVisuals;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.block.PolymerMultipartBlocks;
import com.zurrtum.create.infrastructure.polymer.block.PolymerTableClothVisuals;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerBlockCollision;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerBounceBlocks;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerLadders;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerProxyMining;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerShapeCollision;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerStaticCollision;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerWallCollision;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerSeatCarriers;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerBlockParticles;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrackVisuals;
import dev.veeda.createpoly.PatchAssets;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.mixin.BlockResourceCreatorAccessor;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class PolymerBlockProjections {
    private static final Map<TexturedStateKey, BlockState> TEXTURED_STATES = new HashMap<>();
    private static final Set<TexturedStateKey> UNAVAILABLE_TEXTURED_STATES = new HashSet<>();
    private static final Map<Identifier, BlockState> POLYMER_MODEL_STATES = new HashMap<>();
    public static final Set<Identifier> DISPLAY_BLOCK_MODELS = new HashSet<>();
    private static final Map<BlockState, PolymerBlockModel> MOVING_DISPLAY_MODELS = new HashMap<>();

    static Identifier resolveModelAlias(Identifier source) {
        return VANILLA_MODEL_ALIASES.getOrDefault(source, source);
    }

    static final Map<Identifier, Identifier> VANILLA_MODEL_ALIASES = Map.ofEntries(
            Map.entry(modelId("block/copper_backtank/block"), modelId("block/polymer/copper_backtank")),
            Map.entry(modelId("block/netherite_backtank/block"), modelId("block/polymer/netherite_backtank")),
            Map.entry(modelId("block/schematicannon/block"), modelId("block/polymer/schematicannon")),
            Map.entry(modelId("block/schematic_table/block"), modelId("block/polymer/schematic_table")),
            Map.entry(modelId("block/cogwheel/block"), modelId("block/polymer/cogwheel")),
            Map.entry(modelId("block/chain_conveyor/block"), modelId("block/polymer/chain_conveyor")),
            Map.entry(modelId("block/water_wheel/wheel"), modelId("block/polymer/water_wheel_rotor")),
            Map.entry(modelId("block/large_water_wheel/block"), modelId("block/polymer/large_water_wheel")),
            Map.entry(
                    modelId("block/large_water_wheel/block_extension"),
                    modelId("block/polymer/large_water_wheel_extension")),
            Map.entry(modelId("block/crushing_wheel/block"), modelId("block/polymer/crushing_wheel")),
            Map.entry(modelId("block/blaze_burner/block"), modelId("block/polymer/blaze_burner")),
            Map.entry(modelId("block/blaze_burner/block_with_fire"), modelId("block/polymer/blaze_burner_lit")),
            Map.entry(modelId("block/blaze_burner/block_with_soul_fire"), modelId("block/polymer/blaze_burner_soul")),
            Map.entry(modelId("block/valve_handle"), modelId("block/polymer/valve_handle")),
            Map.entry(modelId("block/mechanical_roller/wheel"), modelId("block/polymer/mechanical_roller_wheel")),
            Map.entry(modelId("block/flywheel/block"), modelId("block/polymer/flywheel")),
            Map.entry(modelId("block/flywheel/flywheel"), modelId("block/polymer/flywheel_rotor")),
            Map.entry(modelId("block/track/ascending"), modelId("block/polymer/track_ascending")));
    private static final Map<Identifier, Float> SCALED_DISPLAY_MODELS = Map.of();
    private static final Set<String> PRIORITY_BLOCKS = Set.of(
            "copper_backtank",
            "netherite_backtank",
            "schematicannon",
            "schematic_table",
            "chain_conveyor",
            "large_water_wheel",
            "crushing_wheel",
            "blaze_burner",
            "lit_blaze_burner",
            "copper_valve_handle",
            "mechanical_roller",
            "flywheel");
    private static final List<Identifier> PRIORITY_MODEL_IDS = VANILLA_MODEL_ALIASES.values().stream()
            .filter(id -> !id.equals(modelId("block/polymer/cogwheel")))
            .filter(id -> !SCALED_DISPLAY_MODELS.containsKey(id))
            .distinct()
            .sorted(Comparator.comparing(Identifier::toString))
            .toList();
    public static final BlockState KINETIC_BASE_CARRIER_STATE = Blocks.BARRIER.defaultBlockState();

    private PolymerBlockProjections() {}

    static void registerBlocks() {
        PolymerBounceBlocks.register();
        PolymerLadders.registerNativeHost();
        PolymerBlockCollision.reserveCarriers();
        PolymerWallCollision.register();
        PolymerStaticCollision.register();
        PolymerShapeCollision.register();
        int fallbackCount = 0;
        int texturedCount = 0;
        int blocksWithStateVariants = 0;
        int additionalStateMappings = 0;
        int unsupportedStateModelFallbacks = 0;
        int unavailableStateModelFallbacks = 0;
        int displayBackedStates = 0;
        int displayBackedBlocks = 0;
        for (Identifier modelId : PRIORITY_MODEL_IDS) {
            BlockState state = requestTexturedState(PolymerBlockModel.of(modelId), BlockModelType.LEAVES);
            if (state == null) {
                Create.LOGGER.warn("Polymer could not allocate priority Create model state for {}", modelId);
            } else {
                POLYMER_MODEL_STATES.put(modelId, state);
            }
        }
        Create.LOGGER.info(
                "Reserved {} high-priority vanilla model states for reported Create machine visuals",
                POLYMER_MODEL_STATES.size());
        List<BlockModelPlan> plans = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null || !id.getNamespace().equals(Create.MOD_ID)) {
                continue;
            }

            if (block == AllBlocks.CRUSHING_WHEEL_CONTROLLER) {
                PolymerBlock.registerOverlay(block, new CrushingControllerProjection());
                fallbackCount++;
                continue;
            }

            if (block == AllBlocks.FAKE_TRACK) {
                PolymerBlock.registerOverlay(block, new InvisibleBlockProjection());
                fallbackCount++;
                continue;
            }

            if (PolymerFluidVisuals.register(block)) {
                displayBackedBlocks++;
                texturedCount++;
                continue;
            }
            if (block == AllBlocks.TURNTABLE) {
                BlockState carrier = PolymerBlockResourceUtils.requestEmpty(BlockModelType.SLAB_BOTTOM);
                if (carrier == null) throw new IllegalStateException("No half-height carrier available for Turntable");
                PolymerBlock.registerOverlay(block, new FixedBlockProjection(carrier));
                displayBackedBlocks++;
                texturedCount++;
                continue;
            }
            if (block instanceof ValveHandleBlock
                    || block == AllBlocks.CHAIN_CONVEYOR
                    || block == AllBlocks.WATER_WHEEL
                    || block == AllBlocks.WATER_WHEEL_STRUCTURAL) {
                PolymerBlock.registerOverlay(block, new FixedBlockProjection(KINETIC_BASE_CARRIER_STATE));
                texturedCount++;
                continue;
            }
            if (block instanceof SlidingDoorBlock) {
                PolymerBlock.registerOverlay(block, new DoorCarrierProjection());
                texturedCount++;
                continue;
            }

            if (block == AllBlocks.ITEM_VAULT) {
                PolymerBlock.registerOverlay(block, new FixedBlockProjection(Blocks.BARRIER.defaultBlockState()));
                Identifier vaultModel = modelId("block/item_vault");
                for (BlockState sourceState : block.getStateDefinition().getPossibleStates())
                    MOVING_DISPLAY_MODELS.put(
                            sourceState,
                            PolymerBlockModel.of(
                                    vaultModel,
                                    0,
                                    ItemVaultBlock.getVaultBlockAxis(sourceState) == Direction.Axis.X ? 90 : 0,
                                    false));
                DISPLAY_BLOCK_MODELS.add(vaultModel);
                texturedCount++;
                continue;
            }
            if (block == AllBlocks.COPYCAT_PANEL || block == AllBlocks.COPYCAT_STEP) {
                texturedCount++;
                continue;
            }
            if (block == AllBlocks.SCHEMATICANNON) {
                PolymerBlock.registerOverlay(block, new FixedBlockProjection(Blocks.BARRIER.defaultBlockState()));
                texturedCount++;
                continue;
            }

            if (PolymerMultipartBlocks.register(id, block)) {
                displayBackedBlocks++;
                texturedCount++;
                continue;
            }

            BlockModelPlan plan = blockModelPlan(id, block);
            if (plan == null) {
                Create.LOGGER.info("Polymer material fallback (no usable model): {} -> {}", id, fallbackState(id));
                PolymerBlock.registerOverlay(block, new BlockProjection(fallbackState(id)));
                fallbackCount++;
                continue;
            }
            plans.add(plan);
        }
        plans.sort(Comparator.comparingInt(
                plan -> PRIORITY_BLOCKS.contains(plan.id().getPath()) ? 0 : 1));

        List<ResolvedBlockModelPlan> resolvedPlans = new ArrayList<>();
        for (BlockModelPlan plan : plans) {
            if (PolymerKineticVisuals.rotatingItemModel(plan.block().defaultBlockState()) != null
                    || plan.block() == AllBlocks.LARGE_WATER_WHEEL) {
                PolymerBlock.registerOverlay(plan.block(), new FixedBlockProjection(KINETIC_BASE_CARRIER_STATE));
                for (BlockState sourceState : plan.block().getStateDefinition().getPossibleStates()) {
                    String selector = selectMatchingVariant(plan.variants(), sourceState);
                    PolymerBedrockCarriers.candidate(
                            sourceState,
                            plan.models()
                                    .getOrDefault(
                                            selector == null ? plan.defaultSelector() : selector,
                                            plan.models().get(plan.defaultSelector())));
                }
                texturedCount++;
                continue;
            }
            BlockState defaultClientState = requestTexturedState(
                    plan.models().get(plan.defaultSelector()),
                    modelTypeFor(plan.block().defaultBlockState()));
            resolvedPlans.add(new ResolvedBlockModelPlan(plan, defaultClientState));
        }

        for (ResolvedBlockModelPlan resolved : resolvedPlans) {
            BlockModelPlan plan = resolved.plan();
            Block block = plan.block();
            BlockState defaultClientState = resolved.defaultClientState();
            PolymerBlockModel defaultModel = plan.models().get(plan.defaultSelector());
            Map<BlockState, BlockState> clientStates = new LinkedHashMap<>();
            Map<BlockState, PolymerBlockModel> displayModels = new LinkedHashMap<>();
            for (BlockState sourceState : block.getStateDefinition().getPossibleStates()) {
                String selector = selectMatchingVariant(plan.variants(), sourceState);
                PolymerBlockModel model =
                        selector == null ? null : plan.models().get(selector);
                if (model == null) {
                    unsupportedStateModelFallbacks++;
                    model = defaultModel;
                }
                if (sourceState.is(AllBlocks.GANTRY_CARRIAGE) && model != null) {
                    MOVING_DISPLAY_MODELS.put(sourceState, model);
                    DISPLAY_BLOCK_MODELS.add(model.model());
                }
                if (PolymerTrackVisuals.isDrawnShape(sourceState)) {
                    clientStates.put(sourceState, PolymerStaticCollision.carrier(sourceState));
                    continue;
                }
                if (PolymerStaticCollision.targets(sourceState)
                        || sourceState.getBlock() instanceof StickerBlock
                                && sourceState.getValue(BlockStateProperties.FACING) == Direction.UP) {
                    displayModels.put(sourceState, model);
                    displayBackedStates++;
                    continue;
                }
                if (model != null
                        && (SCALED_DISPLAY_MODELS.containsKey(model.model())
                                || sourceState.is(AllBlocks.CONTROLLER_RAIL))) {
                    displayModels.put(sourceState, model);
                    displayBackedStates++;
                    continue;
                }
                BlockState clientState =
                        sourceState.getBlock() instanceof TrainTrapdoorBlock ? pairedTrapdoor(sourceState, plan) : null;
                if (clientState == null)
                    clientState = model == null ? null : requestTexturedState(model, modelTypeFor(sourceState));
                if (clientState != null
                        && !sourceState.is(AllBlocks.WEIGHTED_EJECTOR)
                        && (!sourceState.getFluidState().isEmpty()
                                == !clientState.getFluidState().isEmpty())
                        && (PolymerBlockCollision.typeFor(sourceState) == null
                                || PolymerBlockCollision.sameShape(
                                        PolymerBlockCollision.shape(sourceState),
                                        PolymerBlockCollision.shape(clientState)))) {
                    if (clientState != defaultClientState) {
                        additionalStateMappings++;
                    }
                    clientStates.put(sourceState, clientState);
                    if (model != null) GEYSER_BLOCK_SOURCES.putIfAbsent(clientState, Map.entry(sourceState, model));
                    continue;
                }
                unavailableStateModelFallbacks++;
                if (defaultClientState != null
                        && sourceState.isSolidRender()
                        && (!sourceState.getFluidState().isEmpty()
                                == !defaultClientState.getFluidState().isEmpty())
                        && PolymerBlockCollision.sameShape(
                                PolymerBlockCollision.shape(sourceState),
                                PolymerBlockCollision.shape(defaultClientState))) {
                    clientStates.put(sourceState, defaultClientState);
                } else if (model != null) {
                    displayModels.put(sourceState, model);
                    displayBackedStates++;
                } else {
                    clientStates.put(sourceState, fallbackState(plan.id()));
                }
            }

            if (plan.variants().size() > 1 || !plan.defaultSelector().isEmpty()) {
                blocksWithStateVariants++;
            }
            BlockState replacement = defaultClientState != null ? defaultClientState : KINETIC_BASE_CARRIER_STATE;
            MOVING_DISPLAY_MODELS.putAll(displayModels);
            displayModels.forEach(PolymerBedrockCarriers::candidate);
            if (!(block instanceof TrackBlock))
                DISPLAY_BLOCK_MODELS.addAll(displayModels.values().stream()
                        .map(PolymerBlockModel::model)
                        .toList());
            displayBackedStates -= PolymerTerrainPromotion.promote(clientStates, displayModels);
            PolymerBlock.registerOverlay(
                    block,
                    new TexturedBlockProjection(replacement, Map.copyOf(clientStates), Map.copyOf(displayModels)));
            if (block instanceof TrackBlock) {
                BlockWithElementHolder.registerOverlay(block, PolymerTrackVisuals.shapeHolderProvider());
            } else if (!displayModels.isEmpty()) {
                BlockWithElementHolder.registerOverlay(
                        block, new ModelDisplayHolderProvider(Map.copyOf(displayModels)));
                displayBackedBlocks++;
                DISPLAY_BLOCK_MODELS.addAll(displayModels.values().stream()
                        .map(PolymerBlockModel::model)
                        .toList());
            }
            texturedCount++;
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof TableClothBlock) {
                PolymerTableClothVisuals.register(block);
                PolymerBlock.registerOverlay(block, new FixedBlockProjection(PolymerTableClothVisuals.carrier()));
            }
            if (block instanceof SeatBlock) PolymerSeatCarriers.register(block);
            if (block instanceof MetalLadderBlock) PolymerLadders.register(block);
        }
        PolymerBlockParticles.register();
        PolymerBedrockCarriers.allocate(
                (model, source) -> requestTexturedState(model, BlockModelType.FULL_BLOCK),
                (carrier, source, model) -> GEYSER_BLOCK_SOURCES.putIfAbsent(carrier, Map.entry(source, model)));
        writeGeyserBlockSources();
        Create.LOGGER.info(
                "Registered Polymer projections for {} Create blocks ({} resource-pack models, {} with source-state variants, {} additional state mappings, {} source states without a supported model, {} source states without an allocated model state, {} material fallbacks)",
                texturedCount + fallbackCount,
                texturedCount,
                blocksWithStateVariants,
                additionalStateMappings,
                unsupportedStateModelFallbacks,
                unavailableStateModelFallbacks,
                fallbackCount);
        Create.LOGGER.info(
                "Polymer distinct block models allocated: {}, unavailable due to FULL_BLOCK capacity: {}",
                TEXTURED_STATES.size(),
                UNAVAILABLE_TEXTURED_STATES.size());
        Create.LOGGER.info(
                "Polymer FULL_BLOCK model-state slots remaining after Create projections: {}",
                PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.FULL_BLOCK));
        Create.LOGGER.info(
                "Polymer non-occluding model-state slots remaining: LEAVES={}, LEAVES_WATERLOGGED={}",
                PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.LEAVES),
                PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.LEAVES_WATERLOGGED));
        StringBuilder pools = new StringBuilder();
        for (BlockModelType type : BlockModelType.values())
            pools.append(pools.isEmpty() ? "" : ", ")
                    .append(type.name())
                    .append('=')
                    .append(PolymerBlockResourceUtils.getBlocksLeft(type));
        Create.LOGGER.info("Polymer model-state slots left per pool: {}", pools);
        PolymerTerrainPromotion.log();
        Create.LOGGER.info(
                "Polymer display-backed block models: {} source states across {} blocks ({} distinct models)",
                displayBackedStates,
                displayBackedBlocks,
                DISPLAY_BLOCK_MODELS.size());
        Create.LOGGER.info("Polymer kinetic base carrier state: {}", describeState(KINETIC_BASE_CARRIER_STATE));
    }

    private static String describeState(@Nullable BlockState state) {
        return state == null ? "unavailable" : state.toString();
    }

    private static final Map<BlockState, Map.Entry<BlockState, PolymerBlockModel>> GEYSER_BLOCK_SOURCES =
            new LinkedHashMap<>();

    private static void writeGeyserBlockSources() {
        JsonObject root = new JsonObject();
        for (var entry : GEYSER_BLOCK_SOURCES.entrySet()) {
            BlockState carrier = entry.getKey();
            String key = BuiltInRegistries.BLOCK.getKey(carrier.getBlock()) + "["
                    + carrier.getProperties().stream()
                            .sorted(Comparator.comparing(Property::getName))
                            .map(property -> property.getName() + "=" + propertyValueName(carrier, property))
                            .collect(Collectors.joining(","))
                    + "]";
            JsonObject value = new JsonObject();
            value.addProperty("source", entry.getValue().getKey().toString());
            value.addProperty("model", entry.getValue().getValue().model().toString());
            value.addProperty("x", entry.getValue().getValue().x());
            value.addProperty("y", entry.getValue().getValue().y());
            root.add(key, value);
        }
        try {
            Path out = Path.of("polymerpatch", "geyser_block_sources.json");
            Files.createDirectories(out.getParent());
            Files.writeString(
                    out, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException | RuntimeException e) {
            Create.LOGGER.warn("Could not write polymerpatch/geyser_block_sources.json", e);
        }
    }

    public static @Nullable BlockState getPolymerModelState(String modelPath) {
        return POLYMER_MODEL_STATES.get(modelId(modelPath));
    }

    static Identifier modelId(String path) {
        return CreateIds.id(path);
    }

    private static BlockModelPlan blockModelPlan(Identifier id, Block block) {
        JsonObject blockstateJson = readAssetJson("/assets/create/blockstates/" + id.getPath() + ".json");
        if (blockstateJson == null || !blockstateJson.has("variants")) {
            return null;
        }

        JsonObject variants = blockstateJson.getAsJsonObject("variants");
        Map.Entry<String, JsonElement> selectedVariant = selectDefaultVariant(variants, block.defaultBlockState());
        if (selectedVariant == null) {
            return null;
        }

        Map<String, PolymerBlockModel> models = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
            PolymerBlockModel model = polymerModel(entry.getValue());
            if (model != null) {
                models.put(entry.getKey(), model);
            }
        }
        if (!models.containsKey(selectedVariant.getKey())) {
            return null;
        }
        return new BlockModelPlan(id, block, variants, Map.copyOf(models), selectedVariant.getKey());
    }

    public static PolymerBlockModel polymerModel(JsonElement value) {
        JsonObject modelVariant = firstModel(value);
        if (modelVariant == null || !modelVariant.has("model")) {
            return null;
        }

        Identifier sourceModelId = parseIdentifier(modelVariant.get("model").getAsString());
        if (sourceModelId == null || !sourceModelId.getNamespace().equals(Create.MOD_ID)) {
            return null;
        }
        Identifier modelId = VANILLA_MODEL_ALIASES.getOrDefault(sourceModelId, sourceModelId);
        if (!hasVanillaModelDefinition(modelId)) {
            return null;
        }

        return PolymerBlockModel.of(
                modelId,
                modelVariant.has("x") ? modelVariant.get("x").getAsInt() : 0,
                modelVariant.has("y") ? modelVariant.get("y").getAsInt() : 0,
                modelVariant.has("uvlock") && modelVariant.get("uvlock").getAsBoolean());
    }

    private static BlockState emptyTrackState;

    private static BlockState emptyTrackState() {
        if (emptyTrackState == null) {
            emptyTrackState = PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRIPWIRE_FLAT);
            if (emptyTrackState == null) {
                emptyTrackState = Blocks.AIR.defaultBlockState();
            }
        }
        return emptyTrackState;
    }

    private static BlockModelType modelTypeFor(BlockState sourceState) {
        BlockModelType exact = PolymerBlockCollision.typeFor(sourceState);
        if (exact != null) return exact;
        // Only full opaque cubes may use FULL_BLOCK: canOcclude() is also true for many non-full Create blocks,
        // whose note-block projection then culled neighbours.
        if (sourceState.isSolidRender()) {
            return BlockModelType.FULL_BLOCK;
        }
        if (sourceState.hasProperty(BlockStateProperties.WATERLOGGED)
                && sourceState.getValue(BlockStateProperties.WATERLOGGED)) {
            return BlockModelType.LEAVES_WATERLOGGED;
        }
        return BlockModelType.LEAVES;
    }

    private static final Map<BlockState, BlockState> TRAPDOOR_PAIRS = new HashMap<>();

    private static BlockState pairedTrapdoor(BlockState source, BlockModelPlan plan) {
        BlockState done = TRAPDOOR_PAIRS.get(source);
        if (done != null) return done;
        BlockState partner = source.cycle(TrapDoorBlock.OPEN);
        String s1 = selectMatchingVariant(plan.variants(), source),
                s2 = selectMatchingVariant(plan.variants(), partner);
        PolymerBlockModel m1 = s1 == null ? null : plan.models().get(s1),
                m2 = s2 == null ? null : plan.models().get(s2);
        BlockModelType t1 = modelTypeFor(source), t2 = modelTypeFor(partner);
        if (m1 == null
                || m2 == null
                || t1 == null
                || t2 == null
                || !t1.name().startsWith("TRAPDOOR_")
                || !t2.name().startsWith("TRAPDOOR_")) return null;
        var creator = BlockResourceCreatorAccessor.polymerpatch$getSharedCreator();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof TrapDoorBlock)
                    || block == Blocks.IRON_TRAPDOOR
                    || !BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) continue;
            BlockState v1 = block.withPropertiesOf(source);
            BlockState v2 = v1.cycle(TrapDoorBlock.OPEN);
            BlockState r1 = creator.requestBlock(t1, state -> state == v1, m1);
            if (r1 == null) continue;
            BlockState r2 = creator.requestBlock(t2, state -> state == v2, m2);
            if (r2 == null) continue;
            TRAPDOOR_PAIRS.put(source, r1);
            TRAPDOOR_PAIRS.put(partner, r2);
            return r1;
        }
        return null;
    }

    private static BlockState requestTexturedState(PolymerBlockModel model, BlockModelType type) {
        if (model == null) {
            return null;
        }
        TexturedStateKey key = new TexturedStateKey(type, model);
        BlockState cached = TEXTURED_STATES.get(key);
        if (cached != null) {
            return cached;
        }
        if (UNAVAILABLE_TEXTURED_STATES.contains(key)) {
            return null;
        }

        BlockState requested = null;
        if (type.name().startsWith("TRAPDOOR_")) {
            requested = BlockResourceCreatorAccessor.polymerpatch$getSharedCreator()
                    .requestBlock(type, state -> state.is(Blocks.IRON_TRAPDOOR), model);
        }
        if (requested == null) requested = PolymerBlockResourceUtils.requestBlock(type, model);
        if (requested != null) {
            TEXTURED_STATES.put(key, requested);
        } else {
            UNAVAILABLE_TEXTURED_STATES.add(key);
        }
        return requested;
    }

    private static Map.Entry<String, JsonElement> selectDefaultVariant(JsonObject variants, BlockState state) {
        String selected = selectMatchingVariant(variants, state);
        if (selected != null) {
            return Map.entry(selected, variants.get(selected));
        }
        return variants.entrySet().stream().findFirst().orElse(null);
    }

    public static String selectMatchingVariant(JsonObject variants, BlockState state) {
        String selected = null;
        int selectedSpecificity = -1;
        for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
            int specificity = selectorSpecificity(state, entry.getKey());
            if (specificity > selectedSpecificity) {
                selected = entry.getKey();
                selectedSpecificity = specificity;
            }
        }
        return selectedSpecificity < 0 ? null : selected;
    }

    private static int selectorSpecificity(BlockState state, String selector) {
        if (selector.isEmpty()) {
            return 0;
        }

        int specificity = 0;
        for (String condition : selector.split(",")) {
            String[] pair = condition.split("=", 2);
            if (pair.length != 2) {
                return -1;
            }
            Property<?> property = state.getBlock().getStateDefinition().getProperty(pair[0].trim());
            if (property == null || !propertyValueName(state, property).equals(pair[1].trim())) {
                return -1;
            }
            specificity++;
        }
        return specificity;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(BlockState state, Property property) {
        return property.getName((Comparable) state.getValue(property));
    }

    private static JsonObject firstModel(JsonElement value) {
        if (value.isJsonObject()) {
            return value.getAsJsonObject();
        }
        if (value.isJsonArray()) {
            JsonArray variants = value.getAsJsonArray();
            for (JsonElement candidate : variants) {
                if (candidate.isJsonObject() && candidate.getAsJsonObject().has("model")) {
                    return candidate.getAsJsonObject();
                }
            }
        }
        return null;
    }

    private static boolean hasVanillaModelDefinition(Identifier modelId) {
        return hasVanillaModelDefinition(modelId, new HashSet<>());
    }

    private static boolean hasVanillaModelDefinition(Identifier modelId, Set<Identifier> parents) {
        if (modelId.getNamespace().equals("minecraft")) {
            return true;
        }
        if (!modelId.getNamespace().equals(Create.MOD_ID) || !parents.add(modelId)) {
            return false;
        }

        String resourcePath = "/assets/" + modelId.getNamespace() + "/models/" + modelId.getPath() + ".json";
        JsonObject modelJson = readAssetJson(resourcePath);
        if (modelJson == null || modelJson.has("loader")) {
            return false;
        }
        return !modelJson.has("parent")
                || hasVanillaModelDefinition(
                        parseIdentifier(modelJson.get("parent").getAsString()), parents);
    }

    public static JsonObject readAssetJson(String resourcePath) {
        try (InputStream stream = PatchAssets.open(resourcePath)) {
            if (stream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            return null;
        }
    }

    private static Identifier parseIdentifier(String value) {
        int separator = value.indexOf(':');
        if (separator < 0) {
            return Identifier.fromNamespaceAndPath("minecraft", value);
        }
        return Identifier.fromNamespaceAndPath(value.substring(0, separator), value.substring(separator + 1));
    }

    private static BlockState fallbackState(Identifier id) {
        String path = id.getPath();
        if (path.contains("andesite")) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        if (path.contains("brass")) {
            return Blocks.GOLD_BLOCK.defaultBlockState();
        }
        if (path.contains("zinc") || path.contains("industrial_iron")) {
            return Blocks.IRON_BLOCK.defaultBlockState();
        }
        if (path.contains("copper")) {
            return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
        }
        if (path.contains("rose_quartz")) {
            return Blocks.AMETHYST_BLOCK.defaultBlockState();
        }
        if (path.contains("shadow_steel")) {
            return Blocks.OBSIDIAN.defaultBlockState();
        }
        if (path.contains("refined_radiance")) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        if (path.contains("deepslate")) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        if (path.contains("diorite")) {
            return Blocks.POLISHED_DIORITE.defaultBlockState();
        }
        if (path.contains("granite")) {
            return Blocks.POLISHED_GRANITE.defaultBlockState();
        }
        if (path.contains("wood") || path.contains("cardboard")) {
            return Blocks.OAK_PLANKS.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private record TexturedStateKey(BlockModelType type, PolymerBlockModel model) {}

    private record DoorCarrierProjection() implements PolymerCreateBlock, PolymerTexturedBlock {
        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            BlockState fallback = state.getValue(BlockStateProperties.OPEN)
                    ? Blocks.STRUCTURE_VOID.defaultBlockState()
                    : KINETIC_BASE_CARRIER_STATE;
            return PolymerBlockCollision.emptyCarrier(state, fallback);
        }
    }

    private record CrushingControllerProjection() implements PolymerCreateBlock {
        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            boolean waterlogged = state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED);
            BlockState empty = waterlogged ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            if (state.getValue(CrushingWheelControllerBlock.VALID)) return empty;
            return PolymerBlockCollision.emptyCarrier(state, empty);
        }
    }

    private record InvisibleBlockProjection() implements PolymerCreateBlock {
        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            boolean waterlogged = state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED);
            return waterlogged ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
    }

    private record BlockProjection(BlockState clientState) implements PolymerCreateBlock {
        @Override
        public Block getPolymerReplacement(Block block, PacketContext context) {
            return this.clientState.getBlock();
        }

        @Override
        @SuppressWarnings("unchecked")
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            BlockState result = this.clientState;
            for (Property<?> property : state.getProperties()) {
                Property targetProperty = result.getBlock().getStateDefinition().getProperty(property.getName());
                if (targetProperty != null) {
                    Comparable value = state.getValue((Property) property);
                    if (targetProperty.getPossibleValues().contains(value)) {
                        result = result.setValue(targetProperty, value);
                    }
                }
            }
            return result;
        }
    }

    private record FixedBlockProjection(BlockState clientState) implements PolymerCreateBlock, PolymerTexturedBlock {
        @Override
        public Block getPolymerReplacement(Block block, PacketContext context) {
            return this.clientState.getBlock();
        }

        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            if (state.is(AllBlocks.TURNTABLE)) return this.clientState;
            return PolymerBlockCollision.emptyCarrier(state, this.clientState);
        }
    }

    private record TexturedBlockProjection(
            BlockState clientState,
            Map<BlockState, BlockState> stateMappings,
            Map<BlockState, PolymerBlockModel> displayModels)
            implements PolymerTexturedBlock, PolymerCreateBlock {
        @Override
        public Block getPolymerReplacement(Block block, PacketContext context) {
            return this.clientState.getBlock();
        }

        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            if (state.getBlock() instanceof TrackBlock) return PolymerStaticCollision.carrier(state);
            if (state.getBlock() instanceof MetalLadderBlock) return PolymerLadders.carrier(state);
            if (displayModels.containsKey(state)) {
                boolean waterlogged = state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && state.getValue(BlockStateProperties.WATERLOGGED);
                return PolymerBlockCollision.emptyCarrier(
                        state, KINETIC_BASE_CARRIER_STATE.setValue(BlockStateProperties.WATERLOGGED, waterlogged));
            }
            return stateMappings.getOrDefault(state, this.clientState);
        }
    }

    public static Identifier displayItemModelId(Identifier blockModel) {
        return Identifier.fromNamespaceAndPath(
                Create.MOD_ID, "polymer_block/" + blockModel.getNamespace() + "/" + blockModel.getPath());
    }

    public static @Nullable PolymerBlockModel movingDisplayModel(BlockState state) {
        return MOVING_DISPLAY_MODELS.get(state);
    }

    private record ModelDisplayHolderProvider(Map<BlockState, PolymerBlockModel> displayModels)
            implements BlockWithElementHolder {
        @Override
        public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
            ModelDisplayHolder holder = new ModelDisplayHolder(level, pos, displayModels);
            holder.show(state);
            return holder;
        }

        @Override
        public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
            return true;
        }
    }

    private static final class ModelDisplayHolder extends PolymerLitModelHolder {
        private final Map<BlockState, PolymerBlockModel> displayModels;
        private ItemDisplayElement display;
        private PolymerBlockModel shown;
        private InteractionElement clipboardTarget;
        private BlockState clipboardState;
        private final ServerLevel targetLevel;
        private final BlockPos targetPos;

        private ModelDisplayHolder(ServerLevel level, BlockPos pos, Map<BlockState, PolymerBlockModel> displayModels) {
            super(level, pos);
            targetLevel = level;
            targetPos = pos.immutable();
            this.displayModels = displayModels;
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            if (PolymerBedrockCarriers.bedrockNative(player.player, targetLevel.getBlockState(targetPos))) return false;
            return super.startWatching(player);
        }

        @Override
        public void notifyUpdate(HolderAttachment.UpdateType updateType) {
            super.notifyUpdate(updateType);
            if (updateType == BlockAwareAttachment.BLOCK_STATE_UPDATE) {
                BlockAwareAttachment attachment = BlockAwareAttachment.get(this);
                if (attachment != null) {
                    show(attachment.getBlockState());
                }
            }
        }

        private void show(BlockState state) {
            staticCollision(state);
            clipboardTarget(state);
            PolymerBlockModel model = displayModels.get(state);
            if (model != null && model.equals(shown)) {
                railTint(state);
                return;
            }
            if (display != null) {
                removeElement(display);
                display = null;
            }
            shown = model;
            if (model == null) {
                return;
            }
            display = PolymerDisplays.item(
                    PolymerKineticVisuals.rotatingDisplayItem(displayItemModelId(model.model())),
                    ItemDisplayContext.NONE);
            Quaternionf rotation = new Quaternionf()
                    .rotateYXZ((float) Math.toRadians(-model.y()), (float) Math.toRadians(-model.x()), 0);
            display.setLeftRotation(rotation);
            display.setRightRotation(new Quaternionf().rotationY((float) Math.PI));
            float scale = SCALED_DISPLAY_MODELS.getOrDefault(model.model(), 1f);
            if (scale != 1f) {
                float shift = (scale - 1) / 2;
                display.setScale(new Vector3f(scale, scale, scale));
                display.setTranslation(rotation.transform(new Vector3f(shift, shift, shift)));
            }
            PolymerDecorativeCulling.stationary(display, model.model());
            railTint(state);
            addElement(display);
        }

        private void railTint(BlockState state) {
            if (display == null || !state.is(AllBlocks.CONTROLLER_RAIL)) return;
            ItemStack item = display.getItem().copy();
            PolymerPartItems.setColors(
                    item,
                    List.of(0xFF000000
                            | RedstoneWireBlock.getColorForPower(state.getValue(BlockStateProperties.POWER))));
            display.setItem(item);
        }

        private void clipboardTarget(BlockState state) {
            if (!(state.getBlock() instanceof ClipboardBlock) || state.equals(clipboardState)) return;
            clipboardState = state;
            if (clipboardTarget != null) removeElement(clipboardTarget);
            AABB plate =
                    state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
            float width = (float) Math.max(plate.getXsize(), plate.getZsize());
            double x = (plate.minX + plate.maxX) / 2, z = (plate.minZ + plate.maxZ) / 2;
            if (plate.getYsize() > .1) {
                Direction out = state.getValue(HorizontalDirectionalBlock.FACING);
                x += (plate.getXsize() < plate.getZsize() ? out.getStepX() * (plate.getXsize() - width) / 2 : 0);
                z += (plate.getZsize() < plate.getXsize() ? out.getStepZ() * (plate.getZsize() - width) / 2 : 0);
            }
            clipboardTarget = new InteractionElement(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand, Vec3 hit, boolean secondary) {
                    var target = PolymerStaticCollision.hit(player, targetLevel, targetPos);
                    if (target != null
                            && hand != null
                            && player.gameMode
                                    .useItemOn(player, targetLevel, player.getItemInHand(hand), hand, target)
                                    .consumesAction()) player.swing(hand, SwingAnimation.DEFAULT, false);
                }

                @Override
                public void attack(ServerPlayer player) {
                    PolymerProxyMining.attack(player, targetLevel, targetPos);
                }
            });
            clipboardTarget.setSize(width, (float) plate.getYsize());
            clipboardTarget.setOffset(new Vec3(x - .5, plate.minY - .5, z - .5));
            addElement(clipboardTarget);
        }
    }

    private record BlockModelPlan(
            Identifier id,
            Block block,
            JsonObject variants,
            Map<String, PolymerBlockModel> models,
            String defaultSelector) {}

    private record ResolvedBlockModelPlan(BlockModelPlan plan, BlockState defaultClientState) {}
}
