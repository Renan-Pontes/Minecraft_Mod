"""
Gera os assets e dados do modulo class_knight (Cavaleiro Runico, v1.0).
Uso (na raiz do projeto):  py -I tools/gen/class_knight.py            (opcional: --preview <pasta>)

Reaproveita os construtores de modelo do armory.py (armas 3D com atlas automatico) e do outfits.py (armadura GeckoLib),
sem alterar esses arquivos. Saidas (todas com ids do modulo):
  - Laminas Runicas runic_blade, runic_blade_t1..t4 e as 6 Espadas Lendarias knight_*: modelo 3D na mao/chao
    (models/item/knight/<id>_held.json + textures/item/knight/<id>.png) e icone 2D no inventario (separate_transforms)
  - Altar do Juramento: modelo de bloco 3D (pedestal com espada cravada), blockstate (4 direcoes), item, loot, receita
  - Armadura em 4 graus (squire, runeguard, templar, runeking): geo/item/armor/knight_<grau>.geo.json, atlas, glowmask
    (graus II-IV), camadas vanilla de reserva, icones e modelos das 16 pecas
  - Icones dos efeitos (mob_effect/knight_blade_aura, knight_rune_mark) e da aba do menu (gui/tab/class_knight)
  - Receitas (lamina I, melhorias na Mesa de Ferraria, altar) e tags (espadas, armaduras, mineable) em src/main/merge/class_knight
"""
import json
import math
import os
import random
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402
import armory as AR  # noqa: E402
import outfits as OF  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
D = os.path.join(ROOT, "src", "main", "resources", "data", "isekai_mahou")
MERGE = os.path.join(ROOT, "src", "main", "merge", "class_knight")
NS = "isekai_mahou"
PREVIEW = None


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def ns(p):
    return p if ":" in p else NS + ":" + p


def H(h, a=255):
    return hex_rgba(h, a)


# =====================================================================================================
#  Materiais extras (pedra para o altar; laminas coloridas)
# =====================================================================================================
class KMat(AR.Mat):
    def _stone(self, c, x0, y0, w, h, rnd):
        """Pedra lavrada: blocos com rejunte escuro e manchas."""
        for yy in range(h):
            for xx in range(w):
                col = self.md
                r = rnd.random()
                if r < 0.18:
                    col = self.lt
                elif r < 0.30:
                    col = self.dk
                if (yy % 4 == 3) or ((xx + (yy // 4) * 3) % 6 == 5):
                    col = darken(self.dk, 0.15)
                c.set(x0 + xx, y0 + yy, col)
        c.set(x0, y0, self.hi)


def KM(style, hi, lt, md, dk, glow=False, accent=None):
    return KMat(style, hi, lt, md, dk, glow=glow, accent=accent)


STONE = KM("stone", "c8c8cc", "a4a4aa", "828288", "56565c")
MOSSY = KM("stone", "b8c8a8", "8a9a7a", "6a7a5a", "44523a")
DARK_STONE = KM("stone", "8a8a98", "5e5e6c", "44444e", "2a2a32")
CRIMSON_BLADE = KM("blade", "fff0e0", "ff9a6a", "d8402a", "7a1408")
STORM_BLADE = KM("blade", "ffffff", "e8f8ff", "a8d8f0", "5a8aa8")
ICE_BLADE = KM("blade", "ffffff", "e8fbff", "b8ecff", "6ab4d8")
ICE_GEM = KM("gem", "ffffff", "f0fcff", "c8f0ff", "7cc4ff", glow=True)
LAKE_BLADE = KM("blade", "ffffff", "f2fbff", "c8dcec", "7a90a8")
GOLD_BLADE = KM("blade", "fffbe8", "fff0b0", "e8c860", "9a7420")
GLOW_STORM = KM("glow", "ffffff", "d8f8ff", "7fdbff", "2a8ab8", glow=True)
GLOW_FROST = KM("glow", "ffffff", "f0fcff", "a8e8ff", "4a9ac8", glow=True)
RUNE_LAKE = KM("rune", "ffffff", "b8f4ff", "4ac0e8", "0a3448", glow=True)


# =====================================================================================================
#  Laminas: modelo 3D (empunhadura em y ~ 8, lamina subindo ate ~31)
# =====================================================================================================
def rune_slots(m, n, y0, y1, mat, z=8.0):
    """n runas quadradas incrustadas no sulco da lamina (cada espaco de runa da arma)."""
    if n <= 0:
        return
    step = (y1 - y0) / n
    for i in range(n):
        cy = y0 + step * (i + 0.5)
        m.cbox(8, cy, z, 0.9, 0.9, 1.15, mat, rot=("z", 45, (8, cy, z)))


def runic_blade(t):
    """Lamina Runica: espada larga de guarda reta com anel de runas; os espacos de runa brilham no sulco."""
    m = AR.Model("runic_blade_t%d" % t)
    metal = (AR.IRON, AR.SILVER, AR.BLUE_STEEL, AR.WHITE_GOLD)[t - 1]
    wrap = (AR.LEATHER, AR.BLUE_WRAP, AR.ROYAL_WRAP, AR.ROYAL_WRAP)[t - 1]
    rune = (AR.RUNE_BLUE, AR.RUNE_BLUE, AR.RUNE_CYAN, AR.RUNE_GOLD)[t - 1]
    gemm = (AR.GEM_PALE_BLUE, AR.GEM_PALE_BLUE, AR.GEM_CYAN, AR.GEM_SUN)[t - 1]
    # pomo em losango com gema
    m.cbox(8, 1.6, 8, 2.4, 2.4, 1.6, metal, rot=("z", 45, (8, 1.6, 8)))
    AR.gem(m, 8, 1.6, 8, 1.0, 1.0, gemm)
    AR.band(m, 2.8, 9.6, wrap, 1.7)
    if t >= 2:
        AR.band(m, 5.8, 6.4, metal, 2.0)
    # guarda reta + anel de runas no centro
    gw = (9.0, 10.0, 10.6, 11.4)[t - 1]
    m.box((8 - gw / 2, 9.6, 7.25), (8 + gw / 2, 10.9, 8.75), metal)
    m.cbox(8 - gw / 2, 10.25, 8, 1.1, 1.6, 1.7, metal, rot=("z", 45, (8 - gw / 2, 10.25, 8)))
    m.cbox(8 + gw / 2, 10.25, 8, 1.1, 1.6, 1.7, metal, rot=("z", 45, (8 + gw / 2, 10.25, 8)))
    AR.diamond_frame(m, 8, 11.2, 1.6 + 0.2 * t, metal, th=0.6, depth=1.4)
    m.cbox(8, 11.2, 8, 1.0, 1.0, 1.6, rune, rot=("z", 45, (8, 11.2, 8)))
    if t >= 3:
        AR.wing(m, 6.6, 11.0, -1, 2.6, 0.8, 22.5, metal)
        AR.wing(m, 9.4, 11.0, 1, 2.6, 0.8, 22.5, metal)
    if t == 4:
        AR.wing(m, 6.4, 12.0, -1, 2.0, 0.6, 45, AR.GOLD, depth=0.8)
        AR.wing(m, 9.6, 12.0, 1, 2.0, 0.6, 45, AR.GOLD, depth=0.8)
    # lamina larga com sulco e os espacos de runa
    bw = 3.0 if t < 4 else 3.4
    m.box((8 - bw / 2, 12.0, 7.6), (8 + bw / 2, 27.4, 8.4), AR.STEEL_BLADE)
    m.box((8 - bw / 2 + 0.5, 27.4, 7.65), (8 + bw / 2 - 0.5, 29.0, 8.35), AR.STEEL_BLADE)
    m.box((7.55, 29.0, 7.7), (8.45, 30.2, 8.3), AR.STEEL_BLADE)
    m.box((7.85, 30.2, 7.75), (8.15, 30.9, 8.25), AR.STEEL_BLADE)
    m.box((7.6, 13.0, 7.52), (8.4, 26.0, 8.48), AR.DARK_BLADE)
    rune_slots(m, t + 1, 13.4, 25.6, rune)
    m.box((8 - bw / 2 - 0.05, 12.0, 7.55), (8 + bw / 2 + 0.05, 12.9, 8.45), metal)  # ricasso
    return m


def legend_model(key):
    """As 6 Espadas Lendarias, cada uma com silhueta propria."""
    m = AR.Model("knight_" + key)
    if key == "lake":
        # Espada do Lago: lamina longa e fina, sulco azul que brilha, guarda dourada de asas para cima, gema do lago
        m.cbox(8, 1.2, 8, 2.2, 2.2, 1.4, AR.WHITE_GOLD, rot=("z", 45, (8, 1.2, 8)))
        AR.gem(m, 8, 1.2, 8, 1.0, 1.0, AR.GEM_CYAN)
        AR.band(m, 2.4, 9.6, AR.ROYAL_WRAP, 1.6)
        AR.band(m, 5.6, 6.2, AR.GOLD, 1.9)
        m.box((4.2, 9.6, 7.3), (11.8, 10.8, 8.7), AR.WHITE_GOLD)
        AR.wing(m, 4.6, 10.2, -1, 2.8, 0.9, 45, AR.WHITE_GOLD)
        AR.wing(m, 11.4, 10.2, 1, 2.8, 0.9, 45, AR.WHITE_GOLD)
        AR.wing(m, 5.2, 11.0, -1, 2.0, 0.6, 22.5, AR.GOLD, depth=0.8)
        AR.wing(m, 10.8, 11.0, 1, 2.0, 0.6, 22.5, AR.GOLD, depth=0.8)
        AR.gem(m, 8, 10.9, 8, 1.4, 1.6, AR.GEM_CYAN, caps=True)
        m.box((6.7, 11.8, 7.65), (9.3, 28.6, 8.35), LAKE_BLADE)
        m.box((7.1, 28.6, 7.7), (8.9, 30.0, 8.3), LAKE_BLADE)
        m.box((7.6, 30.0, 7.75), (8.4, 31.4, 8.25), LAKE_BLADE)
        m.box((7.7, 12.6, 7.55), (8.3, 27.6, 8.45), RUNE_LAKE)
        rune_slots(m, 4, 13.0, 27.0, AR.GEM_CYAN)
    elif key == "caliburn":
        # Caliburn: lamina larga e dourada no centro, guarda reta comprida, gema vermelha, cabo vermelho
        m.cbox(8, 1.6, 8, 2.6, 2.6, 1.6, AR.GOLD, rot=("z", 45, (8, 1.6, 8)))
        AR.gem(m, 8, 1.6, 8, 1.1, 1.1, AR.GEM_RED)
        AR.band(m, 2.8, 9.4, AR.RED_WRAP, 1.8)
        AR.band(m, 4.6, 5.2, AR.GOLD, 2.1)
        AR.band(m, 7.4, 8.0, AR.GOLD, 2.1)
        m.box((2.6, 9.4, 7.2), (13.4, 10.8, 8.8), AR.GOLD)
        m.cbox(2.6, 10.1, 8, 1.4, 2.0, 2.0, AR.GOLD, rot=("z", 45, (2.6, 10.1, 8)))
        m.cbox(13.4, 10.1, 8, 1.4, 2.0, 2.0, AR.GOLD, rot=("z", 45, (13.4, 10.1, 8)))
        m.cbox(8, 10.9, 8, 2.8, 2.8, 2.0, AR.GOLD, rot=("z", 45, (8, 10.9, 8)))
        AR.gem(m, 8, 10.9, 8, 1.3, 1.3, AR.GEM_RED)
        m.box((6.1, 11.8, 7.6), (9.9, 27.2, 8.4), AR.STEEL_BLADE)
        m.box((6.7, 27.2, 7.65), (9.3, 28.8, 8.35), AR.STEEL_BLADE)
        m.box((7.4, 28.8, 7.7), (8.6, 30.2, 8.3), AR.STEEL_BLADE)
        m.box((7.4, 12.6, 7.52), (8.6, 26.0, 8.48), GOLD_BLADE)
        rune_slots(m, 4, 13.0, 25.6, AR.RUNE_GOLD)
    elif key == "flame":
        # Lamina Carmesim: lamina vermelha ondulada com chamas subindo da guarda
        m.cbox(8, 1.4, 8, 2.4, 2.4, 1.6, AR.DARK_IRON, rot=("z", 45, (8, 1.4, 8)))
        AR.gem(m, 8, 1.4, 8, 1.0, 1.0, AR.GEM_AMBER)
        AR.band(m, 2.6, 9.4, AR.BLACK_WRAP, 1.7)
        m.box((4.0, 9.4, 7.3), (12.0, 10.8, 8.7), AR.DARK_IRON)
        for (x, s) in ((4.4, 1), (11.6, -1)):
            AR.prong(m, x, 8, 10.4, 2.2, "z", 22.5 * s, AR.GLOW_FIRE, 0.8)
        AR.gem(m, 8, 10.8, 8, 1.4, 1.4, AR.GEM_AMBER)
        for i, y in enumerate(range(12, 28, 3)):
            off = 0.35 if i % 2 == 0 else -0.35
            m.box((6.6 + off, y, 7.6), (9.4 + off, y + 3.2, 8.4), CRIMSON_BLADE)
        m.box((7.0, 28.0, 7.65), (9.0, 29.6, 8.35), CRIMSON_BLADE)
        m.box((7.6, 29.6, 7.7), (8.4, 31.0, 8.3), CRIMSON_BLADE)
        m.box((7.7, 12.6, 7.52), (8.3, 27.2, 8.48), AR.GLOW_FIRE)
        for (x, y, s) in ((5.9, 12.2, -1), (10.1, 12.2, 1), (5.8, 15.6, -1), (10.2, 15.6, 1), (6.0, 19.0, -1), (10.0, 19.0, 1)):
            AR.prong(m, x, 8, y, 1.6, "z", -22.5 * s, AR.GLOW_EMBER, 0.6)
    elif key == "storm":
        # Espada da Tempestade: lamina clara com raio que brilha, guarda em zigue-zague
        m.cbox(8, 1.4, 8, 2.2, 2.2, 1.4, AR.SILVER, rot=("z", 45, (8, 1.4, 8)))
        AR.gem(m, 8, 1.4, 8, 1.0, 1.0, AR.GEM_CYAN)
        AR.band(m, 2.6, 9.4, AR.BLUE_WRAP, 1.6)
        m.box((5.0, 9.4, 7.3), (11.0, 10.6, 8.7), AR.SILVER)
        AR.prong(m, 5.2, 8, 9.8, 2.6, "z", 45, AR.SILVER, 0.8)
        AR.prong(m, 10.8, 8, 9.8, 2.6, "z", -45, AR.SILVER, 0.8)
        AR.prong(m, 3.4, 8, 11.4, 1.8, "z", -22.5, GLOW_STORM, 0.6)
        AR.prong(m, 12.6, 8, 11.4, 1.8, "z", 22.5, GLOW_STORM, 0.6)
        AR.gem(m, 8, 10.6, 8, 1.3, 1.5, AR.GEM_CYAN, caps=True)
        m.box((6.6, 11.6, 7.6), (9.4, 28.0, 8.4), STORM_BLADE)
        m.box((7.1, 28.0, 7.65), (8.9, 29.6, 8.35), STORM_BLADE)
        m.box((7.6, 29.6, 7.7), (8.4, 30.8, 8.3), STORM_BLADE)
        # raio em zigue-zague no sulco
        pts = [(8.0, 12.4), (7.4, 15.0), (8.6, 17.6), (7.4, 20.2), (8.6, 22.8), (7.8, 25.4), (8.0, 27.4)]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            ang = 22.5 if x1 > x0 else -22.5
            m.cbox(cx, cy, 8, 0.6, abs(y1 - y0) + 0.6, 1.0, GLOW_STORM, rot=("z", -ang, (cx, cy, 8)))
    elif key == "frost":
        # Espada do Gelo Eterno: lamina de gelo e cristais na guarda
        m.cbox(8, 1.4, 8, 2.2, 2.2, 1.4, AR.SILVER, rot=("z", 45, (8, 1.4, 8)))
        AR.gem(m, 8, 1.4, 8, 1.1, 1.1, ICE_GEM, caps=True)
        AR.band(m, 2.6, 9.4, AR.BLUE_WRAP, 1.6)
        m.box((4.8, 9.4, 7.3), (11.2, 10.6, 8.7), AR.SILVER)
        for (x, ang, ln) in ((4.6, 22.5, 2.6), (5.6, 45, 1.8), (11.4, -22.5, 2.6), (10.4, -45, 1.8)):
            AR.prong(m, x, 8, 10.2, ln, "z", ang, ICE_GEM, 0.8)
        m.box((6.5, 11.6, 7.55), (9.5, 27.6, 8.45), ICE_BLADE)
        m.box((7.0, 27.6, 7.6), (9.0, 29.4, 8.4), ICE_BLADE)
        m.box((7.6, 29.4, 7.7), (8.4, 30.8, 8.3), ICE_BLADE)
        m.box((7.75, 12.4, 7.5), (8.25, 27.0, 8.5), GLOW_FROST)
        for (x, y, s) in ((6.3, 16.0, -1), (9.7, 20.0, 1), (6.3, 24.0, -1)):
            AR.prong(m, x, 8, y, 1.4, "z", -45 * s, ICE_GEM, 0.6)
    elif key == "dark":
        # Espada das Trevas: obsidiana com sulco violeta, guarda com espinhos curvos
        m.cbox(8, 1.4, 8, 2.6, 2.6, 1.6, AR.DARK_IRON, rot=("z", 45, (8, 1.4, 8)))
        AR.gem(m, 8, 1.4, 8, 1.1, 1.1, AR.GEM_VIOLET)
        AR.band(m, 2.6, 9.4, AR.BLACK_WRAP, 1.8)
        m.box((4.4, 9.4, 7.2), (11.6, 10.8, 8.8), AR.DARK_IRON)
        AR.prong(m, 4.6, 8, 10.2, 2.4, "z", -22.5, AR.DARK_IRON, 0.8)
        AR.prong(m, 11.4, 8, 10.2, 2.4, "z", 22.5, AR.DARK_IRON, 0.8)
        AR.prong(m, 4.0, 8, 9.6, 2.0, "z", 45, AR.DARK_IRON, 0.7)
        AR.prong(m, 12.0, 8, 9.6, 2.0, "z", -45, AR.DARK_IRON, 0.7)
        AR.gem(m, 8, 10.8, 8, 1.4, 1.4, AR.GEM_VIOLET)
        m.box((6.3, 11.8, 7.6), (9.7, 27.4, 8.4), AR.OBSIDIAN_BLADE)
        m.box((6.9, 27.4, 7.65), (9.1, 29.2, 8.35), AR.OBSIDIAN_BLADE)
        m.box((7.5, 29.2, 7.7), (8.5, 30.8, 8.3), AR.OBSIDIAN_BLADE)
        m.box((7.65, 12.6, 7.52), (8.35, 26.6, 8.48), AR.GLOW_VIOLET)
        AR.prong(m, 9.6, 8, 14.0, 1.6, "z", -45, AR.OBSIDIAN_BLADE, 0.7)
        AR.prong(m, 6.4, 8, 18.0, 1.6, "z", 45, AR.OBSIDIAN_BLADE, 0.7)
    return m


# =====================================================================================================
#  Icones 16x16 das laminas
# =====================================================================================================
def icon_sword(blade, guard, wrap, gemm, fuller, extra=None, wide=False):
    """Espada em diagonal (cabo embaixo a esquerda, ponta em cima a direita), estilo do vanilla."""
    c = Canvas(16, 16)
    bh, bl, bm, bd = AR.P(blade)
    for i in range(10):
        x, y = 5 + i, 10 - i
        c.set(x, y, bm)
        c.set(x, y - 1, bh if i % 3 else bl)
        c.set(x + 1, y, bd)
        if wide and i < 9:
            c.set(x + 1, y - 1, bl)
    c.set(15, 0, bl)
    if fuller is not None:
        fh, fl, fm, fd = AR.P(fuller)
        for i in range(1, 8):
            c.set(5 + i, 10 - i, fl if (i % 2 or fuller.glow) else fm)
    gh, gl, gm, gd = AR.P(guard)
    for (x, y) in ((2, 8), (3, 9), (4, 10), (5, 11), (6, 12), (7, 13)):
        c.set(x, y, gl)
    c.set(3, 8, gh)
    c.set(6, 13, gd)
    c.set(7, 12, gd)
    wh, wl, wm, wd = AR.P(wrap)
    for (x, y) in ((3, 12), (2, 13)):
        c.set(x, y, wl)
        c.set(x + 1, y, wd)
    c.set(4, 11, wm)
    c.set(1, 14, gm)
    c.set(0, 15, gd)
    c.set(1, 15, gd)
    if gemm is not None:
        AR.blob(c, 5.0, 11.0, 0.8, gemm, sparkle=False)
    if extra:
        extra(c)
    glow = []
    return AR.finish(c, glow)


def runic_icon(t):
    metal = (AR.IRON, AR.SILVER, AR.BLUE_STEEL, AR.WHITE_GOLD)[t - 1]
    wrap = (AR.LEATHER, AR.BLUE_WRAP, AR.ROYAL_WRAP, AR.ROYAL_WRAP)[t - 1]
    rune = (AR.RUNE_BLUE, AR.RUNE_BLUE, AR.RUNE_CYAN, AR.RUNE_GOLD)[t - 1]
    gemm = (AR.GEM_PALE_BLUE, AR.GEM_PALE_BLUE, AR.GEM_CYAN, AR.GEM_SUN)[t - 1]

    def runes(c):
        rh, rl, rm, rd = AR.P(rune)
        for k in range(t + 1):
            i = 2 + k * (6 // max(1, t))
            if i <= 8:
                c.set(5 + i, 10 - i, rl)
        if t >= 3:
            c.set(1, 7, AR.P(metal)[2])
            c.set(8, 14, AR.P(metal)[2])

    return icon_sword(AR.STEEL_BLADE, metal, wrap, gemm, AR.DARK_BLADE, runes, wide=t == 4)


def legend_icon(key):
    if key == "lake":
        def ex(c):
            hl = AR.P(AR.WHITE_GOLD)
            c.set(1, 6, hl[1]); c.set(2, 7, hl[1]); c.set(8, 15, hl[1]); c.set(9, 14, hl[1])
        return icon_sword(LAKE_BLADE, AR.WHITE_GOLD, AR.ROYAL_WRAP, AR.GEM_CYAN, RUNE_LAKE, ex)
    if key == "caliburn":
        def ex(c):
            g = AR.P(AR.GOLD)
            c.set(1, 7, g[1]); c.set(8, 14, g[1]); c.set(0, 6, g[2]); c.set(9, 15, g[2])
        return icon_sword(AR.STEEL_BLADE, AR.GOLD, AR.RED_WRAP, AR.GEM_RED, GOLD_BLADE, ex, wide=True)
    if key == "flame":
        def ex(c):
            f = AR.P(AR.GLOW_FIRE)
            for (x, y) in ((7, 6), (9, 4), (11, 2), (8, 9), (10, 7)):
                c.set(x, y, f[1])
            c.set(12, 4, f[2]); c.set(6, 9, f[2])
        return icon_sword(CRIMSON_BLADE, AR.DARK_IRON, AR.BLACK_WRAP, AR.GEM_AMBER, AR.GLOW_FIRE, ex)
    if key == "storm":
        def ex(c):
            s = AR.P(GLOW_STORM)
            for (x, y) in ((8, 4), (10, 6), (12, 2), (1, 7), (8, 14)):
                c.set(x, y, s[1])
        return icon_sword(STORM_BLADE, AR.SILVER, AR.BLUE_WRAP, AR.GEM_CYAN, GLOW_STORM, ex)
    if key == "frost":
        def ex(c):
            s = AR.P(ICE_GEM)
            for (x, y) in ((1, 7), (0, 6), (8, 14), (9, 15), (9, 5), (12, 2)):
                c.set(x, y, s[2])
        return icon_sword(ICE_BLADE, AR.SILVER, AR.BLUE_WRAP, ICE_GEM, GLOW_FROST, ex)

    def ex(c):
        s = AR.P(AR.GLOW_VIOLET)
        d = AR.P(AR.DARK_IRON)
        c.set(1, 7, d[2]); c.set(0, 7, d[3]); c.set(8, 14, d[2]); c.set(8, 15, d[3])
        c.set(10, 6, s[1]); c.set(7, 9, s[1])
    return icon_sword(AR.OBSIDIAN_BLADE, AR.DARK_IRON, AR.BLACK_WRAP, AR.GEM_VIOLET, AR.GLOW_VIOLET, ex)


# =====================================================================================================
#  Saida das laminas
# =====================================================================================================
def build_held(model, name):
    tex = ns("item/knight/" + name)
    try:
        held, atlas = model.build(tex, seed=zlib.crc32(name.encode()) & 0xFFFF)
    except RuntimeError:
        model.atlas = 64
        held, atlas = model.build(tex, seed=zlib.crc32(name.encode()) & 0xFFFF)
    held["display"] = AR.DISPLAY["blade"]
    atlas.save(os.path.join(A, "textures", "item", "knight", name + ".png"))
    write_json(os.path.join(A, "models", "item", "knight", name + "_held.json"), held)
    if PREVIEW:
        AR.preview(model, os.path.join(PREVIEW, name + "_a.png"), yaw=35, pitch=12, center=(8, 16, 8))
        AR.preview(model, os.path.join(PREVIEW, name + "_b.png"), yaw=-60, pitch=20, center=(8, 16, 8))


def wrapper(item_id, held_name):
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": ns("item/" + item_id)}}
    write_json(os.path.join(A, "models", "item", item_id + ".json"), {
        "loader": "neoforge:separate_transforms",
        "gui_light": "front",
        "textures": {"particle": ns("item/" + item_id)},
        "base": {"parent": ns("item/knight/" + held_name + "_held")},
        "perspectives": {"gui": flat, "fixed": flat},
    })


def gen_blades():
    for t in range(1, 5):
        name = "runic_blade_t%d" % t
        build_held(runic_blade(t), name)
        runic_icon(t).save(os.path.join(A, "textures", "item", name + ".png"))
        wrapper(name, name)
    # a arma do kit usa o modelo do grau I
    runic_icon(1).save(os.path.join(A, "textures", "item", "runic_blade.png"))
    wrapper("runic_blade", "runic_blade_t1")
    for key in ("lake", "caliburn", "flame", "storm", "frost", "dark"):
        iid = "knight_caliburn" if key == "caliburn" else "knight_%s_sword" % key
        name = "knight_" + key
        build_held(legend_model(key), name)
        legend_icon(key).save(os.path.join(A, "textures", "item", iid + ".png"))
        wrapper(iid, name)


# =====================================================================================================
#  Altar do Juramento (bloco 3D)
# =====================================================================================================
def altar_model():
    m = AR.Model("knight_oath_altar", atlas=64)
    m.box((1, 0, 1), (15, 2.5, 15), STONE)
    m.box((1.5, 2.5, 1.5), (14.5, 3.2, 14.5), DARK_STONE)
    m.box((3, 3.2, 3), (13, 10, 13), STONE)
    # faixa de runas que brilham nos 4 lados do pilar
    m.box((2.9, 6.0, 2.9), (13.1, 7.0, 13.1), AR.RUNE_BLUE, faces=("north", "south", "east", "west"))
    m.box((2, 10, 2), (14, 12.5, 14), STONE)
    m.box((2.5, 12.5, 2.5), (13.5, 13.0, 13.5), DARK_STONE)
    # pedra em que a espada esta cravada
    m.box((4.5, 13, 4.5), (11.5, 15.5, 11.5), MOSSY)
    m.box((5.5, 15.5, 5.5), (10.5, 16.5, 10.5), MOSSY)
    # espada cravada
    m.box((7.3, 16.5, 7.75), (8.7, 22.5, 8.25), AR.STEEL_BLADE)
    m.box((7.75, 17.0, 7.7), (8.25, 22.0, 8.3), AR.RUNE_GOLD)
    m.box((5.2, 22.5, 7.4), (10.8, 23.6, 8.6), AR.GOLD)
    m.box((7.4, 23.6, 7.4), (8.6, 27.6, 8.6), AR.RED_WRAP)
    m.cbox(8, 28.4, 8, 1.6, 1.6, 1.6, AR.GEM_SUN, rot_y45=True)
    # cantos com runas
    for (x, z) in ((1.6, 1.6), (13.4, 1.6), (1.6, 13.4), (13.4, 13.4)):
        m.cbox(x, 3.0, z, 1.2, 1.2, 1.2, AR.GEM_PALE_BLUE, rot_y45=True)
    return m


def gen_altar():
    m = altar_model()
    model, atlas = m.build(ns("block/knight_oath_altar"), seed=7)
    model["parent"] = "minecraft:block/block"
    model["render_type"] = "minecraft:cutout"
    model["textures"]["particle"] = "minecraft:block/stone_bricks"
    model["display"] = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.5, 0.5, 0.5]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }
    atlas.save(os.path.join(A, "textures", "block", "knight_oath_altar.png"))
    write_json(os.path.join(A, "models", "block", "knight_oath_altar.json"), model)
    write_json(os.path.join(A, "blockstates", "knight_oath_altar.json"), {"variants": {
        "facing=north": {"model": ns("block/knight_oath_altar")},
        "facing=east": {"model": ns("block/knight_oath_altar"), "y": 90},
        "facing=south": {"model": ns("block/knight_oath_altar"), "y": 180},
        "facing=west": {"model": ns("block/knight_oath_altar"), "y": 270},
    }})
    write_json(os.path.join(A, "models", "item", "knight_oath_altar.json"), {"parent": ns("block/knight_oath_altar")})
    write_json(os.path.join(D, "loot_table", "blocks", "knight_oath_altar.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": ns("knight_oath_altar")}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })
    if PREVIEW:
        AR.preview(m, os.path.join(PREVIEW, "altar_a.png"), yaw=35, pitch=20, center=(8, 12, 8))


# =====================================================================================================
#  Armadura (GeckoLib) — base do Cavaleiro da v0.9 com emblema de espada e runas, 4 graus
# =====================================================================================================
TIERS = ["squire", "runeguard", "templar", "runeking"]
TH = {
    # MAIN (placas), SEC (tabardo/capa), TRIM, LEATH, ACC (crista), METAL (ornamentos), GEM, GLOW
    "squire": OF.theme(OF.pal("2C3038", "484E58", "6C7480", "9AA2AE", "D2D8E0"), OF.pal("0E1838", "18285E", "223A86", "3456B0", "6484D8"),
                       OF.BRASS, OF.LEATHER_STD, OF.pal("1A2A5A", "2A4290", "4A6AC8", "7A9AE8", "B8CCFF"), OF.STEEL_P,
                       OF.pal("0A1E5A", "1A3AA0", "5A8AFF", "D0E0FF"), "8FD8FF"),
    "runeguard": OF.theme(OF.pal("3A4048", "5A626E", "828C9A", "B0BAC6", "E4EAF2"), OF.pal("0A1A44", "142C70", "1E42A0", "3A62C8", "6E94EC"),
                          OF.SILVER_M, OF.LEATHER_STD, OF.pal("1A3A6A", "2A5A9A", "4A8AD0", "8AC4F4", "D8F0FF"), OF.SILVER_M,
                          OF.pal("0A2A5A", "1A5AB0", "8FD8FF", "E0F6FF"), "8FD8FF"),
    "templar": OF.theme(OF.pal("4A4E58", "6E747E", "9CA2AC", "C8CED6", "F0F4F8"), OF.pal("8A8A90", "B4B4BA", "D8D8DE", "EEEEF2", "FFFFFF"),
                        OF.GOLD_R, OF.LEATHER_STD, OF.pal("4A3004", "8A5E10", "C8962A", "F0C850", "FFF0B0"), OF.GOLD_R,
                        OF.pal("4A3004", "A07010", "FFD24A", "FFF0B0"), "FFD86B"),
    "runeking": OF.theme(OF.pal("181A22", "2A2E3A", "40465A", "60687E", "9AA2B8"), OF.pal("060C24", "0E1A44", "182C6A", "26449A", "4A6ACC"),
                         OF.GOLD_B, OF.LEATHER_DARK, OF.pal("5A3A06", "9A6A10", "E0B030", "FFE070", "FFFBE0"), OF.GOLD_B,
                         OF.pal("2A4AA0", "6A9AF0", "D8F0FF", "FFFFFF"), "E8F4FF"),
}


def runic_mats(P, T, lv):
    M = OF.knight_mats(P, T, lv)
    MAIN, SEC, TRIM, ACC = P["MAIN"], P["SEC"], P["TRIM"], P["ACC"]
    GL = T["GLOW"]
    lit = lv >= 1

    def emblem(r, cx, cy, light):
        """Espada vertical com guarda e um losango de runas em volta (o brasao do Cavaleiro Runico)."""
        put = OF.put(r, light)
        for j in range(cy - 4, cy + 3):
            r.set(cx, j, TRIM[3])
        r.set(cx, cy - 5, TRIM[4])
        for i in range(cx - 2, cx + 3):
            r.set(i, cy + 1, TRIM[2])
        r.set(cx, cy + 2, ACC[2])
        r.set(cx, cy + 3, TRIM[1])
        for (i, j) in ((cx - 3, cy - 2), (cx + 3, cy - 2), (cx - 2, cy - 4), (cx + 2, cy - 4), (cx - 2, cy), (cx + 2, cy)):
            put(i, j, GL if light else ACC[3])

    def tabard(r, front):
        OF.cloth(r, SEC, period=4, base=2, top=0.06, bottom=0.10)
        r.col(1, SEC[3])
        r.col(-2, SEC[1])
        for j in range(r.h):
            r.clear(0, j)
            r.clear(r.w - 1, j)
        if front and r.w >= 6 and r.h >= 10:
            emblem(r, r.w // 2, 6, lit)

    M["tabard_front"] = lambda r: tabard(r, True)
    M["tabard_back"] = lambda r: tabard(r, False)

    def cape_out(r):
        OF.cloth(r, SEC, period=4, base=2, top=0.06, bottom=0.12)
        r.col(0, TRIM[2])
        r.col(-1, TRIM[2])
        OF.trim_rows(r, TRIM, 2)
        if r.w >= 7 and r.h >= 12:
            emblem(r, r.w // 2, 7, True)
        if lv >= 2:
            for k, j in enumerate(range(3, r.h - 4, 3)):
                r.glow(1, j, GL)
                r.glow(r.w - 2, j, GL)

    M["cape_out"] = cape_out

    def crest(r):
        """Crista de metal em lamina (substitui a pluma): filete claro no meio e borda escura."""
        r.fill(ACC[2])
        cx = r.w // 2
        r.col(cx, ACC[4])
        if r.w > 2:
            r.col(0, ACC[1])
            r.col(-1, ACC[1])
        r.vshade(0.12, 0.12)

    def crest_tip(r):
        crest(r)
        if lit:
            for i in range(r.w):
                r.glow(i, 0, GL)

    M["plume"] = crest
    M["plume_tip"] = crest_tip
    if lv >= 1:
        def cuirass_front(r):
            OF.plate(r, MAIN, TRIM, rivets=False, ridge=True, edge="top")
            if r.w >= 7 and r.h >= 8:
                cx, cy = r.w // 2, r.h // 2
                for (i, j) in ((cx, cy - 2), (cx - 2, cy), (cx + 2, cy), (cx, cy + 2)):
                    r.glow(i, j, GL)
                r.glow(cx, cy, lighten(GL, 0.4))
        M["cuirass_front"] = cuirass_front
    return M


def runic_model(name, lv):
    m = OF.knight_model(name, lv)
    # faixa de runas nos bracos ja no grau II (no modelo antigo so a partir do III)
    if lv == 1:
        m.box(OF.RA, [-8, 13.4, -2], [4, 0.8, 4], OF.fc(side="o_rune_band", u="o_metal_flat@2x2", d="o_metal_flat@2x2"), inflate=1.0)
        OF.mirror_new(m, OF.RA, OF.LA, len(m.by[OF.RA]["cubes"]) - 1)
    # Rei Runico: aureola de runas sobre o elmo
    if lv == 3:
        OF.ring(m, OF.HD, -3.2, 3.2, -3.2, 3.2, 34.2, 0.5, 0.35, side="o_rune_band", cap="o_metal_flat")
    return m


def gen_armor():
    for lv, tier in enumerate(TIERS):
        th = TH[tier]
        name = "knight_" + tier
        _, glow = OF.build(runic_model(name, lv), runic_mats(th["pal"], th, lv))
        want = lv > 0
        if glow != want:
            raise RuntimeError("%s: glowmask=%s, esperado %s" % (name, glow, want))
        P = th["pal"]
        armor_layers(name, P["MAIN"][2], P["TRIM"][2], P["SEC"][2], P["MAIN"][0])
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            iid = "%s_%s" % (name, piece)
            armor_icon(piece, th, lv).save(os.path.join(A, "textures", "item", iid + ".png"))
            write_json(os.path.join(A, "models", "item", iid + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": ns("item/" + iid)}})


def armor_layers(name, main, trim, sec, dark):
    c1 = Canvas(64, 32)
    rnd = random.Random(len(name))
    for (x0, y0, w, h) in [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                col = main if (x + y) % 5 else dark
                if rnd.random() < 0.06:
                    col = trim
                c1.set(x, y, col)
    for y in range(20, 32):
        for x in range(21, 27):
            c1.set(x, y, sec)
    c1.save(os.path.join(A, "textures", "models", "armor", name + "_layer_1.png"))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(A, "textures", "models", "armor", name + "_layer_2.png"))


ICON_ROWS = {
    "helmet": ["....tttt....",
               "...tmmmmt...",
               "..tmllmmdt..",
               "..mlmmmmmd..",
               "..mggggggd..",
               "..mmm..mmd..",
               "..md....md.."],
    "chestplate": ["ttt......ttt",
                   "tmmt....tmmt",
                   "tmlmssssmmdt",
                   ".mlmsrrsmmd.",
                   ".mlmsrrsmmd.",
                   ".mlmsssmmmd.",
                   ".mmmsssmmdd.",
                   ".mmmmmmmmdd.",
                   ".dddddddddd."],
    "leggings": [".tttttttttt.",
                 ".mlmmmmmmmd.",
                 ".mlmm..mmmd.",
                 ".mlm....mmd.",
                 ".mlm....mmd.",
                 ".mgm....mgd.",
                 ".mlm....mmd.",
                 ".ddd....ddd."],
    "boots": [".mlm....mmd.",
              ".mlm....mmd.",
              ".mgm....mgd.",
              "mmlmm..mmmdd",
              "ddddd..ddddd"],
}


def armor_icon(piece, th, lv):
    P = th["pal"]
    MAIN, SEC, TRIM = P["MAIN"], P["SEC"], P["TRIM"]
    gem = th["GEM"]
    pal = {"m": MAIN[2], "l": MAIN[3], "d": MAIN[1], "t": TRIM[2], "s": SEC[2], "r": TRIM[3] if lv == 0 else gem[2],
           "g": TRIM[3] if lv == 0 else gem[2]}
    rows = ICON_ROWS[piece]
    c = Canvas(16, 16)
    oy = (16 - len(rows)) // 2
    c.from_rows(rows, pal, ox=2, oy=oy)
    c.outline(H("16121e"))
    if lv >= 2:
        c.set(13, 1, gem[3])
    if lv == 3:
        c.set(2, 1, gem[3])
        c.set(14, 2, gem[2])
    return c


# =====================================================================================================
#  Icones: efeitos (18x18) e aba do menu (16x16)
# =====================================================================================================
def effect_icons():
    # Aura de Espada: lamina dourada com brilho
    c = Canvas(18, 18)
    gold = [H("8a5e10"), H("c8962a"), H("f0c850"), H("fff0b0")]
    for i in range(11):
        x, y = 4 + i, 13 - i
        c.set(x, y, gold[2])
        c.set(x, y - 1, gold[3])
        c.set(x + 1, y, gold[1])
    for (x, y) in ((2, 11), (3, 12), (4, 13), (5, 14), (6, 15)):
        c.set(x, y, gold[1])
    c.set(2, 15, gold[0])
    c.set(3, 14, gold[0])
    for (x, y) in ((3, 3), (14, 9), (9, 15), (6, 5)):
        c.set(x, y, H("ffffff"))
    c.outline(H("3a2a08"))
    c.save(os.path.join(A, "textures", "mob_effect", "knight_blade_aura.png"))
    # Marca Runica: losango azul com runa
    c = Canvas(18, 18)
    blue = [H("0a2a5a"), H("2a6ab0"), H("8fd8ff"), H("e0f6ff")]
    for y in range(18):
        for x in range(18):
            d = abs(x - 8.5) + abs(y - 8.5)
            if d <= 7.5:
                c.set(x, y, blue[1] if d > 6 else blue[0])
    for j in range(4, 14):
        c.set(9, j, blue[2])
    for i in range(6, 13):
        c.set(i, 7, blue[2])
    c.set(7, 11, blue[3])
    c.set(11, 11, blue[3])
    c.set(9, 4, blue[3])
    c.outline(H("06101e"))
    c.save(os.path.join(A, "textures", "mob_effect", "knight_rune_mark.png"))
    # aba do menu: espada cravada na pedra
    c = Canvas(16, 16)
    steel = [H("5a6474"), H("a8b2c0"), H("e0e6ee"), H("ffffff")]
    for j in range(2, 10):
        c.set(7, j, steel[2])
        c.set(8, j, steel[1])
    c.set(7, 1, steel[3])
    for i in range(4, 12):
        c.set(i, 10, H("f0c850"))
    c.set(7, 11, H("b8303c"))
    c.set(8, 11, H("801c26"))
    c.set(7, 12, H("ffe27a"))
    stone = [H("56565c"), H("828288"), H("a4a4aa")]
    for j in range(12, 16):
        for i in range(2 + (15 - j) // 2, 14 - (15 - j) // 2):
            c.set(i, j, stone[(i + j) % 3])
    c.set(5, 5, H("8fd8ff"))
    c.set(10, 3, H("8fd8ff"))
    c.outline(H("16121e"))
    c.save(os.path.join(A, "textures", "gui", "tab", "class_knight.png"))


# =====================================================================================================
#  Receitas e tags
# =====================================================================================================
def advancement(recipe_id, item):
    write_json(os.path.join(D, "advancement", "recipes", "class_knight", recipe_id + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": ns(recipe_id)}},
        },
        "requirements": [["has_item", "has_the_recipe"]],
        "rewards": {"recipes": [ns(recipe_id)]},
    })


def recipes():
    R = os.path.join(D, "recipe")
    write_json(os.path.join(R, "runic_blade_t1.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" I ", "ELE", " S "],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "E": {"item": ns("essence_light")},
                "L": {"item": "minecraft:iron_sword"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": ns("runic_blade_t1"), "count": 1},
    })
    advancement("runic_blade_t1", ns("essence_light"))
    ups = [("runic_blade_t2", [ns("runic_blade"), ns("runic_blade_t1")], ns("essence_light"), "minecraft:diamond"),
           ("runic_blade_t3", [ns("runic_blade_t2")], ns("essence_earth"), ns("mana_crystal")),
           ("runic_blade_t4", [ns("runic_blade_t3")], ns("guardian_core_light"), "minecraft:netherite_ingot")]
    for out, bases, template, addition in ups:
        write_json(os.path.join(R, out + "_upgrade.json"), {
            "type": "minecraft:smithing_transform",
            "template": {"item": template},
            "base": [{"item": b} for b in bases],
            "addition": {"item": addition},
            "result": {"id": ns(out), "count": 1},
        })
        advancement(out + "_upgrade", bases[0])
    write_json(os.path.join(R, "knight_oath_altar.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" S ", "BEB", "CCC"],
        "key": {"S": {"item": "minecraft:iron_sword"}, "B": {"item": "minecraft:stone_bricks"},
                "E": {"item": ns("essence_light")}, "C": {"item": "minecraft:chiseled_stone_bricks"}},
        "result": {"id": ns("knight_oath_altar"), "count": 1},
    })
    advancement("knight_oath_altar", ns("essence_light"))


def tags():
    blades = [ns("runic_blade")] + [ns("runic_blade_t%d" % t) for t in range(1, 5)] + \
             [ns(i) for i in ("knight_lake_sword", "knight_caliburn", "knight_flame_sword", "knight_storm_sword",
                              "knight_frost_sword", "knight_dark_sword")]
    T = os.path.join(MERGE, "data", "minecraft", "tags", "item")
    write_json(os.path.join(T, "swords.json"), {"replace": False, "values": blades})
    write_json(os.path.join(MERGE, "data", "c", "tags", "item", "tools", "melee_weapon.json"), {"replace": False, "values": blades})
    for piece, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        write_json(os.path.join(T, tag + ".json"), {"replace": False, "values": [ns("knight_%s_%s" % (t, piece)) for t in TIERS]})
    write_json(os.path.join(MERGE, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": [ns("knight_oath_altar")]})


def main():
    global PREVIEW
    if "--preview" in sys.argv:
        PREVIEW = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(PREVIEW, exist_ok=True)
    print("== class_knight: laminas")
    gen_blades()
    print("== class_knight: altar")
    gen_altar()
    print("== class_knight: armadura")
    gen_armor()
    print("== class_knight: icones, receitas e tags")
    effect_icons()
    recipes()
    tags()
    print("ok")


if __name__ == "__main__":
    main()
