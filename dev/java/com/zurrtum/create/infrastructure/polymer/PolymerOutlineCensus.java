package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerShapeCollision;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerStaticCollision;
import eu.pb4.polymer.core.api.block.BlockMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PolymerOutlineCensus implements ModInitializer {
    private static final double TOLERANCE = 1 / 16.0 + 1e-6;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("outlines").executes(context -> start(context.getSource())))));
    }

    private static int start(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("outlines: run as a vanilla player"));
            return 0;
        }
        int[] result = new int[1];
        BlockMapper mapper = BlockMapper.get(player.connection);
        PacketContext.runWithContext(player.connection, () -> result[0] = run(source, mapper));
        return result[0];
    }

    private static int run(CommandSourceStack source, BlockMapper mapper) {
        List<String> rows = new ArrayList<>();
        rows.add("block,state,client,flag,source_bounds,client_bounds,max_diff_px,shulker_cells");
        Map<String, int[]> perBlock = new TreeMap<>();
        int[] totals = new int[4];
        for (Block block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(Create.MOD_ID)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                totals[3]++;
                AABB sourceBox = bounds(state);
                if (sourceBox == null) continue;
                BlockState client;
                AABB clientBox;
                try {
                    client = mapper.toClientSideState(state, PacketContext.get());
                    clientBox = bounds(client);
                } catch (RuntimeException failed) {
                    rows.add(id + "," + quote(state.toString()) + ",ERROR " + quote(failed.toString()) + ",,,,,");
                    continue;
                }
                int cells = cells(state);
                double diff = clientBox == null ? 16 : diff(sourceBox, clientBox);
                int kind = clientBox == null ? 0 : diff > TOLERANCE ? 1 : cells > 0 ? 2 : -1;
                if (kind < 0) continue;
                perBlock.computeIfAbsent(id.getPath(), k -> new int[3])[kind]++;
                totals[kind]++;
                rows.add(id + "," + quote(state.toString()) + "," + quote(client.toString()) + ","
                        + new String[] {"EMPTY", "BOUNDS", "CELLS"}[kind] + "," + quote(text(sourceBox)) + ","
                        + quote(clientBox == null ? "" : text(clientBox)) + "," + String.format("%.1f", diff * 16) + ","
                        + cells);
            }
        }
        Path out = FabricLoader.getInstance().getGameDir().resolve("logs/outlines.csv");
        try {
            Files.write(out, rows, StandardCharsets.UTF_8);
        } catch (IOException failed) {
            source.sendFailure(Component.literal("outlines: " + failed));
            return 0;
        }
        StringBuilder summary = new StringBuilder();
        perBlock.forEach((block, counts) -> summary.append(block)
                .append(counts[0] > 0 ? " empty " + counts[0] : "")
                .append(counts[1] > 0 ? " off " + counts[1] : "")
                .append(counts[2] > 0 ? " cells " + counts[2] : "")
                .append("; "));
        Create.LOGGER.info(
                "Outline census: {} states, {} empty, {} off by more than 1 px, {} matching but with shulker cells: {}",
                totals[3],
                totals[0],
                totals[1],
                totals[2],
                summary);
        Component message =
                Component.literal("outlines: " + totals[3] + " states, " + totals[0] + " with no client outline, "
                        + totals[1] + " off by >1 px, " + totals[2] + " with shulker cells, in " + perBlock.size()
                        + " blocks; logs/outlines.csv");
        source.sendSuccess(() -> message, false);
        return perBlock.size();
    }

    private static double diff(AABB a, AABB b) {
        return Math.max(
                Math.max(
                        Math.max(Math.abs(a.minX - b.minX), Math.abs(a.maxX - b.maxX)),
                        Math.max(Math.abs(a.minY - b.minY), Math.abs(a.maxY - b.maxY))),
                Math.max(Math.abs(a.minZ - b.minZ), Math.abs(a.maxZ - b.maxZ)));
    }

    @SuppressWarnings("unchecked")
    private static int cells(BlockState state) {
        int count = PolymerStaticCollision.cells(state).size();
        try {
            Field field = PolymerShapeCollision.class.getDeclaredField("cells");
            field.setAccessible(true);
            List<?> shape = ((Map<BlockState, List<?>>) field.get(null)).get(state);
            if (shape != null) count += shape.size();
        } catch (ReflectiveOperationException ignored) {
        }
        return count;
    }

    private static AABB bounds(BlockState state) {
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return shape.isEmpty() ? null : shape.bounds();
    }

    private static String text(AABB box) {
        return String.format(
                "%.1f %.1f %.1f / %.1f %.1f %.1f",
                box.minX * 16, box.minY * 16, box.minZ * 16, box.maxX * 16, box.maxY * 16, box.maxZ * 16);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "'") + "\"";
    }
}
