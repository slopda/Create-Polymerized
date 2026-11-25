package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.catnip.math.AngleHelper;
import com.zurrtum.create.content.equipment.armor.BacktankBlock;
import com.zurrtum.create.content.equipment.armor.BacktankBlockEntity;
import dev.veeda.createpoly.assets.PackJson;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public final class PolymerBacktankParts {
    private static final float PX = .5f, PY = .40625f, PZ = .6875f;
    private static final Identifier COPPER_SHAFT = PolymerBlockParts.model("polymer/copper_backtank_block_shaft_input"),
            COPPER_COGS = PolymerBlockParts.model("polymer/copper_backtank_block_cogs_pivot"),
            NETHERITE_SHAFT = PolymerBlockParts.model("polymer/netherite_backtank_block_shaft_input"),
            NETHERITE_COGS = PolymerBlockParts.model("polymer/netherite_backtank_block_cogs_pivot");

    public static void addAssets(ResourcePackBuilder builder) {
        float[] shift = {(.5f - PX) * 16, (.5f - PY) * 16, (.5f - PZ) * 16};
        for (String metal : new String[] {"copper", "netherite"}) {
            String path = "assets/create/models/block/polymer/" + metal + "_backtank_block_cogs";
            byte[] data = builder.getDataOrSource(path + ".json");
            if (data == null) throw new IllegalStateException("Missing " + path);
            var model = PackJson.object(data);
            for (var element : model.getAsJsonArray("elements")) {
                var object = element.getAsJsonObject();
                for (String key : new String[] {"from", "to"}) move(object.getAsJsonArray(key), shift);
                if (object.has("rotation") && object.getAsJsonObject("rotation").has("origin"))
                    move(object.getAsJsonObject("rotation").getAsJsonArray("origin"), shift);
            }
            builder.addStringData(path + "_pivot.json", model.toString());
        }
    }

    private static void move(JsonArray vector, float[] shift) {
        for (int i = 0; i < 3; i++)
            vector.set(i, new JsonPrimitive(vector.get(i).getAsFloat() + shift[i]));
    }

    private PolymerBacktankParts() {}

    static void register() {
        PolymerBlockParts.<BacktankBlockEntity>register(
                PolymerBacktankParts::collect, AllBlocks.COPPER_BACKTANK, AllBlocks.NETHERITE_BACKTANK);
    }

    private static void collect(
            BacktankBlockEntity be, BlockState state, float time, List<PolymerBlockParts.Part> out) {
        boolean netherite = state.is(AllBlocks.NETHERITE_BACKTANK);
        float angle = (PolymerBlockParts.progress(be.getSpeed(), time)
                        + PolymerBlockParts.rotationOffset(state, Direction.Axis.Y, be.getBlockPos()))
                % 360;
        Matrix4f shaft = new Matrix4f()
                .translate(.5f, .5f, .5f)
                .rotateY(AngleHelper.rad(angle))
                .translate(-.5f, -.5f, -.5f);
        out.add(PolymerBlockParts.part(netherite ? NETHERITE_SHAFT : COPPER_SHAFT, shaft)
                .identified("backtank:shaft"));
        float yaw = 180 + AngleHelper.horizontalAngle(state.getValue(BacktankBlock.HORIZONTAL_FACING));
        Matrix4f cogs = new Matrix4f()
                .translate(.5f, .5f, .5f)
                .rotateY(AngleHelper.rad(yaw))
                .translate(-.5f, -.5f, -.5f)
                .translate(PX, PY, PZ)
                .rotateX(AngleHelper.rad((float) Math.IEEEremainder((double) be.getSpeed() / 4 * time, 360)))
                .translate(-.5f, -.5f, -.5f);
        out.add(PolymerBlockParts.part(netherite ? NETHERITE_COGS : COPPER_COGS, cogs)
                .identified("backtank:cogs"));
    }
}
