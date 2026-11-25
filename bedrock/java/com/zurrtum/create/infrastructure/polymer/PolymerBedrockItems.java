package com.zurrtum.create.infrastructure.polymer;

import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PolymerBedrockItems {
    /** UUID most significant bits: ASCII "ppitemhd" (bridge ItemHolderEntities.MARKER). */
    static final long MARKER = 0x70706974656d6864L;

    private static final int REFRESH_TICKS = 10;
    private static final float YAW_EPSILON = 0.5f;

    private static boolean warnedShear;

    private static long respawns, reportedRespawns, lastReport;
    private static final java.util.Map<String, Integer> RESPAWN_CAUSES = new java.util.HashMap<>();

    private static void countRespawn(long gameTime, String cause, BlockPos pos, ItemStack was, ItemStack now) {
        respawns++;
        RESPAWN_CAUSES.merge(
                cause + " at " + pos.toShortString() + " " + was.getItem() + " -> " + now.getItem(), 1, Integer::sum);
        if (gameTime - lastReport < 1200) return;
        lastReport = gameTime;
        com.zurrtum.create.Create.LOGGER.info(
                "Bedrock item holders: {} respawns since the last report ({} total); top causes {}",
                respawns - reportedRespawns,
                respawns,
                RESPAWN_CAUSES.entrySet().stream()
                        .sorted(java.util.Map.Entry.<String, Integer>comparingByValue()
                                .reversed())
                        .limit(8)
                        .map(e -> e.getValue() + "x " + e.getKey())
                        .toList());
        RESPAWN_CAUSES.clear();
        reportedRespawns = respawns;
    }

    private static final long EULER_BITS = (1L << 27) - 1;

    /** System property key under which the Geyser bridge (same JVM) registers its live pose updater. */
    static final String POSE_HOOK_KEY = "polymerpatch.itemHolderPose";

    /**
     * The bridge's live pose updater: (viewer UUID, [java entity id, rx, ry, rz]). Registered by the bridge as a plain
     * JDK type in the system properties, so neither side needs the other's classes. Null without the bridge.
     */
    @SuppressWarnings("unchecked")
    private static java.util.function.@org.jspecify.annotations.Nullable BiConsumer<UUID, int[]> poseHook() {
        return System.getProperties().get(POSE_HOOK_KEY) instanceof java.util.function.BiConsumer<?, ?> hook
                ? (java.util.function.BiConsumer<UUID, int[]>) hook
                : null;
    }

    private PolymerBedrockItems() {}

    public static final class Holder {
        private final Set<ServerPlayer> viewers = new HashSet<>();
        private final int refreshPhase;
        private int entityId = -1;
        private UUID uuid;
        private ItemStack item = ItemStack.EMPTY;
        private long pose = Long.MIN_VALUE;
        private float scale = Float.NaN;
        private float yaw;
        private Vec3 at = Vec3.ZERO;

        public Holder(BlockPos pos) {
            this.refreshPhase = Math.floorMod(pos.hashCode(), REFRESH_TICKS);
        }

        public void update(
                ServerLevel level,
                BlockPos pos,
                ItemStack stack,
                Matrix4f transform,
                ItemDisplayContext context,
                long gameTime) {
            update(level, pos, stack, transform, context, gameTime, renderKind(stack));
        }

        public void update(
                ServerLevel level,
                BlockPos pos,
                ItemStack stack,
                Matrix4f transform,
                ItemDisplayContext context,
                long gameTime,
                int kind) {
            Vector3f translation = transform.getTranslation(new Vector3f());
            Vector3f scales = transform.getScale(new Vector3f());
            float s = Math.round((scales.x + scales.y + scales.z) / 3f * 1000f) / 1000f;
            float spread = Math.max(Math.abs(scales.x - s), Math.max(Math.abs(scales.y - s), Math.abs(scales.z - s)));
            if (!warnedShear && spread > 0.01f * s) {
                warnedShear = true;
                com.zurrtum.create.Create.LOGGER.warn(
                        "Bedrock item holder: non-uniform part scale {} at {} drawn uniform {}", scales, pos, s);
            }
            Quaternionf rotation =
                    transform.getUnnormalizedRotation(new Quaternionf()).normalize();
            float yawRad = yawOf(rotation);
            Quaternionf residual = new Quaternionf().rotationY(-yawRad).mul(rotation);
            long p = encodePose(bedrockEuler(residual), kind, contextCode(context));
            Object v2 = System.getProperties().get(PolymerBedrockItemPose.HOOK);
            java.util.function.BiConsumer<UUID, int[]> fullHook =
                    v2 instanceof java.util.function.BiConsumer<?, ?> value
                            ? (java.util.function.BiConsumer<UUID, int[]>) value
                            : null;
            int image = fullHook != null && s >= 0 && s <= 65.535f && spread <= 0.01f * Math.max(s, 0.001f)
                    ? PolymerBedrockItemIcons.icon(stack, context)
                    : 0;
            p = PolymerBedrockItemPose.withIcon(p, image, Math.max(0, Math.min(65535, Math.round(s * 1000))));
            boolean scaleModeChanged = PolymerBedrockItemPose.hasIcon(pose) != PolymerBedrockItemPose.hasIcon(p);
            float y = (float) -Math.toDegrees(yawRad);
            Vec3 where = new Vec3(pos.getX() + translation.x, pos.getY() + translation.y, pos.getZ() + translation.z);
            boolean sameStack = entityId >= 0 && ItemStack.isSameItemSameComponents(stack, item);
            java.util.function.BiConsumer<UUID, int[]> hook = fullHook == null ? poseHook() : fullHook;
            long fixedBits = fullHook == null ? ~EULER_BITS : 15L << 27;
            boolean reposed = sameStack && p != pose && (p & fixedBits) == (pose & fixedBits) && hook != null;
            boolean respawn = !sameStack || p != pose && !reposed;
            if (reposed) {
                pose = p;
                int[] data = fullHook == null
                        ? new int[] {entityId, (int) (p & 0x1ff), (int) (p >>> 9 & 0x1ff), (int) (p >>> 18 & 0x1ff)}
                        : new int[] {
                            entityId,
                            (int) (p & 0x1ff),
                            (int) (p >>> 9 & 0x1ff),
                            (int) (p >>> 18 & 0x1ff),
                            image,
                            (int) (p >>> 43 & 65535)
                        };
                for (ServerPlayer player : viewers) hook.accept(player.getUUID(), data);
            }
            boolean moved =
                    !respawn && (where.distanceToSqr(at) > 1e-6 || Math.abs(Mth.wrapDegrees(y - yaw)) > YAW_EPSILON);
            boolean rescaled = !respawn && (s != scale || scaleModeChanged);
            if (respawn) {
                countRespawn(
                        gameTime,
                        entityId < 0 ? "new" : !sameStack ? "stack" : "pose " + Long.toHexString(pose ^ p),
                        pos,
                        item,
                        stack);
                despawnAll();
                item = stack.copy();
                pose = p;
                entityId = VirtualEntityUtils.requestEntityId();
                uuid = new UUID(MARKER, p);
            }
            if (respawn || moved) {
                at = where;
                yaw = y;
            }
            if (respawn || rescaled) scale = s;
            boolean refresh = Math.floorMod(gameTime + refreshPhase, REFRESH_TICKS) == 0;
            if (!respawn && !moved && !rescaled && !refresh) return;
            List<ServerPlayer> tracking = level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(pos), false);
            viewers.removeIf(player -> {
                if (tracking.contains(player) && !player.hasDisconnected()) return false;
                if (!player.hasDisconnected()) player.connection.send(new ClientboundRemoveEntitiesPacket(entityId));
                return true;
            });
            for (ServerPlayer player : viewers) {
                if (respawn) {
                    spawn(player);
                    continue;
                }
                if (moved)
                    player.connection.send(new ClientboundTeleportEntityPacket(
                            entityId, new PositionMoveRotation(at, Vec3.ZERO, yaw, 0), Set.of(), false));
                if (rescaled) sendScale(player);
            }
            for (ServerPlayer player : tracking) {
                if (viewers.contains(player) || !PolymerBedrockCarriers.isBedrock(player.getUUID())) continue;
                viewers.add(player);
                spawn(player);
            }
        }

        private void spawn(ServerPlayer player) {
            player.connection.send(new ClientboundAddEntityPacket(
                    entityId,
                    new UUID(MARKER, pose),
                    at.x,
                    at.y,
                    at.z,
                    0,
                    yaw,
                    EntityTypes.ARMOR_STAND,
                    0,
                    Vec3.ZERO,
                    yaw));
            sendScale(player);
            player.connection.send(
                    new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, item.copy()))));
        }

        private void sendScale(ServerPlayer player) {
            AttributeInstance attribute = new AttributeInstance(Attributes.SCALE, instance -> {});
            // The engine ignores this scale for held equipment, but applies it to our own icon geometry. That
            // geometry already gets the source scale through icon_scale; multiplying again squares it.
            attribute.setBaseValue(PolymerBedrockItemPose.carrierScale(pose, scale));
            player.connection.send(new ClientboundUpdateAttributesPacket(entityId, List.of(attribute)));
        }

        private void despawnAll() {
            if (entityId < 0) return;
            for (ServerPlayer player : viewers)
                if (!player.hasDisconnected()) player.connection.send(new ClientboundRemoveEntitiesPacket(entityId));
        }

        public void destroy() {
            despawnAll();
            viewers.clear();
        }
    }

    static float yawOf(Quaternionf rotation) {
        Vector3f x = rotation.transform(new Vector3f(1, 0, 0));
        if (x.x * x.x + x.z * x.z > 1e-6f) return (float) Math.atan2(-x.z, x.x);
        Vector3f z = rotation.transform(new Vector3f(0, 0, 1));
        return (float) Math.atan2(z.x, z.z);
    }

    /**
     * Bedrock bone euler (degrees, 0..359) of a Java rotation. Bedrock geometry mirrors x, and a bone applies x, then
     * y, then z, so Java R = Rz(c) Ry(b) Rx(a) is Bedrock (-a, -b, c).
     */
    public static int[] bedrockEuler(Quaternionf rotation) {
        double[] zyx = eulerZYX(rotation);
        return new int[] {
            Math.floorMod((int) Math.round(Math.toDegrees(-zyx[0])), 360),
            Math.floorMod((int) Math.round(Math.toDegrees(-zyx[1])), 360),
            Math.floorMod((int) Math.round(Math.toDegrees(zyx[2])), 360)
        };
    }

    static final double GIMBAL_SIN2 = 0.99998;

    public static double[] eulerZYX(Quaternionf rotation) {
        Quaternionf q = new Quaternionf(rotation).normalize();
        double w = q.w, x = q.x, y = q.y, z = q.z;
        double sp = 2 * (w * y - z * x);
        double b = Math.asin(Math.max(-1, Math.min(1, sp)));
        if (sp * sp > GIMBAL_SIN2) return new double[] {2 * Math.atan2(x, w), b, 0};
        return new double[] {
            Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y)),
            b,
            Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z))
        };
    }

    /** Bridge property {@code ctx}: 0 = FIXED, 1 = GROUND, 2 = THIRD_PERSON_RIGHT_HAND, 3 = NONE (no display). */
    static int contextCode(ItemDisplayContext context) {
        return switch (context) {
            case GROUND -> 1;
            case THIRD_PERSON_RIGHT_HAND -> 2;
            case NONE -> 3;
            default -> 0;
        };
    }

    /**
     * Bridge property {@code kind}: how the Bedrock engine draws the held stack when no attachable takes over, 0 =
     * flat item sprite, 1 = block. Only vanilla block items can be drawn as blocks (Create items reach Bedrock as
     * Geyser custom items: sprites or attachables), and of those only the ones whose Bedrock item is not an icon:
     * plain {@link BlockItem}s (not seeds, doors, signs, beds, torches, banners) of blocks with a collision shape,
     * less a set known to have icons on Bedrock. A heuristic: a wrong case shows in the other kind's pose.
     */
    static int renderKind(ItemStack stack) {
        if (stack.getItem().getClass() != BlockItem.class) return 0;
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")) return 0;
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof IronBarsBlock
                || block instanceof LanternBlock
                || block instanceof ChainBlock
                || block instanceof HopperBlock
                || block instanceof AbstractCauldronBlock
                || block instanceof BrewingStandBlock
                || block instanceof CakeBlock
                || block instanceof FlowerPotBlock
                || block instanceof CampfireBlock
                || block instanceof BellBlock
                || block instanceof PointedDripstoneBlock
                || block instanceof CandleBlock) return 0;
        return block.defaultBlockState()
                        .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                        .isEmpty()
                ? 0
                : 1;
    }

    /**
     * UUID least significant bits (bridge ItemHolderEntities.onSpawn): rx | ry << 9 | rz << 18 (degrees 0..359, the
     * Bedrock bone euler of R'), kind << 27, ctx << 29.
     */
    static long encodePose(int[] euler, int kind, int ctx) {
        return (euler[0] & 0x1FF)
                | ((long) (euler[1] & 0x1FF) << 9)
                | ((long) (euler[2] & 0x1FF) << 18)
                | ((long) (kind & 0x3) << 27)
                | ((long) (ctx & 0x3) << 29);
    }
}
