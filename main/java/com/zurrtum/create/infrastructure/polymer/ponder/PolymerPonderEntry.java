package com.zurrtum.create.infrastructure.polymer.ponder;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.SguiUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class PolymerPonderEntry implements ModInitializer {
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Charging> CHARGING = new HashMap<>();

    private record Pending(
            ServerPlayer player,
            ServerLevel level,
            AbstractContainerMenu menu,
            int slot,
            ItemStack expected,
            PolymerPonderScene.Definition scene) {}

    private record Charging(Pending request, PolymerPonderHold hold) {}

    private static void cancel(UUID id) {
        CHARGING.remove(id);
        PENDING.remove(id);
    }

    record Shown(String item, int bars, int style) {}

    private static final Map<UUID, Shown> SHOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Display> DISPLAY = new HashMap<>();

    private static final class Display {
        final ServerPlayer player;
        final AbstractContainerMenu menu;
        final int slot;
        final ItemStack expected;
        float value;
        Shown shown;

        Display(ServerPlayer player, AbstractContainerMenu menu, int slot, ItemStack expected) {
            this.player = player;
            this.menu = menu;
            this.slot = slot;
            this.expected = expected;
        }

        boolean same(Pending request) {
            return request.player == player
                    && request.menu == menu
                    && request.slot == slot
                    && ItemStack.matches(request.expected, expected);
        }
    }

    static final int STYLES = 8;
    static final int BARS = PolymerArtKit.width("Hold [F] to Ponder") / PolymerArtKit.width("|");

    private static Component hint() {
        return Component.literal("Hold [")
                .withStyle(ChatFormatting.DARK_GRAY)
                .withStyle(style -> style.withItalic(false))
                .append(Component.keybind("key.swapOffhand").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("] to Ponder").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component bars(int filled) {
        var line = Component.literal("|".repeat(filled))
                .withStyle(ChatFormatting.GRAY)
                .withStyle(style -> style.withItalic(false));
        if (filled < BARS)
            line.append(Component.literal("|".repeat(BARS - filled)).withStyle(ChatFormatting.DARK_GRAY));
        return line;
    }

    private static @org.jspecify.annotations.Nullable Shown charging(
            ItemStack original, String item, @org.jspecify.annotations.Nullable UUID viewer) {
        Shown shown = viewer == null ? null : SHOWN.get(viewer);
        return shown != null && shown.item.equals(item) && shown.style > 0 ? shown : null;
    }

    public static void tooltip(
            List<Component> lines, ItemStack original, @org.jspecify.annotations.Nullable UUID viewer) {
        String item = BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
        if (PolymerPonderScenes.forItem(item).isEmpty()) return;
        Shown shown = charging(original, item, viewer);
        Component plain = hint();
        lines.removeIf(existing -> existing.equals(plain));
        lines.add(0, shown != null ? bars(shown.bars) : plain);
    }

    public static void tooltipStyle(
            ItemStack original, ItemStack client, @org.jspecify.annotations.Nullable UUID viewer) {
        String item = BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
        Shown shown = charging(original, item, viewer);
        if (shown != null) client.set(DataComponents.TOOLTIP_STYLE, CreateIds.id("ponder_hold_" + shown.style));
    }

    private static void display(long now) {
        for (var iterator = DISPLAY.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            Display display = entry.getValue();
            Charging charging = CHARGING.get(entry.getKey());
            boolean held = charging != null
                    && charging.request.menu == display.menu
                    && charging.request.slot == display.slot
                    && charging.hold.appearsHeld(now);
            display.value = PolymerPonderHold.step(display.value, held);
            float progress = Math.min(1, display.value * 8 / 7);
            boolean open = display.player.containerMenu == display.menu
                    && !display.player.isRemoved()
                    && display.menu.slots.size() > display.slot
                    && ItemStack.matches(display.menu.getSlot(display.slot).getItem(), display.expected);
            Shown shown = progress <= 0 || !open
                    ? null
                    : new Shown(
                            BuiltInRegistries.ITEM
                                    .getKey(display.expected.getItem())
                                    .toString(),
                            (int) (progress * BARS),
                            Math.max(1, Math.round(progress * STYLES)));
            if (Objects.equals(shown, display.shown)) {
                if (shown == null) iterator.remove();
                continue;
            }
            display.shown = shown;
            if (shown == null) {
                SHOWN.remove(entry.getKey());
                iterator.remove();
                if (open) display.menu.sendAllDataToRemote();
            } else {
                SHOWN.put(entry.getKey(), shown);
                if (open)
                    display.player.connection.send(new ClientboundContainerSetSlotPacket(
                            display.menu.containerId,
                            display.menu.getStateId(),
                            display.slot,
                            display.menu.getSlot(display.slot).getItem()));
            }
        }
    }

    private static void clearDisplay(UUID id, boolean resend) {
        Display display = DISPLAY.remove(id);
        SHOWN.remove(id);
        if (resend && display != null && display.player.containerMenu == display.menu)
            display.menu.sendAllDataToRemote();
    }

    @Override
    public void onInitialize() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerPonderDialog::addAssets);
        PolymerInventorySync.register();
        ServerTickEvents.END_SERVER_TICK.register(PolymerPonderEntry::flush);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            cancel(handler.player.getUUID());
            clearDisplay(handler.player.getUUID(), false);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PENDING.clear();
            CHARGING.clear();
            DISPLAY.clear();
            SHOWN.clear();
        });
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerPonderEntry::addHoldAssets);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("ponder")
                                .then(Commands.literal("scene")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .suggests((context, suggestions) -> {
                                                    PolymerPonderScenes.ALL.forEach(
                                                            scene -> suggestions.suggest(scene.id()));
                                                    return suggestions.buildFuture();
                                                })
                                                .executes(context -> {
                                                    ServerPlayer owner =
                                                            context.getSource().getPlayerOrException();
                                                    if (PolymerNativeClients.isNative(owner)
                                                            || !owner.isAlive()
                                                            || owner.isSpectator()) return 0;
                                                    var scene = PolymerPonderScenes.byId(
                                                            StringArgumentType.getString(context, "id"));
                                                    return scene.isPresent()
                                                                    && PolymerPonderDialog.open(owner, scene.get())
                                                            ? 1
                                                            : 0;
                                                }))))));
    }

    static void addHoldAssets(ResourcePackBuilder builder) {
        String root = "assets/minecraft/textures/gui/sprites/tooltip/";
        byte[] frame = builder.getDataOrSource(root + "frame.png"),
                frameMeta = builder.getDataOrSource(root + "frame.png.mcmeta");
        byte[] background = builder.getDataOrSource(root + "background.png"),
                backgroundMeta = builder.getDataOrSource(root + "background.png.mcmeta");
        if (frame == null || frameMeta == null || background == null || backgroundMeta == null)
            throw new IllegalStateException("Vanilla tooltip sprites unavailable for Ponder hold styles");
        try {
            BufferedImage source = PngCodec.decode(frame);
            for (int style = 1; style <= STYLES; style++) {
                int color = holdColor(style / (float) STYLES);
                var image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < source.getHeight(); y++)
                    for (int x = 0; x < source.getWidth(); x++)
                        if ((source.getRGB(x, y) >>> 24) != 0) image.setRGB(x, y, 0xFF000000 | color);
                String prefix = "assets/create/textures/gui/sprites/tooltip/ponder_hold_" + style;
                builder.addData(prefix + "_frame.png", PngCodec.encode(image));
                builder.addData(prefix + "_frame.png.mcmeta", frameMeta);
                builder.addData(prefix + "_background.png", background);
                builder.addData(prefix + "_background.png.mcmeta", backgroundMeta);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static int holdColor(float progress) {
        return progress < .5f ? mix(0x5000ff, 0x5555ff, progress * 2) : mix(0x5555ff, 0xffffff, (progress - .5f) * 2);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    public static boolean inventorySwap(ServerPlayer player, ServerboundContainerClickPacket packet) {
        AbstractContainerMenu menu = player.containerMenu;
        if (packet.containerInput() != ContainerInput.SWAP
                || packet.buttonNum() != 40
                || PolymerNativeClients.isNative(player)
                || player.isSpectator()
                || !player.isAlive()
                || packet.containerId() != menu.containerId
                || !menu.stillValid(player)
                || SguiUtils.getCurrentGui(player) != null
                || !menu.getClass().getPackageName().equals("net.minecraft.world.inventory")
                || !menu.getCarried().isEmpty()
                || packet.slotNum() < 0
                || packet.slotNum() >= menu.slots.size()) {
            cancel(player.getUUID());
            return false;
        }
        var slot = menu.getSlot(packet.slotNum());
        ItemStack item = slot.getItem();
        var scene = PolymerPonderScenes.forItem(
                BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        if (!slot.mayPickup(player) || scene.isEmpty()) {
            cancel(player.getUUID());
            return false;
        }

        UUID id = player.getUUID();
        Charging charging = CHARGING.get(id);
        boolean continuing = charging != null
                && valid(charging.request, serverOf(player))
                && charging.request.slot == packet.slotNum();
        if (packet.stateId() == menu.getStateId() || continuing) {
            if (!continuing) {
                PENDING.remove(id);
                charging = new Charging(
                        new Pending(player, player.level(), menu, packet.slotNum(), item.copy(), scene.get()),
                        new PolymerPonderHold());
                CHARGING.put(id, charging);
            }
            Display display = DISPLAY.get(id);
            if (display == null || !display.same(charging.request)) {
                clearDisplay(id, false);
                DISPLAY.put(id, new Display(player, menu, packet.slotNum(), item.copy()));
            }
            if (charging.hold.press(System.nanoTime())) {
                PENDING.putIfAbsent(id, charging.request);
            }
        } else cancel(player.getUUID());
        menu.sendAllDataToRemote();
        return true;
    }

    private static void flush(MinecraftServer server) {
        long now = System.nanoTime();
        CHARGING.entrySet()
                .removeIf(entry -> entry.getValue().hold.expired(now) || !valid(entry.getValue().request, server));
        display(now);
        var requests = List.copyOf(PENDING.values());
        PENDING.clear();
        for (Pending request : requests) {
            ServerPlayer player = request.player;
            if (!valid(request, server)) continue;
            CHARGING.remove(player.getUUID());
            clearDisplay(player.getUUID(), true);
            PolymerPonderDialog.open(
                    player,
                    request.scene,
                    BuiltInRegistries.ITEM.getKey(request.expected.getItem()).toString());
        }
    }

    private static MinecraftServer serverOf(ServerPlayer player) {
        return player.level().getServer();
    }

    private static boolean valid(Pending request, MinecraftServer server) {
        ServerPlayer player = request.player;
        return serverOf(player) == server
                && player.level() == request.level
                && player.isAlive()
                && !player.isRemoved()
                && !player.isSpectator()
                && !PolymerNativeClients.isNative(player)
                && player.containerMenu == request.menu
                && request.menu.stillValid(player)
                && request.menu.getCarried().isEmpty()
                && ItemStack.matches(request.menu.getSlot(request.slot).getItem(), request.expected);
    }
}
