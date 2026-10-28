# Porting to a new Minecraft version

WynnTitles supports exactly the Minecraft version that Wynntils supports. As of October 2026 that is 1.21.11; Wynntils' next port (the `26.4` branch on GitHub) targets 26.4.

## Where Minecraft and Wynntils code lives

Most of the mod is plain Java and should not need changes: `api/`, `account/`, `catalog/`, `titles/`, and `achievements/AchievementTracker`.

Expect to touch:

| File | Why |
|---|---|
| `mc/Mc.java` | Screens and chat. 26.2 moved `setScreen` to `Minecraft#gui` and chat to the new `Hud` class. |
| `mc/screen/*` | owo-ui screens. Check owo-lib's changelog for the target version. |
| `mc/Icons.java`, `mc/UnlockToast.java`, `mc/screen/AchievementIcon.java` | Resource reloaders, item components (damage, custom model data), toasts and item rendering in GUIs. `icons/ItemModelIndex.java` reads the item model definition format, which Mojang changes every few versions. |
| `mc/TitleRenderer.java` | Components and styles (rarely changes). |
| `mc/WynnFonts.java` | `Style.withFont(new FontDescription.Resource(...))` and the font resource path; 1.21.9 changed fonts from ids to `FontDescription`. |
| `mc/MojangSession.java` | `services().sessionService().joinServer(...)`. |
| `mc/Nametags.java`, `mixin/WynntilsNametagMixin.java` | Hooks a **private** Wynntils method and constructs its private `CustomNametag` record. Re-check both after every Wynntils update. |
| `achievements/WynntilsDetectors.java` | Wynntils model APIs. |
| `WynnTitles.java` | Keybinding registration. |

## Moving to the 26.x line (unobfuscated)

1. Wait for Wynntils, owo-lib and Cloth Config builds for the target version.
2. In `build.gradle`, change the plugin id `net.fabricmc.fabric-loom-remap` to `net.fabricmc.fabric-loom`, drop `mappings loom.officialMojangMappings()`, and rename the remapping configurations (`modImplementation` → `implementation`, `modCompileOnly` → `compileOnly`, `modLocalRuntime` → `localRuntime`, `modApi` → `api`).
3. In `gradle.properties`, set `java_version=25` and bump `minecraft_version`, `loader_version`, `fabric_api_version`, `wynntils_version_id`, `owo_version`, `cloth_config_version` and `modmenu_version`. The mixin config's `compatibilityLevel` goes to `JAVA_25`.
4. The Gradle daemon already runs on Java 25 (`gradle/gradle-daemon-jvm.properties`), so no toolchain change is needed.
5. Build, then fix the files in the table above.

Because the code already uses Mojang's names, the unobfuscated jars need no renames.
