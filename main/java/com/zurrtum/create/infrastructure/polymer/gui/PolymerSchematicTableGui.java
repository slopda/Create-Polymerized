package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.schematics.table.SchematicTableBlockEntity;
import com.zurrtum.create.foundation.utility.CreatePaths;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerSchematicFiles;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PolymerSchematicTableGui extends ChestMenu {
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle(20, false);
    private static final int AX = -13, AY = -5, ART_W = 214, ART_H = 85;
    private static final int FOREGROUND = 0, CONFIRM = 19, INPUT = 18, OUTPUT = 26;
    private static final int LABEL_X = 51 + AX, LABEL_Y = 26 + AY, LABEL_W = 132;
    private static final int BAR_X = 70 + AX, BAR_Y = 59 + AY, BAR_W = 84;
    private static final int TITLE = 0, NONE = 1, BAR = 2, STRINGS = 3;
    private static final int LANE_IDLE = 0, LANE_BUSY = PolymerGuiPixelText.flags(LABEL_W);
    private static final String FILE_PATTERN = "[A-Za-z0-9][A-Za-z0-9_.-]{0,63}\\.nbt";

    private final SchematicTableBlockEntity table;
    private final ServerPlayer owner;
    private final SimpleContainer controls = new SimpleContainer(36);
    private List<String> files = List.of();
    private int selected;

    private PolymerSchematicTableGui(
            int id, Inventory inventory, SchematicTableBlockEntity table, ServerPlayer player) {
        super(MenuType.GENERIC_9x4, id, inventory, new SimpleContainer(36), 4);
        this.table = table;
        this.owner = player;
        for (int i = 0; i < 36; i++) {
            Slot slot = i == INPUT
                    ? new TableSlot(table.inventory, 0, true)
                    : i == OUTPUT ? new TableSlot(table.inventory, 1, false) : new ControlSlot(controls, i);
            slot.index = i;
            slots.set(i, slot);
        }
        refreshFiles();
        refresh();
    }

    public static boolean open(ServerPlayer player, SchematicTableBlockEntity table) {
        if (!NativePresentationPolicy.allowsPresentation(player)) return false;
        if (!(table.getLevel() instanceof ServerLevel)) return false;
        return player.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> new PolymerSchematicTableGui(id, inventory, table, player),
                        PolymerFrogArt.title("schematic_table")))
                .isPresent();
    }

    private final class TableSlot extends Slot {
        private final boolean input;

        TableSlot(Container container, int index, boolean input) {
            super(container, index, 0, 0);
            this.input = input;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return input
                    && (stack.is(AllItems.EMPTY_SCHEMATIC)
                            || stack.is(AllItems.SCHEMATIC_AND_QUILL)
                            || stack.is(AllItems.SCHEMATIC));
        }
    }

    private static final class ControlSlot extends Slot {
        ControlSlot(Container container, int index) {
            super(container, index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    private boolean canWrite() {
        return !table.inventory.getItem(0).isEmpty()
                && table.inventory.getItem(1).isEmpty()
                && !table.isUploading;
    }

    private void refreshFiles() {
        List<String> next = staged(owner.getGameProfile().name());
        String current = selected < files.size() ? files.get(selected) : null;
        files = next;
        selected = current == null ? 0 : Math.max(0, files.indexOf(current));
    }

    private static List<String> staged(String owner) {
        try {
            Path base = CreatePaths.UPLOADED_SCHEMATICS_DIR.toAbsolutePath().normalize();
            Path directory = base.resolve(owner).normalize();
            if (!base.equals(directory.getParent())
                    || Files.isSymbolicLink(base)
                    || Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
            long maxBytes = AllConfigs.server().schematics.maxTotalSchematicSize.get() * 1000L;
            try (Stream<Path> list = Files.list(directory)) {
                return list.filter(path ->
                                !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> {
                            try {
                                return maxBytes > 0 && Files.size(path) <= maxBytes;
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.matches(FILE_PATTERN))
                        .sorted()
                        .limit(256)
                        .toList();
            }
        } catch (IOException | SecurityException exception) {
            Create.LOGGER.warn("Could not list staged schematics for {}", owner, exception);
            return List.of();
        }
    }

    private void refresh() {
        List<String> strings = PolymerArtKit.blank(STRINGS);
        boolean finished = !table.inventory.getItem(1).isEmpty();
        strings.set(TITLE, table.isUploading ? "uploading" : finished ? "finished" : "title");
        boolean busy = table.isUploading || finished;
        if (files.isEmpty() && !busy) strings.set(NONE, "none");
        float progress = finished && !table.isUploading ? 1 : table.isUploading ? table.uploadingProgress : 0;
        int width = Math.round(BAR_W * Math.min(1, progress));
        if (width > 0) strings.set(BAR, Integer.toString(width));
        String label = table.isUploading
                ? table.uploadingSchematic
                : finished
                        ? table.inventory.getItem(1).get(AllDataComponents.SCHEMATIC_FILE)
                        : files.isEmpty() ? null : files.get(selected);
        List<Boolean> pixels = PolymerArtKit.flags(2 * LABEL_W);
        if (label != null) PolymerArtKit.write(pixels, busy ? LANE_BUSY : LANE_IDLE, LABEL_X, LABEL_W, LABEL_X, label);
        set(FOREGROUND, PolymerArtKit.stack("schematic_table_foreground", strings, pixels), tooltip(FOREGROUND));
        for (int i = 0; i < 36; i++) {
            if (i == FOREGROUND || i == INPUT || i == OUTPUT) continue;
            set(i, PolymerArtKit.stack("schematic_table_empty", null), tooltip(i));
        }
    }

    private void set(int slot, ItemStack stack, List<Component> tooltip) {
        stack = PolymerGuiControls.nativeDecoration(stack, tooltip);
        if (!ItemStack.matches(controls.getItem(slot), stack)) controls.setItem(slot, stack);
    }

    private List<Component> tooltip(int slot) {
        if (slot == FOREGROUND)
            return List.of(
                    white("Schematics folder"),
                    gray("Vanilla clients cannot upload local files."),
                    gray("Place .nbt files in the server's"),
                    gray("schematics/uploaded/" + owner.getGameProfile().name() + "/"));
        if (slot >= 1 && slot <= 8) {
            if (files.isEmpty() || table.isUploading) return null;
            List<Component> lines = new ArrayList<>();
            lines.add(white(PolymerSchematicGuis.lang("create.gui.schematicTable.availableSchematics")));
            int from = Math.max(0, Math.min(selected - 3, files.size() - 7));
            for (int i = from; i < Math.min(files.size(), from + 7); i++)
                lines.add(Component.literal((i == selected ? "-> " : "   ") + files.get(i))
                        .withStyle(s -> s.withItalic(false))
                        .withStyle(i == selected ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            lines.add(PolymerGuiControls.hint("Left-click: next   Right-click: previous"));
            return lines;
        }
        if (slot == CONFIRM) return List.of(white(PolymerSchematicGuis.lang("create.action.confirm")));
        return null;
    }

    private static Component white(String text) {
        return Component.literal(text)
                .withStyle(s -> s.withColor(ChatFormatting.WHITE).withItalic(false));
    }

    private static Component gray(String text) {
        return Component.literal(text)
                .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (player != owner || !stillValid(player)) return;
        if (slot >= 0 && slot < 36 && slot != INPUT && slot != OUTPUT) {
            if (input == ContainerInput.PICKUP && (button == 0 || button == 1)) {
                if (slot >= 1 && slot <= 8 && !files.isEmpty() && !table.isUploading)
                    selected = Math.floorMod(selected + (button == 0 ? 1 : -1), files.size());
                else if (slot == CONFIRM) confirm();
                refresh();
            }
            sendAllDataToRemote();
            return;
        }
        super.clicked(slot, button, input, player);
    }

    private void confirm() {
        if (!canWrite() || files.isEmpty()) return;
        refreshFiles();
        if (files.isEmpty()) return;
        String selectedFile = files.get(selected);
        ItemStack blueprint = PolymerSchematicFiles.blueprint(owner, selectedFile);
        if (blueprint.isEmpty() || !stillValid(owner) || !canWrite()) {
            owner.sendOverlayMessage(
                    Component.literal("Cannot load that schematic. Check the staged file and its size/bounds."));
            return;
        }
        table.startUpload(selectedFile);
        table.finishUpload();
        table.inventory.setItem(1, blueprint);
        table.inventory.setChanged();
        table.setChanged();
    }

    @Override
    public void broadcastChanges() {
        lifecycle.tick(
                () -> {
                    if (!table.isUploading) refreshFiles();
                },
                () -> {});
        refresh();
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        if (index < 36 && index != INPUT && index != OUTPUT) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), moved = stack.copy();
        if (index == INPUT || index == OUTPUT) {
            if (!moveItemStackTo(stack, 36, slots.size(), false)) return ItemStack.EMPTY;
        } else if (!slots.get(INPUT).mayPlace(stack) || !moveItemStackTo(stack, INPUT, INPUT + 1, false))
            return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return moved;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof ControlSlot) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof ControlSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner
                && !table.isRemoved()
                && player.level() == table.getLevel()
                && !owner.isSpectator()
                && player.level().getBlockEntity(table.getBlockPos()) == table
                && player.level().getBlockState(table.getBlockPos()).is(AllBlocks.SCHEMATIC_TABLE)
                && table.canPlayerUse(player)
                && player.isWithinBlockInteractionRange(table.getBlockPos(), PolymerGuiControls.MENU_REACH_BUFFER);
    }

    @Override
    public void removed(Player player) {
        lifecycle.close(() -> super.removed(player), () -> {
            if (player instanceof ServerPlayer serverPlayer) PolymerInventorySync.afterMenu(serverPlayer);
        });
    }

    public static void addAssets(ResourcePackBuilder builder) throws IOException {
        Map<Integer, BufferedImage> glyphs = PolymerArtKit.asciiGlyphs(builder);
        BufferedImage atlas = PolymerArtKit.image(builder, "assets/create/textures/gui/schematics.png");
        BufferedImage art = new BufferedImage(ART_W, ART_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = art.createGraphics();
        g.drawImage(atlas.getSubimage(10, 139, ART_W, ART_H), 0, 0, null);
        BufferedImage output = deepCopy(art.getSubimage(162, 54, 28, 26));
        g.drawImage(output, 161, 54, null);
        g.dispose();
        PolymerFrogArt.panel(builder, "schematic_table", art, AX, AY, 4);

        PolymerArtKit kit = new PolymerArtKit(builder, "schematic_table", 16, 26);
        kit.allocate(STRINGS);
        Map<String, BufferedImage> titles = new LinkedHashMap<>();
        titles.put("title", title(glyphs, PolymerSchematicGuis.lang("block.create.schematic_table")));
        titles.put("uploading", title(glyphs, PolymerSchematicGuis.lang("create.gui.schematicTable.uploading")));
        titles.put("finished", title(glyphs, PolymerSchematicGuis.lang("create.gui.schematicTable.finished")));
        kit.layer(TITLE, titles, AX, AY, .01f);
        BufferedImage none = new BufferedImage(140, 10, BufferedImage.TYPE_INT_ARGB);
        PolymerArtKit.text(
                none,
                glyphs,
                0,
                0,
                PolymerSchematicGuis.lang("create.gui.schematicTable.noSchematics"),
                0xD3D3D3,
                true);
        kit.layer(NONE, Map.of("none", none), 54 + AX, 28 + AY, .01f);
        kit.bar(BAR, "bar", atlas.getSubimage(10, 224, BAR_W, 16), BAR_X, BAR_Y, .01f);
        kit.compactLane(LANE_IDLE, LABEL_X, LABEL_Y, LABEL_W, glyphs, 0xFFFFFF, true, .02f);
        kit.compactLane(LANE_BUSY, LABEL_X, LABEL_Y, LABEL_W, glyphs, 0xCCDDFF, true, .02f);
        kit.finish("schematic_table_foreground");
        PolymerArtKit.empty(builder, "schematic_table_empty");
    }

    private static BufferedImage title(Map<Integer, BufferedImage> glyphs, String text) {
        BufferedImage image = new BufferedImage(ART_W, 14, BufferedImage.TYPE_INT_ARGB);
        PolymerArtKit.text(image, glyphs, (ART_W - 8 - PolymerArtKit.width(text)) / 2, 4, text, 0x505050, false);
        return image;
    }

    private static BufferedImage deepCopy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.createGraphics().drawImage(source, 0, 0, null);
        return copy;
    }
}
