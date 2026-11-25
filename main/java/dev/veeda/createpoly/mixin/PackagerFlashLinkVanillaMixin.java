package dev.veeda.createpoly.mixin;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.logistics.packager.PackagerBlockEntity;
import com.zurrtum.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.zurrtum.create.infrastructure.polymer.block.PolymerLinkBulbs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackagerBlockEntity.class)
public abstract class PackagerFlashLinkVanillaMixin {
    @Inject(method = "flashLink", at = @At("HEAD"))
    private void polymerVanillaFlash(CallbackInfo ci) {
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) return;
        for (Direction d : Direction.values()) {
            BlockPos adjacent = self.getBlockPos().relative(d);
            var state = level.getBlockState(adjacent);
            if (!state.is(AllBlocks.STOCK_LINK) || PackagerLinkBlock.getConnectedDirection(state) != d) continue;
            PolymerLinkBulbs.wifi(level, adjacent);
            return;
        }
    }
}
