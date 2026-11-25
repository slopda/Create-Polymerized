package dev.veeda.createpoly.mixin;

import com.zurrtum.create.infrastructure.polymer.PolymerRegistrySyncFilter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager")
public class RegistrySyncFilterMixin {
    @Inject(method = "createAndPopulateRegistryMap", at = @At("RETURN"))
    private static void omitCreateEntries(CallbackInfoReturnable<Map<Identifier, Object2IntMap<Identifier>>> cir) {
        PolymerRegistrySyncFilter.removeCreateEntries(cir.getReturnValue(), "Fabric registry sync map");
    }
}
