package li.cil.oc.api.prefab;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entities can implement {@link Environment} to interact with the OC
 * component network by providing a {@link Node}.
 *
 * <p>Set {@link #node} in the subclass constructor.
 * Use {@link li.cil.oc.api.Network#newNode} to create the node.
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class TileEntityEnvironment extends BlockEntity implements Environment {

    private static final String TAG_NODE = "oc:node";

    protected Node node;

    protected TileEntityEnvironment(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Node node() { return node; }

    @Override
    public void onConnect(Node node) {}

    @Override
    public void onDisconnect(Node node) {}

    @Override
    public void onMessage(Message message) {}

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            Network.joinOrCreateNetwork(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (node != null) node.remove();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (node != null) node.remove();
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (node != null && node.host() == this && tag.contains(TAG_NODE)) {
            node.load(tag.getCompound(TAG_NODE));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (node != null && node.host() == this) {
            CompoundTag nodeTag = new CompoundTag();
            node.save(nodeTag);
            tag.put(TAG_NODE, nodeTag);
        }
    }
}
