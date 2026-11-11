# API v0.9 — Classes (núcleo A1)

Contrato: `docs/V09_DESIGN.md` §2. Código: `core/MagicPath.java`, `core/ClassKits.java`, `core/MagicApi.java` (recurso),
`core/CoreGameplay.java` (iniciais), `core/classes/**`, `glyph/weapon/**`, `client/PathSelectionScreen.java`, `client/classes/**`.
Traduções: `src/main/lang/core_classes/`.

## 1. `MagicPath` — 15 classes em 3 categorias

```java
enum MagicPath {                       // ORDEM SALVA (o pacote de escolha usa o ordinal): novas classes só no FIM
    NONE, MAGE, WITCH, WARLOCK, DRUID, NECROMANCER,            // Mágicas (ids/ordinais da v0.1)
    WARRIOR, PALADIN, BARBARIAN, MONK, KNIGHT,                 // Guerreiras (v0.9)
    RANGER, ROGUE, BARD, ARTIFICER, HUNTER;                    // À Distância (v0.9)

    static final MagicPath[] PLAYABLE;        // as 15 (Mágicas, Guerreiras, À Distância)
    static List<MagicPath> byCategory(Category c);   // as 5 da categoria, na ordem da tela
    Category category();
    Resource resource();                      // MAGIC→MANA, MARTIAL→VIGOR, RANGED→FOCUS
    @Nullable Element primaryAffinity();      // 1º elemento despertado ao escolher (null só em NONE)
    List<Element> affinityElements();         // base de afinidade, com o principal primeiro
    List<Element> hardElements();             // base difíceis
    String weaponPrefix();                    // prefixo do Arsenal: mage_staff, witch_wand, pact_blade, druid_staff,
                                              // reaper_scythe, longsword, holy_mace, greataxe, ki_gauntlets, knight_blade,
                                              // longbow, twin_daggers, lute, arcane_crossbow, hunter_crossbow (NONE = mage_staff)
    @Nullable ResourceLocation kitWeapon();   // isekai_mahou:<prefixo>_t1 nas Guerreiras/À Distância; null nas Mágicas
    ResourceLocation iconTexture();           // textures/gui/class/<id>.png (pode não existir)
    MutableComponent displayName();           // "path.isekai_mahou.<id>" na cor da classe
    MutableComponent styleName();             // "path.isekai_mahou.<id>.style"
    MutableComponent description();           // "path.isekai_mahou.<id>.desc"
    int rgb(); int argb();
    ChatFormatting color;                     // cor de chat mais próxima (compatibilidade)
    Affinity affinity(Element);               // igual à v0.6
}
```

| classe | id | categoria | afinidade (principal primeiro) | difícil | arma do kit |
|---|---|---|---|---|---|
| Mago | mage | MAGIC | Fogo, Água, Terra, Vento | Sombra | — |
| Bruxa | witch | MAGIC | Água, Sombra | Luz | — |
| Warlock | warlock | MAGIC | Sombra, Fogo | Luz | — |
| Druida | druid | MAGIC | Terra, Água, Luz | Sombra | — |
| Necromante | necromancer | MAGIC | Sombra, Terra | Luz | — |
| Guerreiro | warrior | MARTIAL | Fogo, Vento | Sombra | `longsword_t1` |
| Paladino | paladin | MARTIAL | Luz, Terra | Sombra | `holy_mace_t1` |
| Bárbaro | barbarian | MARTIAL | Fogo, Terra | Luz | `greataxe_t1` |
| Monge | monk | MARTIAL | Vento, Água | Sombra | `ki_gauntlets_t1` |
| Cavaleiro | knight | MARTIAL | Terra, Luz | Vento | `knight_blade_t1` (+ `knight_shield`) |
| Patrulheiro | ranger | RANGED | Vento, Terra | Sombra | `longbow_t1` |
| Ladino | rogue | RANGED | Sombra, Vento | Luz | `twin_daggers_t1` |
| Bardo | bard | RANGED | Luz, Água | Sombra | `lute_t1` |
| Artífice | artificer | RANGED | Fogo, Terra | Água | `arcane_crossbow_t1` |
| Caçador | hunter | RANGED | Terra, Água | Fogo | `hunter_crossbow_t1` |

**Para os módulos:** troque `switch (path)` sem `default` por `path.weaponPrefix()` / `path.primaryAffinity()` /
`path.category()` sempre que der. `PathRules.PRIMARY` deve virar `path.primaryAffinity()` (trabalho do classes_martial).

```java
enum MagicPath.Category { MAGIC, MARTIAL, RANGED;   // id "magic" | "martial" | "ranged"
    Resource resource; MutableComponent displayName();  // "category.isekai_mahou.<id>"
    ResourceLocation iconTexture();                      // textures/gui/category/<id>.png
    static Category byId(String); }
enum MagicPath.Resource { MANA, VIGOR, FOCUS;       // id "mana" | "vigor" | "focus"
    int argb();          // MANA 0xFF7A5CFF, VIGOR 0xFFE0563B, FOCUS 0xFF3BC9A8
    int rgb();
    float passiveRegen;  // 1.0 / 0.35 / 0.5
    MutableComponent displayName();   // "resource.isekai_mahou.<id>" já na cor
    MutableComponent description();   // "resource.isekai_mahou.<id>.desc" (como o recurso volta)
    ResourceLocation iconTexture(); } // textures/gui/resource/<id>.png
```

## 2. Recurso por categoria (mesmo valor salvo de mana)

`MagicData.mana()/maxMana()/addMana()/consumeMana()` continuam iguais para todos — só o nome, a cor e a regen mudam.

```java
MagicApi.resource(Player)            // MagicPath.Resource (servidor: dados do jogador; cliente: ClientMagic.data())
MagicApi.resourceName(Player)        // "Mana"/"Vigor"/"Foco" colorido
MagicApi.resourceColor(Player)       // ARGB
MagicApi.noResourceMessage(Player)   // "Mana insuficiente!" | "Vigor insuficiente!" | "Foco insuficiente!" (sem estilo)
```
No cliente (HUD/Janela de Status) também dá para usar `ClientMagic.data().path().resource()`.
O `SpellCaster` já usa `noResourceMessage` nas falhas de conjuração.

Regras (`core/classes/ClassResources`):
- **Vigor:** regen passiva ×0,35 (modificador temporário `isekai_mahou:class_resource_regen` no atributo `mana_regen`,
  conferido a cada segundo); **+3 + 5% do máximo** por golpe corpo a corpo PRINCIPAL (não a varrida) com a barra de
  ataque ≥ 0,8; **+2** ao receber dano (máx. 1× a cada 10 ticks).
- **Foco:** regen passiva ×0,5; **+4** por projétil do jogador que acerta (uma vez por projétil por alvo): flechas/tridentes
  (`AbstractArrow`) e qualquer projétil com a tag de entidade `ClassResources.FOCUS_SHOT_TAG` (`"isekai_mahou.focus_shot"`);
  **+1/s** parado, no chão, agachado e mirando (puxando arco/usando item de mira, ou segurando besta carregada).
- Meditação vale para todos (o núcleo multiplica a regen, já reduzida).

```java
ClassResources.FOCUS_SHOT_TAG                 // projectile.addTag(...) — ex.: a forma `throw` do classes_ranged
ClassResources.addAimItem(Predicate<ItemStack>)  // conta como "mirando" (ex.: o alaúde do armory)
ClassResources.gain(ServerPlayer, float)      // dá recurso (respeita o máximo)
```

## 3. Técnicas de ARMA no motor de glifos (`glyph/weapon`, registradas em `CoreGlyphs.register()`)

| id | tipo | custo | tier/Círculo | regra |
|---|---|---|---|---|
| `strike` "Golpe" | FORMA | 4 | t1 C1 | o PRÓXIMO golpe corpo a corpo (até 6 s; Estender +50%) aplica os efeitos no alvo (`ctx.impact` + `resolveOnEntity`, 1 tick depois e sem a invencibilidade do golpe). Dividir = +1 golpe por cópia. Arma brilha (pó do elemento) e o golpe deixa um arco de luz. Conjurar de novo substitui. Mobs também usam (MOB_ATTACK). Recarga base 20 |
| `arrow` "Flecha" | FORMA, `stageable` | 5 | t1 C1 | com arco/besta (qualquer `ProjectileWeaponItem`) e flecha (ou criativo): FLECHA REAL do vanilla (`ArrowItem.createArrow`, crítica, dano base ×0,75; gasta 1 flecha — Infinidade vale — e 1 de durabilidade). Sem arco/flecha: flecha conjurada (dano base 0,8, não recolhível). Marcada nos dados persistentes (`WeaponArrows.KEY`), aplica os efeitos ao acertar entidade/bloco; Dividir (leque de 6°), Espalhar, Acelerar, Perfurar, Ricochete, Teleguiado e Expandir valem; 10 s voando sem acertar = Ao Terminar. Rastro do elemento. Estágio 2+ (e Saltar): flecha conjurada saindo do ponto do estágio, que ATRAVESSA o alvo anterior (nasce dentro dele). Recarga base 12 |
| `stance` "Postura" | FORMA (canal) | 6 | t2 C1 | enquanto a TECLA do slot está segurada, os efeitos se aplicam no conjurador a cada 1 s (`resolveOnSelf`); anda a 60% e +0,5 de resistência a empurrão; cada segundo custa 30% do custo da magia; soltar / sem recurso / morrer = acaba. Sem tecla (gatilho, mob, sequência) dura 5 s. Recarga base 30 |
| `parry` "Aparar" | GATILHO reativo | 4 | t2 C1 | o slot sai SOZINHO (pagando, respeitando recarga/silêncio), virado para o atacante, quando o jogador bloqueia com escudo (`LivingShieldBlockEvent`) ou é atingido corpo a corpo até 6 ticks depois de começar a atacar (nesse caso o dano cai 25%). Só conta (e só reduz o dano) se a técnica vai mesmo sair: receita válida, sem recarga, sem Silêncio/Exaustão e com recurso — faltando recurso, avisa "Vigor insuficiente!". Apertar a tecla do slot não conjura (aviso) |
| `guard` "Guarda" | EFEITO benéfico | 8 | t1 C1 | Resistência I por 5 s (Amplificar até III, Estender). Inverter: Fraqueza no inimigo. É o efeito da "Postura Firme" (o `shield` do Arcano é C2) |

Classes/consultas públicas:
```java
WeaponGlyphs.STRIKE / ARROW / STANCE / PARRY / GUARD (+ *_ID)
WeaponStrikes.arm(LivingEntity, SpellContext, int charges, int ticks); charges(e); disarm(e)
WeaponArrows.isSpellArrow(Entity); WeaponArrows.KEY
WeaponStances.inStance(e); stop(e)
WeaponParry.react(ServerPlayer, @Nullable LivingEntity attacker)   // dispara os slots com Aparar (ex.: um módulo de duelo)
WeaponParry.leadsWithParry(SpellRecipe)
MeleeSwings.mainHitStrength(Player, Entity target, long tick)       // força do golpe principal deste tick (-1 = não foi ele)
WeaponFx.handPos / weaponGlow / slash / groundRing / dust            // visual reaproveitável
```
Pacote C→S `classes_slot_keys` (`ClassesPayloads.SlotKeys(mask)`): o cliente avisa as teclas Z X C G seguradas
(`client/classes/ClassesCoreClient`), só quando mudam.

## 4. Glifos e magias iniciais (`CoreGlyphs.starter(category)`, `CoreGameplay.starterSpells(category)`)

- Todos: `CoreGlyphs.STARTER` (igual à v0.8).
- MARTIAL: + `strike`, `stance`, `parry`, `guard` (`CoreGlyphs.STARTER_MARTIAL`).
- RANGED: + `arrow` (`CoreGlyphs.STARTER_RANGED`).
- Magias prontas no loadout 1 do Arcano (só se todos os slots Arcanos estiverem vazios):
  - MAGIC: Z "Míssil Arcano" [projectile, arcane_damage], X "Onda de Choque" [touch, push, amplify] (como antes);
  - MARTIAL: Z "Golpe Arcano" [strike, arcane_damage], X "Postura Firme" [stance, guard], C "Contragolpe" [parry, touch, push];
  - RANGED: Z "Flecha Arcana" [arrow, arcane_damage], X "Flecha Repulsora" [arrow, push].
- Constantes: `CoreGameplay.STARTER_MISSILE/SHOCKWAVE/STRIKE/STANCE/COUNTER/ARROW/PUSH_ARROW` (nomes em português).
- `CoreGameplay.starterSpells(category, language)`: as mesmas, com os nomes das técnicas v0.9 no idioma do jogador
  (fora do português: Arcane Strike, Steadfast Stance, Counterstrike, Arcane Arrow, Repelling Arrow). A escolha de classe
  usa o idioma do cliente (`player.clientInformation().language()`); a tela usa o idioma selecionado. As Mágicas ficam
  como na v0.8.

## 5. Tela de classe e kits

- `client/PathSelectionScreen`: moldura da Janela de Status; 3 abas (ícone `textures/gui/category/<id>.png`, reserva:
  livro/espada/arco + dica da categoria), lista das 5 classes (ícone `textures/gui/class/<id>.png`, reserva
  `ClassKits.icon`), painel com nome, categoria · estilo, recurso (chip na cor + explicação), descrição, afinidades (o
  principal com losango), difícil, elemento inicial, técnicas prontas (com as teclas reais), arma inicial e kit (com
  dicas). Rolagem no painel se não couber. ←/→/Tab trocam a aba, ↑/↓ a classe; Esc continua bloqueado. "Escolher: X" manda
  `CorePayloads.ChoosePath(ordinal)`.
- `ClassKits.icon(path)`: peitoral registrado → arma do kit (`ClassKits.weapon(path)`) → item padrão por classe.
- `ClassKits.weapon(path)`: `ItemStack` da `kitWeapon()` (vazio se o Arsenal não registrou).
- `ClassKits.fallbackGear(path)`: arma do kit + (Cavaleiro) `knight_shield`, só os que existem. `ClassKits.give` entrega
  esses itens no FIM se nenhum item/armadura registrado pelos módulos for o mesmo item — a classe nunca começa
  desarmada, e quem registrar a arma via `addItem` não ganha cópia dupla.
- Kits das classes novas: os módulos registram com `ClassKits.addItem(path, () -> stack)` / `setArmor(...)` resolvendo
  itens POR ID e tolerando ausência (fornecedor que lança exceção ou devolve vazio é ignorado).

## 6. Traduções (`src/main/lang/core_classes`)
`category.isekai_mahou.<id>`, `resource.isekai_mahou.<id>[.desc]`, `path.isekai_mahou.<10 novas>[.style|.desc]` (as 5
antigas continuam em `core`), `glyph.isekai_mahou.<strike|arrow|stance|parry|guard>[.desc]`,
`screen.isekai_mahou.classes.*`, `message.isekai_mahou.no_resource`, `message.isekai_mahou.classes.*`.

## 7. Avisos para os módulos da fase B
- Ids de glifo já usados no núcleo NÃO podem ser registrados de novo (o `GlyphRegistry` lança exceção e o jogo não abre).
  Em especial **`charge` já é o aumento "Carregar"** do núcleo: a forma "Investida" do `classes_martial` precisa de
  outro id (ex.: `charge_rush`). Também ocupados: `strike`, `arrow`, `stance`, `parry`, `guard`, `split`, `channel`...
- Projéteis próprios que devem encher o Foco: `projectile.addTag(ClassResources.FOCUS_SHOT_TAG)` antes do `addFreshEntity`.
- Item que conta como "mirando" (alaúde etc.): `ClassResources.addAimItem(stack -> ...)` no init.
