package dev.veeda.createpoly.mixin;

import com.zurrtum.create.content.redstone.displayLink.LinkWithBulbBlockEntity;
import com.zurrtum.create.infrastructure.polymer.block.PolymerLinkBulbs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LinkWithBulbBlockEntity.class)
public abstract class LinkBulbPulseMixin {
    @Inject(method = "sendPulseNextSync", at = @At("HEAD"))
    private void polymerRecordPulse(CallbackInfo ci) {
        PolymerLinkBulbs.pulse((LinkWithBulbBlockEntity) (Object) this);
    }
}
