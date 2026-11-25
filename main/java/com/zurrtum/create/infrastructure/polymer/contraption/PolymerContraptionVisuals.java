package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.Contraption;
import com.zurrtum.create.content.contraptions.minecart.MinecartCouplingItem;
import com.zurrtum.create.content.contraptions.mounted.MinecartContraptionItem;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.infrastructure.packet.c2s.ContraptionInteractionPacket;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerAdapterConfig;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerTintedModels;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCopycatParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerCollisionShulkers;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerLadders;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerWallCollision;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerElevatorControls;
import com.zurrtum.create.infrastructure.polymer.train.PolymerRailVisuals;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrainControls;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class PolymerContraptionVisuals {
    private static final Map<AbstractContraptionEntity, Assembly> ASSEMBLIES = new IdentityHashMap<>();
    private static final int MAX_COLLISION_SHULKERS = 512;
    private static final int CELLS_PER_BLOCK = 4;
    private static final double CELL_SCALE = 0.5;
    private static volatile Thread flushThread;

    private PolymerContraptionVisuals() {}

    public static void register() {
        PolymerTrainControls.register();
        PolymerRailVisuals.register();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Vanilla batches a tick's packets with suspendFlushing during tickChildren, but has already resumed
            // flushing before END_SERVER_TICK; without this every holder move/teleport is flushed on its own.
            List<ServerPlayer> batched = suspendFlushing(server);
            try {
                PolymerContraptionPassengers.tickHolders();
                tickAssemblies(server);
                PolymerShiftCarry.flushSeatMoves(server);
                PolymerShiftPacer.commit(PolymerShiftCarry.SUBSTEPS);
            } finally {
                resumeFlushing(batched);
            }
        });
    }

    static boolean deferringFlush() {
        return flushThread == Thread.currentThread();
    }

    private static List<ServerPlayer> suspendFlushing(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) players.addAll(level.players());
        for (ServerPlayer player : players) player.connection.suspendFlushing();
        flushThread = Thread.currentThread();
        return players;
    }

    private static void resumeFlushing(List<ServerPlayer> players) {
        flushThread = null;
        for (ServerPlayer player : players) {
            try {
                player.connection.resumeFlushing();
            } catch (RuntimeException error) {
                Create.LOGGER.warn("Contraption flush resume failed for {}", player.getScoreboardName(), error);
            }
        }
    }

    private static void tickAssemblies(MinecraftServer server) {
        {
            List<PolymerContraptionPhysics.Surface> surfaces = new ArrayList<>();
            List<PolymerContraptionPhysics.Surface> itemSurfaces = new ArrayList<>();
            List<AbstractContraptionEntity> assemblies = new ArrayList<>(ASSEMBLIES.keySet());
            List<AbstractContraptionEntity> shifted = new ArrayList<>();
            assemblies.sort(Comparator.comparingInt(AbstractContraptionEntity::getId));
            for (AbstractContraptionEntity entity : assemblies) {
                Contraption contraption = entity.getContraption();
                if (entity.isRemoved()
                        || contraption == null
                        || contraption.getBlocks().isEmpty()) {
                    remove(entity);
                    continue;
                }
                Assembly assembly = ASSEMBLIES.get(entity);
                boolean trial = PolymerContraptionLab.prepare(entity);
                boolean shift = !trial && PolymerShiftCarry.prepare(entity);
                if (!trial && !shift) PolymerContraptionFloors.prepare(entity, assembly.getWatchingPlayers());
                assembly.update(entity, contraption);
                assembly.tick();
                PolymerContraptionLab.updated(entity);
                if (shift) shifted.add(entity);
                PolymerContraptionPhysics.Surface surface = assembly.surface(entity);
                itemSurfaces.add(surface);
                if (PolymerContraptionFloors.owns(entity)) PolymerContraptionFloors.update(surface);
                else surfaces.add(surface);
            }
            for (AbstractContraptionEntity entity : shifted) {
                Assembly assembly = ASSEMBLIES.get(entity);
                if (assembly != null && !entity.isRemoved())
                    PolymerShiftCarry.updated(entity, assembly.getWatchingPlayers());
            }
            PolymerRailVisuals.tick(server);
            PolymerContraptionPhysics.tick(server, surfaces);
            PolymerContraptionFloors.tick(server);
            PolymerContraptionLab.tick(server);
            PolymerContraptionItems.tick(itemSurfaces);
        }
    }

    public static void tick(AbstractContraptionEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) {
            remove(entity);
            return;
        }

        Contraption contraption = entity.getContraption();
        if (contraption == null || contraption.getBlocks().isEmpty()) {
            remove(entity);
            return;
        }

        ASSEMBLIES.computeIfAbsent(entity, Assembly::new);
        PolymerContraptionPassengers.see(entity);
    }

    public static void remove(AbstractContraptionEntity entity) {
        // Destroy the holder first. Shift carry's leave bundle is paced (sent after this tick); restoring a rider's
        // removed originals while the holder still exists would respawn them after its removal as frozen ghost
        // blocks.
        Assembly assembly = ASSEMBLIES.remove(entity);
        if (assembly != null) {
            assembly.destroy();
        }
        PolymerContraptionLab.removed(entity);
        PolymerShiftCarry.remove(entity);
        PolymerContraptionPassengers.remove(entity);
        if (entity instanceof CarriageContraptionEntity carriage) PolymerRailVisuals.remove(carriage);
        PolymerElevatorControls.removed(entity);
        PolymerContraptionFloors.remove(entity);
        PolymerContraptionPhysics.remove(entity);
        PolymerTrainControls.remove(entity);
    }

    private static void clear() {
        PolymerContraptionLab.stopping();
        PolymerShiftCarry.clear();
        PolymerContraptionFloors.clear();
        for (Assembly assembly : ASSEMBLIES.values()) {
            assembly.destroy();
        }
        ASSEMBLIES.clear();
        PolymerContraptionPhysics.clear();
    }

    static List<DisplayElement> carriedDisplays(AbstractContraptionEntity entity) {
        Assembly assembly = ASSEMBLIES.get(entity);
        if (assembly == null) return List.of();
        List<DisplayElement> displays = new ArrayList<>(assembly.displays.values());
        displays.addAll(assembly.bogeyDisplays);
        displays.addAll(PolymerContraptionPassengers.carriedDisplays(entity));
        return displays;
    }

    static Set<DisplayElement> ownedDisplays(AbstractContraptionEntity entity) {
        Assembly assembly = ASSEMBLIES.get(entity);
        if (assembly == null) return Set.of();
        Set<DisplayElement> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        owned.addAll(assembly.displays.values());
        owned.addAll(assembly.bogeyDisplays);
        return owned;
    }

    public static int TRAIN_SMOOTH = 2;

    static int smoothTicks(AbstractContraptionEntity entity) {
        return entity instanceof CarriageContraptionEntity ? TRAIN_SMOOTH : 1;
    }

    static Lead partLead(
            AbstractContraptionEntity entity,
            PolymerContraptionPhysics.Transform previous,
            PolymerContraptionPhysics.Transform current) {
        return smoothTicks(entity) > 1
                ? Lead.of(previous, current, entity.position(), smoothTicks(entity) - 1)
                        .inTransform()
                : Lead.NONE;
    }

    record Lead(Vec3 offset, Quaternionf rotation, Vec3 shift) {
        static final Lead NONE = new Lead(Vec3.ZERO, new Quaternionf(), Vec3.ZERO);

        Lead inTransform() {
            return this == NONE ? NONE : new Lead(Vec3.ZERO, rotation, offset);
        }

        Matrix4f matrix() {
            return new Matrix4f()
                    .translation((float) shift.x, (float) shift.y, (float) shift.z)
                    .rotate(rotation);
        }

        static Lead of(
                PolymerContraptionPhysics.Transform previous,
                PolymerContraptionPhysics.Transform current,
                Vec3 pivot,
                int ticks) {
            if (previous == null || current == null || ticks <= 0) return NONE;
            Vec3 ahead = pivot;
            for (int i = 0; i < ticks; i++) ahead = current.toWorld(previous.toLocal(ahead));
            Vec3 offset = ahead.subtract(pivot);
            Quaternionf step = basis(current).mul(basis(previous).conjugate());
            double angle = 2 * Math.acos(Math.min(1, Math.abs(step.w)));
            if (!Double.isFinite(offset.lengthSqr())
                    || offset.lengthSqr() > 16.0 * ticks * ticks
                    || angle > Math.toRadians(15)) return NONE;
            Quaternionf rotation = new Quaternionf();
            for (int i = 0; i < ticks; i++) rotation.premul(step);
            return new Lead(offset, rotation, Vec3.ZERO);
        }

        private static Quaternionf basis(PolymerContraptionPhysics.Transform transform) {
            return new Matrix3f()
                    .setColumn(0, vector(transform.x()))
                    .setColumn(1, vector(transform.y()))
                    .setColumn(2, vector(transform.z()))
                    .getNormalizedRotation(new Quaternionf());
        }
    }

    static Lead leadOf(AbstractContraptionEntity entity, DisplayElement display) {
        Assembly assembly = ASSEMBLIES.get(entity);
        if (assembly == null) return Lead.NONE;
        if (assembly.bodies.contains(display)) return assembly.lead;
        if (assembly.bogeyDisplays.contains(display)) return assembly.partLead;
        return PolymerContraptionPassengers.lead(entity);
    }

    static boolean watches(AbstractContraptionEntity entity, ServerPlayer player) {
        Assembly assembly = ASSEMBLIES.get(entity);
        return assembly != null && assembly.getWatchingPlayers().contains(player.connection);
    }

    public static Brightness light(Level level, Vec3 worldCenter) {
        BlockPos pos = BlockPos.containing(worldCenter);
        for (int up = 0; up < 3 && level.getBlockState(pos).canOcclude(); up++) pos = pos.above();
        return new Brightness(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
    }

    public static List<AbstractContraptionEntity> watchedContraptions(ServerPlayer player) {
        return ASSEMBLIES.entrySet().stream()
                .filter(entry -> !entry.getKey().isRemoved()
                        && entry.getKey().level() == player.level()
                        && entry.getValue().getWatchingPlayers().contains(player.connection))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(AbstractContraptionEntity::getId))
                .toList();
    }

    private static final class Assembly extends AdapterHolder {
        private final Map<BlockPos, DisplayElement> displays = new HashMap<>();
        private final Map<BlockPos, Identifier> itemModels = new HashMap<>();
        private final Map<BlockPos, GenericEntityElement> interactions = new HashMap<>();
        private final Map<BlockPos, List<CollisionCell>> colliders = new HashMap<>();
        private int shulkerCount;
        private final List<ItemDisplayElement> bogeyDisplays = new ArrayList<>();
        private List<Identifier> bogeyModels = List.of();
        private PolymerContraptionPhysics.Transform previousTransform;
        private PolymerContraptionPhysics.Transform currentTransform;
        private final Set<DisplayElement> bodies = Collections.newSetFromMap(new IdentityHashMap<>());

        private Lead lead = Lead.NONE;
        private Lead partLead = Lead.NONE;

        private Assembly(AbstractContraptionEntity entity) {
            EntityAttachment.of(this, entity);
        }

        @Override
        protected float displayScale() {
            return PolymerAdapterConfig.contraptionScale();
        }

        private void update(AbstractContraptionEntity entity, Contraption contraption) {
            var bogeyParts = entity instanceof CarriageContraptionEntity carriage
                    ? PolymerRailVisuals.bogeyParts(carriage)
                    : List.<PolymerRailVisuals.Part>of();
            var nextBogeyModels =
                    bogeyParts.stream().map(PolymerRailVisuals.Part::model).toList();
            if (!bogeyModels.equals(nextBogeyModels)) {
                bogeyDisplays.forEach(this::removeElement);
                bogeyDisplays.clear();
                bogeyModels = nextBogeyModels;
                for (var part : bogeyParts) {
                    var bogey = PolymerRailVisuals.display(part);
                    bogey.setInterpolationDuration(smoothTicks(entity));
                    bogey.setTeleportDuration(smoothTicks(entity));
                    bogeyDisplays.add(addElement(bogey));
                }
            }
            previousTransform = currentTransform;
            currentTransform = PolymerContraptionPhysics.Transform.sample(entity);
            partLead = partLead(entity, previousTransform, currentTransform);
            Matrix4f bogeyLead = partLead.matrix();
            for (int i = 0; i < bogeyParts.size(); i++)
                PolymerRailVisuals.pose(bogeyDisplays.get(i), bogeyParts.get(i), bogeyLead);
            if ((entity.tickCount & 3) == 0
                    || !bogeyDisplays.isEmpty() && bogeyDisplays.getFirst().getBrightness() == null) {
                var brightness = light(entity.level(), entity.position().add(0, .5, 0));
                for (var bogey : bogeyDisplays)
                    if (!brightness.equals(bogey.getBrightness())) bogey.setBrightness(brightness);
            }
            Set<BlockPos> present = new HashSet<>();
            for (Map.Entry<BlockPos, StructureBlockInfo> entry :
                    contraption.getBlocks().entrySet()) {
                BlockPos localPos = entry.getKey().immutable();
                BlockState state = entry.getValue().state();
                if (!state.isAir()) {
                    present.add(localPos);
                }
            }

            Iterator<Map.Entry<BlockPos, DisplayElement>> iterator =
                    displays.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, DisplayElement> entry = iterator.next();
                if (!present.contains(entry.getKey())) {
                    removeElement(entry.getValue());
                    bodies.remove(entry.getValue());
                    itemModels.remove(entry.getKey());
                    iterator.remove();
                }
            }
            Iterator<Map.Entry<BlockPos, GenericEntityElement>> interactionIterator =
                    interactions.entrySet().iterator();
            while (interactionIterator.hasNext()) {
                Map.Entry<BlockPos, GenericEntityElement> entry = interactionIterator.next();
                if (!present.contains(entry.getKey())) {
                    List<CollisionCell> cells = colliders.remove(entry.getKey());
                    if (cells != null) {
                        for (CollisionCell cell : cells) {
                            removeElement(cell.shulker);
                            removeElement(cell.vehicle);
                            shulkerCount--;
                        }
                    }
                    removeElement(entry.getValue());
                    interactionIterator.remove();
                }
            }
            Iterator<Map.Entry<BlockPos, List<CollisionCell>>> colliderIterator =
                    colliders.entrySet().iterator();
            while (colliderIterator.hasNext()) {
                Map.Entry<BlockPos, List<CollisionCell>> entry = colliderIterator.next();
                if (present.contains(entry.getKey())) continue;
                for (CollisionCell cell : entry.getValue()) {
                    removeElement(cell.shulker);
                    removeElement(cell.vehicle);
                    shulkerCount--;
                }
                colliderIterator.remove();
            }

            Quaternionf rotation = rotationOf(entity);
            // Create maps local -> global as R(local - 0.5) + 0.5 + anchor, so the pivot sits at anchor + 0.5. A
            // BlockDisplay rotates about its own origin (the block's lower corner), so per-block positions would
            // make blocks orbit. Instead every display shares the pivot as its origin and carries its local
            // position inside the transform: R * T(localPos - 0.5). The client then interpolates the whole
            // contraption as one rigid body.
            Vec3 pivotOffset = entity.getAnchorVec().add(0.5, 0.5, 0.5).subtract(entity.position());
            lead = smoothTicks(entity) > 1
                    ? Lead.of(
                            previousTransform,
                            currentTransform,
                            entity.position().add(pivotOffset),
                            smoothTicks(entity) - 1)
                    : Lead.NONE;
            pivotOffset = pivotOffset.add(lead.offset());
            rotation = new Quaternionf(lead.rotation()).mul(rotation);
            for (Map.Entry<BlockPos, StructureBlockInfo> entry :
                    contraption.getBlocks().entrySet()) {
                BlockPos localPos = entry.getKey().immutable();
                BlockState state = entry.getValue().state();
                if (state.isAir()) {
                    continue;
                }

                Vec3 worldCenter = entity.toGlobalVector(Vec3.atCenterOf(localPos), 1);
                Identifier itemModel = PolymerKineticVisuals.movingItemModel(state);
                var sourceDisplayModel = PolymerBlockProjections.movingDisplayModel(state);
                if (itemModel == null && sourceDisplayModel != null)
                    itemModel = PolymerBlockProjections.displayItemModelId(sourceDisplayModel.model());
                Identifier wallModel = PolymerWallCollision.movingModel(state);
                if (wallModel != null) itemModel = wallModel;
                Identifier copycatModel = PolymerCopycatParts.contraptionItemModel(entry.getValue());
                if (copycatModel != null) itemModel = copycatModel;
                if (itemModel == null) {
                    itemModel = PolymerTintedModels.itemModel(state);
                }
                Identifier ladderModel = PolymerLadders.movingItemModel(state);
                if (ladderModel != null) itemModel = ladderModel;
                float cornerShift = itemModel == null ? 0.5f : 0;
                Vector3f translation = rotation.transform(new Vector3f(
                        localPos.getX() - cornerShift, localPos.getY() - cornerShift, localPos.getZ() - cornerShift));
                Quaternionf displayRotation = itemModel == null
                        ? rotation
                        : ladderModel != null
                                ? new Quaternionf(rotation).mul(PolymerLadders.movingRotation(state))
                                : sourceDisplayModel != null && copycatModel == null && wallModel == null
                                        ? new Quaternionf(rotation)
                                                .mul(new Quaternionf()
                                                        .rotateYXZ(
                                                                (float) Math.toRadians(-sourceDisplayModel.y()),
                                                                (float) Math.toRadians(-sourceDisplayModel.x()),
                                                                0))
                                        : new Quaternionf(rotation)
                                                .mul(PolymerKineticVisuals.staticAxisRotation(state, localPos));
                DisplayElement display = displays.get(localPos);
                boolean hidden = itemModel == null && state.isSolidRender() && enclosed(contraption, localPos);
                boolean fresh = display == null;
                boolean wrongKind = display != null
                        && (hidden
                                || (itemModel == null
                                        ? !(display instanceof BlockDisplayElement)
                                        : !itemModel.equals(itemModels.get(localPos))));
                if (wrongKind) {
                    removeElement(display);
                    bodies.remove(display);
                    displays.remove(localPos);
                    itemModels.remove(localPos);
                    display = null;
                }
                if (hidden) {
                } else if (display == null) {
                    if (itemModel == null) {
                        display = new BlockDisplayElement(state);
                    } else {
                        ItemDisplayElement itemDisplay = PolymerDisplays.item(
                                copycatModel != null
                                        ? PolymerCopycatParts.contraptionDisplayItem(
                                                entry.getValue(), entity.level(), BlockPos.containing(worldCenter))
                                        : PolymerTintedModels.isTinted(state)
                                                ? PolymerTintedModels.displayItem(
                                                        state, entity.level(), BlockPos.containing(worldCenter))
                                                : PolymerKineticVisuals.rotatingDisplayItem(itemModel),
                                ItemDisplayContext.NONE);
                        if (copycatModel != null
                                || ladderModel != null
                                || sourceDisplayModel != null && wallModel == null) {
                            itemDisplay.setRightRotation(new Quaternionf().rotationY((float) Math.PI));
                        }
                        display = itemDisplay;
                        itemModels.put(localPos, itemModel);
                    }
                    display.setInterpolationDuration(smoothTicks(entity));
                    display.setTeleportDuration(smoothTicks(entity));
                    display.setOffset(pivotOffset);
                    display.setTranslation(translation);
                    display.setLeftRotation(displayRotation);
                    PolymerContraptionLab.display(entity, display);
                    displays.put(localPos, addElement(display));
                    bodies.add(display);
                } else {
                    if (copycatModel != null && display instanceof ItemDisplayElement itemDisplay) {
                        var item = PolymerCopycatParts.contraptionDisplayItem(
                                entry.getValue(), entity.level(), BlockPos.containing(worldCenter));
                        if (!ItemStack.isSameItemSameComponents(itemDisplay.getItem(), item)) itemDisplay.setItem(item);
                    }
                    if (display instanceof BlockDisplayElement blockDisplay
                            && !blockDisplay.getBlockState().equals(state)) {
                        blockDisplay.setBlockState(state);
                    }
                    if (display.getOffset().distanceToSqr(pivotOffset) > 1.0E-8) {
                        display.setOffset(pivotOffset);
                    }
                    boolean changed = false;
                    if (translationDiffers(display.getTranslation(), translation)) {
                        display.setTranslation(translation);
                        changed = true;
                    }
                    if (rotationDiffers(display.getLeftRotation(), displayRotation)) {
                        display.setLeftRotation(displayRotation);
                        changed = true;
                    }
                    if (changed) {
                        display.startInterpolationIfDirty();
                    }
                }

                if (display != null) PolymerContraptionLab.display(entity, display);
                GenericEntityElement interaction = interactions.get(localPos);
                if (!clickable(entity, contraption, localPos)) {
                    if (interaction != null) {
                        removeElement(interaction);
                        interactions.remove(localPos);
                    }
                } else {
                    double[] span = interactionSpan(contraption, localPos, state);
                    Vec3 spanOffset = entity.toGlobalVector(
                                    Vec3.atBottomCenterOf(localPos).add(0, span[0], 0), 1)
                            .subtract(entity.position());
                    if (interaction == null) {
                        interaction = createInteraction(entity, localPos);
                        interaction.setOffset(spanOffset);
                        addElement(interaction);
                        interactions.put(localPos, interaction);
                    } else if (interaction.getOffset().distanceToSqr(spanOffset) > 1.0E-8) {
                        interaction.setOffset(spanOffset);
                    }
                    if (interaction instanceof InteractionElement box && Math.abs(box.getHeight() - span[1]) > 1.0E-4)
                        box.setHeight((float) span[1]);
                }
                if (display != null && (fresh || (entity.tickCount & 3) == 0)) {
                    var brightness = light(entity.level(), worldCenter);
                    if (!brightness.equals(display.getBrightness())) display.setBrightness(brightness);
                }
                boolean collidable = !PolymerContraptionFloors.covers(entity, localPos)
                        && !PolymerContraptionLab.covers(entity, localPos)
                        && !PolymerShiftCarry.covers(entity, localPos)
                        && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                                .isEmpty()
                        && isExposed(contraption, localPos);
                if (!collidable && colliders.containsKey(localPos)) {
                    for (CollisionCell cell : colliders.remove(localPos)) {
                        removeElement(cell.shulker);
                        removeElement(cell.vehicle);
                        shulkerCount--;
                    }
                }
                if (collidable && !colliders.containsKey(localPos)) {
                    if (shulkerCount + CELLS_PER_BLOCK <= MAX_COLLISION_SHULKERS) {
                        List<CollisionCell> cells = new ArrayList<>(CELLS_PER_BLOCK);
                        for (int i = 0; i < CELLS_PER_BLOCK; i++) {
                            cells.add(new CollisionCell(this, entity, localPos));
                            shulkerCount++;
                        }
                        colliders.put(localPos, cells);
                    }
                }
                List<CollisionCell> cells = colliders.get(localPos);
                if (cells != null) {
                    placeCells(entity, localPos, cells);
                }
            }
        }

        @Override
        public void sendPacket(Packet<? extends ClientGamePacketListener> packet) {
            int id = PolymerShiftCarry.hiddenPacketId(packet);
            if (id == PolymerShiftCarry.NO_ENTITY) super.sendPacket(packet);
            else super.sendPacket(packet, player -> !PolymerShiftCarry.hidden(player, id));
        }

        @Override
        public void sendPacket(Packet<? extends ClientGamePacketListener> packet, Predicate<ServerPlayer> predicate) {
            int id = PolymerShiftCarry.hiddenPacketId(packet);
            if (id == PolymerShiftCarry.NO_ENTITY) super.sendPacket(packet, predicate);
            else super.sendPacket(packet, player -> predicate.test(player) && !PolymerShiftCarry.hidden(player, id));
        }

        private PolymerContraptionPhysics.Surface surface(AbstractContraptionEntity entity) {
            List<PolymerContraptionPhysics.Cell> cells = new ArrayList<>(shulkerCount);
            for (List<CollisionCell> blockCells : colliders.values()) {
                for (CollisionCell cell : blockCells) {
                    cells.add(new PolymerContraptionPhysics.Cell(cell.bounds, cell.previousTop));
                }
            }
            return new PolymerContraptionPhysics.Surface(
                    entity, previousTransform, currentTransform, cells, getWatchingPlayers());
        }

        private static void placeCells(AbstractContraptionEntity entity, BlockPos localPos, List<CollisionCell> cells) {
            Vec3[] centers = new Vec3[8];
            Integer[] order = new Integer[8];
            for (int i = 0; i < 8; i++) {
                Vec3 local = new Vec3(
                        localPos.getX() + ((i & 1) == 0 ? 0.25 : 0.75),
                        localPos.getY() + ((i & 2) == 0 ? 0.25 : 0.75),
                        localPos.getZ() + ((i & 4) == 0 ? 0.25 : 0.75));
                centers[i] = entity.toGlobalVector(local, 1);
                order[i] = i;
            }
            Arrays.sort(order, (a, b) -> Double.compare(centers[b].y, centers[a].y));
            Integer[] chosen = Arrays.copyOf(order, cells.size());
            Arrays.sort(chosen);
            for (int i = 0; i < cells.size(); i++) {
                Vec3 bottomCenter =
                        centers[chosen[i]].add(0, -CELL_SCALE / 2, 0).subtract(entity.position());
                cells.get(i).moveTo(bottomCenter, entity.position());
            }
        }

        private static boolean clickable(AbstractContraptionEntity entity, Contraption contraption, BlockPos localPos) {
            if (!(entity instanceof CarriageContraptionEntity)) return true;
            if (contraption.getInteractors().containsKey(localPos)
                    || contraption.getSeats().contains(localPos)) return true;
            try {
                return contraption.getStorage().getAllItemStorages().containsKey(localPos);
            } catch (IllegalStateException uninitialized) {
                return true;
            }
        }

        static double[] interactionSpan(Contraption contraption, BlockPos localPos, BlockState state) {
            try {
                var shape = state.getShape(contraption.getContraptionWorld(), localPos);
                if (shape.isEmpty()) return new double[] {0, 1};
                double min = Math.max(0, shape.min(Direction.Axis.Y)), max = Math.min(1, shape.max(Direction.Axis.Y));
                if (max - min < .125) {
                    double mid = (min + max) / 2;
                    min = Math.max(0, Math.min(.875, mid - .0625));
                    max = min + .125;
                }
                return new double[] {min, max - min};
            } catch (RuntimeException unavailable) {
                return new double[] {0, 1};
            }
        }

        private static boolean enclosed(Contraption contraption, BlockPos localPos) {
            for (Direction direction : Direction.values()) {
                StructureBlockInfo neighbour = contraption.getBlocks().get(localPos.relative(direction));
                if (neighbour == null || !neighbour.state().isSolidRender()) return false;
            }
            return true;
        }

        private static boolean isExposed(Contraption contraption, BlockPos localPos) {
            for (Direction direction : Direction.values()) {
                StructureBlockInfo neighbour = contraption.getBlocks().get(localPos.relative(direction));
                if (neighbour == null || neighbour.state().isAir()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void startWatchingExtraPackets(
                ServerGamePacketListenerImpl player, Consumer<Packet<ClientGamePacketListener>> packetConsumer) {
            super.startWatchingExtraPackets(player, packetConsumer);
            for (List<CollisionCell> cells : colliders.values()) {
                for (CollisionCell cell : cells) {
                    cell.linkPackets(packetConsumer);
                }
            }
        }

        private static final class CollisionCell {
            private final ItemDisplayElement vehicle;
            private final SimpleEntityElement shulker;
            private AABB bounds;
            private double previousTop;

            private CollisionCell(Assembly holder, AbstractContraptionEntity entity, BlockPos localPos) {
                vehicle = new ItemDisplayElement(ItemStack.EMPTY);
                vehicle.setTeleportDuration(1);
                vehicle.setViewRange(0);
                shulker = createCollisionShulker(entity, localPos);
                holder.addElement(vehicle);
                holder.addElement(shulker);
                linkPackets(holder::sendPacket);
            }

            private void moveTo(Vec3 offset, Vec3 origin) {
                Vec3 bottom = offset.add(origin);
                previousTop = bounds == null ? bottom.y + CELL_SCALE : bounds.maxY;
                bounds = new AABB(
                        bottom.x - CELL_SCALE / 2,
                        bottom.y,
                        bottom.z - CELL_SCALE / 2,
                        bottom.x + CELL_SCALE / 2,
                        bottom.y + CELL_SCALE,
                        bottom.z + CELL_SCALE / 2);
                if (vehicle.getOffset().distanceToSqr(offset) > 1.0E-8) {
                    vehicle.setOffset(offset);
                    shulker.setOffset(offset);
                }
            }

            private void linkPackets(Consumer<Packet<ClientGamePacketListener>> packets) {
                packets.accept(VirtualEntityUtils.createClientboundSetPassengersPacket(
                        vehicle.getEntityId(), new int[] {shulker.getEntityId()}));
                AttributeInstance scale = new AttributeInstance(Attributes.SCALE, instance -> {});
                scale.setBaseValue(CELL_SCALE);
                packets.accept(new ClientboundUpdateAttributesPacket(shulker.getEntityId(), List.of(scale)));
            }
        }

        private static SimpleEntityElement createCollisionShulker(AbstractContraptionEntity entity, BlockPos localPos) {
            BlockPos targetPos = localPos.immutable();
            SimpleEntityElement shulker = new SimpleEntityElement(EntityTypes.SHULKER);
            PolymerCollisionShulkers.hide(shulker);
            shulker.setInteractionHandler(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand, Vec3 hitPosition, boolean secondary) {
                    handleInteraction(entity, targetPos, player, hand, hitPosition);
                }
            });
            return shulker;
        }

        private static InteractionElement createInteraction(AbstractContraptionEntity entity, BlockPos localPos) {
            BlockPos targetPos = localPos.immutable();
            InteractionElement interaction = new InteractionElement();
            interaction.setSize(1, 1);
            interaction.setResponse(true);
            interaction.setInteractionHandler(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand, Vec3 hitPosition, boolean secondary) {
                    handleInteraction(entity, targetPos, player, hand, hitPosition);
                }
            });
            return interaction;
        }
    }

    static void handleInteraction(
            AbstractContraptionEntity entity,
            BlockPos localPos,
            ServerPlayer player,
            InteractionHand hand,
            Vec3 relativeHit) {
        if (entity.isRemoved() || entity.level() != player.level() || hand == null) {
            return;
        }

        var info = entity.getContraption() == null
                ? null
                : entity.getContraption().getBlocks().get(localPos);
        double[] span = info == null
                ? new double[] {0, 1}
                : Assembly.interactionSpan(entity.getContraption(), localPos, info.state());
        Vec3 localHit =
                relativeHit == null || relativeHit.equals(Vec3.ZERO) ? new Vec3(0, span[1] / 2, 0) : relativeHit;
        if (!Double.isFinite(localHit.x)
                || !Double.isFinite(localHit.y)
                || !Double.isFinite(localHit.z)
                || Math.abs(localHit.x) > 0.6
                || localHit.y < -0.1
                || localHit.y > span[1] + 0.1
                || Math.abs(localHit.z) > 0.6) {
            return;
        }

        Vec3 entityOrigin =
                entity.toGlobalVector(Vec3.atBottomCenterOf(localPos).add(0, span[0], 0), 1);
        Vec3 hitPoint = entityOrigin.add(localHit);
        double reach = player.blockInteractionRange() + 0.1;
        if (player.getEyePosition().distanceToSqr(hitPoint) > reach * reach) {
            return;
        }

        if (PolymerMinecartInteractions.couple(player, hand, entity)
                || MinecartCouplingItem.handleInteractionWithMinecart(
                                player, hand, PolymerMinecartInteractions.cartTarget(entity))
                        != null
                || MinecartContraptionItem.wrenchCanBeUsedToPickUpMinecartContraptions(player, hand, entity) != null) {
            player.swing(hand);
            player.getInventory().setChanged();
            return;
        }

        Vec3 contraptionHit = entity.toLocalVector(hitPoint, 1);
        Direction face = nearestFace(contraptionHit, localPos);
        new ContraptionInteractionPacket(entity, hand, localPos, face).handle(player.connection);
        PolymerElevatorControls.interacted(entity, localPos, player);
        PolymerTrainControls.interacted(entity, localPos, player);
    }

    private static Direction nearestFace(Vec3 hit, BlockPos block) {
        double x = clamp(hit.x - block.getX());
        double y = clamp(hit.y - block.getY());
        double z = clamp(hit.z - block.getZ());
        double[] distances = {x, 1 - x, y, 1 - y, z, 1 - z};
        Direction[] faces = {
            Direction.WEST, Direction.EAST, Direction.DOWN,
            Direction.UP, Direction.NORTH, Direction.SOUTH
        };
        int nearest = 0;
        for (int i = 1; i < distances.length; i++) {
            if (distances[i] < distances[nearest]) {
                nearest = i;
            }
        }
        return faces[nearest];
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static Quaternionf rotationOf(AbstractContraptionEntity entity) {
        Vec3 origin = entity.applyRotation(Vec3.ZERO, 1);
        Vector3f xAxis = vector(entity.applyRotation(new Vec3(1, 0, 0), 1).subtract(origin));
        Vector3f yAxis = vector(entity.applyRotation(new Vec3(0, 1, 0), 1).subtract(origin));
        Vector3f zAxis = vector(entity.applyRotation(new Vec3(0, 0, 1), 1).subtract(origin));
        Matrix3f matrix = new Matrix3f().setColumn(0, xAxis).setColumn(1, yAxis).setColumn(2, zAxis);
        return matrix.getNormalizedRotation(new Quaternionf());
    }

    private static Vector3f vector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }

    private static final float RESEND = 5.0E-4f;

    private static boolean translationDiffers(Vector3fc current, Vector3f next) {
        return Math.abs(current.x() - next.x) > RESEND
                || Math.abs(current.y() - next.y) > RESEND
                || Math.abs(current.z() - next.z) > RESEND;
    }

    private static boolean rotationDiffers(Quaternionfc current, Quaternionf next) {
        return Math.abs(current.x() - next.x) > RESEND
                || Math.abs(current.y() - next.y) > RESEND
                || Math.abs(current.z() - next.z) > RESEND
                || Math.abs(current.w() - next.w) > RESEND;
    }
}
