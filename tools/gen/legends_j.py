"""
Icones do modulo "legends_j" — efeitos e aumentos de anime (rode da raiz:  py -I tools/gen/legends_j.py)

So os icones 32x32 dos glifos deste modulo (textures/gui/glyph/<id>.png), com o MESMO desenho que o tools/gen/icons.py
faria (tipo, elemento e pictograma deduzidos do id/nome/descricao das traducoes). Nao mexe em mais nada.
A rocha do Tengai Shinsei usa blocos do vanilla (sem textura nova).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")

GLYPHS = ["boogie_woogie", "kazuma_steal", "flame_alchemy", "alchemy_wall", "zoltraak", "geass", "chain_jail",
          "holy_chain", "dowsing_chain", "tengai_shinsei", "black_flash", "starburst_stream", "excalibur_light"]


def glyph_icons():
    saved = sys.argv[:]
    sys.argv = [sys.argv[0]]
    try:
        import icons
    finally:
        sys.argv = saved
    specs, _, _ = icons.build_specs()
    out = os.path.join(A, "textures", "gui", "glyph")
    os.makedirs(out, exist_ok=True)
    n = 0
    for gid in GLYPHS:
        if gid in specs:
            icons.save_px(icons.make_glyph_icon(specs[gid]), os.path.join(out, gid + ".png"))
            n += 1
        else:
            print("  sem spec (traducao faltando?):", gid)
    print("  icones dos glifos: %d" % n)


if __name__ == "__main__":
    glyph_icons()
