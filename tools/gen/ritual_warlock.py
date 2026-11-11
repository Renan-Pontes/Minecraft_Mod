"""
Gera os assets e dados do modulo ritual_warlock (Pacto no Circulo de Giz).
Uso (na raiz do projeto):  py -I tools/gen/ritual_warlock.py

Saidas:
  assets/isekai_mahou/textures/{item,block,entity}   giz, adaga, altar, runas de giz, 3 Patronos (+ glowmask)
  assets/isekai_mahou/models/{item,block}, blockstates
  assets/isekai_mahou/geo/entity, animations/entity   modelos GeckoLib dos Patronos
  data/isekai_mahou/{loot_table,recipe,tags/item}      drops, receitas e tags de oferenda
  src/main/merge/ritual_warlock/...                    mineable/pickaxe, can_breathe_under_water
  src/main/lang/ritual_warlock/{pt_br,en_us}.json      traducoes
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, lighten, darken, mix  # noqa: E402

RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "isekai_mahou")
DATA = os.path.join(RES, "data", "isekai_mahou")
LANG = os.path.join(ROOT, "src", "main", "lang", "ritual_warlock")
MERGE = os.path.join(ROOT, "src", "main", "merge", "ritual_warlock")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def tex(sub, name):
    return os.path.join(ASSETS, "textures", sub, name + ".png")


# =====================================================================================================
#  GIZ RITUAL (bloco): 8 runas, branco e vermelho (sangue)
# =====================================================================================================
RUNES = [
    # 0: Algiz (tridente)
    [(8, 13, 8, 3), (8, 7, 4, 3), (8, 7, 12, 3)],
    # 1: cruz solar (circulo + cruz) - circulo desenhado a parte
    [(8, 3, 8, 13), (3, 8, 13, 8)],
    # 2: triangulo com ponto
    [(8, 3, 3, 12), (8, 3, 13, 12), (3, 12, 13, 12)],
    # 3: Othala (losango com pernas)
    [(8, 3, 4, 7), (8, 3, 12, 7), (4, 7, 8, 11), (12, 7, 8, 11), (8, 11, 4, 13), (8, 11, 12, 13)],
    # 4: estrela de 5 pontas
    [(8, 2, 5, 13), (8, 2, 11, 13), (3, 6, 13, 6), (3, 6, 11, 13), (13, 6, 5, 13)],
    # 5: lua crescente + linha (desenhada a parte)
    [(11, 3, 11, 13)],
    # 6: olho (amendoa + pupila) - desenhado a parte
    [],
    # 7: Sowilo (raio)
    [(11, 3, 6, 7), (6, 7, 10, 9), (10, 9, 5, 13)],
]


def chalk_rune(idx, blood):
    rnd = random.Random(100 + idx)
    c = Canvas(16, 16)
    if blood:
        main, soft, core = rgb("#a3121c"), rgb("#6e0a12"), rgb("#e0303a")
    else:
        main, soft, core = rgb("#f2efe6"), rgb("#bdb8ad"), rgb("#ffffff")
    for (x0, y0, x1, y1) in RUNES[idx]:
        c.line(x0, y0, x1, y1, main)
    if idx == 1:
        for a in range(0, 360, 12):
            c.set(int(round(8 + 4.6 * math.cos(math.radians(a)))), int(round(8 + 4.6 * math.sin(math.radians(a)))), main)
    if idx == 5:
        for a in range(70, 300, 8):
            c.set(int(round(8 + 4.5 * math.cos(math.radians(a)))), int(round(8 + 4.5 * math.sin(math.radians(a)))), main)
        for a in range(100, 270, 10):
            c.set(int(round(9 + 3 * math.cos(math.radians(a)))), int(round(8 + 3 * math.sin(math.radians(a)))), soft)
    if idx == 6:
        for x in range(3, 14):
            t = (x - 8) / 5.5
            dy = int(round(3 * math.sqrt(max(0.0, 1 - t * t))))
            c.set(x, 8 - dy, main)
            c.set(x, 8 + dy, main)
        c.rect(7, 7, 8, 8, core if blood else main)
        c.set(6, 7, soft)
    # Tracos com falhas (giz arrastado) e realce no meio do traco.
    for y in range(16):
        for x in range(16):
            p = c.get(x, y)
            if p[3] == 0:
                continue
            r = rnd.random()
            if r < 0.12:
                c.set(x, y, soft)
            elif r < 0.2:
                c.set(x, y, core)
    # Borda tracejada (liga os blocos do anel) + poeira.
    for i in range(0, 16, 3):
        for (x, y) in ((i, 0), (i + 1, 0), (i, 15), (i + 1, 15), (0, i), (0, i + 1), (15, i), (15, i + 1)):
            if 0 <= x < 16 and 0 <= y < 16 and rnd.random() < 0.85:
                c.set(x, y, soft)
    for _ in range(10):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if c.get(x, y)[3] == 0:
            c.set(x, y, soft)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, main)
    return c


def gen_chalk():
    for i in range(8):
        chalk_rune(i, False).save(tex("block", "ritual_chalk_%d" % i))
        chalk_rune(i, True).save(tex("block", "ritual_chalk_red_%d" % i))
    write_json(os.path.join(ASSETS, "models", "block", "ritual_chalk_base.json"), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {"particle": "#chalk"},
        "elements": [{
            "from": [0, 0.25, 0], "to": [16, 0.25, 16], "shade": False,
            "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#chalk"},
                "down": {"uv": [0, 16, 16, 0], "texture": "#chalk"},
            },
        }],
    })
    variants = {}
    for blood in (False, True):
        for i in range(8):
            name = ("ritual_chalk_red_%d" if blood else "ritual_chalk_%d") % i
            write_json(os.path.join(ASSETS, "models", "block", name + ".json"), {
                "parent": "isekai_mahou:block/ritual_chalk_base",
                "render_type": "minecraft:cutout",
                "textures": {"chalk": "isekai_mahou:block/" + name, "particle": "isekai_mahou:block/" + name},
            })
            key = "blood=%s,rune=%d" % ("true" if blood else "false", i)
            variants[key] = [{"model": "isekai_mahou:block/" + name, "y": r} for r in (0, 90, 180, 270)]
    write_json(os.path.join(ASSETS, "blockstates", "ritual_chalk.json"), {"variants": variants})


# =====================================================================================================
#  ALTAR DE OFERENDAS
# =====================================================================================================
STONE_D, STONE_M, STONE_L = rgb("#1c1a20"), rgb("#2c2932"), rgb("#423d48")
RUNE_R, RUNE_G = rgb("#b3202a"), rgb("#ff4a3a")


def stone(w, h, seed, rough=True):
    rnd = random.Random(seed)
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            t = rnd.random()
            col = STONE_M if t < 0.6 else (STONE_D if t < 0.82 else STONE_L)
            c.set(x, y, col)
    if rough:
        # Juntas de tijolos polidos.
        for y in range(0, h, 8):
            for x in range(w):
                c.set(x, y, STONE_D)
        for y in range(h):
            off = 0 if (y // 8) % 2 == 0 else 4
            for x in range(off, w, 8):
                c.set(x, y, STONE_D)
    return c


def gen_altar():
    # Lado do tampo/base: pedra negra com friso dourado-sangue.
    side = stone(16, 16, 1)
    for x in range(16):
        side.set(x, 3, rgb("#5a1218"))
        side.set(x, 12, rgb("#5a1218"))
    for x in range(1, 16, 3):
        side.set(x, 6, RUNE_R)
        side.set(x + 1 if x + 1 < 16 else x, 9, RUNE_R)
    side.save(tex("block", "offering_altar_side"))

    # Coluna: runas vermelhas brilhantes entalhadas.
    col = stone(16, 16, 2, rough=False)
    for y in range(16):
        col.set(0, y, STONE_D)
        col.set(15, y, STONE_D)
    glyph = [(7, 2, 7, 13), (4, 4, 10, 4), (5, 8, 9, 8), (4, 11, 7, 13), (10, 11, 7, 13)]
    for (x0, y0, x1, y1) in glyph:
        col.line(x0, y0, x1, y1, RUNE_R)
    for (x, y) in ((7, 6), (7, 10), (5, 4), (9, 4)):
        col.set(x, y, RUNE_G)
    col.save(tex("block", "offering_altar_column"))

    # Tampo: circulo ritual vermelho com pentagrama.
    top = stone(16, 16, 3, rough=False)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 6.2 <= d <= 7.1:
                top.set(x, y, RUNE_R)
            elif d < 6.2:
                top.set(x, y, darken(top.get(x, y), 0.25))
    pts = [(8 + 5.6 * math.cos(math.radians(-90 + 144 * i)), 8 + 5.6 * math.sin(math.radians(-90 + 144 * i))) for i in range(6)]
    for i in range(5):
        top.line(int(round(pts[i][0])), int(round(pts[i][1])), int(round(pts[i + 1][0])), int(round(pts[i + 1][1])), RUNE_G)
    top.set(7, 7, RUNE_G)
    top.set(8, 8, RUNE_G)
    top.save(tex("block", "offering_altar_top"))

    bottom = stone(16, 16, 4)
    bottom.save(tex("block", "offering_altar_bottom"))

    spike = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            t = y / 15.0
            spike.set(x, y, mix(rgb("#d9cfc0"), rgb("#4a3a32"), t))
    spike.noise(0.08, 5)
    spike.save(tex("block", "offering_altar_spike"))

    def face(u0, v0, u1, v1, t, cull=None):
        f = {"uv": [u0, v0, u1, v1], "texture": t}
        if cull:
            f["cullface"] = cull
        return f

    def box(fr, to, side_t, top_t, bot_t, cull_bottom=False):
        x0, y0, z0 = fr
        x1, y1, z1 = to
        # UV laterais seguem a altura real do elemento.
        sv0, sv1 = 16 - y1, 16 - y0
        return {"from": fr, "to": to, "faces": {
            "north": face(x0, sv0, x1, sv1, side_t), "south": face(x0, sv0, x1, sv1, side_t),
            "west": face(z0, sv0, z1, sv1, side_t), "east": face(z0, sv0, z1, sv1, side_t),
            "up": face(x0, z0, x1, z1, top_t), "down": face(x0, z0, x1, z1, bot_t, "down" if cull_bottom else None)}}

    elements = [
        box([1, 0, 1], [15, 3, 15], "#side", "#bottom", "#bottom", True),
        box([3, 3, 3], [13, 10, 13], "#column", "#bottom", "#bottom"),
        box([1, 10, 1], [15, 13, 15], "#side", "#top", "#bottom"),
    ]
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        elements.append(box([x, 13, z], [x + 2, 16, z + 2], "#spike", "#spike", "#spike"))
    write_json(os.path.join(ASSETS, "models", "block", "offering_altar.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "isekai_mahou:block/offering_altar_side",
            "side": "isekai_mahou:block/offering_altar_side",
            "column": "isekai_mahou:block/offering_altar_column",
            "top": "isekai_mahou:block/offering_altar_top",
            "bottom": "isekai_mahou:block/offering_altar_bottom",
            "spike": "isekai_mahou:block/offering_altar_spike",
        },
        "elements": elements,
    })
    write_json(os.path.join(ASSETS, "blockstates", "offering_altar.json"),
               {"variants": {"": {"model": "isekai_mahou:block/offering_altar"}}})
    write_json(os.path.join(ASSETS, "models", "item", "offering_altar.json"), {"parent": "isekai_mahou:block/offering_altar"})


# =====================================================================================================
#  ITENS
# =====================================================================================================
def gen_items():
    # Giz Ritual: bastao branco diagonal com faixa de pano vermelho.
    c = Canvas(16, 16)
    W1, W2, W3 = rgb("#ffffff"), rgb("#e6e1d6"), rgb("#b9b2a4")
    for i in range(10):
        x, y = 3 + i, 12 - i
        c.set(x, y, W2)
        c.set(x + 1, y, W1)
        c.set(x, y - 1, W1)
        c.set(x + 1, y + 1, W3)
        c.set(x - 1, y, W3)
    for i in range(3):
        x, y = 3 + i, 12 - i
        for (dx, dy) in ((0, 0), (1, 0), (0, -1), (1, 1), (-1, 0)):
            c.set(x + dx, y + dy, rgb("#8e1520") if (dx + dy) % 2 == 0 else rgb("#5e0c14"))
    c.set(13, 2, W1)
    c.set(14, 2, W2)
    c.set(13, 1, W2)
    c.outline(rgb("#2a2420"))
    for (x, y) in ((1, 14), (3, 15), (0, 12)):
        c.set(x, y, rgb("#d8d3c8"))
    c.save(tex("item", "ritual_chalk_item"))
    write_json(os.path.join(ASSETS, "models", "item", "ritual_chalk_item.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/ritual_chalk_item"}})

    # Adaga Ritual: lamina ondulada escura com runas vermelhas, cabo de osso, pomo de rubi.
    d = Canvas(16, 16)
    B1, B2, B3 = rgb("#5a5866"), rgb("#8d8a9c"), rgb("#2c2a34")
    blade = [(5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (12, 3), (13, 2)]
    for k, (x, y) in enumerate(blade):
        wob = 1 if k % 3 == 1 else 0
        d.set(x, y, B1)
        d.set(x + 1, y, B2)
        d.set(x, y - 1 - wob, B2 if wob else B1)
        d.set(x - 1 + wob, y + 1, B3)
    d.set(14, 1, B2)
    for (x, y) in ((8, 7), (10, 5), (12, 3)):
        d.set(x, y, rgb("#d4232f"))
    # Guarda
    for (x, y) in ((3, 9), (4, 10), (5, 11), (6, 12), (2, 8), (7, 13)):
        d.set(x, y, rgb("#3a1418"))
    d.set(4, 11, rgb("#7a1a22"))
    d.set(5, 12, rgb("#7a1a22"))
    # Cabo de osso
    for (x, y) in ((3, 12), (2, 13), (1, 14)):
        d.set(x, y, rgb("#e3d9c4"))
        d.set(x + 1, y, rgb("#b8ab92"))
    d.set(0, 15, rgb("#e0303a"))
    d.set(1, 15, rgb("#8e1520"))
    d.set(0, 14, rgb("#8e1520"))
    d.outline(rgb("#120e12"))
    d.save(tex("item", "ritual_dagger"))
    write_json(os.path.join(ASSETS, "models", "item", "ritual_dagger.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "isekai_mahou:item/ritual_dagger"}})

    for pid in ("patron_ember_lord", "patron_void_lady", "patron_drowned_king"):
        write_json(os.path.join(ASSETS, "models", "item", pid + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================================
#  GECKOLIB: construtor de modelo com UV por face + pintura procedural
# =====================================================================================================
FACES = ("north", "south", "east", "west", "up", "down")


class Geo:
    def __init__(self, ident, yoff=0.0):
        self.ident = ident
        self.yoff = yoff
        self.bones = []
        self.cubes = []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name, "pivot": [pivot[0], pivot[1] + self.yoff, pivot[2]]}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, mat, faces=None, rotation=None, pivot=None, inflate=None):
        self.cubes.append(dict(bone=bone, origin=[origin[0], origin[1] + self.yoff, origin[2]], size=list(size),
                               mat=mat, faces=faces or {}, rotation=rotation,
                               pivot=None if pivot is None else [pivot[0], pivot[1] + self.yoff, pivot[2]], inflate=inflate))

    @staticmethod
    def face_dims(size, f):
        w, h, d = [max(1, int(math.ceil(v))) for v in size]
        return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}[f]

    def pack(self):
        rects = []
        for ci, c in enumerate(self.cubes):
            for f in FACES:
                w, h = self.face_dims(c["size"], f)
                rects.append([ci, f, w, h])
        rects.sort(key=lambda r: (-r[3], -r[2]))
        for tw, th in ((64, 64), (128, 64), (128, 128), (256, 128), (256, 256)):
            placed = {}
            x = y = shelf = 0
            ok = True
            for ci, f, w, h in rects:
                if x + w > tw:
                    x = 0
                    y += shelf
                    shelf = 0
                if y + h > th:
                    ok = False
                    break
                placed[(ci, f)] = (x, y, w, h)
                x += w
                shelf = max(shelf, h)
            if ok:
                self.tw, self.th, self.uv = tw, th, placed
                return
        raise SystemExit("modelo grande demais: " + self.ident)

    def build(self, painter):
        self.pack()
        cv = Canvas(self.tw, self.th)
        gl = Canvas(self.tw, self.th)
        bones = {b["name"]: dict(b, cubes=[]) for b in self.bones}
        for ci, c in enumerate(self.cubes):
            uv = {}
            for f in FACES:
                x, y, w, h = self.uv[(ci, f)]
                uv[f] = {"uv": [x, y], "uv_size": [w, h]}
                painter(c["faces"].get(f, c["mat"]), f, cv, gl, x, y, w, h, random.Random(ci * 31 + FACES.index(f)))
            cube = {"origin": c["origin"], "size": c["size"], "uv": uv}
            if c["rotation"]:
                cube["rotation"] = c["rotation"]
                cube["pivot"] = c["pivot"] or c["origin"]
            if c["inflate"]:
                cube["inflate"] = c["inflate"]
            bones[c["bone"]]["cubes"].append(cube)
        out_bones = []
        for b in self.bones:
            ob = bones[b["name"]]
            if not ob["cubes"]:
                del ob["cubes"]
            out_bones.append(ob)
        geo = {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.ident,
                    "texture_width": self.tw, "texture_height": self.th,
                    "visible_bounds_width": 5, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.5, 0],
                },
                "bones": out_bones,
            }],
        }
        write_json(os.path.join(ASSETS, "geo", "entity", self.ident + ".geo.json"), geo)
        cv.save(tex("entity", self.ident))
        gl.save(tex("entity", self.ident + "_glowmask"))


def shade(cv, x, y, w, h, base, face, rnd, noise=0.07, edge=0.3, vgrad=0.22):
    for j in range(h):
        for i in range(w):
            if face == "up":
                f = 0.10
            elif face == "down":
                f = -0.28
            else:
                t = j / (h - 1) if h > 1 else 0.5
                f = vgrad * (0.5 - t)
                if face in ("east", "west"):
                    f -= 0.06
                if face == "south":
                    f -= 0.03
            f += (rnd.random() - 0.5) * 2 * noise
            col = lighten(base, f) if f > 0 else darken(base, -f)
            if w > 2 and h > 2 and (i == 0 or j == 0 or i == w - 1 or j == h - 1):
                col = darken(col, edge * 0.5)
            cv.set(x + i, y + j, col)


def glow_px(cv, gl, x, y, col):
    cv.set(x, y, col)
    gl.set(x, y, col)


def cracks(cv, gl, x, y, w, h, colors, n, rnd, glow=True):
    for _ in range(n):
        cx, cy = rnd.randrange(w), rnd.randrange(h)
        length = rnd.randint(2, max(3, (w + h) // 2))
        dirs = [(0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, 1)]
        d = rnd.choice(dirs)
        for k in range(length):
            if 0 <= cx < w and 0 <= cy < h:
                col = colors[0] if k % 3 else colors[min(1, len(colors) - 1)]
                if glow:
                    glow_px(cv, gl, x + cx, y + cy, col)
                else:
                    cv.set(x + cx, y + cy, col)
            if rnd.random() < 0.35:
                d = rnd.choice(dirs)
            cx += d[0]
            cy += d[1]


def specks(cv, gl, x, y, w, h, colors, density, rnd, glow=True):
    for j in range(h):
        for i in range(w):
            if rnd.random() < density:
                col = rnd.choice(colors)
                if glow:
                    glow_px(cv, gl, x + i, y + j, col)
                else:
                    cv.set(x + i, y + j, col)


def band(cv, gl, x, y, w, h, col, where, thick, glow=False, jag=None):
    for i in range(w):
        t = thick + (jag.randint(0, 1) if jag else 0)
        for k in range(min(t, h)):
            yy = y + (h - 1 - k if where == "bottom" else k)
            if glow:
                glow_px(cv, gl, x + i, yy, col)
            else:
                cv.set(x + i, yy, col)


# ----------------------------------------------------------------------------------------------- Ember Lord
E = {
    "skin": rgb("#3a2420"), "skin_l": rgb("#5a3a30"), "crack": rgb("#ff8a1e"), "hot": rgb("#ffd36a"),
    "armor": rgb("#3c3a44"), "trim": rgb("#c8902e"), "horn": rgb("#4a3a34"), "cloth": rgb("#701418"),
    "blade": rgb("#2a2a32"), "edge": rgb("#ff9a3a"), "flame": rgb("#ff8a1e"), "flame2": rgb("#ffe08a"),
}


def paint_ember(mat, face, cv, gl, x, y, w, h, rnd):
    if mat in ("skin", "chest", "face", "jaw"):
        shade(cv, x, y, w, h, E["skin"], face, rnd)
        if mat == "chest" and face == "north":
            cx, cy = w // 2, h // 2 - 1
            for j in range(h):
                for i in range(w):
                    dd = math.hypot(i + 0.5 - cx, j + 0.5 - cy)
                    if dd < 1.6:
                        glow_px(cv, gl, x + i, y + j, E["hot"])
                    elif dd < 2.7:
                        glow_px(cv, gl, x + i, y + j, E["crack"])
            for a in range(0, 360, 60):
                for r in range(3, 6):
                    i = int(round(cx + r * math.cos(math.radians(a + rnd.randint(-15, 15)))))
                    j = int(round(cy + r * math.sin(math.radians(a))))
                    if 0 <= i < w and 0 <= j < h:
                        glow_px(cv, gl, x + i, y + j, E["crack"])
        elif mat == "face" and face == "north":
            for i in range(w):
                cv.set(x + i, y + 2, darken(E["skin"], 0.4))
            for i in (1, 2, w - 3, w - 2):
                glow_px(cv, gl, x + i, y + 3, E["hot"])
            glow_px(cv, gl, x + 2, y + 4, E["crack"])
            glow_px(cv, gl, x + w - 3, y + 4, E["crack"])
            for i in range(2, w - 2):
                glow_px(cv, gl, x + i, y + h - 2, E["crack"] if i % 2 else E["hot"])
            cv.set(x + w // 2, y + 5, darken(E["skin"], 0.35))
        elif mat == "jaw" and face == "north":
            for i in range(1, w - 1):
                glow_px(cv, gl, x + i, y + 0, E["crack"])
            for i in range(1, w - 1, 2):
                cv.set(x + i, y + 1, rgb("#e8dcc8"))
        else:
            cracks(cv, gl, x, y, w, h, [E["crack"], E["hot"]], max(1, (w * h) // 40), rnd)
    elif mat == "armor":
        shade(cv, x, y, w, h, E["armor"], face, rnd, edge=0.4)
        if face not in ("up", "down"):
            band(cv, gl, x, y, w, h, E["trim"], "top", 1)
            band(cv, gl, x, y, w, h, darken(E["trim"], 0.3), "bottom", 1)
            for i in range(1, w - 1, 3):
                if h > 3:
                    cv.set(x + i, y + h // 2, lighten(E["armor"], 0.35))
    elif mat == "horn":
        for j in range(h):
            t = j / (h - 1) if h > 1 else 0
            col = mix(E["crack"], E["horn"], min(1.0, t * 1.6)) if face not in ("up",) else E["crack"]
            for i in range(w):
                c = darken(col, 0.2) if i in (0, w - 1) else col
                if t < 0.35 or face == "up":
                    glow_px(cv, gl, x + i, y + j, c)
                else:
                    cv.set(x + i, y + j, c)
    elif mat == "cloth":
        shade(cv, x, y, w, h, E["cloth"], face, rnd, vgrad=0.1)
        if face not in ("up", "down"):
            band(cv, gl, x, y, w, h, E["flame"], "bottom", 1, glow=True, jag=rnd)
            band(cv, gl, x, y, w, h, E["trim"], "top", 1)
    elif mat == "blade":
        shade(cv, x, y, w, h, E["blade"], face, rnd, edge=0.2)
        if face in ("east", "west", "up"):
            for i in range(w):
                glow_px(cv, gl, x + i, y, E["edge"])
            cracks(cv, gl, x, y, w, h, [E["crack"]], max(1, w // 4), rnd)
    elif mat == "hilt":
        shade(cv, x, y, w, h, rgb("#2a1a14"), face, rnd)
        specks(cv, gl, x, y, w, h, [E["trim"]], 0.2, rnd, glow=False)
    elif mat == "flame":
        for j in range(h):
            t = j / (h - 1) if h > 1 else 0
            col = mix(E["flame2"], E["flame"], t)
            for i in range(w):
                glow_px(cv, gl, x + i, y + j, col if rnd.random() > 0.15 else E["hot"])
    else:
        shade(cv, x, y, w, h, E["skin"], face, rnd)


def build_ember():
    g = Geo("patron_ember_lord")
    g.bone("root")
    g.bone("left_leg", "root", (3.5, 22, 0))
    g.cube("left_leg", (1.5, 9, -2.5), (5, 13, 5), "skin")
    g.cube("left_leg", (1, 3, -3), (6, 7, 6), "armor")
    g.cube("left_leg", (1, 0, -4), (6, 3, 7), "armor")
    g.bone("right_leg", "root", (-3.5, 22, 0))
    g.cube("right_leg", (-6.5, 9, -2.5), (5, 13, 5), "skin")
    g.cube("right_leg", (-7, 3, -3), (6, 7, 6), "armor")
    g.cube("right_leg", (-7, 0, -4), (6, 3, 7), "armor")
    g.bone("body", "root", (0, 22, 0))
    g.bone("torso", "body", (0, 22, 0))
    g.cube("torso", (-6, 22, -3.5), (12, 5, 7), "armor")
    g.cube("torso", (-4, 12, -4.5), (8, 10, 1), "cloth")
    g.cube("torso", (-4, 13, 3.5), (8, 9, 1), "cloth")
    g.cube("torso", (-5.5, 27, -3), (11, 5, 6), "skin")
    g.cube("torso", (-8, 32, -4.5), (16, 10, 9), "skin", faces={"north": "chest"})
    g.cube("torso", (-8.5, 40, -5), (17, 3, 10), "armor")
    g.bone("cape", "torso", (0, 42, 4.5))
    g.cube("cape", (-7, 16, 4.5), (14, 26, 1), "cloth")
    g.bone("neck", "torso", (0, 43, -0.5))
    g.bone("head", "neck", (0, 43, -0.5))
    g.cube("head", (-4.5, 43, -5.5), (9, 9, 9), "skin", faces={"north": "face"})
    g.cube("head", (-3.5, 41, -6), (7, 3, 6), "skin", faces={"north": "jaw"})
    g.bone("left_horn", "head", (4.5, 49, -1), rotation=(-20, 0, 35))
    g.cube("left_horn", (4.5, 48, -2.5), (3, 4, 3), "horn")
    g.bone("left_horn_tip", "left_horn", (6, 52, -1), rotation=(-25, 0, -25))
    g.cube("left_horn_tip", (5, 52, -2), (2, 6, 2), "horn")
    g.bone("right_horn", "head", (-4.5, 49, -1), rotation=(-20, 0, -35))
    g.cube("right_horn", (-7.5, 48, -2.5), (3, 4, 3), "horn")
    g.bone("right_horn_tip", "right_horn", (-6, 52, -1), rotation=(-25, 0, 25))
    g.cube("right_horn_tip", (-7, 52, -2), (2, 6, 2), "horn")
    g.cube("head", (-1, 52, -2), (2, 3, 2), "flame")
    g.cube("head", (-3, 52, -1), (1, 2, 1), "flame")
    g.cube("head", (2, 52, -1), (1, 2, 1), "flame")
    g.bone("left_arm", "torso", (10, 40, 0))
    g.cube("left_arm", (8, 37, -4.5), (7, 6, 9), "armor")
    g.cube("left_arm", (10, 42, -1), (3, 3, 2), "flame")
    g.cube("left_arm", (9, 23, -2.5), (5, 14, 5), "skin")
    g.cube("left_arm", (8.5, 19, -3), (6, 4, 6), "armor")
    g.bone("right_arm", "torso", (-10, 40, 0))
    g.cube("right_arm", (-15, 37, -4.5), (7, 6, 9), "armor")
    g.cube("right_arm", (-13, 42, -1), (3, 3, 2), "flame")
    g.cube("right_arm", (-14, 23, -2.5), (5, 14, 5), "skin")
    g.cube("right_arm", (-14.5, 19, -3), (6, 4, 6), "armor")
    g.bone("blade", "right_arm", (-11.5, 21, -1))
    g.cube("blade", (-12.5, 19.5, -4), (2, 3, 3), "hilt")
    g.cube("blade", (-13.5, 19, -5), (4, 4, 1), "armor")
    g.cube("blade", (-12, 19.5, -23), (1, 3, 18), "blade")
    g.build(paint_ember)


# ----------------------------------------------------------------------------------------------- Void Lady
V = {
    "robe": rgb("#1c1030"), "robe_l": rgb("#2e1a4c"), "trim": rgb("#b070ff"), "star": rgb("#e8dcff"),
    "skin": rgb("#c9bedb"), "eye": rgb("#c47aff"), "eye_c": rgb("#ffffff"), "lip": rgb("#54204e"),
    "veil": rgb("#22143a"), "silver": rgb("#c8c8dc"), "orb": rgb("#0c0614"),
}


def paint_void(mat, face, cv, gl, x, y, w, h, rnd):
    if mat in ("robe", "skirt", "mantle", "collar"):
        base = V["robe_l"] if mat == "mantle" else V["robe"]
        shade(cv, x, y, w, h, base, face, rnd, vgrad=0.28)
        specks(cv, gl, x, y, w, h, [V["star"], V["trim"]], 0.025, rnd)
        if mat == "skirt" and face not in ("up", "down"):
            band(cv, gl, x, y, w, h, V["trim"], "bottom", 1, glow=True)
        if mat in ("mantle", "collar") and face not in ("up", "down"):
            band(cv, gl, x, y, w, h, V["silver"], "top", 1)
        if mat == "collar" and face == "north":
            for i in range(0, w, 2):
                glow_px(cv, gl, x + i, y + 1, V["trim"])
    elif mat == "chest":
        shade(cv, x, y, w, h, V["robe_l"], face, rnd, vgrad=0.2)
        specks(cv, gl, x, y, w, h, [V["star"]], 0.03, rnd)
        if face == "north":
            cx = w // 2
            glow_px(cv, gl, x + cx, y + 2, V["eye_c"])
            for (dx, dy) in ((-1, 2), (1, 2), (0, 1), (0, 3)):
                glow_px(cv, gl, x + cx + dx, y + dy, V["trim"])
            for j in range(4, h):
                cv.set(x + cx, y + j, V["silver"])
    elif mat == "wisp":
        for j in range(h):
            t = j / (h - 1) if h > 1 else 0
            col = mix(V["robe"], V["trim"], t * 0.8)
            for i in range(w):
                if t > 0.5:
                    glow_px(cv, gl, x + i, y + j, col)
                else:
                    cv.set(x + i, y + j, col)
    elif mat == "skin":
        shade(cv, x, y, w, h, V["skin"], face, rnd, noise=0.03, vgrad=0.15)
    elif mat == "head":
        # Capuz/veu cobrindo topo, costas e lados.
        shade(cv, x, y, w, h, V["veil"], face, rnd, vgrad=0.2)
        specks(cv, gl, x, y, w, h, [V["trim"]], 0.03, rnd)
    elif mat == "face":
        shade(cv, x, y, w, h, V["skin"], face, rnd, noise=0.03, vgrad=0.1)
        for i in range(w):
            cv.set(x + i, y, V["veil"])
            cv.set(x + i, y + 1, V["veil"] if i in (0, w - 1) or rnd.random() < 0.5 else darken(V["skin"], 0.2))
        for j in range(h):
            cv.set(x, y + j, V["veil"])
            cv.set(x + w - 1, y + j, V["veil"])
        for i in (1, 2, w - 3, w - 2):
            glow_px(cv, gl, x + i, y + 4, V["eye"])
        glow_px(cv, gl, x + 2, y + 4, V["eye_c"])
        glow_px(cv, gl, x + w - 3, y + 4, V["eye_c"])
        cv.set(x + w // 2, y + 6, V["lip"])
        cv.set(x + 1, y + 5, darken(V["skin"], 0.25))
        cv.set(x + w - 2, y + 5, darken(V["skin"], 0.25))
    elif mat == "crown":
        for j in range(h):
            for i in range(w):
                c = V["silver"] if j > 0 else V["trim"]
                if j == 0:
                    glow_px(cv, gl, x + i, y + j, c)
                else:
                    cv.set(x + i, y + j, darken(c, 0.15 * (j / max(1, h - 1))))
    elif mat == "orb":
        for j in range(h):
            for i in range(w):
                edge = i in (0, w - 1) or j in (0, h - 1)
                if edge:
                    glow_px(cv, gl, x + i, y + j, V["trim"])
                else:
                    cv.set(x + i, y + j, V["orb"])
    elif mat == "veil":
        shade(cv, x, y, w, h, V["veil"], face, rnd, vgrad=0.3)
        for i in range(0, w, 2):
            for j in range(h):
                if rnd.random() < 0.3:
                    cv.set(x + i, y + j, V["robe_l"])
        band(cv, gl, x, y, w, h, V["trim"], "bottom", 1, glow=True)
    else:
        shade(cv, x, y, w, h, V["robe"], face, rnd)


def build_void():
    g = Geo("patron_void_lady", yoff=3)
    g.bone("root")
    g.bone("body", "root", (0, 20, 0))
    g.bone("skirt", "body", (0, 22, 0))
    g.cube("skirt", (-5, 14, -4), (10, 8, 8), "skirt")
    g.cube("skirt", (-6, 7, -5), (12, 7, 10), "skirt")
    g.cube("skirt", (-7, 2, -6), (14, 5, 12), "skirt")
    for (wx, wz) in ((-6, -5), (4, -5), (-6, 3), (4, 3), (-1, -6), (-1, 4)):
        g.cube("skirt", (wx, -2, wz), (2, 4, 2), "wisp")
    g.bone("torso", "body", (0, 22, 0))
    g.cube("torso", (-3.5, 22, -2.5), (7, 6, 5), "robe")
    g.cube("torso", (-4.5, 28, -3), (9, 7, 6), "robe", faces={"north": "chest"})
    g.cube("torso", (-6.5, 34, -3.5), (13, 2, 7), "mantle")
    g.cube("torso", (-6, 35, 2.5), (12, 8, 1), "collar")
    g.bone("neck", "torso", (0, 36, 0))
    g.bone("head", "neck", (0, 36, 0))
    g.cube("head", (-3.5, 36, -3.5), (7, 8, 7), "head", faces={"north": "face"})
    g.cube("head", (-3.5, 44, -0.5), (1, 2, 1), "crown")
    g.cube("head", (-1.5, 44, -0.5), (1, 3, 1), "crown")
    g.cube("head", (-0.5, 44, -0.5), (1, 5, 1), "crown")
    g.cube("head", (0.5, 44, -0.5), (1, 3, 1), "crown")
    g.cube("head", (2.5, 44, -0.5), (1, 2, 1), "crown")
    g.cube("head", (-4, 30, 3.5), (8, 13, 1), "veil")
    g.bone("left_arm", "torso", (5.5, 34, 0))
    g.cube("left_arm", (4.5, 25, -1.5), (3, 9, 3), "robe")
    g.cube("left_arm", (4, 20, -2.5), (4, 5, 5), "skirt")
    g.cube("left_arm", (5, 17, -1), (2, 3, 2), "skin")
    g.bone("right_arm", "torso", (-5.5, 34, 0))
    g.cube("right_arm", (-7.5, 25, -1.5), (3, 9, 3), "robe")
    g.cube("right_arm", (-8, 20, -2.5), (4, 5, 5), "skirt")
    g.cube("right_arm", (-7, 17, -1), (2, 3, 2), "skin")
    g.bone("orbs", "body", (0, 30, 0))
    for k in range(3):
        a = math.radians(90 + 120 * k)
        ox, oz = 10 * math.cos(a), 10 * math.sin(a)
        g.cube("orbs", (round(ox - 1, 2), 29, round(oz - 1, 2)), (2, 2, 2), "orb")
    g.build(paint_void)


# ----------------------------------------------------------------------------------------------- Drowned King
D = {
    "skin": rgb("#3f7a6a"), "spot": rgb("#2d5e52"), "belly": rgb("#5f9c88"), "barn": rgb("#d0cfc4"),
    "barn2": rgb("#8e8d84"), "cape": rgb("#1d3557"), "weed": rgb("#2f7a3a"), "gold": rgb("#d2a63c"),
    "coral": rgb("#ff6f61"), "coral2": rgb("#ffb08a"), "eye": rgb("#7affee"), "shell": rgb("#c99a78"),
    "pris": rgb("#4f9e92"), "prong": rgb("#8ef0e2"),
}


def paint_drowned(mat, face, cv, gl, x, y, w, h, rnd):
    if mat in ("skin", "face", "belly"):
        base = D["belly"] if (mat == "belly" and face == "north") else D["skin"]
        shade(cv, x, y, w, h, base, face, rnd)
        specks(cv, gl, x, y, w, h, [D["spot"]], 0.08, rnd, glow=False)
        if mat == "belly" and face == "north":
            for j in range(2, h, 3):
                for i in range(1, w - 1):
                    cv.set(x + i, y + j, darken(D["belly"], 0.18))
        specks(cv, gl, x, y, w, h, [D["barn"], D["barn2"]], 0.03, rnd, glow=False)
        if mat == "face" and face == "north":
            for i in range(1, w - 1):
                cv.set(x + i, y + 2, darken(D["skin"], 0.35))
            for i in (1, 2, w - 3, w - 2):
                glow_px(cv, gl, x + i, y + 3, D["eye"])
            cv.set(x + w // 2, y + 4, darken(D["skin"], 0.3))
            cv.set(x + w // 2, y + 5, darken(D["skin"], 0.3))
            for i in range(2, w - 2):
                cv.set(x + i, y + 7, rgb("#14261f"))
            for i in range(2, w - 2, 2):
                cv.set(x + i, y + 7, rgb("#d8d2b8"))
    elif mat == "beard":
        for i in range(w):
            col = D["weed"] if i % 2 == 0 else darken(D["weed"], 0.3)
            length = h - rnd.randint(0, 2)
            for j in range(length):
                cv.set(x + i, y + j, lighten(col, 0.1) if j == 0 else col)
    elif mat == "cape":
        shade(cv, x, y, w, h, D["cape"], face, rnd, vgrad=0.18)
        if face not in ("up", "down"):
            band(cv, gl, x, y, w, h, D["weed"], "bottom", 2, jag=rnd)
            band(cv, gl, x, y, w, h, D["gold"], "top", 1)
            specks(cv, gl, x, y, w, h, [rgb("#7affee")], 0.01, rnd)
    elif mat == "gold":
        shade(cv, x, y, w, h, D["gold"], face, rnd, edge=0.4)
        specks(cv, gl, x, y, w, h, [D["shell"], D["barn"]], 0.08, rnd, glow=False)
    elif mat == "coral":
        for j in range(h):
            t = j / (h - 1) if h > 1 else 0
            col = mix(D["coral2"], D["coral"], t)
            for i in range(w):
                if j == 0 or face == "up":
                    glow_px(cv, gl, x + i, y + j, D["coral2"])
                else:
                    cv.set(x + i, y + j, darken(col, 0.15) if i in (0, w - 1) else col)
    elif mat == "shell":
        shade(cv, x, y, w, h, D["shell"], face, rnd, edge=0.4)
        for i in range(0, w, 2):
            for j in range(h):
                cv.set(x + i, y + j, darken(D["shell"], 0.15))
        specks(cv, gl, x, y, w, h, [D["barn"], D["barn2"]], 0.12, rnd, glow=False)
    elif mat == "weed":
        for j in range(h):
            for i in range(w):
                cv.set(x + i, y + j, D["weed"] if (i + j) % 3 else darken(D["weed"], 0.3))
    elif mat == "shaft":
        shade(cv, x, y, w, h, D["pris"], face, rnd, edge=0.1)
        for j in range(0, h, 7):
            for i in range(w):
                cv.set(x + i, y + j, D["gold"])
    elif mat == "prong":
        for j in range(h):
            for i in range(w):
                glow_px(cv, gl, x + i, y + j, D["prong"] if j < h - 1 else D["pris"])
    else:
        shade(cv, x, y, w, h, D["skin"], face, rnd)


def build_drowned():
    g = Geo("patron_drowned_king")
    g.bone("root")
    g.bone("left_leg", "root", (4, 12, 0))
    g.cube("left_leg", (1, 0, -3), (6, 12, 6), "skin")
    g.bone("right_leg", "root", (-4, 12, 0))
    g.cube("right_leg", (-7, 0, -3), (6, 12, 6), "skin")
    g.bone("body", "root", (0, 12, 0))
    g.bone("torso", "body", (0, 12, 0))
    g.cube("torso", (-8, 12, -5), (16, 13, 10), "skin", faces={"north": "belly"})
    g.cube("torso", (-8.5, 12, -5.5), (17, 2, 11), "gold")
    g.cube("torso", (-7, 25, -4), (14, 8, 8), "skin")
    g.cube("torso", (-6, 5, -5.5), (3, 7, 1), "weed")
    g.cube("torso", (3, 6, -5.5), (3, 6, 1), "weed")
    g.bone("cape", "torso", (0, 33, 4))
    g.cube("cape", (-8, 5, 4), (16, 28, 1), "cape")
    g.bone("neck", "torso", (0, 33, -1))
    g.bone("head", "neck", (0, 33, -1))
    g.cube("head", (-4.5, 33, -6), (9, 9, 9), "skin", faces={"north": "face"})
    g.bone("beard", "head", (0, 34, -6))
    g.cube("beard", (-4, 28, -7), (8, 6, 2), "beard")
    g.cube("head", (-5, 42, -6.5), (10, 2, 10), "gold")
    g.cube("head", (-4.5, 44, -6), (2, 4, 2), "coral")
    g.cube("head", (-1, 44, -6.5), (2, 6, 2), "coral")
    g.cube("head", (2.5, 44, -6), (2, 4, 2), "coral")
    g.cube("head", (-4.5, 44, 1), (2, 3, 2), "coral")
    g.cube("head", (2.5, 44, 1), (2, 3, 2), "coral")
    g.cube("head", (-6, 45, -3), (1, 2, 1), "coral")
    g.cube("head", (5, 45, -3), (1, 2, 1), "coral")
    g.bone("left_arm", "torso", (9, 31, 0))
    g.cube("left_arm", (8, 28, -4), (6, 5, 8), "shell")
    g.cube("left_arm", (8.5, 15, -2.5), (5, 13, 5), "skin")
    g.cube("left_arm", (8, 12, -3), (6, 3, 6), "skin")
    g.bone("right_arm", "torso", (-9, 31, 0))
    g.cube("right_arm", (-14, 28, -4), (6, 5, 8), "shell")
    g.cube("right_arm", (-13.5, 15, -2.5), (5, 13, 5), "skin")
    g.cube("right_arm", (-14, 12, -3), (6, 3, 6), "skin")
    g.bone("trident", "right_arm", (-11, 14, -3.5))
    g.cube("trident", (-11.5, 2, -4), (1, 40, 1), "shaft")
    g.cube("trident", (-14.5, 40, -4), (7, 1, 1), "shaft")
    g.cube("trident", (-14.5, 41, -4), (1, 4, 1), "prong")
    g.cube("trident", (-11.5, 41, -4), (1, 6, 1), "prong")
    g.cube("trident", (-8.5, 41, -4), (1, 4, 1), "prong")
    g.build(paint_drowned)


# =====================================================================================================
#  ANIMACOES (Bedrock 1.8.0, lidas pelo GeckoLib)
# =====================================================================================================
def k(t):
    return ("%.2f" % t).rstrip("0").rstrip(".") if t != int(t) else "%.1f" % t


def frames(d):
    return {k(t): [round(v, 2) for v in vec] for t, vec in sorted(d.items())}


def wave(length, amp, steps=8, phase=0.0, base=(0, 0, 0), cycles=1):
    """Keyframes senoidais (loop perfeito)."""
    out = {}
    for i in range(steps + 1):
        t = length * i / steps
        s = math.sin(2 * math.pi * cycles * i / steps + phase)
        out[t] = [base[j] + amp[j] * s for j in range(3)]
    return frames(out)


def jitter(length, amp, step, seed, base=(0, 0, 0)):
    rnd = random.Random(seed)
    out = {}
    n = int(round(length / step))
    for i in range(n + 1):
        t = i * step
        if i in (0, n):
            out[t] = list(base)
        else:
            out[t] = [base[j] + (rnd.random() - 0.5) * 2 * amp[j] for j in range(3)]
    return frames(out)


def anim(loop, length, bones):
    a = {"animation_length": length, "bones": bones}
    if loop:
        a["loop"] = True
    return a


def write_anims(ident, anims):
    write_json(os.path.join(ASSETS, "animations", "entity", ident + ".animation.json"), {
        "format_version": "1.8.0",
        "animations": {"animation.%s.%s" % (ident, name): a for name, a in anims.items()},
    })


def humanoid_anims(ident, *, flyer=False, extra_idle=None, extra_bound=None, weapon_arm="right_arm"):
    L, R = "left_arm", "right_arm"
    idle = {
        "torso": {"rotation": wave(3.0, (2, 0, 0))},
        "body": {"position": wave(3.0, (0, 0.4, 0))},
        L: {"rotation": wave(3.0, (2, 0, 2), base=(0, 0, -3))},
        R: {"rotation": wave(3.0, (2, 0, -2), phase=math.pi, base=(0, 0, 3))},
        "neck": {"rotation": wave(3.0, (2, 4, 0), phase=0.5)},
    }
    if flyer:
        idle["root"] = {"position": wave(3.0, (0, 1.5, 0))}
    if extra_idle:
        idle.update(extra_idle)

    bound = {
        L: {"rotation": jitter(2.0, (6, 4, 6), 0.25, 1, base=(15, 0, -40))},
        R: {"rotation": jitter(2.0, (6, 4, 6), 0.25, 2, base=(15, 0, 40))},
        "torso": {"rotation": jitter(2.0, (4, 3, 3), 0.2, 3, base=(12, 0, 0))},
        "neck": {"rotation": frames({0: (-15, 0, 0), 0.5: (-20, 10, 0), 1.0: (-12, -10, 0), 1.5: (-20, 5, 0), 2.0: (-15, 0, 0)})},
        "body": {"position": jitter(2.0, (0.2, 0.3, 0.2), 0.25, 4)},
    }
    if extra_bound:
        bound.update(extra_bound)

    if flyer:
        move = {
            "body": {"rotation": frames({0: (18, 0, 0), 1.2: (18, 0, 0)})},
            "skirt": {"rotation": wave(1.2, (6, 0, 0), base=(22, 0, 0))},
            L: {"rotation": wave(1.2, (8, 0, 0), base=(35, 0, -15))},
            R: {"rotation": wave(1.2, (8, 0, 0), phase=math.pi, base=(35, 0, 15))},
            "root": {"position": wave(1.2, (0, 1.0, 0))},
        }
    else:
        move = {
            "left_leg": {"rotation": wave(1.2, (30, 0, 0))},
            "right_leg": {"rotation": wave(1.2, (30, 0, 0), phase=math.pi)},
            L: {"rotation": wave(1.2, (20, 0, 0), phase=math.pi, base=(0, 0, -4))},
            R: {"rotation": wave(1.2, (20, 0, 0), base=(0, 0, 4))},
            "body": {"position": wave(1.2, (0, 0.8, 0), cycles=2), "rotation": wave(1.2, (0, 0, 2))},
            "torso": {"rotation": frames({0: (6, 0, 0), 1.2: (6, 0, 0)})},
        }

    W = weapon_arm
    attack = {
        W: {"rotation": frames({0: (0, 0, 0), 0.3: (-160, 0, 15), 0.45: (-35, 0, 0), 0.8: (0, 0, 0)})},
        "torso": {"rotation": frames({0: (0, 0, 0), 0.3: (-10, 10, 0), 0.45: (16, -8, 0), 0.8: (0, 0, 0)})},
    }
    cast = {
        L: {"rotation": frames({0: (0, 0, 0), 0.4: (-110, 0, -25), 0.6: (-85, 0, -10), 1.0: (0, 0, 0)})},
        R: {"rotation": frames({0: (0, 0, 0), 0.4: (-110, 0, 25), 0.6: (-85, 0, 10), 1.0: (0, 0, 0)})},
        "torso": {"rotation": frames({0: (0, 0, 0), 0.4: (-8, 0, 0), 0.6: (6, 0, 0), 1.0: (0, 0, 0)})},
    }
    talk = {
        "neck": {"rotation": frames({0: (0, 0, 0), 0.3: (8, 0, 0), 0.6: (-5, 8, 0), 1.0: (6, -6, 0), 1.6: (0, 0, 0)})},
        L: {"rotation": frames({0: (0, 0, 0), 0.4: (-50, 0, -25), 1.0: (-40, 15, -20), 1.6: (0, 0, 0)})},
    }
    special = {
        L: {"rotation": frames({0: (0, 0, 0), 0.5: (-170, 0, -15), 0.7: (-30, 0, -30), 1.2: (0, 0, 0)})},
        R: {"rotation": frames({0: (0, 0, 0), 0.5: (-170, 0, 15), 0.7: (-30, 0, 30), 1.2: (0, 0, 0)})},
        "body": {"position": frames({0: (0, 0, 0), 0.5: (0, 1, 0), 0.7: (0, -2.5, 0), 1.2: (0, 0, 0)})},
        "torso": {"rotation": frames({0: (0, 0, 0), 0.5: (-12, 0, 0), 0.7: (20, 0, 0), 1.2: (0, 0, 0)})},
    }
    summon = {
        "root": {"position": frames({0: (0, -60, 0), 1.0: (0, -40, 0), 2.5: (0, -4, 0), 3.0: (0, 2, 0), 3.5: (0, 0, 0)}),
                 "rotation": frames({0: (0, 180, 0), 2.5: (0, 20, 0), 3.5: (0, 0, 0)})},
        L: {"rotation": frames({0: (-170, 0, -20), 2.5: (-150, 0, -40), 3.0: (-60, 0, -60), 3.5: (0, 0, -3)})},
        R: {"rotation": frames({0: (-170, 0, 20), 2.5: (-150, 0, 40), 3.0: (-60, 0, 60), 3.5: (0, 0, 3)})},
        "neck": {"rotation": frames({0: (-30, 0, 0), 2.8: (-30, 0, 0), 3.5: (0, 0, 0)})},
    }
    vanish = {
        "root": {"position": frames({0: (0, 0, 0), 0.6: (0, 3, 0), 2.5: (0, -60, 0)}),
                 "rotation": frames({0: (0, 0, 0), 2.5: (0, 270, 0)})},
        L: {"rotation": frames({0: (0, 0, 0), 0.6: (-160, 0, -30), 2.5: (-170, 0, -10)})},
        R: {"rotation": frames({0: (0, 0, 0), 0.6: (-160, 0, 30), 2.5: (-170, 0, 10)})},
        "neck": {"rotation": frames({0: (0, 0, 0), 0.6: (-35, 0, 0), 2.5: (-35, 0, 0)})},
    }
    if flyer:
        summon["root"]["scale"] = frames({0: (0.2, 0.2, 0.2), 2.5: (0.9, 0.9, 0.9), 3.0: (1.08, 1.08, 1.08), 3.5: (1, 1, 1)})
        vanish["root"]["scale"] = frames({0: (1, 1, 1), 0.6: (1.1, 1.1, 1.1), 2.5: (0.05, 0.05, 0.05)})
        vanish["root"]["position"] = frames({0: (0, 0, 0), 2.5: (0, 24, 0)})
    return {
        "idle": anim(True, 3.0, idle),
        "bound": anim(True, 2.0, bound),
        "move": anim(True, 1.2, move),
        "attack": anim(False, 0.8, attack),
        "cast": anim(False, 1.0, cast),
        "talk": anim(False, 1.6, talk),
        "special": anim(False, 1.2, special),
        "summon": anim(False, 3.5, summon),
        "vanish": anim(False, 2.5, vanish),
    }


def gen_animations():
    ember_extra = {"cape": {"rotation": wave(3.0, (4, 0, 1), base=(6, 0, 0))}}
    write_anims("patron_ember_lord", humanoid_anims("patron_ember_lord", extra_idle=ember_extra,
                                                    extra_bound={"cape": {"rotation": wave(1.0, (8, 0, 2), base=(12, 0, 0))}}))
    void_idle = {"skirt": {"rotation": wave(3.0, (3, 0, 2))}, "orbs": {"rotation": frames({0: (0, 0, 0), 0.75: (0, 90, 0), 1.5: (0, 180, 0), 2.25: (0, 270, 0), 3.0: (0, 360, 0)})}}
    void_bound = {"orbs": {"rotation": frames({0: (0, 0, 0), 0.5: (10, 120, 0), 1.0: (-10, 200, 0), 1.5: (8, 300, 0), 2.0: (0, 360, 0)})},
                  "skirt": {"rotation": jitter(2.0, (4, 0, 4), 0.25, 9)}}
    anims = humanoid_anims("patron_void_lady", flyer=True, extra_idle=void_idle, extra_bound=void_bound)
    anims["move"]["bones"]["orbs"] = {"rotation": frames({0: (0, 0, 0), 0.3: (0, 90, 0), 0.6: (0, 180, 0), 0.9: (0, 270, 0), 1.2: (0, 360, 0)})}
    anims["cast"]["bones"]["orbs"] = {"rotation": frames({0: (0, 0, 0), 0.5: (0, 360, 0), 1.0: (0, 720, 0)}),
                                      "scale": frames({0: (1, 1, 1), 0.5: (1.3, 1.3, 1.3), 1.0: (1, 1, 1)})}
    anims["special"]["bones"]["left_arm"] = {"rotation": frames({0: (0, 0, 0), 0.4: (-10, 0, -95), 0.8: (-90, 0, -5), 1.2: (0, 0, 0)})}
    anims["special"]["bones"]["right_arm"] = {"rotation": frames({0: (0, 0, 0), 0.4: (-10, 0, 95), 0.8: (-90, 0, 5), 1.2: (0, 0, 0)})}
    anims["special"]["bones"]["orbs"] = {"scale": frames({0: (1, 1, 1), 0.4: (1.6, 1, 1.6), 0.8: (0.4, 1, 0.4), 1.2: (1, 1, 1)})}
    write_anims("patron_void_lady", anims)

    drowned_idle = {"cape": {"rotation": wave(3.0, (3, 0, 1), base=(5, 0, 0))},
                    "beard": {"rotation": wave(3.0, (5, 0, 0), phase=1.0)},
                    "trident": {"rotation": wave(3.0, (2, 0, 0))}}
    anims = humanoid_anims("patron_drowned_king", extra_idle=drowned_idle,
                           extra_bound={"beard": {"rotation": jitter(2.0, (8, 0, 4), 0.25, 7)}})
    anims["move"]["bones"]["body"]["rotation"] = wave(1.2, (0, 0, 5))
    anims["attack"]["bones"]["right_arm"] = {"rotation": frames({0: (0, 0, 0), 0.3: (-130, 0, 0), 0.45: (-55, 0, 0), 0.8: (0, 0, 0)})}
    anims["attack"]["bones"]["trident"] = {"rotation": frames({0: (0, 0, 0), 0.3: (20, 0, 0), 0.45: (70, 0, 0), 0.8: (0, 0, 0)})}
    write_anims("patron_drowned_king", anims)


# =====================================================================================================
#  DADOS: loot, receitas, tags
# =====================================================================================================
def gen_data():
    write_json(os.path.join(DATA, "loot_table", "blocks", "offering_altar.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "isekai_mahou:offering_altar"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "isekai_mahou:blocks/offering_altar",
    })

    def entity_loot(pid, main, extra, essence):
        pools = [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": main[0], "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": main[1], "max": main[2]}},
                {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting", "count": {"min": 0, "max": 1}}]}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": extra[0], "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": extra[1], "max": extra[2]}}]}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": essence, "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}}]}]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}],
             "entries": [{"type": "minecraft:item", "name": "minecraft:diamond"}]},
        ]
        write_json(os.path.join(DATA, "loot_table", "entities", pid + ".json"),
                   {"type": "minecraft:entity", "pools": pools, "random_sequence": "isekai_mahou:entities/" + pid})

    entity_loot("patron_ember_lord", ("minecraft:blaze_rod", 3, 6), ("minecraft:magma_cream", 2, 5), "isekai_mahou:essence_fire")
    entity_loot("patron_void_lady", ("minecraft:ender_pearl", 3, 6), ("minecraft:echo_shard", 1, 2), "isekai_mahou:essence_shadow")
    entity_loot("patron_drowned_king", ("minecraft:prismarine_shard", 4, 8), ("minecraft:nautilus_shell", 1, 2), "isekai_mahou:essence_water")

    write_json(os.path.join(DATA, "recipe", "ritual_chalk_item.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:calcite"}, {"item": "minecraft:bone_meal"}, {"item": "minecraft:bone_meal"}],
        "result": {"id": "isekai_mahou:ritual_chalk_item", "count": 1},
    })
    write_json(os.path.join(DATA, "recipe", "ritual_dagger.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["O", "I", "S"],
        "key": {"O": {"item": "minecraft:obsidian"}, "I": {"item": "minecraft:iron_ingot"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": "isekai_mahou:ritual_dagger", "count": 1},
    })
    write_json(os.path.join(DATA, "recipe", "offering_altar.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["G G", "BBB", " B "],
        "key": {"G": {"item": "minecraft:gold_ingot"},
                "B": [{"item": "minecraft:polished_blackstone"}, {"item": "minecraft:polished_deepslate"}]},
        "result": {"id": "isekai_mahou:offering_altar", "count": 1},
    })

    opt = lambda i: {"id": i, "required": False}  # noqa: E731
    write_json(os.path.join(DATA, "tags", "item", "warlock_offering_ember_lord.json"), {"values": [
        "minecraft:blaze_rod", "minecraft:blaze_powder", "minecraft:magma_cream", "minecraft:magma_block",
        "#minecraft:coals", "minecraft:coal_block", "minecraft:fire_charge", "minecraft:lava_bucket",
        opt("isekai_mahou:essence_fire"), opt("isekai_mahou:guardian_core_fire")]})
    write_json(os.path.join(DATA, "tags", "item", "warlock_offering_void_lady.json"), {"values": [
        "minecraft:ender_pearl", "minecraft:ender_eye", "minecraft:phantom_membrane", "minecraft:echo_shard",
        "minecraft:chorus_fruit", "minecraft:popped_chorus_fruit", "minecraft:shulker_shell", "minecraft:dragon_breath",
        "minecraft:sculk", "minecraft:crying_obsidian", "minecraft:wither_rose", "minecraft:nether_star",
        opt("isekai_mahou:essence_shadow"), opt("isekai_mahou:guardian_core_shadow")]})
    water = ["minecraft:prismarine_shard", "minecraft:prismarine_crystals", "#minecraft:fishes", "minecraft:nautilus_shell",
             "minecraft:heart_of_the_sea", "minecraft:sea_pickle", "minecraft:ink_sac", "minecraft:glow_ink_sac",
             "minecraft:turtle_scute"]
    for coral in ("tube", "brain", "bubble", "fire", "horn"):
        water += ["minecraft:%s_coral" % coral, "minecraft:%s_coral_block" % coral, "minecraft:%s_coral_fan" % coral]
    water += [opt("isekai_mahou:essence_water"), opt("isekai_mahou:guardian_core_water")]
    write_json(os.path.join(DATA, "tags", "item", "warlock_offering_drowned_king.json"), {"values": water})
    write_json(os.path.join(DATA, "tags", "item", "warlock_offering_generous.json"), {"values": [
        "minecraft:heart_of_the_sea", "minecraft:echo_shard", "minecraft:dragon_breath", "minecraft:nether_star",
        "minecraft:wither_rose", "minecraft:lava_bucket", "minecraft:shulker_shell",
        opt("isekai_mahou:guardian_core_fire"), opt("isekai_mahou:guardian_core_shadow"), opt("isekai_mahou:guardian_core_water")]})

    write_json(os.path.join(MERGE, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"values": ["isekai_mahou:offering_altar"]})
    write_json(os.path.join(MERGE, "data", "minecraft", "tags", "entity_type", "can_breathe_under_water.json"),
               {"values": ["isekai_mahou:patron_drowned_king"]})
    # Adaga na tag de espadas: em 1.21 os encantamentos (Afiação, Saque, Inquebrável...) vêm das tags.
    write_json(os.path.join(MERGE, "data", "minecraft", "tags", "item", "swords.json"),
               {"values": ["isekai_mahou:ritual_dagger"]})


# =====================================================================================================
#  TRADUCOES
# =====================================================================================================
def gen_lang():
    pt, en = {}, {}

    def t(key, p, e):
        pt[key] = p
        en[key] = e

    t("item.isekai_mahou.ritual_chalk_item", "Giz Ritual", "Ritual Chalk")
    t("item.isekai_mahou.ritual_dagger", "Adaga Ritual", "Ritual Dagger")
    t("block.isekai_mahou.ritual_chalk", "Desenho de Giz", "Chalk Drawing")
    t("block.isekai_mahou.offering_altar", "Altar de Oferendas", "Offering Altar")
    t("entity.isekai_mahou.patron_ember_lord", "Lorde das Brasas", "Ember Lord")
    t("entity.isekai_mahou.patron_void_lady", "Senhora do Vazio", "Void Lady")
    t("entity.isekai_mahou.patron_drowned_king", "Rei Afogado", "Drowned King")
    t("item.isekai_mahou.patron_ember_lord_spawn_egg", "Ovo do Lorde das Brasas", "Ember Lord Spawn Egg")
    t("item.isekai_mahou.patron_void_lady_spawn_egg", "Ovo da Senhora do Vazio", "Void Lady Spawn Egg")
    t("item.isekai_mahou.patron_drowned_king_spawn_egg", "Ovo do Rei Afogado", "Drowned King Spawn Egg")

    t("tooltip.isekai_mahou.ritual_chalk_item.0", "Desenha runas de giz no chão.", "Draws chalk runes on the floor.")
    t("tooltip.isekai_mahou.ritual_chalk_item.1", "Clique numa runa para trocar o desenho.", "Click a rune to change its drawing.")
    t("tooltip.isekai_mahou.ritual_chalk_item.2", "Agachado no Altar: desenha o anel do próximo pacto.",
      "Sneak-click the Altar: draws the ring for your next pact.")
    t("tooltip.isekai_mahou.ritual_dagger.0", "Matar com ela colhe almas e um pouco de XP mágico.",
      "Kills with it harvest souls and a little magic XP.")
    t("tooltip.isekai_mahou.ritual_dagger.1", "No Altar, à noite: paga 2 corações e invoca um Patrono.",
      "On the Altar, at night: pay 2 hearts to summon a Patron.")
    t("tooltip.isekai_mahou.ritual_dagger.2", "Clique numa vela apagada para acendê-la.", "Click an unlit candle to light it.")
    t("tooltip.isekai_mahou.offering_altar.0", "O centro do Círculo de Giz.", "The heart of the Chalk Circle.")
    t("tooltip.isekai_mahou.offering_altar.1", "Pequeno: anel 3×3 + 4 velas · Médio: 5×5 + 6 · Grande: 7×7 + 8",
      "Small: 3×3 ring + 4 candles · Medium: 5×5 + 6 · Large: 7×7 + 8")
    t("tooltip.isekai_mahou.offering_altar.2", "Agache e clique com a mão vazia para ver o desenho.",
      "Sneak and click with an empty hand to see the layout.")

    t("warlock.isekai_mahou.circle.small", "Círculo Pequeno", "Small Circle")
    t("warlock.isekai_mahou.circle.medium", "Círculo Médio", "Medium Circle")
    t("warlock.isekai_mahou.circle.large", "Círculo Grande", "Large Circle")
    t("warlock.isekai_mahou.demand.item", "Entregar %s× %s no altar", "Deliver %s× %s to the altar")
    t("warlock.isekai_mahou.demand.kill", "Matar %s× %s em %s minutos", "Slay %s× %s within %s minutes")
    t("warlock.isekai_mahou.demand.souls", "Colher %s almas com a Adaga Ritual em %s minutos",
      "Harvest %s souls with the Ritual Dagger within %s minutes")
    t("warlock.isekai_mahou.demand.hearts", "Ceder %s corações máximos por %s minutos", "Surrender %s max hearts for %s minutes")
    t("warlock.isekai_mahou.demand.levels", "Sacrificar %s níveis de experiência", "Sacrifice %s experience levels")
    t("warlock.isekai_mahou.souls", "Almas", "Souls")
    t("warlock.isekai_mahou.hearts", "Corações", "Hearts")
    t("warlock.isekai_mahou.levels", "Níveis", "Levels")
    t("warlock.isekai_mahou.unbound", "O Patrono se libertou!", "The Patron breaks free!")

    m = "message.isekai_mahou.warlock."
    t(m + "bad_offering", "O altar rejeita isto. Ofereça algo de fogo, do Fim/sombra ou do mar.",
      "The altar rejects this. Offer something of fire, of the End/shadow, or of the sea.")
    t(m + "offering_placed", "A oferenda atrai a atenção de %s...", "The offering draws the attention of %s...")
    t(m + "offering_taken", "Você recolhe a oferenda.", "You take back the offering.")
    t(m + "altar_hint", "Coloque uma oferenda e use a Adaga Ritual à noite. (Agache para ver o círculo.)",
      "Place an offering and use the Ritual Dagger at night. (Sneak to see the circle.)")
    t(m + "already_bound", "Um Patrono já está preso neste círculo.", "A Patron is already bound to this circle.")
    t(m + "pending_elsewhere", "Você ainda deve um tributo a %s.", "You still owe a tribute to %s.")
    t(m + "not_night", "Os Patronos só atendem sob o manto da noite.", "Patrons answer only under the cloak of night.")
    t(m + "sated", "Os Patronos já atenderam ao seu sangue esta noite. Volte na próxima.",
      "The Patrons have already tasted your blood tonight. Return next night.")
    t(m + "no_offering", "O altar está vazio. Coloque uma oferenda primeiro.", "The altar is empty. Place an offering first.")
    t(m + "circle_incomplete", "O %s (pacto nº %s) está incompleto: faltam %s giz e %s de %s velas acesas.",
      "The %s (pact #%s) is incomplete: %s chalk and %s of %s lit candles missing.")
    t(m + "too_weak", "Você está fraco demais para oferecer o seu sangue.", "You are too weak to offer your blood.")
    t(m + "ritual_begins", "O sangue toca o giz. Algo responde do outro lado: %s!", "Blood touches the chalk. Something answers: %s!")
    t(m + "guide", "Próximo pacto (nº %2$s) pede o %1$s: faltam %3$s giz e %4$s de %5$s velas acesas (marcados).",
      "Next pact (#%2$s) needs the %1$s: %3$s chalk and %4$s of %5$s lit candles missing (marked).")
    t(m + "guide_ready", "O %s está pronto para o pacto nº %s. Volte à noite com uma oferenda.",
      "The %s is ready for pact #%s. Return at night with an offering.")
    t(m + "auto_drawn", "Você desenhou %s runas do %s.", "You drew %s runes of the %s.")
    t(m + "patron_says", "[%s] %s", "[%s] %s")
    t(m + "accepted", "Pacto firmado com %s. Tributo: %s", "Pact struck with %s. Tribute: %s")
    t(m + "sacrifice_cannot", "Você não tem os %s níveis exigidos.", "You do not have the %s levels required.")
    t(m + "sacrifice_hearts", "Você cedeu %s corações ao Patrono por %s minutos.", "You surrendered %s hearts to the Patron for %s minutes.")
    t(m + "sacrifice_levels", "Você sacrificou %s níveis de experiência.", "You sacrificed %s experience levels.")
    t(m + "sacrifice_ended", "O seu vigor retorna.", "Your vigor returns.")
    t(m + "deliver_missing", "Ainda faltam %s× %s.", "Still missing %s× %s.")
    t(m + "deliver_progress", "O tributo ainda não foi cumprido (%s/%s).", "The tribute is not fulfilled yet (%s/%s).")
    t(m + "demand_progress", "Tributo: %s %s/%s", "Tribute: %s %s/%s")
    t(m + "demand_fulfilled", "Tributo cumprido! Volte ao círculo de %s (%s, %s, %s) para selar o pacto.",
      "Tribute fulfilled! Return to the circle of %s (%s, %s, %s) to seal the pact.")
    t(m + "demand_failed", "O prazo acabou... %s vai se libertar!", "Time is up... %s will break free!")
    t(m + "pact_expired", "O pacto nº %s expirou e o Patrono partiu.", "Pact #%s expired and the Patron is gone.")
    t(m + "pact_sealed", "PACTO SELADO com %s! (pactos selados: %s)", "PACT SEALED with %s! (pacts sealed: %s)")
    t(m + "pact_sealed_minor", "Pacto menor selado com %s. (pactos selados: %s)", "Minor pact sealed with %s. (pacts sealed: %s)")
    t(m + "glyph_taught", "O Patrono sussurra um segredo: %s", "The Patron whispers a secret: %s")
    t(m + "no_glyphs", "Nenhum segredo novo de Pacto foi revelado; o Patrono paga em poder.",
      "No new Pact secret was revealed; the Patron pays in power instead.")
    t(m + "boon", "Dádiva: %s (nível %s) — %s", "Boon: %s (level %s) — %s")
    t(m + "soul_harvested", "Alma colhida (%s)", "Soul harvested (%s)")
    for key, p, e in (("refused", "Você recusou %s. Ele ataca!", "You refused %s. It attacks!"),
                      ("circle", "O círculo foi quebrado! %s está livre!", "The circle was broken! %s is free!"),
                      ("timeout", "O prazo acabou e %s se libertou!", "The deadline passed and %s broke free!"),
                      ("failed", "Você falhou o tributo. %s se libertou!", "You failed the tribute. %s broke free!"),
                      ("impatient", "%s perdeu a paciência e se libertou!", "%s lost patience and broke free!")):
        t(m + "broken_" + key, p, e)

    gpre = "gui.isekai_mahou.warlock."
    t(gpre + "accept", "Aceitar o Pacto", "Accept the Pact")
    t(gpre + "refuse", "Recusar", "Refuse")
    t(gpre + "deliver", "Entregar Tributo", "Offer Tribute")
    t(gpre + "close", "Fechar", "Close")
    t(gpre + "pact_number", "Pacto nº %s", "Pact #%s")
    t(gpre + "demand_title", "Tributo exigido", "Tribute demanded")
    t(gpre + "paid_now", "Pago no momento em que aceitar.", "Paid the moment you accept.")
    t(gpre + "deadline_minutes", "Prazo: %s minutos.", "Deadline: %s minutes.")
    t(gpre + "deadline_day", "Prazo: 1 dia.", "Deadline: 1 day.")
    t(gpre + "favor", "Sua oferenda generosa abrandou o preço.", "Your generous offering softened the price.")
    t(gpre + "progress", "Progresso: %s/%s · resta %s", "Progress: %s/%s · %s left")
    t(gpre + "progress_done", "Cumprido (%s/%s)! Entregue antes do fim do prazo (%s).", "Fulfilled (%s/%s)! Seal it before the deadline (%s).")
    t(gpre + "have_items", "Você tem %s/%s · resta %s", "You have %s/%s · %s left")
    t(gpre + "patience", "Paciência: %s", "Patience: %s")
    t(gpre + "hud_title", "Pacto: %s", "Pact: %s")
    t(gpre + "hud_status", "%s/%s · ⌛ %s", "%s/%s · ⌛ %s")
    t(gpre + "hud_done", "Cumprido! Volte ao círculo.", "Fulfilled! Return to the circle.")
    t(gpre + "hud_failed", "Prazo esgotado!", "Out of time!")

    t("command.isekai_mahou.warlock.cleared", "Pacto pendente, sacrifícios e espera do ritual apagados.",
      "Pending pact, sacrifices and ritual cooldown cleared.")
    t("command.isekai_mahou.warlock.pacts", "Pactos selados definidos para %s.", "Sealed pacts set to %s.")

    # Dadivas
    t("boon.isekai_mahou.ember_lord", "Pele de Brasa", "Ember Skin")
    t("boon.isekai_mahou.void_lady", "Olhos do Vazio", "Eyes of the Void")
    t("boon.isekai_mahou.drowned_king", "Fôlego do Rei", "King's Breath")
    for lvl in (1, 2, 3):
        t("boon.isekai_mahou.ember_lord.desc.%d" % lvl,
          "queima %d%% menos tempo e sofre %d%% menos dano de fogo" % (20 * lvl, 12 * lvl),
          "burn %d%% shorter and take %d%% less fire damage" % (20 * lvl, 12 * lvl))
        t("boon.isekai_mahou.drowned_king.desc.%d" % lvl,
          "+%d de fôlego, nado e mineração submersa melhores" % lvl,
          "+%d oxygen, better swimming and underwater mining" % lvl)
    t("boon.isekai_mahou.void_lady.desc.1", "visão noturna durante a noite", "night vision during the night")
    t("boon.isekai_mahou.void_lady.desc.2", "visão noturna à noite; Endermen não se irritam com o seu olhar",
      "night vision at night; Endermen ignore your gaze")
    t("boon.isekai_mahou.void_lady.desc.3", "visão noturna, Endermen calmos e quedas mais seguras",
      "night vision, calm Endermen and safer falls")

    # Falas dos Patronos
    lines = {
        "ember_lord": {
            "epithet": ("Senhor das Chamas Eternas", "Sovereign of the Eternal Flame"),
            "greet": [("Quem ousa queimar o próprio sangue para me chamar?", "Who dares burn their own blood to call me?"),
                      ("Ahh... o cheiro de cinzas e ambição. Fale, mortal.", "Ahh... the scent of ash and ambition. Speak, mortal."),
                      ("As brasas me trouxeram. Que seja breve.", "The embers brought me here. Make it brief.")],
            "demand": [("Poder não se dá. Se compra. Este é o meu preço.", "Power is not given. It is bought. This is my price."),
                       ("Alimente o meu fogo e eu alimentarei o seu.", "Feed my fire and I shall feed yours.")],
            "accept": [("Então está feito. Não me faça esperar.", "Then it is done. Do not keep me waiting."),
                       ("O contrato arde. Cumpra-o antes que se apague.", "The contract burns. Fulfill it before it fades.")],
            "sealed": [("O pacto está selado em chamas. Queime em meu nome!", "The pact is sealed in flame. Burn in my name!"),
                       ("Você tem fogo de verdade. Volte quando quiser mais.", "You have true fire. Return when you crave more.")],
            "taunt": [("Sinto o gelo do seu medo daqui.", "I can feel the chill of your fear from here."),
                      ("Este giz não me prenderá para sempre.", "This chalk will not hold me forever."),
                      ("Tic, tac. As velas derretem.", "Tick, tock. The candles melt."),
                      ("Já queimei reinos por menos.", "I have burned kingdoms for less.")],
            "waiting": [("Trouxe o meu tributo, ou só veio se aquecer?", "Did you bring my tribute, or merely come to warm yourself?"),
                        ("Meu fogo está com fome.", "My fire is hungry.")],
            "missing": [("Isto é um insulto. Traga TUDO.", "This is an insult. Bring ALL of it.")],
            "stranger": [("Você não me chamou, verme. Saia da minha luz.", "You did not call me, worm. Out of my light.")],
            "enrage": [("BASTA! Vou reduzir tudo a cinzas!", "ENOUGH! I will reduce everything to ash!")],
            "death": [("As brasas... sempre... reacendem...", "The embers... always... rekindle...")],
            "wild": [("Livre do círculo, livre para queimar!", "Free of the circle, free to burn!")],
            "freed_refused": [("Recusa?! Então pague com a sua carne!", "Refuse?! Then pay with your flesh!")],
            "freed_circle": [("O círculo se partiu! Agora EU decido o preço!", "The circle is broken! Now I set the price!")],
            "freed_timeout": [("O prazo acabou. E você também.", "Time is up. And so are you.")],
            "freed_failed": [("Falhou comigo? Ninguém falha comigo duas vezes.", "You failed me? No one fails me twice.")],
            "freed_impatient": [("Eu não espero mortais indecisos!", "I do not wait on dithering mortals!")],
        },
        "void_lady": {
            "epithet": ("Rainha do Silêncio Entre as Estrelas", "Queen of the Silence Between Stars"),
            "greet": [("Shhh... você ouviu o vazio chamar e respondeu.", "Shhh... you heard the void calling, and you answered."),
                      ("Tão pequeno, tão cheio de vontade. Que curioso.", "So small, so full of want. How curious."),
                      ("Abriu uma porta que não sabe fechar, criança.", "You opened a door you cannot close, child.")],
            "demand": [("Todo segredo do vazio tem um preço. Este é o seu.", "Every secret of the void has a price. This is yours."),
                       ("Dê-me algo que você não pode ter de volta.", "Give me something you cannot take back.")],
            "accept": [("Eu estarei observando... sempre.", "I will be watching... always."),
                       ("Vá. As estrelas contam os seus passos.", "Go. The stars are counting your steps.")],
            "sealed": [("Agora parte do vazio vive em você. Não tenha medo do escuro.", "Now part of the void lives in you. Do not fear the dark."),
                       ("Pacto selado. Sussurre meu nome e eu ouvirei.", "Pact sealed. Whisper my name and I will hear.")],
            "taunt": [("As velas tremem. Você também.", "The candles tremble. So do you."),
                      ("Quanto tempo até o giz se apagar?", "How long until the chalk fades?"),
                      ("Eu já vi o fim de mundos maiores que este.", "I have seen the end of worlds larger than this."),
                      ("Seus pensamentos são tão... barulhentos.", "Your thoughts are so... loud.")],
            "waiting": [("Voltou. Trouxe o que eu pedi?", "You returned. Did you bring what I asked?"),
                        ("O silêncio está impaciente.", "The silence grows impatient.")],
            "missing": [("Incompleto. O vazio não aceita metades.", "Incomplete. The void accepts no halves.")],
            "stranger": [("Você não é quem me chamou. Desvie o olhar.", "You are not the one who called me. Look away.")],
            "enrage": [("Vou apagar você das estrelas!", "I will erase you from the stars!")],
            "death": [("Eu... volto... ao silêncio...", "I... return... to silence...")],
            "wild": [("Que estrela deixou a porta aberta?", "Which star left the door open?")],
            "freed_refused": [("Recusou o vazio? Então o vazio vai até você.", "You refused the void? Then the void comes to you.")],
            "freed_circle": [("Correntes quebradas... que delícia.", "Broken chains... how delightful.")],
            "freed_timeout": [("O tempo acabou para você, pequeno.", "Your time has run out, little one.")],
            "freed_failed": [("Promessas vazias merecem um fim vazio.", "Empty promises deserve an empty end.")],
            "freed_impatient": [("Indecisão é um tipo de recusa.", "Indecision is a kind of refusal.")],
        },
        "drowned_king": {
            "epithet": ("Soberano das Profundezas Esquecidas", "Sovereign of the Forgotten Deep"),
            "greet": [("Glub... quem tirou o Rei do fundo do mar?", "Glub... who dragged the King from the sea floor?"),
                      ("A maré me trouxe. A maré sempre cobra.", "The tide brought me. The tide always collects."),
                      ("Ajoelhe-se diante do trono afogado, terrestre.", "Kneel before the drowned throne, landwalker.")],
            "demand": [("Meu reino precisa de tributo. Pague.", "My kingdom requires tribute. Pay."),
                       ("O oceano dá e o oceano toma. Agora, você dá.", "The ocean gives and the ocean takes. Now, you give.")],
            "accept": [("Um acordo de rei! Não me desonre.", "A king's bargain! Do not dishonor me."),
                       ("A maré espera... mas não para sempre.", "The tide waits... but not forever.")],
            "sealed": [("Pacto selado! As águas agora te reconhecem.", "Pact sealed! The waters now know your name."),
                       ("Você tem o respeito do trono afogado.", "You have the respect of the drowned throne.")],
            "taunt": [("Este ar seco me dá coceira.", "This dry air makes me itch."),
                      ("Meus afogados estão com saudade de companhia.", "My drowned subjects long for company."),
                      ("O giz vai lavar com a próxima chuva...", "The chalk will wash away with the next rain..."),
                      ("Já afundei frotas inteiras por menos.", "I have sunk whole fleets for less.")],
            "waiting": [("Então? Onde está o meu tributo?", "Well? Where is my tribute?"),
                        ("O Rei espera. O Rei não gosta de esperar.", "The King waits. The King hates waiting.")],
            "missing": [("Isto mal enche um balde! Traga o resto.", "This barely fills a bucket! Bring the rest.")],
            "stranger": [("Você não me chamou, peixinho. Some daqui.", "You did not summon me, little fish. Begone.")],
            "enrage": [("ÀS PROFUNDEZAS COM VOCÊ!", "TO THE DEPTHS WITH YOU!")],
            "death": [("A maré... vai... voltar...", "The tide... will... return...")],
            "wild": [("Terra firme! Que horror. Alguém vai pagar por isso.", "Dry land! How dreadful. Someone will pay for this.")],
            "freed_refused": [("Recusa o Rei?! Afogue-se!", "You refuse the King?! Drown!")],
            "freed_circle": [("O círculo rachou! A maré sobe!", "The circle cracked! The tide rises!")],
            "freed_timeout": [("A maré virou. Contra você.", "The tide has turned. Against you.")],
            "freed_failed": [("Dívida não paga se cobra em sangue.", "Unpaid debt is collected in blood.")],
            "freed_impatient": [("O Rei não espera ninguém!", "The King waits for no one!")],
        },
    }
    for pid, d in lines.items():
        pre = "patron.isekai_mahou.%s." % pid
        ep = d.pop("epithet")
        t(pre + "epithet", ep[0], ep[1])
        for key, variants in d.items():
            for n, (p, e) in enumerate(variants):
                t(pre + "%s.%d" % (key, n), p, e)

    write_json(os.path.join(LANG, "pt_br.json"), pt)
    write_json(os.path.join(LANG, "en_us.json"), en)
    return pt


def main():
    gen_chalk()
    gen_altar()
    gen_items()
    build_ember()
    build_void()
    build_drowned()
    gen_animations()
    gen_data()
    gen_lang()
    print("ritual_warlock: assets gerados")


if __name__ == "__main__":
    main()
