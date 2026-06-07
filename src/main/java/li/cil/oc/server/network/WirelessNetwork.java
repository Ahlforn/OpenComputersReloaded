package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.network.WirelessEndpoint;
import li.cil.oc.util.RTree;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial registry of wireless endpoints, one {@link RTree} per dimension. Faithful port of the
 * Scala {@code WirelessNetwork}, with two adaptations for MC 1.21+: dimensions are keyed by their
 * {@link ResourceKey} (no longer integer ids), and world/chunk events use NeoForge's
 * {@code LevelEvent}/{@code ChunkEvent}.
 */
public final class WirelessNetwork {

    private static final Map<ResourceKey<Level>, RTree<WirelessEndpoint>> dimensions = new ConcurrentHashMap<>();

    private WirelessNetwork() {
    }

    /** Registers the world/chunk cleanup listeners on the NeoForge game event bus. */
    public static void init() {
        NeoForge.EVENT_BUS.addListener(WirelessNetwork::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(WirelessNetwork::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(WirelessNetwork::onChunkUnload);
    }

    // ----------------------------------------------------------------------- //
    // Event handlers
    // ----------------------------------------------------------------------- //

    private static void onLevelLoad(LevelEvent.Load e) {
        if (e.getLevel() instanceof Level level && !level.isClientSide()) {
            dimensions.remove(level.dimension());
        }
    }

    private static void onLevelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof Level level && !level.isClientSide()) {
            dimensions.remove(level.dimension());
        }
    }

    // Safety clean up, in case some block entities didn't properly leave the net.
    private static void onChunkUnload(ChunkEvent.Unload e) {
        if (e.getChunk() instanceof LevelChunk chunk) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof WirelessEndpoint endpoint) remove(endpoint);
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // Registry operations
    // ----------------------------------------------------------------------- //

    public static void add(WirelessEndpoint endpoint) {
        dimensions.computeIfAbsent(dimension(endpoint), k -> new RTree<>(
                Settings.get().rTreeMaxEntries,
                e -> new double[]{e.x() + 0.5, e.y() + 0.5, e.z() + 0.5})).add(endpoint);
    }

    public static void update(WirelessEndpoint endpoint) {
        RTree<WirelessEndpoint> tree = dimensions.get(dimension(endpoint));
        if (tree == null) return;
        double[] pos = tree.position(endpoint);
        if (pos == null) return;
        double dx = Math.abs(endpoint.x() + 0.5 - pos[0]);
        double dy = Math.abs(endpoint.y() + 0.5 - pos[1]);
        double dz = Math.abs(endpoint.z() + 0.5 - pos[2]);
        if (dx > 0.5 || dy > 0.5 || dz > 0.5) {
            tree.remove(endpoint);
            tree.add(endpoint);
        }
    }

    public static boolean remove(WirelessEndpoint endpoint) {
        RTree<WirelessEndpoint> tree = dimensions.get(dimension(endpoint));
        return tree != null && tree.remove(endpoint);
    }

    /**
     * Legacy int-dimension removal. MC 1.21 dimensions are {@link ResourceKey}s, not integer ids, so
     * the id cannot be mapped; as a safe fallback the endpoint is removed from every tracked
     * dimension (used when an endpoint changed dimension and only the change can be reacted to).
     */
    public static boolean remove(WirelessEndpoint endpoint, int dimension) {
        boolean removed = false;
        for (RTree<WirelessEndpoint> tree : dimensions.values()) {
            removed |= tree.remove(endpoint);
        }
        return removed;
    }

    public static List<WirelessEndpoint> computeReachableFrom(WirelessEndpoint endpoint, double strength) {
        RTree<WirelessEndpoint> tree = dimensions.get(dimension(endpoint));
        if (tree == null || strength <= 0) return List.of();
        double range = strength + 1;
        List<WirelessEndpoint> result = new ArrayList<>();
        for (WirelessEndpoint other : tree.query(offset(endpoint, -range), offset(endpoint, range))) {
            if (other == endpoint) continue;
            double squared = squaredDistance(endpoint, other);
            if (squared > range * range) continue;
            if (isUnobstructed(endpoint, strength, other, Math.sqrt(squared))) {
                result.add(other);
            }
        }
        return result;
    }

    // ----------------------------------------------------------------------- //
    // Internals
    // ----------------------------------------------------------------------- //

    private static ResourceKey<Level> dimension(WirelessEndpoint endpoint) {
        return endpoint.world().dimension();
    }

    private static double[] offset(WirelessEndpoint endpoint, double value) {
        return new double[]{endpoint.x() + 0.5 + value, endpoint.y() + 0.5 + value, endpoint.z() + 0.5 + value};
    }

    private static double squaredDistance(WirelessEndpoint reference, WirelessEndpoint endpoint) {
        double dx = endpoint.x() - reference.x();
        double dy = endpoint.y() - reference.y();
        double dz = endpoint.z() - reference.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Approximate line-of-sight check: sample a few points along the path (more for longer gaps) and
     * subtract the hardness of any blocks hit from the surplus signal strength. Faithful port of the
     * Scala obstruction sampling.
     */
    private static boolean isUnobstructed(WirelessEndpoint reference, double strength, WirelessEndpoint endpoint, double distance) {
        double gap = distance - 1;
        if (gap <= 0) return true;

        Level world = endpoint.world();
        Vec3 origin = new Vec3(reference.x(), reference.y(), reference.z());
        Vec3 target = new Vec3(endpoint.x(), endpoint.y(), endpoint.z());

        Vec3 v = target.subtract(origin).normalize();
        // Orthogonal vectors to the direction, used to jitter the samples.
        Vec3 up = (v.x == 0 && v.z == 0) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = v.cross(up);
        Vec3 top = v.cross(side);

        double hardness = 0.0;
        int samples = Math.max(1, (int) Math.sqrt(gap));
        RandomSource rand = world.getRandom();
        for (int i = 0; i < samples; i++) {
            double rGap = rand.nextDouble() * gap;
            int rSide = rand.nextInt(3) - 1;
            int rTop = rand.nextInt(3) - 1;
            int x = (int) (origin.x + v.x * rGap + side.x * rSide + top.x * rTop);
            int y = (int) (origin.y + v.y * rGap + side.y * rSide + top.y * rTop);
            int z = (int) (origin.z + v.z * rGap + side.z * rSide + top.z * rTop);
            BlockPos pos = new BlockPos(x, y, z);
            if (world.isLoaded(pos)) {
                BlockState state = world.getBlockState(pos);
                hardness += state.getDestroySpeed(world, pos);
            }
        }

        // Normalize and scale obstructions, then check we have enough power left.
        hardness *= gap / samples;
        return strength - gap > hardness;
    }
}
