package li.cil.oc.common.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class Print extends OcBlock {
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(1.5f, 5.0f)
            .noOcclusion();
    }

    public Print(BlockBehaviour.Properties props) {
        super(props);
    }
}
