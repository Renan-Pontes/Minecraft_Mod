# API v1.0 — A3 UX: menu principal, entrada, HudLayout, teleporte de mapa, ArenaBounds

Núcleo novo da v1.0 (contrato: `docs/V10_DESIGN.md` §2 A3). Init: `core/ux/UxCore.init` (servidor/comum) e
`client/ux/UxCoreClient.init` (cliente). Traduções: `src/main/lang/core_ux/`. Pacotes: registrar `"1"`, ids
`isekai_mahou:core_ux_*` (`core_ux_arena_sync`).

| Arquivo | O quê |
|---|---|
| `core/api/ArenaBounds.java` | API de limites de arena (servidor) |
| `core/ux/{UxCore,UxPayloads,ArenaService,MapTeleportGuard,EntryFlow}.java` | servidor/comum |
| `client/hud/{HudLayout,MinimapProbe,HudToasts}.java` | layout fixo do HUD + avisos da esquerda |
| `client/title/{TitleHook,MahouTitleScreen,TitleSky,TitleLogo,RuneButton}.java` | menu principal |
| `client/ux/{UxCoreClient,ClientArenas,ArenaWallRenderer,ArenaNotice,UxClientHandlers,XaeroSeed}.java` | cliente |
| `core/MahouConfig.java` | configs novas (abaixo) |

---------------------------------------------------------------------------------------------------
## 1. Configs novas (`core/MahouConfig`)

| Constante | arquivo | chave | padrão | para quê |
|---|---|---|---|---|
| `BLOCK_MAP_TELEPORT` | common | `blockMapTeleport` | `true` | cancela `/tp`, `/teleport`, `/jtp` (e `execute ... run tp`) mandados por JOGADOR fora do criativo/espectador |
| `VANILLA_SCALING` | common | `vanillaScaling` | `true` | mobs vanilla mais fortes (o A1 aplica) |
| `VANILLA_HEALTH_MULT` | common | `vanillaHealthMultiplier` | `1.5` | vida dos mobs vanilla com `VANILLA_SCALING` |
| `VANILLA_DAMAGE_MULT` | common | `vanillaDamageMultiplier` | `1.25` | dano dos mobs vanilla com `VANILLA_SCALING` |
| `CUSTOM_TITLE_SCREEN` | client | `customTitleScreen` | `true` | menu principal do Isekai Mahou |
| `SHOW_ARENA_BOUNDS` | client | `showArenaBounds` | `true` | parede das arenas + aviso "Limite da arena" |
| `LAYOUT_AVOID_MINIMAP` | client | `layoutAvoidMinimap` | `true` | o `HudLayout` mede o minimapa (false = finge que não há minimapa) |

Leitura segura (config ainda não carregada → padrão): `MahouConfig.safe(BooleanValue)` / `MahouConfig.safe(DoubleValue)`.

---------------------------------------------------------------------------------------------------
## 2. `client/hud/HudLayout` — layout fixo do HUD (cliente)

```
 ┌─────────────┬──────── topo central ────────┬─────────────┐
 │ CARD        │ barras de chefe, Olho        │ MINIMAP     │ ← Xaero/JourneyMap (lido/estimado)
 │ (sup. esq.) │ Analítico, banners (ui_hud)  │ TRACKER     │ ← rastreador logo abaixo do minimapa
 │ STACK       │                              │             │
 │ (Pilha)     │       ARENA (aviso)          │             │
 │ TOASTS      │                              │             │
 │ (esq.-centro│      DIALOGUE (embaixo)      │             │
 └─────────────┴──────── barra de itens ──────┴─────────────┘
```
Coordenadas na GUI escalada da janela atual. **Regra: nada da nossa UI desenha por cima do minimapa.**

```java
record HudLayout.Rect(int x, int y, int w, int h)   // right() bottom() isEmpty() intersects(x,y,w,h) contains(px,py) inflate(d); Rect.EMPTY
enum HudLayout.Region { CARD, MINIMAP, TRACKER, TOASTS, STACK, DIALOGUE, ARENA }
// constantes: MARGIN 4 · EFFECTS_BOTTOM 52 · CARD_BOTTOM 70 · CARD_WIDTH 128 · RIGHT_COLUMN_TOP 56 · TRACKER_MAX_WIDTH 160 · BOTTOM_RESERVED 64

// Minimapa
HudLayout.minimap() -> Rect            // área do minimapa + linhas de coordenadas + margem (EMPTY sem minimapa)
HudLayout.hasMinimap() · minimapTopRight()
HudLayout.overlapsMinimap(x, y, w, h)
HudLayout.rightLimit(top, bottom) -> int   // borda direita máxima para algo nessas linhas (à esquerda do minimapa)
HudLayout.belowMinimap(x, w, y) -> int      // y empurrado para baixo do minimapa se cruzar com ele
HudLayout.setMinimapProvider(Supplier<Rect>)   // outro minimapa / módulo que sabe melhor (null = padrão)

// Regiões fixas
HudLayout.card()                 // faixa do cartão (o cartão é do ui_hud)
HudLayout.rightColumnTop()       // 1ª linha livre da coluna direita: abaixo do minimapa (ou dos ícones de efeito, y=56)
HudLayout.trackerWidth() · tracker()   // painel do rastreador (x,y já com as reservas; h = espaço até embaixo).
                                 //  Pouco espaço abaixo do minimapa → o rastreador vai para a ESQUERDA dele, y=56
HudLayout.leftEdge()             // borda esquerda do meio-esquerdo (StackHud.leftEdge: à direita da barra de mana)
HudLayout.stack(contentH)        // Pilha: meio-esquerdo, centrada, entre o cartão e a barra de itens
HudLayout.toasts()               // avisos: coluna da esquerda a ~42% da altura; se a Pilha está na tela, ao lado
                                 //  (ou abaixo) dela. Cada dono desenha em toasts().y() + reservedBefore(TOASTS, id)
HudLayout.dialogue(w, h)         // caixa de diálogo embaixo, centrada
HudLayout.arenaNotice(w, h)      // aviso da arena, logo abaixo da mira

// Reservas (empilhar dentro de uma região; ordem de registro)
HudLayout.reserve(Region, String id, IntSupplier height) · release(Region, id)
HudLayout.reserved(Region) · reservedBefore(Region, id)

// O que foi desenhado no último quadro (vale 250 ms)
HudLayout.drawn(Region, Rect) · lastDrawn(Region) -> @Nullable Rect
```

**Minimapa** (`MinimapProbe`): Xaero's Minimap → pergunta ao próprio mod por reflexão (`BuiltInHudModules.MINIMAP`
→ sessão: `isActive`, `getEffectiveX/Y`, `getWidth/Height` na escala da GUI) + linhas de informação da config
(`config/xaero/minimap/profiles/info_display_config/<perfil>.cfg.txt`); se a API do Xaero mudar, estima pela config
dele (`profiles/<perfil>.cfg`: `display_minimap`, `minimap_size`, `minimap_ui_scale`; `config/xaerohud.txt`: posição) com
as fórmulas do Xaero. JourneyMap: quadrado de ~28% da altura no canto superior direito. Cache de 100 ms.

**Quem já segue o layout (núcleo):**
- `client/objectives/ObjectiveHud` → `tracker()` (abaixo do minimapa). `ObjectiveTracker.reserveTop/releaseTop/reservedTop`
  viraram atalhos de `reserve(Region.TRACKER, ...)`; a reserva antiga `"xaero_minimap"` é ignorada (o layout mede).
  `ObjectiveTracker.TOP` (56) = topo SEM minimapa; use `ObjectiveTracker.top()` / `tracker().y()`.
- `client/reputation/ReputationNotices` → região TOASTS (esquerda-centro, entram pela esquerda); `reservedHeight()` = 0.
- Recompensas do núcleo (`Rewards.toast`) → `client/hud/HudToasts` na região TOASTS, abaixo da reputação (o toast do
  vanilla ficava no canto superior direito, por cima do minimapa). Módulos: `HudToasts.push(title, subtitle, icon, rgb)`.
- `client/stack/StackHud` → `stack(h)` + `drawn(STACK)` (os avisos desviam da Pilha).
- `client/stack/StackBanner` (faixa "PILHA!" a 27% da altura) → largura limitada por `rightLimit(top, bottom)` (não
  cruza o minimapa).
- `client/title/MahouTitleScreen` desenha o céu dentro de `GuiGraphics.drawManaged` (milhares de retângulos num envio só).
- `client/onboard/DialogueClient` → `dialogue(w, h)`.

**Para os módulos (fase B):**
- `ui_hud`: o Olho Analítico/banners/topo central devem respeitar `rightLimit(top, bottom)` (não cobrir o minimapa) e
  o rastreador em `tracker()`; o cartão fica em `card()`.
- Painel no canto superior direito (ex.: pacto do Warlock, hoje em y=56 — cai em cima do minimapa):
  `ObjectiveTracker.reserveTop("warlock_pact", h)` e desenhar em
  `HudLayout.rightColumnTop() + HudLayout.reservedBefore(Region.TRACKER, "warlock_pact")`.
- HUD de party (A2) na esquerda: abaixo do cartão, use `reserve(Region.TOASTS, ...)` ou leia `lastDrawn(STACK)`.

---------------------------------------------------------------------------------------------------
## 3. `core/api/ArenaBounds` — limites de arena (servidor)

```java
enum ArenaBounds.Shape { CIRCLE, BOX }
record ArenaBounds.Arena(String id, ResourceKey<Level> dimension, Shape shape, Vec3 center, double radiusX, double radiusZ,
                         double minY, double maxY, int rgb, Component name, int warnDistance, boolean showWall)
  Arena.circle(id, dim, center, radius) · Arena.box(id, dim, AABB)
  .withColor(rgb) .withName(c) .withWarnDistance(n) .withVertical(minY, maxY) .withWall(bool)
  size() · hasVerticalLimit() · inHeight(y, folga) · edgeDistance(pos) (+ dentro / − fora) · contains(pos)
  nearestEdge(pos) · clampInside(pos, margem)

ArenaBounds.register(ServerLevel, id, Vec3 center, double radius, int rgb, @Nullable Component name) -> Arena
ArenaBounds.registerBox(ServerLevel, id, AABB box, int rgb, @Nullable Component name) -> Arena
ArenaBounds.register(MinecraftServer, Arena) -> Arena          // registrar o mesmo id substitui
ArenaBounds.unregister(MinecraftServer|ServerLevel, id)
ArenaBounds.get(id) · all(Level) · at(Level, Vec3) (a menor que contém)
ArenaBounds.DEFAULT_WARN = 10 · MAX_SYNCED = 128
```
- Só VISUAL + consultas. Cliente: parede de luz translúcida na cor da arena (faixas subindo), só num trecho de ~28
  blocos perto do jogador, mais forte conforme ele chega perto (até `warnDistance + 4`); faíscas de pó na cor; e no
  meio da tela, abaixo da mira: **"Limite da arena: N blocos"** (dentro, a até `warnDistance` da borda; esquenta e
  pulsa nos últimos 3) ou **"Fora da arena! Volte N blocos"**. Sons: toque ao entrar nos últimos 3 blocos, grave ao
  sair, carrilhão ao voltar. Config de cliente `showArenaBounds`.
- Não é salvo: registre de novo quando a instância/luta recomeçar; tudo some ao parar o servidor. Sincronizado por
  dimensão (login, troca de dimensão, renascer e a cada mudança).
- Quem quiser PRENDER o jogador usa `contains`/`clampInside` no próprio tick (ex.: Provação: teleporta de volta).
- `trials`: registre a arena da instância (`ArenaBounds.register(level, "trial_" + id, centro, raio, cor, nome)`).
  `BossArena` (A2) pode registrar a arena do chefe aqui também para os jogadores verem a borda.

---------------------------------------------------------------------------------------------------
## 4. Bloqueio de teleporte de mapa (`core/ux/MapTeleportGuard`)
- `CommandEvent` (prioridade alta): se `BLOCK_MAP_TELEPORT` e quem mandou é um `ServerPlayer` fora do criativo/
  espectador e o comando (ou o que vem depois de `execute ... run`) é `tp`, `teleport` ou `jtp` → cancelado, mensagem
  vermelha "O teleporte por mapa e por comando está bloqueado" + dica "Use as Pedras de Teleporte...", som grave.
  Os minimapas (Xaero minimapa/mapa-múndi, JourneyMap) teleportam mandando esses comandos, então todos caem aqui.
  Console, blocos de comando, funções e o código dos módulos (sem comando) não são afetados.
- Cliente (`XaeroSeed`): na construção do mod, se o Xaero's World Map está instalado e
  `config/xaero/world-map/profiles/default.cfg` NÃO existe, cria com `map_teleport_allowed = false` (some o botão
  "Teleportar" do mapa). Configs existentes nunca são tocadas.

---------------------------------------------------------------------------------------------------
## 5. Entrada no jogo (v1.0) — `core/ux/EntryFlow` + `core/onboard`
Sequência: **classe → raça (etapa de criação `"race"`, se o módulo `character` registrar) → "Parabéns!" → título do
capítulo 1 (Diário) → Espírito Guia (Dialogue)**. Sem atributos. A API de etapas continua a da v0.9
(`Onboarding.addCreationStep/completeCreationStep`, `OnboardingEvent.CreationStep`).

```java
EntryFlow.STEP_RACE = "race"
EntryFlow.STEP_ORDER = List.of("race")      // etapas com lugar fixo, antes das outras (as outras: ordem de registro)
EntryFlow.ordered(List<String>) -> List<String>
EntryFlow.addCongratsLine(String id, int order, Function<ServerPlayer, Component> line)   // linha extra no "Parabéns!"
EntryFlow.congratsLines(ServerPlayer) -> List<Component>   // até 3, menor order primeiro
```
- `Onboarding.creationSteps()` / `pendingCreationSteps(p)` já vêm nessa ordem (a raça sempre logo depois da classe).
- O letreiro "Parabéns!" mostra até 3 linhas extras (ex.: `character`:
  `EntryFlow.addCongratsLine("race", 0, sp -> Component.translatable("character.isekai_mahou.congrats_race", nome))`).
  Pacote `OnboardPayloads.Congrats` ganhou `List<Component> extras` (construtor antigo de 7 campos mantido).

---------------------------------------------------------------------------------------------------
## 6. Menu principal (`client/title`)
- `TitleHook` (`ScreenEvent.Opening`, prioridade baixa): quando vai abrir EXATAMENTE o `TitleScreen` do vanilla (fora do
  demo, `customTitleScreen` ligado), abre o `MahouTitleScreen`. Se outro mod já trocou o menu, a tela dele fica.
- `MahouTitleScreen extends TitleScreen` (quem testa `instanceof TitleScreen` continua funcionando): o vanilla monta os
  botões e o NeoForge o "Mods"; os conhecidos (Um Jogador, Multijogador, Realms, Mods, Opções, Sair, idioma,
  acessibilidade) viram `RuneButton`s nos MESMOS destinos (o clique chama o botão original: aviso de multijogador,
  banimento, Realms iguais); botões que outros mods põem no `Init.Post` ficam onde eles puseram. Copyright do vanilla
  mantido. Fundo: panorama + céu violeta, aurora, estrelas, estrela cadente, círculo mágico girando com runas do
  alfabeto galáctico atrás do logo, runas e partículas de mana subindo, névoa (`TitleSky`). Logo desenhado
  (`TitleLogo`: "ISEKAI MAHOU" dourado de dois tons com reflexo, "異世界魔法", frase), frase amarela aleatória
  (`title.isekai_mahou.splash.0..15`), versões embaixo à esquerda, aviso de atualização do NeoForge embaixo à direita.
- "Menu clássico" (canto superior direito) → vanilla até fechar o jogo; no vanilla aparece "✦ Isekai Mahou" para voltar.
- `RuneButton(x, y, w, h, Component, Runnable, boolean primary[, ResourceLocation iconSprite])` — botão estilizado
  reutilizável em telas do mod (placa escura, borda dourada, brilho ao passar o mouse, carrilhão ao clicar).
