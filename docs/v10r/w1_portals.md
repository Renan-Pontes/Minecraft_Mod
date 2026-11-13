The whole tree now compiles (`check-tree.sh portals` → COMPILOU OK); `check-module.sh travel` and `check-module.sh dimensions` both end in TUDO OK. Nothing was run in game — everything below is compile-verified only.

## Files touched
All paths under `c:/Users/renan/Music/ROCKET/RocketSimVis/Minecraft_Mod/`.

- **New, server/common** in `src/main/java/com/renanpontes/isekaimahou/feature/travel/guide/`:
  - `PortalGuide.java` — the 8 dimension entries, plus `register(Entry)` for later modules.
  - `GuideNet.java` — payloads.
  - `GuideService.java` — track-nearest, persisted target, Circle-up chat line.
  - `MapMarkers.java` — discovered places/structures synced for the minimap.
  - `GuideCommands.java`, `XaeroServerProfile.java`.
- **New, client** in `feature/travel/client/`: `PortalGuideView.java`, `GuideClient.java`, `XaeroMarkers.java`.
- **Edited:** `feature/travel/TravelModule.java`, `feature/travel/client/TravelClient.java`, `feature/travel/client/TravelTab.java` (sub-tabs "Destinos | Guia de Portais"), `client/ux/XaeroSeed.java`, `core/ux/MapTeleportGuard.java`, `src/main/lang/travel/pt_br.json` and `en_us.json` (125 keys each).
- **Edited outside my listed area** (the contract says to raise rarity if a portal barely generates; these belong to world_v09):
  - `src/main/resources/data/isekai_mahou/worldgen/structure_set/{elemental_gate,abyssal_chasm,celestial_stairway,twilight_rupture}.json`
  - `src/main/merge/world_v09/data/isekai_mahou/tags/worldgen/biome/has_structure/celestial_stairway.json`
  - `tools/gen/world_v09.py` (same numbers, so regenerating does not revert them)
- `feature/dimensions/**` was not changed.

## What was built
- **Portal Guide:** B menu → Viagem → "Guia de Portais". Each dimension shows where the portal is, how to open it, and requirements with ✔/✘ from the player's own Circle and boss kills. "Rastrear o mais próximo" puts an arrow on the objective tracker; it survives relog and death and clears on arrival.
- **Circle-up notice:** one chat line when a new Circle meets a portal's requirement, pointing to the guide.
- **Rarity (spacing/separation in chunks):**

  | Structure | Before | After |
  |---|---|---|
  | `elemental_gate` | 48/18 | 36/14 |
  | `abyssal_chasm` | 56/20 | 40/14 |
  | `celestial_stairway` | 44/16 | 28/10, plus 6 biomes (plains, sunflower plains, savanna plateau, windswept savanna, windswept gravelly hills, snowy plains) |
  | `twilight_rupture` | 48/18 | 40/16 |

  The Celestial Stairway was the one that almost never generated (mountain biomes only). `fey_ring` and `garganta_site` were left as they are.
- **Xaero minimap points:** discovered cities, portals, lairs, dungeons, towers, citadel, library and sanctum appear automatically, per dimension. They use Xaero's third-party waypoint hook by reflection, so there is no dependency and nothing is written to Xaero's files. Structure discoveries show one action-bar line; places stay silent because they already show their own banner.
- **Xaero teleport removed:**
  - `map_teleport_allowed = false` is now forced in every World Map profile at each launch and again at exit. Before, it was only seeded when the file did not exist, and his existing profile had it `true`.
  - The same line is seeded into Xaero's server-enforced profile.
  - `MapTeleportGuard` now lets through only a logged-in admin; creative mode no longer bypasses it. In dev, where admin auth is off, the old creative/spectator rule still applies so the block can be tested.
  - The guard now checks who typed the command, so command blocks and functions pass as its doc always claimed.

## New ids
- Payloads: `travel_guide_track`, `travel_guide_target`, `travel_map_markers` (registrar `travel_guide-1`).
- Command: `/mahou portals`.
- Tracker provider: `portal_guide`.
- Player-persisted NBT keys (new, optional): `isekai_mahou_portal_track`, `isekai_mahou_map_marks`.
- Xaero waypoint origin: `isekai_mahou:places`.
- No new items, blocks or entities; no existing id or NBT key renamed.

## How to test in game
1. Open B → Viagem → "Guia de Portais", pick a dimension, click "Rastrear o mais próximo"; the arrow should appear on the tracker (J).
2. `/mahou portals` prints the guide as text; `/mahou portals track elemental_plane` and `/mahou portals untrack` do the same as the button.
3. `/mahou portals locate celestial_sanctuary` (op) prints nearest coordinates, distance and search time — use it on a fresh world to judge the new rarity. Ids: `elemental_plane`, `abyss`, `celestial_sanctuary`, `fey_realm`, `hollow_world`, `twilight_rift`.
4. `/mahou portals markers` (op) lists what would be sent to the minimap; `markers resync` and `markers forget` are also there. Walk into a structure such as `/locate structure isekai_mahou:depths_dungeon` with Xaero's Minimap installed and a point should appear.
5. Teleport block: in survival, try teleport from the Xaero map or `/tp @s ~ ~10 ~`. In the dev client, creative still passes; in the built jar, only after `/mahou admin login`.
6. After one launch, check `config/xaero/world-map/profiles/default.cfg` and `server_profiles/default.cfg` for `map_teleport_allowed = false`.

## Not done / not verified
- **No new physical portal was added.** The six world dimensions already have one. Trial Realm and Inner World are personal instances entered by menu and by Shinigami meditation; the guide lists them as "Sem portal" with instructions. Endless Abyss and Home Realm are not in the guide yet; their modules can call `PortalGuide.register(...)`.
- **Xaero behaviour is unverified at runtime.** The reflection signatures were checked against the installed Minimap 26.6.0 jar. Not checked: whether the World Map also draws these points, whether the server profile actually enforces the option, and whether Xaero reads its config before or after our edit on the very first launch (the server-side block covers that case).
- **Spacing changes apply to newly generated chunks only**, and a structure can be cut at the border of already-generated terrain in old worlds.
- **Track-nearest runs a `/locate`-style search** on the server thread (limited radius, 2 s cooldown per player); its cost was not measured.
- Player-founded cities and waystone/hunting-ground places: cities get a point, the other two deliberately do not.
- JourneyMap gets no automatic points.

## PEDIDOS
1. `core/ux/AdminAuth.java` or `UxCore.java`: in production `AdminAuth` (HIGHEST) cancels a non-admin's `/tp` first, so the player sees "admin required" instead of "Use as Pedras de Teleporte". Either register `MapTeleportGuard::onCommand` before it, or have `AdminAuth` show `ux.isekai_mahou.teleport_blocked` + `_hint` when `MapTeleportGuard.teleports(ctx)` is true (now public).
2. `ui` agent: optionally call the first-time hint for the Portal Guide; `TravelTab.openGuide()` opens the B menu directly on it.
3. `abyss` (Endless Abyss) and `home_realm`: add their dimension with `PortalGuide.register(new PortalGuide.Entry(...))` and lang keys `guide.isekai_mahou.portals.<id>.name|site|where|how`.
4. Modules with new important structures (towers, dungeons, biomes in wave 3): call `MapMarkers.registerStructure("<structure_path>", "<city|dungeon|tower|citadel|library|portal|lair|sanctum>")` and add `map.isekai_mahou.structure.<path>`.
5. world_v09 / hueco_mundo owners: be aware of the spacing and biome edits listed above.