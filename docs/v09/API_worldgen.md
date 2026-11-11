# API v0.9 — Worldgen e Reinos (trilha A3)

Complementa `docs/V09_DESIGN.md` §4. Pedido do jogador: "achei só 1 reino e um acampamento orc DENTRO da cidade" →
**vários reinos** e **nada nascendo dentro das cidades**. Tudo é aditivo: mundos salvos continuam abrindo (ids, salts e
NBT antigos mantidos; os chunks já gerados não mudam — só o que for gerado depois).

| Arquivo | Para quê |
|---|---|
| `core/worldgen/WorldgenCore.java` | Registra os tipos de posicionamento e o comando `/mahou worldgen kingdoms` |
| `core/worldgen/RandomSpreadMultiPlacement.java` | Posicionamento `isekai_mahou:random_spread_multi` (várias zonas de exclusão) |
| `core/worldgen/KingdomRingPlacement.java` | Posicionamento `isekai_mahou:kingdom_ring` (capital de reino num anel em volta do spawn) |
| `core/worldgen/StructureProximity.java` | "Tem estrutura do conjunto X a até N chunks?" (rápido, mesmo resultado do vanilla) |
| `core/worldgen/CityExclusion.java` + `mixin/StructurePlacementMixin.java` | Vilas/postos/ruínas/cabanas do vanilla não nascem nas cidades |
| `feature/cities/world/CityStructure.java` | Números novos das cidades, campo `kingdom`, rio/praia não contam como bioma ruim |
| `feature/cities/world/CityKingdom.java` | Os 3 reinos (id, paleta, facção) |
| `tools/gen/city_build.py` | Moldes (.nbt), worldgen das cidades/capitais, tags, traduções (`src/main/lang/cities_world/`) |

---------------------------------------------------------------------------------------------------
## 1. `isekai_mahou:random_spread_multi`

O `random_spread` do vanilla (mesma grade, mesmo `salt`, mesmo `/locate`) com uma LISTA de zonas de exclusão:

```json
"placement": {
  "type": "isekai_mahou:random_spread_multi",
  "spacing": 40, "separation": 12, "salt": 1946300104,
  "exclusion_zones": [
    {"other_set": "minecraft:villages",            "chunk_count": 4},
    {"other_set": "isekai_mahou:guild_city",       "chunk_count": 10},
    {"other_set": "isekai_mahou:capital_city",     "chunk_count": 13},
    {"other_set": "isekai_mahou:capital_sahra",    "chunk_count": 13},
    {"other_set": "isekai_mahou:capital_nordheim", "chunk_count": 13}
  ]
}
```
- Campos do vanilla continuam valendo: `spread_type`, `frequency`, `frequency_reduction_method`, `locate_offset` e até o
  `exclusion_zone` antigo (soma com a lista). `chunk_count` vai de 1 a **32** (o vanilla só até 16).
- A estrutura NÃO nasce se algum conjunto da lista tiver um chunk de início a ≤ `chunk_count` chunks (distância de
  Chebyshev entre chunks de início). Implementado em `applyInteractionsWithOtherStructures` (é chamado por
  `isStructureChunk`, então vale para geração e `/locate`).
- **Exclusões são de mão única** (igual ao vanilla): as capitais não olham para ninguém; `guild_city` olha para as 3
  capitais; as outras estruturas do mod olham para cidades e capitais. Não faça dois conjuntos se excluírem um ao outro
  (um ciclo é cortado pela proteção de profundidade e vira "nada por perto").
- **Para módulos novos (world_v09, ...)**: toda estrutura de SUPERFÍCIE do Overworld usa este tipo com as 4 zonas acima
  (`guild_city` 10, capitais 13) + `minecraft:villages` 4 se fizer sentido.
- Aplicado em: `arcane_ruins, druid_circle, fairy_lake, forgotten_crypt, goblin_fortress, goblin_village,
  guild_hall_village, harpy_nest, kobold_den, lizardman_village, mage_tower, minotaur_labyrinth, orc_camp,
  profane_altar, renegade_tower, shrine_<earth|fire|light|shadow|water|wind>, troll_bridge, wandering_mage_camp,
  witch_hut_ancient` e `guild_city` (zonas: as 3 capitais a 16). As de dimensão (`abyss_throne`,
  `celestial_cathedral`, `guardian_temple_*`) ficaram como estavam.
- Atenção: os geradores antigos (`tools/gen/structures_*.py` etc.) ainda escrevem `minecraft:random_spread`; se forem
  rodados de novo, reaplique o tipo/zonas nesses `structure_set`.

## 2. `isekai_mahou:kingdom_ring` (capitais de Sahra e Nordheim)

Um `concentric_rings` (o jogo trata como anel: `/locate` funciona) que gera `count` candidatos em volta do spawn e usa
UM só, nesta ordem:
1. o primeiro da lista `candidates` em que a busca do vanilla ACHOU um bioma de `preferred_biomes`;
2. senão, o primeiro da lista cujo ponto cai em terra que a estrutura aceita (bioma da tag `biomes` da estrutura no
   centro e em pelo menos 3 de 4 pontos a 48 blocos, medido em y=64) — o reino não some por ter caído no mar;
3. senão, o primeiro da lista.
```json
"placement": {"type": "isekai_mahou:kingdom_ring", "distance": 30, "spread": 6, "count": 6, "salt": 1946300803,
              "preferred_biomes": ["minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands"],
              "candidates": [2, 1, 3]}
```
Por quê: com `count: 1` todo anel do mundo usa o MESMO ângulo (vem só da semente) — Aldória, Sahra e Nordheim sairiam
alinhadas na mesma direção — e a busca do bioma (raio de 112 blocos) falha muitas vezes. Com `spread = count = 6`, o
candidato *k* fica no ângulo base + k·60°; Aldória (`concentric_rings` de 1) fica no 0, Sahra em 2/1/3 (~120°) e
Nordheim em 4/5/0 (~240°): **cada reino numa direção**. Distância do centro: 4·d ± 1,25·d chunks.

Java: `KingdomRingPlacement#chosen(ChunkGeneratorStructureState)` → chunk de início da capital (ou null se o conjunto
não existe naquele estado).

## 3. Cidades e reinos

| Estrutura | Reino | Posicionamento | Onde | Paleta |
|---|---|---|---|---|
| `isekai_mahou:capital_city` | Aldória (`aldoria`) | `concentric_rings` d=6 (igual à v0.8) | ~260–620 blocos do spawn | planície |
| `isekai_mahou:capital_sahra` | Império de Sahra (`sahra`) | `kingdom_ring` d=30, candidatos 2,1,3 | ~1300–2500 blocos, prefere deserto/badlands | deserto |
| `isekai_mahou:capital_nordheim` | Reino de Nordheim (`nordheim`) | `kingdom_ring` d=36, candidatos 4,5,0 | ~1600–3000 blocos, prefere neve | neve |
| `isekai_mahou:guild_city` | o da paleta | `random_spread_multi` 48/28, longe 16 chunks das capitais | 1 a cada ~770 blocos (antes 64/24) | pelo bioma |

- Tipo Java de todas: `isekai_mahou:guild_city` (`CityStructure`). Campo novo opcional `"kingdom": "aldoria"|"sahra"|
  "nordheim"` — fixa a paleta do reino em QUALQUER bioma (as capitais aceitam qualquer bioma de terra para que o reino
  sempre exista; o anel puxa cada uma para o bioma certo).
- Cidades comuns: `max_slope` 36 (era 24), `max_water` 0,3 (era 0,2), bioma ruim tolerado 0,55 (era 0,35; capital 0,7) e
  pontos de **rio/praia não contam** como ruins. Biomas novos na tag `has_structure/guild_city`: `meadow`,
  `cherry_grove`, `old_growth_birch_forest`, `windswept_savanna`, `sparse_jungle` (paleta planície) e
  `wooded_badlands` (paleta deserto).
- Tag `#isekai_mahou:city` = `guild_city`, `capital_city`, `capital_sahra`, `capital_nordheim`.
- `CityKingdom` (Java, `feature/cities/world`): `ALDORIA/SAHRA/NORDHEIM`, campos `id`, `palette`, `factionId`
  (`kingdom`, `kingdom_sahra`, `kingdom_nordheim`), `forPalette(CityPalette)`, `byId(String)`.
  `CityStructure#kingdom()` → `Optional<CityKingdom>` (só as capitais).

### 3.1 NBT dos moldes (para o módulo `cities` e outros)
- **`city_heart`** (praça): `{Radius, CityType:"city"|"capital", Palette:"plains"|"desert"|"snowy", Kingdom:"aldoria"|
  "sahra"|"nordheim", NeoForgeData:{Kingdom:...}}`. O reino vem da paleta (plains→aldoria, desert→sahra,
  snowy→nordheim); a capital de Sahra usa a paleta desert, a de Nordheim a snowy, Aldória a plains. A cópia em
  `NeoForgeData` (= `BlockEntity.getPersistentData()`) sobrevive no mundo mesmo que o BlockEntity não grave `Kingdom`.
  Corações de mundos antigos não têm `Kingdom`: use `CityKingdom.forHeart(kingdom, cityType, palette)` (com o
  `Kingdom` gravado → ele; sem ele, `CityType:"capital"` → Aldória, a única capital da v0.8, mesmo que tenha saído com a
  paleta do bioma; cidade comum → reino da paleta).
- **Altar de Ascensão PRONTO** (blocos do `ascension`, por id) — na **Igreja** (meio da nave; no deserto é o Templo do
  Sol, com cúpula sobre o altar) e no térreo da **torre da Academia** ("Salão da Ascensão"):
  `isekai_mahou:ascension_core` no centro com NBT `{PublicAltar:1b, NeoForgeData:{PublicAltar:1b}}`, 8
  `isekai_mahou:ascension_pedestal` em (±2,0),(0,±2),(±3,±3) e 16 `isekai_mahou:rune_stone` em (±2,±2),(±3,0),(0,±3),
  (±4,±2),(±2,±4) — o padrão completo do `AscensionPattern` (Círculos 2 a 10), todos na altura do núcleo. Os 4 cantos
  (±4,±4) NÃO têm Bloco de Cristal de Mana (v0.9: o anel 9×9 deixa de exigir cristal — ver §5 do design, módulo
  `ascension`); na Igreja são colunas com lanterna, na Academia ficam dentro da parede redonda. Placa "Altar de
  Ascensão ~ público ~" ao lado. Nada sólido em cima dos pedestais/núcleo.
- **Pedra de Teleporte** `isekai_mahou:travel_stone` (bloco do `travel`, estado padrão, sem NBT) no jardim NOROESTE da
  praça (praça 51×51: em x=11, y=1, z=11 do molde, num círculo de pedra com 4 postes de luz azul e placa "Pedra de
  Teleporte"), com 2 blocos livres em cima. O `travel` registra o lugar quando o bloco é colocado pela estrutura.
- Estandartes do reino por paleta (Aldória azul/dourado, Sahra laranja com sol, Nordheim azul-claro com cruz branca).
  O quadro de missões do reino continua `faction_board[faction=kingdom]` (o bloco não tem os reinos novos na
  propriedade; o `cities` decide pelo `Kingdom` do coração).
- Bloco ou BlockEntity inexistente na hora de colocar o molde (ex.: módulo ainda sem o `travel_stone`) vira ar —
  nada quebra.

## 4. Nada do vanilla dentro das cidades (mixin)
`StructurePlacementMixin` injeta no RETURN de `StructurePlacement#isStructureChunk`: se o vanilla ia aceitar o chunk e
o conjunto é um destes — `minecraft:villages, pillager_outposts, desert_pyramids, jungle_temples, igloos, swamp_huts,
woodland_mansions, ruined_portals, trail_ruins, shipwrecks, ocean_ruins` — e há chunk de início de `guild_city` a ≤ 12 chunks ou de
uma capital a ≤ 14 chunks, devolve `false`. Só age quando o estado de geração TEM algum conjunto de cidade (Overworld);
outras dimensões e outros mods não mudam. Custo: só roda quando o chunk já era o sorteado (raro).
As estruturas do PRÓPRIO mod ficam de fora pelas zonas do `random_spread_multi` (§1). Rede de segurança (módulo
`cities`, fase B): o coração da cidade remove mobs hostis de molde que tenham caído dentro.

## 5. Mundos salvos (v0.8 → v0.9)
- Chunks já gerados não mudam. Aldória (`capital_city`) continua exatamente no mesmo lugar (mesmo anel e sal).
- `guild_city` mudou de grade (64/24 → 48/28): as cidades que JÁ existem continuam; as novas saem só em chunks ainda não
  gerados. Uma cidade nova pode, raramente, nascer colada na borda de uma área já explorada.
- Sahra e Nordheim ficam a ~1300–3000 blocos do spawn: se aquela área já foi gerada na v0.8, a capital não aparece lá
  (`/mahou worldgen kingdoms` mostra o ponto).
- Custo das zonas: como no vanilla, a zona olha o chunk SORTEADO do conjunto vizinho, mesmo que lá a cidade acabe não
  nascendo (bioma/terreno) — perto de um ponto de grade de cidade sem cidade também não nascem estruturas.

## 6. Como testar (cheats ligados, mundo NOVO ou chunks nunca gerados)
- `/mahou worldgen kingdoms` — lista onde cada capital vai nascer neste mundo (ponto previsto, clique para `/tp`),
  sem carregar chunks.
- `/locate structure isekai_mahou:capital_city` · `/locate structure isekai_mahou:capital_sahra` ·
  `/locate structure isekai_mahou:capital_nordheim` · `/locate structure isekai_mahou:guild_city` ·
  `/locate structure #isekai_mahou:city` (a mais perto de qualquer tipo).
- Nada dentro: perto de uma cidade, `/locate structure minecraft:village_plains` e `/locate structure
  isekai_mahou:orc_camp` não podem apontar para dentro dela (o resultado fica a > 10–14 chunks do chunk de início).
- Altar pronto: na Igreja ou na torre da Academia, o núcleo deve aceitar o tributo de qualquer jogador
  (`{PublicAltar:1b}`; regra do módulo `ascension`). `/data get block <x y z>` no núcleo mostra o NBT.
- Pedra de Teleporte: jardim noroeste da praça; `/data get block` no `city_heart` mostra `Kingdom`.
- Moldes: `py -I tools/gen/city_build.py` (gera e confere tudo, inclusive o padrão do altar, o `PublicAltar` e o
  `Kingdom`); `--preview church` / `--preview academy` desenham as camadas em ASCII.
