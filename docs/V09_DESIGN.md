# Isekai Mahou v0.9 — "Classes, Caminhos e o Dragão": design e contrato

Complementa `docs/ARCHITECTURE.md`, `docs/V07_DESIGN.md` e `docs/V08_DESIGN.md` (continuam valendo). Em conflito, ESTE vence.
APIs anteriores: `docs/v07/API_*.md`, `docs/v08/API_*.md`. APIs NOVAS (escritas na fase A): `docs/v09/API_classes.md` (A1),
`docs/v09/API_player.md` (A2: Dialogue, Onboarding, Travel), `docs/v09/API_worldgen.md` (A3).
Plano aprovado pelo jogador: `C:\Users\renan\.claude\plans\poh-vamos-l-eu-gentle-glade.md` (resumo de tudo; leia).

## 0. Pedido do jogador (feedback da v0.8 — "está MUITO BOM", gostou dos chefes)
1. Depois do Arauto do Crepúsculo, um **DRAGÃO GIGANTE que controla todos os elementos, com 5 fases**, que aparece quando o Arauto morre.
2. **3 categorias de classe, 5 classes cada (inspiradas em D&D):** Mágicas (as 5 de hoje), **Guerreiras** (espadachins e tanques — "as
   magias são condicionais e vinculadas aos elementos também, mas aprendem tipo resistência da terra, cura da luz, imbuir espada"),
   **À Distância** (arqueiros com flechas e coisas específicas).
3. **Portais não podem ser "portais do Nether"**: viram **estruturas** que você visita; a localização vem ao avançar a **rota principal**,
   que vira um **guia de chefes**. **Mais chefes no Overworld.**
4. A barra de vida (Olho Analítico) **às vezes se mexe** — tem que ficar parada.
5. Entrada no jogo com coisa demais: **escolher a classe → parabéns → J fechado → título → Espírito Guia explica a história, ensina J, B...**
6. **Upar mais fácil**: Ascensão sem 30 min de vanilla — **altares prontos + missões "A Ascensão"**; **fast travel** só para lugares
   visitados — **Pedras de Teleporte E aba no menu**.
7. Achou só 1 reino e havia um **acampamento orc dentro da cidade** → **vários reinos**, coisas longe ou perto, **nunca dentro**.
8. **Áreas de caça**: aceitar missão dá um local próximo; lá as criaturas nascem.
9. (depois) **Mais chefes na rota**, como a **Falsa Hidra**, e a rota tem que incluir os que já existem (o **Lich da cripta**, Arquimago
   Renegado, Bruxa Corrompida, Patrono Aprisionado, Ent Corrompido). **Melhorar o Atômico** — e com cuidado para não matar o amigo que
   joga junto. **Montar o personagem ao começar**: distribuir **pontos de atributo** e escolher **raça** (Humano no começo; as outras
   bloqueadas — "a partir daí, se vira!").

## 1. Fases
- **A (paralelo, implementador+revisor):** A1 classes (núcleo) · A2 jogador (diálogo, entrada, viagem — núcleo) · A3 worldgen (núcleo +
  cidades/geração) · art (ícones).
- **B (paralelo, implementador+revisor+verificador):** classes_martial, classes_ranged, armory, outfits, bosses_e (Dragão + gancho no
  Arauto), bosses_f, world_v09, dimensions, journal, ascension, travel, cities, guild, ui_hud.
- **C (coordenador):** build, ícones, testes no jogo, deploy. Versão 0.9.0. Skeletons/inits JÁ existem.

## 2. A1 — Classes (núcleo)
### 2.1 `core/MagicPath`
- Novo `enum Category { MAGIC, MARTIAL, RANGED }` e `enum Resource { MANA, VIGOR, FOCUS }` (cada um com `displayName()` traduzido e cor
  ARGB: MANA 0xFF7A5CFF, VIGOR 0xFFE0563B, FOCUS 0xFF3BC9A8). `MagicPath.category()`, `MagicPath.resource()` (MAGIC→MANA, MARTIAL→VIGOR,
  RANGED→FOCUS), `MagicPath.byCategory(Category)` (lista de 5), `PLAYABLE` = as 15.
- Classes novas (ids NOVOS; os 5 antigos ficam iguais e na mesma ordem no início do enum — o ordinal é usado no pacote de seleção):

| enum | id | nome PT | categoria | afinidade | difícil | arma do kit (id armory) |
|---|---|---|---|---|---|---|
| WARRIOR | warrior | Guerreiro | MARTIAL | Fogo, Vento | Sombra | `longsword_t1` |
| PALADIN | paladin | Paladino | MARTIAL | Luz, Terra | Sombra | `holy_mace_t1` |
| BARBARIAN | barbarian | Bárbaro | MARTIAL | Fogo, Terra | Luz | `greataxe_t1` |
| MONK | monk | Monge | MARTIAL | Vento, Água | Sombra | `ki_gauntlets_t1` |
| KNIGHT | knight | Cavaleiro | MARTIAL | Terra, Luz | Vento | `knight_blade_t1` (+ `knight_shield`) |
| RANGER | ranger | Patrulheiro | RANGED | Vento, Terra | Sombra | `longbow_t1` |
| ROGUE | rogue | Ladino | RANGED | Sombra, Vento | Luz | `twin_daggers_t1` |
| BARD | bard | Bardo | RANGED | Luz, Água | Sombra | `lute_t1` |
| ARTIFICER | artificer | Artífice | RANGED | Fogo, Terra | Água | `arcane_crossbow_t1` |
| HUNTER | hunter | Caçador | RANGED | Terra, Água | Fogo | `hunter_crossbow_t1` |
- Afinidade principal (1º elemento despertado ao escolher — o `paths` usa `MagicPath.primaryAffinity()` NOVO; as 5 antigas mantêm a
  atual): Guerreiro Fogo, Paladino Luz, Bárbaro Fogo, Monge Vento, Cavaleiro Terra, Patrulheiro Vento, Ladino Sombra, Bardo Luz,
  Artífice Fogo, Caçador Terra.
- Ao adicionar constantes, mantenha a ÁRVORE INTEIRA compilando: em módulos com `switch` exaustivo sobre MagicPath (ex.:
  `feature/outfits/client/OutfitAmbience.java`), A1 pode adicionar SÓ um ramo `default` seguro (exceção pontual de dono).

### 2.2 Recurso por categoria (mesmo valor salvo de mana)
- MANA: como hoje. VIGOR: regen passiva ×0,35; +3 (+5% do máx.) por golpe corpo a corpo que acerta (cooldown de ataque ≥ 0,8), +2 ao
  receber dano (máx. 1×/10 ticks). FOCUS: regen passiva ×0,5; +4 por projétil do jogador que acerta (flecha/tridente/arremesso de
  magia de forma `arrow`/`throw`), +1/s parado e agachado mirando com arco/besta. Meditação vale para todos.
- Textos: "Mana/Vigor/Foco" (`resource.isekai_mahou.<mana|vigor|focus>`), `MagicApi.resourceName(Player)`, cor `MagicApi.resourceColor`.
  Mensagens de "sem mana" usam o nome do recurso. (O HUD/barra é do `ui_hud`; a Janela de Status também.)

### 2.3 Formas de ARMA no motor de glifos (núcleo, registradas em `CoreGlyphs`)
| id | tipo | regra |
|---|---|---|
| `strike` "Golpe" | FORMA ARCANE t1 C1 | por 6 s (ou 1 golpe), o PRÓXIMO golpe corpo a corpo do conjurador aplica os efeitos no alvo atingido (`ctx.impact`), com rastro de arma brilhando na cor do elemento; conjuração 0, ideal p/ guerreiros. Com Dividir: 1 golpe a mais por cópia |
| `arrow` "Flecha" | FORMA ARCANE t1 C1 | se o conjurador segura arco/besta e tem flecha (ou é criativo), dispara uma FLECHA REAL (entidade vanilla `Arrow` marcada) que ao acertar entidade/bloco aplica os efeitos (`ctx.impact`/`end`); sem arco: projétil mágico em forma de flecha. Perfurar/Ricochete/Teleguiado/Dividir valem. Consome 1 flecha |
| `stance` "Postura" | FORMA ARCANE t2 C1 | enquanto o jogador segura a tecla (canal) os efeitos se aplicam a ELE a cada 1 s (buffs: Pele de Pedra, Curar, Velocidade...), andando a 60%; ao soltar, acaba |
| `parry` "Aparar" | GATILHO reativo ARCANE t2 C1 | o slot sai sozinho quando o jogador BLOQUEIA um golpe com escudo ou é atingido corpo a corpo nos 0,3 s após começar a atacar; mira o atacante (implementar como os reativos de `feature/arcane/ArcaneTriggers`, mas no núcleo) |
- Todas suportam estágios quando fizer sentido (`stageable()`).
- `CoreGlyphs.STARTER` por categoria: MARTIAL aprende `strike`, `stance`, `parry` + os iniciais; RANGED aprende `arrow` + os iniciais.
  Magias iniciais nos slots Arcanos ao escolher: MARTIAL "Golpe Arcano" [strike, arcane_damage] e "Postura Firme" [stance, shield];
  RANGED "Flecha Arcana" [arrow, arcane_damage] e "Flecha Repulsora" [arrow, push]; MAGIC como hoje.

### 2.4 Tela de classe e kits
- `client/PathSelectionScreen` refeita: 3 abas no topo (Mágicas / Guerreiras / À Distância, com ícone `textures/gui/category/<magic|martial|ranged>.png`),
  lista de 5 classes da aba (ícone `textures/gui/class/<id>.png` feito pelo art — reserva: ícone do kit), painel com nome, estilo, descrição,
  afinidades, recurso (com cor), arma inicial e kit. Esc continua bloqueado. Botão "Escolher". Traduções `path.isekai_mahou.<id>`,
  `.style`, `.desc` para as 15.
- `ClassKits.icon` com reserva genérica (arma do kit) para classes novas. Os kits das classes novas são registrados pelos módulos
  `classes_martial`/`classes_ranged`/`armory`/`outfits` via `ClassKits.addItem/setArmor` (por id, tolerando ausência).
- Escrever `docs/v09/API_classes.md`.

## 3. A2 — Jogador: Diálogo, Entrada, Viagem (núcleo)
### 3.1 `core/api/Dialogue`
```java
static void show(ServerPlayer p, String dialogueId, Component speaker, int rgb, List<Dialogue.Page> pages)
record Page(Component text, @Nullable String await)   // await: null (avança com Enter/clique/tempo), "key:tracker", "key:menu",
                                                      // "screen:grimoire", "cast", "key:wheel" — o cliente espera o jogador fazer
static void close(ServerPlayer p)
// Evento NeoForge.EVENT_BUS: DialogueEvent.Finished(ServerPlayer, String dialogueId, boolean skipped)
```
- Cliente: caixa de diálogo na parte de baixo da tela (acima da barra de itens; não pausa o jogo, não tira o controle), com nome do
  falante na cor, texto aparecendo letra a letra (som suave), "▶ Enter" para avançar, contador de página, botão/tecla para PULAR tudo
  (Backspace). Quando a página tem `await`, mostra a tecla destacada ("Aperte [J]") com o nome REAL da tecla configurada e espera.
  Tecla nova `CoreKeys.DIALOGUE_NEXT` (Enter). Fila: um diálogo por vez.
### 3.2 Entrada no jogo (núcleo)
- Login sem classe: SÓ a tela de classe. `KeyConflictFix` e o aviso do Curios ficam guardados e só aparecem depois do evento
  `OnboardingEvent.TutorialDone` (ou 5 min depois de escolher a classe).
- `CoreGameplay.choosePath`: sem as linhas de chat; manda o cliente mostrar a tela/letreiro **"Parabéns!"** (nome da classe grande na
  cor, ícone, categoria, recurso, fanfarra — 4 s) e posta `MagicProgressEvent.PathChosen` como hoje. Os outros módulos que hoje
  mandam chat/toast ao escolher (paths `startAffinity`) devem ficar silenciosos durante a entrada: núcleo expõe `Onboarding.quiet(Player)`
  (true até o tutorial acabar ou 5 min) — os módulos consultam.
- `MahouConfig.SHOW_OBJECTIVES` padrão **false** (o tutorial liga quando o jogador aperta J). Rastreador `ObjectiveTracker.setVisible`.
- Evento `OnboardingEvent.ClassCongratsDone(ServerPlayer)` disparado ~4 s depois da escolha (o journal começa o capítulo 1 com título
  e chama o Espírito Guia + tutorial via `Dialogue`). `Onboarding.finishTutorial(ServerPlayer)` → `OnboardingEvent.TutorialDone`.
  Flags `onboard.class`, `onboard.tutorial`. Comando op `/mahou onboarding reset|skip`.
### 3.2b Etapas de criação (para a criação de personagem)
- `Onboarding.addCreationStep(String id)` (módulos registram no init); depois da escolha de classe o "Parabéns" SÓ aparece quando o
  jogador concluir todas as etapas: `Onboarding.pendingCreationSteps(Player)`, `Onboarding.completeCreationStep(ServerPlayer, id)`;
  evento `OnboardingEvent.CreationStep(ServerPlayer, id)` disparado quando a etapa deve abrir (o módulo abre a sua tela). Ordem de registro.
  Jogadores antigos (já com classe) não passam pelas etapas a menos que o módulo peça (`/mahou onboarding reset`).

### 3.3 `core/api/Travel` (fast travel sobre `Places`)
```java
static boolean discovered(Player p, String placeId)          // flag visit.place.<id> (já gravada pelo Places) ou travel.<id>
static void discover(ServerPlayer p, String placeId)          // grava + aviso "Novo destino de viagem: <nome>"
static List<Places.Place> destinations(ServerPlayer p)        // lugares descobertos com ponto de chegada (inclui hubs de dimensões visitadas)
static int cost(ServerPlayer p, Places.Place to, boolean fromStone)  // pedra: 10 + 1 por 50 blocos (máx. 120); menu: ×3 e recarga
static TravelResult travel(ServerPlayer p, String placeId, boolean fromStone)  // paga o recurso, efeito de partida/chegada, teleporta
                                                                     // (entre dimensões só para hubs já visitados), recarga do menu 10 min
```
- Ponto de chegada: `Place.center` ajustado à superfície segura, ou o `travel_stone` do lugar se houver (o módulo `travel` registra).
- Pacotes: lista de destinos para o cliente (a tela é do módulo `travel`). Escrever `docs/v09/API_player.md`.

## 4. A3 — Worldgen e cidades (núcleo + geração)
- **Posicionamento novo** `isekai_mahou:random_spread_multi` (registrar `StructurePlacementType` em `Registries.STRUCTURE_PLACEMENT`,
  classe estendendo `RandomSpreadStructurePlacement`, codec com `"exclusion_zones": [{"other_set": id, "chunk_count": n}, ...]`,
  override de `isStructureChunk` testando todas as zonas). Aplicar em TODOS os `structure_set` de superfície do mod (manter os campos e
  sais atuais): zona `isekai_mahou:guild_city` ≥ 9 chunks, `isekai_mahou:capital_city`/`capital_sahra`/`capital_nordheim` ≥ 11, e manter
  `minecraft:villages` 4 onde já havia.
- **Mixin** em `StructurePlacement#isStructureChunk` (ou equivalente seguro) para que conjuntos VANILLA de superfície (vilas, postos de
  saqueadores, ruínas, cabanas) não nasçam a ≤9 chunks de candidatos de cidade/capital do mod. Arquivo em `mixin/` + registro no json.
- **Mais cidades**: `guild_city` 48/28; `max_slope` 36, `max_water` 0,3; em `CityStructure` o limite de bioma ruim 0,55 para cidades e
  NÃO contar pontos de rio/praia como ruins; mais biomas (meadow, cherry_grove, old_growth_birch_forest, windswept_savanna, sparse_jungle,
  wooded_badlands) com paletas coerentes.
- **3 reinos**: estruturas `capital_city` (Aldória, plains — mantém), `capital_sahra` (Império de Sahra, paleta desert, maior), `capital_nordheim`
  (Reino de Nordheim, paleta snowy). Sahra/Nordheim por `concentric_rings` com `distance` maior (≈1500–3000 blocos), `preferred_biomes` do
  reino, sais próprios. O `city_heart` do molde recebe `{Kingdom:"aldoria"|"sahra"|"nordheim"}` no NBT (cidade comum: reino pela paleta:
  plains→aldoria, desert→sahra, snowy→nordheim). `CityType` "capital" nas 3 capitais.
- Templos/Igrejas das cidades e a Torre da Academia ganham um **Altar de Ascensão PRONTO** (blocos do módulo ascension por id:
  `isekai_mahou:ascension_core` no centro + pedestais `isekai_mahou:ascension_pedestal` + `isekai_mahou:rune_stone` no padrão 5×5 do
  `AscensionPattern` — confira os ids reais) com BlockEntity NBT `{PublicAltar:1b}` no núcleo; e uma **Pedra de Teleporte**
  `isekai_mahou:travel_stone` na praça (bloco do módulo travel). Tag `#isekai_mahou:city` inclui as 3 capitais.
- Escrever `docs/v09/API_worldgen.md`.

## 5. Módulos da fase B (resumo — o plano aprovado tem os detalhes)
### classes_martial (5 classes guerreiras)
- Formas: `cleave_arc` "Arco de Lâmina", `charge_rush` "Investida", `spin` "Giro", `war_cry` "Grito de Guerra", forma G `battle_avatar`
  "Avatar de Batalha" (transformação corpo a corpo 30 s: tamanho maior, golpes em área, armadura de energia do elemento).
- Origens (glifos com `origin(MagicPath.X)`): Guerreiro `riposte`, `blade_storm`, `heroic_strike`, `second_wind` · Paladino `smite`,
  `lay_on_hands`, `divine_shield`, `aura_of_devotion` · Bárbaro `rage`, `reckless_attack`, `earth_shatter`, `primal_roar` · Monge
  `flurry_of_blows`, `ki_palm`, `step_of_wind`, `stunning_strike` · Cavaleiro `shield_bash`, `taunt`, `bulwark`, `iron_fortress`.
- **Provas de Classe** (ritual das 5): 5 ranks por classe com tarefas (contadores: abates corpo a corpo, dano bloqueado, duelos com
  "Campeão" — NPC desafiante que o módulo cria —, chefes), cada rank grava `MagicApi` contador `ritual_rank` +1 e dá glifos de origem,
  XP e itens; aba/tela própria no menu B ("Provas de Classe", compartilhável com classes_ranged por id de aba diferente).
- Kit de cada classe (arma por id do armory, roupa por id do outfits, itens) via `ClassKits`.
### classes_ranged (5 classes à distância)
- Formas: `volley` "Saraivada", `throw` "Arremesso", `snare` "Armadilha", `song` "Canção" (aura contínua do bardo enquanto toca),
  `mark_shot` "Tiro Marcado"; forma G `celestial_arrow_rain` "Chuva de Flechas Celestial".
- Origens: Patrulheiro `hunters_mark`, `piercing_shot`, `multishot`, `wind_arrow` · Ladino `backstab`, `poison_blade`, `smoke_bomb`,
  `shadow_dagger` · Bardo `inspire`, `healing_ballad`, `dissonant_chord`, `lullaby` · Artífice `arcane_bomb`, `gear_turret`,
  `infuse_item`, `flamethrower` · Caçador `beast_companion`, `bear_trap`, `crossbow_bolt`, `pack_tactics`.
- Provas de Classe das 5 (abates a >30 blocos, tiros na cabeça/críticos, buffs em aliados, engenhocas, abates com a fera...). Kits.
### armory — armas novas (4 tiers, modelos 3D, atributos, passiva no tier IV)
`longsword`, `holy_mace`, `greataxe`, `ki_gauntlets`, `knight_blade` + item `knight_shield` (escudo funcional), `longbow` (arco funcional
mais forte), `twin_daggers`, `lute` (instrumento: "toca" e conta como foco do Bardo), `arcane_crossbow`, `hunter_crossbow` (bestas
funcionais) — ids `<arma>_t1..t4`. Atualizar `WeaponKind.forPath`, marcos de nível, `PromotionGear`, `TrialRewards.armoryReward`,
`CityTrades.ARMORY` (por id), atributos: guerreiras dano/vel. de ataque/armadura + MAX_MANA(vigor); à distância dano de projétil + foco.
### outfits — 10 conjuntos × 4 tiers (aprendiz/adepto/mestre/arquimago, mesmo padrão de ids e de entrega por Círculo)
Guerreiro (cota e capa), Paladino (placas brancas/douradas), Bárbaro (peles e ossos), Monge (gi e faixas), Cavaleiro (armadura pesada e
elmo), Patrulheiro (capuz verde e capa), Ladino (capuz e máscara), Bardo (chapéu de pluma e gibão), Artífice (óculos, avental e
engrenagens), Caçador (peles e chapéu de caçador). `OutfitAmbience` ganha os ramos novos.
### bosses_e — Dragão Primordial (`primordial_dragon`)
§2 do plano: 5 fases (Fogo; Água/Gelo; Terra; Vento/Raio; Luz+Sombra com o domínio "Fim dos Elementos"), GeckoLib ~20 blocos voando,
3000 de vida + escala de grupo, telegrafia, Pilha, status, destruição, anti-trapaça; ao matar o Arauto (gancho em
`feature/bosses_b/entity/TwilightHerald.java` — SÓ a chamada de spawn por id na morte final, editável por este trabalho) a arena da
Fenda se rasga numa cutscene e o Dragão surge; recompensas `primordial_dragon_heart`, título, lendas; contador `boss.primordial_dragon`.
### bosses_f — 4 chefes do Overworld
`swamp_hydra` "Hidra do Pântano" (3→5 cabeças, veneno, regenera cabeças com fogo impedindo), `wyvern_king` "Wyvern Rei dos Picos"
(voo, raios, investidas), `desert_basilisk` "Basilisco do Deserto" (olhar que Petrifica — status do núcleo, cauda, escava), `ruin_colossus`
"Colosso das Ruínas" (golem gigante, crateras, pedras). GeckoLib, fases, telegrafia, recompensas.
### world_v09 — estruturas novas (structgen + JSON, posicionamento `random_spread_multi`)
Covis: `hydra_lair` (pântano), `wyvern_peak` (picos), `basilisk_tomb` (deserto), `colossus_ruins` (planície/floresta) com o chefe por
entidade no molde. Portais (com o portal ADORMECIDO pronto, blocos do dimensions por id): `elemental_gate` (círculo de pedra com 4
obeliscos e moldura elemental), `abyssal_chasm` (cânion com moldura sombria no fundo), `celestial_stairway` (templo de montanha com o
Farol sobre ouro), `twilight_rupture` (ilha rachada com a Fenda). `ascension_sanctum` (Santuário de Ascensão com altar pronto
`{PublicAltar:1b}`). Pedras de Teleporte `isekai_mahou:travel_stone` em TODAS essas estruturas. **Veio de Mana**: blocos
`mana_vein_ore`/`deepslate_mana_vein_ore` (drop 1–3 cristais de mana, Fortuna) + feature de minério no Overworld (y −16..80, comum) +
aglomerados `mana_crystal_cluster` perto de santuários. Tags `#isekai_mahou:boss_lair`, `#isekai_mahou:portal_site`.
### dimensions — portais adormecidos
Sem as receitas de moldura/Farol/Fenda (os blocos seguem funcionando em mundos antigos e por comando); ativação das estruturas como
hoje (oferenda + Círculo). `/mahou dimension info` mostra onde fica o portal se descoberto. Hubs de dimensão registrados no `Places`
(para a Viagem).
### journal — rota-guia de chefes + entrada
Reescrever `chapters.json` como rota de chefes/portais com `locate` (Rei Slime → Rei Goblin → Lorde Orc → Minotauro → os 4 chefes novos →
Portão Elemental → Guardiões → Fenda Abissal + Escadaria Celeste → Soberano + Serafim → Ruptura → Arauto → Dragão), passos
"A Ascensão → Círculo N" (missão que dá o tributo pronto do Círculo via contadores/recompensas), mantendo ids antigos quando fizer sentido.
O capítulo 1 começa no `OnboardingEvent.ClassCongratsDone` com o título; o **Espírito Guia** aparece e roda o tutorial por `Dialogue`
(história curta → "Aperte J" [liga o rastreador] → "Aperte B" → "Abra o Grimório" → "Conjure com Z" → `Onboarding.finishTutorial`),
pulável; dicas do espírito também por `Dialogue` (não chat). Epílogo/finale passa a ser depois do Dragão (`boss.primordial_dragon`).
### ascension — altares prontos e tributos baratos
Altar com `{PublicAltar:1b}` funciona para qualquer jogador (sem precisar ser dono), não pode ser quebrado em sobrevivência; anel 9×9 sem
blocos de cristal; tributos ~¼ das essências arcanas, sem diamante/estrela (trocar por itens de chefe da rota); missão "A Ascensão" (o
journal entrega item `ascension_tribute_<n>` "Tributo do Círculo N" que vale como tributo completo — item deste módulo).
### travel — Pedras de Teleporte e aba Viagem
Bloco `travel_stone` (pedra rúnica com runa brilhante; ao ser colocado por estrutura, registra `Places` tipo `waystone` com nome do lugar
mais próximo); tocar = `Travel.discover`; usar = tela "Viagem" (mapa simples com pontos + lista, custo, botão Viajar). Aba "Viagem" no menu
B (de qualquer lugar, `fromStone=false`, recarga). Receita cara para o jogador fazer a própria pedra (nome pela bigorna).
### cities — reinos, purga, altar e pedra
`city_heart` lê `Kingdom`; facções novas `kingdom_sahra` "Império de Sahra" e `kingdom_nordheim` "Reino de Nordheim" (JSON em
`data/isekai_mahou/factions/` + emblemas; Aldória continua `kingdom`); NPCs/guardas pertencem ao reino da cidade (FactionMember);
nomes de cidade por reino; ao registrar a cidade, remover mobs hostis de molde/estruturas dentro (inclusive persistentes que não são
da cidade nem nomeados por jogador) e blocos de "acampamento" óbvios; registrar a pedra de teleporte e o altar público.
### guild — áreas de caça
Missões de caça (kill) ganham "Área de Caça": ao aceitar, servidor escolhe um ponto a 150–500 blocos no bioma da criatura (use a
lógica de `JournalLocator.nearest`/`findClosestBiome3d`), cria `Places` tipo `hunting_ground` (temporário), estandarte + círculo no
chão, seta no rastreador; ao chegar, ondas das criaturas da missão nascem por perto (escala por grupo/rank); some ao concluir/abandonar.
Essências caem dos mobs das áreas de caça.
### character — criação de personagem: atributos e raça (módulo NOVO)
- Etapa de criação "character" (§3.2b): tela "Monte seu Personagem" logo depois da classe: **raça** (Humano liberado; Elfo, Anão,
  Meio-Orc, Demônio (Tiefling), Dragonato e Bestial BLOQUEADOS com dica misteriosa de como desbloquear) e **6 atributos estilo D&D**
  (Força, Destreza, Constituição, Inteligência, Sabedoria, Carisma): todos começam em 8 e o jogador distribui 27 pontos de compra
  (custo D&D: 9→1 … 15→9; máx. 15 na criação), com sugestão automática pela classe e botão "Sugerido".
- Efeitos (modificadores de atributo com ids próprios, aplicados no login/mudança): FOR dano corpo a corpo e carga; DES velocidade de
  ataque, dano de projétil, esquiva leve; CON vida máx. e resistência a status; INT poder mágico e mana máx.; SAB regen de mana/recurso e
  resistência mágica; CAR preços (Reputation), mais companheiros, duração de buffs em aliados. Modificador = (valor−10)/2 estilo D&D.
- +1 ponto a cada 4 níveis (aba "Personagem" no menu B para distribuir; máx. 20 por atributo). Raças dão bônus (Humano +1 em tudo;
  Elfo +2 DES +1 INT, visão no escuro; Anão +2 CON +1 FOR, mineração; Meio-Orc +2 FOR +1 CON, fúria; Demônio +2 CAR +1 INT, resistência a
  fogo; Dragonato +2 FOR +1 CAR, sopro; Bestial +2 DES +1 SAB, velocidade).
- **Desbloqueio de raças ("se vira!")**: cada raça tem um feito secreto (Elfo: Exaltado com o Povo Selvagem; Anão: minerar 500 blocos
  abaixo de y 0 e matar o Rei Kobold/visitar o Covil Kobold; Meio-Orc: Amigável com a Horda; Demônio: selar um pacto com um Patrono;
  Dragonato: derrotar o Dragão Primordial; Bestial: domar 10 animais e derrotar a Manticora). Ao desbloquear: aviso dramático; trocar de
  raça num "Altar da Reencarnação" (bloco do módulo, nas cidades/Academia por id e por receita) — redistribui os pontos também.
- Raça visível: tom/orelhas/chifres/caudas como camada de render no jogador (simples, por raça).

### bosses_g — mais chefes (módulo NOVO)
`false_hydra` "Falsa Hidra" (sob uma vila abandonada — covil próprio; cabeças/pescoços invisíveis que só aparecem quando você não olha;
canto que "apaga a memória": esconde o rastreador/minimapa/HUD por instantes e embaralha a barra de itens — e cura a hidra; precisa
achar o corpo enterrado), `mimic_king` "Rei Mímico" (sala do tesouro: baús viram mímicos, ele devora jogadores e cospe; língua
telegrafada), `manticore` "Manticora" (cauda de espinhos em leque, voo curto, rugido que amedronta). Covis próprios gerados pelo módulo
(estruturas com random_spread_multi). GeckoLib, fases, telegrafia, BossKit, BossRewards, ovos, /mahou spawnboss.

### Rota-guia (journal) — incluir TODOS os chefes
Ordem sugerida: Rei Slime → Rei Goblin → Lorde Orc → Minotauro → Ent Corrompido → Bruxa Corrompida → Arquimago Renegado → **Lich da
Cripta** → Patrono Aprisionado → Hidra do Pântano → Basilisco → Colosso → Wyvern → Falsa Hidra → Manticora → Rei Mímico → Portão Elemental →
Guardiões → Fenda Abissal + Escadaria Celeste → Soberano + Serafim → Ruptura → Arauto → Dragão Primordial (vários como opcionais/paralelos
para não ficar linear demais; cada capítulo com 2–4 chefes).

### legends_d — Atômico melhorado
"Eu Sou Atômico" mais épico: carga de 3 s com o conjurador flutuando, anéis em camadas, letreiro "Eu... sou..." letra a letra e
"ATÔMICO!", câmera tremendo; e **NÃO fere aliados**: jogadores do mesmo time/grupo, companheiros contratados, invocações e animais
domados do conjurador e (sem PvP) qualquer jogador ficam numa bolha protetora azul durante a explosão (sem dano, sem empurrão
mortal, sem cair na cratera — ficam flutuando 3 s). Aviso para jogadores próximos durante a carga ("Abriguem-se!").

### ui_hud — HUD estável + recurso
Olho Analítico com posição/tamanho FIXOS (largura 196, altura reservada fixa, y fixo abaixo da última barra de chefe; sem o deslocamento
animado do Jade — com Jade, posição fixa sem colisão; sem `belowCardIfCramped` alternando; nome do lugar/Procurado não mudam a largura);
barras flutuantes com âncora suavizada. Barra de recurso com nome/cor por categoria (Mana/Vigor/Foco) e Janela de Status idem.

## 6. Ids novos (catálogo v0.9)
- Núcleo A1: glifos `strike`, `arrow`, `stance`, `parry`. A2: tecla `key.isekai_mahou.dialogue_next`. A3: tipo de posicionamento
  `random_spread_multi`; estruturas `capital_sahra`, `capital_nordheim`.
- character: blocos `reincarnation_altar`; atributos `isekai_mahou:{strength,dexterity,constitution,intelligence,wisdom,charisma}` (ou dados próprios).
  bosses_g: `false_hydra`, `mimic_king`, `manticore` + covis `false_hydra_lair`, `mimic_vault`, `manticore_den`.
- classes_martial: `cleave_arc`, `charge_rush`, `spin`, `war_cry`, `battle_avatar` + 20 origens (§5). classes_ranged: `volley`, `throw`,
  `snare`, `song`, `mark_shot`, `celestial_arrow_rain` + 20 origens (§5).
- armory: `<longsword|holy_mace|greataxe|ki_gauntlets|knight_blade|longbow|twin_daggers|lute|arcane_crossbow|hunter_crossbow>_t1..t4`,
  `knight_shield`. bosses_e: `primordial_dragon`, `primordial_dragon_heart`. bosses_f: `swamp_hydra`, `wyvern_king`, `desert_basilisk`,
  `ruin_colossus`. world_v09: estruturas do §5, `mana_vein_ore`, `deepslate_mana_vein_ore`, `mana_crystal_cluster`. travel:
  `travel_stone`. ascension: `ascension_tribute_2..10`. cities: facções `kingdom_sahra`, `kingdom_nordheim`.

## 7. Donos de arquivos
| trabalho | pode editar |
|---|---|
| A1 classes | `core/MagicPath.java`, `core/ClassKits.java`, `core/CoreGameplay.java` (só o que for de classe/iniciais), `core/MagicApi.java` (resourceName/Color), `core/classes/**` (novo), `glyph/CoreGlyphs.java` + `glyph/weapon/**` (formas de arma), `client/PathSelectionScreen.java`, `client/classes/**`, `src/main/lang/core_classes/`, ramos `default` em switches exaustivos de módulos, `docs/v09/API_classes.md` |
| A2 jogador | `core/api/{Dialogue,Travel,Onboarding}.java`, `core/event/{DialogueEvent,OnboardingEvent}.java`, `core/onboard/**`, `client/onboard/**`, `client/KeyConflictFix.java`, `client/CoreKeys.java` (tecla nova), `client/objectives/ObjectiveTracker.java` (setVisible), `core/MahouConfig.java` (padrão SHOW_OBJECTIVES), `core/CoreGameplay.java` (só chat/parabéns da escolha), `core/CoreEvents.java` (só login), `feature/equipment/client/CuriosKeyFix.java` (só adiar), `src/main/lang/core_onboard/`, `docs/v09/API_player.md` |
| A3 worldgen | `core/worldgen/**`, `mixin/StructurePlacementMixin.java` (+ json do mixin), `feature/cities/world/**`, `tools/gen/city_build.py`, `data/isekai_mahou/worldgen/structure_set/*.json` (só trocar o tipo/zonas), `data/isekai_mahou/worldgen/structure/{guild_city,capital_city,capital_sahra,capital_nordheim}.json`, tags de bioma das cidades, `data/isekai_mahou/structure/city/**`, `src/main/lang/cities_world/`, `docs/v09/API_worldgen.md` |
| art | `tools/gen/icons.py`, `assets/isekai_mahou/textures/gui/{glyph,class,category,resource}/**`, `textures/gui/tab/{travel,class_trials}.png` |
| cada módulo B | o próprio módulo (`feature/<m>/**`, `src/main/lang/<m>/`, `tools/gen/<m>.py`, recursos com nomes próprios) + exceções: bosses_e pode editar SÓ o gancho de morte em `feature/bosses_b/entity/TwilightHerald.java`; journal edita `data/isekai_mahou/journal/**`; ascension edita `data/isekai_mahou/ascension/**`; guild edita `data/isekai_mahou/{guild_quests,guild_shop}/**`; cities edita `data/isekai_mahou/factions/kingdom_*.json` e tags de facção dos reinos; world_v09 edita `data/isekai_mahou/{structure,worldgen}/**` das SUAS estruturas e features; dimensions edita as receitas antigas do módulo e `paths`/`PathRules.PRIMARY` NÃO (isso é do classes); classes_martial/ranged podem editar `feature/paths/PathRules.java` SÓ para usar `MagicPath.primaryAffinity()` (o martial faz; o ranged não mexe) |
