package li.cil.oc.server.network;

/**
 * The energy-distribution view of a {@link NetworkImpl}, mirroring the Scala {@code Distributor}
 * trait. {@link ConnectorNodeImpl} holds a reference to its distributor and reconciles its local
 * buffer against the shared global buffer through this interface.
 *
 * <p>Getter/setter pairs use the Scala-style naming ({@code globalBuffer()} / {@code globalBuffer(v)})
 * rather than JavaBean {@code getX}/{@code setX} to stay close to the original source.
 */
interface Distributor {
    double globalBuffer();

    void globalBuffer(double value);

    double globalBufferSize();

    void globalBufferSize(double value);

    void addConnector(ConnectorNodeImpl connector);

    void removeConnector(ConnectorNodeImpl connector);

    double changeBuffer(double delta);
}
