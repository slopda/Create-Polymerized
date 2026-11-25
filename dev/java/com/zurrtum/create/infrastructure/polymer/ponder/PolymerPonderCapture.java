package com.zurrtum.create.infrastructure.polymer.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.AllClientHandle;
import com.zurrtum.create.Create;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.catnip.levelWrappers.SchematicLevel;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import com.zurrtum.create.content.kinetics.belt.BeltBlockEntity;
import com.zurrtum.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.kinetics.chainConveyor.ChainConveyorBehaviour;
import com.zurrtum.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.zurrtum.create.content.kinetics.crafter.ConnectedInputHandler;
import com.zurrtum.create.content.kinetics.deployer.DeployerBlockEntity;
import com.zurrtum.create.content.logistics.box.PackageEntity;
import com.zurrtum.create.content.logistics.box.PackageItem;
import com.zurrtum.create.content.logistics.chute.ChuteBlock;
import com.zurrtum.create.content.logistics.chute.ChuteBlockEntity;
import com.zurrtum.create.content.logistics.chute.SmartChuteBlockEntity;
import com.zurrtum.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.zurrtum.create.content.trains.GlobalRailwayManager;
import com.zurrtum.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.zurrtum.create.content.trains.bogey.StandardBogeyBlockEntity;
import com.zurrtum.create.foundation.blockEntity.behaviour.audio.FrogportAudioBehaviour;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerChainConveyors;
import com.zurrtum.create.infrastructure.polymer.train.PolymerRailVisuals;
import com.zurrtum.create.ponder.api.VirtualBlockEntity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class PolymerPonderCapture {
    private PolymerPonderCapture() {}

    static List<String> run(String out, ServerLevel original) throws IOException {
        List<String> report = new ArrayList<>();
        Files.createDirectories(Path.of(out));
        AllClientHandle handle = AllClientHandle.INSTANCE;
        GlobalRailwayManager railways = new GlobalRailwayManager();
        AllClientHandle.INSTANCE = new AllClientHandle() {
            @Override
            public GlobalRailwayManager getGlobalRailwayManager() {
                return railways;
            }
        };
        try {
            for (PolymerPonderScene.Definition definition : PolymerPonderCatalog.capturePending()) {
                JsonObject track = capture(definition, original);
                Files.writeString(
                        Path.of(out, definition.id() + ".json"), track.toString() + "\n", StandardCharsets.UTF_8);
                report.add(
                        definition.id() + ": " + track.getAsJsonArray("ticks").size() + " ticks with items");
            }
        } finally {
            AllClientHandle.INSTANCE = handle;
        }
        return report;
    }

    private static final class CaptureLevel extends SchematicLevel {
        CaptureLevel(ServerLevel original) {
            super(BlockPos.ZERO, original);
        }

        @Override
        public boolean isClientSide() {
            return true;
        }

        @Override
        protected void onBEadded(BlockEntity blockEntity, BlockPos pos) {
            super.onBEadded(blockEntity, pos);
            if (blockEntity instanceof VirtualBlockEntity virtual) virtual.markVirtual();
            if (blockEntity instanceof ChainConveyorBlockEntity chain
                    && chain.getBehaviour(ChainConveyorBehaviour.TYPE) == null)
                chain.attachBehaviourLate(new ChainConveyorBehaviour(chain) {});
            if (blockEntity instanceof FrogportBlockEntity frog
                    && frog.getBehaviour(com.zurrtum
                                    .create
                                    .foundation
                                    .blockEntity
                                    .behaviour
                                    .audio
                                    .FrogportAudioBehaviour
                                    .TYPE)
                            == null)
                frog.attachBehaviourLate(new FrogportAudioBehaviour(frog) {
                    @Override
                    public void open(Level level, BlockPos pos) {}

                    @Override
                    public void close(Level level, BlockPos pos) {}

                    @Override
                    public void catchPackage(Level level, BlockPos pos) {}

                    @Override
                    public void depositPackage(Level level, BlockPos pos) {}
                });
        }
    }

    static final float PROBE_TIME = 7919.37f;
    static final float FROZEN = Float.NaN;

    static JsonObject capture(PolymerPonderScene.Definition definition, ServerLevel original) throws IOException {
        Map<Integer, JsonArray> probe = new HashMap<>();
        replay(definition, original, PROBE_TIME, probe, null, null);
        Map<Integer, JsonArray> frozen = new HashMap<>(), one = new HashMap<>(), seven = new HashMap<>();
        replay(definition, original, FROZEN, frozen, one, seven);
        Map<Integer, JsonArray> parts = new HashMap<>();
        JsonObject result = replay(definition, original, 0, parts, null, null);
        Set<String> variant = new HashSet<>();
        for (int tick : parts.keySet()) {
            Map<String, JsonObject> a = keyed(parts.get(tick)), b = keyed(probe.getOrDefault(tick, new JsonArray()));
            for (var entry : a.entrySet())
                if (!entry.getValue().equals(b.get(entry.getKey()))) variant.add(entry.getKey());
            for (String key : b.keySet()) if (!a.containsKey(key)) variant.add(key);
        }
        JsonArray ticks = result.getAsJsonArray("ticks");
        Map<Integer, JsonObject> byTick = new TreeMap<>();
        for (var element : ticks) byTick.put(element.getAsJsonObject().get("t").getAsInt(), element.getAsJsonObject());
        for (int tick : parts.keySet()) {
            JsonArray kept = new JsonArray();
            Map<String, JsonObject> still = keyed(frozen.getOrDefault(tick, new JsonArray())),
                    stillOne = keyed(one.getOrDefault(tick, new JsonArray())),
                    stillSeven = keyed(seven.getOrDefault(tick, new JsonArray()));
            for (var entry : keyed(parts.get(tick)).entrySet()) {
                if (!variant.contains(entry.getKey())) {
                    kept.add(entry.getValue());
                    continue;
                }
                JsonObject part = still.get(entry.getKey());
                if (part == null) continue;
                JsonObject spin = spin(part, stillOne.get(entry.getKey()), stillSeven.get(entry.getKey()));
                if (spin != null) {
                    part = part.deepCopy();
                    part.add("spin", spin);
                }
                kept.add(part);
            }
            if (kept.isEmpty()) continue;
            JsonObject entry = byTick.computeIfAbsent(tick, t -> {
                JsonObject created = new JsonObject();
                created.addProperty("t", t);
                created.add("items", new JsonArray());
                return created;
            });
            entry.add("parts", kept);
        }
        JsonArray merged = new JsonArray();
        byTick.values().forEach(merged::add);
        result.add("ticks", merged);
        result.addProperty("schema", 2);
        result.addProperty("clockDrivenParts", variant.size());
        return result;
    }

    static JsonObject spin(JsonObject still, JsonObject one, JsonObject seven) {
        if (one == null || seven == null) return null;
        Matrix4f m0 = parse(still), m1 = parse(one), m7 = parse(seven);
        Matrix4f r = new Matrix4f(m1).mul(new Matrix4f(m0).invert());
        Matrix3f r3 = new Matrix3f();
        r.get3x3(r3);
        AxisAngle4f rotation = new AxisAngle4f().set(new Matrix3f(r3));
        if (!Float.isFinite(rotation.angle) || Math.abs(rotation.angle) < 1e-4f) return null;
        Vector3f axis = new Vector3f(rotation.x, rotation.y, rotation.z).normalize();
        Vector3f t = r.getTranslation(new Vector3f()), c = m0.transformPosition(new Vector3f(.5f, .5f, .5f));
        Matrix3f a = new Matrix3f()
                .identity()
                .sub(r3)
                .add(new Matrix3f(
                        axis.x * axis.x,
                        axis.y * axis.x,
                        axis.z * axis.x,
                        axis.x * axis.y,
                        axis.y * axis.y,
                        axis.z * axis.y,
                        axis.x * axis.z,
                        axis.y * axis.z,
                        axis.z * axis.z));
        Vector3f pivot = new Vector3f(t).add(new Vector3f(axis).mul(axis.dot(c)));
        a.invert().transform(pivot);
        float deg = (float) Math.toDegrees(rotation.angle);
        for (int n : new int[] {1, 7}) {
            Matrix4f turned = new Matrix4f()
                    .translation(pivot)
                    .rotate((float) Math.toRadians(deg * n), axis)
                    .translate(-pivot.x, -pivot.y, -pivot.z)
                    .mul(m0);
            Matrix4f expected = n == 1 ? m1 : m7;
            for (int i = 0; i < 16; i++) {
                float[] x = turned.get(new float[16]), y = expected.get(new float[16]);
                if (Math.abs(x[i] - y[i]) > 2e-3f) return null;
            }
        }
        JsonObject result = new JsonObject();
        JsonArray axisJson = new JsonArray(), pivotJson = new JsonArray();
        axisJson.add(round(axis.x));
        axisJson.add(round(axis.y));
        axisJson.add(round(axis.z));
        pivotJson.add(round(pivot.x));
        pivotJson.add(round(pivot.y));
        pivotJson.add(round(pivot.z));
        result.add("axis", axisJson);
        result.add("pivot", pivotJson);
        result.addProperty("deg", round(deg));
        return result;
    }

    private static Matrix4f parse(JsonObject part) {
        JsonArray m = part.getAsJsonArray("m");
        float[] values = new float[16];
        for (int i = 0; i < 16; i++) values[i] = m.get(i).getAsFloat();
        return new Matrix4f().set(values).transpose();
    }

    private static Map<String, JsonObject> keyed(JsonArray parts) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (var element : parts) {
            JsonObject part = element.getAsJsonObject();
            String base = part.get("at").toString() + "|" + part.get("model").getAsString();
            int occurrence = occurrences.merge(base, 1, Integer::sum) - 1;
            result.put(base + "|" + occurrence, part);
        }
        return result;
    }

    private static boolean sceneRendered(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (name.equals(PolymerPonderBlockModels.BELT) || PolymerPonderScene.isPulley(name)) return true;
        if (name.equals("create:mechanical_bearing")
                || name.equals("create:windmill_bearing")
                || name.equals("create:clockwork_bearing")) return true;
        for (var spinner : PolymerPonderScene.SPINNERS) if (spinner.block().equals(name)) return true;
        return false;
    }

    private static JsonObject replay(
            PolymerPonderScene.Definition definition,
            ServerLevel original,
            float partTime,
            Map<Integer, JsonArray> partsOut,
            Map<Integer, JsonArray> oneOut,
            Map<Integer, JsonArray> sevenOut)
            throws IOException {
        CaptureLevel level = new CaptureLevel(original);
        String schematic = definition.builder.schematic;
        CompoundTag nbt = PolymerPonderBlockModels.schematicNbt(PolymerPonderCapture::resource, schematic);
        StructureTemplate template = new StructureTemplate();
        template.load(original.registryAccess().lookupOrThrow(Registries.BLOCK), nbt);
        template.placeInWorld(
                level,
                BlockPos.ZERO,
                BlockPos.ZERO,
                new StructurePlaceSettings(),
                level.getRandom(),
                Block.UPDATE_CLIENTS);

        List<PolymerPonderScene.Definition.Segment> segments = definition.captureTimeline();
        List<PolymerPonderScene.Builder.Simulation> simulation = definition.simulation();
        int length = 0;
        for (var segment : segments)
            if (segment.until() != Integer.MAX_VALUE) length = Math.max(length, segment.until());
        for (var event : simulation) length = Math.max(length, event.time());
        length = Math.max(length, definition.builder.lastTime()) + 1;

        Map<String, List<TransportedItemStack>> beltLinks = new HashMap<>();
        List<double[]> deployerAnimations = new ArrayList<>(), bogeyAnimations = new ArrayList<>();
        Birbs birbs = new Birbs();
        Map<String, Entity> entityLinks = new HashMap<>();
        JsonArray ticks = new JsonArray();
        for (int tick = 0; tick <= length; tick++) {
            for (var iterator = level.getEntityList().iterator(); iterator.hasNext(); ) {
                Entity entity = iterator.next();
                entity.tickCount++;
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
                entity.tick();
                if (entity.getY() <= -0.5f) entity.discard();
                if (!entity.isAlive()) iterator.remove();
            }
            birbs.chasePoi();
            for (var segment : segments) {
                if (segment.state().name().equals("minecraft:air")
                        || tick < segment.from()
                        || tick >= segment.until()
                        || !shown(segment, tick)) continue;
                BlockPos pos = new BlockPos(
                        segment.original().x(),
                        segment.original().y(),
                        segment.original().z());
                tickBlockEntity(level, pos);
            }
            birbs.tickElements(level, tick);
            for (var segment : segments) {
                if (segment.from() != tick) continue;
                BlockPos pos = new BlockPos(
                        segment.original().x(),
                        segment.original().y(),
                        segment.original().z());
                BlockState state = blockState(segment.state());
                if (level.getBlockState(pos) != state) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                if (level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic
                        && kinetic.getSpeed() != segment.speed()) kinetic.setSpeed(segment.speed());
            }
            for (var event : simulation)
                if (event.time() == tick) {
                    JsonObject json = JsonParser.parseString(event.json()).getAsJsonObject();
                    String action = json.get("action").getAsString();
                    if (action.startsWith("birb") || action.startsWith("cart") || action.equals("poi")) {
                        birbs.schedule(json);
                    } else if (json.get("action").getAsString().equals("animateDeployer")) {
                        JsonArray p = json.getAsJsonArray("position");
                        deployerAnimations.add(new double[] {
                            p.get(0).getAsInt(),
                            p.get(1).getAsInt(),
                            p.get(2).getAsInt(),
                            json.get("distance").getAsDouble(),
                            json.get("duration").getAsInt(),
                            json.get("duration").getAsInt(),
                            Double.NaN
                        });
                    } else if (json.get("action").getAsString().equals("animateBogey")) {
                        JsonArray p = json.getAsJsonArray("position");
                        int duration = json.get("duration").getAsInt();
                        bogeyAnimations.add(new double[] {
                            p.get(0).getAsInt(),
                            p.get(1).getAsInt(),
                            p.get(2).getAsInt(),
                            (float) (json.get("distance").getAsFloat() / duration),
                            duration
                        });
                    } else apply(level, json, beltLinks, entityLinks);
                }
            tickDeployerAnimations(level, deployerAnimations);
            tickBogeyAnimations(level, bogeyAnimations);
            birbs.tickInstructions();
            JsonArray items = record(level, segments, tick);
            JsonArray models = birbs.record(tick, items);
            JsonArray parts = recordParts(level, segments, tick, Float.isNaN(partTime) ? 0 : tick + partTime);
            if (!parts.isEmpty()) partsOut.put(tick, parts);
            if (oneOut != null && !parts.isEmpty()) {
                oneOut.put(tick, recordParts(level, segments, tick, 1));
                sevenOut.put(tick, recordParts(level, segments, tick, 7));
            }
            if (!items.isEmpty() || !models.isEmpty()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("t", tick);
                entry.add("items", items);
                if (!models.isEmpty()) entry.add("models", models);
                ticks.add(entry);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("scene", definition.id());
        result.addProperty("length", length);
        result.add("ticks", ticks);
        return result;
    }

    private static final class Birbs {
        static final class Birb {
            double lx, ly, lz, x, y, z, xo, yo, zo, xOld, yOld, zOld;
            float yaw = 180, yawO = 180, pitch, flapSpeed;
            int tickCount, variant;
            String pose;
            int[] component;
            boolean hat, inScene, visible, created;
            boolean chain;

            float fade;
            double[] fadeVec = {0, 0, 0};
            String cart;

            float cartYaw;
        }

        static final class Ticking {
            String kind;
            Birb birb;
            int total, remaining;
            double[] delta, target, fadeVec;
        }

        final Map<String, Birb> links = new HashMap<>();
        final List<Birb> all = new ArrayList<>();
        final List<Ticking> active = new ArrayList<>();
        double[] poi = {0, 4, 0}, chasing = {0, 4, 0};
        CaptureLevel level;

        void chasePoi() {
            if (chasing != null) for (int i = 0; i < 3; i++) poi[i] += (chasing[i] - poi[i]) * 0.25;
        }

        void tickElements(CaptureLevel level, int tick) {
            this.level = level;
            for (Birb b : all) {
                if (!b.inScene) continue;
                if (b.cart != null) {
                    b.created = true;
                    b.tickCount++;
                    b.xo = b.xOld = b.x;
                    b.yo = b.yOld = b.y;
                    b.zo = b.zOld = b.z;
                    continue;
                }
                if (!b.created) {
                    b.created = true;
                    b.yaw = b.yawO = 180;
                }
                b.tickCount++;
                b.xo = b.x;
                b.yo = b.y;
                b.zo = b.z;
                b.yawO = b.yaw;
                pose(b, tick);
                b.xOld = b.x;
                b.yOld = b.y;
                b.zOld = b.z;
            }
        }

        private void pose(Birb b, int tick) {
            switch (b.pose) {
                case "dance" -> b.yaw = b.yawO - 2;
                case "flappy" -> {
                    double length = Math.sqrt((b.x - b.xOld) * (b.x - b.xOld)
                            + (b.y - b.yOld) * (b.y - b.yOld)
                            + (b.z - b.zOld) * (b.z - b.zOld));
                    double phase = Math.min(length * 15, 8);
                    float f = (float) ((tick % 100) * phase);
                    b.flapSpeed = length == 0 ? 0 : (float) Math.sin(f) + 1;
                }
                case "poi" -> {
                    double dx = poi[0] - (b.lx + b.x), dy = poi[1] - (b.ly + b.y + 0.54), dz = poi[2] - (b.lz + b.z);
                    double horizontal = Math.sqrt((float) (dx * dx + dz * dz));
                    float targetPitch = wrap((float) -(Math.atan2(dy, horizontal) * (180 / Math.PI)));
                    float targetYaw = wrap((float) -(Math.atan2(dz, dx) * (180 / Math.PI)) + 90);
                    b.pitch = angleLerp(0.4f, b.pitch, targetPitch);
                    b.yaw = angleLerp(0.4f, b.yaw, targetYaw);
                }
                case "spin" -> {
                    if (level.getBlockEntity(new BlockPos(b.component[0], b.component[1], b.component[2]))
                            instanceof KineticBlockEntity kinetic) b.yaw = b.yawO + kinetic.getSpeed() * 0.3f;
                }
                default -> {}
            }
        }

        void schedule(JsonObject event) {
            String action = event.get("action").getAsString();
            if (action.equals("poi")) {
                double[] target = triple(event.getAsJsonArray("location"));
                if (chasing == null) poi = target.clone();
                chasing = target;
                return;
            }
            String link = event.get("link").getAsString();
            switch (action) {
                case "cart" -> {
                    Birb b = new Birb();
                    double[] at = triple(event.getAsJsonArray("location"));
                    b.lx = at[0];
                    b.ly = at[1] + 1 / 16.0;
                    b.lz = at[2];
                    b.cart = event.get("type").getAsString();
                    b.cartYaw = event.get("rotation").getAsFloat();
                    links.put(link, b);
                    all.add(b);
                    start("fadeIn", b, 10, null, new double[] {0, -0.5, 0});
                }
                case "cartMove" ->
                    start(
                            "move",
                            links.get(link),
                            event.get("duration").getAsInt(),
                            triple(event.getAsJsonArray("offset")),
                            null);
                case "cartRotate" ->
                    start(
                            "rotate",
                            links.get(link),
                            event.get("duration").getAsInt(),
                            new double[] {0, event.get("rotation").getAsDouble(), 0},
                            null);
                case "birb" -> {
                    Birb b = new Birb();
                    double[] at = triple(event.getAsJsonArray("location"));
                    b.lx = at[0];
                    b.ly = at[1];
                    b.lz = at[2];
                    b.pose = event.get("pose").getAsString();
                    b.component = event.has("component") ? ints(event.getAsJsonArray("component")) : null;
                    b.variant = event.get("variant").getAsInt();
                    b.chain = event.has("chain") && event.get("chain").getAsBoolean();
                    links.put(link, b);
                    all.add(b);
                    double[] d = direction(
                            event.has("fadeDirection")
                                    ? event.get("fadeDirection").getAsString()
                                    : "DOWN");
                    start("fadeIn", b, event.has("fade") ? event.get("fade").getAsInt() : 10, null, new double[] {
                        d[0] * 0.5, d[1] * 0.5, d[2] * 0.5
                    });
                }
                case "birbMove" ->
                    start(
                            "move",
                            links.get(link),
                            event.get("duration").getAsInt(),
                            triple(event.getAsJsonArray("offset")),
                            null);
                case "birbRotate" ->
                    start(
                            "rotate",
                            links.get(link),
                            event.get("duration").getAsInt(),
                            triple(event.getAsJsonArray("rotation")),
                            null);
                case "birbPose" -> {
                    Birb b = links.get(link);
                    if (b != null) {
                        b.pose = event.get("pose").getAsString();
                        b.component = event.has("component") ? ints(event.getAsJsonArray("component")) : null;
                    }
                }
                case "birbConductor" -> {
                    Birb b = links.get(link);
                    if (b != null) b.hat = event.get("on").getAsBoolean();
                }
                case "birbHide", "cartHide" -> {
                    double[] vec = {0, 0, 0};
                    if (event.has("direction") && !event.get("direction").isJsonNull()) {
                        double[] d = direction(event.get("direction").getAsString());
                        vec = new double[] {-d[0] * 0.5, -d[1] * 0.5, -d[2] * 0.5};
                    }
                    start("fadeOut", links.get(link), 15, null, vec);
                }
                default -> throw new IllegalArgumentException("Unknown parrot action " + action);
            }
        }

        private void start(String kind, Birb birb, int ticks, double[] delta, double[] fadeVec) {
            if (birb == null) return;
            Ticking t = new Ticking();
            t.kind = kind;
            t.birb = birb;
            t.total = t.remaining = ticks;
            t.delta = delta;
            t.fadeVec = fadeVec;
            active.add(t);
        }

        void tickInstructions() {
            for (var iterator = active.iterator(); iterator.hasNext(); ) {
                Ticking t = iterator.next();
                Birb b = t.birb;
                boolean first = t.remaining == t.total;
                if (t.remaining > 0) t.remaining--;
                switch (t.kind) {
                    case "fadeIn" -> {
                        if (first) {
                            b.inScene = true;
                            b.visible = true;
                            b.fade = 0;
                            b.fadeVec = t.fadeVec;
                        }
                        float f = t.total == 0 ? 1 : t.remaining / (float) t.total;
                        b.fade = t.remaining == 0 ? 1 : 1 - f * f;
                    }
                    case "fadeOut" -> {
                        if (first) {
                            b.visible = true;
                            b.fade = 1;
                            b.fadeVec = t.fadeVec;
                        }
                        float f = t.remaining / (float) t.total;
                        b.fade = 1 - (1 - f) * (1 - f);
                        if (t.remaining == 0) {
                            b.visible = false;
                            b.fade = 0;
                        }
                    }
                    case "move", "rotate" -> {
                        double[] current = !b.created
                                ? new double[3]
                                : t.kind.equals("move")
                                        ? new double[] {b.x, b.y, b.z}
                                        : b.cart != null
                                                ? new double[] {0, b.cartYaw, 0}
                                                : new double[] {b.pitch, b.yaw, 0};
                        if (first)
                            t.target = new double[] {
                                current[0] + t.delta[0], current[1] + t.delta[1], current[2] + t.delta[2]
                            };
                        if (!b.created) break;
                        double[] next = new double[3];
                        for (int i = 0; i < 3; i++)
                            next[i] = t.remaining == 0 ? t.target[i] : current[i] + t.delta[i] / Math.max(1, t.total);
                        if (t.kind.equals("move")) {
                            b.x = next[0];
                            b.y = next[1];
                            b.z = next[2];
                            if (t.total == 0) {
                                b.xo = b.x;
                                b.yo = b.y;
                                b.zo = b.z;
                            }
                        } else if (b.cart != null) {
                            b.cartYaw = (float) next[1];
                        } else {
                            b.pitch = (float) next[0];
                            b.yaw = (float) next[1];
                            if (t.total == 0) b.yawO = b.yaw;
                        }
                    }
                    default -> {}
                }
                if (t.remaining == 0) iterator.remove();
            }
        }

        JsonArray record(int tick, JsonArray items) {
            JsonArray models = new JsonArray();
            for (Birb b : all) {
                if (!b.inScene || !b.visible) continue;
                double s = b.fade - 1;
                float x = (float) (b.lx + b.x + b.fadeVec[0] * s),
                        y = (float) (b.ly + b.y + b.fadeVec[1] * s),
                        z = (float) (b.lz + b.z + b.fadeVec[2] * s);
                if (b.cart != null) {
                    BlockPos own = BlockPos.containing(b.x, b.y, b.z);
                    if (level.getBlockState(own).getBlock() instanceof BaseRailBlock
                            || level.getBlockState(own.below()).getBlock() instanceof BaseRailBlock)
                        throw new IllegalStateException("Ponder minecart entity position " + own + " is on a rail");
                    for (var draw : PolymerPonderCarts.draws(b.cart, x, y, z, b.cartYaw)) models.add(drawEntry(draw));
                    continue;
                }
                PolymerPonderParrots.Pose pose = b.pose.equals("dance")
                        ? PolymerPonderParrots.Pose.PARTY
                        : b.pose.equals("flappy")
                                ? PolymerPonderParrots.Pose.FLYING
                                : PolymerPonderParrots.Pose.STANDING;
                float flap = b.flapSpeed;
                Matrix4f after = new Matrix4f();
                if (b.chain) {
                    after.translate(0, 1.5f, 0)
                            .rotateZ((float) Math.toRadians(Math.sin(tick * 0.2f) * 10))
                            .translate(0, -1.5f, 0);
                    flap = 2;
                    Matrix4f hand = new Matrix4f()
                            .translation(x, y, z)
                            .rotateY((float) Math.toRadians(b.yaw))
                            .mul(after)
                            .rotateY((float) Math.toRadians(90))
                            .rotateX((float) Math.toRadians(90))
                            .rotateZ((float) Math.toRadians(90))
                            .scale(1.5f)
                            .translate(-0.1f, 0.2f, -0.6f);
                    JsonObject wrench = new JsonObject();
                    wrench.addProperty("item", "create:wrench");
                    wrench.addProperty("ctx", "ground");
                    wrench.add("m", PolymerPonderScene.matrix(hand));
                    items.add(wrench);
                }
                for (var draw : PolymerPonderParrots.draws(
                        PolymerPonderParrots.VARIANTS[b.variant % PolymerPonderParrots.VARIANTS.length],
                        x,
                        y,
                        z,
                        b.yaw,
                        b.pitch,
                        flap,
                        b.tickCount,
                        pose,
                        b.hat,
                        after)) models.add(drawEntry(draw));
            }
            return models;
        }

        private static JsonObject drawEntry(PolymerPonderParrots.Draw draw) {
            JsonObject model = new JsonObject();
            model.addProperty("model", draw.model());
            model.add("m", PolymerPonderScene.matrix(rounded(draw.transform())));
            return model;
        }

        private static float wrap(float degrees) {
            float d = degrees % 360;
            if (d >= 180) d -= 360;
            if (d < -180) d += 360;
            return d;
        }

        private static float angleLerp(float pct, float current, float target) {
            float diff = ((target - current) % 360 + 540) % 360 - 180;
            return current + diff * pct;
        }

        private static double[] triple(JsonArray a) {
            return new double[] {
                a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()
            };
        }

        private static int[] ints(JsonArray a) {
            return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
        }

        private static double[] direction(String name) {
            return switch (name) {
                case "UP" -> new double[] {0, 1, 0};
                case "DOWN" -> new double[] {0, -1, 0};
                case "NORTH" -> new double[] {0, 0, -1};
                case "SOUTH" -> new double[] {0, 0, 1};
                case "WEST" -> new double[] {-1, 0, 0};
                default -> new double[] {1, 0, 0};
            };
        }
    }

    private static void tickBogeyAnimations(CaptureLevel level, List<double[]> animations) {
        for (var iterator = animations.iterator(); iterator.hasNext(); ) {
            double[] a = iterator.next();
            BlockPos pos = new BlockPos((int) a[0], (int) a[1], (int) a[2]);
            a[4]--;
            if (level.getBlockEntity(pos) instanceof AbstractBogeyBlockEntity bogey)
                bogey.animate(a[4] <= 0 ? 0 : (float) a[3]);
            if (a[4] <= 0) iterator.remove();
        }
    }

    private static void tickDeployerAnimations(CaptureLevel level, List<double[]> animations) {
        for (var iterator = animations.iterator(); iterator.hasNext(); ) {
            double[] a = iterator.next();
            BlockPos pos = new BlockPos((int) a[0], (int) a[1], (int) a[2]);
            if (!(level.getBlockEntity(pos) instanceof DeployerBlockEntity deployer)) {
                iterator.remove();
                continue;
            }
            float current = deployer.animatedOffset.getValue(1);
            if (Double.isNaN(a[6])) a[6] = current + a[3];
            a[5]--;
            float next = (float) (a[5] <= 0 ? a[6] : current + a[3] / a[4]);
            deployer.setAnimatedOffset(next);
            if (a[5] <= 0) iterator.remove();
        }
    }

    private static boolean shown(PolymerPonderScene.Definition.Segment segment, int tick) {
        for (var span : segment.spans()) {
            int end = span.hide() == Integer.MAX_VALUE ? Integer.MAX_VALUE : span.hide() + PolymerPonderScene.SLIDE;
            if (span.start() <= tick && tick < end) return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tickBlockEntity(CaptureLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        if (blockEntity == null || !(state.getBlock() instanceof EntityBlock provider)) return;
        blockEntity.setBlockState(state);
        BlockEntityTicker ticker = provider.getTicker(level, state, blockEntity.getType());
        if (ticker != null) ticker.tick(level, pos, state, blockEntity);
    }

    private static BlockState blockState(PolymerPonderScene.Block block) {
        Block type = BuiltInRegistries.BLOCK.getValue(Identifier.parse(block.name()));
        BlockState state = type.defaultBlockState();
        for (var entry : block.properties().entrySet()) {
            Property<?> property = type.getStateDefinition().getProperty(entry.getKey());
            if (property != null) state = with(state, property, entry.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    private static ItemStack stack(JsonObject event) {
        if (!event.has("item") || event.get("item").isJsonNull()) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getValue(
                Identifier.parse(event.get("item").getAsString()));
        return new ItemStack(item, event.has("count") ? event.get("count").getAsInt() : 1);
    }

    private static BlockPos position(JsonObject event, String key) {
        JsonArray p = event.getAsJsonArray(key);
        return new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
    }

    private static void apply(
            CaptureLevel level,
            JsonObject event,
            Map<String, List<TransportedItemStack>> beltLinks,
            Map<String, Entity> entityLinks) {
        String action = event.get("action").getAsString();
        switch (action) {
            case "createItemOnBelt" -> {
                BlockPos pos = position(event, "position");
                if (!(level.getBlockEntity(pos) instanceof BeltBlockEntity belt)) return;
                Direction side = Direction.valueOf(event.get("side").getAsString());
                DirectBeltInputBehaviour behaviour = belt.getBehaviour(DirectBeltInputBehaviour.TYPE);
                behaviour.handleInsertion(stack(event), side.getOpposite(), false);
                BeltBlockEntity controller = belt.getControllerBE();
                if (controller != null) controller.tick();
                List<TransportedItemStack> linked = new ArrayList<>();
                TransportedItemStackHandlerBehaviour transporter =
                        belt.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE);
                transporter.handleProcessingOnAllItems(tis -> {
                    linked.add(tis);
                    return TransportedResult.doNothing();
                });
                beltLinks.put(event.get("link").getAsString(), linked);
            }
            case "createItemOnBeltLike" -> {
                BlockPos pos = position(event, "position");
                DirectBeltInputBehaviour behaviour =
                        BlockEntityBehaviour.get(level, pos, DirectBeltInputBehaviour.TYPE);
                if (behaviour != null)
                    behaviour.handleInsertion(
                            stack(event),
                            Direction.valueOf(event.get("side").getAsString()).getOpposite(),
                            false);
            }
            case "removeItemsFromBelt" -> {
                TransportedItemStackHandlerBehaviour transporter = BlockEntityBehaviour.get(
                        level, position(event, "position"), TransportedItemStackHandlerBehaviour.TYPE);
                if (transporter != null)
                    transporter.handleCenteredProcessingOnAllItems(0.52f, tis -> TransportedResult.removeItem());
            }
            case "stallBeltItem" -> {
                for (TransportedItemStack tis :
                        beltLinks.getOrDefault(event.get("link").getAsString(), List.of()))
                    tis.locked = event.get("stalled").getAsBoolean();
            }
            case "changeBeltItemTo" -> {
                for (TransportedItemStack tis :
                        beltLinks.getOrDefault(event.get("link").getAsString(), List.of())) tis.stack = stack(event);
            }
            case "createItemEntity" -> {
                JsonArray at = event.getAsJsonArray("location"), motion = event.getAsJsonArray("motion");
                ItemEntity entity = new ItemEntity(
                        level,
                        at.get(0).getAsDouble(),
                        at.get(1).getAsDouble(),
                        at.get(2).getAsDouble(),
                        stack(event));
                entity.setDeltaMovement(new Vec3(
                        motion.get(0).getAsDouble(),
                        motion.get(1).getAsDouble(),
                        motion.get(2).getAsDouble()));
                level.addFreshEntity(entity);
                entityLinks.put(event.get("link").getAsString(), entity);
            }
            case "removeEntity" -> {
                Entity entity = entityLinks.get(event.get("link").getAsString());
                if (entity != null) entity.discard();
            }
            case "removeEntities" -> {
                String type = event.get("entity").getAsString();
                for (Entity entity : level.getEntityList())
                    if (type.equals("Entity")
                            || type.equals("ItemEntity") && entity instanceof ItemEntity
                            || entity.getClass().getSimpleName().equals(type)) entity.discard();
            }
            case "entityMotion" -> {
                Entity entity = entityLinks.get(event.get("link").getAsString());
                JsonArray motion = event.getAsJsonArray("motion");
                if (entity != null)
                    entity.setDeltaMovement(new Vec3(
                            motion.get(0).getAsDouble(),
                            motion.get(1).getAsDouble(),
                            motion.get(2).getAsDouble()));
            }
            case "setItems" -> {
                for (Entity entity : level.getEntityList())
                    if (entity instanceof ItemEntity item) item.setItem(stack(event));
            }
            case "removeEntitiesBelow" -> {
                double y = event.get("y").getAsDouble();
                for (Entity entity : level.getEntityList())
                    if (entity instanceof ItemEntity && entity.getY() < y) entity.discard();
            }
            case "setFilterData" -> {
                ItemStack filter = stack(event);
                for (var p : event.getAsJsonArray("positions")) {
                    JsonArray xyz = p.getAsJsonArray();
                    BlockEntity blockEntity = level.getBlockEntity(new BlockPos(
                            xyz.get(0).getAsInt(),
                            xyz.get(1).getAsInt(),
                            xyz.get(2).getAsInt()));
                    if (blockEntity == null
                            || !blockEntity
                                    .getClass()
                                    .getSimpleName()
                                    .equals(event.get("blockEntity").getAsString())) continue;
                    var registries = level.registryAccess();
                    CompoundTag data = blockEntity.saveWithFullMetadata(registries);
                    if (!filter.isEmpty())
                        data.store(
                                "Filter",
                                ItemStack.CODEC,
                                registries.createSerializationContext(NbtOps.INSTANCE),
                                filter);
                    try (var logging = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), Create.LOGGER)) {
                        blockEntity.loadWithComponents(TagValueInput.create(logging, registries, data));
                    }
                }
            }
            case "blockEntity" -> blockEntity(level, event);
            case "packageHop" -> {
                BlockPos pos = position(event, "position");
                Direction side = Direction.valueOf(event.get("side").getAsString());
                int offsetX = side.getStepX(), offsetZ = side.getStepZ();
                var entity = new PackageEntity(
                        level,
                        pos.getX() + 0.5 + offsetX * 0.675,
                        pos.getY() + 0.875,
                        pos.getZ() + 0.5 + offsetZ * 0.675);
                entity.setDeltaMovement(new Vec3(offsetX, 1.0f, offsetZ).scale(0.125f));
                entity.box = stack(event);
                level.addFreshEntity(entity);
                entityLinks.put(event.get("link").getAsString(), entity);
            }
            case "createEntity" -> {
                Operator factory = new Operator(level, level);
                factory.run(event.getAsJsonArray("body"));
                if (!(factory.result instanceof Entity entity))
                    throw new IllegalStateException("createEntity factory returned " + factory.result);
                level.addFreshEntity(entity);
                entityLinks.put(event.get("link").getAsString(), entity);
            }
            case "entityOperator" -> {
                Entity entity = entityLinks.get(event.get("link").getAsString());
                if (entity != null) new Operator(level, entity).run(event.getAsJsonArray("body"));
            }
            case "chainBoxTransfer" -> {
                if (!(level.getBlockEntity(position(event, "position")) instanceof ChainConveyorBlockEntity be)) return;
                BlockPos offset = position(event, "to").subtract(position(event, "origin"));
                for (var iterator = be.getLoopingPackages().iterator(); iterator.hasNext(); ) {
                    var box = iterator.next();
                    box.chainPosition = 0;
                    be.addTravellingPackage(box, offset);
                    iterator.remove();
                }
            }
            case "connectCrafters" ->
                ConnectedInputHandler.toggleConnection(level, position(event, "first"), position(event, "second"));
            default -> throw new IllegalArgumentException("Unknown Ponder capture action " + action);
        }
    }

    private static void blockEntity(CaptureLevel level, JsonObject event) {
        Class<?> type = type(event.getAsJsonArray("type"));
        boolean nbt = event.get("nbt").getAsBoolean();
        for (var p : event.getAsJsonArray("positions")) {
            JsonArray xyz = p.getAsJsonArray();
            BlockEntity blockEntity = level.getBlockEntity(new BlockPos(
                    xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
            if (!type.isInstance(blockEntity)) continue;
            var registries = level.registryAccess();
            if (!nbt) {
                new Operator(level, blockEntity).run(event.getAsJsonArray("body"));
                continue;
            }
            CompoundTag data = blockEntity.saveWithFullMetadata(registries);
            new Operator(level, data).run(event.getAsJsonArray("body"));
            try (var logging = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), Create.LOGGER)) {
                blockEntity.loadWithComponents(TagValueInput.create(logging, registries, data));
            }
        }
    }

    private static Class<?> type(JsonArray candidates) {
        for (var candidate : candidates) {
            String name = candidate.getAsString();
            while (true) {
                try {
                    return Class.forName(name, false, PolymerPonderCapture.class.getClassLoader());
                } catch (ClassNotFoundException ignored) {
                }
                int dot = name.lastIndexOf('.');
                if (dot < 0 || !Character.isUpperCase(name.charAt(dot + 1)) || name.lastIndexOf('.', dot - 1) < 0)
                    break;
                name = name.substring(0, dot) + '$' + name.substring(dot + 1);
            }
        }
        throw new IllegalArgumentException("No class among " + candidates);
    }

    private static final class Operator {
        private final CaptureLevel level;
        private final Object parameter;
        private final Map<String, Object> locals = new HashMap<>();
        Object result;

        Operator(CaptureLevel level, Object parameter) {
            this.level = level;
            this.parameter = parameter;
        }

        boolean run(JsonArray body) {
            for (var element : body) {
                JsonObject statement = element.getAsJsonObject();
                switch (statement.get("s").getAsString()) {
                    case "return" -> {
                        if (statement.has("e")) result = eval(statement.getAsJsonObject("e"));
                        return false;
                    }
                    case "if" -> {
                        boolean taken = (Boolean) eval(statement.getAsJsonObject("c"));
                        if (!run(statement.getAsJsonArray(taken ? "t" : "f"))) return false;
                    }
                    case "local" -> locals.put(statement.get("n").getAsString(), eval(statement.getAsJsonObject("e")));
                    default -> eval(statement.getAsJsonObject("e"));
                }
            }
            return true;
        }

        private Object[] args(JsonObject node) {
            JsonArray array = node.getAsJsonArray("a");
            Object[] values = new Object[array.size()];
            for (int i = 0; i < values.length; i++)
                values[i] = eval(array.get(i).getAsJsonObject());
            return values;
        }

        Object eval(JsonObject node) {
            try {
                return switch (node.get("k").getAsString()) {
                    case "param" -> parameter;
                    case "local" -> locals.get(node.get("n").getAsString());
                    case "ops" -> level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                    case "lit" -> literal(node);
                    case "item" -> stack(node);
                    case "pos" -> {
                        JsonArray v = node.getAsJsonArray("v");
                        yield new BlockPos(
                                v.get(0).getAsInt(),
                                v.get(1).getAsInt(),
                                v.get(2).getAsInt());
                    }
                    case "vec" -> {
                        JsonArray v = node.getAsJsonArray("v");
                        yield new Vec3(
                                v.get(0).getAsDouble(),
                                v.get(1).getAsDouble(),
                                v.get(2).getAsDouble());
                    }
                    case "class" -> type(node.getAsJsonArray("c"));
                    case "static" ->
                        type(node.getAsJsonArray("c"))
                                .getField(node.get("n").getAsString())
                                .get(null);
                    case "get" -> {
                        Object owner = eval(node.getAsJsonObject("o"));
                        yield owner.getClass()
                                .getField(node.get("n").getAsString())
                                .get(owner);
                    }
                    case "set" -> {
                        Object owner = eval(node.getAsJsonObject("o"));
                        var field = owner.getClass().getField(node.get("n").getAsString());
                        Object value = coerce(eval(node.getAsJsonObject("v")), field.getType());
                        field.set(owner, value);
                        yield value;
                    }
                    case "setl" -> {
                        Object value = eval(node.getAsJsonObject("v"));
                        locals.put(node.get("n").getAsString(), value);
                        yield value;
                    }
                    case "call" -> {
                        Object owner = eval(node.getAsJsonObject("o"));
                        yield invoke(owner.getClass(), owner, node.get("n").getAsString(), args(node));
                    }
                    case "scall" ->
                        invoke(
                                type(node.getAsJsonArray("c")),
                                null,
                                node.get("n").getAsString(),
                                args(node));
                    case "new" -> construct(type(node.getAsJsonArray("c")), args(node));
                    case "neg" -> negate(eval(node.getAsJsonObject("v")));
                    case "not" -> !(Boolean) eval(node.getAsJsonObject("v"));
                    case "isnull" ->
                        (eval(node.getAsJsonObject("v")) == null)
                                != node.get("neg").getAsBoolean();
                    case "cast" ->
                        coerce(
                                eval(node.getAsJsonObject("v")),
                                primitive(node.get("t").getAsString()));
                    case "bin" ->
                        binary(
                                node.get("op").getAsString(),
                                eval(node.getAsJsonObject("a")),
                                eval(node.getAsJsonObject("b")));
                    default -> throw new IllegalArgumentException("Unknown operator node " + node);
                };
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Ponder capture operator failed at " + node, e);
            }
        }

        private static Object literal(JsonObject node) {
            var v = node.get("v");
            return switch (node.get("t").getAsString()) {
                case "int" -> v.getAsInt();
                case "long" -> v.getAsLong();
                case "float" -> v.getAsFloat();
                case "double" -> v.getAsDouble();
                case "boolean" -> v.getAsBoolean();
                case "char" -> v.getAsString().charAt(0);
                case "null" -> null;
                default -> v.getAsString();
            };
        }

        private static Class<?> primitive(String name) {
            return switch (name) {
                case "int" -> int.class;
                case "long" -> long.class;
                case "float" -> float.class;
                default -> double.class;
            };
        }

        private static Object negate(Object value) {
            if (value instanceof Integer i) return -i;
            if (value instanceof Long l) return -l;
            if (value instanceof Float f) return -f;
            return -((Number) value).doubleValue();
        }

        private static Object binary(String op, Object a, Object b) {
            if (op.equals("PLUS") && (a instanceof String || b instanceof String)) return String.valueOf(a) + b;
            Number x = (Number) a, y = (Number) b;
            if (x instanceof Double || y instanceof Double || x instanceof Float || y instanceof Float) {
                double l = x.doubleValue(), r = y.doubleValue();
                double result = switch (op) {
                    case "PLUS" -> l + r;
                    case "MINUS" -> l - r;
                    case "MULTIPLY" -> l * r;
                    default -> l / r;
                };
                return x instanceof Double || y instanceof Double ? (Object) result : (Object) (float) result;
            }
            long l = x.longValue(), r = y.longValue();
            long result = switch (op) {
                case "PLUS" -> l + r;
                case "MINUS" -> l - r;
                case "MULTIPLY" -> l * r;
                default -> l / r;
            };
            return x instanceof Long || y instanceof Long ? (Object) result : (Object) (int) result;
        }

        private static int cost(Class<?> parameter, Object value) {
            if (value == null) return parameter.isPrimitive() ? -1 : 1;
            Class<?> boxed = box(parameter);
            if (boxed.isInstance(value)) return 0;
            if (value instanceof Number && Number.class.isAssignableFrom(boxed)
                    || value instanceof Character && boxed == Integer.class) return 2;
            return -1;
        }

        private static Object coerce(Object value, Class<?> parameter) {
            if (value == null || box(parameter).isInstance(value)) return value;
            Number number = value instanceof Character c ? (Number) (int) c : (Number) value;
            Class<?> boxed = box(parameter);
            if (boxed == Integer.class) return number.intValue();
            if (boxed == Long.class) return number.longValue();
            if (boxed == Float.class) return number.floatValue();
            if (boxed == Double.class) return number.doubleValue();
            if (boxed == Short.class) return number.shortValue();
            if (boxed == Byte.class) return number.byteValue();
            return value;
        }

        private static Class<?> box(Class<?> type) {
            if (!type.isPrimitive()) return type;
            if (type == int.class) return Integer.class;
            if (type == long.class) return Long.class;
            if (type == float.class) return Float.class;
            if (type == double.class) return Double.class;
            if (type == boolean.class) return Boolean.class;
            if (type == short.class) return Short.class;
            if (type == byte.class) return Byte.class;
            if (type == char.class) return Character.class;
            return Void.class;
        }

        private static Object invoke(Class<?> owner, Object target, String name, Object[] args)
                throws ReflectiveOperationException {
            Method best = null;
            int bestCost = Integer.MAX_VALUE;
            boolean ambiguous = false;
            for (var method : owner.getMethods()) {
                if (!method.getName().equals(name)
                        || method.getParameterCount() != args.length
                        || method.isVarArgs()
                        || method.isBridge()) continue;
                if ((target == null) != Modifier.isStatic(method.getModifiers())) continue;
                int total = 0;
                for (int i = 0; i < args.length && total >= 0; i++) {
                    int c = cost(method.getParameterTypes()[i], args[i]);
                    total = c < 0 ? -1 : total + c;
                }
                if (total < 0) continue;
                if (total < bestCost) {
                    best = method;
                    bestCost = total;
                    ambiguous = false;
                } else if (total == bestCost && !Arrays.equals(best.getParameterTypes(), method.getParameterTypes()))
                    ambiguous = true;
            }
            if (best == null || ambiguous)
                throw new NoSuchMethodException(
                        (ambiguous ? "Ambiguous " : "No ") + owner.getName() + "#" + name + Arrays.toString(args));
            Object[] converted = new Object[args.length];
            for (int i = 0; i < args.length; i++) converted[i] = coerce(args[i], best.getParameterTypes()[i]);
            best.setAccessible(true);
            return best.invoke(target, converted);
        }

        private static Object construct(Class<?> type, Object[] args) throws ReflectiveOperationException {
            for (var constructor : type.getConstructors()) {
                if (constructor.getParameterCount() != args.length || constructor.isVarArgs()) continue;
                boolean fits = true;
                for (int i = 0; i < args.length && fits; i++)
                    fits = cost(constructor.getParameterTypes()[i], args[i]) >= 0;
                if (!fits) continue;
                Object[] converted = new Object[args.length];
                for (int i = 0; i < args.length; i++)
                    converted[i] = coerce(args[i], constructor.getParameterTypes()[i]);
                return constructor.newInstance(converted);
            }
            throw new NoSuchMethodException("No constructor " + type.getName() + Arrays.toString(args));
        }
    }

    private static JsonArray record(
            CaptureLevel level, List<PolymerPonderScene.Definition.Segment> segments, int tick) {
        JsonArray items = new JsonArray();
        Set<BlockPos> seen = new HashSet<>();
        for (var segment : segments) {
            if (tick < segment.from() || tick >= segment.until() || !shown(segment, tick)) continue;
            BlockPos pos = new BlockPos(
                    segment.original().x(),
                    segment.original().y(),
                    segment.original().z());
            if (!seen.add(pos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) continue;
            if (blockEntity instanceof ChuteBlockEntity chute) chuteItem(chute, pos, items);
            for (var part : PolymerBlockParts.textParts(blockEntity, tick)) {
                String text = part.text().getString();
                if (text.isBlank()) continue;
                JsonObject item = new JsonObject();
                item.addProperty("text", text);
                var color = part.text().getStyle().getColor();
                item.addProperty("color", color == null ? 0xFFFFFF : color.getValue());
                item.add(
                        "m",
                        PolymerPonderScene.matrix(new Matrix4f()
                                .translation(pos.getX(), pos.getY(), pos.getZ())
                                .mul(part.transform())));
                items.add(item);
            }
            for (var part : PolymerBlockParts.itemParts(blockEntity, tick)) {
                JsonObject item = new JsonObject();
                item.addProperty(
                        "item",
                        BuiltInRegistries.ITEM.getKey(part.item().getItem()).toString());
                item.addProperty("ctx", part.context().getSerializedName());
                item.add(
                        "m",
                        PolymerPonderScene.matrix(new Matrix4f()
                                .translation(pos.getX(), pos.getY(), pos.getZ())
                                .mul(part.transform())));
                items.add(item);
            }
        }
        for (Entity entity : level.getEntityList()) {
            if (entity instanceof PackageEntity box) {
                packageEntity(box, items);
                continue;
            }
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                mob(living, items);
                continue;
            }
            if (!(entity instanceof ItemEntity itemEntity)
                    || itemEntity.getItem().isEmpty()) continue;
            JsonObject item = new JsonObject();
            item.addProperty(
                    "item",
                    BuiltInRegistries.ITEM
                            .getKey(itemEntity.getItem().getItem())
                            .toString());
            item.addProperty("count", itemEntity.getItem().getCount());
            item.addProperty("ctx", "ground");
            JsonArray at = new JsonArray();
            at.add(round(itemEntity.getX()));
            at.add(round(itemEntity.getY()));
            at.add(round(itemEntity.getZ()));
            item.add("p", at);
            item.addProperty("age", itemEntity.getAge());
            item.addProperty("bob", itemEntity.bobOffs);
            items.add(item);
        }
        return items;
    }

    private static JsonArray recordParts(
            CaptureLevel level, List<PolymerPonderScene.Definition.Segment> segments, int tick, float time) {
        JsonArray parts = new JsonArray();
        Set<BlockPos> seen = new HashSet<>();
        for (var segment : segments) {
            if (tick < segment.from() || tick >= segment.until() || !shown(segment, tick)) continue;
            BlockPos pos = new BlockPos(
                    segment.original().x(),
                    segment.original().y(),
                    segment.original().z());
            if (!seen.add(pos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || sceneRendered(blockEntity.getBlockState())) continue;
            if (blockEntity instanceof StandardBogeyBlockEntity bogey) {
                for (var part : PolymerRailVisuals.ponderParts(bogey))
                    parts.add(partEntry(part.model(), pos, part.pose()));
                continue;
            }
            for (var part : PolymerBlockParts.modelParts(blockEntity, time))
                parts.add(partEntry(part.model(), pos, part.transform()));
            if (blockEntity instanceof ChainConveyorBlockEntity chain)
                for (var part : PolymerChainConveyors.ponderParts(chain, tick))
                    parts.add(partEntry(part.model(), pos, part.transform()));
        }
        return parts;
    }

    private static JsonObject partEntry(Identifier model, BlockPos pos, Matrix4f transform) {
        JsonObject entry = new JsonObject();
        entry.addProperty("model", model.toString());
        JsonArray at = new JsonArray();
        at.add(pos.getX());
        at.add(pos.getY());
        at.add(pos.getZ());
        entry.add("at", at);
        entry.add("m", PolymerPonderScene.matrix(rounded(transform)));
        return entry;
    }

    private static Matrix4f rounded(Matrix4f m) {
        float[] values = m.get(new float[16]);
        for (int i = 0; i < 16; i++) values[i] = Math.round(values[i] * 1e5f) / 1e5f + 0f;
        return new Matrix4f().set(values);
    }

    private static void packageEntity(PackageEntity entity, JsonArray items) {
        if (!(entity.box.getItem() instanceof PackageItem box)) return;
        float yaw = entity.getYRot();
        Matrix4f pose = new Matrix4f()
                .translation((float) entity.getX(), (float) entity.getY(), (float) entity.getZ())
                .translate(-0.5f, 0, -0.5f)
                .translate(0.5f, 0.5f, 0.5f)
                .rotateY((float) Math.toRadians(-90 - yaw))
                .translate(-0.5f, -0.5f, -0.5f);
        JsonObject item = new JsonObject();
        item.addProperty("item", BuiltInRegistries.ITEM.getKey(box).toString());
        item.addProperty("model", box.style.getModel().toString());
        item.addProperty("ctx", "none");
        item.add("m", PolymerPonderScene.matrix(pose));
        items.add(item);
    }

    private static void mob(LivingEntity living, JsonArray items) {
        float x = (float) living.getX(), y = (float) living.getY(), z = (float) living.getZ();
        float bodyRot = living.yBodyRot,
                headYaw = Mth.wrapDegrees(living.yHeadRot - living.yBodyRot),
                pitch = living.getXRot();
        List<PolymerPonderMobs.Draw> draws;
        if (living instanceof Blaze) {
            draws = PolymerPonderMobs.blaze(x, y, z, bodyRot, headYaw, pitch, living.tickCount);
        } else if (living instanceof Sheep sheep) {
            DyeColor dye = sheep.getColor();
            int c = dye.getTextureDiffuseColor();
            int color = dye == DyeColor.WHITE
                    ? -1644826
                    : 0xFF000000 | channel(c >> 16) << 16 | channel(c >> 8) << 8 | channel(c);
            draws = PolymerPonderMobs.sheep(
                    x,
                    y,
                    z,
                    bodyRot,
                    headYaw,
                    pitch,
                    living.walkAnimation.position(0),
                    living.walkAnimation.speed(0),
                    sheep.getHeadEatPositionScale(0),
                    sheep.getHeadEatAngleScale(0),
                    color,
                    dye == DyeColor.WHITE,
                    sheep.isSheared());
        } else throw new IllegalStateException("No Ponder model for " + living.getType());
        for (var draw : draws) {
            JsonObject entry = new JsonObject();
            entry.addProperty("model", draw.model());
            entry.addProperty("ctx", "none");
            entry.add("m", PolymerPonderScene.matrix(draw.transform()));
            if (draw.tint() != -1) entry.addProperty("tint", draw.tint());
            items.add(entry);
        }
    }

    private static int channel(int value) {
        return Math.clamp((int) Math.floor((value & 255) * 0.75f), 0, 255);
    }

    private static void chuteItem(ChuteBlockEntity chute, BlockPos pos, JsonArray items) {
        ItemStack stack = chute.getItem();
        BlockState state = chute.getBlockState();
        float position = chute.itemPosition.getValue();
        if (stack.isEmpty()) return;
        if (chute instanceof SmartChuteBlockEntity) {
            if (position > 0) return;
        } else {
            var shape = state.getValue(ChuteBlock.SHAPE);
            boolean window = shape == ChuteBlock.Shape.WINDOW;
            if (state.getValue(ChuteBlock.FACING) != Direction.DOWN || !window && chute.bottomPullDistance == 0) return;
            if (position > 0.5f && !window) return;
        }
        Matrix4f pose =
                new Matrix4f().translation(pos.getX(), pos.getY(), pos.getZ()).translate(0.5f, position, 0.5f);
        if (position != 0 && !PackageItem.isPackage(stack)) {
            float angle = Mth.DEG_TO_RAD * position * 180;
            pose.scale(0.5f).rotateX(angle).rotateY(angle);
        } else pose.scale(1.5f);
        JsonObject item = new JsonObject();
        item.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        item.addProperty("ctx", "fixed");
        item.add("m", PolymerPonderScene.matrix(pose));
        items.add(item);
    }

    private static double round(double v) {
        return Math.round(v * 1e5) / 1e5;
    }

    private static byte[] resource(String path) {
        try (var stream = PolymerPonderCapture.class.getClassLoader().getResourceAsStream(path)) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
