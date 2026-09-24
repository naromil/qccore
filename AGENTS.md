# Repository Guidelines

QCCore is a **Fabric 1.20.1 mod** (mod id `qccore`, package root `com.qccore`) that ports the QCSimple desktop
pipeline into an in-world editor: players configure the nine QC Unit components from `<gameDir>/schematics`, build a
multi-layer layout with the crosshair (keybinds + a translucent overlay), then save it as a vanilla structure `.nbt`
or, in creative, build it into the world and re-align whole buildings to `(8x+a, 8y+b, 8z+c)`.

`QCCORE_IN_WORLD_QC_UNIT_EDITOR_PLAN.md` (repo root) is the **spec/design document of record**: ordered steps, the
1.20.1/yarn API facts the code relies on, every user-visible failure message and the verification procedure.
`README.md` is the short user-facing version; the class-level Javadoc on the `core`, `world`, `net` and `client`
types is the third source. Read all three before changing behaviour.

## Project Overview

- **Purpose:** replace the QCSimple JavaFX canvas with crosshair editing. There is no 2D editor screen; the only GUI
  is the four IO/config screens (`QcConfigScreen`, `QcFileBrowserScreen`, `QcSaveScreen`, `QcAlignScreen`).
- **Domain:** a **QC Unit** is a 9x9x9 block cell with a 7x7x7 interior. Units are spaced **8 blocks apart** on every
  axis, so neighbours *share* the boundary plane (local `0` of one unit is local `8` of the next); unit origins
  satisfy `x = 8*dx + anchorX` (same for `y`, `z`) and unit-local `(x, y, z)` lands at `absX = dx*8 + x`,
  `absY = dy*8 + y`, `absZ = dz*8 + z`. **Layers start at 1**: layer `dy` is the row based at `anchor + 8*dy`, and
  layer 1 is the player's own cell when the editor is entered.
- **Nine components** in `core/palette/`, fixed order `frameworkBlock, rowBlock, columnBlock, wallBlock,
  floorBlock` (block ids, seeded by `Defaults.applyTo` with `polished_deepslate`, `chiseled_deepslate`, `spruce_log`,
  `deepslate_tiles`, `spruce_planks`) then `innerWall, gate, outerWall` (all `deepslate_bricks`) and `innerColumn`
  (`stripped_spruce_log`). Seeded sizes `7x7x3`/`7x7x3`/`7x7x3`/`3x7x3`; `isValidSize` accepts inner wall/gate
  `7x7x1` or `7x7x3`, outer wall `7x7x1..7x7x3`, inner column `3x7x3`.
- **Vanilla `.nbt` only** - component files and saved layouts are regular structure files
  (`{DataVersion, size, palette, blocks, entities}`); there is no `.litematic` or `.schem` reader.
- **Byte-for-byte parity with QCSimple** is the pipeline's acceptance criterion: the export and import dumps are
  pinned against QCSimple's golden baselines, copied verbatim into `src/gametest/resources/golden/`.

## Architecture & Data Flow

Two representations, converted by the two entry points of `core/UnitBlockConverter`: the **layer model**
(`UnitLayers` = `TreeMap<Integer, LinkedHashMap<UnitPos, QcUnit>>`, ascending layer -> ordered unit position -> the
eight wall/gate flags) and the **block map** (`Map<BlockPos, BlockState>`, where gaps are *absent entries*, never air
blocks).

**Export** (layers -> components -> `.nbt`):

```
QcClientState.layers (client) / UnitLayers built by QcBuildService|QcAlignService (server)
  -> UnitBlockConverter.convertLayersToBlockMap(layers, palette, origin)
       -> UnitExporter.export(...)                    // walks layers x units
            ctx.beginUnit(dx, dy, dz, layerMap)
            for every 0..8 voxel: framework/row/column/wall/floor .exportVoxel(ctx, x, y, z, boundaryCount)
            then per unit:        innerColumn -> innerWall -> gate -> outerWall .exportUnit(ctx)
  -> StructureNbt.write(blockMap)                     // {DataVersion:3465, size, palette, blocks, entities:[]}
  -> StructureNbt.writeFile(tag, file)                // NbtIo.writeCompressed, GZIP root name ""
```

**Import** (`.nbt` -> block map -> layers, re-detecting the grid and the components):

```
QcSchematics.load(file) -> StructureNbt.readFile(file) -> StructureNbt.read(root) : Map<BlockPos, BlockState>
  -> UnitBlockConverter.convertBlockMapToLayers(blockMap, palette)
       -> UnitImporter.importLayers(blockMap, palette, UnitGrid.CENTER_X, UnitGrid.CENTER_Z)
            palette.frameworkBlock().detectGrid(blockMap)  // 9x9x9 shell scan -> GridOrigin
            scanUnits(ctx)                                 // UnitGridStats + one QcUnit per unit, layers keyed dy + 1
            columnBlock/rowBlock/floorBlock/wallBlock/outerWall .extract(ctx)
            InnerStructure.scanSides(ctx, handler)          // discovers walls + gates, then innerWall/gate extract
            innerColumn.extract(ctx)
            centerLayers(ctx, 16, 16)
  -> QcConfigScreen "Load layout…" replaces QcClientState.layers, adopting the palette the import detected
```

Server/client split:

- `QCCore.onInitialize()` registers `QcServerNetworking.register()` and `QcCommands.register()`;
  `QCCoreClient.onInitializeClient()` registers the keybinds, `QcInteraction::onClientTick`,
  `QcOverlayRenderer.INSTANCE` on `WorldRenderEvents.AFTER_TRANSLUCENT`, `QcHud::render` on `HudRenderCallback.EVENT`
  and the two `ClientPlayNetworking` receivers.
- `QcBuildService.build` validates, then clears the footprint, writes the layout and registers one `QcUnitIndex`
  record per unit. `QcAlignService.align` is creative-gated, prunes, BFS-walks the connected building over
  `origin.offset(dir, 8)` (cap 4096), clears the *old* footprint before rebuilding at the shifted anchor and re-keys
  the records. Both return `QcResult(ok, message)`, both run on the server thread - receivers hop with
  `player.server.execute(...)`, client receivers with `MinecraftClient.getInstance().execute(...)`.
- `QcWorldOps` is the **only world-write seam**: `clearFootprint` (9x9x9 cell plus a 2-block shell outside every
  exposed face), `placeBlocks`, `fits`, writing with `Block.NOTIFY_ALL | Block.FORCE_STATE` and skipping positions
  outside `World.isValid`/the height limits.
- `QcUnitIndex extends PersistentState`, state id `qccore_units`, holding
  `Map<Identifier dimension, LinkedHashMap<BlockPos origin, QcUnitRecord>>`. `QcServerNetworking` pushes every unit
  within 128 blocks of a player (nearest first, capped 4096) on join, after a successful build/align, and every 40
  ticks once the player moved 16 blocks.
- `QcClientState` is the static single-session state (`active`, `overlayVisible`, `anchor`, `alignA/B/C`, `layers`,
  `palette`, `nearbyUnits`, `dimension`, `message`), read by the renderer, HUD, screens and keybinds. `QcKeys` holds
  the eight keybinds (G editor, Y re-anchor, H duplicate layer, J save, K build, I components, U align, O overlay) in
  category `key.categories.qccore`. Input arrives through `client/mixin/MinecraftClientMixin`, which cancels
  `doAttack`/`doItemUse` at `HEAD` while a session is active and no screen is open, forwarding to
  `QcInteraction.click(...)`.

Four packets in `net/QcPackets`, all `FabricPacket` records whose `PacketType`s are built in static initialisers:
`QcBuildC2S` (`qccore:build`), `QcAlignC2S` (`qccore:align`), `QcIndexS2C` (`qccore:index`) and
`QcResultS2C` (`qccore:result`). Wire formats:

| Codec | Shape |
|---|---|
| `core/nbt/LayersCodec` | `{"layers":[{"y":<int>,"units":[{"x":<int>,"z":<int>,"w":<byte>,"g":<byte>},…]},…]}`, layers ascending |
| `core/nbt/PaletteCodec` | `{"framework","row","column","wall","floor":<String id>, "innerWall"\|"gate"\|"outerWall"\|"innerColumn":<structure tag, omitted when empty>}` |
| `world/QcUnitRecord` | `{"pos":[x,y,z],"w":<byte>,"g":<byte>}`; `QcIndexS2C` wraps a list of them as `{"units":[record, …]}` |
| `core/nbt/StructureNbt` | `{DataVersion,size,palette,blocks,entities}`; `size` and each `pos` are int **lists**, both list and int-array form are read |

Bit layout of `w`/`g`, identical in `LayersCodec`, `QcUnitRecord` and `QcOverlayRenderer`:
**bit0 = NORTH, bit1 = EAST, bit2 = SOUTH, bit3 = WEST** (`LayersCodecTest#bitLayoutIsNorthEastSouthWest` pins it).

## Key Directories

| Path | Purpose |
|---|---|
| `src/main/java/com/qccore/QCCore.java` | common `ModInitializer`: `MOD_ID`, `LOGGER`, `id(String)`, registers networking + `/qc` |
| `src/main/java/com/qccore/core/` | algorithm layer: `UnitLayers`/`QcUnit`/`UnitPos`/`UnitSide`, `SpatialUtils`, `Alignment`, converter/exporter/importer, both contexts |
| `src/main/java/com/qccore/core/nbt/` | `StructureNbt`, `LayersCodec`, `PaletteCodec` |
| `src/main/java/com/qccore/core/palette/` | the nine components, `UnitComponent`/`QcComponent`/`BlockComponent`, `BlockPalette`, `Defaults`, `InnerStructure` |
| `src/main/java/com/qccore/world/` | `QcBuildService`, `QcAlignService`, `QcWorldOps`, `QcUnitIndex`, `QcUnitRecord` |
| `src/main/java/com/qccore/net/` | `QcPackets`, `QcServerNetworking`, `QcCommands` |
| `src/main/resources/` | `fabric.mod.json`, `qccore.mixins.json` (empty `mixins` list), `assets/qccore/{icon.png,lang/en_us.json}` |
| `src/client/java/com/qccore/client/` | session, keybinds, crosshair interaction, overlay, HUD, four screens, `QcSchematics` |
| `src/client/java/com/qccore/client/mixin/` | `MinecraftClientMixin` (the only client mixin) |
| `src/client/resources/` | `qccore.client.mixins.json` (the only file) |
| `src/test/java/com/qccore/core/` | 14 JUnit tests over registry-free logic |
| `src/gametest/java/com/qccore/gametest/` | 7 gametests plus `TestFixtures` (fixtures + golden plumbing) |
| `src/gametest/resources/` | the `qccore-gametest` mod metadata and `golden/unit-{export,import}-baseline.txt` |

The `client` source set exists so client-only Minecraft classes are absent from the server; new client-only code under
`com.qccore` is mirrored under `src/client/java`.

## Development Commands

```bash
./gradlew build               # compile all source sets, test + runGameTest through check, then remapJar
./gradlew test                # the 14 JUnit tests: no game, no block registry
./gradlew runGameTest         # the 7 gametests in a real dev server world, ending "All 7 required tests passed :)"
./gradlew runClient           # dev client with the mod loaded (needs a display)
./gradlew runServer           # dev dedicated server; /qc works from its console
./gradlew compileClientJava   # compile the client source set only
./gradlew genSources          # decompile Minecraft for IDE navigation
./gradlew cleanTest test      # force test re-execution after a caching hit
```

Two jars matter and they are **not** the same file: `remapJar` writes the shippable `build/libs/qccore-1.0.0.jar`
(+ `-sources.jar`), while the plain `jar` task writes the un-remapped `build/devlibs/qccore-1.0.0-dev.jar`. `run/` is
the client/server working directory (`run/eula.txt`, `run/config/qccore.json`, `run/schematics/`, `run/world` or
`run/saves`); `build/run/gameTest/` is the gametest working directory (`world/`, `logs/`, `qccore-golden/` with the
dumps of a failing golden test). Loom's wiring adds `acceptGameTestEula`/`deleteGameTestRunDir`, but
`clearRunDirectory` does **not** recreate the game-test world between runs. CI (`.github/workflows/build.yml`) runs
`./gradlew build` on `ubuntu-24.04` with JDK 21 (Microsoft) on every push and PR and uploads `build/libs/`.

## Code Conventions & Common Patterns

- **Indentation is not uniform - match the file you touch.** `src/client/java/**` and
  `src/main/java/com/qccore/QCCore.java` are **tab**-indented; `src/main/java/com/qccore/{core,net,world}/**`,
  `src/test/java/**` and `src/gametest/java/**` are **4-space**-indented. No `.editorconfig`, checkstyle or spotless.
- **Naming:** `Qc` prefix on every domain type; `XxxCodec` for wire formats, `XxxService` for server mutations,
  `XxxOps` for world access, `XxxScreen` for GUI, components named after the building part. Getters are `getXxx`,
  records use accessor form, booleans mix `hasXxx` (walls, structures) and `isXxx` (`isGateN`, `isConfigured`);
  constants are `UPPER_SNAKE_CASE`.
- **Value types are records** (`UnitPos`, `QcStructure` + `QcStructure.Entry`, `QcResult`, `QcUnitRecord`,
  `GridOrigin`, `UnitGridStats`); mutable domain objects are plain classes (`QcUnit`, `UnitLayers`, both contexts,
  `BlockPalette`). `UnitComponent` is the extension seam: implement it, wire it into `BlockPalette`, call it from
  `UnitExporter`/`UnitImporter`.
- **Utility classes** are `public final class` + private constructor + `static` methods (`UnitExporter`,
  `UnitImporter`, `UnitBlockConverter`, `SpatialUtils`, `Alignment`, `StructureNbt`, `LayersCodec`, `PaletteCodec`,
  `QcWorldOps`, `QcBuildService`, `QcCommands`). `QcUnitIndex` is the one final class that is instantiated (it is a
  `PersistentState`), and `BlockPalette` is deliberately **not** a singleton - one instance per session or build.
- **Logging:** SLF4J only, `private static final Logger LOGGER = LoggerFactory.getLogger("qccore")` in the
  `core`/`world`/`net` classes (`QCCore` uses `getLogger(MOD_ID)`, the client `"qccore/client"`); messages start with
  `qccore: `, use `{}` placeholders, and a refusal is `warn` while a mutation is `info`.
- **Error handling has three deliberate kinds.** `QcResult(false, message)` for anything a player can trigger
  (creative checks, empty layout, the 4096-unit cap, `layer 0`, an unconfigured framework, a wrong component size);
  that wording is user-visible contract. `IllegalStateException`/`IllegalArgumentException` for programming errors
  (`"No framework block found"` in `FrameworkBlock.detectGrid`, `"missing 'size'"` in `StructureNbt.parseStructure`).
  `UncheckedIOException` for file IO. No exception crosses the network boundary: both server receivers catch
  `RuntimeException` and reply `QcResultS2C(false, "the build request was malformed")` / `"…align…"`.
- **Javadoc:** class-level Javadoc with `<p>` paragraphs and `{@link}` on every non-trivial type, short `/** … */` on
  public methods carrying a contract, numbered step comments inside long methods (`// 1. Guessing the framework block
  and the grid origin`). The bit layout and the `boundaryCount` contract live in the Javadoc they belong to.
- **Java style:** `options.release = 17`; records, `switch` expressions with `->`, pattern-matching `instanceof` and
  text blocks are the modern features used. No `var`, no `Optional`, no nullability framework beyond
  `org.jetbrains.annotations.Nullable` in the client source set.
- **No defensive copies, single-threaded by construction.** `UnitLayers.layer(dy)` and the contexts'
  `blockMap()`/`layerMap()` hand out the live collections, and mutating what a context exposed is how components
  write blocks. No locks, executors or concurrent collections; mutable static state is confined to `QcClientState`
  (client session) and `QcServerNetworking.LAST_PUSH`/`tick`.

### Non-obvious invariants (respect these when editing)

- **Geometry:** a cell is 9 blocks (`0..8`) per axis but units are spaced 8 apart, so the `8` plane of one unit is
  the `0` plane of its neighbour. `SpatialUtils.boundaryCount(x, y, z)` counts how many coordinates sit on `0`/`8`
  (3 = corner, 2 = shell face, <=1 = interior) out of a precomputed `int[9][9][9]`; components branch on it.
- **Layer indices:** builds require every layer key `>= 1`, the importer writes layers as `dy + 1`, and the overlay
  and HUD report the same indices the player sees.
- **Iteration order is part of the output.** `UnitLayers` uses `TreeMap` + `LinkedHashMap` so layers ascend and units
  keep insertion order - the importer was ported off QCSimple's `HashMap` edition on purpose. The per-unit component
  call order (`framework -> row -> column -> wall -> floor` per voxel, then `innerColumn -> innerWall -> gate ->
  outerWall`) is the export contract the golden baselines pin.
- **`put` overwrites, `putIfAbsent` keeps.** The split decides which component wins a contested voxel; turning a
  ported `putIfAbsent` into `put` silently changes the output.
- **Never re-pin a golden baseline to make a test pass.** A mismatch means the port diverged; fix the port. The
  baselines are byte-identical to QCSimple's (md5 `da86e79397e1162f8a5b4455d8cd1ef6` export,
  `6dfe109a2dd0e028f24b64134a906a86` import).
- **Wall/gate bit layout** is `bit0 NORTH, bit1 EAST, bit2 SOUTH, bit3 WEST` in `LayersCodec.bits/apply`,
  `QcUnitRecord.toNbt/fromNbt` and the renderer; the flags are persisted, so changing it corrupts saved worlds.
- **No `minecraft:stone` fallback**, deliberately diverging from QCSimple: `BlockComponent.state()` returns `null`
  for a blank or unknown id, that component exports nothing, `QcConfigScreen` paints the field red, and
  `QcBuildService` refuses with `framework block id '<id>' is not a block` (`<empty>` when blank).
- **`QcWorldOps` is the only class that calls `world.setBlockState`.** Writing blocks elsewhere breaks the "nothing is
  written before validation passes" guarantee and the shell logic that leaves neighbouring cells alone.
- **Mixin / source-set rules.** `qccore.mixins.json` is a common config with an empty `mixins` list, kept only
  because `fabric.mod.json` references it; `qccore.client.mixins.json` lists `MinecraftClientMixin` only and is
  marked `"environment": "client"`. A client-only mixin in the common config crashes a dedicated server.
- **`fabric-gametest` entrypoint rules.** The gametests live in a separate dev-only mod (`qccore-gametest`, own
  `src/gametest/resources/fabric.mod.json`). Do not set `modId = "qccore"` in `fabricApi.configureTests` (it collides
  with the `loom { mods { "qccore" } }` block), and never declare `fabric-gametest` in
  `src/main/resources/fabric.mod.json`: loom then puts `fabric-gametest-api-v1` on the dev `runClient`/`runServer`
  classpaths, where the test classes do not exist, and the run dies with
  `ClassNotFoundException: com.qccore.gametest.QcGameTests`. The published `fabric-api` jar ships no
  `fabric-gametest-api-v1`, so nothing resolves that entrypoint in production; the shipped
  `build/libs/qccore-1.0.0.jar` contains neither the test classes, nor the baselines, nor the entrypoint.

## Important Files

Paths are relative to `src/main/java/com/qccore/`, except the `client/` rows (`src/client/java/com/qccore/`).

| File | Role |
|---|---|
| `world/QcBuildService.java` | validates a build (creative, non-empty, <= 4096 units, `layer >= 1`, framework configured, component sizes, height fit) then applies it |
| `world/QcAlignService.java` | BFS over the connected building, `Alignment.nearestDelta` per axis, clears the old footprint, rebuilds at the shifted anchor, re-keys the index |
| `world/QcWorldOps.java` | the only world-write seam: `clearFootprint` (cell + 2-block shell), `placeBlocks`, `fits`, with `Block.NOTIFY_ALL \| Block.FORCE_STATE` |
| `world/QcUnitIndex.java` | the `qccore_units` `PersistentState`: per-dimension `BlockPos -> QcUnitRecord`; `prune` drops records whose origin block is air |
| `core/UnitExporter.java` | the export walk; owns the component call order and the `LinkedHashMap` block map |
| `core/UnitImporter.java` | grid detection, unit scan, the five single-block extractions, `scanSides`, re-centring on `UnitGrid.CENTER_X/CENTER_Z` |
| `core/UnitBlockConverter.java` | the two-line facade between the layer model and the block map |
| `core/nbt/StructureNbt.java` | structure tag read/write, `parseStructure`, `validateComponentFile`, `readFile`/`writeFile` (`UncheckedIOException`) |
| `core/palette/Defaults.java` + `InnerStructure.java` | built-in ids plus the four seeded structures (`simple`); the shared anchor/probe/rotation logic of inner wall and gate |
| `core/palette/BlockPalette.java` | the nine components in the exported order, `isConfigured()`, `applyDefaultConfig()` |
| `client/QcInteraction.java` | crosshair logic: `enterEditorMode`, `reanchor`, `adoptNearbyLattice`, `cursor`, `click`, keybind pump `onClientTick` |
| `client/QcClientState.java` | the static client session (layers, palette, anchor, lattice, `nearbyUnits`, HUD message) and `reset()` |
| `client/QcOverlayRenderer.java` | the overlay on `AFTER_TRANSLUCENT` via `getBuffer(RenderLayer.getLines())` + `WorldRenderer.drawBox`; cells, plates, cursor, markers |
| `client/mixin/MinecraftClientMixin.java` | cancels `doAttack`/`doItemUse` at `HEAD` while a session is active - without it RMB/LMB still edit the world |
| `build.gradle` | the two test blocks (`fabricApi { configureTests { modId = "qccore-gametest" … } }`, `test { useJUnitPlatform() }` + JUnit BOM), `splitEnvironmentSourceSets()` |
| both `fabric.mod.json` | the shipped mod (main/client entrypoints, both mixin configs) versus the dev-only test mod with the four `fabric-gametest` entrypoints |
| `src/gametest/resources/golden/*.txt` | the export (7254 lines) and import (25 lines) baselines copied verbatim from QCSimple; parity is measured against these |

## Testing & QA

Two suites, split by whether the code needs the block registry: a plain JUnit JVM cannot bootstrap the registries
here (`IllegalAccessError` from yarn's split package inside `net.minecraft.registry.SimpleRegistry`), so anything
touching `Registries.BLOCK` is a gametest instead.

| Suite | Files | Covers |
|---|---|---|
| JUnit, 14 tests | `AlignmentTest`, `IndexNbtTest`, `LayersCodecTest` | lattice math and residues; the index NBT round trip plus malformed state; the `w`/`g` bit layout and a playlist round trip |
| Gametests, 7 tests | `QcGameTests`, `StructureExportGoldenTest`, `StructureImportGoldenTest`, `PaletteCodecTest` | golden export/import parity; a build registers records, an align moves the building, layer 0 and non-creative are refused |

Golden mechanics: baselines are **classpath resources** (`/golden/unit-export-baseline.txt`) inside the test mod,
so they never reach the shipped jar; comparison is exact, order-sensitive line equality after CRLF/CR -> LF
normalisation; a failing run leaves the dump at `build/run/gameTest/qccore-golden/export-actual.txt` (and
`import-actual.txt`) for a plain shell `diff`; there is **no regenerate/update flag**; the export dump is sorted
explicitly and the import dump renders extracted structures as size + path only, because the palette ordering inside
an extracted tag is not deterministic. The game-test world is not recreated between runs, so
`QcGameTests.clearIndexArea` clears the test area's index records first. A JUnit XML report comes from
`-Dfabric-api.gametest.report-file=<file>`.

**Not covered:** the four screens, `QcInteraction`, `QcOverlayRenderer`, `QcHud`, `QcSchematics`, the push loop, the
`/qc` output and the input mixin have no automated tests, and `QcGameTests` does not cover erase or wall-flag editing.

**Verified project state (evidence behind this file):** export and import dumps byte-identical to the baselines,
`./gradlew test` 14/14, `./gradlew runGameTest` 7/7, plus a client smoke in a creative world (editor HUD verbatim,
place/erase, the component screen rejecting `not_a_block`, `J` producing a valid tag - `DataVersion 3465`, size
`13x13x13`, 750 blocks, 6 palette entries - and `K` reporting `built 1 QC unit(s) on 1 layer(s) at -2, 112, -4`) and a
headless dedicated-server smoke (`qc info` -> `0 QC unit(s) in minecraft:overworld` on a fresh world, and
`1 QC unit(s)` after loading a world the client had built into, proving the index survives a restart.
The test/gametest counts and the `All 7 required tests passed :)` line were re-checked against `build/test-results/`
and `build/run/gameTest/logs/latest.log` while writing this file; the two smoke transcripts themselves were not
re-executed. `[INFERENCE from the code; not executed]`

**Manual checklist (human required):** wall and gate plates on a shared face (blue, then green with Ctrl), a second
layer placed 8 blocks up, `H` duplicating a layer, `Y` re-anchoring, the `U` align screen moving a placed building,
`O` toggling the overlay, and the config screen's `Choose…` / `Load layout…` flows including a size-mismatch
rejection.

**Reproducible smoke recipes:** for the server, put `eula=true` in `run/eula.txt`, start `./gradlew runServer` as a
background service (ready when the log contains `Done (`) and drive its stdin through a FIFO with `qc info`,
`qc forget 64` and `stop` (`/qc` needs permission level 2, which the console has). For the client, launch
`./gradlew runClient` and drive it with `xdotool` against the display loom allocates - read `DISPLAY` from the game
process' `/proc/<pid>/environ`, since `xvfb-run` gives it a private one - and keep a screenshot as visual proof.

## Runtime/Tooling Preferences

- **JDK 21 is the build machine, Java 17 is the target bytecode.** `gradle.properties` holds every version
  (`minecraft_version=1.20.1`, `yarn_mappings=1.20.1+build.10`, `loader_version=0.19.5`, `loom_version=1.17.21`,
  `fabric_api_version=0.92.12+1.20.1`, `version=1.0.0`, `group=com.qccore`, `archives_base_name=qccore`) and is the
  single source of truth; `build.gradle` only reads `project.*`. There is **no `toolchain` block** - the bytecode
  level comes from `tasks.withType(JavaCompile) { it.options.release = 17 }`.
- **Build with the wrapper:** `./gradlew` (Gradle **9.5.1**, `-bin` distribution, wrapper jar committed,
  `distributionSha256Sum` pinned). `org.gradle.jvmargs=-Xmx2G`, `org.gradle.parallel=true` and
  `org.gradle.configuration-cache=false` (loom is not configuration-cache clean).
- **Loom essentials:** `splitEnvironmentSourceSets()` plus a `loom { mods { "qccore" { sourceSet main; sourceSet
  client } } }` block; `fabricApi { configureTests { … } }` creates the `gametest` source set and the `runGameTest`
  task; `withSourcesJar()` explains the `-sources.jar`. Remapped dependency artifacts land in `.gradle/loom-cache/`;
  `build/devlibs/` holds the un-remapped dev jar.
- **Display:** `DISPLAY=:1` is exported in this environment and `xvfb-run`/`xdotool` are on `PATH`, so `runClient` can
  be launched and driven headlessly; no Xvfb process runs by default, so a display has to be started for a client
  smoke.
- **Gitignored/disposable:** `build/`, `run/`, `logs/`, `.gradle/`, `out/`, `classes/`, `.codebase-index/`, IDE
  files (`*.ipr`, `*.iws`), `hs_err_*.log`, `*.hprof`, `*.jfr`. Never edit anything under `build/`;
  `src/gametest/resources/golden/` is the opposite and is checked in on purpose.
