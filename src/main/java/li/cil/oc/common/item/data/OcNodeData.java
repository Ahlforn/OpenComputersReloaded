package li.cil.oc.common.item.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Network-node data stored on component items (address, energy buffer,
 * component-network visibility).
 *
 * <p>Replaces the old NBT-tag pattern from {@code NodeData.scala}.
 * In MC 1.20.5+ item data lives in data components, not raw NBT.</p>
 *
 * <p>The outer {@link Optional}s allow a partial record — an item that has
 * only ever been placed in a slot, never connected, will have empty values.</p>
 */
public record OcNodeData(
    Optional<String>  address,
    Optional<Double>  buffer,
    Optional<Integer> visibility
) {
    public static final OcNodeData EMPTY = new OcNodeData(
        Optional.empty(), Optional.empty(), Optional.empty());

    // -----------------------------------------------------------------------
    // Codec (persistent — saved to item NBT / world data)
    // -----------------------------------------------------------------------

    public static final Codec<OcNodeData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.STRING.optionalFieldOf("address")    .forGetter(OcNodeData::address),
        Codec.DOUBLE.optionalFieldOf("buffer")     .forGetter(OcNodeData::buffer),
        Codec.INT   .optionalFieldOf("visibility") .forGetter(OcNodeData::visibility)
    ).apply(inst, OcNodeData::new));

    // -----------------------------------------------------------------------
    // StreamCodec (network — sent in item-stack sync packets)
    // -----------------------------------------------------------------------

    public static final StreamCodec<RegistryFriendlyByteBuf, OcNodeData> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), OcNodeData::address,
            ByteBufCodecs.optional(ByteBufCodecs.DOUBLE),       OcNodeData::buffer,
            ByteBufCodecs.optional(ByteBufCodecs.INT),          OcNodeData::visibility,
            OcNodeData::new
        );

    // -----------------------------------------------------------------------
    // Convenience copy-mutate helpers (data components are immutable)
    // -----------------------------------------------------------------------

    public OcNodeData withAddress(String addr) {
        return new OcNodeData(Optional.of(addr), buffer, visibility);
    }

    public OcNodeData withBuffer(double buf) {
        return new OcNodeData(address, Optional.of(buf), visibility);
    }

    public OcNodeData withVisibility(int vis) {
        return new OcNodeData(address, buffer, Optional.of(vis));
    }
}
