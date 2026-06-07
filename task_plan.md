# Task Plan — Faithful Scala→Java Port of the Network API

**Goal:** Finish porting `li/cil/oc/server/network/**` from Scala to Java for the NeoForge 26.1
build — as a **faithful 1:1 structural port** of the Scala `Vertex`/`Edge` + queued-notification +
`Distributor` design — covering wired graph, energy distribution, **and** wireless / Quantum /
Waypoints. Detailed design + integrity assessment: see `NETWORK_PORT_PLAN.md`.

**Status:** Planning complete. Implementation not started.

**Decisions (confirmed with user):**
- Fidelity: faithful 1:1 structural port (replace the current clean-room rewrite).
- Scope: everything now (wired graph + energy + wireless + Quantum + Waypoints).

---

## Phase 0 — Baseline & cleanup
Status: `complete`
1. ✅ `./gradlew compileJava --rerun-tasks` → **BUILD SUCCESSFUL** (only generic deprecation note).
   The untracked network code **compiles**; yesterday's "technical issue" was NOT a compile break —
   the problems are behavioral (the integrity defects in `findings.md`). Baseline is clean.
   - Note: build runs Gradle **8.14.4** (not 9.3.0 as memory recorded); wrapper props are modified.
2. ✅ Stale textures planning files replaced with network-port content.

## Phase 1 — Faithful core graph (`server/network/`)
Status: `complete` (compiles; behavioral tests in Phase 5)
- ✅ `Distributor.java`, faithful `NodeImpl` (no neighbors field), `ConnectorNodeImpl` energy,
  `NetworkImpl` (Vertex/Edge/searchGraphs/add/handleSplit/changeBuffer + Notify queue),
  `ComponentSupport` (shared scan/dispatch), both component nodes. `./gradlew compileJava` ✅.
- New `Distributor.java` (mirrors `Distributor.scala`).
- Keep `NodeImpl.java` (already matches `Node.scala`); verify load/save + onConnect/onDisconnect.
- Rewrite `ConnectorNodeImpl.java` energy: `change`/`changeBuffer`/`tryChangeBuffer`/
  `setLocalBufferSize` against a `Distributor` ref (`Connector.scala:24-130`).
- Rewrite `NetworkImpl.java` with inner `Vertex`/`Edge`, `searchGraphs`, faithful
  `connect`/`disconnect`/`remove`/`add`/`handleSplit`/`changeBuffer` (`Network.scala:69-441,651-693`).
- Factor shared `@Callback` scan/invoke into one helper for the two component nodes; keep the
  reflection-based dispatch as an explicit interim seam (Callbacks/Registry not ported).

## Phase 2 — NetworkAPIImpl corrections
Status: `complete` (compiles)
- ✅ Rewrote both `joinOrCreateNetwork` overloads: `BlockEntity` delegates to `(level,pos)`;
  per-side `localNode` resolution; added `canConnectBasedOnColor` (COLORED capability, SILVER
  default) + the `else disconnect` branch. Immibis-microblock check omitted (integration not ported).
- Builder records + packet (de)serialization kept as-is.

## Phase 3 — Wireless subsystem
Status: `complete` (compiles)
- ✅ `util/RTree.java` (full port), `WirelessNetwork.java` (per-dim RTree keyed by
  `ResourceKey<Level>`; LevelEvent/ChunkEvent cleanup; obstruction raycast via Vec3/getDestroySpeed),
  `QuantumNetwork.java`. Wireless delegations wired into `NetworkAPIImpl`; `WirelessNetwork.init()`
  registered in `OpenComputersMod`.
- `SideTracker` left excluded — NOT needed (wireless uses `level.isClientSide()`; interim Component
  doesn't use it). Defer its 26.1 rewrite to when the Machine/Component wiring lands.

## Phase 4 — Waypoints
Status: `complete`
- ✅ `Waypoints.java` (per-dim RTree registry, WirelessNetwork pattern)
- ✅ `WaypointBlockEntity.java` (extends TileEntityEnvironment, node "waypoint", label NBT, client particles)
- ✅ `Waypoint.java` block: implements EntityBlock, wires BE + client ticker
- ✅ `Registries.WAYPOINT_BE` registered
- ✅ `OpenComputersMod`: `Waypoints.init()` + ENVIRONMENT capability for WAYPOINT_BE
- Deferred: label-editor screen (WaypointScreen), label sync packet, Geolyzer/NavUpgrade integration

## Phase 5 — Tests & verification
Status: `complete` (unit); runClient pending
- ✅ `src/test/java/.../NetworkTest.java`: 6 tests — merge+notify, onConnect contract (3 nodes),
  edge-disconnect split + cross-component notify, **3-way split notify** (the rewrite's bug),
  address remap. **5 pass.**
- ⚠️ Energy test skipped: `Settings.get()` can't init headlessly — bundled `application.conf` is
  missing key `robot.xp.baseXpToLevel` (pre-existing config-port gap, unrelated to network). Energy
  pooling is a faithful translation + compiles; verify via runClient.
- `./gradlew compileJava` ✅, `test` ✅ (1 skipped). `build` + `runClient` smoke test still pending.

---

## Phase 6 — Faithful dispatch core (close the component-dispatch seam)
Status: `complete` (`./gradlew build` green)
- `server/machine/ArgumentsImpl.java` — faithful port of `ArgumentsImpl.scala` (~350 lines, all 28
  `Arguments` methods). Replaces `ComponentSupport.SimpleArguments` (deleted).
- `server/machine/Callbacks.java` — faithful port of `Callbacks.scala`: per-class cache,
  `staticAnalyze`/`dynamicAnalyze`, `ComponentCallback`/`PeripheralCallback`, honoring
  `ManagedPeripheral`/`FilteredEnvironment`/`MethodWhitelist`/`NamedBlock`. Invocation via plain
  reflection (no ASM `CallbackWrapper` — Phase 9 already dropped ASM). Seam: omit
  `CompoundBlockEnvironment` branch (single-environment is the only live path).
- `server/driver/Registry.java` (minimal) — port `convert`/`convertRecursively`/`convertList`/
  `convertMap` (`Registry.scala:151-252`) + a `converters` list & `add(Converter)` (empty today).

## Phase 7 — Rewire dispatch call sites
Status: `complete`
- `ComponentSupport.java` — Callbacks-backed map; `methods`/`annotation`/`invoke` faithful
  (`Registry.convert(callback.apply(host,ctx,new ArgumentsImpl(args)))`); add `addTo`/`removeFrom`.
- `ComponentNodeImpl.setVisibility` (+ `ComponentConnectorNodeImpl` if duplicated) — port the
  transition matrix `Component.scala:54-81`; drop `SideTracker.isServer` guard.
- `Machine.java` — make `addComponent`/`removeComponent` public; real `methods(value)`,
  `invoke(address,…)` (`Machine.scala:375-392`), `invoke(Value,…)` (`Machine.scala:394-405`).

## Phase 8 — Dispatch tests & verification
Status: `complete`
- ✅ `ComponentDispatchTest` — 7 tests: enumerate callbacks, invoke+convert, unknown→NoSuchMethod,
  bad-arg→IllegalArgument, visibility transition matrix (safe + state), reachability-bound reject,
  canBeSeenFrom reflects visibility. All pass.
- ⚠️ Machine (de)registration via `setVisibility` is NOT unit-assertable: it fires only for in-range
  hosts that are a concrete `server.machine.Machine`, which needs mod-initialized `API.network` + a
  world to construct. Covered by runClient (mirrors the deferred energy assertion). Tests instead
  verify the transition matrix runs cleanly + lands in the right state.
- ✅ `./gradlew build` green; `NetworkTest` holds (6 tests, 5 pass / 1 skip).

---

## Known dependency seams (do not silently stub)
- Waypoint label-editor screen / sync packet / Geolyzer-NavUpgrade integration (deferred from Phase 4).
- `SideTracker` currently excluded from compilation.
- Driver-layer seams left by Phase 6: `CompoundBlockEnvironment` multi-environment hosts; converter
  registration into `Registry.converters` (list wired but empty until the driver layer lands).

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| _(none yet)_ | | |
