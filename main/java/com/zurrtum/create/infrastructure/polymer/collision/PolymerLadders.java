package com.zurrtum.create.infrastructure.polymer.collision;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zurrtum.create.content.decoration.MetalLadderBlock;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerLoadedBlocks;
import com.zurrtum.create.infrastructure.polymer.block.PolymerBlockParts;
import com.zurrtum.create.infrastructure.polymer.block.PolymerDecorativeCulling;
import com.zurrtum.create.infrastructure.polymer.block.PolymerKineticVisuals;
import dev.veeda.createpoly.assets.CreateIds;
import dev.veeda.createpoly.assets.PackJson;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PolymerLadders {
    private static final Identifier NATIVE_BLOCK_MODEL = Identifier.parse("create:polymer_part/ladder/native");

    private PolymerLadders() {}

    public static BlockState carrier(BlockState source) {
        return Blocks.LADDER
                .defaultBlockState()
                .setValue(LadderBlock.FACING, source.getValue(LadderBlock.FACING))
                .setValue(BlockStateProperties.WATERLOGGED, source.getValue(BlockStateProperties.WATERLOGGED));
    }

    public static void register(Block block) {
        BlockWithElementHolder.registerOverlay(block, provider(false));
    }

    public static void registerNativeHost() {
        BlockWithElementHolder.registerOverlay(Blocks.LADDER, provider(true));
    }

    private static BlockWithElementHolder provider(boolean nativeSkin) {
        return new BlockWithElementHolder() {
            @Override
            public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return new LadderHolder(level, pos, state, nativeSkin);
            }

            @Override
            public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
                return true;
            }
        };
    }

    static int visibility(BlockGetter world, BlockPos pos, BlockState state) {
        int mask = 0;
        if (Block.shouldRenderFace(state, PolymerLoadedBlocks.stateOrAir(world, pos.above()), Direction.UP)) mask |= 1;
        Direction back = state.getValue(LadderBlock.FACING).getOpposite();
        if (Block.shouldRenderFace(state, PolymerLoadedBlocks.stateOrAir(world, pos.relative(back)), back)) mask |= 2;
        return mask;
    }

    private static Identifier item(BlockState state, int mask) {
        return CreateIds.id("polymer_part/ladder/"
                + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + "/" + mask);
    }

    private static float angle(BlockState state) {
        return switch (state.getValue(LadderBlock.FACING)) {
            case EAST -> -(float) Math.PI / 2;
            case SOUTH -> -(float) Math.PI;
            case WEST -> -(float) Math.PI * 1.5f;
            default -> 0;
        };
    }

    private static Matrix4f pose(BlockState state) {
        return PolymerBlockParts.displayTransform(
                new Matrix4f().translate(.5f, .5f, .5f).rotateY(angle(state)).translate(-.5f, -.5f, -.5f));
    }

    public static @org.jspecify.annotations.Nullable Identifier movingItemModel(BlockState state) {
        if (state.getBlock() instanceof MetalLadderBlock) return item(state, 3);
        return state.is(Blocks.LADDER) ? NATIVE_BLOCK_MODEL : null;
    }

    public static Quaternionf movingRotation(BlockState state) {
        return new Quaternionf().rotationY(angle(state));
    }

    private static final class LadderHolder extends PolymerLitModelHolder {
        final ServerLevel level;
        final BlockPos pos;
        final boolean nativeSkin;
        final ItemDisplayElement display = new ItemDisplayElement();
        BlockState shown;
        int shownMask = -1;

        LadderHolder(ServerLevel level, BlockPos pos, BlockState state, boolean nativeSkin) {
            super(level, pos);
            this.level = level;
            this.pos = pos.immutable();
            this.nativeSkin = nativeSkin;
            display.setItemDisplayContext(ItemDisplayContext.NONE);
            PolymerDecorativeCulling.inBlock(display);
            show(state, 3);
            addElement(display);
        }

        void show(BlockState state, int mask) {
            if (state == shown && (nativeSkin || shownMask == mask)) return;
            shown = state;
            shownMask = mask;
            display.setItem(
                    PolymerKineticVisuals.rotatingDisplayItem(nativeSkin ? NATIVE_BLOCK_MODEL : item(state, mask)));
            display.setTransformation(pose(state));
        }

        private void refresh() {
            var attachment = BlockAwareAttachment.get(this);
            if (attachment == null || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null)
                return;
            BlockState state = attachment.getBlockState();
            if (!(state.getBlock() instanceof LadderBlock)) return;
            show(state, nativeSkin ? 3 : visibility(level, pos, state));
        }

        @Override
        protected void onTick() {
            super.onTick();
            refresh();
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            refresh();
            return super.startWatching(player);
        }
    }

    static JsonObject variant(JsonObject base, JsonObject child, int mask) {
        JsonObject result = base.deepCopy();
        result.addProperty("parent", "minecraft:block/block");
        result.remove("groups");
        var textures = result.getAsJsonObject("textures");
        for (var entry : child.getAsJsonObject("textures").entrySet())
            textures.add(entry.getKey(), entry.getValue().deepCopy());
        JsonArray elements = new JsonArray();
        for (var entry : result.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject().deepCopy();
            var faces = element.getAsJsonObject("faces");
            for (String direction : List.copyOf(faces.keySet())) {
                var face = faces.getAsJsonObject(direction);
                if (!face.has("cullface")) continue;
                String cull = face.get("cullface").getAsString();
                if (cull.equals("up") && (mask & 1) == 0 || cull.equals("south") && (mask & 2) == 0)
                    faces.remove(direction);
                else face.remove("cullface");
            }
            if (!faces.isEmpty()) elements.add(element);
        }
        result.add("elements", elements);
        return result;
    }

    public static void addAssets(ResourcePackBuilder builder) {
        builder.addStringData(
                "assets/create/items/" + NATIVE_BLOCK_MODEL.getPath() + ".json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/ladder\"}}");
        builder.addStringData(
                "assets/minecraft/blockstates/ladder.json",
                "{\"variants\":{\"\":{\"model\":\"create:block/polymer/native_ladder_empty\"}}}");
        builder.addStringData(
                "assets/create/models/block/polymer/native_ladder_empty.json",
                "{\"textures\":{\"particle\":\"minecraft:block/ladder\"},\"elements\":[]}");
        StringBuilder vine = new StringBuilder("{\"multipart\":[");
        String[][] faces = {
            {"north", ""}, {"east", ",\"y\":90"}, {"south", ",\"y\":180"}, {"west", ",\"y\":270"}, {"up", ",\"x\":270"}
        };
        for (int i = 0; i < faces.length; i++)
            vine.append(i == 0 ? "" : ",")
                    .append("{\"apply\":{\"model\":\"minecraft:block/vine\"")
                    .append(i == 0 ? "" : ",\"uvlock\":true")
                    .append(faces[i][1])
                    .append("},\"when\":{\"")
                    .append(faces[i][0])
                    .append("\":\"true\"}}");
        builder.addStringData(
                "assets/minecraft/blockstates/vine.json", vine.append("]}").toString());
        JsonObject base = read(builder, "assets/create/models/block/ladder.json");
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof MetalLadderBlock)) continue;
            var id = BuiltInRegistries.BLOCK.getKey(block);
            JsonObject child = read(builder, "assets/" + id.getNamespace() + "/models/block/" + id.getPath() + ".json");
            for (int mask = 0; mask < 4; mask++) {
                String model = "create:block/polymer/ladder/" + id.getPath() + "/" + mask;
                builder.addStringData(
                        "assets/create/models/block/polymer/ladder/" + id.getPath() + "/" + mask + ".json",
                        variant(base, child, mask).toString());
                builder.addStringData(
                        "assets/create/items/"
                                + item(block.defaultBlockState(), mask).getPath() + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"}}");
            }
        }
    }

    private static JsonObject read(ResourcePackBuilder builder, String path) {
        byte[] data = builder.getDataOrSource(path);
        if (data == null) throw new IllegalStateException("Missing source ladder model: " + path);
        return PackJson.object(data);
    }
}
