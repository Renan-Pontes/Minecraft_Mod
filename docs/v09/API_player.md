# API v0.9 — A2 Jogador: Diálogo, Entrada no jogo, Viagem

Núcleo novo da v0.9 (contrato: `docs/V09_DESIGN.md` §3). Pacotes: `core/api/{Dialogue,Onboarding,Travel}.java` (API),
`core/event/{DialogueEvent,OnboardingEvent}.java` (eventos), `core/onboard/**` (servidor), `client/onboard/**` (cliente).
Init: `OnboardCore.init` / `OnboardCoreClient.init`. Traduções: `src/main/lang/core_onboard/`. Pacotes de rede: registrar `"1"`,
ids `isekai_mahou:core_onboard_*`.

---------------------------------------------------------------------------------------------------
## 1. `core/api/Dialogue` — caixa de diálogo

```java
Dialogue.show(ServerPlayer p, String dialogueId, Component speaker, int rgb, List<Dialogue.Page> pages)
record Dialogue.Page(Component text, @Nullable String await)   // Page.of(text) · Page.await(text, await)
Dialogue.close(ServerPlayer p)                     // fecha o atual e esvazia a fila (SEM evento de fim)
Dialogue.close(ServerPlayer p, String dialogueId)  // fecha um (aberto ou na fila), sem evento
Dialogue.isOpen(ServerPlayer p, String id) · Dialogue.isBusy(ServerPlayer p)
Dialogue.signal(ServerPlayer p, String token)      // libera páginas "signal:<token>" (e "cast" com token "cast")
// NeoForge.EVENT_BUS: DialogueEvent.Finished(getPlayer(), getDialogueId(), isSkipped())
```
- Caixa embaixo da tela (acima da barra de itens), não pausa nem tira o controle. Nome de quem fala numa aba na cor `rgb`;
  texto letra a letra (som suave), contador de página, "Enter ▶". **Enter** (tecla `key.isekai_mahou.dialogue_next`,
  `CoreKeys.DIALOGUE_NEXT`) mostra o texto todo / avança; com o chat aberto, **clicar na caixa** avança; páginas sem ação
  avançam sozinhas depois do tempo de leitura (barrinha na base). **Segurar Backspace** (~0,8 s) pula o diálogo inteiro
  (`Finished` com `skipped = true`).
- Até 5 linhas por página (~340 px); `Dialogue.MAX_PAGES` = 64. Um diálogo por vez; os outros esperam na fila (mesmo id =
  substitui). Com uma tela aberta (menos o chat) o texto e o tempo param e um aviso pequeno fica no canto superior esquerdo.
- `await` (a página mostra a ação com o nome REAL da tecla configurada, piscando, e espera):

| await | o que o jogador faz |
|---|---|
| `Dialogue.AWAIT_TRACKER` `"key:tracker"` | aperta J (o rastreador de objetivos fica VISÍVEL) |
| `Dialogue.AWAIT_MENU` `"key:menu"` | abre o menu B |
| `Dialogue.AWAIT_WHEEL` `"key:wheel"` | troca o elemento da Roda (Mouse 5 + rolar) |
| `"key:slot1"`..`"key:slot3"`, `"key:ultimate"`, `"key:loadout_modifier"`, `"key:dialogue_next"`, `"key:<nome da tecla>"` | aperta a tecla |
| `Dialogue.AWAIT_GRIMOIRE` `"screen:grimoire"` / `"screen:<aba>"` | abre essa aba do menu B (ids de `MenuTabs`) |
| `Dialogue.AWAIT_CAST` `"cast"` | conjura qualquer magia (o servidor confirma pelo `SpellCastEvent.Post`) |
| `"signal:<token>"` | ação do módulo: ele chama `Dialogue.signal(p, "<token>")` |

- `Finished` NÃO é postado quando o servidor fecha (`close`) nem quando o jogador sai do mundo com o diálogo aberto — quem
  usa diálogo para algo importante (tutorial) retoma na próxima entrada (veja `OnboardingEvent.TutorialResume`).

```java
Dialogue.show(sp, "journal.tutorial", Component.translatable("entity.isekai_mahou.guide_spirit"), 0x9FE8FF, List.of(
        Dialogue.Page.of(Component.translatable("journal.isekai_mahou.tutorial.story1")),
        Dialogue.Page.await(Component.translatable("journal.isekai_mahou.tutorial.press_j"), Dialogue.AWAIT_TRACKER),
        Dialogue.Page.await(Component.translatable("journal.isekai_mahou.tutorial.open_grimoire"), Dialogue.AWAIT_GRIMOIRE),
        Dialogue.Page.await(Component.translatable("journal.isekai_mahou.tutorial.cast"), Dialogue.AWAIT_CAST)));
// e no EVENT_BUS:
static void onFinished(DialogueEvent.Finished e) {
    if (e.getDialogueId().equals("journal.tutorial")) Onboarding.finishTutorial(e.getPlayer());   // pulou ou não
}
```

---------------------------------------------------------------------------------------------------
## 2. `core/api/Onboarding` — entrada no jogo

Sequência: **login sem classe → SÓ a tela de classe** → escolhe → letreiro **"PARABÉNS!"** (ícone, nome da classe grande
na cor, categoria, recurso na cor, estilo, fanfarra; 4 s; sem chat) → `OnboardingEvent.ClassCongratsDone` → o Diário mostra
o título do capítulo 1 e o Espírito Guia roda o tutorial por `Dialogue` → `Onboarding.finishTutorial` → `TutorialDone`.

```java
Onboarding.quiet(Player p)          // true: sem classe, nas etapas de criação, ou < 5 min depois do letreiro e tutorial não terminado (servidor)
Onboarding.classChosen(Player p)    // flag onboard.class (escolheu pela tela da v0.9+)
Onboarding.congratsDone(Player p)   // ClassCongratsDone já postado
Onboarding.tutorialDone(Player p)   // flag onboard.tutorial (ou personagem antigo)
Onboarding.finishTutorial(ServerPlayer p)   // grava a flag, posta TutorialDone(p, false), libera os avisos guardados
Onboarding.skipTutorial(ServerPlayer p)     // idem com skipped = true (não fecha diálogos)
Onboarding.QUIET_TICKS = 6000 (5 min) · FLAG_CLASS "onboard.class" · FLAG_CONGRATS "onboard.congrats" · FLAG_TUTORIAL "onboard.tutorial"
```
Eventos (`core/event/OnboardingEvent`, NeoForge.EVENT_BUS, servidor):
- `ClassCongratsDone(getPlayer())` — ~4 s (80 ticks) depois da escolha; 1× por personagem. Se o jogador saiu antes, sai ~3 s
  depois da próxima entrada. Se o tutorial for pulado antes disso, é postado na hora (com `tutorialDone` já true → o
  Diário começa a história SEM o tutorial).
- `TutorialResume(getPlayer())` — ~3 s depois de entrar no mundo com o letreiro já mostrado e o tutorial não terminado.
- `TutorialDone(getPlayer(), isSkipped())` — 1× por personagem.

**Regras para os módulos:**
- Quem manda chat/toast/letreiro ao escolher a classe (`MagicProgressEvent.PathChosen` — ex. `paths` ao despertar o 1º
  elemento) ou nos primeiros minutos: **pule a mensagem se `Onboarding.quiet(player)`**. As flags são gravadas ANTES do
  `PathChosen`, então dentro do evento `quiet` já é true.
- `CoreGameplay.choosePath` não manda mais chat (as chaves antigas `message.isekai_mahou.path_chosen/controls_hint/
  starter_spells` ficaram sem uso).
- Personagens de versões antigas (classe sem `onboard.class`) são migrados no login: tutorial já concluído.
- Rastreador de objetivos: `MahouConfig.SHOW_OBJECTIVES` agora é **false** por padrão; personagem novo (login sem classe)
  começa com ele fechado (1× por personagem); a página `"key:tracker"` do tutorial o liga. Cliente:
  `ObjectiveTracker.setVisible(boolean)` / `isVisible()` (sem mensagem, salva na config).
- Avisos de teclas trocadas (`KeyConflictFix`, Curios `CuriosKeyFix`): a troca acontece no login, mas o AVISO no chat fica
  guardado e só sai depois de `TutorialDone` (ou 5 min depois da escolha). Cliente: `ClientOnboarding.defer(Component)` para
  qualquer aviso de sistema não urgente; `ClientOnboarding.quiet()` / `tutorialDone()`.

Comando op: `/mahou onboarding reset [jogadores]` (sem classe → tela de classe; com classe → etapas de criação + letreiro +
tutorial de novo, sem kit) · `/mahou onboarding skip [jogadores]` (fecha os diálogos e pula o tutorial — e as etapas de
criação que faltarem).

### 2b. Etapas de criação de personagem (§3.2b — ex. módulo `character`: atributos e raça)

```java
Onboarding.addCreationStep(String id)                       // no init do módulo (nos dois lados); ordem de registro = ordem das telas
Onboarding.creationSteps() -> List<String>                  // registradas (só leitura)
Onboarding.pendingCreationSteps(Player p) -> List<String>   // que faltam, na ordem (vazia depois do "Parabéns!"/personagem antigo)
Onboarding.completeCreationStep(ServerPlayer p, String id)  // o jogador confirmou a tela da etapa
// NeoForge.EVENT_BUS: OnboardingEvent.CreationStep(getPlayer(), getStepId()) — "abra a sua tela agora"
```
- Fluxo: escolhe a classe (`PathChosen` sai na hora, `quiet` = true) → `CreationStep(1ª etapa)` → módulo abre a tela →
  `completeCreationStep` → (~0,5 s) `CreationStep(próxima)` … → sem etapas: letreiro **"PARABÉNS!"** → `ClassCongratsDone`
  (~4 s). Sem nenhuma etapa registrada o letreiro aparece direto na escolha.
- Enquanto há etapas pendentes `Onboarding.quiet` é true e o relógio dos 5 min de silêncio só começa no letreiro.
- Saiu no meio: ~3 s depois da próxima entrada, `CreationStep` da etapa pendente é postado de novo.
- Flag por etapa: `onboard.step.<id>`. Personagens antigos (já com classe) NÃO passam pelas etapas; `/mahou onboarding reset`
  apaga as flags `onboard.step.*` e refaz tudo. `completeCreationStep` fora da sequência só grava a flag.

---------------------------------------------------------------------------------------------------
## 3. `core/api/Travel` — viagem rápida

```java
Travel.discovered(Player p, String placeId)               // flag visit.place.<id> ou travel.<id>; hub: visitou a dimensão
Travel.discover(ServerPlayer p, String placeId)           // grava travel.<id> + toast "Novo destino de viagem: <nome>" (se nova)
Travel.destinations(ServerPlayer p) -> List<Places.Place> // conhecidos e que aceitam viagem (todas as dimensões + hubs), perto primeiro
Travel.cost(ServerPlayer p, Places.Place to, boolean fromStone) -> int
Travel.check(ServerPlayer p, Places.Place to, boolean fromStone) -> TravelResult   // sem pagar/teleportar
Travel.travel(ServerPlayer p, String placeId, boolean fromStone) -> TravelResult   // paga, efeitos, teleporta
Travel.menuCooldown(ServerPlayer p) -> ticks · Travel.resetCooldown(p)
Travel.openMenu(ServerPlayer p, @Nullable BlockPos stone)  // manda a lista ao cliente (stone != null = viagem de pedra)
Travel.registerStone(ServerLevel l, String placeId, BlockPos pos) · Travel.unregisterStone(l, pos) · Travel.stone(server, placeId)
Travel.excludeType(String type) · Travel.isHub(Places.Place)
Travel.HUB_TYPE = "dimension_hub" · Travel.WAYSTONE_TYPE = "waystone" · MENU_COOLDOWN_TICKS = 12000 · STONE_REACH = 8
enum Travel.TravelResult { OK, UNKNOWN_PLACE, NOT_DISCOVERED, ALREADY_HERE, OTHER_DIMENSION, NO_RESOURCE, COOLDOWN, IN_COMBAT,
                           BLOCKED, NO_SAFE_SPOT }   // success(), message() (travel.isekai_mahou.result.<nome>), byOrdinal(i)
```
Regras:
- **Custo** no recurso da classe (Mana/Vigor/Foco = o mesmo valor de mana): pedra = 10 + 1 por 50 blocos (máx. 120; outra
  dimensão conta como 2000 blocos); **menu = ×3 e recarga de 10 min** (contador `travel.menu_until`, em segundos do jogo).
  Nunca mais que o máximo do recurso (com a barra cheia sempre dá). Criativo: grátis, sem recarga, sem trava de combate.
- **Bloqueios:** sem classe, morto, dormindo, dentro da `trial_realm` (BLOCKED); ferido por mob nos últimos 5 s (IN_COMBAT);
  dentro do lugar ou a < 12 blocos dele (ALREADY_HERE); outra dimensão e destino não é hub (OTHER_DIMENSION).
- **Destinos:** todo `Places.Place` conhecido, menos os tipos `hunting_ground`, `trial`, `arena` (+ `excludeType`) e lugares
  da `trial_realm`. Uma pedra (`waystone`) dentro de um lugar conhecido não aparece à parte: vira a chegada do lugar.
- **Hubs:** destino depois da 1ª visita à dimensão (contador `visit.isekai_mahou.<dim>`, flag `dimensions.seen.<dim>` ou
  `travel.hub_<dim>`). **O módulo dimensions registra cada hub no `Places`** com id `hub_<dim>` (`hub_elemental_plane`,
  `hub_abyss`, `hub_celestial_sanctuary`, `hub_twilight_rift`), tipo `Travel.HUB_TYPE`, `center` = ponto de chegada (pés).
  Sem registro: chegada perto de (0, chão do hub, 0) — Plano Elemental y=111, Abismo y=65, Santuário y=151, Fenda y=65 (pés)
  — e, se não houver chão, uma plataforma 3×3 de pedra lisa (nunca dentro da rocha: aí `NO_SAFE_SPOT`). Em dimensões com
  teto (Abismo) o topo da coluna nunca é usado como chão.
- Lugares de qualquer dimensão são achados pelo id (cada nível guarda os seus); dentro da mesma dimensão dá para viajar entre
  eles normalmente. Se outro mod cancelar a troca de dimensão, o recurso é devolvido (`BLOCKED`).
- **Chegada:** pedra registrada (`registerStone`) → pedra `waystone` dentro da área → `Place.center`; sempre ajustada a um
  chão firme e seguro num raio de 6 (sem líquido, fogo, magma, cacto; 2 blocos livres); +3 s de Queda Lenta por segurança.
- **Efeitos:** partida (anel de pó na cor do lugar, portal reverso, som de teleporte/ametista, clarão na tela), chegada
  (anel, portal, FLASH, som de teleporte + farol, clarão se dissolvendo), barra de ação "Você chegou: <nome>"; falha:
  mensagem vermelha na barra de ação ("Vigor insuficiente (30/45)", "recarregando: 7:12"...) + som grave.
- Primeira visita a um lugar viajável (ou 1ª chegada numa dimensão com hub): toast "Novo destino de viagem" (não durante a
  entrada no jogo). Contador `travel.count` +1 a cada viagem.

### Pacotes e cliente (`client/onboard/ClientTravel`)
- Servidor → cliente `TravelMenu(fromStone, stone, cooldown, cooldownTotal, resourceName, resourceRgb, dests)` com
  `TravelDest(id, name, type, faction, dim, pos /*pedra ou centro*/, color, cost, status /*TravelResult*/, hub)`;
  `TravelOutcome(placeId, result)`; `TravelFx(rgb, arrival)` (clarão). Cliente → servidor: `TravelOpen` (lista do menu),
  `TravelGo(placeId)`.
- O servidor guarda a **sessão** da última lista: `TravelGo` vale como viagem de pedra só se a lista foi aberta por
  `openMenu(p, pedra)`, há < 5 min, o jogador está a ≤ 8 blocos da pedra e a pedra (`isekai_mahou:travel_stone`) ainda
  existe; senão é viagem de menu (×3, recarga).

```java
// módulo travel — cliente (init):
ClientTravel.onMenu(menu -> { if (menu.fromStone()) mc.setScreen(new TravelScreen(menu)); });  // e a aba Viagem lê menu()
ClientTravel.onOutcome((id, r) -> { if (r.success()) mc.setScreen(null); });
ClientTravel.requestMenu();            // a aba "Viagem" do menu B pede a lista (sem pedra)
ClientTravel.travel(dest.id());        // botão Viajar
ClientTravel.menu() / version() / cooldownLeft() / byDimension()
// Destination(id, name, type, faction, dim, pos, color, cost, status, hub) · available() · status().message()
// módulo travel — servidor (bloco travel_stone):
//   tocar/usar: Travel.discover(sp, placeId); Travel.openMenu(sp, pos);
//   colocado por estrutura: Places.register(... tipo Travel.WAYSTONE_TYPE ...) e/ou Travel.registerStone(level, idDoLugar, pos);
//   quebrado: Travel.unregisterStone(level, pos)
```

Comandos op: `/mahou travel list | menu | go <lugar> | discover <lugar|all> | cooldown`.

---------------------------------------------------------------------------------------------------
## 4. Arquivos/ids
- Tecla `key.isekai_mahou.dialogue_next` (Enter).
- Flags: `onboard.class`, `onboard.congrats`, `onboard.tutorial`, `onboard.step.<id>` (+ internas `onboard.quiet_over`,
  `onboard.tracker`);
  `travel.<placeId>`, `travel.hub_<dim>`. Contadores: `onboard.class_at`, `travel.menu_until`, `travel.count`.
- SavedData `isekai_mahou_travel` (Overworld): pedras registradas por lugar.
- Camadas de HUD: `isekai_mahou:core_onboard_dialogue` (abaixo do chat), `core_onboard_congrats`, `core_onboard_travel_flash`.
