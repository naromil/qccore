# QCCore

Fabric mod for Minecraft 1.20.1.

## Requirements

- JDK 21 (the build targets Java 17 bytecode, which is what Minecraft 1.20.1 runs on)

## Build

```bash
./gradlew build
```

The remapped mod jar lands in `build/libs/qccore-<version>.jar`. Put it in your `mods/` folder alongside
[Fabric Loader](https://fabricmc.net/use/installer/) and [Fabric API](https://modrinth.com/mod/fabric-api).

## Develop

```bash
./gradlew runClient    # dev client with the mod loaded
./gradlew runServer    # dev dedicated server
./gradlew runDatagen   # data generation (no-op until a DataGenerationEntrypoint is added)
./gradlew genSources   # decompile Minecraft for IDE navigation
```

For IDE setup, open the folder as a Gradle project (IntelliJ IDEA) or run `./gradlew eclipse` (Eclipse).

## Layout

```
src/main/java      common code, loaded on client and server
src/main/resources fabric.mod.json, main mixin config, assets
src/client/java    client-only code (rendering, input, keybinds)
src/client/resources client-only mixin config
```

Client code lives in its own source set, so it can reference client-only Minecraft classes without them being
available on the server. Add new packages under `com.qccore` and mirror them in `src/client/java` for client code.

## Versions

Set in `gradle.properties`: Minecraft `1.20.1`, Yarn `1.20.1+build.10`, Fabric Loader `0.19.5`,
Fabric API `0.92.12+1.20.1`, Loom `1.17.21`. Check <https://fabricmc.net/develop> for newer builds.

## Icon

`src/main/resources/assets/qccore/icon.png` is the placeholder from the Fabric example mod (CC0) - replace it
with your own before publishing.

## License

MIT - see [LICENSE](LICENSE).
