package li.cil.oc.server.network;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.server.driver.Registry;
import li.cil.oc.server.machine.ArgumentsImpl;
import li.cil.oc.server.machine.Callbacks;
import li.cil.oc.server.machine.Machine;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared {@code @Callback} discovery/dispatch and visibility wiring for the two component node types
 * ({@link ComponentNodeImpl}, {@link ComponentConnectorNodeImpl}), which cannot share a base class
 * because of Java single inheritance.
 *
 * <p>Faithful to the Scala {@code Component} trait: callbacks are built via {@link Callbacks}, arguments
 * wrapped in {@link ArgumentsImpl}, and results passed through {@link Registry#convert}. Visibility
 * changes (de)register the component with in-range {@link Machine}s via the {@code addTo}/{@code removeFrom}
 * transition matrix ({@code Component.scala:54-97}).
 */
final class ComponentSupport {

    private final Environment host;
    private Map<String, Callbacks.Callback> callbacks;

    ComponentSupport(Environment host) {
        this.host = host;
    }

    private Map<String, Callbacks.Callback> callbacks() {
        if (callbacks == null) {
            callbacks = Callbacks.apply(host);
        }
        return callbacks;
    }

    Collection<String> methods() {
        return Collections.unmodifiableSet(callbacks().keySet());
    }

    Callback annotation(String method) {
        Callbacks.Callback callback = callbacks().get(method);
        return callback != null ? callback.annotation() : null;
    }

    Object[] invoke(String method, Context context, Object... arguments) throws Exception {
        Callbacks.Callback callback = callbacks().get(method);
        if (callback == null) throw new NoSuchMethodException();
        return Registry.convert(callback.apply(host, context, new ArgumentsImpl(arguments)));
    }

    // ----------------------------------------------------------------------- //
    // Visibility -> machine (de)registration. Faithful to Component.scala:54-97.
    // ----------------------------------------------------------------------- //

    /** Apply the visibility transition, (de)registering {@code node} with in-range machines. */
    static <T extends NodeImpl & Component> void changeVisibility(T node, Visibility current, Visibility target) {
        if (node.network() == null) return;
        switch (current) {
            case Neighbors -> {
                switch (target) {
                    case Network -> addTo(node, node.reachableNodes());
                    case None -> removeFrom(node, node.neighbors());
                    default -> { }
                }
            }
            case Network -> {
                switch (target) {
                    case Neighbors -> {
                        Set<Node> neighborSet = Collections.newSetFromMap(new IdentityHashMap<>());
                        for (Node n : node.neighbors()) neighborSet.add(n);
                        for (Node n : node.reachableNodes()) {
                            if (!neighborSet.contains(n)) removeFrom1(node, n);
                        }
                    }
                    case None -> removeFrom(node, node.reachableNodes());
                    default -> { }
                }
            }
            case None -> {
                switch (target) {
                    case Neighbors -> addTo(node, node.neighbors());
                    case Network -> addTo(node, node.reachableNodes());
                    default -> { }
                }
            }
        }
    }

    private static void addTo(Component self, Iterable<Node> nodes) {
        for (Node n : nodes) {
            if (n.host() instanceof Machine machine) machine.addComponent(self);
        }
    }

    private static void removeFrom(Component self, Iterable<Node> nodes) {
        for (Node n : nodes) removeFrom1(self, n);
    }

    private static void removeFrom1(Component self, Node n) {
        if (n.host() instanceof Machine machine) machine.removeComponent(self);
    }
}
