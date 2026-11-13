"""
Assets do modulo vehicles (rode da raiz do projeto: py -I tools/gen/vehicles.py):
  - modelos GeckoLib (.geo.json), animacoes, texturas + glowmask dos 5 veiculos magicos
    (arcane_broom, flying_carpet, arcane_boat, golem_cart, mini_airship)
  - icones e modelos dos itens, receitas de bancada e traducoes pt_br / en_us

Reusa o construtor de modelos de tools/gen/mounts.py. Convencao: o modelo olha para -Z e +X e a ESQUERDA;
os assentos de VehicleKind usam o espaco da entidade (z para a frente), entao assento z=+0.35 = modelo z=-5.6.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from mounts import (Model, H, SIDES, save_json, save_anims, anim, rot, const_rot, keys, sampled, S, A, D, NS, TAU)  # noqa: E402
from pixelart import Canvas, darken, lighten, mix, item_model  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/vehicles")

CRY = H("7FF5E6")
CRY2 = H("D6FFF8")
GOLD = H("E8C25A")


def merge_lang(folder, entries):
    for idx, name in enumerate(("pt_br.json", "en_us.json")):
        path = os.path.join(folder, name)
        cur = {}
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                cur = json.load(f)
        for k, v in entries.items():
            cur[k] = v[idx]
        save_json(path, cur)


def bob(L, amp, steps=8):
    return {"position": sampled(L, steps, lambda t: [0, amp * math.sin(TAU * t / L), 0])}


# =====================================================================================
#  1. VASSOURA ARCANA
# =====================================================================================
def broom():
    m = Model("arcane_broom", 64, 64)
    wood = H("7A4E2A"); wood2 = H("5A3418"); straw = H("D9B25A"); straw2 = H("A8802E")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 8, 0], "root")
    st = m.cube("body", [-1, 7, -14], [2, 2, 22], wood, col2=wood2)
    for j in range(2, 22, 5):
        m.hline(st["top"], j, darken(wood, 0.2))
    m.cube("body", [-1.5, 6.5, -15], [3, 3, 2], GOLD, col2=darken(GOLD, 0.3))
    m.cube("body", [-3, 5.5, -3], [6, 1, 1], wood2)
    m.cube("body", [-2, 6, 8], [4, 4, 2], GOLD, col2=darken(GOLD, 0.3))
    m.bone("crystal", [0, 8, -17], "body")
    m.cube("crystal", [-1, 7, -18], [2, 2, 3], CRY, col2=CRY2, glow=True)
    m.cube("crystal", [-0.5, 9, -17], [1, 2, 1], CRY2, glow=True)
    m.bone("bristles", [0, 8, 10], "body")
    m.cube("bristles", [-3, 5, 10], [6, 6, 7], straw, col2=straw2, pattern="hair")
    m.cube("bristles", [-2.5, 5.5, 17], [5, 5, 4], straw2, col2=darken(straw2, 0.15), pattern="hair")
    m.cube("bristles", [-1.5, 6.5, 21], [3, 3, 2], darken(straw2, 0.2), pattern="hair")
    m.save((3, 2, 0.5))

    L = 3.0
    idle = anim(L, {"body": {**bob(L, 0.7), **rot(L, 8, fz=S(L, 2))},
                    "crystal": rot(L, 8, fz=S(L, 8, 2.0))})
    F = 0.5
    move = anim(F, {"body": {**bob(F, 0.25), **rot(F, 8, fx=lambda t: -4)},
                    "bristles": rot(F, 8, fy=S(F, 4), fx=S(F, 3, phase=1.0)),
                    "crystal": rot(F, 8, fz=S(F, 20, 2.0))})
    save_anims("arcane_broom", {"idle": idle, "move": move})


# =====================================================================================
#  2. TAPETE VOADOR
# =====================================================================================
def carpet():
    m = Model("flying_carpet", 128, 64)
    red = H("A8243A"); red2 = H("7A1628"); blue = H("27408A"); under = H("5A1420")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 3, 0], "root")
    m.bone("seg2", [0, 3.5, 0], "body")
    m.bone("seg3", [0, 3.5, 0], "body")
    m.bone("seg1", [0, 3.5, -8], "seg2")
    m.bone("seg4", [0, 3.5, 8], "seg3")

    def paint(i, gz):
        """Cor do tapete visto de cima: i = 0..23 (lado a lado), gz = -16..15 (da frente para tras)."""
        edge = min(i, 23 - i, gz + 16, 15 - gz)
        if edge == 0:
            return GOLD, False
        if edge == 1:
            return red2, False
        if edge == 2:
            return (GOLD if (i + gz) % 2 == 0 else red), False
        dx, dz = abs(i - 11.5), abs(gz + 0.5)
        if dx + dz < 2.5:
            return CRY, True
        if dx + dz < 4.5:
            return GOLD, False
        if dx + dz < 7.5:
            return blue, False
        if (i + gz) % 4 == 0 or (i - gz) % 4 == 0:
            return red2, False
        return red, False

    for name, z0 in (("seg1", -16), ("seg2", -8), ("seg3", 0), ("seg4", 8)):
        f = m.cube(name, [-12, 3, z0], [24, 1, 8], red, shade_k=0.5)
        x, y, fw, fh = f["top"]
        for j in range(fh):
            gz = z0 + (fh - 1 - j)       # j=0 e a linha de TRAS (+Z)
            for i in range(fw):
                col, glow = paint(i, gz)
                m.px(f["top"], i, j, lighten(col, 0.05), glow)
                m.px(f["bottom"], i, j, under if (i + gz) % 3 else darken(under, 0.2))
        for s in SIDES:
            m.hline(f[s], 0, GOLD)
    # Borlas nos quatro cantos
    for name, z in (("seg1", -17), ("seg4", 16)):
        for x in (-12, 11):
            m.cube(name, [x, 1, z], [1, 3, 1], GOLD, col2=darken(GOLD, 0.35))
    m.save((4, 2, 0.3))

    L = 2.4
    idle = anim(L, {"body": bob(L, 0.5),
                    "seg1": rot(L, 8, fx=S(L, 7)), "seg4": rot(L, 8, fx=S(L, 7, phase=2.2)),
                    "seg2": rot(L, 8, fx=S(L, 2, phase=0.8)), "seg3": rot(L, 8, fx=S(L, 2, phase=1.6))})
    F = 0.9
    move = anim(F, {"body": bob(F, 0.3),
                    "seg1": rot(F, 8, fx=S(F, 6, base=-5)), "seg4": rot(F, 8, fx=S(F, 14, phase=2.2, base=4)),
                    "seg2": rot(F, 8, fx=S(F, 2, phase=0.8)), "seg3": rot(F, 8, fx=S(F, 5, phase=1.6))})
    save_anims("flying_carpet", {"idle": idle, "move": move})


# =====================================================================================
#  3. BARCO ARCANO
# =====================================================================================
def boat():
    m = Model("arcane_boat", 128, 128)
    hull = H("3A4E7A"); hull2 = H("27365A"); deck = H("8A6A42"); deck2 = H("6A4E2E")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 3, 0], "root")
    m.cube("body", [-6, 0, -12], [12, 1, 26], hull2, col2=darken(hull2, 0.2))
    fl = m.cube("body", [-7, 1, -12], [14, 1, 26], deck, col2=deck2)
    for j in range(0, 26, 4):
        m.hline(fl["top"], j, deck2)
    for x in (7, -8):
        sd = m.cube("body", [x, 1, -12], [1, 6, 26], hull, col2=hull2)
        for s in ("left", "right"):
            m.hline(sd[s], 0, GOLD)
            for i in range(3, 26, 6):       # runas brilhantes ao longo do casco
                m.px(sd[s], i, 3, CRY, True); m.px(sd[s], i + 1, 2, CRY, True); m.px(sd[s], i + 1, 4, CRY, True)
        m.hline(sd["top"], 0, GOLD); m.vline(sd["top"], 0, GOLD)
    st = m.cube("body", [-7, 1, 14], [14, 6, 1], hull, col2=hull2)
    m.hline(st["back"], 0, GOLD)
    m.cube("body", [-7, 1, -13], [14, 6, 1], hull, col2=hull2)
    m.cube("body", [-5, 1, -16], [10, 5, 3], hull, col2=hull2, top=deck2)
    m.cube("body", [-3, 2, -19], [6, 4, 3], hull, col2=hull2, top=deck2)
    m.cube("body", [-0.5, 3, -21], [1, 7, 2], GOLD, col2=darken(GOLD, 0.3))
    m.cube("body", [-7, 4, -6], [14, 1, 3], deck, col2=deck2)
    m.cube("body", [-7, 4, 9], [14, 1, 3], deck, col2=deck2)
    m.bone("crystal", [0, 11, -20], "body")
    m.cube("crystal", [-1, 10, -21], [2, 2, 2], CRY, col2=CRY2, glow=True)
    m.cube("body", [-1, 2, 15], [2, 5, 1], CRY, col2=CRY2, glow=True)
    m.save((3, 2, 0.5))

    L = 3.2
    idle = anim(L, {"body": {**bob(L, 0.3), **rot(L, 8, fz=S(L, 2), fx=S(L, 1, phase=1.2))},
                    "crystal": rot(L, 8, fy=S(L, 20))})
    F = 1.0
    move = anim(F, {"body": {**bob(F, 0.4), **rot(F, 8, fz=S(F, 1.5), fx=S(F, 1.5, base=-4))},
                    "crystal": rot(F, 8, fy=S(F, 40))})
    save_anims("arcane_boat", {"idle": idle, "move": move})


# =====================================================================================
#  4. CARROCA-GOLEM
# =====================================================================================
def cart():
    m = Model("golem_cart", 128, 128)
    stone = H("8A8E96"); stone2 = H("5E626C"); moss = H("6E8B4A"); wood = H("8A6238"); wood2 = H("5E4022")
    iron = H("4A4E58")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 11, 0], "root")
    ch = m.cube("body", [-9, 9, -12], [18, 4, 24], stone, col2=stone2, pattern="scales")
    for s in SIDES:
        for i in range(1, ch[s][2], 5):
            m.px(ch[s], i, 0, moss); m.px(ch[s], i + 1, 0, moss); m.px(ch[s], i, 1, darken(moss, 0.2))
    # Banco do condutor (na frente) com encosto
    m.cube("body", [-8, 13, -10], [16, 5, 5], wood, col2=wood2)
    m.cube("body", [-8, 18, -5], [16, 4, 1], wood2, col2=darken(wood2, 0.2))
    # Bau de carga (atras)
    bx = m.cube("body", [-7, 13, 0], [14, 9, 10], wood, col2=wood2)
    for s in SIDES:
        m.hline(bx[s], 3, iron)
        m.vline(bx[s], 1, iron); m.vline(bx[s], bx[s][2] - 2, iron)
    m.px(bx["back"], 6, 3, GOLD); m.px(bx["back"], 7, 3, GOLD); m.px(bx["back"], 6, 4, GOLD); m.px(bx["back"], 7, 4, GOLD)
    for x in (8, -9):
        m.cube("body", [x, 13, -2], [1, 3, 13], wood2, col2=darken(wood2, 0.2))
    m.cube("body", [-2, 13.5, 10], [4, 4, 2], CRY, col2=CRY2, glow=True)
    # Cabeca do golem (na frente)
    m.bone("head", [0, 13, -12], "body")
    hd = m.cube("head", [-4, 10, -18], [8, 8, 6], stone, col2=stone2, pattern="scales")
    for i in (1, 2, 5, 6):
        m.px(hd["front"], i, 3, CRY, True)
    m.px(hd["front"], 1, 4, CRY2, True); m.px(hd["front"], 6, 4, CRY2, True)
    m.hline(hd["front"], 2, stone2); m.hline(hd["front"], 6, stone2, i0=2, i1=6)
    m.hline(hd["top"], 0, moss); m.px(hd["top"], 2, 1, moss); m.px(hd["top"], 5, 2, moss)
    m.cube("head", [-5, 16, -17], [10, 2, 4], stone2, col2=darken(stone2, 0.2))
    # Pernas de pedra
    for name, x, z in (("leg_fl", 6, -8), ("leg_fr", -6, -8), ("leg_bl", 6, 8), ("leg_br", -6, 8)):
        m.bone(name, [x, 9, z], "body")
        lg = m.cube(name, [x - 2, 2, z - 2], [4, 7, 4], stone2, col2=darken(stone2, 0.15), pattern="scales")
        for s in SIDES:
            m.px(lg[s], 1, 0, moss)
        m.cube(name, [x - 2.5, 0, z - 2.5], [5, 2, 5], stone, col2=stone2)
    m.save((3, 3, 0.8))

    L = 3.0
    idle = anim(L, {"body": bob(L, 0.2), "head": rot(L, 8, fy=S(L, 8), fx=S(L, 2, 2.0))})
    W = 0.9
    move = anim(W, {
        "leg_fl": rot(W, 8, fx=S(W, 26)), "leg_br": rot(W, 8, fx=S(W, 24, phase=0.3)),
        "leg_fr": rot(W, 8, fx=S(W, -26)), "leg_bl": rot(W, 8, fx=S(W, -24, phase=0.3)),
        "head": rot(W, 8, fx=S(W, 3, 2.0)),
        "body": {"position": sampled(W, 8, lambda t: [0, 0.5 * abs(math.sin(TAU * t / W)), 0]),
                 "rotation": sampled(W, 8, lambda t: [0, 0, 1.5 * math.sin(TAU * t / W)])},
    })
    save_anims("golem_cart", {"idle": idle, "move": move})


# =====================================================================================
#  5. DIRIGIVEL ARCANO
# =====================================================================================
def airship():
    m = Model("mini_airship", 128, 256)
    wood = H("8A6238"); wood2 = H("5E4022"); cloth = H("EFE6D0"); cloth2 = H("CFC2A4"); stripe = H("3FB8A8")
    rope = H("6B4A2A"); iron = H("4A4E58")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 6, 0], "root")
    m.cube("body", [-4, 1, -10], [8, 1, 20], wood2, col2=darken(wood2, 0.2))
    m.cube("body", [-6, 2, -12], [12, 1, 24], wood2, col2=darken(wood2, 0.1))
    fl = m.cube("body", [-8, 3, -14], [16, 1, 28], wood, col2=wood2)
    for j in range(0, 28, 4):
        m.hline(fl["top"], j, wood2)
    for x in (8, -9):
        w = m.cube("body", [x, 4, -14], [1, 5, 28], wood, col2=wood2)
        for s in ("left", "right"):
            m.hline(w[s], 0, GOLD)
            for i in range(4, 28, 8):
                m.px(w[s], i, 2, CRY, True); m.px(w[s], i + 1, 2, CRY, True)
    bk = m.cube("body", [-8, 4, 14], [16, 5, 1], wood, col2=wood2)
    m.hline(bk["back"], 0, GOLD)
    fr = m.cube("body", [-8, 4, -15], [16, 5, 1], wood, col2=wood2)
    m.hline(fr["front"], 0, GOLD)
    m.cube("body", [-5, 4, -18], [10, 4, 3], wood, col2=wood2, top=GOLD)
    m.cube("body", [-2, 5, -20], [4, 3, 2], wood, col2=wood2, top=GOLD)
    m.cube("body", [-0.5, 8, -21], [1, 2, 1], CRY, col2=CRY2, glow=True)
    # Bancos: piloto na frente, dois atras
    m.cube("body", [-5, 4, -13], [10, 3, 4], wood2, col2=darken(wood2, 0.2), top=H("8E2A35"))
    m.cube("body", [-8, 4, 3], [16, 3, 5], wood2, col2=darken(wood2, 0.2), top=H("8E2A35"))
    # Cordas ate o balao
    for x in (7, -8):
        for z in (-12, 11):
            m.cube("body", [x, 9, z], [1, 13, 1], rope, col2=darken(rope, 0.2))
    # Motor de cristal e helice
    m.cube("body", [-1.5, 5, 15], [3, 3, 2], CRY, col2=CRY2, glow=True)
    m.bone("prop", [0, 6.5, 18], "body")
    m.cube("prop", [-5, 6, 17.5], [10, 1, 1], iron, col2=lighten(iron, 0.3))
    m.cube("prop", [-0.5, 1.5, 17.5], [1, 10, 1], iron, col2=lighten(iron, 0.3))
    # Balao
    m.bone("balloon", [0, 28, 0], "body")
    bl = m.cube("balloon", [-8, 22, -16], [16, 12, 32], cloth, col2=cloth2, shade_k=0.8)
    for face in ("left", "right", "top", "bottom"):
        x, y, fw, fh = bl[face]
        along_i = face in ("left", "right")      # nos lados o comprimento (Z) corre em i; em cima/baixo, em j
        n = fw if along_i else fh
        for k in range(n):
            if k % 8 in (3, 4):
                if along_i:
                    m.vline(bl[face], k, stripe if face != "bottom" else darken(stripe, 0.25))
                else:
                    m.hline(bl[face], k, stripe if face != "bottom" else darken(stripe, 0.25))
    for z, d in ((-19, 3), (16, 3)):
        m.cube("balloon", [-6, 24, z], [12, 8, d], cloth2, col2=darken(cloth2, 0.12))
    m.cube("balloon", [-3, 26, -21], [6, 4, 2], stripe, col2=darken(stripe, 0.2))
    m.cube("balloon", [-0.5, 34, 8], [1, 5, 9], stripe, col2=darken(stripe, 0.25))
    m.cube("balloon", [-8, 27.5, 10], [16, 1, 8], stripe, col2=darken(stripe, 0.25))
    m.save((5, 5, 1.2))

    L = 4.0
    spin = lambda T: {"rotation": keys((0, [0, 0, 0]), (T, [0, 0, 360]))}  # noqa: E731
    idle = anim(L, {"body": {**bob(L, 0.5), **rot(L, 8, fz=S(L, 1.0))},
                    "balloon": rot(L, 8, fz=S(L, 1.2, phase=1.0)), "prop": spin(L)})
    F = 2.0
    move = anim(F, {"body": {**bob(F, 0.3), **rot(F, 8, fz=S(F, 0.8))},
                    "balloon": rot(F, 8, fz=S(F, 1.0, phase=1.0)),
                    "prop": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, 360]), (1.0, [0, 0, 720]), (1.5, [0, 0, 1080]),
                                              (2.0, [0, 0, 1440]))}})
    save_anims("mini_airship", {"idle": idle, "move": move})


# =====================================================================================
#  Icones dos itens
# =====================================================================================
ICONS = {
    "arcane_broom": ([
        "..............oC",
        ".............oCc",
        "............oSo.",
        "...........oSo..",
        "..........oSo...",
        ".........oSo....",
        "........oSo.....",
        ".......oSo......",
        "......oGo.......",
        "....ooBGo.......",
        "...oBBBBo.......",
        "..oBbBBbo.......",
        ".oBbBbBo........",
        ".obBbBo.........",
        ".oobbo..........",
        "...oo...........",
    ], {"S": "7A4E2A", "B": "D9B25A", "b": "A8802E", "G": "E8C25A", "C": "7FF5E6", "c": "D6FFF8"}),
    "flying_carpet": ([
        "................",
        "................",
        "................",
        ".G............G.",
        "oooooooooooooooo",
        "oGGGGGGGGGGGGGGo",
        "oGRRRRRRRRRRRRGo",
        "oGRrRRBGGBRRrRGo",
        "oGRRRBGCcGBRRRGo",
        "oGRrRRBGGBRRrRGo",
        "oGRRRRRRRRRRRRGo",
        "oGGGGGGGGGGGGGGo",
        "oooooooooooooooo",
        ".G............G.",
        "................",
        "................",
    ], {"R": "A8243A", "r": "7A1628", "B": "27408A", "G": "E8C25A", "C": "7FF5E6", "c": "D6FFF8"}),
    "arcane_boat": ([
        "................",
        "..oo............",
        ".oCco...........",
        "..oo............",
        "..oG............",
        "..oG............",
        "..oG..........oo",
        "ooGGooooooooooHo",
        "oHHHHHHHHHHHHHHo",
        "oHHCHHHHCHHHHCHo",
        ".oHCCHHHCCHHHCo.",
        ".oHHCHHHHCHHHHo.",
        "..oHHHHHHHHHHo..",
        "...oooooooooo...",
        "................",
        "................",
    ], {"H": "3A4E7A", "G": "E8C25A", "C": "7FF5E6", "c": "D6FFF8"}),
    "golem_cart": ([
        "................",
        "......oooooooo..",
        ".....oWWWWWWWWo.",
        ".....oWIIIIIIWo.",
        ".oooooWWWGGWWWo.",
        "oSSSSoWWWWWWWWo.",
        "oSCSCoooooooooo.",
        "oSSSSSSSSSSSSSSo",
        "oSsSSMSSSsSSMSSo",
        "oooooooooooooooo",
        "..oSo......oSo..",
        "..oSo......oSo..",
        "..oso......oso..",
        ".oSSSo....oSSSo.",
        ".ooooo....ooooo.",
        "................",
    ], {"S": "8A8E96", "s": "5E626C", "M": "6E8B4A", "W": "8A6238", "I": "4A4E58", "G": "E8C25A", "C": "7FF5E6"}),
    "mini_airship": ([
        "....oooooooo....",
        "..ooBBTBBTBBoo..",
        ".oBBBBTBBTBBBBo.",
        "oTBBBBTBBTBBBBBo",
        ".oBBBBTBBTBBBBo.",
        "..ooBBTBBTBBoo..",
        "....oooooooo....",
        "....r......r....",
        "....r......r....",
        "..oooooooooooo..",
        ".CoHHHHHHHHHHoI.",
        "..oHGHHHHHHGHoI.",
        "...oHHHHHHHHo...",
        "....oooooooo....",
        "................",
        "................",
    ], {"B": "EFE6D0", "T": "3FB8A8", "r": "6B4A2A", "H": "8A6238", "G": "E8C25A", "C": "7FF5E6", "I": "4A4E58"}),
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
#  Receitas e traducoes
# =====================================================================================
def data():
    I = lambda i: {"item": i}        # noqa: E731,E741
    T = lambda t: {"tag": t}         # noqa: E731
    cry, wind, water, earth = f"{NS}:mana_crystal", f"{NS}:essence_wind", f"{NS}:essence_water", f"{NS}:essence_earth"
    # Onda 10 (vehicles_mechanic): os veiculos sao VENDIDOS pelo Mecanico; so o Barco Arcano (o mais basico) continua na
    # bancada. As receitas da vassoura, tapete, carroca e dirigivel foram APAGADAS -- nao as recoloque aqui.
    recipes = {
        "arcane_boat": (["P P", "ECE", "PPP"], {"P": T("minecraft:planks"), "E": I(water), "C": I(cry)}),
    }
    _ = (wind, earth)
    for ident, (pattern, key) in recipes.items():
        save_json(os.path.join(D, "recipe", "vehicles_" + ident + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
            "result": {"id": f"{NS}:{ident}", "count": 1}})
        # A receita aparece no livro de receitas assim que o jogador pega o primeiro Cristal de Mana
        rid = f"{NS}:vehicles_{ident}"
        save_json(os.path.join(D, "advancement/recipes", "vehicles_" + ident + ".json"), {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": cry}]}},
                "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": rid}}},
            "requirements": [["has_item", "has_the_recipe"]],
            "rewards": {"recipes": [rid]}})

    names = {
        "arcane_broom": ("Vassoura Arcana", "Arcane Broom",
                         "Voo livre e rápido: vai para onde você olha. 1 lugar; gasta mais energia.",
                         "Fast free flight: it goes where you look. 1 seat; uses more energy."),
        "flying_carpet": ("Tapete Voador", "Flying Carpet",
                          "Plana baixo acompanhando o relevo e cruza água, lava e desertos. Econômico.",
                          "Hovers low following the terrain and crosses water, lava and deserts. Efficient."),
        "arcane_boat": ("Barco Arcano", "Arcane Boat",
                        "Muito rápido em rios e oceanos. Fora d'água quase não anda.",
                        "Very fast on rivers and oceans. Barely moves out of water."),
        "golem_cart": ("Carroça-Golem", "Golem Cart",
                       "Anda por terra subindo degraus e leva um baú de carga (agachado + clique abre).",
                       "Walks over land climbing steps and carries a cargo chest (sneak + click to open)."),
        "mini_airship": ("Dirigível Arcano", "Arcane Airship",
                         "Lento, mas sobe a qualquer altura e leva o grupo. Tem um porão pequeno (agachado + clique).",
                         "Slow, but climbs to any height and carries the party. Has a small hold (sneak + click)."),
    }
    lang = {}
    for ident, (pt, en, dpt, den) in names.items():
        lang[f"entity.{NS}.{ident}"] = (pt, en)
        lang[f"item.{NS}.{ident}"] = (pt, en)
        lang[f"item.{NS}.{ident}.desc"] = (dpt, den)
    lang.update({
        f"item.{NS}.vehicle.seats": ("Lugares: %s", "Seats: %s"),
        f"item.{NS}.vehicle.charge": ("Carga de cristais: %s%%", "Crystal charge: %s%%"),
        f"item.{NS}.vehicle.energy": ("Energia: mana do piloto ou Cristais de Mana", "Energy: the pilot's mana or Mana Crystals"),
        f"message.{NS}.vehicle_controls.fly": ("Olhe para guiar · [%1$s] subir · [%2$s] descer (no chão: desmontar)",
                                               "Look to steer · [%1$s] climb · [%2$s] descend (on the ground: dismount)"),
        f"message.{NS}.vehicle_controls.hover": ("Olhe para guiar · [%1$s] subir · [%2$s] descer (no chão: desmontar)",
                                                 "Look to steer · [%1$s] climb · [%2$s] descend (on the ground: dismount)"),
        f"message.{NS}.vehicle_controls.water": ("Olhe para guiar · [%2$s] desmontar", "Look to steer · [%2$s] dismount"),
        f"message.{NS}.vehicle_controls.ground": ("Olhe para guiar · [%2$s] desmontar", "Look to steer · [%2$s] dismount"),
        f"message.{NS}.vehicle_no_energy": ("Sem energia! Recupere %s ou carregue o veículo com um Cristal de Mana.",
                                            "Out of energy! Recover %s or charge the vehicle with a Mana Crystal."),
        f"message.{NS}.vehicle_tank_full": ("O tanque de cristais já está cheio.", "The crystal tank is already full."),
        f"message.{NS}.vehicle_charged": ("Carga de cristais: %s%%", "Crystal charge: %s%%"),
        f"message.{NS}.vehicle_not_owner": ("Este veículo pertence a outra pessoa.", "This vehicle belongs to someone else."),
        f"message.{NS}.vehicle_collect_hint": ("[%s] + bater guarda o veículo.", "[%s] + hit packs the vehicle up."),
        f"message.{NS}.vehicle_no_room": ("Não há espaço para o veículo aqui.", "There is no room for the vehicle here."),
        f"hud.{NS}.vehicle_src_mana": ("Energia: %s", "Energy: %s"),
        f"hud.{NS}.vehicle_src_tank": ("Energia: cristais", "Energy: crystals"),
        f"hud.{NS}.vehicle_src_free": ("Energia: livre", "Energy: free"),
        f"hud.{NS}.vehicle_src_none": ("Sem energia!", "Out of energy!"),
        f"hud.{NS}.vehicle_tank": ("Cristais: %s%%", "Crystals: %s%%"),
        f"hint.{NS}.vehicles.energy": ("O veículo gasta %s do piloto; se faltar, usa cristais: clique nele com um Cristal de Mana.",
                                       "The vehicle spends the pilot's %s; when short, it burns crystals: click it with a Mana Crystal."),
        f"hint.{NS}.vehicles.placed": ("Clique no veículo para subir. %s + bater guarda de volta no inventário.",
                                       "Click the vehicle to board. %s + hit packs it back into your inventory."),
    })
    merge_lang(LANG, lang)


if __name__ == "__main__":
    broom()
    carpet()
    boat()
    cart()
    airship()
    icons()
    data()
    print("vehicles: assets gerados")
