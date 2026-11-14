# v1.1 — Agente J: King Crimson e Bites the Dust (legends_h)

Os dois continuam sendo **efeitos comuns do Grimório**, com os mesmos ids (`king_crimson`, `bites_the_dust`), tier 4, Círculo 5, custo 30 / 28.
Nenhum id novo. Arquivos: `feature/legends_h/jojo/TimeSkip.java`, `jojo/Rewind.java`, `glyph/JojoGlyphs.java`,
`client/LhScreenFx.java`, `src/main/lang/legends_h/{pt_br,en_us}.json`.

## King Crimson em você (Si Mesmo)
- Uma **cutscene curta só sua** (`"king_crimson"`, 1,5 s, controles travados). O tempo fora do tempo começa a contar **depois** dela.
- **4 s** fora do tempo (Estender +, até **10 s**). O mundo continua andando. Você fica invisível, ninguém te mira, tudo te atravessa e você também não fere ninguém (como antes).
- Todo mundo a até **16 blocos** (Expandir +, de 8 a 32; até **24** alvos; chefes nunca; quem você monta ou quem te monta, nunca) ganha um **destino**: um ponto seguro aleatório a **2–6 blocos** (chão firme, espaço para o corpo, sem água/lava/fogo/cacto/teia/neve fofa, dentro da borda do mundo, mesma arena, e nas primeiras tentativas sem parede no caminho).
- Um **X vermelho** no chão (e uma linha fraca da pessoa até ele) aparece **só para você**, atualizado a cada 4 ticks. As vítimas não veem nada.
- Os inimigos ficam com o tempo apagado **em silêncio** (sem véu e sem aviso): andam normalmente, mas não miram em você e os ataques deles não acontecem.
- Quando o tempo volta: todos aparecem **de uma vez** no X deles (mesma rotação). Antes de mover, o ponto é conferido de novo (ainda seguro, mesma arena, a menos de 64 blocos). A mão também muda:
  - **jogadores**: outro slot da barra (no servidor e com pacote para o cliente) **e**, se tiverem outro loadout de magias com algo, outro loadout (`MagicApi.selectLoadout`);
  - **criaturas**: trocam a mão principal pela secundária e perdem o alvo.
  - Os jogadores atingidos recebem o corte rápido de tela (`SKIP`) e uma linha curta na barra de ação ("...o tempo pulou?").
- Você reaparece com o clarão de sempre, o **1º golpe ×1,5** (3 s) e o **Epitáfio por 3 s**. Nenhum bloco é alterado.
- Se a vítima deslogar, morrer ou trocar de dimensão, ela sai da lista. Se **você** morrer ou deslogar, ninguém é movido.
- O modo "num inimigo" não mudou (o tempo dele some por 2 s).

**Mudança em relação ao pedido:** a cutscene vai **só para quem conjura** (`Cutscenes.play`), e não `playNear` para todos.
O dono pediu que as vítimas "não saibam", e uma câmera girando nelas entregaria o golpe. Se quiser para todos, é uma linha em `TimeSkip.crimson`.

## Bites the Dust
- **Plantar**: em uma criatura, no **bloco** atingido (agora o glifo também pega blocos) ou em você (Si Mesmo). Pavio **T = 8 s** (Estender +, de 3 a **20 s**). Um marcador rosa fica no alvo e a barra de ação mostra a contagem.
- **Foto**: todo mundo a até **16 blocos** da bomba (Expandir +, de 8 a 32; até **24**), e você sempre: posição, rotação, dimensão, arena e vida. Para você, também fome, saturação e mana.
- **A bomba explode** (fim do pavio):
  - cutscene `"bites_the_dust"` para quem está a 32 blocos (o dono trava);
  - tela rosa de rebobinar (sépia, relógio ao contrário e, agora, faixas de "fita voltando") para cada jogador da foto;
  - todos voltam para onde estavam, com um rastro rosa (mesma dimensão e mesma arena; chefes não se movem);
  - **você volta exatamente para a vida da foto** (mesmo que tenha curado depois), e fome/saturação e mana também voltam (a mana só até 30% da máxima, como antes);
  - aliados recuperam a vida até a da foto; inimigos só voltam de lugar.
- **2ª explosão**: **T segundos depois** (o mesmo T, então Estender aumenta as duas esperas), o alvo explode:
  - **10 × poder** nele (chefes levam normal) e **3 × poder** nos inimigos a até **3 blocos** (Expandir +, de 2 a 8);
  - nunca fere você nem aliados e nunca quebra blocos; em **zona segura (cidade) não fere ninguém**, igual à Killer Queen;
  - bomba em você: explode no **inimigo da foto mais perto** de você (se não houver nenhum, não tem 2ª explosão);
  - bomba num bloco: explode no ponto (o inimigo a até 1,5 bloco leva o golpe cheio).
- **Salvação**: se você ia **morrer** com o pavio aceso, o tempo volta na hora (1 vez a cada 2 min), e a 2ª explosão continua valendo.
- Cada conjurador tem **uma bomba por vez** (a nova substitui a velha). Se o dono deslogar ou morrer, a bomba some.
- Mudou: inimigos não ficam mais atordoados ao voltar, e aliados marcados não têm mais a "salvação da morte" (agora ela é só de quem conjura).

## Como testar (em criativo ou sobrevivência, com OP)
1. Dê os glifos para você: `/mahou glyphs grant legends_h`.
2. No Grimório, monte:
   - `Si Mesmo + King Crimson`. Com `+ Estender` dura 6 s; com `+ Expandir`, pega mais longe.
   - `Projétil + Bites the Dust` (acerta uma criatura ou um bloco) e `Si Mesmo + Bites the Dust`.
3. **King Crimson**: perto de zumbis ou aldeões (e de um segundo jogador, se possível):
   - conjure, veja a cena curta e confira os X vermelhos;
   - ande até um X e espere o tempo voltar;
   - todos devem aparecer nos X e o outro jogador deve trocar de slot da barra.
4. **Bites the Dust**:
   - acerte um zumbi, ande, tome dano e espere 8 s: cena, todos voltam, sua vida volta;
   - 8 s depois, o zumbi explode;
   - teste também ficar com pouca vida e levar um golpe fatal com o pavio aceso.

Não existe uma árvore de comandos de teste no legends_h (só `grant`), então não criei comandos novos.

## O que NÃO foi verificado (não dá para abrir o jogo daqui)
- Compilação: `bash tools/check-tree.sh j11` compilou **OK** (a árvore inteira, 3196 arquivos).
- **Nada foi testado no jogo**:
  - se o X aparece bem (tamanho/cor) e se o número de partículas pesa com 24 alvos;
  - se a busca de lugar seguro funciona bem em cavernas, florestas densas e telhados;
  - se a troca de loadout pelo servidor é sincronizada com o HUD do cliente (usei a mesma chamada que a rede usa);
  - se o teleporte de jogador no fim do pulo fica suave (teleporte simples, sem interpolação);
  - como as câmeras `"king_crimson"`/`"bites_the_dust"` do agente C ficam (se elas não existirem, cai na órbita padrão);
  - se a trava de controle da cutscene da Bites the Dust (2,5 s) atrapalha numa luta. Se atrapalhar, troque o último argumento de `playNear` para `null` em `Rewind.rewind`.
- **PvP / deslogar**: se a vítima deslogar durante o pulo, ela simplesmente **não é movida** (a entrada é descartada). Não existe punição ao voltar, porque guardar isso no disco ficou fora do escopo.
