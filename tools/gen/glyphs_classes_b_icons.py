"""
Icones dos 14 glifos novos do modulo glyphs_classes_b (v1.4: um aumento e um gatilho para Encantador, Shinigami,
Quincy, Pistoleiro, Patrulheiro, Apostador e Steel Ball).
Uso (na raiz do projeto):  py -I tools/gen/glyphs_classes_b_icons.py

Gera SO: src/main/resources/assets/isekai_mahou/textures/gui/glyph/<id>.png (32x32), no mesmo estilo do
tools/gen/icons.py (moldura do tipo + pictograma + selo no canto, na cor da classe). Nao roda o icons.py inteiro
(ele sobrescreveria os icones feitos a mao) — so importa as funcoes de desenho.
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/gui/glyph")

# glifo -> (tipo da moldura, cor da classe, pictograma do icons.py, selos [(nome, canto)])
ICONS = {
    # Encantador
    "enchanter_engraved_rune": ("augment", "enchanter", "explosive_seal", [("b_spark", "br")]),
    "enchanter_on_enchanted_strike": ("trigger", "enchanter", "temp_enchant", [("b_star", "br")]),
    # Shinigami
    "shinigami_shunpo_cast": ("augment", "shinigami", "shadow_step", [("b_spark", "br")]),
    "shinigami_on_released_cut": ("trigger", "shinigami", "v10_zanpakuto", [("b_star", "br")]),
    # Quincy
    "quincy_sklaverei": ("augment", "quincy", "soul_drain", [("b_star", "br")]),
    "quincy_on_reishi": ("trigger", "quincy", "v10_absorb", [("b_ring", "br")]),
    # Pistoleiro
    "gun_trick_ricochet": ("augment", "gunslinger", "ricochet", [("b_star", "br")]),
    "gun_on_headshot": ("trigger", "gunslinger", "mark_shot", [("b_x", "br")]),
    # Patrulheiro
    "ranger_coordinated_strike": ("augment", "ranger", "pack_tactics", [("b_plus", "br")]),
    "ranger_on_mark": ("trigger", "ranger", "hunters_mark", [("b_spark", "br")]),
    # Apostador
    "gambler_russian_roulette": ("augment", "gambler", "v10_roulette", [("b_x", "br")]),
    "gambler_on_big_win": ("trigger", "gambler", "v10_dice", [("b_star", "br")]),
    # Steel Ball
    "steelball_golden_spin": ("augment", "steelball", "spiral_sphere", [("b_star", "br")]),
    "steelball_on_ball_hit": ("trigger", "steelball", "v10_spin_ball", [("b_spark", "br")]),
}


def main():
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
    sys.argv = sys.argv[:1]
    import icons as I  # noqa: E402  (so as funcoes; o main() dele nao roda)
    os.makedirs(OUT, exist_ok=True)
    for gid, (kind, elem, pic, badges) in ICONS.items():
        if pic not in I.PICS:
            raise SystemExit("pictograma desconhecido: " + pic)
        spec = {"kind": kind, "elem": elem, "pic": pic, "badges": badges}
        I.save_px(I.make_glyph_icon(spec), os.path.join(OUT, gid + ".png"))
        print("ok", gid)


if __name__ == "__main__":
    main()
