"""
Gera os assets e dados do modulo ritual_druid (Bosque Sagrado / Arvore Coracao).
Uso (na raiz do projeto):  py -I tools/gen/ritual_druid.py

Saidas:
  assets/isekai_mahou/textures/block/{ancestral_sapling,heart_tree_*}.png   blocos (com camadas emissivas de runas)
  assets/isekai_mahou/textures/item/ancestral_sapling.png
  assets/isekai_mahou/{blockstates,models/block,models/item}/...
  assets/isekai_mahou/geo/entity/animal_spirit_<deer|wolf|hawk>.geo.json   (GeckoLib, Bedrock 1.12.0, UV de caixa)
  assets/isekai_mahou/animations/entity/animal_spirit_<...>.animation.json
  assets/isekai_mahou/textures/entity/animal_spirit_<...>.png               (pintadas a partir do proprio modelo)
  data/isekai_mahou/{loot_table/blocks,recipe,tags/block}/...
  src/main/merge/ritual_druid/data/minecraft/tags/block/{mineable/axe,mineable/hoe,wither_immune,dragon_immune}.json
  src/main/lang/ritual_druid/{pt_br,en_us}.json
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
LANG = os.path.join(ROOT, "src", "main", "lang", "ritual_druid")
MERGE = os.path.join(ROOT, "src", "main", "merge", "ritual_druid")
NS = "isekai_mahou"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def with_alpha(c, a):
    return (c[0], c[1], c[2], a)


def tex(kind, name):
    return os.path.join(ASSETS, "textures", kind, name + ".png")


# =====================================================================================================
#  TEXTURAS DE BLOCO
# =====================================================================================================
BARK = [rgb("#3A281E"), rgb("#523A2B"), rgb("#6B4D38"), rgb("#86644A"), rgb("#A2805F")]
GLOW = [rgb("#E6FFF0"), rgb("#B4FFD4"), rgb("#6CF0A8"), rgb("#34C47E")]


def bark(seed, darker_center=False):
    """Casca em placas verticais separadas por sulcos (que serpenteiam um pouco), com rachaduras e realces."""
    rnd = random.Random(seed)
    c = Canvas(16, 16)
    base_grooves = [0, 5, 11]
    wiggle = [[0] * 16 for _ in base_grooves]
    for gi in range(len(base_grooves)):
        w, y = 0, 0
        while y < 16:
            run = rnd.randint(3, 6)
            for k in range(run):
                if y + k < 16:
                    wiggle[gi][y + k] = w
            y += run
            w = rnd.choice((0, 1)) if w == 0 else 0
    cracks = {(rnd.randint(0, 2), rnd.randint(1, 14)) for _ in range(4)}
    for y in range(16):
        grooves = sorted((g + wiggle[i][y]) % 16 for i, g in enumerate(base_grooves))
        for x in range(16):
            if x in grooves:
                col = BARK[0]
            else:
                # posição dentro da placa
                left = max([g for g in grooves if g < x], default=grooves[-1] - 16)
                right = min([g for g in grooves if g > x], default=grooves[0] + 16)
                r, n = x - left - 1, right - left - 1
                plate = sum(1 for g in grooves if g < x) % 3
                if r == 0:
                    col = BARK[2]
                elif r == n - 1:
                    col = BARK[1]
                elif r == 1:
                    col = BARK[4] if rnd.random() < 0.35 else BARK[3]
                else:
                    col = BARK[3] if rnd.random() < 0.55 else BARK[2]
                if (plate, y) in cracks:
                    col = BARK[1] if r not in (0, n - 1) else BARK[0]
            if darker_center:
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 6:
                    col = darken(col, 0.3 * (1 - d / 6))
            c.set(x, y, col)
    return c


def log_top(core=False):
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if x in (0, 15) or y in (0, 15):
                col = BARK[1] if (x + y) % 3 else BARK[0]
            elif d > 6.6:
                col = BARK[2]
            else:
                ring = int(d * 1.4) % 2
                col = rgb("#CDA77D") if ring == 0 else rgb("#AE875F")
                if d < 1.6:
                    col = rgb("#8C6A4F")
            c.set(x, y, col)
    if core:
        # cavidade escura onde o coracao brilha (o brilho vem da camada emissiva)
        for y in range(16):
            for x in range(16):
                if math.hypot(x - 7.5, y - 7.5) < 3.2:
                    c.set(x, y, rgb("#22160F"))
    c.noise(0.05, seed=21)
    return c


def log_top_glow():
    c = Canvas(16, 16)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        c.set(x, y, GLOW[1])
    for (x, y) in ((7, 6), (8, 9), (6, 8), (9, 7)):
        c.set(x, y, with_alpha(GLOW[3], 200))
    return c


def runes_overlay():
    """Runas que brilham na casca (camada emissiva)."""
    c = Canvas(16, 16)
    # Runa 1 (feoh): haste com dois ramos
    for y in range(3, 10):
        c.set(3, y, GLOW[1])
    c.set(4, 4, GLOW[2]); c.set(5, 3, GLOW[2])
    c.set(4, 6, GLOW[2]); c.set(5, 5, GLOW[2])
    c.set(3, 2, with_alpha(GLOW[3], 170)); c.set(3, 10, with_alpha(GLOW[3], 170))
    # Runa 2 (othala): losango com pernas
    for (x, y) in ((11, 8), (10, 9), (12, 9), (9, 10), (13, 10), (10, 11), (12, 11), (11, 12)):
        c.set(x, y, GLOW[1])
    c.set(10, 13, GLOW[2]); c.set(12, 13, GLOW[2]); c.set(9, 14, with_alpha(GLOW[3], 170)); c.set(13, 14, with_alpha(GLOW[3], 170))
    c.set(11, 10, with_alpha(GLOW[0], 220))
    # Veias de seiva ligando as runas
    for (x, y) in ((4, 11), (5, 12), (6, 12), (7, 13), (8, 13)):
        c.set(x, y, with_alpha(GLOW[3], 150))
    for (x, y) in ((7, 2), (8, 3), (8, 4), (9, 5)):
        c.set(x, y, with_alpha(GLOW[3], 130))
    return c


def core_side():
    c = bark(77, darker_center=True)
    # cavidade em forma de gota/olho no centro
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 4.2, (y - 8.0) / 5.6
            d = dx * dx + dy * dy
            if d < 1.0:
                c.set(x, y, rgb("#1B110C") if d < 0.75 else rgb("#2C1D14"))
            elif d < 1.35:
                c.set(x, y, BARK[0])
    return c


HEART_ROWS = [
    ".##...##.",
    "####.####",
    "#########",
    "#########",
    ".#######.",
    "..#####..",
    "...###...",
    "....#....",
]


def core_glow():
    """Coração de luz pulsando dentro da cavidade (camada emissiva)."""
    c = Canvas(16, 16)
    ox, oy = 3, 4
    for py, row in enumerate(HEART_ROWS):
        for px, ch in enumerate(row):
            if ch != "#":
                continue
            d = math.hypot(px - 3.0, (py - 2.5) * 1.1)
            col = GLOW[0] if d < 1.3 else GLOW[1] if d < 2.6 else GLOW[2] if d < 3.8 else GLOW[3]
            c.set(ox + px, oy + py, col)
    # pontinhos de runa em volta da cavidade
    for (x, y) in ((7, 1), (8, 1), (2, 7), (13, 7), (2, 9), (13, 9), (7, 14), (8, 14)):
        c.set(x, y, with_alpha(GLOW[2], 210))
    return c


def value_noise(seed, cells=4):
    """Ruído suave e repetível (lado a lado) de 16x16."""
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]
    step = 16 / cells
    out = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            gx, gy = x / step, y / step
            x0, y0 = int(gx) % cells, int(gy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            fx, fy = gx - int(gx), gy - int(gy)
            fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            a = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
            b = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
            out[y][x] = a * (1 - fy) + b * fy
    return out


def leaves(palette, seed, holes=0.42):
    """Folhas 16x16 (cutout): tufos com buracos, borda de cima iluminada e sombra embaixo."""
    rnd = random.Random(seed)
    dark, mid, light, hi = palette
    shape = value_noise(seed, 4)
    detail = value_noise(seed + 1, 8)
    c = Canvas(16, 16)
    solid = [[False] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v = shape[y][x] * 0.65 + detail[y][x] * 0.35 + (rnd.random() - 0.5) * 0.12
            solid[y][x] = v > holes
    for y in range(16):
        for x in range(16):
            if not solid[y][x]:
                continue
            up = solid[(y - 1) % 16][x]
            down = solid[(y + 1) % 16][x]
            v = detail[y][x] + (rnd.random() - 0.5) * 0.25
            col = light if v > 0.62 else mid if v > 0.38 else dark
            if not up:
                col = hi if rnd.random() < 0.6 else light   # borda de cima pega luz
            elif not down:
                col = dark                                  # borda de baixo fica na sombra
            c.set(x, y, col)
    return c


def leaves_glow(base, count, color, seed):
    rnd = random.Random(seed)
    c = Canvas(16, 16)
    placed = 0
    while placed < count:
        x, y = rnd.randint(0, 15), rnd.randint(0, 15)
        if base.get(x, y)[3] == 0 or c.get(x, y)[3] > 0:
            continue
        c.set(x, y, color)
        placed += 1
    return c


SAPLING_ROWS = [
    "................",
    "..s......y......",
    "........ygy...s.",
    ".......ygwgo....",
    ".....LLgyggLL...",
    "....LllLggolLl..",
    "...dlLllLlllld..",
    "...ddllLllldd...",
    "....dddlTtddd...",
    "......d.Tt.d....",
    "........Tt......",
    ".......tTt......",
    "........Tt......",
    "........tT......",
    ".......tTTt.....",
    "......tT..Tt....",
]


def sapling(item=False):
    pal = {"g": rgb("#F2C64A"), "y": rgb("#FFF0A0"), "o": rgb("#C98F22"), "w": rgb("#FFFFFF"),
           "L": rgb("#8CEB86"), "l": rgb("#4FC064"), "d": rgb("#2E8A45"),
           "t": rgb("#8A6140"), "T": rgb("#5A3C26"), "s": rgb("#E8FFE0", 210)}
    c = Canvas(16, 16)
    c.from_rows([r.ljust(16, ".")[:16] for r in SAPLING_ROWS], pal)
    if item:
        # torrão de terra e contorno para ler bem no inventário
        for x in range(5, 12):
            c.set(x, 15, rgb("#5B3B24") if x % 2 else rgb("#6E4A2E"))
        c.outline(rgb("#173A20", 170))
    return c


def gen_block_textures():
    bark(11).save(tex("block", "heart_tree_log"))
    runes_overlay().save(tex("block", "heart_tree_log_runes"))
    log_top().save(tex("block", "heart_tree_log_top"))
    log_top_glow().save(tex("block", "heart_tree_log_top_glow"))
    core_side().save(tex("block", "heart_tree_core"))
    core_glow().save(tex("block", "heart_tree_core_glow"))
    log_top(core=True).save(tex("block", "heart_tree_core_top"))
    green = leaves([rgb("#2C7A3C"), rgb("#45A64B"), rgb("#6FCC62"), rgb("#B8F59A")], seed=5)
    green.save(tex("block", "heart_tree_leaves"))
    leaves_glow(green, 7, rgb("#E2FFC8"), 6).save(tex("block", "heart_tree_leaves_glow"))
    gold = leaves([rgb("#B0701A"), rgb("#DDA02C"), rgb("#F6CB4E"), rgb("#FFF2A6")], seed=8, holes=0.3)
    gold.save(tex("block", "heart_tree_leaves_golden"))
    leaves_glow(gold, 14, rgb("#FFFBE2"), 9).save(tex("block", "heart_tree_leaves_golden_glow"))
    sapling().save(tex("block", "ancestral_sapling"))
    sapling(item=True).save(tex("item", "ancestral_sapling"))


# =====================================================================================================
#  MODELOS DE BLOCO / BLOCKSTATES / ITENS
# =====================================================================================================
def ref(name):
    return f"{NS}:block/{name}"


def cube_faces(side, end, cull=True, glow=False):
    faces = {}
    for d in ("down", "up", "north", "south", "west", "east"):
        t = end if d in ("down", "up") else side
        if t is None:
            continue
        f = {"uv": [0, 0, 16, 16], "texture": t}
        if cull:
            f["cullface"] = d
        faces[d] = f
    return faces


def overlay_model(base_side, base_end, glow_side, glow_end, render_type, particle):
    elements = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": cube_faces("#side", "#end")}]
    textures = {"particle": particle, "side": base_side, "end": base_end}
    if glow_side or glow_end:
        if glow_side:
            textures["glow_side"] = glow_side
        if glow_end:
            textures["glow_end"] = glow_end
        elements.append({
            "from": [0, 0, 0], "to": [16, 16, 16],
            "neoforge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False},
            "faces": cube_faces("#glow_side" if glow_side else None, "#glow_end" if glow_end else None),
        })
    return {"parent": "minecraft:block/block", "render_type": render_type, "textures": textures, "elements": elements}


def gen_models():
    bs = os.path.join(ASSETS, "blockstates")
    mb = os.path.join(ASSETS, "models", "block")
    mi = os.path.join(ASSETS, "models", "item")

    # ---- Tronco (com runas emissivas) ----
    write_json(os.path.join(mb, "heart_tree_log.json"), overlay_model(
        ref("heart_tree_log"), ref("heart_tree_log_top"), ref("heart_tree_log_runes"), ref("heart_tree_log_top_glow"),
        "minecraft:cutout", ref("heart_tree_log")))
    m = f"{NS}:block/heart_tree_log"
    write_json(os.path.join(bs, "heart_tree_log.json"), {"variants": {
        "axis=y": {"model": m}, "axis=z": {"model": m, "x": 90}, "axis=x": {"model": m, "x": 90, "y": 90}}})
    write_json(os.path.join(mi, "heart_tree_log.json"), {"parent": m})

    # ---- Nucleo (coracao emissivo) ----
    write_json(os.path.join(mb, "heart_tree_core.json"), overlay_model(
        ref("heart_tree_core"), ref("heart_tree_core_top"), ref("heart_tree_core_glow"), ref("heart_tree_log_top_glow"),
        "minecraft:cutout", ref("heart_tree_core")))
    write_json(os.path.join(bs, "heart_tree_core.json"), {"variants": {"": {"model": f"{NS}:block/heart_tree_core"}}})
    write_json(os.path.join(mi, "heart_tree_core.json"), {"parent": f"{NS}:block/heart_tree_core"})

    # ---- Folhas (verdes e douradas, com brilhos emissivos) ----
    for name in ("heart_tree_leaves", "heart_tree_leaves_golden"):
        model = overlay_model(ref(name), ref(name), ref(name + "_glow"), ref(name + "_glow"),
                              "minecraft:cutout_mipped", ref(name))
        model["parent"] = "minecraft:block/block"
        write_json(os.path.join(mb, name + ".json"), model)
    write_json(os.path.join(bs, "heart_tree_leaves.json"), {"variants": {
        "golden=false": {"model": f"{NS}:block/heart_tree_leaves"},
        "golden=true": {"model": f"{NS}:block/heart_tree_leaves_golden"}}})
    write_json(os.path.join(mi, "heart_tree_leaves.json"), {"parent": f"{NS}:block/heart_tree_leaves"})

    # ---- Muda Ancestral: cruz levemente luminosa ----
    light = {"block_light": 11, "sky_light": 11}
    write_json(os.path.join(mb, "ancestral_sapling.json"), {
        "ambientocclusion": False,
        "render_type": "minecraft:cutout",
        "textures": {"particle": ref("ancestral_sapling"), "cross": ref("ancestral_sapling")},
        "elements": [
            {"from": [0.8, 0, 8], "to": [15.2, 16, 8],
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}, "shade": False,
             "neoforge_data": light,
             "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#cross"}, "south": {"uv": [0, 0, 16, 16], "texture": "#cross"}}},
            {"from": [8, 0, 0.8], "to": [8, 16, 15.2],
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}, "shade": False,
             "neoforge_data": light,
             "faces": {"west": {"uv": [0, 0, 16, 16], "texture": "#cross"}, "east": {"uv": [0, 0, 16, 16], "texture": "#cross"}}},
        ]})
    write_json(os.path.join(bs, "ancestral_sapling.json"), {"variants": {"": {"model": f"{NS}:block/ancestral_sapling"}}})
    write_json(os.path.join(mi, "ancestral_sapling.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/ancestral_sapling"}})

    # ---- Ovo do espirito ----
    write_json(os.path.join(mi, "animal_spirit_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================================
#  DADOS: loot, receita, tags
# =====================================================================================================
SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
SHEARS = {"ability": "shears_dig", "condition": "neoforge:can_item_perform_ability"}


def loot(name, entries_conditions, item=None):
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                   "entries": [{"type": "minecraft:item", "name": item or f"{NS}:{name}"}],
                   "conditions": entries_conditions}],
        "random_sequence": f"{NS}:blocks/{name}"})


def gen_data():
    loot("ancestral_sapling", [{"condition": "minecraft:survives_explosion"}])
    loot("heart_tree_core", [{"condition": "minecraft:survives_explosion"}], item=f"{NS}:ancestral_sapling")
    loot("heart_tree_log", [SILK])
    loot("heart_tree_leaves", [{"condition": "minecraft:any_of", "terms": [SHEARS, SILK]}])

    write_json(os.path.join(DATA, "recipe", "ancestral_sapling.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" A ", "BSB", " E "],
        "key": {"A": {"item": "minecraft:golden_apple"}, "B": {"item": "minecraft:bone_meal"},
                "S": {"tag": "minecraft:saplings"}, "E": {"item": "minecraft:emerald"}},
        "result": {"id": f"{NS}:ancestral_sapling", "count": 1}})

    write_json(os.path.join(DATA, "tags", "block", "heart_tree_corruption.json"), {"values": [
        "minecraft:netherrack", "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:crimson_nylium",
        "minecraft:warped_nylium", "minecraft:magma_block", "minecraft:nether_wart_block", "minecraft:warped_wart_block",
        "minecraft:wither_rose", "minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_catalyst",
        "minecraft:sculk_shrieker", "minecraft:fire", "minecraft:soul_fire", "minecraft:lava", "minecraft:blackstone",
        "minecraft:crying_obsidian",
        {"id": f"{NS}:abyssal_stone", "required": False},
        {"id": f"{NS}:void_crystal", "required": False}]})

    write_json(os.path.join(MERGE, "data", "minecraft", "tags", "block", "mineable", "axe.json"),
               {"values": [f"{NS}:heart_tree_log", f"{NS}:heart_tree_core"]})
    write_json(os.path.join(MERGE, "data", "minecraft", "tags", "block", "mineable", "hoe.json"),
               {"values": [f"{NS}:heart_tree_leaves"]})
    # O Núcleo é a "vida" do druida: Wither e Dragão não podem destruí-lo
    for tag in ("wither_immune", "dragon_immune"):
        write_json(os.path.join(MERGE, "data", "minecraft", "tags", "block", tag + ".json"),
                   {"values": [f"{NS}:heart_tree_core"]})


# =====================================================================================================
#  ESPIRITOS ANIMAIS (GeckoLib): modelo, UV de caixa automatico, textura pintada pelo modelo, animacoes
# =====================================================================================================
class Geo:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []      # dicts
        self.cubes = []      # (bone_index, origin, size, paint)

    def bone(self, name, parent, pivot, cubes):
        b = {"name": name, "pivot": pivot}
        if parent:
            b["parent"] = parent
        self.bones.append(b)
        for (origin, size, paint) in cubes:
            self.cubes.append((len(self.bones) - 1, origin, size, paint))

    def pack(self):
        """Empacota as caixas de UV (prateleiras) e devolve o tamanho da textura."""
        items = []
        for i, (_, origin, size, paint) in enumerate(self.cubes):
            w, h, d = (int(math.ceil(s)) for s in size)
            items.append((i, 2 * (d + w), d + h))
        for tw, th in ((64, 64), (128, 64), (128, 128)):
            uv = {}
            x = y = shelf = 0
            ok = True
            for (i, bw, bh) in sorted(items, key=lambda t: -t[2]):
                if x + bw > tw:
                    x, y, shelf = 0, y + shelf, 0
                if y + bh > th or bw > tw:
                    ok = False
                    break
                uv[i] = (x, y)
                x += bw
                shelf = max(shelf, bh)
            if ok:
                return tw, th, uv
        raise RuntimeError("modelo grande demais para a textura")

    def build(self, alpha, sparkle, seed):
        tw, th, uv = self.pack()
        canvas = Canvas(tw, th)
        rnd = random.Random(seed)
        bones = [dict(b, cubes=[]) for b in self.bones]
        for i, (bi, origin, size, paint) in enumerate(self.cubes):
            u, v = uv[i]
            bones[bi]["cubes"].append({"origin": origin, "size": size, "uv": [u, v]})
            paint_box(canvas, u, v, [int(math.ceil(s)) for s in size], paint, alpha, sparkle, rnd)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": tw, "texture_height": th,
                            "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
            "bones": bones}]}
        return geo, canvas


def paint_box(c, u, v, size, paint, alpha, sparkle, rnd):
    """Pinta as 6 faces da caixa de UV: topo claro, base escura, laterais em degrade; detalhes por face."""
    w, h, d = size
    base = paint["color"]
    top, bottom = lighten(base, 0.25), darken(base, 0.25)
    # topo (u+d, v) e base (u+d+w, v)
    c.rect(u + d, v, u + d + w - 1, v + d - 1, with_alpha(top, alpha))
    c.rect(u + d + w, v, u + d + 2 * w - 1, v + d - 1, with_alpha(bottom, alpha))
    # laterais: oeste (u), norte/frente (u+d), leste (u+d+w), sul/tras (u+2d+w)
    for yy in range(h):
        t = yy / max(1, h - 1)
        col = lighten(base, 0.15 * (1 - t)) if t < 0.5 else darken(base, 0.2 * (t - 0.5) * 2)
        band = paint.get("band")
        if band and yy >= h - band[0]:
            col = band[1]
        c.rect(u, v + d + yy, u + 2 * (d + w) - 1, v + d + yy, with_alpha(col, alpha))
    # brilhos (manchas de luz no corpo do espirito)
    if paint.get("sparkle", True):
        for _ in range(max(1, (w * h + d * h) // 10)):
            x = rnd.randint(u, u + 2 * (d + w) - 1)
            y = rnd.randint(v + d, v + d + h - 1)
            c.set(x, y, sparkle)
    # olhos e focinho na face da frente (norte = -Z)
    fx, fy = u + d, v + d
    for (ex, ey, col) in paint.get("front", []):
        c.set(fx + ex, fy + ey, col)
    # faixa clara na barriga (base)
    if paint.get("belly"):
        c.rect(u + d + w, v, u + d + 2 * w - 1, v + d - 1, with_alpha(paint["belly"], alpha))
    # pintas claras no dorso e no alto das laterais (filhote de cervo)
    if paint.get("spots"):
        for _ in range(paint["spots"][1]):
            x = rnd.randint(u + d, u + d + w - 1)
            y = rnd.randint(v, v + d - 1)
            c.set(x, y, with_alpha(paint["spots"][0], 255))
        for _ in range(paint["spots"][1]):
            x = rnd.randint(u, u + 2 * (d + w) - 1)
            y = rnd.randint(v + d, v + d + max(1, h // 2) - 1)
            c.set(x, y, with_alpha(paint["spots"][0], 255))
    # listra escura no dorso (lobo)
    if paint.get("back_stripe"):
        mid = u + d + w // 2
        c.rect(mid - 1, v, mid, v + d - 1, with_alpha(paint["back_stripe"], alpha))
        for yy in range(min(2, h)):
            c.rect(u, v + d + yy, u + d - 1, v + d + yy, with_alpha(paint["back_stripe"], alpha))
            c.rect(u + d + w, v + d + yy, u + 2 * d + w - 1, v + d + yy, with_alpha(paint["back_stripe"], alpha))
    # penas: barras ao longo da asa (topo e base), clareando para a ponta
    if paint.get("feathers"):
        light_c, dark_c = paint["feathers"]
        for face_u in (u + d, u + d + w):
            for xx in range(w):
                t = xx / max(1, w - 1)
                for yy in range(d):
                    col = mix(base, light_c, t * 0.7)
                    if xx % 2 == 1:
                        col = darken(col, 0.12)
                    if yy == d - 1:
                        col = dark_c
                    c.set(face_u + xx, v + yy, with_alpha(col, alpha))
    # contorno escuro da boca/olhos já vem em "front"; marcas extras (bochechas, testa)
    for (ex, ey, col) in paint.get("marks", []):
        c.set(u + d + ex, v + ey, col)


def deer():
    body = {"color": rgb("#9EEBCB"), "belly": rgb("#E6FFF4"), "spots": (rgb("#F4FFFA"), 9)}
    neck = {"color": rgb("#AEF0D4"), "spots": (rgb("#F4FFFA"), 2)}
    leg = {"color": rgb("#A3E9CC"), "band": (2, rgb("#4FB58F"))}
    antler = {"color": rgb("#FFE07A"), "sparkle": False, "band": (1, rgb("#E8B640"))}
    eye = rgb("#FFF7B0")
    head = {"color": rgb("#B4F3D9"), "front": [(0, 1, eye), (3, 1, eye), (0, 2, rgb("#3E9C7A")), (3, 2, rgb("#3E9C7A"))],
            "marks": [(1, 1, rgb("#F4FFFA")), (2, 1, rgb("#F4FFFA"))]}
    snout = {"color": rgb("#E2FFF2"), "front": [(1, 0, rgb("#3E9C7A")), (1, 1, rgb("#2F7F62"))], "sparkle": False}
    ear = {"color": rgb("#8FE2C0"), "sparkle": False}
    g = Geo("animal_spirit_deer")
    g.bone("root", None, [0, 0, 0], [])
    g.bone("body", "root", [0, 13, 0], [([-3, 10, -6], [6, 6, 12], body)])
    g.bone("neck", "body", [0, 15, -5], [([-1.5, 14, -7.5], [3, 7, 3], neck)])
    g.bone("head", "neck", [0, 21, -6], [([-2, 20, -10], [4, 4, 5], head), ([-1.5, 20, -12], [3, 3, 2], snout),
                                         ([2, 22.5, -7], [2, 1, 1], ear), ([-4, 22.5, -7], [2, 1, 1], ear)])
    g.bone("antlers", "head", [0, 24, -7], [
        ([1, 24, -7.5], [1, 4, 1], antler), ([2, 27, -7.5], [2, 1, 1], antler), ([3, 28, -7.5], [1, 3, 1], antler),
        ([1, 26, -8.5], [1, 1, 1], antler),
        ([-2, 24, -7.5], [1, 4, 1], antler), ([-4, 27, -7.5], [2, 1, 1], antler), ([-4, 28, -7.5], [1, 3, 1], antler),
        ([-2, 26, -8.5], [1, 1, 1], antler)])
    g.bone("tail", "body", [0, 15, 6], [([-1, 13, 6], [2, 3, 1], {"color": rgb("#F4FFFA"), "sparkle": False})])
    g.bone("leg_fl", "body", [2, 10, -4], [([1, 0, -5], [2, 10, 2], leg)])
    g.bone("leg_fr", "body", [-2, 10, -4], [([-3, 0, -5], [2, 10, 2], leg)])
    g.bone("leg_bl", "body", [2, 10, 4], [([1, 0, 3], [2, 10, 2], leg)])
    g.bone("leg_br", "body", [-2, 10, 4], [([-3, 0, 3], [2, 10, 2], leg)])
    return g


def wolf():
    body = {"color": rgb("#A9C2FA"), "belly": rgb("#EEF4FF"), "back_stripe": rgb("#6F8FDB")}
    mane = {"color": rgb("#DCE7FF")}
    leg = {"color": rgb("#B3CAFA"), "band": (1, rgb("#6F8FDB"))}
    eye = rgb("#9BFFF4")
    head = {"color": rgb("#BCD0FB"), "front": [(1, 1, eye), (3, 1, eye), (2, 3, rgb("#EEF4FF")), (2, 4, rgb("#EEF4FF"))],
            "marks": [(2, 0, rgb("#6F8FDB")), (2, 1, rgb("#6F8FDB"))]}
    snout = {"color": rgb("#E4EDFF"), "front": [(1, 0, rgb("#3E5FAE")), (0, 2, rgb("#6F8FDB")), (2, 2, rgb("#6F8FDB"))],
             "sparkle": False}
    ear = {"color": rgb("#8EAAF0"), "sparkle": False}
    g = Geo("animal_spirit_wolf")
    g.bone("root", None, [0, 0, 0], [])
    g.bone("body", "root", [0, 9, 0], [([-3, 6, -5], [6, 6, 10], body)])
    g.bone("mane", "body", [0, 9, -4], [([-3.5, 5.5, -6], [7, 7, 4], mane)])
    g.bone("head", "body", [0, 11, -6], [([-2.5, 9, -10], [5, 5, 4], head), ([-1.5, 9, -13], [3, 3, 3], snout),
                                         ([-2.5, 14, -8], [2, 2, 1], ear), ([0.5, 14, -8], [2, 2, 1], ear)])
    g.bone("tail", "body", [0, 11, 5], [([-1, 10, 5], [2, 2, 7], {"color": rgb("#C9D9FF"), "band": (1, rgb("#F4F8FF"))})])
    g.bone("leg_fl", "body", [2, 6, -3], [([1, 0, -4], [2, 6, 2], leg)])
    g.bone("leg_fr", "body", [-2, 6, -3], [([-3, 0, -4], [2, 6, 2], leg)])
    g.bone("leg_bl", "body", [2, 6, 4], [([1, 0, 3], [2, 6, 2], leg)])
    g.bone("leg_br", "body", [-2, 6, 4], [([-3, 0, 3], [2, 6, 2], leg)])
    return g


def hawk():
    body = {"color": rgb("#FFE59A"), "belly": rgb("#FFF8E0")}
    wing = {"color": rgb("#F7B940"), "feathers": (rgb("#FFF0C0"), rgb("#C98A1E")), "sparkle": False}
    tip = {"color": rgb("#FFF0C0"), "feathers": (rgb("#FFFFFF"), rgb("#E0A030")), "sparkle": False}
    eye = rgb("#FFFFFF")
    head = {"color": rgb("#FFF3CF"), "front": [(0, 1, eye), (2, 1, eye), (0, 0, rgb("#C98A1E")), (2, 0, rgb("#C98A1E"))]}
    beak = {"color": rgb("#FF9E2C"), "sparkle": False, "band": (1, rgb("#C96A10"))}
    talon = {"color": rgb("#F2A93B"), "sparkle": False}
    g = Geo("animal_spirit_hawk")
    g.bone("root", None, [0, 0, 0], [])
    g.bone("body", "root", [0, 5, 0], [([-2, 3, -3], [4, 4, 7], body)])
    g.bone("head", "body", [0, 7, -3], [([-1.5, 6, -6], [3, 3, 3], head), ([-0.5, 6, -8], [1, 2, 2], beak)])
    g.bone("left_wing", "body", [2, 7, 0], [([2, 6.5, -2], [9, 1, 5], wing), ([11, 6.5, -1], [3, 1, 4], tip)])
    g.bone("right_wing", "body", [-2, 7, 0], [([-11, 6.5, -2], [9, 1, 5], wing), ([-14, 6.5, -1], [3, 1, 4], tip)])
    g.bone("tail", "body", [0, 5, 4], [([-2, 4.5, 4], [4, 1, 4], tip)])
    g.bone("legs", "body", [0, 3, 0], [([-1.5, 1, 0], [1, 2, 1], talon), ([0.5, 1, 0], [1, 2, 1], talon)])
    return g


def wave(length, amp, phase=0.0, steps=8, axis=0, base=(0, 0, 0)):
    """Keyframes de uma senoide (em graus ou pixels) num eixo, em loop."""
    kf = {}
    for i in range(steps + 1):
        t = length * i / steps
        val = amp * math.sin(2 * math.pi * i / steps + phase)
        vec = list(base)
        vec[axis] = round(base[axis] + val, 3)
        kf[f"{round(t, 4)}"] = vec
    return kf


def anims(ident, spec):
    out = {}
    for name, (length, bones) in spec.items():
        out[f"animation.{ident}.{name}"] = {"loop": True, "animation_length": length, "bones": bones}
    return {"format_version": "1.8.0", "animations": out}


def gen_spirits():
    sparkle = {"deer": rgb("#FFFFFF"), "wolf": rgb("#FFFFFF"), "hawk": rgb("#FFFFFF")}
    models = {"deer": deer(), "wolf": wolf(), "hawk": hawk()}
    for k, g in models.items():
        geo, canvas = g.build(alpha=225, sparkle=sparkle[k], seed=len(k) * 17)
        write_json(os.path.join(ASSETS, "geo", "entity", f"animal_spirit_{k}.geo.json"), geo)
        canvas.save(tex("entity", f"animal_spirit_{k}"))

    pi = math.pi
    deer_a = anims("animal_spirit_deer", {
        "idle": (2.4, {
            "root": {"position": wave(2.4, 0.8, axis=1)},
            "neck": {"rotation": wave(2.4, 4, phase=0.5)},
            "tail": {"rotation": wave(0.6, 20, axis=1, steps=4)},
            "head": {"rotation": wave(2.4, 3, phase=1.2, axis=2)}}),
        "walk": (0.8, {
            "root": {"position": wave(0.4, 0.6, axis=1, steps=4)},
            "leg_fl": {"rotation": wave(0.8, 35)}, "leg_br": {"rotation": wave(0.8, 35)},
            "leg_fr": {"rotation": wave(0.8, 35, phase=pi)}, "leg_bl": {"rotation": wave(0.8, 35, phase=pi)},
            "neck": {"rotation": wave(0.4, 4, steps=4)},
            "tail": {"rotation": wave(0.4, 15, axis=1, steps=4)}}),
    })
    wolf_a = anims("animal_spirit_wolf", {
        "idle": (2.0, {
            "root": {"position": wave(2.0, 0.6, axis=1)},
            "tail": {"rotation": wave(0.5, 25, axis=1, steps=4, base=(-20, 0, 0))},
            "head": {"rotation": wave(2.0, 6, phase=0.8, axis=2)}}),
        "walk": (0.6, {
            "root": {"position": wave(0.3, 0.5, axis=1, steps=4)},
            "leg_fl": {"rotation": wave(0.6, 40)}, "leg_br": {"rotation": wave(0.6, 40)},
            "leg_fr": {"rotation": wave(0.6, 40, phase=pi)}, "leg_bl": {"rotation": wave(0.6, 40, phase=pi)},
            "tail": {"rotation": wave(0.3, 15, axis=1, steps=4, base=(-10, 0, 0))},
            "head": {"rotation": wave(0.3, 3, steps=4)}}),
    })
    hawk_a = anims("animal_spirit_hawk", {
        "idle": (1.6, {
            "root": {"position": wave(1.6, 0.8, axis=1)},
            "left_wing": {"rotation": wave(1.6, 10, axis=2, base=(0, 0, -10))},
            "right_wing": {"rotation": wave(1.6, -10, axis=2, base=(0, 0, 10))},
            "tail": {"rotation": wave(1.6, 5)}}),
        "fly": (0.5, {
            "root": {"position": wave(0.5, 1.2, phase=pi / 2, axis=1)},
            "left_wing": {"rotation": wave(0.5, 35, axis=2)},
            "right_wing": {"rotation": wave(0.5, -35, axis=2)},
            "tail": {"rotation": wave(0.5, 6, phase=pi / 2)},
            "head": {"rotation": wave(0.5, 3, phase=pi)}}),
    })
    for k, a in (("deer", deer_a), ("wolf", wolf_a), ("hawk", hawk_a)):
        write_json(os.path.join(ASSETS, "animations", "entity", f"animal_spirit_{k}.animation.json"), a)


# =====================================================================================================
#  TRADUCOES
# =====================================================================================================
L = {}


def t(key, pt, en):
    L[key] = (pt, en)


def gen_lang():
    M = "message.isekai_mahou.ritual_druid."
    G = "gui.isekai_mahou.ritual_druid."
    C = "command.isekai_mahou.ritual_druid."
    t("block.isekai_mahou.ancestral_sapling", "Muda Ancestral", "Ancestral Sapling")
    t("block.isekai_mahou.heart_tree_core", "Núcleo da Árvore Coração", "Heart Tree Core")
    t("block.isekai_mahou.heart_tree_log", "Tronco da Árvore Coração", "Heart Tree Log")
    t("block.isekai_mahou.heart_tree_leaves", "Folhas da Árvore Coração", "Heart Tree Leaves")
    t("item.isekai_mahou.animal_spirit_spawn_egg", "Ovo de Espírito Animal", "Animal Spirit Spawn Egg")
    t("entity.isekai_mahou.animal_spirit", "Espírito Animal", "Animal Spirit")
    t("entity.isekai_mahou.animal_spirit.deer", "Espírito do Cervo", "Stag Spirit")
    t("entity.isekai_mahou.animal_spirit.wolf", "Espírito do Lobo", "Wolf Spirit")
    t("entity.isekai_mahou.animal_spirit.hawk", "Espírito do Falcão", "Hawk Spirit")

    t("tooltip.isekai_mahou.ancestral_sapling", "Plante em grama ou terra: germina na Árvore Coração do Druida.",
      "Plant on grass or dirt: it sprouts into the Druid's Heart Tree.")
    t("tooltip.isekai_mahou.ancestral_sapling.unique", "Só uma Árvore Coração por jogador.", "Only one Heart Tree per player.")
    t("tooltip.isekai_mahou.heart_tree_core", "Clique para ver a Harmonia do bosque. Clique com a oferenda para entregá-la.",
      "Click to see the grove's Harmony. Click while holding the offering to give it.")

    t(M + "planted", "A Muda Ancestral se enraíza... a terra pulsa.", "The Ancestral Sapling takes root... the earth pulses.")
    t(M + "only_one", "Você já tem uma Árvore Coração em %s %s %s.", "You already have a Heart Tree at %s %s %s.")
    t(M + "too_close", "Outra Árvore Coração cresce perto demais: plante a pelo menos %s blocos dela.",
      "Another Heart Tree grows too close: plant at least %s blocks away from it.")
    t(M + "germinated", "A Árvore Coração despertou! Um espírito vem ao seu encontro.",
      "The Heart Tree has awakened! A spirit comes to meet you.")
    t(M + "regrowing", "A árvore se lembra de você e volta a crescer até o estágio %s.",
      "The tree remembers you and regrows up to stage %s.")
    t(M + "claimed", "Esta Árvore Coração agora está ligada a você.", "This Heart Tree is now bound to you.")
    t(M + "already_have", "Esta árvore não tem dono, mas você já cuida de outra.", "This tree has no keeper, but you already tend another.")
    t(M + "still_growing", "A árvore ainda está crescendo... espere.", "The tree is still growing... wait.")
    t(M + "offering_full", "A árvore já recebeu %s suficiente.", "The tree has already received enough %s.")
    t(M + "offering_progress", "Oferenda: %s (%s/%s)", "Offering: %s (%s/%s)")
    t(M + "offering_complete", "Oferenda completa! (%s %s/%s)", "Offering complete! (%s %s/%s)")
    t(M + "tree_lost", "Sua Árvore Coração se desfez em pétalas. Plante a Muda Ancestral de novo para recomeçar (seu progresso continua).",
      "Your Heart Tree dissolved into petals. Plant the Ancestral Sapling again to start over (your progress is kept).")
    t(M + "complete", "A Árvore Coração está completa.", "The Heart Tree is complete.")
    t(M + "not_ready", "O bosque ainda não está pronto para crescer.", "The grove is not ready to grow yet.")
    t(M + "stage_title", "Árvore Coração — Estágio %s", "Heart Tree — Stage %s")
    t(M + "stage_subtitle", "%s desperta", "%s awakens")
    t(M + "stage_up", "A Árvore Coração cresceu! Estágio %s.", "The Heart Tree has grown! Stage %s.")
    t(M + "glyph_gift", "Os espíritos te ensinam a Metamorfose: %s", "The spirits teach you Metamorphosis: %s")
    t(M + "no_glyph", "(Nenhuma Metamorfose nova para aprender agora.)", "(No new Metamorphosis to learn right now.)")
    t(M + "tree_grown", "Uma muda que você plantou virou árvore! (%s no total)", "A sapling you planted grew into a tree! (%s total)")
    t(M + "blessing", "%s te abençoa: Regeneração, Sorte e o dom do animal.", "%s blesses you: Regeneration, Luck and the animal's gift.")

    t(M + "spirit.greet.deer", "O Cervo de luz inclina a cabeça para você.", "The stag of light bows its head to you.")
    t(M + "spirit.greet.wolf", "O Lobo de luz uiva para a lua.", "The wolf of light howls at the moon.")
    t(M + "spirit.greet.hawk", "O Falcão de luz circula sobre você.", "The hawk of light circles above you.")
    lines = [
        ("Pequeno guardião, a semente acordou. Cuide do bosque e ele cuidará de você.",
         "Little guardian, the seed has awakened. Tend the grove and it will tend to you."),
        ("Suas raízes já tocam a terra. A Terra responde ao seu chamado.",
         "Your roots already touch the earth. Earth answers your call."),
        ("A matilha cresce onde há fartura. Que os filhotes corram pelo seu bosque.",
         "The pack grows where there is plenty. Let the young run through your grove."),
        ("Do alto eu vejo seus campos. A Água agora corre em você.",
         "From above I see your fields. Water now flows within you."),
        ("Os galhos se abrem como braços. Plante mais, guardião, o bosque precisa de irmãos.",
         "The branches open like arms. Plant more, guardian, the grove needs siblings."),
        ("A Luz desce pelas folhas. Poucos druidas chegam tão longe.",
         "Light descends through the leaves. Few druids come this far."),
        ("As primeiras folhas douradas nasceram. Seu bosque é lembrado pelos ventos.",
         "The first golden leaves are born. Your grove is remembered by the winds."),
        ("A árvore já é mais velha que muitas aldeias. Continue plantando o amanhã.",
         "The tree is already older than many villages. Keep planting tomorrow."),
        ("O lobo e o homem caçam juntos quando a terra é generosa. Você é parte da matilha.",
         "Wolf and man hunt together when the land is generous. You are part of the pack."),
        ("A copa dourada toca as nuvens. Falta só o último inverno.",
         "The golden crown touches the clouds. Only the last winter remains."),
        ("Grande Druida, a Árvore Coração está completa. Todos os espíritos do bosque respondem a você.",
         "Great Druid, the Heart Tree is complete. Every spirit of the grove answers to you."),
    ]
    for i, (pt, en) in enumerate(lines):
        t(M + f"spirit.line.{i}", pt, en)
    t(M + "spirit.final", "Não há mais estágios: o bosque é eterno enquanto você cuidar dele.",
      "There are no more stages: the grove is eternal while you tend it.")
    t(M + "spirit.next", "Próximo estágio %s — %s:", "Next stage %s — %s:")
    t(M + "spirit.offering", "Oferenda: %s", "Offering: %s")
    t(M + "spirit.harmony", "Harmonia do bosque: %s ou mais", "Grove Harmony: %s or more")
    t(M + "spirit.wander", "O espírito te observa em silêncio e deixa um pouco de sorte no ar.",
      "The spirit watches you in silence and leaves a little luck in the air.")

    t(G + "title", "Árvore Coração", "Heart Tree")
    t(G + "stage", "Estágio %s", "Stage %s")
    t(G + "owner", "de %s", "of %s")
    t(G + "growing_note", "A árvore está crescendo...", "The tree is growing...")
    t(G + "harmony", "Harmonia do bosque: %s / %s", "Grove Harmony: %s / %s")
    t(G + "harmony_plain", "Harmonia do bosque: %s", "Grove Harmony: %s")
    cats = {
        "flowers": ("Flores", "Flowers", "%s tipos diferentes", "%s different kinds",
                    "Plante flores de tipos variados num raio de 16 blocos.", "Plant many kinds of flowers within 16 blocks."),
        "trees": ("Árvores", "Trees", "%s espécies (mudas e troncos)", "%s species (saplings and logs)",
                  "Mudas e árvores de espécies diferentes (carvalho, bétula, cerejeira...).",
                  "Saplings and trees of different species (oak, birch, cherry...)."),
        "crops": ("Plantações", "Crops", "%s tipos de cultivo", "%s kinds of crops",
                  "Trigo, cenoura, batata, beterraba, frutas silvestres, cacau...", "Wheat, carrots, potatoes, beetroots, berries, cocoa..."),
        "animals": ("Animais", "Animals", "%s espécies por perto", "%s species nearby",
                    "Traga animais de espécies diferentes para o bosque.", "Bring animals of different species to the grove."),
        "water": ("Água", "Water", "%s blocos de água", "%s water blocks",
                  "Um lago ou riacho perto da árvore.", "A pond or stream near the tree."),
        "corruption": ("Corrupção", "Corruption", "%s monstros, %s blocos corrompidos", "%s monsters, %s corrupted blocks",
                       "Afaste monstros e remova netherrack, sculk, fogo e lava do bosque.",
                       "Drive monsters away and remove netherrack, sculk, fire and lava from the grove."),
    }
    for k, (pt, en, dpt, den, hpt, hen) in cats.items():
        t(G + f"cat.{k}", pt, en)
        t(G + f"cat.{k}.detail", dpt, den)
        t(G + f"cat.{k}.hint", hpt, hen)
    t(G + "cat_max", "%s de no máximo %s pontos", "%s of at most %s points")
    t(G + "offering", "Oferenda", "Offering")
    t(G + "offering_done", "A árvore aceitou a oferenda.", "The tree accepted the offering.")
    t(G + "offering_hint", "Clique no núcleo segurando o item para oferecê-lo.", "Click the core while holding the item to offer it.")
    t(G + "task_header", "Tarefa de estação — %s", "Season task — %s")
    t(G + "task.trees", "Plante %s árvores (mudas que crescerem)", "Plant %s trees (saplings that grow)")
    t(G + "task.harvest", "Colha %s plantações maduras", "Harvest %s ripe crops")
    t(G + "task.breed", "Reproduza %s animais", "Breed %s animals")
    t(G + "season.spring", "Primavera", "Spring")
    t(G + "season.summer", "Verão", "Summer")
    t(G + "season.autumn", "Outono", "Autumn")
    t(G + "season.winter", "Inverno", "Winter")
    t(G + "task_unknown", "(o guardião desta árvore não está por perto)", "(this tree's keeper is not nearby)")
    t(G + "spirit_waits", "%s aguarda o próximo estágio.", "%s awaits the next stage.")
    t(G + "not_owner", "Só o guardião da árvore pode fazê-la crescer.", "Only the tree's keeper can make it grow.")
    t(G + "grow", "Fazer a árvore crescer", "Make the tree grow")
    t(G + "growing", "Crescendo...", "Growing...")
    t(G + "complete_note", "Todos os espíritos do bosque já despertaram.", "Every spirit of the grove has awakened.")
    t(G + "done", "entregue", "delivered")
    t(G + "pending", "pendente", "pending")

    t(C + "no_tree", "Você não tem uma Árvore Coração.", "You don't have a Heart Tree.")
    t(C + "rank", "Rank do Druida definido para %s.", "Druid rank set to %s.")
    t(C + "info", "Druida — estágio %s de %s", "Druid — stage %s of %s")
    t(C + "where", "Árvore Coração em %s %s %s", "Heart Tree at %s %s %s")
    t(C + "status", "Harmonia %s/%s · %s: %s/%s · Oferenda: %s", "Harmony %s/%s · %s: %s/%s · Offering: %s")

    pt = {k: v[0] for k, v in sorted(L.items())}
    en = {k: v[1] for k, v in sorted(L.items())}
    write_json(os.path.join(LANG, "pt_br.json"), pt)
    write_json(os.path.join(LANG, "en_us.json"), en)


if __name__ == "__main__":
    gen_block_textures()
    gen_models()
    gen_data()
    gen_spirits()
    gen_lang()
    print("ritual_druid: assets gerados.")
