package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerArtKit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class PolymerPonderScene {
    enum Fade {
        UP(0, 1, 0),
        DOWN(0, -1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0),
        NONE(0, 0, 0);
        final int x, y, z;

        Fade(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    record Spinner(
            String block, String model, List<String> textures, boolean cogOffset, boolean housed, boolean alongZ) {
        Spinner(String block, String model, List<String> textures, boolean cogOffset, boolean housed) {
            this(block, model, textures, cogOffset, housed, false);
        }
    }

    static final List<Spinner> SPINNERS = List.of(
            new Spinner("create:cogwheel", "create:block/cogwheel", List.of("0", "3", "1_2"), true, false),
            new Spinner("create:shaft", "create:block/shaft", List.of("0", "1"), false, false),
            new Spinner("create:large_cogwheel", "create:block/large_cogwheel", List.of("0", "3", "4"), false, false),
            new Spinner("create:andesite_encased_shaft", "create:block/shaft", List.of("0", "1"), false, true),
            new Spinner("create:brass_encased_shaft", "create:block/shaft", List.of("0", "1"), false, true),
            new Spinner("create:belt", "create:block/belt_pulley", List.of("0", "1", "2", "3"), false, true),
            new Spinner("create:mechanical_bearing", "create:block/shaft_half", List.of("0", "1"), false, true, true),
            new Spinner("create:windmill_bearing", "create:block/shaft_half", List.of("0", "1"), false, true, true),
            new Spinner("create:clockwork_bearing", "create:block/shaft_half", List.of("0", "1"), false, true, true),
            new Spinner("create:rope_pulley", "create:block/shaft", List.of("0", "1"), false, true),
            new Spinner("create:elevator_pulley", "create:block/shaft", List.of("0", "1"), false, true));

    static final int SLIDE = 15;

    record Part(
            int x,
            int y,
            int z,
            List<ModelRef> models,
            int appear,
            Fade fade,
            Spinner spinner,
            int dir,
            int ticksPerPose,
            boolean offset,
            int activeFrom,
            int activeUntil,
            int disappear,
            Fade exitFade,
            ModelRef axle) {}

    record ModelRef(String model, int rx, int ry, int rz, String track) {
        ModelRef(String model, int rx, int ry) {
            this(model, rx, ry, 0, null);
        }

        ModelRef(String model, int rx, int ry, int rz) {
            this(model, rx, ry, rz, null);
        }
    }

    record Point(float x, float y, float z) {}

    record Motion(String section, int start, int duration, Point delta, boolean rotate) {}

    record Assignment(int start, Set<String> positions, String section) {}

    static String position(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    record Caption(int start, int end, List<String> lines, Point target) {}

    record Outline(
            int start, int duration, Point centre, Point expand, Fade side, int color, float lineWidth, boolean chase) {
        Outline(int start, int duration, Point centre, Point expand, Fade side, int color, float lineWidth) {
            this(start, duration, centre, expand, side, color, lineWidth, true);
        }
    }

    static int firstOutlineState(Outline outline) {
        return outline.chase ? 0 : 5;
    }

    record Block(int x, int y, int z, String name, Map<String, String> properties, float speed, String dye) {
        Block(int x, int y, int z, String name, Map<String, String> properties, float speed) {
            this(x, y, z, name, properties, speed, null);
        }
    }

    final String id, item, title;
    final List<Part> parts;
    final List<Caption> captions;
    final List<Outline> outlines;
    final List<Motion> motions;
    final List<Assignment> assignments;
    final Map<String, Point> pivots;
    final Map<String, Float> pulleys;
    final Map<Integer, List<PolymerPonderItems.Draw>> items;
    private final TreeSet<Integer> itemChanges = new TreeSet<>();

    final boolean nextUpEnabled;
    final int length, renderLength;
    final int plateX, plateZ, plateSize;
    final float scale, skirt;
    final int[] keyframes, phases, frames;
    final int[][] seekZones;
    final float[] yaws;
    private final int[] viewAt;
    private volatile Map<Part, Integer> partIndex;

    static final float PITCH = 35, START_YAW = 145;
    private static final double[] TURN_STEPS = {.12, .35, .65, .88};

    private PolymerPonderScene(
            String id,
            String item,
            String title,
            List<Part> parts,
            List<Caption> captions,
            List<Outline> outlines,
            int length,
            int renderLength,
            int[] keyframes,
            int plateX,
            int plateZ,
            int plateSize,
            List<Motion> motions,
            List<Assignment> assignments,
            Map<String, Point> pivots,
            Map<String, Float> pulleys,
            Map<Integer, List<PolymerPonderItems.Draw>> items,
            boolean nextUpEnabled,
            List<float[]> cameraTurns) {
        this.id = id;
        this.item = item;
        this.title = title;
        this.parts = parts;
        this.captions = captions;
        this.outlines = outlines;
        this.length = length;
        this.renderLength = renderLength;
        this.keyframes = keyframes;
        this.plateX = plateX;
        this.plateZ = plateZ;
        this.plateSize = plateSize;
        this.motions = motions;
        this.assignments = assignments;
        this.pivots = pivots;
        this.pulleys = pulleys;
        this.items = new HashMap<>(items);
        this.nextUpEnabled = nextUpEnabled;
        List<Float> views = new ArrayList<>(List.of(START_YAW));
        double[] yaw = new double[renderLength + 1];
        double value = START_YAW, target = START_YAW;
        for (int tick = 0; tick <= renderLength; tick++) {
            if (tick > 0) value += (target - value) * .1;
            for (float[] turn : cameraTurns) {
                if ((int) turn[0] != tick) continue;
                double start = value;
                target += turn[1];
                for (double step : TURN_STEPS) addView(views, start + (target - start) * step);
                addView(views, target);
            }
            yaw[tick] = value;
        }
        int[] nearest = new int[renderLength + 1];
        boolean[] used = new boolean[views.size()];
        used[0] = true;
        for (int tick = 0; tick <= renderLength; tick++) {
            int best = 0;
            for (int v = 1; v < views.size(); v++)
                if (angleBetween(yaw[tick], views.get(v)) < angleBetween(yaw[tick], views.get(best))) best = v;
            nearest[tick] = best;
            used[best] = true;
        }
        int[] index = new int[views.size()];
        List<Float> kept = new ArrayList<>();
        for (int v = 0; v < views.size(); v++)
            if (used[v]) {
                index[v] = kept.size();
                kept.add(views.get(v));
            }
        yaws = new float[kept.size()];
        for (int i = 0; i < yaws.length; i++) yaws[i] = kept.get(i);
        viewAt = new int[renderLength + 1];
        for (int tick = 0; tick <= renderLength; tick++) viewAt[tick] = index[nearest[tick]];
        for (int tick = 0; tick <= renderLength; tick++)
            if (!itemsAt(tick).equals(itemsAt(tick - 1))) itemChanges.add(tick);
        phases = IntStream.concat(
                        motions.stream().flatMapToInt(m -> IntStream.of(m.start, m.start + m.duration)),
                        parts.stream().flatMapToInt(p -> IntStream.of(p.appear, p.activeFrom, p.activeUntil)))
                .filter(t -> t >= 0 && t <= renderLength)
                .distinct()
                .sorted()
                .toArray();
        frames = IntStream.rangeClosed(0, renderLength)
                .map(this::frame)
                .filter(f -> f >= 0)
                .distinct()
                .toArray();
        seekZones = seekZones();
        scale = fitScale();
        skirt = skirtLength();
    }

    private static void addView(List<Float> views, double yaw) {
        for (float view : views) if (angleBetween(view, yaw) < .5) return;
        views.add((float) yaw);
    }

    private static double angleBetween(double a, double b) {
        double d = Math.abs((a - b) % 360);
        return Math.min(d, 360 - d);
    }

    int views() {
        return yaws.length;
    }

    int viewAt(int tick) {
        return viewAt[Math.clamp(tick, 0, renderLength)];
    }

    List<PolymerPonderItems.Draw> itemsAt(int tick) {
        return items.getOrDefault(tick, List.of());
    }

    boolean sliding(int tick) {
        for (var motion : motions) if (motion.start <= tick && tick < motion.start + motion.duration) return true;
        for (Part part : parts)
            if (part.appear <= tick && tick < part.appear + SLIDE
                    || part.disappear <= tick && tick < part.disappear + SLIDE) return true;
        return false;
    }

    private boolean itemMoving(int tick) {
        return itemChanges.contains(tick) && (itemChanges.contains(tick - 1) || itemChanges.contains(tick + 1));
    }

    int itemTick(int tick) {
        if (items.isEmpty()) return -1;
        int even = tick & ~1;
        if (itemMoving(even) || itemMoving(even + 1)) return Math.min(renderLength, even + 1);
        Integer last = itemChanges.floor(tick);
        return last == null || itemsAt(last).isEmpty() ? -1 : last;
    }

    static List<String> stackStrings(String flash, int itemTick, String key) {
        return List.of(flash, "i" + itemTick, key);
    }

    int frame(int tick) {
        return sliding(tick) ? tick & ~1 : -1;
    }

    int phase(int tick) {
        int phase = 0;
        for (int i = 0; i < phases.length; i++) if (phases[i] <= tick) phase = i;
        return phase;
    }

    int caption(int tick) {
        int shown = 0;
        for (int i = 0; i < captions.size(); i++)
            if (captions.get(i).start <= tick && tick < captions.get(i).end) shown = i + 1;
        return shown;
    }

    static final float[] FADES = {0, .16f, .36f, .64f, 1};

    int captionFade(int tick) {
        return captionFade(caption(tick), tick);
    }

    int captionFade(int shown, int tick) {
        if (shown == 0) return 0;
        Caption caption = captions.get(shown - 1);
        if (tick < caption.start || tick >= caption.end) return 0;
        int r = tick - caption.start + 1, remaining = caption.end - caption.start - r;
        return Math.max(0, Math.min(Math.min(r, remaining), 5) - 1);
    }

    static final int OUTLINE_STATES = 10;

    static float outlineGrowth(int state) {
        return state >= 5 ? 1 : 1 - (float) Math.pow(.5, state);
    }

    static float outlineAlpha(int state) {
        float alpha = state < 6 ? 1 : 1 - (state - 5) / 8f;
        return alpha * alpha * alpha;
    }

    static int outlineState(Outline outline, int tick) {
        int r = tick - outline.start + 1;
        if (r < 1) return -1;
        if (r <= outline.duration) return outline.chase ? Math.min(r - 1, 5) : 5;
        int fade = r - outline.duration - 1;
        return fade == 0 ? 5 : fade <= 4 ? 5 + fade : -1;
    }

    boolean facing(Outline outline, int view) {
        if (outline.side == Fade.NONE) return true;
        var normal = viewTransform(view, scale, PolymerPonderLayout.SCENE_WIDTH, PolymerPonderLayout.SCENE_HEIGHT)
                .transformDirection(new Vector3f(outline.side.x, outline.side.y, outline.side.z));
        return normal.z > 0;
    }

    String sceneId(int tick, int view) {
        return "ponder_" + id + "_v" + view;
    }

    final Map<String, String> aliases = new ConcurrentHashMap<>();

    private int[][] seekZones() {
        List<int[]> zones = new ArrayList<>();
        int from = 0, target = 0;
        for (int frame : keyframes) {
            if (frame == 0) continue;
            zones.add(new int[] {from, frame, target});
            from = frame;
            target = frame;
        }
        int end = (2 * length + from + 2) / 3;
        zones.add(new int[] {from, end, target});
        zones.add(new int[] {end, length + 1, length});
        return zones.toArray(int[][]::new);
    }

    static Builder builder(String id, String item, String title, String schematic) {
        return new Builder(id, item, title, schematic);
    }

    static final class Select {
        private final List<Block> blocks;

        Select(List<Block> blocks) {
            this.blocks = blocks;
        }

        List<Block> layer(int y) {
            return blocks.stream().filter(b -> b.y == y).toList();
        }

        List<Block> position(int x, int y, int z) {
            return fromTo(x, y, z, x, y, z);
        }

        List<Block> fromTo(int x1, int y1, int z1, int x2, int y2, int z2) {
            return blocks.stream()
                    .filter(b -> b.x >= Math.min(x1, x2)
                            && b.x <= Math.max(x1, x2)
                            && b.y >= Math.min(y1, y2)
                            && b.y <= Math.max(y1, y2)
                            && b.z >= Math.min(z1, z2)
                            && b.z <= Math.max(z1, z2))
                    .toList();
        }

        List<Block> everywhere() {
            return blocks;
        }
    }

    static final class Builder {
        final String id, item, title, schematic;
        private final List<Step> steps = new ArrayList<>();
        private final Map<String, String> modelOverrides = new LinkedHashMap<>();
        private final List<SpeedChange> speeds = new ArrayList<>();
        private final List<Visibility> visibility = new ArrayList<>();

        private record Visibility(int time, Function<Select, List<Block>> selection, Fade fade, boolean show) {}

        record Span(int start, Fade fade, int hide, Fade exitFade) {}

        private int time;
        private Integer finishedAt;
        private boolean nextUpEnabled = true;

        private record Section(int time, String id, Function<Select, List<Block>> selection, boolean merge) {
            Section(int time, String id, Function<Select, List<Block>> selection) {
                this(time, id, selection, false);
            }
        }

        private final List<Section> sections = new ArrayList<>();
        private final List<Motion> motions = new ArrayList<>();
        private final Map<String, Point> pivots = new LinkedHashMap<>();

        private record Step(
                int time,
                Function<Select, List<Block>> selection,
                Fade fade,
                int duration,
                String text,
                boolean keyframe,
                Point target) {}

        private final List<Outline> outlines = new ArrayList<>();
        private int plateX, plateZ, plateSize = 5;

        Builder configureBasePlate(int xOffset, int zOffset, int size) {
            plateX = xOffset;
            plateZ = zOffset;
            plateSize = size;
            return this;
        }

        private record SpeedChange(
                int time,
                Function<Select, List<Block>> selection,
                float value,
                boolean multiply,
                UnaryOperator<Block> stateChange,
                boolean restore) {
            SpeedChange(
                    int time,
                    Function<Select, List<Block>> selection,
                    float value,
                    boolean multiply,
                    UnaryOperator<Block> stateChange) {
                this(time, selection, value, multiply, stateChange, false);
            }
        }

        private static UnaryOperator<Block> unlessAir(UnaryOperator<Block> change) {
            return b -> b.name.equals("minecraft:air") ? b : change.apply(b);
        }

        Builder speed(Function<Select, List<Block>> selection, float speed) {
            speeds.add(new SpeedChange(time, selection, speed, false, null));
            return this;
        }

        Builder multiplySpeed(Function<Select, List<Block>> selection, float factor) {
            speeds.add(new SpeedChange(time, selection, factor, true, null));
            return this;
        }

        Builder setBlocks(Function<Select, List<Block>> selection, String name, String... properties) {
            Map<String, String> state = new LinkedHashMap<>(PolymerPonderBlockModels.defaultState(name));
            for (String property : properties) {
                String[] kv = property.split("=", 2);
                state.put(kv[0], kv[1]);
            }
            speeds.add(new SpeedChange(
                    time,
                    selection,
                    0,
                    false,
                    b -> new Block(
                            b.x, b.y, b.z, name, derived(state, b), b.speed, name.equals(b.name) ? b.dye : null)));
            return this;
        }

        Builder replaceBlocks(Function<Select, List<Block>> selection, String name, String... properties) {
            Map<String, String> state = new LinkedHashMap<>(PolymerPonderBlockModels.defaultState(name));
            for (String property : properties) {
                String[] kv = property.split("=", 2);
                state.put(kv[0], kv[1]);
            }
            speeds.add(new SpeedChange(
                    time,
                    selection,
                    0,
                    false,
                    unlessAir(b -> new Block(
                            b.x, b.y, b.z, name, derived(state, b), b.speed, name.equals(b.name) ? b.dye : null))));
            return this;
        }

        Builder dyeBelts(Function<Select, List<Block>> selection, String color) {
            speeds.add(new SpeedChange(
                    time,
                    selection,
                    0,
                    false,
                    b -> b.name.equals(PolymerPonderBlockModels.BELT)
                            ? new Block(b.x, b.y, b.z, b.name, b.properties, b.speed, color)
                            : b));
            return this;
        }

        Builder restoreBlocks(Function<Select, List<Block>> selection) {
            speeds.add(new SpeedChange(time, selection, 0, false, null, true));
            return this;
        }

        Builder modifyBlocks(Function<Select, List<Block>> selection, String... properties) {
            Map<String, String> patch = new LinkedHashMap<>();
            for (String property : properties) {
                String[] kv = property.split("=", 2);
                if (kv.length != 2) throw new IllegalArgumentException("Invalid block property: " + property);
                patch.put(kv[0], kv[1]);
            }
            speeds.add(new SpeedChange(time, selection, 0, false, unlessAir(b -> {
                Map<String, String> state = new LinkedHashMap<>(b.properties);
                for (var entry : patch.entrySet())
                    if (state.containsKey(entry.getKey())) state.put(entry.getKey(), entry.getValue());
                return new Block(b.x, b.y, b.z, b.name, derived(state, b), b.speed, b.dye);
            })));
            return this;
        }

        private static Map<String, String> derived(Map<String, String> state, Block current) {
            Map<String, String> out = new LinkedHashMap<>(state);
            for (var entry : state.entrySet()) {
                if (!entry.getValue().startsWith("@")) continue;
                String[] parts = entry.getValue().substring(1).split(":");
                String value = current.properties.get(parts[1]);
                if (value == null)
                    throw new IllegalArgumentException(
                            "No " + parts[1] + " on " + current.name + " for " + entry.getValue());
                out.put(
                        entry.getKey(),
                        switch (parts[0]) {
                            case "copy" -> value;
                            case "opposite" ->
                                Direction.valueOf(value.toUpperCase(Locale.ROOT))
                                        .getOpposite()
                                        .getSerializedName();
                            case "add" -> Integer.toString(Integer.parseInt(value) + Integer.parseInt(parts[2]));
                            default ->
                                throw new IllegalArgumentException(
                                        "Unknown derived property value " + entry.getValue());
                        });
            }
            return Map.copyOf(out);
        }

        Builder toggleRedstonePower(Function<Select, List<Block>> selection) {
            speeds.add(new SpeedChange(time, selection, 0, false, b -> {
                Map<String, String> state = new LinkedHashMap<>(b.properties);
                if (state.containsKey("power"))
                    state.put("power", state.get("power").equals("0") ? "15" : "0");
                for (String key : List.of("powered", "lit"))
                    if (state.containsKey(key)) state.put(key, Boolean.toString(!Boolean.parseBoolean(state.get(key))));
                return new Block(b.x, b.y, b.z, b.name, Map.copyOf(state), b.speed, b.dye);
            }));
            return this;
        }

        Builder hideSection(Function<Select, List<Block>> selection, Fade fade) {
            visibility.add(new Visibility(time, selection, fade, false));
            return this;
        }

        private Builder(String id, String item, String title, String schematic) {
            this.id = id;
            this.item = item;
            this.title = title;
            this.schematic = schematic;
        }

        Builder showSection(Function<Select, List<Block>> selection, Fade fade) {
            visibility.add(new Visibility(time, selection, fade, true));
            sections.add(new Section(time, "", selection));
            return this;
        }

        Builder mergeSection(String id, Function<Select, List<Block>> selection, Fade fade) {
            visibility.add(new Visibility(time, selection, fade, true));
            sections.add(new Section(time, id, selection, true));
            return this;
        }

        Builder section(String id, Function<Select, List<Block>> selection) {
            sections.add(new Section(time, id, selection));
            return this;
        }

        Builder moveSection(String id, float x, float y, float z, int duration) {
            motions.add(new Motion(id, time, duration, new Point(x, y, z), false));
            return this;
        }

        Builder rotateSection(String id, float x, float y, float z, int duration) {
            motions.add(new Motion(id, time, duration, new Point(x, y, z), true));
            return this;
        }

        Builder rotateBearing(int x, int y, int z, float angle, int duration) {
            bearingTurns.add(new BearingTurn(time, x, y, z, angle, duration));
            return this;
        }

        private record BearingTurn(int time, int x, int y, int z, float angle, int duration) {}

        private final List<BearingTurn> bearingTurns = new ArrayList<>();

        Builder movePulley(int x, int y, int z, float distance, int duration) {
            pulleyMoves.add(new BearingTurn(time, x, y, z, distance, duration));
            return this;
        }

        private final List<BearingTurn> pulleyMoves = new ArrayList<>();

        record Breaking(int time, int x, int y, int z, int stage) {}

        private final List<Breaking> breaking = new ArrayList<>();

        Builder breaking(int x, int y, int z, int stage) {
            breaking.add(new Breaking(time, x, y, z, stage));
            return this;
        }

        record Simulation(int time, String json) {}

        private final List<Simulation> simulation = new ArrayList<>();

        Builder simulate(String json) {
            simulation.add(new Simulation(time, json));
            return this;
        }

        Builder centerOfRotation(String id, float x, float y, float z) {
            pivots.put(id, new Point(x, y, z));
            return this;
        }

        Builder stabilize(String id, float x, float y, float z) {
            pivots.put(id + STABILIZED, new Point(x, y, z));
            return this;
        }

        int lastTime() {
            return time;
        }

        Builder markAsFinished() {
            if (finishedAt == null) finishedAt = time;
            return this;
        }

        private final List<float[]> cameraTurns = new ArrayList<>();

        Builder rotateCamera(float degrees) {
            cameraTurns.add(new float[] {time, degrees});
            return this;
        }

        Builder nextUpEnabled(boolean enabled) {
            nextUpEnabled = enabled;
            return this;
        }

        Builder cycleBlockProperty(Function<Select, List<Block>> selection, String property) {
            speeds.add(new SpeedChange(time, selection, 0, false, b -> {
                Map<String, String> state = new LinkedHashMap<>(b.properties);
                if (state.containsKey(property)) {
                    String value = state.get(property);
                    if (value.equals("true") || value.equals("false"))
                        state.put(property, Boolean.toString(!Boolean.parseBoolean(value)));
                    else {
                        List<String> order = PolymerPonderBlockModels.valueOrder(b.name, property);
                        int index = order.indexOf(value);
                        if (index < 0)
                            throw new IllegalArgumentException("Uncaptured value order: " + b.name + " " + property);
                        state.put(property, order.get((index + 1) % order.size()));
                    }
                }
                return new Block(b.x, b.y, b.z, b.name, Map.copyOf(state), b.speed, b.dye);
            }));
            return this;
        }

        Builder idle(int ticks) {
            time += ticks;
            return this;
        }

        Builder text(int duration, String text) {
            steps.add(new Step(time, null, null, duration, text, false, null));
            return this;
        }

        Builder pointAt(float x, float y, float z) {
            if (steps.isEmpty() || steps.getLast().text == null || steps.getLast().time != time)
                throw new IllegalStateException("pointAt requires the current caption");
            Step last = steps.removeLast();
            steps.add(new Step(last.time, null, null, last.duration, last.text, last.keyframe, new Point(x, y, z)));
            return this;
        }

        Builder showFilterSlotInput(float x, float y, float z, int duration) {
            outlines.add(new Outline(
                    time, duration, new Point(x, y, z), new Point(.1f, .1f, .1f), Fade.NONE, 0xEEEEEE, 1 / 15f));
            return this;
        }

        Builder showFilterSlotInput(float x, float y, float z, Fade side, int duration) {
            float e = 11 / 128f, back = -3 / 128f;
            outlines.add(new Outline(
                    time,
                    duration,
                    new Point(x + side.x * back, y + side.y * back, z + side.z * back),
                    new Point(side.x == 0 ? e : 0, side.y == 0 ? e : 0, side.z == 0 ? e : 0),
                    side,
                    0xEEEEEE,
                    1 / 15f));
            return this;
        }

        Builder outlineCaption(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
            if (steps.isEmpty() || steps.getLast().text == null || steps.getLast().time != time)
                throw new IllegalStateException("outlineCaption requires the current caption");
            outlines.add(new Outline(
                    time,
                    steps.getLast().duration + 10,
                    new Point((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2),
                    new Point(Math.abs(x1 - x0) / 2, Math.abs(y1 - y0) / 2, Math.abs(z1 - z0) / 2),
                    Fade.NONE,
                    color,
                    1 / 16f,
                    false));
            return this;
        }

        Builder outline(float x0, float y0, float z0, float x1, float y1, float z1, int color, int duration) {
            outlines.add(new Outline(
                    time,
                    duration,
                    new Point((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2),
                    new Point(Math.abs(x1 - x0) / 2, Math.abs(y1 - y0) / 2, Math.abs(z1 - z0) / 2),
                    Fade.NONE,
                    color,
                    1 / 16f,
                    false));
            return this;
        }

        Builder keyframe(int delay) {
            steps.add(new Step(time + delay, null, null, 0, null, true, null));
            return this;
        }

        Builder keyframe() {
            if (!steps.isEmpty() && steps.getLast().text != null && steps.getLast().time == time) {
                Step last = steps.removeLast();
                steps.add(new Step(last.time, null, null, last.duration, last.text, true, last.target));
            } else steps.add(new Step(time, null, null, 0, null, true, null));
            return this;
        }

        Builder model(String block, String model) {
            modelOverrides.put(block, model);
            return this;
        }

        Definition build() {
            return new Definition(this);
        }
    }

    static final class Definition {
        final Builder builder;
        private volatile PolymerPonderScene compiled;

        Definition(Builder builder) {
            this.builder = builder;
        }

        String id() {
            return builder.id;
        }

        PolymerPonderScene compile(Function<String, byte[]> resources) throws IOException {
            PolymerPonderScene result = compiled;
            if (result != null) return result;
            List<Block> blocks = PolymerPonderBlockModels.schematic(resources, builder.schematic);
            Select select = new Select(blocks);
            List<Caption> captions = new ArrayList<>();
            List<Integer> keyframes = new ArrayList<>(List.of(0));
            for (Builder.Step step : builder.steps) {
                if (step.text != null)
                    captions.add(new Caption(step.time, step.time + step.duration + 10, wrap(step.text), step.target));
                if (step.keyframe && !keyframes.contains(step.time)) keyframes.add(step.time);
            }
            List<Part> parts = new ArrayList<>();
            for (Segment segment : segments(blocks, select, false))
                addParts(parts, resources, segment.state, segment.speed, segment.from, segment.until, segment.spans);
            List<Assignment> assignments = new ArrayList<>();
            Map<String, Point> pivots = new LinkedHashMap<>();
            List<Motion> motions = new ArrayList<>(builder.motions);
            for (var section : builder.sections) {
                var selected = section.selection.apply(select);
                if (selected.isEmpty()) {
                    if (section.id.isEmpty() || section.merge) continue;
                    throw new IOException("Empty independent section: " + section.id);
                }
                var positions =
                        selected.stream().map(b -> position(b.x, b.y, b.z)).collect(Collectors.toUnmodifiableSet());
                if (section.merge) {
                    String held = section.id + "#merge" + assignments.size();
                    assignments.add(new Assignment(section.time, positions, held));
                    pivots.put(held, new Point(0, 0, 0));
                    motions.add(new Motion(
                            held, section.time, 0, offset(builder.motions, section.id, section.time), false));
                    assignments.add(new Assignment(section.time + SLIDE, positions, section.id));
                    continue;
                }
                assignments.add(new Assignment(section.time, positions, section.id));
                pivots.put(
                        section.id,
                        new Point(
                                (selected.stream().mapToInt(Block::x).min().orElseThrow()
                                                + selected.stream()
                                                        .mapToInt(Block::x)
                                                        .max()
                                                        .orElseThrow()
                                                + 1)
                                        / 2f,
                                (selected.stream().mapToInt(Block::y).min().orElseThrow()
                                                + selected.stream()
                                                        .mapToInt(Block::y)
                                                        .max()
                                                        .orElseThrow()
                                                + 1)
                                        / 2f,
                                (selected.stream().mapToInt(Block::z).min().orElseThrow()
                                                + selected.stream()
                                                        .mapToInt(Block::z)
                                                        .max()
                                                        .orElseThrow()
                                                + 1)
                                        / 2f));
            }
            pivots.putAll(builder.pivots);
            for (var turn : builder.bearingTurns) {
                Block bearing = blocks.stream()
                        .filter(b -> b.x == turn.x && b.y == turn.y && b.z == turn.z)
                        .findFirst()
                        .orElse(null);
                if (bearing == null || !PolymerPonderBlockModels.isBearing(bearing))
                    throw new IOException("rotateBearing without a bearing at " + position(turn.x, turn.y, turn.z));
                String axis = switch (bearing.properties.getOrDefault("facing", "up")) {
                    case "east", "west" -> "x";
                    case "north", "south" -> "z";
                    default -> "y";
                };
                Point delta = new Point(
                        axis.equals("x") ? turn.angle : 0,
                        axis.equals("y") ? turn.angle : 0,
                        axis.equals("z") ? turn.angle : 0);
                String track = PolymerPonderBlockModels.bearingTrack(bearing);
                motions.add(new Motion(track, turn.time, turn.duration, delta, true));
                pivots.put(track, new Point(turn.x + .5f, turn.y + .5f, turn.z + .5f));
            }
            Map<String, Float> pulleys = new LinkedHashMap<>();
            for (Block block : blocks) if (isPulley(block.name)) pulleys.put(position(block.x, block.y, block.z), 0f);
            pulleys.putAll(
                    PolymerPonderBlockModels.blockEntityFloats(resources, builder.schematic, ROPE_PULLEY, "Offset"));
            pulleys.putAll(PolymerPonderBlockModels.blockEntityFloats(
                    resources, builder.schematic, ELEVATOR_PULLEY, "Offset"));
            for (var move : builder.pulleyMoves) {
                String key = position(move.x, move.y, move.z);
                if (!pulleys.containsKey(key))
                    throw new IOException("movePulley without a rope or elevator pulley at " + key);
                motions.add(new Motion("pulley@" + key, move.time, move.duration, new Point(0, move.angle, 0), false));
            }
            assignments.sort(Comparator.comparingInt(Assignment::start));
            int length = builder.finishedAt == null ? builder.time : builder.finishedAt;
            int renderLength = Math.max(
                    builder.time, captions.stream().mapToInt(Caption::end).max().orElse(0));
            renderLength = Math.max(
                    renderLength,
                    builder.outlines.stream()
                            .mapToInt(o -> o.start + o.duration + 8)
                            .max()
                            .orElse(0));
            renderLength = Math.max(
                    renderLength,
                    builder.motions.stream()
                            .mapToInt(m -> m.start + m.duration)
                            .max()
                            .orElse(0));
            JsonObject track = PolymerPonderItems.track(builder.id);
            Map<Integer, List<PolymerPonderItems.Draw>> items =
                    track == null ? Map.of() : PolymerPonderItems.draws(track, resources);
            if (track != null)
                renderLength = Math.max(renderLength, track.get("length").getAsInt());
            if (!builder.breaking.isEmpty()) {
                Map<Integer, List<PolymerPonderItems.Draw>> merged = new HashMap<>();
                items.forEach((tick, draws) -> merged.put(tick, new ArrayList<>(draws)));
                Matrix4f inflate = new Matrix4f()
                        .translate(-1 / 256f, -1 / 256f, -1 / 256f)
                        .scale(1 + 2 / 256f);
                for (int i = 0; i < builder.breaking.size(); i++) {
                    Builder.Breaking event = builder.breaking.get(i);
                    if (event.stage() < 0) continue;
                    int until = renderLength + 1;
                    for (int j = i + 1; j < builder.breaking.size(); j++) {
                        Builder.Breaking next = builder.breaking.get(j);
                        if (next.x() == event.x() && next.y() == event.y() && next.z() == event.z()) {
                            until = next.time();
                            break;
                        }
                    }
                    var draw = new PolymerPonderItems.Draw(
                            breakingModel(event.stage()),
                            inflate,
                            new PolymerPonderItems.At(event.x(), event.y(), event.z()));
                    for (int tick = event.time(); tick < until; tick++)
                        merged.computeIfAbsent(tick, t -> new ArrayList<>()).add(draw);
                }
                items = merged;
            }
            result = new PolymerPonderScene(
                    builder.id,
                    builder.item,
                    builder.title,
                    List.copyOf(parts),
                    List.copyOf(captions),
                    List.copyOf(builder.outlines),
                    length,
                    renderLength,
                    keyframes.stream().mapToInt(Integer::intValue).sorted().toArray(),
                    builder.plateX,
                    builder.plateZ,
                    builder.plateSize,
                    List.copyOf(motions),
                    List.copyOf(assignments),
                    Map.copyOf(pivots),
                    Map.copyOf(pulleys),
                    Map.copyOf(items),
                    builder.nextUpEnabled,
                    List.copyOf(builder.cameraTurns));
            compiled = result;
            return result;
        }

        record Segment(Block original, Block state, float speed, int from, int until, List<Builder.Span> spans) {}

        List<Segment> segments(List<Block> blocks, Select select, boolean allSpeeds) {
            List<Segment> result = new ArrayList<>();
            Map<Object, Set<Block>> chosen = new IdentityHashMap<>();
            for (var change : builder.visibility) chosen.put(change, new HashSet<>(change.selection.apply(select)));
            for (var change : builder.speeds) chosen.put(change, new HashSet<>(change.selection.apply(select)));
            for (Block block : blocks) {
                List<Builder.Span> spans = new ArrayList<>();
                int appear = -1;
                Fade fade = Fade.NONE;
                for (Builder.Visibility change : builder.visibility) {
                    if (!chosen.get(change).contains(block)) continue;
                    if (change.show && appear < 0) {
                        appear = change.time;
                        fade = change.fade;
                    } else if (!change.show && appear >= 0) {
                        spans.add(new Builder.Span(appear, fade, change.time, change.fade));
                        appear = -1;
                    }
                }
                if (appear >= 0) spans.add(new Builder.Span(appear, fade, Integer.MAX_VALUE, Fade.NONE));
                if (spans.isEmpty()) continue;
                Block state = block;
                float speed = block.speed;
                int from = 0;
                for (Builder.SpeedChange change : builder.speeds) {
                    if (!chosen.get(change).contains(block)
                            || change.stateChange == null && !change.restore && !allSpeeds && !kinetic(state)) continue;
                    if (change.time > from) result.add(new Segment(block, state, speed, from, change.time, spans));
                    if (change.restore) {
                        state = block;
                        speed = block.speed;
                    } else if (change.stateChange != null) state = change.stateChange.apply(state);
                    else speed = change.multiply ? speed * change.value : change.value;
                    from = change.time;
                }
                result.add(new Segment(block, state, speed, from, Integer.MAX_VALUE, spans));
            }
            return result;
        }

        List<Segment> captureTimeline() throws IOException {
            List<Block> blocks = PolymerPonderBlockModels.schematic(
                    path -> {
                        try (var stream =
                                PolymerPonderScene.class.getClassLoader().getResourceAsStream(path)) {
                            return stream == null ? null : stream.readAllBytes();
                        } catch (IOException e) {
                            return null;
                        }
                    },
                    builder.schematic);
            return segments(blocks, new Select(blocks), true);
        }

        List<Builder.Simulation> simulation() {
            return List.copyOf(builder.simulation);
        }

        private static Point offset(List<Motion> motions, String section, int tick) {
            float x = 0, y = 0, z = 0;
            for (var motion : motions) {
                if (!motion.section.equals(section) || motion.rotate || tick < motion.start) continue;
                float f = motion.duration == 0 ? 1 : Math.clamp((tick - motion.start) / (float) motion.duration, 0, 1);
                x += motion.delta.x * f;
                y += motion.delta.y * f;
                z += motion.delta.z * f;
            }
            return new Point(x, y, z);
        }

        private void addParts(
                List<Part> parts,
                Function<String, byte[]> resources,
                Block state,
                float speed,
                int from,
                int until,
                List<Builder.Span> spans)
                throws IOException {
            if (state.name.equals("minecraft:air")) return;
            for (var span : spans) {
                int end = span.hide == Integer.MAX_VALUE ? Integer.MAX_VALUE : span.hide + SLIDE;
                int lo = from, hi = Math.min(until, end);
                if (until <= span.start) continue;
                if (lo >= hi) continue;
                parts.add(part(
                        state,
                        models(resources, state, speed),
                        spinner(state),
                        span.start,
                        span.fade,
                        speed,
                        lo,
                        hi,
                        span.hide,
                        span.exitFade));
            }
        }

        private static boolean kinetic(Block block) {
            return block.name.equals(PolymerPonderBlockModels.BELT) || spinner(block) != null;
        }

        private static Spinner spinner(Block block) {
            if (block.name.equals(PolymerPonderBlockModels.BELT)
                    && block.properties.getOrDefault("part", "middle").equals("middle")) return null;
            return SPINNERS.stream()
                    .filter(s -> s.block.equals(block.name))
                    .findFirst()
                    .orElse(null);
        }

        private List<ModelRef> models(Function<String, byte[]> resources, Block block, float speed) throws IOException {
            if (PolymerPonderBlockModels.isBearing(block)) {
                List<ModelRef> result = new ArrayList<>(PolymerPonderBlockModels.models(resources, block));
                result.add(PolymerPonderBlockModels.bearingTop(block));
                return List.copyOf(result);
            }
            if (block.name.equals(ROPE_PULLEY)) {
                List<ModelRef> result = new ArrayList<>(PolymerPonderBlockModels.models(resources, block));
                result.add(PolymerPonderBlockModels.facingSouthModel(
                        ROPE_COIL, block.properties.getOrDefault("axis", "z").equals("x") ? "east" : "south"));
                return List.copyOf(result);
            }
            if (block.name.equals(ELEVATOR_PULLEY)) {
                List<ModelRef> result = new ArrayList<>(PolymerPonderBlockModels.models(resources, block));
                result.add(PolymerPonderBlockModels.facingSouthModel(
                        ELEVATOR_COIL, block.properties.getOrDefault("facing", "north")));
                return List.copyOf(result);
            }
            if (block.name.equals(PolymerPonderBlockModels.BELT)) {
                List<ModelRef> result = new ArrayList<>(PolymerPonderBlockModels.beltSurfaces(block, speed));
                for (ModelRef model : PolymerPonderBlockModels.models(resources, block))
                    if (!model.model.endsWith("/belt/particle")) result.add(model);
                return List.copyOf(result);
            }
            String override = builder.modelOverrides.get(block.name);
            if (override == null) return PolymerPonderBlockModels.models(resources, block);
            if (block.name.equals("create:hand_crank"))
                return List.of(
                        switch (block.properties.getOrDefault("facing", "north")) {
                            case "up" -> new ModelRef(override, 270, 0);
                            case "down" -> new ModelRef(override, 90, 0);
                            case "east" -> new ModelRef(override, 0, 90);
                            case "south" -> new ModelRef(override, 0, 180);
                            case "west" -> new ModelRef(override, 0, 270);
                            default -> new ModelRef(override, 0, 0);
                        });
            return List.of(new ModelRef(override, 0, 0));
        }

        private static Part part(
                Block block,
                List<ModelRef> models,
                Spinner spinner,
                int appear,
                Fade fade,
                float speed,
                int from,
                int until,
                int disappear,
                Fade exitFade) {
            int dir = spinner == null || speed == 0 ? 0 : speed > 0 ? 1 : -1;
            ModelRef axle = block.name.equals(PolymerPonderBlockModels.BELT)
                    ? PolymerPonderBlockModels.pulleyAxle(block)
                    : null;
            if (block.name.equals(ROPE_PULLEY))
                axle = block.properties.getOrDefault("axis", "z").equals("x")
                        ? new ModelRef("create:block/shaft", 90, 90)
                        : new ModelRef("create:block/shaft", 90, 0);
            if (block.name.equals(ELEVATOR_PULLEY))
                axle = switch (block.properties.getOrDefault("facing", "north")) {
                    case "north", "south" -> new ModelRef("create:block/shaft", 90, 90);
                    default -> new ModelRef("create:block/shaft", 90, 0);
                };
            if (PolymerPonderBlockModels.isBearing(block)) {
                String opposite = PolymerPonderBlockModels.opposite(block.properties.getOrDefault("facing", "up"));
                axle = PolymerPonderBlockModels.facingSouthModel("create:block/shaft_half", opposite);
                if (!opposite.equals("east") && !opposite.equals("up") && !opposite.equals("north")) dir = -dir;
            }
            int ticks = dir == 0 ? 0 : Math.clamp(Math.round(64 / Math.abs(speed)), 1, 8);
            return new Part(
                    block.x,
                    block.y,
                    block.z,
                    models,
                    appear,
                    fade,
                    dir != 0 || spinner != null && spinner.housed ? spinner : null,
                    dir,
                    ticks,
                    spinner != null && spinner.cogOffset && PolymerPonderBlockModels.cogOffset(block),
                    from,
                    until,
                    disappear,
                    exitFade,
                    axle);
        }

        PolymerPonderScene scene() {
            try {
                return compile(path -> {
                    try (var stream = PolymerPonderScene.class.getClassLoader().getResourceAsStream(path)) {
                        return stream == null ? null : stream.readAllBytes();
                    } catch (IOException e) {
                        return null;
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException("Ponder scene " + builder.id + " unavailable", e);
            }
        }
    }

    static final String ROPE_PULLEY = "create:rope_pulley";
    static final String ROPE_COIL = "create:block/rope_pulley/rope_coil",
            ROPE = "create:block/rope_pulley/rope",
            PULLEY_MAGNET = "create:block/rope_pulley/pulley_magnet",
            ROPE_HALF = "create:block/rope_pulley/rope_half",
            ROPE_HALF_MAGNET = "create:block/rope_pulley/rope_half_magnet";
    static final List<String> ROPE_PULLEY_PARTS = List.of(ROPE, PULLEY_MAGNET, ROPE_HALF, ROPE_HALF_MAGNET);

    float pulleyOffset(Part part, int tick) {
        String key = position(part.x, part.y, part.z);
        return pulleys.getOrDefault(key, 0f) + Definition.offset(motions, "pulley@" + key, tick).y;
    }

    private void pulleyRope(JsonArray entries, Part part, int tick, float ox, float oy, float oz) {
        float offset = pulleyOffset(part, tick);
        Matrix4f base = sectionTransform(part, tick).translate(ox, oy, oz);
        entries.add(entry(
                stillModel(offset > .25f ? PULLEY_MAGNET : ROPE_HALF_MAGNET),
                new Matrix4f(base).translate(0, -offset, 0)));
        if (offset > .75f) {
            float f = offset % 1;
            if (f < .25f || f > .75f)
                entries.add(entry(stillModel(ROPE_HALF), new Matrix4f(base).translate(0, -(f > .75f ? f - 1 : f), 0)));
        }
        if (offset <= 1.25f) return;
        int size = (int) Math.ceil(offset - 1.25f);
        for (int i = 1; i <= size; i++)
            entries.add(entry(stillModel(ROPE), new Matrix4f(base).translate(0, -offset + i, 0)));
    }

    static final String ELEVATOR_PULLEY = "create:elevator_pulley";
    static final String ELEVATOR_COIL = "create:block/elevator_pulley/rope_coil",
            ELEVATOR_MAGNET = "create:block/elevator_pulley/pulley_magnet",
            ELEVATOR_BELT = "create:block/elevator_pulley/rope",
            ELEVATOR_BELT_HALF = "create:block/elevator_pulley/rope_half";
    static final List<String> ELEVATOR_PULLEY_PARTS = List.of(ELEVATOR_MAGNET, ELEVATOR_BELT, ELEVATOR_BELT_HALF);

    static boolean isPulley(String block) {
        return block.equals(ROPE_PULLEY) || block.equals(ELEVATOR_PULLEY);
    }

    private void elevatorBelt(JsonArray entries, Part part, int tick, float ox, float oy, float oz) {
        float offset = pulleyOffset(part, tick);
        int coil = part.models.stream()
                .filter(m -> m.model.equals(ELEVATOR_COIL))
                .mapToInt(ModelRef::ry)
                .findFirst()
                .orElse(-180);
        float yaw = 180
                + switch (coil) {
                    case -180 -> 180f;
                    case 90 -> -90f;
                    case 270 -> -270f;
                    default -> 0f;
                };
        Matrix4f base = sectionTransform(part, tick)
                .translate(ox, oy, oz)
                .translate(.5f, .5f, .5f)
                .rotateY((float) Math.toRadians(yaw))
                .translate(-.5f, -.5f, -.5f);
        entries.add(entry(stillModel(ELEVATOR_MAGNET), new Matrix4f(base).translate(0, -offset, 0)));
        if (offset == 0) return;
        float f = offset % 1;
        if (f < .25f || f > .75f)
            entries.add(
                    entry(stillModel(ELEVATOR_BELT_HALF), new Matrix4f(base).translate(0, -(f > .75f ? f - 1 : f), 0)));
        if (offset <= .25f) return;
        int size = (int) Math.ceil(offset - .25f);
        for (int i = 0; i < size; i++)
            entries.add(entry(stillModel(ELEVATOR_BELT), new Matrix4f(base).translate(0, -offset + i, 0)));
    }

    private static List<String> wrap(String text) {
        return wrapLines(text, PolymerPonderLayout.CAPTION_TEXT);
    }

    static List<String> wrapLines(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (PolymerArtKit.width(next) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return List.copyOf(lines);
    }

    static final int COPIES = 18;
    static final float DEG_PER_COPY = 5;
    private static final float COG_OFFSET = 22.5f;

    static String stillModel(String model) {
        if (model.startsWith(PART_BANK)) return model;
        return "create:block/p/m/" + model.replace(':', '/');
    }

    static final String PART_BANK = "create:block/p/cap/";

    static String breakingModel(int stage) {
        return PART_BANK + "destroy_stage_" + stage;
    }

    static String spinModel(Spinner spinner, int ticks, int dir) {
        return "create:block/p/" + spinner.model.substring(spinner.model.lastIndexOf('/') + 1) + "_t" + ticks
                + (dir > 0 ? "_f" : "_r");
    }

    static final String PLATE_SHADOW = "create:block/p/platform_shadow";

    static String plateFlash(int step) {
        return "create:block/p/platform_flash_" + step;
    }

    static float finishFlashValue(int step) {
        return (float) Math.pow(.9, 2 * (step - 1));
    }

    static float[] finishFlash(int step) {
        float alpha = finishFlashValue(step) * .9f, progress = alpha * alpha;
        progress = 4 * progress * (1 - progress);
        return new float[] {Math.fma(progress, .75f, .5f), alpha};
    }

    static String ring(String model) {
        return model + "_ring";
    }

    private void walls(JsonArray entries, String model, float bottom, float top, float out) {
        entries.add(entry(
                ring(model),
                new Matrix4f()
                        .translation(plateX - out, bottom, plateZ - out)
                        .scale(plateSize + 2 * out, top - bottom, plateSize + 2 * out)));
    }

    String key(int tick) {
        int frame = frame(tick);
        return frame >= 0 ? "t" + frame : "p" + phase(tick);
    }

    record Layout(
            List<String> keys,
            List<JsonArray> shared,
            Map<String, BitSet> flags,
            Map<String, Map<Integer, JsonArray>> unique) {}

    private Layout layout;
    private Map<String, BitSet> keyFlags;

    synchronized void releaseLayout() {
        if (layout != null) keyFlags = layout.flags();
        layout = null;
    }

    synchronized Layout layout() {
        if (layout != null) return layout;
        List<String> keys = new ArrayList<>();
        Map<String, Map<Integer, String>> states = new LinkedHashMap<>();
        Map<String, Integer> uses = new HashMap<>();
        for (int phase = 0; phase < phases.length; phase++)
            collect(keys, states, uses, "p" + phase, phases[phase], true);
        for (int frame : frames) collect(keys, states, uses, "t" + frame, frame + 1, false);
        List<JsonArray> shared = new ArrayList<>();
        Map<String, Integer> sharedIndex = new HashMap<>();
        Map<String, BitSet> flags = new LinkedHashMap<>();
        Map<String, Map<Integer, JsonArray>> unique = new LinkedHashMap<>();
        for (String key : keys) {
            BitSet on = new BitSet();
            Map<Integer, JsonArray> own = new LinkedHashMap<>();
            for (var state : states.get(key).entrySet()) {
                String id = state.getKey() + "|" + state.getValue();
                if (uses.get(id) >= 2) {
                    int index = sharedIndex.computeIfAbsent(id, k -> {
                        shared.add(JsonParser.parseString(state.getValue()).getAsJsonArray());
                        return shared.size() - 1;
                    });
                    on.set(index);
                } else
                    own.put(
                            state.getKey(),
                            JsonParser.parseString(state.getValue()).getAsJsonArray());
            }
            flags.put(key, on);
            unique.put(key, own);
        }
        return layout = new Layout(List.copyOf(keys), List.copyOf(shared), flags, unique);
    }

    private void collect(
            List<String> keys,
            Map<String, Map<Integer, String>> states,
            Map<String, Integer> uses,
            String key,
            int tick,
            boolean settled) {
        keys.add(key);
        Map<Integer, String> shown = new LinkedHashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            JsonArray entries = new JsonArray();
            partEntries(entries, parts.get(i), tick, settled, false);
            if (entries.isEmpty()) continue;
            shown.put(i, entries.toString());
            uses.merge(i + "|" + entries, 1, Integer::sum);
        }
        states.put(key, shown);
    }

    boolean ownAt(Part part, int frame) {
        Map<Part, Integer> index = partIndex;
        if (index == null) {
            index = new HashMap<>();
            for (int i = 0; i < parts.size(); i++) index.putIfAbsent(parts.get(i), i);
            partIndex = index;
        }
        return layout().unique().get("t" + frame).containsKey(index.getOrDefault(part, -1));
    }

    List<Boolean> stackFlags(String key) {
        Map<String, BitSet> flags;
        synchronized (this) {
            flags = keyFlags != null ? keyFlags : layout().flags();
        }
        BitSet on = flags.getOrDefault(key, new BitSet());
        List<Boolean> result = new ArrayList<>(on.length());
        for (int i = 0; i < on.length(); i++) result.add(on.get(i));
        return result;
    }

    JsonObject sceneDefinition(int view, float scale, int width, int height, Map<Integer, Baked> baked) {
        return sceneDefinition(view, scale, width, height, baked, null);
    }

    JsonObject sceneDefinition(
            int view,
            float scale,
            int width,
            int height,
            Map<Integer, Baked> baked,
            BiFunction<String, JsonArray, JsonArray> bakeShared) {
        Layout layout = layout();
        JsonArray entries = new JsonArray();
        walls(entries, PLATE_SHADOW, -skirt, 0, 0);
        JsonArray flash = new JsonArray();
        for (int step = 1; step <= PolymerPonderDialog.FINISH_FLASH_STEPS; step++) {
            JsonArray walls = new JsonArray();
            walls(walls, plateFlash(step), 0, finishFlash(step)[0], 1 / 256f);
            flash.add(when("f" + step, group(walls)));
        }
        entries.add(select(0, flash));
        Set<String> viewKeys = new HashSet<>();
        Set<Integer> viewItemTicks = new HashSet<>();
        for (int t = 0; t <= renderLength; t++)
            if (viewAt(t) == view) {
                viewKeys.add(key(t));
                viewItemTicks.add(itemTick(t));
            }
        Map<BitSet, List<Integer>> byKeys = new LinkedHashMap<>();
        for (int i = 0; i < layout.shared().size(); i++) {
            BitSet shownAt = new BitSet();
            for (int k = 0; k < layout.keys().size(); k++)
                if (layout.flags().get(layout.keys().get(k)).get(i)) shownAt.set(k);
            byKeys.computeIfAbsent(shownAt, k -> new ArrayList<>()).add(i);
        }
        int sharedGroup = 0;
        for (var group : byKeys.entrySet()) {
            List<Integer> states = group.getValue();
            boolean shown = false;
            for (int k = group.getKey().nextSetBit(0);
                    k >= 0 && !shown;
                    k = group.getKey().nextSetBit(k + 1))
                shown = viewKeys.contains(layout.keys().get(k));
            if (!shown) continue;
            JsonArray state = new JsonArray();
            for (int i : states) state.addAll(layout.shared().get(i));
            if (bakeShared != null && states.size() > 1)
                state = bakeShared.apply("create:block/p/baked/" + id + "_s" + sharedGroup++, state);
            JsonObject condition = new JsonObject();
            condition.addProperty("type", "minecraft:condition");
            condition.addProperty("property", "minecraft:custom_model_data");
            condition.addProperty("index", states.get(0));
            condition.add("on_true", state.size() == 1 ? state.get(0) : group(state));
            condition.add("on_false", empty());
            entries.add(condition);
        }
        JsonArray keyed = new JsonArray();
        for (String key : layout.keys()) {
            if (!viewKeys.contains(key)) continue;
            JsonArray own = new JsonArray();
            Baked frameBaked =
                    key.startsWith("t") && baked != null ? baked.get(Integer.parseInt(key.substring(1))) : null;
            if (frameBaked != null) for (JsonObject entry : frameBaked.entries()) own.add(entry);
            for (var state : layout.unique().get(key).entrySet()) {
                if (frameBaked != null && frameBaked.parts().contains(parts.get(state.getKey()))) continue;
                own.addAll(state.getValue());
            }
            if (!own.isEmpty()) keyed.add(when(key, own.size() == 1 ? own.get(0) : group(own)));
        }
        if (!keyed.isEmpty()) entries.add(select(2, keyed));
        SortedSet<Integer> itemTicks = new TreeSet<>();
        for (int t = 0; t <= renderLength; t++) if (itemTick(t) >= 0) itemTicks.add(itemTick(t));
        Map<String, JsonArray> shownAt = new LinkedHashMap<>();
        Map<String, List<Matrix4f>> kept = new HashMap<>();
        for (int itemTick : itemTicks) {
            boolean settled = frame(itemTick) < 0;
            Map<String, List<Matrix4f>> keptNow = new HashMap<>();
            for (PolymerPonderItems.Draw draw : itemsAt(itemTick)) {
                Matrix4f transform;
                if (draw.at() == null) transform = new Matrix4f(draw.transform());
                else {
                    Matrix4f origin = partOrigin(draw.at(), draw.model(), itemTick, settled);
                    if (origin == null) continue;
                    transform = origin.mul(draw.transform());
                }
                String identity = draw.model() + "|" + draw.tint();
                for (Matrix4f previous : kept.getOrDefault(identity, List.of()))
                    if (withinPixels(previous, transform, scale, width, height)) {
                        transform = previous;
                        break;
                    }
                keptNow.computeIfAbsent(identity, k -> new ArrayList<>()).add(transform);
                if (!viewItemTicks.contains(itemTick)) continue;
                JsonObject drawn = entry(draw, transform);
                JsonArray ticks = shownAt.computeIfAbsent(drawn.toString(), k -> new JsonArray());
                if (ticks.isEmpty()
                        || !ticks.get(ticks.size() - 1).getAsString().equals("i" + itemTick)) ticks.add("i" + itemTick);
            }
            kept = keptNow;
        }
        Map<String, JsonArray> byTicks = new LinkedHashMap<>();
        for (var drawn : shownAt.entrySet())
            byTicks.computeIfAbsent(drawn.getValue().toString(), k -> new JsonArray())
                    .add(JsonParser.parseString(drawn.getKey()));
        for (var group : byTicks.entrySet()) {
            JsonArray ticks = JsonParser.parseString(group.getKey()).getAsJsonArray();
            JsonArray cases = new JsonArray();
            JsonObject when = new JsonObject();
            when.add("when", ticks.size() == 1 ? ticks.get(0) : ticks);
            when.add("model", group.getValue().size() == 1 ? group.getValue().get(0) : group(group.getValue()));
            cases.add(when);
            entries.add(select(1, cases));
        }
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", entries);
        composite.add("transformation", matrix(viewTransform(view, scale, width, height)));
        JsonObject definition = new JsonObject();
        definition.addProperty("oversized_in_gui", true);
        definition.add("model", composite);
        return definition;
    }

    static final float MERGE_PIXELS = 0.5f;

    private boolean withinPixels(Matrix4f a, Matrix4f b, float scale, int width, int height) {
        for (int view = 0; view < views(); view++) {
            Matrix4f v = viewTransform(view, scale, width, height);
            Matrix4f va = new Matrix4f(v).mul(a), vb = new Matrix4f(v).mul(b);
            for (int c = 0; c < 8; c++) {
                Vector3f corner = new Vector3f(c & 1, (c >> 1) & 1, (c >> 2) & 1);
                Vector3f pa = va.transformPosition(new Vector3f(corner)), pb = vb.transformPosition(corner);
                if (Math.abs(pa.x - pb.x) * 16 > MERGE_PIXELS || Math.abs(pa.y - pb.y) * 16 > MERGE_PIXELS)
                    return false;
            }
        }
        return true;
    }

    private static JsonObject group(JsonArray models) {
        JsonObject group = new JsonObject();
        group.addProperty("type", "minecraft:composite");
        group.add("models", models);
        return group;
    }

    private static JsonObject empty() {
        JsonObject empty = new JsonObject();
        empty.addProperty("type", "minecraft:empty");
        return empty;
    }

    private static JsonObject when(String value, JsonElement model) {
        JsonObject when = new JsonObject();
        when.addProperty("when", value);
        when.add("model", model);
        return when;
    }

    private static JsonObject select(int index, JsonArray cases) {
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:custom_model_data");
        select.addProperty("index", index);
        select.add("cases", cases);
        select.add("fallback", empty());
        return select;
    }

    record Baked(Set<Part> parts, List<JsonObject> entries) {}

    private void blocks(JsonArray entries, int tick, boolean settled, boolean still) {
        for (Part part : parts) partEntries(entries, part, tick, settled, still);
    }

    Matrix4f partOrigin(PolymerPonderItems.At at, String model, int tick, boolean settled) {
        for (Part part : parts) {
            if (part.x != at.x() || part.y != at.y() || part.z != at.z()) continue;
            if (part.appear > tick || tick < part.activeFrom || tick >= part.activeUntil) continue;
            for (ModelRef own : part.models) if (own.model.equals(model)) return null;
            float remaining = settled ? 0 : Math.max(0, part.appear + SLIDE - tick) / (float) SLIDE;
            float back = .5f * remaining * remaining;
            float exit = part.disappear == Integer.MAX_VALUE || tick < part.disappear
                    ? 0
                    : Math.clamp((tick - part.disappear) / (float) SLIDE, 0, 1);
            float out = .5f * exit * exit;
            return sectionTransform(part, tick)
                    .translate(
                            part.x - part.fade.x * back + part.exitFade.x * out,
                            part.y - part.fade.y * back + part.exitFade.y * out,
                            part.z - part.fade.z * back + part.exitFade.z * out);
        }
        return null;
    }

    void partEntries(JsonArray entries, Part part, int tick, boolean settled, boolean still) {
        {
            if (part.appear > tick || tick < part.activeFrom || tick >= part.activeUntil) return;
            float remaining = settled ? 0 : Math.max(0, part.appear + SLIDE - tick) / (float) SLIDE;
            float back = .5f * remaining * remaining;
            float exit = part.disappear == Integer.MAX_VALUE || tick < part.disappear
                    ? 0
                    : Math.clamp((tick - part.disappear) / (float) SLIDE, 0, 1);
            float out = .5f * exit * exit;
            float ox = part.x - part.fade.x * back + part.exitFade.x * out;
            float oy = part.y - part.fade.y * back + part.exitFade.y * out;
            float oz = part.z - part.fade.z * back + part.exitFade.z * out;
            boolean axle = part.spinner != null && part.spinner.housed;
            for (ModelRef model : part.models) {
                Matrix4f placed = place(tracked(part, model, tick).translate(ox + .5f, oy + .5f, oz + .5f), model);
                if (part.spinner == null || part.spinner.housed) {
                    entries.add(entry(stillModel(model.model), new Matrix4f(placed).translate(-.5f, -.5f, -.5f)));
                    if (!axle) continue;
                    axle = false;
                    if (part.axle != null)
                        placed = place(
                                tracked(part, part.axle, tick).translate(ox + .5f, oy + .5f, oz + .5f), part.axle);
                    if (part.dir == 0 || still) {
                        entries.add(entry(
                                stillModel(part.spinner.model), new Matrix4f(placed).translate(-.5f, -.5f, -.5f)));
                        continue;
                    }
                }
                if (still) {
                    entries.add(entry(
                            stillModel(part.spinner.model),
                            spun(new Matrix4f(placed), part.spinner, part.offset ? COG_OFFSET : 0)
                                    .translate(-.5f, -.5f, -.5f)));
                    continue;
                }
                entries.add(entry(
                        spinModel(part.spinner, part.ticksPerPose, part.dir),
                        spun(new Matrix4f(placed), part.spinner, part.offset ? COG_OFFSET : 0)
                                .translate(-.5f, -.5f, -.5f)));
            }
            if (part.spinner != null && part.spinner.block.equals(ROPE_PULLEY))
                pulleyRope(entries, part, tick, ox, oy, oz);
            if (part.spinner != null && part.spinner.block.equals(ELEVATOR_PULLEY))
                elevatorBelt(entries, part, tick, ox, oy, oz);
        }
    }

    private static Matrix4f spun(Matrix4f placed, Spinner spinner, float degrees) {
        float radians = (float) Math.toRadians(degrees);
        return spinner.alongZ ? placed.rotateZ(radians) : placed.rotateY(radians);
    }

    Matrix4f tracked(Part part, ModelRef model, int tick) {
        Matrix4f transform = sectionTransform(part, tick);
        return model.track == null || !pivots.containsKey(model.track)
                ? transform
                : transform.mul(motionTransform(model.track, tick));
    }

    private static Matrix4f place(Matrix4f centre, ModelRef model) {
        return centre.rotateYXZ(
                (float) Math.toRadians(-model.ry), (float) Math.toRadians(-model.rx), (float) Math.toRadians(model.rz));
    }

    static final int SWITCH_FRAMES = 8;
    static final int SWITCH_HIDE = 4;

    static String switchId(PolymerPonderScene from, PolymerPonderScene to, int frame, int view) {
        return "ponder_switch_" + from.id + "_" + to.id + "_" + frame + "_" + view;
    }

    static int switchTick(int frame) {
        return 2 * frame + 1;
    }

    static float[] switchOffset(float diff) {
        float slide = (200 + 400 * diff * diff) * diff * (16 * PolymerPonderDialog.SCALE / 30);
        return new float[] {.5736f * slide, -.4698f * slide};
    }

    interface SwitchBlocks {
        JsonArray blocks(PolymerPonderScene scene, boolean outgoing, int tick);
    }

    static JsonArray switchBlocks(PolymerPonderScene scene, boolean outgoing, int tick) {
        JsonArray entries = new JsonArray();
        if (outgoing) scene.blocks(entries, scene.length, true, true);
        else scene.blocks(entries, tick, false, false);
        return entries;
    }

    static JsonObject switchDefinition(
            PolymerPonderScene from,
            PolymerPonderScene to,
            int direction,
            int frame,
            int fromView,
            float scale,
            int width,
            int height,
            SwitchBlocks blocks) {
        int t = switchTick(frame);
        float remaining = (float) Math.pow(.75, t);
        JsonArray outgoing = new JsonArray();
        from.walls(outgoing, PLATE_SHADOW, -from.skirt, 0, 0);
        if (t <= SWITCH_HIDE) outgoing.addAll(blocks.blocks(from, true, from.length));
        JsonArray incoming = new JsonArray();
        to.walls(incoming, PLATE_SHADOW, -to.skirt, 0, 0);
        incoming.addAll(blocks.blocks(to, false, t));
        JsonArray models = new JsonArray();
        models.add(group(
                outgoing, from.switchTransform(-direction * (1 - remaining), fromView, from.scale, width, height)));
        models.add(group(incoming, to.switchTransform(direction * remaining, 0, to.scale, width, height)));
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", models);
        JsonObject definition = new JsonObject();
        definition.addProperty("oversized_in_gui", true);
        definition.add("model", composite);
        return definition;
    }

    Matrix4f sectionTransform(Part part, int tick) {
        String section = null, key = position(part.x, part.y, part.z);
        for (var assignment : assignments)
            if (assignment.start <= tick && assignment.positions.contains(key)) section = assignment.section;
        if (section == null || section.isEmpty()) return new Matrix4f();
        return motionTransform(section, tick);
    }

    private Matrix4f motionTransform(String section, int tick) {
        float x = 0, y = 0, z = 0, rx = 0, ry = 0, rz = 0;
        for (var motion : motions) {
            if (!motion.section.equals(section) || tick < motion.start) continue;
            float f = motion.duration == 0 ? 1 : Math.clamp((tick - motion.start) / (float) motion.duration, 0, 1);
            if (motion.rotate) {
                rx += motion.delta.x * f;
                ry += motion.delta.y * f;
                rz += motion.delta.z * f;
            } else {
                x += motion.delta.x * f;
                y += motion.delta.y * f;
                z += motion.delta.z * f;
            }
        }
        var pivot = pivots.get(section);
        Matrix4f transform = new Matrix4f()
                .translation(x, y, z)
                .translate(pivot.x, pivot.y, pivot.z)
                .rotateX((float) Math.toRadians(rx))
                .rotateY((float) Math.toRadians(ry))
                .rotateZ((float) Math.toRadians(rz))
                .translate(-pivot.x, -pivot.y, -pivot.z);
        var anchor = pivots.get(section + STABILIZED);
        if (anchor != null)
            transform
                    .translate(anchor.x, anchor.y, anchor.z)
                    .rotateX((float) Math.toRadians(-rx))
                    .rotateY((float) Math.toRadians(-ry))
                    .rotateZ((float) Math.toRadians(-rz))
                    .translate(-anchor.x, -anchor.y, -anchor.z);
        return transform;
    }

    static final String STABILIZED = "#stabilized";

    private Matrix4f switchTransform(float diff, int view, float scale, int width, int height) {
        float[] offset = switchOffset(diff);
        return new Matrix4f()
                .translation(offset[0] / 16, -offset[1] / 16, 0)
                .mul(viewTransform(view, scale, width, height));
    }

    private static JsonObject group(JsonArray entries, Matrix4f transform) {
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", entries);
        composite.add("transformation", matrix(transform));
        return composite;
    }

    Matrix4f viewTransform(int view, float scale, int width, int height) {
        return new Matrix4f()
                .translation(PolymerPonderLayout.WIDTH / 2f / 16f, 1 - height / 2f / 16f, 0)
                .scale(scale)
                .mul(rotation(view))
                .translate(-plateX - plateSize / 2f, -1, -plateZ - plateSize / 2f);
    }

    private Matrix4f rotation(int view) {
        return new Matrix4f().rotateX((float) Math.toRadians(PITCH)).rotateY((float) Math.toRadians(yaws[view]));
    }

    private static final int FIT_MARGIN = 6;

    private float fitScale() {
        float halfW = PolymerPonderLayout.WIDTH / 2f - FIT_MARGIN,
                halfH = PolymerPonderLayout.SCENE_HEIGHT / 2f - FIT_MARGIN;
        float scale = PolymerPonderDialog.SCALE;
        List<float[]> boxes = new ArrayList<>();
        boxes.add(new float[] {plateX, 0, plateZ, plateX + plateSize, 1, plateZ + plateSize});
        for (Part part : parts)
            boxes.add(new float[] {
                part.x - .25f, part.y - .25f, part.z - .25f, part.x + 1.25f, part.y + 1.25f, part.z + 1.25f
            });
        TreeSet<Integer> samples = new TreeSet<>(List.of(0, renderLength));
        for (Motion motion : motions)
            for (int tick = motion.start - motion.start % 2; tick <= motion.start + motion.duration + 1; tick += 2)
                samples.add(Math.clamp(tick, 0, renderLength));
        for (Assignment assignment : assignments)
            samples.add(Math.clamp(assignment.start() - assignment.start() % 2, 0, renderLength));
        for (Part part : parts)
            for (int edge : new int[] {part.appear, part.activeFrom, part.activeUntil - 1}) {
                int even = edge + (edge & 1);
                if (even >= 0 && even <= renderLength) samples.add(even);
                if (edge - (edge & 1) >= 0 && edge - (edge & 1) <= renderLength) samples.add(edge - (edge & 1));
            }
        Set<String> seen = new HashSet<>();
        for (Part part : parts)
            if (!assignments.isEmpty())
                for (int tick : samples) {
                    if (tick < part.appear || tick < part.activeFrom || tick >= part.activeUntil) continue;
                    var transform = sectionTransform(part, tick);
                    var centre = transform.transformPosition(new Vector3f(part.x + .5f, part.y + .5f, part.z + .5f));
                    if (Math.abs(centre.x - plateX - plateSize / 2f) > plateSize + 16
                            || Math.abs(centre.y) > 32
                            || Math.abs(centre.z - plateZ - plateSize / 2f) > plateSize + 16) continue;
                    float[] bounds = {
                        Float.POSITIVE_INFINITY,
                        Float.POSITIVE_INFINITY,
                        Float.POSITIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY
                    };
                    for (int c = 0; c < 8; c++) {
                        var p = transform.transformPosition(new Vector3f(
                                part.x + ((c & 1) == 0 ? -.25f : 1.25f),
                                part.y + ((c & 2) == 0 ? -.25f : 1.25f),
                                part.z + ((c & 4) == 0 ? -.25f : 1.25f)));
                        bounds[0] = Math.min(bounds[0], p.x);
                        bounds[1] = Math.min(bounds[1], p.y);
                        bounds[2] = Math.min(bounds[2], p.z);
                        bounds[3] = Math.max(bounds[3], p.x);
                        bounds[4] = Math.max(bounds[4], p.y);
                        bounds[5] = Math.max(bounds[5], p.z);
                    }
                    if (seen.add(Arrays.toString(bounds))) boxes.add(bounds);
                }
        for (int view = 0; view < views(); view++)
            for (float[] box : boxes)
                for (int corner = 0; corner < 8; corner++) {
                    var p = rotation(view)
                            .transformPosition(new Vector3f(
                                    ((corner & 1) == 0 ? box[0] : box[3]) - plateX - plateSize / 2f,
                                    ((corner & 2) == 0 ? box[1] : box[4]) - 1,
                                    ((corner & 4) == 0 ? box[2] : box[5]) - plateZ - plateSize / 2f));
                    if (Math.abs(p.x) > 1e-4) scale = Math.min(scale, halfW / (16 * Math.abs(p.x)));
                    if (Math.abs(p.y) > 1e-4) scale = Math.min(scale, halfH / (16 * Math.abs(p.y)));
                }
        return scale;
    }

    private float skirtLength() {
        float lowest = 0;
        for (int view = 0; view < views(); view++)
            for (int corner = 0; corner < 4; corner++) {
                var p = rotation(view)
                        .transformPosition(new Vector3f(
                                ((corner & 1) == 0 ? 0 : plateSize) - plateSize / 2f,
                                -1,
                                ((corner & 2) == 0 ? 0 : plateSize) - plateSize / 2f));
                lowest = Math.max(lowest, -16 * scale * p.y);
            }
        float room = PolymerPonderLayout.SCENE_HEIGHT / 2f - 2 - lowest;
        return Math.clamp(room / (16 * scale * (float) Math.cos(Math.toRadians(PITCH))), 0, 4);
    }

    Point project(Point target, int view) {
        var point = viewTransform(view, scale, PolymerPonderLayout.SCENE_WIDTH, PolymerPonderLayout.SCENE_HEIGHT)
                .transformPosition(new Vector3f(target.x, target.y, target.z));
        return new Point(8 + 16 * (point.x - .5f), 8 - 16 * (point.y - .5f), point.z);
    }

    private static JsonObject entry(PolymerPonderItems.Draw draw, Matrix4f transform) {
        JsonObject entry = entry(stillModel(draw.model()), transform, 1e4f);
        if (draw.tint() != -1) {
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:constant");
            tint.addProperty("value", draw.tint());
            JsonArray tints = new JsonArray();
            tints.add(tint);
            entry.add("tints", tints);
        }
        return entry;
    }

    private static JsonObject entry(String model, Matrix4f transform) {
        return entry(model, transform, 1e5f);
    }

    private static JsonObject entry(String model, Matrix4f transform, float precision) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:model");
        entry.addProperty("model", model);
        entry.add("transformation", matrix(transform, precision));
        return entry;
    }

    static JsonArray matrix(Matrix4f value) {
        return matrix(value, 1e5f);
    }

    static JsonArray matrix(Matrix4f value, float precision) {
        JsonArray result = new JsonArray();
        for (float f : new Matrix4f(value).transpose().get(new float[16])) {
            float rounded = Math.round(f * precision) / precision;
            if (rounded == (int) rounded) result.add((int) rounded);
            else result.add(rounded);
        }
        return result;
    }
}
