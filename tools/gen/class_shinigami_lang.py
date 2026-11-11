"""Traducoes do modulo class_shinigami (pt_br + en_us). Uso: py -I tools/gen/class_shinigami_lang.py

Homenagem a Bleach com nomes proprios (espiritos, comandos e Bankai inventados para o mod).
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
T = {}


def k(key, pt, en):
    if key in T:
        raise RuntimeError("chave duplicada: " + key)
    T[key] = (pt, en)


M = "message.isekai_mahou.shinigami."
S = "screen.isekai_mahou.shinigami."
D = "dialogue.isekai_mahou.shinigami."

# =====================================================================================
#  Itens
# =====================================================================================
k("item.isekai_mahou.zanpakuto", "Zanpakutō", "Zanpakutō")
for n, (pt, en) in enumerate([("Zanpakutō I: Asauchi", "Zanpakutō I: Asauchi"),
                              ("Zanpakutō II: Lâmina do Tenente", "Zanpakutō II: Lieutenant's Blade"),
                              ("Zanpakutō III: Lâmina do Capitão", "Zanpakutō III: Captain's Blade"),
                              ("Zanpakutō IV: Lâmina da Alma Desperta", "Zanpakutō IV: Blade of the Awakened Soul")], 1):
    k("item.isekai_mahou.zanpakuto_t%d" % n, pt, en)
k("item.isekai_mahou.shinigami_spirit_candy", "Doce Espiritual", "Spirit Candy")
k("item.isekai_mahou.zanpakuto_spirit_spawn_egg", "Ovo Gerador de Espírito da Zanpakutō", "Zanpakutō Spirit Spawn Egg")

OUTFIT = {
    "apprentice": dict(pt=("Faixa de Testa do Shihakushō", "Quimono Shihakushō", "Hakama do Shihakushō", "Tabi e Waraji do Shihakushō"),
                       en=("Shihakushō Headband", "Shihakushō Kimono", "Shihakushō Hakama", "Shihakushō Tabi and Waraji")),
    "adept": dict(pt=("Faixa do Tenente", "Quimono do Tenente", "Hakama do Tenente", "Sandálias do Tenente"),
                  en=("Lieutenant's Headband", "Lieutenant's Kimono", "Lieutenant's Hakama", "Lieutenant's Sandals")),
    "master": dict(pt=("Ornamento do Capitão", "Haori do Capitão", "Hakama do Capitão", "Sandálias do Capitão"),
                   en=("Captain's Hair Ornament", "Captain's Haori", "Captain's Hakama", "Captain's Sandals")),
    "archmage": dict(pt=("Faixa da Bankai", "Manto da Bankai", "Hakama da Bankai", "Botas da Bankai"),
                     en=("Bankai Headband", "Bankai Mantle", "Bankai Hakama", "Bankai Boots")),
}
for tier, names in OUTFIT.items():
    for i, piece in enumerate(("helmet", "chestplate", "leggings", "boots")):
        iid = ("shinigami_" + piece) if tier == "apprentice" else ("shinigami_%s_%s" % (tier, piece))
        k("item.isekai_mahou." + iid, names["pt"][i], names["en"][i])

# =====================================================================================
#  Entidades
# =====================================================================================
k("entity.isekai_mahou.zanpakuto_spirit", "Espírito da Zanpakutō", "Zanpakutō Spirit")
k("entity.isekai_mahou.shinigami_reishi_wave", "Onda de Reishi", "Reishi Wave")

# =====================================================================================
#  Glifos de origem (Shinigami)
# =====================================================================================
G = {
    "zanpakuto_zanjutsu": (
        "Zanjutsu", "Zanjutsu",
        "FORMA (Shinigami). A arte da espada: um corte em arco de 140° à sua frente (raio 3,2; +1 por Expandir; +0,8 "
        "segurando a zanpakutō) que aplica os efeitos em cada inimigo do arco, com um passo curto para a frente. Cada "
        "Dividir soma mais um corte em sequência (custo por cópia). Com a zanpakutō na mão sai 15% mais forte. Pode ser "
        "estágio 2+.",
        "FORM (Shinigami). The art of the sword: a 140° arc slash in front of you (radius 3.2; +1 per Expand; +0.8 while "
        "holding the zanpakutō) that applies the effects to every enemy in the arc, with a short step forward. Each Split "
        "adds one more follow-up slash (cost per copy). 15% stronger with the zanpakutō in hand. Can be stage 2+."),
    "shinigami_shunpo": (
        "Shunpo (Passo Instantâneo)", "Shunpo (Flash Step)",
        "FORMA (Shinigami). Você some e reaparece até 9 blocos à frente (+3 por Expandir), parando antes das paredes. "
        "Mirando um inimigo no alcance, reaparece ÀS COSTAS dele e aplica os efeitos nele. Sem efeitos, é só o "
        "deslocamento. Deixa uma imagem residual e zera a queda. Sempre 1 cópia (Dividir não multiplica).",
        "FORM (Shinigami). You vanish and reappear up to 9 blocks ahead (+3 per Expand), stopping before walls. Aiming at "
        "an enemy in range, you reappear BEHIND it and apply the effects to it. Without effects it is just the dash. "
        "Leaves an afterimage and resets fall damage. Always 1 copy (Split does not multiply it)."),
    "zanpakuto_moon_fang": (
        "Presa Lunar", "Moon Fang",
        "FORMA (Shinigami). A lâmina solta o Reiatsu numa meia-lua larga (3 blocos; +1 por Expandir) que voa ~28 blocos "
        "e corta TODOS os inimigos no caminho, cada um uma vez. Dividir solta mais meias-luas em leque (custo por cópia). "
        "Sai na cor do arquétipo; durante a Bankai fica negra com borda rubra e 50% mais larga. Pode ser estágio 2+.",
        "FORM (Shinigami). The blade releases its Reiatsu as a wide crescent (3 blocks; +1 per Expand) that flies ~28 "
        "blocks and cuts EVERY enemy in its path, each one once. Split fires more crescents in a fan (cost per copy). Takes "
        "the archetype's color; during Bankai it turns black with a crimson edge and 50% wider. Can be stage 2+."),
    "kido_hado_4": (
        "Hadō nº 4: Raio Branco", "Hadō #4: White Lightning",
        "FORMA (Shinigami, Kidō). Um relâmpago branco disparado da ponta do dedo, instantâneo, até 26 blocos (+4 por "
        "Acelerar). Acerta o primeiro inimigo da linha (+1 por Perfurar) e para nos blocos (Fantasma atravessa). Dividir "
        "dispara raios extras em leque (custo por cópia). Conta como Kidō. Pode ser estágio 2+.",
        "FORM (Shinigami, Kidō). A white lightning bolt fired from the fingertip, instant, up to 26 blocks (+4 per "
        "Accelerate). Hits the first enemy in line (+1 per Pierce) and stops at blocks (Phantom passes through). Split "
        "fires extra bolts in a fan (cost per copy). Counts as Kidō. Can be stage 2+."),
    "kido_hado_31": (
        "Hadō nº 31: Canhão Rubro", "Hadō #31: Crimson Cannon",
        "FORMA (Shinigami, Kidō). Uma esfera de Reiatsu vermelho que voa reta e explode no primeiro acerto, aplicando os "
        "efeitos em área (raio 2,5; Expandir aumenta). Não quebra blocos. Dividir dispara mais esferas em leque (custo por "
        "cópia). Conta como Kidō. Pode ser estágio 2+.",
        "FORM (Shinigami, Kidō). A sphere of red Reiatsu that flies straight and bursts on the first hit, applying the "
        "effects in an area (radius 2.5; Expand increases it). Does not break blocks. Split fires more spheres in a fan "
        "(cost per copy). Counts as Kidō. Can be stage 2+."),
    "kido_bakudo_61": (
        "Bakudō nº 61: Seis Grades de Luz", "Bakudō #61: Six Bars of Light",
        "EFEITO (Shinigami, Kidō). Seis lâminas de luz cravam na cintura do alvo: Enraizado por 2,5 s e Atordoado por "
        "metade disso (Estender aumenta). Chefes ficam presos por 40% do tempo. Dano mínimo. Combina com qualquer forma "
        "(Raio Branco + Seis Grades = prender de longe).",
        "EFFECT (Shinigami, Kidō). Six blades of light pin the target at the waist: Rooted for 2.5 s and Stunned for half "
        "of that (Extend increases it). Bosses are held for 40% of the time. Minimal damage. Works with any form (White "
        "Lightning + Six Bars = bind from afar)."),
    "kido_bakudo_81": (
        "Bakudō nº 81: Muro do Vazio", "Bakudō #81: Void Wall",
        "FORMA (Shinigami, Kidō). Ergue uma parede de Reiatsu dourado 2,5 blocos à frente (5 de largura, +2 por Expandir; "
        "3,5 de altura) por 8 s (Estender aumenta). Desfaz projéteis inimigos, barra a passagem de inimigos e, se a magia "
        "tiver efeitos, aplica-os uma vez por segundo em quem encosta (custo como pulsos, de forma moderada). Funciona sem "
        "efeitos. Até 3 paredes por conjurador.",
        "FORM (Shinigami, Kidō). Raises a wall of golden Reiatsu 2.5 blocks ahead (5 wide, +2 per Expand; 3.5 tall) for "
        "8 s (Extend increases it). Dissolves enemy projectiles, blocks enemies from passing and, if the spell has effects, "
        "applies them once per second to whoever touches it (cost as pulses, moderated). Works without effects. Up to 3 "
        "walls per caster."),
    "zanpakuto_bankai": (
        "Bankai", "Bankai",
        "FORMA G (Shinigami, ultimate). A liberação final da zanpakutō por 20 s (Estender aumenta): Força II, Resistência "
        "I, Velocidade II e Salto; golpes com a espada ×1,6 com a assinatura reforçada e, a cada segundo, um pulso num raio "
        "de 10 (+Expandir) aplica os efeitos da magia e o campo do arquétipo em todos os inimigos (Peso: gravidade "
        "esmagadora; Pétalas: tempestade de lâminas; Gelo: campo congelante; Chama: inferno; Pincel: domínio de tinta). "
        "Funciona sem efeitos. Custo: pulsos por segundo × duração, de forma moderada. Ao acabar, Fraqueza e Cansaço.",
        "G FORM (Shinigami, ultimate). The zanpakutō's final release for 20 s (Extend increases it): Strength II, "
        "Resistance I, Speed II and Jump; sword hits ×1.6 with an empowered signature and, every second, a pulse in a "
        "radius of 10 (+Expand) applies the spell's effects and the archetype's field to every enemy (Weight: crushing "
        "gravity; Petals: storm of blades; Frost: freezing field; Flame: inferno; Brush: ink domain). Works without "
        "effects. Cost: pulses per second × duration, moderated. When it ends: Weakness and Fatigue."),
    "shikai_weight": (
        "Shikai: Peso", "Shikai: Weight",
        "EFEITO (Shinigami, arquétipo Peso). Dano leve e cada acerto DOBRA o peso do alvo (×2, ×4, ×8, ×16, ×32): "
        "lentidão crescente, cansaço, depois preso ao chão e Exposto. Amplificar sobe 2 de cada vez; o peso cai sozinho "
        "se você parar de acertar.",
        "EFFECT (Shinigami, Weight archetype). Light damage and every hit DOUBLES the target's weight (×2, ×4, ×8, ×16, "
        "×32): growing slowness, fatigue, then pinned to the ground and Exposed. Amplify raises it 2 at a time; the weight "
        "fades if you stop hitting."),
    "shikai_petals": (
        "Shikai: Pétalas de Lâmina", "Shikai: Blade Petals",
        "EFEITO (Shinigami, arquétipo Pétalas). Mil pétalas cortantes: dano e Sangrando acumulado (dano contínuo que "
        "dobra se o alvo se mexe). Amplificar abre feridas mais fundas; Estender faz sangrar por mais tempo.",
        "EFFECT (Shinigami, Petals archetype). A thousand cutting petals: damage and stacking Bleeding (damage over time "
        "that doubles while the target moves). Amplify opens deeper wounds; Extend makes it bleed longer."),
    "shikai_frost": (
        "Shikai: Gelo", "Shikai: Frost",
        "EFEITO (Shinigami, arquétipo Gelo). A dança do gelo: dano e Gelado acumulado; no quinto acúmulo o alvo congela. "
        "Estender alonga o frio.",
        "EFFECT (Shinigami, Frost archetype). The ice dance: damage and stacking Chilled; at the fifth stack the target "
        "freezes. Extend lengthens the cold."),
    "shikai_flame": (
        "Shikai: Chama Ancestral", "Shikai: Ancestral Flame",
        "EFEITO (Shinigami, arquétipo Chama). O fogo mais antigo: dano forte, Em Chamas acumulado e um fogo que custa a "
        "apagar. Amplificar acumula mais rápido.",
        "EFFECT (Shinigami, Flame archetype). The oldest fire: strong damage, stacking Burning and a fire that is hard to "
        "put out. Amplify stacks faster."),
    "shikai_white_brush": (
        "Shikai: Pincel Branco", "Shikai: White Brush",
        "EFEITO (Shinigami, arquétipo Pincel). A tinta \"apaga o nome\": Silenciado (sem magias nem habilidades), "
        "Fraqueza II e perde todos os efeitos bons; monstros esquecem quem atacavam. Depois que a tinta some, o alvo fica "
        "imune ao pincel por 8 s. Chefes resistem (1/3 do tempo).",
        "EFFECT (Shinigami, Brush archetype). The ink \"erases the name\": Silenced (no spells or abilities), Weakness II "
        "and loses every beneficial effect; monsters forget who they were attacking. After the ink fades the target is "
        "immune to the brush for 8 s. Bosses resist (1/3 of the time)."),
}
for gid, (npt, nen, dpt, den) in G.items():
    k("glyph.isekai_mahou." + gid, npt, nen)
    k("glyph.isekai_mahou." + gid + ".desc", dpt, den)

# =====================================================================================
#  Magias Lendárias
# =====================================================================================
k("legend.isekai_mahou.shinigami_black_moon", "Presa da Lua Negra", "Black Moon Fang")
k("legend.isekai_mahou.shinigami_double_kido", "Kidō Duplo: Grade e Canhão", "Double Kidō: Bars and Cannon")

# =====================================================================================
#  Arquétipos, espíritos, ranks e requisitos
# =====================================================================================
A = "shinigami.isekai_mahou.archetype."
ARCH = {
    "weight": dict(
        name=("Peso", "Weight"),
        desc=("Uma lâmina paciente: cada corte deixa o oponente mais pesado, até ele não conseguir mais ficar de pé.",
              "A patient blade: every cut makes the opponent heavier, until they can no longer stand."),
        shikai=("Cada corte DOBRA o peso do alvo (×2 → ×32): lentidão crescente, cansaço, depois preso ao chão e exposto.",
                "Every cut DOUBLES the target's weight (×2 → ×32): growing slowness, fatigue, then pinned to the ground and exposed."),
        bankai=("Gravidade esmagadora: a cada segundo todos em volta ficam mais pesados e são puxados para o chão.",
                "Crushing gravity: every second everyone around grows heavier and is dragged to the ground."),
        command=("Curve-se, %s!", "Bow down, %s!"),
        bankai_name=("Tenjū Shizukane", "Tenjū Shizukane"),
        landscape=("Planície de Ferro", "Iron Plain"),
        spirit=("Shizukane", "Shizukane")),
    "petals": dict(
        name=("Pétalas de Lâmina", "Blade Petals"),
        desc=("A lâmina se desfaz em mil pétalas cortantes que dançam ao seu comando.",
              "The blade scatters into a thousand cutting petals that dance at your command."),
        shikai=("Cada corte abre feridas: Sangrando acumulado (dano contínuo que dobra se o alvo se mexer).",
                "Every cut opens wounds: stacking Bleeding (damage over time that doubles if the target moves)."),
        bankai=("Tempestade de mil lâminas: a cada segundo as pétalas cortam todos em volta e aprofundam as feridas.",
                "A storm of a thousand blades: every second the petals cut everyone around and deepen the wounds."),
        command=("Disperse-se, %s!", "Scatter, %s!"),
        bankai_name=("Senka Chiribana", "Senka Chiribana"),
        landscape=("Jardim das Mil Pétalas", "Garden of a Thousand Petals"),
        spirit=("Chiribana", "Chiribana")),
    "frost": dict(
        name=("Gelo", "Frost"),
        desc=("A dança do gelo: cada movimento da lâmina deixa o ar mais frio e mais quieto.",
              "The ice dance: every movement of the blade makes the air colder and stiller."),
        shikai=("Cada corte acumula Gelado; no quinto acúmulo o alvo congela.",
                "Every cut stacks Chilled; at the fifth stack the target freezes."),
        bankai=("Campo congelante: o frio toma todo o raio e congela quem ficar nele.",
                "Freezing field: the cold takes the whole radius and freezes whoever stays in it."),
        command=("Dance, %s!", "Dance, %s!"),
        bankai_name=("Hyōga Yukishiro", "Hyōga Yukishiro"),
        landscape=("Campo de Neve Eterna", "Field of Eternal Snow"),
        spirit=("Yukishiro", "Yukishiro")),
    "flame": dict(
        name=("Chama Ancestral", "Ancestral Flame"),
        desc=("O fogo mais antigo de todos, que não aceita ordens de ninguém além de você e reduz tudo a cinzas.",
              "The oldest fire of all, which obeys no one but you and turns everything to ash."),
        shikai=("Cada corte acumula Em Chamas e acende um fogo que custa a apagar.",
                "Every cut stacks Burning and lights a fire that is hard to put out."),
        bankai=("Inferno: tudo em volta queima sem parar enquanto a Bankai durar.",
                "Inferno: everything around burns without stopping while the Bankai lasts."),
        command=("Reduza tudo a cinzas, %s!", "Turn all creation to ash, %s!"),
        bankai_name=("Shōen Enkoku", "Shōen Enkoku"),
        landscape=("Templo da Chama Ancestral", "Temple of the Ancestral Flame"),
        spirit=("Enkoku", "Enkoku")),
    "brush": dict(
        name=("Pincel Branco", "White Brush"),
        desc=("Uma lâmina-pincel: a tinta negra \"apaga o nome\" do que toca — e o que não tem nome não pode lutar.",
              "A brush-blade: its black ink \"erases the name\" of what it touches — and what has no name cannot fight."),
        shikai=("A tinta apaga o nome: Silenciado (sem magias), Fraqueza e perde os efeitos bons; depois fica imune ao pincel por 8 s.",
                "The ink erases the name: Silenced (no spells), Weakness and loses its good effects; then immune to the brush for 8 s."),
        bankai=("Domínio de tinta: a cada segundo, todos em volta têm o nome apagado (chefes resistem).",
                "Ink domain: every second, everyone around has their name erased (bosses resist)."),
        command=("Escreva, %s!", "Write, %s!"),
        bankai_name=("Mumei Shirosumi", "Mumei Shirosumi"),
        landscape=("Página em Branco", "Blank Page"),
        spirit=("Shirosumi", "Shirosumi")),
}
for aid, v in ARCH.items():
    k(A + aid, *v["name"])
    for part in ("desc", "shikai", "bankai", "command", "bankai_name", "landscape"):
        k(A + aid + "." + part, *v[part])
    k("shinigami.isekai_mahou.spirit." + aid, *v["spirit"])
k("shinigami.isekai_mahou.spirit.unnamed", "Espírito sem nome", "Nameless Spirit")

for n, (pt, en) in enumerate([("Recruta da Academia", "Academy Recruit"), ("Asauchi Desperto", "Awakened Asauchi"),
                              ("O Nome", "The Name"), ("Shikai", "Shikai"), ("Mestre do Kidō", "Kidō Master"),
                              ("Bankai", "Bankai")]):
    k("shinigami.isekai_mahou.rank.%d" % n, pt, en)

R = "shinigami.isekai_mahou.req."
k(R + "circle", "Círculo %s", "Circle %s")
k(R + "kills", "Abates (zanpakutō ou técnicas Shinigami): %s", "Kills (zanpakutō or Shinigami techniques): %s")
k(R + "kido", "Kidō conjurados: %s", "Kidō cast: %s")
k(R + "meditations", "Visitas ao Mundo Interior: %s", "Visits to the Inner World: %s")
k(R + "shikai_hits", "Cortes da assinatura (Shikai ou glifo do arquétipo): %s", "Signature cuts (Shikai or archetype glyph): %s")
k(R + "releases", "Liberações da Shikai: %s", "Shikai releases: %s")
k(R + "bosses", "Chefes derrotados: %s", "Bosses defeated: %s")

for tid, (pt, en) in {"apprentice": ("Shihakushō", "Shihakushō"), "adept": ("Tenente", "Lieutenant"),
                      "master": ("Capitão", "Captain"), "archmage": ("Manto da Bankai", "Bankai Mantle")}.items():
    k("shinigami.isekai_mahou.outfit_tier." + tid, pt, en)

# =====================================================================================
#  Dicas de itens e traje
# =====================================================================================
Z = "tooltip.isekai_mahou.zanpakuto."
k(Z + "grade", "Zanpakutō de grau %s", "Grade %s zanpakutō")
k(Z + "state.0", "Selada", "Sealed")
k(Z + "state.1", "Shikai", "Shikai")
k(Z + "state.2", "Bankai", "Bankai")
k(Z + "spirit", "Espírito: %s (%s)", "Spirit: %s (%s)")
k(Z + "nameless", "A lâmina ainda não disse o próprio nome...", "The blade has not spoken its name yet...")
k(Z + "use", "Usar: libera a Shikai (rank III) · de novo: sela", "Use: release the Shikai (rank III) · again: seal it")
k(Z + "meditate", "Agachado + usar: meditar (Jinzen) e descer ao Mundo Interior",
  "Sneak + use: meditate (Jinzen) and descend into the Inner World")
k(Z + "soul", "Ligada à alma: não cai ao morrer e voa de volta ao dono", "Soul-bound: not dropped on death and flies back to its owner")
k(Z + "more", "Segure Shift para mais detalhes", "Hold Shift for more details")
k("tooltip.isekai_mahou.shinigami_spirit_candy",
  "Devolve 30% do recurso (Shinigami; outras classes 15%), cura um pouco e dá Velocidade. Recarga de 30 s.",
  "Restores 30% of your resource (Shinigami; other classes 15%), heals a little and grants Speed. 30 s cooldown.")

O = "tooltip.isekai_mahou.shinigami_outfit."
k(O + "tier", "Grau %s · %s (Círculo %s)", "Tier %s · %s (Circle %s)")
k(O + "set", "Traje completo: defesa %s · +%s de Vigor · +%s%% regen · +%s%% velocidade · +%s%% dano corpo a corpo",
  "Full outfit: %s defense · +%s Vigor · +%s%% regen · +%s%% speed · +%s%% melee damage")
k(O + "gift", "Entregue de graça ao Shinigami no Círculo %s.", "Given for free to Shinigami at Circle %s.")
k(O + "more", "Segure Shift para ver os bônus do traje", "Hold Shift to see the outfit bonuses")
F = "tooltip.isekai_mahou.shinigami_outfit_flavor."
k(F + "apprentice", "Quimono negro e faixa branca: o uniforme de quem acabou de sair da Academia.",
  "Black kimono and white sash: the uniform of someone fresh out of the Academy.")
k(F + "adept", "A braçadeira do esquadrão pesa no braço — e nas responsabilidades.",
  "The squad armband weighs on the arm — and on the responsibilities.")
k(F + "master", "O haori branco com o emblema do esquadrão. Poucos têm o direito de vesti-lo.",
  "The white haori bearing the squad emblem. Few have the right to wear it.")
k(F + "archmage", "Um casaco longo e rasgado, forrado de rubro. O Reiatsu da Bankai escorre pelas costuras.",
  "A long, tattered coat lined in crimson. The Bankai's Reiatsu seeps through the seams.")

k("toast.isekai_mahou.shinigami.outfit", "Traje Shinigami: %s", "Shinigami Outfit: %s")
k("toast.isekai_mahou.shinigami.outfit_sub", "Recompensa do Círculo %s", "Circle %s reward")

# =====================================================================================
#  HUD e aba do menu
# =====================================================================================
k("hud.isekai_mahou.shinigami.shikai", "%s · Shikai %s", "%s · Shikai %s")
k("hud.isekai_mahou.shinigami.bankai", "%s · BANKAI %s", "%s · BANKAI %s")
k("tab.isekai_mahou.shinigami", "Shinigami", "Shinigami")

k(S + "title", "Caminho da Zanpakutō", "Path of the Zanpakutō")
k(S + "rank_line", "Rank %s · %s", "Rank %s · %s")
k(S + "spirit", "Espírito da espada", "Sword spirit")
k(S + "no_archetype", "Sem arquétipo: o espírito diz o nome no rank II.", "No archetype: the spirit speaks its name at rank II.")
k(S + "archetype_line", "Arquétipo: %s", "Archetype: %s")
k(S + "unlocked.0", "Lâmina selada (a Shikai chega no rank III).", "Sealed blade (the Shikai comes at rank III).")
k(S + "unlocked.1", "Shikai liberada: use a espada para gritar o comando. (Bankai no rank V)",
  "Shikai unlocked: use the sword to shout the command. (Bankai at rank V)")
k(S + "unlocked.2", "Shikai e Bankai liberadas: monte a Bankai na sua magia G.",
  "Shikai and Bankai unlocked: put the Bankai in your G spell.")
k(S + "power", "Técnicas Shinigami: +%s%% de poder", "Shinigami techniques: +%s%% power")
k(S + "bonuses", "Bônus de classe", "Class bonuses")
for i, (pt, en) in enumerate([
    ("Regeneração espiritual: meditando (agachado e parado) cura vida, recupera Vigor e solta efeitos ruins.",
     "Spiritual regeneration: while meditating (sneaking and still) you heal, recover Vigor and shake off bad effects."),
    ("Reiatsu na lâmina: cada golpe da zanpakutō devolve 2% do Vigor; liberada, ×1,25 (Shikai) / ×1,6 (Bankai) de dano e a assinatura do arquétipo.",
     "Reiatsu in the blade: every zanpakutō hit restores 2% Vigor; released, ×1.25 (Shikai) / ×1.6 (Bankai) damage plus the archetype's signature."),
    ("Hohō: +4% de velocidade, +1% por rank do Caminho.", "Hohō: +4% speed, +1% per Path rank."),
    ("Caminho da Zanpakutō: magias com glifo Shinigami ganham +4% de poder por rank.",
     "Path of the Zanpakutō: spells with a Shinigami glyph gain +4% power per rank."),
    ("Lâmina ligada à alma: a zanpakutō não cai ao morrer e voa de volta quando jogada fora.",
     "Soul-bound blade: the zanpakutō is not dropped on death and flies back when thrown away."),
], 1):
    k(S + "bonus.%d" % i, pt, en)
k(S + "meditate", "Meditar (Jinzen)", "Meditate (Jinzen)")
k(S + "meditate_out", "Voltar ao mundo (Jinzen)", "Return to the world (Jinzen)")
k(S + "meditate_tip", "Fique parado 5 s para a alma descer ao Mundo Interior (agachado + usar a zanpakutō também funciona).",
  "Stay still for 5 s for your soul to descend into the Inner World (sneak + use the zanpakutō works too).")
k(S + "not_shinigami", "Você não é Shinigami: estas técnicas só chegam por Lição de um Shinigami.",
  "You are not a Shinigami: these techniques only come through a Lesson from a Shinigami.")
k(S + "done", "Concluído", "Done")
k(S + "next", "Próximo", "Next")
k(S + "locked", "Bloqueado", "Locked")
k(S + "reward", "Prêmio:", "Reward:")
k(S + "reward_xp", "+%s XP", "+%s XP")
k(S + "reward_name", "+ nome do espírito", "+ spirit's name")
k(S + "reward_shikai", "+ Shikai", "+ Shikai")
k(S + "reward_legend", "+ Magia Lendária", "+ Legendary Spell")
k(S + "go_fight", "Pronto! Medite e vença o espírito no Mundo Interior.", "Ready! Meditate and defeat the spirit in the Inner World.")
k(S + "go_talk", "Pronto! Medite e fale com o espírito no Mundo Interior.", "Ready! Meditate and talk to the spirit in the Inner World.")
k(S + "keep_going", "Cumpra os feitos acima para avançar.", "Complete the feats above to advance.")
k(S + "archetypes", "Arquétipos de Shikai", "Shikai archetypes")
k(S + "yours", "  (o seu)", "  (yours)")
k(S + "guide", "Como evoluir", "How to progress")
for i, (pt, en) in enumerate([
    ("Agache e use a zanpakutō para meditar (Jinzen): a alma desce ao Mundo Interior, onde o espírito da espada espera.",
     "Sneak and use the zanpakutō to meditate (Jinzen): your soul descends into the Inner World, where the sword spirit waits."),
    ("Cada rank pede Círculo e feitos (abates, Kidō, cortes, liberações, chefes) e é FEITO falando com o espírito.",
     "Each rank needs a Circle and feats (kills, Kidō, cuts, releases, bosses) and is DONE by talking to the spirit."),
    ("Rank II: o espírito diz o nome e você escolhe o arquétipo da Shikai — a paisagem do Mundo Interior muda.",
     "Rank II: the spirit says its name and you choose the Shikai archetype — the Inner World's landscape changes."),
    ("Ranks III e V terminam numa luta: vença (ele se rende abaixo de 10%). Perder só leva de volta à praça.",
     "Ranks III and V end in a fight: win (it yields below 10%). Losing just sends you back to the plaza."),
    ("Todas as técnicas são glifos: monte Zanjutsu, Shunpo, Kidō e a assinatura do arquétipo no Grimório com o resto do sistema.",
     "Every technique is a glyph: build Zanjutsu, Shunpo, Kidō and the archetype signature in the Grimoire with the rest of the system."),
    ("Agachado + clicar no espírito (rank III+) = treino: luta sem risco, com XP uma vez por dia.",
     "Sneak + click the spirit (rank III+) = sparring: a risk-free fight, with XP once per day."),
], 1):
    k(S + "guide.%d" % i, pt, en)
k(S + "choose_title", "O nome da sua lâmina", "The name of your blade")
k(S + "choose_sub", "Escolha o arquétipo da sua Shikai. A sua alma vai moldar o Mundo Interior.",
  "Choose your Shikai archetype. Your soul will shape the Inner World.")
k(S + "choose_click", "Clique para escolher", "Click to choose")
k(S + "shikai_label", "Shikai:", "Shikai:")
k(S + "bankai_label", "Bankai — %s:", "Bankai — %s:")
k(S + "landscape", "Paisagem: %s", "Landscape: %s")

# =====================================================================================
#  Mensagens
# =====================================================================================
MSG = {
    "welcome": ("Você é um Shinigami! A zanpakutō está ligada à sua alma. Agache e use a espada para meditar (Jinzen) e "
                "descer ao Mundo Interior. Veja a aba Shinigami no menu (B).",
                "You are a Shinigami! The zanpakutō is bound to your soul. Sneak and use the sword to meditate (Jinzen) and "
                "descend into the Inner World. See the Shinigami tab in the menu (B)."),
    # meditação
    "jinzen_start": ("Jinzen: fique parado para a alma descer ao Mundo Interior...",
                     "Jinzen: stay still for your soul to descend into the Inner World..."),
    "jinzen_return_start": ("Jinzen: fique parado para a alma voltar ao corpo...", "Jinzen: stay still for your soul to return to your body..."),
    "jinzen_bar": ("Jinzen %s", "Jinzen %s"),
    "jinzen_return_bar": ("Voltando %s", "Returning %s"),
    "jinzen_cancel": ("Meditação cancelada.", "Meditation cancelled."),
    "jinzen_broken": ("A concentração se quebrou.", "Your concentration broke."),
    "jinzen_trial": ("Não dá para sair no meio de uma prova!", "You cannot leave in the middle of a trial!"),
    "jinzen_ground": ("Medite com os pés no chão.", "Meditate with your feet on the ground."),
    "jinzen_combat": ("Em combate não dá para se concentrar.", "You cannot concentrate in combat."),
    "jinzen_hint": ("Agache e use a zanpakutō (ou o botão da aba) para meditar.", "Sneak and use the zanpakutō (or the tab button) to meditate."),
    # Mundo Interior
    "inner_missing": ("O Mundo Interior não existe neste mundo (dimensão ausente).", "The Inner World does not exist in this world (missing dimension)."),
    "inner_forming": ("A sua alma está tomando forma...", "Your soul is taking shape..."),
    "inner_title": ("Mundo Interior", "Inner World"),
    "inner_landscape_none": ("Mar de Névoa", "Sea of Mist"),
    "inner_arrived": ("Você está no seu Mundo Interior. %s espera na praça. Medite de novo para voltar.",
                      "You are in your Inner World. %s waits in the plaza. Meditate again to return."),
    "inner_left": ("A alma voltou ao corpo.", "Your soul returned to your body."),
    # liberação
    "shikai_shout": ("%s: \"%s\"", "%s: \"%s\""),
    "bankai_shout": ("%s: \"BANKAI... %s!\"", "%s: \"BANKAI... %s!\""),
    "shikai_locked_name": ("A lâmina ainda não tem nome. Medite e fale com o espírito.", "The blade has no name yet. Meditate and talk to the spirit."),
    "shikai_locked": ("A Shikai só chega no rank III do Caminho da Zanpakutō.", "The Shikai only comes at rank III of the Path of the Zanpakutō."),
    "shikai_cooldown": ("A lâmina ainda descansa: %s s", "The blade is still resting: %s s"),
    "already_bankai": ("A Bankai já está ativa.", "The Bankai is already active."),
    "sealed": ("A lâmina foi selada.", "The blade is sealed."),
    "shikai_end": ("A Shikai acabou: a lâmina voltou a dormir.", "The Shikai ended: the blade went back to sleep."),
    "bankai_end": ("A Bankai acabou. O corpo cobra o preço (Fraqueza e Cansaço).", "The Bankai ended. Your body pays the price (Weakness and Fatigue)."),
    "shikai_learned": ("Shikai liberada! Use a zanpakutō e grite: \"%s\"", "Shikai unlocked! Use the zanpakutō and shout: \"%s\""),
    "bankai_learned": ("Bankai aprendida: %s! Monte o glifo Bankai na sua magia G do Grimório.",
                       "Bankai learned: %s! Put the Bankai glyph in your G spell in the Grimoire."),
    # assinaturas
    "weight": ("Peso ×%s", "Weight ×%s"),
    "erased": ("O nome de %s foi apagado!", "%s's name has been erased!"),
    # lâmina ligada à alma
    "blade_kept": ("A sua zanpakutō voltou com você.", "Your zanpakutō came back with you."),
    "blade_returned": ("A sua zanpakutō voltou para você.", "Your zanpakutō returned to you."),
    "blade_returned_other": ("Esta lâmina pertence a outra alma: ela voltou ao dono.", "This blade belongs to another soul: it returned to its owner."),
    "not_shinigami_blade": ("Só um Shinigami consegue ouvir esta lâmina.", "Only a Shinigami can hear this blade."),
    # espírito e provas
    "spirit_not_yours": ("Este espírito não é seu.", "This spirit is not yours."),
    "choose_not_now": ("Converse com o espírito antes de escolher.", "Talk to the spirit before choosing."),
    "spirit_materialized": ("%s (Materializado)", "%s (Materialized)"),
    "arena_name": ("Mundo Interior", "Inner World"),
    "trial_start.1": ("Prova da Shikai: vença %s! Ele se rende abaixo de 10%% da vida.", "Shikai trial: defeat %s! It yields below 10%% health."),
    "trial_start.2": ("Prova da Bankai: subjugue %s materializado — 3 fases!", "Bankai trial: subdue the materialized %s — 3 phases!"),
    "trial_start.3": ("Treino com %s (XP uma vez por dia).", "Sparring with %s (XP once per day)."),
    "trial_failed": ("Você perdeu a prova. Recupere-se e tente de novo!", "You lost the trial. Recover and try again!"),
    # ranks
    "rank_up_title": ("Caminho da Zanpakutō — Rank %s", "Path of the Zanpakutō — Rank %s"),
    "rank_up_announce": ("%s alcançou \"%s\" no Caminho da Zanpakutō!", "%s reached \"%s\" on the Path of the Zanpakutō!"),
    "rank_up": ("Caminho da Zanpakutō — rank %s: %s! +%s XP", "Path of the Zanpakutō — rank %s: %s! +%s XP"),
    "rank_ready": ("O rank %s do Caminho da Zanpakutō está pronto! Medite e fale com %s no Mundo Interior.",
                   "Rank %s of the Path of the Zanpakutō is ready! Meditate and talk to %s in the Inner World."),
    "rank_ready_inner": ("O rank %s está pronto! Fale com %s.", "Rank %s is ready! Talk to %s."),
    # traje
    "outfit_granted": ("Círculo %s: o traje %s chegou!", "Circle %s: the %s outfit has arrived!"),
    "outfit_equipped": ("Vestido na hora (as peças antigas foram para o inventário).", "Equipped right away (the old pieces went to your inventory)."),
    "outfit_inventory": ("As peças novas foram para o inventário.", "The new pieces went to your inventory."),
    # comando
    "status_head": ("Caminho da Zanpakutō — rank %s: %s", "Path of the Zanpakutō — rank %s: %s"),
    "status_spirit": ("Espírito: %s · Arquétipo: %s", "Spirit: %s · Archetype: %s"),
    "status_no_archetype": ("nenhum", "none"),
    "status_release": ("Liberação máxima: %s", "Highest release: %s"),
    "status_ready": ("O rank %s está pronto! Medite e fale com o espírito.", "Rank %s is ready! Meditate and talk to the spirit."),
    "status_next": ("Para o rank %s falta:", "For rank %s you still need:"),
    "cmd_rank": ("Rank Shinigami ajustado para %s.", "Shinigami rank set to %s."),
    "cmd_archetype": ("Arquétipo trocado para %s.", "Archetype changed to %s."),
    "cmd_archetype_unknown": ("Arquétipo desconhecido (weight, petals, frost, flame, brush).", "Unknown archetype (weight, petals, frost, flame, brush)."),
    "cmd_feats": ("Feitos do rank %s completos.", "Feats for rank %s completed."),
}
for key, (pt, en) in MSG.items():
    k(M + key, pt, en)

# =====================================================================================
#  Diálogos com o espírito da espada
# =====================================================================================
DLG = {
    "foreign.1": ("...Você não é a minha alma. Não tenho nada a dizer a quem não carrega a minha lâmina.",
                  "...You are not my soul. I have nothing to say to one who does not carry my blade."),
    "spar.1": ("Quer treinar, %s? Então levante a lâmina. Não vou pegar leve só porque somos a mesma alma.",
               "You want to train, %s? Then raise your blade. I won't go easy just because we share a soul."),
    "rank1.1": ("...Então você finalmente consegue me ouvir.", "...So you can finally hear me."),
    "rank1.2": ("Eu sou a lâmina que você carrega, %s. Até hoje você me usou como aço comum. A partir de agora, lute COMIGO.",
                "I am the blade you carry, %s. Until today you used me as common steel. From now on, fight WITH me."),
    "rank1.3": ("Leve as Seis Grades de Luz: prender também é lutar. Volte quando eu souber quem você é — aí direi o meu nome.",
                "Take the Six Bars of Light: binding is fighting too. Come back when I know who you are — then I'll tell you my name."),
    "rank2.1": ("Você cresceu. Seus Kidō já não tremem, e eu senti cada corte.", "You have grown. Your Kidō no longer tremble, and I felt every cut."),
    "rank2.2": ("Um nome não se dá: se ESCUTA. Feche os olhos e me diga o que vê dentro de você.",
                "A name is not given: it is HEARD. Close your eyes and tell me what you see inside yourself."),
    "rank2.3": ("Escolha. A sua alma vai moldar este mundo — e a mim também.", "Choose. Your soul will shape this world — and me as well."),
    "named.1": ("Ouça bem, porque só vou dizer uma vez... Meu nome é %s.", "Listen well, because I will only say it once... My name is %s."),
    "named.2.weight": ("Cada corte meu pesa o dobro do anterior. Ninguém fica de pé diante de %s por muito tempo. Quando chegar a hora, o comando será: \"%s\"",
                       "Each of my cuts weighs twice the last. No one stands before %s for long. When the time comes, the command will be: \"%s\""),
    "named.2.petals": ("Eu não sou uma lâmina: sou mil. Quando %s se dispersar, nenhum escudo vai bastar. O comando será: \"%s\"",
                       "I am not one blade: I am a thousand. When %s scatters, no shield will be enough. The command will be: \"%s\""),
    "named.2.frost": ("O frio é paciente e bonito. %s dança, e o mundo para para assistir. O comando será: \"%s\"",
                      "Cold is patient and beautiful. %s dances, and the world stops to watch. The command will be: \"%s\""),
    "named.2.flame": ("Eu sou o fogo mais antigo. %s não obedece a reis nem a deuses — só a você, se for digno. O comando será: \"%s\"",
                      "I am the oldest fire. %s obeys neither kings nor gods — only you, if you are worthy. The command will be: \"%s\""),
    "named.2.brush": ("Tudo o que existe tem um nome escrito. %s é a tinta que o apaga. O comando será: \"%s\"",
                      "Everything that exists has a name written on it. %s is the ink that erases it. The command will be: \"%s\""),
    "named.3": ("Mas saber o meu nome não é me dominar. No rank III você vai ter que me vencer.",
                "But knowing my name is not mastering me. At rank III you will have to defeat me."),
    "rank3.1": ("Você sabe o meu nome. Mas saber não basta: %s só obedece a quem vence.",
                "You know my name. But knowing is not enough: %s only obeys those who win."),
    "rank3.2": ("Lute comigo! Se me derrubar, eu me rendo — e a Shikai é sua.", "Fight me! If you bring me down, I yield — and the Shikai is yours."),
    "rank3_won.1": ("...Hah. Nada mal. Nada mal mesmo.", "...Hah. Not bad. Not bad at all."),
    "rank3_won.2": ("Grite e eu responderei. Use a espada e diga: \"%s\"", "Shout and I will answer. Use the sword and say: \"%s\""),
    "rank4.1": ("A lâmina não é tudo. Um Shinigami de verdade domina também o Kidō.", "The blade is not everything. A true Shinigami also masters Kidō."),
    "rank4.2": ("Destruição e Amarras: você já entende os dois caminhos. Vou te mostrar o Muro do Vazio.",
                "Destruction and Binding: you already understand both ways. I'll show you the Void Wall."),
    "rank4.3": ("E leve esta técnica com você: uma meia-lua tão negra que até o céu vai querer fugir dela.",
                "And take this technique with you: a crescent so black that even the sky will want to flee it."),
    "rank5.1": ("Então você chegou à última porta.", "So you have reached the last door."),
    "rank5.2": ("A Bankai não é um presente. Você precisa me materializar e me SUBJUGAR. Eu sou %s — e desta vez lutarei com tudo.",
                "Bankai is not a gift. You must materialize me and SUBDUE me. I am %s — and this time I will fight with everything."),
    "rank5.3": ("Se cair, volta para a praça. Se vencer... nunca mais seremos dois.", "If you fall, you return to the plaza. If you win... we will never be two again."),
    "rank5_won.1": ("...Você me subjugou. Então é isto que significa ser um só.", "...You subdued me. So this is what it means to be one."),
    "rank5_won.2": ("Chame o meu nome verdadeiro na hora certa: %s. Monte a Bankai no Grimório, na sua magia G.",
                    "Call my true name at the right moment: %s. Put the Bankai in the Grimoire, in your G spell."),
    "spar_won.1": ("Bom treino. Leve esta experiência com você.", "Good training. Take this experience with you."),
    "spar_won.2": ("Bom treino... mas hoje já te ensinei tudo o que podia. Volte amanhã.",
                   "Good training... but I've taught you all I could for today. Come back tomorrow."),
    "fail.1": ("Ainda não. Descanse, lute lá fora e volte quando estiver pronto.", "Not yet. Rest, fight out there and come back when you are ready."),
    "fail.2": ("A Bankai esmaga quem não está pronto. Fique mais forte e me desafie de novo.",
               "Bankai crushes those who are not ready. Grow stronger and challenge me again."),
    "fail.3": ("Caiu? Levante. É para isso que serve o treino.", "Fell down? Get up. That's what training is for."),
    "hint.0": ("Você ainda não me ouve direito... Lute mais com a lâmina e volte.", "You still can't hear me properly... Fight more with the blade and come back."),
    "hint.1": ("Ainda não é hora de saber o meu nome.", "It is not yet time to know my name."),
    "hint.2": ("Você ainda não é forte o bastante para me liberar.", "You are not yet strong enough to release me."),
    "hint.3": ("O Kidō e a lâmina precisam andar juntos. Treine os dois.", "Kidō and the blade must walk together. Train both."),
    "hint.4": ("A Bankai exige muito mais de você.", "The Bankai demands far more of you."),
    "hint.missing": ("Ainda falta:\n%s", "You still need:\n%s"),
    "idle.1": ("O vento aqui dentro mudou, %s. Você anda lutando bem.", "The wind in here has changed, %s. You've been fighting well."),
    "idle.2": ("Quando você hesita, chove neste mundo. Não deixe chover.", "When you hesitate, it rains in this world. Don't let it rain."),
    "idle.3": ("Uma lâmina sem propósito é só um pedaço de ferro. Lembre-se do seu, %s.",
               "A blade without purpose is just a piece of iron. Remember yours, %s."),
    "idle.4": ("O Kidō numerado é como uma oração: quanto maior o número, mais a alma precisa estar firme.",
               "Numbered Kidō is like a prayer: the higher the number, the steadier the soul must be."),
    "idle.5": ("Sinto os Vazios lá fora. Quando os encontrar, não tenha piedade.", "I sense Hollows out there. When you find them, show no mercy."),
    "idle.6": ("Sente-se um pouco, %s. Até os capitães precisam de silêncio.", "Sit for a while, %s. Even captains need silence."),
    "idle.exit": ("Quando quiser voltar, medite de novo.", "When you want to go back, meditate again."),
}
for key, (pt, en) in DLG.items():
    k(D + key, pt, en)

# =====================================================================================
#  Técnicas avançadas (pacote de técnicas icônicas): Getsuga Tenshō, Kurohitsugi, Máscara do Vazio + Cero,
#  Vasto Lorde, Mugetsu — glifos, lendas, mensagens, aba, requisitos e diálogos do Vazio Interior
# =====================================================================================
k("entity.isekai_mahou.shinigami_black_coffin", "Caixão Negro", "Black Coffin")
k("entity.isekai_mahou.shinigami_hollow_beam", "Cero", "Cero")
TECH_G = {
    "zanpakuto_getsuga": (
        "Getsuga Tenshō", "Getsuga Tenshō",
        "FORMA (Shinigami, rank IV). A lâmina libera o Reiatsu numa LUA CRESCENTE ENORME na diagonal (5 blocos, +2 por "
        "Expandir; Carregar a deixa 25% maior) que voa ~40 blocos e corta TODOS os inimigos no caminho com dano próprio "
        "(5) além dos efeitos. Durante a Bankai ou com a Máscara do Vazio sai o GETSUGA TENSHŌ NEGRO: negro de borda "
        "rubra, 40% mais largo, dano 7 e +25% de poder. Dividir = leque. Pode ser estágio de magia.",
        "FORM (Shinigami, rank IV). The blade releases its Reiatsu as a HUGE diagonal CRESCENT MOON (5 blocks, +2 per "
        "Expand; Charge makes it 25% bigger) that flies ~40 blocks and cuts EVERY enemy in its path with its own damage "
        "(5) on top of the effects. During Bankai or with the Hollow Mask it becomes the BLACK GETSUGA TENSHŌ: black "
        "with a crimson rim, 40% wider, damage 7 and +25% power. Split = fan. Can be a spell stage."),
    "kido_hado_90": (
        "Hadō nº 90: Kurohitsugi", "Hadō #90: Kurohitsugi",
        "FORMA (Kidō, rank IV + 200 Kidō). Depois de 1,5 s de encantamento, um CAIXÃO NEGRO se ergue em volta do inimigo "
        "mirado (até 24 blocos, +4 por Acelerar): prende quem está dentro (Enraizado + Atordoado; chefes 1/3 do tempo), "
        "quatro rodadas de lanças de trevas aplicam os efeitos e no fim o caixão se ESMAGA (dano próprio 7 + efeitos). "
        "Expandir alarga o caixão. Custo: 4 cópias (as lanças).",
        "FORM (Kidō, rank IV + 200 Kidō). After a 1.5 s incantation, a BLACK COFFIN rises around the targeted enemy (up "
        "to 24 blocks, +4 per Accelerate): it traps everyone inside (Rooted + Stunned; bosses 1/3 of the time), four "
        "volleys of dark spears apply the effects and at the end the coffin CRUSHES (own damage 7 + effects). Expand "
        "widens the coffin. Cost: 4 copies (the spears)."),
    "shinigami_visored": (
        "Máscara do Vazio (Visored)", "Hollow Mask (Visored)",
        "FORMA (Shinigami, vencer o Vazio Interior). A mão passa no rosto e a máscara branca de listras rubras se forma "
        "por 20 s (+5 s a cada 10 usos, até 45 s; Estender aumenta): técnicas Shinigami +30% de poder, Força I, "
        "Velocidade I, Cero com força total, Getsuga Negro e a VOZ DUPLA (o que você fala ecoa distorcido). Ao pôr a "
        "máscara, uma explosão de Reiatsu aplica os efeitos em quem estiver a 4 blocos. Funciona sem efeitos.",
        "FORM (Shinigami, defeat the Inner Hollow). Your hand sweeps across your face and the white mask with crimson "
        "stripes forms for 20 s (+5 s every 10 uses, up to 45 s; Extend increases it): Shinigami techniques +30% "
        "power, Strength I, Speed I, full-power Cero, Black Getsuga and the DOUBLE VOICE (what you say echoes "
        "distorted). Putting on the mask releases a Reiatsu burst that applies the effects within 4 blocks. Works "
        "without effects."),
    "shinigami_cero": (
        "Cero do Vazio", "Hollow Cero",
        "FORMA (Shinigami, vencer o Vazio Interior). Carrega 0,7 s e dispara um feixe rubro de núcleo negro em linha "
        "reta (32 blocos, +4 por Acelerar) que atravessa TODOS os inimigos (dano próprio 4 + efeitos) e explode no fim. "
        "Expandir engrossa; Fantasma atravessa blocos. Força total só com a Máscara do Vazio ou no Vasto Lorde; sem ela o "
        "poder vaza (60%, dano 2). Pode ser estágio de magia.",
        "FORM (Shinigami, defeat the Inner Hollow). Charges for 0.7 s and fires a crimson beam with a black core in a "
        "straight line (32 blocks, +4 per Accelerate) that pierces EVERY enemy (own damage 4 + effects) and explodes at "
        "the end. Expand thickens it; Phantom goes through blocks. Full power only with the Hollow Mask or in Vasto "
        "Lorde; without it the power leaks (60%, damage 2). Can be a spell stage."),
    "shinigami_vasto_lorde": (
        "Vasto Lorde", "Vasto Lorde",
        "FORMA G (Shinigami, ultimate; rank V + Vazio domado + 30 usos da Máscara). Por 25 s o Vazio toma o corpo: "
        "máscara com chifres longos e buraco no peito, Força III, Resistência II, Velocidade II, regeneração de 3% da "
        "vida por segundo, golpes ×1,5, técnicas Shinigami +50%, um pulso por segundo (raio 6, +Expandir) com os efeitos "
        "e um CERO GIGANTE a cada 5 s. ARRISCADA: ao acabar o corpo desaba — vida em 25%, Fraqueza II, Lentidão, "
        "Cansaço e o Vigor zerado. Funciona sem efeitos.",
        "G FORM (Shinigami, ultimate; rank V + tamed Hollow + 30 Mask uses). For 25 s the Hollow takes over: a mask with "
        "long horns and a hole in the chest, Strength III, Resistance II, Speed II, 3% health regeneration per second, "
        "hits ×1.5, Shinigami techniques +50%, one pulse per second (radius 6, +Expand) with the effects and a GIANT "
        "CERO every 5 s. RISKY: when it ends the body collapses — health drops to 25%, Weakness II, Slowness, Fatigue "
        "and Vigor emptied. Works without effects."),
    "zanpakuto_mugetsu": (
        "Mugetsu", "Mugetsu",
        "FORMA G (Shinigami, ultimate de ÚLTIMO RECURSO; rank V + 150 Getsuga + 6 chefes → a última lição do espírito). "
        "Você se ergue, o mundo mergulha na escuridão total (Escuridão nos jogadores, Cegueira e Lentidão nos inimigos a "
        "40 blocos) e 1 s depois uma parede de trevas de 18 blocos (+2 por Expandir) corta TUDO por ~65 blocos, "
        "atravessando blocos: dano próprio 30 + os efeitos. O PREÇO: 10 minutos SEM PODER ESPIRITUAL (sem técnicas "
        "Shinigami, sem Shikai/Bankai, Vigor quase parado).",
        "G FORM (Shinigami, LAST-RESORT ultimate; rank V + 150 Getsuga + 6 bosses → the spirit's final lesson). You rise, "
        "the world sinks into total darkness (Darkness on players, Blindness and Slowness on enemies within 40 blocks) "
        "and 1 s later an 18-block wall of darkness (+2 per Expand) cuts EVERYTHING for ~65 blocks, through blocks: own "
        "damage 30 + the effects. THE PRICE: 10 minutes WITHOUT SPIRITUAL POWER (no Shinigami techniques, no "
        "Shikai/Bankai, Vigor almost frozen)."),
}
for gid, (npt, nen, dpt, den) in TECH_G.items():
    k("glyph.isekai_mahou." + gid, npt, nen)
    k("glyph.isekai_mahou." + gid + ".desc", dpt.replace("%", "%%"), den.replace("%", "%%"))

k("legend.isekai_mahou.shinigami_getsuga_tensho", "Getsuga Tenshō", "Getsuga Tenshō")
k("legend.isekai_mahou.shinigami_kurohitsugi", "Hadō nº 90: Kurohitsugi", "Hadō #90: Kurohitsugi")
k("legend.isekai_mahou.shinigami_mugetsu", "Mugetsu", "Mugetsu")

k(R + "rank", "Rank %s do Caminho da Zanpakutō", "Path of the Zanpakutō rank %s")
k(R + "getsuga", "Getsuga Tenshō disparados: %s", "Getsuga Tenshō fired: %s")
k(R + "visored_uses", "Usos da Máscara do Vazio: %s", "Hollow Mask uses: %s")
k(R + "hollow", "Domar o Vazio Interior (Mundo Interior)", "Tame the Inner Hollow (Inner World)")

TECH_MSG = {
    "tech_announce": ("%s domina uma técnica avançada: %s!", "%s masters an advanced technique: %s!"),
    "tech_toast": ("Técnica avançada", "Advanced technique"),
    "tech_learned": ("Nova técnica Shinigami: %s! Monte-a no Grimório.", "New Shinigami technique: %s! Put it together in the Grimoire."),
    "powerless_start": ("O Mugetsu consumiu todo o seu poder espiritual. Você ficará sem poder por %s minutos.",
                        "Mugetsu consumed all your spiritual power. You will be powerless for %s minutes."),
    "powerless_block": ("Sem poder espiritual... (%s)", "No spiritual power... (%s)"),
    "powerless_end": ("O seu poder espiritual voltou!", "Your spiritual power has returned!"),
    "vasto_busy": ("O Vazio já tomou o seu corpo.", "The Hollow has already taken your body."),
    "visored_shout": ("%s põe a Máscara do Vazio!", "%s puts on the Hollow Mask!"),
    "vasto_shout": ("%s: \"...\" — o Vazio desperta por completo: VASTO LORDE!", "%s: \"...\" — the Hollow fully awakens: VASTO LORDE!"),
    "vasto_end": ("O Vazio recua... o seu corpo desaba.", "The Hollow recedes... your body collapses."),
    "visored_end": ("A máscara se parte.", "The mask shatters."),
    "getsuga_shout": ("Getsuga Tenshō!", "Getsuga Tenshō!"),
    "getsuga_black_shout": ("Getsuga... Tenshō!!", "Getsuga... Tenshō!!"),
    "hado90_shout": ("Hadō nº 90: Kurohitsugi!", "Hadō #90: Kurohitsugi!"),
    "cero_shout": ("Cero!", "Cero!"),
    "mugetsu_shout": ("Mugetsu.", "Mugetsu."),
    "hollow_name": ("Vazio Interior", "Inner Hollow"),
    "trial_start.4": ("O VAZIO INTERIOR tomou %s! Vença-o — ele se rende abaixo de 10%% da vida.",
                      "The INNER HOLLOW has taken %s! Defeat it — it yields below 10%% health."),
}
for key, (pt, en) in TECH_MSG.items():
    k(M + key, pt, en)

TECH_S = {
    "techniques": ("Técnicas Avançadas", "Advanced Techniques"),
    "tech_known": ("Aprendida", "Learned"),
    "tech_ready": ("Pronta!", "Ready!"),
    "powerless": ("Sem poder espiritual (Mugetsu): %s", "No spiritual power (Mugetsu): %s"),
    "tech_how.getsuga": ("Chega sozinha no rank IV. Durante a Bankai ou com a máscara vira o Getsuga Negro.",
                         "Comes on its own at rank IV. During Bankai or with the mask it becomes the Black Getsuga."),
    "tech_how.hado_90": ("Chega sozinha no rank IV depois de 200 Kidō conjurados.", "Comes on its own at rank IV after casting 200 Kidō."),
    "tech_how.visored": ("No Mundo Interior, fale com o espírito: o VAZIO INTERIOR toma o lugar dele. Vença-o para aprender a "
                         "Máscara do Vazio e o Cero.",
                         "In the Inner World, talk to the spirit: the INNER HOLLOW takes its place. Defeat it to learn the "
                         "Hollow Mask and the Cero."),
    "tech_how.vasto_lorde": ("Chega sozinha no rank V, com o Vazio domado e 30 usos da Máscara. Forma G arriscada.",
                             "Comes on its own at rank V, with the Hollow tamed and 30 Mask uses. A risky G form."),
    "tech_how.mugetsu": ("A última lição: no rank V, com 150 Getsuga e 6 chefes, fale com o espírito no Mundo Interior. "
                         "Forma G de último recurso.",
                         "The final lesson: at rank V, with 150 Getsuga and 6 bosses, talk to the spirit in the Inner World. "
                         "A last-resort G form."),
    "tech_tip.getsuga": ("Getsuga Tenshō: monte no Grimório com um efeito de dano (ex.: Dano Arcano) — Carregar deixa maior.",
                         "Getsuga Tenshō: assemble it in the Grimoire with a damage effect (e.g. Arcane Damage) — Charge makes it bigger."),
    "tech_tip.hado_90": ("Kurohitsugi: mire num inimigo; as lanças repetem os efeitos 4 vezes antes do esmagamento.",
                         "Kurohitsugi: aim at an enemy; the spears repeat the effects 4 times before the crush."),
    "tech_tip.visored": ("Máscara do Vazio e Cero aprendidos! Ponha a máscara antes do Cero e do Getsuga para a força total.",
                         "Hollow Mask and Cero learned! Put on the mask before Cero and Getsuga for full power."),
    "tech_tip.vasto_lorde": ("Vasto Lorde: monte na magia G. Cuidado — quando acaba, o corpo desaba.",
                             "Vasto Lorde: put it in your G spell. Careful — when it ends, your body collapses."),
    "tech_tip.mugetsu": ("Mugetsu: monte na magia G e use só quando não houver outra saída — depois, 10 minutos sem poder.",
                         "Mugetsu: put it in your G spell and use it only when there is no other way — then 10 minutes powerless."),
}
for key, (pt, en) in TECH_S.items():
    k(S + key, pt, en)

TECH_DLG = {
    "hollow.1": ("...? Espere. Tem mais alguém aqui dentro. Algo BRANCO se mexe por trás de mim...",
                 "...? Wait. There is someone else in here. Something WHITE moves behind me..."),
    "hollow.2": ("\"Ei, Rei... %s. Esse poder todo e você ainda deixa ele dormindo comigo? Se não consegue usar, EU uso.\"",
                 "\"Hey, King... %s. All this power and you still leave it sleeping with me? If you can't use it, I WILL.\""),
    "hollow.3": ("Ele tomou o meu lugar! Vença o Vazio Interior — mostre quem manda nesta alma!",
                 "It took my place! Defeat the Inner Hollow — show who rules this soul!"),
    "hollow_won.1": ("\"Heh... Então o Rei aprendeu a andar a cavalo. Por enquanto.\"",
                     "\"Heh... So the King learned to ride the horse. For now.\""),
    "hollow_won.2": ("O Vazio recua, %s. O poder dele agora é seu: passe a mão no rosto e a MÁSCARA se forma.",
                     "The Hollow retreats, %s. Its power is now yours: sweep your hand across your face and the MASK forms."),
    "hollow_won.3": ("Use a máscara e o Cero com cuidado. Se ele crescer demais... o seu corpo inteiro vira Vazio.",
                     "Use the mask and the Cero with care. If it grows too much... your whole body becomes Hollow."),
    "mugetsu.1": ("%s... Existe uma última técnica. Não é um golpe: é você se tornar o próprio Getsuga.",
                  "%s... There is one last technique. It is not a strike: it is you becoming the Getsuga itself."),
    "mugetsu.2": ("Toda a sua força espiritual numa única lâmina de escuridão. Sem lua, sem céu. Ela corta tudo.",
                  "All your spiritual strength in a single blade of darkness. No moon, no sky. It cuts everything."),
    "mugetsu.3": ("Mas o preço é perder esse poder por um bom tempo. Use só quando não houver mais nada.",
                  "But the price is losing that power for a long while. Use it only when nothing else remains."),
    "mugetsu.4": ("Este é o meu último ensinamento: Mugetsu.", "This is my final teaching: Mugetsu."),
    "fail.4": ("\"Fraco demais, Rei. Volte quando conseguir me olhar nos olhos.\"",
               "\"Too weak, King. Come back when you can look me in the eye.\""),
}
for key, (pt, en) in TECH_DLG.items():
    k(D + key, pt, en)

# =====================================================================================
#  Saída
# =====================================================================================
out = os.path.join(ROOT, "src/main/lang/class_shinigami")
os.makedirs(out, exist_ok=True)
for idx, lang in ((0, "pt_br"), (1, "en_us")):
    data = {key: v[idx] for key, v in sorted(T.items())}
    with open(os.path.join(out, lang + ".json"), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
print(len(T), "chaves")
