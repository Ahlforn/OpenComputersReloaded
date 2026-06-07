package li.cil.oc.common.tileentity;

import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.TileEntityEnvironment;
import li.cil.oc.common.block.Waypoint;
import li.cil.oc.common.init.Registries;
import li.cil.oc.server.network.Waypoints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Port of {@code Waypoint.scala}. Provides a network node that is also registered
 * in the spatial {@link Waypoints} registry so other components can query nearby
 * waypoints by label via {@link Waypoints#findWaypoints}.
 */
public class WaypointBlockEntity extends TileEntityEnvironment {

    private static final String LABEL_TAG = Settings.namespace + "label";
    private static final int MAX_LABEL_LENGTH = 32;

    public String label = "";

    public WaypointBlockEntity(BlockPos pos, BlockState state) {
        super(Registries.WAYPOINT_BE.get(), pos, state);
        node = Network.newNode(this, Visibility.Network)
                .withComponent("waypoint")
                .create();
    }

    // -------------------------------------------------------------------------
    // Callbacks
    // -------------------------------------------------------------------------

    @Callback(doc = "function():string -- Returns the waypoint label.")
    public Object[] getLabel(Context ctx, Arguments args) {
        return new Object[]{label};
    }

    @Callback(doc = "function(value:string) -- Sets the waypoint label (max 32 chars).")
    public Object[] setLabel(Context ctx, Arguments args) {
        String value = args.checkString(0);
        label = value.substring(0, Math.min(MAX_LABEL_LENGTH, value.length()));
        ctx.pause(0.5);
        return null;
    }

    // -------------------------------------------------------------------------
    // Lifecycle — register/unregister with spatial registry
    // -------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            Waypoints.add(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            Waypoints.remove(this);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide()) {
            Waypoints.remove(this);
        }
        super.onChunkUnloaded();
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        label = input.getStringOr(LABEL_TAG, "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(LABEL_TAG, label);
    }

    // -------------------------------------------------------------------------
    // Client-side particle animation (called by Waypoint.getTicker on client)
    // -------------------------------------------------------------------------

    public void clientTick(Level lvl, BlockState state) {
        Direction pitch = state.getValue(Waypoint.PITCH);
        Direction yaw = state.getValue(Waypoint.YAW);
        Direction facing = (pitch != Direction.NORTH) ? pitch : yaw;

        Vec3 origin = Vec3.atCenterOf(getBlockPos()).add(
                facing.getStepX() * 0.5,
                facing.getStepY() * 0.5,
                facing.getStepZ() * 0.5);

        RandomSource rand = lvl.getRandom();
        double dx = (rand.nextFloat() - 0.5f) * 0.8;
        double dy = (rand.nextFloat() - 0.5f) * 0.8;
        double dz = (rand.nextFloat() - 0.5f) * 0.8;
        double vx = (rand.nextFloat() - 0.5f) * 0.2 + facing.getStepX() * 0.3;
        double vy = (rand.nextFloat() - 0.5f) * 0.2 + facing.getStepY() * 0.3 - 0.5;
        double vz = (rand.nextFloat() - 0.5f) * 0.2 + facing.getStepZ() * 0.3;

        lvl.addParticle(ParticleTypes.PORTAL,
                origin.x + dx, origin.y + dy, origin.z + dz,
                vx, vy, vz);
    }
}
