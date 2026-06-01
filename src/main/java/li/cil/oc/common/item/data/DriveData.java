package li.cil.oc.common.item.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Drive metadata stored on floppy and HDD items.
 *
 * <p>Replaces the old {@code DriveData.scala} load/save-on-CompoundTag pattern.</p>
 *
 * <p>{@code lock} is the player name that locked this drive (empty → unlocked).
 * {@code unmanaged} means the filesystem is not managed by OC's virtual-FS
 * layer — it exposes the raw bytes directly.</p>
 */
public record DriveData(boolean unmanaged, String lock) {

    public static final DriveData DEFAULT = new DriveData(false, "");

    // -----------------------------------------------------------------------
    // Codec
    // -----------------------------------------------------------------------

    public static final Codec<DriveData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.BOOL  .optionalFieldOf("unmanaged", false).forGetter(DriveData::unmanaged),
        Codec.STRING.optionalFieldOf("lock",      ""   ).forGetter(DriveData::lock)
    ).apply(inst, DriveData::new));

    // -----------------------------------------------------------------------
    // StreamCodec
    // -----------------------------------------------------------------------

    public static final StreamCodec<RegistryFriendlyByteBuf, DriveData> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,         DriveData::unmanaged,
            ByteBufCodecs.STRING_UTF8,  DriveData::lock,
            DriveData::new
        );

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    public boolean isLocked() {
        return lock != null && !lock.isEmpty();
    }

    public DriveData withLock(String playerName) {
        return new DriveData(unmanaged, playerName == null ? "" : playerName);
    }

    public DriveData withUnmanaged(boolean value) {
        return new DriveData(value, isLocked() ? lock : "");
    }
}
