package li.cil.oc.client.gui;

import li.cil.oc.common.container.CaseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side screen for the computer case GUI.
 *
 * <p>Phase 7 adds texture rendering, slot highlights, and the status LED.
 * This stub simply renders nothing, keeping the game from crashing when
 * a player right-clicks a case.</p>
 */
@OnlyIn(Dist.CLIENT)
public class CaseScreen extends AbstractContainerScreen<CaseMenu> {

    public CaseScreen(CaseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth  = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Phase 7: draw the background texture.
        // For now, draw a plain dark rectangle so the screen isn't invisible.
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
    }
}
