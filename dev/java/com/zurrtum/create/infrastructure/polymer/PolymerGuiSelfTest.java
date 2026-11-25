package com.zurrtum.create.infrastructure.polymer;

import com.mojang.authlib.GameProfile;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.equipment.blueprint.BlueprintEntity;
import com.zurrtum.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.zurrtum.create.content.schematics.table.SchematicTableBlockEntity;
import com.zurrtum.create.foundation.utility.CreatePaths;
import com.zurrtum.create.infrastructure.player.FakePlayerNetworkHandler;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerBlueprintGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerRedstoneRequesterGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSchematicTableGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerSchematicannonGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerStockCategoryGui;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

public final class PolymerGuiSelfTest implements ModInitializer {
    private static final BlockPos BASE = new BlockPos(7000, 120, 7000);
    private static final String NAME = "GuiSelfTest";

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 != 0) return;
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("polymerpatch");
            Path trigger = dir.resolve("gui_selftest.run");
            if (!Files.exists(trigger)) return;
            try {
                Files.delete(trigger);
            } catch (Exception ignored) {
                return;
            }
            List<String> log = new ArrayList<>();
            log.add("GUI self-test " + Instant.now());
            run(server, log);
            try {
                Files.createDirectories(dir);
                Files.writeString(
                        dir.resolve("gui_selftest.txt"), String.join("\n", log) + "\n", StandardCharsets.UTF_8);
            } catch (Exception failure) {
                Create.LOGGER.warn("Could not write GUI self-test results", failure);
            }
            log.forEach(line -> Create.LOGGER.info("[gui-selftest] {}", line));
        });
    }

    private static void run(MinecraftServer server, List<String> log) {
        ServerLevel level = server.overworld();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8)), NAME);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new FakePlayerNetworkHandler(server, player);
        try {
            blueprint(level, player, log);
        } catch (Throwable failure) {
            fail(log, "blueprint threw " + failure, failure);
        }
        safeClose(player);
        try {
            table(server, level, player, log);
        } catch (Throwable failure) {
            fail(log, "schematic table threw " + failure, failure);
        }
        safeClose(player);
        try {
            cannon(level, player, log);
        } catch (Throwable failure) {
            fail(log, "schematicannon threw " + failure, failure);
        }
        safeClose(player);
        try {
            requester(level, player, log);
        } catch (Throwable failure) {
            fail(log, "requester threw " + failure, failure);
        }
        safeClose(player);
        try {
            categories(level, player, log);
        } catch (Throwable failure) {
            fail(log, "stock categories threw " + failure, failure);
        }
        safeClose(player);
        try {
            nativeFilters(level, player, log);
        } catch (Throwable failure) {
            fail(log, "native filters threw " + failure, failure);
        }
        safeClose(player);
        long failed = log.stream().filter(line -> line.startsWith("FAIL")).count();
        log.add(failed == 0 ? "ALL PASS" : failed + " FAILED");
    }

    private static void blueprint(ServerLevel level, ServerPlayer player, List<String> log) {
        BlockPos pos = BASE, wall = pos.south(2);
        floor(level, pos);
        for (int x = -1; x <= 1; x++)
            for (int y = 0; y <= 2; y++)
                level.setBlockAndUpdate(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
        AABB area = new AABB(pos).inflate(4);
        level.getEntitiesOfClass(BlueprintEntity.class, area).forEach(BlueprintEntity::discard);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.getInventory().clearContent();
        BlueprintEntity placed = new BlueprintEntity(level, wall.north(), Direction.NORTH, Direction.DOWN);
        if (!check(log, "blueprint: placement survives", placed.survives())) return;
        level.addFreshEntity(placed);
        BlueprintEntity entity = placed;
        check(log, "blueprint: entity alive", entity.isAlive());
        player.getInventory().clearContent();

        check(log, "blueprint: opens", PolymerBlueprintGui.open(player, entity, 0));
        if (!(player.containerMenu instanceof SlotBasedWrapperMenu menu)) {
            check(log, "blueprint: sgui menu", false);
            return;
        }
        var gui = menu.getBackingGui();
        check(log, "blueprint: 54 + 36 slots", menu.slots.size() == 90);

        menu.setCarried(new ItemStack(Items.IRON_INGOT, 5));
        sgui(menu, 1, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        check(
                log,
                "blueprint: grid click sets ghost",
                item(menu, 1).is(Items.IRON_INGOT) && item(menu, 1).getCount() == 1);
        check(
                log,
                "blueprint: carried kept",
                menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 5);
        menu.setCarried(ItemStack.EMPTY);
        sgui(menu, 1, ClickType.MOUSE_RIGHT, ContainerInput.PICKUP);
        check(log, "blueprint: empty right-click clears ghost", item(menu, 1).isEmpty());

        menu.setCarried(new ItemStack(Items.STICK));
        sgui(menu, 10, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        menu.setCarried(ItemStack.EMPTY);
        check(log, "blueprint: second grid cell set", item(menu, 10).is(Items.STICK));
        sgui(menu, 42, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        check(log, "blueprint: Clear empties grid", item(menu, 10).isEmpty());

        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 3));
        player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
        check(log, "blueprint: inventory slot 54 is inventory 9", item(menu, 54).is(Items.IRON_INGOT));
        check(log, "blueprint: inventory slot 81 is hotbar 0", item(menu, 81).is(Items.GOLD_INGOT));
        sgui(menu, 54, ClickType.MOUSE_LEFT_SHIFT, ContainerInput.QUICK_MOVE);
        sgui(menu, 81, ClickType.MOUSE_LEFT_SHIFT, ContainerInput.QUICK_MOVE);
        log.add("info  blueprint: after shift-clicks " + describe(menu, 0, 54));
        check(log, "blueprint: shift-click main row adds ghost", contains(menu, Items.IRON_INGOT));
        check(log, "blueprint: shift-click hotbar adds ghost", contains(menu, Items.GOLD_INGOT));
        check(
                log,
                "blueprint: shift-click keeps inventory",
                player.getInventory().getItem(9).getCount() == 3
                        && player.getInventory().getItem(0).getCount() == 2);

        sgui(menu, 44, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        check(log, "blueprint: Confirm closes", player.containerMenu == player.inventoryMenu);
        PolymerBlueprintGui.open(player, entity, 0);
        if (player.containerMenu instanceof SlotBasedWrapperMenu again) {
            check(
                    log,
                    "blueprint: ghosts saved on close",
                    contains(again, Items.IRON_INGOT) && contains(again, Items.GOLD_INGOT));
            sgui(again, 42, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        } else check(log, "blueprint: reopens", false);
        safeClose(player);
        entity.discard();
        player.getInventory().clearContent();
    }

    private static void table(MinecraftServer server, ServerLevel level, ServerPlayer player, List<String> log)
            throws Exception {
        BlockPos pos = BASE.east(8);
        floor(level, pos);
        level.setBlockAndUpdate(pos, AllBlocks.SCHEMATIC_TABLE.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof SchematicTableBlockEntity table)) {
            check(log, "table: block entity", false);
            return;
        }
        table.inventory.setItem(0, ItemStack.EMPTY);
        table.inventory.setItem(1, ItemStack.EMPTY);
        table.isUploading = false;
        check(log, "table: staged schematic", stage());
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(AllItems.EMPTY_SCHEMATIC));
        player.getInventory().setItem(10, new ItemStack(Items.DIRT, 4));

        check(log, "table: opens", PolymerSchematicTableGui.open(player, table));
        AbstractContainerMenu menu = player.containerMenu;
        if (!check(log, "table: adapter menu", menu instanceof PolymerSchematicTableGui)) return;
        check(log, "table: 36 + 36 slots", menu.slots.size() == 72);

        menu.clicked(37, 0, ContainerInput.QUICK_MOVE, player);
        check(
                log,
                "table: shift-click dirt refused",
                player.getInventory().getItem(10).is(Items.DIRT)
                        && table.inventory.getItem(0).isEmpty());
        menu.clicked(36, 0, ContainerInput.QUICK_MOVE, player);
        check(
                log,
                "table: shift-click empty schematic to input",
                table.inventory.getItem(0).is(AllItems.EMPTY_SCHEMATIC)
                        && player.getInventory().getItem(9).isEmpty());
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        menu.clicked(1, 1, ContainerInput.PICKUP, player);
        check(log, "table: selection bar click keeps menu open", player.containerMenu == menu);
        menu.clicked(5, 0, ContainerInput.PICKUP, player);
        check(log, "table: control slot cannot be taken", menu.getCarried().isEmpty());

        menu.clicked(19, 0, ContainerInput.PICKUP, player);
        check(
                log,
                "table: Confirm writes schematic",
                table.inventory.getItem(1).is(AllItems.SCHEMATIC)
                        && table.inventory.getItem(0).isEmpty()
                        && !table.isUploading);
        menu.clicked(26, 0, ContainerInput.QUICK_MOVE, player);
        boolean inInventory = false;
        for (int i = 0; i < 36; i++)
            inInventory |= player.getInventory().getItem(i).is(AllItems.SCHEMATIC);
        check(
                log,
                "table: shift-click output to inventory",
                table.inventory.getItem(1).isEmpty() && inInventory);

        player.getInventory().setItem(11, new ItemStack(AllItems.EMPTY_SCHEMATIC));
        menu.clicked(38, 0, ContainerInput.PICKUP, player);
        menu.clicked(18, 0, ContainerInput.PICKUP, player);
        check(
                log,
                "table: hand-place into input",
                table.inventory.getItem(0).is(AllItems.EMPTY_SCHEMATIC)
                        && menu.getCarried().isEmpty());
        menu.clicked(18, 0, ContainerInput.PICKUP, player);
        check(
                log,
                "table: pick back out of input",
                menu.getCarried().is(AllItems.EMPTY_SCHEMATIC)
                        && table.inventory.getItem(0).isEmpty());
        menu.setCarried(ItemStack.EMPTY);
        safeClose(player);
        table.inventory.setItem(0, ItemStack.EMPTY);
        table.inventory.setItem(1, ItemStack.EMPTY);
        player.getInventory().clearContent();
    }

    private static void cannon(ServerLevel level, ServerPlayer player, List<String> log) {
        BlockPos pos = BASE.east(16);
        floor(level, pos);
        level.setBlockAndUpdate(pos, AllBlocks.SCHEMATICANNON.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof SchematicannonBlockEntity cannon)) {
            check(log, "cannon: block entity", false);
            return;
        }
        cannon.replaceMode = 0;
        cannon.skipMissing = false;
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        player.getInventory().clearContent();
        check(log, "cannon: opens", PolymerSchematicannonGui.open(player, cannon));
        AbstractContainerMenu menu = player.containerMenu;
        if (!check(log, "cannon: adapter menu", menu instanceof PolymerSchematicannonGui)) return;
        menu.clicked(38, 0, ContainerInput.PICKUP, player);
        check(log, "cannon: hidden option ignored", cannon.replaceMode == 0);
        menu.clicked(36, 0, ContainerInput.PICKUP, player);
        menu.clicked(38, 0, ContainerInput.PICKUP, player);
        check(log, "cannon: toggle shows options, replace solid", cannon.replaceMode == 1);
        menu.clicked(48, 0, ContainerInput.PICKUP, player);
        check(log, "cannon: row 5 repeats row 4 (replace any)", cannon.replaceMode == 2);
        menu.clicked(41, 0, ContainerInput.PICKUP, player);
        check(log, "cannon: skip missing toggles", cannon.skipMissing);
        menu.clicked(41, 0, ContainerInput.PICKUP, player);
        menu.clicked(36, 0, ContainerInput.PICKUP, player);
        menu.clicked(39, 0, ContainerInput.PICKUP, player);
        check(log, "cannon: toggle hides options again", cannon.replaceMode == 2 && !cannon.skipMissing);
        player.getInventory().setItem(9, new ItemStack(Items.GUNPOWDER, 3));
        menu.clicked(54, 0, ContainerInput.QUICK_MOVE, player);
        check(
                log,
                "cannon: shift-click gunpowder to fuel",
                cannon.inventory.getItem(4).is(Items.GUNPOWDER));
        menu.clicked(44, 0, ContainerInput.PICKUP, player);
        check(log, "cannon: Done closes", player.containerMenu == player.inventoryMenu);
        cannon.inventory.setItem(4, ItemStack.EMPTY);
        player.getInventory().clearContent();
    }

    private static void requester(ServerLevel level, ServerPlayer player, List<String> log) {
        BlockPos pos = BASE.east(24);
        floor(level, pos);
        level.setBlockAndUpdate(pos, AllBlocks.REDSTONE_REQUESTER.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof RedstoneRequesterBlockEntity requester)) {
            check(log, "requester: block entity", false);
            return;
        }
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        player.getInventory().clearContent();
        check(log, "requester: opens", PolymerRedstoneRequesterGui.open(player, requester));
        if (!(player.containerMenu instanceof SlotBasedWrapperMenu menu)) {
            check(log, "requester: sgui menu", false);
            return;
        }
        check(log, "requester: 54 + 36 slots", menu.slots.size() == 90);
        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        sgui(menu, 0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        menu.setCarried(ItemStack.EMPTY);
        check(log, "requester: ghost on shelf cell 0", item(menu, 0).is(Items.IRON_INGOT));
        sgui(menu, 9, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        sgui(menu, 9, ClickType.MOUSE_LEFT_SHIFT, ContainerInput.QUICK_MOVE);
        sgui(menu, 37, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        sgui(menu, 44, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
        check(log, "requester: Confirm (row 4) closes", player.containerMenu == player.inventoryMenu);
        var stacks = requester.encodedRequest.stacks();
        check(log, "requester: amount 1 + 1 + 10 saved", stacks.size() == 1 && stacks.getFirst().count == 12);
        check(log, "requester: full-order button (row 4) saved", !requester.allowPartialRequests);
        PolymerRedstoneRequesterGui.open(player, requester);
        if (player.containerMenu instanceof SlotBasedWrapperMenu again) {
            sgui(again, 27, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            sgui(again, 0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            check(log, "requester: empty cursor clears ghost", item(again, 0).isEmpty());
            sgui(again, 35, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
            check(log, "requester: partial button (row 3) saved", requester.allowPartialRequests);
        } else check(log, "requester: reopens", false);
        safeClose(player);
    }

    private static void categories(ServerLevel level, ServerPlayer player, List<String> log) {
        BlockPos pos = BASE.east(32);
        floor(level, pos);
        level.setBlockAndUpdate(pos, AllBlocks.STOCK_TICKER.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker)) {
            check(log, "categories: block entity", false);
            return;
        }
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() - 1.5);
        player.getInventory().clearContent();
        PolymerStockCategoryGui.open(player, ticker, () -> {});
        check(
                log,
                "categories: opens (" + player.containerMenu.getClass().getSimpleName() + ")",
                player.containerMenu instanceof SlotBasedWrapperMenu);
    }

    @SuppressWarnings("unchecked")
    private static void nativeFilters(ServerLevel level, ServerPlayer player, List<String> log) throws Exception {
        var field = PolymerNativeClients.class.getDeclaredField("NATIVE");
        field.setAccessible(true);
        Set<UUID> natives = (Set<UUID>) field.get(null);
        natives.add(player.getUUID());
        try {
            for (var item : List.of(AllItems.FILTER, AllItems.ATTRIBUTE_FILTER, AllItems.PACKAGE_FILTER)) {
                safeClose(player);
                player.getInventory().clearContent();
                ItemStack stack = new ItemStack(item);
                player.getInventory().setItem(player.getInventory().getSelectedSlot(), stack);
                var result = stack.use(level, player, InteractionHand.MAIN_HAND);
                String menu = player.containerMenu == player.inventoryMenu
                        ? "none"
                        : player.containerMenu.getClass().getSimpleName();
                check(
                        log,
                        "native " + item + ": use " + result + ", source menu " + menu,
                        player.containerMenu != player.inventoryMenu
                                && !(player.containerMenu instanceof SlotBasedWrapperMenu));
            }
        } finally {
            natives.remove(player.getUUID());
            safeClose(player);
            player.getInventory().clearContent();
        }
    }

    private static boolean stage() throws Exception {
        Path base = CreatePaths.UPLOADED_SCHEMATICS_DIR.toAbsolutePath().normalize();
        Path mine = base.resolve(NAME);
        Files.createDirectories(mine);
        try (Stream<Path> own = Files.list(mine)) {
            if (own.anyMatch(p -> p.toString().endsWith(".nbt"))) return true;
        }
        try (Stream<Path> all = Files.walk(base, 2)) {
            Path source = all.filter(
                            p -> p.toString().endsWith(".nbt") && !p.getParent().equals(mine))
                    .findFirst()
                    .orElse(null);
            if (source == null) return false;
            Files.copy(source, mine.resolve("selftest.nbt"), StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
    }

    private static void floor(ServerLevel level, BlockPos pos) {
        level.getChunk(pos);
        for (int x = -2; x <= 2; x++)
            for (int z = -3; z <= 3; z++) {
                level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                if (z != 2) level.setBlockAndUpdate(pos.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            }
    }

    private static void sgui(SlotBasedWrapperMenu menu, int slot, ClickType type, ContainerInput action) {
        var gui = menu.getBackingGui();
        if (gui.onAnyClick(slot, type, action) && slot >= 0 && slot < gui.getSize()) gui.click(slot, type, action);
    }

    private static ItemStack item(AbstractContainerMenu menu, int slot) {
        return menu.getSlot(slot).getItem();
    }

    private static boolean contains(AbstractContainerMenu menu, Item wanted) {
        for (int i = 0; i < 54; i++) if (item(menu, i).is(wanted)) return true;
        return false;
    }

    private static String describe(AbstractContainerMenu menu, int from, int to) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < to; i++) {
            ItemStack stack = item(menu, i);
            if (!stack.isEmpty() && !stack.has(DataComponents.ITEM_MODEL))
                out.append(i).append('=').append(stack.getItem()).append(' ');
            else if (!stack.isEmpty()
                    && (i == 1 || i == 2 || i == 3 || i == 10 || i == 11 || i == 12 || i == 19 || i == 20 || i == 21
                            || i == 15 || i == 25))
                out.append(i).append('=').append(stack.getItem()).append(' ');
        }
        return out.toString().trim();
    }

    private static boolean check(List<String> log, String name, boolean ok) {
        log.add((ok ? "PASS  " : "FAIL  ") + name);
        return ok;
    }

    private static void fail(List<String> log, String message, Throwable failure) {
        log.add("FAIL  " + message);
        Create.LOGGER.warn("GUI self-test failure", failure);
    }

    private static void safeClose(ServerPlayer player) {
        try {
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        } catch (Throwable ignored) {
        }
    }
}
