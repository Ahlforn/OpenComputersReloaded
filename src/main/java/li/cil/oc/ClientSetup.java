package li.cil.oc;

import li.cil.oc.client.gui.CaseScreen;
import li.cil.oc.client.renderer.tileentity.CaseBlockEntityRenderer;
import li.cil.oc.common.init.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@OnlyIn(Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(Registries.CASE_MENU.get(), CaseScreen::new);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Registries.CASE_BE.get(), CaseBlockEntityRenderer::new);
    }
}
