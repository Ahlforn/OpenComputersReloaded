package li.cil.oc.common.tileentity;

import li.cil.oc.Settings;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import li.cil.oc.util.Power;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Bridges NeoForge Forge Energy (RF) to the OC-internal energy buffer held
 * by a {@link CaseBlockEntity}'s machine {@link Connector} node.
 */
@SuppressWarnings({"deprecation", "removal"})
public class OcEnergyStorage implements IEnergyStorage, EnergyHandler {

    private final CaseBlockEntity be;

    public OcEnergyStorage(CaseBlockEntity be) {
        this.be = be;
    }

    private Connector connector() {
        var machine = be.machine();
        if (machine == null) return null;
        Node n = machine.node();
        return n instanceof Connector c ? c : null;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        Connector con = connector();
        if (con == null || !canReceive()) return 0;
        double ocAmount = Power.fromRF(maxReceive);
        double accepted = simulate ? Math.min(ocAmount, con.globalBufferSize() - con.globalBuffer())
                                   : con.changeBuffer(ocAmount);
        return (int) Power.toRF(accepted);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        Connector con = connector();
        return con != null ? (int) Math.min(Power.toRF(con.globalBuffer()), Integer.MAX_VALUE) : 0;
    }

    @Override
    public int getMaxEnergyStored() {
        Connector con = connector();
        Settings s = Settings.get();
        double buffer = con != null ? con.globalBufferSize()
                                    : (s != null ? s.bufferComputer : 0);
        return (int) Math.min(Power.toRF(buffer), Integer.MAX_VALUE);
    }

    @Override
    public boolean canExtract() { return false; }

    @Override
    public boolean canReceive() {
        Settings s = Settings.get();
        return s == null || !s.ignorePower;
    }

    // EnergyHandler implementation (NeoForge 26.1+ transfer API)
    @Override
    public long getAmountAsLong() { return getEnergyStored(); }

    @Override
    public long getCapacityAsLong() { return getMaxEnergyStored(); }

    @Override
    public int insert(int amount, TransactionContext context) {
        return receiveEnergy(amount, false);
    }

    @Override
    public int extract(int amount, TransactionContext context) {
        return extractEnergy(amount, false);
    }
}
