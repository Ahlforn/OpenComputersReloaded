package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.detail.Builder;
import li.cil.oc.api.detail.NetworkAPI;
import li.cil.oc.api.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Java implementation of the OC network API.
 * Wired into {@link li.cil.oc.api.API#network} during common setup.
 *
 * <p>Replaces the Scala {@code Network} object from the 1.12.2 port.</p>
 */
public final class NetworkAPIImpl implements NetworkAPI {

    // -----------------------------------------------------------------------
    // joinOrCreateNetwork
    // -----------------------------------------------------------------------

    @Override
    public void joinOrCreateNetwork(BlockEntity be) {
        if (be == null) return;
        net.minecraft.world.level.Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        if (level != null && pos != null) {
            joinOrCreateNetwork(level, pos);
        }
    }

    @Override
    public void joinOrCreateNetwork(LevelReader level, BlockPos pos) {
        if (!(level instanceof net.minecraft.world.level.Level worldLevel) || worldLevel.isClientSide()) return;
        BlockEntity tileEntity = worldLevel.getBlockEntity(pos);
        if (tileEntity == null || tileEntity.isRemoved() || tileEntity.getLevel() == null) return;

        // The local node is resolved per side so that SidedEnvironment blocks (e.g. cables, screens)
        // expose the correct node toward each neighbour. Mirrors Network.scala joinOrCreateNetwork.
        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            if (!worldLevel.isLoaded(neighborPos)) continue;

            Node localNode = getNodeFor(worldLevel, pos, side);
            if (localNode == null) continue;

            Node neighborNode = getNodeFor(worldLevel, neighborPos, side.getOpposite());
            if (neighborNode != null && neighborNode != localNode && neighborNode.network() != null) {
                if (canConnectBasedOnColor(worldLevel, pos, neighborPos)) {
                    neighborNode.connect(localNode);
                } else {
                    localNode.disconnect(neighborNode);
                }
            }

            if (localNode.network() == null) {
                joinNewNetwork(localNode);
            }
        }
    }

    @Override
    public void joinNewNetwork(Node node) {
        if (node == null || node.network() != null) return;
        NodeImpl n = (NodeImpl) node;
        // Assign address if needed, then create a single-node network.
        if (n.address == null) {
            n.address = NodeImpl.randomAddress();
        }
        new NetworkImpl(n);
    }

    // -----------------------------------------------------------------------
    // Wireless
    // -----------------------------------------------------------------------

    @Override
    public void joinWirelessNetwork(WirelessEndpoint endpoint) {
        WirelessNetwork.add(endpoint);
    }

    @Override
    public void updateWirelessNetwork(WirelessEndpoint endpoint) {
        WirelessNetwork.update(endpoint);
    }

    @Override
    public void leaveWirelessNetwork(WirelessEndpoint endpoint) {
        WirelessNetwork.remove(endpoint);
    }

    @Override
    public void leaveWirelessNetwork(WirelessEndpoint endpoint, int dimension) {
        WirelessNetwork.remove(endpoint, dimension);
    }

    @Override
    public void sendWirelessPacket(WirelessEndpoint source, double strength, Packet packet) {
        for (WirelessEndpoint endpoint : WirelessNetwork.computeReachableFrom(source, strength)) {
            endpoint.receivePacket(packet, source);
        }
    }

    // -----------------------------------------------------------------------
    // newNode — builder chain
    // -----------------------------------------------------------------------

    @Override
    public Builder.NodeBuilder newNode(Environment host, Visibility reachability) {
        return new NodeBuilderImpl(host, reachability);
    }

    // -----------------------------------------------------------------------
    // newPacket
    // -----------------------------------------------------------------------

    @Override
    public Packet newPacket(String source, String destination, int port, Object[] data) {
        PacketImpl packet = new PacketImpl(source, destination, port, data);
        Settings s = Settings.get();
        if (s != null && packet.size() > s.maxNetworkPacketSize) {
            throw new IllegalArgumentException("packet too big (max " + s.maxNetworkPacketSize + ")");
        }
        return packet;
    }

    @Override
    public Packet newPacket(CompoundTag nbt) {
        String source      = nbt.getStringOr("source", "");
        String destination = nbt.contains("dest") ? nbt.getStringOr("dest", null) : null;
        int port = nbt.getIntOr("port", 0);
        int ttl  = nbt.getIntOr("ttl", 5);
        int len  = nbt.getIntOr("dataLength", 0);
        Object[] data = new Object[len];
        for (int i = 0; i < len; i++) {
            String key = "data" + i;
            if (!nbt.contains(key)) continue;
            net.minecraft.nbt.Tag tag = nbt.get(key);
            if (tag == null) continue;
            data[i] = switch (tag.getId()) {
                case net.minecraft.nbt.Tag.TAG_BYTE ->
                    ((net.minecraft.nbt.NumericTag) tag).byteValue() == 1;
                case net.minecraft.nbt.Tag.TAG_SHORT ->
                    ((net.minecraft.nbt.NumericTag) tag).shortValue();
                case net.minecraft.nbt.Tag.TAG_INT ->
                    ((net.minecraft.nbt.NumericTag) tag).intValue();
                case net.minecraft.nbt.Tag.TAG_LONG ->
                    ((net.minecraft.nbt.NumericTag) tag).longValue();
                case net.minecraft.nbt.Tag.TAG_FLOAT ->
                    ((net.minecraft.nbt.NumericTag) tag).floatValue();
                case net.minecraft.nbt.Tag.TAG_DOUBLE ->
                    ((net.minecraft.nbt.NumericTag) tag).doubleValue();
                case net.minecraft.nbt.Tag.TAG_STRING ->
                    tag.asString().orElse("");
                case net.minecraft.nbt.Tag.TAG_BYTE_ARRAY ->
                    ((net.minecraft.nbt.ByteArrayTag) tag).getAsByteArray();
                default -> null;
            };
        }
        return new PacketImpl(source, destination, port, data, ttl);
    }

    // -----------------------------------------------------------------------
    // Node resolution via capabilities
    // -----------------------------------------------------------------------

    private static @Nullable Node getNodeFor(LevelReader level, BlockPos pos, @Nullable Direction side) {
        // LevelReader doesn't expose getCapability — need a ServerLevel.
        // Level extends LevelReader, so try a cast.
        if (!(level instanceof net.minecraft.world.level.Level worldLevel)) return null;

        // Try SIDED_ENVIRONMENT first (OC SidedEnvironment blocks).
        li.cil.oc.api.network.SidedEnvironment sided = worldLevel.getCapability(
            li.cil.oc.common.capabilities.OcCapabilities.SIDED_ENVIRONMENT, pos, side);
        if (sided != null) {
            return sided.sidedNode(side);
        }

        // Fall back to plain ENVIRONMENT (e.g. Case).
        Environment env = worldLevel.getCapability(
            li.cil.oc.common.capabilities.OcCapabilities.ENVIRONMENT, pos, side);
        if (env != null) {
            return env.node();
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Connection color (cables/components only connect if colors are compatible)
    // -----------------------------------------------------------------------

    /** Default "uncolored" connection color (dye light gray / silver), matching Color.scala. */
    private static final int SILVER = 0xABABAB;

    private static int getConnectionColor(net.minecraft.world.level.Level level, BlockPos pos) {
        li.cil.oc.api.internal.Colored colored = level.getCapability(
            li.cil.oc.common.capabilities.OcCapabilities.COLORED, pos, null);
        if (colored != null && colored.controlsConnectivity()) return colored.getColor();
        return SILVER;
    }

    private static boolean canConnectBasedOnColor(net.minecraft.world.level.Level level, BlockPos a, BlockPos b) {
        int c1 = getConnectionColor(level, a);
        int c2 = getConnectionColor(level, b);
        return c1 == c2 || c1 == SILVER || c2 == SILVER;
    }

    // -----------------------------------------------------------------------
    // Builder implementations
    // -----------------------------------------------------------------------

    private record NodeBuilderImpl(Environment host, Visibility reachability)
            implements Builder.NodeBuilder {

        @Override
        public Builder.ComponentBuilder withComponent(String name, Visibility visibility) {
            return new ComponentBuilderImpl(host, reachability, name, visibility);
        }

        @Override
        public Builder.ComponentBuilder withComponent(String name) {
            return withComponent(name, reachability);
        }

        @Override
        public Builder.ConnectorBuilder withConnector(double bufferSize) {
            return new ConnectorBuilderImpl(host, reachability, bufferSize);
        }

        @Override
        public Builder.ConnectorBuilder withConnector() {
            return withConnector(0);
        }

        @Override
        public Node create() {
            return new NodeImpl(host, reachability);
        }
    }

    private record ComponentBuilderImpl(Environment host, Visibility reachability, String name, Visibility visibility)
            implements Builder.ComponentBuilder {

        @Override
        public Builder.ComponentConnectorBuilder withConnector(double bufferSize) {
            return new ComponentConnectorBuilderImpl(host, reachability, name, visibility, bufferSize);
        }

        @Override
        public Builder.ComponentConnectorBuilder withConnector() {
            return withConnector(0);
        }

        @Override
        public Component create() {
            return new ComponentNodeImpl(host, reachability, name, visibility);
        }
    }

    private record ConnectorBuilderImpl(Environment host, Visibility reachability, double bufferSize)
            implements Builder.ConnectorBuilder {

        @Override
        public Builder.ComponentConnectorBuilder withComponent(String name, Visibility visibility) {
            return new ComponentConnectorBuilderImpl(host, reachability, name, visibility, bufferSize);
        }

        @Override
        public Builder.ComponentConnectorBuilder withComponent(String name) {
            return withComponent(name, reachability);
        }

        @Override
        public Connector create() {
            return new ConnectorNodeImpl(host, reachability, bufferSize);
        }
    }

    private record ComponentConnectorBuilderImpl(
            Environment host, Visibility reachability,
            String name, Visibility visibility, double bufferSize)
            implements Builder.ComponentConnectorBuilder {

        @Override
        public ComponentConnector create() {
            return new ComponentConnectorNodeImpl(host, reachability, name, visibility, bufferSize);
        }
    }
}
