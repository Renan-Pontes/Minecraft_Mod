**market — report**

Both checks pass: `bash tools/check-module.sh market` ends in TUDO OK and `bash tools/check-tree.sh market` ends in COMPILOU OK for the whole tree. Nothing was run in game; everything below is compile-checked only.

**What was built**
- **Auction:** "Mercado" tab in the B menu (Social category, appears at level 5) with Buy, Sell and Collect modes.
  - Buying works with the seller offline.
  - Fee is 5% on sale, charged to the seller (none below price 5).
  - Currency is `isekai_mahou:guild_coin`; purchases spend the Market balance first, then inventory coins.
  - Limits: 10 listings per player, 300 total, max price 9999, listings expire after 7 real days and the item returns to the owner's collection box.
  - Class/soulbound items and coins cannot be listed.
- **Guild board:** sneak + right-click on any existing `quest_board` opens the Market, without touching the guild module. A new craftable `market_board` block does the same on a normal click.
- **Party:**
  - **Markers:** far members (over 20 blocks, including beyond view range) get a small green diamond with name and distance on the HUD. The party card shows a direction arrow and distance.
  - **Map:** members are registered as tracked players in Xaero's Minimap and World Map by reflection; API shapes were checked against the jars in the TLauncher mods folder.
  - **XP share:** already existed (the party doc says "XP de abate dividido com o grupo" — full kill XP to everyone within 24 blocks, party members up to 64). I only fixed kills with no direct author (fire, poison, fall) so they also reach distant party members.
  - **Party chat:** already existed (`/p`, toggle in the Grupo tab); unchanged.
- **Hints:** three first-time hints via `Hints.show`.

**Files touched**
- `src/main/java/com/renanpontes/isekaimahou/feature/market/**` (all new except the two skeleton classes)
- `core/social/`: `SocialPayloads.java`, `ClientSocialView.java`, `SocialSync.java`, `PartyService.java`
- `client/social/`: `PartyHud.java`, `SocialCoreClient.java`, new `PartyMarkers.java`, new `XaeroPartyTracker.java`
- `src/main/lang/market/{pt_br,en_us}.json`
- `tools/gen/market.py`, which generates the tab icon, four block textures, blockstate, models, loot table, recipe, and `src/main/merge/market/data/minecraft/tags/block/mineable/axe.json`

**New ids**
- Block and item: `market_board` (recipe: gold nugget / paper / gold nugget, three planks, two sticks; creative tab TAB_WORLD)
- Menu tab: `market`
- Payloads: `market_query`, `market_op`, `market_listings`, `market_box`, `market_result`, `market_open` (registrar "market-1"); `social_party_pos` (added to the social registrar "1")
- SavedData file: `isekai_mahou_market` (overworld)
- Counters: `market.sold`, `market.bought`
- Hint ids: `market.open`, `market.quest_board`, `market.party`
- GUI layer: `social_party_markers`

**How to test** (after `/mahou admin login`)
1. `/mahou market coins 200`, then `/mahou market open` (or B → Social → Mercado).
2. Sell: click an inventory item, set quantity and price, press "Anunciar". It should appear under "Meus anúncios"; "Cancelar" needs two clicks.
3. Buy alone: hold any item, `/mahou market fake 30`, then Buy → "Comprar" → "Confirmar?".
4. Offline sale with two accounts: A lists, A logs out, B buys, A logs back in. A should get one chat line and find the coins minus fee under Collect.
5. `/mahou market expire` returns all listings to the owners' boxes; `/mahou market box`, `list`, `clear`, `sellhand <price>` are also available.
6. Sneak + right-click a Quest Board, or place a `market_board` and click it.
7. Party: `/party invite <player>`, move more than 20 blocks apart. Check the diamond marker, the arrow on the party card, and the friend's head on the Xaero minimap and world map.

**Not done / not verified**
- The Market tab layout (especially at GUI scale 4), the marker screen projection, and the Xaero registration are unverified at runtime.
- No on/off option for the party markers, because `MahouConfig` belongs to another job; F1 or any open screen hides them.
- Item search is client-side only (localized item name or seller name).
- The debug command output is plain literal text, not translated.

**PEDIDOS**
- guild: add a "Mercado" button to `GuildScreen` that calls `StatusScreen.open("market")`, so the sneak-click is not the only route.
- cities_npc / settlements / structures: place `isekai_mahou:market_board` in guild halls and city markets (by id; it is only craftable for now).
- ui (`MahouConfig`): add a client toggle for party markers, to be read in `client/social/PartyMarkers.render`.
- renown: counters `market.sold` and `market.bought` are available for titles or ranking.