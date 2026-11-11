"""
TECNICAS AVANCADAS do modulo class_quincy (pacote de tecnicas iconicas, homenagens a Bleach): assets + traducoes.
Uso (na raiz do projeto):  py -I tools/gen/class_quincy_techniques.py

Gera:
  - textures/entity/quincy_light_cage.png          grade vertical de luz do Sankt Zwinger (clara no meio; tingida no Java)
  - textures/entity/quincy_sankt_zwinger_floor.png a estrela Quincy no chao da gaiola (64x64, transparente fora)
  - Luva Sanrei: textures/item/quincy_sanrei_glove.png + models/item/quincy_sanrei_glove.json + receita
  - traducoes (pt_br + en_us) das tecnicas novas, das 5 Letras novas, da luva e das mensagens — MESCLADAS em
    src/main/lang/class_quincy/*.json (chaves existentes com o mesmo texto ficam; o arquivo mantem a ordem e as quebras
    de linha originais e as chaves novas vao no fim)
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, item_model, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
LANG = os.path.join(ROOT, "src/main/lang/class_quincy")


def H(h, a=255):
    return hex_rgba(h, a)


def clamp(v):
    return max(0, min(255, int(v)))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  Texturas
# =====================================================================================
def cage_bar():
    """16x16: u atravessa a grade (clara no meio), v corre na altura (anéis a cada 4 px)."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = abs((x + 0.5) - 8) / 8.0
            prof = max(0.0, 1 - d ** 1.4)
            ring = 1.0 if y % 4 else 1.25
            w = clamp(210 + 45 * prof)
            c.set(x, y, (w, w, w, clamp(255 * min(1.0, prof * ring))))
    c.save(os.path.join(A, "textures/entity", "quincy_light_cage.png"))


def zwinger_floor():
    """64x64: circulo duplo e a estrela de cinco pontas, com brilho suave; transparente fora."""
    n = 64
    c = Canvas(n, n)
    cx = cy = n / 2
    R = n / 2 - 1.5
    pts = []
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        pts.append((cx + R * 0.94 * math.cos(a), cy + R * 0.94 * math.sin(a)))
    order = [0, 2, 4, 1, 3, 0]
    segs = [(pts[a], pts[b]) for a, b in zip(order, order[1:])]

    def seg_dist(px, py, a, b):
        (x0, y0), (x1, y1) = a, b
        dx, dy = x1 - x0, y1 - y0
        t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / (dx * dx + dy * dy)))
        return math.hypot(px - (x0 + t * dx), py - (y0 + t * dy))

    for y in range(n):
        for x in range(n):
            px, py = x + 0.5, y + 0.5
            r = math.hypot(px - cx, py - cy)
            a = 0.0
            if abs(r - R) < 1.2 or abs(r - R * 0.86) < 0.7:
                a = 1.0
            elif r < R:
                dmin = min(seg_dist(px, py, s0, s1) for s0, s1 in segs)
                if dmin < 0.9:
                    a = 1.0
                elif dmin < 2.6:
                    a = 0.35 * (1 - (dmin - 0.9) / 1.7)
                else:
                    a = 0.06
            if a > 0:
                col = mix(H("FFFFFF"), H("8FD0FF"), 0.0 if a >= 1 else 0.6)
                c.set(x, y, (col[0], col[1], col[2], clamp(255 * a)))
    c.save(os.path.join(A, "textures/entity", "quincy_sankt_zwinger_floor.png"))


def sanrei_glove():
    """Luva de treino: couro branco-azulado com a cruz Quincy no dorso e faixas de prata."""
    c = Canvas(16, 16)
    W = [H("8A93A8"), H("BCC6D8"), H("E2E8F2"), H("F6F9FF")]
    S = [H("4A505E"), H("8A92A2"), H("C6CCD8")]
    # palma e dedos (mão aberta, levemente diagonal)
    c.rect(4, 7, 11, 13, W[1])
    c.rect(5, 8, 10, 12, W[2])
    for i, (x, top) in enumerate(((4, 3), (6, 2), (8, 2), (10, 3))):
        c.rect(x, top, x + 1, 7, W[2] if i % 2 else W[1])
        c.set(x, top, W[3])
    c.rect(12, 8, 13, 10, W[1])  # polegar
    # punho com faixas de prata
    c.rect(4, 13, 11, 15, S[1])
    c.rect(4, 14, 11, 14, S[2])
    # cruz Quincy no dorso
    c.rect(7, 8, 8, 12, H("3576D0"))
    c.rect(5, 9, 10, 9, H("3576D0"))
    c.set(7, 8, H("8FD0FF"))
    c.set(8, 9, H("CBE6FF"))
    c.outline(darken(W[0], 0.5))
    c.save(os.path.join(A, "textures/item", "quincy_sanrei_glove.png"))
    item_model(A, "quincy_sanrei_glove")
    write_json(os.path.join(D, "recipe/quincy_sanrei_glove.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["LSL", "LEL", " T "],
        "key": {"L": {"item": "minecraft:leather"}, "S": {"item": "minecraft:string"},
                "E": {"item": "isekai_mahou:essence_light"}, "T": {"item": "isekai_mahou:quincy_silver_tube"}},
        "result": {"id": "isekai_mahou:quincy_sanrei_glove", "count": 1}})


# =====================================================================================
#  Traducoes
# =====================================================================================
T = {}


def k(key, pt, en):
    if key in T:
        raise RuntimeError("chave duplicada: " + key)
    T[key] = (pt, en)


def pct(s):
    return s.replace("%", "%%")


M = "message.isekai_mahou.quincy."
S = "screen.isekai_mahou.quincy."
L = "quincy.isekai_mahou.letter."
R = "quincy.isekai_mahou.req."

k("item.isekai_mahou.quincy_sanrei_glove", "Luva Sanrei", "Sanrei Glove")
k("tooltip.isekai_mahou.quincy_sanrei_glove.1", "Na mão: técnicas Quincy saem com 70%% do poder (o peso do treino).",
  "Held: Quincy techniques come out at 70%% power (the weight of training).")
k("tooltip.isekai_mahou.quincy_sanrei_glove.2", "Cada inimigo derrotado com elas conta para a Quincy: Letzt Stil (rank V).",
  "Every enemy defeated with them counts toward Quincy: Letzt Stil (rank V).")
k("tooltip.isekai_mahou.quincy_sanrei_glove.progress", "Treino: %s/%s", "Training: %s/%s")
k("entity.isekai_mahou.quincy_light_cage", "Sankt Zwinger", "Sankt Zwinger")

GLYPHS = {
    "quincy_heilig_bogen": (
        "Heilig Bogen (Arco Sagrado)", "Heilig Bogen (Sacred Bow)",
        "FORMA Quincy (rank IV). Depois de 1 s armando, um ARCO DE LUZ GIGANTE se abre na sua frente e dispara uma "
        "flecha-lança central enorme (perfura 4) e um leque de 4 flechas grandes (perfuram 3); +2 flechas por Dividir. Cada "
        "flecha é uma cópia com os efeitos. Acelerar/Perfurar/Teleguiado/Expandir valem.",
        "Quincy FORM (rank IV). After 1 s drawing, a GIANT BOW OF LIGHT opens in front of you and fires a huge central "
        "spear-arrow (pierces 4) and a fan of 4 large arrows (pierce 3); +2 arrows per Split. Each arrow is a copy with "
        "the effects. Accelerate/Pierce/Homing/Expand apply."),
    "quincy_sankt_zwinger": (
        "Sankt Zwinger (Gaiola Sagrada)", "Sankt Zwinger (Sacred Cage)",
        "FORMA Quincy (rank IV + 80 usos da Letra). Cinco pilares de luz caem na estrela Quincy no ponto mirado e erguem "
        "uma GAIOLA de grades de Reishi (raio 5, +1,5 por Expandir) por 6 s (Estender aumenta): inimigos lá dentro não "
        "saem e, a cada 0,5 s, lâminas de luz RETALHAM todos eles com os efeitos. Custo: pulsos × duração (moderado).",
        "Quincy FORM (rank IV + 80 Letter uses). Five pillars of light fall on the Quincy star at the aimed spot and raise "
        "a CAGE of Reishi bars (radius 5, +1.5 per Expand) for 6 s (Extend increases it): enemies inside cannot leave "
        "and, every 0.5 s, blades of light SHRED all of them with the effects. Cost: pulses × duration (moderated)."),
    "quincy_antithesis": (
        "A Antítese", "The Antithesis",
        "EFEITO Quincy (rank IV + 350 absorções). INVERTE o que aconteceu entre você e o alvo nos últimos 10 s: você se cura "
        "do dano que sofreu e o alvo o recebe (até 60% da vida dele, chefes 20%); o alvo se cura de METADE do dano que "
        "sofreu e essa metade passa para você (nunca mata). Use quando estiver mais ferido que o inimigo! Sem feridas: 2 de "
        "dano.",
        "Quincy EFFECT (rank IV + 350 absorptions). INVERTS what happened between you and the target in the last 10 s: you "
        "heal the damage you took and the target receives it (up to 60% of its health, bosses 20%); the target heals HALF "
        "the damage it took and that half moves to you (never lethal). Use it when you are more wounded than the enemy! "
        "No wounds: 2 damage."),
    "quincy_letzt_stil": (
        "Quincy: Letzt Stil", "Quincy: Letzt Stil",
        "FORMA G (Quincy, ultimate; rank V + treino com a Luva Sanrei). Por 30 s o Quincy libera TODO o Reishi: técnicas "
        "Quincy ×2, Força II, Velocidade II, Resistência I, Regeneração I, plana com asas enormes de luz dourada, engole "
        "todo projétil a 8 blocos e a cada 0,75 s um leque de 6 flechas teleguiadas gigantes cai nos inimigos a 24 blocos "
        "(os efeitos). O PREÇO: ao acabar, vida em 50%, Fraqueza II e 10 minutos SEM PODER QUINCY.",
        "G FORM (Quincy, ultimate; rank V + Sanrei Glove training). For 30 s the Quincy unleashes ALL their Reishi: Quincy "
        "techniques ×2, Strength II, Speed II, Resistance I, Regeneration I, glides on huge wings of golden light, swallows "
        "every projectile within 8 blocks and every 0.75 s a fan of 6 giant homing arrows falls on enemies within 24 "
        "blocks (the effects). THE PRICE: when it ends, health at 50%, Weakness II and 10 minutes WITHOUT QUINCY POWER."),
    "quincy_almighty": (
        "O Todo-Poderoso", "The Almighty",
        "FORMA G (Quincy, ultimate; rank V + 8 Auswählen + 6 chefes). Por 20 s você VÊ O FUTURO: 45% de chance de esquivar "
        "de cada golpe (passo para o lado com imagem residual); quem vai atacar você brilha e a linha do golpe/trajetória "
        "aparece antes; e a cada 5 s o primeiro golpe pesado (≥ 20% da vida, ou letal) é REESCRITO — anulado e devolvido a "
        "quem atacou, com os efeitos. Funciona sem efeitos.",
        "G FORM (Quincy, ultimate; rank V + 8 Auswählen + 6 bosses). For 20 s you SEE THE FUTURE: 45% chance to dodge each "
        "hit (a sidestep with an afterimage); whoever is about to attack you glows and the line of the blow/trajectory shows "
        "up beforehand; and every 5 s the first heavy blow (≥ 20% of your health, or lethal) is REWRITTEN — cancelled and "
        "sent back to the attacker, with the effects. Works without effects."),
}
for gid, (npt, nen, dpt, den) in GLYPHS.items():
    k("glyph.isekai_mahou." + gid, npt, nen)
    k("glyph.isekai_mahou." + gid + ".desc", pct(dpt), pct(den))

k("legend.isekai_mahou.quincy_heilig_bogen_legend", "Heilig Bogen", "Heilig Bogen")
k("legend.isekai_mahou.quincy_sankt_zwinger_legend", "Sankt Zwinger", "Sankt Zwinger")
k("legend.isekai_mahou.quincy_letzt_stil_legend", "Quincy: Letzt Stil", "Quincy: Letzt Stil")

LETTERS = {
    "burnt": ("O Queimado", "The Burnt",
              "A Schrift incendeia o alvo por 4 s e uma labareda queima quem está a 2 blocos.",
              "The Schrift sets the target ablaze for 4 s and a flare burns anyone within 2 blocks.",
              "Passiva: você nunca arde por mais de 2 s.", "Passive: you never burn for more than 2 s."),
    "jail": ("A Prisão", "The Jail",
             "A Schrift ergue grades de luz em volta do alvo: Enraizado por 2 s (chefes 0,75 s).",
             "The Schrift raises bars of light around the target: Rooted for 2 s (bosses 0.75 s).",
             "Passiva: você guarda +4 de Reishi.", "Passive: you store +4 Reishi."),
    "xaxis": ("O Eixo X", "The X-Axis",
              "A Schrift atravessa o alvo num traço reto que segue 10 blocos atrás dele, ferindo tudo no caminho.",
              "The Schrift pierces the target in a straight line that continues 10 blocks behind it, hurting everything in its path.",
              "Passiva: as flechas do Arco Espiritual atravessam +1 alvo.", "Passive: Spirit Bow arrows pierce +1 target."),
    "balance": ("O Equilíbrio", "The Balance",
                "A Schrift cura você em 40%% do dano causado e passa um efeito ruim seu para o alvo.",
                "The Schrift heals you for 40%% of the damage dealt and passes one of your harmful effects to the target.",
                "Passiva: abaixo de 50%% da vida, você recebe 8%% menos dano.", "Passive: below 50%% health, you take 8%% less damage."),
    "deathdealing": ("O Golpe Mortal", "The Deathdealing",
                     "A Schrift envenena: Veneno II e Murchar por 4 s.",
                     "The Schrift poisons: Poison II and Wither for 4 s.",
                     "Passiva: você é imune a veneno.", "Passive: you are immune to poison."),
}
for lid, (npt, nen, dpt, den, ppt, pen) in LETTERS.items():
    k(L + lid, npt, nen)
    k(L + lid + ".desc", dpt, den)
    k(L + lid + ".passive", ppt, pen)

k(R + "rank", "Rank %s do Auswählen", "Auswählen rank %s")
k(R + "sanrei", "Abates com a Luva Sanrei: %s", "Kills with the Sanrei Glove: %s")

MSG = {
    "bogen_shout": ("Heilig Bogen!", "Heilig Bogen!"),
    "zwinger_shout": ("Sankt Zwinger!", "Sankt Zwinger!"),
    "antithesis_shout": ("The Antithesis!", "The Antithesis!"),
    "letzt_shout": ("%s: \"Quincy... LETZT STIL!\"", "%s: \"Quincy... LETZT STIL!\""),
    "almighty_shout": ("%s abre os olhos do futuro: O TODO-PODEROSO!", "%s opens the eyes of the future: THE ALMIGHTY!"),
    "almighty_rewrite": ("O futuro foi reescrito!", "The future has been rewritten!"),
    "tech_announce": ("%s domina uma técnica avançada: %s!", "%s masters an advanced technique: %s!"),
    "tech_toast": ("Técnica avançada", "Advanced technique"),
    "tech_learned": ("Nova técnica Quincy: %s! Monte-a no Grimório.", "New Quincy technique: %s! Put it together in the Grimoire."),
    "sanrei_progress": ("Treino da Luva Sanrei: %s/%s", "Sanrei Glove training: %s/%s"),
    "powerless_start": ("A Letzt Stil queimou todo o seu Reishi. Você ficará sem poder Quincy por %s minutos.",
                        "Letzt Stil burned all your Reishi. You will be without Quincy power for %s minutes."),
    "powerless_block": ("Sem poder Quincy... (%s)", "No Quincy power... (%s)"),
    "powerless_end": ("O seu Reishi voltou!", "Your Reishi has returned!"),
}
for key, (pt, en) in MSG.items():
    k(M + key, pt, en)

SCREEN = {
    "techniques": ("Técnicas Avançadas", "Advanced Techniques"),
    "tech_known": ("Aprendida", "Learned"),
    "tech_ready": ("Pronta!", "Ready!"),
    "powerless": ("Sem poder Quincy (Letzt Stil): %s", "No Quincy power (Letzt Stil): %s"),
    "tech_how.heilig_bogen": ("Chega sozinha no rank IV do Auswählen.", "Comes on its own at Auswählen rank IV."),
    "tech_how.sankt_zwinger": ("Chega sozinha no rank IV, depois de usar a sua Letra 80 vezes.",
                               "Comes on its own at rank IV, after using your Letter 80 times."),
    "tech_how.antithesis": ("Chega sozinha no rank IV, depois de 350 absorções.", "Comes on its own at rank IV, after 350 absorptions."),
    "tech_how.letzt_stil": ("Treine com a LUVA SANREI (rank IV ou bancada) na mão: técnicas mais fracas, mas cada abate conta. "
                            "150 abates no rank V liberam a forma final.",
                            "Train with the SANREI GLOVE (rank IV or crafting) in hand: weaker techniques, but every kill counts. "
                            "150 kills at rank V unlock the final form."),
    "tech_how.almighty": ("Chega sozinha no rank V, com 8 Auswählen e 6 chefes derrotados. Os olhos do futuro.",
                          "Comes on its own at rank V, with 8 Auswählen and 6 bosses defeated. The eyes of the future."),
    "tech_tip.heilig_bogen": ("Heilig Bogen: monte com um efeito de dano; Dividir abre o leque.",
                              "Heilig Bogen: assemble it with a damage effect; Split widens the fan."),
    "tech_tip.sankt_zwinger": ("Sankt Zwinger: mire no chão, no meio dos inimigos — ninguém sai da gaiola.",
                               "Sankt Zwinger: aim at the ground in the middle of the enemies — nobody leaves the cage."),
    "tech_tip.antithesis": ("A Antítese: coloque num projétil e use quando estiver mais ferido que o alvo.",
                            "The Antithesis: put it on a projectile and use it when you are more wounded than the target."),
    "tech_tip.letzt_stil": ("Letzt Stil: monte na magia G. O preço é alto — 10 minutos sem poder Quincy.",
                            "Letzt Stil: put it in your G spell. The price is high — 10 minutes without Quincy power."),
    "tech_tip.almighty": ("O Todo-Poderoso: monte na magia G e veja o futuro por 20 s.",
                          "The Almighty: put it in your G spell and see the future for 20 s."),
}
for key, (pt, en) in SCREEN.items():
    k(S + key, pt, en)


def entry(key, value):
    return "  " + json.dumps(key, ensure_ascii=False) + ": " + json.dumps(value, ensure_ascii=False)


def merge_lang():
    """Mescla sem reformatar: troca o texto das chaves que mudaram (na mesma linha) e acrescenta as novas no fim, num
    bloco separado por uma linha em branco. Rodar de novo não muda nada."""
    for idx, lang in ((0, "pt_br"), (1, "en_us")):
        path = os.path.join(LANG, lang + ".json")
        raw = open(path, "rb").read().decode("utf-8")
        nl = "\r\n" if "\r\n" in raw else "\n"
        data = json.loads(raw)
        lines = raw.split(nl)
        changed = 0
        for key, vals in T.items():
            if key in data and data[key] != vals[idx]:
                prefix = "  " + json.dumps(key, ensure_ascii=False) + ":"
                for i, ln in enumerate(lines):
                    if ln.startswith(prefix):
                        lines[i] = entry(key, vals[idx]) + ("," if ln.rstrip().endswith(",") else "")
                        changed += 1
                        break
        new = [(key, vals[idx]) for key, vals in T.items() if key not in data]
        text = nl.join(lines).rstrip()
        if not text.endswith("}"):
            raise RuntimeError(path + ": JSON não termina em }")
        if new:
            body = text[:-1].rstrip()
            if not body.endswith("{"):
                body += ","
            text = body + nl + nl + ("," + nl).join(entry(kk, vv) for kk, vv in new) + nl + "}"
        text += nl
        json.loads(text)  # confere antes de gravar
        with open(path, "wb") as f:
            f.write(text.encode("utf-8"))
        print(lang, "+", len(new), "chaves,", changed, "atualizadas")


ICONS = {  # glifo -> (tipo da moldura, pictograma do tools/gen/icons.py)
    "quincy_heilig_bogen": ("form", "v10_spirit_bow"),
    "quincy_sankt_zwinger": ("form", "sacred_barrier"),
    "quincy_antithesis": ("effect", "yin_yang"),
    "quincy_letzt_stil": ("form", "solar_crown"),
    "quincy_almighty": ("form", "reveal"),
}


def glyph_icons():
    """Icones 32x32 dos glifos novos (mesmo estilo do tools/gen/icons.py, com a moldura certa de cada tipo)."""
    sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
    sys.argv = sys.argv[:1]
    import icons as I  # noqa: E402
    specs, _, _ = I.build_specs()
    for gid, (kind, pic) in ICONS.items():
        spec = dict(specs.get(gid) or {"elem": "light"})
        spec["kind"], spec["pic"] = kind, pic
        spec.pop("args", None)
        I.save_px(I.make_glyph_icon(spec), os.path.join(A, "textures/gui/glyph", gid + ".png"))


def main():
    print("== tecnicas avancadas do Quincy")
    cage_bar()
    zwinger_floor()
    sanrei_glove()
    merge_lang()
    glyph_icons()
    print("ok")


if __name__ == "__main__":
    main()
