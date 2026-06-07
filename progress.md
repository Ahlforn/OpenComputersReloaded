# Progress Log — Network API Port

## Session: 2026-06-07 (cont.) — Component-dispatch port

Goal: close the component-dispatch seam left by the Network port (faithful Callbacks/ArgumentsImpl/
Registry.convert + machine wiring). Added Phases 6–8 to `task_plan.md`; design in `findings.md`.
**Status: COMPLETE — all three phases done, `./gradlew build` green.**

- Phase 6 — dispatch core (ArgumentsImpl, Callbacks, minimal Registry.convert): `complete`
- Phase 7 — rewire ComponentSupport / setVisibility / Machine: `complete`
- Phase 8 — tests & verification: `complete`

Key decisions: reflection over ASM `CallbackWrapper`; empty-converter `Registry.convert` is faithful
today; `CompoundBlockEnvironment` left as a documented seam.

### What landed
- Phase 6 ✅ New: `server/machine/ArgumentsImpl.java`, `server/machine/Callbacks.java` (+ Java
  `PeripheralAnnotation.java`), `server/driver/Registry.java` (convert chain + converters list).
- Phase 7 ✅ `ComponentSupport` now Callbacks-backed (`Registry.convert(callback.apply(host, ctx,
  new ArgumentsImpl(args)))`) + static `changeVisibility`/`addTo`/`removeFrom`. `setVisibility`
  transition matrix wired in both component node types. `Machine`: `addComponent`/`removeComponent`
  made public; `methods(value)`, `invoke(address,…)`, `invoke(Value,…)` implemented faithfully.
- Phase 8 ✅ `ComponentDispatchTest` 7/7 pass; `NetworkTest` 6 (5 pass, 1 skip) unchanged.

### Test results
| When | Command | Result |
|------|---------|--------|
| Phase 6 | `gradlew compileJava` | BUILD SUCCESSFUL |
| Phase 7 | `gradlew compileJava` | BUILD SUCCESSFUL |
| Phase 8 | `gradlew test (ComponentDispatchTest+NetworkTest)` | 7 pass; 5 pass/1 skip |
| Phase 8 | `gradlew build` | BUILD SUCCESSFUL |

### Seams still open (driver layer)
- `CompoundBlockEnvironment` multi-environment hosts + `hosts(method)` resolution.
- Converter registration into `Registry.converters` (list wired, empty).
- `ArgumentsImpl.makeStack` (table→ItemStack w/ NBT/data-components) — item-data port.
- Machine (de)registration assertion needs in-game (`API.network` + world).

---

## Session: 2026-06-07

### Phase 4 — Waypoints (complete)
- Created `server/network/Waypoints.java` (per-dim RTree registry, mirrors WirelessNetwork.java)
- Created `common/tileentity/WaypointBlockEntity.java` (extends TileEntityEnvironment, node
  "waypoint", label NBT, client particle tick)
- Modified `common/block/Waypoint.java`: implements EntityBlock, wires BE + client ticker
- Modified `common/init/Registries.java`: added WAYPOINT_BE DeferredHolder
- Modified `OpenComputersMod.java`: Waypoints.init() + ENVIRONMENT capability for WAYPOINT_BE
- `./gradlew compileJava` → BUILD SUCCESSFUL
- `./gradlew test` → 6 tests, 0 failures, 1 skipped (same as before)
- **All 5 network API port phases now complete.**

---

## Session: 2026-06-05

### Restored / re-scoped context
- Asked to review yesterday's stopped Network API port and plan its completion.
- Found the work in untracked `src/main/java/li/cil/oc/server/network/` (8 files, ~1450 lines).
- Identified pre-existing `task_plan.md`/`findings.md`/`progress.md` as a **stale, unrelated**
  textures/build-warnings task (essentially complete). Replaced all three with network-port content.
- Completed a full integrity review of the port against the Scala originals — see `findings.md`
  and `NETWORK_PORT_PLAN.md`.

### Decisions (from user)
- Fidelity: **faithful 1:1 structural port** (replace the current rewrite).
- Scope: **everything now** — wired graph + energy + wireless + Quantum + Waypoints.

### Planning artifacts
- `NETWORK_PORT_PLAN.md` — detailed plan + integrity assessment.
- `task_plan.md` — phase tracker (Phase 0–5).
- `findings.md` — defects, faithful algorithms, infra status, 26.1 API notes.

### Phase 0 — baseline (complete)
- `./gradlew compileJava --rerun-tasks` → **BUILD SUCCESSFUL** (only a generic deprecation note).
- Key result: the untracked network code **compiles**. Yesterday's stop was NOT a compile error;
  the issues are behavioral (integrity defects). Safe to begin the faithful rewrite from here.
- Observed: build uses Gradle 8.14.4 (memory said 9.3.0); `gradle-wrapper.properties` is modified
  in the working tree — worth confirming the intended Gradle version before committing.

### Next action
- Phase 1: faithful core graph rewrite — start with `Distributor.java` + `ConnectorNodeImpl` energy,
  then `NetworkImpl` (Vertex/Edge/searchGraphs/add/handleSplit/changeBuffer).

### Phases 1–5 (this session, continued)
- Phase 1 ✅ faithful core graph (Distributor, NodeImpl, ConnectorNodeImpl energy, NetworkImpl
  Vertex/Edge/searchGraphs/add/handleSplit/changeBuffer + Notify queue, ComponentSupport, 2 component nodes).
- Phase 2 ✅ NetworkAPIImpl per-side node resolution + color-connection check + else-disconnect.
- Phase 3 ✅ RTree.java, WirelessNetwork.java (ResourceKey-keyed), QuantumNetwork.java; wired
  wireless delegations + WirelessNetwork.init() in OpenComputersMod. SideTracker left excluded (not needed).
- Phase 4 ⛔ DEFERRED — hard-blocked on un-ported Waypoint BlockEntity.
- Phase 5 ✅ NetworkTest: 5 pass, 1 skipped (energy — Settings headless init fails on missing
  `robot.xp.baseXpToLevel` in application.conf).

### Discoveries
- `Settings.get()` returns null until `Settings.load()`; `Settings.load` from defaults throws
  `ConfigException$Missing: robot.xp.baseXpToLevel` (application.conf line 385). Pre-existing
  config-port gap — flag for a later Settings/config phase.
- Faithful `ConnectorNodeImpl` calls `Settings.get().ignorePower` directly (Scala assumes Settings
  is always loaded); fine in-game, but unit tests must init Settings.

### Test results
| When | Command | Result |
|------|---------|--------|
| Phase 0 | `gradlew compileJava --rerun-tasks` | BUILD SUCCESSFUL (deprecation note only) |
| Phase 1–3 | `gradlew compileJava` | BUILD SUCCESSFUL |
| Phase 5 | `gradlew test --tests NetworkTest` | 5 passed, 1 skipped (energy/Settings), 0 failed |
