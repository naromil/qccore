# QCCore

Minecraft 1.20.1 mod for **Fabric** and **Forge**, built from one shared code base.

## Planning QC buildings in the world

QCCore is an Effortless-Building-style tool for **QC Units** (9x9x9 cells on an 8-block pitch that
share their boundary planes). You queue up modifications as a **plan**, see them as wireframes and
ghost blocks, and only then **apply** the whole plan to the world in one step (creative only, undoable)
or **export** it as a vanilla structure `.nbt`.

1. Press **G** to enter the editor: the lattice is anchored at your feet (layer 1 = your cell), or
   on the lattice of a nearby QC building. **Y** re-anchors at the crosshair.
2. Pick a **tool** (**N** cycles, or **B** for the tools screen, where **1-9** pick a tool directly) and a
   **shape** (**V** cycles). The HUD panel in the top-left corner shows the tool, shape progress, plan and
   cursor; **hold Left Alt** to see every control with your current key bindings. Every control in the
   screens has a tooltip; number fields are steppers (click the arrows or scroll, Shift steps by 8).

   | Tool | Right click over the shape |
   |---|---|
   | units | adds QC units building the selected components |
   | erase | removes planned units, roads and cleared cells |
   | paint components | sets the selected components on planned units |
   | partition: walls / gates / open | inner walls, inner walls with a gate (rooms), or nothing (a hall) on every shared side inside the shape |
   | clear terrain | clears the cells (local y 1..8, plus a 0-4 block margin outside the exposed sides and above) and optionally levels the floor plane with a ground block |
   | road | lays a QC road: qckit's 7-wide negative space, lanes 1-4, joint lights every 8 blocks, a medallion at bends/tees/crossings |
   | template | stamps a saved template at the crosshair; **R** turns it a quarter |

   | Shape | Clicks |
   |---|---|
   | single | 1 (hold to paint) |
   | line | 2 (dominant horizontal axis) |
   | floor / ring | 2 corners (filled rectangle / its outline) |
   | box / hollow box | 2 corners, then aim up or down for the height |

   **LMB** cancels a shape in progress, otherwise erases the aimed cell. **Shift/Ctrl + RMB/LMB** set or
   clear a single wall/gate on the aimed face, as before.
3. **Components**: in the tools screen choose which of the nine components new units build (all, "shell
   only" = framework + floor, "skin only", or any combination) — build just the framework of a block of
   units, then paint the rest on later. A unit planned with all nine components clears its footprint
   (cell + 2-block shell) when applied; a partial unit only adds blocks to what stands there.
4. **Modifiers** (tools screen): mirror east-west and/or north-south through a mirror cell, and an array
   (copies + a step in cells along x / layer / z). They apply to every shape and to templates.
5. **Undo/redo** plan edits with **Z** / **Shift+Z** (100 steps). **H** copies the aimed layer one up.
6. **K** applies the plan to the world. **/qc undo** (or "Undo apply" in the tools screen)
   reverts the last 10 applies (in memory; block-entity contents are not restored), and **/qc redo**
   (or "Redo apply") puts an undone apply back until the next apply.
   **J** exports the plan as `.nbt` to the schematics folder, sparse by default, or with the clearing
   written as air.
7. **M** toggles the ghost-block preview, **O** the wireframes, **I** opens the nine components and
   the lattice, **U** re-aligns a placed building.

**Templates** (tools screen → Templates; double-click a template to use it) save the whole plan — units, flags, component masks, roads,
cleared cells and the palette — to `<gameDir>/qccore/templates/<name>.qct.nbt`; pick one to stamp it with
the template tool. **Kits** (tools screen → Kits/roads) are the in-game counterpart of qckit's per-build
block choices: the palette, the road styles and the ground block as editable JSON in
`<gameDir>/qccore/kits/<name>.json`. As in qckit there are no preset road styles: choose the blocks of each
lane (block states allowed, e.g. `oak_log[axis=x]`) and save them as a kit.

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
