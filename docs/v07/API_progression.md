# API v0.7 — Progressão, APIs do núcleo e UI (trilha A2)

Tudo aqui é ADITIVO: o que compilava na v0.6 continua compilando e funcionando igual (exceto o que o
`V07_DESIGN.md` mandou mudar). Pacote base `com.renanpontes.isekaimahou` (abreviado `J.`).

Resumo do que existe:

| Onde | O quê | Lado |
|---|---|---|
| `J.core.Progression` | fórmulas v0.7 (+ `killXp`, `killElementXp`, `KILL_SHARE_RADIUS`) | comum |
| `J.core.MagicData` / `MagicApi` | **banco de XP** no limite do Círculo | comum/servidor |
| `J.core.CoreEvents` | XP de abate em **grupo**, vida por nível | servidor |
| `J.core.CoreGameplay` | magias iniciais do Arcano, `updateGrowthHealth`, `GROWTH_HEALTH` | servidor |
| `J.core.api.TrialsApi` | Câmaras de Provação: handler, flags, requisitos, `markCleared` | comum/servidor |
| `J.core.api.Rewards` | `give`, `toast`, `banner` | servidor |
| `J.core.api.Legends` | registro de Magias Lendárias + pergaminhos | comum |
| `J.network.CorePayloads.RequestTrial` / `RewardToast` | pacotes novos | comum |
| `J.core.MahouConfig` | 6 opções novas | comum/cliente |
| `J.client.objectives.ObjectiveTracker` / `ObjectiveLine` | rastreador de objetivos na tela | **cliente** |
| `J.client.GlyphArt` | ícones de glifo/elemento/tipo/magia/aba | **cliente** |
| `J.client.MahouUi` | peças de desenho do estilo "Janela de Status" | **cliente** |
| `J.client.RewardToast` | o toast com moldura arcana | **cliente** |
| `J.client.menu.MenuTabs.setIcon` / `StatusScreen` | menu B repaginado | **cliente** |
| `J.client.CoreKeys.TRACKER` | tecla J | **cliente** |

> Regra de ouro: classes em `J.client.**` só podem ser usadas a partir do `client/` do seu módulo
> (`initClient`, telas, camadas de HUD). Nunca no `init` comum nem em handlers do servidor.

---------------------------------------------------------------------------------------------------
## 1. Progressão (XP, banco, abates, vida)

### 1.1 Fórmulas (`core.Progression`, coordenador; A2 só ACRESCENTOU os itens marcados com ★)
```java
int  Progression.xpToNext(int level)                 // 25 + 10·L
int  Progression.xpForLevels(int fromLevel, int levels)
int  Progression.levelCap(int circle)                // circle·10
int  Progression.bankCap(int level)                  // XP dos próximos 5 níveis (tamanho do banco)
int  Progression.maxGlyphs(int level, int circle)    // min(40, 4 + nível/4 + círculo)
int  Progression.maxSpellElements(int circle)        // 2 / 3 / 4
int  Progression.bonusHealth(int level)              // +2 de vida a cada 5 níveis (máx. +30)
int  Progression.arcaneUnlockLevel(int tier)         // {1, 5, 10, 18, 28}
List<String> Progression.TRIAL_NODES                 // apprentice..archmage (rank 1..7)
int  Progression.MAX_TRIAL_RANK                      // 7
int  Progression.trialElementLevel(int rank) / trialCircle(int rank) / trialRankOfNode(String node)
double Progression.KILL_SHARE_RADIUS                 // ★ 24.0 blocos
int  Progression.killXp(float maxHealth, boolean spellKill)  // ★ magia max(3, vida/2) · outro max(2, vida/4)
int  Progression.killElementXp(float maxHealth)      // ★ max(2, vida/3)
```

### 1.2 Banco de XP (XP no limite do Círculo NÃO se perde)
- `MagicApi.giveXp(sp, n, source)` continua sendo o ÚNICO jeito de dar XP de nível. No limite do Círculo
  (`d.atLevelCap()`), o XP vai para o banco, até `d.bankCap()` (= `Progression.bankCap(nível)`); o que passar
  disso é perdido. Ao chegar no limite, o jogador recebe UMA vez a mensagem do limite + "XP guardado"; quando o
  banco enche, um aviso na barra de ação.
- `MagicApi.setCircle(sp, c)`: ao SUBIR, dispara `CircleChanged` e logo depois aplica o banco como XP normal
  (mensagem "XP guardado aplicado", mensagens de nível e eventos `LevelUp`).
- Leitura (servidor e cliente — vem no `MagicSync`):
```java
MagicData d = MagicApi.get(player);      // ou ClientMagic.data() no cliente
int bank = d.xpBank();                    // XP guardado
int cap  = d.bankCap();                   // tamanho máximo do banco agora
boolean full = d.bankFull();
```
- Persistência: campo opcional `extra.xp_bank` no codec do `MagicData` (saves antigos abrem com 0).
  Novos campos opcionais do `MagicData` devem entrar no record `Extra` (o codec principal já tem 16 campos).
- `/mahou info` mostra uma linha extra com o banco quando ele não está vazio.

> ATENÇÃO para quem ouve `MagicProgressEvent.LevelUp` (ex.: marcos de nível do armory): um único evento pode
> pular VÁRIOS níveis (`oldLevel → newLevel`, ex.: banco aplicado ou XP grande). Trate todos os níveis do
> intervalo `(oldLevel, newLevel]`, e use uma flag por marco para não entregar duas vezes:
```java
NeoForge.EVENT_BUS.addListener((MagicProgressEvent.LevelUp e) -> {
    MagicData d = MagicApi.get(e.getPlayer());
    for (int lv = e.oldLevel + 1; lv <= e.newLevel; lv++) {
        if (lv % 5 != 0 || d.hasFlag("armory.milestone." + lv)) continue;
        d.setFlag("armory.milestone." + lv, true);
        // ... entregar a recompensa do nível lv
    }
});
```

### 1.3 XP por abate (em grupo)
`CoreEvents.onDeath` (prioridade LOWEST, ignora mortes canceladas):
- Só conta se quem matou é um `ServerPlayer` de verdade (`FakePlayer` não ganha nada) e a vítima não é jogador.
- Quem matou: contadores `kill.<ns>.<mob>`, `kill.total`, `kill.spell.<elemento>` (se foi magia) e XP de
  elemento `Progression.killElementXp(vida)` no elemento do último dano (`ElementReactions.lastElement`).
- XP de NÍVEL `Progression.killXp(vida, foiMagia)` para quem matou **e** para todo jogador vivo, não espectador,
  na mesma dimensão, a ≤ `KILL_SHARE_RADIUS` (24) blocos do mob — todos ganham o valor CHEIO.
- Chefes que dão bônus próprio (ex.: `BossRewards.grant` da trilha A3) somam por cima disso.

### 1.4 Vida que cresce com o nível
- Modificador de `Attributes.MAX_HEALTH` com id `isekai_mahou:growth_health` (`CoreGameplay.GROWTH_HEALTH`),
  operação `ADD_VALUE`, valor `Progression.bonusHealth(nível)`.
- Atualizado em `LevelUp`, `CircleChanged`, login, respawn, troca de dimensão e `/mahou reset`.
  Ao aumentar, cura a diferença; ao diminuir, só limita a vida atual.
- O modificador é salvo com o jogador (permanente) — assim a vida salva não é cortada ao entrar no mundo — mas
  é recalculado em todos os eventos acima, então nunca fica desatualizado. Na saída do End (`PlayerEvent.Clone`
  sem morte) o núcleo devolve o modificador e a vida que o jogador tinha. Para forçar:
  `CoreGameplay.updateGrowthHealth(serverPlayer)`.
- Não crie outro modificador com o id `isekai_mahou:growth_health`; bônus de vida de equipamentos/acessórios usam
  ids próprios (ex.: `isekai_mahou:vitality_charm`).
- Para MOSTRAR (aba Status, Olho Analítico): `player.getMaxHealth()` já inclui o bônus;
  o bônus sozinho é `Progression.bonusHealth(d.level())`.

### 1.5 Magias iniciais (ao escolher a classe)
`CoreGameplay.choosePath` (pacote `ChoosePath`): se TODOS os slots do Arcano estiverem vazios, grava no
loadout 1 do Arcano:
- Z (slot 0): `CoreGameplay.STARTER_MISSILE` = "Míssil Arcano" `[projectile, arcane_damage]` (cor `0xC9A0FF`)
- X (slot 1): `CoreGameplay.STARTER_SHOCKWAVE` = "Onda de Choque" `[touch, push, amplify]` (cor `0x8FD8FF`)

e manda uma mensagem com as teclas reais do jogador. O evento `PathChosen` é postado DEPOIS disso (o módulo
paths desperta o elemento de afinidade e grava a magia desse elemento no seu `PathChosen`).

---------------------------------------------------------------------------------------------------
## 2. `core.api.TrialsApi` — Câmaras de Provação

O núcleo só guarda progresso e encaminha pedidos; o módulo `trials` implementa tudo instalando um `Handler`.

```java
public interface TrialsApi.Handler {
    boolean canStart(ServerPlayer player, Element element, int rank, List<Component> reasons);
    void start(ServerPlayer player, Element element, int rank);
}
static void      TrialsApi.set(@Nullable Handler h)            // instala no init do módulo trials
static @Nullable Handler TrialsApi.handler()
static String    TrialsApi.flag(Element e, int rank)            // "trial.<el>.<rank>"
static boolean   TrialsApi.isCleared(MagicData d, Element e, int rank)
static int       TrialsApi.clearedRank(MagicData d, Element e)  // maior N com a flag (0 = nenhum)
static int       TrialsApi.nextRank(MagicData d, Element e)     // próximo rank a fazer (0 = tudo feito)
static boolean   TrialsApi.meetsRequirements(MagicData d, Element e, int rank, List<Component> missing)
static String    TrialsApi.roman(int rank)                      // "I".."VII"
static MutableComponent TrialsApi.title(Element e, int rank)    // "Provação de Fogo III" (cor do elemento)
static void      TrialsApi.markCleared(ServerPlayer p, Element e, int rank)
static void      TrialsApi.request(ServerPlayer p, Element e, int rank)
static void      TrialsApi.request(ServerPlayer p, String elementId, int rank)
```

- `meetsRequirements` aplica a tabela do §3 (elemento despertado e não Arcano, rank anterior concluído,
  `trialElementLevel(rank)`, `trialCircle(rank)`) e adiciona UMA linha traduzida por requisito que falta.
  Funciona no cliente com `ClientMagic.data()` — use nas telas (aba Caminhos) para mostrar o que falta.
  O `Handler.canStart` do módulo trials deve chamá-la e acrescentar os próprios motivos (ex.: limite de
  instâncias `MahouConfig.TRIAL_MAX_INSTANCES`, já está numa provação...). Ranks já concluídos podem ser
  repetidos (Desafio): `meetsRequirements` continua true para eles.
- `markCleared` grava a flag `trial.<el>.<rank>` e soma os contadores `trial.<el>.<rank>`, `trial.clear` e
  `trial.clear.<el>` via `MagicApi.addCounter` (cada um dispara `MagicProgressEvent.Counter`; a flag já está
  gravada quando o evento chega). O módulo paths libera o nó `Progression.TRIAL_NODES.get(rank - 1)` ouvindo
  esse evento (ou checando `TrialsApi.isCleared`).
- `request` (chamado pelo pacote `RequestTrial`): ignora elemento/rank inválidos, tem anti-spam de 0,75 s,
  sem handler → mensagem "Câmaras de Provação não disponíveis"; `canStart` false → lista os motivos no chat;
  senão chama `start`.

Exemplo — módulo trials (init comum):
```java
TrialsApi.set(new TrialsApi.Handler() {
    @Override public boolean canStart(ServerPlayer p, Element e, int rank, List<Component> reasons) {
        boolean ok = TrialsApi.meetsRequirements(MagicApi.get(p), e, rank, reasons);
        if (TrialInstances.active() >= MahouConfig.TRIAL_MAX_INSTANCES.get()) {
            reasons.add(Component.translatable("message.isekai_mahou.trials.full"));
            ok = false;
        }
        return ok;
    }
    @Override public void start(ServerPlayer p, Element e, int rank) { TrialInstances.open(p, e, rank); }
});
// na vitória, para cada jogador elegível:
TrialsApi.markCleared(sp, element, rank);
```
Exemplo — botão "Iniciar Provação" (cliente, aba Caminhos):
```java
PacketDistributor.sendToServer(new CorePayloads.RequestTrial(element.id, rank));
```
Exemplo — mostrar requisitos (cliente):
```java
List<Component> missing = new ArrayList<>();
boolean ok = TrialsApi.meetsRequirements(ClientMagic.data(), element, rank, missing);
```

---------------------------------------------------------------------------------------------------
## 3. `core.api.Rewards` — entregar recompensas (servidor)

```java
static final int Rewards.GOLD = 0xFFD86B;
static void Rewards.give(ServerPlayer p, ItemStack stack)          // inventário; o que não couber cai aos pés (sem demora, com dono)
static void Rewards.giveAll(ServerPlayer p, List<ItemStack> stacks)
static void Rewards.toast(ServerPlayer p, Component title, Component subtitle, ItemStack icon)            // cor dourada
static void Rewards.toast(ServerPlayer p, Component title, Component subtitle, ItemStack icon, int rgb)
static void Rewards.banner(ServerPlayer p, Component text, int rgb) // faixa de sistema no topo (Announce STYLE_SYSTEM)
```
- `give` copia o stack (o seu não é alterado) e toca o som de pegar item.
- `toast`: pacote `CorePayloads.RewardToast` → toast no canto superior direito com moldura arcana, ícone num
  medalhão, título na cor `rgb`, subtítulo, reflexo de luz ao aparecer e um "plim". Dura ~6 s. Título/subtítulo
  longos são cortados com "…" (largura 160–240 px). `icon` pode ser `ItemStack.EMPTY` (aparece uma gema).
- `banner`: o mesmo banner de "Missão atualizada" do Diário, só para esse jogador.

```java
Rewards.give(sp, new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 4));
Rewards.toast(sp, Component.translatable("toast.isekai_mahou.armory.level_reward", 20),
        armaT2.getHoverName(), armaT2, 0xFFD86B);
Rewards.banner(sp, TrialsApi.title(Element.FIRE, 3).append(" — Vitória!"), Element.FIRE.color);
```

---------------------------------------------------------------------------------------------------
## 4. `core.api.Legends` — Magias Lendárias

Registro COMUM (popule no `init` do módulo — roda no cliente e no servidor; a galeria do Grimório lê o mesmo).

```java
record Legends.Legend(String id, SpellRecipe recipe, Element element, int minCircle) {
    MutableComponent title();   // tradução "legend.isekai_mahou.<id>" se existir, senão o nome da receita
    int color();                // RGB: cor da receita, ou a do elemento
}
static Legend           Legends.register(String id, SpellRecipe recipe, Element element, int minCircle) // id repetido substitui;
                        // id fora de [a-z0-9_]+ ou receita vazia → IllegalArgumentException (erro no init, não no jogo)
                        // element null → ARCANE; minCircle < 1 → 1
static List<Legend>     Legends.all()                    // ordem de registro (cópia)
static @Nullable Legend Legends.get(String id)
static ItemStack        Legends.scroll(String id)        // spell_scroll com a receita; EMPTY se não existe
static ItemStack        Legends.scroll(Legend legend)
static ItemStack        Legends.randomScroll(RandomSource random)                 // EMPTY se não há lendas
static ItemStack        Legends.randomScroll(RandomSource random, int maxCircle)  // só minCircle <= maxCircle (se nenhuma, qualquer uma)
```
- O pergaminho tem nome dourado "Pergaminho Lendário: <título>", raridade ÉPICA, brilho e descrição
  (Magia Lendária · Elemento · Círculo mínimo). Usar o pergaminho copia a magia para a biblioteca do Grimório
  (comportamento normal do `spell_scroll`).
- `element` = elemento primário (o do slot). Receitas multi-elemento são permitidas (motor A1).
- Comandos: `/mahou legend list` (qualquer jogador; para ops cada linha é clicável) e
  `/mahou legend give <id> [jogadores]` (op).
- Traduza o nome: `"legend.isekai_mahou.explosion": "Explosão!"` (pt_br e en_us no lang do SEU módulo).

```java
// init do módulo legends_a
Legends.register("explosion", new SpellRecipe(List.of(IsekaiMahou.id("projectile"), IsekaiMahou.id("crater"),
        IsekaiMahou.id("all_in"), IsekaiMahou.id("colossal")), "Explosão!", 0xFF5A2B, "Ó negra chama..."), Element.FIRE, 4);
// recompensa de Provação rank VI–VII (módulo trials)
Rewards.give(sp, Legends.randomScroll(sp.getRandom(), MagicApi.get(sp).circle()));
```

---------------------------------------------------------------------------------------------------
## 5. Pacotes novos (`network.CorePayloads`, registrador "core-2")

| Pacote | Direção | Campos | O que faz |
|---|---|---|---|
| `RequestTrial` | C→S | `String element, int rank` | `TrialsApi.request(sp, element, rank)` |
| `RewardToast` | S→C | `Component title, Component subtitle, ItemStack icon, int rgb` | `ClientHooks.toast` (padrão: `RewardToast` do núcleo) |

`ClientHooks.toast` (`Consumer<CorePayloads.RewardToast>`) pode ser trocado por um módulo de UI, como os outros
ganchos. Prefira sempre `Rewards.toast(...)` em vez de mandar o pacote na mão.

---------------------------------------------------------------------------------------------------
## 6. `core.MahouConfig` — opções novas

| Campo | Arquivo | Padrão | Uso |
|---|---|---|---|
| `SPELL_BLOCK_DESTRUCTION` (`BooleanValue`) | common | true | lendas podem abrir crateras/apagar blocos (respeite também `mobGriefing`) |
| `TRIAL_MAX_INSTANCES` (`IntValue`, 1–64) | common | 8 | máximo de Câmaras de Provação ativas |
| `PEDESTAL_MAX_PER_CHUNK` (`IntValue`, 0–64) | common | 4 | limite de Pedestais Rúnicos por chunk |
| `SHOW_OBJECTIVES` (`BooleanValue`) | client | true | rastreador de objetivos (a tecla J alterna e salva) |
| `SHOW_TARGET_INFO` (`BooleanValue`) | client | true | Olho Analítico (ui_hud) |
| `FLOATING_HEALTH_BARS` (`BooleanValue`) | client | true | barrinhas de vida flutuantes (ui_hud) |

Ler: `MahouConfig.TRIAL_MAX_INSTANCES.get()`. Opções do cliente só no cliente.

---------------------------------------------------------------------------------------------------
## 7. Rastreador de objetivos (cliente) — `client.objectives`

```java
record ObjectiveLine(Component title, Component detail, int current, int target, int rgb, ItemStack icon,
                     @Nullable Vec3 targetPos, @Nullable ResourceKey<Level> targetDim) {
    static ObjectiveLine of(Component title, Component detail, int rgb, ItemStack icon)               // sem barra
    static ObjectiveLine progress(Component title, Component detail, int current, int target, int rgb, ItemStack icon)
    ObjectiveLine withTarget(Vec3 pos, @Nullable ResourceKey<Level> dim)       // seta + distância
    ObjectiveLine withTarget(BlockPos pos, @Nullable ResourceKey<Level> dim)
    float fraction()
}
static void ObjectiveTracker.register(String id, int priority, Supplier<List<ObjectiveLine>> provider) // mesmo id substitui
static void ObjectiveTracker.unregister(String id)
static void ObjectiveTracker.reserveTop(String id, IntSupplier heightPx)   // outro HUD no mesmo canto empurra o painel
static void ObjectiveTracker.releaseTop(String id)
static List<ObjectiveLine> ObjectiveTracker.lines()                        // linhas do último tick
static boolean ObjectiveTracker.isEnabled() / setEnabled(boolean) / toggle()
static final int ObjectiveTracker.MAX_LINES = 6, TOP = 56, WIDTH = 160
```
- `ObjectiveLine` está em `client/objectives` porque só o cliente cria/lê linhas (os provedores rodam no
  cliente com dados já sincronizados). Os tipos dela são comuns, mas NÃO a use em código de servidor.
- Campos: `detail` vazio → linha baixa (só título); `target <= 0` → sem barra e sem "n/m"; `rgb` sem alpha;
  `icon` pode ser `ItemStack.EMPTY`; `targetPos` != null → setinha que gira apontando para o ponto + distância
  em blocos (e ↑/↓ se o alvo estiver bem acima/abaixo); `targetDim` diferente da dimensão atual → "outra dimensão".
- Provedores são chamados a cada TICK do cliente (não a cada quadro): podem calcular a partir de
  `ClientMagic.data()`, estado sincronizado do módulo etc. Devolva `List.of()` quando não há nada.
  Exceção num provedor é registrada no log uma vez e o provedor é pulado.
- Ordem: prioridade MAIOR primeiro (empate = ordem de registro). Sugestões: Provação/chefe em andamento 1000 ·
  Ascensão (núcleo, `CoreObjectives`, id `isekai_mahou:core`) 600 · história do Diário 500 · Guilda 300 · dicas 100.
- Desenho: canto superior direito, x = largura − 164, y = 56 (+ reservas), cabeçalho "Objetivos [J]", até 6 linhas
  sem descer na área da hotbar. Some com F1, F3, telas abertas (menos o chat) e com `SHOW_OBJECTIVES` = false.
  Tecla `CoreKeys.TRACKER` (J, categoria Isekai Mahou) alterna e salva na config.
- Provedor embutido do núcleo: no limite do Círculo mostra "Ascensão disponível: Círculo N" com
  "Faça o Ritual de Ascensão" ou, se houver banco, "XP guardado: N / M" com barra.
- Painel do pacto do Warlock (y=56, mesmo canto): deve chamar `reserveTop("warlock_pact", () -> visível ? altura + 4 : 0)`.

Exemplos ILUSTRATIVOS (`JournalBook`, `step.title()`, `TrialsClient` etc. são classes/métodos do SEU módulo — adapte):
```java
// JournalClient.initClient(modBus)
ObjectiveTracker.register("journal", 500, () -> {
    JournalStep step = JournalBook.currentStep();
    if (step == null) return List.of();
    return List.of(ObjectiveLine.progress(step.title(), step.hint(), step.current(), step.target(),
            0x9FD8FF, new ItemStack(Items.WRITABLE_BOOK)));
});
// Provação em andamento, com seta para o altar:
ObjectiveLine.progress(TrialsApi.title(el, rank), Component.translatable("...wave", 2, 4), killed, total, el.color, icon)
        .withTarget(altarPos, TrialsClient.dimension());
```

---------------------------------------------------------------------------------------------------
## 8. Ícones — `client.GlyphArt` (cliente)

```java
static void GlyphArt.glyph(GuiGraphics g, Glyph glyph, int x, int y, int size)
static void GlyphArt.element(GuiGraphics g, Element element, int x, int y, int size)
static void GlyphArt.kind(GuiGraphics g, Glyph glyph, int x, int y, int size)                 // ligações → "link"
static void GlyphArt.kind(GuiGraphics g, GlyphKind kind, boolean link, int x, int y, int size)
static void GlyphArt.spell(GuiGraphics g, @Nullable SpellRecipe recipe, Element element, int x, int y, int size)
static void GlyphArt.tab(GuiGraphics g, String tabId, int x, int y, int size)
static void GlyphArt.icon(GuiGraphics g, ResourceLocation texture, int x, int y, int size)    // qualquer textura inteira, escalada
static ResourceLocation GlyphArt.glyphTexture(Glyph) / elementTexture(Element) / kindTexture(GlyphKind, boolean) / tabTexture(String)
static String  GlyphArt.kindName(GlyphKind kind, boolean link)   // form|effect|augment|trigger|link|law
static int     GlyphArt.kindColor(GlyphKind kind, boolean link)  // ARGB padrão de cada tipo
static int     GlyphArt.color(Glyph)                             // cor do glifo (Origem = cor da classe)
static boolean GlyphArt.isLink(Glyph)                            // TriggerGlyph.isLink()
static @Nullable Glyph GlyphArt.mainGlyph(SpellRecipe)           // 1º efeito, senão a forma
static Set<Element> GlyphArt.extraElements(SpellRecipe, Element primary)
static boolean GlyphArt.exists(ResourceLocation texture)         // cache, limpo na recarga de recursos
static void    GlyphArt.clearCache()
```
Texturas (§10), desenhadas inteiras e escaladas para `size`×`size` (pixel art nítido em múltiplos do tamanho):

| Ícone | Arquivo | Reserva se faltar |
|---|---|---|
| glifo | `textures/gui/glyph/<path do id>.png` (32×32) | quadrado arredondado na cor do elemento (Origem: cor da classe) + símbolo do tipo + pontinhos de tier |
| elemento | `textures/gui/element/<id>.png` (16×16) | runa de `textures/gui/mage_runes.png`; senão disco na cor + inicial |
| tipo | `textures/gui/glyph_kind/<form\|effect\|augment\|trigger\|link\|law>.png` (16×16) | símbolo na cor do tipo |
| aba | `MenuTabs.icon(id)` = `setIcon` ou `textures/gui/tab/<id>.png` (16×16) | quadrado arcano com a inicial do título |

`spell(...)`: fundo escuro na cor da magia, ícone do glifo principal (ou as 2 letras de `CoreHud.abbreviation`
se o glifo não tem textura), borda na cor da magia, runa do elemento primário no canto inferior direito
(size ≥ 14) e pontinhos coloridos no topo para os outros elementos usados (size ≥ 16). Receita null/vazia =
espaço vazio. Para tingir/esmaecer: `g.setColor(1, 1, 1, 0.5F)` antes e `g.setColor(1, 1, 1, 1)` depois.

```java
GlyphArt.glyph(g, glyph, x, y, 20);                      // paleta do Grimório
GlyphArt.spell(g, slot.main(), element, x, y, 20);       // slot do HUD
GlyphArt.element(g, Element.SUN, x, y, 12);              // chip de runa
```

---------------------------------------------------------------------------------------------------
## 9. Menu B — `client.menu`

```java
static void MenuTabs.register(String id, int order, Component title, Supplier<MenuTab> factory)  // igual à v0.6
static void MenuTabs.setIcon(String id, ResourceLocation texture)                               // NOVO
static ResourceLocation MenuTabs.icon(String id)                                                // NOVO
```
Ordem sugerida: status 0 · paths 10 · grimoire 20 · loadouts 30 · glyphs 40 · journal 50 · guild 60 ·
bestiary 70 · trials 80 · legends 90.

`StatusScreen` (API antiga mantida: `open(id)`, `switchTab`, `refreshTab`, `addTabWidget`, `panelLeft/Top/Width/Height`;
novos: `currentTab()`, `cycleTab(int dir)`, constantes `MAX_PANEL_W = 520`, `MAX_PANEL_H = 300`):
- Painel `min(largura−16, 520) × min(altura−16, 300)`, centralizado; moldura arcana (gradiente escuro, borda
  dupla dourada + cor da classe, cantos ornamentados) e placa "Janela de Status" no meio da borda de cima.
- Barra de abas própria (não usa mais botões do vanilla): ícone + nome; aba ativa dourada e "aberta" para o
  conteúdo; hover clareia. Se não couber: só a ativa com nome e as outras só com ícone; em telas muito estreitas,
  só ícones (o nome da aba ativa vai para a placa do título). Abas sem nome mostram tooltip. Nunca passa do painel.
- Área da aba: **(left+6, top+26, panelW−12, panelH−32)** — igual à v0.6, só maior (até 508×268).
- Trocar de aba: clique, roda do mouse sobre a barra, Ctrl+Tab / Ctrl+Shift+Tab, Page Down / Page Up
  (a aba recebe as teclas primeiro; se ela consumir, a tela não troca).

---------------------------------------------------------------------------------------------------
## 10. `client.MahouUi` — peças de desenho (cliente, opcional)

Para módulos de UI ficarem com o mesmo visual do menu/toast/rastreador:
```java
MahouUi.GOLD, GOLD_DIM, GOLD_PALE, INK_TOP, INK_BOTTOM, EDGE_DARK, TEXT, TEXT_DIM, ARCANE   // ARGB
static int  MahouUi.alpha(int argb, int alpha) / mix(int a, int b, float t) / lighten(int, float) / darken(int, float)
static void MahouUi.frame(GuiGraphics g, int x, int y, int w, int h, int accentArgb)  // moldura arcana completa
static void MahouUi.roundRect(GuiGraphics g, int x, int y, int w, int h, int topArgb, int bottomArgb)
static void MahouUi.roundOutline(GuiGraphics g, int x, int y, int w, int h, int argb)
static void MahouUi.brackets(GuiGraphics g, int x, int y, int w, int h, int len, int argb)
static void MahouUi.corners(GuiGraphics g, int x, int y, int w, int h)
static void MahouUi.diamond(GuiGraphics g, int cx, int cy, int r, int argb)
static void MahouUi.bar(GuiGraphics g, int x, int y, int w, int h, float frac, int argb)
static FormattedCharSequence MahouUi.fit(Font font, Component text, int width)       // corta com "…"
static void MahouUi.smallText(GuiGraphics g, Font font, Component text, float x, float y, float scale, int argb, boolean shadow)
static void MahouUi.sprite(GuiGraphics g, int x, int y, int u, int v, int w, int h)  // folha core_ui.png
```
Folha `textures/gui/core_ui.png` (64×32, `tools/gen/core_ui.py`): seta (0,0,16,16), cantos (16,0)/(24,0)/(16,8)/(24,8)
8×8, gema (32,0,7,7), estrela (40,0,7,7).

---------------------------------------------------------------------------------------------------
## 11. Traduções novas (src/main/lang/core)
`message.isekai_mahou.xp_banked|xp_bank_full|xp_bank_applied|starter_spells|trial_unavailable|trial_blocked|trial_cannot_start`,
`core.isekai_mahou.trial.title` ("Provação de %s %s"), `core.isekai_mahou.trial.req.*`,
`item.isekai_mahou.spell_scroll.legend`, `tooltip.isekai_mahou.legend.*`, `command.isekai_mahou.legend.*`,
`command.isekai_mahou.info_bank`, `objectives.isekai_mahou.*`, `key.isekai_mahou.tracker`.
Não reutilize essas chaves com outro texto. Prefixos livres para os módulos: `legend.isekai_mahou.<id>`
(nome das lendas) e os do próprio módulo.
