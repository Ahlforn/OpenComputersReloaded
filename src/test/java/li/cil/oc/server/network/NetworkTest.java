package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for the faithful network port: graph connect/merge, the documented
 * onConnect/onDisconnect notification contract, multi-way split notifications, address remap, and
 * cross-connector energy pooling.
 */
class NetworkTest {

    private static void requireSettings() {
        if (Settings.get() != null) return;
        Settings.load(new File("build/tmp/network-test-settings.conf"));
    }

    /** Recording host: remembers which other hosts it was told connected/disconnected. */
    static final class Rec implements Environment {
        final String tag;
        NodeImpl node;
        final List<String> connects = new ArrayList<>();
        final List<String> disconnects = new ArrayList<>();

        Rec(String tag) {
            this.tag = tag;
        }

        @Override
        public Node node() {
            return node;
        }

        @Override
        public void onConnect(Node other) {
            connects.add(tagOf(other));
        }

        @Override
        public void onDisconnect(Node other) {
            disconnects.add(tagOf(other));
        }

        @Override
        public void onMessage(Message message) {
        }

        private static String tagOf(Node n) {
            return n == null ? "null" : ((Rec) ((NodeImpl) n).host).tag;
        }
    }

    private static NodeImpl node(String tag) {
        Rec rec = new Rec(tag);
        NodeImpl node = new NodeImpl(rec, Visibility.Network);
        rec.node = node;
        return node;
    }

    private static ConnectorNodeImpl connector(String tag, double bufferSize) {
        Rec rec = new Rec(tag);
        ConnectorNodeImpl node = new ConnectorNodeImpl(rec, Visibility.Network, bufferSize);
        rec.node = node;
        return node;
    }

    private static Rec rec(NodeImpl node) {
        return (Rec) node.host;
    }

    // ----------------------------------------------------------------------- //

    @Test
    void mergeJoinsNetworksAndNotifiesBothSides() {
        NodeImpl a = node("A");
        NodeImpl b = node("B");
        new NetworkImpl(a);
        new NetworkImpl(b);

        a.connect(b);

        assertNotNull(a.network());
        assertSame(a.network(), b.network());
        assertTrue(rec(a).connects.contains("B"), "A should be told B connected");
        assertTrue(rec(b).connects.contains("A"), "B should be told A connected");
    }

    @Test
    void onConnectContractAcrossThreeNodes() {
        NodeImpl a = node("A");
        NodeImpl b = node("B");
        NodeImpl c = node("C");
        new NetworkImpl(a);
        new NetworkImpl(b);
        new NetworkImpl(c);

        a.connect(b);
        a.connect(c);

        assertSame(a.network(), b.network());
        assertSame(b.network(), c.network());
        // Every node must have learned about every other node (documented contract).
        assertTrue(rec(a).connects.contains("B") && rec(a).connects.contains("C"));
        assertTrue(rec(b).connects.contains("A") && rec(b).connects.contains("C"));
        assertTrue(rec(c).connects.contains("A") && rec(c).connects.contains("B"));
    }

    @Test
    void disconnectingAnEdgeSplitsAndNotifiesBothComponents() {
        // Line: A - B - C - D, all in one network.
        NodeImpl a = node("A");
        NodeImpl b = node("B");
        NodeImpl c = node("C");
        NodeImpl d = node("D");
        new NetworkImpl(a);
        a.connect(b);
        b.connect(c);
        c.connect(d);
        assertSame(a.network(), d.network());

        // Cut the middle edge: {A,B} | {C,D}.
        assertTrue(a.network().disconnect(b, c));

        assertSame(a.network(), b.network());
        assertSame(c.network(), d.network());
        assertFalse(a.network() == c.network());

        // Cross-component disconnect notifications must reach every node.
        assertTrue(rec(a).disconnects.contains("C") && rec(a).disconnects.contains("D"));
        assertTrue(rec(d).disconnects.contains("A") && rec(d).disconnects.contains("B"));
    }

    @Test
    void removingAHubProducesThreeWaySplitNotifications() {
        // Star: A connected to B, C, D. Removing A leaves three isolated components.
        NodeImpl a = node("A");
        NodeImpl b = node("B");
        NodeImpl c = node("C");
        NodeImpl d = node("D");
        new NetworkImpl(a);
        a.connect(b);
        a.connect(c);
        a.connect(d);

        a.remove();

        // Each leaf learned the hub disconnected...
        assertTrue(rec(b).disconnects.contains("A"));
        // ...and, crucially, learned about the *other* leaves leaving (the 3-way case the rewrite missed).
        assertTrue(rec(b).disconnects.contains("C") && rec(b).disconnects.contains("D"));
        assertTrue(rec(c).disconnects.contains("B") && rec(c).disconnects.contains("D"));
    }

    @Test
    void remapKeepsNodeConnectedUnderNewAddress() {
        NodeImpl a = node("A");
        NodeImpl b = node("B");
        new NetworkImpl(a);
        a.connect(b);

        NetworkImpl network = (NetworkImpl) a.network();
        network.remap(a, "fixed-address");

        assertEquals("fixed-address", a.address());
        assertNotNull(a.network());
        assertSame(a.network(), b.network());
        assertNotNull(a.network().node("fixed-address"));
    }

    @Test
    void energyPoolsAcrossConnectors() {
        requireSettings();
        // Capacitor with a 1000 buffer, machine with no local buffer of its own.
        ConnectorNodeImpl capacitor = connector("CAP", 1000);
        ConnectorNodeImpl machine = connector("CPU", 0);
        new NetworkImpl(capacitor);
        capacitor.connect(machine);

        assertEquals(0, capacitor.changeBuffer(1000), 1e-9); // fill it up, no overflow
        assertEquals(1000, capacitor.globalBuffer(), 1e-9);
        assertEquals(1000, machine.globalBuffer(), 1e-9);    // machine sees the shared pool

        assertTrue(machine.tryChangeBuffer(-10));            // machine draws from the capacitor
        assertEquals(990, capacitor.localBuffer, 1e-9);
        assertEquals(990, machine.globalBuffer(), 1e-9);

        assertFalse(machine.tryChangeBuffer(-100000));       // can't overdraw the pool
    }
}
