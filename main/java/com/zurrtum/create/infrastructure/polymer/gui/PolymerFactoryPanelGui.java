package com.zurrtum.create.infrastructure.polymer.gui;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.logistics.BigItemStack;
import com.zurrtum.create.content.logistics.box.PackageStyles;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.zurrtum.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.zurrtum.create.content.logistics.factoryBoard.ServerFactoryPanelBehaviour;
import com.zurrtum.create.infrastructure.packet.c2s.FactoryPanelConfigurationPacket;
import com.zurrtum.create.infrastructure.packet.c2s.FactoryPanelConnectionPacket;
import com.zurrtum.create.infrastructure.polymer.PolymerInventorySync;
import com.zurrtum.create.infrastructure.polymer.hud.PolymerGogglesOverlay;
import dev.veeda.createpoly.assets.CreateIds;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class PolymerFactoryPanelGui extends SimpleGui {
    private static final int CRAFTING = 10, CONNECT = 19, RELOCATE = 28;
    private static final int[] INPUTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};
    private static final int OUTPUT = 26, RESTOCK_INPUT = 22, LABEL_COVER = 0;
    private static final int LINKS = 45,
            ADDRESS = 46,
            PROMISES = 48,
            EXPIRY = 49,
            RESTOCK = 51,
            RESET = 52,
            CONFIRM = 53;
    private static final int MAX_ADDRESS = 25;

    private static final Identifier ADDRESS_ACTION = CreateIds.id("polymer_factory_panel_address");
    private static final PolymerDialogSessions<AddressEdit> ADDRESS_EDITS =
            new PolymerDialogSessions<>(Duration.ofMinutes(2));
    private static final PolymerDialogForms.TextField ADDRESS_FIELD =
            new PolymerDialogForms.TextField("address", MAX_ADDRESS);

    private static final Map<UUID, Pending> CONNECTING = PolymerDialogSessions.states();

    private static final long CONNECT_NANOS = TimeUnit.SECONDS.toNanos(60);

    private record Pending(FactoryPanelPosition from, ResourceKey<Level> dimension, long expires) {}

    private record AddressEdit(
            ServerFactoryPanelBehaviour behaviour, ServerLevel level, PolymerFactoryPanelGui draft) {}

    private final ServerFactoryPanelBehaviour behaviour;
    private final ServerLevel level;
    private final FactoryPanelPosition position;
    private final boolean restocker;
    private List<FactoryPanelConnection> connections = List.of();
    private List<BigItemStack> inputConfig = List.of();
    private final BigItemStack outputConfig;
    private boolean craftingActive;
    private int promiseExpiration;
    private String address;
    private boolean sendReset, sendRedstoneReset, skipSave;

    private PolymerFactoryPanelGui(ServerPlayer player, ServerFactoryPanelBehaviour behaviour) {
        super(MenuType.GENERIC_9x6, player, false);
        this.behaviour = behaviour;
        this.level = player.level();
        this.position = behaviour.getPanelPosition();
        restocker = behaviour.panelBE().restocker;
        craftingActive = !behaviour.activeCraftingArrangement.isEmpty();
        outputConfig = new BigItemStack(behaviour.getFilter(), behaviour.recipeOutput);
        promiseExpiration = behaviour.promiseClearingInterval;
        address = behaviour.recipeAddress;
        updateConfigs();
        String titleKey =
                restocker ? "create.gui.factory_panel.title_as_restocker" : "create.gui.factory_panel.title_as_recipe";
        var space = new FontDescription.Resource(CreateIds.id("value_board_space"));
        PolymerGuiControls.configure(
                this,
                Component.empty()
                        .append(PolymerGuiTextures.title(
                                restocker ? "factory_panel_restock" : "factory_panel_recipe", Component.empty()))
                        .append(PolymerArtKit.space(
                                space, (160 - PolymerGogglesOverlay.width(PolymerSchematicGuis.lang(titleKey))) / 2))
                        .append(Component.translatable(titleKey)
                                .withStyle(Style.EMPTY.withColor(0x3D3C48).withoutShadow())),
                true);
        refresh();
    }

    public static void open(ServerPlayer player, ServerFactoryPanelBehaviour behaviour) {
        ADDRESS_EDITS.bindToServer();
        if (completeConnection(player, behaviour)) return;
        new PolymerFactoryPanelGui(player, behaviour).open();
    }

    private void updateConfigs() {
        connections = new ArrayList<>(behaviour.targetedBy.values());
        inputConfig = connections.stream()
                .map(c -> {
                    ServerFactoryPanelBehaviour b = ServerFactoryPanelBehaviour.at(level, c.from);
                    return b == null ? new BigItemStack(ItemStack.EMPTY, 0) : new BigItemStack(b.getFilter(), c.amount);
                })
                .toList();
        if (behaviour.craftingList == null) craftingActive = false;
    }

    @Override
    public boolean stillValid() {
        return !player.isRemoved()
                && player.level() == level
                && !player.isSpectator()
                && player.mayBuild()
                && ServerFactoryPanelBehaviour.at(level, position) == behaviour
                && behaviour.isActive()
                && player.mayInteract(level, position.pos())
                && player.isWithinBlockInteractionRange(position.pos(), PolymerGuiControls.MENU_REACH_BUFFER)
                && Create.LOGISTICS.mayInteract(behaviour.network, player);
    }

    @Override
    public void onTick() {
        if (!stillValid()) {
            close();
            return;
        }
        if (inputConfig.size() != behaviour.targetedBy.size()) {
            updateConfigs();
            refresh();
        }
    }

    private void refresh() {
        for (int slot = 0; slot < getSize(); slot++) clearSlot(slot);
        setSlot(
                LABEL_COVER,
                PolymerGuiControls.decoration(
                        PolymerGuiTextures.icon(
                                (restocker ? "factory_panel_restock" : "factory_panel_recipe") + "_slot0"),
                        null));

        if (behaviour.craftingList != null)
            setSlot(
                    CRAFTING,
                    button(
                            PolymerGuiTextures.icon(craftingActive ? "factory_crafting_on" : "factory_crafting"),
                            List.of(lang("gui.factory_panel.activate_crafting")),
                            this::toggleCrafting));
        if (!restocker) {
            setSlot(
                    CONNECT,
                    button(
                            PolymerGuiTextures.icon("factory_connect"),
                            List.of(
                                    lang("gui.factory_panel.connect_input"),
                                    hint("Then right-click a second panel with an empty hand")),
                            this::startConnection));
            setSlot(
                    RELOCATE,
                    PolymerGuiControls.decoration(
                            PolymerGuiTextures.icon("factory_relocate"),
                            List.of(
                                    lang("gui.factory_panel.relocate"),
                                    hint("Not available on vanilla clients: break and re-place the gauge"))));
        }

        if (craftingActive) {
            List<BigItemStack> arrangement = behaviour.craftingList.subList(1, behaviour.craftingList.size());
            for (int i = 0; i < INPUTS.length && i < arrangement.size(); i++) {
                ItemStack stack = arrangement.get(i).stack;
                if (stack.isEmpty()) continue;
                setSlot(
                        INPUTS[i],
                        PolymerGuiControls.decoration(
                                stack.copyWithCount(1),
                                List.of(
                                        lang("gui.factory_panel.crafting_input"),
                                        grey("gui.factory_panel.crafting_input_tip"),
                                        grey("gui.factory_panel.crafting_input_tip_1"))));
            }
        } else if (restocker) {
            ItemStack filter = behaviour.getFilter();
            if (!filter.isEmpty())
                setSlot(
                        RESTOCK_INPUT,
                        PolymerGuiControls.decoration(
                                filter.copyWithCount(1),
                                List.of(
                                        Component.translatable(
                                                        "create.gui.factory_panel.sending_item",
                                                        filter.getHoverName().getString())
                                                .withStyle(ChatFormatting.GOLD),
                                        grey("gui.factory_panel.sending_item_tip"),
                                        grey("gui.factory_panel.sending_item_tip_1"))));
        } else {
            for (int i = 0; i < inputConfig.size() && i < INPUTS.length; i++) setSlot(INPUTS[i], input(i));
            for (int i = inputConfig.size(); i < INPUTS.length; i++)
                setSlot(
                        INPUTS[i],
                        PolymerGuiControls.decoration(
                                PolymerGuiTextures.icon("blank"),
                                List.of(
                                        lang("gui.factory_panel.unconfigured_input"),
                                        grey("gui.factory_panel.unconfigured_input_tip"),
                                        grey("gui.factory_panel.unconfigured_input_tip_1"))));
        }

        if (!restocker) setSlot(OUTPUT, output());

        if (!behaviour.targetedByLinks.isEmpty())
            setSlot(
                    LINKS,
                    button(
                            new ItemStack(AllItems.REDSTONE_LINK),
                            List.of(
                                    lang("gui.factory_panel.has_link_connections"),
                                    italic("gui.factory_panel.left_click_disconnect")),
                            () -> {
                                sendRedstoneReset = true;
                                sendIt(null, false);
                                sendRedstoneReset = false;
                                click();
                                refresh();
                            }));
        setSlot(ADDRESS, addressElement());
        setSlot(PROMISES, promisesElement());
        setSlot(EXPIRY, expiryElement());
        if (restocker) setSlot(RESTOCK, restockAmount());
        setSlot(
                RESET,
                button(PolymerGuiTextures.icon("factory_reset"), List.of(lang("gui.factory_panel.reset")), () -> {
                    sendReset = true;
                    close();
                }));
        setSlot(
                CONFIRM,
                button(
                        PolymerGuiTextures.icon("factory_confirm"),
                        List.of(lang("gui.factory_panel.save_and_close")),
                        this::close));
    }

    private GuiElement input(int i) {
        BigItemStack config = inputConfig.get(i);
        FactoryPanelPosition from = connections.get(i).from;
        if (config.stack.isEmpty())
            return PolymerGuiControls.builder(PolymerGuiTextures.icon("blank"))
                    .setName(lang("gui.factory_panel.empty_panel"))
                    .addLoreLine(italic("gui.factory_panel.left_click_disconnect"))
                    .setCallback((slot, click, action, gui) -> {
                        if (primary(click, action) && click.isLeft) disconnect(from);
                    })
                    .build();
        return PolymerGuiControls.builder(PolymerMarketItems.display(config.stack, config.count))
                .setName(Component.translatable(
                                "create.gui.factory_panel.sending_item",
                                config.stack.getHoverName().getString() + " x" + config.count)
                        .withStyle(ChatFormatting.GOLD))
                .addLoreLine(hint("Left/right-click: +1/-1 (shift: 10)"))
                .addLoreLine(hint("Drop key (Q): disconnect"))
                .setCallback((slot, click, action, gui) -> {
                    if (action == ContainerInput.THROW) {
                        disconnect(from);
                        return;
                    }
                    if (!primary(click, action)) return;
                    config.count = Math.clamp(config.count + (click.isRight ? -1 : 1) * (click.shift ? 10 : 1), 1, 64);
                    refresh();
                })
                .build();
    }

    private GuiElement output() {
        ItemStack filter = behaviour.getFilter();
        ItemStack shown = filter.isEmpty()
                ? PolymerGuiTextures.icon("blank")
                : PolymerMarketItems.display(filter, outputConfig.count);
        var builder = PolymerGuiControls.builder(shown)
                .setName(Component.translatable(
                                "create.gui.factory_panel.expected_output",
                                (filter.isEmpty() ? "" : filter.getHoverName().getString()) + " x" + outputConfig.count)
                        .withStyle(ChatFormatting.GOLD))
                .addLoreLine(grey("gui.factory_panel.expected_output_tip"))
                .addLoreLine(grey("gui.factory_panel.expected_output_tip_1"));
        if (!craftingActive) builder.addLoreLine(grey("gui.factory_panel.expected_output_tip_2"));
        return builder.addLoreLine(hint("Left/right-click: +1/-1 (shift: 10)"))
                .setCallback((slot, click, action, gui) -> {
                    if (!primary(click, action)) return;
                    outputConfig.count =
                            Math.clamp(outputConfig.count + (click.isRight ? -1 : 1) * (click.shift ? 10 : 1), 1, 64);
                    refresh();
                })
                .build();
    }

    private GuiElement restockAmount() {
        ItemStack filter = behaviour.getFilter();
        ItemStack shown = filter.isEmpty()
                ? PolymerGuiTextures.icon("blank")
                : PolymerMarketItems.display(filter, behaviour.count);
        return PolymerGuiControls.builder(shown)
                .setName(Component.literal(
                                "Restock amount: " + (behaviour.upTo ? "up to " : "exactly ") + behaviour.count)
                        .withStyle(ChatFormatting.GOLD))
                .addLoreLine(hint("Left/right-click: +1/-1 (shift: 10)"))
                .addLoreLine(hint("Drop key (Q): up to / exactly"))
                .setCallback((slot, click, action, gui) -> {
                    if (action == ContainerInput.THROW) behaviour.upTo = !behaviour.upTo;
                    else if (primary(click, action))
                        behaviour.count =
                                Math.clamp(behaviour.count + (click.isRight ? -1 : 1) * (click.shift ? 10 : 1), 1, 64);
                    else return;
                    behaviour.panelBE().setChanged();
                    behaviour.panelBE().sendData();
                    refresh();
                })
                .build();
    }

    private GuiElement addressElement() {
        List<Component> lines = new ArrayList<>();
        if (address.isBlank()) {
            String key = restocker ? "gui.factory_panel.restocker_address" : "gui.factory_panel.recipe_address";
            lines.add(lang(key));
            lines.add(grey(key + "_tip"));
            lines.add(grey(key + "_tip_1"));
            lines.add(italic("gui.schedule.lmb_edit"));
        } else {
            lines.add(lang(
                    restocker
                            ? "gui.factory_panel.restocker_address_given"
                            : "gui.factory_panel.recipe_address_given"));
            lines.add(Component.literal("'" + address + "'").withStyle(ChatFormatting.GRAY));
            lines.add(italic("gui.schedule.lmb_edit"));
        }
        return button(PolymerGuiTextures.icon("factory_address"), lines, this::editAddress);
    }

    private GuiElement promisesElement() {
        int promised = behaviour.getPromised();
        ItemStack box = PackageStyles.getDefaultBox().copyWithCount(Math.clamp(promised, 1, 99));
        List<Component> lines = promised == 0
                ? List.of(
                        lang("gui.factory_panel.no_open_promises"),
                        grey(
                                restocker
                                        ? "gui.factory_panel.restocker_promises_tip"
                                        : "gui.factory_panel.recipe_promises_tip"),
                        grey(
                                restocker
                                        ? "gui.factory_panel.restocker_promises_tip_1"
                                        : "gui.factory_panel.recipe_promises_tip_1"),
                        grey("gui.factory_panel.promise_prevents_oversending"))
                : List.of(
                        lang("gui.factory_panel.promised_items"),
                        Component.literal(behaviour.getFilter().getHoverName().getString() + " x" + promised),
                        italic("gui.factory_panel.left_click_reset"));
        return button(box, lines, () -> {
            sendIt(null, true);
            click();
            refresh();
        });
    }

    private GuiElement expiryElement() {
        String shown = promiseExpiration == -1 ? "/" : promiseExpiration == 0 ? "30s" : promiseExpiration + "m";
        ItemStack value = PolymerGuiTextures.icon("blank");
        value.setCount(Math.clamp(promiseExpiration, 1, 30));
        return PolymerGuiControls.builder(value)
                .setName(lang("gui.factory_panel.promises_expire_title"))
                .addLoreLine(Component.literal(shown).withStyle(ChatFormatting.WHITE))
                .addLoreLine(hint("Left/right-click: +1/-1 (shift: 10)"))
                .setCallback((slot, click, action, gui) -> {
                    if (!primary(click, action)) return;
                    promiseExpiration =
                            Math.clamp(promiseExpiration + (click.isRight ? -1 : 1) * (click.shift ? 10 : 1), -1, 30);
                    refresh();
                })
                .build();
    }

    private void toggleCrafting() {
        if (behaviour.craftingList == null) return;
        craftingActive = !craftingActive;
        if (craftingActive) outputConfig.count = behaviour.craftingList.getFirst().count;
        refresh();
    }

    private void disconnect(FactoryPanelPosition from) {
        if (craftingActive) return;
        sendIt(from, false);
        click();
        updateConfigs();
        refresh();
    }

    private void startConnection() {
        CONNECTING.put(player.getUUID(), new Pending(position, level.dimension(), System.nanoTime() + CONNECT_NANOS));
        close();
        player.sendOverlayMessage(Component.translatable("create.factory_panel.click_second_panel"));
    }

    private static boolean completeConnection(ServerPlayer player, ServerFactoryPanelBehaviour panel) {
        Pending pending = CONNECTING.remove(player.getUUID());
        if (pending == null) return false;
        ServerLevel level = player.level();
        ServerFactoryPanelBehaviour at = pending.expires() < System.nanoTime()
                        || !level.dimension().equals(pending.dimension())
                        || !pending.from().pos().closerThan(player.blockPosition(), 16)
                ? null
                : ServerFactoryPanelBehaviour.at(level, pending.from());
        if (panel.getPanelPosition().equals(pending.from()) || at == null) {
            player.sendOverlayMessage(Component.empty());
            return at != null;
        }
        String issue = issue(at, panel);
        if (issue != null) {
            player.sendOverlayMessage(Component.translatable("create." + issue).withStyle(ChatFormatting.RED));
            return true;
        }
        if (!Create.LOGISTICS.mayInteract(at.network, player) || !Create.LOGISTICS.mayInteract(panel.network, player)) {
            player.sendOverlayMessage(Component.translatable("create.polymer.factory_panel.connection.error.permission")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        ItemStack filterFrom = panel.getFilter(), filterTo = at.getFilter();
        AllHandle.onFactoryPanelConnection(
                player.connection, new FactoryPanelConnectionPacket(panel.getPanelPosition(), pending.from(), false));
        player.sendOverlayMessage(Component.translatable(
                        "create.factory_panel.panels_connected",
                        filterFrom.getHoverName().getString(),
                        filterTo.getHoverName().getString())
                .withStyle(ChatFormatting.GREEN));
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 0.5f);
        return true;
    }

    private static @Nullable String issue(ServerFactoryPanelBehaviour from, ServerFactoryPanelBehaviour to) {
        if (from.targetedBy.containsKey(to.getPanelPosition())) return "factory_panel.already_connected";
        if (from.targetedBy.size() >= 9) return "factory_panel.cannot_add_more_inputs";
        BlockState state1 = to.blockEntity.getBlockState(), state2 = from.blockEntity.getBlockState();
        BlockPos diff = to.getPos().subtract(from.getPos());
        if (state1.setValue(FactoryPanelBlock.WATERLOGGED, false).setValue(FactoryPanelBlock.POWERED, false)
                != state2.setValue(FactoryPanelBlock.WATERLOGGED, false).setValue(FactoryPanelBlock.POWERED, false))
            return "factory_panel.same_orientation";
        if (FactoryPanelBlock.connectedDirection(state1).getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0)
            return "factory_panel.same_surface";
        if (!diff.closerThan(BlockPos.ZERO, 16)) return "factory_panel.too_far_apart";
        if (to.panelBE().restocker) return "factory_panel.input_in_restock_mode";
        if (to.getFilter().isEmpty() || from.getFilter().isEmpty()) return "factory_panel.no_item";
        return null;
    }

    private void editAddress() {
        skipSave = true;
        close();
        CompoundTag action = ADDRESS_EDITS.open(player.getUUID(), new AddressEdit(behaviour, level, this));
        String key = restocker ? "gui.factory_panel.restocker_address" : "gui.factory_panel.recipe_address";
        Input input = ADDRESS_FIELD.input(200, lang(key), address);
        var common = new CommonDialogData(
                Component.translatable(
                        restocker
                                ? "create.gui.factory_panel.title_as_restocker"
                                : "create.gui.factory_panel.title_as_recipe"),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(grey(key + "_tip"), 200), new PlainMessage(grey(key + "_tip_1"), 200)),
                List.of(input));
        Holder<Dialog> dialog = Holder.direct(new NoticeDialog(
                common,
                new ActionButton(
                        new CommonButtonData(PolymerGuiTextures.actionLabel("Confirm"), 200),
                        Optional.of(new CustomAll(ADDRESS_ACTION, Optional.of(action))))));
        PolymerDialogForms.open(player, dialog);
    }

    static boolean handleAddress(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!ADDRESS_ACTION.equals(id)) return false;
        if (!PolymerDialogForms.validSubmission(player, id, payload, ADDRESS_ACTION)) return true;
        var data = PolymerDialogForms.payload(payload);
        if (data.isEmpty()) return true;
        var session = ADDRESS_EDITS.consume(player.getUUID(), data.get());
        if (session.isEmpty()) return true;
        AddressEdit edit = session.get();
        PolymerFactoryPanelGui draft = edit.draft();
        Optional<String> text = ADDRESS_FIELD.read(data.get());
        if (text.isPresent() && text.get().codePoints().noneMatch(Character::isISOControl))
            draft.address = text.get().trim();
        if (draft.getPlayer() != player
                || ServerFactoryPanelBehaviour.at(edit.level(), draft.position) != edit.behaviour()) return true;
        PolymerFactoryPanelGui reopened = new PolymerFactoryPanelGui(player, edit.behaviour());
        reopened.address = draft.address;
        reopened.craftingActive = draft.craftingActive && edit.behaviour().craftingList != null;
        reopened.outputConfig.count = draft.outputConfig.count;
        reopened.promiseExpiration = draft.promiseExpiration;
        if (reopened.connections.equals(draft.connections))
            for (int i = 0; i < reopened.inputConfig.size(); i++)
                reopened.inputConfig.get(i).count = draft.inputConfig.get(i).count;
        if (!reopened.stillValid()) return true;
        reopened.refresh();
        reopened.open();
        return true;
    }

    private void sendIt(@Nullable FactoryPanelPosition toRemove, boolean clearPromises) {
        Map<FactoryPanelPosition, Integer> inputs = new HashMap<>();
        if (inputConfig.size() == connections.size()) {
            for (int i = 0; i < inputConfig.size(); i++) {
                BigItemStack stackInConfig = inputConfig.get(i);
                inputs.put(
                        connections.get(i).from,
                        craftingActive
                                ? (int) behaviour.craftingList.stream()
                                        .skip(1)
                                        .filter(b -> !b.stack.isEmpty()
                                                && ItemStack.isSameItemSameComponents(b.stack, stackInConfig.stack))
                                        .count()
                                : stackInConfig.count);
            }
        }
        List<ItemStack> arrangement = craftingActive && behaviour.craftingList != null
                ? behaviour.craftingList.stream().skip(1).map(b -> b.stack).toList()
                : List.of();
        AllHandle.onFactoryPanelConfiguration(
                player.connection,
                new FactoryPanelConfigurationPacket(
                        position,
                        address,
                        inputs,
                        arrangement,
                        outputConfig.count,
                        promiseExpiration,
                        toRemove,
                        clearPromises,
                        sendReset,
                        sendRedstoneReset));
    }

    @Override
    public void onRemoved() {
        if (!skipSave && stillValid()) sendIt(null, false);
        skipSave = true;
        PolymerInventorySync.afterMenu(player);
    }

    private static boolean primary(ClickType click, ContainerInput action) {
        return PolymerGuiControls.primaryClick(click, action);
    }

    private void click() {
        level.playSound(
                null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.25f, 1);
    }

    private GuiElement button(ItemStack stack, List<Component> lines, Runnable action) {
        var builder = PolymerGuiControls.builder(stack).setName(lines.getFirst());
        for (Component line : lines.subList(1, lines.size())) builder.addLoreLine(line);
        return builder.setCallback((slot, click, action2, gui) -> {
                    if (primary(click, action2) && click.isLeft && stillValid()) action.run();
                })
                .build();
    }

    private static MutableComponent lang(String key) {
        return Component.translatable("create." + key)
                .withStyle(style -> style.withColor(0x5391E1).withItalic(false));
    }

    private static MutableComponent grey(String key) {
        return Component.translatable("create." + key).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent italic(String key) {
        return Component.translatable("create." + key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    private static MutableComponent hint(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }
}
