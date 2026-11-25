package dev.veeda.createpoly.mixin;

import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(
        value = {GameProtocols.class, ConfigurationProtocols.class},
        priority = 800)
public abstract class NativeProtocolMixin {}
