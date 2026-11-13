"""
Icones 32x32 dos glifos do modulo "auto_glyphs" (v1.5: glifos de fazenda dos pedestais).
Rode da raiz:  py -I tools/gen/auto_glyphs_icons.py [--preview PASTA]

Usa as funcoes do tools/gen/icons.py (moldura pelo TIPO, gradiente na cor do ELEMENTO, pictograma com contorno e
sombreado automaticos), com pictogramas proprios deste modulo. So grava os 11 ids deste modulo em
textures/gui/glyph/. NAO roda o icons.py inteiro.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures", "gui", "glyph")


def _load_icons():
    sys.path.insert(0, HERE)
    saved = sys.argv[:]
    sys.argv = sys.argv[:1]
    try:
        import icons as I  # noqa: E402
    finally:
        sys.argv = saved
    return I


I = _load_icons()


# =====================================================================================================  PICTOGRAMAS
# Tela 20x20. "p" = cor clara do elemento (Arcano), materiais do MAT_BASE, ".l/.d/.h" fixam o tom.

def axe(d, x0, y0, x1, y1, head="steel", handle="wood", g=0):
    """Machado: cabo de (x0,y0) (base) a (x1,y1) (topo), lamina para a esquerda do topo."""
    d.line(x0, y0, x1, y1, 1.8, handle, g=g)
    d.poly([(x1 - 0.6, y1 - 1.0), (x1 - 5.4, y1 - 2.6), (x1 - 6.0, y1 + 1.6), (x1 - 4.4, y1 + 3.6), (x1 + 0.6, y1 + 2.4)], head, g=g + 1)
    d.line(x1 - 5.6, y1 - 1.8, x1 - 5.0, y1 + 2.8, 1.0, head + ".h", g=g + 2)


def torch(d, cx, top, h=9.0, g=0):
    d.rect(int(cx - 0.5), int(top + 2), int(cx + 0.5), int(top + h), "wood", g=g)
    I.flame(d, cx, top + 3.2, 0.42, "fire", "flame", "W", g=g + 1)


@I.pic("ag_quarry")
def _(d):
    # cava em degraus (camadas) vista de frente + seta para baixo + picareta
    d.rect(0, 6, 19, 7, "grass", g=0)
    d.rect(0, 8, 19, 19, "stone", g=1)
    d.rect(2, 8, 17, 10, "k", g=2)
    d.rect(4, 11, 15, 13, "k", g=2)
    d.rect(6, 14, 13, 16, "k", g=2)
    d.rect(8, 17, 11, 18, "k", g=2)
    for (x, y) in ((1, 11), (17, 13), (3, 16), (15, 17)):
        d.put(x, y, "stone.d", g=3)
    d.put(16, 10, "cyan", g=3)
    d.arrow(9.5, 7.6, 9.5, 16.6, 1.8, 3.4, "p", g=5)
    I.pickaxe(d, 14.0, 3.6, 0.52, "steel", "wood", g=7)


@I.pic("ag_lumberjack")
def _(d):
    I.tree_canopy(d, 8.0, 6.4, "leaf", g=0, s=0.86)
    d.rect(7, 9, 9, 18, "wood", g=2)
    d.rect(3, 18, 13, 19, "dirt", g=3)
    axe(d, 18.0, 17.6, 15.6, 8.4, "steel", "wood.l", g=5)
    d.line(10.4, 13.0, 12.4, 13.0, 0.9, "W", g=8)


@I.pic("ag_arcane_fishing")
def _(d):
    I.waves(d, (14, 18), "water", 1.2, 1.8, 1.0, 19.0, g=0)
    # peixe pulando (corpo laranja, cauda, olho)
    d.ellipse(7.6, 8.8, 4.6, 2.5, "orange", g=3, rot=-25)
    d.tri((11.2, 6.6), (14.6, 3.6), (14.4, 8.4), "orange", g=4)
    d.put(5, 8, "k")
    d.line(6.4, 11.4, 8.6, 10.4, 0.8, "orange.d", g=5)
    # linha e anzol
    d.line(17.0, 0.4, 17.0, 10.4, 0.8, "W", g=6)
    d.arc(15.8, 10.8, 1.7, 0.9, 0, 180, "steel", g=7)
    I.sparkle(d, 2.6, 2.6, 2.0, "p", g=9)


@I.pic("ag_illuminate")
def _(d):
    d.rect(1, 18, 18, 19, "stone", g=0)
    torch(d, 6.0, 5.4, 12.6, g=1)
    torch(d, 14.0, 8.4, 9.6, g=3)
    for (x, y) in ((2, 6), (10, 4), (17, 7), (11, 10)):
        d.put(x, y, "yellow.l", g=6)
    I.sparkle(d, 10.0, 2.0, 2.0, "W", g=7)


@I.pic("ag_on_ripe")
def _(d):
    d.rect(0, 17, 19, 18, "dirt", g=0)
    I.wheat(d, 6.0, 17.4, 14.0, "gold", g=1)
    I.wheat(d, 12.6, 17.4, 12.0, "gold", g=5, lean=0.8)
    I.sparkle(d, 16.6, 3.6, 2.6, "W", g=9)


@I.pic("ag_on_empty")
def _(d):
    for i, y in enumerate((11, 14, 17)):
        d.rect(1, y, 18, y + 1, "dirt" if i % 2 == 0 else "dirt.d", g=i)
    # broto de um lado; o buraco vazio marcado pelo anel do elemento
    d.line(4.4, 10.6, 4.4, 7.4, 1.0, "grass", g=4)
    d.ellipse(5.6, 7.0, 1.6, 0.9, "grass", g=4, rot=-30)
    d.ring(13.2, 7.6, 3.6, 2.4, "p", g=6)
    d.put(13, 7, "p.h", g=7)


@I.pic("ag_on_items")
def _(d):
    d.rect(0, 17, 19, 18, "grass", g=0)
    I.cube(d, 6.0, 11.0, 0.5, "wood", g=1)
    I.cube(d, 13.4, 12.0, 0.45, "stone", g=2)
    d.ellipse(10.0, 15.4, 1.6, 1.0, "gold", g=3)
    d.ellipse(16.4, 15.8, 1.2, 0.9, "red", g=4)
    d.arrow(10.0, 0.8, 10.0, 6.4, 1.6, 3.0, "p", g=6)


@I.pic("ag_on_work")
def _(d):
    # engrenagem girando (ha trabalho) + picareta pequena
    d.star(9.6, 10.4, 8.6, 6.4, 8, -90, "steel", g=0)
    d.disc(9.6, 10.4, 5.8, "steel", g=0)
    d.disc(9.6, 10.4, 2.4, "dark", g=1)
    d.arc_arrow(9.6, 10.4, 4.0, 200, 470, 1.2, 2.4, "p", g=3)
    I.pickaxe(d, 15.0, 4.6, 0.42, "gold", "wood", g=6)


@I.pic("ag_on_night")
def _(d):
    I.moon(d, 9.0, 10.4, 7.4, "bone", 3.6, -2.6, g=0)
    I.sparkle(d, 15.6, 4.0, 2.4, "W", g=3)
    d.put(17, 12, "yellow.l", g=4)
    d.put(14, 16, "yellow.l", g=4)


@I.pic("ag_on_day")
def _(d):
    d.rect(0, 15, 19, 19, "grass", g=0)
    d.rect(0, 15, 19, 15, "grass.l", g=0)
    I.sun(d, 10.0, 13.6, 4.4, 8, 3.0, "yellow", "flame", g=2)
    d.rect(0, 16, 19, 19, "grass", g=5)


@I.pic("ag_on_monsters")
def _(d):
    I.skull(d, 9.0, 10.8, 0.95, "green", g=0)
    d.rect(16, 2, 17, 9, "red", g=4)
    d.rect(16, 11, 17, 12, "red", g=4)


# glifo -> (tipo da moldura, elemento, pictograma, selos)
ICONS = {
    "quarry": ("effect", "arcane", "ag_quarry", []),
    "lumberjack": ("effect", "arcane", "ag_lumberjack", []),
    "arcane_fishing": ("effect", "arcane", "ag_arcane_fishing", []),
    "illuminate": ("effect", "arcane", "ag_illuminate", []),
    "on_ripe": ("trigger", "arcane", "ag_on_ripe", []),
    "on_empty": ("trigger", "arcane", "ag_on_empty", []),
    "on_items": ("trigger", "arcane", "ag_on_items", []),
    "on_work": ("trigger", "arcane", "ag_on_work", []),
    "on_night": ("trigger", "arcane", "ag_on_night", []),
    "on_day": ("trigger", "arcane", "ag_on_day", []),
    "on_monsters": ("trigger", "arcane", "ag_on_monsters", []),
}


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
    print("  icones dos glifos (auto_glyphs): %d" % len(items))
    if preview:
        I.sheet(items, 4, 6, os.path.join(preview, "auto_glyphs_icons.png"))
        print("  previa:", os.path.join(preview, "auto_glyphs_icons.png"))


if __name__ == "__main__":
    main()
