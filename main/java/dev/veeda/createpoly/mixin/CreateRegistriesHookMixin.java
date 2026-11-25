package dev.veeda.createpoly.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.api.registry.CreateRegistries;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CreateRegistries.class, remap = false)
abstract class CreateRegistriesHookMixin {
    @ModifyReturnValue(
            method = "register(Lnet/minecraft/resources/ResourceKey;Z)Lnet/minecraft/core/Registry;",
            at = @At("RETURN"))
    private static <T> Registry<T> polymerpatch$optionalRegistry(
            Registry<T> registry, @Local(argsOnly = true) ResourceKey<? extends Registry<T>> key) {
        RegistryAttributeHolder.get(key).addAttribute(RegistryAttribute.OPTIONAL);
        return registry;
    }
}
