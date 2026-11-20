# QCCore

Minecraft 1.20.1 mod for **Fabric** and **Forge**, built from one shared code base.

## Planning QC buildings in the world

QCCore is an Effortless-Building-style tool for **QC Units** (9x9x9 cells on an 8-block pitch that
share their boundary planes). You queue up modifications as a **plan**, see them as wireframes and
ghost blocks, and only then **apply** the whole plan to the world in one step (creative only, undoable)
or **export** it as a vanilla structure `.nbt`.

The QC rules are those of **qckit** (`naromil/worldedit`: `builds/QCSYSTEM.md`, `quarterchunk/qckit/`), ported
into the mod: a plan is made of **buildings** (units tagged with a building, each built from a qckit
template - house, shop, hall, office, warehouse, tower, academy, townhouse, villa, chapel, manor, cottage,
workshop, library, loft, qc_raw - with its own roof, facades, piers, partition, doors and lights) and
**roads** (qckit's 7-wide road). The geometry never names a block: every building and road reads its blocks
from a **style**, the blocks chosen role by role (frame, beam, floor, wall, glass, row, roof, ...) in the
**block chooser**. Nothing is ever filled in for you: a role nobody chose stops the apply and is listed by name.
The block chooser shows a live 3D sample of the selected building or road style (beside the roles on a wide
screen, behind the **3D** chip on a narrow one; drag to turn it), built from exactly the blocks you chose.
QCSimple's nine components (column, row, inner column...) are outdated rules and only survive as the
`qcsimple` template, so plans and layouts made before still build.

1. Press **G** to enter the editor: the lattice is anchored at your feet (layer 1 = your cell), or
   on the lattice of a nearby QC building. **Y** re-anchors at the crosshair. Leaving and re-entering
   the editor keeps a non-empty plan where it was; only **Y** moves it.
2. Pick a **tool** (**N** cycles, or **B** for the tools screen, where **1-9** pick a tool directly) and a
   **shape** (**V** cycles). The HUD panel in the top-left corner shows the tool, shape progress, plan and
   cursor; **hold Left Alt** to see every control with your current key bindings. Every control in the
   screens has a tooltip; number fields are steppers (click the arrows or scroll, Shift steps by 8).

   | Tool | Right click over the shape |
   |---|---|
   | building | adds QC units to the active building (a new building with the current design if none is active) |
   | erase | removes planned units, roads and cleared cells |
   | assign | moves planned units into the active building |
   | partition: walls / gates / open | overrides the building's partition on every shared side inside the shape: a party wall, a wall with a gate (rooms), or nothing (a hall) |
   | clear terrain | clears the cells (local y 1..8, plus a 0-4 block margin outside the exposed sides and above) and optionally levels the floor plane with a ground block |
   | road | lays a QC road: qckit's 7-wide negative space, lanes 1-4, joint lights every 8 blocks, a medallion at bends/tees/crossings |
   | template | stamps a saved template at the crosshair; **R** turns it a quarter |

   | Shape | Clicks |
   |---|---|
   | single | 1 (hold to paint) |
   | line | 2 (dominant horizontal axis) |
   | floor / ring | 2 corners (filled rectangle / its outline) |
   | box / hollow box | 2 corners, then aim up or down for the height |

   The shape under the crosshair is outlined in the tool's colour as what the tool would make: a unit's
   framework (corner posts and beams) for building, template, assign and erase, the shared sides (with a
   doorway for gates) for partitions, the 7-wide floor for a road, the emptied volume for a clear. The
   white box marks the aimed cell only where the preview lands elsewhere (beside the aimed face, a
   template, the height point of a box).

   **LMB** cancels a shape in progress, otherwise erases the aimed cell. **Shift/Ctrl + RMB/LMB** set or
   clear a single wall/gate on the aimed face, as before.
3. **Buildings and blocks** (tools screen, Building panel): **‹ ›** picks the active building, **New** makes
   the next unit start a new one, **Pick aimed** takes the building of the aimed unit. **Design…** sets the
   template and its overrides: roof (hip, gable, gambrel, mansard, flat, garden, none; rise and ridge),
   facades per storey (a mask or a qckit piece such as framed, arched, twin, bay, balcony, timber, arcade;
   `bay|balcony` is a rhythm along the run), run ends and blind faces, piers (normal, dense, 2x2, thick),
   partition, doors, lights, and row / sills / plinth / stairs / portal. **Blocks…** opens the block
   chooser: the plan's styles on the left, the roles the plan actually reaches on the right, the ones still
   missing in red. Type an id (states allowed, `oak_log[axis=x]`), **⌕** search every block, **✋** take
   the held block. An empty optional role leaves its part out; an empty role with a fallback repeats it
   (beam = frame, wall_upper = wall, ...). Doors face the roads, so lay the roads before or after the
   building, the generator sees them either way. Every unit of a qckit building clears its footprint
   (cell + 2-block shell) when applied.
4. **Modifiers** (tools screen): mirror east-west and/or north-south through a mirror cell, and an array
   (copies + a step in cells along x / layer / z). They apply to every shape and to templates.
5. **Undo/redo** plan edits with **Z** / **Shift+Z** (100 steps). **H** copies the aimed layer one up.
6. **K** applies the plan to the world. **/qc undo** (or "Undo apply" in the tools screen)
   reverts the last 10 applies (in memory; block-entity contents are not restored), and **/qc redo**
   (or "Redo apply") puts an undone apply back until the next apply.
   **J** exports the plan as `.nbt` to the schematics folder, sparse by default, or with the clearing
   written as air.
7. **M** toggles the ghost-block preview, **O** the wireframes, **I** opens the lattice, layout import
   and the legacy QCSimple palette, **U** re-aligns a placed building.
8. **/qc scan [radius]** (default 32, up to 64) finds QC buildings that already stand round you, built by
   hand, by worldedit's qckit or before the mod was installed, and adds them to the mod's unit memory: the
   grid they sit on, every unit whose twelve framework edges are built, wall/gate/open between neighbours,
   doors on the ground floor and the road cells in front (a laid 7x7 floor, never plain terrain). Units the
   mod built itself keep their record. It only reads the world; `/qc forget` drops what it added.

**Street decor** (qckit's `decor.py`): add a decor style in the block chooser (**+ decor**) and choose its
roles (pot, planter, flower, seat, goods, logs, bush, post, lamp, awning, trunk, crown, basin; `a,b,c` picks one
per prop). The plan's streets then get props on the ground round the buildings: planters and benches in front of
houses, stalls and crates by shops, lamps and benches by halls, potted plants beside doors, trees and flowers in
grass yards, a fountain or tree at some crossings, never in a doorway. A prop whose roles are not all chosen is
left out. **decor on/off**, **density** and **seed** sit under the decor style's roles.

**Templates** (tools screen → Templates; double-click a template to use it) save the plan's geometry —
buildings with their designs and style names, partitions, roads, cleared cells — to
`<gameDir>/qccore/templates/<name>.qct.nbt`, **with every block choice stripped**. Stamping a template makes
new buildings and adds its styles empty (a style of the same name you already filled is used as it is);
fill them in the block chooser, then apply. The template screen previews the selected template in 3D, built
with default blocks (the brick-and-spruce house and high street of the QC Shorts brickworks build) where its
styles are empty; the defaults are only for that picture and never go into your plan. **Kits** (block chooser → Kits) save one style's blocks as
editable JSON in `<gameDir>/qccore/kits/<name>.json` (`{"kind": "building" | "road", "blocks": {role: id}}`)
and load them into any style of the same kind. As in qckit there are no preset kits.

## Requirements

- JDK 21 (the build targets Java 17 bytecode, which is what Minecraft 1.20.1 runs on)

## Build

```bash
./gradlew build
```

One build produces both mods:

| Loader | Jar | Install alongside |
|---|---|---|
| Fabric | `fabric/build/libs/qccore-fabric-<version>.jar` | [Fabric Loader](https://fabricmc.net/use/installer/) and [Fabric API](https://modrinth.com/mod/fabric-api) |
| Forge | `forge/build/libs/qccore-forge-<version>.jar` | [Forge](https://files.minecraftforge.net/) 47.x for 1.20.1 |

Client and server must run the same loader's jar; the two network protocols are not compatible with each other.

## Develop

```bash
./gradlew :fabric:runClient   # dev Fabric client with the mod loaded
./gradlew :forge:runClient    # dev Forge client
./gradlew :fabric:runServer   # dev Fabric dedicated server (working dir fabric/run)
./gradlew :forge:runServer    # dev Forge dedicated server (working dir forge/run)
./gradlew :common:test        # plain JUnit tests (logic without a running game)
./gradlew :fabric:runGameTest # in-game tests: world mutation and the golden structure baselines
./gradlew genSources          # decompile Minecraft for IDE navigation
```

For IDE setup, open the folder as a Gradle project (IntelliJ IDEA).

## Layout

```
common/   the mod itself, loader-neutral: core logic, world services, packets, client screens/renderer/HUD
          src/main/java, src/main/resources (assets), src/test/java (JUnit)
fabric/   Fabric glue: entry points, Fabric API networking and events, the input mixin, fabric.mod.json
          src/gametest: the "qccore-gametest" test mod and the golden baselines
forge/    Forge glue: @Mod entry point, a SimpleChannel for the packets, Forge events, META-INF/mods.toml
```

All code uses Mojang's official names. `common` must not import a loader API: it reaches the loader through
`com.qccore.platform.QcPlatform` (game directory, sending packets), and the loader glue calls plain hook
methods for everything incoming (packets, ticks, join/leave, commands, keybindings, world and HUD rendering,
mouse input). Each loader's jar bundles the `common` classes, remapped to that loader's runtime names.

Anything that touches the block registry needs a running game, because Minecraft's registries cannot be
bootstrapped in a plain JUnit JVM: those tests belong in `fabric/src/gametest/java`. The golden baselines live
with that test mod, so they never reach a shipped jar. The gametests exercise the shared `common` code, so they
cover both loaders' building logic; the Forge glue itself is checked by hand (see `AGENTS.md`).

## Versions

Set in `gradle.properties`: Minecraft `1.20.1`, Mojang mappings, Architectury Loom `1.17.487`
(the Loom fork that builds both loaders), Fabric Loader `0.19.5`, Fabric API `0.92.12+1.20.1`,
Forge `47.4.29`.

## Icon

`common/src/main/resources/assets/qccore/icon.png` is the placeholder from the Fabric example mod (CC0) -
replace it with your own before publishing.

## License

MIT - see [LICENSE](LICENSE).
