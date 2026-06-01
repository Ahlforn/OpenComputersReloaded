package li.cil.oc.common.item.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.nio.ByteBuffer;

/**
 * Data stored on EEPROM items: the program code, volatile scratch space,
 * a human-readable label, and a write-protection flag.
 *
 * <p>Replaces the raw NBT read/write in {@code server/component/EEPROM.scala}.
 * The EEPROM component still operates on this data at runtime (loaded from the
 * item when the machine starts, saved back when the machine stops).</p>
 */
public record EepromData(
    byte[] code,
    byte[] data,
    String label,
    boolean readonly
) {
    public static final EepromData DEFAULT = new EepromData(
        new byte[0], new byte[0], "EEPROM", false);

    // -----------------------------------------------------------------------
    // Helpers for byte[] ↔ ByteBuffer (Codec.BYTE_BUFFER is available; BYTE_ARRAY is not)
    // -----------------------------------------------------------------------

    private static final Codec<byte[]> BYTES_CODEC =
        Codec.BYTE_BUFFER.xmap(ByteBuffer::array, ByteBuffer::wrap);

    // -----------------------------------------------------------------------
    // Codec (persistent — saved to world data / item NBT)
    // -----------------------------------------------------------------------

    public static final Codec<EepromData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        BYTES_CODEC .optionalFieldOf("code",     new byte[0]).forGetter(EepromData::code),
        BYTES_CODEC .optionalFieldOf("data",     new byte[0]).forGetter(EepromData::data),
        Codec.STRING.optionalFieldOf("label",    "EEPROM"   ).forGetter(EepromData::label),
        Codec.BOOL  .optionalFieldOf("readonly", false      ).forGetter(EepromData::readonly)
    ).apply(inst, EepromData::new));

    // -----------------------------------------------------------------------
    // StreamCodec
    // -----------------------------------------------------------------------

    public static final StreamCodec<RegistryFriendlyByteBuf, EepromData> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY,   EepromData::code,
            ByteBufCodecs.BYTE_ARRAY,   EepromData::data,
            ByteBufCodecs.STRING_UTF8,  EepromData::label,
            ByteBufCodecs.BOOL,         EepromData::readonly,
            EepromData::new
        );

    // -----------------------------------------------------------------------
    // Copy-mutate helpers
    // -----------------------------------------------------------------------

    public EepromData withCode(byte[] newCode) {
        return new EepromData(newCode, data, label, readonly);
    }

    public EepromData withData(byte[] newData) {
        return new EepromData(code, newData, label, readonly);
    }

    public EepromData withLabel(String newLabel) {
        return new EepromData(code, data, newLabel == null ? "EEPROM" : newLabel, readonly);
    }

    public EepromData withReadonly(boolean value) {
        return new EepromData(code, data, label, value);
    }
}
