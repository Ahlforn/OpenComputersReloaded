package li.cil.oc.server.driver;

import li.cil.oc.OpenComputersMod;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.InventoryProvider;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Minimal port of the Scala driver {@code Registry}, covering only the return-value conversion used
 * by component dispatch ({@code convert}/{@code convertRecursively}/{@code convertList}/{@code convertMap},
 * faithful to {@code Registry.scala:151-252}).
 *
 * <p>The full driver registry (block/item drivers, environment providers, inventory providers) is not
 * ported yet. The {@link #converters} list and {@link #add(Converter)} hook are wired so the driver
 * layer can register type converters later; with no converters registered the recursive conversion is
 * already correct for all primitives, arrays, maps, collections and {@link Value}s (unknown reference
 * types fall back to {@code toString}), which is exactly the behaviour callbacks see today.
 */
public final class Registry {

    private static boolean locked = false;

    private static final List<Converter> converters = new ArrayList<>();
    private static final List<DriverBlock> sidedBlocks = new ArrayList<>();
    private static final List<DriverItem> items = new ArrayList<>();
    private static final List<EnvironmentProvider> environmentProviders = new ArrayList<>();
    private static final List<InventoryProvider> inventoryProviders = new ArrayList<>();

    private Registry() {
    }

    /** Locks all registries — call after mod init. */
    public static void lock() {
        locked = true;
    }

    /** Register a block driver. Idempotent. */
    public static void add(DriverBlock driver) {
        if (locked) throw new IllegalStateException("Please register all drivers in the init phase.");
        if (!sidedBlocks.contains(driver)) sidedBlocks.add(driver);
    }

    /** Register an item driver. Idempotent. */
    public static void add(DriverItem driver) {
        if (locked) throw new IllegalStateException("Please register all drivers in the init phase.");
        if (!items.contains(driver)) items.add(driver);
    }

    /** Register a type converter. Idempotent. */
    public static void add(Converter converter) {
        if (locked) throw new IllegalStateException("Please register all converters in the init phase.");
        if (!converters.contains(converter)) {
            converters.add(converter);
        }
    }

    /** Register an environment provider. Idempotent. */
    public static void add(EnvironmentProvider provider) {
        if (locked) throw new IllegalStateException("Please register all environment providers in the init phase.");
        if (!environmentProviders.contains(provider)) environmentProviders.add(provider);
    }

    /** Register an inventory provider. Idempotent. */
    public static void add(InventoryProvider provider) {
        if (locked) throw new IllegalStateException("Please register all inventory providers in the init phase.");
        if (!inventoryProviders.contains(provider)) inventoryProviders.add(provider);
    }

    /** Find and wrap all block drivers matching this position. Returns null when none match. */
    public static DriverBlock driverFor(Level world, BlockPos pos, Direction side) {
        List<DriverBlock> matching = new ArrayList<>();
        for (DriverBlock d : sidedBlocks) if (d.worksWith(world, pos, side)) matching.add(d);
        if (matching.isEmpty()) return null;
        return new CompoundBlockDriver(matching.toArray(new DriverBlock[0]));
    }

    /** Host-aware item driver lookup. */
    public static DriverItem driverFor(ItemStack stack, Class<? extends li.cil.oc.api.network.EnvironmentHost> host) {
        if (stack.isEmpty()) return null;
        List<li.cil.oc.api.driver.item.HostAware> hostAware = new ArrayList<>();
        for (DriverItem d : items) {
            if (d instanceof li.cil.oc.api.driver.item.HostAware ha && ha.worksWith(stack)) hostAware.add(ha);
        }
        if (!hostAware.isEmpty()) {
            for (li.cil.oc.api.driver.item.HostAware ha : hostAware) {
                if (ha.worksWith(stack, host)) return (DriverItem) ha;
            }
            return null;
        }
        return driverFor(stack);
    }

    /** Simple item driver lookup. */
    public static DriverItem driverFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (DriverItem d : items) if (d.worksWith(stack)) return d;
        return null;
    }

    public static Object[] convert(Object[] value) {
        if (value == null) return null;
        Object[] out = new Object[value.length];
        for (int i = 0; i < value.length; i++) {
            out[i] = convertRecursively(value[i], new IdentityHashMap<>(), false);
        }
        return out;
    }

    static Object convertRecursively(Object value, IdentityHashMap<Object, Object> memo, boolean force) {
        if (value == null) return null;
        if (!force && memo.containsKey(value)) {
            return memo.get(value);
        }

        // Scalars passed straight through; any other Number is coerced to double (Lua's number type).
        if (value instanceof Boolean || value instanceof Byte || value instanceof Character
            || value instanceof Short || value instanceof Integer || value instanceof Long
            || value instanceof Float || value instanceof Double || value instanceof String) {
            return value;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Value) {
            return value;
        }

        // Arrays of scalars pass straight through (String[] must precede the generic Object[] check).
        if (value instanceof boolean[] || value instanceof byte[] || value instanceof short[]
            || value instanceof int[] || value instanceof long[] || value instanceof float[]
            || value instanceof double[] || value instanceof char[] || value instanceof String[]
            || value instanceof Boolean[] || value instanceof Byte[] || value instanceof Character[]
            || value instanceof Short[] || value instanceof Integer[] || value instanceof Long[]
            || value instanceof Float[] || value instanceof Double[]) {
            return value;
        }

        if (value instanceof Object[] arr) {
            return convertList(value, Arrays.asList(arr).iterator(), memo);
        }
        if (value instanceof Map<?, ?> m) {
            return convertMap(value, m, memo);
        }
        if (value instanceof Iterable<?> it) {
            return convertList(value, it.iterator(), memo);
        }

        // Unknown reference type: offer it to the registered converters; fall back to toString.
        Map<Object, Object> converted = new HashMap<>();
        memo.put(value, converted);
        for (Converter converter : converters) {
            try {
                converter.convert(value, converted);
            } catch (Throwable t) {
                OpenComputersMod.LOGGER.warn("Type converter threw an exception.", t);
            }
        }
        if (converted.isEmpty()) {
            String s = value.toString();
            memo.put(value, s);
            return s;
        } else {
            memo.put(converted, converted); // Makes convertMap re-use the map.
            convertRecursively(converted, memo, true);
            memo.remove(converted);
            if (converted.size() == 1 && converted.containsKey("oc:flatten")) {
                Object flattened = converted.get("oc:flatten");
                memo.put(value, flattened);
                return flattened;
            }
            return converted;
        }
    }

    private static Object convertList(Object obj, Iterator<?> values, IdentityHashMap<Object, Object> memo) {
        List<Object> converted = new ArrayList<>();
        memo.put(obj, converted);
        while (values.hasNext()) {
            converted.add(convertRecursively(values.next(), memo, false));
        }
        return converted.toArray();
    }

    @SuppressWarnings("unchecked")
    private static Object convertMap(Object obj, Map<?, ?> map, IdentityHashMap<Object, Object> memo) {
        Map<Object, Object> converted = (Map<Object, Object>) memo.computeIfAbsent(obj, k -> new HashMap<>());
        // Iterate a snapshot so a self-referential map (obj == converted, generic-converter path)
        // does not throw ConcurrentModificationException.
        for (Map.Entry<?, ?> entry : new ArrayList<>(map.entrySet())) {
            if (entry.getKey() != null && entry.getValue() != null) {
                converted.put(
                    convertRecursively(entry.getKey(), memo, false),
                    convertRecursively(entry.getValue(), memo, false));
            }
        }
        return memo.get(obj);
    }
}
