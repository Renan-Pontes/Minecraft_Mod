# -*- coding: utf-8 -*-
"""
Icones do modulo transform_laws (v1.4 — Leis de Transformacao).

Gera SO os arquivos deste modulo, reaproveitando a moldura/sombreado do tools/gen/icons.py (sem rodar o main dele,
que sobrescreveria icones feitos a mao):
  textures/gui/glyph/tlaw_*.png      32x32  (moldura de Lei + pictograma proprio + selo de corrente no canto)
  textures/gui/tab/transform_laws.png 16x16 (aba Votos)
  textures/mob_effect/transform_vow.png 18x18 (efeito "Votos Selados")

Uso: py tools/gen/transform_laws_icons.py
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
    from icons import pic, badge, Pic  # noqa: E402,F401
finally:
    sys.argv = _saved

ROOT = os.path.dirname(os.path.dirname(HERE))
TEX = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures")


# ------------------------------------------------------------------ selo comum: elo de corrente (voto)
@badge("b_tlaw_chain")
def _(p):
    icons.chain_link(p, 4.2, 5.5, 3.6, 2.4, 1.3, 35, "steel")
    icons.chain_link(p, 7.0, 5.5, 3.6, 2.4, 1.3, 35, "silver", g=1)


# ------------------------------------------------------------------ pictogramas 20x20
@pic("tlaw_glass_body")
def _(d):
    icons.vfig(d, 10, 18.8, 17.0, "glass", arms="down")
    d.line(10.4, 4.0, 8.8, 8.0, 0.9, "W", g=3)
    d.line(8.8, 8.0, 11.2, 11.0, 0.9, "W", g=3)
    d.line(11.2, 11.0, 9.6, 14.6, 0.9, "W", g=3)


@pic("tlaw_predator_eyes")
def _(d):
    icons.eye(d, 10, 10, 1.0, "yellow", "orange", "k")
    d.rect(9, 6, 10, 13, "k", g=5)
    d.line(2.0, 4.0, 8.0, 6.2, 1.6, "red", g=6)
    d.line(18.0, 4.0, 12.0, 6.2, 1.6, "red", g=6)


@pic("tlaw_blind_fury")
def _(d):
    icons.burst(d, 10, 10.6, 9.2, 5.0, 10, "red")
    d.disc(10, 10.6, 4.6, "orange", g=1)
    d.line(5.6, 8.0, 9.0, 10.0, 1.6, "k", g=2)
    d.line(14.4, 8.0, 11.0, 10.0, 1.6, "k", g=2)
    d.rect(7, 13, 12, 13, "k", g=2)


@pic("tlaw_bloodlust")
def _(d):
    icons.drop(d, 10, 11.4, 6.4, "blood", 1.7)
    d.poly([(6.0, 8.6), (9.0, 8.6), (7.5, 14.6)], "bone", g=4)
    d.poly([(11.0, 8.6), (14.0, 8.6), (12.5, 14.6)], "bone", g=5)


@pic("tlaw_unstable_form")
def _(d):
    icons.crystal(d, 10, 10, 8.6, 4.0, "crystal")
    d.line(8.6, 3.6, 11.4, 9.0, 1.0, "W", g=2)
    d.line(11.4, 9.0, 8.8, 13.6, 1.0, "W", g=2)
    d.disc(3.0, 4.0, 2.0, "fire", g=3)
    d.disc(17.0, 4.0, 2.0, "water", g=4)
    d.disc(3.0, 16.0, 2.0, "leaf", g=5)
    d.disc(17.0, 16.0, 2.0, "yellow", g=6)


@pic("tlaw_iron_root")
def _(d):
    icons.cube(d, 10, 8.6, 1.0, "iron")
    for i, x in enumerate((5.0, 10.0, 15.0)):
        d.line(x, 14.0, x + (i - 1) * 2.4, 19.0, 1.8, "wood", g=4 + i)


@pic("tlaw_silence_vow")
def _(d):
    icons.sparkle(d, 10, 9, 6.6, "mana")
    icons.chain_link(d, 4.6, 10.0, 3.6, 2.6, 1.4, 0, "steel", g=3)
    icons.chain_link(d, 10.0, 10.0, 3.6, 2.6, 1.4, 0, "silver", g=4)
    icons.chain_link(d, 15.4, 10.0, 3.6, 2.6, 1.4, 0, "steel", g=5)


@pic("tlaw_last_breath")
def _(d):
    icons.hourglass(d, 9, 10, 0.9, "wood", "glass", "sand", 0.15, 0.85)
    icons.cloud(d, 15.4, 4.6, 0.55, "cloud", g=6)


@pic("tlaw_relentless_rhythm")
def _(d):
    for i, x in enumerate((5.0, 10.0, 15.0)):
        icons.chevron(d, x, 10.0, 4.0, 2.4, 1, 0, "orange" if i < 2 else "red", g=i)


@pic("tlaw_mana_pact")
def _(d):
    icons.orb(d, 10, 10, 8.2, "mana")
    icons.drop(d, 10, 11.0, 4.0, "blood", 1.6, shine=False, g=3)


@pic("tlaw_guardian_oath")
def _(d):
    icons.shield(d, 10, 10, 1.0, "gold", "steel")
    icons.plus(d, 10, 9.6, 3.6, 2.2, "W", g=5)


@pic("tlaw_brief_flame")
def _(d):
    icons.flame(d, 10, 19.0, 1.0, "red", "fire", "flame")
    icons.sparkle(d, 15.6, 4.0, 2.6, "W", g=7)


@pic("tlaw_transcend")
def _(d):
    icons.sun(d, 10, 9.6, 6.0, 12, 3.2, "yellow", "white")
    icons.vfig(d, 10, 19.4, 12.0, "purple", arms="up", g=6)


LAWS = [
    ("tlaw_glass_body", False), ("tlaw_predator_eyes", False), ("tlaw_blind_fury", False), ("tlaw_bloodlust", False),
    ("tlaw_unstable_form", False), ("tlaw_iron_root", False), ("tlaw_silence_vow", False), ("tlaw_last_breath", False),
    ("tlaw_relentless_rhythm", False), ("tlaw_mana_pact", False), ("tlaw_guardian_oath", False),
    ("tlaw_brief_flame", False), ("tlaw_transcend", True),
]


def vow16(p):
    """Simbolo dos votos 16x16: dois elos cruzados com uma faisca."""
    icons.chain_link(p, 5.6, 8.0, 4.4, 3.0, 1.5, 35, "silver")
    icons.chain_link(p, 10.4, 8.0, 4.4, 3.0, 1.5, 35, "steel", g=1)
    icons.sparkle(p, 12.6, 3.4, 2.6, "W", g=2)


def main():
    gdir = os.path.join(TEX, "gui", "glyph")
    os.makedirs(gdir, exist_ok=True)
    for gid, legend in LAWS:
        spec = {"kind": "law", "elem": "arcane", "pic": gid, "legend": legend, "badges": [("b_tlaw_chain", "bl")]}
        icons.save_px(icons.make_glyph_icon(spec), os.path.join(gdir, gid + ".png"))
    # aba 16x16
    tab = icons.render16(vow16, "arcane")
    tdir = os.path.join(TEX, "gui", "tab")
    os.makedirs(tdir, exist_ok=True)
    icons.save_px(tab, os.path.join(tdir, "transform_laws.png"))
    # efeito 18x18 (o mesmo simbolo com 1 px de margem)
    eff = [[(0, 0, 0, 0)] * 18 for _ in range(18)]
    for y in range(16):
        for x in range(16):
            eff[y + 1][x + 1] = tab[y][x]
    edir = os.path.join(TEX, "mob_effect")
    os.makedirs(edir, exist_ok=True)
    icons.save_px(eff, os.path.join(edir, "transform_vow.png"))
    print("transform_laws: %d icones de lei + aba + efeito" % len(LAWS))


if __name__ == "__main__":
    main()
