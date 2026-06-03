package li.cil.oc.api.prefab;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int index = 0;
        for (Node node : nodes) {
            if (node != null && node.host() == this) {
                final int i = index;
                input.read("oc:node" + i, CompoundTag.CODEC).ifPresent(node::load);
            }
            ++index;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        int index = 0;
        for (Node node : nodes) {
            if (node != null && node.host() == this) {
                final CompoundTag nodeNbt = new CompoundTag();
                node.save(nodeNbt);
                output.store("oc:node" + index, CompoundTag.CODEC, nodeNbt);
            }
            ++index;
        }
    }
}
