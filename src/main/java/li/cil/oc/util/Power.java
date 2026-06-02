package li.cil.oc.util;

import li.cil.oc.Settings;

public final class Power {

    private static double ratio() {
        Settings s = Settings.get();
        return s != null ? s.ratioForgeEnergy : 0.1;
    }

    public static double fromRF(long rf) {
        return rf * ratio();
    }

    public static long toRF(double oc) {
        return (long) (oc / ratio());
    }

    private Power() {}
}
