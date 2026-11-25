package com.zurrtum.create.infrastructure.polymer.interaction;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import com.zurrtum.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement.ElevatorFloorSelection;
import com.zurrtum.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.zurrtum.create.content.contraptions.elevator.ElevatorContraption;
import com.zurrtum.create.infrastructure.packet.c2s.ElevatorTargetFloorPacket;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionVisuals;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PolymerElevatorControls {
    private static final Map<UUID, PlayerState> PLAYERS = new HashMap<>();
    private static final Map<UUID, Integer> ACKS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_ACK = new AtomicInteger(0x43724500);
    private static final long IDLE = TimeUnit.MINUTES.toNanos(2);
    private static final long ACK_TIMEOUT = TimeUnit.SECONDS.toNanos(5);
    private static final double HIT_RADIUS = .508f * .85;

    private record Key(UUID entity, BlockPos pos) {}

    private record Aim(AbstractContraptionEntity entity, BlockHitResult hit) {
        Key key() {
            return new Key(entity.getUUID(), hit.getBlockPos().immutable());
        }
    }

    private static final class Selection {
        final AbstractContraptionEntity entity;
        final ElevatorContraption contraption;
        final BlockPos pos;
        final ElevatorFloorSelection floor = new ElevatorFloorSelection();

        @Nullable
        List<?> names;

        @Nullable
        Integer lastTarget;

        long touched;

        Selection(Aim aim) {
            entity = aim.entity();
            contraption = (ElevatorContraption) entity.getContraption();
            pos = aim.hit().getBlockPos().immutable();
        }

        void refresh() {
            Integer target = contraption.getCurrentTargetY(entity.level());
            if (target == null) target = (int) Math.round(entity.getY() + contraption.getContactYOffset());
            if (names != contraption.namesList || !Objects.equals(lastTarget, target)) {
                Integer wanted = names == null || target != null && !Objects.equals(lastTarget, target)
                        ? target
                        : names.isEmpty() ? null : floor.currentTargetY;
                if (wanted != null) {
                    for (int i = 0; i < contraption.namesList.size(); i++) {
                        if (contraption.namesList.get(i).getFirst().equals(wanted)) {
                            floor.currentIndex = i;
                            break;
                        }
                    }
                }
                names = contraption.namesList;
            }
            lastTarget = target;
            ContraptionControlsMovement.tickFloorSelection(floor, contraption);
            if (!contraption.namesList.isEmpty())
                floor.targetYEqualsSelection = Objects.equals(target, floor.currentTargetY);
            touched = System.nanoTime();
        }

        boolean valid() {
            var actor = contraption.getActorAt(pos);
            return !entity.isRemoved()
                    && entity.getContraption() == contraption
                    && contraption.getBlocks().containsKey(pos)
                    && actor != null
                    && actor.right != null
                    && contraption.getBlocks().get(pos).state().is(AllBlocks.CONTRAPTION_CONTROLS);
        }
    }

    private static final class Epoch {
        final Key key;
        final int original;
        int reported;

        @Nullable
        Integer ack;

        long deadline;
        boolean active = true;
        boolean dirty;

        Epoch(Key key, int original) {
            this.key = key;
            this.original = original;
            reported = original;
        }
    }

    private static final class PlayerState {
        final LinkedHashMap<Key, Selection> drafts = new LinkedHashMap<>(16, .75f, true);

        @Nullable
        Epoch epoch;

        Selection selection(Aim aim) {
            Selection result = drafts.computeIfAbsent(aim.key(), unused -> new Selection(aim));
            if (!result.valid() || result.entity != aim.entity()) {
                result = new Selection(aim);
                drafts.put(aim.key(), result);
            }
            while (drafts.size() > 16) drafts.remove(drafts.firstEntry().getKey());
            result.refresh();
            return result;
        }
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PLAYERS.remove(handler.player.getUUID());
            ACKS.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PLAYERS.clear();
            ACKS.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                tick(player);
        });
    }

    public static boolean onSlotChange(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= 9) return false;
        PlayerState state = PLAYERS.get(player.getUUID());
        Aim aim = eligible(player) ? aim(player) : null;
        if (aim == null || !hotspot(aim)) {
            if (state != null) abandon(player, state);
            return false;
        }
        if (state == null) {
            state = new PlayerState();
            PLAYERS.put(player.getUUID(), state);
        }
        Epoch epoch = state.epoch;
        if (epoch != null
                && (!epoch.active
                        || !epoch.key.equals(aim.key())
                        || player.getInventory().getSelectedSlot() != epoch.original)) {
            abandon(player, state);
            if (state.epoch != null) return false;
            epoch = null;
        }
        int previous = epoch == null ? player.getInventory().getSelectedSlot() : epoch.reported;
        int step = step(previous, slot);
        if (step == 0) return epoch != null;
        if (Math.abs(step) != 1) {
            abandon(player, state);
            return false;
        }
        if (epoch == null) state.epoch = epoch = new Epoch(aim.key(), previous);
        Selection selection = state.selection(aim);
        selection.floor.currentIndex -= step;
        selection.refresh();
        epoch.reported = slot;
        if (epoch.ack == null) epoch.dirty = true;
        player.resetLastActionTime();
        return true;
    }

    public static int step(int previous, int slot) {
        int delta = Math.floorMod(slot - previous, 9);
        return delta > 4 ? delta - 9 : delta;
    }

    public static boolean onPong(ServerGamePacketListenerImpl listener, ServerboundPongPacket packet) {
        UUID id = listener.player.getUUID();
        if (!Objects.equals(ACKS.get(id), packet.getId())) return false;
        PacketUtils.ensureRunningOnSameThread(packet, listener, listener.player.level());
        if (!ACKS.remove(id, packet.getId())) return false;
        PlayerState state = PLAYERS.get(id);
        if (state == null || state.epoch == null || !Objects.equals(state.epoch.ack, packet.getId())) return true;
        Epoch epoch = state.epoch;
        epoch.ack = null;
        epoch.dirty = false;
        epoch.reported = epoch.original;
        if (!epoch.active
                || !eligible(listener.player)
                || listener.player.getInventory().getSelectedSlot() != epoch.original) {
            restore(listener.player, listener.player.getInventory().getSelectedSlot());
            state.epoch = null;
        }
        return true;
    }

    public static void interacted(AbstractContraptionEntity entity, BlockPos pos, ServerPlayer player) {
        if (!(entity.getContraption() instanceof ElevatorContraption) || !eligible(player)) return;
        Aim aim = aim(player);
        if (aim == null || aim.entity() != entity || !aim.hit().getBlockPos().equals(pos)) return;
        PlayerState state = PLAYERS.computeIfAbsent(player.getUUID(), unused -> new PlayerState());
        Selection selection = state.selection(aim);
        if (!selection.contraption.namesList.isEmpty() && !selection.floor.targetYEqualsSelection) {
            AllHandle.onElevatorTargetFloor(
                    player.connection, new ElevatorTargetFloorPacket(entity, selection.floor.currentTargetY));
        }
        selection.refresh();
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && player.containerMenu == player.inventoryMenu
                && player.connection.awaitingPositionFromClient == null;
    }

    private static void tick(ServerPlayer player) {
        PlayerState state = PLAYERS.get(player.getUUID());
        Aim aim = eligible(player) ? aim(player) : null;
        if (aim != null && hotspot(aim)) {
            if (state == null) {
                state = new PlayerState();
                PLAYERS.put(player.getUUID(), state);
            }
            state.selection(aim);
        }
        if (state == null) return;
        long now = System.nanoTime();
        state.drafts
                .values()
                .removeIf(draft ->
                        !draft.valid() || draft.entity.level() != player.level() || now - draft.touched > IDLE);
        Epoch epoch = state.epoch;
        if (epoch != null) {
            if (aim == null
                    || !hotspot(aim)
                    || !epoch.key.equals(aim.key())
                    || player.getInventory().getSelectedSlot() != epoch.original) abandon(player, state);
            if (epoch.ack != null && now >= epoch.deadline) {
                ACKS.remove(player.getUUID(), epoch.ack);
                restore(player, player.getInventory().getSelectedSlot());
                state.epoch = null;
            } else if (epoch.active && epoch.dirty && epoch.ack == null) {
                epoch.ack = NEXT_ACK.getAndIncrement();
                epoch.deadline = now + ACK_TIMEOUT;
                epoch.dirty = false;
                ACKS.put(player.getUUID(), epoch.ack);
                restore(player, epoch.original);
                NativePresentationPolicy.sendPresentation(player, new ClientboundPingPacket(epoch.ack));
            }
        }
        if (state.drafts.isEmpty() && state.epoch == null) PLAYERS.remove(player.getUUID());
    }

    private static void restore(ServerPlayer player, int slot) {
        NativePresentationPolicy.sendPresentation(player, new ClientboundSetHeldSlotPacket(slot));
    }

    public static @Nullable ElevatorFloorSelection latestSelection(AbstractContraptionEntity entity, BlockPos pos) {
        Key key = new Key(entity.getUUID(), pos);
        long now = System.nanoTime();
        Selection best = null;
        for (PlayerState state : PLAYERS.values()) {
            Selection selection = state.drafts.get(key);
            if (selection != null
                    && selection.entity == entity
                    && now - selection.touched < IDLE
                    && (best == null || selection.touched > best.touched)) best = selection;
        }
        return best == null ? null : best.floor;
    }

    public static void removed(AbstractContraptionEntity entity) {
        UUID id = entity.getUUID();
        for (PlayerState state : PLAYERS.values()) {
            state.drafts.keySet().removeIf(key -> key.entity().equals(id));
            if (state.epoch != null && state.epoch.key.entity().equals(id)) state.epoch.active = false;
        }
    }

    private static void abandon(ServerPlayer player, PlayerState state) {
        Epoch epoch = state.epoch;
        if (epoch == null) return;
        epoch.active = false;
        if (epoch.ack == null) {
            if (epoch.dirty) restore(player, player.getInventory().getSelectedSlot());
            state.epoch = null;
        }
    }

    static boolean hotspot(Aim aim) {
        BlockState state = aim.entity()
                .getContraption()
                .getBlocks()
                .get(aim.hit().getBlockPos())
                .state();
        Vec3 offset = VecHelper.rotateCentered(
                VecHelper.voxelSpace(8, 14, 5.5),
                AngleHelper.horizontalAngle(state.getValue(ControlsBlock.FACING)),
                Direction.Axis.Y);
        return aim.hit()
                        .getLocation()
                        .subtract(Vec3.atLowerCornerOf(aim.hit().getBlockPos()))
                        .distanceTo(offset)
                < HIT_RADIUS;
    }

    private static @Nullable Aim aim(ServerPlayer player) {
        List<AbstractContraptionEntity> candidates = PolymerContraptionVisuals.watchedContraptions(player);
        if (candidates.stream().noneMatch(entity -> entity.getContraption() instanceof ElevatorContraption))
            return null;
        Vec3 origin = player.getEyePosition();
        Vec3 target = origin.add(player.getLookAngle().scale(player.blockInteractionRange()));
        BlockHitResult world = player.level()
                .clip(new ClipContext(origin, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (world.getType() == HitResult.Type.BLOCK) target = world.getLocation();
        double best = origin.distanceToSqr(target) + 1e-7;
        Aim result = null;
        AABB bounds = new AABB(origin, target).inflate(16);
        for (AbstractContraptionEntity entity : candidates) {
            if (!entity.getBoundingBox().intersects(bounds)) continue;
            BlockHitResult hit = ray(entity, origin, target);
            if (hit == null) continue;
            double distance = entity.toGlobalVector(hit.getLocation(), 1).distanceToSqr(origin);
            if (distance >= best) continue;
            best = distance;
            result = null;
            if (!(entity.getContraption() instanceof ElevatorContraption contraption)) continue;
            var block = contraption.getBlocks().get(hit.getBlockPos());
            var actor = contraption.getActorAt(hit.getBlockPos());
            if (block != null
                    && block.state().is(AllBlocks.CONTRAPTION_CONTROLS)
                    && actor != null
                    && actor.right != null) {
                result = new Aim(entity, hit);
            }
        }
        return result;
    }

    private static @Nullable BlockHitResult ray(AbstractContraptionEntity entity, Vec3 origin, Vec3 target) {
        Vec3 start = entity.toLocalVector(origin, 1), end = entity.toLocalVector(target, 1);
        var contraption = entity.getContraption();
        return BlockGetter.traverseBlocks(
                start,
                end,
                contraption,
                (source, cursor) -> {
                    BlockHitResult nearest = null;
                    double distance = Double.POSITIVE_INFINITY;
                    for (Direction side : Direction.values()) {
                        if (side == Direction.UP) continue;
                        BlockPos pos = side == Direction.DOWN ? cursor : cursor.relative(side);
                        var block = source.getBlocks().get(pos);
                        if (block == null || source.isHiddenInPortal(pos)) continue;
                        var shape = block.state().getShape(source.getContraptionWorld(), BlockPos.ZERO.below());
                        BlockHitResult hit = shape.clip(start, end, pos);
                        if (hit != null && start.distanceToSqr(hit.getLocation()) < distance) {
                            nearest = hit;
                            distance = start.distanceToSqr(hit.getLocation());
                        }
                    }
                    return nearest;
                },
                unused -> null);
    }
}
