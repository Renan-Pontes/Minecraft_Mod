"""
Icones 32x32 dos 3 EFEITOS de obra (modulo settlements, v1.0 onda 4 "city_magic"): Impulso de Obra, Moldar Lote, Erguer Obra.
Rode da raiz:  py -I tools/gen/city_magic_icons.py [--preview PASTA]

Usa as funcoes do tools/gen/icons.py (moldura pelo TIPO, gradiente na cor do ELEMENTO, pictograma com contorno e
sombreado automaticos), com pictogramas proprios. So grava os 3 ids deste trabalho em textures/gui/glyph/.
NAO roda o icons.py inteiro.
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
# Tela 20x20. "p" = cor clara do elemento (Arcano).

@I.pic("cm_hasten")
def _(d):
    # Impulso de Obra: martelo batendo, com faiscas de mana
    d.line(5.0, 16.0, 12.5, 8.5, 2.0, "wood", g=1)
    d.poly([(9.0, 6.0), (12.0, 3.0), (17.0, 8.0), (14.0, 11.0)], "steel", g=3)
    I.sparkle(d, 4.5, 5.5, 2.6, "p", g=5)
    I.sparkle(d, 16.0, 15.0, 2.0, "W", g=5)
    d.rect(2, 11, 3, 11, "p", g=5)


@I.pic("cm_shape")
def _(d):
    # Moldar Lote: o lote (grama sobre terra) com setas de subir e baixar
    d.rect(3, 11, 16, 16, "dirt", g=1)
    d.rect(3, 9, 16, 10, "green", g=3)
    d.tri((6.5, 1.5), (3.5, 5.5), (9.5, 5.5), "p", g=5)
    d.rect(6, 5, 7, 7, "p", g=5)
    d.tri((13.5, 7.5), (10.5, 3.5), (16.5, 3.5), "W", g=5)
    d.rect(13, 1, 14, 3, "W", g=5)


@I.pic("cm_erect")
def _(d):
    # Erguer Obra: a casa pronta surgindo de um circulo de mana
    d.ellipse(10.0, 16.8, 8.0, 1.8, "p", g=0)
    d.rect(5, 10, 14, 16, "stone", g=2)
    d.tri((10.0, 3.0), (3.0, 10.0), (17.0, 10.0), "wood", g=3)
    d.rect(9, 13, 10, 16, "wood", g=1)
    I.sparkle(d, 16.0, 4.0, 2.6, "W", g=5)
    I.sparkle(d, 3.5, 5.0, 1.8, "p", g=5)


# glifo -> (tipo da moldura, elemento, pictograma, selos)
ICONS = {
    "city_hasten": ("effect", "arcane", "cm_hasten", []),
    "city_shape": ("effect", "arcane", "cm_shape", []),
    "city_erect": ("effect", "arcane", "cm_erect", []),
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
    print("  icones dos glifos (city_magic): %d" % len(items))
    if preview:
        I.sheet(items, 4, 5, os.path.join(preview, "city_magic_icons.png"))
        print("  previa:", os.path.join(preview, "city_magic_icons.png"))


if __name__ == "__main__":
    main()
