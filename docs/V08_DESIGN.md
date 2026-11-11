# Isekai Mahou v0.8 — "Reinos e Lendas": design e contrato

Complementa `docs/ARCHITECTURE.md` e `docs/V07_DESIGN.md` (continuam valendo). Em conflito, ESTE documento vence.
APIs da v0.7 (continuam valendo): `docs/v07/API_engine.md`, `API_progression.md`, `API_boss.md`.
APIs NOVAS da v0.8 (escritas pela fase A): `docs/v08/API_world.md` (A1), `docs/v08/API_stack.md` (A2), `docs/v08/API_reputation.md` (A3).
Mapas de pesquisa da v0.6/v0.7 (ainda úteis): `C:\Users\renan\AppData\Local\Temp\claude\a85216ce7956359b\3f5ad9af-2628-4cd6-a069-6c670d6a919e\scratchpad\v07\map_*.md`.

---------------------------------------------------------------------------------------------------
## 0. O que o jogador pediu (feedback da v0.7 — "de resto ESTÁ MUITO BOM, os ícones ficaram absurdos")

1. **Cidades** onde fica a Guilda, com **NPCs aventureiros** e outros NPCs para **trocas** etc.
2. **Sistema de reputação.**
3. **Mais magias**, principalmente as **misturas (fusões)**.
4. Lendas novas: **I AM ATOMIC** (Eminence in Shadow), **Desintegração** (Tensura), **Parar o Tempo** (Za Warudo), **Magias Supremas do
   Overlord**, **Transmutação em cabra**.
5. Lendas mais **específicas** (visual e mecânica próprios, não genéricos): **MUGEN** (Infinito do Gojo); no **Santuário Maligno** os cortes
   têm que ser os do Sukuna (Desmantelar/Clivar) e **o santuário tem que aparecer**.
6. **Alcance maior** (Domínio etc.) e **destruir o mundo** lutando com magias fortes — com opção de **ligar/desligar** a destruição.
7. **Mais magias de G** (ultimate), não só Cataclismo.
8. **Meteoro** e **chuva de meteoros**.
9. **Condições de status**: Molhado (aumenta dano de raio), Em Chamas (dano extra de fogo + dano contínuo), Congelado (perde velocidade,
   conjuração etc.) — e outras.
10. **Contramágica e mecânicas de Magic: The Gathering.** "Melhore o sistema."
11. **Regra da barreira do Domínio** (pedido depois): "o domínio pode ser quebrado por fora, mas não por dentro; se você inverter, vira
    por dentro e não por fora! Com isso, um domínio maior só ataca fora!" → §11.1.

As homenagens a animes são **homenagens** (nomes em português, mecânicas e visuais próprios), nunca cópia de assets.

---------------------------------------------------------------------------------------------------
## 1. Fases e donos

- **Fase A (núcleo + independentes, em paralelo):** A1 mundo (status, reações, destruição, lugares) · A2 pilha (contramágica, eventos de dano,
  API de domínios) · A3 reputação · `city_build` (geração das cidades) · `art` (ícones/texturas).
- **Fase B (módulos, em paralelo, implementador + revisor):** `cities`, `guild`, `legends_c`, `legends_d`, `legends_e`, `ultimates`, `fusions_c`,
  `fusions_d`, `arcane_duel`, `power_up`, `ui_hud`.
- **Fase C (coordenador):** build, ícones faltantes, testes no jogo, deploy.

Regra de ouro: só edite os arquivos que são SEUS (§14). Módulo NÃO usa classe Java de outro módulo — só ids em texto, eventos e APIs do núcleo.
Skeletons dos módulos novos e os `init` do núcleo novo JÁ existem e já estão ligados (`Modules.java`, `ClientModules.java`, `IsekaiMahou.java`,
`IsekaiMahouClient.java`); as configs novas JÁ existem em `core/MahouConfig.java` (§2.6). Versão: 0.8.0.

---------------------------------------------------------------------------------------------------
## 2. A1 — Mundo: condições de status, reações, destruição, lugares

Pacotes: `core/status/**` (impl), `core/api/{Statuses,Destruction,Places}.java` (API), `client/status/**` (cliente). Init: `StatusCore.init` /
`StatusCoreClient.init` (já chamados). Traduções: `src/main/lang/core_status/`.

### 2.1 Condições de status (MobEffects no `CoreRegistries`)
| id | nome PT | acúmulos | regra |
|---|---|---|---|
| `soaked` (já existe) | **Molhado** (renomear a tradução) | 1 | Raio +50% (Eletrocutar consome); Fogo −50% e apaga Em Chamas; Gelado acumula em dobro. Aplicado ao ficar na água/chuva (refresca 5 s), por magias de Água/Abismo/Vapor |
| `burning` NOVO | **Em Chamas** | 1–5 | dano contínuo mágico (0,5 + 0,5·acúmulo por s, ×poder de quem aplicou, guardado no efeito/NBT), dano de Fogo recebido +8%/acúmulo, cura recebida −30%; põe fogo visual; Água/Molhado apaga. Fogo vanilla (`isOnFire`) conta como Em Chamas 1 para reações |
| `chilled` NOVO | **Gelado** | 1–5 | −12% velocidade/acúmulo, −8% vel. de ataque/acúmulo, +15% tempo de conjuração/acúmulo, −20% regen de mana/acúmulo (jogador). Ao chegar a 5 → **Congelado** |
| `frozen` (já existe) | **Congelado** | 1 | preso no gelo: não anda, não ataca, não conjura (2–4 s; chefes 1 s e depois Gelado 3). Imunidade a novo Congelado por 6 s |
| `shocked` NOVO | **Eletrizado** | 1–3 | Raio +15%/acúmulo; a cada s, 12%·acúmulo de chance de micro-atordoar 0,5 s; jogador perde 2 mana/s/acúmulo; Molhado+Eletrizado arqueia para Molhados a ≤4 blocos |
| `bleeding` NOVO | **Sangrando** | 1–10 | 0,4/acúmulo por s; o dobro se o alvo se moveu no último segundo. Aplicado por cortes (APIs dos módulos) |
| `petrified` NOVO | **Petrificação** | 1–3 | 1–2: −20%/acúmulo de velocidade ("Enrijecendo"); 3: **Petrificado** (pedra: atordoado 2 s, +50% armadura) e zera |
| `corrupted`, `exposed`, `stunned`, `rooted`, `silenced`, `magic_exhaustion` | já existem | | sem mudança de regra |

- **Chefes** (tag `c:bosses`, `BossKit.isTrialBoss`, ou vida máx. ≥ 150): acúmulos recebidos pela metade (arredonda para cima), durações ×0,5,
  Congelado/Petrificado viram 1 s + Lento. Config `STATUS_ON_PLAYERS` desliga em jogadores.
- **Aplicação automática pelo elemento** (em `ElementReactions.onSpellHit`, para todo dano de magia; poder = `ctx.power`):
  FOGO/MAGMA/SOL/CHAMA INFERNAL → Em Chamas +1 · ÁGUA/ABISMO/VAPOR → Molhado · GELO → Gelado +1 (+2 se Molhado) · RAIO → Eletrizado +1 ·
  CRISTAL → Petrificação +1 · CREPÚSCULO → Corrompido curto. Terra, Vento, Luz, Sombra, Arcano: nada automático (reações e glifos próprios).
- **Clima/ambiente:** quem está em água/chuva fica Molhado (checa a cada 20 ticks); neve em pó → Gelado; lava/fogo → Em Chamas 1.
- Duração padrão: 6 s (refresca ao reaplicar). Cada acúmulo novo reinicia a duração.

### 2.2 Reações (tabela em `ElementReactions`, reescrita de forma limpa)
| reação | condição | efeito |
|---|---|---|
| **ELETROCUTAR** | Molhado + Raio | dano ×2, atordoa 2 s, consome Molhado, arco para Molhados a ≤4 blocos (50% do dano) |
| **CONGELAR** | Molhado + Gelo | Congelado direto, consome Molhado |
| **VAPOR** | Em Chamas + Água | apaga, cegueira em área 2,5 (já existe) |
| **INCÊNDIO** | Em Chamas + Vento | espalha Em Chamas a ≤3 blocos (já existe) |
| **DERRETER** | (Em Chamas + Gelo) ou (Gelado/Congelado + Fogo) | dano ×1,6, remove os dois |
| **SOBRECARGA** | Em Chamas + Raio | mini-explosão 2,5 blocos (3·poder, SEM quebrar blocos), remove Em Chamas |
| **SUPERCONDUTOR** | Gelado + Raio | Exposto II por 5 s |
| **ESTILHAÇAR** | (Congelado ou Petrificado) + Terra/dano físico corpo a corpo | dano ×1,75, remove o estado (já existe para Congelado) |
| **LACERAR** | Sangrando + Vento | +3 Sangrando |
| **RUPTURA** | Luz + Sombra em sequência (≤3 s) | ×1,3 (já existe) |
| **AREIA NOS OLHOS** | Terra + Vento em sequência | cegueira (já existe) |
- Cada reação: partículas + som + **texto flutuante** ("ELETROCUTAR!") sobre o alvo para jogadores a ≤32 blocos (pacote S2C; cliente desenha
  billboard 1,2 s; config cliente `SHOW_REACTION_TEXT`). Evento `ElementReactionEvent` (NeoForge.EVENT_BUS) com alvo, reação, contexto.

### 2.3 API `core/api/Statuses`
```java
enum Statuses.Kind { WET, BURNING, CHILLED, FROZEN, SHOCKED, BLEEDING, PETRIFIED, CORRUPTED, EXPOSED, STUNNED, ROOTED, SILENCED }
static void apply(LivingEntity target, Kind kind, int stacks, int ticks, @Nullable LivingEntity source, float power)  // soma acúmulos (até o máx.)
static int stacks(LivingEntity e, Kind kind)      // 0 = não tem
static boolean has(LivingEntity e, Kind kind)
static void clear(LivingEntity e, Kind kind)
static Holder<MobEffect> effect(Kind kind)
static boolean isBossLike(LivingEntity e)
```
- **Sincronização**: o servidor manda aos jogadores que rastreiam a entidade a lista (tipo, acúmulos, ticks restantes) quando muda
  (+ ao começar a rastrear). Cliente: `client/status/ClientStatuses.get(Entity)` → `List<Entry(Kind kind, int stacks, int ticksLeft)>` e
  `ClientStatuses.icon(Kind)` (ResourceLocation 18×18 `textures/mob_effect/<id>.png`).
- **Ícones sobre os mobs** (cliente A1): fileira pequena de ícones com número de acúmulos sobre a cabeça dos mobs com status a ≤24 blocos,
  config `SHOW_STATUS_ICONS`. (O `ui_hud` pode desenhar os ícones no Olho Analítico e nas barras flutuantes — aí ele chama
  `ClientStatuses.setWorldIconsHandledExternally(true)` para o A1 não desenhar duplicado.)
- Visual no mundo: Em Chamas = fogo vanilla + brasas; Gelado = partículas de neve, tinta azulada; Congelado = bloco de gelo translúcido
  em volta (render layer genérico no cliente); Eletrizado = faíscas; Sangrando = gotas; Petrificado = tinta cinza de pedra.

### 2.4 API `core/api/Destruction` — destruir o mundo (com regra de ligar/desligar)
```java
enum Destruction.Mode { ERASE, BREAK, SCORCH, VITRIFY, FREEZE, CRUSH }
static boolean allowed(ServerLevel level, @Nullable Entity caster)        // regra + config + mobGriefing (mobs) + criativo/aventura
static boolean protectedAt(ServerLevel level, BlockPos pos, @Nullable Entity caster)
static void addProtection(BiPredicate<ServerLevel, BlockPos> p)
static int sphere  (ServerLevel l, Vec3 center, float radius, @Nullable Entity caster, Mode mode)
static int crater  (ServerLevel l, Vec3 center, float radius, @Nullable Entity caster)              // tigela + borda queimada + escombros
static int cylinder(ServerLevel l, Vec3 base, float radius, float height, @Nullable Entity caster, Mode mode)
static int line    (ServerLevel l, Vec3 from, Vec3 to, float width, float height, @Nullable Entity caster, Mode mode)  // corte/túnel
static int surface (ServerLevel l, Vec3 center, float radius, @Nullable Entity caster, Mode mode)  // só a camada de cima (queimar, congelar, vitrificar)
```
- Retorno = blocos enfileirados. **Fila global** processada no fim do tick do servidor, do centro para fora, até
  `MahouConfig.DESTRUCTION_BLOCKS_PER_TICK` (3000) por tick; fila máx. 250 000 (o excesso é descartado com aviso no log).
- **Regra de jogo** `mahouSpellDestruction` (boolean, padrão **true**, `GameRules.register(..., Category.MISC, ...)`): `/gamerule mahouSpellDestruction false`
  desliga. Atalho op: `/mahou destruction on|off|status`. Também precisa `MahouConfig.SPELL_BLOCK_DESTRUCTION` = true. Conjurador mob: também `mobGriefing`.
- Nunca destrói: bedrock e blocos com dureza < 0, `#minecraft:wither_immune`, `#minecraft:dragon_immune`, blocos com BlockEntity (a menos que
  `DESTRUCTION_BLOCK_ENTITIES`), a dimensão `isekai_mahou:trial_realm`, áreas protegidas (`Places` seguros, `addProtection`), e onde o jogador
  conjurador não pode interagir (`level.mayInteract`).
- Modos: ERASE (some, sem drops) · BREAK (quebra, 10% dos drops) · SCORCH (pedra→pedra-negra/basalto, terra/grama→terra grossa/magma,
  fogo na superfície) · VITRIFY (areia→vidro, pedra→obsidiana chorona/vidro tingido) · FREEZE (água→gelo/gelo compactado, superfície→neve) ·
  CRUSH (esmaga: blocos viram cascalho/pedregulho e afundam 1).
- **Escombros**: ~1 a cada 40 blocos removidos vira um `BlockDisplay` (ou entidade própria simples) lançado para fora que some em 2–3 s;
  NUNCA coloca bloco nem dá item. Poeira/fumaça e som de desmoronamento em lotes.
- `crater`: remove esfera achatada (raio r, profundidade r·0,6), borda SCORCH, fogo em 15% da borda, escombros, tremor (BossKit.shake).

### 2.5 API `core/api/Places` — lugares nomeados (cidades etc.)
```java
record Places.Place(String id, Component name, String type, String faction, ResourceKey<Level> dim, BoundingBox box,
                    BlockPos center, boolean safeZone, int color)
static Place register(ServerLevel level, Place place)   // idempotente pelo id (atualiza)
static void remove(ServerLevel level, String id)
@Nullable static Place get(ServerLevel level, String id)
@Nullable static Place at(ServerLevel level, BlockPos pos)
@Nullable static Place nearest(ServerLevel level, BlockPos pos, @Nullable String type)
static List<Place> all(ServerLevel level)
```
- `SavedData` por nível. Tipos usados: `"capital"`, `"city"`, `"village"`.
- Entrada/saída (checa cada jogador a cada 20 ticks): evento `PlaceEvent.Enter/Leave`; no cliente, letreiro grande central
  "Valdória" + subtítulo (nome traduzido da facção `faction.isekai_mahou.<faction>`) com a cor do lugar; contadores `visit.<type>` (+1 na
  1ª visita de cada lugar) e flag `visit.place.<id>`. Cliente: `client/status/ClientPlaces.current()` (lugar atual ou null).
- `safeZone` (e config `CITY_PROTECTION`): Destruction protegido, explosões não quebram blocos dentro, e monstros (MobCategory.MONSTER)
  não nascem naturalmente dentro (spawn natural e de chunk).
- Comando op `/mahou place list|tp <id>|remove <id>`.

### 2.6 Configs novas (JÁ criadas pelo coordenador em `core/MahouConfig.java`)
Comum: `DESTRUCTION_BLOCKS_PER_TICK` (3000), `DESTRUCTION_BLOCK_ENTITIES` (false), `STATUS_ON_PLAYERS` (true), `CITY_PROTECTION` (true).
Cliente: `SHOW_STATUS_ICONS`, `SHOW_REACTION_TEXT`, `SHOW_STACK`, `REPUTATION_TOASTS` (todas true).

---------------------------------------------------------------------------------------------------
## 3. A2 — A Pilha (contramágica), eventos de dano, API de domínios, usos diários

Pacotes: `core/stack/**` (impl), `core/api/{SpellStack,Domains}.java`, `core/event/{SpellDamageEvent,PendingCastEvent,SpellCounteredEvent}.java`,
`client/stack/**`. Init: `StackCore.init` / `StackCoreClient.init`. Traduções: `src/main/lang/core_stack/`.

### 3.1 `core/api/SpellStack` — conjurações pendentes (inspirado na "pilha" de Magic: The Gathering)
Tudo que está **para acontecer** (golpe telegrafado de chefe, magia sendo canalizada, ultimate carregando) vira uma **conjuração pendente**
que pode ser **respondida** (contramágica) antes de resolver.
```java
enum SpellStack.Tier { MINOR, MAJOR, ULTIMATE }
final class SpellStack.Pending {  // somente leitura para os módulos
    int id(); @Nullable LivingEntity caster(); UUID casterId(); ResourceKey<Level> dim(); Vec3 pos(); float radius();
    long startTick(); long endTick(); Tier tier(); Component name(); @Nullable Element element(); boolean counterable(); boolean countered();
}
static Pending push(ServerLevel l, LivingEntity caster, Vec3 pos, float radius, int ticks, Tier tier, Component name,
                    @Nullable Element element, @Nullable Runnable onCountered)
static void resolve(Pending p)                    // aconteceu (sai da pilha, sem contramágica)
static void cancel(Pending p)                     // sumiu (dono morreu etc.)
static List<Pending> near(ServerLevel l, Vec3 pos, double range)        // ativas
static CounterResult counter(ServerLevel l, Pending p, LivingEntity by, float power)   // COUNTERED, RESISTED (poder baixo), COOLDOWN, NOT_COUNTERABLE
static int counterNear(ServerLevel l, Vec3 pos, double radius, LivingEntity by, float power)  // contra todas as hostis a "by" cujo pos OU conjurador esteja no raio
record SpellStack.Recent(UUID caster, String casterName, SpellRecipe recipe, Element element, float power, long tick, Vec3 pos)
static void recordCast(LivingEntity caster, SpellRecipe recipe, Element element, float power)
static List<Recent> recent(ServerLevel l, Vec3 pos, double range, int maxAgeTicks)   // para "Copiar Magia"
```
- **Integração com `core/boss/Telegraph`** (A2 é dono do Telegraph nesta versão): todo `Telegraph.delayed*(...)` COM dono (`.owner(e)`) cria
  (ou junta-se à) conjuração pendente do dono naquele tick — várias formas criadas pelo mesmo dono no mesmo tick = UMA pendente (um golpe).
  Contramágica cancela TODAS as formas do grupo (`Telegraph.cancel`, sem callback). Tier pelo aquecimento: <20 ticks MINOR, 20–59 MAJOR,
  ≥60 ULTIMATE. Novos métodos na Shape: `.castName(Component)`, `.tier(SpellStack.Tier)`, `.uncounterable()`. Nome padrão: nome do dono.
  `Telegraph.show(...)` (só visual) NÃO cria pendente.
- **Integração com `SpellCaster`**: jogador canalizando/carregando uma magia com tempo de conjuração ≥ 10 ticks → pendente (tier por custo:
  <40 MINOR, <120 MAJOR, ultimate/Domínio = ULTIMATE); contramágica → `SpellCaster.interrupt` + Silenciado 2 s. Toda conjuração resolvida
  (jogador e `castFrom`) → `recordCast`.
- **Regras da contramágica** (`counter`):
  - Poder necessário: MINOR 0,5 · MAJOR 1,0 · ULTIMATE 2,0 (poder = `SpellStats.power()` do glifo de contramágica × poder do contexto).
  - Mesmo dono não pode ser contrariado de novo por 12 s se for chefe (RESULT `COOLDOWN`, mensagem "O chefe resiste!").
  - Chefe contrariado: MAJOR → Atordoado 30 ticks + Exposto I 5 s; ULTIMATE → Atordoado 60 ticks + Exposto II 8 s.
  - Efeitos: estilhaço de vidro mágico + círculo rúnico quebrando no `pos` e no conjurador, letreiro "CONTRAMÁGICA!" para quem está a ≤48,
    som; eventos `SpellCounteredEvent` (pendente, quem contrariou) e `PendingCastEvent` (postado no `push`: módulos podem reagir — gatilho
    "Em Resposta").
- **HUD "Pilha"** (cliente A2; config `SHOW_STACK`): painel à esquerda, no meio da tela: até 5 pendentes HOSTIS a ≤48 blocos (de chefes/mobs
  e de jogadores que não são você), cada uma com runa do elemento (`GlyphArt.element`, ou ícone de perigo), nome do conjurador + nome do
  golpe, barra de contagem regressiva, marca de tier (ULTIMATE em vermelho pulsante) e "✦ pode ser contrariado" quando o jogador conhece
  um glifo de contramágica (`counterspell`, `mana_leak`). Pacote S2C quando entra/sai/é contrariada.

### 3.2 `core/event/SpellDamageEvent` (postado por `SpellContext.damage`)
- Ordem: dano base → `ElementReactions.onSpellHit` (status/reações, A1) → **`SpellDamageEvent.Pre`** (cancelável; `getAmount/setAmount`,
  `ctx()`, `target()`, `element()`) → hurt → **`SpellDamageEvent.Post`** (`dealt`, `overkill` = dano além da vida que o alvo tinha, `killed`).
- Usado por aumentos estilo MTG (Toque Mortal, Atropelar — módulo `arcane_duel`) e pelo Infinito (`legends_c`).

### 3.3 `core/api/Domains` — Domínios para outros módulos
```java
record Domains.Info(int id, ServerLevel level, LivingEntity owner, Vec3 center, float radius, boolean open, Set<ResourceLocation> laws,
                    Element element, @Nullable SpellContext ctx, long openedTick, int durationTicks)
interface Domains.LawHandler { default void onOpen(Info d){} default void onTick(Info d, int age){} default void onClose(Info d){}
                               default void onEnemyTick(Info d, LivingEntity e, int age){} }
static void registerLaw(ResourceLocation lawId, LawHandler handler)
@Nullable static LawHandler handler(ResourceLocation lawId)
static void opened(Info d); static void ticked(Info d, int age); static void closed(Info d)   // chamados pelo módulo domain
static List<Info> active(ServerLevel l); @Nullable static Info at(ServerLevel l, Vec3 pos)
static boolean sureHitOn(LivingEntity target)   // o domínio MAIS INTERNO que contém o alvo é INIMIGO e tem law_sure_hit (fura o Infinito)
// v0.8 — barreira (regra do §11.1). O módulo domain guarda a integridade e registra o handler.
interface Domains.BarrierHandler { float integrity(int domainId); float maxIntegrity(int domainId);
                                   float damage(int domainId, float amount, boolean fromInside, @Nullable Entity attacker); }
static void setBarrierHandler(BarrierHandler h)
static float hitBarrier(ServerLevel l, Vec3 hitPos, Vec3 attackerPos, @Nullable Entity attacker, float amount)
        // acha o domínio FECHADO cuja parede está a ≤1,5 de hitPos; lado = attackerPos dentro (dist<r) ou fora;
        // aplica a regra (normal: só de FORA quebra; invertida: só de DENTRO); devolve o dano aplicado (0 = a barreira aguentou)
@Nullable static Info innermost(ServerLevel l, Vec3 pos)   // o domínio fechado de MENOR raio que contém pos (ou o aberto, se não houver fechado)
static boolean shielded(LivingEntity target, Info attackerDomain)  // alvo protegido por uma barreira fechada de outro dono dentro do domínio atacante
```
`Domains.Info` ganha `boolean inverted()` (barreira invertida). `open == true` = sem barreira (não tem integridade, não pode ser quebrado).
O núcleo só guarda o registro e chama os handlers; o módulo `domain` (trabalho `power_up`) chama `opened/ticked/closed` e os handlers das
leis registradas por outros módulos (`legends_c`: `law_shrine`, `law_infinite_void`).

### 3.4 Usos diários (para Magias Supremas)
`MagicApi.dailyUses(ServerPlayer p, String key)` e `MagicApi.tryUseDaily(ServerPlayer p, String key, int max)` → boolean (dia do mundo =
`level.getDayTime()/24000`; guardado em contadores `daily.<key>` + `daily.<key>.day`). Criativo ignora o limite.

---------------------------------------------------------------------------------------------------
## 4. A3 — Reputação e facções

Pacotes: `core/reputation/**`, `core/api/Reputation.java`, `client/reputation/**`. Init: `ReputationCore.init` / `ReputationCoreClient.init`.
Traduções: `src/main/lang/core_reputation/`. Dados: `data/isekai_mahou/factions/<id>.json` (recarregáveis com /reload, sincronizados).

### 4.1 Facções
| id | nome PT | cor | membros (tag `isekai_mahou:faction/<id>` + interface) | rivais |
|---|---|---|---|---|
| `guild` | Guilda dos Aventureiros | 0xE8B84A | guild_receptionist, guild_master, adventurer | — |
| `kingdom` | Reino de Aldória | 0x3F6FD8 | city_guard, citizen, city_lord, city_merchant (exceto os de outra facção), villager, iron_golem | horde, underworld (½) |
| `academy` | Academia Arcana | 0xB98CFF | wandering_mage (creatures_b), city_merchant escriba | — |
| `church` | Igreja da Aurora | 0xFFE9A8 | city_merchant sacerdote | underworld |
| `underworld` | Submundo | 0x8A2B3A | city_merchant receptador | church, kingdom (½) |
| `wildfolk` | Povo Selvagem | 0x4FBF4A | fairy e espíritos da natureza (procure os ids nos módulos creatures_*) | horde (½) |
| `horde` | A Horda | 0xA0522D | goblin, goblin_archer, goblin_shaman, goblin_wolf_rider, hobgoblin, goblin_king, orcs, kobolds (procure os ids) | kingdom |
- Interface `core/api/Reputation.FactionMember { String factionId(); }` — entidades podem decidir a facção (ex.: mercadores por profissão);
  senão vale a tag. `Reputation.factionOf(Entity)` → `@Nullable String`.

### 4.2 Valores e patamares (`Reputation.Standing`)
Valor de −1000 a +1000, guardado em contadores do `MagicData` `rep.<id>` (sincronizados como o resto do MagicData).
| patamar | de | preço (`priceMultiplier`) | efeito |
|---|---|---|---|
| ENEMY "Inimigo" | −1000 | — (não negocia) | membros atacam à vista |
| HOSTILE "Hostil" | −600 | — (não negocia) | guardas/membros atacam |
| UNFRIENDLY "Desconfiado" | −300 | ×1,25 | |
| NEUTRAL "Neutro" | −49 | ×1,0 | |
| FRIENDLY "Amigável" | 100 | ×0,95 | membros hostis por natureza (Horda, fadas) ficam neutros |
| HONORED "Honrado" | 300 | ×0,9 | |
| REVERED "Reverenciado" | 600 | ×0,8 | |
| EXALTED "Exaltado" | 900 | ×0,7 | |
```java
static int get(Player p, String faction)            // funciona no cliente (dados sincronizados)
static Standing standing(Player p, String faction)
static int add(ServerPlayer p, String faction, int amount, @Nullable Component reason)  // retorna o novo valor
static void set(ServerPlayer p, String faction, int value)
static float priceMultiplier(Player p, String faction); static boolean canTrade(Player p, String faction)
static boolean isHostile(Player p, String faction); static boolean isFriendly(Player p, String faction)
static Collection<Faction> factions(); @Nullable static Faction faction(String id)
```
- `add`: aplica respingo nos rivais (−50% do ganho positivo; ½ = −25%) e aliados (+25%); evento `ReputationChangedEvent` (jogador, facção,
  antes, depois); aviso no cliente ("+15 Reputação — Guilda dos Aventureiros", na lateral, config `REPUTATION_TOASTS`); ao subir de patamar:
  `Rewards.banner` + recompensas do patamar (uma vez cada; flag `rep.<id>.<patamar>`), ao cair: aviso vermelho.
- **Fontes automáticas (núcleo A3)**: matar membro de uma facção → `kill_penalty` do JSON (padrão −25; guardas −40; matar aldeão −30 Reino);
  matar membro da Horda → +2 Reino (limite 40/dia por facção de bônus); matar monstro hostil dentro de um `Place` de tipo cidade/capital →
  +1 Reino (limite 30/dia); concluir provação → +10 Academia. As outras fontes são dos módulos (missões, serviços, crimes).
- **Neutralidade**: mobs da Horda/Povo Selvagem não escolhem como alvo jogadores com patamar ≥ FRIENDLY nessa facção (LivingChangeTargetEvent),
  a menos que o jogador os ataque (vingança normal).
- **Recompensas de patamar (JSON)**: por facção e patamar `friendly/honored/revered/exalted`: `xp`, `items` [{id,count}] (ids ausentes são
  ignorados), `glyph_scrolls` (tier), `legend` ("random" ou id de lenda), `title` (chave de tradução). Ex.: Academia Exaltada → pergaminho de lenda.
- **Aba "Reputação"** no menu B (`MenuTabs.register("reputation", 55, ...)`): um cartão por facção com o emblema
  (`textures/gui/faction/<id>.png` 32×32, feito pelo `art`), nome, patamar colorido, barra até o próximo patamar com números, valor,
  vantagens do patamar atual e do próximo (`faction.isekai_mahou.<id>.perk.<standing>`), rivais. Título escolhido (opcional) mostrado no topo.
- Comandos: `/mahou rep [jogador]` (lista), op: `/mahou rep set|add <jogador> <facção> <n>`.

---------------------------------------------------------------------------------------------------
## 5. Cidades (`city_build` = geração · `cities` = vida/NPCs)

### 5.1 `city_build` (fase A) — estrutura própria, cidades de verdade
- **Tipo de estrutura Java próprio** (`feature/cities/world/**`, registrado em `CityWorldgen.register`): `isekai_mahou:guild_city` — monta
  a cidade num **lote em grade** (~112×112 a 128×128, centro na estrutura): **muralha de pedra com torres e 2–4 portões**, avenida principal
  em cruz até a **praça central** (chafariz + `city_heart`), ruas secundárias, iluminação (lanternas/postes), bancos, canteiros, árvores.
  Lotes preenchidos por **templates .nbt** gerados por `tools/gen/city_build.py` (com `tools/structgen.py`). Terreno: aplainar/fundação
  (pilares de pedra até o chão, `beard_box`) — nada flutuando nem enterrado. Paletas por bioma: `plains` (carvalho+pedra+tijolos, padrão),
  `desert` (arenito, cúpulas), `snowy` (abeto+pedra, telhados íngremes). Altura máx. por peça respeitando o chunk.
- **Distritos/prédios (todos com interior mobiliado):** Salão da Guilda grande (balcão `guild_counter` + Recepcionista, sala do Mestre,
  `quest_board`, mesas, troféus) · Taverna/Estalagem (balcão, quartos com camas, lareira) · Mercado (barracas) · Ferraria · Alquimia ·
  Torre da Academia (biblioteca, mesa de encantamento) · Igreja da Aurora (vitrais, altar) · Quartel (guardas, armeiros) · Mansão do
  Lorde · Casas (3+ variações) · Beco do Submundo (porta escondida/porão com o receptador) · Estábulos · Jardim/fazendinha.
- **Quadros de facção:** `isekai_mahou:faction_board[facing=...,faction=<guild|kingdom|academy|church|underworld>]` (bloco do `guild`): no Salão
  da Guilda (guild), praça (kingdom), Academia (academy), Igreja (church), Beco (underworld).
- **NPCs por template** (entidades no .nbt; ids do módulo `cities`): `isekai_mahou:city_guard` (portões, muralha, praça; 6–10),
  `isekai_mahou:citizen` (6–12), `isekai_mahou:city_merchant` com NBT `{Profession:"<p>"}` — `blacksmith`, `alchemist`, `scribe`, `jeweler`,
  `innkeeper`, `priest`, `fence`, `grocer`, `tailor` — um em cada prédio certo; `isekai_mahou:city_lord` na mansão; guild: `guild_receptionist`,
  `guild_master` no Salão. Todos com `PersistenceRequired:1b`.
- **`city_heart`** (bloco do `cities`) na praça, com NBT de BlockEntity `{Radius:<meia largura em blocos>, CityType:"capital"|"city",
  Palette:"plains"|"desert"|"snowy"}`.
- **Onde nascem**: `worldgen/structure_set/guild_city.json` — `random_spread` espaçamento 64 chunks, separação 24, salt próprio, bioma
  `#isekai_mahou:has_structure/guild_city` (planícies, savana, floresta, taiga, deserto, neve plana — sem oceano/rio/montanha);
  `worldgen/structure_set/capital_city.json` — `concentric_rings` (distance 6, spread 1, count 1, `preferred_biomes` planícies/floresta) para
  a **Capital perto do spawn** (~350–600 blocos) — estrutura `isekai_mahou:capital_city` (mesmo tipo Java, maior: ~144×144, castelo do Lorde).
  Tags `#isekai_mahou:city` (as duas) para localizar.
- Não usar jigsaw para a cidade inteira (limite de 128 e sem muralha). Peças podem ser templates por lote + código para muros/ruas.

### 5.2 `cities` (fase B) — NPCs, comércio, serviços, crimes
- Bloco `city_heart` (invisível/decorativo: pedestal com cristal azul) + BlockEntity: no 1º tick registra `Places.register` (id `city_<x>_<z>`,
  nome gerado pela semente do mundo — lista de nomes de fantasia em PT ("Valdória", "Eldrath", "Pedralva", "Lunária", "Brumafria",
  "Sóis-de-Areia"...; a capital se chama **Aldória**), tipo capital/city, facção `kingdom`, `safeZone` = `CITY_PROTECTION`, caixa = Radius).
- **NPCs** (GeckoLib humanoides com variações de roupa/cabelo/pele por textura; animações idle/walk/talk/attack; nomes de fantasia):
  - `city_guard` "Guarda": patrulha perto de casa, ataca monstros hostis na cidade, ataca jogadores **Procurados** ou com Reino ≤ HOSTILE;
    clicar Procurado → diálogo "pagar fiança" (moedas) limpa.
  - `citizen` "Cidadão": anda, conversa ("Rumores": aponta uma estrutura do mod por perto — `ServerLevel.findNearestMapStructure` — e
    manda seta para o rastreador de objetivos), foge de monstros, de noite fica dentro.
  - `city_merchant` (profissões §5.1): trocas com `MerchantMenu` vanilla (moeda: `isekai_mahou:guild_coin` e esmeraldas), preço ×
    `Reputation.priceMultiplier` da facção da profissão, ofertas extras liberadas por patamar (ex.: escriba vende pergaminhos de glifo por tier
    conforme Academia; Exaltado vende `Legends.scroll` de lendas; ferreiro vende armas do `armory` por id; joalheiro acessórios; alquimista
    poções/elixires; receptador itens raros e "contratos"; taverneiro comida/bebida + **quarto** (dorme e define ponto de nascimento) +
    **Rumores**; sacerdote **serviços**: cura total, remover status/maldição, Bênção da Aurora 10 min). Estoque renova por dia.
  - `city_lord` "Lorde/Lady": diálogo com notícias do reino e missões (abre o quadro do Reino se houver — o bloco é do guild; o Lorde só
    orienta) e recompensa especial ao atingir Honrado/Exaltado (título "Herói de Aldória").
  - Tela de **diálogo** própria (retrato 3D do NPC, nome, profissão, facção + patamar colorido, fala que muda com a reputação, botões
    Conversar / Trocar / Serviços / Rumores / Despedir-se).
- **Crimes**: ferir NPC da cidade → −10 Reino e status "Procurado" (efeito `wanted`, 5 min; matar → −60 e 15 min); guardas próximos
  vêm atacar. Pagar fiança ou esperar limpa. Abrir baús de prédios da cidade (marcados no template com NBT `CityChest:1b` OU tudo dentro da
  área da cidade que não foi colocado pelo jogador — use o que for confiável) estando visível a um NPC → −5 Reino, +2 Submundo.
- **Cerco à Cidade** (evento): à noite, com jogador na cidade, 4% de chance por noite de um cerco da Horda (3 ondas de goblins/orcs vindo
  do portão, sinos, barra de evento); defender → +40 Reino, moedas, −15 Horda. Telegrafa a chegada (título + sino).
- **Rep Reino**: matar monstro na cidade (núcleo faz), defender cerco, ajudar guardas.
- Provedor do `ObjectiveTracker`: rumor ativo (seta + distância) e "Procurado — pague a fiança" quando procurado.

---------------------------------------------------------------------------------------------------
## 6. `guild` (fase B) — aventureiros vivos, contratos, quadros de facção, missões

- **Aventureiros** (entidade `adventurer` existente): nome de fantasia, classe (guerreiro/arqueiro/ladino/mago + NOVO **clérigo**), **rank F–S**
  na plaqueta; trocas com preço × `Reputation.priceMultiplier(p,"guild")`; diálogo.
- **Contratar** (mercenário/companheiro): botão no diálogo; preço por rank (F 8 moedas … S 120) por 3 dias de jogo (renovável); segue o dono
  (teleporta se longe), luta contra o alvo do dono e hostis, usa habilidades da classe — **magos e clérigos conjuram magias de glifo** via
  `SpellCaster.castFrom` (mago: projétil/feixe do seu elemento; clérigo: cura no dono/aliados e luz contra mortos-vivos); ganha experiência
  (sobe de rank com abates). Máx. 2 companheiros (3 com Guilda ≥ Honrado). Agachar + clicar: seguir/esperar. Contrato acaba → despedida.
  Morre → some (aviso). Companheiro nunca ataca o dono nem aliados; não conta como "membro ferido" para crimes.
- **Grupos de aventureiros** nascem em cidades (`Places` tipo city/capital: na taverna/salão) e nas estradas (2–4 juntos).
- **Quadros de facção**: bloco `faction_board` (propriedades `facing` e `faction` = guild|kingdom|academy|church|underworld). O `quest_board`
  antigo continua (= guild). O quadro abre a MESMA tela de missões filtrada pela facção. `GuildQuest` JSON ganha `"faction"` (padrão
  "guild") e `rewards.reputation: {"kingdom": 20, ...}`; missões de facção não-guild são liberadas por **patamar** da facção (campo
  `"standing": "neutral"|"friendly"|...`), não por rank da Guilda. Toda missão da Guilda dá + reputação Guilda (5–30 pelo rank).
- **Missões novas (JSON, ~45)**: Reino (defender a cidade, caçar Horda perto de cidades, escoltar/entregar suprimentos, limpar covil),
  Academia (conjurar N magias de um elemento, entregar cristais/essências, concluir Provação, provocar N reações elementais — contador
  `reaction.<id>` que o A1 grava?), Igreja (mortos-vivos, purificar, doar moedas), Submundo (contratos noturnos, entregar itens raros,
  "roubar" — pegar item X), Povo Selvagem (plantar árvores — contador de mudas plantadas se houver —, proteger fadas, matar Horda na floresta).
  Contadores que o núcleo grava: `kill.<ns>.<mob>`, `boss.<id>`, `cast.*`, `trial.clear`, `visit.city`, `visit.capital`, `rep.<id>`,
  `reaction.<nome>` (A1 grava +1 por reação causada pelo jogador).
- **Diário** (só JSON `data/isekai_mahou/journal/**`): passos novos "Chegue à Capital" (`locate` `#isekai_mahou:city` / contador
  `visit.capital` ou `visit.city`), "Contrate um aventureiro", "Alcance Amigável com uma facção", "Responda a um golpe com Contramágica"
  (contador `counter.success` gravado pelo A2), "Cause 10 reações elementais".

---------------------------------------------------------------------------------------------------
## 7. Lendas novas — glifos ESPECÍFICOS com visual próprio (cada um registra suas lendas em `core/api/Legends`)

Regra: cada glifo lendário tem **entidade/renderização própria** (modelos GeckoLib ou renderers com quads/partículas/feixes), som, letreiro.
Destruição de terreno SEMPRE via `Destruction` (respeita a regra). Números escalam com `stats.power()`/`radius()` e são fortes de verdade.

### 7.1 `legends_c` — Técnicas Malditas (Jujutsu)
| id | tipo | elem / tier / C | o que faz |
|---|---|---|---|
| `infinity` "Infinito" | efeito (em si) | ARCANE lendário, C5 | **Mugen**: por 8–20 s (drena 2 mana/s): tudo que se aproxima desacelera assintoticamente e PARA a ~1,5 bloco; projéteis ficam suspensos no ar e caem ao fim; ataques corpo a corpo e de projétil contra você são anulados (SpellDamageEvent/LivingIncomingDamage), EXCETO de quem está num domínio com Acerto Garantido (`Domains.sureHitOn`) e de chefes (que causam 25%). Visual: distorção esférica sutil, partículas presas |
| `lapse_blue` "Azul" | efeito | ARCANE t4, C4 | ponto de atração violento: puxa entidades e (com destruição) arranca blocos ERASE numa esfera que encolhe, esmaga quem fica no centro |
| `reversal_red` "Vermelho" | efeito | ARCANE t4, C4 | repulsão: explosão de onda vermelha que arremessa longe, dano alto, cratera pequena |
| `dismantle` "Desmantelar" | efeito | WIND t4, C4 | corte invisível reto e LONGO (24 blocos + área): linha branco-avermelhada, corta entidades (Sangrando 3) e **corta o terreno** (`Destruction.line` ERASE fino, marcas limpas). Com Dividir: vários cortes em leque |
| `cleave` "Clivar" | efeito | WIND t4, C4 | corte que se adapta: dano = base + 8% da vida máx. + 1/ponto de armadura do alvo; padrão de cortes em grade sobre o alvo, Sangrando 5 |
| `flame_arrow` "Fuga" | efeito | FIRE lendário, C6 | arco de chamas se forma na mão; flecha flamejante que explode em coluna de fogo e cratera SCORCH ao longo do caminho |
| `law_shrine` "Lei: Santuário Maligno" | LEI | ARCANE, C6 | ao abrir: **o Santuário aparece** (modelo GeckoLib grande ~10 blocos: templo com bocas, chifres, crânios e véu vermelho) atrás do dono; céu vermelho para quem está dentro; o domínio vira **sem barreira** (aberto) e com raio ×1,5; a cada 10 ticks, Desmantelar em blocos aleatórios (terreno é retalhado — com destruição ligada) e Clivar em todo inimigo dentro (Acerto Garantido) |
| `law_infinite_void` "Lei: Vazio Infinito" | LEI | ARCANE, C6 | interior vira cosmos (estrelas, nebulosa — visual do cliente para quem está dentro); inimigos dentro ficam Atordoados (sobrecarga de informação) os 3 primeiros s e depois a cada 4 s por 1 s; Lentos o tempo todo |
- Lendas: `malevolent_shrine` "Santuário Maligno" (SUBSTITUI a do `legends_b`: edite só essa entrada em `feature/legends_b/LegendRecipes.java`;
  receita: `domain_expansion, law_shrine, dismantle, cleave, law_sure_hit, amplify` — confira no parser), `unlimited_void` "Vazio Infinito",
  `blue` "Azul", `red` "Vermelho", `infinity` "Infinito", `fuga` "Fuga", `world_cutting_slash` "Corte que Divide o Mundo" (Desmantelar colossal).
- Ligue as leis com `Domains.registerLaw` (§3.3).

### 7.2 `legends_d` — Atômico, Desintegração, Tempo, Cabra
| id | tipo | elem / tier / C | o que faz |
|---|---|---|---|
| `atomic` "Atômico" | efeito | ARCANE lendário, C7 | **I AM ATOMIC**: o conjurador flutua, anéis azul-violeta se formam, fala "Eu... sou..." (letreiro letra a letra) e "**ATÔMICO!**": esfera azul-violeta se expande a partir do ponto (raio 12 + 6·área, ×poder), dano enorme ignorando armadura, **cratera gigante** (`Destruction.crater`), cúpula de choque, flash branco na tela de quem viu, tremor forte. Conjurador imune à própria explosão |
| `disintegration` "Desintegração" | efeito | LIGHT lendário, C6 | uma caixa/cilindro de círculos mágicos sagrados envolve o alvo (ou área); 1,5 s depois luz pura **desintegra**: dano = 40% da vida máx. (chefes 12%) + 20·poder ignorando armadura, os alvos se desfazem em partículas, **cilindro de terreno apagado** até o céu (ERASE) |
| `time_stop` "Parar o Tempo" | **FORMA ultimate (G)** | ARCANE, C6 | **Za Warudo**: onda que inverte cores se expande (raio 48); tela cinza para quem está no raio; tudo que não é o conjurador (e aliados) **para**: mobs, projéteis, itens, blocos caindo (`EntityTickEvent.Pre` cancelado); jogadores parados (efeito `time_stopped`: sem andar/pular/atacar/conjurar). Dura 3 s + 1 s por Estender/Círculo≥8 (máx. 9 s); relógio tiquetaqueando. Projéteis do conjurador lançados durante a parada **param no ar após 0,5 s** e retomam quando o tempo volta. Dano causado em congelados é **acumulado e aplicado de uma vez** quando o tempo volta (flash). Os EFEITOS da receita depois da forma são aplicados a todos os inimigos parados no instante em que o tempo volta |
| `goatify` "Transmutar em Cabra" | efeito | ARCANE t3, C3 | troca o alvo (mob não-chefe) por uma **cabra** (vanilla) por 20 s (guarda o NBT do original); se a cabra morre, o original morre (com loot normal, crédito ao conjurador); ao fim volta. Chefes resistem (Atordoado 1 s + mensagem). Jogadores: "Maldição da Cabra" 6 s (sem conjurar/atacar, berra) |
- Lendas: `i_am_atomic` "Eu Sou Atômico" (self/nova + atomic + all_in), `all_range_atomic` "Eu Sou Atômico — Alcance Total" (colossal),
  `disintegration` "Desintegração", `the_world` "O Mundo" (time_stop + conjured_weapon/arcane_damage — "Toki yo tomare" em PT: "Tempo, pare!"),
  `goat_curse` "Maldição da Cabra".

### 7.3 `legends_e` — Magias de Nível Supremo (Overlord)
| id | tipo | elem / tier / C | o que faz |
|---|---|---|---|
| `super_tier` "Magia de Nível Supremo" | **FORMA ultimate (G)** | ARCANE, C6 | o conjurador fica parado e **enormes círculos mágicos azul-brancos** (várias camadas girando, ~20 blocos, domo de runas) se formam por 8 s (pendente ULTIMATE na Pilha — dá para ser contrariada!); depois os efeitos acontecem com **poder ×3 e área ×3** sobre todos os inimigos a ≤48 do ponto mirado. **Usos por dia**: 1 + Círculo/3 (`MagicApi.tryUseDaily`). Item **`arcane_hourglass` "Ampulheta Arcana"** (consumível, receita cara): conjuração instantânea |
| `fallen_down` "Queda Final" | efeito | LIGHT lendário, C7 | pilar azul-branco descomunal desce do céu: vaporiza (dano altíssimo), cilindro de terreno VITRIFY/ERASE, onda de calor |
| `black_goat` "Iä! Cabra Negra" | efeito | SHADOW lendário, C8 | vento negro: mobs não-chefes com ≤40 de vida na área morrem na hora (sacrifício); para cada 6 sacrificados (mín. 1) nasce um **Filhote Sombrio** (`dark_young`, GeckoLib: árvore-cabra de tentáculos, bocas, cascos, ~4 blocos) que luta pelo conjurador por 60 s (pisões em área telegrafados) |
| `grasp_heart` "Agarrar o Coração" | efeito | SHADOW t4, C5 | mão espectral aperta um coração: mob não-chefe com vida ≤ 60·poder morre; chefes perdem 15% da vida máx. e ficam Atordoados 2 s |
| `death_clock` "O Fim de Toda Vida" | efeito | SHADOW lendário, C9 | um relógio gigante de 12 marcas aparece no céu da área; após 12 s, todo inimigo não-chefe ainda na área morre; chefes perdem 35% da vida |
- Lendas: `fallen_down` "Queda Final", `ia_shub_niggurath` "Iä Shub-Niggurath" (Lovecraft, domínio público), `grasp_heart`, `goal_of_all_life`
  "O Fim de Toda Vida é a Morte", `supreme_meteor` "Queda de Meteoros Suprema" (super_tier + efeitos de meteoro/explosão).

---------------------------------------------------------------------------------------------------
## 8. `ultimates` — Meteoro e novas magias de G

| id | tipo | elem / tier / C | o que faz |
|---|---|---|---|
| `meteor` "Meteoro" | FORMA (normal, stageable) | ARCANE t3, C3 | um meteoro de rocha em brasa (entidade com modelo, rastro de fogo/fumaça, tamanho pelo poder) cai do alto no ponto mirado (marca no chão), impacto aplica os efeitos em raio 4+área, cratera (`Destruction.crater`) e onda de choque |
| `meteor_shower` "Chuva de Meteoros" | FORMA ultimate (G) | ARCANE, C4 | céu escurece/avermelha para quem está perto; 10 + 4·Círculo meteoros em 15 s numa área de raio 16 + 4·área ao redor do ponto mirado, cada um com os efeitos e cratera |
| `transcend` "Transcendência" | FORMA ultimate (G) | ARCANE, C5 | transformação por 30 s: aura do elemento primário, auréola/asas de partículas, **voo**, +50% poder, recargas pela metade, regen de mana ×3; os efeitos da receita pulsam a cada 1 s em inimigos a ≤6 blocos. Ao fim: Exaustão Mágica 5 s |
| `colossus` "Colosso" | FORMA ultimate (G) | ARCANE, C6 | invoca um **gigante espectral** (GeckoLib translúcido, ~12 blocos, cor do elemento, braços e espada) ao redor do conjurador por 30 s: segue o dono, golpeia inimigos (cortes em arco largos, pisão) aplicando os efeitos da receita; bloqueia projéteis vindo de frente |
- Lendas: `meteor` "Meteoro", `meteor_swarm` "Chuva de Meteoros", `awakening` "Despertar" (transcend), `spectral_colossus` "Colosso Espectral".

---------------------------------------------------------------------------------------------------
## 9. Misturas — `fusions_c` e `fusions_d` (2 glifos novos por fusão, todos com visual próprio)

| fusão | glifos (id "nome") — tier 3–4, minCircle 3–5 |
|---|---|
| Magma | `lava_geyser` "Gêiser de Lava" (coluna que arremessa e queima) · `volcano` "Vulcão" (mini-vulcão temporário que cospe bombas de lava telegrafadas) |
| Vapor | `steam_explosion` "Explosão de Vapor" (detona Molhados em explosões de vapor) · `steam_cannon` "Canhão de Vapor" (jato que empurra longe e escalda) |
| Raio | `railgun` "Canhão Elétrico" (feixe perfurante que atravessa tudo em linha, Eletrizado 2) · `ball_lightning` "Bola de Raio" (orbe lento que dispara raios nos próximos) |
| Gelo | `absolute_zero` "Zero Absoluto" (onda que Congela tudo e vira água em gelo — `Destruction.surface FREEZE`) · `ice_coffin` "Caixão de Gelo" (prende num bloco de gelo e estilhaça depois) |
| Natureza | `world_roots` "Raízes do Mundo" (raízes gigantes brotam prendendo e esmagando) · `pollen_storm` "Tempestade de Pólen" (nuvem de sono/veneno) |
| Areia | `sand_coffin` "Caixão de Areia" (envolve e esmaga) · `sand_tsunami` "Tsunami de Areia" (onda de areia que soterra) |
| Sol | `supernova` "Supernova" (explosão solar cegante, Em Chamas 3) · `solar_flare` "Erupção Solar" (feixe que segue a mira) |
| Pureza | `sacred_spring` "Fonte Sagrada" (zona que cura aliados e remove status) · `purge` "Purgar" (tira os status ruins dos aliados e os transforma em dano nos inimigos próximos) |
| Cristal | `crystal_lotus` "Lótus de Cristal" (flor que dispara estilhaços em espiral) · `prism_cannon` "Canhão Prismático" (feixe que se divide em 7 cores) |
| Astral | `black_hole` "Buraco Negro" (puxa tudo, com destruição arranca blocos) · `starfall` "Chuva de Estrelas" (estrelas caem na área) |
| Chama Infernal | `black_flame_pillar` "Pilar de Chamas Negras" · `soul_burn` "Queimar Alma" (dano pelo mana/vida do alvo, não apaga) |
| Abismo | `kraken_grasp` "Garras do Kraken" (tentáculos gigantes do chão) · `abyssal_maw` "Bocarra Abissal" (boca que engole e cospe) |
| Gravidade | `planet_crush` "Esmagamento Planetário" (prende os inimigos numa esfera de rochas arrancadas do terreno, que depois desaba) · `gravity_well` "Poço Gravitacional" |
| Vazio | `dimension_slash` "Corte Dimensional" (corte que ignora armadura e corta terreno) · `void_rift` "Fenda do Vazio" (fenda que suga e cospe em outro lugar) |
| Crepúsculo | `ragnarok` "Ragnarök" (pilares alternados de luz e sombra telegrafados) · `yin_yang` "Equilíbrio Absoluto" (orbe duplo que cura aliados e fere inimigos) |
- `fusions_c`: Magma, Vapor, Raio, Gelo, Natureza, Areia, Sol, Pureza. `fusions_d`: Cristal, Astral, Chama Infernal, Abismo, Gravidade, Vazio,
  Crepúsculo. Cada glifo aplica o status certo (`Statuses.apply`) e combina com as reações. Elemento do glifo = a fusão (`Element.MAGMA`...).
- 2 lendas por módulo usando os glifos novos (ex.: "Zero Absoluto", "Chibaku" homenagem → `planet_crush`).

---------------------------------------------------------------------------------------------------
## 10. `arcane_duel` — Contramágica e mecânicas de Magic: The Gathering

| id | tipo | elem / tier / C | o que faz |
|---|---|---|---|
| `counterspell` (EXISTENTE, no módulo `arcane`) | efeito | ARCANE t3, **C2** (antes lendário C8), custo 30 | além do que já faz (silenciar), **contraria** conjurações pendentes da Pilha no alvo/ponto (`SpellStack.counterNear` com o poder da magia); com Si Mesmo: pulso que contraria tudo a ≤8. Visual de vidro mágico quebrando |
| `mana_leak` "Vazamento de Mana" | efeito | ARCANE t2, C1 | contraria só MINOR (ou MAJOR com Amplificar); em jogadores: tira 30 mana (se não tiverem, contraria) |
| `redirect` "Redirecionar" | efeito | ARCANE t3, C3 | projéteis inimigos perto do alvo trocam de dono e voltam ao conjurador original |
| `fork` "Copiar Magia" | efeito | ARCANE t4, C4 | copia a última magia de glifo conjurada por OUTRO a ≤16 blocos nos últimos 4 s (`SpellStack.recent`) e a conjura como sua (do seu ponto, para a sua mira) |
| `spell_steal` "Roubar Encanto" | efeito | ARCANE t3, C3 | rouba os efeitos benéficos do alvo para você |
| `deathtouch` "Toque Mortal" | aumento | ARCANE t4, C4 | dano deste glifo em mob não-chefe abaixo de 25% de vida mata; em chefes +15% de dano (via SpellDamageEvent) |
| `trample` "Atropelar" | aumento | ARCANE t3, C3 | o dano que sobra ao matar (overkill) passa para o inimigo mais próximo a ≤5 blocos |
| `instant` "Instantâneo" | aumento | ARCANE t3, C3 | tempo de conjuração 0 e não entra na Pilha (não pode ser contrariada), custo +50% |
| `hexproof` "Resguardo" | efeito | ARCANE t3, C3 | aliado/você imune a status negativos e a contramágica por 8 s; magias inimigas que o atingem custam (jogador) +20 mana ao atacante |
| `indestructible` "Indestrutível" | efeito | LIGHT t4, C5 | aliado/você não cai abaixo de 1 de vida por 4 s (aura dourada) |
| `exile` "Exilar" | efeito | ARCANE t4, C5 | mob não-chefe com ≤50% de vida é banido (portal, some sem loot, conta como abate); chefes: Exposto II 6 s |
| `wrath` "Ira Divina" | efeito | LIGHT lendário, C8 | destrói TODAS as criaturas não-jogador e não-domadas na área (inclusive as suas invocações); chefes perdem 25% |
| `time_walk` "Passo no Tempo" | efeito | ARCANE lendário, C7 | zera todas as suas recargas e devolve 30% do mana; recarga própria 120 s |
| `in_response` "Em Resposta" | GATILHO (reativo, como `on_hurt`) | ARCANE t2, C2 | o slot com este gatilho é **preparado**: quando uma conjuração pendente HOSTIL aparece a ≤20 blocos (PendingCastEvent), a magia do slot sai sozinha mirando o conjurador/ponto da pendente (paga mana, respeita recarga) |
- Lendas: `perfect_counter` "Contramágica Perfeita", `wrath_of_god` "Ira de Deus", `time_walk` "Passo no Tempo".
- O módulo também grava contadores `counter.success` / `counter.boss` (para diário/missões) ao ouvir `SpellCounteredEvent`.

---------------------------------------------------------------------------------------------------
## 11. `power_up` — alcance maior e destruição nas magias fortes existentes

- **Domínio** (`feature/domain`): raio = 12 + 2,5·Círculo + 6·área (aberto ×1,6; absoluto ×1,75 como antes); duração +25%; chama
  `Domains.opened/ticked/closed` e os handlers de leis registradas por outros módulos (leis desconhecidas pelo domain = do registro);
  `Domains.sureHitOn`. **Barreira com integridade e lados (§11.1)** — registra o `Domains.BarrierHandler`. Nova lei **`law_ruin` "Lei: Ruína"** (ARCANE, C6): o domínio destrói o terreno dentro aos poucos (rachaduras,
  blocos arrancados e lançados, `Destruction` CRUSH/ERASE) e ao fechar deixa uma cratera. Choque de domínios continua.
### 11.1 Barreira do Domínio — "quebra por fora, não por dentro; invertida é o contrário; o maior só ataca por fora"
Regra pedida pelo jogador (inspirada em Jujutsu Kaisen). Substitui o choque instantâneo antigo por uma disputa visível.
- **Integridade**: todo domínio FECHADO (com barreira) tem integridade = 60 + 20·Círculo do dono + 0,5·mana gasto (+25% com `law_absolute`).
  Barra estilo chefe "Barreira — <nome do domínio>" (cor do elemento; "(Invertida)" quando for) para quem está dentro ou a ≤ r+16.
  Ao chegar a 0: **despedaça** (efeito atual de shatter, som de vidro gigante, tremor) e o domínio acaba; o dono fica com Exaustão Mágica.
- **Barreira NORMAL: quebra POR FORA, não por dentro.** Ataques vindos de FORA que acertam a parede tiram integridade: projéteis (vanilla e
  de magia) que batem na parede vindo de fora, explosões a ≤3 blocos da parede do lado de fora, efeitos de magia que caem na parede, e golpes
  corpo a corpo de quem está fora a ≤4 blocos mirando a parede (pacote C2S do cliente ao bater no ar mirando o domo). Quem está PRESO DENTRO
  não consegue quebrar: projéteis de dentro se desfazem na parede com faíscas e som abafado, golpes não fazem nada (mensagem "A barreira não
  cede por dentro..."). Aliados de fora podem resgatar quebrando a barreira.
- **Barreira INVERTIDA** (aumento `invert` "Inverter" colocado logo depois da forma `domain_expansion`): o contrário — **não quebra por fora,
  quebra por dentro**. Quem está fora não consegue entrar nem quebrar (projéteis de fora ricocheteiam), mas os presos podem quebrá-la por dentro
  (dano dos presos na parede tira integridade). Visual: cores invertidas/negativas na parede, runas giradas, letreiro "Barreira Invertida".
  Útil contra domínios maiores (ver abaixo). Custo +10%.
- **Domínio maior só ataca por fora**: quando um domínio de OUTRO dono envolve totalmente um domínio fechado menor, o acerto garantido e os
  efeitos do MAIOR não atravessam a barreira do menor — eles **atacam a barreira por fora** (cada pulso do maior tira integridade do menor
  = poder do pulso × 4) e atingem só quem está FORA do menor. Quem está dentro do menor fica protegido enquanto ela aguentar, e dentro do
  menor vale o acerto garantido do menor. Se a barreira do menor for **invertida**, os ataques de fora não a quebram: o maior só consegue
  atacar o lado de fora. `Domains.sureHitOn` e `Domains.shielded` seguem essa regra (o domínio mais interno manda).
- **Domínio aberto** (sem barreira, `law_open_barrier`/Santuário Maligno): não tem integridade (não tem o que quebrar, não prende ninguém),
  é maior, e contra um domínio fechado dentro dele faz o mesmo: ataca a barreira por fora.
- **Sobreposição parcial** de dois domínios fechados (nenhum contém o outro): disputa de refinamento — a cada 10 ticks cada um tira da
  integridade do outro (2 + pontuação antiga/10); o que zerar primeiro despedaça (com as barras visíveis, é um cabo de guerra).
- **Domínio Simples** continua anulando o acerto garantido de quem está dentro dele.
- `/mahou domain info` (op) mostra integridade, lado, se é invertida, quem envolve quem.

- **Cataclismo**: raio ×1,75 e cratera (`Destruction.crater`) no impacto principal.
- **legends_a**: `crater` (Explosão!) usa `Destruction.crater` (raio maior: ~20–35), `CraterQueue` vira só uma camada sobre a API do núcleo
  (ou é removida), respeitando a regra de destruição.
- **legends_b**: `converge` (Vazio Roxo) apaga um túnel de terreno no caminho (`Destruction.line` ERASE, largura = tamanho da esfera);
  `annihilate` usa ERASE do núcleo; `slash_storm` deixa cortes no terreno (`line` fino); `sky_lances` derrete/vitrifica a superfície.
- **fire_water** `explosion`: com Amplificar ≥2 ou Colossal abre cratera pequena (BREAK).
- Tudo respeitando `Destruction.allowed/protectedAt` (cidades e a dimensão das provações ficam intactas).

---------------------------------------------------------------------------------------------------
## 12. UI — `ui_hud` (fase B) e `art` (fase A)

- `ui_hud`: ícones de status (`ClientStatuses`) com acúmulos e tempo no **Olho Analítico** e nas **barras flutuantes** (e chama
  `ClientStatuses.setWorldIconsHandledExternally(true)`); no Olho Analítico, para NPCs com facção (`Reputation.factionOf` + tags no cliente),
  mostra "Facção — Patamar" colorido; nome do lugar atual (`ClientPlaces.current()`) discreto sob o cartão; aba Status: linha de reputação
  (melhor facção) e título escolhido; ícones de status do jogador ao lado da barra de mana (com acúmulos).
- `art`: `tools/gen/icons.py` — pictogramas para TODOS os glifos novos do §13 (mesmo estilo dos 317 atuais); texturas 18×18
  `textures/mob_effect/{burning,chilled,shocked,bleeding,petrified}.png` (e conferir `soaked/frozen`); emblemas 32×32
  `textures/gui/faction/{guild,kingdom,academy,church,underworld,wildfolk,horde}.png`; ícone de aba `textures/gui/tab/reputation.png`;
  ícones 16×16 da Pilha `textures/gui/stack/{minor,major,ultimate,countered}.png`.

---------------------------------------------------------------------------------------------------
## 13. Catálogo de ids novos (v0.8)

- **Efeitos (núcleo A1)**: `burning`, `chilled`, `shocked`, `bleeding`, `petrified`. Regra de jogo `mahouSpellDestruction`.
- **A3**: dados `factions/{guild,kingdom,academy,church,underworld,wildfolk,horde}.json`; tags `entity_type/faction/<id>`.
- **cities**: blocos `city_heart`; entidades `city_guard`, `citizen`, `city_merchant`, `city_lord`; efeito `wanted`. Estruturas (`city_build`):
  `guild_city`, `capital_city`; tag `#isekai_mahou:city`; templates `structure/city/**`.
- **guild**: bloco `faction_board`; (entidade `adventurer` existente ganha classe `cleric`).
- **legends_c**: `infinity`, `lapse_blue`, `reversal_red`, `dismantle`, `cleave`, `flame_arrow`, `law_shrine`, `law_infinite_void`; entidade
  `malevolent_shrine` (modelo do santuário) + as que precisar.
- **legends_d**: `atomic`, `disintegration`, `time_stop` (forma G), `goatify`; efeito `time_stopped`, `goat_curse`.
- **legends_e**: `super_tier` (forma G), `fallen_down`, `black_goat`, `grasp_heart`, `death_clock`; item `arcane_hourglass`; entidade `dark_young`.
- **ultimates**: `meteor` (forma), `meteor_shower`, `transcend`, `colossus` (formas G); entidades `meteor`, `spectral_colossus`; efeito `transcendence`.
- **fusions_c**: `lava_geyser`, `volcano`, `steam_explosion`, `steam_cannon`, `railgun`, `ball_lightning`, `absolute_zero`, `ice_coffin`,
  `world_roots`, `pollen_storm`, `sand_coffin`, `sand_tsunami`, `supernova`, `solar_flare`, `sacred_spring`, `purge`.
- **fusions_d**: `crystal_lotus`, `prism_cannon`, `black_hole`, `starfall`, `black_flame_pillar`, `soul_burn`, `kraken_grasp`, `abyssal_maw`,
  `planet_crush`, `gravity_well`, `dimension_slash`, `void_rift`, `ragnarok`, `yin_yang`.
- **arcane_duel**: `mana_leak`, `redirect`, `fork`, `spell_steal`, `deathtouch`, `trample`, `instant`, `hexproof`, `indestructible`, `exile`,
  `wrath`, `time_walk`, `in_response` (+ `counterspell` existente revisto).
- **power_up**: lei `law_ruin`.
- Prefixe ids de entidades/blocos/itens auxiliares com algo único do módulo para não colidir.

---------------------------------------------------------------------------------------------------
## 14. Donos de arquivos

| trabalho | pode editar |
|---|---|
| A1 mundo | `core/status/**`, `core/api/{Statuses,Destruction,Places}.java`, `core/event/{ElementReactionEvent,PlaceEvent}.java`, `core/ElementReactions.java`, `core/CoreRegistries.java` (efeitos novos), `core/effect/**`, `client/status/**`, `src/main/lang/core_status/`, `src/main/lang/core/` (só renomear "Encharcado"→"Molhado" e chaves existentes de efeitos), `docs/v08/API_world.md` |
| A2 pilha | `core/stack/**`, `core/api/{SpellStack,Domains}.java`, `core/event/{SpellDamageEvent,PendingCastEvent,SpellCounteredEvent}.java`, `core/boss/Telegraph.java`, `core/boss/BossKit.java`, `core/MagicApi.java`, `glyph/**`, `client/stack/**`, `src/main/lang/core_stack/`, `docs/v08/API_stack.md` |
| A3 reputação | `core/reputation/**`, `core/api/Reputation.java`, `core/event/ReputationChangedEvent.java`, `client/reputation/**`, `data/isekai_mahou/factions/**`, tags `data/isekai_mahou/tags/entity_type/faction/**`, `src/main/lang/core_reputation/`, `core/MagicData.java` SÓ se for indispensável (contador negativo), `docs/v08/API_reputation.md` |
| city_build | `feature/cities/world/**`, `tools/gen/city_build.py`, `data/isekai_mahou/structure/city/**`, `data/isekai_mahou/worldgen/{structure,structure_set}/{guild_city,capital_city}.json`, `data/isekai_mahou/tags/worldgen/biome/has_structure/{guild_city,capital_city}.json`, `data/isekai_mahou/tags/worldgen/structure/city.json`, `src/main/lang/cities_world/` |
| art | `tools/gen/icons.py` (+ `tools/pixelart.py` só adicionar), `assets/isekai_mahou/textures/gui/{glyph,faction,stack}/**`, `textures/gui/tab/reputation.png`, `textures/mob_effect/{burning,chilled,shocked,bleeding,petrified}.png` |
| cities | `feature/cities/**` EXCETO `world/**`, `src/main/lang/cities/`, recursos/assets com nomes do módulo, `tools/gen/cities.py` |
| guild | `feature/guild/**`, `data/isekai_mahou/{guild_quests,guild_shop,journal}/**`, `src/main/lang/{guild,journal}/`, `tools/gen/guild.py` |
| legends_c | `feature/legends_c/**` + a ENTRADA `malevolent_shrine` em `feature/legends_b/LegendRecipes.java` |
| legends_d / legends_e / ultimates / fusions_c / fusions_d | o próprio módulo |
| arcane_duel | `feature/arcane_duel/**` + `feature/arcane/glyph/ManaEffects.java` (só `CounterspellEffect`) e traduções do counterspell em `src/main/lang/arcane/` |
| power_up | `feature/domain/**`, `feature/legends_a/**`, `feature/legends_b/**` (exceto a entrada `malevolent_shrine` das receitas), `feature/fire_water/glyph/**` (só o efeito explosion), langs desses módulos |
| ui_hud | `feature/ui_hud/**`, `src/main/lang/ui_hud/` |

Cada módulo: assets/recursos com nomes próprios, traduções `src/main/lang/<modulo>/{pt_br,en_us}.json`, script `tools/gen/<modulo>.py`.
O núcleo (`core/`, `glyph/`, `network/`, `client/`, `module/`) é da fase A; na fase B, se faltar algo, contorne no módulo e reporte em `followups`.
