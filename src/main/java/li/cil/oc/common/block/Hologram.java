package li.cil.oc.common.block;

public class Hologram extends OcBlock {

    private final int tier;

    public Hologram(int tier) {
        super();
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }
}
