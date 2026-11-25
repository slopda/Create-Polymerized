package dev.veeda.createpoly.mixin;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.content.equipment.sandPaper.SandPaperItem;
import com.zurrtum.create.infrastructure.component.SandPaperItemComponent;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SandPaperItem.class)
public abstract class SandPaperNativeMixin {
    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void polymerSandingCrumbs(Level level, LivingEntity user, ItemStack stack, int remaining, CallbackInfo ci) {
        if (!(level instanceof ServerLevel server) || remaining % 2 != 0) return;
        ItemStack polishing = polishing(stack);
        if (polishing.isEmpty()) return;
        Vec3 at = user.getEyePosition().add(user.getLookAngle().scale(0.5)).add(0, -0.2, 0);
        server.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(polishing)),
                at.x,
                at.y,
                at.z,
                1,
                0.05,
                0.05,
                0.05,
                0.05);
    }

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void polymerPolishBurst(
            ItemStack stack, Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (!(level instanceof ServerLevel server)) return;
        ItemStack polishing = polishing(stack);
        if (polishing.isEmpty()) return;
        Vec3 at = user.getEyePosition(1).add(user.getLookAngle().scale(0.5f));
        server.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(polishing)),
                at.x,
                at.y,
                at.z,
                20,
                0,
                0,
                0,
                1 / 8.0);
    }

    private static ItemStack polishing(ItemStack stack) {
        SandPaperItemComponent component = stack.get(AllDataComponents.SAND_PAPER_POLISHING);
        return component == null ? ItemStack.EMPTY : component.item();
    }
}
