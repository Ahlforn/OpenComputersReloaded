package li.cil.oc.server.machine;

import li.cil.oc.OpenComputersMod;
import li.cil.oc.api.driver.MethodWhitelist;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.FilteredEnvironment;
import li.cil.oc.api.network.ManagedPeripheral;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Faithful Java port of the Scala {@code Callbacks} object: discovers the {@code @Callback}-annotated
 * methods (and {@link ManagedPeripheral} dynamic methods) of a host environment and builds a
 * {@code name -> }{@link Callback} dispatch map. Honors {@link MethodWhitelist} (intersection),
 * {@link NamedBlock} priority ordering, and {@link FilteredEnvironment} per-callback gating.
 *
 * <p><b>Invocation strategy:</b> unlike the Scala original, which emitted an ASM call-wrapper
 * ({@code CallbackWrapper}), {@link ComponentCallback} invokes the target method by plain reflection.
 * Phase 9 already removed ASM from this port; the behaviour is identical.
 *
 * <p><b>Seam:</b> the {@code CompoundBlockEnvironment} (multi-environment block host) branch of
 * {@code dynamicAnalyze} is omitted — block drivers are not ported, so every host is a single
 * environment. When the driver layer lands, add a branch that runs {@code process} per sub-environment.
 */
public final class Callbacks {

    private static final Map<Class<?>, Map<String, Callback>> cache = new ConcurrentHashMap<>();

    private Callbacks() {
    }

    public static Map<String, Callback> apply(Object host) {
        // CompoundBlockEnvironment branch intentionally omitted (driver layer not ported).
        if (host instanceof ManagedPeripheral || host instanceof FilteredEnvironment) {
            return dynamicAnalyze(host);
        }
        return cache.computeIfAbsent(host.getClass(), c -> dynamicAnalyze(host));
    }

    /** Clear the per-class cache; used when a world unloads (config may change which callbacks apply). */
    public static void clear() {
        cache.clear();
    }

    public static Map<String, Callback> fromClass(Class<?> environment) {
        return staticAnalyze(environment, null, null);
    }

    // ----------------------------------------------------------------------- //

    private record Prioritized(int priority, Runnable run) {
    }

    private static Map<String, Callback> dynamicAnalyze(Object host) {
        List<Set<String>> whitelists = new ArrayList<>();
        Map<String, Callback> callbacks = new LinkedHashMap<>();

        // Computed after all environments have contributed their whitelists, before the deferred
        // staticAnalyze steps run (so it is final by the time shouldAdd is consulted).
        @SuppressWarnings("unchecked")
        Set<String>[] whitelist = new Set[]{Collections.emptySet()};
        Predicate<String> shouldAdd = name ->
            !callbacks.containsKey(name) && (whitelist[0].isEmpty() || whitelist[0].contains(name));

        List<Prioritized> steps = new ArrayList<>();
        // Single-environment host only (see class seam note).
        steps.add(process(host, whitelists, callbacks, shouldAdd));

        whitelist[0] = whitelists.stream()
            .reduce((a, b) -> {
                Set<String> r = new HashSet<>(a);
                r.retainAll(b);
                return r;
            })
            .orElse(Collections.emptySet());

        // Highest priority first; Collections.sort is stable, matching Scala's sortBy.
        steps.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        for (Prioritized step : steps) {
            step.run().run();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(callbacks));
    }

    private static Prioritized process(Object environment, List<Set<String>> whitelists,
                                       Map<String, Callback> callbacks, Predicate<String> shouldAdd) {
        if (environment instanceof MethodWhitelist list) {
            String[] methods = list.whitelistedMethods();
            whitelists.add(methods == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(methods)));
        }
        int priority = environment instanceof NamedBlock named ? named.priority() : 0;
        Predicate<String> filter = environment instanceof FilteredEnvironment filtered
            ? (s -> shouldAdd.test(s) && filtered.isCallbackEnabled(s))
            : shouldAdd;

        if (environment instanceof ManagedPeripheral peripheral) {
            return new Prioritized(priority, () -> {
                for (String name : peripheral.methods()) {
                    if (filter.test(name)) {
                        callbacks.put(name, new PeripheralCallback(name));
                    }
                }
                staticAnalyze(environment.getClass(), filter, callbacks);
            });
        }
        return new Prioritized(priority, () -> staticAnalyze(environment.getClass(), filter, callbacks));
    }

    private static Map<String, Callback> staticAnalyze(Class<?> seed, Predicate<String> shouldAdd,
                                                       Map<String, Callback> optCallbacks) {
        Map<String, Callback> callbacks = optCallbacks != null ? optCallbacks : new LinkedHashMap<>();
        Class<?> c = seed;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(li.cil.oc.api.machine.Callback.class)) {
                    continue;
                }
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 2 || params[0] != Context.class || params[1] != Arguments.class) {
                    OpenComputersMod.LOGGER.error(
                        "Invalid use of Callback annotation on {}.{}: invalid argument types or count.",
                        m.getDeclaringClass().getName(), m.getName());
                } else if (m.getReturnType() != Object[].class) {
                    OpenComputersMod.LOGGER.error(
                        "Invalid use of Callback annotation on {}.{}: invalid return type.",
                        m.getDeclaringClass().getName(), m.getName());
                } else if (!Modifier.isPublic(m.getModifiers())) {
                    OpenComputersMod.LOGGER.error(
                        "Invalid use of Callback annotation on {}.{}: method must be public.",
                        m.getDeclaringClass().getName(), m.getName());
                } else {
                    li.cil.oc.api.machine.Callback a = m.getAnnotation(li.cil.oc.api.machine.Callback.class);
                    String name = (a.value() != null && !a.value().trim().isEmpty()) ? a.value() : m.getName();
                    if (shouldAdd == null || shouldAdd.test(name)) {
                        callbacks.put(name, new ComponentCallback(m, a));
                    }
                }
            }
            c = c.getSuperclass();
        }
        return callbacks;
    }

    // ----------------------------------------------------------------------- //

    /** A resolved callback: carries the (possibly synthetic) annotation and knows how to dispatch. */
    public abstract static class Callback {
        private final li.cil.oc.api.machine.Callback annotation;

        protected Callback(li.cil.oc.api.machine.Callback annotation) {
            this.annotation = annotation;
        }

        public li.cil.oc.api.machine.Callback annotation() {
            return annotation;
        }

        public abstract Object[] apply(Object instance, Context context, Arguments args) throws Exception;
    }

    /** A statically discovered {@code @Callback} method, invoked by reflection (no ASM). */
    public static final class ComponentCallback extends Callback {
        private final Method method;

        public Method method() { return method; }

        public ComponentCallback(Method method, li.cil.oc.api.machine.Callback annotation) {
            super(annotation);
            method.setAccessible(true);
            this.method = method;
        }

        @Override
        public Object[] apply(Object instance, Context context, Arguments args) throws Exception {
            try {
                return (Object[]) method.invoke(instance, context, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) throw ex;
                if (cause instanceof Error err) throw err;
                throw e;
            }
        }
    }

    /** A dynamically provided method from a {@link ManagedPeripheral}. */
    public static final class PeripheralCallback extends Callback {
        private final String name;

        public String name() { return name; }

        public PeripheralCallback(String name) {
            super(new PeripheralAnnotation(name));
            this.name = name;
        }

        @Override
        public Object[] apply(Object instance, Context context, Arguments args) throws Exception {
            if (instance instanceof ManagedPeripheral peripheral) {
                return peripheral.invoke(name, context, args);
            }
            throw new NoSuchMethodException();
        }
    }
}
