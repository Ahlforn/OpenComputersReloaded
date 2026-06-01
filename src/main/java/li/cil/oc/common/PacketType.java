package li.cil.oc.common;

/**
 * Enumerates every packet type used by OC's custom networking layer.
 *
 * <p>The ordinal of each constant is written as a single byte at the start of
 * every {@link OcPacketPayload} data buffer, making it the packet's type tag.
 * Do not reorder or remove entries — that would break compatibility with
 * existing worlds.  Append new entries before {@link #EndOfList}.</p>
 */
public enum PacketType {

    // ---------------------------------------------------------------------- //
    // Server → Client
    // ---------------------------------------------------------------------- //

    AdapterState,
    Analyze,
    ChargerState,
    ClientLog,
    ColorChange,
    ComputerState,
    ComputerUserList,
    ContainerUpdate,
    DisassemblerActiveChange,
    FileSystemActivity,
    FloppyChange,
    HologramArea,
    HologramClear,
    HologramColor,
    HologramPowerChange,
    HologramRotation,
    HologramRotationSpeed,
    HologramScale,
    HologramTranslation,
    HologramValues,
    LootDisk,
    CyclingDisk,
    NanomachinesConfiguration,
    NanomachinesInputs,
    NanomachinesPower,
    NetSplitterState,
    NetworkActivity,
    ParticleEffect,
    PetVisibility,       // bidirectional
    PowerState,
    PrinterState,
    RackInventory,
    RackMountableData,
    RaidStateChange,
    RedstoneState,
    RobotAnimateSwing,
    RobotAnimateTurn,
    RobotAssemblingState,
    RobotInventoryChange,
    RobotLightChange,
    RobotMove,
    RobotNameChange,
    RobotSelectedSlotChange,
    RotatableState,
    SwitchActivity,
    TextBufferInit,                         // bidirectional
    TextBufferMulti,
    TextBufferRamInit,
    TextBufferBitBlt,
    TextBufferRamDestroy,
    TextBufferMultiColorChange,
    TextBufferMultiCopy,
    TextBufferMultiDepthChange,
    TextBufferMultiFill,
    TextBufferMultiPaletteChange,
    TextBufferMultiResolutionChange,
    TextBufferMultiViewportResolutionChange,
    TextBufferMultiMaxResolutionChange,
    TextBufferMultiSet,
    TextBufferMultiRawSetText,
    TextBufferMultiRawSetBackground,
    TextBufferMultiRawSetForeground,
    TextBufferPowerChange,
    ScreenTouchMode,
    SoundEffect,
    Sound,
    SoundPattern,
    TransposerActivity,
    WaypointLabel,                          // bidirectional

    // ---------------------------------------------------------------------- //
    // Client → Server
    // ---------------------------------------------------------------------- //

    ComputerPower,
    CopyToAnalyzer,
    DriveLock,
    DriveMode,
    DronePower,
    KeyDown,
    KeyUp,
    Clipboard,
    MachineItemStateRequest,
    MachineItemStateResponse,
    MouseClickOrDrag,
    MouseScroll,
    MouseUp,
    MultiPartPlace,
    RackMountableMapping,
    RackRelayState,
    RobotAssemblerStart,
    RobotStateRequest,
    ServerPower,

    EndOfList
}
