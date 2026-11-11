# Isekai Mahou v0.7 — "Provações": design e contrato

Este documento complementa `docs/ARCHITECTURE.md` (que continua valendo: regras de módulo, padrões 1.21.1, catálogo de ids v0.6).
Leia os dois. Em conflito, ESTE documento vence.

Mapeamentos do código v0.6 (leitura útil, feitos por agentes de pesquisa):
`C:\Users\renan\AppData\Local\Temp\claude\a85216ce7956359b\3f5ad9af-2628-4cd6-a069-6c670d6a919e\scratchpad\v07\map_*.md`
(`map_engine.md` motor de glifos/formas · `map_progression.md` XP/caminhos/ascensão · `map_bosses.md` chefes · `map_world.md` dimensões/teleporte/ondas ·
`map_quests.md` diário/guilda/HUD · `map_ui.md` menu/Grimório · `map_gear.md` equipamentos · `map_catalog.md` catálogo de glifos + magias de anime).

---------------------------------------------------------------------------------------------------
## 0. O que o jogador pediu (feedback da v0.6)

1. Glifos e abas feios → **ícones de verdade** (imagens) para cada glifo, elemento e aba; menu bonito.
2. Upar muito difícil → **progressão mais fácil e recompensadora**. **Câmaras de Provação**: a cada rank elemental o jogador é
   levado a uma **recepção** (amigos entram junto), depois a uma **arena** com **ondas e chefes**, e depois **volta**.
3. Faltam objetivos → **rastreador de objetivos na tela**, rota principal com passos menores, setas para onde ir.
   Mais **glifos por magia conforme o nível**. **Magias programáveis**: Feixe → AO ACERTAR → Zona no ponto atingido;
   corrente que **pula de inimigo em inimigo até acabar os inimigos ou o mana**.
4. Seleção de elemento no Grimório ruim → refazer.
5. **Magias com vários elementos** (sei Fogo e Luz → uma magia usa os dois).
6. **Magias lendárias de anime** possíveis de montar (homenagens, não cópias): Explosão (Megumin), Portão do Tesouro
   (Gilgamesh), Vazio Roxo (Gojo), Ruína Carmesim (Rias), Santuário Maligno (Sukuna), Megido (Rimuru), Esfera Espiral,
   Raio Assassino (Zoltraak), Lei Sagrada (Fairy Law), Rugido do Dragão, Campo de Lâminas (UBW).
7. **Vida/armadura crescem** com o nível + **armaduras e armas novas** como recompensa de progressão.
8. **Ver a vida dos inimigos** ao olhar (+ mods facilitadores: o coordenador instala Jade/JEI/AppleSkin/Xaero/Mouse Tweaks).
9. **Magia útil/farmável**: chuva, colheita, e um **Pedestal Rúnico** que recebe uma magia e a executa sozinho numa área
   (colheita automática, farm de mobs/ferro...).

FOCO do jogador: **progressão, mapas (as câmaras) e chefes** — chefes DIFÍCEIS, bem animados, telegrafados (justos), e upar gostoso.

---------------------------------------------------------------------------------------------------
## 1. Fases de implementação

- **Fase A (núcleo, agentes com dono de arquivos disjuntos):** A1 motor de glifos v2 · A2 progressão/APIs/UI do núcleo · A3 kit de chefes.
- **Fase B (módulos em paralelo, cada um com implementador + revisor):** ver §12 (lista de módulos e donos).
- **Fase C (coordenador):** build completo, ícones dos glifos novos, testes automáticos, deploy.

Na fase B o núcleo da fase A já existe. A documentação da API nova está em `docs/v07/API_*.md` (escrita pela fase A) — LEIA.

---------------------------------------------------------------------------------------------------
## 2. Motor de glifos v2 (A1 — pacote `glyph/`)

### 2.1 Gramática
```
[TRIGGER...] STAGE (LINK STAGE)* [LINK_LEAP] [LAW...]
STAGE = FORM [AUGMENT...] (EFFECT [AUGMENT...])*
```
- `LinkGlyph extends TriggerGlyph` (kind continua `TRIGGER`, `isPlaceable()=true`; `isLink()=true`), com `LinkKind { HIT, END, KILL, LEAP }`.
- Glifos de ligação do núcleo (ARCANE):
  | id | nome PT | custo | tier | o que faz |
  |---|---|---|---|---|
  | `link_hit` | Ao Acertar | 4 | 2 | o próximo estágio nasce **no ponto de impacto** (entidade ou bloco), na direção do estágio anterior |
  | `link_end` | Ao Terminar | 4 | 2 | o próximo estágio nasce onde o estágio anterior **terminou** (projétil expirou, zona acabou, feixe chegou ao fim) |
  | `link_kill` | Ao Abater | 6 | 3 | o próximo estágio nasce **onde um alvo morreu** por dano deste estágio |
  | `link_leap` | Saltar | 10 | 3 | **terminal**: ao acertar uma entidade, REPETE o estágio atual a partir dela em direção ao inimigo mais próximo ainda não atingido; paga mana por salto (15% do custo da magia, mín. 2); para quando acabam alvos válidos, o mana, ou 64 saltos |
- Aumento novo `relentless` "Insaciável" (custo 12, tier 3): o Encadear (`chain`) da forma/efeito continua pulando (alcance 8, 3 ticks por pulo, ×0.92 de poder por pulo) enquanto houver inimigo diferente do atual E o conjurador puder pagar o pulo (mesma regra de mana do `link_leap`). Pode voltar a um inimigo já atingido, desde que não seja o atual → "pula até só sobrar um inimigo ou acabar o mana". Mobs: no máx. 8 pulos.
- O `chain` colocado depois de um EFEITO passa a funcionar (hoje é ignorado).
- Estágios depois do primeiro: só formas com `FormGlyph.stageable()==true`; não podem ser ultimate nem aceitar leis; cada estágio precisa de ≥1 efeito.
- Leis só no fim e só se o estágio 0 for forma que aceita leis.
- Limites: cada instância de estágio dispara o próximo no máx. `max(1, count())` vezes; no total, no máx. 24 estágios-filho por conjuração raiz.
- Novos erros (traduções em `src/main/lang/core/`): `link_needs_form`, `link_first`, `stage_form_not_allowed`, `stage_no_effect`, `leap_not_last`, `too_many_elements`.

### 2.2 Multi-elemento
- O elemento do SLOT (Roda) continua sendo o **elemento primário** (organização, carga de ultimate, HUD).
- Uma magia pode usar glifos de **qualquer elemento despertado** pelo jogador (`SpellParser.canUse(slotElement, glyph, data)`), além de Arcano, Origem e pais da fusão.
- Máximo de elementos distintos (não-Arcanos) por magia: `Progression.maxSpellElements(circle)` = 2 (C1–3), 3 (C4–6), 4 (C7+).
- Custo +10% por elemento extra. XP de elemento dividida entre os elementos usados.
- Cada efeito causa dano/visual com o **elemento do próprio glifo** (contexto-visão por efeito; só em conjurações de JOGADOR).
- `ParsedSpell.elements()` = conjunto de elementos usados (primário incluso).

### 2.3 API do SpellContext (nova, aditiva — nada antigo quebra)
- `eyePos()` / `look()` retornam a ORIGEM/DIREÇÃO do estágio quando definidas (senão, do conjurador como antes).
- `hasOrigin()`, `anchor()` (ponto de impacto para formas de área, pode ser null), `stage()`.
- `impact(Vec3 pos, Vec3 dir, @Nullable Entity hit)` — formas chamam em impactos REAIS (dispara `link_hit`/`link_leap`).
- `end(Vec3 pos, Vec3 dir)` — formas chamam quando o estágio termina (dispara `link_end`).
- `link_kill` é disparado pelo núcleo dentro de `damage(...)` quando o alvo morre.
- `applyEffect(ParsedSpell.EffectEntry, Entity)`, `forElement(Element)`, `copy(float powerMult)`, `withOrigin(Vec3 origin, Vec3 dir)`.
- `payHop(float cost)` — paga mana de salto (jogador: mana; criativo: grátis; mob: contador limitado).
- `save()/load()` guardam estágio/origem/direção/âncora/disparos (compatível com NBT antigo).
- `SpellCaster.castFrom(LivingEntity caster, Element element, SpellRecipe recipe, float power, @Nullable Vec3 origin, @Nullable Vec3 dir, boolean postEvents)` → `SpellContext` (sem mana/XP/conhecimento; para pedestais, chefes, portais de lendas).
- Formas do núcleo `projectile` e `touch` passam a respeitar origem; `self` não é "stageable".
- `minCircle` mais baixos nos aumentos do núcleo: `chain` 1, `vampiric`/`persistent`/`echo`/`homing`/`phantom` 2, `overload` 3.

---------------------------------------------------------------------------------------------------
## 3. Progressão v0.7 (A2 — `core/`; números em `core/Progression.java`, já atualizado pelo coordenador)

| Item | v0.6 | v0.7 |
|---|---|---|
| XP para o próximo nível | 40+15·L | **25+10·L** (L1→10 ≈ 675 XP) |
| XP no limite do Círculo | descartado | **guardado** (banco até os próximos 5 níveis); aplicado após a Ascensão |
| XP por conjuração | 1+custo/8 | **2+custo/6** |
| XP por abate com magia | max(2, vida/4) | **max(3, vida/2)** |
| XP por outro abate | 1+vida/10 | **max(2, vida/4)** |
| Abate em grupo | só quem deu o golpe final | **todo jogador a ≤24 blocos ganha o XP cheio** |
| XP de elemento: curva | 30+20·l | **25+15·l** |
| XP de elemento por conjuração | 1+custo/4 | **2+custo/4** (dividido entre os elementos da magia) |
| Glifos por magia | círculo+2 | **`maxGlyphs(level, circle)` = min(40, 4 + nível/4 + círculo)** (L1: 5 · L10: 7 · L30/C3: 14 · L60/C6: 25 · L100: 39) |
| Vida do jogador | fixa | **+2 de vida máx. a cada 5 níveis** (até +30 no nível 75) — `Progression.bonusHealth(level)`, modificador `isekai_mahou:growth_health` |
| Glifos Arcanos | por Círculo {1,2,3,5,8} | **por NÍVEL** `Progression.arcaneUnlockLevel(tier)` = {1, 5, 10, 18, 28} (respeitando o `minCircle` do glifo) |
| Nós do caminho elemental | feitos estranhos | **Provações** (rank 1–7), ver §4 |

Ranks de Provação por elemento (núcleo: `Progression.TRIAL_NODES`, `trialElementLevel(rank)`, `trialCircle(rank)`):

| Rank | Nó do caminho | Nível do elemento | Círculo |
|---|---|---|---|
| I | apprentice (Aprendiz) | 3 | 1 |
| II | adept (Adepto) | 6 | 1 |
| III | fluidity (Fluidez) | 9 | 2 |
| IV | master (Mestre) | 12 | 3 |
| V | cataclysm (Cataclismo) | 15 | 4 |
| VI | domain (Domínio) | 19 | 6 |
| VII | archmage (Arquimago) | 24 | 8 |

- Para ENTRAR na provação de rank N: elemento despertado, rank N-1 concluído, nível do elemento e Círculo da tabela.
- Concluir rank N grava a flag `trial.<elemento>.<N>` e o contador `trial.<elemento>.<N>` (+ `trial.clear`, `trial.clear.<elemento>`) e o módulo `paths` libera o nó (ensina os glifos do tier).
- Ao escolher a classe: o jogador recebe 2 magias prontas nos slots Arcanos (A2) e o `paths` DESPERTA o 1º elemento de afinidade da classe (Mago Fogo, Bruxa Água, Warlock Sombra, Druida Terra, Necromante Sombra) com uma magia pronta desse elemento.

### 3.1 APIs novas do núcleo (A2)
- `core/api/TrialsApi`: `interface Handler { boolean canStart(ServerPlayer, Element, int rank, List<Component> reasons); void start(ServerPlayer, Element, int rank); }`, `set(Handler)`, `handler()`, `clearedRank(MagicData, Element)` (maior N com flag `trial.<el>.<N>`), `flag(Element, int)`, `markCleared(ServerPlayer, Element, int)` (grava flag+contadores e dispara `MagicProgressEvent.Counter`). Pacote C→S `CorePayloads.RequestTrial(String element, int rank)` chama o handler.
- `core/api/Rewards`: `give(ServerPlayer, ItemStack)` (inventário ou chão), `toast(ServerPlayer, Component title, Component subtitle, ItemStack icon)` (toast estilizado no cliente), `banner(ServerPlayer, Component, int rgb)`.
- `core/api/Legends`: registro de **Magias Lendárias** pré-montadas: `register(String id, SpellRecipe recipe, Element element, int minCircle)`, `all()`, `get(id)`, `scroll(id)` (ItemStack `spell_scroll` com a receita), `randomScroll(RandomSource)`. Comando `/mahou legend list|give <id>`. A aba Biblioteca do Grimório mostra a galeria.
- `client/objectives/ObjectiveTracker` (cliente): `register(String id, int priority, Supplier<List<ObjectiveLine>>)`; `ObjectiveLine(Component title, Component detail, int current, int target, int rgb, ItemStack icon, @Nullable Vec3 targetPos, @Nullable ResourceKey<Level> targetDim)`. HUD no canto superior direito (abaixo dos ícones de efeito), tecla `CoreKeys.TRACKER` (J) mostra/esconde. Setinha + distância quando há `targetPos` na mesma dimensão.
- `client/GlyphArt` (cliente): `glyph(GuiGraphics, Glyph, x, y, size)`, `element(GuiGraphics, Element, x, y, size)`, `kind(GuiGraphics, GlyphKind/boolean link, x, y, size)`, `spell(GuiGraphics, SpellRecipe, Element, x, y, size)`, `tab(GuiGraphics, String tabId, x, y, size)`. Usa as texturas de §10 e cai num desenho de reserva se a textura não existir.
- Menu B: `MenuTabs.setIcon(id, ResourceLocation)` (ícone padrão `textures/gui/tab/<id>.png`); `StatusScreen` repaginado (abas com ícone + nome, aba atual destacada em dourado, moldura de "Janela de Status", painel maior até 520×300).
- `MahouConfig` novos: comum `SPELL_BLOCK_DESTRUCTION` (true: lendas podem abrir crateras/apagar blocos), `TRIAL_MAX_INSTANCES` (8), `PEDESTAL_MAX_PER_CHUNK` (4); cliente `SHOW_OBJECTIVES` (true), `SHOW_TARGET_INFO` (true), `FLOATING_HEALTH_BARS` (true).

---------------------------------------------------------------------------------------------------
## 4. Câmaras de Provação (módulo NOVO `trials`)

Ciclo: **pedido → Recepção (lobby) → Arena (ondas + chefe) → Vitória (recompensas) → Volta**.

- **Dimensão** `isekai_mahou:trial_realm`: gerador flat só ar, sem features/estruturas/spawns, bioma próprio `trial_realm`, `fixed_time` 18000, céu próprio (efeitos registrados no cliente do módulo). Não adicionar ao `DimKind` do módulo dimensions. JSON gerado por `tools/gen/trials.py`.
- **Instâncias**: uma dimensão, instância n na origem `(n·1024 + 512, 100, 512)`; recepção na origem, arena a +96 em Z. `SavedData` com instâncias ativas; force-load por `TicketController` (registrado no mod bus); limpeza ao terminar e no boot (instâncias abortadas, blocos apagados, jogadores evacuados no login para o ponto de retorno).
- **Pedido**: botão "Iniciar Provação" na aba Caminhos (pacote `RequestTrial`), comando `/mahou trial start <elemento> [rank]` (qualquer jogador), mensagem clicável no chat quando fica elegível. Ranks já concluídos podem ser **repetidos** como Desafio (XP/loot 50%).
- **Recepção** (sala temática do elemento ~21×21): bloco `trial_altar` (clique: anfitrião começa; contagem 5 s), bloco `trial_exit` (sair), painel de `TextDisplay` com a provação, o grupo e instruções.
  Convites: `/mahou trial invite <jogador>` → convite clicável; `/mahou trial join <jogador>`; `/mahou trial leave`. Quem entra salva o próprio ponto de retorno.
- **Arena** circular (raio 18–24 pelo rank), paleta do elemento (fusões misturam os pais), colunas para cobertura, parede com barreira, perigos do elemento SEMPRE telegrafados com `core/boss/Telegraph` (fogo: gêiseres de lava; água: ondas que empurram; terra: pedras caindo; vento: rajadas em linha; luz: pilares sagrados + círculos de cura para jogadores; sombra: zonas de escuridão). Proteção: jogadores não quebram/colocam blocos (exceto criativo); explosões não quebram blocos.
- **Ondas**: rank I–II 3 ondas, III–IV 4, V–VII 5; mobs do elemento (ids das criaturas do mod com reserva vanilla), quantidade `3 + rank + 2·(jogadores−1)`, vida +20%·(rank−1), dano +10%·(rank−1); onda 2+ tem um Elite; círculo de invocação 30 ticks antes de cada mob; intervalo 5 s com título "Onda 2/4". Barra de chefe com o progresso.
- **Chefe final**: I–IV `trial_avatar` (módulo `bosses_d`) do elemento no rank; V: Guardião do elemento (`ignis`, `tide_guardian`, `primordial_golem`, `zephyr`, `seraph`, `abyss_sovereign`; fusão → avatar do rank V); VI: avatar rank VI + 2 `trial_sentinel`; VII: Guardião + avatar juntos. Todos recebem a tag `BossKit.TRIAL_TAG` e escala de grupo.
- **Morte na arena**: cancelar a morte → jogador fica "Abatido" (espectador sobre a arena), revive com 50% de vida na próxima onda; se todos caírem → derrota: todos voltam à recepção (podem tentar de novo). Itens NUNCA se perdem.
- **Vitória**: título + cutscene curta; recompensas por jogador via `Rewards`: XP `150·rank`, XP do elemento `60·rank`, essências, cristais, pergaminhos de glifo do elemento (tier ≤ rank), itens do `armory` por id (§8), pergaminho lendário (`Legends.randomScroll`) nos ranks VI–VII; `TrialsApi.markCleared` para quem era elegível ("Assistência" para os demais: XP + loot). Portal de saída abre; volta automática após 45 s.
- **HUD**: provedor do `ObjectiveTracker` ("Provação de Fogo III — Onda 2/4 — 5 inimigos").
- **Bloco/itens/ids**: `trial_altar`, `trial_exit`, `trial_barrier` (invisível), blocos decorativos com prefixo `trial_`. Contadores `trial.clear`, `trial.clear.<el>`.

## 5. Chefes de Provação (módulo NOVO `bosses_d`)
- `trial_avatar` "Avatar Elemental": humanoide flutuante ~2.8 blocos (GeckoLib), núcleo de cristal, placas, glowmask; textura por elemento base (fusões = textura do pai A com brilho do pai B). Lê `isekai_mahou.trial_element` (String) e `isekai_mahou.trial_rank` (int) do `getPersistentData()` no 1º tick (padrão: arcane/1). Vida 120 + 90·rank. 3 fases (66%/33%), 6+ ataques por elemento TELEGRAFADOS, usa magias de glifo do elemento incluindo **magias programáveis** (ex.: feixe → link_hit → nova). Animações: idle, float, walk, intro, phase, death, cast, slam, dash, channel, summon, + específicas. Anti-trapaça (salta/teleporta até alvos inalcançáveis). `/mahou spawnboss trial_avatar <elemento> <rank>`.
- `trial_sentinel` "Sentinela da Provação": elite (escudo + golpe em área + investida), tingido pelo elemento.

## 6. Chefes v0.7 (módulos `bosses_a`, `bosses_b`, `bosses_c`, `creatures_a`/`creatures_b` mini-chefes)
- TODO ataque em área/linha/investida ganha telegrafia no chão com `core/boss/Telegraph` (aquecimento ≥10 ticks nos grandes; golpes corpo a corpo com cone curto de 8–10 ticks).
- Corrigir ataques indesviáveis (lista no `map_bosses.md` §6).
- Escala de grupo (`BossKit.scaleToParty`) na intro. Anti-trapaça: alvo inalcançável (pilar, barco) → salto/teleporte/projétil; `canRide` false.
- Animações que faltam (intro/fase/morte) + animações dos ataques novos. Cada chefe ganha +1–2 ataques novos.
- Modo provação: com `BossKit.TRIAL_TAG` não reseta por ociosidade e usa o centro da arena como casa.
- Recompensa de grupo via `BossRewards.grant` (XP para todos por perto).

### 6.1 Kit de chefes do núcleo (A3 — `core/boss/`, `client/boss/`)
- `Telegraph` (servidor → clientes a ≤96 blocos): `circle(level, center, radius, warmupTicks, rgb)`, `ring(level, center, inner, outer, warmup, rgb)`, `line(level, from, to, width, warmup, rgb)`, `cone(level, origin, yawDeg, angleDeg, range, warmup, rgb)`, `follow(level, Entity, radius, warmup, rgb)`, `safe(...)` (zona verde segura); desenhado no chão como decalque translúcido que ENCHE do centro até a borda durante o aquecimento, borda pulsante, some com um flash.
- `Telegraph.delayedBlast(level, center, radius, warmup, rgb, Consumer<List<LivingEntity>> onHit)` e `delayedLine(...)`.
- `BossKit`: `TRIAL_TAG`, `isTrialBoss(e)`, `scaleToParty(Mob, radius)`, `partyAround(Entity, radius)`, `isUnreachable(Mob, LivingEntity)`, `leapAt(Mob, LivingEntity, power)`, `blinkNear(Mob, LivingEntity, dist)`, `shake(level, pos, radius, intensity, ticks)` (tremor de câmera), `roar(Mob)`.
- `BossRewards.grant(LivingEntity boss, int xp, double radius)` (XP + contador `boss.<id>` para todos no raio).

## 7. Magias Lendárias (módulos NOVOS `legends_a`, `legends_b`)
Glifos genéricos que permitem montar as magias famosas; cada módulo registra suas receitas prontas em `core/api/Legends`.
- `legends_a`: aumento `all_in` "Tudo ou Nada" (gasta TODO o mana; poder escala com o mana gasto; conjurador desmaia: Exaustão Mágica + lentidão + animação de colapso) · aumento `colossal` "Colossal" (área ×2.5, poder ×1.6, custo ×2.5, +1 s de conjuração) · efeito `crater` "Explosão Colossal" (FOGO t4: explosão gigante com cratera de verdade respeitando `SPELL_BLOCK_DESTRUCTION`/mobGriefing, nuvem em cogumelo, círculos mágicos empilhados no céu) · forma `arsenal_gate` "Portão do Arsenal" (portais dourados atrás do conjurador disparam rajadas) · efeito `conjured_weapon` "Arma Conjurada" (armas voadoras com modelo de espada/lança que perfuram e ficam cravadas) · efeito `blade_field` "Campo de Lâminas" (espadas cravadas no chão que se lançam nos inimigos; ótimo em Domínio).
  Lendas: "Explosão!", "Portão do Tesouro", "Campo de Lâminas Infinitas", "Rugido do Dragão".
- `legends_b`: forma `converge` "Convergência" (dois orbes — 1º e 2º efeito — giram, se fundem e soltam uma esfera enorme e lenta que atravessa tudo em linha) · efeito `annihilate` "Aniquilação" (VAZIO t4: % da vida máx. ignorando armadura, apaga blocos sem drops se permitido) · forma `sky_lances` "Lanças do Céu" (lentes no céu disparam feixes em todos os inimigos da área) · forma `spiral_sphere` "Esfera Espiral" (esfera na mão que tritura ao tocar e arremessa) · efeito `slash_storm` "Tempestade de Cortes" (VENTO t4: vários cortes invisíveis rápidos + marcas nos blocos).
  Lendas: "Vazio Roxo", "Ruína Carmesim", "Santuário Maligno", "Megido Solar", "Esfera Espiral", "Raio Assassino", "Lei Sagrada".

## 8. Equipamento de progressão (módulos `outfits` e NOVO `armory`; vida no núcleo)
- `outfits`: tiers de armadura por classe: I Aprendiz (o atual), II Adepto (Círculo 3), III Mestre (C5), IV Arquimago (C8). Ids `<classe>_<tier>_<peça>` com tier ∈ {adept, master, archmage}. Modelos 3D GeckoLib novos por tier (ombreiras, coroas, gemas, capas), glowmask. Defesa total ~7/13/17/20 + resistência; bônus mágicos (MAX_MANA, MANA_REGEN, SPELL_POWER) por peça. Entregue automaticamente ao subir de Círculo (flag `gear.armor.<tier>`), com toast. Receita de melhoria (smithing).
- `armory`: armas por classe ×4 tiers: Mago `mage_staff_t1..t4` (Cajado), Bruxa `witch_wand_t1..t4` (Varinha), Warlock `pact_blade_t1..t4` (Lâmina do Pacto), Druida `druid_staff_t1..t4` (Cajado de Galhos), Necromante `reaper_scythe_t1..t4` (Foice). Modelos 3D (item JSON com elementos, estilo Blockbench). Atributos SPELL_POWER/MAX_MANA/MANA_REGEN (+dano corpo a corpo nas lâminas/foices); tier IV com passiva especial. Acessórios Curios: `vitality_charm` (+4 vida), `ward_ring` (+2 armadura +1 resistência), `archmage_sigil` (+15% poder). Item `arcane_upgrade_template` (smithing). **Marcos de nível**: nível 5 arma T1 · 10 acessório · 20 arma T2 · 30 acessório · 45 arma T3 · 60 `archmage_sigil` · 75 arma T4; nos outros múltiplos de 5: pacote (essências/cristais/pergaminho). Toast "Recompensa de nível N!". Loja da Guilda `guild_shop/armory.json`.

## 9. Automação e magia útil (módulo NOVO `automation`)
- Bloco `runic_pedestal` "Pedestal Rúnico": recebe um `spell_scroll` (receita) e a conjura sozinho a cada N s (1–60, GUI) a partir do topo, numa direção configurável (para baixo/frente) e alvo deslocado (GUI), usando `SpellCaster.castFrom` com um FakePlayer do dono (créditos de abate de jogador → drops como farm de ferro). Modo redstone (sempre/ligado/desligado/pulso). Consome mana de uma bateria a ≤8 blocos. Limite `PEDESTAL_MAX_PER_CHUNK`.
- Bloco `mana_battery` "Bateria de Mana" (guarda 20 000; o jogador agachado+clique transfere mana; mostra nível). Bloco `mana_condenser` "Condensador de Mana" (queima cristais/essências/lápis/ametista → mana para baterias próximas). Bloco `arcane_collector` "Coletor Arcano" (puxa itens num raio para o inventário interno; funil tira).
- Glifos utilitários (efeitos): `harvest_replant` Colheita Fértil (ARCANE t1: colhe maduras e replanta) · `collect_items` Recolher (ARCANE t1: puxa itens para o conjurador ou para o coletor/pedestal mais próximo) · `shear` Tosquiar (ARCANE t1) · `breed` Reproduzir (ARCANE t2: alimenta animais com o item certo do baú/inventário) · `till` Arar (TERRA t1) · `sow` Semear (ARCANE t1: planta sementes do inventário do pedestal/jogador) · `call_rain` Chamar Chuva (ÁGUA t2: chuva no mundo por 5 min) · `clear_sky` Céu Limpo (LUZ t2) · `call_thunder` Chamar Tempestade (RAIO t3) · `smelt_items` Forja Arcana (FOGO t2: cozinha itens no chão).

## 10. Arte e UI (agentes `art`, `ui_grimoire`, `ui_hud`)
Texturas (todas PNG, geradas por `tools/gen/icons.py` com `tools/pixelart.py`):
- `assets/isekai_mahou/textures/gui/glyph/<id>.png` — 32×32, um por glifo (moldura pelo tipo + cor do elemento + pictograma único).
- `assets/isekai_mahou/textures/gui/element/<element_id>.png` — 16×16, uma runa por elemento (22, incl. arcane).
- `assets/isekai_mahou/textures/gui/glyph_kind/<form|effect|augment|trigger|link|law>.png` — 16×16.
- `assets/isekai_mahou/textures/gui/tab/<tab_id>.png` — 16×16 (status, paths, grimoire, loadouts, glyphs, journal, guild, bestiary, trials, legends).
- `ui_grimoire`: Grimório 2.0 (ícones; vista de PROGRAMA com estágios e ligações; paleta multi-elemento com chips de runa; destino simplificado; sem textos cortados; galeria de Lendas na Biblioteca).
- `ui_hud`: ícones nos slots; **Olho Analítico** (alvo olhado: nome, barra de vida com números, armadura, fraquezas/resistências elementais); barrinhas de vida flutuantes em mobs feridos; linhas de Vida/Armadura na aba Status; banner grande de nível.

## 11. Ids novos (catálogo v0.7)
- Glifos núcleo: `link_hit`, `link_end`, `link_kill`, `link_leap`, `relentless`.
- legends_a: `all_in`, `colossal`, `crater`, `arsenal_gate`, `conjured_weapon`, `blade_field`. legends_b: `converge`, `annihilate`, `sky_lances`, `spiral_sphere`, `slash_storm`.
- automation: `harvest_replant`, `collect_items`, `shear`, `breed`, `till`, `sow`, `call_rain`, `clear_sky`, `call_thunder`, `smelt_items`; blocos `runic_pedestal`, `mana_battery`, `mana_condenser`, `arcane_collector`.
- trials: dimensão `trial_realm`; blocos `trial_altar`, `trial_exit`, `trial_barrier`. bosses_d: entidades `trial_avatar`, `trial_sentinel`.
- armory: `mage_staff_t1..4`, `witch_wand_t1..4`, `pact_blade_t1..4`, `druid_staff_t1..4`, `reaper_scythe_t1..4`, `vitality_charm`, `ward_ring`, `archmage_sigil`, `arcane_upgrade_template`.
- outfits: `<classe>_<adept|master|archmage>_<helmet|chestplate|leggings|boots>`.

## 12. Donos de arquivos (fase B)
| Agente | Pode editar |
|---|---|
| trials | `feature/trials/**`, lang/merge/tools do módulo, `data/isekai_mahou/{dimension,dimension_type,worldgen/biome}/trial_realm.json` |
| bosses_d | `feature/bosses_d/**` + assets/lang do módulo |
| progression (paths+ascension) | `feature/paths/**`, `feature/ascension/**`, `data/isekai_mahou/ascension/**`, lang de paths/ascension |
| journal | `feature/journal/**`, `data/isekai_mahou/journal/**`, lang journal |
| guild | `feature/guild/**`, `data/isekai_mahou/guild_quests/**`, `guild_shop/{magic,scrolls,supplies}.json`, lang guild |
| ui_grimoire | `feature/ui_grimoire/**`, lang ui_grimoire |
| ui_hud | `feature/ui_hud/**`, lang ui_hud |
| art | `tools/gen/icons.py`, `assets/isekai_mahou/textures/gui/{glyph,element,glyph_kind,tab}/**` |
| bosses_a / bosses_b / bosses_c | o próprio módulo |
| minibosses | em `feature/creatures_a/**` e `feature/creatures_b/**` SÓ os arquivos dos chefes (king_slime, goblin_king, orc_lord, minotaur, BossFeatures, CbBoss) + seus assets/animações + lang desses módulos (só chaves novas) |
| legends_a / legends_b / automation / armory | o próprio módulo (armory também `data/isekai_mahou/guild_shop/armory.json`) |
| outfits | `feature/outfits/**`, `tools/gen/outfits.py`, assets/lang/merge do módulo |
| forms_a | `feature/forms_a/**` |
| forms_b | `feature/forms_b/**` e `feature/domain/**` |

O núcleo (`core/`, `glyph/`, `network/`, `client/`, `module/`) é da fase A. Se faltar algo do núcleo na fase B, contorne no seu módulo e reporte em `followups`.
