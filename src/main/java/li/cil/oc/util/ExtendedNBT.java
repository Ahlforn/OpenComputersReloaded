package li.cil.oc.util;

import java.nio.charset.StandardCharsets;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;

/**
 * Java port of ExtendedNBT.scala.
 *
 * <p>All helpers are static — no implicit-conversion magic from Scala.
 * The call-site convention throughout ported code is:
 * {@code ExtendedNBT.setNewCompoundTag(nbt, "key", t -> { ... })}</p>
 *
 * <p>{@link #typedMapToNbt} and {@link #nbtToTypedMap} handle the
 * bidirectional conversion used by the Lua callback system when Lua code
 * serialises/deserialises raw NBT via the computer API.</p>
 */
public final class ExtendedNBT {

    private ExtendedNBT() {}

    // -----------------------------------------------------------------------
    // CompoundTag helpers
    // -----------------------------------------------------------------------

    /** Writes a new nested CompoundTag built by {@code f} and sets it under {@code name}. */
    public static CompoundTag setNewCompoundTag(CompoundTag nbt, String name,
                                                Consumer<CompoundTag> f) {
        CompoundTag t = new CompoundTag();
        f.accept(t);
        nbt.put(name, t);
        return nbt;
    }

    /** Writes a new ListTag populated with {@code values} and sets it under {@code name}. */
    public static CompoundTag setNewTagList(CompoundTag nbt, String name,
                                            Iterable<? extends Tag> values) {
        ListTag list = new ListTag();
        for (Tag v : values) list.add(v);
        nbt.put(name, list);
        return nbt;
    }

    // -----------------------------------------------------------------------
    // Direction helpers
    // -----------------------------------------------------------------------

    /** Reads an optional Direction stored as a signed byte (−1 → absent). */
    public static Optional<Direction> getDirection(CompoundTag nbt, String name) {
        if (!nbt.contains(name)) return Optional.empty();
        byte id = nbt.getByteOr(name, (byte) -1);
        Direction[] values = Direction.values();
        if (id < 0 || id >= values.length) return Optional.empty();
        return Optional.of(values[id]);
    }

    /** Stores an optional Direction as a signed byte (−1 → absent). */
    public static void setDirection(CompoundTag nbt, String name,
                                    Optional<Direction> direction) {
        nbt.putByte(name, direction.map(d -> (byte) d.ordinal()).orElse((byte) -1));
    }

    // -----------------------------------------------------------------------
    // Boolean array helpers
    // -----------------------------------------------------------------------

    /** Reads a boolean[] stored as a byte array (1 = true, 0 = false). */
    public static boolean[] getBooleanArray(CompoundTag nbt, String name) {
        byte[] bytes = nbt.getByteArray(name).orElse(new byte[0]);
        boolean[] result = new boolean[bytes.length];
        for (int i = 0; i < bytes.length; i++) result[i] = bytes[i] == 1;
        return result;
    }

    /** Stores a boolean[] as a byte array. */
    public static void setBooleanArray(CompoundTag nbt, String name, boolean[] value) {
        byte[] bytes = new byte[value.length];
        for (int i = 0; i < value.length; i++) bytes[i] = value[i] ? (byte) 1 : 0;
        nbt.putByteArray(name, bytes);
    }

    // -----------------------------------------------------------------------
    // ItemStack ↔ CompoundTag
    // -----------------------------------------------------------------------

    /** Serialises an ItemStack to a CompoundTag (empty stack → empty tag). */
    public static CompoundTag itemStackToNbt(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack.isEmpty()) return new CompoundTag();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) ItemStack.CODEC.encodeStart(ops, stack)
            .resultOrPartial(e -> {})
            .orElseGet(CompoundTag::new);
    }

    // -----------------------------------------------------------------------
    // ListTag iteration helpers
    // -----------------------------------------------------------------------

    /** Iterates over a ListTag as CompoundTag entries, running {@code action} on each. */
    public static void forEachCompound(ListTag list, Consumer<CompoundTag> action) {
        for (int i = 0; i < list.size(); i++) {
            Tag tag = list.get(i);
            if (tag instanceof CompoundTag c) action.accept(c);
        }
    }

    // -----------------------------------------------------------------------
    // Lua typed-map ↔ NBT (used by the machine's component-call serialisation)
    // -----------------------------------------------------------------------

    // NBT type IDs (mirror net.minecraftforge.common.util.Constants.NBT)
    public static final int TAG_BYTE       = 1;
    public static final int TAG_SHORT      = 2;
    public static final int TAG_INT        = 3;
    public static final int TAG_LONG       = 4;
    public static final int TAG_FLOAT      = 5;
    public static final int TAG_DOUBLE     = 6;
    public static final int TAG_BYTE_ARRAY = 7;
    public static final int TAG_STRING     = 8;
    public static final int TAG_LIST       = 9;
    public static final int TAG_COMPOUND   = 10;
    public static final int TAG_INT_ARRAY  = 11;

    /**
     * Converts a Lua-style typed map {@code {type: N, value: V}} to the
     * corresponding NBT {@link Tag}.
     *
     * <p>The map must be a {@code Map<String, Object>} with keys "type" (a
     * {@link Number}) and "value" (type-dependent).  This is the same format
     * that the Lua standard library exposes to user scripts via
     * {@code computer.nbt}.</p>
     */
    public static Tag typedMapToNbt(Map<?, ?> map) {
        Object rawType = map.get("type");
        Object rawValue = map.get("value");
        if (!(rawType instanceof Number)) {
            throw new IllegalArgumentException("Missing or non-numeric 'type' key.");
        }
        int type = ((Number) rawType).intValue();
        return switch (type) {
            case TAG_BYTE   -> ByteTag.valueOf(requireNumber(rawValue, "byte").byteValue());
            case TAG_SHORT  -> ShortTag.valueOf(requireNumber(rawValue, "short").shortValue());
            case TAG_INT    -> IntTag.valueOf(requireNumber(rawValue, "int").intValue());
            case TAG_LONG   -> LongTag.valueOf(requireNumber(rawValue, "long").longValue());
            case TAG_FLOAT  -> FloatTag.valueOf(requireNumber(rawValue, "float").floatValue());
            case TAG_DOUBLE -> DoubleTag.valueOf(requireNumber(rawValue, "double").doubleValue());
            case TAG_BYTE_ARRAY -> {
                Object[] elements = requireArray(rawValue);
                byte[] bytes = new byte[elements.length];
                for (int i = 0; i < elements.length; i++)
                    bytes[i] = requireNumber(elements[i], "byte-array element").byteValue();
                yield new ByteArrayTag(bytes);
            }
            case TAG_STRING -> {
                if (rawValue instanceof String s) yield StringTag.valueOf(s);
                if (rawValue instanceof byte[] b) yield StringTag.valueOf(new String(b, StandardCharsets.UTF_8));
                throw new IllegalArgumentException("Illegal value for TAG_STRING.");
            }
            case TAG_LIST -> {
                ListTag list = new ListTag();
                for (Object entry : requireArray(rawValue)) {
                    list.add(typedMapToNbt(requireMap(entry)));
                }
                yield list;
            }
            case TAG_COMPOUND -> {
                CompoundTag compound = new CompoundTag();
                Map<?, ?> entries = requireMap(rawValue);
                for (Map.Entry<?, ?> entry : entries.entrySet()) {
                    String key = entry.getKey().toString();
                    try {
                        compound.put(key, typedMapToNbt(requireMap(entry.getValue())));
                    } catch (Exception ex) {
                        throw new IllegalArgumentException(
                            "Error converting entry '" + key + "': " + ex.getMessage(), ex);
                    }
                }
                yield compound;
            }
            case TAG_INT_ARRAY -> {
                Object[] elements = requireArray(rawValue);
                int[] ints = new int[elements.length];
                for (int i = 0; i < elements.length; i++)
                    ints[i] = requireNumber(elements[i], "int-array element").intValue();
                yield new IntArrayTag(ints);
            }
            default -> throw new IllegalArgumentException("Unsupported NBT type: " + type);
        };
    }

    /**
     * Converts an NBT {@link Tag} to a Lua-style typed map
     * {@code {type: N, value: V}}.
     */
    public static Map<String, Object> nbtToTypedMap(Tag tag) {
        Object value = switch (tag.getId()) {
            case TAG_BYTE   -> ((NumericTag) tag).byteValue();
            case TAG_SHORT  -> ((NumericTag) tag).shortValue();
            case TAG_INT    -> ((NumericTag) tag).intValue();
            case TAG_LONG   -> ((NumericTag) tag).longValue();
            case TAG_FLOAT  -> ((NumericTag) tag).floatValue();
            case TAG_DOUBLE -> ((NumericTag) tag).doubleValue();
            case TAG_BYTE_ARRAY -> ((ByteArrayTag) tag).getAsByteArray();
            case TAG_STRING -> tag.asString().orElse("");
            case TAG_LIST   -> {
                ListTag list = (ListTag) tag;
                List<Map<String, Object>> entries = new ArrayList<>(list.size());
                for (Tag entry : list) entries.add(nbtToTypedMap(entry));
                yield entries;
            }
            case TAG_COMPOUND -> {
                CompoundTag compound = (CompoundTag) tag;
                Map<String, Object> map = new LinkedHashMap<>();
                for (String key : compound.keySet()) {
                    map.put(key, nbtToTypedMap(compound.get(key)));
                }
                yield map;
            }
            case TAG_INT_ARRAY -> ((IntArrayTag) tag).getAsIntArray();
            default -> throw new IllegalArgumentException("Unsupported tag type: " + tag.getId());
        };
        return Map.of("type", (int) tag.getId(), "value", value);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static Number requireNumber(Object v, String context) {
        if (v instanceof Number n) return n;
        throw new IllegalArgumentException("Expected number for " + context + ", got: " + v);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> requireMap(Object v) {
        if (v instanceof Map<?, ?> m) return m;
        throw new IllegalArgumentException("Expected map, got: " + (v == null ? "null" : v.getClass()));
    }

    private static Object[] requireArray(Object v) {
        if (v instanceof Object[] a) return a;
        if (v instanceof List<?> l) return l.toArray();
        throw new IllegalArgumentException("Expected array or list, got: " + (v == null ? "null" : v.getClass()));
    }
}
