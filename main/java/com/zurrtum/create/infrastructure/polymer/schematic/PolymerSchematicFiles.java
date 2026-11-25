package com.zurrtum.create.infrastructure.polymer.schematic;

import com.zurrtum.create.AllDataComponents;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.schematics.SchematicItem;
import com.zurrtum.create.foundation.utility.CreatePaths;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import java.io.IOException;
import java.nio.file.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class PolymerSchematicFiles {
    private PolymerSchematicFiles() {}

    public static ItemStack blueprint(ServerPlayer player, String name) {
        return blueprint(player, player.getGameProfile().name(), name);
    }

    public static ItemStack blueprint(ServerPlayer player, String owner, String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}\\.nbt")) return ItemStack.EMPTY;
        if (owner == null || !owner.matches("[A-Za-z0-9_]{1,16}")) return ItemStack.EMPTY;
        try {
            Path base = CreatePaths.UPLOADED_SCHEMATICS_DIR.toAbsolutePath().normalize();
            Path directory = base.resolve(owner).normalize(),
                    file = directory.resolve(name).normalize();
            long maxBytes = Math.min(
                    8L * 1024 * 1024,
                    AllConfigs.server().schematics.maxTotalSchematicSize.get() * 1000L);
            if (!directory.getParent().equals(base)
                    || !file.getParent().equals(directory)
                    || Files.isSymbolicLink(base)
                    || Files.isSymbolicLink(directory)
                    || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || maxBytes <= 0
                    || Files.size(file) > maxBytes) return ItemStack.EMPTY;
            var tag = NbtIo.readCompressed(file, NbtAccounter.create(32L * 1024 * 1024));
            StructureTemplate template = new StructureTemplate();
            template.load(player.level().holderLookup(Registries.BLOCK), tag);
            var bounds = template.getSize();
            if (bounds.getX() <= 0
                    || bounds.getY() <= 0
                    || bounds.getZ() <= 0
                    || bounds.getX() > 4096
                    || bounds.getY() > 4096
                    || bounds.getZ() > 4096
                    || (long) bounds.getX() * bounds.getY() * bounds.getZ() > 16L * 1024 * 1024) return ItemStack.EMPTY;
            ItemStack blueprint = SchematicItem.create(player.level(), name, owner);
            return bounds.equals(blueprint.get(AllDataComponents.SCHEMATIC_BOUNDS)) ? blueprint : ItemStack.EMPTY;
        } catch (IOException | RuntimeException error) {
            Create.LOGGER.warn(
                    "Could not load staged schematic {} for {}",
                    name,
                    player.getGameProfile().name(),
                    error);
            return ItemStack.EMPTY;
        }
    }
}
