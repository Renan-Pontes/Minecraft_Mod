# Isekai Mahou v1.0 — "Lançamento": design e contrato

Este documento VENCE todos os anteriores (`ARCHITECTURE.md`, `V07/V08/V09_DESIGN.md`, que continuam valendo no que não conflitar).
APIs existentes: `docs/v07/*.md`, `docs/v08/*.md`, `docs/v09/*.md` (classes v0.9, Dialogue/Onboarding/Travel, worldgen dos reinos).
APIs NOVAS (fase A da v1.0): `docs/v10/API_spells.md` (A1), `docs/v10/API_social.md` (A2), `docs/v10/API_ux.md` (A3).
A v0.9 NUNCA foi lançada: nada dela precisa de compatibilidade de save (os 5 Mágicos e tudo da v0.8 sim). Versão: **1.0.0**.

## 0. Pedido do jogador (feedback dos amigos testando a v0.8/v0.9)
- **Todos GOSTARAM MUITO da criação de magias** → o sistema de glifos vale para TODAS as classes e skills (tudo é montável no Grimório).
- Mostrar melhor as informações das magias (**dano de cada parte, custo de mana**).
- Custo de mana tem que **crescer conforme se adiciona coisa**: os modificadores aplicados a cada cópia (ex.: Atômico em cada meteoro da
  chuva) **custam por cópia**. Em troca, **mana máxima bem maior**, com **limite de mana** que sobe ao **chegar em níveis**, **usar 100%
  do mana muitas vezes** e **recarregar ao longo dos dias**.
- **Presets** de magias (salvar rascunhos para testar).
- **Círculo de Ascensão** um pouco maior (+3 blocos de raio) com **barreira**: quem ascende não sai (mobs e magias sim); **não se constrói
  na área**. A Ascensão agora é em locais (altares prontos).
- **Teleporte pelo minimapa/mapa NÃO pode funcionar** — fast travel só em locais específicos.
- Nas **Provações**, ver **até onde dá para ir** (limite da arena e distância).
- **Cidade**: jogadores saqueiam tudo → se um **guarda vê**, ele **reclama**, o jogador **perde reputação**, pode **virar briga**, e dá para
  **persuadir o guarda**.
- **Escolher quais guildas/facções são aliadas** → não dá para ferir os membros por engano (aventureiro morto sem querer).
- **Party livre**: **agachar + scroll do mouse** em outro jogador → convidar para o grupo e **trocar itens**.
- **Entrada nova é necessária**; se der, **menu principal do Minecraft** mais legal.
- **Sem atributos/status** (complexo demais); **raças continuam**, com **skills únicas**.
- **Reinos e cidades** ficam. Os jogadores vão se dividir em **2 facções**: cada um numa cidade, **subir até tomar o controle da cidade**,
  **cobrar impostos**; **criar a própria cidade** (estilo MineColonies simplificado: sem recursos, autoriza no centro e os NPCs constroem
  com o tempo) em **3 estilos**: **Reino** (muralha de pedra), **Desértico** (maravilhas como pirâmides, bônus, muros simples), **Nórdico**
  (muro de madeira, rústico, fácil de upar). **Declarar cidade inimiga** → os jogadores da guilda/party do líder viram inimigos; aldeões
  lutam ou fogem e não negociam; preparar guerreiros (precisa de afinidade máxima).
- **Chefe final**: perfeito, mas **feito para 6 jogadores de nível máximo**, com contramágica; **sem morte instantânea**, mas **trava,
  drena, transforma em estátua** e outras coisas piores que a morte.
- Chefes novos OK. **Novas dimensões e chefes, como Hueco Mundo** e outros.
- **Rotas-guia mudam para cada classe/progressão** ("garantir um guia pra todos").
- **Ascensão fácil de achar e de recursos, difícil de habilidade** (como está).
- Área de caça e HUD parado OK; **o minimapa às vezes fica embaixo das coisas** → fixar mais o layout.
- Atômico: está bom (não mexer).
- **Nível máximo 150; escondido: 150→200 só depois de matar o chefe final.** Antes do 100 tem que ser **difícil upar**.
- **Lutas de chefe não destroem o cenário** (o Serafim caiu do mapa; a arena do Arauto/Dragão é pequena e não dura).
- Pendentes da conversa anterior: **magias das outras classes**; **classes com bônus bons** — o Mago NÃO tem "imbuir espada" a menos que
  **um guerreiro ensine**; **aprender pelo pergaminho exige uma essência do elemento**; **tirar os loadouts por elemento: só 9 loadouts**;
  **FARM melhor** (construir casas/objetos mais rápido, farms de materiais vanilla, farm de essências ao **roubar os pedestais com
  Toque Suave**); **arenas inquebráveis durante a luta**.

- (depois) **Poções de mana e de vida** (vários tiers, até as melhores), **armaduras com mais defesa**, **mais mana e vida** — **inflar
  os números mantendo a proporção** ("não que nem DOOM com 1 bilhão de dano e 3 bilhões de vida; algo básico: em vez de um golpe tirar 1
  barra de vida, tirar 1,5 e o cara ter 2").

### 0.1 Inflação de números (A1 define as constantes; todos os módulos seguem)
- **Vida do jogador**: base 20 → **40** (o vanilla desenha em fileiras; o `ui_hud` mostra a vida como BARRA numérica além dos corações —
  ver ui_hud) e o crescimento por nível dobra (+4 a cada 5 níveis); armaduras do mod (outfits/armory/classes): **defesa ×1,5** e
  resistência +; poções do mod e regen proporcionais.
- **Dano**: todo dano de magia/técnica do mod **×1,5** (constante `Progression.DAMAGE_SCALE = 1.5F` aplicada em `SpellContext.damage`);
  **mobs e chefes do mod**: vida ×2 e dano ×1,5 (constantes `MOB_HEALTH_SCALE`, `MOB_DAMAGE_SCALE` aplicadas por atributo no spawn das
  entidades do namespace isekai_mahou — sem mexer em cada classe); mobs vanilla: vida ×1,5 e dano ×1,25 (configurável
  `VANILLA_SCALING`, padrão ligado) para manter a proporção com o jogador mais forte.
- **Mana/Vigor/Foco**: além do item 4 do A1 (≈3×), números exibidos maiores (custos ×3 proporcionais) — mantenha a razão custo/mana.
- **Poções** (módulo NOVO `potions`): Poção de Vida e Poção de Mana (o mesmo frasco restaura o recurso da classe) em tiers **Menor,
  Comum, Maior, Superior, Suprema, Elixir Lendário** (cura instantânea + regen; tiers altos com receitas de essências/itens de chefe),
  arremessáveis e de efeito prolongado, com cooldown compartilhado (para não spammar), ícones e líquidos coloridos; vendidas pelo
  alquimista das cidades por id; caem de chefes.

## 1. Classes (15 = 5 Mágicas + 5 Físicas + 5 Destreza) — TODAS com dificuldade parecida para evoluir
Categorias: `MAGIC` (recurso Mana), `PHYSICAL` (Vigor), `DEXTERITY` (Foco). Os 5 Mágicos não mudam (ids/ordinais 1–5). As 10 classes da
v0.9 (warrior...hunter) SAEM e dão lugar a estas (ordinais 6–15, ids novos):

| enum | id | nome PT | cat. | afinidade (primária primeiro) | difícil | sistema próprio (módulo) |
|---|---|---|---|---|---|---|
| SMITH | smith | Ferreiro | PHYSICAL | Fogo, Terra | Água | **Forja Arcana**: cria e CUSTOMIZA armas com magia de forja (lâmina curta→longa: adaga vira espada; larga, curva, fosca/brilhante, ponta, serrilha, peso, empunhadura, runa) — a arma é montada como glifos; herda o caminho das lâminas (Campo de Lâminas/Arma Conjurada/Portão do Arsenal = glifos de origem do Ferreiro). `class_smith` |
| ROGUE | rogue | Ladino | PHYSICAL | Sombra, Vento | Luz | **Roubo**: roubar itens (bolsos de mobs/NPCs/jogadores com PvP), mana, buffs e MAGIAS (copiar a receita de quem conjurou); **crítico e ataque pelas costas** (×2,5) com teleportes de sombra. `class_rogue` |
| KNIGHT | knight | Cavaleiro Rúnico | PHYSICAL | Luz, Terra | Sombra | **Lâminas Rúnicas**: grava runas/magias na espada (golpes viram magia), **espadas mágicas lendárias** (homenagem Excalibur "Espada do Lago", Caliburn, espada flamejante, espada da tempestade...) despertadas por feitos; raios de espada. `class_knight` |
| ENCHANTER | enchanter | Encantador | PHYSICAL | Arcano*, Luz, Vento | Sombra | **Encantamentos**: buffs e melhorias em si e em ALIADOS (força, velocidade, armadura, regen, imbuir elemento na arma do aliado, auras de grupo, sobrecarga temporária) que acumulam com limite. (*Arcano é neutro para todos; afinidade real Luz/Vento) `class_enchanter` |
| SHINIGAMI | shinigami | Shinigami | PHYSICAL | Sombra, Vento | Luz | **Caminho da Zanpakutō** (homenagem Bleach): espada espiritual ligada à alma; **Mundo Interior** (meditar leva a uma dimensão pessoal onde se conversa/luta com o espírito da espada); liberações **Selada → Shikai → Bankai**; Shikai escolhido por arquétipo (ex.: **Peso** — cada corte deixa o alvo mais pesado, homenagem Wabisuke; **Pétalas de Lâmina**; **Gelo**; **Chama Ancestral**; **Pincel Branco** — tinta que "apaga o nome": o alvo perde magias/habilidades por um tempo, homenagem Shirafude Ichimonji); Kidō (Hadō/Bakudō) como glifos. Jogabilidade única de meditação e reinos. `class_shinigami` |
| QUINCY | quincy | Quincy | DEXTERITY | Luz, Vento | Sombra | **Arco espiritual** (forma de glifo), **absorver** energia/projéteis/magias inimigas e **devolver como tiros**, espadas de energia (mais fracas), **Letras** (um poder modesto escolhido), **Auswählen** (homenagem): absorver o poder de Quincys próximos (NPCs Quincy de estruturas e jogadores Quincy que aceitarem) para evoluir. `class_quincy` |
| GUNSLINGER | gunslinger | Pistoleiro | DEXTERITY | Fogo, Raio→(Fogo+Vento) | Água | **Armas mágicas criadas com mana** (pistola, revólver, escopeta, fuzil, pistolas duplas) — a magia sai pelas balas (forma "Bala" e variações); recarga, tiro rápido, tiro carregado; pistoleiro clássico. `class_gunslinger` |
| RANGER | ranger | Ranger | DEXTERITY | Vento, Terra | Fogo | **Companheiros** (águia, lobo, cavalo-montaria) que evoluem; arqueiro clássico — **monta o efeito das flechas** (explosiva, teleguiada, de corrente, perfurante...) como glifos. `class_ranger` |
| GAMBLER | gambler | Apostador | DEXTERITY | Arcano*, Luz, Sombra | — | **Sorte** (homenagem Hakari/Gambit): cada magia **rola a sorte** — de −99% a +100% de poder (com faixas no meio); jackpot = estado de "sorte infinita" por alguns segundos (regen e poder máximos); cartas carregadas de energia arremessadas, roleta, caça-níquel, dados; domínio "Cassino". `class_gambler` |
| STEELBALL | steelball | Steel Ball | DEXTERITY | Terra, Vento | Fogo | **Rotação** (homenagem Steel Ball Run): bolas de aço arremessadas com giro; **lições do mestre** (NPC mentor que ensina rotações em lições) e **Partes do Cadáver Santo** escondidas em estruturas pelo mundo — cada parte evolui o poder ("Atos" 1→4 da unha giratória, homenagem Tusk; o Ato 4 é a rotação infinita); bônus montado a cavalo. `class_steelball` |

- **Encantador — encantamentos obrigatórios** (o amigo do jogador vai jogar de "Yuuki Kagurazaka" de Tensura): **Anti-Magia** (magias
  não funcionam nele: anula/reduz muito o dano e os efeitos de magias inimigas — mas também limita as próprias magias ofensivas; custo
  de manter) e **Restrição Celestial** (homenagem Toji: abre mão de mana/magia em troca de corpo absurdo — força, velocidade, sentidos,
  resistência, invisível para detecção mágica; enquanto ativa, não conjura e é imune a sure-hit de domínio/barreiras que miram "mana").
  Os dois como glifos/encantamentos de origem do Encantador, montáveis e com níveis.
- **Multiclasse livre**: nada impede farmar/aprender coisas de outras classes (Lições, pergaminhos, itens). Classe e raça dão
  **direcionamento** (afinidade, kit, glifos exclusivos com desconto, rota-guia, bônus), não prisão.
- Cada classe: kit (arma própria + roupa 4 tiers), **glifos de origem exclusivos** (6–10), uma **forma G** própria, um **ritual/progressão
  própria** que grava `ritual_rank` (5 ranks, dificuldade parecida entre todas), **bônus de classe bons** (passivos fortes ligados ao
  estilo, ex.: Ferreiro +durabilidade/reparo, Ladino +crítico, Cavaleiro +armadura com espada, Encantador buffs mais longos, Shinigami
  regen espiritual meditando, Quincy absorve projéteis, Pistoleiro recarga, Ranger companheiros, Apostador sorte, Steel Ball giro).
- **Magias de outras classes**: glifos de origem são EXCLUSIVOS — outra classe só aprende se **um jogador da classe ensinar** (item
  "Lição" escrito pelo professor, ver A1) e mesmo assim com custo maior. Ex.: o Mago não tem "imbuir arma" a menos que um guerreiro ensine.
- O mapa de ícones/cores/nomes está no A1 (tela de classe).

## 2. Fase A (núcleo) — paralelo, implementador + revisor
### A1 — Magias e progressão (`docs/v10/API_spells.md`)
1. **Classes**: `MagicPath` com as 10 classes NOVAS acima (troca as da v0.9; `Category { MAGIC, PHYSICAL, DEXTERITY }`; `Resource`),
   `primaryAffinity`, afinidades, kit/ícones, tela de classe com 3 abas (refazer conteúdos/textos da v0.9). Remover o código da v0.9 que
   não serve (formas guerreiras/à distância do núcleo continuam: `strike`, `arrow`, `stance`, `parry`, `guard`).
2. **9 loadouts**: acabar com loadouts por elemento e com a Roda Elemental. São **9 loadouts** fixos, cada um com Z/X/C/G. Troca:
   **Alt+scroll** e **Mouse 5+scroll** (os dois trocam loadout); teclas 1–9 com Alt = loadout direto. O ELEMENTO da magia vem dos glifos
   (primário = elemento do primeiro efeito elemental; puro Arcano = Arcano); carga de ultimate passa a ser **uma só** (não por elemento).
   **Migração** do save da v0.8: copiar os slots existentes para os 9 loadouts na ordem (roda × loadout) até encher; o resto vira presets.
3. **Custo por cópia**: `SpellParser` calcula `instances` de cada estágio (Dividir, Chuva/Meteoros/Chuva de Meteoros (contagem), Saraivada,
   Eco/Repetir, Encadear, cópias de forma etc.) e o custo vira `formCost + Σ(effects+augments) × instances` (Saltar continua pagando por
   salto). Ultimates/Domínios: efeitos aplicados em pulsos contam pulsos por segundo × duração de forma moderada (documentar).
4. **Mana maior + limite treinável**: mana máxima base ~3× a atual e cresce mais por nível; **Limite de Mana** (teto do máximo) sobe por
   (a) marcos de nível, (b) **Treino**: esvaziar 100% do recurso conta 1 treino (máx. 3 por dia de jogo) e cada treino dá +X ao limite até
   o teto do nível; recarga natural lenta "ao longo dos dias" quando zerado (regen reduzida por 60 s após esvaziar). Vale para Vigor/Foco.
5. **Nível**: máx. **150**; **150→200 escondido** (só aparece depois de `boss.primordial_dragon`). Curva bem mais difícil até 100 (jogador:
   "antes disso quero que se fodam pra upar"), magias absurdas só no fim (Círculo 10 em 100; níveis 100–200 dão limite de mana, glifos
   por magia, poder). Ajustar `Progression` e tudo que usa `MAX_LEVEL`.
6. **Aprender**: usar Pergaminho de Glifo **consome 1 essência do elemento do glifo** (Arcano = essência arcana; fusão = de um dos pais);
   glifo de origem de OUTRA classe só com **Lição** (item `technique_lesson` gravado por um jogador da classe que conhece o glifo: ele
   escreve com tinta + papel + essência; quem lê aprende se tiver Círculo; o professor ganha XP).
7. **Informações da magia**: `ParsedSpell.breakdown()` → por estágio e por efeito: dano estimado (base×poder), cópias, custo de cada parte,
   tempo de conjuração, recarga, alcance/área, duração — para o Grimório mostrar.
8. **Presets**: até 30 rascunhos nomeados guardados no `MagicData` (salvar/carregar/apagar), API `MagicApi.presets`.
### A2 — Social e arenas (`docs/v10/API_social.md`)
1. **Party**: `Party` (núcleo): líder, até 8 membros, convite/aceitar/sair/expulsar, sem dano entre membros, XP compartilhado (já existe o
   compartilhamento por proximidade — use), HUD de grupo (vida/recurso dos membros à esquerda), chat de grupo opcional. **Interação**:
   agachar e girar o scroll do mouse olhando para outro jogador abre uma **roda de interação** (Convidar para o grupo, Trocar itens,
   Duelar, Ver perfil); **Troca**: tela de troca segura de 2 lados (cada um põe itens, os dois confirmam, troca atômica).
2. **Facções aliadas**: `Reputation.setAllied(player, faction, bool)` (máx. 3; só com patamar ≥ Neutro) — membros de facção aliada não
   recebem dano do jogador (golpes, magias, flechas — `ctx.isAlly` passa a considerar), e eles ajudam em brigas. Aba Reputação ganha o botão.
3. **Arenas de chefe protegidas**: `BossArena` — todo chefe (tag `c:bosses`, `BossKit` ou registro explícito) ao entrar em combate registra
   uma arena (centro = posição casa/arena; raio 24–48 por tamanho) protegida **enquanto a luta durar**: jogadores e magias não quebram/
   colocam blocos ali, explosões e `Destruction` não mexem (exceto os efeitos TEMPORÁRIOS do próprio chefe que voltam sozinhos),
   chefes não caem para fora (puxar de volta / teleporte à arena se sair do raio ou cair no vazio). Hubs de dimensão com chefe (Catedral
   Celeste, Trono do Abismo, Fenda) ficam sempre protegidos. Liberar 30 s após a luta.
4. **Guerra de cidades (núcleo de dados)**: `Allegiance` do jogador (cidade/reino a que pertence), `Enemies` (cidade declarada inimiga →
   todos os jogadores da guilda/party do líder viram inimigos: PvP liberado entre eles mesmo com PvP desligado só para esse par; NPCs da
   cidade inimiga hostis/fogem, não negociam). Dados salvos + eventos; o módulo `settlements` usa.
### A3 — UX (`docs/v10/API_ux.md`)
1. **Menu principal** próprio do Isekai Mahou (substitui o TitleScreen): fundo animado (panorama/céu mágico com runas), logo, botões
   estilizados (Um Jogador, Multijogador, Opções, Mods, Sair), versão; sem quebrar outros mods (botões vanilla preservados).
2. **Entrada**: revisar a sequência da v0.9 (classe → raça → Parabéns → título → Espírito Guia por Dialogue) — sem atributos.
3. **Layout fixo do HUD** (`HudLayout` no núcleo): regiões reservadas e fixas — canto superior direito = minimapa (Xaero, com tamanho
   lido/estimado) e, abaixo dele, o rastreador; nada da nossa UI desenha por cima do minimapa; toasts/avisos de reputação vão para a
   esquerda-centro; o cartão fica no canto superior esquerdo; a Pilha no meio-esquerdo; diálogo embaixo. Ajustar quem desenha no núcleo.
4. **Teleporte de mapa bloqueado**: config `BLOCK_MAP_TELEPORT` (padrão true): cancelar `/tp`, `/teleport` e o teleporte de waypoints
   (Xaero/JourneyMap) feitos por jogador fora do criativo (mensagem "Use as Pedras de Teleporte"); pré-semear a config do Xaero com o
   teleporte desligado só se o arquivo não existir.
5. **Provações**: o núcleo expõe `ArenaBounds` para HUD mostrar a borda (anel de partículas/decalque) e "Limite da arena: N blocos" ao
   se aproximar (o módulo trials registra a arena; A3 desenha).
### art
Ícones: 10 classes novas (`textures/gui/class/<id>.png`), glifos novos do catálogo §4 (as origens de cada classe vêm da fase B — o
`icons.py` tem chute por palavra-chave; o coordenador roda de novo na fase C), ícones de party/troca/aliado, raças (`gui/race/<id>.png`).

## 3. Fase B (módulos) — implementador + revisor + verificador
- **10 módulos de classe** (`class_smith`, `class_rogue`, `class_knight`, `class_enchanter`, `class_shinigami`, `class_quincy`,
  `class_gunslinger`, `class_ranger`, `class_gambler`, `class_steelball`): tudo da linha da tabela §1 — glifos (formas/efeitos/aumentos/
  gatilhos de origem), forma G, armas/itens próprios com modelos, roupa da classe (4 tiers: GeckoLib por script, padrão do `outfits`),
  progressão/ritual de 5 ranks (`ritual_rank`), NPCs/estruturas/dimensões próprias quando a classe pedir (Mundo Interior do Shinigami,
  NPCs Quincy, mestre e Partes do Cadáver do Steel Ball, companheiros do Ranger), bônus de classe, kit via `ClassKits`, aba própria no menu B
  se precisar, lendas da classe. O `class_smith` pode mover os glifos de lâmina do `legends_a` para origem Ferreiro (editar SÓ a origem/
  receitas desses glifos em `feature/legends_a`).
- `character` → **raças** (sem atributos): Humano liberado; Elfo, Anão, Meio-Orc, Demônio, Dragonato, Bestial bloqueadas (desbloqueios da
  v0.9), cada raça com **skills únicas** (2–3 glifos/habilidades raciais montáveis no Grimório + passiva), visual, Altar da Reencarnação.
- `cities` → guardas veem saque (abrir baú/barril da cidade, quebrar/pegar blocos de prédios, pegar itens de NPC): reclamam (balão +
  diálogo), −rep, podem prender/atacar; **Persuadir** (diálogo: chance pela reputação, propina com moedas, devolver o item).
- `settlements` (NOVO): **tomar o controle de uma cidade** (Reino Exaltado + missão "Juramento" + vencer o desafio no coração) →
  o jogador vira **Senhor da cidade**: impostos (moedas por dia das trocas), decretos simples, guardas defendem a party dele;
  **fundar a própria cidade**: item "Carta de Fundação" no lugar escolhido → coração da cidade do jogador → escolher estilo (**Reino**,
  **Desértico** com maravilhas como pirâmides e bônus, **Nórdico** com muro de madeira e fácil de upar) → plantas de prédios (menu)
  autorizadas → **NPCs construtores** levantam os prédios bloco a bloco com o tempo, **sem recursos**; níveis da cidade liberam prédios;
  moradores, guardas, mercadores; **Declarar inimiga** (com afinidade máxima: preparar guerreiros/milícia) usando o `Allegiance/Enemies`
  do A2. Integração com reinos/facções e Places.
- `ascension` → círculo +3 de raio com **barreira** (quem ascende não sai; mobs e magias passam), **sem construir** na área, altares nos locais.
- `trials` → registra a arena em `ArenaBounds` (borda visível + distância).
- `travel` → só Pedras/locais (o bloqueio de mapa é do A3); revisar.
- `bosses_e` → **Dragão Primordial para 6 jogadores nível máximo** (vida/dano por grupo, contramágica obrigatória nos ultimates, sem
  morte instantânea: **petrificar em estátua**, **acorrentar**, **drenar vida/mana/nível temporário**, **trocar de lugar**, **apagar um
  loadout por um tempo**, maldições "piores que a morte"), **arena da Fenda maior** (pode editar o molde/hub do `dimensions` para a arena
  — exceção) e protegida pela `BossArena`.
- `hueco_mundo` (NOVO): dimensão **Mundo Oco** (homenagem Hueco Mundo: deserto branco sob lua eterna, árvores de quartzo, Vazios (Hollows)
  e Menos gigantes, palácio no centro com chefes (homenagens originais — ex. "Rei Vazio", "Imperatriz das Areias"), acesso por "Garganta"
  (rasgo no céu por estrutura/ritual; Shinigami e Quincy têm atalho), loot para as classes espirituais. + **uma dimensão extra** que você
  julgar legal (ex.: **Reino das Fadas** ou **Inferno de Brasas**) com 1 chefe.
- `journal` → **rota-guia por classe**: a rota de chefes comum + um **capítulo/linha por classe** (as 15) guiando a progressão própria
  (ritual, itens, lições, partes do cadáver, Mundo Interior...); dicas por Dialogue; nível 150→200 após o Dragão (passo secreto).
- `ui_grimoire` → **9 loadouts** (sem roda), **detalhamento da magia** (dano/custo/cópias por parte, avisos de custo por cópia), **presets**
  (salvar/carregar/apagar), Lições (glifos de outra classe marcados).
- `ui_hud` → 9 loadouts no HUD (sem roda: "Loadout 3/9"), barra de recurso com limite treinável, seguir o `HudLayout` (nada sobre o
  minimapa), HUD de party.
- `farm` (NOVO): **construir mais rápido** (glifos/ferramentas de construção: Construção Arcana — preencher/replicar/copiar e colar
  área pequena com custo de recurso e blocos do inventário; "Planta" de casa pré-pronta que se constrói com os blocos do baú); **farms de
  materiais vanilla** por magia (geradores mágicos de pedra/minério lento com mana, árvores aceleradas, criadouros), **essências**: os
  pedestais dos santuários elementais podem ser **retirados com Toque Suave** e viram **Geradores de Essência** (geram essência do elemento
  com o tempo e mana) — integrado à automação (pedestal rúnico/bateria).
- `outfits`/`armory` → remover/arquivar os conjuntos/armas das classes da v0.9 que saíram (ou manter só como itens genéricos sem kit);
  armas de classe ficam nos módulos de classe.

## 4. Ids (catálogo base; cada módulo de classe define seus glifos com prefixo coerente e reporta)
- Classes: `smith`, `rogue`, `knight`, `enchanter`, `shinigami`, `quincy`, `gunslinger`, `ranger`, `gambler`, `steelball`.
- A1: item `technique_lesson`; A2: nada de item; A3: config `BLOCK_MAP_TELEPORT`.
- Módulos novos: `class_*` (10), `settlements`, `hueco_mundo`, `farm`.
- Glifos de origem: prefixe com a classe quando o nome for genérico (ex.: `gun_bullet`, `card_throw`, `spin_ball`, `zanpakuto_release`).

## 5. Donos (resumo)
- **A1**: `core/MagicPath.java`, `core/MagicData.java`, `core/MagicApi.java`, `core/Progression.java`, `core/ClassKits.java`,
  `core/CoreGameplay.java`, `core/classes/**`, `glyph/**`, `client/PathSelectionScreen.java`, `client/classes/**`, `client/CoreKeys.java`
  (troca de loadout), `network/**` (pacotes de slot/loadout), `src/main/lang/core*` (só chaves de classes/glifos/loadouts), e o MÍNIMO
  em módulos para compilar (trocas de API de loadout/elemento — prefira deixar métodos antigos como adaptadores `@Deprecated`).
- **A2**: `core/social/**`, `core/api/{Party,BossArena,Allegiance}.java`, `core/reputation/**` (aliadas), `core/boss/BossKit.java`
  (registro de arena), `client/social/**`, `src/main/lang/core_social/`.
- **A3**: `client/title/**`, `client/hud/**` (`HudLayout`), `client/objectives/**`, `client/onboard/**`, `core/onboard/**`, `core/api/ArenaBounds.java`,
  `core/ux/**`, `client/KeyConflictFix.java`, `core/MahouConfig.java` (config nova), `src/main/lang/core_ux/`.
- **art**: `tools/gen/icons.py`, `textures/gui/{class,race,social}/**`, `textures/gui/glyph/**`.
- **Fase B**: cada módulo o seu (`feature/<m>/**`, lang, `tools/gen/<m>.py`), com as exceções escritas na tarefa.
