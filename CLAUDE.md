# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

OpenComputers is a Minecraft 1.12.2 (Forge) mod that adds programmable computers, robots, and related devices to the game. The primary language is **Scala**, with Java used for the public API (`src/main/java/li/cil/oc/api/`). The build target is Java 8.

## Build Commands

```bash
# First-time setup (decompiles Minecraft, downloads assets — takes several minutes)
./gradlew setupDecompWorkspace

# Build the mod JAR
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "InternetFilteringRuleTest"

# Launch Minecraft client in dev environment
./gradlew runClient

# Launch Minecraft server in dev environment
./gradlew runServer
```

The build requires **Java 8** (not newer). CI enforces this. The version string is assembled from `gradle.properties` (`mod_version`), `build.properties` (minecraft/forge versions), and the current git short SHA.

Source token substitution (`@VERSION@`, `@MCVERSIONDEP@`) is handled by the `replaceSourceTokensScala` Gradle task. When building outside IntelliJ IDEA, `compileScala` uses the substituted sources from `build/srcReplaced/scala/`.

## Architecture

### Layer Overview

```
src/main/java/li/cil/oc/api/      — Public Java API (for third-party mod integration)
src/main/scala/li/cil/oc/
  common/                          — Shared client+server code (blocks, items, tile entities, packets)
  server/                          — Server-only logic (machine execution, network, drivers, filesystem)
  client/                          — Client-only code (GUI, rendering)
  integration/                     — Optional hooks for third-party mods
  util/                            — Shared utilities
src/main/resources/assets/opencomputers/
  lua/                             — BIOS and per-architecture Lua bootstrap
  loot/openos/                     — OpenOS (the default Lua OS shipped on loot disks)
  lang/                            — Localization files
```

### Machine Execution (`server/machine/`)

The `Machine` class (`server/machine/Machine.scala`) is the core runtime. It owns:
- The component network node (with power buffer)
- A signal queue for inter-thread communication
- An `Architecture` that drives actual Lua execution

Two architecture backends exist:
- **NativeLua** (`server/machine/luac/`) — JNLua-based, supports Lua 5.2/5.3/5.4 via native libs
- **LuaJ** (`server/machine/luaj/`) — Pure-Java fallback

`Machine` runs in a dedicated thread pool (`util/ThreadPoolFactory.scala`) and communicates back to the server thread via synchronized signals. `@Callback`-annotated methods flagged `direct = false` are dispatched synchronously on the server tick; `direct = true` callbacks run in the machine thread.

### Component Network (`server/network/`, `api/network/`)

Every in-game device is a `Node` in a graph-based network. `Network.scala` maintains the graph as a union-find structure. Components (nodes with `Visibility`) emit and receive messages (`sendToAddress`, `sendToReachable`, etc.). The `ComponentConnector` subtype also tracks energy flow.

Wireless networking is handled separately in `WirelessNetwork.scala` using an R-tree (`util/RTree.scala`) for spatial range lookups.

### Driver Registry (`server/driver/Registry.scala`)

The `Registry` singleton maps `ItemStack`s and `Block`s to their driver implementations. When a computer scans its inventory, it queries this registry to discover which components are available. Block drivers wrap arbitrary third-party tile entities; item drivers attach components to machine slots.

### Tile Entities (`common/tileentity/`)

Most devices have a corresponding tile entity (e.g. `Case.scala` for computer cases, `Robot.scala` for robots). Tile entities compose behavior through Scala traits in `common/tileentity/traits/`:
- `Computer` — wires a `Machine` to the tile entity lifecycle
- `ComponentInventory` — tracks installed components and syncs them to the machine
- `PowerAcceptor` / `PowerBalancer` — energy handling
- `RedstoneAware`, `Rotatable`, etc. — peripheral behaviors

### Integration (`integration/`)

Each supported mod has a package with a `ModProxy` implementation. Mods are detected at runtime by `Mods.scala`; if absent, their proxy is never initialized. Registration (drivers, converters) happens in `ModProxy.initialize()`. All integration must be optional — OC must work without any of these mods present.

`ClassTransformer.scala` + `TransformerLoader.scala` handle the `SimpleComponent` bytecode injection that lets third-party tile entities implement the OC component interface without a hard compile-time dependency.

### Lua Layer (`resources/assets/opencomputers/lua/`)

`bios.lua` is the first Lua code executed. It finds an EEPROM/filesystem and loads `/init.lua`. `machine.lua` is the Lua-side runtime that sets up the component API, event loop, coroutine scheduler, and mounts filesystems. OpenOS lives in `loot/openos/` and is the default OS shipped on generated loot disks.

### Power System

Internal energy uses OC's own unit (`Settings.get.powerRatio` converts from external sources). Power traits in `tileentity/traits/power/` bridge to IC2, Mekanism, and other energy APIs. The `Connector` node type participates in energy distribution across the component network.

## Key Conventions

- **Blocks and items** are registered via `common/init/Blocks.scala` and `common/init/Items.scala` during `FMLPreInitializationEvent`.
- **Packets** use a custom builder/handler (`PacketBuilder.scala`, `PacketHandler.scala`, `PacketType.scala`) — not Forge's packet system directly.
- **NBT persistence** uses extension methods from `util/ExtendedNBT.scala`.
- **Callbacks** (Lua-callable methods) use the `@Callback` Java annotation from `api/machine/Callback.java`. Return `Array[AnyRef]` or use `util/ResultWrapper.result(...)`.
- **Tiers** are represented by the `Tier` object constants (`Tier.One` = 1, etc.) and flow through item/component creation.
- The `Settings` singleton (`Settings.scala` at `li/cil/oc/`) loads `opencomputers/settings.conf` from the config directory; defaults are in `resources/application.conf`.

## Adding a New Mod Integration

1. Add the mod ID to `Mods.IDs` in `integration/Mods.scala`.
2. Create a `SimpleMod` (or custom) instance in `Mods.scala`.
3. Create a package under `integration/yourmod/` with a class implementing `ModProxy`.
4. Register drivers/converters in `ModProxy.initialize()`, guarded so OC still functions when the mod is absent.
5. Add the mod as a `compileOnly` dependency in `build.gradle` if an API JAR is available.

## PR Guidelines (from project docs)

- Keep changes minimal — no whitespace-only edits to existing files.
- All integration with other mods must be weak (OC works without them).
- Squash commits before opening a PR.
- Java contributions may be converted to Scala by maintainers.
