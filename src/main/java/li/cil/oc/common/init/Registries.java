package li.cil.oc.common.init;

import li.cil.oc.Constants;
import li.cil.oc.OpenComputersMod;
import li.cil.oc.common.Tier;
import li.cil.oc.common.block.*;
import li.cil.oc.common.tileentity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central registry for all OC blocks, items, block-entity types, and creative tabs.
 *
 * <p>Call {@link #register(IEventBus)} once from the mod constructor to attach every
 * DeferredRegister to the mod event bus.</p>
 *
 * <p>Phase 3 adds BLOCK_ENTITY_TYPES entries as block entities are ported.
 * Phase 6 adds MENU_TYPES.
 * Phase 8 adds capability registration.</p>
 */
public final class Registries {

    // -----------------------------------------------------------------------
    // DeferredRegisters
    // -----------------------------------------------------------------------

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(OpenComputersMod.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(OpenComputersMod.MOD_ID);

    @SuppressWarnings("unchecked")
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, OpenComputersMod.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, OpenComputersMod.MOD_ID);

    // -----------------------------------------------------------------------
    // Blocks — one entry per registered block (no metadata subtypes)
    // -----------------------------------------------------------------------

    public static final DeferredBlock<Case> CASE_TIER1 =
        BLOCKS.register(Constants.BlockName.CaseTier1, () -> new Case(Tier.ONE));
    public static final DeferredBlock<Case> CASE_TIER2 =
        BLOCKS.register(Constants.BlockName.CaseTier2, () -> new Case(Tier.TWO));
    public static final DeferredBlock<Case> CASE_TIER3 =
        BLOCKS.register(Constants.BlockName.CaseTier3, () -> new Case(Tier.THREE));
    public static final DeferredBlock<Case> CASE_CREATIVE =
        BLOCKS.register(Constants.BlockName.CaseCreative, () -> new Case(Tier.FOUR));

    public static final DeferredBlock<Screen> SCREEN_TIER1 =
        BLOCKS.register(Constants.BlockName.ScreenTier1, () -> new Screen(Tier.ONE));
    public static final DeferredBlock<Screen> SCREEN_TIER2 =
        BLOCKS.register(Constants.BlockName.ScreenTier2, () -> new Screen(Tier.TWO));
    public static final DeferredBlock<Screen> SCREEN_TIER3 =
        BLOCKS.register(Constants.BlockName.ScreenTier3, () -> new Screen(Tier.THREE));

    public static final DeferredBlock<Hologram> HOLOGRAM_TIER1 =
        BLOCKS.register(Constants.BlockName.HologramTier1, () -> new Hologram(Tier.ONE));
    public static final DeferredBlock<Hologram> HOLOGRAM_TIER2 =
        BLOCKS.register(Constants.BlockName.HologramTier2, () -> new Hologram(Tier.TWO));

    public static final DeferredBlock<Adapter>          ADAPTER          = BLOCKS.register(Constants.BlockName.Adapter,          Adapter::new);
    public static final DeferredBlock<Assembler>        ASSEMBLER        = BLOCKS.register(Constants.BlockName.Assembler,        Assembler::new);
    public static final DeferredBlock<Cable>            CABLE            = BLOCKS.register(Constants.BlockName.Cable,            Cable::new);
    public static final DeferredBlock<Capacitor>        CAPACITOR        = BLOCKS.register(Constants.BlockName.Capacitor,        Capacitor::new);
    public static final DeferredBlock<CarpetedCapacitor> CARPETED_CAPACITOR = BLOCKS.register(Constants.BlockName.CarpetedCapacitor, CarpetedCapacitor::new);
    public static final DeferredBlock<Charger>          CHARGER          = BLOCKS.register(Constants.BlockName.Charger,          Charger::new);
    public static final DeferredBlock<Disassembler>     DISASSEMBLER     = BLOCKS.register(Constants.BlockName.Disassembler,     Disassembler::new);
    public static final DeferredBlock<DiskDrive>        DISK_DRIVE       = BLOCKS.register(Constants.BlockName.DiskDrive,        DiskDrive::new);
    public static final DeferredBlock<Geolyzer>         GEOLYZER         = BLOCKS.register(Constants.BlockName.Geolyzer,         Geolyzer::new);
    public static final DeferredBlock<Keyboard>         KEYBOARD         = BLOCKS.register(Constants.BlockName.Keyboard,         Keyboard::new);
    public static final DeferredBlock<Microcontroller>  MICROCONTROLLER  = BLOCKS.register(Constants.BlockName.Microcontroller,  Microcontroller::new);
    public static final DeferredBlock<MotionSensor>     MOTION_SENSOR    = BLOCKS.register(Constants.BlockName.MotionSensor,     MotionSensor::new);
    public static final DeferredBlock<NetSplitter>      NET_SPLITTER     = BLOCKS.register(Constants.BlockName.NetSplitter,      NetSplitter::new);
    public static final DeferredBlock<PowerConverter>   POWER_CONVERTER  = BLOCKS.register(Constants.BlockName.PowerConverter,   PowerConverter::new);
    public static final DeferredBlock<PowerDistributor> POWER_DISTRIBUTOR = BLOCKS.register(Constants.BlockName.PowerDistributor, PowerDistributor::new);
    public static final DeferredBlock<Print>            PRINT            = BLOCKS.register(Constants.BlockName.Print,            Print::new);
    public static final DeferredBlock<Printer>          PRINTER          = BLOCKS.register(Constants.BlockName.Printer,          Printer::new);
    public static final DeferredBlock<Rack>             RACK             = BLOCKS.register(Constants.BlockName.Rack,             Rack::new);
    public static final DeferredBlock<Raid>             RAID             = BLOCKS.register(Constants.BlockName.Raid,             Raid::new);
    public static final DeferredBlock<Redstone>         REDSTONE         = BLOCKS.register(Constants.BlockName.Redstone,         Redstone::new);
    public static final DeferredBlock<Relay>            RELAY            = BLOCKS.register(Constants.BlockName.Relay,            Relay::new);
    public static final DeferredBlock<Robot>            ROBOT            = BLOCKS.register(Constants.BlockName.Robot,            Robot::new);
    public static final DeferredBlock<Transposer>       TRANSPOSER       = BLOCKS.register(Constants.BlockName.Transposer,       Transposer::new);
    public static final DeferredBlock<Waypoint>         WAYPOINT         = BLOCKS.register(Constants.BlockName.Waypoint,         Waypoint::new);

    // -----------------------------------------------------------------------
    // Block items — auto-generated from every registered block
    // -----------------------------------------------------------------------

    public static final DeferredItem<BlockItem> CASE_TIER1_ITEM         = blockItem(CASE_TIER1);
    public static final DeferredItem<BlockItem> CASE_TIER2_ITEM         = blockItem(CASE_TIER2);
    public static final DeferredItem<BlockItem> CASE_TIER3_ITEM         = blockItem(CASE_TIER3);
    public static final DeferredItem<BlockItem> CASE_CREATIVE_ITEM      = blockItem(CASE_CREATIVE);
    public static final DeferredItem<BlockItem> SCREEN_TIER1_ITEM       = blockItem(SCREEN_TIER1);
    public static final DeferredItem<BlockItem> SCREEN_TIER2_ITEM       = blockItem(SCREEN_TIER2);
    public static final DeferredItem<BlockItem> SCREEN_TIER3_ITEM       = blockItem(SCREEN_TIER3);
    public static final DeferredItem<BlockItem> HOLOGRAM_TIER1_ITEM     = blockItem(HOLOGRAM_TIER1);
    public static final DeferredItem<BlockItem> HOLOGRAM_TIER2_ITEM     = blockItem(HOLOGRAM_TIER2);
    public static final DeferredItem<BlockItem> ADAPTER_ITEM            = blockItem(ADAPTER);
    public static final DeferredItem<BlockItem> ASSEMBLER_ITEM          = blockItem(ASSEMBLER);
    public static final DeferredItem<BlockItem> CABLE_ITEM              = blockItem(CABLE);
    public static final DeferredItem<BlockItem> CAPACITOR_ITEM          = blockItem(CAPACITOR);
    public static final DeferredItem<BlockItem> CARPETED_CAPACITOR_ITEM = blockItem(CARPETED_CAPACITOR);
    public static final DeferredItem<BlockItem> CHARGER_ITEM            = blockItem(CHARGER);
    public static final DeferredItem<BlockItem> DISASSEMBLER_ITEM       = blockItem(DISASSEMBLER);
    public static final DeferredItem<BlockItem> DISK_DRIVE_ITEM         = blockItem(DISK_DRIVE);
    public static final DeferredItem<BlockItem> GEOLYZER_ITEM           = blockItem(GEOLYZER);
    public static final DeferredItem<BlockItem> KEYBOARD_ITEM           = blockItem(KEYBOARD);
    public static final DeferredItem<BlockItem> MICROCONTROLLER_ITEM    = blockItem(MICROCONTROLLER);
    public static final DeferredItem<BlockItem> MOTION_SENSOR_ITEM      = blockItem(MOTION_SENSOR);
    public static final DeferredItem<BlockItem> NET_SPLITTER_ITEM       = blockItem(NET_SPLITTER);
    public static final DeferredItem<BlockItem> POWER_CONVERTER_ITEM    = blockItem(POWER_CONVERTER);
    public static final DeferredItem<BlockItem> POWER_DISTRIBUTOR_ITEM  = blockItem(POWER_DISTRIBUTOR);
    public static final DeferredItem<BlockItem> PRINT_ITEM              = blockItem(PRINT);
    public static final DeferredItem<BlockItem> PRINTER_ITEM            = blockItem(PRINTER);
    public static final DeferredItem<BlockItem> RACK_ITEM               = blockItem(RACK);
    public static final DeferredItem<BlockItem> RAID_ITEM               = blockItem(RAID);
    public static final DeferredItem<BlockItem> REDSTONE_ITEM           = blockItem(REDSTONE);
    public static final DeferredItem<BlockItem> RELAY_ITEM              = blockItem(RELAY);
    public static final DeferredItem<BlockItem> ROBOT_ITEM              = blockItem(ROBOT);
    public static final DeferredItem<BlockItem> TRANSPOSER_ITEM         = blockItem(TRANSPOSER);
    public static final DeferredItem<BlockItem> WAYPOINT_ITEM           = blockItem(WAYPOINT);

    // -----------------------------------------------------------------------
    // Standalone items (hardware, upgrades, crafting components, etc.)
    // All registered as base Item stubs; behaviour added in Phase 4+.
    // -----------------------------------------------------------------------

    // CPUs
    public static final DeferredItem<Item> CPU_TIER1 = simpleItem(Constants.ItemName.CPUTier1);
    public static final DeferredItem<Item> CPU_TIER2 = simpleItem(Constants.ItemName.CPUTier2);
    public static final DeferredItem<Item> CPU_TIER3 = simpleItem(Constants.ItemName.CPUTier3);

    // RAM
    public static final DeferredItem<Item> RAM_TIER1 = simpleItem(Constants.ItemName.RAMTier1);
    public static final DeferredItem<Item> RAM_TIER2 = simpleItem(Constants.ItemName.RAMTier2);
    public static final DeferredItem<Item> RAM_TIER3 = simpleItem(Constants.ItemName.RAMTier3);
    public static final DeferredItem<Item> RAM_TIER4 = simpleItem(Constants.ItemName.RAMTier4);
    public static final DeferredItem<Item> RAM_TIER5 = simpleItem(Constants.ItemName.RAMTier5);
    public static final DeferredItem<Item> RAM_TIER6 = simpleItem(Constants.ItemName.RAMTier6);

    // HDDs
    public static final DeferredItem<Item> HDD_TIER1 = simpleItem(Constants.ItemName.HDDTier1);
    public static final DeferredItem<Item> HDD_TIER2 = simpleItem(Constants.ItemName.HDDTier2);
    public static final DeferredItem<Item> HDD_TIER3 = simpleItem(Constants.ItemName.HDDTier3);

    // Graphics cards
    public static final DeferredItem<Item> GRAPHICS_CARD_TIER1 = simpleItem(Constants.ItemName.GraphicsCardTier1);
    public static final DeferredItem<Item> GRAPHICS_CARD_TIER2 = simpleItem(Constants.ItemName.GraphicsCardTier2);
    public static final DeferredItem<Item> GRAPHICS_CARD_TIER3 = simpleItem(Constants.ItemName.GraphicsCardTier3);

    // EEPROM & floppy
    public static final DeferredItem<Item> EEPROM = simpleItem(Constants.ItemName.EEPROM);
    public static final DeferredItem<Item> FLOPPY  = simpleItem(Constants.ItemName.Floppy);
    public static final DeferredItem<Item> OPEN_OS = simpleItem(Constants.ItemName.OpenOS);

    // APUs
    public static final DeferredItem<Item> APU_TIER1    = simpleItem(Constants.ItemName.APUTier1);
    public static final DeferredItem<Item> APU_TIER2    = simpleItem(Constants.ItemName.APUTier2);
    public static final DeferredItem<Item> APU_CREATIVE = simpleItem(Constants.ItemName.APUCreative);

    // Servers
    public static final DeferredItem<Item> SERVER_TIER1    = simpleItem(Constants.ItemName.ServerTier1);
    public static final DeferredItem<Item> SERVER_TIER2    = simpleItem(Constants.ItemName.ServerTier2);
    public static final DeferredItem<Item> SERVER_TIER3    = simpleItem(Constants.ItemName.ServerTier3);
    public static final DeferredItem<Item> SERVER_CREATIVE = simpleItem(Constants.ItemName.ServerCreative);

    // Network cards
    public static final DeferredItem<Item> NETWORK_CARD             = simpleItem(Constants.ItemName.NetworkCard);
    public static final DeferredItem<Item> WIRELESS_CARD_TIER1      = simpleItem(Constants.ItemName.WirelessNetworkCardTier1);
    public static final DeferredItem<Item> WIRELESS_CARD_TIER2      = simpleItem(Constants.ItemName.WirelessNetworkCardTier2);
    public static final DeferredItem<Item> LINKED_CARD              = simpleItem(Constants.ItemName.LinkedCard);

    // Data / redstone / component bus cards
    public static final DeferredItem<Item> DATA_CARD_TIER1      = simpleItem(Constants.ItemName.DataCardTier1);
    public static final DeferredItem<Item> DATA_CARD_TIER2      = simpleItem(Constants.ItemName.DataCardTier2);
    public static final DeferredItem<Item> DATA_CARD_TIER3      = simpleItem(Constants.ItemName.DataCardTier3);
    public static final DeferredItem<Item> REDSTONE_CARD_TIER1  = simpleItem(Constants.ItemName.RedstoneCardTier1);
    public static final DeferredItem<Item> REDSTONE_CARD_TIER2  = simpleItem(Constants.ItemName.RedstoneCardTier2);
    public static final DeferredItem<Item> INTERNET_CARD        = simpleItem(Constants.ItemName.InternetCard);
    public static final DeferredItem<Item> ABSTRACT_BUS_CARD    = simpleItem(Constants.ItemName.AbstractBusCard);
    public static final DeferredItem<Item> DEBUG_CARD           = simpleItem(Constants.ItemName.DebugCard);
    public static final DeferredItem<Item> WORLD_SENSOR_CARD    = simpleItem(Constants.ItemName.WorldSensorCard);
    public static final DeferredItem<Item> COMPONENT_BUS_TIER1  = simpleItem(Constants.ItemName.ComponentBusTier1);
    public static final DeferredItem<Item> COMPONENT_BUS_TIER2  = simpleItem(Constants.ItemName.ComponentBusTier2);
    public static final DeferredItem<Item> COMPONENT_BUS_TIER3  = simpleItem(Constants.ItemName.ComponentBusTier3);
    public static final DeferredItem<Item> COMPONENT_BUS_CREATIVE = simpleItem(Constants.ItemName.ComponentBusCreative);

    // Card / upgrade containers
    public static final DeferredItem<Item> CARD_CONTAINER_TIER1    = simpleItem(Constants.ItemName.CardContainerTier1);
    public static final DeferredItem<Item> CARD_CONTAINER_TIER2    = simpleItem(Constants.ItemName.CardContainerTier2);
    public static final DeferredItem<Item> CARD_CONTAINER_TIER3    = simpleItem(Constants.ItemName.CardContainerTier3);
    public static final DeferredItem<Item> UPGRADE_CONTAINER_TIER1 = simpleItem(Constants.ItemName.UpgradeContainerTier1);
    public static final DeferredItem<Item> UPGRADE_CONTAINER_TIER2 = simpleItem(Constants.ItemName.UpgradeContainerTier2);
    public static final DeferredItem<Item> UPGRADE_CONTAINER_TIER3 = simpleItem(Constants.ItemName.UpgradeContainerTier3);

    // Robot/drone cases
    public static final DeferredItem<Item> DRONE              = simpleItem(Constants.ItemName.Drone);
    public static final DeferredItem<Item> DRONE_CASE_TIER1   = simpleItem(Constants.ItemName.DroneCaseTier1);
    public static final DeferredItem<Item> DRONE_CASE_TIER2   = simpleItem(Constants.ItemName.DroneCaseTier2);
    public static final DeferredItem<Item> DRONE_CASE_CREATIVE = simpleItem(Constants.ItemName.DroneCaseCreative);

    public static final DeferredItem<Item> MICROCONTROLLER_CASE_TIER1    = simpleItem(Constants.ItemName.MicrocontrollerCaseTier1);
    public static final DeferredItem<Item> MICROCONTROLLER_CASE_TIER2    = simpleItem(Constants.ItemName.MicrocontrollerCaseTier2);
    public static final DeferredItem<Item> MICROCONTROLLER_CASE_CREATIVE = simpleItem(Constants.ItemName.MicrocontrollerCaseCreative);

    public static final DeferredItem<Item> TABLET            = simpleItem(Constants.ItemName.Tablet);
    public static final DeferredItem<Item> TABLET_CASE_TIER1    = simpleItem(Constants.ItemName.TabletCaseTier1);
    public static final DeferredItem<Item> TABLET_CASE_TIER2    = simpleItem(Constants.ItemName.TabletCaseTier2);
    public static final DeferredItem<Item> TABLET_CASE_CREATIVE = simpleItem(Constants.ItemName.TabletCaseCreative);

    // Upgrades
    public static final DeferredItem<Item> BATTERY_UPGRADE_TIER1  = simpleItem(Constants.ItemName.BatteryUpgradeTier1);
    public static final DeferredItem<Item> BATTERY_UPGRADE_TIER2  = simpleItem(Constants.ItemName.BatteryUpgradeTier2);
    public static final DeferredItem<Item> BATTERY_UPGRADE_TIER3  = simpleItem(Constants.ItemName.BatteryUpgradeTier3);
    public static final DeferredItem<Item> ANGEL_UPGRADE           = simpleItem(Constants.ItemName.AngelUpgrade);
    public static final DeferredItem<Item> CHUNKLOADER_UPGRADE     = simpleItem(Constants.ItemName.ChunkloaderUpgrade);
    public static final DeferredItem<Item> CRAFTING_UPGRADE        = simpleItem(Constants.ItemName.CraftingUpgrade);
    public static final DeferredItem<Item> DATABASE_UPGRADE_TIER1  = simpleItem(Constants.ItemName.DatabaseUpgradeTier1);
    public static final DeferredItem<Item> DATABASE_UPGRADE_TIER2  = simpleItem(Constants.ItemName.DatabaseUpgradeTier2);
    public static final DeferredItem<Item> DATABASE_UPGRADE_TIER3  = simpleItem(Constants.ItemName.DatabaseUpgradeTier3);
    public static final DeferredItem<Item> EXPERIENCE_UPGRADE      = simpleItem(Constants.ItemName.ExperienceUpgrade);
    public static final DeferredItem<Item> GENERATOR_UPGRADE       = simpleItem(Constants.ItemName.GeneratorUpgrade);
    public static final DeferredItem<Item> HOVER_UPGRADE_TIER1     = simpleItem(Constants.ItemName.HoverUpgradeTier1);
    public static final DeferredItem<Item> HOVER_UPGRADE_TIER2     = simpleItem(Constants.ItemName.HoverUpgradeTier2);
    public static final DeferredItem<Item> INVENTORY_CONTROLLER_UPGRADE = simpleItem(Constants.ItemName.InventoryControllerUpgrade);
    public static final DeferredItem<Item> INVENTORY_UPGRADE       = simpleItem(Constants.ItemName.InventoryUpgrade);
    public static final DeferredItem<Item> LEASH_UPGRADE           = simpleItem(Constants.ItemName.LeashUpgrade);
    public static final DeferredItem<Item> NAVIGATION_UPGRADE      = simpleItem(Constants.ItemName.NavigationUpgrade);
    public static final DeferredItem<Item> PISTON_UPGRADE          = simpleItem(Constants.ItemName.PistonUpgrade);
    public static final DeferredItem<Item> STICKY_PISTON_UPGRADE   = simpleItem(Constants.ItemName.StickyPistonUpgrade);
    public static final DeferredItem<Item> SIGN_UPGRADE            = simpleItem(Constants.ItemName.SignUpgrade);
    public static final DeferredItem<Item> SOLAR_GENERATOR_UPGRADE = simpleItem(Constants.ItemName.SolarGeneratorUpgrade);
    public static final DeferredItem<Item> TANK_CONTROLLER_UPGRADE = simpleItem(Constants.ItemName.TankControllerUpgrade);
    public static final DeferredItem<Item> TANK_UPGRADE            = simpleItem(Constants.ItemName.TankUpgrade);
    public static final DeferredItem<Item> TRACTOR_BEAM_UPGRADE    = simpleItem(Constants.ItemName.TractorBeamUpgrade);
    public static final DeferredItem<Item> TRADING_UPGRADE         = simpleItem(Constants.ItemName.TradingUpgrade);

    // Tools / misc
    public static final DeferredItem<Item> ANALYZER      = simpleItem(Constants.ItemName.Analyzer);
    public static final DeferredItem<Item> DEBUGGER      = simpleItem(Constants.ItemName.Debugger);
    public static final DeferredItem<Item> HOVER_BOOTS   = simpleItem(Constants.ItemName.HoverBoots);
    public static final DeferredItem<Item> MANUAL        = simpleItem(Constants.ItemName.Manual);
    public static final DeferredItem<Item> MFU           = simpleItem(Constants.ItemName.MFU);
    public static final DeferredItem<Item> NANOMACHINES  = simpleItem(Constants.ItemName.Nanomachines);
    public static final DeferredItem<Item> TERMINAL      = simpleItem(Constants.ItemName.Terminal);
    public static final DeferredItem<Item> TERMINAL_SERVER = simpleItem(Constants.ItemName.TerminalServer);
    public static final DeferredItem<Item> TEXTURE_PICKER = simpleItem(Constants.ItemName.TexturePicker);
    public static final DeferredItem<Item> WRENCH        = simpleItem(Constants.ItemName.Wrench);

    // Crafting materials / intermediates
    public static final DeferredItem<Item> ALU                  = simpleItem(Constants.ItemName.Alu);
    public static final DeferredItem<Item> CARD                 = simpleItem(Constants.ItemName.Card);
    public static final DeferredItem<Item> CHAMELIUM            = simpleItem(Constants.ItemName.Chamelium);
    public static final DeferredItem<Item> CHIP_TIER1           = simpleItem(Constants.ItemName.ChipTier1);
    public static final DeferredItem<Item> CHIP_TIER2           = simpleItem(Constants.ItemName.ChipTier2);
    public static final DeferredItem<Item> CHIP_TIER3           = simpleItem(Constants.ItemName.ChipTier3);
    public static final DeferredItem<Item> CHIP_DIAMOND         = simpleItem(Constants.ItemName.DiamondChip);
    public static final DeferredItem<Item> CIRCUIT_BOARD        = simpleItem(Constants.ItemName.CircuitBoard);
    public static final DeferredItem<Item> CONTROL_UNIT         = simpleItem(Constants.ItemName.ControlUnit);
    public static final DeferredItem<Item> CUTTING_WIRE         = simpleItem(Constants.ItemName.CuttingWire);
    public static final DeferredItem<Item> DISK                 = simpleItem(Constants.ItemName.Disk);
    public static final DeferredItem<Item> DISK_DRIVE_MOUNTABLE = simpleItem(Constants.ItemName.DiskDriveMountable);
    public static final DeferredItem<Item> INK_CARTRIDGE_EMPTY  = simpleItem(Constants.ItemName.InkCartridgeEmpty);
    public static final DeferredItem<Item> INK_CARTRIDGE        = simpleItem(Constants.ItemName.InkCartridge);
    public static final DeferredItem<Item> INTERWEB             = simpleItem(Constants.ItemName.Interweb);
    public static final DeferredItem<Item> LUA_BIOS             = simpleItem(Constants.ItemName.LuaBios);
    public static final DeferredItem<Item> PRINTED_CIRCUIT_BOARD = simpleItem(Constants.ItemName.PrintedCircuitBoard);
    public static final DeferredItem<Item> RAW_CIRCUIT_BOARD    = simpleItem(Constants.ItemName.RawCircuitBoard);
    public static final DeferredItem<Item> TRANSISTOR           = simpleItem(Constants.ItemName.Transistor);

    // Misc items
    public static final DeferredItem<Item> ACID         = simpleItem(Constants.ItemName.Acid);
    public static final DeferredItem<Item> ARROW_KEYS   = simpleItem(Constants.ItemName.ArrowKeys);
    public static final DeferredItem<Item> BUTTON_GROUP = simpleItem(Constants.ItemName.ButtonGroup);
    public static final DeferredItem<Item> NUM_PAD      = simpleItem(Constants.ItemName.NumPad);
    public static final DeferredItem<Item> PRESENT      = simpleItem(Constants.ItemName.Present);

    // -----------------------------------------------------------------------
    // Block entity types (Phase 3 — add more as block entities are ported)
    //
    // One type covers all tier variants for Case and Screen so the legacy
    // single-class design is preserved.  Explicit lambdas avoid constructor
    // ambiguity that arises with multiple constructors and method references.
    // -----------------------------------------------------------------------

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CaseBlockEntity>> CASE_BE =
        BLOCK_ENTITY_TYPES.register("case",
            () -> new BlockEntityType<>(
                (pos, state) -> new CaseBlockEntity(pos, state),
                CASE_TIER1.get(), CASE_TIER2.get(), CASE_TIER3.get(), CASE_CREATIVE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScreenBlockEntity>> SCREEN_BE =
        BLOCK_ENTITY_TYPES.register("screen",
            () -> new BlockEntityType<>(
                (pos, state) -> new ScreenBlockEntity(pos, state),
                SCREEN_TIER1.get(), SCREEN_TIER2.get(), SCREEN_TIER3.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyboardBlockEntity>> KEYBOARD_BE =
        BLOCK_ENTITY_TYPES.register(Constants.BlockName.Keyboard,
            () -> new BlockEntityType<>(
                (pos, state) -> new KeyboardBlockEntity(pos, state),
                KEYBOARD.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DiskDriveBlockEntity>> DISK_DRIVE_BE =
        BLOCK_ENTITY_TYPES.register(Constants.BlockName.DiskDrive,
            () -> new BlockEntityType<>(
                (pos, state) -> new DiskDriveBlockEntity(pos, state),
                DISK_DRIVE.get()));

    // -----------------------------------------------------------------------
    // Creative tab
    // -----------------------------------------------------------------------

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OC_TAB =
        CREATIVE_MODE_TABS.register("opencomputers", () ->
            CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.opencomputers"))
                .icon(() -> new net.minecraft.world.item.ItemStack(CASE_TIER1.get()))
                .displayItems((params, output) -> {
                    // Phase 6: populate tab contents via BuildCreativeModeTabContentsEvent
                })
                .build()
        );

    // -----------------------------------------------------------------------
    // Wiring
    // -----------------------------------------------------------------------

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static <B extends Block> DeferredItem<BlockItem> blockItem(DeferredBlock<B> block) {
        return ITEMS.register(block.getId().getPath(),
            () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static DeferredItem<Item> simpleItem(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties()));
    }

    private Registries() {}
}
