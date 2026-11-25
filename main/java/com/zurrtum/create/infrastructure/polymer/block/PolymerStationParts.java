package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.model;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.part;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.trains.observer.TrackObserverBlockEntity;
import com.zurrtum.create.content.trains.signal.SignalBlockEntity;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import com.zurrtum.create.content.trains.track.ITrackBlock;
import com.zurrtum.create.content.trains.track.TrackBlock;
import com.zurrtum.create.content.trains.track.TrackBlockEntity;
import com.zurrtum.create.content.trains.track.TrackTargetingBehaviour;
import dev.veeda.createpoly.assets.PackJson;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PolymerStationParts {
    private static final Identifier ON = model("track_station/flag_on"),
            OFF = model("track_station/flag_off"),
            ASSEMBLE = model("track_station/flag_assemble"),
            STATION = model("track_overlay/station"),
            SIGNAL = model("track_overlay/signal"),
            DUAL = model("track_overlay/signal_dual"),
            OBSERVER = model("track_overlay/observer"),
            BLUE = model("polymer/track_overlay/assembly_blue"),
            GREEN = model("polymer/track_overlay/assembly_green");
    private static final Map<StationBlockEntity, Flag> FLAGS = new WeakHashMap<>();

    private PolymerStationParts() {}

    private static final class Flag {
        final LerpedFloat value = LerpedFloat.linear().startWithValue(0);
        long tick = Long.MIN_VALUE;

        void update(StationBlockEntity be) {
            long now = be.getLevel().getGameTime();
            if (tick == now) return;
            tick = now;
            float target = value.getChaseTarget();
            if (target == 0 || value.settled()) {
                int next = be.trainPresent || be.isAssembling() ? 1 : 0;
                if (next != target) value.chase(next, .1f, LerpedFloat.Chaser.LINEAR);
            }
            value.tickChaser();
        }
    }

    static void register() {
        PolymerBlockParts.<StationBlockEntity>register(PolymerStationParts::station, AllBlocks.TRACK_STATION);
        PolymerBlockParts.<SignalBlockEntity>register(
                (be, state, time, out) -> {
                    if (be.getOverlay() == SignalBlockEntity.OverlayState.SKIP) return;
                    overlay(
                            be.edgePoint,
                            be.getBlockPos(),
                            be.getOverlay() == SignalBlockEntity.OverlayState.DUAL ? DUAL : SIGNAL,
                            out);
                },
                AllBlocks.TRACK_SIGNAL);
        PolymerBlockParts.<TrackObserverBlockEntity>register(
                (be, state, time, out) -> overlay(be.edgePoint, be.getBlockPos(), OBSERVER, out),
                AllBlocks.TRACK_OBSERVER);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> FLAGS.clear());
    }

    private static BlockState loadedState(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private static void station(StationBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        if (be.depotBehaviour != null) PolymerLogisticsParts.depotItems(be.depotBehaviour, new Matrix4f(), out);
        if (!(be.getLevel() instanceof ServerLevel level)) return;
        BlockPos target = be.edgePoint.getGlobalPosition();
        BlockState trackState = loadedState(level, target);
        if (trackState == null || !(trackState.getBlock() instanceof ITrackBlock track)) return;
        Flag flag = FLAGS.computeIfAbsent(be, key -> new Flag());
        flag.update(be);
        Vec3 axis = null;
        for (Vec3 candidate : track.getTrackAxes(level, target, trackState))
            axis = candidate.scale(be.edgePoint.getTargetDirection().getStep());
        if (axis == null) return;
        Direction nearest = Direction.getApproximateNearest(axis.x, 0, axis.z);
        int yaw = (int) (-nearest.toYRot() - 90);
        Vec3 difference =
                Vec3.atLowerCornerOf(target.subtract(be.getBlockPos())).multiply(1, 0, 1);
        boolean flipped =
                difference.dot(Vec3.atLowerCornerOf(nearest.getClockWise().getUnitVec3i())) > 0;
        float value = flag.value.getValue(1), progress = (float) Math.pow(Math.min(value * 5, 1), 2);
        if (flag.value.getChaseTarget() > 0 && !flag.value.settled() && progress == 1) {
            float wiggle = (value - .2f) / .8f;
            progress += (float) (Math.sin(wiggle * 2 * Math.PI * 4) / 8 / Math.max(1, 8 * wiggle));
        }
        var station = be.getStation();
        boolean assembling = be.isAssembling() && station != null && station.getPresentTrain() == null;
        Matrix4f pose = new Matrix4f()
                .translate(.5f, .5f, .5f)
                .rotateY(AngleHelper.rad(yaw))
                .translate(.001953125f, .59375f, flipped ? .875f - 1 / 512f : .125f + 1 / 512f)
                .translate(-.5f, -.5f, -.5f)
                .rotateX(AngleHelper.rad((flipped ? 1 : -1) * (progress * 90 + 270)));
        if (!flipped) pose.translate(.03125f, 0, 0).rotateY((float) Math.PI).translate(-.03125f, 0, 0);
        out.add(part(assembling ? ASSEMBLE : value > .75f ? ON : OFF, pose).identified("station:flag"));
        if (assembling) assembly(be, target, out);
        else overlay(be.edgePoint, be.getBlockPos(), STATION, out);
    }

    private static void assembly(StationBlockEntity be, BlockPos target, List<PolymerBlockParts.Part> out) {
        Direction direction = be.assemblyDirection;
        if (direction == null || be.assemblyLength <= 0 || be.bogeyLocations == null) return;
        BlockPos delta = target.subtract(be.getBlockPos());
        Matrix4f base = new Matrix4f()
                .translate(delta.getX(), delta.getY(), delta.getZ())
                .translate(.5f, .5f, .5f)
                .rotateY(AngleHelper.rad(AngleHelper.horizontalAngle(direction)))
                .translate(-.5f, -.5f, -.5f);
        for (int i = 0; i < be.assemblyLength; i++) {
            boolean bogey = false;
            for (int location : be.bogeyLocations) {
                if (location == -1 || location >= be.assemblyLength) break;
                if (location == i) {
                    bogey = true;
                    break;
                }
            }
            if (!bogey && !be.isValidBogeyOffset(i)) continue;
            out.add(part(bogey ? GREEN : BLUE, new Matrix4f(base).translate(0, 0, i + 1))
                    .identified("station:assembly:" + i));
        }
    }

    private static void overlay(
            TrackTargetingBehaviour<?> target, BlockPos owner, Identifier model, List<PolymerBlockParts.Part> out) {
        if (!(target.blockEntity.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = target.getGlobalPosition();
        BlockState state = loadedState(level, pos);
        if (state == null || !(state.getBlock() instanceof TrackBlock)) return;
        Vec3 axis = null, normal = null, diff = null;
        BlockPos delta = pos.subtract(owner);
        Matrix4f pose = new Matrix4f().translate(delta.getX(), delta.getY(), delta.getZ());
        var bezier = target.getTargetBezier();
        TrackBlockEntity trackBE = level.getBlockEntity(pos) instanceof TrackBlockEntity be ? be : null;
        if (bezier != null) {
            if (trackBE == null) return;
            var connection = trackBE.getConnections().get(bezier.curveTarget());
            if (connection == null) return;
            double length = Mth.floor(connection.getLength() * 2);
            if (length <= 0) return;
            int segment = bezier.segment() + 1;
            double t = segment / length;
            Vec3 offset = connection
                    .getPosition(t)
                    .subtract(Vec3.atBottomCenterOf(pos))
                    .add(0, -.25, 0);
            pose.translate((float) offset.x, (float) offset.y, (float) offset.z);
            normal = connection.getNormal(t);
            diff = connection
                    .getPosition((segment + 1) / length)
                    .subtract(connection.getPosition((segment - 1) / length))
                    .normalize();
        }
        if (normal == null) {
            var shape = state.getValue(TrackBlock.SHAPE);
            if (shape.getAxes().isEmpty()) return;
            axis = shape.getAxes().getFirst();
            normal = shape.getNormal();
            diff = axis.scale(target.getTargetDirection().getStep()).normalize();
        }
        float yaw = (float) Mth.atan2(diff.x, diff.z);
        float pitch = (float) (Mth.atan2(Mth.sqrt((float) (diff.x * diff.x + diff.z * diff.z)), diff.y) - Math.PI * .5);
        pose.translate(.5f, .5f, .5f).rotateY(yaw).rotateX(pitch).translate(-.5f, -.5f, -.5f);
        int step = target.getTargetDirection().getStep();
        if (axis != null) {
            if (axis.y != 0) pose.translate(0, 7 / 16f, step * 2.5f / 16);
        } else {
            pose.translate(0, .25f, 0);
            if (step < 0) pose.translate(.5f, .5f, .5f).rotateY((float) Math.PI).translate(-.5f, -.5f, -.5f);
        }
        if (bezier == null && trackBE != null && trackBE.isTilted()) {
            double yOffset = 0;
            for (var connection : trackBE.getConnections().values())
                yOffset += connection.starts.getFirst().y - pos.getY();
            pose.translate(.5f, .5f, .5f)
                    .rotateX(AngleHelper.rad((float) (-step * trackBE.tilt.smoothingAngle.get())))
                    .translate(-.5f, -.5f, -.5f)
                    .translate(0, (float) (yOffset / 2), 0);
        }
        out.add(part(model, pose).identified("track:target"));
    }

    public static void addAssets(ResourcePackBuilder builder) {
        try {
            byte[] original = builder.getDataOrSource("assets/create/models/block/track_overlay/assembling.json");
            byte[] texture = builder.getDataOrSource("assets/create/textures/block/track_assembly_overlay.png");
            if (original == null || texture == null) throw new IllegalStateException("Source assembly overlay missing");
            var image = PngCodec.decode(texture);
            for (int color : new int[] {0x96B5FF, 0xCAFF96}) {
                String name = color == 0x96B5FF ? "assembly_blue" : "assembly_green";
                var tinted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < image.getHeight(); y++)
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getRGB(x, y);
                        int r = ((pixel >>> 16) & 255) * ((color >>> 16) & 255) / 255,
                                g = ((pixel >>> 8) & 255) * ((color >>> 8) & 255) / 255,
                                b = (pixel & 255) * (color & 255) / 255;
                        tinted.setRGB(x, y, (pixel & 0xFF000000) | (r << 16) | (g << 8) | b);
                    }
                builder.addData(
                        "assets/create/textures/block/polymer/track_overlay/" + name + ".png", PngCodec.encode(tinted));
                var model = PackJson.object(original);
                model.getAsJsonObject("textures").addProperty("3", "create:block/polymer/track_overlay/" + name);
                builder.addStringData(
                        "assets/create/models/block/polymer/track_overlay/" + name + ".json", model.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot generate source assembly colors", e);
        }
    }
}
