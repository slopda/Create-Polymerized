package com.zurrtum.create.infrastructure.polymer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JavaOps;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.equipment.blueprint.BlueprintEntity;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.zurrtum.create.content.logistics.factoryBoard.PanelSlot;
import com.zurrtum.create.content.logistics.factoryBoard.ServerFactoryPanelBehaviour;
import com.zurrtum.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerBlueprintGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerFactoryPanelGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerFactoryPanelItemGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerFilterForms;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerGhostItemGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerMachineDialogs;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerScheduleScreen;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerStockCategoryGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerStockMarketGui;
import com.zurrtum.create.infrastructure.polymer.gui.PolymerToolDialogs;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerHudRadial;
import com.zurrtum.create.infrastructure.polymer.nativeclient.NativePresentationPolicy;
import com.zurrtum.create.infrastructure.polymer.nativeclient.PolymerNativeClients;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderDialog;
import com.zurrtum.create.infrastructure.polymer.ponder.PolymerPonderScenes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PolymerGuiReview implements ModInitializer {
    private static final int BASE_X = 6000, BASE_Y = 120, BASE_Z = 6000, SPACING = 16, OPEN_DELAY = 10;

    private enum Fixture {
        NONE,
        BARRELS,
        TRACK,
        WALL
    }

    @FunctionalInterface
    private interface Opener {
        void open(ServerPlayer player, ServerLevel level, BlockPos pos) throws Exception;
    }

    private record Entry(
            String id,
            String title,
            String source,
            String block,
            String item,
            Fixture fixture,
            Opener opener,
            String hint) {
        boolean shiftRates() {
            return opener != null;
        }
    }

    private static final class Session {
        int index;
        int openAt = -1;
        int openedAt;
        boolean wasShift;
        int savedSlot = -1;
        ItemStack saved = ItemStack.EMPTY;
        final ServerLevel returnLevel;
        final Vec3 returnPos;
        final float returnYaw, returnPitch;

        Session(ServerPlayer player) {
            returnLevel = player.level();
            returnPos = player.position();
            returnYaw = player.getYRot();
            returnPitch = player.getXRot();
        }
    }

    private static final Opener USE_BLOCK = (player, level, pos) -> player.gameMode.useItemOn(
            player,
            level,
            player.getMainHandItem(),
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, -.5), Direction.NORTH, pos, false));

    private static final List<Entry> ENTRIES = List.of(
            use("schematic_table", "Schematic Table", "SchematicTableScreen", "create:schematic_table"),
            use("schematicannon", "Schematicannon", "SchematicannonScreen", "create:schematicannon"),
            use("sequencer", "Sequenced Gearshift screen", "SequencedGearshiftScreen", "create:sequenced_gearshift"),
            new Entry(
                    "threshold",
                    "Threshold Switch",
                    "ThresholdSwitchScreen",
                    "create:stockpile_switch",
                    null,
                    Fixture.BARRELS,
                    USE_BLOCK,
                    null),
            new Entry(
                    "display_link",
                    "Display Link",
                    "DisplayLinkScreen",
                    "create:display_link",
                    null,
                    Fixture.BARRELS,
                    USE_BLOCK,
                    "Fixture has no display target, so the target line reads as none."),
            use("toolbox", "Toolbox", "ToolboxScreen", "create:brown_toolbox"),
            use("clipboard_block", "Clipboard (placed)", "ClipboardScreen", "create:clipboard"),
            use("elevator_contact", "Elevator Contact", "ElevatorContactScreen", "create:elevator_contact"),
            use("requester", "Redstone Requester screen", "RedstoneRequesterScreen", "create:redstone_requester"),
            use("frogport", "Package Frogport", "PackagePortScreen", "create:package_frogport"),
            use("postbox", "Postbox", "PackagePortScreen", "create:white_postbox"),
            new Entry(
                    "stock_request",
                    "Stock Keeper request",
                    "StockKeeperRequestScreen",
                    "create:stock_ticker",
                    null,
                    Fixture.NONE,
                    (player, level, pos) -> stock(player, level, pos),
                    "Opened directly: the source also needs a seated Stock Keeper."),
            new Entry(
                    "stock_configure",
                    "Stock Keeper configure",
                    "StockKeeperRequestScreen (configure)",
                    "create:stock_ticker",
                    "create:white_table_cloth",
                    Fixture.NONE,
                    (player, level, pos) -> stock(player, level, pos),
                    "Holding a table cloth. Check the 3.5x item beside the panel."),
            new Entry(
                    "stock_category",
                    "Stock Keeper categories",
                    "StockKeeperCategoryScreen",
                    "create:stock_ticker",
                    null,
                    Fixture.NONE,
                    (player, level, pos) -> {
                        if (level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker)
                            PolymerStockCategoryGui.open(player, ticker, () -> {});
                        else throw new IllegalStateException("No stock ticker at fixture");
                    },
                    "Hold a filter item to test adding a category."),
            new Entry(
                    "value_rpm",
                    "Value board: RPM",
                    "ValueSettingsScreen (kinetic)",
                    "create:creative_motor",
                    null,
                    Fixture.NONE,
                    (player, level, pos) -> value(player, level, pos),
                    null),
            new Entry(
                    "value_timer",
                    "Value board: Pulse",
                    "ValueSettingsScreen (generic)",
                    "create:pulse_extender",
                    null,
                    Fixture.NONE,
                    (player, level, pos) -> value(player, level, pos),
                    null),
            new Entry(
                    "filter",
                    "List Filter",
                    "FilterScreen",
                    null,
                    "create:filter",
                    Fixture.NONE,
                    (player, level, pos) -> PolymerGhostItemGui.openFilter(player),
                    null),
            new Entry(
                    "attribute_filter",
                    "Attribute Filter",
                    "AttributeFilterScreen",
                    null,
                    "create:attribute_filter",
                    Fixture.NONE,
                    (player, level, pos) -> PolymerFilterForms.openAttributeFilterSettings(player),
                    null),
            new Entry(
                    "package_filter",
                    "Package Filter",
                    "PackageFilterScreen",
                    null,
                    "create:package_filter",
                    Fixture.NONE,
                    (player, level, pos) -> PolymerFilterForms.openPackageFilterSettings(player),
                    null),
            new Entry(
                    "linked_controller",
                    "Linked Controller",
                    "LinkedControllerScreen",
                    null,
                    "create:linked_controller",
                    Fixture.NONE,
                    (player, level, pos) -> PolymerGhostItemGui.openController(player),
                    null),
            new Entry(
                    "schedule",
                    "Train Schedule",
                    "ScheduleScreen",
                    null,
                    "create:schedule",
                    Fixture.NONE,
                    (player, level, pos) -> PolymerScheduleScreen.open(player),
                    null),
            new Entry(
                    "worldshaper",
                    "Worldshaper",
                    "WorldshaperScreen",
                    null,
                    "create:handheld_worldshaper",
                    Fixture.NONE,
                    (player, level, pos) -> PolymerToolDialogs.openWorldshaper(player, InteractionHand.MAIN_HAND),
                    null),
            new Entry(
                    "symmetry",
                    "Wand of Symmetry",
                    "SymmetryWandScreen",
                    null,
                    "create:wand_of_symmetry",
                    Fixture.NONE,
                    (player, level, pos) -> PolymerToolDialogs.openSymmetry(player, InteractionHand.MAIN_HAND),
                    null),
            new Entry(
                    "clipboard_item",
                    "Clipboard (held)",
                    "ClipboardScreen",
                    null,
                    "create:clipboard",
                    Fixture.NONE,
                    (player, level, pos) -> PolymerToolDialogs.openClipboardItem(player, InteractionHand.MAIN_HAND),
                    null),
            new Entry(
                    "blueprint",
                    "Crafting Blueprint",
                    "BlueprintScreen",
                    null,
                    "create:crafting_blueprint",
                    Fixture.WALL,
                    (player, level, pos) -> blueprint(player, level, pos),
                    null),
            new Entry(
                    "goggle_config",
                    "Goggle overlay position",
                    "GoggleConfigScreen",
                    null,
                    null,
                    Fixture.NONE,
                    (player, level, pos) -> level.getServer()
                            .getCommands()
                            .performPrefixedCommand(player.createCommandSourceStack(), "createfly overlay"),
                    null),
            new Entry(
                    "ponder",
                    "Ponder lesson",
                    "PonderUI",
                    null,
                    null,
                    Fixture.NONE,
                    (player, level, pos) -> PolymerPonderDialog.open(player, PolymerPonderScenes.COGWHEEL),
                    "Also use its tag and index navigation: PonderTagScreen, PonderTagIndexScreen, PonderIndexScreen."),
            new Entry(
                    "radial_wrench",
                    "Radial wrench",
                    "RadialWrenchMenu",
                    "create:shaft",
                    "create:wrench",
                    Fixture.NONE,
                    null,
                    "Look at the shaft and press F. Rate with /gr afterwards."),
            new Entry(
                    "toolbelt",
                    "Toolbelt radial",
                    "RadialToolboxMenu",
                    "create:brown_toolbox",
                    null,
                    Fixture.NONE,
                    null,
                    "Hold Sneak and press F. Set toolbox filters first to see items. Rate with /gr afterwards."),
            new Entry(
                    "station",
                    "Train Station",
                    "StationScreen",
                    "create:track",
                    "create:track_station",
                    Fixture.TRACK,
                    null,
                    "Right-click the track with the station item, place the station, then right-click it. Rate with /gr."),
            new Entry(
                    "assembly",
                    "Train Assembly screen",
                    "AssemblyScreen",
                    "create:track",
                    "create:track_station",
                    Fixture.TRACK,
                    null,
                    "From the Station screen, press Assemble. Rate with /gr."),
            new Entry(
                    "factory_panel",
                    "Factory Panel",
                    "FactoryPanelScreen",
                    null,
                    null,
                    Fixture.WALL,
                    (player, level, pos) -> factory(player, level, pos, true),
                    null),
            new Entry(
                    "factory_set_item",
                    "Factory Panel item",
                    "FactoryPanelSetItemScreen",
                    null,
                    null,
                    Fixture.WALL,
                    (player, level, pos) -> factory(player, level, pos, false),
                    null),
            new Entry(
                    "quill",
                    "Save Schematic",
                    "SchematicPromptScreen",
                    null,
                    "create:schematic_and_quill",
                    Fixture.WALL,
                    null,
                    "Right-click two corners, then press F to name and save. Rate with /gr."),
            new Entry(
                    "schematic_tools",
                    "Schematic tools and edit",
                    "ToolSelectionScreen, SchematicEditScreen",
                    "create:schematic_table",
                    null,
                    Fixture.NONE,
                    null,
                    "Needs a schematic from a staged .nbt file. Hold it, then press F. Rate with /gr."));

    private static Entry use(String id, String title, String source, String block) {
        return new Entry(id, title, source, block, null, Fixture.NONE, USE_BLOCK, null);
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, JsonObject> RESULTS = new LinkedHashMap<>();
    private static Path resultsFile;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("gr")
                        .executes(c -> run(c, player -> {
                            Session session = SESSIONS.get(player.getUUID());
                            if (session == null) start(player, 0);
                            else panel(player, session);
                        }))
                        .then(Commands.literal("start").executes(c -> run(c, player -> start(player, firstUnrated()))))
                        .then(Commands.literal("restart").executes(c -> run(c, player -> start(player, 0))))
                        .then(Commands.literal("open")
                                .executes(c -> run(c, player -> withSession(player, s -> go(player, s, s.index)))))
                        .then(Commands.literal("next")
                                .executes(c -> run(c, player -> withSession(player, s -> advance(player, s, 1)))))
                        .then(Commands.literal("back")
                                .executes(c -> run(c, player -> withSession(player, s -> advance(player, s, -1)))))
                        .then(Commands.literal("stop").executes(c -> run(c, PolymerGuiReview::stop)))
                        .then(Commands.literal("list").executes(c -> run(c, PolymerGuiReview::list)))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            ENTRIES.forEach(e -> b.suggest(e.id()));
                                            return b.buildFuture();
                                        })
                                        .executes(c -> run(c, player -> {
                                            int index = indexOf(StringArgumentType.getString(c, "id"));
                                            if (index < 0) {
                                                player.sendSystemMessage(Component.literal("Unknown screen id")
                                                        .withStyle(ChatFormatting.RED));
                                                return;
                                            }
                                            Session session = SESSIONS.get(player.getUUID());
                                            if (session == null) start(player, index);
                                            else go(player, session, index);
                                        }))))
                        .then(Commands.literal("mark").then(mark("correct")).then(mark("wrong")))));
        ServerLifecycleEvents.SERVER_STARTED.register(PolymerGuiReview::load);
        ServerTickEvents.END_SERVER_TICK.register(PolymerGuiReview::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session session = SESSIONS.remove(handler.player.getUUID());
            if (session != null) restoreHand(handler.player, session);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mark(String verdict) {
        return Commands.literal(verdict)
                .then(Commands.argument("note", StringArgumentType.greedyString())
                        .executes(c -> run(
                                c,
                                player -> withSession(player, s -> {
                                    String note = StringArgumentType.getString(c, "note");
                                    record(
                                            player,
                                            ENTRIES.get(s.index),
                                            verdict,
                                            note.startsWith("n:")
                                                    ? note.substring(2).trim()
                                                    : note.trim());
                                    advance(player, s, 1);
                                }))));
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(ServerPlayer player) throws Exception;
    }

    private static int run(CommandContext<CommandSourceStack> context, PlayerAction action) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null
                || PolymerNativeClients.isNative(player)
                || !player.level().getServer().getPlayerList().isOp(player.nameAndId())) return 0;
        try {
            action.run(player);
        } catch (Exception failure) {
            Create.LOGGER.warn("GUI review command failed", failure);
            player.sendSystemMessage(
                    Component.literal("GUI review error: " + failure).withStyle(ChatFormatting.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface SessionAction {
        void run(Session session) throws Exception;
    }

    private static void withSession(ServerPlayer player, SessionAction action) throws Exception {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null)
            player.sendSystemMessage(
                    Component.literal("No review running. Use /gr start.").withStyle(ChatFormatting.RED));
        else action.run(session);
    }

    private static int indexOf(String id) {
        for (int i = 0; i < ENTRIES.size(); i++) if (ENTRIES.get(i).id().equals(id)) return i;
        return -1;
    }

    private static int firstUnrated() {
        for (int i = 0; i < ENTRIES.size(); i++)
            if (!RESULTS.containsKey(ENTRIES.get(i).id())) return i;
        return 0;
    }

    private static void start(ServerPlayer player, int index) {
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session(player));
        go(player, session, index);
    }

    private static void advance(ServerPlayer player, Session session, int step) {
        int next = session.index + step;
        if (next >= ENTRIES.size()) {
            player.sendSystemMessage(Component.literal("All " + ENTRIES.size() + " screens visited.")
                    .withStyle(ChatFormatting.GREEN));
            list(player);
            stop(player);
            return;
        }
        go(player, session, Math.max(next, 0));
    }

    private static void go(ServerPlayer player, Session session, int index) {
        NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
        player.closeContainer();
        restoreHand(player, session);
        session.index = index;
        Entry entry = ENTRIES.get(index);
        ServerLevel level = player.level().getServer().overworld();
        BlockPos pos = position(index);
        if (!buildFixture(level, pos, entry)) {
            player.sendSystemMessage(
                    Component.literal("Fixture space at " + pos.toShortString() + " is occupied; skipping build.")
                            .withStyle(ChatFormatting.RED));
        }
        player.teleportTo(level, pos.getX() + .5, pos.getY(), pos.getZ() - 2.5, Set.of(), 0, 20, true);
        if (entry.item() != null) hold(player, session, new ItemStack(item(entry.item())));
        else hold(player, session, ItemStack.EMPTY);
        session.openAt = player.level().getServer().getTickCount() + OPEN_DELAY;
        announce(player, index, entry);
    }

    private static void announce(ServerPlayer player, int index, Entry entry) {
        MutableComponent line = Component.literal("[" + (index + 1) + "/" + ENTRIES.size() + "] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(entry.title()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  source: " + entry.source()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(line);
        if (entry.hint() != null)
            player.sendSystemMessage(Component.literal(entry.hint()).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.empty()
                .append(chatButton("[Rate]", "gr", ChatFormatting.AQUA))
                .append(" ")
                .append(chatButton("[Open again]", "gr open", ChatFormatting.GRAY))
                .append(" ")
                .append(chatButton("[Skip]", "gr next", ChatFormatting.GRAY)));
    }

    private static Component chatButton(String text, String command, ChatFormatting color) {
        return Component.literal(text)
                .withStyle(style -> style.withColor(color).withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static void stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        restoreHand(player, session);
        NativePresentationPolicy.sendPresentation(player, ClientboundClearDialogPacket.INSTANCE);
        player.closeContainer();
        player.teleportTo(
                session.returnLevel,
                session.returnPos.x,
                session.returnPos.y,
                session.returnPos.z,
                Set.of(),
                session.returnYaw,
                session.returnPitch,
                true);
        player.sendSystemMessage(Component.literal("Review stopped. /gr start continues at the first unrated screen.")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        int now = server.getTickCount();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            Session session = SESSIONS.get(player.getUUID());
            if (session == null) continue;
            Entry entry = ENTRIES.get(session.index);
            if (session.openAt >= 0 && now >= session.openAt) {
                session.openAt = -1;
                session.openedAt = now;
                open(player, session, entry);
            }
            boolean shift = player.isShiftKeyDown();
            if (shift
                    && !session.wasShift
                    && session.openAt < 0
                    && entry.shiftRates()
                    && now - session.openedAt > OPEN_DELAY
                    && player.containerMenu == player.inventoryMenu
                    && !PolymerHudRadial.isOpen(player)) panel(player, session);
            session.wasShift = shift;
        }
    }

    private static void open(ServerPlayer player, Session session, Entry entry) {
        if (entry.opener() == null) {
            actionBar(player, "Follow the chat steps, then /gr to rate");
            return;
        }
        ServerLevel level = player.level();
        try {
            entry.opener().open(player, level, position(session.index));
            actionBar(player, "Esc to close, then tap Shift to rate");
        } catch (Exception | LinkageError failure) {
            Create.LOGGER.warn("GUI review could not open {}", entry.id(), failure);
            player.sendSystemMessage(Component.literal("Could not open " + entry.title() + ": " + failure)
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void actionBar(ServerPlayer player, String text) {
        NativePresentationPolicy.sendPresentation(
                player, new ClientboundSetActionBarTextPacket(Component.literal(text)));
    }

    private static void panel(ServerPlayer player, Session session) {
        Entry entry = ENTRIES.get(session.index);
        int correct = 0, wrong = 0;
        for (JsonObject result : RESULTS.values())
            if ("correct".equals(result.get("verdict").getAsString())) correct++;
            else wrong++;
        JsonObject previous = RESULTS.get(entry.id());
        String note =
                previous != null && previous.has("note") ? previous.get("note").getAsString() : "";
        String body = "Source: " + entry.source()
                + "\nRated so far: " + correct + " correct, " + wrong + " wrong, of " + ENTRIES.size()
                + (previous == null
                        ? ""
                        : "\nCurrent verdict: " + previous.get("verdict").getAsString());
        Input input = new Input(
                "note",
                new TextInput(300, Component.literal("Note (what is wrong)"), true, note, 200, Optional.empty()));
        var common = new CommonDialogData(
                Component.literal((session.index + 1) + "/" + ENTRIES.size() + "  " + entry.title()),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(body), 300)),
                List.of(input));
        List<ActionButton> buttons = List.of(
                button("Correct", template("gr mark correct n:$(note)")),
                button("Wrong", template("gr mark wrong n:$(note)")),
                button("Skip", command("gr next")),
                button("Open again", command("gr open")),
                button("Back", command("gr back")),
                button("Stop", command("gr stop")));
        Holder<Dialog> dialog = Holder.direct(new MultiActionDialog(common, buttons, Optional.empty(), 3));
        player.openDialog(dialog);
    }

    private static ActionButton button(String label, Action action) {
        return new ActionButton(new CommonButtonData(Component.literal(label), 96), Optional.of(action));
    }

    private static Action command(String command) {
        return new StaticAction(new ClickEvent.RunCommand(command));
    }

    private static Action template(String template) {
        return new CommandTemplate(
                ParsedTemplate.CODEC.parse(JavaOps.INSTANCE, template).getOrThrow());
    }

    private static BlockPos position(int index) {
        return new BlockPos(BASE_X + index * SPACING, BASE_Y, BASE_Z);
    }

    private static boolean buildFixture(ServerLevel level, BlockPos pos, Entry entry) {
        level.getChunk(pos);
        Block wanted = entry.block() == null ? null : block(entry.block());
        for (int x = -3; x <= 3; x++)
            for (int z = -4; z <= 3; z++) {
                BlockPos floor = pos.offset(x, -1, z);
                BlockState state = level.getBlockState(floor);
                if (!state.isAir() && !state.is(Blocks.SMOOTH_STONE) && !state.is(Blocks.BARREL)) return false;
                if (state.isAir()) level.setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
            }
        switch (entry.fixture()) {
            case BARRELS -> {
                for (BlockPos side : List.of(pos.below(), pos.east(), pos.west(), pos.south()))
                    level.setBlockAndUpdate(side, Blocks.BARREL.defaultBlockState());
            }
            case WALL -> {
                BlockPos wall = pos.south(2);
                for (int x = -2; x <= 2; x++)
                    for (int y = 0; y <= 3; y++)
                        if (level.getBlockState(wall.offset(x, y, 0)).isAir())
                            level.setBlockAndUpdate(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
            }
            case TRACK -> {
                for (int x = -3; x <= 3; x++) {
                    BlockPos rail = pos.offset(x, 0, 0);
                    if (level.getBlockState(rail).isAir())
                        level.setBlockAndUpdate(rail, block("create:track").defaultBlockState());
                }
            }
            case NONE -> {}
        }
        if (wanted != null && entry.fixture() != Fixture.TRACK) {
            BlockState present = level.getBlockState(pos);
            if (present.isAir()) level.setBlockAndUpdate(pos, wanted.defaultBlockState());
            else if (!present.is(wanted)) return false;
        }
        return true;
    }

    private static void stock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker)
            PolymerStockMarketGui.open(player, ticker);
        else throw new IllegalStateException("No stock ticker at fixture");
    }

    private static void value(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SmartBlockEntity smart)
            PolymerMachineDialogs.openValueSettings(player, level, pos, smart);
        else throw new IllegalStateException("No smart block entity at fixture");
    }

    private static void blueprint(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos wall = pos.south(2);
        AABB area = new AABB(pos).inflate(4);
        List<BlueprintEntity> found = level.getEntitiesOfClass(BlueprintEntity.class, area);
        if (found.isEmpty()) {
            player.gameMode.useItemOn(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0, -.5), Direction.NORTH, wall, false));
            found = level.getEntitiesOfClass(BlueprintEntity.class, area);
        }
        if (found.isEmpty()) throw new IllegalStateException("Blueprint did not place");
        PolymerBlueprintGui.open(player, found.getFirst(), 0);
    }

    private static void factory(ServerPlayer player, ServerLevel level, BlockPos pos, boolean withItem) {
        BlockPos wall = pos.south(2), gauge = wall.north();
        var slots = PanelSlot.values();
        if (!level.getBlockState(gauge).is(block("create:factory_gauge"))) {
            ItemStack tuned = new ItemStack(item("create:factory_gauge"));
            LogisticallyLinkedBlockItem.assignFrequency(tuned, player, UUID.randomUUID());
            var inventory = player.getInventory();
            ItemStack previous = inventory.getItem(inventory.getSelectedSlot());
            inventory.setItem(inventory.getSelectedSlot(), tuned);
            player.gameMode.useItemOn(
                    player,
                    level,
                    tuned,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0, -.5), Direction.NORTH, wall, false));
            inventory.setItem(inventory.getSelectedSlot(), previous);
        }
        ServerFactoryPanelBehaviour panel = null;
        for (var slot : slots) {
            var found = ServerFactoryPanelBehaviour.at(level, new FactoryPanelPosition(gauge, slot));
            if (found != null && found.isActive()) {
                panel = found;
                break;
            }
        }
        if (panel == null) throw new IllegalStateException("Gauge did not place");
        panel.setFilter(withItem ? new ItemStack(Items.IRON_INGOT) : ItemStack.EMPTY);
        if (withItem) PolymerFactoryPanelGui.open(player, panel);
        else PolymerFactoryPanelItemGui.open(player, panel);
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static void hold(ServerPlayer player, Session session, ItemStack stack) {
        var inventory = player.getInventory();
        session.savedSlot = inventory.getSelectedSlot();
        session.saved = inventory.getItem(session.savedSlot);
        inventory.setItem(session.savedSlot, stack);
    }

    private static void restoreHand(ServerPlayer player, Session session) {
        if (session.savedSlot < 0) return;
        player.getInventory().setItem(session.savedSlot, session.saved);
        session.savedSlot = -1;
        session.saved = ItemStack.EMPTY;
        player.containerMenu.broadcastChanges();
    }

    private static void record(ServerPlayer player, Entry entry, String verdict, String note) {
        JsonObject result = new JsonObject();
        result.addProperty("title", entry.title());
        result.addProperty("source", entry.source());
        result.addProperty("verdict", verdict);
        result.addProperty("note", note);
        result.addProperty("player", player.getGameProfile().name());
        result.addProperty("time", Instant.now().toString());
        RESULTS.put(entry.id(), result);
        save();
    }

    private static void list(ServerPlayer player) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            JsonObject result = RESULTS.get(entry.id());
            String verdict = result == null ? "unrated" : result.get("verdict").getAsString();
            ChatFormatting color = result == null
                    ? ChatFormatting.GRAY
                    : "correct".equals(verdict) ? ChatFormatting.GREEN : ChatFormatting.RED;
            String note = result == null || result.get("note").getAsString().isEmpty()
                    ? ""
                    : "  " + result.get("note").getAsString();
            player.sendSystemMessage(Component.literal(entry.title() + ": " + verdict + note)
                    .withStyle(style ->
                            style.withColor(color).withClickEvent(new ClickEvent.RunCommand("gr goto " + entry.id()))));
        }
    }

    private static void load(MinecraftServer server) {
        resultsFile =
                server.getWorldPath(LevelResource.ROOT).resolve("polymerpatch").resolve("gui_review.json");
        RESULTS.clear();
        if (!Files.isRegularFile(resultsFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(resultsFile, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var entry : root.entrySet())
                RESULTS.put(entry.getKey(), entry.getValue().getAsJsonObject());
        } catch (IOException | RuntimeException failure) {
            Create.LOGGER.warn("Ignoring unreadable GUI review results {}", resultsFile, failure);
        }
    }

    private static void save() {
        if (resultsFile == null) return;
        JsonObject root = new JsonObject();
        RESULTS.forEach(root::add);
        try {
            Files.createDirectories(resultsFile.getParent());
            Path temporary = resultsFile.resolveSibling(resultsFile.getFileName() + ".tmp");
            Files.writeString(
                    temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            Files.move(temporary, resultsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            Create.LOGGER.warn("Could not save GUI review results {}", resultsFile, failure);
        }
    }
}
