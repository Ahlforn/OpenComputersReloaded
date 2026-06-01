package li.cil.oc;

import li.cil.oc.api.API;
import li.cil.oc.common.OcPacketPayload;
import li.cil.oc.common.PacketHandler;
import li.cil.oc.common.init.Registries;
import li.cil.oc.server.machine.MachineAPIImpl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * OpenComputers mod entry point for NeoForge 1.21.x+.
 *
 * <p>Replaces the Scala {@code OpenComputers.scala} object which used the
 * removed {@code modLanguage="scala"} FML mechanism, {@code @SidedProxy},
 * and the three-phase {@code FML*Event} lifecycle.</p>
 *
 * <p>Porting is organised in phases (see the migration plan). Registries,
 * event subscriptions, and capability registration are added phase by phase.
 * Until a phase is complete the feature its covers will not function, but the
 * mod will load without errors.</p>
 *
 * <p><b>Phase status:</b></p>
 * <ul>
 *   <li>Phase 0 — Toolchain &amp; scaffolding (this file)</li>
 *   <li>Phase 1 — Forge-agnostic core: Network graph, Machine, LuaJ backend</li>
 *   <li>Phase 2 — Registration (DeferredRegister for blocks/items/BEs)</li>
 *   <li>Phase 3 — Block entities &amp; traits</li>
 *   <li>Phase 4 — NBT &amp; data components</li>
 *   <li>Phase 5 — Networking (CustomPacketPayload)</li>
 *   <li>Phase 6 — GUI / menus</li>
 *   <li>Phase 7 — Rendering (BlockEntityRenderer, PoseStack)</li>
 *   <li>Phase 8 — Capabilities &amp; energy</li>
 *   <li>Phase 9 — SimpleComponent redesign (interface + capability)</li>
 *   <li>Phase 10 — Public Java API revision (breaking bump)</li>
 *   <li>Phase 11 — Bump NeoForge 1.21.x → 26.1 (Java 25)</li>
 * </ul>
 */
@Mod(OpenComputersMod.MOD_ID)
public class OpenComputersMod {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    public static final String MOD_ID = "opencomputers";
    /** @deprecated use {@link #MOD_ID} */
    @Deprecated
    public static final String ID   = MOD_ID;
    public static final String NAME = "OpenComputers";

    // -----------------------------------------------------------------------
    // Logger
    // -----------------------------------------------------------------------

    public static final Logger LOGGER = LogManager.getLogger(NAME);

    // -----------------------------------------------------------------------
    // Mod event bus (stored for use by registration helpers in later phases)
    // -----------------------------------------------------------------------

    private final IEventBus modEventBus;

    // -----------------------------------------------------------------------
    // Constructor — called by NeoForge during mod loading
    // -----------------------------------------------------------------------

    public OpenComputersMod(IEventBus modEventBus, ModContainer container) {
        this.modEventBus = modEventBus;

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterPayloadHandlers);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientSetup::onRegisterMenuScreens);
        }

        // Phase 2: DeferredRegisters for blocks, items, block-entity types, creative tab.
        Registries.register(modEventBus);

        // TODO Phase 5:  register CustomPacketPayload types via
        //                RegisterPayloadHandlersEvent on the mod bus.
        // TODO Phase 6:  Registries.MENU_TYPES.register(modEventBus);
        // TODO Phase 8:  RegisterCapabilitiesEvent subscriber on the mod bus.

        LOGGER.info("OpenComputers loading — NeoForge port Phase 2 (registration).");
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Replaces the old FMLCommonSetupEvent / proxy.init() call.
     *
     * <p>Phase 2+ will move registration to DeferredRegister; this method
     * handles work that must happen <em>after</em> registration completes,
     * e.g. cross-mod integration queries (when those phases land).</p>
     */
    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        PacketHandler handler = new PacketHandler();
        registrar.playBidirectional(
            OcPacketPayload.TYPE,
            OcPacketPayload.STREAM_CODEC,
            handler::handle
        );
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("OpenComputers common setup.");

        // Phase 3: wire MachineAPI so Case block entities can create machines.
        API.machine = new MachineAPIImpl();

        // TODO Phase 8:  register external energy bridges (Forge Energy).
    }
}
