package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.Create;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.Contraption;
import com.zurrtum.create.content.contraptions.ContraptionCollider;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.packet.c2s.TrainCollisionPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerBedrockCarriers;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerCollisionShulkers;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.mixin.ObserverMovePacketAccessor;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.mixin.accessors.DisplayAccessor;
import eu.pb4.polymer.virtualentity.mixin.accessors.ItemDisplayAccessor;
import java.util.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerShiftCarry {
    static final double RANGE = 24;
    public static final int MAX_CELLS = 384, SUBSTEPS = 3, HISTORY = 12;
    static final double SEATED_REACH = 3;

    static final double MAX_CONTINUOUS_MOTION = 64;
    private static final int MAX_GEOMETRY_CELLS = 8192;
    public static boolean fineTrainCells = true;

    private static boolean registered;
    private static final Map<UUID, Set<PolymerShiftQueue<Packet<? super ClientGamePacketListener>>>> QUEUES =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CARRIED_TICK = new HashMap<>();
    private static final Map<UUID, AbstractContraptionEntity> CARRIED_BY = new HashMap<>();
    static final int SPEED_CHECK_GRACE = 40;
    public static boolean airMomentum;

    private static void trainHit(
            Carrier carrier, CarriageContraptionEntity carriage, ServerPlayer player, Rider rider, Vec3 push) {
        if (PolymerNativeClients.isNative(player) || !player.onGround() || player.isCreative() || player.isSpectator())
            return;
        if (carriage.nonDamageTicks != 0 || carrier.previous == null) return;
        if (!AllConfigs.server().trains.trainsCauseDamage.get()) return;
        Vec3 contact = carrier.current.motionFrom(carrier.previous, player.position());
        Vec3 own = rider.lastFeet == null
                ? Vec3.ZERO
                : player.position().subtract(push).subtract(rider.lastFeet);
        if (!validMotion(contact) || !validMotion(own)) return;
        Vec3 diff = contact.subtract(own);
        double damage = diff.length();
        if (damage <= .35f || contact.length() <= .35f) return;
        AllHandle.onTrainCollision(player.connection, new TrainCollisionPacket((int) (damage * 16), carriage.getId()));
        Vec3 knock = own.add(diff.multiply(1, 0, 1).normalize().add(0, .25, 0).scale(damage * 4))
                .add(contact);
        player.setDeltaMovement(VecHelper.clamp(knock, 3));
        player.syncVelocity = true;
    }

    static final int RECOVERY_GRACE = 40;
    static final int FREEZE_WATCH = 20;
    static final int STALL_MIN = 5;

    static final double STALL_OFFSET = 1.5, STALL_REACH = 48;
    static final int LANDING_TICKS = 4;

    static final double LANDING_DEPTH = 1.0;
    static final int STALE_HOLD = 200;
    static final int FREEZE_MIN = 2;
    static final double FREEZE_DEPTH = 8;
    static final int RIDE_COPY_HOLD = 40;
    static final int MAX_BUNDLE = 4000;

    static final long STEP_MS = 16;
    public static Tracer tracer;
    public static ObjIntConsumer<UUID> seatedSends;

    public static final int SEATED_DIRECT = 0, SEATED_LANE = 1, SEATED_TURN = 2;

    private static void seatedSend(UUID player, int kind) {
        var observer = seatedSends;
        if (observer != null) observer.accept(player, kind);
    }

    public static BiConsumer<ServerPlayer, String> seatTrace;

    private static void seatTrace(ServerPlayer player, String line) {
        var observer = seatTrace;
        if (observer != null) observer.accept(player, line);
    }

    public interface Tracer {
        void freeze(String who, int ticks, boolean wasCarrying, double lastGap, double y, boolean recovered);

        void tick(TraceTick tick);

        default void rescueMiss(String who, long now, String detail) {}
    }

    public record TraceTick(
            java.util.UUID player,
            String who,
            long now,
            double gap,
            double currentGap,
            double lastGap,
            int age,
            int staleTicks,
            int sunkTicks,
            boolean sunk,
            boolean wasAboard,
            boolean aboard,
            boolean carrying,
            boolean copied,
            boolean airborne,
            boolean grounded,
            boolean eligible,
            double dy,
            double speed,
            double tilt,
            int latency,
            double y,
            Vec3 local,
            int misses,
            boolean rescued,
            boolean trapped,
            Vec3 push) {}

    static final double LIFT_MARGIN = .002, RESCUE = -.03;
    private static final Map<AbstractContraptionEntity, Carrier> CARRIERS = new IdentityHashMap<>();
    private static final Set<AbstractContraptionEntity> UNSUPPORTED =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<UUID, AbstractContraptionEntity> MANAGED = new HashMap<>();

    private static final ScheduledExecutorService STEPS = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "polymerpatch-shift-carry-steps");
        thread.setDaemon(true);
        return thread;
    });
    private static int shiftIds;

    private PolymerShiftCarry() {}

    public record ObserverSupport(
            AbstractContraptionEntity entity,
            PolymerContraptionPhysics.Transform previous,
            PolymerContraptionPhysics.Transform current,
            Vec3 feet,
            Vec3 localFeet) {}

    private record NativeLocal(AbstractContraptionEntity owner, Vec3 local) {}

    private static final Map<UUID, NativeLocal> NATIVE_LOCAL = new HashMap<>();

    static final int NO_ENTITY = Integer.MIN_VALUE;
    private static final Map<UUID, Set<Integer>> HIDDEN = new HashMap<>();

    static boolean hidden(ServerPlayer player, int entityId) {
        if (HIDDEN.isEmpty()) return false;
        Set<Integer> ids = HIDDEN.get(player.getUUID());
        return ids != null && ids.contains(entityId);
    }

    private static final Map<UUID, List<Packet<? super ClientGamePacketListener>>> HELD_SEAT_MOVES = new HashMap<>();

    static int movePacketId(Packet<?> packet) {
        if (packet instanceof ClientboundMoveEntityPacket move)
            return ((ObserverMovePacketAccessor) move).polymerpatch$observerEntityId();
        if (packet instanceof ClientboundEntityPositionSyncPacket sync) return sync.id();
        if (packet instanceof ClientboundTeleportEntityPacket teleport) return teleport.id();
        return NO_ENTITY;
    }

    @SuppressWarnings("unchecked")
    static boolean holdSeatMove(ServerPlayer player, Packet<?> packet) {
        if (movePacketId(packet) == NO_ENTITY || !(player.getVehicle() instanceof CarriageContraptionEntity carriage))
            return false;
        Carrier carrier = CARRIERS.get(carriage);
        if (carrier == null || !carrier.riders.containsKey(player.getUUID())) return false;
        var held = HELD_SEAT_MOVES.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        for (var existing : held) if (existing == packet) return true;
        held.add((Packet<? super ClientGamePacketListener>) packet);
        return true;
    }

    private static List<Packet<? super ClientGamePacketListener>> takeSeatMoves(UUID playerId) {
        var held = HELD_SEAT_MOVES.remove(playerId);
        return held == null ? List.of() : held;
    }

    static void flushSeatMoves(MinecraftServer server) {
        if (HELD_SEAT_MOVES.isEmpty()) return;
        HELD_SEAT_MOVES.forEach((id, held) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && !player.hasDisconnected()) for (var packet : held) player.connection.send(packet);
        });
        HELD_SEAT_MOVES.clear();
    }

    static int hiddenPacketId(Packet<?> packet) {
        if (HIDDEN.isEmpty()) return NO_ENTITY;
        if (packet instanceof ClientboundMoveEntityPacket move)
            return ((ObserverMovePacketAccessor) move).polymerpatch$observerEntityId();
        if (packet instanceof ClientboundEntityPositionSyncPacket sync) return sync.id();
        if (packet instanceof ClientboundTeleportEntityPacket teleport) return teleport.id();
        if (packet instanceof ClientboundSetEntityDataPacket data) return data.id();
        return NO_ENTITY;
    }

    private static void hide(UUID playerId, int entityId) {
        HIDDEN.computeIfAbsent(playerId, id -> new HashSet<>()).add(entityId);
    }

    private static boolean unhide(UUID playerId, int entityId) {
        Set<Integer> ids = HIDDEN.get(playerId);
        if (ids == null || !ids.remove(entityId)) return false;
        if (ids.isEmpty()) HIDDEN.remove(playerId);
        return true;
    }

    private static void unhideAll(UUID playerId, Rider rider) {
        if (rider != null) for (DisplayElement display : rider.copies.keySet()) unhide(playerId, display.getEntityId());
    }

    static ObserverSupport observerSupport(ServerPlayer player) {
        if (!player.isAlive()
                || player.isSpectator()
                || player.isPassenger()
                || player.getAbilities().flying
                || player.getLastClientInput().jump()) return null;
        AbstractContraptionEntity managed = MANAGED.get(player.getUUID());
        Carrier best = null;
        double gap = Double.POSITIVE_INFINITY;
        Vec3 bestFeet = null, bestLocal = null;
        for (Carrier carrier : CARRIERS.values()) {
            if (carrier.current == null || carrier.owner.isRemoved() || carrier.owner.level() != player.level())
                continue;
            PolymerObserverFrameHistory.Report report = null;
            boolean nativeRider = PolymerNativeClients.isNative(player);
            if (!nativeRider) {
                Rider rider = carrier.riders.get(player.getUUID());
                if (carrier.owner != managed || rider == null || !rider.aboard || rider.contact.airborne()) continue;
                report = PolymerObserverFrameHistory.latest(
                        player.getUUID(),
                        carrier.owner.getUUID(),
                        player.level().dimension(),
                        player.level().getGameTime());
                if (report != null && report.feet().distanceToSqr(player.position()) > 1e-8) report = null;
            }
            if (report == null
                    && !PolymerShiftGeometry.near(player.getBoundingBox(), carrier.owner.getBoundingBox(), 2)) continue;
            List<PolymerContraptionPhysics.Transform> poses = report != null
                    ? List.of(report.frame().pose())
                    : nativeRider && !carrier.poses.isEmpty() ? List.copyOf(carrier.poses) : List.of(carrier.current);
            Fit fit = fit(player, poses, carrier.surface);
            if (fit == null || fit.gap < -.3 || fit.gap > .5 || Math.abs(fit.gap) >= gap) continue;
            Vec3 local = report == null
                    ? poses.get(Math.min(fit.age, poses.size() - 1)).toLocal(player.position())
                    : report.localFeet();
            NativeLocal last = NATIVE_LOCAL.get(player.getUUID());
            var input = player.getLastClientInput();
            if (nativeRider
                    && last != null
                    && last.owner == carrier.owner
                    && !(input.forward() || input.backward() || input.left() || input.right())) {
                double drift = local.distanceToSqr(last.local);
                for (int age = 0; age < Math.min(poses.size(), 8); age++) {
                    Vec3 candidate = poses.get(age).toLocal(player.position());
                    double d = candidate.distanceToSqr(last.local);
                    if (d < drift) {
                        drift = d;
                        local = candidate;
                    }
                }
            }
            Vec3 feet = report == null && !nativeRider ? player.position() : carrier.current.toWorld(local);
            if (!validMotion(feet.subtract(player.position()))) continue;
            best = carrier;
            gap = Math.abs(fit.gap);
            bestFeet = feet;
            bestLocal = local;
        }
        if (best == null) {
            NATIVE_LOCAL.remove(player.getUUID());
            return null;
        }
        if (PolymerNativeClients.isNative(player))
            NATIVE_LOCAL.put(player.getUUID(), new NativeLocal(best.owner, bestLocal));
        return new ObserverSupport(
                best.owner, best.previous == null ? best.current : best.previous, best.current, bestFeet, bestLocal);
    }

    record Cell(
            BlockPos local,
            double x,
            double y,
            double z,
            double s,
            int vehicle,
            int shulker,
            UUID vehicleUuid,
            UUID shulkerUuid) {
        Cell {
            shulkerUuid = PolymerBedrockCollisionProtocol.mark(shulkerUuid, s);
        }

        double top() {
            return y + s;
        }
    }

    private record CellShape(BlockPos local, double x, double y, double z, double s) {
        CellShape(Cell cell) {
            this(cell.local, cell.x, cell.y, cell.z, cell.s);
        }
    }

    private record Fit(double gap, int age) {}

    private record CopyPose(Vector3f translation, Quaternionf rotation) {}

    private static final class Rider {
        final UUID playerId;
        boolean spawned, aboard, carrying, climbing;
        boolean contactPush;
        final Set<BlockPos> ladders = new HashSet<>();

        int misses, copyTick, stale, sunk;
        boolean traceSeated;

        Vec3 traceLocal;
        long rideTick = -1000;
        long carriedTick = -1000;

        double carriedGap = Double.NaN;
        long airborneTick = -1000, watchUntil = -1000;

        double watchSupport = Double.NaN;
        boolean airCarrying;

        double holdGap = Double.NaN, holdSupport = Double.NaN;
        double lastGap = Double.NaN;
        Vec3 lastLocal, walk = Vec3.ZERO;
        Vec3 safeLocal;

        final PolymerShiftQueue<Packet<? super ClientGamePacketListener>> queue;
        final Set<Cell> visible = Collections.newSetFromMap(new IdentityHashMap<>());
        Vec3 lastFeet;
        final Map<Integer, PositionMoveRotation> sent = new HashMap<>();

        Rider(ServerPlayer player) {
            UUID playerId = player.getUUID();
            this.playerId = playerId;
            Connection connection = player.connection.connection;
            Consumer<List<Packet<? super ClientGamePacketListener>>> send =
                    packets -> rawSend(playerId, connection, packets);
            queue = new PolymerShiftQueue<>(send, PolymerShiftPacer.lane(playerId, send));
            QUEUES.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet())
                    .add(queue);
        }

        final Map<DisplayElement, Integer> copies = new IdentityHashMap<>();
        final Map<DisplayElement, List<SynchedEntityData.DataValue<?>>> copyData = new IdentityHashMap<>();
        final Map<DisplayElement, CopyPose> copyPose = new IdentityHashMap<>();
        final Map<DisplayElement, CopyPose> copyResidual = new IdentityHashMap<>();

        final Map<DisplayElement, PolymerShiftVisibility> copyVisibility = new IdentityHashMap<>();
        final PolymerShiftContact contact = new PolymerShiftContact();
        public Cell net;

        boolean netShown;
        Vec3 netFrom, netTo;
    }

    private static final class Carrier {
        final AbstractContraptionEntity owner;
        final List<Cell> cells = new ArrayList<>(), surface = new ArrayList<>();
        final Set<BlockPos> covered = new HashSet<>();
        final Map<UUID, Rider> riders = new HashMap<>();
        final ArrayDeque<PolymerContraptionPhysics.Transform> poses = new ArrayDeque<>();
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, List<AABB>> shapes = new HashMap<>();
        final List<AABB> boxes = new ArrayList<>(), sweptBoxes = new ArrayList<>();
        final Set<Cell> surfaceSet = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Cell> enclosed = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<AABB> uncovered = new ArrayList<>();

        final String teamName;
        long shapeCheck;
        PolymerContraptionPhysics.Transform previous, current;

        Carrier(AbstractContraptionEntity owner, Contraption contraption) {
            this(owner, contraption, null);
        }

        Carrier(AbstractContraptionEntity owner, Contraption contraption, Carrier prior) {
            this.owner = owner;
            Map<CellShape, ArrayDeque<Cell>> reusable = new HashMap<>();
            if (prior != null)
                for (Cell cell : prior.cells)
                    reusable.computeIfAbsent(new CellShape(cell), key -> new ArrayDeque<>())
                            .addLast(cell);
            var world = contraption.getContraptionWorld();
            Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
            teamName = "pps_" + Integer.toUnsignedString(owner.getId(), 36);
            List<AABB> thin = new ArrayList<>();
            for (var entry : blocks.entrySet()) {
                BlockPos local = entry.getKey().immutable();
                BlockState state = entry.getValue().state();
                states.put(local, state);
                List<AABB> parts = state.getCollisionShape(world, local).toAabbs();
                shapes.put(local, List.copyOf(parts));
                if (parts.isEmpty()) continue;
                covered.add(local);
                for (AABB part : parts) {
                    AABB box = part.move(local);
                    boxes.add(box);
                    List<AABB> proxies = PolymerShiftGeometry.proxies(box);
                    if (proxies.isEmpty()) {
                        if (thinLayer(part)) thin.add(box);
                        else uncovered.add(box);
                    }
                    for (AABB cube : proxies)
                        addCell(
                                local,
                                cube.minY,
                                (cube.minX + cube.maxX) / 2,
                                (cube.minZ + cube.maxZ) / 2,
                                cube.getXsize(),
                                reusable);
                }
            }
            for (AABB box : thin) {
                BlockPos local = BlockPos.containing(box.getCenter());
                if (!fullTop(shapes.get(local.below()))) {
                    uncovered.add(box);
                    continue;
                }
                addCell(local, box.maxY - 1, local.getX() + .5, local.getZ() + .5, 1, reusable);
            }
            if (fineTrainCells
                    && owner instanceof CarriageContraptionEntity
                    && cells.stream().mapToLong(cell -> cell.s > .5 ? 8 : 1).sum() <= MAX_GEOMETRY_CELLS) {
                List<Cell> coarse = List.copyOf(cells);
                cells.clear();
                for (Cell cell : coarse) {
                    if (cell.s <= .5) {
                        cells.add(cell);
                        continue;
                    }
                    double half = cell.s / 2;
                    for (int x = 0; x < 2; x++)
                        for (int y = 0; y < 2; y++)
                            for (int z = 0; z < 2; z++)
                                addCell(
                                        cell.local,
                                        cell.y + y * half,
                                        cell.x + (x - .5) * half,
                                        cell.z + (z - .5) * half,
                                        half,
                                        reusable);
                }
            }
            if (cells.size() <= MAX_GEOMETRY_CELLS)
                for (Cell cell : cells) {
                    boolean obstructed = false;
                    BlockPos at = BlockPos.containing(cell.x, cell.top() + .001, cell.z);
                    for (int dx = -1; dx <= 1 && !obstructed; dx++)
                        for (int dy = -1; dy <= 1 && !obstructed; dy++)
                            for (int dz = -1; dz <= 1 && !obstructed; dz++) {
                                BlockPos local = at.offset(dx, dy, dz);
                                for (AABB part : shapes.getOrDefault(local, List.of()))
                                    if (part.move(local).contains(new Vec3(cell.x, cell.top() + .001, cell.z))) {
                                        obstructed = true;
                                        break;
                                    }
                            }
                    if (!obstructed) surface.add(cell);
                }
            surfaceSet.addAll(surface);
            if (cells.size() <= MAX_GEOMETRY_CELLS) {
                Map<BlockPos, List<Cell>> byBlock = new HashMap<>();
                for (Cell cell : cells)
                    byBlock.computeIfAbsent(
                                    BlockPos.containing(cell.x, cell.y + cell.s / 2, cell.z), key -> new ArrayList<>())
                            .add(cell);
                for (Cell cell : cells) if (enclosedBy(byBlock, cell)) enclosed.add(cell);
            }
            if (prior != null) {
                riders.putAll(prior.riders);
                poses.addAll(prior.poses);
                previous = prior.previous;
                current = prior.current;
                shapeCheck = owner.level().getGameTime();
            }
        }

        private void addCell(
                BlockPos local,
                double minY,
                double x,
                double z,
                double size,
                Map<CellShape, ArrayDeque<Cell>> reusable) {
            if (cells.size() > MAX_GEOMETRY_CELLS) return;
            ArrayDeque<Cell> matches = reusable.get(new CellShape(local, x, minY, z, size));
            Cell cell = matches == null ? null : matches.pollFirst();
            cells.add(
                    cell != null
                            ? cell
                            : new Cell(
                                    local,
                                    x,
                                    minY,
                                    z,
                                    size,
                                    VirtualEntityUtils.requestEntityId(),
                                    VirtualEntityUtils.requestEntityId(),
                                    UUID.randomUUID(),
                                    UUID.randomUUID()));
        }

        private static boolean enclosedBy(Map<BlockPos, List<Cell>> byBlock, Cell cell) {
            double r = cell.s / 2 + 1e-3, cy = cell.y + cell.s / 2;
            return inCell(byBlock, cell.x, cy + r, cell.z)
                    && inCell(byBlock, cell.x, cy - r, cell.z)
                    && inCell(byBlock, cell.x + r, cy, cell.z)
                    && inCell(byBlock, cell.x - r, cy, cell.z)
                    && inCell(byBlock, cell.x, cy, cell.z + r)
                    && inCell(byBlock, cell.x, cy, cell.z - r);
        }

        private static boolean inCell(Map<BlockPos, List<Cell>> byBlock, double x, double y, double z) {
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int bx = (int) Math.floor(x - .5); bx <= (int) Math.floor(x + .5); bx++)
                for (int by = (int) Math.floor(y - .5); by <= (int) Math.floor(y + .5); by++)
                    for (int bz = (int) Math.floor(z - .5); bz <= (int) Math.floor(z + .5); bz++) {
                        List<Cell> near = byBlock.get(at.set(bx, by, bz));
                        if (near == null) continue;
                        for (Cell other : near) {
                            double h = other.s / 2;
                            if (Math.abs(x - other.x) < h
                                    && Math.abs(z - other.z) < h
                                    && y > other.y
                                    && y < other.y + other.s) return true;
                        }
                    }
            return false;
        }

        private static boolean thinLayer(AABB part) {
            return part.minX < 1e-6
                    && part.minZ < 1e-6
                    && part.maxX > 1 - 1e-6
                    && part.maxZ > 1 - 1e-6
                    && part.minY < 1e-6
                    && part.getYsize() <= .25;
        }

        private static boolean fullTop(List<AABB> parts) {
            if (parts != null)
                for (AABB part : parts)
                    if (part.minX < 1e-6
                            && part.minZ < 1e-6
                            && part.maxX > 1 - 1e-6
                            && part.maxZ > 1 - 1e-6
                            && part.maxY > 1 - 1e-6) return true;
            return false;
        }

        boolean changed(Contraption contraption) {
            var blocks = contraption.getBlocks();
            if (states.size() != blocks.size()) return true;
            for (var entry : blocks.entrySet())
                if (states.get(entry.getKey()) != entry.getValue().state()) return true;
            if (owner.level().getGameTime() - shapeCheck < 20) return false;
            shapeCheck = owner.level().getGameTime();
            for (var entry : blocks.entrySet())
                if (!Objects.equals(
                        shapes.get(entry.getKey()),
                        entry.getValue()
                                .state()
                                .getCollisionShape(contraption.getContraptionWorld(), entry.getKey())
                                .toAabbs())) return true;
            return false;
        }

        boolean usable() {
            return !boxes.isEmpty() && cells.size() <= MAX_GEOMETRY_CELLS;
        }

        void sample() {
            previous = current;
            current = PolymerContraptionPhysics.Transform.sample(owner);
            if (!PolymerShiftGeometry.valid(current)) {
                current = previous;
                return;
            }
            if (previous != null
                    && current.origin().distanceToSqr(previous.origin())
                            > MAX_CONTINUOUS_MOTION * MAX_CONTINUOUS_MOTION) {
                for (Rider rider : riders.values()) {
                    rider.queue.discard();
                    rider.sent.clear();
                    rider.aboard = false;
                }
                poses.clear();
                previous = current;
            }
            sweptBoxes.clear();
            for (AABB box : boxes)
                sweptBoxes.add(PolymerShiftGeometry.sweptBounds(box, previous == null ? current : previous, current));
            poses.addFirst(current);
            while (poses.size() > HISTORY) poses.removeLast();
        }

        Vec3 world(Cell cell, PolymerContraptionPhysics.Transform transform) {
            Vec3 centre = transform.toWorld(new Vec3(cell.x, cell.y + cell.s / 2, cell.z));
            return new Vec3(centre.x, centre.y - cell.s / 2, centre.z);
        }

        Vec3 displayAt(DisplayElement display) {
            return owner.position()
                    .add(display.getOffset())
                    .subtract(PolymerContraptionVisuals.leadOf(owner, display).offset());
        }

        Vec3 copyAt() {
            return current.toWorld(new Vec3(.5, .5, .5));
        }
    }

    static boolean prepare(AbstractContraptionEntity entity) {
        register();
        if (UNSUPPORTED.contains(entity)) {
            if (entity.level().getGameTime() % 20 != 0) return false;
            UNSUPPORTED.remove(entity);
        }
        Contraption contraption = entity.getContraption();
        if (contraption == null) return false;
        Carrier carrier = CARRIERS.get(entity);
        if (carrier != null && carrier.changed(contraption)) {
            Carrier replacement = new Carrier(entity, contraption, carrier);
            if (!replacement.usable()) {
                remove(entity);
                UNSUPPORTED.add(entity);
                return false;
            }
            CARRIERS.put(entity, replacement);
            carrier = replacement;
        }
        if (carrier == null) {
            carrier = new Carrier(entity, contraption);
            if (!carrier.usable()) {
                UNSUPPORTED.add(entity);
                return false;
            }
            CARRIERS.put(entity, carrier);
            PolymerContraptionFloors.remove(entity);
        }
        carrier.sample();
        return true;
    }

    static boolean covers(AbstractContraptionEntity entity, BlockPos local) {
        Carrier carrier = CARRIERS.get(entity);
        return carrier != null && carrier.covered.contains(local);
    }

    static boolean manages(ServerPlayer player) {
        return MANAGED.containsKey(player.getUUID());
    }

    static void updated(AbstractContraptionEntity entity, Collection<ServerGamePacketListenerImpl> watchers) {
        Carrier carrier = CARRIERS.get(entity);
        if (carrier == null || carrier.current == null) return;
        Set<UUID> present = new HashSet<>();
        for (ServerGamePacketListenerImpl watcher : watchers) {
            ServerPlayer player = watcher.player;
            if (player.level() != entity.level()
                    || player.isSpectator()
                    || !PolymerShiftGeometry.near(player.getBoundingBox(), entity.getBoundingBox(), RANGE)
                    || PolymerNativeClients.isNative(player)) continue;
            present.add(player.getUUID());
            try {
                control(carrier, player);
            } catch (RuntimeException error) {
                Create.LOGGER.warn("Shift carry failed for {}; releasing", player.getScoreboardName(), error);
                leave(carrier, player);
            }
        }
        for (UUID id : new ArrayList<>(carrier.riders.keySet())) {
            if (present.contains(id)) continue;
            ServerPlayer player = NativePresentationPolicy.presentationPlayer(
                    entity.level().getServer().getPlayerList(), id);
            if (player != null) leave(carrier, player);
            else {
                Rider gone = carrier.riders.remove(id);
                unhideAll(id, gone);
                retire(id, gone);
                if (MANAGED.get(id) == entity) MANAGED.remove(id);
            }
        }
    }

    static void remove(AbstractContraptionEntity entity) {
        UNSUPPORTED.remove(entity);
        Carrier carrier = CARRIERS.remove(entity);
        if (carrier == null) return;
        for (UUID id : new ArrayList<>(carrier.riders.keySet())) {
            ServerPlayer player = entity.level().getServer() == null
                    ? null
                    : NativePresentationPolicy.presentationPlayer(
                            entity.level().getServer().getPlayerList(), id);
            if (player != null) leave(carrier, player);
            if (MANAGED.get(id) == entity) MANAGED.remove(id);
        }
        for (var entry : carrier.riders.entrySet()) {
            unhideAll(entry.getKey(), entry.getValue());
            retire(entry.getKey(), entry.getValue());
        }
        carrier.riders.clear();
    }

    static void clear() {
        for (AbstractContraptionEntity entity : new ArrayList<>(CARRIERS.keySet())) remove(entity);
        UNSUPPORTED.clear();
        MANAGED.clear();
        CARRIED_TICK.clear();
        CARRIED_BY.clear();
        NATIVE_LOCAL.clear();
        HIDDEN.clear();
        PolymerObserverFrameHistory.clearAll();
        PolymerShiftDiagnostics.clear();
    }

    public static void cancelPending(ServerPlayer player) {
        PolymerObserverFrameHistory.clear(player.getUUID());
        Set<PolymerShiftQueue<Packet<? super ClientGamePacketListener>>> queues = QUEUES.get(player.getUUID());
        if (queues != null) for (var queue : queues) queue.cancel();
    }

    private static void register() {
        if (registered) return;
        registered = true;
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetPlayer(handler.player));
        ServerPlayerEvents.LEAVE.register(PolymerShiftCarry::forgetPlayer);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            forgetPlayer(oldPlayer);
            forgetPlayer(newPlayer);
        });
    }

    private static void retire(UUID id, Rider rider) {
        if (rider == null) return;
        rider.queue.cancel();
        Set<PolymerShiftQueue<Packet<? super ClientGamePacketListener>>> queues = QUEUES.get(id);
        if (queues != null) {
            queues.remove(rider.queue);
            if (queues.isEmpty()) QUEUES.remove(id, queues);
        }
    }

    static void forgetPlayer(ServerPlayer player) {
        cancelPending(player);
        PolymerShiftPacer.forget(player.getUUID());
        for (Carrier carrier : CARRIERS.values()) leave(carrier, player);
        MANAGED.remove(player.getUUID());
        CARRIED_TICK.remove(player.getUUID());
        CARRIED_BY.remove(player.getUUID());
        NATIVE_LOCAL.remove(player.getUUID());
        HIDDEN.remove(player.getUUID());
        PolymerShiftDiagnostics.forget(player.getUUID());
    }

    private static void control(Carrier carrier, ServerPlayer player) {
        Rider rider = carrier.riders.get(player.getUUID());
        boolean renewed = rider != null && rider.queue.closed();
        if (renewed) rider = renew(carrier, player, rider);
        boolean awaiting = player.connection.awaitingPositionFromClient != null;
        if (rider == null) {
            if (awaiting) return;
            rider = new Rider(player);
            carrier.riders.put(player.getUUID(), rider);
        }
        Connection connection = player.connection.connection;
        rider.queue.drain();
        List<Packet<? super ClientGamePacketListener>> bundle = new ArrayList<>();
        boolean fresh = !rider.spawned;
        if (fresh) {
            bundle.add(team(carrier, List.of(), true));
            rider.spawned = true;
        }
        bundle.addAll(syncCells(carrier, rider, player));
        Fit fit = fit(player, carrier.poses, carrier.surface);
        boolean eligible = !awaiting && player.isAlive() && !player.isPassenger() && !player.getAbilities().flying;
        boolean stale = !awaiting
                && rider.lastFeet != null
                && rider.lastFeet.distanceToSqr(player.position()) < 1e-12
                && carrier.previous != null
                && carrier.current.origin().distanceToSqr(carrier.previous.origin()) > 1e-6;
        int frozeFor = stale ? 0 : rider.stale;
        rider.stale = stale ? rider.stale + 1 : 0;
        boolean hold = stale && rider.stale <= STALE_HOLD;
        boolean watching = player.level().getGameTime() <= rider.watchUntil;
        rider.climbing = eligible && climbable(carrier, player.position());
        boolean wasAboard = rider.aboard;
        if (!eligible) {
            rider.aboard = false;
            rider.misses = 0;
        } else if (rider.climbing) {
            rider.aboard = true;
            rider.misses = 0;
        } else if (rider.aboard) {
            if (fit == null || fit.gap < -.6 || fit.gap > 2.5) {
                if (!hold && !watching && ++rider.misses >= 6) {
                    rider.aboard = false;
                    rider.misses = 0;
                }
            } else rider.misses = 0;
        } else if (fit != null && fit.gap >= -.3 && fit.gap <= .5) {
            rider.aboard = true;
            rider.misses = 0;
        }
        PolymerContraptionPhysics.Transform before = carrier.previous == null ? carrier.current : carrier.previous;
        PolymerContraptionPhysics.Transform reported = reportedPose(carrier, player, fit);
        Vec3 localFeet = reported.toLocal(player.position());
        if (stale && fit != null && fit.gap > -.6 && fit.gap < .5) {
            rider.holdGap = fit.gap;
            rider.holdSupport = localFeet.y - fit.gap;
        }
        Vec3 previousLocal = rider.lastLocal;
        double lastSupport = rider.lastLocal == null || !Double.isFinite(rider.lastGap)
                ? Double.NaN
                : rider.lastLocal.y - rider.lastGap;
        if (!stale) {
            rider.walk = rider.lastLocal == null || rider.lastLocal.distanceToSqr(localFeet) > 1
                    ? Vec3.ZERO
                    : new Vec3(localFeet.x - rider.lastLocal.x, 0, localFeet.z - rider.lastLocal.z);
            rider.lastLocal = localFeet;
        }
        Vec3 motion = carrier.current.toWorld(localFeet).subtract(before.toWorld(localFeet));
        double dy = Double.isFinite(motion.y) ? motion.y : 0;
        double motionSq = motion.lengthSqr();
        double observedY = rider.lastFeet == null ? player.getKnownMovement().y : player.getY() - rider.lastFeet.y;
        Fit currentFit = fit(player, List.of(carrier.current), carrier.surface);
        boolean grounded = hold && rider.aboard && eligible
                ? !rider.contact.airborne()
                : rider.contact.update(
                        player.level().getGameTime(),
                        eligible,
                        player.getLastClientInput().jump(),
                        fit == null ? Double.NaN : fit.gap,
                        currentFit == null ? Double.NaN : currentFit.gap,
                        observedY - motion.y,
                        motion.y);
        if (rider.contact.airborne()) rider.airborneTick = player.level().getGameTime();
        boolean owns = chooseOwner(carrier, rider, player, fit, currentFit, eligible);
        boolean wasCarrying = rider.carrying;
        boolean carriedThisTick = Objects.equals(
                CARRIED_TICK.get(player.getUUID()), player.level().getGameTime());
        rider.carrying = owns
                && (grounded && fit != null || rider.climbing || hold && rider.carrying)
                && validMotion(motion)
                && motionSq > 1e-10
                && !carriedThisTick;
        boolean airCarry = airMomentum
                && owns
                && !rider.carrying
                && !rider.climbing
                && rider.contact.airborne()
                && (wasCarrying || rider.airCarrying)
                && validMotion(motion)
                && motionSq > 1e-10
                && !carriedThisTick;
        rider.airCarrying = airCarry;
        if (airCarry) rider.carrying = true;
        boolean copied = owns;
        if (rider.carrying) {
            CARRIED_TICK.put(player.getUUID(), player.level().getGameTime());
            CARRIED_BY.put(player.getUUID(), carrier.owner);
            rider.carriedTick = player.level().getGameTime();
            if (!stale && fit != null) rider.carriedGap = fit.gap;
        }
        long tick = player.level().getGameTime();
        if (copied
                || rider.aboard
                || MANAGED.containsKey(player.getUUID())
                || player.getVehicle() instanceof AbstractContraptionEntity) rider.rideTick = tick;
        boolean renderCopies =
                copied || carrier.owner instanceof CarriageContraptionEntity && tick - rider.rideTick <= RIDE_COPY_HOLD;
        boolean seatClock = player.getVehicle() instanceof CarriageContraptionEntity;
        if (renderCopies) bundle.addAll(copySync(carrier, rider, before, player, seatClock));
        else if (!rider.copies.isEmpty()) bundle.addAll(copyRestore(carrier, rider));
        boolean moved =
                carrier.previous != null && carrier.previous.origin().distanceToSqr(carrier.current.origin()) > 1e-12;
        boolean stepCells = !copied && moved && MANAGED.containsKey(player.getUUID());
        if (!copied
                && !stepCells
                && (fresh
                        || renewed
                        || moved
                        || carrier.previous == null
                        || !sameBasis(carrier.previous, carrier.current)))
            for (Cell cell : rider.visible) {
                Vec3 at = carrier.world(cell, carrier.current);
                move(rider, bundle, cell.vehicle, at, 0, 0);
                move(rider, bundle, cell.shulker, at, 0, 0);
            }
        if (rider.carrying) {
            player.connection.resetFlyingTicks();
            player.resetFallDistance();
            carrier.owner.registerColliding(player);
        } else if (rider.climbing) {
            player.connection.resetFlyingTicks();
            player.resetFallDistance();
        }
        boolean exact = exactReport(carrier, player);
        boolean canPush = !awaiting
                && !stale
                && player.isAlive()
                && carrier.owner.canCollideWith(player)
                && carrier.previous != null
                && !player.level().tickRateManager().isEntityFrozen(player)
                && !protectedByOtherCarrier(carrier, player);
        boolean cabin = copied && eligible && !rider.climbing && !rider.contact.airborne();
        Vec3 plannedPush =
                canPush && cabin ? cabinPush(carrier, player, rider, reported, previousLocal, lastSupport) : null;
        Vec3 supportFeet = plannedPush == null
                ? localFeet
                : reported.toLocal(player.position().add(plannedPush));
        Packet<ClientGamePacketListener> rescue = null;
        double allowance = .7 * Math.abs(dy);
        boolean held = copied && supportedElsewhere(carrier, player, allowance);
        boolean landing =
                player.level().getGameTime() - rider.airborneTick <= LANDING_TICKS && fit != null && fit.gap < RESCUE;
        boolean sunk = copied
                && !stale
                && !held
                && (!rider.contact.airborne() || landing)
                && fit != null
                && fit.gap < RESCUE - allowance
                && fit.gap > (landing ? -LANDING_DEPTH : -.6)
                && player.getY() - supportTop(carrier, player, localFeet, reported) < RESCUE - allowance;
        boolean trapped = copied
                && !stale
                && !held
                && (!rider.contact.airborne() || landing)
                && exact
                && trappedInCell(carrier, rider, player, reported);
        if (!stale) rider.sunk = sunk ? rider.sunk + 1 : 0;
        if (trapped
                || sunk
                        && (rider.sunk >= 2
                                || !Double.isNaN(rider.lastGap) && fit.gap - rider.lastGap < -.05 - allowance)) {
            double top = rescueTop(carrier, player, supportFeet, before, rider.carrying);
            if (!Double.isNaN(top)) {
                rescue = rescuePacket(top + .01);
                rider.sunk = 0;
            } else if (tracer != null)
                tracer.rescueMiss(
                        player.getName().getString(),
                        player.level().getGameTime(),
                        (trapped ? "trapped" : "sunk") + " "
                                + supportMiss(
                                        carrier,
                                        player,
                                        supportFeet,
                                        rider.carrying
                                                ? observerFrame(before, carrier.current, 1.0 / SUBSTEPS)
                                                : carrier.current));
        }
        long sinceCarried = player.level().getGameTime() - rider.carriedTick;
        boolean recentlyCarried = wasCarrying || sinceCarried <= frozeFor + RECOVERY_GRACE;
        double standGap =
                wasCarrying ? (Double.isNaN(rider.lastGap) ? rider.holdGap : rider.lastGap) : rider.carriedGap;
        double standSupport = Double.isFinite(lastSupport) ? lastSupport : rider.holdSupport;
        boolean recovered = false;
        if (frozeFor >= FREEZE_MIN
                && recentlyCarried
                && !Double.isNaN(standGap)
                && standGap > -.6
                && standGap < .5
                && Double.isFinite(standSupport)) {
            rider.watchUntil = player.level().getGameTime() + FREEZE_WATCH;
            rider.watchSupport = standSupport;
        }
        if (rescue == null
                && frozeFor >= FREEZE_MIN
                && recentlyCarried
                && copied
                && eligible
                && !rider.climbing
                && !Double.isNaN(standGap)
                && standGap > -.6
                && standGap < .5) {
            double top = recoveryTop(carrier, player, supportFeet, before, rider.carrying, standSupport);
            if (!Double.isNaN(top) && player.getY() < top + RESCUE) {
                rescue = rescuePacket(top + .01);
                rider.sunk = 0;
                rider.aboard = true;
                rider.misses = 0;
                recovered = true;
            }
        }
        if (rescue == null
                && !recovered
                && frozeFor >= STALL_MIN
                && recentlyCarried
                && copied
                && eligible
                && !rider.climbing
                && previousLocal != null
                && !Double.isNaN(standGap)
                && standGap > -.6
                && standGap < .5
                && Double.isFinite(standSupport)) {
            Vec3 spot = new Vec3(previousLocal.x, standSupport - .01, previousLocal.z);
            Vec3 target = carrier.current.toWorld(spot);
            double off = Math.hypot(target.x - player.getX(), target.z - player.getZ());
            double top = off > STALL_OFFSET && off < STALL_REACH
                    ? recoveryTop(carrier, player, spot, before, rider.carrying, standSupport)
                    : Double.NaN;
            if (!Double.isNaN(top)) {
                rescue = new ClientboundPlayerPositionPacket(
                        nextId(),
                        new PositionMoveRotation(
                                new Vec3(target.x - player.getX(), top + .01, target.z - player.getZ()),
                                Vec3.ZERO,
                                0,
                                0),
                        RESCUE_FLAGS);
                player.setPos(target.x, top + .01, target.z);
                player.connection.resetPosition();
                player.connection.resetFlyingTicks();
                player.resetFallDistance();
                rider.sunk = 0;
                rider.aboard = true;
                rider.misses = 0;
                recovered = true;
                rider.watchUntil = player.level().getGameTime() + FREEZE_WATCH;
                rider.watchSupport = standSupport;
                if (tracer != null)
                    tracer.freeze(
                            player.getScoreboardName() + " on "
                                    + carrier.owner.getStringUUID().substring(0, 8) + " (stall "
                                    + String.format(Locale.ROOT, "%.1f", off) + ")",
                            frozeFor,
                            wasCarrying,
                            standGap,
                            player.getY(),
                            true);
            }
        }
        if (frozeFor > 0) {
            rider.holdGap = Double.NaN;
            rider.holdSupport = Double.NaN;
        }
        if (rescue == null
                && !recovered
                && !stale
                && frozeFor == 0
                && watching
                && copied
                && eligible
                && !rider.climbing
                && (fit == null || fit.gap <= -.6)) {
            double top = recoveryTop(carrier, player, supportFeet, before, rider.carrying, rider.watchSupport);
            if (!Double.isNaN(top) && player.getY() < top + RESCUE) {
                rescue = rescuePacket(top + .01);
                rider.sunk = 0;
                rider.aboard = true;
                rider.misses = 0;
                recovered = true;
                rider.watchUntil = -1000;
                if (tracer != null)
                    tracer.freeze(
                            player.getScoreboardName() + " on "
                                    + carrier.owner.getStringUUID().substring(0, 8) + " (watch)",
                            0,
                            wasCarrying,
                            rider.lastGap,
                            player.getY(),
                            true);
            }
        }
        if (tracer != null && frozeFor >= FREEZE_MIN)
            tracer.freeze(
                    player.getScoreboardName() + " on "
                            + carrier.owner.getStringUUID().substring(0, 8),
                    frozeFor,
                    wasCarrying,
                    rider.lastGap,
                    player.getY(),
                    recovered);
        Vec3 supportMotion = plannedPush == null
                ? motion
                : carrier.current.toWorld(supportFeet).subtract(before.toWorld(supportFeet));
        Vec3 carryMotion = airCarry
                ? new Vec3(motion.x, 0, motion.z)
                : rider.carrying && !rider.climbing
                        ? clearedMotion(
                                carrier,
                                player,
                                rider,
                                liftedMotion(carrier, player, rider, supportMotion, supportFeet, exact ? 0 : allowance),
                                supportFeet)
                        : supportMotion;
        Vec3 push = null;
        boolean ridingCarriage = carrier.owner.collidingEntities.containsKey(player);
        if (canPush) {
            push = cabin ? applyPush(carrier, player, plannedPush) : pushMotion(carrier, player, rider, copied);
            if (push != null && !stale && rider.lastLocal != null)
                rider.lastLocal = reported.toLocal(player.position());
        }
        rider.contactPush = cabin && push != null;
        if (push != null && !copied && !ridingCarriage && carrier.owner instanceof CarriageContraptionEntity carriage)
            trainHit(carrier, carriage, player, rider, push);
        rider.lastFeet = player.position();
        if (tracer != null)
            tracer.tick(new TraceTick(
                    player.getUUID(),
                    player.getScoreboardName() + " on "
                            + carrier.owner.getStringUUID().substring(0, 8),
                    player.level().getGameTime(),
                    fit == null ? Double.NaN : fit.gap,
                    currentFit == null ? Double.NaN : currentFit.gap,
                    rider.lastGap,
                    fit == null ? -1 : fit.age,
                    rider.stale,
                    rider.sunk,
                    sunk,
                    wasAboard,
                    rider.aboard,
                    rider.carrying,
                    copied,
                    rider.contact.airborne(),
                    grounded,
                    eligible,
                    dy,
                    Math.sqrt(motionSq),
                    Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, carrier.current.y().y)))),
                    player.connection.latency(),
                    player.getY(),
                    carrier.current.toLocal(player.position()),
                    rider.misses,
                    rescue != null,
                    trapped,
                    push));
        if (!stale) rider.lastGap = fit == null ? Double.NaN : fit.gap;
        bundle.addAll(syncLadders(carrier, rider, player, motion));
        bundle.addAll(syncNet(
                carrier,
                rider,
                player,
                supportFeet,
                before,
                copied && rider.carrying && !rider.climbing && !rider.contact.airborne()));
        bundle.removeIf(Objects::isNull);
        long now = player.level().getGameTime();
        PolymerShiftDiagnostics.record(player.getUUID(), now, bundle);
        String stateRoute = "none";
        if (!bundle.isEmpty()) {
            if (seatClock && PolymerShiftPacer.idle(player.getUUID())) {
                NativePresentationPolicy.sendPresentation(player, new ClientboundBundlePacket(bundle));
                seatedSend(player.getUUID(), SEATED_DIRECT);
                stateRoute = "direct";
            } else {
                rider.queue.sendNow(bundle);
                if (seatClock) seatedSend(player.getUUID(), SEATED_LANE);
                stateRoute = "lane";
            }
        }
        if (seatTrace != null && (seatClock || rider.traceSeated)) {
            boolean seatedHere = player.getVehicle() == carrier.owner;
            Vec3 local = carrier.current.toLocal(player.position());
            Vec3 drift = rider.traceSeated && rider.traceLocal != null ? local.subtract(rider.traceLocal) : Vec3.ZERO;
            double step =
                    carrier.previous == null ? 0 : carrier.previous.origin().distanceTo(carrier.current.origin());
            long data = bundle.stream()
                    .filter(p -> p instanceof ClientboundSetEntityDataPacket)
                    .count();
            String mount = seatedHere
                    ? "seated"
                    : rider.traceSeated
                            ? "DISMOUNT(vehicle="
                                    + (player.getVehicle() == null
                                            ? "none"
                                            : player.getVehicle().getId()) + ")"
                            : "other-carriage";
            seatTrace(
                    player,
                    String.format(
                            Locale.ROOT,
                            "%d carriage=%d %s local=%.4f,%.4f,%.4f drift=%.4f,%.4f,%.4f deckStep=%.4f state=%s(%d pkts,%d data) laneIdle=%s",
                            now,
                            carrier.owner.getId(),
                            mount,
                            local.x,
                            local.y,
                            local.z,
                            drift.x,
                            drift.y,
                            drift.z,
                            step,
                            stateRoute,
                            bundle.size(),
                            data,
                            PolymerShiftPacer.idle(player.getUUID())));
            rider.traceSeated = seatedHere;
            rider.traceLocal = seatedHere ? local : null;
        }
        double seatYaw = player.getVehicle() == carrier.owner ? yawDelta(carrier) : 0;
        if (Math.abs(seatYaw) < 1e-4) seatYaw = 0;
        if (copied || stepCells || renderCopies && !rider.copies.isEmpty())
            substeps(
                    carrier,
                    rider,
                    connection,
                    carryMotion,
                    0,
                    rider.carrying,
                    copied ? validMotion(motion) && motionSq > 1e-10 : moved || !sameBasis(before, carrier.current),
                    rescue,
                    copied || stepCells,
                    seatClock,
                    push,
                    seatYaw);
        else if (push != null || seatYaw != 0) {
            if (seatClock) seatedSend(player.getUUID(), SEATED_TURN);
            List<List<Packet<? super ClientGamePacketListener>>> steps = new ArrayList<>();
            for (int k = 0; k < SUBSTEPS; k++) {
                List<Packet<? super ClientGamePacketListener>> step = new ArrayList<>();
                if (push != null) step.add(shiftPacket(push.scale(1.0 / SUBSTEPS), 0));
                if (seatYaw != 0) step.add(seatTurn(seatYaw / SUBSTEPS));
                steps.add(step);
                PolymerShiftDiagnostics.record(rider.playerId, now, step);
            }
            rider.queue.submit(steps, STEP_MS, (task, delay) -> STEPS.schedule(task, delay, TimeUnit.MILLISECONDS));
        }
        PolymerShiftDiagnostics.gauge(
                player.getUUID(),
                now,
                carrier.owner,
                rider.visible.size(),
                rider.copies.size(),
                player.getVehicle() instanceof AbstractContraptionEntity);
    }

    static boolean validMotion(Vec3 motion) {
        return Double.isFinite(motion.x)
                && Double.isFinite(motion.y)
                && Double.isFinite(motion.z)
                && motion.lengthSqr() <= MAX_CONTINUOUS_MOTION * MAX_CONTINUOUS_MOTION;
    }

    private static boolean chooseOwner(
            Carrier carrier, Rider rider, ServerPlayer player, Fit fit, Fit currentFit, boolean eligible) {
        UUID id = player.getUUID();
        if (!eligible || !rider.aboard) {
            if (MANAGED.get(id) == carrier.owner) MANAGED.remove(id);
            return false;
        }
        AbstractContraptionEntity prior = MANAGED.get(id);
        if (prior != null && prior != carrier.owner) {
            Carrier other = CARRIERS.get(prior);
            Rider previous = other == null ? null : other.riders.get(id);
            Fit oldFit = other == null ? null : fit(player, other.poses, other.surface);
            boolean better = fit != null
                    && fit.gap >= -.3
                    && fit.gap <= .5
                    && currentFit != null
                    && currentFit.gap >= -.3
                    && currentFit.gap <= .5
                    && (oldFit == null || Math.abs(fit.gap) + .15 < Math.abs(oldFit.gap));
            if (previous != null && previous.aboard && !previous.queue.closed() && !better) return false;
            if (Objects.equals(CARRIED_TICK.get(id), player.level().getGameTime())) return false;
            if (previous != null) {
                previous.queue.discard();
                previous.sent.clear();
                previous.aboard = false;
                var restore = copyRestore(other, previous);
                if (!restore.isEmpty()) sendOrdered(player, restore);
            }
        }
        MANAGED.put(id, carrier.owner);
        return true;
    }

    private static boolean supportedElsewhere(Carrier carrier, ServerPlayer player, double allowance) {
        for (Carrier other : CARRIERS.values()) {
            if (other == carrier || other.owner.level() != player.level()) continue;
            Rider rider = other.riders.get(player.getUUID());
            if (rider == null || !rider.aboard) continue;
            Fit fit = fit(player, other.poses, other.surface);
            if (fit != null && fit.gap >= RESCUE - allowance && fit.gap <= .1) return true;
        }
        return false;
    }

    private static boolean trappedInCell(
            Carrier carrier, Rider rider, ServerPlayer player, PolymerContraptionPhysics.Transform frame) {
        AABB body = player.getBoundingBox();
        double feet = body.minY;
        boolean inside = false;
        for (Cell cell : rider.visible) {
            Vec3 at = carrier.world(cell, frame);
            double s = cell.s / 2;
            if (at.x + s <= body.minX + 1e-7
                    || at.x - s >= body.maxX - 1e-7
                    || at.z + s <= body.minZ + 1e-7
                    || at.z - s >= body.maxZ - 1e-7) continue;
            double top = at.y + cell.s;
            if (top <= feet + 1e-7 && top >= feet - .1) return false;
            if (feet > at.y && feet < top - 1e-4) inside = true;
        }
        return inside;
    }

    private static boolean sameBasis(PolymerContraptionPhysics.Transform a, PolymerContraptionPhysics.Transform b) {
        return a.x().distanceToSqr(b.x()) < 1e-12
                && a.y().distanceToSqr(b.y()) < 1e-12
                && a.z().distanceToSqr(b.z()) < 1e-12;
    }

    private static Rider renew(Carrier carrier, ServerPlayer player, Rider old) {
        retire(player.getUUID(), old);
        Rider rider = new Rider(player);
        rider.spawned = old.spawned;
        rider.copyTick = old.copyTick;
        rider.visible.addAll(old.visible);
        rider.ladders.addAll(old.ladders);
        rider.copies.putAll(old.copies);
        rider.copyData.putAll(old.copyData);
        rider.copyVisibility.putAll(old.copyVisibility);
        rider.net = old.net;
        rider.netShown = old.netShown;
        carrier.riders.put(player.getUUID(), rider);
        return rider;
    }

    public static boolean exemptFromSpeedCheck(ServerPlayer player) {
        AbstractContraptionEntity owner = CARRIED_BY.get(player.getUUID());
        Long tick = CARRIED_TICK.get(player.getUUID());
        if (owner != null
                && tick != null
                && !owner.isRemoved()
                && owner.level() == player.level()
                && player.level().getGameTime() - tick <= SPEED_CHECK_GRACE
                && CARRIERS.containsKey(owner)
                && PolymerShiftGeometry.near(player.getBoundingBox(), owner.getBoundingBox(), RANGE)) return true;
        long now = player.level().getGameTime();
        for (Carrier carrier : CARRIERS.values()) {
            Rider rider = carrier.riders.get(player.getUUID());
            if (rider == null
                    || now - rider.rideTick > SPEED_CHECK_GRACE
                    || carrier.owner.isRemoved()
                    || carrier.owner.level() != player.level()) continue;
            if (PolymerShiftGeometry.near(player.getBoundingBox(), carrier.owner.getBoundingBox(), RANGE)) return true;
        }
        return false;
    }

    private static void leave(Carrier carrier, ServerPlayer player) {
        Rider rider = carrier.riders.remove(player.getUUID());
        if (MANAGED.get(player.getUUID()) == carrier.owner) MANAGED.remove(player.getUUID());
        PolymerShiftDiagnostics.forgetCarrier(player.getUUID(), carrier.owner);
        if (rider == null) return;
        retire(player.getUUID(), rider);
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        if (!rider.copies.isEmpty()) packets.addAll(copyRestore(carrier, rider));
        if (rider.spawned) {
            packets.add(removal(rider.visible));
            if (rider.netShown) packets.add(removal(List.of(rider.net)));
            packets.add(team(carrier, List.of(), false));
        }
        for (BlockPos at : rider.ladders)
            packets.add(new ClientboundBlockUpdatePacket(at, player.level().getBlockState(at)));
        rider.ladders.clear();
        if (!packets.isEmpty()) sendOrdered(player, packets);
    }

    private static void sendOrdered(ServerPlayer player, List<Packet<? super ClientGamePacketListener>> packets) {
        if (!PolymerShiftPacer.sendInOrder(player.getUUID(), packets))
            NativePresentationPolicy.sendPresentation(player, new ClientboundBundlePacket(packets));
    }

    private static Vec3 residencyFeet(ServerPlayer player) {
        if (player.isPassenger()
                || player.getAbilities().flying
                || player.connection.awaitingPositionFromClient != null) return player.position();
        AbstractContraptionEntity owner = MANAGED.get(player.getUUID());
        Carrier deck = owner == null ? null : CARRIERS.get(owner);
        Rider rider = deck == null ? null : deck.riders.get(player.getUUID());
        if (deck == null
                || deck.current == null
                || owner.isRemoved()
                || owner.level() != player.level()
                || rider == null
                || !rider.aboard
                || !rider.carrying
                || rider.contact.airborne()
                || rider.queue.closed()
                || rider.stale >= STALE_HOLD
                || rider.lastLocal == null
                || rider.lastFeet == null
                || rider.lastFeet.distanceToSqr(player.position()) >= 1e-12) return player.position();
        Vec3 carried = deck.current.toWorld(rider.lastLocal);
        return validMotion(carried.subtract(player.position())) ? carried : player.position();
    }

    private static List<Packet<? super ClientGamePacketListener>> syncCells(
            Carrier carrier, Rider rider, ServerPlayer player) {
        double reach;
        Vec3 feet = residencyFeet(player);
        AABB playerBox = player.getBoundingBox().move(feet.subtract(player.position())), approach = playerBox;
        if (player.getVehicle() instanceof AbstractContraptionEntity) reach = SEATED_REACH;
        else {
            reach = 6;
            Vec3 relative = carrier.previous == null ? Vec3.ZERO : relativeMotion(carrier, player);
            double ticks = 3 + Math.min(6, Math.max(0, player.connection.latency()) / 50.0);
            if (validMotion(relative) && relative.lengthSqr() > 1e-8) {
                Vec3 ahead = relative.scale(-ticks);
                if (ahead.lengthSqr() > 10 * 10) ahead = ahead.normalize().scale(10);
                approach = playerBox.expandTowards(ahead);
            }
            if (rider.aboard && carrier.current != null && MANAGED.get(player.getUUID()) == carrier.owner) {
                double lead = 1 + Math.min(6, Math.max(0, player.connection.latency()) / 50.0);
                Vec3 walk = rider.walk.scale(lead);
                if (walk.lengthSqr() > MAX_LEAD * MAX_LEAD)
                    walk = walk.normalize().scale(MAX_LEAD);
                Vec3 local = carrier.current.toLocal(feet);
                Vec3 ahead = carrier.current.toWorld(local.add(walk)).subtract(feet);
                if (validMotion(ahead)) approach = approach.minmax(playerBox.move(ahead));
            }
        }
        List<Ranked> ranked = new ArrayList<>();
        for (Cell cell : carrier.cells) {
            Vec3 at = carrier.world(cell, carrier.current);
            AABB box = new AABB(
                    at.x - cell.s / 2, at.y, at.z - cell.s / 2, at.x + cell.s / 2, at.y + cell.s, at.z + cell.s / 2);
            boolean shown = rider.visible.contains(cell);
            double radius = reach + (shown ? 2 : 0), distanceSqr = PolymerShiftGeometry.distanceSqr(approach, box);
            if (distanceSqr > radius * radius) continue;
            double distance = Math.sqrt(distanceSqr);
            if (carrier.enclosed.contains(cell) && distance > (shown ? ENCLOSED_HOLD : ENCLOSED_KEEP)) continue;
            ranked.add(new Ranked(cell, distance - (shown ? RANK_HYSTERESIS : 0), at.distanceToSqr(feet)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::rank).thenComparingDouble(Ranked::feet));
        List<Cell> candidates = new ArrayList<>(ranked.size());
        for (Ranked entry : ranked) candidates.add(entry.cell());
        Set<Cell> desired = Collections.newSetFromMap(new IdentityHashMap<>());
        desired.addAll(candidates.subList(0, Math.min(PolymerShiftCellLimit.limit(player), candidates.size())));
        AABB guard = playerBox.deflate(1e-3);
        desired.removeIf(cell -> {
            if (rider.visible.contains(cell)) return false;
            Vec3 at = carrier.world(cell, carrier.current);
            AABB box = new AABB(
                    at.x - cell.s / 2, at.y, at.z - cell.s / 2, at.x + cell.s / 2, at.y + cell.s, at.z + cell.s / 2);
            return box.maxY > playerBox.minY + STEP_UP && box.intersects(guard);
        });
        List<Cell> removed =
                rider.visible.stream().filter(cell -> !desired.contains(cell)).toList();
        List<Cell> added = candidates.stream()
                .filter(cell -> desired.contains(cell) && !rider.visible.contains(cell))
                .toList();
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        PlayerTeam team = new PlayerTeam(new Scoreboard(), carrier.teamName);
        if (!removed.isEmpty()) {
            packets.add(removal(removed));
            for (Cell cell : removed) {
                packets.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                        team, cell.shulkerUuid.toString(), ClientboundSetPlayerTeamPacket.Action.REMOVE));
                rider.sent.remove(cell.vehicle);
                rider.sent.remove(cell.shulker);
            }
        }
        if (!added.isEmpty()) {
            packets.addAll(spawn(carrier, added));
            for (Cell cell : added)
                packets.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                        team, cell.shulkerUuid.toString(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
        rider.visible.clear();
        rider.visible.addAll(desired);
        return packets;
    }

    private static Vec3 relativeMotion(Carrier carrier, ServerPlayer player) {
        Vec3 deck = carrier.current.motionFrom(carrier.previous, player.position());
        AbstractContraptionEntity managed = MANAGED.get(player.getUUID());
        Carrier ride = managed == null ? null : CARRIERS.get(managed);
        if (ride == null || ride.previous == null || ride.current == null || ride.owner.level() != player.level())
            return deck;
        Vec3 carried = ride.current.motionFrom(ride.previous, player.position());
        return validMotion(carried) ? deck.subtract(carried) : deck;
    }

    private static List<Packet<? super ClientGamePacketListener>> spawn(Carrier carrier, Collection<Cell> cells) {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        for (Cell cell : cells) spawnAt(packets, cell, carrier.world(cell, carrier.current));
        return packets;
    }

    private static void spawnAt(List<Packet<? super ClientGamePacketListener>> packets, Cell cell, Vec3 at) {
        packets.add(new ClientboundAddEntityPacket(
                cell.vehicle, cell.vehicleUuid, at.x, at.y, at.z, 0, 0, EntityTypes.ITEM_DISPLAY, 0, Vec3.ZERO, 0));
        packets.add(new ClientboundSetEntityDataPacket(
                cell.vehicle, List.of(SynchedEntityData.DataValue.create(Display.DATA_VIEW_RANGE_ID, 0f))));
        packets.add(new ClientboundAddEntityPacket(
                cell.shulker, cell.shulkerUuid, at.x, at.y, at.z, 0, 0, EntityTypes.SHULKER, 0, Vec3.ZERO, 0));
        packets.add(new ClientboundSetEntityDataPacket(cell.shulker, PolymerCollisionShulkers.hiddenData()));
        packets.add(VirtualEntityUtils.createClientboundSetPassengersPacket(cell.vehicle, new int[] {cell.shulker}));
        AttributeInstance scale = new AttributeInstance(Attributes.SCALE, ignored -> {});
        scale.setBaseValue(cell.s);
        packets.add(new ClientboundUpdateAttributesPacket(cell.shulker, List.of(scale)));
    }

    static final double NET_SIZE = 1.5, NET_DROP = .06;

    private static final int NET_SAMPLES = 4;
    public static boolean net = true;

    private static List<Packet<? super ClientGamePacketListener>> syncNet(
            Carrier carrier,
            Rider rider,
            ServerPlayer player,
            Vec3 local,
            PolymerContraptionPhysics.Transform before,
            boolean wanted) {
        Vec3 from = null, to = null;
        if (net && wanted && carrier.previous != null) {
            double ticks = 1 + Math.min(6, Math.max(0, player.connection.latency()) / 50.0);
            Vec3 walk = rider.walk.scale(ticks);
            if (walk.lengthSqr() > MAX_LEAD * MAX_LEAD) walk = walk.normalize().scale(MAX_LEAD);
            Vec3 centre = local.add(walk);
            from = netAt(carrier, before, centre);
            to = from == null ? null : netAt(carrier, carrier.current, centre);
            if (to == null) from = null;
        }
        rider.netFrom = from;
        rider.netTo = to;
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        PlayerTeam team = new PlayerTeam(new Scoreboard(), carrier.teamName);
        if (from != null && !rider.netShown) {
            if (rider.net == null)
                rider.net = new Cell(
                        BlockPos.ZERO,
                        0,
                        0,
                        0,
                        NET_SIZE,
                        VirtualEntityUtils.requestEntityId(),
                        VirtualEntityUtils.requestEntityId(),
                        UUID.randomUUID(),
                        UUID.randomUUID());
            spawnAt(packets, rider.net, from);
            packets.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, rider.net.shulkerUuid.toString(), ClientboundSetPlayerTeamPacket.Action.ADD));
            rider.netShown = true;
        } else if (from == null && rider.netShown) {
            packets.add(removal(List.of(rider.net)));
            packets.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, rider.net.shulkerUuid.toString(), ClientboundSetPlayerTeamPacket.Action.REMOVE));
            rider.sent.remove(rider.net.vehicle);
            rider.sent.remove(rider.net.shulker);
            rider.netShown = false;
        }
        return packets;
    }

    private static Vec3 netAt(Carrier carrier, PolymerContraptionPhysics.Transform pose, Vec3 local) {
        Vec3 centre = pose.toWorld(local);
        List<Vec3> near = new ArrayList<>();
        List<Cell> nearCells = new ArrayList<>();
        for (Cell cell : carrier.surface) {
            Vec3 at = carrier.world(cell, pose);
            double reach = NET_SIZE / 2 + cell.s / 2, top = at.y + cell.s;
            if (Math.abs(at.x - centre.x) > reach
                    || Math.abs(at.z - centre.z) > reach
                    || top < centre.y - 1.2
                    || top > centre.y + .6) continue;
            near.add(at);
            nearCells.add(cell);
        }
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < NET_SAMPLES; i++)
            for (int j = 0; j < NET_SAMPLES; j++) {
                double x = centre.x + NET_SIZE * (i / (NET_SAMPLES - 1.0) - .5),
                        z = centre.z + NET_SIZE * (j / (NET_SAMPLES - 1.0) - .5);
                double floor = Double.NaN;
                for (int k = 0; k < near.size(); k++) {
                    Vec3 at = near.get(k);
                    double s = nearCells.get(k).s;
                    if (Math.abs(x - at.x) > s / 2 || Math.abs(z - at.z) > s / 2) continue;
                    if (Double.isNaN(floor) || at.y + s > floor) floor = at.y + s;
                }
                if (Double.isNaN(floor)) return null;
                lowest = Math.min(lowest, floor);
            }
        return new Vec3(centre.x, lowest - NET_DROP - NET_SIZE, centre.z);
    }

    private static ClientboundRemoveEntitiesPacket removal(Collection<Cell> cells) {
        return new ClientboundRemoveEntitiesPacket(cells.stream()
                .flatMapToInt(cell -> IntStream.of(cell.shulker, cell.vehicle))
                .toArray());
    }

    private static ClientboundSetPlayerTeamPacket team(Carrier carrier, Collection<Cell> cells, boolean add) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), carrier.teamName);
        team.setCollisionRule(Team.CollisionRule.NEVER);
        for (Cell cell : cells) team.getPlayers().add(cell.shulkerUuid.toString());
        return add
                ? ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true)
                : ClientboundSetPlayerTeamPacket.createRemovePacket(team);
    }

    private static void move(
            Rider rider,
            List<Packet<? super ClientGamePacketListener>> packets,
            int id,
            Vec3 at,
            float yaw,
            float pitch) {
        PositionMoveRotation next = new PositionMoveRotation(at, Vec3.ZERO, yaw, pitch);
        if (!next.equals(rider.sent.put(id, next))) packets.add(teleport(id, at, yaw, pitch));
    }

    private static ClientboundTeleportEntityPacket teleport(int id, Vec3 at, float yRot, float xRot) {
        return new ClientboundTeleportEntityPacket(
                id, new PositionMoveRotation(at, Vec3.ZERO, yRot, xRot), Set.of(), false);
    }

    private static Fit fit(
            ServerPlayer player, Collection<PolymerContraptionPhysics.Transform> poses, List<Cell> cells) {
        Vec3 feet = player.position();
        Fit best = null;
        int age = 0,
                limit = Math.max(1, Math.min(6, (int) Math.ceil(Math.max(0, player.connection.latency()) / 50.0) + 1));
        for (var pose : poses) {
            if (age > limit) break;
            Vec3 local = pose.toLocal(feet);
            for (Cell cell : cells) {
                double reach = cell.s / 2 + .28;
                if (Math.abs(local.x - cell.x) > reach || Math.abs(local.z - cell.z) > reach) continue;
                double gap = local.y - cell.top();
                if (best == null || Math.abs(gap) < Math.abs(best.gap)) best = new Fit(gap, age);
            }
            age++;
        }
        return best;
    }

    private static PolymerContraptionPhysics.Transform pose(Carrier carrier, int age) {
        int index = 0;
        for (var pose : carrier.poses) if (index++ == age) return pose;
        return carrier.poses.isEmpty() ? carrier.current : carrier.poses.peekLast();
    }

    private static PolymerContraptionPhysics.Transform reportedPose(Carrier carrier, ServerPlayer player, Fit fit) {
        var report = PolymerObserverFrameHistory.latest(
                player.getUUID(),
                carrier.owner.getUUID(),
                player.level().dimension(),
                player.level().getGameTime());
        if (report != null && report.feet().distanceToSqr(player.position()) < 1e-8)
            return report.frame().pose();
        return fit == null ? carrier.current : pose(carrier, fit.age);
    }

    private static double supportTop(
            Carrier carrier, ServerPlayer player, Vec3 local, PolymerContraptionPhysics.Transform pose) {
        double top = Double.NaN;
        Vec3 feet = pose.toWorld(local);
        AABB footprint = player.getBoundingBox().move(feet.subtract(player.position()));
        for (Cell cell : carrier.surface) {
            if (Math.abs(local.y - cell.top()) > 1.5) continue;
            Vec3 at = carrier.world(cell, pose);
            if (at.x + cell.s / 2 <= footprint.minX + 1e-7
                    || at.x - cell.s / 2 >= footprint.maxX - 1e-7
                    || at.z + cell.s / 2 <= footprint.minZ + 1e-7
                    || at.z - cell.s / 2 >= footprint.maxZ - 1e-7) continue;
            if (!clearAbove(carrier, player, cell, local, pose)) continue;
            double t = at.y + cell.s;
            if (Double.isNaN(top) || t > top) top = t;
        }
        return top;
    }

    private static String supportMiss(
            Carrier carrier, ServerPlayer player, Vec3 local, PolymerContraptionPhysics.Transform pose) {
        Rider rider = carrier.riders.get(player.getUUID());
        Vec3 feet = pose.toWorld(local);
        AABB footprint = player.getBoundingBox().move(feet.subtract(player.position()));
        StringBuilder out = new StringBuilder(String.format(
                Locale.ROOT,
                "local=(%.2f,%.2f,%.2f) feet=(%.2f,%.2f,%.2f) player=(%.2f,%.2f,%.2f) tilt=%.1f cells:",
                local.x,
                local.y,
                local.z,
                feet.x,
                feet.y,
                feet.z,
                player.getX(),
                player.getY(),
                player.getZ(),
                Math.toDegrees(Math.asin(Math.min(1, Math.abs(pose.toWorld(local.add(1, 0, 0)).y - feet.y))))));
        int listed = 0;
        for (Cell cell : carrier.surface) {
            if (Math.abs(local.x - cell.x) > 2 || Math.abs(local.z - cell.z) > 2) continue;
            Vec3 at = carrier.world(cell, pose);
            double ox = Math.min(at.x + cell.s / 2, footprint.maxX) - Math.max(at.x - cell.s / 2, footprint.minX),
                    oz = Math.min(at.z + cell.s / 2, footprint.maxZ) - Math.max(at.z - cell.s / 2, footprint.minZ);
            String why = Math.abs(local.y - cell.top()) > 1.5
                    ? "dy"
                    : ox <= 1e-7 || oz <= 1e-7
                            ? "foot"
                            : !clearAbove(carrier, player, cell, local, pose) ? "head" : "ok";
            out.append(String.format(
                    Locale.ROOT,
                    " [%s s=%.2f l=(%.2f,%.2f,%.2f) top=%.3f wtop=%.3f ox=%.2f oz=%.2f %s %s]",
                    cell.local().toShortString(),
                    cell.s,
                    cell.x,
                    cell.y,
                    cell.z,
                    cell.top(),
                    at.y + cell.s,
                    ox,
                    oz,
                    why,
                    rider != null && rider.visible.contains(cell) ? "sent" : "unsent"));
            if (++listed >= 24) {
                out.append(" ...");
                break;
            }
        }
        if (listed == 0) out.append(" none within 2");
        return out.toString();
    }

    private static double rescueTop(
            Carrier carrier,
            ServerPlayer player,
            Vec3 localFeet,
            PolymerContraptionPhysics.Transform before,
            boolean shifted) {
        double first = supportTop(carrier, player, localFeet, observerFrame(before, carrier.current, 1.0 / SUBSTEPS));
        if (shifted) return first;
        double end = supportTop(carrier, player, localFeet, carrier.current);
        return Double.isNaN(first) ? end : Double.isNaN(end) ? first : Math.max(first, end);
    }

    private static double recoveryTop(
            Carrier carrier,
            ServerPlayer player,
            Vec3 localFeet,
            PolymerContraptionPhysics.Transform before,
            boolean shifted,
            double lastSupport) {
        if (!Double.isFinite(lastSupport)) return Double.NaN;
        Cell floor = null;
        for (Cell cell : carrier.surface) {
            double reach = cell.s / 2 + .28, above = cell.top() - localFeet.y;
            if (Math.abs(localFeet.x - cell.x) > reach || Math.abs(localFeet.z - cell.z) > reach) continue;
            if (above < -.05 || above > FREEZE_DEPTH) continue;
            double distance = Math.abs(cell.top() - lastSupport);
            if (distance > STEP_UP + 1e-6) continue;
            if (floor != null
                    && (distance > Math.abs(floor.top() - lastSupport)
                            || distance == Math.abs(floor.top() - lastSupport) && cell.top() >= floor.top())) continue;
            Vec3 standing = new Vec3(localFeet.x, cell.top(), localFeet.z);
            if (!clearAbove(carrier, player, cell, standing, carrier.current)) continue;
            floor = cell;
        }
        if (floor == null) return Double.NaN;
        Vec3 on = new Vec3(localFeet.x, floor.top(), localFeet.z);
        double first = supportTop(carrier, player, on, observerFrame(before, carrier.current, 1.0 / SUBSTEPS));
        if (shifted) return first;
        double end = supportTop(carrier, player, on, carrier.current);
        return Double.isNaN(first) ? end : Double.isNaN(end) ? first : Math.max(first, end);
    }

    private static boolean clearAbove(
            Carrier carrier, ServerPlayer player, Cell support, Vec3 local, PolymerContraptionPhysics.Transform pose) {
        Rider rider = carrier.riders.get(player.getUUID());
        if (rider == null) return false;
        double top = carrier.world(support, pose).y + support.s;
        Vec3 feet = pose.toWorld(local);
        AABB standing = player.getBoundingBox()
                .move(feet.x - player.getX(), top + .001 - player.getY(), feet.z - player.getZ())
                .deflate(1e-5);
        for (Cell cell : rider.visible) {
            Vec3 at = carrier.world(cell, pose);
            if (standing.intersects(new AABB(
                    at.x - cell.s / 2, at.y, at.z - cell.s / 2, at.x + cell.s / 2, at.y + cell.s, at.z + cell.s / 2)))
                return false;
        }
        return true;
    }

    private static final double STEP_UP = .6;
    private static final double MAX_LEAD = .6, MAYBE_HOVER = .07, MAX_EXTRA_LIFT = .3;

    private static Vec3 liftedMotion(
            Carrier carrier, ServerPlayer player, Rider rider, Vec3 motion, Vec3 local, double noise) {
        if (carrier.previous == null) return motion;
        PolymerContraptionPhysics.Transform before = carrier.previous;
        Vec3 feet = before.toWorld(local);
        double ticks = 1 + Math.min(6, Math.max(0, player.connection.latency()) / 50.0);
        Vec3 walk = rider.walk.scale(ticks);
        if (walk.lengthSqr() > MAX_LEAD * MAX_LEAD) walk = walk.normalize().scale(MAX_LEAD);
        Vec3 ahead = before.toWorld(local.add(walk)).subtract(feet);
        double half = player.getBbWidth() / 2;
        AABB footprint = new AABB(feet.x - half, feet.y, feet.z - half, feet.x + half, feet.y, feet.z + half)
                .expandTowards(ahead.x, 0, ahead.z);
        double sure = motion.y, maybe = motion.y;
        for (Carrier deck : supportDecks(carrier, player)) {
            for (Cell cell : deck.surface) {
                Vec3 at = deck.world(cell, deck.previous);
                double s = cell.s / 2;
                if (at.x + s <= footprint.minX
                        || at.x - s >= footprint.maxX
                        || at.z + s <= footprint.minZ
                        || at.z - s >= footprint.maxZ) continue;
                double top = at.y + cell.s, rise = deck.world(cell, deck.current).y - at.y, clearance = feet.y - top;
                if (clearance > 1) continue;
                if (clearance >= -.02) sure = Math.max(sure, rise - Math.max(0, clearance - noise));
                else if (clearance >= -STEP_UP) maybe = Math.max(maybe, rise);
            }
        }
        double lift = Math.max(sure, Math.min(maybe, sure + MAYBE_HOVER));
        return new Vec3(motion.x, Math.min(lift, motion.y + MAX_EXTRA_LIFT) + LIFT_MARGIN, motion.z);
    }

    static final double WALL_MARGIN = .002;
    private static final double MAX_WALL_CLEARANCE = .25;
    private static final double ENCLOSED_KEEP = .75, ENCLOSED_HOLD = 1.25;
    private static final double RANK_HYSTERESIS = .25;

    private record Ranked(Cell cell, double rank, double feet) {}

    private static Vec3 clearedMotion(Carrier carrier, ServerPlayer player, Rider rider, Vec3 motion, Vec3 local) {
        if (carrier.previous == null || !validMotion(motion)) return motion;
        Vec3 feet = carrier.previous.toWorld(local);
        AABB body = player.getBoundingBox().move(feet.subtract(player.position()));
        double ticks = 1 + Math.min(6, Math.max(0, player.connection.latency()) / 50.0);
        Vec3 lead = rider.walk.scale(ticks);
        if (lead.lengthSqr() > MAX_LEAD * MAX_LEAD) lead = lead.normalize().scale(MAX_LEAD);
        Vec3 walk = carrier.previous.toWorld(local.add(lead)).subtract(feet);
        if (!validMotion(walk)) walk = Vec3.ZERO;
        AABB end = body.move(motion);
        AABB reach = body.minmax(end).inflate(1 + Math.abs(walk.x) + Math.abs(walk.z));
        double plusX = 0, minusX = 0, plusZ = 0, minusZ = 0;
        List<AABB> walls = new ArrayList<>();
        double cut = Math.sqrt(reach.getXsize() * reach.getXsize()
                                + reach.getYsize() * reach.getYsize()
                                + reach.getZsize() * reach.getZsize())
                        / 2
                + 1;
        for (Carrier deck : supportDecks(carrier, player)) {
            Rider seen = deck == carrier ? rider : deck.riders.get(rider.playerId);
            if (seen == null || deck.previous == null || deck.current == null) continue;
            Vec3 middle = deck.previous.toLocal(reach.getCenter());
            for (Cell cell : seen.visible) {
                if (Math.abs(cell.x - middle.x) > cut
                        || Math.abs(cell.y + cell.s / 2 - middle.y) > cut
                        || Math.abs(cell.z - middle.z) > cut) continue;
                Vec3 from = deck.world(cell, deck.previous), to = deck.world(cell, deck.current);
                double s = cell.s / 2;
                AABB start = new AABB(from.x - s, from.y, from.z - s, from.x + s, from.y + cell.s, from.z + s);
                if (!start.intersects(reach)) continue;
                AABB moved = start.move(to.subtract(from));
                if (moved.maxY <= end.minY + 1e-6 || moved.minY >= end.maxY - 1e-6) continue;
                if (moved.maxY <= end.minY + STEP_UP + 1e-6 && deck.surfaceSet.contains(cell)) continue;
                double gapX = Math.max(start.minX - body.maxX, body.minX - start.maxX);
                double gapZ = Math.max(start.minZ - body.maxZ, body.minZ - start.maxZ);
                if (gapX < -1e-7 && gapZ < -1e-7) continue;
                walls.add(moved);
                if (gapX >= gapZ) {
                    if (moved.minZ >= Math.max(end.maxZ, end.maxZ + walk.z) - 1e-7
                            || moved.maxZ <= Math.min(end.minZ, end.minZ + walk.z) + 1e-7) continue;
                    boolean plusSide = start.minX >= body.maxX - 1e-7;
                    double gap = Math.max(0, gapX - Math.max(0, plusSide ? walk.x : -walk.x));
                    double cellMotion = to.x - from.x;
                    double approach = plusSide ? motion.x - cellMotion : cellMotion - motion.x;
                    if (approach <= 1e-9) continue;
                    double need = approach - gap + WALL_MARGIN;
                    if (need <= 0) continue;
                    if (plusSide) minusX = Math.max(minusX, need);
                    else plusX = Math.max(plusX, need);
                } else {
                    if (moved.minX >= Math.max(end.maxX, end.maxX + walk.x) - 1e-7
                            || moved.maxX <= Math.min(end.minX, end.minX + walk.x) + 1e-7) continue;
                    boolean plusSide = start.minZ >= body.maxZ - 1e-7;
                    double gap = Math.max(0, gapZ - Math.max(0, plusSide ? walk.z : -walk.z));
                    double cellMotion = to.z - from.z;
                    double approach = plusSide ? motion.z - cellMotion : cellMotion - motion.z;
                    if (approach <= 1e-9) continue;
                    double need = approach - gap + WALL_MARGIN;
                    if (need <= 0) continue;
                    if (plusSide) minusZ = Math.max(minusZ, need);
                    else plusZ = Math.max(plusZ, need);
                }
            }
        }
        double cx = plusX > 0 && minusX > 0 ? 0 : plusX - minusX;
        double cz = plusZ > 0 && minusZ > 0 ? 0 : plusZ - minusZ;
        if (cx == 0 && cz == 0) return motion;
        cx = Math.max(-MAX_WALL_CLEARANCE, Math.min(MAX_WALL_CLEARANCE, cx));
        cz = Math.max(-MAX_WALL_CLEARANCE, Math.min(MAX_WALL_CLEARANCE, cz));
        AABB corrected = end.move(cx, 0, cz).deflate(1e-6);
        for (AABB wall : walls) if (wall.intersects(corrected)) return motion;
        return motion.add(cx, 0, cz);
    }

    private static List<Carrier> supportDecks(Carrier carrier, ServerPlayer player) {
        List<Carrier> decks = new ArrayList<>(2);
        decks.add(carrier);
        for (Carrier other : CARRIERS.values()) {
            if (other == carrier
                    || other.previous == null
                    || other.current == null
                    || other.owner.level() != player.level()) continue;
            Rider rider = other.riders.get(player.getUUID());
            if (rider != null && rider.aboard) decks.add(other);
        }
        return decks;
    }

    private static boolean exactReport(Carrier carrier, ServerPlayer player) {
        var report = PolymerObserverFrameHistory.latest(
                player.getUUID(),
                carrier.owner.getUUID(),
                player.level().dimension(),
                player.level().getGameTime());
        return report != null && report.feet().distanceToSqr(player.position()) < 1e-8;
    }

    static final BlockState CLIMB = Blocks.VINE.defaultBlockState();

    private static boolean climbable(Carrier carrier, Vec3 feet) {
        Vec3 local = carrier.current.toLocal(feet);
        for (double y : new double[] {.01, 1})
            if (carrier.states
                    .getOrDefault(BlockPos.containing(local.x, local.y + y, local.z), Blocks.AIR.defaultBlockState())
                    .is(BlockTags.CLIMBABLE)) return true;
        return false;
    }

    private static List<Packet<? super ClientGamePacketListener>> syncLadders(
            Carrier carrier, Rider rider, ServerPlayer player, Vec3 motion) {
        Set<BlockPos> want = new HashSet<>();
        if (rider.climbing) {
            Vec3 local = carrier.current.toLocal(player.position());
            Vec3 ahead = player.position().add(validMotion(motion) ? motion : Vec3.ZERO);
            for (int k = -1; k <= 2; k++) {
                if (!carrier.states
                        .getOrDefault(
                                BlockPos.containing(local.x, local.y + k + .01, local.z),
                                Blocks.AIR.defaultBlockState())
                        .is(BlockTags.CLIMBABLE)) continue;
                for (Vec3 feet : List.of(player.position(), ahead)) {
                    BlockPos at = BlockPos.containing(feet.x, feet.y + k + .01, feet.z);
                    if (player.level().getBlockState(at).isAir()) want.add(at.immutable());
                }
            }
        }
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        for (BlockPos at : rider.ladders)
            if (!want.contains(at))
                packets.add(new ClientboundBlockUpdatePacket(at, player.level().getBlockState(at)));
        for (BlockPos at : want)
            if (!rider.ladders.contains(at)) packets.add(new ClientboundBlockUpdatePacket(at, CLIMB));
        rider.ladders.clear();
        rider.ladders.addAll(want);
        return packets;
    }

    private static double yawDelta(Carrier carrier) {
        if (carrier.previous == null) return 0;
        Vec3 a = carrier.previous.x(), b = carrier.current.x();
        double d = Math.toDegrees(Math.atan2(b.z, b.x) - Math.atan2(a.z, a.x));
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    private static ClientboundPlayerRotationPacket seatTurn(double yaw) {
        return new ClientboundPlayerRotationPacket((float) yaw, true, 0, true);
    }

    private static Vec3 cabinPush(
            Carrier carrier,
            ServerPlayer player,
            Rider rider,
            PolymerContraptionPhysics.Transform frame,
            Vec3 previousLocal,
            double support) {
        Vec3 target = player.position();
        if (previousLocal == null) previousLocal = frame.toLocal(target);
        if (!Double.isFinite(support)) support = previousLocal.y;
        Vec3 previous = frame.toWorld(previousLocal);
        if (!validMotion(target.subtract(previous)) || target.distanceToSqr(previous) > 4) return null;
        AABB volume = player.getBoundingBox().minmax(player.getBoundingBox().move(previous.subtract(target)));
        if (rider.safeLocal != null) {
            Vec3 safe = frame.toWorld(rider.safeLocal);
            if (safe.distanceToSqr(target) <= 4)
                volume = volume.minmax(player.getBoundingBox().move(safe.subtract(target)));
        }
        volume = volume.inflate(STEP_UP + .01);
        List<AABB> boxes = new ArrayList<>(), floors = new ArrayList<>();
        Vec3 centre = frame.toLocal(volume.getCenter());
        cabinCells(carrier, frame::toWorld, centre, volume, support, rider.visible, boxes, floors);
        Vec3 supportPoint = carrier.current.toWorld(new Vec3(previousLocal.x, support, previousLocal.z));
        if (pushAllCells)
            for (Carrier other : supportDecks(carrier, player)) {
                if (other == carrier) continue;
                Rider seen = other.riders.get(rider.playerId);
                cabinCells(
                        other,
                        local -> frame.toWorld(carrier.current.toLocal(other.current.toWorld(local))),
                        other.current.toLocal(carrier.current.toWorld(centre)),
                        volume,
                        other.current.toLocal(supportPoint).y,
                        seen == null ? Set.of() : seen.visible,
                        boxes,
                        floors);
            }
        if (pushAllCells && square(frame))
            for (AABB local : carrier.uncovered) {
                if (local.getYsize() < .5) continue;
                AABB box = PolymerShiftGeometry.bounds(local, frame);
                if (box.intersects(volume)) boxes.add(box);
            }
        Vec3 start = cabinPoint(player, previous, boxes, floors);
        if (start == null && rider.safeLocal != null) {
            Vec3 safe = frame.toWorld(rider.safeLocal);
            if (safe.distanceToSqr(target) <= 4) start = cabinPoint(player, safe, boxes, floors);
        }
        if (start == null) return null;
        Vec3 end = cabinWalk(player, start, target, boxes, floors);
        if (end.distanceToSqr(target) > 1e-10) {
            Vec3 slide = cabinWalk(player, end, new Vec3(target.x, end.y, end.z), boxes, floors);
            end = cabinWalk(player, slide, new Vec3(slide.x, end.y, target.z), boxes, floors);
        }
        Vec3 correction = new Vec3(end.x - target.x, 0, end.z - target.z);
        if (correction.lengthSqr() > 1e-10) {
            Vec3 allowed = ContraptionCollider.collide(correction, player);
            if (!validMotion(allowed) || cabinPoint(player, target.add(allowed), boxes, floors) == null) return null;
            correction = new Vec3(allowed.x, 0, allowed.z);
        }
        Vec3 clear = cabinPoint(player, target.add(correction), boxes, floors);
        if (clear != null && Double.isFinite(floorUnder(player.getBoundingBox().move(clear.subtract(target)), floors)))
            rider.safeLocal = frame.toLocal(clear);
        return correction.lengthSqr() < 1e-10 ? null : correction;
    }

    private static void cabinCells(
            Carrier deck,
            UnaryOperator<Vec3> place,
            Vec3 centre,
            AABB volume,
            double support,
            Set<Cell> shown,
            List<AABB> boxes,
            List<AABB> floors) {
        double radius = Math.sqrt(volume.getXsize() * volume.getXsize()
                                + volume.getYsize() * volume.getYsize()
                                + volume.getZsize() * volume.getZsize())
                        / 2
                + 1;
        for (Cell cell : pushAllCells ? deck.cells : shown) {
            if (Math.abs(cell.x - centre.x) > radius
                    || Math.abs(cell.y + cell.s / 2 - centre.y) > radius
                    || Math.abs(cell.z - centre.z) > radius) continue;
            Vec3 middle = place.apply(new Vec3(cell.x, cell.y + cell.s / 2, cell.z));
            double bottom = middle.y - cell.s / 2;
            AABB box = new AABB(
                    middle.x - cell.s / 2,
                    bottom,
                    middle.z - cell.s / 2,
                    middle.x + cell.s / 2,
                    bottom + cell.s,
                    middle.z + cell.s / 2);
            if (!box.intersects(volume)) continue;
            boxes.add(box);
            if (shown.contains(cell)
                    && deck.surfaceSet.contains(cell)
                    && Math.abs(cell.top() - support) <= STEP_UP + 1e-6) floors.add(box);
        }
    }

    public static boolean pushAllCells = true;

    private static boolean square(PolymerContraptionPhysics.Transform frame) {
        for (Vec3 axis : new Vec3[] {frame.x(), frame.y(), frame.z()})
            if (Math.max(Math.abs(axis.x), Math.max(Math.abs(axis.y), Math.abs(axis.z))) < 1 - 1e-6) return false;
        return true;
    }

    private static Vec3 cabinWalk(ServerPlayer player, Vec3 start, Vec3 target, List<AABB> boxes, List<AABB> floors) {
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(target) / .1));
        Vec3 clear = start;
        for (int k = 1; k <= steps; k++) {
            Vec3 point = lerp(start, target, k / (double) steps);
            Vec3 next = cabinPoint(player, point, boxes, floors);
            if (next != null) {
                clear = next;
                continue;
            }
            Vec3 blocked = point;
            for (int n = 0; n < 12; n++) {
                Vec3 middle = lerp(clear, blocked, .5), checked = cabinPoint(player, middle, boxes, floors);
                if (checked == null) blocked = middle;
                else clear = checked;
            }
            return clear;
        }
        return clear;
    }

    private static Vec3 cabinPoint(ServerPlayer player, Vec3 point, List<AABB> boxes, List<AABB> floors) {
        AABB body = player.getBoundingBox().move(point.subtract(player.position()));
        double floor = floorUnder(body, floors);
        if (Double.isFinite(floor) && Math.abs(body.minY - floor) <= 1.5) {
            point = new Vec3(point.x, floor + .001, point.z);
            body = body.move(0, point.y - body.minY, 0);
        }
        body = body.deflate(1e-7);
        for (AABB box : boxes) if (body.intersects(box)) return null;
        return point;
    }

    private static double floorUnder(AABB body, List<AABB> floors) {
        double top = Double.NaN;
        for (AABB box : floors) {
            if (box.maxX <= body.minX + 1e-7
                    || box.minX >= body.maxX - 1e-7
                    || box.maxZ <= body.minZ + 1e-7
                    || box.minZ >= body.maxZ - 1e-7) continue;
            if (Double.isNaN(top) || box.maxY > top) top = box.maxY;
        }
        return top;
    }

    private static double supportFloor(
            Carrier carrier, Rider rider, AABB body, PolymerContraptionPhysics.Transform frame, double level) {
        List<AABB> floors = new ArrayList<>();
        for (Cell cell : carrier.surface) {
            if (!rider.visible.contains(cell) || Math.abs(cell.top() - level) > STEP_UP + 1e-6) continue;
            Vec3 at = carrier.world(cell, frame);
            floors.add(new AABB(
                    at.x - cell.s / 2, at.y, at.z - cell.s / 2, at.x + cell.s / 2, at.y + cell.s, at.z + cell.s / 2));
        }
        return floorUnder(body, floors);
    }

    private static boolean protectedByOtherCarrier(Carrier carrier, ServerPlayer player) {
        AbstractContraptionEntity managed = MANAGED.get(player.getUUID());
        if (managed == null
                || managed == carrier.owner
                || managed.isRemoved()
                || managed.level() != player.level()
                || player.isPassenger()
                || player.getAbilities().flying
                || player.isSpectator()) return false;
        Carrier owner = CARRIERS.get(managed);
        Rider rider = owner == null ? null : owner.riders.get(player.getUUID());
        return rider != null && rider.aboard && !rider.queue.closed();
    }

    private static Vec3 pushMotion(Carrier carrier, ServerPlayer player, Rider rider, boolean aboard) {
        AABB now = player.getBoundingBox();
        Vec3 previousFeet = rider.lastFeet == null ? player.position() : rider.lastFeet;
        if (previousFeet.distanceToSqr(player.position()) > MAX_CONTINUOUS_MOTION * MAX_CONTINUOUS_MOTION)
            previousFeet = player.position();
        AABB before = now.move(previousFeet.subtract(player.position()));
        Vec3 correction = Vec3.ZERO;
        double localFeet = aboard ? carrier.current.toLocal(player.position()).y : Double.NaN;
        Vec3 deck = rider != null && rider.contact.airborne() && !rider.carrying
                ? carrier.current.motionFrom(carrier.previous, player.position())
                : relativeMotion(carrier, player);
        double limit = (validMotion(deck) ? Math.sqrt(deck.lengthSqr()) : 0) + .3;
        for (int index = 0; index < carrier.boxes.size(); index++) {
            AABB box = carrier.boxes.get(index);
            if (aboard && box.maxY < localFeet + .6) continue;
            if (!carrier.sweptBoxes
                    .get(index)
                    .intersects(before.move(correction).minmax(now.move(correction)))) continue;
            Vec3 push = PolymerShiftGeometry.sweep(
                    before.move(correction), now.move(correction), box, carrier.previous, carrier.current);
            if (aboard && push.y > 0 && Math.abs(push.y) > Math.abs(push.x) + Math.abs(push.z)) continue;
            correction = correction.add(push);
        }
        if (!validMotion(correction) || correction.lengthSqr() < 1e-10) return null;
        if (correction.lengthSqr() > limit * limit)
            correction = correction.normalize().scale(limit);
        Vec3 allowed = ContraptionCollider.collide(correction, player);
        if (!validMotion(allowed) || allowed.lengthSqr() < 1e-10) return null;
        if (aboard && allowed.y < 0) {
            double floor = supportFloor(
                    carrier,
                    rider,
                    player.getBoundingBox(),
                    carrier.current,
                    carrier.current.toLocal(player.position()).y);
            if (Double.isFinite(floor))
                allowed = new Vec3(allowed.x, Math.max(allowed.y, Math.min(0, floor - player.getY())), allowed.z);
        }
        return applyPush(carrier, player, allowed);
    }

    private static Vec3 applyPush(Carrier carrier, ServerPlayer player, Vec3 allowed) {
        if (allowed == null || !validMotion(allowed) || allowed.lengthSqr() < 1e-10) return null;
        player.setPos(player.position().add(allowed));
        player.connection.resetPosition();
        player.connection.resetFlyingTicks();
        if (allowed.y > 0) player.resetFallDistance();
        carrier.owner.registerColliding(player);
        return allowed;
    }

    private static final Set<Relative> SHIFT = EnumSet.of(
            Relative.X,
            Relative.Y,
            Relative.Z,
            Relative.Y_ROT,
            Relative.X_ROT,
            Relative.DELTA_X,
            Relative.DELTA_Y,
            Relative.DELTA_Z);

    private static final Set<Relative> RESCUE_FLAGS =
            EnumSet.of(Relative.X, Relative.Z, Relative.Y_ROT, Relative.X_ROT, Relative.DELTA_X, Relative.DELTA_Z);

    static ClientboundPlayerPositionPacket shiftPacket(Vec3 motion, float yaw) {
        return new ClientboundPlayerPositionPacket(
                nextId(), new PositionMoveRotation(motion, Vec3.ZERO, yaw, 0), SHIFT);
    }

    static ClientboundPlayerPositionPacket rescuePacket(double y) {
        return new ClientboundPlayerPositionPacket(
                nextId(), new PositionMoveRotation(new Vec3(0, y, 0), Vec3.ZERO, 0, 0), RESCUE_FLAGS);
    }

    private static synchronized int nextId() {
        return -1 - (shiftIds++ & 0x3fffffff);
    }

    private static boolean warnedMissingBedrockTransport;

    private static List<Packet<? super ClientGamePacketListener>> bedrockPackets(
            List<Packet<? super ClientGamePacketListener>> packets) {
        boolean ready = PolymerBedrockCarryProtocol.ready();
        List<Packet<? super ClientGamePacketListener>> result = new ArrayList<>(packets.size());
        for (Packet<? super ClientGamePacketListener> packet : packets) {
            if (!(packet instanceof ClientboundPlayerPositionPacket position)) {
                result.add(packet);
            } else if (ready) {
                result.add(new ClientboundPlayerPositionPacket(
                        PolymerBedrockCarryProtocol.mark(position.id()), position.change(), position.relatives()));
            } else if (!warnedMissingBedrockTransport) {
                warnedMissingBedrockTransport = true;
                Create.LOGGER.warn(
                        "Bedrock contraption carry requires the matching Geyser Fabric patch; player shifts are suppressed to preserve input");
            }
        }
        return result;
    }

    private static void rawSend(
            UUID playerId, Connection connection, List<Packet<? super ClientGamePacketListener>> packets) {
        try {
            if (PolymerBedrockCarriers.isBedrock(playerId)) packets = bedrockPackets(packets);
            if (connection.isConnected() && !packets.isEmpty()) {
                // The vanilla client disconnects on a bundle over 4096 packets. The paced lane merges every deck's
                // slot (initial cell and copy spawns included) into one list, so split it into consecutive, ordered
                // bundles.
                boolean flush = !PolymerContraptionVisuals.deferringFlush();
                for (int from = 0; from < packets.size(); from += MAX_BUNDLE) {
                    int to = Math.min(packets.size(), from + MAX_BUNDLE);
                    NativePresentationPolicy.sendPresentation(
                            connection,
                            new ClientboundBundlePacket(packets.subList(from, to)),
                            null,
                            flush && to == packets.size());
                }
                var spacing = PolymerShiftPacer.spacingObserver;
                if (spacing != null) spacing.accept(playerId);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double f) {
        return new Vec3(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f);
    }

    private static void substeps(
            Carrier carrier,
            Rider rider,
            Connection connection,
            Vec3 motion,
            double yaw,
            boolean shift,
            boolean moving,
            Packet<ClientGamePacketListener> rescue,
            boolean moveCells,
            boolean seatClock,
            Vec3 push,
            double seatYaw) {
        List<DisplayElement> displays = PolymerContraptionVisuals.carriedDisplays(carrier.owner);
        PolymerContraptionPhysics.Transform before = carrier.previous == null ? carrier.current : carrier.previous;
        int count = moving && !seatClock ? SUBSTEPS : 1;
        Quaternionf qBefore = deckRotation(before), qCurrent = deckRotation(carrier.current);
        float[][] angles = new float[count][];
        for (int k = 1; k <= count; k++)
            angles[k - 1] = entityAngles(qBefore.slerp(qCurrent, moving ? k / (float) count : 1f, new Quaternionf()));
        Quaternionf inverse = new Quaternionf()
                .rotationYXZ(
                        (float) Math.toRadians(-angles[count - 1][0]), (float) Math.toRadians(angles[count - 1][1]), 0)
                .conjugate();
        List<Packet<? super ClientGamePacketListener>> residuals = new ArrayList<>();
        for (DisplayElement display : displays) {
            Integer copy = rider.copies.get(display);
            if (copy == null) continue;
            CopyPose holder = holderPose(carrier, display);
            Vector3f t = inverse.transform(holder.translation());
            Quaternionf q = new Quaternionf(inverse).mul(holder.rotation());
            CopyPose previous = rider.copyResidual.put(display, new CopyPose(new Vector3f(t), new Quaternionf(q)));
            Vector3f lt = t;
            Quaternionf lq = q;
            if (!seatClock && previous != null && previous.translation.distanceSquared(t) < .25f) {
                lt = new Vector3f(t).mul(2).sub(previous.translation);
                lq = new Quaternionf(q)
                        .mul(new Quaternionf(previous.rotation).conjugate().mul(q))
                        .normalize();
            }
            CopyPose last = rider.copyPose.get(display);
            if (last == null || !last.translation.equals(lt, 1e-4f) || !last.rotation.equals(lq, 1e-5f)) {
                rider.copyPose.put(display, new CopyPose(lt, lq));
                residuals.add(new ClientboundSetEntityDataPacket(
                        copy,
                        List.of(
                                SynchedEntityData.DataValue.create(Display.DATA_TRANSLATION_ID, lt),
                                SynchedEntityData.DataValue.create(Display.DATA_LEFT_ROTATION_ID, lq),
                                SynchedEntityData.DataValue.create(
                                        DisplayAccessor.getDATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID(),
                                        0))));
            }
        }
        List<List<Packet<? super ClientGamePacketListener>>> bundles = new ArrayList<>();
        boolean wholePush = rider.contactPush || rescue != null;
        for (int k = 1; k <= count; k++) {
            double f = moving ? k / (double) count : 1;
            List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
            if (moveCells)
                for (Cell cell : rider.visible) {
                    Vec3 at = lerp(carrier.world(cell, before), carrier.world(cell, carrier.current), f);
                    move(rider, packets, cell.vehicle, at, 0, 0);
                    move(rider, packets, cell.shulker, at, 0, 0);
                }
            if (moveCells && rider.netShown && rider.netTo != null) {
                Vec3 at = lerp(rider.netFrom, rider.netTo, f);
                move(rider, packets, rider.net.vehicle, at, 0, 0);
                move(rider, packets, rider.net.shulker, at, 0, 0);
            }
            for (DisplayElement display : displays) {
                Integer copy = rider.copies.get(display);
                if (copy == null) continue;
                Vec3 to = carrier.copyAt(), from = to.subtract(carrier.current.motionFrom(before, to));
                move(rider, packets, copy, moving ? lerp(from, to, f) : to, angles[k - 1][0], angles[k - 1][1]);
            }
            if (k == 1) packets.addAll(residuals);
            if (shift) {
                Vec3 correction = motion.scale(1.0 / SUBSTEPS);
                if (push != null) correction = correction.add(push.scale(!wholePush ? 1.0 / count : k == 1 ? 1 : 0));
                var packet = shiftPacket(correction, (float) (yaw / SUBSTEPS));
                PolymerObserverFrameHistory.publish(
                        rider.playerId,
                        packet.id(),
                        carrier.owner.getUUID(),
                        carrier.owner.level().dimension(),
                        observerFrame(before, carrier.current, f),
                        carrier.owner.level().getGameTime());
                packets.add(packet);
            }
            if (!shift && push != null && (!wholePush || k == 1))
                packets.add(shiftPacket(push.scale(wholePush ? 1 : 1.0 / count), 0));
            if (k == 1 && rescue != null) {
                if (rescue instanceof ClientboundPlayerPositionPacket position)
                    PolymerObserverFrameHistory.publish(
                            rider.playerId,
                            position.id(),
                            carrier.owner.getUUID(),
                            carrier.owner.level().dimension(),
                            observerFrame(before, carrier.current, f),
                            carrier.owner.level().getGameTime());
                packets.add(rescue);
            }
            if (seatYaw != 0 && !(seatClock && count == 1)) packets.add(seatTurn(seatYaw / count));
            PolymerShiftDiagnostics.record(rider.playerId, carrier.owner.level().getGameTime(), packets);
            bundles.add(packets);
        }
        ServerPlayer seated = seatClock && carrier.owner.level().getServer() != null
                ? carrier.owner.level().getServer().getPlayerList().getPlayer(rider.playerId)
                : null;
        String stepRoute;
        if (seated != null && bundles.size() == 1 && PolymerShiftPacer.idle(rider.playerId)) {
            if (seated.getVehicle() == carrier.owner) bundles.get(0).addAll(0, takeSeatMoves(rider.playerId));
            if (!bundles.get(0).isEmpty())
                NativePresentationPolicy.sendPresentation(seated, new ClientboundBundlePacket(bundles.get(0)));
            seatedSend(rider.playerId, SEATED_DIRECT);
            stepRoute = "direct";
        } else {
            rider.queue.submit(bundles, STEP_MS, (task, delay) -> STEPS.schedule(task, delay, TimeUnit.MILLISECONDS));
            if (seatClock) seatedSend(rider.playerId, seatYaw != 0 ? SEATED_TURN : SEATED_LANE);
            stepRoute = "lane";
        }
        if (seated != null && count == 1 && seatYaw != 0) {
            float part = (float) (seatYaw / SUBSTEPS);
            var seatedConnection = seated.connection;
            seatedConnection.send(seatTurn(part));
            for (int k = 1; k < SUBSTEPS; k++)
                STEPS.schedule(
                        () -> {
                            if (!seated.hasDisconnected()) seatedConnection.send(seatTurn(part));
                        },
                        k * STEP_MS,
                        TimeUnit.MILLISECONDS);
        }
        if (seated != null)
            seatTrace(
                    seated,
                    String.format(
                            Locale.ROOT,
                            "  steps carriage=%d route=%s count=%d seatYaw=%.4f",
                            carrier.owner.getId(),
                            stepRoute,
                            bundles.size(),
                            seatYaw));
    }

    private static PolymerContraptionPhysics.Transform observerFrame(
            PolymerContraptionPhysics.Transform before, PolymerContraptionPhysics.Transform current, double fraction) {
        if (fraction >= 1) return current;
        return new PolymerContraptionPhysics.Transform(
                lerp(before.origin(), current.origin(), fraction),
                lerp(before.x(), current.x(), fraction),
                lerp(before.y(), current.y(), fraction),
                lerp(before.z(), current.z(), fraction));
    }

    private static Quaternionf deckRotation(PolymerContraptionPhysics.Transform transform) {
        Matrix3f m = new Matrix3f()
                .setColumn(0, new Vector3f((float) transform.x().x, (float) transform.x().y, (float) transform.x().z))
                .setColumn(1, new Vector3f((float) transform.y().x, (float) transform.y().y, (float) transform.y().z))
                .setColumn(2, new Vector3f((float) transform.z().x, (float) transform.z().y, (float) transform.z().z));
        return m.getNormalizedRotation(new Quaternionf());
    }

    private static float[] entityAngles(Quaternionf q) {
        Vector3f euler = q.getEulerAnglesYXZ(new Vector3f());
        return new float[] {(float) -Math.toDegrees(euler.y), (float) Math.toDegrees(euler.x)};
    }

    private static List<Packet<? super ClientGamePacketListener>> copySync(
            Carrier carrier,
            Rider rider,
            PolymerContraptionPhysics.Transform before,
            ServerPlayer viewer,
            boolean seatClock) {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        List<DisplayElement> allDisplays = PolymerContraptionVisuals.carriedDisplays(carrier.owner);
        List<DisplayElement> displays = allDisplays.stream()
                .filter(display -> viewer != null
                        && carrier.displayAt(display)
                                        .add(new Vec3(
                                                display.getTranslation().x(),
                                                display.getTranslation().y(),
                                                display.getTranslation().z()))
                                        .distanceToSqr(viewer.position())
                                <= (rider.copies.containsKey(display) ? 16 * 16 : 12 * 12))
                .toList();
        Set<DisplayElement> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(displays);
        List<Integer> stale = new ArrayList<>();
        for (var iterator = rider.copies.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (live.contains(entry.getKey())) continue;
            stale.add(entry.getValue());
            rider.sent.remove(entry.getValue());
            if (allDisplays.contains(entry.getKey())) restoreOriginal(carrier, rider, entry.getKey(), viewer, packets);
            else unhide(rider.playerId, entry.getKey().getEntityId());
            rider.copyData.remove(entry.getKey());
            rider.copyPose.remove(entry.getKey());
            rider.copyResidual.remove(entry.getKey());
            rider.copyVisibility.remove(entry.getKey());
            iterator.remove();
        }
        if (!stale.isEmpty())
            packets.add(new ClientboundRemoveEntitiesPacket(
                    stale.stream().mapToInt(Integer::intValue).toArray()));
        boolean refresh = rider.copyTick++ % 20 == 0;
        int durationId = Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID.id(),
                transformId = Display.DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID.id();
        int translationId = Display.DATA_TRANSLATION_ID.id(), rotationId = Display.DATA_LEFT_ROTATION_ID.id();
        long tick = carrier.owner.level().getGameTime();
        Set<DisplayElement> owned = PolymerContraptionVisuals.ownedDisplays(carrier.owner);
        List<Integer> removeOriginals = new ArrayList<>();
        for (DisplayElement display : displays) {
            Integer copy = rider.copies.get(display);
            boolean fresh = copy == null;
            if (fresh) {
                EntityType<?> type = display instanceof ItemDisplayElement
                        ? EntityTypes.ITEM_DISPLAY
                        : display instanceof BlockDisplayElement ? EntityTypes.BLOCK_DISPLAY : EntityTypes.TEXT_DISPLAY;
                copy = VirtualEntityUtils.requestEntityId();
                rider.copies.put(display, copy);
                rider.copyVisibility.put(display, new PolymerShiftVisibility(tick));
                Vec3 to = carrier.copyAt(), from = to.subtract(carrier.current.motionFrom(before, to));
                float[] spawnAngles = entityAngles(deckRotation(before));
                packets.add(new ClientboundAddEntityPacket(
                        copy,
                        UUID.randomUUID(),
                        from.x,
                        from.y,
                        from.z,
                        spawnAngles[1],
                        spawnAngles[0],
                        type,
                        0,
                        Vec3.ZERO,
                        0));
            }
            PolymerShiftVisibility visibility = rider.copyVisibility.get(display);
            boolean activate = visibility.activate(tick);
            List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
            for (var value : display.getSyncedData().getChangedEntries()) {
                int id = value.id();
                if (id != durationId
                        && id != transformId
                        && id != translationId
                        && id != rotationId
                        && id != Display.DATA_VIEW_RANGE_ID.id()) {
                    if (value.value() instanceof ItemStack stack
                            && id == ItemDisplayAccessor.getDATA_ITEM_STACK_ID().id())
                        data.add(SynchedEntityData.DataValue.create(
                                ItemDisplayAccessor.getDATA_ITEM_STACK_ID(), stack.copy()));
                    else data.add(value);
                }
            }
            data.add(SynchedEntityData.DataValue.create(
                    Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, seatClock ? 1 : 0));
            data.add(SynchedEntityData.DataValue.create(Display.DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, 1));
            data.add(SynchedEntityData.DataValue.create(
                    Display.DATA_VIEW_RANGE_ID, visibility.active() ? display.getViewRange() : 0f));
            if (fresh) {
                CopyPose initial = copyPose(carrier, display, carrier.current);
                rider.copyPose.put(display, initial);
                List<SynchedEntityData.DataValue<?>> initialized = new ArrayList<>(data);
                initialized.removeIf(value -> value.id()
                                == DisplayAccessor.getDATA_SCALE_ID().id()
                        || value.id()
                                == DisplayAccessor.getDATA_RIGHT_ROTATION_ID().id());
                initialized.add(SynchedEntityData.DataValue.create(Display.DATA_TRANSLATION_ID, initial.translation));
                initialized.add(SynchedEntityData.DataValue.create(Display.DATA_LEFT_ROTATION_ID, initial.rotation));
                initialized.add(SynchedEntityData.DataValue.create(
                        DisplayAccessor.getDATA_SCALE_ID(), new Vector3f(display.getScale())));
                initialized.add(SynchedEntityData.DataValue.create(
                        DisplayAccessor.getDATA_RIGHT_ROTATION_ID(), new Quaternionf(display.getRightRotation())));
                packets.add(new ClientboundSetEntityDataPacket(copy, initialized));
                rider.copyData.put(display, data);
            } else {
                List<SynchedEntityData.DataValue<?>> changed = changedData(data, rider.copyData.get(display));
                if (!changed.isEmpty()) {
                    rider.copyData.put(display, data);
                    packets.add(new ClientboundSetEntityDataPacket(copy, changed));
                }
            }
            if (visibility.active() && (activate || refresh)) {
                if (owned.contains(display)) {
                    hide(rider.playerId, display.getEntityId());
                    removeOriginals.add(display.getEntityId());
                } else
                    packets.add(new ClientboundSetEntityDataPacket(
                            display.getEntityId(),
                            List.of(SynchedEntityData.DataValue.create(Display.DATA_VIEW_RANGE_ID, 0f))));
            }
        }
        if (!removeOriginals.isEmpty())
            packets.add(new ClientboundRemoveEntitiesPacket(
                    removeOriginals.stream().mapToInt(Integer::intValue).toArray()));
        return packets;
    }

    private static CopyPose copyPose(
            Carrier carrier, DisplayElement display, PolymerContraptionPhysics.Transform pose) {
        float[] angles = entityAngles(deckRotation(pose));
        Quaternionf inverse = new Quaternionf()
                .rotationYXZ((float) Math.toRadians(-angles[0]), (float) Math.toRadians(angles[1]), 0)
                .conjugate();
        CopyPose holder = holderPose(carrier, display);
        return new CopyPose(inverse.transform(holder.translation()), new Quaternionf(inverse).mul(holder.rotation()));
    }

    private static CopyPose holderPose(Carrier carrier, DisplayElement display) {
        var lead = PolymerContraptionVisuals.leadOf(carrier.owner, display);
        Quaternionf unlead = new Quaternionf(lead.rotation()).conjugate();
        Vec3 anchor = carrier.displayAt(display).subtract(carrier.copyAt());
        Vector3f translation = unlead.transform(new Vector3f(display.getTranslation())
                        .sub((float) lead.shift().x, (float) lead.shift().y, (float) lead.shift().z))
                .add((float) anchor.x, (float) anchor.y, (float) anchor.z);
        return new CopyPose(translation, unlead.mul(display.getLeftRotation()));
    }

    private static List<SynchedEntityData.DataValue<?>> changedData(
            List<SynchedEntityData.DataValue<?>> next, List<SynchedEntityData.DataValue<?>> last) {
        if (last == null) return next;
        Map<Integer, SynchedEntityData.DataValue<?>> before = new HashMap<>();
        for (var value : last) before.put(value.id(), value);
        List<SynchedEntityData.DataValue<?>> changed = new ArrayList<>();
        for (var value : next) {
            var old = before.get(value.id());
            boolean same = old != null
                    && (value.value() instanceof ItemStack s && old.value() instanceof ItemStack t
                            ? ItemStack.matches(s, t)
                            : Objects.equals(value.value(), old.value()));
            if (!same) changed.add(value);
        }
        return changed;
    }

    private static void restoreOriginal(
            Carrier carrier,
            Rider rider,
            DisplayElement display,
            ServerPlayer viewer,
            List<Packet<? super ClientGamePacketListener>> packets) {
        if (unhide(rider.playerId, display.getEntityId())) {
            if (viewer != null && PolymerContraptionVisuals.watches(carrier.owner, viewer))
                display.startWatching(viewer, packets::add);
        } else
            packets.add(new ClientboundSetEntityDataPacket(
                    display.getEntityId(),
                    List.of(SynchedEntityData.DataValue.create(Display.DATA_VIEW_RANGE_ID, display.getViewRange()))));
    }

    private static List<Packet<? super ClientGamePacketListener>> copyRestore(Carrier carrier, Rider rider) {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        var server = carrier.owner.level().getServer();
        ServerPlayer viewer = server == null
                ? null
                : NativePresentationPolicy.presentationPlayer(server.getPlayerList(), rider.playerId);
        for (DisplayElement display : PolymerContraptionVisuals.carriedDisplays(carrier.owner))
            if (rider.copies.containsKey(display)) restoreOriginal(carrier, rider, display, viewer, packets);
        unhideAll(rider.playerId, rider);
        if (!rider.copies.isEmpty())
            packets.add(new ClientboundRemoveEntitiesPacket(
                    rider.copies.values().stream().mapToInt(Integer::intValue).toArray()));
        for (Integer id : rider.copies.values()) rider.sent.remove(id);
        rider.copies.clear();
        rider.copyData.clear();
        rider.copyPose.clear();
        rider.copyResidual.clear();
        rider.copyVisibility.clear();
        return packets;
    }

    public static String copyReport(ServerPlayer player) {
        StringBuilder out = new StringBuilder();
        for (Carrier carrier : CARRIERS.values()) {
            if (carrier.owner.level() != player.level() || carrier.owner.distanceTo(player) > 48) continue;
            Rider rider = carrier.riders.get(player.getUUID());
            out.append("carriage ")
                    .append(carrier.owner.getId())
                    .append(rider == null ? " (no rider state)" : " copies=" + rider.copies.size())
                    .append('\n');
            for (DisplayElement display : PolymerContraptionPassengers.carriedDisplays(carrier.owner)) {
                Integer copy = rider == null ? null : rider.copies.get(display);
                PolymerShiftVisibility visibility = rider == null ? null : rider.copyVisibility.get(display);
                CopyPose sent = rider == null ? null : rider.copyPose.get(display);
                String item = display instanceof ItemDisplayElement item0
                        ? String.valueOf(item0.getItem().get(DataComponents.ITEM_MODEL))
                        : display.getClass().getSimpleName();
                out.append(String.format(
                        Locale.ROOT,
                        "  part %s original=%d watched=%s hidden=%s viewRange=%.2f t=%s L=%s R=%s | copy=%s active=%s sentT=%s sentL=%s%n",
                        item,
                        display.getEntityId(),
                        display.getHolder() != null
                                && display.getHolder().getWatchingPlayers().contains(player.connection),
                        hidden(player, display.getEntityId()),
                        display.getViewRange(),
                        display.getTranslation(),
                        display.getLeftRotation(),
                        display.getRightRotation(),
                        copy,
                        visibility == null ? "-" : visibility.active(),
                        sent == null ? "-" : sent.translation,
                        sent == null ? "-" : sent.rotation));
            }
        }
        return out.length() == 0 ? "no carriage within 48 blocks" : out.toString();
    }
}
