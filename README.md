# Ender Distance

Directional terrain rendering optimization for **Minecraft 26.3** on Fabric.

Ender Distance looks at where you are actually looking and where you are actually going, then
stops the renderer from drawing terrain the camera has no use for. Chunks stay loaded, stay
ticked and stay fully functional — the mod only ever removes *rendering* work.

Version: **0.0.1-alpha**

---

## What it does

Every frame vanilla already frustum-culls and occlusion-tests the world, then hands the
surviving terrain sections to the renderer. Ender Distance filters that final list a second
time, this time using direction:

```
                      you look this way
                            ▲
        LOW   ┌─────────────┼─────────────┐   HIGH
        LOW   │             │             │   HIGH
        LOW   │             │             │   HIGH
              └─────────────┼─────────────┘
                     MEDIUM (sides)
                            │
                           LOW (behind)
```

A chunk is only skipped when **all** of the following agree:

- it is far outside a safe radius around the player (nothing close is ever touched),
- it is beyond the distance limit for the direction it sits in (front, side or back),
- it was already culled, with hysteresis, so it does not flip-flop at the boundary.

Anything the system is unsure about is rendered. Correct output always wins over frame rate.

## How the decision is made

1. **Camera heading** is smoothed over time so tiny mouse wobbles do not move the boundary.
2. **Movement direction** nudges the heading while you travel, so sprinting forward widens
   what is kept in front and strafing does not fight the view.
3. **Turn stress** rises when the camera is swung quickly. While it is high, culling fades out
   entirely and ramps back in over roughly half a second once you settle. This is what stops
   a 180-degree turn from revealing terrain that is not there yet.
4. **Hysteresis** means a skipped chunk must travel a good margin back inside the limit before
   it is drawn again.
5. **Render distance** and the **dimension** are tracked, and the whole decision set is rebuilt
   when either changes.

## Why it stays cheap

The per-chunk decisions live in a flat byte grid centred on your chunk, so the per-section check
is one array read. The grid is rebuilt only after you have moved half a block or turned about a
degree, not once per frame. No per-frame allocation, no streams, no logging, no trigonometry in
the hot loop, and squared distances wherever an exact root is not needed.

## Installation

Ender Distance is a client-side mod.

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.0 or newer.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) (optional, not required by this mod).
3. Drop `ender-distance-0.0.1-alpha.jar` into your `mods` folder.
4. Requires Minecraft Java Edition **26.3** and Java **25** or newer.

## Configuration

The config file is created at `config/enderdistance.json`:

```json
{
  "enabled": true,
  "strength": 0.5,
  "forwardDistance": 1.0,
  "sideDistance": 0.85,
  "backDistance": 0.55,
  "smoothing": 0.5,
  "debug": false
}
```

| Option | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch. `false` makes the mod a no-op. |
| `strength` | `0.5` | How aggressive the culling is. `0` disables culling, `1` applies the full distance profile. |
| `forwardDistance` | `1.0` | Fraction of the render distance kept within roughly 60 degrees of the view direction. |
| `sideDistance` | `0.85` | Fraction kept between roughly 60 and 120 degrees. |
| `backDistance` | `0.55` | Fraction kept behind the player. |
| `smoothing` | `0.5` | How slowly the view direction catches up when you turn. |
| `debug` | `false` | Shows the in-game overlay. |

Set `"debug": true` and restart the game to see live status, FPS, culled chunk counts, camera
heading, turn stress and the time spent in the mod itself.

## Compatibility

| Mod | Behaviour in 0.0.1-alpha |
| --- | --- |
| **Vanilla 26.3** | Supported. This is the only renderer the alpha hooks into. |
| **Sodium** | Detected. Ender Distance stands down and renders nothing itself, because Sodium replaces the section pipeline this mod filters. |
| **Iris** | Detected. Stands down for the same reason; shader rendering is never touched. |
| **Distant Horizons** | Detected. Stands down so its LOD terrain is never affected. |
| **Other mods** | Not tested in this alpha. Report anything that looks wrong. |

Compatibility is treated as more important than frame rate. When an unsafe renderer is present,
the optimisation is disabled rather than forced.

## Known limitations

- Only the vanilla terrain section list is filtered. Entities, block entities, particles,
  weather and chunk loading are untouched.
- The mod is a no-op under Sodium, Iris and Distant Horizons in this alpha.
- No Mod Menu screen yet; configuration lives in the JSON file.
- Terrain beyond 32 chunks is always rendered, since decisions are cached in a 32 chunk grid.
- Terrain that is visible in a mirror, through a window or on a screen is still rendered.
- Single player and multiplayer have not been benchmarked in this alpha.

## Alpha warning

This is a first, experimental release. The rendering pipeline in 26.3 is new and the mod is
built against it for the first time. Expect rough edges, and please report crashes, black
terrain or visual corruption with your Minecraft version, Java version, renderer and GPU.

## Performance disclaimer

How much this helps depends entirely on your hardware, world and renderer. Users who are
GPU-bound at long render distances will see the most. Users who are already CPU-bound or who
run Sodium may see no change at all, because in those setups the mod is disabled or the work it
removes is not the bottleneck. **No specific FPS improvement is claimed.** Measure yourself with
the debug overlay enabled and compare against the same scene without the mod.

## Building from source

```bash
./gradlew build
```

The mod jar is written to `build/libs/ender-distance-0.0.1-alpha.jar`.

## License

MIT