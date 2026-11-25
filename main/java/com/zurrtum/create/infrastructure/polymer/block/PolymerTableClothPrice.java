package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.filtering.ServerFilteringBehaviour;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.control.TableClothFilterSlot;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerMachineDialogs;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PolymerTableClothPrice {
    private PolymerTableClothPrice() {}

    private record Target(TableClothBlockEntity cloth, BlockPos pos, Vec3 center, double distance) {}

    static void register() {
        AdapterInteractions.useBlock(
                (player, level, hand, hit) -> player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND
                        ? use(sp)
                        : InteractionResult.PASS);
        AdapterInteractions.useItem(
                (player, level, hand) -> player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND
                        ? use(sp)
                        : InteractionResult.PASS);
    }

    private static InteractionResult use(ServerPlayer player) {
        if (player.isSpectator() || player.isShiftKeyDown()) return InteractionResult.PASS;
        Target target = target(player);
        if (target == null) return InteractionResult.PASS;
        ServerFilteringBehaviour filter = target.cloth().getBehaviour(ServerFilteringBehaviour.TYPE);
        if (filter == null || !filter.isActive() || !filter.mayInteract(player)) return InteractionResult.PASS;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            PolymerMachineDialogs.openValueSettings(player, (ServerLevel) player.level(), target.pos(), target.cloth());
        } else {
            BlockHitResult hit = new BlockHitResult(target.center(), target.cloth().facing, target.pos(), false);
            filter.onShortInteract(player, InteractionHand.MAIN_HAND, target.cloth().facing, hit);
            PolymerBlockParts.tick(target.cloth());
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean targetsPriceTag(ServerPlayer player) {
        Target target = target(player);
        if (target == null) return false;
        ServerFilteringBehaviour filter = target.cloth().getBehaviour(ServerFilteringBehaviour.TYPE);
        return filter != null && filter.mayInteract(player);
    }

    private static Target target(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getViewVector(1);
        double reach = player.blockInteractionRange();
        HitResult block = player.pick(reach, 1, false);
        double max = block.getType() == HitResult.Type.MISS
                ? reach
                : block.getLocation().distanceTo(eye) + 0.35;
        Target best = null;
        Set<BlockPos> seen = new HashSet<>();
        for (double t = 0; t <= max + 0.5; t += 0.25) {
            BlockPos cell = BlockPos.containing(eye.add(dir.scale(t)));
            for (BlockPos pos : List.of(cell, cell.above())) {
                if (!seen.add(pos)
                        || !(player.level().getBlockEntity(pos) instanceof TableClothBlockEntity cloth)
                        || !cloth.isShop()) continue;
                Vec3 offset = new TableClothFilterSlot(cloth).getLocalOffset(cloth.getBlockState());
                if (offset == null) continue;
                Vec3 center = Vec3.atLowerCornerOf(pos).add(offset);
                double along = center.subtract(eye).dot(dir);
                if (along < 0 || along > max) continue;
                if (eye.add(dir.scale(along)).distanceTo(center) > 0.25) continue;
                if (best == null || along < best.distance()) best = new Target(cloth, pos.immutable(), center, along);
            }
        }
        return best;
    }

    public static void collectHover(ServerPlayer player, BlockPos aim, List<PolymerBlockParts.Part> out) {
        Target target = target(player);
        if (target == null || aim == null) return;
        ServerFilteringBehaviour filter = target.cloth().getBehaviour(ServerFilteringBehaviour.TYPE);
        if (filter == null || !filter.isActive() || !filter.mayInteract(player)) return;
        TableClothFilterSlot slot = new TableClothFilterSlot(target.cloth());
        BlockPos rel = target.pos().subtract(aim);
        Matrix4f pose = new Matrix4f()
                .translate(rel.getX(), rel.getY(), rel.getZ())
                .mul(PolymerBlockControls.pose(slot, target.cloth().getBlockState()));
        ItemStack payment = filter.getFilter();
        PolymerBlockControls.frame(out, pose, !payment.isEmpty());
        if (!payment.isEmpty() && filter.isCountVisible()) {
            String count = String.valueOf(filter.count);
            int width = PolymerGogglesOverlay.width(count);
            Matrix4f text = new Matrix4f(pose)
                    .scale(-slot.getFontScale())
                    .translate(17.5f, -5, 7)
                    .translate(-width, 0, 0)
                    .translate(-7, 10, payment.getItem() instanceof BlockItem ? 10.25f : 0)
                    .scale(1.5f)
                    .translate(width / 2f, 0, 0);
            out.add(PolymerBlockParts.textPart(Component.literal(count).withColor(0xEDEDED), text));
        }
        if (player.tickCount % 10 == 0)
            player.sendOverlayMessage(Component.translatable("create.table_cloth.price_per_order")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("  "))
                    .append(Component.translatable(
                                    payment.isEmpty()
                                            ? "create.logistics.filter.click_to_set"
                                            : "create.logistics.filter.click_to_replace")
                            .withStyle(ChatFormatting.WHITE))
                    .append(
                            payment.isEmpty()
                                    ? Component.empty()
                                    : Component.literal(", ")
                                            .append(Component.translatable(
                                                    "create.logistics.filter.hold_to_set_amount"))
                                            .withStyle(ChatFormatting.WHITE)));
    }
}
