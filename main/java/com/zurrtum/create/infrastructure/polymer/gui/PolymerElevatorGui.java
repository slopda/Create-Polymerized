package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.zurrtum.create.content.decoration.slidingDoor.DoorControl;
import com.zurrtum.create.infrastructure.polymer.PolymerFrogArt;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

final class PolymerElevatorGui extends SimpleGui {
    private static final int OX = -28, OY = -5, ROWS = 4;
    private static final int SHORT = 0, HOST = 2, DOOR_ICON = 18, DOOR_FIRST = 19, DOOR_LAST = 24, CONFIRM = 26;
    private static final int LONG_X = 63 + OX,
            NAME_Y = 30 + OY,
            LONG_W = 140,
            SHORT_X = -6,
            SHORT_W = 28,
            DOOR_X = 40,
            DOOR_Y = 62 + OY,
            DOOR_W = 80;

    private final ElevatorContactBlockEntity contact;
    private final ServerLevel level;
    private String shortName, longName;
    private DoorControl door;
    private final PolymerGuiLifecycle lifecycle = new PolymerGuiLifecycle();

    private PolymerElevatorGui(ServerPlayer player, ElevatorContactBlockEntity contact, ServerLevel level) {
        super(MenuType.GENERIC_9x4, player, false);
        this.contact = contact;
        this.level = level;
        shortName = contact.shortName;
        longName = contact.longName;
        door = contact.doorControls.mode;
        PolymerGuiControls.configure(this, PolymerFrogArt.title("elevator_contact"), true);
        refresh();
    }

    static boolean open(ServerPlayer player, ElevatorContactBlockEntity contact, ServerLevel level) {
        return new PolymerElevatorGui(player, contact, level).open();
    }

    private static String doorLabel(DoorControl mode) {
        return PolymerSchematicGuis.lang("create.contraption.door_control." + mode.getSerializedName());
    }

    private static String doorShort(DoorControl mode) {
        return PolymerSchematicGuis.lang("create.contraption.door_control." + mode.getSerializedName() + ".short");
    }

    private void refresh() {
        int shortFlags = PolymerGuiPixelText.flags(SHORT_W), longFlags = PolymerGuiPixelText.flags(LONG_W);
        List<Boolean> pixels = PolymerArtKit.flags(SHORT_W + LONG_W + DOOR_W);
        PolymerArtKit.write(pixels, 0, SHORT_X, SHORT_W, 8 - PolymerArtKit.width(shortName) / 2, shortName);
        PolymerArtKit.write(pixels, shortFlags, LONG_X, LONG_W, LONG_X, longName);
        PolymerArtKit.write(pixels, shortFlags + longFlags, DOOR_X, DOOR_W, DOOR_X, doorShort(door));
        List<Component> shortTip = List.of(
                Component.translatable("create.elevator_contact.floor_identifier")
                        .withStyle(s -> s.withColor(0x5391E1)),
                Component.translatable("create.gui.schedule.lmb_edit")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        List<Component> longTip = List.of(
                Component.translatable("create.elevator_contact.floor_description")
                        .withStyle(s -> s.withColor(0x5391E1)),
                Component.translatable("create.crafting_blueprint.optional").withStyle(ChatFormatting.GRAY),
                Component.translatable("create.gui.schedule.lmb_edit")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        setSlot(SHORT, PolymerGuiControls.decoration(PolymerArtKit.stack("elevator_empty", null), shortTip));
        setSlot(
                HOST,
                PolymerGuiControls.decoration(PolymerArtKit.stack("elevator_foreground", List.of(), pixels), longTip));
        for (int slot = 3; slot <= 8; slot++)
            setSlot(slot, PolymerGuiControls.decoration(PolymerArtKit.stack("elevator_empty", null), longTip));
        setSlot(DOOR_ICON, PolymerGuiControls.decoration(new ItemStack(AllItems.TRAIN_DOOR), null));
        List<Component> doorTip = new ArrayList<>();
        doorTip.add(Component.translatable("create.contraption.door_control").withStyle(ChatFormatting.WHITE));
        for (DoorControl mode : DoorControl.values())
            doorTip.add(Component.literal((mode == door ? "-> " : "   ") + doorLabel(mode))
                    .withStyle(mode == door ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        doorTip.add(
                Component.literal("Left-click: next   Right-click: previous").withStyle(ChatFormatting.DARK_GRAY));
        for (int slot = DOOR_FIRST; slot <= DOOR_LAST; slot++)
            setSlot(slot, PolymerGuiControls.decoration(PolymerArtKit.stack("elevator_empty", null), doorTip));
        setSlot(
                CONFIRM,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon("elevator_confirm"),
                        List.of(Component.translatable("create.action.confirm"))));
        lifecycle.requestSync();
    }

    @Override
    public boolean onAnyClick(int slot, ClickType click, ContainerInput action) {
        lifecycle.requestSync();
        if (!stillValid()) {
            close();
            return false;
        }
        if (!PolymerGuiControls.primaryClick(click, action)) return false;
        if (slot == SHORT || slot >= HOST && slot <= 8) {
            if (click.isLeft)
                PolymerTextPrompt.open(
                        player,
                        Component.translatable("create.elevator_contact.title"),
                        List.of(
                                new PolymerTextPrompt.Field(
                                        Component.translatable("create.elevator_contact.floor_identifier"),
                                        shortName,
                                        4),
                                new PolymerTextPrompt.Field(
                                        Component.translatable("create.elevator_contact.floor_description"),
                                        longName,
                                        30)),
                        values -> {
                            shortName = values.get(0);
                            longName = values.get(1);
                            if (stillValid()) refresh();
                        });
        } else if (slot >= DOOR_FIRST && slot <= DOOR_LAST) {
            DoorControl[] modes = DoorControl.values();
            door = modes[Math.floorMod(door.ordinal() + (click.isLeft ? 1 : -1), modes.length)];
            refresh();
        } else if (slot == CONFIRM && click.isLeft) {
            contact.updateName(shortName, longName);
            contact.doorControls.set(door);
            close();
        }
        return false;
    }

    @Override
    public boolean stillValid() {
        return PolymerGuiControls.editableTarget(player, contact, level, AllBlocks.ELEVATOR_CONTACT);
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
                    PolymerArtKit.copy(PolymerArtKit.image(builder, "assets/create/textures/gui/display_link.png")
                            .getSubimage(20, 172, 233, 82));
            PolymerFrogArt.erase(art, 196, 55, 26, 22, 150, 66);
            for (int y = 52; y < art.getHeight(); y++) {
                int dark = art.getRGB(194, y), light = art.getRGB(195, y), body = art.getRGB(190, y);
                if (dark == body && light == body) continue;
                art.setRGB(174, y, dark);
                art.setRGB(175, y, light);
                art.setRGB(194, y, body);
                art.setRGB(195, y, body);
            }
            String title = PolymerSchematicGuis.lang("create.elevator_contact.title");
            PolymerArtKit.text(art, glyphs, (233 - 8) / 2 - PolymerArtKit.width(title) / 2, 6, title, 0x2F3738, false);
            PolymerFrogArt.panel(builder, "elevator_contact", art, OX, OY, ROWS);
            PolymerFrogArt.item(
                    builder,
                    "elevator_confirm",
                    PolymerFrogArt.button(builder, 0, 0, 1),
                    CONFIRM,
                    PolymerFrogArt.slotX(CONFIRM) - 1,
                    53);
            var kit = new PolymerArtKit(builder, "elevator", 52, 26);
            kit.compactLane(0, SHORT_X, NAME_Y, SHORT_W, glyphs, 0xFFFFFF, true, .02f);
            kit.compactLane(PolymerGuiPixelText.flags(SHORT_W), LONG_X, NAME_Y, LONG_W, glyphs, 0xFFFFFF, true, .02f);
            kit.compactLane(
                    PolymerGuiPixelText.flags(SHORT_W + LONG_W), DOOR_X, DOOR_Y, DOOR_W, glyphs, 0xFFFFFF, true, .02f);
            kit.finish("elevator_foreground");
            PolymerArtKit.empty(builder, "elevator_empty");
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot build Elevator Contact screen art", error);
        }
    }
}
