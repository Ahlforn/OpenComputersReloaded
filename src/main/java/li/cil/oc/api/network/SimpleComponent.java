package li.cil.oc.api.network;

/**
 * Implement this interface on a {@code BlockEntity} to make it discoverable
 * as an OpenComputers component <em>without</em> a dedicated OC block driver.
 *
 * <p>Unlike the original 1.12.2 design (which used ASM bytecode injection),
 * this version requires the implementing class to also implement
 * {@link Environment} and manage its own node lifecycle. The easiest way is
 * to extend {@link li.cil.oc.api.prefab.AbstractSimpleBlockEntity}, which
 * handles node creation, network join/leave, and NBT persistence.
 *
 * <p>To expose methods to Lua, annotate them with
 * {@link li.cil.oc.api.machine.Callback}.
 *
 * <p>Example:
 * <pre>
 * public class MyBlockEntity extends AbstractSimpleBlockEntity {
 *     {@literal @}Override
 *     public String getComponentName() { return "my_thing"; }
 *
 *     {@literal @}Callback
 *     public Object[] greet(Context ctx, Arguments args) {
 *         return new Object[]{"hello from my_thing"};
 *     }
 * }
 * </pre>
 *
 * <p><b>Migration from 1.12.2:</b> remove {@code @Optional.Interface} and
 * {@code @Optional.Method}, extend {@code AbstractSimpleBlockEntity} instead
 * of plain {@code TileEntity}, and implement {@code getComponentName()}.
 */
public interface SimpleComponent {
    /**
     * The name of this component as seen by Lua scripts (e.g. in
     * {@code component.list()}). Use lowercase with underscores.
     */
    String getComponentName();
}
