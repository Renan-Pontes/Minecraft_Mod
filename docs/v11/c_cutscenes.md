# v1.1 — Agente C: cutscenes coreografadas

Pedido do dono: "melhore um pouco as cutscenes das skills... principalmente o G tem que ter cutscene que não só a
camera girando... Principalmente as transformações..."

Resultado: cutscenes de **transformação**, **G (ultimate)**, **domínio**, **King Crimson** e **Bites the Dust** agora
têm vários planos de câmera (cortes secos e chicotes), batidas de clarão/tremor/impacto, partículas no mundo e uma
camada 2D própria de cada estilo. Tudo roda no cliente de quem assiste; nenhum pacote novo (o estilo vai no texto do
tipo da cena). As cenas antigas (chefes, portal, ascensão, história, o "ultimate" puro dos chefes) continuam com a
órbita de antes.

Compilação: `bash tools/check-tree.sh c11` → **COMPILOU OK (all)**, 3204 arquivos.

## Arquivos

| Arquivo | O quê |
|---|---|
| `feature/cinematic/client/Choreo.java` (novo) | Coreografias: planos de câmera (`Cam`), batidas (`Beat`) e a biblioteca por tipo/estilo |
| `feature/cinematic/client/SceneFx.java` (novo) | Partículas no mundo de cada estilo (carregar, aura, explosão, raio) |
| `feature/cinematic/client/ScreenFx.java` (novo) | Desenho 2D: máscara radial, linhas de velocidade, quadro de impacto, relógio, faixas, falhas |
| `feature/cinematic/client/StyleOverlay.java` (novo) | Camada 2D de cada estilo + clarão das batidas + quadro de impacto |
| `feature/cinematic/CinematicConfig.java` (novo) | Opções de cliente `isekai_mahou-cinematic-client.toml` |
| `feature/cinematic/client/CutsceneDirector.java` | Câmera em vários planos, batidas, prioridade transformação > ultimate, pose congelada ao pular |
| `feature/cinematic/client/CutsceneOverlay.java` | Etiqueta pelo tipo-base, título que "bate" na tela, chama as camadas novas |
| `feature/cinematic/client/CutsceneShot.java` | `quiet()` (plano mudo para cenas coreografadas) |
| `feature/cinematic/client/CinematicSettings.java` | `impactFrames()`, `speedLines()`, `particleFactor()` |
| `feature/cinematic/CinematicModule.java` | Registra a config; comando `/mahou cinematic test <tipo>` |
| `core/api/Cutscenes.java` | `ultimate(..., formId)` + `ultimateKind(element, formId)`; `transform` intocado |
| `glyph/SpellCaster.java` (linha ~285) | Passa o id da forma: `Cutscenes.ultimate(player, recipe, element, spell.form().id().getPath())` |
| `src/main/lang/cinematic/{pt_br,en_us}.json` | Chaves novas (as duas línguas iguais) |

## Tipos de rede

- `transform:<estilo>` — enviado pelo `Cutscenes.transform` (agente T). Estilo desconhecido = `default`.
- `ultimate:<elemento>[:<forma>]` — enviado por todo G de jogador (ex.: `ultimate:fire:cataclysm`).
  O `ultimate` puro (sem `:`) continua com a órbita antiga (chefes, ex.: `PrimordialDragon`).
- `domain` — agora em 2 planos (vale também para os chefes que usam).
- `king_crimson` (40 ticks) e `bites_the_dust` (50 ticks) — enviados pelo agente J via `playNear`.

Tempos das coreografias são **frações** da duração, então funcionam com qualquer `ticks` (T usa 50–70).

**Prioridade:** um G que *é* uma transformação manda a cena `transform:*` de dentro da forma e logo depois a
`ultimate:*` genérica. No cliente, se uma cena de transformação do MESMO foco começou há ≤ 10 ticks, a `ultimate:*`
é ignorada (o título dela é absorvido para não virar faixa extra). Se a ordem for a inversa, a transformação substitui.

## Plano a plano

Ângulos: 0° = de frente para o rosto, 180° = atrás. "Corte" = sem transição; "chicote" = transição de 2–4 ticks.

### Transformações (`transform:*`, ~3 s)

Esqueleto comum (frações): **(a)** 0 → explosão: close extremo no rosto/olhos, lente fechada (FOV ×0,5→0,4),
aproximando devagar, leve inclinação. **(b)** corte na explosão: plano heroico de baixo para cima, aberto
(FOV ×1,18→1,0 = "soco"), dutch angle. **(c)** 0,8 → fim: chicote para trás do personagem, abrindo e subindo; volta à
câmera do jogo em 6 ticks. Na explosão: clarão, tremor, soco de FOV, quadro de impacto, explosão de partículas, título
"bate" na tela (vem 2,3× maior, encolhe num estalo, treme e tem uma pincelada de cor atrás) + linhas de velocidade.

| Estilo | Explosão | Câmera própria | Partículas (mundo) | Camada 2D | Sons (vanilla) |
|---|---|---|---|---|---|
| `holy` | 38% | close de cima ("divino"); (b) bem baixo olhando o céu; (c) grua alta | penas brancas caindo, olhos dourados; na explosão **pilar de luz de 48 blocos** + **auréola girando** sobre a cabeça | brilho dourado vindo do alto; bordas brancas | beacon, ametista; totem + sino |
| `dark` | 45% | close mais longo e mais fechado | tentáculos de sombra subindo do chão, **olhos brilhando**; explosão de fiapos | **tela escurece até quase preto, só o personagem iluminado**; na explosão abre | 3 batimentos do Warden; sonic boom + wither |
| `time` | 32% | (b) "rebobinando": câmera recua pelo caminho, em saltos de 2 ticks; (c) gira ao contrário | partículas que **voltam** para o ponto; **anel-relógio** vertical atrás, girando ao contrário | cores lavadas (cinza) + **relógio** com ponteiros correndo para trás | tique-taque; sino grave + ametista |
| `speed` | 28% | (b) de longe com **zoom-relâmpago**; (c) chicote de 80° | riscos prateados passando; faíscas | **linhas de velocidade a cena toda**; bordas prateadas | breeze; tridente + fogos; varrida |
| `fire` | 38% | inclinação maior | brasas subindo (mais depois da explosão) | **faixas de calor subindo**, brilho quente embaixo, bordas laranja | carga de fogo, blaze; tiro de blaze + explosão |
| `blood` | 38% | close mais perto | brasas vermelho-escuras, gotas vermelhas, olhos vermelhos | **vinheta vermelha pulsando** (batimento) | batimentos; wither + explosão |
| `ice` | 38% | (b) gira mais | gelo vindo em espiral; aura de gelo | bordas de geada | ametista, neve; vidro quebrando + congelar |
| `storm` | 38% | (b) inclinada | faíscas; **3 raios** (15%, 38%, 66%) do céu até o personagem, cada um com clarão | tom azul-escuro | trovão; impacto de raio ×2 |
| `beast` | 32% | close inclinado; (b) baixo e perto, inclinação 12° | poeira no chão, olhos acesos; pedras voando + onda grande | vinheta marrom pulsando | uivo; **rugido do Warden + Ravager**, tremor forte (3,0 por 30 ticks) |
| `spirit` | 38% | (b) aberto para ver o pilar; (c) grua | energia em espiral; **pilar de reiatsu na cor (rgb) da transformação** + chamas subindo | bordas na cor; linhas de velocidade na cor ficam | alma; beacon + vento |
| `default` | 38% | esqueleto | energia em espiral (rgb) + explosão | escurece as bordas antes; bordas na cor depois | beacon, encantamento; vento + explosão |

### G / ultimates (`ultimate:<elemento>[:<forma>]`, 50 ticks)

**(a)** 0–30%: close no rosto enquanto grita o nome (título bate logo no começo). **(b)** corte aos 30%: plano baixo e
aberto, com a explosão do elemento (clarão, tremor, impacto, partículas). **(c)** 72%–fim: por cima do ombro olhando
**para onde a magia vai** (mira 7 blocos à frente), volta ao jogo em 7 ticks. Camadas 2D com 65% da força das
transformações.

Sabores por elemento: fogo/magma/sol = brasas + calor; fogo infernal = sangue; água/abismo/pureza/vapor = gotas +
faixas de água; terra/areia/cristal/gravidade = pedras subindo, onda dupla, tremor forte; vento/astral = vento em
espiral; raio = raio do céu; luz = pilar + auréola; sombra/vazio/crepúsculo = escuridão com o personagem iluminado;
gelo = geada; natureza = folhas; arcano = runas. Sons próprios por família.

Sabores por forma: `*domain*` → o plano (c) vira a grua que revela o domo; `*meteor*` / `*cataclysm*` → o plano (c)
olha o céu.

### Domínio (`domain`)

(a) 0–40%: órbita baixa e fechada (mãos fechando o selo), bordas escuras. (b) corte aos 40%: grua subindo e revelando o
domo, com clarão, tremor e sino. Usa a órbita "antiga" (raio pelo tamanho do foco), segura para chefes enormes.

### King Crimson (`king_crimson`, 40 ticks)

(a) 0–50%: plano aberto de lado **congelado que gagueja** (a câmera anda em saltos de 4 ticks), a cada salto um tique
seco, um tremorzinho e 1 tick mais escuro; barras de "falha" carmim; vinheta carmim forte; quadro de impacto avermelhado
no início. (b) corte aos 50%: **atrás de quem usou**, por cima do ombro, olhando 4 blocos à frente; volta ao jogo.
Sons: beacon desligando + teleporte grave; tiques; varrida + teleporte no corte.

### Bites the Dust (`bites_the_dust`, 50 ticks)

Abre com explosão (clarão rosa, tremor, impacto, fumaça rosa). (a) 0–22%: close na âncora. (b) **a câmera se afasta da
âncora** (2 → 9 blocos, subindo e girando ao contrário). Tom rosa, **relógio com ponteiros correndo para trás**, linhas de
"rebobinar" subindo, partículas voltando para o ponto. Sons: explosão; sino + beacon; tique-taque; carrilhão no fim.

## Opções (menu do Minecraft: Opções > Isekai Mahou > "Cutscenes (só você)")

Arquivo novo `config/isekai_mahou-cinematic-client.toml`:
- **Quadros de impacto** (`cinematicImpactFrames`, padrão ligado). Também desligam sozinhos com "Esconder relâmpagos"
  (Acessibilidade do Minecraft) ligado ou "Efeitos de distorção" em 0. São 1–2 ticks de inversão a 85%.
- **Linhas de velocidade (mangá)** (`cinematicSpeedLines`, padrão ligado).

Continuam valendo as opções antigas: Cutscenes (desligado = vira só o anúncio), Só as minhas cutscenes, Menos
partículas (metade das partículas das cenas), Tremor de tela (%), "Efeitos de FOV" do Minecraft (se reduzido, a câmera
aproxima/afasta para manter o enquadramento) e "Efeitos de distorção" (clarões).

Segurança mantida: câmera nunca entra em blocos (teste do corpo até a câmera, a cada corte o teste reinicia), distância
máxima 96 blocos, tecla de pular (a pose congela no momento do pulo e volta suave), espectadores nunca travam (andar /
atacar / levar dano devolve a câmera), 1ª pessoa vira 3ª só enquanto a câmera está longe do olho.

## Comandos de teste (precisam de cheats)

`/mahou cinematic test <tipo>` — toca para você, travando como no jogo (com sugestões no Tab):

- `transform:default`, `transform:holy`, `transform:dark`, `transform:time`, `transform:speed`, `transform:beast`,
  `transform:spirit`, `transform:fire`, `transform:ice`, `transform:storm`, `transform:blood`
- `ultimate:fire`, `ultimate:water`, `ultimate:earth`, `ultimate:wind`, `ultimate:light`, `ultimate:shadow`,
  `ultimate:arcane`, `ultimate:lightning`, `ultimate:ice`, `ultimate:nature`, `ultimate:hellfire` (qualquer id de
  elemento serve)
- `ultimate:fire:domain_expansion`, `ultimate:arcane:meteor_shower` (forma no 3º pedaço)
- `ultimate` (o antigo, dos chefes), `domain`, `king_crimson`, `bites_the_dust`

O comando antigo `/mahou cinematic cutscene <tipo> [foco]` continua (só aceita tipos sem `:`).

## O que NÃO foi verificado (não dá para abrir o jogo daqui)

- **Nada foi visto rodando.** Só compilação. Precisa de teste visual de todos os tipos acima.
- Enquadramento real dos closes (distância 1,0–1,45 bloco, FOV ×0,4) — pode precisar de ajuste fino; o close usa a
  altura dos olhos (93% da altura do foco). Em mobs com cabeça fora do centro (dragão) o close de rosto pode errar; por
  isso o `ultimate` puro dos chefes e o `domain` usam a órbita.
- Se a pessoa estiver encostada numa parede, o close de frente encurta (como antes) e pode ficar em 1ª pessoa olhando
  para trás.
- Quadro de impacto (inversão por blend `ONE_MINUS_DST_COLOR`) e a máscara radial (triângulos coloridos) — pode
  conflitar com shaders (Iris/Oculus).
- Partículas de "pilar", "auréola" e "anel-relógio" usam as partículas existentes (`fx_pillar`, `fx_circle`) com
  posição fixa; se o personagem andar durante a cena (mobs), elas ficam para trás.
- Ordem real dos pacotes transformação × ultimate quando a transformação é um G (supus: transformação primeiro).
- Sons: volumes/tons escolhidos no papel.
- Desempenho: estimado (≤ ~6 partículas por tick, ~30 na explosão, máscara radial com 160 triângulos).
