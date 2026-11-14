# v1.1 — Correções de skills, transformações como EFEITOS, cutscenes

Pedido do dono (2026-11-13, palavras dele):

> **KC**: Apaga o tempo ao redor, usado em self, vc fica invisivel, o tempo continua a correr. Mas as pessoas, mobs etc vão
> para posicões aleatorias X em vermelho... Elas não sabem disso, apenas o jogador de King Crimson sabe onde elas vão
> parar... mas é sempre perto. E vão mudar o item que estão na mão ou roda no slot de magia... Ao voltar, o player vai ter
> se posicionado melhor e pode fazer algo contra um player sem posicionamento entende?
>
> **Bites**: Você coloca uma bomba em 8 segundo em algo ou alguem... Isso salva a posição de todos que estiverem perto.
> Após os 8 segundos, a bomba explode, fazendo uma cutscene e todos voltando pros locais de origem... Vc volta para a
> vida que estava. Após 8 segundos que voltar, o alvo do voltar explode tomando dano (mesmo tempo que demorou pra
> explodir, se usar aumentar o tempo, aumenta o tempo pra explodir as 2 vezes...)
>
> **Almight**: A transformação dura muito tempo, tem que durar uns 5 minutos. mas pra cada ataque que voce tomar, ele
> deve desviar automaticamente, aparecer atras do alvo e acertar ele com um golpe, dizendo algo como "eu vi tudo..."
> cada ataque reduz 30 segundos da transformação! Após um ataque desses, fica 5 segundos sem teleportar dos outros,
> mas ainda desviando automaticamente. (então vc fica ivulneravel, por 5 min ou 10 hits entende?)
>
> Essas coisas como ele, ultra instinto, Vão tudo pra transformação... pq não são como o Love train que é uma
> habilidade entende? vc usa e se transforma...
>
> melhore um pouco as cutscenes das skills ou coisa assim... algumas principalmente o G tem que ter cutscene que não
> só a camera girando... Principalmente as transformações...
>
> vamos mudar a transformação de seraph pra ser algum personagem anjo super poderoso...

Respostas dele às perguntas: 3 agentes; anjo = **Jibril (No Game No Life)**; sobre "transformações de classes bugadas":
**"A ideia não é bug, é saírem de formas e virarem efeitos pra forma 'transformação'"**.

Regras de gosto que valem para tudo (memória do projeto): poderes de anime = glifos comuns compostos no Grimório (não
magias lendárias); sem poluição de tela (nada de números flutuantes; avisos curtos); cidades são sagradas (nada que
quebre blocos em zona segura). Textos em PT-BR **e** EN (todo `src/main/lang/<modulo>/*.json` que já existir).

## Gancho comum (já criado pelo coordenador)

`core/api/Cutscenes.java`:
- `Cutscenes.transform(ServerPlayer who, Component title, Component subtitle, int rgb, String style, int ticks)`
- `Cutscenes.transform(Entity who, ...)` (mobs; ninguém trava)
- Tipo de rede: `"transform:<estilo>"`. Estilos: `default, holy, dark, time, speed, beast, spirit, fire, ice, storm, blood`.
- Duração recomendada 50–70 ticks. O dono pode pular (já existe tecla de pular cutscene).

**Agente T chama, agente C desenha.** Ninguém mais muda a assinatura.

## Tabela de posse de arquivos

| Agente | Pode editar | NÃO edita |
|---|---|---|
| **J** (JoJo) | `feature/legends_h/**`, `src/main/lang/legends_h/*` | o resto |
| **T** (Transformações) | `feature/class_quincy/**`, `feature/class_shinigami/**`, `feature/class_hollow/**`, `feature/class_enchanter/**`, `feature/legends_f/**` (Monarca, se for transformação), `feature/character/**`, `feature/transform_laws/**`, `feature/ultimates/glyph/TranscendForm.java`/`ColossusForm.java` (só se forem transformações), langs desses módulos, `glyph/SpellParser.java`/carregamento de receitas **só** para a migração de receitas antigas | `feature/cinematic/**`, `core/api/Cutscenes.java`, `glyph/SpellCaster.java`, `feature/legends_h/**` |
| **C** (Cutscenes) | `feature/cinematic/**`, `core/api/Cutscenes.java` (só o método `ultimate` e novos helpers; não mudar `transform`), `glyph/SpellCaster.java` (só o trecho da cutscene de ultimate, linha ~285), `network/CorePayloads.java` só se for indispensável, `src/main/lang/cinematic/*` | os módulos de classe/raça (quem chama a cutscene de transformação é o T) |

Compilar: `bash tools/check-tree.sh <tag>` (tags: `j11`, `t11`, `c11`) — compila a árvore inteira com javac.
Nada de `gradlew` (o coordenador roda). Ninguém faz commit.

Relatórios: `docs/v11/<agente>.md` — o que mudou, ids novos, comandos de teste, o que NÃO foi testado.

## Sessão paralela (aberta em 2026-11-13 ~20:05 por outra conversa) — agentes K / S / D / SH / W

Pedido do dono nessa conversa: técnicas de anime nas classes (Kamui/dimensão do Valentine, Pressão Espiritual, Modo Titã,
Ponto Zero do Giorno, Orihime, Naoya, Accelerator + outras criativas), mutações mais cedo + glifos com evoluções tipo Tusk,
stands SÓ para Steel Ball, shader leve (Iris), Dark Self no nível 100 e "mundo vivo". Esses agentes NÃO editam os arquivos
do agente T (tabela acima). Posse:

| Agente | Pode editar |
|---|---|
| **K** (técnicas de classe) | `feature/legends_k/**`, `src/main/lang/legends_k/*` (+1 linha em Modules/ClientModules) |
| **S** (stands/evoluções/mutações) | `glyph/GlyphMastery.java`, `glyph/MasteryMutations.java`, `feature/legends_h/**` (depois do J), `feature/class_steelball/**`, `feature/stands/**`, `ui_grimoire/client/GlyphsTab|GlyphIcons` (só maestria) |
| **D** (Dark Self nível 100) | `feature/dark_self/**`, gancho mínimo em `core/Progression.java` |
| **SH** (shaders) | `build.gradle` (lista qolMods/deploy), `tools/pack/**` |
| **W** (mundo vivo) | biomas/ambience/world_events/structures_*/cities/settlements (só visual/vida)/creatures_*/worldgen JSON |

Relatórios: `docs/v11/{k_class_techniques,s_stands_evolutions,d_dark_self,sh_shaders,w_living_world}.md`.
Build/deploy: só quando T **e** esses agentes terminarem (quem coordenar por último roda `./gradlew build`).
