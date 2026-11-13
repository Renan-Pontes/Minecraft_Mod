# Isekai Mahou 1.0 — RELEASE (contrato)

Mod NeoForge 1.21.1 (Java 21, Mojang + Parchment, GeckoLib 4.9.3, Curios, SmartBrainLib, playerAnimator). Dono: Renan (PT-BR, não programa).
Esta é a primeira versão PÚBLICA: os amigos dele vão jogar e testar. **Leia primeiro `docs/ARCHITECTURE.md`** (regras de módulo, núcleo, glifos, arte) — vale tudo de lá, exceto o que este arquivo mudar.

O Renan revisou uma lista de ~120 melhorias e respondeu item por item. Cada trabalho abaixo traz AS PALAVRAS DELE (entre aspas) e o que entregar. Onde ele limitou ou mudou a ideia, a palavra dele manda.
Prioridade geral dele: *"em principal, foque na cidade, a cidade do player deve ser a mecânica mais legal após as magias... Ter mais que 2 construtores, expandir ela a rodo... Ser feliz e por aí vai!"*

## Regras comuns (TODOS)
- **Texto** do jogador em PT-BR + `en_us`, por `Component.translatable`, em `src/main/lang/<modulo>/`. Comentários em PT-BR.
- **Nunca rode gradle.** Compile com `bash tools/check-module.sh <modulo>` ("TUDO OK"); se mexer fora de `feature/<seu módulo>` rode também `bash tools/check-tree.sh <tag>` ("COMPILOU OK"). Outros agentes editam a árvore ao mesmo tempo: **só toque no que é seu** (tabela). Não referencie classes Java de módulos NOVOS de outros agentes da mesma onda — só por id em texto. Núcleo e módulos antigos (já compilados) podem ser usados.
- **Mundos salvos:** nunca renomeie/remova ids, campos NBT ou chaves salvas. Campo novo = opcional com padrão.
- **Desempenho:** nada de varredura grande por tick; espalhe trabalho; só chunks carregados; FX pelo `client/fx/FxQuality`.
- **Balanço** (palavra dele): *"quero que quebrem de usar magias absurdas no lvl 100... antes disso quero que se fodam pra upar"*. Nada de poder fácil. Upar pesa mais que item: *"acho que upar tem que ser mais requisito que itens"*.
- **Tela limpa:** ele NÃO quer poluição (recusou números de dano flutuantes). Avisos curtos, um de cada vez, nada "explodindo na tela".
- **Cidades são sagradas:** nenhum evento/efeito novo destrói ou estraga cidades (NPC ou de jogador). Use `core/api/Places` (`safeZone`) para pular.
- **Admin:** existe `core/ux/AdminAuth` (senha de admin; comandos op só depois de `/mahou admin login`). Não mexa. Comandos de teste continuam como `MahouCommands.op(...)`. Existe também `core/ux/PeaceMode` (Modo Paz) — não quebre.
- **Obtível em survival**: tudo com fonte real (receita, loot, troca, drop) e aba criativa. Nem tudo precisa de receita (*"nem tudo tem que ser craftado"*).
- **Arte:** texturas por `tools/pixelart.py` em `tools/gen/<modulo>*.py`; modelos GeckoLib à mão. NUNCA rode `tools/gen/icons.py` inteiro.
- **Menu B:** abas por `client/menu/MenuTabs.register`. Não lote o menu: prefira sub-abas dentro de abas existentes quando fizer sentido.
- **Bestiário/diário:** se você cria criatura nova, acrescente a entrada do bestiário como os módulos atuais fazem (veja `feature/journal/bestiary`); capítulo opcional em `data/isekai_mahou/journal/<modulo>.json` quando fizer sentido.
- **Debug:** comandos op `/mahou <modulo> ...` para o coordenador testar tudo sem jogar horas.
- **Relatório final** (inglês, curto): arquivos; ids novos; como testar (comandos exatos); o que NÃO foi feito/testado; PEDIDOS (mudanças em arquivos que não são seus).

---
# ONDA 1

## city_build → depois city_life (em sequência, mesma área)
Área dos dois: `feature/settlements/**`, `feature/city_arcana/**`, `src/main/lang/settlements/**`, `tools/gen/settlements*.py` (as chaves de texto de settlements vêm do gerador `tools/gen/settlements_v14_lang.py`: edite o gerador e rode-o).
Palavras dele: o parágrafo de prioridade acima; *"Tente aplainar as coisas, se realmente precisar, colocamos escadas"*; *"temos que melhorar o funcionamento das cidades, a ideia é que elas possam crescer indefinidamente, mas sempre obedecendo o sistema de criação, podendo chegar até 15x15 por exemplo"*; *"Já tem a opção de fazer festival, faz o festival durar 2 dias e ter 'efeitos' na cidade tipo decoração"*; comércio: *"mandam um 'comerciante' que vai como % até o local, e negocia lá"*; defesa de cerco: *"pode ser"*.
Estado atual: cidade fundada cresce por anéis (`Settlement.radius`, 2..12 = 5×5..25×25), várias muralhas, planta no `client/SettlementPanel`, necessidades (comida/combustível/segurança/moral) em `CityEconomy`, obras em `build/ConstructionEngine` (terraços + escadas na frente), festival e invasões já existem. Leia tudo antes.

**city_build (1º)** — obras e terreno:
- **Aplainar**: ao construir, o lote é nivelado (corta/aterra) para o nível da rua, com transição suave de 1 bloco nas bordas; ruas contínuas entre lotes (caminho/lajes). Escada SÓ quando o desnível com o vizinho for inevitável (>1 bloco depois de aplainar). Nada de escada duplicada. Não estrague lotes já construídos de mundos salvos.
- **Muitos construtores**: obras em paralelo = nº de construtores; a Casa dos Construtores dá bem mais construtores por nível; vários construtores na mesma obra aceleram; construtores visíveis trabalhando. Fila de obras bem maior. Obras mais rápidas no geral ("expandir a rodo").
- **Visual**: melhore os moldes/prédios onde der (telhados, variação, detalhes), ruas com postes, praça, bandeiras do estilo da cidade.
- **Decoração de festival**: API `CityDecor` (liga/desliga enfeites pela cidade: lanternas, bandeirolas, flores, fogos à noite) para o city_life usar. Deve desfazer limpo.
- Orçamento de blocos por tick (sem travar o servidor em cidade 15×15+).

**city_life (2º)** — a cidade viva (leia o relatório/código do city_build antes):
- **Felicidade** clara no painel (número + motivos + dica do que falta): depende das necessidades, moradia, enfeites, impostos, segurança, festivais. Cidade feliz cresce (imigração), produz mais e libera coisas; infeliz perde gente. Sem punição injusta.
- **Crescer a rodo, mas pelo sistema**: revise os requisitos de expansão de anel para dependerem de população+felicidade+prédios (não de espera longa); cidade grande (15×15 ou mais) tem que funcionar bem e o painel continuar usável.
- **Festival de 2 dias** do jogo, com os enfeites do `CityDecor`, bônus de felicidade/comércio e fogos.
- **Caravanas**: mandar um comerciante para outra cidade (NPC ou de outro jogador); ele viaja em % (sem andar de verdade), negocia lá e volta com lucro/mercadorias; pequenos riscos; preços diferentes por cidade; tudo pelo painel.
- **Defesa de cerco**: prédios/lotes de defesa (torre de vigia com arqueiros, armadilhas, quartel) que ajudam nas invasões; aviso antes do ataque.
- Qualidade de vida: avisos do que a cidade precisa, pedidos dos moradores (mini-metas com recompensa), coleta fácil de impostos/produção.

## cities_npc
Área: `feature/cities/**`, lang `cities*`, `tools/gen/cities*.py`, `tools/gen/city_build.py`.
- **Cidade inicial** — *"faz os players nascerem numa cidade inicial"*: o ponto de nascimento do mundo passa a ser dentro de uma cidade do mod (praça da cidade/capital mais próxima do spawn original; em mundo novo). Jogador novo nasce lá, seguro (zona segura). Mundo antigo: não mude o spawn de quem já tem cama.
- **Estalagem** — *"estalagem pra alugar"*: pagar para dormir (buff "Bem Descansado" + tira exaustão), alugar quarto por dias (vira sua "casa" — ponto de retorno; o Modo Paz já usa o ponto de renascimento), rumores do estalajadeiro que marcam lugares (estruturas/chefes) no rastreador. O estalajadeiro também vende, bem caro, o item `isekai_mahou:home_key` SE ele existir (módulo da onda 2; por id em texto).
- **Sidequests** — *"sidequests, sim"*: missões curtas com história em cada cidade (3–5 modelos por tipo de NPC, com 2–3 passos e uma escolha simples), recompensa modesta (ouro, reputação, XP pequeno).
- **Rotina simples dos NPCs** — *"Pode ser simples... se for complexo esquece"*: de noite vão para casa/taverna, de dia trabalham; lojas fecham de madrugada. Só se ficar barato e estável.
- **Tirar a venda de pergaminhos de glifo** do Escriba (glifos vêm de exploração, chefes, templos). Deixe pergaminhos em branco/itens de escrita.

## fixes
Área: `feature/afterlife/**`, `core/onboard/**`, `client/onboard/**`, `core/boss/**`, `feature/bosses_e/**`.
- *"ao morrer não virei alma mais, tem que arrumar que parece estar bugado!"* — o sistema de quase-morte/alma (afterlife) parou de acionar. Ache a causa (suspeitos: ordem dos eventos de morte com módulos novos — PeaceMode, space_magic, mana_core/overcharge, arenas — ou condição de classe/raça) e conserte. Garanta que funciona em morte por mob, por chefe e por queda.
- Entrada no jogo: morrer com a tela de classe aberta deixou o jogador "Sem Classe" e depois abriu a tela de RAÇA sem classe. Enquanto o jogador não escolheu classe/raça ele deve ficar protegido (invulnerável, mobs não miram) e, se algo fechar a tela, ela reabre.
- O Dragão Primordial ignora `/kill` — conserte (deve morrer/sumir limpo com /kill).
- *"é isso, pokémon"*: invocações/aliados lutando contam como luta na arena de chefe (a arena não abre nem reseta enquanto invocações do jogador lutam).

## portals
Área: `feature/dimensions/**`, `feature/travel/**`, `client/ux/XaeroSeed.java`, `core/ux/MapTeleportGuard.java`.
- *"Não achei os portais pra todas as dimensões..."*: TODA dimensão do mod (Plano Elemental, Abismo, Santuário Celeste, Reino Feérico, Mundo Hollow, Fenda do Crepúsculo...) precisa de um caminho claro: estrutura de portal que realmente gera (confira raridade/biomas — se alguma quase não gera, aumente), requisito mostrado, e um **Guia de Portais** na aba Viagem do menu B: cada dimensão com "como abrir", requisito (Círculo/oferenda) e botão "rastrear o mais próximo" (seta no rastreador). Dimensão sem portal físico ganha um.
- **Pontos no minimapa (Xaero)** criados sozinhos ao descobrir estrutura importante (cidade, masmorra, torre, cidadela, biblioteca, portal, covil).
- *"LEMBRE DE TIRAR O TP DO XAEROSMAP"*: o teleporte pelo mapa do Xaero tem que ficar impossível para jogador (opção desligada na config pré-semeada E bloqueio no servidor; admin logado pode).

## ui
Área: `feature/ui_hud/**`, `core/MahouConfig.java`, `client/title/**`, lang `ui_hud`.
- *"às vezes o boss fica com a barra lá em cima e a que você vê... bosses deixa só em cima"*: chefes NUNCA têm barra flutuante sobre a cabeça, só a barra do topo. E a busca de entidades das barras flutuantes não roda todo quadro (cache a cada poucos ticks).
- *"Deixe isso no menu do Minecraft, não do mod"*: tela de configurações do mod pelo menu do jogo (Mods → Isekai Mahou → Config, e um botão em Opções), usando a tela de config do NeoForge (`IConfigScreenFactory`/`ConfigurationScreen`). Acrescente as opções que faltam em `MahouConfig` (ligar/desligar partes do HUD, tamanho do HUD, Modo Paz automático, avisos/tutoriais, tremor de tela) com nomes e descrições em PT-BR, e faça o código respeitá-las (as de fora de `ui_hud` vão como PEDIDOS, ou use getters estáticos que os outros leem).
- **Dicas simples** — *"tem que ser simples pra não explodir na tela do cara"*: sistema de dica de primeira vez (uma linha discreta, uma por vez, some sozinha, nunca repete; API estática `Hints.show(player, id)` no seu módulo + lista de dicas dos sistemas principais).
- Tela de carregamento/menu principal com arte do mod e dicas de jogo.

## mastery
Área: `glyph/**`, `client/spellfx/**`, `feature/ui_grimoire/**`, `core/MagicData.java` (só campos novos), lang `core_spells`/`ui_grimoire`.
- **Maestria por glifo** — *"PERFEITO, ISSO É MUITO BOM"*: cada glifo ganha XP de uso (uso real em combate/trabalho, com limite contra spam no vazio) e sobe de nível (ex.: I–X): custo menor, efeito um pouco maior, visual mais forte nos níveis altos. Curva LENTA (é mais um caminho de "sofrer pra ficar forte"). Mostra no Grimório (barra por glifo) e na dica do glifo.
- **Círculo mágico por magia** — *"SIM, mas por magia, não por player"*: no Grimório, cada receita escolhe forma/cor/símbolo do círculo de conjuração (campo opcional na receita; receitas antigas = padrão).
- **Aura ao canalizar** — *"em alguns casos ou usar algum botão pra canalizar mana, mas às vezes essa canalização ao redor vai ser uma merda"*: aura discreta só enquanto medita/segura uma conjuração longa; opção para desligar. Baixa prioridade.

## talents
Área: módulo novo `talents` (já criado), `core/classes/**`, `client/classes/**`.
- **Árvore de talentos** por classe (15 classes) — *"Sim"*: pontos ganhos ao subir de nível, tela visual (aba/sub-aba no menu B), nós passivos pequenos (use atributos/modificadores existentes), dados em JSON por classe. Guarde em attachment PRÓPRIO (não em MagicData).
- **Subclasses no Círculo 5** — *"Se conseguir! ÓTIMO"*: cada classe escolhe 1 de 2 especializações (30 no total), cada uma com identidade clara e 2–3 passivas marcantes (sem criar 30 kits de técnicas novos). Escolha permanente (o reset fica em outro módulo: exponha `Talents.reset(player)`).
- Balanço: bônus pequenos somados; nada que deixe upar fácil.

## abyss
Área: módulo novo `endless_abyss` (já criado), `core/Progression.java`, `core/balance/**`.
- *"Deixe difícil de upar, ainda mais após lvl 50 e 100"*: revise a curva de XP — mais íngreme depois do 50 e muito mais depois do 100 (veja `Progression.HIDDEN_MAX_LEVEL`). Não mexa em nível já alcançado de mundo salvo. Comando op `/mahou balance` que imprime a tabela (XP por nível, total acumulado).
- **Abismo Infinito** — *"Perfeito! entra nela pelo B após o lvl 100"*: masmorra sem fim (dimensão/arena própria), entrada por uma aba/botão no menu B liberada no nível 100; andares cada vez mais difíceis com ondas + chefes, recorde por andar (salvo; exponha para o ranking: `SavedData` + método estático de leitura), recompensas raras a cada N andares; sair/morrer devolve ao lugar de origem com segurança.
- **Lutas das Sombras** — *"Adicionamos isso após o lvl 100... como um shadowfight"*: no Abismo, enfrentar a versão sombria (mais forte, visual escuro) dos chefes que o jogador já venceu, com recompensa própria.

## combat_a
Área: `core/anim/**`, `client/anim/**`, módulo novo `combat_lock` (já criado).
- **Travar o alvo** — *"é legal, mas tem que mudar a câmera pra condizer, não?"*: tecla trava num inimigo; câmera acompanha (em 3ª pessoa: por cima do ombro, enquadrando jogador e alvo; em 1ª: mira suave), marcador discreto no alvo, roda/tecla troca de alvo, solta sozinho se o alvo morre/some/fica longe. Magias e golpes miram o alvo travado.
- **Bloqueio com aparo** — *"Coloca um block com parry"*: todos podem bloquear (tecla/segurar; com arma ou cajado): reduz dano enquanto segura (gasta vigor/fome), e bloquear no instante certo (~0,2–0,3 s) é APARO: anula o golpe, atordoa rápido o atacante, som/flash curto. Não empilhe com a esquiva de forma abusiva; Modo Paz bloqueia como já bloqueia a esquiva.
- **Aviso "!!!"** — *"Pode ser um '!!!' de aviso"*: um "!!!" curto na borda da tela/sobre o mob quando vem golpe forte de mob comum ou ataque por trás. Discreto.

---
# ONDA 2

## home_realm
Área: módulo novo `home_realm`.
*"ter uma 'dimensão casa', se comprar libera pra entrar a qualquer hora que não em combate por 2 min"*: dimensão pessoal (um terreno por jogador numa dimensão própria, com uma casa inicial pronta e espaço para construir), comprada com o item `home_key` (caro; vendido na estalagem). Entrar/sair por tecla ou pelo menu B, só se estiver há 2 min fora de combate; volta para onde estava. Convidar amigos. Seguro (sem mobs, sem PvP). Baús/camas funcionam.

## renown
Área: módulo novo `renown`.
- **Ranking** — *"Ranking dos players é legal pra geral saber"*: quadro (bloco na Guilda/cidades + sub-aba no B): maior nível, chefes mortos, andar do Abismo, torres conquistadas, cidade mais rica/populosa. Leia os dados por contadores do `MagicData`/ids; trate "não existe".
- **Títulos** — *"Títulos são legais"*: títulos ganhos por feitos, escolhidos no B, mostrados acima do nome (discreto) e no chat.
- **Recorde de tempo por chefe** — *"isso é legal"*: cronômetro da luta, melhor tempo por chefe no ranking.

## market
Área: módulo novo `market`, `core/social/**`, `client/social/**`.
- **Leilão entre jogadores** — *"isso é legal, pode ser dentro do M"* (entenda como: dentro do MENU — aba "Mercado" no B, e também no quadro da Guilda): anunciar item com preço, comprar mesmo com o dono offline, taxa pequena, retirada do dinheiro/itens. Moeda: a do mod (veja guild/cities).
- **Grupo melhorado** — *"Hum, não pensei nisso, pode ser"*: XP dividido para quem está perto, marcador dos amigos (HUD discreto + mapa), chat de grupo.

## world_events
Área: módulo novo `world_events`.
*"eventos não afetam as cidades... mas faz eventos tipo 'Queda de meteoro': cai um meteoro próximo numa área livre e aparecem aliens... Lua de Sangue faz mais mobs; tempestade aumenta os efeitos de magia de água e vento... Terremoto e por aí vai... Só não vai destruir as cidades que vai ser triste kkkk"*
- Queda de Meteoro (cratera pequena longe de construções + invasores alienígenas — 2–3 criaturas novas + mini-chefe — e minério/loot do meteoro), Lua de Sangue (mais monstros, mais loot/XP leve), Tempestade Arcana (água/vento mais fortes), Terremoto (tremor, fendas pequenas no ermo, minérios expostos), Tempestade de Mana (magia mais forte para todos, cristais caem). Aviso curto; raros; NUNCA dentro/perto de `Places` seguros nem de blocos de jogador.

## familiars
Área: módulo novo `familiars`.
*"Familiares são legais, ainda mais se for tipo patrono que dependem de como você é e forem mudando com você"*: um familiar por jogador, cuja FORMA nasce do jeito de jogar (classe, raça, elemento mais usado, feitos: contadores do `MagicData`) e evolui em estágios conforme o jogador cresce/muda. Ritual simples para despertar. Ajuda pequena e temática (achar minério, avisar perigo, cura leve, carregar itens) — sem lutar pelo jogador. ~8–10 formas base × 3 estágios (GeckoLib simples).

## arcana_items
Área: módulo novo `arcana_items`, `feature/space_magic/**`.
- **Colar de Mana** — *"prefiro adicionar uma forma de acumular mana em item tipo colar e gastar o sobressalente"*: curio que guarda a mana que sobraria (regen com a barra cheia / depósito) e é gasta antes da barra. Níveis de capacidade. Ligue com a sobrecarga do `mana_core` se fizer sentido (sem criar mana infinita).
- **Pergaminho de Receita** — *"é válido, mas dificulta fazer os pergaminhos pra não ser só reseta e farma de outro"*: grava a receita de uma magia para dar a um amigo (ele precisa conhecer os glifos). Fazer é CARO (materiais raros + XP/mana), uso único.
- **Recuperar Pertences** — *"eu daria até magia pra recuperar os itens à distância mas só lvl alto"*: glifo de Círculo alto que traz de volta os itens da sua última morte (dos drops/corpo), custo alto.
- **Bolsa do Alquimista** — *"só pra algumas classes como bruxas e mágicas"*: bolsa só para essências/cristais/reagentes, usável só por classes mágicas.
- **Pergaminho de Retorno**: consumível barato que leva à última cidade visitada (fora de combate).
- Loot de chefe vai para o Inventário Dimensional quando o inventário está cheio (se o jogador tem o glifo).

## character
Área: `feature/character/**`.
- *"podemos fazer um local pra resetar e outro pra trocar de classe e Raça"*: dois lugares distintos (estruturas/altares raros ou em capitais): **Altar do Recomeço** (reseta talentos/pontos/atributos — chame `Talents.reset` por reflexão/id se existir) e **Altar da Reencarnação** (troca de classe e de raça, custo MUITO alto, mantém nível mas perde o específico da classe antiga).
- *"PFF"* (transformações raciais mais bonitas): entrada com efeito, aura e partículas próprias para cada uma das 11 raças, som, pose.

## gear
Área: módulo novo `gear_plus`, `feature/outfits/**`, `feature/armory/**`, `feature/equipment/**`.
- **Bônus de conjunto** (4 peças do mesmo set) — *"Sim"*.
- *"conjunto de classe, permite ele pegar outros no B de alguma forma... pra caso perca por N motivos... é o básico né"*: recuperar o kit/conjunto de classe pelo menu B (com recarga longa/custo simbólico, sem duplicar: os itens são vinculados — veja `core/item/SoulBinding`).
- **Gemas com encaixe** — *"só pra algumas classes físicas (como rune knight ou ferreiro)"*.
- **Armas que despertam** — *"Legal pra algumas classes"*: armas de classe que sobem com abates e liberam 1 habilidade no despertar (classes físicas/destreza).

## mounts_vehicles
Área: `feature/mounts/**`, módulo novo `vehicles`, `feature/class_hollow/client/HollowInput.java` (só a tecla).
- *"Tem que deixar mais claro a montaria! liberar outras montarias e arrumar isso."*: explicar como conseguir/usar montaria (dica + página), mais montarias obtíveis (por reputação, chefes, cidades), consertar o conflito da tecla R (Hollow × habilidade da montaria).
- *"Crie vários veículos simples mágicos, não com magia, mas que você crie eles e com isso possa usar pra explorar o mundo"*: 4–5 veículos fabricáveis (ex.: vassoura, tapete voador, barco arcano rápido, carroça-golem, planador/dirigível pequeno) com controles simples, combustível = mana/cristal, cada um bom para um terreno.

## combat_feel
Área: módulo novo `combat_feel`.
- **Hit-stop** — *"é legal, pode fazer"*: micro-pausa (2–4 quadros, só visual/cliente) em golpe pesado, crítico, aparo e explosão grande; opção de desligar.
- **Golpe de misericórdia** — *"Isso é legal"*: inimigo comum com <10% de vida pode ser finalizado (tecla de interação perto) com animação curta.
- **Combo de magias** — *"é tipo um storm do Magic"*: conjurar em sequência rápida soma um contador "Combo ×N" discreto, com bônus pequeno crescente que zera ao parar.
- **Conjuradores mais espertos** — *"pode ser"*: mobs que conjuram desviam, recuam, se curam e pedem ajuda (por tag de entidade, sem editar os módulos das criaturas).

---
# ONDA 3

## towers
Área: `feature/towers/**`.
*"Melhore o design do que for possível!"* (as torres são caixas de tijolo por fora): contrafortes, sacadas, janelas, telhado, estandartes, base e topo marcantes. *"Legal! Ilhas voadoras também pff"*: variantes novas (floresta, submersa, flutuante) e **ilhas voadoras** com ruínas e loot. **Afixos** por andar — *"Pode ser"* — (modificadores aleatórios + recorde de tempo).

## dungeons
Área: `feature/depths/**`, `feature/nether_citadel/**`, `feature/end_library/**`, `feature/dwarf_hold/**`.
- Cidadela Demoníaca nasce meio enterrada — *"Se der pra resolver ok"*: abra espaço em volta/acesso.
- **Quebra-cabeças** — *"Sim, puzzles nas dungeons e coisas"*: salas de enigma (alavancas, placas, espelhos/luz simples, sequência de runas) nas masmorras e na biblioteca.
- **Afixos** na Masmorra em Camadas.
- Melhore o visual do que der. Confira Forja Rúnica, Altar do Pacto e itens de anime (*"Arruma isso aí"*): revise o código por erros óbvios.

## biomes
Área: `feature/flat_lands/**`, `mixin/OverworldBiomeBuilderFlatMixin.java` (+ mixins novos seus e a linha no `isekai_mahou.mixins.json`), módulo novo `magic_biomes`, `worldgen/structure_set/*.json` (só `spacing/separation`).
- *"Pode ser, mas aí separa mais as coisas tipo estruturas"*: 2–3 biomas mágicos no Overworld (Floresta de Cristal, Pântano Assombrado, ...) pelo mesmo método do flat_lands (cuidado com o `VanillaRegistries` — veja `FlatLandsBiomes.vanillaBootstrap`), E aumente um pouco o espaçamento das estruturas do mod para o mundo não ficar entupido (cidades continuam achando lugar).
- *"Ok, válido, mas simples, nada muito complexo"*: 1 bioma próprio no Nether e 1 no End.
- **Caverna de Cristal Mágico** — *"Feito"*: bioma subterrâneo com luz própria.

## lore → depois story (em sequência)
**lore** — área: módulo novo `lore`, `docs/LORE.md`.
- *"Crie a lore do mundo, pra quem quiser saber..."*: escreva a bíblia do mundo em `docs/LORE.md` (coerente com o que já existe: reinos Aldória/Sahra/Nordheim, dimensões, chefes, classes, raças, Rei Demônio, por que o jogador foi trazido) e uma **Enciclopédia** no menu B (sub-aba do Diário se possível) para quem quiser ler.
- **Páginas de lore colecionáveis** — *"Sim pff"*; **ruínas pequenas com lore** — *"pra justificar explorar a lore, ela passar coordenadas de coisas no mapa"*: ler um fragmento revela/marca um lugar (estrutura, chefe, tesouro).

**story** — área: `feature/journal/**`, `feature/cinematic/**`.
- **Campanha** — *"Ok, mas sem forçar ela, se o player pular, pulou"*: fio narrativo opcional pela rota principal (usa `docs/LORE.md`), sempre pulável.
- **Cutscenes nos marcos** — *"Pode ser"* (curtas, puláveis).
- *"o ideal era ser no caminho do jogo, nas progressões"*: o Diário diz ONDE conseguir o que o próximo passo pede (em vez de dica em cada item).
- Bestiário de tudo que faltar (v1.6 e ondas 1–2).

## boss_polish
Área: `feature/bosses_a/**` … `feature/bosses_g/**` (menos o que a onda 1 mudou em bosses_e: leia antes), `feature/espada/**`.
- *"okay"*: animações de ataque/morte mais marcantes nos chefes antigos; *"justo, é válido"*: falas curtas e mini-cutscene de entrada/troca de fase para os chefes que não têm.

## audio
Área: módulo novo `ambience`, `tools/gen/ambience*.py`.
- **Sons próprios das magias** — *"Sim"*: por elemento e tamanho (gerados por código: numpy + ffmpeg, como `tools/gen/boss_music.py` e `character_sounds.py`), tocados por evento de conjuração/impacto sem editar os glifos.
- **Música ambiente** — *"Sim, sempre música baixa e de dark fantasy"*: faixas ambientes GERADAS (nada baixado, nada com direito autoral) para masmorra, cidadela, biblioteca, torre, cidade, Abismo; volume baixo, troca suave, opção de volume/desligar.
- Brilho no escuro (emissivo) onde for simples — *"pode ser"*.

## perf
Área: `core/ElementFx.java`, `core/ElementReactions.java` + FX de reação do cliente, `feature/forms_a/**`, `tools/music_import.py`, `assets/.../sounds/music/**`; e a troca mecânica de `MahouConfig.REDUCED_PARTICLES` por `FxQuality` nos arquivos que ainda leem direto (exceto pastas de outros agentes da onda 3).
- Partículas de magia em lote (um pacote por efeito, não um por partícula); reações elementais mais leves *"mas deixa ainda bem forte"* (o visual continua impactante); recomprimir as músicas (.ogg menor, sem perder qualidade audível); tudo respeita o Preset Gráfico.

---
# ONDA 4

Pedido do Renan (2026-11-12, depois das ondas 1–3 definidas): *"Antes de lançar, quero que verifique uma coisa, vamos chamar de wave 4... A ideia é verificar se tem como melhorar a criação de cidades e deixar as transformações e outras coisas controláveis por magia, tipo os domains... entende?"*

Perguntei e ele escolheu:
- Transformações: **"Montar no Grimório"** — igual ao Domínio: uma forma "Transformação" montada com glifos (elemento/aura, efeitos dos golpes, leis/votos, duração); a tecla V e as formas de classe usam a transformação montada.
- Outras coisas controláveis por magia: **"Liberações de classe"**, **"Invocações e familiares"**, **"Cidade por magia"**.
- Criação de cidades: **"Prévia antes de fundar"**, **"Posição livre dos prédios"**, **"Construir com magia"**.

Como o Domínio funciona hoje (o modelo a copiar): uma FORMA (`feature/domain/glyph/DomainExpansionForm`, `FormGlyph.acceptsLaws()`) + efeitos + aumentos + LEIS (`glyph/LawGlyph`), tudo montado no Grimório e validado pelo `glyph/SpellParser`. Desde a v1.4 as transformações aceitam só Leis de Transformação (`glyph/TransformLawGlyph`, `TransformLaws`, `TransformTargets`, módulo `transform_laws`, aba "Votos").

Regras extras desta onda:
- Balanço dele continua valendo: nada de poder fácil; o forte vem com nível/Círculo alto. Custo, duração e recarga saem da receita.
- Mundos salvos: tudo o que existe hoje (transformações fixas, Votos escolhidos, invocações, lotes de cidade) continua funcionando sem o jogador refazer nada.
- A onda 3 roda ao mesmo tempo: NÃO toque `feature/forms_a/**`, `core/ElementFx.java`, `core/ElementReactions.java` (perf), `feature/journal/**`, `feature/cinematic/**` (story), nem `feature/towers|depths|nether_citadel|end_library|dwarf_hold|flat_lands|magic_biomes|lore|ambience|bosses_*|espada`. O que precisar lá vai como PEDIDO.

## transform_forge → depois releases (em sequência)
**transform_forge** — área: `glyph/**` (núcleo: só o necessário para a Forma de Transformação montável; Domínio e leis atuais continuam EXATAMENTE como estão), `feature/transform_laws/**`, `feature/character/**` (transformação racial, tecla V), `feature/ui_grimoire/**`.
- **Forma "Transformação"** (glifo FORM novo, com fonte real em survival e requisito de Círculo coerente com o Domínio): o jogador monta no Grimório como monta o Domínio — forma + elemento (define aura/partículas/cor) + EFEITOS (o que os golpes e/ou a aura aplicam enquanto transformado; ex.: Incendiar → golpes queimam, Cura → regenera aos poucos) + aumentos (duração, potência, alcance da aura) + Leis de Transformação (as 13 que já existem). O Grimório mostra o resumo (o que ganha, o que custa, duração, recarga).
- **Tecla V (raça):** a Transformação Racial pode usar uma transformação montada: a aba "Votos" passa a escolher a receita (e continua escolhendo votos soltos como hoje). A base da raça (asas, voo, visual, passivas da forma) continua; a receita entra por cima. Sem receita escolhida = igual a hoje.
- Transformações fixas (Kaioken, Lobo, Lich, Transcendência...) continuam; se for simples, deixe-as aceitar efeitos depois delas na receita pelo mesmo caminho.
- Visual pelo elemento da receita (FX pelo `FxQuality`; tremor pelo `MahouConfig.shakeScale()`).
- **API para o estágio 2** (documente no relatório): como uma liberação de classe recebe e aplica uma receita de transformação (algo como `TransformLaws.begin(...)` já faz para leis).

**releases** — área: `feature/class_shinigami/**`, `feature/class_quincy/**`, `feature/class_hollow/**` (e outra classe que tenha "liberação", se achar).
- Shikai/Bankai, Resurrección, Vollständig e parecidas passam a ser ajustáveis por magia: cada liberação tem um espaço de receita (efeitos dos golpes/aura + leis) montado pelo jogador com a API do estágio 1. A identidade da liberação (visual, técnicas próprias, nome) NÃO muda; a receita acrescenta. Sem receita = igual a hoje.
- Mostre isso na tela da classe/Grimório de forma simples (um botão "Moldar liberação"), com dica de primeira vez pelo `Hints.show`.

## summon_forge
Área: `feature/forms_b/**` (formas de invocação/conjurador), as classes de invocação dos módulos `fire_water`, `earth_wind`, `light_shadow`, `fusions_a` (só as invocações), `feature/familiars/**`.
- **Invocações montáveis:** o que vem depois da forma de invocação na receita passa a mandar na criatura: elemento, o que o ataque dela aplica (efeitos), quantidade/tamanho/duração (aumentos) e comportamento simples (seguir, guardar um ponto, caçar o alvo marcado, ficar parado) escolhido por glifo/gatilho ou por um comando curto. As invocações fixas viram os "corpos"; receitas antigas continuam valendo.
- **Familiares:** o dono pode ensinar UMA receita de apoio ao familiar (cura leve, luz, proteção, achar coisas) que ele usa sozinho com recarga. Palavra dele na onda 2: familiar ajuda *sem lutar pelo jogador* — então nada de receita de dano no familiar.

## city_plan → depois city_magic (em sequência)
**city_plan** — área: `feature/settlements/**`.
- **Prévia antes de fundar:** com a Carta na mão, um holograma no terreno mostra a praça, os lotes do começo e a linha da primeira muralha; cores avisam terreno ruim (água, desnível grande, perto demais de outra cidade/estrutura). Dá para mover e girar antes de confirmar. Nada é gasto antes de confirmar.
- **Posição livre dos prédios:** ao planejar uma obra o jogador escolhe ONDE ela fica e para onde vira, com prévia fantasma (verde = pode, vermelho = não pode) dentro da área da cidade, respeitando ruas, muralhas e outros prédios. Os lotes da grade que já existem continuam válidos (mundos salvos) e a grade continua sendo o jeito rápido/padrão. Se couber: mover/girar um prédio pronto (com custo).
- Leia primeiro o que a onda 1 fez (`CityTerrain`, `ConstructionEngine`, `CityPlanning`, `CityGrowth`) — relatórios em `docs/v10r/w1_city_build.md` e `w1_city_life.md` se existirem; não desfaça.

**city_magic** — área: `feature/settlements/**`, `feature/city_arcana/**`.
- **Construir com magia:** canalizar mana perto de uma obra acelera os construtores; obras pequenas podem ser erguidas na hora por um custo alto de mana; moldar o terreno da cidade (nivelar, subir, baixar um lote) com magia. Tudo escala com nível/Círculo, sem pular os requisitos da cidade (felicidade, moradores, nível do centro).
- **Defesa da cidade montada como um Domínio:** na Barreira Arcana / Núcleo de Mana (`city_arcana`) o Lorde grava uma receita (efeitos + leis) que a barreira aplica nos invasores durante cercos (ex.: Lentidão + Queimar), gastando a mana do Núcleo. Nunca atinge moradores, aliados nem blocos.
- Cidades são sagradas: nenhuma magia nova estraga cidade de ninguém; magia de construção só funciona na SUA cidade (ou com permissão do Lorde).

---
# EXTRA (roda junto com as ondas 3 e 4)

## integration
Área: `feature/cities/**`, `feature/guild/**`, `core/MahouConfig.java` (só opções novas), lang `ui_hud` (só nomes/dicas das opções novas), `docs/ARCHITECTURE.md` (catálogo), e UMA linha de leitura de opção em `client/social/PartyMarkers`, `feature/renown/client/TitleTags` e `feature/renown/client/FightTimerHud`.
Trabalho de costura: ligar o que as ondas 1–2 criaram às cidades e à Guilda (pedidos dos agentes — relatórios em `docs/v10r/w1_*.md` e `w2_*.md`, leia os PEDIDOS de todos).
- **Blocos nas cidades NPC** (mundos novos E mundos já criados; nunca quebre blocos da cidade: só em ar, sobre chão firme, perto da praça/guilda — veja como `feature/renown/BoardPlacer` já faz e siga o mesmo caminho): `isekai_mahou:market_board` em cidades com mercado/guilda; `isekai_mahou:new_beginnings_altar` e `isekai_mahou:reincarnation_altar` só nas capitais (palavra dele: *"um local pra resetar e outro pra trocar de classe e Raça"* — dois lugares distintos, não lado a lado).
- **Vendas nos mercadores das cidades** (`feature/cities/trade/CityTrades`, por id, com `required:false`/checagem de existência): `return_scroll` barato na estalagem/armazém; `alchemist_bag` no alquimista; `land_drake_reins` na estalagem com reputação Amigável (`b.modSell("land_drake_reins", "land_drake_reins", 45, 1, 1)`); `bond_incense`; `gem_chisel` e gemas lascadas; comprar `world_events_star_metal`.
- **Guilda:** botão "Mercado" na `GuildScreen` que chama `StatusScreen.open("market")`.
- **Opções no menu do Minecraft** (`MahouConfig`, cliente, com nome e descrição PT-BR/EN em `isekai_mahou.configuration.<chave>`): marcadores do grupo (ligar/desligar), cronômetro da luta de chefe, títulos acima dos nomes — e faça as três classes de cliente citadas lerem a opção.
- **Documentação:** acrescente ao catálogo do `docs/ARCHITECTURE.md` os ids novos das ondas 1–2 (itens, blocos, entidades, dimensões, abas, contadores, APIs públicas como `GlyphMastery`, `SpellParser.parse` de 4 argumentos, `SpellRecipe.circle()`, `Hints.show`, `PortalGuide.register`, `MapMarkers.registerStructure`, `ClassTalents`, `AbyssRecords`, `GuardEvent`).

---
# ONDA 5

Pedido do Renan (2026-11-13, depois de ver o relatório da 1.0 já compilada):
*"Calma 1. Transformações tem que ser editáveis pros efeitos que o player quiser... 2. O dragão é o fim, não deve ter muito conteúdo após ele, mova tudo pra antes... depois dele é só a torre infinita... 3. As cidades, temos que dar upgrades nela, pra que saiam de uma vila pra uma cidade, sendo que talvez, devêssemos adicionar mais estilos além dos 3 e uma mudança de lvl mais significativa"*

Tudo das ondas 1–4 está pronto e compilado (relatórios em `docs/v10r/`). Valem as "Regras comuns" do começo deste arquivo. Mundos salvos continuam carregando: nada de renomear ids/chaves.

## transform_edit
Área: `glyph/**` (núcleo, só o necessário), `feature/transform_laws/**`, `feature/character/**`, `feature/ui_grimoire/**`, `feature/class_shinigami/**`, `feature/class_quincy/**`, `feature/class_hollow/**`, e os registros de transformação dos outros módulos de classe (só a linha que registra o espaço/alvo).
Palavra dele: *"Transformações tem que ser editáveis pros efeitos que o player quiser"*.
Hoje (leia `docs/v10r/w3_transform_forge.md` e `w4_releases.md`): existe a forma "Transformação" montável e espaços "Moldar" para a tecla V, Shikai, Bankai, Vollständig e Resurrección; mas ficaram de fora Máscara do Vazio (`shinigami_visored`), Vasto Lorde (`shinigami_vasto_lorde`), Letzt Stil (`quincy_letzt_stil`) e as transformações fixas (Kaioken, formas de Druida, Lich, Transcendência, Modo Sábio... tudo o que está em `TransformTargets`/`TlawTargets`), que só aceitam efeitos pela forma nova.
Entregar:
- **TODA transformação do mod tem um espaço editável**: racial (V), todas as liberações e formas de classe (inclusive as três citadas e as formas do Hollow) e todas as transformações fixas. O jogador escolhe os efeitos que quiser entre os glifos de efeito que ele CONHECE — sem lista fechada inventada; se hoje há filtro que barra efeitos, remova ou deixe só o que for tecnicamente impossível (e diga qual no relatório). Limite de quantidade = o mesmo das magias (glifos por Círculo).
- **Um lugar só e claro para editar**: na aba de Votos/Transformação, a lista de TODAS as transformações que o jogador tem, cada uma com "Moldar" (efeitos + aumentos + leis) e um resumo do que ganha/custa. As telas de classe continuam com o atalho.
- A receita também pode mexer em duração e recarga por aumentos (hoje só a mana vem da receita), com custo coerente — nada de poder fácil antes do nível alto.
- Sem receita = exatamente como hoje. Sessões devem sobreviver a relogar se for simples (hoje perdem a mana).
- Dica de primeira vez pelo `Hints.show` e uma linha no texto da aba.

## journal_end
Área: `feature/journal/**`, os arquivos `data/isekai_mahou/journal/*.json` e a linha `"order"` nos geradores deles (`tools/gen/journal.py`, `depths.py`, `dwarf_hold.py`, `nether_citadel*.py`, `end_library_lang.py`, `towers.py`, `magic_biomes.py`, `journal_story.py`), lang `journal`.
Palavra dele: *"O dragão é o fim, não deve ter muito conteúdo após ele, mova tudo pra antes... depois dele é só a torre infinita"*.
Hoje a trilha principal é linear por `order`: ... `hollow_world` 55, `las_noches` 56, `primordial` 60 (final), e DEPOIS `depths` 61, `dwarf_hold` 62, `nether_citadel` 63, `end_library` 64, `towers` 65, `magic_biomes` 66, e o capítulo secreto `beyond_limit` 70.
Entregar:
- Mova os seis capítulos para ANTES do Dragão, em pontos que façam sentido pela dificuldade e pelo que cada um exige (ex.: biomas mágicos e torres cedo/meio; masmorra e cidade anã no meio; cidadela do Nether e biblioteca do End perto do fim). Para a rota principal não virar uma parede, deixe obrigatório em cada um só o essencial e marque o resto como opcional. Atualize o JSON E o gerador de cada um. Ids de capítulo e de passo não mudam.
- Jogadores de mundos antigos que já venceram o Dragão não podem ficar travados nem perder o final: confira a lógica de "capítulo atual" com esse caso.
- **Depois do Dragão só a "torre infinita"**: o capítulo pós-final (`beyond_limit`) passa a ser sobre o Abismo Infinito (módulo `endless_abyss`, liberado no nível 100: andares, marcos de 10 andares, Lutas das Sombras — contadores `abyss.best_floor`, `abyss.floors`, `abyss.shadow_wins`). Os passos antigos dele que não são do Abismo viram opcionais ou saem da lista visível (sem quebrar flags salvas). O epílogo do Dragão aponta para o Abismo.
- Ajuste as batidas da campanha (`journal_story.py`) e a aba "Onde" que dependam da ordem.

## city_tiers → depois city_styles (em sequência, mesma área)
Palavra dele: *"As cidades, temos que dar upgrades nela, pra que saiam de uma vila pra uma cidade, sendo que talvez, devêssemos adicionar mais estilos além dos 3 e uma mudança de lvl mais significativa"*. Lembrete da prioridade dele: a cidade do jogador é a mecânica mais legal depois das magias; ele gosta de crescer "a rodo", construtores, felicidade; cidade nunca é destruída.
Leia antes: `docs/v10r/w1_city_build.md`, `w1_city_life.md`, `w3_city_plan.md`, `w4_city_magic.md` e o código de `feature/settlements/**` (não desfaça nada).

**city_tiers** — área: `feature/settlements/**`.
- **A cidade muda de CATEGORIA ao subir o nível do centro**, com nome próprio em cada degrau (algo como Acampamento → Vila → Burgo → Cidade → Capital; escolha nomes bons em PT-BR/EN) mostrado no letreiro do lugar, no painel, no mapa e no anúncio.
- **Subir de nível tem que ser um acontecimento visível na cidade INTEIRA**, não só no prédio do centro: ruas (terra → cascalho → pedra → pavimento nobre), postes e iluminação, praça maior com monumento/fonte, muralha (paliçada → pedra → fortificada com torres e portão maior), fachadas das casas e prédios já construídos ganham acabamento do novo degrau, estandartes, jardins, mais moradores e tipos de NPC. As mudanças são aplicadas aos poucos pelos construtores (respeite o orçamento de blocos por tick da onda 1), sem travar o servidor, e nunca apagam o que o jogador colocou à mão.
- **Cada degrau libera coisas de verdade**: novos prédios e serviços, mais construtores, mais anéis, caravanas melhores, defesa — organize o que já existe por degrau e mostre "o que você ganha no próximo nível".
- **Requisitos que pesam** (ele gosta que upar custe): moradores, felicidade, prédios-chave, tesouro e um "grande projeto" por degrau (obra especial ou feito, ex.: vencer um cerco). Lista de requisitos clara no painel, com o que falta.
- **Cerimônia** curta e pulável ao subir (estandarte, fogos do `CityDecor`, anúncio no servidor).
- Cidades que já existem mantêm o nível e ganham o visual do degrau delas aos poucos.
- Comandos de teste para pular degraus. Descreva no relatório as APIs/pontos onde o estilo (estágio 2) entra: paleta de rua, muralha, poste, monumento por degrau.

**city_styles** — área: `feature/settlements/**`.
- **Mais estilos além dos 3** (Reino, Desértico, Nórdico) para cidades de jogador. Faça pelo menos 3 novos, cada um com cara e personalidade próprias (bônus e ponto fraco, como os atuais): **Oriental** (telhados curvos, madeira escura e vermelho, lanternas, cerejeiras, portão torii), **Élfico** (madeira clara, folhas, vidro, luzes suaves, integrado à floresta) e **Sombrio/Gótico** (pedra escura, telhados pontudos, lanternas de alma). Se sobrar fôlego, um quarto (ex.: Anão, de pedra e forja).
- Cada estilo cobre TUDO o que os três atuais cobrem: todos os prédios procedurais em todos os níveis, praça, ruas, postes, muralha e portão por degrau (com o trabalho do estágio 1), estandarte/cor, guardas, texto, ícone na tela de fundação e na prévia. Enum salvo por id: só ACRESCENTE valores.
- Trocar o estilo de uma cidade existente ("Reforma"), com custo alto e obra gradual, se couber.
- Reinos NPC (Aldória, Sahra, Nordheim) não mudam.

---
# ONDA 6

Pedidos do Renan (2026-11-13, enquanto a onda 5 era testada), nas palavras dele:
1. *"deixe o up da cidade lento em dias, mas que seja legal! demore dias pra construir e upar as coisas, mas fazer que valha a pena! talvez ter ferreiros e outros tipos de comerciantes"*
2. *"começar a construir monumentos com buffs, templos e etc..."*

Tudo das ondas 1–5 está pronto e compilado (relatórios em `docs/v10r/`, os da cidade: `w1_city_build.md`, `w1_city_life.md`, `w3_city_plan.md`, `w4_city_magic.md`, `w5_city_tiers.md`, `w5_city_styles.md`). Valem as "Regras comuns" do começo deste arquivo. Mundos salvos continuam carregando: nada de renomear ids/chaves; obras em andamento em mundos antigos terminam sem quebrar. "Dia" aqui é o dia do Minecraft (20 min), leitura do coordenador — ele disse só "dias".
Lembretes do gosto dele: a cidade do jogador é a mecânica mais legal depois das magias; tela limpa (nada de HUD poluído); cidade nunca é destruída; subir de nível vale mais que item; configurações no menu do Minecraft.

## city_pace  (primeiro; área `feature/settlements/**`)
Palavra dele: *"deixe o up da cidade lento em dias, mas que seja legal! demore dias pra construir e upar as coisas, mas fazer que valha a pena!"*
- **Construir e melhorar leva DIAS.** Cada obra tem duração em dias conforme tamanho e nível (ordem de grandeza: casa pequena ~1 dia; prédios comuns 2–3; prédios-chave e muralhas 3–5; melhorias de nível custam mais a cada nível; grandes obras e o próprio aumento de degrau da cidade 5–10). Números num lugar só, fáceis de ajustar, e uma opção de servidor "ritmo da cidade" (multiplicador, padrão 1) no arquivo de config do módulo, com nome e dica no menu de config do Minecraft.
- **A cidade anda mesmo com o Senhor longe**: o progresso é por dias passados (contador de dias do mundo), com recuperação quando os chunks carregam — ninguém deve ter de ficar parado olhando a obra. Dormir conta como dia passado.
- **"Mas que seja legal"** — a espera tem de ser visível e gostosa de acompanhar: a obra cresce em ETAPAS ao longo dos dias (terreno/fundação → andaime e estrutura → paredes → telhado → acabamento), construtores trabalhando de verdade no canteiro, placa do canteiro com "dia X de Y"; um resumo curto ao amanhecer (uma linha no chat + um "Diário da cidade" no painel com o que andou, o que terminou e o que falta), sem poluir a tela.
- **O jogador pode ajudar e sentir que ajudou**: entregar materiais, trabalhar no canteiro, felicidade alta, festival e mais construtores (mais obras ao mesmo tempo, não obra instantânea) aceleram um pouco; a magia de obra da onda 4 (`/mahou city magic hasten`, construir com mana) continua existindo mas rende uma fração de dia e tem limite diário, para não apagar a espera.
- **"Fazer que valha a pena"** — toda obra terminada dá algo que se nota: inauguração curta (fogos/sino/anúncio, pulável), efeito claro no painel ("antes → depois"), e o planejador mostra ANTES de enfileirar "leva N dias" e "você ganha X". Cada nível de prédio muda o prédio e o serviço dele de forma visível.
- Requisitos de degrau da onda 5 continuam; ajuste o que ficar impossível com o novo ritmo. `/mahou city finish`, `/mahou city day` e os demais comandos de teste continuam funcionando; acrescente o que precisar para pular dias.
- No relatório: tabela de durações, as APIs que os estágios seguintes usam (duração de obra, gancho de "obra concluída", gancho de "novo dia") e o que NÃO foi testado.

## city_trades  (depois de city_pace, em paralelo com city_monuments; área `feature/settlements/**`)
Palavra dele: *"talvez ter ferreiros e outros tipos de comerciantes"*.
- **Comerciantes especializados ligados aos prédios**: Ferreiro (ferraria — armas, ferramentas, consertos, serviço de melhoria), e outros tipos, por exemplo: Alquimista/Curandeiro (casa do curandeiro — poções), Escriba/Bibliotecário (biblioteca — papel, pergaminhos em branco, páginas), Taverneiro (taverna — comida, boatos), Estribeiro (estábulo — itens de montaria), Joalheiro (oficina — gemas do `gear_plus`, por id opcional), Lenhador e Minerador (serraria/mina — matéria-prima), Mercador arcano (torre/pináculo de mana — itens de mana). Use os itens que já existem no mod por id; nada de inventar equipamento novo de fim de jogo (ele prefere subir de nível a ganhar item pronto): vendem utilidade, material e conveniência, com alguns itens raros travados por degrau.
- Cada um tem cara própria (roupa/textura da profissão, nome na cabeça), fica no prédio dele de dia, e o **estoque melhora com o nível do prédio e o degrau da cidade**, com reposição diária (use o gancho de "novo dia" do city_pace). Moeda e sistema de troca: os que o `MerchantNpc` já usa. O Senhor recebe uma pequena taxa das vendas no cofre.
- Todos os 7 estilos de cidade; NPCs nunca morrem de vez (cidade é sagrada). Painel: uma linha por comerciante ("quem a cidade tem / quem falta e qual prédio traz").
- Arquivos compartilhados com o agente city_monuments (que roda ao mesmo tempo): veja a regra no fim desta seção.

## city_monuments  (depois de city_pace, em paralelo com city_trades; área `feature/settlements/**`)
Palavra dele: *"começar a construir monumentos com buffs, templos e etc..."*.
- **Monumentos e templos são grandes obras de vários dias que dão bônus (buffs)** aos cidadãos. Já existem estátua, arco, templos elementais, Santuário da Luz, torre/pináculo de mana, maravilhas do Deserto e castelo do Reino: dê a CADA um um bônus com nome, claro e sentido (ex.: templo de um elemento → poder/XP de maestria daquele elemento; Santuário → regeneração; estátua → XP; arco → caravanas/viagem), e acrescente monumentos novos (pelo menos 4, ex.: Obelisco das Eras, Colosso do Guardião, Grande Farol, Jardim Suspenso) e, se der fôlego, uma maravilha própria para cada estilo que ainda não tem (Nórdico, Oriental, Élfico, Gótico, Anão).
- **Como o bônus funciona**: vale para os cidadãos dentro da cidade; e o jogador pode "receber a bênção" no monumento uma vez por dia para levar uma versão mais fraca por um tempo fora dela. Nada que quebre o equilíbrio de chefes: números modestos, somando com limite.
- **Monumentos têm níveis** (1–3): cada nível é outra obra de vários dias, muda o visual e fortalece o bônus. **Limite de monumentos ativos por degrau** (ex.: Vila 1, Burgo 2, Cidade 3, Capital 5), para a escolha importar; dá para trocar.
- Painel: lista dos bônus ativos da cidade, uma linha cada; planejador mostra o bônus e os dias antes de enfileirar. Sem ícones novos no HUD além dos efeitos normais do Minecraft.
- Use as durações e os ganchos do city_pace (obra concluída → inauguração; novo dia → bênção diária).

**Regra para os dois agentes paralelos (city_trades e city_monuments):** cada um cria os SEUS arquivos Java novos e o SEU gerador de textos (`tools/gen/settlements_w6_trades_lang.py` / `settlements_w6_monuments_lang.py`, que só ACRESCENTAM chaves ao JSON existente — nunca reescrevem o arquivo inteiro nem rodam os geradores antigos). Nos arquivos compartilhados (`SettlementPanel`, `Blueprints`, `CityEconomy`, `ProcBuildings`, `MerchantNpc`...) faça edições pequenas e pontuais, relendo o trecho logo antes de editar; nunca reescreva o arquivo inteiro. Se `check-tree` falhar só em arquivo do outro agente, espere um minuto e tente de novo.


# ONDA 7

Pedidos do Renan (2026-11-13, depois de receber a onda 6), nas palavras dele:
1. *"Quero criar um tipo de cidade que é SkyLand Island pra cidade que o player pode criar..."*
2. *"quero poder parar as construções no meio..."*

Tudo das ondas 1–6 está pronto e compilado (relatórios em `docs/v10r/`; os da cidade: `w1_city_build.md`, `w1_city_life.md`, `w3_city_plan.md`, `w4_city_magic.md`, `w5_city_tiers.md`, `w5_city_styles.md`, `w6_city_pace.md`, `w6_city_trades.md`, `w6_city_monuments.md`). Valem as "Regras comuns" do começo deste arquivo. Mundos salvos continuam carregando: nada de renomear ids/chaves; campos novos são opcionais com padrão; cidades no chão continuam EXATAMENTE como estão.
Lembretes do gosto dele: a cidade do jogador é a mecânica mais legal depois das magias; tela limpa; cidade nunca é destruída; subir de nível vale mais que item; a espera em dias tem de ser gostosa de acompanhar.

Leitura do coordenador (ele disse só a frase acima; o que não está nela é escolha nossa e vai avisado a ele):
- "SkyLand Island" = a cidade do jogador fundada sobre uma **ilha flutuante no céu**, criada pelo mod na hora da fundação. É um **tipo de terreno** escolhido ao fundar ("No chão" / "Ilha Celeste (SkyLand)"), que combina com QUALQUER um dos 7 estilos (Reino, Deserto, Nórdico, Oriental, Élfico, Gótico, Anão). Id interno: `skyland`. Nome em PT: "Ilha Celeste (SkyLand)"; EN: "Sky Island (SkyLand)".
- "parar as construções no meio" = pausar e retomar qualquer obra em andamento, e também poder desistir dela (cancelar no meio).

## sky_island  (primeiro e sozinho; área `feature/settlements/**`)
Palavra dele: *"Quero criar um tipo de cidade que é SkyLand Island pra cidade que o player pode criar..."*
- **Dado da cidade**: campo novo opcional na `Settlement` (ex.: `sky`), salvo e enviado ao cliente; `SkyCity` (classe nova) concentra as regras: `isSky(s)`, altura, pegada da ilha, "a terra deste lote já existe?", ponto do cais. Cidades antigas = chão.
- **Fundação**: o jogador usa a Carta de Fundação no chão como hoje; se escolher Ilha Celeste, o Coração nasce no céu acima do ponto (altura: algo como `clamp(terreno mais alto do território + 64, 160, 208)`, de modo que o prédio mais alto caiba abaixo do limite de construção e a cidade fique na altura das nuvens). Mesmas regras de distância de outras cidades/lugares, conferindo a caixa NO CÉU (as Ilhas Voadoras das torres, `tower_sky_island_*`, e a Torre Flutuante não podem ser atravessadas). O pacote `Found` ganha o campo do tipo (compatível com o cliente desta mesma versão); a tela/prévia bonitas são do estágio 2 — aqui basta funcionar de ponta a ponta por comando de teste (`/mahou settlements found sky [estilo]` ou equivalente) e o servidor aceitar o campo.
- **Custo de fundar no céu**: além da Carta, um item novo **Núcleo Celeste** (`sky_core`), consumido na fundação: receita de meio de jogo com itens que já existem (vanilla "do céu" — membrana de phantom, penas, ametista — mais um item de mana do mod; escolha lendo o que há). Textura própria feita por um gerador pequeno SEU (nunca rodar `tools/gen/icons.py` inteiro). Modelo, receita, nome e dica do item.
- **A ilha**: gerada por código, bonita e orgânica (nada de disco perfeito): topo plano na altura da praça cobrindo o território do raio atual + margem (os pontos de chegada na frente dos portões e a faixa fora da muralha têm de ficar em terra firme), borda irregular, barriga em cone invertido com ruído e pontas de pedra, camadas grama/terra/pedra com a paleta do ESTILO (ex.: arenito no Deserto, ardósia no Gótico/Anão, pedra musgosa no Élfico, pedra+neve no Nórdico). **Gerada aos poucos** (orçamento de blocos por tick, cursor salvo, só em chunk carregado, retoma depois de reiniciar), sem travar o servidor: primeiro o miolo da praça (rápido, para o fundador já pisar), depois crescendo para fora em anéis — é para ser um espetáculo curto ("a terra se forma"), com som/partículas discretos. O fundador é levado para a praça quando o miolo existir.
- **Obras só onde há terra**: lote cujo chão ainda não foi gerado espera (com motivo claro no painel/planejador); `CityTerrain`/`ConstructionEngine` nunca podem "aterrar" o vazio com pilares até o chão. Numa ilha o terreno é plano: aproveite (nada de escadas/saia desnecessárias).
- **A ilha cresce com a cidade**: quando o raio aumenta (`CityPlanning.expand`, subida de degrau), a ilha ganha o anel novo de terra, também aos poucos e bonito; os terrenos novos liberam conforme a terra aparece. Vale para todos os raios até `CityLayout.MAX_RADIUS` — pense no custo (a ilha máxima tem ~480 blocos de lado: barriga mais fina nos anéis de fora, orçamento por tick, nada de recalcular tudo).
- **Sombra**: uma ilha grande escurece o chão embaixo dela. Evite que isso vire uma fábrica de monstros de dia: sem spawn natural de monstros hostis na superfície sob a pegada da ilha (teste barato; cavernas continuam normais).
- **Nunca destruir**: a ilha só substitui ar/fluido/plantas soltas; se o jogador construiu algo no caminho, contorna. Cidade dissolvida/abandonada: a ilha fica.
- Comandos de teste em `CityDebugCommands` (fundar no céu, ver estado da geração, terminar a ilha já, forçar o crescimento). `/mahou city finish`, `tier`, `free`, `style` etc. têm de funcionar numa cidade do céu.
- No relatório, descreva com precisão as APIs que os agentes do estágio 2 vão usar (`SkyCity.*`: isSky, altura do topo, pegada/“tem terra em (x,z)?”, ponto(s) da borda para o cais, estado da geração) e o que NÃO foi testado.

## sky_access  (estágio 2, em paralelo com sky_ui e city_pause; área `feature/settlements/**`)
A cidade do céu tem de ser vivível: subir, descer e todos os sistemas da cidade funcionando lá em cima.
- **Subir e descer**: uma **Corrente Ascendente** própria da cidade (mesma sensação da das Ilhas Voadoras — leia `feature/towers/SkySites.java`, sem editar aquele módulo): um **Cais Celeste** pequeno na borda da ilha (acompanha a borda quando a ilha cresce; blocos do cais rastreados para remover/recolocar sem lixo) e um **marco no chão** embaixo dele (círculo de pedra discreto, protegido, com brilho de partículas só para quem está perto). Entrou na coluna → sobe e pousa no cais; agachado → desce devagar. Obedece à política de portões da cidade (portões fechados: só cidadãos sobem). A viagem rápida entre cidades que já existe continua funcionando.
- **Cair não mata**: quem despenca da ilha ganha Queda Lenta (como nas Ilhas Voadoras). Traço da cidade do céu (uma linha no painel): cidadãos não levam dano de queda dentro do território.
- **NPCs**: moradores, construtores, guardas, comerciantes, mercadores e campeões nunca se perdem — quem cair/ficar abaixo da ilha é devolvido para casa no próximo sinal de vida; pontos de nascimento/chegada sempre em terra da ilha (`CityLayout.arrivalPoint`, `SettlementService.spawnNpc`, `CityTrades`, visitantes).
- **Invasões** (`CityRaids`): os atacantes chegam "pelo ar" — nascem em terra da ilha, na faixa fora da muralha, com um efeito curto (nuvem/fumaça); nada de onda nascendo no chão 70 blocos abaixo. **Guerra/conquista** (`WarService`, `Conquest`, milícia) e **caravanas** (`CityCaravans`) conferidas e ajustadas do mesmo jeito (o que for físico chega/sai pelo cais).
- Tudo com os 7 estilos; nada muda nas cidades do chão.

## sky_ui  (estágio 2, em paralelo com sky_access e city_pause; área `feature/settlements/**`)
- **Tela de fundação** (`client/FoundingScreen`): escolha clara "No chão" / "Ilha Celeste (SkyLand)", limpa. Sem o Núcleo Celeste a opção aparece apagada com a dica de como conseguir.
- **Prévia** (`client/CityPlacer`, holograma da onda 4): com Ilha Celeste escolhida, a prévia mostra onde a ilha vai ficar (contorno no céu na altura certa + feixe do chão até lá), ainda podendo mover/girar; o servidor continua sendo quem decide a altura.
- **Painel**: uma linha no Geral ("Ilha Celeste · altitude N"), o traço da cidade do céu, o estado "a terra está se formando: N%" enquanto gera, e o motivo "aguardando a terra" nos lotes/obras que esperam.
- **A beleza da ilha** (classe nova de enfeite, por cima do gerador do estágio 1, sem reescrevê-lo): cachoeiras caindo da borda (água que não inunda nada: some no ar ou cai num laguinho próprio), raízes/cipós e cristais luminosos na barriga, 2–4 ilhotas-satélite decorativas com árvore, névoa de nuvem na borda (FX leve, por `client/fx/FxQuality`). Tudo rastreado para refazer quando a ilha cresce; por estilo quando fizer sentido (ex.: neve no Nórdico, cerejeira no Oriental).
- **Textos**: gerador próprio `tools/gen/settlements_w7_sky_lang.py` (só acrescenta chaves, PT-BR e en_us) para tela, painel, dicas (`Hints.show`: primeira vez que funda no céu, primeira vez na corrente, primeira queda), anúncio de fundação ("...fundou X, uma cidade nas nuvens"). O que for de outros módulos (diário/guia) vai em PEDIDOS.

## city_pause  (estágio 2, em paralelo com sky_access e sky_ui; área `feature/settlements/**`)
Palavra dele: *"quero poder parar as construções no meio..."*
- **Pausar e retomar qualquer obra em andamento** (construção, melhoria, reforma, monumento, muralha, subida de degrau): botão na aba Obras (por obra) e um "Pausar tudo / Retomar tudo". Obra pausada não avança nos dias (`CityPace`, inclusive na recuperação de dias com a cidade descarregada), não consome materiais/ajuda, libera os construtores para as outras obras, e o canteiro fica como está (andaimes e etapa atual), com a placa mostrando "Pausada — dia X de Y". O Diário da cidade registra pausa e retomada. Estado salvo (reinicia e continua pausada).
- **Desistir no meio (cancelar)**: hoje só dá para cancelar antes de começar. Passa a dar em qualquer etapa, com confirmação: o canteiro é desmontado com segurança (construção nova: o lote volta a ficar livre e limpo; melhoria/reforma: o prédio volta ao estado anterior — nunca deixar prédio pela metade quebrado nem apagar bloco do jogador) e devolve parte do custo proporcional ao que faltava. Onde desfazer não for seguro (ex.: subida de degrau já aplicada em parte, prédio mudado de lugar), só pausar: diga o motivo no botão.
- Regras de quem pode: as mesmas de planejar obras (Senhor/conselho). Sem poluir: nada de HUD novo; um ícone/tonalidade discreta na linha da obra pausada.
- Comandos de teste (`/mahou city pause [n|all]`, `/mahou city resume [n|all]`, `/mahou city cancel <n>`); textos pelo gerador próprio `tools/gen/settlements_w7_pause_lang.py`.

**Regra para os três agentes paralelos do estágio 2:** cada um cria os SEUS arquivos Java novos e o SEU gerador de textos (só ACRESCENTA chaves ao JSON existente; nunca rodar os geradores antigos; não rodar dois geradores ao mesmo tempo — se o JSON mudou entre ler e gravar, releia e grave de novo). Nos arquivos compartilhados (`SettlementPanel`, `SettlementService`, `ConstructionEngine`, `CityPace`, `SettlementView`, `CityDebugCommands`...) faça edições pequenas e pontuais com a ferramenta de edição, relendo o trecho logo antes; nunca reescreva o arquivo inteiro. Se `check-tree` falhar só em arquivo de outro agente, espere um minuto e tente de novo.


# ONDA 8

Pedido do Renan (2026-11-13, com a onda 7 em andamento), nas palavras dele: *"e uma outra cidade em cima da agua!"* (logo depois de pedir a cidade "SkyLand Island").

Roda DEPOIS da onda 7 inteira (a Ilha Celeste já existe: leia `docs/v10r/w7_*.md` e o código de `SkyCity*` antes de tudo — a cidade da água é o terceiro tipo de terreno e deve seguir o MESMO desenho: mesmo campo/enum de tipo de terreno na `Settlement` (se a onda 7 guardou só um booleano `sky`, generalize sem quebrar mundos salvos: o booleano antigo continua sendo lido), mesma escolha na tela de fundação, mesmas regras de "obra só onde há chão"). Valem as "Regras comuns" e os lembretes das ondas 6 e 7. Cidades no chão e no céu continuam exatamente como estão.

Leitura do coordenador (ele disse só a frase acima): um **tipo de terreno "Sobre a Água"** (id `water`; PT "Cidade das Águas", EN "Water City") escolhido ao fundar, que combina com QUALQUER um dos 7 estilos: a cidade fica **em cima da água** — mar, lago grande ou rio largo —, sobre plataformas e passarelas apoiadas em estacas, com canais entre os quarteirões.

## water_city  (área `feature/settlements/**`)
Palavra dele: *"e uma outra cidade em cima da agua!"*
- **Fundação**: o jogador usa a Carta de Fundação olhando para a água (de um barco, da margem ou nadando); a prévia (`CityPlacer`) aceita a superfície da água como chão quando o tipo "Sobre a Água" está escolhido e mostra o contorno ali. Exige água de verdade sob boa parte do território inicial (ex.: ≥ 60% das colunas do raio inicial com água, profundidade ≥ 2) — senão explica o motivo. Sem item extra: o custo é achar o lugar. O Coração fica na plataforma da praça, 1–2 blocos acima do nível da água. Mesmas regras de distância de outras cidades/lugares; nada de fundar em cima de monumento oceânico ou de estrutura do mod.
- **O chão da cidade**: gerado por código e aos poucos (mesmo motor/orçamento da Ilha Celeste — reaproveite, não duplique): deque da praça primeiro, depois os quarteirões: cada lote ganha a SUA plataforma na altura da praça (piso do estilo: tábuas/pedra/arenito/bambu... com borda e estacas descendo até o fundo, limitadas a uma profundidade razoável — em mar fundo as estacas terminam em flutuadores/pedras de fundação, sem pilar de 40 blocos), ligadas por **passarelas** no lugar das ruas; entre os quarteirões ficam **canais** de água navegáveis de barco (pontes baixas onde a passarela cruza o canal principal). Onde o território pega terra firme (margem), a cidade usa o chão normal e a transição é uma rampa/escada curta. Postes do degrau (`TierLook`) nas passarelas; a reforma de degrau (`CityRenewal`/`TierLook`) troca o piso das passarelas como troca o das ruas.
- **Obras só onde há plataforma**; a plataforma do lote faz parte da obra do prédio (etapa de fundação visível: estacas → deque → prédio). `CityTerrain`/`ConstructionEngine` nunca aterram o mar (nada de encher a água de pedra) nem drenam água fora dos lotes; nada de escada/saia sem sentido. Muralhas por degrau (`TierWalls`): viram quebra-mar/paliçada sobre estacas no mesmo traçado, com os portões como portões de água + cais.
- **Cresce com a cidade**: raio maior → novos quarteirões e passarelas, do mesmo jeito progressivo.
- **Vida na água**: Cais com 2–3 barcos na borda (repostos devagar se sumirem); NPCs nunca se perdem (quem cair na água volta para a passarela mais próxima no próximo sinal de vida; pontos de nascimento/chegada sempre em piso firme); invasões chegam "pelo mar" (nascem na borda das plataformas, com respingos; os afogados podem entrar no elenco); caravanas, guerra/conquista e comerciantes conferidos como na cidade do céu. Traço da cidade (uma linha no painel): **Porto** — pesca abastece a cidade (bônus modesto de comida por dia, maior com o degrau) e cidadãos respiram mais tempo debaixo d'água dentro do território.
- **Beleza**: lanternas na beira dos canais, redes/barris/caixotes nos cais, vitórias-régias e algas nos cantos calmos, uma fonte/chafariz que aproveita a água na praça; por estilo quando fizer sentido (ex.: torii na água no Oriental, drakkar ancorado no Nórdico). Tudo rastreado para refazer sem lixo.
- **Tela e textos**: a escolha de terreno da tela de fundação ganha a terceira opção; painel com "Cidade das Águas"; dicas (`Hints.show`) da primeira fundação na água e do primeiro passeio de barco pelos canais; anúncio de fundação ("...fundou X, uma cidade sobre as águas"). Gerador próprio `tools/gen/settlements_w8_water_lang.py` (só acrescenta chaves).
- Comandos de teste (fundar na água sem a tela, ver/terminar a geração). `/mahou city finish`, `tier`, `free`, `style`, `pause/resume/cancel` têm de funcionar numa cidade da água. Os 7 estilos.
- No relatório: como testar, o que NÃO foi feito/testado, PEDIDOS.


## city_looks  (onda 8, em paralelo com water_city; área `feature/settlements/**`)
Palavras dele (2026-11-13, jogando a onda 6 numa Vila gótica com os prédios no nível 2):
- *"tem que ter mudanças mais significativas visuais do lvl 1 pro 2... até o 5... tem que ser bem diferente..."* — perguntado, respondeu **"Os dois"**: os níveis dos PRÉDIOS (1–5) e os degraus da CIDADE (Acampamento → Capital).
- *"RENOVATION tem que renovar o muro tbm..."* — perguntado, respondeu **"Troquei o estilo"**: fez a Reforma de estilo da cidade e o muro continuou no estilo antigo.

- **Prédios: cada nível tem de ser outro prédio à primeira vista** (`build/ProcBuildings`, moldes e `Blueprints`): do 1 ao 5 mudam a silhueta (área ocupada do lote, andares/altura, torre/chaminé/anexos), os materiais (madeira crua → madeira+pedra → pedra lavrada → nobre do estilo), o telhado, janelas/varandas/vitrais, o quintal (cerca → muro baixo → jardim/pátio) e os detalhes de função (ex.: fazenda nível 1 = canteiro e espantalho; nível 5 = celeiro grande, silo, moinho; ferraria nível 1 = bancada sob telheiro; nível 5 = forja de dois andares com chaminé dupla e pátio de armas). Vale para TODOS os tipos de prédio do catálogo e os 7 estilos (motor procedural com parâmetros por nível e por estilo — não 5×N×7 moldes escritos à mão). Monumentos (níveis 1–3) também: cada nível visivelmente maior/mais rico, corrigindo os níveis visuais 4–5 inalcançáveis dos monumentos antigos e a Árvore-Mãe nível 1 pequena demais.
  - Prédios que já existem em mundos salvos não mudam sozinhos (nada de mexer no que está de pé sem obra): ganham o visual novo na próxima melhoria/reforma; ofereça no planejador um "Refazer no visual novo" barato para quem quiser. Nunca apagar bloco do jogador nem baú com itens (regras atuais de melhoria).
  - O planejador/catálogo mostra uma linha do que muda no visual de cada nível ("nível 3: segundo andar e telhado de ardósia").
- **Cidade: cada degrau muda a cidade inteira de forma óbvia** (`build/TierLook`, `CityRenewal`, `build/TierWalls`): além das ruas/postes/praça/fachadas de hoje, a diferença entre degraus tem de ser "bem diferente" — largura e desenho das ruas (trilha de terra → cascalho com meio-fio → pedra com calçada → avenida com canteiro central e árvores → pavimento nobre com mosaico na praça), praça (fogueira e tendas no Acampamento → poço/mercado → fonte → estátua e arcadas → grande monumento do coração), mobília urbana (bancos, barris, carroças, canteiros, fontes pequenas, estandartes grandes, arcos de entrada), iluminação, e a muralha por degrau (paliçada → pedra baixa → pedra com ameias e torres → fortificada → real, com portões cada vez mais imponentes). Tudo rastreado como hoje para refazer sem lixo; por estilo.
- **A Reforma de estilo refaz o muro também, de verdade**: reproduza o erro (muralha pronta + `/mahou city style <id>`; e muralha AINDA EM OBRA no momento da reforma) e conserte: muralha pronta → obra de troca que substitui todas as peças pelo estilo novo (sem pular peça em silêncio: se a peça antiga não bate com o mundo, reconstrua aquela peça do zero no lugar certo em vez de deixá-la velha); muralha em obra → as peças que faltam já saem no estilo novo e as já erguidas entram na troca. O mesmo vale para a reforma de degrau (subiu de degrau → muro do grau novo). O painel mostra a obra "Reforma da muralha" com dias e etapa como as outras, e ela pode ser pausada/cancelada pelas regras da onda 7.
- Comandos de teste para ver tudo sem esperar (ex.: `/mahou city showcase <prédio>` erguendo os 5 níveis lado a lado num terreno de teste; os de degrau e estilo que já existem). No relatório: como testar, o que não foi feito/testado, PEDIDOS.

**Regra para os dois agentes paralelos da onda 8 (water_city e city_looks):** a mesma das ondas 6 e 7 — arquivos Java novos próprios, gerador de textos próprio que só ACRESCENTA chaves (`settlements_w8_water_lang.py` / `settlements_w8_looks_lang.py`), edições pequenas e pontuais nos arquivos compartilhados (`TierWalls`, `TierLook`, `ConstructionEngine`, `CityRenewal`, `SettlementPanel`...), relendo o trecho logo antes; nunca reescrever arquivo inteiro de outro. `city_looks` é dono de `ProcBuildings`/`TierLook`/`TierWalls` (o water_city pede ali só ganchos pequenos: piso de passarela, quebra-mar).

**Consertos do coordenador já no código (feitos durante a onda 7; não desfazer):**
- "ao mandar construir com a altura -1, ele não cava" → o seletor de altura do painel vale também para Construir (`plan_build` 4º campo, `plan_free` 6º campo, `CityPlanning.build(..., offset)`).
- "in peace mode eu não deveria poder bater nos NPC" → `core/ux/PeaceMode` protege NPCs (tag `isekai_mahou:peace_protected`).
- "acho que as decorações do festival tão ficando após o festival" → causa: o dia das cidades era o relógio do mundo; um `/time set` voltava a contagem (no mundo dele: último dia contado 6, relógio no dia 2) e travava conta do dia, fim de festival e enfeites. Agora `SettlementService.day` é um calendário próprio que nunca volta (`SettlementData.day`).
- "pra virar town preciso do 7x7 mas pra virar 7X7 preciso ter 12 construções" → `CityGrowth.needBuildings` nunca pede mais que 3/4 dos terrenos que a cidade pode usar no degrau atual (Vila: 6 de 8).
