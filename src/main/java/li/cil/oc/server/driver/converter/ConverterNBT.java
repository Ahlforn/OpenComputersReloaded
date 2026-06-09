package li.cil.oc.server.driver.converter;

import li.cil.oc.api.driver.Converter;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

public final class ConverterNBT implements Converter {

    @Override
    public void convert(Object value, Map<Object, Object> output) {
        if (value instanceof CompoundTag tag) {
            output.put("oc:flatten", flattenTag(tag));
        }
    }

    private static Object flattenTag(Tag tag) {
        if (tag instanceof NumericTag n) return n.box();
        if (tag instanceof StringTag s) return s.value();
        if (tag instanceof ByteArrayTag b) return b.getAsByteArray();
        if (tag instanceof IntArrayTag i) return i.getAsIntArray();
        if (tag instanceof LongArrayTag l) return l.getAsLongArray();
        if (tag instanceof ListTag list) {
            Object[] arr = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) arr[i] = flattenTag(list.get(i));
            return arr;
        }
        if (tag instanceof CompoundTag compound) {
            Map<Object, Object> map = new HashMap<>();
            for (String key : compound.keySet()) {
                Tag child = compound.get(key);
                if (child != null) map.put(key, flattenTag(child));
            }
            return map;
        }
        return null;
    }
}
