package dev.veeda.createpoly.mixin;

import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.content.logistics.funnel.FunnelBlockEntity;
import com.zurrtum.create.infrastructure.polymer.effects.PolymerVanillaEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FunnelBlockEntity.class)
public abstract class FunnelFlapServerMixin {
    @Inject(method = "flap", at = @At("HEAD"))
    private void polymerServerFlap(boolean inward, CallbackInfo ci) {
        FunnelBlockEntity self = (FunnelBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) return;
        LerpedFloat flap = self.flap;
        flap.setValue(inward ? -1 : 1);
        PolymerVanillaEffects.sound(
                level,
                Vec3.atCenterOf(self.getBlockPos()),
                AllSoundEvents.FUNNEL_FLAP.getMainEvent(),
                SoundSource.BLOCKS,
                1,
                1);
    }
}
