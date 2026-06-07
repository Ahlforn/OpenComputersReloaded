package li.cil.oc.client.renderer.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.tileentity.CaseBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public class CaseBlockEntityRenderer implements BlockEntityRenderer<CaseBlockEntity, BlockEntityRenderState> {

    public CaseBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        // Phase 7 stub — visual state is conveyed by blockstate model (running/facing).
        // Future phases may render component labels, status LEDs, etc. here.
    }
}
