# API do kit de chefes (v0.7, trilha A3)

Pacotes: `com.renanpontes.isekaimahou.core.boss` (servidor/comum) e `com.renanpontes.isekaimahou.client.boss` (só cliente — você NÃO precisa chamar nada dele).
Tudo já é registrado pelo núcleo (`BossToolkit.init` / `BossToolkitClient.init`). Módulos só chamam os métodos estáticos abaixo.

| Classe | Para quê |
|---|---|
| `Telegraph` | Telegrafia no chão (círculo, anel, linha, cone, perseguição, zona segura) + golpe atrasado com callback |
| `Telegraph.Shape` | Construtor fluente de uma forma (aquecimento, hold, cor, dono, faixa vertical...) |
| `BossKit` | Modo provação/arena, escala de grupo, anti-trapaça (salto, teleporte), rugido, tremor de câmera, agendador |
| `BossRewards` | XP + contador `boss.<id>` para o grupo inteiro quando o chefe morre |

**Regras gerais**
- Chame SEMPRE no servidor, na thread do servidor (dentro do `tick()`/`aiStep()` do chefe, de goals, de eventos). Pegue o nível com `if (level() instanceof ServerLevel sl)`.
- Ids de telegrafia são `int`. `-1` = recusado (só acontece com formas só-visuais acima de 4096 ativas no servidor).
- O jogador vê a telegrafia se estiver a ≤ 96 blocos (`Telegraph.VIEW_RANGE`) quando ela é criada. Quem chega depois não vê as antigas (é proposital).
- O cliente desenha no máx. 256 formas ao mesmo tempo (as mais antigas somem primeiro).

---------------------------------------------------------------------------------------------------
## 1. `Telegraph` — telegrafia no chão

### O que o jogador vê
Um decalque translúcido **deitado no terreno** (sobe degraus, lajes, fica sobre a água rasa) que:
1. aparece com a área inteira bem fraca (mostra o tamanho total);
2. **enche do centro até a borda** durante o aquecimento (`warmup`), com uma "frente" brilhante andando — quando a frente chega na borda, o golpe acerta;
3. tem **borda brilhante pulsante** (pulsa mais rápido perto do fim) e **anel de runas girando** (formas redondas ≥ 1,5 bloco) ou **setas correndo** (linhas, na direção do golpe);
4. no fim **pisca** (flash branco + anel que expande) e some. Com `hold`, fica cheia e pulsando mais um tempo (perigo que permanece).

Linha: enche do `from` para o `to`. Anel: enche do raio interno para o externo. Cone: enche do vértice para fora.
Zona segura (`safe`): verde, borda suave, um "relógio" gira mostrando quanto tempo falta; sem flash.

### Y da forma
Passe o **Y do chão onde o golpe acontece** — normalmente `alvo.position()` ou `position()` do chefe (pés).
Em cada vértice o cliente deita o decalque na **superfície exposta (com ar em cima) mais perto do Y**, procurando de Y + topo da faixa de acerto (`vertical`, padrão +3) até Y−5. Resultado: o desenho aparece em cima de degraus/pilares que TAMBÉM levam o golpe (pilar de 2–3 blocos dentro do círculo fica marcado), fica no piso de uma casa e não no telhado, sobre água rasa, neve, trilhos e placas. Parede mais alta que a faixa de acerto não é marcada (e não é atingida).
Para arenas planas/plataformas no ar use `.flat()` (desenha reto em Y+0,02).

### Atalhos (contrato do design §6.1)
```java
int  Telegraph.circle(ServerLevel level, Vec3 center, float radius, int warmupTicks, int rgb)
int  Telegraph.ring  (ServerLevel level, Vec3 center, float inner, float outer, int warmupTicks, int rgb)
int  Telegraph.line  (ServerLevel level, Vec3 from, Vec3 to, float width, int warmupTicks, int rgb)      // width = largura TOTAL
int  Telegraph.cone  (ServerLevel level, Vec3 origin, float yawDeg, float angleDeg, float range, int warmupTicks, int rgb)
int  Telegraph.follow(ServerLevel level, Entity e, float radius, int warmupTicks, int rgb)
int  Telegraph.safe  (ServerLevel level, Vec3 center, float radius, int ticks)

int  Telegraph.delayedBlast (ServerLevel level, Vec3 center, float radius, int warmupTicks, int rgb, Consumer<List<LivingEntity>> onHit)
int  Telegraph.delayedLine  (ServerLevel level, Vec3 from, Vec3 to, float width, int warmupTicks, int rgb, Consumer<List<LivingEntity>> onHit)
int  Telegraph.delayedRing  (ServerLevel level, Vec3 center, float inner, float outer, int warmupTicks, int rgb, Consumer<List<LivingEntity>> onHit)
int  Telegraph.delayedCone  (ServerLevel level, Vec3 origin, float yawDeg, float angleDeg, float range, int warmupTicks, int rgb, Consumer<List<LivingEntity>> onHit)
int  Telegraph.delayedFollow(ServerLevel level, Entity e, float radius, int warmupTicks, int rgb, Consumer<List<LivingEntity>> onHit)
```
- `yawDeg` do cone está no padrão do Minecraft: use `getYRot()` do chefe (0 = sul/+Z, 90 = oeste/−X). `angleDeg` é a abertura TOTAL (90 = 45° para cada lado), 5..360.
- `follow`: o círculo segue a entidade e **trava** nos últimos `Telegraph.DEFAULT_LOCK` = 10 ticks (ou `warmup/3` se o aquecimento for curto) — o jogador vê o círculo parar e ainda tem tempo de sair. `delayedFollow` acerta quem está no ponto TRAVADO.
- `safe` não acerta ninguém; use `Telegraph.entitiesIn(...)` para saber quem está dentro (ver exemplo 1.6), ou `Telegraph.delayed(sl, Telegraph.Shape.safe(spot, 3F).warmup(60), dentro -> ...)` — o callback recebe quem está DENTRO da zona segura no fim do tempo.

### API completa
```java
int  Telegraph.show(ServerLevel level, Telegraph.Shape shape)                                   // só visual
int  Telegraph.delayed(ServerLevel level, Telegraph.Shape shape, Consumer<List<LivingEntity>> onHit)
void Telegraph.cancel(ServerLevel level, int id)     // some rápido, SEM flash e SEM callback
void Telegraph.finish(ServerLevel level, int id)     // termina AGORA com o flash de "acertou" (sem callback)
void Telegraph.cancelAll(Entity owner)               // cancela tudo cujo .owner(...) é este chefe
boolean Telegraph.isActive(int id)                   // ainda aquecendo/em hold no servidor?
@Nullable Vec3 Telegraph.centerOf(int id)            // centro atual (follow: alvo ou ponto travado); vale DENTRO do callback
List<LivingEntity> Telegraph.entitiesIn(ServerLevel level, Telegraph.Shape shape)   // quem está dentro agora
int  Telegraph.color(Element e)                      // cor RGB do elemento
```
Cores prontas (RGB): `Telegraph.DANGER` 0xFF3B30 (vermelho), `WARNING` 0xFF9F1C (laranja), `ARCANE` 0xB98CFF, `HOLY` 0xFFE27A, `VOID` 0x9B4DFF, `FROST` 0x8FE3FF, `SAFE` 0x4CFF7A (verde). Ou `Telegraph.color(Element.FIRE)`.

### `Telegraph.Shape` (fluente)
```java
Telegraph.Shape.circle(Vec3 center, float radius)
Telegraph.Shape.ring(Vec3 center, float inner, float outer)
Telegraph.Shape.line(Vec3 from, Vec3 to, float width)
Telegraph.Shape.cone(Vec3 origin, float yawDeg, float angleDeg, float range)
Telegraph.Shape.follow(Entity target, float radius)
Telegraph.Shape.safe(Vec3 center, float radius)            // verde, sem flash

.warmup(int ticks)          // ticks até o golpe (padrão 20, mín. 1)
.hold(int ticks)            // continua desenhada (cheia, pulsando) depois do golpe — perigo que fica (padrão 0)
.color(int rgb)             // padrão Telegraph.DANGER
.owner(Entity owner)        // some sozinha (sem callback) se o dono morrer/sumir; o dono nunca entra na lista de atingidos
.lock(int ticks)            // só follow: ticks finais travado (padrão 10)
.vertical(double below, double above)   // faixa vertical do acerto relativa ao Y (padrão 1,5 abaixo / 3,0 acima)
.flat()                     // desenha reto na Y dada (não deita no terreno)
.noFlash()                  // sem flash final (avisos)
.noRunes()                  // sem runas/setas e sem a linha decorativa (formas pequenas ou muitas de uma vez)

boolean shape.contains(Entity e)   // teste de acerto (o mesmo que o callback usa)
Vec3 shape.center()                 // linha: ponto médio
```
`show`/`delayed` copiam a Shape: pode reaproveitar o objeto.

### Quem entra na lista do callback
- `LivingEntity` viva, **não espectadora**, cuja caixa de colisão encosta na forma (raio + meia largura da entidade) e cruza a faixa vertical.
- A área de acerto é EXATAMENTE a desenhada: círculo, anel (miolo seguro), setor do cone, e na linha um **retângulo** de `from` a `to` (sem pontas arredondadas — quem está logo depois da ponta não é atingido). A faixa vertical da linha acompanha o Y de `from` a `to`.
- O `owner` nunca entra. **Aliados do chefe entram** (outros mobs, invocações) — filtre no callback.
- A lista é nova (pode alterar). O callback roda no fim do tick do servidor exatamente `warmup` ticks depois (o enchimento no cliente termina junto). Se o dono morreu antes, não roda. Exceções no callback são registradas no log e não derrubam o servidor.
- Não use para dano contínuo: o callback roda UMA vez. Para zonas que ficam, use `hold` + `entitiesIn` (exemplo 1.7).

### Tempos recomendados (design §6)
| Ataque | warmup |
|---|---|
| Golpe corpo a corpo (cone curto) | 8–10 ticks |
| Área grande / linha / investida | ≥ 10 (normalmente 16–30) |
| Meteoro / marca no jogador (`follow`) | 30–60 (trava nos últimos 10) |
| Ultimate / fase | 40–80 |

---------------------------------------------------------------------------------------------------
Nos exemplos, `isFoe(e)` é o filtro de inimigos do SEU chefe (ex.: `e instanceof Player p && !p.isCreative()`, ou o `isFoe` do `BbBoss`).

## 1.1 Como converter um ataque: área instantânea → explosão atrasada

**Antes (indesviável):**
```java
for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), this::isFoe)) {
    e.hurt(damageSources().mobAttack(this), 12F);
}
```
**Depois (telegrafado, justo):**
```java
if (level() instanceof ServerLevel sl) {
    float dmg = 12F * BossKit.damageScale(this);                  // escala de grupo para dano próprio
    Telegraph.delayed(sl, Telegraph.Shape.circle(position(), 8F).warmup(24).color(Telegraph.DANGER).owner(this), hits -> {
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;                              // o callback recebe aliados também
            e.hurt(damageSources().mobAttack(this), dmg);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
        BossKit.shake(sl, position(), 24, 1.5F, 16);
    });
}
```
Atalho equivalente sem dono: `Telegraph.delayedBlast(sl, position(), 8F, 24, Telegraph.DANGER, hits -> {...})`.

## 1.2 Cone curto antes de um golpe corpo a corpo
Opção A — o chefe mantém o tempo no próprio tick (bom quando a animação já tem o frame do impacto):
```java
// tick 0 do golpe (o chefe para de girar durante o aquecimento!)
coneId = Telegraph.show(sl, Telegraph.Shape.cone(position(), getYRot(), 100F, 4.5F)
        .warmup(10).color(Telegraph.DANGER).owner(this).noRunes());
// tick 10 do golpe: acerta quem AINDA está no cone e pisca exatamente agora
Telegraph.Shape cone = Telegraph.Shape.cone(position(), getYRot(), 100F, 4.5F);
for (LivingEntity e : Telegraph.entitiesIn(sl, cone)) if (isFoe(e)) doHurtTarget(e);
Telegraph.finish(sl, coneId);
```
Opção B — deixa o kit cuidar do tempo:
```java
Telegraph.delayed(sl, Telegraph.Shape.cone(position(), getYRot(), 100F, 4.5F).warmup(10).owner(this).noRunes(),
        hits -> hits.stream().filter(this::isFoe).forEach(this::doHurtTarget));
```

## 1.3 Linha antes de uma investida
```java
Vec3 from = position();
Vec3 dir = target.position().subtract(from).multiply(1, 0, 1).normalize();
Vec3 to = from.add(dir.scale(18));
Telegraph.show(sl, Telegraph.Shape.line(from, to, getBbWidth() + 1.5F).warmup(16).color(Telegraph.WARNING).owner(this));
// ...no tick 16 o chefe corre de "from" até "to" (mesma direção travada) e acerta o que encostar.
```
Rajada de vento/raio em linha com dano no callback: `Telegraph.delayedLine(sl, from, to, 3F, 20, Telegraph.FROST, hits -> ...)`.

## 1.4 Marca que persegue o jogador (meteoro)
```java
for (ServerPlayer p : BossKit.partyAround(this, 32)) {
    Telegraph.delayed(sl, Telegraph.Shape.follow(p, 3F).warmup(50).color(Telegraph.color(Element.FIRE)).owner(this),
            hits -> hits.forEach(e -> { if (isFoe(e)) e.hurt(damageSources().mobAttack(this), 14F); }));
}
```
Para saber ONDE travou (efeito de impacto no ponto certo), guarde o id e chame `Telegraph.centerOf(id)` dentro do callback:
```java
int[] id = new int[1];
id[0] = Telegraph.delayedFollow(sl, p, 3F, 50, Telegraph.DANGER, hits -> {
    Vec3 at = Telegraph.centerOf(id[0]);          // ponto travado (não é null dentro do callback)
    spawnMeteorImpact(sl, at, hits);
});
```

## 1.5 Onda de choque que dá para pular
```java
Telegraph.delayed(sl, Telegraph.Shape.ring(position(), 5F, 9F).warmup(20).vertical(0.5, 1.0).owner(this), hits -> {...});
// vertical(0.5, 1.0): quem está pulando (pés > 1 bloco acima do chão) não é atingido.
```

## 1.6 Zona segura ("fique no círculo verde")
```java
Vec3 spot = arenaPoint();
Telegraph.safe(sl, spot, 3F, 60);
BossKit.schedule(sl, 60, () -> {
    Telegraph.Shape zone = Telegraph.Shape.safe(spot, 3F);
    for (ServerPlayer p : BossKit.partyAround(this, 40)) {
        if (!zone.contains(p)) p.hurt(damageSources().magic(), 18F);
    }
});
```

## 1.7 Perigo que fica no chão (hold + dano periódico)
```java
Vec3 pool = target.position();
Telegraph.show(sl, Telegraph.Shape.circle(pool, 4F).warmup(20).hold(100).color(0xFF5A1F).owner(this));
for (int t = 20; t <= 120; t += 10) {
    BossKit.schedule(sl, t, () -> {
        if (!isAlive()) return;
        for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(pool, 4F))) if (isFoe(e)) e.igniteForSeconds(3);
    });
}
```

## 1.8 Cancelar quando o chefe é interrompido
```java
// com .owner(this) a forma some sozinha se o chefe morrer. Para atordoamento/troca de fase:
Telegraph.cancelAll(this);
```

---------------------------------------------------------------------------------------------------
## 2. `BossKit`

```java
String  BossKit.TRIAL_TAG = "isekai_mahou.trial_boss"      // tag de entidade (entity.addTag)
boolean BossKit.isTrialBoss(Entity e)
void    BossKit.markTrialBoss(Entity e)                       // = e.addTag(TRIAL_TAG)

void    BossKit.setArena(Entity boss, Vec3 center, double radius)   // grava no persistentData ("isekai_mahou.arena")
@Nullable Vec3 BossKit.arenaCenter(Entity boss)               // null = sem arena (use o ponto de spawn)
double  BossKit.arenaRadius(Entity boss)                      // 0 = sem arena

List<ServerPlayer> BossKit.partyAround(Entity center, double radius)   // vivos, não-espectadores, mesmo nível
int     BossKit.scaleToParty(Mob boss, double radius)         // devolve n (no cliente não faz nada e devolve partySize)
int     BossKit.partySize(Entity boss)                        // n gravado (1 se não escalou)
float   BossKit.damageScale(Entity boss)                      // 1 + 0.15·(n−1) — para dano PRÓPRIO

boolean BossKit.isUnreachable(Mob mob, @Nullable LivingEntity target)
int     BossKit.leapAt(Mob mob, LivingEntity target, double power)     // devolve ticks de voo
int     BossKit.leapTo(Mob mob, Vec3 dest, double power)
boolean BossKit.blinkNear(Mob mob, LivingEntity target, double dist)

void    BossKit.roar(Mob mob)
void    BossKit.roar(Mob mob, SoundEvent sound, float pitch)
void    BossKit.shake(ServerLevel level, Vec3 pos, double radius, float intensity, int ticks)
void    BossKit.schedule(ServerLevel level, int delayTicks, Runnable action)
```

### Modo provação
- O módulo `trials` chama `BossKit.markTrialBoss(boss)` e `BossKit.setArena(boss, centroDaArena, raio)` ao invocar.
- O chefe, se `BossKit.isTrialBoss(this)`: **não reseta por ociosidade** e usa `BossKit.arenaCenter(this)` como casa/leash (raio `arenaRadius`).

### Escala de grupo — `scaleToParty`
n = `partyAround(boss, radius).size()` (mín. 1). Vida máx. ×(1 + 0,6·(n−1)) e `ATTACK_DAMAGE` ×(1 + 0,15·(n−1)) via modificadores permanentes `isekai_mahou:party_health` / `isekai_mahou:party_damage` (operação `ADD_MULTIPLIED_TOTAL`), cura até o máximo. **Só aplica uma vez** por entidade (grava `isekai_mahou.party_size` no persistentData; chamadas seguintes só devolvem n). Chame na intro:
```java
private void startIntro() {
    BossKit.scaleToParty(this, 48);
    BossKit.roar(this);
    ...
}
```
Danos que não usam `ATTACK_DAMAGE` (magias, ondas, projéteis próprios): multiplique por `BossKit.damageScale(this)`.

### Anti-trapaça — `isUnreachable` + `leapAt` / `blinkNear`
`isUnreachable` é verdadeiro quando: o alvo está no ar alto (elytra, voo criativo, levitação, > 3 blocos do chão) acima de um mob que não voa; ou está num barco/carrinho na água com o mob em terra; ou **não há caminho** até ele (pilar, buraco, mob grande demais para o túnel). O teste de caminho é refeito no máx. a cada 20 ticks por mob (barato). Alvo além do `FOLLOW_RANGE` = "longe", não inalcançável. Dentro do alcance corpo a corpo = sempre alcançável. No cliente sempre devolve `false` (chame no servidor).

`leapAt(mob, alvo, power)`: salto balístico até a posição ATUAL do alvo (desviável). `power` 1 = normal, >1 mais rápido/baixo, <1 mais lento/alto. A velocidade é calculada com a física exata do `LivingEntity` (inclusive o atrito do chão no tick da decolagem), então o mob pousa no ponto mirado se nada o empurrar no caminho. Se o mob estiver montado (barco/carrinho), desmonta antes. Devolve os ticks até o pouso; o mob não leva dano de queda desse salto. Alvo mais alto (pilar): o arco passa acima do topo e cai em cima. Alvo MUITO alto e colado (dy > 6 a menos de 3 blocos na horizontal) bate na lateral — use `blinkNear`. Telegrafe o pouso com o número devolvido:
```java
int unreachableTicks;
void antiCheese(ServerLevel sl, LivingEntity target) {
    unreachableTicks = BossKit.isUnreachable(this, target) ? unreachableTicks + 1 : 0;
    if (unreachableTicks < 40) return;                 // 2 s preso → reage
    unreachableTicks = 0;
    if (onGround() && distanceTo(target) < 20) {
        int t = BossKit.leapAt(this, target, 1.0);
        Telegraph.delayedBlast(sl, target.position(), 3.5F, t, Telegraph.WARNING, hits -> slamLanding(hits));
    } else if (!BossKit.blinkNear(this, target, 4)) {
        fireProjectileAt(target);                      // último recurso: ataque à distância
    }
}
```
Durante o salto, pare a navegação/goals de movimento (o kit já chama `getNavigation().stop()`), senão o `MoveControl` atrapalha. Voadores com `MoveControl` próprio que sobrescreve a velocidade: prefira `blinkNear`.

`blinkNear(mob, alvo, dist)`: teleporta para ~`dist` blocos do alvo, num chão firme de até 2 blocos acima a 10 abaixo do pé do alvo (voadores: no ar, um pouco acima do alvo), sem colisão nem líquido, dentro da borda do mundo, preferindo ponto com linha de visão (nunca em cima do teto de uma sala baixa). Desmonta de barco/carrinho antes. Partículas de portal + som na saída e na chegada, vira o rosto para o alvo. Devolve `false` se não achou lugar (24 tentativas) — aí use um ataque à distância.

### Presença
- `roar(mob)`: rugido (`RAVAGER_ROAR`, mais grave quanto maior o mob), anel de poeira, empurrão leve (sem dano) nos jogadores a ≤ 6 + largura, e tremor de câmera (raio 28, intensidade 1,2, 24 ticks).
- `shake(level, pos, radius, intensity, ticks)`: tremor de câmera para jogadores a ≤ `radius`; diminui com a distância (suave até a borda do raio) e com o tempo. `intensity` ≈ graus de balanço perto do centro: 0,5 leve · 1,5 forte · 3 máx. (teto). Sempre ativo (não depende da opção de cutscenes); com "partículas reduzidas" vale a metade. Vários tremores somam (teto 3).
- `schedule(level, ticks, Runnable)`: roda depois de N ticks (no fim do tick do servidor); descartado se o servidor parar ou o nível descarregar. Confira `isAlive()` do chefe dentro da tarefa.

---------------------------------------------------------------------------------------------------
## 3. `BossRewards`

```java
List<ServerPlayer> BossRewards.grant(LivingEntity boss, int xp, double radius)
boolean BossRewards.wasRewarded(LivingEntity boss)
int BossRewards.ASSIST_WINDOW = 600   // 30 s
```
- Recompensa **todos que lutaram**: jogadores no mesmo nível a ≤ `radius` (inclusive espectadores — jogadores "Abatidos" na provação) **+** quem causou dano ao chefe nos últimos 30 s (mesmo longe; dono de invocação/pet conta; projéteis contam para o atirador). O núcleo rastreia o dano sozinho em alvos com vida máx. ≥ 60, com `TRIAL_TAG` ou na tag `c:bosses`.
- Para cada jogador, uma vez: `MagicApi.giveXp(p, xp, "boss")` + `MagicApi.addCounter(p, "boss.<path do id da entidade>", 1)` (ex.: `boss.ignis`) + mensagem na barra de ação.
- **Paga uma vez por chefe** (grava `isekai_mahou.boss_rewarded`); chamadas repetidas devolvem lista vazia.
- Use no `die()` do chefe **no lugar** do contador `boss.<id>` manual e do `giveXp` manual (senão conta em dobro):
```java
@Override
public void die(DamageSource source) {
    super.die(source);
    if (!level().isClientSide) BossRewards.grant(this, 250, 48);
}
```

---------------------------------------------------------------------------------------------------
## 4. Comandos de teste (op)
- `/mahou boss telegraph` — demonstra TODAS as formas em sequência ao redor de você (uma a cada 25 ticks; sem dano; as com callback avisam no chat quantos alvos estavam dentro).
- `/mahou boss telegraph <circle|ring|line|cone|follow|safe|blast|line_blast>` — uma forma só.
- `/mahou boss shake [intensidade 0.1–3]` — testa o tremor de câmera.

## 5. Detalhes técnicos (para quem for depurar)
- Pacotes (registrar `core_boss-1`): `core_boss_telegraph`, `core_boss_telegraph_cancel`, `core_boss_telegraph_lock`, `core_boss_shake` (todos servidor → cliente).
- Tempo no servidor = `gameTime` do nível (para junto com `/tick freeze`); agendador no `ServerTickEvent.Post`, limpo no `ServerStoppedEvent` e no `LevelEvent.Unload`.
- Desenho: `RenderLevelStageEvent` estágio `AFTER_PARTICLES`, `RenderType`s translúcidos sem cull, sem escrever profundidade, com polygon offset, saída no alvo de partículas (funciona em Rápido/Bonito/Fabuloso e com Iris). Texturas `textures/misc/telegraph_runes.png` e `telegraph_chevron.png` (geradas por `tools/gen/core_boss.py`, brancas e tingidas pela cor); se faltarem, o desenho usa só cores.
