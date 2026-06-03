package li.cil.oc.common;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Single {@link CustomPacketPayload} type wrapping the raw byte buffer produced
 * by OC's existing {@link PacketBuilder}.
 *
 * <p>The buffer layout is identical to the old Forge channel format:
 * <pre>
 *   byte[0] = 0 (uncompressed) | 1 (zlib compressed)
 *   byte[1] = {@link PacketType} ordinal
 *   byte[2..] = packet body
 * </pre>
 * This lets {@link PacketHandler} continue to work unchanged — only the
 * channel transport changes from {@code FMLProxyPacket} to this record.</p>
 */
public record OcPacketPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<OcPacketPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("opencomputers", "packet"));

    // -----------------------------------------------------------------------
    // StreamCodec: length-prefixed byte array over RegistryFriendlyByteBuf
    // -----------------------------------------------------------------------

    public static final StreamCodec<RegistryFriendlyByteBuf, OcPacketPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.data().length);
                buf.writeBytes(payload.data());
            },
            buf -> {
                int len = buf.readVarInt();
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                return new OcPacketPayload(bytes);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
