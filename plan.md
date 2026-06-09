# Plan — Close the Open Seams of the OpenComputers NeoForge Port

## Context

The OpenComputers port to NeoForge / MC 26.1 (Java 25) has landed Phases 0–11 (toolchain,
registries, block-entities, networking, capabilities, API, rendering) plus an uncommitted faithful
Scala→Java port of the **network layer** and **component-dispatch** layer. That work left a set of
documented "seams" — intentionally deferred holes — recorded in `findings.md`, `task_plan.md`,
`progress.md`, and the auto-memory. This plan closes **all** of them, **including the block-driver
layer** (the largest, which the multi-environment dispatch seam depends on).

Why now: the seams block (a) a skipped energy unit test, (b) item-table callback arguments, (c) the
waypoint UX, (d) any callback that returns an item/fluid/world producing a useful Lua table, and
(e) multi-driver block hosts. Closing them makes the ported runtime exercisable end-to-end.

Scope decisions (confirmed with user): **everything, including block drivers**; **SideTracker
rewritten now**. Driver-layer work targets the *infrastructure + dispatch* (registration, lookup,
compound wrapping, multi-env routing) with a minimal test driver to exercise it — not a port of all
~40 individual component drivers (that is the separate component-port effort).

All new code is Java under `src/main/java/li/cil/oc/`. The Scala tree under `src/main/scala/` is
reference-only — port faithfully against it, do not modify it.

---

## Phase A — Settings / config reconciliation  *(unblocks energy test)*

**Problem (verified):** `Settings.java:469-472` reads `robot.xp.baseXpToLevel`, `robot.xp.xpCostPerTier`,
`robot.xp.baseXpForAction`, `robot.xp.upgradeXpCostMultiplier` — **invented key names absent from both**
the bundled `application.conf` and the Scala original. `application.conf` (xp block ~L385-433) uses
the faithful Scala keys: `baseValue`, `constantGrowth`, `exponentialGrowth`, `actionXp`,
`exhaustionXpRate`, `oreXpRate`, `bufferPerLevel`, `toolEfficiencyPerLevel`,
`harvestSpeedBoostPerLevel`. `Settings.load()` therefore throws `ConfigException$Missing`, which is
why `NetworkTest.energyPoolsAcrossConnectors` is skipped (`requireSettings()` aborts on the throw).

**Fix:** Port the `robot.xp` section of `Settings.java` faithfully from `Settings.scala` (xp fields
~L121-129) — replace the four invented reads with the real keys/fields the Scala settings expose.
Do **not** paper over it by adding bogus keys to `application.conf`; the config is the faithful one.

- **Modify:** `src/main/java/li/cil/oc/Settings.java` (xp field declarations + the four reads at
  ~L468-472), matching `src/main/scala/li/cil/oc/Settings.scala` field-by-field.
- **Modify:** `src/test/java/li/cil/oc/server/network/NetworkTest.java` — remove the skip guard on the
  energy test once `Settings.load()` succeeds headlessly.

**Verify:** `./gradlew test --tests NetworkTest` → energy test runs and passes (6/6, no skip).

---

## Phase B — `ArgumentsImpl.makeStack` (table → ItemStack)

**Problem (verified):** `ArgumentsImpl.java:289-292` `makeStack(...)` throws `UnsupportedOperationException`;
reached from the `checkItemStack` path (`:181`). Scala original (`ArgumentsImpl.scala:339-347`) looks
the item up in the registry, builds a stack with damage, and applies the NBT tag.

**Fix (26.1 APIs):**
- Item lookup: `BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(name))` (→ `IllegalArgumentException`
  on miss, faithful to Scala).
- Construct `new ItemStack(item, 1)`.
- Damage → `stack.set(DataComponents.DAMAGE, damage)` (NBT `damage` slot is gone in 26.1).
- Tag → `stack.set(DataComponents.CUSTOM_DATA, CustomData.of(compound))`, reusing the exact pattern in
  `api/prefab/DriverItem.java:50-58`.

- **Modify:** `src/main/java/li/cil/oc/server/machine/ArgumentsImpl.java` (`makeStack` body; class javadoc note at L18).

**Verify:** Unit test feeding a `{name, damage, tag}` table through `checkItemStack`; assert item, damage,
and round-tripped custom data. `./gradlew build`.

---

## Phase C — `SideTracker` 26.1 rewrite

**Problem (verified):** `util/SideTracker.java` wraps removed Forge `FMLCommonHandler`; excluded in
`build.gradle`. No live compiled caller today (only user, `common/asm/template/StaticSimpleEnvironment.java`,
is itself an excluded ASM template). Rewriting now per user request so it's ready for callers.

**Fix:** Reimplement logical-side detection for NeoForge 26.1 — track the server thread (capture on
`ServerStartingEvent` / server thread, compare `Thread.currentThread()`), falling back to the existing
thread-local registration set the Scala version uses for off-thread machine work. Mirror
`SideTracker.scala`'s `isServer()/isClient()` contract.

- **Modify:** `src/main/java/li/cil/oc/util/SideTracker.java` — drop FML imports; 26.1 thread/side logic.
- **Modify:** `build.gradle` — remove the `SideTracker.java` compilation exclusion.

**Verify:** `./gradlew compileJava` green with SideTracker included; quick test asserting `isServer()`
on the (registered) server thread vs. an unregistered thread.

---

## Phase D — Waypoint label packet + editor screen

**Problem (verified):** `WaypointBlockEntity.java` / `block/Waypoint.java` have node + label NBT +
particles, but the label-editor screen and the client→server label packet are deferred.
`PacketType.WaypointLabel` exists but is unhandled. Scala uses a plain `GuiScreen` (single `EditBox`,
32-char cap, ENTER sends), `PacketSender.sendWaypointLabel`, and a server handler that validates
distance ≤ 64 and rebroadcasts (`client/gui/Waypoint.scala`, `client/PacketSender.scala`,
`server/PacketHandler.scala`).

**Fix (follow the established Phase-5 packet + Phase-7 screen patterns):**
- **Create:** `client/PacketSender.java` — `sendWaypointLabel(WaypointBlockEntity)` via
  `PacketBuilder.simple(PacketType.WaypointLabel)` + `sendToServer()` (mirror `server/PacketSender.sendComputerState`).
- **Create:** `client/gui/WaypointScreen.java` — a plain `Screen` (not `AbstractContainerScreen`; no
  Menu needed) with a single `EditBox`, 32-char cap, ENTER → `PacketSender.sendWaypointLabel`, ESC /
  distance > 64 closes.
- **Modify:** `common/PacketHandler.java` — add `case WaypointLabel -> handleWaypointLabel(...)`:
  read tile + UTF, validate distance, update `WaypointBlockEntity` label, rebroadcast to nearby players.
- **Wire:** open the screen on block activation (client) — match how `Waypoint.scala` opens its GUI;
  register/open via the existing screen-opening path used by `CaseScreen`.

**Verify:** `runClient` — place waypoint, open editor, type a label, confirm it persists + syncs to a
second client. `./gradlew build` for compile/asset checks.

---

## Phase E — Core converters

**Problem (verified):** `Registry.java` has the recursive `convert` chain + `add(Converter)` but the
`converters` list is empty and **no** `Converter` is ported, so callbacks returning items/fluids/worlds
yield `toString()` fallbacks instead of structured Lua tables.

**Fix:** Port the core Minecraft converters from `integration/minecraft/` and register them at mod init.
Scope to the vanilla set (mod-specific converters — IC2/Mekanism/etc. — wait for their integration ports):
- **Create:** `ConverterItemStack`, `ConverterNBT`, `ConverterFluidStack`, `ConverterWorld`
  (+ `ConverterWorldProvider`, fluid-tank/container converters if their 26.1 fluid APIs are available),
  faithful to the Scala files of the same name. 26.1 notes: NBT→`CustomData`/`CompoundTag`, fluids via
  `net.neoforged.neoforge.fluids`, item fields via data components.
- **Modify:** `OpenComputersMod.java` — register each via `Registry.add(new ConverterX())` during init
  (near the existing `API.network`/`Waypoints.init()` wiring).

**Verify:** Unit test: convert an `ItemStack` and an NBT compound through `Registry.convert`, assert the
Lua-table shape (name/label/size/damage/tag for items; `oc:flatten` unwrap for NBT). `./gradlew build`.

---

## Phase F — Block-driver layer + multi-environment dispatch  *(the large one)*

**Problem (verified):** `Registry.java` has **no** item/block driver lists, no `driverFor` lookups, and
no compound wrapping; no Java `CompoundBlockDriver`/`CompoundBlockEnvironment` exist. `ComponentSupport`
does single-environment dispatch only — the `hosts(method)` resolution + `CompoundBlockEnvironment`
branch of `Component.scala:26-49` is unported (dead until block drivers land). This is the dependency
sink the other dispatch seams point at.

**Fix — build the infrastructure, faithful to Scala, then a minimal driver to exercise it:**
1. **Registry driver registry** — extend `server/driver/Registry.java` to mirror `Registry.scala`:
   `sidedBlocks`/`items` lists + the `add(DriverBlock)`, `add(DriverItem)`, `add(EnvironmentProvider)`,
   `add(InventoryProvider)` overloads (+ the `locked` init-phase gate), and the three `driverFor`
   lookups: `driverFor(ItemStack)`, `driverFor(ItemStack, host)`, `driverFor(Level, BlockPos, Direction)`
   (`Registry.scala:101-121`).
2. **`CompoundBlockDriver`** — port `CompoundBlockDriver.scala`: aggregate the block drivers matching a
   position; `driverFor(world,pos,side)` returns it when >1 match.
3. **`CompoundBlockEnvironment`** — port `CompoundBlockEnvironment.scala`: single `Component` node
   wrapping `(name, ManagedEnvironment)*`, max-reachability visibility, fan-out `update/load/save` and
   connect/disconnect, per-driver NBT compartments.
4. **Multi-env dispatch** — extend `server/network/ComponentSupport.java` to port the `hosts(method)`
   resolution (`Component.scala:26-49`): for a `CompoundBlockEnvironment` host, route each `@Callback` /
   peripheral method to the owning environment; single-environment hosts keep the direct path.
5. **Minimal test driver** — one small `DriverBlock` producing two environments for one block, used only
   to exercise compound wrapping + multi-env routing (not a production component).

- **Create:** `server/driver/CompoundBlockDriver.java`, `server/driver/CompoundBlockEnvironment.java`.
- **Modify:** `server/driver/Registry.java`, `server/network/ComponentSupport.java`,
  and `OpenComputersMod.java` (lock the registry after init, faithful to Scala).

**Verify:** `CompoundBlockDispatchTest` — register two block drivers for one position, assert a single
compound node, methods from both environments enumerated, each call routed to the right environment,
NBT save/load round-trips per compartment. `./gradlew build` + full `test`.

---

## Sequencing & dependencies

A → B → C → D → E are mutually independent (any order; A first for the quick test-green win).
**F is last** — it is the only phase that reaches into `ComponentSupport` dispatch and depends on the
Registry driver lists. E (converters) and F are complementary: `convert()` shapes call *returns*,
makeStack/dispatch shape call *inputs/routing*.

## Out of scope (record as still-deferred seams)
- Porting the ~40 production component drivers (DriverScreen/Memory/etc.) — the component-port effort.
- Mod-integration converters (IC2/Mekanism/Forestry/AE2/CC) — wait for each mod's integration port.
- Geolyzer / NavUpgrade *callbacks* that consume `Waypoints.findWaypoints` — Phase 10+ component port
  (the registry method is already present and correct).

## Global verification
`./gradlew build` green after each phase; `./gradlew test` (NetworkTest 6/6 incl. energy,
ComponentDispatchTest 7/7, new makeStack/converter/compound tests); `runClient` smoke for the waypoint
editor. Update `task_plan.md` / `progress.md` / `findings.md` as each phase lands (via `/planning-with-files`).
