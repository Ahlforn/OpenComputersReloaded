package li.cil.oc;

import li.cil.oc.client.gui.CaseScreen;
import li.cil.oc.common.init.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup: screen registrations, key bindings, renderers.
 *
 * <p>Called from {@link OpenComputersMod} only when {@code Dist == CLIENT}.
 * Phase 7 adds block entity renderers and texture loading here.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(Registries.CASE_MENU.get(), CaseScreen::new);
    }
}
