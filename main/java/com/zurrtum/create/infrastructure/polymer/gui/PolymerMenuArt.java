package com.zurrtum.create.infrastructure.polymer.gui;

import static dev.veeda.createpoly.assets.PackJson.array;

import com.google.gson.*;
import com.zurrtum.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.zurrtum.create.content.logistics.packagePort.PackagePortVanillaMenu;
import com.zurrtum.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.PackAssetId;
import dev.veeda.createpoly.assets.PackAssetWriter;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;

public final class PolymerMenuArt {
    private PolymerMenuArt() {}

    static final int PANEL_X = -19, BODY_Y = 9, FOOTER_INSERT = 5, INVENTORY_Y = 103;

    private static final class SourceNames {
        private static final Map<String, String> VALUES = sourceNames();
    }

    private static Map<String, String> sourceNames() {
        try {
            var file = FabricLoader.getInstance()
                    .getModContainer("create")
                    .orElseThrow()
                    .findPath("assets/create/lang/en_us.json")
                    .orElseThrow();
            Map<String, String> names = new HashMap<>();
            JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().entrySet().stream()
                    .filter(e -> e.getKey().equals("block.create.package_frogport")
                            || e.getKey().endsWith("_postbox"))
                    .forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
            return Map.copyOf(names);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read original Package Port names", e);
        }
    }

    private static FontDescription font(String name) {
        return new FontDescription.Resource(Identifier.parse("create:" + name));
    }

    private static Component space(int delta) {
        StringBuilder chars = new StringBuilder();
        for (int bit = 9; bit >= 0; bit--)
            while (Math.abs(delta) >= (1 << bit)) {
                chars.appendCodePoint(0xF000 + (delta < 0 ? 32 : 0) + bit + 1);
                delta += delta < 0 ? 1 << bit : -(1 << bit);
            }
        return Component.literal(chars.toString())
                .withStyle(Style.EMPTY.withFont(font("menu_art")).withoutShadow());
    }

    public static Component title(AbstractContainerMenu menu, Component title) {
        if (!(menu instanceof PackagePortVanillaMenu port)) return title;
        PackagePortBlockEntity be = port.getAnimatedContainerBlockEntity();
        String name = title.getContents() instanceof TranslatableContents translated
                ? SourceNames.VALUES.getOrDefault(translated.getKey(), title.getString())
                : title.getString();
        String label = be.addressFilter.isBlank() ? name : be.addressFilter;
        label = label.replaceAll("[^\\x20-\\x7e]", "?");
        int x = 91 - PolymerGogglesOverlay.width(label) / 2;
        MutableComponent out = Component.empty()
                .append(space(-27))
                .append(Component.literal(be instanceof PostboxBlockEntity ? "\uE001" : "\uE000")
                        .withStyle(Style.EMPTY
                                .withFont(font("menu_art"))
                                .withColor(0xFFFFFF)
                                .withoutShadow()))
                .append(space(x - 202))
                .append(Component.literal(label)
                        .withStyle(Style.EMPTY
                                .withFont(font("package_port_label"))
                                .withColor(0x3D3C48)
                                .withoutShadow()))
                .append(space(-x - PolymerGogglesOverlay.width(label)))
                .append(Component.literal("\uE002")
                        .withStyle(Style.EMPTY
                                .withFont(font("menu_art"))
                                .withColor(0xFFFFFF)
                                .withoutShadow()))
                .append(space(-177))
                .append(Component.literal("\uE004")
                        .withStyle(Style.EMPTY
                                .withFont(font("menu_art"))
                                .withColor(0xFFFFFF)
                                .withoutShadow()));
        if (be.target != null)
            out.append(space(7 - 177))
                    .append(Component.literal("\uE003")
                            .withStyle(Style.EMPTY
                                    .withFont(font("menu_art"))
                                    .withColor(0xFFFFFF)
                                    .withoutShadow()));
        return out;
    }

    public static ItemStack control(PackagePortBlockEntity be, int index) {
        String name;
        Component label;
        if (index == 7) {
            name = "edit";
            label = Component.literal("Edit address: " + (be.addressFilter.isBlank() ? "(empty)" : be.addressFilter));
        } else if ((index == 1 || index == 2) && be.target != null) {
            boolean receive = index == 1;
            name = (receive ? "receive" : "send") + (be.acceptsPackages == receive ? "_selected" : "");
            label = Component.translatable(
                    receive ? "create.gui.package_port.send_and_receive" : "create.gui.package_port.send_only");
        } else if (index == 0 && be.target != null) {
            ItemStack icon = be.target.getIcon().copy();
            icon.set(DataComponents.CUSTOM_NAME, Component.literal("Connected target"));
            return icon;
        } else if (index == 8) {
            name = "confirm";
            label = Component.translatable("gui.done");
        } else {
            name = "empty";
            label = Component.empty();
        }
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, Identifier.parse("create:ui/port_" + name));
        stack.set(DataComponents.CUSTOM_NAME, label);
        if (label.getString().isEmpty())
            stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
        return stack;
    }

    public static ItemStack foreground(PackagePortBlockEntity be) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, Identifier.parse("create:ui/port_label_cover"));
        stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
        PolymerGuiPreviews.variant(stack, new ItemStack(be.getBlockState().getBlock()));
        return stack;
    }

    private static BufferedImage image(ResourcePackBuilder b, String path) throws IOException {
        byte[] data = b.getDataOrSource(path);
        if (data == null) throw new IOException("Missing " + path);
        return PngCodec.decode(data);
    }

    private static void png(PackAssetWriter assets, String path, BufferedImage image) throws IOException {
        assets.png(new PackAssetId("create", path), image);
    }

    private static void json(ResourcePackBuilder b, String path, JsonElement value) throws IOException {
        if (!b.addData(path, value.toString().getBytes(StandardCharsets.UTF_8)))
            throw new IOException("Cannot write " + path);
    }

    private static void model(PackAssetWriter assets, String name, BufferedImage image) throws IOException {
        png(assets, "item/ui/port_" + name, image);
        float scale = 1, shrink = 16f / 18;
        JsonObject root = new JsonObject(), textures = new JsonObject();
        root.addProperty("gui_light", "front");
        textures.addProperty("panel", "create:item/ui/port_" + name);
        textures.addProperty("particle", "#panel");
        root.add("textures", textures);
        JsonObject e = new JsonObject(), face = new JsonObject(), faces = new JsonObject();
        e.add("from", array(8 - image.getWidth() / scale / 2f, 8 - image.getHeight() / scale / 2f, 8));
        e.add("to", array(8 + image.getWidth() / scale / 2f, 8 + image.getHeight() / scale / 2f, 8));
        e.addProperty("shade", false);
        face.addProperty("texture", "#panel");
        face.add("uv", array(0, 0, 16, 16));
        faces.add("south", face);
        e.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(e);
        root.add("elements", elements);
        JsonObject gui = new JsonObject(), display = new JsonObject();
        gui.add("rotation", array(0, 0, 0));
        gui.add("translation", array(0, 0, 0));
        gui.add("scale", array(scale * shrink, scale * shrink, 1));
        display.add("gui", gui);
        root.add("display", display);
        assets.model(new PackAssetId("create", "item/ui/port_" + name), root);
        assets.item(
                new PackAssetId("create", "ui/port_" + name),
                JsonParser.parseString(
                        "{\"oversized_in_gui\":true,\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:item/ui/port_"
                                + name + "\"}}"));
    }

    static void addAssets(ResourcePackBuilder b) {
        try {
            PackAssetWriter assets = new PackAssetWriter(b);
            BufferedImage sheet = image(b, "assets/create/textures/gui/frogport_and_mailbox.png");
            BufferedImage widgets = image(b, "assets/create/textures/gui/widgets.png"),
                    icons = image(b, "assets/create/textures/gui/icons.png");
            JsonArray providers = new JsonArray();
            JsonObject spaces = new JsonObject(), advances = new JsonObject();
            spaces.addProperty("type", "space");
            for (int bit = 0; bit < 10; bit++) {
                advances.addProperty(Character.toString(0xF001 + bit), 1 << bit);
                advances.addProperty(Character.toString(0xF021 + bit), -(1 << bit));
            }
            spaces.add("advances", advances);
            providers.add(spaces);
            BufferedImage body = new BufferedImage(220, 82 + FOOTER_INSERT, BufferedImage.TYPE_INT_ARGB);
            var bg = body.createGraphics();
            bg.drawImage(sheet.getSubimage(0, 47, 220, 52), 0, 0, null);
            for (int row = 0; row < FOOTER_INSERT; row++)
                bg.drawImage(sheet.getSubimage(0, 97 + row % 2, 220, 1), 0, 52 + row, null);
            bg.drawImage(sheet.getSubimage(0, 99, 220, 30), 0, 52 + FOOTER_INSERT, null);
            bg.dispose();
            for (int yy = 58 + FOOTER_INSERT; yy < 76 + FOOTER_INSERT; yy++)
                for (int xx = 187; xx < 205; xx++) body.setRGB(xx, yy, body.getRGB(180, yy));
            for (int header = 0; header < 2; header++) {
                int height = header == 0 ? 17 : 24;
                BufferedImage panel = new BufferedImage(220, height + body.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = panel.createGraphics();
                g.drawImage(sheet.getSubimage(0, header == 0 ? 0 : 23, 214, height), 0, 0, null);
                g.drawImage(body, 0, height, null);
                g.dispose();
                String name = "menu_art_" + header;
                png(assets, "font/" + name, panel);
                JsonObject p = new JsonObject();
                p.addProperty("type", "bitmap");
                p.addProperty("file", "create:font/" + name + ".png");
                p.addProperty("height", panel.getHeight());
                p.addProperty("ascent", 4 + height);
                JsonArray chars = new JsonArray();
                chars.add(Character.toString(0xE000 + header));
                p.add("chars", chars);
                providers.add(p);
            }
            BufferedImage inventory =
                    image(b, "assets/create/textures/gui/player_inventory.png").getSubimage(0, 0, 176, 108);
            png(assets, "font/port_inventory", inventory);
            JsonObject inventoryProvider = new JsonObject();
            inventoryProvider.addProperty("type", "bitmap");
            inventoryProvider.addProperty("file", "create:font/port_inventory.png");
            inventoryProvider.addProperty("height", 108);
            inventoryProvider.addProperty("ascent", 13 - INVENTORY_Y);
            JsonArray inventoryChars = new JsonArray();
            inventoryChars.add("\uE002");
            inventoryProvider.add("chars", inventoryChars);
            providers.add(inventoryProvider);
            png(assets, "font/port_target_slot", sheet.getSubimage(26, 55, 18, 18));
            JsonObject targetProvider = new JsonObject();
            targetProvider.addProperty("type", "bitmap");
            targetProvider.addProperty("file", "create:font/port_target_slot.png");
            targetProvider.addProperty("height", 18);
            targetProvider.addProperty("ascent", 13 - 71);
            JsonArray targetChars = new JsonArray();
            targetChars.add("\uE003");
            targetProvider.add("chars", targetChars);
            providers.add(targetProvider);
            BufferedImage gap = new BufferedImage(176, 7, BufferedImage.TYPE_INT_ARGB);
            var gapGraphics = gap.createGraphics();
            gapGraphics.setColor(new Color(0x222428));
            gapGraphics.fillRect(0, 0, 176, 7);
            gapGraphics.dispose();
            png(assets, "font/port_inventory_gap", gap);
            JsonObject gapProvider = new JsonObject();
            gapProvider.addProperty("type", "bitmap");
            gapProvider.addProperty("file", "create:font/port_inventory_gap.png");
            gapProvider.addProperty("height", 7);
            gapProvider.addProperty("ascent", 13 - 96);
            JsonArray gapChars = new JsonArray();
            gapChars.add("\uE004");
            gapProvider.add("chars", gapChars);
            providers.add(gapProvider);
            JsonObject f = new JsonObject();
            f.add("providers", providers);
            json(b, "assets/create/font/menu_art.json", f);
            JsonArray asciiChars = PolymerArtKit.asciiRows(b);
            BufferedImage ascii = image(b, "assets/minecraft/textures/font/ascii.png"),
                    padded = new BufferedImage(128, 256, BufferedImage.TYPE_INT_ARGB);
            var g = padded.createGraphics();
            for (int row = 0; row < 16; row++) g.drawImage(ascii.getSubimage(0, row * 8, 128, 8), 0, row * 16, null);
            g.dispose();
            png(assets, "font/package_port_label", padded);
            JsonObject p = new JsonObject();
            p.addProperty("type", "bitmap");
            p.addProperty("file", "create:font/package_port_label.png");
            p.addProperty("height", 16);
            p.addProperty("ascent", 15);
            p.add("chars", asciiChars);
            JsonArray labels = new JsonArray();
            JsonObject blank = new JsonObject(), blankWidth = new JsonObject();
            blank.addProperty("type", "space");
            blankWidth.addProperty(" ", 4);
            blank.add("advances", blankWidth);
            labels.add(blank);
            labels.add(p);
            f = new JsonObject();
            f.add("providers", labels);
            json(b, "assets/create/font/package_port_label.json", f);
            for (String name :
                    new String[] {"edit", "receive", "receive_selected", "send", "send_selected", "confirm"}) {
                BufferedImage icon = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
                g = icon.createGraphics();
                g.drawImage(widgets.getSubimage(name.endsWith("selected") ? 72 : 0, 0, 18, 18), 0, 0, null);
                if (name.equals("edit")) g.drawImage(sheet.getSubimage(230, 3, 13, 13), 2, 2, null);
                else
                    g.drawImage(
                            icons.getSubimage(
                                    name.startsWith("receive") ? 16 : 0, name.equals("confirm") ? 16 : 192, 16, 16),
                            1,
                            1,
                            null);
                g.dispose();
                model(assets, name, icon);
            }
            JsonElement empty = JsonParser.parseString("{\"model\":{\"type\":\"minecraft:empty\"}}");
            assets.item(new PackAssetId("create", "ui/port_label_cover"), empty);
            assets.item(new PackAssetId("create", "ui/port_empty"), empty);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot generate native Package Port art", e);
        }
    }
}
