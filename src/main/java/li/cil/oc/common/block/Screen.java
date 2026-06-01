package li.cil.oc.common.block;

public class Screen extends OcBlock {

    private final int tier;

    public Screen(int tier) {
        super();
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }
}
