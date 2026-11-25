package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.itemPart;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.model;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.part;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.progress;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotationOffset;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.data.Pair;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.zurrtum.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.zurrtum.create.content.kinetics.crafter.MechanicalCrafterBlockEntity.Phase;
import com.zurrtum.create.content.kinetics.crafter.RecipeGridHandler.GroupedItems;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PolymerCrafterParts {
    private static final Identifier COG = model("polymer/cogwheel_shaftless"),
            LID = model("mechanical_crafter/lid"),
            ARROW = model("mechanical_crafter/arrow"),
            FRAME = model("mechanical_crafter/belt");
    private static final Identifier[] BELT = {
        model("polymer/crafter_belt_0"),
        model("polymer/crafter_belt_1"),
        model("polymer/crafter_belt_2"),
        model("polymer/crafter_belt_3")
    };
    private static final Map<MechanicalCrafterBlockEntity, Craft> CRAFTS = new WeakHashMap<>();
    static final Map<MechanicalCrafterBlockEntity, GroupedItems> PENDING_BEFORE_CRAFT = new WeakHashMap<>();

    public static void captureBeforeCraft(MechanicalCrafterBlockEntity be, GroupedItems items) {
        PENDING_BEFORE_CRAFT.put(be, items);
    }

    private static final class Craft {
        Map<Pair<Integer, Integer>, ItemStack> before = Map.of();
        int minX, minY, width, height;
        boolean burst;
        long particleTick = Long.MIN_VALUE;
    }

    private PolymerCrafterParts() {}

    static void register() {
        PolymerBlockParts.<MechanicalCrafterBlockEntity>register(
                PolymerCrafterParts::crafter, AllBlocks.MECHANICAL_CRAFTER);
    }

    private static void crafter(MechanicalCrafterBlockEntity be, BlockState state, float time, List<Part> out) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Axis axis = facing.getAxis();

        float angle = progress(be.getSpeed(), time) + rotationOffset(state, axis, be.getBlockPos());
        Matrix4f cog = PolymerBlockParts.spin(axis, angle);
        if (axis == Axis.X) cog.mul(PolymerBlockParts.rotateCentered(new Quaternionf().rotationY(Mth.HALF_PI)));
        cog.mul(PolymerBlockParts.rotateCentered(new Quaternionf().rotationX(Mth.HALF_PI)));
        out.add(part(COG, cog).identified("crafter:cog"));

        Matrix4f shell = PolymerBlockParts.rotateCentered(
                new Quaternionf().rotationY(Mth.DEG_TO_RAD * (AngleHelper.horizontalAngle(facing) + 90)));
        int pointing = state.getValue(MechanicalCrafterBlock.POINTING).getXRotation();
        if (pointing != 0)
            shell.mul(PolymerBlockParts.rotateCentered(new Quaternionf().rotationX(Mth.DEG_TO_RAD * pointing)));
        Phase phase = be.phase;
        if ((be.covered || phase != Phase.IDLE) && phase.ordinal() < Phase.CRAFTING.ordinal())
            out.add(part(LID, new Matrix4f(shell)).identified("crafter:lid"));
        Direction target = MechanicalCrafterBlock.getTargetDirection(state);
        if (be.getLevel() != null
                && MechanicalCrafterBlock.isValidTarget(
                        be.getLevel(), be.getBlockPos().relative(target), state)) {
            int frame = phase == Phase.EXPORTING ? Math.floorMod((int) (be.getCountDownSpeed() / 128.0f * time), 4) : 0;
            out.add(part(BELT[frame], new Matrix4f(shell)).identified("crafter:belt"));
            out.add(part(FRAME, new Matrix4f(shell)).identified("crafter:frame"));
        } else {
            out.add(part(ARROW, new Matrix4f(shell)).identified("crafter:arrow"));
        }

        items(be, state, facing, time, out);
    }

    private static void items(
            MechanicalCrafterBlockEntity be, BlockState state, Direction facing, float time, List<Part> out) {
        Phase phase = be.phase;
        Craft craft = CRAFTS.get(be);
        if (phase != Phase.CRAFTING) {
            CRAFTS.remove(be);
            craft = null;
        } else if (craft == null) {
            craft = new Craft();
            GroupedItems before = be.groupedItemsBeforeCraft;
            if (before == null) before = PENDING_BEFORE_CRAFT.remove(be);
            else PENDING_BEFORE_CRAFT.remove(be);
            if (before != null && !before.grid.isEmpty()) {
                before.calcStats();
                craft.before = copy(before.grid);
                craft.minX = before.minX;
                craft.minY = before.minY;
                craft.width = before.width;
                craft.height = before.height;
            }
            CRAFTS.put(be, craft);
        }

        Vec3 vec = Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(0.58).add(0.5, 0.5, 0.5);
        if (phase == Phase.EXPORTING) {
            Direction target = MechanicalCrafterBlock.getTargetDirection(state);
            float progress = Mth.clamp((1000 - be.countDown) / 1000.0f, 0, 1);
            vec = vec.add(Vec3.atLowerCornerOf(target.getUnitVec3i()).scale(progress * 0.75f));
        }
        Matrix4f base = new Matrix4f()
                .translate((float) vec.x, (float) vec.y, (float) vec.z)
                .scale(0.5f);
        float yRot = AngleHelper.horizontalAngle(facing);
        if (yRot != 0) base.rotateY(Mth.DEG_TO_RAD * yRot);

        if (phase == Phase.IDLE) {
            ItemStack stack = be.getInventory().getStack();
            if (!stack.isEmpty())
                out.add(item(
                        stack, new Matrix4f(base).translate(0, 0, -1 / 256f).rotateY(Mth.PI), "crafter:item"));
            return;
        }
        if (phase == Phase.CRAFTING) {
            crafting(be, craft, base, time, out);
            return;
        }
        Map<Pair<Integer, Integer>, ItemStack> grid = be.groupedItems.grid;
        if (grid.isEmpty()) return;
        float offset = 0;
        if (phase == Phase.EXPORTING)
            offset = switch (state.getValue(MechanicalCrafterBlock.POINTING)) {
                case UP -> -9 / 1024f;
                case LEFT -> 18 / 1024f;
                case RIGHT -> -18 / 1024f;
                case DOWN -> 9 / 1024f;
            };
        grid(grid, base, 0.5f, offset, phase == Phase.INSERTING, "crafter:grid:", out);
    }

    private static void crafting(
            MechanicalCrafterBlockEntity be, Craft craft, Matrix4f base, float time, List<Part> out) {
        float value = be.countDown;
        if (!craft.before.isEmpty()) {
            float progress = Mth.clamp((2000 - value) / 1000.0f, 0, 1);
            float early = Mth.clamp(progress * 2, 0, 1);
            float scale = 1 - Mth.clamp(progress * 2 - 1, 0, 1);
            float cx = (-craft.minX + (-craft.width + 1) / 2.0f) * early * 0.5f;
            float cy = (-craft.minY + (-craft.height + 1) / 2.0f) * early * 0.5f;
            float distance = 0.5f + (-4 * (progress - 0.5f) * (progress - 0.5f) + 1) * 0.25f;
            if (scale > 0) {
                Matrix4f before = new Matrix4f(base).scale(scale).translate(cx, cy, 0);
                grid(craft.before, before, distance, 0, be.countDown < 1000, "crafter:before:", out);
            }
        }
        Map<Pair<Integer, Integer>, ItemStack> result = be.groupedItems.grid;
        ItemStack first = result.get(Pair.of(0, 0));
        if (first != null && !first.isEmpty()) {
            float progress = Mth.clamp((1000 - value) / 1000.0f, 0, 1);
            float early = Mth.clamp(progress * 2, 0, 1);
            float up = early * 1.125f;
            float down = 1 + (1 - Mth.clamp(progress * 2 - 1, 0, 1)) * 0.125f;
            if (up > 0) {
                Matrix4f pose = new Matrix4f(base)
                        .rotateZ(Mth.DEG_TO_RAD * early * 720)
                        .scale(up * down)
                        .rotateY(Mth.PI);
                out.add(item(first, pose, "crafter:result"));
            }
        }
        particles(be, craft, first, (long) time);
    }

    private static void particles(MechanicalCrafterBlockEntity be, Craft craft, ItemStack result, long tick) {
        if (!(be.getLevel() instanceof ServerLevel level) || craft.particleTick == tick) return;
        craft.particleTick = tick;
        Direction facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        Vec3 facingVec = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 centre = facingVec.scale(0.65).add(Vec3.atCenterOf(be.getBlockPos()));
        float progress = be.countDown / 2000.0f;
        if (progress > 0.5f) {
            Vec3 at = jitter(level, facingVec).scale(progress * 0.5f).add(centre);
            PolymerVanillaEffects.sourceParticles(level, ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        if (!craft.burst && progress < 0.5f && result != null && !result.isEmpty()) {
            craft.burst = true;
            ItemParticleOption option =
                    new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(result));
            for (int i = 0; i < 10; i++) {
                Vec3 rand = jitter(level, facingVec).scale(0.25f);
                Vec3 at = centre.add(rand);
                Vec3 motion = rand.add(facingVec.scale(0.25f)).scale(0.25f);
                PolymerVanillaEffects.sourceParticles(
                        level, option, at.x, at.y, at.z, 0, motion.x, motion.y, motion.z, 1);
            }
        }
    }

    private static Vec3 jitter(ServerLevel level, Vec3 facingVec) {
        var random = level.getRandom();
        Vec3 offset = new Vec3(
                (random.nextFloat() - 0.5f) * 0.25f,
                (random.nextFloat() - 0.5f) * 0.25f,
                (random.nextFloat() - 0.5f) * 0.25f);
        Vec3 plane = new Vec3(1 - Math.abs(facingVec.x), 1 - Math.abs(facingVec.y), 1 - Math.abs(facingVec.z));
        Vec3 v = offset.multiply(plane);
        return v.lengthSqr() < 1e-6 ? Vec3.ZERO : v.normalize();
    }

    private static void grid(
            Map<Pair<Integer, Integer>, ItemStack> grid,
            Matrix4f base,
            float distance,
            float offset,
            boolean onlyFirst,
            String key,
            List<Part> out) {
        for (Map.Entry<Pair<Integer, Integer>, ItemStack> entry : grid.entrySet()) {
            int x = entry.getKey().getFirst(), y = entry.getKey().getSecond();
            if (onlyFirst && (x != 0 || y != 0)) continue;
            if (entry.getValue().isEmpty()) continue;
            float tx = onlyFirst ? 0 : x * distance, ty = onlyFirst ? 0 : y * distance;
            float tz = (onlyFirst ? 0 : (x + y * 3) / 1024f) + offset;
            Matrix4f pose =
                    new Matrix4f(base).translate(tx, ty, 0).rotateY(Mth.PI).translate(0, 0, tz);
            out.add(item(entry.getValue(), pose, key + x + "," + y));
        }
    }

    private static Part item(ItemStack stack, Matrix4f pose, String key) {
        return itemPart(stack, pose, ItemDisplayContext.FIXED).identified(key);
    }

    private static Map<Pair<Integer, Integer>, ItemStack> copy(Map<Pair<Integer, Integer>, ItemStack> grid) {
        Map<Pair<Integer, Integer>, ItemStack> copy = new HashMap<>();
        grid.forEach((k, v) -> copy.put(k, v.copy()));
        return copy;
    }

    public static void addAssets(ResourcePackBuilder builder) {
        for (int frame = 0; frame < 4; frame++) {
            String json =
                    "{\"parent\":\"block/block\",\"textures\":{\"3\":\"create:block/crafter_thingies\",\"particle\":\"#3\"},"
                            + "\"elements\":[{\"from\":[-0.5,12,6],\"to\":[0.5,20,10],\"faces\":{\"west\":{\"uv\":["
                            + (4 * frame) + ",6," + (4 * frame + 4) + ",14],\"texture\":\"#3\"}}}]}";
            builder.addStringData("assets/create/models/block/polymer/crafter_belt_" + frame + ".json", json);
        }
    }
}
