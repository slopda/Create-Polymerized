package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.actors.seat.SeatBlock;
import com.zurrtum.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerAdapterConfig;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.train.PolymerBogeyInteractions;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrainControls;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrainHats;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class PolymerContraptionPassengers {
    private static final Map<AbstractContraptionEntity, Holder> HOLDERS = new IdentityHashMap<>();
    // Packet sends may run on connection threads: publish values, never entities/worlds.
    private static volatile Map<Integer, Set<Integer>> parents = Map.of();

    private record SeatLink(int anchor, int hat, List<Integer> children) {}

    private static volatile Map<Integer, SeatLink> seatLinks = Map.of();
    private static Identifier cover, lever;

    private PolymerContraptionPassengers() {}

    static Matrix4f displayPose(Matrix4f local) {
        return new Matrix4f(local).translate(.5f, .5f, .5f).rotateY((float) Math.PI);
    }

    public static void applyDisplayPose(ItemDisplayElement display, Matrix4f local) {
        Matrix4f pose = displayPose(local);
        display.setTranslation(pose.getTranslation(new Vector3f()));
        display.setScale(pose.getScale(new Vector3f()));
        display.setLeftRotation(pose.getUnnormalizedRotation(new Quaternionf()).normalize());
        display.setRightRotation(new Quaternionf());
    }

    public static void register() {
        PolymerBogeyInteractions.register();
        PolymerTrainHats.register();
        cover = PolymerBlockParts.model(Identifier.parse("create:block/controls/train/cover"));
        lever = PolymerBlockParts.model(Identifier.parse("create:block/controls/train/lever"));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HOLDERS.values().forEach(ElementHolder::destroy);
            HOLDERS.clear();
            parents = Map.of();
            seatLinks = Map.of();
        });
    }

    static void tickHolders() {
        for (var entity : new ArrayList<>(HOLDERS.keySet())) {
            if (entity.isRemoved() || entity.getContraption() == null) remove(entity);
            else {
                Holder holder = HOLDERS.get(entity);
                holder.update();
                holder.tick();
            }
        }
        publish();
    }

    static void see(AbstractContraptionEntity entity) {
        if (!HOLDERS.containsKey(entity)) {
            HOLDERS.put(entity, new Holder(entity));
            publish();
        }
    }

    static List<DisplayElement> carriedDisplays(AbstractContraptionEntity entity) {
        Holder holder = HOLDERS.get(entity);
        if (holder == null) return List.of();
        List<DisplayElement> displays = new ArrayList<>(holder.seats.values());
        for (var controls : holder.controls.values()) {
            displays.add(controls.lid);
            displays.add(controls.first);
            displays.add(controls.second);
        }
        displays.addAll(holder.doors.displays());
        displays.addAll(holder.blades.displays());
        return displays;
    }

    static PolymerContraptionVisuals.Lead lead(AbstractContraptionEntity entity) {
        Holder holder = HOLDERS.get(entity);
        return holder == null ? PolymerContraptionVisuals.Lead.NONE : holder.lead;
    }

    static void remove(AbstractContraptionEntity entity) {
        Holder holder = HOLDERS.remove(entity);
        if (holder != null) {
            holder.destroy();
            publish();
        }
    }

    public static void beforePassengerRemoved(AbstractContraptionEntity entity, Entity passenger) {
        if (!(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        Holder holder = HOLDERS.get(entity);
        if (holder == null) return;
        ItemDisplayElement anchor = holder.riders.remove(passenger.getId());
        if (anchor != null) holder.unlinkRider(anchor);
        holder.hats.remove(passenger);
        publish();
    }

    private static void publish() {
        Map<Integer, Set<Integer>> next = new HashMap<>();
        Map<Integer, SeatLink> nextSeats = new HashMap<>();
        for (var entity : HOLDERS.keySet()) {
            var children = entity.getPassengers().stream()
                    .filter(AbstractContraptionEntity.class::isInstance)
                    .map(Entity::getId)
                    .collect(Collectors.toUnmodifiableSet());
            next.put(entity.getId(), children);
            Holder holder = HOLDERS.get(entity);
            holder.riders.forEach((id, anchor) -> {
                var hat = holder.hats.link(id);
                nextSeats.put(
                        id,
                        new SeatLink(
                                anchor.getEntityId(),
                                hat == null ? -1 : hat.hat(),
                                hat == null ? List.of() : hat.children()));
            });
        }
        parents = Map.copyOf(next);
        seatLinks = Map.copyOf(nextSeats);
    }

    public static Packet<?> rewrite(Packet<?> packet) {
        return rewrite(packet, Set.of());
    }

    private static Packet<?> rewrite(Packet<?> packet, Set<Integer> alreadyMounted) {
        if (packet instanceof ClientboundAddEntityPacket spawn) {
            SeatLink seat = seatLinks.get(spawn.getId());
            if (seat == null || alreadyMounted.contains(spawn.getId())) return packet;
            ArrayList<Packet<? super ClientGamePacketListener>> parts = new ArrayList<>();
            parts.add(spawn);
            parts.add(VirtualEntityUtils.createClientboundSetPassengersPacket(seat.anchor, new int[] {spawn.getId()}));
            if (seat.hat >= 0) {
                int[] children = new int[seat.children.size() + 1];
                for (int i = 0; i < seat.children.size(); i++) children[i] = seat.children.get(i);
                children[children.length - 1] = seat.hat;
                parts.add(VirtualEntityUtils.createClientboundSetPassengersPacket(spawn.getId(), children));
            }
            return new ClientboundBundlePacket(parts);
        }
        if (packet instanceof ClientboundSetPassengersPacket ride) {
            SeatLink seat = seatLinks.get(ride.getVehicle());
            if (seat != null
                    && seat.hat >= 0
                    && Arrays.stream(ride.getPassengers()).noneMatch(id -> id == seat.hat)) {
                int[] children = Arrays.copyOf(ride.getPassengers(), ride.getPassengers().length + 1);
                children[children.length - 1] = seat.hat;
                return VirtualEntityUtils.createClientboundSetPassengersPacket(ride.getVehicle(), children);
            }
            Set<Integer> children = parents.get(ride.getVehicle());
            if (children == null) return packet;
            int[] retained = Arrays.stream(ride.getPassengers())
                    .filter(children::contains)
                    .toArray();
            return retained.length == ride.getPassengers().length
                    ? packet
                    : VirtualEntityUtils.createClientboundSetPassengersPacket(ride.getVehicle(), retained);
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            ArrayList<Packet<? super ClientGamePacketListener>> parts = new ArrayList<>();
            Set<Integer> mounted = new HashSet<>(alreadyMounted);
            for (var part : bundle.subPackets())
                if (part instanceof ClientboundSetPassengersPacket ride)
                    for (int id : ride.getPassengers()) {
                        SeatLink seat = seatLinks.get(id);
                        if (seat != null && seat.anchor == ride.getVehicle()) mounted.add(id);
                    }
            boolean changed = false;
            for (var part : bundle.subPackets()) {
                Packet<?> replacement = rewrite(part, mounted);
                changed |= replacement != part;
                if (replacement instanceof ClientboundBundlePacket additions) {
                    for (var addition : additions.subPackets()) parts.add(addition);
                    changed = true;
                    continue;
                }
                @SuppressWarnings("unchecked")
                var typed = (Packet<? super ClientGamePacketListener>) replacement;
                parts.add(typed);
            }
            if (changed) return new ClientboundBundlePacket(parts);
        }
        return packet;
    }

    private static final class Holder extends AdapterHolder {
        final AbstractContraptionEntity entity;
        final Map<Integer, ItemDisplayElement> riders = new HashMap<>();
        final Map<BlockPos, ItemDisplayElement> seats = new HashMap<>();
        final Map<BlockPos, Controls> controls = new HashMap<>();
        final Map<ServerGamePacketListenerImpl, Integer> replays = new HashMap<>();
        final Display.ItemDisplay nativeAnchor;
        final PolymerMovingDoors doors;
        final PolymerMovingBlades blades;
        final PolymerTrainHats hats;
        private PolymerContraptionPhysics.Transform previous;
        PolymerContraptionVisuals.Lead lead = PolymerContraptionVisuals.Lead.NONE;

        Holder(AbstractContraptionEntity entity) {
            this.entity = entity;
            nativeAnchor = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, entity.level());
            doors = new PolymerMovingDoors(this, entity);
            blades = new PolymerMovingBlades(this, entity);
            hats = new PolymerTrainHats(this, entity);
            EntityAttachment.of(this, entity);
        }

        @Override
        protected float displayScale() {
            return PolymerAdapterConfig.contraptionScale();
        }

        @Override
        public void sendPacket(Packet<? extends ClientGamePacketListener> packet) {
            ServerPlayer seated = heldFor(packet, player -> true);
            if (seated == null) super.sendPacket(packet);
            else super.sendPacket(packet, player -> player != seated);
        }

        @Override
        public void sendPacket(Packet<? extends ClientGamePacketListener> packet, Predicate<ServerPlayer> predicate) {
            ServerPlayer seated = heldFor(packet, predicate);
            if (seated == null) super.sendPacket(packet, predicate);
            else super.sendPacket(packet, player -> player != seated && predicate.test(player));
        }

        private ServerPlayer heldFor(Packet<?> packet, Predicate<ServerPlayer> predicate) {
            int id = PolymerShiftCarry.movePacketId(packet);
            if (id == PolymerShiftCarry.NO_ENTITY) return null;
            for (var entry : riders.entrySet()) {
                if (entry.getValue().getEntityId() != id) continue;
                return entity.level().getEntity(entry.getKey()) instanceof ServerPlayer player
                                && predicate.test(player)
                                && PolymerShiftCarry.holdSeatMove(player, packet)
                        ? player
                        : null;
            }
            return null;
        }

        void update() {
            var contraption = entity.getContraption();
            var pose = PolymerContraptionPhysics.Transform.sample(entity);
            lead = PolymerContraptionVisuals.partLead(entity, previous, pose);
            previous = pose;
            Matrix4f world = lead.matrix().mul(localToAnchor(entity));
            doors.update(world);
            blades.update(world);
            seats.entrySet().removeIf(entry -> {
                var block = contraption.getBlocks().get(entry.getKey());
                if (block != null && block.state().getBlock() instanceof SeatBlock) return false;
                removeElement(entry.getValue());
                return true;
            });
            controls.entrySet().removeIf(entry -> {
                var block = contraption.getBlocks().get(entry.getKey());
                if (block != null && block.state().getBlock() instanceof ControlsBlock) return false;
                entry.getValue().remove();
                return true;
            });
            Set<BlockPos> relevant = new HashSet<>(contraption.getSeats());
            for (var actor : contraption.getActors())
                if (actor.left.state().getBlock() instanceof ControlsBlock) relevant.add(actor.left.pos());
            for (var pos : relevant) {
                var block = contraption.getBlocks().get(pos);
                if (block == null) continue;
                if (block.state().getBlock() instanceof SeatBlock) {
                    var display = seats.computeIfAbsent(
                            block.pos(),
                            local -> part(PolymerBlockParts.model(CreateIds.id("block/"
                                    + BuiltInRegistries.BLOCK
                                            .getKey(block.state().getBlock())
                                            .getPath()))));
                    transform(
                            display,
                            new Matrix4f(world)
                                    .translate(
                                            block.pos().getX(),
                                            block.pos().getY(),
                                            block.pos().getZ()));
                    light(display, block.pos());
                } else if (entity instanceof CarriageContraptionEntity carriage
                        && block.state().getBlock() instanceof ControlsBlock) {
                    var parts = controls.computeIfAbsent(block.pos(), local -> new Controls());
                    parts.update(carriage, block.pos(), block.state(), world);
                    light(parts.lid, block.pos());
                    light(parts.first, block.pos());
                    light(parts.second, block.pos());
                }
            }
            Set<Integer> current = new HashSet<>();
            for (Entity passenger : entity.getPassengers()) {
                if (passenger instanceof AbstractContraptionEntity
                        || contraption.getSeatOf(passenger.getUUID()) == null) continue;
                current.add(passenger.getId());
                boolean created = !riders.containsKey(passenger.getId());
                var anchor = riders.computeIfAbsent(passenger.getId(), id -> {
                    var display = new ItemDisplayElement(ItemStack.EMPTY);
                    display.setTeleportDuration(1);
                    display.setViewRange(0);
                    addElement(display);
                    return display;
                });
                entity.positionRider(passenger, (rider, x, y, z) -> {
                    rider.setPos(x, y, z);
                    Vec3 nativeFeet = nativeAnchor
                            .getPassengerRidingPosition(rider)
                            .subtract(rider.getVehicleAttachmentPoint(nativeAnchor));
                    anchor.setOffset(
                            new Vec3(x, y, z).subtract(entity.position()).subtract(nativeFeet));
                });
                if (created) {
                    anchor.tick();
                    link(anchor, passenger.getId(), this::sendPacket);
                    hats.link(passenger.getId(), this::sendPacket);
                }
            }
            riders.entrySet().removeIf(entry -> {
                if (current.contains(entry.getKey())) return false;
                unlinkRider(entry.getValue());
                return true;
            });
            hats.update();
            replays.entrySet().removeIf(entry -> {
                if (!getWatchingPlayers().contains(entry.getKey())) return true;
                if (entry.getValue() > 0) {
                    entry.setValue(entry.getValue() - 1);
                    return false;
                }
                riders.forEach((id, anchor) -> {
                    link(anchor, id, entry.getKey()::send);
                    hats.link(id, entry.getKey()::send);
                });
                return true;
            });
        }

        private void light(ItemDisplayElement part, BlockPos local) {
            if ((entity.tickCount & 3) != 0 && part.getBrightness() != null) return;
            var brightness =
                    PolymerContraptionVisuals.light(entity.level(), entity.toGlobalVector(Vec3.atCenterOf(local), 1));
            if (!brightness.equals(part.getBrightness())) part.setBrightness(brightness);
        }

        private void unlinkRider(ItemDisplayElement anchor) {
            sendPacket(VirtualEntityUtils.createClientboundSetPassengersPacket(anchor.getEntityId(), new int[0]));
            removeElement(anchor);
        }

        ItemDisplayElement part(Identifier model) {
            var display = PolymerDisplays.item(PolymerBlockParts.displayItem(model), ItemDisplayContext.NONE);
            display.setInterpolationDuration(PolymerContraptionVisuals.smoothTicks(entity));
            display.setTeleportDuration(PolymerContraptionVisuals.smoothTicks(entity));
            addElement(display);
            return display;
        }

        private void transform(ItemDisplayElement part, Matrix4f pose) {
            part.setTransformation(displayPose(pose));
            part.startInterpolation();
        }

        @Override
        protected void startWatchingExtraPackets(
                ServerGamePacketListenerImpl viewer, Consumer<Packet<ClientGamePacketListener>> packets) {
            super.startWatchingExtraPackets(viewer, packets);
            riders.forEach((id, anchor) -> {
                link(anchor, id, packets);
                hats.link(id, packets);
            });
            replays.put(viewer, 1);
        }

        final class Controls {
            final ItemDisplayElement lid = part(cover), first = part(lever), second = part(lever);
            final LerpedFloat equip = LerpedFloat.linear(), speed = LerpedFloat.linear(), steer = LerpedFloat.linear();

            void update(CarriageContraptionEntity carriage, BlockPos pos, BlockState state, Matrix4f world) {
                var driver = PolymerTrainControls.driver(carriage, pos);
                var facing = state.getValue(ControlsBlock.FACING);
                boolean active = driver != null;
                equip.chase(active ? 1 : 0, .2f, LerpedFloat.Chaser.EXP);
                steer.chase(
                        active
                                ? (driver.getLastClientInput().right() ? 1 : 0)
                                        - (driver.getLastClientInput().left() ? 1 : 0)
                                : 0,
                        active ? .2f : 0,
                        LerpedFloat.Chaser.EXP);
                var actor = entity.getContraption().getActorAt(pos);
                double motion = actor == null || actor.right == null ? 0 : actor.right.motion.length();
                float sign = carriage.movingBackwards
                                ^ !facing.equals(
                                        carriage.getInitialOrientation().getCounterClockWise())
                        ? -1
                        : 1;
                speed.chase(active ? Math.min(motion, .5) * sign : 0, active ? .2f : 0, LerpedFloat.Chaser.EXP);
                equip.tickChaser();
                steer.tickChaser();
                speed.tickChaser();
                float offset = Mth.lerp(equip.getValue() * equip.getValue(), -.15f, .05f);
                var base = new Matrix4f(world)
                        .translate(pos.getX() + .5f, pos.getY() + .5f, pos.getZ() + .5f)
                        .rotateY((180 + AngleHelper.horizontalAngle(facing)) * Mth.DEG_TO_RAD);
                transform(lid, new Matrix4f(base).translate(-.5f, -.5f, -.5f));
                lever(first, base, (Mth.clamp(speed.getValue() * 70 - 25, -45, 45) - 45) * Mth.DEG_TO_RAD, offset, 0);
                lever(second, base, (Mth.clamp(steer.getValue() * 15, -45, 45) - 45) * Mth.DEG_TO_RAD, offset, .375f);
            }

            void lever(ItemDisplayElement part, Matrix4f base, float angle, float offset, float x) {
                transform(
                        part,
                        new Matrix4f(base)
                                .translate(0, .25f, .25f)
                                .rotateX(angle)
                                .translate(0, offset, 0)
                                .rotateX(45 * Mth.DEG_TO_RAD)
                                .translate(-.5f, -.5f, -.5f)
                                .translate(x, -.375f, -.1875f));
            }

            void remove() {
                removeElement(lid);
                removeElement(first);
                removeElement(second);
            }
        }
    }

    private static void link(ItemDisplayElement anchor, int rider, Consumer<Packet<ClientGamePacketListener>> packets) {
        packets.accept(
                VirtualEntityUtils.createClientboundSetPassengersPacket(anchor.getEntityId(), new int[] {rider}));
    }

    static Matrix4f localToAnchor(AbstractContraptionEntity entity) {
        Vec3 origin = entity.toGlobalVector(Vec3.ZERO, 1);
        Vec3 x = entity.toGlobalVector(new Vec3(1, 0, 0), 1).subtract(origin);
        Vec3 y = entity.toGlobalVector(new Vec3(0, 1, 0), 1).subtract(origin);
        Vec3 z = entity.toGlobalVector(new Vec3(0, 0, 1), 1).subtract(origin);
        Vec3 offset = origin.subtract(entity.position());
        return new Matrix4f()
                .setColumn(0, new Vector4f((float) x.x, (float) x.y, (float) x.z, 0))
                .setColumn(1, new Vector4f((float) y.x, (float) y.y, (float) y.z, 0))
                .setColumn(2, new Vector4f((float) z.x, (float) z.y, (float) z.z, 0))
                .setColumn(3, new Vector4f((float) offset.x, (float) offset.y, (float) offset.z, 1));
    }
}
