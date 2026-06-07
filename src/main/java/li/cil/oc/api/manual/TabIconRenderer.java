package li.cil.oc.api.manual;


/**
 * Allows defining a renderer for a manual tab.
 * <br>
 * Each renderer instance represents the single graphic it is drawing. To
 * provide different graphics for different tabs you'll need to create
 * multiple tab renderer instances.
 * <br>
 *
 * Prefab implementations: {@code ItemStackTabIconRenderer} and
 * {@code TextureTabIconRenderer} in {@code li.cil.oc.api.prefab}.
 */
public interface TabIconRenderer {
    /**
     * Called when icon of a tab should be rendered.
     * <br>
     * This should render something in a 16x16 area. The OpenGL state has been
     * adjusted so that drawing starts at (0,0,0), and should go to (16,16,0).
     */
    void render();
}
