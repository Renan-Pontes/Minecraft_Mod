**vehicles_mechanic report**

The owner asked: "E melhore os veículos, pra que sejam vendidos na cidade por um mecânico".

What is in:
- a Mechanic tradesman and a new building, the Mechanic's Workshop (`garage`);
- a Mechanic in every world city;
- vehicles now have health, repair, upgrades, a recover/rebuild service, smoother handling, lights, smoke and sound;
- three new vehicles.

Checks:
- `bash tools/check-tree.sh vehicles` → COMPILOU OK (whole tree, last run after all edits).
- A headless JVM test passed:
  - `garage` generated for 7 styles × 5 levels, both the old procedural mould and the new-look 13×13 lot, with no exception and no fallback floor;
  - the upgrade packing and every vehicle's price, repair, recover and rebuild numbers were printed and checked.
- **Nothing was run in game.**

## Mechanic and Mechanic's Workshop (player city)
- **Building `garage`**, added to every style's catalog:

  | | Value |
  |---|---|
  | Unlocks at | city rank 1 (Camp) |
  | Prosperity | 4 |
  | Max count | 1, plus 1 every 2 expansion rings (district rule) |
  | Build time | 2 days at pace 1, via `CityPace.register` |
  | Economy | fuel use 1, security 1, +3% taxes |

  - Names by style: Oficina Mecânica, Oficina das Caravanas, Oficina do Estaleiro, Oficina dos Artífices, Oficina dos Ventos, Oficina das Engrenagens, Oficina dos Engenhos.
  - Planner icon: minecart.
- **Look:** a new procedural mould `ProcBuildings.garage`, which the wave-8 look engine wraps in the level's yard. What each level adds:
  - L1: wide 3×3 front gate, iron-plate lift bay, tool wall.
  - L2: chains and a lantern over the bay.
  - L3: chain hoist and copper details.
  - L4: second floor of part storage and an amethyst mana-charging pillar.
  - L5: crafter, copper grates and a lightning rod.
- **Tradesman:** job `mechanic` (append-only id), colour `0x5FB8D9`, bones cap, apron and hammer, hammering animation. He arrives, respawns and pays the Lord's 10% like the other 9 trades.
- **Stock** (shop level = Workshop level, which is capped by city rank):

  | Shop level | Sells | Price (coins) |
  |---|---|---|
  | 1 | Mana Crystal | 5 |
  | 1 | 2 Repair Kits | 10 |
  | 1 | Arcane Boat / Golem Cart | 22 / 28 |
  | 2 | Flying Carpet | 45 |
  | 2 | Runic Engine / Arcane Plating | 18 / 16 |
  | 3 | Arcane Broom / Mana Rover | 72 / 64 |
  | 3 | Expanded Hold | 14 |
  | 4 | Arcane Airship / Tide Skiff | 110 / 90 |
  | 5 | Sky Galleon | 128 |

  - He also buys iron, copper and redstone (redstone from level 2).
  - Vehicles: 1 a day, rising with level and rank; sold with a half tank.
  - Prices above 64 use the trade screen's second coin slot (cap 128). The Lord's fee now counts both slots.
- **Services (sneak + click, price first, second click confirms):**
  - **Repair:** fixes the vehicle in your hand, or your own vehicle parked within 16 blocks. Cost = missing health × (3 + price/20), from −0% at Workshop level 1 to −40% at level 5.
  - **Rebuild a destroyed vehicle:** 35% of its price; it comes back whole, upgrades kept, tank empty.
  - **Recover a lost vehicle:** 4 + price/10.
  - If the vehicle left between the two clicks, the coins are refunded.
  - With nothing to do, he talks: what he sells, how the shop is doing, and a summary of your vehicles.
- **Panel:** the Comerciantes tab gets a 10th row; the list scrolls.
- **One-time hints:**
  - `vehicles.mechanic` when first trading with a player-city Mechanic;
  - `vehicles.mechanic.world` with a world-city Mechanic;
  - `vehicles.wrecked` on the first destroyed vehicle.

## Mechanic in world cities (`feature/cities`)
- **New `Profession.MECHANIC`** with cap, apron and goggles. Female Mechanics wear the overalls, not the skirt.
- **Arrival:** `CityMechanic` is called by the city heart every 10 s and brings one Mechanic once per city, in new and existing worlds.
  - He goes to a free 3×3 spot on the plaza ring (14/18/10/22 blocks out, off the avenues). Nothing is placed or broken.
  - After 90 tries with no free spot, he goes to the top block next to the heart.
  - He is registered in the census as `merchant:mechanic`, so he respawns like any citizen.
  - The flag is `MechanicDone` in the heart's persistent data.
- **Trades are gated by kingdom standing, like the Blacksmith:**

  | Standing | Adds |
  |---|---|
  | Anyone | Boat, Cart, Repair Kits, Mana Crystal |
  | Friendly | Carpet, Engine, Plating |
  | Honored | Broom, Rover, Hold |
  | Revered | Airship, Skiff |
  | Exalted | Galleon |

  - Prices are the same as the player city, times the reputation multiplier.
- **Services** in the dialogue's Services button: `vehicle_repair`, `vehicle_rebuild`, `vehicle_recover`. Discount is fixed at Workshop level 2. +1 kingdom reputation per service (daily cap).
- Shops close at night (existing routine).

## Vehicle changes
- **Health:** values are in the new-vehicle table below; Plating gives +40% health per level.
  - **Damage sources:** monsters, monster projectiles, explosions and lightning.
  - **Never damaged by:** players (owner, PvP, player-lit TNT), fire, lava, falls, suffocation or cactus.
  - 10-tick invulnerability after each hit; the model shakes when hit.
  - Below 50% health it smokes; below 25% it smokes black, runs at 70% speed and warns the pilot.
  - At 0 it is destroyed:
    - riders are ejected and the cargo drops on the ground;
    - no blocks are touched (fake explosion particles and sound);
    - the owner gets a chat line with coordinates, and the vehicle is marked "wrecked" for the Mechanic.
- **Repair Kit** (`vehicle_repair_kit`): click a vehicle to restore 40% health. Anyone can use it on any vehicle.
- **Upgrades:** click your own vehicle with the part; max level 3 each. They are stored on the entity and kept in the item when packed.

  | Part | Per level |
  |---|---|
  | `vehicle_part_engine` | +10% top speed and acceleration |
  | `vehicle_part_armor` | +40% health, −15% damage taken |
  | `vehicle_part_storage` | +9 cargo slots (up to 54); a vehicle without a chest gets one |

- **Handling:**
  - Turning eases toward the look direction instead of a fixed degrees-per-tick snap.
  - Braking is 1.6× faster than accelerating when no key is held.
  - Auto-hop over 1-block steps (needs 1 block of headroom, so never over fences or walls):
    - ground vehicles: when the step-up misses, e.g. leaving water;
    - boats: onto a 1-block dock or shore;
    - Carpet: climbs over low walls.
  - Boats now have step 0.6.
- **Energy:** unchanged (pilot's mana keeping 25%, then the tank). New: when the tank runs dry, the vehicle burns a Mana Crystal from its own cargo by itself.
- **Seats and friends:** only the owner pilots. Friends board after the owner. If the owner gets off, nobody pilots, and the seat order keeps seat 0 for the pilot. Creative players can still drive any vehicle for testing.
- **Visuals and sound (client only, no network):**
  - two headlamp particles (end rod) when manned, powered and in the dark (light < 8);
  - Mana Rover sparks;
  - engine sound every few ticks while moving: golem steps on the Cart, paddle on boats, a soft beacon hum on flyers and the Rover.
- **HUD:** a "Vida" line and a second bar under the tank bar.
- **Placement** respects spawn protection, adventure mode and `mayInteract`.
- **Packed item tooltip:** health, cargo slots, tank and upgrades.

## New vehicles
They use the same entity class; models are generated by `tools/gen/vehicles_w10.py`.

| id | Name | Mode | Seats | Speed | Cargo | Health | Workshop | Price |
|---|---|---|---|---|---|---|---|---|
| `mana_rover` | Triciclo de Mana | ground | 2 | 0.62 | 9 | 50 | 3 | 64 |
| `tide_skiff` | Lancha das Marés | water | 4 | 0.95 | 18 | 80 | 4 | 90 |
| `sky_galleon` | Galeão Celeste | fly | 5 | 0.55 | 27 | 140 | 5 | 128 |

Existing vehicles:

| id | Health | Workshop | Price |
|---|---|---|---|
| broom | 24 | 3 | 72 |
| carpet | 30 | 2 | 45 |
| boat | 40 | 1 | 22 |
| cart | 60 | 1 | 28 |
| airship | 70 | 4 | 110 |

## Recover and rebuild (`VehicleRegistry`, SavedData `isekai_mahou_vehicles`)
- **What is tracked:** each owned vehicle gets a `VehicleId` and a generation.
  - The registry stores kind, state (out / packed / wrecked), upgrades, tank, health and last position (updated every 5 s).
  - It keeps at most 24 entries per owner; the oldest packed or wrecked ones are dropped first.
- **Recover:**
  - **Vehicle loaded somewhere:** the entity is removed and its cargo goes to the player.
  - **Vehicle not loaded:** the player gets a new item with generation + 1. The old copy deletes itself when its chunk loads, and its cargo drops on the spot.
  - A stale item in an inventory disappears within 2 s; one in a chest is refused when placed.
  - Placing a duplicate item makes the older entity vanish, so there are never two of the same vehicle.
- **Not offered:** a vehicle already in your inventory or loaded within 48 blocks of you.

## Migrations and old saves
- **Old vehicle entities:** no `Health` means full health. They get a `VehicleId` on their first second loaded. Cargo moves into the new 54-slot container (same slot indices).
- **Old vehicle items:** only `VehicleFuel` means a whole vehicle with no upgrades; it gets an id when placed.
- No id, NBT key or registry name was renamed. The new optional keys:
  - entity: `VehicleId`, `VehicleGen`, `Health`, `Upgrades`;
  - item: `VehicleId`, `VehicleGen`, `VehicleHp`, `VehicleUpg`;
  - city heart: `MechanicDone`, `MechanicTries`.
- **Recipes:** only the **Arcane Boat** stays craftable (the cheapest, needed for water cities and for players with no city access).
  - The recipe and unlock-advancement JSON for broom, carpet, cart and airship were **deleted**.
  - `tools/gen/vehicles.py` now only emits the boat, so a re-run will not bring them back.
  - Players who had unlocked them only lose the recipe-book entry.

## Files
- **New:**
  - `feature/vehicles/`: `VehicleMechanic`, `VehicleRegistry`, `VehicleStacks`, `VehicleUpgrades`, `item/VehiclePartItem`, `item/VehicleRepairKitItem`
  - `feature/cities/CityMechanic`
  - `tools/gen/vehicles_w10.py`, `tools/gen/vehicles_w10_lang.py`
- **Rewritten:** `VehicleKind`, `entity/MagicVehicle`, `item/VehicleItem`, `VehiclesRegistries`, `VehiclesModule`.
- **Edited:** `client/VehicleHud`, `client/VehicleRenderer` (hit shake).
- **Settlements:**
  - `CityTrades` (job, catalog, >64 price, service, chat, pace)
  - `entity/TraderNpc` (hint)
  - `client/CityTradesPage` (icon)
  - `client/SettlementPanel` (icon)
  - `build/Blueprints`, `build/ProcBuildings` (`garage`)
  - `CityEconomy`, `CityGrowth`
- **Cities:**
  - `entity/Profession`, `entity/CityMerchant` (hint, no skirt)
  - `trade/CityTrades`, `CityServices`, `CityQuests` (switch case)
  - `block/CityHeartBlockEntity` (one tick line)
- **Assets:**
  - geo, animation, texture and glowmask for the 3 new vehicles;
  - 7 item icons and models;
  - `textures/entity/settlement_npc/trader_mechanic_{0,1}.png`, `textures/entity/city_npc/merchant_mechanic_{0,1}.png`.
- **Lang** (`vehicles_w10_lang.py`):
  - `vehicles` +43 keys; `settlements` +25 keys (sorted); `cities` +13 keys;
  - rewritten in place: `story` guide `vehicles.where` / `vehicles.how` and `mounts` journal `mounts.vehicle.hint`.
- **Side effect:** running the generators may have rewritten tracked `tools/gen/__pycache__/*.pyc`.

## How to test (op, `/mahou admin login` first)
1. `/mahou vehicles give all` and `/mahou vehicles give parts`. Place the Rover, Skiff and Galleon. Board, check the HUD health bar, drive into 1-block steps, and at night look for the headlamps.
2. On a vehicle:
   - `/mahou vehicles hp 20`: smoke and slower speed. Click it with a Repair Kit.
   - `/mahou vehicles upgrade engine 3` or click it with parts; sneak-click opens the bigger hold.
   - `/mahou vehicles hp 0`: it is destroyed, the cargo drops and a chat line appears. Then `/mahou vehicles list`.
3. **Player city:**
   - `/mahou city tier 5`; queue "Oficina Mecânica" in the planner; `/mahou city finish`; `/mahou city trades spawn`.
   - Trade with the Mechanic, then `/mahou city blevel 5` on the lot and reopen: more vehicles.
   - Sneak + click twice to rebuild the wrecked vehicle; damage another, park it nearby and sneak + click to repair.
4. **Recover:** place a vehicle, fly or teleport far away (out of loaded range), sneak + click the Mechanic to recover. The item comes back. Go back: the old vehicle is gone and its cargo is on the ground.
5. **World city:** stand on a city plaza and wait ~10 s for "Mecânico chegou" in the log (or `/mahou city spawnnpc merchant mechanic`). Use Trocar, then Serviços.
6. `/mahou city showcase garage <style>` shows levels 1–5.
7. Recipe book: only the Arcane Boat remains.

## Not verified
- **Nothing in game:**
  - new models, seat positions, animations, textures on the NPC models;
  - garage looks and NPC walking through the 3-wide gate;
  - headlamp and sound feel, hop behaviour on docks in water cities;
  - Galleon size against archipelago bridges.
- **Balance:** prices, health, damage and the auto-hop heights are on paper only.
- **World-city Mechanic placement:** the plaza-ring spots were chosen by reading `CityFixtures`; whether he ends up standing well in every city template is unverified.
- **Headlamps are particles, not real block light** (placing light blocks would touch the world).
- **Dedicated server and multiplayer sync** (health and upgrades are synced entity data) are untested.

## PEDIDOS
- **integration** (`docs/ARCHITECTURE.md`): add:
  - `VehicleRegistry` (SavedData `isekai_mahou_vehicles`), `VehicleMechanic`, `VehicleUpgrades`, `VehicleStacks`;
  - building `garage` and trade `mechanic`;
  - `Profession.MECHANIC`, `CityMechanic`;
  - hints `vehicles.mechanic`, `vehicles.mechanic.world`, `vehicles.wrecked`;
  - commands `/mahou vehicles hp|upgrade|list`;
  - generators `vehicles_w10.py` and `vehicles_w10_lang.py`.
- **journal** (`Bestiary`): `settlement_tradesman` already covers the Mechanic. No new entity besides the 3 vehicles (not creatures).
- **Lang:** if `vehicles.py`, `mounts.py`, `mounts_v1.py`, `cities.py`, `journal_story.py` or an older `settlements_*_lang.py` is re-run, run `py -I tools/gen/vehicles_w10_lang.py` afterwards.
