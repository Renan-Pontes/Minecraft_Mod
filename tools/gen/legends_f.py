"""
Assets do modulo "legends_f" — tecnicas de anime das 5 classes magicas (rode da raiz:  py -I tools/gen/legends_f.py)

Texturas de entidade (textures/entity/legends_f/):
  white.png          16   branco liso (pecas solidas coloridas por vertice: vassoura, Kodama, motosserras, cervo, maos)
  grain.png          32   veios de madeira em cinza claro (cabo da vassoura)
  hex.png            64   ladrilho de hexagonos SEM emenda (Barreira Defensiva Geral, Detectar Mana) — branco com alfa
  monarch_sigil.png  128  selo do Monarca das Sombras (aneis, runas, coroa de pontas) — branco com alfa
  contract_seal.png  128  selo do contrato (pentagrama, escrita infernal) — branco com alfa
  contract_page.png  48x64 pergaminho do contrato (linhas de texto, assinatura, lacre) — branco com alfa
  sleep_z.png        32   letra "Z" do sono
Icones de efeito (textures/mob_effect/, 18x18): mana_concealed enchanted_sleep chainsaw_devil faust_contract sage_mode
shadow_monarch.
Traducoes: tools/gen/legends_f_lang.py. Icones dos glifos (textures/gui/glyph/<id>.png): desenhados com as funcoes do
tools/gen/icons.py (mesmo resultado da rodada completa do coordenador), so para os ids deste modulo.
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
from pixelart import Canvas, hex_rgba, lighten, darken  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
TEX = os.path.join(A, "textures", "entity", "legends_f")
EFF = os.path.join(A, "textures", "mob_effect")

GLYPHS = ["mage_mana_sense", "mage_conceal_mana", "mage_general_barrier", "witch_broom", "witch_sleep_curse",
          "witch_envy_hands", "warlock_chainsaw", "warlock_faust", "warlock_bang", "druid_forest_god", "druid_sage_mode",
          "druid_kodama", "necro_arise", "necro_shadow_command", "necro_shadow_exchange", "necro_monarch"]


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def smooth(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


class Mask:
    """Cobertura 0..1 com desenho suave (anti-serrilhado por distancia)."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = [[0.0] * w for _ in range(h)]

    def put(self, x, y, v):
        if 0 <= x < self.w and 0 <= y < self.h and v > self.a[y][x]:
            self.a[y][x] = v

    def line(self, p0, p1, width, strength=1.0):
        (ax, ay), (bx, by) = p0, p1
        dx, dy = bx - ax, by - ay
        l2 = dx * dx + dy * dy
        for y in range(max(0, int(min(ay, by) - width - 2)), min(self.h, int(max(ay, by) + width + 3))):
            for x in range(max(0, int(min(ax, bx) - width - 2)), min(self.w, int(max(ax, bx) + width + 3))):
                px, py = x + 0.5, y + 0.5
                t = 0.0 if l2 == 0 else clamp(((px - ax) * dx + (py - ay) * dy) / l2)
                d = math.hypot(px - (ax + dx * t), py - (ay + dy * t))
                self.put(x, y, clamp(width + 0.5 - d) * strength)

    def ring(self, cx, cy, r, width, strength=1.0):
        ext = r + width + 2
        for y in range(max(0, int(cy - ext)), min(self.h, int(cy + ext) + 1)):
            for x in range(max(0, int(cx - ext)), min(self.w, int(cx + ext) + 1)):
                d = abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r)
                self.put(x, y, clamp(width + 0.5 - d) * strength)

    def disc(self, cx, cy, r, strength=1.0):
        for y in range(max(0, int(cy - r - 2)), min(self.h, int(cy + r + 3))):
            for x in range(max(0, int(cx - r - 2)), min(self.w, int(cx + r + 3))):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                self.put(x, y, clamp(r + 0.5 - d) * strength)

    def poly(self, pts, width, strength=1.0, closed=True):
        n = len(pts)
        for i in range(n if closed else n - 1):
            self.line(pts[i], pts[(i + 1) % n], width, strength)

    def save(self, path, rgb=(255, 255, 255)):
        c = Canvas(self.w, self.h)
        for y in range(self.h):
            for x in range(self.w):
                v = self.a[y][x]
                if v > 0:
                    c.set(x, y, (rgb[0], rgb[1], rgb[2], int(round(clamp(v) * 255))))
        c.save(path)


def rune(m, cx, cy, s, seed, width=1.0, strength=1.0):
    """Runa procedural: 2-4 tracos num quadrado de lado s."""
    rnd = random.Random(seed)
    pts = [(cx + (rnd.random() - 0.5) * s, cy + (rnd.random() - 0.5) * s) for _ in range(rnd.randint(3, 5))]
    for i in range(len(pts) - 1):
        m.line(pts[i], pts[i + 1], width, strength)


# =============================================================================================  texturas
def tex_white():
    c = Canvas(16, 16, (255, 255, 255, 255))
    c.save(os.path.join(TEX, "white.png"))


def tex_grain():
    c = Canvas(32, 32)
    rnd = random.Random(7)
    for y in range(32):
        for x in range(32):
            w = math.sin(y * 0.55 + math.sin(x * 0.2) * 1.4) * 0.5 + 0.5
            v = int(178 + 60 * w + rnd.randint(-8, 8))
            c.set(x, y, (v, v, v, 255))
    for k in range(3):
        cx, cy = rnd.randint(4, 28), rnd.randint(4, 28)
        for a in range(0, 360, 8):
            for r in (1.5, 2.5):
                c.set(int(cx + math.cos(math.radians(a)) * r * 1.6), int(cy + math.sin(math.radians(a)) * r), (150, 150, 150, 255))
    c.save(os.path.join(TEX, "grain.png"))


def tex_hex():
    """Hexagonos de topo chato, periodicos (sem emenda): X = u*6, Y = v*2*sqrt(3)."""
    n = 64
    m = Mask(n, n)
    s3 = math.sqrt(3)
    ap = s3 / 2
    for py in range(n):
        for px in range(n):
            X = (px + 0.5) / n * 6.0
            Y = (py + 0.5) / n * 2 * s3
            best = 9.0
            for i in range(-1, 6):
                for j in range(-1, 4):
                    cx = 1.5 * i
                    cy = s3 * (j + 0.5 * (i & 1))
                    qx, qy = abs(X - cx), abs(Y - cy)
                    d = max(qy, qx * ap + qy * 0.5) / ap
                    best = min(best, d)
            edge = smooth(0.80, 0.93, best) * (1 - smooth(1.0, 1.08, best))
            fill = 0.12 + 0.1 * smooth(0.3, 0.9, best)
            m.a[py][px] = clamp(max(edge, fill))
    m.save(os.path.join(TEX, "hex.png"))


def tex_monarch():
    n = 128
    c = n / 2
    m = Mask(n, n)
    m.ring(c, c, 61, 1.4)
    m.ring(c, c, 56, 0.8, 0.8)
    m.ring(c, c, 40, 1.2)
    m.ring(c, c, 22, 1.0, 0.9)
    for k in range(18):
        a = math.tau * k / 18
        rune(m, c + math.cos(a) * 48, c + math.sin(a) * 48, 7, 100 + k, 0.8, 0.9)
    # coroa de pontas (sete pontas viradas para fora)
    pts = []
    for k in range(14):
        a = math.tau * k / 14 - math.pi / 2
        r = 38 if k % 2 == 0 else 26
        pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
    m.poly(pts, 1.1)
    # olho no centro
    for t in range(-16, 17):
        x = c + t
        h = 9 * (1 - (t / 16) ** 2)
        m.put(int(x), int(c - h), 1.0)
        m.put(int(x), int(c + h), 1.0)
    m.disc(c, c, 5, 1.0)
    m.disc(c, c, 2.2, 0.2)
    m.save(os.path.join(TEX, "monarch_sigil.png"))


def tex_seal():
    n = 128
    c = n / 2
    m = Mask(n, n)
    m.ring(c, c, 60, 1.5)
    m.ring(c, c, 52, 0.9)
    m.ring(c, c, 44, 1.2)
    star = [(c + math.cos(math.tau * k / 5 - math.pi / 2) * 43, c + math.sin(math.tau * k / 5 - math.pi / 2) * 43) for k in range(5)]
    for k in range(5):
        m.line(star[k], star[(k + 2) % 5], 1.2)
    for k in range(24):
        a = math.tau * k / 24
        rune(m, c + math.cos(a) * 56, c + math.sin(a) * 56, 5, 300 + k, 0.7, 0.85)
    m.disc(c, c, 6, 0.9)
    m.save(os.path.join(TEX, "contract_seal.png"))


def tex_page():
    w, h = 48, 64
    m = Mask(w, h)
    for y in range(h):
        for x in range(w):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            m.a[y][x] = 0.28 + 0.12 * smooth(0, 4, edge)
    rnd = random.Random(11)
    m.poly([(2, 2), (w - 3, 2), (w - 3, h - 3), (2, h - 3)], 0.6, 0.9)
    m.line((10, 7), (38, 7), 0.9, 1.0)
    y = 13
    while y < h - 20:
        x0 = 6 + rnd.randint(0, 3)
        x1 = w - 7 - rnd.randint(0, 8)
        x = x0
        while x < x1:
            seg = rnd.randint(2, 6)
            m.line((x, y), (min(x1, x + seg), y + rnd.uniform(-0.4, 0.4)), 0.45, 0.85)
            x += seg + 2
        y += 4
    # assinatura e lacre
    pts = [(8 + i * 2.2, h - 13 + math.sin(i * 1.3) * 2.2) for i in range(10)]
    m.poly(pts, 0.6, 1.0, closed=False)
    m.disc(w - 12, h - 12, 5, 1.0)
    m.disc(w - 12, h - 12, 2.5, 0.5)
    m.save(os.path.join(TEX, "contract_page.png"))


def tex_z():
    m = Mask(32, 32)
    m.line((7, 7), (25, 7), 2.4)
    m.line((25, 7), (7, 25), 2.4)
    m.line((7, 25), (25, 25), 2.4)
    m.save(os.path.join(TEX, "sleep_z.png"))


# =============================================================================================  icones de efeito
def icon(name, draw):
    c = Canvas(18, 18)
    draw(c)
    c.outline((20, 14, 26, 255))
    c.save(os.path.join(EFF, name + ".png"))


def eff_conceal(c):
    cyan, dark = hex_rgba("7FD8E8"), hex_rgba("2A5A66")
    for x in range(3, 15):
        h = 4.2 * (1 - ((x - 8.5) / 6) ** 2)
        c.set(x, int(9 - h), cyan)
        c.set(x, int(9 + h), cyan)
    c.circle(9, 9, 2.6, dark)
    c.line(3, 15, 15, 3, hex_rgba("E8F8FF"))


def eff_sleep(c):
    pink = hex_rgba("F0A0D0")
    c.line(5, 4, 12, 4, pink)
    c.line(12, 4, 5, 11, pink)
    c.line(5, 11, 12, 11, pink)
    c.line(11, 13, 14, 13, lighten(pink, 0.3))
    c.line(14, 13, 11, 16, lighten(pink, 0.3))
    c.line(11, 16, 14, 16, lighten(pink, 0.3))
    c.circle(4, 15, 1.8, hex_rgba("C0182E"))


def eff_chainsaw(c):
    orange, grey, dark = hex_rgba("FF7A1A"), hex_rgba("B8BEC6"), hex_rgba("2A2A30")
    c.rect(2, 8, 6, 13, orange)
    c.rect(6, 9, 15, 12, grey)
    for x in range(6, 16, 2):
        c.set(x, 8, dark)
        c.set(x + 1, 13, dark)
    c.rect(3, 6, 5, 7, dark)


def eff_faust(c):
    paper, red = hex_rgba("E8D8B0"), hex_rgba("C0182E")
    c.rect(4, 3, 13, 15, paper)
    for y in (5, 7, 9, 11):
        c.line(6, y, 11, y, darken(paper, 0.45))
    c.circle(11.5, 13.5, 2.4, red)
    c.line(3, 3, 14, 3, darken(paper, 0.3))


def eff_sage(c):
    orange, gold, dark = hex_rgba("F08A24"), hex_rgba("FFE070"), hex_rgba("3A2008")
    c.circle(9, 9, 6.5, orange)
    for x in range(4, 15):
        h = 3.2 * (1 - ((x - 9) / 5.5) ** 2)
        c.set(x, int(9 - h), gold)
        c.set(x, int(9 + h), gold)
    c.rect(6, 8, 12, 9, dark)


def eff_monarch(c):
    purple, light = hex_rgba("6A3DD8"), hex_rgba("B89BFF")
    c.rect(3, 10, 14, 14, purple)
    for x, top in ((3, 4), (6, 7), (9, 3), (12, 7), (14, 4)):
        c.line(x, 10, x, top, purple)
    c.set(9, 12, light)
    c.set(6, 12, light)
    c.set(12, 12, light)


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(EFF, exist_ok=True)
    tex_white()
    tex_grain()
    tex_hex()
    tex_monarch()
    tex_seal()
    tex_page()
    tex_z()
    icon("mana_concealed", eff_conceal)
    icon("enchanted_sleep", eff_sleep)
    icon("chainsaw_devil", eff_chainsaw)
    icon("faust_contract", eff_faust)
    icon("sage_mode", eff_sage)
    icon("shadow_monarch", eff_monarch)
    print("  texturas: 7 de entidade + 6 icones de efeito")
    import legends_f_lang
    legends_f_lang.main()
    if "--no-icons" not in sys.argv:
        glyph_icons()


def glyph_icons():
    """Icones 32x32 dos glifos deste modulo, com o MESMO desenho que o tools/gen/icons.py faria (tipo, elemento e
    pictograma deduzidos do id/nome/descricao). So grava os ids do legends_f."""
    saved = sys.argv[:]
    sys.argv = [sys.argv[0]]
    try:
        import icons
    finally:
        sys.argv = saved
    specs, _, _ = icons.build_specs()
    out = os.path.join(A, "textures", "gui", "glyph")
    n = 0
    for gid in GLYPHS:
        if gid in specs:
            icons.save_px(icons.make_glyph_icon(specs[gid]), os.path.join(out, gid + ".png"))
            n += 1
    print("  icones dos glifos: %d" % n)

if __name__ == "__main__":
    main()
