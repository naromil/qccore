# Repository Guidelines

QCCore is a **Minecraft 1.20.1 mod for Fabric and Forge** (mod id `qccore`, package root `com.qccore`), built
from one loader-neutral code base in Mojang's names (see *Multi-loader layout* below). It ports the QCSimple desktop
pipeline into an in-world editor: players configure the nine QC Unit components from `<gameDir>/schematics`, build a
multi-layer **plan** with the crosshair (Effortless-Building-style tools and shapes, keybinds, wireframes and ghost
blocks), then export it as a vanilla structure `.nbt` or, in creative, apply it to the world in one undoable step and
re-align whole buildings to `(8x+a, 8y+b, 8z+c)`. The QC building rules come from the user's `naromil/worldedit`
repo (`builds/QCSYSTEM.md`, the `quarterchunk-buildings` skill, `quarterchunk/qckit/`); the road template is a port
of qckit's `road.py`.

`QCCORE_IN_WORLD_QC_UNIT_EDITOR_PLAN.md` (repo root) is the **spec/design document of record**: ordered steps, the
1.20.1 API facts the code relies on (in Yarn names; the code now uses Mojang names), every user-visible failure message and the verification procedure.
`README.md` is the short user-facing version; the class-level Javadoc on the `core`, `world`, `net` and `client`
types is the third source. Read all three before changing behaviour.

## Project Overview

- **Purpose:** replace the QCSimple JavaFX canvas with crosshair editing. There is no 2D editor screen; the GUI is
  the IO/config screens (`QcConfigScreen`, `QcFileBrowserScreen`, `QcSaveScreen`, `QcAlignScreen`) plus the planning
  screens (`QcToolsScreen` hub on B, `QcTemplateScreen`, `QcKitScreen`). All of them and `QcHud` share the flat
  dark look of `client/QcUi` (panels, `Chip` buttons with a live selected state, `Stepper` number widgets,
  status pill, tool colours/icons); the tools screen lays out all choices at once and must fit a 427x240 GUI.
- **Plan model (`core/plan/`):** `QcPlan` = units (`UnitLayers`) + per-unit `ComponentMask` (absent = all nine) +
  cleared cells (`clearMargin` 0..4, optional `ground` fill of the floor plane) + road cells naming a `RoadStyle`
  (lanes 1..4, joint, over, post, medallion, centre; ids may carry states, parsed by `BlockIds`). `BuildShape`
  (single/line/floor/ring/box/hollow box) and `Modifiers` (array then mirror) produce `Cell(x, layer, z)` sets;
  `QcPlan.transformed/normalized/merge` rotate/mirror/move plans with their flags (templates). `PlanCompiler` is the
  one plan -> blocks conversion (server apply, client ghost preview, `.nbt` export): masked `UnitExporter.export`,
  then `RoadKit` per layer (gives way to unit blocks, `putIfAbsent`), then clears + 7 blocks of road headroom as air,
  then ground. `PlanCodec` is the wire/file format. All of `core/plan` except `BlockIds`/`PlanCompiler` is
  registry-free and JUnit-tested.
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
  pinned against QCSimple's golden baselines, copied verbatim into `fabric/src/gametest/resources/golden/`.

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

### Multi-loader layout

Three Gradle projects, all built with Architectury Loom (`dev.architectury.loom`, the Loom fork that builds Fabric
and Forge) and **Mojang's official mappings** (`loom.officialMojangMappings()`; the Yarn names of the original
Fabric-only code were migrated with Loom's `migrateMappings`):

| Project | Contents |
|---|---|
| `common/` | the whole mod except the loader glue: `core`, `world`, `net`, `client`, `platform`, the assets, the JUnit tests. **Must not import `net.fabricmc.*` or `net.minecraftforge.*`.** Not shipped on its own (`remapJar` disabled). |
| `fabric/` | `com.qccore.fabric`: `QCCoreFabric` (`ModInitializer`), `QCCoreFabricClient`, `mixin/MinecraftClientMixin`; `fabric.mod.json`, `qccore.client.mixins.json`; the gametest mod under `src/gametest`. |
| `forge/` | `com.qccore.forge`: `QCCoreForge` (`@Mod("qccore")`), `QCCoreForgeClient`, `QcForgeNetwork`; `META-INF/mods.toml`, `pack.mcmeta`. `forge/gradle.properties` sets `loom.platform=forge`. |

The root `build.gradle` wires the loader projects the same way: `implementation project(path: ':common',
configuration: 'namedElements')`, a `loom.mods.qccore` group holding their own `main` plus `common`'s `main` (one mod in
dev runs), and `jar { from(common output) }`, so each `remapJar` ships one self-contained mod remapped to its
loader's runtime names (intermediary for Fabric, SRG for Forge).

Seams, in both directions:

- **common -> loader:** `com.qccore.platform.QcPlatform` (`gameDir()`, `sendToServer(packet)`,
  `sendToPlayer(player, packet)`), whose `Services` each loader entry point installs first thing.
- **loader -> common:** plain hooks. Server: `QcServerNetworking.handle(player, packet)` (game thread),
  `onJoin`, `onLeave`, `onServerTick(server)`; `QcCommands.register(dispatcher)`. Client: `QCCoreClient.handle(packet)`
  (client thread), `QcKeys.create()` (the fourteen `KeyMapping`s to register), `QcInteraction.onClientTick(client)`,
  `QcOverlayRenderer.render(poseStack, camera)` after the translucent terrain, `QcHud.render(graphics, partialTick)`,
  `QcInteraction.click(useKey)` for mouse input.

| Concern | Fabric (`fabric/`) | Forge (`forge/`) |
|---|---|---|
| packets | one raw play channel per packet id (`ServerPlayNetworking`/`ClientPlayNetworking` with `PacketByteBufs`), payload = the record's `write`, so the wire format is the pre-port one | one `SimpleChannel` `qccore:main`, protocol `"2"`, discriminators in `QcPackets.C2S` then `S2C` order, `consumerMainThread` |
| join / leave / tick | `ServerPlayConnectionEvents.JOIN/DISCONNECT`, `ServerTickEvents.END_SERVER_TICK` | `PlayerLoggedInEvent`/`PlayerLoggedOutEvent`, `TickEvent.ServerTickEvent` (`END`) |
| commands | `CommandRegistrationCallback` | `RegisterCommandsEvent` |
| keys / tick | `KeyBindingHelper`, `ClientTickEvents.END_CLIENT_TICK` | `RegisterKeyMappingsEvent` (mod bus), `TickEvent.ClientTickEvent` (`END`) |
| world overlay / HUD | `WorldRenderEvents.AFTER_TRANSLUCENT`, `HudRenderCallback` | `RenderLevelStageEvent` at `AFTER_TRANSLUCENT_BLOCKS`, `RegisterGuiOverlaysEvent.registerAboveAll("qccore_hud")` |
| mouse input | `MinecraftClientMixin` cancels `startAttack`/`startUseItem` at `HEAD` | no mixin: attack presses are drained from `keyAttack.consumeClick()` at `ClientTickEvent` `START` (one click per press), `InputEvent.InteractionKeyMappingTriggered` turns use (main hand) into `click(true)` and cancels every use/attack event (held attack never mines) |
| game dir | `FabricLoader.getGameDir()` | `FMLPaths.GAMEDIR` |

Known behavioural difference: holding RMB repeats `click(true)` every tick on Fabric (the mixin cancels before
vanilla sets `rightClickDelay`) but every 4 ticks on Forge (Forge fires the event after setting it). Shapes with more
than one point debounce held clicks either way.

Server/client split:

- The loader entry points (`QCCoreFabric`/`QCCoreForge`, `QCCoreFabricClient`/`QCCoreForgeClient`) register the
  hooks above; `QCCore` (common) only holds `MOD_ID`, `LOGGER`, `id(String)`.
- `QcBuildService.build` validates, then clears the footprint, writes the layout and registers one `QcUnitIndex`
  record per unit. `QcAlignService.align` is creative-gated, prunes, BFS-walks the connected building over
  `origin.offset(dir, 8)` (cap 4096), clears the *old* footprint before rebuilding at the shifted anchor and re-keys
  the records. Both return `QcResult(ok, message)`, both run on the server thread - the loader glue hops onto it
  (`server.execute(...)` on Fabric, `consumerMainThread` on Forge), and onto the client thread for S2C packets.
- `QcWorldOps` is the **only world-write seam**: `clearFootprint` (9x9x9 cell plus a 2-block shell outside every
  exposed face), `placeBlocks`, `clearBlocks`, `fillAir` (only replaceable blocks), `restore`, `fits`, writing with
  `Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE` and skipping positions outside `Level.isInWorldBounds`/the height limits. Every
  write method has an overload taking an undo map (first previous state per position).
- `QcPlanService.apply` (creative, `validate`, compile, fit check) writes clears -> footprint of **full-mask units
  only** (partial units add to what stands) -> blocks -> ground, registers index records and pushes a
  `QcUndoService` entry (per player, in memory, 10 entries / 1.5M blocks, block-entity contents not restored;
  `/qc undo` or `QcUndoC2S`). Undo swaps an entry with the world, so its inverse lands on a per-player redo stack
  (`/qc redo` or `QcRedoC2S`, same limits); a new apply drops the redo stack. Plans travel as gzip NBT split into
  `QcPlanC2S` chunks of 30000 bytes (vanilla caps a C2S payload at 32767), reassembled per player in
  `QcServerNetworking`: the first chunk fixes the transfer's total (<= 256 chunks) and is refused outside creative
  before anything is buffered, and the reassembled bytes are inflated under a 32 MiB `NbtAccounter` budget
  (`QcServerNetworking.decode`), never with the unbounded `NbtIo.readCompressed`.
- `QcUnitIndex extends SavedData`, state id `qccore_units`, holding
  `Map<ResourceLocation dimension, LinkedHashMap<BlockPos origin, QcUnitRecord>>`. `QcServerNetworking` pushes every unit
  within 128 blocks of a player (nearest first, capped 4096) on join, after a successful build/align, and every 40
  ticks once the player moved 16 blocks.
- `QcClientState` is the static single-session state (`active`, `overlayVisible`, `anchor`, `alignA/B/C`, `layers`,
  `palette`, `nearbyUnits`, `dimension`, `message`) plus the planning state (`plan`, `planVersion`, `history`, `tool`,
  `shape`, `points`, `componentMask`, `modifiers`, `roadStyle(s)`, `template*`, `blockPreview`), read by the renderer,
  HUD, screens and keybinds. Every plan edit goes through `QcInteraction.edit` (snapshot, run, push to `QcHistory`
  only if it changed, bump `planVersion`). `QcKeys` holds fourteen keybinds (G editor, Y re-anchor, H duplicate
  layer, J export, K apply, I components, U align, O overlay, B tools, V shape, N tool, Z undo/Shift redo, R turn
  template, M ghosts) in category `key.categories.qccore`. Input arrives through the loader glue (Fabric `MinecraftClientMixin`,
  Forge input events, see the table above) while a session is active and no screen is open, forwarding to
  `QcInteraction.click(...)`.

Seven packets in `net/QcPackets`, all loader-neutral `QcPacket` records (`write(FriendlyByteBuf)` plus a static
`Type` = channel id, class, reader), listed in `QcPackets.C2S`/`S2C` for the loader glue to register:
`QcBuildC2S` (`qccore:build`, kept for compatibility; the K key now sends a plan), `QcAlignC2S` (`qccore:align`),
`QcPlanC2S` (`qccore:plan`), `QcUndoC2S` (`qccore:undo`), `QcRedoC2S` (`qccore:redo`), `QcIndexS2C` (`qccore:index`) and
`QcResultS2C` (`qccore:result`). Wire formats:

| Codec | Shape |
|---|---|
| `core/nbt/LayersCodec` | `{"layers":[{"y":<int>,"units":[{"x":<int>,"z":<int>,"w":<byte>,"g":<byte>},…]},…]}`, layers ascending |
| `core/nbt/PaletteCodec` | `{"framework","row","column","wall","floor":<String id>, "innerWall"\|"gate"\|"outerWall"\|"innerColumn":<structure tag, omitted when empty>}` |
| `world/QcUnitRecord` | `{"pos":[x,y,z],"w":<byte>,"g":<byte>}`; `QcIndexS2C` wraps a list of them as `{"units":[record, …]}` |
| `core/nbt/PlanCodec` | `{"units":LayersCodec,"masks":[{x,y,z,m}],"clears":[{x,y,z}],"clearMargin","ground","roads":[{x,y,z,s}],"roadStyles":{name:style}}`, `y` = layer |
| `QcPlanC2S` request | gzip of `{"action":"apply","anchor":[x,y,z],"plan":PlanCodec,"palette":PaletteCodec}` |
| templates / kits | `<gameDir>/qccore/templates/<name>.qct.nbt` = gzip `{"qcTemplate":1,"plan","palette"}` (plan normalised to min cell `(0,1,0)`); `<gameDir>/qccore/kits/<name>.json` = palette ids + structure file names, road styles, ground |
| `core/nbt/StructureNbt` | `{DataVersion,size,palette,blocks,entities}`; `size` and each `pos` are int **lists**, both list and int-array form are read |

Bit layout of `w`/`g`, identical in `LayersCodec`, `QcUnitRecord` and `QcOverlayRenderer`:
**bit0 = NORTH, bit1 = EAST, bit2 = SOUTH, bit3 = WEST** (`LayersCodecTest#bitLayoutIsNorthEastSouthWest` pins it).

## Key Directories

| Path | Purpose |
|---|---|
| `common/src/main/java/com/qccore/QCCore.java` | shared constants: `MOD_ID`, `LOGGER`, `id(String)` |
| `common/src/main/java/com/qccore/platform/` | `QcPlatform`: the services the loaders install |
| `common/src/main/java/com/qccore/core/` | algorithm layer: `UnitLayers`/`QcUnit`/`UnitPos`/`UnitSide`, `SpatialUtils`, `Alignment`, converter/exporter/importer, both contexts |
| `common/src/main/java/com/qccore/core/nbt/` | `StructureNbt`, `LayersCodec`, `PaletteCodec` |
| `common/src/main/java/com/qccore/core/palette/` | the nine components, `UnitComponent`/`QcComponent`/`BlockComponent`, `BlockPalette`, `Defaults`, `InnerStructure` |
| `common/src/main/java/com/qccore/world/` | `QcBuildService`, `QcAlignService`, `QcWorldOps`, `QcUnitIndex`, `QcUnitRecord` |
| `common/src/main/java/com/qccore/net/` | `QcPackets`, `QcServerNetworking`, `QcCommands` |
| `common/src/main/resources/` | `assets/qccore/{icon.png,lang/en_us.json}` |
| `fabric/src/main/` | `com.qccore.fabric` glue + `fabric.mod.json`, `qccore.client.mixins.json` |
| `forge/src/main/` | `com.qccore.forge` glue + `META-INF/mods.toml`, `pack.mcmeta` |
| `common/src/main/java/com/qccore/client/` | session, keybinds, crosshair interaction, overlay, HUD, the screens, `QcSchematics` |
| `common/src/main/java/com/qccore/core/plan/` | the plan model: `QcPlan`, `Cell`, `BuildShape`, `Modifiers`, `ComponentMask`, `Flags`, `RoadStyle`, `RoadKit`, `BlockIds`, `PlanCompiler` |
| `common/src/test/java/com/qccore/core/` | JUnit tests over registry-free logic (`plan/` holds the shape, plan and road tests) |
| `common/src/test/java/com/qccore/net/` | `PlanRequestDecodeTest`: the bounded inflate of a plan request |
| `common/src/test/java/com/qccore/client/` | `QcHistoryTest`, `QcKitsRoadJsonTest`: the registry-free client helpers (plan undo/redo, kit road JSON) |
| `fabric/src/gametest/java/com/qccore/gametest/` | 9 gametests plus `TestFixtures` (fixtures + golden plumbing) |
| `fabric/src/gametest/resources/` | the `qccore-gametest` mod metadata and `golden/unit-{export,import}-baseline.txt` |

There is no separate client source set any more: client classes live in `common` next to the server code and are
simply never loaded on a dedicated server (nothing server-side references `com.qccore.client`; the Forge glue reaches
it only behind `FMLEnvironment.dist == Dist.CLIENT` / `DistExecutor`, the Fabric glue only from the client entry point).

## Development Commands

```bash
./gradlew build                 # all projects: :common:test, :fabric:runGameTest (through check), both remapJars
./gradlew :common:test          # the 37 JUnit tests: no game, no block registry
./gradlew :fabric:runGameTest   # the 9 gametests in a real dev server world, ending "All 9 required tests passed :)"
./gradlew :fabric:runClient     # dev Fabric client (needs a display); :forge:runClient for Forge
./gradlew :fabric:runServer     # dev dedicated server; /qc works from its console; :forge:runServer for Forge
./gradlew :forge:compileJava    # compile the Forge glue (and common) only
./gradlew genSources            # decompile Minecraft for IDE navigation
./gradlew :common:cleanTest :common:test  # force test re-execution after a caching hit
```

The shippable jars are the `remapJar` outputs `fabric/build/libs/qccore-fabric-1.0.0.jar` and
`forge/build/libs/qccore-forge-1.0.0.jar` (+ `-sources.jar`, which include `common`'s sources); the plain `jar` tasks
write the un-remapped dev jars under `<project>/build/devlibs/`. `fabric/run/` and `forge/run/` are the client/server
working directories (`eula.txt`, `schematics/`, `world` or `saves`); `fabric/build/run/gameTest/` is the gametest
working directory (`world/`, `logs/`, `qccore-golden/` with the
dumps of a failing golden test). Loom's wiring adds `acceptGameTestEula`/`deleteGameTestRunDir`, but
`clearRunDirectory` does **not** recreate the game-test world between runs. CI (`.github/workflows/build.yml`) runs
`./gradlew build` on `ubuntu-24.04` with JDK 21 (Microsoft) and uploads `fabric/build/libs/` and `forge/build/libs/`.

## Code Conventions & Common Patterns

- **Indentation is not uniform - match the file you touch.** `common/.../com/qccore/client/**`,
  `common/.../com/qccore/QCCore.java`, `fabric/src/main/**` and `forge/src/main/**` are **tab**-indented;
  `common/.../com/qccore/{core,net,world,platform}/**`, `common/src/test/java/**` and `fabric/src/gametest/java/**` are
  **4-space**-indented. No `.editorconfig`, checkstyle or spotless.
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
  `SavedData`), and `BlockPalette` is deliberately **not** a singleton - one instance per session or build.
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
- **Component masks must not change the plain export.** `UnitExporter.export(layers, palette, origin)` delegates to
  the masked overload with `ComponentMask.ALL`, which calls every component in the same order; the golden tests pin it.
- **Roads own only the 7-wide interior plus arm mouths** (QCSYSTEM.md): never the four corner posts or a closed
  plane, and they give way to unit blocks. Keep `RoadKit` a faithful port of qckit `road.py`; there are no preset road
  styles or kits, by the user's rule ("no default blocks").
- **Never re-pin a golden baseline to make a test pass.** A mismatch means the port diverged; fix the port. The
  baselines are byte-identical to QCSimple's (md5 `da86e79397e1162f8a5b4455d8cd1ef6` export,
  `6dfe109a2dd0e028f24b64134a906a86` import).
- **Wall/gate bit layout** is `bit0 NORTH, bit1 EAST, bit2 SOUTH, bit3 WEST` in `LayersCodec.bits/apply`,
  `QcUnitRecord.toNbt/fromNbt` and the renderer; the flags are persisted, so changing it corrupts saved worlds.
- **No `minecraft:stone` fallback**, deliberately diverging from QCSimple: `BlockComponent.state()` returns `null`
  for a blank or unknown id, that component exports nothing, `QcConfigScreen` paints the field red, and
  `QcBuildService` refuses with `framework block id '<id>' is not a block` (`<empty>` when blank).
- **`QcWorldOps` is the only class that calls `level.setBlock`.** Writing blocks elsewhere breaks the "nothing is
  written before validation passes" guarantee and the shell logic that leaves neighbouring cells alone.
- **Loader rules.** Nothing in `common` imports a loader API; a new loader service goes into `QcPlatform.Services`
  and both implementations. The only mixin is Fabric's `MinecraftClientMixin` in `qccore.client.mixins.json`
  (`"environment": "client"`); Loom remaps its targets in place, so there is no refmap. Forge uses events instead -
  keep it mixin-free. A packet added to `QcPackets` must be appended to `C2S`/`S2C` (Forge discriminators follow
  that order; bump `QcForgeNetwork.PROTOCOL` when it changes).
- **`fabric-gametest` entrypoint rules.** The gametests live in a separate dev-only mod (`qccore-gametest`, own
  `fabric/src/gametest/resources/fabric.mod.json`), configured by `fabricApi.configureTests` in `fabric/build.gradle`. Do not set `modId = "qccore"` in `fabricApi.configureTests` (it collides
  with the `loom { mods { "qccore" } }` block), and never declare `fabric-gametest` in
  `fabric/src/main/resources/fabric.mod.json`: loom then puts `fabric-gametest-api-v1` on the dev `runClient`/`runServer`
  classpaths, where the test classes do not exist, and the run dies with
  `ClassNotFoundException: com.qccore.gametest.QcGameTests`. The published `fabric-api` jar ships no
  `fabric-gametest-api-v1`, so nothing resolves that entrypoint in production; the shipped
  `fabric/build/libs/qccore-fabric-1.0.0.jar` contains neither the test classes, nor the baselines, nor the entrypoint.

## Important Files

Paths are relative to `common/src/main/java/com/qccore/`, except where a project is named.

| File | Role |
|---|---|
| `world/QcBuildService.java` | validates a build (creative, non-empty, <= 4096 units, `layer >= 1`, framework configured, component sizes, height fit) then applies it |
| `world/QcAlignService.java` | BFS over the connected building, `Alignment.nearestDelta` per axis, clears the old footprint, rebuilds at the shifted anchor, re-keys the index |
| `world/QcWorldOps.java` | the only world-write seam: `clearFootprint` (cell + 2-block shell), `placeBlocks`, `fits`, with `Block.UPDATE_ALL \| Block.UPDATE_KNOWN_SHAPE` |
| `world/QcUnitIndex.java` | the `qccore_units` `SavedData`: per-dimension `BlockPos -> QcUnitRecord`; `prune` drops records whose origin block is air |
| `core/UnitExporter.java` | the export walk; owns the component call order and the `LinkedHashMap` block map |
| `core/UnitImporter.java` | grid detection, unit scan, the five single-block extractions, `scanSides`, re-centring on `UnitGrid.CENTER_X/CENTER_Z` |
| `core/UnitBlockConverter.java` | the two-line facade between the layer model and the block map |
| `core/nbt/StructureNbt.java` | structure tag read/write, `parseStructure`, `validateComponentFile`, `readFile`/`writeFile` (`UncheckedIOException`) |
| `core/palette/Defaults.java` + `InnerStructure.java` | built-in ids plus the four seeded structures (`simple`); the shared anchor/probe/rotation logic of inner wall and gate |
| `core/palette/BlockPalette.java` | the nine components in the exported order, `isConfigured()`, `applyDefaultConfig()` |
| `client/QcInteraction.java` | crosshair logic: `enterEditorMode`, `reanchor`, `adoptNearbyLattice`, `cursor`, `click`, keybind pump `onClientTick` |
| `client/QcClientState.java` | the static client session (layers, palette, anchor, lattice, `nearbyUnits`, HUD message) and `reset()` |
| `client/QcUi.java` | the shared screen/HUD toolkit: theme colours, `panel`, `titleBar`, `status`, `Chip`, `Stepper`, tool colours, icons, tooltips |
| `client/QcHud.java` | the editor HUD panel (Left Alt swaps it for the controls list read from the live key bindings) and the fading message toast above the hotbar |
| `client/QcOverlayRenderer.java` | the overlay after the translucent pass via `getBuffer(RenderType.lines())` + `LevelRenderer.renderLineBox`, flushed by itself; cells, plates, cursor, markers |
| `platform/QcPlatform.java` | the common -> loader services (game dir, send packet) |
| `fabric/.../fabric/mixin/MinecraftClientMixin.java` | cancels `startAttack`/`startUseItem` at `HEAD` while a session is active - without it RMB/LMB still edit the world |
| `forge/.../forge/QCCoreForgeClient.java` | the Forge equivalent without a mixin (attack drain at tick start + `InteractionKeyMappingTriggered`), keys, overlay, HUD |
| `forge/.../forge/QcForgeNetwork.java` | the `SimpleChannel` carrying `QcPackets` |
| `build.gradle` (root) | Architectury Loom + Mojang mappings for every project; bundles `common` into the `fabric`/`forge` jars |
| `common/build.gradle`, `fabric/build.gradle`, `forge/build.gradle` | JUnit (`test { useJUnitPlatform() }` + BOM); `fabricApi { configureTests { modId = "qccore-gametest" … } }` + Fabric API; the Forge dependency |
| both `fabric.mod.json` | the shipped Fabric mod (main/client entrypoints, the client mixin config) versus the dev-only test mod with the four `fabric-gametest` entrypoints |
| `forge/src/main/resources/META-INF/mods.toml` | the Forge mod metadata (`${version}` expanded by `processResources`) |
| `fabric/src/gametest/resources/golden/*.txt` | the export (7254 lines) and import (25 lines) baselines copied verbatim from QCSimple; parity is measured against these |

## Testing & QA

Two suites, split by whether the code needs the block registry: a plain JUnit JVM cannot bootstrap the registries
here (under Yarn this was an `IllegalAccessError` from a split package inside the registry classes; not re-checked under
Mojang names), so anything touching `BuiltInRegistries.BLOCK` is a gametest instead.

| Suite | Files | Covers |
|---|---|---|
| JUnit, 37 tests | `AlignmentTest`, `IndexNbtTest`, `LayersCodecTest`, `plan/BuildShapeTest`, `plan/QcPlanTest`, `plan/RoadKitTest`, `net/PlanRequestDecodeTest`, `client/QcHistoryTest`, `client/QcKitsRoadJsonTest` | lattice math and residues; the index NBT round trip plus malformed state; the `w`/`g` bit layout and a playlist round trip; shapes and modifiers; partitions, rotation/mirror of flags, normalise/merge, `PlanCodec` round trip; the road contract (7 wide, lanes, joints, corner posts, medallion, plaza post, give-way); a plan request round-trips through the bounded inflate and a gzip bomb is refused; plan undo/redo depth, redo drop and the 100-step cap; kit road styles round-trip and hand-edited nulls/nested values are skipped instead of thrown |
| Gametests, 9 tests | `QcGameTests`, `QcPlanGameTests`, `StructureExportGoldenTest`, `StructureImportGoldenTest`, `PaletteCodecTest` | golden export/import parity; a build registers records, an align moves the building, layer 0 and non-creative are refused; a plan (full unit, framework-only unit, cleared cell, road) applies, `/qc undo` restores every block and record, redo re-applies it, a second undo restores it again and a new apply drops the redo stack; plan refusals are user-visible |

Golden mechanics: baselines are **classpath resources** (`/golden/unit-export-baseline.txt`) inside the test mod,
so they never reach the shipped jar; comparison is exact, order-sensitive line equality after CRLF/CR -> LF
normalisation; a failing run leaves the dump at `fabric/build/run/gameTest/qccore-golden/export-actual.txt` (and
`import-actual.txt`) for a plain shell `diff`; there is **no regenerate/update flag**; the export dump is sorted
explicitly and the import dump renders extracted structures as size + path only, because the palette ordering inside
an extracted tag is not deterministic. The game-test world is not recreated between runs, so
`QcGameTests.clearIndexArea` clears the test area's index records first. A JUnit XML report comes from
`-Dfabric-api.gametest.report-file=<file>`.

**Not covered:** the Forge glue (no Forge gametests; the gametests run on Fabric and exercise the shared code), the screens, `QcInteraction` (tools, shapes, the box height ray), `QcOverlayRenderer`,
`QcBlockPreview`, `QcHud`, `QcSchematics`, `QcTemplates`, `QcKits`, the chunked transfer, the push loop, the `/qc`
output and the input mixin have no automated tests, and `QcGameTests` does not cover erase or wall-flag editing.

**Verified project state (evidence behind this file):** export and import dumps byte-identical to the baselines,
`./gradlew test` 14/14, `./gradlew runGameTest` 7/7, plus a client smoke in a creative world (editor HUD verbatim,
place/erase, the component screen rejecting `not_a_block`, `J` producing a valid tag - `DataVersion 3465`, size
`13x13x13`, 750 blocks, 6 palette entries - and `K` reporting `built 1 QC unit(s) on 1 layer(s) at -2, 112, -4`) and a
headless dedicated-server smoke (`qc info` -> `0 QC unit(s) in minecraft:overworld` on a fresh world, and
`1 QC unit(s)` after loading a world the client had built into, proving the index survives a restart.
The test/gametest counts and the `All 7 required tests passed :)` line were re-checked against `build/test-results/`
and `build/run/gameTest/logs/latest.log` while writing this file; the two smoke transcripts themselves were not
re-executed. `[INFERENCE from the code; not executed]`

**Planning tools client smoke (2026-11-17, Xvfb + xdotool, creative superflat world via
`runClient --args="--quickPlaySingleplayer qc"`):** a floor shape planned 2 units with translucent ghost blocks; K was
refused on bedrock ("the plan does not fit inside this world's height"), then after Y applied 2 units / 1150 blocks
through the chunked packet; "Undo last apply" restored all 1150; the kit screen validated ids green and the road tool
with a 3-copy array applied 3 units + 3 road cells (1686 blocks); J exported a sparse `.nbt` with the road blocks;
a template saved to `run/qccore/templates/` and stamped rotated 90 deg with the array (10 units, 9 road cells). The
xdotool mouse cannot turn the camera (raw input), so shapes were only exercised from one aim; the box height ray,
mirrors and partition tools are unexercised in a client.

**Forge port smoke (2026-11-17):** after the Yarn -> Mojang migration `./gradlew build` passed 29/29 JUnit and
`All 9 required tests passed :)` (golden export/import parity unchanged). `:forge:runServer` and `:fabric:runServer`
loaded the mod and answered `qc info` -> `0 QC unit(s) in minecraft:overworld` and `qc forget 64` -> `forgot 0 QC
unit(s)`. `:forge:runClient --args="--quickPlaySingleplayer qc"` (creative superflat, Xvfb + xdotool): G showed the
editor HUD, RMB planned a unit with ghost blocks, K was refused "does not fit inside this world's height" (result
packet back), after Y it applied 1 unit / 750 blocks through the chunked plan packet; holding LMB on a placed block did
not mine it, RMB then LMB on an empty cell planned and erased a unit. Forge undo, align, the screens and templates
were not exercised in a client. The jars were checked to carry SRG (`m_…`) resp. intermediary (`method_…`) names.

**Manual checklist (human required):** wall and gate plates on a shared face (blue, then green with Ctrl), a second
layer placed 8 blocks up, `H` duplicating a layer, `Y` re-anchoring, the `U` align screen moving a placed building,
`O` toggling the overlay, and the config screen's `Choose…` / `Load layout…` flows including a size-mismatch
rejection.

**Reproducible smoke recipes:** for the server, put `eula=true` in `<loader>/run/eula.txt`, start `./gradlew :<loader>:runServer` as a
background service (ready when the log contains `Done (`) and drive its stdin through a FIFO with `qc info`,
`qc forget 64` and `stop` (`/qc` needs permission level 2, which the console has). For the client, start `Xvfb :99`, launch
`DISPLAY=:99 ./gradlew :<loader>:runClient --args="--quickPlaySingleplayer qc"` (a world in `<loader>/run/saves/qc`; a
creative superflat one can be made by running the dedicated server with `gamemode=creative` and
`level-type=minecraft:flat` and copying `run/world`), drive it with `xdotool` and keep a screenshot as visual proof.
`xdotool` may need `apt-get install -y xdotool`.

## Runtime/Tooling Preferences

- **JDK 21 is the build machine, Java 17 is the target bytecode.** `gradle.properties` holds every version
  (`minecraft_version=1.20.1`, `loom_version=1.17.487` (Architectury Loom), `loader_version=0.19.5`,
  `fabric_api_version=0.92.12+1.20.1`, `forge_version=47.4.29`, `version=1.0.0`, `group=com.qccore`,
  `archives_base_name=qccore`) and is the single source of truth; the build files only read `rootProject.*`.
  `forge/gradle.properties` holds only `loom.platform=forge`. There is **no `toolchain` block** - the bytecode
  level comes from `tasks.withType(JavaCompile) { it.options.release = 17 }`.
- **Build with the wrapper:** `./gradlew` (Gradle **9.5.1**, `-bin` distribution, wrapper jar committed,
  `distributionSha256Sum` pinned). `org.gradle.jvmargs=-Xmx2G`, `org.gradle.parallel=true` and
  `org.gradle.configuration-cache=false` (loom is not configuration-cache clean).
- **Loom essentials:** Architectury Loom in every project with `mappings loom.officialMojangMappings()`; the loader
  projects declare `loom { mods { "qccore" { sourceSet sourceSets.main; sourceSet 'main', ':common' } } }`;
  `fabricApi { configureTests { … } }` (fabric) creates the `gametest` source set and the `runGameTest` task;
  `withSourcesJar()` explains the `-sources.jar`. Remapped dependency artifacts land in `.gradle/loom-cache/`.
  Plugin and Forge artifacts come from `maven.architectury.dev` and `maven.minecraftforge.net` (both must be reachable);
  Maven Central sometimes answers 429 on a cold cache - retry, `--max-workers=2` helps.
- **Display:** `DISPLAY=:1` is exported in this environment and `xvfb-run`/`xdotool` are on `PATH`, so `runClient` can
  be launched and driven headlessly; no Xvfb process runs by default, so a display has to be started for a client
  smoke.
- **Gitignored/disposable:** `build/`, `run/` (any depth, so `fabric/run`, `forge/run`), `logs/`, `.gradle/`, `out/`, `classes/`, `.codebase-index/`, IDE
  files (`*.ipr`, `*.iws`), `hs_err_*.log`, `*.hprof`, `*.jfr`. Never edit anything under `build/`;
  `fabric/src/gametest/resources/golden/` is the opposite and is checked in on purpose.
