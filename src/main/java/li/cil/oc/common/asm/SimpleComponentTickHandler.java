package li.cil.oc.common.asm;

import li.cil.oc.api.Network;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/**
 * Defers {@link Network#joinOrCreateNetwork} calls for simple components to
 * the next server tick start, avoiding network-join during world loading.
 *
 * <p>Register {@link #INSTANCE} on the NeoForge game event bus once at mod
 * startup.
 */
public final class SimpleComponentTickHandler {

    public static final SimpleComponentTickHandler INSTANCE = new SimpleComponentTickHandler();

    private static final Logger LOG = LogManager.getLogger("OpenComputers");

    public static final ArrayList<Runnable> pending = new ArrayList<>();

    private SimpleComponentTickHandler() {}

    public static void schedule(BlockEntity blockEntity) {
        synchronized (pending) {
            pending.add(() -> Network.joinOrCreateNetwork(blockEntity));
        }
    }

    @SubscribeEvent
    public void onServerTickPre(ServerTickEvent.Pre event) {
        final Runnable[] tasks;
        synchronized (pending) {
            if (pending.isEmpty()) return;
            tasks = pending.toArray(new Runnable[0]);
            pending.clear();
        }
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.warn("Error in SimpleComponent tick action.", t);
            }
        }
    }
}
