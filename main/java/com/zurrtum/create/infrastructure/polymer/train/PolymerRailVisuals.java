package com.zurrtum.create.infrastructure.polymer.train;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllBogeyStyles;
import com.zurrtum.create.Create;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.contraptions.minecart.capability.CapabilityMinecartController;
import com.zurrtum.create.content.trains.bogey.AbstractBogeyBlock;
import com.zurrtum.create.content.trains.bogey.AllBogeySizes;
import com.zurrtum.create.content.trains.bogey.BogeySize;
import com.zurrtum.create.content.trains.bogey.StandardBogeyBlockEntity;
import com.zurrtum.create.content.trains.entity.*;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class PolymerRailVisuals {
    public record Part(Identifier model, Matrix4f pose, float meshScale) {}

    private record Key(ServerLevel level, UUID first, UUID second, boolean train) {}

    private static final Map<Key, Link> LINKS = new HashMap<>();
    private static final Map<CarriageContraptionEntity, Bogeys> BOGEYS = new IdentityHashMap<>();
    private static final float RAD = (float) (Math.PI / 180);
    private static final int MAX_LINKS = 128, MAX_CABLE_SEGMENTS = 64;
    private static final Identifier FRAME = model("bogey_frame"),
            WHEELS = model("bogey_wheel"),
            DRIVE = model("bogey_drive"),
            BELT = model("bogey_drive_belt"),
            PISTON = model("bogey_drive_piston"),
            LARGE_WHEELS = model("bogey_drive_wheel"),
            PIN = model("bogey_drive_wheel_pin"),
            ATTACHMENT = model("cart_attachment"),
            RING = model("cart_ring"),
            CONNECTOR = model("cart_connector"),
            HEAD = model("train_head"),
            CABLE = model("train_cable"),
            TOP = PolymerBlockParts.model("track/bogey/top");

    private static Identifier model(String name) {
        return PolymerBlockParts.model("polymer/rail/" + name);
    }

    private static final Set<Identifier> AXLE_PIVOT = Set.of(WHEELS, LARGE_WHEELS);

    private static final float AXLE_MESH_SCALE = 2;

    private static boolean axlePivot(Part part) {
        return AXLE_PIVOT.contains(part.model) && part.meshScale == AXLE_MESH_SCALE;
    }

    private static Identifier axleItem(Identifier model) {
        return CreateIds.id("polymer_part/axle/" + model.getPath());
    }

    public static void addAssets(ResourcePackBuilder builder) {
        Matrix4f local = new Matrix4f()
                .translate(.5f, .5f, .5f)
                .rotateY(-(float) Math.PI)
                .scale(AXLE_MESH_SCALE)
                .translate(.5f, .5f, .5f)
                .rotateY((float) Math.PI)
                .rotateY((float) Math.PI)
                .translate(-.5f, -.5f, -.5f);
        JsonArray matrix = new JsonArray();
        for (float f : new Matrix4f(local).transpose().get(new float[16])) matrix.add(Math.round(f * 1e5f) / 1e5f);
        for (Identifier model : AXLE_PIVOT) {
            JsonObject inner = new JsonObject();
            inner.addProperty("type", "minecraft:model");
            inner.addProperty("model", model.toString());
            inner.add("transformation", matrix);
            JsonObject definition = new JsonObject();
            definition.add("model", inner);
            Identifier item = axleItem(model);
            builder.addStringData(
                    "assets/" + item.getNamespace() + "/items/" + item.getPath() + ".json", definition.toString());
        }
    }

    public static void register() {
        PolymerBlockParts.<StandardBogeyBlockEntity>register(
                (be, state, time, out) -> {}, AllBlocks.SMALL_BOGEY, AllBlocks.LARGE_BOGEY);
        for (var block : List.of(AllBlocks.SMALL_BOGEY, AllBlocks.LARGE_BOGEY))
            BlockWithElementHolder.registerOverlay(block, new BlockWithElementHolder() {
                @Override
                public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                    return new PlacedBogeyHolder(level, pos, state);
                }

                @Override
                public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                    return true;
                }
            });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    private record PlacedKey(Identifier model, int occurrence) {}

    private static final class PlacedBogeyHolder extends PolymerLitModelHolder {
        final ServerLevel level;
        final BlockPos pos;
        final Map<PlacedKey, ItemDisplayElement> shown = new LinkedHashMap<>();
        BlockState lastState;
        float lastAngle = Float.NaN;
        boolean lastStandard;

        PlacedBogeyHolder(ServerLevel level, BlockPos pos, BlockState state) {
            super(level, pos);
            this.level = level;
            this.pos = pos.immutable();
            show(state, 0, true);
        }

        private void refresh() {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) return;
            var attachment = BlockAwareAttachment.get(this);
            BlockState state = attachment == null ? lastState : attachment.getBlockState();
            if (!(state.getBlock() instanceof AbstractBogeyBlock<?>)) {
                show(state, 0, false);
                return;
            }
            var be = chunk.getBlockEntity(pos);
            if (be instanceof StandardBogeyBlockEntity bogey)
                show(state, bogey.getVirtualAngle(1) * RAD, bogey.getStyle() == AllBogeyStyles.STANDARD);
        }

        private void show(BlockState state, float angle, boolean standard) {
            if (lastState == state && Float.compare(lastAngle, angle) == 0 && lastStandard == standard) return;
            lastState = state;
            lastAngle = angle;
            lastStandard = standard;
            List<Part> parts = new ArrayList<>();
            if (state.getBlock() instanceof AbstractBogeyBlock<?>) {
                Matrix4f top = new Matrix4f().translate(.5f, .5f, .5f);
                if (state.getValue(AbstractBogeyBlock.AXIS) == Axis.X) top.rotateY(-(float) Math.PI / 2);
                parts.add(new Part(TOP, top.translate(-.5f, -.5f, -.5f), 1));
            }
            if (standard && state.getBlock() instanceof AbstractBogeyBlock<?> bogey) {
                Matrix4f base = new Matrix4f().translate(.5f, .5f, .5f);
                if (state.getValue(AbstractBogeyBlock.AXIS) == Axis.X) base.rotateY((float) Math.PI / 2);
                base.translate(0, -1.5f - 1 / 128f, 0);
                collectStandard(base, bogey.getSize(), angle, parts);
            }
            Set<PlacedKey> retained = new HashSet<>();
            Map<Identifier, Integer> counts = new HashMap<>();
            for (Part part : parts) {
                PlacedKey key = new PlacedKey(part.model, counts.merge(part.model, 1, Integer::sum) - 1);
                retained.add(key);
                ItemDisplayElement element = shown.get(key);
                if (element == null) {
                    element = display(part);
                    shown.put(key, element);
                }
                Matrix4f pose = isShaft(part)
                        ? PolymerBlockParts.itemDisplayTransform(part.pose)
                        : axlePivot(part)
                                ? new Matrix4f().translate(-.5f, -.5f, -.5f).mul(part.pose)
                                : PolymerBlockParts.displayTransform(new Matrix4f(part.pose).scale(part.meshScale));
                element.setTransformation(pose);
                element.startInterpolationIfDirty();
                if (element.getHolder() == null) addElement(element);
            }
            for (var iterator = shown.entrySet().iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                if (retained.contains(entry.getKey())) continue;
                removeElement(entry.getValue());
                iterator.remove();
            }
        }

        @Override
        protected void onTick() {
            super.onTick();
            refresh();
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            refresh();
            return super.startWatching(player);
        }
    }

    public static void remove(CarriageContraptionEntity entity) {
        BOGEYS.remove(entity);
    }

    private static void clear() {
        LINKS.values().forEach(ElementHolder::destroy);
        LINKS.clear();
        BOGEYS.clear();
    }

    private static final class Bogeys {
        Carriage carriage;
        CarriageBogey first, second;
        Vec3 previous;
        long time = Long.MIN_VALUE;

        Bogeys(Carriage carriage) {
            this.carriage = carriage;
        }

        void update(CarriageContraptionEntity entity) {
            if (time == entity.level().getGameTime()) return;
            time = entity.level().getGameTime();
            Vec3 diff = previous == null ? Vec3.ZERO : entity.position().subtract(previous);
            double distance =
                    diff.length() > 16 ? 0 : diff.length() * Math.signum(-VecHelper.rotate(diff, entity.yaw, Axis.Y).x);
            previous = entity.position();
            first = shadow(carriage.leadingBogey(), first, entity, distance, true);
            second =
                    carriage.isOnTwoBogeys() ? shadow(carriage.trailingBogey(), second, entity, distance, false) : null;
            first.updateCouplingAnchor(
                    entity.position(),
                    entity.getViewXRot(1),
                    entity.getViewYRot(1),
                    carriage.bogeySpacing,
                    first.yaw.getValue(1),
                    first.pitch.getValue(1),
                    true);
            CarriageBogey trailing = second == null ? first : second;
            trailing.updateCouplingAnchor(
                    entity.position(),
                    entity.getViewXRot(1),
                    entity.getViewYRot(1),
                    carriage.bogeySpacing,
                    trailing.yaw.getValue(1),
                    trailing.pitch.getValue(1),
                    false);
        }
    }

    private static TravellingPoint copy(TravellingPoint point) {
        return new TravellingPoint(point.node1, point.node2, point.edge, point.position, point.upsideDown);
    }

    private static CarriageBogey shadow(
            CarriageBogey source,
            @Nullable CarriageBogey shadow,
            CarriageContraptionEntity entity,
            double distance,
            boolean leading) {
        if (shadow == null || shadow.type != source.type || shadow.isUpsideDown() != source.isUpsideDown())
            shadow = new CarriageBogey(
                    source.type,
                    source.isUpsideDown(),
                    source.bogeyData.copy(),
                    copy(source.leading()),
                    copy(source.trailing()));
        TravellingPoint a = source.leading(), b = source.trailing(), x = shadow.leading(), y = shadow.trailing();
        x.node1 = a.node1;
        x.node2 = a.node2;
        x.edge = a.edge;
        x.position = a.position;
        y.node1 = b.node1;
        y.node2 = b.node2;
        y.edge = b.edge;
        y.position = b.position;
        shadow.carriage = source.carriage;
        shadow.isLeading = leading;
        shadow.bogeyData = source.bogeyData.copy();
        shadow.updateAngles(entity, distance);
        return shadow;
    }

    private static Bogeys bogeys(CarriageContraptionEntity entity) {
        Carriage carriage = Objects.requireNonNull(entity.getCarriage());
        Bogeys state = BOGEYS.get(entity);
        if (state == null || state.carriage != carriage) BOGEYS.put(entity, state = new Bogeys(carriage));
        state.update(entity);
        return state;
    }

    public static List<Part> bogeyParts(CarriageContraptionEntity entity) {
        if (entity.getCarriage() == null) return List.of();
        Bogeys state = bogeys(entity);
        List<Part> out = new ArrayList<>();
        collectBogey(entity, state.first, out);
        if (state.second != null) collectBogey(entity, state.second, out);
        return out;
    }

    private static void collectBogey(CarriageContraptionEntity entity, CarriageBogey bogey, List<Part> out) {
        if (bogey.getStyle() != AllBogeyStyles.STANDARD) return;
        var contraption = entity.getContraption();
        var local = bogey.isLeading
                ? BlockPos.ZERO
                : BlockPos.ZERO.relative(
                        entity.getInitialOrientation().getCounterClockWise(), bogey.carriage.bogeySpacing);
        if (contraption == null || contraption.isHiddenInPortal(local)) return;
        Matrix4f base = new Matrix4f();
        if (!bogey.isLeading)
            base.rotateY((entity.getViewYRot(1) + 90) * RAD)
                    .rotateX(-entity.getViewXRot(1) * RAD)
                    .rotateY((float) Math.PI)
                    .translate(0, 0, -bogey.carriage.bogeySpacing)
                    .rotateY(-(float) Math.PI)
                    .rotateX(entity.getViewXRot(1) * RAD)
                    .rotateY(-(entity.getViewYRot(1) + 90) * RAD);
        base.rotateY(bogey.yaw.getValue(1) * RAD)
                .rotateX(bogey.pitch.getValue(1) * RAD)
                .translate(0, .5f, 0)
                .rotateZ(bogey.isUpsideDown() ? (float) Math.PI : 0)
                .translate(
                        0, bogey.isUpsideDown() != bogey.carriage.leadingBogey().isUpsideDown() ? 2 : 0, 0)
                .translate(0, -1.5f - 1 / 128f, 0);
        collectStandard(base, bogey.getSize(), bogey.wheelAngle.getValue(1) * RAD, out);
    }

    private static void collectStandard(Matrix4f base, BogeySize size, float angle, List<Part> out) {
        Identifier shaft = PolymerKineticVisuals.rotatingItemModel(AllBlocks.SHAFT.defaultBlockState());
        for (int z = 0; z >= -1; z--)
            out.add(new Part(
                    shaft,
                    new Matrix4f(base)
                            .translate(0, .75f, z + .5f)
                            .rotateZ(angle)
                            .rotateX((float) Math.PI / 2),
                    1));
        if (size == AllBogeySizes.SMALL) {
            out.add(new Part(FRAME, new Matrix4f(base).scale(.998046875f), 2));
            for (int z : new int[] {-1, 1})
                out.add(new Part(
                        WHEELS, new Matrix4f(base).translate(0, .75f, z).rotateX(angle), 2));
        } else if (size == AllBogeySizes.LARGE) {
            for (int z : new int[] {-1, 1})
                out.add(new Part(
                        shaft,
                        new Matrix4f(base).translate(0, .75f, z).rotateX(angle).rotateZ(-(float) Math.PI / 2),
                        1));
            out.add(new Part(DRIVE, new Matrix4f(base).scale(.998046875f), 2));
            out.add(new Part(BELT, new Matrix4f(base).scale(.998046875f), 2));
            out.add(new Part(PISTON, new Matrix4f(base).translate(0, 0, (float) Math.sin(angle) / 4), 2));
            out.add(new Part(LARGE_WHEELS, new Matrix4f(base).translate(0, 1, 0).rotateX(angle), 2));
            out.add(new Part(
                    PIN,
                    new Matrix4f(base)
                            .translate(0, 1, 0)
                            .rotateX(angle)
                            .translate(0, .25f, 0)
                            .rotateX(-angle),
                    2));
        }
    }

    public static List<Part> ponderParts(StandardBogeyBlockEntity bogey) {
        BlockState state = bogey.getBlockState();
        if (bogey.getStyle() != AllBogeyStyles.STANDARD || !(state.getBlock() instanceof AbstractBogeyBlock<?> block))
            return List.of();
        Matrix4f base = new Matrix4f().translate(.5f, .5f, .5f);
        if (state.getValue(AbstractBogeyBlock.AXIS) == Axis.X) base.rotateY((float) Math.PI / 2);
        base.translate(0, -1.5f - 1 / 128f, 0);
        List<Part> parts = new ArrayList<>();
        collectStandard(base, block.getSize(), bogey.getVirtualAngle(1) * RAD, parts);
        Identifier shaft = CreateIds.id("block/shaft");
        List<Part> out = new ArrayList<>();
        for (Part part : parts)
            out.add(
                    isShaft(part)
                            ? new Part(shaft, new Matrix4f(part.pose).translate(-.5f, -.5f, -.5f), 1)
                            : new Part(part.model, new Matrix4f(part.pose).scale(part.meshScale), 1));
        return out;
    }

    private static boolean isShaft(Part part) {
        return part.model.equals(PolymerKineticVisuals.rotatingItemModel(AllBlocks.SHAFT.defaultBlockState()));
    }

    public static ItemDisplayElement display(Part part) {
        boolean shaft = isShaft(part);
        ItemDisplayElement display = PolymerDisplays.item(
                shaft
                        ? PolymerKineticVisuals.rotatingDisplayItem(part.model)
                        : axlePivot(part)
                                ? PolymerKineticVisuals.rotatingDisplayItem(axleItem(part.model))
                                : PolymerBlockParts.displayItem(part.model),
                ItemDisplayContext.NONE);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        return display;
    }

    public static void pose(ItemDisplayElement display, Part part) {
        pose(display, part, new Matrix4f());
    }

    public static void pose(ItemDisplayElement display, Part part, Matrix4f lead) {
        Matrix4f pose = new Matrix4f(lead).mul(part.pose);
        boolean shaft = isShaft(part);
        if (!shaft && !axlePivot(part))
            pose.scale(part.meshScale).translate(.5f, .5f, .5f).rotateY((float) Math.PI);
        display.setTransformation(pose);
        display.startInterpolationIfDirty();
    }

    public static void tick(MinecraftServer server) {
        Set<Key> present = new HashSet<>();
        for (ServerLevel world : server.getAllLevels()) {
            for (var controller : new ArrayList<>(CapabilityMinecartController.loadedMinecartsByUUID
                    .get(world)
                    .values())) {
                if (!controller.isPresent()
                        || !controller.isLeadingCoupling()
                        || controller.hasContraptionCoupling(true)) continue;
                var other = CapabilityMinecartController.getIfPresent(world, controller.getCoupledCart(true));
                if (other == null || !other.isPresent()) continue;
                AbstractMinecart a = controller.cart(), b = other.cart();
                if (a.isRemoved() || b.isRemoved() || a.level() != b.level()) continue;
                show(new Key(world, a.getUUID(), b.getUUID(), false), a, b, cartParts(a, b), present);
            }
            for (var train : Create.RAILWAYS.trains.values())
                for (int i = 0; i + 1 < train.carriages.size(); i++) {
                    var ca = train.carriages.get(i).getDimensionalIfPresent(world.dimension());
                    var cb = train.carriages.get(i + 1).getDimensionalIfPresent(world.dimension());
                    if (ca == null || cb == null) continue;
                    var a = ca.entity.get();
                    var b = cb.entity.get();
                    if (a == null
                            || b == null
                            || a.isRemoved()
                            || b.isRemoved()
                            || a.getCarriage() == null
                            || b.getCarriage() == null) continue;
                    var ba = bogeys(a);
                    var bb = bogeys(b);
                    var trailing = ba.second == null ? ba.first : ba.second;
                    Vec3 start = trailing.couplingAnchors.getSecond(), end = bb.first.couplingAnchors.getFirst();
                    if (start == null || end == null || i >= train.carriageSpacing.size()) continue;
                    double distance = train.carriageSpacing.get(i)
                            - .375
                            - trailing.type.getConnectorAnchorOffset(trailing.isUpsideDown()).z
                            - bb.first.type.getConnectorAnchorOffset(bb.first.isUpsideDown()).z;
                    show(
                            new Key(world, a.getUUID(), b.getUUID(), true),
                            a,
                            b,
                            trainParts(start.subtract(a.position()), end.subtract(a.position()), distance),
                            present);
                }
        }
        LINKS.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) return false;
            entry.getValue().destroy();
            return true;
        });
        BOGEYS.keySet().removeIf(e -> e.isRemoved() || e.getCarriage() == null);
    }

    private static void show(Key key, Entity a, Entity b, List<Part> parts, Set<Key> present) {
        if (parts.isEmpty()) return;
        var watchers = key.level.getChunkSource().chunkMap.getPlayers(a.chunkPosition(), false).stream()
                .filter(p -> key.level
                        .getChunkSource()
                        .chunkMap
                        .getPlayers(b.chunkPosition(), false)
                        .contains(p))
                .filter(p -> p.position().distanceToSqr(a.position()) < 64 * 64
                        || p.position().distanceToSqr(b.position()) < 64 * 64)
                .map(p -> p.connection)
                .toList();
        if (watchers.isEmpty()) return;
        Link link = LINKS.get(key);
        if (link != null && link.origin != a) {
            link.destroy();
            LINKS.remove(key);
            link = null;
        }
        if (link == null) {
            if (LINKS.size() >= MAX_LINKS) return;
            LINKS.put(key, link = new Link(key.level, a));
        }
        present.add(key);
        link.update(parts, watchers);
    }

    private static final class Link extends AdapterHolder {
        final ManualAttachment attachment;
        final Entity origin;
        final List<ItemDisplayElement> displays = new ArrayList<>();
        List<Identifier> models = List.of();

        Link(ServerLevel world, Entity origin) {
            this.origin = origin;
            attachment = new ManualAttachment(this, world, origin::position);
        }

        void update(List<Part> parts, List<ServerGamePacketListenerImpl> watchers) {
            var next = parts.stream().map(Part::model).toList();
            if (!models.equals(next)) {
                displays.forEach(this::removeElement);
                displays.clear();
                models = next;
                for (Part part : parts) displays.add(addElement(display(part)));
            }
            for (int i = 0; i < parts.size(); i++) pose(displays.get(i), parts.get(i));
            tick();
            for (var connection : new ArrayList<>(getWatchingPlayers()))
                if (!watchers.contains(connection)) stopWatching(connection);
            for (var connection : watchers) if (!getWatchingPlayers().contains(connection)) startWatching(connection);
        }
    }

    private record CartEnd(Vec3 point, Matrix4f pose, float yaw) {}

    private static CartEnd endpoint(AbstractMinecart cart, Vec3 midpoint, Vec3 origin) {
        Vec3 position = cart.position();
        float yaw = cart.getYRot(), pitch = -cart.getXRot();
        Vec3 front = null, back = null;
        boolean old = cart.getBehavior() instanceof OldMinecartBehavior;
        if (cart.getBehavior() instanceof OldMinecartBehavior behavior) {
            Vec3 rail = behavior.getPos(cart.getX(), cart.getY(), cart.getZ());
            if (rail != null) {
                front = Objects.requireNonNullElse(
                        behavior.getPosOffs(cart.getX(), cart.getY(), cart.getZ(), .3), rail);
                back = Objects.requireNonNullElse(
                        behavior.getPosOffs(cart.getX(), cart.getY(), cart.getZ(), -.3), rail);
                Vec3 diff = back.subtract(front).normalize();
                if (diff.lengthSqr() > 0) {
                    yaw = 180 - (float) Math.toDegrees(Math.atan2(diff.z, diff.x));
                    pitch = -(float) (Math.atan(diff.y) * 73);
                } else yaw = 180 - yaw;
                position = new Vec3(rail.x, (front.y + back.y) / 2, rail.z);
            } else yaw = 180 - yaw;
        }
        float damage = Math.max(0, cart.getDamage() - 1), hurt = cart.getHurtTime() - 1;
        float roll = hurt > 0 ? (float) Math.sin(hurt) * hurt * damage / 10 * cart.getHurtDir() : 0;
        if (front == null) front = position.add(VecHelper.rotate(new Vec3(.5, 0, 0), yaw, Axis.Y));
        if (back == null) back = position.add(VecHelper.rotate(new Vec3(-.5, 0, 0), yaw, Axis.Y));
        boolean flip = front.distanceToSqr(midpoint) > back.distanceToSqr(midpoint);
        Matrix4f pose = new Matrix4f()
                .translate((float) (position.x - origin.x), (float) (position.y - origin.y), (float)
                        (position.z - origin.z));
        if (old) pose.translate(0, .5f, 0);
        pose.rotateY(yaw * RAD).rotateZ(pitch * RAD);
        if (!old) pose.translate(0, .5f, 0);
        pose.rotateX(roll * RAD).translate(flip ? -13 / 16f : 13 / 16f, 0, 0);
        var point = pose.transformPosition(new Vector3f());
        return new CartEnd(new Vec3(point.x, point.y, point.z), pose.rotateY(flip ? (float) Math.PI : 0), yaw);
    }

    static List<Part> cartParts(AbstractMinecart a, AbstractMinecart b) {
        Vec3 midpoint = a.position().add(b.position()).scale(.5);
        CartEnd first = endpoint(a, midpoint, a.position()), second = endpoint(b, midpoint, a.position());
        Vec3 diff = second.point.subtract(first.point);
        if (diff.lengthSqr() < 1e-8 || diff.length() > 64) return List.of();
        float yaw = (float) -Math.atan2(diff.z, diff.x), pitch = (float) Math.atan2(diff.y, Math.hypot(diff.x, diff.z));
        List<Part> out = new ArrayList<>();
        for (CartEnd end : List.of(first, second)) {
            out.add(new Part(ATTACHMENT, new Matrix4f(end.pose), 1));
            out.add(new Part(RING, new Matrix4f(end.pose).rotateY(yaw - end.yaw * RAD), 1));
        }
        out.add(new Part(
                CONNECTOR,
                new Matrix4f()
                        .translate((float) first.point.x, (float) first.point.y, (float) first.point.z)
                        .rotateY(yaw)
                        .rotateZ(pitch)
                        .scale((float) diff.length(), 1, 1),
                1));
        return out;
    }

    static List<Part> trainParts(Vec3 start, Vec3 end, double sourceDistance) {
        Vec3 diff = end.subtract(start);
        double length = diff.length();
        if (length < 1e-4 || length > 64) return List.of();
        float yaw = (float) -(Math.atan2(diff.z, diff.x) + Math.PI / 2),
                pitch = (float) Math.atan2(diff.y, Math.hypot(diff.x, diff.z));
        List<Part> out = new ArrayList<>();
        out.add(new Part(
                HEAD,
                new Matrix4f()
                        .translate((float) start.x, (float) start.y, (float) start.z)
                        .rotateY(yaw)
                        .rotateX(pitch),
                1));
        Matrix4f cable = new Matrix4f()
                .translate((float) start.x, (float) start.y, (float) start.z)
                .rotateY(yaw + (float) Math.PI)
                .rotateX(-pitch);
        int segments = (int) Math.round(sourceDistance * 4);
        if (segments > 0) {
            segments = Math.min(segments, MAX_CABLE_SEGMENTS);
            float stretch = (float) Math.max(0, (length - .375) * 4 / segments);
            cable.translate(0, 0, .3125f).scale(1, 1, stretch);
            for (int i = 0; i < segments; i++)
                out.add(new Part(CABLE, new Matrix4f(cable).translate(0, 0, i * .25f), 1));
        }
        out.add(new Part(
                HEAD,
                new Matrix4f()
                        .translate((float) end.x, (float) end.y, (float) end.z)
                        .rotateY(yaw + (float) Math.PI)
                        .rotateX(-pitch),
                1));
        return out;
    }
}
