package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.Create;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBedrockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDecorativeCulling;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDisplayCulling;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerContraptionVisuals;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCarry;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftCellLimit;
import com.zurrtum.create.infrastructure.polymer.contraption.PolymerShiftPacer;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PolymerDevSwitches implements ModInitializer {
    @Override
    public void onInitialize() {
        if (off("polymerpatch.bedrockRotors")) PolymerBedrockRotors.ENABLED = false;
        if (off("polymerpatch.bedrockStaticModels")) PolymerBedrockCarriers.ENABLED = false;
        if (off("polymerpatch.bedrockSkipPaperDisplays")) PolymerBedrockCarriers.SKIP_PAPER_DISPLAYS = false;
        if (off("polymerpatch.nativeClients")) PolymerNativeClients.ENABLED = false;
        if (off("polymerpatch.culling.decorations")) PolymerDecorativeCulling.ENABLED = false;
        if (off("polymerpatch.culling.kinetics")) PolymerDisplayCulling.KINETICS_ENABLED = false;
        if (Boolean.getBoolean("polymerpatch.bedrockHolderStats"))
            AdapterHolder.watchStarted = new BedrockHolderStats();
        if (Boolean.getBoolean("polymerpatch.bedrockPartsDebug")) PolymerBedrockParts.DEBUG = true;
        Integer smooth = Integer.getInteger("polymerpatch.train.smoothTicks");
        if (smooth != null) PolymerContraptionVisuals.TRAIN_SMOOTH = Math.clamp(smooth, 1, 3);
        Integer cells = Integer.getInteger("polymerpatch.carry.cellLimit");
        if (cells != null) PolymerShiftCellLimit.DEFAULT = Math.clamp(cells, 32, PolymerShiftCarry.MAX_CELLS);
        Long spin = Long.getLong("polymerpatch.carry.pacerSpinMicros");
        if (spin != null) PolymerShiftPacer.SPIN = spin * 1_000L;
        Integer png = Integer.getInteger("polymerpatch.pack.pngLevel");
        if (png != null) PolymerPackOptimizer.PNG_LEVEL = Math.clamp(png, 0, 9);
        if (Boolean.getBoolean("polymerpatch.carry.seatedStats")) PolymerShiftCarry.seatedSends = new SeatedStats();
    }

    private static final class SeatedStats implements ObjIntConsumer<UUID> {
        private static final Logger LOGGER = LoggerFactory.getLogger("polymerpatch-seated");
        private final Map<UUID, long[]> counts = new ConcurrentHashMap<>();

        @Override
        public void accept(UUID player, int kind) {
            long now = System.nanoTime();
            long[] c = counts.computeIfAbsent(player, ignored -> new long[] {0, 0, 0, now});
            synchronized (c) {
                c[kind]++;
                if (now - c[3] < 1_000_000_000L) return;
                LOGGER.info(
                        "Seated sends {}: with seat {}, lane fallback {}, seat turn via lane {}",
                        player,
                        c[0],
                        c[1],
                        c[2]);
                c[0] = c[1] = c[2] = 0;
                c[3] = now;
            }
        }
    }

    private static final class BedrockHolderStats implements BiConsumer<AdapterHolder, ServerGamePacketListenerImpl> {
        private final Map<String, AtomicLong[]> watches = new ConcurrentHashMap<>();
        private final AtomicLong total = new AtomicLong();

        @Override
        public void accept(AdapterHolder holder, ServerGamePacketListenerImpl player) {
            if (!PolymerBedrockCarriers.isBedrock(player.player.getUUID())) return;
            AtomicLong[] c = watches.computeIfAbsent(
                    holder.getClass().getName(), k -> new AtomicLong[] {new AtomicLong(), new AtomicLong()});
            c[0].incrementAndGet();
            c[1].addAndGet(holder.getElements().size());
            if (total.incrementAndGet() % 300 != 0) return;
            StringBuilder out = new StringBuilder("Bedrock holder watches (holders/elements):");
            watches.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue()[1].get(), a.getValue()[1].get()))
                    .forEach(e -> out.append("\n  ")
                            .append(e.getKey())
                            .append(' ')
                            .append(e.getValue()[0].get())
                            .append('/')
                            .append(e.getValue()[1].get()));
            Create.LOGGER.info(out.toString());
        }
    }

    private static boolean off(String key) {
        return "false".equalsIgnoreCase(System.getProperty(key));
    }
}
