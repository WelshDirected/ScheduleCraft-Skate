# Schedule I Skateboard for Minecraft

A Fabric mod for **Minecraft 26.3** that adds **Schedule I's skateboard** to Minecraft. Press **F2** to
step on, and you ride with Schedule I's physics, movement, camera and board model inside your own world.
Press F2 again to step off.

Minecraft is the host: its world, blocks and collision stay real. Schedule I's skateboard code
(`Skateboard` and `SkateboardCamera`) is ported line by line to Java and runs inside Minecraft at Unity's
50 Hz physics rate.

> **This repository contains no Schedule I content.** There are no models, textures, sounds or game
> files here. You need your own copy of Schedule I. A converter script reads the boards from your install
> into a resource pack that stays on your PC. Without it, the mod runs with a placeholder board and
> approximate physics.
>
> You don't need Schedule I running to play, only installed when you run the converter. The mod never
> launches, patches or connects to Schedule I.

## Features
- **Riding:** push, coast, brake, carve, and an ollie you charge by holding Space.
- **Hover physics:** four hover springs, steering torque, lateral grip and a rotation clamp, using
  Schedule I's own numbers and curves.
- **Terrain:** grass, dirt, sand, gravel and snow slow you down, as in Schedule I (the offroad board doesn't care).
- **Rain:** rain changes the handling, using Schedule I's rain settings.
- **Camera:** a chase camera that follows behind, a right-click orbit, and an FOV kick at speed.
- **Boards:** all of Schedule I's boards with their own stats (Default, Cheap, Lightweight, Cruiser,
  Offroad, Golden). F6 cycles through them.
- **Minecraft collision:** walls stop you, slabs and stairs step you up, and riding doesn't cause fall damage.

## Controls (while riding)
| Key | Action |
|---|---|
| W | push |
| S | brake |
| A / D | carve |
| hold Space, release | ollie (hold up to 0.5 s for full height) |
| W / S in the air | pitch the board |
| hold Right-click + mouse | orbit the camera |
| F2 | step on / off |
| F6 | next board |

You can rebind both keys under *Controls → Schedule I Skateboard*. F2 is also Minecraft's screenshot key.
In a world the skateboard takes it; screenshots still work from menus, or you can rebind either key.

## Requirements
- Minecraft Java **26.3**
- [Fabric Loader](https://fabricmc.net/use/) **0.19.5+** and [Fabric API](https://modrinth.com/mod/fabric-api) **0.161.0+26.3**
- Your own copy of **Schedule I** (Steam, Mono build) for the real boards
- To convert the boards: **Python 3.10+** with `UnityPy` (1.25+), `TypeTreeGeneratorAPI` and `Pillow`

## Install
1. Install Fabric Loader and Fabric API for 26.3.
2. Put `s1skate-<version>.jar` (from Releases, or build it as described below) into `.minecraft/mods`.
3. Convert the boards from your own Schedule I install:
   ```
   pip install UnityPy TypeTreeGeneratorAPI Pillow
   python converter/extract_s1_skateboards.py "C:/Program Files (x86)/Steam/steamapps/common/Schedule I" "%APPDATA%/.minecraft/resourcepacks/S1Skate-Local"
   ```
4. In Minecraft, enable **S1Skate-Local** under *Options → Resource Packs*.

Rerun step 3 if a Schedule I update changes the skateboards. **Don't share or upload the generated pack.**
It contains the game's assets.

## Build from source
This needs JDK 25. The Minecraft Launcher ships one in `runtime/java-runtime-epsilon`.
```
gradlew build
```
The jar is written to `build/libs/`.

### Tests
- **Headless physics bench.** It runs the same sim on a flat floor without Minecraft and prints hover
  height, push speeds, top speed, ollie height, carve rate, braking and grass drag:
  ```
  java -cp "build/classes/java/client;<joml.jar>;<gson.jar>" bench/SimBench.java <path/to/boards.json> board
  ```
- **Scripted in-game ride:** `gradlew runSelftest`. It needs a world copied to `run/saves/S1Test` and the
  pack in `run/resourcepacks/`. It builds a test pad, rides it, and writes logs and screenshots to `run/`.

## How it works
| Part | File |
|---|---|
| Port of `Skateboard`: rigidbody, hover PID, push/jump coroutines, friction, clamps | `src/client/java/dev/s1skate/client/sim/SkateSim.java` |
| Unity `AnimationCurve` evaluation (Hermite and weighted Bezier) | `sim/UnityCurve.java` |
| `SkateboardSettings` and rain blending | `sim/SkateSettings.java` |
| Port of `SkateboardCamera` (keyboard and mouse path) | `SkateCamera.java` |
| Drives the player, routes collisions through Minecraft's `move()`, renders the board | `SkateController.java` |
| Unity ↔ Minecraft axes: (x, y, z) → (x, y, −z) | `Space.java` |
| Hooks: travel, mouse turn, camera, FOV, F2 key, use-item | `mixin/` |
| Reads the boards from your install into a resource pack | `converter/extract_s1_skateboards.py` |

## Limitations
- **Client-side only.** Other players see you glide; they don't see the board.
- **No stamina.** Minecraft has none, so pushing never runs out.
- **No riding pose.** The rider stands sideways without Schedule I's riding pose.
- **Placeholder sounds** (Minecraft wood sounds).
- **Keyboard and mouse only.** Schedule I's gamepad camera isn't ported.

## Disclaimer
This is a fan project. It isn't affiliated with or endorsed by TVGS (Schedule I) or Mojang/Microsoft
(Minecraft). Schedule I and its assets belong to their owners. This repository only contains original code
and a converter that works on a copy of the game you own.

Built with the help of Claude Code (AI-assisted).
