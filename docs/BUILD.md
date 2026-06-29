# Building Rory's Inventory Tweaks

Target: Minecraft **1.2.5** · Forge **3.4.9.171** · LWJGL **2.9.4**

---

## Build system

The upstream project used **Maven** (`pom.xml`). Rory's Inventory Tweaks uses a **direct `javac` build script** (`build.bat`) that follows the same pattern established by Rory's Excavation and Rory's Utilities — no Maven, no MCP workspace, no stubs.

`pom.xml` is kept for historical reference only. Do not use it for actual builds.

---

## What the build requires

The mod is compiled directly against the unmodified Minecraft 1.2.5 and Forge 3.4.9.171 jars — **no MCP deobfuscation, no stubs required.** All Minecraft game classes are accessed by their obfuscated names (`vp`, `gb`, `aan`, `afu`, etc.) which are present verbatim in the runtime jars.

`BaseMod` (the ModLoader base class that `mod_InvTweaks` extends) ships inside the Forge 3.4.9.171 jar at the default-package root. ModLoader does not need to be separately patched into anything.

---

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| **Java JDK** | 8 | Must include `javac` and `jar`. JDK 8 avoids `-source`/`-target` compatibility warnings. JDK 11–21 also works. **A JRE alone is not sufficient.** |
| `minecraft-1.2.5-client.jar` | 1.2.5 | Prism caches it at `%APPDATA%\PrismLauncher\libraries\com\mojang\minecraft\1.2.5\` after first launch. |
| `forge-1.2.5-3.4.9.171-client.jar` | 3.4.9.171 | Prism caches at `%APPDATA%\PrismLauncher\libraries\net\minecraftforge\forge\1.2.5-3.4.9.171\` |
| `lwjgl-2.9.4-nightly-20150209.jar` | 2.9.4 | Prism caches at `%APPDATA%\PrismLauncher\libraries\org\lwjgl\lwjgl\lwjgl\2.9.4-nightly-20150209\` |
| `lwjgl_util-2.9.4-nightly-20150209.jar` | 2.9.4 | Same directory as lwjgl above. |

### Installing a JDK

**Eclipse Temurin JDK 8** from [https://adoptium.net/](https://adoptium.net/) is recommended.

After installing, either:
- Add `C:\Program Files\Eclipse Adoptium\jdk-8.x.x.x-hotspot\bin` to your `PATH`, **or**
- Set `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.x.x.x-hotspot`

`build.bat` checks both automatically.

### Getting the Prism jars

The jars are downloaded automatically when Prism Launcher launches the 1.2.5 instance for the first time. If you have the `1.2.5 com mods` instance already imported, they will be present. If not, launch the instance at least once so Prism fetches and caches the libraries.

---

## Build (Windows)

```bat
build.bat
```

That's it. The script:
1. Validates `javac` is reachable.
2. Validates all four Prism jars exist.
3. Compiles all 34 source files from `src/` and `src/invtweaks/`.
4. Copies `.dat` resource files, `invtweaks/lang/*.properties`, and `doc/*.txt` into `build/classes/`.
5. Packages `build/classes/` into `build/libs/rorys-invtweaks-0.9.0.zip`.

On success:

```
Build successful: build\libs\rorys-invtweaks-0.9.0.zip
```

---

## Build (Linux / macOS)

No dedicated shell script yet. Use the equivalent `javac` invocation:

```bash
PRISM="$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/libraries"
# adjust PRISM path to your Prism installation

MC="$PRISM/com/mojang/minecraft/1.2.5/minecraft-1.2.5-client.jar"
FORGE="$PRISM/net/minecraftforge/forge/1.2.5-3.4.9.171/forge-1.2.5-3.4.9.171-client.jar"
LWJGL="$PRISM/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"
LWJGL_UTIL="$PRISM/org/lwjgl/lwjgl/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar"

mkdir -p build/classes/invtweaks/lang build/classes/doc build/libs

javac -source 8 -target 8 \
  -classpath "$MC:$FORGE:$LWJGL:$LWJGL_UTIL" \
  -d build/classes \
  src/*.java src/invtweaks/*.java

cp src/DefaultConfig.dat src/DefaultTree.dat build/classes/
cp src/invtweaks/lang/*.properties build/classes/invtweaks/lang/
cp src/doc/*.txt build/classes/doc/

cd build/classes
jar cMf ../../build/libs/rorys-invtweaks-0.9.0.zip .
```

---

## Expected ZIP contents

```
DefaultConfig.dat
DefaultTree.dat
InvTweaks.class
InvTweaksConfig.class
InvTweaksConfigInventoryRuleset.class
InvTweaksConfigManager.class
InvTweaksConfigProperties.class
InvTweaksConfigSortingRule.class
InvTweaksConfigSortingRuleType.class
InvTweaksContainerManager.class
InvTweaksContainerSection.class
InvTweaksContainerSectionManager.class
InvTweaksGuiIconButton.class
InvTweaksGuiSettings.class
InvTweaksGuiSettingsAbstract.class
InvTweaksGuiSettingsAdvanced.class
InvTweaksGuiSettingsButton.class
InvTweaksGuiShortcutsHelp.class
InvTweaksGuiSortingButton.class
InvTweaksGuiTooltipButton.class
InvTweaksHandlerAutoRefill.class
InvTweaksHandlerAutoRefill$1.class
InvTweaksHandlerShortcuts.class
InvTweaksHandlerShortcuts$1.class
InvTweaksHandlerShortcuts$ShortcutConfig.class
InvTweaksHandlerSorting.class
InvTweaksLocalization.class
InvTweaksModCompatibility.class
InvTweaksObfuscation.class
InvTweaksObfuscationGuiButton.class
InvTweaksShortcutMapping.class
InvTweaksShortcutType.class
mod_InvTweaks.class
invtweaks/InvTweaksConst.class
invtweaks/InvTweaksItemTree.class
invtweaks/InvTweaksItemTreeCategory.class
invtweaks/InvTweaksItemTreeItem.class
invtweaks/InvTweaksItemTreeListener.class
invtweaks/InvTweaksItemTreeLoader.class
invtweaks/lang/da_DK.properties
invtweaks/lang/de_DE.properties
invtweaks/lang/en_US.properties
invtweaks/lang/es_ES.properties
invtweaks/lang/fr_FR.properties
invtweaks/lang/ja_JP.properties
invtweaks/lang/ko_KR.properties
invtweaks/lang/nl_NL.properties
invtweaks/lang/pl_PL.properties
invtweaks/lang/pt_PT.properties
invtweaks/lang/ru_RU.properties
invtweaks/lang/sv_SE.properties
invtweaks/lang/zh_CN.properties
invtweaks/lang/zh_TW.properties
doc/README.txt
doc/README-fr.txt
doc/license.txt
```

---

## Classpath order matters

Put `minecraft-1.2.5-client.jar` **before** `forge-1.2.5-3.4.9.171-client.jar` on the classpath. Forge contains a `BaseMod.class` and `ModLoader.class` at the default-package root; the MC jar contains the complete set of obfuscated game classes. Placing MC first ensures the obfuscated classes resolve from the correct jar.

---

## Why the upstream Maven build does not work today

| Blocker | Root cause |
| --- | --- |
| Missing `minecraft.jar` at `~/.minecraft/bin/` | The `pom.xml` `systemPath` references the old pre-2012 launcher layout. Modern Minecraft installations use versioned jars under `~/.minecraft/versions/`. |
| Missing `BaseMod` | The `pom.xml` assumed ModLoader was patched into `minecraft.jar`. Prism handles this differently — `BaseMod` is provided by `forge-1.2.5-3.4.9.171-client.jar`. |
| Missing LWJGL at `~/.minecraft/bin/` | Same old-launcher layout issue. |

`build.bat` resolves all three by using the Prism Launcher library cache, which is populated automatically when you run the instance.

---

## Obfuscation utility

`obfutils.sh` is the upstream developer's shell script for bulk-renaming obfuscated class name references when porting to a new Minecraft version:

```bash
./obfutils.sh -class OLD_CLASSNAME NEW_CLASSNAME
```

It runs `sed -i` over all `src/*.java` files. Only needed if MC updates its obfuscation mappings, which does not apply at the fixed 1.2.5 target.
