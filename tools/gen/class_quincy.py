"""
Gera os assets e dados do modulo class_quincy (classe Quincy, homenagem a Bleach com nomes e visuais proprios).
Uso (na raiz do projeto):  py -I tools/gen/class_quincy.py

Gera:
  - traje Quincy em 4 graus (GeckoLib, mesmas convencoes/ossos do modulo outfits; reaproveita os pinceis de
    tools/gen/outfits.py): geo/item/armor/quincy_outfit[_<grau>].geo.json + textura + glowmask, reserva vanilla
    textures/models/armor/quincy_outfit[_<grau>]_layer_1/2.png e os 16 icones das pecas
  - Cruz Quincy (arma do kit, 5 graus) com o Arco Espiritual que aparece ao puxar (pulling_0..2), Lamina Espiritual
    (Seele Schneider), Ampola de Prata e o ovo do NPC
  - Flecha Espiritual (textura no formato do ArrowRenderer: lateral 16x5 + traseira 5x5)
  - NPC Quincy (GeckoLib): modelo, animacoes e 3 variantes de textura (com glowmask)
  - Acampamento Quincy (.nbt + worldgen random_spread_multi + tag de bioma no merge), loot tables, receitas
  - icone da aba do menu (textures/gui/tab/quincy.png)
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix, item_model  # noqa: E402
import outfits as O  # noqa: E402  (pinceis e montagem de atlas dos trajes)
from structgen import Structure, Int, Byte  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/class_quincy")
T = (0, 0, 0, 0)
HD, BD, RA, LA, RL, LL, RB, LB = O.HD, O.BD, O.RA, O.LA, O.RL, O.LL, O.RB, O.LB
fc, OMIT, gem, cape, waist, skirt = O.fc, O.OMIT, O.gem, O.cape, O.waist, O.skirt


def H(h, a=255):
    return hex_rgba(h, a)


def pal(*hexes):
    return [H(h) for h in hexes]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  TRAJE QUINCY (4 graus)
#  I Aprendiz (branco e azul, quepe com a cruz) · II Cacador Espiritual (prata, capa media)
#  III Sternritter (capuz, capa longa com a estrela, preto e ouro) · IV Vollstandig (ouro, aureola e asas de luz)
# =====================================================================================
WHITE = pal("7E8598", "AEB6C8", "D6DCE8", "EEF2F8", "FFFFFF")
IVORY_W = pal("8A8676", "B8B3A0", "DCD8C6", "F2EFE2", "FFFDF4")
BLUE = pal("0E2A5E", "1B4A96", "3576D0", "78B6FF", "CBE6FF")
SILVER = pal("4A505E", "7A8292", "ABB3C2", "D6DCE6", "F6F9FF")
BLACK = pal("0A0B10", "161821", "242734", "363B4C", "50566A")
GOLD = pal("6A4508", "A06C12", "D49A26", "F5C84A", "FFF0A8")
LEATH_G = pal("1C1E26", "2C2F3A", "3E4252", "52586A", "6E7486")
GEM_B = pal("0A3A7A", "2A7AE0", "8FD0FF", "E6F6FF")
GEM_GOLD = pal("6A4A04", "C08A10", "FFE07A", "FFF8D8")

TIERS = ("base", "adept", "master", "archmage")
LV = {"base": 0, "adept": 1, "master": 2, "archmage": 3}
QUINCY_T = {
    "base": O.theme(WHITE, IVORY_W, BLUE, LEATH_G, SILVER, SILVER, GEM_B, "9FE0FF"),
    "adept": O.theme(WHITE, pal("8E96A8", "BCC4D4", "E0E6F0", "F4F7FC", "FFFFFF"), SILVER, LEATH_G, BLUE, SILVER, GEM_B, "9FE0FF"),
    "master": O.theme(WHITE, IVORY_W, BLACK, BLACK, GOLD, GOLD, GEM_B, "8FD8FF"),
    "archmage": O.theme(IVORY_W, pal("C4A866", "DCC48A", "EEDDB0", "FAF0D2", "FFFBEA"), GOLD, LEATH_G, BLUE, GOLD,
                        pal("0A4A9A", "3A9CFF", "B8ECFF", "FFFFFF"), "B8ECFF"),
}


def quincy_cross_px(r, cx, cy, c, lit, big=True):
    """Cruz Quincy (braco de baixo mais longo) centrada em (cx, cy)."""
    p = O.put(r, lit)
    arm = 2 if big else 1
    for j in range(cy - arm, cy + arm + 2):
        p(cx, j, c)
    for i in range(cx - arm, cx + arm + 1):
        p(i, cy, c)
    p(cx, cy, lighten(c, 0.45))


def pentacle_px(r, cx, cy, rad, c, lit):
    """Estrela de cinco pontas (o simbolo Quincy) desenhada com linhas."""
    p = O.put(r, lit)
    pts = []
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        pts.append((cx + rad * math.cos(a), cy + rad * math.sin(a)))
    order = [0, 2, 4, 1, 3, 0]
    for a, b in zip(order, order[1:]):
        (x0, y0), (x1, y1) = pts[a], pts[b]
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for t in range(n + 1):
            p(int(round(x0 + (x1 - x0) * t / n)), int(round(y0 + (y1 - y0) * t / n)), c)


def quincy_mats(P, Th, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = O.v9_common(M, P, Th, lv)
    GL, GEMS = Th["GLOW"], Th["GEM"]
    lit = lv >= 1

    def trimmed_cloth(r, base_pal, trim, rows=2, folds=True):
        O.cloth(r, base_pal, period=4, base=3, top=0.06, bottom=0.12, folds=folds)
        for k in range(min(rows, r.h)):
            r.row(r.h - 1 - k, trim[2] if k else trim[1])

    # ---- quepe (graus I e II) e capuz (III e IV)
    M["cap"] = lambda r: (O.cloth(r, MAIN, period=3, base=3, folds=False), r.row(-1, TRIM[2]), r.row(-2, TRIM[3]) if r.h > 3 else None)
    M["cap_top"] = lambda r: (r.fill(MAIN[3]), r.noise(0.02), r.set(r.w // 2, r.h // 2, TRIM[3]))

    def cap_front(r):
        O.cloth(r, MAIN, period=3, base=3, folds=False)
        r.row(-1, TRIM[2])
        if r.h > 3:
            r.row(-2, TRIM[3])
        quincy_cross_px(r, r.w // 2, max(1, r.h // 2 - 2), TRIM[3] if lv == 0 else GL, lit, big=False)

    M["cap_front"] = cap_front
    M["visor"] = lambda r: (r.fill(LEATH[1]), r.row(0, LEATH[3]), r.noise(0.02))
    M["hood"] = lambda r: O.cloth(r, MAIN, period=3, base=3, top=0.10, bottom=0.10)

    def hood_front(r):
        O.cloth(r, MAIN, period=3, base=3)
        for j in range(2, r.h):
            for i in range(1, r.w - 1):
                r.clear(i, j)
        for i in range(1, r.w - 1):
            r.set(i, 1, TRIM[2])
        r.set(0, r.h - 1, TRIM[3]); r.set(r.w - 1, r.h - 1, TRIM[3])

    M["hood_front"] = hood_front

    # ---- casaco branco de gola alta com a cruz no peito
    def coat(r, front):
        O.cloth(r, MAIN, period=4, base=3, top=0.06, bottom=0.14)
        if front:
            cx = r.w // 2
            # abotoamento duplo + debrum azul no meio
            for j in range(r.h):
                r.set(cx - 1, j, TRIM[1]); r.set(cx, j, TRIM[2])
            for j in range(3, r.h - 1, 3):
                r.set(cx - 3, j, ACC[3]); r.set(cx + 2, j, ACC[3])
            quincy_cross_px(r, cx - 1 if r.w % 2 == 0 else cx, 3, GL if lit else TRIM[3], lit)
        r.row(0, TRIM[3])

    M["coat_front"] = lambda r: coat(r, True)
    M["coat"] = lambda r: coat(r, False)
    M["collar"] = lambda r: (O.cloth(r, MAIN, period=2, base=3, folds=False), r.row(0, TRIM[3]), r.row(-1, TRIM[1]))
    M["coat_skirt"] = lambda r: (trimmed_cloth(r, MAIN, TRIM), O.leaf_hem(r, TRIM[2], 1) if lv >= 2 else None)
    M["coat_in"] = lambda r: O.cloth(r, SEC, period=4, base=2, folds=False)

    def mantle(r):
        O.cloth(r, MAIN, period=3, base=3, top=0.06, bottom=0.10)
        r.row(-1, TRIM[2])
        if r.h > 2:
            r.row(-2, TRIM[3])
        if lit:
            for i in range(1, r.w - 1, 4):
                r.glow(i, r.h - 2, GL)

    M["mantle"] = mantle

    def cape_out(r):
        O.cloth(r, MAIN, period=4, base=3, top=0.05, bottom=0.12)
        r.col(0, TRIM[2]); r.col(-1, TRIM[2])
        r.row(-1, TRIM[1])
        if lv >= 2:
            pentacle_px(r, r.w / 2 - 0.5, min(r.h - 6, 7), min(4.0, r.w / 2 - 1.5), GL if lv == 3 else TRIM[3], lv == 3)
        elif lv == 1:
            quincy_cross_px(r, r.w // 2, 4, GL, True)
        if lv == 3:
            for i in range(1, r.w - 1, 2):
                r.glow(i, r.h - 2, GL)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (O.cloth(r, SEC if lv < 2 else BLUE, period=4, base=2, folds=False), r.row(-1, TRIM[1]))
    M["cape_edge"] = lambda r: (r.fill(TRIM[2]), r.noise(0.03))

    def sleeve(r):
        O.cloth(r, MAIN, period=3, base=3)
        if r.h > 3:
            r.row(-1, TRIM[2]); r.row(-2, TRIM[3])

    M["sleeve"] = sleeve

    def glove(r):
        r.fill(SEC[3] if lv < 2 else LEATH[2])
        r.row(0, TRIM[3])
        if r.h > 2:
            r.row(1, TRIM[2])
        r.noise(0.03)

    M["glove"] = glove

    def trousers(r):
        O.cloth(r, MAIN, period=4, base=3, top=0.04, bottom=0.10)
        r.col(r.w // 2, TRIM[2])

    M["trousers"] = trousers
    M["boot"] = lambda r: (r.fill(MAIN[3] if lv != 2 else BLACK[2]), r.row(-1, LEATH[1]), r.row(-2, LEATH[2]) if r.h > 3 else None,
                           r.vshade(0.08, 0.14), r.noise(0.03))
    M["boot_cuff"] = lambda r: (r.fill(TRIM[2]), r.row(0, TRIM[3]), r.noise(0.03))

    def bracelet(r):
        r.fill(ACC[2])
        r.row(0, ACC[4])
        for i in range(0, r.w, 2):
            r.set(i, r.h - 1, ACC[1])
        r.noise(0.02)

    M["bracelet"] = bracelet
    # o pingente da Cruz Quincy sempre brilha (e o foco do Reishi, em todos os graus)
    M["cross_charm"] = lambda r: (r.fill(ACC[3]), r.glow(r.w // 2, r.h // 2, GL))

    def wing(r):
        # pena de luz (grau IV): haste clara e barbas que brilham
        cx = r.w // 2
        for j in range(r.h):
            for i in range(r.w):
                if abs(i - cx) <= max(0, (r.h - j) * r.w // (2 * r.h) + 1):
                    r.glow(i, j, GL if (i + j) % 3 else lighten(GL, 0.4))
            r.glow(cx, j, lighten(GL, 0.6))

    M["wing"] = wing
    M["halo"] = O.glow_fill(lighten(GL, 0.15), 0.55)
    return M


def quincy_model(name, lv):
    m = O.Model(name)
    O.skeleton(m)
    # ---------------- cabeca
    if lv <= 1:
        # quepe branco com a cruz na frente e aba escura
        m.box(HD, [-4.3, 29.2, -4.3], [8.6, 3.0, 8.6], fc(n="cap_front", s="cap", ew="cap", u="cap_top", d=OMIT), inflate=0.35)
        m.box(HD, [-4.6, 31.6, -4.9], [9.2, 1.0, 9.4], fc(side="cap@4x1", u="cap_top", d="visor@4x4"))
        m.box(HD, [-3.8, 28.8, -6.6], [7.6, 0.5, 2.6], fc(n="visor@4x1", ew="visor@1x1", u="visor@4x2", d="visor@4x2", s=OMIT),
              rot=[-8, 0, 0], pivot=[0, 29.0, -4.2])
        if lv == 1:
            O.ring(m, HD, -4.65, 4.65, -4.65, 4.65, 29.3, 0.7, 0.3, side="o_metal_flat", cap="o_metal_flat")
    else:
        # capuz dos Sternritter, com a ponta caida para tras (balanca)
        m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="hood_front", s="hood", ew="hood", u="hood", d=OMIT), inflate=0.75)
        m.box(HD, [-3.4, 31.2, -4.2], [6.8, 1.0, 1.0], fc(all="cape_edge@4x1"))
        m.bone("hatMid", [0, 31.0, 4.2], parent=HD, rot=[24, 0, 0])
        m.box("hatMid", [-2.4, 27.8, 4.0], [4.8, 3.4, 1.4], fc(side="hood", u="hood@3x2", d="hood@3x2"))
        m.bone("hatTip", [0, 28.0, 4.8], parent="hatMid", rot=[18, 0, 0])
        m.box("hatTip", [-1.2, 25.6, 4.3], [2.4, 2.6, 1.0], fc(side="hood", u="hood@2x1", d="hood@2x1"))
        if lv == 3:
            # aureola de luz flutuando sobre o capuz (Vollstandig)
            m.bone("halo", [0, 35.4, 0], parent=HD)
            O.ring(m, "halo", -3.6, 3.6, -3.6, 3.6, 35.0, 0.5, 0.5, side="halo", cap="halo")
            for (x, z) in ((0, -4.4), (0, 4.4), (-4.4, 0), (4.4, 0)):
                gem(m, "halo", [x, 35.25, z], 0.9, "o_gem@1x1", rot=[0, 45, 0], pivot=[x, 35.25, z])

    # ---------------- tronco: casaco de gola alta, cinto, manto curto e capa
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="coat_front", s="coat", ew="coat", u="coat", d="dark@2x2"), inflate=0.5)
    m.box(BD, [-4.4, 22.6, -2.4], [8.8, 1.8, 4.8], fc(side="collar", u="collar@4x4", d=OMIT), inflate=0.3)
    m.box(BD, [-4, 12.0, -2], [8, 1.4, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2", d="leather_top@2x2"),
          inflate=0.95)
    if lv >= 1:
        m.box(BD, [-4.6, 20.4, -2.7], [9.2, 3.4, 5.4], fc(n="mantle", s="mantle", ew="mantle", u="cap_top@4x4", d="dark@2x2"))
    if lv >= 2:
        O.strap_diag(m, BD, "strap", -3.05, 1, width=0.7)
        gem(m, BD, [2.6, 21.6, -3.0], [1.1, 1.1, 0.4], "o_gem@2x2")
    waist(m, "Chest")
    # casaco longo: a saia cresce a cada grau
    skirt(m, "Chest", (7.0, 5.6, 4.4, 3.4)[lv], "coat_skirt", "coat_skirt", "coat_skirt", "coat_in@4x4", "coat_in",
          flare=4, depth=0.5, width=9.2)
    cape(m, 24.0, (19.5, 14.0, 6.5, 3.5)[lv], 2.9, (8.6, 9.0, 9.6, 10.2)[lv], out="cape_out", inn="cape_in",
         edge="cape_edge", flare=3)
    if lv == 3:
        # asas de luz atras da capa (cada uma = 3 penas em leque)
        m.bone("wings", [0, 21.0, 3.6], parent=BD)
        for side in (-1, 1):
            for k, (ang, ln) in enumerate(((22, 9.0), (42, 7.6), (62, 6.0))):
                rz = side * ang
                m.box("wings", [-0.7, 21.0, 3.7 + 0.25 * k], [1.4, ln, 0.3], fc(ns="wing@3x8", ew=OMIT, u=OMIT, d=OMIT),
                      rot=[-10, 0, rz], pivot=[side * 1.2, 21.0, 3.8])

    # ---------------- bracos: mangas brancas, luvas e a pulseira com a cruz (braco direito)
    m.box(RA, [-8, 15.6, -2], [4, 8.4, 4], fc(side="sleeve", u="coat@2x2", d=OMIT), inflate=0.5)
    m.box(RA, [-8, 12, -2], [4, 3.8, 4], fc(side="glove", u="leather_top@2x2", d="dark@2x2"), inflate=0.6)
    m.mirror(RA, LA)
    m.box(RA, [-8, 14.6, -2], [4, 0.9, 4], fc(side="bracelet@4x1", u=OMIT, d=OMIT), inflate=0.85)
    m.box(RA, [-6.6, 12.6, -3.35], [1.2, 1.8, 0.35], fc(all="cross_charm@2x2"))
    m.box(RA, [-7.0, 13.3, -3.35], [2.0, 0.5, 0.35], fc(all="cross_charm@2x1"))
    if lv >= 1:
        O.pauldrons(m, 1 if lv < 3 else 2, side="mantle", top="cap_top", under="o_dark@2x2",
                    gem_mat="o_gem@2x2" if lv >= 2 else None, gem_size=1.0)

    # ---------------- pernas e botas
    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(RL, LL)
    m.box(RB, [-3.9, 0, -2], [4, 6, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(RB, [-3.9, 5.4, -2], [4, 1.2, 4], fc(side="boot_cuff", u="dark@2x2", d="dark@2x2"), inflate=0.8)
    if lv >= 2:
        gem(m, RB, [-4.75, 2.6, 0.0], 0.6, "o_gem@1x1")
    m.mirror(RB, LB)
    return m


def vanilla_layers(name, main, trim, dark):
    c1 = Canvas(64, 32)
    regions = [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]
    rnd = random.Random(len(name))
    for (x0, y0, w, h) in regions:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = main if (x + y) % 5 else dark
                if rnd.random() < 0.06:
                    c = trim
                c1.set(x, y, c)
    for x in range(0, 64):
        c1.set(x, 31, trim)
    c1.save(os.path.join(A, "textures/models/armor", name + "_layer_1.png"))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(A, "textures/models/armor", name + "_layer_2.png"))


# Icones das pecas: W branco (3 tons), T debrum, G gema/brilho, D escuro, S prata/ouro
ICON_ROWS = {
    "helmet": [
        "................",
        "................",
        "....DDDDDDDD....",
        "...DWWWWWWWWD...",
        "..DWWWWGWWWWWD..",
        "..DWWWGGGWWWWD..",
        "..DWWWWGWWWWwD..",
        "..DWWWWGWWWwwD..",
        "..DTTTTTTTTTTD..",
        "..DttttttttttD..",
        ".DDDDDDDDDDDDDD.",
        ".DSSSSSSSSSSSSD.",
        "..DDDDDDDDDDDD..",
        "................",
        "................",
        "................"],
    "chestplate": [
        "................",
        "...DDD....DDD...",
        "..DWWWDDDDWWWD..",
        ".DWWWTTWWTTWWWD.",
        ".DWWWWTGGTWWwwD.",
        ".DDWWWWGGWWwwDD.",
        "..DWWWGGGGWwD...",
        "..DWWWWGGWWwD...",
        "..DWWWWGGWWwD...",
        "..DSSSSSSSSSD...",
        "..DWWWWTTWWwD...",
        "..DWWWWTTWWwwD..",
        "..DWWWWTTWWwwD..",
        "..DTTTTTTTTTTD..",
        "...DDDDDDDDDD...",
        "................"],
    "leggings": [
        "................",
        "...DDDDDDDDDD...",
        "...DSSSSSSSSD...",
        "...DWWWWWWWwD...",
        "...DWWTWWTWwD...",
        "...DWWTDDTWwD...",
        "...DWWTDDTWwD...",
        "...DWWTDDTWwD...",
        "...DWWTDDTWwD...",
        "...DWWTDDTWwD...",
        "...DWWTDDTWwD...",
        "...DTTTDDTTTD...",
        "...DDDDDDDDDD...",
        "................",
        "................",
        "................"],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "...DDDD..DDDD...",
        "...DWWD..DWWD...",
        "...DWWD..DWWD...",
        "...DTTD..DTTD...",
        "...DWWD..DWWD...",
        "...DWWD..DWWD...",
        "..DWWWD..DWWWD..",
        ".DWWWwD..DWWWwD.",
        ".DSSSSD..DSSSSD.",
        ".DDDDDD..DDDDDD.",
        "................",
        "................"],
}


def draw_armor_icon(piece, tkey):
    th = QUINCY_T[tkey]
    P = th["pal"]
    lv = LV[tkey]
    cols = {"W": P["MAIN"][3], "w": P["MAIN"][2], "T": P["TRIM"][2], "t": P["TRIM"][3], "G": th["GEM"][2],
            "D": darken(P["MAIN"][0], 0.55), "S": th["METAL"][3]}
    c = Canvas(16, 16)
    for y, row in enumerate(ICON_ROWS[piece]):
        for x, ch in enumerate(row):
            if ch != "." and ch in cols:
                c.set(x, y, cols[ch])
    if lv >= 1:
        g = th["GEM"]
        c.set(14, 1, g[2])
        if lv >= 2:
            c.set(1, 14, g[2])
        if lv == 3:
            for (x, y) in ((13, 1), (14, 0), (14, 2), (15, 1)):
                c.set(x, y, lighten(th["GLOW"], 0.3))
    name = f"quincy_{piece}" if lv == 0 else f"quincy_{tkey}_{piece}"
    c.save(os.path.join(A, "textures/item", name + ".png"))
    item_model(A, name)


def build_outfit():
    print("== traje Quincy")
    for tkey in TIERS:
        lv = LV[tkey]
        th = QUINCY_T[tkey]
        name = "quincy_outfit" if lv == 0 else f"quincy_outfit_{tkey}"
        _, glow = O.build(quincy_model(name, lv), quincy_mats(th["pal"], th, lv))
        if not glow:
            raise RuntimeError(f"{name}: sem glowmask (o Java liga a camada de brilho em todos os graus)")
        P = th["pal"]
        vanilla_layers(name, P["MAIN"][3], P["TRIM"][2], P["MAIN"][1])
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            draw_armor_icon(piece, tkey)


# =====================================================================================
#  ITENS
# =====================================================================================
REISHI = pal("0C2E66", "1F5FC0", "4C9BFF", "A8DFFF", "E8F8FF")
CROSS_TIERS = {  # metal do pingente por grau da Cruz
    1: pal("3E4452", "6E7686", "A2AABA", "D2D8E4", "F4F8FF"),
    2: pal("2E4A6A", "4E7AA6", "7EB0DA", "B8DCF6", "EAF6FF"),
    3: pal("5A3A08", "9A6A14", "D4A030", "F2CC5A", "FFF0B0"),
    4: pal("3A1E5A", "6A3EA0", "A070E0", "D6B4FF", "F6ECFF"),
}


def draw_cross(name, metal, glow):
    """Cruz Quincy pendurada numa pulseira de contas (icone da arma do kit)."""
    c = Canvas(16, 16)
    # contas da pulseira (arco no topo)
    for k in range(9):
        a = math.pi * (0.1 + 0.8 * k / 8)
        x = int(round(8 - 5.5 * math.cos(a)))
        y = int(round(5 - 4.2 * math.sin(a)))
        c.set(x, y, metal[3] if k % 2 else metal[2])
    # cruz: braco de baixo mais longo
    c.rect(7, 5, 8, 14, metal[2])
    c.rect(4, 7, 11, 8, metal[2])
    c.rect(7, 5, 7, 14, metal[3])
    c.rect(4, 7, 11, 7, metal[3])
    c.set(8, 14, metal[1]); c.set(11, 8, metal[1]); c.set(8, 8, metal[1])
    # centro de Reishi
    c.set(7, 7, glow[4]); c.set(8, 7, glow[3]); c.set(7, 8, glow[3]); c.set(8, 8, glow[2])
    c.outline(darken(metal[0], 0.5))
    c.save(os.path.join(A, "textures/item", name + ".png"))


def draw_spirit_bow(name, stage, glow):
    """Arco espiritual de luz azul que se forma a partir da cruz (0..2 = cada vez mais puxado)."""
    c = Canvas(16, 16)
    pull = (0, 1, 2)[stage]
    # arco curvo (diagonal, como o arco vanilla na mao)
    pts = []
    for k in range(15):
        t = k / 14
        x = 2 + t * 11 + math.sin(t * math.pi) * (2.6 - 0.5 * pull)
        y = 13 - t * 11 + math.sin(t * math.pi) * (2.6 - 0.5 * pull)
        pts.append((int(round(x)), int(round(y))))
    for (x, y) in pts:
        c.set(x, y, glow[3])
    for (x, y) in pts[3:-3]:
        c.set(x + 1, y, glow[2])
    # corda puxada para tras
    sx0, sy0 = pts[0]
    sx1, sy1 = pts[-1]
    mx, my = (sx0 + sx1) / 2 - pull * 1.3, (sy0 + sy1) / 2 - pull * 1.3
    for (ax, ay) in ((sx0, sy0), (sx1, sy1)):
        n = 10
        for k in range(n + 1):
            c.set(int(round(ax + (mx - ax) * k / n)), int(round(ay + (my - ay) * k / n)), glow[4])
    # flecha de luz no centro quando puxado
    if pull > 0:
        for k in range(6 + pull):
            c.set(int(round(mx)) + k, int(round(my)) - k, lighten(glow[4], 0.4))
        c.set(int(round(mx)) + 6 + pull, int(round(my)) - 6 - pull, (255, 255, 255, 255))
    c.outline(H("0A1A40", 200))
    c.save(os.path.join(A, "textures/item", name + ".png"))


def items():
    print("== itens")
    tex = os.path.join(A, "textures/item")
    names = ["quincy_cross"] + [f"quincy_cross_t{n}" for n in (1, 2, 3, 4)]
    for name in names:
        tier = 1 if name in ("quincy_cross", "quincy_cross_t1") else int(name[-1])
        draw_cross(name, CROSS_TIERS[tier], REISHI if tier < 4 else pal("2A1060", "6A3EE0", "A88CFF", "D8C8FF", "F8F0FF"))
    for stage in range(3):
        draw_spirit_bow(f"quincy_spirit_bow_pulling_{stage}", stage, REISHI)
    # modelo da Cruz: segura como um arco; ao puxar vira o arco espiritual
    for name in names:
        overrides = [{"predicate": {"pulling": 1}, "model": "isekai_mahou:item/quincy_spirit_bow_pulling_0"},
                     {"predicate": {"pulling": 1, "pull": 0.65}, "model": "isekai_mahou:item/quincy_spirit_bow_pulling_1"},
                     {"predicate": {"pulling": 1, "pull": 0.9}, "model": "isekai_mahou:item/quincy_spirit_bow_pulling_2"}]
        write_json(os.path.join(A, "models/item", name + ".json"),
                   {"parent": "minecraft:item/bow", "textures": {"layer0": "isekai_mahou:item/" + name}, "overrides": overrides})
    for stage in range(3):
        write_json(os.path.join(A, "models/item", f"quincy_spirit_bow_pulling_{stage}.json"),
                   {"parent": "minecraft:item/bow", "textures": {"layer0": f"isekai_mahou:item/quincy_spirit_bow_pulling_{stage}"}})

    # Lamina Espiritual (Seele Schneider): punho de prata e lamina de luz
    c = Canvas(16, 16)
    blade = REISHI
    for i in range(10):
        x, y = 5 + i, 10 - i
        c.set(x, y, blade[4]); c.set(x + 1, y, blade[3]); c.set(x, y + 1, blade[2])
    c.set(15, 0, (255, 255, 255, 255))
    c.rect(3, 11, 5, 11, SILVER[3]); c.rect(4, 10, 4, 12, SILVER[3])
    c.set(2, 13, SILVER[2]); c.set(3, 12, SILVER[1]); c.set(1, 14, SILVER[1]); c.set(0, 15, SILVER[2])
    c.set(4, 11, blade[4])
    c.outline(H("0A1A40"))
    c.save(os.path.join(tex, "quincy_seele_schneider.png"))
    write_json(os.path.join(A, "models/item", "quincy_seele_schneider.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "isekai_mahou:item/quincy_seele_schneider"}})

    # Ampola de Prata (Ginto): tubo de prata com Reishi liquido dentro
    c = Canvas(16, 16)
    for k in range(9):
        x, y = 4 + k, 12 - k
        c.set(x, y, SILVER[2]); c.set(x + 1, y, SILVER[3]); c.set(x, y + 1, SILVER[1]); c.set(x + 1, y + 1, SILVER[2])
    for k in range(2, 7):
        c.set(4 + k, 12 - k, REISHI[3]); c.set(5 + k, 12 - k, REISHI[2])
    c.set(13, 3, SILVER[4]); c.set(12, 3, SILVER[3]); c.set(13, 4, SILVER[3])
    c.set(3, 13, SILVER[0]); c.set(4, 13, SILVER[1])
    c.outline(H("16202E"))
    c.save(os.path.join(tex, "quincy_silver_tube.png"))
    item_model(A, "quincy_silver_tube")
    write_json(os.path.join(A, "models/item", "quincy_npc_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})

    # Flecha Espiritual: formato do ArrowRenderer (lateral 16x5 em (0,0), traseira 5x5 em (0,5)), textura 32x32
    c = Canvas(32, 32)
    for x in range(16):
        t = x / 15
        core = mix(REISHI[3], (255, 255, 255, 255), t)
        c.set(x, 2, core)
        if 2 <= x <= 13:
            c.set(x, 1, H("7AB8FF", 150 + int(80 * t)))
            c.set(x, 3, H("7AB8FF", 150 + int(80 * t)))
    for (x, y) in ((12, 0), (13, 1), (14, 2), (13, 3), (12, 4), (11, 0), (11, 4)):
        c.set(x, y, (240, 250, 255, 255))
    c.set(15, 2, (255, 255, 255, 255))
    for (x, y) in ((0, 0), (1, 1), (0, 4), (1, 3), (2, 0), (2, 4)):
        c.set(x, y, H("4C9BFF", 210))
    for y in range(5, 10):
        for x in range(0, 5):
            d = abs(x - 2) + abs(y - 7)
            if d <= 2:
                c.set(x, y, REISHI[4] if d == 0 else H("8FD0FF", 220 - 40 * d))
    c.save(os.path.join(A, "textures/entity", "quincy_spirit_arrow.png"))

    # Icone da aba do menu: a estrela Quincy
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 6.2 <= d <= 7.4:
                c.set(x, y, REISHI[2])
    pts = []
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        pts.append((8 + 5.6 * math.cos(a), 8 + 5.6 * math.sin(a)))
    order = [0, 2, 4, 1, 3, 0]
    for a, b in zip(order, order[1:]):
        (x0, y0), (x1, y1) = pts[a], pts[b]
        for t in range(25):
            c.set(int(x0 + (x1 - x0) * t / 24), int(y0 + (y1 - y0) * t / 24), REISHI[4])
    c.set(7, 8, (255, 255, 255, 255)); c.set(8, 8, (255, 255, 255, 255))
    c.save(os.path.join(A, "textures/gui/tab", "quincy.png"))


# =====================================================================================
#  NPC QUINCY (GeckoLib): modelo com UV de caixa, pintura por face, 3 variantes de textura
# =====================================================================================
CLEAR = (0, 0, 0, 0)


def C(h, a=255):
    return h if isinstance(h, tuple) else hex_rgba(h, a)


class Face:
    def __init__(self, model, name, x, y, w, h):
        self.m, self.name, self.x, self.y, self.w, self.h = model, name, x, y, w, h

    def set(self, fx, fy, c, glow=False):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.m.tex.set(self.x + fx, self.y + fy, C(c))
            self.m.glow.set(self.x + fx, self.y + fy, C(c) if glow else CLEAR)

    def get(self, fx, fy):
        return self.m.tex.get(self.x + fx, self.y + fy)

    def is_glow(self, fx, fy):
        return self.m.glow.get(self.x + fx, self.y + fy)[3] > 0

    def rect(self, x0, y0, x1, y1, c, glow=False):
        for yy in range(max(0, y0), min(self.h - 1, y1) + 1):
            for xx in range(max(0, x0), min(self.w - 1, x1) + 1):
                self.set(xx, yy, c, glow)

    def fill(self, c, glow=False):
        self.rect(0, 0, self.w - 1, self.h - 1, c, glow)

    def tint(self, fn):
        for yy in range(self.h):
            for xx in range(self.w):
                p = self.get(xx, yy)
                if p[3] > 0 and not self.is_glow(xx, yy):
                    self.m.tex.set(self.x + xx, self.y + yy, fn(p, xx, yy))

    def noise(self, amount, seed):
        rnd = random.Random(seed)

        def f(p, xx, yy):
            r = (rnd.random() - 0.5) * 2 * amount
            return lighten(p, r) if r > 0 else darken(p, -r)
        self.tint(f)


class NModel:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []
        self.cubes = []

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, paint, inflate=0.0):
        self.cubes.append({"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size],
                           "paint": paint, "inflate": inflate})

    def layout(self):
        items = []
        for i, c in enumerate(self.cubes):
            w, h, d = c["size"]
            items.append((i, 2 * (d + w), d + h))
        order = sorted(items, key=lambda t: (-t[2], -t[1], t[0]))
        for W in (64, 128, 256):
            x = y = row_h = 0
            pos = {}
            ok = True
            for i, rw, rh in order:
                if rw > W:
                    ok = False
                    break
                if x + rw > W:
                    x = 0
                    y += row_h
                    row_h = 0
                pos[i] = (x, y)
                x += rw
                row_h = max(row_h, rh)
            if not ok:
                continue
            used = y + row_h
            hh = 16
            while hh < used:
                hh *= 2
            if hh <= W:
                self.tw, self.th = W, hh
                for i, c in enumerate(self.cubes):
                    c["uv"] = pos[i]
                return
        raise RuntimeError("modelo grande demais: " + self.ident)

    def faces(self, c):
        u, v = c["uv"]
        w, h, d = c["size"]
        return {
            "top": Face(self, "top", u + d, v, w, d),
            "bottom": Face(self, "bottom", u + d + w, v, w, d),
            "right": Face(self, "right", u, v + d, d, h),
            "front": Face(self, "front", u + d, v + d, w, h),
            "left": Face(self, "left", u + d + w, v + d, d, h),
            "back": Face(self, "back", u + 2 * d + w, v + d, w, h),
        }

    def paint(self, seed=1):
        self.tex = Canvas(self.tw, self.th)
        self.glow = Canvas(self.tw, self.th)
        for i, c in enumerate(self.cubes):
            c["paint"](self.faces(c), seed + i * 17)

    def save_geo(self, bounds):
        bones = []
        for b in self.bones:
            bb = dict(b)
            cubes = []
            for c in self.cubes:
                if c["bone"] != b["name"]:
                    continue
                cj = {"origin": c["origin"], "size": c["size"], "uv": list(c["uv"])}
                if c["inflate"]:
                    cj["inflate"] = c["inflate"]
                cubes.append(cj)
            if cubes:
                bb["cubes"] = cubes
            bones.append(bb)
        write_json(os.path.join(A, "geo/entity", self.ident + ".geo.json"), {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {"identifier": "geometry." + self.ident, "texture_width": self.tw, "texture_height": self.th,
                                "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1],
                                "visible_bounds_offset": [0, bounds[2], 0]},
                "bones": bones}]})

    def save_texture(self, name):
        self.tex.save(os.path.join(A, "textures/entity", name + ".png"))
        self.glow.save(os.path.join(A, "textures/entity", name + "_glowmask.png"))


def shade(fs, base, seed, noise=0.05, grad=0.18):
    for name, f in fs.items():
        c = C(base)
        if name == "top":
            c = lighten(c, 0.12)
        elif name == "bottom":
            c = darken(c, 0.3)
        elif name == "back":
            c = darken(c, 0.08)
        elif name in ("left", "right"):
            c = darken(c, 0.05)
        f.fill(c)
        if name in ("front", "back", "left", "right") and f.h > 2 and grad:
            hh = f.h
            f.tint(lambda p, xx, yy: lighten(p, grad * 0.4 * (1 - yy / (hh * 0.35))) if yy < hh * 0.35
                   else darken(p, grad * (yy - hh * 0.35) / (hh * 0.65)))
        if noise:
            f.noise(noise, seed + len(name) * 13)


def solid(base, edge=0.35, noise=0.05, grad=0.18):
    def p(fs, seed):
        shade(fs, base, seed, noise, grad)
        if edge:
            for f in fs.values():
                if f.w >= 3 and f.h >= 3:
                    for xx in range(f.w):
                        f.set(xx, 0, darken(C(base), edge))
                        f.set(xx, f.h - 1, darken(C(base), edge))
    return p


def glowing(base, edge=0.25):
    def p(fs, seed):
        rnd = random.Random(seed)
        for f in fs.values():
            for yy in range(f.h):
                for xx in range(f.w):
                    e = (xx in (0, f.w - 1) or yy in (0, f.h - 1)) and f.w > 2 and f.h > 2
                    c = darken(C(base), edge) if e else lighten(C(base), 0.15 * rnd.random())
                    f.set(xx, yy, c, True)
    return p


def anim_file(ident, anims):
    out = {}
    for name, a in anims.items():
        entry = {"animation_length": a["length"], "bones": a["bones"]}
        if a.get("loop"):
            entry["loop"] = a["loop"]
        out["animation." + ident + "." + name] = entry
    write_json(os.path.join(A, "animations/entity", ident + ".animation.json"), {"format_version": "1.8.0", "animations": out})


def kf(*pairs):
    d = {}
    for i in range(0, len(pairs), 2):
        d[str(round(float(pairs[i]), 4))] = [round(float(v), 3) for v in pairs[i + 1]]
    return d


def wave(length, amp, axis=0, phase=0.0, steps=8, offset=None):
    d = {}
    for i in range(steps + 1):
        t = length * i / steps
        v = [0.0, 0.0, 0.0]
        v[axis] = amp * math.sin(2 * math.pi * (i / steps) + phase)
        if offset:
            v = [v[k] + offset[k] for k in range(3)]
        d[str(round(t, 4))] = [round(x, 3) for x in v]
    return d


NPC_VARIANTS = {
    # textura: casaco, debrum, detalhe metalico, cabelo, pele, olhos, barba
    "quincy_npc": dict(coat="#EEF2F8", trim="#3576D0", metal="#C8D0DC", hair="#1C2030", skin="#EAC9A4", eyes="#4C9BFF", beard=False),
    "quincy_npc_sentinel": dict(coat="#E6EAF0", trim="#1A1C26", metal="#D4A030", hair="#C8CCD8", skin="#D8B48C", eyes="#8FD0FF", beard=False),
    "quincy_npc_elder": dict(coat="#F4F0E2", trim="#B08A2E", metal="#F2CC5A", hair="#E8E8EE", skin="#E2C2A0", eyes="#A8DFFF", beard=True),
}


def quincy_npc_model(v):
    m = NModel("quincy_npc")
    coat, trim, metal = C(v["coat"]), C(v["trim"]), C(v["metal"])
    hair, skin, eyes = C(v["hair"]), C(v["skin"]), C(v["eyes"])
    reishi = C("#8FD0FF")

    def coat_paint(trim_rows=1, cross=False):
        def p(fs, seed):
            shade(fs, coat, seed, 0.04, 0.16)
            for n, f in fs.items():
                if n in ("front", "back", "left", "right"):
                    for xx in range(0, f.w, 4):
                        for yy in range(f.h):
                            f.set(xx, yy, darken(f.get(xx, yy), 0.06))
                    for k in range(trim_rows):
                        for xx in range(f.w):
                            f.set(xx, f.h - 1 - k, trim)
            if cross:
                f = fs["front"]
                cx = f.w // 2
                for yy in range(f.h):
                    f.set(cx - 1, yy, darken(trim, 0.1))
                    f.set(cx, yy, trim)
                # cruz Quincy no peito (brilha)
                for yy in range(1, 6):
                    f.set(cx + 2, yy, reishi, True)
                for xx in range(cx + 1, cx + 4):
                    f.set(xx, 2, reishi, True)
                for yy in (3, 6, 9):
                    f.set(cx - 3, yy, metal)
                    f.set(cx + 2, yy + 1, metal)
                fs["top"].fill(darken(coat, 0.1))
        return p

    def head_paint(fs, seed):
        shade(fs, skin, seed, 0.03, 0.08)
        f = fs["front"]
        f.rect(0, 0, f.w - 1, 1, hair)
        f.set(1, 3, C("#FFFFFF"))
        f.set(2, 3, eyes, True)
        f.set(f.w - 3, 3, eyes, True)
        f.set(f.w - 2, 3, C("#FFFFFF"))
        f.rect(1, 2, 2, 2, darken(hair, 0.2))
        f.rect(f.w - 3, 2, f.w - 2, 2, darken(hair, 0.2))
        f.rect(3, 6, f.w - 4, 6, darken(skin, 0.28))
        for n in ("left", "right", "back"):
            fs[n].rect(0, 0, fs[n].w - 1, 3 if n != "back" else 6, hair)
        fs["top"].fill(hair)

    def hair_paint(fs, seed):
        shade(fs, hair, seed, 0.08, 0.12)
        fs["front"].fill(CLEAR)
        fs["bottom"].fill(CLEAR)
        f = fs["front"]
        for xx in range(f.w):
            if xx % 3 != 1:
                f.set(xx, 0, hair)

    def beard_paint(fs, seed):
        if not v["beard"]:
            for f in fs.values():
                f.fill(CLEAR)
            return
        shade(fs, hair, seed, 0.06, 0.1)
        fs["top"].fill(CLEAR)
        fs["back"].fill(CLEAR)

    def cape_paint(fs, seed):
        shade(fs, coat, seed, 0.04, 0.14)
        for n in ("front", "back"):
            f = fs[n]
            for xx in range(f.w):
                f.set(xx, f.h - 1, trim)
                f.set(xx, f.h - 2, darken(trim, 0.2))
        # estrela Quincy nas costas (brilha)
        f = fs["back"]
        cx, cy, rad = f.w / 2 - 0.5, 6.0, 3.6
        pts = [(cx + rad * math.cos(-math.pi / 2 + k * 2 * math.pi / 5), cy + rad * math.sin(-math.pi / 2 + k * 2 * math.pi / 5))
               for k in range(5)]
        for a, b in zip([0, 2, 4, 1, 3], [2, 4, 1, 3, 0]):
            (x0, y0), (x1, y1) = pts[a], pts[b]
            for t in range(13):
                f.set(int(round(x0 + (x1 - x0) * t / 12)), int(round(y0 + (y1 - y0) * t / 12)), reishi, True)

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 12, 0], "root")
    m.bone("head", [0, 24, 0], "body")
    m.bone("cape", [0, 24, 2], "body")
    m.bone("coat_tail", [0, 12, 0], "body")
    m.bone("arm_r", [-5, 22, 0], "body")
    m.bone("arm_l", [5, 22, 0], "body")
    m.bone("bow", [6, 13, 0], "arm_l")
    m.bone("leg_r", [-2, 12, 0], "root")
    m.bone("leg_l", [2, 12, 0], "root")
    # tronco: casaco de gola alta com a cruz
    m.cube("body", [-4, 12, -2], [8, 12, 4], coat_paint(1, True))
    m.cube("body", [-4.5, 21.5, -2.5], [9, 3, 5], solid(coat, 0.18), inflate=0.05)
    m.cube("body", [-4.5, 12, -2.5], [9, 1, 5], solid(trim, 0.25))
    m.cube("coat_tail", [-4.5, 4, -2.5], [9, 8, 5], coat_paint(2), inflate=0.02)
    # cabeca, cabelo e barba (so o anciao)
    m.cube("head", [-4, 24, -4], [8, 8, 8], head_paint)
    m.cube("head", [-4.5, 28, -4.5], [9, 4, 9], hair_paint, inflate=0.1)
    m.cube("head", [-3, 22, -4.6], [6, 3, 1], beard_paint)
    m.cube("cape", [-5, 7, 2.2], [10, 17, 1], cape_paint)
    # bracos; pulseira com a cruz no braco direito
    m.cube("arm_r", [-8, 12, -2], [4, 12, 4], coat_paint(2))
    m.cube("arm_r", [-8.3, 15, -2.3], [4, 1, 4], solid(metal, 0.2), inflate=0.15)
    m.cube("arm_r", [-6.5, 12.6, -2.9], [1, 2, 1], glowing(reishi, 0.1))
    m.cube("arm_l", [4, 12, -2], [4, 12, 4], coat_paint(2))
    # arco espiritual (luz azul; so aparece quando ele mira — as animacoes escalam o osso)
    m.cube("bow", [5, 0, -4], [1, 26, 1], glowing(C("#6FB8FF"), 0.15))
    m.cube("bow", [5, 11, -5], [1, 4, 3], glowing(C("#CFEFFF"), 0.1))
    m.cube("bow", [5.5, 1, -3], [0, 24, 1], glowing(C("#E8F8FF", 210), 0.0))
    # pernas e botas
    m.cube("leg_r", [-4, 0, -2], [4, 12, 4], coat_paint(0))
    m.cube("leg_r", [-4.2, 0, -2.2], [4, 4, 4], solid(darken(coat, 0.12), 0.3), inflate=0.2)
    m.cube("leg_l", [0, 0, -2], [4, 12, 4], coat_paint(0))
    m.cube("leg_l", [-0.2, 0, -2.2], [4, 4, 4], solid(darken(coat, 0.12), 0.3), inflate=0.2)
    return m


def npc():
    print("== NPC Quincy")
    first = True
    for name, v in NPC_VARIANTS.items():
        m = quincy_npc_model(v)
        m.layout()
        m.paint(77)
        if first:
            m.save_geo([2.4, 2.8, 1.2])
            first = False
        m.save_texture(name)
    hide_bow = {"bow": {"scale": kf(0, [0, 0, 0], 0.05, [0, 0, 0])}}
    aim = {"arm_l": {"rotation": kf(0, [-85, -10, 0], 1.0, [-85, -10, 0])},
           "arm_r": {"rotation": kf(0, [-85, 30, 0], 1.0, [-85, 30, 0])},
           "bow": {"rotation": kf(0, [85, 0, 0], 1.0, [85, 0, 0]), "scale": kf(0, [1, 1, 1], 1.0, [1, 1, 1])}}
    anim_file("quincy_npc", {
        "idle": {"loop": True, "length": 3.0, "bones": dict(hide_bow, **{
            "body": {"scale": kf(0, [1, 1, 1], 1.5, [1.01, 1.02, 1.01], 3.0, [1, 1, 1])},
            "cape": {"rotation": wave(3.0, 3, 0, offset=[5, 0, 0])},
            "coat_tail": {"rotation": wave(3.0, 1.5, 0)},
            "arm_r": {"rotation": wave(3.0, 2, 2)},
            "arm_l": {"rotation": wave(3.0, -2, 2)}})},
        "walk": {"loop": True, "length": 0.9, "bones": dict(hide_bow, **{
            "leg_r": {"rotation": wave(0.9, 30, 0)}, "leg_l": {"rotation": wave(0.9, 30, 0, math.pi)},
            "arm_r": {"rotation": wave(0.9, 20, 0, math.pi)}, "arm_l": {"rotation": wave(0.9, 20, 0)},
            "coat_tail": {"rotation": wave(0.9, 6, 0, offset=[4, 0, 0])},
            "cape": {"rotation": wave(0.9, 6, 0, offset=[14, 0, 0])}})},
        "aim": {"loop": True, "length": 1.0, "bones": dict(aim, **{
            "cape": {"rotation": wave(1.0, 3, 0, offset=[8, 0, 0])},
            "body": {"rotation": kf(0, [0, 15, 0], 1.0, [0, 15, 0])}})},
        "shoot": {"length": 0.35, "bones": {
            "arm_r": {"rotation": kf(0, [-90, 60, 0], 0.08, [-80, 10, 0], 0.35, [-85, 30, 0])},
            "arm_l": {"rotation": kf(0, [-90, -10, 0], 0.08, [-80, -10, 0], 0.35, [-85, -10, 0])},
            "bow": {"rotation": kf(0, [90, 0, 0], 0.08, [85, 0, -6], 0.35, [85, 0, 0]),
                    "scale": kf(0, [1, 1, 1], 0.35, [1, 1, 1])}}},
        "talk": {"loop": True, "length": 1.2, "bones": dict(hide_bow, **{
            "head": {"rotation": kf(0, [0, 0, 0], 0.3, [-6, 4, 0], 0.6, [2, -3, 0], 0.9, [-4, 2, 0], 1.2, [0, 0, 0])},
            "arm_r": {"rotation": kf(0, [0, 0, 0], 0.4, [-30, 0, 10], 0.8, [-25, 0, 6], 1.2, [0, 0, 0])}})},
        "yield": {"loop": "hold_on_last_frame", "length": 1.0, "bones": dict(hide_bow, **{
            "root": {"position": kf(0, [0, 0, 0], 0.6, [0, -5, 0], 1.0, [0, -5, 0])},
            "leg_r": {"rotation": kf(0, [0, 0, 0], 0.6, [-80, 0, 0], 1.0, [-80, 0, 0])},
            "leg_l": {"rotation": kf(0, [0, 0, 0], 0.6, [10, 0, 0], 1.0, [10, 0, 0])},
            "body": {"rotation": kf(0, [0, 0, 0], 0.6, [20, 0, 0], 1.0, [20, 0, 0])},
            "head": {"rotation": kf(0, [0, 0, 0], 0.6, [25, 0, 0], 1.0, [25, 0, 0])},
            "arm_l": {"rotation": kf(0, [0, 0, 0], 0.6, [-20, 0, -10], 1.0, [-20, 0, -10])},
            "arm_r": {"rotation": kf(0, [0, 0, 0], 0.6, [-30, 0, 10], 1.0, [-30, 0, 10])}})},
        "bow": {"length": 1.4, "bones": {
            "body": {"rotation": kf(0, [0, 0, 0], 0.4, [26, 0, 0], 1.0, [26, 0, 0], 1.4, [0, 0, 0])},
            "head": {"rotation": kf(0, [0, 0, 0], 0.4, [15, 0, 0], 1.0, [15, 0, 0], 1.4, [0, 0, 0])},
            "arm_r": {"rotation": kf(0, [0, 0, 0], 0.4, [-40, 0, 20], 1.0, [-40, 0, 20], 1.4, [0, 0, 0])}}},
    })


# =====================================================================================
#  ACAMPAMENTO QUINCY (estrutura de superficie com 3 NPCs) + worldgen
# =====================================================================================
CAMP = 23


def star_cells(cx, cz, rad):
    """Celulas da estrela de cinco pontas (linhas grossas) num disco do chao."""
    pts = [(cx + rad * math.cos(-math.pi / 2 + k * 2 * math.pi / 5), cz + rad * math.sin(-math.pi / 2 + k * 2 * math.pi / 5))
           for k in range(5)]
    cells = set()
    for a, b in zip([0, 2, 4, 1, 3], [2, 4, 1, 3, 0]):
        (x0, z0), (x1, z1) = pts[a], pts[b]
        for t in range(41):
            x = x0 + (x1 - x0) * t / 40
            z = z0 + (z1 - z0) * t / 40
            cells.add((int(round(x)), int(round(z))))
    return cells


def tent(s, x0, z0, facing, chest=False):
    """Tenda em A (5 de largura x 5 de fundo) de la branca com cumeeira azul; a frente fica aberta."""
    for dz in range(5):
        for (dx, y) in ((0, 1), (4, 1), (1, 2), (3, 2)):
            s.set(x0 + dx, y, z0 + dz, "minecraft:white_wool")
        s.set(x0 + 2, 3, z0 + dz, "minecraft:light_blue_wool")
    # fundo fechado (triangulo)
    back = z0 if facing == "south" else z0 + 4
    for x in range(x0 + 1, x0 + 4):
        s.set(x, 1, back, "minecraft:white_wool")
    s.set(x0 + 2, 2, back, "minecraft:white_wool")
    inner = z0 + 1 if facing == "south" else z0 + 3
    s.set(x0 + 1, 1, inner, "minecraft:white_carpet")
    s.set(x0 + 3, 1, inner, "minecraft:light_blue_carpet")
    if chest:
        s.chest(x0 + 2, 1, inner, "isekai_mahou:chests/quincy_camp", facing=facing)
    else:
        s.set(x0 + 2, 1, inner, "minecraft:white_carpet")
    # estandarte da cruz na entrada
    front = z0 + 4 if facing == "south" else z0
    out = front + (1 if facing == "south" else -1)
    rot = 0 if facing == "south" else 8
    s.set(x0, 1, out, f"minecraft:light_blue_banner[rotation={rot}]",
          nbt={"patterns": [{"color": "white", "pattern": "minecraft:straight_cross"},
                            {"color": "white", "pattern": "minecraft:circle"},
                            {"color": "blue", "pattern": "minecraft:border"}]})


def camp():
    print("== acampamento Quincy")
    s = Structure(CAMP, 9, CAMP)
    c = CAMP // 2
    # limpa a vegetacao acima do chao (o ar e salvo explicitamente)
    s.fill(0, 1, 0, CAMP - 1, 8, CAMP - 1, "minecraft:air")
    rnd = random.Random(1946)
    star = star_cells(c, c, 5.4)
    for x in range(CAMP):
        for z in range(CAMP):
            d = math.hypot(x - c, z - c)
            if d <= 6.6:
                block = "minecraft:light_blue_concrete" if (x, z) in star else (
                    "minecraft:smooth_quartz" if d <= 6.0 else "minecraft:quartz_bricks")
            elif d <= 10.5:
                block = "minecraft:dirt_path" if rnd.random() < 0.35 else "minecraft:grass_block"
            else:
                block = "minecraft:grass_block"
            s.set(x, 0, z, block)
    # altar de Reishi no centro da estrela
    s.set(c, 0, c, "minecraft:sea_lantern")
    s.set(c, 1, c, "minecraft:chiseled_quartz_block")
    s.set(c, 2, c, "minecraft:quartz_pillar")
    s.set(c, 3, c, "minecraft:light_blue_stained_glass")
    s.set(c, 4, c, "minecraft:end_rod[facing=up]")
    # lanternas espirituais nas 5 pontas da estrela
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        x, z = int(round(c + 7.6 * math.cos(a))), int(round(c + 7.6 * math.sin(a)))
        s.set(x, 1, z, "minecraft:quartz_pillar")
        s.set(x, 2, z, "minecraft:quartz_pillar")
        s.set(x, 3, z, "minecraft:soul_lantern")
    # tendas (norte com o bau; leste e oeste)
    tent(s, c - 2, 0, "south", chest=True)
    tent(s, 0, c + 3, "north")
    tent(s, CAMP - 5, c + 3, "north")
    # treino: alvos em postes ao sul
    for x in (c - 4, c, c + 4):
        s.set(x, 1, CAMP - 2, "minecraft:oak_fence")
        s.set(x, 2, CAMP - 2, "minecraft:target")
    s.set(c - 6, 1, CAMP - 3, "minecraft:hay_block")
    s.set(c + 6, 1, CAMP - 3, "minecraft:hay_block")
    s.set(c + 6, 2, CAMP - 3, "minecraft:hay_block")
    # fogueira de chama azul
    s.set(4, 1, 5, "minecraft:soul_campfire[lit=true,facing=north]")
    s.set(3, 1, 5, "minecraft:spruce_log[axis=x]")
    s.set(4, 1, 6, "minecraft:spruce_log[axis=z]")
    # os tres Quincy: arqueiro, sentinela e o anciao
    for (x, z, v) in ((c - 3.5, c + 2.5, 0), (c + 4.5, c - 1.5, 1), (c + 0.5, 6.5, 2)):
        s.entity(x, 1, z, "isekai_mahou:quincy_npc", {"Variant": Int(v), "PersistenceRequired": Byte(1)})
    n = s.save(os.path.join(D, "structure", "quincy_camp.nbt"), keep_air=True)
    print(f"  quincy_camp: {n} blocos")

    write_json(os.path.join(D, "worldgen/structure/quincy_camp.json"), {
        "type": "minecraft:jigsaw", "biomes": "#isekai_mahou:has_structure/quincy_camp", "step": "surface_structures",
        "spawn_overrides": {}, "terrain_adaptation": "beard_thin", "start_pool": "isekai_mahou:quincy_camp/start",
        "size": 1, "start_height": {"absolute": 0}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80, "use_expansion_hack": False})
    write_json(os.path.join(D, "worldgen/template_pool/quincy_camp/start.json"), {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                "location": "isekai_mahou:quincy_camp", "processors": "minecraft:empty",
                                                "projection": "rigid"}}]})
    write_json(os.path.join(D, "worldgen/structure_set/quincy_camp.json"), {
        "structures": [{"structure": "isekai_mahou:quincy_camp", "weight": 1}],
        "placement": {"type": "isekai_mahou:random_spread_multi", "spacing": 28, "separation": 10, "salt": 1946311011,
                      "exclusion_zones": [
                          {"other_set": "minecraft:villages", "chunk_count": 4},
                          {"other_set": "isekai_mahou:guild_city", "chunk_count": 10},
                          {"other_set": "isekai_mahou:capital_city", "chunk_count": 13},
                          {"other_set": "isekai_mahou:capital_sahra", "chunk_count": 13},
                          {"other_set": "isekai_mahou:capital_nordheim", "chunk_count": 13}]}})
    write_json(os.path.join(MERGE, "data/isekai_mahou/tags/worldgen/biome/has_structure/quincy_camp.json"), {
        "replace": False,
        "values": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                   "minecraft:birch_forest", "minecraft:taiga", "minecraft:snowy_plains", "minecraft:snowy_taiga",
                   "minecraft:savanna", "minecraft:cherry_grove", "minecraft:grove"]})


# =====================================================================================
#  Loot e receitas
# =====================================================================================
def data():
    print("== loot e receitas")
    write_json(os.path.join(D, "loot_table/entities/quincy_npc.json"), {
        "type": "minecraft:entity",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "isekai_mahou:quincy_silver_tube",
                                      "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 0, "max": 2}},
                                                    {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                                                     "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:spectral_arrow",
                                      "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]}]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.3}],
             "entries": [{"type": "minecraft:item", "name": "isekai_mahou:essence_light"}]}]})
    write_json(os.path.join(D, "loot_table/chests/quincy_camp.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 7}, "entries": [
                {"type": "minecraft:item", "name": "isekai_mahou:quincy_silver_tube", "weight": 20,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
                {"type": "minecraft:item", "name": "minecraft:spectral_arrow", "weight": 15,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 4, "max": 12}}]},
                {"type": "minecraft:item", "name": "minecraft:bread", "weight": 12,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}}]},
                {"type": "minecraft:item", "name": "minecraft:iron_nugget", "weight": 10,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 9}}]},
                {"type": "minecraft:item", "name": "minecraft:amethyst_shard", "weight": 8,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                {"type": "minecraft:item", "name": "isekai_mahou:essence_light", "weight": 6},
                {"type": "minecraft:item", "name": "isekai_mahou:essence_wind", "weight": 4},
                {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 6,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]}]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}],
             "entries": [{"type": "minecraft:item", "name": "isekai_mahou:mana_crystal"}]}]})
    write_json(os.path.join(D, "recipe/quincy_silver_tube.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" N ", "NGN", " A "],
        "key": {"N": {"item": "minecraft:iron_nugget"}, "G": {"item": "minecraft:glass_pane"}, "A": {"item": "minecraft:amethyst_shard"}},
        "result": {"id": "isekai_mahou:quincy_silver_tube", "count": 3}})
    write_json(os.path.join(D, "recipe/quincy_cross.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["SIS", " I ", " E "],
        "key": {"S": {"item": "minecraft:string"}, "I": {"item": "minecraft:iron_ingot"}, "E": {"item": "isekai_mahou:essence_light"}},
        "result": {"id": "isekai_mahou:quincy_cross", "count": 1}})


def main():
    build_outfit()
    items()
    npc()
    camp()
    data()
    print("ok")


if __name__ == "__main__":
    main()
