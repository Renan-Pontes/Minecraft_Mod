"""
Traducoes do modulo class_steelball (pt_br principal + en_us), escritas em src/main/lang/class_steelball/.
Usado por tools/gen/class_steelball.py (rode aquele).
"""
import json
import os

PT = {}
EN = {}


def t(key, pt, en):
    PT[key] = pt
    EN[key] = en


K = "isekai_mahou"
SB = "steelball"

# ------------------------------------------------------------------ itens
t(f"item.{K}.steel_ball", "Bola de Aço", "Steel Ball")
t(f"item.{K}.steel_ball_t1", "Bola de Aço Temperada", "Tempered Steel Ball")
t(f"item.{K}.steel_ball_t2", "Bola de Aço Cravejada", "Studded Steel Ball")
t(f"item.{K}.steel_ball_t3", "Bola Dourada", "Golden Ball")
t(f"item.{K}.steel_ball_t4", "Bola da Proporção Áurea", "Ball of the Golden Ratio")
t(f"item.{K}.steelball_relic", "Relicário Selado", "Sealed Reliquary")
t(f"item.{K}.steelball_saint_compass", "Bússola do Santo", "Saint's Compass")
t(f"item.{K}.steelball_mentor_letter", "Carta do Mestre", "Master's Letter")
t(f"item.{K}.steelball_horse_whistle", "Apito do Corcel", "Steed Whistle")
t(f"item.{K}.steelball_mentor_spawn_egg", "Ovo Gerador do Mestre Rotari", "Master Rotari Spawn Egg")

PARTS = {
    "eyes": ("Olhos do Santo", "Saint's Eyes",
             "Agachado, revela as criaturas hostis a até 16 blocos.", "While sneaking, reveals hostile creatures within 16 blocks.",
             "Olhos fechados há séculos... ainda enxergam o que se esconde.", "Closed for centuries... still seeing what hides."),
    "left_arm": ("Braço Esquerdo do Santo", "Saint's Left Arm", "Unhas Giratórias com +20% de dano.",
                 "Spin Nails deal +20% damage.",
                 "As unhas desta mão giram sozinhas quando você a segura.", "The nails of this hand spin on their own when you hold it."),
    "right_arm": ("Braço Direito do Santo", "Saint's Right Arm", "Bolas de aço (arremesso e forma Bola de Aço) com +20% de dano.",
                  "Steel balls (throw and Steel Ball form) deal +20% damage.",
                  "Firme como quem já lançou mil bolas de aço.", "Steady as one who has thrown a thousand steel balls."),
    "spine": ("Coluna do Santo", "Saint's Spine", "+25% de resistência a repulsão e +2 de resistência da armadura.",
              "+25% knockback resistance and +2 armor toughness.",
              "Reta como a estrada que atravessa o continente.", "Straight as the road that crosses the continent."),
    "ears": ("Orelhas do Santo", "Saint's Ears", "+15% de regeneração do recurso (Foco/Mana/Vigor).",
             "+15% resource regeneration (Focus/Mana/Vigor).",
             "Escutam o vento antes de ele soprar.", "They hear the wind before it blows."),
    "legs": ("Pernas do Santo", "Saint's Legs", "+8% de velocidade e +2 blocos de queda segura.",
             "+8% speed and +2 blocks of safe fall.",
             "Andaram de um mar ao outro.", "They walked from one sea to the other."),
    "torso": ("Tronco do Santo", "Saint's Torso", "+4 de armadura.", "+4 armor.",
              "Coberto de cicatrizes de flechas que nunca o derrubaram.", "Covered in arrow scars that never brought it down."),
    "skull": ("Crânio do Santo", "Saint's Skull", "+8% de Poder Mágico e +60 do recurso máximo.",
              "+8% Spell Power and +60 max resource.",
              "Guarda os pensamentos do Santo. Quem reúne todos, dizem, alcança o giro perfeito.",
              "It keeps the Saint's thoughts. Whoever gathers them all, they say, reaches the perfect spin."),
    "heart": ("Coração do Cadáver Santo", "Heart of the Holy Corpse",
              "+8 de vida máxima. Com o Santo inteiro, desperta o LOVE TRAIN.",
              "+8 max health. With the Saint whole, it awakens the LOVE TRAIN.",
              "A última e mais rara parte. Ainda bate, devagar... esperando o resto do corpo.",
              "The last and rarest part. It still beats, slowly... waiting for the rest of the body."),
}
for pid, (npt, nen, bpt, ben, fpt, fen) in PARTS.items():
    t(f"item.{K}.steelball_part_{pid}", npt, nen)
    t(f"tooltip.{K}.{SB}.part.{pid}.bonus", bpt, ben)
    t(f"tooltip.{K}.{SB}.part.flavor.{pid}", fpt, fen)

OUT = {
    "jockey": ("Jóquei", "Jockey", ("Chapéu do Jóquei", "Colete do Jóquei", "Calça de Montaria do Jóquei", "Botas com Esporas do Jóquei"),
               ("Jockey's Hat", "Jockey's Vest", "Jockey's Riding Pants", "Jockey's Spurred Boots"),
               "Roupa leve de corrida: chapéu de aba com faixa, colete curto e esporas.",
               "Light racing clothes: banded brim hat, short vest and spurs."),
    "plains": ("Cavaleiro das Planícies", "Plains Rider",
               ("Chapéu do Cavaleiro das Planícies", "Sobretudo do Cavaleiro das Planícies", "Calça do Cavaleiro das Planícies",
                "Botas do Cavaleiro das Planícies"),
               ("Plains Rider's Hat", "Plains Rider's Duster", "Plains Rider's Pants", "Plains Rider's Boots"),
               "Sobretudo de viagem, cachecol ao vento e a cartucheira de bolas.",
               "Travel duster, scarf in the wind and the ball bandolier."),
    "rotation": ("Mestre da Rotação", "Master of Rotation",
                 ("Chapéu do Mestre da Rotação", "Casaca do Mestre da Rotação", "Calça do Mestre da Rotação",
                  "Botas do Mestre da Rotação"),
                 ("Master of Rotation's Hat", "Master of Rotation's Coat", "Master of Rotation's Pants",
                  "Master of Rotation's Boots"),
                 "Espirais gravadas que acendem quando a rotação é perfeita.",
                 "Engraved spirals that light up when the spin is perfect."),
    "saint": ("Peregrino Dourado", "Golden Pilgrim",
              ("Chapéu do Peregrino Dourado", "Manto do Peregrino Dourado", "Calça do Peregrino Dourado",
               "Botas do Peregrino Dourado"),
              ("Golden Pilgrim's Hat", "Golden Pilgrim's Mantle", "Golden Pilgrim's Pants", "Golden Pilgrim's Boots"),
              "O Retângulo Dourado no peito e a capa de quem atravessou o continente.",
              "The Golden Rectangle on the chest and the cape of one who crossed the continent."),
}
PIECES = ("helmet", "chestplate", "leggings", "boots")
for tid, (tpt, ten, ipt, ien, fpt, fen) in OUT.items():
    t(f"outfit.{K}.{SB}.tier.{tid}", tpt, ten)
    t(f"tooltip.{K}.{SB}.outfit.flavor.{tid}", fpt, fen)
    for i, piece in enumerate(PIECES):
        t(f"item.{K}.steelball_outfit_{tid}_{piece}", ipt[i], ien[i])

# ------------------------------------------------------------------ entidades e efeito
t(f"entity.{K}.steelball_ball", "Bola de Aço", "Steel Ball")
t(f"entity.{K}.steelball_nail", "Unha Giratória", "Spin Nail")
t(f"entity.{K}.steelball_vortex", "Turbilhão Giratório", "Spinning Vortex")
t(f"entity.{K}.steelball_infinite_spin", "Rotação Infinita", "Infinite Rotation")
t(f"entity.{K}.steelball_mentor", "Mestre Rotari", "Master Rotari")
t(f"entity.{K}.steelball_steed", "Corcel de %s", "%s's Steed")
t(f"effect.{K}.steelball_spun", "Girado", "Spun")
t(f"effect.{K}.steelball_spun.desc", "A rotação tomou o corpo: mais lento, golpes mais fracos e, nas criaturas, rodopio sem controle.",
  "Rotation has taken the body: slower, weaker blows and, for creatures, uncontrolled spinning.")

# ------------------------------------------------------------------ glifos (todos de origem do Steel Ball)
GLYPHS = {
    "spin_ball": ("Bola de Aço", "Steel Ball",
                  "Forma de origem do Steel Ball: arremessa uma bola de aço girando que leva a magia e VOLTA para a mão. "
                  "Com uma Bola de Aço na mão, +6% de poder por grau dela. Dividir = leque de bolas; Perfurar, Ricochete, "
                  "Teleguiado e Acelerar valem.",
                  "Steel Ball origin form: throws a spinning steel ball that carries the spell and RETURNS to your hand. "
                  "Holding a Steel Ball gives +6% power per tier. Split = fan of balls; Pierce, Ricochet, Homing and "
                  "Accelerate apply."),
    "spin_nail": ("Unha Giratória", "Spin Nail",
                  "Forma de origem: rajada de unhas girando (3 + 1 por Dividir, até 10), cada uma é uma cópia dos efeitos. "
                  "Muda com o Ato: Ato 2 persegue e deixa Girado; Ato 3 — agachado, você entra no buraco e sai atrás do alvo.",
                  "Origin form: burst of spinning nails (3 + 1 per Split, up to 10), each one a copy of the effects. "
                  "Changes with your Act: Act 2 homes in and leaves Spun; Act 3 — sneaking, you enter the hole and come "
                  "out behind the target."),
    "spin_vortex": ("Turbilhão Giratório", "Spinning Vortex",
                    "Forma de origem: crava uma bola girando no chão mirado (até 24 blocos) que vira um redemoinho de raio "
                    "3,5 por 4 s. Puxa os inimigos em espiral e aplica os efeitos a cada 1 s (Expandir/Estender valem).",
                    "Origin form: drives a spinning ball into the targeted ground (up to 24 blocks), creating a 3.5-radius "
                    "whirl for 4 s. Pulls enemies in a spiral and applies the effects every 1 s (Expand/Extend apply)."),
    "infinite_rotation": ("Rotação Infinita", "Infinite Rotation",
                          "Forma G (Ato 4): a unha dourada do giro perfeito. O alvo atingido fica preso numa espiral sem fim "
                          "por 10 s — não sai do lugar, gira e recebe o giro e os efeitos a cada 0,5 s. +5% de poder por "
                          "Parte do Cadáver Santo. Chefes só ficam muito lentos.",
                          "G form (Act 4): the golden nail of the perfect spin. The target is trapped in an endless spiral "
                          "for 10 s — it can't move, spins and takes the spin plus the effects every 0.5 s. +5% power per "
                          "Holy Corpse Part. Bosses are only heavily slowed."),
    "rotation_shock": ("Choque de Rotação", "Rotation Shock",
                       "Efeito de origem: a rotação entra no corpo — dano de Terra e o alvo fica Girado (acumula até III). "
                       "Amplificar começa no II. Inverter: arremessa o alvo girando para longe.",
                       "Origin effect: rotation enters the body — Earth damage and the target becomes Spun (stacks up to "
                       "III). Amplify starts at II. Invert: hurls the target away, spinning."),
    "spin_heal": ("Rotação Medicinal", "Healing Rotation",
                  "Efeito de origem (benéfico): o giro da família de médicos fecha feridas e tira 1 efeito ruim (2 com "
                  "Amplificar), com Regeneração. Inverter, em inimigos: dano e tira 1 efeito bom.",
                  "Origin effect (beneficial): the physicians' spin closes wounds and removes 1 harmful effect (2 with "
                  "Amplify), plus Regeneration. Invert, on enemies: damage and removes 1 beneficial effect."),
    "muscle_lock": ("Contração Muscular", "Muscle Lock",
                    "Efeito de origem: o giro contrai os músculos — o alvo fica Enraizado e Fraco por 2 s (Estender "
                    "alonga). Inverter: Lentidão e Fraqueza longas, sem prender (bom contra chefes).",
                    "Origin effect: the spin contracts the muscles — the target is Rooted and Weakened for 2 s (Extend "
                    "lengthens). Invert: long Slowness and Weakness without rooting (good against bosses)."),
    "spin_drill": ("Furadeira Giratória", "Spin Drill",
                   "Efeito de origem: 4 golpes rápidos que perfuram e deixam o alvo Exposto. Em blocos, cava um túnel "
                   "curto (Expandir alonga; respeita arenas e lugares protegidos).",
                   "Origin effect: 4 quick piercing hits that leave the target Exposed. On blocks, digs a short tunnel "
                   "(Expand lengthens; respects arenas and protected places)."),
    "golden_rectangle": ("Retângulo Dourado", "Golden Rectangle",
                         "Aumento de origem: a proporção perfeita do giro — +50% de dano no que ele aumenta e atravessa +1 "
                         "alvo. Acertos com ele treinam a lição V.",
                         "Origin augment: the perfect proportion of the spin — +50% damage on what it boosts and pierces "
                         "+1 target. Hits with it train lesson V."),
    "gallop_spin": ("Energia do Galope", "Gallop Energy",
                    "Aumento de origem: a força do cavalo entra na rotação — montado +40% de dano (a pé +10%), a montaria "
                    "dispara junto e a Bola de Aço sai 40% mais rápida.",
                    "Origin augment: the horse's power flows into the spin — mounted +40% damage (on foot +10%), your "
                    "mount surges with it and the Steel Ball flies 40% faster."),
}
for gid, (npt, nen, dpt, den) in GLYPHS.items():
    t(f"glyph.{K}.{gid}", npt, nen)
    t(f"glyph.{K}.{gid}.desc", dpt, den)

t(f"legend.{K}.steelball_golden_rider", "Esfera do Cavaleiro Dourado", "Golden Rider's Sphere")
t(f"legend.{K}.steelball_infinite_act", "Rotação Infinita — Ato 4", "Infinite Rotation — Act 4")

# ------------------------------------------------------------------ tooltips
t(f"tooltip.{K}.{SB}.ball.flavor", "Pesada, polida, perfeitamente redonda. Gira na mão antes de voar.",
  "Heavy, polished, perfectly round. It spins in the hand before it flies.")
t(f"tooltip.{K}.{SB}.ball.flavor_t1", "Temperada no fogo: o giro não perde força.", "Fire-tempered: the spin never loses strength.")
t(f"tooltip.{K}.{SB}.ball.flavor_t2", "Cravejada de ouro: cada ponto marca uma volta.", "Studded with gold: each dot marks a turn.")
t(f"tooltip.{K}.{SB}.ball.flavor_t3", "Dourada como a rotação perfeita.", "Golden as the perfect rotation.")
t(f"tooltip.{K}.{SB}.ball.flavor_t4", "Medida na razão áurea. Quem a lança não erra a proporção.",
  "Measured in the golden ratio. Whoever throws it never misses the proportion.")
t(f"tooltip.{K}.{SB}.ball.damage", "Arremesso carregado: %s de dano", "Charged throw: %s damage")
t(f"tooltip.{K}.{SB}.ball.use", "Segure o botão direito para girar (até 1 s) e solte para arremessar. A bola volta para a mão.",
  "Hold right-click to spin it (up to 1 s) and release to throw. The ball returns to your hand.")
t(f"tooltip.{K}.{SB}.ball.spell", "Na mão: +%s%% de poder na forma Bola de Aço", "In hand: +%s%% power for the Steel Ball form")
t(f"tooltip.{K}.{SB}.compass", "A agulha treme na direção de um santo enterrado.", "The needle trembles toward a buried saint.")
t(f"tooltip.{K}.{SB}.compass.use", "Botão direito: aponta a Sepultura do Santo mais próxima.",
  "Right-click: points to the nearest Saint's Tomb.")
t(f"tooltip.{K}.{SB}.letter", "\"Quando a lição estiver pronta, eu apareço. Se tiver pressa, escreva.\" — R.",
  "\"When the lesson is ready, I'll show up. If you're in a hurry, write.\" — R.")
t(f"tooltip.{K}.{SB}.letter.use", "Botão direito: chama o Mestre Rotari (recarga de 1 min).",
  "Right-click: calls Master Rotari (1 min cooldown).")
t(f"tooltip.{K}.{SB}.whistle", "Um Steel Ball nunca anda a pé.", "A Steel Ball never walks.")
t(f"tooltip.{K}.{SB}.whistle.unbound", "Botão direito: chama um corcel de corrida domado e selado.",
  "Right-click: calls a tamed, saddled racing steed.")
t(f"tooltip.{K}.{SB}.whistle.bound", "Botão direito: chama o SEU corcel para o seu lado.",
  "Right-click: calls YOUR steed to your side.")
t(f"tooltip.{K}.{SB}.relic", "Um relicário de madeira lacrado com ouro. Algo santo repousa aqui dentro.",
  "A wooden reliquary sealed with gold. Something holy rests within.")
t(f"tooltip.{K}.{SB}.relic.use", "Botão direito: abre e revela uma Parte do Cadáver Santo que você ainda não tem (o Coração "
  "nunca está num relicário).", "Right-click: opens it, revealing a Holy Corpse Part you don't have yet (the Heart is never "
  "in a reliquary).")
t(f"tooltip.{K}.{SB}.part.use", "Botão direito: a parte se funde ao seu corpo (uma vez por parte).",
  "Right-click: the part fuses with your body (once per part).")
t(f"tooltip.{K}.{SB}.part.owned", "Você já absorveu esta parte (dá para trocar com um amigo).",
  "You already absorbed this part (you can trade it with a friend).")
t(f"tooltip.{K}.{SB}.part.count", "Partes absorvidas: %s/%s", "Parts absorbed: %s/%s")
t(f"tooltip.{K}.{SB}.outfit.tier", "Grau %s — %s (Círculo %s)", "Tier %s — %s (Circle %s)")
t(f"tooltip.{K}.{SB}.outfit.set", "Conjunto completo: %s de defesa", "Full set: %s defense")
t(f"tooltip.{K}.{SB}.outfit.more", "Segure Shift para ver o conjunto", "Hold Shift to see the full set")
t(f"tooltip.{K}.{SB}.outfit.stat.focus", "+%s do recurso máximo (Foco)", "+%s max resource (Focus)")
t(f"tooltip.{K}.{SB}.outfit.stat.speed", "+%s%% de velocidade", "+%s%% speed")
t(f"tooltip.{K}.{SB}.outfit.stat.regen", "+%s%% de regeneração do recurso", "+%s%% resource regeneration")
t(f"tooltip.{K}.{SB}.outfit.stat.power", "+%s%% de Poder Mágico", "+%s%% Spell Power")
t(f"tooltip.{K}.{SB}.outfit.stat.safe_fall", "+%s blocos de queda segura", "+%s blocks of safe fall")

# ------------------------------------------------------------------ progressão (lições, atos, tarefas)
RANKS = {
    1: ("A Bola que Gira", "The Spinning Ball",
        "Segure a bola, sinta o giro, solte. A bola sempre volta para quem gira direito. Ensina a Unha Giratória (Ato 1) "
        "e a Rotação Medicinal; dá o Apito do Corcel e a Bússola do Santo.",
        "Hold the ball, feel the spin, release. The ball always returns to whoever spins it right. Teaches Spin Nail "
        "(Act 1) and Healing Rotation; gives the Steed Whistle and the Saint's Compass."),
    2: ("O Cavalo e o Giro", "The Horse and the Spin",
        "A força não vem do braço: vem do chão, sobe pelas pernas do cavalo e sai pela mão. Ensina a Energia do Galope "
        "e a Contração Muscular. A unha chega ao Ato 2 (persegue).",
        "Power doesn't come from the arm: it comes from the ground, climbs through the horse's legs and leaves through "
        "the hand. Teaches Gallop Energy and Muscle Lock. Your nail reaches Act 2 (homing)."),
    3: ("O Giro que Cura e Perfura", "The Spin that Heals and Pierces",
        "O mesmo giro que fecha uma ferida abre um túnel na rocha. Ensina o Turbilhão Giratório e a Furadeira "
        "Giratória. A unha chega ao Ato 3 (o buraco).",
        "The same spin that closes a wound opens a tunnel in rock. Teaches Spinning Vortex and Spin Drill. Your nail "
        "reaches Act 3 (the hole)."),
    4: ("O Desafio do Mestre", "The Master's Challenge",
        "Lute com o Mestre de verdade. Ninguém morre: ele se rende quando o seu giro for melhor. Ensina o Retângulo "
        "Dourado e dá a lenda Esfera do Cavaleiro Dourado.",
        "Fight the Master for real. Nobody dies: he yields when your spin is better. Teaches the Golden Rectangle and "
        "gives the legend Golden Rider's Sphere."),
    5: ("A Proporção Áurea", "The Golden Ratio",
        "O giro perfeito segue a proporção da natureza. Com quase todo o Cadáver Santo reunido, a unha alcança o Ato 4: "
        "a Rotação Infinita. Dá a lenda Rotação Infinita — Ato 4.",
        "The perfect spin follows nature's proportion. With almost the whole Holy Corpse gathered, the nail reaches "
        "Act 4: Infinite Rotation. Gives the legend Infinite Rotation — Act 4."),
}
for n, (tpt, ten, lpt, len_) in RANKS.items():
    t(f"gui.{K}.{SB}.rank.{n}", tpt, ten)
    t(f"gui.{K}.{SB}.rank.{n}.lesson", lpt, len_)
ACTS = {0: ("Sem Ato", "No Act"), 1: ("A Unha que Gira", "The Spinning Nail"), 2: ("A Unha que Persegue", "The Hunting Nail"),
        3: ("O Buraco Espiral", "The Spiral Hole"), 4: ("Rotação Infinita", "Infinite Rotation")}
for n, (apt, aen) in ACTS.items():
    t(f"gui.{K}.{SB}.act.{n}", apt, aen)
TASKS = {
    "ball_hits": ("Acertar %s bolas de aço (arremesso ou forma Bola de Aço)", "Land %s steel balls (throw or Steel Ball form)"),
    "mounted_kills": ("Derrotar %s inimigos montado", "Defeat %s enemies while mounted"),
    "nail_hits": ("Acertar %s Unhas Giratórias", "Land %s Spin Nails"),
    "spin_heals": ("Curar %s vezes com a Rotação Medicinal", "Heal %s times with Healing Rotation"),
    "golden_hits": ("Acertar %s golpes com o Retângulo Dourado", "Land %s hits with the Golden Rectangle"),
    "mentor_duel": ("Vencer o Desafio do Mestre (%s)", "Win the Master's Challenge (%s)"),
    "boss_kills": ("Derrotar %s chefes (você por perto)", "Defeat %s bosses (with you nearby)"),
}
for k, (tpt, ten) in TASKS.items():
    t(f"gui.{K}.{SB}.task.{k}", tpt, ten)
t(f"gui.{K}.{SB}.need.circle", "Círculo %s (você está no %s)", "Circle %s (you are at %s)")
t(f"gui.{K}.{SB}.need.parts", "Partes do Cadáver Santo: %s (você tem %s)", "Holy Corpse Parts: %s (you have %s)")
t(f"gui.{K}.{SB}.need.task", "%s — %s/%s", "%s — %s/%s")
t(f"gui.{K}.{SB}.req.circle", "Círculo %s", "Circle %s")
t(f"gui.{K}.{SB}.req.parts", "Partes do Cadáver Santo: %s (tem %s)", "Holy Corpse Parts: %s (have %s)")

# ------------------------------------------------------------------ aba "Rotação" do menu B
t(f"gui.{K}.{SB}.tab", "Rotação", "Rotation")
t(f"gui.{K}.{SB}.tab.title", "Lições do Mestre Rotari", "Master Rotari's Lessons")
t(f"gui.{K}.{SB}.tab.status", "Lição %s · Ato %s: %s", "Lesson %s · Act %s: %s")
t(f"gui.{K}.{SB}.tab.other", "Você não é um Steel Ball: as Partes do Cadáver ainda dão seus poderes, mas as lições e os Atos "
  "são da classe.", "You are not a Steel Ball: Holy Corpse Parts still grant their powers, but lessons and Acts belong to the class.")
t(f"gui.{K}.{SB}.tab.done", "aprendida", "learned")
t(f"gui.{K}.{SB}.tab.current", "próxima", "next")
t(f"gui.{K}.{SB}.tab.locked", "trancada", "locked")
t(f"gui.{K}.{SB}.tab.teaches", "Ensina: %s", "Teaches: %s")
t(f"gui.{K}.{SB}.tab.parts", "Cadáver Santo %s/%s", "Holy Corpse %s/%s")
t(f"gui.{K}.{SB}.tab.part_missing", "Ainda não encontrada — Sepulturas do Santo, baús antigos e chefes.",
  "Not found yet — Saint's Tombs, old chests and bosses.")
t(f"gui.{K}.{SB}.tab.bonus", "Bônus de classe", "Class bonuses")
t(f"gui.{K}.{SB}.tab.bonus.spin", "Giro Perfeito: +%s%% de dano nas magias com técnica de rotação.",
  "Perfect Spin: +%s%% damage on spells with a rotation technique.")
t(f"gui.{K}.{SB}.tab.bonus.mounted", "Montado: +25%% de Poder Mágico, bola arremessada +30%%, montaria 20%% mais rápida e sem "
  "dano de queda.", "Mounted: +25%% Spell Power, thrown ball +30%%, mount 20%% faster and immune to fall damage.")
t(f"gui.{K}.{SB}.tab.bonus.return", "A bola volta para a mão: apanhá-la devolve 1%% do Foco.",
  "The ball returns to your hand: catching it restores 1%% Focus.")
t(f"gui.{K}.{SB}.tab.hint", "O Mestre aparece de dia quando uma lição está pronta (ou use a Carta do Mestre). A Bússola do "
  "Santo aponta as Sepulturas.", "The Master shows up by day when a lesson is ready (or use the Master's Letter). The "
  "Saint's Compass points to the Tombs.")

# ------------------------------------------------------------------ mensagens
t(f"banner.{K}.{SB}.rank", "LIÇÃO %s — %s", "LESSON %s — %s")
t(f"toast.{K}.{SB}.rank", "Lição %s aprendida!", "Lesson %s learned!")
t(f"toast.{K}.{SB}.part", "Parte absorvida: %s", "Part absorbed: %s")
t(f"toast.{K}.{SB}.outfit", "Roupa: %s", "Outfit: %s")
t(f"toast.{K}.{SB}.outfit.sub", "Círculo %s alcançado", "Circle %s reached")
t(f"message.{K}.{SB}.rank_up", "O Mestre Rotari ensinou a Lição %s: %s!", "Master Rotari taught Lesson %s: %s!")
t(f"message.{K}.{SB}.glyph", "Técnica aprendida: %s", "Technique learned: %s")
t(f"message.{K}.{SB}.act", "Sua unha chegou ao Ato %s: %s!", "Your nail reached Act %s: %s!")
t(f"message.{K}.{SB}.outfit", "Círculo %s: o Mestre mandou a roupa de %s!", "Circle %s: the Master sent the %s outfit!")
t(f"message.{K}.{SB}.outfit.stats", "Defesa total %s", "Total defense %s")
t(f"message.{K}.{SB}.outfit.equipped", "A roupa nova já está no corpo (a antiga foi para o inventário).",
  "The new outfit is already on you (the old one went to your inventory).")
t(f"message.{K}.{SB}.outfit.inventory", "As peças foram para o inventário (você está usando outra coisa).",
  "The pieces went to your inventory (you are wearing something else).")
t(f"message.{K}.{SB}.part_owned", "Você já absorveu esta parte: %s.", "You already absorbed this part: %s.")
t(f"message.{K}.{SB}.part_absorbed", "%s se fundiu ao seu corpo! (%s/%s)", "%s fused with your body! (%s/%s)")
t(f"message.{K}.{SB}.part_lesson_ready", "Você já tem as partes para \"%s\". O Mestre vai querer ver isso.",
  "You now have the parts for \"%s\". The Master will want to see this.")
t(f"message.{K}.{SB}.relic_opened", "O relicário se abre: %s!", "The reliquary opens: %s!")
for d, (dpt, den) in {"n": ("norte", "north"), "ne": ("nordeste", "northeast"), "e": ("leste", "east"),
                       "se": ("sudeste", "southeast"), "s": ("sul", "south"), "sw": ("sudoeste", "southwest"),
                       "w": ("oeste", "west"), "nw": ("noroeste", "northwest")}.items():
    t(f"message.{K}.{SB}.compass.dir.{d}", dpt, den)
t(f"message.{K}.{SB}.compass.found", "Sepultura do Santo: %s blocos a %s", "Saint's Tomb: %s blocks to the %s")
t(f"message.{K}.{SB}.compass.none", "A agulha gira sem rumo... nenhuma sepultura por perto.",
  "The needle spins aimlessly... no tomb nearby.")
t(f"message.{K}.{SB}.mentor.arrive_first", "Um cavalo se aproxima a galope... um velho de chapéu de aba desce e acena para você.",
  "A horse approaches at a gallop... an old man in a brimmed hat dismounts and waves at you.")
t(f"message.{K}.{SB}.mentor.arrive", "O Mestre Rotari chegou: há uma lição pronta para você.",
  "Master Rotari has arrived: a lesson is ready for you.")
t(f"message.{K}.{SB}.mentor.already", "O Mestre já está por perto.", "The Master is already nearby.")
t(f"message.{K}.{SB}.mentor.letter", "Você escreve a carta e assobia... o Mestre chega em instantes.",
  "You write the letter and whistle... the Master arrives in a moment.")
t(f"message.{K}.{SB}.mentor.dodge", "O Mestre desvia girando. \"Fora do desafio, não.\"",
  "The Master dodges with a spin. \"Not outside the challenge.\"")
t(f"message.{K}.{SB}.whistle.busy", "Seu corcel está carregando outra pessoa.", "Your steed is carrying someone else.")
t(f"message.{K}.{SB}.whistle.called", "Seu corcel atendeu o apito!", "Your steed answered the whistle!")
t(f"message.{K}.{SB}.whistle.new", "Um corcel de corrida atendeu o apito: ele é seu agora.",
  "A racing steed answered the whistle: it's yours now.")
t(f"message.{K}.{SB}.duel.start", "Desafio do Mestre! Vença o giro dele (ninguém morre).",
  "The Master's Challenge! Beat his spin (nobody dies).")
t(f"message.{K}.{SB}.duel.lost", "O desafio acabou. O Mestre ajeita o chapéu: \"Volte quando girar melhor.\"",
  "The challenge is over. The Master adjusts his hat: \"Come back when you spin better.\"")
t(f"message.{K}.{SB}.duel.timeout", "O sol mudou de lugar e ninguém venceu. \"Outro dia.\"",
  "The sun moved and nobody won. \"Another day.\"")
t(f"message.{K}.{SB}.duel.phase2", "O Mestre gira a cura no próprio corpo e as bolas ficam douradas!",
  "The Master spins healing into his own body and his balls turn golden!")

# ------------------------------------------------------------------ comandos
t(f"command.{K}.{SB}.rank", "Lição do Steel Ball: %s (Ato %s)", "Steel Ball lesson: %s (Act %s)")
t(f"command.{K}.{SB}.status", "Lição %s · Ato %s · Partes %s/%s", "Lesson %s · Act %s · Parts %s/%s")
t(f"command.{K}.{SB}.ready", "Pronto para a lição \"%s\": fale com o Mestre.", "Ready for lesson \"%s\": talk to the Master.")
t(f"command.{K}.{SB}.parts", "Partes do Cadáver Santo absorvidas: %s", "Holy Corpse Parts absorbed: %s")

# ------------------------------------------------------------------ diálogos do Mestre
D = f"dialogue.{K}.{SB}"
t(f"{D}.mentor.intro1", "Então é você o garoto da bola. Hm. O braço é fraco, mas o olho é bom.",
  "So you're the kid with the ball. Hm. Weak arm, but a good eye.")
t(f"{D}.mentor.intro2", "Eu sou Rotari. Ensinei a rotação a médicos, carrascos e a um cavaleiro que atravessou o continente.",
  "I'm Rotari. I taught rotation to physicians, executioners and to a rider who crossed the continent.")
t(f"{D}.mentor.intro3", "O giro não é força. É proporção. Quando você entender isso, a bola vai voltar sozinha para a sua mão.",
  "The spin isn't strength. It's proportion. When you understand that, the ball will return to your hand by itself.")
t(f"{D}.mentor.intro4", "Pelo mundo há pedaços de um santo enterrado. Cada um que você reunir vai mudar o seu giro. Eu "
  "volto quando você estiver pronto — ou escreva.", "Across the world lie pieces of a buried saint. Each one you gather "
  "will change your spin. I'll come back when you're ready — or write.")
t(f"{D}.mentor.other1", "Você não gira, viajante. Mas tudo bem: os pedaços do santo ajudam qualquer um que os carregue.",
  "You don't spin, traveler. But that's fine: the saint's pieces help anyone who carries them.")
t(f"{D}.mentor.other2", "Se um Steel Ball escrever uma Lição para você, talvez aprenda uma ou duas voltas.",
  "If a Steel Ball writes you a Lesson, maybe you'll learn a turn or two.")
t(f"{D}.mentor.master1", "Não há mais nada que eu possa ensinar. A Rotação Infinita é sua.",
  "There's nothing left I can teach. Infinite Rotation is yours.")
t(f"{D}.mentor.master2", "Agora vá. Um giro perfeito não para — e você também não deveria.",
  "Now go. A perfect spin never stops — and neither should you.")
t(f"{D}.mentor.ready", "Muito bem. Você está pronto para \"%s\". Preste atenção.", "Very well. You're ready for \"%s\". Pay attention.")
TEACH = {
    1: ("Segure a bola. Gire com o pulso, não com o braço. Solte quando sentir o peso girar sozinho... isso. Agora as "
        "unhas: o mesmo giro, menor e mais rápido.", "Hold the ball. Spin with the wrist, not the arm. Release when you feel "
        "the weight spin by itself... there. Now the nails: the same spin, smaller and faster."),
    2: ("Monte. Sinta o cavalo: a força sobe pelas patas, pelas suas pernas, pela coluna... e sai pela mão. O galope é "
        "parte do giro.", "Mount up. Feel the horse: the power climbs through its legs, yours, your spine... and leaves "
        "through the hand. The gallop is part of the spin."),
    3: ("O giro que fecha uma ferida é o mesmo que abre uma pedra. Crave a bola no chão e deixe o mundo girar em volta dela.",
        "The spin that closes a wound is the same that opens a stone. Drive the ball into the ground and let the world "
        "spin around it."),
    4: ("Você me venceu de verdade. Então veja: o retângulo dourado. A proporção perfeita está em tudo — na concha, no "
        "cavalo, na sua mão. Gire nessa proporção.", "You truly beat me. So look: the golden rectangle. The perfect "
        "proportion is in everything — the shell, the horse, your hand. Spin in that proportion."),
    5: ("Com o santo quase inteiro em você, o giro não precisa mais acabar. Isso é o Ato 4. Quem for preso nele nunca "
        "chega ao fim da rotação.", "With the saint almost whole within you, the spin no longer needs to end. That is "
        "Act 4. Whoever is caught in it never reaches the end of the rotation."),
}
for n, (tpt, ten) in TEACH.items():
    t(f"{D}.mentor.teach{n}", tpt, ten)
t(f"{D}.mentor.duel1", "Você tem as partes e o Círculo. Falta uma coisa: provar o giro contra o meu.",
  "You have the parts and the Circle. One thing is missing: prove your spin against mine.")
t(f"{D}.mentor.duel2", "Quando fechar esta conversa, o desafio começa. Ninguém morre — mas eu não vou facilitar.",
  "When this conversation ends, the challenge begins. Nobody dies — but I won't go easy.")
t(f"{D}.mentor.lesson", "A próxima é \"%s\". %s", "The next one is \"%s\". %s")
t(f"{D}.mentor.missing", "Ainda falta:", "Still missing:")
t(f"{D}.mentor.parts_hint", "As partes do santo estão em Sepulturas do Santo (a Bússola aponta), às vezes em baús antigos de "
  "outras ruínas e com chefes. Abra os Relicários. O Coração é diferente: só bate para quem já carrega as outras 8 — dentro "
  "de um chefe.", "The saint's parts lie in Saint's Tombs (the Compass points), sometimes in old chests of other ruins and "
  "with bosses. Open the Reliquaries. The Heart is different: it only beats for one who already carries the other 8 — inside "
  "a boss.")
TIPS = {
    1: ("Arremesse a bola de aço ou use a forma Bola de Aço. A bola volta: treine até ela voltar sem você pensar.",
        "Throw the steel ball or use the Steel Ball form. It comes back: practice until it returns without you thinking."),
    2: ("Use o Apito do Corcel. Lute montado; a bola montada bate 30% mais forte.",
        "Use the Steed Whistle. Fight mounted; a mounted ball hits 30% harder."),
    3: ("Unhas em quantidade, curas com a Rotação Medicinal e um chefe de verdade. O giro cresce com prática.",
        "Nails in quantity, heals with Healing Rotation and a real boss. The spin grows with practice."),
    4: ("Derrote chefes. Quando estiver pronto, eu mesmo vou testar você.",
        "Defeat bosses. When you're ready, I'll test you myself."),
    5: ("Use o Retângulo Dourado nas magias, lute montado e enfrente chefes. E reúna o santo quase inteiro.",
        "Use the Golden Rectangle in your spells, fight mounted and face bosses. And gather the saint almost whole."),
}
for n, (tpt, ten) in TIPS.items():
    t(f"{D}.mentor.tip{n}", tpt, ten)
t(f"{D}.duel.yield1", "Chega! Chega... *ajeita o chapéu* Bom giro. Muito bom giro.", "Enough! Enough... *adjusts his hat* Good spin. Very good spin.")
t(f"{D}.duel.yield2", "Fale comigo de novo: você ganhou a lição.", "Talk to me again: you've earned the lesson.")


# ------------------------------------------------------------------ v1.0 — Técnicas do Santo (pacote de técnicas de anime)
TECH = {
    "tusk_act1": ("Tusk Ato 1 — Unhas-Bala", "Tusk Act 1 — Nail Bullets",
                  "Forma de origem (Ato 1): as dez unhas viram balas — 5 (+1 por Dividir, até 10) disparadas uma a uma, "
                  "alternando as mãos. Cada unha voa em SACA-ROLHAS e faz o alvo RODOPIAR (Girado e o corpo torcido). Cada "
                  "unha é uma cópia dos efeitos (60% do poder). Libera: Lição I + Braço Esquerdo do Santo (ou 2 Partes).",
                  "Origin form (Act 1): your ten nails become bullets — 5 (+1 per Split, up to 10) fired one by one, "
                  "alternating hands. Each nail flies in a CORKSCREW and makes the target SPIN (Spun and body twisted). Each "
                  "nail is a copy of the effects (60% power). Unlock: Lesson I + Saint's Left Arm (or 2 Parts)."),
    "tusk_act2": ("Tusk Ato 2 — Rotação Dourada", "Tusk Act 2 — Golden Spin",
                  "Forma de origem (Ato 2): dispara a unha dourada. Onde ela acerta fica um BURACO ESPIRAL que PERSEGUE o inimigo "
                  "por 6 s (×Estender), deslizando por cima de tudo; quando alcança, morde a cada 0,75 s (efeitos a 60%), "
                  "deixa Girado e prende o pé. Libera: Lição II + 3 Partes do Cadáver.",
                  "Origin form (Act 2): fires the golden nail. Wherever it hits, a SPIRAL HOLE is left that CHASES the enemy for "
                  "6 s (×Extend), sliding over everything; when it catches up it bites every 0.75 s (effects at 60%), "
                  "leaves Spun and grips the feet. Unlock: Lesson II + 3 Corpse Parts."),
    "tusk_act3": ("Tusk Ato 3 — Buraco de Minhoca", "Tusk Act 3 — Wormhole",
                  "Forma de origem (Ato 3): você dispara a unha em SI MESMO, mergulha no buraco e atravessa o espaço até o "
                  "ponto mirado (24 blocos, ×Acelerar) ou até as COSTAS do inimigo mirado. Dentro do buraco nada acerta você "
                  "(escudo de 2 s). Ao sair, a espiral aplica os efeitos nos inimigos a até 3,5 blocos (×Expandir) e os "
                  "deixa Girados. Libera: Lição III + 5 Partes.",
                  "Origin form (Act 3): you shoot the nail into YOURSELF, dive into the hole and cross space to the aimed "
                  "point (24 blocks, ×Accelerate) or to the BACK of the aimed enemy. Inside the hole nothing hits you (2 s "
                  "shield). On exit, the spiral applies the effects to enemies within 3.5 blocks (×Expand) and leaves them "
                  "Spun. Unlock: Lesson III + 5 Parts."),
    "scan_ball": ("Bola Escâner", "Scanner Ball",
                  "Forma de origem: lança a bola cravejada de luzes, que para no ar e vibra — 3 ondas de varredura (raio 20, "
                  "×Expandir; com os Olhos do Santo ×1,5). Todos os inimigos brilham por 10 s (através das paredes); os 3 "
                  "mais perigosos (+1 por Dividir) ganham PONTO FRACO (Exposto II) e recebem os efeitos. Funciona sem "
                  "efeito. Libera: Lição I.",
                  "Origin form: throws the light-studded ball, which stops mid-air and vibrates — 3 scan waves (radius 20, ×Expand; "
                  "×1.5 with the Saint's Eyes). Every enemy glows for 10 s (through walls); the 3 most dangerous (+1 per "
                  "Split) get a WEAK POINT (Exposed II) and receive the effects. Works with no effect. Unlock: Lesson I."),
    "golden_gallop": ("Lição do Cavalo — Corrida Dourada", "Horse Lesson — Golden Gallop",
                      "Forma de origem que lança o galope dourado: montado, o cavalo entra no galope por 8 s (×Estender) — Velocidade III, "
                      "Salto II, Resistência II e um rastro de espiral dourada; quem ele atropela gira, é jogado de lado e "
                      "recebe os efeitos (80%; sem efeitos, dano 3). A pé: metade do tempo, Velocidade II e atropelo com o "
                      "corpo (50%). Libera: Lição II.",
                      "Origin form: mounted, your horse enters the golden gallop for 8 s (×Extend) — Speed III, Jump II, "
                      "Resistance II and a golden spiral trail; whoever it tramples spins, is thrown aside and receives the "
                      "effects (80%; with no effects, 3 damage). On foot: half the time, Speed II and a body-check trample "
                      "(50%). Unlock: Lesson II."),
    "spin_mend": ("Rotação Restauradora", "Restoring Rotation",
                  "Efeito de origem (benéfico): a técnica máxima da família de médicos — cura 10 na hora e +2 por segundo "
                  "por 5 s, tira TODOS os efeitos ruins e condições (queimando, congelado, sangrando...), dá Absorção II e "
                  "Resistência I por 5 s. Inverter, em inimigos: dano 6, tira todos os efeitos bons e deixa Fraco. "
                  "Libera: Lição III + 4 Partes.",
                  "Origin effect (beneficial): the physicians' ultimate technique — heals 10 at once and +2 per second for "
                  "5 s, removes ALL harmful effects and conditions (burning, frozen, bleeding...), grants Absorption II and "
                  "Resistance I for 5 s. Invert, on enemies: 6 damage, strips every beneficial effect and leaves Weakness. "
                  "Unlock: Lesson III + 4 Parts."),
    "ball_breaker": ("Esfera Quebrantadora", "Ball Breaker Sphere",
                     "Efeito de origem (homenagem ao Ball Breaker): a rotação dourada perfeita que DESGASTA. O espectro da "
                     "bola paira sobre o alvo por 8 s (×Estender) e a cada segundo o envelhece: dano crescente (1 → 4,5 "
                     "base), Lentidão e Fraqueza que pioram, Cansaço e defesa quebrada (Exposto). Chefes desgastam mais "
                     "devagar. Libera: Lição V + 7 Partes.",
                     "Origin effect (homage to Ball Breaker): the perfect golden rotation that WEARS DOWN. The ball's "
                     "specter hovers over the target for 8 s (×Extend) and ages it every second: growing damage (1 → 4.5 "
                     "base), worsening Slowness and Weakness, Mining Fatigue and broken defense (Exposed). Bosses wear "
                     "down slower. Unlock: Lesson V + 7 Parts."),
}
for gid, (npt, nen, dpt, den) in TECH.items():
    t(f"glyph.{K}.{gid}", npt, nen)
    t(f"glyph.{K}.{gid}.desc", dpt, den)

t(f"entity.{K}.steelball_tusk_nail", "Unha de Tusk", "Tusk Nail")
t(f"entity.{K}.steelball_spiral_hole", "Buraco Espiral", "Spiral Hole")
t(f"entity.{K}.steelball_ball_breaker", "Esfera Quebrantadora", "Ball Breaker Sphere")
t(f"entity.{K}.steelball_scan_ball", "Bola Escâner", "Scanner Ball")

t(f"legend.{K}.steelball_golden_chase", "Ato 2 — O Buraco que Persegue", "Act 2 — The Hole that Hunts")
t(f"legend.{K}.steelball_golden_chase.desc", "A unha dourada com o Retângulo Dourado: o buraco caça o alvo, gira e prende os músculos.",
  "The golden nail with the Golden Rectangle: the hole hunts the target, spins it and locks its muscles.")
t(f"legend.{K}.steelball_wormhole", "Ato 3 — Travessia do Buraco de Minhoca", "Act 3 — Wormhole Crossing")
t(f"legend.{K}.steelball_wormhole.desc", "Atire em si mesmo, atravesse o espaço e saia girando e perfurando no meio dos inimigos.",
  "Shoot yourself, cross space and come out spinning and drilling among your enemies.")
t(f"legend.{K}.steelball_ball_breaker", "Esfera Quebrantadora", "Ball Breaker Sphere")
t(f"legend.{K}.steelball_ball_breaker.desc", "A bola de aço no Retângulo Dourado leva o espectro que desgasta o alvo até o fim.",
  "The steel ball in the Golden Rectangle carries the specter that wears the target down to the end.")

t(f"callout.{K}.{SB}.tusk_act1", "TUSK — ATO 1: UNHAS-BALA!", "TUSK — ACT 1: NAIL BULLETS!")
t(f"callout.{K}.{SB}.tusk_act2", "TUSK — ATO 2", "TUSK — ACT 2")
t(f"callout.{K}.{SB}.tusk_act2.sub", "Rotação Dourada: o buraco já está em você", "Golden Spin: the hole is already on you")
t(f"callout.{K}.{SB}.tusk_act3", "TUSK — ATO 3", "TUSK — ACT 3")
t(f"callout.{K}.{SB}.tusk_act3.sub", "Buraco de Minhoca", "Wormhole")
t(f"callout.{K}.{SB}.scan_ball", "BOLA ESCÂNER!", "SCANNER BALL!")
t(f"callout.{K}.{SB}.golden_gallop", "CORRIDA DOURADA", "GOLDEN GALLOP")
t(f"callout.{K}.{SB}.golden_gallop.sub", "A Lição do Cavalo: a força vem do chão", "The Horse Lesson: power comes from the ground")
t(f"callout.{K}.{SB}.golden_gallop.foot", "A pé, só metade do galope...", "On foot, only half the gallop...")
t(f"callout.{K}.{SB}.ball_breaker", "ESFERA QUEBRANTADORA", "BALL BREAKER")
t(f"callout.{K}.{SB}.ball_breaker.sub", "A rotação desgasta até o tempo do corpo", "The rotation wears down even the body's time")

t(f"gui.{K}.{SB}.tech.title", "Técnicas do Santo", "Saint's Techniques")
t(f"gui.{K}.{SB}.tech.hint", "Os Atos de Tusk evoluem com as Partes do Cadáver Santo: cumpra a lição e reúna as Partes e a "
  "técnica é aprendida sozinha.", "Tusk's Acts evolve with the Holy Corpse Parts: pass the lesson and gather the Parts and the "
  "technique is learned on its own.")
t(f"gui.{K}.{SB}.tech.req", "Lição %s · %s Partes", "Lesson %s · %s Parts")
t(f"gui.{K}.{SB}.tech.req_alt", " (ou %s)", " (or %s)")
t(f"banner.{K}.{SB}.tech", "TÉCNICA DO SANTO — %s", "SAINT'S TECHNIQUE — %s")
t(f"toast.{K}.{SB}.tech", "Nova técnica do Santo!", "New Saint's technique!")
t(f"message.{K}.{SB}.tech", "As Partes do Santo despertaram uma técnica: %s!", "The Saint's Parts awakened a technique: %s!")
t(f"message.{K}.{SB}.scan", "Bola Escâner: %s inimigos revelados, %s pontos fracos", "Scanner Ball: %s enemies revealed, %s weak points")


# ------------------------------------------------------------------ v1.0 — O CORAÇÃO DO CADÁVER SANTO e o LOVE TRAIN
t(f"glyph.{K}.steelball_love_train", "Love Train", "Love Train")
t(f"glyph.{K}.steelball_love_train.desc",
  "Forma G (lendária) do Santo inteiro: por 12 s (×Estender, até 15 s) você fica envolto numa cortina de luz dimensional que "
  "o acompanha. TODO dano e todo efeito ruim que iriam acertar você viram DESGRAÇA e vão para outro lugar: para o inimigo "
  "mais próximo (até 8 blocos) ou, sem ninguém perto, para um inimigo ao acaso a até 24 blocos. Projéteis se curvam para "
  "longe. Os efeitos da magia pulsam (1×/s) em quem recebe a desgraça; funciona sem efeito nenhum. Só o que vem de DENTRO "
  "da luz atravessa: Acerto Garantido de domínios, dano verdadeiro/vazio e a Rotação Dourada (Rotação Infinita ou "
  "Retângulo Dourado). Libera: absorver o Coração do Cadáver Santo (as 9 Partes).",
  "G form (legendary) of the whole Saint: for 12 s (×Extend, up to 15 s) you are wrapped in a dimensional curtain of light "
  "that follows you. ALL damage and every harmful effect that would hit you become MISFORTUNE and go somewhere else: to the "
  "nearest enemy (up to 8 blocks) or, with no one close, to a random enemy within 24 blocks. Projectiles bend away. The "
  "spell's effects pulse (1×/s) on whoever receives the misfortune; works with no effect at all. Only what comes from "
  "INSIDE the light gets through: domain Sure-Hit, true/void damage and the Golden Spin (Infinite Rotation or Golden "
  "Rectangle). Unlock: absorb the Heart of the Holy Corpse (all 9 Parts).")
t(f"entity.{K}.steelball_love_train", "Love Train", "Love Train")
t(f"legend.{K}.steelball_love_train_heart", "Love Train — A Luz do Coração", "Love Train — Light of the Heart")
t(f"legend.{K}.steelball_love_train_heart.desc",
  "A cortina de luz do Santo inteiro: toda desgraça vai para outro lugar, e quem a recebe gira e tem os músculos travados.",
  "The whole Saint's curtain of light: all misfortune goes somewhere else, and whoever receives it spins and has its "
  "muscles locked.")
t(f"callout.{K}.{SB}.love_train", "LOVE TRAIN", "LOVE TRAIN")
t(f"callout.{K}.{SB}.love_train.sub", "Toda a desgraça vai para outro lugar", "All misfortune goes somewhere else")
t(f"callout.{K}.{SB}.love_train.awaken", "O Coração do Cadáver Santo despertou", "The Heart of the Holy Corpse has awakened")
t(f"callout.{K}.{SB}.heart_found", "O CORAÇÃO BATE", "THE HEART BEATS")
t(f"callout.{K}.{SB}.heart_found.sub", "O Coração do Cadáver Santo apareceu!", "The Heart of the Holy Corpse has appeared!")
t(f"banner.{K}.{SB}.love_train", "LOVE TRAIN — O SANTO ESTÁ INTEIRO", "LOVE TRAIN — THE SAINT IS WHOLE")
t(f"toast.{K}.{SB}.love_train", "O Santo está inteiro!", "The Saint is whole!")
t(f"message.{K}.{SB}.love_train.awaken",
  "O CORAÇÃO DO CADÁVER SANTO bate junto com o seu... o Santo está inteiro. A luz desce: você despertou o LOVE TRAIN!",
  "The HEART OF THE HOLY CORPSE beats along with yours... the Saint is whole. The light descends: you awakened the LOVE TRAIN!")
t(f"message.{K}.{SB}.love_train.howto",
  "Monte o Love Train (forma G) no Grimório — ou leia o Pergaminho Lendário \"Love Train — A Luz do Coração\". Usa a carga de "
  "ultimate.",
  "Build Love Train (G form) in the Grimoire — or read the Legendary Scroll \"Love Train — Light of the Heart\". Uses ultimate "
  "charge.")
t(f"message.{K}.{SB}.love_train.pierced", "Acerto Garantido: o domínio atravessa a luz do Love Train!",
  "Sure-Hit: the domain pierces the Love Train's light!")
t(f"message.{K}.{SB}.love_train.fading", "A luz do Love Train está se apagando (3 s)...", "The Love Train's light is fading (3 s)...")
t(f"message.{K}.{SB}.love_train.end", "O Love Train se desfaz. Desgraças desviadas: %s.", "The Love Train fades. Misfortunes redirected: %s.")
t(f"message.{K}.{SB}.heart_found",
  "Dentro do chefe, algo continua batendo... o CORAÇÃO DO CADÁVER SANTO! Pegue-o antes que pare.",
  "Inside the boss, something keeps beating... the HEART OF THE HOLY CORPSE! Grab it before it stops.")
t(f"message.{K}.{SB}.heart_hint",
  "As 8 partes estão no seu corpo. O Coração não está em relicários: ele bate dentro de um grande inimigo. Derrote um chefe.",
  "All 8 parts are in your body. The Heart isn't in reliquaries: it beats inside a great enemy. Defeat a boss.")
t(f"message.{K}.{SB}.heart_rejects", "O Coração rejeita um corpo incompleto (%s/%s partes). Ele quer ser o último.",
  "The Heart rejects an incomplete body (%s/%s parts). It wants to be the last.")
t(f"message.{K}.{SB}.heart_incomplete", "O Coração bate... mas falta algo do Santo para a luz descer.",
  "The Heart beats... but something of the Saint is still missing for the light to descend.")
t(f"tooltip.{K}.{SB}.part.final", "A última parte do Cadáver Santo — a mais rara.", "The last part of the Holy Corpse — the rarest.")
t(f"tooltip.{K}.{SB}.part.love_train", "O Santo inteiro desperta o LOVE TRAIN.", "The whole Saint awakens the LOVE TRAIN.")
t(f"tooltip.{K}.{SB}.part.final_use", "Só se funde a quem já tem as outras partes (%s/%s).",
  "Only fuses with one who already has the other parts (%s/%s).")
t(f"gui.{K}.{SB}.love_train.req", "%s · Santo inteiro (%s/%s Partes)", "%s · Whole Saint (%s/%s Parts)")
t(f"gui.{K}.{SB}.love_train.info",
  "Forma G: 12 s numa cortina de luz — todo dano e efeito ruim vira desgraça e vai para o inimigo mais próximo; projéteis se "
  "curvam. Só Acerto Garantido, dano verdadeiro e a Rotação Dourada atravessam.",
  "G form: 12 s in a curtain of light — all damage and harmful effects become misfortune and go to the nearest enemy; "
  "projectiles bend away. Only Sure-Hit, true damage and the Golden Spin get through.")
t(f"gui.{K}.{SB}.tab.heart_missing",
  "A última parte. Só aparece quando as outras 8 estão no seu corpo: aí ela bate dentro de um chefe derrotado por perto.",
  "The last part. It only appears once the other 8 are in your body: then it beats inside a boss defeated nearby.")
t(f"gui.{K}.{SB}.tab.heart_ready", "O Coração já bate por você: derrote um chefe e pegue-o!",
  "The Heart already beats for you: defeat a boss and grab it!")


def write(lang_dir):
    os.makedirs(lang_dir, exist_ok=True)
    for name, data in (("pt_br", PT), ("en_us", EN)):
        with open(os.path.join(lang_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(dict(sorted(data.items())), f, indent=2, ensure_ascii=False)
            f.write("\n")
    print(f"  traducoes: {len(PT)} chaves")
