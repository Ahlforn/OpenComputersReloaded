package li.cil.oc.server.driver;

import li.cil.oc.OpenComputersMod;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class CompoundBlockEnvironment implements ManagedEnvironment {
    private static final String TYPE_HASH_TAG = "typeHash";

    public final String name;
    public final List<Object[]> environments; // each entry: [String driverClass, ManagedEnvironment env]

    private final Component node;
    private final List<ManagedEnvironment> updatingEnvironments;

    public CompoundBlockEnvironment(String name, List<Object[]> environments) {
        this.name = name;
        this.environments = new ArrayList<>(environments);

        // Max reachability across all environments that have a node.
        Visibility maxReach = Visibility.None;
        for (Object[] pair : environments) {
            ManagedEnvironment env = (ManagedEnvironment) pair[1];
            if (env.node() != null) {
                Visibility r = env.node().reachability();
                if (r.ordinal() > maxReach.ordinal()) maxReach = r;
            }
        }

        this.node = Network.newNode(this, maxReach).withComponent(name).create();

        // Collect updating environments.
        this.updatingEnvironments = new ArrayList<>();
        for (Object[] pair : environments) {
            ManagedEnvironment env = (ManagedEnvironment) pair[1];
            if (env.canUpdate()) updatingEnvironments.add(env);
        }

        // Force wrapped components to Neighbors visibility.
        for (Object[] pair : environments) {
            ManagedEnvironment env = (ManagedEnvironment) pair[1];
            if (env.node() instanceof Component comp) {
                comp.setVisibility(Visibility.Neighbors);
            }
        }
    }

    @Override public Component node() { return node; }

    @Override
    public boolean canUpdate() {
        return !updatingEnvironments.isEmpty();
    }

    @Override
    public void update() {
        for (ManagedEnvironment env : updatingEnvironments) env.update();
    }

    @Override public void onMessage(Message message) {}

    @Override
    public void onConnect(Node n) {
        if (n == this.node) {
            for (Object[] pair : environments) {
                ManagedEnvironment env = (ManagedEnvironment) pair[1];
                if (env.node() != null) n.connect(env.node());
            }
        }
    }

    @Override
    public void onDisconnect(Node n) {
        if (n == this.node) {
            for (Object[] pair : environments) {
                ManagedEnvironment env = (ManagedEnvironment) pair[1];
                if (env.node() != null) env.node().remove();
            }
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        if (nbt.contains(TYPE_HASH_TAG) && nbt.getLongOr(TYPE_HASH_TAG, 0L) != typeHash()) return;
        node.load(nbt);
        for (Object[] pair : environments) {
            String driverClass = (String) pair[0];
            ManagedEnvironment env = (ManagedEnvironment) pair[1];
            if (nbt.contains(driverClass)) {
                try {
                    env.load(nbt.getCompoundOrEmpty(driverClass));
                } catch (Throwable e) {
                    OpenComputersMod.LOGGER.warn("Block component '{}' (driver '{}') threw on load.",
                        env.getClass().getName(), driverClass, e);
                }
            }
        }
    }

    @Override
    public void save(CompoundTag nbt) {
        nbt.putLong(TYPE_HASH_TAG, typeHash());
        node.save(nbt);
        for (Object[] pair : environments) {
            String driverClass = (String) pair[0];
            ManagedEnvironment env = (ManagedEnvironment) pair[1];
            try {
                CompoundTag sub = new CompoundTag();
                env.save(sub);
                nbt.put(driverClass, sub);
            } catch (Throwable e) {
                OpenComputersMod.LOGGER.warn("Block component '{}' (driver '{}') threw on save.",
                    env.getClass().getName(), driverClass, e);
            }
        }
    }

    private long typeHash() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            List<String> names = new ArrayList<>();
            for (Object[] pair : environments) names.add(((ManagedEnvironment) pair[1]).getClass().getName());
            names.sort(null);
            for (String n : names) sha.update(n.getBytes(StandardCharsets.UTF_8));
            byte[] d = sha.digest();
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xff);
            return h;
        } catch (Exception e) { return 0; }
    }
}
