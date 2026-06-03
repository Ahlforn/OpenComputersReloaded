package li.cil.oc.api.prefab;

import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * If you wish to create item components such as the network card or hard drives
 * you will need an item driver.
 * <br>
 * This prefab allows creating a driver that works for a specified list of item
 * stacks (to support different items with the same id but different damage
 * values). It also takes care of creating and getting the tag compound on an
 * item stack to save data to or load data from.
 * <br>
 * You still have to specify your component's slot type and provide the
 * implementation for creating its environment, if any.
 *
 * @see li.cil.oc.api.network.ManagedEnvironment
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class DriverItem implements li.cil.oc.api.driver.DriverItem {
    protected final ItemStack[] items;

    protected DriverItem(final ItemStack... items) {
        this.items = items.clone();
    }

    @Override
    public boolean worksWith(final ItemStack stack) {
        if (!stack.isEmpty()) {
            for (ItemStack item : items) {
                if (!item.isEmpty() && item.getItem() == stack.getItem()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int tier(final ItemStack stack) {
        return 0;
    }

    @Override
    public CompoundTag dataTag(final ItemStack stack) {
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root;
        if (existing == null) {
            root = new CompoundTag();
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        } else {
            root = existing.copyTag();
            // Re-set so edits to 'root' are reflected when saved
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
        if (!root.contains("oc:data")) {
            root.put("oc:data", new CompoundTag());
        }
        return root.getCompound("oc:data");
    }

    // Convenience methods provided for HostAware drivers.

    protected boolean isAdapter(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Adapter.class.isAssignableFrom(host);
    }

    protected boolean isComputer(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Case.class.isAssignableFrom(host);
    }

    protected boolean isRobot(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Robot.class.isAssignableFrom(host);
    }

    protected boolean isRotatable(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Rotatable.class.isAssignableFrom(host);
    }

    protected boolean isServer(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Server.class.isAssignableFrom(host);
    }

    protected boolean isTablet(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);
    }
}
