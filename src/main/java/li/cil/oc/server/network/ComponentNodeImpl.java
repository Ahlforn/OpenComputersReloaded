package li.cil.oc.server.network;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;

import java.util.Collection;

/**
 * A node that is also a {@link Component} — enumerable and callable from computers. Faithful port of
 * the Scala {@code Component} trait; callback discovery/dispatch is delegated to {@link ComponentSupport}.
 */
class ComponentNodeImpl extends NodeImpl implements Component {

    private final String name;
    private Visibility visibility;
    private final ComponentSupport support;

    ComponentNodeImpl(Environment host, Visibility reachability, String name, Visibility visibility) {
        super(host, reachability);
        this.name = name;
        this.visibility = visibility;
        this.support = new ComponentSupport(host);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Visibility visibility() {
        return visibility;
    }

    @Override
    public void setVisibility(Visibility value) {
        if (value.ordinal() > reachability.ordinal()) {
            throw new IllegalArgumentException("Trying to set computer visibility to '" + value
                    + "' on a '" + name + "' node with reachability '" + reachability
                    + "'. It will be limited to the node's reachability.");
        }
        ComponentSupport.changeVisibility(this, this.visibility, value);
        this.visibility = value;
    }

    @Override
    public boolean canBeSeenFrom(Node other) {
        return switch (visibility) {
            case None -> false;
            case Network -> canBeReachedFrom(other);
            case Neighbors -> isNeighborOf(other);
        };
    }

    @Override
    public Collection<String> methods() {
        return support.methods();
    }

    @Override
    public Callback annotation(String method) {
        return support.annotation(method);
    }

    @Override
    public Object[] invoke(String method, Context context, Object... arguments) throws Exception {
        return support.invoke(method, context, arguments);
    }

    // ----------------------------------------------------------------------- //

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("visibility")) {
            int ord = nbt.getIntOr("visibility", -1);
            Visibility[] values = Visibility.values();
            if (ord >= 0 && ord < values.length) visibility = values[ord];
        }
    }

    @Override
    public void save(CompoundTag nbt) {
        super.save(nbt);
        nbt.putInt("visibility", visibility.ordinal());
    }

    @Override
    public String toString() {
        return super.toString() + "@" + name;
    }
}
