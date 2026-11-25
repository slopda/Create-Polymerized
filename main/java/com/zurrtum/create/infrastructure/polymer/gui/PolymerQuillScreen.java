package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.schematic.PolymerQuill;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;

public final class PolymerQuillScreen extends SimpleGui {
    private static final int OX = -6, OY = -1, ROWS = 4;
    private static final int HOST = 0, DISCARD = 18, SAVE = 25, CONVERT = 26;
    private static final int LANE_X = 43, LANE_Y = 27, LANE_W = 131;

    private String name = "";
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();

    private PolymerQuillScreen(ServerPlayer player) {
        super(MenuType.GENERIC_9x4, player, false);
        PolymerGuiControls.configure(this, PolymerFrogArt.title("quill_prompt"), true);
        refresh();
    }

    public static void open(ServerPlayer player) {
        new PolymerQuillScreen(player).open();
    }

    private void refresh() {
        List<Boolean> pixels = PolymerArtKit.flags(LANE_W);
        PolymerArtKit.write(pixels, 0, LANE_X, LANE_W, LANE_X, name);
        setSlot(
                HOST,
                PolymerGuiControls.decoration(PolymerArtKit.stack("quill_prompt_foreground", List.of(), pixels), null));
        for (int slot = 2; slot <= 8; slot++) {
            setSlot(
                    slot,
                    PolymerGuiControls.decoration(
                            PolymerArtKit.stack("quill_prompt_empty", null),
                            List.of(
                                    Component.translatable("create.schematicAndQuill.title"),
                                    Component.literal(name.isEmpty() ? "(empty)" : name))));
        }
        setSlot(
                DISCARD,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("quill_discard"),
                        List.of(Component.translatable("create.action.discard"))));
        setSlot(
                SAVE,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("quill_save"),
                        List.of(Component.translatable("create.action.saveToFile"))));
        setSlot(
                CONVERT,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("quill_convert"),
                        List.of(Component.translatable("create.schematicAndQuill.convert"))));
        lifecycle.requestSync();
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (!PolymerGuiControls.primaryClick(click, action) || !click.isLeft) return false;
        if (slot >= 2 && slot <= 8) {
            PolymerTextPrompt.open(
                    player,
                    Component.translatable("create.schematicAndQuill.title"),
                    new PolymerTextPrompt.Field(Component.literal("Name"), name, 35),
                    value -> {
                        name = value;
                        if (stillValid()) refresh();
                    });
        } else if (slot == DISCARD) {
            close();
            PolymerQuill.discard(player);
        } else if (slot == SAVE) {
            close();
            PolymerQuill.saveToFile(player, name);
        } else if (slot == CONVERT) {
            close();
            PolymerQuill.convert(player, name);
        }
        return false;
    }

    @Override
    public boolean stillValid() {
        return PolymerQuill.ready(player);
    }

    @Override
    public void onTick() {
        lifecycle.tickWhileValid(stillValid(), this::close, wrappedMenu);
    }

    @Override
    public void onRemoved() {
        lifecycle.close(() -> {}, () -> PolymerInventorySync.afterMenu(player));
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            var glyphs = PolymerArtKit.asciiGlyphs(builder);
            BufferedImage art =
                    PolymerArtKit.copy(PolymerArtKit.image(builder, "assets/create/textures/gui/schematics_2.png")
                            .getSubimage(0, 0, 213, 79));
            PolymerFrogArt.erase(art, 6, 54, 21, 21, 90, 64);
            PolymerFrogArt.erase(art, 146, 52, 56, 24, 90, 64);
            Graphics2D g = art.createGraphics();
            g.drawImage(
                    PolymerArtKit.image(builder, "assets/create/textures/item/schematic.png"), 22, 24, 16, 16, null);
            g.dispose();
            String title = PolymerSchematicGuis.lang("create.schematicAndQuill.title");
            PolymerArtKit.text(art, glyphs, (213 - 8 - PolymerArtKit.width(title)) / 2, 4, title, 0x505050, false);
            PolymerFrogArt.panel(builder, "quill_prompt", art, OX, OY, ROWS);
            PolymerFrogArt.item(
                    builder, "quill_discard", PolymerFrogArt.button(builder, 0, 1, 0), DISCARD, 7 + OX, 55 + OY);
            PolymerFrogArt.item(
                    builder,
                    "quill_save",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    SAVE,
                    PolymerFrogArt.slotX(SAVE) - 1,
                    55 + OY);
            PolymerFrogArt.item(
                    builder,
                    "quill_convert",
                    PolymerFrogArt.button(builder, 0, 0, 8),
                    CONVERT,
                    PolymerFrogArt.slotX(CONVERT) - 1,
                    55 + OY);
            var kit = new PolymerArtKit(builder, "quill_prompt", 16, 26);
            kit.compactLane(0, LANE_X, LANE_Y, LANE_W, glyphs, 0xFFFFFF, true, .02f);
            kit.finish("quill_prompt_foreground");
            PolymerArtKit.empty(builder, "quill_prompt_empty");
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot build Save Schematic screen art", error);
        }
    }
}
