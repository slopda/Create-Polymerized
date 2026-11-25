package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.zurrtum.create.content.schematics.cannon.SchematicannonMenu;
import com.zurrtum.create.infrastructure.packet.c2s.ConfigureSchematicannonPacket;
import com.zurrtum.create.infrastructure.packet.c2s.ConfigureSchematicannonPacket.Option;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PolymerSchematicannonGui extends SchematicannonMenu {
    private static final int SIZE = 54, FOREGROUND = 1, STRINGS = 9, LANE = 0, WIDTH = 146;
    private static final int[] REAL = {18, 26, 6, 8, 0};
    private static final int TOGGLE = 36, DONE = 44, STRIP_Y = 98;
    private SimpleContainer controls;
    private boolean showOptions;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(4, false, true);

    private PolymerSchematicannonGui(int id, Inventory inventory, SchematicannonBlockEntity cannon) {
        super(id, inventory, cannon);
    }

    public static boolean open(ServerPlayer player, SchematicannonBlockEntity cannon) {
        if (!NativePresentationPolicy.allowsPresentation(player)) return false;
        return player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new PolymerSchematicannonGui(id, inv, cannon), Component.literal(" ")))
                .isPresent();
    }

    @Override
    public MenuType<?> getType() {
        return MenuType.GENERIC_9x6;
    }

    @Override
    protected void addSlots() {
        controls = new SimpleContainer(SIZE);
        for (int cell = 0; cell < SIZE; cell++) {
            int source = -1;
            for (int i = 0; i < REAL.length; i++) if (REAL[i] == cell) source = i;
            int x = 8 + cell % 9 * 18, y = 18 + cell / 9 * 18;
            if (source >= 0) {
                final int sourceIndex = source;
                addSlot(new Slot(contentHolder.inventory, sourceIndex, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return contentHolder.inventory.canPlaceItem(sourceIndex, stack);
                    }
                });
            } else addSlot(new ControlSlot(controls, cell, x, y));
        }
        addPlayerSlots(8, 139);
    }

    private static final class ControlSlot extends Slot {
        ControlSlot(SimpleContainer c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player p) {
            return false;
        }
    }

    private static boolean real(int cell) {
        for (int value : REAL) if (value == cell) return true;
        return false;
    }

    private static String text(String suffix) {
        return PolymerSchematicGuis.lang("create.gui.schematicannon." + suffix);
    }

    private void refresh() {
        if (controls == null) return;
        var be = contentHolder;
        List<String> strings = PolymerArtKit.blank(STRINGS);
        strings.set(0, Integer.toString(Math.clamp(Math.round(be.schematicProgress * 102), 0, 102)));
        strings.set(1, Integer.toString(Math.clamp(Math.round(be.bookPrintingProgress * 16), 0, 16)));
        strings.set(2, be.state.name().toLowerCase(Locale.ROOT));
        strings.set(3, showOptions ? Integer.toString(be.replaceMode) : "");
        strings.set(
                4,
                Integer.toString(Math.clamp(
                        Math.round(be.remainingFuel / (float) Math.max(1, be.getShotsPerGunpowder()) * 47), 0, 47)));
        strings.set(5, be.hasCreativeCrate ? "creative" : "");
        strings.set(6, showOptions ? Boolean.toString(be.skipMissing) : "");
        strings.set(7, showOptions ? Boolean.toString(!be.replaceBlockEntities) : "");
        strings.set(8, showOptions ? "shown" : "");
        String status = PolymerSchematicGuis.lang("create.schematicannon.status." + be.statusMsg);
        if (status.startsWith("create.")) status = be.statusMsg;
        List<Boolean> pixels = PolymerArtKit.flags(WIDTH);
        int width = PolymerArtKit.width(status) + (be.missingItem == null || be.missingItem.isEmpty() ? 0 : 16);
        PolymerArtKit.write(pixels, LANE, 8, WIDTH, Math.max(8, 96 - width / 2), status);
        for (int cell = 0; cell < SIZE; cell++) {
            if (real(cell)) continue;
            ItemStack icon = PolymerArtKit.stack(
                    cell == FOREGROUND ? "schematicannon_foreground" : "schematicannon_empty",
                    cell == FOREGROUND ? strings : null,
                    cell == FOREGROUND ? pixels : null);
            Component title = Component.literal(label(cell));
            icon = PolymerGuiControls.nativeDecoration(
                    icon,
                    title,
                    List.of(
                            Component.literal("Fuel shots: " + be.remainingFuel),
                            Component.literal("Placed: " + be.blocksPlaced + " / " + be.blocksToPlace),
                            Component.literal("Missing: "
                                    + (be.missingItem == null || be.missingItem.isEmpty()
                                            ? "none"
                                            : be.missingItem.getHoverName().getString()))));
            if (!ItemStack.matches(controls.getItem(cell), icon)) controls.setItem(cell, icon);
        }
    }

    private static int strip(int cell) {
        return cell >= 45 && cell < SIZE ? cell - 9 : cell;
    }

    private String label(int cell) {
        int c = strip(cell);
        if (!showOptions && c >= 37 && c <= 42) return text("listPrinter");
        return switch (c) {
            case 30 -> "Play";
            case 31 -> "Pause";
            case 32 -> "Stop";
            case TOGGLE -> text("showOptions");
            case 37 -> text("option.dontReplaceSolid");
            case 38 -> text("option.replaceWithSolid");
            case 39 -> text("option.replaceWithAny");
            case 40 -> text("option.replaceWithEmpty");
            case 41 -> text("option.skipMissing") + ": " + contentHolder.skipMissing;
            case 42 -> text("option.skipBlockEntities") + ": " + !contentHolder.replaceBlockEntities;
            case DONE -> PolymerSchematicGuis.lang("create.action.confirm");
            default -> text("listPrinter");
        };
    }

    @Override
    public void broadcastChanges() {
        if (controls != null && lifecycle != null) lifecycle.tick(this::refresh, () -> {});
        super.broadcastChanges();
    }

    @Override
    public void clicked(int cell, int button, ContainerInput input, Player p) {
        if (!stillValid(p)) return;
        if (cell >= 0 && cell < SIZE && !real(cell)) {
            if (input == ContainerInput.PICKUP && (button == 0 || button == 1) && p instanceof ServerPlayer owner) {
                int c = strip(cell);
                Option option = switch (c) {
                    case 30 -> Option.PLAY;
                    case 31 -> Option.PAUSE;
                    case 32 -> Option.STOP;
                    case 37 -> Option.DONT_REPLACE;
                    case 38 -> Option.REPLACE_SOLID;
                    case 39 -> Option.REPLACE_ANY;
                    case 40 -> Option.REPLACE_EMPTY;
                    case 41 -> Option.SKIP_MISSING;
                    case 42 -> Option.SKIP_BLOCK_ENTITIES;
                    default -> null;
                };
                if (option != null && c >= 37 && !showOptions) option = null;
                if (option != null) {
                    boolean value = option == Option.SKIP_MISSING
                            ? !contentHolder.skipMissing
                            : option == Option.SKIP_BLOCK_ENTITIES ? !contentHolder.replaceBlockEntities : true;
                    AllHandle.onConfigureSchematicannon(
                            owner.connection, new ConfigureSchematicannonPacket(option, value));
                    contentHolder.setChanged();
                    refresh();
                } else if (c == TOGGLE) {
                    showOptions = !showOptions;
                    refresh();
                } else if (c == DONE) owner.closeContainer();
            }
            sendAllDataToRemote();
            return;
        }
        super.clicked(cell, button, input, p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (!stillValid(p) || index < 0 || index >= slots.size() || index < SIZE && !real(index))
            return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        boolean moved = false;
        if (index < SIZE) moved = moveItemStackTo(stack, SIZE, slots.size(), true);
        else
            for (int source : new int[] {0, 2, 4}) {
                int cell = REAL[source];
                if (slots.get(cell).mayPlace(stack) && moveItemStackTo(stack, cell, cell + 1, false)) {
                    moved = true;
                    break;
                }
            }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean canDragTo(Slot s) {
        return !(s instanceof ControlSlot) && super.canDragTo(s);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack s, Slot slot) {
        return !(slot instanceof ControlSlot) && super.canTakeItemForPickAll(s, slot);
    }

    @Override
    public boolean stillValid(Player p) {
        return p == player
                && p instanceof ServerPlayer owner
                && !PolymerNativeClients.isNative(owner)
                && p.mayBuild()
                && !p.isSpectator()
                && !contentHolder.isRemoved()
                && p.level() == contentHolder.getLevel()
                && p.level().getBlockEntity(contentHolder.getBlockPos()) == contentHolder
                && p.level().getBlockState(contentHolder.getBlockPos()).is(AllBlocks.SCHEMATICANNON)
                && owner.isWithinBlockInteractionRange(
                        contentHolder.getBlockPos(), PolymerGuiControls.MENU_REACH_BUFFER)
                && owner.mayInteract(owner.level(), contentHolder.getBlockPos())
                && super.stillValid(p);
    }

    @Override
    public void removed(Player p) {
        lifecycle.close(() -> super.removed(p), () -> {
            if (p instanceof ServerPlayer owner) PolymerInventorySync.afterMenu(owner);
        });
    }

    public static void addAssets(ResourcePackBuilder builder) throws IOException {
        var glyphs = PolymerArtKit.asciiGlyphs(builder);
        BufferedImage atlas = PolymerArtKit.image(builder, "assets/create/textures/gui/schematics_2.png");
        BufferedImage art = new BufferedImage(213, 141, BufferedImage.TYPE_INT_ARGB);
        var g = art.createGraphics();
        g.drawImage(atlas.getSubimage(0, 77, 213, 42), 0, 0, null);
        g.drawImage(atlas.getSubimage(0, 119, 213, 99), 0, 42, null);
        g.dispose();
        BufferedImage canvas = new BufferedImage(201, 230, BufferedImage.TYPE_INT_ARGB);
        g = canvas.createGraphics();
        for (int y = 0; y < 121; y++)
            for (int x = 0; x < 201; x++) {
                int sourceY = y < 43 ? y : y < 108 ? y + 10 : y + 14;
                canvas.setRGB(x, y, art.getRGB(x < 120 ? x : x + 12, sourceY));
            }
        for (int y = 67; y < 92; y++)
            for (int x = 67; x < 140; x++) canvas.setRGB(x, y, art.getRGB(40 + x % 4, 90 + y % 4));
        for (int y = 92; y < 95; y++)
            for (int x = 67; x < 140; x++) canvas.setRGB(x, y, art.getRGB(40 + x % 4, y + 10));
        for (int y = 97; y < 119; y++) for (int x = 3; x < 191; x++) canvas.setRGB(x, y, art.getRGB(100, 120));
        for (int y = 97; y < 119; y++) {
            canvas.setRGB(157, y, art.getRGB(174, 120));
            canvas.setRGB(158, y, art.getRGB(175, 120));
        }
        for (int x = 7; x < 183; x++) canvas.setRGB(x, 121, PolymerFrogArt.STRIP);
        for (int y = 0; y < 13; y++)
            for (int x = 0; x < 205; x++) {
                if (x >= 120 && x < 132) continue;
                int argb = atlas.getRGB(x, y + 2);
                if ((argb >>> 24) != 0) canvas.setRGB(x < 120 ? x : x - 12, y, argb);
            }
        String name = PolymerSchematicGuis.lang("block.create.schematicannon");
        PolymerArtKit.text(canvas, glyphs, (193 - PolymerArtKit.width(name)) / 2, 2, name, 0x505050, false);
        BufferedImage inventory = PolymerArtKit.image(builder, "assets/create/textures/gui/player_inventory.png");
        g.drawImage(inventory.getSubimage(0, 0, 176, 108), 7, 122, null);
        g.dispose();
        var kit = new PolymerArtKit(builder, "schematicannon", 34, 26);
        kit.allocate(STRINGS);
        kit.panel("panel", canvas, -7, -1, 0);
        int[][] buttons = {{30, 0, 5}, {31, 1, 5}, {32, 2, 5}, {TOGGLE, 3, 5}, {DONE, 0, 1}};
        for (int[] button : buttons) {
            int y = button[0] == TOGGLE || button[0] == DONE ? STRIP_Y : 18 + button[0] / 9 * 18;
            kit.panel(
                    "button_" + button[0],
                    PolymerFrogArt.button(builder, 0, button[1], button[2]),
                    8 + button[0] % 9 * 18,
                    y,
                    .02f);
        }
        int[][] options = {{0, 2}, {1, 2}, {2, 2}, {3, 2}, {1, 3}, {2, 3}};
        BufferedImage normal = new BufferedImage(108, 18, BufferedImage.TYPE_INT_ARGB);
        var og = normal.createGraphics();
        for (int i = 0; i < 6; i++)
            og.drawImage(PolymerFrogArt.button(builder, 0, options[i][0], options[i][1]), i * 18, 0, null);
        og.dispose();
        kit.layer(8, Map.of("shown", normal), 26, STRIP_Y, .02f);
        Map<String, BufferedImage> replaceGreen = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            BufferedImage green = new BufferedImage(72, 18, BufferedImage.TYPE_INT_ARGB);
            var gg = green.createGraphics();
            gg.drawImage(PolymerFrogArt.button(builder, 72, options[i][0], options[i][1]), i * 18, 0, null);
            gg.dispose();
            replaceGreen.put(Integer.toString(i), green);
        }
        kit.layer(3, replaceGreen, 26, STRIP_Y, .025f);
        kit.layer(6, Map.of("true", PolymerFrogArt.button(builder, 72, 1, 3)), 98, STRIP_Y, .025f);
        kit.layer(7, Map.of("true", PolymerFrogArt.button(builder, 72, 2, 3)), 116, STRIP_Y, .025f);
        Map<String, BufferedImage> stateIndicators = new LinkedHashMap<>();
        String[] states = {"running", "paused", "stopped"};
        int[] colors = {0xff90c052, 0xffe8c53a, 0xffd8403a};
        for (int i = 0; i < 3; i++) stateIndicators.put(states[i], indicator(54, i * 18, colors[i]));
        kit.layer(2, stateIndicators, 62, 69, .025f);
        kit.compactLane(LANE, 8, 42, WIDTH, glyphs, 0xDDEEFF, true, .03f);
        BufferedImage progress = new BufferedImage(102, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 102; x++)
            for (int y = 0; y < 16; y++) progress.setRGB(x, y, atlas.getRGB(76 + (x < 76 ? x : x + 12), 239 + y));
        kit.bar(0, "build_progress", progress, 37, 53, .03f);
        kit.bar(1, "book_progress", atlas.getSubimage(191, 240, 16, 14), 135, 19, .03f);
        kit.bar(4, "fuel", atlas.getSubimage(28, 222, 47, 16), 29, 18, .03f);
        kit.layer(5, Map.of("creative", atlas.getSubimage(28, 239, 47, 16)), 29, 18, .031f);
        kit.finish("schematicannon_foreground");
        PolymerArtKit.empty(builder, "schematicannon_empty");
    }

    private static BufferedImage indicator(int width, int x, int color) {
        var image = new BufferedImage(width, 2, BufferedImage.TYPE_INT_ARGB);
        for (int px = x; px < Math.min(x + 16, width); px++) for (int y = 0; y < 2; y++) image.setRGB(px, y, color);
        return image;
    }
}
