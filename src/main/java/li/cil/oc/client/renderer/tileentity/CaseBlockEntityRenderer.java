package li.cil.oc.client.renderer.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.tileentity.CaseBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CaseBlockEntityRenderer implements BlockEntityRenderer<CaseBlockEntity> {

    public CaseBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CaseBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        // Phase 7 stub — visual state is conveyed by blockstate model (running/facing).
        // Future phases may render component labels, status LEDs, etc. here.
    }
}
