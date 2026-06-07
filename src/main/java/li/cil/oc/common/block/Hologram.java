package li.cil.oc.common.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

public class Hologram extends OcBlock {

    private final int tier;

    public Hologram(int tier, BlockBehaviour.Properties props) {
        super(props);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }
}
