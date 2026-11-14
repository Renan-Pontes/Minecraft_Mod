"""
Assets do modulo "legends_k" — tecnicas de ANIME nas CLASSES (glifos de origem). Rode da raiz:
    py -I tools/gen/legends_k.py

Icones dos glifos (textures/gui/glyph/<id>.png, 32x32): desenhados com as funcoes do tools/gen/icons.py, com a COR DA
CLASSE no lugar do elemento (como os outros glifos de origem), so para os ids deste modulo — NAO roda a rodada completa.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")

# id -> (tipo, classe (cor), pictograma da biblioteca do icons.py, selo no canto)
SPECS = {
    "lk_kamui": ("effect", "rogue", "portal", "b_ring"),
    "lk_d4c": ("effect", "steelball", "rift", "b_diamond"),
    "lk_spiritual_pressure": ("effect", "shinigami", "weight", "b_wave"),
    "lk_titan_mode": ("effect", "smith", "colossus", "b_up"),
    "lk_return_to_zero": ("effect", "steelball", "counterspell", "b_star"),
    "lk_santen_kesshun": ("effect", "enchanter", "divine_shield", "b_plus"),
    "lk_soten_kisshun": ("effect", "enchanter", "lay_on_hands", "b_ring"),
    "lk_koten_zanshun": ("effect", "enchanter", "slash", "b_spark"),
    "lk_projection": ("effect", "gunslinger", "blink", "b_diamond"),
    "lk_vector_reflect": ("effect", "mage", "reflect", "b_ring"),
    "lk_vector_punch": ("effect", "mage", "ki_palm", "b_up"),
    "lk_vector_burst": ("effect", "mage", "vacuum", "b_star"),
    "lk_shadow_toad": ("effect", "necromancer", "frogify", "b_dot"),
    "lk_mahoraga_wheel": ("effect", "warlock", "orbit", "b_plus"),
    "lk_dead_calm": ("effect", "knight", "parry", "b_wave"),
    "lk_return_by_death": ("effect", "witch", "return_rune", "b_star"),
    "lk_flower_field": ("effect", "druid", "bloom", "b_dot"),
    "lk_shadow_possession": ("effect", "ranger", "living_shadow", "b_ring"),
    "lk_aschente": ("effect", "gambler", "v10_dice", "b_star"),
}


def glyph_icons():
    saved = sys.argv[:]
    sys.argv = [sys.argv[0]]
    try:
        import icons
    finally:
        sys.argv = saved
    out = os.path.join(A, "textures", "gui", "glyph")
    os.makedirs(out, exist_ok=True)
    n = 0
    for gid, (kind, elem, pic, badge) in SPECS.items():
        spec = {"kind": kind, "elem": elem, "pic": pic, "badges": [badge], "legend": False}
        icons.save_px(icons.make_glyph_icon(spec), os.path.join(out, gid + ".png"))
        n += 1
    print("  icones dos glifos: %d" % n)


if __name__ == "__main__":
    glyph_icons()
