package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiTextures;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import net.minecraft.network.chat.Component;

public final class PolymerFrogArt {
    public static final int STRIP = 0xFF222428;

    private PolymerFrogArt() {}

    static int inventoryY(int rows) {
        return 13 + 18 * rows;
    }

    public static int slotX(int slot) {
        return 8 + slot % 9 * 18;
    }

    public static int slotY(int slot) {
        return 18 + slot / 9 * 18;
    }

    public static void panel(ResourcePackBuilder builder, String name, BufferedImage art, int ox, int oy, int rows)
            throws IOException {
        int inventoryY = inventoryY(rows);
        BufferedImage inventory = PolymerArtKit.image(builder, "assets/create/textures/gui/player_inventory.png")
                .getSubimage(0, 0, 176, 108);
        int width = Math.max(art.getWidth(), 176 - ox), height = inventoryY + 108 - oy;
        if (width > 256 || height > 256) throw new IOException("Frog panel exceeds a font page: " + name);
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(STRIP, true));
        g.fillRect(-ox, -oy, 176, inventoryY);
        g.drawImage(inventory, -ox, inventoryY - oy, null);
        g.drawImage(art, 0, 0, null);
        g.dispose();
        builder.addData("assets/create/textures/font/" + name + "_gui.png", png(canvas));
        int actual = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) if (canvas.getRGB(x, y) >>> 24 != 0) actual = Math.max(actual, x + 1);
        int before = -8 + ox;
        JsonObject bitmap = new JsonObject();
        bitmap.addProperty("type", "bitmap");
        bitmap.addProperty("file", "create:font/" + name + "_gui.png");
        bitmap.addProperty("ascent", 13 - oy);
        bitmap.addProperty("height", height);
        JsonArray chars = new JsonArray();
        chars.add("");
        bitmap.add("chars", chars);
        JsonObject advances = new JsonObject();
        advances.addProperty("", before);
        advances.addProperty("", -actual - 1 - before);
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        space.add("advances", advances);
        JsonArray providers = new JsonArray();
        providers.add(bitmap);
        providers.add(space);
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        builder.addStringData("assets/create/font/" + name + "_gui.json", font.toString());
    }

    public static Component title(String name) {
        return PolymerGuiTextures.title(name, Component.empty());
    }

    public static BufferedImage button(ResourcePackBuilder builder, int u, int column, int row) throws IOException {
        BufferedImage widgets = PolymerArtKit.image(builder, "assets/create/textures/gui/widgets.png");
        BufferedImage icons = PolymerArtKit.image(builder, "assets/create/textures/gui/icons.png");
        BufferedImage image = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(widgets.getSubimage(u, 0, 18, 18), 0, 0, null);
        g.drawImage(icons.getSubimage(column * 16, row * 16, 16, 16), 1, 1, null);
        g.dispose();
        return image;
    }

    public static void item(ResourcePackBuilder builder, String name, BufferedImage sprite, int slot, int x, int y)
            throws IOException {
        builder.addData("assets/create/textures/item/ui/gui_" + name + ".png", png(sprite));
        float dx = x + sprite.getWidth() / 2f - (slotX(slot) + 8),
                dy = -(y + sprite.getHeight() / 2f - (slotY(slot) + 8));
        float scale = sprite.getWidth() / 16f;
        builder.addStringData(
                "assets/create/models/item/ui/gui_" + name + ".json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"create:item/ui/gui_" + name
                        + "\"}," + "\"display\":{\"gui\":{\"rotation\":[0,0,0],\"translation\":[" + dx + "," + dy
                        + ",0],\"scale\":[" + scale + "," + scale + ",1]}}}");
        builder.addStringData(
                "assets/create/items/ui/gui_" + name + ".json",
                "{\"oversized_in_gui\":true,\"model\":{\"type\":\"minecraft:model\",\"model\":\"create:item/ui/gui_"
                        + name + "\"}}");
    }

    public static void erase(BufferedImage image, int x, int y, int w, int h, int fromX, int fromY) {
        int argb = image.getRGB(fromX, fromY);
        for (int py = y; py < y + h; py++) for (int px = x; px < x + w; px++) image.setRGB(px, py, argb);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        return PngCodec.encode(image);
    }
}
