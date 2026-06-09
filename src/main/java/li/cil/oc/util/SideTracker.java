package li.cil.oc.util;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SideTracker {
    private static volatile Thread serverThread = null;
    private static final Set<Thread> serverThreads =
        Collections.newSetFromMap(new WeakHashMap<>());

    public static void addServerThread() {
        serverThreads.add(Thread.currentThread());
    }

    // Called from the ServerStartingEvent subscriber in OpenComputersMod.
    public static void onServerStarting(ServerStartingEvent event) {
        serverThread = Thread.currentThread();
        serverThreads.add(serverThread);
    }

    public static boolean isServer() {
        if (FMLEnvironment.getDist() == Dist.DEDICATED_SERVER) return true;
        Thread current = Thread.currentThread();
        return current == serverThread || serverThreads.contains(current);
    }

    public static boolean isClient() {
        return !isServer();
    }
}
