package com.zurrtum.create.infrastructure.polymer;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

public final class PolymerContraptionRenderSettings {
    public record Setting(int teleport, int transform) {
        public Setting {
            if (teleport < 0 || teleport > 2 || transform < 0 || transform > 2)
                throw new IllegalArgumentException("Interpolation candidates are 0, 1 or 2 ticks");
        }
    }

    private static final Map<AbstractContraptionEntity, Setting> SETTINGS = new IdentityHashMap<>();

    private PolymerContraptionRenderSettings() {}

    public static void configure(AbstractContraptionEntity entity, int teleport, int transform) {
        if (!(entity.level() instanceof ServerLevel world)
                || !world.getServer().isSameThread()
                || world.getServer().getPort() != 25566
                || entity.isRemoved())
            throw new IllegalStateException("Render experiment requires a live lab25566 entity on the server thread");
        SETTINGS.put(entity, new Setting(teleport, transform));
    }

    public static Setting get(AbstractContraptionEntity entity) {
        return SETTINGS.get(entity);
    }

    static void apply(AbstractContraptionEntity entity, DisplayElement display) {
        Setting setting = SETTINGS.get(entity);
        if (setting == null) return;
        if (display.getTeleportDuration() != setting.teleport()) display.setTeleportDuration(setting.teleport());
        if (display.getInterpolationDuration() != setting.transform())
            display.setInterpolationDuration(setting.transform());
    }

    static void remove(AbstractContraptionEntity entity) {
        SETTINGS.remove(entity);
    }

    static void clear() {
        SETTINGS.clear();
    }
}
