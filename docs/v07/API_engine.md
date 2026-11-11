# API v0.7 — Motor de glifos v2 (trilha A1, pacote `glyph/`)

Complementa `docs/V07_DESIGN.md` §2 e `docs/ARCHITECTURE.md` §3. Tudo aqui é **aditivo**: código v0.6 compila e se comporta igual
(as exceções estão em §10).

Arquivos: `glyph/ParsedSpell.java`, `glyph/SpellParser.java`, `glyph/SpellContext.java`, `glyph/SpellCaster.java`,
`glyph/FormGlyph.java`, `glyph/SpellStats.java`, `glyph/CoreGlyphs.java`, `glyph/LinkGlyph.java`, `glyph/entity/SpellProjectileEntity.java`.
Traduções: `src/main/lang/core_engine/`.

---------------------------------------------------------------------------------------------------
## 1. Gramática (magias programáveis)

```
[TRIGGER...] STAGE (LINK STAGE)* [LINK_LEAP] [LAW...]
STAGE = FORM [AUGMENT...] (EFFECT [AUGMENT...])*
```

- `SpellRecipe` continua sendo uma lista PLANA de ids (codecs iguais). As ligações dentro dela separam os estágios.
- `LinkGlyph extends TriggerGlyph` (kind continua `GlyphKind.TRIGGER`, `isPlaceable()==true`, `isLink()==true`).
  Para distinguir no Grimório: `g instanceof LinkGlyph link` → `link.linkKind()` (`HIT`, `END`, `KILL`, `LEAP`).
- Ligações do núcleo (ARCANE):

| id | nome PT | custo | tier | quando o próximo estágio nasce |
|---|---|---|---|---|
| `link_hit` | Ao Acertar | 4 | 2 | no ponto de impacto (entidade ou bloco), na direção do estágio anterior |
| `link_end` | Ao Terminar | 4 | 2 | onde o estágio anterior terminou (projétil expirou, zona acabou...) |
| `link_kill` | Ao Abater | 6 | 3 | onde um alvo morreu pelo dano deste estágio |
| `link_leap` | Saltar | 10 | 3 | **terminal**: repete o estágio a partir do alvo atingido (ver §7) |

Constantes: `CoreGlyphs.LINK_HIT`, `LINK_END`, `LINK_KILL`, `LINK_LEAP`, `CoreGlyphs.RELENTLESS` (`ResourceLocation`).

Regras do parser:
- Gatilhos comuns (`on_hurt`, `then`...) só no começo (antes da 1ª forma), como antes.
- Uma ligação fecha o estágio atual; o próximo glifo tem que ser uma FORMA. Uma 2ª forma SEM ligação antes → `form_needs_link`.
- Estágios 2+: só formas com `stageable()==true`, que não sejam ultimate nem aceitem leis; cada estágio precisa de ≥1 efeito.
- `link_leap` só no fim (depois dele, só leis) e exige que a forma do estágio seja `stageable()`.
- Leis só no FIM da receita (depois do último estágio) e só se a forma do ESTÁGIO 0 aceita leis (Domínio). Elas ficam em
  `rootSpell().laws()` e valem para o estágio 0. Ex. válido: `domain_expansion, ignite, link_kill, projectile, ignite, law_x`.
- Estágio 0 pode ser QUALQUER forma (inclusive não-stageable); veja o "plano B" do núcleo em §6.2.

Erros (`Component.translatable("spell_error.isekai_mahou.<chave>", args)`):

| chave | quando | args |
|---|---|---|
| `link_first` | ligação antes de qualquer forma | nome da ligação |
| `link_needs_form` | depois de uma ligação veio efeito/aumento/outra ligação/lei, ou a receita acabou | nome da ligação anterior |
| `form_needs_link` | 2ª forma sem ligação antes dela (substitui `two_forms`, que não é mais emitido) | nome da forma |
| `stage_form_not_allowed` | forma não-stageable/ultimate/de leis num estágio 2+, ou Saltar numa forma não-stageable | nome da forma |
| `stage_no_effect` | um estágio sem efeito | nº do estágio (1 = primeiro) |
| `leap_not_last` | algo (exceto lei) depois de `link_leap` | — |
| `too_many_elements` | elementos distintos não-Arcanos > `Progression.maxSpellElements(circle)` | (qtd, máx) |
| `too_many_glyphs` | glifos > `Progression.maxGlyphs(level, circle)` (substitui `too_many`, que não é mais emitido) | (qtd, máx) |

Os erros antigos continuam iguais (`empty`, `unknown`, `not_known`, `circle`, `wrong_element`, `trigger_after_form`, `trigger_not_placeable`,
`no_form_first`, `effect_after_law` (também: forma ou ligação depois de uma lei), `augment_alone`, `law_needs_domain`, `no_form`, `no_effect`,
`ultimate_circle`). `two_forms` e `too_many` continuam traduzidos, mas o parser não os emite mais.

---------------------------------------------------------------------------------------------------
## 2. `ParsedSpell`

```java
public record ParsedSpell(List<TriggerGlyph> triggers, FormGlyph form, SpellStats formStats, List<EffectEntry> effects,
                          List<LawGlyph> laws, int manaCost, int castTicks, int cooldown, int glyphCount,
                          @Nullable Stage next, Set<Element> elements) {
    public record EffectEntry(EffectGlyph effect, SpellStats stats) {}
    public record Stage(LinkGlyph link, @Nullable ParsedSpell spell) {}
    // construtor v0.6 (9 args) continua existindo: next=null, elements=Set.of()
}
```

- O record RAIZ é o **estágio 0**: `form()`, `effects()`, `formStats()`, `laws()`, `triggers()` são do estágio 0 (código antigo não muda).
- A raiz guarda os TOTAIS: `manaCost` (soma de todos os glifos, ligações inclusas, +10%/elemento extra, Eficiente), `castTicks`, `cooldown`,
  `glyphCount` (= tamanho da receita). Estágios-filho têm `manaCost == castTicks == cooldown == 0` e `glyphCount` = glifos do estágio
  (inclui a ligação que o fecha). `triggers`/`laws` dos filhos são vazios.
- `elements()`: elementos usados pela magia inteira (o primário incluso; o MESMO conjunto em todos os estágios; ordem do enum; nunca vazio vindo do parser).

Helpers:

| método | retorno |
|---|---|
| `hasNext()` | existe próximo estágio de verdade (Ao Acertar/Terminar/Abater). `link_leap` NÃO conta |
| `leap()` | este estágio termina em `link_leap` |
| `linkKind()` | `@Nullable LinkGlyph.LinkKind` da ligação que sai deste estágio |
| `next()` | `@Nullable Stage`. Com Saltar: `next().link()` é o Saltar e `next().spell()` é **null** |
| `stageCount()` | nº de estágios a partir deste (1 = magia simples) |
| `stage(int i)` | `@Nullable ParsedSpell` (0 = este) |
| `stages()` | `List<ParsedSpell>` em ordem (para desenhar o "programa") |
| `anyStats(Predicate<SpellStats>)` | algum aumento (forma ou efeito) em qualquer estágio satisfaz |
| `isUltimate()`, `hasLaw(path)`, `hasTrigger(path)` | iguais à v0.6 (estágio 0) |

```java
// Grimório: desenhar o programa
ParsedSpell p = SpellParser.parse(recipe, element, data).spell();
for (ParsedSpell st : p.stages()) {
    drawForm(st.form()); st.effects().forEach(e -> drawEffect(e.effect()));
    if (st.hasNext()) drawArrow(st.next().link());        // Ao Acertar / Ao Terminar / Ao Abater
    else if (st.leap()) drawLoop(st.next().link());      // Saltar
}
```

---------------------------------------------------------------------------------------------------
## 3. `SpellParser`

```java
public static SpellParser.Result parse(SpellRecipe recipe, @Nullable Element element, @Nullable MagicData data)
public static boolean canUse(@Nullable Element slotElement, Glyph g, @Nullable MagicData data)
public static final float EXTRA_ELEMENT_COST = 0.10F;   // +10% de custo por elemento extra
```

- `element` = elemento PRIMÁRIO (o do slot). `null` = mob: sem regra de elemento e sem acréscimo de custo.
- `data` = jogador; `null` = mob/criativo: não checa conhecimento, Círculo, despertar, nº de glifos nem nº de elementos.
- Limite de glifos: `Progression.maxGlyphs(data.level(), data.circle())`.
- `canUse(slot, g, data)` — **só a regra de elemento** (não checa conhecimento nem Círculo):
  true se `slot == null`, ou o glifo é de Origem, ou `slot.canUseGlyphOf(g.element())` (Arcano, mesmo elemento, pais da fusão),
  ou `data == null`, ou `data.isAwakened(g.element())`. Use no Grimório no lugar de `gl.origin()==null && !el.canUseGlyphOf(gl.element())`:

```java
boolean bad = !d.knowsGlyph(id) || d.circle() < gl.minCircle() || !SpellParser.canUse(el, gl, d);
```

- Elementos "extras" = elementos não-Arcanos de glifos (sem Origem) que o primário não cobre sozinho. Cada um: +10% no custo (só com `element != null`).
- `elements()` = {primário} ∪ extras (mob sem primário: os elementos dos glifos, ou {ARCANE}).
- Máximo de elementos distintos não-Arcanos: `Progression.maxSpellElements(circle)` (2 em C1–3, 3 em C4–6, 4 em C7+).

---------------------------------------------------------------------------------------------------
## 4. Multi-elemento em jogo

- O elemento do SLOT é o primário: carga de ultimate, HUD, recarga, slot G — tudo igual.
- Conjurações de JOGADOR (`performCast`, inclusive as automáticas do módulo arcane) marcam o contexto (`ctx.isPlayerCast()`).
  Nelas, cada EFEITO cujo elemento o primário não cobre roda numa **visão** do contexto com `element() = elemento do glifo`:
  `ctx.damage(...)`, reações, `ctx.color()`, `ElementFx` com `ctx.element()` e as lambdas agendadas pelo efeito usam o elemento certo.
  A visão compartilha alvos atingidos, orçamento e disparos de ligação com o contexto da forma.
- A carga de ultimate gerada pelo dano de uma visão vai para o elemento PRIMÁRIO (`ctx.primaryElement()`).
- `kill.spell.<e>` segue o elemento do dano (como antes). XP de elemento da conjuração: `2 + custo/4` dividido igualmente entre
  `spell.elements()` (mín. 1 cada) e contador `cast.<e>` +1 para CADA elemento usado. XP de nível: `2 + custo/6`.
- Mobs (`castAsMob`) e `castFrom` NÃO criam visões (chefes que já misturam elementos continuam iguais).

---------------------------------------------------------------------------------------------------
## 5. `SpellContext` — API nova

Constantes públicas: `MAX_CHILD_STAGES = 24`, `MAX_LEAPS = 64`, `LEAP_RANGE = 16.0`, `RELENTLESS_RANGE = 8.0`,
`RELENTLESS_INTERVAL = 3` (ticks), `RELENTLESS_FALLOFF = 0.92F`, `RELENTLESS_MAX_HOPS = 64`, `MOB_HOP_CAP = 8`,
`PREVIOUS_TARGET_TICKS = 10`.

| método | o que faz |
|---|---|
| `Vec3 eyePos()` / `Vec3 look()` | origem/direção do ESTÁGIO se definidas; senão olhos/olhar do conjurador (v0.6) |
| `boolean hasOrigin()` | o contexto tem origem própria (estágio 2+, salto, `castFrom` com origem/direção, `withOrigin`) |
| `@Nullable Vec3 anchor()` | ponto de impacto que gerou o estágio (para formas de ÁREA) |
| `int stage()` | índice do estágio (0 = primeiro) |
| `ParsedSpell spell()` | o ESTÁGIO executado por este contexto (no 0, a raiz) |
| `ParsedSpell rootSpell()` | a magia inteira (custos totais) |
| `@Nullable UUID previousTarget()` / `boolean ignores(Entity)` | entidade que o estágio anterior ACABOU de atingir — formas de disparo a ignoram. Vale só na hora em que o estágio nasce: até `PREVIOUS_TARGET_TICKS` (10) ticks depois de a forma rodar (Atraso/Eco da forma somados); depois, `null`/`false` (uma torre/zona criada como estágio volta a atingir esse inimigo) |
| `void impact(Vec3 pos, Vec3 dir, @Nullable Entity hit)` | impacto REAL: dispara `link_hit` / `link_leap` (hit = null para bloco) |
| `void end(Vec3 pos, Vec3 dir)` | o estágio terminou: dispara `link_end` |
| `void applyEffect(ParsedSpell.EffectEntry, Entity)` | aplica UM efeito (sem checar aliado/inimigo), com Atraso/Eco e visão por elemento |
| `SpellContext forElement(Element)` | visão com outro elemento (compartilha estado) |
| `SpellContext copy(float powerMult)` | nova instância do mesmo estágio com poder ABSOLUTO `powerMult` |
| `SpellContext withOrigin(Vec3 origin, Vec3 dir)` | nova instância do mesmo estágio saindo de outro ponto (`hasOrigin()` true; `anchor()` vira null, para formas de área mirarem da nova origem) |
| `boolean payHop(float cost)` | paga um salto: jogador = mana (criativo grátis, offline falha); mob/castFrom = contador até 8 |
| `float hopCost()` | `max(2, 15% de rootSpell().manaCost())` |
| `void markPlayerCast()` | marca como conjuração de jogador (visões + saltos pagos com mana). O núcleo já faz em `performCast`; use só ao RECRIAR um contexto do jogador (ex.: `SealHandler`). Sem efeito se o conjurador é um mob (não-`Player`) |
| `boolean isPlayerCast()`, `boolean isView()`, `Element primaryElement()` | informações |

Tudo o que já existia (`resolveOnEntity`, `resolveArea`, `damage`, `save/load`, construtor público de 5 args...) continua com a mesma assinatura.

Regras internas que você não precisa reimplementar:
- `impact`/`end`/morte disparam o próximo estágio **1 tick depois** via `SpellCaster.runForm(child)` (Atraso/Eco da forma-filho valem),
  **sem** `SpellCastEvent` (não reaplica foco/gatilhos; Domínios inimigos não anulam filhos).
- Cada INSTÂNCIA de estágio dispara no máx. `max(1, formStats().count())` vezes; a conjuração inteira cria no máx. 24 estágios-filho.
- O mesmo alvo informado duas vezes no mesmo tick conta uma vez (forma + núcleo podem avisar o mesmo impacto sem duplicar).
- `link_kill` é disparado dentro de `damage(...)` quando o alvo morre (também a partir de visões e de lambdas agendadas).
- Contextos derivados (estágios, saltos, visões, cópias) compartilham **por referência** o conjunto de alvos atingidos e o orçamento.
- O filho herda `powerMult`, conjurador, elemento primário e `isPlayerCast()`. Onde ele nasce:

| ligação | `eyePos()` (origem) | `look()` | `anchor()` | `previousTarget()` |
|---|---|---|---|---|
| `link_hit` em entidade | ponto exato do impacto | `dir` passado a `impact` | ponto do impacto | a entidade atingida |
| `link_hit` em bloco | ponto do impacto recuado 0.3 contra `dir` | `dir` | ponto do impacto | null |
| `link_end` | `pos` passado a `end` | `dir` (ou `look()` se zero) | `pos` | null |
| `link_kill` | centro do corpo da vítima | `look()` do estágio que matou | pés da vítima | a vítima |
| `link_leap` | centro do alvo atingido | rumo ao centro do próximo inimigo | pés do próximo inimigo | o alvo atingido |

### 5.1 `save()` / `load()`
`save()` grava também `stage`, `origin`, `dir`, `anchor`, `lastHit` + `ignoreUntil`, `fires`, `player`, `budget` e, numa visão,
`primary` (todas opcionais). `"element"` continua sendo o elemento DO contexto salvo (numa visão, o do glifo) — quem lê
`tag.getString("element")` para o visual continua certo. NBT antigo sem as chaves novas carrega como estágio 0.
Uma entidade que guarda a magia deve salvar `ctx.save()` **de novo** no `addAdditionalSaveData` (se tiver o contexto em memória)
para não "esquecer" os disparos já feitos — o projétil do núcleo faz isso.
`load` reconstrói o estágio certo (`rootSpell().stage(n)`), nunca falha por elemento e, se o tag veio de uma visão, devolve a visão
(mesmo `element()`, carga de ultimate para o primário).

### 5.2 Exemplo: entidade que guarda a magia
```java
private CompoundTag spellTag; private SpellContext ctx;
SpellContext context() { if (ctx == null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag); return ctx; }
@Override protected void addAdditionalSaveData(CompoundTag tag) {
    tag.put("Spell", ctx != null ? ctx.save() : spellTag);
}
```

---------------------------------------------------------------------------------------------------
## 6. Como uma FORMA suporta estágios (checklist para forms_a / forms_b / legends)

1. `@Override public boolean stageable() { return true; }` — SÓ depois de cumprir 2–5. Formas presas ao conjurador
   (self, seal, orbit, conjure, portal, aura, domínios, cataclismo) ficam `false`.
2. **Origem/direção:** use `ctx.eyePos()` e `ctx.look()` em vez de `caster.getEyePosition()`/`getLookAngle()`/`position()`.
   Formas de ÁREA (zona, nova, runa, chuva, queda, pulso, torre...): centro = `ctx.anchor()` se não for null (desça até o chão se precisar);
   senão a mira de sempre a partir de `ctx.eyePos()/look()`.
3. **Mira de mob:** pule o "olhar para `mob.getTarget()`" quando `ctx.hasOrigin()` (a direção do estágio manda).
4. **Impactos reais:** chame `ctx.impact(pontoExato, direção, entidadeOuNull)` ANTES de aplicar os efeitos, em cada acerto de verdade
   (projétil/feixe/onda/queda acertou entidade ou bloco). Pode chamar sempre: sem ligação não faz nada.
   NÃO chame em toques incidentais (raspão no chão de um corte, pulsos periódicos de zona).
5. **Fim:** chame `ctx.end(ponto, direção)` uma vez quando o estágio acaba (projétil/onda expirou, zona sumiu, feixe chegou ao alcance máximo sem acertar...).
6. **Ignorar o alvo anterior** (formas de DISPARO: projétil, feixe, cone, corte, toque): filtre `!ctx.ignores(e)` na busca de alvos
   (`SpellProjectileEntity.create(ctx, ...)` já ignora). Formas de ÁREA nascidas na âncora (zona, nova, runa, queda...) NÃO precisam
   filtrar — atingir de novo quem está no ponto de impacto é o objetivo delas. `ignores` só vale na hora (ver `previousTarget()`).
7. Visuais "da mão" (partícula na mão, `caster.swing`) só quando `!ctx.hasOrigin()`.
8. Entidades que guardam a magia: §5.2.

```java
public class BeamForm extends FormGlyph {
    @Override public boolean stageable() { return true; }
    @Override public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        Vec3 from = ctx.eyePos();
        Vec3 dir = ctx.hasOrigin() ? ctx.look() : FormAim.aimDir(ctx);   // mira de mob só sem origem
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult block = ctx.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(ctx.level(), caster, from, block.getLocation(),
                new AABB(from, block.getLocation()).inflate(1), e -> e.isPickable() && e != caster && !ctx.ignores(e));
        if (ent != null) {
            ctx.impact(ent.getLocation(), dir, ent.getEntity());
            ctx.resolveOnEntity(ent.getEntity(), null);
        } else if (block.getType() == HitResult.Type.BLOCK) {
            ctx.impact(block.getLocation(), dir, null);
            ctx.resolveOnBlock(block, null);
        } else {
            ctx.end(to, dir);
        }
    }
}

public class ZoneForm extends FormGlyph {
    @Override public boolean stageable() { return true; }
    @Override public void cast(SpellContext ctx) {
        Vec3 center = ctx.anchor() != null ? groundBelow(ctx.level(), ctx.anchor()) : FormsBUtil.aimGround(ctx, 24);
        SpellZoneEntity zone = SpellZoneEntity.create(ctx, center, ...);
        ...
    }
}
// e na entidade da zona, quando ela some:  context().end(position(), Vec3.ZERO);
```

### 6.1 O núcleo já portou
- `projectile` (stageable): nasce em `ctx.eyePos()` e voa em `ctx.look()` quando `ctx.hasOrigin()`; sem origem é idêntico à v0.6.
- `touch` (stageable): `ctx.eyePos()/look()`, `impact` no acerto (entidade/bloco), `end` se não acertar nada.
- `self`: não-stageable (não pode ser estágio 2+ nem usar Saltar). Como estágio 0 chama `end(olhos, olhar)` logo depois de aplicar:
  `self, heal, link_end, projectile, arcane_damage` cura e dispara dos olhos. `link_hit` depois de `self` nunca dispara (o alvo é você).
- `SpellProjectileEntity`: nasce na origem do contexto, ignora `ctx.previousTarget()` (se ainda valer quando é criado), chama `impact`
  ao acertar entidade/bloco (não no ricochete nem com Fantasma atravessando bloco) e `end` SÓ ao expirar (sumir sem acertar).
  Novo: `exclude(Entity)` (atravessar uma entidade). Salva no NBT ("Hit") quem já atingiu/ignora: Perfurar e o alvo anterior
  continuam valendo depois de recarregar. Torres/reflexos que usam `create(ctx, shooter)` + `setPos` continuam iguais.

### 6.2 Plano B para formas não portadas (estágio 0)
Se a forma do estágio é `stageable()==false`, o núcleo chama `impact(centro do alvo, ctx.look(), alvo)` sozinho dentro de
`resolveOnEntity` (alvo ≠ conjurador). Assim `cone, ignite, link_hit, ...` ou Orbe/Selo com Ao Acertar funcionam no estágio 0
mesmo sem a forma avisar. `resolveArea`/`resolveOnBlock` não disparam nada sozinhos. Formas stageable são responsáveis pelos próprios avisos.

---------------------------------------------------------------------------------------------------
## 7. Saltar (`link_leap`) e Insaciável (`relentless`)

**Saltar** (terminal): a cada `impact(pos, dir, entidade)` do estágio, se ainda houver disparo na instância:
procura o inimigo vivo mais próximo do alvo (≤ 16 blocos, não-aliado, ≠ conjurador) **ainda não atingido nesta conjuração**;
paga `payHop(hopCost())`; e repete o MESMO estágio (nova instância, 1 tick depois) saindo do centro do alvo em direção a ele,
com `anchor()` = pés do próximo inimigo (uma forma de área que salta cai nele) e ignorando o alvo atual.
Para quando: não há alvo novo, o mana não paga, ou 64 saltos na conjuração. Mobs/castFrom: no máx. 8 saltos pagos.
"Atingido" = passou por `resolveOnEntity`, por um salto ou por um pulo de Encadear/Insaciável (`resolveArea` não marca).
`impact` em BLOCO não salta.

**Insaciável** (aumento ARCANE, custo 12, tier 3, guardado em `SpellStats.custom` com id `isekai_mahou:relentless`;
leia com `stats.relentless()`; constante `SpellStats.RELENTLESS`). Vale para o glifo anterior:
- depois da FORMA: todos os efeitos pulam; depois de um EFEITO: só aquele efeito pula.
- Funciona sozinho (implica Encadear). Os N primeiros pulos (N = nº de `chain` no mesmo glifo) são grátis; os seguintes pagam `hopCost()`.
- Pulo a cada 3 ticks, alcance 8, poder ×0.92 por pulo (acumulado), prefere inimigos ainda não atingidos; sem eles volta a QUALQUER
  inimigo ≠ o atual ("até só sobrar um inimigo ou acabar o mana"). Teto de segurança: 64 pulos por corrente; mobs: 8 pulos pagos por conjuração.

**Encadear** (`chain`): na FORMA, igual à v0.6 (pulos instantâneos, 6 blocos, sem queda). Depois de um EFEITO agora funciona:
só aquele efeito pula N vezes (6 blocos, instantâneo). `minCircle` do `chain` agora é 1.

`minCircle` dos aumentos do núcleo (v0.7): `chain` 1; `vampiric`, `persistent`, `echo`, `homing`, `phantom` 2; `overload` 3.
Sobrecarga em QUALQUER estágio faz a magia inteira ser paga com vida.

---------------------------------------------------------------------------------------------------
## 8. `SpellCaster`

```java
@Nullable
public static SpellContext castFrom(LivingEntity caster, @Nullable Element element, SpellRecipe recipe, float power,
                                    @Nullable Vec3 origin, @Nullable Vec3 dir, boolean postEvents)
```
- Para pedestais (FakePlayer do dono), portais, chefes, lendas. Parse sem `MagicData` (sem conhecimento/Círculo/limites).
  Sem mana, sem XP, sem recarga, sem anúncio, sem som (toque o seu). Sem visões por elemento; saltos pagos = regra de mob (8).
- `origin`/`dir` null → olhos/olhar do conjurador. Se qualquer um for dado, `ctx.hasOrigin()` é true (formas portadas saem de lá).
- `postEvents=true` posta `SpellCastEvent.Pre` (cancelável → retorna null) e `Post`. Retorna o contexto (ou null se a receita é inválida).

```java
// Pedestal: conjura para baixo a partir do topo do bloco
SpellContext c = SpellCaster.castFrom(fakePlayer, Element.ARCANE, scrollRecipe, 1.0F,
        Vec3.atCenterOf(pos).add(0, 0.7, 0), new Vec3(0, -1, 0), false);
// Chefe: magia programável a partir da mão, mirando o alvo
SpellCaster.castFrom(boss, Element.FIRE, recipe, 1.5F, handPos, target.getEyePosition().subtract(handPos), true);
```

Outros: `castAsMob(mob, element, recipe, power)` igual à v0.6 (aceita receitas com estágios; sem visões; saltos = regra de mob).
`runForm(ctx)` igual. `performCast` agora marca `isPlayerCast()` e usa XP `2+custo/6` / `2+custo/4` dividido (§4).

---------------------------------------------------------------------------------------------------
## 9. Receitas de exemplo

| receita | o que faz |
|---|---|
| `beam, ignite, link_hit, zone, ignite` | feixe incendeia o alvo; no ponto atingido nasce uma zona de fogo. (Estágio 0 = feixe funciona já pelo plano B; `zone` precisa ser marcada stageable pelo forms_b.) |
| `projectile, arcane_damage, link_leap` | o projétil acerta e salta de inimigo em inimigo (cada salto = 15% do custo, mín. 2), até acabar alvo novo, mana ou 64 saltos |
| `projectile, chain, relentless, lightning_bolt` | o raio pula a cada 3 ticks (1º pulo grátis, depois pago), ficando 8% mais fraco por pulo, até sobrar um inimigo ou acabar o mana |
| `projectile, ignite, link_kill, touch, arcane_damage` | quem morrer queimado solta um toque arcano a partir do corpo |
| `projectile, split, split, push, link_end, projectile, arcane_damage` | 3 projéteis; cada um que EXPIRAR sem acertar lança outro dali (até 3 disparos na instância) |
| `touch, push, link_hit, projectile, arcane_damage` | toque empurra; do ponto do toque sai um projétil na mesma direção, ignorando o alvo empurrado |

---------------------------------------------------------------------------------------------------
## 10. Compatibilidade e o que mudou de comportamento

- `ctx.spell()` num estágio 2+ é o ESTÁGIO (custo 0) → `FormsBUtil.refund` não devolve mana de filhos (correto).
- `Progression.maxGlyphs(circle)` (deprecated) ainda existe; o parser usa `maxGlyphs(level, circle)` e o erro `too_many_glyphs`.
- Duas formas sem ligação: erro `form_needs_link` (antes `two_forms`). Leis podem vir depois de estágios ligados se o estágio 0 é Domínio.
- Criativo (`data == null`) agora pode misturar elementos livremente.
- `SpellContext.load` faz o parse com o elemento salvo (só muda o custo informado; nunca invalida).
- Para copiar um contexto, prefira `ctx.copy(poder)` / `ctx.withOrigin(...)` a `new SpellContext(..., ctx.spell())`: a cópia mantém estágio,
  origem, visões e orçamento. (O construtor de 5 args continua funcionando; com um estágio-filho ele descobre o índice pela receita.)
- Aplicar efeito direto (`entry.effect().onEntity(ctx, alvo, stats)`) ignora a visão por elemento e Atraso/Eco: use `ctx.applyEffect(entry, alvo)`
  (ex.: `FormsBUtil.applyBeneficialToCaster`, `DomainDomeEntity.applyBeneficialToOwner`).
- `ElementFx`/dano com `ctx.element()` dentro de um EFEITO já sai com o elemento do glifo; dentro de uma FORMA é o primário.

---------------------------------------------------------------------------------------------------
## 11. Traduções (`src/main/lang/core_engine/`)
`glyph.isekai_mahou.{link_hit,link_end,link_kill,link_leap,relentless}` (+ `.desc`): Ao Acertar, Ao Terminar, Ao Abater, Saltar, Insaciável.
`spell_error.isekai_mahou.{link_first,link_needs_form,form_needs_link,stage_form_not_allowed,stage_no_effect,leap_not_last,too_many_elements,too_many_glyphs}`.
Ícones dos 5 glifos novos: fase C (`art`).
