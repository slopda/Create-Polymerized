package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.ControlledContraptionEntity;
import com.zurrtum.create.content.contraptions.OrientedContraptionEntity;
import com.zurrtum.create.content.contraptions.actors.seat.SeatBlock;
import com.zurrtum.create.content.contraptions.bearing.BearingContraption;
import com.zurrtum.create.content.contraptions.gantry.GantryContraption;
import com.zurrtum.create.content.contraptions.gantry.GantryContraptionEntity;
import com.zurrtum.create.content.contraptions.mounted.MountedContraption;
import com.zurrtum.create.content.trains.entity.CarriageContraption;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.infrastructure.player.FakePlayerEntity;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.ApplyEntityImpulse;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PolymerContraptionFloors {
    private static final int PER_ASSEMBLY_LIMIT = 128;
    private static final int TOTAL_LIMIT = 256;
    private static final Map<AbstractContraptionEntity, Floor> FLOORS = new IdentityHashMap<>();
    private static final Set<AbstractContraptionEntity> DISABLED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<UUID, Rider> RIDERS = new HashMap<>();
    private static final Map<AbstractContraptionEntity, PolymerContraptionPhysics.Transform> TRANSFORMS =
            new IdentityHashMap<>();

    private static final class Rider {
        Floor floor;
        Vec3 previous = Vec3.ZERO;
        boolean jumpHeld;
        long jumpUntil;
    }

    private record Contact(Floor floor, NativeCell cell, double gap) {}

    public static List<BlockPos> plan(AbstractContraptionEntity entity) {
        if (entity.isRemoved() || !(entity.level() instanceof ServerLevel) || entity.getContraption() == null)
            return List.of();
        boolean bearing = entity instanceof ControlledContraptionEntity
                && !entity.isPassenger()
                && entity.getContraption() instanceof BearingContraption b
                && b.getFacing().getAxis() == Direction.Axis.Y;
        boolean cart = entity instanceof OrientedContraptionEntity
                && entity.getContraption() instanceof MountedContraption
                && entity.getVehicle() instanceof AbstractMinecart;
        boolean train = entity instanceof CarriageContraptionEntity carriage
                && carriage.getCarriage() != null
                && entity.getContraption() instanceof CarriageContraption;
        boolean gantry =
                entity instanceof GantryContraptionEntity && entity.getContraption() instanceof GantryContraption;
        if (!bearing && !cart && !train && !gantry) return List.of();
        var transform = PolymerContraptionPhysics.Transform.sample(entity);
        if (Math.abs(transform.y().y - 1) > 1e-6) return List.of();
        var contraption = entity.getContraption();
        var world = contraption.getContraptionWorld();
        List<BlockPos> positions = new ArrayList<>();
        for (var entry : contraption.getBlocks().entrySet()) {
            BlockPos local = entry.getKey();
            if (!entry.getValue().state().isCollisionShapeFullBlock(world, local)) continue;
            boolean clear = true;
            for (int y = 1; y <= 2; y++) {
                BlockPos above = local.above(y);
                var info = contraption.getBlocks().get(above);
                if (info != null
                        && !info.state().getCollisionShape(world, above).isEmpty()) {
                    if (train && y == 1 && info.state().getBlock() instanceof SeatBlock) continue;
                    clear = false;
                    break;
                }
            }
            if (clear) positions.add(local.immutable());
            if (positions.size() > PER_ASSEMBLY_LIMIT) return List.of();
        }
        positions.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getZ()));
        return positions;
    }

    static void prepare(AbstractContraptionEntity entity, Collection<ServerGamePacketListenerImpl> watchers) {
        var current = PolymerContraptionPhysics.Transform.sample(entity);
        var previous = TRANSFORMS.put(entity, current);
        if (DISABLED.contains(entity) || watchers.isEmpty()) {
            retire(entity);
            return;
        }
        List<BlockPos> positions = plan(entity);
        Floor old = FLOORS.get(entity);
        if (!(entity instanceof GantryContraptionEntity)
                && previous != null
                && Math.abs(current.origin().y - previous.origin().y) > 1e-5) positions = List.of();
        if (positions.isEmpty()) {
            retire(entity);
            return;
        }
        if (old != null && !old.positions.equals(positions)) {
            retire(entity);
            old = null;
        }
        if (old != null && old.cells.stream().anyMatch(cell -> cell.anchor.isRemoved() || cell.shulker.isRemoved())) {
            retire(entity);
            old = null;
        }
        if (old == null) {
            int used = FLOORS.values().stream()
                            .mapToInt(floor -> floor.cells.size())
                            .sum()
                    + PolymerContraptionLab.nativeHelperCount(entity.level().getServer());
            if (used + positions.size() > TOTAL_LIMIT) return;
            Floor created = new Floor(entity, positions);
            try {
                created.spawn();
            } catch (RuntimeException error) {
                created.destroy();
                Create.LOGGER.warn("Cannot create native contraption floor; retaining virtual collision", error);
                return;
            }
            FLOORS.put(entity, created);
            PolymerContraptionPhysics.remove(entity);
        }
    }

    static boolean owns(AbstractContraptionEntity entity) {
        return FLOORS.containsKey(entity);
    }

    static boolean covers(AbstractContraptionEntity entity, BlockPos local) {
        Floor floor = FLOORS.get(entity);
        return floor != null && floor.covered.contains(local);
    }

    public static boolean enable(AbstractContraptionEntity entity) {
        if (plan(entity).isEmpty()) return false;
        DISABLED.remove(entity);
        return true;
    }

    public static void disable(AbstractContraptionEntity entity) {
        DISABLED.add(entity);
        retire(entity);
    }

    public static boolean trialDisabled(AbstractContraptionEntity entity) {
        return DISABLED.contains(entity);
    }

    public static void restoreTrial(AbstractContraptionEntity entity, boolean disabled) {
        if (disabled) DISABLED.add(entity);
        else DISABLED.remove(entity);
        TRANSFORMS.remove(entity);
    }

    public static void forgetPlayer(ServerPlayer player) {
        RIDERS.remove(player.getUUID());
    }

    public static Floor floor(AbstractContraptionEntity entity) {
        return FLOORS.get(entity);
    }

    static void update(PolymerContraptionPhysics.Surface surface) {
        Floor floor = FLOORS.get(surface.entity());
        if (floor == null) return;
        floor.surface = surface;
        if (surface.entity().level().tickRateManager().isEntityFrozen(surface.entity())) return;
        for (NativeCell cell : floor.cells) {
            Vec3 bottom = surface.current()
                    .toWorld(new Vec3(cell.local.getX() + .5, cell.local.getY(), cell.local.getZ() + .5));
            cell.anchor.setPos(bottom);
            cell.anchor.positionRider(cell.shulker);
            cell.anchor.needsSync = true;
        }
    }

    static void tick(MinecraftServer server) {
        RIDERS.keySet().removeIf(id -> NativePresentationPolicy.presentationPlayer(server.getPlayerList(), id) == null);
        for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
            if (PolymerContraptionLab.controls(player)
                    || PolymerShiftCarry.manages(player)
                    || player instanceof FakePlayerEntity
                    || !player.isAlive()
                    || player.isSpectator()
                    || player.isPassenger()
                    || player.getAbilities().flying
                    || player.connection.awaitingPositionFromClient != null
                    || player.level().tickRateManager().isEntityFrozen(player)) {
                RIDERS.remove(player.getUUID());
                continue;
            }
            Rider rider = RIDERS.get(player.getUUID());
            Contact contact = contact(player, rider == null ? null : rider.floor);
            if (contact == null) {
                RIDERS.remove(player.getUUID());
                continue;
            }
            if (rider == null || rider.floor != contact.floor) {
                rider = new Rider();
                rider.floor = contact.floor;
                RIDERS.put(player.getUUID(), rider);
            }
            var surface = contact.floor.surface;
            Vec3 carry = surface.current().motionFrom(surface.previous(), player.position());
            if (!Double.isFinite(carry.lengthSqr()) || carry.lengthSqr() > .64) {
                RIDERS.remove(player.getUUID());
                continue;
            }
            if (contact.floor.owner instanceof GantryContraptionEntity
                    && Math.abs(surface.current().origin().y
                                    - surface.previous().origin().y)
                            > 1e-5) {
                vertical(player, rider, contact, carry.y);
                continue;
            }
            if (player.getLastClientInput().jump() || contact.gap > .16 || Math.abs(carry.y) > 1e-5) continue;
            horizontal(player, rider.previous, carry);
            rider.previous = carry;
            contact.floor.owner.registerColliding(player);
        }
    }

    private static void horizontal(ServerPlayer player, Vec3 previous, Vec3 carry) {
        double drag = player.onGround() ? .6f * .91f : .91f;
        Vec3 known = player.getKnownMovement();
        player.setDeltaMovement(new Vec3(
                known.x * drag - previous.x * drag, player.getDeltaMovement().y, known.z * drag - previous.z * drag));
        Vec3 look = player.getLookAngle();
        Vec3 local = new Vec3(
                carry.dot(look.addLocalCoordinates(new Vec3(1, 0, 0))),
                carry.dot(look.addLocalCoordinates(new Vec3(0, 1, 0))),
                carry.dot(look.addLocalCoordinates(new Vec3(0, 0, 1))));
        new ApplyEntityImpulse(local, new Vec3(1, 1, 1), LevelBasedValue.constant(1))
                .apply((ServerLevel) player.level(), 1, null, player, player.position());
        NativePresentationPolicy.sendPresentation(player, new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    private static void vertical(ServerPlayer player, Rider rider, Contact contact, double dy) {
        boolean jump = player.getLastClientInput().jump();
        long tick = player.level().getGameTime();
        try {
            double previousGap = contact.gap + dy;
            boolean currentTop = contact.gap >= -.06 && contact.gap <= .10;
            boolean previousTop = previousGap >= -.06 && previousGap <= .10;
            double knownY = player.getKnownMovement().y;
            if ((!currentTop && !previousTop) || knownY > .12 && knownY - dy > .12) return;
            if (jump && !rider.jumpHeld) {
                rider.jumpUntil = tick + 8;
                NativePresentationPolicy.sendPresentation(
                        player, PolymerContraptionPhysics.carryPacket(player.getId(), Vec3.ZERO, 0, true, true));
                return;
            }
            if (tick < rider.jumpUntil) return;
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(new Vec3(velocity.x, dy, velocity.z));
            player.setOnGround(true);
            NativePresentationPolicy.sendPresentation(
                    player, PolymerContraptionPhysics.carryPacket(player.getId(), Vec3.ZERO, dy, false, true));
            player.applyPostImpulseGraceTime(10);
            player.connection.resetFlyingTicks();
            contact.floor.owner.registerColliding(player);
            rider.previous = Vec3.ZERO;
        } finally {
            rider.jumpHeld = jump;
        }
    }

    static Contact contact(ServerPlayer player, Floor preferred) {
        Contact best = null;
        for (Floor floor : FLOORS.values()) {
            var surface = floor.surface;
            if (surface == null
                    || surface.previous() == null
                    || floor.owner.isRemoved()
                    || floor.owner.level() != player.level()
                    || !surface.viewers().contains(player.connection)
                    || floor.owner.level().tickRateManager().isEntityFrozen(floor.owner)) continue;
            for (NativeCell cell : floor.cells) {
                AABB box = cell.shulker.getBoundingBox();
                double gap = player.getY() - box.maxY;
                AABB feet = player.getBoundingBox();
                double penetration = .35;
                if (floor.owner instanceof GantryContraptionEntity)
                    penetration = Math.max(
                            penetration,
                            Math.abs(surface.current().origin().y
                                            - surface.previous().origin().y)
                                    + .06);
                if (gap < -penetration
                        || gap > .6
                        || feet.maxX <= box.minX
                        || feet.minX >= box.maxX
                        || feet.maxZ <= box.minZ
                        || feet.minZ >= box.maxZ) continue;
                if (best == null
                        || Math.abs(gap) < Math.abs(best.gap) - 1e-6
                        || Math.abs(Math.abs(gap) - Math.abs(best.gap)) < 1e-6
                                && floor == preferred
                                && best.floor != preferred) best = new Contact(floor, cell, gap);
            }
        }
        return best;
    }

    static void remove(AbstractContraptionEntity entity) {
        retire(entity);
        DISABLED.remove(entity);
        TRANSFORMS.remove(entity);
    }

    private static void retire(AbstractContraptionEntity entity) {
        Floor floor = FLOORS.remove(entity);
        if (floor != null) {
            floor.destroy();
            RIDERS.values().removeIf(rider -> rider.floor == floor);
        }
    }

    static void clear() {
        for (Floor floor : FLOORS.values()) floor.destroy();
        FLOORS.clear();
        DISABLED.clear();
        RIDERS.clear();
        TRANSFORMS.clear();
    }

    public static final class Floor {
        public final AbstractContraptionEntity owner;
        public final List<BlockPos> positions;
        final Set<BlockPos> covered;
        public final List<NativeCell> cells = new ArrayList<>();
        PolymerContraptionPhysics.Surface surface;

        Floor(AbstractContraptionEntity owner, List<BlockPos> positions) {
            this.owner = owner;
            this.positions = List.copyOf(positions);
            this.covered = new HashSet<>(positions);
        }

        private void spawn() {
            if (!(owner.level() instanceof ServerLevel world))
                throw new IllegalStateException("Native floor requires a server level");
            for (BlockPos local : positions) {
                Anchor anchor = new Anchor(world);
                anchor.setPos(owner.toGlobalVector(new Vec3(local.getX() + .5, local.getY(), local.getZ() + .5), 1));
                Proxy shulker = new Proxy(world, owner, local);
                cells.add(new NativeCell(local, anchor, shulker));
                if (!world.addFreshEntity(anchor) || !shulker.startRiding(anchor, true, false))
                    throw new IllegalStateException("Cannot create native floor vehicle");
                anchor.positionRider(shulker);
                if (!world.addFreshEntity(shulker))
                    throw new IllegalStateException("Cannot create native floor passenger");
            }
        }

        private void destroy() {
            for (NativeCell cell : cells) {
                cell.shulker.discard();
                cell.anchor.discard();
            }
            cells.clear();
        }
    }

    private record NativeCell(BlockPos local, Anchor anchor, Proxy shulker) {}

    private static final class Anchor extends Display.ItemDisplay {
        Anchor(ServerLevel world) {
            super(EntityTypes.ITEM_DISPLAY, world);
            setPosRotInterpolationDuration(1);
            setNoGravity(true);
            setInvulnerable(true);
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }
    }

    private static final class Proxy extends Shulker {
        final AbstractContraptionEntity owner;
        final BlockPos local;

        Proxy(ServerLevel world, AbstractContraptionEntity owner, BlockPos local) {
            super(EntityTypes.SHULKER, world);
            this.owner = owner;
            this.local = local;
            setNoAi(true);
            setNoGravity(true);
            setInvulnerable(true);
            setPersistenceRequired();
            setInvisible(true);
            addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }

        @Override
        public InteractionResult interact(Player player, InteractionHand hand, Vec3 hit) {
            if (player instanceof ServerPlayer serverPlayer && (owns(owner) || PolymerContraptionLab.active(owner))) {
                PolymerContraptionVisuals.handleInteraction(owner, local, serverPlayer, hand, hit);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
    }
}
