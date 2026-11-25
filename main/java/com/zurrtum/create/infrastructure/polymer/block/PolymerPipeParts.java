package com.zurrtum.create.infrastructure.polymer.block;

import com.zurrtum.create.content.fluids.FluidTransportBehaviour;
import com.zurrtum.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.zurrtum.create.content.fluids.pipes.FluidPipeBlock;
import com.zurrtum.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

final class PolymerPipeParts {
    private static final Map<ComponentPartials, Map<Direction, Identifier>> ATTACHMENTS =
            new EnumMap<>(ComponentPartials.class);
    private static final Identifier CASING = PolymerBlockParts.model("fluid_pipe/casing");

    static {
        for (ComponentPartials part : ComponentPartials.values()) {
            Map<Direction, Identifier> sides = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values())
                sides.put(
                        direction,
                        PolymerBlockParts.model("fluid_pipe/" + part.name().toLowerCase(Locale.ROOT) + "/"
                                + direction.getSerializedName()));
            ATTACHMENTS.put(part, sides);
        }
    }

    static void init() {}

    static void collect(BlockEntity blockEntity, BlockState state, List<PolymerBlockParts.Part> out) {
        if (!(blockEntity instanceof SmartBlockEntity be)) return;
        FluidTransportBehaviour transport = be.getBehaviour(FluidTransportBehaviour.TYPE);
        if (transport == null) return;
        for (Direction side : Direction.values()) {
            var attachment = transport.getRenderedRimAttachment(be.getLevel(), be.getBlockPos(), state, side);
            for (ComponentPartials part : attachment.partials)
                out.add(PolymerBlockParts.part(ATTACHMENTS.get(part).get(side), PolymerBlockParts.identity()));
        }
        if (state.getBlock() instanceof FluidPipeBlock
                && FluidPipeBlock.shouldDrawCasing(be.getLevel(), be.getBlockPos(), state))
            out.add(PolymerBlockParts.part(CASING, PolymerBlockParts.identity()));
        if (state.getBlock() instanceof GlassFluidPipeBlock) streams(transport, out);
    }

    private static void streams(FluidTransportBehaviour transport, List<PolymerBlockParts.Part> out) {
        float r = .1875f;
        for (Direction side : Direction.values()) {
            var flow = transport.getFlow(side);
            if (flow == null || flow.fluid == null || flow.fluid.isEmpty() || flow.progress == null) continue;
            float length = Mth.clamp(flow.progress.getValue(1), 0, 1) * .5f;
            if (length < 1e-3f) continue;
            float from = flow.inbound ? .5f - length : 0, to = flow.inbound ? .5f : length;
            float[] min = {.5f - r, .5f - r, .5f - r}, max = {.5f + r, .5f + r, .5f + r};
            int axis = side.getAxis().ordinal(), sign = side.getAxisDirection().getStep();
            min[axis] = .5f + sign * (sign > 0 ? from : to);
            max[axis] = .5f + sign * (sign > 0 ? to : from);
            Matrix4f box = new Matrix4f()
                    .translate(min[0], min[1], min[2])
                    .scale(max[0] - min[0], max[1] - min[1], max[2] - min[2]);
            out.add(PolymerBlockParts.part(PolymerBasinParts.fluidModel(flow.fluid), box)
                    .identified("pipe:fluid:" + side.getSerializedName()));
        }
    }
}
