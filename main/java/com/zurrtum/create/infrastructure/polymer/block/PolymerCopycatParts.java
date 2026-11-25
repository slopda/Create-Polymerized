package com.zurrtum.create.infrastructure.polymer.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zurrtum.create.AllBlockTags;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.decoration.copycat.CopycatBlock;
import com.zurrtum.create.content.decoration.copycat.CopycatBlockEntity;
import com.zurrtum.create.content.decoration.copycat.CopycatPanelBlock;
import com.zurrtum.create.content.decoration.copycat.CopycatSpecialCases;
import com.zurrtum.create.content.decoration.copycat.CopycatStepBlock;
import com.zurrtum.create.infrastructure.polymer.PolymerBlockProjections;
import com.zurrtum.create.infrastructure.polymer.PolymerCreateBlock;
import com.zurrtum.create.infrastructure.polymer.PolymerLitModelHolder;
import com.zurrtum.create.infrastructure.polymer.PolymerTintedModels;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerCopycatStepCollision;
import com.zurrtum.create.infrastructure.polymer.collision.PolymerCrackModels;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class PolymerCopycatParts {
    private static final Map<BlockState, String> MATERIALS = new ConcurrentHashMap<>();
    private static final Map<BlockState, String> TRAPDOORS = new ConcurrentHashMap<>();

    private static final Map<BlockState, BlockState> CARRIERS = new HashMap<>();
    private static final Set<BlockState> WARNED = new HashSet<>();

    public static void registerCarriers() {
        for (var block : List.of(AllBlocks.COPYCAT_PANEL, AllBlocks.COPYCAT_STEP)) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                boolean water = state.getValue(BlockStateProperties.WATERLOGGED);
                BlockState carrier = block == AllBlocks.COPYCAT_PANEL
                        ? PolymerBlockResourceUtils.requestEmpty(
                                BlockModelType.getTrapdoor(state.getValue(CopycatPanelBlock.FACING), water))
                        : Blocks.SCULK_VEIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, water);
                CARRIERS.put(state, carrier == null ? Blocks.STRUCTURE_VOID.defaultBlockState() : carrier);
            }
            PolymerBlock.registerOverlay(block, new Carrier());
            BlockWithElementHolder.registerOverlay(block, new HolderProvider());
        }
    }

    private record Carrier() implements PolymerCreateBlock, PolymerTexturedBlock {
        @Override
        public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
            return CARRIERS.getOrDefault(state, Blocks.STRUCTURE_VOID.defaultBlockState());
        }
    }

    private record HolderProvider() implements BlockWithElementHolder {
        @Override
        public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
            return new MaterialHolder(level, pos, state);
        }

        @Override
        public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
            return true;
        }
    }

    private static final class MaterialHolder extends PolymerLitModelHolder {
        private final ServerLevel level;
        private final BlockPos pos;
        private final ItemDisplayElement display;
        private @Nullable Identifier shown;
        private @Nullable BlockState collisionState;
        private final List<PolymerCopycatStepCollision.Cell> cells = new ArrayList<>();
        private final PolymerBedrockCopycats.Skin skin = new PolymerBedrockCopycats.Skin();

        private int repairAge;

        MaterialHolder(ServerLevel level, BlockPos pos, BlockState state) {
            super(level, pos);
            this.level = level;
            this.pos = pos.immutable();
            display = addElement(new ItemDisplayElement());
            display.setItemDisplayContext(ItemDisplayContext.NONE);
            display.setRightRotation(new Quaternionf().rotationY((float) Math.PI));
            PolymerDecorativeCulling.inBlock(display);
            shown = model(state, AllBlocks.COPYCAT_BASE.defaultBlockState());
            display.setItem(PolymerBlockParts.displayItem(shown));
            updateCollision(state);
        }

        @Override
        protected void onTick() {
            super.onTick();
            refresh();
        }

        private void refresh() {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) return;
            BlockState state = chunk.getBlockState(pos);
            if (!state.is(AllBlocks.COPYCAT_PANEL) && !state.is(AllBlocks.COPYCAT_STEP)) return;
            BlockState material = chunk.getBlockEntity(pos) instanceof CopycatBlockEntity be
                    ? be.getMaterial()
                    : AllBlocks.COPYCAT_BASE.defaultBlockState();
            Identifier next = model(state, material);
            ItemStack item = displayItem(state, material, level, pos);
            if (!ItemStack.isSameItemSameComponents(display.getItem(), item)) display.setItem(item);
            shown = next;
            skin.update(level, pos, next, getWatchingPlayers());
            updateCollision(state);
            if (state.is(AllBlocks.COPYCAT_STEP) && ++repairAge >= 20) {
                repairAge = 0;
                for (var listener : getWatchingPlayers()) {
                    var player = listener.player;
                    if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < 36)
                        NativePresentationPolicy.sendPresentation(player, new ClientboundBlockUpdatePacket(pos, state));
                }
            }
        }

        private void updateCollision(BlockState state) {
            if (state == collisionState) return;
            cells.forEach(cell -> cell.remove(this));
            cells.clear();
            collisionState = state;
            if (state.is(AllBlocks.COPYCAT_STEP)) {
                for (var box : PolymerCopycatStepCollision.boxes(state, level, pos))
                    cells.add(new PolymerCopycatStepCollision.Cell(this, level, pos, box));
            }
        }

        @Override
        public boolean stopWatching(ServerGamePacketListenerImpl player) {
            skin.stopWatching(player);
            return super.stopWatching(player);
        }

        @Override
        public void destroy() {
            skin.destroy();
            super.destroy();
        }

        @Override
        protected void startWatchingExtraPackets(
                ServerGamePacketListenerImpl player, Consumer<Packet<ClientGamePacketListener>> packets) {
            super.startWatchingExtraPackets(player, packets);
        }
    }

    public static final String FOLDER = "block/c/";

    static BlockState visualMaterial(BlockState material) {
        if (BuiltInRegistries.BLOCK.getKey(material.getBlock()).getNamespace().equals(Create.MOD_ID)) return material;
        var synced = PolymerSyncedObject.getSyncedObject(BuiltInRegistries.BLOCK, material.getBlock());
        if (!(synced instanceof PolymerBlock polymer) || synced instanceof PolymerTexturedBlock) return material;
        if (!catalogue()
                .blocks()
                .get(BuiltInRegistries.BLOCK.getKey(material.getBlock()).toString())
                .isEmpty()) return material;
        BlockState shown = PolymerBlockUtils.getBlockStateSafely(polymer, material, PacketContext.get());
        return shown == null || PolymerSyncedObject.getSyncedObject(BuiltInRegistries.BLOCK, shown.getBlock()) != null
                ? material
                : shown;
    }

    static Identifier model(BlockState wrapper, BlockState material) {
        material = visualMaterial(material);
        String key = wrapper.is(AllBlocks.COPYCAT_PANEL) && CopycatSpecialCases.isTrapdoorMaterial(material)
                ? TRAPDOORS.computeIfAbsent(
                        material, state -> materialKey(catalogue().panels(), state))
                : MATERIALS.computeIfAbsent(
                        material, state -> materialKey(catalogue().blocks(), state));
        if (key.isEmpty()) {
            if (WARNED.add(material))
                Create.LOGGER.info(
                        "Copycat material {} needs a specialized native mesh; showing source base", material);
            key = MATERIALS.computeIfAbsent(
                    AllBlocks.COPYCAT_BASE.defaultBlockState(),
                    state -> materialKey(catalogue().blocks(), state));
        }
        if (key.isEmpty()) throw new IllegalStateException("Copycat base material mesh missing");
        String shape = wrapper.is(AllBlocks.COPYCAT_PANEL)
                ? "panel_" + wrapper.getValue(CopycatPanelBlock.FACING).getSerializedName()
                : "step_" + wrapper.getValue(CopycatStepBlock.FACING).getSerializedName() + "_"
                        + wrapper.getValue(CopycatStepBlock.HALF).getSerializedName();
        return modelId(key, shape);
    }

    private static String materialKey(Lookups lookups, BlockState material) {
        JsonObject variants =
                lookups.get(BuiltInRegistries.BLOCK.getKey(material.getBlock()).toString());
        if (variants.isEmpty()) return "";
        String selector = PolymerBlockProjections.selectMatchingVariant(variants, material);
        return selector == null ? "" : variants.get(selector).getAsString();
    }

    private static Identifier modelId(String key, String shape) {
        String file = key + "/" + shape;
        return CreateIds.id(FOLDER + catalogue().canonical().getOrDefault(file, file));
    }

    public static ItemStack displayItem(BlockState wrapper, BlockState material, Level level, BlockPos pos) {
        return PolymerTintedModels.tintItem(
                PolymerBlockParts.displayItem(model(wrapper, material)), visualMaterial(material), level, pos);
    }

    public static boolean hasMaterialMesh(BlockState material) {
        return !MATERIALS
                .computeIfAbsent(
                        visualMaterial(material),
                        state -> materialKey(catalogue().blocks(), state))
                .isEmpty();
    }

    public static ItemStack contraptionDisplayItem(StructureBlockInfo info, Level level, BlockPos pos) {
        BlockState material = info.nbt() == null
                ? AllBlocks.COPYCAT_BASE.defaultBlockState()
                : info.nbt().read("Material", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState());
        return displayItem(info.state(), material, level, pos);
    }

    static void addItemTint(Identifier model, JsonObject definition) {
        if (!model.getPath().startsWith(FOLDER)) return;
        String key = model.getPath().substring(FOLDER.length()).split("/", 2)[0];
        JsonArray tints = catalogue().baked().tints.get(key);
        if (tints != null) definition.add("tints", tints.deepCopy());
    }

    public static @Nullable Identifier contraptionItemModel(StructureBlockInfo info) {
        Identifier mesh = contraptionMesh(info);
        return mesh == null ? null : CreateIds.id("polymer_part/" + mesh.getPath());
    }

    public static @Nullable Identifier contraptionMesh(StructureBlockInfo info) {
        if (!info.state().is(AllBlocks.COPYCAT_PANEL) && !info.state().is(AllBlocks.COPYCAT_STEP)) return null;
        BlockState material = info.nbt() == null
                ? AllBlocks.COPYCAT_BASE.defaultBlockState()
                : info.nbt().read("Material", BlockState.CODEC).orElse(AllBlocks.COPYCAT_BASE.defaultBlockState());
        return model(info.state(), material);
    }

    private record Lookups(Map<String, JsonObject> byBlock) {
        JsonObject get(String block) {
            JsonObject variants = byBlock.get(block);
            return variants == null ? new JsonObject() : variants;
        }

        static Lookups of(Map<String, Map<String, String>> source) {
            Map<String, JsonObject> out = new HashMap<>();
            source.forEach((block, selectors) -> {
                JsonObject variants = new JsonObject();
                selectors.forEach(variants::addProperty);
                out.put(block, variants);
            });
            return new Lookups(Map.copyOf(out));
        }
    }

    private record Runtime(
            PolymerCopycatBaker.Catalogue baked, Lookups blocks, Lookups panels, Map<String, String> canonical) {}

    private static volatile @Nullable Runtime runtime;

    private static Runtime catalogue() {
        Runtime current = runtime;
        if (current != null) return current;
        synchronized (PolymerCopycatParts.class) {
            if (runtime != null) return runtime;
            long start = System.nanoTime();
            PolymerCopycatBaker.Catalogue baked = new PolymerCopycatBaker.Catalogue(PolymerCopycatParts::readAsset);
            for (var block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                boolean source = id.getNamespace().equals("minecraft")
                        || id.getNamespace().equals(Create.MOD_ID);
                boolean trapdoor = block instanceof TrapDoorBlock
                        || (source && id.getPath().endsWith("_trapdoor"));
                boolean cube = source || acceptedMaterial(block);
                if (!trapdoor && !cube) continue;
                JsonObject blockstate =
                        readAsset("assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json");
                if (blockstate == null) continue;
                if (trapdoor) baked.addTrapdoorPanel(id.toString(), blockstate);
                if (cube) baked.addCubeMaterial(id.toString(), blockstate);
            }
            runtime = new Runtime(
                    baked, Lookups.of(baked.blocks), Lookups.of(baked.panels), Map.copyOf(baked.canonical()));
            Create.LOGGER.info(
                    "Copycat catalogue: {} cube-material blocks, {} trapdoor panels, {} materials, {} meshes in {} ms",
                    baked.blocks.size(),
                    baked.panels.size(),
                    baked.meshes.size(),
                    baked.meshes.values().stream().mapToInt(Map::size).sum()
                            - runtime.canonical().size(),
                    (System.nanoTime() - start) / 1_000_000);
            return runtime;
        }
    }

    private static volatile @Nullable Map<String, List<Path>> assetRoots;

    private static Map<String, List<Path>> assetRoots() {
        Map<String, List<Path>> roots = assetRoots;
        if (roots != null) return roots;
        Map<String, List<Path>> found = new HashMap<>();
        try {
            Path client = PolymerCommonUtils.getClientJarRoot();
            if (client != null) addRoot(found, client);
        } catch (RuntimeException unavailable) {
            Create.LOGGER.warn("Copycat catalogue: vanilla client assets unavailable; vanilla materials keep the base");
        }
        for (var mod : FabricLoader.getInstance().getAllMods())
            for (Path root : mod.getRootPaths()) addRoot(found, root);
        assetRoots = Map.copyOf(found);
        return assetRoots;
    }

    private static void addRoot(Map<String, List<Path>> found, Path root) {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        try (var namespaces = Files.list(assets)) {
            for (Path ns : namespaces.toList())
                if (Files.isDirectory(ns))
                    found.computeIfAbsent(ns.getFileName().toString().replace("/", ""), k -> new ArrayList<>())
                            .add(root);
        } catch (IOException unreadable) {
        }
    }

    private static @Nullable JsonObject readAsset(String path) {
        String ns = path.split("/", 3)[1];
        for (Path root : assetRoots().getOrDefault(ns, List.of())) {
            Path file = root.resolve(path);
            if (!Files.isRegularFile(file)) continue;
            try {
                return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (IOException | RuntimeException malformed) {
                return null;
            }
        }
        return null;
    }

    public static void addAssets(ResourcePackBuilder builder) {
        JsonObject vein = readAsset("assets/minecraft/blockstates/sculk_vein.json");
        if (vein == null) throw new IllegalStateException("Missing vanilla sculk_vein blockstate for the step carrier");
        int changed = 0;
        for (JsonElement part : vein.getAsJsonArray("multipart")) {
            JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
            if (when == null || !when.keySet().equals(Set.copyOf(PolymerCopycatBaker.FACES))) continue;
            boolean allFalse = true;
            for (var value : when.entrySet())
                allFalse &= value.getValue().getAsString().equalsIgnoreCase("false");
            if (!allFalse) continue;
            JsonObject empty = new JsonObject();
            empty.addProperty("model", changed == 0 ? PolymerCrackModels.CUBE.toString() : "polymer:block/empty");
            part.getAsJsonObject().add("apply", empty);
            changed++;
        }
        if (changed != 6)
            throw new IllegalStateException("Native vein fallback schema changed; review the copycat step carrier");
        builder.addStringData("assets/minecraft/blockstates/sculk_vein.json", vein.toString());

        Runtime catalogue = catalogue();
        Map<String, String> meshes = new LinkedHashMap<>();
        String models = "assets/create/models/" + FOLDER;
        for (var material : catalogue.baked().meshes.entrySet())
            for (var shape : material.getValue().entrySet()) {
                String file = material.getKey() + "/" + shape.getKey();
                if (catalogue.canonical().containsKey(file)) continue;
                meshes.put(models + file + ".json", shape.getValue().toString());
                JsonObject inner = new JsonObject();
                inner.addProperty("type", "minecraft:model");
                inner.addProperty("model", CreateIds.id(FOLDER + file).toString());
                addItemTint(CreateIds.id(FOLDER + file), inner);
                JsonObject definition = new JsonObject();
                definition.add("model", inner);
                builder.addStringData(
                        "assets/create/items/polymer_part/" + FOLDER + file + ".json", definition.toString());
            }
        meshes.forEach(builder::addStringData);
        shareGeometry(builder, meshes);
    }

    private static boolean acceptedMaterial(Block block) {
        if (block.asItem() == Items.AIR
                || block instanceof EntityBlock
                || block instanceof StairBlock
                || block instanceof CopycatBlock) return false;
        BlockState state = block.defaultBlockState();
        if (state.is(AllBlockTags.COPYCAT_DENY)) return false;
        try {
            var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return !shape.isEmpty()
                    && shape.bounds().equals(Shapes.block().bounds())
                    && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                            .isEmpty();
        } catch (RuntimeException needsLevel) {
            return false;
        }
    }

    static final String SHARED = FOLDER + "g/";

    static void shareGeometry(ResourcePackBuilder builder, Map<String, String> meshes) {
        String folder = "assets/create/models/" + FOLDER;
        Map<String, List<String>> byGeometry = new LinkedHashMap<>();
        Map<String, JsonObject> parsed = new HashMap<>();
        for (var mesh : meshes.entrySet()) {
            if (!mesh.getKey().startsWith(folder) || !mesh.getKey().endsWith(".json")) continue;
            JsonObject model = JsonParser.parseString(mesh.getValue()).getAsJsonObject();
            if (model.size() != 2 || !model.has("textures") || !model.has("elements")) continue;
            parsed.put(mesh.getKey(), model);
            byGeometry
                    .computeIfAbsent(model.get("elements").toString(), k -> new ArrayList<>())
                    .add(mesh.getKey());
        }
        int shared = 0;
        for (var group : byGeometry.entrySet()) {
            if (group.getValue().size() < 2) continue;
            String parent = SHARED + Integer.toHexString(shared++);
            JsonObject geometry = new JsonObject();
            geometry.add("elements", JsonParser.parseString(group.getKey()));
            builder.addStringData("assets/create/models/" + parent + ".json", geometry.toString());
            for (String path : group.getValue()) {
                JsonObject child = new JsonObject();
                child.addProperty("parent", "create:" + parent);
                child.add("textures", parsed.get(path).get("textures"));
                builder.addStringData(path, child.toString());
            }
        }
        Create.LOGGER.info("Copycat meshes share {} geometries", shared);
    }
}
