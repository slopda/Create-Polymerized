package dev.veeda.createpoly.mixin;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.Queue;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class NativeClientPackMixin {
    @Shadow
    @Final
    private Queue<ConfigurationTask> configurationTasks;

    @Inject(method = "startNextTask", at = @At("HEAD"))
    private void polymerSkipPackForNativeCreate(CallbackInfo ci) {
        var profile = ((ServerConfigurationPacketListenerImpl) (Object) this).getOwner();
        if (profile == null || !PolymerNativeClients.isNative(profile.id())) return;
        if (configurationTasks.removeIf(
                task -> task.getClass().getName().equals("eu.pb4.polymer.autohost.impl.AutoHostTask")))
            Create.LOGGER.info("Skipped the Polymer resource pack for native Create client {}", profile.name());
    }
}
