package li.cil.oc.common.capabilities;

import li.cil.oc.OpenComputersMod;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

public final class OcCapabilities {

    public static final BlockCapability<Environment, @Nullable Direction> ENVIRONMENT =
        BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath(OpenComputersMod.MOD_ID, "environment"),
            Environment.class);

    public static final BlockCapability<SidedEnvironment, @Nullable Direction> SIDED_ENVIRONMENT =
        BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath(OpenComputersMod.MOD_ID, "sided_environment"),
            SidedEnvironment.class);

    // COLORED capability (api/internal/Colored) will be added in Phase 10 (API revision)

    private OcCapabilities() {}
}
