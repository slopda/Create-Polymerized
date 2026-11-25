package com.zurrtum.create.infrastructure.polymer.block;

import static com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.api.connectivity.ConnectivityHandler;
import com.zurrtum.create.content.fluids.tank.FluidTankBlockEntity;
import com.zurrtum.create.content.logistics.vault.ItemVaultBlock;
import com.zurrtum.create.content.logistics.vault.ItemVaultBlockEntity;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

final class PolymerStorageParts {
    private static final Map<Identifier, JsonObject> MODELS = new LinkedHashMap<>();
    private static final Map<String, Identifier> FLUIDS = new LinkedHashMap<>();
    private static final int[] SQUARE = {10, 6, 9, 5};
    private static final int[] WITHOUT_SINGLE = {10, 14, 6, 11, 15, 7, 9, 13, 5};
    private static final int[] WITHOUT_HORIZONTAL_SINGLE = {10, 14, 6, 11, 15, 7, 9, 13, 5, 8, 12, 4};
    private static final int[] WITHOUT_VERTICAL_MIDDLE = {2, 10, 6, 3, 11, 7, 1, 9, 5, 8, 4};

    static void registerAll() {
        JsonObject original = PolymerBlockProjections.readAssetJson("/assets/create/models/block/item_vault.json");
        JsonObject faces =
                original.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
        for (boolean large : new boolean[] {false, true})
            for (Direction face : Direction.values()) {
                String part = vaultPart(face);
                int size = table(large, part).length + 1;
                for (int i = 0; i < size; i++) {
                    Identifier model = vaultModel(large, part, face, i);
                    String texture = "create:block/vault/vault_" + part
                            + (i == 0 ? "_small" : (large ? "_large/" : "_medium/") + i);
                    JsonObject element = new JsonObject();
                    JsonArray from = new JsonArray();
                    from.add(0);
                    from.add(0);
                    from.add(0);
                    JsonArray to = new JsonArray();
                    to.add(16);
                    to.add(16);
                    to.add(16);
                    element.add("from", from);
                    element.add("to", to);
                    JsonObject faceData =
                            faces.getAsJsonObject(face.getSerializedName()).deepCopy();
                    faceData.addProperty("texture", "#ct");
                    JsonObject onlyFace = new JsonObject();
                    onlyFace.add(face.getSerializedName(), faceData);
                    element.add("faces", onlyFace);
                    JsonArray elements = new JsonArray();
                    elements.add(element);
                    JsonObject textures = new JsonObject();
                    textures.addProperty("ct", texture);
                    JsonObject definition = new JsonObject();
                    definition.add("textures", textures);
                    definition.add("elements", elements);
                    MODELS.put(model, definition);
                }
            }
        for (String fluid : new String[] {"water", "lava", "honey", "chocolate", "milk"}) {
            Identifier model = model("polymer/fluid/" + fluid);
            FLUIDS.put(fluid, model);
            JsonObject element = new JsonObject();
            JsonArray from = new JsonArray();
            from.add(0);
            from.add(0);
            from.add(0);
            JsonArray to = new JsonArray();
            to.add(16);
            to.add(16);
            to.add(16);
            element.add("from", from);
            element.add("to", to);
            JsonObject allFaces = new JsonObject();
            for (Direction face : Direction.values()) {
                JsonObject f = new JsonObject();
                f.addProperty("texture", "#fluid");
                if (fluid.equals("water")) f.addProperty("tintindex", 0);
                allFaces.add(face.getSerializedName(), f);
            }
            element.add("faces", allFaces);
            JsonArray elements = new JsonArray();
            elements.add(element);
            JsonObject textures = new JsonObject();
            textures.addProperty(
                    "fluid",
                    (fluid.equals("water") || fluid.equals("lava") ? "minecraft:" : "create:") + "block/" + fluid
                            + "_still");
            JsonObject definition = new JsonObject();
            definition.add("textures", textures);
            definition.add("elements", elements);
            definition.addProperty("render_type", "minecraft:translucent");
            MODELS.put(model, definition);
        }
        PolymerBlockParts.<ItemVaultBlockEntity>register(PolymerStorageParts::vault, AllBlocks.ITEM_VAULT);
        PolymerBlockParts.<FluidTankBlockEntity>register(
                PolymerStorageParts::tank, AllBlocks.FLUID_TANK, AllBlocks.CREATIVE_FLUID_TANK);
        PolymerSteamEngineParts.register();
    }

    private static void vault(ItemVaultBlockEntity be, BlockState state, float time, List<Part> out) {
        boolean alongX = ItemVaultBlock.getVaultBlockAxis(state) == Axis.X;
        ItemVaultBlockEntity controller = be.getControllerBE();
        boolean large = controller != null ? controller.getWidth() > 2 : ItemVaultBlock.isLarge(state);
        Matrix4f pose = alongX ? spin(Direction.UP, -90) : identity();
        for (Direction sourceFace : Direction.values()) {
            Direction face = alongX && sourceFace.getAxis().isHorizontal() ? sourceFace.getClockWise() : sourceFace;
            if (connected(be, state, face)) continue;
            Direction up = face.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH;
            Direction right = face.getAxis() == Axis.X ? Direction.SOUTH : Direction.WEST;
            Axis axis = alongX ? Axis.X : Axis.Z;
            if (face.getAxis().isVertical() && alongX) {
                up = up.getClockWise();
                right = right.getClockWise();
            }
            if (face.getAxis() != axis && face.getAxis().isHorizontal()) {
                up = Direction.fromAxisAndDirection(
                        axis, alongX ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
                right = Direction.fromAxisAndDirection(Axis.Y, face.getAxisDirection());
            }
            if (face == Direction.DOWN) up = up.getOpposite();
            if ((face == Direction.DOWN) ^ face.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                right = right.getOpposite();
            int mask = (connected(be, state, up) ? 1 : 0)
                    | (connected(be, state, up.getOpposite()) ? 2 : 0)
                    | (connected(be, state, right.getOpposite()) ? 4 : 0)
                    | (connected(be, state, right) ? 8 : 0);
            String part = vaultPart(sourceFace);
            int[] map = table(large, part);
            int index = 0;
            for (int i = 0; i < map.length; i++) if (map[i] == mask) index = i + 1;
            out.add(part(vaultModel(large, part, sourceFace, index), new Matrix4f(pose)));
        }
    }

    private static boolean connected(ItemVaultBlockEntity be, BlockState state, Direction side) {
        var pos = be.getBlockPos().relative(side);
        BlockState other = be.getLevel().getBlockState(pos);
        return other.getBlock() == state.getBlock()
                && ItemVaultBlock.getVaultBlockAxis(other) == ItemVaultBlock.getVaultBlockAxis(state)
                && ConnectivityHandler.isConnected(be.getLevel(), be.getBlockPos(), pos);
    }

    private static String vaultPart(Direction face) {
        return face.getAxis() == Axis.Z
                ? "front"
                : face == Direction.UP ? "top" : face == Direction.DOWN ? "bottom" : "side";
    }

    private static int[] table(boolean large, String part) {
        return part.equals("front")
                ? large ? WITHOUT_SINGLE : SQUARE
                : large ? WITHOUT_HORIZONTAL_SINGLE : WITHOUT_VERTICAL_MIDDLE;
    }

    private static Identifier vaultModel(boolean large, String part, Direction face, int index) {
        return model("polymer/vault/" + (large ? "large/" : "medium/") + part + "_" + face.getSerializedName() + "_"
                + index);
    }

    private static void tank(FluidTankBlockEntity be, BlockState state, float time, List<Part> out) {
        PolymerSteamEngineParts.boiler(be, out);
        if (!be.isController() || !be.window) return;
        var inventory = be.getTankInventory();
        var stack = inventory.getFluid();
        if (stack.isEmpty()) return;
        String id = BuiltInRegistries.FLUID.getKey(stack.getFluid()).getPath().replace("flowing_", "");
        Identifier model = FLUIDS.get(id);
        if (model == null) return;
        float ratio = Math.clamp((float) stack.getAmount() / inventory.getMaxAmountPerStack(), 0, 1);
        float height = (be.getHeight() - 2 / 16f) * ratio;
        if (height > 0)
            out.add(part(
                    model,
                    new Matrix4f()
                            .translate(1 / 16f, 1 / 16f, 1 / 16f)
                            .scale(be.getWidth() - 2 / 16f, height, be.getWidth() - 2 / 16f)));
    }

    static void addAssets(ResourcePackBuilder builder) {
        MODELS.forEach((model, data) ->
                builder.addStringData("assets/create/models/" + model.getPath() + ".json", data.toString()));
    }
}
