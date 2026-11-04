# TNT Suite (Meteor Client addon)

Target: Minecraft 26.1.2, Fabric Loader 0.19.2+, Java 25, Meteor Client 26.1.2-SNAPSHOT.

Modules (category "TNT Suite"):
- **auto-cart** (main module): places a rail near each target, puts a TNT minecart on it, lights it.
- **auto-tnt**: places TNT blocks around each target and lights them.

## Build

Recommended: take the Gradle wrapper and `gradle/libs.versions.toml` from the official
https://github.com/MeteorDevelopment/meteor-addon-template and keep this project's `src/`,
`build.gradle.kts` and `gradle.properties`. Then:

    ./gradlew build

The jar is in `build/libs/`. Put it in `mods/` next to Meteor Client 26.1.2.

## NOT yet compiled or tested

This was written without being able to compile against Meteor 26.1.2. If the build fails,
the likely culprits are all isolated:

| File | What may need a tweak |
|---|---|
| `util/Interact.java` | `mc.gameMode.useItemOn / interact`, `Rotations.rotate / getYaw / getPitch` |
| `util/Hotbar.java` | `InvUtils.findInHotbar / swap / swapBack`, `FindItemResult.isOffhand / isMainHand` |
| `targeting/TargetSettings.java` | `Friends.isFriend`, `Player.isAlliedTo`, `mc.level.players()` |
| `placement/*` | `BlockState.canBeReplaced / isFaceSturdy`, `BlockTags.RAILS`, `level.getEntities(...)` |
| `gradle/libs.versions.toml` | Loom version and plugin id |

Names are Mojang mappings (26.1.x is unobfuscated).

## Adding a module

1. Create `modules/MyModule.java` extending `SuiteModule` (implement `reset()`).
2. Reuse `TargetSettings`, `MotionTracker`, `Hotbar`, `Interact`.
3. Add `modules.add(new MyModule());` in `TntSuiteAddon.onInitialize()`.

## Build without installing anything (GitHub Actions)

1. Create a free GitHub account and a new repository.
2. Upload the contents of this folder (including the hidden `.github` folder).
3. Open the repo's **Actions** tab, then the "Build jar" run, then download the **tnt-suite-jar** artifact.
4. Unzip it and put the `.jar` in your `mods` folder next to Meteor Client 26.1.2.

If the run fails, open the failed step and send the error text to Claude.
