package com.zurrtum.create.infrastructure.polymer.gui;

import static dev.veeda.createpoly.assets.CreateIds.id;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItemTags;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.logistics.BigItemStack;
import com.zurrtum.create.content.logistics.filter.FilterItem;
import com.zurrtum.create.content.logistics.filter.FilterItemStack;
import com.zurrtum.create.content.logistics.packager.InventorySummary;
import com.zurrtum.create.content.logistics.packagerLink.LogisticsNetwork;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlock;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.infrastructure.component.ClipboardEntry;
import com.zurrtum.create.infrastructure.component.PackageOrderWithCrafts;
import com.zurrtum.create.infrastructure.packet.c2s.PackageOrderRequestPacket;
import com.zurrtum.create.infrastructure.packet.c2s.StockKeeperCategoryHidingPacket;
import com.zurrtum.create.infrastructure.packet.c2s.StockKeeperLockPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class PolymerStockMarketGui {
    private static final Identifier SEARCH_ACTION = id("polymer_stock_search");
    private static final Identifier ADDRESS_ACTION = id("polymer_stock_address");
    private static final Identifier GUI_FONT = id("stock_keeper_sgui");

    private static final int STOCK_START = 9;
    private static final int STOCK_SLOTS = 45;
    private static final int CART_START = 54;
    private static final int CART_SLOTS = 9;
    private static final int ADDRESS_START = 72;
    private static final int ADDRESS_END = 76;
    private static final int SEND_START = 78;
    private static final int SEND_END = 80;
    private static final int PREVIOUS_SLOT = 1;
    private static final int NEXT_SLOT = 7;
    private static final int MAX_CANDIDATES = 999;
    private static final int MAX_PER_ITEM = 999;
    private static final int MAX_SEARCH = 50;
    private static final int MAX_ADDRESS = 25;
    private static final long SESSION_LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(3);

    private static final Map<UUID, MarketSession> SESSIONS = PolymerDialogSessions.states();

    private PolymerStockMarketGui() {}

    public static void register() {
        PolymerInventorySync.register();
        PolymerDialogSessions.bindStates(SESSIONS);
    }

    public static void clear(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public static boolean open(ServerPlayer player, StockTickerBlockEntity ticker) {
        if (!validTarget(player, ticker)) {
            error(player, "This Stock Keeper is unavailable or out of reach.");
            return true;
        }

        clear(player.getUUID());
        ItemStack held = player.getMainHandItem();
        boolean encode = held.is(AllItemTags.TABLE_CLOTHS) || held.is(AllItems.REDSTONE_REQUESTER);
        MarketSession session = new MarketSession(
                ticker,
                ticker.getBlockPos().immutable(),
                player.level().dimension(),
                ticker.previouslyUsedAddress,
                System.nanoTime() + SESSION_LIFETIME_NANOS,
                encode ? held : null,
                encode ? held.copy() : ItemStack.EMPTY);
        if (held.is(AllItems.CLIPBOARD)) fillFromClipboard(session, held);
        session.gui = new MarketGui(player, session);
        PolymerGuiControls.configure(session.gui, screenTitle(), true);
        SESSIONS.put(player.getUUID(), session);
        refresh(player, session);
        session.gui.open();
        return true;
    }

    private static void fillFromClipboard(MarketSession session, ItemStack clipboard) {
        InventorySummary available = session.ticker.getAccurateSummary();
        for (List<ClipboardEntry> page : ClipboardEntry.readAll(clipboard)) {
            for (ClipboardEntry entry : page) {
                if (entry.icon.isEmpty() || session.cart.size() >= CART_SLOTS) continue;
                int amount = Math.min(MAX_PER_ITEM, Math.min(entry.itemAmount, available.getCountOf(entry.icon)));
                if (amount <= 0) continue;
                BigItemStack existing = findCart(session, entry.icon);
                if (existing == null) session.cart.add(new BigItemStack(entry.icon.copyWithCount(1), amount));
                else existing.count = Math.min(MAX_PER_ITEM, existing.count + amount);
            }
        }
    }

    public static boolean handleCustomAction(
            ServerGamePacketListenerImpl listener, ServerboundCustomClickActionPacket packet) {
        if (!PolymerDialogForms.validSubmission(
                listener.player, packet.id(), packet.payload(), SEARCH_ACTION, ADDRESS_ACTION, GUI_FONT)) return true;
        if (SEARCH_ACTION.equals(packet.id())) {
            applyTextAction(listener.player, packet.payload(), true);
            return true;
        }
        if (ADDRESS_ACTION.equals(packet.id())) {
            applyTextAction(listener.player, packet.payload(), false);
            return true;
        }
        return false;
    }

    private static Component screenTitle() {
        Component art = Component.literal("\uE101\uE100\uE102")
                .withStyle(style ->
                        style.withFont(new FontDescription.Resource(GUI_FONT)).withColor(0xFFFFFF));
        Component title = Component.literal("Stock Keeper")
                .withStyle(style -> style.withColor(0x714A40)
                        .withFont(PolymerScreenArt.textFont(PolymerScreenArt.MENU_TITLE_RAISED_2)));
        return Component.empty().append(art).append(title);
    }

    private static void refresh(ServerPlayer player, MarketSession session) {
        if (!validSession(player, session)) {
            clear(player.getUUID());
            if (session.gui != null && session.gui.isOpen()) {
                session.gui.close();
            }
            error(player, "The Stock Keeper screen expired or is no longer reachable.");
            return;
        }
        session.expiresAtNanos = System.nanoTime() + SESSION_LIFETIME_NANOS;
        reloadStock(session);
        revalidateCart(session);

        SimpleGui gui = session.gui;
        for (int slot = 0; slot < gui.getSize(); slot++) {
            gui.setSlot(slot, filler(slot));
        }

        for (int slot = 2; slot <= 6; slot++) {
            gui.setSlot(
                    slot,
                    control(
                            slot,
                            "Search Items",
                            session.search.isBlank()
                                    ? "Click to search. Supports @mod and #tag."
                                    : "Current: " + session.search,
                            () -> openTextDialog(player, session, true)));
        }

        if (session.ticker.behaviour.mayAdministrate(player)) {
            boolean locked = isNetworkLocked(session.ticker);
            gui.setSlot(
                    8,
                    icon(
                            locked ? "stock_lock_locked" : "stock_lock_open",
                            locked ? "Network Locked" : "Network Open",
                            "Click to toggle direct ordering",
                            () -> {
                                if (!validSession(player, session)) {
                                    return;
                                }
                                AllHandle.onStockKeeperLock(
                                        player.connection,
                                        new StockKeeperLockPacket(session.pos, !isNetworkLocked(session.ticker)));
                                refresh(player, session);
                            }));
        }

        if (session.ticker.behaviour.mayAdministrate(player)) {
            gui.setSlot(
                    7,
                    PolymerGuiControls.builder(new ItemStack(AllItems.FILTER))
                            .setName(Component.translatable("create.gui.stock_ticker.category_editor")
                                    .withStyle(ChatFormatting.GOLD))
                            .addLoreLine(Component.literal("Group stock under filter categories")
                                    .withStyle(ChatFormatting.GRAY))
                            .setCallback((slot, type, action, g) -> {
                                if (type.isDragging || !validSession(player, session)) return;
                                StockTickerBlockEntity ticker = session.ticker;
                                PolymerStockCategoryGui.open(player, ticker, () -> {
                                    if (player.containerMenu == player.inventoryMenu) open(player, ticker);
                                });
                            })
                            .build());
        }

        List<List<Stock>> rows = rows(withCategoryHeaders(player, session, filteredStock(session)));
        int pageCount = Math.max(1, (rows.size() + STOCK_ROWS - 1) / STOCK_ROWS);
        session.page = Math.max(0, Math.min(session.page, pageCount - 1));
        Stock[] headers = new Stock[STOCK_ROWS];
        List<String> cells = new ArrayList<>(Collections.nCopies(STOCK_SLOTS, ""));
        for (int r = 0; r < STOCK_ROWS && session.page * STOCK_ROWS + r < rows.size(); r++) {
            List<Stock> row = rows.get(session.page * STOCK_ROWS + r);
            int base = STOCK_START + r * 9;
            if (row.getFirst().count == HEADER) {
                headers[r] = row.getFirst();
                for (int column = 0; column < 9; column++)
                    gui.setSlot(base + column, headerElement(player, session, row.getFirst(), base + column));
                continue;
            }
            for (int column = 0; column < row.size(); column++) {
                gui.setSlot(base + column, stockElement(player, session, row.get(column), base + column));
                cells.set(r * 9 + column, "on");
            }
        }
        gui.setSlot(TEXT_HOST, textHost(player, session, headers));
        gui.setSlot(0, inventoryLabelCover(cells));

        for (int i = 0; i < session.cart.size() && i < CART_SLOTS; i++) {
            gui.setSlot(CART_START + i, cartElement(player, session, session.cart.get(i)));
        }

        for (int slot = ADDRESS_START; slot <= ADDRESS_END; slot++) {
            gui.setSlot(
                    slot,
                    control(
                            slot,
                            "Package Address",
                            session.address.isBlank() ? "Click to set an address" : "Current: " + session.address,
                            () -> openTextDialog(player, session, false)));
        }
        boolean encode = session.encodes();
        String programmed = encode ? session.heldSnapshot.getHoverName().getString() : "";
        for (int slot = SEND_START; slot <= SEND_END; slot++) {
            GuiElement send = control(
                    slot,
                    encode ? "Configure" : "Send",
                    session.cart.isEmpty()
                            ? (encode
                                    ? "Choose the items for the held " + programmed + " first"
                                    : "Add items to the order first")
                            : encode
                                    ? "Program the held " + programmed + " with " + session.cart.size() + " item type"
                                            + (session.cart.size() == 1 ? "" : "s")
                                    : "Request " + session.cart.size() + " item type"
                                            + (session.cart.size() == 1 ? "" : "s"),
                    () -> sendOrder(player, session));
            if (encode && slot == SEND_END) PolymerGuiPreviews.variant(send.getItemStack(), session.heldSnapshot);
            gui.setSlot(slot, send);
        }

        gui.setSlot(
                PREVIOUS_SLOT,
                icon(
                        "stock_page_previous",
                        session.page > 0 ? "Previous page" : "First page",
                        "Page " + (session.page + 1) + " of " + pageCount,
                        () -> {
                            session.page = Math.max(0, session.page - 1);
                            refresh(player, session);
                        }));
        gui.setSlot(
                NEXT_SLOT,
                icon(
                        "stock_page_next",
                        session.page + 1 < pageCount ? "Next page" : "Last page",
                        "Page " + (session.page + 1) + " of " + pageCount,
                        () -> {
                            session.page = Math.min(pageCount - 1, session.page + 1);
                            refresh(player, session);
                        }));
    }

    private static List<Stock> withCategoryHeaders(ServerPlayer player, MarketSession session, List<Stock> stock) {
        List<ItemStack> categories = session.ticker.categories;
        if (categories.isEmpty() || !session.search.isBlank()) return stock;
        List<Integer> hidden = session.ticker.hiddenCategoriesByPlayer.getOrDefault(player.getUUID(), List.of());
        List<Stock> result = new ArrayList<>();
        int current = -1;
        for (Stock entry : stock) {
            if (entry.category != current) {
                current = entry.category;
                ItemStack icon = current < categories.size() ? categories.get(current) : new ItemStack(Items.CHEST);
                result.add(new Stock(icon, HEADER, current));
            }
            if (!hidden.contains(current)) result.add(entry);
        }
        return result;
    }

    private static final int HEADER = -1;
    private static final int STOCK_ROWS = STOCK_SLOTS / 9;

    private static List<List<Stock>> rows(List<Stock> visible) {
        List<List<Stock>> rows = new ArrayList<>();
        List<Stock> current = new ArrayList<>();
        for (Stock stock : visible) {
            if (stock.count == HEADER) {
                if (!current.isEmpty()) rows.add(current);
                current = new ArrayList<>();
                rows.add(List.of(stock));
                continue;
            }
            current.add(stock);
            if (current.size() == 9) {
                rows.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) rows.add(current);
        return rows;
    }

    private static boolean collapsed(ServerPlayer player, MarketSession session, Stock header) {
        return session.ticker
                .hiddenCategoriesByPlayer
                .getOrDefault(player.getUUID(), List.of())
                .contains(header.category);
    }

    private static Component headerName(MarketSession session, Stock header) {
        return header.category >= session.ticker.categories.size()
                ? Component.translatable("create.gui.stock_keeper.unsorted_category")
                : header.stack.getHoverName().copy();
    }

    private static GuiElement headerElement(ServerPlayer player, MarketSession session, Stock header, int cell) {
        List<Integer> hidden = session.ticker.hiddenCategoriesByPlayer.getOrDefault(player.getUUID(), List.of());
        boolean collapsed = collapsed(player, session, header);
        Component name = headerName(session, header);
        return PolymerGuiControls.builder(Items.PAPER)
                .model(id("ui/stock_patch_" + cell))
                .setName(Component.literal(collapsed ? "▶ " : "▼ ").append(name).withStyle(ChatFormatting.YELLOW))
                .addLoreLine(Component.literal(collapsed ? "Click to expand" : "Click to collapse")
                        .withStyle(ChatFormatting.GRAY))
                .setCallback((slot, type, action, g) -> {
                    if (type.isDragging || !validSession(player, session)) return;
                    List<Integer> next = new ArrayList<>(hidden);
                    if (collapsed) next.remove(Integer.valueOf(header.category));
                    else next.add(header.category);
                    AllHandle.onStockKeeperCategoryHiding(
                            player.connection, new StockKeeperCategoryHidingPacket(session.pos, next));
                    refresh(player, session);
                })
                .build();
    }

    private static GuiElement stockElement(ServerPlayer player, MarketSession session, Stock stock, int slot) {
        if (stock.count == HEADER) return headerElement(player, session, stock, slot);
        BigItemStack ordered = findCart(session, stock.stack);
        int remaining = stock.count >= BigItemStack.INF
                ? stock.count
                : Math.max(0, stock.count - (ordered == null ? 0 : ordered.count));
        ItemStack shown = remaining > 0
                ? PolymerMarketItems.display(stock.stack, remaining)
                : filler(slot).getItemStack().copy();
        return PolymerGuiControls.builder(shown)
                .setName(stock.stack.getHoverName())
                .addLoreLine(Component.literal("In stock: " + remaining).withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Left: +1   Right: -1").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Shift-click: ±" + Math.max(1, stock.stack.getMaxStackSize()))
                        .withStyle(ChatFormatting.DARK_GRAY))
                .setCallback((clickedSlot, click, action, gui) ->
                        adjustCart(player, session, stock.stack, stock.count, click))
                .build();
    }

    private static GuiElement cartElement(ServerPlayer player, MarketSession session, BigItemStack ordered) {
        ItemStack shown = PolymerMarketItems.display(ordered.stack, ordered.count);
        return PolymerGuiControls.builder(shown)
                .setName(ordered.stack.getHoverName())
                .addLoreLine(Component.literal("Ordered: " + ordered.count).withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal("Left: +1   Right: -1").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Shift-right removes quickly").withStyle(ChatFormatting.DARK_GRAY))
                .setCallback((slot, click, action, gui) -> {
                    int available = session.ticker.getAccurateSummary().getCountOf(ordered.stack);
                    adjustCart(player, session, ordered.stack, available, click);
                })
                .build();
    }

    private static void adjustCart(
            ServerPlayer player, MarketSession session, ItemStack item, int available, ClickType click) {
        if (!validSession(player, session) || click.isDragging || !click.isLeft && !click.isRight) {
            return;
        }
        int step = click.shift ? Math.max(1, item.getMaxStackSize()) : 1;
        int delta = click.isRight ? -step : step;
        BigItemStack existing = findCart(session, item);
        int current = existing == null ? 0 : existing.count;
        int maximum = Math.min(MAX_PER_ITEM, available >= BigItemStack.INF ? MAX_PER_ITEM : Math.max(0, available));
        int updated = Math.max(0, Math.min(maximum, current + delta));

        if (updated > 0 && existing == null && session.cart.size() >= CART_SLOTS) {
            error(player, "The visible order row is full (9 distinct item types).");
            return;
        }
        if (updated == 0) {
            if (existing != null) {
                session.cart.remove(existing);
            }
        } else if (existing == null) {
            session.cart.add(new BigItemStack(item.copyWithCount(1), updated));
        } else {
            existing.count = updated;
        }
        refresh(player, session);
    }

    private static @Nullable BigItemStack findCart(MarketSession session, ItemStack stack) {
        for (BigItemStack ordered : session.cart) {
            if (ItemStack.isSameItemSameComponents(ordered.stack, stack)) {
                return ordered;
            }
        }
        return null;
    }

    private static void openTextDialog(ServerPlayer player, MarketSession session, boolean search) {
        if (!validSession(player, session)) {
            error(player, "The Stock Keeper screen expired.");
            return;
        }
        String initial = search ? session.search : session.address;
        String key = search ? "search" : "address";
        int maxLength = search ? MAX_SEARCH : MAX_ADDRESS;
        Component label = Component.literal(search ? "Search Items" : "Package Address");
        String help = search
                ? "Filter the visible stock. Use @namespace for a mod and #tag for an item tag. Empty text shows everything."
                : "Set the package delivery address. Leave it blank for an empty address.";

        List<Input> inputs = List.of(PolymerDialogForms.input(
                key, PolymerDialogForms.textControl(240, label, true, initial, maxLength, Optional.empty())));
        List<DialogBody> body = List.of(new PlainMessage(Component.literal(help), 280));
        CommonDialogData data = new CommonDialogData(
                Component.literal(search ? "Search Stock" : "Package Address"),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                body,
                inputs);
        ActionButton apply = new ActionButton(
                new CommonButtonData(Component.literal(search ? "Apply Search" : "Save Address"), 160),
                Optional.of(new CustomAll(search ? SEARCH_ACTION : ADDRESS_ACTION, Optional.empty())));
        PolymerDialogForms.open(player, Holder.direct(new NoticeDialog(data, apply)));
    }

    private static void applyTextAction(ServerPlayer player, Optional<Tag> optionalPayload, boolean search) {
        MarketSession session = SESSIONS.get(player.getUUID());
        if (session == null
                || !validSession(player, session)
                || optionalPayload.isEmpty()
                || !(optionalPayload.get() instanceof CompoundTag tag)) {
            error(player, "That Stock Keeper form expired. Open the Stock Keeper again.");
            return;
        }
        String key = search ? "search" : "address";
        Optional<String> value = tag.getString(key);
        if (value.isEmpty()) {
            error(player, "The Stock Keeper form did not contain the expected text field.");
            return;
        }
        String text = value.get().trim();
        int max = search ? MAX_SEARCH : MAX_ADDRESS;
        if (text.length() > max || text.codePoints().anyMatch(Character::isISOControl)) {
            error(player, "That text is too long or contains unsupported control characters.");
            return;
        }
        if (search) {
            session.search = text;
            session.page = 0;
        } else {
            session.address = text;
        }
        refresh(player, session);
    }

    private static void sendOrder(ServerPlayer player, MarketSession session) {
        if (!validSession(player, session)) {
            error(player, "The Stock Keeper screen expired.");
            return;
        }
        if (session.cart.isEmpty()) {
            return;
        }

        boolean encode = session.encodes();
        InventorySummary current = session.ticker.getAccurateSummary();
        List<BigItemStack> order = new ArrayList<>(session.cart.size());
        for (BigItemStack requested : session.cart) {
            int available = current.getCountOf(requested.stack);
            if (requested.count <= 0
                    || requested.count > MAX_PER_ITEM
                    || !encode && available < requested.count && available < BigItemStack.INF) {
                error(
                        player,
                        requested.stack.getHoverName().getString()
                                + " no longer has enough stock. The order was not sent.");
                refresh(player, session);
                return;
            }
            order.add(new BigItemStack(requested.stack.copyWithCount(1), requested.count));
        }

        AllHandle.onPackageOrderRequest(
                player.connection,
                new PackageOrderRequestPacket(
                        session.pos, PackageOrderWithCrafts.simple(order), session.address, encode));
        if (encode) {
            clear(player.getUUID());
            player.getInventory().setChanged();
            return;
        }
        session.cart.clear();
        session.page = 0;
        refresh(player, session);
    }

    private static void reloadStock(MarketSession session) {
        InventorySummary summary = session.ticker.getAccurateSummary();
        List<Stock> stock = new ArrayList<>();
        for (BigItemStack entry : summary.getStacksByCount()) {
            if (stock.size() >= MAX_CANDIDATES) {
                break;
            }
            if (!entry.stack.isEmpty() && entry.count > 0) {
                stock.add(
                        new Stock(entry.stack.copyWithCount(1), entry.count, categoryFor(session.ticker, entry.stack)));
            }
        }
        stock.sort(Comparator.comparingInt((Stock value) -> value.category)
                .thenComparing(value -> value.stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        session.stock = List.copyOf(stock);
    }

    private static void revalidateCart(MarketSession session) {
        InventorySummary summary = session.ticker.getAccurateSummary();
        session.cart.removeIf(entry -> {
            int available = summary.getCountOf(entry.stack);
            if (available <= 0) {
                return true;
            }
            if (available < BigItemStack.INF) {
                entry.count = Math.min(entry.count, Math.min(MAX_PER_ITEM, available));
            } else {
                entry.count = Math.min(entry.count, MAX_PER_ITEM);
            }
            return entry.count <= 0;
        });
    }

    private static List<Stock> filteredStock(MarketSession session) {
        String raw = session.search.trim();
        if (raw.isEmpty()) {
            return session.stock;
        }
        boolean mod = raw.startsWith("@");
        boolean tag = raw.startsWith("#");
        String needle = (mod || tag ? raw.substring(1) : raw).toLowerCase(Locale.ROOT);
        List<Stock> result = new ArrayList<>();
        for (Stock stock : session.stock) {
            ItemStack stack = stock.stack;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean matches;
            if (mod) {
                matches = id.getNamespace().toLowerCase(Locale.ROOT).contains(needle);
            } else if (tag) {
                matches = stack.tags()
                        .anyMatch(key -> key.location()
                                .toString()
                                .toLowerCase(Locale.ROOT)
                                .contains(needle));
            } else {
                matches = stack.getHoverName()
                                .getString()
                                .toLowerCase(Locale.ROOT)
                                .contains(needle)
                        || id.getPath().toLowerCase(Locale.ROOT).contains(needle);
            }
            if (matches) {
                result.add(stock);
            }
        }
        return result;
    }

    private static int categoryFor(StockTickerBlockEntity ticker, ItemStack item) {
        for (int i = 0; i < ticker.categories.size(); i++) {
            ItemStack filter = ticker.categories.get(i);
            if (!filter.isEmpty()
                    && filter.getItem() instanceof FilterItem
                    && FilterItemStack.of(filter).test(ticker.getLevel(), item)) {
                return i;
            }
        }
        return ticker.categories.size();
    }

    private static boolean isNetworkLocked(StockTickerBlockEntity ticker) {
        LogisticsNetwork network = Create.LOGISTICS.logisticsNetworks.get(ticker.behaviour.freqId);
        return network != null && network.locked;
    }

    private static GuiElement filler(int slot) {
        return PolymerGuiControls.builder(Items.PAPER)
                .model(id("ui/stock_patch_" + slot))
                .setName(Component.literal(" "))
                .hideTooltip()
                .build();
    }

    private static final int TEXT_HOST = 89;
    private static final int SEARCH_X = 46, SEARCH_Y = 22, SEARCH_W = 98;
    private static final int ADDRESS_X = 9, ADDRESS_Y = 175, ADDRESS_W = 100;
    private static final int SEND_X = 117, SEND_Y = 176, SEND_W = 64;
    private static final int SEARCH_LANE = 0,
            ADDRESS_LANE = PolymerGuiPixelText.flags(SEARCH_W),
            HINT_LANE = ADDRESS_LANE + PolymerGuiPixelText.flags(ADDRESS_W),
            SEND_LANE = HINT_LANE + PolymerGuiPixelText.flags(ADDRESS_W),
            TEXT_W = SEARCH_W + 2 * ADDRESS_W + SEND_W;
    private static final int HEADER_X = 8, HEADER_W = 160, HEADER_TEXT_X = HEADER_X + 8;
    private static final int TEXT_FLAGS = TEXT_W + STOCK_ROWS * HEADER_W;

    private static int headerLane(int row) {
        return PolymerGuiPixelText.flags(TEXT_W + row * HEADER_W);
    }

    private static int headerY(int row) {
        return 18 + 18 * (row + 1) + 4;
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            var glyphs = PolymerArtKit.asciiGlyphs(builder);
            var kit = new PolymerArtKit(builder, "stock_text", 160, 206);
            kit.panel(
                    "patch",
                    PolymerArtKit.image(builder, "assets/create/textures/item/ui/stock_patch_" + TEXT_HOST + ".png"),
                    151,
                    197,
                    0);
            kit.compactLane(SEARCH_LANE, SEARCH_X, SEARCH_Y, SEARCH_W, glyphs, 0x4A2D31, false, .03f);
            kit.compactLane(ADDRESS_LANE, ADDRESS_X, ADDRESS_Y, ADDRESS_W, glyphs, 0x714A40, false, .03f);
            kit.compactLane(HINT_LANE, ADDRESS_X, ADDRESS_Y, ADDRESS_W, glyphs, 0xCDBCA8, false, .03f);
            kit.compactLane(SEND_LANE, SEND_X, SEND_Y, SEND_W, glyphs, 0x252525, false, .03f);
            for (int row = 0; row < STOCK_ROWS; row++)
                kit.compactLane(headerLane(row), HEADER_X, headerY(row), HEADER_W, glyphs, 0xF8F8EC, true, .03f);
            kit.finish("stock_text");
            var cellKit = new PolymerArtKit(builder, "stock_cells", 16, 26);
            JsonObject cover = JsonParser.parseString(new String(
                            builder.getDataOrSource("assets/create/items/ui/stock_inventory_label_cover.json"),
                            StandardCharsets.UTF_8))
                    .getAsJsonObject();
            cover.getAsJsonObject("model").getAsJsonArray("models").forEach(cellKit.models::add);
            BufferedImage source = PolymerArtKit.image(builder, "assets/create/textures/gui/stock_keeper.png")
                    .getSubimage(32, 200, 18, 18);
            BufferedImage cell = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++) cell.setRGB(x, y, source.getRGB(x == 15 ? 17 : x, y == 15 ? 17 : y));
            for (int i = 0; i < STOCK_SLOTS; i++)
                cellKit.layer(i, Map.of("on", cell), 8 + 18 * (i % 9), 36 + 18 * (i / 9), 0);
            cellKit.finish("stock_cells");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot build Stock Keeper text lanes", exception);
        }
    }

    private static GuiElement textHost(ServerPlayer player, MarketSession session, Stock[] headers) {
        List<Boolean> pixels = PolymerArtKit.flags(TEXT_FLAGS);
        for (int row = 0; row < headers.length; row++) {
            if (headers[row] == null) continue;
            int lane = headerLane(row);
            if (collapsed(player, session, headers[row]))
                for (int dx = 0; dx < 3; dx++)
                    for (int dy = dx; dy < 5 - dx; dy++)
                        pixels.set(lane + (1 + dx) * PolymerGuiPixelText.ROWS + 1 + dy, true);
            else
                for (int dy = 0; dy < 3; dy++)
                    for (int dx = dy; dx < 5 - dy; dx++)
                        pixels.set(lane + dx * PolymerGuiPixelText.ROWS + 2 + dy, true);
            PolymerArtKit.write(
                    pixels,
                    lane,
                    HEADER_X,
                    HEADER_W,
                    HEADER_TEXT_X,
                    headerName(session, headers[row]).getString());
        }
        if (session.search.isBlank()) {
            String hint = PolymerSchematicGuis.lang("create.gui.stock_keeper.search_items");
            PolymerArtKit.write(pixels, SEARCH_LANE, SEARCH_X, SEARCH_W, 88 - PolymerArtKit.width(hint) / 2, hint);
        } else PolymerArtKit.write(pixels, SEARCH_LANE, SEARCH_X, SEARCH_W, SEARCH_X, session.search);
        if (session.address.isBlank())
            PolymerArtKit.write(
                    pixels,
                    HINT_LANE,
                    ADDRESS_X,
                    ADDRESS_W,
                    ADDRESS_X,
                    PolymerSchematicGuis.lang("create.gui.stock_keeper.package_address"));
        else PolymerArtKit.write(pixels, ADDRESS_LANE, ADDRESS_X, ADDRESS_W, ADDRESS_X, session.address);
        String send = PolymerSchematicGuis.lang(
                session.encodes() ? "create.gui.stock_keeper.configure" : "create.gui.stock_keeper.send");
        PolymerArtKit.write(pixels, SEND_LANE, SEND_X, SEND_W, 149 - PolymerArtKit.width(send) / 2, send);
        return PolymerGuiControls.builder(PolymerArtKit.stack("stock_text", List.of(), pixels))
                .setName(Component.literal(" "))
                .hideTooltip()
                .build();
    }

    private static GuiElement inventoryLabelCover(List<String> cells) {
        return PolymerGuiControls.builder(PolymerArtKit.stack("stock_cells", cells))
                .setName(Component.literal(" "))
                .hideTooltip()
                .build();
    }

    private static GuiElement control(int slot, String title, String detail, Runnable click) {
        return PolymerGuiControls.builder(Items.PAPER)
                .model(id("ui/stock_patch_" + slot))
                .setName(Component.literal(title).withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal(detail).withStyle(ChatFormatting.GRAY))
                .setCallback((clickedSlot, type, action, gui) -> {
                    if (!type.isDragging) {
                        click.run();
                    }
                })
                .build();
    }

    private static GuiElement icon(String model, String title, String detail, Runnable click) {
        return PolymerGuiControls.builder(Items.PAPER)
                .model(id("ui/" + model))
                .setName(Component.literal(title).withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal(detail).withStyle(ChatFormatting.GRAY))
                .setCallback((slot, type, action, gui) -> {
                    if (!type.isDragging) {
                        click.run();
                    }
                })
                .build();
    }

    private static boolean validSession(ServerPlayer player, MarketSession session) {
        return session.expiresAtNanos > System.nanoTime()
                && player.level().dimension().equals(session.dimension)
                && session.pos.equals(session.ticker.getBlockPos())
                && validTarget(player, session.ticker)
                && (!session.encodes() || heldUnchanged(player, session));
    }

    private static boolean heldUnchanged(ServerPlayer player, MarketSession session) {
        ItemStack held = player.getMainHandItem();
        return held == session.heldReference && ItemStack.isSameItemSameComponents(held, session.heldSnapshot);
    }

    private static boolean validTarget(ServerPlayer player, StockTickerBlockEntity ticker) {
        if (!(ticker.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos pos = ticker.getBlockPos();
        return player.level() == level
                && !player.isSpectator()
                && player.mayBuild()
                && level.hasChunkAt(pos)
                && !ticker.isRemoved()
                && level.getBlockEntity(pos) == ticker
                && level.getBlockState(pos).getBlock() instanceof StockTickerBlock
                && pos.closerThan(player.blockPosition(), 20)
                && player.mayInteract(level, pos)
                && ticker.behaviour.mayInteract(player);
    }

    private static void error(ServerPlayer player, String message) {
        player.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static final class MarketGui extends SimpleGui {
        private final MarketSession session;
        private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();

        private MarketGui(ServerPlayer player, MarketSession session) {
            super(MenuType.GENERIC_9x6, player, true);
            this.session = session;
        }

        @Override
        public void onRemoved() {
            lifecycle.close(() -> {}, () -> PolymerInventorySync.afterMenu(player));
        }

        @Override
        public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
            lifecycle.requestSync();
            return validSession(player, session)
                    && !click.isDragging
                    && (action == ContainerInput.PICKUP || action == ContainerInput.QUICK_MOVE);
        }

        @Override
        public void onTick() {
            if (!validSession(player, session)) {
                close();
                clear(player.getUUID());
                return;
            }
            if (player.containerMenu instanceof SlotBasedWrapperMenu menu && menu.getBackingGui() == this)
                lifecycle.tick(() -> {}, menu::sendAllDataToRemote);
        }
    }

    private record Stock(ItemStack stack, int count, int category) {}

    private static final class MarketSession {
        private final StockTickerBlockEntity ticker;
        private final BlockPos pos;
        private final ResourceKey<Level> dimension;
        private final List<BigItemStack> cart = new ArrayList<>();
        private List<Stock> stock = List.of();
        private String search = "";
        private String address;
        private int page;
        private long expiresAtNanos;
        private SimpleGui gui;
        private final @Nullable ItemStack heldReference;

        private final ItemStack heldSnapshot;

        private MarketSession(
                StockTickerBlockEntity ticker,
                BlockPos pos,
                ResourceKey<Level> dimension,
                String address,
                long expiresAtNanos,
                @Nullable ItemStack heldReference,
                ItemStack heldSnapshot) {
            this.ticker = ticker;
            this.pos = pos;
            this.dimension = dimension;
            this.address = address;
            this.expiresAtNanos = expiresAtNanos;
            this.heldReference = heldReference;
            this.heldSnapshot = heldSnapshot;
        }

        private boolean encodes() {
            return heldReference != null;
        }
    }
}
