package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItemTags;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.zurrtum.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.zurrtum.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import com.zurrtum.create.content.logistics.box.PackageItem;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.packet.c2s.ChainConveyorConnectionPacket;
import com.zurrtum.create.infrastructure.packet.c2s.ChainPackageInteractionPacket;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.interaction.PolymerPackagePorts;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.train.PolymerTrackPlacementPreview;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class PolymerChainConveyors {
    private record Selection(ServerLevel level, BlockPos pos) {}

    private static final Map<UUID, Selection> SELECTED = new HashMap<>();
    private static final Map<UUID, Ride> RIDERS = new HashMap<>();
    private static final Identifier SHAFT = PolymerBlockParts.model("polymer/chain_conveyor_shaft");
    private static final Identifier GUARD = PolymerBlockParts.model("polymer/chain_conveyor_guard");
    private static final Identifier CASING = PolymerBlockParts.model("polymer/chain_conveyor");
    private static final Map<UUID, LinkPreview> LINK_PREVIEWS = new HashMap<>();
    private static final Map<UUID, PointPreview> POINT_PREVIEWS = new HashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, ChainHolder>> LOADED = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<Integer, PolymerChainGeometry.Motion>> SPRINGS = new IdentityHashMap<>();
    private static final int PART_LIMIT = 256;

    private static final class Ride {
        final ServerLevel level;
        BlockPos pos;

        @Nullable
        BlockPos connection;

        float position;
        boolean flipped;
        int catchingUp = 20;

        Ride(ChainConveyorBlockEntity be, @Nullable BlockPos connection, float position) {
            if (!(be.getLevel() instanceof ServerLevel serverLevel))
                throw new IllegalArgumentException("Chain ride requires a server level");
            level = serverLevel;
            pos = be.getBlockPos();
            this.connection = connection;
            this.position = position;
            flipped = be.getSpeed() < 0;
        }
    }

    private static final Map<ChainConveyorPackage, PolymerChainGeometry.Motion> PONDER_SPRINGS = new WeakHashMap<>();

    public static List<PolymerBlockParts.Part> ponderParts(ChainConveyorBlockEntity be, long tick) {
        List<PolymerBlockParts.Part> out = new ArrayList<>();
        out.add(new PolymerBlockParts.Part(CASING, new Matrix4f()));
        be.prepareStats();
        BlockPos pos = be.getBlockPos();
        for (var entry : be.connectionStats.entrySet()) {
            var value = entry.getValue();
            int count = Math.min(Math.max(1, (int) Math.ceil(value.chainLength())), Math.max(1, PART_LIMIT * 2 / 3));
            for (int i = 0; i < count; i++) {
                Vec3 a = value.start().lerp(value.end(), (double) i / count),
                        b = value.start().lerp(value.end(), (double) (i + 1) / count);
                Vec3 delta = b.subtract(a);
                if (delta.lengthSqr() < 1e-8) continue;
                float yaw = (float) Math.atan2(delta.x, delta.z);
                float pitch = (float) (Math.PI / 2 - Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
                Matrix4f local = new Matrix4f()
                        .translate(a.subtract(Vec3.atLowerCornerOf(pos)).toVector3f())
                        .rotate(new Quaternionf().rotationY(yaw).rotateX(pitch))
                        .scale(
                                1,
                                (float) (delta.length() / (PolymerChainModels.lengthBucket(delta.length()) / 16.0)),
                                1)
                        .translate(-.5f, 0, -.5f);
                int phase = PolymerChainGeometry.phase(
                        tick,
                        be.getSpeed(),
                        value.chainLength(),
                        value.chainLength() * (double) i / count,
                        delta.length(),
                        true);
                out.add(new PolymerBlockParts.Part(PolymerChainModels.link(phase, delta.length()), local));
            }
        }
        BiConsumer<ChainConveyorPackage, BlockPos> box = (pack, connection) -> {
            PolymerChainModels.PackageModels models = PolymerChainModels.box(pack.item);
            if (models == null || pack.item.isEmpty()) return;
            var motion = PONDER_SPRINGS.computeIfAbsent(pack, unused -> new PolymerChainGeometry.Motion());
            motion.tick(
                    tick,
                    be.getPackagePosition(pack.chainPosition, connection),
                    packageYaw(be, pack.chainPosition, connection));
            Matrix4f pose = packagePose(motion, pos);
            for (boolean rig : new boolean[] {false, true})
                out.add(new PolymerBlockParts.Part(
                        rig ? models.rigging() : models.box(), packagePart(pose, rig, be.reversed, pack.item)));
        };
        for (ChainConveyorPackage pack : be.getLoopingPackages()) box.accept(pack, null);
        be.getTravellingPackages().forEach((connection, boxes) -> boxes.forEach(pack -> box.accept(pack, connection)));
        return out;
    }

    public static void register() {
        PolymerChainModels.register();
        PolymerPackagePorts.register();
        PolymerBlockParts.<ChainConveyorBlockEntity>register(
                (be, state, time, out) -> {
                    out.add(new PolymerBlockParts.Part(
                            SHAFT,
                            PolymerBlockParts.spin(
                                    Direction.Axis.Y,
                                    be.getSpeed() * time * .3f
                                            + PolymerBlockParts.rotationOffset(
                                                    state, Direction.Axis.Y, be.getBlockPos()))));
                    be.prepareStats();
                    for (var entry : be.connectionStats.entrySet()) {
                        var stats = entry.getValue();
                        Vec3 direction = stats.end().subtract(stats.start());
                        float yaw = (float) Math.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG;
                        out.add(new PolymerBlockParts.Part(GUARD, PolymerBlockParts.spin(Direction.Axis.Y, yaw))
                                .identified(entry.getKey()));
                    }
                },
                AllBlocks.CHAIN_CONVEYOR);
        BlockWithElementHolder original = BlockWithElementHolder.get(AllBlocks.CHAIN_CONVEYOR.defaultBlockState());
        BlockWithElementHolder.registerOverlay(AllBlocks.CHAIN_CONVEYOR, new BlockWithElementHolder() {
            @Override
            public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                ElementHolder base = original == null ? null : original.createElementHolder(level, pos, state);
                return new ChainHolder(level, pos, base);
            }

            @Override
            public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return true;
            }
        });
        AdapterInteractions.useBlock((player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level))
                return InteractionResult.PASS;
            if (tryUseSelection(serverPlayer, hand)) return InteractionResult.SUCCESS;
            if (!player.getItemInHand(hand).is(Items.IRON_CHAIN)) return InteractionResult.PASS;
            if (!(level.getBlockEntity(hit.getBlockPos()) instanceof ChainConveyorBlockEntity be)) {
                if (SELECTED.remove(player.getUUID()) == null) return InteractionResult.PASS;
                player.sendOverlayMessage(Component.translatable("create.chain_conveyor.selection_cleared"));
                return InteractionResult.SUCCESS;
            }
            connect(serverPlayer, hand, be);
            return InteractionResult.SUCCESS;
        });
        AdapterInteractions.useItem((player, world, hand) -> {
            if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel)) return InteractionResult.PASS;
            if (tryUseSelection(sp, hand)) return InteractionResult.SUCCESS;
            if (sp.getItemInHand(hand).is(Items.IRON_CHAIN)
                    && sp.isShiftKeyDown()
                    && SELECTED.remove(sp.getUUID()) != null) {
                sp.sendOverlayMessage(Component.translatable("create.chain_conveyor.selection_cleared"));
                clearPreviews(sp.getUUID());
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SELECTED.remove(handler.player.getUUID());
            clearPreviews(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SELECTED.entrySet().removeIf(entry -> {
                ServerPlayer player =
                        NativePresentationPolicy.presentationPlayer(server.getPlayerList(), entry.getKey());
                return player == null
                        || !player.isAlive()
                        || player.level() != entry.getValue().level
                        || !entry.getValue().level.hasChunkAt(entry.getValue().pos)
                        || !(entry.getValue().level.getBlockEntity(entry.getValue().pos)
                                instanceof ChainConveyorBlockEntity);
            });
            LINK_PREVIEWS.entrySet().removeIf(entry -> {
                if (SELECTED.containsKey(entry.getKey())) return false;
                entry.getValue().holder.destroy();
                return true;
            });
            for (UUID id : List.copyOf(SELECTED.keySet())) {
                ServerPlayer player = NativePresentationPolicy.presentationPlayer(server.getPlayerList(), id);
                if (player != null) preview(player);
            }
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                pointPreview(player);
            if (server.getTickCount() % 20 == 0)
                SPRINGS.forEach((level, states) ->
                        states.values().removeIf(motion -> level.getGameTime() - motion.lastTick > 30));
            RIDERS.entrySet().removeIf(entry -> {
                ServerPlayer player =
                        NativePresentationPolicy.presentationPlayer(server.getPlayerList(), entry.getKey());
                if (player == null || !tickRider(player, entry.getValue())) {
                    if (player != null) ServerChainConveyorHandler.handleStopRidingPacket(server, player);
                    else ServerChainConveyorHandler.hangingPlayers.removeInt(entry.getKey());
                    return true;
                }
                return false;
            });
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SELECTED.clear();
            LINK_PREVIEWS.values().forEach(view -> view.holder.destroy());
            LINK_PREVIEWS.clear();
            POINT_PREVIEWS.values().forEach(view -> view.holder.destroy());
            POINT_PREVIEWS.clear();
            LOADED.clear();
            SPRINGS.clear();
            RIDERS.keySet().forEach(ServerChainConveyorHandler.hangingPlayers::removeInt);
            RIDERS.clear();
        });
    }

    private static boolean usable(ServerPlayer player, ChainConveyorBlockEntity be) {
        if (PolymerNativeClients.isNative(player)) return false;
        return player.isAlive()
                && !player.isSpectator()
                && !be.isRemoved()
                && player.level() == be.getLevel()
                && player.mayBuild()
                && player.level().hasChunkAt(be.getBlockPos())
                && player.level().mayInteract(player, be.getBlockPos())
                && player.mayInteract(player.level(), be.getBlockPos());
    }

    private record Pick(
            ChainConveyorBlockEntity be, @Nullable BlockPos connection, PolymerChainGeometry.Point point) {}

    private record PointPreview(ServerLevel level, Vec3 origin, ElementHolder holder, ItemDisplayElement point) {}

    private static void clearPreviews(UUID id) {
        LinkPreview link = LINK_PREVIEWS.remove(id);
        if (link != null) link.holder.destroy();
        PointPreview point = POINT_PREVIEWS.remove(id);
        if (point != null) point.holder.destroy();
    }

    private static @Nullable Pick pick(ServerPlayer player) {
        if (PolymerNativeClients.isNative(player)) return null;
        if (!player.isAlive() || player.isSpectator()) return null;
        Map<BlockPos, ChainHolder> holders = LOADED.get(player.level());
        if (holders == null) return null;
        Vec3 eye = player.getEyePosition(),
                end = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 1));
        var terrain = player.pick(player.blockInteractionRange() + 1, 1, false);
        double best = terrain.getLocation().distanceToSqr(eye);
        Pick result = null;
        boolean dismantling = player.isShiftKeyDown() && player.isHolding(s -> s.is(AllItemTags.TOOLS_WRENCH));
        double reach = AllConfigs.server().kinetics.maxChainConveyorLength.get() + player.blockInteractionRange() + 3;
        for (ChainHolder holder : holders.values()) {
            ChainConveyorBlockEntity be = holder.source;
            if (be == null || be.isRemoved() || eye.distanceToSqr(Vec3.atCenterOf(holder.pos)) > reach * reach)
                continue;
            if (!dismantling) {
                var hit = PolymerChainGeometry.loop(holder.pos, eye, end);
                if (hit != null && hit.distanceSqr() <= best) {
                    best = hit.distanceSqr();
                    result = new Pick(be, null, hit);
                }
            }
            be.prepareStats();
            for (var entry : be.connectionStats.entrySet()) {
                var stats = entry.getValue();
                var hit = PolymerChainGeometry.connection(entry.getKey(), stats.start(), stats.end(), eye, end);
                if (hit != null && hit.distanceSqr() <= best) {
                    best = hit.distanceSqr();
                    result = new Pick(be, entry.getKey(), hit);
                }
            }
        }
        return result;
    }

    private static boolean tryUseSelection(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(hand == InteractionHand.MAIN_HAND
                        && (PackageItem.isPackage(stack) || stack.is(AllItems.PACKAGE_FROGPORT)))
                && !stack.is(AllItemTags.CHAIN_RIDEABLE)) return false;
        Pick pick = pick(player);
        if (pick == null) return false;
        usePoint(player, hand, pick.be, pick.connection, pick.point.position(), pick.point.world());
        return true;
    }

    private static void pointPreview(ServerPlayer player) {
        boolean active = PackageItem.isPackage(player.getMainHandItem())
                || player.getMainHandItem().is(AllItems.PACKAGE_FROGPORT);
        Pick selected = active && !player.isHolding(s -> s.is(AllItemTags.TOOLS_WRENCH)) ? pick(player) : null;
        PointPreview preview = POINT_PREVIEWS.get(player.getUUID());
        if (selected == null || preview != null && preview.level != player.level()) {
            if (preview != null) {
                preview.holder.destroy();
                POINT_PREVIEWS.remove(player.getUUID());
            }
            if (selected == null) return;
            preview = null;
        }
        if (preview == null) {
            ElementHolder holder = new AdapterHolder();
            Vec3 origin = selected.point.world();
            new ManualAttachment(holder, player.level(), () -> origin);
            ItemDisplayElement dot =
                    new ItemDisplayElement(PolymerTrackPlacementPreview.display("track_preview_line", 0xFFFFFF));
            dot.setItemDisplayContext(ItemDisplayContext.NONE);
            dot.setTransformation(new Matrix4f().scale(1 / 6f).rotateY(Mth.PI));
            dot.setBrightness(new Brightness(15, 15));
            dot.setTeleportDuration(1);
            holder.addElement(dot);
            holder.startWatching(player);
            preview = new PointPreview(player.level(), origin, holder, dot);
            POINT_PREVIEWS.put(player.getUUID(), preview);
        }
        preview.point.setOffset(selected.point.world().subtract(preview.origin));
        preview.holder.tick();
    }

    private record LinkPreview(String key, ElementHolder holder) {}

    private static void preview(ServerPlayer player) {
        if (!player.isHolding(stack -> stack.is(Items.IRON_CHAIN))) {
            LinkPreview old = LINK_PREVIEWS.remove(player.getUUID());
            if (old != null) old.holder.destroy();
            return;
        }
        Selection selection = SELECTED.get(player.getUUID());
        if (selection == null
                || !selection.level.hasChunkAt(selection.pos)
                || !(selection.level.getBlockEntity(selection.pos) instanceof ChainConveyorBlockEntity from)) {
            SELECTED.remove(player.getUUID());
            LinkPreview old = LINK_PREVIEWS.remove(player.getUUID());
            if (old != null) old.holder.destroy();
            return;
        }
        BlockHitResult ray = PolymerReach.targetBlock(player);
        BlockPos target = ray != null
                        && !ray.getBlockPos().equals(selection.pos)
                        && selection.level.hasChunkAt(ray.getBlockPos())
                        && selection.level.getBlockEntity(ray.getBlockPos()) instanceof ChainConveyorBlockEntity
                ? ray.getBlockPos()
                : null;
        ItemStack chain =
                player.getMainHandItem().is(Items.IRON_CHAIN) ? player.getMainHandItem() : player.getOffhandItem();
        ChainConveyorBlockEntity to =
                target != null && selection.level.getBlockEntity(target) instanceof ChainConveyorBlockEntity hit
                        ? hit
                        : null;
        String failure = to == null ? null : connectionFailure(player, from, to, chain);
        boolean valid = to != null && failure == null;
        Component status = Component.translatable(
                "create.chain_conveyor." + (to == null ? "select_second" : valid ? "valid_connection" : failure));
        if (to != null) status = status.copy().withStyle(valid ? ChatFormatting.GREEN : ChatFormatting.RED);
        player.sendOverlayMessage(status);
        String key = selection.pos + ":" + target + ":" + valid;
        LinkPreview old = LINK_PREVIEWS.get(player.getUUID());
        if (old != null && old.key.equals(key)) {
            old.holder.tick();
            return;
        }
        if (old != null) old.holder.destroy();
        ElementHolder holder = new AdapterHolder();
        Vec3 origin = Vec3.atCenterOf(selection.pos);
        new ManualAttachment(holder, selection.level, () -> origin);
        int color = target == null ? 0xFFFFFF : valid ? 0x95CD41 : 0xEA5C2B;
        previewRing(holder, origin, selection.pos, color);
        if (target != null) {
            previewRing(holder, origin, target, color);
            Vec3 fromPoint = Vec3.atCenterOf(target), toPoint = origin, diff = fromPoint.subtract(toPoint);
            Vec3 normal = diff.cross(new Vec3(0, 1, 0)).normalize().scale(.875);
            fromPoint = fromPoint.subtract(diff.normalize().scale(.5));
            toPoint = toPoint.add(diff.normalize().scale(.5));
            previewLine(holder, origin, fromPoint.add(normal), toPoint.add(normal), color);
            previewLine(holder, origin, fromPoint.subtract(normal), toPoint.subtract(normal), color);
        }
        holder.startWatching(player);
        holder.tick();
        LINK_PREVIEWS.put(player.getUUID(), new LinkPreview(key, holder));
    }

    private static void previewRing(ElementHolder holder, Vec3 origin, BlockPos pos, int color) {
        for (int y = 0; y < 2; y++) {
            Vec3 previous = VecHelper.rotate(new Vec3(0, .125 + y * .75, 1.25), -22.5, Direction.Axis.Y)
                    .add(Vec3.atBottomCenterOf(pos));
            for (int i = 0; i < 8; i++) {
                Vec3 next = VecHelper.rotate(new Vec3(0, .125 + y * .75, 1.25), 22.5 + i * 45, Direction.Axis.Y)
                        .add(Vec3.atBottomCenterOf(pos));
                previewLine(holder, origin, previous, next, color);
                previous = next;
            }
        }
    }

    private static void previewLine(ElementHolder holder, Vec3 origin, Vec3 from, Vec3 to, int color) {
        PolymerTrackPlacementPreview.addLine(
                holder, origin, new PolymerTrackPlacementPreview.Line(from, to, 1 / 16f), color);
    }

    static @Nullable String connectionFailure(
            ServerPlayer player, ChainConveyorBlockEntity from, ChainConveyorBlockEntity to, ItemStack chain) {
        BlockPos a = from.getBlockPos(), b = to.getBlockPos(), diff = b.subtract(a);
        double horizontal = Math.hypot(diff.getX(), diff.getZ()) - 1.5;
        int maxConnections =
                AllConfigs.server().kinetics.maxChainConveyorConnections.get();
        if (!a.closerThan(b, AllConfigs.server().kinetics.maxChainConveyorLength.get())) return "too_far";
        if (a.closerThan(b, 2.5)) return "too_close";
        if (horizontal <= 0) return "cannot_connect_vertically";
        if (Math.abs(diff.getY()) / horizontal > 1) return "too_steep";
        if (!usable(player, from) || !usable(player, to)) return "blocks_invalid";
        if (from.connections.size() >= maxConnections || to.connections.size() >= maxConnections)
            return "cannot_add_more_connections";
        if (from.connections.contains(diff) || to.connections.contains(diff.multiply(-1))) return "already_connected";
        if (!chain.is(Items.IRON_CHAIN)
                || !player.isCreative()
                        && !ChainConveyorBlockEntity.getChainsFromInventory(
                                player, chain, ChainConveyorBlockEntity.getChainCost(diff), true))
            return "not_enough_chains";
        return null;
    }

    private static void connect(ServerPlayer player, InteractionHand hand, ChainConveyorBlockEntity be) {
        if (!usable(player, be)) return;
        if (player.isShiftKeyDown()) {
            SELECTED.remove(player.getUUID());
            clearPreviews(player.getUUID());
            player.sendOverlayMessage(Component.translatable("create.chain_conveyor.selection_cleared"));
            return;
        }
        Selection first = SELECTED.remove(player.getUUID());
        if (first == null || first.level != player.level()) {
            if (be.connections.size()
                    >= AllConfigs.server().kinetics.maxChainConveyorConnections.get()) {
                player.sendOverlayMessage(Component.translatable("create.chain_conveyor.cannot_add_more_connections"));
                return;
            }
            SELECTED.put(player.getUUID(), new Selection(player.level(), be.getBlockPos()));
            player.sendOverlayMessage(Component.translatable("create.chain_conveyor.select_second"));
            player.swing(hand, SwingAnimation.DEFAULT, false);
            return;
        }
        if (!first.level.hasChunkAt(first.pos)
                || !(first.level.getBlockEntity(first.pos) instanceof ChainConveyorBlockEntity source)) return;
        ItemStack chain = player.getItemInHand(hand);
        String failure = connectionFailure(player, source, be, chain);
        clearPreviews(player.getUUID());
        if (failure != null) {
            player.sendOverlayMessage(
                    Component.translatable("create.chain_conveyor." + failure).withStyle(ChatFormatting.RED));
            return;
        }
        source.prepareStats();
        be.prepareStats();
        AllHandle.onChainConveyorConnection(
                player.connection,
                new ChainConveyorConnectionPacket(first.pos, be.getBlockPos(), chain.copyWithCount(1), true));
        player.getInventory().setChanged();
        player.swing(hand, SwingAnimation.DEFAULT, false);
        player.sendOverlayMessage(Component.empty());
    }

    private static void usePoint(
            ServerPlayer player,
            InteractionHand hand,
            ChainConveyorBlockEntity be,
            @Nullable BlockPos connection,
            float position,
            Vec3 point) {
        if (hand == null
                || !usable(player, be)
                || player.getEyePosition().distanceToSqr(point) > Math.pow(player.blockInteractionRange() + 1, 2))
            return;
        var obstruction = player.level()
                .clip(new ClipContext(
                        player.getEyePosition(), point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (obstruction.getType() == HitResult.Type.BLOCK
                && !obstruction.getBlockPos().equals(be.getBlockPos())
                && obstruction.getLocation().distanceToSqr(player.getEyePosition()) + .01
                        < point.distanceToSqr(player.getEyePosition())) return;
        if (connection != null && !player.level().hasChunkAt(be.getBlockPos().offset(connection))) return;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.IRON_CHAIN)) {
            connect(player, hand, be);
            return;
        }
        if (player.isHolding(item -> item.is(AllItemTags.CHAIN_RIDEABLE))) {
            if (player.isShiftKeyDown()) {
                if (connection == null || !be.connections.contains(connection)) return;
                BlockPos end = be.getBlockPos().offset(connection);
                if (!player.level().hasChunkAt(end)
                        || !(player.level().getBlockEntity(end) instanceof ChainConveyorBlockEntity target)
                        || !usable(player, target)
                        || !target.connections.contains(connection.multiply(-1))) return;
                be.prepareStats();
                target.prepareStats();
                AllHandle.onChainConveyorConnection(
                        player.connection,
                        new ChainConveyorConnectionPacket(
                                be.getBlockPos(), end, new ItemStack(Items.IRON_CHAIN), false));
            } else if (!player.isPassenger()) {
                RIDERS.put(player.getUUID(), new Ride(be, connection, position));
                player.sendOverlayMessage(Component.translatable("mount.onboard", Component.keybind("key.sneak")));
            }
        } else if (hand == InteractionHand.MAIN_HAND && stack.is(AllItems.PACKAGE_FROGPORT)) {
            PolymerPackagePorts.select(player, be, connection, position);
        } else if (hand == InteractionHand.MAIN_HAND && PackageItem.isPackage(stack)) {
            if (connection != null && !be.connections.contains(connection)) return;
            AllHandle.onChainPackageInteraction(
                    player.connection,
                    new ChainPackageInteractionPacket(
                            be.getBlockPos(), connection == null ? BlockPos.ZERO : connection, position, false));
            player.getInventory().setChanged();
        }
    }

    private static boolean advance(ServerPlayer player, Ride ride, ChainConveyorBlockEntity be) {
        be.prepareStats();
        float speed = be.getSpeed() / 360f;
        if (ride.connection != null) {
            var stats = be.connectionStats.get(ride.connection);
            if (stats == null) return false;
            BlockPos end = be.getBlockPos().offset(ride.connection);
            if (!ride.level.hasChunkAt(end)
                    || !(ride.level.getBlockEntity(end) instanceof ChainConveyorBlockEntity next)) return false;
            if (ride.flipped != (be.getSpeed() < 0)) {
                ride.flipped = be.getSpeed() < 0;
                ride.pos = end;
                ride.position = stats.chainLength() - ride.position;
                ride.connection = ride.connection.multiply(-1);
                return true;
            }
            ride.position = Math.min(stats.chainLength(), ride.position + Math.abs(speed));
            if (ride.position >= stats.chainLength()) {
                ride.position = be.wrapAngle(stats.tangentAngle() + 180 + 70 * (be.reversed ? -1 : 1));
                ride.pos = next.getBlockPos();
                ride.connection = null;
            }
            return true;
        }
        float previous = ride.position;
        ride.position = be.wrapAngle(ride.position + speed / (Mth.PI * 1.5f) * 360f);
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos branch : be.connections) {
            double diff = Vec3.atLowerCornerOf(branch)
                    .normalize()
                    .distanceToSqr(player.getLookAngle().normalize());
            if (diff <= best) {
                nearest = branch;
                best = diff;
            }
        }
        if (nearest != null
                && be.loopThresholdCrossed(
                        ride.position, previous, be.connectionStats.get(nearest).tangentAngle())) {
            ride.position = 0;
            ride.connection = nearest;
        }
        return true;
    }

    private static boolean tickRider(ServerPlayer player, Ride ride) {
        if (PolymerNativeClients.isNative(player)) return false;
        if (!player.isAlive()
                || player.isSpectator()
                || player.isPassenger()
                || player.isShiftKeyDown()
                || player.level() != ride.level
                || player.connection.awaitingPositionFromClient != null
                || !player.isHolding(stack -> stack.is(AllItemTags.CHAIN_RIDEABLE))
                || !ride.level.hasChunkAt(ride.pos)
                || !(ride.level.getBlockEntity(ride.pos) instanceof ChainConveyorBlockEntity be)) return false;
        if (ride.level.tickRateManager().isEntityFrozen(player)) return true;
        if (!advance(player, ride, be)
                || !ride.level.hasChunkAt(ride.pos)
                || !(ride.level.getBlockEntity(ride.pos) instanceof ChainConveyorBlockEntity current)) return false;
        current.prepareStats();
        Vec3 target;
        if (ride.connection == null)
            target = Vec3.atBottomCenterOf(ride.pos)
                    .add(VecHelper.rotate(new Vec3(0, .25, 1), ride.position, Direction.Axis.Y));
        else {
            var stats = current.connectionStats.get(ride.connection);
            if (stats == null) return false;
            target = stats.start()
                    .add(stats.end()
                            .subtract(stats.start())
                            .normalize()
                            .scale(Math.min(stats.chainLength(), ride.position)));
        }
        Vec3 head = player.position().add(0, player.getBoundingBox().getYsize() + .5 * player.getScale(), 0);
        Vec3 error = target.subtract(head);
        if (ride.catchingUp > 0) ride.catchingUp--;
        if (!Double.isFinite(error.lengthSqr()) || ride.catchingUp == 0 && (error.lengthSqr() > 9 || error.y < -1))
            return false;
        Vec3 velocity = PolymerChainHang.clientDelta(player, player.getKnownMovement())
                .scale(.75)
                .add(error.scale(.25));
        player.setDeltaMovement(velocity);
        NativePresentationPolicy.sendPresentation(player, new ClientboundSetEntityMotionPacket(player));
        player.applyPostImpulseGraceTime(10);
        player.fallDistance = 0;
        player.connection.resetFlyingTicks();
        if (ride.level.getGameTime() % 10 == 0)
            ServerChainConveyorHandler.handleTTLPacket(ride.level.getServer(), player);
        return true;
    }

    private static final class ChainHolder extends PolymerLitModelHolder {
        private final ServerLevel level;
        private final BlockPos pos;
        private final List<VirtualElement> links = new ArrayList<>();
        private final List<Link> scrolling = new ArrayList<>();
        private final Map<ChainConveyorPackage, PackageDisplay> packages = new IdentityHashMap<>();
        private final Map<ChainConveyorPackage, BlockPos> packageConnections = new IdentityHashMap<>();
        private Map<BlockPos, ChainConveyorBlockEntity.ConnectionStats> shown = Map.of();
        private boolean initialized;
        private @Nullable ChainConveyorBlockEntity source;

        private static final class Link {
            final ItemDisplayElement display;
            final double total, start, length;
            int phase = -1;

            Link(ItemDisplayElement display, double total, double start, double length) {
                this.display = display;
                this.total = total;
                this.start = start;
                this.length = length;
            }
        }

        private record PackageDisplay(ItemDisplayElement box, ItemDisplayElement rigging, InteractionElement target) {}

        ChainHolder(ServerLevel level, BlockPos pos, @Nullable ElementHolder base) {
            super(level, pos);
            this.level = level;
            this.pos = pos.immutable();
            ItemDisplayElement casing =
                    PolymerDisplays.item(PolymerBlockParts.displayItem(CASING), ItemDisplayContext.NONE);
            casing.setTransformation(PolymerBlockParts.displayTransform(new Matrix4f()));
            addElement(casing);
            if (base != null)
                for (VirtualElement element : List.copyOf(base.getElements())) {
                    base.removeElement(element);
                    addElement(element);
                }
        }

        @Override
        protected void onTick() {
            super.onTick();
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity be)) return;
            source = be;
            LOADED.computeIfAbsent(level, unused -> new HashMap<>()).put(pos, this);
            if (getWatchingPlayers().isEmpty()) return;
            updateVisuals(be);
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            if (!level.getServer().isSameThread())
                throw new IllegalStateException("Chain watching must run on the server thread");
            if (!getWatchingPlayers().contains(player)) {
                if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof ChainConveyorBlockEntity be))
                    return false;
                source = be;
                LOADED.computeIfAbsent(level, unused -> new HashMap<>()).put(pos, this);
                updateVisuals(be);
            }
            return super.startWatching(player);
        }

        private void updateVisuals(ChainConveyorBlockEntity be) {
            be.prepareStats();
            Map<BlockPos, ChainConveyorBlockEntity.ConnectionStats> stats = Map.copyOf(be.connectionStats);
            if (!initialized || !stats.equals(shown)) {
                links.forEach(this::removeElement);
                links.clear();
                scrolling.clear();
                shown = stats;
                initialized = true;
                for (int i = 0; i < 8; i++) {
                    float angle = i * 45;
                    Vec3 a = be.getPackagePosition(angle, null);
                    point(be, null, angle, a);
                }
                int budget = Math.max(1, (PART_LIMIT - links.size()) / Math.max(1, stats.size()));
                for (var entry : stats.entrySet()) {
                    var value = entry.getValue();
                    int count =
                            Math.min(Math.max(1, (int) Math.ceil(value.chainLength())), Math.max(1, budget * 2 / 3));
                    for (int i = 0; i < count; i++) {
                        Vec3 a = value.start().lerp(value.end(), (double) i / count),
                                b = value.start().lerp(value.end(), (double) (i + 1) / count);
                        segment(a, b, value.chainLength(), value.chainLength() * (double) i / count);
                        if (i % 2 == 0)
                            point(be, entry.getKey(), value.chainLength() * (i + .5f) / count, a.lerp(b, .5));
                    }
                }
            }
            for (Link link : scrolling) {
                int phase = PolymerChainGeometry.phase(
                        level.getGameTime(), be.getSpeed(), link.total, link.start, link.length);
                if (phase != link.phase) {
                    link.display.setItem(PolymerBlockParts.displayItem(PolymerChainModels.link(phase, link.length)));
                    link.phase = phase;
                }
            }
            Set<ChainConveyorPackage> current = new HashSet<>(be.getLoopingPackages());
            be.getTravellingPackages().values().forEach(current::addAll);
            packageConnections.clear();
            be.getLoopingPackages().forEach(box -> packageConnections.put(box, null));
            be.getTravellingPackages()
                    .forEach((connection, boxes) -> boxes.forEach(box -> packageConnections.put(box, connection)));
            packages.entrySet().removeIf(entry -> {
                if (current.contains(entry.getKey())) return false;
                PackageDisplay display = entry.getValue();
                removeElement(display.box);
                removeElement(display.rigging);
                removeElement(display.target);
                return true;
            });
            for (ChainConveyorPackage box : be.getLoopingPackages()) showPackage(be, box, null);
            be.getTravellingPackages()
                    .forEach((connection, boxes) -> boxes.forEach(box -> showPackage(be, box, connection)));
        }

        @Override
        protected void onAttachmentRemoved(HolderAttachment attachment) {
            Map<BlockPos, ChainHolder> holders = LOADED.get(level);
            if (holders != null) {
                holders.remove(pos, this);
                if (holders.isEmpty()) LOADED.remove(level);
            }
            source = null;
            super.onAttachmentRemoved(attachment);
        }

        private void segment(Vec3 a, Vec3 b, double total, double start) {
            Vec3 delta = b.subtract(a);
            if (delta.lengthSqr() < 1e-8) return;
            float yaw = (float) Math.atan2(delta.x, delta.z);
            float pitch = (float) (Math.PI / 2 - Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
            var rotation = new Quaternionf().rotationY(yaw).rotateX(pitch);
            Matrix4f local = new Matrix4f()
                    .translate(a.subtract(Vec3.atLowerCornerOf(pos)).toVector3f())
                    .rotate(rotation)
                    .scale(1, (float) (delta.length() / (PolymerChainModels.lengthBucket(delta.length()) / 16.0)), 1)
                    .translate(-.5f, 0, -.5f);
            ItemDisplayElement display = PolymerDisplays.item(
                    PolymerBlockParts.displayItem(PolymerChainModels.link(0, delta.length())), ItemDisplayContext.NONE);
            Matrix4f transform = PolymerBlockParts.displayTransform(local);
            if (PolymerDecorativeCulling.ENABLED) {
                Vec3 anchor = new Vec3((a.x + b.x) / 2, Math.min(a.y, b.y), (a.z + b.z) / 2);
                Vec3 offset = anchor.subtract(Vec3.atCenterOf(pos));
                transform = PolymerDecorativeCulling.anchored(display, offset, transform);
                PolymerDecorativeCulling.sized(
                        display, (float) Math.max(Math.max(Math.abs(delta.x), Math.abs(delta.z)), .25), (float)
                                Math.max(Math.abs(delta.y), .25));
            }
            display.setTransformation(transform);
            addElement(display);
            links.add(display);
            scrolling.add(new Link(display, total, start, delta.length()));
        }

        private void point(
                ChainConveyorBlockEntity be, @Nullable BlockPos connection, float position, Vec3 worldPoint) {
            InteractionElement point = new InteractionElement(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand, Vec3 hit, boolean secondary) {
                    if (player.getItemInHand(hand).is(Items.IRON_CHAIN)) connect(player, hand, be);
                    else tryUseSelection(player, hand);
                }
            });
            point.setSize(.55f, .45f);
            point.setOffset(worldPoint.subtract(Vec3.atCenterOf(pos)).add(0, -.2, 0));
            addElement(point);
            links.add(point);
        }

        private void showPackage(ChainConveyorBlockEntity be, ChainConveyorPackage box, @Nullable BlockPos connection) {
            PolymerChainModels.PackageModels models = PolymerChainModels.box(box.item);
            if (models == null || box.item.isEmpty()) return;
            PackageDisplay elements = packages.get(box);
            if (elements == null) {
                ItemDisplayElement display = new ItemDisplayElement(PolymerBlockParts.displayItem(models.box()));
                ItemDisplayElement rigging = new ItemDisplayElement(PolymerBlockParts.displayItem(models.rigging()));
                for (ItemDisplayElement element : List.of(display, rigging)) {
                    element.setItemDisplayContext(ItemDisplayContext.NONE);
                    element.setInterpolationDuration(1);
                    element.setTeleportDuration(1);
                }
                InteractionElement target = new InteractionElement(new VirtualElement.InteractionHandler() {
                    @Override
                    public void interact(ServerPlayer player, InteractionHand hand, Vec3 hit, boolean secondary) {
                        BlockPos selected = packageConnections.get(box);
                        if (hand != InteractionHand.MAIN_HAND
                                || !usable(player, be)
                                || player.getMainHandItem().is(AllItemTags.TOOLS_WRENCH)
                                || player.getMainHandItem().is(Items.IRON_CHAIN)
                                || player.getMainHandItem().is(AllItems.PACKAGE_FROGPORT)
                                || player.getEyePosition()
                                                .distanceToSqr(be.getPackagePosition(box.chainPosition, selected))
                                        > Math.pow(player.blockInteractionRange() + 1, 2)) return;
                        List<ChainConveyorPackage> list = selected == null
                                ? be.getLoopingPackages()
                                : be.getTravellingPackages().get(selected);
                        if (list == null || !list.contains(box)) return;
                        AllHandle.onChainPackageInteraction(
                                player.connection,
                                new ChainPackageInteractionPacket(
                                        pos, selected == null ? BlockPos.ZERO : selected, box.chainPosition, true));
                        player.getInventory().setChanged();
                    }
                });
                target.setSize(.9f, 1.4f);
                addElement(display);
                addElement(rigging);
                addElement(target);
                elements = new PackageDisplay(display, rigging, target);
                packages.put(box, elements);
            }
            var motion = SPRINGS.computeIfAbsent(level, unused -> new HashMap<>())
                    .computeIfAbsent(box.netId, unused -> new PolymerChainGeometry.Motion());
            motion.tick(
                    level.getGameTime(),
                    be.getPackagePosition(box.chainPosition, connection),
                    packageYaw(be, box.chainPosition, connection));
            Matrix4f pose = packagePose(motion, pos);
            for (boolean rig : new boolean[] {false, true}) {
                ItemDisplayElement element = rig ? elements.rigging : elements.box;
                element.setTransformation(
                        PolymerBlockParts.displayTransform(packagePart(pose, rig, be.reversed, box.item)));
                element.startInterpolationIfDirty();
            }
            elements.target.setOffset(
                    motion.target.subtract(Vec3.atCenterOf(pos)).add(0, -.7, 0));
        }
    }

    private static float packageYaw(ChainConveyorBlockEntity be, float chainPosition, @Nullable BlockPos connection) {
        var stats = connection == null ? null : be.connectionStats.get(connection);
        if (stats == null) return chainPosition + (be.reversed ? 180 : 0);
        Vec3 diff = stats.end().subtract(stats.start());
        return Mth.wrapDegrees((float) Math.atan2(diff.x, diff.z) * Mth.RAD_TO_DEG - 90);
    }

    private static Matrix4f packagePose(PolymerChainGeometry.Motion motion, BlockPos pos) {
        Vec3 dangle = VecHelper.rotate(motion.target.add(0, .5, 0).subtract(motion.pos), -motion.yaw, Direction.Axis.Y);
        float zRot = Mth.clamp(Mth.wrapDegrees((float) Math.atan2(-dangle.x, dangle.y) * Mth.RAD_TO_DEG) / 2, -25, 25);
        float xRot = Mth.clamp(Mth.wrapDegrees((float) Math.atan2(dangle.z, dangle.y) * Mth.RAD_TO_DEG) / 2, -25, 25);
        return new Matrix4f()
                .translate(motion.target.subtract(Vec3.atLowerCornerOf(pos)).toVector3f())
                .translate(0, 10 / 16f, 0)
                .rotateY(motion.yaw * Mth.DEG_TO_RAD)
                .rotateZ(zRot * Mth.DEG_TO_RAD)
                .rotateX(xRot * Mth.DEG_TO_RAD);
    }

    private static Matrix4f packagePart(Matrix4f pose, boolean rig, boolean reversed, ItemStack item) {
        Matrix4f part = new Matrix4f(pose);
        if (rig && reversed) part.rotateY(Mth.PI);
        return part.translate(-.5f, -PackageItem.getHookDistance(item) - 1 / 16f, -.5f);
    }
}
