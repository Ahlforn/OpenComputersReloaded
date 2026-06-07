package li.cil.oc.server.network;

import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;

class MessageImpl implements Message {
    private final Node source;
    private final String name;
    private final Object[] data;
    private boolean canceled = false;

    MessageImpl(Node source, String name, Object[] data) {
        this.source = source;
        this.name = name;
        this.data = data != null ? data : new Object[0];
    }

    @Override public Node source() { return source; }
    @Override public String name() { return name; }
    @Override public Object[] data() { return data; }
    @Override public void cancel() { canceled = true; }

    boolean isCanceled() { return canceled; }
}
