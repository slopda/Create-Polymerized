package com.zurrtum.create.infrastructure.polymer;

import com.mojang.brigadier.Command;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

public final class AdapterInteractions {
    private AdapterInteractions() {}

    public static Command<CommandSourceStack> command(Command<CommandSourceStack> callback) {
        return context -> NativePresentationPolicy.allowsCommand(context.getSource()) ? callback.run(context) : 0;
    }

    public static void useBlock(UseBlockCallback callback) {
        useBlock(Event.DEFAULT_PHASE, callback);
    }

    public static void useBlock(Identifier phase, UseBlockCallback callback) {
        UseBlockCallback.EVENT.register(phase, useBlockCallback(callback));
    }

    static UseBlockCallback useBlockCallback(UseBlockCallback callback) {
        return (player, level, hand, hit) -> NativePresentationPolicy.allowsAction(player)
                ? callback.interact(player, level, hand, hit)
                : InteractionResult.PASS;
    }

    public static void useItem(UseItemCallback callback) {
        useItem(Event.DEFAULT_PHASE, callback);
    }

    public static void useItem(Identifier phase, UseItemCallback callback) {
        UseItemCallback.EVENT.register(phase, useItemCallback(callback));
    }

    static UseItemCallback useItemCallback(UseItemCallback callback) {
        return (player, level, hand) -> NativePresentationPolicy.allowsAction(player)
                ? callback.interact(player, level, hand)
                : InteractionResult.PASS;
    }

    public static void useEntity(Identifier phase, UseEntityCallback callback) {
        UseEntityCallback.EVENT.register(phase, useEntityCallback(callback));
    }

    static UseEntityCallback useEntityCallback(UseEntityCallback callback) {
        return (player, level, hand, entity, hit) -> NativePresentationPolicy.allowsAction(player)
                ? callback.interact(player, level, hand, entity, hit)
                : InteractionResult.PASS;
    }

    public static void attackEntity(Identifier phase, AttackEntityCallback callback) {
        AttackEntityCallback.EVENT.register(phase, attackEntityCallback(callback));
    }

    static AttackEntityCallback attackEntityCallback(AttackEntityCallback callback) {
        return (player, level, hand, entity, hit) -> NativePresentationPolicy.allowsAction(player)
                ? callback.interact(player, level, hand, entity, hit)
                : InteractionResult.PASS;
    }

    public static void attackBlock(AttackBlockCallback callback) {
        attackBlock(Event.DEFAULT_PHASE, callback);
    }

    public static void attackBlock(Identifier phase, AttackBlockCallback callback) {
        AttackBlockCallback.EVENT.register(phase, attackBlockCallback(callback));
    }

    static AttackBlockCallback attackBlockCallback(AttackBlockCallback callback) {
        return (player, level, hand, pos, direction) -> NativePresentationPolicy.allowsAction(player)
                ? callback.interact(player, level, hand, pos, direction)
                : InteractionResult.PASS;
    }
}
