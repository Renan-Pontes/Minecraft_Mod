"""
Icones do modulo "legends_h" (onda 10: glifos COMUNS de anime — JoJo, High School DxD, My Hero Academia, Mob Psycho
100, Dragon Ball, Jujutsu Kaisen). Rode da raiz:  py -I tools/gen/legends_h_icons.py [--preview PASTA]

- 17 icones 32x32 dos glifos (textures/gui/glyph/<id>.png), com as funcoes do tools/gen/icons.py (moldura pelo TIPO,
  gradiente na cor do ELEMENTO, pictograma proprio). So grava os ids deste modulo. NAO roda o icons.py inteiro.
- 5 icones 18x18 dos efeitos de status (textures/mob_effect/lh_*.png).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures")
OUT = os.path.join(A, "gui", "glyph")
EFF = os.path.join(A, "mob_effect")


def _load_icons():
    sys.path.insert(0, HERE)
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    saved = sys.argv[:]
    sys.argv = sys.argv[:1]
    try:
        import icons as I  # noqa: E402
    finally:
        sys.argv = saved
    return I


I = _load_icons()


# =====================================================================================================  PICTOGRAMAS
# Tela 20x20. "p" = cor clara do elemento.

@I.pic("lh_king_crimson")
def _(d):
    # relogio partido ao meio, com uma fenda vermelha no meio (o tempo apagado)
    I.clock_face(d, 10.0, 10.0, 8.0, face="white", rim="red", hands=((300, 0.6), (60, 0.8)), g=0)
    d.poly([(9.0, 1.5), (11.6, 1.5), (10.4, 7.0), (12.2, 10.0), (9.6, 13.5), (11.0, 18.5), (8.4, 18.5), (8.8, 13.0),
            (7.2, 10.0), (9.4, 6.6)], "blood", g=4)
    I.sparkle(d, 16.8, 3.2, 1.8, "W", g=6)


@I.pic("lh_epitaph")
def _(d):
    # olho com uma seta curva no meio (o futuro que ele ve)
    I.eye(d, 10.0, 10.0, 1.1, white="white", iris="red", pupil="k", g=0)
    d.arc_arrow(10.0, 10.0, 8.2, 200, 340, 1.4, 3.0, "blood", g=4)


@I.pic("lh_bites_the_dust")
def _(d):
    # relogio com seta girando ao contrario + caveira-gato pequena (a bomba)
    I.clock_face(d, 9.6, 10.4, 7.6, face="white", rim="pink", hands=((270, 0.6), (0, 0.8)), g=0)
    d.arc_arrow(9.6, 10.4, 9.0, 330, 120, 1.5, 3.2, "magenta", g=4)
    I.sparkle(d, 16.6, 3.4, 1.8, "W", g=6)


@I.pic("lh_killer_queen")
def _(d):
    # mao de dedo apontado tocando um botao rosa com cranio de gato
    d.disc(12.0, 12.0, 5.4, "pink", g=0)
    d.disc(12.0, 12.0, 3.2, "magenta", g=1)
    I.skull(d, 12.0, 11.8, 0.42, "bone", g=2, jaw=False)
    d.line(2.5, 4.0, 8.4, 9.4, 2.4, "flesh", g=4)
    d.disc(8.6, 9.6, 1.6, "flesh.l", g=5)
    I.sparkle(d, 4.0, 15.6, 1.8, "flame", g=6)


@I.pic("lh_sheer_heart_attack")
def _(d):
    # tanquinho: esteira, casco cinza e a caveira na frente
    d.rect(2, 14, 17, 17, "black", g=0)
    for x in (4, 8, 12, 16):
        d.disc(x, 15.5, 1.2, "iron", g=1)
    d.rect(4, 8, 15, 13, "iron", g=2)
    d.rect(7, 5, 12, 8, "steel", g=3)
    I.skull(d, 10.0, 10.6, 0.5, "bone", g=4, jaw=False)
    I.sparkle(d, 17.0, 3.0, 1.8, "flame", g=6)


@I.pic("lh_ora_rush")
def _(d):
    # varios punhos em leque socando para a direita (a rajada)
    I.fist(d, 7.0, 5.0, 0.62, "violet", g=0, sleeve="purple")
    I.fist(d, 12.0, 10.0, 0.75, "violet", g=2, sleeve="purple")
    I.fist(d, 7.0, 15.0, 0.62, "violet", g=4, sleeve="purple")
    I.sparkle(d, 17.6, 4.0, 1.6, "W", g=6)
    I.sparkle(d, 17.6, 16.0, 1.6, "W", g=6)


@I.pic("lh_boost_gear")
def _(d):
    # manopla vermelha com a gema verde e "x2" (seta para cima)
    d.poly([(3.0, 18.0), (3.6, 8.0), (7.0, 3.0), (13.0, 3.0), (16.4, 8.0), (17.0, 18.0)], "red", g=0)
    d.poly([(5.0, 8.0), (10.0, 4.4), (15.0, 8.0)], "gold", g=1)
    d.disc(10.0, 11.0, 3.0, "green", g=2)
    d.disc(9.2, 10.2, 0.9, "W", g=3)
    d.arrow(10.0, 18.6, 10.0, 14.6, 1.4, 2.6, "gold", g=4)


@I.pic("lh_boost_transfer")
def _(d):
    # gema verde passando de uma mao para a outra
    d.disc(4.0, 13.0, 3.2, "red", g=0)
    d.disc(16.0, 7.0, 3.2, "gold", g=0)
    d.arrow(5.6, 11.6, 14.0, 8.0, 1.6, 3.4, "green", g=2)
    d.disc(10.0, 10.0, 2.2, "green", g=3)
    d.disc(9.4, 9.4, 0.7, "W", g=4)


@I.pic("lh_balance_breaker")
def _(d):
    # armadura vermelha de dragao com gemas verdes e asas
    d.poly([(1.0, 4.0), (6.0, 7.0), (5.0, 12.0)], "red.d", g=0)
    d.poly([(19.0, 4.0), (14.0, 7.0), (15.0, 12.0)], "red.d", g=0)
    I.chestplate(d, 10.0, 11.0, "red", trim="gold", g=1)
    d.disc(10.0, 9.0, 1.8, "green", g=3)
    d.disc(7.0, 14.0, 1.0, "green", g=3)
    d.disc(13.0, 14.0, 1.0, "green", g=3)


@I.pic("lh_full_cowl")
def _(d):
    # silhueta envolta em raios verdes
    I.figure(d, "stand", 10.0, "teal", g=0)
    I.bolt(d, 4.0, 7.0, 0.5, "green", g=3)
    I.bolt(d, 16.0, 12.0, 0.5, "green", g=3)
    I.bolt(d, 5.0, 15.0, 0.4, "green", g=3)
    I.sparkle(d, 15.6, 3.4, 1.6, "W", g=5)


@I.pic("lh_one_for_all")
def _(d):
    # punho de frente com raios verdes e um "%" de brilho
    I.fist_front(d, 10.0, 10.0, 1.4, "flesh", g=0, sleeve="green")
    I.bolt(d, 3.6, 4.6, 0.45, "green", g=3)
    I.bolt(d, 16.4, 4.6, 0.45, "green", g=3)
    I.sparkle(d, 16.6, 16.0, 1.8, "W", g=5)


@I.pic("lh_delaware_smash")
def _(d):
    # dedo dando peteleco e um anel de ar indo embora
    d.line(2.0, 15.0, 7.0, 11.0, 2.6, "flesh", g=0)
    d.disc(7.4, 10.6, 1.6, "flesh.l", g=1)
    for r, gg in ((4.0, 2), (6.4, 3), (8.8, 4)):
        d.arc(8.0, 10.0, r, r - 1.2, -50, 50, "p", g=gg)
    I.sparkle(d, 17.0, 3.0, 1.6, "W", g=6)


@I.pic("lh_detroit_smash")
def _(d):
    # punho grande socando + onda de choque e nuvens
    I.fist(d, 9.0, 10.0, 1.15, "flesh", g=0, sleeve="green")
    d.arc(12.0, 10.0, 7.6, 6.2, -60, 60, "p", g=3)
    d.arc(12.0, 10.0, 5.2, 4.2, -50, 50, "W", g=4)
    I.cloud(d, 15.0, 16.0, 0.5, "cloud", g=5)


@I.pic("lh_blackwhip")
def _(d):
    # dois tentaculos negros enrolando
    I.tentacle(d, 4.0, 19.0, "black", g=0, s=0.85)
    I.tentacle(d, 15.0, 19.0, "ink", g=2, s=0.8, flip=True)
    d.disc(10.0, 4.0, 1.4, "green", g=4)


@I.pic("lh_psycho_hundred")
def _(d):
    # pedras subindo em volta de uma aura branca e um "100%" pequeno
    d.disc(10.0, 11.0, 6.0, "sky", g=0)
    d.disc(10.0, 11.0, 3.6, "W", g=1)
    I.cube(d, 3.6, 5.0, 0.35, "stone", g=3)
    I.cube(d, 16.4, 4.0, 0.3, "stone", g=3)
    I.cube(d, 15.6, 16.0, 0.32, "stone", g=3)
    d.arrow(4.0, 18.0, 4.0, 11.0, 1.2, 2.6, "magenta", g=5)


@I.pic("lh_genki_dama")
def _(d):
    # esfera azul enorme sobre maos erguidas, com fios de energia chegando
    d.disc(10.0, 7.0, 6.2, "sky", g=0)
    d.disc(10.0, 7.0, 4.0, "W", g=1)
    d.line(1.0, 18.0, 5.0, 11.0, 1.0, "aqua", g=2)
    d.line(19.0, 18.0, 15.0, 11.0, 1.0, "aqua", g=2)
    d.rect(6, 15, 8, 19, "flesh", g=3)
    d.rect(12, 15, 14, 19, "flesh", g=3)


@I.pic("lh_cursed_speech")
def _(d):
    # boca com marcas de presas (o selo) e ondas de som roxas
    d.ellipse(7.0, 10.0, 5.0, 3.0, "pink", g=0)
    d.line(3.0, 10.0, 11.0, 10.0, 1.0, "k", g=1)
    d.disc(4.0, 13.6, 1.0, "purple", g=2)
    d.disc(10.0, 13.6, 1.0, "purple", g=2)
    for r, gg in ((4.0, 3), (6.4, 4), (8.8, 5)):
        d.arc(11.0, 10.0, r, r - 1.2, -45, 45, "purple", g=gg)


# glifo -> (tipo da moldura, elemento, pictograma, selos)
ICONS = {
    "king_crimson": ("effect", "arcane", "lh_king_crimson", []),
    "epitaph": ("augment", "arcane", "lh_epitaph", []),
    "bites_the_dust": ("effect", "arcane", "lh_bites_the_dust", []),
    "killer_queen": ("effect", "fire", "lh_killer_queen", []),
    "sheer_heart_attack": ("form", "fire", "lh_sheer_heart_attack", []),
    "ora_rush": ("effect", "arcane", "lh_ora_rush", []),
    "boost_gear": ("augment", "arcane", "lh_boost_gear", []),
    "boost_transfer": ("effect", "arcane", "lh_boost_transfer", []),
    "balance_breaker": ("effect", "fire", "lh_balance_breaker", []),
    "full_cowl": ("effect", "arcane", "lh_full_cowl", []),
    "one_for_all": ("augment", "arcane", "lh_one_for_all", []),
    "delaware_smash": ("effect", "wind", "lh_delaware_smash", []),
    "detroit_smash": ("effect", "wind", "lh_detroit_smash", []),
    "blackwhip": ("effect", "shadow", "lh_blackwhip", []),
    "psycho_hundred": ("effect", "arcane", "lh_psycho_hundred", []),
    "genki_dama": ("augment", "arcane", "lh_genki_dama", []),
    "cursed_speech": ("effect", "shadow", "lh_cursed_speech", []),
}


# =====================================================================================================  EFEITOS 18x18
def effect_icons():
    from pixelart import Canvas, hex_rgba  # noqa: E402

    def save(name, draw):
        c = Canvas(18, 18)
        draw(c)
        c.save(os.path.join(EFF, name + ".png"))

    def disc(c, cx, cy, r, col):
        for y in range(18):
            for x in range(18):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                    c.set(x, y, col)

    def ring(c, cx, cy, r, w, col):
        for y in range(18):
            for x in range(18):
                if abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r) <= w / 2:
                    c.set(x, y, col)

    def line(c, x0, y0, x1, y1, col, w=1.0):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
        for i in range(n + 1):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            for yy in range(int(y - w), int(y + w) + 1):
                for xx in range(int(x - w), int(x + w) + 1):
                    if 0 <= xx < 18 and 0 <= yy < 18 and math.hypot(xx + 0.5 - x, yy + 0.5 - y) <= w:
                        c.set(xx, yy, col)

    def cowl(c):
        disc(c, 9, 9, 7, hex_rgba("1E5A2A"))
        line(c, 10, 2, 6, 9, hex_rgba("5CFF7A"), 1.1)
        line(c, 6, 9, 11, 9, hex_rgba("5CFF7A"), 1.1)
        line(c, 11, 9, 7, 16, hex_rgba("CFFFD8"), 1.1)

    def scale(c):
        pts = [(3, 3), (15, 3), (15, 10), (9, 16), (3, 10)]
        for y in range(18):
            for x in range(18):
                inside = 3 <= x <= 14 and 3 <= y <= 10 or (y > 10 and abs(x + 0.5 - 9) <= (16 - y) * 0.95)
                if inside:
                    c.set(x, y, hex_rgba("B0102A"))
        disc(c, 9, 8, 2.4, hex_rgba("3CFF6A"))
        disc(c, 8.4, 7.4, 0.8, hex_rgba("E8FFE8"))
        del pts

    def boosted(c):
        disc(c, 9, 9, 5.5, hex_rgba("3CFF6A"))
        disc(c, 9, 9, 3.2, hex_rgba("B8FFC8"))
        line(c, 9, 15, 9, 3, hex_rgba("FFD24A"), 0.9)
        line(c, 9, 3, 5, 7, hex_rgba("FFD24A"), 0.9)
        line(c, 9, 3, 13, 7, hex_rgba("FFD24A"), 0.9)

    def rewind(c):
        disc(c, 9, 9, 7, hex_rgba("FF8CC6"))
        disc(c, 9, 9, 5.6, hex_rgba("FFF0F8"))
        line(c, 9, 9, 9, 4.5, hex_rgba("5A1E4A"), 0.7)
        line(c, 9, 9, 5.5, 9, hex_rgba("5A1E4A"), 0.7)
        ring(c, 9, 9, 7.6, 1.0, hex_rgba("E24AC8"))

    def skip(c):
        ring(c, 9, 9, 6.5, 2.0, hex_rgba("B01040"))
        line(c, 8, 1, 10, 6, hex_rgba("2A0010"), 1.0)
        line(c, 10, 6, 7, 11, hex_rgba("2A0010"), 1.0)
        line(c, 7, 11, 10, 17, hex_rgba("2A0010"), 1.0)

    os.makedirs(EFF, exist_ok=True)
    save("lh_full_cowl", cowl)
    save("lh_scale_mail", scale)
    save("lh_boosted", boosted)
    save("lh_rewind_mark", rewind)
    save("lh_time_skip", skip)
    print("  icones de efeito (legends_h): 5")


def main():
    preview = None
    if "--preview" in sys.argv:
        i = sys.argv.index("--preview")
        preview = sys.argv[i + 1] if i + 1 < len(sys.argv) else None
    os.makedirs(OUT, exist_ok=True)
    items = []
    for gid, (kind, elem, pic, badges) in ICONS.items():
        spec = {"kind": kind, "elem": elem, "pic": pic, "badges": badges}
        img = I.make_glyph_icon(spec)
        I.save_px(img, os.path.join(OUT, gid + ".png"))
        items.append((gid, img))
    print("  icones dos glifos (legends_h): %d" % len(items))
    effect_icons()
    if preview:
        I.sheet(items, 4, 6, os.path.join(preview, "legends_h_icons.png"))
        print("  previa:", os.path.join(preview, "legends_h_icons.png"))


if __name__ == "__main__":
    main()
