"""
v1.4 — icones da classe Hollow (mesmo estilo pixel-art do tools/gen/icons.py, sem rodar o gerador inteiro):
  - textures/gui/glyph/hollowfied_cero_fusionado.png  (aumento novo "Cero Fusionado")
  - textures/gui/glyph/hollowfied_on_devour.png       (gatilho novo "Ao Devorar uma Alma")
  - textures/gui/glyph/hollowfied_*.png                (os 10 glifos de forma antigos, que nunca tiveram icone)
  - textures/gui/hollow_tech/<tecnica>.png             (HUD, Roda Hollow e aba: as 12 tecnicas inatas)
Uso (na raiz do projeto):  py -I tools/gen/class_hollow_icons.py
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")

# id -> (tipo da moldura, elemento da cor, pictograma, insignias)
GLYPHS = {
    "hollowfied_cero_fusionado": ("augment", "hellfire", "beam", ["b_plus"]),
    "hollowfied_on_devour": ("trigger", "shadow", "devour", []),
    "hollowfied_cero": ("form", "hellfire", "beam", []),
    "hollowfied_bala": ("form", "fire", "projectile", ["b_dot"]),
    "hollowfied_pesquisa": ("form", "shadow", "sonar", []),
    "hollowfied_regen": ("form", "shadow", "regenerate", []),
    "hollowfied_hierro": ("form", "earth", "stoneskin", []),
    "hollowfied_sonido": ("form", "wind", "shadow_step", []),
    "hollowfied_garganta": ("form", "abyss", "rift", []),
    "hollowfied_gran_rey_cero": ("form", "hellfire", "beam", ["b_star"]),
    "hollowfied_descorrer": ("form", "void", "void_rift", []),
    "hollowfied_resurreccion": ("form", "twilight", "primal_roar", []),
}

TECHS = {
    "cero": ("form", "hellfire", "beam", []),
    "bala": ("form", "fire", "projectile", ["b_dot"]),
    "gran_rey_cero": ("form", "hellfire", "beam", ["b_star"]),
    "cero_oscuras": ("form", "void", "eclipse", ["b_star"]),
    "sonido": ("form", "wind", "shadow_step", []),
    "pesquisa": ("form", "shadow", "sonar", []),
    "regen": ("form", "shadow", "regenerate", []),
    "hierro": ("form", "earth", "stoneskin", []),
    "garganta": ("form", "abyss", "rift", []),
    "negacion": ("form", "light", "holy_ray", []),
    "descorrer": ("form", "void", "void_rift", []),
    "resurreccion": ("form", "twilight", "primal_roar", ["b_star"]),
}


def main():
    sys.argv = sys.argv[:1]
    import icons as I  # noqa: E402

    def make(kind, elem, pic, badges, path):
        spec = {"kind": kind, "elem": elem, "pic": pic, "badges": badges, "legend": False}
        I.save_px(I.make_glyph_icon(spec), path)

    os.makedirs(os.path.join(A, "textures/gui/glyph"), exist_ok=True)
    os.makedirs(os.path.join(A, "textures/gui/hollow_tech"), exist_ok=True)
    for gid, (kind, elem, pic, badges) in GLYPHS.items():
        make(kind, elem, pic, badges, os.path.join(A, "textures/gui/glyph", gid + ".png"))
    for tid, (kind, elem, pic, badges) in TECHS.items():
        make(kind, elem, pic, badges, os.path.join(A, "textures/gui/hollow_tech", tid + ".png"))
    print("ok:", len(GLYPHS), "glifos,", len(TECHS), "tecnicas")


if __name__ == "__main__":
    main()
