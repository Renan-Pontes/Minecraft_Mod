"""
Onda 10 (vehicles_mechanic) -- arte nova dos veiculos e do Mecanico. Rode da raiz do projeto:
    py -I tools/gen/vehicles_w10.py

Gera SO arquivos novos (nada dos geradores antigos e reescrito):
  - modelos GeckoLib (.geo.json), animacoes, textura + glowmask dos 3 veiculos novos:
    mana_rover (Triciclo de Mana), tide_skiff (Lancha das Mares), sky_galleon (Galeao Celeste);
  - icones e modelos de item: os 3 veiculos, as 3 pecas de melhoria (vehicle_part_*) e o Kit de Reparos;
  - roupa do Mecanico nas duas familias de NPC:
    textures/entity/settlement_npc/trader_mechanic_<0|1>.png (cidade do jogador, modelo settlement_npc) e
    textures/entity/city_npc/merchant_mechanic_<0|1>.png (cidades do mundo, modelo city_npc).
Textos: tools/gen/vehicles_w10_lang.py. Convencao dos modelos igual a vehicles.py: o modelo olha para -Z, +X e a
ESQUERDA, 16 px = 1 bloco; assento z=+0.35 (VehicleKind) = modelo z=-5.6.
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
from mounts import Model, H, SIDES, save_anims, anim, rot, keys, sampled, S, A, TAU  # noqa: E402
from pixelart import Canvas, darken, lighten, item_model  # noqa: E402

CRY = H("7FF5E6")
CRY2 = H("D6FFF8")
GOLD = H("E8C25A")


def bob(L, amp, steps=8):
    return {"position": sampled(L, steps, lambda t: [0, amp * math.sin(TAU * t / L), 0])}


def spin_x(T, turns=1):
    return {"rotation": keys((0, [0, 0, 0]), (T / 2, [180 * turns, 0, 0]), (T, [360 * turns, 0, 0]))}


# =====================================================================================
#  1. TRICICLO DE MANA (terra, rapido, 2 lugares, bagageiro)
# =====================================================================================
def rover():
    m = Model("mana_rover", 128, 128)
    cop = H("C9733A"); cop2 = H("8E4A22"); iron = H("4A4E58"); iron2 = H("2E3138"); leather = H("6B3A22")
    tire = H("2A2420"); tire2 = H("181412")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 6, 0], "root")
    # chassi de cobre com runas
    ch = m.cube("body", [-5, 4, -12], [10, 3, 24], cop, col2=cop2)
    for s in ("left", "right"):
        for i in range(2, 24, 5):
            m.px(ch[s], i, 1, CRY, True)
    m.hline(ch["top"], 0, GOLD)
    # banco do piloto e do garupa (couro)
    m.cube("body", [-3, 7, -4], [6, 3, 6], leather, col2=darken(leather, 0.25))
    m.cube("body", [-3, 9, 7], [6, 3, 6], leather, col2=darken(leather, 0.25))
    m.cube("body", [-3, 12, 12], [6, 4, 1], leather, col2=darken(leather, 0.25))
    # motor de cristal atras do piloto
    m.cube("body", [-2.5, 7, 1], [5, 3, 5], iron, col2=iron2)
    m.bone("core", [0, 11.5, 3.5], "body")
    m.cube("core", [-1.5, 10, 2], [3, 3, 3], CRY, col2=CRY2, glow=True)
    # garfo e guidao na frente
    m.cube("body", [-1, 6, -15], [2, 8, 2], iron, col2=iron2)
    m.cube("body", [-5, 13, -15], [10, 1, 1], iron, col2=iron2)
    m.cube("body", [-6, 12.5, -15.5], [2, 2, 2], leather)
    m.cube("body", [4, 12.5, -15.5], [2, 2, 2], leather)
    # farol
    m.cube("body", [-1.5, 10, -17], [3, 2, 2], GOLD, col2=darken(GOLD, 0.3))
    m.cube("body", [-1, 10.5, -17.6], [2, 1, 1], CRY2, glow=True)
    # bagageiro atras
    bg = m.cube("body", [-5, 6, 12], [10, 4, 5], H("8A6238"), col2=H("5E4022"))
    for s in SIDES:
        m.hline(bg[s], 1, iron)
    # rodas (giram no "move")
    for name, x, z, wdt in (("wheel_f", 0, -14, 2), ("wheel_bl", 6.5, 9, 3), ("wheel_br", -6.5, 9, 3)):
        m.bone(name, [x, 3.5, z], "body")
        wh = m.cube(name, [x - wdt / 2, 0, z - 3.5], [wdt, 7, 7], tire, col2=tire2)
        for s in ("left", "right"):
            m.px(wh[s], 3, 3, GOLD); m.px(wh[s], 3, 2, iron); m.px(wh[s], 3, 4, iron)
            m.px(wh[s], 2, 3, iron); m.px(wh[s], 4, 3, iron)
    m.save((3, 2, 1.0))

    L = 3.0
    idle = anim(L, {"body": bob(L, 0.15), "core": rot(L, 8, fy=S(L, 25))})
    F = 0.6
    wheels = spin_x(F, 1)
    move = anim(F, {"body": {**bob(F, 0.25), **rot(F, 8, fz=S(F, 1.0), fx=S(F, 0.6, base=-1.5))},
                    "core": rot(F, 8, fy=S(F, 60)), "wheel_f": wheels, "wheel_bl": wheels, "wheel_br": wheels})
    save_anims("mana_rover", {"idle": idle, "move": move})


# =====================================================================================
#  2. LANCHA DAS MARES (agua, 4 lugares, porao, vela pequena)
# =====================================================================================
def skiff():
    m = Model("tide_skiff", 256, 128)
    hull = H("2F7A86"); hull2 = H("1E4F5A"); white = H("EDEDE4"); white2 = H("C9C9BE"); deck = H("A8845A"); deck2 = H("7A5E3A")
    sail = H("F2EEDC"); sail2 = H("D9D2B8")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 3, 0], "root")
    m.cube("body", [-9, 0, -18], [18, 1, 36], hull2, col2=darken(hull2, 0.2))
    fl = m.cube("body", [-11, 1, -18], [22, 1, 36], deck, col2=deck2)
    for j in range(0, 36, 4):
        m.hline(fl["top"], j, deck2)
    for x in (11, -12):
        sd = m.cube("body", [x, 1, -18], [1, 7, 36], hull, col2=hull2)
        for s in ("left", "right"):
            m.hline(sd[s], 0, white)
            m.hline(sd[s], 1, white2)
            for i in range(4, 36, 7):            # escotilhas de cristal
                m.px(sd[s], i, 4, CRY, True); m.px(sd[s], i + 1, 4, CRY, True)
        m.hline(sd["top"], 0, white)
    st = m.cube("body", [-11, 1, 18], [22, 7, 1], hull, col2=hull2)
    m.hline(st["back"], 0, white)
    # proa afinando
    m.cube("body", [-9, 1, -21], [18, 6, 3], hull, col2=hull2, top=white)
    m.cube("body", [-6, 2, -24], [12, 5, 3], hull, col2=hull2, top=white)
    m.cube("body", [-3, 3, -26], [6, 4, 2], hull, col2=hull2, top=white)
    m.cube("body", [-0.5, 4, -28], [1, 4, 2], GOLD, col2=darken(GOLD, 0.3))
    # para-brisa de vidro na frente do piloto
    m.cube("body", [-5, 6, -16], [10, 4, 1], H("9FE8F0"), col2=H("6FC8D8"))
    # bancos (4 lugares) e a tampa do porao
    m.cube("body", [-4, 2, -13], [8, 3, 4], white, col2=white2)
    m.cube("body", [-10, 2, -2], [6, 3, 4], white, col2=white2)
    m.cube("body", [4, 2, -2], [6, 3, 4], white, col2=white2)
    m.cube("body", [-4, 2, 10], [8, 3, 4], white, col2=white2)
    m.cube("body", [-3, 1.5, 3], [6, 1, 5], deck2, col2=darken(deck2, 0.2))
    # propulsor de cristal na popa
    m.bone("prop", [0, 3, 21], "body")
    m.cube("prop", [-2, 1, 19], [4, 4, 3], CRY, col2=CRY2, glow=True)
    m.cube("prop", [-3, 2, 22], [6, 2, 1], H("4A4E58"))
    # mastro e vela
    m.cube("body", [-0.5, 2, -6], [1, 22, 1], deck2, col2=darken(deck2, 0.2))
    m.bone("sail", [0, 14, -6], "body")
    sl = m.cube("sail", [-0.25, 8, -5], [0.5, 14, 10], sail, col2=sail2)
    for s in ("left", "right"):
        for i in range(10):
            m.px(sl[s], i, 4, H("2F7A86")); m.px(sl[s], i, 5, H("2F7A86"))
    m.save((4, 3, 1.0))

    L = 3.0
    idle = anim(L, {"body": {**bob(L, 0.35), **rot(L, 8, fz=S(L, 2), fx=S(L, 1, phase=1.2))},
                    "sail": rot(L, 8, fy=S(L, 6)), "prop": rot(L, 8, fz=S(L, 10))})
    F = 1.0
    move = anim(F, {"body": {**bob(F, 0.4), **rot(F, 8, fz=S(F, 1.5), fx=S(F, 1.5, base=-5))},
                    "sail": rot(F, 8, fy=S(F, 4, base=-10)),
                    "prop": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, 180]), (1.0, [0, 0, 360]))}})
    save_anims("tide_skiff", {"idle": idle, "move": move})


# =====================================================================================
#  3. GALEAO CELESTE (voo, 5 lugares, porao grande, velas e asas)
# =====================================================================================
def galleon():
    m = Model("sky_galleon", 256, 512)
    wood = H("7A4E2A"); wood2 = H("4E2E14"); trim = GOLD; sail = H("F4EEDC"); sail2 = H("D8CFB4"); blue = H("2B4A8A")
    iron = H("4A4E58")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 6, 0], "root")
    # casco em degraus (quilha de cristal embaixo)
    m.cube("body", [-6, -2, -24], [12, 2, 48], wood2, col2=darken(wood2, 0.2))
    m.cube("body", [-10, 0, -28], [20, 2, 56], wood2, col2=darken(wood2, 0.1))
    fl = m.cube("body", [-14, 2, -30], [28, 1, 60], wood, col2=wood2)
    for j in range(0, 60, 4):
        m.hline(fl["top"], j, wood2)
    m.cube("body", [-1, -4, -20], [2, 2, 40], CRY, col2=CRY2, glow=True)
    for x in (14, -15):
        w = m.cube("body", [x, 3, -30], [1, 6, 60], wood, col2=wood2)
        for s in ("left", "right"):
            m.hline(w[s], 0, trim)
            for i in range(5, 60, 9):
                m.px(w[s], i, 3, CRY, True); m.px(w[s], i + 1, 3, CRY, True)
    bk = m.cube("body", [-14, 3, 30], [28, 8, 1], wood, col2=wood2)
    m.hline(bk["back"], 0, trim)
    m.cube("body", [-14, 3, -31], [28, 6, 1], wood, col2=wood2)
    # proa com figura de proa dourada
    m.cube("body", [-10, 3, -35], [20, 5, 4], wood, col2=wood2, top=trim)
    m.cube("body", [-5, 4, -39], [10, 4, 4], wood, col2=wood2, top=trim)
    m.cube("body", [-1.5, 6, -43], [3, 4, 4], trim, col2=darken(trim, 0.3))
    m.cube("body", [-0.5, 9, -44], [1, 2, 1], CRY, col2=CRY2, glow=True)
    # castelo de popa (banco do piloto na frente; 4 bancos de passageiros)
    m.cube("body", [-14, 3, 22], [28, 3, 8], wood2, col2=darken(wood2, 0.2), top=wood)
    m.cube("body", [-5, 3, -23], [10, 3, 4], wood2, col2=darken(wood2, 0.2), top=H("8E2A35"))
    for x in (-13, 6):
        for z in (-7, 9):
            m.cube("body", [x, 3, z], [7, 3, 4], wood2, col2=darken(wood2, 0.2), top=H("8E2A35"))
    # porao (escotilha) e lanternas
    m.cube("body", [-4, 2.5, 0], [8, 1, 6], wood2, col2=darken(wood2, 0.3))
    for x, z in ((12, -28), (-13, -28), (12, 27), (-13, 27)):
        m.cube("body", [x, 9, z], [1, 2, 1], iron)
        m.cube("body", [x - 0.5, 7, z - 0.5], [2, 2, 2], H("FFD86B"), glow=True)
    # mastros e velas
    for name, z, h in (("sail_f", -14, 26), ("sail_b", 8, 30)):
        m.cube("body", [-1, 3, z], [2, h, 2], wood2, col2=darken(wood2, 0.2))
        m.cube("body", [-12, 3 + h - 4, z], [24, 1, 1], wood2)
        m.bone(name, [0, 3 + h - 4, z], "body")
        sl = m.cube(name, [-11, 3 + h - 18, z + 1.5], [22, 14, 0.5], sail, col2=sail2)
        for f in ("front", "back"):
            x0, y0, fw, fh = sl[f]
            for j in range(fh):
                for i in range(fw):
                    if abs(i - fw / 2 + 0.5) < 2.5 and 3 <= j <= 9:
                        m.px(sl[f], i, j, blue)
            m.hline(sl[f], 0, trim)
    m.cube("body", [-0.5, 36, 7.5], [1, 6, 9], blue, col2=darken(blue, 0.25))
    # asas de cristal (batem devagar)
    for name, x, sgn in (("wing_l", 14, 1), ("wing_r", -14, -1)):
        m.bone(name, [x, 6, -4], "body")
        x0 = x if sgn > 0 else x - 14
        wg = m.cube(name, [x0, 6, -10], [14, 1, 16], CRY, col2=CRY2, glow=True, shade_k=0.6)
        for f in ("top", "bottom"):
            for i in range(0, 14, 3):
                m.vline(wg[f], i, lighten(CRY, 0.3), True)
        m.cube(name, [x0 if sgn > 0 else x0 + 13, 5.5, -10.5], [1, 2, 17], trim)
    m.save((6, 6, 1.4))

    L = 4.0
    idle = anim(L, {"body": {**bob(L, 0.6), **rot(L, 8, fz=S(L, 1.0))},
                    "sail_f": rot(L, 8, fx=S(L, 3)), "sail_b": rot(L, 8, fx=S(L, 3, phase=1.0)),
                    "wing_l": rot(L, 8, fz=S(L, 6)), "wing_r": rot(L, 8, fz=S(L, -6))})
    F = 2.0
    move = anim(F, {"body": {**bob(F, 0.4), **rot(F, 8, fz=S(F, 0.8), fx=S(F, 0.6, base=-2))},
                    "sail_f": rot(F, 8, fx=S(F, 2, base=-8)), "sail_b": rot(F, 8, fx=S(F, 2, phase=1.0, base=-8)),
                    "wing_l": rot(F, 8, fz=S(F, 14)), "wing_r": rot(F, 8, fz=S(F, -14))})
    save_anims("sky_galleon", {"idle": idle, "move": move})


# =====================================================================================
#  Icones dos itens
# =====================================================================================
ICONS = {
    "mana_rover": ([
        "................",
        "................",
        "..oo............",
        ".oIo............",
        ".oIoo...........",
        "..oIoo..oooo....",
        "...oIooLLLLoo...",
        "...oCCCCCCCCCo..",
        "..oCcCCCCCCCCCo.",
        ".oooooooooooooo.",
        ".oTTo......oTTo.",
        "oTGGTo....oTGGTo",
        "oTGGTo....oTGGTo",
        ".oTTo......oTTo.",
        "..oo........oo..",
        "................",
    ], {"C": "C9733A", "c": "7FF5E6", "I": "4A4E58", "L": "6B3A22", "T": "2A2420", "G": "E8C25A"}),
    "tide_skiff": ([
        ".......o........",
        "......oSo.......",
        "......oSSo......",
        "......oSBSo.....",
        "......oSBSSo....",
        "......oSSSSSo...",
        "......oSSSSSSo..",
        "......ooooooooo.",
        ".......M........",
        "oooooooMoooooooo",
        "oWWWWWWWWWWWWWWo",
        "oHHCHHHHCHHHHCHo",
        ".oHHHHHHHHHHHHo.",
        "..oHHHHHHHHHHo..",
        "...oooooooooo...",
        "................",
    ], {"S": "F2EEDC", "B": "2F7A86", "M": "7A5E3A", "W": "EDEDE4", "H": "2F7A86", "C": "7FF5E6"}),
    "sky_galleon": ([
        "....o.....o.....",
        "...oSo...oSo....",
        "..oSBSo.oSBSo...",
        "..oSBSo.oSBSo...",
        "..oSSSo.oSSSo...",
        "...oMo...oMo....",
        "oo..M.....M...oo",
        "oCCooooooooooCCo",
        ".oCWWWWWWWWWWCo.",
        "..oWGWWGWWGWWo..",
        "...oWWWWWWWWo...",
        "....oKKKKKKo....",
        ".....oooooo.....",
        "................",
        "................",
        "................",
    ], {"S": "F4EEDC", "B": "2B4A8A", "M": "4E2E14", "W": "7A4E2A", "G": "E8C25A", "C": "7FF5E6", "K": "D6FFF8"}),
    "vehicle_part_engine": ([
        "................",
        "....oooooooo....",
        "...oIIIIIIIIo...",
        "..oIiIIIIIIiIo..",
        "..oIIoooooIIIo..",
        "..oIIoCCCoIIIo..",
        ".ooIIoCcCoIIIoo.",
        "oGGIIoCCCoIIIGGo",
        "oGGIIoooooIIIGGo",
        ".ooIIIIIIIIIIoo.",
        "..oIiIIIIIIiIo..",
        "...oIIIIIIIIo...",
        "....oooooooo....",
        "......oGGo......",
        "......oooo......",
        "................",
    ], {"I": "6E7782", "i": "A8B2BE", "C": "7FF5E6", "c": "D6FFF8", "G": "E8C25A"}),
    "vehicle_part_armor": ([
        "................",
        "..oooooooooooo..",
        ".oSSSSSSSSSSSSo.",
        ".oSsSSSSSSSSsSo.",
        ".oSSSSGGGSSSSSo.",
        ".oSSSGCCCGSSSSo.",
        ".oSSSGCcCGSSSSo.",
        ".oSSSGCCCGSSSSo.",
        ".oSSSSGGGSSSSSo.",
        "..oSSSSSSSSSSo..",
        "..oSsSSSSSSsSo..",
        "...oSSSSSSSSo...",
        "....oSSSSSSo....",
        ".....oSSSSo.....",
        "......oooo......",
        "................",
    ], {"S": "8E99A8", "s": "D8DEE6", "G": "E8C25A", "C": "7FF5E6", "c": "D6FFF8"}),
    "vehicle_part_storage": ([
        "................",
        "................",
        "..oooooooooooo..",
        ".oWWWWWWWWWWWWo.",
        ".oWwWWWWWWWWwWo.",
        ".oIIIIIIIIIIIIo.",
        ".oWWWWWGGWWWWWo.",
        ".oWWWWWGGWWWWWo.",
        ".oWwWWWWWWWWwWo.",
        ".oIIIIIIIIIIIIo.",
        ".oWWWWWWWWWWWWo.",
        ".oWwWWWWWWWWwWo.",
        "..oooooooooooo..",
        "...oCo....oCo...",
        "....o......o....",
        "................",
    ], {"W": "8A6238", "w": "5E4022", "I": "4A4E58", "G": "E8C25A", "C": "7FF5E6"}),
    "vehicle_repair_kit": ([
        "................",
        "......oooo......",
        ".....o....o.....",
        "..oooooooooooo..",
        ".oRRRRRRRRRRRRo.",
        ".oRrRRRRRRRRrRo.",
        ".oRRRRRWWRRRRRo.",
        ".oRRRRRWWRRRRRo.",
        ".oRRRWWWWWWRRRo.",
        ".oRRRWWWWWWRRRo.",
        ".oRRRRRWWRRRRRo.",
        ".oRRRRRWWRRRRRo.",
        ".oRrRRRRRRRRrRo.",
        "..oooooooooooo..",
        "................",
        "................",
    ], {"R": "B8452E", "r": "E8C25A", "W": "F4F4F4"}),
}


def icons():
    for ident, (rows, pal) in ICONS.items():
        palette = {k: H(v) for k, v in pal.items()}
        palette["o"] = H("1E1A24")
        c = Canvas(16, 16)
        c.from_rows(rows, palette)
        c.save(os.path.join(A, "textures/item", ident + ".png"))
        item_model(A, ident)


# =====================================================================================
#  O Mecanico (roupas)
# =====================================================================================
def mechanic_textures():
    import cities as cz
    import settlements as st
    # cidade do jogador: macacao azul de alcas, bone de aba, avental de couro com bolso e a chave inglesa (martelo)
    g = st.npc_geo()
    out = os.path.join(A, "textures/entity/settlement_npc")
    for v in range(2):
        female = v % 2 == 1
        rnd = random.Random(f"trader/mechanic/{v}")
        skin = rnd.choice(st.SKIN)
        hair = rnd.choice(["5A3A22", "1E1A1A", "C97A3A", "8A6A4A"])
        eye = rnd.choice(["3A6BC9", "4A6B3A", "5A3A22"])
        hstyle = "long" if female else "short"
        shirt = "3A5A8A"
        kit = st.person(skin, hair, hstyle, eye, cz.body(shirt, straps="23344F", belt="2A1E16", buckle="E8C25A",
                                                          emblem="7FF5E6"),
                        shirt, "2E4670", "2A1E16", female=female, stubble=not female)
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=4, glove="3A2A1E")
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=4, glove="3A2A1E")
        kit["apron"] = cz.apron("6B4A2E", pocket="C9733A", stains=True)
        kit["cap_top"] = cz.cap_top("C9733A", band_c="7FF5E6")
        kit["cap_brim"] = cz.solid("8E4A22")
        kit["hammer_handle"] = cz.wood_rod("4A3220")
        kit["hammer_head"] = cz.metal("A8B2BE")
        g.paint(kit, 9300 + v).save(os.path.join(out, f"trader_mechanic_{v}.png"))
    # cidades do mundo: mesmo uniforme, com os oculos de protecao de lente ambar
    g2 = cz.npc_geo()
    out2 = os.path.join(A, "textures/entity/city_npc")
    for v in range(2):
        female = v == 1
        skin = ["C98E64", "F2CFB0"][v]
        hair = ["2A1E16", "C97A3A"][v]
        shirt = "3A5A8A"
        kit = cz.person(skin, hair, "long" if female else "short", ["3A6BC9", "4A6B3A"][v],
                        cz.body(shirt, straps="23344F", belt="2A1E16", buckle="E8C25A", emblem="7FF5E6"),
                        shirt, "2E4670", "2A1E16", female=female, stubble=not female)
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=4, glove="3A2A1E")
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=4, glove="3A2A1E")
        kit["apron"] = cz.apron("6B4A2E", pocket="C9733A", stains=True)
        kit["cap_top"] = cz.cap_top("C9733A", band_c="7FF5E6")
        kit["cap_brim"] = cz.solid("8E4A22")
        kit["leg"] = cz.leg("2E4670", "2A1E16", boot_rows=5)
        g2.paint(kit, 320 + v).save(os.path.join(out2, f"merchant_mechanic_{v}.png"))


if __name__ == "__main__":
    rover()
    skiff()
    galleon()
    icons()
    mechanic_textures()
    print("vehicles_w10: 3 veiculos, 7 icones e 4 texturas do Mecanico")
