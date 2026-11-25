package com.zurrtum.create.infrastructure.polymer.contraption;

import com.zurrtum.create.content.contraptions.AbstractContraptionEntity;
import com.zurrtum.create.content.contraptions.Contraption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public final class PolymerBedrockContraptions {
    private PolymerBedrockContraptions() {}

    static boolean replaces(StructureBlockInfo info) {
        return false;
    }

    static void tick(AbstractContraptionEntity entity, ServerLevel level, Contraption contraption) {}

    static void remove(AbstractContraptionEntity entity) {}

    static void clear() {}
}
