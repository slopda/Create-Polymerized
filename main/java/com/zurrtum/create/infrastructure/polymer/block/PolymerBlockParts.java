package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.kinetics.base.IRotate;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.simpleRelays.ICogWheel;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockCarriers;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerCrackModels;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerSeatCarriers;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerSuperGlue;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerBearingDiagnostics;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerWorldPreviews;
import com.zurrtum.create.infrastructure.polymer.train.PolymerRailVisuals;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PolymerBlockParts {
    public record Part(
            @org.jspecify.annotations.Nullable Identifier model,
            Matrix4f transform,
            ItemStack item,
            net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component text,
            ItemDisplayContext context,
            @org.jspecify.annotations.Nullable Object identity,
            @org.jspecify.annotations.Nullable Motion motion) {
        public Part {
            item = item.copyWithCount(1);
        }

        public Part(Identifier model, Matrix4f transform) {
            this(model, transform, ItemStack.EMPTY, null, ItemDisplayContext.NONE);
        }

        public Part(Identifier model, Matrix4f transform, ItemStack item, Component text, ItemDisplayContext context) {
            this(model, transform, item, text, context, null, null);
        }

        public Part identified(Object key) {
            return new Part(model, transform, item, text, context, Objects.requireNonNull(key), motion);
        }

        public Part glowing() {
            return new Part(model, transform, item, text, context, new Glow(identity), motion);
        }

        public Part spinning(Direction facing, float speed, float angle) {
            return new Part(model, transform, item, text, context, identity, new Motion(facing, speed, angle));
        }

        public Part near(float blocks) {
            Object key = identity != null ? identity : model != null ? model : text != null ? "text" : "item";
            return new Part(model, transform, item, text, context, new Near(key, blocks), motion);
        }

        boolean isGlowing() {
            return identity instanceof Glow || identity instanceof Near near && near.key() instanceof Glow;
        }

        float nearBlocks() {
            return identity instanceof Near near ? near.blocks() : 0;
        }

        boolean sameContent(Part other) {
            if (context != other.context) {
                return false;
            }
            if (text != null) {
                return text.equals(other.text);
            }
            return item.isEmpty()
                    ? other.item.isEmpty() && other.text == null && model != null && model.equals(other.model)
                    : ItemStack.isSameItemSameComponents(item, other.item);
        }
    }

    public record Motion(Direction facing, float speed, float angle) {}

    record Glow(@org.jspecify.annotations.Nullable Object key) {}

    record Near(@org.jspecify.annotations.Nullable Object key, float blocks) {}

    private static final Brightness FULL_BRIGHT = new Brightness(15, 15);

    private static Brightness brightnessOf(Part part, Brightness neighbours) {
        return part.isGlowing() ? FULL_BRIGHT : neighbours;
    }

    @FunctionalInterface
    public interface Provider<T extends BlockEntity> {
        void collect(T blockEntity, BlockState state, float time, List<Part> out);
    }

    private static final Map<Block, Provider<?>> PROVIDERS = new HashMap<>();
    private static final Set<Identifier> PART_MODELS = new LinkedHashSet<>();
    private static final Map<BlockEntity, PartsDisplay> DISPLAYS = new IdentityHashMap<>();
    private static final Set<PartsDisplay> STALE = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final Set<Block> FAILED = new HashSet<>();
    private static final Set<Identifier> CONTINUOUS = new HashSet<>();
    private static final Set<Block> STILL = new HashSet<>();

    private static final int INTERPOLATION_TICKS = 2;
    private static final int DISPLAY_CACHE_LIMIT = 2048;
    private static final Map<Identifier, ItemStack> DISPLAY_ITEMS = new ConcurrentHashMap<>();

    private PolymerBlockParts() {}

    public static void register() {
        PolymerKineticParts.registerAll();
        PolymerLogisticsParts.registerAll();
        PolymerPulleyParts.registerAll();
        PolymerMachineParts.registerAll();
        PolymerDecorParts.registerAll();
        PolymerDoorParts.registerAll();
        PolymerFilterParts.registerAll();
        PolymerTextParts.registerAll();
        PolymerPackagingParts.registerAll();
        PolymerBracketParts.register();
        PolymerTableClothParts.registerAll();
        PolymerCannonParts.registerAll();
        PolymerBearingDiagnostics.register();
        PolymerFidelityParts.registerAll();
        PolymerStorageParts.registerAll();
        PolymerPipeParts.init();
        PolymerBlockControls.register();
        PolymerSuperGlue.register();
        PolymerWorldPreviews.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (STALE.isEmpty()) return;
            List<PartsDisplay> stale = List.copyOf(STALE);
            STALE.clear();
            stale.forEach(PartsDisplay::destroy);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            STALE.clear();
            PolymerBedrockParts.clear();
            List.copyOf(DISPLAYS.values()).forEach(PartsDisplay::destroy);
            DISPLAYS.clear();
            DISPLAY_ITEMS.clear();
        });
        Create.LOGGER.info(
                "Registered Polymer block-part renderers for {} blocks ({} part models)",
                PROVIDERS.size(),
                PART_MODELS.size());
    }

    @SafeVarargs
    public static <T extends BlockEntity> void register(Provider<T> provider, Block... blocks) {
        for (Block block : blocks) {
            PROVIDERS.put(block, provider);
        }
    }

    public static Identifier continuous(Identifier model) {
        CONTINUOUS.add(model);
        return model;
    }

    public static void still(Block... blocks) {
        Collections.addAll(STILL, blocks);
    }

    public static boolean handles(BlockState state) {
        return PROVIDERS.containsKey(state.getBlock());
    }

    public static Identifier model(String blockModelPath) {
        return model(CreateIds.id("block/" + blockModelPath));
    }

    public static Identifier model(Identifier id) {
        PART_MODELS.add(id);
        return id;
    }

    public static volatile java.util.function.@org.jspecify.annotations.Nullable BiConsumer<
                    BlockEntity, Collection<Part>>
            censusHook;

    public static void tick(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level) || blockEntity.isRemoved()) {
            remove(blockEntity);
            return;
        }
        PolymerBedrockParts.tick(blockEntity, level);
        var census = censusHook;
        if (census != null) {
            Map<PartKey, Part> sample = collectParts(blockEntity, level);
            if (sample != null) census.accept(blockEntity, sample.values());
        }
        PartsDisplay display = DISPLAYS.get(blockEntity);
        if (display != null && display.holder.getWatchingPlayers().isEmpty()) {
            return;
        }
        Map<PartKey, Part> parts = collectParts(blockEntity, level);
        if (parts == null || parts.isEmpty()) {
            remove(blockEntity);
        } else if (display == null) {
            display = new PartsDisplay(blockEntity, level, parts);
            DISPLAYS.put(blockEntity, display);
        } else {
            display.update(parts, false);
        }
    }

    private record PartKey(int channel, Object token, int occurrence) {}

    @SuppressWarnings("unchecked")
    public static List<Part> itemParts(BlockEntity blockEntity, float time) {
        BlockState state = blockEntity.getBlockState();
        Provider<BlockEntity> provider = (Provider<BlockEntity>) PROVIDERS.get(state.getBlock());
        if (provider == null) return List.of();
        List<Part> parts = new ArrayList<>();
        provider.collect(blockEntity, state, time, parts);
        parts.removeIf(part -> part.item().isEmpty());
        return parts;
    }

    @SuppressWarnings("unchecked")
    public static List<Part> textParts(BlockEntity blockEntity, float time) {
        BlockState state = blockEntity.getBlockState();
        Provider<BlockEntity> provider = (Provider<BlockEntity>) PROVIDERS.get(state.getBlock());
        if (provider == null) return List.of();
        List<Part> parts = new ArrayList<>();
        provider.collect(blockEntity, state, time, parts);
        parts.removeIf(part -> part.text() == null);
        return parts;
    }

    @SuppressWarnings("unchecked")
    public static List<Part> allParts(BlockEntity blockEntity, float time) {
        BlockState state = blockEntity.getBlockState();
        Provider<BlockEntity> provider = (Provider<BlockEntity>) PROVIDERS.get(state.getBlock());
        if (provider == null) return List.of();
        List<Part> parts = new ArrayList<>();
        provider.collect(blockEntity, state, time, parts);
        return parts;
    }

    @SuppressWarnings("unchecked")
    public static List<Part> modelParts(BlockEntity blockEntity, float time) {
        BlockState state = blockEntity.getBlockState();
        Provider<BlockEntity> provider = (Provider<BlockEntity>) PROVIDERS.get(state.getBlock());
        if (provider == null) return List.of();
        List<Part> parts = new ArrayList<>();
        provider.collect(blockEntity, state, time, parts);
        parts.removeIf(part -> part.model() == null || !part.item().isEmpty() || part.text() != null);
        return parts;
    }

    @SuppressWarnings("unchecked")
    private static Map<PartKey, Part> collectParts(BlockEntity blockEntity, ServerLevel level) {
        BlockState state = blockEntity.getBlockState();
        Provider<BlockEntity> provider = (Provider<BlockEntity>) PROVIDERS.get(state.getBlock());
        List<Part> parts = new ArrayList<>();
        Map<PartKey, Part> result = new LinkedHashMap<>();
        try {
            if (provider != null) provider.collect(blockEntity, state, providerTime(level), parts);
            appendParts(0, parts, result);
            parts.clear();
            PolymerBlockControls.collect(blockEntity, state, parts);
            appendParts(1, parts, result);
            parts.clear();
            PolymerPipeParts.collect(blockEntity, state, parts);
            appendParts(2, parts, result);
            parts.clear();
            PolymerBracketParts.collect(blockEntity, parts);
            appendParts(3, parts, result);
        } catch (RuntimeException e) {
            if (FAILED.add(state.getBlock())) {
                Create.LOGGER.error("Polymer part renderer failed for {}", state.getBlock(), e);
            }
            return null;
        }
        return result;
    }

    private static void appendParts(int channel, List<Part> parts, Map<PartKey, Part> result) {
        Map<Object, Integer> occurrences = new HashMap<>();
        for (Part part : parts) {
            Object token = part.identity() != null
                    ? part.identity()
                    : part.model() != null ? part.model() : part.text() != null ? "text" : "item";
            int occurrence = occurrences.merge(token, 1, Integer::sum) - 1;
            result.put(new PartKey(channel, token, occurrence), part);
        }
    }

    public static void remove(BlockEntity blockEntity) {
        if (blockEntity.isRemoved()) PolymerBedrockParts.remove(blockEntity);
        PartsDisplay display = DISPLAYS.remove(blockEntity);
        if (display != null) {
            display.destroy();
        }
    }

    private static final class PartsDisplay {
        private final ElementHolder holder = new AdapterHolder() {
            @Override
            public boolean startWatching(ServerGamePacketListenerImpl player) {
                if (PolymerBedrockCarriers.skipPaperDisplays(player)) return false;
                // Polymer calls this while iterating the chunk's attachment list (chunk send on join). Destroying
                // here removes this attachment mid-iteration (ConcurrentModificationException, main crash
                // 2026-10-24): refuse the watcher and destroy at the end of the tick instead.
                if (!getWatchingPlayers().contains(player)
                        && getWatchingPlayers().isEmpty()
                        && !refresh(false)) {
                    STALE.add(PartsDisplay.this);
                    return false;
                }
                return super.startWatching(player);
            }

            @Override
            protected void onAttachmentRemoved(HolderAttachment attachment) {
                DISPLAYS.remove(owner, PartsDisplay.this);
                super.onAttachmentRemoved(attachment);
            }
        };
        private final Map<PartKey, ShownPart> shown = new LinkedHashMap<>();
        private final Map<PartKey, Matrix4f> lastPose = new HashMap<>(), lastStep = new HashMap<>();

        private final int phase;
        private final BlockEntity owner;
        private final ServerLevel level;
        private final BlockPos pos;
        private int lightAge;
        private Brightness brightness;
        private BlockState lastState;
        private float lastSpeed;

        private record ShownPart(DisplayElement element, Part part) {}

        private PartsDisplay(BlockEntity owner, ServerLevel level, Map<PartKey, Part> parts) {
            this.owner = owner;
            this.level = level;
            this.pos = owner.getBlockPos().immutable();
            phase = Math.floorMod(pos.hashCode(), PolymerKineticVisuals.MAX_ROTATION_CADENCE);
            brightness = neighbourBrightness(level, pos);
            update(parts, false);
            ChunkAttachment.ofTicking(holder, level, pos);
        }

        private boolean refresh(boolean destroyStale) {
            if (!level.getServer().isSameThread())
                throw new IllegalStateException("Block-part watching must run on the server thread");
            if (owner.isRemoved() || owner.getLevel() != level) {
                if (destroyStale) destroy();
                return false;
            }
            Map<PartKey, Part> parts = collectParts(owner, level);
            if (parts == null || parts.isEmpty()) {
                if (destroyStale) destroy();
                return false;
            }
            update(parts, true);
            return true;
        }

        private DisplayElement create(Part part) {
            DisplayElement element;
            if (part.text() != null) {
                TextDisplayElement text = new TextDisplayElement(part.text());
                text.setBackground(0);
                text.setLineWidth(1000);
                element = text;
            } else if (part.item().isEmpty()) {
                ItemDisplayElement item = PolymerDisplays.item(displayItem(part.model()), ItemDisplayContext.NONE);
                element = item;
            } else {
                ItemDisplayElement item = PolymerDisplays.item(part.item().copyWithCount(1), part.context());
                element = item;
            }
            element.setInterpolationDuration(
                    STILL.contains(owner.getBlockState().getBlock()) ? 0 : INTERPOLATION_TICKS);
            PolymerDecorativeCulling.pose(
                    element,
                    transformOf(part),
                    part.text() == null && part.item().isEmpty() ? part.model() : null);
            float near = part.nearBlocks();
            if (near > 0) {
                element.setViewRange((near + (element.getViewRange() > 1 ? 4 : 0)) / 64f);
            }
            element.setBrightness(brightnessOf(part, brightness));
            return element;
        }

        private void update(Map<PartKey, Part> parts, boolean forceLight) {
            if (forceLight || ++lightAge >= 20) {
                lightAge = 0;
                Brightness nextLight = neighbourBrightness(level, pos);
                if (!nextLight.equals(brightness)) {
                    brightness = nextLight;
                    shown.values()
                            .forEach(value -> value.element().setBrightness(brightnessOf(value.part(), brightness)));
                }
            }
            BlockState currentState = owner.getBlockState();
            float currentSpeed = owner instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : 0;
            boolean structural = currentState != lastState || !parts.keySet().equals(shown.keySet());
            boolean speedChanged = currentSpeed != lastSpeed;
            boolean still = STILL.contains(currentState.getBlock());
            lastState = currentState;
            lastSpeed = currentSpeed;
            shown.entrySet().removeIf(entry -> {
                if (parts.containsKey(entry.getKey())) return false;
                holder.removeElement(entry.getValue().element());
                lastPose.remove(entry.getKey());
                lastStep.remove(entry.getKey());
                return true;
            });
            long gameTime = level.getGameTime();
            for (var entry : parts.entrySet()) {
                Part next = entry.getValue();
                ShownPart previous = shown.get(entry.getKey());
                if (previous != null
                        && (next.text() == null) != (previous.part().text() == null)) {
                    holder.removeElement(previous.element());
                    previous = null;
                }
                if (previous == null) {
                    var element = create(next);
                    holder.addElement(element);
                    shown.put(entry.getKey(), new ShownPart(element, next));
                    lastPose.put(entry.getKey(), transformOf(next));
                    lastStep.remove(entry.getKey());
                    continue;
                }
                var element = previous.element();
                if (!next.sameContent(previous.part())) {
                    if (element instanceof TextDisplayElement text) {
                        text.setText(next.text());
                    } else if (element instanceof ItemDisplayElement item) {
                        item.setItem(
                                next.item().isEmpty()
                                        ? displayItem(next.model())
                                        : next.item().copyWithCount(1));
                        item.setItemDisplayContext(next.context());
                    }
                }
                boolean snap = structural
                        || still
                        || speedChanged && !(next.model() != null && CONTINUOUS.contains(next.model()));
                Matrix4f pose = transformOf(next);
                Matrix4f prior = lastPose.put(entry.getKey(), pose);
                Matrix4f step = null;
                int cadence = 1;
                if (!snap && prior != null && next.text() == null) {
                    step = new Matrix4f(pose).mul(new Matrix4f(prior).invert());
                    Matrix4f before = lastStep.put(entry.getKey(), step);
                    if (before != null && steadyTurn(prior, pose, step, before))
                        cadence = PolymerKineticVisuals.stepCadence(turnDegrees(step));
                } else {
                    lastStep.remove(entry.getKey());
                }
                shown.put(entry.getKey(), new ShownPart(element, next));
                if (cadence > 1 && Math.floorMod(gameTime + phase, cadence) != 0) continue;
                Matrix4f sent = pose;
                for (int ahead = 1; ahead < cadence; ahead++) sent = new Matrix4f(step).mul(sent);
                int interpolation = snap ? 0 : cadence > 1 ? cadence + 1 : INTERPOLATION_TICKS;
                if (element.getInterpolationDuration() != interpolation)
                    element.setInterpolationDuration(interpolation);
                PolymerDecorativeCulling.pose(
                        element, sent, next.text() == null && next.item().isEmpty() ? next.model() : null);
                element.startInterpolationIfDirty();
            }
        }

        private static boolean steadyTurn(Matrix4f prior, Matrix4f pose, Matrix4f step, Matrix4f before) {
            if (Math.abs(pose.m30() - prior.m30()) > 1e-5
                    || Math.abs(pose.m31() - prior.m31()) > 1e-5
                    || Math.abs(pose.m32() - prior.m32()) > 1e-5) return false;
            if (!step.equals(before, 1e-4f)) return false;
            return Math.abs(step.determinant3x3() - 1) < 1e-3;
        }

        private static double turnDegrees(Matrix4f step) {
            double cos = (step.m00() + step.m11() + step.m22() - 1) / 2;
            return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        }

        private void destroy() {
            DISPLAYS.remove(owner, this);
            holder.destroy();
            shown.clear();
        }
    }

    public static Brightness neighbourBrightness(ServerLevel level, BlockPos pos) {
        int block = 0;
        int sky = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, neighbour));
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, neighbour));
        }
        return new Brightness(block, sky);
    }

    private static Matrix4f transformOf(Part part) {
        if (part.text() != null) {
            return textDisplayTransform(part.transform());
        }
        return part.item().isEmpty() ? displayTransform(part.transform()) : itemDisplayTransform(part.transform());
    }

    static Matrix4f textDisplayTransform(Matrix4f blockLocal) {
        return new Matrix4f().translate(-0.5f, -0.5f, -0.5f).mul(blockLocal).scale(40, -40, 40);
    }

    public static Part textPart(Component text, Matrix4f fontPose) {
        return new Part(null, fontPose, ItemStack.EMPTY, text, ItemDisplayContext.NONE);
    }

    public static Matrix4f itemDisplayTransform(Matrix4f blockLocal) {
        return new Matrix4f().translate(-0.5f, -0.5f, -0.5f).mul(blockLocal).rotateY(Mth.PI);
    }

    public static Part itemPart(ItemStack stack, Matrix4f transform) {
        return itemPart(stack, transform, ItemDisplayContext.FIXED);
    }

    public static Part itemPart(ItemStack stack, Matrix4f transform, ItemDisplayContext context) {
        return new Part(null, transform, stack, null, context);
    }

    public static Matrix4f displayTransform(Matrix4f blockLocal) {
        // The vanilla item-display renderer turns the item 180 degrees about Y before drawing it (after our
        // transformation in matrix order), so cancel it here; otherwise asymmetric models render flipped (backwards
        // hatches, track rails shifted a block sideways).
        return new Matrix4f()
                .translate(-0.5f, -0.5f, -0.5f)
                .mul(blockLocal)
                .translate(0.5f, 0.5f, 0.5f)
                .rotateY(Mth.PI);
    }

    public static ItemStack displayItem(Identifier blockModel) {
        ItemStack template = DISPLAY_ITEMS.get(blockModel);
        if (template == null) {
            template = PolymerKineticVisuals.rotatingDisplayItem(itemModelId(blockModel));
            synchronized (DISPLAY_ITEMS) {
                var existing = DISPLAY_ITEMS.get(blockModel);
                if (existing != null) template = existing;
                else if (DISPLAY_ITEMS.size() < DISPLAY_CACHE_LIMIT) DISPLAY_ITEMS.put(blockModel, template);
            }
        }
        return template.copy();
    }

    private static Identifier itemModelId(Identifier blockModel) {
        return CreateIds.id("polymer_part/" + blockModel.getPath());
    }

    public static void addItemModels(ResourcePackBuilder builder) {
        PolymerSeatCarriers.addAssets(builder);
        PolymerCrackModels.addAssets(builder);
        PolymerTableClothVisuals.addAssets(builder);
        BeltScroll.addAssets(builder);
        PolymerStorageParts.addAssets(builder);
        PolymerFidelityParts.addAssets(builder);
        PolymerRailVisuals.addAssets(builder);
        for (Identifier model : PART_MODELS) {
            Identifier itemModel = itemModelId(model);
            JsonObject inner = new JsonObject();
            inner.addProperty("type", "minecraft:model");
            inner.addProperty("model", model.toString());
            PolymerCopycatParts.addItemTint(model, inner);
            if (model.getPath().equals("block/polymer/fluid/water")) {
                JsonObject tint = new JsonObject();
                tint.addProperty("type", "minecraft:constant");
                tint.addProperty("value", 0xFF3F76E4);
                JsonArray tints = new JsonArray();
                tints.add(tint);
                inner.add("tints", tints);
            }
            JsonObject definition = new JsonObject();
            definition.add("model", inner);
            builder.addStringData(
                    "assets/" + itemModel.getNamespace() + "/items/" + itemModel.getPath() + ".json",
                    definition.toString());
        }
    }

    public static Part part(Identifier model, Matrix4f transform) {
        return new Part(model, transform);
    }

    public static Matrix4f identity() {
        return new Matrix4f();
    }

    public static Matrix4f rotateCentered(Quaternionf rotation) {
        return new Matrix4f().translate(0.5f, 0.5f, 0.5f).rotate(rotation).translate(-0.5f, -0.5f, -0.5f);
    }

    public static Matrix4f pivot(Quaternionf rotation, float x, float y, float z) {
        return new Matrix4f().translate(x, y, z).rotate(rotation).translate(-x, -y, -z);
    }

    public static Matrix4f spin(Direction direction, float degrees) {
        return rotateCentered(new Quaternionf()
                .setAngleAxis(
                        Mth.DEG_TO_RAD * (degrees % 360),
                        direction.getStepX(),
                        direction.getStepY(),
                        direction.getStepZ()));
    }

    public static Matrix4f spin(Axis axis, float degrees) {
        return spin(axis.getPositive(), degrees);
    }

    public static Matrix4f rotateToFace(Direction facing) {
        return switch (facing) {
            case NORTH -> rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * 180));
            case WEST -> rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * -90));
            case EAST -> rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * -270));
            case UP -> rotateCentered(new Quaternionf().rotationX(Mth.DEG_TO_RAD * -90));
            case DOWN -> rotateCentered(new Quaternionf().rotationX(Mth.DEG_TO_RAD * 90));
            case SOUTH -> new Matrix4f();
        };
    }

    public static Matrix4f rotateToFaceVertical(Direction facing) {
        return switch (facing) {
            case NORTH ->
                rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * 180).rotateX(Mth.DEG_TO_RAD * 90));
            case WEST ->
                rotateCentered(new Quaternionf().rotationY(Mth.DEG_TO_RAD * -90).rotateX(Mth.DEG_TO_RAD * 90));
            case EAST ->
                rotateCentered(
                        new Quaternionf().rotationY(Mth.DEG_TO_RAD * -270).rotateX(Mth.DEG_TO_RAD * 90));
            case SOUTH -> rotateCentered(new Quaternionf().rotationX(Mth.DEG_TO_RAD * 90));
            case DOWN -> rotateCentered(new Quaternionf().rotationX(Mth.DEG_TO_RAD * 180));
            case UP -> new Matrix4f();
        };
    }

    public static float progress(float speed, float time) {
        return (float) Math.IEEEremainder((double) time * speed * 0.3, 360);
    }

    static final long PROVIDER_TIME_PERIOD = 1200L << 13;

    static float providerTime(ServerLevel level) {
        return (float) Math.floorMod(level.getGameTime(), PROVIDER_TIME_PERIOD);
    }

    public static float rotationOffset(BlockState state, Axis axis, Vec3i pos) {
        if (shouldOffset(axis, pos)) {
            return 22.5f;
        }
        return ICogWheel.isLargeCog(state) ? 11.25f : 0;
    }

    public static boolean shouldOffset(Axis axis, Vec3i pos) {
        int x = axis == Axis.X ? 0 : pos.getX();
        int y = axis == Axis.Y ? 0 : pos.getY();
        int z = axis == Axis.Z ? 0 : pos.getZ();
        return (x + y + z) % 2 == 0;
    }

    public static Axis axisOf(BlockState state) {
        return ((IRotate) state.getBlock()).getRotationAxis(state);
    }
}
