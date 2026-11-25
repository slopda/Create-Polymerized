package dev.veeda.createpoly.mixin;

import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PolymerBlockResourceUtils.class, remap = false)
public interface BlockResourceCreatorAccessor {
    @Accessor("CREATOR")
    static BlockResourceCreator polymerpatch$getSharedCreator() {
        throw new AssertionError();
    }
}
