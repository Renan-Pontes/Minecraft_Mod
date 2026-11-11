# API v1.0 — Social e arenas (trilha A2)

Pacote base `com.renanpontes.isekaimahou` (abreviado `J.`). Tudo é ADITIVO: nenhuma assinatura pública antiga mudou.

| Onde | O quê | Lado |
|---|---|---|
| `J.core.api.Party` | grupos (party), sem dano entre membros, XP dividido, chat; troca segura, duelo, perfil | comum (leitura) / servidor (escrita) |
| `J.core.api.BossArena` | arenas de chefe protegidas + áreas sempre protegidas (templos/hubs de chefe) | servidor |
| `J.core.api.Allegiance` | guerra de cidades: lealdade, líder, cidades inimigas, PvP só entre lados inimigos | comum (leitura) / servidor |
| `J.core.api.Reputation#setAllied/isAllied/allied` | facções aliadas (máx. 3) | comum (leitura) / servidor |
| `J.core.social.SocialEvent.*` | eventos (NeoForge.EVENT_BUS) | servidor |
| `J.core.boss.BossKit#sealArena` | sela a arena já na intro do chefe | servidor |
| `J.core.social.*` | implementação (não use direto, exceto `SocialEvent` e `ClientSocialView` no cliente) | — |
| `J.client.social.*` | roda de interação, tela de troca, perfil, HUD de grupo, avisos, aba "Grupo" (menu B, ordem 57) | cliente |
| `J.mixin.ServerPlayerSocialMixin` | `ServerPlayer.canHarmPlayer` consulta as regras sociais | servidor |

Pacotes (registrador "1"): `social_action`, `social_reply`, `social_party_op` (C→S), `social_party`, `social_state`,
`social_profile` (S→C), `rep_set_allied` (C→S). Menu: `isekai_mahou:social_trade`. Lang: `src/main/lang/core_social/`.

---------------------------------------------------------------------------------------------------
## 1. Party (`core/api/Party`)

```java
// leitura (cliente: só o jogador local)
static boolean inParty(Player p)
static boolean sameParty(Player a, Player b)
static List<UUID> members(Player p)                 // inclui o próprio e os offline, ordem de entrada
static List<ServerPlayer> onlineMembers(ServerPlayer p)   // sem grupo: só o próprio
@Nullable static UUID leader(Player p)  ·  static boolean isLeader(Player p)
@Nullable static Boolean allyOverride(Entity source, Entity target)   // regra social de aliado (ver §5)
// escrita (servidor) — devolvem Party.Result (ok(), message(nome) = texto pronto "social.isekai_mahou.result.<id>")
static Result invite(ServerPlayer from, ServerPlayer to)   // líder (ou sem grupo: o grupo nasce no aceite)
static Result accept(ServerPlayer p, @Nullable UUID from)  // null = convite mais recente
static Result decline(ServerPlayer p, @Nullable UUID from)
static Result leave(ServerPlayer p)                        // líder sai → próximo (online primeiro) assume; 1 membro → desfaz
static Result kick(ServerPlayer leader, UUID target) · promote(leader, target) · disband(leader)
static Result chat(ServerPlayer from, String msg)          // = /p <msg>
// interações
static Result trade(ServerPlayer from, ServerPlayer to)    // pede troca (ou aceita se o outro já pediu)
static boolean isTrading(Player p)
static Result duel(ServerPlayer from, ServerPlayer to)     // desafia (ou aceita)
static boolean dueling(Player a, Player b)                 // duelando entre si, já na fase de luta
static void showProfile(ServerPlayer viewer, ServerPlayer target)
static void addProfileLine(Function<ServerPlayer, Component> line)   // linha extra no perfil (ex.: "Raça: Elfo"); null = nada
static final int MAX_MEMBERS = 8;  static final double INTERACT_RANGE = 10;
enum Result { OK, SELF, OFFLINE, TOO_FAR, TARGET_IN_PARTY, ALREADY_MEMBER, ALREADY_IN_PARTY, NOT_LEADER, PARTY_FULL,
              NO_REQUEST, NOT_IN_PARTY, NOT_MEMBER, BUSY, TARGET_BUSY, DEAD, NOT_ALLOWED }
```

Regras:
- **Grupos salvos no mundo** (SavedData `isekai_mahou_social` no Overworld): sobrevivem ao reinício e ao logout.
- **Sem dano entre membros**: golpes, flechas (passam através), magias (`SpellContext.isAlly` = aliado, então curas
  afetam), mascotes/invocações e TNT/projéteis com dono. Vale com PvP ligado ou desligado.
- **XP de abate**: o núcleo já dá XP cheio a todo jogador a ≤ 24 blocos; membros do grupo do autor do golpe final a
  24–64 blocos (mesma dimensão) também recebem (`MagicApi.giveXp(p, xp, "party")`).
- **HUD de grupo** (cliente, `client/social/PartyHud`): coluna da esquerda, logo abaixo do cartão (`HudLayout.CARD_BOTTOM`),
  desvia da Pilha. Rosto, nome, nível, vida e recurso da classe; outro mundo/offline apagado; pisca com ≤ 25% de vida.
  `PartyHud.enabled = false` desliga (o `ui_hud` pode desenhar o próprio lendo `ClientSocialView.partyMembers()`).
- **Chat de grupo**: `/p <msg>` ou `/party chat` (liga/desliga: o chat normal vai só para o grupo; aviso na tela).
- **Convite vencido por mudança**: se quem convidou entrou em outro grupo (ou deixou de ser líder) antes do aceite, o
  convite não vale mais (`NO_REQUEST`) — ninguém entra num grupo cujo líder não convidou.
- **Pedidos** (convite, troca, duelo) duram 60 s: chat com botões [Aceitar]/[Recusar], aviso na coluna da esquerda
  (`HudToasts`) e a roda mostra "Aceitar …". Expirou → quem pediu é avisado.

### Roda de interação (cliente)
**Agachar + girar o scroll olhando para outro jogador** (até 8 blocos, sem parede no meio) abre a roda: Grupo (cima),
Trocar (direita), Duelar (baixo), Ver perfil (esquerda). Escolha por direção do mouse, scroll ou 1–4; clique/Enter
confirma; Esc/botão direito fecha. Ouvinte de scroll em prioridade NORMAL (diálogos/cutscenes, que cancelam antes,
continuam valendo). Sem jogador na mira, o scroll segue normal.

### Troca segura
Tela de 2 lados (12 espaços cada): à esquerda a SUA oferta, à direita a do outro (só ver). Os dois confirmam → troca
atômica no servidor (confere espaço no inventário dos dois antes; sem espaço = desconfirma e avisa). Qualquer mudança nas
ofertas desconfirma os dois e trava a confirmação por 2 s. Fechar a tela, se afastar (> 12 blocos), trocar de dimensão,
morrer ou deslogar cancela e devolve cada oferta ao dono (no logout, ANTES do save). Evento `SocialEvent.TradeCompleted`.

### Duelo
Contagem de 3 s (títulos) → até 3 min num círculo de 32 blocos (anel de partículas). Vale entre os dois mesmo com PvP
desligado ou sendo do mesmo grupo. **Ninguém morre**: o golpe que mataria deixa o perdedor com 25% da vida. Sair do
círculo/deslogar = derrota; tempo esgotado = empate. Contadores `duel.win` / `duel.loss`; `SocialEvent.DuelEnded`.

### Perfil
Modelo 3D (se carregado), classe, nível/Círculo, título, cidade, grupo, duelos, abates (`kill.total`), chefes vencidos
(contadores `boss.*` > 0), facção mais conhecida + linhas de `Party.addProfileLine`. Botões Convidar/Trocar/Duelar.

---------------------------------------------------------------------------------------------------
## 2. Facções aliadas (`core/api/Reputation`)

```java
static final int MAX_ALLIED = 3;
static boolean isAllied(Player p, String faction)     // flag "rep.allied:<id>" (sincronizada; cliente = jogador local)
static List<String> allied(Player p)
static AllyResult setAllied(ServerPlayer p, String faction, boolean allied)
enum AllyResult { OK, UNKNOWN_FACTION, LOW_STANDING, LIMIT, UNCHANGED }   // ok(), message()
```
- Só com patamar **≥ Neutro**; cair abaixo de Neutro desfaz a aliança sozinho (mensagem vermelha). `/mahou rep reset` limpa.
- Membros de facção aliada: **não recebem dano do jogador** (golpes, flechas passam, magias — `ctx.isAlly` = aliado —,
  mascotes/invocações), **não miram o jogador** (exceto se ele os atacou há < 30 s), e **ajudam nas brigas**: quando o
  jogador é ferido ou fere um monstro (`Enemy`), até 4 membros que sabem lutar (atributo `attack_damage`), sem alvo, a
  ≤ 16 blocos partem para cima (1×/s). A proteção cai para um membro que está mirando o jogador. Chefes nunca contam (chefe = tag `c:bosses`, chefe de
  provação ou de arena — NÃO o limiar de vida do `BossKit.isBossLike`, que a vida ×2 dos mobs da v1.0 faria guardas passarem).
- Aba **Reputação**: botão "Tornar aliada / ✦ Aliada" nos detalhes da facção + contagem "Aliadas: n/3"; losango verde no
  cartão das aliadas. A aba **Grupo** lista as aliadas.

---------------------------------------------------------------------------------------------------
## 3. Arenas de chefe (`core/api/BossArena`)

```java
@Nullable static Info seal(LivingEntity boss)                       // sela agora (centro/raio automáticos)
@Nullable static Info seal(LivingEntity boss, Vec3 center, double radius)
static boolean release(Entity boss)                                 // libera já (sem os 30 s)
static boolean isSealed(Entity boss)
@Nullable static Info at(Level level, Vec3 pos)
static boolean isProtected(ServerLevel level, BlockPos pos)          // arena selada OU área sempre protegida
static List<Info> all()  ·  static boolean isArenaBoss(Entity e)
static void registerType(ResourceLocation|EntityType)   // chefe fora da tag c:bosses
static void excludeType(ResourceLocation)               // nunca sela (padrão: minecraft:ender_dragon)
static void protectStructure(ResourceLocation structureId)   // estrutura worldgen sempre protegida
static void protectPlace(String placeId)                     // lugar (Places) sempre protegido
record Info(UUID boss, ResourceLocation dimension, Vec3 center, double radius, boolean releasing)
static final int RELEASE_TICKS = 600; MIN_RADIUS = 24; MAX_RADIUS = 48; String ARENA_TAG = "isekai_mahou.arena_boss";
```
- **Quem sela**: tag `c:bosses`, chefe de provação, entidade com `BossKit.setArena`, `registerType`, ou `addTag(ARENA_TAG)`.
  Sela sozinho quando o chefe mira um jogador (fora do criativo) ou é ferido por um. `BossKit.sealArena(boss)` sela na intro.
- **Centro**: `BossKit.arenaCenter` → centro da peça da estrutura protegida em que o chefe está → centro de restrição do
  mob → posição; centro automático desce até o chão. **Raio**: `BossKit.arenaRadius` ou `24 + 5 × (tamanho − 1)`
  (≥ meia-largura da estrutura + 4), limitado a 24–48. Altura: do centro − max(12, r/2) até centro + r + 16.
- **Enquanto selada** (e nos 30 s de liberação): jogadores fora do criativo não quebram nem colocam blocos; baldes,
  isqueiro e bola de fogo são bloqueados; explosões não destroem blocos; `Destruction` não mexe (proteção registrada em
  `Destruction.addProtection`); monstros não fazem mobGriefing (o chefe do mod pode — efeitos temporários dele que
  consultam o mobGriefing; blocos que o chefe põe com `setBlock` passam). O chefe é empurrado de volta na borda e
  **teleportado** perto do centro se passar de r + 8, subir demais, cair abaixo do chão da arena ou ir para o vazio.
- **Fim**: chefe morto ("X caiu!") ou 30 s sem combate (sem alvo jogador e sem dano de jogador) → liberação em 30 s.
  Se a luta recomeçar antes, sela de novo. Chefe descarregado = fim da luta.
- **Visual**: anel de fogo das almas ao selar, anel de partículas a cada 2 s, parede de runas + "Limite da arena" do A3
  (`ArenaBounds`, id `boss_arena_<uuid>`; chefes de provação NÃO ganham esta parede — o `trials` mostra a da própria arena), mensagem no chat, sons de farol; HUD abaixo da mira ("Arena selada",
  "liberada em N s"). Quebrar/colocar mostra "A magia da arena protege este lugar".
- **Sempre protegidas** (com ou sem luta: sem quebrar fora do criativo, sem explosão, sem Destruction, sem mobGriefing):
  estruturas `isekai_mahou:guardian_temple_{fire,water,earth,wind}`, `abyss_throne`, `celestial_cathedral`,
  `twilight_arena` (peças da estrutura + 2 blocos) e o lugar `hub_twilight_rift` (arena do Arauto; os outros hubs já são
  zonas seguras). Colocar blocos nessas áreas só é bloqueado durante a luta (tochas na exploração continuam valendo).
  Módulos novos (hueco_mundo, bosses_e...) chamam `protectStructure`/`protectPlace` no init.
  Custo: o teste é por bloco (explosões, Destruction) e por consulta de mobGriefing — caixas das estruturas em cache por
  chunk (60 s) e lugares protegidos em cache por dimensão (5 s); só na thread do servidor.

---------------------------------------------------------------------------------------------------
## 4. Guerra de cidades (`core/api/Allegiance`)

```java
@Nullable static String of(Player p)                   // cidade (id de Places) — cliente: jogador local
static void set(ServerPlayer p, @Nullable String cityId)
static List<UUID> citizens(MinecraftServer s, String cityId)
static void setLeader(MinecraftServer s, String cityId, @Nullable UUID leader)  ·  @Nullable UUID leader(s, cityId)
static boolean isLeader(ServerPlayer p, String cityId)
static boolean declareEnemy(MinecraftServer s, String cityId, String targetId, @Nullable ServerPlayer by)
static boolean makePeace(MinecraftServer s, String a, String b)
static boolean areEnemies(s, a, b)  ·  Set<String> enemiesOf(s, cityId)
static Set<String> sides(ServerPlayer p)             // a cidade dele + as dos líderes que estão no grupo dele
static boolean atWar(Player a, Player b)              // cliente: jogador local × outro (lista sincronizada)
static boolean isEnemyOf(ServerPlayer p, String cityId)
static boolean isEnemyAt(ServerPlayer p, BlockPos pos)    // o lugar em pos é cidade inimiga?
static Component cityName(MinecraftServer s, String cityId)
```
- **Lado** de um jogador = a cidade dele + as cidades cujo **líder está no grupo dele** ("os jogadores da party do líder
  viram inimigos"). Guerra é mútua.
- **PvP**: jogadores de lados inimigos se ferem mesmo com `pvp=false` (mixin em `canHarmPlayer`), magias deixam de tratá-los
  como aliados. Ninguém do mesmo grupo se fere, mesmo em guerra. Nomes de inimigos ganham "⚔" vermelho; do grupo, "✦" verde.
- Ao declarar: chamado de guerra (chifre de invasão) e mensagem para os envolvidos online.
- **NPCs** (módulo `settlements`/`cities`): use `isEnemyOf(player, placeId)` / `isEnemyAt` para deixá-los hostis/fugir e
  recusar comércio. Eventos `SocialEvent.AllegianceChanged`, `WarDeclared`, `PeaceMade`.

---------------------------------------------------------------------------------------------------
## 5. Regra social de aliado/PvP (resumo)

| par | resultado |
|---|---|
| duelo em luta | inimigos (PvP liberado) |
| contagem do duelo | ninguém fere |
| mesmo grupo | aliados (sem dano; curas valem) |
| lados em guerra | inimigos (PvP liberado mesmo com PvP off) |
| jogador × membro de facção aliada (que não o mira) | aliados (sem dano) |
| resto | regras normais (PvP do servidor, times) |

`SpellContext.isAlly` consulta `Party.allyOverride` logo depois de "eu mesmo / meu mascote".

---------------------------------------------------------------------------------------------------
## 6. Eventos (`core.social.SocialEvent`, NeoForge.EVENT_BUS, servidor)

`Party(partyId, player, PartyChange {CREATED, JOINED, LEFT, KICKED, PROMOTED, DISBANDED})`,
`AllegianceChanged(player, oldCity, newCity)`, `WarDeclared(city, target, by)`, `PeaceMade(a, b)`,
`DuelEnded(winner, loser, draw)`, `TradeCompleted(a, b)`, `ArenaSealed(boss, center, radius)`, `ArenaReleased(bossUuid, center)`.

---------------------------------------------------------------------------------------------------
## 7. Comandos

- Todos: `/party` (lista) · `/party invite <jogador>` · `accept [nome]` · `decline [nome]` · `leave` · `kick <membro>` ·
  `promote <membro>` · `disband` · `list` · `chat [mensagem]` · `/p <mensagem>` · `/trade <jogador>` · `/trade accept|decline [nome]` ·
  `/duel <jogador>` · `/duel accept|decline [nome]`.
- Op: `/mahou social allegiance <jogadores> <cidade|none>` · `leader <cidade> <jogador|none>` ·
  `war declare <cidade> <alvo>` · `war peace <a> <b>` · `war list` · `arena list` · `arena release` · `parties`.

---------------------------------------------------------------------------------------------------
## 8. Cliente

- `ClientSocialView` (core/social, só dados): `inParty()`, `isMember(uuid)`, `partyMembers()` (`Member`: id, name, online,
  hp, maxHp, res, maxRes, resArgb, path, level, dim), `leader()`, `state()` (`State`: cidade, guerras, inimigos online,
  duelo, pedidos, arena, chat de grupo), `isEnemy(uuid)`, `duelOpponent()`, `requestFrom(uuid, kind)`, `version()`.
- Aba **Grupo** (`MenuTabs` id `party`, ordem 57): membros (vida/recurso, líder; o líder expulsa/promove), Sair/Desfazer,
  chat de grupo, perfil; pedidos com Aceitar/Recusar; cidade e guerras; facções aliadas; recorde de duelos.
- HUD: `PartyHud` (coluna esquerda) e `SocialStatus` (abaixo da mira, região `ARENA` do `HudLayout` com reserva
  `social_status`: duelo, arena selada/liberando, chat de grupo ligado). Pedidos novos viram `HudToasts`.
