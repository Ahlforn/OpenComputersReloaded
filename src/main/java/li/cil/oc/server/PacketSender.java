package li.cil.oc.server;

import li.cil.oc.common.PacketBuilder;
import li.cil.oc.common.PacketType;
import li.cil.oc.common.tileentity.CaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Java port of server/PacketSender.scala.
 *
 * <p>All methods are static.  Each method builds an {@link li.cil.oc.common.OcPacketPayload}
 * via a {@link PacketBuilder} and sends it to the appropriate audience.</p>
 *
 * <p>Most methods are stubs — they will be filled in as corresponding
 * block entities and subsystems are ported (Phase 3b+).</p>
 */
public final class PacketSender {

    private static final Logger LOGGER = LogManager.getLogger("OpenComputers/PacketSender");

    private PacketSender() {}

    // -----------------------------------------------------------------------
    // Computer / Case
    // -----------------------------------------------------------------------

    /**
     * Notifies nearby clients of the running/errored state of a computer case.
     * Called from {@link CaseBlockEntity#serverTick} whenever the state changes.
     */
    public static void sendComputerState(CaseBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = be.getBlockPos();
        try {
            PacketBuilder pb = PacketBuilder.simple(PacketType.ComputerState);
            pb.writeInt(serverLevel.dimension().identifier().hashCode());
            pb.writeInt(pos.getX());
            pb.writeInt(pos.getY());
            pb.writeInt(pos.getZ());
            pb.writeBoolean(be.isRunning);
            pb.writeBoolean(be.hasErrored);
            pb.sendToPlayersNearPos(serverLevel, pos);
        } catch (Exception e) {
            LOGGER.warn("Failed to send ComputerState packet.", e);
        }
    }

    // TODO Phase 3b+: port the remaining 836-line PacketSender.scala methods.
}
