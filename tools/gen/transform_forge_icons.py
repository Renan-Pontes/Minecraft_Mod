# -*- coding: utf-8 -*-
"""
Icones da Transformacao montada (release 1.0, onda 4 — modulo transform_laws).

Gera SO dois arquivos, reaproveitando a moldura/sombreado do tools/gen/icons.py (sem rodar o main dele):
  textures/gui/glyph/transformation.png   32x32  (moldura de Forma + figura de bracos abertos dentro de uma explosao de aura)
  textures/mob_effect/forged_form.png     18x18  (efeito "Transformacao": o mesmo simbolo)

Uso: py tools/gen/transform_forge_icons.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))

_saved = sys.argv[:]
sys.argv = [sys.argv[0]]
try:
    import icons  # noqa: E402
    from icons import pic  # noqa: E402
finally:
    sys.argv = _saved

ROOT = os.path.dirname(os.path.dirname(HERE))
TEX = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures")


@pic("transformation")
def _(d):
    icons.burst(d, 10, 10.4, 9.4, 5.2, 10, "purple")
    icons.vfig(d, 10, 19.2, 14.0, "white", arms="up", g=4)
    icons.sparkle(d, 15.8, 3.8, 2.6, "W", g=7)


def forged16(p):
    """Simbolo 16x16: figura de bracos abertos dentro da aura."""
    icons.burst(p, 8, 8.4, 7.4, 4.2, 8, "purple")
    icons.vfig(p, 8, 15.4, 10.5, "white", arms="up", g=3)


def main():
    gdir = os.path.join(TEX, "gui", "glyph")
    os.makedirs(gdir, exist_ok=True)
    spec = {"kind": "form", "elem": "arcane", "pic": "transformation", "legend": False, "badges": []}
    icons.save_px(icons.make_glyph_icon(spec), os.path.join(gdir, "transformation.png"))
    sym = icons.render16(forged16, "arcane")
    eff = [[(0, 0, 0, 0)] * 18 for _ in range(18)]
    for y in range(16):
        for x in range(16):
            eff[y + 1][x + 1] = sym[y][x]
    edir = os.path.join(TEX, "mob_effect")
    os.makedirs(edir, exist_ok=True)
    icons.save_px(eff, os.path.join(edir, "forged_form.png"))
    print("transform_forge: icone do glifo + efeito")


if __name__ == "__main__":
    main()
