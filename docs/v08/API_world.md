# API v0.8 — Mundo: condições de status, reações, destruição de terreno, lugares (trilha A1)

Complementa `docs/V08_DESIGN.md` §2. Tudo é **aditivo**: código da v0.7 compila e funciona igual (o efeito `soaked` mudou só de
nome visível: "Molhado"/"Wet"; `frozen` agora prende de verdade — ver §1.5).

| Arquivo | Para quê |
|---|---|
| `core/api/Statuses.java` | Condições de status (aplicar, ler, limpar) — **use sempre esta API** |
| `core/ElementReactions.java` | Reações elementais, resistências/fraquezas (`setMultiplier` continua igual) |
| `core/event/ElementReactionEvent.java` | Evento "uma reação aconteceu" |
| `core/api/Destruction.java` | Destruir o terreno (crateras, cortes, colunas, superfície) respeitando a regra de jogo |
| `core/api/Places.java` + `core/event/PlaceEvent.java` | Lugares nomeados (cidades), entrada/saída, zonas seguras |
| `client/status/ClientStatuses.java` | (cliente) condições de qualquer entidade + ícones |
| `client/status/ClientPlaces.java` | (cliente) lugar onde o jogador está |

Implementação: `core/status/**` (servidor) e `client/status/**` (cliente). Traduções: `src/main/lang/core_status/`.
Regras gerais: chame as APIs de servidor **no servidor** (`!level.isClientSide`); as de `client/` só em classes de cliente.

---------------------------------------------------------------------------------------------------
## 1. `Statuses` — condições de status

```java
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.api.Statuses.Kind;

Statuses.apply(LivingEntity alvo, Kind tipo, int acumulos, int ticks, @Nullable LivingEntity quemAplicou, float poder)
Statuses.applyUpTo(alvo, tipo, acumulos, ticks, quemAplicou, poder, int limite) // não passa de "limite"; no limite só renova
int     Statuses.stacks(LivingEntity e, Kind tipo)      // 0 = não tem (Em Chamas: fogo vanilla conta 1)
boolean Statuses.has(LivingEntity e, Kind tipo)
void    Statuses.clear(LivingEntity e, Kind tipo)       // Em Chamas também apaga o fogo vanilla
int     Statuses.ticksLeft(LivingEntity e, Kind tipo)   // 0 = não tem, -1 = infinita
float   Statuses.power(LivingEntity e, Kind tipo)       // poder guardado de quem aplicou (1 se nenhum)
boolean Statuses.incapacitated(LivingEntity e)          // Congelado ou Petrificado (pedra): não anda/ataca/conjura
Holder<MobEffect> Statuses.effect(Kind tipo)            // o MobEffect do núcleo
boolean Statuses.isBossLike(LivingEntity e)             // tag c:bosses, chefe de Provação ou vida máx. ≥ 150 (jogador nunca;
                                                        // mesma regra de BossKit.isBossLike)
int     Statuses.DEFAULT_TICKS = 120                    // 6 s
```
`Kind`: `id()` (id do efeito, ex. `"soaked"`), `maxStacks()`, `color()`, `elemental()`, `displayName()`,
`displayName(int acumulos)` (Petrificação: "Enrijecendo"/"Petrificado"), `effect()`, `Kind.of(Holder<MobEffect>)`,
`Kind.byId(String)`, `Kind.byOrdinal(int)`.
Suportes de armadura (e tudo com `isAffectedByPotions() == false`) nunca recebem condições.

### 1.1 Tipos e regras
| Kind | efeito (`isekai_mahou:`) | acúmulos | regra |
|---|---|---|---|
| `WET` | `soaked` "Molhado" | 1 | Raio: reação ELETROCUTAR (×2). Gelo: CONGELAR. Fogo: −50% e só evapora a água. Apaga Em Chamas. Chuva/água dão Molhado (5 s, renovado a cada 1 s) |
| `BURNING` | `burning` "Em Chamas" | 1–5 | dano mágico a cada 1 s = (0,5 + 0,5·acúmulos) × poder (crédito de quem aplicou, sem recuo); +8%/acúmulo no dano de Fogo recebido; −30% de cura; chamas desenhadas no cliente. Água/chuva/Molhado apagam. Imunes a fogo não pegam. Lava/fogo dão Em Chamas 1 |
| `CHILLED` | `chilled` "Gelado" | 1–5 | −12% velocidade, −8% vel. de ataque, −20% regeneração de mana (jogador), +15% tempo de conjuração por acúmulo. Molhado: acumula em dobro. Ao chegar a 5 → **Congelado**. Neve em pó dá Gelado (até 3) |
| `FROZEN` | `frozen` "Congelado" | 1 | preso no gelo 2–4 s: velocidade/pulo/ataque zerados, golpes corpo a corpo cancelados, conjuração cancelada/interrompida; ninguém usa itens (arco/besta/tridente não carregam, comida não termina; jogador nem começa a usar) e projéteis VANILLA de um dono preso não saem (flecha, bola de fogo de Ghast/Blaze, poção de Bruxa...; chefes e projéteis de mods ficam de fora). Aplicar de novo enquanto congelado NÃO renova. Ao sair: **6 s de imunidade** (aplicar de novo vira Gelado +2) |
| `SHOCKED` | `shocked` "Eletrizado" | 1–3 | Raio +15%/acúmulo; a cada 1 s, 12%·acúmulos de chance de Atordoar 0,5 s; jogador perde 2 mana/s/acúmulo; se também Molhado, faísca em até 3 Molhados a ≤4 blocos |
| `BLEEDING` | `bleeding` "Sangrando" | 1–10 | 0,4·acúmulos × poder por segundo, em dobro se o alvo se mexeu no último segundo |
| `PETRIFIED` | `petrified` "Petrificação" | 1–3 | 1–2 "Enrijecendo": −20%/acúmulo de velocidade. 3 "Petrificado": pedra por 2 s (preso como no Congelado, Atordoado, +50% armadura, sem recuo) e depois zera; ao sair da pedra, **4 s** sem acumular Petrificação de novo |
| `CORRUPTED` `EXPOSED` `STUNNED` `ROOTED` `SILENCED` | os antigos | 5/4/1/1/1 | sem mudança de regra (Exposto: +25% de dano de magia por nível) |

- **Duração**: cada aplicação reinicia a duração para `max(restante, ticks)` (nunca encurta). `ticks ≤ 0` = 6 s.
- **Chefes** (`isBossLike`): metade dos acúmulos (arredonda para cima) e da duração; Congelado = 1 s + Lentidão II 3 s e depois
  Gelado 3; Petrificado = 1 s + Lentidão.
- **Jogadores**: config `statusOnPlayers=false` desliga as condições elementais (Molhado...Petrificação) em jogadores.
- **Poder** (`poder`): multiplica o dano contínuo de Em Chamas e Sangrando (limitado a 0,25–6). Use `ctx.powerMult()` (×
  `ctx.casterSpellPower()` se quiser o atributo do conjurador). Se dois aplicam, vale o MAIS forte enquanto a condição durar.
- Efeitos aplicados direto com `addEffect(new MobEffectInstance(CoreRegistries.FROZEN, ...))` também respeitam a imunidade a
  Congelado (não-chefes), a config de jogadores e sincronizam os ícones; chefes usam só a tenacidade do próprio `addEffect`.
  Prefira `Statuses.apply` (soma acúmulos, regras de chefe do §1.1, guarda o crédito do dano contínuo).

### 1.2 Aplicação automática pelo elemento (no dano de magia, `SpellContext.damage`)
FOGO/MAGMA/SOL/CHAMA INFERNAL → Em Chamas +1 · ÁGUA/ABISMO/VAPOR → Molhado · GELO → Gelado +1 (+2 se Molhado) · RAIO →
Eletrizado +1 · CRISTAL → Petrificação +1 · CREPÚSCULO → Corrompido 3 s (o automático não passa de II). Terra, Vento, Luz,
Sombra, Arcano e as outras fusões:
nada automático — **o glifo aplica o que fizer sentido** com `Statuses.apply`.

### 1.3 Exemplos
```java
// Corte que sangra (Desmantelar, Clivar...)
ctx.damage(alvo, 8F, stats, null);
if (alvo instanceof LivingEntity le) Statuses.apply(le, Kind.BLEEDING, 3, stats.duration(120), ctx.caster(), ctx.powerMult());

// Supernova: Em Chamas 3 em todos
for (LivingEntity e : ctx.enemiesAround(centro, raio)) Statuses.apply(e, Kind.BURNING, 3, 160, ctx.caster(), ctx.powerMult());

// Zero Absoluto: congela direto (respeita imunidade e chefes)
Statuses.apply(e, Kind.FROZEN, 1, 60, ctx.caster(), ctx.powerMult());

// Purgar: tira todas as condições ruins de um aliado
for (Kind k : Kind.values()) Statuses.clear(aliado, k);

// Sacred Spring/Hexproof: "está preso?"
if (Statuses.incapacitated(e)) ...
```

### 1.4 Visual (automático, nada a fazer)
O servidor manda a lista de condições de cada entidade para quem a rastreia (pacote `core_status_list`, também ao começar a
rastrear e para o próprio jogador). O cliente desenha: bloco de gelo translúcido em volta do Congelado, casca de pedra no
Petrificado, chamas no Em Chamas, tinta no modelo (azulada no Gelado, cinza no Enrijecendo, brasa, faíscas, roxo) — para
renderizadores vanilla E GeckoLib —, partículas (gotas, brasas, neve, faíscas, sangue, poeira, estrelinhas no Atordoado) e uma
fileira de ícones com acúmulos sobre a cabeça dos mobs a ≤24 blocos (config cliente `showStatusIcons`). Os efeitos NÃO
mostram as partículas genéricas do vanilla.

### 1.5 Mudanças de comportamento em relação à v0.7
- `frozen`: modificadores agora −100% (velocidade, voo, pulo, vel. de ataque) e bloqueia ataque corpo a corpo e conjuração.
- `soaked`: não solta mais partículas pelo servidor (o cliente desenha as gotas); apaga Em Chamas.
- Reações antigas mudaram de número: Estilhaçar ×1,75 (era ×1,5), Congelar 3 s (era 4 s).

---------------------------------------------------------------------------------------------------
## 2. Reações elementais (`ElementReactions`)

Automáticas no dano de magia (`SpellContext.damage` → `ElementReactions.onSpellHit`; NÃO mude a ordem: dano base →
`onSpellHit` → `SpellDamageEvent.Pre` → dano). No máximo UMA reação de condição por golpe + as de sequência.

| `ElementReactions.Reaction` | id (contador `reaction.<id>`) | condição | efeito |
|---|---|---|---|
| `ELECTROCUTE` "ELETROCUTAR!" | `electrocute` | Molhado + Raio | dano ×2, Atordoado 2 s, consome Molhado, arco (50% do dano) em até 6 Molhados a ≤4 blocos |
| `FREEZE` "CONGELAR!" | `freeze` | Molhado + Gelo (alvo não imune) | Congelado 3 s, consome Molhado |
| `VAPOR` "VAPOR!" | `vapor` | Em Chamas + Água/Abismo/Vapor/Natureza/Pureza | apaga; Cegueira 2,5 s em quem está a ≤2,5 blocos |
| `WILDFIRE` "INCÊNDIO!" | `wildfire` | Em Chamas + Vento/Areia/Astral/Vazio | Em Chamas (até 3) nos inimigos a ≤3 blocos |
| `MELT` "DERRETER!" | `melt` | Em Chamas + Gelo, ou Gelado/Congelado + Fogo | dano ×1,6, remove os dois |
| `OVERLOAD` "SOBRECARGA!" | `overload` | Em Chamas + Raio | +3·poder no alvo e em quem está a ≤2,5 blocos (sem quebrar blocos), empurra; remove Em Chamas |
| `SUPERCONDUCT` "SUPERCONDUTOR!" | `superconduct` | Gelado + Raio | Exposto II por 5 s |
| `SHATTER` "ESTILHAÇAR!" | `shatter` | Congelado/Petrificado + Terra (fusões de Terra; Magma em Congelado DERRETE) ou golpe corpo a corpo | dano ×1,75, quebra o gelo/pedra |
| `LACERATE` "LACERAR!" | `lacerate` | Sangrando + Vento | +3 Sangrando |
| `RUPTURE` "RUPTURA!" | `rupture` | Luz e Sombra em sequência (≤3 s) | dano ×1,3 |
| `SAND_EYES` "AREIA NOS OLHOS!" | `sand_eyes` | Terra e Vento em sequência (≤3 s) | Cegueira 3 s |

Também: Fogo em alvo Molhado = −50% (sem texto). Em Chamas +8%/acúmulo no Fogo e Eletrizado +15%/acúmulo no Raio.
Classes de elemento usadas: `ElementReactions.isFire/isWater/isIce/isLightning/isWind/isEarth(Element)` (públicos).

Cada reação: partículas + som + **texto flutuante** colorido para jogadores a ≤32 blocos (config cliente `showReactionText`),
`ElementReactionEvent` e, se quem causou é jogador, `MagicApi.addCounter(p, "reaction.<id>", 1)` + `"reaction.any"`.
A MESMA reação no MESMO alvo é anunciada (efeitos, texto, evento, contadores) no máx. 1 vez a cada 10 ticks — feixes que
acertam a cada 2–4 ticks não viram spam nem "fazenda" de contador; a mecânica (dano, condições) vale em todo golpe.

```java
// Ouvir reações (ex.: missão "provoque 10 reações", passiva "ao Eletrocutar, recupera mana")
NeoForge.EVENT_BUS.addListener((ElementReactionEvent e) -> {
    if (e.getReaction() == ElementReactions.Reaction.ELECTROCUTE && e.getCauser() instanceof ServerPlayer p) ...
    // e.getTarget(), e.getContext() (null em golpe corpo a corpo), e.getElement() (null em golpe físico)
});
```
API antiga intacta: `setMultiplier`, `multiplier`, `mark`, `lastElement`, `lastCaster`, `onSpellHit` (quem causa dano de magia
sem `SpellContext.damage`, como o `FbUtil` da fusions_b, pode continuar chamando `onSpellHit` + `mark`).

---------------------------------------------------------------------------------------------------
## 3. `Destruction` — destruir o terreno

```java
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Destruction.Mode;   // ERASE, BREAK, SCORCH, VITRIFY, FREEZE, CRUSH

boolean Destruction.allowed(ServerLevel l, @Nullable Entity conjurador)          // pode destruir agora?
boolean Destruction.protectedAt(ServerLevel l, BlockPos pos, @Nullable Entity conjurador)
void    Destruction.addProtection(BiPredicate<ServerLevel, BlockPos> p)          // área protegida extra (no init do módulo)
int Destruction.sphere  (l, Vec3 centro, float raio, conjurador, Mode)           // raio máx. 64
int Destruction.crater  (l, Vec3 centro, float raio, conjurador)                 // raio 1–48
int Destruction.cylinder(l, Vec3 base, float raio, float altura, conjurador, Mode) // altura <0 desce; limitada ao mundo
int Destruction.line    (l, Vec3 de, Vec3 ate, float largura, float altura, conjurador, Mode)  // até 256 blocos
int Destruction.surface (l, Vec3 centro, float raio, conjurador, Mode)           // só a camada de cima
int Destruction.queued()                                                          // blocos na fila
@Nullable GameRules.Key<GameRules.BooleanValue> Destruction.rule()               // mahouSpellDestruction
```
- Retorno = blocos **enfileirados** (0 = nada: destruição desligada, sem permissão, fila cheia, nada sólido na área...). Só
  entram blocos que existem agora (ar e chunks não carregados ficam de fora e não gastam a fila). Os blocos mudam aos poucos
  (fila global: `destructionBlocksPerTick`, padrão 3000/tick, do centro para fora; fila máx. 250 000). Não precisa checar
  `allowed` antes — as formas já checam; use `allowed` só para decidir o VISUAL (ex.: mostrar rachaduras ou não).
- Chame na **thread do servidor** (efeitos de magia, ticks de entidade). Fora dela (geração de mundo) o pedido é ignorado.
- Também respeita a borda do mundo.
- `conjurador`: passe `ctx.caster()` (jogador → respeita proteção de spawn/aventura; mob → regra `mobGriefing`).
- **Nunca** destrói: bedrock/inquebráveis, `#minecraft:wither_immune`, `#minecraft:dragon_immune` (obsidiana...), blocos com
  inventário/dados (a menos que `destructionBlockEntities=true`), a dimensão `trial_realm`, lugares seguros (cidades), áreas de
  `addProtection`, chunks não carregados. Líquidos só mudam no modo FREEZE.
- Modos: **ERASE** some sem drops · **BREAK** 10% dos blocos deixam o drop normal · **SCORCH** pedra→pedra-negra/basalto/magma,
  terra→terra grossa/magma, areia→vidro às vezes, plantas/madeira/neve somem, fogo em ~12% da superfície · **VITRIFY**
  areia→vidro, pedra→obsidiana chorona/vidro tingido, terra→vidro marrom/terracota · **FREEZE** água→gelo/gelo compactado,
  lava→obsidiana, fogo apaga, chão exposto ganha neve · **CRUSH** vira pedregulho/cascalho e a camada de cima afunda 1.
- `crater`: tigela (raio r, profundidade r·0,6) a partir do chão abaixo do centro, morros/árvores acima também somem, borda
  queimada (SCORCH) com fogo em 15% dela, escombros voando, estrondo e tremor de câmera (`BossKit.shake`).
- **Escombros**: ~1 a cada 40 blocos removidos vira a entidade `isekai_mahou:destruction_debris` (bloco girando que quica e some
  em 2,5 s; máx. 160 vivos). NUNCA coloca bloco nem vira item. Poeira/fumaça e som de desmoronamento saem em lotes.
- Vizinhos são acomodados no fim (água volta a escorrer, flores sem apoio somem sem drop, cascalho cai).
- Regra de jogo **`mahouSpellDestruction`** (padrão `true`): `/gamerule mahouSpellDestruction false` ou `/mahou destruction off`.
  Também precisa da config `spellBlockDestruction=true`.

```java
// Meteoro: cratera no impacto
Destruction.crater(level, impacto, 4F + stats.radius(2F), ctx.caster());
// Desmantelar: corte fino e longo no terreno
Destruction.line(level, origem, origem.add(ctx.look().scale(24)), 0.6F, 5F, ctx.caster(), Mode.ERASE);
// Desintegração: coluna apagada até o céu
Destruction.cylinder(level, chao, 5F, level.getMaxBuildHeight() - (float) chao.y, ctx.caster(), Mode.ERASE);
// Zero Absoluto: congela a superfície
Destruction.surface(level, centro, 14F, ctx.caster(), Mode.FREEZE);
// Domínio "Ruína": arena do seu módulo protegida
Destruction.addProtection((lvl, pos) -> MinhaArena.contains(lvl, pos));
```

---------------------------------------------------------------------------------------------------
## 4. `Places` — lugares nomeados (cidades)

```java
import com.renanpontes.isekaimahou.core.api.Places;

record Places.Place(String id, Component name, String type, String faction, ResourceKey<Level> dim, BoundingBox box,
                    BlockPos center, boolean safeZone, int color)       // .contains(pos), .volume()
Places.Place Places.register(ServerLevel l, Places.Place p)   // idempotente pelo id (atualiza); devolve o guardado
void   Places.remove(ServerLevel l, String id)
@Nullable Places.Place Places.get(ServerLevel l, String id)   // procura em todos os níveis
@Nullable Places.Place Places.at(ServerLevel l, BlockPos pos) // o MENOR lugar que contém a posição
@Nullable Places.Place Places.nearest(ServerLevel l, BlockPos pos, @Nullable String type)
List<Places.Place> Places.all(ServerLevel l)
boolean Places.isSafe(ServerLevel l, BlockPos pos)           // zona segura + config cityProtection
```
- Guardado por nível (`data/isekai_mahou_places.dat`), sobrevive ao reinício. Tipos usados: `"capital"`, `"city"`, `"village"`.
- Leituras (`at`, `nearest`, `all`, `get`, `isSafe`) funcionam em qualquer thread. `register`/`remove` chamados fora da thread
  do servidor (ex.: durante a geração de uma estrutura) são gravados no próximo tick, na thread principal.
- Os monstros que nascem JUNTO com a geração do chunk só são barrados se o lugar já estiver registrado; quem registra no 1º
  tick de um bloco (o `city_heart`) deve limpar os monstros da área ao registrar.
- `faction` = id da facção (`kingdom`...), mostrado como `faction.isekai_mahou.<faction>` no letreiro; `""` = sem subtítulo.
- **Entrada/saída** (checado a cada 20 ticks por jogador): `PlaceEvent.Enter` (`getPlayer()`, `getPlace()`, `isFirstVisit()`) e
  `PlaceEvent.Leave` no `NeoForge.EVENT_BUS`; letreiro grande no cliente (nome na cor do lugar + facção; "✦ Novo lugar
  descoberto ✦" na 1ª vez; "Saindo de X" ao sair); na 1ª visita de cada lugar: flag `visit.place.<id>` e contador
  `visit.<type>` (+1) — ex. `visit.city`, `visit.capital`.
- **Zona segura** (`safeZone` e config `cityProtection`): `Destruction` não mexe nos blocos, explosões não quebram blocos lá
  dentro (o dano continua), monstros (`MobCategory.MONSTER`) não nascem naturalmente nem na geração de chunks, nem patrulhas.

```java
// city_heart (módulo cities), no 1º tick do bloco:
BoundingBox caixa = new BoundingBox(pos.getX() - r, pos.getY() - 24, pos.getZ() - r, pos.getX() + r, pos.getY() + 48, pos.getZ() + r);
Places.register(sl, new Places.Place("city_" + pos.getX() + "_" + pos.getZ(), Component.literal("Valdória"),
        capital ? "capital" : "city", "kingdom", sl.dimension(), caixa, pos, MahouConfig.CITY_PROTECTION.get(), 0x3F6FD8));

// "matou monstro dentro da cidade"
Places.Place p = Places.at(sl, mob.blockPosition());
if (p != null && (p.type().equals("city") || p.type().equals("capital"))) ...

NeoForge.EVENT_BUS.addListener((PlaceEvent.Enter e) -> { if (e.isFirstVisit()) ... });
```

---------------------------------------------------------------------------------------------------
## 5. Cliente

```java
import com.renanpontes.isekaimahou.client.status.ClientStatuses;
import com.renanpontes.isekaimahou.client.status.ClientPlaces;

List<ClientStatuses.Entry> ClientStatuses.get(Entity e)       // Entry(Kind kind, int stacks, int ticksLeft) — ticksLeft -1 = infinita
int  ClientStatuses.stacks(Entity e, Kind k)
ResourceLocation ClientStatuses.icon(Kind k)                  // isekai_mahou:textures/mob_effect/<id>.png (18×18)
void ClientStatuses.drawIcon(GuiGraphics g, Kind k, int stacks, int x, int y, int size)   // com o número; reserva sem textura
void ClientStatuses.setWorldIconsHandledExternally(boolean)   // true = o núcleo para de desenhar os ícones sobre os mobs
@Nullable Places.Place ClientPlaces.current()                 // lugar do jogador local (null fora)
```
- Funciona para o próprio jogador (`ClientStatuses.get(mc.player)`) — ícones ao lado da barra de mana no `ui_hud`.
- Nomes: `kind.displayName(acumulos)` (Petrificação com 1–2 = "Enrijecendo" `status.isekai_mahou.petrified.stiff`, com 3 =
  "Petrificado" `status.isekai_mahou.petrified.full`; os outros = `kind.displayName()`). Descrições:
  `effect.isekai_mahou.<id>.description`.
- Dentro d'água o ícone/gotas de Molhado não aparecem sobre a criatura (o lago não fica cheio de ícones).
- Configs de cliente: `showStatusIcons`, `showReactionText`, `reducedParticles` (partículas das condições pela metade).

---------------------------------------------------------------------------------------------------
## 6. Comandos (cheats)
- `/mahou destruction [status|on|off]` — regra `mahouSpellDestruction` (e mostra config, `mobGriefing`, fila).
- `/mahou destruction test <sphere|crater|cylinder|line|surface> <raio> [modo]` — no bloco que você olha.
- `/mahou place list | tp <id> | remove <id>`.
- `/mahou status give <alvos> <condição> [acúmulos] [segundos]` · `/mahou status clear <alvos> [condição]`
  (condição = `wet`, `burning`, `chilled`, `frozen`, `shocked`, `bleeding`, `petrified`, `corrupted`, `exposed`, `stunned`,
  `rooted`, `silenced`).

## 7. Detalhes técnicos
- Pacotes (registrar `"1"`, todos servidor → cliente): `core_status_list`, `core_status_reaction`, `core_status_place`.
- Dados por entidade em `persistentData["isekai_mahou_status"]` (poder/quem aplicou por tipo — apagado quando a condição
  acaba —, imunidade a Congelado e à Petrificação, última posição do Sangrando). Mudanças de efeitos no próprio alvo pedidas de dentro do tick de um efeito são adiadas para o fim do
  tick (`StatusEngine.defer`).
- Gelado no jogador: o tempo de conjuração é esticado no início de cada canalização (o cliente recebe o `CastState` novo).
- Estado estático limpo no `ServerStoppedEvent` (servidor) e ao sair do mundo (cliente).
