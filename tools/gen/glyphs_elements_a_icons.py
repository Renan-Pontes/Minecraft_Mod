"""
Icones dos glifos do modulo "glyphs_elements_a" (v1.4): 7 aumentos + 7 gatilhos dos elementos base e o efeito
Aplainar Terreno. Rode da raiz do projeto:  py -I tools/gen/glyphs_elements_a_icons.py

Usa a MESMA arte do tools/gen/icons.py (moldura pelo tipo, gradiente na cor do elemento, pictograma), mas com o
pictograma e o selo escolhidos a mao (o chute por palavra-chave errava: "Mare" virava onda gigante, "Emboscada"
virava punhal de classe...). So grava os 15 arquivos deste modulo em textures/gui/glyph/ — NAO roda o icons.py inteiro.
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures", "gui", "glyph")

# id: (tipo, elemento, pictograma, selos)
ICONS = {
    # Aumentos (placa quadrada)
    "fire_afterburn": ("augment", "fire", "explosion", ["b_dot"]),
    "water_undertow": ("augment", "water", "current", [("b_down", "bl")]),
    "earth_aftershock": ("augment", "earth", "tremor", ["b_wave"]),
    "wind_ricochet": ("augment", "wind", "ricochet", ["b_wave"]),
    "light_refraction": ("augment", "light", "prism", ["b_plus"]),
    "shadow_ambush": ("augment", "shadow", "shadow_dagger", ["b_x"]),
    "arcane_mimic": ("augment", "arcane", "spell_steal", ["b_ring"]),
    # Gatilhos (triangulo)
    "trigger_on_ignite": ("trigger", "fire", "ignite", []),
    "trigger_on_wet": ("trigger", "water", "soak", []),
    "trigger_on_landing": ("trigger", "earth", "earth_shatter", []),
    "trigger_on_momentum": ("trigger", "wind", "swiftness", []),
    "trigger_on_ally_heal": ("trigger", "light", "heal", []),
    "trigger_in_shadows": ("trigger", "shadow", "darkness", []),
    "trigger_resonance": ("trigger", "arcane", "pulse", []),
    # Efeito utilitario (gema)
    "earth_flatten": ("effect", "earth", "till", [("b_minus", "bl")]),
}


def main():
    sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    saved = sys.argv[:]
    sys.argv = [sys.argv[0]]
    try:
        import icons  # noqa: E402  (so a arte; nao chama o main dele)
    finally:
        sys.argv = saved
    os.makedirs(OUT, exist_ok=True)
    for gid, (kind, elem, pic, badges) in ICONS.items():
        if pic not in icons.PICS:
            raise RuntimeError("pictograma desconhecido: %s (%s)" % (pic, gid))
        spec = {"kind": kind, "elem": elem, "pic": pic, "badges": badges, "args": {}, "legend": False}
        icons.save_px(icons.make_glyph_icon(spec), os.path.join(OUT, gid + ".png"))
    print("  icones dos glifos (glyphs_elements_a): %d" % len(ICONS))


if __name__ == "__main__":
    main()
