package li.cil.oc.server.network;

import li.cil.oc.api.network.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Registry of linked ("quantum") network cards, grouped by tunnel id. Faithful port of the Scala
 * {@code QuantumNetwork}. Nodes are held weakly so abandoned cards don't leak.
 */
public final class QuantumNetwork {

    /** Implemented by linked-card components so they can be addressed by tunnel and receive packets. */
    public interface QuantumNode {
        String tunnel();

        void receivePacket(Packet packet);
    }

    private static final Map<String, Set<QuantumNode>> tunnels = new HashMap<>();

    private QuantumNetwork() {
    }

    public static synchronized void add(QuantumNode card) {
        tunnels.computeIfAbsent(card.tunnel(), k -> Collections.newSetFromMap(new WeakHashMap<>())).add(card);
    }

    public static synchronized void remove(QuantumNode card) {
        Set<QuantumNode> set = tunnels.get(card.tunnel());
        if (set != null) set.remove(card);
    }

    public static synchronized Iterable<QuantumNode> getEndpoints(String tunnel) {
        Set<QuantumNode> set = tunnels.get(tunnel);
        return set == null ? List.of() : new ArrayList<>(set);
    }
}
