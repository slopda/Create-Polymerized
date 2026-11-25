package com.zurrtum.create.infrastructure.polymer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiControls;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiLifecycle;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EntityBlock;

public final class PolymerParityCatalogue implements ModInitializer {
    private record Entry(Identifier id, ItemStack stack, boolean obtainable) {}

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = Commands.literal("parity")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(ctx -> open(ctx.getSource().getPlayerOrException(), "items", ""));
            for (String kind : List.of("items", "blocks", "machines"))
                root.then(Commands.literal(kind)
                        .executes(ctx -> open(ctx.getSource().getPlayerOrException(), kind, ""))
                        .then(Commands.argument("search", StringArgumentType.greedyString())
                                .executes(ctx -> open(
                                        ctx.getSource().getPlayerOrException(),
                                        kind,
                                        StringArgumentType.getString(ctx, "search")))));
            dispatcher.register(Commands.literal("createfly").then(root));
        });
    }

    static boolean permitted(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
    }

    private static int open(ServerPlayer player, String kind, String query) {
        if (!permitted(player)) return 0;
        return new Catalogue(player, kind, query).open() ? 1 : 0;
    }

    static List<Entry> entries(String kind, String query) {
        List<Entry> out = new ArrayList<>();
        if (kind.equals("items")) {
            for (var item : BuiltInRegistries.ITEM) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id.getNamespace().equals(Create.MOD_ID))
                    out.add(new Entry(id, item.getDefaultInstance().copy(), true));
            }
        } else if (kind.equals("blocks") || kind.equals("machines")) {
            for (var block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                if (!id.getNamespace().equals(Create.MOD_ID)
                        || kind.equals("machines") && !(block instanceof EntityBlock)) continue;
                boolean obtainable = block.asItem() != Items.AIR;
                out.add(new Entry(
                        id,
                        obtainable ? block.asItem().getDefaultInstance().copy() : new ItemStack(Items.BARRIER),
                        obtainable));
            }
        }
        String match = query.trim().toLowerCase(Locale.ROOT);
        out.removeIf(entry -> !entry.id.toString().contains(match)
                && !entry.stack
                        .getHoverName()
                        .getString()
                        .toLowerCase(Locale.ROOT)
                        .contains(match));
        out.sort(Comparator.comparing(entry -> entry.id.toString()));
        return List.copyOf(out);
    }

    static int grant(ServerPlayer player, Entry entry, boolean stack) {
        if (!permitted(player) || !entry.obtainable) return 0;
        ItemStack fresh = entry.stack.copyWithCount(stack ? entry.stack.getMaxStackSize() : 1);
        int requested = fresh.getCount();
        player.getInventory().add(fresh);
        int inserted = requested - fresh.getCount();
        player.getInventory().setChanged();
        return inserted;
    }

    private static final class Catalogue extends SimpleGui {
        private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();
        private final String kind;
        private final String query;
        private final List<Entry> entries;
        private int page;

        Catalogue(ServerPlayer player, String kind, String query) {
            super(MenuType.GENERIC_9x6, player, false);
            this.kind = kind;
            this.query = query;
            entries = entries(kind, query);
            refresh();
        }

        @Override
        public boolean stillValid() {
            return permitted(player);
        }

        private void refresh() {
            int pages = Math.max(1, (entries.size() + 44) / 45);
            page = Math.max(0, Math.min(page, pages - 1));
            PolymerGuiControls.configure(
                    this,
                    Component.literal("Create " + kind + " · " + (page + 1) + "/" + pages + " · " + entries.size()),
                    false);
            for (int slot = 0; slot < 54; slot++) clearSlot(slot);
            for (int slot = 0; slot < 45 && page * 45 + slot < entries.size(); slot++) {
                Entry entry = entries.get(page * 45 + slot);
                setSlot(
                        slot,
                        PolymerGuiControls.builder(entry.stack.copyWithCount(1))
                                .setName(entry.stack.getHoverName())
                                .addLoreLine(Component.literal(entry.id.toString()))
                                .addLoreLine(Component.literal(
                                        entry.obtainable
                                                ? "Click: one · Shift-click: stack"
                                                : "Helper block — no obtainable item"))
                                .build());
            }
            button(45, Items.ARROW, "Prev page");
            button(46, Items.CHEST, "All items");
            button(47, Items.BRICKS, "All blocks");
            button(48, Items.CRAFTING_TABLE, "GUI / machine candidates");
            button(49, Items.BOOK, "Search and testing help");
            if (floorTests()) {
                button(50, Items.STONE, "Create horizontal movement test");
                button(51, Items.ENDER_PEARL, "Board existing movement test");
                button(52, Items.COMPASS, "Return from movement test");
            }
            button(53, Items.ARROW, "Next page");
        }

        private boolean floorTests() {
            var root = player.level()
                    .getServer()
                    .getCommands()
                    .getDispatcher()
                    .getRoot()
                    .getChild("createfly");
            return root != null && root.getChild("floor") != null;
        }

        private void button(int slot, Item item, String label) {
            setSlot(
                    slot,
                    PolymerGuiControls.builder(item)
                            .setName(Component.literal(label))
                            .build());
        }

        @Override
        public boolean onAnyClick(int slot, ClickType click, ContainerInput input) {
            if (!stillValid()) {
                close();
                return false;
            }
            if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE || !click.isLeft) return false;
            if (slot >= 0 && slot < 45 && page * 45 + slot < entries.size()) {
                Entry entry = entries.get(page * 45 + slot);
                int count = grant(player, entry, input == ContainerInput.QUICK_MOVE);
                if (count == 0)
                    player.sendSystemMessage(Component.literal(
                            entry.obtainable ? "Inventory is full." : "No obtainable item for " + entry.id));
            } else
                switch (slot) {
                    case 45 -> page--;
                    case 53 -> page++;
                    case 46, 47, 48 -> {
                        close();
                        PolymerParityCatalogue.open(
                                player, slot == 46 ? "items" : slot == 47 ? "blocks" : "machines", query);
                        return false;
                    }
                    case 49 ->
                        player.sendSystemMessage(
                                Component.literal(
                                        "Search: /createfly parity items <name> (or blocks/machines). Place a machine to compare its real UI; aim at settings controls or use /createfly settings. Axis/slope rooms are still being prepared."));
                    case 50, 51, 52 -> {
                        if (!floorTests()) return false;
                        close();
                        String command = slot == 50
                                ? "createfly floor demo plain"
                                : slot == 51 ? "createfly floor board" : "createfly floor back";
                        player.level()
                                .getServer()
                                .getCommands()
                                .performPrefixedCommand(player.createCommandSourceStack(), command);
                        return false;
                    }
                    default -> {}
                }
            refresh();
            lifecycle.requestSync();
            lifecycle.tick(() -> {}, wrappedMenu::sendAllDataToRemote);
            return false;
        }

        @Override
        public void onTick() {
            if (!stillValid()) close();
        }

        @Override
        public void onRemoved() {
            lifecycle.close(() -> {}, () -> {});
        }
    }
}
