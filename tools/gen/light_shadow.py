"""
Gera os assets do modulo light_shadow (rode da raiz do projeto: py -I tools/gen/light_shadow.py):
  - texturas GeckoLib (64x64) + glowmask da Fada Guardia e da Sombra Viva
  - modelos .geo.json e animacoes .animation.json das duas invocacoes
  - icones 18x18 dos efeitos de status do modulo
  - tag isekai_mahou:unholy (demonios/sombras) no merge do modulo
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/light_shadow")
T = (0, 0, 0, 0)


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)


# =====================================================================================
#  UV de caixa (layout Bedrock/Java): devolve o retangulo (x, y, w, h) de cada face
# =====================================================================================
def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(c, rect, col):
    x, y, w, h = rect
    if w > 0 and h > 0:
        c.rect(x, y, x + w - 1, y + h - 1, col)


def vgrad(c, rect, top, bottom):
    x, y, w, h = rect
    for j in range(h):
        t = j / max(1, h - 1)
        for i in range(w):
            c.set(x + i, y + j, mix(top, bottom, t))


def paint_box(c, u, v, w, h, d, col, shade=True):
    """Pinta todas as faces com uma cor, com topo mais claro e laterais/baixo mais escuros."""
    f = box_faces(u, v, w, h, d)
    fill(c, f["front"], col)
    fill(c, f["back"], darken(col, 0.12) if shade else col)
    fill(c, f["right"], darken(col, 0.08) if shade else col)
    fill(c, f["left"], darken(col, 0.08) if shade else col)
    fill(c, f["top"], lighten(col, 0.12) if shade else col)
    fill(c, f["bottom"], darken(col, 0.2) if shade else col)
    return f


# =====================================================================================
#  Fada Guardia
# =====================================================================================
def fairy_texture():
    c = Canvas(64, 64)
    g = Canvas(64, 64)  # glowmask
    skin = hex_rgba("FFE3CC")
    blush = hex_rgba("FFB3B8")
    eye = hex_rgba("3A7BD5")
    eye_hi = hex_rgba("BFE6FF")
    hair = hex_rgba("FFD86B")
    hair_dk = hex_rgba("E0AE38")
    dress = hex_rgba("FFF8E8")
    trim = hex_rgba("E8C860")
    gold = hex_rgba("FFE27A")

    # Cabeca 5x5x5 em (0,0)
    f = paint_box(c, 0, 0, 5, 5, 5, skin)
    fx, fy, _, _ = f["front"]
    c.set(fx + 1, fy + 2, eye); c.set(fx + 3, fy + 2, eye)
    c.set(fx + 1, fy + 1, eye_hi); c.set(fx + 3, fy + 1, eye_hi)
    c.set(fx + 0, fy + 3, blush); c.set(fx + 4, fy + 3, blush)
    c.set(fx + 2, fy + 4, hex_rgba("D9767E"))
    g.set(fx + 1, fy + 2, eye_hi); g.set(fx + 3, fy + 2, eye_hi)

    # Cabelo (caixa inflada) 5x5x5 em (20,0): franja na frente, comprido atras e dos lados
    h = box_faces(20, 0, 5, 5, 5)
    fill(c, h["top"], hair)
    for side in ("right", "left"):
        x, y, w, hh = h[side]
        for j in range(hh):
            for i in range(w):
                if j < 3 or (j == 3 and i % 2 == 0):
                    c.set(x + i, y + j, hair if (i + j) % 3 else hair_dk)
    x, y, w, hh = h["back"]
    for j in range(hh):
        for i in range(w):
            c.set(x + i, y + j, hair if (i * 3 + j) % 4 else hair_dk)
    x, y, w, hh = h["front"]
    for i in range(w):
        c.set(x + i, y, hair)
        if i != 2:
            c.set(x + i, y + 1, hair_dk if i in (1, 3) else hair)

    # Tronco 3x4x2 em (0,10)
    t = paint_box(c, 0, 10, 3, 4, 2, dress)
    for key in ("front", "back"):
        x, y, w, hh = t[key]
        for i in range(w):
            c.set(x + i, y + 2, trim)
    x, y, _, _ = t["front"]
    c.set(x + 1, y + 2, gold)
    g.set(x + 1, y + 2, gold)

    # Saia 5x3x3 em (10,10): barra dourada embaixo
    s = paint_box(c, 10, 10, 5, 3, 3, dress)
    for key in ("front", "back", "left", "right"):
        x, y, w, hh = s[key]
        for i in range(w):
            c.set(x + i, y + hh - 1, trim)
            if i % 2 == 0:
                c.set(x + i, y, lighten(dress, 0.3))

    # Bracos 1x3x1 em (26,10) e (30,10): manga branca
    for u in (26, 30):
        a = paint_box(c, u, 10, 1, 3, 1, skin)
        for key in ("front", "back", "left", "right"):
            x, y, w, hh = a[key]
            c.set(x, y, dress)
    # Pernas 1x2x1 em (26,14) e (30,14): sapatilha dourada
    for u in (26, 30):
        l = paint_box(c, u, 14, 1, 2, 1, skin)
        for key in ("front", "back", "left", "right"):
            x, y, w, hh = l[key]
            c.set(x, y + 1, trim)
        fill(c, l["bottom"], trim)

    # Aureola 4x0x4 em (0,16): anel dourado (topo e baixo)
    a = box_faces(0, 16, 4, 0, 4)
    for key in ("top", "bottom"):
        x, y, w, hh = a[key]
        for j in range(hh):
            for i in range(w):
                if i in (0, w - 1) or j in (0, hh - 1):
                    col = gold if (i + j) % 2 else lighten(gold, 0.4)
                    c.set(x + i, y + j, col)
                    g.set(x + i, y + j, col)

    # Asas (planos): superiores 7x8 em (40,0)/(40,8), inferiores 5x5 em (54,0)/(54,5)
    def wing(x0, y0, w, hh, root_right):
        """Asa em gota: estreita na raiz (junto do corpo), larga na ponta, borda clara e 3 nervuras."""
        m = Canvas(w, hh)
        cy = (hh - 1) / 2.0
        for j in range(hh):
            for i in range(w):
                t = i / max(1, w - 1)                      # 0 = raiz, 1 = ponta
                half = (hh / 2.0) * (0.35 + 0.65 * math.sin(min(1.0, t * 1.15) * math.pi * 0.5 + 0.15))
                if t > 0.82:
                    half *= max(0.0, 1.0 - (t - 0.82) * 3.0)  # ponta arredondada
                if abs(j - cy) <= half:
                    m.set(i, j, mix((175, 230, 255, 170), (235, 250, 255, 115), t))
        inner = [row[:] for row in m.px]
        for j in range(hh):
            for i in range(w):
                if inner[j][i][3] == 0:
                    continue
                border = any(not (0 <= i + a < w and 0 <= j + b < hh) or inner[j + b][i + a][3] == 0
                             for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if border:
                    m.set(i, j, (140, 215, 255, 225))
        for (tx, ty) in ((w - 2, 1), (w - 1, round(cy)), (w - 2, hh - 2)):
            m.line(0, round(cy), tx, ty, (255, 255, 255, 230))
        for j in range(hh):
            for i in range(w):
                col = m.px[j][i]
                if inner[j][i][3] == 0 or col[3] == 0:
                    continue
                xx = (w - 1 - i) if root_right else i
                c.set(x0 + xx, y0 + j, col)
                if col[3] >= 220:
                    g.set(x0 + xx, y0 + j, (col[0], col[1], col[2], 190))

    # Na face da frente (norte) o U cresce para +X: a asa esquerda (+X) tem a raiz à esquerda da imagem,
    # a direita (-X) à direita; no verso (sul) é o contrário.
    for (u, v, w, hh, right_wing) in ((40, 0, 7, 8, False), (40, 8, 7, 8, True), (54, 0, 5, 5, False), (54, 5, 5, 5, True)):
        wing(u, v, w, hh, right_wing)          # frente
        wing(u + w, v, w, hh, not right_wing)  # verso

    c.save(os.path.join(A, "textures/entity/summoned_guardian_fairy.png"))
    g.save(os.path.join(A, "textures/entity/summoned_guardian_fairy_glowmask.png"))


def fairy_geo():
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 4, 0],
         "cubes": [{"origin": [-1.5, 4, -1], "size": [3, 4, 2], "uv": [0, 10]}]},
        {"name": "skirt", "parent": "body", "pivot": [0, 5, 0],
         "cubes": [{"origin": [-2.5, 2, -1.5], "size": [5, 3, 3], "uv": [10, 10]}]},
        {"name": "head", "parent": "body", "pivot": [0, 8, 0],
         "cubes": [{"origin": [-2.5, 8, -2.5], "size": [5, 5, 5], "uv": [0, 0]},
                   {"origin": [-2.5, 8, -2.5], "size": [5, 5, 5], "uv": [20, 0], "inflate": 0.4}]},
        {"name": "halo", "parent": "head", "pivot": [0, 14.5, 0],
         "cubes": [{"origin": [-2, 14.5, -2], "size": [4, 0, 4], "uv": [0, 16]}]},
        {"name": "right_arm", "parent": "body", "pivot": [-1.5, 7.5, 0],
         "cubes": [{"origin": [-2.5, 4.5, -0.5], "size": [1, 3, 1], "uv": [26, 10]}]},
        {"name": "left_arm", "parent": "body", "pivot": [1.5, 7.5, 0],
         "cubes": [{"origin": [1.5, 4.5, -0.5], "size": [1, 3, 1], "uv": [30, 10]}]},
        {"name": "right_leg", "parent": "root", "pivot": [-0.75, 2, 0],
         "cubes": [{"origin": [-1.25, 0, -0.5], "size": [1, 2, 1], "uv": [26, 14]}]},
        {"name": "left_leg", "parent": "root", "pivot": [0.75, 2, 0],
         "cubes": [{"origin": [0.25, 0, -0.5], "size": [1, 2, 1], "uv": [30, 14]}]},
        {"name": "left_wing", "parent": "body", "pivot": [0.5, 7, 1.1], "rotation": [0, -20, 10],
         "cubes": [{"origin": [0.5, 5, 1.1], "size": [7, 8, 0], "uv": [40, 0]},
                   {"origin": [0.5, 0, 1.15], "size": [5, 5, 0], "uv": [54, 0]}]},
        {"name": "right_wing", "parent": "body", "pivot": [-0.5, 7, 1.1], "rotation": [0, 20, -10],
         "cubes": [{"origin": [-7.5, 5, 1.1], "size": [7, 8, 0], "uv": [40, 8]},
                   {"origin": [-5.5, 0, 1.15], "size": [5, 5, 0], "uv": [54, 5]}]},
    ]
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.summoned_guardian_fairy", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
        "bones": bones}]}
    save_json(os.path.join(A, "geo/entity/summoned_guardian_fairy.geo.json"), geo)


def r3(v):
    return [round(x, 2) for x in v]


def sampled(length, steps, fn):
    """Keyframes amostrando uma funcao t -> [x, y, z] (animacao suave com interpolacao linear)."""
    out = {}
    for i in range(steps + 1):
        t = length * i / steps
        out[str(round(t, 4))] = r3(fn(t))
    return out


def fairy_anim():
    L = 1.0
    idle = {
        "loop": True, "animation_length": L,
        "bones": {
            "root": {"position": sampled(L, 8, lambda t: [0, 0.8 * math.sin(2 * math.pi * t / L), 0])},
            # 4 batidas de asa por ciclo
            "left_wing": {"rotation": sampled(L, 16, lambda t: [0, 35 * math.sin(8 * math.pi * t / L), 0])},
            "right_wing": {"rotation": sampled(L, 16, lambda t: [0, -35 * math.sin(8 * math.pi * t / L), 0])},
            "left_arm": {"rotation": sampled(L, 8, lambda t: [0, 0, -10 - 8 * math.sin(2 * math.pi * t / L)])},
            "right_arm": {"rotation": sampled(L, 8, lambda t: [0, 0, 10 + 8 * math.sin(2 * math.pi * t / L)])},
            "skirt": {"rotation": sampled(L, 8, lambda t: [4 * math.sin(2 * math.pi * t / L + 1.0), 0, 0])},
            "right_leg": {"rotation": sampled(L, 8, lambda t: [12 * math.sin(2 * math.pi * t / L), 0, 0])},
            "left_leg": {"rotation": sampled(L, 8, lambda t: [-12 * math.sin(2 * math.pi * t / L), 0, 0])},
            "halo": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 360, 0]},
                     "position": sampled(L, 8, lambda t: [0, 0.3 * math.sin(2 * math.pi * t / L + 2.0), 0])},
            "head": {"rotation": sampled(L, 8, lambda t: [0, 0, 4 * math.sin(2 * math.pi * t / L)])},
        }}
    heal = {
        "loop": False, "animation_length": 0.6,
        "bones": {
            "left_arm": {"rotation": {"0.0": [0, 0, 0], "0.15": [-160, 0, -20], "0.45": [-160, 0, -20], "0.6": [0, 0, 0]}},
            "right_arm": {"rotation": {"0.0": [0, 0, 0], "0.15": [-160, 0, 20], "0.45": [-160, 0, 20], "0.6": [0, 0, 0]}},
            "head": {"rotation": {"0.0": [0, 0, 0], "0.2": [-15, 0, 0], "0.45": [-15, 0, 0], "0.6": [0, 0, 0]}},
        }}
    save_json(os.path.join(A, "animations/entity/summoned_guardian_fairy.animation.json"), {
        "format_version": "1.8.0",
        "animations": {"animation.summoned_guardian_fairy.idle": idle, "animation.summoned_guardian_fairy.heal": heal}})


# =====================================================================================
#  Sombra Viva (layout de skin de jogador 64x64)
# =====================================================================================
def shadow_texture():
    c = Canvas(64, 64)
    g = Canvas(64, 64)
    core = hex_rgba("120A1C")
    mid = hex_rgba("221233")
    rim = hex_rgba("3B1D5A")
    violet = hex_rgba("8A4FC8")
    glow = hex_rgba("E3C4FF")

    def shadow_box(u, v, w, h, d, smoky_bottom=False):
        f = box_faces(u, v, w, h, d)
        for key, rect in f.items():
            x, y, ww, hh = rect
            for j in range(hh):
                for i in range(ww):
                    # Borda violeta escura, miolo quase preto, levemente mais claro no alto
                    edge = i == 0 or i == ww - 1 or (key in ("top", "bottom") and (j == 0 or j == hh - 1))
                    base = mix(mid, core, j / max(1, hh - 1)) if key not in ("top", "bottom") else mid
                    col = rim if edge else base
                    if smoky_bottom and key not in ("top", "bottom") and j >= hh - 3:
                        fade = (j - (hh - 3) + 1) / 4.0
                        if (i + j) % 3 == 0 and j == hh - 1:
                            continue  # buracos de fumaca na barra
                        col = (col[0], col[1], col[2], int(255 * (1 - fade * 0.6)))
                    c.set(x + i, y + j, col)
        return f

    head = shadow_box(0, 0, 8, 8, 8)
    shadow_box(16, 16, 8, 12, 4)                       # corpo
    shadow_box(40, 16, 4, 12, 4)                       # braco direito
    shadow_box(32, 48, 4, 12, 4)                       # braco esquerdo
    shadow_box(0, 16, 4, 12, 4, smoky_bottom=True)     # perna direita
    shadow_box(16, 48, 4, 12, 4, smoky_bottom=True)    # perna esquerda

    # Rosto: olhos em fenda brilhando e um sorriso fino
    fx, fy, _, _ = head["front"]
    for (i, j) in ((1, 3), (2, 3), (2, 4), (5, 3), (6, 3), (5, 4)):
        c.set(fx + i, fy + j, glow)
        g.set(fx + i, fy + j, glow)
    for (i, j) in ((1, 4), (6, 4)):
        c.set(fx + i, fy + j, violet)
        g.set(fx + i, fy + j, violet)
    for i in range(2, 6):
        c.set(fx + i, fy + 6, violet)
        g.set(fx + i, fy + 6, darken(violet, 0.2))
    c.set(fx + 1, fy + 5, violet); c.set(fx + 6, fy + 5, violet)

    # Peito: runa/rachadura violeta (nucleo da sombra)
    bx, by = 20, 20  # frente do corpo
    rune = [(3, 2), (4, 2), (3, 3), (4, 3), (2, 4), (5, 4), (3, 5), (4, 5), (3, 6), (4, 6), (3, 7), (2, 8), (5, 8)]
    for (i, j) in rune:
        col = glow if (i, j) in ((3, 3), (4, 3), (3, 5), (4, 5)) else violet
        c.set(bx + i, by + j, col)
        g.set(bx + i, by + j, col)

    # Veias violeta nos bracos (frente)
    for (u, v) in ((40, 16), (32, 48)):
        f = box_faces(u, v, 4, 12, 4)
        x, y, _, _ = f["front"]
        for j in range(3, 11):
            i = 1 + (j // 3) % 2
            c.set(x + i, y + j, rim if j % 2 else violet)
            if j % 3 == 0:
                g.set(x + i, y + j, darken(violet, 0.3))
        for j in (10, 11):
            for i in range(4):
                c.set(x + i, y + j, mix(core, violet, 0.25))

    # Manto (plano 8x13 em (0,32)): rasgado embaixo
    for (x0, mirror) in ((0, False), (8, True)):
        for j in range(13):
            for i in range(8):
                ii = 7 - i if mirror else i
                tear = (ii * 5 + 3) % 4
                if j >= 13 - tear:
                    continue
                col = mix(rim, core, j / 12.0)
                if ii in (0, 7):
                    col = darken(rim, 0.2)
                c.set(x0 + i, 32 + j, (col[0], col[1], col[2], 230))
    c.noise(0.06, seed=7)
    c.save(os.path.join(A, "textures/entity/living_shadow.png"))
    g.save(os.path.join(A, "textures/entity/living_shadow_glowmask.png"))


def shadow_geo():
    bones = [
        {"name": "waist", "pivot": [0, 12, 0]},
        {"name": "body", "parent": "waist", "pivot": [0, 24, 0],
         "cubes": [{"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]}]},
        {"name": "shroud", "parent": "body", "pivot": [0, 24, 2.1], "rotation": [8, 0, 0],
         "cubes": [{"origin": [-4, 11, 2.1], "size": [8, 13, 0], "uv": [0, 32]}]},
        {"name": "head", "parent": "body", "pivot": [0, 24, 0],
         "cubes": [{"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0]}]},
        {"name": "right_arm", "parent": "body", "pivot": [-5, 22, 0],
         "cubes": [{"origin": [-8, 12, -2], "size": [4, 12, 4], "uv": [40, 16]}]},
        {"name": "left_arm", "parent": "body", "pivot": [5, 22, 0],
         "cubes": [{"origin": [4, 12, -2], "size": [4, 12, 4], "uv": [32, 48]}]},
        {"name": "right_leg", "parent": "waist", "pivot": [-1.9, 12, 0],
         "cubes": [{"origin": [-3.9, 0, -2], "size": [4, 12, 4], "uv": [0, 16]}]},
        {"name": "left_leg", "parent": "waist", "pivot": [1.9, 12, 0],
         "cubes": [{"origin": [-0.1, 0, -2], "size": [4, 12, 4], "uv": [16, 48]}]},
    ]
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.living_shadow", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 2, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    save_json(os.path.join(A, "geo/entity/living_shadow.geo.json"), geo)


def shadow_anim():
    tau = 2 * math.pi
    LI = 2.0
    idle = {
        "loop": True, "animation_length": LI,
        "bones": {
            "body": {"position": sampled(LI, 8, lambda t: [0, -0.3 + 0.3 * math.sin(tau * t / LI), 0])},
            "head": {"rotation": sampled(LI, 8, lambda t: [3 * math.sin(tau * t / LI), 0, 2 * math.sin(tau * t / LI + 1)])},
            "right_arm": {"rotation": sampled(LI, 8, lambda t: [4 * math.sin(tau * t / LI), 0, 4 + 3 * math.sin(tau * t / LI)])},
            "left_arm": {"rotation": sampled(LI, 8, lambda t: [-4 * math.sin(tau * t / LI), 0, -4 - 3 * math.sin(tau * t / LI)])},
            "shroud": {"rotation": sampled(LI, 8, lambda t: [6 + 5 * math.sin(tau * t / LI + 0.8), 0, 0])},
        }}
    LW = 1.0
    walk = {
        "loop": True, "animation_length": LW,
        "bones": {
            "right_leg": {"rotation": sampled(LW, 8, lambda t: [35 * math.sin(tau * t / LW), 0, 0])},
            "left_leg": {"rotation": sampled(LW, 8, lambda t: [-35 * math.sin(tau * t / LW), 0, 0])},
            "right_arm": {"rotation": sampled(LW, 8, lambda t: [-30 * math.sin(tau * t / LW), 0, 4])},
            "left_arm": {"rotation": sampled(LW, 8, lambda t: [30 * math.sin(tau * t / LW), 0, -4])},
            "body": {"position": sampled(LW, 8, lambda t: [0, 0.4 * abs(math.sin(tau * t / LW)), 0]),
                     "rotation": sampled(LW, 8, lambda t: [4, 4 * math.sin(tau * t / LW), 0])},
            "shroud": {"rotation": sampled(LW, 8, lambda t: [22 + 6 * math.sin(2 * tau * t / LW), 0, 0])},
        }}
    attack = {
        "loop": False, "animation_length": 0.4,
        "bones": {
            "right_arm": {"rotation": {"0.0": [0, 0, 0], "0.12": [-120, 15, 0], "0.26": [25, -20, 0], "0.4": [0, 0, 0]}},
            "left_arm": {"rotation": {"0.0": [0, 0, 0], "0.12": [-30, 0, -10], "0.26": [20, 0, 0], "0.4": [0, 0, 0]}},
            "body": {"rotation": {"0.0": [0, 0, 0], "0.12": [0, -15, 0], "0.26": [8, 20, 0], "0.4": [0, 0, 0]}},
            "shroud": {"rotation": {"0.0": [8, 0, 0], "0.2": [30, 0, 0], "0.4": [8, 0, 0]}},
        }}
    save_json(os.path.join(A, "animations/entity/living_shadow.animation.json"), {
        "format_version": "1.8.0",
        "animations": {"animation.living_shadow.idle": idle, "animation.living_shadow.walk": walk,
                       "animation.living_shadow.attack": attack}})


# =====================================================================================
#  Icones de efeito 18x18
# =====================================================================================
def icon_starburst(path):
    c = Canvas(18, 18)
    cx = cy = 8.5
    for y in range(18):
        for x in range(18):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            spike = 4.0 + 4.2 * max(0.0, math.cos(4 * a)) ** 6 + 2.0 * max(0.0, math.cos(4 * a + math.pi / 4)) ** 6
            if r <= spike:
                t = r / spike
                col = mix(hex_rgba("FFFFFF"), hex_rgba("FFD24A"), min(1.0, t * 1.2))
                c.set(x, y, col)
    c.outline(hex_rgba("B07A10"))
    c.save(path)


def icon_rows(path, rows, pal, shade=None):
    c = Canvas(18, 18)
    c.from_rows([r.ljust(18, ".")[:18] for r in rows], pal)
    if shade:
        c.shade_sphere(*shade)
    c.save(path)


def effect_icons():
    d = os.path.join(A, "textures/mob_effect")
    icon_starburst(os.path.join(d, "holy_dazzle.png"))

    # Medo: fantasma de olhos arregalados
    icon_rows(os.path.join(d, "shadow_fear.png"), [
        "..................",
        "......oooooo......",
        ".....oppppppo.....",
        "....oppppppppo....",
        "...opppppppppo....",
        "...opwwppwwpppo...",
        "...opwkppwkpppo...",
        "...opwwppwwpppo...",
        "...oppppppppppo...",
        "...opppkkkppppo...",
        "...oppkkkkkpppo...",
        "...opppkkkppppo...",
        "...oppppppppppo...",
        "...opppppppppppo..",
        "...oppopppoppppo..",
        "...opo.opo.oppo...",
        "...oo...o...oo....",
        "..................",
    ], {"o": hex_rgba("1E0F2E"), "p": hex_rgba("6A3FA0"), "w": hex_rgba("F2E6FF"), "k": hex_rgba("0A0410")},
        shade=(8.5, 8, 8, 0.35, 0.35))

    # Correntes: dois elos entrelacados
    icon_rows(os.path.join(d, "shadow_chains.png"), [
        "..................",
        "..........oooo....",
        ".........ollllo...",
        "........olo..olo..",
        "........ol....lo..",
        "........ol....lo..",
        ".....ooooloo.olo..",
        "....olllolllloo...",
        "...olo..oolloo....",
        "...ol....oo.......",
        "...ol....lo.......",
        "...olo..olo.......",
        "....ollllo........",
        "....oollo.........",
        "....olo...........",
        "...olo............",
        "...oo.............",
        "..................",
    ], {"o": hex_rgba("2A2A36"), "l": hex_rgba("8C8CA6")})

    # Vinculo de Dor: dois coracoes ligados
    icon_rows(os.path.join(d, "pain_link.png"), [
        "..................",
        "..................",
        ".oo.oo......oo.oo.",
        "orrorro....orrorro",
        "orRrrro....orRrrro",
        "orrrrro....orrrrro",
        ".orrro......orrro.",
        "..oro..dd.dd.oro..",
        "...o..d..d..d.o...",
        "......d.....d.....",
        ".......d...d......",
        "........d.d.......",
        ".........d........",
        "..................",
        "..................",
        "..................",
        "..................",
        "..................",
    ], {"o": hex_rgba("4A0A14"), "r": hex_rgba("C0283C"), "R": hex_rgba("FF8A96"), "d": hex_rgba("9B1B30")})

    # Pesadelo: lua crescente com um olho
    icon_rows(os.path.join(d, "nightmare_curse.png"), [
        "..................",
        ".......oooo.......",
        ".....oommmmo......",
        "....ommmmoo.......",
        "...ommmmo.........",
        "...ommmo..........",
        "..ommmo...........",
        "..ommmo....owwo...",
        "..ommmo...owkkwo..",
        "..ommmo...owkkwo..",
        "..ommmo....owwo...",
        "..ommmmo..........",
        "...ommmmo.........",
        "...ommmmmoo.......",
        "....ommmmmmoo.....",
        ".....oommmmmmo....",
        ".......ooooooo....",
        "..................",
    ], {"o": hex_rgba("12061C"), "m": hex_rgba("5B2C8A"), "w": hex_rgba("E3C4FF"), "k": hex_rgba("1A0826")})

    # Possuido: olho violeta com espiral
    icon_rows(os.path.join(d, "shadow_possession.png"), [
        "..................",
        "..................",
        "..................",
        "......oooooo......",
        "....oovvvvvvoo....",
        "...ovvvppppvvvo...",
        "..ovvppkkkkppvvo..",
        ".ovvppkkwwkkppvvo.",
        ".ovvpkkwkkwkkpvvo.",
        ".ovvpkkwkkkkkpvvo.",
        ".ovvppkkwwwkppvvo.",
        "..ovvppkkkkppvvo..",
        "...ovvvppppvvvo...",
        "....oovvvvvvoo....",
        "......oooooo......",
        "..................",
        "..................",
        "..................",
    ], {"o": hex_rgba("1E0F2E"), "v": hex_rgba("8A4FC8"), "p": hex_rgba("B98CFF"), "k": hex_rgba("2B0F3F"),
        "w": hex_rgba("F2E6FF")})

    # Graca da Ressurreicao: ankh dourado
    icon_rows(os.path.join(d, "resurrection_grace.png"), [
        "..................",
        ".......oooo.......",
        "......oggggo......",
        ".....oggoogo......",
        ".....ogo..ogo.....",
        ".....ogo..ogo.....",
        "......ogoogo......",
        "..ooooogggoooooo..",
        "..oGGGGGGGGGGGGo..",
        "..oooooogggooooo..",
        ".......ogGgo......",
        ".......ogGgo......",
        ".......ogGgo......",
        ".......ogGgo......",
        ".......ogGgo......",
        ".......ogggo......",
        ".......ooooo......",
        "..................",
    ], {"o": hex_rgba("8A5A10"), "g": hex_rgba("FFD24A"), "G": hex_rgba("FFF2B0")})


# =====================================================================================
#  Tag de "profanos" (demonios e sombras; mortos-vivos ja estao em minecraft:undead)
# =====================================================================================
def unholy_tag():
    opt = ["summoned_imp", "summoned_demon", "ember_imp", "creeping_shade", "void_bat", "void_crawler", "watcher_eye",
           "abyss_sovereign", "imprisoned_patron", "patron_void_lady", "patron_ember_lord", "living_shadow",
           # mortos-vivos de outros modulos (caso nao estejam em minecraft:undead)
           "lich", "undead_servant", "bone_warrior"]
    values = ["minecraft:vex", "minecraft:ghast", "minecraft:wither"]
    values += [{"id": "isekai_mahou:" + x, "required": False} for x in opt]
    save_json(os.path.join(MERGE, "data/isekai_mahou/tags/entity_type/unholy.json"), {"replace": False, "values": values})


def spawn_eggs():
    for eid in ("summoned_guardian_fairy", "living_shadow"):
        save_json(os.path.join(A, "models/item/%s_spawn_egg.json" % eid), {"parent": "minecraft:item/template_spawn_egg"})


if __name__ == "__main__":
    fairy_texture()
    fairy_geo()
    fairy_anim()
    shadow_texture()
    shadow_geo()
    shadow_anim()
    effect_icons()
    unholy_tag()
    spawn_eggs()
    print("light_shadow assets ok")
