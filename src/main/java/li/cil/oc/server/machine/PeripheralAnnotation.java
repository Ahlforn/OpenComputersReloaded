package li.cil.oc.server.machine;

import li.cil.oc.api.machine.Callback;

import java.lang.annotation.Annotation;

/**
 * Synthetic {@link Callback} annotation for methods exposed through
 * {@link li.cil.oc.api.network.ManagedPeripheral} (which provide their callbacks dynamically rather
 * than via the {@code @Callback} annotation). Faithful port of the Scala-tree {@code PeripheralAnnotation}.
 *
 * <p>Java class to avoid the warnings Scala emits for hand-implemented annotation types.
 */
@SuppressWarnings("ClassExplicitlyAnnotation")
public class PeripheralAnnotation implements Callback {
    private final String name;

    public PeripheralAnnotation(final String name) {
        this.name = name;
    }

    @Override
    public String value() {
        return name;
    }

    @Override
    public boolean direct() {
        return true;
    }

    @Override
    public int limit() {
        return 100;
    }

    @Override
    public String doc() {
        return "";
    }

    @Override
    public boolean getter() {
        return false;
    }

    @Override
    public boolean setter() {
        return false;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Callback.class;
    }
}
