package dev.veeda.createpoly.harness;

import com.mojang.brigadier.StringReader;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

// Kept outside com.zurrtum.create.infrastructure.polymer: NativePresentationPolicy.adapterPacket drops packets to
// native clients whose first Create frame is an adapter class, which hid this harness's teleports, break events and
// particles from native clients (Nov 9 run).
public final class PolymerParticleHarness implements ModInitializer {
    private static final BlockPos STAGE = new BlockPos(9000, 129, 9000);
    private static final int PLACE_TICKS = 4, SHOT_TICKS = 5, ACK_TIMEOUT = 200;
    private static final List<String> COPYCAT_MATERIALS = List.of(
            "minecraft:stone",
            "minecraft:oak_planks",
            "minecraft:glass",
            "minecraft:gold_block",
            "minecraft:copper_block");
    private static final Map<String, String> TEMPLATES = Map.ofEntries(
            Map.entry("minecraft:block", "{block_state:\"minecraft:stone\"}"),
            Map.entry("minecraft:block_marker", "{block_state:\"minecraft:stone\"}"),
            Map.entry("minecraft:falling_dust", "{block_state:\"minecraft:sand\"}"),
            Map.entry("minecraft:dust_pillar", "{block_state:\"minecraft:stone\"}"),
            Map.entry("minecraft:block_crumble", "{block_state:\"minecraft:stone\"}"),
            Map.entry("minecraft:item", "{item:\"minecraft:apple\"}"),
            Map.entry("minecraft:dust", "{color:[1.0,0.2,0.2],scale:1.5}"),
            Map.entry("minecraft:dust_color_transition", "{from_color:[1.0,0.2,0.2],to_color:[0.2,0.2,1.0],scale:1.5}"),
            Map.entry("minecraft:entity_effect", "{color:[0.4,0.8,0.2,1.0]}"),
            Map.entry("minecraft:tinted_leaves", "{color:[0.4,0.8,0.2,1.0]}"),
            Map.entry("minecraft:flash", "{color:[1.0,1.0,1.0,1.0]}"),
            Map.entry("minecraft:sculk_charge", "{roll:0.0}"),
            Map.entry("minecraft:shriek", "{delay:0}"),
            Map.entry("minecraft:dragon_breath", "{power:1.0}"),
            Map.entry("minecraft:effect", "{color:[0.4,0.8,0.2],power:1.0}"),
            Map.entry("minecraft:instant_effect", "{color:[0.4,0.8,0.2],power:1.0}"),
            Map.entry("minecraft:trail", "{target:[9000.5,131.0,9001.5],color:-16711936,duration:30}"),
            Map.entry("minecraft:vibration", "{destination:{type:\"block\",pos:[9000,131,9002]},arrival_in_ticks:20}"));

    private static final Set<String> SKIP_BLOCKS = Set.of(
            "minecraft:air",
            "minecraft:cave_air",
            "minecraft:void_air",
            "minecraft:moving_piston",
            "minecraft:structure_void");

    private record Shot(String name, Runnable setup, Runnable fire) {}

    private static Run run;

    private static final class Run {
        final ServerPlayer player;
        final List<Shot> shots;
        final Path dir;
        int index = -1, phase, wait, warmup = 40;
        boolean waitingAck;

        Run(ServerPlayer player, List<Shot> shots, Path dir) {
            this.player = player;
            this.shots = shots;
            this.dir = dir;
        }
    }

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(PolymerParticleHarness::tick);
    }

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("polymerpatch").resolve("particle_harness");
    }

    private static void tick(MinecraftServer server) {
        try {
            Path dir = dir(server);
            if (run == null) {
                if (server.getTickCount() % 20 != 0 || !Files.exists(dir.resolve("run.txt"))) return;
                String[] args = Files.readString(dir.resolve("run.txt"), StandardCharsets.UTF_8)
                        .trim()
                        .split("\\s+");
                Files.delete(dir.resolve("run.txt"));
                start(server, dir, args);
                return;
            }
            if (Files.exists(dir.resolve("stop.txt")) || run.player.hasDisconnected()) {
                Files.deleteIfExists(dir.resolve("stop.txt"));
                finish("stopped at " + run.index);
                return;
            }
            step(server);
        } catch (Exception failure) {
            Create.LOGGER.warn("[particle-harness] tick failed", failure);
        }
    }

    private static void start(MinecraftServer server, Path dir, String[] args) throws Exception {
        ServerPlayer player = server.getPlayerList().getPlayerByName(args[0]);
        if (player == null) {
            Create.LOGGER.warn("[particle-harness] no player {}", args[0]);
            return;
        }
        String mode = args.length > 1 ? args[1] : "all";
        String filter = args.length > 2 ? args[2] : "";
        ServerLevel level = server.overworld();
        List<Shot> shots = new ArrayList<>();
        if (mode.equals("blocks") || mode.equals("all")) blockShots(level, filter, shots);
        if (mode.equals("copycats") || mode.equals("all")) copycatShots(level, filter, shots);
        if (mode.equals("particles") || mode.equals("all")) particleShots(level, player, filter, shots);
        for (String file : List.of("ready.txt", "ack.txt", "done.txt")) Files.deleteIfExists(dir.resolve(file));
        stage(level);
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, STAGE.getX() + .5, STAGE.getY() + .2, STAGE.getZ() - 2.1, Set.of(), 0, 22, true);
        run = new Run(player, shots, dir);
        Files.writeString(
                dir.resolve("plan.txt"),
                String.join("\n", shots.stream().map(Shot::name).toList()) + "\n",
                StandardCharsets.UTF_8);
        Create.LOGGER.info(
                "[particle-harness] start {} {} shots for {}",
                mode,
                shots.size(),
                player.getGameProfile().name());
    }

    private static void stage(ServerLevel level) {
        level.getChunk(STAGE);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 5; z++)
                for (int y = -1; y <= 5; y++) {
                    BlockPos pos = STAGE.offset(x, y, z);
                    BlockState state = y == -1
                            ? Blocks.SMOOTH_STONE.defaultBlockState()
                            : z == 5
                                    ? BuiltInRegistries.BLOCK
                                            .getValue(Identifier.parse("minecraft:light_gray_concrete"))
                                            .defaultBlockState()
                                    : Blocks.AIR.defaultBlockState();
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
    }

    private static void step(MinecraftServer server) throws Exception {
        Run r = run;
        if (r.warmup > 0) {
            r.warmup--;
            return;
        }
        if (r.waitingAck) {
            Path ack = r.dir.resolve("ack.txt");
            boolean acked = Files.exists(ack)
                    && Files.readString(ack, StandardCharsets.UTF_8).trim().equals(Integer.toString(r.index));
            if (!acked && --r.wait > 0) return;
            if (!acked)
                Create.LOGGER.warn(
                        "[particle-harness] no ack for {} {}",
                        r.index,
                        r.shots.get(r.index).name());
            r.waitingAck = false;
        }
        if (r.phase == 0) {
            r.index++;
            if (r.index >= r.shots.size()) {
                finish(r.shots.size() + " shots");
                return;
            }
            ServerLevel level = server.overworld();
            level.setBlock(STAGE, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            r.player.teleportTo(level, STAGE.getX() + .5, STAGE.getY() + .2, STAGE.getZ() - 2.1, Set.of(), 0, 22, true);
            safe(r.shots.get(r.index).setup(), r.shots.get(r.index).name());
            r.phase = 1;
            r.wait = PLACE_TICKS;
        } else if (r.phase == 1) {
            if (--r.wait > 0) return;
            safe(r.shots.get(r.index).fire(), r.shots.get(r.index).name());
            r.phase = 2;
            r.wait = SHOT_TICKS;
        } else {
            if (--r.wait > 0) return;
            Files.writeString(
                    r.dir.resolve("ready.txt"),
                    r.index + " " + r.shots.get(r.index).name(),
                    StandardCharsets.UTF_8);
            r.phase = 0;
            r.waitingAck = true;
            r.wait = ACK_TIMEOUT;
        }
    }

    private static void safe(Runnable action, String name) {
        try {
            action.run();
        } catch (Throwable failure) {
            Create.LOGGER.warn("[particle-harness] {} failed: {}", name, failure.toString());
        }
    }

    private static void finish(String summary) throws Exception {
        Files.writeString(run.dir.resolve("done.txt"), summary + "\n", StandardCharsets.UTF_8);
        Create.LOGGER.info("[particle-harness] done: {}", summary);
        run = null;
    }

    private static void blockShots(ServerLevel level, String filter, List<Shot> shots) {
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (SKIP_BLOCKS.contains(id) || !id.contains(filter) || id.contains("copycat")) continue;
            BlockState state = block.defaultBlockState();
            shots.add(new Shot(
                    "block_" + id.replace(':', '.'),
                    () -> place(level, state),
                    () -> level.destroyBlock(STAGE, false)));
        }
    }

    private static void copycatShots(ServerLevel level, String filter, List<Shot> shots) {
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!id.startsWith("create:copycat") || !id.contains(filter)) continue;
            shots.add(new Shot(
                    "copycat_" + id.replace(':', '.') + "__empty",
                    () -> place(level, block.defaultBlockState()),
                    () -> level.destroyBlock(STAGE, false)));
            for (String material : COPYCAT_MATERIALS) {
                BlockState materialState = BuiltInRegistries.BLOCK
                        .getValue(Identifier.parse(material))
                        .defaultBlockState();
                shots.add(new Shot(
                        "copycat_" + id.replace(':', '.') + "__" + material.replace(':', '.'),
                        () -> {
                            place(level, block.defaultBlockState());
                            if (level.getBlockEntity(STAGE) instanceof CopycatBlockEntity copycat)
                                copycat.setMaterial(materialState);
                        },
                        () -> level.destroyBlock(STAGE, false)));
            }
        }
    }

    private static void particleShots(ServerLevel level, ServerPlayer player, String filter, List<Shot> shots) {
        for (ParticleType<?> type : BuiltInRegistries.PARTICLE_TYPE) {
            String id = BuiltInRegistries.PARTICLE_TYPE.getKey(type).toString();
            if (!id.contains(filter)) continue;
            ParticleOptions options;
            try {
                options = type instanceof SimpleParticleType simple
                        ? simple
                        : ParticleArgument.readParticle(
                                new StringReader(id + TEMPLATES.getOrDefault(id, "")), level.registryAccess());
            } catch (Exception failure) {
                Create.LOGGER.warn("[particle-harness] skip particle {}: {}", id, failure.getMessage());
                continue;
            }
            ParticleOptions chosen = options;
            shots.add(new Shot(
                    "particle_" + id.replace(':', '.'),
                    () -> {},
                    () -> level.sendParticles(
                            player,
                            chosen,
                            true,
                            true,
                            STAGE.getX() + .5,
                            STAGE.getY() + .6,
                            STAGE.getZ() + .5,
                            40,
                            .4,
                            .4,
                            .4,
                            .02)));
        }
    }

    private static void place(ServerLevel level, BlockState state) {
        level.setBlock(STAGE, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
