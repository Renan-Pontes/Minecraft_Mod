"""
Icones 32x32 dos glifos do modulo "glyphs_elements_b" (v1.4: 1 aumento + 1 gatilho por fusao).
Rode da raiz:  py -I tools/gen/glyphs_elements_b_icons.py

Usa as funcoes do tools/gen/icons.py (moldura pelo TIPO, gradiente na cor do ELEMENTO e um pictograma da
biblioteca), mas so grava os 30 ids deste modulo em textures/gui/glyph/. NAO roda o icons.py inteiro.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures", "gui", "glyph")

# glifo -> (tipo da moldura, elemento, pictograma do icons.py, selos)
ICONS = {
    # ---------------- aumentos
    "magma_aftershock": ("augment", "magma", "lava_geyser", []),
    "steam_jet": ("augment", "steam", "steam_propulsion", []),
    "lightning_fork": ("augment", "lightning", "arc_lightning", []),
    "sun_captive_star": ("augment", "sun", "solar_flare", []),
    "hellfire_hunger": ("augment", "hellfire", "black_flame", []),
    "nature_seed": ("augment", "nature", "sow", []),
    "ice_splinter": ("augment", "ice", "shards", []),
    "purity_ablution": ("augment", "purity", "holy_water", []),
    "abyss_anchor": ("augment", "abyss", "abyssal_pressure", ["b_down"]),
    "sand_erosion": ("augment", "sand", "sandstorm", []),
    "crystal_resonance": ("augment", "crystal", "crystal_lotus", ["b_ring"]),
    "gravity_collapse": ("augment", "gravity", "gravity_well", []),
    "astral_starstep": ("augment", "astral", "star_teleport", []),
    "void_annihilate": ("augment", "void", "annihilate", []),
    "twilight_tide": ("augment", "twilight", "duality", []),
    # ---------------- gatilhos
    "magma_on_land": ("trigger", "magma", "meteor", []),
    "steam_on_vapor": ("trigger", "steam", "hot_mist", []),
    "lightning_on_overload": ("trigger", "lightning", "overcharge", []),
    "sun_on_sunlight": ("trigger", "sun", "sunlight", []),
    "hellfire_on_burn": ("trigger", "hellfire", "soul_burn", []),
    "nature_on_root": ("trigger", "nature", "root", []),
    "ice_on_freeze": ("trigger", "ice", "freeze", []),
    "purity_on_debuff": ("trigger", "purity", "cleanse", []),
    "abyss_on_dive": ("trigger", "abyss", "abyssal_drown", []),
    "sand_on_blind": ("trigger", "sand", "blind", []),
    "crystal_on_petrify": ("trigger", "crystal", "crystallize", []),
    "gravity_on_slam": ("trigger", "gravity", "crush", []),
    "astral_on_teleport": ("trigger", "astral", "warp", []),
    "void_on_erase": ("trigger", "void", "erase", []),
    "twilight_on_threshold": ("trigger", "twilight", "eclipse", []),
}


def main():
    sys.path.insert(0, HERE)
    saved = sys.argv[:]
    sys.argv = sys.argv[:1]
    try:
        import icons as I  # noqa: E402
    finally:
        sys.argv = saved
    os.makedirs(OUT, exist_ok=True)
    n = 0
    for gid, (kind, elem, pic, badges) in ICONS.items():
        if pic not in I.PICS:
            print("ATENCAO: pictograma inexistente", pic, "para", gid)
            continue
        spec = {"kind": kind, "elem": elem, "pic": pic, "badges": badges}
        I.save_px(I.make_glyph_icon(spec), os.path.join(OUT, gid + ".png"))
        n += 1
    print("  icones dos glifos (glyphs_elements_b): %d" % n)


if __name__ == "__main__":
    main()
