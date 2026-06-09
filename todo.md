# TODO

## Custom baked models not ported

`ModelInitialization.scala`, `CableModel`, `RobotModel`, and `NetSplitterModel` use the old
1.12.2 `ModelBakeEvent`/`ModelLoader` API. Scala compilation is disabled, so these are dead
code. The blocks that relied on them (Cable, Robot, NetSplitter) will show a fallback/missing
model. These need NeoForge 1.21 equivalents using `ModelEvent.RegisterGeometryLoaders`.

## `ClientSetup.java` has no model event listeners

Only `RegisterMenuScreensEvent` and `RegisterRenderers` are wired. Any custom model geometry
loaders or `BakingCompleted` callbacks need to be added here.

## `blockstates/case1.json` references `facing` and `running` properties

These need to match the actual `BlockStateProperties` on the `Case` block class. If mismatched,
the case blocks will use the wrong model variant.
