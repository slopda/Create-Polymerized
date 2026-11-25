package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.content.logistics.BigItemStack;
import com.zurrtum.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.zurrtum.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import com.zurrtum.create.infrastructure.packet.c2s.RedstoneRequesterConfigurationPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public final class PolymerRedstoneRequesterGui extends SimpleGui {
    private static final int GHOSTS = 9;
    private static final int GHOST_START = 0;
    private static final int AMOUNT_START = 9;
    private static final int ADDRESS_ROW = 18;
    private static final int ALLOW_PARTIAL = 27, REQUIRE_FULL = 28, CONFIRM = 35;
    private static final int FOREGROUND = 45;

    private static final int SIZE = 54;
    private static final int MAX_AMOUNT = 256;
    private static final int OX = -16, OY = -10, FOOTER_Y = 85;
    private static final int AMOUNT_TEXT = 0,
            ADDRESS_TEXT = PolymerGuiPixelText.flags(GHOSTS * 18),
            ADDRESS_WIDTH = 110;
    private static final Identifier ADDRESS_ACTION = CreateIds.id("polymer_requester_address");
    private static final PolymerDialogForms.TextField ADDRESS_FIELD = new PolymerDialogForms.TextField("address", 25);
    private static final PolymerDialogSessions<AddressEdit> ADDRESS_EDITS =
            new PolymerDialogSessions<>(Duration.ofMinutes(2));

    private record AddressEdit(PolymerRedstoneRequesterGui gui) {}

    private final RedstoneRequesterBlockEntity requester;
    private final ServerLevel level;
    private final RedstoneRequesterMenu source;
    private final int[] amounts = new int[GHOSTS];
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();
    private String address;
    private boolean allowPartial;

    private PolymerRedstoneRequesterGui(
            ServerPlayer player, RedstoneRequesterBlockEntity requester, ServerLevel level) {
        super(MenuType.GENERIC_9x6, player, false);
        this.requester = requester;
        this.level = level;
        this.source = new RedstoneRequesterMenu(0, player.getInventory(), requester);
        for (int i = 0; i < amounts.length; i++) amounts[i] = 1;
        List<BigItemStack> existing = requester.encodedRequest.stacks();
        for (int i = 0; i < Math.min(existing.size(), amounts.length); i++)
            amounts[i] = Math.max(1, existing.get(i).count);
        address = requester.encodedTargetAdress;
        allowPartial = requester.allowPartialRequests;
        PolymerGuiControls.configure(this, PolymerFrogArt.title("redstone_requester"), false);
        refresh();
    }

    public static boolean open(ServerPlayer player, RedstoneRequesterBlockEntity requester) {
        if (PolymerNativeClients.isNative(player)
                || !(requester.getLevel() instanceof ServerLevel level)
                || !validTarget(player, requester, level)) return false;
        ADDRESS_EDITS.bindToServer();
        ADDRESS_EDITS.forget(player.getUUID());
        return new PolymerRedstoneRequesterGui(player, requester, level).open();
    }

    private static boolean validTarget(ServerPlayer player, RedstoneRequesterBlockEntity requester, ServerLevel level) {
        return PolymerGuiControls.targetPresent(player, requester, level, AllBlocks.REDSTONE_REQUESTER)
                && !player.isSpectator()
                && player.mayBuild()
                && player.mayInteract(level, requester.getBlockPos())
                && requester.behaviour.mayInteract(player);
    }

    @Override
    public boolean stillValid() {
        return validTarget(player, requester, level) && source.stillValid(player);
    }

    private void refresh() {
        for (int slot = 0; slot < SIZE; slot++)
            setSlot(slot, PolymerGuiControls.decoration(PolymerArtKit.stack("requester_empty", null), null));
        List<String> text = List.of(allowPartial ? "partial" : "full");
        List<Boolean> pixels = PolymerArtKit.flags(GHOSTS * 18 + ADDRESS_WIDTH);
        PolymerArtKit.write(pixels, ADDRESS_TEXT, 36, ADDRESS_WIDTH, 36, address);

        for (int index = 0; index < GHOSTS; index++) {
            ItemStack item = source.ghostInventory.getItem(index);
            if (item.isEmpty()) {
                clearSlot(GHOST_START + index);
                amounts[index] = 1;
                continue;
            }
            setSlot(
                    GHOST_START + index,
                    PolymerGuiControls.builder(item.copyWithCount(1))
                            .setName(Component.translatable(
                                            "create.gui.factory_panel.send_item",
                                            item.getHoverName().copy().append(" x" + amounts[index]))
                                    .withStyle(s -> s.withColor(0x5391E1).withItalic(false)))
                            .addLoreLine(Component.literal("Click below the item: left +1, right -1, shift ±10")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                            .addLoreLine(Component.literal("Click with an item to replace; empty cursor clears")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                            .build());
            String count = Integer.toString(amounts[index]);
            int cell = 8 + index * 18;
            PolymerArtKit.write(
                    pixels,
                    AMOUNT_TEXT + PolymerGuiPixelText.flags(index * 18),
                    cell,
                    18,
                    cell + 17 - PolymerArtKit.width(count),
                    count);
            setSlot(
                    AMOUNT_START + index,
                    PolymerGuiControls.builder(PolymerArtKit.stack("requester_empty", null))
                            .setName(Component.literal("Amount: " + amounts[index])
                                    .withStyle(ChatFormatting.GOLD))
                            .addLoreLine(
                                    Component.literal("Left: +1   Right: -1").withStyle(ChatFormatting.GRAY))
                            .addLoreLine(Component.literal("Shift: ±10   Range: 1–256")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                            .build());
        }

        setSlot(
                FOREGROUND,
                PolymerGuiControls.decoration(
                        PolymerArtKit.stack("redstone_requester_foreground", text, pixels), null));
        for (int slot = ADDRESS_ROW; slot < ADDRESS_ROW + 9; slot++) {
            var tag = PolymerGuiControls.builder(PolymerArtKit.stack("requester_empty", null));
            if (address.isBlank())
                tag.setName(Component.translatable("create.gui.redstone_requester.requester_address")
                                .withStyle(s -> s.withColor(0x5391E1).withItalic(false)))
                        .addLoreLine(Component.translatable("create.gui.redstone_requester.requester_address_tip")
                                .withStyle(ChatFormatting.GRAY))
                        .addLoreLine(Component.translatable("create.gui.redstone_requester.requester_address_tip_1")
                                .withStyle(ChatFormatting.GRAY));
            else
                tag.setName(Component.translatable("create.gui.redstone_requester.requester_address_given")
                                .withStyle(s -> s.withColor(0x5391E1).withItalic(false)))
                        .addLoreLine(Component.literal("'" + address + "'").withStyle(ChatFormatting.GRAY));
            setSlot(
                    slot,
                    tag.addLoreLine(Component.translatable("create.gui.schedule.lmb_edit")
                                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                            .build());
        }
        for (int slot : new int[] {ALLOW_PARTIAL, ALLOW_PARTIAL + 9})
            setting(slot, "create.gui.redstone_requester.allow_partial");
        for (int slot : new int[] {REQUIRE_FULL, REQUIRE_FULL + 9})
            setting(slot, "create.gui.redstone_requester.dont_allow_partial");
        for (int slot : new int[] {CONFIRM, CONFIRM + 9})
            setSlot(
                    slot,
                    PolymerGuiControls.builder(PolymerArtKit.stack("requester_empty", null))
                            .setName(Component.translatable("create.action.confirm")
                                    .withStyle(s ->
                                            s.withColor(ChatFormatting.WHITE).withItalic(false)))
                            .build());
        lifecycle.requestSync();
    }

    private void setting(int slot, String key) {
        setSlot(
                slot,
                PolymerGuiControls.builder(PolymerArtKit.stack("requester_empty", null))
                        .setName(Component.translatable(key)
                                .withStyle(
                                        s -> s.withColor(ChatFormatting.WHITE).withItalic(false)))
                        .build());
    }

    private static int footer(int slot) {
        return slot >= 36 && slot < 45 ? slot - 9 : slot;
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (slot < 0) return true;
        if (slot >= SIZE) {
            if (action == ContainerInput.QUICK_MOVE) {
                quickMove(slot);
                return false;
            }
            return action != ContainerInput.PICKUP_ALL;
        }

        if (slot >= GHOST_START && slot < GHOST_START + GHOSTS) {
            int index = slot - GHOST_START;
            if (action == ContainerInput.PICKUP || action == ContainerInput.CLONE) {
                source.setCarried(player.containerMenu.getCarried());
                source.clicked(36 + index, click.isRight ? 1 : 0, action, player);
                player.containerMenu.setCarried(source.getCarried());
                source.setCarried(ItemStack.EMPTY);
                refresh();
            } else if (action == ContainerInput.QUICK_MOVE) {
                source.quickMoveStack(player, 36 + index);
                refresh();
            }
            return false;
        }

        if (slot >= AMOUNT_START && slot < AMOUNT_START + GHOSTS && PolymerGuiControls.primaryClick(click, action)) {
            int index = slot - AMOUNT_START;
            if (!source.ghostInventory.getItem(index).isEmpty()) {
                int delta = (click.isLeft ? 1 : -1) * (click.shift ? 10 : 1);
                amounts[index] = Math.clamp((long) amounts[index] + delta, 1, MAX_AMOUNT);
                refresh();
            }
            return false;
        }

        if (!PolymerGuiControls.primaryClick(click, action)) return false;
        if (slot >= ADDRESS_ROW && slot < ADDRESS_ROW + 9) {
            openAddressDialog();
            return false;
        }
        switch (footer(slot)) {
            case ALLOW_PARTIAL -> {
                allowPartial = true;
                refresh();
            }
            case REQUIRE_FULL -> {
                allowPartial = false;
                refresh();
            }
            case CONFIRM -> close();
            default -> {}
        }
        return false;
    }

    @Override
    public ItemStack quickMove(int slot) {
        if (!stillValid()) return ItemStack.EMPTY;
        if (slot >= SIZE && slot < SIZE + 36) source.quickMoveStack(player, slot - SIZE);
        else if (slot >= GHOST_START && slot < GHOST_START + GHOSTS)
            source.quickMoveStack(player, 36 + slot - GHOST_START);
        refresh();
        return ItemStack.EMPTY;
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            BufferedImage sheet = PolymerArtKit.image(builder, "assets/create/textures/gui/requester.png");
            BufferedImage source = sheet.getSubimage(16, 16, 232, 120);
            BufferedImage panel = adaptPanel(source);
            var glyphs = PolymerArtKit.asciiGlyphs(builder);
            String title = PolymerSchematicGuis.lang("block.create.redstone_requester");
            PolymerArtKit.rawText(
                    panel, glyphs, 114 - PolymerGogglesOverlay.width(title) / 2, 4, title, 0x3D3C48, false);
            PolymerFrogArt.panel(builder, "redstone_requester", panel, OX, OY, 6);
            var kit = new PolymerArtKit(builder, "redstone_requester", 16, 116);
            kit.panel("button_partial", PolymerFrogArt.button(builder, 0, 2, 12), 8, FOOTER_Y, .01f);
            kit.panel("button_full", PolymerFrogArt.button(builder, 0, 3, 12), 26, FOOTER_Y, .01f);
            kit.panel("button_confirm", PolymerFrogArt.button(builder, 0, 0, 1), 152, FOOTER_Y, .01f);
            BufferedImage partial = new BufferedImage(36, 18, BufferedImage.TYPE_INT_ARGB);
            BufferedImage full = new BufferedImage(36, 18, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = partial.createGraphics();
            g.drawImage(PolymerFrogArt.button(builder, 72, 2, 12), 0, 0, null);
            g.dispose();
            g = full.createGraphics();
            g.drawImage(PolymerFrogArt.button(builder, 72, 3, 12), 18, 0, null);
            g.dispose();
            kit.layer(0, Map.of("partial", partial, "full", full), 8, FOOTER_Y, .02f);
            for (int i = 0; i < GHOSTS; i++)
                kit.compactLane(
                        AMOUNT_TEXT + PolymerGuiPixelText.flags(i * 18),
                        8 + i * 18,
                        27,
                        18,
                        glyphs,
                        0xFFFFFF,
                        true,
                        .03f);
            kit.compactLane(ADDRESS_TEXT, 36, 59, ADDRESS_WIDTH, glyphs, 0x555555, false, .03f);
            kit.finish("redstone_requester_foreground");
            PolymerArtKit.empty(builder, "requester_empty");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot build Redstone Requester GUI art", exception);
        }
    }

    static BufferedImage adaptPanel(BufferedImage source) {
        BufferedImage panel = new BufferedImage(216, 120, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < panel.getHeight(); y++) {
            boolean shelf = y >= 16 && y < 56;
            for (int x = 0; x < panel.getWidth(); x++) {
                int sx;
                if (shelf) {
                    sx = x;
                    for (int gap = 0; gap < 8; gap++) if (sx >= 41 + 20 * gap) sx += 2;
                } else sx = x < 120 ? x : x + 16;
                panel.setRGB(x, y, source.getRGB(sx, y));
            }
        }
        int body = source.getRGB(190, 100);
        for (int y = 89; y < 118; y++) for (int x = 175; x < 204; x++) panel.setRGB(x, y, body);
        for (int y = 89; y < 118; y++) {
            panel.setRGB(164, y, source.getRGB(193, y));
            panel.setRGB(165, y, source.getRGB(194, y));
        }
        return panel;
    }

    private void openAddressDialog() {
        if (!stillValid()) return;
        CompoundTag addition = ADDRESS_EDITS.open(player.getUUID(), new AddressEdit(this));
        Input input = ADDRESS_FIELD.input(240, Component.literal("Package address"), address);
        var common = new CommonDialogData(
                Component.translatable("block.create.redstone_requester"),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal("Set the package address for this request."), 240)),
                List.of(input));
        Holder<Dialog> dialog = Holder.direct(new NoticeDialog(
                common,
                new ActionButton(
                        new CommonButtonData(Component.literal("Apply address"), 200),
                        Optional.of(new CustomAll(ADDRESS_ACTION, Optional.of(addition))))));
        PolymerDialogForms.open(player, dialog);
    }

    static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!ADDRESS_ACTION.equals(packet.id())) return false;
        ServerPlayer player = listener.player;
        if (!PolymerDialogForms.validSubmission(player, packet.id(), packet.payload(), ADDRESS_ACTION)) return true;
        Optional<CompoundTag> data = PolymerDialogForms.payload(packet.payload());
        if (data.isEmpty()) return true;
        Optional<AddressEdit> session = ADDRESS_EDITS.consume(player.getUUID(), data.get());
        if (session.isEmpty()) return true;
        PolymerRedstoneRequesterGui gui = session.get().gui();
        if (gui.player != player || !gui.stillValid()) return true;
        ADDRESS_FIELD.read(data.get()).ifPresent(value -> {
            gui.address = value;
            gui.refresh();
        });
        return true;
    }

    @Override
    public void onTick() {
        if (!stillValid()) {
            close();
            return;
        }
        if (player.containerMenu instanceof SlotBasedWrapperMenu menu && menu.getBackingGui() == this)
            lifecycle.tick(() -> {}, menu::sendAllDataToRemote);
    }

    private void saveOnClose() {
        lifecycle.close(
                () -> {
                    if (!stillValid()) return;
                    List<Integer> configuredAmounts = new ArrayList<>(GHOSTS);
                    for (int slot = 0; slot < GHOSTS; slot++)
                        if (!source.ghostInventory.getItem(slot).isEmpty()) configuredAmounts.add(amounts[slot]);
                    source.setCarried(ItemStack.EMPTY);
                    source.removed(player);
                    AllHandle.onRedstoneRequesterConfiguration(
                            player.connection,
                            new RedstoneRequesterConfigurationPacket(
                                    requester.getBlockPos(), address, allowPartial, configuredAmounts));
                },
                () -> {
                    ADDRESS_EDITS.forget(player.getUUID());
                    PolymerInventorySync.afterMenu(player);
                });
    }

    @Override
    public void onManualClose() {
        saveOnClose();
    }

    @Override
    public void onPlayerClose(boolean close) {
        saveOnClose();
    }

    @Override
    public void onRemoved() {
        saveOnClose();
        super.onRemoved();
    }
}
