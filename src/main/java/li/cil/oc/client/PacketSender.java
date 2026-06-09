package li.cil.oc.client;

import li.cil.oc.common.PacketBuilder;
import li.cil.oc.common.PacketType;
import li.cil.oc.common.tileentity.WaypointBlockEntity;

import java.io.IOException;

public final class PacketSender {
    private PacketSender() {}

    public static void sendWaypointLabel(WaypointBlockEntity waypoint) {
        try {
            PacketBuilder pb = PacketBuilder.simple(PacketType.WaypointLabel);
            pb.writeLong(waypoint.getBlockPos().asLong());
            pb.writeUTF(waypoint.label);
            pb.sendToServer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
