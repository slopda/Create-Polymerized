package com.zurrtum.create.infrastructure.polymer.equipment;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.zurrtum.create.content.redstone.link.controller.LinkedControllerItem;
import com.zurrtum.create.content.redstone.link.controller.LinkedControllerServerHandler;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiControls;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativeControlFreeze;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class PolymerLinkedControllerInput {
    private static final int PACKET_RATE = 5;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();
    private static volatile Set<UUID> activeView = Set.of();

    private enum Mode {
        ACTIVE,
        BIND
    }

    private static final class Session {
        final Mode mode;
        final @Nullable BlockPos lectern, bindTarget;
        Set<Integer> pressed = new HashSet<>();
        int cooldown;

        Session(Mode mode, @Nullable BlockPos lectern, @Nullable BlockPos bindTarget) {
            this.mode = mode;
            this.lectern = lectern;
            this.bindTarget = bindTarget;
        }
    }

    private PolymerLinkedControllerInput() {}

    public static void register() {
        NativeControlFreeze.register(ControlFreezeState.Owner.LINKED_CONTROLLER, player -> {
            try {
                stop(player, false);
            } finally {
                LAST_TOGGLE.remove(player.getUUID());
            }
        });
        AdapterInteractions.useItem(PolymerLinkedControllerInput::onUseItem);
        AdapterInteractions.useBlock((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer)
                    || level.isClientSide()
                    || PolymerNativeClients.isNative(serverPlayer)) return InteractionResult.PASS;
            BlockState state = level.getBlockState(hit.getBlockPos());
            ItemStack stack = player.getItemInHand(hand);
            if (!player.isShiftKeyDown() && state.is(AllBlocks.LECTERN_CONTROLLER)) {
                pendingLectern(serverPlayer, hit.getBlockPos());
                return InteractionResult.PASS;
            }
            if (!stack.is(AllItems.LINKED_CONTROLLER) || player.isShiftKeyDown() || !player.mayBuild())
                return InteractionResult.PASS;
            if (state.is(AllBlocks.REDSTONE_LINK)) {
                toggleBind(serverPlayer, hit.getBlockPos());
                return InteractionResult.PASS;
            }
            if (state.is(Blocks.LECTERN) || state.is(AllBlocks.LECTERN_CONTROLLER)) return InteractionResult.PASS;
            toggle(serverPlayer, hand);
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(PolymerLinkedControllerInput::tick);
        PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, context) -> {
            if (PolymerNativeClients.isNative(context)
                    || !original.is(AllItems.LINKED_CONTROLLER)
                    || activeView.isEmpty()) return client;
            var connection = context.get(PacketContext.CONNECTION);
            if (connection != null
                    && connection.getPacketListener() instanceof ServerGamePacketListenerImpl listener
                    && activeView.contains(listener.getPlayer().getUUID()))
                client.set(
                        DataComponents.CUSTOM_MODEL_DATA,
                        new CustomModelData(List.of(), List.of(), List.of("active"), List.of()));
            return client;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SESSIONS.clear();
            PENDING_LECTERN.clear();
            LAST_TOGGLE.clear();
            activeView = Set.of();
        });
    }

    private static final Map<UUID, BlockPos> PENDING_LECTERN = new HashMap<>();

    private static void pendingLectern(ServerPlayer player, BlockPos pos) {
        if (SESSIONS.containsKey(player.getUUID())) return;
        PENDING_LECTERN.put(player.getUUID(), pos.immutable());
    }

    private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || level.isClientSide()
                || PolymerNativeClients.isNative(serverPlayer)) return InteractionResult.PASS;
        if (!player.getItemInHand(hand).is(AllItems.LINKED_CONTROLLER) || player.isShiftKeyDown())
            return InteractionResult.PASS;
        toggle(serverPlayer, hand);
        return InteractionResult.PASS;
    }

    private static void toggle(ServerPlayer player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND && player.getMainHandItem().getItem() instanceof LinkedControllerItem)
            return;
        if (!firstToggleThisTick(player)) return;
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player, true);
            return;
        }
        start(player, new Session(Mode.ACTIVE, null, null));
    }

    private static boolean firstToggleThisTick(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST_TOGGLE.put(player.getUUID(), now);
        return last == null || last != now;
    }

    private static void toggleBind(ServerPlayer player, BlockPos link) {
        if (!firstToggleThisTick(player)) return;
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player, true);
            return;
        }
        start(player, new Session(Mode.BIND, null, link.immutable()));
    }

    private static void start(ServerPlayer player, Session session) {
        if (PolymerNativeClients.isNative(player)) return;
        NativeControlFreeze.set(player, ControlFreezeState.Owner.LINKED_CONTROLLER, true);
        SESSIONS.put(player.getUUID(), session);
        if (session.mode == Mode.ACTIVE && session.lectern == null) showActive(player, true);
    }

    private static void showActive(ServerPlayer player, boolean active) {
        Set<UUID> next = new HashSet<>(activeView);
        if (!(active ? next.add(player.getUUID()) : next.remove(player.getUUID()))) return;
        activeView = Set.copyOf(next);
        if (!NativePresentationPolicy.allowsPresentation(player)) return;
        player.inventoryMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.sendAllDataToRemote();
    }

    private static void stop(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.get(player.getUUID());
        PENDING_LECTERN.remove(player.getUUID());
        NativeControlFreeze.set(player, ControlFreezeState.Owner.LINKED_CONTROLLER, false);
        if (session != null) {
            try {
                if (session.mode == Mode.ACTIVE && !session.pressed.isEmpty()) {
                    ItemStack controller = controller(player, session);
                    if (!controller.isEmpty()) send(player, controller, session.pressed, false);
                }
            } finally {
                if (session.lectern != null
                        && player.level().getBlockEntity(session.lectern)
                                instanceof LecternControllerBlockEntity lectern
                        && lectern.isUsedBy(player)) lectern.tryStopUsing(player);
            }
        }
        SESSIONS.remove(player.getUUID());
        showActive(player, false);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : NativePresentationPolicy.onlinePlayers(server.getPlayerList())) {
            if (PolymerNativeClients.isNative(player)) {
                stop(player, false);
                continue;
            }
            BlockPos lecternPos = PENDING_LECTERN.remove(player.getUUID());
            if (lecternPos != null
                    && !SESSIONS.containsKey(player.getUUID())
                    && player.level().getBlockEntity(lecternPos) instanceof LecternControllerBlockEntity lectern
                    && lectern.isUsedBy(player)) start(player, new Session(Mode.ACTIVE, lecternPos, null));
            Session session = SESSIONS.get(player.getUUID());
            if (session != null) tick(player, session);
        }
    }

    private static void tick(ServerPlayer player, Session session) {
        ItemStack controller = controller(player, session);
        if (player.isSpectator()
                || !player.isAlive()
                || player.isRemoved()
                || controller.isEmpty()
                || player.containerMenu != player.inventoryMenu) {
            stop(player, true);
            return;
        }
        Input input = player.getLastClientInput();
        boolean[] controls = {
            input.forward(), input.backward(), input.left(), input.right(), input.jump(), input.shift()
        };
        Set<Integer> pressed = new HashSet<>();
        for (int i = 0; i < controls.length; i++) if (controls[i]) pressed.add(i);
        Set<Integer> newKeys = new HashSet<>(pressed);
        newKeys.removeAll(session.pressed);
        Set<Integer> released = new HashSet<>(session.pressed);
        released.removeAll(pressed);
        session.pressed = pressed;
        if (session.cooldown > 0) session.cooldown--;

        if (session.mode == Mode.BIND) {
            if (newKeys.isEmpty()) return;
            int button = newKeys.stream().sorted().findFirst().orElseThrow();
            BlockPos target = session.bindTarget;
            stop(player, false);
            if (target != null && AllHandle.bindLinkedController(player, button, target))
                player.sendSystemMessage(
                        Component.translatable(
                                "create.linked_controller.key_bound", Component.translatable(KEYS[button])),
                        true);
            return;
        }
        if (!released.isEmpty()) {
            send(player, controller, released, false);
            click(player, .5f);
        }
        if (!newKeys.isEmpty()) {
            send(player, controller, newKeys, true);
            session.cooldown = PACKET_RATE;
            click(player, .75f);
        }
        if (session.cooldown == 0 && !pressed.isEmpty()) {
            send(player, controller, pressed, true);
            session.cooldown = PACKET_RATE;
        }
    }

    private static final String[] KEYS = {"key.forward", "key.back", "key.left", "key.right", "key.jump", "key.sneak"};

    private static void send(ServerPlayer player, ItemStack controller, Set<Integer> buttons, boolean press) {
        if (player.isSpectator() && press) return;
        List<Integer> valid = buttons.stream()
                .filter(b -> b >= 0 && b < 6)
                .distinct()
                .sorted()
                .toList();
        if (valid.isEmpty()) return;
        ServerLevel level = player.level();
        LinkedControllerServerHandler.receivePressed(
                level,
                player.blockPosition(),
                player.getUUID(),
                valid.stream()
                        .map(i -> LinkedControllerItem.toFrequency(controller, i))
                        .collect(Collectors.toList()),
                press);
    }

    private static ItemStack controller(ServerPlayer player, Session session) {
        if (session.lectern != null) {
            if (!player.level().isLoaded(session.lectern)
                    || !player.isWithinBlockInteractionRange(session.lectern, PolymerGuiControls.MENU_REACH_BUFFER)
                    || !(player.level().getBlockEntity(session.lectern) instanceof LecternControllerBlockEntity lectern)
                    || !lectern.isUsedBy(player)) return ItemStack.EMPTY;
            return lectern.getController();
        }
        ItemStack main = player.getMainHandItem();
        if (main.is(AllItems.LINKED_CONTROLLER)) return main;
        ItemStack off = player.getOffhandItem();
        return off.is(AllItems.LINKED_CONTROLLER) ? off : ItemStack.EMPTY;
    }

    private static void click(ServerPlayer player, float pitch) {
        var event = AllSoundEvents.CONTROLLER_CLICK.getMainEvent();
        NativePresentationPolicy.sendPresentation(
                player,
                new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event),
                        SoundSource.BLOCKS,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        .35f,
                        pitch,
                        player.getRandom().nextLong()));
    }
}
