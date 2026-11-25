package dev.veeda.createpoly.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagNetworkSerialization.NetworkPayload.class)
public interface NativeTagPayloadAccessor {
    @Accessor("tags")
    Map<Identifier, IntList> nativeTags();
}
