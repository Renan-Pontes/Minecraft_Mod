"""
Gera os assets e dados do modulo structures_b:
  estruturas de classe (Torre do Mago Anciao, Cabana da Bruxa Ancia, Altar Profano, Circulo Druidico,
  Cripta Esquecida) e pequenas (Ruinas Arcanas, Acampamento do Mago Errante, Lago das Fadas):
  .nbt, worldgen jigsaw, loot tables, tags de bioma (merge), fenomenos do Mago, texturas/modelos dos blocos.
Uso (na raiz do projeto):  py -I tools/gen/structures_b.py
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402
from structgen import Structure, Int, Byte  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/structures_b")
NS = "isekai_mahou"
T = (0, 0, 0, 0)
AIR = "minecraft:cave_air"   # ar "explicito" (o structgen nao salva minecraft:air)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def tex(name):
    return os.path.join(A, "textures", name + ".png")


# =====================================================================================
#  Texturas
# =====================================================================================
def glowbloom_textures():
    stem_d, stem_m, stem_l = hex_rgba("245A2C"), hex_rgba("3A8A40"), hex_rgba("6CC46A")
    leaf_d, leaf_m, leaf_l = hex_rgba("2C6E34"), hex_rgba("4FA552"), hex_rgba("8FDA7A")

    # Textura em cruz (caule + folhas). Pontas dos galhos em u=4 e u=12, v=8 (onde ficam os botoes).
    c = Canvas(16, 16)
    for y in range(3, 16):
        c.set(8, y, stem_m)
        c.set(7, y, stem_d if y > 9 else T)
    c.set(8, 3, stem_l)
    # galhos simetricos
    for (x0, y0, x1, y1) in ((8, 11, 6, 9), (6, 9, 4, 8), (8, 11, 10, 9), (10, 9, 12, 8)):
        c.line(x0, y0, x1, y1, stem_m)
    c.set(4, 8, stem_l); c.set(12, 8, stem_l)
    # folhas baixas
    leaf_rows = [
        "..............",
        "..LL......LL..",
        ".LMML....LMML.",
        "LMMDD....DDMML",
        ".DDD..MM..DDD.",
        "......MD......",
    ]
    c.from_rows(leaf_rows, {"L": leaf_l, "M": leaf_m, "D": leaf_d}, ox=1, oy=10)
    # folhinhas no meio do caule
    c.set(9, 6, leaf_m); c.set(10, 5, leaf_l); c.set(7, 7, leaf_m); c.set(6, 6, leaf_l)
    c.save(tex("block/fairy_glowbloom"))

    # Botao luminoso: gradiente radial (usado com uv central nos cubinhos emissivos)
    b = Canvas(16, 16)
    core, mid, edge = hex_rgba("FFFFD2"), hex_rgba("EFFF7A"), hex_rgba("9BE04A")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8.0
            col = mix(core, mid, min(1.0, d * 1.6)) if d < 0.62 else mix(mid, edge, min(1.0, (d - 0.62) / 0.5))
            b.set(x, y, col)
    b.save(tex("block/fairy_glowbloom_bulb"))

    # Item: planta inteira com os tres botoes e um halo suave
    it = Canvas(16, 16)
    for y in range(4, 16):
        it.set(8, y, stem_m)
    it.line(8, 11, 4, 8, stem_m); it.line(8, 11, 12, 8, stem_m)
    it.from_rows(leaf_rows, {"L": leaf_l, "M": leaf_m, "D": leaf_d}, ox=1, oy=10)
    halo = (230, 255, 140, 90)
    for (cx, cy) in ((8, 3), (4, 7), (12, 7)):
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                if abs(dx) + abs(dy) == 2 or (abs(dx) == 2 and dy == 0) or (abs(dy) == 2 and dx == 0):
                    if it.get(cx + dx, cy + dy)[3] == 0:
                        it.set(cx + dx, cy + dy, halo)
        it.rect(cx - 1, cy - 1, cx + 1, cy + 1, mid)
        it.set(cx, cy, core); it.set(cx - 1, cy - 1, core)
        it.set(cx + 1, cy + 1, edge)
    it.save(tex("item/fairy_glowbloom"))

    # Particula de vaga-lume: ponto branco com halo (a cor vem da particula)
    p = Canvas(8, 8)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
            a = 255 if d < 1.2 else (170 if d < 2.0 else (70 if d < 3.0 else 0))
            if a:
                p.set(x, y, (255, 255, 255, a))
    p.save(tex("particle/fairy_firefly"))


def sarcophagus_textures():
    base, dark, light = hex_rgba("4B5053"), hex_rgba("2F3336"), hex_rgba("6E7579")
    moss, bone, bone_d = hex_rgba("5C7A5E"), hex_rgba("D9D2BC"), hex_rgba("A79F86")
    rnd = random.Random(7)

    def stone(c, x0, y0, x1, y1):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                r = rnd.random()
                c.set(x, y, light if r < 0.08 else (dark if r < 0.16 else base))

    def lid_band(c):
        stone(c, 0, 0, 15, 4)
        for x in range(16):
            c.set(x, 2, light); c.set(x, 4, dark)

    # Lateral: faixa da tampa (linhas 2-4) + painel entalhado (5-15)
    s = Canvas(16, 16)
    lid_band(s)
    stone(s, 0, 5, 15, 15)
    for x in range(16):
        s.set(x, 5, dark); s.set(x, 15, dark)
    for y in range(5, 16):
        s.set(0, y, dark); s.set(15, y, dark)
    for x in range(1, 15):
        s.set(x, 7, darken(base, 0.25)); s.set(x, 13, darken(base, 0.25))
    for x in range(2, 15, 4):   # runas entalhadas em losango
        for (dx, dy) in ((1, 9), (0, 10), (2, 10), (1, 11)):
            s.set(x + dx, dy, dark)
        s.set(x + 1, 10, light)
    for (x, y) in ((1, 14), (2, 14), (14, 6), (13, 14)):
        s.set(x, y, moss)
    s.save(tex("block/crypt_sarcophagus_side"))

    # Ponta: caveira em relevo
    e = Canvas(16, 16)
    lid_band(e)
    stone(e, 0, 5, 15, 15)
    for x in range(16):
        e.set(x, 5, dark); e.set(x, 15, dark)
    for y in range(5, 16):
        e.set(0, y, dark); e.set(15, y, dark)
    skull = [
        "..LLLL..",
        ".LBBBBL.",
        "LBDBBDBL",
        "LBDBBDBL",
        ".LBBBBL.",
        "..BDDB..",
        "..B.B...",
    ]
    e.from_rows(skull, {"L": light, "B": lighten(base, 0.25), "D": dark}, ox=4, oy=7)
    e.save(tex("block/crypt_sarcophagus_end"))

    # Tampa (cabeca): rosto de pedra de olhos fechados e bracos cruzados
    def lid_base():
        c = Canvas(16, 16)
        stone(c, 0, 0, 15, 15)
        for i in range(16):
            c.set(i, 0, light); c.set(0, i, light); c.set(i, 15, dark); c.set(15, i, dark)
        return c
    h = lid_base()
    relief, hi, deep = lighten(base, 0.18), lighten(base, 0.32), darken(base, 0.35)
    face = [
        "..LLLL..",
        ".LBBBBL.",
        "LBBBBBBL",
        "LBDDBDDL",
        "LBBBBBBL",
        ".LBDDBL.",
        "..LBBL..",
    ]
    h.from_rows(face, {"L": darken(base, 0.2), "B": relief, "D": deep}, ox=4, oy=1)
    # ombros e bracos cruzados segurando o cabo da espada (a lamina continua na tampa dos pes)
    for x in range(3, 13):
        h.set(x, 8, darken(base, 0.2))
    h.line(3, 9, 7, 12, relief); h.line(12, 9, 8, 12, relief)
    h.line(3, 10, 6, 12, darken(base, 0.2)); h.line(12, 10, 9, 12, darken(base, 0.2))
    for y in range(9, 16):
        h.set(7, y, hi); h.set(8, y, relief)
    h.rect(5, 13, 10, 13, hi)          # guarda da espada
    h.set(7, 9, light); h.set(8, 9, light)  # pomo
    h.save(tex("block/crypt_sarcophagus_lid_head"))

    # Tampa (pes): espada de pedra apontando para os pes, dobras do manto
    f = lid_base()
    for y in range(0, 10):
        f.set(7, y, lighten(base, 0.3)); f.set(8, y, lighten(base, 0.18))
    f.set(7, 10, light); f.set(8, 10, lighten(base, 0.18))
    for y in range(1, 12):
        f.set(4, y, darken(base, 0.2)); f.set(11, y, darken(base, 0.2))
    f.from_rows([".LL..LL.", "LBBLLBBL", "LBBLLBBL", ".LL..LL."],
                {"L": darken(base, 0.2), "B": lighten(base, 0.15)}, ox=4, oy=11)
    f.set(2, 13, moss); f.set(13, 3, moss)
    f.save(tex("block/crypt_sarcophagus_lid_foot"))

    # Interior (paredes internas): pedra escura e lisa
    def dark_stone():
        c = Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                c.set(x, y, hex_rgba("1F2124") if rnd.random() > 0.12 else hex_rgba("2A2D31"))
        return c
    dark_stone().save(tex("block/crypt_sarcophagus_inside"))

    # Fundo: UM esqueleto deitado ocupando as duas metades (linhas 0-15 na cabeca, 16-31 nos pes).
    # So as colunas 4..11 e as linhas 2..29 ficam visiveis (o resto fica sob as paredes internas).
    shroud, shroud_d = hex_rgba("3B2F2C"), hex_rgba("2C2321")
    skeleton = [
        "................",   # 0
        "................",   # 1
        "......BBBB......",   # 2  cranio
        ".....BHBBBB.....",   # 3
        ".....BDBBDB.....",   # 4  olhos
        ".....BBBBBB.....",   # 5
        "......BDDB......",   # 6  dentes
        ".......BB.......",   # 7  pescoco
        "....BBBBBBBB....",   # 8  ombros
        "....B..HB..B....",   # 9
        "....BBBBBBBB....",   # 10 costelas
        "....B..BB..B....",   # 11
        "....BBBBBBBB....",   # 12
        "....B..BB..B....",   # 13
        "....B.BBBB.B....",   # 14
        "....B..BB..B....",   # 15
        "....B..BB..B....",   # 16
        "....B.BBBB.B....",   # 17 bacia
        "....BBBDDBBB....",   # 18 maos sobre a bacia
        "......B..B......",   # 19 femures
        "......B..B......",   # 20
        "......H..H......",   # 21
        "......B..B......",   # 22
        ".....BB..BB.....",   # 23 joelhos
        "......B..B......",   # 24 tibias
        "......B..B......",   # 25
        "......B..H......",   # 26
        "......B..B......",   # 27
        ".....BB..BB.....",   # 28 pes
        ".....B....B.....",   # 29
        "................",   # 30
        "................",   # 31
    ]
    for part, rows in (("head", skeleton[:16]), ("foot", skeleton[16:])):
        fl = dark_stone()
        top = 0 if part == "head" else 16
        for y in range(16):   # mortalha puida por baixo dos ossos
            gy = top + y
            if 8 <= gy <= 26:
                for x in range(5, 11):
                    if rnd.random() < 0.85:
                        fl.set(x, y, shroud if (x + gy) % 3 else shroud_d)
        fl.from_rows(rows, {"B": bone, "D": bone_d, "H": lighten(bone, 0.12)}, ox=0, oy=0)
        fl.save(tex(f"block/crypt_sarcophagus_floor_{part}"))

    # Item: sarcofago visto de lado
    it = Canvas(16, 16)
    it.rect(1, 6, 14, 7, light)
    it.rect(1, 8, 14, 8, dark)
    it.rect(2, 9, 13, 13, base)
    it.rect(2, 14, 13, 14, dark)
    for x in (4, 8, 12):
        it.set(x, 11, dark); it.set(x - 1, 11, light)
    it.set(1, 6, base); it.set(14, 6, base)
    it.rect(6, 4, 9, 5, lighten(base, 0.2))   # rosto entalhado da tampa
    it.set(7, 5, dark); it.set(8, 5, dark)
    it.outline(hex_rgba("1A1C1E"))
    it.save(tex("item/crypt_sarcophagus"))


# =====================================================================================
#  Modelos e blockstates
# =====================================================================================
def face(uv, texture, **extra):
    d = {"uv": uv, "texture": texture}
    d.update(extra)
    return d


def glowbloom_models():
    emissive = {"block_light": 15, "sky_light": 15}

    def bulb(x, y, z, s=2):
        f = face([5, 5, 11, 11], "#bulb")
        return {"from": [x, y, z], "to": [x + s, y + s, z + s], "shade": False, "neoforge_data": emissive,
                "faces": {d: dict(f) for d in ("north", "south", "east", "west", "up", "down")}}

    cross_faces = {"uv": [0, 0, 16, 16], "texture": "#cross"}
    model = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {"particle": f"{NS}:block/fairy_glowbloom", "cross": f"{NS}:block/fairy_glowbloom",
                     "bulb": f"{NS}:block/fairy_glowbloom_bulb"},
        "elements": [
            {"from": [0.8, 0, 8], "to": [15.2, 16, 8], "shade": False,
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
             "faces": {"north": dict(cross_faces), "south": dict(cross_faces)}},
            {"from": [8, 0, 0.8], "to": [8, 16, 15.2], "shade": False,
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
             "faces": {"west": dict(cross_faces), "east": dict(cross_faces)}},
            bulb(7, 13, 7),
            bulb(3.2, 7.4, 3.2, 1.6), bulb(11.2, 7.4, 11.2, 1.6),
            bulb(3.2, 7.4, 11.2, 1.6), bulb(11.2, 7.4, 3.2, 1.6),
        ],
    }
    write_json(os.path.join(A, "models/block/fairy_glowbloom.json"), model)
    write_json(os.path.join(A, "blockstates/fairy_glowbloom.json"),
               {"variants": {"": {"model": f"{NS}:block/fairy_glowbloom"}}})
    write_json(os.path.join(A, "models/item/fairy_glowbloom.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/fairy_glowbloom"}})
    write_json(os.path.join(A, "particles/fairy_firefly.json"), {"textures": [f"{NS}:fairy_firefly"]})


def sarcophagus_models():
    tx = {"particle": f"{NS}:block/crypt_sarcophagus_side", "side": f"{NS}:block/crypt_sarcophagus_side",
          "end": f"{NS}:block/crypt_sarcophagus_end", "inside": f"{NS}:block/crypt_sarcophagus_inside"}

    def closed(head):
        outer = "north" if head else "south"
        t = dict(tx); t["lid"] = f"{NS}:block/crypt_sarcophagus_lid_" + ("head" if head else "foot")
        return {"parent": "minecraft:block/block", "textures": t, "elements": [
            {"from": [1, 0, 0], "to": [15, 11, 16], "faces": {
                "down": face([1, 0, 15, 16], "#side", cullface="down"),
                outer: face([1, 5, 15, 16], "#end"),
                "east": face([0, 5, 16, 16], "#side"),
                "west": face([0, 5, 16, 16], "#side")}},
            {"from": [0, 11, 0], "to": [16, 14, 16], "faces": {
                "up": face([0, 0, 16, 16], "#lid"),
                "down": face([0, 0, 16, 16], "#side"),
                outer: face([0, 2, 16, 5], "#end"),
                "east": face([0, 2, 16, 5], "#side"),
                "west": face([0, 2, 16, 5], "#side")}},
        ]}

    def opened(head):
        outer = "north" if head else "south"
        inner = "south" if head else "north"
        t = dict(tx); t["lid"] = f"{NS}:block/crypt_sarcophagus_lid_" + ("head" if head else "foot")
        t["floor"] = f"{NS}:block/crypt_sarcophagus_floor_" + ("head" if head else "foot")
        endwall = ([3, 2, 0], [13, 11, 2]) if head else ([3, 2, 14], [13, 11, 16])
        lid_rot = {"origin": [15, 11, 8], "axis": "z", "angle": -22.5}
        return {"parent": "minecraft:block/block", "textures": t, "elements": [
            {"from": [1, 0, 0], "to": [15, 2, 16], "faces": {
                "down": face([1, 0, 15, 16], "#side", cullface="down"),
                "up": face([1, 0, 15, 16], "#floor"),
                outer: face([1, 14, 15, 16], "#end"),
                "east": face([0, 14, 16, 16], "#side"),
                "west": face([0, 14, 16, 16], "#side")}},
            {"from": [1, 2, 0], "to": [3, 11, 16], "faces": {
                "up": face([1, 5, 3, 16], "#side"),
                outer: face([1, 5, 3, 14], "#end"),
                "west": face([0, 5, 16, 14], "#side"),
                "east": face([0, 5, 16, 14], "#inside")}},
            {"from": [13, 2, 0], "to": [15, 11, 16], "faces": {
                "up": face([13, 5, 15, 16], "#side"),
                outer: face([13, 5, 15, 14], "#end"),
                "east": face([0, 5, 16, 14], "#side"),
                "west": face([0, 5, 16, 14], "#inside")}},
            {"from": endwall[0], "to": endwall[1], "faces": {
                "up": face([3, 5, 13, 7], "#side"),
                outer: face([3, 5, 13, 14], "#end"),
                inner: face([3, 5, 13, 14], "#inside")}},
            {"from": [6, 11, 0], "to": [22, 14, 16], "rotation": lid_rot, "faces": {
                "up": face([0, 0, 16, 16], "#lid"),
                "down": face([0, 0, 16, 16], "#side"),
                "east": face([0, 2, 16, 5], "#side"),
                "west": face([0, 2, 16, 5], "#side"),
                outer: face([0, 2, 16, 5], "#end")}},
        ]}

    for head in (True, False):
        part = "head" if head else "foot"
        write_json(os.path.join(A, f"models/block/crypt_sarcophagus_{part}.json"), closed(head))
        write_json(os.path.join(A, f"models/block/crypt_sarcophagus_{part}_open.json"), opened(head))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for part in ("head", "foot"):
            for op in ("false", "true"):
                m = {"model": f"{NS}:block/crypt_sarcophagus_{part}" + ("_open" if op == "true" else "")}
                if rot:
                    m["y"] = rot
                variants[f"facing={facing},open={op},part={part}"] = m
    write_json(os.path.join(A, "blockstates/crypt_sarcophagus.json"), {"variants": variants})
    write_json(os.path.join(A, "models/item/crypt_sarcophagus.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/crypt_sarcophagus"}})


# =====================================================================================
#  Utilitarios de construcao
# =====================================================================================
CARD = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def dir_of(dx, dz):
    for k, v in CARD.items():
        if v == (dx, dz):
            return k
    raise ValueError((dx, dz))


def fill_empty(s, x0, y0, z0, x1, y1, z1, state=AIR, pred=None):
    """Preenche so as posicoes ainda nao definidas (para limpar o interior com ar explicito)."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                if s.get(x, y, z) is None and (pred is None or pred(x, y, z)):
                    s.set(x, y, z, state)


def connect_fences(s, cells, block):
    """Cercas/paineis/grades com as conexoes ja calculadas (a estrutura nao recalcula formas ao ser colocada):
    ligam entre si e em blocos solidos vizinhos (tijolos, tabuas, toras, pedra, la, ardosia)."""
    cells = set(cells)
    for (x, y, z) in cells:
        props = []
        for d, (dx, dz) in CARD.items():
            n = (x + dx, y, z + dz)
            nb = s.get(*n) or ""
            solid = (any(k in nb for k in ("bricks", "planks", "log", "stone", "wool", "deepslate"))
                     and not any(k in nb for k in ("slab", "stairs", "fence", "wall", "cave_air")))
            props.append(f"{d}={'true' if (n in cells or solid) else 'false'}")
        s.set(x, y, z, f"{block}[{','.join(props)}]")


def chest(s, x, y, z, table, facing="north", block="minecraft:chest"):
    s.set(x, y, z, f"{block}[facing={facing}]", nbt={"LootTable": f"{NS}:{table}"})


def barrel(s, x, y, z, table, facing="up"):
    s.set(x, y, z, f"minecraft:barrel[facing={facing},open=false]", nbt={"LootTable": f"{NS}:{table}"})


def pot(s, x, y, z, table, facing="north"):
    s.set(x, y, z, f"minecraft:decorated_pot[facing={facing},cracked=false]", nbt={"LootTable": f"{NS}:{table}"})


def brewing_stand(s, x, y, z, potions):
    """Suporte de pocoes com as garrafas de verdade no inventario: o estado has_bottle_N e recalculado
    pelo bloco no 1o tick, entao garrafas so no estado sumiriam assim que o jogador chegasse."""
    props, items = [], []
    for i in range(3):
        pid = potions[i] if i < len(potions) else None
        props.append(f"has_bottle_{i}={'true' if pid else 'false'}")
        if pid:
            items.append({"Slot": Byte(i), "id": "minecraft:potion", "count": Int(1),
                          "components": {"minecraft:potion_contents": {"potion": f"minecraft:{pid}"}}})
    s.set(x, y, z, f"minecraft:brewing_stand[{','.join(props)}]", nbt={"Items": items} if items else None)


def chiseled_bookshelf(s, x, y, z, facing, books):
    """Estante entalhada com livros reais nos espacos marcados (books: {slot: item})."""
    props = [f"facing={facing}"] + [f"slot_{i}_occupied={'true' if i in books else 'false'}" for i in range(6)]
    items = [{"Slot": Byte(i), "id": it, "count": Int(1)} for i, it in sorted(books.items())]
    s.set(x, y, z, f"minecraft:chiseled_bookshelf[{','.join(props)}]", nbt={"Items": items})


def boss(s, x, y, z, entity):
    s.entity(x + 0.5, y, z + 0.5, f"{NS}:{entity}", {"PersistenceRequired": True})


def stair_spiral(s, cx, cz, y_start, steps, block, ring=None):
    """Escada caracol 3x3 em volta de (cx,cz): um degrau por posicao do anel, subindo 1 por degrau.
    Retorna [(k, x, y, z)] dos degraus. O degrau 'olha' para o proximo (sobe-se andando nessa direcao)."""
    ring = ring or [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
    out = []
    for k in range(steps):
        dx, dz = ring[k % 8]
        nx, nz = ring[(k + 1) % 8]
        facing = dir_of(nx - dx, nz - dz)
        x, y, z = cx + dx, y_start + k, cz + dz
        s.set(x, y, z, f"{block}[facing={facing},half=bottom,shape=straight]")
        out.append((k, x, y, z))
    return out


def spiral_floor(s, cx, cz, y_floor, steps, floor_block, rail_block, y_start):
    """Ajusta o piso no nivel y_floor em volta da caracol: abre o vao sobre os degraus logo abaixo
    (altura livre), deixa o degrau de chegada e cerca o vao com corrimao."""
    ring = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
    k_arrive = y_floor - y_start
    holes = set()
    for d in (1, 2, 3):
        k = k_arrive - d
        if 0 <= k < steps:
            dx, dz = ring[k % 8]
            holes.add((cx + dx, cz + dz))
    arrive = None
    if 0 <= k_arrive < steps:
        dx, dz = ring[k_arrive % 8]
        arrive = (cx + dx, cz + dz)
    for (dx, dz) in ring:
        p = (cx + dx, cz + dz)
        if p in holes:
            s.set(p[0], y_floor, p[1], AIR)
        elif p != arrive:
            cur = s.get(p[0], y_floor, p[1])
            if cur is None or cur == AIR:
                s.set(p[0], y_floor, p[1], floor_block)
    # corrimao no anel 5x5 em volta do vao (menos junto da saida do degrau de chegada)
    rails = set()
    for (hx, hz) in holes:
        for (dx, dz) in CARD.values():
            q = (hx + dx, hz + dz)
            if max(abs(q[0] - cx), abs(q[1] - cz)) == 2:
                if arrive and abs(q[0] - arrive[0]) + abs(q[1] - arrive[1]) <= 1:
                    continue
                rails.add((q[0], y_floor + 1, q[1]))
    if rail_block:
        connect_fences(s, rails, rail_block)
    return holes


# =====================================================================================
#  Livro do Arquimago (atril da torre e loot da biblioteca)
# =====================================================================================
JOURNAL_PAGES = 4


def journal_item_nbt():
    pages = [json.dumps({"translate": f"book.{NS}.archmage_journal.page{i}"}) for i in range(1, JOURNAL_PAGES + 1)]
    return {
        "id": "minecraft:written_book", "count": Int(1),
        "components": {
            "minecraft:written_book_content": {"title": "Valdris", "author": "Valdris", "pages": pages,
                                               "resolved": Byte(1)},
            "minecraft:custom_name": json.dumps({"translate": f"book.{NS}.archmage_journal", "italic": False,
                                                 "color": "light_purple"}),
        },
    }


# =====================================================================================
#  Torre do Mago Anciao  (19x52x19: 3 andares + santuario no topo com o Arquimago Renegado)
# =====================================================================================
def mage_tower():
    S, H = 19, 52
    s = Structure(S, H, S)
    c, R = 9, 6
    rnd = random.Random(1101)
    floors = [10, 20, 30]
    top_ceiling = 39

    def d(x, z):
        return math.hypot(x - c, z - c)

    cells = [(x, z) for x in range(S) for z in range(S)]

    def wall(x, z):
        return R - 0.5 <= d(x, z) <= R + 0.5

    def inside(x, z):
        return d(x, z) < R - 0.5

    # base / plinto
    for (x, z) in cells:
        dd = d(x, z)
        if dd <= R + 1.5:
            if dd > R - 0.5:
                st = "minecraft:stone_bricks"
            elif dd < 1:
                st = "minecraft:chiseled_stone_bricks"
            else:
                st = "minecraft:polished_andesite" if int(dd) % 2 == 0 else "minecraft:andesite"
            s.set(x, 0, z, st)
        if R + 0.5 < dd <= R + 1.5:
            s.set(x, 1, z, "minecraft:stone_brick_slab[type=bottom]")
    # paredes (faixas de tijolo de ardosia na altura de cada piso)
    for y in range(1, top_ceiling):
        for (x, z) in cells:
            if wall(x, z):
                if y in floors or y == 1:
                    st = "minecraft:deepslate_bricks"
                elif y <= 3 and rnd.random() < 0.35:
                    st = "minecraft:mossy_stone_bricks"
                else:
                    st = "minecraft:stone_bricks"
                s.set(x, y, z, st)
    # pisos dos andares e teto do santuario
    for fy in floors:
        for (x, z) in cells:
            if inside(x, z):
                if fy == 30:
                    s.set(x, fy, z, "minecraft:polished_deepslate")
                else:
                    s.set(x, fy, z, "minecraft:spruce_planks" if (x + z) % 2 else "minecraft:dark_oak_planks")
    for (x, z) in cells:
        if inside(x, z):
            s.set(x, top_ceiling, z, "minecraft:deepslate_tiles")
    # janelas (paineis roxos) nos 4 pontos cardeais de cada andar (porta ao sul no terreo)
    panes = []
    for fy in [0] + floors:
        for (dx, dz) in CARD.values():
            if fy == 0 and (dx, dz) == (0, 1):
                continue
            x, z = c + dx * R, c + dz * R
            for y in ((fy + 4, fy + 5) if fy < 30 else (fy + 3, fy + 4, fy + 5)):
                panes.append((x, y, z))
    connect_fences(s, panes, "minecraft:purple_stained_glass_pane")
    # porta ao sul, lampioes e caminho
    s.set(c, 1, c + R, "minecraft:dark_oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    s.set(c, 2, c + R, "minecraft:dark_oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    s.set(c, 3, c + R, "minecraft:chiseled_stone_bricks")
    for dx in (-2, 2):
        s.set(c + dx, 1, c + R + 1, "minecraft:spruce_fence")
        s.set(c + dx, 2, c + R + 1, "minecraft:lantern[hanging=false]")
    for dx in (-1, 0, 1):   # sem degrau do plinto na frente da porta
        s.set(c + dx, 1, c + R + 1, AIR)
    for z in range(c + R + 2, S):
        s.set(c, 0, z, "minecraft:dirt_path")

    # escada caracol central (coluna de carvalho escuro)
    for y in range(1, 30):
        s.set(c, y, c, "minecraft:stripped_dark_oak_log[axis=y]")
    steps = 30   # degrau k fica em y=1+k; o ultimo (y=30) chega rente ao piso do santuario
    stair_spiral(s, c, c, 1, steps, "minecraft:spruce_stairs")
    for fy in floors:
        spiral_floor(s, c, c, fy, steps, "minecraft:spruce_planks" if fy < 30 else "minecraft:polished_deepslate",
                     "minecraft:dark_oak_fence", 1)

    # ---------- Terreo: entrada, mesa de pesquisa, atril com o diario ----------
    s.set(c - 3, 1, c - 2, f"{NS}:research_table[facing=east]")
    s.set(c - 3, 1, c - 3, "minecraft:bookshelf")
    s.set(c - 3, 2, c - 3, "minecraft:potted_azure_bluet")
    s.set(c + 3, 1, c - 2, "minecraft:lectern[facing=west,has_book=true,powered=false]",
          nbt={"Book": journal_item_nbt(), "Page": Int(0)})
    s.set(c + 3, 1, c + 2, "minecraft:crafting_table")
    barrel(s, c - 3, 1, c + 3, "chests/mage_tower_storage")
    barrel(s, c - 4, 1, c + 2, "chests/mage_tower_storage", facing="east")
    for (x, z) in cells:   # estantes na parede oeste/norte do terreo
        if inside(x, z) and d(x, z) >= 4.5 and (x < c - 1 or z < c - 2) and s.get(x, 1, z) is None:
            s.set(x, 1, z, "minecraft:bookshelf")
            s.set(x, 2, z, "minecraft:bookshelf")
    for (x, z) in cells:
        if inside(x, z) and d(x, z) <= 4.6 and max(abs(x - c), abs(z - c)) > 2 and s.get(x, 1, z) is None:
            s.set(x, 1, z, "minecraft:purple_carpet" if d(x, z) > 3.5 else "minecraft:magenta_carpet")
    for (x, z) in ((c - 3, c - 3), (c + 3, c + 3), (c - 3, c + 3), (c + 3, c - 3)):
        s.set(x, 9, z, "minecraft:lantern[hanging=true]")

    # ---------- 2o andar: biblioteca ----------
    y0 = 11
    pane_set = set(panes)
    for (x, z) in cells:
        if inside(x, z) and d(x, z) >= 4.5:
            for y in (y0, y0 + 1, y0 + 2):
                if (x, y, z) not in pane_set:
                    s.set(x, y, z, "minecraft:bookshelf")
            if rnd.random() < 0.3:
                s.set(x, y0 + 3, z, f"minecraft:purple_candle[candles={rnd.randint(1, 3)},lit=true]")
    s.set(c + 3, y0, c, "minecraft:enchanting_table")
    s.set(c - 3, y0, c + 1, "minecraft:lectern[facing=east,has_book=false,powered=false]")
    s.set(c, y0, c + 4, "minecraft:lectern[facing=north,has_book=false,powered=false]")
    chest(s, c - 2, y0, c + 3, "chests/mage_tower_library", facing="north")
    chiseled_bookshelf(s, c + 2, y0, c + 3, "north", {0: "minecraft:book", 2: "minecraft:writable_book",
                                                       3: "minecraft:book", 5: "minecraft:book"})
    for (x, z) in cells:
        if inside(x, z) and 2.9 < d(x, z) < 4.5 and s.get(x, y0, z) is None and rnd.random() < 0.5:
            s.set(x, y0, z, "minecraft:blue_carpet")
    for (x, z) in ((c - 3, c - 3), (c + 3, c + 3), (c - 3, c + 3), (c + 3, c - 3)):
        s.set(x, 19, z, "minecraft:soul_lantern[hanging=true]")

    # ---------- 3o andar: laboratorio e quarto ----------
    y0 = 21
    brewing_stand(s, c + 3, y0, c - 2, ["night_vision", None, "awkward"])
    s.set(c + 3, y0, c - 1, "minecraft:water_cauldron[level=3]")
    brewing_stand(s, c + 4, y0, c, [None, "regeneration"])
    s.set(c - 3, y0, c + 2, "minecraft:purple_bed[facing=north,part=foot,occupied=false]")
    s.set(c - 3, y0, c + 1, "minecraft:purple_bed[facing=north,part=head,occupied=false]")
    barrel(s, c - 4, y0, c, "chests/mage_tower_storage", facing="east")
    s.set(c + 2, y0, c + 3, "minecraft:amethyst_block")
    s.set(c + 2, y0 + 1, c + 3, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    s.set(c + 3, y0, c + 2, f"{NS}:mana_crystal_block")
    s.set(c - 2, y0, c - 3, "minecraft:bookshelf")
    s.set(c - 2, y0 + 1, c - 3, "minecraft:potted_flowering_azalea_bush")
    s.set(c + 1, y0, c - 4, "minecraft:cartography_table")
    for (x, z) in cells:
        if inside(x, z) and 2.9 < d(x, z) < 4.6 and s.get(x, y0, z) is None and rnd.random() < 0.55:
            s.set(x, y0, z, "minecraft:gray_carpet")
    for (x, z) in ((c - 3, c - 3), (c + 3, c + 3), (c - 3, c + 3), (c + 3, c - 3)):
        s.set(x, 29, z, "minecraft:lantern[hanging=true]")

    # ---------- Santuario (topo): circulo ritual, tesouro e o Arquimago Renegado ----------
    y0 = 31
    for (x, z) in cells:
        if inside(x, z) and 3.0 <= d(x, z) < 4.0 and max(abs(x - c), abs(z - c)) > 2:
            s.set(x, 30, z, "minecraft:crying_obsidian" if (x + z) % 3 == 0 else "minecraft:amethyst_block")
    s.set(c, 30, c, "minecraft:chiseled_deepslate")
    for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        for y in range(y0, top_ceiling):
            s.set(c + dx, y, c + dz, "minecraft:chiseled_polished_blackstone" if y in (y0, top_ceiling - 1)
                  else "minecraft:polished_blackstone_bricks")
    for (dx, dz) in ((-2, -4), (2, -4), (-4, 0), (4, 0), (-2, 4), (2, 4)):
        s.set(c + dx, y0, c + dz, f"minecraft:purple_candle[candles={rnd.randint(2, 4)},lit=true]")
    chest(s, c, y0, c - 4, "chests/mage_tower", facing="south")
    s.set(c - 1, y0, c - 4, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    s.set(c + 1, y0, c - 4, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    for (dx, dz) in ((0, -2), (-2, 0), (2, 0)):
        s.set(c + dx, top_ceiling - 1, c + dz, "minecraft:soul_lantern[hanging=true]")
    s.set(c, top_ceiling - 1, c + 2, "minecraft:lantern[hanging=true]")
    boss(s, c, y0, c - 2, "renegade_archmage")

    # ---------- Telhado conico de ardosia ----------
    for i, y in enumerate(range(top_ceiling, top_ceiling + 9)):
        r = R + 1.5 - i
        for (x, z) in cells:
            dd = d(x, z)
            if dd <= r:
                s.set(x, y, z, "minecraft:deepslate_tiles")
            elif dd <= r + 1 and y > top_ceiling and s.get(x, y, z) is None:
                s.set(x, y, z, "minecraft:deepslate_tile_slab[type=bottom]")
    s.set(c, top_ceiling + 9, c, "minecraft:polished_deepslate_wall[up=true]")
    s.set(c, top_ceiling + 10, c, "minecraft:polished_deepslate_wall[up=true]")
    s.set(c, top_ceiling + 11, c, "minecraft:lightning_rod[facing=up]")

    # trepadeiras subindo pela base
    for (x, z) in cells:
        if R + 0.5 < d(x, z) <= R + 1.5 and rnd.random() < 0.18:
            for (dx, dz) in CARD.values():
                if wall(x + dx, z + dz):
                    face_dir = dir_of(dx, dz)
                    for y in range(2, 2 + rnd.randint(3, 9)):
                        if s.get(x, y, z) is None:
                            s.set(x, y, z, f"minecraft:vine[{face_dir}=true]")
                    break

    # interior limpo (ar explicito apaga terreno/arvores dentro da torre)
    for y in range(1, top_ceiling):
        fill_empty(s, 0, y, 0, S - 1, y, S - 1, AIR, pred=lambda x, yy, z: inside(x, z))
    return s


# =====================================================================================
#  Cabana da Bruxa Ancia (13x19x13: palafitas no pantano, caldeirao no fogo, Bruxa Corrompida)
# =====================================================================================
def witch_hut_ancient():
    S, H = 13, 19
    s = Structure(S, H, S)
    rnd = random.Random(2202)
    P = 7   # altura da plataforma
    # palafitas de mangue + raizes na base
    for (x, z) in ((1, 1), (1, 11), (11, 1), (11, 11), (6, 1), (6, 11), (1, 6), (11, 6)):
        for y in range(0, P):
            s.set(x, y, z, "minecraft:mangrove_log[axis=y]")
        for (dx, dz) in CARD.values():
            for y in range(0, rnd.randint(1, 3)):
                if s.get(x + dx, y, z + dz) is None:
                    s.set(x + dx, y, z + dz, "minecraft:mangrove_roots[waterlogged=false]")
    # plataforma
    for x in range(1, 12):
        for z in range(1, 12):
            edge = x in (1, 11) or z in (1, 11)
            s.set(x, P, z, "minecraft:stripped_mangrove_log[axis=x]" if edge and z in (1, 11)
                  else ("minecraft:stripped_mangrove_log[axis=z]" if edge else "minecraft:dark_oak_planks"))
    # casa: paredes x 2..10, z 2..8, pilares de carvalho escuro
    x0, x1, z0, z1 = 2, 10, 2, 8
    top = P + 5
    for y in range(P + 1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    if corner:
                        st = "minecraft:dark_oak_log[axis=y]"
                    elif y == top:
                        st = "minecraft:dark_oak_log[axis=x]" if z in (z0, z1) else "minecraft:dark_oak_log[axis=z]"
                    else:
                        st = "minecraft:spruce_planks" if rnd.random() > 0.15 else "minecraft:mossy_cobblestone"
                    s.set(x, y, z, st)
    # janelas
    windows = []
    for (x, z) in ((4, z0), (8, z0), (x0, 5), (x1, 5), (4, z1), (8, z1)):
        for y in (P + 2, P + 3):
            windows.append((x, y, z))
    connect_fences(s, windows, "minecraft:green_stained_glass_pane")
    # porta
    s.set(6, P + 1, z1, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
    s.set(6, P + 2, z1, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    # telhado em duas aguas (cumeeira ao longo de x, em z=5)
    for i in range(5):
        y = top + 1 + i
        zn, zs = 1 + i, 9 - i
        for x in range(1, 12):
            if zn < zs:
                s.set(x, y, zn, "minecraft:dark_oak_stairs[facing=south,half=bottom,shape=straight]")
                s.set(x, y, zs, "minecraft:dark_oak_stairs[facing=north,half=bottom,shape=straight]")
            else:
                s.set(x, y, zn, "minecraft:dark_oak_slab[type=bottom]")
        if i < 4:  # oitoes fechados
            for z in range(zn + 1, zs):
                for x in (x0, x1):
                    s.set(x, y, z, "minecraft:spruce_planks")
    # musgo/folhas pendendo do beiral
    for x in range(x0, x1 + 1):
        for z in (1, 9):
            if rnd.random() < 0.3:
                for k in range(rnd.randint(1, 2)):
                    if s.get(x, top - k, z) is None:
                        s.set(x, top - k, z, "minecraft:vine[" + ("south" if z == 1 else "north") + "=true]")
    # chamine de pedra musgosa com fogueira no alto (fumaca!)
    for y in range(P + 1, top + 6):
        s.set(9, y, 3, "minecraft:mossy_cobblestone")
    s.set(9, top + 6, 3, "minecraft:campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")

    # ---------- interior ----------
    fy = P + 1
    s.set(4, fy, 4, "minecraft:campfire[lit=true,signal_fire=false,facing=south,waterlogged=false]")
    s.set(4, fy + 1, 4, f"{NS}:witch_cauldron[lit=true]")
    brewing_stand(s, 8, fy, 6, ["poison", "weakness"])
    chest(s, 3, fy, 3, "chests/witch_hut_ancient", facing="south")
    barrel(s, 3, fy, 7, "chests/witch_hut_pantry", facing="east")
    s.set(8, fy, 3, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]")
    s.set(7, fy, 3, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]")
    s.set(8, fy + 1, 3, "minecraft:potted_red_mushroom")
    s.set(7, fy + 1, 3, "minecraft:green_candle[candles=3,lit=true]")
    # prateleiras com potes
    for (x, z, item) in ((x0 + 1, 5, "minecraft:potted_brown_mushroom"), (x0 + 1, 6, "minecraft:potted_dead_bush"),
                         (x1 - 1, 4, "minecraft:potted_crimson_fungus"), (x1 - 1, 5, "minecraft:potted_fern")):
        s.set(x, fy + 2, z, "minecraft:spruce_slab[type=top]")
        s.set(x, fy + 3, z, item)
    s.set(x1 - 1, fy + 3, 6, "minecraft:skeleton_skull[rotation=12]")
    s.set(x1 - 1, fy + 2, 6, "minecraft:spruce_slab[type=top]")
    for x in range(x0 + 1, x1):   # viga do teto (aberto ate o telhado)
        s.set(x, top, 5, "minecraft:dark_oak_log[axis=x]")
    s.set(6, top - 1, 5, "minecraft:soul_lantern[hanging=true]")
    s.set(4, top - 1, 5, "minecraft:spore_blossom")
    s.set(8, top - 1, 5, "minecraft:soul_lantern[hanging=true]")
    for (x, z) in ((3, 3), (9, 7), (3, 6)):
        s.set(x, top - 1, z, "minecraft:cobweb")
    for (x, z) in ((5, 6), (6, 6), (7, 6), (5, 5), (6, 4), (7, 5)):
        if s.get(x, fy, z) is None:
            s.set(x, fy, z, "minecraft:green_carpet" if (x + z) % 2 else "minecraft:brown_carpet")
    s.entity(7.5, fy, 4.5, "minecraft:cat", {"variant": "minecraft:all_black", "PersistenceRequired": True})
    boss(s, 6, fy, 5, "corrupted_witch")

    # ---------- varanda e escada ----------
    rails = []
    for x in range(1, 12):
        if x != 6:
            rails.append((x, P + 1, 11))
    for z in range(9, 11):
        rails.append((1, P + 1, z)); rails.append((11, P + 1, z))
    connect_fences(s, rails, "minecraft:mangrove_fence")
    s.set(1, P + 2, 11, "minecraft:lantern[hanging=false]")
    s.set(11, P + 2, 11, "minecraft:lantern[hanging=false]")
    s.set(9, P + 1, 10, "minecraft:cauldron")
    s.set(3, P + 1, 10, "minecraft:barrel[facing=up,open=false]")
    for y in range(0, P + 1):
        s.set(6, y, 12, "minecraft:ladder[facing=south,waterlogged=false]")
    s.set(6, P + 1, 11, AIR)
    # limpa interior e varanda
    fill_empty(s, x0 + 1, P + 1, z0 + 1, x1 - 1, top, z1 - 1, AIR)
    fill_empty(s, 1, P + 1, 9, 11, top - 1, 11, AIR)
    for i in range(4):   # sotao
        fill_empty(s, x0 + 1, top + 1 + i, 2 + i, x1 - 1, top + 1 + i, 8 - i, AIR)
    return s


# =====================================================================================
#  Altar Profano (19x12x19: ruinas de pedra-negra, circulo de giz com sangue, Patrono Aprisionado)
# =====================================================================================
def profane_altar():
    S, H = 19, 12
    s = Structure(S, H, S)
    c = 9
    rnd = random.Random(3303)

    def d(x, z):
        return math.hypot(x - c, z - c)

    cells = [(x, z) for x in range(S) for z in range(S)]
    # chao de ruinas (borda irregular)
    for (x, z) in cells:
        dd = d(x, z)
        if dd <= 6.5 or (dd <= 9 and rnd.random() < 1.2 - dd / 9):
            r = rnd.random()
            st = ("minecraft:polished_blackstone_bricks" if r < 0.55 else
                  "minecraft:blackstone" if r < 0.8 else
                  "minecraft:cracked_polished_blackstone_bricks" if r < 0.95 else "minecraft:crying_obsidian")
            s.set(x, 0, z, st)
    # plataforma ritual elevada (x/z 5..13) com degraus em volta
    for x in range(4, 15):
        for z in range(4, 15):
            if 5 <= x <= 13 and 5 <= z <= 13:
                s.set(x, 1, z, "minecraft:polished_blackstone")
            else:
                if x in (4, 14) and z in (4, 14):
                    s.set(x, 1, z, "minecraft:polished_blackstone_brick_slab[type=bottom]")
                elif z == 4:
                    s.set(x, 1, z, "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom]")
                elif z == 14:
                    s.set(x, 1, z, "minecraft:polished_blackstone_brick_stairs[facing=north,half=bottom]")
                elif x == 4:
                    s.set(x, 1, z, "minecraft:polished_blackstone_brick_stairs[facing=east,half=bottom]")
                else:
                    s.set(x, 1, z, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom]")
    # circulo de giz ritual ativado com sangue (anel + estrela de 4 pontas)
    def rune(x, z):
        h = (x * 73856093) ^ (z * 19349663)
        return h % 8
    for (x, z) in cells:
        dd = d(x, z)
        if 2.6 <= dd <= 3.5 or (dd <= 2.6 and (x == c or z == c or abs(x - c) == abs(z - c))):
            s.set(x, 2, z, f"{NS}:ritual_chalk[rune={rune(x, z)},blood=true]")
    s.set(c, 1, c, "minecraft:crying_obsidian")
    # correntes "prendendo" o centro: pilares de grade nas diagonais
    for (dx, dz) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        s.set(c + dx, 2, c + dz, "minecraft:polished_blackstone_brick_wall[up=true]")
        s.set(c + dx, 3, c + dz, "minecraft:polished_blackstone_brick_wall[up=true]")
        s.set(c + dx, 4, c + dz, "minecraft:soul_lantern[hanging=false]")
    # altar de oferendas ao norte e o bau atras dele
    s.set(c, 2, 5, f"{NS}:offering_altar")
    s.set(c - 1, 2, 5, "minecraft:red_candle[candles=3,lit=true]")
    s.set(c + 1, 2, 5, "minecraft:black_candle[candles=4,lit=true]")
    chest(s, c, 1, 3, "chests/profane_altar", facing="south")
    s.set(c - 1, 1, 3, "minecraft:skeleton_skull[rotation=8]")
    s.set(c + 1, 1, 3, "minecraft:bone_block[axis=y]")
    # velas em volta do circulo
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x, z = c + round(4.4 * math.cos(a)), c + round(4.4 * math.sin(a))
        if s.get(x, 2, z) is None:
            col = "black" if k % 2 else "red"
            s.set(x, 2, z, f"minecraft:{col}_candle[candles={rnd.randint(1, 4)},lit=true]")
    # colunas quebradas no anel externo
    for k in range(7):
        a = k * 2 * math.pi / 7 + 0.3
        x, z = c + round(7.3 * math.cos(a)), c + round(7.3 * math.sin(a))
        h = rnd.choice((2, 3, 4, 5, 6))
        for y in range(1, h + 1):
            s.set(x, y, z, "minecraft:polished_blackstone_bricks" if y < h else "minecraft:chiseled_polished_blackstone")
        if h >= 4 and rnd.random() < 0.6:
            s.set(x, h + 1, z, "minecraft:soul_campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")
        elif h <= 3:
            s.set(x, h + 1, z, f"minecraft:skeleton_skull[rotation={rnd.randint(0, 15)}]")
        # pedras caidas
        fx, fz = x + rnd.choice((-1, 1)), z + rnd.choice((-1, 1))
        if s.get(fx, 1, fz) is None and 0 <= fx < S and 0 <= fz < S:
            s.set(fx, 1, fz, "minecraft:polished_blackstone_brick_slab[type=bottom]")
    # braseiros de fogo das almas
    for (x, z) in ((2, 9), (16, 9)):
        s.set(x, 0, z, "minecraft:soul_soil")
        s.set(x, 1, z, "minecraft:soul_fire")
        for (dx, dz) in CARD.values():
            s.set(x + dx, 1, z + dz, "minecraft:polished_blackstone_brick_wall[up=true]")
    # ossos espalhados
    for _ in range(9):
        x, z = rnd.randint(1, S - 2), rnd.randint(1, S - 2)
        if s.get(x, 0, z) is not None and s.get(x, 1, z) is None and d(x, z) > 5.5:
            s.set(x, 1, z, rnd.choice(("minecraft:bone_block[axis=x]", "minecraft:bone_block[axis=z]",
                                       f"minecraft:skeleton_skull[rotation={rnd.randint(0, 15)}]",
                                       "minecraft:cobweb")))
    boss(s, c, 2, c, "imprisoned_patron")
    # limpa a vegetacao/terreno acima das ruinas
    for (x, z) in cells:
        if d(x, z) <= 8.5:
            fill_empty(s, x, 1, z, x, H - 1, z, AIR)
    return s


# =====================================================================================
#  Circulo de Pedras Druidico (23x12x23: menires, altar com azaleia sagrada, metade corrompida, Ent Corrompido)
# =====================================================================================
def druid_circle():
    S, H = 23, 12
    s = Structure(S, H, S)
    c = 11
    rnd = random.Random(4404)

    def d(x, z):
        return math.hypot(x - c, z - c)

    def corrupted(x, z):   # o lado leste esta sendo tomado pela corrupcao do Ent
        return x - c > 1.5 + 1.5 * math.sin(z * 0.9)

    cells = [(x, z) for x in range(S) for z in range(S)]
    for (x, z) in cells:
        dd = d(x, z)
        if dd <= 10.5:
            if corrupted(x, z):
                r = rnd.random()
                st = ("minecraft:sculk" if r < 0.25 else "minecraft:podzol" if r < 0.55 else
                      "minecraft:coarse_dirt" if r < 0.8 else "minecraft:rooted_dirt")
            else:
                r = rnd.random()
                st = "minecraft:moss_block" if (dd < 3.5 or r < 0.2) else "minecraft:grass_block[snowy=false]"
            if 5.6 <= dd <= 6.4 and rnd.random() < 0.7:
                st = "minecraft:mossy_cobblestone" if not corrupted(x, z) else "minecraft:cobbled_deepslate"
            s.set(x, 0, z, st)
    # menires (2x2) no anel de raio 8
    for k in range(8):
        a = k * math.pi / 4
        mx, mz = c + round(8 * math.cos(a)), c + round(8 * math.sin(a))
        h = 4 + (k % 3)
        bad = corrupted(mx, mz)
        for y in range(1, h + 1):
            for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                if y == h and (dx, dz) != (0, 0) and rnd.random() < 0.5:
                    continue
                x, z = mx + dx - (1 if mx > c else 0), mz + dz - (1 if mz > c else 0)
                if bad:
                    st = rnd.choice(("minecraft:cobbled_deepslate", "minecraft:blackstone", "minecraft:sculk"))
                else:
                    st = rnd.choice(("minecraft:mossy_stone_bricks", "minecraft:mossy_cobblestone", "minecraft:stone",
                                     "minecraft:mossy_stone_bricks"))
                s.set(x, y, z, st)
        # liquens brilhantes nas faces externas das pedras saudaveis
        if not bad and mx != c:
            ox, attach = (mx + 1, "west") if mx > c else (mx - 1, "east")
            for y in range(1, h):
                if rnd.random() < 0.45 and 0 <= ox < S and s.get(ox, y, mz) is None:
                    s.set(ox, y, mz, f"minecraft:glow_lichen[{attach}=true,waterlogged=false]")
    # altar central: plataforma de pedra musgosa com a azaleia florida sagrada
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            s.set(x, 1, z, "minecraft:mossy_stone_bricks" if (x, z) != (c, c) else "minecraft:moss_block")
    s.set(c, 2, c, "minecraft:flowering_azalea")
    for (x, z) in ((c - 1, c - 1), (c + 1, c + 1), (c - 1, c + 1), (c + 1, c - 1)):
        s.set(x, 2, z, "minecraft:moss_carpet")
    chest(s, c, 1, c + 2, "chests/druid_circle", facing="south")
    # flores e Flores-Vaga-lume do lado saudavel; arbustos mortos no lado corrompido
    flowers = ["minecraft:allium", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
               "minecraft:azure_bluet", "minecraft:poppy", "minecraft:short_grass", "minecraft:fern"]
    for (x, z) in cells:
        if s.get(x, 1, z) is not None or d(x, z) > 10:
            continue
        ground = s.get(x, 0, z)
        if ground is None:
            continue
        if corrupted(x, z):
            if "sculk" not in ground and rnd.random() < 0.12:
                s.set(x, 1, z, "minecraft:dead_bush")
        elif ground.startswith("minecraft:grass_block") or ground == "minecraft:moss_block":
            r = rnd.random()
            if 2.2 < d(x, z) < 5.2 and r < 0.16:
                s.set(x, 1, z, f"{NS}:fairy_glowbloom")
            elif r < 0.3:
                s.set(x, 1, z, rnd.choice(flowers))
    # tronco caido musgoso no lado saudavel
    for x in range(3, 7):
        s.set(x, 1, c + 5, "minecraft:oak_log[axis=x]")
        s.set(x, 2, c + 5, "minecraft:moss_carpet")
    boss(s, c + 4, 1, c, "corrupted_ent")
    for (x, z) in cells:
        if d(x, z) <= 10.5:
            fill_empty(s, x, 1, z, x, H - 1, z, AIR)
    return s


# =====================================================================================
#  Cripta Esquecida (jigsaw): mausoleu na superficie + caracol -> salao -> salas laterais / camara do Lich
# =====================================================================================
CRYPT_F = 16   # profundidade da escadaria (piso do mausoleu em y=F na peca de entrada)


def jig(s, x, y, z, facing, name, target="minecraft:empty", pool="minecraft:empty", priority=0):
    """Bloco jigsaw horizontal (vira ar quando a estrutura e gerada)."""
    s.set(x, y, z, f"minecraft:jigsaw[orientation={facing}_up]", nbt={
        "name": f"{name}", "target": f"{target}", "pool": f"{pool}", "final_state": AIR, "joint": "aligned",
        "placement_priority": Int(0), "selection_priority": Int(priority)})


def wall_torch(s, x, y, z, facing):
    s.set(x, y, z, f"minecraft:soul_wall_torch[facing={facing}]")


def sarcophagus(s, foot, facing, sealed=True, open_=False):
    """Coloca as duas metades do Sarcofago Antigo (cabeca = pe + facing)."""
    fx, fy, fz = foot
    dx, dz = CARD[facing]
    props = f"facing={facing},open={'true' if open_ else 'false'},sealed={'true' if sealed else 'false'}"
    s.set(fx, fy, fz, f"{NS}:crypt_sarcophagus[{props},part=foot]")
    s.set(fx + dx, fy, fz + dz, f"{NS}:crypt_sarcophagus[{props},part=head]")


def crypt_box(s, x0, y0, z0, x1, y1, z1, wall="minecraft:deepslate_bricks", floor="minecraft:polished_deepslate",
              ceiling="minecraft:deepslate_tiles"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, y0, z, floor)
            s.set(x, y1, z, ceiling)
            for y in range(y0 + 1, y1):
                if x in (x0, x1) or z in (z0, z1):
                    s.set(x, y, z, wall)


def crypt_entrance():
    F = CRYPT_F
    S, H = 11, F + 9
    s = Structure(S, H, S)
    c = 5
    rnd = random.Random(5501)
    # sala de chegada no fundo (piso y=0, teto y=5)
    crypt_box(s, 0, 0, 0, 10, 5, 10)
    for x in range(c - 1, c + 2):        # abertura do poco no teto
        for z in range(c - 1, c + 2):
            s.set(x, 5, z, AIR)
    for x in range(4, 7):                 # porta ao norte
        for y in range(1, 4):
            s.set(x, y, 0, AIR)
    jig(s, c, 1, 0, "north", f"{NS}:crypt_entrance", f"{NS}:crypt_hall_in", f"{NS}:forgotten_crypt/hall")
    # poco da escadaria: anel 5x5 de paredes do teto da sala ate o mausoleu
    for y in range(5, F):
        for x in range(c - 2, c + 3):
            for z in range(c - 2, c + 3):
                if max(abs(x - c), abs(z - c)) == 2:
                    s.set(x, y, z, "minecraft:deepslate_bricks" if rnd.random() > 0.1 else "minecraft:cracked_deepslate_bricks")
    # fundacao sob o mausoleu
    for y in range(F - 3, F):
        for x in range(S):
            for z in range(S):
                if max(abs(x - c), abs(z - c)) >= 2:
                    s.set(x, y, z, "minecraft:cobbled_deepslate")
    # piso do mausoleu (antes da caracol, para o degrau de chegada ficar por cima)
    for x in range(S):
        for z in range(S):
            s.set(x, F, z, "minecraft:stone_bricks" if max(abs(x - c), abs(z - c)) > 3 else "minecraft:polished_andesite")
    # coluna central + caracol (o ultimo degrau chega rente ao piso do mausoleu)
    for y in range(1, F):
        s.set(c, y, c, "minecraft:polished_deepslate" if y % 4 else "minecraft:chiseled_deepslate")
    steps = stair_spiral(s, c, c, 1, F, "minecraft:deepslate_tile_stairs")
    ring = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
    for (k, x, y, z) in steps:   # tochas das almas nas paredes do poco
        if k % 4 == 2 and 5 <= y + 2 < F:
            dx, dz = ring[k % 8]
            out = (dx, 0) if dx != 0 else (0, dz)
            wall_torch(s, x, y + 2, z, dir_of(-out[0], -out[1]))
    # decoracao da sala de baixo
    for (x, z) in ((2, 2), (8, 2), (2, 8), (8, 8)):
        s.set(x, 4, z, "minecraft:soul_lantern[hanging=true]")
    pot(s, 1, 1, 9, "chests/forgotten_crypt_urn")
    pot(s, 9, 1, 9, "chests/forgotten_crypt_urn")
    s.set(1, 1, 1, "minecraft:skeleton_skull[rotation=6]")
    s.set(9, 1, 1, "minecraft:bone_block[axis=y]")
    s.set(9, 2, 1, "minecraft:skeleton_skull[rotation=10]")
    for (x, z) in ((1, 4), (9, 6), (3, 9)):
        s.set(x, 4, z, "minecraft:cobweb")
    s.set(7, 1, 9, "minecraft:candle[candles=2,lit=false]")
    fill_empty(s, 1, 1, 1, 9, 4, 9, AIR)
    fill_empty(s, c - 1, 5, c - 1, c + 1, F - 1, c + 1, AIR)

    # ---------- Mausoleu ----------
    spiral_floor(s, c, c, F, F, "minecraft:polished_andesite", "minecraft:iron_bars", 1)
    for y in range(F + 1, F + 6):
        for x in range(1, 10):
            for z in range(1, 10):
                if x in (1, 9) or z in (1, 9):
                    corner = x in (1, 9) and z in (1, 9)
                    st = ("minecraft:chiseled_stone_bricks" if corner and y in (F + 1, F + 5) else
                          "minecraft:polished_andesite" if corner else
                          "minecraft:mossy_stone_bricks" if rnd.random() < 0.3 else "minecraft:stone_bricks")
                    s.set(x, y, z, st)
    for x in range(4, 7):     # arco de entrada ao sul
        for y in range(F + 1, F + 4):
            s.set(x, y, 9, AIR)
    s.set(4, F + 3, 9, "minecraft:stone_brick_stairs[facing=east,half=top,shape=straight]")
    s.set(6, F + 3, 9, "minecraft:stone_brick_stairs[facing=west,half=top,shape=straight]")
    s.set(c, F + 4, 9, "minecraft:chiseled_stone_bricks")
    bars = []
    for (x, z) in ((1, 4), (1, 6), (9, 4), (9, 6)):
        for y in (F + 2, F + 3):
            bars.append((x, y, z))
    connect_fences(s, bars, "minecraft:iron_bars")
    # telhado em degraus
    for x in range(S):
        for z in range(S):
            s.set(x, F + 6, z, "minecraft:deepslate_tiles" if 1 <= x <= 9 and 1 <= z <= 9
                  else "minecraft:deepslate_tile_slab[type=bottom]")
    for x in range(2, 9):
        for z in range(2, 9):
            s.set(x, F + 7, z, "minecraft:deepslate_tiles" if 3 <= x <= 7 and 3 <= z <= 7
                  else "minecraft:deepslate_tile_slab[type=bottom]")
    s.set(c, F + 8, c, "minecraft:skeleton_skull[rotation=8]")
    for (x, z) in ((1, 1), (9, 1), (1, 9), (9, 9)):
        s.set(x, F + 7, z, "minecraft:soul_lantern[hanging=false]")
    # obelisco no centro (continua a coluna) e detalhes
    s.set(c, F + 1, c, "minecraft:chiseled_stone_bricks")
    s.set(c, F + 2, c, "minecraft:polished_andesite")
    s.set(c, F + 3, c, "minecraft:soul_lantern[hanging=false]")
    for (x, z) in ((2, 2), (8, 2), (2, 8), (8, 8)):
        s.set(x, F + 1, z, f"minecraft:candle[candles={rnd.randint(1, 4)},lit=true]")
    s.set(2, F + 5, 2, "minecraft:cobweb")
    s.set(8, F + 5, 8, "minecraft:cobweb")
    s.set(2, F + 1, 5, "minecraft:potted_dead_bush")
    fill_empty(s, 2, F + 1, 2, 8, F + 5, 8, AIR)
    for x in range(S):   # limpa o caminho em frente ao arco
        for y in range(F + 1, F + 5):
            fill_empty(s, x, y, 10, x, y, 10, AIR, pred=lambda xx, yy, zz: 3 <= xx <= 7)
    return s


def crypt_hall():
    s = Structure(11, 8, 25)
    rnd = random.Random(5502)
    crypt_box(s, 0, 0, 0, 10, 7, 24)
    for z in range(1, 24):
        for x in range(4, 7):
            s.set(x, 0, z, "minecraft:deepslate_tiles")
        for x in (1, 9):
            if rnd.random() < 0.25:
                s.set(x, 0, z, "minecraft:soul_soil")
        s.set(1, 6, z, "minecraft:deepslate_tile_stairs[facing=west,half=top,shape=straight]")
        s.set(9, 6, z, "minecraft:deepslate_tile_stairs[facing=east,half=top,shape=straight]")
    # portas
    for x in range(4, 7):
        for y in range(1, 4):
            s.set(x, y, 0, AIR)
            s.set(x, y, 24, AIR)
    for z0 in (7, 17):
        for z in range(z0 - 1, z0 + 2):
            for y in range(1, 4):
                s.set(0, y, z, AIR)
                s.set(10, y, z, AIR)
        jig(s, 0, 1, z0, "west", f"{NS}:crypt_hall_side", f"{NS}:crypt_room_in", f"{NS}:forgotten_crypt/side_rooms")
        jig(s, 10, 1, z0, "east", f"{NS}:crypt_hall_side", f"{NS}:crypt_room_in", f"{NS}:forgotten_crypt/side_rooms")
    jig(s, 5, 1, 24, "south", f"{NS}:crypt_hall_in")
    jig(s, 5, 1, 0, "north", f"{NS}:crypt_hall_out", f"{NS}:crypt_lich_in", f"{NS}:forgotten_crypt/lich_chamber",
        priority=10)
    # pilares
    for z in (3, 12, 21):
        for x in (2, 8):
            for y in range(1, 6):
                s.set(x, y, z, "minecraft:chiseled_deepslate" if y in (1, 5) else "minecraft:polished_deepslate")
    # sarcofagos entre os pilares do meio (cabeca encostada na parede)
    for z in (11, 13):
        sarcophagus(s, (2, 1, z), "west")
        sarcophagus(s, (8, 1, z), "east")
    for (x, z) in ((3, 10), (7, 14), (3, 14), (7, 10)):
        s.set(x, 1, z, f"minecraft:candle[candles={rnd.randint(1, 3)},lit=true]")
    # lanternas das almas penduradas no corredor
    for z in (4, 12, 20):
        s.set(5, 6, z, "minecraft:chain[axis=y]")
        s.set(5, 5, z, "minecraft:soul_lantern[hanging=true]")
    # caveiras nas paredes, urnas e teias
    for z in (2, 10, 14, 22):
        s.set(1, 3, z, "minecraft:skeleton_wall_skull[facing=east]")
        s.set(9, 3, z, "minecraft:skeleton_wall_skull[facing=west]")
    pot(s, 1, 1, 2, "chests/forgotten_crypt_urn")
    pot(s, 9, 1, 22, "chests/forgotten_crypt_urn")
    s.set(1, 1, 21, "minecraft:bone_block[axis=z]")
    s.set(9, 1, 3, "minecraft:bone_block[axis=x]")
    for (x, z) in ((1, 1), (9, 23), (1, 15), (9, 9)):
        s.set(x, 5, z, "minecraft:cobweb")
    fill_empty(s, 1, 1, 1, 9, 6, 23, AIR)
    return s


def crypt_room(kind):
    s = Structure(9, 7, 9)
    rnd = random.Random(5510 + len(kind))
    crypt_box(s, 0, 0, 0, 8, 6, 8)
    for x in range(3, 6):
        for y in range(1, 4):
            s.set(x, y, 8, AIR)
    jig(s, 4, 1, 8, "south", f"{NS}:crypt_room_in")
    if kind == "sarcophagus_room":
        for x in range(1, 8):
            s.set(x, 0, 4, "minecraft:chiseled_deepslate")
        for x in (2, 4, 6):
            sarcophagus(s, (x, 1, 3), "north")
        for x in (1, 3, 5, 7):
            s.set(x, 1, 5, f"minecraft:candle[candles={rnd.randint(1, 4)},lit=true]")
        for (x, z) in ((2, 5), (6, 5)):
            s.set(x, 5, z, "minecraft:soul_lantern[hanging=true]")
        pot(s, 1, 1, 7, "chests/forgotten_crypt_urn")
        pot(s, 7, 1, 7, "chests/forgotten_crypt_urn")
        for x in (1, 3, 5, 7):
            s.set(x, 3, 1, "minecraft:skeleton_wall_skull[facing=south]")
        s.set(1, 5, 1, "minecraft:cobweb"); s.set(7, 5, 2, "minecraft:cobweb")
    elif kind == "ossuary_room":
        for z in range(1, 8):
            for x in (1, 7):
                h = rnd.randint(1, 3)
                for y in range(1, h + 1):
                    s.set(x, y, z, "minecraft:bone_block[axis=y]")
                s.set(x, h + 1, z, f"minecraft:skeleton_skull[rotation={rnd.randint(0, 15)}]")
        s.spawner(4, 1, 3, "minecraft:skeleton")
        chest(s, 4, 1, 1, "chests/forgotten_crypt_side", facing="south")
        s.set(2, 5, 4, "minecraft:soul_lantern[hanging=true]")
        s.set(6, 5, 4, "minecraft:soul_lantern[hanging=true]")
        for (x, z) in ((2, 1), (6, 1), (2, 7), (6, 6), (3, 3)):
            s.set(x, 5 if z != 3 else 1, z, "minecraft:cobweb")
        s.set(5, 1, 6, "minecraft:candle[candles=3,lit=false]")
    else:   # collapsed_room: desabamento com escavacao arqueologica
        rubble = ("minecraft:cobbled_deepslate", "minecraft:tuff", "minecraft:gravel", "minecraft:cobbled_deepslate")
        for x in range(1, 8):
            for z in range(1, 8):
                h = max(0, int(4.5 - x * 0.8 - (z - 1) * 0.25 + rnd.random()))
                for y in range(1, min(5, h) + 1):
                    s.set(x, y, z, rnd.choice(rubble))
        for (x, y, z) in ((1, 1, 6), (2, 1, 3), (3, 1, 5)):
            s.set(x, y, z, "minecraft:suspicious_gravel[dusted=0]",
                  nbt={"LootTable": f"{NS}:archaeology/forgotten_crypt"})
        sarcophagus(s, (6, 1, 3), "north", sealed=False, open_=True)
        for (x, z) in ((5, 5), (6, 6), (7, 1)):
            s.set(x, 5, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]")
        for (x, z) in ((4, 2), (6, 5), (3, 6)):
            if s.get(x, 5, z) is None:
                s.set(x, 5, z, "minecraft:hanging_roots[waterlogged=false]")
        pot(s, 7, 1, 7, "chests/forgotten_crypt_urn")
        s.set(5, 1, 7, "minecraft:skeleton_skull[rotation=3]")
        s.set(4, 5, 4, "minecraft:soul_lantern[hanging=true]")
    fill_empty(s, 1, 1, 1, 7, 5, 7, AIR)
    return s


def crypt_dead_end():
    s = Structure(5, 5, 2)
    for x in range(5):
        for y in range(5):
            s.set(x, y, 0, "minecraft:deepslate_bricks")
            if x in (0, 4) or y in (0, 4):
                s.set(x, y, 1, "minecraft:deepslate_bricks" if y > 0 else "minecraft:polished_deepslate")
    s.set(2, 2, 0, "minecraft:chiseled_deepslate")
    s.set(1, 1, 1, "minecraft:candle[candles=2,lit=false]")
    s.set(2, 2, 1, "minecraft:skeleton_wall_skull[facing=south]")
    s.set(3, 3, 1, "minecraft:cobweb")
    jig(s, 2, 1, 1, "south", f"{NS}:crypt_room_in")
    fill_empty(s, 1, 1, 1, 3, 3, 1, AIR)
    return s


def crypt_lich_chamber():
    S, H = 19, 12
    s = Structure(S, H, S)
    rnd = random.Random(5520)
    c = 9
    crypt_box(s, 0, 0, 0, 18, 11, 18)
    for x in range(1, 18):
        for z in range(1, 18):
            s.set(x, 0, z, "minecraft:polished_blackstone" if (x + z) % 2 else "minecraft:polished_deepslate")
    for x in range(8, 11):
        for y in range(1, 4):
            s.set(x, y, 18, AIR)
    jig(s, c, 1, 18, "south", f"{NS}:crypt_lich_in")
    # abobada
    for i in range(1, 18):
        s.set(i, 10, 1, "minecraft:deepslate_tile_stairs[facing=north,half=top,shape=straight]")
        s.set(i, 10, 17, "minecraft:deepslate_tile_stairs[facing=south,half=top,shape=straight]")
        s.set(1, 10, i, "minecraft:deepslate_tile_stairs[facing=west,half=top,shape=straight]")
        s.set(17, 10, i, "minecraft:deepslate_tile_stairs[facing=east,half=top,shape=straight]")
    # pilares
    for (x, z) in ((4, 5), (14, 5), (4, 13), (14, 13)):
        for y in range(1, 11):
            s.set(x, y, z, "minecraft:chiseled_deepslate" if y in (1, 10) else
                  ("minecraft:polished_deepslate" if y % 3 else "minecraft:deepslate_tiles"))
        for (dx, dz) in CARD.values():
            s.set(x + dx, 9, z + dz, f"minecraft:deepslate_tile_stairs[facing={OPP[dir_of(dx, dz)]},half=top,shape=straight]")
    # estrado ao norte com o Altar Ossuario
    for x in range(5, 14):
        for z in range(1, 6):
            s.set(x, 1, z, "minecraft:polished_blackstone_bricks")
        s.set(x, 1, 6, "minecraft:polished_blackstone_brick_stairs[facing=north,half=bottom,shape=straight]")
    for x in range(6, 13):
        for z in range(1, 4):
            s.set(x, 2, z, "minecraft:polished_blackstone_bricks")
        s.set(x, 2, 4, "minecraft:polished_blackstone_brick_stairs[facing=north,half=bottom,shape=straight]")
    s.set(c, 3, 2, f"{NS}:ossuary_altar[lit=false]")
    chest(s, c, 3, 1, "chests/forgotten_crypt", facing="south")
    for x in (6, 12):
        s.set(x, 3, 2, "minecraft:soul_campfire[lit=true,signal_fire=false,facing=south,waterlogged=false]")
    for x in (7, 11):
        s.set(x, 3, 3, f"minecraft:black_candle[candles={rnd.randint(2, 4)},lit=true]")
    for x in (7, 8, 10, 11):
        s.set(x, 3, 1, "minecraft:bone_block[axis=y]")
        s.set(x, 4, 1, f"minecraft:skeleton_skull[rotation={rnd.choice((7, 8, 9))}]")
    # braseiros de fogo das almas nos cantos
    for (x, z) in ((2, 15), (16, 15), (2, 8), (16, 8)):
        s.set(x, 0, z, "minecraft:soul_soil")
        s.set(x, 1, z, "minecraft:soul_fire")
    # correntes com lanternas
    for (x, z) in ((6, 9), (12, 9), (6, 15), (12, 15)):
        for y in (9, 8):
            s.set(x, y, z, "minecraft:chain[axis=y]")
        s.set(x, 10, z, "minecraft:chain[axis=y]")
        s.set(x, 7, z, "minecraft:soul_lantern[hanging=true]")
    # sarcofagos lacrados nas laterais
    for z in (9, 12):
        sarcophagus(s, (2, 1, z), "west")
        sarcophagus(s, (16, 1, z), "east")
    # ossos, sculk e teias
    for _ in range(14):
        x, z = rnd.randint(1, 17), rnd.randint(7, 17)
        if s.get(x, 1, z) is None and abs(x - c) > 1:
            s.set(x, 1, z, rnd.choice(("minecraft:bone_block[axis=x]", "minecraft:bone_block[axis=z]",
                                       f"minecraft:skeleton_skull[rotation={rnd.randint(0, 15)}]")))
    for _ in range(10):
        x, z = rnd.randint(1, 17), rnd.randint(7, 17)
        if s.get(x, 1, z) is None:
            s.set(x, 0, z, "minecraft:sculk")
    for (x, z) in ((1, 1), (17, 1), (1, 17), (17, 17), (2, 1), (16, 17)):
        s.set(x, 9, z, "minecraft:cobweb")
    for (x, z) in ((1, 7), (17, 7), (1, 14), (17, 14)):
        s.set(x, 4, z, "minecraft:skeleton_wall_skull[facing=" + ("east" if x == 1 else "west") + "]")
    boss(s, c, 1, c + 1, "lich")
    s.entity(5.5, 2, 12.5, f"{NS}:wandering_spirit", {"PersistenceRequired": True})
    s.entity(13.5, 2, 12.5, f"{NS}:wandering_spirit", {"PersistenceRequired": True})
    fill_empty(s, 1, 1, 1, 17, 10, 17, AIR)
    return s


# =====================================================================================
#  Ruinas Arcanas (3 variantes pequenas e comuns)
# =====================================================================================
def column(s, x, z, h, y0=1):
    for y in range(y0, y0 + h):
        s.set(x, y, z, "minecraft:chiseled_stone_bricks" if y == y0 else "minecraft:stone_bricks")
    if h >= 4:
        s.set(x, y0 + h, z, "minecraft:stone_brick_slab[type=bottom]")


def ruins_circle():
    S, H = 13, 8
    s = Structure(S, H, S)
    c = 6
    rnd = random.Random(6601)
    for x in range(S):
        for z in range(S):
            d = math.hypot(x - c, z - c)
            if d <= 5.5 and rnd.random() < 0.92:
                if 2.6 <= d <= 3.4:
                    st = "minecraft:purple_glazed_terracotta[facing=" + \
                         ("north", "east", "south", "west")[int((math.atan2(z - c, x - c) + math.pi) / (math.pi / 2)) % 4] + "]"
                elif d < 1:
                    st = "minecraft:chiseled_stone_bricks"
                else:
                    st = "minecraft:stone_bricks"
                s.set(x, 0, z, st)
    # 6 colunas quebradas
    for k in range(6):
        a = k * math.pi / 3 + 0.2
        x, z = c + round(4.6 * math.cos(a)), c + round(4.6 * math.sin(a))
        column(s, x, z, rnd.choice((1, 2, 3, 4, 5)))
    # coluna tombada fora do circulo
    for x in range(2, 6):
        s.set(x, 1, 11, "minecraft:stone_bricks" if x % 2 else "minecraft:mossy_stone_bricks")
    # pedestal central com o bau e cristais
    s.set(c, 0, c, "minecraft:chiseled_stone_bricks")
    chest(s, c, 1, c, "chests/arcane_ruins", facing=rnd.choice(("north", "south", "east", "west")))
    for (dx, dz) in ((1, 1), (-1, -1), (1, -1)):
        s.set(c + dx, 1, c + dz, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    s.set(c - 1, 1, c + 1, "minecraft:small_amethyst_bud[facing=up,waterlogged=false]")
    s.set(c + 2, 1, c + 1, f"{NS}:mana_crystal_block")
    s.set(c - 4, 0, c + 2, "minecraft:suspicious_gravel[dusted=0]", nbt={"LootTable": f"{NS}:archaeology/arcane_ruins"})
    for x in range(S):
        for z in range(S):
            if math.hypot(x - c, z - c) <= 6:
                fill_empty(s, x, 1, z, x, 5, z, AIR)
    return s


def ruins_arch():
    S, H = 13, 9
    s = Structure(S, H, S)
    rnd = random.Random(6602)
    for x in range(1, 12):
        for z in range(2, 11):
            if rnd.random() < 0.8:
                s.set(x, 0, z, rnd.choice(("minecraft:stone_bricks", "minecraft:cracked_stone_bricks",
                                           "minecraft:mossy_cobblestone", "minecraft:stone_bricks")))
    # arco: dois pilares (x=3 e x=9) e a verga parcialmente caida
    for x in (3, 9):
        for y in range(1, 6):
            s.set(x, y, 6, "minecraft:stone_bricks" if y not in (1, 5) else "minecraft:chiseled_stone_bricks")
    for x in range(4, 9):
        if x < 7:
            s.set(x, 6, 6, "minecraft:stone_bricks")
    s.set(4, 5, 6, "minecraft:stone_brick_stairs[facing=west,half=top,shape=straight]")
    s.set(3, 6, 6, "minecraft:stone_bricks")
    s.set(5, 7, 6, "minecraft:purple_glazed_terracotta[facing=north]")
    # muro em L arruinado
    for z in range(2, 11):
        h = max(0, 3 - abs(z - 5) // 2 - rnd.randint(0, 1))
        for y in range(1, h + 1):
            s.set(1, y, z, "minecraft:stone_bricks")
    for x in range(1, 7):
        h = max(0, 2 - rnd.randint(0, 2))
        for y in range(1, h + 1):
            s.set(x, y, 10, "minecraft:mossy_stone_bricks")
    # entulho e estantes quebradas
    for (x, z) in ((7, 4), (8, 8), (10, 3), (6, 9), (11, 7)):
        s.set(x, 1, z, rnd.choice(("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:gravel")))
    s.set(2, 1, 4, "minecraft:bookshelf")
    s.set(2, 1, 8, "minecraft:bookshelf")
    s.set(2, 2, 8, "minecraft:cobweb")
    # bau meio enterrado sob o arco
    chest(s, 6, 0, 6, "chests/arcane_ruins", facing="south")
    s.set(5, 1, 5, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    s.set(10, 1, 9, "minecraft:candle[candles=3,lit=false]")
    s.set(8, 0, 3, "minecraft:suspicious_gravel[dusted=0]", nbt={"LootTable": f"{NS}:archaeology/arcane_ruins"})
    fill_empty(s, 1, 1, 2, 11, 7, 10, AIR)
    return s


def ruins_study():
    S, H = 11, 7
    s = Structure(S, H, S)
    rnd = random.Random(6603)
    for x in range(1, 10):
        for z in range(1, 10):
            s.set(x, 0, z, rnd.choice(("minecraft:stone_bricks", "minecraft:cracked_stone_bricks",
                                       "minecraft:polished_andesite")))
    for x in range(1, 10):
        for z in range(1, 10):
            if x in (1, 9) or z in (1, 9):
                h = max(1, 4 - rnd.randint(0, 3)) if (x + z) % 3 else rnd.randint(0, 2)
                if (x, z) in ((5, 9), (4, 9), (6, 9)):
                    h = 0   # entrada desmoronada
                for y in range(1, h + 1):
                    s.set(x, y, z, "minecraft:stone_bricks" if rnd.random() > 0.25 else "minecraft:mossy_stone_bricks")
    for z in range(2, 8):
        if rnd.random() < 0.7:
            s.set(2, 1, z, "minecraft:bookshelf")
            if rnd.random() < 0.5:
                s.set(2, 2, z, "minecraft:bookshelf")
    s.set(6, 1, 3, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    chest(s, 8, 1, 2, "chests/arcane_ruins", facing="west")
    s.set(8, 1, 7, f"{NS}:mana_crystal_block")
    s.set(8, 1, 6, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    s.set(7, 1, 7, "minecraft:medium_amethyst_bud[facing=up,waterlogged=false]")
    s.set(4, 1, 4, "minecraft:candle[candles=2,lit=false]")
    for (x, z) in ((3, 6), (5, 5), (4, 7), (6, 6)):
        s.set(x, 1, z, "minecraft:moss_carpet")
    s.set(3, 3, 2, "minecraft:cobweb")
    s.set(7, 1, 5, "minecraft:suspicious_gravel[dusted=0]", nbt={"LootTable": f"{NS}:archaeology/arcane_ruins"})
    fill_empty(s, 2, 1, 2, 8, 5, 8, AIR)
    return s


# =====================================================================================
#  Acampamento do Mago Errante (11x7x11: tenda, fogueira, bancos, Mago Errante)
# =====================================================================================
def mage_camp():
    S, H = 12, 7
    s = Structure(S, H, S)
    # tenda em A ao longo de x (x 1..6), cumeeira em z=4
    for x in range(1, 7):
        stripe = "minecraft:blue_wool" if x in (2, 5) else "minecraft:purple_wool"
        for (y, zs) in ((1, (1, 7)), (2, (2, 6)), (3, (3, 5)), (4, (4,))):
            for z in zs:
                s.set(x, y, z, "minecraft:yellow_wool" if y == 4 else stripe)
    for (y, z0, z1) in ((1, 2, 6), (2, 3, 5), (3, 4, 4)):   # fundo fechado
        for z in range(z0, z1 + 1):
            s.set(1, y, z, "minecraft:purple_wool")
    # dentro: saco de dormir (cama), bau, tapete, lanterna
    s.set(3, 1, 3, "minecraft:blue_bed[facing=west,part=foot,occupied=false]")
    s.set(2, 1, 3, "minecraft:blue_bed[facing=west,part=head,occupied=false]")
    chest(s, 2, 1, 5, "chests/wandering_mage_camp", facing="east")
    for x in range(3, 7):
        for z in range(4, 6):
            if s.get(x, 1, z) is None:
                s.set(x, 1, z, "minecraft:brown_carpet")
    s.set(4, 3, 4, "minecraft:lantern[hanging=true]")
    s.set(5, 1, 3, "minecraft:white_carpet")
    # fogueira com bancos de tronco
    s.set(8, 0, 4, "minecraft:cobblestone")
    s.set(8, 1, 4, "minecraft:campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        s.set(8 + dx, 0, 4 + dz, "minecraft:cobblestone" if (dx + dz) % 2 else "minecraft:mossy_cobblestone")
    for x in range(7, 10):
        s.set(x, 1, 7, "minecraft:stripped_spruce_log[axis=x]")
    for z in range(3, 6):
        s.set(10, 1, z, "minecraft:stripped_spruce_log[axis=z]")
    brewing_stand(s, 10, 1, 1, ["swiftness"])
    s.set(9, 1, 1, "minecraft:cauldron")
    s.set(7, 1, 1, "minecraft:barrel[facing=up,open=false]")
    s.set(7, 2, 1, "minecraft:potted_blue_orchid")
    # poste com lanterna e estandarte
    s.set(6, 1, 9, "minecraft:spruce_fence")
    s.set(6, 2, 9, "minecraft:lantern[hanging=false]")
    s.set(10, 1, 10, "minecraft:spruce_fence")
    s.set(10, 2, 10, "minecraft:spruce_fence")
    s.set(10, 3, 10, "minecraft:purple_banner[rotation=4]")
    s.entity(8.5, 1, 6.2, f"{NS}:wandering_mage", {"PersistenceRequired": True})
    for x in range(S):
        for z in range(S):
            if 0 < x < S - 1 and 0 < z < S - 1:
                fill_empty(s, x, 1, z, x, 4, z, AIR, pred=lambda xx, yy, zz: not (1 <= xx <= 6 and 1 <= zz <= 7 and yy >= 1))
    for x in range(2, 7):   # interior da tenda
        fill_empty(s, x, 1, 2, x, 1, 6, AIR)
        fill_empty(s, x, 2, 3, x, 2, 5, AIR)
        fill_empty(s, x, 3, 4, x, 3, 4, AIR)
    return s


# =====================================================================================
#  Lago das Fadas (19x8x19: lagoa com flores, Flores-Vaga-lume, nenufares e fadas)
# =====================================================================================
def fairy_lake():
    S, H = 19, 8
    s = Structure(S, H, S)
    c = 9
    G = 3   # nivel do chao
    rnd = random.Random(7701)

    def e(x, z):   # distancia eliptica da lagoa (1 = margem)
        return math.hypot((x - c) / 6.2, (z - c) / 4.8)

    foot = [(x, z) for x in range(S) for z in range(S) if math.hypot(x - c, z - c) <= 9.2]
    for (x, z) in foot:
        ee = e(x, z)
        for y in range(0, G):
            s.set(x, y, z, "minecraft:dirt")
        if ee < 1.0:
            depth = 3 if ee < 0.45 else (2 if ee < 0.75 else 1)
            for y in range(G - depth + 1, G + 1):
                s.set(x, y, z, "minecraft:water")
            s.set(x, G - depth, z, "minecraft:clay" if depth >= 2 else "minecraft:sand")
            if depth >= 2 and rnd.random() < 0.35:
                s.set(x, G - depth + 1, z, "minecraft:seagrass")
            if depth == 3 and rnd.random() < 0.18:
                s.set(x, G - depth + 1, z, f"minecraft:sea_pickle[pickles={rnd.randint(2, 4)},waterlogged=true]")
            if rnd.random() < 0.12:
                s.set(x, G + 1, z, "minecraft:lily_pad")
        elif ee < 1.2:
            s.set(x, G, z, rnd.choice(("minecraft:sand", "minecraft:moss_block", "minecraft:grass_block[snowy=false]")))
        else:
            s.set(x, G, z, "minecraft:moss_block" if rnd.random() < 0.15 else "minecraft:grass_block[snowy=false]")
    # pedras musgosas com liquen brilhante
    for (x, z) in ((3, 6), (14, 13), (4, 13)):
        s.set(x, G + 1, z, "minecraft:mossy_cobblestone")
        s.set(x + 1, G + 1, z, "minecraft:mossy_stone_bricks")
        s.set(x, G + 2, z, "minecraft:moss_carpet")
        s.set(x, G + 1, z + 1, "minecraft:glow_lichen[north=true,waterlogged=false]")
    # pequena azaleia florida (arvorezinha) na margem norte
    tx, tz = 12, 3
    for y in range(G + 1, G + 4):
        s.set(tx, y, tz, "minecraft:oak_log[axis=y]")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for y in (G + 3, G + 4):
                if abs(dx) + abs(dz) <= (3 if y == G + 3 else 2) and (dx, dz) != (0, 0) or (y == G + 4 and (dx, dz) == (0, 0)):
                    leaf = "minecraft:flowering_azalea_leaves" if rnd.random() < 0.5 else "minecraft:azalea_leaves"
                    if 0 <= tx + dx < S and 0 <= tz + dz < S:
                        s.set(tx + dx, y, tz + dz, f"{leaf}[distance=1,persistent=true,waterlogged=false]")
    # flores da margem e Flores-Vaga-lume
    flowers = ["minecraft:allium", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:lily_of_the_valley",
               "minecraft:cornflower", "minecraft:pink_tulip", "minecraft:white_tulip", "minecraft:short_grass"]
    for (x, z) in foot:
        ee = e(x, z)
        if ee >= 1.1 and s.get(x, G + 1, z) is None:
            ground = s.get(x, G, z)
            if ground and (ground.startswith("minecraft:grass_block") or ground == "minecraft:moss_block"):
                r = rnd.random()
                if ee < 1.7 and r < 0.2:
                    s.set(x, G + 1, z, f"{NS}:fairy_glowbloom")
                elif r < 0.42:
                    s.set(x, G + 1, z, rnd.choice(flowers))
                elif r < 0.5:
                    s.set(x, G + 1, z, f"minecraft:pink_petals[flower_amount={rnd.randint(1, 4)},facing=" +
                          rnd.choice(("north", "south", "east", "west")) + "]")
    # toco com o esconderijo das fadas
    barrel(s, 2, G + 1, 13, "chests/fairy_lake")
    s.set(2, G + 2, 13, "minecraft:moss_carpet")
    for (x, z) in ((1, 13), (2, 14)):
        s.set(x, G + 1, z, "minecraft:oak_log[axis=y]")
        s.set(x, G + 2, z, "minecraft:moss_carpet")
    # fadas
    for (x, z) in ((6, 12), (13, 7), (9, 14)):
        s.entity(x + 0.5, G + 1.5, z + 0.5, f"{NS}:fairy", {"PersistenceRequired": True})
    for (x, z) in foot:
        fill_empty(s, x, G + 1, z, x, H - 1, z, AIR)
    return s


# =====================================================================================
#  Worldgen (estrutura jigsaw, conjunto, pools, processadores) e tags de bioma
# =====================================================================================
OVERWORLD_LAND = ["#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:plains", "minecraft:sunflower_plains",
                  "minecraft:meadow", "minecraft:snowy_plains", "minecraft:windswept_hills",
                  "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest"]

STRUCTURES = {
    # id: (biomas, terreno, start_height, spacing, separation, salt, size, excluir vilas, spawn_overrides)
    "mage_tower": (OVERWORLD_LAND + ["minecraft:savanna", "minecraft:savanna_plateau"],
                   "beard_thin", 0, 52, 22, 870412331, 1, True, None),
    "witch_hut_ancient": (["minecraft:swamp", "minecraft:mangrove_swamp"],
                          "none", -4, 40, 16, 870412332, 1, True,
                          {"monster": {"bounding_box": "piece", "spawns": [
                              {"type": "minecraft:witch", "weight": 1, "minCount": 1, "maxCount": 1}]}}),
    "profane_altar": (["minecraft:dark_forest", "#minecraft:is_badlands", "minecraft:desert", "minecraft:savanna_plateau",
                       "minecraft:windswept_savanna", "minecraft:snowy_taiga", "minecraft:old_growth_spruce_taiga"],
                      "beard_thin", 0, 50, 20, 870412333, 1, True, None),
    "druid_circle": (["minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
                      "minecraft:old_growth_birch_forest", "minecraft:plains", "minecraft:sunflower_plains",
                      "minecraft:meadow", "minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:cherry_grove",
                      "minecraft:dark_forest"],
                     "beard_thin", 0, 50, 20, 870412334, 1, True, None),
    "forgotten_crypt": (OVERWORLD_LAND + ["#minecraft:is_savanna", "#minecraft:is_badlands", "minecraft:desert"],
                        "none", -CRYPT_F, 48, 20, 870412335, 3, True,
                        {"monster": {"bounding_box": "piece", "spawns": [
                            {"type": "minecraft:skeleton", "weight": 10, "minCount": 1, "maxCount": 2},
                            {"type": "minecraft:zombie", "weight": 6, "minCount": 1, "maxCount": 2},
                            {"type": "minecraft:stray", "weight": 2, "minCount": 1, "maxCount": 1},
                            {"type": "minecraft:cave_spider", "weight": 2, "minCount": 1, "maxCount": 2}]}}),
    "arcane_ruins": (OVERWORLD_LAND + ["#minecraft:is_jungle", "#minecraft:is_savanna", "#minecraft:is_badlands",
                                       "minecraft:desert", "minecraft:cherry_grove"],
                     "beard_thin", 0, 20, 7, 870412336, 1, False, None),
    "wandering_mage_camp": (["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                             "minecraft:birch_forest", "minecraft:flower_forest", "minecraft:taiga",
                             "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:snowy_plains",
                             "minecraft:snowy_taiga", "minecraft:cherry_grove"],
                            "beard_thin", 0, 30, 11, 870412337, 1, False, None),
    "fairy_lake": (["minecraft:flower_forest", "minecraft:forest", "minecraft:birch_forest",
                    "minecraft:old_growth_birch_forest", "minecraft:meadow", "minecraft:cherry_grove",
                    "minecraft:plains", "minecraft:sunflower_plains"],
                   "none", -3, 28, 10, 870412338, 1, False, None),
}

PROCESSORS = {
    "mage_tower_weathering": [("minecraft:stone_bricks", 0.12, "minecraft:cracked_stone_bricks"),
                              ("minecraft:stone_bricks", 0.10, "minecraft:mossy_stone_bricks"),
                              ("minecraft:deepslate_bricks", 0.12, "minecraft:cracked_deepslate_bricks")],
    "profane_altar_decay": [("minecraft:polished_blackstone_bricks", 0.2, "minecraft:cracked_polished_blackstone_bricks"),
                            ("minecraft:blackstone", 0.04, "minecraft:gilded_blackstone")],
    "druid_circle_moss": [("minecraft:mossy_cobblestone", 0.2, "minecraft:moss_block"),
                          ("minecraft:stone", 0.3, "minecraft:mossy_cobblestone")],
    "forgotten_crypt_decay": [("minecraft:deepslate_bricks", 0.15, "minecraft:cracked_deepslate_bricks"),
                              ("minecraft:deepslate_tiles", 0.1, "minecraft:cracked_deepslate_tiles"),
                              ("minecraft:stone_bricks", 0.2, "minecraft:cracked_stone_bricks"),
                              ("minecraft:stone_bricks", 0.15, "minecraft:mossy_stone_bricks"),
                              ("minecraft:polished_deepslate", 0.05, "minecraft:cobbled_deepslate")],
    "arcane_ruins_decay": [("minecraft:stone_bricks", 0.25, "minecraft:cracked_stone_bricks"),
                           ("minecraft:stone_bricks", 0.2, "minecraft:mossy_stone_bricks")],
}


def processor_list(name, rules, rot=None):
    procs = [{"processor_type": "minecraft:rule", "rules": [
        {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": blk, "probability": p},
         "location_predicate": {"predicate_type": "minecraft:always_true"},
         "output_state": {"Name": out}} for (blk, p, out) in rules]}]
    if rot:
        procs.append({"processor_type": "minecraft:block_rot", "integrity": rot, "rottable_blocks": "#minecraft:stone_bricks"})
    write_json(os.path.join(D, f"worldgen/processor_list/{name}.json"), {"processors": procs})


def pool(name, elements, fallback="minecraft:empty"):
    write_json(os.path.join(D, f"worldgen/template_pool/{name}.json"), {
        "fallback": fallback,
        "elements": [{"weight": w, "element": {"element_type": "minecraft:single_pool_element",
                                               "location": f"{NS}:{loc}", "processors": proc,
                                               "projection": "rigid"}} for (loc, w, proc) in elements]})


def worldgen():
    for name, rules in PROCESSORS.items():
        processor_list(name, rules, rot=0.93 if name == "arcane_ruins_decay" else None)
    for sid, (biomes, terrain, sh, spacing, sep, salt, size, excl, spawns) in STRUCTURES.items():
        write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/{sid}.json"),
                   {"replace": False, "values": biomes})
        st = {
            "type": "minecraft:jigsaw",
            "biomes": f"#{NS}:has_structure/{sid}",
            "step": "surface_structures",
            "spawn_overrides": spawns or {},
            "terrain_adaptation": terrain,
            "start_pool": f"{NS}:{sid}/start",
            "size": size,
            "start_height": {"absolute": sh},
            "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 80,
            "use_expansion_hack": False,
        }
        write_json(os.path.join(D, f"worldgen/structure/{sid}.json"), st)
        placement = {"type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": salt}
        if excl:
            placement["exclusion_zone"] = {"other_set": "minecraft:villages", "chunk_count": 4}
        write_json(os.path.join(D, f"worldgen/structure_set/{sid}.json"),
                   {"structures": [{"structure": f"{NS}:{sid}", "weight": 1}], "placement": placement})
    p = lambda n: f"{NS}:{n}"
    pool("mage_tower/start", [("mage_tower", 1, p("mage_tower_weathering"))])
    pool("witch_hut_ancient/start", [("witch_hut_ancient", 1, "minecraft:empty")])
    pool("profane_altar/start", [("profane_altar", 1, p("profane_altar_decay"))])
    pool("druid_circle/start", [("druid_circle", 1, p("druid_circle_moss"))])
    pool("forgotten_crypt/start", [("forgotten_crypt/entrance", 1, p("forgotten_crypt_decay"))])
    pool("forgotten_crypt/hall", [("forgotten_crypt/hall", 1, p("forgotten_crypt_decay"))])
    pool("forgotten_crypt/lich_chamber", [("forgotten_crypt/lich_chamber", 1, p("forgotten_crypt_decay"))])
    pool("forgotten_crypt/side_rooms", [("forgotten_crypt/sarcophagus_room", 3, p("forgotten_crypt_decay")),
                                        ("forgotten_crypt/ossuary_room", 2, p("forgotten_crypt_decay")),
                                        ("forgotten_crypt/collapsed_room", 2, p("forgotten_crypt_decay"))],
         fallback=p("forgotten_crypt/dead_ends"))
    pool("forgotten_crypt/dead_ends", [("forgotten_crypt/dead_end", 1, p("forgotten_crypt_decay"))])
    pool("arcane_ruins/start", [("arcane_ruins/circle", 3, p("arcane_ruins_decay")),
                                ("arcane_ruins/arch", 2, p("arcane_ruins_decay")),
                                ("arcane_ruins/study", 2, p("arcane_ruins_decay"))])
    pool("wandering_mage_camp/start", [("wandering_mage_camp", 1, "minecraft:empty")])
    pool("fairy_lake/start", [("fairy_lake", 1, "minecraft:empty")])

    # tags de estrutura para os mapas de exploracao
    classes = ["mage_tower", "witch_hut_ancient", "profane_altar", "druid_circle", "forgotten_crypt"]
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure/on_arcane_ruin_maps.json"),
               {"replace": False, "values": [p(c) for c in classes]})
    write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure/on_mage_camp_maps.json"),
               {"replace": False, "values": [p("mage_tower")]})
    # o gato da cabana nasce preto (Cat.finalizeSpawn sorteia a cor, exceto dentro destas estruturas)
    write_json(os.path.join(MERGE, "data/minecraft/tags/worldgen/structure/cats_spawn_as_black.json"),
               {"replace": False, "values": [p("witch_hut_ancient")]})
    # tags de bloco
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"),
               {"replace": False, "values": [p("crypt_sarcophagus")]})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/small_flowers.json"),
               {"replace": False, "values": [p("fairy_glowbloom")]})


# =====================================================================================
#  Loot tables
# =====================================================================================
def num(c):
    return c if isinstance(c, int) else {"type": "minecraft:uniform", "min": c[0], "max": c[1]}


def item(name, weight=1, count=None, functions=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    fn = list(functions or [])
    if count is not None and count != 1:
        fn.append({"function": "minecraft:set_count", "count": num(count)})
    if fn:
        e["functions"] = fn
    return e


def scroll(weight, glyphs=(), elements=(), min_tier=1, max_tier=3):
    f = {"function": f"{NS}:ruin_glyph_scroll", "min_tier": min_tier, "max_tier": max_tier}
    if glyphs:
        f["glyphs"] = [f"{NS}:{g}" for g in glyphs]
    if elements:
        f["elements"] = list(elements)
    return item(f"{NS}:glyph_scroll", weight, functions=[f])


def opt(name, fallback, weight=1, count=None, components=None):
    """Item de outro modulo (por id) com substituto vanilla/nucleo se o modulo nao existir.
    A quantidade vem ANTES da troca: o item_if_present limita ao tamanho de pilha do item trocado
    (ex.: giz com durabilidade sai 1, o substituto vanilla mantem a quantidade sorteada)."""
    f = {"function": f"{NS}:item_if_present", "item": f"{NS}:{name}"}
    if components:
        f["components"] = components
    fn = []
    if count is not None and count != 1:
        fn.append({"function": "minecraft:set_count", "count": num(count)})
    fn.append(f)
    return item(fallback, weight, functions=fn)


def note(element, phenomenon, weight=1, count=(1, 2)):
    return opt("research_note", "minecraft:paper", weight, count,
               {f"{NS}:mage_research_note": {"phenomenon": f"{NS}:{phenomenon}", "element": element}})


def ess(e, weight, count=(1, 2)):
    return item(f"{NS}:essence_{e}", weight, count)


def book_ench(weight, levels=None):
    fn = ({"function": "minecraft:enchant_with_levels", "levels": num(levels), "options": "#minecraft:on_random_loot"}
          if levels else {"function": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"})
    return item("minecraft:book", weight, functions=[fn])


def potion(pid, weight, kind="minecraft:potion"):
    return item(kind, weight, functions=[{"function": "minecraft:set_potion", "id": f"minecraft:{pid}"}])


def explorer_map(weight, tag, name_key, decoration):
    return item("minecraft:map", weight, functions=[
        {"function": "minecraft:exploration_map", "destination": f"{NS}:{tag}", "decoration": decoration,
         "zoom": 2, "search_radius": 50, "skip_existing_chunks": False},
        {"function": "minecraft:set_name", "name": {"translate": f"filled_map.{NS}.{name_key}"}, "target": "item_name"}])


def journal_book(weight):
    return item("minecraft:written_book", weight, functions=[
        {"function": "minecraft:set_book_cover", "title": "Valdris", "author": "Valdris"},
        {"function": "minecraft:set_written_book_pages", "mode": "replace_all",
         "pages": [{"translate": f"book.{NS}.archmage_journal.page{i}"} for i in range(1, JOURNAL_PAGES + 1)]},
        {"function": "minecraft:set_name", "target": "custom_name",
         "name": {"translate": f"book.{NS}.archmage_journal", "italic": False, "color": "light_purple"}}])


def lpool(rolls, entries):
    return {"rolls": num(rolls), "bonus_rolls": 0, "entries": entries}


def loot(path, pools, kind="minecraft:chest"):
    write_json(os.path.join(D, f"loot_table/{path}.json"),
               {"type": kind, "pools": pools, "random_sequence": f"{NS}:{path}"})


ARCANE_GLYPHS = ["beam", "cone", "nova", "zone", "wall", "rune", "turret", "orbit", "blink", "shield", "counterspell",
                 "dispel", "telekinesis", "mana_drain", "reflect", "haste_time", "slow_time", "levitate", "pulse", "aura"]
BASIC_GLYPHS = ["ignite", "soak", "rock", "wind_blade", "holy_ray", "life_drain", "pull", "blink", "shield", "beam",
                "cone", "nova", "zone"]


def loot_tables():
    # ---------- Torre do Mago ----------
    loot("chests/mage_tower", [
        lpool(2, [scroll(1, ARCANE_GLYPHS, max_tier=4)]),
        lpool((3, 5), [
            item(f"{NS}:mana_crystal", 15, (1, 4)), ess("arcane", 12, (1, 3)),
            ess("fire", 3), ess("water", 3), ess("earth", 3), ess("wind", 3), ess("light", 3), ess("shadow", 3),
            book_ench(8, (15, 30)), item("minecraft:experience_bottle", 8, (2, 5)),
            item("minecraft:amethyst_shard", 6, (2, 6)), item("minecraft:ender_pearl", 5, (1, 2)),
            opt("arcane_lens", "minecraft:spyglass", 3), opt("arcane_amulet", f"{NS}:mana_crystal", 2, (1, 1)),
            opt("mana_ring", f"{NS}:mana_crystal", 2), item(f"{NS}:spell_scroll", 3),
            opt("focus_fire", f"{NS}:essence_fire", 1), opt("focus_water", f"{NS}:essence_water", 1),
            opt("focus_earth", f"{NS}:essence_earth", 1), opt("focus_wind", f"{NS}:essence_wind", 1),
            opt("focus_light", f"{NS}:essence_light", 1), opt("focus_shadow", f"{NS}:essence_shadow", 1)]),
        lpool(1, [note(e, "mage_tower", 1, (2, 4)) for e in ("arcane", "fire", "water", "earth", "wind", "light", "shadow")]),
    ])
    loot("chests/mage_tower_library", [
        lpool((3, 6), [
            item("minecraft:book", 15, (2, 6)), item("minecraft:paper", 10, (3, 9)), item("minecraft:writable_book", 3),
            item("minecraft:ink_sac", 6, (1, 3)), item("minecraft:glow_ink_sac", 3, (1, 2)),
            item("minecraft:feather", 5, (1, 4)), item("minecraft:lapis_lazuli", 6, (3, 9)),
            item("minecraft:purple_candle", 4, (1, 3)), book_ench(5, (8, 20)), journal_book(3),
            scroll(8, ARCANE_GLYPHS, max_tier=3)] +
            [note(e, "mage_tower", 2) for e in ("arcane", "fire", "water", "earth", "wind", "light", "shadow")]),
    ])
    loot("chests/mage_tower_storage", [
        lpool((3, 6), [
            item("minecraft:bread", 10, (1, 4)), item("minecraft:apple", 8, (1, 3)),
            item("minecraft:glass_bottle", 8, (1, 4)), item("minecraft:coal", 6, (2, 6)),
            item("minecraft:string", 6, (1, 4)), item("minecraft:glowstone_dust", 5, (1, 4)),
            item("minecraft:redstone", 5, (1, 5)), item("minecraft:sugar", 5, (1, 3)),
            item("minecraft:candle", 4, (1, 3)), potion("healing", 3), potion("night_vision", 3),
            potion("regeneration", 2), item(f"{NS}:mana_crystal", 3, (1, 2)), ess("arcane", 2)]),
    ])
    # ---------- Cabana da Bruxa ----------
    loot("chests/witch_hut_ancient", [
        lpool((1, 2), [opt("witch_recipe_page", "minecraft:paper", 1)]),
        lpool(1, [scroll(1, ["soak", "mist", "curse", "weaken", "life_drain", "fertilize", "root", "mend",
                             "regenerate", "bubble", "fear", "blind", "pain_link", "hydrate", "local_rain"])]),
        lpool((2, 5), [
            potion("poison", 5), potion("healing", 5), potion("swiftness", 4), potion("night_vision", 4),
            potion("invisibility", 2), potion("weakness", 3, "minecraft:splash_potion"),
            potion("slowness", 3, "minecraft:splash_potion"),
            item("minecraft:glowstone_dust", 6, (2, 6)), item("minecraft:redstone", 6, (2, 6)),
            item("minecraft:spider_eye", 6, (1, 3)), item("minecraft:fermented_spider_eye", 3, (1, 2)),
            item("minecraft:sugar", 5, (1, 4)), opt("slime_gel", "minecraft:slime_ball", 4, (1, 3)),
            ess("water", 6), ess("shadow", 6), item(f"{NS}:mana_crystal", 5, (1, 3)),
            opt("ritual_spoon", "minecraft:bowl", 2), item("minecraft:rabbit_foot", 2),
            item("minecraft:phantom_membrane", 2), item("minecraft:emerald", 4, (1, 4))]),
    ])
    loot("chests/witch_hut_pantry", [
        lpool((3, 6), [
            item("minecraft:red_mushroom", 8, (1, 4)), item("minecraft:brown_mushroom", 8, (1, 4)),
            item("minecraft:spider_eye", 6, (1, 2)), item("minecraft:sugar", 6, (1, 4)),
            item("minecraft:glow_berries", 6, (2, 6)), item("minecraft:sweet_berries", 6, (2, 6)),
            item("minecraft:nether_wart", 3, (1, 3)), item("minecraft:bone_meal", 5, (2, 6)),
            item("minecraft:string", 5, (1, 4)), item("minecraft:glass_bottle", 6, (1, 4)),
            item("minecraft:honey_bottle", 3), item("minecraft:dried_kelp", 4, (2, 6)),
            opt("witch_recipe_page", "minecraft:paper", 2)]),
    ])
    # ---------- Altar Profano ----------
    loot("chests/profane_altar", [
        lpool((1, 2), [scroll(1, ["life_drain", "curse", "darkness", "chains", "fear", "pain_link", "devour",
                                  "shadow_step", "explosion", "ember", "blue_flame", "weaken", "nightmare",
                                  "steal_blessing", "combustion"], max_tier=4)]),
        lpool(1, [opt("ritual_chalk_item", "minecraft:bone_meal", 8, (2, 6)),
                  opt("ritual_dagger", "minecraft:flint", 2)]),
        lpool((3, 6), [
            ess("shadow", 12, (1, 3)), ess("fire", 8), item("minecraft:gold_ingot", 8, (1, 4)),
            item("minecraft:gold_nugget", 8, (3, 9)), item("minecraft:crying_obsidian", 5, (1, 3)),
            item("minecraft:obsidian", 4, (1, 2)), item("minecraft:blaze_powder", 4, (1, 3)),
            item("minecraft:ender_pearl", 4), item("minecraft:black_candle", 6, (1, 3)),
            item("minecraft:golden_apple", 2), item("minecraft:experience_bottle", 5, (2, 4)),
            item(f"{NS}:mana_crystal", 8, (1, 3))]),
    ])
    # ---------- Circulo Druidico ----------
    loot("chests/druid_circle", [
        lpool(1, [opt("ancestral_sapling", "minecraft:flowering_azalea", 1)]),
        lpool((1, 2), [scroll(1, ["root", "fertilize", "rock", "stoneskin", "raise_pillar", "gust", "wind_blade",
                                  "regenerate", "mend", "heal", "quicksand", "harvest", "vines", "growth",
                                  "thorns", "spores", "tremor", "hydrate"])]),
        lpool((3, 6), [
            ess("earth", 10, (1, 3)), ess("wind", 6), ess("water", 6), item(f"{NS}:mana_crystal", 6, (1, 3)),
            item("minecraft:moss_block", 5, (2, 6)), item("minecraft:glow_berries", 6, (2, 6)),
            item("minecraft:sweet_berries", 6, (2, 6)), item("minecraft:apple", 6, (1, 4)),
            item("minecraft:golden_carrot", 3, (1, 3)), item("minecraft:bone_meal", 6, (3, 8)),
            item("minecraft:oak_sapling", 3, (1, 3)), item("minecraft:cherry_sapling", 2, (1, 2)),
            item("minecraft:honeycomb", 3, (1, 3)), item("minecraft:emerald", 3, (1, 3)),
            item("minecraft:experience_bottle", 4, (1, 3)), item(f"{NS}:fairy_glowbloom", 4, (1, 3))]),
    ])
    # ---------- Cripta Esquecida ----------
    crypt_glyphs = ["life_drain", "darkness", "curse", "weaken", "fear", "chains", "exorcise", "holy_ray", "purify",
                    "reveal", "nightmare", "devour", "sanctuary", "blessing"]
    loot("chests/forgotten_crypt", [
        lpool(2, [scroll(1, crypt_glyphs, max_tier=4)]),
        lpool(1, [opt("phylactery", "minecraft:amethyst_shard", 3, (1, 1)),
                  opt("bone_scythe", "minecraft:bone", 2, (1, 1)),
                  item(f"{NS}:mana_crystal", 2, (3, 6))]),
        lpool((3, 6), [
            ess("shadow", 10, (1, 3)), ess("light", 6), item(f"{NS}:mana_crystal", 8, (2, 5)),
            item("minecraft:gold_ingot", 6, (2, 5)), item("minecraft:emerald", 5, (1, 4)),
            item("minecraft:diamond", 2, (1, 2)), book_ench(5, (20, 30)),
            item("minecraft:experience_bottle", 6, (3, 7)), item("minecraft:bone", 6, (4, 10)),
            item("minecraft:golden_apple", 2), item("minecraft:echo_shard", 1), item("minecraft:ender_pearl", 3)]),
    ])
    loot("chests/forgotten_crypt_side", [
        lpool((2, 5), [
            item("minecraft:bone", 10, (2, 6)), item("minecraft:arrow", 6, (2, 8)),
            item("minecraft:rotten_flesh", 6, (1, 4)), item("minecraft:gold_nugget", 6, (2, 8)),
            item("minecraft:iron_ingot", 4, (1, 3)), item("minecraft:candle", 4, (1, 3)),
            item("minecraft:book", 3, (1, 2)), ess("shadow", 4), item(f"{NS}:mana_crystal", 4, (1, 2)),
            scroll(3, crypt_glyphs, max_tier=3), item("minecraft:name_tag", 1), item("minecraft:skeleton_skull", 1)]),
    ])
    loot("chests/forgotten_crypt_sarcophagus", [
        lpool((1, 3), [
            item("minecraft:bone", 8, (1, 4)), item("minecraft:gold_nugget", 8, (2, 7)),
            item("minecraft:gold_ingot", 4, (1, 2)), item("minecraft:emerald", 4, (1, 2)),
            item("minecraft:golden_helmet", 2, functions=[{"function": "minecraft:enchant_randomly",
                                                           "options": "#minecraft:on_random_loot"}]),
            item("minecraft:iron_sword", 2, functions=[{"function": "minecraft:enchant_randomly",
                                                        "options": "#minecraft:on_random_loot"}]),
            ess("shadow", 4), item(f"{NS}:mana_crystal", 3, (1, 2)), scroll(2, crypt_glyphs),
            item("minecraft:rotten_flesh", 4, (1, 3))]),
    ])
    loot("chests/forgotten_crypt_urn", [
        lpool(1, [
            item("minecraft:emerald", 4, (1, 2)), item("minecraft:gold_nugget", 8, (2, 6)),
            item("minecraft:bone", 6, (1, 3)), item(f"{NS}:mana_crystal", 3), ess("shadow", 2, 1),
            item("minecraft:diamond", 1), item("minecraft:arrow", 5, (2, 6)), item("minecraft:amethyst_shard", 3, (1, 3))]),
    ])
    loot("archaeology/forgotten_crypt", [
        lpool(1, [item("minecraft:bone", 5, (1, 2)), item("minecraft:skull_pottery_sherd", 3),
                  item("minecraft:gold_nugget", 4, (1, 3)), ess("shadow", 3, 1), item("minecraft:emerald", 2),
                  item(f"{NS}:mana_crystal", 2)]),
    ], kind="minecraft:archaeology")
    # ---------- Ruinas Arcanas ----------
    loot("chests/arcane_ruins", [
        lpool(1, [scroll(3, BASIC_GLYPHS), scroll(5)]),
        lpool(1, [item(f"{NS}:mana_crystal", 1, (1, 3))]),
        lpool((2, 4), [
            ess("arcane", 8), ess("fire", 3), ess("water", 3), ess("earth", 3), ess("wind", 3), ess("light", 3),
            ess("shadow", 3), item("minecraft:book", 6, (1, 3)), item("minecraft:paper", 6, (2, 6)),
            item("minecraft:amethyst_shard", 8, (1, 4)), item("minecraft:lapis_lazuli", 6, (2, 6)),
            item("minecraft:gold_nugget", 5, (2, 6)), item("minecraft:experience_bottle", 4, (1, 3)),
            note("arcane", "arcane_ruins", 4), item("minecraft:candle", 4, (1, 3)), book_ench(3),
            explorer_map(2, "on_arcane_ruin_maps", "arcane_ruin_map", "minecraft:target_x")]),
    ])
    loot("archaeology/arcane_ruins", [
        lpool(1, [ess("arcane", 4, 1), item(f"{NS}:mana_crystal", 3), item("minecraft:amethyst_shard", 5, (1, 2)),
                  item("minecraft:lapis_lazuli", 4, (1, 3)), item("minecraft:gold_nugget", 4, (1, 3)),
                  scroll(2, max_tier=2), item("minecraft:prize_pottery_sherd", 2),
                  item("minecraft:heart_pottery_sherd", 2), item("minecraft:arms_up_pottery_sherd", 2)]),
    ], kind="minecraft:archaeology")
    # ---------- Acampamento do Mago Errante ----------
    loot("chests/wandering_mage_camp", [
        lpool((3, 5), [
            item("minecraft:bread", 8, (1, 4)), item("minecraft:cooked_mutton", 6, (1, 3)),
            item("minecraft:apple", 6, (1, 3)), potion("healing", 4), item("minecraft:paper", 6, (2, 5)),
            item("minecraft:book", 4, (1, 2)), item(f"{NS}:mana_crystal", 6, (1, 2)), ess("arcane", 5),
            scroll(4, max_tier=2), opt("guild_coin", "minecraft:emerald", 3, (1, 3)),
            explorer_map(2, "on_mage_camp_maps", "mage_camp_map", "minecraft:target_point"),
            item("minecraft:lantern", 3), item("minecraft:candle", 3, (1, 3)), item("minecraft:torch", 5, (2, 6))]),
    ])
    # ---------- Lago das Fadas ----------
    loot("chests/fairy_lake", [
        lpool(1, [scroll(1, ["heal", "mend", "regenerate", "blessing", "conjure_light", "mist", "gust", "slow_fall",
                             "levitate", "growth", "purify", "sunlight", "hydrate", "bubble"])]),
        lpool((2, 4), [
            ess("light", 6), ess("water", 5), ess("wind", 4), item("minecraft:glow_berries", 6, (2, 6)),
            item("minecraft:honey_bottle", 5, (1, 2)), item("minecraft:amethyst_shard", 5, (1, 4)),
            item("minecraft:sugar", 4, (1, 4)), item("minecraft:sweet_berries", 5, (2, 6)),
            item(f"{NS}:fairy_glowbloom", 6, (1, 3)), item("minecraft:cake", 1), item("minecraft:golden_apple", 1),
            item(f"{NS}:mana_crystal", 4, (1, 2))]),
    ])
    # ---------- Blocos ----------
    write_json(os.path.join(D, "loot_table/blocks/fairy_glowbloom.json"), {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/fairy_glowbloom",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:fairy_glowbloom"}]}]})
    write_json(os.path.join(D, "loot_table/blocks/crypt_sarcophagus.json"), {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/crypt_sarcophagus",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "conditions": [
            {"condition": "minecraft:survives_explosion"},
            {"condition": "minecraft:block_state_property", "block": f"{NS}:crypt_sarcophagus",
             "properties": {"part": "head"}}],
            "entries": [{"type": "minecraft:item", "name": f"{NS}:crypt_sarcophagus"}]}]})


# =====================================================================================
#  Fenomenos para a Lente Arcana (modulo ritual_mage le data/<ns>/mage_phenomena)
# =====================================================================================
def phenomena():
    entries = {
        "mage_tower": (["arcane", "light"], "epic", ["minecraft:bookshelf"], 300),
        "witch_hut_ancient": (["water", "shadow"], "rare", ["minecraft:cauldron"], 301),
        "profane_altar": (["shadow", "fire"], "epic", ["minecraft:crying_obsidian"], 302),
        "druid_circle": (["earth", "nature"], "rare", ["minecraft:mossy_cobblestone"], 303),
        "forgotten_crypt": (["shadow", "light"], "epic", ["minecraft:skeleton_skull"], 304),
        "arcane_ruins": (["arcane"], "uncommon", ["minecraft:chiseled_stone_bricks"], 305),
        "wandering_mage_camp": (["arcane", "fire"], "common", ["minecraft:campfire"], 306),
        "fairy_lake": (["light", "water"], "uncommon", [f"{NS}:fairy_glowbloom", "minecraft:lily_pad"], 307),
    }
    for sid, (els, rarity, icon, order) in entries.items():
        write_json(os.path.join(D, f"mage_phenomena/{sid}.json"), {
            "category": "structure", "elements": els, "rarity": rarity, "icon": icon, "order": order,
            "structures": [f"{NS}:{sid}"]})


# =====================================================================================
def main():
    glowbloom_textures()
    sarcophagus_textures()
    glowbloom_models()
    sarcophagus_models()
    out = os.path.join(D, "structure")
    built = {
        "mage_tower": mage_tower(),
        "witch_hut_ancient": witch_hut_ancient(),
        "profane_altar": profane_altar(),
        "druid_circle": druid_circle(),
        "forgotten_crypt/entrance": crypt_entrance(),
        "forgotten_crypt/hall": crypt_hall(),
        "forgotten_crypt/sarcophagus_room": crypt_room("sarcophagus_room"),
        "forgotten_crypt/ossuary_room": crypt_room("ossuary_room"),
        "forgotten_crypt/collapsed_room": crypt_room("collapsed_room"),
        "forgotten_crypt/dead_end": crypt_dead_end(),
        "forgotten_crypt/lich_chamber": crypt_lich_chamber(),
        "arcane_ruins/circle": ruins_circle(),
        "arcane_ruins/arch": ruins_arch(),
        "arcane_ruins/study": ruins_study(),
        "wandering_mage_camp": mage_camp(),
        "fairy_lake": fairy_lake(),
    }
    for name, s in built.items():
        n = s.save(os.path.join(out, name + ".nbt"))
        print(f"  {name}.nbt: {n} blocos, {len(s.entities)} entidades, tamanho {s.size}")
    worldgen()
    loot_tables()
    phenomena()
    print("structures_b: ok")


if __name__ == "__main__":
    main()
