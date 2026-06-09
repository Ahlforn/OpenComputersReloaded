package li.cil.oc.client.gui;

import li.cil.oc.client.PacketSender;
import li.cil.oc.common.tileentity.WaypointBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class WaypointScreen extends Screen {
    private static final int MAX_LABEL_LENGTH = 32;
    private static final double MAX_EDIT_DISTANCE = 64.0;

    private final WaypointBlockEntity waypoint;
    private EditBox labelField;

    public WaypointScreen(WaypointBlockEntity waypoint) {
        super(Component.translatable("gui.opencomputers.waypoint"));
        this.waypoint = waypoint;
    }

    @Override
    protected void init() {
        int x = (width - 200) / 2;
        int y = (height - 20) / 2;
        labelField = new EditBox(font, x, y, 200, 20, Component.literal(""));
        labelField.setMaxLength(MAX_LABEL_LENGTH);
        labelField.setValue(waypoint.label);
        labelField.setFocused(true);
        addRenderableWidget(labelField);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Enter (257) or numpad Enter (335) — send and close.
        if (event.key() == 257 || event.key() == 335) {
            waypoint.label = labelField.getValue();
            PacketSender.sendWaypointLabel(waypoint);
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { onClose(); return; }
        Vec3 playerPos = mc.player.position();
        Vec3 blockCenter = Vec3.atCenterOf(waypoint.getBlockPos());
        if (playerPos.distanceTo(blockCenter) > MAX_EDIT_DISTANCE) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
