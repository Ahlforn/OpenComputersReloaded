package li.cil.oc.server.network;

import li.cil.oc.OpenComputersMod;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.UUID;

/**
 * Mutable server-side {@link Node} implementation — a faithful port of the Scala {@code Node} trait
 * (plus {@code NodeVarargPart}). Graph adjacency is NOT stored here; it lives in the owning
 * {@link NetworkImpl}'s {@code Vertex}/{@code Edge} structure, so neighbour/reachability queries
 * delegate to the network.
 */
class NodeImpl implements Node {
    final Environment host;
    final Visibility reachability;

    String address = null;
    NetworkImpl network = null;

    NodeImpl(Environment host, Visibility reachability) {
        this.host = host;
        this.reachability = reachability;
    }

    @Override
    public Environment host() {
        return host;
    }

    @Override
    public Visibility reachability() {
        return reachability;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public li.cil.oc.api.network.Network network() {
        return network;
    }

    @Override
    public boolean canBeReachedFrom(Node other) {
        return switch (reachability) {
            case None -> false;
            case Neighbors -> isNeighborOf(other);
            case Network -> isInSameNetwork(other);
        };
    }

    @Override
    public boolean isNeighborOf(Node other) {
        if (!isInSameNetwork(other)) return false;
        for (Node n : network.neighbors(this)) {
            if (n == other) return true;
        }
        return false;
    }

    @Override
    public Iterable<Node> reachableNodes() {
        return network == null ? Collections.emptyList() : network.nodes(this);
    }

    @Override
    public Iterable<Node> neighbors() {
        return network == null ? Collections.emptyList() : network.neighbors(this);
    }

    // A node should be added to a network before it can connect to a node, but sometimes other mods
    // try to create nodes and connect them before the network is ready. We don't want those to crash.
    @Override
    public void connect(Node other) {
        if (network != null) network.connect(this, other);
    }

    @Override
    public void disconnect(Node other) {
        if (network != null && isInSameNetwork(other)) network.disconnect(this, other);
    }

    @Override
    public void remove() {
        if (network != null) network.remove(this);
    }

    @Override
    public void sendToAddress(String target, String name, Object... data) {
        if (network != null) network.sendToAddress(this, target, name, data);
    }

    @Override
    public void sendToNeighbors(String name, Object... data) {
        if (network != null) network.sendToNeighbors(this, name, data);
    }

    @Override
    public void sendToReachable(String name, Object... data) {
        if (network != null) network.sendToReachable(this, name, data);
    }

    @Override
    public void sendToVisible(String name, Object... data) {
        if (network != null) network.sendToVisible(this, name, data);
    }

    // ----------------------------------------------------------------------- //

    @Override
    public void load(CompoundTag nbt) {
        if (nbt.contains("address")) {
            String newAddress = nbt.getStringOr("address", "");
            if (!newAddress.isEmpty() && !newAddress.equals(address)) {
                if (network != null) network.remap(this, newAddress);
                else address = newAddress;
            }
        }
    }

    @Override
    public void save(CompoundTag nbt) {
        if (address != null) nbt.putString("address", address);
    }

    // ----------------------------------------------------------------------- //

    void onConnect(Node node) {
        try {
            host.onConnect(node);
        } catch (Throwable e) {
            OpenComputersMod.LOGGER.warn("A component of type '{}' threw an error while being connected to the component network.", host.getClass().getName(), e);
        }
    }

    void onDisconnect(Node node) {
        try {
            host.onDisconnect(node);
        } catch (Throwable e) {
            OpenComputersMod.LOGGER.warn("A component of type '{}' threw an error while being disconnected from the component network.", host.getClass().getName(), e);
        }
    }

    // ----------------------------------------------------------------------- //

    private boolean isInSameNetwork(Node other) {
        return network != null && other != null && network == other.network();
    }

    static String randomAddress() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String toString() {
        return "Node(" + address + ", " + host + ")";
    }
}
