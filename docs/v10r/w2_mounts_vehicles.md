**mounts_vehicles — report**

Both checks pass: `bash tools/check-module.sh mounts` and `vehicles` end in TUDO OK, and `bash tools/check-tree.sh mounts_vehicles` ends in COMPILOU OK (whole tree). Nothing was run in game.

**Decision for the coordinator:** the new journal chapter `mounts` sits at order 24 in the single linear story track, so it gates the chapters after it. I kept it to one mandatory step that accepts any saddled animal (vanilla horse included), any old `feat.tame_*` counter, any reins used, or any vehicle piloted. If you don't want a story gate at all, move or drop `data/isekai_mahou/journal/mounts.json`; the hints and tooltips still explain mounts without it.

**Files/dirs touched**
- `feature/mounts/**`
  - new: `MountsGuide`, `entity/RideMount`, `entity/GroundMount`, `entity/LandDrake`, `entity/Nightmare`, `entity/AuroraStag`, `item/MountReinsItem`, `client/MountKeyContext`, `client/GroundMountRenderer`
  - edited: `MountsModule`, `MountsRegistries`, `MountsNetwork`, `MountsSpawns`, `MountFx`, `FlyingMount`, `AbstractSkyMount`, `MountWhistleItem`, `MountHud`, `MountInput`, `MountsClient`
- `feature/vehicles/**`: `VehiclesModule`, `VehiclesRegistries`, `VehicleKind`, `entity/MagicVehicle`, `item/VehicleItem`, `client/{VehiclesClient,VehicleRenderer,VehicleInput,VehicleHud}`
- `feature/class_hollow/client/HollowInput.java`: key context and the riding check only
- `tools/gen/mounts_v1.py`, `tools/gen/vehicles.py`
- `src/main/lang/mounts/*`, `src/main/lang/vehicles/*`
- Generated assets: geo, animations, textures and item models for the 3 mounts and 5 vehicles
- Generated data:
  - `loot_table/entities/{land_drake,nightmare,aurora_stag}.json`
  - `neoforge/biome_modifier/mounts_land_drake_{savanna,plains}.json`
  - `guild_shop/mounts.json`
  - `journal/mounts.json`
  - `recipe/vehicles_*.json`, `advancement/recipes/vehicles_*.json`

**New ids**
- Entities: `land_drake`, `nightmare`, `aurora_stag`, `arcane_broom`, `flying_carpet`, `arcane_boat`, `golem_cart`, `mini_airship`
- Items: `land_drake_reins`, `nightmare_reins`, `aurora_stag_reins`, three `*_spawn_egg`, and one item per vehicle (same id as its entity)
- Abilities: `drake_sprint`, `nightmare_gallop`, `stag_leap`
- Hints: `mounts.city`, `mounts.wild`, `mounts.reins`, `vehicles.energy`, `vehicles.placed`
- Flags: `mounts.got.nightmare`, `mounts.got.aurora_stag`
- Counters: `mounts.boss_rolls`, `feat.mount_ride`, `feat.mount_ability`, `feat.mount_own.<id>`, `feat.vehicle_ride`, `feat.vehicle.<id>`
- No existing id or NBT key was renamed.

**What was built**
- **R key conflict:** mount ability and Hollow technique now use two mutually exclusive key contexts, so they share R without the red conflict in Controls. Hollow keeps its technique in boats and on horses; it only yields R while piloting a mount that has an ability.
- **Clarity:**
  - First-time hints through `Hints.show`.
  - Empty-hand click on any wild mount now says what it eats.
  - Reins tooltips say where each mount comes from.
- **More mounts** (ground mounts that arrive tamed and saddled through Reins; if the mount dies the Reins return to the owner):
  - Land Drake: Guild shop rank E for 45 coins, or tamed wild in savanna/plains with meat.
  - Nightmare: boss drop, 1 in 6 per boss, guaranteed on the 6th.
  - Aurora Stag: gift on reaching Honored with any `kingdom*` faction.
  - The Guild shop also sells saddle and whistle at rank F; the whistle now calls ground mounts too.
- **Vehicles:**
  - Five craftable vehicles: broom (fast free flight), carpet (low hover, max 10 blocks up), boat (water), golem cart (land, 27-slot chest), airship (3 seats, 9-slot hold).
  - Energy: runs on the pilot's mana, always leaving 25%, then on a crystal tank charged by clicking with a Mana Crystal.
  - Ownership: the placer is the owner; sneak + hit packs it back into an item that keeps its charge.
  - Vehicles never break blocks or hurt anything, and no camera shake was added.

**How to test** (after `/mahou admin login`)
- `/mahou mounts reins` — the 3 Reins plus whistle and saddle; use the reins, click the mount, press R.
- `/mahou mounts summon <griffin|sky_ray|pegasus|wyvern|land_drake|nightmare|aurora_stag>`
- `/mahou mounts boss` — simulates a boss kill; the 6th call guarantees the Nightmare.
- `/mahou mounts gift` — checks the kingdom gift (set kingdom reputation to 300 or more first).
- `/mahou mounts hints`
- `/mahou vehicles give all`, then place one, click to board; `/mahou vehicles fuel <0-100>` while aboard.
- As a Hollow: R on foot fires the technique, R on a mount fires the mount ability, and Controls shows no conflict.
- Guild shop should list the reins, saddle and whistle; menu B → Journal should show "Montarias e Veículos".

**Not done / not verified**
- Vehicle physics, seat positions, the stag leap and the mana costs are tuned on paper only. Models were checked with an offline preview render, not in the game.
- Dedicated-server behaviour is unverified.
- The Stable Master NPC idea was dropped; the Guild shop is the city source.
- The three new mounts show in the Bestiary under "Other" rather than "World".
- Re-running `tools/gen/mounts.py` rewrites the mounts lang files; run `tools/gen/mounts_v1.py` again afterwards.

**PEDIDOS**
- `feature/cities/trade/CityTrades`: sell the reins at a city merchant too, e.g. innkeeper at Friendly: `b.modSell("land_drake_reins", "land_drake_reins", 45, 1, 1)`.
- `feature/journal/bestiary/Bestiary`: add `land_drake`, `nightmare`, `aurora_stag` to the WORLD catalog.