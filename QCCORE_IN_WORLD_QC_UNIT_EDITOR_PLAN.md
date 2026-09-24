# QCCore — in-world QC Unit editor & structure generator (Fabric 1.20.1)

Port the functionality of the desktop tool `~/Projects/QCSimple` (JavaFX, `com.naromil.qcsimple`) into the existing
Fabric mod scaffold at `~/Projects/QCCore` (mod id `qccore`, package root `com.qccore`), so players build QC Unit
buildings in-game instead of in a desktop GUI.

## Context

QCSimple edits a multi-layer map of **QC Units** (9x9x9 cubes spaced 8 blocks apart, labels in
`~/Projects/QCSimple/README.md`) and turns it into a vanilla structure `.nbt`
(`{DataVersion,size,palette,blocks,entities}`). Nine *components* make up a unit: framework / row / column / wall /
floor block ids plus four uploaded structures (inner wall 7x7x1|3, gate 7x7x1|3, outer wall 7x7x1..3, inner column
3x7x3). `~/Projects/QCSimple/AGENTS.md` documents the pipeline: `layers -> block map -> structure tag` on export and
`tag -> block map -> layers` (incl. grid detection + component extraction) on import.

Required end state, from the user's request + the four decisions taken with them:

1. Players configure the nine components in-game; component files are picked from `<gameDir>/schematics`, which is
   Litematica's default schematic folder. Vanilla structure `.nbt` only (no `.litematic` reader).
2. **In-world editing only** (no 2D canvas screen), Litematica/Effortless-Building style: a keybind enters a build
   mode, the crosshair + mouse + a few keys edit a virtual layout rendered as a translucent overlay in the world;
   the player then either saves the layout as a `.nbt` into the schematics folder or, in creative, builds it into the
   world.
3. Every QC Unit can be (re-)aligned to `(8x + a, 8y + b, 8z + c)`; configuring `a,b,c` **immediately moves** the
   whole connected building onto that lattice. Placement is free by default and automatically matches the lattice of
   surrounding placed QC Units.
4. A build **replaces** each unit's 9x9x9 cell (and the 2-block shell outside its exposed faces) with the layout:
   the layout's blocks are placed, everything else in that volume becomes air.

The mod keeps QCSimple's algorithm and its output byte-for-byte compatible: the block set of an exported layout is
pinned against QCSimple's checked-in golden baselines (see *Verification*).

## Verified ground truth (already checked on this machine — do not re-derive)

Toolchain (`~/Projects/QCCore/gradle.properties`, `build.gradle`, verified to resolve on `maven.fabricmc.net`):
MC 1.20.1, yarn `1.20.1+build.10`, loader `0.19.5`, fabric-api `0.92.12+1.20.1`, loom plugin
`net.fabricmc.fabric-loom-remap` `1.17.21`, Gradle wrapper `9.5.1`, JDK 21 with `options.release = 17`,
`loom { splitEnvironmentSourceSets() }`. A previous `./gradlew build` in this repo produced
`build/libs/qccore-1.0.0.jar`; all dependencies are in the local Gradle cache (`DISPLAY=:1` and `xvfb-run` exist, so
`runClient` can be launched).

Mapped jars for `javap` checks by the implementer:
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common/1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/minecraft-common-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar`
(client classes in the sibling `minecraft-clientonly-…jar`, server/test classes in `minecraft-common-…jar`);
yarn-remapped fabric-api modules under `~/Projects/QCCore/.gradle/loom-cache/remapped_mods/remapped/net/fabricmc/fabric-api/`.

Exact API facts the plan relies on (all verified by `javap`/bytecode, yarn 1.20.1):

| Need | Exact API |
|---|---|
| block state <-> NBT | `NbtHelper.fromBlockState(BlockState)`, `NbtHelper.toBlockState(RegistryEntryLookup<Block>, NbtCompound)`; pass `Registries.BLOCK.getReadOnlyWrapper()` (a `DefaultedRegistry<Block>` is **not** a `RegistryEntryLookup`). `NbtHelper.putDataVersion(NbtCompound)` writes `SharedConstants.WORLD_VERSION` = **3465** (= QCSimple's hardcoded `DataVersion`) |
| block-state rotation | `AbstractBlockState#rotate(BlockRotation)` / `#mirror(BlockMirror)`; `BlockRotation.{NONE,CLOCKWISE_90,CLOCKWISE_180,COUNTERCLOCKWISE_90}`; `BlockPos#rotate(BlockRotation)` — `CLOCKWISE_90` maps `(x,y,z) -> (-z,y,x)` (verified in `BlockPos#rotate` bytecode) = QCSimple's `"90"` `(-k,j,i)`, so `"0"|"90"|"180"|"270"` ↔ `NONE|CLOCKWISE_90|CLOCKWISE_180|COUNTERCLOCKWISE_90` |
| NBT I/O | `NbtIo.readCompressed(File)`, `readCompressed(InputStream)`, `writeCompressed(NbtCompound, File)`, `writeCompressed(NbtCompound, OutputStream)` |
| NBT access | `NbtCompound.{putInt,putString,put,putByte,putBoolean,putIntArray,getInt,getString,getCompound,getList(String,int),getByteArray,getIntArray,getKeys,contains(String),contains(String,int)}`; `NbtList.{add,size,getCompound(int),getInt(int)}`; type ids `NbtElement.{COMPOUND_TYPE,LIST_TYPE,INT_TYPE,STRING_TYPE}` |
| registries | `Registries.BLOCK` (`DefaultedRegistry<Block>`), `Registry#getOrEmpty(Identifier)`, `Registry#getId(T)`, `Block#getDefaultState()`, `Blocks.AIR`; `Identifier(String)`, `Identifier.tryParse(String)` |
| world writes | `World#setBlockState(BlockPos, BlockState, int)`; flags `Block.NOTIFY_ALL`, `Block.FORCE_STATE`, `Block.SKIP_DROPS`; `World#getBlockState`, `World.isValid(BlockPos)`, `World#getBottomY()/getTopY()` |
| persistence | `ServerWorld#getPersistentStateManager()` -> `PersistentStateManager#getOrCreate(Function<NbtCompound,T> reader, Supplier<T> factory, String id)`; `PersistentState#writeNbt(NbtCompound)`, `#markDirty()`; `MinecraftServer#getOverworld()`, `#getRegistryManager()` |
| packets | fabric-networking-api-v1 **1.3.15** has **no** `CustomPayload`/`PayloadTypeRegistry` API (that is 1.20.2+). Use `FabricPacket` (`write(PacketByteBuf)`, `getType()`) + `PacketType.create(Identifier, Function<PacketByteBuf,P>)`; `ServerPlayNetworking.registerGlobalReceiver(PacketType<T>, PlayPacketHandler<T>)` where the handler is `receive(T packet, ServerPlayerEntity player, PacketSender sender)`; `ServerPlayNetworking.send(ServerPlayerEntity, T)`; `ClientPlayNetworking.registerGlobalReceiver(PacketType<T>, …)` / `ClientPlayNetworking.send(T)`. Sending needs no receiver registration (verified in `ServerPlayNetworking#send` bytecode: it just writes and sends). `PacketByteBuf.{writeNbt,readNbt,writeBlockPos,readBlockPos,writeIdentifier,readIdentifier,writeString,readString,writeVarInt,readVarInt,writeBoolean,readBoolean}` exist |
| events | `ServerPlayConnectionEvents.JOIN/DISCONNECT`, `ServerTickEvents.END_SERVER_TICK`, `ClientTickEvents.END_CLIENT_TICK`, `ClientPlayConnectionEvents.JOIN/DISCONNECT`, `CommandRegistrationCallback.EVENT` (`register(CommandDispatcher<ServerCommandSource>, CommandRegistryAccess, CommandManager.RegistrationEnvironment)`) |
| keybinds | `KeyBindingHelper.registerKeyBinding(KeyBinding)`, `new KeyBinding(String translationKey, InputUtil.Type, int glfwKey, String category)`, `KeyBinding#wasPressed()`/`#isPressed()` |
| render hooks | `WorldRenderEvents.AFTER_TRANSLUCENT` (fires while a `Screen` is open too), `WorldRenderContext.{matrixStack(),consumers(),camera(),world()}` — `consumers()` is null before `BEFORE_ENTITIES`/after `BEFORE_DEBUG_RENDER`, so `AFTER_TRANSLUCENT` is safe; `VertexConsumerProvider#getBuffer(RenderLayer)`; `RenderLayer.getLines()`; `WorldRenderer.drawBox(MatrixStack, VertexConsumer, double x1,double y1,double z1,double x2,double y2,double z2, float r,float g,float b,float a)` (writes vertex/color/normal, i.e. exactly what `getLines()` wants); `MatrixStack.{push,pop,translate}`; `Camera#getPos()` |
| input | `MinecraftClient.crosshairTarget` (`HitResult#getPos()`), `BlockHitResult#{getBlockPos,getSide}`, `ClientWorld`/`ClientPlayerEntity`; `MinecraftClient#doAttack()` (private boolean), `#doItemUse()` (private void), `#currentScreen`, `#player`, `#world`, `#options.{attackKey,useKey}`, `#execute` |
| gui | `Screen#{render(DrawContext,int,int,float),init,addDrawableChild,keyPressed,mouseClicked,mouseDragged,mouseReleased,shouldPause,shouldCloseOnEsc,close}`, `DrawContext#{fill,drawBorder,drawTextWithShadow,drawText,enableScissor,disableScissor}`, `TextFieldWidget#{setText,getText,setChangedListener,setTextPredicate,setMaxLength}`, `ButtonWidget.builder(Text, ButtonWidget.PressAction)`, `ElementListWidget<E extends ElementListWidget.Entry<E>>`, `HudRenderCallback.EVENT` |
| commands | `CommandManager.{literal,argument}`, `BlockPosArgumentType.blockPos()`, `IntegerArgumentType.integer(int,int)`, `ServerCommandSource#{sendFeedback(Supplier<Text>,boolean),sendError,sendMessage,hasPermissionLevel,getPlayer,getWorld,getPosition}` |
| players | `PlayerEntity#isCreative()`, `ServerPlayerEntity#{sendMessage(Text,boolean),getServerWorld()}` |
| path | `FabricLoader.getInstance().getGameDir()` (`Path`) |
| tests | loom 1.17.21 exposes `fabricApi { configureTests { … } }` with `GameTestSettings{createSourceSet,modId,enableGameTests,eula,clearRunDirectory,username}` (creates source set **`gametest`** and run config `gameTest` -> task `runGameTest`); fabric-gametest-api-v1 **1.2.15** is present; entrypoint key **`fabric-gametest`**, marker interface `net.fabricmc.fabric.api.gametest.v1.FabricGameTest` (`EMPTY_STRUCTURE`), vanilla `@GameTest(templateName=…)` annotation, `TestContext#{getWorld,getAbsolutePos,setBlockState,getBlockState,expectBlock,createMockCreativeServerPlayerInWorld,complete,runAtTick}`; launched with JVM arg `-Dfabric-api.gametest` (+ optional `-Dfabric-api.gametest.report-file=<file>` for a JUnit XML report) |

## Approach

Ordered so the project compiles and existing behaviour stays green after every step. Steps 1–2 are pure logic with
no Minecraft world access (unit-testable); 3–4 are server side; 5–7 client side; 8 is wiring.

### Step 1 — Port the generation pipeline to `com.qccore.core` (main source set)

Port QCSimple's algorithm verbatim, changing only the data types: `Point3D/Point2D` -> `BlockPos`/(new
`UnitPos`), `CompoundTag` block states -> `BlockState`, rotation strings -> `BlockRotation`. Conventions that MUST
stay identical (they are what the golden baselines pin): unit spacing 8, unit-local coordinates `0..8`,
`absX = dx*8 + x`, layers keyed by `dy` with `absY = dy*8 + y`, layers sorted ascending and units iterated in
insertion order, `put` overwrites / `putIfAbsent` keeps, and the per-unit call order
framework -> row -> column -> wall -> floor voxel sweep, then innerColumn -> innerWall -> gate -> outerWall.

Files to create under `src/main/java/com/qccore/core/`:

- `UnitPos.java` — `public record UnitPos(int x, int z)`.
- `UnitSide.java` — `public enum UnitSide { NORTH, EAST, SOUTH, WEST }` plus
  `public static UnitSide of(Direction d)` (UP/DOWN -> `null`) and `public Direction toDirection()`.
- `QcUnit.java` — port of `logic/QCUnit.java`: the eight `boolean` fields with `setWallX/setGateX`,
  `hasWallX/isGateX/hasAnyX` and the copy constructor.
- `UnitLayers.java` — `final class` wrapping `TreeMap<Integer, LinkedHashMap<UnitPos, QcUnit>>` (QCSimple uses
  `HashMap`s; a `TreeMap`+`LinkedHashMap` gives deterministic ascending-layer/insertion-order iteration, which is
  what the golden fixture also uses). Methods: `Map<UnitPos,QcUnit> layer(int dy)` (creates on demand like
  `EditorState.getMapForLayer`), `Map<UnitPos,QcUnit> peekLayer(int dy)`, `boolean contains(int dx,int dy,int dz)`
  (port of `SpatialUtils.layersContains`), `boolean isEmpty()`, `int unitCount()`, `Set<Integer> layerKeys()`,
  `void setLayer(int dy, Map<UnitPos,QcUnit> m)`, `UnitLayers copy()`.
- `SpatialUtils.java` — `static int boundaryCount(int x,int y,int z)` backed by a `static final int[9][9][9]`
  built in a static initializer (no lazy cache, no console logging), and
  `static boolean isValidPlacement(UnitLayers layers, int x,int y,int z, int dx,int dy,int dz)` — literal port
  including the `x/y/z < 0` or `> 8` divisor adjustments and the six boundary-neighbour checks.
- `GridOrigin.java`, `UnitGridStats.java` — records, ports of the QCSimple ones (`EMPTY`, `hasSoutheastCorner`).
- `UnitGrid.java` — `public static final int CENTER_X = 16, CENTER_Z = 16`.
- `Alignment.java` — `public static int nearestDelta(int value, int residue)` =
  `int d = Math.floorMod(residue - value, 8); return d > 4 ? d - 8 : d;`,
  `public static int snap(int value, int residue)` = `value + nearestDelta(value, residue)`,
  `public static BlockPos residues(BlockPos p)` = each component `Math.floorMod(c, 8)`.
- `QcStructure.java` — `public record QcStructure(int sizeX, int sizeY, int sizeZ, List<Entry> entries)` with
  `public record Entry(int x, int y, int z, BlockState state)`; no NBT inside.
- `QcResult.java` — `public record QcResult(boolean ok, String message)`.
- `palette/UnitComponent.java` — same five methods as QCSimple (`applyDefaultConfig()`, `isValidSize(int,int,int)`
  default `false`, `exportVoxel(UnitExportContext,int,int,int,int)` default no-op, `exportUnit(UnitExportContext)`
  default no-op, `extract(UnitImportContext)` default no-op; the `boundaryCount` javadoc contract is copied).
- `palette/BlockComponent.java` — `String id` + `getRawId()/setId(String)`; `void applyDefaultConfig()` default sets
  the per-class `DEFAULT_ID`; `BlockState state()` returns `Registries.BLOCK.getOrEmpty(Identifier.tryParse(normalise(id)))`
  -> `Optional<Block>` -> `block.getDefaultState()`, else `null`; `normalise` trims, returns `null` for blank and
  prefixes `minecraft:` when no `:` is present; `boolean isConfigured()` = `state() != null`.
  **Divergence from QCSimple (deliberate):** a blank/invalid id is *not* silently replaced by `minecraft:stone`; such a
  component exports nothing, the config screen paints the field red and the server refuses the build with
  `"framework block id '<id>' is not a block"`. The golden fixtures set every id explicitly, so parity is unaffected.
- `palette/QcComponent.java` — port of `data/QCComponent.java`: `String path`, `@Nullable QcStructure structure`,
  `getSizeX/Y/Z()` (`0` when absent), `isEmpty()` = `structure == null`,
  `setStructure(QcStructure, String path)`, `setExtracted(QcStructure)` (path becomes the literal
  `"[Extracted from Opened File]"` when non-null, otherwise `"[Not Configured]"`).
- `palette/BlockPalette.java` — non-singleton `final class` (one instance per client session / per server build
  request) holding the nine components in QCSimple's order with accessors `frameworkBlock(), rowBlock(), columnBlock(),
  wallBlock(), floorBlock(), innerWall(), gate(), outerWall(), innerColumn()`; `isConfigured()` = framework
  configured; `applyDefaultConfig()` calls all nine.
- `palette/Defaults.java` — `static QcStructure simple(BlockState state, byte[][][] map)` (port of
  `NBTGenerator.generateSimpleStructureTag`: `map[x][y][z]`, `map.length`/`map[0].length`/`map[0][0].length` are the
  size triple, negative entries are skipped) and `static void applyTo(BlockPalette)`, which installs QCSimple's exact
  block ids, the four `byte[][][]` maps **copied verbatim** from `InnerWall.applyDefaultConfig`,
  `Gate.applyDefaultConfig`, `OuterWall.applyDefaultConfig`, `InnerColumn.applyDefaultConfig`, and the literal paths
  `"[Generated Default: Deepslate Bricks]"` / `"[Generated Default: Stripped Spruce Log]"`.
- `palette/FrameworkBlock.java` — port of `data/FrameworkBlock.java`: `DEFAULT_ID = "minecraft:polished_deepslate"`,
  corner cube + `i/j/k = -2..2` axial arms with `putIfAbsent`, the three diagonal transition voxels via `ctx.isFree`,
  the shell voxels (`boundaryCount == 2`) via `putIfAbsent`, and
  `public GridOrigin detectGrid(Map<BlockPos,BlockState> blockMap)` — port of QCSimple's shell scan, comparing
  candidate states with `==` (states are interned) using `boundaryCount >= 2`, adopting the detected state as the id and
  throwing `new IllegalStateException("No framework block found")` when nothing matches.
- `palette/RowBlock.java`, `ColumnBlock.java`, `WallBlock.java`, `FloorBlock.java` — verbatim ports including
  `DEFAULT_ID`s (`minecraft:chiseled_deepslate`, `minecraft:spruce_log`, `minecraft:deepslate_tiles`,
  `minecraft:spruce_planks`), the probe offsets, the `isFree` guards, the `unitExists` diagonal-corner rule of the
  column and the `extract` probes (`ctx.stateOrDefault(new BlockPos(...), state())`).
- `palette/InnerStructure.java` — abstract port: `acceptsGateSides()`, `preserveOnPlace()`, `outShift()`
  (`getSizeZ() == 3 ? 1 : 0`), `final exportUnit` (SOUTH then EAST), `exportSide`, `rotationFor(UnitSide)`,
  `relativePos`, `canBePlaced(layerMap, dx, dz, side)`, and the static `scanSides(UnitImportContext, SideHandler)`
  scan with the same probe boxes (`absX+7..absX+9 / absZ+2..absZ+6` for EAST, `absX+2..absX+6 / absZ+7..absZ+9` for
  SOUTH) and the same `isGate` emptiness tests; `extractSide` builds the extracted `QcStructure` from the same
  regions with rotations `NONE` (SOUTH) / `CLOCKWISE_90` (EAST).
- `palette/InnerWall.java`, `Gate.java` — `isValidSize` = `x==7 && y==7 && (z==1||z==3)`, gate/`InnerWall`
  `acceptsGateSides` `true`/`false`, `preserveOnPlace()` `true` for the gate only, `onDetectedSide` with the
  "already extracted" gates (`markInnerWallExtracted`/`markGateExtracted`).
- `palette/OuterWall.java` — `isValidSize` = `x==7 && y==7 && z<=3`, the four `putStructure` calls with
  `outShift = getSizeZ() >= 2 ? 1 : 0` and rotations `NONE/CLOCKWISE_180/CLOCKWISE_90/COUNTERCLOCKWISE_90` for
  N/S/E/W, plus the `extract` probe region `base-(7,7,1) .. base-(1,1,-1)` with `CLOCKWISE_180`.
- `palette/InnerColumn.java` — `isValidSize` = `3x7x3`, `DEFAULT_ID` `minecraft:stripped_spruce_log`,
  `canBePlaced(layerMap, dx, dz, isWallEmpty)` with the four-unit cross conflict test, `relativePos`,
  `exportUnit` (`isWallEmpty = palette.innerWall().isEmpty() && palette.gate().isEmpty()`), and the `extract` scan
  probing `absX+7..absX+9 / absY+1..absY+7 / absZ+7..absZ+9` excluding `ctx.frameworkState()`.
- `UnitExportContext.java` — holds `UnitLayers layers`, `Map<BlockPos,BlockState> blockMap` (a `LinkedHashMap`),
  the `BlockPalette`, `BlockPos origin`, and the current unit `dx/dy/dz/layerMap`; methods
  `palette()`, `dx()/dy()/dz()`, `layerMap()`, `blockMap()`, `absX/absY/absZ(int)`, `BlockPos abs(int,int,int)`,
  `void put(BlockPos,BlockState)`, `void putIfAbsent(BlockPos,BlockState)`, `void putStructure(BlockPos, QcStructure, BlockRotation, boolean preserve)`
  (port of `BlockNBTConverter.putStructure`: rotate the entry position with `BlockPos#rotate`, rotate the state with
  `BlockState#rotate`, write unless `preserve` and the position is occupied) and `boolean isFree(int,int,int)`,
  `boolean unitExists(int,int,int)` delegating to `SpatialUtils`/`UnitLayers`. `put`/`putIfAbsent` ignore positions
  whose state is `null`.
- `UnitExporter.java` — `public static Map<BlockPos,BlockState> export(UnitLayers layers, BlockPalette palette, BlockPos origin)`
  — literal port of `logic/UnitExporter.java` including the empty-layers early return (returns an empty map, logs via
  `LOGGER.info("qccore: export skipped, empty layers")`).
- `UnitImportContext.java` — port: `blockMap()`, `layers()`, `palette()`, `originX/Y/Z()`, `absX/absY/absZ(int)`,
  `frameworkState()`, `stats()/setStats`, `southeastBase()` (`null` when no grid), `stateOrDefault(BlockPos, BlockState)`,
  `hasAnyBlock(x,y,z,X,Y,Z)` / `hasAnyBlock(..., BlockState noBlock)`, and the three extracted flags.
- `UnitImporter.java` — `public static UnitLayers importLayers(Map<BlockPos,BlockState> blockMap, BlockPalette palette, int centerX, int centerZ)`
  — literal port of `logic/UnitImporter.java`: `detectGrid`, `scanUnits` (the same 9³ validity flag test, `floorDiv`
  unit coordinates, average dx/dz, the `maxX/maxY/maxZ` "south-east" bookkeeping, layers keyed by `dy + 1`), then
  `columnBlock/rowBlock/floorBlock/wallBlock/outerWall .extract`, then `InnerStructure.scanSides` +
  `innerWall/gate .onDetectedSide` + the `setExtracted(null)` fallbacks, then `innerColumn.extract`, then
  `centerLayers(ctx, centerX, centerZ)`.
- `UnitBlockConverter.java` — two-line facade: `convertLayersToBlockMap(layers, palette, origin)` and
  `convertBlockMapToLayers(blockMap, palette)` (centres on `UnitGrid.CENTER_X/CENTER_Z`).

### Step 2 — Structure NBT I/O + session/palette codec (`com.qccore.core.nbt`)

- `StructureNbt.java`:
  - `public static NbtCompound write(Map<BlockPos,BlockState> blockMap)` — port of
    `BlockNBTConverter.convertMapToTag`: bounding box -> `size` int list, palette deduplicated through a
    `LinkedHashMap<BlockState,Integer>`, `blocks` list entries `{"pos":[x-minX,y-minY,z-minZ],"state":i}`,
    `entities` = empty `NbtList`, then `NbtHelper.putDataVersion(root)` (writes 3465, identical to QCSimple's
    literal). Empty map -> size `[0,0,0]` is *not* emitted; instead `write` returns `null` so callers can report
    "nothing to save" (no such case exists in QCSimple because `NbtIO.writeNbt` would throw).
  - `public static Map<BlockPos,BlockState> read(NbtCompound root)` — port of `convertTagToMap` using
    `NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), tag)`.
  - `public static QcStructure parseStructure(NbtCompound tag)` — reads `size` (**required**, else
    `IllegalArgumentException("missing 'size'")`) and `blocks`/`palette` (missing = empty) into `QcStructure`.
  - `public static NbtCompound writeStructure(QcStructure structure)` — the same shape as `write` but from a
    `QcStructure` (used for extracting components and for the palette wire format).
  - `public static String validateComponentFile(NbtCompound tag)` — returns `null` when acceptable, otherwise a
    message: missing `size`, `size` not three ints, or a palette entry whose `Name` does not resolve
    (`Registries.BLOCK.getOrEmpty` -> `"unknown block '<name>' in palette"`). Used by the file browser before
    `parseStructure`, so bad files produce a red message instead of an exception.
  - `public static NbtCompound readFile(Path file)` / `public static void writeFile(NbtCompound, Path)` wrapping
    `NbtIo.readCompressed(File)` / `NbtIo.writeCompressed(NbtCompound, File)` and rethrowing `IOException` as
    `UncheckedIOException` (callers catch it and show the message).
- `PaletteCodec.java` (in the same package):
  - `public static NbtCompound toNbt(BlockPalette p)`:
    `{"framework","row","column","wall","floor": <String id>, "innerWall"|"gate"|"outerWall"|"innerColumn":
    <structure NbtCompound, omitted when the component is empty>}`.
  - `public static BlockPalette fromNbt(NbtCompound tag)` — the inverse; unknown/missing ids become empty strings
    (`BlockComponent#setId("")`) and paths become `"[Not Configured]"` for absent structures and the literal
    `"[Sent by client]"` for structures received over the wire (the wire carries ids and structures only, so the
    server-side palette's display path is nominal; only component ids, sizes and entries matter for a build).
- `LayersCodec.java`:
  - `public static NbtCompound toNbt(UnitLayers layers)`:
    `{"layers":[{"y":<int>,"units":[{"x":<int>,"z":<int>,"w":<byte walls>,"g":<byte gates>},…]},…]}` (layers ascending).
  - `public static UnitLayers fromNbt(NbtCompound tag)` — tolerates missing keys (empty result); malformed entries are
    skipped, never throw.
  - Bit layout documentation (used by `w`/`g`, the index records and the renderer):
    **bit0 = NORTH, bit1 = EAST, bit2 = SOUTH, bit3 = WEST**; helper
    `static int bits(QcUnit unit, boolean gates)` / `static void apply(QcUnit unit, int walls, int gates)`.

### Step 3 — Server-side world state and mutations (`com.qccore.world`, main source set)

- `QcUnitRecord.java` — `public record QcUnitRecord(BlockPos origin, int walls, int gates)`: bit layout as above,
  `QcUnit toUnit()`, `static QcUnitRecord of(BlockPos, QcUnit)`, and `NbtCompound toNbt()`/`static QcUnitRecord fromNbt(NbtCompound)`
  (`{"pos":[x,y,z],"w":byte,"g":byte}`).
- `QcUnitIndex.java` — `public final class QcUnitIndex extends PersistentState`:
  - `private final Map<Identifier, LinkedHashMap<BlockPos, QcUnitRecord>> byDimension`.
  - `static QcUnitIndex get(ServerWorld world)` = `world.getPersistentStateManager().getOrCreate(QcUnitIndex::read, QcUnitIndex::new, "qccore_units")`
    (vanilla caches it). The state file therefore lives in the dimension's own data dir; the per-dimension key inside
    keeps the data correct regardless of scoping.
  - `Map<BlockPos,QcUnitRecord> units(Identifier dimension)`, `void put(Identifier, BlockPos, QcUnitRecord)`,
    `void remove(Identifier, BlockPos)`, `int totalUnits()`.
  - `writeNbt`: `{"dimensions":{"<dimension id>":{"units":[ …record toNbt… ]}}}`; `static QcUnitIndex read(NbtCompound)`
    tolerant of missing keys.
  - `int prune(ServerWorld world, BlockPos center, int radius)` — drops records within `radius` blocks of `center`
    whose origin block `world.getBlockState(origin).isAir()` (a built unit always has a non-air framework block at its
    origin corner), so hand-mined buildings stop being matched against. Called with a 128-block radius for the player
    region in the push path and with the affected building's bounding box in `QcAlignService`. Returns the number
    dropped and logs `LOGGER.info("qccore: pruned {} stale unit record(s)", n)` when `n > 0`.
- `QcWorldOps.java` — the single place that touches world blocks:
  - `static void clearFootprint(ServerWorld world, Collection<BlockPos> unitOrigins)` — for every origin: clear the
    9x9x9 cell (`0..8` on every axis) **and** for each of the six `Direction`s whose neighbour
    `origin.offset(dir, 8)` is not in the origin set, clear the two-block-thick slab just outside that face
    (local `t = 9..10` along the face axis, `0..8` along the two tangent axes) — skipping positions that lie inside
    another unit's cell. All writes use `Blocks.AIR.getDefaultState()`.
  - `static void placeBlocks(ServerWorld world, Map<BlockPos,BlockState> blockMap)` — `world.setBlockState(pos, state, Block.NOTIFY_ALL | Block.FORCE_STATE)`,
    skipping positions where `!World.isValid(pos)` or `pos.getY() < world.getBottomY() || >= world.getTopY()`.
  - `static boolean fits(ServerWorld world, Collection<BlockPos> positions)` — `World.isValid` + height bounds for
    every position (used for the pre-flight check so a build never half-applies).
- `QcBuildService.java`:
  - `public static QcResult build(ServerPlayerEntity player, BlockPos anchor, UnitLayers layers, BlockPalette palette)`
  - Validation order and exact failure messages (all returned as `QcResult(false, msg)`, nothing written):
    1. `!player.isCreative()` -> `"creative mode is required to build into the world"`;
    2. `layers.unitCount() == 0` -> `"the layout is empty"`;
    3. `layers.unitCount() > 4096` -> `"the layout has <n> QC units; the limit is 4096"`;
    4. any layer key `< 1` -> `"layer <n> is not allowed (layers start at 1)"`;
    5. `!palette.isConfigured()` -> `"framework block id '<id>' is not a block"` (id quoted from the component, or
       `"<empty>"`);
    6. any non-empty component structure failing `isValidSize` -> `"<component> structure is <x>x<y>x<z>; expected <rule>"`
       (inner wall/gate `7x7x1 or 7x7x3`, outer wall `7x7x0..3`, inner column `3x7x3`);
    7. exported block map empty -> `"the layout produced no blocks"`;
    8. `!QcWorldOps.fits(world, cells ∪ blockMap.keys())` -> `"the layout does not fit inside this world's height (y <a>..<b>)"`.
  - Apply: `QcWorldOps.clearFootprint(world, origins)` where `origins = { anchor + (8*dx, 8*dy, 8*dz) }`, then
    `QcWorldOps.placeBlocks(world, blockMap)`, then register one record per unit
    (`QcUnitIndex.get(world).put(dimension, origin, QcUnitRecord.of(origin, unit))`, `markDirty()`).
  - Success message: `"built <n> QC unit(s) on <m> layer(s) at <x>, <y>, <z>"`; logs
    `LOGGER.info("qccore: {} built {} units at {} in {}", player.getName().getString(), n, anchor, dimension)`.
- `QcAlignService.java`:
  - `public static QcResult align(ServerPlayerEntity player, BlockPos unitOrigin, int a, int b, int c, BlockPalette palette)`
  - Steps: creative check (`"creative mode is required to re-align a building"`); prune; look up `unitOrigin`
    (`"no QC unit recorded at <pos>"`); BFS the connected building over the six `origin.offset(dir, 8)` neighbours
    (`Map<BlockPos,QcUnitRecord> building`, cap 4096); compute `delta` per axis with `Alignment.nearestDelta`;
    all-zero -> `"already aligned to (8x+<a>, 8y+<b>, 8z+<c>)"`.
  - Rebuild: `newAnchor = oldAnchor.add(delta)` where `oldAnchor` = component-wise min over the building; rebuild the
    layout as `UnitLayers` from the building's records (`dy = (origin.y - oldAnchor.y)/8`,
    `UnitPos((origin.x - oldAnchor.x)/8, (origin.z - oldAnchor.z)/8)`, flags via
    `LayersCodec.apply`); `QcWorldOps.clearFootprint(world, building.keySet())` (old positions) **before** placing;
    then the same clear/place/record sequence as `QcBuildService.build` at `newAnchor`, then re-key every record of
    the building to its shifted origin and remove the old keys.
  - Success message: `"moved <n> QC unit(s) by (<dx>, <dy>, <dz>) to align to (8x+<a>, 8y+<b>, 8z+<c>)"`; logs the
    same at INFO with the player name.
  - Palette note (inline, user-visible behaviour): a re-align rebuilds the building from its recorded unit flags and
    the palette sent by the client, so it re-applies the *current* component configuration and discards hand edits
    inside the building's footprint.

### Step 4 — Networking and commands (`com.qccore.net`, main source set)

- `QcPackets.java` — four `FabricPacket` records with `PacketType`s created in a static block:
  - `QcBuildC2S(BlockPos anchor, NbtCompound layers, NbtCompound palette)` id `qccore:build`
    (`write`: `writeBlockPos`, `writeNbt`, `writeNbt`).
  - `QcAlignC2S(BlockPos unit, int a, int b, int c, NbtCompound palette)` id `qccore:align`.
  - `QcIndexS2C(Identifier dimension, NbtCompound units)` id `qccore:index`.
  - `QcResultS2C(boolean ok, String message)` id `qccore:result`.
- `QcServerNetworking.java`:
  - `static void register()` called from `QCCore.onInitialize()`: `ServerPlayNetworking.registerGlobalReceiver(QcBuildC2S.TYPE, …)`
    and `…(QcAlignC2S.TYPE, …)`; both handlers wrap their body in `player.server.execute(() -> …)` (handlers arrive on
    the netty thread), decode `LayersCodec.fromNbt`/`PaletteCodec.fromNbt`, call the service, log, reply with
    `QcResultS2C` and push the index region.
  - Index pushes: `static void push(ServerPlayerEntity player)` sends every unit within 128 blocks of the player's
    position for the player's current dimension (capped at 4096 records, nearest first) as `QcIndexS2C`; called on
    `ServerPlayConnectionEvents.JOIN`, right after any successful build/align, and from a
    `ServerTickEvents.END_SERVER_TICK` ticker every 40 ticks when the player moved ≥ 16 blocks since its last push
    (tracked in a `Map<UUID, BlockPos>` inside `QcServerNetworking`). Prune the player's region before pushing and
    push again if pruning removed anything.
- `QcCommands.java` — `CommandRegistrationCallback.EVENT` registers `/qc` with two children, both
  `requires(src -> src.hasPermissionLevel(2))`:
  - `/qc info` — `sendFeedback` with `"<units> QC unit(s) in <dimension>"`, plus `"<n> within 64 blocks of <x> <y> <z>"`.
    Works from the dedicated-server console (no player required).
  - `/qc forget [radius]` (default 64, `IntegerArgumentType.integer(1, 512)`) — drops index records within that radius of
    the source position without touching blocks; feedback `"forgot <n> QC unit(s)"`.
- `QCCore.java` — add `QcServerNetworking.register(); QcCommands.register();` to `onInitialize()` (keep the existing
  `MOD_ID`, `LOGGER`, `id(String)`); remove the now-unused scaffold mixin (see Step 8).

### Step 5 — Client session, keybinds and in-world interaction (`src/client/java/com/qccore/client`)

- `QcClientState.java` — static mutable session state (single-instance, mirrors QCSimple's `EditorState` singleton,
  but scoped to the client):
  - `boolean active`, `boolean overlayVisible = true`, `BlockPos anchor`, `Integer alignA/alignB/alignC` (`null` = free
    lattice), `UnitLayers layers = new UnitLayers()`, `BlockPalette palette` (initialised with `Defaults.applyTo`),
    `Map<BlockPos, QcUnitRecord> nearbyUnits = new HashMap<>()`, `Identifier dimension`,
    `String message`, `int messageTicks` (`void say(String)` starts a 60-tick HUD message), `void reset()`.
- `QcKeys.java` — the eight `KeyBinding`s, category `"key.categories.qccore"`:

  | translation key | default key | action |
  |---|---|---|
  | `key.qccore.editor` | `GLFW_KEY_G` | toggle editor mode (enter: capture anchor; leave: keep the layout in memory) |
  | `key.qccore.reanchor` | `GLFW_KEY_Y` | move the session anchor to the crosshair |
  | `key.qccore.duplicate_layer` | `GLFW_KEY_H` | copy the cursor layer into layer+1 (deep copy, port of `EditorState.duplicateLayerData`) |
  | `key.qccore.save` | `GLFW_KEY_J` | open `QcSaveScreen` |
  | `key.qccore.build` | `GLFW_KEY_K` | send `QcBuildC2S` for the current layout |
  | `key.qccore.config` | `GLFW_KEY_I` | open `QcConfigScreen` |
  | `key.qccore.unit_align` | `GLFW_KEY_U` | open `QcAlignScreen` for the targeted placed unit |
  | `key.qccore.overlay` | `GLFW_KEY_O` | toggle `overlayVisible` |

- `QcInteraction.java` — all crosshair logic, no rendering:
  - `record Cursor(UnitPos cell, int layer, @Nullable Direction face, boolean blockHit)`.
  - `static void enterEditorMode(MinecraftClient client)` — `anchor = new BlockPos(floor(x), floorDiv(floor(y), 8) * 8 - 8, floor(z))`
    of the player (layer 1 = the player's own cell, "free" lattice), then `adoptNearbyLattice()`.
  - `static void reanchor(MinecraftClient client)` — reference = `client.crosshairTarget.getPos()` floored (works for
    both block hits and misses), `anchor = new BlockPos(ref.x, ref.y - 8, ref.z)`, then `adoptNearbyLattice()`.
  - `static void adoptNearbyLattice()` — if the session alignment is set (a/b/c non-null) snap each anchor component
    with `Alignment.snap(anchor.c, alignC)`; otherwise take the nearest `QcClientState.nearbyUnits` record within 48
    blocks and snap the anchor to that record's residues (`Alignment.residues(record.origin())`). No record and no
    configured alignment -> the anchor stays free ("can be placed anywhere").
  - `static @Nullable Cursor cursor(MinecraftClient client)` — from `client.crosshairTarget`:
    block hit -> reference `hit.getBlockPos()` for the cursor cell, `hit.getBlockPos().offset(hit.getSide())` for the
    placement cell, `hit.getSide()` as the face; miss -> `BlockPos.ofFloored(hit.getPos())` for both, `face = null`.
    `cell = new UnitPos(floorDiv(ref.x - anchor.x, 8), floorDiv(ref.z - anchor.z, 8))`,
    `layer = floorDiv(ref.y - anchor.y, 8)`.
  - `static void click(boolean useKey)` — called by the input mixin. Repeats while a mouse button is held are
    idempotent on purpose: every action *sets* a state instead of inverting it, which is what makes drag-painting
    match QCSimple's canvas drag behaviour — do not add a cooldown or a toggle:
    - plain `use` (RMB): place — cell from the placement reference, `layer >= 1` required (else
      `say("QC: layers start at 1")`), `layers.layer(layer).putIfAbsent(cell, new QcUnit())`.
    - plain attack (LMB): erase — cell from the cursor reference; remove the unit and clear the facing wall/gate flags
      of the four horizontal neighbours (port of `EditorController.closeWall`).
    - `Shift` + `use`: `toggleWall(cursor, face, true, false)`; `Ctrl` + `use`: `…, true, true` (gate);
      `Shift` + attack: `toggleWall(cursor, face, false, false)`; `Ctrl` + attack: `toggleWall(cursor, face, false, true)`.
      Input priority when both modifiers are held: `Ctrl` wins.
    - `toggleWall` requires a horizontal `face` (else `say("QC: look at a side face to place walls")`), both the cursor
      cell and `cell + face` to hold units in that layer (else
      `say("QC: both QC units must exist to attach a wall")`), and ports `EditorController.tryToggleWall`: gate toggles
      only when both sides already have the wall flag; clearing a wall leaves the gate flag alone.
  - `static void onClientTick(MinecraftClient client)` — the keybind pump: handles all eight keys with
    `while (key.wasPressed())`, plus a world-change guard (`client.world == null || !client.world.getRegistryKey().getValue().equals(QcClientState.dimension)`
    -> `QcClientState.reset()` and `say("QC: editor reset (changed dimension)")`), plus `messageTicks` decay.
- `QCCoreClient.java` — `onInitializeClient()`: `QcKeys.register()`, `ClientTickEvents.END_CLIENT_TICK.register(QcInteraction::onClientTick)`,
  `WorldRenderEvents.AFTER_TRANSLUCENT.register(QcOverlayRenderer.INSTANCE)`, `HudRenderCallback.EVENT.register(QcHud::render)`,
  `ClientPlayNetworking.registerGlobalReceiver(QcIndexS2C.TYPE, …)` (store the records + dimension, drop records for
  other dimensions) and `…(QcResultS2C.TYPE, …)` (`QcClientState.say(message)`, and on `ok` a green chat line via
  `client.player.sendMessage(Text.literal(message), false)` clamped to the client thread with `client.execute(...)`).

### Step 6 — World overlay and HUD

- `QcOverlayRenderer.java` — `implements WorldRenderEvents.AfterTranslucent` (a functional listener object):
  - Guard: `QcClientState.active && QcClientState.overlayVisible && client.world != null`; `VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLines())`;
    then `ctx.matrixStack().push(); Vec3d cam = ctx.camera().getPos(); ctx.matrixStack().translate(-cam.x, -cam.y, -cam.z);`
    and `pop()` in a `finally`.
  - Draw, in this order (all via `WorldRenderer.drawBox(MatrixStack, VertexConsumer, x1,y1,z1,x2,y2,z2, r,g,b,a)`):
    1. every unit cell of every layer: box `origin..origin+8`, colour by cursor layer
       `(0.98,0.86,0.30,0.9f)` / filled other layer `(0.35,0.65,0.95,0.30f)`, skipped when the cell centre is more than
       128 blocks from the camera; stop drawing after 2048 boxes.
    2. walls/gates: for every unit and horizontal side whose neighbour exists in the same layer and whose flag is set,
       an outward "plate": the shared face plane at `origin + 8*(dir axis)` extended by `0.02` outward, spanning
       `y = origin.y+1 .. origin.y+7` and `1..7` along the tangent axis — wall `(0.29,0.66,1.0,0.9f)`,
       gate `(0.27,0.88,0.48,0.9f)`.
    3. the cursor cell in bright white `(1.0,1.0,1.0,0.9f)` (skipped when `layer < 1`).
    4. placed-unit markers: a `0.15` cube at each `nearbyUnits` origin corner `(0.95,0.75,0.20,0.9f)` and a `0.15` cube
       at the session anchor `(0.20,0.95,0.55,0.9f)`.
- `QcHud.java` — `static void render(DrawContext ctx, float tickDelta)`; while `QcClientState.active`, draw with
  `ctx.drawTextWithShadow(client.textRenderer, line, 4, y, 0xFFFFFF)` at 10-pixel line spacing, five lines:
  1. `QC editor  lattice: free|<a>,<b>,<c>  anchor: <x> <y> <z>`
  2. `cursor: cell <x>,<z>  layer <n>  face: <north|east|south|west|none>`
  3. `RMB place   LMB erase   Shift+RMB wall   Ctrl+RMB gate   Shift/Ctrl+LMB clear`
  4. `G exit   Y re-anchor   H duplicate layer   J save   K build   I components   U align   O overlay`
  5. the pending message in `0xFFE36E` when `messageTicks > 0`.

### Step 7 — Screens and the schematics folder

- `QcSchematics.java`:
  - `static Path dir()` — `<gameDir>/schematics` by default; if `config/qccore.json` exists and contains
    `{"schematicsDir": "<string>"}`, use that path (relative paths resolve against the game dir). Parsed with
    `com.google.gson.Gson`/`JsonObject` (bundled with Minecraft). Any read/parse failure logs
    `LOGGER.warn("qccore: could not read config/qccore.json: {}", e.getMessage())` and falls back to the default.
  - `static List<Path> list()` — `Files.list(dir())`, regular files whose lowercased name ends in `.nbt`, sorted by
    file name; a missing directory yields an empty list (it is created on the first save).
  - `static String normaliseName(String raw)` — lowercased, characters outside `[a-z0-9._-]` replaced by `_`, a
    trailing `.nbt` stripped, empty result -> `"qc_layout"`; the saved file is `<name>.nbt`
    (Litematica only lists lower-case names).
  - `static Path save(String rawName, NbtCompound tag)` / `static NbtCompound load(Path file)`.
- `QcConfigScreen.java extends Screen` (`shouldPause() == false`, `Text.translatable("qccore.screen.components")` title):
  - Five `TextFieldWidget`s for framework/row/column/wall/floor; each render pass colours the field text green when
    `Registries.BLOCK.getOrEmpty(Identifier.tryParse(normalised))` is present, red otherwise (namespace prefix added
    while parsing so bare `deepslate_bricks` is accepted).
  - The cursor-layer-neutral layout settings: three 6x18 `TextFieldWidget`s for `a`,`b`,`c` (`setTextPredicate(s -> s.matches("[0-7]?"))`)
    and a `ButtonWidget` whose label is `"lattice: free"` / `"lattice: 8x+a, 8y+b, 8z+c"`; toggling stores/clears
    `QcClientState.alignA/B/C`.
  - Four component rows (inner wall, gate, outer wall, inner column): a label with the current display path
    (`"[Not Configured]"`, `"[Generated Default: …]"`, `"[Extracted from Opened File]"` or the file name), the parsed
    size triple, and a `Choose…` button opening `QcFileBrowserScreen` in `Mode.PICK_COMPONENT`.
  - Buttons: `Apply defaults` (`Defaults.applyTo(QcClientState.palette)` + refresh all fields), `Load layout…`
    (`QcFileBrowserScreen` in `Mode.LOAD_LAYOUT`), `Done` (validates; invalid ids keep the screen open with the
    message `"fix the highlighted block ids"`; otherwise writes the fields into `QcClientState.palette`, applies the
    lattice setting and closes).
  - Loading a layout: `StructureNbt.read(file)` -> `UnitBlockConverter.convertBlockMapToLayers(...)` catching
    `IllegalStateException` -> red footer `"<file> contains no QC framework shell"`; on success replace
    `QcClientState.layers`, keep/adjust the anchor (if no session exists, `QcInteraction.enterEditorMode`), adopt the
    palette the import just configured (QCSimple behaviour) and refresh the fields; footer
    `"loaded <name>: <n> unit(s) on <m> layer(s)"`.
- `QcFileBrowserScreen.java extends Screen` — `Mode.PICK_COMPONENT(BlockPalette, component)` / `Mode.LOAD_LAYOUT`;
  `ElementListWidget` over `QcSchematics.list()` with one row per file (name, byte size); a footer label shows the
  directory, a second footer line shows errors in red; `Select` acts on the highlighted row and `Cancel` returns to
  the parent screen; an empty list renders a single grey non-selectable row `"no .nbt files in <dir>"`.
  Component mode validates in this order: `StructureNbt.validateComponentFile` -> `parseStructure` ->
  `component.isValidSize(x,y,z)`; on failure show
  `"<name> is <x>x<y>x<z>; <component> needs 7x7x1 or 7x7x3"` and keep the list open.
- `QcSaveScreen.java extends Screen` — name field (default `"qc_layout"`), a grey label with the resolved directory and
  the normalised file name, `Save` writes the current layout
  (`StructureNbt.write(UnitExporter.export(layers, palette, BlockPos.ORIGIN))`, `null` -> `"the layout is empty"`),
  and shows either `"saved <absolute path>"` (then closes) or the exception message in red.
- `QcAlignScreen.java extends Screen` — target record (captured when opened via `U`: the `nearbyUnits` record
  containing the crosshair's cell/layer), its origin, its current residues, and three `0..7` fields for `a`,`b`,`c`;
  `Apply` sends `QcAlignC2S(origin, a, b, c, PaletteCodec.toNbt(QcClientState.palette))` and closes; `Cancel` closes.
  `K`/`U` without a session/target show `"QC: no placed QC Unit in the crosshair"` (the `U` keybind resolves the
  target by scanning `nearbyUnits` for `record.origin().equals(anchor + 8*cell)` in the cursor's layer and is a no-op
  with a message when nothing matches).

### Step 8 — Mixins, metadata and build wiring

- `src/client/java/com/qccore/client/mixin/MinecraftClientMixin.java` — delete the placeholder `run` injection; add:
  - `@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true) private void qccore$attack(CallbackInfoReturnable<Boolean> cir)`
    — when `QcClientState.active && client.currentScreen == null`, call `QcInteraction.click(false)` and
    `cir.setReturnValue(false)`.
  - `@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true) private void qccore$use(CallbackInfo ci)`
    — same guard + `QcInteraction.click(true)` + `ci.cancel()`.
  - Keep the existing class-level `@Mixin(MinecraftClient.class)`; injection targets are verified to exist (both are
    private methods of `MinecraftClient`).
- `src/main/java/com/qccore/mixin/MinecraftServerMixin.java` — **delete** (empty placeholder), and set
  `"mixins": []` in `src/main/resources/qccore.mixins.json` (keep the file and its `fabric.mod.json` entry: a common
  mixin config with no entries is valid).
- `src/main/resources/fabric.mod.json` — keep id/version/license/depends/entrypoints; update `description` to
  `"In-world QC Unit building editor and structure generator."`; leave the `fabric-gametest` entrypoint to loom (Step
  8 gradle + Verification).
- `src/main/resources/assets/qccore/lang/en_us.json` (new) — `key.categories.qccore` = `"QCCore"` and the eight
  `key.qccore.*` names, plus the screen titles used as translation keys (`qccore.screen.components`,
  `qccore.screen.save`, `qccore.screen.files`, `qccore.screen.align`). All other on-screen text is `Text.literal` on
  purpose (keeps user-visible strings greppable; only keybinds and screen titles need lang entries).
- `build.gradle` — add, without touching the existing plugin/dependency/loom configuration:
  - `fabricApi { configureTests { createSourceSet = true; modId = "qccore"; enableGameTests = true; eula = true; clearRunDirectory = true; username = "Player0" } }`
    (loom 1.17.21 creates the `gametest` source set — `src/gametest/java`, `src/gametest/resources` — and the
    `runGameTest` task; do **not** set `enableClientGameTests`).
  - JUnit for the plain logic tests: `testImplementation platform('org.junit:junit-bom:6.0.0')`,
    `testImplementation 'org.junit.jupiter:junit-jupiter'`,
    `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` and `test { useJUnitPlatform() }`
    (identical to the working setup in `~/Projects/QCSimple/build.gradle`; those artifacts are already in the local
    Gradle cache). If the test source set cannot see `net.minecraft.*` (check with
    `./gradlew dependencies --configuration testCompileClasspath`), add
    `sourceSets.test.compileClasspath += sourceSets.main.compileClasspath` and the matching `runtimeClasspath` line —
    `splitEnvironmentSourceSets()` is expected to do this already.
- `src/test/resources/golden/` — copy `unit-export-baseline.txt` and `unit-import-baseline.txt` verbatim from
  `~/Projects/QCSimple/src/test/resources/golden/`.

## Critical files & anchors

- `~/Projects/QCSimple/src/main/java/com/naromil/qcsimple/…` — the source of every ported algorithm; the four
  `applyDefaultConfig()` methods (`data/InnerWall.java`, `Gate.java`, `OuterWall.java`, `InnerColumn.java`) and
  `logic/NBTGenerator.generateSimpleStructureTag` are the exact literals to copy; `logic/UnitImporter.scanUnits` and
  `data/InnerStructure.scanSides` are the two scans that must match probe-for-probe.
- `~/Projects/QCSimple/src/test/java/com/naromil/qcsimple/logic/TestFixtures.java` — copy `buildFixtureLayers()` and
  `flaggedSquare(dx, dz)` (only `Point2D` -> `UnitPos`, `QCUnit` -> `QcUnit`).
- `~/Projects/QCCore/src/client/java/com/qccore/client/mixin/MinecraftClientMixin.java` — the input-suppression seam;
  without it RMB/LMB still place and break blocks while the editor is on.
- `~/Projects/QCCore/src/main/resources/fabric.mod.json` — entrypoints (main/client) and mixin config references;
  the `fabric-gametest` entrypoint is added by loom's `configureTests`.
- `~/Projects/QCCore/src/main/java/com/qccore/world/QcBuildService.java` — the single place that validates a request
  before touching the world; every failure message listed in Step 3 is user-visible, keep the wording.

## Verification

Run everything from `~/Projects/QCCore`.

1. **Build**: `./gradlew build` -> `build/libs/qccore-1.0.0.jar` (+ `-sources.jar`) produced, no compile errors in the
   `main`, `client` and `test` source sets.
2. **Ported-behaviour tests** (`src/test/java/com/qccore/core/`): `StructureExportGoldenTest`, `StructureImportGoldenTest`
   (dump generators ported from QCSimple incl. the `x y z <id>` and `L<x> U<x>,<z> walls=…` line formats),
   `AlignmentTest`, `LayersCodecTest` (playlist round trip incl. `w`/`g` bits), `PaletteCodecTest`
   (`toNbt`/`fromNbt` round trip for all four default structures), `IndexNbtTest` (three dimensions, re-read equality).
   Registry-dependent tests call `SharedConstants.createGameVersion(); Bootstrap.initialize();` in a `@BeforeAll`.
   Run: `./gradlew test`.
   Expected observable results: the export test's sorted dump equals
   `src/test/resources/golden/unit-export-baseline.txt` line for line (**7254 lines**), the import test's dump equals
   `unit-import-baseline.txt` (**25 lines**, including `innerWall=7x7x3 [Extracted from Opened File]` and the unit
   flag lines such as `L1 U10,10 walls=0011 gates=0000`), and `AlignmentTest` asserts
   `nearestDelta(2, 0) == -2`, `nearestDelta(0, 5) == -3`, `nearestDelta(3, 3) == 0`, `snap(13, 0) == 16`,
   `snap(13, 5) == 13`, `residues(-3, 9, -1) == (5, 1, 7)`.
   A diff means the port diverged — fix the port, never the baseline copy.
3. **In-game server-side tests** (`src/gametest/java/com/qccore/gametest/QcGameTests.java`, class
   `implements FabricGameTest`, methods `public void`, annotated
   `@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)`; test origin = `ctx.getAbsolutePos(new BlockPos(0,0,0))`
   offset by `(0, 40, 0)` so the test area is air; failures thrown as `AssertionError`; success = `ctx.complete()`):
   - `buildPlacesUnitsAndRegistersRecords` — layout: layer 1, two adjacent cells `(0,0)` and `(1,0)`, both with the
     shared wall flags; palette = `Defaults`. Call `QcBuildService.build(ctx.createMockCreativeServerPlayerInWorld(), origin, layers, palette)`
     asserting `result.ok()`. Then assert `ctx.getWorld().getBlockState(origin)` is `Blocks.POLISHED_DEEPSLATE`,
     `ctx.getWorld().getBlockState(origin.add(4,4,4))` is `Blocks.AIR` (cell interior cleared),
     a deepslate-bricks block exists at the shared wall region (`origin.add(8,1,8)`..) and the index holds exactly two
     records with the expected wall/gate bits.
   - `alignMovesWholeBuilding` — after the build above, `QcAlignService.align(player, origin, 0,0,0, palette)` with the
     origin deliberately placed at a residue that requires a shift (assert `result.ok()` and that the returned message
     contains `"moved 2 QC unit(s) by"`); then assert the old origin block is now air, the shifted origin holds
     `Blocks.POLISHED_DEEPSLATE`, and the index keys are the shifted origins.
   - `buildRejectsLayerZeroAndNonCreative` — a layer-0 layout returns `ok == false` with
     `"layer 0 is not allowed (layers start at 1)"` and writes nothing; a survival mock player
     (`ctx.createMockSurvivalPlayer()`) returns `"creative mode is required to build into the world"`.
   Run: `./gradlew runGameTest` (loom's `configureTests` wiring; if the tests do not run — no `Registered test class`
   log — add `"fabric-gametest": ["com.qccore.gametest.QcGameTests"]` to `fabric.mod.json`'s `entrypoints` and re-run).
   Observable success: the run ends with `All required tests passed` and the world/log shows no failures; with
   `vmArg "-Dfabric-api.gametest.report-file=…"` a JUnit XML is written.
4. **In-world client smoke test** (`./gradlew runClient`, display `:1` is available; create a creative single-player
   world):
   - Press `G`: HUD shows `QC editor  lattice: free …` and a wireframe grid appears at the player's feet.
   - Aim at the ground 8 blocks away and RMB: a new cell outline appears; aim higher (+8 blocks) and RMB: a second
     layer appears (HUD shows `layer 2`); LMB on a cell removes it.
   - RMB on two cells, then Shift+RMB on the shared face: a blue wall plate appears; Ctrl+RMB: it turns green (gate);
     Shift+LMB: the plate disappears.
   - `I`: the components screen shows the eight default ids/structures; type `not_a_block` into framework and press
     `Done`: the field stays red and the screen refuses to close; restore it.
   - `Choose…` for the inner wall, pick a valid 7x7x1/7x7x3 `.nbt`: the row shows the size; pick a 7x7x7 file: the
     footer shows the size mismatch and nothing changes.
   - `Apply defaults`, then `Load layout…`: load a layout saved by QCSimple (e.g. export one from the desktop tool) and
     confirm the cells/walls appear in the overlay.
   - `J`: save as `demo` -> `run/schematics/demo.nbt` exists (verify the absolute path printed in the screen), and
     opening that file in QCSimple (`cd ~/Projects/QCSimple && ./gradlew run`) reproduces the same unit/wall layout.
   - `K`: the layout is built into the world (blocks + shell columns appear, cells are cleared inside); the chat line
     `built <n> QC unit(s) on <m> layer(s) at <x>, <y>, <z>` appears.
   - `U` while looking at the built unit, enter `0,0,0`, `Apply`: the building shifts to the nearest `8x,8y,8z` lattice
     position (blocks gone from the old spot, present at the new one, chat reports the delta).
   - Enter editor mode again and place a unit right next to the shifted building: the new unit snaps to that
     building's lattice (its cell shares the boundary plane), i.e. no half-block seam.
   - `O` hides the overlay; `G` leaves the editor and RMB/LMB behave vanilla again.
5. **Headless dedicated-server smoke**: create `run/eula.txt` with `eula=true`, start `./gradlew runServer` as a
   background service (ready when the log contains `Done (`), then send `qc info` to its stdin and read the output:
   expect `0 QC unit(s) in minecraft:overworld` and a "within 64 blocks of" line for the spawn position; then send
   `stop`. Re-run with a world where step 4 built units (`run/saves/<world>` copied into `run/world`): `/qc info`
   must report the unit count that was built (proves the persistent index survives a restart).

## Assumptions & contingencies

- **User decisions taken** (do not revisit): in-world editing only (no 2D canvas screen); vanilla `.nbt` only; an
  alignment change moves the connected building immediately; a build replaces unit cells and the shell outside their
  exposed faces.
- **Component files with block entities** (chests, signs): the vanilla structure format's per-block `nbt` payload is
  ignored (only `Name`/`Properties` are read), matching QCSimple; not a bug to fix in this change.
- **`minecraft:stone` fallback**: deliberately not ported (see Step 1, `BlockComponent`).
- **If `Bootstrap.initialize()` cannot run in the JUnit source set** (registry init fails headless): move the
  registry-dependent tests (`StructureExportGoldenTest`, `StructureImportGoldenTest`, `PaletteCodecTest`,
  `IndexNbtTest`'s palette parts) into `src/gametest/java` as gametests that read the same baseline files from the mod
  jar's resources (`QcGameTests.class.getResourceAsStream("/golden/unit-export-baseline.txt")`, i.e. copy the
  baselines into `src/main/resources/golden/` in that case) and keep JUnit for `AlignmentTest`/`LayersCodecTest`.
- **If `./gradlew runGameTest` does not exist or the tests are not picked up**: check that loom's `configureTests`
  created the `gametest` source set (`ls src/gametest/java`), that the test class is public with a no-arg constructor,
  and add the `fabric-gametest` entrypoint to `fabric.mod.json` manually (harmless in production because fabric-api's
  fat jar does not ship `fabric-gametest-api-v1`); if the task is still missing, add a manual run config
  `loom { runs { gameTest { server(); vmArg "-Dfabric-api.gametest"; vmArg "-Dfabric-api.gametest.report-file=${buildDir}/gametest-report.xml"; runDir "build/gametest" } } }`
  and run `./gradlew runGameTest`.
- **If the gametest module turns out unusable on this toolchain**, the fallback headless proof of the server side is
  step 5 plus `/qc` command output; the pipeline correctness is already pinned by step 2.
- **If the input mixin fails to apply** (`doAttack`/`doItemUse` are unambiguous private methods in 1.20.1, verified):
  fall back to reading `client.options.attackKey.wasPressed()`/`useKey.wasPressed()` in
  `ClientTickEvents.END_CLIENT_TICK` and accept that one extra vanilla block break/place may land per click.
- **Build cost**: a large layout (up to the 4096-unit cap) issues ~1.5M `setBlockState` calls with
  `NOTIFY_ALL | FORCE_STATE`; this can take seconds to tens of seconds on the server thread. That is accepted for this
  change (no chunk-batching optimisation), and the cap message tells the player the limit.
- **Litematica folder**: `<gameDir>/schematics` is Litematica's default and needs no probing of Litematica's own
  config file; users with a custom base directory set it via `config/qccore.json` (`{"schematicsDir": "…"}`). Files are
  written lower-cased because Litematica only lists lower-case names.
