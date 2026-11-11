"""
Gera os assets do modulo class_gambler (Apostador).
Uso (na raiz do projeto):  py -I tools/gen/class_gambler.py

  - Traje do Apostador em 4 graus (GeckoLib, no padrao do tools/gen/outfits.py, reaproveitando o mesmo montador de atlas):
      geo/item/armor/outfit_gambler[_<grau>].geo.json, textures/item/armor/outfit_gambler[_<grau>].png (+ _glowmask nos graus II-IV),
      textures/models/armor/outfit_gambler[_<grau>]_layer_1/2.png (reserva vanilla), icones 16x16 das 16 pecas + modelos de item.
      Graus: I Crupie (viseira verde, colete, gravata borboleta) · II Mesa Alta (fedora preto, smoking com lapela vermelha)
             III Rei do Cassino (fedora branco, terno branco e ouro, polainas) · IV Senhor da Fortuna (cartola, sobretudo
             longo preto e roxo com naipes que brilham, ombreiras de ficha)
  - Entidades GeckoLib (geo/entity, textures/entity, animations/entity): gambler_dice, roulette_wheel, slot_machine, casino_domain.
  - Itens: gambler_deck (+ _t1.._t4), gambler_card, casino_chip; efeitos (mob_effect): gambler_jackpot, gambler_lucky, gambler_jinxed;
    icone da aba do menu (gui/tab/casino.png).
  - Receitas: carta, baralho e as 4 pecas do traje grau I; tags de armadura no merge (src/main/merge/class_gambler).
Traducoes: tools/gen/class_gambler_lang.py (escreve src/main/lang/class_gambler/{pt_br,en_us}.json).

Convencoes do .geo.json (Bedrock): frente da entidade = -z, lado DIREITO = -x, y para cima.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix, item_model  # noqa: E402
import outfits as O  # noqa: E402  (montador de modelos/atlas dos trajes)

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
T = (0, 0, 0, 0)
fc, OMIT = O.fc, O.OMIT


def H(h, a=255):
    return hex_rgba(h, a)


def pal(*hexes):
    return [H(h) for h in hexes]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ===================================================================================================
#  Cores
# ===================================================================================================
RED = pal("4A0810", "7A1020", "B01E30", "D8303A", "FF6A70")
BLACK = pal("08070A", "121016", "1E1A24", "2C2634", "403848")
GOLD = pal("5E3F0E", "94691E", "C4962E", "EBC45C", "FFEBA6")
FELT = pal("0A3A22", "125A34", "1E7A4A", "2EA064", "5AC88A")
WHITE = pal("8A8478", "B4AE9E", "D8D2C2", "F0ECE0", "FFFFFA")
PURPLE = pal("1C0A2E", "2E1248", "481C6E", "6A2A9A", "9A5AD0")
PINK = pal("5A0A3A", "8A1A5A", "C0307E", "E04A8C", "FF8AC0")
CHROME = pal("3A3C44", "60646E", "9AA0AC", "C8CED8", "F2F6FA")
WOOD = pal("2A160C", "442612", "623A1C", "80522A", "A0703C")
CYAN = pal("0A3A4A", "1A6A8A", "3AC8E8", "C8F4FF")
VISOR = pal("0E4A2A", "1A6A3E", "2E9A5A", "5AC88A", "9AF0BE")


# ===================================================================================================
#  Pintores reaproveitaveis (cartas, dados, naipes)
# ===================================================================================================
SUIT = {
    "spade": ["..#..", ".###.", "#####", "#####", ".#.#.", "..#.."],
    "heart": [".#.#.", "#####", "#####", ".###.", "..#..", "....."],
    "diamond": ["..#..", ".###.", "#####", ".###.", "..#..", "....."],
    "club": [".###.", ".###.", "#####", "#####", "..#..", ".###."],
}
PIPS = {1: [(1, 1)], 2: [(0, 0), (2, 2)], 3: [(0, 0), (1, 1), (2, 2)], 4: [(0, 0), (2, 0), (0, 2), (2, 2)],
        5: [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)], 6: [(0, 0), (2, 0), (0, 1), (2, 1), (0, 2), (2, 2)]}


def suit_at(r, kind, ox, oy, c, glow=False):
    for j, row in enumerate(SUIT[kind]):
        for i, ch in enumerate(row):
            if ch == "#":
                (r.glow if glow else r.set)(ox + i, oy + j, c)


def die_face(n, rim_glow=True):
    """Face de dado (regiao quadrada qualquer): marfim, cantos arredondados, borda dourada que brilha, pintas."""
    def f(r):
        r.fill(WHITE[3])
        for j in range(r.h):
            for i in range(r.w):
                edge = i in (0, r.w - 1) or j in (0, r.h - 1)
                if edge:
                    (r.glow if rim_glow else r.set)(i, j, GOLD[3] if (i + j) % 2 else GOLD[2])
        r.set(0, 0, GOLD[1]); r.set(r.w - 1, 0, GOLD[1]); r.set(0, r.h - 1, GOLD[1]); r.set(r.w - 1, r.h - 1, GOLD[1])
        # sombra leve embaixo
        for i in range(1, r.w - 1):
            r.set(i, r.h - 2, WHITE[2])
        cell = (r.w - 2) / 3.0
        col = RED[3] if n == 1 else BLACK[1]
        size = max(1, int(cell * 0.55))
        for (a, b) in PIPS[n]:
            cx = 1 + int(cell * a + (cell - size) / 2)
            cy = 1 + int(cell * b + (cell - size) / 2)
            for dj in range(size):
                for di in range(size):
                    r.set(cx + di, cy + dj, col)
            r.set(cx, cy, lighten(col, 0.35))
    return f


def card_face(rank, suit, big=False):
    """Frente de carta: papel creme, borda, indice no canto e o naipe grande no meio."""
    red = suit in ("heart", "diamond")
    ink = RED[3] if red else BLACK[1]

    def f(r):
        r.fill(WHITE[4])
        for i in range(r.w):
            r.set(i, 0, WHITE[2]); r.set(i, r.h - 1, WHITE[1])
        for j in range(r.h):
            r.set(0, j, WHITE[2]); r.set(r.w - 1, j, WHITE[1])
        suit_at(r, suit, (r.w - 5) // 2, (r.h - 6) // 2, ink)
        # indice: letra simples em 3x4 no canto superior esquerdo e inferior direito
        glyph = {"A": [".#.", "#.#", "###", "#.#"], "K": ["#.#", "##.", "##.", "#.#"], "Q": [".#.", "#.#", "#.#", ".##"],
                 "J": ["..#", "..#", "#.#", ".#."]}[rank]
        for j, row in enumerate(glyph):
            for i, ch in enumerate(row):
                if ch == "#":
                    r.set(1 + i, 1 + j, ink)
                    r.set(r.w - 2 - i, r.h - 2 - j, ink)
        if big:
            r.glow((r.w - 1) // 2, 1, GOLD[4])
    return f


def card_back(r):
    r.fill(RED[2])
    for j in range(r.h):
        for i in range(r.w):
            if (i + j) % 4 == 0 or (i - j) % 4 == 0:
                r.set(i, j, RED[1])
    for i in range(r.w):
        r.set(i, 0, WHITE[4]); r.set(i, r.h - 1, WHITE[4])
    for j in range(r.h):
        r.set(0, j, WHITE[4]); r.set(r.w - 1, j, WHITE[4])
    cx, cy = r.w // 2, r.h // 2
    for d in range(3):
        r.glow(cx - d, cy, GOLD[3]); r.glow(cx + d, cy, GOLD[3]); r.glow(cx, cy - d, GOLD[3]); r.glow(cx, cy + d, GOLD[3])


def solid(c, noise=0.03):
    def f(r):
        r.fill(c)
        r.noise(noise)
    return f


def glow_solid(c):
    def f(r):
        for j in range(r.h):
            for i in range(r.w):
                r.glow(i, j, c)
    return f


def metal(P):
    return lambda r: O.metal(r, P)


# ===================================================================================================
#  Montagem de entidades GeckoLib (mesmo empacotador de atlas do outfits.py)
# ===================================================================================================
def build_entity(m, mats, size=(64, 64)):
    keys = {}
    for b in m.bones:
        for c in b["cubes"]:
            for f, mat in c["faces"].items():
                if mat is None:
                    continue
                fw, fh = O.face_dims(f, c["size"])
                k = O.region_key(mat, fw, fh)
                if k[0] not in mats:
                    raise KeyError(f"[{m.name}] material desconhecido: {k[0]}")
                keys[k] = (k, k[1], k[2])
    W, Hh = size
    pos = None
    for cand in (size, (128, 64), (128, 128), (256, 128), (256, 256), (512, 256)):
        pos = O.pack(list(keys.values()), *cand)
        if pos is not None:
            W, Hh = cand
            break
    if pos is None:
        raise RuntimeError(f"[{m.name}] atlas nao coube")
    print(f"  {m.name}: {len(keys)} regioes, atlas {W}x{Hh}")
    cv, gl = Canvas(W, Hh), Canvas(W, Hh)
    for k, (x, y) in sorted(pos.items(), key=lambda kv: str(kv[0])):
        seed = sum(ord(ch) * (i + 7) for i, ch in enumerate(f"{m.name}:{k}"))
        mats[k[0]](O.Region(cv, gl, x, y, k[1], k[2], seed))
    bones = []
    for b in m.bones:
        jb = {"name": b["name"]}
        if "parent" in b:
            jb["parent"] = b["parent"]
        jb["pivot"] = [O.r4(v) for v in b["pivot"]]
        if "rotation" in b:
            jb["rotation"] = [O.r4(v) for v in b["rotation"]]
        cubes = []
        for c in b["cubes"]:
            jc = {"origin": [O.r4(v) for v in c["origin"]], "size": [O.r4(v) for v in c["size"]]}
            if c["inflate"]:
                jc["inflate"] = O.r4(c["inflate"])
            if c["rotation"] and any(abs(v) > 1e-6 for v in c["rotation"]):
                jc["pivot"] = [O.r4(v) for v in (c["pivot"] or c["origin"])]
                jc["rotation"] = [O.r4(v) for v in c["rotation"]]
            uv = {}
            for f in O.FACES:
                mat = c["faces"].get(f)
                if mat is None:
                    continue
                fw, fh = O.face_dims(f, c["size"])
                k = O.region_key(mat, fw, fh)
                x, y = pos[k]
                uv[f] = {"uv": [x, y], "uv_size": [k[1], k[2]]}
            if uv:
                jc["uv"] = uv
                cubes.append(jc)
        if cubes:
            jb["cubes"] = cubes
        bones.append(jb)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + m.name, "texture_width": W, "texture_height": Hh,
                        "visible_bounds_width": 8, "visible_bounds_height": 6, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    write_json(os.path.join(A, "geo/entity", m.name + ".geo.json"), geo)
    cv.save(os.path.join(A, "textures/entity", m.name + ".png"))
    gpath = os.path.join(A, "textures/entity", m.name + "_glowmask.png")
    if any(gl.px[y][x][3] > 0 for y in range(Hh) for x in range(W)):
        gl.save(gpath)
    elif os.path.exists(gpath):
        os.remove(gpath)


def anim(name, anims):
    write_json(os.path.join(A, "animations/entity", name + ".animation.json"), {"format_version": "1.8.0", "animations": anims})


# ---------------------------------------------------------------------------------------------- dado
def dice():
    m = O.Model("gambler_dice")
    m.bone("die", [0, 4, 0])
    m.box("die", [-4, 0, -4], [8, 8, 8], fc(u="f1@12x12", d="f6@12x12", n="f2@12x12", s="f5@12x12", e="f3@12x12", w="f4@12x12"))
    for k in range(1, 7):
        m.bone(f"plate{k}", [0, 8, 0])
        m.box(f"plate{k}", [-4, 8.02, -4], [8, 0.01, 8], fc(u=f"f{k}@12x12", d=OMIT, side=OMIT))
    mats = {f"f{k}": die_face(k) for k in range(1, 7)}
    build_entity(m, mats, (64, 32))
    anim("gambler_dice", {"animation.gambler_dice.glow": {"loop": True, "animation_length": 1.0, "bones": {
        f"plate{k}": {"scale": {"0.0": [1, 1, 1], "0.5": [1.06, 1, 1.06], "1.0": [1, 1, 1]}} for k in range(1, 7)}}})


# ---------------------------------------------------------------------------------------------- roleta
WHEEL = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
         20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}


def wheel_top(r):
    """Topo da roda (vista de cima): casas vermelhas/pretas e o zero verde no anel externo, divisorias douradas,
    cone central de madeira. A casa i fica no angulo i*2pi/37 a partir de +x, no sentido do giro positivo do osso."""
    cx, cy = (r.w - 1) / 2.0, (r.h - 1) / 2.0
    R = r.w / 2.0
    n = len(WHEEL)
    for j in range(r.h):
        for i in range(r.w):
            dx, dy = i - cx, j - cy
            d = math.hypot(dx, dy) / R
            if d > 1.0:
                continue
            ang = math.atan2(dy, dx) % (2 * math.pi)
            k = int(round(ang / (2 * math.pi) * n)) % n
            frac = (ang / (2 * math.pi) * n) % 1.0
            if d > 0.93:
                r.set(i, j, GOLD[1] if d > 0.97 else GOLD[2])
            elif d > 0.62:
                num = WHEEL[k]
                c = FELT[3] if num == 0 else RED[3] if num in REDS else BLACK[2]
                if abs(frac - 0.5) > 0.42:
                    c = GOLD[3]
                elif d > 0.86:
                    c = lighten(c, 0.15)
                r.set(i, j, c)
            elif d > 0.56:
                r.glow(i, j, GOLD[3])
            elif d > 0.24:
                c = WOOD[3] if (k % 4) < 2 else WOOD[2]
                r.set(i, j, c)
            else:
                r.glow(i, j, GOLD[4] if d < 0.1 else GOLD[2])


def bowl_top(r):
    """Aro de madeira da roleta (anel), transparente fora e dentro."""
    cx, cy = (r.w - 1) / 2.0, (r.h - 1) / 2.0
    R = r.w / 2.0
    for j in range(r.h):
        for i in range(r.w):
            d = math.hypot(i - cx, j - cy) / R
            if 0.80 < d <= 1.0:
                c = WOOD[2] if d > 0.9 else WOOD[3]
                if 0.80 < d < 0.83:
                    c = GOLD[3]
                r.set(i, j, c)
            elif d <= 0.80:
                r.set(i, j, WOOD[0])


def roulette():
    m = O.Model("roulette_wheel")
    m.bone("base", [0, 0, 0])
    for rot in (None, [0, 45, 0]):
        m.box("base", [-14, 0, -14], [28, 3, 28], fc(u="bowl@64x64", d=OMIT, side="wood@8x2"), rot=rot, pivot=[0, 0, 0])
    m.box("base", [-2, 0, -2], [4, 3, 4], fc(all="gold@2x2"))
    m.bone("wheel", [0, 3, 0], parent="base")
    m.box("wheel", [-12, 3, -12], [24, 0.6, 24], fc(u="wheel@64x64", d=OMIT, side=OMIT))
    m.box("wheel", [-1, 3.6, -1], [2, 3, 2], fc(side="gold@2x3", u="gold@2x2", d=OMIT))
    m.box("wheel", [-4, 6, -0.5], [8, 0.8, 1], fc(all="gold@4x1"))
    m.box("wheel", [-0.5, 6, -4], [1, 0.8, 8], fc(all="gold@1x4"))
    for (x, z) in ((-4.5, 0), (4.5, 0), (0, -4.5), (0, 4.5)):
        m.box("wheel", [x - 0.7, 5.9, z - 0.7], [1.4, 1.4, 1.4], fc(all="knob@2x2"))
    m.box("wheel", [-1.2, 6.6, -1.2], [2.4, 1.6, 2.4], fc(all="knob@2x2"))
    m.bone("ballOrbit", [0, 3, 0], parent="wheel")
    m.box("ballOrbit", [8.7, 3.6, -0.75], [1.5, 1.5, 1.5], fc(all="ball@2x2"))
    # ponteiro fixo no aro (+x) e pezinhos
    m.box("base", [12.5, 3, -0.8], [2.5, 1.2, 1.6], fc(all="gold@2x1"))
    for (x, z) in ((-11, -11), (11, -11), (-11, 11), (11, 11)):
        m.box("base", [x - 1.5, -0.5, z - 1.5], [3, 1, 3], fc(all="wood_dark@2x2"))
    mats = {"bowl": bowl_top, "wheel": wheel_top, "wood": lambda r: O.bark(r, WOOD), "wood_dark": solid(WOOD[0]),
            "gold": metal(GOLD), "knob": glow_solid(GOLD[3]), "ball": glow_solid(WHITE[4])}
    build_entity(m, mats, (128, 128))
    anim("roulette_wheel", {"animation.roulette_wheel.idle": {"loop": True, "animation_length": 2.0, "bones": {
        "base": {"position": {"0.0": [0, 0, 0], "1.0": [0, 0.15, 0], "2.0": [0, 0, 0]}}}}})


# ---------------------------------------------------------------------------------------------- caca-niquel
SYMS = ["cherry", "bell", "bar", "seven"]


def symbol(kind, bg=WHITE[4]):
    """Simbolo do rolo (12x12): cereja, sino, BAR e 7 vermelho; tudo brilha (visor iluminado)."""
    art = {
        "cherry": ["............", ".......gg...", "......g..g..", ".....g....g.", "....g.....g.", "...g......g.",
                   "..rr.....rr.", ".rRrr...rRrr", ".rrrr...rrrr", ".rrrr...rrrr", "..rr.....rr.", "............"],
        "bell": ["............", ".....kk.....", "....yYYy....", "...yYYYYy...", "...yYYYYy...", "...yYYYYy...",
                 "..yYYYYYYy..", "..yYYYYYYy..", ".yyyyyyyyyy.", "......k.....", ".....kk.....", "............"],
        "bar": ["............", "............", "............", ".bbbbbbbbbb.", ".bWbWWbWWWb.", ".bWbWbbWbWb.",
                ".bWWbWWbWWb.", ".bWbWbbWWbb.", ".bbbbbbbbbb.", "............", "............", "............"],
        "seven": ["............", ".RRRRRRRRR..", ".RrrrrrrRR..", "........RR..", ".......RR...", "......RR....",
                  ".....RR.....", "....RR......", "....RR......", "...RRR......", "...RRR......", "............"],
    }[kind]
    colors = {"g": FELT[2], "r": RED[3], "R": RED[2] if kind != "cherry" else RED[4], "y": GOLD[2], "Y": GOLD[3],
              "k": GOLD[1], "b": BLACK[1], "W": WHITE[4]}

    def f(r):
        for j in range(r.h):
            for i in range(r.w):
                r.glow(i, j, bg)
        sx, sy = r.w / 12.0, r.h / 12.0
        for j in range(r.h):
            for i in range(r.w):
                ch = art[min(11, int(j / sy))][min(11, int(i / sx))]
                if ch != ".":
                    r.glow(i, j, colors[ch])
    return f


def cabinet(front):
    def f(r):
        r.fill(RED[2])
        for j in range(r.h):
            for i in range(r.w):
                if (i + j) % 6 == 0:
                    r.set(i, j, RED[1])
        for i in range(r.w):
            r.set(i, 0, GOLD[3]); r.set(i, r.h - 1, GOLD[1])
        for j in range(r.h):
            r.set(0, j, GOLD[2]); r.set(r.w - 1, j, GOLD[1])
        if front == "lower":
            # bandeja de moedas e a fenda
            for i in range(r.w // 4, 3 * r.w // 4):
                for j in range(r.h - r.h // 3, r.h - 3):
                    r.set(i, j, BLACK[1])
                r.set(i, r.h - r.h // 3, GOLD[3])
            for i in range(r.w // 2 - 2, r.w // 2 + 2):
                r.set(i, 3, BLACK[0])
            suit_at(r, "diamond", 3, 4, GOLD[3])
            suit_at(r, "heart", r.w - 8, 4, GOLD[3])
        elif front == "upper":
            for j in range(2, r.h - 2):
                for i in range(2, r.w - 2):
                    r.set(i, j, BLACK[2] if (j % 4) else BLACK[1])
        r.noise(0.02)
    return f


def marquee(r):
    r.fill(BLACK[1])
    for i in range(r.w):
        r.glow(i, 0, GOLD[4] if i % 2 else PINK[3]); r.glow(i, r.h - 1, PINK[3] if i % 2 else GOLD[4])
    # "7 7 7" aceso no meio
    cx = r.w // 2 - 7
    sev = ["####", "...#", "..#.", ".#..", ".#.."]
    for k in range(3):
        for j, row in enumerate(sev):
            for i, ch in enumerate(row):
                if ch == "#":
                    r.glow(cx + k * 5 + i, 3 + j, RED[4])


def slot_machine():
    m = O.Model("slot_machine")
    m.bone("cabinet", [0, 0, 0])
    m.box("cabinet", [-8, 0, -6], [16, 12, 12], fc(n="cab_lower@32x24", s="cab@16x12", ew="cab@12x12", u="cab_top@16x12", d="cab@16x12"))
    m.box("cabinet", [-7, 12, -5], [14, 14, 10], fc(n="cab_upper@28x28", s="cab@14x14", ew="cab@10x14", u=OMIT, d=OMIT))
    m.box("cabinet", [-8, 26, -6], [16, 6, 12], fc(n="marquee@32x12", s="cab@16x6", ew="cab@12x6", u="cab_top@16x12", d="cab@16x12"))
    m.box("cabinet", [-6, 30.5, -6.4], [12, 0.8, 0.6], fc(all="bulbs@12x1"))
    m.box("cabinet", [-8.5, 0, -6.5], [17, 1, 13], fc(all="chrome@8x4"))
    # moldura do visor
    m.box("cabinet", [-7.2, 15.7, -7.6], [14.4, 0.6, 2.6], fc(all="chrome@8x1"))
    m.box("cabinet", [-7.2, 21.7, -7.6], [14.4, 0.6, 2.6], fc(all="chrome@8x1"))
    xs = (-4.6, 0, 4.6)
    for i, x in enumerate(xs):
        m.bone(f"reel{i}", [x, 19, -5], parent="cabinet")
        m.box(f"reel{i}", [x - 2, 17, -7], [4, 4, 4], fc(n="sym_cherry@12x12", u="sym_bell@12x12", s="sym_bar@12x12",
                                                        d="sym_seven@12x12", ew="reel_side@4x4"))
        for k, s in enumerate(SYMS):
            name = f"sym{i}_{k}"
            m.bone(name, [x, 19, -7.1], parent="cabinet")
            m.box(name, [x - 2, 17, -7.12], [4, 4, 0.01], fc(n=f"sym_{s}@12x12", s=OMIT, ew=OMIT, u=OMIT, d=OMIT))
    m.bone("lever", [8.5, 16, 0], parent="cabinet")
    m.box("lever", [8.2, 16, -0.5], [1, 10, 1], fc(all="chrome@1x8"))
    m.box("lever", [7.6, 25.6, -1.1], [2.2, 2.2, 2.2], fc(all="knob@2x2"))
    m.box("cabinet", [8, 14.5, -1.5], [1.5, 3, 3], fc(all="chrome@2x2"))
    mats = {"cab_lower": cabinet("lower"), "cab_upper": cabinet("upper"), "cab": cabinet("plain"),
            "cab_top": solid(RED[1]), "marquee": marquee, "bulbs": lambda r: [r.glow(i, 0, GOLD[4] if i % 2 else PINK[4])
                                                                                 for i in range(r.w)],
            "chrome": metal(CHROME), "knob": glow_solid(RED[4]), "reel_side": solid(WHITE[2])}
    for s in SYMS:
        mats["sym_" + s] = symbol(s)
    build_entity(m, mats, (128, 128))
    anim("slot_machine", {
        "animation.slot_machine.pull": {"animation_length": 0.7, "bones": {
            "lever": {"rotation": {"0.0": [0, 0, 0], "0.25": [-75, 0, 0], "0.7": [0, 0, 0]}},
            "cabinet": {"scale": {"0.0": [0.85, 1.15, 0.85], "0.2": [1.08, 0.94, 1.08], "0.45": [1, 1, 1]}}}},
        "animation.slot_machine.idle": {"loop": True, "animation_length": 0.6, "bones": {
            "cabinet": {"scale": {"0.0": [1, 1, 1], "0.3": [1.01, 0.99, 1.01], "0.6": [1, 1, 1]}}}}})


# ---------------------------------------------------------------------------------------------- cassino (dominio)
def felt_floor(r):
    """Mesa de feltro circular com borda de ouro, naipes bordados e o desenho de "apostas"."""
    cx, cy = (r.w - 1) / 2.0, (r.h - 1) / 2.0
    R = r.w / 2.0
    rnd = random.Random(7)
    for j in range(r.h):
        for i in range(r.w):
            d = math.hypot(i - cx, j - cy) / R
            if d > 1.0:
                continue
            if d > 0.96:
                r.glow(i, j, GOLD[3])
            elif d > 0.93:
                r.set(i, j, WOOD[2])
            elif d > 0.90:
                r.glow(i, j, GOLD[2])
            else:
                c = FELT[2] if rnd.random() > 0.15 else FELT[1]
                if abs(d - 0.55) < 0.01 or abs(d - 0.3) < 0.01:
                    c = GOLD[2]
                r.set(i, j, c)
    # naipes em volta (8 posicoes)
    for k, s in enumerate(["spade", "heart", "diamond", "club"] * 2):
        a = k * math.pi / 4
        x = int(cx + math.cos(a) * R * 0.74) - 2
        y = int(cy + math.sin(a) * R * 0.74) - 3
        suit_at(r, s, x, y, RED[3] if s in ("heart", "diamond") else BLACK[1])
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        x = int(cx + math.cos(a) * R * 0.42) - 2
        y = int(cy + math.sin(a) * R * 0.42) - 3
        suit_at(r, "diamond", x, y, GOLD[3], glow=True)


def ring_floor(r):
    """Anel da roleta gigante no chao (casas vermelhas/pretas/zero), transparente no meio e fora."""
    cx, cy = (r.w - 1) / 2.0, (r.h - 1) / 2.0
    R = r.w / 2.0
    n = len(WHEEL)
    for j in range(r.h):
        for i in range(r.w):
            d = math.hypot(i - cx, j - cy) / R
            if not (0.86 < d <= 1.0):
                continue
            ang = math.atan2(j - cy, i - cx) % (2 * math.pi)
            k = int(ang / (2 * math.pi) * n) % n
            frac = (ang / (2 * math.pi) * n) % 1.0
            num = WHEEL[k]
            c = FELT[3] if num == 0 else RED[3] if num in REDS else BLACK[2]
            if frac < 0.08 or d > 0.985 or d < 0.875:
                r.glow(i, j, GOLD[3])
            else:
                r.set(i, j, c)


def casino():
    m = O.Model("casino_domain")
    m.bone("floor", [0, 0, 0])
    m.box("floor", [-32, 0.15, -32], [64, 0.01, 64], fc(u="felt@128x128", d=OMIT, side=OMIT))
    m.bone("wheelRing", [0, 0.3, 0])
    m.box("wheelRing", [-30, 0.3, -30], [60, 0.01, 60], fc(u="ring@128x128", d=OMIT, side=OMIT))
    m.bone("orbit", [0, 0, 0])
    cards = [("A", "spade"), ("K", "heart"), ("Q", "diamond"), ("J", "club")]
    for k, (rank, s) in enumerate(cards):
        b = f"card{k}"
        a = k * 90
        m.bone(b, [0, 16, 0], parent="orbit", rot=[0, a, 0])
        m.box(b, [-3.5, 12, -27], [7, 10, 0.4], fc(n=f"card_{rank}@14x20", s="card_back@14x20", ew="card_edge@1x4",
                                                   u="card_edge@2x1", d="card_edge@2x1"))
    for k in range(2):
        b = f"bigDie{k}"
        m.bone(b, [0, 14, 0], parent="orbit", rot=[0, 45 + 180 * k, 0])
        m.box(b, [-2.5, 15, -23.5], [5, 5, 5], fc(u="f6@12x12", d="f1@12x12", n="f5@12x12", s="f2@12x12", e="f4@12x12",
                                                  w="f3@12x12"), rot=[20, 30, 10], pivot=[0, 17.5, -21])
    mats = {"felt": felt_floor, "ring": ring_floor, "card_back": card_back, "card_edge": solid(WHITE[3])}
    for rank, s in cards:
        mats["card_" + rank] = card_face(rank, s, big=True)
    for k in range(1, 7):
        mats[f"f{k}"] = die_face(k)
    build_entity(m, mats, (256, 256))
    bones = {"orbit": {"rotation": {"0.0": [0, 0, 0], "12.0": [0, 360, 0]}}}
    for k in range(4):
        t0 = k * 0.75
        bones[f"card{k}"] = {"position": {"0.0": [0, 0, 0], str(1.5 + t0): [0, 1.5, 0], "6.0": [0, 0, 0], str(4.5 + t0 * 0.5): [0, -1, 0],
                                          "12.0": [0, 0, 0]}}
    for k in range(2):
        bones[f"bigDie{k}"] = {"rotation": {"0.0": [0, 0, 0], "12.0": [360, 720, 0]}}
    # chaves em ordem crescente (o GeckoLib aceita fora de ordem, mas o arquivo fica legivel)
    for b in bones.values():
        for ch in b.values():
            items = sorted(ch.items(), key=lambda kv: float(kv[0]))
            ch.clear()
            ch.update(items)
    anim("casino_domain", {"animation.casino_domain.spin": {"loop": True, "animation_length": 12.0, "bones": bones}})



# ===================================================================================================
#  Traje do Apostador (4 graus, motor de trajes do outfits.py)
# ===================================================================================================
HD, BD, RA, LA, RL, LL, RB, LB = O.HD, O.BD, O.RA, O.LA, O.RL, O.LL, O.RB, O.LB
TIER_IDS = ("dealer", "high_roller", "casino_king", "fortune_lord")
OUT_NAMES = {"dealer": "outfit_gambler", "high_roller": "outfit_gambler_high_roller",
             "casino_king": "outfit_gambler_casino_king", "fortune_lord": "outfit_gambler_fortune_lord"}
IVORY = pal("A8A496", "CEC9B8", "E8E4D6", "F6F3EA", "FFFFFF")
TUX = pal("08080C", "121218", "1C1C24", "2A2A34", "3C3C48")
BURGUNDY = pal("2E0A12", "4A1220", "6A1C2E", "8A2A40", "A84058")
LORD = pal("0C0614", "160A24", "221036", "301A4A", "442A64")
SHOE_BLACK = pal("0E0C10", "1A161C", "28222A", "3A3240", "50465A")
SHOE_BROWN = pal("2A160C", "442612", "623A1C", "80522A", "A0703C")
THEMES = {
    # I Crupie: colete bordo sobre camisa branca, gravata borboleta preta, viseira verde
    "dealer": O.theme(BURGUNDY, O.CREAM, O.BRASS, SHOE_BLACK, BLACK, O.BRASS, pal("0A3A22", "1E7A4A", "5AC88A", "C8F4DA"), "5AC88A"),
    # II Mesa Alta: smoking preto com lapela vermelha, fedora preto de fita vermelha, abotoaduras azuis
    "high_roller": O.theme(TUX, O.CREAM, O.SILVER_M, SHOE_BLACK, RED, O.SILVER_M, pal("0A2E4A", "1A5A8A", "7FC8FF", "D8F0FF"), "7FC8FF"),
    # III Rei do Cassino: terno branco com lapela de ouro, camisa preta, fedora branco, polainas
    "casino_king": O.theme(IVORY, BLACK, O.GOLD_R, SHOE_BROWN, GOLD, O.GOLD_R, pal("6A4508", "D49A26", "FFD24A", "FFF6C8"), "FFC94A"),
    # IV Senhor da Fortuna: sobretudo longo preto e roxo com naipes que brilham, cartola, ombreiras de ficha
    "fortune_lord": O.theme(LORD, PURPLE, O.GOLD_B, SHOE_BLACK, PINK, O.GOLD_B, pal("6A1A5A", "C040A0", "FF8AD8", "FFE0F4"), "FF4AD2"),
}
TIES = {"dealer": BLACK, "high_roller": RED, "casino_king": RED, "fortune_lord": PINK}
PANTS = {"dealer": BLACK, "high_roller": TUX, "casino_king": IVORY, "fortune_lord": LORD}
SUITS4 = ("spade", "heart", "diamond", "club")


def card_back_plain(r):
    """Verso de carta sem brilho (o grau I nao tem glowmask)."""
    r.fill(RED[2])
    for j in range(r.h):
        for i in range(r.w):
            if (i + j) % 4 == 0 or (i - j) % 4 == 0:
                r.set(i, j, RED[1])
    for i in range(r.w):
        r.set(i, 0, WHITE[4])
        r.set(i, r.h - 1, WHITE[4])
    for j in range(r.h):
        r.set(0, j, WHITE[4])
        r.set(r.w - 1, j, WHITE[4])


def gb_mats(tkey, lv):
    th = THEMES[tkey]
    P = th["pal"]
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = O.v9_common(M, P, th, lv)
    GL = th["GLOW"]
    TIE, PNT = TIES[tkey], PANTS[tkey]
    lit = lv >= 1

    def buttons(r, col, rows):
        for j in rows:
            if 0 <= j < r.h:
                r.set(col, j, TRIM[3])

    def suits_glow(r, js, step=4):
        """Naipes pequenos (3x3) que brilham na barra/bainha (grau IV)."""
        shapes = [((1, 0), (0, 1), (1, 1), (2, 1), (1, 2)), ((0, 0), (2, 0), (0, 1), (1, 1), (2, 1), (1, 2)),
                  ((1, 0), (0, 1), (2, 1), (1, 2), (1, 1)), ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (0, 2), (2, 2))]
        for jj in js:
            for k, i in enumerate(range(1, r.w - 3, step)):
                for (dx, dy) in shapes[k % 4]:
                    r.glow(i + dx, jj + dy, GL if k % 2 else lighten(GL, 0.3))

    # ---------------- tronco
    def front(r):
        cx = r.w // 2
        if lv == 0:
            # colete: camisa branca no V do pescoco, botoes de latao, bolsinho
            O.cloth(r, MAIN, period=4, base=2, folds=False)
            for j in range(min(r.h, 6)):
                half = max(0, 2 - j // 2)
                for i in range(cx - 1 - half, cx + 1 + half):
                    r.set(i, j, SEC[3] if (i + j) % 3 else SEC[2])
            for j in range(6, r.h):
                r.set(cx - 1, j, MAIN[1])
            buttons(r, cx, (6, 8, 10))
            for i in range(1, 3):
                r.set(i, 6, MAIN[3])
            r.row(-1, MAIN[1])
            return
        # paleto: lapelas em V ate o meio, camisa no V, botoes; naipes na barra (IV)
        O.cloth(r, MAIN, period=4, base=2, top=0.04, bottom=0.10, folds=lv != 2)
        depth = 7
        for j in range(depth):
            half = max(0, (depth - 1 - j) * cx // depth)
            for i in range(cx - half, cx + half):
                r.set(i, j, SEC[2] if lv != 2 else SEC[1])
            for d in (1, 2):
                r.set(cx - half - d, j, ACC[3] if d == 1 else ACC[2])
                r.set(cx + half - 1 + d, j, ACC[3] if d == 1 else ACC[2])
        for j in range(depth, r.h):
            r.set(cx - 1, j, MAIN[1])
        buttons(r, cx, (depth + 1, depth + 3))
        if lv >= 3:
            suits_glow(r, (r.h - 4,), step=4)
        r.row(-1, TRIM[2])

    def back(r):
        O.cloth(r, MAIN, period=4, base=2, top=0.04, bottom=0.12)
        if lv >= 3:
            suit_at(r, "spade", r.w // 2 - 2, 2, GL, glow=True)
        r.row(-1, TRIM[2] if lv >= 1 else MAIN[1])

    def side(r):
        O.cloth(r, MAIN, period=4, base=2, top=0.04, bottom=0.12)
        r.row(-1, TRIM[2] if lv >= 1 else MAIN[1])

    M["front"], M["back"], M["side"] = front, back, side

    def collar(r):
        r.fill(SEC[3])
        r.row(0, SEC[4])
        r.noise(0.02)

    def tie(r):
        r.fill(TIE[3])
        r.col(0, TIE[2])
        r.col(-1, TIE[2])
        r.noise(0.03)

    def tie_knot(r):
        r.fill(TIE[2])
        r.set(0, 0, TIE[4])

    def pocket(r):
        r.fill(ACC[3])
        r.row(0, ACC[4])
        r.noise(0.02)

    M["collar"], M["tie"], M["tie_knot"], M["pocket"] = collar, tie, tie_knot, pocket

    def cummerbund(r):
        for j in range(r.h):
            r.row(j, ACC[2] if j % 2 else ACC[1])
        if lv >= 3:
            for i in range(1, r.w - 1, 3):
                r.glow(i, r.h // 2, GL)
        r.noise(0.03)

    M["cummerbund"] = cummerbund

    # ---------------- abas do fraque / capa / saia do sobretudo
    def coat_out(r):
        O.cloth(r, MAIN, period=4, base=2, top=0.04, bottom=0.12)
        r.col(0, MAIN[1])
        r.col(-1, MAIN[1])
        if lv >= 3:
            for k, j in enumerate(range(2, r.h - 4, 5)):
                suit_at(r, SUITS4[k % 4], (r.w - 5) // 2, j, GL, glow=True)
        O.trim_rows(r, TRIM, 1)

    M["coat_out"] = coat_out
    M["cape_out"] = coat_out
    M["coat_in"] = lambda r: O.cloth(r, ACC if lv >= 1 else MAIN, period=4, base=2, folds=False)

    def cape_in(r):
        O.cloth(r, SEC, period=4, base=2, folds=False)
        O.trim_rows(r, TRIM, 1)

    def cape_edge(r):
        r.fill(TRIM[2])
        r.noise(0.03)

    M["cape_in"], M["cape_edge"] = cape_in, cape_edge

    def coat_skirt(r):
        O.cloth(r, MAIN, period=4, base=2, top=0.04, bottom=0.14)
        if lv >= 3:
            suits_glow(r, (max(0, r.h - 5),), step=5)
        O.trim_rows(r, TRIM, 1)

    M["coat_skirt"] = coat_skirt

    # ---------------- bracos
    def sleeve(r):
        if lv == 0:
            O.cloth(r, SEC, period=3, base=2, folds=True)
            return
        O.cloth(r, MAIN, period=3, base=2)
        r.row(-1, SEC[3])
        r.row(-2, SEC[2])
        if lv >= 2:
            r.row(-3, TRIM[3])

    def cuff(r):
        r.fill(SEC[3])
        r.row(0, SEC[4])
        r.noise(0.02)

    def garter(r):
        r.fill(BLACK[3])
        r.row(0, O.BRASS[3])
        r.noise(0.02)

    M["sleeve"], M["cuff"], M["garter"] = sleeve, cuff, garter

    # ---------------- pernas e sapatos
    def trousers(r):
        O.cloth(r, PNT, period=4, base=2, top=0.03, bottom=0.12)
        if lv >= 1:
            c = r.w // 2
            for j in range(r.h):
                if lv >= 3:
                    r.glow(c, j, GL)
                else:
                    r.set(c, j, TRIM[3])
        r.row(0, PNT[1])

    def shoe(r):
        r.fill(LEATH[1])
        r.row(0, LEATH[3])
        if r.w > 2 and r.h > 1:
            r.set(1, 1, LEATH[4])
        r.row(-1, LEATH[0])
        r.noise(0.03)

    SP = WHITE if lv == 2 else SEC

    def spats(r):
        r.fill(SP[3])
        r.row(-1, SP[1])
        for i in range(0, r.w, 3):
            r.set(i, r.h // 2, TRIM[3])
        r.noise(0.02)

    M["trousers"], M["shoe"], M["spats"] = trousers, shoe, spats

    # ---------------- chapeus
    def visor(r):
        for j in range(r.h):
            for i in range(r.w):
                r.set(i, j, VISOR[2] if (i + j) % 5 else VISOR[3])
        r.row(-1, VISOR[1])

    def visor_band(r):
        r.fill(BLACK[2])
        r.row(0, BLACK[4])
        r.noise(0.03)

    M["visor"], M["visor_band"] = visor, visor_band
    M["visor_under"] = solid(VISOR[1])
    M["visor_edge"] = solid(VISOR[0])
    hatP = {1: TUX, 2: IVORY, 3: LORD}.get(lv, TUX)
    bandP = {1: RED, 2: GOLD, 3: PINK}.get(lv, RED)
    M["hat_crown"] = lambda r: O.cloth(r, hatP, period=4, base=2, top=0.06, bottom=0.10, folds=False)

    def flat(c, edge=None):
        def f(r):
            r.fill(c)
            if edge is not None:
                r.row(0, edge)
            r.noise(0.02)
        return f

    M["hat_top"] = flat(hatP[3])
    M["hat_brim"] = flat(hatP[2], hatP[4])
    M["hat_brim_top"] = flat(hatP[3])
    M["hat_brim_under"] = flat(hatP[1])

    def hat_band(r):
        r.fill(bandP[2])
        r.row(0, bandP[3])
        if r.h > 1:
            r.row(-1, bandP[1])
        if lv >= 3:
            for i in range(1, r.w - 1, 3):
                r.glow(i, r.h // 2, GL)
        r.noise(0.03)

    M["hat_band"] = hat_band
    # cartas (na fita do chapeu e na manga)
    M["card_A"] = card_face("A", "spade")
    M["card_K"] = card_face("K", "heart")
    M["card_back"] = card_back if lit else card_back_plain
    M["card_edge"] = solid(WHITE[3])

    # ombreiras de ficha (IV)
    def chip_side(r):
        for j in range(r.h):
            for i in range(r.w):
                if j in (0, r.h - 1):
                    r.set(i, j, GOLD[2])
                else:
                    r.set(i, j, PINK[3] if (i // 2) % 2 else WHITE[4])

    def chip_top(r):
        cx, cy = (r.w - 1) / 2.0, (r.h - 1) / 2.0
        R = max(1.0, min(r.w, r.h) / 2.0)
        for j in range(r.h):
            for i in range(r.w):
                d = math.hypot(i - cx, j - cy) / R
                ang = math.atan2(j - cy, i - cx)
                if d > 0.72:
                    r.set(i, j, PINK[3] if int((ang + math.pi) / (math.pi / 4)) % 2 else WHITE[4])
                elif d > 0.6:
                    r.set(i, j, GOLD[2])
                else:
                    r.glow(i, j, GOLD[3] if d > 0.25 else GL)

    M["chip_side"], M["chip_top"] = chip_side, chip_top
    return M


def gb_model(name, lv):
    m = O.Model(name)
    O.skeleton(m)
    # ---------------- cabeca: viseira de crupie (I), fedora (II/III), cartola (IV)
    if lv == 0:
        O.ring(m, HD, -4.2, 4.2, -4.2, 4.2, 28.4, 1.2, 0.45, side="visor_band", cap="visor_band")
        m.box(HD, [-4.4, 28.5, -8.0], [8.8, 0.4, 3.8], fc(u="visor", d="visor_under", side="visor_edge@4x1"))
        card_x, card_y = -4.7, 27.9
    elif lv < 3:
        m.box(HD, [-5.8, 30.3, -5.8], [11.6, 0.5, 11.6], fc(side="hat_brim", u="hat_brim_top", d="hat_brim_under"))
        m.box(HD, [-3.9, 30.8, -3.9], [7.8, 3.6, 7.8], fc(side="hat_crown", u="hat_top", d=OMIT))
        m.box(HD, [-3.2, 34.4, -2.6], [6.4, 0.6, 5.2], fc(side="hat_crown", u="hat_top", d=OMIT))
        m.box(HD, [-4.0, 30.8, -4.0], [8.0, 1.0, 8.0], fc(side="hat_band", u=OMIT, d=OMIT))
        card_x, card_y = -4.1, 30.6
    else:
        m.box(HD, [-5.6, 31.0, -5.6], [11.2, 0.6, 11.2], fc(side="hat_brim", u="hat_brim_top", d="hat_brim_under"))
        m.box(HD, [-3.7, 31.6, -3.7], [7.4, 7.0, 7.4], fc(side="hat_crown", u="hat_top", d=OMIT))
        m.box(HD, [-3.8, 31.6, -3.8], [7.6, 1.4, 7.6], fc(side="hat_band", u=OMIT, d=OMIT))
        O.gem(m, HD, [0, 32.3, -3.95], [1.0, 1.0, 0.3])
        card_x, card_y = -3.9, 31.5
    # cartas presas na fita (osso hatCards: tremulam no renderer)
    m.bone("hatCards", [card_x, card_y, 0], parent=HD)
    cards = [(-0.4, 0)] if lv == 0 else [(-1.3, -10), (0.4, 12)]
    for k, (z, rx) in enumerate(cards):
        m.box("hatCards", [card_x - 0.2, card_y, z - 1.1], [0.15, 3.0, 2.2],
              fc(w="card_A" if k == 0 else "card_K", e="card_back", ns="card_edge@1x3", u="card_edge@1x2", d=OMIT),
              rot=[rx, 0, 0], pivot=[card_x, card_y, z])
    # ---------------- tronco: colete (I) / paleto (II-IV), gola, gravata borboleta, faixa
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="front", s="back", ew="side", u="side", d="dark@2x2"), inflate=0.55)
    m.box(BD, [-4.3, 22.6, -2.4], [8.6, 1.4, 4.8], fc(side="collar", u="collar@4x2", d=OMIT))
    m.box(BD, [-1.6, 21.6, -3.0], [3.2, 1.3, 0.5], fc(all="tie@2x1"))
    O.gem(m, BD, [0, 22.25, -3.2], [0.8, 0.9, 0.4], "tie_knot@1x1")
    band = "cummerbund" if lv else "belt"
    m.box(BD, [-4, 12.0, -2], [8, 1.3, 4], fc(n=band if lv else "belt_front", s=band, ew=band, u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=0.85)
    if lv >= 1:
        m.box(BD, [1.3, 20.0, -2.95], [1.4, 0.9, 0.4], fc(all="pocket@2x1"))
    if lv >= 2:
        m.box(BD, [-3.2, 15.3, -2.95], [3.4, 0.4, 0.3], fc(all="o_chain@4x1"))
        O.gem(m, BD, [-3.4, 15.5, -3.0], [0.7, 0.7, 0.3])
    if lv in (1, 2):
        # abas do fraque (sobem ao correr)
        m.bone("outfitCape", [0, 13.0, 2.6], parent=BD, rot=[6, 0, 0])
        for x0 in (-3.9, 0.1):
            m.box("outfitCape", [x0, 3.5 - lv, 2.6], [3.8, 9.5 + lv, 0.5],
                  fc(s="coat_out", n="coat_in", ew="cape_edge@1x8", u="cape_edge@2x1", d=OMIT))
    elif lv == 3:
        O.waist(m, "Chest")
        O.skirt(m, "Chest", 4.0, "coat_skirt", "coat_skirt", "coat_skirt", "coat_in@4x4", "coat_in", flare=6, depth=0.5,
                width=9.2)
        O.cape(m, 24.0, 3.0, 2.9, 9.0, out="cape_out", inn="cape_in", edge="cape_edge", flare=4)
    # ---------------- bracos: mangas, liga (I) ou punho (II-IV), abotoadura, carta na manga
    m.box(RA, [-8, 12, -2], [4, 12, 4], fc(side="sleeve", u="sleeve@4x4", d=OMIT), inflate=0.45)
    if lv == 0:
        m.box(RA, [-8, 18.0, -2], [4, 0.8, 4], fc(side="garter@4x1", u=OMIT, d=OMIT), inflate=0.65)
    else:
        m.box(RA, [-8, 12, -2], [4, 1.3, 4], fc(side="cuff@4x1", u=OMIT, d=OMIT), inflate=0.65)
    if lv >= 2:
        O.gem(m, RA, [-8.75, 12.65, 0], [0.3, 0.6, 0.6], "o_gem@1x1")
    m.mirror(RA, LA)
    if lv >= 1:
        m.box(RA, [-7.3, 10.2, -2.85], [1.6, 2.4, 0.12], fc(n="card_A", s="card_back", ew="card_edge@1x2",
                                                           u="card_edge@2x1", d="card_edge@2x1"))
    if lv >= 3:
        O.pauldrons(m, 1, side="chip_side", top="chip_top", under="o_dark@2x2", gem_mat="o_gem@2x2", gem_size=1.0)
    # ---------------- pernas
    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(RL, LL)
    # ---------------- sapatos (polainas no III e IV)
    m.box(RB, [-3.9, 0, -2], [4, 3.2, 4], fc(side="shoe", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(RB, [-3.6, -0.4, -3.3], [3.4, 1.5, 1.4], fc(n="shoe", ew="shoe", u="shoe", d="sole@2x2", s=OMIT))
    if lv >= 2:
        m.box(RB, [-3.9, 1.6, -2], [4, 2.6, 4], fc(side="spats", u=OMIT, d=OMIT), inflate=0.75)
    if lv >= 3:
        O.gem(m, RB, [-1.9, 2.4, -3.1], [0.7, 0.7, 0.3])
    m.mirror(RB, LB)
    return m


# ---------------------------------------------------------------------------------------------- icones das pecas
ICON_ROWS = {
    "visor": ["................", "................", "................", "................", "................",
              "....oooooooo....", "...onnnnnnnno...", "..oooooooooooo..", ".oVVVVVVVVVVVVo.", ".oVWVVVVVVVVVVo.",
              "..oVVVVVVVVVVo..", "...oooooooooo...", "................", "................", "................",
              "................"],
    "fedora": ["................", "................", "..........cco...", ".....oooooccco..", "....olmmmmccco..",
               "...olmmoommdo...", "...olmmmmmmdo...", "...ouuuuuuuuo...", ".oooooooooooooo.", "olllmmmmmmmmmddo",
               ".oooooooooooooo.", "................", "................", "................", "................",
               "................"],
    "tophat": ["................", "....oooooooo....", "....olmmmmdo....", "....olmmmmdo.cco", "....olmmmmdoccco",
               "....olmmmmdoccco", "....olmmmmdocco.", "....ouuxuuuo....", "..oooooooooooo..", ".olmmmmmmmmmmdo.",
               "..oooooooooooo..", "................", "................", "................", "................",
               "................"],
    "chestplate": ["................", ".ooooo....ooooo.", "odmmmlo..olmmmdo", "odmmmmlvvlmmmmdo", "odmmmmlbblmmmmdo",
                   "oddmmmlbblmmmddo", "occddmlbblmddcco", ".oo.odlbbldo.oo.", "....odmlhmmdo...", "....odmmmmmdo...",
                   "....odmmhmmdo...", "....odmmmmmdo...", "....oddmmmddo...", "....oddo.oddo...", "....ooo...ooo...",
                   "................"],
    "leggings": ["................", "...oooooooooo...", "...ouuuuuuuuo...", "...okkkkkkkko...", "...okkkookkkko..",
                 "...okhkoo.khko..", "...okhko..okhko.", "...okhko..okhko.", "...okhko..okhko.", "...okhko..okhko.",
                 "...okhko..okhko.", "...okhko..okhko.", "...okkko..okkko.", "...oooo...oooo..", "................",
                 "................"],
    "boots": ["................", "................", "................", "................", "................",
              "................", "................", "................", "..oooo....oooo..", "..okko....okko..",
              "..occo....occo..", "..occo....occo..", ".ofFfo...ofFfo..", "offfffo.offfffo.", "oeeeeeo.oeeeeeo.",
              ".ooooo...ooooo.."],
}


def piece_palette(tkey):
    th = THEMES[tkey]
    cols = O.v9_icon_palette(th)
    cols.update({"k": PANTS[tkey][2], "V": VISOR[2], "W": VISOR[4], "c": WHITE[4], "v": TIES[tkey][3],
                 "b": th["pal"]["SEC"][3]})
    if tkey == "casino_king":
        cols["b"] = BLACK[2]
    return cols


def draw_piece_icon(tkey, piece):
    lv = TIER_IDS.index(tkey)
    th = THEMES[tkey]
    cols = piece_palette(tkey)
    if piece == "helmet":
        rows = ICON_ROWS["visor" if lv == 0 else "tophat" if lv == 3 else "fedora"]
        hp = {1: TUX, 2: IVORY, 3: LORD}.get(lv)
        if hp:
            cols.update({"l": hp[3], "m": hp[2], "d": hp[1], "u": {1: RED, 2: GOLD, 3: PINK}[lv][3]})
    else:
        rows = ICON_ROWS[piece]
    c = Canvas(16, 16)
    for y, row in enumerate(rows[:16]):
        row = (row + "." * 16)[:16]
        for x, ch in enumerate(row):
            if ch != "." and ch in cols:
                c.set(x, y, cols[ch])
    G = th["GEM"]
    corner = (14, 1) if piece != "helmet" else (1, 1)
    if lv == 3:
        glow_c = lighten(th["GLOW"], 0.2)
        O.sparkle(c, corner, glow_c)
        O.sparkle(c, (1, 14), glow_c)
    elif lv >= 1:
        O.tier_pip(c, corner, G)
        if lv == 2:
            O.tier_pip(c, (1, 14), G)
    item = ("gambler_" + piece) if lv == 0 else ("gambler_%s_%s" % (tkey, piece))
    c.save(os.path.join(A, "textures/item", item + ".png"))
    item_model(A, item)


def vanilla_layers(name, main, trim, dark):
    """Reserva no formato vanilla (so aparece se o GeckoLib faltar)."""
    rnd = random.Random(len(name))
    os.makedirs(os.path.join(A, "textures/models/armor"), exist_ok=True)
    c1 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c1.set(x, y, trim if rnd.random() < 0.06 else (main if (x + y) % 5 else dark))
    c1.save(os.path.join(A, "textures/models/armor", name + "_layer_1.png"))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(A, "textures/models/armor", name + "_layer_2.png"))


def gen_outfit():
    for lv, tkey in enumerate(TIER_IDS):
        name = OUT_NAMES[tkey]
        _, glow = O.build(gb_model(name, lv), gb_mats(tkey, lv))
        if glow != (lv >= 1):
            raise RuntimeError("%s: glowmask=%s, esperado %s (GamblerOutfitRenderer)" % (name, glow, lv >= 1))
        P = THEMES[tkey]["pal"]
        vanilla_layers(name, P["MAIN"][2], P["TRIM"][2], P["MAIN"][0])
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            draw_piece_icon(tkey, piece)
    print("  traje: 4 graus, 16 icones")


# ===================================================================================================
#  Itens: baralho (graus), carta, ficha; efeitos; aba
# ===================================================================================================
DECK_BACKS = {1: RED, 2: pal("0A1A3A", "142C6A", "1E46A0", "3A6AD0", "7AA0F0"),
              3: pal("100C04", "2A2008", "4A3A10", "C4962E", "FFE27A"), 4: PURPLE}
MINI_SUIT = {"spade": [".#.", "###", ".#."], "heart": ["#.#", "###", ".#."], "diamond": [".#.", "###", ".#."]}


def small_card(c, x0, y0, back, glow=None, face=None):
    """Carta 6x9 (verso ou frente) em (x0, y0)."""
    w, h = 6, 9
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            if face:
                col = WHITE[1] if edge else WHITE[4]
            else:
                col = WHITE[3] if edge else (back[2] if (x + y) % 2 else back[3])
            c.set(x0 + x, y0 + y, col)
    if face:
        kind, ink = face
        for j, row in enumerate(MINI_SUIT[kind]):
            for i, ch in enumerate(row):
                if ch == "#":
                    c.set(x0 + 1 + i, y0 + 3 + j, ink)
        c.set(x0 + 1, y0 + 1, ink)
    elif glow:
        c.set(x0 + 2, y0 + 4, glow)
        c.set(x0 + 3, y0 + 4, glow)


def icon_deck(tier):
    c = Canvas(16, 16)
    back = DECK_BACKS[tier]
    small_card(c, 2, 5, back)
    small_card(c, 5, 4, back)
    small_card(c, 8, 3, back, glow=GOLD[4])
    small_card(c, 9, 6, back, face=("spade", BLACK[1]) if tier < 4 else ("heart", RED[3]))
    c.outline(darken(back[0], 0.5))
    if tier >= 2:
        c.set(14, 1, GOLD[4]); c.set(13, 1, GOLD[2]); c.set(14, 2, GOLD[2])
    if tier >= 4:
        c.set(1, 2, PINK[4]); c.set(1, 3, PINK[3]); c.set(2, 2, PINK[3])
    return c


def icon_card():
    c = Canvas(16, 16)
    for y in range(1, 15):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (1, 14)
            c.set(x, y, WHITE[1] if edge else WHITE[4])
    for j, row in enumerate(SUIT["spade"]):
        for i, ch in enumerate(row):
            if ch == "#":
                c.set(5 + i + 1, 5 + j, BLACK[1])
    for (x, y) in ((5, 2), (6, 2), (5, 3), (10, 13), (9, 13), (10, 12)):
        c.set(x, y, RED[3])
    c.set(10, 2, GOLD[4])
    c.set(5, 13, GOLD[3])
    c.outline(darken(WHITE[0], 0.5))
    return c


def icon_chip(size=16, cx=None, cy=None, r=6.5):
    c = Canvas(size, size)
    cx = size / 2.0 if cx is None else cx
    cy = size / 2.0 if cy is None else cy
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > r:
                continue
            ang = math.atan2(y + 0.5 - cy, x + 0.5 - cx)
            if d > r - 1.6:
                c.set(x, y, PINK[3] if int((ang + math.pi) / (math.pi / 4)) % 2 else WHITE[4])
            elif d > r - 2.4:
                c.set(x, y, GOLD[2])
            else:
                c.set(x, y, PINK[2] if d > 1.8 else GOLD[4])
    for (dx, dy) in ((0, -1), (-1, 0), (1, 0), (0, 1)):
        c.set(int(cx) + dx, int(cy) + dy, GOLD[3])
    c.set(int(cx), int(cy), GOLD[4])
    c.outline(PINK[0])
    return c


def effect_jackpot():
    c = Canvas(18, 18)
    c.circle(9, 9, 8, GOLD[2])
    c.circle(9, 9, 6.6, GOLD[3])
    c.shade_sphere(9, 9, 8, 0.35, 0.3)
    seven = ["#####", "....#", "...#.", "..#..", ".##..", ".#...", ".#..."]
    for j, row in enumerate(seven):
        for i, ch in enumerate(row):
            if ch == "#":
                c.set(7 + i, 5 + j, RED[3])
    c.set(3, 4, GOLD[4])
    c.set(14, 4, WHITE[4])
    c.set(4, 14, PINK[4])
    c.outline(GOLD[0])
    return c


def effect_lucky():
    c = Canvas(18, 18)
    G = pal("0A3A12", "1E6A2A", "3AA04A", "6AD87A", "B8F4C0")
    leaves = ((6, 6), (12, 6), (6, 11), (12, 11))
    for (x, y) in leaves:
        c.circle(x, y, 3.4, G[2])
    for (x, y) in leaves:
        c.set(x - 1, y - 1, G[3])
        c.set(x - 2, y - 1, G[4])
    c.line(9, 9, 13, 16, G[1])
    c.line(10, 9, 14, 16, G[1])
    c.set(9, 9, G[0])
    c.outline(G[0])
    return c


def effect_jinxed():
    c = Canvas(18, 18)
    K = pal("08060C", "141018", "201A26", "2E2636")
    rows = ["..#..........#..", "..##........##..", ".####......####.", ".#####....#####.",
            ".##############.", "################", "################", "################",
            "################", ".##############.", "..############..", "...##########...",
            "....########...."]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                c.set(i + 1, j + 3, K[2] if (i + j) % 5 else K[3])
    eye = H("C08AFF")
    for (x, y) in ((5, 10), (6, 10), (11, 10), (12, 10)):
        c.set(x, y, eye)
    c.set(6, 11, PURPLE[4])
    c.set(11, 11, PURPLE[4])
    c.set(8, 13, PINK[3])
    c.set(9, 13, PINK[3])
    c.outline(darken(PURPLE[0], 0.4))
    return c


def icon_tab():
    """Dado + ficha (aba Mesa da Casa)."""
    c = icon_chip(16, 11.0, 11.0, 4.6)
    for y in range(1, 11):
        for x in range(1, 11):
            edge = x in (1, 10) or y in (1, 10)
            c.set(x, y, GOLD[2] if edge else WHITE[4])
    for (x, y) in ((3, 3), (7, 3), (5, 5), (3, 7), (7, 7)):
        c.set(x, y, RED[3] if (x, y) == (5, 5) else BLACK[1])
        c.set(x + 1, y, RED[2] if (x, y) == (5, 5) else BLACK[2])
    c.outline(BLACK[0])
    return c


def gen_items():
    for t in (1, 2, 3, 4):
        icon_deck(t).save(os.path.join(A, "textures/item", "gambler_deck_t%d.png" % t))
        item_model(A, "gambler_deck_t%d" % t)
    item_model(A, "gambler_deck", "gambler_deck_t1")
    icon_card().save(os.path.join(A, "textures/item", "gambler_card.png"))
    item_model(A, "gambler_card")
    icon_chip().save(os.path.join(A, "textures/item", "casino_chip.png"))
    item_model(A, "casino_chip")
    os.makedirs(os.path.join(A, "textures/mob_effect"), exist_ok=True)
    effect_jackpot().save(os.path.join(A, "textures/mob_effect", "gambler_jackpot.png"))
    effect_lucky().save(os.path.join(A, "textures/mob_effect", "gambler_lucky.png"))
    effect_jinxed().save(os.path.join(A, "textures/mob_effect", "gambler_jinxed.png"))
    os.makedirs(os.path.join(A, "textures/gui/tab"), exist_ok=True)
    icon_tab().save(os.path.join(A, "textures/gui/tab", "casino.png"))
    print("  itens: baralho I-IV, carta, ficha; 3 efeitos; aba")


# ===================================================================================================
#  Receitas e tags
# ===================================================================================================
NS = "isekai_mahou"
MERGE = os.path.join(ROOT, "src/main/merge/class_gambler")


def ing(i):
    return {"item": i if ":" in i else NS + ":" + i}


def shaped(name, pattern, key, result, count=1):
    write_json(os.path.join(D, "recipe/class_gambler", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
        "key": {k: ing(v) for k, v in key.items() if any(k in row for row in pattern)},
        "result": {"id": NS + ":" + result, "count": count}})


def shapeless(name, ingredients, result, count=1):
    write_json(os.path.join(D, "recipe/class_gambler", name + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [ing(i) for i in ingredients],
        "result": {"id": NS + ":" + result, "count": count}})


def gen_recipes():
    shapeless("gambler_card", ["minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:ink_sac",
                               "minecraft:red_dye"], "gambler_card", 8)
    shaped("gambler_deck", ["CCC", "CGC", "CCC"], {"C": "gambler_card", "G": "minecraft:gold_ingot"}, "gambler_deck")
    keys = {"L": "minecraft:leather", "K": "minecraft:black_wool", "S": "minecraft:string", "R": "minecraft:red_wool",
            "G": "minecraft:green_stained_glass_pane"}
    shaped("gambler_helmet", ["KKK", "GGG"], keys, "gambler_helmet")
    shaped("gambler_chestplate", ["K K", "RSR", "RKR"], keys, "gambler_chestplate")
    shaped("gambler_leggings", ["KSK", "K K", "K K"], keys, "gambler_leggings")
    shaped("gambler_boots", ["L L", "L L"], keys, "gambler_boots")
    print("  receitas: 6")


def tag(path, values):
    write_json(os.path.join(MERGE, "data", path), {"replace": False, "values": [NS + ":" + v for v in values]})


def gen_tags():
    for piece, t in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"),
                     ("boots", "foot_armor")):
        tag("minecraft/tags/item/%s.json" % t, ["gambler_" + piece] + ["gambler_%s_%s" % (k, piece) for k in TIER_IDS[1:]])
    print("  tags (merge)")


def main():
    print("== class_gambler")
    dice()
    roulette()
    slot_machine()
    casino()
    gen_outfit()
    gen_items()
    gen_recipes()
    gen_tags()
    print("class_gambler: assets, receitas e tags gerados")


if __name__ == "__main__":
    main()
