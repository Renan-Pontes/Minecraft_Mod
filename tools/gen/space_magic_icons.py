"""
Icones 32x32 dos glifos do modulo "space_magic" (v1.6: Magia Espacial).
Rode da raiz:  py -I tools/gen/space_magic_icons.py [--preview PASTA]

Usa as funcoes do tools/gen/icons.py (moldura pelo TIPO, gradiente na cor do ELEMENTO, pictograma com contorno e
sombreado automaticos), com pictogramas proprios. So grava os ids deste modulo (5 da v1.6 + recover_belongings da v1.0) em textures/gui/glyph/.
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

@I.pic("sm_item_box")
def _(d):
    # redemoinho do espaco atras + bau saindo dele
    I.spiral(d, 10.0, 8.6, 1.0, 7.6, 1.5, 1.6, "violet", a0=200, g=0)
    d.rect(4, 10, 15, 17, "wood", g=3)
    d.rect(4, 9, 15, 11, "wood.l", g=4)
    d.rect(4, 12, 15, 12, "wood.d", g=5)
    d.rect(9, 11, 10, 13, "gold", g=6)
    I.sparkle(d, 16.6, 3.2, 2.2, "W", g=8)


@I.pic("sm_gate")
def _(d):
    # anel oval com o vazio dentro e um redemoinho
    d.ellipse(10.0, 10.0, 6.6, 9.0, "p", g=0)
    d.ellipse(10.0, 10.0, 4.6, 7.0, "void", g=1)
    I.spiral(d, 10.0, 10.0, 0.6, 3.8, 1.4, 1.0, "violet", g=2)
    d.put(10, 10, "W", g=3)
    I.sparkle(d, 16.8, 3.0, 2.0, "W", g=4)
    I.sparkle(d, 3.2, 16.6, 1.6, "cyan", g=4)


@I.pic("sm_step")
def _(d):
    # parede no meio, seta que atravessa e um borrao de teleporte dos dois lados
    d.rect(8, 3, 11, 17, "stone", g=0)
    d.rect(8, 7, 11, 7, "stone.d", g=1)
    d.rect(8, 12, 11, 12, "stone.d", g=1)
    d.arrow(1.5, 10.0, 18.6, 10.0, 2.0, 4.0, "p", g=3)
    d.disc(3.0, 10.0, 2.4, "violet", g=2)
    I.sparkle(d, 16.0, 4.4, 2.0, "W", g=5)


@I.pic("sm_return")
def _(d):
    # marca rombica no chao + seta curva voltando para ela
    d.ellipse(10.0, 16.0, 7.0, 2.4, "violet", g=0)
    d.poly([(10.0, 8.6), (13.0, 13.0), (10.0, 17.4), (7.0, 13.0)], "p", g=2)
    d.disc(10.0, 13.0, 1.0, "W", g=3)
    d.arc_arrow(10.0, 9.0, 6.6, 20, -200, 1.6, 3.4, "cyan", g=5)


@I.pic("sm_gravity")
def _(d):
    # nucleo escuro com quatro setas para dentro
    d.disc(10.0, 10.0, 4.0, "void", g=0)
    d.ring(10.0, 10.0, 5.2, 4.2, "p", g=1)
    d.arrow(10.0, 0.6, 10.0, 4.4, 1.6, 2.6, "cyan", g=3)
    d.arrow(10.0, 19.4, 10.0, 15.6, 1.6, 2.6, "cyan", g=3)
    d.arrow(0.6, 10.0, 4.4, 10.0, 1.6, 2.6, "cyan", g=3)
    d.arrow(19.4, 10.0, 15.6, 10.0, 1.6, 2.6, "cyan", g=3)
    d.put(9, 9, "W", g=4)


@I.pic("sm_recover")
def _(d):
    # redemoinho do espaco no alto (o lugar da morte) e o bau dos pertences voltando para voce
    I.spiral(d, 5.6, 5.6, 0.8, 4.8, 1.4, 1.4, "violet", a0=200, g=0)
    d.rect(8, 12, 17, 17, "wood", g=3)
    d.rect(8, 11, 17, 13, "wood.l", g=4)
    d.rect(8, 14, 17, 14, "wood.d", g=5)
    d.rect(12, 13, 13, 15, "gold", g=6)
    d.arrow(5.0, 5.0, 11.4, 9.6, 1.6, 3.2, "cyan", g=7)
    I.sparkle(d, 16.4, 4.0, 2.0, "W", g=8)


# glifo -> (tipo da moldura, elemento, pictograma, selos)
ICONS = {
    "dimensional_inventory": ("effect", "arcane", "sm_item_box", []),
    "dimension_gate": ("effect", "arcane", "sm_gate", []),
    "space_step": ("effect", "arcane", "sm_step", []),
    "return_mark": ("effect", "arcane", "sm_return", []),
    "space_gravity": ("effect", "arcane", "sm_gravity", []),
    "recover_belongings": ("effect", "arcane", "sm_recover", []),
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
    print("  icones dos glifos (space_magic): %d" % len(items))
    if preview:
        I.sheet(items, 4, 5, os.path.join(preview, "space_magic_icons.png"))
        print("  previa:", os.path.join(preview, "space_magic_icons.png"))


if __name__ == "__main__":
    main()
