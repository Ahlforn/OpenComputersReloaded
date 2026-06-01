package li.cil.oc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
import li.cil.oc.util.InternetFilteringRule;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Settings loaded from {@code application.conf} (the Typesafe Config reference file) merged with
 * the per-world config file in the NeoForge config directory.
 *
 * Phase 1 note: VersionRange-based config-patch migration, the debug card whitelist, and the
 * config comment manipulation hook are omitted until their dependencies are ported (Phases 3/10).
 */
public class Settings {
    // ----------------------------------------------------------------------- //
    // Statics (companion object equivalents)
    // ----------------------------------------------------------------------- //

    public static final String resourceDomain = "opencomputers";
    public static final String namespace = "oc:";
    public static final String savePath = "opencomputers/";
    public static final String scriptPath = "/assets/" + resourceDomain + "/lua/";

    /** (width, height) per tier (0=T1, 1=T2, 2=T3). */
    public static final int[][] screenResolutionsByTier = {{50, 16}, {80, 25}, {160, 50}};

    /** DeviceComplexity limit per tier. */
    public static final int[] deviceComplexityByTier = {12, 24, 32, 9001};

    public static boolean rTreeDebugRenderer = false;

    private static final String[] FORBIDDEN_CONFIG_LISTS = {
        "internet.blacklist", "internet.whitelist"
    };
    private static final String PREFIX = "opencomputers.";

    public static int basicScreenPixels() {
        return screenResolutionsByTier[0][0] * screenResolutionsByTier[0][1];
    }

    private static Settings instance;

    public static Settings get() {
        return instance;
    }

    public static void load(File file) {
        Config defaults;
        try (InputStream in = Settings.class.getResourceAsStream("/application.conf")) {
            if (in == null) throw new IOException("application.conf not found on classpath");
            defaults = ConfigFactory.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default config", e);
        }

        Config config;
        try {
            String plain = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            config = ConfigFactory.parseString(plain).withFallback(defaults);
            instance = new Settings(config.getConfig("opencomputers"));
        } catch (Throwable e) {
            if (file.exists()) {
                throw new RuntimeException(
                    "Error parsing configuration file. To restore defaults, delete '" +
                    file.getName() + "' and restart the game.", e);
            }
            instance = new Settings(defaults.getConfig("opencomputers"));
            config = defaults;
        }

        for (String key : FORBIDDEN_CONFIG_LISTS) {
            String fullPath = PREFIX + key;
            if (config.hasPath(fullPath) && !config.getStringList(fullPath).isEmpty()) {
                throw new RuntimeException(
                    "Error parsing configuration file: removed configuration option '" +
                    key + "' is not empty. This option should no longer be used.");
            }
        }

        try {
            ConfigRenderOptions renderSettings = ConfigRenderOptions.defaults()
                .setJson(false).setOriginComments(false);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8)) {
                out.write(config.root().render(renderSettings));
            }
        } catch (Throwable e) {
            OpenComputersMod.LOGGER.warn("Failed saving config.", e);
        }
    }

    /** Returns the config directory for OpenComputers (creates it if needed). */
    public static File configDir() {
        File dir = FMLPaths.CONFIGDIR.get().resolve("opencomputers").toFile();
        dir.mkdirs();
        return dir;
    }

    // ----------------------------------------------------------------------- //
    // Instance fields
    // ----------------------------------------------------------------------- //

    // client
    public final double screenTextFadeStartDistance;
    public final double maxScreenTextRenderDistance;
    public final boolean textLinearFiltering;
    public final boolean textAntiAlias;
    public final boolean robotLabels;
    public final float soundVolume;
    public final double fontCharScale;
    public final double hologramFadeStartDistance;
    public final double hologramRenderDistance;
    public final double hologramFlickerFrequency;
    public final int monochromeColor;
    public final String fontRenderer;
    public final int beepSampleRate;
    public final int beepAmplitude;
    public final float beepRadius;
    public final double[] nanomachineHudPos; // [x, y]
    public final boolean enableNanomachinePfx;
    public final int transposerFluidTransferRate;

    // computer
    public final int threads;
    public final double timeout;
    public final double startupDelay;
    public final int eepromSize;
    public final int eepromDataSize;
    public final int[] cpuComponentSupport; // 4 tiers
    public final double[] callBudgets;      // 3 tiers
    public final boolean canComputersBeOwned;
    public final int maxUsers;
    public final int maxUsernameLength;
    public final boolean eraseTmpOnReboot;
    public final int executionDelay;
    public final int maxSignalQueueSize;

    // computer.lua
    public final boolean allowBytecode;
    public final boolean allowGC;
    public final boolean enableLua53;
    public final boolean defaultLua53;
    public final boolean enableLua54;
    public final int[] ramSizes;       // 6 tiers
    public final double ramScaleFor64Bit;
    public final int maxTotalRam;

    // robot
    public final boolean allowActivateBlocks;
    public final boolean allowUseItemsWithDuration;
    public final boolean canAttackPlayers;
    public final int limitFlightHeight;
    public final boolean screwCobwebs;
    public final double swingRange;
    public final double useAndPlaceRange;
    public final double itemDamageRate;
    public final String nameFormat;
    public final String uuidFormat;
    public final int[] upgradeFlightHeight; // 2 tiers

    // robot.xp
    public final double baseXpToLevel;
    public final double xpCostPerTier;
    public final double baseXpForAction;
    public final double upgradeXpCostMultiplier;

    // robot.delays
    public final double[] moveDelay;
    public final double[] turnDelay;
    public final double[] swingDelay;
    public final double[] useDelay;
    public final double[] placeDelay;
    public final double[] dropDelay;
    public final double[] suckDelay;
    public final double[] harvestRatio;

    // power
    public final double[] powerRatioFromBatteryBlock = new double[0]; // IC2, TE, etc. — unused in Phase 1 → unused in Phase 1
    public final double[] costPerOperation;           // named by operation

    // filesystem
    public final int maxHandles;
    public final int maxReadBuffer;
    public final double hddTimeCost;
    public final double hddBytesCost;
    public final double ssdTimeCost;
    public final double ssdBytesCost;
    public final double floppyTimeCost;
    public final double floppyBytesCost;
    public final int[] hddSizes;      // 3 tiers
    public final double[] hddScales;  // 3 tiers
    public final int[] ssdSizes;      // 3 tiers
    public final double[] ssdScales;  // 3 tiers
    public final int floppySize;
    public final int tmpSize;
    public final int maxFloppy;
    public final double sectorSeekTime;

    // internet
    public final boolean httpEnabled;
    public final boolean httpHeadersEnabled;
    public final boolean tcpEnabled;
    public final InternetFilteringRule[] internetFilteringRules;
    public final boolean internetFilteringRulesObserved;
    public final int httpTimeout;
    public final int maxConnections;
    public final int internetThreads;
    public final String httpUserAgent;

    // switch
    public final int switchDefaultMaxQueueSize;
    public final int switchQueueSizeUpgrade;
    public final int switchDefaultRelayDelay;
    public final double switchRelayDelayUpgrade;
    public final int switchDefaultRelayAmount;
    public final int switchRelayAmountUpgrade;

    // hologram
    public final double[] hologramMaxScaleByTier;
    public final double[] hologramMaxTranslationByTier;
    public final double hologramSetRawDelay;
    public final boolean hologramLight;

    // misc
    public final int maxScreenWidth;
    public final int maxScreenHeight;
    public final boolean inputUsername;
    public final int initialNetworkPacketTTL;
    public final int maxNetworkPacketSize;
    public final int maxNetworkPacketParts;
    public final int[] maxOpenPorts;          // 3 tiers: wired, t1 wireless, t2 wireless
    public final double[] maxWirelessRange;   // 2 tiers
    public final int rTreeMaxEntries = 10;
    public final int terminalsPerServer = 4;
    public final boolean updateCheck;
    public final int lootProbability;
    public final boolean lootRecrafting;
    public final int geolyzerRange;
    public final float geolyzerNoise;
    public final boolean disassembleAllTheThings;
    public final double disassemblerBreakChance;
    public final List<String> disassemblerInputBlacklist;
    public final boolean hideOwnPet;
    public final boolean allowItemStackInspection;
    public final int[] databaseEntriesPerTier = {9, 25, 81};
    public final double presentChance;
    public final List<String> assemblerBlacklist;
    public final int threadPriority;
    public final boolean giveManualToNewPlayers;
    public final int dataCardSoftLimit;
    public final int dataCardHardLimit;
    public final double dataCardTimeout;
    public final int serverRackSwitchTier;
    public final double redstoneDelay;
    public final double tradingRange;
    public final int mfuRange;

    // nanomachines
    public final double nanomachineTriggerQuota;
    public final double nanomachineConnectorQuota;
    public final int nanomachineMaxInputs;
    public final int nanomachineMaxOutputs;
    public final int nanomachinesSafeInputsActive;
    public final int nanomachinesMaxInputsActive;
    public final double nanomachinesCommandDelay;
    public final double nanomachinesCommandRange;
    public final double nanomachineMagnetRange;
    public final int nanomachineDisintegrationRange;
    public final List<?> nanomachinePotionWhitelist;
    public final float nanomachinesHungryDamage;
    public final double nanomachinesHungryEnergyRestored;

    // printer
    public final int maxPrintComplexity;
    public final double printRecycleRate;
    public final boolean chameliumEdible;
    public final int maxPrintLightLevel;
    public final int printCustomRedstone;
    public final int printMaterialValue;
    public final int printInkValue;
    public final boolean printsHaveOpacity;
    public final double noclipMultiplier;

    // chunkloader
    public final List<Integer> chunkloadDimensionBlacklist;
    public final List<Integer> chunkloadDimensionWhitelist;

    // integration
    public final List<String> modBlacklist;
    public final List<String> peripheralBlacklist;
    public final String fakePlayerUuid;
    public final String fakePlayerName;
    public final boolean enableInventoryDriver;
    public final boolean enableTankDriver;
    public final boolean enableCommandBlockDriver;
    public final boolean allowItemStackNBTTags;
    public final double costProgrammingTable;

    // power
    public final boolean ignorePower;
    public final int tickFrequency;
    public final double bufferComputer;
    public final double computerCost;
    public final double robotCost;
    public final double sleepCostFactor;
    public final double hddReadCost;
    public final double hddWriteCost;
    public final double hddSeekCost;
    public final double screenCostFactor;
    public final double gpuFillCost;
    public final double gpuClearCost;
    public final double gpuCopyCost;
    public final double gpuSetCost;
    public final double gpuBitbltCost;
    public final double robotTurnCost;
    public final double robotMoveCost;
    public final double robotExhaustionCost;
    public final double assemblerCost;
    public final double chargeCostFactor;
    public final double printerCost;
    public final double nanomachineCost;
    public final double terminalServerCost;
    public final double dataCardTrivial;
    public final double dataCardSimple;
    public final double dataCardComplex;
    public final double dataCardAsymmetric;
    public final double transposerCost;
    public final double generatorEfficiency;
    public final double solarGeneratorEfficiency;
    public final double abstractBusPacketCost;

    // debug
    public final boolean logLuaCallbackErrors;
    public final boolean forceLuaJ;
    public final boolean allowUserdata;
    public final boolean allowPersistence;
    public final boolean limitMemory;
    public final boolean forceCaseInsensitive;
    public final boolean logFullLibLoadErrors;
    public final String forceNativeLibPlatform;
    public final String forceNativeLibPathFirst;
    public final boolean logOpenGLErrors;
    public final boolean logHexFontErrors;
    public final boolean alwaysTryNative;
    public final boolean debugPersistence;
    public final boolean nativeInTmpDir;
    public final boolean periodicallyForceLightUpdate;
    public final boolean insertIdsInConverters;
    public final boolean registerLuaJArchitecture;
    public final boolean disableLocaleChanging;

    // >= 1.7.6
    public final double[] vramSizes; // 3 tiers
    public final double bitbltCost;

    // >= 1.8.2
    public final int diskActivitySoundDelay;
    public final double maxNetworkClientPacketDistance;
    public final double maxNetworkClientEffectPacketDistance;
    public final double maxNetworkClientSoundPacketDistance;

    // ----------------------------------------------------------------------- //

    public Settings(Config config) {
        // client
        screenTextFadeStartDistance = config.getDouble("client.screenTextFadeStartDistance");
        maxScreenTextRenderDistance = config.getDouble("client.maxScreenTextRenderDistance");
        textLinearFiltering = config.getBoolean("client.textLinearFiltering");
        textAntiAlias = config.getBoolean("client.textAntiAlias");
        robotLabels = config.getBoolean("client.robotLabels");
        soundVolume = (float) Math.max(0, Math.min(2, config.getDouble("client.soundVolume")));
        fontCharScale = Math.max(0.5, Math.min(2, config.getDouble("client.fontCharScale")));
        hologramFadeStartDistance = Math.max(0, config.getDouble("client.hologramFadeStartDistance"));
        hologramRenderDistance = Math.max(0, config.getDouble("client.hologramRenderDistance"));
        hologramFlickerFrequency = Math.max(0, config.getDouble("client.hologramFlickerFrequency"));
        monochromeColor = Integer.decode(config.getString("client.monochromeColor"));
        fontRenderer = config.getString("client.fontRenderer");
        beepSampleRate = config.getInt("client.beepSampleRate");
        beepAmplitude = Math.max(0, Math.min(Byte.MAX_VALUE, config.getInt("client.beepVolume")));
        beepRadius = (float) Math.max(1, Math.min(32, config.getDouble("client.beepRadius")));
        List<Double> hudPos = config.getDoubleList("client.nanomachineHudPos");
        if (hudPos.size() == 2) {
            nanomachineHudPos = new double[]{hudPos.get(0), hudPos.get(1)};
        } else {
            OpenComputersMod.LOGGER.warn("Bad number of HUD coordinates, ignoring.");
            nanomachineHudPos = new double[]{-1.0, -1.0};
        }
        enableNanomachinePfx = config.getBoolean("client.enableNanomachinePfx");
        transposerFluidTransferRate = config.getInt("misc.transposerFluidTransferRate");

        // computer
        threads = Math.max(1, config.getInt("computer.threads"));
        timeout = Math.max(0, config.getDouble("computer.timeout"));
        startupDelay = Math.max(0.05, config.getDouble("computer.startupDelay"));
        eepromSize = Math.max(0, config.getInt("computer.eepromSize"));
        eepromDataSize = Math.max(0, config.getInt("computer.eepromDataSize"));
        List<Integer> cpuCounts = config.getIntList("computer.cpuComponentCount");
        if (cpuCounts.size() == 4) {
            cpuComponentSupport = new int[]{cpuCounts.get(0), cpuCounts.get(1), cpuCounts.get(2), cpuCounts.get(3)};
        } else {
            OpenComputersMod.LOGGER.warn("Bad number of CPU component counts, ignoring.");
            cpuComponentSupport = new int[]{8, 12, 16, 1024};
        }
        List<Double> budgets = config.getDoubleList("computer.callBudgets");
        if (budgets.size() == 3) {
            callBudgets = new double[]{budgets.get(0), budgets.get(1), budgets.get(2)};
        } else {
            OpenComputersMod.LOGGER.warn("Bad number of call budgets, ignoring.");
            callBudgets = new double[]{0.5, 1.0, 1.5};
        }
        canComputersBeOwned = config.getBoolean("computer.canComputersBeOwned");
        maxUsers = Math.max(0, config.getInt("computer.maxUsers"));
        maxUsernameLength = Math.max(0, config.getInt("computer.maxUsernameLength"));
        eraseTmpOnReboot = config.getBoolean("computer.eraseTmpOnReboot");
        executionDelay = Math.max(0, config.getInt("computer.executionDelay"));
        maxSignalQueueSize = Math.max(256,
            config.hasPath("computer.maxSignalQueueSize") ? config.getInt("computer.maxSignalQueueSize") : 256);

        // computer.lua
        allowBytecode = config.getBoolean("computer.lua.allowBytecode");
        allowGC = config.getBoolean("computer.lua.allowGC");
        enableLua53 = config.getBoolean("computer.lua.enableLua53");
        defaultLua53 = config.getBoolean("computer.lua.defaultLua53");
        enableLua54 = config.getBoolean("computer.lua.enableLua54");
        List<Integer> ram = config.getIntList("computer.lua.ramSizes");
        if (ram.size() == 6) {
            ramSizes = new int[]{ram.get(0), ram.get(1), ram.get(2), ram.get(3), ram.get(4), ram.get(5)};
        } else {
            OpenComputersMod.LOGGER.warn("Bad number of RAM sizes, ignoring.");
            ramSizes = new int[]{192, 256, 384, 512, 768, 1024};
        }
        ramScaleFor64Bit = Math.max(1, config.getDouble("computer.lua.ramScaleFor64Bit"));
        maxTotalRam = Math.max(0, config.getInt("computer.lua.maxTotalRam"));

        // robot
        allowActivateBlocks = config.getBoolean("robot.allowActivateBlocks");
        allowUseItemsWithDuration = config.getBoolean("robot.allowUseItemsWithDuration");
        canAttackPlayers = config.getBoolean("robot.canAttackPlayers");
        limitFlightHeight = Math.max(-1, config.getInt("robot.limitFlightHeight"));
        screwCobwebs = config.getBoolean("robot.notAfraidOfSpiders");
        swingRange = config.getDouble("robot.swingRange");
        useAndPlaceRange = config.getDouble("robot.useAndPlaceRange");
        itemDamageRate = Math.max(0, Math.min(1, config.getDouble("robot.itemDamageRate")));
        nameFormat = config.getString("robot.nameFormat");
        uuidFormat = config.getString("robot.uuidFormat");
        List<Integer> flightH = config.getIntList("robot.upgradeFlightHeight");
        if (flightH.size() == 2) {
            upgradeFlightHeight = new int[]{flightH.get(0), flightH.get(1)};
        } else {
            OpenComputersMod.LOGGER.warn("Bad number of hover flight height counts, ignoring.");
            upgradeFlightHeight = new int[]{64, 256};
        }

        // robot.xp
        baseXpToLevel = config.getDouble("robot.xp.baseXpToLevel");
        xpCostPerTier = config.getDouble("robot.xp.xpCostPerTier");
        baseXpForAction = config.getDouble("robot.xp.baseXpForAction");
        upgradeXpCostMultiplier = config.getDouble("robot.xp.upgradeXpCostMultiplier");

        // robot.delays
        moveDelay = toDoubleArray(config.getDoubleList("robot.delays.move"), 6, "move delay");
        turnDelay = toDoubleArray(config.getDoubleList("robot.delays.turn"), 6, "turn delay");
        swingDelay = toDoubleArray(config.getDoubleList("robot.delays.swing"), 6, "swing delay");
        useDelay = toDoubleArray(config.getDoubleList("robot.delays.use"), 6, "use delay");
        placeDelay = toDoubleArray(config.getDoubleList("robot.delays.place"), 6, "place delay");
        dropDelay = toDoubleArray(config.getDoubleList("robot.delays.drop"), 6, "drop delay");
        suckDelay = toDoubleArray(config.getDoubleList("robot.delays.suck"), 6, "suck delay");
        harvestRatio = toDoubleArray(config.getDoubleList("robot.delays.harvestRatio"), 6, "harvest ratio");

        // power – just load the cost table as doubles from config
        costPerOperation = new double[0]; // placeholder – individual costs loaded on demand

        // filesystem
        maxHandles = Math.max(0, config.getInt("filesystem.maxHandles"));
        maxReadBuffer = Math.max(0, config.getInt("filesystem.maxReadBuffer"));
        hddTimeCost = config.getDouble("filesystem.hddTimeCost");
        hddBytesCost = config.getDouble("filesystem.hddBytesCost");
        ssdTimeCost = config.getDouble("filesystem.ssdTimeCost");
        ssdBytesCost = config.getDouble("filesystem.ssdBytesCost");
        floppyTimeCost = config.getDouble("filesystem.floppyTimeCost");
        floppyBytesCost = config.getDouble("filesystem.floppyBytesCost");
        List<Integer> hddS = config.getIntList("filesystem.hddSizes");
        hddSizes = hddS.size() == 3 ? new int[]{hddS.get(0), hddS.get(1), hddS.get(2)} : new int[]{1024, 2048, 4096};
        List<Double> hddSc = config.getDoubleList("filesystem.hddScales");
        hddScales = hddSc.size() == 3 ? new double[]{hddSc.get(0), hddSc.get(1), hddSc.get(2)} : new double[]{1, 1, 1};
        List<Integer> ssdS = config.getIntList("filesystem.ssdSizes");
        ssdSizes = ssdS.size() == 3 ? new int[]{ssdS.get(0), ssdS.get(1), ssdS.get(2)} : new int[]{512, 1024, 2048};
        List<Double> ssdSc = config.getDoubleList("filesystem.ssdScales");
        ssdScales = ssdSc.size() == 3 ? new double[]{ssdSc.get(0), ssdSc.get(1), ssdSc.get(2)} : new double[]{1, 1, 1};
        floppySize = Math.max(0, config.getInt("filesystem.floppySize"));
        tmpSize = Math.max(0, config.getInt("filesystem.tmpSize"));
        maxFloppy = Math.max(0, config.getInt("filesystem.maxFloppy"));
        sectorSeekTime = config.getDouble("filesystem.sectorSeekTime");

        // internet
        httpEnabled = config.getBoolean("internet.enableHttp");
        httpHeadersEnabled = config.getBoolean("internet.enableHttpHeaders");
        tcpEnabled = config.getBoolean("internet.enableTcp");
        List<String> rules = config.getStringList("internet.filteringRules");
        internetFilteringRulesObserved = !rules.contains("removeme");
        internetFilteringRules = rules.stream()
            .filter(p -> !p.equals("removeme"))
            .map(InternetFilteringRule::new)
            .toArray(InternetFilteringRule[]::new);
        httpTimeout = Math.max(0, config.getInt("internet.requestTimeout")) * 1000;
        maxConnections = Math.max(0, config.getInt("internet.maxTcpConnections"));
        internetThreads = Math.max(1, config.getInt("internet.threads"));
        httpUserAgent = config.getString("internet.httpUserAgent");

        // switch
        switchDefaultMaxQueueSize = Math.max(1, config.getInt("switch.defaultMaxQueueSize"));
        switchQueueSizeUpgrade = Math.max(0, config.getInt("switch.queueSizeUpgrade"));
        switchDefaultRelayDelay = Math.max(1, config.getInt("switch.defaultRelayDelay"));
        switchRelayDelayUpgrade = Math.max(0, config.getDouble("switch.relayDelayUpgrade"));
        switchDefaultRelayAmount = Math.max(1, config.getInt("switch.defaultRelayAmount"));
        switchRelayAmountUpgrade = Math.max(0, config.getInt("switch.relayAmountUpgrade"));

        // hologram
        List<Double> hScale = config.getDoubleList("hologram.maxScale");
        hologramMaxScaleByTier = hScale.size() == 2
            ? new double[]{Math.max(1.0, hScale.get(0)), Math.max(1.0, hScale.get(1))}
            : new double[]{3.0, 4.0};
        List<Double> hTrans = config.getDoubleList("hologram.maxTranslation");
        hologramMaxTranslationByTier = hTrans.size() == 2
            ? new double[]{Math.max(0.0, hTrans.get(0)), Math.max(0.0, hTrans.get(1))}
            : new double[]{0.25, 0.5};
        hologramSetRawDelay = Math.max(0, config.getDouble("hologram.setRawDelay"));
        hologramLight = config.getBoolean("hologram.emitLight");

        // misc
        maxScreenWidth = Math.max(1, config.getInt("misc.maxScreenWidth"));
        maxScreenHeight = Math.max(1, config.getInt("misc.maxScreenHeight"));
        inputUsername = config.getBoolean("misc.inputUsername");
        initialNetworkPacketTTL = Math.max(5, config.getInt("misc.initialNetworkPacketTTL"));
        maxNetworkPacketSize = Math.max(0, config.getInt("misc.maxNetworkPacketSize"));
        maxNetworkPacketParts = Math.max(4, config.getInt("misc.maxNetworkPacketParts"));
        List<Integer> ports = config.getIntList("misc.maxOpenPorts");
        maxOpenPorts = ports.size() == 3
            ? new int[]{Math.max(0, ports.get(0)), Math.max(0, ports.get(1)), Math.max(0, ports.get(2))}
            : new int[]{16, 1, 16};
        List<Double> wireless = config.getDoubleList("misc.maxWirelessRange");
        maxWirelessRange = wireless.size() == 2
            ? new double[]{Math.max(0.0, wireless.get(0)), Math.max(0.0, wireless.get(1))}
            : new double[]{16.0, 400.0};
        updateCheck = config.getBoolean("misc.updateCheck");
        lootProbability = config.getInt("misc.lootProbability");
        lootRecrafting = config.getBoolean("misc.lootRecrafting");
        geolyzerRange = config.getInt("misc.geolyzerRange");
        geolyzerNoise = (float) Math.max(0, config.getDouble("misc.geolyzerNoise"));
        disassembleAllTheThings = config.getBoolean("misc.disassembleAllTheThings");
        disassemblerBreakChance = Math.max(0, Math.min(1, config.getDouble("misc.disassemblerBreakChance")));
        disassemblerInputBlacklist = config.getStringList("misc.disassemblerInputBlacklist");
        hideOwnPet = config.getBoolean("misc.hideOwnSpecial");
        allowItemStackInspection = config.getBoolean("misc.allowItemStackInspection");
        presentChance = Math.max(0, Math.min(1, config.getDouble("misc.presentChance")));
        assemblerBlacklist = config.getStringList("misc.assemblerBlacklist");
        threadPriority = config.getInt("misc.threadPriority");
        giveManualToNewPlayers = config.getBoolean("misc.giveManualToNewPlayers");
        dataCardSoftLimit = Math.max(0, config.getInt("misc.dataCardSoftLimit"));
        dataCardHardLimit = Math.max(0, config.getInt("misc.dataCardHardLimit"));
        dataCardTimeout = Math.max(0, config.getDouble("misc.dataCardTimeout"));
        serverRackSwitchTier = Math.max(li.cil.oc.common.Tier.NONE, Math.min(li.cil.oc.common.Tier.THREE,
            config.getInt("misc.serverRackSwitchTier") - 1));
        redstoneDelay = Math.max(0, config.getDouble("misc.redstoneDelay"));
        tradingRange = Math.max(0, config.getDouble("misc.tradingRange"));
        mfuRange = Math.max(0, Math.min(128, config.getInt("misc.mfuRange")));

        // nanomachines
        nanomachineTriggerQuota = Math.max(0, config.getDouble("nanomachines.triggerQuota"));
        nanomachineConnectorQuota = Math.max(0, config.getDouble("nanomachines.connectorQuota"));
        nanomachineMaxInputs = Math.max(1, config.getInt("nanomachines.maxInputs"));
        nanomachineMaxOutputs = Math.max(1, config.getInt("nanomachines.maxOutputs"));
        nanomachinesSafeInputsActive = Math.max(0, config.getInt("nanomachines.safeInputsActive"));
        nanomachinesMaxInputsActive = Math.max(0, config.getInt("nanomachines.maxInputsActive"));
        nanomachinesCommandDelay = Math.max(0, config.getDouble("nanomachines.commandDelay"));
        nanomachinesCommandRange = Math.max(0, config.getDouble("nanomachines.commandRange"));
        nanomachineMagnetRange = Math.max(0, config.getDouble("nanomachines.magnetRange"));
        nanomachineDisintegrationRange = Math.max(0, config.getInt("nanomachines.disintegrationRange"));
        nanomachinePotionWhitelist = config.getAnyRefList("nanomachines.potionWhitelist");
        nanomachinesHungryDamage = (float) Math.max(0, config.getDouble("nanomachines.hungryDamage"));
        nanomachinesHungryEnergyRestored = Math.max(0, config.getDouble("nanomachines.hungryEnergyRestored"));

        // printer
        maxPrintComplexity = config.getInt("printer.maxShapes");
        printRecycleRate = config.getDouble("printer.recycleRate");
        chameliumEdible = config.getBoolean("printer.chameliumEdible");
        maxPrintLightLevel = Math.max(0, Math.min(15, config.getInt("printer.maxBaseLightLevel")));
        printCustomRedstone = Math.max(0, config.getInt("printer.customRedstoneCost"));
        printMaterialValue = Math.max(0, config.getInt("printer.materialValue"));
        printInkValue = Math.max(0, config.getInt("printer.inkValue"));
        printsHaveOpacity = config.getBoolean("printer.printsHaveOpacity");
        noclipMultiplier = Math.max(0, config.getDouble("printer.noclipMultiplier"));

        // chunkloader
        chunkloadDimensionBlacklist = getIntList(config, "chunkloader.dimBlacklist");
        chunkloadDimensionWhitelist = getIntList(config, "chunkloader.dimWhitelist");

        // integration
        modBlacklist = config.getStringList("integration.modBlacklist");
        peripheralBlacklist = config.getStringList("integration.peripheralBlacklist");
        fakePlayerUuid = config.getString("integration.fakePlayerUuid");
        fakePlayerName = config.getString("integration.fakePlayerName");
        enableInventoryDriver = config.getBoolean("integration.vanilla.enableInventoryDriver");
        enableTankDriver = config.getBoolean("integration.vanilla.enableTankDriver");
        enableCommandBlockDriver = config.getBoolean("integration.vanilla.enableCommandBlockDriver");
        allowItemStackNBTTags = config.getBoolean("integration.vanilla.allowItemStackNBTTags");
        costProgrammingTable = Math.max(0, config.getDouble("integration.buildcraft.programmingTableCost"));

        // power
        ignorePower = config.getBoolean("power.ignorePower");
        tickFrequency = Math.max(1, config.getInt("power.tickFrequency"));
        bufferComputer = Math.max(0, config.getDouble("power.buffer.computer"));
        computerCost = Math.max(0, config.getDouble("power.cost.computer"));
        robotCost = Math.max(0, config.getDouble("power.cost.robot"));
        sleepCostFactor = Math.max(0, config.getDouble("power.cost.sleepFactor"));
        hddReadCost = Math.max(0, config.getDouble("power.cost.hddRead"));
        hddWriteCost = Math.max(0, config.getDouble("power.cost.hddWrite"));
        hddSeekCost = Math.max(0, config.getDouble("power.cost.hddSeek"));
        screenCostFactor = Math.max(0, config.getDouble("power.cost.screenFactor"));
        gpuFillCost = Math.max(0, config.getDouble("power.cost.gpuFill"));
        gpuClearCost = Math.max(0, config.getDouble("power.cost.gpuClear"));
        gpuCopyCost = Math.max(0, config.getDouble("power.cost.gpuCopy"));
        gpuSetCost = Math.max(0, config.getDouble("power.cost.gpuSet"));
        gpuBitbltCost = Math.max(0, config.getDouble("power.cost.gpuBitblt"));
        robotTurnCost = Math.max(0, config.getDouble("power.cost.robotTurn"));
        robotMoveCost = Math.max(0, config.getDouble("power.cost.robotMove"));
        robotExhaustionCost = Math.max(0, config.getDouble("power.cost.robotExhaustion"));
        assemblerCost = Math.max(0, config.getDouble("power.cost.assemblerPerItem"));
        chargeCostFactor = Math.max(0, config.getDouble("power.cost.chargeFactor"));
        printerCost = Math.max(0, config.getDouble("power.cost.printer"));
        nanomachineCost = Math.max(0, config.getDouble("power.cost.nanomachines"));
        terminalServerCost = Math.max(0, config.getDouble("power.cost.terminalServer"));
        dataCardTrivial = Math.max(0, config.getDouble("power.cost.dataCardTrivial"));
        dataCardSimple = Math.max(0, config.getDouble("power.cost.dataCardSimple"));
        dataCardComplex = Math.max(0, config.getDouble("power.cost.dataCardComplex"));
        dataCardAsymmetric = Math.max(0, config.getDouble("power.cost.dataCardAsymmetric"));
        transposerCost = Math.max(0, config.getDouble("power.cost.transposer"));
        generatorEfficiency = Math.max(0, config.getDouble("power.efficiency.generator"));
        solarGeneratorEfficiency = Math.max(0, config.getDouble("power.efficiency.solarGenerator"));
        abstractBusPacketCost = Math.max(0, config.getDouble("power.cost.abstractBusPacket"));

        // debug
        logLuaCallbackErrors = config.getBoolean("debug.logCallbackErrors");
        forceLuaJ = config.getBoolean("debug.forceLuaJ");
        allowUserdata = !config.getBoolean("debug.disableUserdata");
        allowPersistence = !config.getBoolean("debug.disablePersistence");
        limitMemory = !config.getBoolean("debug.disableMemoryLimit");
        forceCaseInsensitive = config.getBoolean("debug.forceCaseInsensitiveFS");
        logFullLibLoadErrors = config.getBoolean("debug.logFullNativeLibLoadErrors");
        forceNativeLibPlatform = config.getString("debug.forceNativeLibPlatform");
        forceNativeLibPathFirst = config.getString("debug.forceNativeLibPathFirst");
        logOpenGLErrors = config.getBoolean("debug.logOpenGLErrors");
        logHexFontErrors = config.getBoolean("debug.logHexFontErrors");
        alwaysTryNative = config.getBoolean("debug.alwaysTryNative");
        debugPersistence = config.getBoolean("debug.verbosePersistenceErrors");
        nativeInTmpDir = config.getBoolean("debug.nativeInTmpDir");
        periodicallyForceLightUpdate = config.getBoolean("debug.periodicallyForceLightUpdate");
        insertIdsInConverters = config.getBoolean("debug.insertIdsInConverters");
        registerLuaJArchitecture = config.getBoolean("debug.registerLuaJArchitecture");
        disableLocaleChanging = config.getBoolean("debug.disableLocaleChanging");

        // >= 1.7.6
        List<Double> vram = config.getDoubleList("gpu.vramSizes");
        vramSizes = vram.size() == 3
            ? new double[]{vram.get(0), vram.get(1), vram.get(2)}
            : new double[]{1, 2, 3};
        bitbltCost = config.hasPath("gpu.bitbltCost") ? config.getDouble("gpu.bitbltCost") : 0.5;

        // >= 1.8.2
        diskActivitySoundDelay = Math.max(-1, config.getInt("misc.diskActivitySoundDelay"));
        maxNetworkClientPacketDistance = Math.max(0, config.getDouble("misc.maxNetworkClientPacketDistance"));
        maxNetworkClientEffectPacketDistance = Math.max(0, config.getDouble("misc.maxNetworkClientEffectPacketDistance"));
        maxNetworkClientSoundPacketDistance = Math.max(0, config.getDouble("misc.maxNetworkClientSoundPacketDistance"));

    }

    public boolean internetFilteringRulesInvalid() {
        for (InternetFilteringRule rule : internetFilteringRules) {
            if (rule.invalid()) return true;
        }
        return false;
    }

    public boolean internetAccessConfigured() {
        return httpEnabled || tcpEnabled;
    }

    public boolean internetAccessAllowed() {
        return internetAccessConfigured() && !internetFilteringRulesInvalid();
    }

    // ----------------------------------------------------------------------- //

    private static double[] toDoubleArray(List<Double> list, int expected, String name) {
        if (list.size() == expected) {
            double[] arr = new double[expected];
            for (int i = 0; i < expected; i++) arr[i] = list.get(i);
            return arr;
        }
        OpenComputersMod.LOGGER.warn("Bad number of {} values, ignoring.", name);
        double[] arr = new double[expected];
        java.util.Arrays.fill(arr, 0.25);
        return arr;
    }

    public static List<Integer> getIntList(Config config, String path) {
        return config.hasPath(path) ? config.getIntList(path) : List.of();
    }
}
