package com.zurrtum.create.infrastructure.polymer.train;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.catnip.math.VecHelper;
import com.zurrtum.create.content.decoration.copycat.CopycatPanelBlock;
import com.zurrtum.create.content.trains.track.BezierConnection;
import com.zurrtum.create.content.trains.track.BezierConnection.Segment;
import com.zurrtum.create.content.trains.track.TrackBlock;
import com.zurrtum.create.content.trains.track.TrackBlockEntity;
import com.zurrtum.create.content.trains.track.TrackShape;
import com.zurrtum.create.infrastructure.polymer.AdapterHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerDisplays;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerCopycatParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDecorativeCulling;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerBlockCollision;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerStaticCollision;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.blocks.impl.BlockExtBlockMapper;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PolymerTrackVisuals {
    private static final Identifier TIE_MODEL = CreateIds.id("polymer/track_tie");
    private static final Identifier LEFT_RAIL_MODEL = CreateIds.id("polymer/track_segment_left");
    private static final Identifier RIGHT_RAIL_MODEL = CreateIds.id("polymer/track_segment_right");
    private static final float RAIL_OFFSET = 0.965f;
    private static final float MODEL_DROP = -0.125f - 1 / 256.0f;
    private static final double TRACK_HEIGHT = 3 / 16.0;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    public static final Set<TrackShape> DRAWN_SHAPES = Set.of(
            TrackShape.ZO,
            TrackShape.XO,
            TrackShape.CR_O,
            TrackShape.AN,
            TrackShape.AS,
            TrackShape.AE,
            TrackShape.AW,
            TrackShape.PD,
            TrackShape.ND,
            TrackShape.CR_D,
            TrackShape.CR_PDX,
            TrackShape.CR_PDZ,
            TrackShape.CR_NDX,
            TrackShape.CR_NDZ);

    private static final Map<TrackBlockEntity, Curves> CURVES = new IdentityHashMap<>();

    private PolymerTrackVisuals() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CURVES.values().forEach(Curves::destroy);
            CURVES.clear();
        });
    }

    public static boolean isDrawnShape(BlockState state) {
        return state.getBlock() instanceof TrackBlock && DRAWN_SHAPES.contains(state.getValue(TrackBlock.SHAPE));
    }

    public static void tick(TrackBlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level) || blockEntity.isRemoved()) {
            remove(blockEntity);
            return;
        }

        List<BezierConnection> primaries = new ArrayList<>();
        for (BezierConnection connection : blockEntity.getConnections().values()) {
            if (connection.isPrimary()) {
                primaries.add(connection);
            }
        }

        Curves curves = CURVES.get(blockEntity);
        if (curves != null && curves.matches(primaries)) {
            return;
        }
        if (curves != null) {
            curves.destroy();
            CURVES.remove(blockEntity);
        }
        if (primaries.isEmpty()) {
            return;
        }
        CURVES.put(blockEntity, new Curves(level, blockEntity.getBlockPos(), primaries));
    }

    public static void remove(TrackBlockEntity blockEntity) {
        Curves curves = CURVES.remove(blockEntity);
        if (curves != null) {
            curves.destroy();
        }
    }

    private static final class Curves {
        private final ElementHolder holder = new AdapterHolder();
        private final List<BezierConnection> source;

        private Curves(ServerLevel level, BlockPos bePos, List<BezierConnection> connections) {
            this.source = List.copyOf(connections);
            for (BezierConnection connection : connections) {
                addConnection(holder, bePos, connection);
            }
            ChunkAttachment.ofTicking(holder, level, bePos);
        }

        private boolean matches(List<BezierConnection> connections) {
            if (connections.size() != source.size()) {
                return false;
            }
            for (int i = 0; i < connections.size(); i++) {
                if (connections.get(i) != source.get(i)) {
                    return false;
                }
            }
            return true;
        }

        private void destroy() {
            holder.destroy();
        }
    }

    private static void addConnection(ElementHolder holder, BlockPos bePos, BezierConnection connection) {
        if (connection.getSegmentCount() == 0) {
            return;
        }
        BlockPos first = connection.bePositions.getFirst();
        Vec3 base = new Vec3(first.getX() - bePos.getX(), first.getY() - bePos.getY(), first.getZ() - bePos.getZ());
        List<Vec3> positions = new ArrayList<>();
        List<Vec3> normals = new ArrayList<>();
        Iterator<Segment> iterator = connection.iterator();
        while (iterator.hasNext()) {
            Segment segment = iterator.next();
            positions.add(base.add(segment.position));
            normals.add(segment.normal);
        }
        addTrack(holder, positions, normals);
    }

    public static BlockWithElementHolder shapeHolderProvider() {
        return new BlockWithElementHolder() {
            @Override
            public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                ShapeHolder holder = new ShapeHolder(level, pos);
                holder.show(state);
                return holder;
            }

            @Override
            public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return true;
            }
        };
    }

    private static final class ShapeHolder extends AdapterHolder {
        private BlockState shownState;
        private final ServerLevel level;
        private final BlockPos pos;

        ShapeHolder(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }

        @Override
        public void notifyUpdate(HolderAttachment.UpdateType updateType) {
            super.notifyUpdate(updateType);
            if (updateType == BlockAwareAttachment.BLOCK_STATE_UPDATE) {
                BlockAwareAttachment attachment = BlockAwareAttachment.get(this);
                if (attachment != null) {
                    show(attachment.getBlockState());
                }
            }
        }

        private void show(BlockState state) {
            TrackShape shape =
                    state.getBlock() instanceof TrackBlock ? state.getValue(TrackBlock.SHAPE) : TrackShape.NONE;
            if (!DRAWN_SHAPES.contains(shape)) {
                shape = TrackShape.NONE;
            }
            if (state == shownState) {
                return;
            }
            for (VirtualElement element : List.copyOf(getElements())) {
                removeElement(element);
            }
            shownState = state;
            PolymerStaticCollision.add(this, level, pos, state);
            for (Vec3 axis : shape.getAxes()) {
                addStraightPiece(this, axis);
            }
            drawn = shape != TrackShape.NONE;
            skin = null;
            skinGround = null;
            snowEdges.clear();
            shownSnow = List.of();
        }

        private boolean drawn;
        private ItemDisplayElement skin;
        private BlockState skinGround;
        private final List<VirtualElement> snowEdges = new ArrayList<>();
        private List<BlockState> shownSnow = List.of();

        // Runs on the server tick, never during chunk construction: reading the block below while this chunk is
        // still being built waits on that same chunk and deadlocks.
        @Override
        protected void onTick() {
            super.onTick();
            if (!drawn || shownState == null) {
                return;
            }
            refreshSnowEdges();
            BlockState ground = level.getBlockState(pos.below());
            if (ground == skinGround) {
                return;
            }
            skinGround = ground;
            if (skin != null) {
                removeElement(skin);
                skin = null;
            }
            skin = groundSkin(shownState, ground);
            if (skin != null) {
                addElement(skin);
            }
        }

        private void refreshSnowEdges() {
            List<Direction> sides = List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
            List<BlockState> neighbours = sides.stream()
                    .map(side -> level.getBlockState(pos.relative(side)))
                    .toList();
            if (neighbours.equals(shownSnow)) return;
            shownSnow = neighbours;
            snowEdges.forEach(this::removeElement);
            snowEdges.clear();
            BlockState carrier = BlockExtBlockMapper.INSTANCE.toClientSideState(shownState, null);
            for (int i = 0; i < sides.size(); i++) {
                BlockState snow = neighbours.get(i);
                Direction side = sides.get(i);
                if (!snow.is(Blocks.SNOW)
                        || Block.shouldRenderFace(snow, carrier, side.getOpposite())
                        || !Block.shouldRenderFace(snow, shownState, side.getOpposite())) continue;
                float height = snow.getValue(SnowLayerBlock.LAYERS) / 8f;
                float thickness = 1 / 4096f;
                Vector3f offset = Vec3.atLowerCornerOf(pos.relative(side))
                        .subtract(Vec3.atCenterOf(pos))
                        .toVector3f();
                Matrix4f pose = new Matrix4f().translate(offset);
                if (side.getAxis() == Direction.Axis.X) {
                    pose.translate(side == Direction.WEST ? 1 - thickness : 0, 0, 0)
                            .scale(thickness, height, 1);
                } else {
                    pose.translate(0, 0, side == Direction.NORTH ? 1 - thickness : 0)
                            .scale(1, height, thickness);
                }
                var edge = new BlockDisplayElement(Blocks.SNOW_BLOCK.defaultBlockState());
                edge.setTransformation(pose);
                PolymerDecorativeCulling.inBlock(edge);
                addElement(edge);
                snowEdges.add(edge);
            }
        }

        private ItemDisplayElement groundSkin(BlockState above, BlockState ground) {
            BlockPos groundPos = pos.below();
            if (!PolymerCopycatParts.hasMaterialMesh(ground)
                    && ground.is(Blocks.GRASS_BLOCK)
                    && ground.getValue(BlockStateProperties.SNOWY))
                ground = ground.setValue(BlockStateProperties.SNOWY, false);
            if (!ground.isSolidRender() || !PolymerCopycatParts.hasMaterialMesh(ground)) {
                return null;
            }
            BlockState nativeAbove = BlockExtBlockMapper.INSTANCE.toClientSideState(above, null);
            BlockState nativeGround = BlockExtBlockMapper.INSTANCE.toClientSideState(ground, null);
            if (Block.shouldRenderFace(nativeGround, nativeAbove, Direction.UP)) {
                return null;
            }
            BlockState panel =
                    AllBlocks.COPYCAT_PANEL.defaultBlockState().setValue(CopycatPanelBlock.FACING, Direction.UP);
            float top = (float) PolymerBlockCollision.shape(panel).bounds().maxY;
            float thickness = .0001f;
            Matrix4f pose =
                    new Matrix4f().translate(.5f, (.5f - top) * thickness, .5f).scale(1, thickness, 1);
            ItemDisplayElement element = PolymerDisplays.item(
                    PolymerCopycatParts.displayItem(panel, ground, level, groundPos), ItemDisplayContext.NONE);
            element.setTransformation(PolymerBlockParts.itemDisplayTransform(pose));
            PolymerDecorativeCulling.inBlock(element);
            return element;
        }
    }

    private static final Identifier STRAIGHT_TRACK = PolymerBlockParts.model("track/z_ortho");

    private static void addStraightPiece(ElementHolder holder, Vec3 axis) {
        double horizontal = Math.sqrt(axis.x * axis.x + axis.z * axis.z);
        Matrix4f pose = new Matrix4f()
                .translate(0.5f, (float) (axis.y / 2), 0.5f)
                .rotateY((float) Math.atan2(axis.x, axis.z))
                .rotateX((float) -Math.atan2(axis.y, horizontal))
                .scale(1, 1, (float) axis.length())
                .translate(-0.5f, 0, -0.5f);
        Matrix4f transform = PolymerBlockParts.displayTransform(pose);
        ItemDisplayElement element =
                PolymerDisplays.item(PolymerBlockParts.displayItem(STRAIGHT_TRACK), ItemDisplayContext.NONE);
        element.setTransformation(transform);
        PolymerDecorativeCulling.sized(element, 2.5f, 1.5f);
        holder.addElement(element);
    }

    private static void addTrack(ElementHolder holder, List<Vec3> positions, List<Vec3> normals) {
        int length = positions.size() - 1;
        if (length < 1) {
            return;
        }
        Vec3 previousLeft = positions.get(0).add(normals.get(0).scale(RAIL_OFFSET));
        Vec3 previousRight = positions.get(0).subtract(normals.get(0).scale(RAIL_OFFSET));
        for (int index = 1; index <= length; index++) {
            Vec3 position = positions.get(index);
            Vec3 normal = normals.get(index);
            Vec3 left = position.add(normal.scale(RAIL_OFFSET));
            Vec3 right = position.subtract(normal.scale(RAIL_OFFSET));
            Vec3 middle = left.add(right).scale(0.5);
            Vec3 previousMiddle = previousLeft.add(previousRight).scale(0.5);

            Vec3 tieAngles = modelAngles(normal, middle.subtract(previousMiddle));
            addPart(holder, TIE_MODEL, pose(previousMiddle, tieAngles).translate(-0.5f, MODEL_DROP, 0), TIE_BOUNDS);

            float scale = index == length ? 2.2f : 2.1f;
            addRail(holder, LEFT_RAIL_MODEL, previousLeft, left, normal, scale);
            addRail(holder, RIGHT_RAIL_MODEL, previousRight, right, normal, scale);

            previousLeft = left;
            previousRight = right;
        }
    }

    private static void addRail(ElementHolder holder, Identifier model, Vec3 from, Vec3 to, Vec3 normal, float scale) {
        Vec3 diff = to.subtract(from);
        Matrix4f rail = pose(from, modelAngles(normal, diff))
                .translate(0, MODEL_DROP, -0.03125f)
                .scale(1, 1, (float) diff.length() * scale);
        addPart(holder, model, rail, RAIL_BOUNDS);
    }

    private static final AABB TIE_BOUNDS = new AABB(-13.95 / 16, -3.056 / 16, 0, 29.95 / 16, 1.25 / 16, 8 / 16.0);
    private static final AABB RAIL_BOUNDS = new AABB(-2.1 / 16, 0, 0, 2.1 / 16, 4.45 / 16, 8 / 16.0);

    private static void addPart(ElementHolder holder, Identifier model, Matrix4f pose, AABB bounds) {
        Matrix4f transform = PolymerBlockParts.displayTransform(pose);
        ItemDisplayElement element =
                PolymerDisplays.item(PolymerKineticVisuals.rotatingDisplayItem(model), ItemDisplayContext.NONE);
        if (PolymerDecorativeCulling.ENABLED) {
            Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = pose.transformPosition(new Vector3f(
                        (float) ((corner & 1) == 0 ? bounds.minX : bounds.maxX),
                        (float) ((corner & 2) == 0 ? bounds.minY : bounds.maxY),
                        (float) ((corner & 4) == 0 ? bounds.minZ : bounds.maxZ)));
                min.min(point);
                max.max(point);
            }
            Vector3f track = pose.transformPosition(new Vector3f((float) bounds.getCenter().x, -MODEL_DROP, 0));
            double block = Math.floor(track.y + 0.25);
            Vec3 anchor = new Vec3(
                    (min.x + max.x) / 2, Mth.clamp(min.y + 0.5, block + 1e-3, block + 1 - 1e-3), (min.z + max.z) / 2);
            Vec3 offset = anchor.subtract(0.5, 0.5, 0.5);
            transform = PolymerDecorativeCulling.anchored(element, offset, transform);
            float half = (float) Math.max(
                    Math.max(anchor.x - min.x, max.x - anchor.x), Math.max(anchor.z - min.z, max.z - anchor.z));
            PolymerDecorativeCulling.sized(element, 2 * half, (float) Math.max(max.y - anchor.y, 0.25));
        }
        element.setTransformation(transform);
        holder.addElement(element);
    }

    private static Matrix4f pose(Vec3 position, Vec3 angles) {
        return new Matrix4f()
                .translate((float) position.x, (float) position.y, (float) position.z)
                .rotateY((float) angles.y)
                .rotateX((float) angles.x)
                .rotateZ((float) angles.z);
    }

    private static Vec3 modelAngles(Vec3 normal, Vec3 diff) {
        double diffX = diff.x();
        double diffY = diff.y();
        double diffZ = diff.z();
        double len = Mth.sqrt((float) (diffX * diffX + diffZ * diffZ));
        double yaw = Mth.atan2(diffX, diffZ);
        double pitch = Mth.atan2(len, diffY) - Math.PI * 0.5;

        Vec3 yawPitchNormal = VecHelper.rotate(
                VecHelper.rotate(new Vec3(0, 1, 0), AngleHelper.deg(pitch), Direction.Axis.X),
                AngleHelper.deg(yaw),
                Direction.Axis.Y);

        double signum = Math.signum(yawPitchNormal.dot(normal));
        if (Math.abs(signum) < 0.5f) {
            signum = yawPitchNormal.distanceToSqr(normal) < 0.5f ? -1 : 1;
        }
        double dot = diff.cross(normal).normalize().dot(yawPitchNormal);
        double roll = Math.acos(Mth.clamp(dot, -1, 1)) * signum;
        return new Vec3(pitch, yaw, roll);
    }
}
