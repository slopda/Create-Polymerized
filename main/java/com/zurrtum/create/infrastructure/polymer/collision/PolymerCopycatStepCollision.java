package com.zurrtum.create.infrastructure.polymer.collision;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PolymerCopycatStepCollision {
    private static final double SIZE = .5;

    public static List<AABB> boxes(BlockState state, ServerLevel level, BlockPos pos) {
        AABB source = state.getCollisionShape(level, pos).bounds();
        if (source.getXsize() > .75)
            return List.of(
                    new AABB(source.minX, source.minY, source.minZ, source.minX + .5, source.maxY, source.maxZ),
                    new AABB(source.minX + .5, source.minY, source.minZ, source.maxX, source.maxY, source.maxZ));
        return List.of(
                new AABB(source.minX, source.minY, source.minZ, source.maxX, source.maxY, source.minZ + .5),
                new AABB(source.minX, source.minY, source.minZ + .5, source.maxX, source.maxY, source.maxZ));
    }

    public static final class Cell {
        final AABB bounds;
        final ItemDisplayElement anchor;
        final SimpleEntityElement shulker;

        public Cell(ElementHolder holder, ServerLevel level, BlockPos pos, AABB bounds) {
            this.bounds = bounds;
            Vec3 offset = new Vec3(
                    (bounds.minX + bounds.maxX) / 2 - .5, bounds.minY - .5, (bounds.minZ + bounds.maxZ) / 2 - .5);
            anchor = new ItemDisplayElement(ItemStack.EMPTY);
            anchor.setViewRange(0);
            shulker = new SimpleEntityElement(EntityTypes.SHULKER) {
                private final Set<UUID> visible = new HashSet<>();

                @Override
                public void startWatching(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> packets) {
                    if (PolymerProxyMining.hidden(player, level, pos) || !visible.add(player.getUUID())) return;
                    super.startWatching(player, packets);
                    Cell.this.link(packets);
                }

                @Override
                public void stopWatching(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> packets) {
                    visible.remove(player.getUUID());
                    super.stopWatching(player, packets);
                }

                @Override
                public void tick() {
                    for (var listener : holder.getWatchingPlayers()) {
                        var player = listener.getPlayer();
                        if (PolymerProxyMining.hidden(player, level, pos)) {
                            if (visible.contains(player.getUUID())) stopWatching(player, listener::send);
                        } else startWatching(player, listener::send);
                    }
                    super.tick();
                }
            };
            anchor.setOffset(offset);
            shulker.setOffset(offset);
            PolymerCollisionShulkers.hide(shulker);
            shulker.setInteractionHandler(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand, Vec3 hit, boolean secondary) {
                    if (player.level() != level) return;
                    Vec3 eye = player.getEyePosition();
                    var target = level.clip(new ClipContext(
                            eye,
                            eye.add(player.getLookAngle().scale(player.blockInteractionRange())),
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            player));
                    if (!target.getBlockPos().equals(pos)) return;
                    player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, target);
                }

                @Override
                public void attack(ServerPlayer player) {
                    PolymerProxyMining.attack(player, level, pos);
                }
            });
            holder.addElement(anchor);
            holder.addElement(shulker);
        }

        void link(Consumer<Packet<ClientGamePacketListener>> packets) {
            packets.accept(VirtualEntityUtils.createClientboundSetPassengersPacket(
                    anchor.getEntityId(), new int[] {shulker.getEntityId()}));
            AttributeInstance scale = new AttributeInstance(Attributes.SCALE, ignored -> {});
            scale.setBaseValue(SIZE);
            packets.accept(new ClientboundUpdateAttributesPacket(shulker.getEntityId(), List.of(scale)));
        }

        public void remove(ElementHolder holder) {
            holder.removeElement(shulker);
            holder.removeElement(anchor);
        }
    }
}
