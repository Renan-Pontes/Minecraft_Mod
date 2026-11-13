"""
Assets da v1.0 do modulo mounts (rode da raiz do projeto: py -I tools/gen/mounts_v1.py):
  - modelos GeckoLib, animacoes e texturas das 3 montarias de CHAO: land_drake, nightmare, aurora_stag
  - itens: Redeas (3), modelos dos ovos
  - loot tables, spawn do Dragao Terrestre, loja da Guilda (guild_shop/mounts.json),
    capitulo do Diario (journal/mounts.json) e traducoes pt_br / en_us

Reusa o construtor de modelos de tools/gen/mounts.py. As traducoes sao ACRESCENTADAS aos arquivos do
modulo (o mounts.py reescreve os arquivos inteiros: se rodar aquele, rode este de novo em seguida).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import mounts as M  # noqa: E402
from mounts import (Model, H, SIDES, save_json, save_anims, anim, rot, const_rot, keys, sampled, S, saddle,  # noqa: E402
                    item_entry, A, D, LANG, NS, TAU)
from pixelart import Canvas, darken, lighten, item_model  # noqa: E402


def merge_lang(folder, entries):
    """Acrescenta/atualiza chaves nos pt_br.json / en_us.json da pasta (mantem o que ja existe)."""
    for idx, name in enumerate(("pt_br.json", "en_us.json")):
        path = os.path.join(folder, name)
        cur = {}
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                cur = json.load(f)
        for k, v in entries.items():
            cur[k] = v[idx]
        save_json(path, cur)


# =====================================================================================
#  Animacoes comuns de quadrupede
# =====================================================================================
LEGS4 = ("leg_fl", "leg_fr", "leg_bl", "leg_br")


def quad_anims(ident, sit_drop, tail_axis="fz", run_len=0.5):
    L = 3.0
    idle = anim(L, {
        "neck": rot(L, 8, fx=S(L, 2.5)), "head": rot(L, 8, fx=S(L, 2.5, phase=1.0)),
        "tail": rot(L, 12, **{tail_axis: S(L, 9, 1.0)}),
        "body": {"position": sampled(L, 8, lambda t: [0, 0.2 * math.sin(TAU * t / L), 0])},
    })
    W = 1.0
    walk = anim(W, {
        "leg_fl": rot(W, 8, fx=S(W, 24)), "leg_br": rot(W, 8, fx=S(W, 22, phase=0.4)),
        "leg_fr": rot(W, 8, fx=S(W, -24)), "leg_bl": rot(W, 8, fx=S(W, -22, phase=0.4)),
        "neck": rot(W, 8, fx=S(W, 4, 2.0)), "tail": rot(W, 8, **{tail_axis: S(W, 6)}),
        "body": {"position": sampled(W, 8, lambda t: [0, 0.3 * abs(math.sin(TAU * t / W)), 0])},
    })
    G = run_len
    run = anim(G, {
        "leg_fl": rot(G, 8, fx=S(G, 50)), "leg_fr": rot(G, 8, fx=S(G, 50, phase=0.6)),
        "leg_bl": rot(G, 8, fx=S(G, -45, phase=1.2)), "leg_br": rot(G, 8, fx=S(G, -45, phase=1.8)),
        "body": {"rotation": sampled(G, 8, lambda t: [5 * math.sin(TAU * t / G), 0, 0]),
                 "position": sampled(G, 8, lambda t: [0, 1.0 * abs(math.sin(TAU * t / G)), 0])},
        "neck": rot(G, 8, fx=S(G, -8, base=-6)), "tail": rot(G, 8, fx=S(G, 10, base=25)),
    })
    T = 3.0
    sit = anim(T, {
        "root": {"position": keys((0, [0, -sit_drop, 0]))},
        "leg_fl": const_rot([-86, 0, 0]), "leg_fr": const_rot([-86, 0, 0]),
        "leg_bl": const_rot([-82, 0, 0]), "leg_br": const_rot([-82, 0, 0]),
        "neck": rot(T, 8, fx=S(T, 2, base=-8)), "tail": rot(T, 8, **{tail_axis: S(T, 5)}),
    })
    save_anims(ident, {"idle": idle, "walk": walk, "run": run, "sit": sit})


# =====================================================================================
#  1. PESADELO (corcel negro de crina em chamas)
# =====================================================================================
def nightmare():
    m = Model("nightmare")
    coat = H("1E1622"); coat2 = H("2E2136"); fire = H("FF7A1F"); fire2 = H("FFD23D"); ember = H("FF4A12")
    eye = H("FF3B1F"); horn = H("3A2C40")

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 15, 0], "root")
    bd = m.cube("body", [-5, 11, -10], [10, 10, 22], coat, col2=coat2, belly=darken(coat, 0.3), pattern="smooth")
    # Rachaduras de brasa nos flancos
    for s in ("left", "right"):
        for (i, j) in ((4, 3), (5, 4), (6, 4), (12, 6), (13, 5), (14, 5), (17, 3), (18, 4)):
            m.px(bd[s], i, j, ember, True)
        m.px(bd[s], 5, 3, fire2, True); m.px(bd[s], 13, 6, fire2, True)

    m.bone("neck", [0, 18, -8], "body", rot=[30, 0, 0])
    m.cube("neck", [-2.5, 16, -11], [5, 12, 6], coat, col2=coat2, pattern="smooth")
    m.cube("neck", [-1, 17, -5], [2, 14, 3], fire, col2=fire2, pattern="hair", glow=True)
    m.bone("look", [0, 27, -8], "neck")
    m.bone("head", [0, 27, -8], "look")
    hd = m.cube("head", [-3, 25, -15], [6, 6, 8], coat, col2=coat2, pattern="smooth")
    for s, ii in (("left", 1), ("right", 6)):
        m.px(hd[s], ii, 2, eye, True); m.px(hd[s], ii + (1 if s == "left" else -1), 2, fire2, True)
        m.px(hd[s], ii, 1, ember, True)
    mz = m.cube("head", [-2, 25, -20], [4, 5, 5], coat, col2=coat2)
    m.px(mz["front"], 0, 2, ember, True); m.px(mz["front"], 3, 2, ember, True)
    for x in (-3, 2):
        m.cube("head", [x, 31, -10], [1, 4, 1], horn, col2=lighten(horn, 0.3))
    m.cube("head", [-1, 30, -14], [2, 3, 6], fire, col2=fire2, pattern="hair", glow=True)

    for name, x, z in (("leg_fl", 3, -7), ("leg_fr", -3, -7), ("leg_bl", 3, 9), ("leg_br", -3, 9)):
        m.bone(name, [x, 11, z], "body")
        ox = x - 1.5
        m.cube(name, [ox, 3, z - 1.5], [3, 9, 3], coat, col2=coat2, pattern="smooth")
        m.cube(name, [ox - 0.25, 0, z - 1.75], [3.5, 3, 3.5], ember, col2=fire2, top=coat2, glow=True)

    m.bone("tail", [0, 20, 12], "body", rot=[30, 0, 0])
    m.cube("tail", [-1.5, 8, 11], [3, 12, 3], fire, col2=fire2, pattern="hair", glow=True)

    saddle(m, "body", 21, 5.5, z0=-4, depth=9, belly_y=10.5)
    m.save((4, 3, 1.0))
    quad_anims("nightmare", sit_drop=8)


# =====================================================================================
#  2. CERVO DA AURORA (branco, galhada luminosa)
# =====================================================================================
def aurora_stag():
    m = Model("aurora_stag")
    coat = H("F1F5F8"); coat2 = H("CFDDE6"); white = H("FFFFFF"); glow = H("8FF5D8"); glow2 = H("5FD8E8")
    hoof = H("3A4A55"); eye = H("2A6A8A"); nose = H("4A3A40")

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 17, 0], "root")
    bd = m.cube("body", [-4, 13, -9], [8, 8, 18], coat, col2=coat2, belly=white, pattern="smooth")
    # Pintas de aurora no dorso
    for (i, j) in ((2, 4), (5, 7), (3, 11), (6, 14), (1, 9), (4, 2)):
        m.px(bd["top"], i, j, glow, True)
    for s in ("left", "right"):
        for (i, j) in ((3, 1), (7, 2), (11, 1), (14, 2)):
            m.px(bd[s], i, j, glow2, True)
    m.cube("body", [-3.5, 12, -10], [7, 5, 4], white, col2=coat, pattern="fur")

    m.bone("neck", [0, 19, -7], "body", rot=[18, 0, 0])
    m.cube("neck", [-2, 18, -10], [4, 11, 4], coat, col2=coat2, belly=white, pattern="smooth")
    m.bone("look", [0, 28, -8], "neck")
    m.bone("head", [0, 28, -8], "look")
    hd = m.cube("head", [-2.5, 26, -13], [5, 5, 6], coat, col2=coat2, pattern="smooth")
    for s, ii in (("left", 1), ("right", 4)):
        m.px(hd[s], ii, 2, eye); m.px(hd[s], ii, 1, glow2, True)
    sn = m.cube("head", [-1.5, 26, -17], [3, 3, 4], coat, col2=white)
    m.hline(sn["front"], 0, nose)
    for x in (-4, 2.5):
        m.cube("head", [x, 29, -9], [1.5, 2, 1], coat, col2=coat2)
    # Galhada (brilha)
    for sgn in (1, -1):
        x = 1.5 if sgn > 0 else -2.5
        m.cube("head", [x, 31, -10], [1, 7, 1], glow, col2=glow2, glow=True)
        m.cube("head", [x, 35, -13], [1, 1, 3], glow, glow=True)
        ox = x + 1 if sgn > 0 else x - 3
        m.cube("head", [ox, 34, -10], [3, 1, 1], glow2, glow=True)
        oy = x + 3 if sgn > 0 else x - 3
        m.cube("head", [oy, 35, -10], [1, 4, 1], glow, col2=white, glow=True)
        m.cube("head", [x, 37, -9], [1, 1, 3], glow2, glow=True)

    for name, x, z in (("leg_fl", 2.5, -7), ("leg_fr", -2.5, -7), ("leg_bl", 2.5, 7), ("leg_br", -2.5, 7)):
        m.bone(name, [x, 13, z], "body")
        ox = x - 1
        m.cube(name, [ox, 2, z - 1], [2, 11, 2], coat, col2=coat2, pattern="smooth")
        m.cube(name, [ox - 0.25, 0, z - 1.25], [2.5, 2, 2.5], hoof, col2=darken(hoof, 0.3), top=coat2)

    m.bone("tail", [0, 20, 9], "body", rot=[-25, 0, 0])
    tl = m.cube("tail", [-1, 17, 9], [2, 4, 2], white, col2=coat)
    for s in SIDES:
        m.px(tl[s], 0, 0, glow, True)

    saddle(m, "body", 21, 4.5, z0=-4, depth=8, belly_y=12.5)
    m.save((4, 4, 1.2))
    quad_anims("aurora_stag", sit_drop=10, tail_axis="fx", run_len=0.45)


# =====================================================================================
#  3. DRAGAO TERRESTRE (lagarto corredor bipede)
# =====================================================================================
def land_drake():
    m = Model("land_drake")
    skin = H("6E8B4A"); skin2 = H("4E6B38"); belly = H("D9C27A"); stripe = H("3D5230"); claw = H("E8E0C8")
    eye = H("FFC23D"); mouth = H("3A2A22")

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 18, 0], "root")
    bd = m.cube("body", [-4, 14, -8], [8, 8, 16], skin, col2=skin2, belly=belly, pattern="scales")
    for j in (2, 5, 8, 11, 14):
        m.hline(bd["top"], j, stripe, i0=2, i1=6)
        for s in ("left", "right"):
            m.px(bd[s], j, 0, stripe); m.px(bd[s], j, 1, stripe)

    m.bone("neck", [0, 20, -7], "body", rot=[25, 0, 0])
    m.cube("neck", [-2, 17, -10], [4, 10, 4], skin, col2=skin2, belly=belly, pattern="scales")
    m.bone("look", [0, 27, -9], "neck")
    m.bone("head", [0, 27, -9], "look")
    hd = m.cube("head", [-2.5, 25, -14], [5, 5, 6], skin, col2=skin2, pattern="scales")
    for s, ii in (("left", 1), ("right", 4)):
        m.px(hd[s], ii, 1, eye); m.px(hd[s], ii, 2, darken(eye, 0.5))
    sn = m.cube("head", [-2, 25, -19], [4, 4, 5], skin, col2=belly, pattern="scales")
    for s in ("left", "right"):
        m.hline(sn[s], 2, mouth)
    m.px(sn["front"], 0, 0, mouth); m.px(sn["front"], 3, 0, mouth)
    m.cube("head", [-0.5, 30, -13], [1, 2, 5], stripe, col2=skin2)

    for name, sgn in (("arm_l", 1), ("arm_r", -1)):
        ox = 4 if sgn > 0 else -6
        m.bone(name, [ox + 1, 16, -6], "body", rot=[-20, 0, 0])
        am = m.cube(name, [ox, 11, -7], [2, 5, 2], skin, col2=skin2)
        for s in SIDES:
            m.hline(am[s], 4, claw)

    for name, sgn in (("leg_l", 1), ("leg_r", -1)):
        ox = 3 if sgn > 0 else -6
        m.bone(name, [ox + 1.5, 15, 2], "body")
        m.cube(name, [ox, 8, -1], [3, 8, 5], skin, col2=skin2, pattern="scales")
        m.cube(name, [ox + 0.5, 2, 1], [2, 7, 2], skin2, col2=darken(skin2, 0.2))
        ft = m.cube(name, [ox, 0, -3], [3, 2, 6], skin2, col2=darken(skin2, 0.25))
        for i in range(3):
            m.px(ft["front"], i, 1, claw)

    m.bone("tail", [0, 19, 8], "body")
    tl = m.cube("tail", [-2.5, 15, 8], [5, 5, 8], skin, col2=skin2, belly=belly, pattern="scales")
    for j in (2, 5):
        m.hline(tl["top"], j, stripe, i0=1, i1=4)
    m.bone("tail_tip", [0, 18, 16], "tail")
    m.cube("tail_tip", [-1.5, 16, 16], [3, 3, 9], skin2, col2=darken(skin2, 0.2), belly=belly, pattern="scales")

    saddle(m, "body", 22, 4.5, z0=-5, depth=8, belly_y=13.5)
    m.save((4, 3, 1.0))

    L = 3.0
    idle = anim(L, {
        "neck": rot(L, 8, fx=S(L, 3)), "head": rot(L, 8, fx=S(L, 3, phase=1.0), fy=S(L, 5, 0.5)),
        "tail": rot(L, 12, fy=S(L, 8)), "tail_tip": rot(L, 12, fy=S(L, 10, phase=-0.8)),
        "arm_l": rot(L, 8, fx=S(L, 4, base=-20)), "arm_r": rot(L, 8, fx=S(L, -4, base=-20)),
        "body": {"position": sampled(L, 8, lambda t: [0, 0.25 * math.sin(TAU * t / L), 0])},
    })
    W = 0.9
    walk = anim(W, {
        "leg_l": rot(W, 8, fx=S(W, 30)), "leg_r": rot(W, 8, fx=S(W, -30)),
        "tail": rot(W, 8, fy=S(W, 7)), "tail_tip": rot(W, 8, fy=S(W, 9, phase=-0.8)),
        "neck": rot(W, 8, fx=S(W, 4, 2.0)),
        "body": {"position": sampled(W, 8, lambda t: [0, 0.5 * abs(math.sin(TAU * t / W)), 0]),
                 "rotation": sampled(W, 8, lambda t: [0, 0, 2.5 * math.sin(TAU * t / W)])},
    })
    R = 0.45
    run = anim(R, {
        "leg_l": rot(R, 8, fx=S(R, 55)), "leg_r": rot(R, 8, fx=S(R, -55)),
        "tail": rot(R, 8, fx=lambda t: -12, fy=S(R, 6)), "tail_tip": rot(R, 8, fx=lambda t: -8, fy=S(R, 8, phase=-0.8)),
        "neck": rot(R, 8, fx=S(R, 3, 2.0, base=22)), "head": const_rot([-18, 0, 0]),
        "arm_l": const_rot([20, 0, 0]), "arm_r": const_rot([20, 0, 0]),
        "body": {"position": sampled(R, 8, lambda t: [0, 0.9 * abs(math.sin(TAU * t / R)), 0]),
                 "rotation": sampled(R, 8, lambda t: [8, 0, 3 * math.sin(TAU * t / R)])},
    })
    T = 3.0
    sit = anim(T, {
        "root": {"position": keys((0, [0, -9, 0]))},
        "leg_l": const_rot([-80, 0, 0]), "leg_r": const_rot([-80, 0, 0]),
        "neck": rot(T, 8, fx=S(T, 2, base=-10)), "tail": rot(T, 8, fy=S(T, 4, base=25)),
        "tail_tip": const_rot([0, 30, 0]),
    })
    save_anims("land_drake", {"idle": idle, "walk": walk, "run": run, "sit": sit})


# =====================================================================================
#  Itens
# =====================================================================================
REINS_ROWS = [
    "................",
    "....oooooo......",
    "...oLLLLLLo.....",
    "..oLlo..olLo....",
    "..oLo....oLo....",
    "..oLo....oLo....",
    "..olLo..oLlo....",
    "...olLooLlo.....",
    "....oRRRRo......",
    "...oRGGGGRo.....",
    "...oRGgGGRo.....",
    "...oRGGGGRo.....",
    "....oRRRRo......",
    ".....oLLo.......",
    "....oLllLo......",
    ".....oooo.......",
]


def reins():
    gems = {"land_drake": ("8CC84B", "D9F2A0"), "nightmare": ("FF5A1A", "FFD23D"), "aurora_stag": ("5FE8D0", "E8FFF8")}
    for ident, (g, hl) in gems.items():
        pal = {"o": H("1E1A24"), "L": H("7A4524"), "l": H("54301A"), "R": H("C9CCD6"), "G": H(g), "g": H(hl)}
        c = Canvas(16, 16)
        c.from_rows(REINS_ROWS, pal)
        c.save(os.path.join(A, "textures/item", ident + "_reins.png"))
        item_model(A, ident + "_reins")
        save_json(os.path.join(A, "models/item", ident + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  Dados
# =====================================================================================
def data():
    loot = {
        "land_drake": [item_entry("minecraft:leather", 1, 3, 1), item_entry("minecraft:beef", 1, 2, 1)],
        "nightmare": [item_entry("minecraft:blaze_powder", 1, 2, 1)],
        "aurora_stag": [item_entry("minecraft:leather", 0, 2, 1)],
    }
    for c, pools in loot.items():
        save_json(os.path.join(D, "loot_table/entities", c + ".json"), {
            "type": "minecraft:entity", "pools": pools, "random_sequence": f"{NS}:entities/{c}"})

    bm = os.path.join(D, "neoforge/biome_modifier")
    for fname, biomes, w, mn, mx in (
            ("mounts_land_drake_savanna", "#minecraft:is_savanna", 6, 1, 2),
            ("mounts_land_drake_plains", ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"], 2, 1, 2)):
        save_json(os.path.join(bm, fname + ".json"), {
            "type": "neoforge:add_spawns", "biomes": biomes,
            "spawners": [{"type": f"{NS}:land_drake", "weight": w, "minCount": mn, "maxCount": mx}]})

    # Loja da Guilda: a primeira montaria do jogador se compra aqui (rank E), junto com sela e apito
    save_json(os.path.join(D, "guild_shop/mounts.json"), {"offers": [
        {"rank": "F", "price": 8, "item": {"id": f"{NS}:mount_whistle", "count": 1}},
        {"rank": "F", "price": 12, "item": {"id": "minecraft:saddle", "count": 1}},
        {"rank": "E", "price": 45, "item": {"id": f"{NS}:land_drake_reins", "count": 1}},
    ]})

    # Capitulo do Diario: "Montarias e Veiculos". A historia e uma trilha so (um capitulo trava o seguinte), entao
    # so UM passo e obrigatorio e ele aceita qualquer jeito de ja ter montado/pilotado algo (inclusive cavalo vanilla
    # e as domas de mundos antigos): o capitulo ensina sem prender ninguem.
    save_json(os.path.join(D, "journal/mounts.json"), {"chapters": [{
        "id": "mounts", "order": 24, "color": "#E8C25A", "intro": True,
        "icon": ["minecraft:saddle", f"{NS}:land_drake_reins", f"{NS}:arcane_broom"],
        "rewards": {"xp": 100, "items": [{"id": f"{NS}:mount_whistle", "count": 1}]},
        "steps": [
            {"id": "ride", "xp": 150, "icon": f"{NS}:land_drake_reins",
             "condition": {"type": "any", "conditions": [
                 {"type": "counter", "key": "feat.mount_ride", "min": 1},
                 {"type": "counter_prefix", "prefix": "feat.tame_", "min": 1},
                 {"type": "counter_prefix", "prefix": "feat.mount_own.", "min": 1},
                 {"type": "counter", "key": "feat.vehicle_ride", "min": 1}]}},
            {"id": "ability", "condition": {"type": "counter", "key": "feat.mount_ability", "min": 1}, "xp": 100,
             "icon": "minecraft:golden_boots", "optional": True},
            {"id": "vehicle", "condition": {"type": "counter", "key": "feat.vehicle_ride", "min": 1}, "xp": 200,
             "icon": f"{NS}:flying_carpet", "optional": True},
            {"id": "tame", "condition": {"type": "counter_prefix", "prefix": "feat.tame_", "min": 1}, "xp": 300,
             "icon": "minecraft:cod", "optional": True},
            {"id": "stag", "condition": {"type": "flag", "flag": "mounts.got.aurora_stag"}, "xp": 300,
             "icon": f"{NS}:aurora_stag_reins", "optional": True},
            {"id": "nightmare", "condition": {"type": "flag", "flag": "mounts.got.nightmare"}, "xp": 300,
             "icon": f"{NS}:nightmare_reins", "optional": True},
            {"id": "fleet", "condition": {"type": "counter_prefix", "prefix": "feat.vehicle.", "min": 3, "distinct": True},
             "xp": 300, "icon": f"{NS}:mini_airship", "optional": True},
        ]}]})

    J = f"journal.{NS}.mounts"
    lang = {
        # --- criaturas e itens ---
        f"entity.{NS}.land_drake": ("Dragão Terrestre", "Land Drake"),
        f"entity.{NS}.nightmare": ("Pesadelo", "Nightmare"),
        f"entity.{NS}.aurora_stag": ("Cervo da Aurora", "Aurora Stag"),
        f"item.{NS}.land_drake_spawn_egg": ("Ovo Gerador de Dragão Terrestre", "Land Drake Spawn Egg"),
        f"item.{NS}.nightmare_spawn_egg": ("Ovo Gerador de Pesadelo", "Nightmare Spawn Egg"),
        f"item.{NS}.aurora_stag_spawn_egg": ("Ovo Gerador de Cervo da Aurora", "Aurora Stag Spawn Egg"),
        f"item.{NS}.land_drake_reins": ("Rédeas do Dragão Terrestre", "Land Drake Reins"),
        f"item.{NS}.land_drake_reins.desc": ("Corredor manso e resistente, o \"cavalo\" dos reinos. Vendidas na loja da Guilda.",
                                             "A tame, sturdy runner: the \"horse\" of the kingdoms. Sold at the Guild shop."),
        f"item.{NS}.nightmare_reins": ("Rédeas do Pesadelo", "Nightmare Reins"),
        f"item.{NS}.nightmare_reins.desc": ("Corcel de crina em chamas, imune a fogo. Só os chefes deixam cair.",
                                            "A steed with a burning mane, immune to fire. Only bosses drop these."),
        f"item.{NS}.aurora_stag_reins": ("Rédeas do Cervo da Aurora", "Aurora Stag Reins"),
        f"item.{NS}.aurora_stag_reins.desc": ("Cervo das montanhas que salta alto e nunca se machuca ao cair. Presente do Reino aos Honrados.",
                                              "A mountain stag that leaps high and never takes fall damage. The Kingdom's gift to the Honored."),
        f"item.{NS}.mount_reins.use": ("Use para chamar a montaria, já domada e selada. Se ela cair em batalha, as Rédeas voltam.",
                                       "Use to call the mount, already tamed and saddled. If it falls in battle, the Reins come back."),
        f"item.{NS}.mount_whistle.desc": ("Chama as suas montarias num raio de 128 blocos.",
                                          "Calls your mounts within 128 blocks."),
        # --- habilidades ---
        f"ability.{NS}.drake_sprint": ("Arrancada", "Sprint"),
        f"ability.{NS}.nightmare_gallop": ("Galope Infernal", "Infernal Gallop"),
        f"ability.{NS}.stag_leap": ("Salto Boreal", "Boreal Leap"),
        # --- mensagens ---
        f"message.{NS}.ground_mount_controls": ("[%1$s] %2$s · [%3$s] desmontar", "[%1$s] %2$s · [%3$s] dismount"),
        f"message.{NS}.mount_reins_used": ("%s chegou! Clique com a mão vazia para montar.",
                                          "%s has arrived! Use an empty hand to ride."),
        f"message.{NS}.mount_reins_back": ("%s caiu em batalha. As Rédeas voltaram para você.",
                                          "%s fell in battle. The Reins came back to you."),
        f"message.{NS}.mount_gift_stag": ("O Reino honra você com um presente: %s!",
                                         "The Kingdom honors you with a gift: %s!"),
        f"message.{NS}.mount_drop_nightmare": ("Das cinzas do chefe surge um prêmio raro: %s!",
                                              "From the boss's ashes comes a rare prize: %s!"),
        # --- dicas de primeira vez ---
        f"hint.{NS}.mounts.city": ("Quer uma montaria? A loja da Guilda vende Rédeas. Veja \"Montarias e Veículos\" no Diário (%s).",
                                   "Want a mount? The Guild shop sells Reins. See \"Mounts and Vehicles\" in the Journal (%s)."),
        f"hint.{NS}.mounts.wild": ("%s selvagem! %s", "A wild %s! %s"),
        f"hint.{NS}.mounts.reins": ("Use as Rédeas para chamar a montaria; depois clique nela com a mão vazia.",
                                    "Use the Reins to call the mount; then click it with an empty hand."),
        f"hint.{NS}.mounts.tame.griffin": ("Ofereça peixe ou carne crua para domar; depois coloque uma Sela.",
                                           "Offer raw fish or raw meat to tame it; then put a Saddle on it."),
        f"hint.{NS}.mounts.tame.sky_ray": ("Ofereça uma Essência (a de Vento funciona melhor) para domar; depois coloque uma Sela.",
                                           "Offer an Essence (Wind works best) to tame it; then put a Saddle on it."),
        f"hint.{NS}.mounts.tame.pegasus": ("Dê Maçãs Douradas até ele confiar em você e monte; depois coloque uma Sela.",
                                           "Feed it Golden Apples until it trusts you, then mount it and add a Saddle."),
        f"hint.{NS}.mounts.tame.wyvern": ("Acalme com carne podre e então ofereça uma Essência Sombria; depois coloque uma Sela.",
                                          "Calm it with rotten flesh, then offer a Shadow Essence; then put a Saddle on it."),
        f"hint.{NS}.mounts.tame.land_drake": ("Ofereça carne para domar; depois coloque uma Sela.",
                                              "Offer meat to tame it; then put a Saddle on it."),
        f"hint.{NS}.mounts.tame.nightmare": ("O Pesadelo não se doma: as Rédeas caem de chefes.",
                                             "The Nightmare cannot be tamed: its Reins drop from bosses."),
        f"hint.{NS}.mounts.tame.aurora_stag": ("O Cervo da Aurora não se doma: é presente do Reino aos Honrados.",
                                               "The Aurora Stag cannot be tamed: it is the Kingdom's gift to the Honored."),
        # --- bestiário ---
        f"bestiary.{NS}.land_drake.desc": ("O \"cavalo\" dos reinos: lagarto corredor manso, puxa carroças e leva aventureiros. Vive solto nas savanas; aceita carne.",
                                           "The \"horse\" of the kingdoms: a tame running lizard that pulls carts and carries adventurers. Roams the savannas; likes meat."),
        f"bestiary.{NS}.nightmare.desc": ("Corcel negro de crina em chamas. Não nasce no mundo: surge das cinzas de chefes derrotados para quem provou o seu valor.",
                                          "A black steed with a burning mane. It is not born in the world: it rises from the ashes of defeated bosses for the worthy."),
        f"bestiary.{NS}.aurora_stag.desc": ("Cervo branco de galhada luminosa, criado nos jardins reais. O Reino só o entrega a quem é Honrado.",
                                            "A white stag with glowing antlers, raised in the royal gardens. The Kingdom only grants it to the Honored."),
        # --- Diário ---
        f"{J}.title": ("Montarias e Veículos", "Mounts and Vehicles"),
        f"{J}.intro": ("Andar a pé cansa, viajante. Este mundo tem montarias para quem conquista a confiança delas — e veículos mágicos para quem sabe construir.",
                       "Walking gets tiring, traveler. This world has mounts for those who earn their trust, and magic vehicles for those who can build."),
        f"{J}.lore": ("Rédeas chamam uma montaria já domada e selada. A tecla da habilidade da montaria (padrão R) só vale montado.",
                      "Reins call a mount that is already tamed and saddled. The mount ability key (default R) only works while riding."),
        f"{J}.outro": ("Do chão ao céu, o mundo ficou menor. Para onde vamos agora?",
                       "From the ground to the sky, the world just got smaller. Where to now?"),
        f"{J}.ride": ("Monte na sua primeira montaria", "Ride your first mount"),
        f"{J}.ride.hint": ("O jeito mais fácil: compre as Rédeas do Dragão Terrestre na loja da Guilda (rank E), use o item e clique na montaria com a mão vazia. Também dá para domar um Dragão Terrestre selvagem (savanas e planícies) com carne e pôr uma Sela (a Guilda vende). Qualquer animal de sela conta, até um cavalo.",
                           "The easy way: buy the Land Drake Reins at the Guild shop (rank E), use the item and click the mount with an empty hand. You can also tame a wild Land Drake (savannas and plains) with meat and add a Saddle (the Guild sells them). Any saddled animal counts, even a horse."),
        f"{J}.ride.lore": ("Agachar desmonta. Agachado + clique manda esperar ou seguir. O Apito de Montaria chama as suas de longe.",
                           "Sneak to dismount. Sneak + click tells it to wait or follow. The Mount Whistle calls yours from afar."),
        f"{J}.ride.tip": ("Guilda", "Guild"),
        f"{J}.ability": ("Use a habilidade da montaria", "Use your mount's ability"),
        f"{J}.ability.hint": ("Montado, aperte a tecla \"Habilidade da montaria\" (padrão R). Cada montaria tem a sua: corrida, salto, investida...",
                              "While riding, press the \"Mount ability\" key (default R). Each mount has its own: sprint, leap, dive..."),
        f"{J}.ability.lore": ("O painel ao lado da barra de itens mostra a habilidade e a recarga.",
                              "The panel next to the hotbar shows the ability and its cooldown."),
        f"{J}.ability.tip": ("Opcional", "Optional"),
        f"{J}.vehicle": ("Pilote um veículo mágico", "Pilot a magic vehicle"),
        f"{J}.vehicle.hint": ("Feitos na bancada com Cristal de Mana e Essências: Vassoura Arcana (voo rápido), Tapete Voador (plana baixo), Barco Arcano (água), Carroça-Golem (terra, com baú) e Dirigível Arcano (voo em grupo). Coloque no chão e clique para subir.",
                              "Crafted with Mana Crystals and Essences: Arcane Broom (fast flight), Flying Carpet (low hover), Arcane Boat (water), Golem Cart (land, with a chest) and Arcane Airship (group flight). Place it and click to board."),
        f"{J}.vehicle.lore": ("A energia é a mana do piloto (sempre sobra 25%% para lutar); sem mana sobrando, o veículo usa a carga de Cristais de Mana (clique nele com um cristal). Agachado + bater guarda o veículo.",
                              "It runs on the pilot's mana (25%% is always kept for fighting); with none to spare, it uses its Mana Crystal charge (click it with a crystal). Sneak + hit to pack it up."),
        f"{J}.vehicle.tip": ("Opcional", "Optional"),
        f"{J}.tame": ("Dome uma montaria voadora", "Tame a flying mount"),
        f"{J}.tame.hint": ("Grifo (montanhas): peixe ou carne crua. Arraia do Céu (Plano Elemental): Essência. Pégaso (Santuário Celestial): Maçãs Douradas. Wyvern (Abismo): carne podre e depois Essência Sombria. Clique na selvagem com a mão vazia para ela dizer o que quer.",
                           "Griffin (mountains): raw fish or meat. Sky Ray (Elemental Plane): an Essence. Pegasus (Celestial Sanctuary): Golden Apples. Wyvern (Abyss): rotten flesh, then a Shadow Essence. Click a wild one with an empty hand to learn what it wants."),
        f"{J}.tame.lore": ("Depois de domar, coloque uma Sela. No ar: pulo sobe, agachar desce.",
                           "After taming, add a Saddle. In the air: jump climbs, sneak descends."),
        f"{J}.tame.tip": ("Opcional", "Optional"),
        f"{J}.stag": ("Ganhe o Cervo da Aurora", "Earn the Aurora Stag"),
        f"{J}.stag.hint": ("Chegue ao patamar Honrado na reputação com o Reino: as Rédeas são entregues na hora.",
                           "Reach Honored reputation with the Kingdom: the Reins are delivered on the spot."),
        f"{J}.stag.lore": ("Salta alto e nunca se machuca ao cair: a montaria das montanhas.",
                           "It leaps high and never takes fall damage: the mountain mount."),
        f"{J}.stag.tip": ("Opcional", "Optional"),
        f"{J}.nightmare": ("Conquiste o Pesadelo", "Claim the Nightmare"),
        f"{J}.nightmare.hint": ("Cada chefe derrotado tem uma chance de deixar as Rédeas do Pesadelo. No sexto chefe elas vêm com certeza.",
                                "Every boss you defeat has a chance to drop the Nightmare Reins. On the sixth boss they are guaranteed."),
        f"{J}.nightmare.lore": ("Imune a fogo, protege o cavaleiro das chamas e queima os inimigos no Galope Infernal.",
                                "Immune to fire, it shields its rider from flames and burns enemies with the Infernal Gallop."),
        f"{J}.nightmare.tip": ("Opcional", "Optional"),
        f"{J}.fleet": ("Pilote 3 veículos diferentes", "Pilot 3 different vehicles"),
        f"{J}.fleet.hint": ("Cada veículo é bom para um terreno: experimente no céu, na água e em terra.",
                            "Each vehicle is good for one terrain: try the sky, the water and the land."),
        f"{J}.fleet.lore": ("\"Quem tem frota não tem fronteira.\"", "\"Those with a fleet have no borders.\""),
        f"{J}.fleet.tip": ("Opcional", "Optional"),
    }
    merge_lang(LANG, lang)


if __name__ == "__main__":
    nightmare()
    aurora_stag()
    land_drake()
    reins()
    data()
    print("mounts_v1: assets gerados")
