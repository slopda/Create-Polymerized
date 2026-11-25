package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockCarriers;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.PolymerCreateBlock;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerBlockCollision;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Quaternionf;

public final class PolymerMultipartBlocks {
    private PolymerMultipartBlocks() {}

    public static boolean register(Identifier id, Block block) {
        JsonObject blockstate =
                PolymerBlockProjections.readAssetJson("/assets/create/blockstates/" + id.getPath() + ".json");
        if (blockstate == null || !blockstate.has("multipart")) {
            return false;
        }
        List<Case> cases = new ArrayList<>();
        for (JsonElement element : blockstate.getAsJsonArray("multipart")) {
            JsonObject entry = element.getAsJsonObject();
            List<PolymerBlockModel> models = new ArrayList<>();
            JsonElement apply = entry.get("apply");
            if (apply.isJsonArray()) {
                apply = apply.getAsJsonArray().get(0);
            }
            PolymerBlockModel model = PolymerBlockProjections.polymerModel(apply);
            if (model == null) {
                continue;
            }
            models.add(model);
            cases.add(new Case(entry.has("when") ? entry.getAsJsonObject("when") : null, models));
        }
        if (cases.isEmpty()) {
            return false;
        }
        Map<BlockState, List<PolymerBlockModel>> parts = new HashMap<>();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            List<PolymerBlockModel> shown = new ArrayList<>();
            for (Case c : cases) {
                if (c.when == null || matches(c.when, state)) {
                    shown.addAll(c.models);
                }
            }
            parts.put(state, List.copyOf(shown));
            for (PolymerBlockModel model : shown) {
                PolymerBlockProjections.DISPLAY_BLOCK_MODELS.add(model.model());
            }
        }
        PolymerBlock.registerOverlay(block, new CarrierProjection());
        BlockWithElementHolder.registerOverlay(block, new HolderProvider(Map.copyOf(parts)));
        return true;
    }

    private record Case(JsonObject when, List<PolymerBlockModel> models) {}

    public static void registerNativeWall(Block block, JsonObject definition) {
        var cases = new ArrayList<Case>();
        for (JsonElement part : definition.getAsJsonArray("multipart")) {
            JsonObject entry = part.getAsJsonObject(), apply = entry.getAsJsonObject("apply");
            PolymerBlockModel model = PolymerBlockModel.of(
                    Identifier.parse(apply.get("model").getAsString()),
                    apply.has("x") ? apply.get("x").getAsInt() : 0,
                    apply.has("y") ? apply.get("y").getAsInt() : 0,
                    apply.has("uvlock") && apply.get("uvlock").getAsBoolean());
            cases.add(new Case(entry.getAsJsonObject("when"), List.of(model)));
            PolymerBlockProjections.DISPLAY_BLOCK_MODELS.add(model.model());
        }
        var parts = new HashMap<BlockState, List<PolymerBlockModel>>();
        for (BlockState state : block.getStateDefinition().getPossibleStates())
            parts.put(
                    state,
                    cases.stream()
                            .filter(c -> matches(c.when, state))
                            .flatMap(c -> c.models.stream())
                            .toList());
        // Never mark a vanilla host as a PolymerBlock: Polymer then moves its states out of the native ID table,
        // corrupting later vanilla wire IDs. Element holders are independent and preserve native registry identity.
        BlockWithElementHolder.registerOverlay(block, new HolderProvider(Map.copyOf(parts)));
    }

    public static boolean matches(JsonObject when, BlockState state) {
        if (when.has("OR")) {
            for (JsonElement option : when.getAsJsonArray("OR")) {
                if (matches(option.getAsJsonObject(), state)) {
                    return true;
                }
            }
            return false;
        }
        if (when.has("AND")) {
            for (JsonElement option : when.getAsJsonArray("AND")) {
                if (!matches(option.getAsJsonObject(), state)) {
                    return false;
                }
            }
            return true;
        }
        for (Map.Entry<String, JsonElement> condition : when.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(condition.getKey());
            if (property == null) {
                return false;
            }
            String value = valueName(state, property);
            String expected = condition.getValue().getAsString();
            boolean negate = expected.startsWith("!");
            if (negate) {
                expected = expected.substring(1);
            }
            boolean any = false;
            for (String option : expected.split("\\|")) {
                if (option.equals(value)) {
                    any = true;
                    break;
                }
            }
            if (any == negate) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private record CarrierProjection() implements PolymerCreateBlock, PolymerTexturedBlock {
        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            boolean waterlogged = state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED);
            return PolymerBlockCollision.emptyCarrier(
                    state,
                    PolymerBlockProjections.KINETIC_BASE_CARRIER_STATE.setValue(
                            BlockStateProperties.WATERLOGGED, waterlogged));
        }
    }

    private record HolderProvider(Map<BlockState, List<PolymerBlockModel>> parts) implements BlockWithElementHolder {
        @Override
        public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
            PartsHolder holder = new PartsHolder(level, pos, parts);
            holder.show(state);
            return holder;
        }

        @Override
        public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
            return true;
        }
    }

    private static final class PartsHolder extends PolymerLitModelHolder {
        private final Map<BlockState, List<PolymerBlockModel>> parts;
        private final List<ItemDisplayElement> displays = new ArrayList<>();
        private List<PolymerBlockModel> shown = List.of();

        private final ServerLevel holderLevel;
        private final BlockPos holderPos;

        private PartsHolder(ServerLevel level, BlockPos pos, Map<BlockState, List<PolymerBlockModel>> parts) {
            super(level, pos);
            this.parts = parts;
            this.holderLevel = level;
            this.holderPos = pos.immutable();
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            if (PolymerBedrockCarriers.bedrockNative(player.player, holderLevel.getBlockState(holderPos))) return false;
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
            List<PolymerBlockModel> models = parts.getOrDefault(state, List.of());
            if (models.equals(shown)) {
                return;
            }
            for (ItemDisplayElement display : displays) {
                removeElement(display);
            }
            displays.clear();
            shown = models;
            for (PolymerBlockModel model : models) {
                ItemDisplayElement display = PolymerDisplays.item(
                        PolymerKineticVisuals.rotatingDisplayItem(
                                PolymerBlockProjections.displayItemModelId(model.model())),
                        ItemDisplayContext.NONE);
                display.setLeftRotation(new Quaternionf()
                        .rotateYXZ((float) Math.toRadians(-model.y()), (float) Math.toRadians(-model.x()), 0));
                display.setRightRotation(new Quaternionf().rotationY((float) Math.PI));
                PolymerDecorativeCulling.inBlock(display);
                displays.add(display);
                addElement(display);
            }
        }
    }
}
