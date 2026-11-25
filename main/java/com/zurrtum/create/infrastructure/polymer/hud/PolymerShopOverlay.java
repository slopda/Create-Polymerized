package com.zurrtum.create.infrastructure.polymer.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.logistics.BigItemStack;
import com.zurrtum.create.content.logistics.tableCloth.ShoppingListItem;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.zurrtum.create.infrastructure.component.ShoppingList;
import com.zurrtum.create.infrastructure.polymer.PolymerReach;
import com.zurrtum.create.infrastructure.polymer.block.PolymerTableClothPrice;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiGlyphs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

public final class PolymerShopOverlay {
    private static final int LIFT = 16;
    private static final int SLOT_TOP = -100 + 72 + LIFT;
    private static final int SPACE_BASE = 0xF000;
    private static final int ART = 0xE000, ICON_BASE = 0xF0000;
    private static final int HOTSLOT = ART,
            HOTSLOT_ACTIVE = ART + 1,
            HOTSLOT_SUPER = ART + 2,
            ARROW = ART + 3,
            ARROW_BAD = ART + 4,
            TRADE = ART + 5,
            LIST = ART + 6,
            BOX_L = ART + 7,
            BOX_R = ART + 8,
            BOX_MID = ART + 16,
            DIGITS = ART + 32;
    private static final int REFRESH_TICKS = 20;

    private static volatile Map<Item, Integer> icons = Map.of();
    private static final Map<UUID, String> SHOWN = new HashMap<>();

    private PolymerShopOverlay() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PolymerShopOverlay::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOWN.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SHOWN.clear());
    }

    private static void tick(MinecraftServer server) {
        if (icons.isEmpty()) return;
        for (ServerPlayer player : NativePresentationPolicy.presentationPlayers(server.getPlayerList())) {
            Component panel = player.isSpectator() || player.isRemoved() ? null : panel(player);
            String previous = SHOWN.get(player.getUUID());
            if (panel == null) {
                if (previous != null) {
                    SHOWN.remove(player.getUUID());
                    NativePresentationPolicy.sendPresentation(
                            player, new ClientboundSetActionBarTextPacket(Component.empty()));
                }
                continue;
            }
            String key = panel.getString() + '|' + (server.getTickCount() / REFRESH_TICKS);
            if (!key.equals(previous)) {
                SHOWN.put(player.getUUID(), key);
                NativePresentationPolicy.sendPresentation(player, new ClientboundSetActionBarTextPacket(panel));
            }
        }
    }

    private static Component panel(ServerPlayer player) {
        BlockHitResult block = PolymerReach.targetBlock(player);
        if (block == null) return null;
        if (!(player.level().getBlockEntity(block.getBlockPos()) instanceof TableClothBlockEntity cloth)) return null;
        if (!cloth.isShop()) return null;
        ItemStack held = player.getMainHandItem();
        if (held.is(AllItems.CLIPBOARD)) return null;
        if (PolymerTableClothPrice.targetsPriceTag(player)) return null;
        ShoppingList list = ShoppingListItem.getList(held);
        int purchases = list == null ? 0 : list.getPurchases(cloth.getBlockPos());
        int stock = cloth.getStockLevelForTrade(list);
        ItemStack payment = cloth.getPaymentItem().copyWithCount(cloth.getPaymentAmount());
        boolean paymentOk = !cloth.getPaymentItem().isEmpty() && stock > purchases;
        List<ItemStack> results = new ArrayList<>();
        for (BigItemStack entry : cloth.requestData.encodedRequest().stacks())
            results.add(entry.stack.copyWithCount(entry.count));
        boolean invalid = payment.isEmpty() || stock == 0;
        return compose(payment, paymentOk, results, invalid, stock > purchases, purchases);
    }

    private static Component compose(
            ItemStack payment,
            boolean paymentOk,
            List<ItemStack> results,
            boolean invalid,
            boolean inStock,
            int purchases) {
        Pen pen = new Pen();
        int w = 21 + 30 + 21 * results.size();
        int x = -w / 2 - (w % 2 == 0 ? 0 : 1);
        pen.art(BOX_L, x - 2, 2);
        int middle = w, cx = x;
        for (int bit = 7; bit >= 0; bit--)
            while (middle >= 1 << bit) {
                pen.art(BOX_MID + bit, cx, 1 << bit);
                cx += 1 << bit;
                middle -= 1 << bit;
            }
        pen.art(BOX_R, x + w, 2);
        pen.art(TRADE, -48, 96);
        if (purchases > 0) {
            pen.art(LIST, 20, 16);
            int tx = 36;
            for (char c : ("x" + purchases).toCharArray()) {
                int glyph = c == 'x' ? DIGITS : DIGITS + 1 + (c - '0');
                pen.art(glyph, tx, 5);
                tx += 6;
            }
        }
        pen.art(paymentOk ? HOTSLOT_ACTIVE : HOTSLOT, x, 22);
        pen.item(payment, x + 3);
        x += 21 + 5;
        pen.art(invalid ? ARROW_BAD : ARROW, x, 20);
        x += 25;
        if (results.isEmpty()) {
            pen.art(HOTSLOT, x, 22);
            pen.item(new ItemStack(Items.BARRIER), x + 3);
        }
        for (ItemStack result : results) {
            boolean active = !invalid && inStock;
            pen.art(active ? HOTSLOT_ACTIVE : HOTSLOT, x, 22);
            pen.item(result, x + 3);
            x += 21;
        }
        pen.moveTo(font("shop_art"), 0);
        return pen.finish();
    }

    private static final class Pen {
        final MutableComponent root = Component.empty();
        float units;
        FontDescription runFont;
        boolean runShadow;
        StringBuilder run = new StringBuilder();

        void art(int codepoint, int x, int width) {
            glyph(font("shop_art"), false, codepoint, x, width + 1);
        }

        void item(ItemStack stack, int x) {
            Integer icon = icons.get(stack.getItem());
            if (icon == null) icon = icons.get(Items.BARRIER);
            if (icon != null) glyph(font("shop_icon"), false, icon, x, 17);
            if (stack.getCount() > 1) {
                String count = Integer.toString(stack.getCount());
                int tx = x + 17 - PolymerGogglesOverlay.width(count);
                moveTo(FontDescription.DEFAULT, tx);
                append(FontDescription.DEFAULT, true, count);
                units += PolymerGogglesOverlay.width(count);
            }
        }

        void glyph(FontDescription font, boolean shadow, int codepoint, int x, int advance) {
            moveTo(font, x);
            append(font, shadow, Character.toString(codepoint));
            units += advance;
        }

        void moveTo(FontDescription font, int x) {
            float delta = x - units;
            if (delta == 0) return;
            append(font("shop_art"), false, PolymerGuiGlyphs.spaces(SPACE_BASE, delta));
            units += delta;
        }

        private void append(FontDescription font, boolean shadow, String text) {
            if (!font.equals(runFont) || shadow != runShadow) flush();
            runFont = font;
            runShadow = shadow;
            run.append(text);
        }

        private void flush() {
            if (run.isEmpty()) return;
            Style style = Style.EMPTY.withFont(runFont).withColor(0xFFFFFF);
            root.append(Component.literal(run.toString()).withStyle(runShadow ? style : style.withoutShadow()));
            run = new StringBuilder();
        }

        Component finish() {
            flush();
            return root;
        }
    }

    private static FontDescription font(String path) {
        return new FontDescription.Resource(CreateIds.id(path));
    }

    public static void addAssets(ResourcePackBuilder builder) {
        try {
            byte[] widgets = builder.getDataOrSource("assets/create/textures/gui/widgets.png");
            byte[] list = builder.getDataOrSource("assets/create/textures/item/shopping_list.png");
            byte[] ascii = builder.getDataOrSource("assets/minecraft/textures/font/ascii.png");
            if (widgets == null || list == null || ascii == null) throw new IOException("Missing shop overlay sources");
            art(builder, PngCodec.decode(widgets), PngCodec.decode(list), PngCodec.decode(ascii));
            icons(builder);
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Table cloth shop overlay assets unavailable", exception);
            icons = Map.of();
        }
    }

    private static void art(ResourcePackBuilder builder, BufferedImage widgets, BufferedImage list, BufferedImage ascii)
            throws IOException {
        JsonArray providers = new JsonArray();
        providers.add(PolymerGuiGlyphs.spaceProvider(SPACE_BASE));
        int top = SLOT_TOP;
        glyph(builder, providers, "hotslot", widgets.getSubimage(0, 68, 22, 22), top, HOTSLOT);
        glyph(builder, providers, "hotslot_active", widgets.getSubimage(0, 46, 22, 22), top, HOTSLOT_ACTIVE);
        glyph(builder, providers, "hotslot_super", widgets.getSubimage(27, 67, 24, 24), top, HOTSLOT_SUPER);
        glyph(builder, providers, "arrow", widgets.getSubimage(24, 51, 20, 12), top + 4, ARROW);
        glyph(builder, providers, "arrow_bad", widgets.getSubimage(52, 51, 20, 15), top + 4, ARROW_BAD);
        glyph(builder, providers, "trade", widgets.getSubimage(128, 98, 96, 46), top - 19, TRADE);
        glyph(builder, providers, "list", list, top - 20, LIST);
        BufferedImage left = new BufferedImage(2, 19, BufferedImage.TYPE_INT_ARGB);
        BufferedImage right = new BufferedImage(2, 19, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 19; y++) {
            int border = lerp(0x505000FF, 0x5028007F, y / 18f);
            boolean edge = y == 0 || y == 18;
            left.setRGB(0, y, edge ? 0 : 0xF0100010);
            left.setRGB(1, y, edge ? 0xF0100010 : over(border, 0xF0100010));
            right.setRGB(0, y, edge ? 0xF0100010 : over(border, 0xF0100010));
            right.setRGB(1, y, edge ? 0 : 0xF0100010);
        }
        glyph(builder, providers, "box_l", left, top + 1, BOX_L);
        glyph(builder, providers, "box_r", right, top + 1, BOX_R);
        for (int bit = 0; bit <= 7; bit++) {
            BufferedImage mid = new BufferedImage(1 << bit, 19, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < mid.getWidth(); x++)
                for (int y = 0; y < 19; y++)
                    mid.setRGB(
                            x,
                            y,
                            y == 0 || y == 18 ? 0xF0100010 : y == 1 ? 0x505000FF : y == 17 ? 0x5028007F : 0xF0100010);
            glyph(builder, providers, "box_" + bit, mid, top + 1, BOX_MID + bit);
        }
        String chars = "x0123456789";
        for (int i = 0; i < chars.length(); i++) {
            int c = chars.charAt(i);
            BufferedImage cell = new BufferedImage(5, 8, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 5; x++) {
                    int argb = ascii.getRGB((c % 16) * 8 + x, (c / 16) * 8 + y);
                    cell.setRGB(x, y, (argb >>> 24) == 0 ? 0 : 0xFFEEEEEE);
                }
            glyph(builder, providers, "digit_" + i, cell, top - 16, DIGITS + i);
        }
        PolymerGuiGlyphs.writeFont(builder, "shop_art", providers);
    }

    private static void glyph(
            ResourcePackBuilder builder,
            JsonArray providers,
            String name,
            BufferedImage image,
            int relTop,
            int codepoint)
            throws IOException {
        int ascent = 7 - relTop, pad = 0;
        if (ascent < 0) {
            pad = -ascent;
            ascent = 0;
        }
        int height = Math.max(image.getHeight() + pad, ascent);
        BufferedImage canvas = new BufferedImage(image.getWidth(), height, BufferedImage.TYPE_INT_ARGB);
        canvas.getGraphics().drawImage(image, 0, pad, null);
        if (canvas.getWidth() > 256 || canvas.getHeight() > 256) throw new IOException(name + " exceeds a font page");
        String path = "create:font/shop/" + name + ".png";
        builder.addData("assets/create/textures/font/shop/" + name + ".png", PngCodec.encode(canvas));
        providers.add(bitmap(path, height, ascent, Character.toString(codepoint)));
    }

    private static void icons(ResourcePackBuilder builder) {
        JsonArray providers = new JsonArray();
        Map<Item, Integer> map = new HashMap<>();
        Map<String, Integer> byTexture = new HashMap<>();
        int next = ICON_BASE;
        int ascent = 7 - (SLOT_TOP + 3);
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            String texture = texture(builder, id);
            if (texture == null) continue;
            Integer codepoint = byTexture.get(texture);
            if (codepoint == null) {
                codepoint = next++;
                byTexture.put(texture, codepoint);
                providers.add(
                        bitmap(texture + ".png", 16, Math.min(16, ascent), new String(Character.toChars(codepoint))));
            }
            map.put(item, codepoint);
        }
        PolymerGuiGlyphs.writeFont(builder, "shop_icon", providers);
        icons = Map.copyOf(map);
    }

    private static String texture(ResourcePackBuilder builder, Identifier item) {
        try {
            JsonObject definition =
                    read(builder, "assets/" + item.getNamespace() + "/items/" + item.getPath() + ".json");
            JsonObject model = definition == null ? null : definition.getAsJsonObject("model");
            String modelId =
                    model != null && model.has("model") ? model.get("model").getAsString() : null;
            Map<String, String> textures = new HashMap<>();
            for (int depth = 0; modelId != null && depth < 8; depth++) {
                Identifier id = Identifier.parse(modelId);
                JsonObject json = read(builder, "assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json");
                if (json == null) break;
                if (json.has("textures"))
                    for (var entry : json.getAsJsonObject("textures").entrySet())
                        if (entry.getValue().isJsonPrimitive())
                            textures.putIfAbsent(
                                    entry.getKey(), entry.getValue().getAsString());
                modelId = json.has("parent") ? json.get("parent").getAsString() : null;
            }
            for (String key : List.of("layer0", "all", "side", "front", "texture", "particle")) {
                String value = textures.get(key);
                for (int guard = 0; value != null && value.startsWith("#") && guard < 8; guard++)
                    value = textures.get(value.substring(1));
                if (value == null || value.startsWith("#")) continue;
                Identifier tex = Identifier.parse(value);
                String path = "assets/" + tex.getNamespace() + "/textures/" + tex.getPath() + ".png";
                byte[] png = builder.getDataOrSource(path);
                if (png == null || builder.getDataOrSource(path + ".mcmeta") != null) continue;
                BufferedImage image = PngCodec.decode(png);
                if (image == null || image.getWidth() != 16 || image.getHeight() != 16) continue;
                return tex.toString();
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return null;
    }

    private static JsonObject read(ResourcePackBuilder builder, String path) {
        byte[] data = builder.getDataOrSource(path);
        if (data == null) return null;
        JsonElement element = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonObject bitmap(String file, int height, int ascent, String chars) {
        JsonObject provider = PolymerGuiGlyphs.bitmapProvider(file, height, ascent);
        JsonArray rows = new JsonArray();
        rows.add(chars);
        provider.add("chars", rows);
        return provider;
    }

    private static int lerp(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
            result |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return result;
    }

    private static int over(int top, int bottom) {
        float ta = (top >>> 24) / 255f, ba = (bottom >>> 24) / 255f;
        float a = ta + ba * (1 - ta);
        if (a <= 0) return 0;
        int result = Math.round(a * 255) << 24;
        for (int shift = 0; shift < 24; shift += 8) {
            float c = ((top >>> shift & 0xFF) * ta + (bottom >>> shift & 0xFF) * ba * (1 - ta)) / a;
            result |= Math.round(c) << shift;
        }
        return result;
    }
}
