package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.AllAdvancements;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.content.contraptions.chassis.AbstractChassisBlock;
import com.zurrtum.create.content.contraptions.glue.SuperGlueEntity;
import com.zurrtum.create.content.contraptions.glue.SuperGlueSelectionHelper;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerWorldPreviews;
import dev.veeda.createpoly.assets.CreateIds;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PolymerSuperGlue {
    public static final int PASSIVE = 0x4D9162, HIGHLIGHT = 0x68C586, FAIL = 0xC5B548;
    private static final Identifier ACTION = CreateIds.id("polymer_super_glue");
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final long LIFETIME = TimeUnit.MINUTES.toNanos(2);

    private record Selection(ResourceKey<Level> dimension, BlockPos from, long expiry) {}

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SELECTIONS.clear());
        AdapterInteractions.useItem((player, level, hand) -> {
            if (player instanceof ServerPlayer sp
                    && player.isShiftKeyDown()
                    && player.getItemInHand(hand).is(AllItems.SUPER_GLUE)
                    && SELECTIONS.containsKey(sp.getUUID())) {
                discard(sp);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        AdapterInteractions.attackBlock((player, level, hand, pos, direction) -> {
            if (!(player instanceof ServerPlayer sp)
                    || !(level instanceof ServerLevel world)
                    || !player.getMainHandItem().is(AllItems.SUPER_GLUE)
                    || !player.mayBuild()) return InteractionResult.PASS;
            SuperGlueEntity glue = aimedEntity(
                    sp,
                    world.getEntitiesOfClass(
                            SuperGlueEntity.class, sp.getBoundingBox().inflate(32, 16, 32)));
            if (glue == null || !canChange(sp, world, glue.getBoundingBox())) return InteractionResult.PASS;
            AllSoundEvents.SLIME_ADDED.play(world, null, pos, 0.5F, 0.5F);
            glue.spawnParticles();
            glue.discard();
            return InteractionResult.SUCCESS;
        });
        AdapterInteractions.useBlock((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp)
                    || !(level instanceof ServerLevel world)
                    || hand != InteractionHand.MAIN_HAND
                    || !player.getMainHandItem().is(AllItems.SUPER_GLUE)
                    || !player.mayBuild()) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            Selection current = selection(sp);
            if (player.isShiftKeyDown()) {
                if (current == null) return InteractionResult.PASS;
                discard(sp);
                return InteractionResult.SUCCESS;
            }
            if (!allowed(sp, world, pos)) return InteractionResult.FAIL;
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof AbstractChassisBlock chassis
                    && chassis.getGlueableSide(state, hit.getDirection()) != null) return InteractionResult.PASS;
            if (current != null) {
                if (!pos.closerThan(current.from(), 24)) {
                    status(sp, "create.super_glue.too_far", FAIL);
                    return InteractionResult.SUCCESS;
                }
                Set<BlockPos> cluster = SuperGlueSelectionHelper.searchGlueGroup(world, current.from(), pos, false);
                if (cluster == null || !cluster.contains(pos)) {
                    status(sp, "create.super_glue.cannot_reach", FAIL);
                    return InteractionResult.SUCCESS;
                }
                if (!SuperGlueSelectionHelper.collectGlueFromInventory(sp, 1, true)) {
                    status(sp, "create.super_glue.not_enough", FAIL);
                    return InteractionResult.SUCCESS;
                }
                confirm(sp, world, current.from(), pos, cluster);
                return InteractionResult.SUCCESS;
            }
            SELECTIONS.put(
                    sp.getUUID(), new Selection(world.dimension(), pos.immutable(), System.nanoTime() + LIFETIME));
            spawnParticles(world, pos, hit.getDirection());
            status(sp, "create.super_glue.first_pos", 0xFFFFFF);
            AllSoundEvents.SLIME_ADDED.play(world, null, pos, 0.5F, 0.85F);
            PolymerVanillaEffects.sourceSound(
                    world, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 1);
            return InteractionResult.SUCCESS;
        });
    }

    private static void confirm(
            ServerPlayer player, ServerLevel world, BlockPos from, BlockPos to, Set<BlockPos> cluster) {
        clear(player);
        AABB box = SuperGlueEntity.span(from, to);
        if (!canChange(player, world, box)) return;
        for (BlockPos pos : cluster) if (!world.isLoaded(pos) || !player.mayInteract(world, pos)) return;
        SuperGlueSelectionHelper.collectGlueFromInventory(player, 1, false);
        player.getInventory().setChanged();
        SuperGlueEntity entity = new SuperGlueEntity(world, box);
        world.addFreshEntity(entity);
        entity.spawnParticles();
        AllAdvancements.SUPER_GLUE.trigger(player);
        AllSoundEvents.SLIME_ADDED.play(world, null, to, 0.5F, 0.95F);
        PolymerVanillaEffects.sourceSound(world, to, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 1);
        status(player, "create.super_glue.success", 0xFFFFFF);
    }

    private static void discard(ServerPlayer player) {
        clear(player);
        status(player, "create.super_glue.abort", 0xFFFFFF);
    }

    private static void status(ServerPlayer player, String key, int color) {
        player.sendOverlayMessage(Component.translatable(key).withColor(color));
    }

    private static void spawnParticles(ServerLevel world, BlockPos pos, Direction direction) {
        Vec3 normal = Vec3.atLowerCornerOf(direction.getUnitVec3i());
        Vec3 face = Vec3.atCenterOf(pos).add(normal.scale(0.5));
        for (int i = 0; i < 40; i++) {
            double u = world.getRandom().nextDouble() - .5,
                    v = world.getRandom().nextDouble() - .5;
            Vec3 offset = switch (direction.getAxis()) {
                case X -> new Vec3(0, u, v);
                case Y -> new Vec3(u, 0, v);
                case Z -> new Vec3(u, v, 0);
            };
            Vec3 motion = offset.normalize().scale(1 / 16f);
            Vec3 at = face.add(offset);
            PolymerVanillaEffects.sourceParticles(
                    world,
                    new ItemParticleOption(ParticleTypes.ITEM, Items.SLIME_BALL),
                    at.x,
                    at.y,
                    at.z,
                    0,
                    motion.x,
                    motion.y,
                    motion.z,
                    1);
        }
    }

    public static AABB preview(ServerPlayer player, BlockPos aim) {
        Selection selected = selection(player);
        if (selected == null) return null;
        BlockPos end = aim;
        if (end == null || !end.closerThan(selected.from(), 24)) end = selected.from();
        return SuperGlueEntity.span(selected.from(), end);
    }

    public static int previewColor(ServerPlayer player, BlockPos aim) {
        Selection selected = selection(player);
        if (selected == null || aim == null || player.isShiftKeyDown() || !aim.closerThan(selected.from(), 24))
            return FAIL;
        Set<BlockPos> cluster = SuperGlueSelectionHelper.searchGlueGroup(player.level(), selected.from(), aim, false);
        return cluster != null
                        && cluster.contains(aim)
                        && SuperGlueSelectionHelper.collectGlueFromInventory(player, 1, true)
                ? HIGHLIGHT
                : FAIL;
    }

    public static AABB aimed(ServerPlayer player, List<SuperGlueEntity> nearby) {
        SuperGlueEntity entity = aimedEntity(player, nearby);
        return entity == null ? null : entity.getBoundingBox();
    }

    private static SuperGlueEntity aimedEntity(ServerPlayer player, List<SuperGlueEntity> nearby) {
        Vec3 origin = player.getEyePosition();
        Vec3 target = origin.add(player.getViewVector(1).scale(player.blockInteractionRange() + 1));
        SuperGlueEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SuperGlueEntity glue : nearby) {
            Optional<Vec3> clip = glue.getBoundingBox().clip(origin, target);
            if (clip.isEmpty()) continue;
            double distance = clip.get().distanceToSqr(origin);
            if (distance > bestDistance) continue;
            best = glue;
            bestDistance = distance;
        }
        return best;
    }

    private static Selection selection(ServerPlayer player) {
        Selection selected = SELECTIONS.get(player.getUUID());
        if (selected != null
                && (selected.expiry() <= System.nanoTime()
                        || !selected.dimension().equals(player.level().dimension())
                        || !player.getMainHandItem().is(AllItems.SUPER_GLUE))) {
            clear(player);
            return null;
        }
        return selected;
    }

    static void clear(ServerPlayer player) {
        SELECTIONS.remove(player.getUUID());
        PolymerWorldPreviews.clear(player.getUUID());
    }

    public static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        return packet.id().equals(ACTION);
    }

    private static boolean allowed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return !player.isSpectator()
                && player.mayBuild()
                && level.isLoaded(pos)
                && player.mayInteract(level, pos)
                && player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))
                        <= Math.pow(player.blockInteractionRange() + 1, 2);
    }

    private static boolean canChange(ServerPlayer player, ServerLevel world, AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - .01, box.maxY - .01, box.maxZ - .01)))
            if (!world.isLoaded(pos) || !player.mayInteract(world, pos)) return false;
        return true;
    }
}
