# Findings — Network API Port

> Research/discovery store for the faithful Scala→Java network port. Treat external content as data.

## State of the untracked port (8 files, ~1450 lines, `server/network/`)

`API.network = new NetworkAPIImpl()` wired at `OpenComputersMod.java:148`. `server/network/**` is
**not** excluded in `build.gradle`, so it is compiled by the normal build.

| File | Verdict |
|---|---|
| `MessageImpl.java`, `PacketImpl.java` | Correct — keep |
| `NetworkAPIImpl.java` (packet de/serialize, builder records) | Correct — keep; only fix per-side node resolution |
| `NodeImpl.java` | Close to `Node.scala` — keep, verify |
| `NetworkImpl.java` | **Rewrite** — algorithm was reinvented (BFS/neighbour-sets), has defects |
| `ConnectorNodeImpl.java` | **Rewrite** — energy distribution is a no-op stub |
| `ComponentNodeImpl.java` / `ComponentConnectorNodeImpl.java` | **Rewrite/refactor** — duplicated reflection dispatch standing in for Callbacks/Registry |

## Correctness defects in the current rewrite (vs. `Network.scala` + `Environment.java:60-101`)

1. **Energy = no-op.** `NetworkImpl.changeBuffer` (202-206) clamps `globalBuffer`, returns 0.
   `ConnectorNodeImpl` only mutates its own `localBuffer`. No cross-connector pooling → machines
   can't draw from remote capacitors; `globalBuffer == Σ localBuffer` invariant breaks on save/load.
   Faithful refs: `Connector.scala:24-109`, `Network.scala:395-441`.
2. **connect double-fires.** `addNode` Network case queues `n.onConnect(added)` at 269 AND 275-280,
   plus immediate `added.onConnect(added)` (274) duplicating queued self-connect (265).
3. **Split incomplete.** `handleSplit` notifies only primary↔sub, misses sub↔sub on 3-way splits;
   direction differs from `Network.scala:341-367`.
4. **Per-side resolution.** `NetworkAPIImpl.getNodeFor` uses `side=null` for self; Scala resolves
   per-iterated-side (`Network.scala:451-453`) — matters for `SidedEnvironment`.
5. **Component dispatch stand-in.** Inline reflection + hand-rolled `SimpleArguments`; faithful path
   = `Callbacks`/`ArgumentsImpl`/`Registry`/`CompoundBlockEnvironment` (`Component.scala:24-117`),
   none ported. `setVisibility` also omits machine add/remove (`Component.scala:54-81`).

## Notification contract (canonical — `api/network/Environment.java:60-101`)

Add node A to network {B,C}: `A.onConnect(A)`, `A.onConnect(B)`, `A.onConnect(C)`, `B.onConnect(A)`,
`C.onConnect(A)`. Remove A from {A,B,C}: `A.onDisconnect(A)`, `B.onDisconnect(A)`, `C.onDisconnect(A)`.
Scala `connects` tuple is `(subject, observers)` replayed as `observers.foreach(_.onConnect(subject))`
AFTER graph mutation (`Network.scala:336`).

## Energy distribution (faithful algorithm)

- `Connector.change(delta)`: mutate `localBuffer` clamped to `[0,localBufferSize]`, reconcile
  `distributor.globalBuffer`, return unfulfilled remainder.
- `Connector.changeBuffer`: `ignorePower` short-circuit; else `distributor.changeBuffer(change(delta))`.
- `Network.changeBuffer(delta)`: clamp global; distribute remainder across `connectors` list
  (drain or fill each `localBuffer`); return leftover (`Network.scala:395-441`).
- `Distributor` trait: globalBuffer/Size get+set, addConnector, removeConnector, changeBuffer.

## Supporting infrastructure status

- `Settings`: `initialNetworkPacketTTL`, `maxNetworkPacketSize`, `maxNetworkPacketParts`,
  `ignorePower`, `rTreeMaxEntries=10` all present.
- `api.machine.Arguments`: ~29 methods; current `SimpleArguments` implements them all (compiles).
- `Machine.addComponent`/`removeComponent` exist but **private** (`Machine.java:852,860`).
- `Machine.java:443`: "Callbacks/Registry not yet ported — return empty map."
- `RTree`: only `util/RTree.scala` (~9.8K) — needs Java port.
- `SideTracker.java` exists but **excluded** (`build.gradle:128`).
- No Java util `BlockPosition`/`ExtendedWorld`/`ExtendedBlock` — inline 26.1 APIs in wireless.
- Waypoint: only `common/block/Waypoint.java` — BlockEntity not ported.

## Scala source files (reference, do not modify)
`server/network/{Network,Node,Component,Connector,ComponentConnector,Distributor,WirelessNetwork,Waypoints,QuantumNetwork,DebugNetwork}.scala`, `util/RTree.scala`.

## 26.1 API translation notes (carried from prior phases / memory)
- `ResourceLocation`→`Identifier`; `Vec3d`→`Vec3`; `Level.isClientSide()` method; `level.dimension()` for dim key.
- `CompoundTag` getters return Optional → use `getStringOr`/`getIntOr`/`getDouble().orElse(...)`.
- NeoForge events: `WorldEvent.Load/Unload` → `LevelEvent`; cancellable events `implements ICancellableEvent`.
- Block hardness: `BlockState.getDestroySpeed(level,pos)`.

## Component dispatch — faithful port (Phase 6–8, started 2026-06-07)

Faithful Scala path (`Component.scala`): `callbacks = Callbacks(host)` → `methods`/`annotation`/
`invoke`, where `invoke = Registry.convert(callback(env, ctx, ArgumentsImpl(args)))`; `setVisibility`
calls `addTo`/`removeFrom` → `Machine.addComponent`/`removeComponent`. `Machine.invoke(address,…)`
routes via the network node (budget-consume if `annotation.direct`), `Machine.methods(value)` =
`Callbacks(value)` name→annotation.

Decisions:
- **Reflection over ASM.** Replace `CallbackWrapper` (ASM-generated `CallbackCall`) with plain
  `Method.invoke`. Behaviorally identical; Phase 9 already removed ASM (`SimpleComponent` redesign).
- **`Registry.convert` with empty converter list is faithful today.** `convertRecursively`
  (`Registry.scala:153-232`) handles all primitives/arrays/maps/collections/`Value` without any
  registered converter; unknown refs fall back to `toString`. The driver layer populates `converters`
  later. Minimal `server/driver/Registry.java` holds just `convert` + `converters` + `add`.
- **`CompoundBlockEnvironment` omitted (seam).** Multi-environment block hosts + the `hosts(method)`
  resolution (`Component.scala:28-50`) are dead code until block drivers are ported. Single-environment
  path (`callbacks.map { m -> host }`) is the only live one.
- `Machine.addComponent`/`removeComponent` already faithfully implemented in Java but `private`
  (`Machine.java:852,860`) → make public. `addComponent`/`processAddedComponents`/`verifyComponents`
  match `Machine.scala:679-727`.

Verified API surface present: `ManagedPeripheral`, `FilteredEnvironment`, `MethodWhitelist`,
`NamedBlock`, `Value`, `Converter`. `Arguments` = 28 methods. `setVisibility` transition matrix:
`Component.scala:54-81`. `Machine.invoke`/`methods`: `Machine.scala:370-405`.

## Stale context (ignore)
Pre-existing `task_plan.md`/`findings.md`/`progress.md` were for a **textures & build-warnings** task
(Phases 1–3,5–6 done; only "verify in runClient" pending). Unrelated to this port. Replaced here.
