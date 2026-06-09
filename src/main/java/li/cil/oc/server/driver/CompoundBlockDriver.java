package li.cil.oc.server.driver;

import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompoundBlockDriver implements DriverBlock {
    private final DriverBlock[] drivers;

    public CompoundBlockDriver(DriverBlock[] drivers) {
        this.drivers = drivers.clone();
    }

    @Override
    public CompoundBlockEnvironment createEnvironment(Level world, BlockPos pos, Direction side) {
        List<String[]> pairs = new ArrayList<>();
        for (DriverBlock driver : drivers) {
            ManagedEnvironment env = driver.createEnvironment(world, pos, side);
            if (env != null) pairs.add(new String[]{driver.getClass().getName()});
        }
        // Collect (name, env) pairs properly
        List<Object[]> envPairs = new ArrayList<>();
        for (DriverBlock driver : drivers) {
            ManagedEnvironment env = driver.createEnvironment(world, pos, side);
            if (env != null) envPairs.add(new Object[]{driver.getClass().getName(), env});
        }
        if (envPairs.isEmpty()) return null;
        String name = cleanName(tryGetName(world, pos, envPairs));
        return new CompoundBlockEnvironment(name, envPairs);
    }

    @Override
    public boolean worksWith(Level world, BlockPos pos, Direction side) {
        for (DriverBlock driver : drivers) if (!driver.worksWith(world, pos, side)) return false;
        return drivers.length > 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CompoundBlockDriver other)) return false;
        if (other.drivers.length != drivers.length) return false;
        int matches = 0;
        for (DriverBlock d : drivers) for (DriverBlock od : other.drivers) if (d == od) matches++;
        return matches == drivers.length;
    }

    // -----------------------------------------------------------------------

    private String tryGetName(Level world, BlockPos pos, List<Object[]> envPairs) {
        // 1. Prefer NamedBlock with highest priority
        NamedBlock best = null;
        for (Object[] pair : envPairs) {
            if (pair[1] instanceof NamedBlock nb) {
                if (best == null || nb.priority() > best.priority()) best = nb;
            }
        }
        if (best != null) return best.preferredName();

        // 2. Block item translation key
        try {
            BlockState state = world.getBlockState(pos);
            return state.getBlock().getDescriptionId().replaceFirst("^block\\.", "");
        } catch (Throwable ignored) {}

        // 3. BlockEntity registry name
        try {
            BlockEntity be = world.getBlockEntity(pos);
            if (be != null) {
                var type = be.getType();
                var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                if (key != null) return key.getPath();
            }
        } catch (Throwable ignored) {}

        return "component";
    }

    private static String cleanName(String name) {
        if (name == null || name.isEmpty()) return "component";
        String safe = name.matches("^[^a-zA-Z_].*") ? "_" + name : name;
        String id = safe.replaceAll("[^\\w_]", "_").trim().toLowerCase();
        return id.isEmpty() ? "component" : id;
    }
}
