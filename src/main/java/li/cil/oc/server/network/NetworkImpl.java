package li.cil.oc.server.network;

import li.cil.oc.OpenComputersMod;
import li.cil.oc.Settings;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Faithful Java port of the Scala {@code Network} (and its {@code Wrapper}/{@code Distributor}). The
 * connection graph is stored as a set of {@link Vertex}/{@link Edge} objects keyed by node address;
 * connect/disconnect/remove mutate the graph and then replay a queue of {@code onConnect} /
 * {@code onDisconnect} notifications, exactly mirroring the original implementation (including its
 * deliberately redundant broadcast of network-visible nodes, which hosts are written to tolerate).
 *
 * <p>The Scala code splits public ({@code Wrapper}, immutable-node API) from internal
 * ({@code Network}, mutable nodes); here the two are collapsed into this single class that implements
 * both {@link li.cil.oc.api.network.Network} and {@link Distributor}.
 */
class NetworkImpl implements li.cil.oc.api.network.Network, Distributor {

    private final Map<String, Vertex> data;
    private final List<ConnectorNodeImpl> connectors = new ArrayList<>();

    double globalBuffer = 0.0;
    double globalBufferSize = 0.0;

    NetworkImpl(NodeImpl node) {
        this(new HashMap<>());
        addNew(node);
        node.onConnect(node);
    }

    private NetworkImpl(Map<String, Vertex> data) {
        this.data = data;
        for (Vertex vertex : data.values()) {
            if (vertex.data instanceof ConnectorNodeImpl c) addConnector(c);
            vertex.data.network = this;
        }
    }

    /** Mirrors {@code Network.joinNewNetwork}: wrap a node in a fresh network if it has none. */
    static void joinNewNetwork(NodeImpl node) {
        if (node.network == null) new NetworkImpl(node);
    }

    // ----------------------------------------------------------------------- //
    // Graph mutation
    // ----------------------------------------------------------------------- //

    @Override
    public boolean connect(Node nodeA, Node nodeB) {
        NodeImpl a = (NodeImpl) nodeA;
        NodeImpl b = (NodeImpl) nodeB;
        if (a == b) {
            throw new IllegalArgumentException("Cannot connect a node to itself.");
        }

        Vertex oldNodeA = data.get(a.address);
        Vertex oldNodeB = data.get(b.address);

        boolean containsA = contains(a) && oldNodeA != null && oldNodeA.data == a;
        boolean containsB = contains(b) && oldNodeB != null && oldNodeB.data == b;

        if (!containsA && !containsB) {
            throw new IllegalArgumentException("At least one of the nodes must be in this network.");
        }

        if (containsA && containsB) {
            // Both nodes already exist in the network but there may be a new connection.
            boolean exists = false;
            for (Edge edge : oldNodeA.edges) {
                if (edge.isBetween(oldNodeA, oldNodeB)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                new Edge(oldNodeA, oldNodeB);
                if (oldNodeA.data.reachability == Visibility.Neighbors) oldNodeB.data.onConnect(oldNodeA.data);
                if (oldNodeB.data.reachability == Visibility.Neighbors) oldNodeA.data.onConnect(oldNodeB.data);
                return true;
            }
            return false;
        } else if (containsA) {
            return add(oldNodeA, b);
        } else {
            return add(oldNodeB, a);
        }
    }

    @Override
    public boolean disconnect(Node nodeA, Node nodeB) {
        NodeImpl a = (NodeImpl) nodeA;
        NodeImpl b = (NodeImpl) nodeB;
        if (a == b) {
            throw new IllegalArgumentException("Cannot disconnect a node from itself.");
        }
        if (!contains(a) || !contains(b)) {
            throw new IllegalArgumentException("Both nodes must be in this network.");
        }

        Vertex va = data.get(a.address);
        Vertex vb = data.get(b.address);
        for (Edge edge : va.edges) {
            if (edge.isBetween(va, vb)) {
                handleSplit(edge.remove());
                if (edge.left.data.reachability == Visibility.Neighbors) edge.right.data.onDisconnect(edge.left.data);
                if (edge.right.data.reachability == Visibility.Neighbors) edge.left.data.onDisconnect(edge.right.data);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean remove(Node node) {
        NodeImpl n = (NodeImpl) node;
        Vertex entry = data.remove(n.address);
        if (entry == null || entry.data != n) {
            if (entry != null) data.put(n.address, entry); // not actually ours; restore
            return false;
        }

        if (n instanceof ConnectorNodeImpl c) removeConnector(c);
        n.network = null;

        List<Map<String, Vertex>> subGraphs = entry.remove();

        // Determine who must be told that this node disconnected, before the split is finalized.
        List<NodeImpl> targets = new ArrayList<>();
        targets.add(n);
        switch (n.reachability) {
            case None -> {
            }
            case Neighbors -> {
                for (Edge edge : entry.edges) targets.add(edge.other(entry).data);
            }
            case Network -> {
                for (Map<String, Vertex> sub : subGraphs) {
                    for (Vertex v : sub.values()) targets.add(v.data);
                }
            }
        }

        handleSplit(subGraphs);
        for (NodeImpl target : targets) target.onDisconnect(n);
        return true;
    }

    /**
     * Mirrors {@code Network.remap}: re-key a node to a new address, splitting and reconnecting via
     * its neighbours. Triggered from NBT load when a node carries a different address (rare; happens
     * with world-edit / block cloning).
     */
    void remap(NodeImpl remappedNode, String newAddress) {
        Vertex node = data.get(remappedNode.address);
        if (node == null || node.data != remappedNode) {
            throw new IllegalArgumentException("Node not in this network.");
        }
        List<NodeImpl> neighbors = new ArrayList<>();
        for (Edge edge : node.edges) neighbors.add(edge.other(node).data);

        node.data.remove();
        node.data.address = newAddress;
        while (data.containsKey(node.data.address)) {
            node.data.address = NodeImpl.randomAddress();
        }

        if (node.data.address.equals(newAddress)) {
            if (neighbors.isEmpty()) {
                addNew(node.data);
            } else {
                for (NodeImpl neighbor : neighbors) {
                    if (neighbor.network != null) neighbor.connect(node.data);
                }
            }
        } else {
            OpenComputersMod.LOGGER.error("Could not remap node to address '{}' due to a conflict.", newAddress);
            node.data.remove();
        }
    }

    // ----------------------------------------------------------------------- //
    // Queries
    // ----------------------------------------------------------------------- //

    @Override
    public Node node(String address) {
        Vertex vertex = data.get(address);
        return vertex == null ? null : vertex.data;
    }

    @Override
    public Iterable<Node> nodes() {
        List<Node> result = new ArrayList<>(data.size());
        for (Vertex vertex : data.values()) result.add(vertex.data);
        return result;
    }

    @Override
    public Iterable<Node> nodes(Node reference) {
        return reachableNodes((NodeImpl) reference);
    }

    @Override
    public Iterable<Node> neighbors(Node node) {
        Vertex vertex = data.get(node.address());
        if (vertex != null && vertex.data == node) {
            List<Node> result = new ArrayList<>(vertex.edges.size());
            for (Edge edge : vertex.edges) result.add(edge.other(vertex).data);
            return result;
        }
        throw new IllegalArgumentException("Node must be in this network.");
    }

    private Iterable<Node> reachableNodes(NodeImpl reference) {
        Set<NodeImpl> referenceNeighbors = new HashSet<>(neighborsOf(reference));
        List<Node> result = new ArrayList<>();
        for (Vertex vertex : data.values()) {
            NodeImpl n = vertex.data;
            if (n != reference && (n.reachability == Visibility.Network
                    || (n.reachability == Visibility.Neighbors && referenceNeighbors.contains(n)))) {
                result.add(n);
            }
        }
        return result;
    }

    private List<NodeImpl> reachingNodes(NodeImpl reference) {
        List<NodeImpl> result = new ArrayList<>();
        if (reference.reachability == Visibility.Network) {
            for (Vertex vertex : data.values()) if (vertex.data != reference) result.add(vertex.data);
        } else if (reference.reachability == Visibility.Neighbors) {
            Set<NodeImpl> referenceNeighbors = new HashSet<>(neighborsOf(reference));
            for (Vertex vertex : data.values()) {
                if (vertex.data != reference && referenceNeighbors.contains(vertex.data)) result.add(vertex.data);
            }
        }
        return result;
    }

    private List<NodeImpl> neighborsOf(NodeImpl n) {
        Vertex vertex = data.get(n.address);
        List<NodeImpl> result = new ArrayList<>();
        if (vertex != null && vertex.data == n) {
            for (Edge edge : vertex.edges) result.add(edge.other(vertex).data);
        }
        return result;
    }

    private List<NodeImpl> nodesList() {
        List<NodeImpl> result = new ArrayList<>(data.size());
        for (Vertex vertex : data.values()) result.add(vertex.data);
        return result;
    }

    private boolean contains(NodeImpl node) {
        return node.network == this && data.containsKey(node.address);
    }

    // ----------------------------------------------------------------------- //
    // Messaging
    // ----------------------------------------------------------------------- //

    @Override
    public void sendToAddress(Node source, String target, String name, Object... data) {
        ensureSource(source);
        Vertex vertex = this.data.get(target);
        if (vertex != null && vertex.data.canBeReachedFrom(source)) {
            send(source, List.of(vertex.data), name, data);
        }
    }

    @Override
    public void sendToNeighbors(Node source, String name, Object... data) {
        ensureSource(source);
        List<Node> targets = new ArrayList<>();
        for (Node neighbor : neighbors(source)) {
            if (((NodeImpl) neighbor).reachability != Visibility.None) targets.add(neighbor);
        }
        send(source, targets, name, data);
    }

    @Override
    public void sendToReachable(Node source, String name, Object... data) {
        ensureSource(source);
        send(source, reachableNodes((NodeImpl) source), name, data);
    }

    @Override
    public void sendToVisible(Node source, String name, Object... data) {
        ensureSource(source);
        List<Node> targets = new ArrayList<>();
        for (Node node : reachableNodes((NodeImpl) source)) {
            if (node instanceof Component component && component.canBeSeenFrom(source)) targets.add(node);
        }
        send(source, targets, name, data);
    }

    private void ensureSource(Node source) {
        if (source.network() != this) {
            throw new IllegalArgumentException("Source node must be in this network.");
        }
    }

    private void send(Node source, Iterable<Node> targets, String name, Object[] args) {
        Message message = new MessageImpl(source, name, args);
        for (Node target : targets) {
            target.host().onMessage(message);
        }
    }

    // ----------------------------------------------------------------------- //
    // Distributor (energy)
    // ----------------------------------------------------------------------- //

    @Override
    public double globalBuffer() {
        return globalBuffer;
    }

    @Override
    public void globalBuffer(double value) {
        globalBuffer = value;
    }

    @Override
    public double globalBufferSize() {
        return globalBufferSize;
    }

    @Override
    public void globalBufferSize(double value) {
        globalBufferSize = value;
    }

    @Override
    public void addConnector(ConnectorNodeImpl connector) {
        if (connector.localBufferSize > 0) {
            connectors.add(connector);
            globalBuffer += connector.localBuffer;
            globalBufferSize += connector.localBufferSize;
        }
        connector.distributor = this;
    }

    @Override
    public void removeConnector(ConnectorNodeImpl connector) {
        if (connector.localBufferSize > 0) {
            connectors.remove(connector);
            globalBuffer -= connector.localBuffer;
            globalBufferSize -= connector.localBufferSize;
        }
    }

    @Override
    public double changeBuffer(double delta) {
        if (delta == 0) return 0;
        if (Settings.get().ignorePower) return delta < 0 ? 0 : delta;
        synchronized (this) {
            double oldBuffer = globalBuffer;
            globalBuffer = Math.min(Math.max(globalBuffer + delta, 0), globalBufferSize);
            if (globalBuffer == oldBuffer) {
                return delta;
            }
            if (delta < 0) {
                double remaining = -delta;
                for (ConnectorNodeImpl connector : connectors) {
                    if (remaining <= 0) break;
                    if (connector.localBuffer > 0) {
                        if (connector.localBuffer < remaining) {
                            remaining -= connector.localBuffer;
                            connector.localBuffer = 0;
                        } else {
                            connector.localBuffer -= remaining;
                            remaining = 0;
                        }
                    }
                }
                return -remaining;
            } else {
                double remaining = delta;
                for (ConnectorNodeImpl connector : connectors) {
                    if (remaining <= 0) break;
                    if (connector.localBuffer < connector.localBufferSize) {
                        double space = connector.localBufferSize - connector.localBuffer;
                        if (space < remaining) {
                            remaining -= space;
                            connector.localBuffer = connector.localBufferSize;
                        } else {
                            connector.localBuffer += remaining;
                            remaining = 0;
                        }
                    }
                }
                return remaining;
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // Internals
    // ----------------------------------------------------------------------- //

    private Vertex addNew(NodeImpl node) {
        Vertex newNode = new Vertex(node);
        if (node.address == null || data.containsKey(node.address)) {
            node.address = NodeImpl.randomAddress();
        }
        data.put(node.address, newNode);
        if (node instanceof ConnectorNodeImpl c) addConnector(c);
        node.network = this;
        return newNode;
    }

    /** A deferred {@code onConnect} broadcast: every {@code observer.onConnect(subject)}. */
    private record Notify(NodeImpl subject, List<NodeImpl> observers) {
    }

    private boolean add(Vertex oldNode, NodeImpl addedNode) {
        List<Notify> connects = new ArrayList<>();

        if (addedNode.network == null) {
            Vertex newNode = addNew(addedNode);
            new Edge(oldNode, newNode);

            switch (addedNode.reachability) {
                case None -> connects.add(new Notify(addedNode, List.of(addedNode)));
                case Neighbors -> {
                    List<NodeImpl> observers = new ArrayList<>();
                    observers.add(addedNode);
                    observers.addAll(neighborsOf(addedNode));
                    connects.add(new Notify(addedNode, observers));
                    for (NodeImpl node : reachingNodes(addedNode)) connects.add(new Notify(node, List.of(addedNode)));
                }
                case Network -> {
                    List<NodeImpl> observers = new ArrayList<>();
                    observers.add(addedNode);
                    for (Vertex vertex : data.values()) if (vertex.data != addedNode) observers.add(vertex.data);
                    connects.add(new Notify(addedNode, observers));
                    for (NodeImpl node : reachingNodes(addedNode)) connects.add(new Notify(node, List.of(addedNode)));
                }
            }

            // The added node may load more internal nodes as part of its onConnect, so do it now and
            // re-read the node set before broadcasting network-visible nodes.
            addedNode.onConnect(addedNode);
            List<NodeImpl> allNodes = nodesList();
            for (Vertex vertex : data.values()) {
                if (vertex.data.reachability == Visibility.Network) connects.add(new Notify(vertex.data, allNodes));
            }
        } else {
            NetworkImpl otherNetwork = addedNode.network;

            // We need to merge the two networks. If the networks share addresses (can happen with
            // NBT editing / block cloning) we re-assign the duplicates in the other network first.
            List<Vertex> duplicates = new ArrayList<>();
            for (Map.Entry<String, Vertex> entry : otherNetwork.data.entrySet()) {
                if (data.containsKey(entry.getKey())) duplicates.add(entry.getValue());
            }

            NetworkImpl otherNetworkAfterReaddress;
            if (duplicates.isEmpty()) {
                otherNetworkAfterReaddress = otherNetwork;
            } else {
                for (Vertex vertex : duplicates) {
                    NodeImpl node = vertex.data;
                    List<NodeImpl> neighbors = new ArrayList<>();
                    for (Edge edge : vertex.edges) neighbors.add(edge.other(vertex).data);

                    String newAddress;
                    do {
                        newAddress = NodeImpl.randomAddress();
                    } while (data.containsKey(newAddress) || otherNetwork.data.containsKey(newAddress));

                    node.remove();
                    node.address = newAddress;
                    joinNewNetwork(node);

                    if (node.address.equals(newAddress)) {
                        for (NodeImpl neighbor : neighbors) {
                            if (neighbor.network != null) neighbor.connect(node);
                        }
                    } else {
                        OpenComputersMod.LOGGER.error("Address conflict could not be resolved; removing node.");
                        node.remove();
                    }
                }
                otherNetworkAfterReaddress = (NetworkImpl) duplicates.get(0).data.network;
            }

            if (addedNode.network != null && addedNode.network == otherNetworkAfterReaddress) {
                if (addedNode.reachability == Visibility.Neighbors) {
                    connects.add(new Notify(addedNode, List.of(oldNode.data)));
                }
                if (oldNode.data.reachability == Visibility.Neighbors) {
                    connects.add(new Notify(oldNode.data, List.of(addedNode)));
                }

                List<NodeImpl> oldNodes = nodesList();
                List<NodeImpl> newNodes = otherNetworkAfterReaddress.nodesList();
                List<NodeImpl> newVisibleNodes = new ArrayList<>();
                for (NodeImpl n : newNodes) if (n.reachability == Visibility.Network) newVisibleNodes.add(n);
                List<NodeImpl> oldVisibleNodes = new ArrayList<>();
                for (NodeImpl n : oldNodes) if (n.reachability == Visibility.Network) oldVisibleNodes.add(n);

                for (NodeImpl node : newVisibleNodes) connects.add(new Notify(node, oldNodes));
                for (NodeImpl node : oldVisibleNodes) connects.add(new Notify(node, newNodes));

                data.putAll(otherNetworkAfterReaddress.data);
                connectors.addAll(otherNetworkAfterReaddress.connectors);
                globalBuffer += otherNetworkAfterReaddress.globalBuffer;
                globalBufferSize += otherNetworkAfterReaddress.globalBufferSize;
                for (Vertex vertex : otherNetworkAfterReaddress.data.values()) {
                    if (vertex.data instanceof ConnectorNodeImpl c) c.distributor = this;
                    vertex.data.network = this;
                }
                otherNetworkAfterReaddress.data.clear();
                otherNetworkAfterReaddress.connectors.clear();

                new Edge(oldNode, data.get(addedNode.address));
            } else {
                return add(oldNode, addedNode);
            }
        }

        for (Notify notify : connects) {
            for (NodeImpl observer : notify.observers) observer.onConnect(notify.subject);
        }
        return true;
    }

    private void handleSplit(List<Map<String, Vertex>> subGraphs) {
        if (subGraphs.size() < 2) return;

        // Snapshot node sets before mutating the graph (the lists are used for notifications).
        List<List<NodeImpl>> nodes = new ArrayList<>();
        List<List<NodeImpl>> visibleNodes = new ArrayList<>();
        for (Map<String, Vertex> sub : subGraphs) {
            List<NodeImpl> all = new ArrayList<>();
            List<NodeImpl> visible = new ArrayList<>();
            for (Vertex vertex : sub.values()) {
                all.add(vertex.data);
                if (vertex.data.reachability == Visibility.Network) visible.add(vertex.data);
            }
            nodes.add(all);
            visibleNodes.add(visible);
        }

        data.clear();
        connectors.clear();
        globalBuffer = 0;
        globalBufferSize = 0;

        // This network keeps the first sub-graph; the rest each get a fresh network.
        data.putAll(subGraphs.get(0));
        for (Vertex vertex : data.values()) {
            if (vertex.data instanceof ConnectorNodeImpl c) addConnector(c);
        }
        for (int i = 1; i < subGraphs.size(); i++) {
            new NetworkImpl(subGraphs.get(i));
        }

        for (int indexA = 0; indexA < subGraphs.size(); indexA++) {
            List<NodeImpl> nodesA = nodes.get(indexA);
            List<NodeImpl> visibleNodesA = visibleNodes.get(indexA);
            for (int indexB = indexA + 1; indexB < subGraphs.size(); indexB++) {
                List<NodeImpl> nodesB = nodes.get(indexB);
                List<NodeImpl> visibleNodesB = visibleNodes.get(indexB);
                for (NodeImpl node : visibleNodesA) {
                    for (NodeImpl other : nodesB) other.onDisconnect(node);
                }
                for (NodeImpl node : visibleNodesB) {
                    for (NodeImpl other : nodesA) other.onDisconnect(node);
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // Graph structure
    // ----------------------------------------------------------------------- //

    static final class Vertex {
        final NodeImpl data;
        final List<Edge> edges = new ArrayList<>();

        Vertex(NodeImpl data) {
            this.data = data;
        }

        /** Detach this vertex from its neighbours and return the resulting connected components. */
        List<Map<String, Vertex>> remove() {
            for (Edge edge : new ArrayList<>(edges)) {
                edge.other(this).edges.remove(edge);
            }
            List<Vertex> others = new ArrayList<>();
            for (Edge edge : edges) others.add(edge.other(this));
            return searchGraphs(others);
        }
    }

    static final class Edge {
        final Vertex left;
        final Vertex right;

        Edge(Vertex left, Vertex right) {
            this.left = left;
            this.right = right;
            left.edges.add(this);
            right.edges.add(this);
        }

        Vertex other(Vertex side) {
            return side == left ? right : left;
        }

        boolean isBetween(Vertex a, Vertex b) {
            return (a == left && b == right) || (b == left && a == right);
        }

        List<Map<String, Vertex>> remove() {
            left.edges.remove(this);
            right.edges.remove(this);
            return searchGraphs(List.of(left, right));
        }
    }

    /** BFS the graph from each seed, returning one address→vertex map per distinct component. */
    private static List<Map<String, Vertex>> searchGraphs(List<Vertex> seeds) {
        Set<Vertex> seen = new HashSet<>();
        List<Map<String, Vertex>> result = new ArrayList<>();
        for (Vertex seed : seeds) {
            if (seen.contains(seed)) continue;
            Map<String, Vertex> addressed = new HashMap<>();
            Deque<Vertex> queue = new ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                Vertex node = queue.poll();
                seen.add(node);
                addressed.put(node.data.address, node);
                for (Edge edge : node.edges) {
                    Vertex other = edge.other(node);
                    if (!seen.contains(other) && !queue.contains(other)) queue.add(other);
                }
            }
            result.add(addressed);
        }
        return result;
    }
}
