package li.cil.oc.common;

import li.cil.oc.Settings;
import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Java port of PacketBuilder.scala.
 *
 * <p>Produces an {@link OcPacketPayload} from a byte stream written via a
 * standard {@link DataOutputStream}.  The first byte of the stream is a
 * compression flag (0 = uncompressed, 1 = zlib); the second byte is the
 * {@link PacketType} ordinal.</p>
 *
 * <p>The send helpers delegate to {@link PacketDistributor}.</p>
 */
public abstract class PacketBuilder extends DataOutputStream {

    protected PacketBuilder(OutputStream out) {
        super(out);
    }

    // -----------------------------------------------------------------------
    // Writing helpers
    // -----------------------------------------------------------------------

    public void writeBlockEntity(Level world, BlockPos pos) throws IOException {
        writeInt(world.dimension().identifier().hashCode()); // dimension identifier stub
        writeInt(pos.getX());
        writeInt(pos.getY());
        writeInt(pos.getZ());
    }

    /** Writes an optional Direction as a signed byte (−1 = absent). */
    public void writeDirection(net.minecraft.core.Direction d) throws IOException {
        writeByte(d == null ? -1 : d.ordinal());
    }

    public void writePacketType(PacketType pt) throws IOException {
        writeByte(pt.ordinal());
    }

    // -----------------------------------------------------------------------
    // Send helpers
    // -----------------------------------------------------------------------

    public void sendToAllPlayers() throws IOException {
        PacketDistributor.sendToAllPlayers(buildPayload());
    }

    public void sendToPlayer(ServerPlayer player) throws IOException {
        PacketDistributor.sendToPlayer(player, buildPayload());
    }

    public void sendToServer() throws IOException {
        ClientPacketDistributor.sendToServer(buildPayload());
    }

    public void sendToPlayersNear(Level level, double x, double y, double z, double range)
            throws IOException {
        if (!(level instanceof ServerLevel serverLevel)) return;
        double configRange = Settings.get().maxNetworkClientPacketDistance;
        double effectiveRange = configRange > 0 ? Math.min(range, configRange) : range;
        PacketDistributor.sendToPlayersNear(serverLevel, null, x, y, z, effectiveRange, buildPayload());
    }

    public void sendToPlayersNearHost(EnvironmentHost host) throws IOException {
        double range = (64 + 1) * 16.0; // default view-distance fallback
        sendToPlayersNear(host.world(), host.xPosition(), host.yPosition(), host.zPosition(), range);
    }

    public void sendToPlayersNearPos(Level level, BlockPos pos) throws IOException {
        sendToPlayersNear(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 512.0);
    }

    // -----------------------------------------------------------------------
    // Subclass contract
    // -----------------------------------------------------------------------

    protected abstract OcPacketPayload buildPayload() throws IOException;

    // -----------------------------------------------------------------------
    // Factory / convenience constructors
    // -----------------------------------------------------------------------

    public static SimplePacketBuilder simple(PacketType type) throws IOException {
        return new SimplePacketBuilder(type);
    }

    public static CompressedPacketBuilder compressed(PacketType type) throws IOException {
        return new CompressedPacketBuilder(type);
    }
}

// ---------------------------------------------------------------------------
// Concrete builders
// ---------------------------------------------------------------------------

class SimplePacketBuilder extends PacketBuilder {
    private final ByteArrayOutputStream backingStream;

    SimplePacketBuilder(PacketType type) throws IOException {
        super(null); // temporarily null — reassigned below
        this.backingStream = new ByteArrayOutputStream();
        this.out = new BufferedOutputStream(this.backingStream);
        writeByte(0); // uncompressed flag
        writeByte(type.ordinal());
    }

    @Override
    protected OcPacketPayload buildPayload() throws IOException {
        flush();
        return new OcPacketPayload(backingStream.toByteArray());
    }
}

class CompressedPacketBuilder extends PacketBuilder {
    private final ByteArrayOutputStream backingStream;
    private final DeflaterOutputStream deflater;

    CompressedPacketBuilder(PacketType type) throws IOException {
        super(null);
        this.backingStream = new ByteArrayOutputStream();
        this.backingStream.write(1); // compressed flag
        this.deflater = new DeflaterOutputStream(backingStream, new Deflater(Deflater.BEST_SPEED));
        this.out = new BufferedOutputStream(deflater);
        writeByte(type.ordinal());
    }

    @Override
    protected OcPacketPayload buildPayload() throws IOException {
        flush();
        deflater.finish();
        return new OcPacketPayload(backingStream.toByteArray());
    }
}
