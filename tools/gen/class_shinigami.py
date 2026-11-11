"""
Gera os assets e dados do modulo class_shinigami (classe Shinigami, homenagem a Bleach com nomes e visuais proprios).
Uso (na raiz do projeto):  py -I tools/gen/class_shinigami.py

Gera:
  - traje Shinigami em 4 graus (GeckoLib, mesmas convencoes/ossos do modulo outfits; reaproveita os pinceis de
    tools/gen/outfits.py): geo/item/armor/shinigami_outfit[_<grau>].geo.json + textura + glowmask, reserva vanilla
    textures/models/armor/shinigami_outfit[_<grau>]_layer_1/2.png e os 16 icones das pecas
      I   Shihakusho (quimono negro, faixa branca, hakama, tabi e sandalias, faixa na testa)
      II  Tenente (bracadeira com o distintivo, faixa vermelha, detalhes de prata)
      III Capitao (haori branco com o emblema do esquadrao, gola e punhos de prata)
      IV  Manto da Bankai (casaco longo negro esfarrapado com forro rubro, correntes e Reiatsu que brilha)
  - Zanpakuto (arma do kit, 5 ids) selada + 5 formas de Shikai + 5 de Bankai (modelos com overrides por
    isekai_mahou:release / isekai_mahou:archetype), Doce Espiritual, ovo do espirito
  - Espirito da zanpakuto (GeckoLib): modelo, animacoes e 6 texturas (sem nome + 5 arquetipos) com glowmask
  - texturas dos projeteis (meia-lua da Presa Lunar, esfera do Canhao Rubro), icone da aba do menu
  - dimensao do Mundo Interior (dimension, dimension_type, biome) e receitas
Traducoes: tools/gen/class_shinigami_lang.py
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

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
T = (0, 0, 0, 0)
HD, BD, RA, LA, RL, LL, RB, LB = O.HD, O.BD, O.RA, O.LA, O.RL, O.LL, O.RB, O.LB
fc, OMIT, gem = O.fc, O.OMIT, O.gem


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
#  TRAJE SHINIGAMI (4 graus)
# =====================================================================================
BLACK = pal("07070A", "101015", "1A1A22", "26262F", "363642")
WHITE = pal("8A8C96", "B6B8C2", "D8DAE2", "EEF0F6", "FFFFFF")
STRAW = pal("5A4420", "7E6232", "A0844A", "C4A866", "E2CC8C")
RED = pal("4A0A0A", "7A1414", "B02424", "D9483B", "FF7A6A")
SILVER = pal("464A56", "6E7482", "9CA2B0", "C8CDD8", "EEF2FA")
GOLD = pal("6A4508", "A06C12", "D49A26", "F5C84A", "FFF0A8")
GEM_R = pal("5A0A0A", "C02020", "FF6A5A", "FFE0D8")
GEM_W = pal("3A4A6A", "8FA8D0", "E0F0FF", "FFFFFF")

TIERS = ("base", "adept", "master", "archmage")
LV = {"base": 0, "adept": 1, "master": 2, "archmage": 3}
SHINI_T = {
    "base": O.theme(BLACK, WHITE, WHITE, STRAW, RED, SILVER, GEM_R, "FF5A40"),
    "adept": O.theme(BLACK, WHITE, WHITE, STRAW, RED, SILVER, GEM_R, "FF5A40"),
    "master": O.theme(BLACK, WHITE, SILVER, STRAW, RED, SILVER, GEM_W, "BFE6FF"),
    "archmage": O.theme(BLACK, WHITE, RED, STRAW, RED, pal("2A1E22", "4A3236", "6E4A50", "946A70", "C09098"), GEM_R, "FF3A22"),
}


def shini_mats(P, Th, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = O.v9_common(M, P, Th, lv)
    GL = Th["GLOW"]

    # ---- faixa na testa (hachimaki) com pontas que caem para tras
    def band(r):
        r.fill(ACC[3] if lv in (1, 3) else SEC[3])
        r.row(0, ACC[4] if lv in (1, 3) else SEC[4])
        if r.h > 1:
            r.row(-1, ACC[1] if lv in (1, 3) else SEC[1])
        if lv == 3:
            for i in range(1, r.w - 1, 3):
                r.glow(i, r.h // 2, GL)
        r.noise(0.03)

    M["band"] = band
    M["band_cap"] = lambda r: (r.fill(ACC[2] if lv in (1, 3) else SEC[2]), r.noise(0.03))
    M["band_tail"] = lambda r: (r.fill(ACC[3] if lv in (1, 3) else SEC[3]), r.vshade(0.05, 0.2), r.noise(0.03))

    # ---- kosode (parte de cima do quimono): gola cruzada mostrando o branco por baixo
    def kosode(r, front):
        O.cloth(r, MAIN, period=4, base=2, top=0.05, bottom=0.12)
        if front:
            cx = r.w / 2
            for j in range(r.h):
                # V da gola: duas faixas brancas descendo em diagonal do ombro ao centro
                d = int(round(j * 0.55))
                for k in range(2):
                    xl = int(cx - 3 + d) - k
                    xr = int(cx + 2 - d) + k
                    if j < r.h * 0.75:
                        if xl < cx:
                            r.set(xl, j, SEC[3] if k == 0 else SEC[2])
                        if xr >= cx:
                            r.set(xr, j, SEC[3] if k == 0 else SEC[2])
            # a gola da peça de cima (preta) cruzando por cima da branca
            for j in range(min(r.h, 6)):
                d = int(round(j * 0.55))
                r.set(int(cx - 4 + d), j, MAIN[3])
                r.set(int(cx + 3 - d), j, MAIN[3])
            if lv == 2:
                for j in range(r.h):
                    r.set(0, j, TRIM[3]); r.set(r.w - 1, j, TRIM[3])
        r.row(0, MAIN[3])

    M["kosode_front"] = lambda r: kosode(r, True)
    M["kosode"] = lambda r: kosode(r, False)
    M["kosode_top"] = lambda r: (r.fill(MAIN[2]), r.noise(0.03))

    # ---- obi (faixa branca da cintura) com o no atras
    def obi(r, front=False):
        r.fill(TRIM[3] if lv != 3 else ACC[2])
        r.row(0, TRIM[4] if lv != 3 else ACC[4])
        if r.h > 2:
            r.row(-1, TRIM[1] if lv != 3 else ACC[1])
        if front and lv >= 1:
            # cordao vermelho atravessando a faixa
            for i in range(r.w):
                r.set(i, r.h // 2, ACC[3] if lv != 3 else MAIN[3])
        r.noise(0.03)

    M["obi"] = obi
    M["obi_front"] = lambda r: obi(r, True)

    def obi_knot(r):
        obi(r)
        # o fio de Reiatsu (brilha em todos os graus)
        cx = r.w // 2
        for j in range(r.h):
            r.glow(cx, j, GL)

    M["obi_knot"] = obi_knot

    # ---- mangas largas (furisode curto)
    def sleeve(r):
        O.cloth(r, MAIN, period=3, base=2, top=0.06, bottom=0.14)
        if lv >= 2:
            r.row(-1, TRIM[3])
        if lv == 3 and r.h > 4:
            r.rnd.seed(r.w * 17 + r.h)
            r.ragged(2, sym=True, chance=0.5)

    M["sleeve"] = sleeve
    M["sleeve_in"] = lambda r: (r.fill(SEC[2]), r.vshade(0.1, 0.25), r.noise(0.03))
    M["under"] = lambda r: (O.cloth(r, SEC, period=3, base=3, folds=False), r.row(-1, SEC[1]))

    # ---- hakama (calca-saia preta com pregas)
    def hakama(r, front=False):
        O.cloth(r, MAIN, period=3, base=2, top=0.05, bottom=0.18)
        for i in range(0, r.w, 3):
            r.col(i, MAIN[1])
        if lv == 3 and r.h > 4:
            r.rnd.seed(r.w * 31 + r.h)
            r.ragged(2, sym=True, chance=0.6)
            for i in range(0, r.w, 4):
                r.glow(i, r.h - 4 if r.h > 4 else 0, GL)
        if front and lv >= 1:
            r.row(0, TRIM[2])

    M["hakama"] = hakama
    M["hakama_front"] = lambda r: hakama(r, True)
    M["hakama_in"] = lambda r: (O.cloth(r, MAIN, period=4, base=1, folds=False), r.vshade(0.0, 0.3))
    M["hakama_edge"] = lambda r: (r.fill(MAIN[2]), r.noise(0.03))
    M["legs"] = lambda r: (O.cloth(r, MAIN, period=3, base=2), r.col(r.w // 2, MAIN[1]))

    # ---- tabi (meia branca) e waraji (sandalia de palha)
    M["sock"] = lambda r: (r.fill(SEC[4]), r.vshade(0.04, 0.12), r.col(r.w // 2, SEC[2]) if r.w > 3 else None, r.noise(0.02))
    M["sandal"] = lambda r: (O.wraps(r, LEATH, period=3))
    M["strap_red"] = lambda r: (r.fill(ACC[2]), r.row(0, ACC[3]), r.noise(0.03))

    # ---- bracadeira do tenente (grau II+)
    def armband(r):
        r.fill(SEC[3])
        r.row(0, SEC[4])
        r.row(-1, SEC[1])
        r.noise(0.02)

    M["armband"] = armband

    def badge(r):
        r.fill(TRIM[2] if lv != 2 else SILVER[3])
        # o "kanji" do esquadrao: linhas que brilham
        for j in range(1, r.h - 1):
            r.glow(r.w // 2, j, GL)
        for i in range(1, r.w - 1):
            r.glow(i, r.h // 2, GL)

    M["badge"] = badge

    # ---- haori (graus III e IV) / casaco da Bankai
    def haori_out(r):
        if lv == 2:
            O.cloth(r, SEC, period=4, base=3, top=0.05, bottom=0.12)
            r.col(0, SEC[2]); r.col(-1, SEC[2])
            # emblema do esquadrao: losango com a marca no meio (brilha)
            cx, cy = r.w // 2, min(6, r.h // 3)
            for k in range(-3, 4):
                w = 3 - abs(k)
                for i in range(-w, w + 1):
                    if abs(i) == w:
                        r.set(cx + i, cy + k, MAIN[2])
            for k in range(-2, 3):
                r.glow(cx, cy + k, GL)
            for i in range(-1, 2):
                r.glow(cx + i, cy, GL)
            # padrao de losangos na barra
            for i in range(r.w):
                if (i // 2) % 2 == 0:
                    r.set(i, r.h - 2, MAIN[2])
                r.set(i, r.h - 1, MAIN[3])
        else:
            O.cloth(r, MAIN, period=3, base=2, top=0.05, bottom=0.10)
            r.col(0, ACC[2]); r.col(-1, ACC[2])
            for i in range(1, r.w - 1, 3):
                r.glow(i, r.h - 4 if r.h > 5 else r.h - 1, GL)
            r.rnd.seed(r.w * 7 + r.h * 3)
            r.ragged(4, sym=True, chance=0.75)

    M["haori_out"] = haori_out
    M["haori_in"] = lambda r: (O.cloth(r, SEC if lv == 2 else ACC, period=4, base=2, folds=False), r.vshade(0.0, 0.25))
    M["haori_edge"] = lambda r: (r.fill(SEC[2] if lv == 2 else ACC[1]), r.noise(0.03))

    # ---- gola e punhos (graus III/IV)
    M["collar"] = lambda r: (r.fill(TRIM[3]), r.row(0, TRIM[4]), r.row(-1, TRIM[1]) if r.h > 1 else None, r.noise(0.02))

    def chain(r):
        r.fill(T)
        for j in range(r.h):
            for i in range(r.w):
                link = (j // 2) % 2
                if (i + link) % 2 == 0:
                    r.set(i, j, Th["METAL"][3] if j % 2 == 0 else Th["METAL"][2])

    M["chain"] = chain
    M["reiatsu"] = O.glow_fill(lighten(GL, 0.1), 0.5)
    return M


def shini_model(name, lv):
    m = O.Model(name)
    O.skeleton(m)
    # ---------------- cabeca: faixa na testa com as pontas caindo
    O.ring(m, HD, -4.3, 4.3, -4.3, 4.3, 28.6, 1.2, 0.35, side="band", cap="band_cap")
    m.box(HD, [-0.9, 27.2, 4.6], [0.7, 2.4, 0.3], fc(all="band_tail@1x3"), rot=[12, 0, -10], pivot=[-0.5, 29.6, 4.6])
    m.box(HD, [0.3, 26.6, 4.6], [0.7, 3.0, 0.3], fc(all="band_tail@1x3"), rot=[16, 0, 12], pivot=[0.6, 29.6, 4.6])
    if lv >= 2:
        # prendedor de cabelo de prata atras (capitao)
        m.box(HD, [-1.2, 30.2, 4.3], [2.4, 1.2, 0.8], fc(all="o_metal_flat@2x1"))
        gem(m, HD, [0, 30.8, 5.0], 0.6, "o_gem@1x1")

    # ---------------- tronco: kosode com a gola, obi e o no
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="kosode_front", s="kosode", ew="kosode", u="kosode_top@4x2", d="dark@2x2"), inflate=0.5)
    m.box(BD, [-4, 12.2, -2], [8, 2.2, 4], fc(n="obi_front", s="obi", ew="obi", u="obi@4x2", d="obi@4x2"), inflate=0.85)
    m.box(BD, [-1.4, 12.4, 2.6], [2.8, 1.8, 1.0], fc(all="obi_knot@2x2"))
    m.bone("sashTail", [0, 13.2, 3.3], parent=BD)
    m.box("sashTail", [-0.6, 9.8, 3.2], [1.2, 3.4, 0.4], fc(ns="obi@1x4", ew="obi@1x4", u=OMIT, d=OMIT))
    if lv >= 2:
        # gola de prata/vermelha mais alta
        m.box(BD, [-4.4, 22.8, -2.4], [8.8, 1.4, 4.8], fc(side="collar", u="collar@4x4", d=OMIT), inflate=0.2)
    if lv == 3:
        # correntes cruzando o peito
        O.strap_diag(m, BD, "chain", -3.0, 1, width=0.8, edge="chain")
        gem(m, BD, [0, 18.2, -3.1], [1.4, 1.4, 0.4], "reiatsu@2x2")

    # ---------------- bracos: manga justa (branca por baixo) + manga larga solta (osso proprio, balanca)
    m.box(RA, [-8, 12, -2], [4, 12, 4], fc(side="under", u="kosode_top@2x2", d=OMIT), inflate=0.3)
    m.bone("sleeveRight", [-5.5, 22.5, 0], parent=RA)
    m.box("sleeveRight", [-9.3, 13.6, -2.7], [5.4, 9.2, 5.4], fc(side="sleeve", u="kosode_top@3x3", d="sleeve_in@3x3"), inflate=0.1)
    m.mirror(RA, LA)
    m.bone("sleeveLeft", [5.5, 22.5, 0], parent=LA)
    m.mirror("sleeveRight", "sleeveLeft")
    if lv >= 1:
        # bracadeira com o distintivo no braco esquerdo
        m.box(LA, [3.6, 18.6, -2.4], [4.8, 1.6, 4.8], fc(side="armband@4x1", u=OMIT, d=OMIT), inflate=0.75)
        m.box(LA, [8.7, 18.4, -1.0], [0.3, 2.0, 2.0], fc(all="badge@2x2"))

    # ---------------- pernas: hakama (cintura propria com 4 paineis, abre com o passo) + perna
    O.waist(m, "Legs")
    O.skirt(m, "Legs", (2.2, 2.0, 1.6, 0.8)[lv], "hakama_front", "hakama", "hakama", "hakama_in@4x4", "hakama_edge",
            flare=4, depth=0.6, width=9.6)
    m.box(RL, [-3.9, 2, -2], [4, 10, 4], fc(side="legs", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(RL, LL)

    # ---------------- pes: tabi branca + sandalia de palha com tira vermelha
    m.box(RB, [-3.9, 0, -2], [4, 4, 4], fc(side="sock", u=OMIT, d="sole@2x2"), inflate=0.45)
    m.box(RB, [-4.3, -0.2, -2.9], [4.4, 0.6, 5.2], fc(side="sandal@4x1", u="sandal@4x4", d="sandal@4x4"))
    m.box(RB, [-2.4, 0.4, -2.6], [1.0, 1.4, 0.4], fc(all="strap_red@1x1"))
    m.mirror(RB, LB)

    # ---------------- haori (III) / casaco da Bankai (IV), presos ao tronco
    if lv >= 2:
        O.cape(m, 24.0, (4.0, 0.5)[lv - 2], 2.9, (9.4, 10.0)[lv - 2], out="haori_out", inn="haori_in", edge="haori_edge", flare=3)
        # ombros do haori
        for side in (-1, 1):
            x0 = -9.0 if side < 0 else 4.4
            m.box(RA if side < 0 else LA, [x0, 21.2, -2.6], [4.6, 1.6, 5.2], fc(side="haori_out@4x1", u="haori_edge@4x4", d=OMIT))
    return m


def vanilla_layers(name, main, trim, dark):
    c1 = Canvas(64, 32)
    regions = [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]
    rnd = random.Random(len(name))
    for (x0, y0, w, h) in regions:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = main if (x + y) % 5 else dark
                if rnd.random() < 0.05:
                    c = trim
                c1.set(x, y, c)
    for x in range(16, 40):
        c1.set(x, 28, trim)
        c1.set(x, 29, trim)
    c1.save(os.path.join(A, "textures/models/armor", name + "_layer_1.png"))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(A, "textures/models/armor", name + "_layer_2.png"))


# Icones: K preto, k preto claro, W branco, R vermelho/acento, S sola/palha, G gema/brilho, D contorno
ICON_ROWS = {
    "helmet": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..DDDDDDDDDDDD..",
        "..DWWWWWWWWWWD..",
        "..DWWWWGWWWWWD..",
        "..DDDDDDDDDDDD..",
        "...........DWD..",
        "..........DWWD..",
        "..........DWD...",
        ".........DWD....",
        "................",
        "................",
        "................"],
    "chestplate": [
        "................",
        "..DDDD....DDDD..",
        ".DKKKKDWWDKKKKD.",
        "DKKKKkKWWKkKKKKD",
        "DKKKKKKWWKKKKKKD",
        "DKKKDKKKKKKDKKKD",
        "DKKKDKKKKKKDKKKD",
        "DKKD.DKKKKD.DKKD",
        "DKKD.DKKKKD.DKKD",
        ".DD..DWWWWD..DD.",
        ".....DWGWWD.....",
        ".....DKKKKD.....",
        ".....DKKkKD.....",
        ".....DKKKKD.....",
        "......DDDD......",
        "................"],
    "leggings": [
        "................",
        "...DDDDDDDDDD...",
        "...DWWWWWWWWD...",
        "...DKKKKKKKKD...",
        "..DKKKkKKkKKKD..",
        "..DKKKkKKkKKKD..",
        "..DKKKkDDkKKKD..",
        ".DKKKKkDDkKKKKD.",
        ".DKKKKkDDkKKKKD.",
        ".DKKKKD..DKKKKD.",
        ".DKKKKD..DKKKKD.",
        "DKKKKKD..DKKKKKD",
        "DKKKKKD..DKKKKKD",
        "DDDDDDD..DDDDDDD",
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
        "...DWWD..DWWD...",
        "...DWWD..DWWD...",
        "..DWWWD..DWWWD..",
        ".DWWRWD..DWWRWD.",
        ".DWWWWD..DWWWWD.",
        "DSSSSSD.DSSSSSD.",
        "DDDDDDD.DDDDDDD.",
        "................",
        "................"],
}


def draw_armor_icon(piece, tkey):
    th = SHINI_T[tkey]
    P = th["pal"]
    lv = LV[tkey]
    cols = {"K": P["MAIN"][3], "k": P["MAIN"][2], "W": P["SEC"][3] if not (lv == 3 and piece == "chestplate") else P["ACC"][3],
            "R": P["ACC"][3], "S": P["LEATH"][3], "G": th["GEM"][2], "D": (8, 6, 10, 255)}
    if lv in (1, 3) and piece == "helmet":
        cols["W"] = P["ACC"][3]
    c = Canvas(16, 16)
    for y, row in enumerate(ICON_ROWS[piece]):
        for x, ch in enumerate(row):
            if ch != "." and ch in cols:
                c.set(x, y, cols[ch])
    if piece == "chestplate" and lv == 2:
        # haori branco nos ombros
        for (x, y) in ((1, 3), (2, 3), (13, 3), (14, 3), (1, 4), (14, 4)):
            c.set(x, y, P["SEC"][4])
    if lv >= 1:
        g = th["GEM"]
        c.set(14, 1, g[2])
        if lv >= 2:
            c.set(1, 14, g[2])
        if lv == 3:
            for (x, y) in ((13, 1), (14, 0), (14, 2), (15, 1)):
                c.set(x, y, lighten(th["GLOW"], 0.3))
    name = f"shinigami_{piece}" if lv == 0 else f"shinigami_{tkey_id(tkey)}_{piece}"
    c.save(os.path.join(A, "textures/item", name + ".png"))
    item_model(A, name)


def tkey_id(tkey):
    return {"base": "apprentice", "adept": "adept", "master": "master", "archmage": "archmage"}[tkey]


def build_outfit():
    print("== traje Shinigami")
    for tkey in TIERS:
        lv = LV[tkey]
        th = SHINI_T[tkey]
        name = "shinigami_outfit" if lv == 0 else f"shinigami_outfit_{tkey_id(tkey)}"
        _, glow = O.build(shini_model(name, lv), shini_mats(th["pal"], th, lv))
        if not glow:
            raise RuntimeError(f"{name}: sem glowmask (o Java liga a camada de brilho em todos os graus)")
        P = th["pal"]
        vanilla_layers(name, P["MAIN"][2], P["SEC"][3] if lv != 3 else P["ACC"][3], P["MAIN"][0])
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            draw_armor_icon(piece, tkey)


# =====================================================================================
#  ZANPAKUTO (16x16, diagonal: cabo embaixo a esquerda, ponta em cima a direita)
# =====================================================================================
STEEL = pal("2E3038", "5A5E6A", "9AA0AE", "D2D6E0", "F6F8FF")
GRADE = {  # (tsuba, empunhadura, lamina)
    1: (pal("1A1A1A", "3A3A3A", "6A6A6A", "9A9A9A", "C0C0C0"), pal("3A0A0A", "6A1414", "A02020", "D04040", "F07070"), STEEL),
    2: (pal("1A2A3A", "2E4A6A", "4E7AA6", "7EB0DA", "B8DCF6"), pal("0A1A3A", "142A6A", "2040A0", "4070D0", "70A0F0"),
        pal("2E3848", "5A6A80", "9AB0C8", "D2E4F4", "F6FBFF")),
    3: (pal("5A3A08", "9A6A14", "D4A030", "F2CC5A", "FFF0B0"), pal("1A1A1A", "2A2A2A", "3A3A3A", "5A5A5A", "7A7A7A"), STEEL),
    4: (pal("2A1040", "4A2070", "7A40B0", "A870E0", "D6B4FF"), pal("0A0A0A", "141414", "202020", "303030", "484848"),
        pal("1A1622", "3A3448", "6E6488", "A89CC8", "E6E0F8")),
}


def katana(c, blade, tsuba, grip, length=11, tip_glow=None, edge=None):
    """Katana na diagonal: cabo (envolto em losangos) em (2..4, 13..11), tsuba, lamina ate a ponta."""
    # cabo
    for k in range(4):
        x, y = 1 + k, 14 - k
        c.set(x, y, grip[2] if k % 2 == 0 else grip[3])
        c.set(x + 1, y, grip[1])
    c.set(0, 15, tsuba[2])
    # tsuba (guarda)
    for (dx, dy) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
        c.set(5 + dx, 10 + dy, tsuba[3] if dy < 0 else tsuba[2])
    c.set(5, 10, tsuba[1])
    # lamina
    for k in range(length):
        x, y = 6 + k, 9 - k
        c.set(x, y, blade[3])
        c.set(x, y + 1, blade[2])
        c.set(x - 1, y, (edge or blade)[4])
    if tip_glow:
        c.set(6 + length - 1, 9 - length + 1, tip_glow)


def draw_blade(name, painter):
    c = Canvas(16, 16)
    painter(c)
    c.outline(H("0A0810", 230))
    c.save(os.path.join(A, "textures/item", name + ".png"))


def sealed(grade):
    tsuba, grip, blade = GRADE[grade]

    def p(c):
        katana(c, blade, tsuba, grip, 10)
    return p


def shikai_weight(c):
    # lamina dobrada em angulos retos (gancho quadrado), cinza-chumbo
    tsuba, grip, _ = GRADE[1]
    blade = pal("1E2028", "3A3E4A", "6A707E", "9AA0AE", "C8CCD6")
    for k in range(4):
        c.set(1 + k, 14 - k, grip[2] if k % 2 == 0 else grip[3]); c.set(2 + k, 14 - k, grip[1])
    c.rect(4, 9, 6, 11, tsuba[2])
    # sobe reto, dobra para a direita, sobe de novo, dobra
    pts = [(6, 8), (6, 7), (6, 6), (7, 6), (8, 6), (9, 6), (9, 5), (9, 4), (9, 3), (10, 3), (11, 3), (12, 3), (12, 2), (12, 1)]
    for (x, y) in pts:
        c.set(x, y, blade[3]); c.set(x + 1, y + 1 if (x, y + 1) not in pts else y, blade[1])
    c.set(12, 0, blade[4])
    for (x, y) in ((7, 9), (8, 10)):
        c.set(x, y, H("8A8E98"))


def shikai_petals(c):
    tsuba, grip, _ = GRADE[1]
    blade = pal("5A2A40", "A04A70", "E07AA8", "FFB0D0", "FFE8F2")
    katana(c, pal("4A4E5A", "7A8090", "B4BAC8", "E0E4EE", "FFFFFF"), tsuba, grip, 6)
    rnd = random.Random(7)
    for k in range(18):
        x = 11 + rnd.randint(-3, 4)
        y = 4 + rnd.randint(-4, 3)
        c.set(x, y, blade[rnd.randint(2, 4)])


def shikai_frost(c):
    tsuba = pal("6A7A90", "9AB0C8", "C8DCF0", "E8F4FF", "FFFFFF")
    grip = pal("8AA0C0", "B0C8E8", "D8E8FF", "F0F8FF", "FFFFFF")
    blade = pal("8ACCE8", "B8E4F8", "E0F6FF", "F4FCFF", "FFFFFF")
    katana(c, blade, tsuba, grip, 11, tip_glow=H("FFFFFF"))
    # fita branca saindo do cabo
    for k in range(6):
        c.set(k // 2, 12 + (k % 3) - 1, H("F4F8FF"))
    for (x, y) in ((9, 3), (12, 5), (14, 2)):
        c.set(x, y, H("CFF4FF"))


def shikai_flame(c):
    tsuba, grip, _ = GRADE[3]
    blade = pal("2A1A14", "4A2A1E", "7A3A22", "A05030", "C07040")
    katana(c, blade, tsuba, grip, 11)
    fire = [H("FF4A10"), H("FF8A20"), H("FFD040")]
    for k in range(0, 11, 2):
        x, y = 6 + k, 9 - k
        c.set(x + 1, y - 1, fire[k % 3])
        c.set(x, y - 1, fire[(k + 1) % 3])


def shikai_brush(c):
    # pincel gigante: cabo de madeira escura, cerdas brancas, ponta de tinta preta
    wood = pal("1E1410", "3A2A1E", "5A4230", "7A5A40", "9A7A58")
    for k in range(7):
        x, y = 1 + k, 14 - k
        c.set(x, y, wood[2] if k % 2 else wood[3]); c.set(x + 1, y, wood[1])
    c.set(8, 7, H("D4A030")); c.set(9, 7, H("D4A030")); c.set(8, 6, H("D4A030"))
    for k in range(5):
        x, y = 9 + k, 6 - k
        c.set(x, y, H("F4F4F4") if k < 3 else H("16141C"))
        c.set(x + 1, y + 1, H("D8D8DC") if k < 3 else H("24202C"))
        if k >= 1:
            c.set(x, y + 1, H("E8E8EC") if k < 3 else H("16141C"))
    c.set(15, 0, H("0A080E")); c.set(14, 1, H("16141C"))


def bankai_weight(c):
    # cutelo negro gigante com correntes
    blade = pal("0A0A0E", "16161C", "26262E", "3A3A44", "5A5A66")
    for k in range(5):
        c.set(1 + k, 14 - k, H("6A6E7A")); c.set(2 + k, 14 - k, H("3A3E4A"))
    for y in range(1, 11):
        for x in range(6, 15):
            if x - 6 <= (10 - y) + 3 and x - 6 >= (10 - y) - 3 and 0 <= x < 16:
                c.set(x, y, blade[2] if (x + y) % 5 else blade[3])
    for k in range(9):
        c.set(6 + k, 10 - k, blade[4])
    for (x, y) in ((3, 9), (2, 8), (2, 7), (3, 6), (4, 5)):
        c.set(x, y, H("9AA0AE"))


def bankai_petals(c):
    blade = pal("5A1A34", "C04A80", "FF7AB0", "FFB8D8", "FFF0F8")
    for k in range(4):
        c.set(1 + k, 14 - k, H("2A2A30")); c.set(2 + k, 14 - k, H("4A4A55"))
    rnd = random.Random(11)
    for k in range(40):
        t = rnd.random()
        x = int(5 + t * 10 + rnd.randint(-2, 2))
        y = int(10 - t * 10 + rnd.randint(-2, 2))
        c.set(x, y, blade[rnd.randint(1, 4)])
    for k in range(10):
        c.set(6 + k, 9 - k, blade[4])


def bankai_frost(c):
    blade = pal("4A8AB0", "7ABCE0", "B0E4FF", "E0F6FF", "FFFFFF")
    for k in range(4):
        c.set(1 + k, 14 - k, H("C8DCF0")); c.set(2 + k, 14 - k, H("8AA0C0"))
    for k in range(10):
        x, y = 6 + k, 9 - k
        c.set(x, y, blade[3]); c.set(x, y + 1, blade[2]); c.set(x - 1, y, blade[4])
        if k % 3 == 1:
            c.set(x + 1, y + 2, blade[2]); c.set(x - 1, y - 2, blade[3])
    for (x, y) in ((4, 9), (3, 10), (5, 8)):
        c.set(x, y, blade[1])


def bankai_flame(c):
    for k in range(4):
        c.set(1 + k, 14 - k, H("2A1A10")); c.set(2 + k, 14 - k, H("4A2A1A"))
    blade = pal("0A0604", "1A0E08", "2A1810", "3A2418", "5A3828")
    for k in range(7):
        x, y = 6 + k, 9 - k
        c.set(x, y, blade[3]); c.set(x, y + 1, blade[1])
    ember = [H("FF3A10"), H("FF7A20"), H("FFC040")]
    for k in range(7):
        c.set(6 + k - 1, 9 - k, ember[k % 3])
    c.set(13, 2, H("FFE080"))


def bankai_brush(c):
    blade = pal("08060C", "121018", "1E1A26", "2E2838", "443C54")
    for k in range(4):
        c.set(1 + k, 14 - k, H("E8E8EC")); c.set(2 + k, 14 - k, H("A0A0A8"))
    for k in range(11):
        x, y = 5 + k, 10 - k
        c.set(x, y, blade[2]); c.set(x, y + 1, blade[1])
        c.set(x - 1, y, H("F4F4F4") if k % 3 == 0 else blade[3])
    for (x, y) in ((8, 9), (11, 6), (13, 2), (7, 4), (12, 8)):
        c.set(x, y, H("FFFFFF"))


ARCHS = ("weight", "petals", "frost", "flame", "brush")
SHIKAI = {"weight": shikai_weight, "petals": shikai_petals, "frost": shikai_frost, "flame": shikai_flame, "brush": shikai_brush}
BANKAI = {"weight": bankai_weight, "petals": bankai_petals, "frost": bankai_frost, "flame": bankai_flame, "brush": bankai_brush}


def items():
    print("== itens")
    ids = {"zanpakuto": 1, "zanpakuto_t1": 1, "zanpakuto_t2": 2, "zanpakuto_t3": 3, "zanpakuto_t4": 4}
    for name, grade in ids.items():
        draw_blade(name, sealed(grade))
    for a in ARCHS:
        draw_blade("zanpakuto_shikai_" + a, SHIKAI[a])
        draw_blade("zanpakuto_bankai_" + a, BANKAI[a])
        write_json(os.path.join(A, "models/item", "zanpakuto_shikai_" + a + ".json"),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "isekai_mahou:item/zanpakuto_shikai_" + a}})
        write_json(os.path.join(A, "models/item", "zanpakuto_bankai_" + a + ".json"),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "isekai_mahou:item/zanpakuto_bankai_" + a}})
    overrides = []
    for k, a in enumerate(ARCHS, 1):
        overrides.append({"predicate": {"isekai_mahou:release": 1, "isekai_mahou:archetype": k}, "model": "isekai_mahou:item/zanpakuto_shikai_" + a})
    for k, a in enumerate(ARCHS, 1):
        overrides.append({"predicate": {"isekai_mahou:release": 2, "isekai_mahou:archetype": k}, "model": "isekai_mahou:item/zanpakuto_bankai_" + a})
    for name in ids:
        write_json(os.path.join(A, "models/item", name + ".json"),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "isekai_mahou:item/" + name}, "overrides": overrides})

    # Doce Espiritual: bala vermelha embrulhada com o "rosto" branco
    c = Canvas(16, 16)
    c.circle(8, 8, 4, H("D9483B"))
    c.shade_sphere(8, 8, 4)
    for (x, y) in ((6, 7), (10, 7), (7, 10), (8, 10), (9, 10)):
        c.set(x, y, H("F4F4F4"))
    for (x, y) in ((2, 6), (3, 7), (2, 8), (3, 9), (2, 10)):
        c.set(x, y, H("F0E0A0"))
    for (x, y) in ((13, 6), (12, 7), (13, 8), (12, 9), (13, 10)):
        c.set(x, y, H("F0E0A0"))
    c.outline(H("2A0A0A"))
    c.save(os.path.join(A, "textures/item", "shinigami_spirit_candy.png"))
    item_model(A, "shinigami_spirit_candy")
    write_json(os.path.join(A, "models/item", "zanpakuto_spirit_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})

    # icone da aba do menu: katana na diagonal sobre um circulo vermelho
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 5.6 <= d <= 7.2:
                c.set(x, y, H("D9483B"))
    katana(c, STEEL, GRADE[1][0], GRADE[1][1], 10)
    c.save(os.path.join(A, "textures/gui/tab", "shinigami.png"))

    # Presa Lunar: meia-lua branca (tingida no jogo), a curva para a frente (topo da imagem)
    c = Canvas(64, 32)
    for y in range(32):
        for x in range(64):
            u = (x + 0.5 - 32) / 32.0
            v = (y + 0.5) / 32.0
            outer = 0.15 + 0.75 * (u * u)
            inner = outer + 0.32 * (1 - abs(u)) ** 0.7
            if outer <= v <= inner and abs(u) < 0.98:
                t = (v - outer) / max(1e-3, inner - outer)
                a = int(255 * (1 - t) ** 0.6 * (1 - abs(u) ** 3))
                core = 255 if t < 0.35 else int(255 - 60 * t)
                c.set(x, y, (core, core, core, max(0, min(255, a))))
    c.save(os.path.join(A, "textures/entity", "shinigami_moon_fang.png"))

    # Canhao Rubro: esfera vermelha com nucleo claro e brilho em volta
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16
            if d < 1:
                a = int(255 * (1 - d) ** 0.5)
                col = mix(H("FFE6B0"), H("FF2A10"), min(1, d * 1.6))
                c.set(x, y, (col[0], col[1], col[2], a))
    c.save(os.path.join(A, "textures/entity", "shinigami_kido_orb.png"))


# =====================================================================================
#  ESPIRITO DA ZANPAKUTO (GeckoLib): modelo com UV de caixa, pintura por face, 6 variantes
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


SPIRITS = {
    # robe, faixa/acento, cabelo, pele, olhos (brilham), lamina, cabo, detalhe (brilha), barba, capuz, padrao do robe
    "zanpakuto_spirit": dict(robe="#4A4C56", trim="#2A2B32", hair="#3A3B44", skin="#1A1A20", eyes="#E8ECFF", blade="#9AA0AE",
                             grip="#2A2A2A", detail="#B8BCC8", beard=False, hood=True, pattern=None),
    "zanpakuto_spirit_weight": dict(robe="#3A3E4A", trim="#8A93A8", hair="#1E2028", skin="#C8A888", eyes="#C8D0E8", blade="#6A707E",
                                    grip="#1E2028", detail="#8A93A8", beard=False, hood=False, pattern="chains"),
    "zanpakuto_spirit_petals": dict(robe="#F4E8EE", trim="#C04A80", hair="#FF9AC4", skin="#F0D6C4", eyes="#FF6AA8", blade="#FFB8D8",
                                    grip="#3A2A30", detail="#FF8FC0", beard=False, hood=False, pattern="petals"),
    "zanpakuto_spirit_frost": dict(robe="#EAF6FF", trim="#7ABCE0", hair="#F4FAFF", skin="#F2E2D8", eyes="#7AD8FF", blade="#E0F6FF",
                                   grip="#B0C8E8", detail="#A8ECFF", beard=False, hood=False, pattern="snow"),
    "zanpakuto_spirit_flame": dict(robe="#2A1410", trim="#B03A1A", hair="#E8E4DC", skin="#C89A78", eyes="#FFB040", blade="#4A2A1E",
                                   grip="#D4A030", detail="#FF7A2E", beard=True, hood=False, pattern="flames"),
    "zanpakuto_spirit_brush": dict(robe="#F4F4F2", trim="#16141C", hair="#101016", skin="#EAD8C8", eyes="#FFFFFF", blade="#1A1622",
                                   grip="#3A2A1E", detail="#F2F2F2", beard=False, hood=False, pattern="ink"),
}


def spirit_model(v):
    m = NModel("zanpakuto_spirit")
    robe, trim, hair = C(v["robe"]), C(v["trim"]), C(v["hair"])
    skin, eyes, blade = C(v["skin"]), C(v["eyes"]), C(v["blade"])
    grip, detail = C(v["grip"]), C(v["detail"])
    pattern = v["pattern"]

    def robe_paint(trim_rows=1, front_v=False, patt=True):
        def p(fs, seed):
            shade(fs, robe, seed, 0.04, 0.16)
            rnd = random.Random(seed)
            for n, f in fs.items():
                if n in ("front", "back", "left", "right"):
                    for xx in range(0, f.w, 3):
                        for yy in range(f.h):
                            f.set(xx, yy, darken(f.get(xx, yy), 0.07))
                    for k in range(trim_rows):
                        for xx in range(f.w):
                            f.set(xx, f.h - 1 - k, trim)
                    if patt and pattern:
                        for _ in range(max(1, f.w * f.h // 14)):
                            xx, yy = rnd.randrange(f.w), rnd.randrange(max(1, f.h - trim_rows))
                            if pattern == "chains":
                                f.set(xx, yy, darken(detail, 0.2))
                                if xx + 1 < f.w:
                                    f.set(xx + 1, yy, detail)
                            elif pattern == "petals":
                                f.set(xx, yy, detail)
                            elif pattern == "snow":
                                f.set(xx, yy, lighten(detail, 0.3))
                            elif pattern == "flames":
                                if yy > f.h * 0.5:
                                    f.set(xx, yy, detail, True)
                            elif pattern == "ink":
                                for k in range(3):
                                    if xx + k < f.w:
                                        f.set(xx + k, yy, trim)
            if front_v:
                f = fs["front"]
                cx = f.w // 2
                for yy in range(min(f.h, 7)):
                    d = yy // 2
                    f.set(cx - 2 + d, yy, trim)
                    f.set(cx + 1 - d, yy, trim)
                for xx in range(f.w):
                    f.set(xx, f.h - 3, trim)
                    f.set(xx, f.h - 4, trim)
        return p

    def head_paint(fs, seed):
        shade(fs, skin, seed, 0.03, 0.08)
        f = fs["front"]
        f.rect(0, 0, f.w - 1, 1, hair)
        if v["hood"]:
            # rosto sem traços: só os olhos brilhando na sombra
            f.rect(0, 0, f.w - 1, f.h - 1, darken(skin, 0.2))
            f.set(2, 4, eyes, True)
            f.set(f.w - 3, 4, eyes, True)
        else:
            f.set(1, 3, C("#FFFFFF"))
            f.set(2, 3, eyes, True)
            f.set(f.w - 3, 3, eyes, True)
            f.set(f.w - 2, 3, C("#FFFFFF"))
            f.rect(1, 2, 2, 2, darken(hair, 0.2))
            f.rect(f.w - 3, 2, f.w - 2, 2, darken(hair, 0.2))
            f.rect(3, 6, f.w - 4, 6, darken(skin, 0.25))
        for n in ("left", "right", "back"):
            fs[n].rect(0, 0, fs[n].w - 1, 3 if n != "back" else 7, hair)
        fs["top"].fill(hair)

    def hair_paint(fs, seed):
        shade(fs, hair, seed, 0.08, 0.12)
        fs["front"].fill(CLEAR)
        fs["bottom"].fill(CLEAR)
        f = fs["front"]
        for xx in range(f.w):
            if xx % 3 != 1:
                f.set(xx, 0, hair)
                if xx % 3 == 0:
                    f.set(xx, 1, hair)

    def hood_paint(fs, seed):
        if not v["hood"]:
            for f in fs.values():
                f.fill(CLEAR)
            return
        shade(fs, robe, seed, 0.05, 0.12)
        f = fs["front"]
        for yy in range(1, f.h):
            for xx in range(1, f.w - 1):
                f.set(xx, yy, CLEAR)
        fs["bottom"].fill(CLEAR)

    def tail_paint(fs, seed):
        # rabo de cavalo / cabelo longo (some com o capuz)
        if v["hood"]:
            for f in fs.values():
                f.fill(CLEAR)
            return
        shade(fs, hair, seed, 0.08, 0.15)
        fs["top"].fill(CLEAR)

    def beard_paint(fs, seed):
        if not v["beard"]:
            for f in fs.values():
                f.fill(CLEAR)
            return
        shade(fs, hair, seed, 0.06, 0.1)
        fs["top"].fill(CLEAR)
        fs["back"].fill(CLEAR)

    def blade_paint(fs, seed):
        for n, f in fs.items():
            base = blade if n not in ("bottom",) else darken(blade, 0.3)
            for yy in range(f.h):
                for xx in range(f.w):
                    edge = xx == 0 and n in ("front", "back")
                    f.set(xx, yy, lighten(detail, 0.3) if edge else (lighten(base, 0.1) if (xx + yy) % 4 == 0 else base), edge)

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 12, 0], "root")
    m.bone("head", [0, 24, 0], "body")
    m.bone("hairTail", [0, 30, 3.5], "head")
    m.bone("robeTail", [0, 12, 0], "body")
    m.bone("arm_r", [-5, 22, 0], "body")
    m.bone("arm_l", [5, 22, 0], "body")
    m.bone("sword", [-6, 12.5, 0], "arm_r")
    m.bone("leg_r", [-2, 12, 0], "root")
    m.bone("leg_l", [2, 12, 0], "root")
    # tronco: quimono com a gola em V e a faixa
    m.cube("body", [-4, 12, -2], [8, 12, 4], robe_paint(1, True))
    m.cube("body", [-4.5, 21.5, -2.5], [9, 3, 5], solid(robe, 0.18), inflate=0.05)
    m.cube("robeTail", [-4.5, 3, -2.5], [9, 9, 5], robe_paint(2), inflate=0.05)
    # cabeca, cabelo, capuz (sem nome), cabelo longo e barba (Chama Ancestral)
    m.cube("head", [-4, 24, -4], [8, 8, 8], head_paint)
    m.cube("head", [-4.5, 28, -4.5], [9, 4, 9], hair_paint, inflate=0.1)
    m.cube("head", [-4.5, 24, -4.5], [9, 9, 9], hood_paint, inflate=0.3)
    m.cube("head", [-3, 22, -4.6], [6, 3, 1], beard_paint)
    m.cube("hairTail", [-1.5, 20, 3.5], [3, 10, 2], tail_paint)
    # bracos: mangas largas
    m.cube("arm_r", [-8, 12, -2], [4, 12, 4], robe_paint(2, patt=False))
    m.cube("arm_r", [-8.6, 14, -2.6], [5, 8, 5], robe_paint(1), inflate=0.05)
    m.cube("arm_l", [4, 12, -2], [4, 12, 4], robe_paint(2, patt=False))
    m.cube("arm_l", [3.6, 14, -2.6], [5, 8, 5], robe_paint(1), inflate=0.05)
    # a espada na mao direita: cabo, guarda e lamina apontando para a frente (-z)
    m.cube("sword", [-6.5, 11.5, -2], [1, 1, 4], solid(grip, 0.2, grad=0))
    m.cube("sword", [-7.5, 10.5, -3], [3, 3, 1], solid(detail, 0.3, grad=0))
    m.cube("sword", [-6.5, 11.2, -17], [1, 2, 14], blade_paint)
    # pernas
    m.cube("leg_r", [-4, 0, -2], [4, 12, 4], robe_paint(1, patt=False))
    m.cube("leg_l", [0, 0, -2], [4, 12, 4], robe_paint(1, patt=False))
    return m


def spirit():
    print("== espirito da zanpakuto")
    first = True
    for name, v in SPIRITS.items():
        m = spirit_model(v)
        m.layout()
        m.paint(91)
        if first:
            m.save_geo([2.8, 3.0, 1.2])
            first = False
        m.save_texture(name)
    sword_down = {"sword": {"rotation": kf(0, [70, 0, 0], 1.0, [70, 0, 0])}}
    guard = {"arm_r": {"rotation": kf(0, [-55, 10, 10], 1.0, [-55, 10, 10])},
             "arm_l": {"rotation": kf(0, [-45, -25, 0], 1.0, [-45, -25, 0])},
             "sword": {"rotation": kf(0, [-30, 0, 0], 1.0, [-30, 0, 0])}}
    anim_file("zanpakuto_spirit", {
        "idle": {"loop": True, "length": 3.0, "bones": dict(sword_down, **{
            "body": {"scale": kf(0, [1, 1, 1], 1.5, [1.01, 1.02, 1.01], 3.0, [1, 1, 1])},
            "robeTail": {"rotation": wave(3.0, 1.5, 0)},
            "hairTail": {"rotation": wave(3.0, 4, 2, offset=[10, 0, 0])},
            "arm_r": {"rotation": wave(3.0, 2, 2)},
            "arm_l": {"rotation": wave(3.0, -2, 2)}})},
        "sit": {"loop": True, "length": 4.0, "bones": {
            "root": {"position": kf(0, [0, -7, 0], 4.0, [0, -7, 0])},
            "leg_r": {"rotation": kf(0, [-90, 30, 0], 4.0, [-90, 30, 0])},
            "leg_l": {"rotation": kf(0, [-90, -30, 0], 4.0, [-90, -30, 0])},
            "robeTail": {"rotation": kf(0, [-60, 0, 0], 4.0, [-60, 0, 0])},
            "arm_r": {"rotation": kf(0, [-40, 20, 0], 4.0, [-40, 20, 0])},
            "arm_l": {"rotation": kf(0, [-40, -20, 0], 4.0, [-40, -20, 0])},
            "sword": {"rotation": kf(0, [40, -70, 0], 4.0, [40, -70, 0])},
            "head": {"rotation": kf(0, [8, 0, 0], 2.0, [12, 0, 0], 4.0, [8, 0, 0])},
            "body": {"scale": kf(0, [1, 1, 1], 2.0, [1.015, 1.03, 1.015], 4.0, [1, 1, 1])},
            "hairTail": {"rotation": wave(4.0, 3, 2, offset=[12, 0, 0])}}},
        "walk": {"loop": True, "length": 1.0, "bones": dict(sword_down, **{
            "leg_r": {"rotation": wave(1.0, 28, 0)}, "leg_l": {"rotation": wave(1.0, 28, 0, math.pi)},
            "arm_r": {"rotation": wave(1.0, 15, 0, math.pi)}, "arm_l": {"rotation": wave(1.0, 18, 0)},
            "robeTail": {"rotation": wave(1.0, 6, 0, offset=[4, 0, 0])},
            "hairTail": {"rotation": wave(1.0, 6, 0, offset=[16, 0, 0])}})},
        "run": {"loop": True, "length": 0.6, "bones": {
            "body": {"rotation": kf(0, [12, 0, 0], 0.6, [12, 0, 0])},
            "leg_r": {"rotation": wave(0.6, 45, 0)}, "leg_l": {"rotation": wave(0.6, 45, 0, math.pi)},
            "arm_r": {"rotation": kf(0, [30, 0, 15], 0.6, [30, 0, 15])},
            "arm_l": {"rotation": wave(0.6, 35, 0)},
            "sword": {"rotation": kf(0, [100, 0, 0], 0.6, [100, 0, 0])},
            "robeTail": {"rotation": kf(0, [30, 0, 0], 0.3, [38, 0, 0], 0.6, [30, 0, 0])},
            "hairTail": {"rotation": kf(0, [45, 0, 0], 0.3, [52, 0, 0], 0.6, [45, 0, 0])}}},
        "guard": {"loop": True, "length": 1.0, "bones": dict(guard, **{
            "body": {"rotation": kf(0, [0, 20, 0], 1.0, [0, 20, 0])},
            "leg_r": {"rotation": kf(0, [-15, 0, 5], 1.0, [-15, 0, 5])},
            "leg_l": {"rotation": kf(0, [10, 0, -5], 1.0, [10, 0, -5])},
            "hairTail": {"rotation": wave(1.0, 4, 2, offset=[12, 0, 0])}})},
        "talk": {"loop": True, "length": 1.2, "bones": dict(sword_down, **{
            "head": {"rotation": kf(0, [0, 0, 0], 0.3, [-6, 4, 0], 0.6, [2, -3, 0], 0.9, [-4, 2, 0], 1.2, [0, 0, 0])},
            "arm_l": {"rotation": kf(0, [0, 0, 0], 0.4, [-35, 0, -10], 0.8, [-28, 0, -6], 1.2, [0, 0, 0])}})},
        "yield": {"loop": "hold_on_last_frame", "length": 1.0, "bones": {
            "root": {"position": kf(0, [0, 0, 0], 0.6, [0, -5, 0], 1.0, [0, -5, 0])},
            "leg_r": {"rotation": kf(0, [0, 0, 0], 0.6, [-80, 0, 0], 1.0, [-80, 0, 0])},
            "leg_l": {"rotation": kf(0, [0, 0, 0], 0.6, [10, 0, 0], 1.0, [10, 0, 0])},
            "body": {"rotation": kf(0, [0, 0, 0], 0.6, [20, 0, 0], 1.0, [20, 0, 0])},
            "head": {"rotation": kf(0, [0, 0, 0], 0.6, [25, 0, 0], 1.0, [25, 0, 0])},
            "arm_r": {"rotation": kf(0, [0, 0, 0], 0.6, [-60, 0, 0], 1.0, [-60, 0, 0])},
            "sword": {"rotation": kf(0, [0, 0, 0], 0.6, [-30, 0, 0], 1.0, [-30, 0, 0])},
            "arm_l": {"rotation": kf(0, [0, 0, 0], 0.6, [-30, 0, -10], 1.0, [-30, 0, -10])}}},
        "slash": {"length": 0.4, "bones": {
            "arm_r": {"rotation": kf(0, [-150, 20, 20], 0.12, [-20, -30, -10], 0.4, [-55, 10, 10])},
            "sword": {"rotation": kf(0, [-20, 0, 0], 0.12, [20, 0, 0], 0.4, [-30, 0, 0])},
            "body": {"rotation": kf(0, [0, 30, 0], 0.12, [0, -25, 0], 0.4, [0, 20, 0])}}},
        "cast": {"length": 0.7, "bones": {
            "arm_l": {"rotation": kf(0, [0, 0, 0], 0.2, [-90, -10, 0], 0.5, [-95, -10, 0], 0.7, [-45, -25, 0])},
            "head": {"rotation": kf(0, [0, 0, 0], 0.2, [-8, 0, 0], 0.7, [0, 0, 0])}}},
    })


# =====================================================================================
#  DIMENSAO DO MUNDO INTERIOR + receitas
# =====================================================================================
def data():
    print("== dimensao e receitas")
    write_json(os.path.join(D, "dimension_type/inner_world.json"), {
        "ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "piglin_safe": False, "respawn_anchor_works": False,
        "has_raids": False, "min_y": 0, "height": 256, "logical_height": 256, "monster_spawn_light_level": 0,
        "monster_spawn_block_light_limit": 0, "has_skylight": True, "has_ceiling": False, "ambient_light": 0.35,
        "fixed_time": 6000, "bed_works": False, "infiniburn": "#minecraft:infiniburn_overworld", "effects": "minecraft:overworld"})
    write_json(os.path.join(D, "dimension/inner_world.json"), {
        "type": "isekai_mahou:inner_world",
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": "isekai_mahou:inner_world", "layers": [{"block": "minecraft:air", "height": 1}],
            "lakes": False, "features": False, "structure_overrides": []}}})
    write_json(os.path.join(D, "worldgen/biome/inner_world.json"), {
        "has_precipitation": False, "temperature": 0.6, "downfall": 0.0,
        "effects": {"sky_color": 10079487, "fog_color": 12104904, "water_color": 4159204, "water_fog_color": 329011,
                    "music": {"sound": "minecraft:music.end", "min_delay": 6000, "max_delay": 12000, "replace_current_music": False},
                    "particle": {"options": {"type": "minecraft:white_ash"}, "probability": 0.004}},
        "spawners": {k: [] for k in ("monster", "creature", "ambient", "axolotls", "underground_water_creature", "water_creature",
                                     "water_ambient", "misc")},
        "spawn_costs": {}, "carvers": {}, "features": [[] for _ in range(11)]})
    write_json(os.path.join(D, "recipe/shinigami_spirit_candy.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:sugar"}, {"item": "minecraft:sweet_berries"}, {"item": "isekai_mahou:essence_wind"}],
        "result": {"id": "isekai_mahou:shinigami_spirit_candy", "count": 3}})
    write_json(os.path.join(D, "recipe/zanpakuto.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["  I", " I ", "SE "],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "S": {"item": "minecraft:stick"}, "E": {"item": "isekai_mahou:essence_shadow"}},
        "result": {"id": "isekai_mahou:zanpakuto", "count": 1}})


def main():
    build_outfit()
    items()
    spirit()
    data()
    print("ok")


if __name__ == "__main__":
    main()
