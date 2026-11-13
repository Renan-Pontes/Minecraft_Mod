## home_realm report

The whole section is built and compiles (`check-module.sh home_realm` ends in TUDO OK, `check-tree.sh home_realm` ends in COMPILOU OK), but nothing was run in game.

### Files / dirs touched (all inside my area)
- `src/main/java/com/renanpontes/isekaimahou/feature/home_realm/`: `HomeRealmModule`, `HomeContent`, `HomeKeyItem`, `HomeDimension`, `HomeData`, `HomeManager`, `HomeNetwork`, `HomeCommands`, and `client/HomeRealmClient`, `client/HomeClientState`, `client/HomeTab`
- `src/main/lang/home_realm/pt_br.json` and `en_us.json`
- `src/main/resources/data/isekai_mahou/dimension/home_realm.json`, `dimension_type/home_realm.json`, `worldgen/biome/home_realm.json`
- `src/main/resources/assets/isekai_mahou/textures/item/home_key.png`, `models/item/home_key.json`, `textures/gui/tab/home_realm.png`
- `tools/gen/home_realm.py`

### New ids
- Item `isekai_mahou:home_key` (creative tab World; the innkeeper's existing trade picks it up by this id)
- Dimension, dimension type and biome `isekai_mahou:home_realm`
- Packets `home_realm_action`, `home_realm_info`; ticket controller `home_realm`
- SavedData file `isekai_mahou_home_realm` (stored in the overworld)
- Key `key.isekai_mahou.home_realm`, default the **Home** key
- Menu tab `home_realm`, placed as a section "Lar" inside the Travel Guide; it only shows for players who own a home, are invited to one, or are inside one
- Hints `home_realm_unlocked`, `home_realm_arrived`, `home_realm_invited`
- Counter `visit.isekai_mahou.home_realm`; Portal Guide entry `home_realm`

### What it does
- **Buying:** using the key consumes it and gives the player a plot; a player who already owns a home keeps the key.
- **Plot:** a floating island with a furnished starter house (bed, double chest with a small welcome kit, crafting table, furnace, barrel), farm, pond, three trees and a 129×129 buildable area. It is built once over about a second and never rebuilt.
- **Enter / leave:** key or menu B. Entering requires 2 minutes out of combat. The menu button shows the countdown. Leaving returns the player to the exact spot they came from.
- **Combat definition:** being hurt by a living entity, or hitting an enemy, a player, or a mob that is targeting something. Killing passive animals does not count.
- **Entry is also blocked** during a boss arena fight, while a spirit, and from `trial_realm`, `inner_world` and `endless_abyss`.
- **Friends:** the owner invites online players from the tab; invited players can visit any time, including when the owner is offline. A toggle lets guests build and open containers (off by default). Removing an invite ejects the guest.
- **Safety:** players take no damage, hostile mobs cannot exist there, explosions and spells break nothing, and falling into the void puts the player back at the arrival point.
- **Beds and chests:** beds set respawn. Sleeping skips the night only if no awake player is outside the home dimension; otherwise the sleeper gets a short message.
- **Extra I added:** the owner can set a custom arrival point ("Marcar chegada aqui").

### How to test
1. `/mahou admin login`, then `/mahou home key` and right-click the key. Expect a toast and a hint. Or skip the item with `/mahou home unlock`.
2. Press **Home**, or open B → Guia de Viagem → Lar → "Ir para o Lar". You should arrive facing the house.
3. `/mahou home leave` (or the key) returns you to the origin spot.
4. `/mahou home combat`, then try to enter: it is denied with a timer and the tab button is greyed out. `/mahou home combat clear` removes it. Real check: hit a zombie and try to enter.
5. `/mahou home go` enters ignoring combat and unlocks if needed. `/mahou home goto <player>` enters someone's home without an invite. `/mahou home status` lists homes and coordinates.
6. With two players: `/mahou home invite <player>` (or the tab), then the guest runs `/mahou home visit <owner>`. Check the guest cannot break blocks or open chests until the owner toggles "Convidados constroem".
7. Inside: jump into the void (should be brought back), try a zombie spawn egg (nothing spawns), light TNT (no block damage), sleep at night in the bed.
8. Survival source: an innkeeper in a city sells the key for 128.

### Not done / not verified
- **Nothing was run in game.** The house, roof and furniture were only checked by reading the code. Night skip, respawning at the home bed, and the new dimension appearing in an existing save are also unchecked.
- A fresh creative-tab item and a dimension JSON mean the coordinator's full Gradle build is the first real test of resource merging.
- Admins in creative mode with op are exempt from the build protection and the void rescue, so test protection in survival.
- The combat timer is in memory; a server restart clears it.
- A player who respawns at a bed inside the home after dying elsewhere has no "where I was", so leaving sends them to world spawn.
- Not built: plot expansion or upgrades, a cast time on entering, bringing pets or mounts along, and a journal chapter.
- Plots are not registered in `Places`, so there is no city-style banner on arrival.

### PEDIDOS
- None blocking.
- Optional (`ui_hud` / `MenuReveal`): a "Nova aba!" badge when the Lar section first appears.
- For reviewers of other modules: any future feature that edits blocks or spawns mobs around a random player should skip the `isekai_mahou:home_realm` dimension. `world_events` is already overworld-only, so nothing is needed there today.