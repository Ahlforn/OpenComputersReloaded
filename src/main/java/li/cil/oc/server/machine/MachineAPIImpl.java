package li.cil.oc.server.machine;

import li.cil.oc.api.detail.MachineAPI;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.MachineHost;

import java.util.Collection;

/** Bridges the static registry on Machine to the MachineAPI interface wired into API.machine. */
public class MachineAPIImpl implements MachineAPI {

    @Override
    public void add(Class<? extends Architecture> architecture) {
        Machine.add(architecture);
    }

    @Override
    public Collection<Class<? extends Architecture>> architectures() {
        return Machine.architectures();
    }

    @Override
    public String getArchitectureName(Class<? extends Architecture> architecture) {
        return Machine.getArchitectureName(architecture);
    }

    @Override
    public li.cil.oc.api.machine.Machine create(MachineHost host) {
        return new Machine(host);
    }
}
