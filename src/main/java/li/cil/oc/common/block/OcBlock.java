package li.cil.oc.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Base class for all OpenComputers blocks.
 *
 * <p>Replaces the 1.12.2 {@code SimpleBlock} (which extended {@code BlockContainer}).
 * Block-entity wiring, rotation state, and GUI handling are added in Phase 3+.</p>
 */
public class OcBlock extends Block {

    public OcBlock() {
        this(defaultProperties());
    }

    public OcBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0f, 5.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();
    }
}
