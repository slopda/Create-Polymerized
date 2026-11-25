package com.zurrtum.create.infrastructure.polymer.interaction;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.kinetics.belt.item.BeltConnectorItem;
import com.zurrtum.create.content.kinetics.simpleRelays.ShaftBlock;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PolymerBeltConnectorPreview {
    private PolymerBeltConnectorPreview() {}

    private static final int CONNECT_COLOR = ARGB.colorFromFloat(1, 0.3f, 0.9f, 0.5f);
    private static final int NO_CONNECT_COLOR = ARGB.colorFromFloat(1, 0.9f, 0.3f, 0.5f);

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList()))
                tick(player);
        });
    }

    private static void tick(ServerPlayer player) {
        if (player.isSpectator() || !(player.level() instanceof ServerLevel world)) return;
        RandomSource random = world.getRandom();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(AllItems.BELT_CONNECTOR) || !held.has(AllDataComponents.BELT_FIRST_SHAFT)) continue;
            BlockPos first = held.get(AllDataComponents.BELT_FIRST_SHAFT);
            if (!world.isLoaded(first) || !world.getBlockState(first).hasProperty(BlockStateProperties.AXIS)) continue;
            Axis axis = world.getBlockState(first).getValue(BlockStateProperties.AXIS);

            BlockHitResult hit = PolymerReach.targetBlock(player);
            if (hit == null) {
                if (random.nextInt(50) == 0)
                    dust(
                            world,
                            player,
                            CONNECT_COLOR,
                            first.getX() + 0.5 + offset(random),
                            first.getY() + 0.5 + offset(random),
                            first.getZ() + 0.5 + offset(random));
                return;
            }
            BlockPos selected = hit.getBlockPos();
            if (world.getBlockState(selected).canBeReplaced()) return;
            if (!ShaftBlock.isShaft(world.getBlockState(selected))) selected = selected.relative(hit.getDirection());
            if (!selected.closerThan(
                    first, AllConfigs.server().kinetics.maxBeltLength.get())) return;

            boolean canConnect = BeltConnectorItem.validateAxis(world, selected)
                    && BeltConnectorItem.canConnect(world, first, selected);
            Vec3 start = Vec3.atLowerCornerOf(first);
            Vec3 end = Vec3.atLowerCornerOf(selected);
            Vec3 actualDiff = end.subtract(start);
            end = end.subtract(
                    axis.choose(actualDiff.x, 0, 0), axis.choose(0, actualDiff.y, 0), axis.choose(0, 0, actualDiff.z));
            Vec3 diff = end.subtract(start);
            double x = Math.abs(diff.x), y = Math.abs(diff.y), z = Math.abs(diff.z);
            float length = (float) Math.max(x, Math.max(y, z));
            Vec3 step = diff.normalize();
            int sames = (x == y ? 1 : 0) + (y == z ? 1 : 0) + (z == x ? 1 : 0);
            if (sames == 0) {
                List<Vec3> valid = new ArrayList<>();
                for (int i = -1; i <= 1; i++)
                    for (int j = -1; j <= 1; j++)
                        for (int k = -1; k <= 1; k++) {
                            if (axis.choose(i, j, k) != 0) continue;
                            if (axis == Axis.Y && i != 0 && k != 0) continue;
                            if (i == 0 && j == 0 && k == 0) continue;
                            valid.add(new Vec3(i, j, k));
                        }
                Vec3 best = valid.getFirst();
                double closest = Double.MAX_VALUE;
                for (Vec3 candidate : valid) {
                    double distance = step.distanceTo(candidate);
                    if (distance < closest) {
                        closest = distance;
                        best = candidate;
                    }
                }
                step = best;
            }
            if (axis == Axis.Y && step.x != 0 && step.z != 0) return;
            step = new Vec3(Math.signum(step.x), Math.signum(step.y), Math.signum(step.z));
            int color = canConnect ? CONNECT_COLOR : NO_CONNECT_COLOR;
            for (float f = 0; f < length; f += 0.0625f) {
                if (random.nextInt(10) != 0) continue;
                Vec3 position = start.add(step.scale(f));
                dust(world, player, color, position.x + 0.5, position.y + 0.5, position.z + 0.5);
            }
            return;
        }
    }

    private static void dust(ServerLevel world, ServerPlayer player, int color, double x, double y, double z) {
        world.sendParticles(player, new DustParticleOptions(color, 1), true, false, x, y, z, 1, 0, 0, 0, 0);
    }

    private static double offset(RandomSource random) {
        return (random.nextFloat() - 0.5f) * 2 * 0.25f;
    }
}
