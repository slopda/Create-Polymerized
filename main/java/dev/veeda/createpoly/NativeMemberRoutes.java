package dev.veeda.createpoly;

import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public final class NativeMemberRoutes {
    private static final String POLICY =
            "com/zurrtum/create/infrastructure/polymer/nativeclient/NativePresentationPolicy";
    private static final Set<String> RECEIVERS = Set.of(
            "net/minecraft/world/entity/player/Player",
            "net/minecraft/server/level/ServerPlayer",
            "net/minecraft/server/network/ServerGamePacketListenerImpl",
            "net/minecraft/network/protocol/game/ServerGamePacketListener",
            "net/minecraft/world/item/context/UseOnContext",
            "net/minecraft/world/item/context/BlockPlaceContext");

    private NativeMemberRoutes() {}

    private static String key(MethodNode method) {
        return method.name + method.desc;
    }

    public static void apply(ClassNode target, ClassNode delta) throws Exception {
        var path = FabricLoader.getInstance()
                .getModContainer("create")
                .orElseThrow()
                .findPath(target.name + ".class")
                .orElseThrow();
        ClassNode original = new ClassNode();
        new ClassReader(Files.readAllBytes(path)).accept(original, 0);
        apply(target, delta, original, name -> {
            try {
                if (name.startsWith("com/zurrtum/create/")) {
                    var source = FabricLoader.getInstance()
                            .getModContainer("create")
                            .orElseThrow()
                            .findPath(name + ".class")
                            .orElseThrow();
                    ClassNode node = new ClassNode();
                    new ClassReader(Files.readAllBytes(source)).accept(node, 0);
                    return node;
                }
                try (var stream = NativeMemberRoutes.class.getClassLoader().getResourceAsStream(name + ".class")) {
                    if (stream == null) return null;
                    ClassNode node = new ClassNode();
                    new ClassReader(stream).accept(node, 0);
                    return node;
                }
            } catch (Exception error) {
                throw new IllegalStateException("Cannot resolve native parent " + name, error);
            }
        });
    }

    public static void apply(
            ClassNode target, ClassNode delta, ClassNode original, Function<String, ClassNode> parents) {
        Map<String, MethodNode> upstream = new HashMap<>();
        original.methods.forEach(method -> upstream.put(key(method), method));
        Set<String> modified = new HashSet<>();
        delta.methods.forEach(method -> modified.add(key(method)));
        Map<String, String> copies = new LinkedHashMap<>();
        Map<String, Integer> receivers = new LinkedHashMap<>();
        for (MethodNode method : delta.methods) {
            if (method.name.startsWith("<")) continue;
            int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0, receiver = -1;
            for (Type type : Type.getArgumentTypes(method.desc)) {
                if (type.getSort() == Type.OBJECT && RECEIVERS.contains(type.getInternalName())) {
                    receiver = slot;
                    break;
                }
                slot += type.getSize();
            }
            if (receiver < 0 && !method.name.equals("getAddEntityPacket")) continue;
            if (!upstream.containsKey(key(method))) {
                if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
                MethodNode inherited = inherited(original.superName, key(method), parents);
                if (inherited == null) continue;
                MethodNode bridge =
                        new MethodNode(Opcodes.ASM9, method.access, method.name, method.desc, method.signature, null);
                bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                int argumentSlot = 1;
                for (Type type : Type.getArgumentTypes(method.desc)) {
                    bridge.instructions.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), argumentSlot));
                    argumentSlot += type.getSize();
                }
                bridge.instructions.add(
                        new MethodInsnNode(Opcodes.INVOKESPECIAL, original.superName, method.name, method.desc, false));
                bridge.instructions.add(
                        new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));
                bridge.maxStack = argumentSlot + 2;
                bridge.maxLocals = argumentSlot;
                upstream.put(key(method), bridge);
            }
            if (upstream.get(key(method)).instructions.size() == 0) continue;
            receivers.put(key(method), receiver);
            collect(target.name, key(method), modified, upstream, copies);
        }
        boolean isInterface = (target.access & Opcodes.ACC_INTERFACE) != 0;
        for (var entry : copies.entrySet()) {
            MethodNode source = upstream.get(entry.getKey());
            MethodNode copy = new MethodNode(
                    Opcodes.ASM9,
                    source.access,
                    source.name,
                    source.desc,
                    source.signature,
                    source.exceptions.toArray(String[]::new));
            source.accept(copy);
            copy.name = entry.getValue();
            copy.access = (copy.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED))
                    | Opcodes.ACC_PRIVATE
                    | Opcodes.ACC_SYNTHETIC;
            for (AbstractInsnNode at : copy.instructions) {
                if (at instanceof MethodInsnNode call
                        && call.owner.equals(target.name)
                        && copies.containsKey(call.name + call.desc)) {
                    call.name = copies.get(call.name + call.desc);
                    if (call.getOpcode() != Opcodes.INVOKESTATIC) call.setOpcode(Opcodes.INVOKESPECIAL);
                    call.itf = isInterface;
                } else if (at instanceof InvokeDynamicInsnNode dynamic) {
                    for (int i = 0; i < dynamic.bsmArgs.length; i++)
                        if (dynamic.bsmArgs[i] instanceof Handle handle
                                && handle.getOwner().equals(target.name)
                                && copies.containsKey(handle.getName() + handle.getDesc())) {
                            boolean isStatic = handle.getTag() == Opcodes.H_INVOKESTATIC;
                            dynamic.bsmArgs[i] = new Handle(
                                    isStatic ? Opcodes.H_INVOKESTATIC : Opcodes.H_INVOKESPECIAL,
                                    target.name,
                                    copies.get(handle.getName() + handle.getDesc()),
                                    handle.getDesc(),
                                    isInterface);
                        }
                }
            }
            if (target.methods.stream()
                    .anyMatch(method -> method.name.equals(copy.name) && method.desc.equals(copy.desc)))
                throw new IllegalStateException("Duplicate native member route " + target.name + "." + copy.name);
            target.methods.add(copy);
        }
        for (var entry : receivers.entrySet()) {
            MethodNode method = target.methods.stream()
                    .filter(value -> key(value).equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow();
            InsnList guard = new InsnList();
            LabelNode fallback = new LabelNode();
            if (entry.getValue() < 0) guard.add(new InsnNode(Opcodes.ACONST_NULL));
            else guard.add(new VarInsnNode(Opcodes.ALOAD, entry.getValue()));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, POLICY, "useOriginal", "(Ljava/lang/Object;)Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
            int slot = 0;
            if ((method.access & Opcodes.ACC_STATIC) == 0) {
                guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
                slot = 1;
            }
            for (Type type : Type.getArgumentTypes(method.desc)) {
                guard.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), slot));
                slot += type.getSize();
            }
            guard.add(new MethodInsnNode(
                    (method.access & Opcodes.ACC_STATIC) != 0 ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL,
                    target.name,
                    copies.get(entry.getKey()),
                    method.desc,
                    isInterface));
            guard.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));
            guard.add(fallback);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            method.maxStack = Math.max(method.maxStack, slot + 2);
        }
    }

    private static MethodNode inherited(String owner, String key, Function<String, ClassNode> parents) {
        while (owner != null) {
            ClassNode node = parents.apply(owner);
            if (node == null) return null;
            for (MethodNode method : node.methods)
                if (key(method).equals(key))
                    return (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) == 0
                            ? method
                            : null;
            owner = node.superName;
        }
        return null;
    }

    private static void collect(
            String owner,
            String key,
            Set<String> modified,
            Map<String, MethodNode> upstream,
            Map<String, String> copies) {
        if (copies.containsKey(key)) return;
        MethodNode source = upstream.get(key);
        copies.put(
                key, "polymerpatch$native$" + source.name + "$" + Integer.toUnsignedString(source.desc.hashCode(), 16));
        for (AbstractInsnNode at : source.instructions) {
            if (at instanceof MethodInsnNode call
                    && !call.name.startsWith("<")
                    && call.owner.equals(owner)
                    && modified.contains(call.name + call.desc)
                    && upstream.containsKey(call.name + call.desc))
                collect(owner, call.name + call.desc, modified, upstream, copies);
            if (at instanceof InvokeDynamicInsnNode dynamic)
                for (Object arg : dynamic.bsmArgs)
                    if (arg instanceof Handle handle
                            && !handle.getName().startsWith("<")
                            && handle.getOwner().equals(owner)
                            && modified.contains(handle.getName() + handle.getDesc())
                            && upstream.containsKey(handle.getName() + handle.getDesc()))
                        collect(owner, handle.getName() + handle.getDesc(), modified, upstream, copies);
        }
    }
}
