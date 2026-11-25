package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.axisOf;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.model;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.part;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.progress;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotateToFaceVertical;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.rotationOffset;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.spin;
import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.textPart;

import com.google.common.base.Strings;
import com.zurrtum.create.Create;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.trains.display.FlapDisplayBlock;
import com.zurrtum.create.content.trains.display.FlapDisplayBlockEntity;
import com.zurrtum.create.content.trains.display.FlapDisplayLayout;
import com.zurrtum.create.content.trains.display.FlapDisplaySection;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.Part;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

final class PolymerFlapDisplay {
    private static final Identifier COG = model("polymer/cogwheel_shaftless");
    private static final int DEFAULT_COLOR = 0xD3C6BA;
    private static final Map<FlapDisplaySection, Mirror> MIRRORS = new WeakHashMap<>();
    private static final @Nullable Field CYCLE = field("cycle");
    private static final @Nullable Field RIGHT_ALIGNED = field("rightAligned");

    private PolymerFlapDisplay() {}

    private static final class Mirror {
        final boolean singleFlap;
        final boolean rightAligned;
        final String @Nullable [] cyclingOptions;
        final boolean[] spinning;
        final RandomSource random = RandomSource.create();
        String text;
        int spinningTicks;

        @Nullable
        String last;

        long ticked = Long.MIN_VALUE;

        Mirror(FlapDisplaySection section) {
            singleFlap = !section.renderCharsIndividually();
            rightAligned = flag(RIGHT_ALIGNED, section);
            String cycle = CYCLE == null ? null : (String) get(CYCLE, section);
            cyclingOptions = section.cyclingOptions != null
                    ? section.cyclingOptions
                    : cycle == null ? null : FlapDisplaySection.getFlapCycle(cycle);
            spinning = new boolean[section.spinning.length];
            text = Strings.repeat(" ", spinning.length);
        }

        void refresh(String newText, boolean transition) {
            if (!singleFlap) {
                if (rightAligned) newText = newText.trim();
                newText = newText.toUpperCase(Locale.ROOT);
                newText = newText.substring(0, Math.min(spinning.length, newText.length()));
                String whitespace = Strings.repeat(" ", spinning.length - newText.length());
                newText = rightAligned ? whitespace + newText : newText + whitespace;
                if (!text.isEmpty()) {
                    for (int i = 0; i < spinning.length; i++) {
                        spinning[i] |= transition && text.charAt(i) != newText.charAt(i);
                    }
                }
            } else if (!text.isEmpty() && spinning.length > 0) {
                spinning[0] |= transition && !newText.equals(text);
            }
            text = newText;
            spinningTicks = 0;
        }

        void tick(boolean instant) {
            if (cyclingOptions == null) return;
            int max = Math.max(4, (int) (cyclingOptions.length * 1.75f));
            if (spinningTicks > max) return;
            spinningTicks++;
            if (spinningTicks <= max && spinningTicks < 2) return;
            for (int i = 0; i < spinning.length; i++) {
                int increasingChance = Mth.clamp(8 - spinningTicks, 1, 10);
                boolean continueSpin = !instant && random.nextInt(increasingChance * max / 4) != 0;
                continueSpin &= max > 5 || spinningTicks < 2;
                spinning[i] &= continueSpin;
                if (i > 0 && random.nextInt(3) > 0) spinning[i - 1] &= continueSpin;
                if (i < spinning.length - 1 && random.nextInt(3) > 0) spinning[i + 1] &= continueSpin;
                if (spinningTicks > max) spinning[i] = false;
            }
        }
    }

    static void collect(FlapDisplayBlockEntity be, BlockState state, float time, List<Part> out) {
        Direction facing = state.getValue(FlapDisplayBlock.HORIZONTAL_FACING);
        Axis axis = axisOf(state);
        float angle = progress(be.getSpeed(), time) + rotationOffset(state, axis, be.getBlockPos());
        out.add(part(COG, spin(axis, angle).mul(rotateToFaceVertical(facing))).identified("flap:cog"));
        if (!be.isController) return;

        long gameTime = (long) time;
        boolean paused = !be.isSpeedRequirementFulfilled();
        boolean instant = Math.abs(be.getSpeed()) > 128;
        int levelTicks = (int) gameTime;
        int ticks = paused ? 0 : levelTicks;
        float renderTime = paused ? 0 : time;

        Matrix4f base = new Matrix4f()
                .translate(0.5f, 0.5f, 0.5f)
                .rotateY(Mth.DEG_TO_RAD * AngleHelper.horizontalAngle(facing))
                .translate(-0.5f, -0.5f, -0.5f)
                .translate(0, 1.0f, 0.8125f)
                .scale(1 / 32f, -1 / 32f, 1 / 32f)
                .translate(0, 0, 0.5f);

        List<FlapDisplayLayout> lines = be.getLines();
        float y = 4.5f;
        for (int j = 0; j < lines.size(); j++) {
            List<FlapDisplaySection> sections = lines.get(j).getSections();
            int color = lineColor(be, j);
            int dimColor = (color >> 16 & 255) * 0xC0 >> 8 << 16
                    | (color >> 8 & 255) * 0xC0 >> 8 << 8
                    | (color & 255) * 0xC0 >> 8;
            float w = 0;
            int count = sections.size();
            float[] offsets = new float[count];
            for (int i = 0; i < count; i++) {
                FlapDisplaySection section = sections.get(i);
                offsets[i] = w;
                w += section.getSize() + (section.hasGap ? 8 : 1);
            }
            float margin = be.xSize * 16 - w / 2 + 1;
            int first = out.size();
            for (int i = 0; i < count; i++) {
                FlapDisplaySection section = sections.get(i);
                Mirror mirror = mirror(section, gameTime, !paused, instant);
                float x = margin + offsets[i];
                String key = "flap:" + j + ":" + i + ":";
                if (section.renderCharsIndividually()) {
                    individual(mirror, section, base, x, y, j, renderTime, paused, color, dimColor, key, out);
                } else {
                    single(mirror, i, base, x, y, ticks, levelTicks, color, dimColor, key, out);
                }
            }
            if (be.isLineGlowing(j))
                for (int k = first; k < out.size(); k++) out.set(k, out.get(k).glowing());
            y += 16;
        }
    }

    private static Mirror mirror(FlapDisplaySection section, long gameTime, boolean running, boolean instant) {
        Mirror mirror = MIRRORS.get(section);
        boolean fresh = mirror == null || mirror.spinning.length != section.spinning.length;
        if (fresh) {
            mirror = new Mirror(section);
            MIRRORS.put(section, mirror);
        }
        Component component = section.getText();
        String current = component == null ? null : component.getString();
        if (current != null && !current.equals(mirror.last)) {
            mirror.refresh(current, !fresh && mirror.last != null);
            mirror.last = current;
        }
        if (mirror.ticked != gameTime) {
            mirror.ticked = gameTime;
            if (running) mirror.tick(instant);
        }
        return mirror;
    }

    private static void individual(
            Mirror mirror,
            FlapDisplaySection section,
            Matrix4f base,
            float x,
            float y,
            int line,
            float time,
            boolean paused,
            int color,
            int dimColor,
            String key,
            List<Part> out) {
        float standardWidth = section.wideFlaps ? FlapDisplaySection.WIDE_MONOSPACE : FlapDisplaySection.MONOSPACE;
        String text = mirror.text;
        for (int c = 0; c < text.length(); c++) {
            char glyph = text.charAt(c);
            boolean dim = false;
            if (mirror.cyclingOptions != null
                    && mirror.cyclingOptions.length > 0
                    && mirror.spinning[Math.min(c, mirror.spinning.length - 1)]) {
                float speed = mirror.spinningTicks > 5 && mirror.spinningTicks < 20 ? 1.75f : 2.5f;
                float cycle = time / speed + c * 16.83f + line * 0.75f;
                float partial = cycle % 1;
                char cyclingGlyph = mirror.cyclingOptions[(int) cycle % mirror.cyclingOptions.length].charAt(0);
                glyph = paused ? cyclingGlyph : partial > 0.5f ? partial > 0.75f ? '_' : '-' : cyclingGlyph;
                dim = true;
            }
            if (glyph != ' ') {
                Matrix4f pose = new Matrix4f(base).translate(x + c * standardWidth + standardWidth / 2 - 1, y + 9, 0);
                out.add(textPart(Component.literal(String.valueOf(glyph)).withColor(dim ? dimColor : color), pose)
                        .identified(key + c));
            }
        }
    }

    private static void single(
            Mirror mirror,
            int index,
            Matrix4f base,
            float x,
            float y,
            int ticks,
            int levelTicks,
            int color,
            int dimColor,
            String key,
            List<Part> out) {
        boolean spinning = mirror.spinning.length > 0
                && mirror.spinning[0]
                && mirror.cyclingOptions != null
                && mirror.cyclingOptions.length > 0;
        String text = spinning
                ? mirror.cyclingOptions[(levelTicks / 3 + index * 13) % mirror.cyclingOptions.length]
                : mirror.text;
        if (text == null || text.isBlank()) return;
        boolean dim = false;
        if (spinning) {
            int phase = ticks % 3;
            StringBuilder flipped = new StringBuilder(text.length());
            for (int k = 0; k < text.length(); k++) {
                char glyph = text.charAt(k);
                int width = advance(glyph);
                if (phase == 0) glyph = width == 6 ? '-' : width == 1 ? '\'' : glyph;
                else if (phase == 2) glyph = width == 6 ? '_' : width == 1 ? '.' : glyph;
                flipped.append(glyph);
            }
            text = flipped.toString();
            dim = phase != 1;
        }
        float width = 0;
        for (int k = 0; k < text.length(); k++) width += advance(text.charAt(k));
        Matrix4f pose = new Matrix4f(base).translate(x + width / 2 - 1, y + 9, 0);
        out.add(textPart(Component.literal(text).withColor(dim ? dimColor : color), pose)
                .identified(key + "s"));
    }

    private static int advance(char c) {
        return switch (c) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    private static int lineColor(FlapDisplayBlockEntity be, int line) {
        DyeColor color = be.colour[line];
        return color == null ? DEFAULT_COLOR : PolymerTextParts.brightColor(color);
    }

    private static @Nullable Field field(String name) {
        try {
            Field field = FlapDisplaySection.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Create.LOGGER.warn("Flap display presentation cannot read FlapDisplaySection.{}", name, e);
            return null;
        }
    }

    private static @Nullable Object get(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static boolean flag(@Nullable Field field, Object owner) {
        return field != null && get(field, owner) instanceof Boolean value && value;
    }
}
