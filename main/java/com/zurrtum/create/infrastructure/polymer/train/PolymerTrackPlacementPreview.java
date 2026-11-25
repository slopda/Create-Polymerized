package com.zurrtum.create.infrastructure.polymer.train;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllItemTags;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.trains.track.*;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PolymerTrackPlacementPreview {
    private static final Map<UUID, ElementHolder> HOLDERS = new HashMap<>();
    private static final Map<UUID, Frame> FRAMES = new HashMap<>();

    public record Line(Vec3 from, Vec3 to, float width) {}

    record Hint(BlockPos pos, boolean valid) {}

    record Target(BlockPos pos, BlockState state) {}

    private record Frame(Object level, Vec3 origin, List<Line> lines, List<Hint> hints, int color, Component status) {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
                if (server.getTickCount() % 5 == 0) tick(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HOLDERS.values().forEach(ElementHolder::destroy);
            HOLDERS.clear();
            FRAMES.clear();
        });
    }

    private static void clear(UUID player) {
        ElementHolder old = HOLDERS.remove(player);
        if (old != null) old.destroy();
        FRAMES.remove(player);
    }

    static void tick(ServerPlayer player) {
        Frame frame = frame(player);
        if (frame == null) {
            clear(player.getUUID());
            return;
        }
        player.sendOverlayMessage(frame.status);
        if (frame.equals(FRAMES.get(player.getUUID()))) return;
        clear(player.getUUID());
        ElementHolder holder = new AdapterHolder();
        new ManualAttachment(holder, player.level(), () -> frame.origin);
        for (Line line : frame.lines) addLine(holder, frame.origin, line, frame.color);
        for (Hint hint : frame.hints) {
            ItemDisplayElement tile = PolymerDisplays.item(
                    display("track_hint", hint.valid ? 0x95CD41 : 0xEA5C2B), ItemDisplayContext.NONE);
            tile.setTransformation(new Matrix4f()
                    .translate(Vec3.atLowerCornerOf(hint.pos)
                            .subtract(frame.origin)
                            .toVector3f())
                    .translate(.5f, .002f, .5f)
                    .scale(.998f)
                    .rotateY((float) Math.PI));
            tile.setBrightness(new Brightness(15, 15));
            holder.addElement(tile);
        }
        holder.startWatching(player);
        holder.tick();
        HOLDERS.put(player.getUUID(), holder);
        FRAMES.put(player.getUUID(), frame);
    }

    private static Frame frame(ServerPlayer player) {
        if (player.isSpectator()) return null;
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(AllItemTags.TRACKS)) {
            hand = InteractionHand.OFF_HAND;
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof TrackBlockItem item) || !stack.has(AllDataComponents.TRACK_CONNECTING_FROM))
            return null;
        BlockHitResult hit = PolymerReach.targetBlock(player);
        if (hit == null) return null;
        Target target = target(player, hand, item, hit);
        if (target == null) return null;
        BlockPos pos = target.pos();
        BlockState state = target.state();
        BlockPos first = stack.get(AllDataComponents.TRACK_CONNECTING_FROM).pos();
        int minX = Math.min(first.getX(), pos.getX()) - 2, maxX = Math.max(first.getX(), pos.getX()) + 2;
        int minZ = Math.min(first.getZ(), pos.getZ()) - 2, maxZ = Math.max(first.getZ(), pos.getZ()) + 2;
        if (first.distSqr(pos) > 256 * 256) return null;
        for (int x = minX >> 4; x <= maxX >> 4; x++)
            for (int z = minZ >> 4; z <= maxZ >> 4; z++)
                if (player.level().getChunkSource().getChunkNow(x, z) == null) return null;
        boolean maximise = stack.getOrDefault(AllDataComponents.TRACK_EXTENDED_CURVE, false);
        TrackPlacement.PlacementInfo info =
                TrackPlacement.previewConnection(player.level(), player, pos, state, stack, maximise);
        int color = info.valid ? 0x95CD41 : 0xEA5C2B;
        Vec3 origin = Vec3.atCenterOf(pos);
        List<Hint> hints = new ArrayList<>();
        if (hit.getDirection() == Direction.UP) {
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++) {
                    BlockPos hint = pos.offset(x, 0, z);
                    TrackPlacement.PlacementInfo candidate =
                            TrackPlacement.previewConnection(player.level(), player, hint, state, stack, maximise);
                    hints.add(new Hint(hint, candidate.valid));
                }
        }
        Component status = info.valid
                ? Component.translatable("create.track.valid_connection").withColor(color)
                : info.message == null
                        ? Component.empty()
                        : Component.translatable("create." + info.message).withColor(color);
        if (!player.isCreative())
            status = status.copy()
                    .append(Component.literal(
                            "  Tracks: " + info.requiredTracks + "  Pavement: " + info.requiredPavement));
        return new Frame(player.level(), origin, List.copyOf(lines(info)), List.copyOf(hints), color, status);
    }

    static Target target(ServerPlayer player, InteractionHand hand, TrackBlockItem item, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = player.level().getBlockState(pos);
        if (!(state.getBlock() instanceof ITrackBlock)) {
            if (!state.canBeReplaced()) pos = pos.relative(hit.getDirection());
            state = item.getPlacementState(new UseOnContext(player, hand, hit));
        }
        return state != null && state.getBlock() instanceof TrackBlock ? new Target(pos, state) : null;
    }

    static List<Line> lines(TrackPlacement.PlacementInfo info) {
        List<Line> lines = new ArrayList<>();
        if (info.end1 == null || info.end2 == null) return lines;
        Vec3 up = new Vec3(0, 4 / 16.0, 0);
        for (int i = 0; i < 2; i++) {
            Vec3 end = i == 0 ? info.end1 : info.end2,
                    axis = i == 0 ? info.axis1 : info.axis2,
                    normal = i == 0 ? info.normal1 : info.normal2;
            Vec3 direction = axis.normalize(),
                    rail = normal.cross(direction).scale(15 / 16.0),
                    offset = direction.scale(.125);
            int extent = info.valid ? (i == 0 ? info.end1Extent : info.end2Extent) : 0;
            if (i == 0 && info.curve == null && extent > 0) extent -= 2;
            Vec3 expansion = direction.scale(extent * axis.length());
            for (int side : new int[] {-1, 1}) {
                Vec3 start = end.add(rail.scale(side)).add(up);
                lines.add(new Line(start.subtract(offset), start.add(expansion), .125f));
            }
        }
        BezierConnection curve = info.curve;
        if (curve == null) return lines;
        Vec3 first = curve.starts.getFirst(), second = curve.starts.getSecond();
        Vec3 handle1 = first.add(curve.axes.getFirst().scale(curve.getHandleLength()));
        Vec3 handle2 = second.add(curve.axes.getSecond().scale(curve.getHandleLength()));
        Vec3 prev1 = null, prev2 = null;
        int count = curve.getSegmentCount();
        for (int i = 0; i <= count; i++) {
            float t = i / (float) count;
            Vec3 center = VecHelper.bezier(first, second, handle1, handle2, t);
            Vec3 derivative = VecHelper.bezierDerivative(first, second, handle1, handle2, t)
                    .normalize();
            Vec3 offset = curve.getNormal(t).cross(derivative).scale(15 / 16.0);
            Vec3 rail1 = center.add(offset).add(up),
                    rail2 = center.subtract(offset).add(up);
            float animation = info.valid ? 1 : 0, s = animation * 7 / 8f + 1 / 8f, width = animation / 16f + 1 / 16f;
            if (prev1 != null) {
                Vec3 mid1 = rail1.add(prev1).scale(.5), mid2 = rail2.add(prev2).scale(.5);
                lines.add(new Line(VecHelper.lerp(s, mid1, prev1), VecHelper.lerp(s, mid1, rail1), width));
                lines.add(new Line(VecHelper.lerp(s, mid2, prev2), VecHelper.lerp(s, mid2, rail2), width));
            }
            prev1 = rail1;
            prev2 = rail2;
        }
        return lines;
    }

    public static ItemStack display(String name, int color) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, CreateIds.id("ui/" + name));
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(color)));
        return stack;
    }

    public static void addLine(ElementHolder holder, Vec3 origin, Line line, int color) {
        Vec3 delta = line.to.subtract(line.from);
        if (delta.lengthSqr() < 1e-8) return;
        Vec3 unit = delta.normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(1, 0, 0, (float) unit.x, (float) unit.y, (float) unit.z);
        ItemDisplayElement item = new ItemDisplayElement(display("track_preview_line", color));
        item.setItemDisplayContext(ItemDisplayContext.NONE);
        item.setTransformation(new Matrix4f()
                .translate(line.from.subtract(origin).toVector3f())
                .rotate(rotation)
                .translate(0, -line.width / 2, -line.width / 2)
                .scale((float) delta.length(), line.width, line.width)
                .translate(.5f, .5f, .5f)
                .rotateY((float) Math.PI));
        item.setBrightness(new Brightness(15, 15));
        holder.addElement(item);
    }
}
