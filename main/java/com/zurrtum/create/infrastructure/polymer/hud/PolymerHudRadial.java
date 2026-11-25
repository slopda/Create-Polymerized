package com.zurrtum.create.infrastructure.polymer.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.AdapterInteractions;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGuiGlyphs;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import dev.veeda.createpoly.assets.PngCodec;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class PolymerHudRadial {
    static final float PX_PER_DEGREE = 3;
    private static final String NAMESPACE = "create";
    private static final FontDescription SPACE_FONT = font("hud/space");
    private static final int SPACE = 0xE600;
    private static final int FRAME_BASE = 0xE300, RING_BASE = 0xE400;
    private static final int TOOLBELT_ICON_BASE = 0xF0000, WRENCH_ICON_BASE = 0xF8000;

    static final int[] TOOLBELT_ROWS = {-40, -36, -28, 0, 28, 40};

    static final int[] FRAME_ROWS = {-40, -28, 0, 28, 40};
    private static final int FRAME_CELL = 80;

    enum Frame {
        SLOT,
        EMPTY,
        INACTIVE,
        HIGHLIGHT
    }

    record Glyph(int x, int codepoint, FontDescription font, int advance, int color) {
        Glyph(int x, int codepoint, FontDescription font, int advance) {
            this(x, codepoint, font, advance, 0xFFFFFF);
        }
    }

    interface Session {
        boolean valid(ServerPlayer player);

        boolean tick(ServerPlayer player, float x, float y, int age);

        List<Glyph> draw();

        Component tip();

        boolean leftClick(ServerPlayer player);

        boolean rightClick(ServerPlayer player);

        boolean swap(ServerPlayer player);

        boolean scroll(ServerPlayer player, int steps);
    }

    private static final class Open {
        final Session session;
        final float yaw, pitch;
        float originYaw, originPitch;
        Component lastTip;
        int age;
        long lastSwing = -1;

        Open(Session session, float yaw, float pitch) {
            this.session = session;
            this.yaw = originYaw = yaw;
            this.pitch = originPitch = pitch;
        }
    }

    static final float MAX_POINTER = 95;

    private static final Map<UUID, Open> OPEN = new HashMap<>();
    private static volatile PolymerHudIcons.Atlas toolbeltIcons, wrenchIcons;
    private static volatile Map<Integer, Integer> frameAdvances = Map.of(), ringAdvances = Map.of();
    private static volatile Map<Integer, Integer> wrenchRows = Map.of();
    private static volatile Map<Integer, Integer> textWidths = Map.of(), textRows = Map.of();
    private static volatile Map<Integer, Integer> indicatorAdvances = Map.of(), uiAdvances = Map.of();
    private static volatile Map<String, Integer> uiIcons = Map.of();
    private static volatile int dimAdvance = 1, streakAdvance = 1, cursorAdvance = 1;
    private static final int CURSOR_BASE = 0xE800, CURSOR_RANGE = 100, CURSOR_SIZE = 9;
    private static final int DIM_LEVELS = 5, DIM_SIZE = 1024, DIM_BASE = 0xE700, STREAK = 0xE710, STREAK_LENGTH = 65;
    private static final int INDICATOR_STEPS = 32, INDICATOR_CANVAS = 120, INDICATOR_BASE = 0xE720, UI_BASE = 0xE780;
    static final int[] WRENCH_TEXT_TOPS = {-13, -3, 7, -30, 22};

    private PolymerHudRadial() {}

    static boolean ready() {
        return toolbeltIcons != null && wrenchIcons != null && !frameAdvances.isEmpty();
    }

    static void register() {
        Identifier phase = Identifier.fromNamespaceAndPath("createpoly", "hud_radial");
        UseBlockCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
        AdapterInteractions.useBlock(phase, (p, l, hand, hit) -> use(p, hand));
        AdapterInteractions.useItem(phase, (p, l, hand) -> use(p, hand));
        AdapterInteractions.useEntity(phase, (p, l, hand, entity, hit) -> use(p, hand));
        AdapterInteractions.attackBlock(
                phase,
                (p, l, hand, pos, dir) ->
                        p instanceof ServerPlayer sp && isOpen(sp) ? InteractionResult.FAIL : InteractionResult.PASS);
        AdapterInteractions.attackEntity(
                phase,
                (p, l, hand, entity, hit) ->
                        p instanceof ServerPlayer sp && isOpen(sp) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    private static InteractionResult use(Player p, InteractionHand hand) {
        if (!(p instanceof ServerPlayer player) || !isOpen(player)) return InteractionResult.PASS;
        if (hand == InteractionHand.MAIN_HAND) rightClick(player);
        return InteractionResult.FAIL;
    }

    static boolean open(ServerPlayer player, Session session) {
        if (!ready() || PolymerNativeClients.isNative(player)) return false;
        OPEN.put(player.getUUID(), new Open(session, player.getYRot(), player.getXRot()));
        return true;
    }

    public static boolean isOpen(ServerPlayer player) {
        return OPEN.containsKey(player.getUUID());
    }

    static void close(ServerPlayer player) {
        Open open = OPEN.remove(player.getUUID());
        if (open == null) return;
        NativePresentationPolicy.sendPresentation(
                player, new ClientboundPlayerRotationPacket(open.yaw, false, open.pitch, false));
        NativePresentationPolicy.sendPresentation(player, new ClientboundSetActionBarTextPacket(Component.empty()));
    }

    static void forget(UUID player) {
        OPEN.remove(player);
    }

    private static boolean dispatch(ServerPlayer player, Predicate<Session> action) {
        Open open = OPEN.get(player.getUUID());
        if (open == null) return false;
        if (!open.session.valid(player) || action.test(open.session)) close(player);
        return true;
    }

    public static boolean swap(ServerPlayer player) {
        return dispatch(player, s -> s.swap(player));
    }

    private static final int HELD_SWING_TICKS = 3;

    public static boolean leftClick(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        if (open == null) return false;
        long now = player.level().getServer().getTickCount();
        boolean held = open.lastSwing >= 0 && now - open.lastSwing <= HELD_SWING_TICKS;
        open.lastSwing = now;
        if (held) return true;
        return dispatch(player, s -> s.leftClick(player));
    }

    public static boolean rightClick(ServerPlayer player) {
        return dispatch(player, s -> s.rightClick(player));
    }

    public static boolean scroll(ServerPlayer player, int slot) {
        if (!OPEN.containsKey(player.getUUID())) return false;
        int from = player.getInventory().getSelectedSlot(), steps = Math.floorMod(slot - from + 4, 9) - 4;
        NativePresentationPolicy.sendPresentation(player, new ClientboundSetHeldSlotPacket(from));
        if (steps != 0) dispatch(player, s -> s.scroll(player, steps));
        return true;
    }

    static Component tick(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        if (open == null) return null;
        Session session = open.session;
        if (!session.valid(player) || player.isRemoved() || !player.isAlive() || player.isSpectator()) {
            close(player);
            return null;
        }
        float yaw = Mth.wrapDegrees(player.getYRot() - open.originYaw), pitch = player.getXRot() - open.originPitch;
        float x = yaw * PX_PER_DEGREE, y = pitch * PX_PER_DEGREE, r = (float) Math.hypot(x, y);
        if (r > MAX_POINTER) {
            float excess = 1 - MAX_POINTER / r;
            open.originYaw += yaw * excess;
            open.originPitch += pitch * excess;
            x *= MAX_POINTER / r;
            y *= MAX_POINTER / r;
        }
        open.age++;
        if (session.tick(player, x, y, open.age)) {
            close(player);
            return null;
        }
        Component tip = session.tip();
        if (!tip.equals(open.lastTip)) {
            open.lastTip = tip;
            NativePresentationPolicy.sendPresentation(player, new ClientboundSetActionBarTextPacket(tip));
        }
        return compose(session.draw());
    }

    static Glyph frame(Frame frame, int cx, int cy) {
        int row = rowIndex(FRAME_ROWS, cy), codepoint = FRAME_BASE + row * 4 + frame.ordinal();
        int left = cx - (frame == Frame.HIGHLIGHT ? 13 : 12);
        return new Glyph(left, codepoint, font("hud/tb_frames"), frameAdvances.getOrDefault(codepoint, 12));
    }

    static Glyph toolbeltIcon(String key, int cx, int cy) {
        PolymerHudIcons.Atlas atlas = toolbeltIcons;
        Integer codepoint = atlas.codepoint(key);
        if (codepoint == null) codepoint = atlas.codepoint(PolymerHudIcons.key(Items.BARRIER));
        return new Glyph(
                cx - 9,
                codepoint,
                font("hud/tb_row_" + rowIndex(TOOLBELT_ROWS, cy)),
                atlas.advances().get(codepoint));
    }

    static Glyph toolbeltIcon(Item item, int cx, int cy) {
        return toolbeltIcon(PolymerHudIcons.key(item), cx, cy);
    }

    static Glyph wrenchIcon(Item item, int cx, int cy) {
        PolymerHudIcons.Atlas atlas = wrenchIcons;
        Integer codepoint = atlas.codepoint(PolymerHudIcons.key(item));
        if (codepoint == null) codepoint = atlas.codepoint(PolymerHudIcons.key(Items.BARRIER));
        Integer row = wrenchRows.get(cy);
        if (row == null) throw new IllegalArgumentException("No wrench icon row for y " + cy);
        return new Glyph(
                cx - 12, codepoint, font("hud/wr_row_" + row), atlas.advances().get(codepoint));
    }

    static Glyph wrenchStateIcon(BlockState state, int cx, int cy) {
        PolymerHudIcons.Atlas atlas = wrenchIcons;
        Integer codepoint = atlas.codepoint("state:" + stateKey(iconState(state)));
        if (codepoint == null)
            return wrenchIcon(
                    state.getBlock().asItem() == Items.AIR
                            ? AllItems.WRENCH
                            : state.getBlock().asItem(),
                    cx,
                    cy);
        Integer row = wrenchRows.get(cy);
        if (row == null) throw new IllegalArgumentException("No wrench icon row for y " + cy);
        return new Glyph(
                cx - 12, codepoint, font("hud/wr_row_" + row), atlas.advances().get(codepoint));
    }

    static BlockState iconState(BlockState state) {
        BlockState result = state.getBlock().defaultBlockState();
        for (Property<?> property : state.getProperties())
            if (PolymerRadialWrench.wrenchProperty(property)) result = copy(result, state, property);
        return result;
    }

    private static <T extends Comparable<T>> BlockState copy(
            BlockState target, BlockState source, Property<T> property) {
        return target.setValue(property, source.getValue(property));
    }

    static String stateKey(BlockState state) {
        StringBuilder key = new StringBuilder(
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                .append('[');
        boolean first = true;
        for (Property<?> property : state.getProperties()) {
            if (!first) key.append(',');
            first = false;
            key.append(property.getName()).append('=').append(valueName(state, property));
        }
        return key.append(']').toString();
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    static final int TEXT_COLOR = 0xEEEEEE;

    static List<Glyph> text(String text, int centreX, int top, int color) {
        Map<Integer, Integer> widths = textWidths;
        int width = 0;
        for (int i = 0; i < text.length(); i++) width += vanillaAdvance(widths, text.charAt(i));
        int x = centreX - width / 2;
        List<Glyph> shadow = new ArrayList<>(), main = new ArrayList<>();
        int shadowColor = (color & 0xFCFCFC) >> 2;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Integer w = widths.get((int) c);
            if (c != ' ' && w != null) {
                int advance = (int) (.5f + w * .5f) + 1;
                shadow.add(new Glyph(x + 1, c, font("hud/txt_" + textRow(top + 1)), advance, shadowColor));
                main.add(new Glyph(x, c, font("hud/txt_" + textRow(top)), advance, color));
            }
            x += vanillaAdvance(widths, c);
        }
        shadow.addAll(main);
        return shadow;
    }

    private static int vanillaAdvance(Map<Integer, Integer> widths, char c) {
        if (c == ' ') return 4;
        Integer w = widths.get((int) c);
        return w == null ? 6 : w + 1;
    }

    private static int textRow(int top) {
        Integer row = textRows.get(top);
        if (row == null) throw new IllegalArgumentException("No HUD text row for top " + top);
        return row;
    }

    static Glyph dim(int age) {
        int level = Math.min(DIM_LEVELS, age / 4 + 1);
        return new Glyph(-DIM_SIZE, DIM_BASE + level - 1, font("hud/dim"), dimAdvance);
    }

    static Glyph streaks() {
        return new Glyph(-STREAK_LENGTH, STREAK, font("hud/streak"), streakAdvance);
    }

    static Glyph indicator(float x, float y) {
        double theta = Math.atan2(x, y);
        int index = Math.floorMod((int) Math.round(theta / (2 * Math.PI) * INDICATOR_STEPS), INDICATOR_STEPS);
        return new Glyph(
                -INDICATOR_CANVAS / 2,
                INDICATOR_BASE + index,
                font("hud/indicator"),
                indicatorAdvances.getOrDefault(INDICATOR_BASE + index, 1));
    }

    static Glyph cursor(float x, float y) {
        int cy = Math.clamp(evenY(y), -CURSOR_RANGE, CURSOR_RANGE);
        return new Glyph(
                Math.round(x) - CURSOR_SIZE / 2,
                CURSOR_BASE + (cy + CURSOR_RANGE) / 2,
                font("hud/cursor"),
                cursorAdvance);
    }

    static Glyph uiIcon(String name, int x, int top) {
        Integer codepoint = uiIcons.get(name + "@" + top);
        if (codepoint == null) throw new IllegalArgumentException("No UI icon " + name + " at " + top);
        return new Glyph(x, codepoint, font("hud/ui"), uiAdvances.getOrDefault(codepoint, 9));
    }

    static Glyph wrenchRing(int n, int hovered) {
        int codepoint = RING_BASE + n * 8 + hovered;
        return new Glyph(-RING / 2, codepoint, font("hud/wr_ring"), ringAdvances.getOrDefault(codepoint, RING / 2 + 1));
    }

    static int evenY(double y) {
        return 2 * (int) Math.round(y / 2);
    }

    static int[] wrenchCell(int i, int n) {
        double a = Math.toRadians(i * 360.0 / n);
        return new int[] {(int) Math.round(80 * Math.sin(a)), evenY(-80 * Math.cos(a))};
    }

    private static int rowIndex(int[] rows, int cy) {
        for (int i = 0; i < rows.length; i++) if (rows[i] == cy) return i;
        throw new IllegalArgumentException("No HUD row for y " + cy);
    }

    private static FontDescription font(String path) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    static Component compose(List<Glyph> glyphs) {
        MutableComponent out = Component.empty();
        float pen = 0;
        for (Glyph glyph : glyphs) {
            float target = glyph.x() / 2f;
            out.append(spaces(target - pen));
            out.append(Component.literal(Character.toString(glyph.codepoint()))
                    .withStyle(Style.EMPTY
                            .withFont(glyph.font())
                            .withColor(glyph.color())
                            .withoutShadow()));
            pen = target + glyph.advance();
        }
        out.append(spaces(-pen));
        return out;
    }

    private static Component spaces(float delta) {
        return Component.literal(PolymerGuiGlyphs.spaces(SPACE, delta, 1e-4f))
                .withStyle(Style.EMPTY.withFont(SPACE_FONT));
    }

    private static final int RING = 240;

    public static void addAssets(ResourcePackBuilder builder, Predicate<Item> wrenchable) {
        try {
            spaceFont(builder);
            frames(builder);
            rings(builder);
            dim(builder);
            streak(builder);
            indicators(builder);
            uiIcons(builder);
            cursor(builder);
            TreeSet<Integer> tops = new TreeSet<>();
            for (int top : WRENCH_TEXT_TOPS) {
                tops.add(top);
                tops.add(top + 1);
            }
            for (int n = 2; n <= 8; n++)
                for (int i = 0; i < n; i++) {
                    int y = wrenchCell(i, n)[1] + 15;
                    tops.add(y);
                    tops.add(y + 1);
                }
            textFonts(builder, tops);
            List<String> all = new ArrayList<>();
            List<String> wrench = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                all.add(PolymerHudIcons.key(item));
                if (wrenchable.test(item)) {
                    wrench.add(PolymerHudIcons.key(item));
                    if (item instanceof BlockItem blockItem)
                        for (var state :
                                blockItem.getBlock().getStateDefinition().getPossibleStates())
                            if (state == iconState(state))
                                wrench.add("state:" + stateKey(state) + "|" + PolymerHudIcons.key(item));
                }
            }
            if (!wrench.contains(PolymerHudIcons.key(Items.BARRIER))) wrench.add(PolymerHudIcons.key(Items.BARRIER));
            for (String icon : List.of(
                    "toolbelt_deposit",
                    "toolbelt_unequip",
                    "toolbelt_flip",
                    "radial_property_up",
                    "radial_property_down")) all.add("texture:create:item/ui/gui_" + icon);
            Map<String, Integer> toolbeltFonts = new LinkedHashMap<>();
            for (int i = 0; i < TOOLBELT_ROWS.length; i++)
                toolbeltFonts.put("hud/tb_row_" + i, (34 - TOOLBELT_ROWS[i]) / 2);
            toolbeltIcons =
                    PolymerHudIcons.write(builder, "toolbelt_icons", all, 74, 1, 37, toolbeltFonts, TOOLBELT_ICON_BASE);
            worldshaperFont(builder, toolbeltIcons);
            TreeSet<Integer> ys = new TreeSet<>();
            for (int n = 2; n <= 8; n++) for (int i = 0; i < n; i++) ys.add(wrenchCell(i, n)[1]);
            Map<Integer, Integer> rows = new HashMap<>();
            Map<String, Integer> wrenchFonts = new LinkedHashMap<>();
            int index = 0;
            for (int y : ys) {
                rows.put(y, index);
                wrenchFonts.put("hud/wr_row_" + index++, (36 - y) / 2);
            }
            wrenchRows = Map.copyOf(rows);
            wrenchIcons =
                    PolymerHudIcons.write(builder, "wrench_icons", wrench, 80, 0, 60, wrenchFonts, WRENCH_ICON_BASE);
        } catch (IOException | RuntimeException error) {
            toolbeltIcons = null;
            wrenchIcons = null;
            Create.LOGGER.warn("HUD radial menus disabled: cannot build their assets", error);
        }
    }

    private static final int ZAP_BLOCK_TOP = 27;

    private static void worldshaperFont(ResourcePackBuilder builder, PolymerHudIcons.Atlas atlas) throws IOException {
        int height = atlas.cellHeight() * 3 / 2, ascent = 7 - ZAP_BLOCK_TOP;
        if (ascent > height) throw new IOException("Worldshaper block font: ascent > height");
        JsonObject provider = PolymerGuiGlyphs.bitmapProvider(atlas.file(), height, ascent);
        provider.add("chars", atlas.chars().deepCopy());
        JsonArray providers = new JsonArray();
        providers.add(provider);
        font(builder, "zap_block", providers);
    }

    public static int @org.jspecify.annotations.Nullable [] worldshaperIcon(Item item) {
        PolymerHudIcons.Atlas atlas = toolbeltIcons;
        if (atlas == null) return null;
        Integer codepoint = atlas.codepoint(PolymerHudIcons.key(item));
        if (codepoint == null) return null;
        int width = atlas.widths().getOrDefault(codepoint, 16);
        return new int[] {codepoint, (int) (.5f + width * 1.5f) + 1};
    }

    private static JsonObject provider(String file, int height, int ascent, int codepoint) throws IOException {
        if (ascent > height)
            throw new IOException(
                    "HUD glyph " + Integer.toHexString(codepoint) + ": ascent " + ascent + " > height " + height);
        JsonObject provider = PolymerGuiGlyphs.bitmapProvider(NAMESPACE + ":" + file, height, ascent);
        JsonArray chars = new JsonArray();
        chars.add(Character.toString(codepoint));
        provider.add("chars", chars);
        return provider;
    }

    private static void font(ResourcePackBuilder builder, String name, JsonArray providers) {
        JsonObject json = new JsonObject();
        json.add("providers", providers);
        builder.addStringData("assets/" + NAMESPACE + "/font/" + name + ".json", json.toString());
    }

    private static int actualWidth(BufferedImage image) {
        int width = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if (image.getRGB(x, y) >>> 24 != 0) width = Math.max(width, x + 1);
        return width;
    }

    private static void dim(ResourcePackBuilder builder) throws IOException {
        JsonArray providers = new JsonArray();
        for (int level = 1; level <= DIM_LEVELS; level++) {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            int alpha = Math.round(0x50 * level / (float) DIM_LEVELS);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) image.setRGB(x, y, alpha << 24 | 0x101010);
            String file = "font/hud/dim_" + level + ".png";
            builder.addData("assets/" + NAMESPACE + "/textures/" + file, PolymerHudIcons.png(image));
            providers.add(provider(file, DIM_SIZE, (24 + DIM_SIZE) / 2, DIM_BASE + level - 1));
        }
        font(builder, "hud/dim", providers);
        dimAdvance = (int) (.5f + 4 * (DIM_SIZE / 4f)) + 1;
    }

    private static void streak(ResourcePackBuilder builder) throws IOException {
        int w = STREAK_LENGTH * 2, rows = 40;
        BufferedImage image = new BufferedImage(w, rows, BufferedImage.TYPE_INT_ARGB);
        int split1 = (int) (.5 * STREAK_LENGTH), split2 = (int) (.75 * STREAK_LENGTH);
        float c1 = .8f * .625f, c2 = .8f * .5f, c3 = .8f * .0625f, c4 = 0;
        for (int d = 0; d < STREAK_LENGTH; d++) {
            float alpha = d < split1
                    ? c1 + (c2 - c1) * d / split1
                    : d < split2
                            ? c2 + (c3 - c2) * (d - split1) / (float) (split2 - split1)
                            : c3 + (c4 - c3) * (d - split2) / (float) (STREAK_LENGTH - split2);
            int argb = Math.round(alpha * 255) << 24;
            for (int y = 0; y < 32; y++) {
                image.setRGB(STREAK_LENGTH + d, y, argb);
                image.setRGB(STREAK_LENGTH - 1 - d, y, argb);
            }
        }
        String file = "font/hud/streak.png";
        builder.addData("assets/" + NAMESPACE + "/textures/" + file, PolymerHudIcons.png(image));
        JsonArray providers = new JsonArray();
        providers.add(provider(file, rows / 2, (24 + 16) / 2, STREAK));
        font(builder, "hud/streak", providers);
        streakAdvance = (int) (.5f + actualWidth(image) * .5f) + 1;
    }

    private static void indicators(ResourcePackBuilder builder) throws IOException {
        JsonArray providers = new JsonArray();
        Map<Integer, Integer> advances = new HashMap<>();
        float[][] fan = {{0, 0}, {5, -5}, {3, -4.5f}, {0, -4.2f}, {-3, -4.5f}, {-5, -5}};
        float[] alphas = {1, .6f, .7f, .7f, .7f, .6f};
        int ss = 4, size = INDICATOR_CANVAS;
        for (int step = 0; step < INDICATOR_STEPS; step++) {
            double theta = step * 2 * Math.PI / INDICATOR_STEPS;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < size; py++)
                for (int px = 0; px < size; px++) {
                    float coverage = 0, alphaSum = 0;
                    for (int sy = 0; sy < ss; sy++)
                        for (int sx = 0; sx < ss; sx++) {
                            double x = px + (sx + .5) / ss - size / 2.0, y = py + (sy + .5) / ss - size / 2.0;
                            double lx = x * Math.cos(theta) - y * Math.sin(theta),
                                    ly = x * Math.sin(theta) + y * Math.cos(theta) - 53;
                            for (int tri = 1; tri < fan.length - 1; tri++) {
                                float[] a = fan[0], b = fan[tri], c = fan[tri + 1];
                                double d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
                                double wa = ((b[1] - c[1]) * (lx - c[0]) + (c[0] - b[0]) * (ly - c[1])) / d;
                                double wb = ((c[1] - a[1]) * (lx - c[0]) + (a[0] - c[0]) * (ly - c[1])) / d,
                                        wc = 1 - wa - wb;
                                if (wa >= 0 && wb >= 0 && wc >= 0) {
                                    coverage++;
                                    alphaSum += wa * alphas[0] + wb * alphas[tri] + wc * alphas[tri + 1];
                                    break;
                                }
                            }
                        }
                    if (coverage == 0) continue;
                    int alpha = Math.round(alphaSum / (ss * ss) * 255);
                    image.setRGB(px, py, alpha << 24 | 0xCCCCCC);
                }
            String file = "font/hud/indicator_" + step + ".png";
            builder.addData("assets/" + NAMESPACE + "/textures/" + file, PolymerHudIcons.png(image));
            int codepoint = INDICATOR_BASE + step;
            providers.add(provider(file, size / 2, (24 + size / 2) / 2, codepoint));
            advances.put(codepoint, (int) (.5f + actualWidth(image) * .5f) + 1);
        }
        font(builder, "hud/indicator", providers);
        indicatorAdvances = Map.copyOf(advances);
    }

    private static void cursor(ResourcePackBuilder builder) throws IOException {
        int rows = 24 + CURSOR_RANGE + CURSOR_SIZE / 2 + 2;
        rows += rows % 2;
        BufferedImage image = new BufferedImage(CURSOR_SIZE, rows, BufferedImage.TYPE_INT_ARGB);
        int mid = CURSOR_SIZE / 2;
        for (int i = 0; i < CURSOR_SIZE; i++)
            for (int d = -1; d <= 1; d++) {
                if (mid + d >= 0 && mid + d < CURSOR_SIZE) {
                    image.setRGB(i, mid + d, 0xC0000000);
                    image.setRGB(mid + d, i, 0xC0000000);
                }
            }
        for (int i = 1; i < CURSOR_SIZE - 1; i++) {
            image.setRGB(i, mid, 0xFFFFFFFF);
            image.setRGB(mid, i, 0xFFFFFFFF);
        }
        String file = "font/hud/cursor.png";
        builder.addData("assets/" + NAMESPACE + "/textures/" + file, PolymerHudIcons.png(image));
        JsonArray providers = new JsonArray();
        for (int cy = -CURSOR_RANGE; cy <= CURSOR_RANGE; cy += 2) {
            int top = cy - mid;
            providers.add(provider(file, rows / 2, (24 - top) / 2, CURSOR_BASE + (cy + CURSOR_RANGE) / 2));
        }
        font(builder, "hud/cursor", providers);
        cursorAdvance = (int) (.5f + CURSOR_SIZE * .5f) + 1;
    }

    private static void uiIcons(ResourcePackBuilder builder) throws IOException {
        Map<String, BufferedImage> icons = new LinkedHashMap<>();
        icons.put(
                "scroll",
                image(builder, "assets/ponder/textures/gui/widgets.png").getSubimage(16, 64, 16, 16));
        icons.put("up", image(builder, "assets/create/textures/item/ui/gui_radial_property_up.png"));
        icons.put("down", image(builder, "assets/create/textures/item/ui/gui_radial_property_down.png"));
        JsonArray providers = new JsonArray();
        Map<String, Integer> codepoints = new HashMap<>();
        Map<Integer, Integer> advances = new HashMap<>();
        int codepoint = UI_BASE;
        for (var icon : icons.entrySet()) {
            BufferedImage padded = new BufferedImage(16, 72, BufferedImage.TYPE_INT_ARGB);
            padded.getGraphics().drawImage(icon.getValue(), 0, 0, 16, 16, null);
            String file = "font/hud/ui_" + icon.getKey() + ".png";
            builder.addData("assets/" + NAMESPACE + "/textures/" + file, PolymerHudIcons.png(padded));
            for (int top : new int[] {-46, 30}) {
                providers.add(provider(file, 36, (24 - top) / 2, codepoint));
                codepoints.put(icon.getKey() + "@" + top, codepoint);
                advances.put(codepoint, (int) (.5f + actualWidth(padded) * .5f) + 1);
                codepoint++;
            }
        }
        font(builder, "hud/ui", providers);
        uiIcons = Map.copyOf(codepoints);
        uiAdvances = Map.copyOf(advances);
    }

    private static void textFonts(ResourcePackBuilder builder, SortedSet<Integer> tops) throws IOException {
        BufferedImage ascii = image(builder, "assets/minecraft/textures/font/ascii.png");
        JsonArray chars = PolymerArtKit.asciiRows(builder);
        int highest = tops.first(), glyphTopMin = highest - Math.floorMod(highest, 2);
        int cell = ascii.getWidth() / 16, cellRows = Math.max(16, 2 * ((24 - glyphTopMin) / 2) + 2);
        Map<Integer, Integer> widths = new HashMap<>();
        for (int row = 0; row < chars.size(); row++) {
            String line = chars.get(row).getAsString();
            for (int col = 0; col < line.length() && col < 16; col++) {
                char c = line.charAt(col);
                if (c == 0) continue;
                int w = 0;
                for (int y = 0; y < cell; y++)
                    for (int x = 0; x < cell; x++)
                        if (ascii.getRGB(col * cell + x, row * cell + y) >>> 24 != 0) w = Math.max(w, x + 1);
                widths.put((int) c, w * 8 / cell);
            }
        }
        String[] files = new String[2];
        for (int pad = 0; pad < 2; pad++) {
            BufferedImage sheet = new BufferedImage(16 * 8, 16 * cellRows, BufferedImage.TYPE_INT_ARGB);
            for (int row = 0; row < 16; row++)
                for (int col = 0; col < 16; col++)
                    for (int y = 0; y < 8; y++)
                        for (int x = 0; x < 8; x++)
                            sheet.setRGB(
                                    col * 8 + x,
                                    row * cellRows + pad + y,
                                    ascii.getRGB(col * cell + x * cell / 8, row * cell + y * cell / 8));
            files[pad] = "font/hud/ascii_pad" + pad + ".png";
            builder.addData("assets/" + NAMESPACE + "/textures/" + files[pad], PolymerHudIcons.png(sheet));
        }
        Map<Integer, Integer> rows = new HashMap<>();
        int index = 0;
        for (int top : tops) {
            int pad = Math.floorMod(top, 2), glyphTop = top - pad, ascent = (24 - glyphTop) / 2;
            if (ascent > cellRows / 2)
                throw new IOException("HUD text top " + top + ": ascent " + ascent + " > height " + cellRows / 2);
            JsonObject provider = PolymerGuiGlyphs.bitmapProvider(NAMESPACE + ":" + files[pad], cellRows / 2, ascent);
            provider.add("chars", chars.deepCopy());
            JsonArray providers = new JsonArray();
            providers.add(provider);
            font(builder, "hud/txt_" + index, providers);
            rows.put(top, index++);
        }
        textWidths = Map.copyOf(widths);
        textRows = Map.copyOf(rows);
    }

    private static void spaceFont(ResourcePackBuilder builder) {
        JsonArray providers = new JsonArray();
        providers.add(PolymerGuiGlyphs.spaceProvider(SPACE));
        JsonObject json = new JsonObject();
        json.add("providers", providers);
        builder.addStringData("assets/" + NAMESPACE + "/font/hud/space.json", json.toString());
    }

    private static void frames(ResourcePackBuilder builder) throws IOException {
        BufferedImage widgets = image(builder, "assets/create/textures/gui/widgets.png");
        int[][] sprites = {{0, 68, 22}, {27, 98, 22}, {52, 98, 22}, {27, 67, 24}};
        JsonArray providers = new JsonArray();
        Map<Integer, Integer> advances = new HashMap<>();
        for (int kind = 0; kind < 4; kind++) {
            int u = sprites[kind][0], v = sprites[kind][1], size = sprites[kind][2], pad = kind == 3 ? 1 : 0;
            BufferedImage sprite = new BufferedImage(size, FRAME_CELL, BufferedImage.TYPE_INT_ARGB);
            int width = 0;
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++) {
                    int argb = widgets.getRGB(u + x, v + y);
                    sprite.setRGB(x, y + pad, argb);
                    if (argb >>> 24 != 0) width = Math.max(width, x + 1);
                }
            String file = "font/hud/tb_frame_" + kind + ".png";
            builder.addData("assets/" + NAMESPACE + "/textures/" + file, PolymerHudIcons.png(sprite));
            for (int row = 0; row < FRAME_ROWS.length; row++) {
                int cy = FRAME_ROWS[row], top = kind == 3 ? cy - 14 : cy - 12, codepoint = FRAME_BASE + row * 4 + kind;
                JsonObject provider = new JsonObject();
                provider.addProperty("type", "bitmap");
                provider.addProperty("file", NAMESPACE + ":" + file);
                if ((24 - top) / 2 > sprite.getHeight() / 2)
                    throw new IOException("HUD frame row " + cy + ": ascent exceeds height");
                provider.addProperty("height", sprite.getHeight() / 2);
                provider.addProperty("ascent", (24 - top) / 2);
                JsonArray chars = new JsonArray();
                chars.add(Character.toString(codepoint));
                provider.add("chars", chars);
                providers.add(provider);
                advances.put(codepoint, (int) (.5f + width * .5f) + 1);
            }
        }
        JsonObject json = new JsonObject();
        json.add("providers", providers);
        builder.addStringData("assets/" + NAMESPACE + "/font/hud/tb_frames.json", json.toString());
        frameAdvances = Map.copyOf(advances);
    }

    private static void rings(ResourcePackBuilder builder) throws IOException {
        JsonArray providers = new JsonArray();
        Map<Integer, Integer> advances = new HashMap<>();
        for (int n = 2; n <= 8; n++)
            for (int hovered = 0; hovered < n; hovered++) {
                BufferedImage ring = new BufferedImage(RING, RING, BufferedImage.TYPE_INT_ARGB);
                double size = 360.0 / n;
                int width = 0;
                for (int y = 0; y < RING; y++)
                    for (int x = 0; x < RING; x++) {
                        double dx = x + .5 - RING / 2.0, dy = y + .5 - RING / 2.0, r = Math.hypot(dx, dy);
                        if (r < 47 || r >= 113) continue;
                        double deg = (Math.toDegrees(Math.atan2(dx, -dy)) + 360) % 360;
                        int k = (int) (((deg + size / 2) % 360) / size);
                        double edge = (size / 2 - Math.abs(((deg + size / 2) % size) - size / 2)) * Math.PI / 180 * r;
                        double[] inner = {1, 1, 1, .05}, outer = {1, 1, 1, .3};
                        if (k == hovered) {
                            inner = mix(inner, new double[] {.8, .8, .2, .2});
                            outer = mix(outer, new double[] {.8, .8, .2, .6});
                        }
                        double[] c = null;
                        if (r >= 47 && r < 48) c = new double[] {inner[0], inner[1], inner[2], inner[3] * .5};
                        else if (r >= 50 && r < 110) {
                            double t = (r - 50) / 60;
                            c = new double[] {
                                inner[0] + (outer[0] - inner[0]) * t,
                                inner[1] + (outer[1] - inner[1]) * t,
                                inner[2] + (outer[2] - inner[2]) * t,
                                inner[3] + (outer[3] - inner[3]) * t
                            };
                        } else if (k == hovered && r >= 112) c = outer;
                        if (edge < .75 && r >= 45 && r < 110) {
                            double t = Math.min(1, Math.max(0, r < 70 ? (70 - r) / 25 : (r - 70) / 50));
                            c = new double[] {1, 1, 1, .5 + (.15 - .5) * t};
                        }
                        if (c == null) continue;
                        int argb = (int) Math.round(c[3] * 255) << 24
                                | (int) Math.round(c[0] * 255) << 16
                                | (int) Math.round(c[1] * 255) << 8
                                | (int) Math.round(c[2] * 255);
                        ring.setRGB(x, y, argb);
                        if (argb >>> 24 != 0) width = Math.max(width, x + 1);
                    }
                int codepoint = RING_BASE + n * 8 + hovered;
                String file = "font/hud/wr_ring_" + n + "_" + hovered + ".png";
                builder.addData("assets/" + NAMESPACE + "/textures/" + file, PolymerHudIcons.png(ring));
                JsonObject provider =
                        PolymerGuiGlyphs.bitmapProvider(NAMESPACE + ":" + file, RING / 2, (24 + RING / 2) / 2);
                JsonArray chars = new JsonArray();
                chars.add(Character.toString(codepoint));
                provider.add("chars", chars);
                providers.add(provider);
                advances.put(codepoint, (int) (.5f + width * .5f) + 1);
            }
        JsonObject json = new JsonObject();
        json.add("providers", providers);
        builder.addStringData("assets/" + NAMESPACE + "/font/hud/wr_ring.json", json.toString());
        ringAdvances = Map.copyOf(advances);
    }

    private static double[] mix(double[] a, double[] b) {
        double[] out = new double[4];
        for (int i = 0; i < 4; i++) out[i] = a[i] + (b[i] - a[i]) * .5;
        return out;
    }

    private static BufferedImage image(ResourcePackBuilder builder, String path) throws IOException {
        byte[] bytes = builder.getDataOrSource(path);
        if (bytes == null) throw new IOException("Missing " + path);
        BufferedImage read = PngCodec.decode(bytes);
        BufferedImage argb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
        argb.getGraphics().drawImage(read, 0, 0, null);
        return argb;
    }
}
