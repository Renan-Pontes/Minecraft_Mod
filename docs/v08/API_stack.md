# API v0.8 — A Pilha, eventos de dano, Domínios e usos diários (trilha A2)

Complementa `docs/V08_DESIGN.md` §3. Tudo é **aditivo**: código v0.7 compila e se comporta igual quando ninguém usa
contramágica (as exceções estão em §10).

| Arquivo | Para quê |
|---|---|
| `core/api/SpellStack.java` | A Pilha: conjurações pendentes, contramágica, conjurações recentes |
| `core/api/Domains.java` | Registro de domínios abertos + handlers de leis de outros módulos + regra da barreira (§11.1) |
| `core/event/PendingCastEvent.java` | Algo entrou na Pilha (gatilho "Em Resposta") |
| `core/event/SpellCounteredEvent.java` | Algo foi contrariado (+ `SpellCounteredEvent.Attempt`, cancelável, ANTES) |
| `core/event/SpellDamageEvent.java` | `Pre` (cancelável, muda o dano) / `Post` (dano real, overkill, abate) |
| `core/boss/Telegraph.java` | Golpes `delayed*` com dono entram na Pilha; `Shape.castName/tier/uncounterable/element` |
| `core/boss/BossKit.java` | `isBossLike(e)` |
| `core/MagicApi.java` | `dailyUses` / `tryUseDaily` / `worldDay` |
| `glyph/SpellCaster.java` | conjuração de jogador vira pendente; `counterInterrupt`; `recordCast` em toda conjuração |
| `glyph/SpellStats.java` | `INSTANT` / `instant()` (aumento Instantâneo do arcane_duel) |
| `core/stack/**` | implementação (não use direto) · `client/stack/**` HUD, letreiro, estilhaço |

Traduções: `src/main/lang/core_stack/`. Tudo de servidor roda na **thread do servidor**.

---------------------------------------------------------------------------------------------------
## 1. A Pilha — conceitos

Tudo que está **para acontecer** vira uma **conjuração pendente** (`SpellStack.Pending`) que pode ser **contrariada**
antes de resolver:

| origem | quando entra | quando sai |
|---|---|---|
| **Golpe de chefe/mob** | `Telegraph.delayed*(...)` com `.owner(mobVivo)` — formas do MESMO dono no MESMO tick = UM golpe | última forma dispara (resolve), dono morre/some, `Telegraph.cancel/cancelAll` em todas (cancela) |
| **Magia de jogador** | conjuração com tempo de conjuração ≥ 10 ticks (tecla segurada, Carregar, magias caras, Domínio...); se o tempo mudar no meio (Gelado: +15%/acúmulo), o fim da pendente acompanha | a magia sai (resolve) ou é solta/interrompida/falha (cancela) |
| **Módulo** | `SpellStack.push(...)` | `SpellStack.resolve/cancel`, ou sozinha 5 ticks depois do fim |

`Telegraph.show(...)` (só visual) **não** entra. Mobs que conjuram por `castAsMob/castFrom` conjuram na hora (não entram).

**Tier** (`SpellStack.Tier`) = o peso; define o **poder necessário** para contrariar:

| Tier | golpe (aquecimento) | magia de jogador (custo) | poder necessário | chefe contrariado |
|---|---|---|---|---|
| `MINOR` | < 20 ticks | < 40 | 0,5 | só perde o golpe |
| `MAJOR` | 20–59 | 40–119 | 1,0 | Atordoado 30 ticks + Exposto I 5 s |
| `ULTIMATE` | ≥ 60 | ≥ 120, ultimate (G) ou Domínio | 2,0 | Atordoado 60 ticks + Exposto II 8 s |

Outras regras do `counter`:
- O **mesmo chefe** (`BossKit.isBossLike`) só pode ser contrariado **uma vez a cada 12 s** (`COOLDOWN`, aviso "O chefe resiste!").
- **Jogador** contrariado: a conjuração é interrompida ("Sua magia foi contrariada!") e fica **Silenciado 2 s**.
- **Mob comum** contrariado: Silenciado 3 s (+ Atordoado 1,5 s se não for MINOR).
- Não dá para contrariar a própria conjuração, nem uma que já saiu da Pilha (`NOT_COUNTERABLE`).
- O núcleo faz TUDO do sucesso: cancela as formas do golpe (sem callback), roda o `onCountered` da pendente, pune,
  estilhaço de vidro mágico + círculo rúnico quebrando (no ponto, em cada forma e no conjurador), sons, letreiro
  **"CONTRAMÁGICA!"** para quem está a ≤ 48 blocos, contadores **`counter.success`** e **`counter.boss`** (chefe) do
  jogador que contrariou e `SpellCounteredEvent`. **Módulos NÃO devem gravar esses contadores de novo.**
- Falhas dão aviso na barra de ação de quem tentou (jogador) e um efeito pequeno (fumaça/escudo dourado/faísca).

**Hostil**: uma pendente é hostil a `e` se o conjurador não é `e`, não é bicho/invocação do mesmo dono, não é aliado
(`isAlliedTo`) e — entre jogadores — só com PvP ligado. É a regra do HUD e do `counterNear`.

---------------------------------------------------------------------------------------------------
## 2. `SpellStack` — referência

```java
enum SpellStack.Tier { MINOR, MAJOR, ULTIMATE;
    float requiredPower(); static Tier byWarmup(int ticks); static Tier byCost(int manaCost, boolean ultimate);
    static Tier max(Tier a, Tier b); Component displayName(); }          // "Menor" / "Maior" / "Suprema"
enum SpellStack.CounterResult { COUNTERED, RESISTED, COOLDOWN, NOT_COUNTERABLE; boolean success(); }

final class SpellStack.Pending {          // somente leitura
    int id(); @Nullable LivingEntity caster(); UUID casterId(); ResourceKey<Level> dim(); Vec3 pos(); float radius();
    long startTick(); long endTick(); Tier tier(); Component name(); @Nullable Component castName();
    @Nullable Element element(); boolean counterable(); boolean countered(); boolean active();
    boolean fromTelegraph(); boolean fromPlayerCast(); boolean bossCaster();
    int ticksLeft(ServerLevel level); boolean hostileTo(@Nullable LivingEntity e);
}
record SpellStack.Recent(UUID caster, String casterName, SpellRecipe recipe, Element element, float power, long tick, Vec3 pos)

static Pending push(ServerLevel l, LivingEntity caster, Vec3 pos, float radius, int ticks, Tier tier, Component name,
                    @Nullable Element element, @Nullable Runnable onCountered)
static Pending push(..., @Nullable Runnable onCountered, boolean counterable)   // false = aparece no HUD, não pode ser contrariada
static void resolve(Pending p)          // aconteceu
static void cancel(Pending p)           // sumiu (golpe de Telegraph: as formas somem também)
static List<Pending> near(ServerLevel l, Vec3 pos, double range)   // ativas cujo pos OU conjurador está no alcance
static List<Pending> all(ServerLevel l)
@Nullable static Pending get(int id)
static CounterResult counter(ServerLevel l, Pending p, @Nullable LivingEntity by, float power)
static int counterNear(ServerLevel l, Vec3 pos, double radius, @Nullable LivingEntity by, float power)  // contra todas as HOSTIS a "by"
static boolean isHostile(Pending p, @Nullable LivingEntity e)
static void recordCast(LivingEntity caster, SpellRecipe recipe, Element element, float power)       // o núcleo já chama
static List<Recent> recent(ServerLevel l, Vec3 pos, double range, int maxAgeTicks)                  // mais nova primeiro
```
Constantes: `VIEW_RANGE = 48`, `BOSS_COUNTER_COOLDOWN = 240`, `MIN_PLAYER_CAST_TICKS = 10`, `COUNTERSPELL`, `MANA_LEAK`
(ids dos glifos que acendem o "✦ pode ser contrariado" no HUD).

- `Pending.name()`: o nome dado (`castName`), senão o nome do conjurador. `pos()` de golpe = centro da 1ª forma (segue
  um `follow`); de jogador = posição atual dele. `radius()` cobre todas as formas do golpe.
- `power` da contramágica = `stats.power() × ctx.powerMult()` (Amplificar ×1,4; Carregar; cântico 1,15...). Ex.:
  contramágica sem aumentos = 1,0 → contraria MINOR e MAJOR; ULTIMATE precisa de 2 (2× Amplificar + Carregar, etc).
- `counter` dá aviso a `by` (jogador) quando falha; `counterNear` dá UM aviso só se nenhuma foi contrariada.
- `counterNear` processa as mais pesadas primeiro. Um chefe contrariado nela perde **todos** os golpes pendentes dele
  apanhados na MESMA resposta (ex.: chuva de brasas criada em vários ticks = várias pendentes, uma linha "×N" no HUD);
  a recarga de 12 s vale para as próximas contramágicas. `counter` (uma pendente) segue a recarga normalmente.
- Pendente de módulo que você não resolver sai sozinha 5 ticks depois de `endTick` (como resolvida).

---------------------------------------------------------------------------------------------------
## 3. Chefes: como nomear o golpe na Pilha

Nada muda no seu código: todo `Telegraph.delayed*` com `.owner(this)` já entra. Os métodos novos da `Shape` deixam o
HUD bonito e controlam o peso:

```java
Telegraph.Shape.circle(target.position(), 4F).warmup(40).color(Telegraph.color(Element.FIRE)).owner(this)
        .castName(Component.translatable("attack.isekai_mahou.ignis.meteor"))   // "Ignis — Meteoro" no HUD
        .tier(SpellStack.Tier.ULTIMATE)      // opcional (padrão pelo aquecimento: 40 → MAJOR)
        .element(Element.FIRE)               // opcional (runa no HUD; padrão: elemento cuja cor == a da forma)
        .uncounterable();                    // opcional: aparece no HUD com ⊘, não pode ser contrariado
```

- Várias formas criadas pelo mesmo dono no mesmo tick (chuva de meteoros, linha de pilares) = **um** golpe: o tempo é o
  da última forma; contrariar no meio cancela as que faltam. Basta pôr `castName`/`tier` em UMA delas (vale o primeiro
  nome e o maior tier; `uncounterable` em qualquer uma protege o grupo).
- Golpes criados em ticks seguidos viram pendentes separadas; o HUD junta as iguais numa linha "×N".
- Contrariado: as formas somem **sem callback** (o seu `onHit` não roda — nem se o callback já estava na fila do mesmo
  tick, ex.: outro golpe do chefe acertou um jogador cujo gatilho reativo contrariou este). O chefe fica atordoado conforme o tier — se o
  seu chefe tem estado próprio de "carregando golpe", confira `Telegraph.isActive(id)` antes de assumir que acertou, ou
  ouça `SpellCounteredEvent` filtrando `getPending().caster() == this`.
- Ultimate do chefe que NÃO usa Telegraph (sequência própria): use `push` (abaixo) e aborte no `onCountered`.

---------------------------------------------------------------------------------------------------
## 4. Módulo: pondo a sua conjuração na Pilha

```java
// Magia de Nível Supremo (legends_e): 8 s de círculos; contrariável; dispara no fim
SpellStack.Pending[] ref = new SpellStack.Pending[1];
boolean[] aborted = {false};
ref[0] = SpellStack.push(level, caster, target, 20F, 160, SpellStack.Tier.ULTIMATE,
        Component.translatable("glyph.isekai_mahou.super_tier"), ctx.element(), () -> {
            aborted[0] = true;                 // contrariada: some com os círculos, sem efeito
            circles.discard();
        });
BossKit.schedule(level, 160, () -> {
    if (aborted[0] || !caster.isAlive()) return;
    SpellStack.resolve(ref[0]);                // aconteceu
    unleash(ctx, target);
});
```
- `onCountered` roda depois da punição padrão (jogador interrompido/silenciado, chefe atordoado).
- Se o seu efeito acabar antes (dono morreu, alvo sumiu), chame `SpellStack.cancel(p)`.

---------------------------------------------------------------------------------------------------
## 5. Glifo que contraria (Contramágica, Vazamento de Mana)

```java
@Override public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
    float power = stats.power() * ctx.powerMult();
    ServerLevel level = ctx.level();
    LivingEntity by = ctx.caster();
    // No alvo: tudo que é hostil a você e foi conjurado por ele / acontece perto dele
    int n = SpellStack.counterNear(level, target.position(), 3.0 + stats.radius(1F), by, power);
    if (target == by) n = SpellStack.counterNear(level, by.position(), stats.radius(8F), by, power);   // Si Mesmo: pulso
}
```
Contrariar UMA pendente específica (ex.: Vazamento de Mana, que só pega MINOR ou MAJOR com Amplificar):
```java
for (SpellStack.Pending p : SpellStack.near(level, target.position(), 4)) {
    if (!p.hostileTo(by)) continue;
    boolean ok = p.tier() == SpellStack.Tier.MINOR || (p.tier() == SpellStack.Tier.MAJOR && stats.amplify > 0);
    if (!ok) continue;
    if (p.caster() instanceof ServerPlayer sp && MagicApi.get(sp).consumeMana(30)) continue;  // pagou o "imposto"
    SpellStack.CounterResult r = SpellStack.counter(level, p, by, Math.max(power, p.tier().requiredPower()));
}
```
Não é preciso efeito/som/letreiro/contador: o núcleo faz.

---------------------------------------------------------------------------------------------------
## 6. Reagindo à Pilha — `PendingCastEvent` ("Em Resposta")

```java
NeoForge.EVENT_BUS.addListener((PendingCastEvent e) -> {
    SpellStack.Pending p = e.getPending();
    for (ServerPlayer sp : e.getLevel().players()) {
        if (!p.hostileTo(sp) || sp.distanceToSqr(p.pos()) > 20 * 20) continue;
        // Prefira o próximo tick (fora do meio da conjuração/golpe de quem empilhou)
        SpellScheduler.schedule(1, () -> { if (p.active()) fireRespondSlot(sp, p); });
    }
});
```
- Postado na hora em `push` e nas magias de jogador; para golpes de Telegraph, no **fim do tick** em que o grupo se
  formou (já com todas as formas, tempo e tier finais).
- Mire em `p.caster()` (pode ser null) ou `p.pos()`.

### Contramágica — `SpellCounteredEvent` e `SpellCounteredEvent.Attempt`
```java
// Depois: o golpe já saiu da Pilha, punição/efeitos/contadores já aplicados
NeoForge.EVENT_BUS.addListener((SpellCounteredEvent e) -> {
    LivingEntity by = e.getCounteredBy(); SpellStack.Pending p = e.getPending(); boolean boss = e.wasBoss();
});
// Antes (cancelável): "Resguardo" — protege as conjurações de quem tem o efeito
NeoForge.EVENT_BUS.addListener((SpellCounteredEvent.Attempt e) -> {
    if (e.getPending().caster() instanceof LivingEntity c && c.hasEffect(MY_HEXPROOF)) e.setCanceled(true);  // → NOT_COUNTERABLE
});
```
`Attempt` NÃO herda de `SpellCounteredEvent` (ouvir um não recebe o outro).

---------------------------------------------------------------------------------------------------
## 7. Conjurações recentes ("Copiar Magia")

O núcleo grava TODA conjuração que resolve: jogador (`performCast`, inclusive gatilhos automáticos), `castAsMob` e
`castFrom`. Guarda 30 s / 64 por dimensão.
```java
for (SpellStack.Recent r : SpellStack.recent(level, caster.position(), 16, 80)) {   // 4 s, mais nova primeiro
    if (r.caster().equals(caster.getUUID())) continue;     // de OUTRO
    SpellCaster.castFrom(caster, r.element(), r.recipe(), ctx.powerMult(), caster.getEyePosition(), caster.getLookAngle(), true);
    break;
}
```
(`castFrom` também grava: a cópia pode ser copiada de volta.)

---------------------------------------------------------------------------------------------------
## 8. `SpellDamageEvent` — dano de magia

Postado por `SpellContext.damage(...)` em **criaturas** (`LivingEntity`), nesta ordem:
dano base (poder/crítico) → `ElementReactions.onSpellHit` (status e reações, A1) → **`Pre`** → `hurt` → **`Post`**.

| | métodos |
|---|---|
| ambos | `ctx()`, `target()`, `element()` (numa magia multi-elemento, o do glifo), `stats()` (aumentos do glifo que causou o dano), `direct()`, `source()` |
| `Pre` (cancelável) | `getAmount()`, `setAmount(f)`, `getOriginalAmount()` — cancelar ou zerar = nenhum dano (`damage` devolve 0) |
| `Post` | `getAmount()` (pedido ao hurt), `landed()` (hurt aceitou), `dealt()` (vida perdida), `overkill()` (dano REAL — depois de armadura/resistências — além da vida que o alvo tinha; 0 se não morreu), `killed()` (estava vivo e morreu; totem = false) |

```java
// Toque Mortal (arcane_duel): aumento próprio no SpellStats.custom
NeoForge.EVENT_BUS.addListener((SpellDamageEvent.Pre e) -> {
    if (e.stats().custom(DEATHTOUCH) <= 0) return;
    LivingEntity t = e.target();
    if (BossKit.isBossLike(t)) e.setAmount(e.getAmount() * 1.15F);
    else if (t.getHealth() <= t.getMaxHealth() * 0.25F) e.setAmount(Math.max(e.getAmount(), t.getHealth() + t.getAbsorptionAmount() + 1000F));
});
// Atropelar: o que sobrou passa para o inimigo mais próximo
NeoForge.EVENT_BUS.addListener((SpellDamageEvent.Post e) -> {
    if (!e.killed() || e.overkill() <= 0 || e.stats().custom(TRAMPLE) <= 0) return;
    LivingEntity next = nearestEnemy(e.ctx(), e.target(), 5);
    if (next != null) next.hurt(e.ctx().damageSource(null), e.overkill());   // hurt direto: não reaplica reações
});
// Infinito (legends_c): anula, exceto Acerto Garantido
NeoForge.EVENT_BUS.addListener((SpellDamageEvent.Pre e) -> {
    if (hasInfinity(e.target()) && !Domains.sureHitOn(e.target())) e.setCanceled(true);
});
```
Chamar `ctx.damage(...)` de novo dentro de um ouvinte funciona (gera outro par de eventos) — cuide para não entrar em laço.

---------------------------------------------------------------------------------------------------
## 9. `Domains` — Domínios para outros módulos

```java
record Domains.Info(int id, ServerLevel level, LivingEntity owner, Vec3 center, float radius, boolean open,
                    Set<ResourceLocation> laws, Element element, @Nullable SpellContext ctx, long openedTick, int durationTicks,
                    boolean inverted)
    // construtor sem o último parâmetro = inverted false
    // + hasLaw(id), closed() (tem barreira: !open e não é Simples), simple(), breaksFrom(boolean fromInside),
    //   contains(Vec3), contains(Entity), wallDistance(Vec3), age(), isEnemy(LivingEntity), canReach(LivingEntity),
    //   enemiesInside() (todos os inimigos dentro), reachableEnemies() (só os que o domínio alcança)
interface Domains.LawHandler { default void onOpen(Info d){} default void onTick(Info d, int age){} default void onClose(Info d){}
                               default void onEnemyTick(Info d, LivingEntity e, int age){} }
static void registerLaw(ResourceLocation lawId, LawHandler handler)        // no init do módulo
@Nullable static LawHandler handler(ResourceLocation lawId)
static void opened(Info d); static void ticked(Info d, int age); static void closed(Info d)   // o módulo domain chama
static List<Info> active(ServerLevel l); @Nullable static Info at(ServerLevel l, Vec3 pos)
static boolean sureHitOn(LivingEntity target); static boolean isEnemy(Info d, LivingEntity e)

// Barreira (§11.1)
interface Domains.BarrierHandler { float integrity(int domainId); float maxIntegrity(int domainId);
                                   float damage(int domainId, float amount, boolean fromInside, @Nullable Entity attacker); }
static void setBarrierHandler(@Nullable BarrierHandler h); @Nullable static BarrierHandler barrierHandler()
static float hitBarrier(ServerLevel l, Vec3 hitPos, Vec3 attackerPos, @Nullable Entity attacker, float amount)
@Nullable static Info barrierAt(ServerLevel l, Vec3 pos)      // domínio FECHADO com a parede a ≤ 1,5 de pos
@Nullable static Info innermost(ServerLevel l, Vec3 pos)      // o FECHADO de menor raio que contém pos, senão o aberto de menor raio
static boolean shielded(LivingEntity target, Info attackerDomain)

Domains.SURE_HIT        = isekai_mahou:law_sure_hit
Domains.SIMPLE          = isekai_mahou:simple_domain   // pseudo-lei: o domain põe em Domínios Simples (protege do Acerto Garantido)
Domains.WALL_TOLERANCE  = 1.5
```

**Lei de outro módulo** (ex.: `legends_c`):
```java
// init do módulo: o glifo da lei é um LawGlyph comum (GlyphRegistry.register(new LawGlyph("law_shrine", 40)...))
Domains.registerLaw(IsekaiMahou.id("law_shrine"), new Domains.LawHandler() {
    @Override public void onOpen(Domains.Info d) { spawnShrine(d.level(), d.owner(), d.center()); }
    @Override public void onTick(Domains.Info d, int age) { if (age % 10 == 0) dismantleRandomBlocks(d); }
    @Override public void onEnemyTick(Domains.Info d, LivingEntity e, int age) { if (age % 10 == 0) cleave(d, e); }
    @Override public void onClose(Domains.Info d) { removeShrine(d.id()); }
});
```

**Módulo domain** (quem abre/fecha):
```java
Domains.Info info = new Domains.Info(dome.getId(), sl, owner, dome.position(), dome.radius(), dome.isOpen(),
        lawIds(ctx.spell()), ctx.element(), ctx, openedTick, durationTicks, dome.isInverted());
Domains.opened(info);           // ao ativar (chamar de novo com o mesmo id só atualiza: onOpen roda UMA vez)
Domains.ticked(info, age);      // a cada tick ativo (pode passar um Info novo com o MESMO id: raio/centro atualizam)
Domains.closed(info);           // ao fechar/quebrar/remover (onClose roda UMA vez; chamar de novo não faz nada)
```
- Domínio Simples: registre também (com `Domains.SIMPLE` nas leis) para o `sureHitOn` respeitar a proteção dele. Ele não
  tem barreira, não conta no `innermost`/`shielded`/`barrierAt`.
- `onTick` e `onEnemyTick` rodam dentro do `ticked`. `onEnemyTick` recebe só os inimigos que o domínio **alcança**
  (`Info.canReach`: dentro, inimigo pela regra do domo — chefes só brigam com jogadores, bichos deles e quem os ataca — e
  NÃO protegidos pela barreira de um domínio menor de outro dono). A lista é refeita a cada 5 ticks (domos enormes) e
  conferida de novo a cada tick (vivo, dentro, inimigo, não protegido). Exceções num handler vão para o log; o domínio continua.
- Domínio que para de receber `ticked` por 5 s (domo sumiu sem avisar) é fechado pelo núcleo (`onClose`).
  Nível descarregando: esquecido sem `onClose`. Fim do servidor: limpo. Os handlers registrados ficam (são do init).

### Barreira — "quebra por fora, não por dentro; invertida é o contrário; o maior só ataca por fora"
- O módulo domain guarda a integridade e registra `Domains.setBarrierHandler(...)` no init.
- Quem detectar um golpe na parede (projétil batendo, explosão, efeito de magia caindo nela, soco mirando o domo) chama:
  ```java
  Domains.Info wall = Domains.barrierAt(level, hitPos);            // há parede aqui?
  if (wall != null) {
      float tirado = Domains.hitBarrier(level, hitPos, shooter.position(), projectile, dano);
      if (tirado <= 0) projectile.discard();                         // aguentou: o projétil se desfaz na parede
  }
  ```
  `hitBarrier` já aplica a regra: barreira normal só perde integridade com ataque de FORA (`attackerPos` fora da esfera),
  invertida só de DENTRO; o lado que não quebra ganha faíscas, som abafado e o aviso "A barreira não cede por dentro..."
  (ou "A barreira invertida não cede por fora...") na barra de ação do jogador (no máx. 1 a cada 1,5 s). O dono do
  domínio e os aliados dele nunca danificam a própria barreira (o dono de um projétil — `TraceableEntity` — conta como
  o atacante). Só o lado que pode quebrar chega ao `BarrierHandler.damage`.
- **O mais interno manda**: `shielded(alvo, D)` = o alvo está dentro da barreira FECHADA (normal ou invertida) de um
  domínio MENOR que `D` e de outro dono. Os efeitos e o Acerto Garantido de `D` não o atingem; `D` só pode atacar essa
  barreira por fora (o domain faz: pulso do maior tira integridade do menor). Domínio aberto não protege ninguém.
- `sureHitOn(alvo)`: existe um domínio INIMIGO com `law_sure_hit` que contém o alvo, não é Simples, o alvo não está
  `shielded` dele e não está num Domínio Simples seu/de aliado. Ex.: preso no Santuário (aberto, enorme) dentro do seu
  próprio domínio fechado → o Santuário não tem Acerto Garantido em você; dentro do domínio de um terceiro com
  `law_sure_hit` → vale o do terceiro.
- `innermost(pos)`: o domínio fechado de menor raio que contém o ponto; se não houver fechado, o aberto de menor raio.

---------------------------------------------------------------------------------------------------
## 10. Usos diários, Instantâneo e outros

```java
// Magia de Nível Supremo: 1 + Círculo/3 por dia do mundo
if (!MagicApi.tryUseDaily(sp, "super_tier", 1 + MagicApi.get(sp).circle() / 3)) { avisar(); return; }
int usados = MagicApi.dailyUses(sp, "super_tier");
```
- Dia = `getDayTime() / 24000` (`MagicApi.worldDay(p)`); contadores `daily.<key>` e `daily.<key>.day`. Criativo: sempre
  pode e não gasta. Com o ciclo de dia desligado o dia não muda (os usos não voltam).

**Instantâneo** (`instant`, glifo do arcane_duel): o aumento só precisa marcar o stat; o núcleo faz o resto:
```java
GlyphRegistry.register(new AugmentGlyph("instant", 10, s -> s.custom.merge(SpellStats.INSTANT, 1, Integer::sum)).element(Element.ARCANE)...);
```
Com `instant` em qualquer glifo: custo ×1,5, tempo de conjuração 0 → nunca entra na Pilha. `ParsedSpell.isInstant()`.

**`BossKit.isBossLike(e)`**: tag `c:bosses`, chefe de provação ou vida máx. ≥ 150 (jogadores nunca).

**`SpellCaster.counterInterrupt(ServerPlayer, by)`**: interrompe a conjuração com "Sua magia foi contrariada!" (a Pilha
usa; módulos raramente precisam).

### O que mudou de comportamento (v0.7 → v0.8)
- Golpes telegrafados com dono e magias de jogador com conjuração ≥ 10 ticks agora **podem** ser contrariados (só
  muda algo se alguém contrariar).
- `SpellContext.damage` devolve 0 se um `SpellDamageEvent.Pre` cancelar/zerar. Sem ouvintes, igual à v0.7 (inclusive
  dano 0 continua chamando `hurt`).
- `Telegraph.cancel/finish/cancelAll` também tiram o golpe da Pilha.

---------------------------------------------------------------------------------------------------
## 11. Cliente (HUD da Pilha)

- Painel à esquerda, no meio da tela (x = 30, abaixo do cartão, acima do chat): até 5 pendentes HOSTIS a ≤ 48 blocos —
  runa do elemento (`GlyphArt.element`) ou ícone de perigo, conjurador + golpe, barra regressiva + segundos, tier
  (Suprema em vermelho pulsando), "✦" quando você conhece `counterspell`/`mana_leak` (Vazamento: não mostra em Suprema)
  e "⊘" quando não pode ser contrariada. Repetidas viram "×N". Contrariada: carimbo "CONTRARIADA" com rachaduras.
- Config de cliente `showStack` (`MahouConfig.SHOW_STACK`).
- Texturas opcionais da `art`: `textures/gui/stack/{minor,major,ultimate,countered}.png` (16×16). Sem elas, desenho de reserva.
- Para o `ui_hud`: `StackHud.leftEdge = () -> x` muda a margem esquerda (ex.: se os ícones de status do jogador ficarem
  ao lado da barra de mana); `StackHud.enabled = false` desliga e você desenha com `ClientStack.entries()`
  (`Entry`: `casterName, spellName(), element, tier, counterable, boss, remainingFraction(now), ticksLeft(now), leaving(), countered`;
  `now = ClientStack.ticks() + partial`).
- Letreiro "CONTRAMÁGICA!" (sempre; respeita F1) e círculo rúnico estilhaçando no mundo (no ponto do golpe e no peito do conjurador).

### Comandos de teste (cheats)
- `/mahou stack test [minor|major|ultimate] [segundos]` — a criatura mais próxima (≤ 24 blocos) telegrafa um golpe
  INOFENSIVO em você ("Golpe de Teste"): aparece no HUD.
- `/mahou stack counter [poder]` — contraria tudo que é hostil a você a ≤ 32 blocos (padrão poder 10).
- `/mahou stack list` — pendentes a ≤ 64 blocos.

### Técnico
- Pacotes (registrar `"1"`): `core_stack_add`, `core_stack_remove`, `core_stack_countered` (servidor → cliente). O
  servidor manda só o que entrou/mudou/saiu, a cada 2 ticks, por jogador.
- Estado estático limpo no `ServerStoppedEvent`; pendentes de um nível que descarrega são canceladas; jogador que sai
  tem a conjuração cancelada.
