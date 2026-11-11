# API v0.8 — Reputação e facções (trilha A3)

Pacote base `com.renanpontes.isekaimahou` (abreviado `J.`). Tudo é ADITIVO: nada da v0.7 mudou.

| Onde | O quê | Lado |
|---|---|---|
| `J.core.api.Reputation` | API pública: valores, patamares, facções, membros, títulos | comum (leitura) / servidor (escrita) |
| `J.core.api.Reputation.Standing` | os 8 patamares (Inimigo … Exaltado) | comum |
| `J.core.api.Reputation.Faction` | definição de uma facção (lida do JSON) | comum |
| `J.core.api.Reputation.FactionMember` | interface: a entidade decide a própria facção | comum |
| `J.core.event.ReputationChangedEvent` | evento depois de cada mudança | servidor |
| `data/<ns>/factions/<id>.json` | as facções (recarregam com `/reload`, vão ao cliente no login e no reload) | dados |
| `data/<ns>/tags/entity_type/faction/<id>.json` | membros por tag | dados |
| `J.core.reputation.*` | implementação (não use direto) | — |
| `J.client.reputation.*` | aba "Reputação" (menu B, ordem 55), avisos laterais | cliente |

> Leitura (`get`, `standing`, `factionOf`, `factions`, `titles`...) funciona no **cliente** (jogador local; dados vêm
> sincronizados no `MagicData`) e no servidor. Escrita (`add`, `set`, `addDaily`, `grantTitle`, `selectTitle`) só no servidor.

---------------------------------------------------------------------------------------------------
## 1. Facções do jogo

| id | nome | cor | rivais (peso) | aliados | membros |
|---|---|---|---|---|---|
| `guild` | Guilda dos Aventureiros | `0xE8B84A` | — | — | `guild_receptionist`, `guild_master`, `adventurer` |
| `kingdom` | Reino de Aldória | `0x3F6FD8` | `horde` (1), `underworld` (½) | `church` | `city_guard`, `citizen`, `city_lord`, `city_merchant`*, `minecraft:villager`, `minecraft:iron_golem` |
| `academy` | Academia Arcana | `0xB98CFF` | — | — | `wandering_mage` (+ escriba*) |
| `church` | Igreja da Aurora | `0xFFE9A8` | `underworld` (1) | `kingdom` | (sacerdote*) |
| `underworld` | Submundo | `0x8A2B3A` | `church` (1), `kingdom` (½) | — | (receptador*) |
| `wildfolk` | Povo Selvagem | `0x4FBF4A` | `horde` (½) | — | `fairy`, `sylph`, `undine`, `lumen_sprite`, `moss_golem`, `wandering_spirit` |
| `horde` | A Horda | `0xA0522D` | `kingdom` (1) | — | goblins (6 ids), `orc`, `orc_berserker`, `orc_lord`, `kobold` |

\* `city_merchant` está na tag do Reino; o módulo `cities` implementa `FactionMember` para mandar o escriba para
`academy`, o sacerdote para `church` e o receptador para `underworld` (ver §4).

Traduções prontas (lang `core_reputation`): `faction.isekai_mahou.<id>` (nome), `.desc`, `.perk.<patamar>` (8 por facção),
`reputation.isekai_mahou.standing.<patamar>`, títulos `reputation.isekai_mahou.title.<facção>.<patamar>`.

---------------------------------------------------------------------------------------------------
## 2. Valores e patamares

Valor de **−1000 a +1000** por facção, no contador `rep.<id>` do `MagicData` (salvo, sincronizado; começa em 0).

| `Standing` | id | de | `priceMultiplier()` | `canTrade()` |
|---|---|---|---|---|
| `ENEMY` | enemy | −1000 | 1,5 | não — membros atacam à vista |
| `HOSTILE` | hostile | −600 | 1,5 | não — guardas/membros atacam |
| `UNFRIENDLY` | unfriendly | −300 | 1,25 | sim |
| `NEUTRAL` | neutral | −49 | 1,0 | sim |
| `FRIENDLY` | friendly | 100 | 0,95 | sim — Horda/Povo Selvagem ficam neutros |
| `HONORED` | honored | 300 | 0,9 | sim |
| `REVERED` | revered | 600 | 0,8 | sim |
| `EXALTED` | exalted | 900 | 0,7 | sim |

```java
enum Reputation.Standing {
    String id; int min; int rgb;               // rgb = cor do patamar (texto/barras)
    int max();  float priceMultiplier();  boolean canTrade();  boolean atLeast(Standing);
    @Nullable Standing next();  @Nullable Standing previous();  int argb();
    MutableComponent displayName();            // "Amigável", já colorido
    static Standing of(int value);  @Nullable static Standing byId(String);
    static List<Standing> rewardTiers();       // FRIENDLY, HONORED, REVERED, EXALTED
}
```

---------------------------------------------------------------------------------------------------
## 3. `Reputation` — métodos

```java
// ---- leitura (cliente: jogador local; servidor: qualquer jogador)
static int      get(Player p, String faction)                 // −1000..1000 (outro jogador no cliente: 0)
static Standing standing(Player p, String faction)
static boolean  reached(Player p, String faction, Standing s) // já alcançou (recompensa entregue)
static float    priceMultiplier(Player p, String faction)     // confira canTrade antes
static boolean  canTrade(Player p, String faction)            // patamar ≥ Desconfiado
static boolean  isHostile(Player p, String faction)           // Hostil ou Inimigo
static boolean  isFriendly(Player p, String faction)          // Amigável ou melhor
static Collection<Faction> factions()                         // na ordem de exibição
@Nullable static Faction faction(String id)
@Nullable static String  factionOf(Entity e)                  // FactionMember, senão a tag faction/<id>
@Nullable static Faction best(Player p)                       // facção com o maior valor
static String counterKey(String faction)                      // "rep.<id>"
static String reachedFlag(String faction, Standing s)         // "rep.<id>.<patamar>"
// ---- títulos
static List<String> titles(Player p)                          // chaves desbloqueadas
static boolean hasTitle(Player p, String key)
@Nullable static String selectedTitleKey(Player p)
@Nullable static MutableComponent selectedTitle(Player p)     // traduzido, dourado
// ---- escrita (servidor)
static int  add(ServerPlayer p, String faction, int amount, @Nullable Component reason)  // devolve o valor novo
static void set(ServerPlayer p, String faction, int value)
static int  addDaily(ServerPlayer p, String faction, int amount, String source, int dailyCap, @Nullable Component reason)
static void grantTitle(ServerPlayer p, String titleKey)
static boolean selectTitle(ServerPlayer p, @Nullable String titleKey)
static boolean recentlyAttacked(Player p, String faction)     // atacou membro nos últimos 30 s (vingança)
static final int MIN = -1000, MAX = 1000;  static final String NO_FACTION = "";
```

O que `add` faz (nesta ordem):
1. soma e limita a −1000..1000 (`MagicApi.addCounter("rep.<id>", …)` → também dispara `MagicProgressEvent.Counter`);
2. **respingo** (sem encadear): ganho POSITIVO tira `50% × peso` dos rivais (peso ½ → −25%); aliados acompanham
   `25% × peso` de qualquer mudança (arredondado para zero: +1 de um abate não respinga). O respingo também dispara
   evento e aviso (motivo "Rival de X"/"Aliado de X");
3. `ReputationChangedEvent` (NeoForge.EVENT_BUS) e o aviso lateral no cliente ("+15 Guilda dos Aventureiros · motivo");
4. ao **subir** de patamar: banner + toast + mensagem com a vantagem nova + recompensas de CADA patamar novo de Amigável
   para cima, uma única vez (flag `rep.<id>.<patamar>`); ao **cair**: banner vermelho e aviso (Hostil: "guardas vão atacar").

Facção desconhecida → nada muda (aviso no log uma vez). `amount` 0 → nada.

`addDaily`: ganho positivo limitado a `dailyCap` pontos por **dia de jogo** (dia do Overworld) para a chave
`<facção>.<source>` (contadores `rep_daily.*` — de propósito FORA do prefixo `rep.`, que só tem os valores). Devolve quanto entrou.

> Contadores com prefixo `rep.` são SÓ os valores (`rep.<id>`) — dá para usar em condições do Diário/missões
> (`counter {key: "rep.kingdom", min: 100}` = Reino Amigável). Patamar alcançado: flag `rep.<id>.<patamar>`.

### Evento
```java
NeoForge.EVENT_BUS.addListener((ReputationChangedEvent e) -> {
    e.getPlayer(); e.getFaction(); e.getOldValue(); e.getNewValue(); e.getDelta();
    e.getCause();          // DIRECT (add) | SPILLOVER (rival/aliado) | SET (set/comando)
    e.getReason();         // @Nullable Component
    e.getOldStanding(); e.getNewStanding(); e.standingChanged();
});
```

---------------------------------------------------------------------------------------------------
## 4. Exemplos

### Dar reputação por missão (guild)
```java
// ao entregar a missão (servidor)
Reputation.add(sp, "guild", 5 + 5 * rankIndex, Component.translatable("quest.isekai_mahou." + id));
// missão de facção com "rewards.reputation": {"kingdom": 20, "underworld": -10}
quest.reputation().forEach((faction, n) -> Reputation.add(sp, faction, n, quest.title()));
// liberar missão de facção por patamar ("standing": "friendly")
boolean ok = Reputation.standing(sp, quest.faction()).atLeast(Reputation.Standing.byId(quest.standing()));
```

### Preço de mercador (cities)
```java
String faction = Reputation.factionOf(merchant);              // ex.: "academy" para o escriba
if (faction != null && !Reputation.canTrade(player, faction)) {
    player.displayClientMessage(Component.translatable("message.isekai_mahou.cities.refuse"), true);
    return;
}
float mult = faction == null ? 1F : Reputation.priceMultiplier(player, faction);
int price = Math.max(1, Math.round(basePrice * mult));
// ofertas extras por patamar
if (Reputation.standing(player, "academy").atLeast(Reputation.Standing.EXALTED)) offers.add(legendScrollOffer());
```
Sugestão de liberação do escriba (combina com o texto das vantagens da Academia): Neutro → tier 1 · Amigável → 2 ·
Honrado → 3 · Reverenciado → 4 · Exaltado → Pergaminhos Lendários.

### Checar hostilidade de guarda (cities)
```java
// no targetSelector do city_guard
targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
        t -> t instanceof Player p && !p.isCreative() && !p.isSpectator()
                && (Reputation.isHostile(p, "kingdom") || p.hasEffect(WANTED))));
// vingança coletiva: o jogador bateu em alguém do Reino nos últimos 30 s?
if (Reputation.recentlyAttacked(player, "kingdom")) setTarget(player);
```

### Implementar `FactionMember` (mercador por profissão)
```java
public class CityMerchant extends PathfinderMob implements Reputation.FactionMember {
    private static final EntityDataAccessor<String> PROFESSION = ...;   // sincronizado: funciona no cliente (Olho Analítico)
    @Override public @Nullable String factionId() {
        return switch (entityData.get(PROFESSION)) {
            case "scribe" -> "academy";
            case "priest" -> "church";
            case "fence"  -> "underworld";
            default -> null;            // null = usa a tag (kingdom); Reputation.NO_FACTION = "sem facção"
        };
    }
}
```
Companheiro contratado que não deve contar como membro: devolva `Reputation.NO_FACTION` enquanto estiver contratado.

### Mostrar na interface (ui_hud, cliente)
```java
String fid = Reputation.factionOf(target);
Reputation.Faction f = Reputation.faction(fid);
if (f != null) {
    Reputation.Standing s = Reputation.standing(mc.player, f.id());
    Component line = Component.empty().append(f.coloredName()).append(" — ").append(s.displayName());
}
MutableComponent title = Reputation.selectedTitle(mc.player);   // pode ser null
Reputation.Faction best = Reputation.best(mc.player);
```
Emblema: `textures/gui/faction/<id>.png` (32×32) = `faction.emblem()`; item: `faction.iconStack()`.

### Ganho diário limitado (ex.: ajudar guardas)
```java
Reputation.addDaily(sp, "kingdom", 2, "cities.help_guard", 20, Component.translatable("reason..."));
```

---------------------------------------------------------------------------------------------------
## 5. Fontes automáticas (núcleo)

| Fonte | Efeito | Onde se configura |
|---|---|---|
| matar membro de facção | `kill_penalty` (Guilda −30, Reino −25: guarda −40, aldeão/cidadão −30, Lorde −150; Academia −30; Igreja −40; Submundo −35; Povo Selvagem −4, fada −30; Horda −3) | `kill_penalty`/`kill_penalties` |
| matar membro da Horda | +2 Reino, até 40/dia | `kill_rewards` + `kill_reward_daily_cap` (horde.json) |
| matar monstro hostil dentro de `Places` tipo `city`/`capital` | +1 na facção do lugar (padrão Reino), até 30/dia | código (`ReputationEvents`) |
| concluir Provação (contador `trial.clear`) | +10 Academia | `counter_rewards` (academy.json) |

Só contam abates de `ServerPlayer` de verdade (FakePlayer não). Mobs domados não contam como monstro.

> **Módulos que punem crimes (cities):** o núcleo JÁ aplica o `kill_penalty` do JSON quando o jogador mata um membro
> (guarda −40, cidadão −30, mercador −35, Lorde −150). Some só o EXTRA do crime (ex.: matar guarda = −60 no total →
> o módulo dá −20), ou ajuste o `kill_penalties` do kingdom.json — senão a punição sai em dobro.

`counter_rewards` paga por **+1 real** do contador: o núcleo guarda o último valor visto em `rep_seen.<chave>`, então
somas de 0, correções para baixo e eventos repetidos não dão reputação (máx. 20 "+1" de uma vez).

**Neutralidade** (`"neutral_when_friendly": true` — Horda e Povo Selvagem): membros não escolhem como alvo jogadores com
patamar ≥ Amigável, exceto se o jogador atacou algum membro da facção nos últimos 30 s (vingança coletiva) ou foi quem feriu
aquele mob. Duas camadas: na busca de alvo o jogador fica "invisível" para eles (`LivingVisibilityEvent`, só a 2 blocos)
e `LivingChangeTargetEvent` barra qualquer outro caminho (alertas de bando etc.). Chefes (tag `c:bosses`, chefes de
Provação, vida máx. ≥ 150) nunca ficam neutros. Ao virar Amigável, quem já mirava o jogador desiste.

**Quem ataca reputação ruim** (objetivo de alvo extra, adicionado quando a entidade entra no mundo; criativo/espectador
nunca; golem feito pelo jogador nunca ataca jogadores):
- **Guardas** (`"guards"`: tipos sem IA própria de facção; Reino: `minecraft:iron_golem`) atacam quem está **Hostil ou
  Inimigo** com a facção.
- **Membros que sabem lutar** (`PathfinderMob` com o atributo `attack_damage`, por tag ou `FactionMember`) atacam quem é
  **Inimigo** da facção DELES — lida na hora, então o mercenário contratado que devolve `NO_FACTION` não ataca.
  Facções `neutral_when_friendly` ficam de fora (já atacam todo mundo). Módulos com IA própria (city_guard) podem ter as
  regras deles por cima — os alvos coincidem.

**Comércio vanilla recusado** (`"traders"`: Reino → `minecraft:villager`; Academia → `isekai_mahou:wandering_mage`):
clicar num desses com patamar Hostil/Inimigo na facção dele não abre as trocas — aviso na barra ("O Reino de Aldória se
recusa a negociar com você"), aldeão balança a cabeça. Nome, laço e ovo continuam funcionando. Mercadores dos módulos
(city_merchant, aventureiro) NÃO entram aqui: eles checam `canTrade` na própria tela de diálogo.

---------------------------------------------------------------------------------------------------
## 6. Formato do JSON (`data/<ns>/factions/<id>.json`)

```json
{
  "color": "#3F6FD8",                     // ou número
  "icon": "minecraft:bell",               // item (toasts/listas)
  "order": 10,                            // ordem na aba
  "rivals": {"horde": 1.0, "underworld": 0.5},   // ou ["horde"] (peso 1)
  "allies": {"church": 1.0},
  "kill_penalty": -25,
  "kill_penalties": {"isekai_mahou:city_guard": -40},
  "kill_rewards": {"kingdom": 2},         // matar membro DESTA facção dá isto em outras (negativo = sem limite)
  "kill_reward_daily_cap": 40,
  "counter_rewards": {"trial.clear": 10}, // contador do MagicData (+1) → reputação aqui (chaves "rep.*"/"rep_*" ignoradas)
  "neutral_when_friendly": false,
  "guards": ["minecraft:iron_golem"],     // atacam quem está Hostil/Inimigo
  "traders": ["minecraft:villager"],      // trocas vanilla recusadas para Hostil/Inimigo
  "rewards": {                            // friendly | honored | revered | exalted
    "friendly": {
      "xp": 100,
      "items": [{"id": "minecraft:emerald", "count": 8}],  // ids ausentes são ignorados
      "glyph_scrolls": 1, "glyph_tier": 2,                 // ou "glyph_scrolls": {"count": 1, "tier": 2}
      "legend": "random",                                  // ou id de lenda (Legends)
      "title": "reputation.isekai_mahou.title.kingdom.friendly"
    }
  }
}
```
- id = nome do arquivo (`[a-z0-9_]+`); tag de membros = `<ns>:faction/<id>`.
- Pergaminho de glifo: um glifo que o jogador ainda não sabe e consegue usar (maior tier até `glyph_tier`); se não houver,
  3 Cristais de Mana. Lenda `"random"`: `Legends.randomScroll(random, círculo)`.
- Datapacks podem adicionar facções novas (aparecem na aba, comandos e API) — traduza `faction.isekai_mahou.<id>`.

---------------------------------------------------------------------------------------------------
## 7. Títulos

- Cada patamar com `"title"` desbloqueia o título (flag `rep.title:<chave>`) ao ser alcançado pela 1ª vez; o primeiro título
  é equipado sozinho. O jogador troca/tira na aba Reputação (clique no título no topo ou na lista da facção).
- Título em uso: flag `rep.title_on:<chave>` (no máximo uma). Outros módulos podem dar títulos próprios com
  `Reputation.grantTitle(sp, "chave.de.traducao")` (ex.: o Lorde dando "Herói de Aldória" =
  `reputation.isekai_mahou.title.kingdom.exalted`) — aparecem na lista de títulos do jogador.

---------------------------------------------------------------------------------------------------
## 8. Comandos

- `/mahou rep [jogador]` — lista facção, patamar, valor e quanto falta para o próximo (qualquer jogador).
- `/mahou rep set <jogadores> <facção> <n>` · `/mahou rep add <jogadores> <facção> <n>` · `/mahou rep reset <jogadores>` (op).

---------------------------------------------------------------------------------------------------
## 9. Cliente

- Aba **Reputação** (`MenuTabs.register("reputation", 55, …)`, ícone `textures/gui/tab/reputation.png`): título em uso no
  topo, cartões das facções à esquerda (emblema, patamar colorido, valor, barra), detalhes à direita (escada dos 8
  patamares com o marcador, progresso "150 / 300", preços, vantagens do patamar atual e do próximo, recompensas de cada
  patamar com ✔ quando recebidas, rivais/aliados, títulos clicáveis). Sem a textura do emblema, desenha um brasão.
- **Avisos laterais** (canto superior direito, abaixo dos ícones de efeito; empurram o rastreador de objetivos com
  `ObjectiveTracker.reserveTop("reputation_notices", …)`): "+15 Guilda dos Aventureiros", patamar · valor · motivo e barra.
  Mudanças seguidas da mesma facção se somam. Config do cliente `reputationToasts` (`MahouConfig.REPUTATION_TOASTS`).
- Pacotes (registrador "1"): `rep_factions` (S→C), `rep_notify` (S→C), `rep_select_title` (C→S).
