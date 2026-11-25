package com.zurrtum.create.infrastructure.polymer.contraption;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;

public final class PolymerShiftDiagnostics {
    public interface Recorder {
        void record(UUID viewer, long gameTime, Collection<? extends Packet<?>> bundle);

        void gauge(UUID viewer, long gameTime, Object carrier, int cells, int copies, boolean seated);

        void forgetCarrier(UUID viewer, Object carrier);

        void forget(UUID viewer);

        void clear();
    }

    private static Recorder recorder;

    private PolymerShiftDiagnostics() {}

    public static void install(Recorder installed) {
        if (recorder != null) throw new IllegalStateException("Shift traffic recorder already installed");
        recorder = installed;
    }

    static void record(UUID viewer, long gameTime, Collection<? extends Packet<?>> bundle) {
        if (recorder != null) recorder.record(viewer, gameTime, bundle);
    }

    static void gauge(UUID viewer, long gameTime, Object carrier, int cells, int copies, boolean seated) {
        if (recorder != null) recorder.gauge(viewer, gameTime, carrier, cells, copies, seated);
    }

    static void forgetCarrier(UUID viewer, Object carrier) {
        if (recorder != null) recorder.forgetCarrier(viewer, carrier);
    }

    static void forget(UUID viewer) {
        if (recorder != null) recorder.forget(viewer);
    }

    static void clear() {
        if (recorder != null) recorder.clear();
    }
}
