# AtlasMap

Minimap + fullscreen world map + administrative region drawing for
Minecraft Java 26.2, Fabric loader.

## Status: Phase 4 - shared map cache, fullscreen world map, Xaero-style waypoint/region placement

### The minimap bug from the screenshot is fixed

The pink ice / red water was a color-channel swap: `NativeImage#setPixelABGR`
expects its int in `0xAABBGGRR` byte order, but the code was handing it
plain `0xAARRGGBB`. `Pixels.toAbgr(int argb)` now does the correct
red/blue swap before every pixel write, in both the minimap and the new
world map.

### Architecture change: one shared map cache

Previously the minimap scanned blocks into its own private texture every
frame. Now there's a proper per-world, per-dimension cache
(`dev.atlasmap.map.DimensionMapCache`) that:
- is filled in the background by `MapManager` (a few nearby chunks scanned
  per tick, farther chunks refreshed less often - cheap enough to run
  every tick without a hitch)
- persists to disk per world+dimension (`ChunkScanner` handles Overworld/
  End as a normal top-down surface scan, and the Nether with a
  ceiling-aware downward scan from just above the player, since a plain
  surface scan there is just bedrock - real cave-layer overlays for the
  Overworld are still a later phase, as you said)
- is shared by BOTH the minimap and the new fullscreen world map, so they
  always agree and blocks are only ever sampled once

The fullscreen map renders this cache through a fixed pool of reusable GPU
tile textures (`MapTilePool`, 128x128-block tiles) instead of one giant
texture, so panning/zooming a heavily-explored world stays cheap and
memory-bounded.

### Fullscreen world map (`'` key) - Xaero-style

- **Drag** to pan, **scroll** to zoom (0.25 to 24 blocks/pixel).
- **Right-click** anywhere for a menu:
  - *Add waypoint here* - creates it and immediately opens the rename/color
    editor (same `EditWaypointScreen` as the `B` waypoint list, so editing
    name/color works identically from either entry point).
  - *New region here* / *Draw: `<existing region>`* - starts region-drawing
    mode targeting that region.
  - *Manage regions...* - opens the region list (rename/color/opacity/
    banner) for when you're not drawing on the map itself.
  - *Waypoint list...*
- While drawing a region: **left-click or drag** paints chunks into the
  selected region (click again on an already-painted chunk to remove it);
  **right-click** to stop.
- Region boundaries are drawn as lines (not filled blocks) in the region's
  own color/opacity, computed from each region's own chunk set - correct
  at any pan/zoom level, and cheap even for a huge, sparse region since it
  never scans the whole visible area.
- Your own position (red square) and every waypoint (colored square +
  name) are shown live.

### Minimap

Unchanged in what it shows, but now reads from the shared cache above (so
it also benefits from the Nether ceiling-scan and the color fix), and is
lighter on the CPU since it isn't scanning anything itself anymore.

### Not implemented yet

A persistent-cache is done, but there's still no separate "cave layer"
overlay for the Overworld (only the always-on Nether ceiling handling from
phase 3) - happy to build that next if you want it. Also still open:
waypoint edge-arrows for off-screen waypoints on the minimap, and a proper
context-menu widget instead of the hand-rolled `ContextMenu` popup (which
works fine, just isn't a "real" Screen widget).

## Keybinds

| Key | Action |
|---|---|
| `M` | Toggle minimap |
| `+` / `-` | Zoom the minimap in/out |
| `'` | Open the fullscreen world map |
| `B` | Open the waypoint list |
| `N` | Open the region manager (rename/color/opacity/banner) |

On the world map itself: drag to pan, scroll to zoom, right-click for the
action menu.

## Mapping notes worth knowing about (26.x specifics)

- Mouse input changed shape: `mouseClicked` now takes a `MouseButtonEvent`
  (coordinates via `.x()`/`.y()`, button via `.button()`); `mouseDragged`
  receives the *delta* since the last event, not an absolute position;
  `mouseScrolled` kept its old plain-double signature but split scroll into
  horizontal/vertical amounts. All confirmed against a real third-party
  library (LibGui) built for this exact Minecraft version.
- `graphics.item(stack, x, y)` (not `renderItem`) draws an item icon in a
  GUI - GUI item drawing was renamed as part of the same "extract" split
  that turned `drawString` into `text`.
- `Items.WHITE_BANNER`-style constants don't exist directly anymore;
  looked up by plain id string via the registry instead (see
  `BannerDesignScreen`).
- `AbstractTexture.setFilter(...)` no longer resolves on `DynamicTexture`;
  removed rather than re-guessed (cosmetic only - crisp-vs-blurred texture
  filtering, not correctness).

## Getting it running

1. Unzip, open in IntelliJ IDEA as a Gradle project.
2. No `gradle-wrapper.jar` is bundled - let IntelliJ sync with its own
   Gradle first, then `./gradlew wrapper --gradle-version latest` once you
   want `./gradlew` from a terminal.
3. Let Gradle sync (Minecraft 26.2, Fabric Loader, Fabric API).
4. Search the project for `VERIFY-ON-26.2` for the handful of spots I
   flagged instead of guessing silently (`ScreenUtil.open` chief among
   them - see its own doc comment).
5. `./gradlew runClient`.

## Project layout

```
src/main/java/dev/atlasmap/map/                Chunk pixel data + the on-disk cache format - no MC imports
src/main/java/dev/atlasmap/region/             Region + BannerDesign models, JSON persistence
src/main/java/dev/atlasmap/waypoint/           Waypoint model, JSON persistence
src/client/java/dev/atlasmap/client/map/       ChunkScanner, MapManager, MapTilePool - the live map pipeline
src/client/java/dev/atlasmap/client/hud/       minimap + its region/waypoint overlays
src/client/java/dev/atlasmap/client/gui/       WorldMapScreen, ContextMenu, waypoint/region/banner screens
src/client/java/dev/atlasmap/client/config/    minimap settings
src/client/java/dev/atlasmap/client/keybind/   keybindings
src/client/java/dev/atlasmap/client/region/    per-world RegionManager holder
src/client/java/dev/atlasmap/client/waypoint/  per-world WaypointManager holder
```

## Data formats

Map cache (gzip binary, not meant to be hand-edited) -
`<gameDir>/atlasmap/maps/<world-or-server-id>/<dimension>.bin`.

Regions and waypoints are unchanged from before (see prior notes in this
file's git history) - plain JSON under
`<gameDir>/config/atlasmap/{regions,waypoints}/<world-or-server-id>/`.

## Notes on multiplayer

Everything (regions, waypoints, the explored-terrain cache) is still
100% client-side and per-player.
