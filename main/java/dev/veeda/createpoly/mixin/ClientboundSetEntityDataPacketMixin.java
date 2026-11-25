package dev.veeda.createpoly.mixin;

import com.zurrtum.create.AllSynchedDatas;
import com.zurrtum.create.Create;
import dev.veeda.createpoly.NativeMetadataSnapshots;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundSetEntityDataPacket.class)
public abstract class ClientboundSetEntityDataPacketMixin {
    private static final AtomicBoolean REPORTED_FILTERING = new AtomicBoolean();

    @Shadow
    @Final
    @Mutable
    private List<SynchedEntityData.DataValue<?>> packedItems;

    @Inject(method = "<init>(ILjava/util/List;)V", at = @At("TAIL"))
    private void filterCreateOnlySerializers(int entityId, List<SynchedEntityData.DataValue<?>> data, CallbackInfo ci) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER || AllSynchedDatas.HANDLERS.isEmpty()) {
            return;
        }

        List<SynchedEntityData.DataValue<?>> filtered = new ArrayList<>(data.size());
        int removed = 0;
        for (SynchedEntityData.DataValue<?> value : data) {
            if (AllSynchedDatas.HANDLERS.contains(value.serializer())
                    || NativeMetadataSnapshots.isCreateOnly(entityId, value.id())) {
                removed++;
            } else {
                filtered.add(value);
            }
        }

        if (removed == 0) {
            return;
        }

        packedItems = List.copyOf(filtered);
        if (REPORTED_FILTERING.compareAndSet(false, true)) {
            Create.LOGGER.info(
                    "Filtered Create-only entity metadata from vanilla-client packets ({} entry/entries in the first packet)",
                    removed);
        }
    }
}
