"""
Assets do modulo "legends_i" — glifos de ANIME comuns do Grimorio (rode da raiz:  py -I tools/gen/legends_i.py)

Icones dos glifos (textures/gui/glyph/<id>.png, 32x32): desenhados com as funcoes do tools/gen/icons.py (tipo, elemento
e pictograma deduzidos do id/nome/descricao do lang), so para os ids deste modulo — NAO roda a rodada completa.
Icones de efeito (textures/mob_effect/, 18x18): li_susanoo li_amaterasu li_paper_wings li_observation.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
from pixelart import Canvas, hex_rgba, lighten, darken  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
EFF = os.path.join(A, "textures", "mob_effect")

# id -> (tipo, elemento, pictograma da biblioteca do icons.py, selo no canto) — escolhidos a mao (o "chute" pelo nome
# erra muito com nomes japoneses)
SPECS = {
    "haki_armament": ("augment", "arcane", "shadow_weapon", "b_spark"),
    "haki_observation": ("effect", "arcane", "evil_eye", "b_ring"),
    "haki_conqueror": ("effect", "arcane", "fear", "b_star"),
    "gomu_pistol": ("effect", "arcane", "flurry_of_blows", "b_wave"),
    "shinra_tensei": ("effect", "gravity", "push", "b_ring"),
    "bansho_tenin": ("effect", "gravity", "pull", "b_ring"),
    "chibaku_tensei": ("effect", "gravity", "debris_orbit", "b_dot"),
    "paper_shuriken": ("effect", "wind", "blade_storm", "b_diamond"),
    "paper_wings": ("augment", "wind", "glide", "b_diamond"),
    "amaterasu": ("effect", "hellfire", "black_flame", "b_dot"),
    "susanoo": ("effect", "shadow", "battle_avatar", "b_star"),
    "chidori": ("effect", "lightning", "charge_rush", "b_spark"),
    "kage_bunshin": ("effect", "shadow", "illusion", "b_plus"),
}
GLYPHS = list(SPECS)


# =============================================================================================  icones de efeito
def icon(name, draw):
    c = Canvas(18, 18)
    draw(c)
    c.outline((20, 14, 26, 255))
    c.save(os.path.join(EFF, name + ".png"))


def eff_susanoo(c):
    purple, light = hex_rgba("9A4DFF"), hex_rgba("E0C8FF")
    c.line(9, 2, 9, 15, purple)                       # coluna
    for i, y in enumerate((5, 8, 11)):               # costelas
        w = 6 - i
        c.line(9, y, 9 - w, y + 2, light)
        c.line(9, y, 9 + w, y + 2, light)
    c.circle(9, 2.5, 1.6, purple)


def eff_amaterasu(c):
    black, rim = hex_rgba("140818"), hex_rgba("5A1E7A")
    for x in range(4, 15):
        h = int(10 * (1 - ((x - 9) / 5.5) ** 2))
        c.line(x, 16, x, 16 - h, black)
    c.line(9, 16, 9, 3, black)
    c.line(6, 15, 4, 8, rim)
    c.line(12, 15, 14, 8, rim)


def eff_paper_wings(c):
    paper, edge = hex_rgba("F6F2E6"), hex_rgba("B8B0A0")
    for k in range(4):
        c.line(9, 10, 2 + k, 3 + k * 2, paper)
        c.line(9, 10, 16 - k, 3 + k * 2, paper)
    c.line(9, 9, 9, 15, edge)


def eff_observation(c):
    red, dark = hex_rgba("E04848"), hex_rgba("3A0A10")
    for x in range(2, 17):
        h = 4.0 * (1 - ((x - 9) / 7) ** 2)
        c.set(x, int(9 - h), red)
        c.set(x, int(9 + h), red)
    c.circle(9, 9, 2.6, red)
    c.circle(9, 9, 1.1, dark)


def glyph_icons():
    saved = sys.argv[:]
    sys.argv = [sys.argv[0]]
    try:
        import icons
    finally:
        sys.argv = saved
    out = os.path.join(A, "textures", "gui", "glyph")
    n = 0
    for gid, (kind, elem, pic, badge) in SPECS.items():
        spec = {"kind": kind, "elem": elem, "pic": pic, "badges": [badge], "legend": False}
        icons.save_px(icons.make_glyph_icon(spec), os.path.join(out, gid + ".png"))
        n += 1
    print("  icones dos glifos: %d" % n)


def main():
    os.makedirs(EFF, exist_ok=True)
    icon("li_susanoo", eff_susanoo)
    icon("li_amaterasu", eff_amaterasu)
    icon("li_paper_wings", eff_paper_wings)
    icon("li_observation", eff_observation)
    print("  icones de efeito: 4")
    if "--no-icons" not in sys.argv:
        glyph_icons()


if __name__ == "__main__":
    main()
