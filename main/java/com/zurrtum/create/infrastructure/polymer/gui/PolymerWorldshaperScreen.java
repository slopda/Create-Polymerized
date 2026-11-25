package com.zurrtum.create.infrastructure.polymer.gui;

import static com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit.image;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.equipment.zapper.terrainzapper.Brush;
import com.zurrtum.create.infrastructure.component.PlacementOptions;
import com.zurrtum.create.infrastructure.component.PlacementPatterns;
import com.zurrtum.create.infrastructure.component.TerrainBrushes;
import com.zurrtum.create.infrastructure.component.TerrainTools;
import com.zurrtum.create.infrastructure.packet.c2s.ConfigureWorldshaperPacket;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerHudRadial;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

final class PolymerWorldshaperScreen {
    private static final Identifier ACTION = CreateIds.id("polymer_worldshaper_screen");
    private static final FontDescription ART_FONT = font("zap_art"), SPACE_FONT = font("value_board_space");
    private static final int W = 234, H = 103, ADVANCE = W + 1, LINES = (H + 8) / 9;
    private static final int BUTTON = 0xE000,
            BUTTON_GREEN = 0xE001,
            INDICATOR = 0xE002,
            INDICATOR_ON = 0xE003,
            INACTIVE = 0xE004,
            ICON = 0xE010;
    private static final int[][] ICONS = {
        {7, 2}, {8, 2}, {9, 2}, {10, 2}, {11, 2}, {12, 2}, {4, 2}, {5, 2}, {6, 2}, {0, 6}, {1, 6}, {2, 6}, {3, 6},
        {0, 7}, {1, 7}, {2, 7}, {3, 7}, {0, 1}
    };

    private static final int TOOL_ICON = 0,
            PLACEMENT_ICON = 6,
            PATTERN_ICON = 9,
            DIAGONAL_ICON = 15,
            MATERIAL_ICON = 16,
            CONFIRM_ICON = 17;
    private static final String[] BRUSHES = {"cuboid", "sphere", "cylinder", "surface", "cluster"};
    private static final Map<UUID, Session> SESSIONS = PolymerDialogSessions.states();

    private static final class Session {
        final String token = UUID.randomUUID().toString();
        final InteractionHand hand;
        final ItemStack zapper;
        TerrainBrushes brush;
        final int[] params = {1, 1, 1};
        boolean followDiagonals, acrossMaterials;
        TerrainTools tool;
        PlacementOptions placement;
        PlacementPatterns pattern;
        int revision;

        Session(InteractionHand hand, ItemStack zapper) {
            this.hand = hand;
            this.zapper = zapper;
            brush = zapper.getOrDefault(AllDataComponents.SHAPER_BRUSH, TerrainBrushes.Cuboid);
            if (zapper.has(AllDataComponents.SHAPER_BRUSH_PARAMS)) {
                BlockPos data = zapper.get(AllDataComponents.SHAPER_BRUSH_PARAMS);
                params[0] = data.getX();
                params[1] = data.getY();
                params[2] = data.getZ();
                followDiagonals = params[1] == 0;
                acrossMaterials = params[2] == 0;
            }
            tool = zapper.getOrDefault(AllDataComponents.SHAPER_TOOL, TerrainTools.Fill);
            placement = zapper.getOrDefault(AllDataComponents.SHAPER_PLACEMENT_OPTIONS, PlacementOptions.Merged);
            pattern = zapper.getOrDefault(AllDataComponents.PLACEMENT_PATTERN, PlacementPatterns.Solid);
            normalise();
        }

        void normalise() {
            Brush b = brush.get();
            for (int i = 0; i < 3; i++)
                params[i] = Math.clamp(params[i], b.getMin(i), Math.max(b.getMin(i), b.getMax(i)));
            TerrainTools[] tools = b.getSupportedTools();
            boolean supported = false;
            for (TerrainTools t : tools) supported |= t == tool;
            if (!supported) tool = tools[0];
        }
    }

    private PolymerWorldshaperScreen() {}

    static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(AllItems.WORLDSHAPER) || player.isSpectator()) return;
        Session session = new Session(hand, stack);
        SESSIONS.put(player.getUUID(), session);
        PolymerDialogForms.open(player, dialog(session));
    }

    private record Run(
            int x0,
            int x1,
            int y0,
            int y1,
            String operation,
            int value,
            @Nullable Component tooltip) {}

    private static Holder<Dialog> dialog(Session s) {
        Brush brush = s.brush.get();
        List<Run> runs = new ArrayList<>();
        Pen pen = new Pen(Component.empty());
        pen.glyph(
                Component.literal("\uE000")
                        .withStyle(
                                Style.EMPTY.withColor(0xFFFFFF).withoutShadow().withFont(ART_FONT)),
                ADVANCE);
        String title = s.zapper.getHoverName().getString();
        pen.text((W - PolymerGogglesOverlay.width(title)) / 2, 4, title, 0x54214F, false);
        pen.text(
                61,
                25,
                PolymerSchematicGuis.lang("create.gui.terrainzapper.brush." + BRUSHES[s.brush.ordinal()]),
                0xFFFFFF,
                true);
        runs.add(new Run(56, 94, 20, 38, "brush", -1, tip("create.gui.terrainzapper.brush")));
        runs.add(new Run(94, 133, 20, 38, "brush", 1, tip("create.gui.terrainzapper.brush")));
        for (int i = 0; i < 3; i++) {
            int x = 56 + 20 * i;
            if (i >= brush.amtParams) {
                pen.tile(x, 40, INACTIVE, 18, 0xFFFFFF);
                continue;
            }
            String value = Integer.toString(s.params[i]);
            pen.text(65 + 20 * i - PolymerGogglesOverlay.width(value) / 2, 45, value, 0xFFFFFF, true);
            Component label = Component.literal(paramLabel(s.brush, i));
            runs.add(new Run(x, x + 9, 40, 58, "param" + i, -1, label));
            runs.add(new Run(x + 9, x + 18, 40, 58, "param" + i, 1, label));
        }
        if (brush.hasConnectivityOptions()) {
            int x1 = 7 + 4 * 18;
            pen.tile(x1, 73, s.followDiagonals ? INDICATOR_ON : INDICATOR, 18, 0xFFFFFF);
            pen.tile(x1 + 18, 73, s.acrossMaterials ? INDICATOR_ON : INDICATOR, 18, 0xFFFFFF);
            button(pen, x1, 79, false, DIAGONAL_ICON);
            button(pen, x1 + 18, 79, false, MATERIAL_ICON);
            runs.add(new Run(x1, x1 + 18, 79, 97, "diagonal", 0, tip("create.gui.terrainzapper.searchDiagonal")));
            runs.add(new Run(x1 + 18, x1 + 36, 79, 97, "material", 0, tip("create.gui.terrainzapper.searchFuzzy")));
        }
        pen.text(7, 69, PolymerSchematicGuis.lang("create.gui.terrainzapper.tool"), 0x767676, false);
        TerrainTools[] tools = brush.getSupportedTools();
        for (int i = 0; i < tools.length; i++) {
            button(pen, 7 + i * 18, 79, tools[i] == s.tool, TOOL_ICON + tools[i].ordinal());
            runs.add(new Run(
                    7 + i * 18,
                    25 + i * 18,
                    79,
                    97,
                    "tool",
                    tools[i].ordinal(),
                    tip("create.gui.terrainzapper.tool." + tools[i].translationKey)));
        }
        if (brush.hasPlacementOptions()) {
            pen.text(136, 69, PolymerSchematicGuis.lang("create.gui.terrainzapper.placement"), 0x767676, false);
            for (PlacementOptions option : PlacementOptions.values()) {
                int x = 136 + option.ordinal() * 18;
                button(pen, x, 79, option == s.placement, PLACEMENT_ICON + option.ordinal());
                runs.add(new Run(
                        x,
                        x + 18,
                        79,
                        97,
                        "placement",
                        option.ordinal(),
                        tip("create.gui.terrainzapper.placement." + option.translationKey)));
            }
        }
        for (PlacementPatterns p : PlacementPatterns.values()) {
            int x = W - 76 + p.ordinal() % 3 * 18, y = 21 + p.ordinal() / 3 * 18;
            button(pen, x, y, p == s.pattern, PATTERN_ICON + p.ordinal());
            runs.add(new Run(
                    x,
                    x + 18,
                    y,
                    y + 18,
                    "pattern",
                    p.ordinal(),
                    tip("create.gui.terrainzapper.pattern." + p.translationKey)));
        }
        var shaperBlock = s.zapper.get(AllDataComponents.SHAPER_BLOCK_USED);
        if (shaperBlock != null && shaperBlock.getBlock().asItem() != Items.AIR) {
            int[] icon = PolymerHudRadial.worldshaperIcon(shaperBlock.getBlock().asItem());
            if (icon != null) {
                int x = 21;
                pen.line.append(PolymerArtKit.space(SPACE_FONT, x - pen.pen));
                pen.line.append(Component.literal(Character.toString(icon[0]))
                        .withStyle(
                                Style.EMPTY.withColor(0xFFFFFF).withoutShadow().withFont(font("zap_block"))));
                pen.pen = x + icon[1];
            }
        }
        button(pen, W - 33, H - 24, false, CONFIRM_ICON);
        runs.add(new Run(W - 33, W - 15, H - 24, H - 6, "close", 0, tip("create.action.confirm")));
        pen.finish(ADVANCE);

        List<MutableComponent> lines = new ArrayList<>();
        lines.add(pen.line);
        for (int line = 1; line < LINES; line++) {
            int y = line * 9 + 4;
            MutableComponent text = Component.empty().append(marker());
            int at = 2;
            List<Run> hit = new ArrayList<>();
            for (Run run : runs) if (y >= run.y0() && y < run.y1()) hit.add(run);
            hit.sort(Comparator.comparingInt(Run::x0));
            for (Run run : hit) {
                if (run.x0() < at) continue;
                text.append(PolymerArtKit.space(SPACE_FONT, run.x0() - at));
                CompoundTag tag = new CompoundTag();
                tag.putString("session", s.token);
                tag.putInt("revision", s.revision);
                tag.putString("operation", run.operation());
                tag.putInt("value", run.value());
                Style style = Style.EMPTY.withClickEvent(new ClickEvent.Custom(ACTION, Optional.of(tag)));
                if (run.tooltip() != null) style = style.withHoverEvent(new HoverEvent.ShowText(run.tooltip()));
                text.append(Component.empty()
                        .withStyle(style)
                        .append(PolymerArtKit.space(SPACE_FONT, run.x1() - run.x0())));
                at = run.x1();
            }
            text.append(PolymerArtKit.space(SPACE_FONT, ADVANCE - 2 - at)).append(marker());
            lines.add(text);
        }
        MutableComponent body = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) body.append(Component.literal("\n"));
            body.append(lines.get(i));
        }
        CompoundTag done = new CompoundTag();
        done.putString("session", s.token);
        done.putInt("revision", s.revision);
        done.putString("operation", "close");
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(new PlainMessage(body, ADVANCE + 8));
        var common = new CommonDialogData(
                Component.empty(),
                Optional.of(s.zapper.getHoverName()),
                true,
                false,
                DialogAction.NONE,
                bodies,
                List.of());
        ActionButton close = new ActionButton(
                new CommonButtonData(PolymerGuiTextures.actionLabel("Done"), 120),
                Optional.of(new CustomAll(ACTION, Optional.of(done))));
        return Holder.direct(new MultiActionDialog(common, List.of(close), Optional.empty(), 1));
    }

    private static Component tip(String key) {
        return Component.literal(PolymerSchematicGuis.lang(key));
    }

    private static String paramLabel(TerrainBrushes brush, int index) {
        return switch (brush) {
            case Surface, Cluster -> PolymerSchematicGuis.lang("create.generic.range");
            case Sphere -> PolymerSchematicGuis.lang("create.generic.radius");
            case Cylinder ->
                PolymerSchematicGuis.lang(
                        index == 0
                                ? "create.generic.radius"
                                : index == 1 ? "create.generic.height" : "create.generic.length");
            default ->
                PolymerSchematicGuis.lang(
                        index == 0
                                ? "create.generic.width"
                                : index == 1 ? "create.generic.height" : "create.generic.length");
        };
    }

    private static void button(Pen pen, int x, int y, boolean green, int icon) {
        pen.tile(x, y, green ? BUTTON_GREEN : BUTTON, 18, 0xFFFFFF);
        pen.tile(x + 1, y + 1, ICON + icon, 16, 0xFFFFFF);
    }

    static boolean handle(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!PolymerDialogForms.validSubmission(player, id, payload, ACTION)) return true;
        if (!ACTION.equals(id)) return false;
        Session s = SESSIONS.get(player.getUUID());
        CompoundTag tag = PolymerDialogForms.payload(payload).orElse(null);
        if (s == null
                || tag == null
                || !tag.getString("session").orElse("").equals(s.token)
                || tag.getInt("revision").orElse(-1) != s.revision) return true;
        if (player.getItemInHand(s.hand) != s.zapper) {
            SESSIONS.remove(player.getUUID());
            NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
            return true;
        }
        String operation = tag.getString("operation").orElse("");
        int value = tag.getInt("value").orElse(0);
        Brush brush = s.brush.get();
        switch (operation) {
            case "brush" -> {
                s.brush = TerrainBrushes.values()[
                        Math.floorMod(s.brush.ordinal() + value, TerrainBrushes.values().length)];
                s.normalise();
            }
            case "param0", "param1", "param2" -> {
                int i = operation.charAt(5) - '0';
                if (i < brush.amtParams)
                    s.params[i] = Math.clamp(
                            s.params[i] + value, brush.getMin(i), Math.max(brush.getMin(i), brush.getMax(i)));
            }
            case "diagonal" -> s.followDiagonals = !s.followDiagonals;
            case "material" -> s.acrossMaterials = !s.acrossMaterials;
            case "tool" -> {
                TerrainTools tool = TerrainTools.values()[Math.clamp(value, 0, TerrainTools.values().length - 1)];
                for (TerrainTools t : brush.getSupportedTools()) if (t == tool) s.tool = tool;
            }
            case "placement" ->
                s.placement = PlacementOptions.values()[Math.clamp(value, 0, PlacementOptions.values().length - 1)];
            case "pattern" ->
                s.pattern = PlacementPatterns.values()[Math.clamp(value, 0, PlacementPatterns.values().length - 1)];
            case "close" -> {
                apply(player, s);
                SESSIONS.remove(player.getUUID());
                NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
                return true;
            }
            default -> {
                return true;
            }
        }
        apply(player, s);
        s.revision++;
        PolymerDialogForms.open(player, dialog(s));
        return true;
    }

    private static void apply(ServerPlayer player, Session s) {
        Brush brush = s.brush.get();
        int x = s.params[0];
        int y = brush.hasConnectivityOptions() ? s.followDiagonals ? 0 : 1 : s.params[1];
        int z = brush.hasConnectivityOptions() ? s.acrossMaterials ? 0 : 1 : s.params[2];
        AllHandle.onConfigureWorldshaper(
                player.connection,
                new ConfigureWorldshaperPacket(s.hand, s.pattern, s.brush, x, y, z, s.tool, s.placement));
    }

    private static final class Pen {
        final MutableComponent line;
        int pen;

        Pen(MutableComponent line) {
            this.line = line;
        }

        void glyph(Component component, int advance) {
            line.append(component);
            pen += advance;
        }

        void tile(int x, int y, int codepoint, int width, int color) {
            line.append(PolymerArtKit.space(SPACE_FONT, x - pen));
            line.append(Component.literal(Character.toString(codepoint))
                    .withStyle(Style.EMPTY.withColor(color).withoutShadow().withFont(rowFont(y))));
            pen = x + width + 1;
        }

        void text(int x, int y, String text, int color, boolean shadow) {
            if (text.isEmpty()) return;
            line.append(PolymerArtKit.space(SPACE_FONT, x - pen));
            Style style = Style.EMPTY.withColor(color).withFont(rowFont(y));
            line.append(Component.literal(text).withStyle(shadow ? style : style.withoutShadow()));
            pen = x + PolymerGogglesOverlay.width(text);
        }

        void finish(int advance) {
            line.append(PolymerArtKit.space(SPACE_FONT, advance - pen));
        }
    }

    private static Component marker() {
        return Component.literal("\uE001")
                .withStyle(Style.EMPTY.withColor(0xFFFFFF).withoutShadow().withFont(ART_FONT));
    }

    private static FontDescription font(String name) {
        return new FontDescription.Resource(CreateIds.id(name));
    }

    private static FontDescription rowFont(int y) {
        return font("zap_row_" + y);
    }

    static void addAssets(ResourcePackBuilder builder) {
        try {
            BufferedImage curiosities = image(builder, "assets/create/textures/gui/curiosities.png");
            BufferedImage widgets = image(builder, "assets/create/textures/gui/widgets.png");
            BufferedImage icons = image(builder, "assets/create/textures/gui/icons.png");
            BufferedImage art = PolymerArtKit.copy(curiosities.getSubimage(0, 0, W, H));
            art.setRGB(W - 1, 0, art.getRGB(W - 1, 0) | 0x01000000);
            PolymerGuiGlyphs.fontPng(builder, "zap_art", art);
            BufferedImage marker = new BufferedImage(1, 9, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 9; y++) marker.setRGB(0, y, 0x01FFFFFF);
            PolymerGuiGlyphs.fontPng(builder, "zap_marker", marker);
            JsonArray artProviders = new JsonArray();
            artProviders.add(PolymerGuiGlyphs.bitmap("create:font/zap_art.png", H, 7, "\uE000"));
            artProviders.add(PolymerGuiGlyphs.bitmap("create:font/zap_marker.png", 9, 7, "\uE001"));
            PolymerGuiGlyphs.writeFont(builder, "zap_art", artProviders);

            List<String> files = new ArrayList<>();
            List<Integer> chars = new ArrayList<>();
            List<Integer> heights = new ArrayList<>();
            tile(builder, files, chars, heights, "zap_button", widgets.getSubimage(0, 0, 18, 18), BUTTON);
            tile(builder, files, chars, heights, "zap_button_green", widgets.getSubimage(72, 0, 18, 18), BUTTON_GREEN);
            tile(builder, files, chars, heights, "zap_indicator", widgets.getSubimage(0, 18, 18, 6), INDICATOR);
            tile(builder, files, chars, heights, "zap_indicator_on", widgets.getSubimage(18, 18, 18, 6), INDICATOR_ON);
            tile(builder, files, chars, heights, "zap_inactive", curiosities.getSubimage(238, 0, 18, 18), INACTIVE);
            for (int i = 0; i < ICONS.length; i++)
                tile(
                        builder,
                        files,
                        chars,
                        heights,
                        "zap_icon_" + i,
                        icons.getSubimage(ICONS[i][0] * 16, ICONS[i][1] * 16, 16, 16),
                        ICON + i);
            JsonArray ascii = PolymerArtKit.asciiRows(builder);
            for (int y = 0; y <= H; y++) {
                int ascent = 7 - y;
                JsonArray providers = new JsonArray();
                JsonObject space = new JsonObject();
                space.addProperty("type", "space");
                JsonObject advances = new JsonObject();
                advances.addProperty(" ", 4);
                space.add("advances", advances);
                providers.add(space);
                JsonObject text = PolymerGuiGlyphs.bitmap("minecraft:font/ascii.png", 8, ascent, null);
                text.add("chars", ascii.deepCopy());
                providers.add(text);
                for (int i = 0; i < files.size(); i++) {
                    if (ascent > heights.get(i)) continue;
                    providers.add(PolymerGuiGlyphs.bitmap(
                            files.get(i), heights.get(i), ascent, Character.toString(chars.get(i))));
                }
                PolymerGuiGlyphs.writeFont(builder, "zap_row_" + y, providers);
            }
        } catch (IOException | RuntimeException exception) {
            Create.LOGGER.warn("Worldshaper screen art unavailable", exception);
        }
    }

    private static void tile(
            ResourcePackBuilder builder,
            List<String> files,
            List<Integer> chars,
            List<Integer> heights,
            String name,
            BufferedImage source,
            int codepoint)
            throws IOException {
        BufferedImage image = PolymerArtKit.copy(source);
        int x = image.getWidth() - 1;
        boolean visible = false;
        for (int y = 0; y < image.getHeight(); y++) visible |= (image.getRGB(x, y) >>> 24) != 0;
        if (!visible) image.setRGB(x, 0, 0x01FFFFFF);
        PolymerGuiGlyphs.fontPng(builder, name, image);
        files.add("create:font/" + name + ".png");
        chars.add(codepoint);
        heights.add(image.getHeight());
    }
}
