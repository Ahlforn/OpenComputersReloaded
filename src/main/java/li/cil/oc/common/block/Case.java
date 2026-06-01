package li.cil.oc.common.block;

import li.cil.oc.common.Tier;

public class Case extends OcBlock {

    private final int tier;

    public Case(int tier) {
        super();
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    public boolean isCreative() {
        return tier >= Tier.FOUR;
    }
}
