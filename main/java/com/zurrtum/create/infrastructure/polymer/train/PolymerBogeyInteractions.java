package com.zurrtum.create.infrastructure.polymer.train;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.trains.bogey.AbstractBogeyBlock;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import com.zurrtum.create.content.trains.track.ITrackBlock;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

public final class PolymerBogeyInteractions {
    private PolymerBogeyInteractions() {}

    public static void register() {
        AdapterInteractions.useBlock((player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !(world instanceof ServerLevel level)
                    || PolymerNativeClients.isNative(serverPlayer)) return InteractionResult.PASS;
            return use(serverPlayer, level, hand, hit);
        });
    }

    static InteractionResult use(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
        if (PolymerNativeClients.isNative(player)
                || player.isSpectator()
                || !player.mayBuild()
                || player.isShiftKeyDown()
                || !player.mayInteract(level, hit.getBlockPos())) return InteractionResult.PASS;
        var held = player.getItemInHand(hand);
        if (!held.isEmpty() && !held.is(AllItems.RAILWAY_CASING)) return InteractionResult.PASS;
        var state = level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof AbstractBogeyBlock<?> bogey)) return InteractionResult.PASS;
        if (player.getEyePosition().distanceToSqr(hit.getLocation()) > Math.pow(player.blockInteractionRange() + .1, 2))
            return InteractionResult.PASS;
        var trackPos = bogey.isUpsideDown(state)
                ? hit.getBlockPos().above()
                : hit.getBlockPos().below();
        if (!level.isLoaded(trackPos) || !player.mayInteract(level, trackPos)) return InteractionResult.PASS;
        var trackState = level.getBlockState(trackPos);
        if (!(trackState.getBlock() instanceof ITrackBlock track)) return InteractionResult.PASS;
        for (var entry : StationBlockEntity.assemblyAreas.get(level).entrySet()) {
            if (!entry.getValue().isInside(trackPos)) continue;
            if (level.getBlockEntity(entry.getKey()) instanceof StationBlockEntity station
                    && station.trackClicked(player, hand, track, trackState, trackPos)) {
                player.swing(hand);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
