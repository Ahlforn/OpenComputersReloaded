package li.cil.oc.server.driver.converter;

import li.cil.oc.api.driver.Converter;
import net.minecraft.world.level.Level;

import java.util.Map;

public final class ConverterWorld implements Converter {

    @Override
    public void convert(Object value, Map<Object, Object> output) {
        if (value instanceof Level level) {
            output.put("name", level.dimension().identifier().toString());
        }
    }
}
