package li.cil.oc.server.machine;

import li.cil.oc.api.machine.Arguments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

/**
 * Faithful Java port of the Scala {@code ArgumentsImpl}. Wraps the raw argument array passed to a
 * {@link li.cil.oc.api.machine.Callback} and provides the type-checked accessors of the
 * {@link Arguments} interface, preserving the original error-message wording and the integer/long
 * clamping behaviour (NaN throws; out-of-range clamps to MIN/MAX rather than wrapping).
 *
 * <p>{@link #checkItemStack(int)} parses the {name,damage,tag} table shape via the MC 26.1 item
 * registry ({@link net.minecraft.core.registries.BuiltInRegistries#ITEM}) and data components.
 */
public class ArgumentsImpl implements Arguments {
    private final Object[] args;

    public ArgumentsImpl(Object[] args) {
        this.args = args != null ? args : new Object[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> iterator() {
        return (Iterator<Object>) (Iterator<?>) Arrays.asList(args).iterator();
    }

    @Override
    public int count() {
        return args.length;
    }

    @Override
    public Object checkAny(int index) {
        checkIndex(index, "value");
        return args[index];
    }

    @Override
    public Object optAny(int index, Object def) {
        return !isDefined(index) ? def : checkAny(index);
    }

    @Override
    public boolean checkBoolean(int index) {
        checkIndex(index, "boolean");
        Object value = args[index];
        if (value instanceof Boolean b) return b;
        throw typeError(index, value, "boolean");
    }

    @Override
    public boolean optBoolean(int index, boolean def) {
        return !isDefined(index) ? def : checkBoolean(index);
    }

    @Override
    public double checkDouble(int index) {
        checkIndex(index, "number");
        Object value = args[index];
        if (value instanceof Number n) return n.doubleValue();
        throw typeError(index, value, "number");
    }

    @Override
    public double optDouble(int index, double def) {
        return !isDefined(index) ? def : checkDouble(index);
    }

    @Override
    public int checkInteger(int index) {
        checkIndex(index, "integer");
        Object value = args[index];
        if (value instanceof Double d) {
            if (d.isNaN()) throw intError(index, value);
            else if (d > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            else if (d < Integer.MIN_VALUE) return Integer.MIN_VALUE;
            else return d.intValue();
        } else if (value instanceof Float f) {
            if (f.isNaN()) throw intError(index, value);
            else if (f > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            else if (f < Integer.MIN_VALUE) return Integer.MIN_VALUE;
            else return f.intValue();
        } else if (value instanceof Long l) {
            if (l > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            else if (l < Integer.MIN_VALUE) return Integer.MIN_VALUE;
            else return l.intValue();
        } else if (value instanceof Number n) {
            return n.intValue();
        }
        throw typeError(index, value, "integer");
    }

    @Override
    public int optInteger(int index, int def) {
        return !isDefined(index) ? def : checkInteger(index);
    }

    @Override
    public long checkLong(int index) {
        checkIndex(index, "integer");
        Object value = args[index];
        if (value instanceof Double d) {
            if (d.isNaN()) throw intError(index, value);
            else if (d > Long.MAX_VALUE) return Long.MAX_VALUE;
            else if (d < Long.MIN_VALUE) return Long.MIN_VALUE;
            else return d.longValue();
        } else if (value instanceof Float f) {
            if (f.isNaN()) throw intError(index, value);
            else if (f > Long.MAX_VALUE) return Long.MAX_VALUE;
            else if (f < Long.MIN_VALUE) return Long.MIN_VALUE;
            else return f.longValue();
        } else if (value instanceof Number n) {
            return n.longValue();
        }
        throw typeError(index, value, "integer");
    }

    @Override
    public long optLong(int index, long def) {
        return !isDefined(index) ? def : checkLong(index);
    }

    @Override
    public String checkString(int index) {
        checkIndex(index, "string");
        Object value = args[index];
        if (value instanceof String s) return s;
        if (value instanceof byte[] b) return new String(b, StandardCharsets.UTF_8);
        throw typeError(index, value, "string");
    }

    @Override
    public String optString(int index, String def) {
        return !isDefined(index) ? def : checkString(index);
    }

    @Override
    public byte[] checkByteArray(int index) {
        checkIndex(index, "string");
        Object value = args[index];
        if (value instanceof String s) return s.getBytes(StandardCharsets.UTF_8);
        if (value instanceof byte[] b) return b;
        throw typeError(index, value, "string");
    }

    @Override
    public byte[] optByteArray(int index, byte[] def) {
        return !isDefined(index) ? def : checkByteArray(index);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map checkTable(int index) {
        checkIndex(index, "table");
        Object value = args[index];
        if (value instanceof Map<?, ?> m) return m;
        throw typeError(index, value, "table");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map optTable(int index, Map def) {
        return !isDefined(index) ? def : checkTable(index);
    }

    @Override
    public ItemStack checkItemStack(int index) {
        Map<?, ?> map = checkTable(index);
        Object name = map.get("name");
        if (name instanceof String s) {
            int damage = map.get("damage") instanceof Number n ? n.intValue() : 0;
            Object tag = map.get("tag");
            return makeStack(s, damage, tag);
        }
        throw new IllegalArgumentException("invalid item stack");
    }

    @Override
    public ItemStack optItemStack(int index, ItemStack def) {
        return !isDefined(index) ? def : checkItemStack(index);
    }

    @Override
    public boolean isBoolean(int index) {
        return index >= 0 && index < count() && args[index] instanceof Boolean;
    }

    @Override
    public boolean isDouble(int index) {
        return index >= 0 && index < count() && args[index] instanceof Number;
    }

    @Override
    public boolean isInteger(int index) {
        if (index < 0 || index >= count()) return false;
        Object value = args[index];
        if (value instanceof Double d) return !d.isNaN();
        if (value instanceof Float f) return !f.isNaN();
        return value instanceof Number;
    }

    @Override
    public boolean isLong(int index) {
        if (index < 0 || index >= count()) return false;
        Object value = args[index];
        if (value instanceof Double d) return !d.isNaN();
        if (value instanceof Float f) return !f.isNaN();
        return value instanceof Number;
    }

    @Override
    public boolean isString(int index) {
        if (index < 0 || index >= count()) return false;
        Object value = args[index];
        return value instanceof String || value instanceof byte[];
    }

    @Override
    public boolean isByteArray(int index) {
        return isString(index);
    }

    @Override
    public boolean isTable(int index) {
        return index >= 0 && index < count() && args[index] instanceof Map;
    }

    @Override
    public boolean isItemStack(int index) {
        if (!isTable(index)) return false;
        Object name = checkTable(index).get("name");
        return name instanceof String || name instanceof byte[];
    }

    @Override
    public Object[] toArray() {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = args[i] instanceof byte[] b ? new String(b, StandardCharsets.UTF_8) : args[i];
        }
        return out;
    }

    // ----------------------------------------------------------------------- //

    private boolean isDefined(int index) {
        return index >= 0 && index < args.length && args[index] != null;
    }

    private void checkIndex(int index, String name) {
        if (index < 0) throw new IndexOutOfBoundsException();
        else if (args.length <= index) throw new IllegalArgumentException(
            "bad arguments #" + (index + 1) + " (" + name + " expected, got no value)");
    }

    private IllegalArgumentException typeError(int index, Object have, String want) {
        return new IllegalArgumentException(
            "bad argument #" + (index + 1) + " (" + want + " expected, got " + typeName(have) + ")");
    }

    private IllegalArgumentException intError(int index, Object have) {
        return new IllegalArgumentException(
            "bad argument #" + (index + 1) + " (" + typeName(have) + " has no integer representation)");
    }

    private String typeName(Object value) {
        if (value == null) return "nil";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Byte || value instanceof Short
            || value instanceof Integer || value instanceof Long) return "integer";
        if (value instanceof Number) return "number";
        if (value instanceof String || value instanceof byte[]) return "string";
        if (value instanceof Map) return "table";
        return value.getClass().getSimpleName();
    }

    private ItemStack makeStack(String name, int damage, Object tag) {
        var optItem = BuiltInRegistries.ITEM.getOptional(Identifier.parse(name));
        if (optItem.isEmpty()) throw new IllegalArgumentException("invalid item stack");
        var stack = new ItemStack(optItem.get(), 1);
        if (damage > 0) stack.set(DataComponents.DAMAGE, damage);
        if (tag instanceof CompoundTag compound) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(compound));
        }
        return stack;
    }
}
