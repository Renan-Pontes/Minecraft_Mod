"""
Gera os assets do modulo fire_water:
  - texturas 64x64 (+ _glowmask) da Salamandra e da Ondina invocadas
  - modelos GeckoLib (.geo.json) e animacoes (.animation.json)
  - icones 18x18 dos efeitos de status
  - modelos dos ovos das invocacoes
Uso (na raiz do projeto):  py -I tools/gen/fire_water.py
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..")
A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
CLEAR = (0, 0, 0, 0)


# =====================================================================================
#  Ajudantes de UV de caixa (layout padrao Bedrock/Blockbench)
# =====================================================================================
def faces(u, v, w, h, d):
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
        col = mix(top, bottom, t)
        for i in range(w):
            c.set(x + i, y + j, col)


def paint_box(c, u, v, w, h, d, base, light=0.18, dark=0.28, top=None, bottom=None):
    f = faces(u, v, w, h, d)
    fill(c, f["top"], top or lighten(base, light))
    fill(c, f["bottom"], bottom or darken(base, dark))
    for k in ("right", "front", "left", "back"):
        vgrad(c, f[k], lighten(base, light * 0.6), darken(base, dark * 0.7))
    return f


def with_alpha(col, a):
    return (col[0], col[1], col[2], a)


def copy_region(src, dst, rect):
    x, y, w, h = rect
    for j in range(h):
        for i in range(w):
            dst.set(x + i, y + j, src.get(x + i, y + j))


def spots(c, rect, cols, count, seed, size=1, edge=None, mark=None):
    """Pintas aleatorias num retangulo. 'mark' recebe as posicoes (para o glowmask)."""
    rnd = random.Random(seed)
    x, y, w, h = rect
    for _ in range(count):
        sx, sy = x + rnd.randrange(max(1, w)), y + rnd.randrange(max(1, h))
        col = cols[rnd.randrange(len(cols))]
        pts = [(sx, sy)]
        if size > 1 and sx + 1 < x + w:
            pts.append((sx + 1, sy))
        for (px, py) in pts:
            c.set(px, py, col)
            if mark is not None:
                mark.append((px, py))
        if edge is not None and sy + 1 < y + h:
            for (px, py) in pts:
                c.set(px, py + 1, edge)


def sh(text):
    """Hash estavel (o hash() do Python muda a cada execucao)."""
    return sum((i + 1) * ord(ch) for i, ch in enumerate(text))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)


# =====================================================================================
#  Salamandra
# =====================================================================================
SAL = {
    "body": hex_rgba("D8431F"), "dark": hex_rgba("8E2412"), "spine": hex_rgba("6E1A0E"),
    "belly": hex_rgba("F4B94A"), "spot": hex_rgba("FFB23A"), "spot_edge": hex_rgba("5A140B"),
    "flame_hi": hex_rgba("FFF1A0"), "flame": hex_rgba("FFC93D"), "flame_lo": hex_rgba("FF7A1F"),
    "mouth": hex_rgba("5A0E0A"), "tongue": hex_rgba("D9555A"), "eye": hex_rgba("FFF27A"), "pupil": hex_rgba("1A0A05"),
    "claw": hex_rgba("2A1208"), "tooth": hex_rgba("F7EBD0"),
}

# (nome, origem, tamanho, uv)
SAL_CUBES = {
    "body": ([-3.5, 2.5, -6], [7, 5, 12], [0, 0]),
    "head": ([-3, 3, -11], [6, 5, 5], [0, 17]),
    "snout": ([-2, 3, -13], [4, 3, 2], [24, 17]),
    "jaw": ([-2.5, 2, -12], [5, 1, 5], [24, 23]),
    "head_crest": ([-0.5, 8, -10], [1, 2, 4], [26, 41]),
    "spike1": ([-0.5, 7.5, -4.5], [1, 2, 2], [42, 30]),
    "spike2": ([-0.5, 7.5, -1.5], [1, 3, 2], [48, 30]),
    "spike3": ([-0.5, 7.5, 1.5], [1, 2, 2], [54, 30]),
    "tail1": ([-2.5, 3, 6], [5, 4, 6], [0, 29]),
    "tail2": ([-1.5, 3.5, 12], [3, 3, 6], [24, 30]),
    "tail3": ([-1, 4, 18], [2, 2, 5], [0, 41]),
    "tail_tip": ([-1.5, 3.5, 22], [3, 3, 3], [14, 41]),
    "leg_fl": ([3, 0, -5.5], [3, 4, 3], [38, 0]),
    "leg_fr": ([-6, 0, -5.5], [3, 4, 3], [50, 0]),
    "leg_bl": ([3, 0, 2.5], [3, 4, 3], [38, 8]),
    "leg_br": ([-6, 0, 2.5], [3, 4, 3], [50, 8]),
}


def sal_faces(name):
    o, s, uv = SAL_CUBES[name]
    return faces(uv[0], uv[1], s[0], s[1], s[2])


def flame_box(c, name):
    f = sal_faces(name)
    for k, rect in f.items():
        if k in ("top",):
            fill(c, rect, SAL["flame_hi"])
        elif k == "bottom":
            fill(c, rect, SAL["flame_lo"])
        else:
            vgrad(c, rect, SAL["flame_hi"], SAL["flame_lo"])
    return f


def salamander():
    c = Canvas(64, 64)
    glow = []  # pixels que brilham alem das partes de fogo
    # Corpo: vermelho com pintas douradas contornadas, barriga amarela segmentada, espinha escura
    o, s, uv = SAL_CUBES["body"]
    f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], SAL["body"], top=SAL["body"], bottom=SAL["belly"])
    x, y, w, h = f["top"]
    vgrad(c, (x, y, w, h), lighten(SAL["body"], 0.12), SAL["body"])
    for j in range(h):
        c.set(x + w // 2, y + j, SAL["spine"])
        c.set(x, y + j, darken(SAL["body"], 0.15))
        c.set(x + w - 1, y + j, darken(SAL["body"], 0.15))
    for side in ("right", "left"):
        x, y, w, h = f[side]
        vgrad(c, (x, y, w, h - 2), lighten(SAL["body"], 0.1), darken(SAL["body"], 0.05))
        for i in range(w):
            c.set(x + i, y, darken(SAL["body"], 0.18))
        vgrad(c, (x, y + h - 2, w, 2), mix(SAL["body"], SAL["belly"], 0.55), SAL["belly"])
    x, y, w, h = f["bottom"]
    for j in range(1, h, 3):
        for i in range(w):
            c.set(x + i, y + j, darken(SAL["belly"], 0.14))
    for k in ("top", "right", "left", "front", "back"):
        rx, ry, rw, rh = f[k]
        if k == "top":
            # duas fileiras de pintas, uma de cada lado da espinha
            spots(c, (rx + 1, ry, 2, rh - 1), [SAL["spot"]], 4, seed=sh(k + "a"), size=2, edge=SAL["spot_edge"], mark=glow)
            spots(c, (rx + rw - 3, ry, 2, rh - 1), [SAL["spot"]], 4, seed=sh(k + "b"), size=2, edge=SAL["spot_edge"], mark=glow)
        else:
            spots(c, (rx, ry + 1, max(1, rw - 1), max(1, rh - 3)), [SAL["spot"]], max(1, rw * rh // 14), seed=sh(k),
                  size=2, edge=SAL["spot_edge"], mark=glow)
    # Cabeca
    o, s, uv = SAL_CUBES["head"]
    f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], SAL["body"], bottom=SAL["belly"])
    spots(c, f["top"], [SAL["spot"]], 3, seed=7, size=2, edge=SAL["spot_edge"], mark=glow)
    x, y, w, h = f["top"]
    c.set(x + w // 2, y + h - 1, SAL["spine"])
    # Olhos nas laterais, perto da frente (3x2 amarelos com pupila em fenda e sobrancelha escura)
    rx, ry, rw, rh = f["right"]
    lx, ly, lw, lh = f["left"]
    for j in (1, 2):
        for i in (1, 2, 3):
            for (px, py) in ((rx + i, ry + j), (lx + rw - 1 - i, ly + j)):
                c.set(px, py, SAL["eye"])
                glow.append((px, py))
        c.set(rx + 2, ry + j, SAL["pupil"])
        c.set(lx + rw - 3, ly + j, SAL["pupil"])
    for i in (1, 2, 3):
        c.set(rx + i, ry, SAL["spine"])
        c.set(lx + rw - 1 - i, ly, SAL["spine"])
    # Focinho com narinas; embaixo = ceu da boca
    o, s, uv = SAL_CUBES["snout"]
    f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], SAL["body"], bottom=SAL["mouth"])
    fx, fy, fw, fh = f["front"]
    c.set(fx, fy, SAL["spine"])
    c.set(fx + fw - 1, fy, SAL["spine"])
    for i in range(fw):
        c.set(fx + i, fy + fh - 1, darken(SAL["body"], 0.35))
    # Mandibula: dentro da boca (lingua), queixo amarelo, dentes na frente
    o, s, uv = SAL_CUBES["jaw"]
    f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], SAL["dark"], top=SAL["mouth"], bottom=SAL["belly"])
    tx, ty, tw, th = f["top"]
    for j in range(th):
        c.set(tx + tw // 2, ty + j, SAL["tongue"])
    c.set(tx + tw // 2 - 1, ty + th - 1, SAL["tongue"])
    c.set(tx + tw // 2 + 1, ty + th - 1, SAL["tongue"])
    fx, fy, fw, fh = f["front"]
    for i in range(fw):
        c.set(fx + i, fy, SAL["tooth"] if i % 2 == 0 else SAL["dark"])
    for side in ("right", "left"):
        x, y, w, h = f[side]
        for i in range(0, w, 2):
            c.set(x + i, y, SAL["tooth"])
    # Cauda: afinando e clareando ate a ponta em chamas
    for name, base in (("tail1", SAL["body"]), ("tail2", mix(SAL["body"], SAL["flame_lo"], 0.3)),
                       ("tail3", mix(SAL["body"], SAL["flame_lo"], 0.6))):
        o, s, uv = SAL_CUBES[name]
        f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], base, bottom=SAL["belly"])
        big = s[0] > 2
        spots(c, f["top"], [SAL["spot"]], max(1, s[0] * s[2] // 8), seed=sh(name), size=2 if big else 1,
              edge=SAL["spot_edge"] if big else None, mark=glow)
        for side in ("right", "left"):
            spots(c, f[side], [SAL["spot"]], max(1, s[1] * s[2] // 10), seed=sh(name + side), mark=glow)
        x, y, w, h = f["top"]
        for j in range(h):
            c.set(x + w // 2, y + j, SAL["spine"])
    # Pernas com garras
    for name in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        o, s, uv = SAL_CUBES[name]
        f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], darken(SAL["body"], 0.08), bottom=SAL["dark"])
        for k in ("right", "front", "left", "back"):
            x, y, w, h = f[k]
            for i in range(w):
                c.set(x + i, y + h - 1, SAL["dark"])
        fx, fy, fw, fh = f["front"]
        for i in range(0, fw, 2):
            c.set(fx + i, fy + fh - 1, SAL["claw"])
        spots(c, f["right"], [SAL["spot"]], 1, seed=sh(name), mark=glow)
        spots(c, f["left"], [SAL["spot"]], 1, seed=sh(name) * 7 + 1, mark=glow)
    # Partes de fogo (brilham): espinhos, crista e ponta da cauda
    for name in ("spike1", "spike2", "spike3", "head_crest", "tail_tip"):
        flame_box(c, name)
    fx, fy, fw, fh = sal_faces("tail_tip")["front"]
    c.set(fx + 1, fy + 1, SAL["flame_hi"])
    c.noise(0.05, seed=11)

    # Glowmask: fogo inteiro + olhos + pintas (mais fracas)
    g = Canvas(64, 64)
    for name in ("spike1", "spike2", "spike3", "head_crest", "tail_tip"):
        for rect in sal_faces(name).values():
            copy_region(c, g, rect)
    eye_px = set()
    hf = sal_faces("head")
    for rect in (hf["right"], hf["left"]):
        x, y, w, h = rect
        for j in range(h):
            for i in range(w):
                eye_px.add((x + i, y + j))
    for (px, py) in glow:
        col = c.get(px, py)
        if col[3] == 0 or col[0] < 120:
            continue
        g.set(px, py, col if (px, py) in eye_px else darken(col, 0.3))
    c.save(os.path.join(A, "textures/entity/summoned_salamander.png"))
    g.save(os.path.join(A, "textures/entity/summoned_salamander_glowmask.png"))


def salamander_model():
    def cube(name, inflate=0.0):
        o, s, uv = SAL_CUBES[name]
        d = {"origin": o, "size": s, "uv": uv}
        if inflate:
            d["inflate"] = inflate
        return d

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 5, 0], "cubes": [cube("body")]},
        {"name": "crest", "parent": "body", "pivot": [0, 7.5, 0],
         "cubes": [cube("spike1"), cube("spike2"), cube("spike3")]},
        {"name": "head", "parent": "body", "pivot": [0, 5.5, -6],
         "cubes": [cube("head"), cube("snout"), cube("head_crest")]},
        {"name": "jaw", "parent": "head", "pivot": [0, 3, -7], "cubes": [cube("jaw")]},
        {"name": "tail1", "parent": "body", "pivot": [0, 5, 6], "cubes": [cube("tail1")]},
        {"name": "tail2", "parent": "tail1", "pivot": [0, 5, 12], "cubes": [cube("tail2")]},
        {"name": "tail3", "parent": "tail2", "pivot": [0, 5, 18], "cubes": [cube("tail3"), cube("tail_tip")]},
        {"name": "leg_fl", "parent": "body", "pivot": [4.5, 4, -4], "cubes": [cube("leg_fl")]},
        {"name": "leg_fr", "parent": "body", "pivot": [-4.5, 4, -4], "cubes": [cube("leg_fr")]},
        {"name": "leg_bl", "parent": "body", "pivot": [4.5, 4, 4], "cubes": [cube("leg_bl")]},
        {"name": "leg_br", "parent": "body", "pivot": [-4.5, 4, 4], "cubes": [cube("leg_br")]},
    ]
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.summoned_salamander", "texture_width": 64, "texture_height": 64,
                            "visible_bounds_width": 3, "visible_bounds_height": 1.5, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": bones,
        }],
    }
    write_json(os.path.join(A, "geo/entity/summoned_salamander.geo.json"), geo)

    def kf(pairs):
        return {f"{t:.4g}" if t != int(t) else f"{t:.1f}": list(v) for t, v in pairs}

    anims = {
        "animation.summoned_salamander.idle": {
            "loop": True, "animation_length": 2.0,
            "bones": {
                "body": {"position": kf([(0, (0, 0, 0)), (1.0, (0, 0.25, 0)), (2.0, (0, 0, 0))])},
                "crest": {"scale": kf([(0, (1, 1, 1)), (0.5, (1, 1.25, 1)), (1.0, (1, 0.9, 1)), (1.5, (1, 1.2, 1)), (2.0, (1, 1, 1))])},
                "jaw": {"rotation": kf([(0, (0, 0, 0)), (1.0, (4, 0, 0)), (2.0, (0, 0, 0))])},
                "tail1": {"rotation": kf([(0, (0, -8, 0)), (1.0, (0, 8, 0)), (2.0, (0, -8, 0))])},
                "tail2": {"rotation": kf([(0, (0, 0, 0)), (0.5, (0, -10, 0)), (1.5, (0, 10, 0)), (2.0, (0, 0, 0))])},
                "tail3": {"rotation": kf([(0, (0, 8, 0)), (1.0, (0, -14, 0)), (2.0, (0, 8, 0))])},
            },
        },
        "animation.summoned_salamander.walk": {
            "loop": True, "animation_length": 0.8,
            "bones": {
                "body": {"rotation": kf([(0, (0, 4, 0)), (0.4, (0, -4, 0)), (0.8, (0, 4, 0))])},
                "leg_fl": {"rotation": kf([(0, (28, 0, 0)), (0.4, (-28, 0, 0)), (0.8, (28, 0, 0))])},
                "leg_br": {"rotation": kf([(0, (28, 0, 0)), (0.4, (-28, 0, 0)), (0.8, (28, 0, 0))])},
                "leg_fr": {"rotation": kf([(0, (-28, 0, 0)), (0.4, (28, 0, 0)), (0.8, (-28, 0, 0))])},
                "leg_bl": {"rotation": kf([(0, (-28, 0, 0)), (0.4, (28, 0, 0)), (0.8, (-28, 0, 0))])},
                "tail1": {"rotation": kf([(0, (0, -12, 0)), (0.4, (0, 12, 0)), (0.8, (0, -12, 0))])},
                "tail2": {"rotation": kf([(0, (0, -6, 0)), (0.2, (0, -14, 0)), (0.6, (0, 14, 0)), (0.8, (0, -6, 0))])},
                "tail3": {"rotation": kf([(0, (0, 10, 0)), (0.4, (0, -16, 0)), (0.8, (0, 10, 0))])},
            },
        },
        "animation.summoned_salamander.breathe": {
            "loop": False, "animation_length": 1.2,
            "bones": {
                "body": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-12, 0, 0)), (1.0, (-12, 0, 0)), (1.2, (0, 0, 0))])},
                "jaw": {"rotation": kf([(0, (0, 0, 0)), (0.15, (38, 0, 0)), (1.0, (38, 0, 0)), (1.2, (0, 0, 0))])},
                "crest": {"scale": kf([(0, (1, 1, 1)), (0.2, (1.1, 1.5, 1.1)), (1.0, (1.1, 1.5, 1.1)), (1.2, (1, 1, 1))])},
                "leg_fl": {"rotation": kf([(0, (0, 0, 0)), (0.2, (12, 0, 0)), (1.0, (12, 0, 0)), (1.2, (0, 0, 0))])},
                "leg_fr": {"rotation": kf([(0, (0, 0, 0)), (0.2, (12, 0, 0)), (1.0, (12, 0, 0)), (1.2, (0, 0, 0))])},
                "tail1": {"rotation": kf([(0, (0, 0, 0)), (0.2, (8, 0, 0)), (1.0, (8, 0, 0)), (1.2, (0, 0, 0))])},
            },
        },
    }
    write_json(os.path.join(A, "animations/entity/summoned_salamander.animation.json"),
               {"format_version": "1.8.0", "animations": anims, "geckolib_format_version": 2})


# =====================================================================================
#  Ondina
# =====================================================================================
UND = {
    "skin": hex_rgba("6FCBF4", 205), "skin_hi": hex_rgba("B9ECFF", 215), "skin_lo": hex_rgba("3B92D6", 215),
    "hair": hex_rgba("2462B8", 235), "hair_hi": hex_rgba("5AA8F0", 235), "hair_lo": hex_rgba("163E86", 235),
    "eye": hex_rgba("EFFFFF"), "iris": hex_rgba("27C6E8"), "mouth": hex_rgba("2D78B8", 230),
    "foam": hex_rgba("EAF9FF", 235), "foam_lo": hex_rgba("BFE6F7", 225), "pearl": hex_rgba("FFFFFF"),
    "gem": hex_rgba("3FE0D0"), "dress": hex_rgba("3FA9FF", 215), "dress_lo": hex_rgba("2A7FD0", 220),
    "tail": hex_rgba("7FD3F7", 185), "tail_end": hex_rgba("C8F1FF", 140),
}

UND_CUBES = {
    "head": ([-4, 22, -4], [8, 8, 8], [0, 0]),
    "hair": ([-4, 22, -4], [8, 8, 8], [32, 0]),
    "crown": ([-3, 30, -1], [6, 2, 1], [26, 37]),
    "hair_tail": ([-3.5, 17, 4], [7, 12, 1], [48, 16]),
    "torso": ([-4, 13, -2], [8, 9, 4], [0, 16]),
    "arm_left": ([4, 12, -1.5], [3, 10, 3], [24, 16]),
    "arm_right": ([-7, 12, -1.5], [3, 10, 3], [36, 16]),
    "waist": ([-4.5, 10, -2.5], [9, 3, 5], [0, 29]),
    "tail1": ([-3.5, 6, -2], [7, 4, 4], [28, 29]),
    "tail2": ([-2.5, 3, -1.5], [5, 3, 3], [0, 37]),
    "tail3": ([-1.5, 0, -1], [3, 3, 2], [16, 37]),
    "fin": ([-3, -1, -0.5], [6, 2, 1], [40, 37]),
}


def und_faces(name):
    o, s, uv = UND_CUBES[name]
    return faces(uv[0], uv[1], s[0], s[1], s[2])


def ripple(c, rect, col, seed, step=3):
    rnd = random.Random(seed)
    x, y, w, h = rect
    for j in range(1, h, step):
        off = rnd.randrange(3)
        for i in range(w):
            if (i + off) % 4 in (0, 1):
                c.set(x + i, y + j, col)


def undine():
    c = Canvas(64, 64)
    # Cabeca: pele de agua, rosto com olhos luminosos
    o, s, uv = UND_CUBES["head"]
    f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], UND["skin"], top=UND["skin_hi"], bottom=UND["skin_lo"])
    fx, fy, fw, fh = f["front"]
    vgrad(c, (fx, fy, fw, fh), UND["skin_hi"], UND["skin"])
    for (ex, ey) in ((1, 4), (5, 4)):
        c.set(fx + ex, fy + ey, UND["eye"]); c.set(fx + ex + 1, fy + ey, UND["iris"])
        c.set(fx + ex, fy + ey + 1, UND["iris"]); c.set(fx + ex + 1, fy + ey + 1, UND["eye"])
        c.set(fx + ex, fy + ey - 1, UND["skin_lo"]); c.set(fx + ex + 1, fy + ey - 1, UND["skin_lo"])
    c.set(fx + 3, fy + 6, UND["mouth"]); c.set(fx + 4, fy + 6, UND["mouth"])
    c.set(fx + 1, fy + 6, lighten(UND["skin"], 0.25)); c.set(fx + 6, fy + 6, lighten(UND["skin"], 0.25))
    # Cabelo (camada externa): cobre topo, laterais e nuca; franja na frente
    o, s, uv = UND_CUBES["hair"]
    hf = faces(uv[0], uv[1], s[0], s[1], s[2])
    fill(c, hf["top"], UND["hair"])
    ripple(c, hf["top"], UND["hair_hi"], 3, step=2)
    for k in ("right", "left", "back"):
        vgrad(c, hf[k], UND["hair_hi"], UND["hair_lo"])
        ripple(c, hf[k], UND["hair"], 5 + len(k), step=2)
    x, y, w, h = hf["front"]
    for i in range(w):
        c.set(x + i, y, UND["hair"]); c.set(x + i, y + 1, UND["hair_hi"] if i % 3 else UND["hair"])
    for j in range(2, 7):
        c.set(x, y + j, UND["hair_lo"]); c.set(x + w - 1, y + j, UND["hair_lo"])
    c.set(x + 1, y + 2, UND["hair"]); c.set(x + w - 2, y + 2, UND["hair"])
    c.set(x + 3, y + 2, UND["hair_hi"])
    # Mecha longa nas costas
    o, s, uv = UND_CUBES["hair_tail"]
    f = faces(uv[0], uv[1], s[0], s[1], s[2])
    for k, rect in f.items():
        vgrad(c, rect, UND["hair"], with_alpha(UND["hair_hi"], 170))
    for k in ("front", "back"):
        x, y, w, h = f[k]
        for i in range(0, w, 2):
            for j in range(h):
                if (i + j) % 5 == 0:
                    c.set(x + i, y + j, UND["hair_hi"])
        for i in range(w):
            if i % 2:
                c.set(x + i, y + h - 1, CLEAR)
    # Coroa de perolas
    o, s, uv = UND_CUBES["crown"]
    f = faces(uv[0], uv[1], s[0], s[1], s[2])
    for rect in f.values():
        fill(c, rect, UND["foam"])
    x, y, w, h = f["front"]
    for i in range(w):
        c.set(x + i, y, UND["pearl"] if i % 2 == 0 else CLEAR)
    c.set(x + w // 2 - 1, y + 1, UND["gem"]); c.set(x + w // 2, y + 1, UND["gem"])
    x, y, w, h = f["back"]
    for i in range(w):
        c.set(x + i, y, UND["pearl"] if i % 2 == 0 else CLEAR)
    # Tronco: vestido de agua com ondas claras e colar de perolas
    o, s, uv = UND_CUBES["torso"]
    f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], UND["dress"], top=UND["skin"], bottom=UND["dress_lo"])
    for k in ("front", "back", "right", "left"):
        x, y, w, h = f[k]
        vgrad(c, (x, y, w, 2), UND["skin"], UND["skin"])
        ripple(c, (x, y + 3, w, h - 3), lighten(UND["dress"], 0.35), 21 + len(k))
    x, y, w, h = f["front"]
    for i in range(1, w - 1):
        c.set(x + i, y + 2, UND["pearl"] if i % 2 else UND["foam_lo"])
    c.set(x + w // 2 - 1, y + 3, UND["gem"]); c.set(x + w // 2, y + 3, UND["gem"])
    # Bracos com pulseiras de espuma
    for name in ("arm_left", "arm_right"):
        o, s, uv = UND_CUBES[name]
        f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], UND["skin"], top=UND["dress"], bottom=UND["skin_hi"])
        for k in ("right", "front", "left", "back"):
            x, y, w, h = f[k]
            fill(c, (x, y, w, 3), UND["dress"])
            fill(c, (x, y + 6, w, 1), UND["foam"])
            fill(c, (x, y + h - 2, w, 2), UND["skin_hi"])
    # Cintura: faixa de espuma
    o, s, uv = UND_CUBES["waist"]
    f = paint_box(c, uv[0], uv[1], s[0], s[1], s[2], UND["foam_lo"], top=UND["dress"], bottom=UND["tail"])
    for k in ("right", "front", "left", "back"):
        x, y, w, h = f[k]
        for i in range(w):
            c.set(x + i, y + (i % 3 == 0), UND["foam"])
            c.set(x + i, y + h - 1, UND["dress"] if i % 2 else UND["foam_lo"])
    # Cauda de agua: fica mais clara e transparente ate a barbatana
    tails = (("tail1", UND["dress"], UND["tail"]), ("tail2", UND["tail"], mix(UND["tail"], UND["tail_end"], 0.5)),
             ("tail3", mix(UND["tail"], UND["tail_end"], 0.5), UND["tail_end"]))
    for name, top, bot in tails:
        o, s, uv = UND_CUBES[name]
        f = faces(uv[0], uv[1], s[0], s[1], s[2])
        fill(c, f["top"], top)
        fill(c, f["bottom"], bot)
        for k in ("right", "front", "left", "back"):
            vgrad(c, f[k], top, bot)
            ripple(c, f[k], lighten(top, 0.3), len(name) + len(k), step=2)
    o, s, uv = UND_CUBES["fin"]
    f = faces(uv[0], uv[1], s[0], s[1], s[2])
    for k, rect in f.items():
        vgrad(c, rect, UND["tail_end"], with_alpha(UND["skin_hi"], 120))
    x, y, w, h = f["front"]
    for i in range(0, w, 2):
        c.set(x + i, y + h - 1, CLEAR)
    x, y, w, h = f["back"]
    for i in range(0, w, 2):
        c.set(x + i, y + h - 1, CLEAR)
    c.noise(0.04, seed=23)

    # Glowmask: olhos, coroa, joias e brilhos no cabelo
    g = Canvas(64, 64)
    hf2 = und_faces("head")["front"]
    x, y, w, h = hf2
    for j in range(h):
        for i in range(w):
            px = c.get(x + i, y + j)
            if px == UND["eye"] or px == UND["iris"] or (px[1] > 190 and px[2] > 220 and px[0] < 80 and px[3] == 255):
                g.set(x + i, y + j, px)
    for rect in und_faces("crown").values():
        copy_region(c, g, rect)
    for name in ("torso",):
        x, y, w, h = und_faces(name)["front"]
        for j in range(h):
            for i in range(w):
                px = c.get(x + i, y + j)
                if px[3] == 255 and (px[0] > 230 or (px[1] > 200 and px[0] < 90)):
                    g.set(x + i, y + j, px)
    rnd = random.Random(99)
    for k, rect in und_faces("hair").items():
        if k in ("bottom", "front"):
            continue
        x, y, w, h = rect
        for _ in range(3):
            sx, sy = x + rnd.randrange(w), y + rnd.randrange(h)
            g.set(sx, sy, hex_rgba("9FE6FF"))
            c.set(sx, sy, hex_rgba("9FE6FF", 235))
    c.save(os.path.join(A, "textures/entity/summoned_undine.png"))
    g.save(os.path.join(A, "textures/entity/summoned_undine_glowmask.png"))


def undine_model():
    def cube(name, inflate=0.0):
        o, s, uv = UND_CUBES[name]
        d = {"origin": o, "size": s, "uv": uv}
        if inflate:
            d["inflate"] = inflate
        return d

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 12, 0], "cubes": [cube("torso"), cube("waist")]},
        {"name": "head", "parent": "body", "pivot": [0, 22, 0], "cubes": [cube("head"), cube("hair", 0.5), cube("crown")]},
        {"name": "hair_tail", "parent": "head", "pivot": [0, 29, 4.5], "cubes": [cube("hair_tail")]},
        {"name": "arm_left", "parent": "body", "pivot": [5.5, 21, 0], "cubes": [cube("arm_left")]},
        {"name": "arm_right", "parent": "body", "pivot": [-5.5, 21, 0], "cubes": [cube("arm_right")]},
        {"name": "tail1", "parent": "body", "pivot": [0, 10, 0], "cubes": [cube("tail1")]},
        {"name": "tail2", "parent": "tail1", "pivot": [0, 6, 0], "cubes": [cube("tail2")]},
        {"name": "tail3", "parent": "tail2", "pivot": [0, 3, 0], "cubes": [cube("tail3"), cube("fin")]},
    ]
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.summoned_undine", "texture_width": 64, "texture_height": 64,
                            "visible_bounds_width": 2, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
            "bones": bones,
        }],
    }
    write_json(os.path.join(A, "geo/entity/summoned_undine.geo.json"), geo)

    def kf(pairs):
        return {f"{t:.4g}" if t != int(t) else f"{t:.1f}": list(v) for t, v in pairs}

    anims = {
        "animation.summoned_undine.idle": {
            "loop": True, "animation_length": 3.0,
            "bones": {
                "body": {"position": kf([(0, (0, 0, 0)), (1.5, (0, 1.2, 0)), (3.0, (0, 0, 0))])},
                "arm_left": {"rotation": kf([(0, (-5, 0, 0)), (1.5, (5, 0, 0)), (3.0, (-5, 0, 0))])},
                "arm_right": {"rotation": kf([(0, (5, 0, 0)), (1.5, (-5, 0, 0)), (3.0, (5, 0, 0))])},
                "hair_tail": {"rotation": kf([(0, (8, 0, 0)), (1.5, (16, 0, 0)), (3.0, (8, 0, 0))])},
                "tail1": {"rotation": kf([(0, (5, 0, 0)), (1.5, (-5, 0, 0)), (3.0, (5, 0, 0))])},
                "tail2": {"rotation": kf([(0, (-5, 0, 0)), (0.75, (8, 0, 0)), (2.25, (-8, 0, 0)), (3.0, (-5, 0, 0))])},
                "tail3": {"rotation": kf([(0, (10, 0, 0)), (1.5, (-12, 0, 0)), (3.0, (10, 0, 0))])},
            },
        },
        "animation.summoned_undine.move": {
            "loop": True, "animation_length": 1.5,
            "bones": {
                "body": {"rotation": kf([(0, (12, 0, 0)), (0.75, (15, 0, 0)), (1.5, (12, 0, 0))]),
                         "position": kf([(0, (0, 0, 0)), (0.75, (0, 0.6, 0)), (1.5, (0, 0, 0))])},
                "arm_left": {"rotation": kf([(0, (25, 0, 0)), (0.75, (32, 0, 0)), (1.5, (25, 0, 0))])},
                "arm_right": {"rotation": kf([(0, (25, 0, 0)), (0.75, (32, 0, 0)), (1.5, (25, 0, 0))])},
                "hair_tail": {"rotation": kf([(0, (30, 0, 0)), (0.75, (40, 0, 0)), (1.5, (30, 0, 0))])},
                "tail1": {"rotation": kf([(0, (15, 0, 0)), (0.75, (5, 0, 0)), (1.5, (15, 0, 0))])},
                "tail2": {"rotation": kf([(0, (10, 0, 0)), (0.375, (22, 0, 0)), (1.125, (0, 0, 0)), (1.5, (10, 0, 0))])},
                "tail3": {"rotation": kf([(0, (22, 0, 0)), (0.75, (0, 0, 0)), (1.5, (22, 0, 0))])},
            },
        },
        "animation.summoned_undine.cast": {
            "loop": False, "animation_length": 0.7,
            "bones": {
                "body": {"rotation": kf([(0, (0, 0, 0)), (0.2, (8, 0, 0)), (0.7, (0, 0, 0))])},
                "arm_right": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-90, 0, 0)), (0.5, (-90, 0, 0)), (0.7, (0, 0, 0))])},
                "arm_left": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-80, 0, 0)), (0.5, (-80, 0, 0)), (0.7, (0, 0, 0))])},
            },
        },
        "animation.summoned_undine.heal": {
            "loop": False, "animation_length": 1.0,
            "bones": {
                "body": {"position": kf([(0, (0, 0, 0)), (0.4, (0, 1.5, 0)), (1.0, (0, 0, 0))])},
                "arm_right": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-160, 0, 0)), (0.7, (-160, 0, 0)), (1.0, (0, 0, 0))])},
                "arm_left": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-160, 0, 0)), (0.7, (-160, 0, 0)), (1.0, (0, 0, 0))])},
                "head": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-12, 0, 0)), (0.7, (-12, 0, 0)), (1.0, (0, 0, 0))])},
            },
        },
    }
    write_json(os.path.join(A, "animations/entity/summoned_undine.animation.json"),
               {"format_version": "1.8.0", "animations": anims, "geckolib_format_version": 2})


# =====================================================================================
#  Icones dos efeitos (18x18)
# =====================================================================================
FLAME_ROWS = [
    "..................",
    "........o.........",
    "........oo........",
    ".......oom....o...",
    ".......omm...oo...",
    "...o..oommo..om...",
    "...oo.ommmo.oom...",
    "..oom.omcmmoomm...",
    "..ommoomccmmomm...",
    "..ommmomccmmmmo...",
    "..ommmmcccmmmmo...",
    "...ommmcccmmmo....",
    "...ommcccccmmo....",
    "....omcccccmo.....",
    "....ommcccmmo.....",
    ".....ommmmmo......",
    "......ooooo.......",
    "..................",
]


def icon_ember():
    c = Canvas(18, 18)
    c.from_rows(FLAME_ROWS[2:16], {"o": hex_rgba("E8461A"), "m": hex_rgba("FF9A2E"), "c": hex_rgba("FFE27A")})
    # carvao em brasa na base, com rachaduras incandescentes
    for y in range(18):
        for x in range(18):
            if ((x + 0.5 - 8.5) / 5.5) ** 2 + ((y + 0.5 - 14.5) / 3.0) ** 2 <= 1.0:
                c.set(x, y, hex_rgba("3A1A12"))
    for (x, y) in ((5, 14), (6, 15), (8, 14), (9, 13), (10, 15), (11, 14), (7, 13), (12, 15)):
        c.set(x, y, hex_rgba("FF6A1F"))
    for (x, y) in ((8, 15), (10, 14), (6, 14)):
        c.set(x, y, hex_rgba("FFD34D"))
    c.outline(hex_rgba("2A0E08"))
    c.save(os.path.join(A, "textures/mob_effect/lingering_ember.png"))


def icon_azure():
    c = Canvas(18, 18)
    c.from_rows(FLAME_ROWS, {"o": hex_rgba("1E6FD8"), "m": hex_rgba("3FC8FF"), "c": hex_rgba("D8FBFF")})
    c.outline(hex_rgba("0B2A5A"))
    c.save(os.path.join(A, "textures/mob_effect/azure_flame.png"))


def icon_bubble():
    c = Canvas(18, 18)
    c.circle(9, 9, 7.5, hex_rgba("9BE7FF", 150))
    c.circle(9, 9, 6.2, hex_rgba("CFF5FF", 90))
    c.shade_sphere(9, 9, 7.5, light=0.5, dark=0.2)
    # contorno da bolha
    for y in range(18):
        for x in range(18):
            d = math.hypot(x + 0.5 - 9, y + 0.5 - 9)
            if 6.6 <= d <= 7.6:
                c.set(x, y, hex_rgba("5AC8F0"))
    for (x, y) in ((5, 5), (6, 4), (5, 6), (7, 4)):
        c.set(x, y, hex_rgba("FFFFFF"))
    c.set(12, 12, hex_rgba("E8FBFF"))
    c.circle(14.5, 3.5, 1.4, hex_rgba("9BE7FF", 200))
    c.save(os.path.join(A, "textures/mob_effect/bubble_prison.png"))


def icon_undertow():
    c = Canvas(18, 18)
    deep, mid, foam = hex_rgba("1E5AA8"), hex_rgba("3FA9FF"), hex_rgba("E6FAFF")
    rows = [
        "..................",
        "..................",
        "....ffff..........",
        "..ffmmmmff........",
        ".fmmddddmmf.......",
        ".fmd....dmmf......",
        "..f......dmmf..f..",
        "..........dmmffmf.",
        "...........dmmmmf.",
        "....ffff....ddmmf.",
        "..ffmmmmff....df..",
        ".fmmddddmmf.......",
        ".fmd....dmmf......",
        "..f......dmmf..f..",
        "..........dmmffmf.",
        "...........dmmmmf.",
        ".............ddf..",
        "..................",
    ]
    c.from_rows(rows, {"d": deep, "m": mid, "f": foam})
    c.outline(hex_rgba("0E2E5E"))
    c.save(os.path.join(A, "textures/mob_effect/undertow.png"))


def icon_choke():
    c = Canvas(18, 18)
    # gota grande
    for y in range(18):
        for x in range(18):
            px, py = x + 0.5, y + 0.5
            if py >= 8:
                inside = math.hypot(px - 8, py - 11.5) <= 5.0
            else:
                half = (py - 2) / 6.0 * 5.0
                inside = py >= 2 and abs(px - 8) <= half
            if inside:
                c.set(x, y, hex_rgba("2F7FD8"))
    c.shade_sphere(8, 11.5, 5.2, light=0.5, dark=0.4)
    c.set(6, 10, hex_rgba("CFF2FF")); c.set(6, 11, hex_rgba("CFF2FF")); c.set(7, 9, hex_rgba("CFF2FF"))
    # bolhas subindo
    for (cx, cy, r) in ((14.5, 6.5, 1.6), (15.5, 2.5, 1.1), (13.0, 11.0, 1.0)):
        for y in range(18):
            for x in range(18):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r - 0.8 <= d <= r + 0.2:
                    c.set(x, y, hex_rgba("9BE7FF"))
    c.outline(hex_rgba("0E2E5E"))
    c.save(os.path.join(A, "textures/mob_effect/water_choke.png"))


def icon_mirror():
    c = Canvas(18, 18)
    frame, face, shine = hex_rgba("2A7FD0"), hex_rgba("8FE0FF"), hex_rgba("FFFFFF")
    for y in range(18):
        for x in range(18):
            d = ((x + 0.5 - 9) / 6.2) ** 2 + ((y + 0.5 - 9) / 7.6) ** 2
            if d <= 1.0:
                c.set(x, y, frame if d > 0.7 else face)
    for y in range(18):
        for x in range(18):
            p = c.get(x, y)
            if p == face:
                c.set(x, y, mix(lighten(face, 0.35), darken(face, 0.2), y / 17))
    for i in range(4):
        c.set(6 + i, 5 + i, shine)
        c.set(7 + i, 5 + i, lighten(face, 0.6))
    c.set(11, 12, shine)
    c.outline(hex_rgba("0E2E5E"))
    c.save(os.path.join(A, "textures/mob_effect/water_mirror.png"))


def icon_phoenix():
    c = Canvas(18, 18)
    gold, orange, red, hi = hex_rgba("FFC23D"), hex_rgba("FF7A1F"), hex_rgba("D8331A"), hex_rgba("FFF1A0")
    rows = [
        "..................",
        "........h.........",
        ".......hgh........",
        "o......ggg......o.",
        "oo.....gog.....oo.",
        ".oo....ggg....oo..",
        ".ooo..ggggg..ooo..",
        "..oooggogoggooo...",
        "..rooogggggooor...",
        "...rroogggggoorr..",
        "....rrooggoorr....",
        ".....rr.ggg.rr....",
        "........gog.......",
        ".......o.g.o......",
        "......o..o..o.....",
        ".....r...o...r....",
        ".........r........",
        "..................",
    ]
    c.from_rows(rows, {"g": gold, "o": orange, "r": red, "h": hi})
    c.outline(hex_rgba("4A1408"))
    c.save(os.path.join(A, "textures/mob_effect/phoenix_blessing.png"))


def spawn_eggs():
    """Modelos dos ovos das invocacoes (cores definidas no Java)."""
    for name in ("summoned_salamander", "summoned_undine"):
        write_json(os.path.join(A, "models/item/%s_spawn_egg.json" % name),
                   {"parent": "minecraft:item/template_spawn_egg"})


def main():
    salamander()
    salamander_model()
    undine()
    undine_model()
    spawn_eggs()
    icon_ember()
    icon_azure()
    icon_bubble()
    icon_undertow()
    icon_choke()
    icon_mirror()
    icon_phoenix()
    print("fire_water: assets gerados em", os.path.abspath(A))


if __name__ == "__main__":
    main()
