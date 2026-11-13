# Job "anime races" (w10): report

This job reworks races so each one matches a popular anime archetype. It compiles (`bash tools/check-tree.sh races` ends with COMPILOU OK for the whole tree; `bash tools/check-module.sh character` ends with TUDO OK) but **nothing was run in game**.

**Save compatibility**
- No race ids changed and no enum order changed, so old saves load with the same race and level.
- Race level still comes from the character level (1/15/35/60/90) plus the race feat at level V.
- Feat counters are untouched. Where a feat's meaning widened (Slime), the old counter still counts.
- One new NBT key, `predator`, holds the Slime's learned list and analysis counts. Old saves without it start empty.
- Changed attribute values (Half-orc melee, Slime form scale) apply again on login through the existing modifier ids.

## Per race

### Slime → Rimuru (Tensura)
- **Passive**: "Predador" (was "Geleia Viva"). Regen, bounce, sticky body, armor and steadiness are unchanged at I–IV.
- **Predator: how it learns**
  - Every Slime kill is "devoured" for +1 HP and a slime-particle stream.
  - Kills of copyable mobs are samples. After 3 samples of one kind, the ability is learned (in the V form, 1 sample is enough, so Predation kills teach instantly).
  - Capacity is 2/3/4/5 abilities at levels I–IV and 6 at V. When full, the oldest ability is dropped (FIFO).
- **Abilities** (`SlimePredator.Mimic`; the ids are saved, so do not rename them):

| Ability | Copied from | Effect |
|---|---|---|
| spider | spider | climb walls, slow (client) |
| wolf | wolf | +6% speed |
| bat | bat | darkvision (tab toggle shown) |
| creeper | creeper | below 30% HP, an acid/knockback burst (once per 60 s) |
| blaze | blaze, magma cube | −40% fire damage |
| aquatic | drowned, guardian, squid | water breathing |
| zombie | zombie | immune to Hunger and Poison |

- **Great Sage** (rare lines):
  - action bar when analysis of a new kind starts (once per kind);
  - one chat line when an ability is learned or dropped;
  - a critical-HP warning in combat, at most once per 10 min;
  - an on-demand report with **Shift + racial key**: learned list, capacity, and not-copied kinds with their analysis progress.
- **V form**: "Despertar do Lorde Demônio" (was "Forma do Rei Slime").
  - Humanoid look: no giant jelly or crown; a blue-silver aura, a dark Demon Lord veil on HIGH graphics, and golden eyes.
  - Scale is +15% instead of +60%. All other stats and the mitosis are kept.
- **Form power**: "Predação" (same mechanics as before).
- **Feat**: "Devorar %s coisas". Food still counts, and kills of copyable mobs now also count.
- **Stages**: Slime → Slime Nomeado → Slime Demônio → Candidato a Lorde Demônio → Lorde Demônio.

### Arachnid → Kumoko (Kumo desu ga)
- **Passive**: "Fios e Veneno". Climbing and poison on hit are unchanged. New on top of them:
  - **I – Spider Thread** (Shift + racial key):
    - It pulls you to the aimed block, or pulls the aimed foe to you. Bosses are only slowed.
    - Range is 12/16/20/24 blocks at levels I–IV and 28 in the form.
    - Cooldown is 4/3.5/3/2.5 s at levels I–IV and 1.5 s in the form.
    - No fall damage for 3 s after a pull.
  - **II – Thread Trap**:
    - Crouch still for 2 s to lay an invisible trap (2 at a time; 3 from level III; 4 in the form). Traps last 60 s.
    - The first foe that steps on one is Rooted, Slowed and Poisoned, and takes small damage.
    - Only the owner sees a faint glint.
  - **III – Evil Eye**:
    - The foe you stare at (16 blocks, line of sight) gets Paralysis (Slowness IV for 1.5 s).
    - It fires every 6 s at level III and every 4 s at IV, when Curse (Corrupted) is added.
    - In the form it fires every 2 s with a 25% chance to Petrify.
- **V form**: "Arachne". Stats are unchanged.
- **Looks evolve with race level**:
  - I: 1 pair of small legs.
  - II–III: 2 pairs that grow.
  - IV: 3 pairs plus a small abdomen.
  - V (form): 4 pairs plus the full abdomen.
- **Stages**: Pequena Taratect Menor → Pequena Taratect Venenosa → Zoa Ele → Zana Horowa → Arachne.
- **Controls**: darkvision toggles with V below level V, or with the tab button.

### Dragonborn → Dragon Slayer (Fairy Tail), with Veldora flavor in the text
- **Passive**: "Dragon Slayer". Existing resistances and bonuses are unchanged.
- **Devour Element** (Shift + racial key):
  - It eats the ancestry element nearby:

| Ancestry | What counts as a source |
|---|---|
| Fire | fire, lava, magma, a lit campfire, or being on fire |
| Water | water or rain |
| Earth | stone, dirt or iron |
| Wind | open sky, or y > 110 |
| Light | daytime sun, or a light level of 12 or more |
| Shadow | raw light of 5 or less |

  - It restores 3/4/5/6 HP, 15/20/25/30% mana, and +2 food.
  - It also gives **Sated** for 10 s: +15% damage with ancestry spells.
  - Cooldown is 30/30/20/20 s, and 6 s in the form. No blocks are changed.
- **V form**: "Dragon Force". Melee hits now apply the ancestry element effect.
- **Form power**: renamed "Rugido do Dragão" (same breath cone).

### Half-orc → Geld / Orc Disaster (Tensura)
- **Passive**: "Faminto".
  - Base melee bonus is +3/5/6/8% (was 5/8/10/12%).
  - Eating, or a melee kill, gives a Ravenous Hunger stack: +2% melee damage each for 30 s.
  - The stack cap is 2/3/4/5 at levels I–IV and 8 in the form.
  - A melee kill also devours the prey: +1 HP and +1 food.
  - Health bonus, Relentless (III) and Hot Blood are unchanged.
  - Fully stacked at IV, this is +18% melee, against the old flat +12%.
- **V form**: "Desastre Orc" (stats unchanged).
- **Form power**: "Fome Insaciável" (was Brado de Guerra).
  - 10 damage to every foe within 8 blocks.
  - Foes are Weakened, Slowed and Exposed (as before).
  - 25% of the damage dealt heals you, and each foe hit adds a stack (up to +4).
  - Allies still get Strength.
- **Stages**: Orc → Orc Superior → General Orc → Lorde Orc → Desastre Orc.

### Demon → Anos Voldigoad (Maou Gakuin)
Anos was chosen over Diablo because he matches the existing hellfire, black flames and wings kit.
- **Passive**: "Sangue do Rei Demônio". Levels I–III are unchanged.
- **IV – Magic Eyes of Destruction**:
  - 15% chance to cancel an incoming enemy spell before it hits (`SpellDamageEvent.Pre`), once per 20 s; 30% in the form.
  - Each cancel shows one confident action-bar line, picked at random from 4.
  - Killing a boss gives one of 3 lines.
- **V form**: "Rei Demônio da Tirania" (stats unchanged).
- **Form power**: "Jio Graze" (was Chamas Negras).
  - A black sun goes to the aimed foe or block (20 blocks).
  - It hits a 4.5 radius for 18 base damage, ignites, and adds Burning ×2 and Corrupted.
  - No block damage.

### Elf → Frieren (optional item, done)
- **III+ Hidden Mana**: while crouching, the elf gets a hidden copy of legends_f's `isekai_mahou:mana_concealed` effect.
- This reuses that module's behavior instead of copying it:
  - distant mobs drop target;
  - Mana Sense skips you;
  - the first hit gets a ×1.3 or ×1.6 surprise plus legends_f's reveal banner.
- After a reveal, it re-hides only after 10 s, so it is not a permanent damage buff.
- The effect is looked up by id, so if legends_f is absent this does nothing.

Angel, phantom and beastfolk got no changes.

## UI
- **Race-select screen**: race descriptions now name the anime, and new traits show the new mechanics.
  - Slime: Predador, Grande Sábio.
  - Arachnid: Fio de Aranha, Armadilha, Olho Maligno.
  - Dragonborn: Devorar Elemento. Half-orc: Faminto. Demon: Olhos da Destruição. Elf: Mana Oculta.
- **Race tab**: shows the stage line, a "Predador x/y" line whose tooltip lists the abilities, and a "Shift + tecla racial: …" hint for the three races with a Shift action.
- **Chat**: race-level announcements add "Evolução: <stage>".
- **Shift + racial key** now routes through `RaceKits.onShiftKey`. Races without a Shift action keep the old vision toggle.

## Files
- **New**: `feature/character/SlimePredator.java`, `KumoThreads.java`, `RaceKits.java`, `tools/gen/character_anime.py`.
- **Edited**:
  - `Race` (stages, `hasShiftAction`, new traits)
  - `CharacterData` (`predator` NBT)
  - `RacePassives`, `RaceProgress`, `RaceForms`
  - `CharacterService` (ticks and clear)
  - `CharacterModule` (init and the `predator` command)
  - `CharacterNetwork` (Shift routing)
  - `CharacterContent` (Slime form scale)
  - `client/CharacterClient` (spider-slime climb), `client/RaceFeatureLayer` (arachnid evolution, Slime form), `client/CharacterTab`
- **Lang**: `src/main/lang/character/{pt_br,en_us}.json` were patched by `py -I tools/gen/character_anime.py`, which is idempotent and keeps the files' CRLF.
  - Running `tools/gen/character.py` alone would drop these keys (it already drops other scripts' keys).
  - After `character.py`, or after `icons.py` followed by `character.py`, run `character_anime.py` again.
- No icons or textures were regenerated.

## Test (after `/mahou admin login`)
- **Slime**
  - `/mahou character race @s slime`, then kill 3 spiders and press Shift+V for the report.
  - `/mahou character predator @s all|spider|bat|clear` changes the copied abilities.
  - Climb a wall with spider; check darkvision in a cave with bat; get low on HP after `creeper`.
- **Arachnid**
  - `/mahou character race @s arachnid`, then Shift+V at a wall or at a mob.
  - Use `/mahou level 15`, `35` and `60` (the level cap may need `/mahou circle <n>` or `/mahou unlock all`) to test traps (crouch still for 2 s) and the Evil Eye (stare at a zombie).
- **Dragonborn**: `/mahou character race @s dragonborn`, then Shift+V near fire, water or stone according to the ancestry (`/mahou character info` shows it).
- **Half-orc**: `/mahou character race @s half_orc`; eat or melee-kill and look for the "Fome Voraz no máximo" line.
- **Demon**: `/mahou character race @s demon`, high level, and get hit by enemy spells.
- **Forms**: `/mahou character transform @s` for each race, then V for the power (Jio Graze, Fome Insaciável, Predação, Rugido do Dragão).
- **Elf**: as an elf at level 35+, crouch near mobs, then hit one.
- **Stages**: `/mahou character feat @s` plus level 90 makes the V-stage name and evolution line appear.

## Not verified
- Nothing was tested in game. Unchecked areas:
  - all balance numbers;
  - the thread pull feel and whether the server-side velocity push is smooth;
  - trap stillness detection;
  - Evil Eye targeting;
  - Jio Graze aim;
  - the new Slime form visuals and the arachnid leg and abdomen positions at levels I–IV;
  - the Elf surprise attack together with legends_f's reveal banner.
- The Slime transformation still plays the old "Rei Slime" squash pose (`RacePoses`).
- Race glyph names (Respingo Ácido, Sopro Dracônico…) are unchanged; renaming them is a text-only follow-up.
- Copyable mob detection uses vanilla classes (`Spider`, `Wolf`…), so modded subclasses count but unrelated modded mobs do not.
