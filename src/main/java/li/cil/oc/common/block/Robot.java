package li.cil.oc.common.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class Robot extends OcBlock {
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0f, 5.0f)
            .noOcclusion();
    }

    public Robot(BlockBehaviour.Properties props) {
        super(props);
    }
}
