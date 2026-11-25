package com.zurrtum.create.infrastructure.polymer.nativeclient;

import io.netty.channel.ChannelFutureListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

public final class NativePresentationPolicy {
    private static final String ADAPTER = "com.zurrtum.create.infrastructure.polymer.";
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ThreadLocal<ServerPlayer> PAIRING = new ThreadLocal<>();
    private static final ThreadLocal<ClientboundMoveEntityPacket.Rot> OBSERVER_ROTATION = new ThreadLocal<>();
    private static final ThreadLocal<PacketListener> CONTROL_ABILITIES = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EXPLICIT_PLAYER_LOOKUP = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PRESENTATION_SEND = new ThreadLocal<>();
    public static volatile BiConsumer<String, Object> fallbackObserver;

    private NativePresentationPolicy() {}

    private static boolean observed(String fence, Object subject, boolean decided) {
        var observer = fallbackObserver;
        if (decided && observer != null) observer.accept(fence, subject);
        return decided;
    }

    public static boolean allowsPresentation(ServerPlayer receiver) {
        return receiver != null && presentationClient(receiver.getUUID());
    }

    public static boolean allowsAction(Player actor) {
        return actor instanceof ServerPlayer player && allowsPresentation(player);
    }

    public static boolean allowsCommand(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer player) || allowsPresentation(player);
    }

    public static void sendPresentation(ServerPlayer receiver, Packet<?> packet) {
        if (!allowsPresentation(receiver)) return;
        withPresentation(() -> receiver.connection.send(packet));
    }

    public static void sendPresentation(Connection receiver, Packet<?> packet) {
        if (nativeReceiver(receiver)) return;
        withPresentation(() -> receiver.send(packet));
    }

    public static void sendPresentation(
            Connection receiver, Packet<?> packet, ChannelFutureListener completion, boolean flush) {
        withPresentation(() -> receiver.send(packet, completion, flush));
    }

    static void withPresentation(Runnable send) {
        Boolean previous = PRESENTATION_SEND.get();
        PRESENTATION_SEND.set(true);
        try {
            send.run();
        } finally {
            if (previous == null) PRESENTATION_SEND.remove();
            else PRESENTATION_SEND.set(previous);
        }
    }

    public static boolean presentationClient(UUID receiver) {
        return receiver != null && !PolymerNativeClients.isNative(receiver);
    }

    public static List<ServerPlayer> onlinePlayers(PlayerList players) {
        return withPlayerLookup(players::getPlayers);
    }

    public static ServerPlayer onlinePlayer(PlayerList players, UUID id) {
        return withPlayerLookup(() -> players.getPlayer(id));
    }

    public static List<ServerPlayer> presentationPlayers(PlayerList players) {
        return onlinePlayers(players).stream()
                .filter(NativePresentationPolicy::allowsPresentation)
                .toList();
    }

    public static ServerPlayer presentationPlayer(PlayerList players, UUID id) {
        ServerPlayer player = onlinePlayer(players, id);
        return allowsPresentation(player) ? player : null;
    }

    static <T> T withPlayerLookup(Supplier<T> lookup) {
        Boolean previous = EXPLICIT_PLAYER_LOOKUP.get();
        EXPLICIT_PLAYER_LOOKUP.set(true);
        try {
            return lookup.get();
        } finally {
            if (previous == null) EXPLICIT_PLAYER_LOOKUP.remove();
            else EXPLICIT_PLAYER_LOOKUP.set(previous);
        }
    }

    public static boolean nativeReceiver(Object receiver) {
        if (receiver instanceof UseOnContext context) return nativeReceiver(context.getPlayer());
        if (receiver instanceof ServerPlayer player) return PolymerNativeClients.isNative(player);
        if (receiver instanceof ServerGamePacketListenerImpl listener)
            return PolymerNativeClients.isNative(listener.player);
        if (receiver instanceof ServerConfigurationPacketListenerImpl listener)
            return listener.getOwner() != null
                    && PolymerNativeClients.isNative(listener.getOwner().id());
        if (receiver instanceof Connection connection) return nativeReceiver(connection.getPacketListener());
        return false;
    }

    public static boolean useOriginal(Object receiver) {
        if (receiver != null) return nativeReceiver(receiver);
        ServerPlayer player = PAIRING.get();
        return player != null
                ? PolymerNativeClients.isNative(player)
                : PolymerNativeClients.isNative(PacketContext.get());
    }

    public static ServerPlayer enterPairing(ServerPlayer player) {
        ServerPlayer previous = PAIRING.get();
        PAIRING.set(player);
        return previous;
    }

    public static void exitPairing(ServerPlayer previous) {
        if (previous == null) PAIRING.remove();
        else PAIRING.set(previous);
    }

    private static boolean adapterClass(String name) {
        return name.startsWith(ADAPTER) && !nativeClientClass(name);
    }

    private static boolean nativeClientClass(String name) {
        String simple = simpleName(name);
        return simple.startsWith("Native") || simple.startsWith("PolymerNative");
    }

    private static String simpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    public static boolean adapterPacket(Connection connection, Packet<?> packet) {
        if (controlAbilitySync(connection.getPacketListener(), packet)) return false;
        if (packet == OBSERVER_ROTATION.get()) return false;
        if (packet.type().id().getNamespace().equals("create")) return false;
        if (Boolean.TRUE.equals(PRESENTATION_SEND.get())) return true;
        return observed(
                "packet",
                packet,
                WALKER.walk(frames -> frames.filter(
                                frame -> frame.getClassName().startsWith("com.zurrtum.create.")
                                        && !(frame.getClassName().startsWith(ADAPTER)
                                                && nativeClientClass(frame.getClassName())))
                        .findFirst()
                        .map(frame -> adapterClass(frame.getClassName()) || injectedAdapter(frame.getMethodName()))
                        .orElse(false)));
    }

    static void syncControlAbilities(ServerPlayer player) {
        withControlAbilitySync(player.connection, player::onUpdateAbilities);
    }

    static void withControlAbilitySync(PacketListener receiver, Runnable update) {
        PacketListener previous = CONTROL_ABILITIES.get();
        CONTROL_ABILITIES.set(receiver);
        try {
            update.run();
        } finally {
            if (previous == null) CONTROL_ABILITIES.remove();
            else CONTROL_ABILITIES.set(previous);
        }
    }

    static boolean controlAbilitySync(PacketListener receiver, Packet<?> packet) {
        return receiver != null
                && receiver == CONTROL_ABILITIES.get()
                && packet instanceof ClientboundPlayerAbilitiesPacket;
    }

    public static void sendObserverRotation(ServerPlayer viewer, ClientboundMoveEntityPacket.Rot rotation) {
        if (!PolymerNativeClients.isNative(viewer)) return;
        ClientboundMoveEntityPacket.Rot previous = OBSERVER_ROTATION.get();
        OBSERVER_ROTATION.set(rotation);
        try {
            viewer.connection.send(rotation);
        } finally {
            if (previous == null) OBSERVER_ROTATION.remove();
            else OBSERVER_ROTATION.set(previous);
        }
    }

    private static boolean injectedAdapter(String method) {
        return method.startsWith("handler$") || method.startsWith("redirect$") || method.startsWith("wrapOperation$");
    }

    public static boolean adapterPlayerLookup() {
        if (Boolean.TRUE.equals(EXPLICIT_PLAYER_LOOKUP.get())) return false;
        return observed(
                "lookup",
                null,
                WALKER.walk(frames -> frames.filter(frame -> frame.getDeclaringClass() != NativePresentationPolicy.class
                                && frame.getDeclaringClass() != PlayerList.class)
                        .map(StackWalker.StackFrame::getClassName)
                        .findFirst()
                        .map(name -> adapterClass(name) && !simpleName(name).startsWith("PolymerBacktankWear"))
                        .orElse(false)));
    }

    public static Object playerCallback(Object callback) {
        if (!adapterClass(callback.getClass().getName())) return callback;
        Class<?>[] interfaces = callback.getClass().getInterfaces();
        List<Class<?>> playerInterfaces = Arrays.stream(interfaces)
                .filter(type -> type.getName().startsWith("net.fabricmc.fabric.api.event.player.")
                        && (type.getSimpleName().endsWith("Callback")
                                || type.getSimpleName().equals("Before")))
                .toList();
        if (playerInterfaces.isEmpty()) return callback;
        return Proxy.newProxyInstance(callback.getClass().getClassLoader(), interfaces, (proxy, method, args) -> {
            if (observed(
                    "callback",
                    callback.getClass().getName(),
                    args != null && Arrays.stream(args).anyMatch(NativePresentationPolicy::nativeReceiver))) {
                if (method.getReturnType() == InteractionResult.class) return InteractionResult.PASS;
                if (method.getReturnType() == boolean.class) return true;
                if (method.getReturnType() == void.class) return null;
            }
            try {
                return method.invoke(callback, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        });
    }
}
