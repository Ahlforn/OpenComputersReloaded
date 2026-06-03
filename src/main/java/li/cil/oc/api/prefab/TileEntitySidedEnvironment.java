package li.cil.oc.api.prefab;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntities can implement the {@link li.cil.oc.api.network.SidedEnvironment}
 * interface to allow them to interact with the component network, by providing
 * a separate {@link li.cil.oc.api.network.Node} for each block face, and
 * connecting it to said network. This allows more control over connectivity
 * than the simple {@link li.cil.oc.api.network.Environment}.
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class TileEntitySidedEnvironment extends BlockEntity implements SidedEnvironment {
    protected Node[] nodes = new Node[6];
    protected boolean addedToNetwork = false;

    protected TileEntitySidedEnvironment(BlockEntityType<?> type, BlockPos pos, BlockState state, final Node... nodes) {
        super(type, pos, state);
        System.arraycopy(nodes, 0, this.nodes, 0, Math.min(nodes.length, this.nodes.length));
    }

    @Override
    public Node sidedNode(final Direction side) {
        return nodes[side.ordinal()];
    }

    /** Call this from your block entity ticker on the server side. */
    public void serverTick() {
        if (!addedToNetwork) {
            addedToNetwork = true;
            Network.joinOrCreateNetwork(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        for (Node node : nodes) {
            if (node != null) node.remove();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        for (Node node : nodes) {
            if (node != null) node.remove();
        }
    }

    @Override
    public void loadAdditional(final CompoundTag nbt, final HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        int index = 0;
        for (Node node : nodes) {
            if (node != null && node.host() == this) {
                node.load(nbt.getCompound("oc:node" + index));
            }
            ++index;
        }
    }

    @Override
    public void saveAdditional(CompoundTag nbt, final HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        int index = 0;
        for (Node node : nodes) {
            if (node != null && node.host() == this) {
                final CompoundTag nodeNbt = new CompoundTag();
                node.save(nodeNbt);
                nbt.put("oc:node" + index, nodeNbt);
            }
            ++index;
        }
    }
}
