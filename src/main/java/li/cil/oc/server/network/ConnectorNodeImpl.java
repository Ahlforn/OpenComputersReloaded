package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;

/**
 * A node that can store and exchange energy. Faithful port of the Scala {@code Connector} trait.
 *
 * <p>Energy lives in a per-node {@link #localBuffer} clamped to {@link #localBufferSize}. While the
 * node is part of a network, a {@link Distributor} (the network) aggregates all connectors into a
 * shared global buffer; {@link #changeBuffer}/{@link #tryChangeBuffer} pull from / push to that
 * shared pool so a machine can draw from a remote capacitor on the same network.
 */
class ConnectorNodeImpl extends NodeImpl implements Connector {
    double localBufferSize;
    double localBuffer = 0.0;

    /** The network acting as our distributor; null while not part of a network. */
    Distributor distributor = null;

    ConnectorNodeImpl(Environment host, Visibility reachability, double bufferSize) {
        super(host, reachability);
        this.localBufferSize = bufferSize;
    }

    // ----------------------------------------------------------------------- //

    @Override
    public double localBuffer() {
        return localBuffer;
    }

    @Override
    public double localBufferSize() {
        return localBufferSize;
    }

    @Override
    public double globalBuffer() {
        return distributor == null ? localBuffer : distributor.globalBuffer();
    }

    @Override
    public double globalBufferSize() {
        return distributor == null ? localBufferSize : distributor.globalBufferSize();
    }

    // ----------------------------------------------------------------------- //

    @Override
    public double changeBuffer(double delta) {
        if (delta == 0) return 0;
        if (Settings.get().ignorePower) return delta < 0 ? 0 : delta;
        synchronized (this) {
            if (distributor != null) {
                synchronized (distributor) {
                    return distributor.changeBuffer(change(delta));
                }
            }
            return change(delta);
        }
    }

    /** Apply {@code delta} to the local buffer (clamped), reconcile the distributor, return remainder. */
    private double change(double delta) {
        if (localBufferSize <= 0) return delta;
        double oldBuffer = localBuffer;
        localBuffer += delta;
        double remaining;
        if (localBuffer < 0) {
            remaining = localBuffer;
            localBuffer = 0;
        } else if (localBuffer > localBufferSize) {
            remaining = localBuffer - localBufferSize;
            localBuffer = localBufferSize;
        } else {
            remaining = 0;
        }
        if (localBuffer != oldBuffer && distributor != null) {
            distributor.globalBuffer(Math.max(0, Math.min(distributor.globalBufferSize(),
                    distributor.globalBuffer() - oldBuffer + localBuffer)));
        }
        return remaining;
    }

    @Override
    public boolean tryChangeBuffer(double delta) {
        if (delta == 0) return true;
        if (Settings.get().ignorePower) return delta < 0;
        synchronized (this) {
            if (distributor != null) {
                synchronized (distributor) {
                    if (localBuffer > localBufferSize) {
                        distributor.changeBuffer(localBuffer - localBufferSize);
                        localBuffer = localBufferSize;
                    }
                    double newGlobalBuffer = globalBuffer() + delta;
                    return (delta > 0 || newGlobalBuffer >= 0)
                            && (delta < 0 || newGlobalBuffer <= globalBufferSize())
                            && distributor.changeBuffer(delta) == 0;
                }
            }
            double newLocalBuffer = localBuffer + delta;
            if ((delta < 0 && newLocalBuffer < 0) || (delta > 0 && newLocalBuffer > localBufferSize)) {
                return false;
            }
            localBuffer = newLocalBuffer;
            return true;
        }
    }

    @Override
    public void setLocalBufferSize(double size) {
        double clampedSize = Math.max(size, 0);
        synchronized (this) {
            if (distributor != null) {
                synchronized (distributor) {
                    double oldSize = localBufferSize;
                    // Must apply the new size before (de)registering with the distributor, else we
                    // get ignored when our size is zero.
                    localBufferSize = clampedSize;
                    if (network != null) {
                        if (oldSize <= 0 && clampedSize > 0) distributor.addConnector(this);
                        else if (oldSize > 0 && clampedSize == 0) distributor.removeConnector(this);
                        else distributor.globalBufferSize(Math.max(distributor.globalBufferSize() - oldSize + clampedSize, 0));
                    }
                    double surplus = Math.max(localBuffer - clampedSize, 0);
                    changeBuffer(-surplus);
                    distributor.changeBuffer(surplus);
                }
            } else {
                localBufferSize = clampedSize;
                localBuffer = Math.min(localBuffer, localBufferSize);
            }
        }
    }

    // ----------------------------------------------------------------------- //

    @Override
    void onDisconnect(Node node) {
        super.onDisconnect(node);
        if (node == this) {
            synchronized (this) {
                distributor = null;
            }
        }
    }

    // ----------------------------------------------------------------------- //

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        localBuffer = nbt.getDouble("buffer").orElse(0.0);
    }

    @Override
    public void save(CompoundTag nbt) {
        super.save(nbt);
        nbt.putDouble("buffer", Math.min(localBuffer, localBufferSize));
    }
}
