"""
Icones 32x32 dos glifos de ORDEM da Forja de Invocacoes (modulo forms_b, v1.0 onda 4).
Rode da raiz:  py -I tools/gen/summon_forge_icons.py [--preview PASTA]

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

@I.pic("sf_guard")
def _(d):
    # Guardar: escudo fincado sobre um marco no chao
    d.ellipse(10.0, 16.6, 7.0, 1.8, "violet", g=0)
    I.shield(d, 10.0, 8.6, 0.95, "steel", g=2)
    d.rect(9, 5, 10, 11, "p", g=5)
    d.rect(7, 7, 12, 8, "p", g=5)


@I.pic("sf_hunt")
def _(d):
    # Cacar: mira sobre o alvo marcado
    d.ring(10.0, 10.0, 7.2, 5.4, "p", g=0)
    d.rect(9, 1, 10, 5, "W", g=3)
    d.rect(9, 14, 10, 18, "W", g=3)
    d.rect(1, 9, 5, 10, "W", g=3)
    d.rect(14, 9, 18, 10, "W", g=3)
    d.ellipse(10.0, 10.0, 2.0, 2.0, "red", g=5)


@I.pic("sf_hold")
def _(d):
    # Ficar: ancora (a criatura nao sai do lugar)
    d.ring(10.0, 4.2, 2.4, 1.0, "steel", g=2)
    d.rect(9, 6, 10, 16, "steel", g=2)
    d.rect(6, 8, 13, 9, "steel", g=3)
    d.line(4.0, 12.0, 10.0, 17.0, 2.0, "steel", g=2)
    d.line(16.0, 12.0, 10.0, 17.0, 2.0, "steel", g=2)
    d.tri((2.6, 10.4), (5.6, 11.4), (3.4, 13.8), "p", g=4)
    d.tri((17.4, 10.4), (14.4, 11.4), (16.6, 13.8), "p", g=4)


# glifo -> (tipo da moldura, elemento, pictograma, selos)
ICONS = {
    "summon_guard": ("augment", "arcane", "sf_guard", []),
    "summon_hunt": ("augment", "arcane", "sf_hunt", []),
    "summon_hold": ("augment", "arcane", "sf_hold", []),
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
    print("  icones dos glifos (summon_forge): %d" % len(items))
    if preview:
        I.sheet(items, 4, 5, os.path.join(preview, "summon_forge_icons.png"))
        print("  previa:", os.path.join(preview, "summon_forge_icons.png"))


if __name__ == "__main__":
    main()
