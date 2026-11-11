"""
Gera os assets e dados do modulo class_enchanter (classe ENCANTADOR, v1.0).
Uso (na raiz do projeto):  py -I tools/gen/class_enchanter.py [--preview <pasta>]

Saidas:
  assets/isekai_mahou/geo/item/armor/enchanter_<grau>.geo.json     traje GeckoLib (4 graus), com as abas do casaco,
  assets/isekai_mahou/textures/item/armor/enchanter_<grau>[_glowmask].png   capa e aureola de runas
  assets/isekai_mahou/textures/models/armor/enchanter_<grau>_layer_1|2.png   reserva vanilla
  assets/isekai_mahou/textures/item/enchanter_<grau>_<peca>.png + models/item/*.json   icones das pecas
  assets/isekai_mahou/models/item/class_enchanter/<manopla>_held.json + textures/item/class_enchanter/<manopla>.png
  assets/isekai_mahou/models/item/<manopla>.json + textures/item/<manopla>.png   manoplas (kit + tiers I-IV)
  assets/isekai_mahou/textures/item/enchanter_codex.png + models/item/enchanter_codex.json
  assets/isekai_mahou/textures/mob_effect/<encantamento>.png        icones 18x18 dos 9 encantamentos
  assets/isekai_mahou/textures/gui/tab/enchanter.png                icone da aba
  assets/isekai_mahou/geo/entity/enchanter_sanctum.geo.json + animations/entity/enchanter_sanctum.animation.json
  assets/isekai_mahou/textures/entity/enchanter_sanctum[_glowmask].png   Santuario do Soberano
  data/isekai_mahou/recipe/class_enchanter/*.json                   receitas (Codex, manoplas, melhorias, traje I)
  src/main/merge/class_enchanter/...                                 tags (espadas, armas, armaduras)
  src/main/lang/class_enchanter/{pt_br,en_us}.json                   traducoes
Reaproveita as ferramentas de tools/gen/outfits.py (atlas/glowmask do traje) e tools/gen/armory.py (modelo 3D das armas).
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, lighten, darken, mix, hex_rgba  # noqa: E402
import outfits as O  # noqa: E402
import armory as AR  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/class_enchanter")
LANG = os.path.join(ROOT, "src/main/lang/class_enchanter")
NS = "isekai_mahou"
PIECES = ("helmet", "chestplate", "leggings", "boots")
TIERS = ("novice", "adept", "master", "sovereign")
PREVIEW = None


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def H(h, a=255):
    return hex_rgba(h, a)


# =====================================================================================
#  TRAJE (GeckoLib) — casaco longo aberto com runas, gola alta, braceletes runicos, diadema e aureola de runas
# =====================================================================================
pal = O.pal
TEAL = pal("0E3A36", "165A52", "1F7E72", "2FA898", "5FE3C0")
TEAL_D = pal("0A2A2A", "10403E", "18605A", "248A80", "48C8B4")
CREAM = pal("6E6A5C", "9A9480", "C6BFA6", "E2DCC4", "F6F2E2")
IVORY = pal("8A8E94", "B4B8BE", "D8DCE0", "EEF0F2", "FFFFFF")
MIDNIGHT = pal("0A1430", "122452", "1C3678", "2A4C9E", "4A72C8")
SNOW = pal("A8B4C0", "C8D4E0", "E4ECF4", "F4F8FC", "FFFFFF")
WHITE_A = pal("8A96A0", "B4C0CA", "D8E2EA", "EEF4F8", "FFFFFF")
GEM_T = pal("0A4A44", "10847A", "3AD8C8", "B8FFF4")
GEM_C = pal("0A4A5A", "1A8AA0", "9AF4FF", "E0FCFF")
GEM_G = pal("5A3A04", "B07810", "FFC24A", "FFF0B8")

ENCH_T = {
    "novice": O.theme(TEAL, CREAM, O.BRASS, O.LEATHER_STD, WHITE_A, O.BRASS, GEM_T, "7AF0E0"),
    "adept": O.theme(IVORY, TEAL, O.SILVER_M, O.LEATHER_DARK, WHITE_A, O.SILVER_M, GEM_T, "8AF0FF"),
    "master": O.theme(MIDNIGHT, TEAL, O.GOLD_R, O.LEATHER_DARK, CREAM, O.GOLD_R, GEM_G, "FFD27A"),
    "sovereign": O.theme(SNOW, TEAL_D, O.GOLD_B, O.LEATHER_DARK, IVORY, O.GOLD_B, GEM_C, "9AF4FF"),
}
LV = {"novice": 0, "adept": 1, "master": 2, "sovereign": 3}


def ench_mats(T, lv):
    M = {}
    P = T["pal"]
    MAIN, SEC, TRIM, LEATH, ACC = O.v9_common(M, P, T, lv)
    GL, GEM, MT = T["GLOW"], T["GEM"], T["METAL"]
    put = O.put

    def coat(r, kind):
        O.cloth(r, MAIN, period=4, base=2, top=0.08, bottom=0.14)
        cx = r.w // 2
        if kind == "front":
            # casaco aberto: a camisa clara aparece no meio, fecho de botoes de metal
            for j in range(r.h):
                r.set(cx - 1, j, ACC[2])
                r.set(cx, j, ACC[3])
                r.set(cx - 2, j, SEC[2])
                r.set(cx + 1, j, SEC[2])
            for j in range(2, r.h - 1, 3):
                r.set(cx - 2, j, TRIM[3])
                r.set(cx + 1, j, TRIM[3])
            if r.w >= 7 and r.h >= 8:
                # selo de encantamento no peito (brilha a partir do grau II)
                O.sigil(r, cx - 3 if cx - 3 >= 2 else 2, 4, GL if lv >= 1 else SEC[3], glow=lv >= 1)
        elif kind == "back" and r.w >= 6 and r.h >= 8:
            O.sigil(r, cx, r.h // 2 - 1, GL if lv >= 2 else SEC[2], glow=lv >= 2)
            for k, i in enumerate(range(1, r.w - 3, 4)):
                O.rune_glyph(r, i, r.h - 5, GL if lv >= 1 else SEC[1], k + 2, glow=lv >= 1)
        r.col(0, SEC[1])
        r.col(-1, SEC[1])

    M["coat_front"] = lambda r: coat(r, "front")
    M["coat_back"] = lambda r: coat(r, "back")
    M["coat_side"] = lambda r: coat(r, "side")
    M["coat_top"] = lambda r: (O.cloth(r, MAIN, period=4, base=3, folds=False), r.row(0, SEC[2]))
    M["collar"] = O.trimmed(SEC, TRIM, base=2, glow_studs=GL if lv >= 1 else None, studs_c=TRIM[3])

    def coat_skirt(r):
        O.cloth(r, MAIN, period=3, base=2, top=0.02, bottom=0.14)
        O.trim_rows(r, TRIM, 2 if lv >= 1 else 1)
        if lv >= 2 and r.h >= 8 and r.w >= 5:
            for k, i in enumerate(range(1, r.w - 2, 4)):
                O.rune_glyph(r, i, r.h - 7, GL, k, glow=True)
        r.col(0, SEC[2])
        r.col(-1, SEC[2])

    M["coat_skirt"] = coat_skirt
    M["coat_skirt_in"] = lambda r: O.cloth(r, SEC, period=4, base=1, folds=False)
    M["coat_skirt_edge"] = lambda r: (r.fill(MAIN[1]), r.row(-1, TRIM[2]))

    def cape_out(r):
        O.cloth(r, SEC, period=2, base=2, top=0.04, bottom=0.12)
        O.trim_rows(r, TRIM, 2)
        if r.w >= 6 and r.h >= 10:
            O.sigil(r, r.w // 2, r.h // 3, GL, glow=True)
        if lv >= 3:
            for j in range(r.h // 2, r.h - 3, 4):
                r.glow(r.w // 2, j, GL)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: O.cloth(r, MAIN, period=3, base=1, folds=False)
    M["cape_edge"] = lambda r: (r.fill(SEC[1]), r.noise(0.03))

    def sleeve(r):
        O.cloth(r, MAIN, period=3, base=2)
        r.row(-1, TRIM[2])
        if r.h > 2:
            r.row(-2, SEC[2])

    M["sleeve"] = sleeve

    def bracer(r):
        O.metal(r, MT, rivets=False)
        cx, cy = r.w // 2, r.h // 2
        for (i, j) in ((cx, cy - 1), (cx - 1, cy), (cx + 1, cy), (cx, cy + 1)):
            r.set(i, j, MT[4])
        put(r, lv >= 1)(cx, cy, GL if lv >= 1 else MT[1])
        r.row(0, TRIM[3])

    M["bracer"] = bracer
    M["lining"] = lambda r: O.cloth(r, SEC, period=4, base=1, folds=False)

    def pants(r):
        if lv >= 2:
            O.cloth(r, SEC, period=3, base=1, top=0.04, bottom=0.08)
        else:
            O.leather(r, LEATH)
        r.row(0, LEATH[1])

    M["pants"] = pants

    def boot(r):
        O.leather(r, LEATH, base=1)
        r.row(-1, LEATH[0])
        if lv >= 1 and r.h >= 3:
            r.row(1, TRIM[2])

    M["boot"] = boot
    M["boot_top"] = lambda r: (r.fill(LEATH[2]), r.noise(0.04))

    def circlet(r, front=False):
        r.fill(MT[2])
        r.row(0, MT[4])
        if r.h > 1:
            r.row(-1, MT[1])
        if front:
            cx = r.w // 2
            r.glow(cx, r.h // 2, GEM[2])
            if r.w > 4:
                r.glow(cx - 1, r.h // 2, GEM[1])
        r.noise(0.02)

    M["circlet"] = circlet
    M["circlet_front"] = lambda r: circlet(r, True)
    M["halo"] = O.glow_fill(GL) if lv >= 2 else (lambda r: (O.metal(r, MT, rivets=False), r.glow(r.w // 2, r.h // 2, GL)))
    return M


HD, BD, RA, LA, RL, LL, RB, LB = O.HD, O.BD, O.RA, O.LA, O.RL, O.LL, O.RB, O.LB
fc, OMIT = O.fc, O.OMIT


def ench_model(name, lv):
    m = O.Model(name)
    O.skeleton(m)
    # ---------------- cabeca: diadema com gema; aureola de runas (II+), pontas (III+)
    m.box(HD, [-4, 28.6, -4], [8, 1.0, 8], fc(side="circlet", n="circlet_front", u=OMIT, d=OMIT), inflate=0.75)
    O.gem(m, HD, [0, 29.1, -4.95], 1.1 + 0.15 * lv, "o_gem@2x2")
    if lv >= 2:
        for x, hgt in ((-1.8, 1.1), (0.0, 1.8), (1.8, 1.1)):
            m.box(HD, [x - 0.35, 29.7, -4.85], [0.7, hgt, 0.5], fc(all="o_point@1x2"))
    if lv >= 3:
        O.gem(m, HD, [0, 33.6, -2.5], 1.0, "o_gem@2x2", rot=[45, 45, 0], pivot=[0, 33.6, -2.5])
    if lv >= 1:
        m.bone("runeHalo", [0, 29.5, 5.6], parent=HD)
        n = 10 + 2 * lv
        rad = 4.6 + 0.5 * lv
        for k in range(n):
            a = 360.0 * k / n
            big = k % (n // 4) == 0
            sw, sh = (1.4, 1.4) if big else (1.1, 0.5)
            mat = "o_gem@2x2" if big else ("halo@2x1" if lv >= 2 else "o_metal_flat@2x1")
            m.box("runeHalo", [-sw / 2, 29.5 + rad - sh / 2, 5.4], [sw, sh, 0.4], fc(all=mat), rot=[0, 0, a], pivot=[0, 29.5, 5.6])

    # ---------------- tronco: casaco, cinto, gola alta, ombreiras
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="coat_front", s="coat_back", ew="coat_side", u="coat_top", d="o_dark@2x2"),
          inflate=0.55)
    m.box(BD, [-4, 12.2, -2], [8, 1.6, 4], fc(n="belt_front", s="belt", ew="belt", u="belt@2x2", d="belt@2x2"), inflate=0.85)
    O.ring(m, BD, -3.0, 3.0, -2.3, 2.3, 23.2, 1.9 + 0.3 * lv, 0.55, side="collar", cap="collar")
    O.gem(m, BD, [0, 12.95, -3.05], [1.2, 1.0, 0.4], "o_gem@2x2")
    if lv >= 1:
        O.pauldrons(m, min(3, lv), gem_mat="o_gem@2x2" if lv >= 2 else None, gem_size=1.1)
    # casaco longo (abas que abrem com o passo)
    O.waist(m, "Chest")
    bottom = {0: 6.0, 1: 4.5, 2: 3.5, 3: 2.5}[lv]
    O.skirt(m, "Chest", bottom, "coat_skirt", "coat_skirt", "coat_skirt", "coat_skirt_in@4x4", "coat_skirt_edge",
            flare=4 + lv, depth=0.55, width=9.6)
    if lv >= 2:
        O.cape(m, 23.6, {2: 7.0, 3: 2.0}[lv], 2.75, 8.8 if lv == 3 else 8.2, out="cape_out", inn="cape_in", edge="cape_edge",
               flare=4, thick=0.45)
    if lv >= 3:
        # aneis de runas orbitando a cintura (dois anelzinhos de gemas)
        O.bead_ring(m, BD, [0, 13.4, 0], 6.4, 12, 0.55, "o_glow@1x1", plane="xz")

    # ---------------- bracos: manga, bracelete runico (o "condutor" dos encantamentos)
    m.box(RA, [-8, 17.5, -2], [4, 6.5, 4], fc(side="sleeve", u="coat_top@2x2", d="lining@2x2"), inflate=0.6)
    m.box(RA, [-8, 12, -2], [4, 5.5, 4], fc(side="bracer", u=OMIT, d="o_metal_flat@2x2"), inflate=0.45)
    m.box(RA, [-8, 15.6, -2], [4, 0.8, 4], fc(side="o_rune_band", u="o_metal_flat@2x2", d="o_metal_flat@2x2"), inflate=0.68)
    if lv >= 2:
        O.gem(m, RA, [-8.75, 14.2, 0], [0.4, 1.1, 1.1], "o_gem@2x2")
    m.mirror(RA, LA)

    # ---------------- pernas
    m.box(RL, [-3.9, 3, -2], [4, 9, 4], fc(side="pants", u=OMIT, d="lining@2x2"), inflate=0.5)
    if lv >= 2:
        m.box(RL, [-3.6, 5.2, -2.75], [3.4, 2.4, 0.6], fc(all="o_metal@2x2"))
        O.gem(m, RL, [-1.9, 6.4, -3.1], 0.7, "o_gem@2x2")
    m.mirror(RL, LL)

    # ---------------- botas
    m.box(RB, [-3.9, 0, -2], [4, 4, 4], fc(side="boot", u="boot_top@2x2", d="sole@2x2"), inflate=0.45)
    m.box(RB, [-3.9, -0.25, -2.8], [4, 1.4, 1.0], fc(side="boot@4x1", u="boot@2x1", d="sole@2x1"), inflate=0.3)
    if lv >= 1:
        m.box(RB, [-3.9, 3.0, -2], [4, 0.7, 4], fc(side="o_rune_band", u=OMIT, d=OMIT), inflate=0.6)
    m.mirror(RB, LB)
    return m


def armor_layers(tid, main, trim, dark):
    c1 = Canvas(64, 32)
    rnd = random.Random(len(tid) * 7)
    for (x0, y0, w, h) in [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = main if (x + y) % 5 else dark
                if rnd.random() < 0.07:
                    c = trim
                c1.set(x, y, c)
    for x in range(64):
        c1.set(x, 31, trim)
    c1.save(os.path.join(A, "textures/models/armor", f"enchanter_{tid}_layer_1.png"))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(A, "textures/models/armor", f"enchanter_{tid}_layer_2.png"))


# ------------------------------------------------------------------ icones das pecas (16x16)
ICON_ROWS = {
    "helmet": [
        "................",
        "................",
        "................",
        "....tttttttt....",
        "...tMMMMMMMMt...",
        "..tMMMMgGMMMMt..",
        "..tMMMMGGMMMMt..",
        "..tttttttttttt..",
        "..m..........m..",
        "................",
    ],
    "chestplate": [
        "..tt........tt..",
        ".tMMt......tMMt.",
        ".tMMMttcctttMMMt",
        ".tMMMMMccMMMMMMt",
        "..MMMMgccMMMMM..",
        "..MMMMMccMMMMM..",
        "..MMMMMccMMMMM..",
        "..bbbbbGGbbbbb..",
        "..MMMMMccMMMMM..",
        "..MMMMMccMMMMM..",
        "..MMMMM..MMMMM..",
        "..MMMM....MMMM..",
        "..tttt....tttt..",
    ],
    "leggings": [
        "...bbbbbbbbbb...",
        "...PPPPPPPPPP...",
        "...PPPPPPPPPP...",
        "...PPPP..PPPP...",
        "...PPPP..PPPP...",
        "...PmPP..PPmP...",
        "...PPPP..PPPP...",
        "...PPPP..PPPP...",
        "...PPPP..PPPP...",
        "...PPPP..PPPP...",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "...LLLL..LLLL...",
        "...rrrr..rrrr...",
        "...LLLL..LLLL...",
        "...LLLL..LLLL...",
        "..LLLLL..LLLLL..",
        "..sssss..sssss..",
    ],
}


def draw_piece_icon(tid, piece):
    T = ENCH_T[tid]
    P = T["pal"]
    MAIN, SEC, TRIM, LEATH = P["MAIN"], P["SEC"], P["TRIM"], P["LEATH"]
    GEM = T["GEM"]
    palette = {"t": TRIM[3], "M": MAIN[2], "m": TRIM[2], "g": GEM[2], "G": GEM[3], "c": SEC[2], "b": LEATH[1],
               "P": (SEC[1] if LV[tid] >= 2 else LEATH[2]), "L": LEATH[2], "r": GEM[2] if LV[tid] >= 1 else TRIM[2],
               "s": LEATH[0]}
    c = Canvas(16, 16)
    rows = ICON_ROWS[piece]
    oy = (16 - len(rows)) // 2
    c.from_rows(rows, palette, 0, oy)
    # sombreado simples: metade de baixo mais escura
    for y in range(16):
        for x in range(16):
            px = c.get(x, y)
            if px[3] and y > oy + len(rows) * 0.55 and px not in (palette["g"], palette["G"]):
                c.set(x, y, darken(px, 0.12))
    c.outline((26, 20, 32, 255))
    if LV[tid] >= 2:
        c.set(14, 1, T["GLOW"])
        c.set(13, 2, T["GLOW"])
    path = os.path.join(A, "textures/item", f"enchanter_{tid}_{piece}.png")
    c.save(path)
    write_json(os.path.join(A, "models/item", f"enchanter_{tid}_{piece}.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/enchanter_{tid}_{piece}"}})


def build_outfit():
    print("== traje do Encantador (GeckoLib)")
    for tid in TIERS:
        lv = LV[tid]
        T = ENCH_T[tid]
        name = f"enchanter_{tid}"
        model = ench_model(name, lv)
        _, glow = O.build(model, ench_mats(T, lv))
        if not glow:
            raise RuntimeError(f"{name}: sem glowmask (o renderer sempre liga a camada de brilho)")
        P = T["pal"]
        armor_layers(tid, P["MAIN"][2], P["TRIM"][2], P["MAIN"][0])
        for piece in PIECES:
            draw_piece_icon(tid, piece)
        if PREVIEW:
            preview_geo(os.path.join(A, "geo/item/armor", name + ".geo.json"),
                        os.path.join(A, "textures/item/armor", name + ".png"), os.path.join(PREVIEW, name + ".png"))


# =====================================================================================
#  MANOPLAS (modelo 3D estilo armory: eixo do braco +Z, punho termina em z=8, costas da mao +Y)
# =====================================================================================
M_ = AR.M
CLOTH_T = M_("wrap", "c8fff4", "5fe3c0", "2a9a88", "145048")
CLOTH_W = M_("wrap", "ffffff", "eef6f4", "c8dcd8", "8aa8a2")
PLATE = [AR.LEATHER, AR.BRONZE, AR.SILVER, AR.SILVER, AR.GOLD]
RUNE_T = M_("rune", "ffffff", "a0fff4", "30c0b0", "08302c", glow=True)
RUNE_G = M_("rune", "ffffff", "fff0b0", "e0b040", "3a2808", glow=True)
GEM_TEAL = M_("gem", "ffffff", "b8fff4", "3ad8c8", "10847a", glow=True)
GLOW_T = M_("glow", "ffffff", "c0fff8", "5fe3c0", "1a8a80", glow=True)
GLOW_G = M_("glow", "ffffff", "fff4c0", "ffd27a", "a07810", glow=True)


def gauntlet_model(name, t):
    """t = 0 (kit) .. 4 (Soberano)."""
    m = AR.Model(name)
    cloth = CLOTH_W if t == 4 else CLOTH_T
    plate = PLATE[t]
    rune = RUNE_G if t >= 3 else RUNE_T
    cy = 6.0
    m.box((5.4, cy - 2.6, 7.4), (10.6, cy + 2.6, 12.2), cloth)
    m.box((5.6, cy - 2.4, 6.6), (10.4, cy + 2.4, 7.4), plate)
    for x in (6.2, 7.4, 8.6, 9.8):
        m.box((x - 0.45, cy + 0.4, 6.0), (x + 0.45, cy + 1.6, 6.6), AR.GOLD if t >= 3 else plate)
    # placa das costas da mao com a runa de encantamento
    m.box((6.0, cy + 2.6, 8.0), (10.0, cy + 3.2, 11.6), plate)
    m.box((6.8, cy + 3.2, 8.6), (9.2, cy + 3.5, 11.0), rune if t >= 1 else AR.LEATHER)
    if t >= 1:
        m.cbox(8, cy + 3.8, 9.8, 1.2, 0.6, 1.2, GEM_TEAL, rot_y45=True)
    # punho com faixa runica
    m.box((5.0, cy - 3.0, 12.2), (11.0, cy + 3.0, 14.8), plate if t >= 1 else cloth)
    m.box((4.9, cy - 3.1, 13.3), (11.1, cy + 3.1, 13.9), rune if t >= 1 else AR.BRONZE)
    if t >= 3:
        # anel de runas flutuando em volta do pulso
        for k, ang in enumerate((-45, -22.5, 0, 22.5, 45)):
            m.box((7.6, cy + 3.6, 15.2), (8.4, cy + 4.2, 16.4), GLOW_G if t == 4 else GLOW_T, rot=("z", ang, (8, cy, 15.8)))
            m.box((7.6, cy - 4.2, 15.2), (8.4, cy - 3.6, 16.4), GLOW_G if t == 4 else GLOW_T, rot=("z", ang, (8, cy, 15.8)))
    if t == 4:
        for s in (1, -1):
            x = 11.0 if s > 0 else 5.0
            m.box((min(x, x + s * 2.0), cy - 0.4, 12.0), (max(x, x + s * 2.0), cy + 0.4, 14.6), AR.GOLD,
                  rot=("y", -22.5 * s, (x, cy, 13.3)))
    return m


GAUNTLET_ICON = [
    (CLOTH_T, AR.LEATHER, None, None),
    (CLOTH_T, AR.BRONZE, GEM_TEAL, None),
    (CLOTH_T, AR.SILVER, GEM_TEAL, RUNE_T),
    (CLOTH_T, AR.SILVER, GEM_TEAL, GLOW_G),
    (CLOTH_W, AR.GOLD, GEM_TEAL, GLOW_T),
]


def build_gauntlets():
    print("== manoplas do Encantador")
    ids = ["enchanter_gauntlets"] + [f"enchanter_gauntlets_t{i}" for i in range(1, 5)]
    for t, iid in enumerate(ids):
        model = gauntlet_model(iid, t)
        tex = f"{NS}:item/class_enchanter/{iid}"
        try:
            held, atlas = model.build(tex, seed=1000 + t)
        except RuntimeError:
            model.atlas = 64
            held, atlas = model.build(tex, seed=1000 + t)
        held["display"] = AR.DISPLAY["fist"]
        atlas.save(os.path.join(A, "textures/item/class_enchanter", iid + ".png"))
        write_json(os.path.join(A, "models/item/class_enchanter", iid + "_held.json"), held)
        cloth, plate, gemm, glow = GAUNTLET_ICON[t]
        icon = AR.icon_gauntlet(max(1, t), cloth, plate, gemm, glow)
        icon.save(os.path.join(A, "textures/item", iid + ".png"))
        flat = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{iid}"}}
        write_json(os.path.join(A, "models/item", iid + ".json"), {
            "loader": "neoforge:separate_transforms",
            "gui_light": "front",
            "textures": {"particle": f"{NS}:item/{iid}"},
            "base": {"parent": f"{NS}:item/class_enchanter/{iid}_held"},
            "perspectives": {"gui": flat, "fixed": flat},
        })
        if PREVIEW:
            AR.preview(model, os.path.join(PREVIEW, iid + ".png"), yaw=35, pitch=12, center=(8, 6, 10))


# =====================================================================================
#  CODEX, ICONES DE EFEITO E DA ABA
# =====================================================================================
OUTLINE = (26, 20, 32, 255)


def build_codex():
    c = Canvas(16, 16)
    cover = [H("145048"), H("1f7e72"), H("2fa898"), H("5fe3c0")]
    gold = [H("8f5a12"), H("d49a22"), H("f5cc4a"), H("fff6b0")]
    c.rect(3, 2, 12, 13, cover[1])
    c.rect(4, 2, 12, 12, cover[2])
    c.rect(3, 2, 3, 13, cover[0])
    c.rect(4, 13, 12, 13, H("e8e0c8"))
    for y in range(3, 12):
        c.set(12, y, cover[1])
    # selo dourado com a runa turquesa brilhando
    for (x, y) in ((8, 4), (7, 5), (9, 5), (6, 6), (10, 6), (7, 7), (9, 7), (8, 8), (6, 8), (10, 8), (8, 10)):
        c.set(x, y, gold[2])
    c.set(8, 6, H("b8fff4"))
    c.set(8, 7, H("5fe3c0"))
    for y in (3, 11):
        for x in range(5, 12):
            c.set(x, y, gold[1])
    c.outline(OUTLINE)
    c.save(os.path.join(A, "textures/item/enchanter_codex.png"))
    write_json(os.path.join(A, "models/item/enchanter_codex.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/enchanter_codex"}})


def effect_icon(name, base, draw):
    c = Canvas(18, 18)
    draw(c, H(base), lighten(H(base), 0.45), darken(H(base), 0.45))
    c.outline(OUTLINE)
    c.save(os.path.join(A, "textures/mob_effect", name + ".png"))


def _fist(c, b, l, d):
    c.rect(5, 6, 12, 12, b)
    for x in (5, 7, 9, 11):
        c.rect(x, 4, x + 1, 6, l)
    c.rect(4, 8, 5, 11, d)
    c.rect(6, 13, 11, 15, d)
    c.line(13, 3, 15, 1, l)
    c.line(14, 5, 16, 4, l)


def _haste(c, b, l, d):
    for k, x in enumerate((3, 7, 11)):
        col = (l, b, d)[k]
        c.line(x, 3, x + 4, 8, col)
        c.line(x + 4, 9, x, 14, col)
        c.line(x + 1, 3, x + 5, 8, col)
        c.line(x + 5, 9, x + 1, 14, col)


def _aegis(c, b, l, d):
    c.rect(4, 3, 13, 9, b)
    for y in range(10, 16):
        k = y - 9
        c.rect(4 + k, y, 13 - k, y, b)
    c.rect(8, 4, 9, 13, l)
    c.rect(5, 7, 12, 8, l)
    c.outline_rect(4, 3, 13, 3, d)


def _vital(c, b, l, d):
    c.circle(6, 7, 3, b)
    c.circle(11, 7, 3, b)
    for y in range(8, 15):
        k = y - 8
        c.rect(3 + k, y, 14 - k, y, b)
    c.set(5, 5, l)
    c.set(6, 5, l)
    c.rect(8, 6, 9, 11, l)
    c.rect(6, 8, 11, 9, l)


def _imbue(c, b, l, d):
    c.line(3, 14, 12, 5, H("d8e2ea"))
    c.line(4, 14, 13, 5, H("aab6c8"))
    c.line(12, 4, 14, 2, H("ffffff"))
    c.rect(3, 11, 6, 12, d)
    c.circle(12, 12, 3, b)
    c.set(12, 12, l)
    c.set(11, 11, l)


def _overdrive(c, b, l, d):
    pts = [(10, 1), (5, 9), (9, 9), (6, 16), (13, 7), (9, 7), (12, 1)]
    for i in range(len(pts) - 1):
        c.line(*pts[i], *pts[i + 1], b)
    c.rect(8, 3, 10, 6, b)
    c.rect(7, 9, 9, 11, b)
    c.set(10, 2, l)
    c.set(8, 10, l)


def _gear(c, b, l, d):
    c.rect(4, 4, 13, 14, b)
    c.rect(2, 4, 3, 8, b)
    c.rect(14, 4, 15, 8, b)
    c.rect(7, 4, 10, 5, d)
    c.rect(8, 8, 9, 13, l)
    for (x, y) in ((5, 7), (12, 10), (6, 12), (11, 6)):
        c.set(x, y, H("ffffff"))


def _anti(c, b, l, d):
    cx, cy, r = 8.5, 8.5, 6.5
    pts = [(cx + r * math.cos(math.pi / 3 * k + math.pi / 6), cy + r * math.sin(math.pi / 3 * k + math.pi / 6)) for k in range(6)]
    for k in range(6):
        x0, y0 = pts[k]
        x1, y1 = pts[(k + 1) % 6]
        c.line(int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1)), b)
    c.line(4, 4, 13, 13, l)
    c.line(5, 4, 14, 13, d)


def _heaven(c, b, l, d):
    # olho fechado (sem mana) sobre um punho
    c.line(3, 6, 8, 8, b)
    c.line(8, 8, 14, 6, b)
    for x in (4, 7, 10, 13):
        c.set(x, 9, d)
    c.rect(5, 11, 12, 15, b)
    for x in (5, 7, 9, 11):
        c.rect(x, 10, x + 1, 11, l)
    c.set(2, 2, H("c8303c"))
    c.set(15, 2, H("c8303c"))


EFFECTS = [
    ("enchant_might", "ff8a5c", _fist), ("enchant_haste", "9ff0d0", _haste), ("enchant_aegis", "ffe27a", _aegis),
    ("enchant_vitality", "7cf08a", _vital), ("enchant_imbue", "b98cff", _imbue), ("enchant_overdrive", "ff5fa8", _overdrive),
    ("enchant_gear", "5fe3c0", _gear), ("anti_magic", "bfe8ff", _anti), ("heavenly_restriction", "9aa3ae", _heaven),
]


def build_icons():
    print("== icones (efeitos, aba, codex)")
    for name, col, fn in EFFECTS:
        effect_icon(name, col, fn)
    c = Canvas(16, 16)
    _anti(c, H("5fe3c0"), H("e8ffff"), H("1f7e72"))
    c.outline(OUTLINE)
    c.save(os.path.join(A, "textures/gui/tab/enchanter.png"))
    build_codex()


# =====================================================================================
#  SANTUARIO DO SOBERANO (entidade GeckoLib): aneis de runas, hexagrama, tabuas e coroa
# =====================================================================================
SANCT_REGIONS = {
    "strip": (0, 0, 32, 4), "plate": (0, 4, 16, 4), "bar": (0, 8, 32, 2), "tablet": (32, 0, 8, 14),
    "gem": (40, 0, 8, 8), "crown": (48, 0, 8, 8), "stone": (16, 4, 16, 4),
}


def sanctum_texture():
    c = Canvas(64, 16)
    g = Canvas(64, 16)
    rnd = random.Random(77)
    teal = [H("08302c"), H("145048"), H("2fa898"), H("5fe3c0"), H("c0fff8")]
    gold = [H("5a3a04"), H("a07810"), H("d49a22"), H("ffd27a"), H("fff6c8")]
    # faixa de runas: fundo escuro + runas que brilham
    x0, y0, w, h = SANCT_REGIONS["strip"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c.set(x, y, teal[1] if (y in (y0, y0 + h - 1)) else teal[0])
    for x in range(x0 + 1, x0 + w - 1, 3):
        for y in range(y0 + 1, y0 + h - 1):
            if rnd.random() < 0.75:
                col = teal[4] if rnd.random() < 0.3 else teal[3]
                c.set(x, y, col)
                g.set(x, y, col)
    for name, pal_ in (("plate", gold), ("crown", gold), ("stone", [H("2a2a34"), H("3c3c48"), H("54546a"), H("70708a"), H("9a9ab4")])):
        x0, y0, w, h = SANCT_REGIONS[name]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                col = pal_[2]
                if y == y0:
                    col = pal_[3]
                elif y == y0 + h - 1:
                    col = pal_[1]
                if rnd.random() < 0.12:
                    col = pal_[1]
                c.set(x, y, col)
    x0, y0, w, h = SANCT_REGIONS["bar"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            col = teal[4] if y == y0 else teal[3]
            c.set(x, y, col)
            g.set(x, y, col)
    # tabua: pedra clara com um glifo brilhante
    x0, y0, w, h = SANCT_REGIONS["tablet"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            c.set(x, y, gold[2] if edge else H("d8d2bc"))
    for (dx, dy) in ((3, 2), (4, 2), (2, 3), (5, 3), (3, 4), (4, 4), (3, 5), (4, 6), (3, 7), (2, 8), (5, 8), (3, 9), (4, 10),
                     (3, 11), (4, 11)):
        c.set(x0 + dx, y0 + dy, teal[3])
        g.set(x0 + dx, y0 + dy, teal[3])
    x0, y0, w, h = SANCT_REGIONS["gem"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            t = ((x - x0) + (y - y0)) / (w + h - 2)
            col = teal[4] if t < 0.25 else teal[3] if t < 0.6 else teal[2]
            c.set(x, y, col)
            g.set(x, y, col)
    c.save(os.path.join(A, "textures/entity/enchanter_sanctum.png"))
    g.save(os.path.join(A, "textures/entity/enchanter_sanctum_glowmask.png"))


def _uv(name):
    x, y, w, h = SANCT_REGIONS[name]
    return {"uv": [x, y], "uv_size": [w, h]}


def _cube(origin, size, mat, rot=None, pivot=None, top=None):
    faces = {f: _uv(mat) for f in ("north", "south", "east", "west", "down")}
    faces["up"] = _uv(top or mat)
    cb = {"origin": [round(v, 4) for v in origin], "size": [round(v, 4) for v in size], "uv": faces}
    if rot:
        cb["rotation"] = [round(v, 4) for v in rot]
        cb["pivot"] = [round(v, 4) for v in (pivot or [0, 0, 0])]
    return cb


def sanctum_geo():
    bones = []

    def bone(name, pivot, parent=None, cubes=None):
        b = {"name": name, "pivot": pivot}
        if parent:
            b["parent"] = parent
        if cubes:
            b["cubes"] = cubes
        bones.append(b)

    bone("sanctum", [0, 0, 0])
    # anel externo: 24 placas de runas no raio 76 (5 blocos = 80 px)
    outer = []
    for k in range(24):
        a = 360.0 * k / 24
        outer.append(_cube([-10.2, 0.1, 74], [20.4, 0.6, 4], "plate", rot=[0, a, 0], top="strip"))
    bone("ringOuter", [0, 0, 0], "sanctum", outer)
    # anel interno: 16 placas no raio 54
    inner = []
    for k in range(16):
        a = 360.0 * k / 16 + 11.25
        inner.append(_cube([-10.8, 0.2, 52.5], [21.6, 0.5, 3], "stone", rot=[0, a, 0], top="strip"))
    bone("ringInner", [0, 0, 0], "sanctum", inner)
    # hexagrama (dois triangulos) inscrito no raio 48
    hexa = []
    side = 48 * math.sqrt(3)
    for tri in range(2):
        for k in range(3):
            a = 120.0 * k + 60.0 * tri
            hexa.append(_cube([-side / 2, 0.15, 23.0], [side, 0.35, 1.6], "bar", rot=[0, a, 0]))
    bone("hexagram", [0, 0, 0], "sanctum", hexa)
    # 8 tabuas de runas em pe, viradas para fora
    tabs = []
    for k in range(8):
        a = 45.0 * k + 22.5
        tabs.append(_cube([-4, 10, 63], [8, 14, 1.2], "tablet", rot=[0, a, 0]))
    bone("runes", [0, 0, 0], "sanctum", tabs)
    # coroa flutuando no centro + gema
    crown = []
    for k in range(8):
        a = 45.0 * k
        crown.append(_cube([-2.4, 40, 6.2], [4.8, 2.4, 1.0], "crown", rot=[0, a, 0], pivot=[0, 40, 0]))
        crown.append(_cube([-0.8, 42.4, 6.2], [1.6, 3.2 if k % 2 == 0 else 2.0, 1.0], "crown", rot=[0, a, 0], pivot=[0, 40, 0]))
    bone("crown", [0, 40, 0], "sanctum", crown)
    core = [_cube([-3, 43, -3], [6, 6, 6], "gem", rot=[45, 0, 45], pivot=[0, 46, 0]),
            _cube([-2, 44, -2], [4, 4, 4], "gem", rot=[0, 45, 0], pivot=[0, 46, 0])]
    bone("core", [0, 46, 0], "crown", core)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.enchanter_sanctum", "texture_width": 64, "texture_height": 16,
                        "visible_bounds_width": 12, "visible_bounds_height": 6, "visible_bounds_offset": [0, 2, 0]},
        "bones": bones}]}
    write_json(os.path.join(A, "geo/entity/enchanter_sanctum.geo.json"), geo)


def _kf(pairs):
    return {("%g" % t if t != int(t) else "%.1f" % t): v for t, v in pairs}


def sanctum_anim():
    idle_len = 12.0
    anims = {
        "animation.enchanter_sanctum.open": {
            "animation_length": 0.75,
            "bones": {
                "sanctum": {"scale": _kf([(0.0, [0.05, 0.05, 0.05]), (0.5, [1.08, 1.0, 1.08]), (0.75, [1.0, 1.0, 1.0])])},
                "crown": {"position": _kf([(0.0, [0, 30, 0]), (0.75, [0, 0, 0])])},
                "runes": {"position": _kf([(0.0, [0, -14, 0]), (0.75, [0, 0, 0])])},
            },
        },
        "animation.enchanter_sanctum.idle": {
            "loop": True,
            "animation_length": idle_len,
            "bones": {
                "ringOuter": {"rotation": _kf([(i * 3.0, [0, i * 90.0, 0]) for i in range(5)])},
                "ringInner": {"rotation": _kf([(i * 3.0, [0, -i * 90.0, 0]) for i in range(5)])},
                "hexagram": {"rotation": _kf([(i * 3.0, [0, i * 30.0, 0]) for i in range(5)])},
                "runes": {"rotation": _kf([(i * 3.0, [0, -i * 22.5, 0]) for i in range(5)]),
                          "position": _kf([(0.0, [0, 0, 0]), (3.0, [0, 2.5, 0]), (6.0, [0, 0, 0]), (9.0, [0, 2.5, 0]), (12.0, [0, 0, 0])])},
                "crown": {"rotation": _kf([(i * 3.0, [0, i * 90.0, 0]) for i in range(5)]),
                          "position": _kf([(0.0, [0, 0, 0]), (2.0, [0, 1.5, 0]), (4.0, [0, 0, 0]), (6.0, [0, 1.5, 0]), (8.0, [0, 0, 0]),
                                           (10.0, [0, 1.5, 0]), (12.0, [0, 0, 0])])},
                "core": {"rotation": _kf([(i * 1.5, [0, i * 90.0, 0]) for i in range(9)]),
                         "scale": _kf([(0.0, [1, 1, 1]), (1.5, [1.15, 1.15, 1.15]), (3.0, [1, 1, 1]), (4.5, [1.15, 1.15, 1.15]),
                                       (6.0, [1, 1, 1]), (7.5, [1.15, 1.15, 1.15]), (9.0, [1, 1, 1]), (10.5, [1.15, 1.15, 1.15]),
                                       (12.0, [1, 1, 1])])},
            },
        },
    }
    write_json(os.path.join(A, "animations/entity/enchanter_sanctum.animation.json"),
               {"format_version": "1.8.0", "animations": anims})


def build_sanctum():
    print("== santuario do soberano")
    sanctum_texture()
    sanctum_geo()
    sanctum_anim()
    if PREVIEW:
        preview_geo(os.path.join(A, "geo/entity/enchanter_sanctum.geo.json"), os.path.join(A, "textures/entity/enchanter_sanctum.png"),
                    os.path.join(PREVIEW, "enchanter_sanctum.png"), pitch=35, scale=2.2)


# =====================================================================================
#  Pre-visualizacao simples de .geo.json (pintor por profundidade, cor media de cada face)
# =====================================================================================
def _rot(p, rx, ry, rz, piv):
    x, y, z = p[0] - piv[0], p[1] - piv[1], p[2] - piv[2]
    rx, ry, rz = math.radians(rx), math.radians(ry), math.radians(rz)
    # Bedrock: aplica Z, depois Y, depois X
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return [x + piv[0], y + piv[1], z + piv[2]]


def preview_geo(geo_path, tex_path, out, yaw=205.0, pitch=12.0, scale=7.0, size=(300, 360)):
    from pixelart import Canvas as Cv
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    tex = _read_png(tex_path)
    bones = {b["name"]: b for b in geo["bones"]}
    quads = []
    for b in geo["bones"]:
        for cb in b.get("cubes", []):
            o, s = cb["origin"], cb["size"]
            inf = cb.get("inflate", 0)
            x0, y0, z0 = o[0] - inf, o[1] - inf, o[2] - inf
            x1, y1, z1 = o[0] + s[0] + inf, o[1] + s[1] + inf, o[2] + s[2] + inf
            faces = {
                "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
                "south": [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
                "west": [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
                "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
                "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
                "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
            }
            for f, uv in cb.get("uv", {}).items():
                col = _avg(tex, uv["uv"], uv["uv_size"])
                if col is None:
                    continue
                pts = [list(p) for p in faces[f]]
                if "rotation" in cb:
                    pts = [_rot(p, *cb["rotation"], cb.get("pivot", [0, 0, 0])) for p in pts]
                bb = b
                while bb is not None:
                    if "rotation" in bb:
                        pts = [_rot(p, -bb["rotation"][0], -bb["rotation"][1], bb["rotation"][2], bb["pivot"]) for p in pts]
                    bb = bones.get(bb.get("parent")) if bb.get("parent") else None
                shade = {"up": 1.0, "north": 0.85, "south": 0.85, "east": 0.7, "west": 0.7, "down": 0.5}[f]
                quads.append((pts, tuple(int(c * shade) for c in col[:3]) + (255,)))
    W, Hh = size
    cv = Cv(W, Hh, (40, 40, 48, 255))
    zbuf = [[-1e9] * W for _ in range(Hh)]
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    ys = [p[1] for q, _ in quads for p in q] or [0]
    midy = (max(ys) + min(ys)) / 2

    def proj(p):
        x, y, z = p[0], p[1] - midy, p[2]
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (W / 2 + x * scale, Hh / 2 - y * scale, z)

    for pts, col in quads:
        pp = [proj(p) for p in pts]
        AR._fill_quad(cv, zbuf, pp, col)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    cv.save(out)


def _read_png(path):
    import zlib as z
    data = open(path, "rb").read()
    pos = 8
    w = h = 0
    idat = b""
    while pos < len(data):
        ln = int.from_bytes(data[pos:pos + 4], "big")
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h = int.from_bytes(body[0:4], "big"), int.from_bytes(body[4:8], "big")
        elif typ == b"IDAT":
            idat += body
        pos += 12 + ln
    raw = z.decompress(idat)
    stride = w * 4
    px, prev, i = [], [0] * stride, 0
    for _ in range(h):
        ft = raw[i]
        i += 1
        line = list(raw[i:i + stride])
        i += stride
        for k in range(stride):
            a = line[k - 4] if k >= 4 else 0
            b = prev[k]
            c = prev[k - 4] if k >= 4 else 0
            if ft == 1:
                line[k] = (line[k] + a) & 255
            elif ft == 2:
                line[k] = (line[k] + b) & 255
            elif ft == 3:
                line[k] = (line[k] + (a + b) // 2) & 255
            elif ft == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[k] = (line[k] + pr) & 255
        px.append([tuple(line[x * 4:x * 4 + 4]) for x in range(w)])
        prev = line
    return px


def _avg(tex, uv, size):
    x0, y0 = int(uv[0]), int(uv[1])
    w, h = max(1, int(abs(size[0]))), max(1, int(abs(size[1])))
    acc, n = [0, 0, 0], 0
    for y in range(y0, min(len(tex), y0 + h)):
        for x in range(x0, min(len(tex[0]), x0 + w)):
            p = tex[y][x]
            if p[3] > 0:
                acc[0] += p[0]
                acc[1] += p[1]
                acc[2] += p[2]
                n += 1
    return None if n == 0 else (acc[0] // n, acc[1] // n, acc[2] // n, 255)


# =====================================================================================
#  RECEITAS E TAGS
# =====================================================================================
def it(i):
    return {"item": i if ":" in i else "minecraft:" + i}


def shaped(name, pattern, key, result, count=1):
    write_json(os.path.join(D, "recipe/class_enchanter", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
        "key": {k: it(v) for k, v in key.items() if any(k in row for row in pattern)}, "result": {"id": f"{NS}:{result}", "count": count}})


def shapeless(name, items, result):
    write_json(os.path.join(D, "recipe/class_enchanter", name + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": "equipment",
        "ingredients": [it(i) for i in items], "result": {"id": f"{NS}:{result}", "count": 1}})


def smithing(name, base, addition, result):
    write_json(os.path.join(D, "recipe/class_enchanter", name + ".json"), {
        "neoforge:conditions": [{"type": "neoforge:item_exists", "item": f"{NS}:arcane_upgrade_template"}],
        "type": "minecraft:smithing_transform",
        "template": it(f"{NS}:arcane_upgrade_template"), "base": it(f"{NS}:{base}"), "addition": it(addition),
        "result": {"id": f"{NS}:{result}", "count": 1}})


def build_data():
    print("== receitas e tags")
    shapeless("enchanter_codex", ["book", "lapis_lazuli", "lapis_lazuli", f"{NS}:essence_arcane"], "enchanter_codex")
    shaped("enchanter_gauntlets", ["LKL", "I I"], {"L": "leather", "K": "lapis_lazuli", "I": "iron_ingot"}, "enchanter_gauntlets")
    shapeless("enchanter_gauntlets_t1", [f"{NS}:enchanter_gauntlets", "gold_ingot", "lapis_lazuli", "lapis_lazuli", "amethyst_shard"],
              "enchanter_gauntlets_t1")
    smithing("enchanter_gauntlets_t2", "enchanter_gauntlets_t1", f"{NS}:mana_crystal", "enchanter_gauntlets_t2")
    smithing("enchanter_gauntlets_t3", "enchanter_gauntlets_t2", "diamond", "enchanter_gauntlets_t3")
    smithing("enchanter_gauntlets_t4", "enchanter_gauntlets_t3", "netherite_ingot", "enchanter_gauntlets_t4")
    key = {"C": "cyan_wool", "G": "gold_nugget", "L": "leather", "K": "lapis_lazuli"}
    shaped("enchanter_novice_helmet", ["CGC", "L L"], key, "enchanter_novice_helmet")
    shaped("enchanter_novice_chestplate", ["L L", "CKC", "CLC"], key, "enchanter_novice_chestplate")
    shaped("enchanter_novice_leggings", ["CKC", "L L", "C C"], key, "enchanter_novice_leggings")
    shaped("enchanter_novice_boots", ["L L", "C C"], key, "enchanter_novice_boots")

    weapons = [f"{NS}:enchanter_gauntlets"] + [f"{NS}:enchanter_gauntlets_t{i}" for i in range(1, 5)]
    write_json(os.path.join(MERGE, "data/minecraft/tags/item/swords.json"), {"values": weapons})
    write_json(os.path.join(MERGE, "data/c/tags/item/tools/melee_weapon.json"), {"values": weapons})
    for piece, tag in zip(PIECES, ("head_armor", "chest_armor", "leg_armor", "foot_armor")):
        write_json(os.path.join(MERGE, f"data/minecraft/tags/item/{tag}.json"),
                   {"values": [f"{NS}:enchanter_{t}_{piece}" for t in TIERS]})


# =====================================================================================
#  TRADUCOES
# =====================================================================================
def build_lang():
    print("== traducoes")
    from class_enchanter_lang import PT, EN  # noqa: E402
    import re

    def pct(v):
        # "%" solto vira "%%" (o Minecraft so aceita %s e %%)
        return re.sub(r"%%|%s|%", lambda m: m.group(0) if m.group(0) in ("%%", "%s") else "%%", v)

    for lg, data in (("pt_br", PT), ("en_us", EN)):
        write_json(os.path.join(LANG, lg + ".json"), {k: pct(v) for k, v in sorted(data.items())})
    missing = set(PT) ^ set(EN)
    if missing:
        raise RuntimeError("chaves so num idioma: " + ", ".join(sorted(missing)))


def main():
    global PREVIEW
    if "--preview" in sys.argv:
        PREVIEW = sys.argv[sys.argv.index("--preview") + 1]
    build_outfit()
    build_gauntlets()
    build_icons()
    build_sanctum()
    build_data()
    build_lang()
    print("ok")


if __name__ == "__main__":
    main()
