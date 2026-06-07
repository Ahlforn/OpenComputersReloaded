package li.cil.oc.common.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class Cable extends OcBlock {

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.0f, 2.0f)
            .sound(SoundType.METAL)
            .noOcclusion();
    }

    public Cable(BlockBehaviour.Properties props) {
        super(props);
    }
}
