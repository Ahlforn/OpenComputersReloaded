package li.cil.oc.common.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class Robot extends OcBlock {
    public Robot() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0f, 5.0f)
            .noOcclusion());
    }
}
