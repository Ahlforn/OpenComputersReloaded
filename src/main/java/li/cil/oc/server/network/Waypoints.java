package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.common.tileentity.WaypointBlockEntity;
import li.cil.oc.util.RTree;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-dimension spatial registry for {@link WaypointBlockEntity} instances.
 * Mirrors {@link WirelessNetwork}: one {@link RTree} per dimension, cleaned up
 * on level/chunk unload to prevent memory leaks.
 */
public final class Waypoints {

    private static final Map<ResourceKey<Level>, RTree<WaypointBlockEntity>> dimensions =
            new ConcurrentHashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Waypoints::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(Waypoints::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(Waypoints::onChunkUnload);
    }

    public static void add(WaypointBlockEntity be) {
        if (be.getLevel() == null) return;
        dimensions.computeIfAbsent(dimension(be), k -> new RTree<>(
                Settings.get().rTreeMaxEntries,
                w -> {
                    BlockPos p = w.getBlockPos();
                    return new double[]{p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5};
                }
        )).add(be);
    }

    public static void remove(WaypointBlockEntity be) {
        if (be.getLevel() == null) return;
        RTree<WaypointBlockEntity> tree = dimensions.get(dimension(be));
        if (tree != null) tree.remove(be);
    }

    /**
     * Returns all registered waypoints within {@code range} blocks of {@code center}
     * in the given level.
     */
    public static List<WaypointBlockEntity> findWaypoints(Level level, BlockPos center, double range) {
        RTree<WaypointBlockEntity> tree = dimensions.get(level.dimension());
        if (tree == null) return Collections.emptyList();
        AABB bounds = new AABB(center).inflate(range * 0.5);
        return tree.query(
                new double[]{bounds.minX, bounds.minY, bounds.minZ},
                new double[]{bounds.maxX, bounds.maxY, bounds.maxZ});
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    private static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof Level level)) return;
        dimensions.remove(level.dimension());
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        dimensions.remove(level.dimension());
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        for (var be : chunk.getBlockEntities().values()) {
            if (be instanceof WaypointBlockEntity wbe) {
                RTree<WaypointBlockEntity> tree = dimensions.get(level.dimension());
                if (tree != null) tree.remove(wbe);
            }
        }
    }

    private static ResourceKey<Level> dimension(WaypointBlockEntity be) {
        return be.getLevel().dimension();
    }

    private Waypoints() {}
}
