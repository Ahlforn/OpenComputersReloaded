package li.cil.oc.server.network;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for the faithful component-dispatch port: {@code @Callback} enumeration, the
 * {@link Callback} annotation passthrough, argument/return conversion via {@code ArgumentsImpl} +
 * {@code Registry.convert}, and the {@code setVisibility} transition matrix.
 *
 * <p>Note: the machine (de)registration effect of {@code setVisibility} cannot be asserted here — it
 * fires only for in-range hosts that are a concrete {@code server.machine.Machine}, which needs the
 * mod-initialized {@code API.network} + a world to construct. That path is covered by the runClient
 * smoke test (mirrors the deferred energy assertion in {@code NetworkTest}). These tests verify the
 * transition matrix runs cleanly over non-machine hosts and ends in the right visibility state.
 */
class ComponentDispatchTest {

    /** Minimal environment exposing a single {@code @Callback}. */
    public static final class EchoHost implements Environment {
        Node node;

        @Override
        public Node node() {
            return node;
        }

        @Override
        public void onConnect(Node other) {
        }

        @Override
        public void onDisconnect(Node other) {
        }

        @Override
        public void onMessage(Message message) {
        }

        @Callback(direct = true, limit = 42, doc = "echo(s:string):string,number")
        public Object[] echo(Context context, Arguments args) {
            return new Object[]{args.checkString(0), args.count()};
        }
    }

    /** Plain peer environment so a component has neighbours/reachable nodes to iterate. */
    private static final class Peer implements Environment {
        Node node;

        @Override public Node node() { return node; }
        @Override public void onConnect(Node other) { }
        @Override public void onDisconnect(Node other) { }
        @Override public void onMessage(Message message) { }
    }

    private static NodeImpl peerNode() {
        Peer peer = new Peer();
        NodeImpl node = new NodeImpl(peer, Visibility.Network);
        peer.node = node;
        return node;
    }

    private static ComponentNodeImpl component(EchoHost host, Visibility reachability, Visibility visibility) {
        ComponentNodeImpl node = new ComponentNodeImpl(host, reachability, "echoer", visibility);
        host.node = node;
        return node;
    }

    // ----------------------------------------------------------------------- //

    @Test
    void enumeratesAnnotatedCallbacks() {
        EchoHost host = new EchoHost();
        ComponentNodeImpl comp = component(host, Visibility.Network, Visibility.Network);

        assertTrue(comp.methods().contains("echo"));
        Callback annotation = comp.annotation("echo");
        assertNotNull(annotation);
        assertTrue(annotation.direct());
        assertEquals(42, annotation.limit());
        assertEquals("echo(s:string):string,number", annotation.doc());
        assertEquals(null, comp.annotation("missing"));
    }

    @Test
    void invokesCallbackAndConvertsResult() throws Exception {
        EchoHost host = new EchoHost();
        ComponentNodeImpl comp = component(host, Visibility.Network, Visibility.Network);

        Object[] result = comp.invoke("echo", null, "hi", 2);
        // checkString(0) -> "hi"; count() -> 2 (Integer passes through Registry.convert unchanged).
        assertArrayEquals(new Object[]{"hi", 2}, result);
    }

    @Test
    void unknownMethodThrows() {
        EchoHost host = new EchoHost();
        ComponentNodeImpl comp = component(host, Visibility.Network, Visibility.Network);

        assertThrows(NoSuchMethodException.class, () -> comp.invoke("nope", null));
    }

    @Test
    void badArgumentThrows() {
        EchoHost host = new EchoHost();
        ComponentNodeImpl comp = component(host, Visibility.Network, Visibility.Network);

        // No arguments -> checkString(0) reports a missing-value error.
        assertThrows(IllegalArgumentException.class, () -> comp.invoke("echo", null));
    }

    @Test
    void visibilityTransitionsAreSafeAndUpdateState() {
        EchoHost host = new EchoHost();
        ComponentNodeImpl comp = component(host, Visibility.Network, Visibility.None);
        new NetworkImpl(comp);
        comp.connect(peerNode()); // gives the component reachable/neighbour nodes to iterate

        comp.setVisibility(Visibility.Network);
        assertEquals(Visibility.Network, comp.visibility());
        comp.setVisibility(Visibility.Neighbors);
        assertEquals(Visibility.Neighbors, comp.visibility());
        comp.setVisibility(Visibility.None);
        assertEquals(Visibility.None, comp.visibility());
    }

    @Test
    void visibilityAboveReachabilityIsRejected() {
        EchoHost host = new EchoHost();
        ComponentNodeImpl comp = component(host, Visibility.Neighbors, Visibility.None);

        assertThrows(IllegalArgumentException.class, () -> comp.setVisibility(Visibility.Network));
        assertEquals(Visibility.None, comp.visibility()); // unchanged after rejection
    }

    @Test
    void canBeSeenReflectsVisibility() {
        EchoHost host = new EchoHost();
        ComponentNodeImpl comp = component(host, Visibility.Network, Visibility.None);
        NodeImpl peer = peerNode();
        new NetworkImpl(comp);
        comp.connect(peer);

        assertFalse(comp.canBeSeenFrom(peer)); // None -> not visible
        comp.setVisibility(Visibility.Network);
        assertTrue(comp.canBeSeenFrom(peer));  // Network -> reachable peer can see it
    }
}
