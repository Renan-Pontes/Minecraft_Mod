# API v1.0 — Magias, classes e progressão (fase A, trilha A1)

Contrato: `docs/V10_DESIGN.md` (§0.1, §1 e §2 A1). Pacote base `com.renanpontes.isekaimahou` (abreviado `J.`).
Este arquivo tem DUAS partes, uma por trilha: **A1a** (classes, aprendizado, progressão e inflação — logo abaixo) e
**A1b** (loadouts, custo por cópia, mana treinável, detalhamento e presets — seção própria mais abaixo). Ao editar, mexa só
na sua seção.

---------------------------------------------------------------------------------------------------
# Parte A1a — Classes, aprendizado, progressão e inflação

Código: `core/MagicPath.java`, `core/ClassKits.java`, `core/CoreGameplay.java`, `core/Progression.java`,
`core/classes/**` (`ClassesCore`, `ClassesRegistries`, `ClassesPayloads`, `GlyphLearning`, `Lessons`, `LessonLedger`),
`core/balance/**` (`BalanceCore`, `MobScaling`), `core/item/{GlyphScrollItem,TechniqueLessonItem,LessonData}.java`,
`client/PathSelectionScreen.java`, `client/classes/**` (`ClassesCoreClient`, `LessonClient`, `LessonWriteScreen`).
Traduções: `src/main/lang/core_classes/`. Assets: `tools/gen/core_classes.py` (Lição), receita `recipe/technique_lesson.json`.

## 1. `MagicPath` — 15 classes (5 Mágicas + 5 Físicas + 5 Destreza)

As 10 classes da v0.9 (warrior, paladin, barbarian, monk, bard, artificer, hunter e as antigas knight/ranger/rogue) SAÍRAM.
A ordem é salva (o pacote de escolha usa o ordinal; o save usa o id — um id que não existe mais vira `NONE`, e o jogador
escolhe de novo). Novas classes só no FIM.

| ord. | enum | id | nome PT | cat. | afinidade (primária 1º) | difícil | arma do kit (`kitWeapon` / `weaponPrefix`) | cor | módulo |
|---|---|---|---|---|---|---|---|---|---|
| 1–5 | MAGE…NECROMANCER | (iguais à v0.8) | | MAGIC | | | — (rituais) | | `ritual_*` |
| 6 | SMITH | smith | Ferreiro | PHYSICAL | Fogo, Terra | Água | `smith_hammer` | `0xE8742C` | `class_smith` |
| 7 | ROGUE | rogue | Ladino | PHYSICAL | Sombra, Vento | Luz | `rogue_dagger` | `0x8E5BD6` | `class_rogue` |
| 8 | KNIGHT | knight | Cavaleiro Rúnico | PHYSICAL | Luz, Terra | Sombra | `runic_blade` | `0x7FA8FF` | `class_knight` |
| 9 | ENCHANTER | enchanter | Encantador | PHYSICAL | Luz, Vento (Arcano é neutro p/ todos) | Sombra | `enchanter_gauntlets` | `0x5FE3C0` | `class_enchanter` |
| 10 | SHINIGAMI | shinigami | Shinigami | PHYSICAL | Sombra, Vento | Luz | `zanpakuto` | `0xD9483B` | `class_shinigami` |
| 11 | QUINCY | quincy | Quincy | DEXTERITY | Luz, Vento | Sombra | `quincy_cross` | `0xA8DFFF` | `class_quincy` |
| 12 | GUNSLINGER | gunslinger | Pistoleiro | DEXTERITY | Fogo, Vento (Raio = afinidade) | Água | `mana_revolver` | `0xD9A441` | `class_gunslinger` |
| 13 | RANGER | ranger | Ranger | DEXTERITY | Vento, Terra | Fogo | `ranger_bow` | `0x6BCB5A` | `class_ranger` |
| 14 | GAMBLER | gambler | Apostador | DEXTERITY | Luz, Sombra | — | `gambler_deck` | `0xE04A8C` | `class_gambler` |
| 15 | STEELBALL | steelball | Steel Ball | DEXTERITY | Terra, Vento | Fogo | `steel_ball` | `0xB4C0CC` | `class_steelball` |

**Fase B — arma do kit:** o módulo `class_<id>` registra o item com EXATAMENTE o id da coluna "arma do kit"
(ex.: `isekai_mahou:zanpakuto`). `ClassKits.give` entrega esse item mesmo sem `addItem` (e não dá cópia dupla se o módulo
registrar via `addItem`); a tela de classe mostra o item como "arma:". Se a arma tiver tiers, registre também
`<id>_t2..t4` (ex.: `steel_ball_t2`) para a Guilda/Provações entregarem (`ClassKits.tieredWeaponPrefix`).

```java
enum MagicPath {
    static final MagicPath[] PLAYABLE;                 // as 15, na ordem da tela
    static List<MagicPath> byCategory(Category c);     // as 5 da aba
    static MagicPath byId(String) / byIndex(int);      // desconhecido → NONE
    Category category(); Resource resource();          // MAGIC→MANA, PHYSICAL→VIGOR, DEXTERITY→FOCUS
    @Nullable Element primaryAffinity();               // 1º elemento despertado (Encantador/Apostador/Quincy/Cavaleiro: Luz)
    List<Element> affinityElements(); List<Element> hardElements(); Affinity affinity(Element);
    String weaponPrefix(); @Nullable ResourceLocation kitWeapon();
    String moduleId();                                 // "class_<id>" | "ritual_<mage|witch|warlock|druid|necro>"
    ResourceLocation iconTexture();                    // textures/gui/class/<id>.png (art; reserva = ClassKits.icon)
    MutableComponent displayName() / styleName() / description();
    MutableComponent systemName();                     // NOVO: "path.isekai_mahou.<id>.system" (ex.: "Forja Arcana")
    MutableComponent systemDescription();              // NOVO: "path.isekai_mahou.<id>.system.desc"
    MutableComponent bonusDescription();               // NOVO: "path.isekai_mahou.<id>.bonus" (o MÓDULO aplica o bônus)
    int rgb(); int argb(); ChatFormatting color;
}
enum MagicPath.Category { MAGIC("magic"), PHYSICAL("physical"), DEXTERITY("dexterity");
    String id; String legacyId;   // legacyId = id da v0.9 (magic/martial/ranged) só p/ chaves antigas (graus dos trajes)
    Resource resource; MutableComponent displayName(); MutableComponent hint(); ResourceLocation iconTexture();
    static Category byId(String);   // aceita também "martial"/"ranged"
}
```
- **Renomeado:** `Category.MARTIAL → PHYSICAL`, `Category.RANGED → DEXTERITY` (ids `physical`/`dexterity`; ícones
  `textures/gui/category/{physical,dexterity}.png`). `CoreGlyphs.STARTER_MARTIAL/STARTER_RANGED` mantêm o nome (são as
  técnicas das Físicas/Destreza).
- **Textos** (todos em `lang/core_classes`): `path.isekai_mahou.<10 novas>` + `.style`, `.desc`, `.system`,
  `.system.desc`, `.bonus`; e `.system`, `.system.desc`, `.bonus` das 5 Mágicas (os nomes/estilos/descrições delas
  continuam em `lang/core`). **Os módulos de classe NÃO redefinem essas chaves** (chave duplicada com texto diferente =
  erro de build); para mudar o texto, peça ao dono do núcleo.
- **Encantador (pedido do jogador — o amigo vai jogar de "Yuuki Kagurazaka"):** a descrição já anuncia os dois
  encantamentos de origem OBRIGATÓRIOS do `class_enchanter`: **Anti-Magia** (magias não funcionam nele: anula/reduz muito
  dano e efeitos de magias inimigas, limita as próprias magias ofensivas, custo de manter) e **Restrição Celestial**
  (abre mão de mana/magia por força, velocidade, sentidos e resistência absurdos; invisível para detecção mágica; imune a
  acertos garantidos de domínio/barreira que miram "mana"; não conjura enquanto ativa). Os dois como glifos de origem
  montáveis e com níveis.

### Tela de classe (`client/PathSelectionScreen`)
3 abas (Mágicas / Físicas / Destreza), lista das 5 classes, painel com nome, categoria · estilo, recurso, descrição,
**Sistema: <nome>** + explicação, **Bônus de classe**, afinidades, difícil, elemento inicial, técnicas prontas, arma do kit
e kit, e o lembrete de **multiclasse livre**. Teclado e rolagem como na v0.9.

### Kits (`core/ClassKits`)
- `addItem(path, () -> stack)`, `setArmor(path, slot, () -> stack)`, `items/armor/give` como antes.
- `icon(path)`: peitoral registrado → arma do kit → item vanilla (Ferreiro bigorna, Ladino trouxinha, Cavaleiro espada de
  diamante, Encantador mesa de encantamento, Shinigami espada de netherite, Quincy flecha espectral, Pistoleiro carga de
  fogo, Ranger arco, Apostador pé de coelho, Steel Ball núcleo pesado).
- `weapon(path)`: a arma do kit (vazio se o módulo não a registrou). `fallbackGear(path)`: a arma do kit; se o módulo
  `class_<id>` não registrou o item, uma reserva vanilla (Físicas: espada de ferro; Destreza: arco — a forma Flecha conjura
  sem flechas), para ninguém começar desarmado. (O escudo do Cavaleiro da v0.9 saiu da reserva.)
- **NOVO** `tieredWeaponPrefix(path)`: prefixo de arma COM tiers para quem entrega armas de tier: a da própria classe
  (`<weaponPrefix>_t1` registrado) → a herdada do Arsenal (Cavaleiro `knight_blade`, Ranger `longbow`, Ladino
  `twin_daggers`) → a genérica da categoria (`mage_staff` / `longsword` / `arcane_crossbow`). Usado pela Guilda
  (`PromotionGear`) e pelas Provações (`TrialRewards`).

## 2. Progressão (`core/Progression`) — nível 150 (+50 escondidos)

```java
int MAX_LEVEL = 150;                 // máximo VISÍVEL
int HIDDEN_MAX_LEVEL = 200;          // só depois do Dragão Primordial
String HIDDEN_CAP_COUNTER = "boss.primordial_dragon";
int CIRCLE_TEN_LEVEL = 100;          // Círculo 10 chega no nível 100
int MAX_GLYPHS_HARD = 50;            // (era 40; o codec aceita 64)

int  xpToNext(int level);            // L<100: 40 + 18L + 0,35L²   ·   L≥100: 5300 + 30(L−100)
int  xpForLevels(int from, int n);   // soma (até o 200)
int  levelCap(int circle);           // C1..C8 = 10..80, C9 = 100, C10 = 150 (o máximo VISÍVEL)
int  levelCap(MagicData d);          // o limite REAL do jogador: no C10, 150 ou 200 (contador ≥ 1)  ← use este
int  maxLevel(MagicData d);          // 150 | 200
boolean hiddenCapUnlocked(MagicData d);
int  absoluteLevelCap(int circle);   // C10 = 200 (só o carregamento do save usa, para não cortar quem passou do 150)
int  maxGlyphs(int level, int circle);   // até o 100 igual à v0.8 (4 + L/4 + C); +1 a cada 10 níveis acima do 100
int  bonusHealth(int level);         // +4 a cada 5 níveis até +60; acima do 100, +2 a cada 5 (150: +80; 200: +100)
double highLevelPower(int level);    // +0,004 de Poder Mágico por nível acima do 100 (150: +0,20; 200: +0,40)
int  levelManaLimitBonus(int level); // +12 no LIMITE de mana por nível acima do 100 (para a parte A1b somar no teto)
int  lessonTeacherXp(int tier); int lessonCircle(int tier, int minCircle, boolean otherClass); int lessonEssenceCost(boolean otherClass);
```
- `MagicData.levelCap()` agora devolve `Progression.levelCap(this)` — quem já usava `d.levelCap()`/`d.atLevelCap()`
  (HUD, Status, Ascensão) passa a ver 150 no C10 e 200 só depois do Dragão. **Nada mostra 200 antes.**
- Para "nível máximo" de verdade, use `d.levelCap()` ou `Progression.levelCap(d)` (ou `Progression.maxLevel(d)` para o
  teto final 150/200); `Progression.MAX_LEVEL` é só o máximo visível. `/mahou level <n>` aceita até 200, mas o
  `setLevel` corta no limite real do jogador.
- Os 9 Círculos agora cobrem 10 níveis cada até o C8 (80) e o **C9 vai de 80 a 100**: a Ascensão ao C10 pede nível 100.

### Tabela da curva (XP para subir no intervalo; vida = 40 base + bônus; glifos no fim do intervalo)
| níveis | Círculo | XP por nível | XP do intervalo | XP total | v0.8 (intervalo) | vida (bônus/total) | glifos |
|---|---|---|---|---|---|---|---|
| 1→10 | C1 | 58–230 | 1.269 | 1.269 | 675 | +8 (48) | 7 |
| 10→20 | C2 | 255–508 | 3.774 | 5.043 | 1.700 | +16 (56) | 11 |
| 20→30 | C3 | 540–856 | 6.939 | 11.982 | 2.700 | +24 (64) | 14 |
| 30→40 | C4 | 895–1.274 | 10.804 | 22.786 | 3.700 | +32 (72) | 18 |
| 40→50 | C5 | 1.320–1.762 | 15.369 | 38.155 | 4.700 | +40 (80) | 21 |
| 50→60 | C6 | 1.815–2.320 | 20.634 | 58.789 | 5.700 | +48 (88) | 25 |
| 60→70 | C7 | 2.380–2.948 | 26.599 | 85.388 | 6.700 | +56 (96) | 28 |
| 70→80 | C8 | 3.015–3.646 | 33.264 | 118.652 | 7.700 | +60 (100) | 32 |
| 80→100 | C9 | 3.720–5.252 | 89.323 | 207.975 | 18.400 | +60 (100) | 38 |
| 100→150 | C10 | 5.300–6.770 | 301.750 | 509.725 | — | +80 (120) | 44 |
| 150→200 (escondido) | C10 | 6.800–8.270 | 376.750 | 886.475 | — | +100 (140) | 49 |

1→100 custa ~4× a v0.8 (o fim do caminho, ~5×). Lembre que o XP por abate (`killXp`, pela vida máxima) cresce sozinho com
`MOB_HEALTH_SCALE` (mobs do mod dão ~2× XP), então o abate fica ~2× mais lento e missões/rituais (valores fixos) ~4×.

### Limite escondido (150 → 200)
- O módulo do Dragão Primordial (`bosses_e`) deve somar o contador **`boss.primordial_dragon`** com
  `MagicApi.addCounter(sp, "boss.primordial_dragon", 1)` para CADA jogador elegível (convenção `boss.<mob>` a ≤48 blocos).
- Na PRIMEIRA vez (`MagicProgressEvent.Counter` com valor ≥ 1 e sem o marcador `limit_broken` —
  `CoreGameplay.LIMIT_BROKEN_FLAG`, gravado na hora), o núcleo (`CoreGameplay.onCounter`): faixa
  "LIMITE ROMPIDO! O nível máximo agora é 200.", toast, rugido + fanfarra, partículas, e aplica o XP que estava no banco
  (o XP ganho no 150 vai para o banco, até `bankCap`).

### Vida e poder do jogador (`CoreGameplay.updateGrowthHealth`)
Três modificadores permanentes, recalculados em login/respawn/dimensão/LevelUp/CircleChanged/`/mahou reset`:
| id | atributo | valor |
|---|---|---|
| `isekai_mahou:base_health` (`CoreGameplay.BASE_HEALTH`) | `max_health` | +20 (`Progression.BASE_HEALTH_BONUS`) → vida base **40** |
| `isekai_mahou:growth_health` (`GROWTH_HEALTH`) | `max_health` | `Progression.bonusHealth(nível)` |
| `isekai_mahou:level_power` (`LEVEL_POWER`) | `isekai_mahou:spell_power` | `Progression.highLevelPower(nível)` |
Não crie outros modificadores com esses ids.

## 3. Inflação de números (§0.1) — `Progression` + `core/balance`

| constante | valor | quem aplica |
|---|---|---|
| `BASE_PLAYER_HEALTH` | 40 | núcleo (`base_health`) |
| `DAMAGE_SCALE` | 1,5 | **A1b** em `SpellContext.damage` (todo dano de magia/técnica, jogador ou mob) |
| `MOB_HEALTH_SCALE` | 2,0 | `MobScaling` (namespace `isekai_mahou`) |
| `MOB_DAMAGE_SCALE` | 1,5 | `MobScaling` (namespace `isekai_mahou`) |
| `VANILLA_HEALTH_SCALE` / `VANILLA_DAMAGE_SCALE` | 1,5 / 1,25 | `MobScaling` (mobs `minecraft:` que são `Enemy`), padrões da config |
| `VANILLA_SCALING_DEFAULT` | true | reserva se a config ainda não carregou |

`core.balance.MobScaling` (registrado por `BalanceCore.init()`, chamado em `ClassesCore.init`):
- **Vida:** no `EntityJoinLevelEvent` (nascer OU carregar do disco; prioridade LOW) põe o modificador
  `isekai_mahou:balance_mod_health` (×2) nas criaturas do mod e `isekai_mahou:balance_vanilla_health`
  (×`MahouConfig.VANILLA_HEALTH_MULT`) nos hostis vanilla se `MahouConfig.VANILLA_SCALING`. Permanentes (salvos), só
  trocados se o valor mudou — **recarregar nunca duplica**; desligar a opção tira o modificador. A vida atual mantém a
  proporção (mob cheio continua cheio). Jogadores nunca.
- **Dano:** no `LivingIncomingDamageEvent` (prioridade HIGH), se quem CAUSOU (`source.getEntity()`) é criatura do mod
  (×1,5) ou hostil vanilla com a opção ligada (×`VANILLA_DAMAGE_MULT`) — vale para golpes, flechas, explosões e dano
  calculado à mão pelos chefes. **Magia (`isekai_mahou:spell`) fica de fora** (já tem `DAMAGE_SCALE`).
- Consultas para telas: `MobScaling.healthScale(entity)`, `MobScaling.damageScale(entity)`.
- **Para módulos de chefes:** NÃO multipliquem a vida/dano de novo; fases por fração (`getHealth()/getMaxHealth()`) seguem
  funcionando. Quem compara a vida com números fixos (ex.: `getHealth() < 300`) precisa usar a fração ou ×`MOB_HEALTH_SCALE`.

## 4. Aprender glifos (§A1.6)

### Pergaminho de Glifo (`core/item/GlyphScrollItem`)
- Gasta **1 essência do elemento do glifo** (`GlyphScrollItem.ESSENCE_COST`): Arcano → `essence_arcane`; base → a dele;
  **fusão → de um dos pais** (o que o jogador tiver mais). Faltando: "Para aprender X é preciso 1× Essência de Fogo (você
  tem 0)." na barra de ação + som de "não". Criativo: de graça.
- **Glifo de origem de OUTRA classe não sai de pergaminho** ("… é um glifo de origem do Ferreiro: só se aprende com uma
  Lição …"). Os da própria classe saem normalmente.
- Dica do item: custo e, para glifo de origem, "da sua classe" (verde) ou "só por Lição" (vermelho).
- Rituais/teses/pactos/comandos que chamam `MagicApi.learnGlyph` direto NÃO pagam essência (regra só do item).

### Lição (`technique_lesson`, `core/item/TechniqueLessonItem`)
- **Em branco**: receita sem forma `papel + bolsa de tinta + qualquer essência` (arcana ou das 6 base). Usar abre a tela
  "Escrever Lição" (`client/classes/LessonWriteScreen`) com os glifos de origem da classe do jogador que ele conhece;
  "Escrever Lição" grava o glifo (uma folha da pilha). Sem classe / sem glifo de origem: aviso.
- **Escrita** (brilha; nome "Lição: <glifo>"; componente `isekai_mahou:lesson` = `LessonData(glyph, author, authorId,
  authorPath)`): quem usa aprende se tiver:
  - **Círculo** `Progression.lessonCircle(tier, minCircle, outraClasse)` = o do tier (1→C1, 2→C2, 3→C3, 4→C5, 5→C8) ou o
    `minCircle` do glifo (o maior), **+1 se o leitor é de outra classe** (máx. 10);
  - **essências** do elemento: **1** (mesma classe) ou **2** (multiclasse) — `Progression.lessonEssenceCost`.
  A Lição é consumida; toast "Técnica aprendida", som e partículas.
- **Professor** ganha `Progression.lessonTeacherXp(tier)` = 60 + 60·tier XP (120..360) quando alguém aprende — na hora se
  estiver online (mensagem "Fulano aprendeu X com a sua Lição! +N XP"), senão no próximo login (guardado em
  `data/isekai_mahou_lessons.dat`). Ler a própria Lição não dá XP.
- Contadores: `lesson.written` (professor ao escrever), `lesson.taught` (professor), `lesson.learned` e
  `lesson.multiclass` (leitor).

```java
// core.classes.GlyphLearning — regras reutilizáveis (itens de aprendizado de módulos)
List<Element> essenceOptions(Glyph);  Item essenceItem(Element);  int count(Player, Element);
Element pickEssence(Player, Glyph, int amount);  void consume(Player, Element, int amount);
MutableComponent costText(Glyph, int amount);   boolean isForeignOrigin(Glyph, MagicPath);  boolean free(Player);
// core.classes.Lessons
List<ResourceLocation> teachable(MagicData);    Lessons.Requirement requirement(Glyph, MagicPath reader);
void openWriter(ServerPlayer, InteractionHand); boolean read(ServerPlayer, ItemStack, LessonData); Glyph glyphOf(ItemStack);
// core.classes.ClassesRegistries
DeferredItem<TechniqueLessonItem> TECHNIQUE_LESSON;  DeferredHolder<..., DataComponentType<LessonData>> LESSON;
```
Pacotes (registrar `"1"` do `ClassesPayloads`): `classes_open_lesson_writer` (S→C: mão + glifos ensináveis),
`classes_write_lesson` (C→S: mão + glifo; o servidor confere tudo de novo).

**Para os módulos de classe (fase B):** marque os glifos exclusivos com `.origin(MagicPath.<CLASSE>)` — é isso que os
torna "de origem" (usáveis em qualquer elemento, ensináveis só por Lição para as outras classes, fora dos pergaminhos de
loot que já filtram `origin() != null`). Dê `tier` coerente (decide o Círculo da Lição e o XP do professor).

## 5. Mudanças mínimas feitas fora do núcleo (para compilar)
- `glyph/CoreGlyphs.starter`: `case PHYSICAL / DEXTERITY`.
- `core/MagicData`: `levelCap()` → `Progression.levelCap(this)`; carregamento usa `absoluteLevelCap`.
- `feature/armory/WeaponKind`: 7 armas da v0.9 viraram GENÉRICAS (`path = NONE`, categoria guardada em `category()`);
  `knight_blade`/`longbow`/`twin_daggers` seguem com Cavaleiro/Ranger/Ladino; `forPath` cai na genérica da categoria
  (Físicas `longsword`, Destreza `arcane_crossbow`). `ArmoryWeapon` usa `k.category()`.
- `feature/outfits/OutfitSet`: 7 trajes da v0.9 GENÉRICOS (`path = NONE`, categoria guardada; `byPath(NONE)` = null;
  `OutfitsModule` não os põe em kit); `tierName` usa `category().legacyId` (as chaves `…tier.<grau>.martial|ranged`
  continuam). `OutfitRegistries`/`OutfitStats`: `PHYSICAL`/`DEXTERITY`.
- `feature/character/CharacterRules.suggested`: arrays das 10 classes novas (todas 27 pontos).
- `feature/trials/TrialRewards` e `feature/guild/PromotionGear`: arma de tier via `ClassKits.tieredWeaponPrefix`.

---------------------------------------------------------------------------------------------------
# Parte A1b — 9 loadouts, custo por cópia, mana treinável, detalhamento e presets

Código: `core/MagicData.java`, `core/MagicApi.java`, `core/classes/ResourceTraining.java` (novo),
`glyph/{SpellParser,ParsedSpell,SpellCaster,SpellContext,FormGlyph,EffectGlyph}.java`, `glyph/SpellCost.java` (novo),
`glyph/SpellBreakdown.java` (novo), `glyph/SpellPreset.java` (novo), `glyph/GlyphHints.java` (novo),
`glyph/weapon/{WeaponParry,WeaponStances}.java`, `network/{CorePayloads,CoreNetwork,ClientMagic}.java`,
`client/{CoreKeys,CoreHud}.java`, `client/LoadoutInput.java` (novo), `client/IsekaiMahouClient.java` (só troca o
ouvinte de scroll por `LoadoutInput.init()`). Traduções: `src/main/lang/core_spells/`.

## 1. 9 loadouts (sem Roda Elemental)

São **9 loadouts fixos** (`MagicData.LOADOUTS = 9`), cada um com Z/X/C/G (`Progression.SLOTS`). O **elemento da magia
vem dos glifos** (§3) — não há mais elemento ativo nem loadout por elemento. Carga de ultimate **única**.

```java
// core.MagicData (lado comum: servidor e cópia do cliente)
int  activeLoadout();                 void setActiveLoadout(int i);      int loadoutCount(); // = 9
static String slotKey(int loadout, int slot);                           // "loadout:slot" (chave de recarga também)
SpellSlot slot(int loadout, int slot);      // escrita (cria vazio)
SpellSlot peekSlot(int loadout, int slot);  // leitura (nunca null)
SpellSlot activeSlot(int slot);             boolean loadoutEmpty(int loadout);
void setSlotRecipe(int loadout, int slot, MoveTrigger t, SpellRecipe r /*null/vazia = apaga*/);  void clearLoadout(int l);
Element slotElement(int slot);  Element slotElement(int loadout, int slot);   // elemento da magia principal do slot
List<Element> awakenedElements();          // Arcano + despertados, em ordem fixa
float charge(); void setCharge(float); void addCharge(float); boolean chargeFull();   // carga ÚNICA (0..100)
int lastCastSlot;                          // transitório: último slot conjurado (-1)

// core.MagicApi (servidor; avisa/interrompe a conjuração)
void    selectLoadout(ServerPlayer, int loadout);
boolean setSlot(ServerPlayer, int loadout, int slot, MoveTrigger, @Nullable SpellRecipe);
```

**Troca (cliente, `client/LoadoutInput`):** **Alt + scroll** e **Mouse 5 + scroll** (os dois trocam loadout;
`CoreKeys.LOADOUT_MODIFIER` e `CoreKeys.LOADOUT_MOUSE`), **Alt + 1..9** vai direto (consome a tecla da hotbar antes do
vanilla, então o item na mão não muda). Mostra "Loadout N/9 · [Z] magia · …" na barra de ação + som de página.
`LoadoutInput.select(int)`, `LoadoutInput.describe(MagicData, int)`, `LoadoutInput.spellName/color(SpellRecipe)` estão
disponíveis para o `ui_hud`/`ui_grimoire`. `ClientMagic.predictLoadout(int)` faz a previsão local.

**Pacotes** (registrador `"core-3"` do núcleo):
| pacote | sentido | uso |
|---|---|---|
| `select_loadout` `SelectLoadout(index)` | C→S | loadout ativo 0..8 |
| `set_loadout_slot` `SetLoadoutSlot(loadout, slot, trigger, Optional<recipe>)` | C→S | grava/apaga uma versão do slot. Fábricas `SetLoadoutSlot.set(l,s,trigger,recipe)` / `.clear(l,s,trigger)` |
| `preset_edit` `PresetEdit(op, index, name, recipe, loadout, slot, trigger)` | C→S | §5 |
| `set_slot` `SetSlot(element, …)` | C→S | **@Deprecated**: o `element` é ignorado (vira `set_loadout_slot`) |
| `select_wheel`, `wheel_edit` | C→S | **@Deprecated**: aceitos e ignorados |
| `cooldown` `Cooldown(key, ticks)` | S→C | `key = MagicData.slotKey(loadout, slot)` |

**Adaptadores `@Deprecated` (módulos antigos continuam compilando e funcionando):**
`activeElement()` = elemento da magia em uso (conjurando → a da conjuração; senão a do último slot conjurado; senão a
primeira magia do loadout ativo; vazio = Arcano) · `wheel()` = `awakenedElements()` · `registeredElements()` = despertados
sem o Arcano · `wheelCapacity()` = nº de elementos (nunca enche) · `registerInWheel(e)` = está despertado? (não guarda) ·
`unregisterFromWheel`/`setActiveWheel` = nada · `activeWheelIndex()` = índice de `activeElement()` em `wheel()` ·
`activeLoadout(Element)`/`setActiveLoadout(Element,i)`/`slotKey(Element,l,s)`/`slot(Element,l,s)`/`peekSlot(Element,l,s)`
= as versões sem elemento · `charge(Element)`/`setCharge(Element,v)`/`addCharge(Element,v)` = carga única ·
`CoreKeys.WHEEL_MODIFIER` = o MESMO objeto que `CoreKeys.LOADOUT_MOUSE`.

**Migração do save da v0.8** (automática ao carregar, no codec): os slots `elemento:loadout:slot` são copiados para os
9 loadouts **na ordem da Roda** (Arcano, elementos registrados na ordem da Roda, depois os outros) × loadout, só os
loadouts com alguma magia, até encher; o loadout que estava ativo continua ativo se coube. **O que sobrar vira preset**
(nome = nome da magia, ou `fogo L3 X (sneak)`); com os 30 presets cheios, vai para a biblioteca. A carga antiga por
elemento vira a carga única (a maior). No primeiro tick o jogador recebe "suas magias foram reorganizadas em 9
loadouts…" (+ quantas viraram presets). Os campos antigos (`wheel`, `active_wheel`, `active_loadouts`, `charge`)
continuam no codec só para LER; a v1.0 grava os novos dentro de `extra` (`active_loadout`, `ult_charge`, `presets`,
`mana_trained`, `trainings`, `fatigue_until`).

## 2. Conjuração (`glyph/SpellCaster`)
- Z/X/C/G leem `peekSlot(activeLoadout(), slot)` (versão pelo `MoveTrigger`, como antes); recarga por
  `slotKey(loadout, slot)`.
- `beginCast(…, element, …)` / `performCast(…, element, …)`: o parâmetro `element` é **ignorado** quando a receita tem
  primário (sempre, para jogador): o contexto usa `spell.primary()`.
- Ultimate: precisa de `charge() ≥ 100`, zera ao conjurar. Carga sobe `2 + baseCost × 0,1` por magia e
  `dano × 0,6 / DAMAGE_SCALE` (`MagicApi.onSpellDamage`) — mesmo ritmo da v0.8.
- XP (`2 + baseCost/6`), XP de elemento, anúncio (`baseCost ≥ 45`) e tempos usam o **custo base** — o custo por cópia
  e a escala ×3 não inflam progresso nem tempos.
- **Sobrecarga** (paga com vida): `SpellCaster.overloadHealthCost(spell)` = `manaCost × (BASE_PLAYER_HEALTH/20) /
  (5 × COST_SCALE)` — a mesma proporção da v0.8 (custo/5 com 20 de vida) depois da inflação (mana ×3, vida ×2).
  Quem checa "dá para pagar com vida?" deve usar este método (não `manaCost/5`).

## 3. Elemento primário (vem dos glifos)
`SpellCost.primaryElement(SpellRecipe | List<Glyph>)`: entre os elementos não-Arcanos dos glifos, o que **cobre** mais
os outros (fusão cobre os pais); empate = o do **primeiro efeito elemental** (depois, o do primeiro glifo elemental);
só Arcano = Arcano. `ParsedSpell.primary()` guarda o resultado. O parser exige o primário **despertado** (erro
`spell_error.isekai_mahou.element_not_awakened`); os outros elementos seguem a regra da v0.7 (despertados, +10% cada).
**Glifos de origem** (de classe, `Glyph.origin() != null`) continuam usáveis em magia de qualquer elemento: só definem o
primário quando a receita não tem outro glifo elemental (`SpellCost.primaryFromOrigin`), e aí o elemento NÃO precisa
estar despertado (ex.: um Cavaleiro com só glifos de origem de Luz conjura sem ter despertado a Luz).

`SpellParser.parse(recipe, element, data)`: desde a v1.0 o `element` só escolhe o MODO (`null` = mob: sem regra de
elemento/acréscimo; qualquer outro valor = jogador). Novo atalho `SpellParser.parse(recipe, data)` (modo jogador).

## 4. Custo por cópia (`glyph/SpellCost`, aplicado no `SpellParser`)
Por estágio:
```
instâncias    = cópias(forma) × (1 + Repetir/Eco da forma) × (1 + Encadear da forma)
custo_efeito  = (custo do efeito + aumentos dele) × (1 + Repetir do efeito) × (1 + Encadear do efeito)   (mín. 1)
custo_estágio = [ (forma + aumentos da forma) + Σ custo_efeito × instâncias + gatilhos/ligação/leis ] × disparos
mana = Σ custo_estágio × (1 + 0,10 × elementos extras) × (Eficiente 0,75) × (Instantâneo 1,5) × COST_SCALE (3)
```
- `cópias(forma)` = `FormGlyph.copies(stats, circle)` (padrão: `1 + Dividir`), suavizado por `SpellCost.moderate`:
  até 6 cópias contam inteiras, depois 0,5 cada (teto 40). Contadores registrados por id (`SpellCost.registerCopies`):
  `rain` = 8·(1+Expandir) + 4·Dividir (as gotas); `meteor` = 1+Dividir (máx. 6); `sky_lances` = 3+Dividir (máx. 8);
  `arsenal_gate` = 4 + 2·Dividir (máx. 12); **ultimates/Domínios de forma moderada**: `meteor_shower` = 1 + √meteoros
  (10 + 4·Círculo), `domain_expansion` = 1 + √(duração/20 pulsos), `simple_domain` idem. `SpellCost.pulseCopies(d, i)`.
  `converge` = min(3, 1+Dividir). Formas que **ignoram o Dividir** contam 1 cópia (o Dividir nelas não multiplica efeitos):
  `touch`, `self`, `stance`, `cone`, `nova`, `aura`, `zone`, `pulse`, `wall`, `seal`, `portal`, `turret`, `spiral_sphere`,
  `time_stop`, `super_tier`, `colossus`, `transcend`, `cataclysm`.
- `disparos` do estágio N+1 = disparos do estágio N × (1 + Dividir da forma do estágio N) (`SpellCost.stageFires`: é o
  limite real do `SpellContext` — as gotas da Chuva ou os meteoros NÃO disparam um estágio cada), teto 8.
- **Saltar** continua pagando POR SALTO na hora (`SpellContext.hopCost()` = 15% do custo, mín. 6) — fora da conta.
- `COST_SCALE = 3` = a inflação de números (a mana base também é ×3): a razão custo/mana de uma magia simples não muda.
- `ParsedSpell.baseCost()` = a soma simples da v0.8 (sem cópias nem escala): tempos de conjuração/recarga, XP, carga,
  anúncio. **Módulos que usam o custo como "patamar" devem usar `baseCost()`** (ver followups: Pilha, Domínio, pedestal).

| receita | v0.8 | v1.0 (mana) | por quê |
|---|---|---|---|
| projétil + incendiar | 13 | 39 | 13 × 3 |
| projétil + dividir + incendiar | 25 | 99 | incendiar ×2 cópias |
| chuva + incendiar | 28 | 228 | 7 cópias (8 gotas suavizadas) |
| chuva de meteoros (C5) + incendiar | 103 | 435 | 1 + √30 ≈ 6,5 cópias |
| chuva de meteoros (C5) + atômico | 165 | 1595 | atômico em cada meteoro |
| projétil + dividir + incendiar → Ao Acertar → projétil + gelo | 47 | 215 | o estágio 2 dispara 2× |

**Para formas novas (fase B) com várias cópias:** sobrescreva `FormGlyph.copies(SpellStats stats, int circle)` (ex.:
Saraivada = nº de flechas; Pistolas Duplas = 2 × tiros) — ou `SpellCost.registerCopies("id", (s, c) -> …)` no init.

```java
// ParsedSpell (record; construtores da v0.6/v0.7 continuam)
Element primary();  int baseCost();  StageCost stageCost();  float instances();  SpellBreakdown breakdown();
record StageCost(int formCost, List<Integer> effectCosts, List<Float> effectMults, float copies, float instances,
                 int fixedCost, float triggers, float scale, int total)   // valores crus por estágio
```

## 5. Detalhamento (`ParsedSpell.breakdown()` → `glyph/SpellBreakdown`)
```java
record SpellBreakdown(Element primary, int manaCost, int baseCost, int castTicks, int cooldown, int glyphCount,
                      boolean ultimate, List<StagePart> stages, float damage, float heal, int perCopyExtra)
record StagePart(int index, FormGlyph form, LinkKind linkIn, int formCost, int fixedCost, float copies, float instances,
                 float triggers, float range, float radius, int durationTicks, List<EffectPart> effects,
                 int totalCost, float damage, float heal)
record EffectPart(EffectGlyph effect, SpellStats stats, int costPerCopy, float multiplier, float copies, int totalCost,
                  float damagePerHit, float damageTotal, float healPerHit, float healTotal, int durationTicks, float radius)
boolean heavyCopies();          // ≥ 50% do custo vem das cópias → o Grimório deve avisar
List<Component> lines();        // texto pronto (spell_info.isekai_mahou.*): totais, estágios, efeitos, aviso de cópias
static String num(float); static String seconds(int ticks);
```
Todos os custos já em mana real. Dano/cura são **estimativas** (`≈`): base do glifo × `stats.power()` × `DAMAGE_SCALE`
(sem crítico, reações, resistências, cântico, equipamento); duração = `stats.duration(base)`, raio = `stats.radius(base)`.
Os números base vêm de `EffectGlyph.baseDamage()/baseHeal()/hintDuration()/hintRadius()` e
`FormGlyph.hintRange()/hintRadius()/hintDuration()`; o padrão lê `glyph/GlyphHints` (tabela por id, extraída do código
dos módulos da v0.8/v0.9; `GlyphHints.set(id, new Hint(dano, cura, duraçãoTicks, raio, alcance))`).
**Glifos novos da fase B: sobrescrevam `baseDamage()` (e os outros) com o número que passam ao `ctx.damage`.**

## 6. Presets (rascunhos nomeados)
`glyph/SpellPreset(String name, SpellRecipe recipe)` (nome ≤ 32). Até **30** por jogador (`MagicData.MAX_PRESETS`),
salvos no `MagicData` (e sincronizados: o cliente lê `ClientMagic.data().presets()`).
```java
// MagicData (sem mensagens)
List<SpellPreset> presets(); SpellPreset preset(int); int presetIndex(String name);
int savePreset(String name, SpellRecipe);   // mesmo nome (sem maiúsculas) SUBSTITUI; -1 = cheio/vazio
boolean deletePreset(int); boolean renamePreset(int, String); String uniquePresetName(String base);
// MagicApi (servidor, com mensagem e som)
List<SpellPreset> presets(Player);          // funciona no cliente também
int savePreset(ServerPlayer, String name, SpellRecipe);   boolean deletePreset(ServerPlayer, int);
boolean renamePreset(ServerPlayer, int, String);
boolean loadPreset(ServerPlayer, int index, int loadout, int slot, MoveTrigger);   // grava no slot
```
Cliente → servidor: `PresetEdit.save(name, recipe)`, `PresetEdit.delete(i)`, `PresetEdit.load(i, loadout, slot,
trigger)`, `PresetEdit.rename(i, name)`. "Carregar no editor" é só do cliente (ler `presets().get(i).recipe()`).

## 7. Mana maior e Limite treinável (`core/classes/ResourceTraining`) — Mana, Vigor e Foco
```
L' = min(nível, 100)      M = marco (maior múltiplo de 10 ≤ L', mín. 1)
TETO(L,C) = 3·(100 + 12·(L'−1) + 40·(C−1)) + Progression.levelManaLimitBonus(L)
PISO(L,C) = 3·(100 +  5·(L'−1) +  3·(M−1) + 40·(C−1)) + levelManaLimitBonus(marco de L)/2      (≤ TETO)
LIMITE    = min(TETO, PISO + treino)          maxMana() = LIMITE + bônus de equipamento (atributo max_mana)
```
| nível/Círculo | piso | teto | +treino |
|---|---|---|---|
| 1 / C1 | 300 | 300 | 12 |
| 10 / C1 | 516 | 624 | 25 |
| 40 / C4 | 1596 | 2064 | 83 |
| 99 / C9 | 3531 | 4788 | 192 |
| 100 / C10 | 3756 | 4944 | 198 |
| 150 / C10 | 4056 | 5544 | 222 |
| 200 / C10 | 4356 | 6144 | 246 |

- **Treino:** esvaziar o recurso (≤ 2% do máximo, por gasto/dreno) depois de tê-lo enchido (≥ 95%) = 1 treino;
  **máx. 3 por dia do mundo** (`MagicApi.tryUseDaily(p, "resource_training", 3)`, contadores `daily.resource_training*`);
  cada treino soma `trainGain = max(6, 4% do TETO)` ao limite (até o teto do nível). O treino é permanente (subiu de nível
  → o teto cresce e há espaço de novo). Contador `resource_training` (+1 por treino) e `MagicData.trainings()`.
- **Fadiga:** todo esvaziamento deixa a regeneração em **×0,3 por 60 s** (modificador `isekai_mahou:resource_fatigue`,
  ADD_MULTIPLIED_TOTAL, no atributo `mana_regen`; `MagicData.fatigueUntil()`/`fatigued(gameTime)` sincronizados — o HUD
  pode mostrar). O HUD do núcleo deixa a barra cinza durante a fadiga.
- **Regen ×3:** modificador permanente `isekai_mahou:resource_scale` (**ADD_MULTIPLIED_TOTAL** +2 = ×3) no `mana_regen`,
  para a barra 3× maior encher no mesmo tempo da v0.8. É TOTAL (não BASE) para os bônus de equipamento
  ADD_MULTIPLIED_BASE (+20%...) continuarem valendo a mesma porcentagem. **Não escale `Progression.regenPerTick` de novo.**
  O atributo `mana_regen` agora fica em ≈3 (o teto do atributo é 10): telas que mostram "regen ×N" devem dividir por
  `ResourceTraining.MANA_SCALE`.
- Marcos: ao passar um múltiplo de 10 o jogador recebe "Marco do nível N! Limite de Mana: X (teto treinável Y)".
- `MagicData`: `manaLimit()`, `manaFloor()`, `manaCeiling()`, `manaTrained()`, `trainings()`, `fatigueUntil()`,
  `fatigued(t)`; transitórios `trainingArmed`, `emptiedPending`. `Progression.maxMana(level, circle)` (da v0.8) não é
  mais usado pelo núcleo.

## 8. Dano (`SpellContext.damage`)
`amount = base × stats.power() × powerMult × spell_power × Progression.DAMAGE_SCALE (1,5)` — todo dano de magia/técnica
(jogador ou mob). `SpellCost.damageScale()` devolve a constante.

## 9. O que cada módulo precisa saber
- **ui_grimoire:** use `SetLoadoutSlot`, `SelectLoadout`, `PresetEdit`, `breakdown()`/`lines()`; `d.loadoutCount()` = 9
  (não limite por `Progression.MAX_LOADOUTS`); elemento de uma receita = `SpellCost.primaryElement(r)`.
- **ui_hud:** "Loadout N/9" (`d.activeLoadout()`), carga `d.charge()`, cor do slot `LoadoutInput.color(r)`, barra com
  `manaLimit()/manaCeiling()` e fadiga `fatigued(gameTime)`.
- **Quem compara `manaCost()` com números fixos** (patamares, integridade, baterias): use `baseCost()` ou ×3.
- Gastos/ganhos de mana com números FIXOS em módulos (ex.: `consumeMana(20)`, `addMana(50)`) ficaram 3× mais baratos/fracos
  em relação ao máximo: escalem por `ResourceTraining.MANA_SCALE` ou usem fração de `maxMana()`.
