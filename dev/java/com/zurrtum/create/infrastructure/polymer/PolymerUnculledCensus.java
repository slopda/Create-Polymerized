package com.zurrtum.create.infrastructure.polymer;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

public final class PolymerUnculledCensus implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("createfly")
                        .then(Commands.literal("unculled").executes(context -> run(context.getSource())))));
    }

    private static final class Row {
        int count;
        float reach;
    }

    private static int run(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("unculled: run as a player"));
            return 0;
        }
        ServerLevel level = player.level();
        Set<ElementHolder> holders = Collections.newSetFromMap(new IdentityHashMap<>());
        int radius = level.getServer().getPlayerList().getViewDistance() + 1;
        int cx = player.chunkPosition().x(), cz = player.chunkPosition().z();
        for (int x = cx - radius; x <= cx + radius; x++)
            for (int z = cz - radius; z <= cz + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk instanceof HolderAttachmentHolder attached)
                    for (HolderAttachment attachment : new ArrayList<>(attached.polymerVE$getHolders()))
                        holders.add(attachment.holder());
            }
        for (Entity entity : level.getEntities((Entity) null, new AABB(player.blockPosition()).inflate(160), e -> true))
            if (entity instanceof HolderAttachmentHolder attached)
                for (HolderAttachment attachment : new ArrayList<>(attached.polymerVE$getHolders()))
                    holders.add(attachment.holder());
        Map<String, Row> unculled = new HashMap<>();
        int total = 0, open = 0;
        for (ElementHolder holder : holders) {
            if (!holder.getWatchingPlayers().contains(player.connection)) continue;
            String owner = holder.getClass().getName();
            owner = owner.substring(owner.lastIndexOf('.') + 1);
            for (VirtualElement element : holder.getElements()) {
                if (!(element instanceof DisplayElement display) || display.getViewRange() <= 0) continue;
                total++;
                if (display.getDisplayWidth() > 0 && display.getDisplayHeight() > 0) continue;
                open++;
                String model = display instanceof ItemDisplayElement item
                        ? String.valueOf(item.getItem().get(DataComponents.ITEM_MODEL))
                        : display instanceof BlockDisplayElement block
                                ? "block " + block.getBlockState().getBlock()
                                : display instanceof TextDisplayElement
                                        ? "text"
                                        : display.getClass().getSimpleName();
                var scale = display.getScale();
                float reach = display.getTranslation().length()
                        + Math.max(Math.abs(scale.x()), Math.max(Math.abs(scale.y()), Math.abs(scale.z())));
                Row row = unculled.computeIfAbsent(owner + " " + model, key -> new Row());
                row.count++;
                row.reach = Math.max(row.reach, reach);
            }
        }
        List<Map.Entry<String, Row>> rows = new ArrayList<>(unculled.entrySet());
        rows.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));
        StringBuilder out = new StringBuilder(String.format(
                "---- unculled, game tick %d, at %s: %d of %d sent displays have no culling box%n",
                level.getGameTime(), player.blockPosition().toShortString(), open, total));
        for (var row : rows)
            out.append(
                    String.format("%6d  reach<=%5.1f  %s%n", row.getValue().count, row.getValue().reach, row.getKey()));
        Path file = Path.of("logs", "unculled.txt");
        try {
            Files.writeString(file, out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            source.sendFailure(Component.literal("unculled: " + e));
            return 0;
        }
        int unculledCount = open, sent = total;
        source.sendSuccess(
                () -> Component.literal(
                        unculledCount + " of " + sent + " displays unculled; written to " + file.toAbsolutePath()),
                false);
        return 1;
    }
}
