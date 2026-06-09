package li.cil.oc.server.driver.converter;

import li.cil.oc.Settings;
import li.cil.oc.api.driver.Converter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConverterItemStack implements Converter {

    @Override
    public void convert(Object value, Map<Object, Object> output) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return;

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        output.put("name", itemId != null ? itemId.toString() : "unknown");
        output.put("label", stack.getHoverName().getString());
        output.put("size", stack.getCount());
        output.put("maxSize", stack.getMaxStackSize());

        Integer damage = stack.get(DataComponents.DAMAGE);
        Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
        output.put("damage", damage != null ? damage : 0);
        output.put("maxDamage", maxDamage != null ? maxDamage : 0);

        var customData = stack.get(DataComponents.CUSTOM_DATA);
        boolean hasTag = customData != null && !customData.isEmpty();
        output.put("hasTag", hasTag);

        if (hasTag && Settings.get() != null && Settings.get().allowItemStackNBTTags) {
            output.put("tag", customData.copyTag());
        }

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            List<Map<Object, Object>> enchList = new ArrayList<>();
            for (var entry : enchantments.entrySet()) {
                var enchHolder = entry.getKey();
                var enchantment = enchHolder.value();
                Map<Object, Object> e = new HashMap<>();
                e.put("name", enchHolder.getKey() != null ? enchHolder.getKey().identifier().toString() : "unknown");
                e.put("label", enchantment.description().getString());
                e.put("level", entry.getIntValue());
                enchList.add(e);
            }
            output.put("enchantments", enchList.toArray());
        }
    }
}
