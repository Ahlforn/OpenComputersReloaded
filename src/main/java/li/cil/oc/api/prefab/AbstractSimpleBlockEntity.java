package li.cil.oc.api.prefab;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SimpleComponent;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.common.asm.SimpleComponentTickHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Base class for third-party block entities that want to expose Lua callbacks
 * to OpenComputers without writing a full block driver.
 *
 * <p>Subclasses must implement {@link #getComponentName()} and may override
 * {@link #onConnect}, {@link #onDisconnect}, and {@link #onMessage} to react
 * to network events. Annotate methods with
 * {@link li.cil.oc.api.machine.Callback} to expose them to Lua.
 *
 * <p>This class replaces the old ASM-injection approach ({@code SimpleComponent}
 * on a plain {@code TileEntity}) from the 1.12.2 era.
 */
public abstract class AbstractSimpleBlockEntity extends BlockEntity
        implements SimpleComponent, ManagedEnvironment {

    private static final String TAG_NODE = "oc:node";

    protected Node node;

    protected AbstractSimpleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // -----------------------------------------------------------------------
    // SimpleComponent — subclasses must provide the component name
    // -----------------------------------------------------------------------

    @Override
    public abstract String getComponentName();

    // -----------------------------------------------------------------------
    // ManagedEnvironment
    // -----------------------------------------------------------------------

    @Override
    public Node node() {
        return node;
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public void update() {}

    @Override
    public void onConnect(Node node) {}

    @Override
    public void onDisconnect(Node node) {}

    @Override
    public void onMessage(Message message) {}

    // -----------------------------------------------------------------------
    // BlockEntity lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            if (node == null) {
                node = Network.newNode(this, Visibility.Network)
                    .withComponent(getComponentName(), Visibility.Neighbors)
                    .create();
            }
            SimpleComponentTickHandler.schedule(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (node != null) {
            node.remove();
            node = null;
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (node != null) {
            node.remove();
            node = null;
        }
    }

    // -----------------------------------------------------------------------
    // NBT persistence
    // -----------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (node != null && node.host() == this) {
            input.read(TAG_NODE, CompoundTag.CODEC).ifPresent(node::load);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (node != null && node.host() == this) {
            CompoundTag nodeTag = new CompoundTag();
            node.save(nodeTag);
            output.store(TAG_NODE, CompoundTag.CODEC, nodeTag);
        }
    }
}
