package li.cil.oc.common;

import li.cil.oc.common.tileentity.WaypointBlockEntity;
import li.cil.oc.common.PacketBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

/**
 * Java port of PacketHandler.scala.
 *
 * <p>Receives an {@link OcPacketPayload}, decompresses if needed, reads the
 * {@link PacketType} ordinal, and dispatches to per-type handler methods.
 * The handler is registered bidirectionally (see
 * {@link li.cil.oc.OpenComputersMod}) so both server-bound and client-bound
 * packets flow through the same class.</p>
 *
 * <p>All per-type dispatch methods are stubs that will be filled in as the
 * corresponding subsystems are ported (Phase 3b+).  Unrecognised packet types
 * are logged and discarded — they never crash the game.</p>
 */
public class PacketHandler {

    private static final Logger LOGGER = LogManager.getLogger("OpenComputers/PacketHandler");

    // -----------------------------------------------------------------------
    // Public entry point — called by NeoForge on both sides
    // -----------------------------------------------------------------------

    public void handle(OcPacketPayload payload, IPayloadContext context) {
        // Dispatch on the calling thread is fine; NeoForge enqueues for the
        // game thread automatically for MAIN_THREAD handlers.
        context.enqueueWork(() -> dispatch(payload, context));
    }

    // -----------------------------------------------------------------------
    // Dispatch
    // -----------------------------------------------------------------------

    private void dispatch(OcPacketPayload payload, IPayloadContext context) {
        try {
            byte[] raw = payload.data();
            InputStream base = new ByteArrayInputStream(raw);
            boolean compressed = base.read() != 0;
            InputStream stream = compressed ? new InflaterInputStream(base) : base;
            DataInputStream in = new DataInputStream(stream);
            int typeId = in.readUnsignedByte();
            PacketType[] types = PacketType.values();
            if (typeId < 0 || typeId >= types.length) {
                LOGGER.warn("Received packet with unknown type id {}; ignoring.", typeId);
                return;
            }
            PacketType type = types[typeId];
            dispatchType(type, in, context);
        } catch (Exception e) {
            LOGGER.warn("Received a badly formatted OC packet; discarding.", e);
        }
    }

    /**
     * Per-type dispatcher.  Stubs will be replaced by real implementations
     * as each subsystem is ported.
     */
    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private void dispatchType(PacketType type, DataInputStream in, IPayloadContext context) {
        switch (type) {
            case ComputerState -> handleComputerState(in, context);
            case WaypointLabel -> handleWaypointLabel(in, context);
            default -> LOGGER.debug("Unhandled packet type {} (not yet ported).", type);
        }
    }

    // -----------------------------------------------------------------------
    // Per-type handlers (stubs — filled in per-phase)
    // -----------------------------------------------------------------------

    private void handleComputerState(DataInputStream in, IPayloadContext context) {
        // Phase 3b: read dim/pos/isRunning/hasErrored from stream, update
        // CaseBlockEntity on client.  Stub until BE sync is wired up.
    }

    private void handleWaypointLabel(DataInputStream in, IPayloadContext context) {
        try {
            long posLong = in.readLong();
            String raw = in.readUTF();
            String label = raw.substring(0, Math.min(WaypointBlockEntity.MAX_LABEL_LENGTH, raw.length()));
            Player player = context.player();
            var level = player.level();
            BlockPos pos = BlockPos.of(posLong);
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof WaypointBlockEntity waypoint)) return;

            if (context.flow() == PacketFlow.SERVERBOUND) {
                // Server: validate distance, update, rebroadcast.
                if (player.blockPosition().distSqr(pos) > 64.0 * 64.0) return;
                waypoint.label = label;
                waypoint.setChanged();
                try {
                    PacketBuilder pb = PacketBuilder.simple(PacketType.WaypointLabel);
                    pb.writeLong(posLong);
                    pb.writeUTF(label);
                    pb.sendToPlayersNearPos(level, pos);
                } catch (IOException ex) {
                    LOGGER.warn("Failed to rebroadcast WaypointLabel", ex);
                }
            } else {
                // Client: apply the authoritative label from server.
                waypoint.label = label;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read WaypointLabel packet", e);
        }
    }
}
