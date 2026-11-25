package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.content.logistics.filter.FilterItem;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.infrastructure.packet.c2s.StockKeeperCategoryEditPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class PolymerStockCategoryGui extends SimpleGui {
    private static final int OX = -13, OY = -8, ROWS = 6, CARDS = 4;
    private static final int HOST = 1, PREVIEW = 36, SCROLL_UP = 43, SCROLL_DOWN = 44, CONFIRM = 53;
    private static final int LANE_X = 42 + OX, LANE_W = 100;
    private static final int MOVE_COLUMN = 7, TRASH_COLUMN = 8;

    private final StockTickerBlockEntity ticker;
    private final Runnable reopen;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();
    private final ServerLevel level;
    private final Block block;
    private int scroll;

    private PolymerStockCategoryGui(ServerPlayer player, StockTickerBlockEntity ticker, Runnable reopen) {
        super(MenuType.GENERIC_9x6, player, false);
        this.ticker = ticker;
        this.reopen = reopen;
        this.level = player.level();
        this.block = ticker.getBlockState().getBlock();
        PolymerGuiControls.configure(this, PolymerFrogArt.title("stock_category"), false);
        draw();
    }

    public static void open(ServerPlayer player, StockTickerBlockEntity ticker, Runnable reopen) {
        new PolymerStockCategoryGui(player, ticker, reopen).open();
    }

    private static int laneY(int row) {
        return 25 + 5 + 18 * row + OY;
    }

    private void draw() {
        List<ItemStack> categories = ticker.categories;
        scroll = Math.clamp(scroll, 0, Math.max(0, categories.size() + 1 - CARDS));
        for (int slot = 0; slot < 54; slot++) clearSlot(slot);
        List<String> strings = new ArrayList<>(PolymerArtKit.blank(CARDS));
        List<Boolean> pixels = PolymerArtKit.flags(CARDS * LANE_W);
        for (int row = 0; row < CARDS; row++) {
            int index = scroll + row;
            if (index < categories.size()) {
                strings.set(row, "card");
                card(row, index, categories.get(index), pixels);
            } else if (index == categories.size()) {
                strings.set(row, "new");
                setSlot(
                        row * 9,
                        PolymerGuiControls.decoration(
                                PolymerArtKit.stack("stock_category_empty", null),
                                List.of(
                                        Component.translatable("create.gui.stock_ticker.new_category")
                                                .withStyle(s -> s.withColor(0x5391E1)),
                                        Component.literal(
                                                        "Click to add; click with a filter to add it with that filter")
                                                .withStyle(ChatFormatting.DARK_GRAY))));
            }
        }
        setSlot(
                HOST,
                PolymerGuiControls.decoration(
                        PolymerArtKit.stack("stock_category_foreground", strings, pixels), hostTip()));
        setSlot(PREVIEW, PolymerGuiControls.decoration(PolymerArtKit.stack("preview_stock_category", null), null));
        if (scroll > 0)
            setSlot(
                    SCROLL_UP,
                    PolymerGuiControls.decoration(
                            PolymerGuiTextures.icon("radial_property_up"),
                            List.of(Component.translatable("create.gui.schedule.move_up"))));
        if (scroll + CARDS <= categories.size())
            setSlot(
                    SCROLL_DOWN,
                    PolymerGuiControls.decoration(
                            PolymerGuiTextures.icon("radial_property_down"),
                            List.of(Component.translatable("create.gui.schedule.move_down"))));
        setSlot(
                CONFIRM,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("stock_category_confirm"),
                        List.of(Component.translatable("create.action.confirm"))));
        lifecycle.requestSync();
    }

    private List<Component> hostTip() {
        int index = scroll;
        List<ItemStack> categories = ticker.categories;
        return index < categories.size() ? nameTip(categories.get(index)) : null;
    }

    private static List<Component> nameTip(ItemStack entry) {
        return List.of(
                entry.isEmpty()
                        ? Component.translatable("create.gui.stock_ticker.empty_category_name_placeholder")
                        : entry.getHoverName(),
                Component.translatable("create.gui.schedule.lmb_edit")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                Component.literal("Click with a filter to swap it").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void card(int row, int index, ItemStack entry, List<Boolean> pixels) {
        setSlot(
                row * 9,
                PolymerGuiControls.decoration(
                        entry.isEmpty() ? PolymerArtKit.stack("stock_category_empty", null) : entry.copyWithCount(1),
                        nameTip(entry)));
        String name = entry.isEmpty()
                ? PolymerSchematicGuis.lang("create.gui.stock_ticker.empty_category_name_placeholder")
                : entry.getHoverName().getString();
        if (name.length() > 20) name = name.substring(0, 20).stripTrailing() + "...";
        PolymerArtKit.write(pixels, PolymerGuiPixelText.flags(row * LANE_W), LANE_X, LANE_W, LANE_X, name);
        for (int column = 1; column < MOVE_COLUMN; column++) {
            if (row * 9 + column != HOST)
                setSlot(
                        row * 9 + column,
                        PolymerGuiControls.decoration(
                                PolymerArtKit.stack("stock_category_empty", null), nameTip(entry)));
        }
        setSlot(
                row * 9 + MOVE_COLUMN,
                PolymerGuiControls.decoration(
                        PolymerArtKit.stack("stock_category_empty", null),
                        List.of(
                                Component.translatable("create.gui.schedule.move_up"),
                                Component.translatable("create.gui.schedule.move_down"),
                                Component.literal("Left-click: up   Right-click: down   Shift: to the end")
                                        .withStyle(ChatFormatting.DARK_GRAY))));
        setSlot(
                row * 9 + TRASH_COLUMN,
                PolymerGuiControls.decoration(
                        PolymerArtKit.stack("stock_category_empty", null),
                        List.of(Component.translatable("create.gui.stock_ticker.delete_category"))));
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (slot < 0) return true;
        if (slot >= 54) return action != ContainerInput.PICKUP_ALL;
        if (!PolymerGuiControls.primaryClick(click, action)) return false;
        if (slot == CONFIRM) {
            if (click.isLeft) close();
            return false;
        }
        if (slot == SCROLL_UP) {
            scroll--;
            draw();
            return false;
        }
        if (slot == SCROLL_DOWN) {
            scroll++;
            draw();
            return false;
        }
        int row = slot / 9, column = slot % 9;
        if (row >= CARDS) return false;
        click(scroll + row, column, click);
        return false;
    }

    private void click(int index, int column, ClickType click) {
        List<ItemStack> categories = new ArrayList<>(ticker.categories);
        ItemStack carried = player.containerMenu.getCarried();
        if (index == categories.size()) {
            if (!(carried.getItem() instanceof FilterItem)) {
                categories.add(ItemStack.EMPTY);
                apply(categories);
                return;
            }
            categories.add(carried.copyWithCount(1));
            carried.shrink(1);
            player.containerMenu.setCarried(carried);
            apply(categories);
            return;
        }
        if (index > categories.size()) return;
        ItemStack entry = categories.get(index);
        if (column == TRASH_COLUMN) {
            if (!click.isLeft) return;
            categories.remove(index);
            if (!entry.isEmpty()) {
                if (carried.isEmpty()) player.containerMenu.setCarried(entry.copy());
                else player.getInventory().placeItemBackInInventory(entry.copy());
            }
            apply(categories);
        } else if (column == MOVE_COLUMN) {
            boolean up = click.isLeft;
            int to = click.shift ? (up ? 0 : categories.size() - 1) : index + (up ? -1 : 1);
            if (to < 0 || to >= categories.size() || to == index) return;
            categories.add(to, categories.remove(index));
            apply(categories);
        } else if (click.isLeft) {
            if (carried.getItem() instanceof FilterItem) {
                ItemStack next = carried.copyWithCount(1);
                carried.shrink(1);
                player.containerMenu.setCarried(carried);
                if (!entry.isEmpty()) player.getInventory().placeItemBackInInventory(entry.copy());
                categories.set(index, next);
                apply(categories);
                return;
            }
            String current = entry.getHoverName().getString();
            PolymerTextPrompt.open(
                    player,
                    Component.translatable("create.gui.stock_ticker.category_editor"),
                    new PolymerTextPrompt.Field(entry.getHoverName(), current, 28),
                    value -> rename(index, entry, value));
        }
    }

    private void rename(int index, ItemStack entry, String value) {
        if (!stillValid()) return;
        List<ItemStack> categories = new ArrayList<>(ticker.categories);
        if (index >= categories.size() || !ItemStack.isSameItemSameComponents(categories.get(index), entry)) return;
        ItemStack named = entry.copy();
        boolean blank = value.isBlank() || value.equals(named.getItemName().getString());
        named.set(DataComponents.CUSTOM_NAME, blank ? null : Component.literal(value));
        categories.set(index, named);
        apply(categories);
    }

    private void apply(List<ItemStack> categories) {
        AllHandle.onStockKeeperCategoryEdit(
                player.connection, new StockKeeperCategoryEditPacket(ticker.getBlockPos(), categories));
        ticker.notifyUpdate();
        player.containerMenu.broadcastChanges();
        draw();
    }

    @Override
    public boolean stillValid() {
        return PolymerGuiControls.editableTarget(player, ticker, level, block);
    }

    @Override
    public void onTick() {
        lifecycle.tickWhileValid(stillValid(), this::close, wrappedMenu);
    }

    @Override
    public void onRemoved() {
        lifecycle.close(() -> {}, () -> PolymerInventorySync.afterMenu(player));
    }

    @Override
    public void onManualClose() {
        var server = player.level().getServer();
        server.schedule(new TickTask(server.getTickCount() + 1, reopen));
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            var glyphs = PolymerArtKit.asciiGlyphs(builder);
            BufferedImage sheet =
                    PolymerArtKit.image(builder, "assets/create/textures/gui/stock_keeper_categories.png");
            BufferedImage art = new BufferedImage(200, 18 + 80 + 33, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = art.createGraphics();
            g.drawImage(sheet.getSubimage(32, 0, 192, 18), 0, 0, null);
            for (int i = 0; i < CARDS; i++) g.drawImage(sheet.getSubimage(32, 32, 192, 20), 0, 18 + 20 * i, null);
            g.drawImage(sheet.getSubimage(32, 79, 200, 33), 0, 98, null);
            g.dispose();
            PolymerFrogArt.erase(art, 165, 101, 33, 28, 90, 114);
            String title = PolymerSchematicGuis.lang("block.create.stock_ticker");
            PolymerArtKit.text(art, glyphs, 96 - PolymerArtKit.width(title) / 2, 4, title, 0x3D3C48, false);
            PolymerFrogArt.panel(builder, "stock_category", art, OX, OY, ROWS);
            PolymerFrogArt.item(
                    builder,
                    "stock_category_confirm",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    CONFIRM,
                    PolymerFrogArt.slotX(CONFIRM) - 1,
                    98);
            var kit = new PolymerArtKit(
                    builder, "stock_category", PolymerFrogArt.slotX(HOST) + 8, PolymerFrogArt.slotY(HOST) + 8);
            kit.allocate(CARDS);
            BufferedImage entry = sheet.getSubimage(38, 159, 171, 18), fresh = sheet.getSubimage(38, 127, 27, 18);
            BufferedImage up = sheet.getSubimage(211, 160, 8, 8), down = sheet.getSubimage(211, 169, 8, 8);
            for (int row = 0; row < CARDS; row++) {
                BufferedImage card = PolymerArtKit.copy(entry);
                Graphics2D c = card.createGraphics();
                c.drawImage(up, PolymerFrogArt.slotX(MOVE_COLUMN) + 4 - (7 + OX), 1, null);
                c.drawImage(down, PolymerFrogArt.slotX(MOVE_COLUMN) + 4 - (7 + OX), 9, null);
                c.dispose();
                kit.layer(row, Map.of("card", card, "new", fresh), 7 + OX, 25 + 18 * row + OY, .01f);
            }
            for (int row = 0; row < CARDS; row++)
                kit.compactLane(
                        PolymerGuiPixelText.flags(row * LANE_W),
                        LANE_X,
                        laneY(row),
                        LANE_W,
                        glyphs,
                        0x656565,
                        false,
                        .02f);
            kit.finish("stock_category_foreground");
            PolymerArtKit.empty(builder, "stock_category_empty");
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot build Stock Keeper category screen art", error);
        }
    }
}
