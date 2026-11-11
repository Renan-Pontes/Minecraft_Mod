"""Gera os assets/dados do modulo "guild" (Guilda dos Aventureiros).

Uso (na raiz do projeto):  py -I tools/gen/guild.py

Gera: texturas (cartao por rank, moeda, quadro de missoes, balcao), modelos de bloco/item, blockstates, ovos,
modelos GeckoLib + texturas + animacoes dos NPCs (recepcionista, mestre, aventureiro em 5 classes),
missoes (data/isekai_mahou/guild_quests), loja (guild_shop), receitas, loot tables, tag de machado (merge)
e as traducoes pt_br/en_us do modulo.

v0.9: Areas de Caca — estandarte GeckoLib (hunt_banner: geo, textura, glowmask, animacoes) e traducoes.
     Rodar so a parte nova:  py -I tools/gen/guild.py v09

v0.8: Quadro de Faccao (faction_board[facing, faction] — 5 brasoes), clerigo, missoes de faccao (Reino, Academia, Igreja,
Submundo + encomendas do Povo Selvagem na Guilda), loja por patamar, capitulo "kingdoms" do Diario e traducoes novas.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/guild")
LANG = os.path.join(ROOT, "src/main/lang/guild")
NS = "isekai_mahou"

T = (0, 0, 0, 0)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def jitter(col, rnd, amount):
    f = (rnd.random() - 0.5) * 2 * amount
    return lighten(col, f) if f > 0 else darken(col, -f)


def save(c, rel):
    c.save(os.path.join(A, "textures", rel + ".png"))


# ------------------------------------------------------------------------------------------------
# Paleta comum
# ------------------------------------------------------------------------------------------------
WOOD0 = hex_rgba("2E1F14")
WOOD1 = hex_rgba("4A3220")
WOOD2 = hex_rgba("5E412A")
WOOD3 = hex_rgba("7A5636")
WOOD4 = hex_rgba("9A6B3F")
WOOD5 = hex_rgba("B98552")
PAPER = hex_rgba("EDE3C8")
PAPER2 = hex_rgba("E2D2A6")
INK = hex_rgba("6B5B45")
GOLD0 = hex_rgba("5A3E10")
GOLD1 = hex_rgba("A57A22")
GOLD2 = hex_rgba("E8B84A")
GOLD3 = hex_rgba("FFE38A")
RED1 = hex_rgba("9E2B25")
RED2 = hex_rgba("C9433A")

RANKS = ["f", "e", "d", "c", "b", "a", "s"]
RANK_COLORS = {"f": "9AA4B0", "e": "6BD66B", "d": "4FD8D8", "c": "5B9BFF", "b": "B46BFF", "a": "FFC94F", "s": "FF5B5B"}
FONT3x5 = {
    "f": ["###", "#..", "##.", "#..", "#.."],
    "e": ["###", "#..", "##.", "#..", "###"],
    "d": ["##.", "#.#", "#.#", "#.#", "##."],
    "c": [".##", "#..", "#..", "#..", ".##"],
    "b": ["##.", "#.#", "##.", "#.#", "##."],
    "a": [".#.", "#.#", "###", "#.#", "#.#"],
    "s": [".##", "#..", ".#.", "..#", "##."],
}


# ------------------------------------------------------------------------------------------------
# Itens
# ------------------------------------------------------------------------------------------------
def adventurer_card(rank):
    c = Canvas(16, 16)
    rc = hex_rgba(RANK_COLORS[rank])
    if rank == "s":
        body, stripe, trim, letter = hex_rgba("6E1622"), hex_rgba("3A0A12"), GOLD2, GOLD3
    elif rank == "a":
        body, stripe, trim, letter = darken(rc, 0.15), darken(rc, 0.5), hex_rgba("FFF3C4"), hex_rgba("3A2A10")
    else:
        body, stripe, trim, letter = darken(rc, 0.25), darken(rc, 0.6), lighten(rc, 0.45), hex_rgba("FFFFFF")
    rnd = random.Random(7 + RANKS.index(rank))
    # corpo do cartao (cantos arredondados)
    for y in range(3, 13):
        for x in range(1, 15):
            if (x in (1, 14)) and (y in (3, 12)):
                continue
            c.set(x, y, jitter(body, rnd, 0.04))
    for x in range(2, 14):
        c.set(x, 3, lighten(body, 0.25))
    # faixa superior com emblema
    for y in (4, 5):
        for x in range(1, 15):
            c.set(x, y, stripe)
    c.set(2, 4, GOLD2); c.set(3, 4, GOLD3); c.set(2, 5, GOLD1); c.set(3, 5, GOLD2)
    for x in range(5, 12):
        c.set(x, 4, darken(trim, 0.2) if x % 2 else trim)
    # retrato
    for y in range(7, 12):
        for x in range(2, 6):
            c.set(x, y, hex_rgba("2A2420"))
    c.set(3, 8, hex_rgba("E8B896")); c.set(4, 8, hex_rgba("E8B896"))
    c.set(3, 9, hex_rgba("D9A27E")); c.set(4, 9, hex_rgba("D9A27E"))
    c.set(2, 10, trim); c.set(3, 10, trim); c.set(4, 10, trim); c.set(5, 10, trim)
    c.set(2, 11, darken(trim, 0.3)); c.set(5, 11, darken(trim, 0.3))
    c.set(3, 7, hex_rgba("4A3020")); c.set(4, 7, hex_rgba("4A3020"))
    # linhas de texto
    for x in range(7, 10):
        c.set(x, 7, lighten(body, 0.5))
    for x in range(7, 9):
        c.set(x, 9, lighten(body, 0.35))
    for x in range(7, 10):
        c.set(x, 11, lighten(body, 0.35))
    # letra do rank
    for dy, row in enumerate(FONT3x5[rank]):
        for dx, ch in enumerate(row):
            if ch == "#":
                c.set(11 + dx, 7 + dy, letter)
    c.outline(hex_rgba("1A120A"))
    # sombra embaixo
    for x in range(2, 15):
        if c.get(x, 13)[3] > 0:
            c.set(x, 13, hex_rgba("140D08"))
    save(c, f"item/adventurer_card_{rank}")


def guild_coin():
    c = Canvas(16, 16)
    c.circle(8, 8, 6.5, GOLD1)
    c.circle(8, 8, 5.5, GOLD2)
    # emblema: escudo com espada
    shield = ["#####", "#####", "#####", ".###.", "..#.."]
    for dy, row in enumerate(shield):
        for dx, ch in enumerate(row):
            if ch == "#":
                c.set(6 + dx, 5 + dy, GOLD1)
    for y in range(4, 11):
        c.set(8, y, GOLD3)
    c.set(7, 6, GOLD3); c.set(9, 6, GOLD3)
    c.shade_sphere(8, 8, 6.5, light=0.35, dark=0.35)
    c.set(5, 4, (255, 250, 220, 255)); c.set(4, 5, (255, 240, 190, 255))
    c.outline(GOLD0)
    save(c, "item/guild_coin")


# ------------------------------------------------------------------------------------------------
# Blocos
# ------------------------------------------------------------------------------------------------
def planks(c, x0, y0, x1, y1, base, seed, vertical=False, seam=4):
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            col = jitter(base, rnd, 0.06)
            k = (x - x0) if vertical else (y - y0)
            if k % seam == seam - 1:
                col = darken(base, 0.35)
            # veios
            g = (y - y0) if vertical else (x - x0)
            if rnd.random() < 0.08 and g % 3 == 0:
                col = darken(col, 0.15)
            c.set(x, y, col)


def note(c, x0, y0, x1, y1, paper, pin, seed, sketch=False):
    rnd = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c.set(x, y, jitter(paper, rnd, 0.04))
    for x in range(x0, x1 + 1):
        c.set(x, y1, darken(paper, 0.18))
    if sketch:
        cx, cy = (x0 + x1) // 2, y0 + 3
        for (dx, dy) in ((0, 0), (-1, 0), (1, 0), (0, 1), (-1, 1), (1, 1), (0, -1)):
            c.set(cx + dx, cy + dy, hex_rgba("3D2E22"))
        for x in range(x0 + 1, x1):
            c.set(x, y1 - 1, INK)
    else:
        for y in range(y0 + 2, y1, 2):
            for x in range(x0 + 1, x1 - (1 if y % 4 else 0)):
                c.set(x, y, INK if rnd.random() < 0.85 else paper)
    c.set((x0 + x1) // 2, y0, pin)


def quest_board_front():
    c = Canvas(16, 16)
    # cortiça / madeira clara no fundo
    rnd = random.Random(11)
    for y in range(16):
        for x in range(16):
            c.set(x, y, jitter(hex_rgba("8B6A45"), rnd, 0.08))
    # moldura
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            c.set(x, y, WOOD1)
    for i in range(1, 15):
        for (x, y) in ((i, 1), (1, i)):
            c.set(x, y, WOOD3)
        for (x, y) in ((i, 14), (14, i)):
            c.set(x, y, WOOD2)
    note(c, 2, 2, 6, 7, PAPER, RED2, 1)
    note(c, 8, 3, 13, 8, PAPER2, hex_rgba("3A6BC9"), 2)
    note(c, 3, 9, 8, 13, PAPER, RED2, 3, sketch=True)
    note(c, 10, 10, 13, 13, hex_rgba("D9E6C8"), GOLD2, 4)
    save(c, "block/quest_board_front")


def quest_board_side():
    c = Canvas(16, 16)
    planks(c, 0, 0, 15, 15, WOOD2, 12, vertical=True, seam=5)
    save(c, "block/quest_board_side")


def quest_board_paper():
    c = Canvas(16, 16)
    note(c, 0, 0, 15, 15, PAPER, RED2, 5)
    # selo de cera
    c.circle(12, 12, 2.2, RED1)
    c.set(12, 12, RED2); c.set(11, 11, hex_rgba("E86A5E"))
    save(c, "block/quest_board_paper")


def counter_front():
    c = Canvas(16, 16)
    planks(c, 0, 0, 15, 15, WOOD1, 21, vertical=False, seam=16)
    for x in range(16):
        c.set(x, 0, WOOD3); c.set(x, 15, WOOD0)
    # painel rebaixado
    for y in range(3, 13):
        for x in range(3, 13):
            c.set(x, y, darken(c.get(x, y), 0.15))
    for i in range(3, 13):
        c.set(i, 3, WOOD0); c.set(3, i, WOOD0)
        c.set(i, 12, WOOD3); c.set(12, i, WOOD3)
    # emblema: escudo dourado com espadas cruzadas
    emblem = [
        "#......#",
        ".#....#.",
        "..####..",
        "..#ss#..",
        "..#ss#..",
        ".#.##.#.",
        "#..##..#",
        "........",
    ]
    for dy, row in enumerate(emblem):
        for dx, ch in enumerate(row):
            if ch == "#":
                c.set(4 + dx, 4 + dy, GOLD2 if (dx + dy) % 3 else GOLD3)
            elif ch == "s":
                c.set(4 + dx, 4 + dy, RED2)
    save(c, "block/guild_counter_front")


def counter_side():
    c = Canvas(16, 16)
    planks(c, 0, 0, 15, 15, WOOD2, 22, vertical=False, seam=4)
    for x in range(16):
        c.set(x, 15, WOOD0)
    save(c, "block/guild_counter_side")


def counter_top():
    c = Canvas(16, 16)
    rnd = random.Random(23)
    for y in range(16):
        for x in range(16):
            col = jitter(WOOD4, rnd, 0.05)
            if (x + y * 3) % 11 == 0:
                col = darken(col, 0.12)
            c.set(x, y, col)
    for i in range(16):
        c.set(i, 0, WOOD5); c.set(0, i, WOOD5)
        c.set(i, 15, WOOD3); c.set(15, i, WOOD3)
    for x in range(16):
        c.set(x, 7, darken(WOOD4, 0.25))
    save(c, "block/guild_counter_top")


def counter_book():
    c = Canvas(16, 16)
    leather = hex_rgba("7A2E22")
    rnd = random.Random(24)
    for y in range(16):
        for x in range(16):
            c.set(x, y, jitter(leather, rnd, 0.06))
    for i in range(16):
        c.set(i, 0, darken(leather, 0.35)); c.set(i, 15, darken(leather, 0.35))
    for (x, y) in ((1, 1), (2, 1), (1, 2), (14, 1), (13, 1), (14, 2), (1, 14), (2, 14), (1, 13), (14, 14), (13, 14), (14, 13)):
        c.set(x, y, GOLD2)
    for y in range(5, 11):
        c.set(8, y, GOLD2)
    for x in range(5, 12):
        c.set(x, 8, GOLD2)
    # faixa de "paginas" (usada nas laterais do livro)
    for y in range(12, 16):
        for x in range(0, 16):
            c.set(x, y, PAPER if (x % 2 or y == 12) else PAPER2)
    save(c, "block/guild_counter_book")


def faces_all(tex, uv=None):
    f = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        f[d] = {"texture": tex}
        if uv:
            f[d]["uv"] = uv
    return f


def block_models():
    write_json(os.path.join(A, "models/block/quest_board.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/quest_board_side",
            "front": f"{NS}:block/quest_board_front",
            "side": f"{NS}:block/quest_board_side",
            "paper": f"{NS}:block/quest_board_paper",
        },
        "elements": [
            # postes
            {"from": [1, 0, 6.5], "to": [3, 16, 9.5], "faces": faces_all("#side")},
            {"from": [13, 0, 6.5], "to": [15, 16, 9.5], "faces": faces_all("#side")},
            # painel (frente = norte)
            {"from": [0, 4, 7], "to": [16, 16, 9], "faces": {
                "north": {"texture": "#front", "uv": [0, 0, 16, 12]},
                "south": {"texture": "#side", "uv": [0, 0, 16, 12]},
                "east": {"texture": "#side", "uv": [7, 0, 9, 12]},
                "west": {"texture": "#side", "uv": [7, 0, 9, 12]},
                "up": {"texture": "#side", "uv": [0, 7, 16, 9]},
                "down": {"texture": "#side", "uv": [0, 7, 16, 9]}}},
            # telhadinho
            {"from": [-0.5, 15.5, 6], "to": [16.5, 16.5, 10], "faces": faces_all("#side", [0, 0, 16, 4])},
            # cartazes em relevo
            {"from": [2.5, 9, 6.6], "to": [6.5, 14, 7], "faces": {
                "north": {"texture": "#paper", "uv": [0, 0, 8, 10]},
                "east": {"texture": "#paper", "uv": [0, 0, 1, 10]},
                "west": {"texture": "#paper", "uv": [0, 0, 1, 10]},
                "up": {"texture": "#paper", "uv": [0, 0, 8, 1]},
                "down": {"texture": "#paper", "uv": [0, 0, 8, 1]}}},
            {"from": [10, 5.5, 6.6], "to": [14, 10, 7], "faces": {
                "north": {"texture": "#paper", "uv": [8, 6, 16, 16]},
                "east": {"texture": "#paper", "uv": [8, 6, 9, 16]},
                "west": {"texture": "#paper", "uv": [8, 6, 9, 16]},
                "up": {"texture": "#paper", "uv": [8, 6, 16, 7]},
                "down": {"texture": "#paper", "uv": [8, 6, 16, 7]}}},
        ],
    })
    write_json(os.path.join(A, "models/block/guild_counter.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/guild_counter_side",
            "front": f"{NS}:block/guild_counter_front",
            "side": f"{NS}:block/guild_counter_side",
            "top": f"{NS}:block/guild_counter_top",
            "book": f"{NS}:block/guild_counter_book",
        },
        "elements": [
            {"from": [1, 0, 1], "to": [15, 13, 15], "faces": {
                "north": {"texture": "#front", "uv": [1, 2, 15, 15]},
                "south": {"texture": "#side", "uv": [1, 3, 15, 16]},
                "east": {"texture": "#side", "uv": [1, 3, 15, 16]},
                "west": {"texture": "#side", "uv": [1, 3, 15, 16]},
                "down": {"texture": "#side", "cullface": "down"}}},
            {"from": [0, 13, 0], "to": [16, 16, 16], "faces": {
                "north": {"texture": "#side", "uv": [0, 0, 16, 3]},
                "south": {"texture": "#side", "uv": [0, 0, 16, 3]},
                "east": {"texture": "#side", "uv": [0, 0, 16, 3]},
                "west": {"texture": "#side", "uv": [0, 0, 16, 3]},
                "up": {"texture": "#top", "cullface": "up"},
                "down": {"texture": "#top"}}},
            # livro-registro aberto sobre o balcão
            {"from": [8, 16, 3], "to": [14, 17, 8], "rotation": {"angle": 22.5, "axis": "y", "origin": [11, 16, 5.5]}, "faces": {
                "north": {"texture": "#book", "uv": [0, 12, 6, 13]},
                "south": {"texture": "#book", "uv": [0, 12, 6, 13]},
                "east": {"texture": "#book", "uv": [0, 12, 5, 13]},
                "west": {"texture": "#book", "uv": [0, 12, 5, 13]},
                "up": {"texture": "#book", "uv": [2, 2, 14, 12]},
                "down": {"texture": "#book", "uv": [2, 2, 14, 12]}}},
            # tinteiro
            {"from": [3, 16, 10], "to": [5, 17.5, 12], "faces": faces_all("#book", [5, 0, 7, 2])},
        ],
    })
    for bid in ("quest_board", "guild_counter"):
        write_json(os.path.join(A, f"blockstates/{bid}.json"), {"variants": {
            "facing=north": {"model": f"{NS}:block/{bid}"},
            "facing=east": {"model": f"{NS}:block/{bid}", "y": 90},
            "facing=south": {"model": f"{NS}:block/{bid}", "y": 180},
            "facing=west": {"model": f"{NS}:block/{bid}", "y": 270},
        }})
        write_json(os.path.join(A, f"models/item/{bid}.json"), {"parent": f"{NS}:block/{bid}"})


def item_models():
    overrides = []
    for i, r in enumerate(RANKS[1:], start=2):
        overrides.append({"predicate": {f"{NS}:rank": round(i / 10.0 - 0.05, 2)}, "model": f"{NS}:item/adventurer_card_{r}"})
    write_json(os.path.join(A, "models/item/adventurer_card.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{NS}:item/adventurer_card_f"},
        "overrides": overrides,
    })
    for r in RANKS[1:]:
        write_json(os.path.join(A, f"models/item/adventurer_card_{r}.json"), {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/adventurer_card_{r}"}})
    write_json(os.path.join(A, "models/item/guild_coin.json"), {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/guild_coin"}})
    for egg in ("guild_receptionist_spawn_egg", "guild_master_spawn_egg", "adventurer_spawn_egg"):
        write_json(os.path.join(A, f"models/item/{egg}.json"), {"parent": "minecraft:item/template_spawn_egg"})


# ------------------------------------------------------------------------------------------------
# GeckoLib: construtor de modelo com UV automatico (box UV) + pintura por peca
# ------------------------------------------------------------------------------------------------
def box_faces(u, v, w, h, d):
    """Retangulos (x0, y0, x1, y1) inclusivos das 6 faces de um cubo em box-UV (layout de skin)."""
    return {
        "top": (u + d, v, u + d + w - 1, v + d - 1),
        "bottom": (u + d + w, v, u + d + 2 * w - 1, v + d - 1),
        "right": (u, v + d, u + d - 1, v + d + h - 1),
        "front": (u + d, v + d, u + d + w - 1, v + d + h - 1),
        "left": (u + d + w, v + d, u + 2 * d + w - 1, v + d + h - 1),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w - 1, v + d + h - 1),
    }


def px(rect):
    x0, y0, x1, y1 = rect
    fw, fh = x1 - x0 + 1, y1 - y0 + 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            yield x, y, x - x0, y - y0, fw, fh


class Geo:
    def __init__(self, ident, tw=128, th=128):
        self.ident, self.tw, self.th = ident, tw, th
        self.bones, self.cubes = [], []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)

    def cube(self, bone, origin, size, key, inflate=0.0):
        self.cubes.append({"bone": bone, "origin": list(origin), "size": list(size), "key": key, "inflate": inflate})

    def pack(self):
        order = sorted(range(len(self.cubes)), key=lambda i: -(self.cubes[i]["size"][2] + self.cubes[i]["size"][1]))
        x = y = row = 0
        for i in order:
            w, h, d = self.cubes[i]["size"]
            bw, bh = 2 * d + 2 * w, d + h
            if x + bw > self.tw:
                x, y, row = 0, y + row, 0
            self.cubes[i]["uv"] = (x, y)
            x += bw
            row = max(row, bh)
        assert y + row <= self.th, f"{self.ident}: textura pequena demais ({y + row} > {self.th})"

    def to_json(self, bounds=(3, 3.5)):
        bones = []
        for b in self.bones:
            out = dict(b)
            cubes = []
            for c in self.cubes:
                if c["bone"] != b["name"]:
                    continue
                e = {"origin": c["origin"], "size": c["size"], "uv": list(c["uv"])}
                if c["inflate"]:
                    e["inflate"] = c["inflate"]
                cubes.append(e)
            if cubes:
                out["cubes"] = cubes
            bones.append(out)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.ident}", "texture_width": self.tw, "texture_height": self.th,
                            "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1],
                            "visible_bounds_offset": [0, bounds[1] / 2 - 0.5, 0]},
            "bones": bones}]}

    def paint(self, painters, seed):
        c = Canvas(self.tw, self.th)
        rnd = random.Random(seed)
        for cube in self.cubes:
            w, h, d = cube["size"]
            u, v = cube["uv"]
            p = painters.get(cube["key"])
            if p:
                p(c, box_faces(u, v, w, h, d), rnd)
        return c


# ---------------------------------------------------------------- pintores
WHITE = hex_rgba("F4F4F4")


def solid(color, amount=0.05, shade=True):
    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = color
                if shade and name in ("front", "back", "left", "right") and fh > 3:
                    col = lighten(color, 0.08) if fy == 0 else (darken(color, 0.12) if fy == fh - 1 else color)
                if name == "bottom":
                    col = darken(color, 0.2)
                c.set(x, y, jitter(col, rnd, amount))
    return p


def head(skin, hair, style="short", eye="3A6BC9", lashes=False, blush=False, eyepatch=False, scar=False, stubble=False,
         brows=None, mouth="B5655A"):
    skin, hair, eye, mouth = hex_rgba(skin), hex_rgba(hair), hex_rgba(eye), hex_rgba(mouth)
    brow = hex_rgba(brows) if brows else darken(hair, 0.3)
    skin_d = darken(skin, 0.14)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = jitter(skin, rnd, 0.03)
                hairy = False
                if name == "top":
                    hairy = style != "bald"
                elif name == "back":
                    hairy = style != "bald" or 3 <= fy <= 5
                    if style == "short" and fy == fh - 1:
                        hairy = False
                elif name in ("right", "left"):
                    back = fx <= 1 if name == "right" else fx >= fw - 2
                    if style == "bald":
                        hairy = (back and 3 <= fy <= 5) or (fy in (3, 4) and (fx <= 3 if name == "right" else fx >= fw - 4))
                    else:
                        hairy = fy <= 1 or (back and fy <= (fh - 1 if style in ("long", "ponytail") else 5))
                        if style in ("long", "ponytail") and fy <= 2:
                            hairy = True
                    if not hairy and fy == 4 and fx in (3, 4):
                        col = skin_d
                elif name == "front":
                    if style != "bald":
                        hairy = fy == 0 or (fy == 1 and fx in (0, 1, 2, 5, 6, 7))
                        if style in ("long", "ponytail") and fx in (0, 7) and fy <= 4:
                            hairy = True
                    if fy == 3 and fx in (1, 2, 5, 6):
                        col = brow
                    if fy == 4:
                        if fx in (1, 6):
                            col = WHITE
                        elif fx in (2, 5):
                            col = eye
                    if lashes and fy == 3 and fx in (1, 6):
                        col = hex_rgba("2A1A14")
                    if fy == 5 and fx in (3, 4):
                        col = skin_d
                    if fy == 6 and fx in (3, 4):
                        col = mouth
                    if blush and fy == 5 and fx in (1, 6):
                        col = mix(skin, hex_rgba("E88A8A"), 0.5)
                    if stubble and fy >= 6 and fx not in (3, 4):
                        col = mix(skin, hair, 0.35)
                    if scar and fx == 2 and 2 <= fy <= 5 and fy != 4:
                        col = hex_rgba("B86A5E")
                    if eyepatch and ((fy == 4 and fx in (5, 6)) or (fy == 3 and fx == 7) or (fy == 2 and fx in (0, 1))
                                     or (fy == 3 and fx in (2, 3, 4))):
                        col = hex_rgba("16110E")
                if hairy:
                    col = jitter(hair, rnd, 0.08)
                    if rnd.random() < 0.12:
                        col = darken(col, 0.18)
                c.set(x, y, col)
    return p


def hair_overlay(hair, style="short", hat=False):
    hair = hex_rgba(hair)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                on = False
                if name == "top":
                    on = style != "bald" and not hat
                elif name == "back":
                    on = style in ("long", "ponytail") or (style == "short" and fy <= 4)
                elif name in ("right", "left"):
                    back = fx <= 2 if name == "right" else fx >= fw - 3
                    on = style != "bald" and (fy == 0 or (back and fy <= (fh - 2 if style in ("long", "ponytail") else 3)))
                elif name == "front":
                    on = style != "bald" and fy == 0 and not hat
                    if style in ("long", "ponytail") and fx in (0, 7) and fy <= 3:
                        on = True
                if on:
                    col = jitter(lighten(hair, 0.06), rnd, 0.1)
                    if rnd.random() < 0.15:
                        col = darken(col, 0.2)
                    c.set(x, y, col)
    return p


def body(shirt, vest=None, belt=None, buckle="E8B84A", collar=None, tie=None, buttons=None, armor=None, robe_trim=None,
         emblem=False, straps=None, sash=None):
    shirt = hex_rgba(shirt)
    vest = hex_rgba(vest) if vest else None
    belt = hex_rgba(belt) if belt else None
    buckle = hex_rgba(buckle)
    collar = hex_rgba(collar) if collar else None
    tie = hex_rgba(tie) if tie else None
    buttons = hex_rgba(buttons) if buttons else None
    armor = hex_rgba(armor) if armor else None
    robe_trim = hex_rgba(robe_trim) if robe_trim else None
    straps = hex_rgba(straps) if straps else None
    sash = hex_rgba(sash) if sash else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = shirt
                mid = (fw - 1) / 2
                if vest is not None:
                    if name in ("back", "left", "right", "top") or (name == "front" and (fx <= 1 or fx >= fw - 2)):
                        col = vest
                if collar is not None and name == "front" and fy <= 2 and abs(fx - mid) <= 1.6 - fy * 0.5:
                    col = collar
                if tie is not None and name == "front" and abs(fx - mid) < 1 and 1 <= fy <= 6:
                    col = tie if fy > 1 else darken(tie, 0.2)
                if buttons is not None and name == "front" and fx in (2, fw - 3) and fy % 3 == 1 and 2 < fy < fh - 4:
                    col = buttons
                if armor is not None and name in ("front", "back") and 1 <= fy <= fh - 5:
                    col = lighten(armor, 0.18) if fy == 1 else (darken(armor, 0.2) if fx in (0, fw - 1) else armor)
                    if name == "front" and fy == 4 and abs(fx - mid) < 1:
                        col = darken(armor, 0.3)
                    if (fx, fy) in ((1, 2), (fw - 2, 2)):
                        col = lighten(armor, 0.4)
                if armor is not None and name in ("left", "right") and 1 <= fy <= fh - 5:
                    col = darken(armor, 0.1)
                if straps is not None and name in ("front", "back") and abs(fx - (fy * 0.6) % fw) < 0.8 and fy < fh - 4:
                    col = straps
                if robe_trim is not None and name == "front" and abs(fx - mid) < 1:
                    col = robe_trim
                if sash is not None and fy in (fh - 4, fh - 3):
                    col = sash
                if belt is not None and fy in (fh - 4, fh - 3):
                    col = belt
                    if name == "front" and abs(fx - mid) < 1:
                        col = buckle
                if emblem and name == "front" and fx in (1, 2) and fy in (2, 3):
                    col = hex_rgba("E8B84A") if (fx + fy) % 2 else hex_rgba("FFE38A")
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def arm(sleeve, hand, cuff=None, sleeve_rows=8, glove=None, bracer=None):
    sleeve, hand = hex_rgba(sleeve), hex_rgba(hand)
    cuff = hex_rgba(cuff) if cuff else None
    glove = hex_rgba(glove) if glove else None
    bracer = hex_rgba(bracer) if bracer else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "top":
                    col = sleeve
                elif name == "bottom":
                    col = glove if glove is not None else hand
                elif fy < sleeve_rows:
                    col = sleeve if fy > 0 else lighten(sleeve, 0.1)
                elif fy == sleeve_rows and cuff is not None:
                    col = cuff
                else:
                    col = glove if glove is not None else hand
                if bracer is not None and name not in ("top", "bottom") and sleeve_rows - 2 <= fy <= fh - 3:
                    col = bracer if fy != sleeve_rows - 2 else lighten(bracer, 0.2)
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def leg(pants, boot, boot_rows=4, cuff=None, patch=None):
    pants, boot = hex_rgba(pants), hex_rgba(boot)
    cuff = hex_rgba(cuff) if cuff else lighten(boot, 0.15)
    patch = hex_rgba(patch) if patch else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "top":
                    col = pants
                elif name == "bottom":
                    col = darken(boot, 0.3)
                elif fy >= fh - boot_rows:
                    col = cuff if fy == fh - boot_rows else boot
                    if fy == fh - 1:
                        col = darken(boot, 0.25)
                else:
                    col = pants
                    if patch is not None and name == "front" and fy in (fh - boot_rows - 3, fh - boot_rows - 2) and 1 <= fx <= fw - 2:
                        col = patch
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def skirt(color, hem):
    color, hem = hex_rgba(color), hex_rgba(hem)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = color
                if name in ("front", "back", "left", "right"):
                    if fx % 2 == 1:
                        col = darken(color, 0.18)
                    if fy == fh - 1:
                        col = hem
                    if fy == 0:
                        col = darken(color, 0.3)
                if name == "bottom":
                    col = darken(color, 0.45)
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def cape(color, lining, trim=None, emblem=None, ragged=False):
    color, lining = hex_rgba(color), hex_rgba(lining)
    trim = hex_rgba(trim) if trim else None
    emblem = hex_rgba(emblem) if emblem else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "back":
                    col = lighten(color, 0.12 * (1 - fy / fh))
                    if trim is not None and (fx in (0, fw - 1) or fy == fh - 1):
                        col = trim
                    if emblem is not None and 2 <= fy <= 7 and abs(fx - (fw - 1) / 2) <= (2.5 if fy < 5 else 7 - fy):
                        col = emblem if (fx + fy) % 3 else lighten(emblem, 0.3)
                    if fx % 3 == 1 and fy > 4 and emblem is None and col != trim:
                        col = darken(col, 0.1)
                elif name == "front":
                    col = lining
                else:
                    col = trim if trim is not None else darken(color, 0.2)
                if ragged and fy == fh - 1 and name in ("back", "front") and rnd.random() < 0.4:
                    c.set(x, y, T)
                    continue
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def hood(color):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "front":
                    if not (fy == 0 or fx in (0, fw - 1) or (fy == 1 and fx in (1, fw - 2))):
                        continue
                    col = darken(color, 0.15)
                elif name == "bottom":
                    continue
                else:
                    col = color
                    if name in ("left", "right") and fy == fh - 1:
                        col = darken(color, 0.2)
                    if name == "back" and fx == (fw - 1) // 2 and fy < 5:
                        col = darken(color, 0.25)
                c.set(x, y, jitter(col, rnd, 0.06))
    return p


def band(color, knot=True):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            if name in ("top", "bottom"):
                continue
            for x, y, fx, fy, fw, fh in px(r):
                col = color
                if knot and name == "back" and fx in (3, 4):
                    col = darken(color, 0.3)
                c.set(x, y, jitter(col, rnd, 0.06))
    return p


def scarf(color):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            if name == "top":
                continue
            for x, y, fx, fy, fw, fh in px(r):
                col = color if (fx + fy) % 4 else darken(color, 0.25)
                if fy == 0:
                    col = lighten(color, 0.12)
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def beard(color):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "front" and fy == 0 and 2 <= fx <= fw - 3:
                    # boca aparece entre o bigode
                    col = hex_rgba("3A2018") if fx in (3, 4) else jitter(lighten(color, 0.1), rnd, 0.08)
                elif fy == fh - 1 and name != "top" and fx % 2 == 0:
                    continue
                else:
                    col = jitter(color, rnd, 0.1)
                    if (fx + fy * 2) % 5 == 0:
                        col = darken(col, 0.18)
                c.set(x, y, col)
    return p


def fur(color):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = jitter(color, rnd, 0.16)
                if rnd.random() < 0.2:
                    col = lighten(col, 0.2)
                c.set(x, y, col)
    return p


def metal(color, rivets=True):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = lighten(color, 0.25) if (name == "top" or fy == 0) else color
                if name == "bottom":
                    col = darken(color, 0.35)
                if rivets and name in ("front", "back", "left", "right") and fy == 1 and fx in (1, fw - 2):
                    col = hex_rgba("E8E8E8")
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def wood_rod(color):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = jitter(color, rnd, 0.08)
                if fy % 5 == 0:
                    col = darken(col, 0.2)
                if fy % 9 == 3:
                    col = lighten(col, 0.15)
                c.set(x, y, col)
    return p


def crystal(color):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = lighten(color, 0.45) if (fx == 0 and fy == 0) or name == "top" else color
                if fx == fw - 1 or fy == fh - 1:
                    col = darken(color, 0.25)
                c.set(x, y, col)
    return p


def quiver(color, strap):
    color, strap = hex_rgba(color), hex_rgba(strap)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = color
                if fy in (1, fh - 2):
                    col = strap
                if name == "top":
                    col = hex_rgba("1A120C")
                c.set(x, y, jitter(col, rnd, 0.06))
    return p


def feathers():
    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = WHITE if (fx + fy) % 2 else hex_rgba("C9433A")
                if name == "bottom":
                    col = hex_rgba("6B4A2E")
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def mage_hat(color, band_color=None, stars=True):
    color = hex_rgba(color)
    band_color = hex_rgba(band_color) if band_color else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = lighten(color, 0.1) if name == "top" else color
                if name == "bottom":
                    col = darken(color, 0.35)
                if band_color is not None and name not in ("top", "bottom") and fy == fh - 1:
                    col = band_color
                if stars and name != "bottom" and rnd.random() < 0.05:
                    col = hex_rgba("FFE38A")
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


# ---------------------------------------------------------------- humanoide base
def humanoid(g, arm_w=4, item_bones=True):
    g.bone("root")
    g.bone("body", "root", (0, 24, 0))
    g.cube("body", (-4, 12, -2), (8, 12, 4), "body")
    g.bone("head", "body", (0, 24, 0))
    g.cube("head", (-4, 24, -4), (8, 8, 8), "head")
    g.cube("head", (-4, 24, -4), (8, 8, 8), "hair", 0.5)
    ax = 4 + arm_w
    g.bone("right_arm", "body", (-5, 22, 0))
    g.cube("right_arm", (-ax, 12, -2), (arm_w, 12, 4), "arm_r")
    g.bone("left_arm", "body", (5, 22, 0))
    g.cube("left_arm", (4, 12, -2), (arm_w, 12, 4), "arm_l")
    if item_bones:
        g.bone("right_item", "right_arm", (-4 - arm_w / 2.0, 12.5, -1))
        g.bone("left_item", "left_arm", (4 + arm_w / 2.0, 12.5, -1))
    g.bone("right_leg", "root", (-1.9, 12, 0))
    g.cube("right_leg", (-3.9, 0, -2), (4, 12, 4), "leg")
    g.bone("left_leg", "root", (1.9, 12, 0))
    g.cube("left_leg", (-0.1, 0, -2), (4, 12, 4), "leg")


def receptionist_geo():
    g = Geo("guild_receptionist")
    humanoid(g, arm_w=3)
    g.bone("ponytail", "head", (0, 29, 4), (22, 0, 0))
    g.cube("ponytail", (-1.5, 21, 3.5), (3, 8, 2), "ponytail")
    g.bone("ribbon", "head", (0, 30, 4.5))
    g.cube("ribbon", (-2.5, 29, 4), (5, 2, 1), "ribbon")
    g.bone("skirt", "body", (0, 12, 0))
    g.cube("skirt", (-4.5, 6, -2.5), (9, 7, 5), "skirt")
    g.pack()
    return g


def receptionist_painters():
    skin, hair = "F2C9A8", "7A3E26"
    return {
        "head": head(skin, hair, "ponytail", eye="3A8A5A", lashes=True, blush=True, mouth="C46A62"),
        "hair": hair_overlay(hair, "ponytail"),
        "body": body("EDEFF5", vest="2B3A67", tie="B0302E", emblem=True, buttons="E8B84A"),
        "arm_r": arm("2B3A67", skin, cuff="EDEFF5", sleeve_rows=8),
        "arm_l": arm("2B3A67", skin, cuff="EDEFF5", sleeve_rows=8),
        "leg": leg("2A2A3A", "1A1A22", boot_rows=2),
        "skirt": skirt("26335C", "E8B84A"),
        "ponytail": solid(hex_rgba(hair), 0.1),
        "ribbon": solid(hex_rgba("B0302E"), 0.05),
    }


def master_geo():
    g = Geo("guild_master")
    g.bone("root")
    g.bone("body", "root", (0, 26, 0))
    g.cube("body", (-5, 13, -3), (10, 13, 6), "body")
    g.cube("body", (-6, 24, -3.5), (12, 3, 7), "fur")
    g.bone("cape", "body", (0, 26, 3))
    g.cube("cape", (-5, 4, 3), (10, 21, 1), "cape")
    g.bone("head", "body", (0, 26, 0))
    g.cube("head", (-4, 26, -4), (8, 8, 8), "head")
    g.bone("beard", "head", (0, 26, -4))
    g.cube("beard", (-3.5, 22, -5), (7, 6, 2), "beard")
    g.bone("right_arm", "body", (-6.5, 24, 0))
    g.cube("right_arm", (-10, 13, -2.5), (5, 13, 5), "arm_r")
    g.bone("right_item", "right_arm", (-7.5, 13.5, -1.5))
    g.bone("left_arm", "body", (6.5, 24, 0))
    g.cube("left_arm", (5, 13, -2.5), (5, 13, 5), "arm_l")
    g.bone("left_item", "left_arm", (7.5, 13.5, -1.5))
    g.bone("right_leg", "root", (-2.5, 13, 0))
    g.cube("right_leg", (-5, 0, -2.5), (5, 13, 5), "leg")
    g.bone("left_leg", "root", (2.5, 13, 0))
    g.cube("left_leg", (0, 0, -2.5), (5, 13, 5), "leg")
    g.pack()
    return g


def master_painters():
    skin = "C98E64"
    return {
        "head": head(skin, "8A8580", "bald", eye="2E4A6B", eyepatch=True, scar=True, brows="6E655C", mouth="8A4A3A"),
        "beard": beard("8F857A"),
        "body": body("6B4A32", vest="3E2C20", belt="1E1814", buckle="E8B84A", collar="8A6A4A"),
        "fur": fur("A88A62"),
        "cape": cape("7A1F1F", "3A1212", trim="C9A23A", emblem="E8B84A"),
        "arm_r": arm("3E2C20", skin, sleeve_rows=7, bracer="5A3E2A", glove="2A1E16"),
        "arm_l": arm("3E2C20", skin, sleeve_rows=7, bracer="5A3E2A", glove="2A1E16"),
        "leg": leg("3A2C22", "241A14", boot_rows=5, patch="4A3A2E"),
    }


def adventurer_geo():
    g = Geo("adventurer")
    humanoid(g, arm_w=4)
    g.bone("hood", "head", (0, 24, 0))
    g.cube("hood", (-4, 24, -4), (8, 8, 8), "hood", 0.75)
    g.bone("headband", "head", (0, 29, 0))
    g.cube("headband", (-4, 29, -4), (8, 1, 8), "headband", 0.6)
    g.bone("scarf", "head", (0, 24, 0))
    g.cube("scarf", (-4, 23, -4), (8, 3, 8), "scarf", 0.6)
    g.bone("hat_mage", "head", (0, 32, 0))
    g.cube("hat_mage", (-6, 32, -6), (12, 1, 12), "hat_brim")
    g.cube("hat_mage", (-4, 33, -4), (8, 3, 8), "hat1")
    g.bone("hat_tip", "hat_mage", (0, 36, 0), (-12, 0, 6))
    g.cube("hat_tip", (-3, 36, -3), (6, 3, 6), "hat2")
    g.cube("hat_tip", (-2, 39, -2), (4, 3, 4), "hat3")
    g.cube("hat_tip", (-1, 42, -1), (2, 2, 2), "hat4")
    g.bone("cape", "body", (0, 24, 2))
    g.cube("cape", (-4, 5, 2), (8, 19, 1), "cape")
    g.bone("quiver", "body", (0, 20, 2.5), (0, 0, -25))
    g.cube("quiver", (-1.5, 13, 2.5), (3, 10, 3), "quiver")
    g.cube("quiver", (-1, 23, 3), (2, 3, 2), "arrows")
    g.bone("pauldron_r", "right_arm", (-5, 22, 0))
    g.cube("pauldron_r", (-9, 21, -3), (6, 4, 6), "pauldron")
    g.bone("pauldron_l", "left_arm", (5, 22, 0))
    g.cube("pauldron_l", (3, 21, -3), (6, 4, 6), "pauldron")
    g.bone("staff", "right_arm", (-6, 12, -2.5))
    g.cube("staff", (-6.5, 1, -3), (1, 28, 1), "staff")
    g.cube("staff", (-7.5, 29, -4), (3, 3, 3), "crystal")
    # v0.8 — clérigo: túnica longa, símbolo sagrado no peito e auréola flutuante (gira no GuildNpcModel).
    g.bone("robe", "body", (0, 12, 0))
    g.cube("robe", (-4.5, 2, -2.5), (9, 11, 5), "robe")
    g.bone("holy_symbol", "body", (0, 24, 0))
    g.cube("holy_symbol", (-1.5, 16, -3), (3, 4, 1), "holy_symbol")
    g.bone("halo", "head", (0, 35.5, 0))
    g.cube("halo", (-4, 35, -4), (8, 1, 1), "halo")
    g.cube("halo", (-4, 35, 3), (8, 1, 1), "halo")
    g.cube("halo", (-4, 35, -3), (1, 1, 6), "halo")
    g.cube("halo", (3, 35, -3), (1, 1, 6), "halo")
    g.pack()
    return g


def adventurer_painters(variant):
    if variant == "warrior":
        skin, hair = "E0AC85", "5A3A22"
        return {
            "head": head(skin, hair, "short", eye="4A6B3A", stubble=True, scar=True),
            "hair": hair_overlay(hair, "short"),
            "body": body("8E2B2B", armor="9AA4B0", belt="3A2A1E"),
            "arm_r": arm("7D8590", skin, sleeve_rows=7, glove="5A3E2A"),
            "arm_l": arm("7D8590", skin, sleeve_rows=7, glove="5A3E2A"),
            "leg": leg("4A3A2E", "6E7782", boot_rows=5),
            "cape": cape("9E2B25", "4A1512", trim="C9A23A", ragged=True),
            "headband": band("B0302E"),
            "pauldron": metal("8A939E"),
        }
    if variant == "archer":
        skin, hair = "F0C8A0", "C9A15A"
        return {
            "head": head(skin, hair, "long", eye="3A7A3A", lashes=True),
            "hair": hair_overlay(hair, "long"),
            "body": body("3E6B35", vest="6B4A2E", belt="3A2A1E", straps="5A3E2A"),
            "arm_r": arm("3E6B35", skin, sleeve_rows=6, bracer="6B4A2E"),
            "arm_l": arm("3E6B35", skin, sleeve_rows=6, bracer="6B4A2E"),
            "leg": leg("5A4632", "3E2E22", boot_rows=5),
            "cape": cape("355E30", "1E3A1A", ragged=True),
            "hood": hood("2F5A2A"),
            "quiver": quiver("6B4A2E", "3A2A1E"),
            "arrows": feathers(),
        }
    if variant == "mage":
        skin, hair = "F2CFB0", "D9D9E6"
        return {
            "head": head(skin, hair, "short", eye="6A4FD0"),
            "hair": hair_overlay(hair, "short", hat=True),
            "body": body("2E4A8A", robe_trim="E8B84A", sash="6A3FD0", collar="1E2E5A", emblem=True),
            "arm_r": arm("2E4A8A", skin, cuff="E8B84A", sleeve_rows=9),
            "arm_l": arm("2E4A8A", skin, cuff="E8B84A", sleeve_rows=9),
            "leg": leg("26407A", "2A1E3A", boot_rows=2),
            "cape": cape("3A2560", "1E1236", trim="E8B84A"),
            "hat_brim": mage_hat("4B2A7A", stars=False),
            "hat1": mage_hat("4B2A7A", band_color="E8B84A"),
            "hat2": mage_hat("4B2A7A"),
            "hat3": mage_hat("553088"),
            "hat4": mage_hat("FFE38A", stars=False),
            "staff": wood_rod("6B4A2E"),
            "crystal": crystal("9B6BFF"),
        }
    if variant == "cleric":
        skin, hair = "F0D2B4", "EADBB0"
        return {
            "head": head(skin, hair, "long", eye="C9A23A", lashes=True, blush=True),
            "hair": hair_overlay(hair, "long"),
            "body": body("EDE6D6", robe_trim="E8B84A", collar="C9A55A", sash="3A6BC9", emblem=True),
            "arm_r": arm("EDE6D6", skin, cuff="E8B84A", sleeve_rows=9),
            "arm_l": arm("EDE6D6", skin, cuff="E8B84A", sleeve_rows=9),
            "leg": leg("D8CFBC", "8A6A3A", boot_rows=2),
            "cape": cape("F6F0E0", "C9B98A", trim="E8B84A", emblem="E8B84A"),
            "hood": hood("F2ECDC"),
            "robe": skirt("EAE2CF", "E8B84A"),
            "holy_symbol": crystal("FFD86B"),
            "halo": crystal("FFF3B0"),
            "staff": wood_rod("E8DCC0"),
            "crystal": crystal("FFE38A"),
        }
    skin, hair = "D9A07A", "1E1A1A"
    return {
        "head": head(skin, hair, "short", eye="8A2B2B", brows="141010"),
        "hair": hair_overlay(hair, "short"),
        "body": body("2A2626", vest="1E1A1A", belt="4A4444", buckle="9AA4B0", straps="4A4444"),
        "arm_r": arm("2A2626", skin, sleeve_rows=7, glove="141212"),
        "arm_l": arm("2A2626", skin, sleeve_rows=7, glove="141212"),
        "leg": leg("302C2C", "141212", boot_rows=5),
        "hood": hood("262222"),
        "scarf": scarf("3A3434"),
    }


# ---------------------------------------------------------------- animacoes
def kf(*pairs):
    out = {}
    for t, v in pairs:
        out["0.0" if t == 0 else f"{t:.2f}".rstrip("0").rstrip(".")] = v
    return out


def common_animations(prefix, master=False):
    s = 0.75 if master else 1.0
    return {
        f"{prefix}.idle": {"loop": True, "animation_length": 3.0, "bones": {
            "body": {"rotation": kf((0, [0, 0, 0]), (1.5, [1.5, 0, 0]), (3.0, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (1.5, [-3, 0, 0]), (3.0, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, 3]), (1.5, [-3, 0, 6]), (3.0, [0, 0, 3]))},
            "left_arm": {"rotation": kf((0, [0, 0, -3]), (1.5, [-3, 0, -6]), (3.0, [0, 0, -3]))},
        }},
        f"{prefix}.walk": {"loop": True, "animation_length": 1.0, "bones": {
            "right_leg": {"rotation": kf((0, [30 * s, 0, 0]), (0.5, [-30 * s, 0, 0]), (1.0, [30 * s, 0, 0]))},
            "left_leg": {"rotation": kf((0, [-30 * s, 0, 0]), (0.5, [30 * s, 0, 0]), (1.0, [-30 * s, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-25 * s, 0, 3]), (0.5, [25 * s, 0, 3]), (1.0, [-25 * s, 0, 3]))},
            "left_arm": {"rotation": kf((0, [25 * s, 0, -3]), (0.5, [-25 * s, 0, -3]), (1.0, [25 * s, 0, -3]))},
            "body": {"rotation": kf((0, [2, 3, 0]), (0.5, [2, -3, 0]), (1.0, [2, 3, 0]))},
        }},
        f"{prefix}.talk": {"loop": True, "animation_length": 1.6, "bones": {
            "right_arm": {"rotation": kf((0, [-20, 0, 8]), (0.4, [-45, 8, 14]), (0.8, [-28, -6, 8]), (1.2, [-40, 4, 12]),
                                         (1.6, [-20, 0, 8]))},
            "left_arm": {"rotation": kf((0, [0, 0, -4]), (0.8, [-10, 0, -8]), (1.6, [0, 0, -4]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.4, [5, 6, 0]), (0.8, [-2, -5, 0]), (1.2, [4, 3, 0]), (1.6, [0, 0, 0]))},
        }},
        f"{prefix}.wave": {"loop": False, "animation_length": 1.0, "bones": {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.15, [-160, 0, 18]), (0.35, [-160, 0, 40]), (0.55, [-160, 0, 15]),
                                         (0.75, [-160, 0, 38]), (1.0, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [-6, 0, 4]), (1.0, [0, 0, 0]))},
        }},
    }


def receptionist_animations():
    p = "animation.guild_receptionist"
    a = common_animations(p)
    a[f"{p}.idle"]["bones"]["ponytail"] = {"rotation": kf((0, [0, 0, 0]), (1.5, [4, 0, 3]), (3.0, [0, 0, 0]))}
    a[f"{p}.walk"]["bones"]["ponytail"] = {"rotation": kf((0, [6, 0, 0]), (0.5, [14, 0, 0]), (1.0, [6, 0, 0]))}
    a[f"{p}.bow"] = {"loop": False, "animation_length": 0.9, "bones": {
        "body": {"rotation": kf((0, [0, 0, 0]), (0.25, [28, 0, 0]), (0.55, [28, 0, 0]), (0.9, [0, 0, 0]))},
        "head": {"rotation": kf((0, [0, 0, 0]), (0.25, [10, 0, 0]), (0.9, [0, 0, 0]))},
        "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [-18, -10, -8]), (0.9, [0, 0, 0]))},
        "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [-18, 10, 8]), (0.9, [0, 0, 0]))},
        "ponytail": {"rotation": kf((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.9, [0, 0, 0]))},
    }}
    a[f"{p}.stamp"] = {"loop": False, "animation_length": 0.9, "bones": {
        "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [-75, 0, 0]), (0.4, [-38, 0, 0]), (0.55, [-70, 0, 0]), (0.9, [0, 0, 0]))},
        "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [14, 0, 0]), (0.9, [0, 0, 0]))},
    }}
    return {"format_version": "1.8.0", "animations": a}


def master_animations():
    p = "animation.guild_master"
    a = common_animations(p, master=True)
    a[f"{p}.idle"]["bones"]["right_arm"] = {"rotation": kf((0, [-6, 0, 12]), (1.5, [-9, 0, 15]), (3.0, [-6, 0, 12]))}
    a[f"{p}.idle"]["bones"]["left_arm"] = {"rotation": kf((0, [-6, 0, -12]), (1.5, [-9, 0, -15]), (3.0, [-6, 0, -12]))}
    a[f"{p}.idle"]["bones"]["cape"] = {"rotation": kf((0, [3, 0, 0]), (1.5, [5, 0, 0]), (3.0, [3, 0, 0]))}
    a[f"{p}.walk"]["bones"]["cape"] = {"rotation": kf((0, [8, 0, 0]), (0.5, [16, 0, 0]), (1.0, [8, 0, 0]))}
    a[f"{p}.nod"] = {"loop": False, "animation_length": 0.6, "bones": {
        "head": {"rotation": kf((0, [0, 0, 0]), (0.2, [18, 0, 0]), (0.45, [0, 0, 0]))}}}
    a[f"{p}.slam"] = {"loop": False, "animation_length": 0.8, "bones": {
        "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-150, 0, 10]), (0.45, [-40, 0, 0]), (0.8, [0, 0, 0]))},
        "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [-5, 0, 0]), (0.45, [10, 0, 0]), (0.8, [0, 0, 0]))},
        "head": {"rotation": kf((0, [0, 0, 0]), (0.45, [8, 0, 0]), (0.8, [0, 0, 0]))},
    }}
    return {"format_version": "1.8.0", "animations": a}


def adventurer_animations():
    p = "animation.adventurer"
    a = common_animations(p)
    a[f"{p}.idle"]["bones"]["cape"] = {"rotation": kf((0, [4, 0, 0]), (1.5, [7, 0, 0]), (3.0, [4, 0, 0]))}
    a[f"{p}.walk"]["bones"]["cape"] = {"rotation": kf((0, [10, 0, 0]), (0.5, [22, 0, 0]), (1.0, [10, 0, 0]))}
    a[f"{p}.attack"] = {"loop": False, "animation_length": 0.45, "bones": {
        "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.12, [-150, 0, 10]), (0.25, [10, 0, -10]), (0.45, [0, 0, 0]))},
        "body": {"rotation": kf((0, [0, 0, 0]), (0.12, [0, 15, 0]), (0.25, [0, -20, 0]), (0.45, [0, 0, 0]))},
    }}
    a[f"{p}.shoot"] = {"loop": False, "animation_length": 0.7, "bones": {
        "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.15, [-90, -10, 0]), (0.45, [-90, -35, 0]), (0.55, [-90, 0, 0]),
                                     (0.7, [0, 0, 0]))},
        "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.15, [-90, 10, 0]), (0.55, [-90, 10, 0]), (0.7, [0, 0, 0]))},
        "head": {"rotation": kf((0, [0, 0, 0]), (0.15, [0, 10, 0]), (0.7, [0, 0, 0]))},
    }}
    a[f"{p}.cast"] = {"loop": False, "animation_length": 0.8, "bones": {
        "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [-120, 0, 8]), (0.45, [-60, 0, 0]), (0.8, [0, 0, 0]))},
        "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-70, 0, -10]), (0.8, [0, 0, 0]))},
        "body": {"rotation": kf((0, [0, 0, 0]), (0.25, [-6, 0, 0]), (0.45, [4, 0, 0]), (0.8, [0, 0, 0]))},
        "cape": {"rotation": kf((0, [4, 0, 0]), (0.45, [20, 0, 0]), (0.8, [4, 0, 0]))},
    }}
    # v0.8: Golpe Giratório do guerreiro e Bênção do clérigo (cura, contrato, subir de rank).
    a[f"{p}.spin"] = {"loop": False, "animation_length": 0.6, "bones": {
        "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [0, 180, 0]), (0.55, [0, 360, 0]), (0.6, [0, 360, 0]))},
        "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.1, [-90, 0, 70]), (0.5, [-90, 0, 70]), (0.6, [0, 0, 0]))},
        "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.1, [-20, 0, -40]), (0.5, [-20, 0, -40]), (0.6, [0, 0, 0]))},
        "cape": {"rotation": kf((0, [4, 0, 0]), (0.3, [45, 0, 0]), (0.6, [4, 0, 0]))},
    }}
    a[f"{p}.bless"] = {"loop": False, "animation_length": 1.0, "bones": {
        "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [-160, 0, 20]), (0.7, [-165, 0, 25]), (1.0, [0, 0, 0]))},
        "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [-160, 0, -20]), (0.7, [-165, 0, -25]), (1.0, [0, 0, 0]))},
        "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.7, [-15, 0, 0]), (1.0, [0, 0, 0]))},
        "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [-4, 0, 0]), (1.0, [0, 0, 0]))},
    }}
    a[f"{p}.idle"]["bones"]["robe"] = {"rotation": kf((0, [0, 0, 0]), (1.5, [1.5, 0, 0]), (3.0, [0, 0, 0]))}
    return {"format_version": "1.8.0", "animations": a}


def geckolib_files():
    g = receptionist_geo()
    write_json(os.path.join(A, "geo/entity/guild_receptionist.geo.json"), g.to_json())
    g.paint(receptionist_painters(), 31).save(os.path.join(A, "textures/entity/guild_receptionist.png"))
    write_json(os.path.join(A, "animations/entity/guild_receptionist.animation.json"), receptionist_animations())

    g = master_geo()
    write_json(os.path.join(A, "geo/entity/guild_master.geo.json"), g.to_json(bounds=(3, 4)))
    g.paint(master_painters(), 32).save(os.path.join(A, "textures/entity/guild_master.png"))
    write_json(os.path.join(A, "animations/entity/guild_master.animation.json"), master_animations())

    g = adventurer_geo()
    write_json(os.path.join(A, "geo/entity/adventurer.geo.json"), g.to_json(bounds=(3, 4)))
    for i, v in enumerate(("warrior", "archer", "mage", "rogue", "cleric")):
        g.paint(adventurer_painters(v), 40 + i).save(os.path.join(A, f"textures/entity/adventurer_{v}.png"))
    write_json(os.path.join(A, "animations/entity/adventurer.animation.json"), adventurer_animations())


# ------------------------------------------------------------------------------------------------
# Missoes (data/isekai_mahou/guild_quests) — titulo/descricao vao para as traducoes
# ------------------------------------------------------------------------------------------------
L = {}       # chave -> (pt_br, en_us)
QUESTS = []  # (id, json)


def tr(key, pt, en):
    L[key] = (pt, en)
    return key


def lbl(key, pt, en):
    return tr(f"guild.{NS}.{key}", pt, en)


def M(x):
    return f"{NS}:{x}"


def V(x):
    return f"minecraft:{x}"


def kill(entity, n, label=None):
    o = {"type": "kill", "entity": entity, "count": n}
    if label:
        o["label"] = label
    return o


def gather(item, n, label=None):
    o = {"type": "gather", "item": item, "count": n}
    if label:
        o["label"] = label
    return o


def explore(dimension=None, structure=None, biome=None, label=None):
    o = {"type": "explore"}
    if dimension:
        o["dimension"] = dimension
    if structure:
        o["structure"] = structure
    if biome:
        o["biome"] = biome
    if label:
        o["label"] = label
    return o


def trial_clear(n=1, rank=1, element=None, distinct=False, absolute=False, label=None):
    """Câmaras de Provação concluídas (v0.7): rank mínimo, elemento opcional, elementos diferentes, total absoluto."""
    o = {"type": "trial_clear", "count": n}
    if rank != 1:
        o["rank"] = rank
    if element:
        o["element"] = element
    if distinct:
        o["distinct"] = True
    if absolute:
        o["absolute"] = True
    if label:
        o["label"] = label
    return o


def counter(key, n, label=None, icon=None, absolute=False):
    o = {"type": "counter", "key": key, "count": n}
    if absolute:
        o["absolute"] = True
    if label:
        o["label"] = label
    if icon:
        o["icon"] = icon
    return o


def quest(qid, rank, objectives, coins, xp, points, pt, en, glyph=False, items=None, category=None, weight=10,
          repeatable=True, trial=False, glyph_count=1, requires=None):
    rewards = {"coins": coins, "xp": xp}
    if points:
        rewards["points"] = points
    if glyph:
        # True = glifo aleatório do tier do rank; texto = id de um glifo específico.
        rewards["glyph"] = glyph if isinstance(glyph, str) else "random"
        if glyph_count > 1:
            rewards["glyph_count"] = glyph_count
    if items:
        rewards["items"] = items
    data = {"rank": rank}
    if category:
        data["category"] = category
    data["objectives"] = objectives
    data["rewards"] = rewards
    if weight != 10:
        data["weight"] = weight
    if not repeatable:
        data["repeatable"] = False
    if trial:
        data["trial"] = True
    if requires:
        data["requires"] = requires
    QUESTS.append((qid, data))
    tr(f"guild_quest.{NS}.{qid}", pt[0], en[0])
    tr(f"guild_quest.{NS}.{qid}.desc", pt[1], en[1])


SLIMES = [M("slime"), M("fire_slime"), M("water_slime"), M("stone_slime"), M("wind_slime")]
ZOMBIES = [V("zombie"), V("husk"), V("zombie_villager"), V("drowned")]
GUARDIANS = [M("ignis"), M("tide_guardian"), M("primordial_golem"), M("zephyr")]
GREATER = [M("greater_fire_elemental"), M("greater_water_elemental"), M("greater_earth_elemental"), M("greater_air_elemental")]
FALLEN = [M("renegade_archmage"), M("corrupted_witch"), M("corrupted_ent"), M("lich")]


def item(i, n=1, potion=None):
    o = {"id": i, "count": n}
    if potion:
        o["potion"] = potion
    return o


def build_quests():
    t_slimes = lbl("target.slimes", "Slimes", "Slimes")
    t_zombies = lbl("target.zombies", "Zumbis", "Zombies")
    t_spiders = lbl("target.spiders", "Aranhas", "Spiders")
    t_skeletons = lbl("target.skeletons", "Esqueletos", "Skeletons")
    t_monsters = lbl("target.monsters", "Monstros da noite", "Night monsters")
    t_illagers = lbl("target.illagers", "Saqueadores", "Illagers")
    t_greater = lbl("target.greater_elementals", "Elementais Maiores", "Greater Elementals")
    t_guardians = lbl("target.guardians", "um Guardião Elemental", "an Elemental Guardian")
    t_fallen = lbl("target.fallen", "um Mestre Caído", "a Fallen Master")
    t_sovereigns = lbl("target.sovereigns", "o Soberano do Abismo ou o Serafim", "the Abyss Sovereign or the Seraph")
    t_legends = lbl("target.legends", "o Dragão do Fim ou o Warden", "the Ender Dragon or the Warden")
    i_flowers = lbl("item.flowers", "Flores", "Flowers")
    i_logs = lbl("item.logs", "Toras de madeira", "Logs")
    i_wool = lbl("item.wool", "Lã", "Wool")
    p_village = lbl("place.village", "uma vila", "a village")
    night = [V("zombie"), V("skeleton"), V("spider"), V("creeper"), V("witch")]

    # ---------------- F ----------------
    quest("f_first_steps", "F", [kill(night, 3, t_monsters)], 5, 40, 8,
          ("Primeiros Passos", "Todo aventureiro começa assim: prove que sabe se defender."),
          ("First Steps", "Every adventurer starts like this: prove you can defend yourself."),
          glyph=True, weight=40, repeatable=False)
    quest("f_slime_cull", "F", [kill(SLIMES, 8, t_slimes)], 3, 25, 6,
          ("Controle de Slimes", "Os slimes estão invadindo as plantações. Diminua o número deles."),
          ("Slime Control", "Slimes are invading the crops. Thin them out."))
    quest("f_goblin_scouts", "F", [kill(M("goblin"), 5)], 4, 30, 7,
          ("Batedores Goblin", "Goblins foram vistos rondando a estrada do vilarejo."),
          ("Goblin Scouts", "Goblins have been spotted lurking by the village road."))
    quest("f_zombie_patrol", "F", [kill(ZOMBIES, 10, t_zombies)], 3, 25, 6,
          ("Patrulha Noturna", "Zumbis atacam os viajantes depois do pôr do sol."),
          ("Night Patrol", "Zombies attack travelers after sundown."))
    quest("f_spider_nest", "F", [kill([V("spider"), V("cave_spider")], 6, t_spiders)], 3, 25, 6,
          ("Ninho de Aranhas", "Um fazendeiro reclama de aranhas no celeiro."),
          ("Spider Nest", "A farmer complains about spiders in the barn."))
    quest("f_restless_bones", "F", [kill([V("skeleton"), V("stray")], 6, t_skeletons)], 3, 25, 6,
          ("Ossos Inquietos", "Esqueletos arqueiros estão emboscando os caçadores."),
          ("Restless Bones", "Skeleton archers are ambushing hunters."))
    quest("f_herb_gathering", "F", [gather("#minecraft:small_flowers", 10, i_flowers), gather(V("wheat_seeds"), 8)], 3, 20, 5,
          ("Coleta de Ervas", "O boticário precisa de flores e sementes para os remédios."),
          ("Herb Gathering", "The apothecary needs flowers and seeds for remedies."), items=[item(V("bread"), 3)])
    quest("f_lumber_request", "F", [gather("#minecraft:logs", 32, i_logs)], 4, 20, 5,
          ("Pedido da Serraria", "A Guilda está reformando o salão e precisa de madeira."),
          ("Lumber Request", "The Guild hall is being renovated and needs timber."))
    quest("f_tavern_supplies", "F", [gather(V("bread"), 12)], 4, 20, 5,
          ("Mantimentos da Taverna", "A taverna ao lado da Guilda ficou sem pão."),
          ("Tavern Supplies", "The tavern next to the Guild ran out of bread."))
    quest("f_coal_delivery", "F", [gather(V("coal"), 16)], 4, 20, 5,
          ("Carvão para a Forja", "O ferreiro precisa de carvão para as encomendas da semana."),
          ("Coal for the Forge", "The blacksmith needs coal for this week's orders."))
    quest("f_winter_wool", "F", [gather("#minecraft:wool", 8, i_wool)], 3, 20, 5,
          ("Lã para o Inverno", "O orfanato precisa de cobertores antes do frio."),
          ("Wool for Winter", "The orphanage needs blankets before the cold arrives."))

    # ---------------- E ----------------
    quest("e_goblin_hunt", "E", [kill(M("goblin"), 10)], 6, 50, 12,
          ("Caçada aos Goblins", "Um bando de goblins está saqueando carroças. Acabe com eles."),
          ("Goblin Hunt", "A goblin band is raiding wagons. Put an end to it."), glyph=True)
    quest("e_zombie_horde", "E", [kill(ZOMBIES, 15, t_zombies)], 5, 45, 11,
          ("Horda de Zumbis", "Uma horda de mortos-vivos se aproxima da vila."),
          ("Zombie Horde", "A horde of undead approaches the village."))
    quest("e_goblin_archers", "E", [kill(M("goblin_archer"), 6)], 6, 50, 12,
          ("Arqueiros na Mata", "Arqueiros goblin estão atirando em quem passa pela floresta."),
          ("Archers in the Woods", "Goblin archers are shooting at anyone crossing the forest."))
    quest("e_creeper_control", "E", [kill(V("creeper"), 6)], 6, 45, 12,
          ("Ameaça Explosiva", "Creepers explodiram metade do muro da vila. De novo."),
          ("Explosive Threat", "Creepers blew up half the village wall. Again."))
    quest("e_iron_requisition", "E", [gather(V("iron_ingot"), 12)], 7, 40, 11,
          ("Requisição de Ferro", "A Guilda precisa de ferro para equipar os novatos."),
          ("Iron Requisition", "The Guild needs iron to equip the rookies."))
    quest("e_village_letter", "E", [explore(structure="#minecraft:village", label=p_village)], 5, 40, 10,
          ("Carta para a Vila", "Leve uma carta da Guilda até uma vila próxima."),
          ("Letter to the Village", "Deliver a Guild letter to a nearby village."), items=[item(V("map"))])
    quest("e_kobold_mines", "E", [kill(M("kobold"), 8)], 6, 50, 12,
          ("Minas dos Kobolds", "Kobolds tomaram as galerias da mina de ferro."),
          ("Kobold Mines", "Kobolds have taken over the iron mine tunnels."))
    quest("e_slime_gel", "E", [gather(M("slime_gel"), 10)], 6, 40, 11,
          ("Gel para o Alquimista", "O alquimista paga bem por gel de slime fresco."),
          ("Gel for the Alchemist", "The alchemist pays well for fresh slime gel."))
    quest("e_night_watch", "E", [kill(night, 15, t_monsters)], 6, 55, 12,
          ("Vigília Noturna", "Monte guarda durante a noite e proteja a vila."),
          ("Night Watch", "Stand guard through the night and protect the village."))
    quest("e_old_mineshaft", "E", [explore(structure=V("mineshaft"))], 6, 50, 12,
          ("Mina Abandonada", "Mapeie a entrada de uma mina abandonada para a Guilda."),
          ("Abandoned Mineshaft", "Scout the entrance of an abandoned mineshaft for the Guild."), glyph=True)
    quest("e_poison_jelly", "E", [kill(M("poison_jelly"), 4)], 6, 50, 12,
          ("Gelatinas Venenosas", "Gelatinas venenosas contaminaram o poço da vila."),
          ("Poison Jellies", "Poison jellies have tainted the village well."))
    quest("e_arcane_essence", "E", [gather(M("essence_arcane"), 2)], 7, 50, 12,
          ("Essência para a Torre", "A torre dos magos precisa de essência arcana."),
          ("Essence for the Tower", "The mages' tower needs arcane essence."))

    # ---------------- D ----------------
    quest("d_king_slime", "D", [kill(M("king_slime"), 1)], 12, 120, 22,
          ("O Rei Slime", "Um slime gigante com uma coroa? Não é piada. Derrote-o."),
          ("The King Slime", "A giant slime wearing a crown? Not a joke. Defeat it."), glyph=True, category="boss")
    quest("d_hobgoblins", "D", [kill(M("hobgoblin"), 3)], 10, 90, 18,
          ("Hobgoblins na Fronteira", "Hobgoblins lideram os ataques na fronteira."),
          ("Hobgoblins at the Border", "Hobgoblins are leading the border raids."))
    quest("d_goblin_shamans", "D", [kill(M("goblin_shaman"), 4)], 10, 100, 18,
          ("Xamãs Goblin", "Xamãs goblin aprenderam magia de glifos. Detenha-os."),
          ("Goblin Shamans", "Goblin shamans have learned glyph magic. Stop them."), glyph=True)
    quest("d_wolf_riders", "D", [kill(M("goblin_wolf_rider"), 4)], 10, 90, 18,
          ("Cavaleiros de Lobo", "Goblins montados em lobos aterrorizam as estradas."),
          ("Wolf Riders", "Goblins riding wolves are terrorizing the roads."))
    quest("d_road_raiders", "D", [kill([V("pillager"), V("vindicator")], 8, t_illagers)], 10, 90, 18,
          ("Saqueadores na Estrada", "Saqueadores atacam as caravanas de comércio."),
          ("Road Raiders", "Illagers are attacking the trade caravans."))
    quest("d_desert_secrets", "D", [explore(structure=V("desert_pyramid"))], 9, 80, 16,
          ("Segredos do Deserto", "Encontre uma pirâmide do deserto e relate o que viu."),
          ("Desert Secrets", "Find a desert pyramid and report what you saw."), glyph=True)
    quest("d_witch_hunt", "D", [kill(V("witch"), 3)], 10, 90, 18,
          ("Bruxas do Pântano", "Bruxas estão envenenando os viajantes do pântano."),
          ("Swamp Witches", "Witches are poisoning swamp travelers."))
    quest("d_gold_tribute", "D", [gather(V("gold_ingot"), 10)], 11, 70, 16,
          ("Tributo de Ouro", "O tesoureiro da Guilda precisa repor o cofre."),
          ("Gold Tribute", "The Guild treasurer needs to refill the vault."))
    quest("d_orc_scouts", "D", [kill(M("orc"), 5)], 11, 100, 19,
          ("Batedores Orc", "Orcs estão espionando as vilas humanas."),
          ("Orc Scouts", "Orcs are spying on human villages."))
    quest("d_drowned_coast", "D", [kill(V("drowned"), 10)], 10, 90, 18,
          ("Costa dos Afogados", "Afogados atacam os pescadores na praia."),
          ("Drowned Coast", "Drowned are attacking fishermen on the beach."))
    quest("d_mana_crystals", "D", [gather(M("mana_crystal"), 3)], 11, 80, 17,
          ("Cristais de Mana", "A Guilda precisa de cristais para os círculos de teleporte."),
          ("Mana Crystals", "The Guild needs crystals for its teleport circles."))
    quest("d_lizardmen", "D", [kill(M("lizardman"), 6)], 10, 100, 18,
          ("Homens-Lagarto", "Homens-lagarto expandiram o território no pântano."),
          ("Lizardmen", "Lizardmen have expanded their swamp territory."))
    quest("d_jungle_temple", "D", [explore(structure=V("jungle_pyramid"))], 9, 80, 16,
          ("Templo na Selva", "Um explorador sumiu perto de um templo na selva. Encontre o templo."),
          ("Jungle Temple", "An explorer vanished near a jungle temple. Find it."))
    quest("d_outpost", "D", [explore(structure=V("pillager_outpost")), kill(V("pillager"), 5)], 12, 100, 20,
          ("Posto Avançado", "Localize um posto avançado de saqueadores e enfraqueça a guarnição."),
          ("Outpost", "Locate an illager outpost and weaken its garrison."), glyph=M("pierce"))

    # ---------------- C ----------------
    quest("c_goblin_fortress", "C", [kill(M("goblin_king"), 1)], 18, 220, 32,
          ("A Fortaleza Goblin", "O Rei Goblin voltou a reunir as tribos. Derrube-o de novo."),
          ("The Goblin Fortress", "The Goblin King has rallied the tribes again. Bring him down."), glyph=True, category="boss")
    quest("c_berserkers", "C", [kill(M("orc_berserker"), 4)], 15, 170, 28,
          ("Fúria dos Berserkers", "Berserkers orc não sentem dor. Tenha cuidado."),
          ("Berserker Fury", "Orc berserkers feel no pain. Be careful."))
    quest("c_hill_ogre", "C", [kill(M("ogre"), 2)], 15, 170, 28,
          ("O Ogro da Colina", "Um ogro está devorando o gado da região."),
          ("The Hill Ogre", "An ogre is devouring the region's cattle."))
    quest("c_troll_bridge", "C", [kill(M("troll"), 1)], 14, 160, 26,
          ("A Ponte do Troll", "Um troll cobra pedágio na ponte. Fogo ajuda contra a regeneração."),
          ("The Troll Bridge", "A troll charges a toll at the bridge. Fire helps against its regeneration."))
    quest("c_nether_expedition", "C", [explore(structure=V("fortress")), gather(V("blaze_rod"), 4)], 16, 200, 30,
          ("Expedição ao Nether", "Encontre uma fortaleza do Nether e traga varas de blaze."),
          ("Nether Expedition", "Find a Nether fortress and bring back blaze rods."), glyph=True)
    quest("c_living_flames", "C", [kill(V("blaze"), 6)], 15, 180, 28,
          ("Chamas Vivas", "Os blazes estão fora de controle perto do portal."),
          ("Living Flames", "Blazes are out of control near the portal."))
    quest("c_diamond_order", "C", [gather(V("diamond"), 5)], 16, 150, 28,
          ("Encomenda de Diamantes", "Um nobre encomendou uma coroa nova."),
          ("Diamond Order", "A noble has commissioned a new crown."))
    quest("c_harpy_nest", "C", [kill(M("harpy"), 6)], 15, 180, 28,
          ("Ninho de Harpias", "Harpias estão roubando crianças nas montanhas."),
          ("Harpy Nest", "Harpies are snatching children in the mountains."))
    quest("c_dark_cult", "C", [kill(M("cultist"), 5)], 16, 200, 30,
          ("O Culto Sombrio", "Cultistas preparam um ritual proibido. Interrompa-o."),
          ("The Dark Cult", "Cultists are preparing a forbidden ritual. Disrupt it."), glyph=True)
    quest("c_full_moon", "C", [kill(M("werewolf"), 2)], 15, 180, 28,
          ("Lua Cheia", "Uivos na lua cheia. Os lobisomens estão caçando."),
          ("Full Moon", "Howls under the full moon. The werewolves are hunting."))
    quest("c_sunken_monument", "C", [explore(structure=V("monument")), kill(V("guardian"), 6)], 17, 200, 30,
          ("Monumento Submerso", "Investigue um monumento oceânico e seus guardiões."),
          ("Sunken Monument", "Investigate an ocean monument and its guardians."), glyph=M("ricochet"))
    quest("c_arcane_supply", "C", [gather(M("essence_arcane"), 4)], 15, 160, 27,
          ("Suprimento Arcano", "Os magos da Guilda estão estudando um selo antigo."),
          ("Arcane Supply", "The Guild mages are studying an ancient seal."))
    quest("c_nether_quartz", "C", [explore(dimension=V("the_nether")), gather(V("quartz"), 16)], 14, 150, 26,
          ("Quartzo do Inferno", "Traga quartzo do Nether para o vitralista."),
          ("Hellish Quartz", "Bring Nether quartz for the glassmaker."))

    # ---------------- B ----------------
    quest("b_orc_lord", "B", [kill(M("orc_lord"), 1)], 25, 320, 45,
          ("O Lorde Orc", "O Lorde Orc declarou guerra. Leve a luta até ele."),
          ("The Orc Lord", "The Orc Lord has declared war. Take the fight to him."), glyph=True, category="boss")
    quest("b_stone_gaze", "B", [kill(M("basilisk"), 1)], 22, 280, 40,
          ("Olhar de Pedra", "Um basilisco transformou uma patrulha em estátuas."),
          ("Stone Gaze", "A basilisk turned a patrol into statues."))
    quest("b_gargoyles", "B", [kill(M("gargoyle"), 4)], 22, 280, 40,
          ("Gárgulas Despertas", "As gárgulas da catedral em ruínas acordaram."),
          ("Awakened Gargoyles", "The gargoyles of the ruined cathedral have awakened."))
    quest("b_beyond_the_veil", "B", [explore(dimension=M("elemental_plane"))], 24, 300, 42,
          ("Além do Véu", "Atravesse para o Plano Elemental e volte para contar."),
          ("Beyond the Veil", "Cross into the Elemental Plane and come back to tell the tale."), glyph=True)
    quest("b_elemental_fury", "B", [kill(GREATER, 2, t_greater)], 26, 340, 46,
          ("Fúria Elemental", "Elementais maiores estão rasgando o véu entre os mundos."),
          ("Elemental Fury", "Greater elementals are tearing the veil between worlds."))
    quest("b_black_bones", "B", [kill(V("wither_skeleton"), 10)], 22, 280, 40,
          ("Esqueletos Negros", "Os ferreiros querem crânios... e menos esqueletos negros."),
          ("Black Bones", "The smiths want skulls... and fewer wither skeletons."))
    quest("b_ghast_tears", "B", [kill(V("ghast"), 4)], 22, 280, 40,
          ("Lágrimas de Ghast", "Os ghasts estão bombardeando o posto da Guilda no Nether."),
          ("Ghast Tears", "Ghasts are bombarding the Guild outpost in the Nether."))
    quest("b_ruined_bastion", "B", [explore(structure=V("bastion_remnant")), kill(V("piglin_brute"), 2)], 25, 320, 44,
          ("Bastião em Ruínas", "Invada um bastião e derrote seus brutamontes."),
          ("Ruined Bastion", "Raid a bastion and defeat its brutes."))
    quest("b_woodland_mansion", "B", [explore(structure=V("mansion")), kill(V("vindicator"), 4)], 26, 340, 46,
          ("A Mansão da Floresta", "Dizem que a mansão guarda segredos dos illagers."),
          ("The Woodland Mansion", "They say the mansion holds illager secrets."), glyph=True)
    quest("b_ancient_debris", "B", [gather(V("netherite_scrap"), 2)], 24, 280, 42,
          ("Detritos Antigos", "O mestre-ferreiro quer forjar uma lâmina lendária."),
          ("Ancient Debris", "The master smith wants to forge a legendary blade."), glyph=M("silk_touch"))
    quest("b_treacherous_chest", "B", [kill(M("mimic"), 2)], 22, 280, 40,
          ("Baú Traiçoeiro", "Aventureiros somem em masmorras: baús com dentes."),
          ("Treacherous Chest", "Adventurers vanish in dungeons: chests with teeth."))
    quest("b_trial_chambers", "B", [explore(structure=V("trial_chambers")), kill(V("breeze"), 3)], 24, 300, 42,
          ("Câmaras do Desafio", "Teste suas habilidades nas câmaras subterrâneas."),
          ("Trial Chambers", "Test your skills in the underground chambers."))

    # ---------------- A ----------------
    quest("a_guardian_challenge", "A", [kill(GUARDIANS, 1, t_guardians)], 40, 550, 65,
          ("Desafio ao Guardião", "Os Guardiões Elementais testam quem ousa entrar nos templos."),
          ("Guardian Challenge", "The Elemental Guardians test those who dare enter their temples."), glyph=True, category="boss")
    quest("a_labyrinth", "A", [kill(M("minotaur"), 1)], 35, 480, 60,
          ("O Labirinto", "Ninguém volta do labirinto. Seja o primeiro."),
          ("The Labyrinth", "No one returns from the labyrinth. Be the first."), category="boss")
    quest("a_triple_ruin", "A", [kill(V("wither"), 1)], 40, 520, 65,
          ("A Ruína Tripla", "Alguém invocou o Wither. A Guilda precisa de você."),
          ("The Triple Ruin", "Someone summoned the Wither. The Guild needs you."), category="boss")
    quest("a_sea_elder", "A", [kill(V("elder_guardian"), 1)], 32, 450, 55,
          ("O Ancião do Mar", "O ancião do monumento amaldiçoa os mineradores."),
          ("The Sea Elder", "The monument's elder curses the miners."))
    quest("a_storm_wings", "A", [kill(M("storm_wyvern"), 1)], 35, 480, 60,
          ("Asas da Tempestade", "Uma wyvern da tempestade derrubou um dirigível."),
          ("Storm Wings", "A storm wyvern brought down an airship."))
    quest("a_end_city", "A", [explore(structure=V("end_city")), gather(V("shulker_shell"), 4)], 38, 500, 62,
          ("Cidade no Fim", "Explore uma cidade do Fim e traga cascos de shulker."),
          ("City at the End", "Explore an End city and bring back shulker shells."), glyph=True)
    quest("a_ancient_echoes", "A", [explore(structure=V("ancient_city")), gather(V("echo_shard"), 2)], 36, 480, 60,
          ("Ecos da Cidade Antiga", "Desça às profundezas em silêncio. Muito silêncio."),
          ("Ancient Echoes", "Descend into the deep in silence. A lot of silence."))
    quest("a_reborn_flames", "A", [kill(M("lesser_phoenix"), 1)], 36, 480, 60,
          ("Chamas Renascidas", "Uma fênix menor está incendiando o Plano Elemental."),
          ("Reborn Flames", "A lesser phoenix is setting the Elemental Plane ablaze."))
    quest("a_fallen_masters", "A", [kill(FALLEN, 1, t_fallen)], 40, 520, 65,
          ("Mestres Caídos", "Magos que se perderam na própria ambição. Detenha um deles."),
          ("Fallen Masters", "Mages lost to their own ambition. Stop one of them."), glyph=True, category="boss")
    quest("a_abyss_descent", "A", [explore(dimension=M("abyss"))], 38, 500, 62,
          ("Descida ao Abismo", "Pise no Abismo e sobreviva para relatar."),
          ("Abyss Descent", "Set foot in the Abyss and live to report."))
    quest("a_void_crawlers", "A", [kill(M("void_crawler"), 4)], 36, 480, 60,
          ("Rastejadores do Vazio", "Algo rasteja para fora das fendas do Abismo."),
          ("Void Crawlers", "Something is crawling out of the Abyss rifts."))
    quest("a_end_expedition", "A", [explore(dimension=V("the_end")), kill(V("enderman"), 10)], 34, 460, 58,
          ("Expedição ao Fim", "Viaje ao Fim e enfrente os endermen."),
          ("End Expedition", "Travel to the End and face the endermen."))

    # ---------------- S ----------------
    quest("s_abyss_sovereign", "S", [kill(M("abyss_sovereign"), 1)], 80, 1000, 110,
          ("O Soberano do Abismo", "O trono do Abismo precisa cair."),
          ("The Abyss Sovereign", "The throne of the Abyss must fall."), glyph=True, category="boss")
    quest("s_seraph_fall", "S", [kill(M("seraph"), 1)], 80, 1000, 110,
          ("A Queda do Serafim", "O Serafim enlouqueceu e fechou os portões do Céu."),
          ("Fall of the Seraph", "The Seraph went mad and sealed the gates of Heaven."), glyph=True, category="boss")
    quest("s_twilight_herald", "S", [kill(M("twilight_herald"), 1)], 100, 1400, 130,
          ("O Arauto do Crepúsculo", "O fim de todas as coisas tem um rosto. Encare-o."),
          ("The Twilight Herald", "The end of all things has a face. Stare it down."), glyph=True, glyph_count=2, category="boss")
    quest("s_deep_warden", "S", [kill(V("warden"), 1)], 70, 900, 100,
          ("O Guardião das Profundezas", "Algo cego e terrível vigia a cidade antiga."),
          ("The Deep Warden", "Something blind and terrible guards the ancient city."), category="boss")
    quest("s_dragon_slayer", "S", [kill(V("ender_dragon"), 1)], 80, 1000, 110,
          ("Matador de Dragões", "Toda lenda precisa de um dragão."),
          ("Dragon Slayer", "Every legend needs a dragon."), category="boss")
    quest("s_heaven_gates", "S", [explore(dimension=M("celestial_sanctuary"))], 70, 900, 100,
          ("Portões do Céu", "Alcance o Santuário Celestial."),
          ("Gates of Heaven", "Reach the Celestial Sanctuary."))
    quest("s_legendary_arsenal", "S", [gather(V("netherite_ingot"), 4)], 60, 800, 95,
          ("Arsenal Lendário", "A Guilda vai equipar seus heróis rank S."),
          ("Legendary Arsenal", "The Guild will equip its S-rank heroes."))
    quest("s_twilight_rift", "S", [explore(dimension=M("twilight_rift"))], 75, 950, 105,
          ("A Fenda do Crepúsculo", "Entre na fenda onde o mundo acaba."),
          ("The Twilight Rift", "Enter the rift where the world ends."))
    quest("s_last_lich", "S", [kill(M("lich"), 1)], 70, 900, 100,
          ("O Último Lich", "Encontre a filactéria. Termine o que começou há séculos."),
          ("The Last Lich", "Find the phylactery. End what began centuries ago."), category="boss")

    # ---------------- Provas de promoção ("rank" = rank alcançado) ----------------
    quest("trial_e", "E", [kill(SLIMES, 8, t_slimes)], 10, 80, 0,
          ("Prova do Rank E: Caçador de Slimes", "Mostre ao Mestre que domina o básico do combate."),
          ("Rank E Trial: Slime Hunter", "Show the Master you've mastered basic combat."), trial=True, category="trial")
    quest("trial_e_alt", "E", [kill([V("zombie"), V("husk")], 12, t_zombies)], 10, 80, 0,
          ("Prova do Rank E: Guardião da Vila", "Proteja a vila dos mortos-vivos durante a noite."),
          ("Rank E Trial: Village Guardian", "Protect the village from the undead through the night."), trial=True, category="trial")
    quest("trial_d", "D", [kill(M("king_slime"), 1)], 15, 150, 0,
          ("Prova do Rank D: O Rei Slime", "Derrote o Rei Slime sem fugir."),
          ("Rank D Trial: The King Slime", "Defeat the King Slime without running away."), trial=True, category="trial")
    quest("trial_d_alt", "D", [kill([V("skeleton"), V("stray")], 12, t_skeletons), kill(V("creeper"), 6)], 15, 150, 0,
          ("Prova do Rank D: Limpeza", "Limpe os arredores de esqueletos e creepers."),
          ("Rank D Trial: Clean Sweep", "Clear the surroundings of skeletons and creepers."), trial=True, category="trial")
    quest("trial_c", "C", [kill(M("goblin_king"), 1)], 25, 300, 0,
          ("Prova do Rank C: O Rei Goblin", "Invada a Fortaleza Goblin e derrote o Rei Goblin."),
          ("Rank C Trial: The Goblin King", "Storm the Goblin Fortress and defeat the Goblin King."), trial=True, category="trial")
    quest("trial_c_alt", "C", [explore(structure=V("pillager_outpost")), kill(V("pillager"), 12)], 25, 300, 0,
          ("Prova do Rank C: O Posto Avançado", "Desmonte um posto avançado de saqueadores."),
          ("Rank C Trial: The Outpost", "Dismantle an illager outpost."), trial=True, category="trial")
    quest("trial_b", "B", [kill(M("orc_lord"), 1)], 35, 450, 0,
          ("Prova do Rank B: O Lorde Orc", "Derrote o Lorde Orc em seu acampamento."),
          ("Rank B Trial: The Orc Lord", "Defeat the Orc Lord in his camp."), trial=True, category="trial")
    quest("trial_b_alt", "B", [kill(V("elder_guardian"), 1)], 35, 450, 0,
          ("Prova do Rank B: O Ancião", "Derrote um guardião ancião no monumento."),
          ("Rank B Trial: The Elder", "Defeat an elder guardian in its monument."), trial=True, category="trial")
    quest("trial_a", "A", [kill(GUARDIANS, 1, t_guardians)], 50, 700, 0,
          ("Prova do Rank A: O Guardião", "Vença um Guardião Elemental em seu templo."),
          ("Rank A Trial: The Guardian", "Defeat an Elemental Guardian in its temple."), trial=True, category="trial")
    quest("trial_a_alt", "A", [kill(V("wither"), 1)], 50, 700, 0,
          ("Prova do Rank A: A Ruína", "Derrote o Wither."),
          ("Rank A Trial: The Ruin", "Defeat the Wither."), trial=True, category="trial")
    quest("trial_s", "S", [kill([M("abyss_sovereign"), M("seraph")], 1, t_sovereigns)], 100, 1200, 0,
          ("Prova do Rank S: Soberania", "Derrote um dos soberanos do Abismo ou do Céu."),
          ("Rank S Trial: Sovereignty", "Defeat one of the sovereigns of the Abyss or of Heaven."), trial=True, category="trial")
    quest("trial_s_alt", "S", [kill([V("ender_dragon"), V("warden")], 1, t_legends)], 100, 1200, 0,
          ("Prova do Rank S: Lenda Viva", "Derrote o Dragão do Fim ou o Warden."),
          ("Rank S Trial: Living Legend", "Defeat the Ender Dragon or the Warden."), trial=True, category="trial")

    v07_quests()

    for qid, data in QUESTS:
        write_json(os.path.join(D, f"guild_quests/{qid}.json"), data)


def v07_quests():
    """Missões da v0.7: Câmaras de Provação (contadores trial.clear/trial.<el>.<rank>), Avatar Elemental e Sentinela
    (boss.trial_avatar / boss.trial_sentinel) e o Pedestal Rúnico (pedestal.cast). Provas de promoção "_chamber" entram
    no sorteio diário do Mestre junto com a prova e a _alt de cada rank."""
    t_pedestal = lbl("target.pedestal_casts", "conjurações do Pedestal Rúnico", "Runic Pedestal casts")
    pedestal = M("runic_pedestal")
    # Avatar e Sentinela só aparecem nas Câmaras: sem o módulo trials (altar) essas missões ficam fora do quadro.
    chambers = M("trial_altar")

    # ---------------- F ----------------
    quest("f_first_trial", "F", [trial_clear(1)], 6, 60, 10,
          ("Batismo da Câmara", "Peça sua primeira Provação Elemental (aba Caminhos, no menu B) e saia vitorioso(a)."),
          ("Chamber Baptism", "Request your first Elemental Trial (Paths tab, in the B menu) and come out victorious."),
          glyph=True, weight=30, repeatable=False, category="chamber")

    # ---------------- E ----------------
    quest("e_chamber_training", "E", [trial_clear(1)], 7, 65, 13,
          ("Treino nas Câmaras", "A Guilda paga quem treina nas Câmaras de Provação. Conclua uma, de qualquer elemento."),
          ("Chamber Training", "The Guild pays those who train in the Trial Chambers. Clear one, of any element."),
          weight=12, category="chamber")

    # ---------------- D ----------------
    quest("d_trial_adept", "D", [trial_clear(1, rank=2)], 12, 130, 22,
          ("A Provação do Adepto", "Conclua uma Provação de rank II ou maior."),
          ("The Adept's Trial", "Clear a rank II or higher Trial."), glyph=True, category="chamber")
    quest("d_avatar_duel", "D", [kill(M("trial_avatar"), 1)], 11, 120, 20,
          ("Duelo com o Avatar", "Derrote um Avatar Elemental no fim de uma Provação — sozinho(a) ou ajudando um amigo."),
          ("Avatar Duel", "Defeat an Elemental Avatar at the end of a Trial — alone or helping a friend."), category="boss",
          requires=[chambers])
    quest("d_runic_pedestal", "D", [counter("pedestal.cast", 30, t_pedestal, icon=pedestal)], 14, 140, 22,
          ("Magia que Trabalha Sozinha", "Coloque um Pergaminho de Magia num Pedestal Rúnico e deixe-o conjurar 30 vezes."),
          ("Magic That Works Alone", "Put a Spell Scroll on a Runic Pedestal and let it cast 30 times."),
          items=[item(M("mana_crystal"), 2)], repeatable=False, category="task", requires=[pedestal])

    # ---------------- C ----------------
    quest("c_trial_fluidity", "C", [trial_clear(1, rank=3)], 18, 210, 32,
          ("Fluidez Elemental", "Conclua uma Provação de rank III ou maior."),
          ("Elemental Fluidity", "Clear a rank III or higher Trial."), glyph=True, category="chamber")
    quest("c_two_elements", "C", [trial_clear(2, distinct=True)], 19, 230, 34,
          ("Versatilidade", "Conclua Provações de dois elementos diferentes."),
          ("Versatility", "Clear Trials of two different elements."), category="chamber")
    quest("c_arcane_farm", "C", [counter("pedestal.cast", 150, t_pedestal, icon=pedestal)], 14, 150, 26,
          ("Fazenda Arcana", "A Guilda compra a produção das suas fazendas mágicas: 150 conjurações de Pedestal Rúnico."),
          ("Arcane Farm", "The Guild buys the output of your magic farms: 150 Runic Pedestal casts."),
          weight=6, category="task", requires=[pedestal])

    # ---------------- B ----------------
    quest("b_trial_master", "B", [trial_clear(1, rank=4)], 27, 350, 46,
          ("A Provação do Mestre", "Conclua uma Provação de rank IV ou maior."),
          ("The Master's Trial", "Clear a rank IV or higher Trial."), glyph=True, category="chamber")
    quest("b_chamber_marathon", "B", [trial_clear(3)], 26, 330, 45,
          ("Maratona das Câmaras", "Conclua três Provações (Desafios repetidos também contam)."),
          ("Chamber Marathon", "Clear three Trials (repeated Challenges count too)."), category="chamber")
    quest("b_avatar_slayer", "B", [kill(M("trial_avatar"), 3)], 25, 320, 44,
          ("Caçador de Avatares", "Derrote três Avatares Elementais. Fique de olho nos círculos no chão!"),
          ("Avatar Hunter", "Defeat three Elemental Avatars. Watch the circles on the ground!"), category="boss",
          requires=[chambers])

    # ---------------- A ----------------
    quest("a_trial_cataclysm", "A", [trial_clear(1, rank=5)], 42, 560, 66,
          ("Cataclismo", "Conclua uma Provação de rank V ou maior e vença o Guardião na arena."),
          ("Cataclysm", "Clear a rank V or higher Trial and beat the Guardian in the arena."), glyph=True, category="chamber")
    quest("a_sentinel_breaker", "A", [kill(M("trial_sentinel"), 2)], 38, 500, 62,
          ("Quebra-Escudos", "Derrote duas Sentinelas da Provação. O escudo só protege a frente: ataque pelos flancos!"),
          ("Shield Breaker", "Defeat two Trial Sentinels. The shield only guards the front: strike from the flanks!"), category="boss",
          requires=[chambers])
    quest("a_elemental_triad", "A", [trial_clear(3, rank=3, distinct=True)], 44, 580, 68,
          ("Tríade Elemental", "Conclua Provações de rank III ou maior em três elementos diferentes."),
          ("Elemental Triad", "Clear rank III or higher Trials in three different elements."), category="chamber")

    # ---------------- S ----------------
    quest("s_trial_domain", "S", [trial_clear(1, rank=6)], 80, 1050, 112,
          ("Domínio Absoluto", "Conclua uma Provação de rank VI ou maior."),
          ("Absolute Domain", "Clear a rank VI or higher Trial."), glyph=True, category="chamber")
    quest("s_trial_archmage", "S", [trial_clear(1, rank=7)], 100, 1400, 130,
          ("A Provação do Arquimago", "Conclua uma Provação de rank VII: o Guardião e o Avatar lutam juntos."),
          ("The Archmage's Trial", "Clear a rank VII Trial: the Guardian and the Avatar fight together."),
          glyph=True, glyph_count=2, category="chamber")
    quest("s_elemental_paragon", "S", [trial_clear(4, rank=5, distinct=True, absolute=True)], 120, 1600, 150,
          ("Paragão dos Elementos", "Tenha Provações de rank V ou maior concluídas em quatro elementos diferentes."),
          ("Paragon of the Elements", "Have rank V or higher Trials cleared in four different elements."),
          glyph=True, glyph_count=2, weight=6, repeatable=False, category="chamber")

    # ---------------- Provas de promoção nas Câmaras ----------------
    quest("trial_d_chamber", "D", [trial_clear(1)], 15, 150, 0,
          ("Prova do Rank D: A Câmara", "Conclua uma Provação Elemental de qualquer rank."),
          ("Rank D Trial: The Chamber", "Clear an Elemental Trial of any rank."), trial=True, category="trial")
    quest("trial_c_chamber", "C", [trial_clear(1, rank=2)], 25, 300, 0,
          ("Prova do Rank C: O Adepto", "Conclua uma Provação de rank II ou maior."),
          ("Rank C Trial: The Adept", "Clear a rank II or higher Trial."), trial=True, category="trial")
    quest("trial_b_chamber", "B", [trial_clear(1, rank=3)], 35, 450, 0,
          ("Prova do Rank B: Fluidez", "Conclua uma Provação de rank III ou maior."),
          ("Rank B Trial: Fluidity", "Clear a rank III or higher Trial."), trial=True, category="trial")
    quest("trial_a_chamber", "A", [trial_clear(1, rank=4)], 50, 700, 0,
          ("Prova do Rank A: Mestre Elemental", "Conclua uma Provação de rank IV ou maior."),
          ("Rank A Trial: Elemental Master", "Clear a rank IV or higher Trial."), trial=True, category="trial")
    quest("trial_s_chamber", "S", [trial_clear(1, rank=6)], 100, 1200, 0,
          ("Prova do Rank S: Domínio", "Conclua uma Provação de rank VI ou maior."),
          ("Rank S Trial: Domain", "Clear a rank VI or higher Trial."), trial=True, category="trial")


def place_translations():
    places = {
        "minecraft.the_nether": ("o Nether", "the Nether"),
        "minecraft.the_end": ("o Fim", "the End"),
        "isekai_mahou.elemental_plane": ("o Plano Elemental", "the Elemental Plane"),
        "isekai_mahou.abyss": ("o Abismo", "the Abyss"),
        "isekai_mahou.celestial_sanctuary": ("o Santuário Celestial", "the Celestial Sanctuary"),
        "isekai_mahou.twilight_rift": ("a Fenda do Crepúsculo", "the Twilight Rift"),
        "minecraft.mineshaft": ("uma mina abandonada", "an abandoned mineshaft"),
        "minecraft.desert_pyramid": ("uma pirâmide do deserto", "a desert pyramid"),
        "minecraft.jungle_pyramid": ("um templo da selva", "a jungle temple"),
        "minecraft.pillager_outpost": ("um posto avançado de saqueadores", "a pillager outpost"),
        "minecraft.fortress": ("uma fortaleza do Nether", "a Nether fortress"),
        "minecraft.monument": ("um monumento oceânico", "an ocean monument"),
        "minecraft.bastion_remnant": ("um bastião em ruínas", "a bastion remnant"),
        "minecraft.mansion": ("uma mansão da floresta", "a woodland mansion"),
        "minecraft.end_city": ("uma cidade do Fim", "an End city"),
        "minecraft.ancient_city": ("uma cidade antiga", "an ancient city"),
        "minecraft.trial_chambers": ("as câmaras do desafio", "the trial chambers"),
    }
    for k, (pt, en) in places.items():
        tr(f"guild.{NS}.place.{k}", pt, en)


# ------------------------------------------------------------------------------------------------
# Loja da Guilda (data/isekai_mahou/guild_shop)
# ------------------------------------------------------------------------------------------------
def shop():
    def it(rank, price, i, n=1, potion=None):
        return {"rank": rank, "price": price, "item": item(i, n, potion)}

    supplies = [
        it("F", 1, V("torch"), 16), it("F", 3, V("bread"), 6), it("F", 2, V("arrow"), 16), it("F", 3, V("cooked_beef"), 4),
        it("F", 4, V("potion"), 1, V("healing")),
        it("E", 5, V("potion"), 1, V("night_vision")), it("E", 6, V("iron_ingot"), 4), it("E", 6, V("shield")),
        it("E", 6, V("splash_potion"), 1, V("healing")),
        it("D", 6, V("potion"), 1, V("swiftness")), it("D", 8, V("potion"), 1, V("regeneration")),
        it("D", 8, V("potion"), 1, V("fire_resistance")), it("D", 8, V("ender_pearl"), 2),
        it("C", 10, V("potion"), 1, V("strength")), it("C", 10, V("experience_bottle"), 8), it("C", 14, V("golden_apple")),
        it("C", 15, V("diamond")),
        it("B", 6, V("potion"), 1, V("slow_falling")), it("B", 6, V("potion"), 1, V("water_breathing")),
        it("B", 60, V("totem_of_undying")),
        it("A", 50, V("netherite_scrap")), it("A", 90, V("enchanted_golden_apple")),
        it("S", 150, V("netherite_ingot")),
    ]
    magic = [
        it("F", 6, M("essence_arcane")), it("F", 8, M("mana_crystal")),
        it("E", 8, M("essence_fire")), it("E", 8, M("essence_water")), it("E", 8, M("essence_earth")), it("E", 8, M("essence_wind")),
        it("D", 10, M("essence_light")), it("D", 10, M("essence_shadow")), it("D", 20, M("mana_crystal"), 3),
    ]
    scrolls = [
        {"rank": "F", "price": 15, "glyph_tier": 1},
        {"rank": "D", "price": 30, "glyph_tier": 2},
        {"rank": "B", "price": 55, "glyph_tier": 3},
        {"rank": "A", "price": 100, "glyph_tier": 4},
        {"rank": "S", "price": 250, "glyph_tier": 5},
        # v0.7: Magia Lendária pronta e aleatória (de preferência uma que o Círculo do jogador já permite)
        {"rank": "S", "price": 300, "legend_scroll": True},
    ]
    write_json(os.path.join(D, "guild_shop/supplies.json"), {"offers": supplies})
    write_json(os.path.join(D, "guild_shop/magic.json"), {"offers": magic})
    write_json(os.path.join(D, "guild_shop/scrolls.json"), {"offers": scrolls})


# ------------------------------------------------------------------------------------------------
# Receitas, loot e tags
# ------------------------------------------------------------------------------------------------
def recipes():
    write_json(os.path.join(D, "recipe/quest_board.json"), {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["PPP", "WWW", "S S"],
        "key": {"P": {"item": V("paper")}, "W": {"tag": "minecraft:planks"}, "S": {"item": V("stick")}},
        "result": {"id": M("quest_board"), "count": 1}})
    write_json(os.path.join(D, "recipe/guild_counter.json"), {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["SSS", "PBP", "PPP"],
        "key": {"S": {"tag": "minecraft:wooden_slabs"}, "P": {"tag": "minecraft:planks"}, "B": {"item": V("book")}},
        "result": {"id": M("guild_counter"), "count": 2}})


def loot_tables():
    for bid in ("quest_board", "guild_counter"):
        write_json(os.path.join(D, f"loot_table/blocks/{bid}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": M(bid)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": M(f"blocks/{bid}")})


def merge_tags():
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/axe.json"), {"values": [M("quest_board"), M("guild_counter")]})


# ------------------------------------------------------------------------------------------------
# Traduções (pt_br principal + en_us)
# ------------------------------------------------------------------------------------------------
ADVENTURER_NAMES = [
    "Kazuo", "Aiko", "Renji", "Mira", "Tobias", "Lina", "Dorian", "Sakura", "Haruto", "Elise", "Garrick", "Yuna", "Bram",
    "Rin", "Cedric", "Nami", "Oskar", "Hana", "Leon", "Freya", "Jiro", "Vera", "Aldo", "Mei", "Rolf", "Kaede", "Theo",
    "Ilsa", "Daichi", "Noor", "Ewan", "Tomoe",
    # v0.8
    "Seraphine", "Kenta", "Odile", "Varek", "Yui", "Matthias", "Lys", "Gunnar", "Ayame", "Corwin", "Talia", "Ryuu", "Brielle",
    "Fenwick", "Sora", "Elowen",
]


def ui_translations():
    def t(k, pt, en):
        tr(k, pt, en)

    # ---- nomes ----
    t(f"item.{NS}.adventurer_card", "Cartão de Aventureiro", "Adventurer Card")
    t(f"item.{NS}.guild_coin", "Moeda da Guilda", "Guild Coin")
    t(f"block.{NS}.quest_board", "Quadro de Missões", "Quest Board")
    t(f"block.{NS}.guild_counter", "Balcão da Guilda", "Guild Counter")
    t(f"entity.{NS}.guild_receptionist", "Recepcionista da Guilda", "Guild Receptionist")
    t(f"entity.{NS}.guild_master", "Mestre da Guilda", "Guild Master")
    t(f"entity.{NS}.adventurer", "Aventureiro", "Adventurer")
    t(f"item.{NS}.guild_receptionist_spawn_egg", "Ovo de Recepcionista da Guilda", "Guild Receptionist Spawn Egg")
    t(f"item.{NS}.guild_master_spawn_egg", "Ovo de Mestre da Guilda", "Guild Master Spawn Egg")
    t(f"item.{NS}.adventurer_spawn_egg", "Ovo de Aventureiro", "Adventurer Spawn Egg")
    t(f"entity.{NS}.adventurer.named", "%s (Rank %s)", "%s (Rank %s)")
    for i, n in enumerate(ADVENTURER_NAMES):
        t(f"guild.{NS}.adventurer_name.{i}", n, n)

    # ---- itens ----
    t(f"item.{NS}.adventurer_card.blank", "Cartão em branco. Receba o seu com a recepcionista da Guilda.",
      "Blank card. Get yours from the Guild receptionist.")
    t(f"item.{NS}.adventurer_card.name", "Nome: %s", "Name: %s")
    t(f"item.{NS}.adventurer_card.rank", "Rank: %s", "Rank: %s")
    t(f"item.{NS}.adventurer_card.points", "Pontos de Guilda: %s", "Guild points: %s")
    t(f"item.{NS}.adventurer_card.points_next", "Pontos de Guilda: %s / %s", "Guild points: %s / %s")
    t(f"item.{NS}.adventurer_card.completed", "Missões concluídas: %s", "Quests completed: %s")
    t(f"item.{NS}.adventurer_card.hint", "Clique direito: abrir a aba da Guilda", "Right-click: open the Guild tab")
    t(f"item.{NS}.guild_coin.desc", "Aceita na loja da Guilda e por aventureiros.", "Accepted at the Guild shop and by adventurers.")

    # ---- ranks ----
    for r in RANKS:
        t(f"guild.{NS}.rank.{r}", r.upper(), r.upper())
    t(f"guild.{NS}.rank.none", "?", "?")
    t(f"guild.{NS}.rank_named", "Rank %s", "Rank %s")
    t(f"guild.{NS}.chat_line", "<%s> %s", "<%s> %s")
    t(f"guild.{NS}.random_scroll", "Pergaminho de Glifo aleatório (Tier %s)", "Random Glyph Scroll (Tier %s)")

    # ---- objetivos ----
    t(f"guild.{NS}.objective.kill", "Derrotar: %s", "Defeat: %s")
    t(f"guild.{NS}.objective.gather", "Entregar: %s", "Deliver: %s")
    t(f"guild.{NS}.objective.explore", "Explorar: %s", "Explore: %s")
    t(f"guild.{NS}.objective.counter", "Progresso: %s", "Progress: %s")
    t(f"guild.{NS}.objective.flag", "Concluir: %s", "Complete: %s")
    t(f"guild.{NS}.objective.and_more", "%s (+%s)", "%s (+%s)")

    # ---- mensagens ----
    m = f"message.{NS}.guild."
    t(m + "not_registered", "Você ainda não tem registro na Guilda. Fale com a recepcionista!",
      "You are not registered with the Guild yet. Talk to the receptionist!")
    t(m + "registered", "Registro concluído! Bem-vindo(a) à Guilda dos Aventureiros — Rank %s.",
      "Registration complete! Welcome to the Adventurers' Guild — Rank %s.")
    t(m + "registered_hint", "Pegue missões no quadro, entregue-as na Guilda e junte pontos para as provas de promoção.",
      "Take quests from the board, turn them in at the Guild and earn points for the promotion trials.")
    t(m + "no_coins", "Moedas da Guilda insuficientes (precisa de %s).", "Not enough Guild Coins (you need %s).")
    t(m + "card_reissued", "Novo Cartão de Aventureiro emitido.", "New Adventurer Card issued.")
    t(m + "card_bound", "O cartão agora tem o seu nome.", "The card now bears your name.")
    t(m + "card_blank", "Um cartão em branco. A recepcionista da Guilda pode registrá-lo.",
      "A blank card. The Guild receptionist can register you.")
    t(m + "card_other", "Este cartão pertence a %s.", "This card belongs to %s.")
    t(m + "counter_empty", "Não há ninguém atendendo neste balcão.", "Nobody is attending this counter.")
    t(m + "not_on_board", "Essa missão não está no quadro de hoje.", "That quest is not on today's board.")
    t(m + "already_taken", "Você já pegou essa missão hoje.", "You already took that quest today.")
    t(m + "already_done", "Você já concluiu essa missão.", "You already completed that quest.")
    t(m + "too_many", "Você já tem %s missões ativas. Entregue ou abandone uma.", "You already have %s active quests. Turn in or abandon one.")
    t(m + "accepted", "Missão aceita: %s", "Quest accepted: %s")
    t(m + "not_complete", "A missão ainda não foi concluída.", "The quest is not complete yet.")
    t(m + "completed", "Missão concluída: %s (+%s pontos de Guilda)", "Quest complete: %s (+%s Guild points)")
    t(m + "trial_unlocked", "Você já pode fazer a prova para o Rank %s! Fale com o Mestre da Guilda.",
      "You can now take the Rank %s trial! Talk to the Guild Master.")
    t(m + "abandoned", "Missão abandonada: %s", "Quest abandoned: %s")
    t(m + "trial_points", "Você precisa de %s pontos de Guilda para a prova do Rank %s.", "You need %s Guild points for the Rank %s trial.")
    t(m + "trial_none", "Não há nenhuma prova disponível neste mundo agora.", "There is no trial available in this world right now.")
    t(m + "trial_accepted", "Prova para o Rank %s aceita: %s", "Rank %s trial accepted: %s")
    t(m + "trial_abandoned", "Você desistiu da prova. Volte quando estiver pronto.", "You gave up the trial. Come back when you're ready.")
    t(m + "trial_ready", "Prova concluída: %s! Volte ao Mestre da Guilda para ser promovido.",
      "Trial complete: %s! Return to the Guild Master to be promoted.")
    t(m + "ready", "Missão pronta: %s! Volte à Guilda para receber a recompensa.", "Quest ready: %s! Return to the Guild for your reward.")
    t(m + "promoted_broadcast", "%s foi promovido(a) ao Rank %s da Guilda dos Aventureiros!",
      "%s has been promoted to Rank %s of the Adventurers' Guild!")
    t(m + "rank_required", "Esta oferta exige Rank %s.", "This offer requires Rank %s.")
    t(m + "glyph_known", "Você já conhece esse glifo.", "You already know that glyph.")
    t(m + "tier_known", "Você já conhece todos os glifos do tier %s.", "You already know every tier %s glyph.")
    t(m + "bought", "Comprado: %sx %s por %s moedas.", "Bought: %sx %s for %s coins.")
    t(m + "quest_gone", "A missão %s não existe mais e foi removida.", "The quest %s no longer exists and was removed.")
    t(m + "explored", "Local descoberto: %s", "Location discovered: %s")
    t(f"title.{NS}.guild.promoted", "Rank %s", "Rank %s")
    t(f"title.{NS}.guild.promoted_sub", "Promovido(a) pela Guilda dos Aventureiros", "Promoted by the Adventurers' Guild")

    # ---- comandos ----
    t(f"command.{NS}.guild.bad_rank", "Rank inválido. Use F, E, D, C, B, A ou S.", "Invalid rank. Use F, E, D, C, B, A or S.")
    t(f"command.{NS}.guild.rank", "%s agora é Rank %s na Guilda.", "%s is now Guild Rank %s.")
    t(f"command.{NS}.guild.points", "Pontos de Guilda: %s", "Guild points: %s")
    t(f"command.{NS}.guild.complete", "Objetivos das missões ativas concluídos (itens ainda precisam ser entregues).",
      "Active quest objectives completed (items still need to be delivered).")
    t(f"command.{NS}.guild.reset", "Dados da Guilda apagados.", "Guild data reset.")

    # ---- falas ----
    d = f"dialogue.{NS}."
    t(d + "receptionist.welcome", "Bem-vindo(a) à Guilda dos Aventureiros! Deseja se registrar?",
      "Welcome to the Adventurers' Guild! Would you like to register?")
    t(d + "receptionist.welcome_back", "Bom te ver, %s! Temos missões novas no quadro.", "Good to see you, %s! There are new quests on the board.")
    t(d + "receptionist.ready", "%s! Vejo que concluiu um pedido. Vamos registrar a entrega?", "%s! I see you finished a request. Shall we log it?")
    t(d + "receptionist.registered", "Pronto, %s! Aqui está o seu Cartão de Aventureiro. Rank F, por enquanto!",
      "All set, %s! Here's your Adventurer Card. Rank F, for now!")
    t(d + "receptionist.turn_in", "Excelente trabalho, %s! Aqui está a sua recompensa.", "Excellent work, %s! Here is your reward.")
    t(d + "receptionist.bought", "Obrigada pela compra! Use com sabedoria.", "Thank you for your purchase! Use it wisely.")
    amb = [("Missões novas aparecem no quadro todos os dias!", "New quests appear on the board every day!"),
           ("Lembre-se: no máximo três missões por vez.", "Remember: three quests at a time, at most."),
           ("O Mestre da Guilda aplica as provas de promoção.", "The Guild Master runs the promotion trials."),
           ("Precisa de poções? A loja da Guilda tem.", "Need potions? The Guild shop has them."),
           ("Ouvi dizer que o Rei Goblin voltou...", "I heard the Goblin King is back..."),
           ("Volte inteiro, está bem?", "Come back in one piece, okay?")]
    for i, (pt, en) in enumerate(amb):
        t(d + f"receptionist.ambient.{i}", pt, en)
    t(d + "master.unregistered", "Hm. Primeiro faça o registro com a recepcionista, novato.", "Hm. Register with the receptionist first, rookie.")
    t(d + "master.max_rank", "Rank S, %s. Não tenho mais nada a te ensinar.", "Rank S, %s. I have nothing left to teach you.")
    t(d + "master.trial_going", "A prova não vai se completar sozinha.", "The trial won't complete itself.")
    t(d + "master.ready", "Você está pronto para a prova do Rank %s.", "You are ready for the Rank %s trial.")
    t(d + "master.not_ready", "Ainda não. Junte mais pontos no quadro.", "Not yet. Earn more points on the board.")
    t(d + "master.trial_given", "Que assim seja. Volte vitorioso ou não volte.", "So be it. Return victorious or don't return.")
    t(d + "master.not_yet", "Você ainda não cumpriu a prova.", "You haven't fulfilled the trial yet.")
    t(d + "master.promoted", "De hoje em diante, você é Rank %s!", "From this day on, you are Rank %s!")
    amb = [("Na minha época, a gente caçava dragões com uma espada de madeira.", "In my day, we hunted dragons with a wooden sword."),
           ("Perdi este olho para uma harpia. Ela perdeu bem mais.", "I lost this eye to a harpy. She lost a lot more."),
           ("Um rank não se dá. Se conquista.", "A rank isn't given. It's earned."),
           ("Hmph. Os novatos de hoje...", "Hmph. Today's rookies..."),
           ("Treine. Coma. Durma. Repita.", "Train. Eat. Sleep. Repeat."),
           ("O Crepúsculo se aproxima. Sinto nos ossos.", "The Twilight approaches. I feel it in my bones.")]
    for i, (pt, en) in enumerate(amb):
        t(d + f"master.ambient.{i}", pt, en)
    t(d + "adventurer.busy", "Agora não, estou lutando!", "Not now, I'm fighting!")
    t(d + "adventurer.unregistered", "Você não tem cartão? Vá até a Guilda e se registre!", "No card? Go to the Guild and register!")
    t(d + "adventurer.respect", "Rank %s?! É uma honra te conhecer!", "Rank %s?! It's an honor to meet you!")
    t(d + "adventurer.senior", "Quando chegar ao Rank %s, a gente sai em missão juntos.", "When you reach Rank %s, we'll go on a quest together.")
    t(d + "adventurer.peer", "Rank %s também? Boa sorte nas missões!", "Rank %s too? Good luck with your quests!")
    for i, (pt, en) in enumerate([("A noite é perigosa. Fique perto da luz.", "The night is dangerous. Stay near the light."),
                                  ("Ouviu isso? Acho que tem zumbis por perto.", "Did you hear that? I think there are zombies nearby."),
                                  ("Vamos acampar aqui até o sol nascer.", "Let's camp here until sunrise.")]):
        t(d + f"adventurer.night.{i}", pt, en)
    variant_lines = {
        "warrior": [("Uma boa espada e um escudo firme. É tudo de que preciso.", "A good sword and a sturdy shield. That's all I need."),
                    ("Fique atrás de mim quando a luta começar!", "Stay behind me when the fight starts!"),
                    ("Esta cicatriz? Um hobgoblin. Ele está pior.", "This scar? A hobgoblin. He's worse off.")],
        "archer": [("Consigo acertar uma maçã a cinquenta passos.", "I can hit an apple at fifty paces."),
                   ("O vento está bom para atirar hoje.", "The wind is good for shooting today."),
                   ("Se vir penas de harpia, me avise.", "If you see harpy feathers, let me know.")],
        "mage": [("Estou estudando um glifo novo. Ainda explode às vezes.", "I'm studying a new glyph. It still explodes sometimes."),
                 ("Já escreveu o seu próprio cântico?", "Have you written your own chant yet?"),
                 ("Meditar perto de estantes acelera os estudos.", "Meditating near bookshelves speeds up your studies.")],
        "rogue": [("Não vi nada. Não ouvi nada.", "I saw nothing. I heard nothing."),
                  ("Baús que mordem... sempre confira duas vezes.", "Chests that bite... always check twice."),
                  ("Tem mercadoria? Eu tenho moedas.", "Got goods? I've got coins.")],
    }
    for v, lines in variant_lines.items():
        for i, (pt, en) in enumerate(lines):
            t(d + f"adventurer.{v}.{i}", pt, en)
    general = [("Dizem que existe um Plano Elemental além dos portais.", "They say there's an Elemental Plane beyond the portals."),
               ("Já viu um slime que fala? Nem eu. Ainda.", "Ever seen a talking slime? Me neither. Yet."),
               ("A recepcionista da Guilda é a pessoa mais assustadora que conheço.", "The Guild receptionist is the scariest person I know."),
               ("Subi de rank semana passada! Ainda não acredito.", "I ranked up last week! I still can't believe it."),
               ("Missão de coleta de ervas de novo... alguém tem que fazer.", "Herb gathering again... someone has to do it."),
               ("Os goblins estão mais organizados ultimamente.", "The goblins have been more organized lately."),
               ("Vou gastar todas as minhas moedas em poções.", "I'm spending all my coins on potions."),
               ("Já ouviu falar dos santuários elementais?", "Have you heard of the elemental shrines?"),
               ("Um dia ainda vou enfrentar um Guardião.", "One day I'll face a Guardian."),
               ("Cuidado com os cultistas. Eles conjuram de verdade.", "Beware the cultists. They really cast spells."),
               ("Sempre leve comida extra numa expedição.", "Always bring extra food on an expedition."),
               ("Isso aqui é melhor que a minha vida antiga, sabia?", "This beats my old life, you know?")]
    for i, (pt, en) in enumerate(general):
        t(d + f"adventurer.general.{i}", pt, en)
    for i, (pt, en) in enumerate([("Pela Guilda!", "For the Guild!"), ("Monstro à vista!", "Monster spotted!"),
                                  ("Cubram-me!", "Cover me!"), ("Hora do trabalho!", "Time to work!")]):
        t(d + f"adventurer.battle.{i}", pt, en)
    for i, (pt, en) in enumerate([("Negócio fechado!", "Pleasure doing business!"), ("Obrigado, colega!", "Thanks, friend!"),
                                  ("Volte sempre!", "Come back anytime!")]):
        t(d + f"adventurer.thanks.{i}", pt, en)

    # ---- tela da Guilda ----
    s = f"screen.{NS}.guild."
    for k, pt, en in [
        ("title", "Guilda dos Aventureiros", "Adventurers' Guild"),
        ("menu_tab", "Guilda", "Guild"),
        ("heading_reception", "Recepção da Guilda", "Guild Reception"),
        ("heading_board", "Quadro de Missões", "Quest Board"),
        ("heading_master", "Sala do Mestre da Guilda", "Guild Master's Office"),
        ("tab.reception", "Recepção", "Reception"),
        ("tab.board", "Quadro", "Board"),
        ("tab.active", "Ativas", "Active"),
        ("tab.shop", "Loja", "Shop"),
        ("tab.active_n", "Ativas (%s/%s)", "Active (%s/%s)"),
        ("welcome_title", "Bem-vindo(a) à Guilda dos Aventureiros!", "Welcome to the Adventurers' Guild!"),
        ("welcome_text", "Aqui aventureiros do mundo todo aceitam pedidos: caçar monstros, coletar materiais, explorar ruínas e "
                         "enfrentar chefes. Cada missão rende moedas, experiência, pergaminhos de glifo e pontos de Guilda.",
         "Here adventurers from all over the world take on requests: hunting monsters, gathering materials, exploring ruins "
         "and facing bosses. Every quest grants coins, experience, glyph scrolls and Guild points."),
        ("welcome_ranks", "Os ranks vão de F até S. Junte pontos e passe na prova do Mestre da Guilda para ser promovido. "
                          "Ranks altos liberam missões melhores e itens raros na loja.",
         "Ranks go from F to S. Earn points and pass the Guild Master's trial to be promoted. Higher ranks unlock better quests "
         "and rare items in the shop."),
        ("register", "Registrar-me como aventureiro(a)", "Register as an adventurer"),
        ("desk_title", "Atendimento", "Service desk"),
        ("desk_active", "Missões ativas: %s de %s.", "Active quests: %s of %s."),
        ("desk_ready", "Prontas para entregar: %s!", "Ready to turn in: %s!"),
        ("desk_trial", "Você já pode fazer a prova de promoção. Procure o Mestre da Guilda!",
         "You can take the promotion trial now. Look for the Guild Master!"),
        ("go_board", "Ver o quadro de missões", "See the quest board"),
        ("go_turn_in", "Entregar missões", "Turn in quests"),
        ("go_shop", "Loja da Guilda", "Guild shop"),
        ("reissue", "Emitir novo cartão (%s moedas)", "Issue a new card (%s coins)"),
        ("tip.0", "Dica: o quadro muda à meia-noite. Dormir também traz missões novas.",
         "Tip: the board changes at midnight. Sleeping also brings new quests."),
        ("tip.1", "Dica: missões de exploração contam assim que você pisa no lugar.",
         "Tip: exploration quests count as soon as you set foot in the place."),
        ("tip.2", "Dica: aventureiros pelo mundo compram restos de monstros por moedas.",
         "Tip: adventurers around the world buy monster drops for coins."),
        ("tip.3", "Dica: o seu cartão mostra rank, pontos e missões concluídas.",
         "Tip: your card shows your rank, points and completed quests."),
        ("tip.4", "Dica: abandonar uma missão não devolve a vaga no quadro de hoje.",
         "Tip: abandoning a quest does not give back its slot on today's board."),
        ("tip.5", "Dica: abra a aba Guilda no menu (B) para ver o progresso em qualquer lugar.",
         "Tip: open the Guild tab in the menu (B) to see your progress anywhere."),
        ("board_title", "Missões do dia", "Today's quests"),
        ("board_sub", "Renova à meia-noite", "Renews at midnight"),
        ("board_empty", "Nenhuma missão no quadro hoje. Volte amanhã!", "No quests on the board today. Come back tomorrow!"),
        ("accept", "Aceitar", "Accept"),
        ("full", "Lotado", "Full"),
        ("turn_in", "Entregar", "Turn in"),
        ("abandon", "Abandonar", "Abandon"),
        ("give_up", "Desistir", "Give up"),
        ("confirm", "Confirmar?", "Confirm?"),
        ("state_active", "Em andamento", "In progress"),
        ("state_ready", "Pronta!", "Ready!"),
        ("state_taken", "Já pega hoje", "Taken today"),
        ("state_locked", "Exige Rank %s", "Requires Rank %s"),
        ("state_unregistered", "Registre-se", "Register first"),
        ("state_trial", "Prova", "Trial"),
        ("category.kill", "Caça", "Hunt"),
        ("category.gather", "Coleta", "Gathering"),
        ("category.explore", "Exploração", "Exploration"),
        ("category.task", "Tarefa", "Task"),
        ("category.boss", "Chefe", "Boss"),
        ("category.trial", "Prova", "Trial"),
        ("active_title", "Missões ativas (%s/%s)", "Active quests (%s/%s)"),
        ("active_empty", "Nenhuma missão ativa. Pegue missões no quadro da Guilda.", "No active quests. Take some from the Guild board."),
        ("shop_title", "Loja da Guilda", "Guild Shop"),
        ("shop_sub", "Shift+clique: comprar 5", "Shift+click: buy 5"),
        ("shop_empty", "A loja está fechada para balanço.", "The shop is closed for inventory."),
        ("buy", "Comprar", "Buy"),
        ("locked", "Bloqueado", "Locked"),
        ("buy_hint", "Shift+clique compra 5 de uma vez (itens comuns).", "Shift+click buys 5 at once (regular items)."),
        ("master_title", "Promoção de Rank", "Rank Promotion"),
        ("master_unregistered", "O Mestre da Guilda só recebe aventureiros registrados. Fale com a recepcionista primeiro.",
         "The Guild Master only receives registered adventurers. Talk to the receptionist first."),
        ("master_max", "Você chegou ao topo: Rank S. As lendas falam de você agora.",
         "You've reached the top: Rank S. The legends speak of you now."),
        ("master_trial_going", "Prova em andamento para o Rank %s. Cumpra o objetivo e volte para relatar.",
         "Trial in progress for Rank %s. Complete the objective and come back to report."),
        ("master_offer", "Você tem pontos suficientes. Aceite a prova para chegar ao Rank %s:",
         "You have enough points. Accept the trial to reach Rank %s:"),
        ("master_need", "Faltam %s pontos de Guilda para a prova do Rank %s. Esta é a prova que te espera:",
         "You need %s more Guild points for the Rank %s trial. This is the trial that awaits you:"),
        ("master_no_trial", "Não há prova disponível neste mundo para o próximo rank.", "There is no trial available in this world for the next rank."),
        ("trial_accept", "Aceitar prova", "Accept trial"),
        ("trial_report", "Relatar", "Report"),
        ("rewards", "Recompensas:", "Rewards:"),
        ("reward_coins", "%s Moedas da Guilda", "%s Guild Coins"),
        ("reward_xp", "%s de XP mágico", "%s magic XP"),
        ("reward_points", "%s pontos de Guilda", "%s Guild points"),
        ("card_title", "Cartão de Aventureiro", "Adventurer Card"),
        ("card_rank", "Rank: %s", "Rank: %s"),
        ("card_completed", "Missões concluídas: %s", "Quests completed: %s"),
        ("card_points", "Pontos: %s / %s", "Points: %s / %s"),
        ("card_points_max", "Pontos: %s", "Points: %s"),
        ("card_next", "Próximo: prova do Rank %s", "Next: Rank %s trial"),
        ("card_trial_ready", "Prova do Rank %s liberada!", "Rank %s trial unlocked!"),
        ("card_trial_going", "Prova em andamento", "Trial in progress"),
        ("card_max", "O auge da Guilda", "The Guild's finest"),
        ("tab_hint", "Pegue e entregue missões no Quadro de Missões ou com a recepcionista. As provas de promoção são com o "
                     "Mestre da Guilda.",
         "Take and turn in quests at the Quest Board or with the receptionist. Promotion trials are with the Guild Master."),
        ("tab_unregistered_title", "Sem registro na Guilda", "Not registered with the Guild"),
        ("tab_unregistered", "Procure a Recepcionista da Guilda dos Aventureiros (na vila da Guilda, ou atrás de um Balcão da Guilda) "
                             "para receber o seu Cartão de Aventureiro e começar no Rank F.",
         "Find the Adventurers' Guild Receptionist (in the Guild village, or behind a Guild Counter) to receive your Adventurer "
         "Card and start at Rank F."),
    ]:
        t(s + k, pt, en)


def v07_translations():
    """Textos da v0.7: objetivo trial_clear, rastreador de objetivos, presentes de promoção, toasts e loja lendária."""
    g = f"guild.{NS}."
    for k, pt, en in [
        ("objective.trial_clear", "Concluir: %s", "Clear: %s"),
        ("trial_subject.any", "uma Provação Elemental", "an Elemental Trial"),
        ("trial_subject.rank", "uma Provação de rank %s", "a rank %s Trial"),
        ("trial_subject.element", "uma Provação de %s", "a %s Trial"),
        ("trial_subject.element_rank", "uma Provação de %s (rank %s)", "a %s Trial (rank %s)"),
        ("trial_subject.distinct", "Provações de elementos diferentes", "Trials of different elements"),
        ("trial_subject.distinct_rank", "Provações rank %s de elementos diferentes", "Rank %s Trials of different elements"),
        ("random_legend", "Pergaminho Lendário aleatório", "Random Legendary Scroll"),
        ("tracker.trial", "Prova do Rank %s", "Rank %s Trial"),
        ("tracker.trial_report", "✔ Concluída! Relate ao Mestre da Guilda", "✔ Done! Report to the Guild Master"),
        ("tracker.trial_unlocked", "Prova do Rank %s liberada!", "Rank %s trial unlocked!"),
        ("tracker.trial_unlocked_hint", "Fale com o Mestre da Guilda", "Talk to the Guild Master"),
        ("tracker.ready", "✔ Pronta! Entregue no Quadro ou na Recepção", "✔ Ready! Turn in at the Board or Reception"),
        ("tracker.ready_many", "%s missões da Guilda prontas", "%s Guild quests ready"),
        ("tracker.step", "[%s/%s] %s", "[%s/%s] %s"),
        ("tracker.next_rank", "Guilda: rumo ao Rank %s", "Guild: road to Rank %s"),
        ("tracker.next_rank_hint", "Pegue missões no Quadro (pontos da Guilda)", "Take quests at the Board (Guild points)"),
    ]:
        tr(g + k, pt, en)
    m = f"message.{NS}.guild."
    for k, pt, en in [
        ("promotion_gift", "Presente da Guilda pela promoção: %s", "The Guild's promotion gift: %s"),
        ("legend_none", "Nenhuma Magia Lendária disponível neste mundo.", "No Legendary Spell is available in this world."),
    ]:
        tr(m + k, pt, en)
    t = f"toast.{NS}.guild."
    for k, pt, en in [
        ("promoted", "Promovido(a) ao Rank %s!", "Promoted to Rank %s!"),
        ("promoted_sub", "Guilda dos Aventureiros", "Adventurers' Guild"),
        ("gift", "Presente da Guilda: %s", "Guild gift: %s"),
        ("quest_done", "Missão concluída! +%s pontos", "Quest complete! +%s points"),
        ("quest_ready", "Missão pronta para entregar", "Quest ready to turn in"),
        ("trial_ready", "Prova concluída! Fale com o Mestre", "Trial complete! See the Master"),
        ("legend_bought", "Pergaminho Lendário!", "Legendary Scroll!"),
    ]:
        tr(t + k, pt, en)
    s = f"screen.{NS}.guild."
    for k, pt, en in [
        ("category.chamber", "Provação", "Chamber"),
        ("master_daily", "O Mestre escolhe uma prova diferente a cada dia. Se esta não for para você, volte amanhã.",
         "The Master picks a different trial each day. If this one isn't for you, come back tomorrow."),
        ("tip.6", "Dica: a prova de promoção muda a cada dia. Não gostou da de hoje? Volte amanhã.",
         "Tip: the promotion trial changes every day. Don't like today's? Come back tomorrow."),
        ("tip.7", "Dica: as Provações Elementais (aba Caminhos, menu B) contam para as missões de Câmara da Guilda.",
         "Tip: Elemental Trials (Paths tab, B menu) count toward the Guild's Chamber quests."),
    ]:
        tr(s + k, pt, en)


# ================================================================================================
# v0.8 — Quadros de Facção, clérigo, contratos, missões de facção, loja por patamar e o capítulo do Diário
# ================================================================================================
FACTIONS = ["guild", "kingdom", "academy", "church", "underworld"]
# cor do estandarte, cor do brasão, cor de detalhe, brasão 6x6 ('#' brasão, '+' detalhe)
FACTION_STYLE = {
    "guild": ("7A3E14", "E8B84A", "F4F4F4", ["+....+", ".+..+.", "..++..", "..##..", ".#..#.", "#....#"]),
    "kingdom": ("23418F", "E8C24A", "C9433A", ["#.##.#", "#.##.#", "######", "#+##+#", "######", "......"]),
    "academy": ("4B2A7A", "D9C3FF", "FFE38A", ["..++..", "..++..", "######", ".####.", ".#..#.", "#....#"]),
    "church": ("E2D6B4", "E8A82A", "FFF3B0", ["#.##.#", ".####.", "##++##", "##++##", ".####.", "#.##.#"]),
    "underworld": ("3A1218", "C9C2B8", "8A2B3A", [".####.", "######", "#+##+#", "######", ".#..#.", ".####."]),
}


def faction_board_front(f):
    banner, emblem, accent, rows = FACTION_STYLE[f]
    banner, emblem, accent = hex_rgba(banner), hex_rgba(emblem), hex_rgba(accent)
    c = Canvas(16, 16)
    rnd = random.Random(70 + FACTIONS.index(f))
    for y in range(16):
        for x in range(16):
            c.set(x, y, jitter(hex_rgba("8B6A45"), rnd, 0.08))
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            c.set(x, y, WOOD1)
    for i in range(1, 15):
        for (x, y) in ((i, 1), (1, i)):
            c.set(x, y, WOOD3)
        for (x, y) in ((i, 14), (14, i)):
            c.set(x, y, WOOD2)
    # cantoneiras na cor da facção
    for (x, y) in ((0, 0), (1, 0), (0, 1), (15, 0), (14, 0), (15, 1), (0, 11), (15, 11)):
        c.set(x, y, lighten(banner, 0.15))
    # estandarte (colunas 5..10, linhas 1..7) com o brasão e a ponta recortada
    for y in range(1, 8):
        for x in range(5, 11):
            col = jitter(banner, rnd, 0.05)
            if x in (5, 10):
                col = darken(banner, 0.25)
            if y == 7 and x in (7, 8):
                col = jitter(hex_rgba("8B6A45"), rnd, 0.08)
            c.set(x, y, col)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                c.set(5 + i, 1 + j, emblem if (i + j) % 3 else lighten(emblem, 0.25))
            elif ch == "+":
                c.set(5 + i, 1 + j, accent)
    # cartazes da facção (com selo da cor) em baixo
    note(c, 2, 8, 6, 11, PAPER, banner, 80 + FACTIONS.index(f))
    note(c, 10, 8, 13, 11, PAPER2, accent, 90 + FACTIONS.index(f))
    note(c, 2, 2, 4, 6, PAPER, banner, 95 + FACTIONS.index(f), sketch=True)
    note(c, 12, 3, 13, 6, hex_rgba("D9E6C8"), GOLD2, 99 + FACTIONS.index(f))
    save(c, f"block/faction_board_{f}_front")


def faction_board_models():
    for f in FACTIONS:
        write_json(os.path.join(A, f"models/block/faction_board_{f}.json"), {
            "parent": f"{NS}:block/quest_board",
            "textures": {
                "particle": f"{NS}:block/quest_board_side",
                "front": f"{NS}:block/faction_board_{f}_front",
                "side": f"{NS}:block/quest_board_side",
                "paper": f"{NS}:block/quest_board_paper",
            },
            "elements": [
                {"from": [1, 0, 6.5], "to": [3, 16, 9.5], "faces": faces_all("#side")},
                {"from": [13, 0, 6.5], "to": [15, 16, 9.5], "faces": faces_all("#side")},
                {"from": [0, 4, 7], "to": [16, 16, 9], "faces": {
                    "north": {"texture": "#front", "uv": [0, 0, 16, 12]},
                    "south": {"texture": "#side", "uv": [0, 0, 16, 12]},
                    "east": {"texture": "#side", "uv": [7, 0, 9, 12]},
                    "west": {"texture": "#side", "uv": [7, 0, 9, 12]},
                    "up": {"texture": "#side", "uv": [0, 7, 16, 9]},
                    "down": {"texture": "#side", "uv": [0, 7, 16, 9]}}},
                {"from": [-0.5, 15.5, 6], "to": [16.5, 16.5, 10], "faces": faces_all("#side", [0, 0, 16, 4])},
                # estandarte com o brasão, em relevo
                {"from": [5, 9, 6.5], "to": [11, 15, 7], "faces": {
                    "north": {"texture": "#front", "uv": [5, 1, 11, 7]},
                    "east": {"texture": "#front", "uv": [10, 1, 11, 7]},
                    "west": {"texture": "#front", "uv": [5, 1, 6, 7]},
                    "up": {"texture": "#front", "uv": [5, 1, 11, 2]},
                    "down": {"texture": "#front", "uv": [5, 6, 11, 7]}}},
                # varão dourado do estandarte
                {"from": [4.5, 15, 6.3], "to": [11.5, 15.5, 6.8], "faces": faces_all("#paper", [12, 12, 14, 13])},
            ],
        })
        write_json(os.path.join(A, f"models/item/faction_board_{f}.json"), {"parent": f"{NS}:block/faction_board_{f}"})
    variants = {}
    for f in FACTIONS:
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"{NS}:block/faction_board_{f}"}
            if y:
                v["y"] = y
            variants[f"facing={facing},faction={f}"] = v
    write_json(os.path.join(A, "blockstates/faction_board.json"), {"variants": variants})
    # Item: o modelo segue a facção do componente block_state (propriedade "isekai_mahou:faction" registrada no cliente).
    write_json(os.path.join(A, "models/item/faction_board.json"), {
        "parent": f"{NS}:block/faction_board_guild",
        "overrides": [{"predicate": {f"{NS}:faction": round(i / 10.0 - 0.05, 2)}, "model": f"{NS}:item/faction_board_{f}"}
                      for i, f in enumerate(FACTIONS) if i > 0],
    })


def faction_board_data():
    write_json(os.path.join(D, "loot_table/blocks/faction_board.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": M("faction_board"),
                                            "functions": [{"function": "minecraft:copy_state", "block": M("faction_board"),
                                                           "properties": ["faction"]}]}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": M("blocks/faction_board")})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/axe.json"),
               {"values": [M("quest_board"), M("guild_counter"), M("faction_board")]})
    extra = {"guild": [V("gold_ingot")], "kingdom": [V("iron_ingot"), V("blue_dye")],
             "academy": [V("amethyst_shard"), V("purple_dye")], "church": [V("gold_nugget"), V("white_dye")],
             "underworld": [V("ink_sac"), V("red_dye")]}
    for f in FACTIONS:
        result = {"id": M("faction_board"), "count": 1}
        if f != "guild":
            result["components"] = {"minecraft:block_state": {"faction": f}}
        write_json(os.path.join(D, f"recipe/faction_board_{f}.json"), {
            "type": "minecraft:crafting_shapeless", "category": "building",
            "ingredients": [{"item": M("quest_board")}] + [{"item": i} for i in extra[f]],
            "result": result})


# ---------------------------------------------------------------- missões de facção
FACTION_REWARDS = {  # rank -> (moedas, xp, pontos)
    "F": (4, 40, 6), "E": (6, 70, 10), "D": (10, 120, 16), "C": (16, 200, 24), "B": (25, 320, 34), "A": (40, 500, 50),
    "S": (70, 900, 80)}


# Missões que dependem de outros módulos somem do quadro sem eles ("requires": item/bloco/entidade/glifo).
CITY = M("city_heart")
COUNTERSPELL = M("counterspell")


def fquest(qid, faction, standing, rank, objectives, rep, pt, en, glyph=False, items=None, category=None, weight=10,
           repeatable=True, coins=None, xp=None, requires=None):
    """Missão de um Quadro de Facção (Reino, Academia, Igreja, Submundo): liberada pelo patamar, sem pontos de rank."""
    c, x, _ = FACTION_REWARDS[rank]
    quest(qid, rank, objectives, c if coins is None else coins, x if xp is None else xp, 0, pt, en, glyph=glyph, items=items,
          category=category, weight=weight, repeatable=repeatable, requires=requires or needs(objectives))
    data = QUESTS[-1][1]
    data["faction"] = faction
    if standing != "neutral":
        data["standing"] = standing
    data["rewards"]["reputation"] = rep


def needs(objectives):
    """Contadores que só existem com outro módulo: cidades (Places) e contramágica (arcane_duel)."""
    req = []
    for o in objectives:
        key = o.get("key", "")
        if key in ("guild.kill_city", "guild.kill_horde_city", "visit.city", "visit.capital") and CITY not in req:
            req.append(CITY)
        if key.startswith("counter.") and COUNTERSPELL not in req:
            req.append(COUNTERSPELL)
    return req or None


def gquest(qid, rank, objectives, pt, en, rep=None, glyph=False, items=None, category=None, weight=10, repeatable=True):
    """Missão da Guilda (v0.8): pontos e moedas pelo rank; + Guilda automático; {@code rep} soma outras facções."""
    c, x, p = FACTION_REWARDS[rank]
    quest(qid, rank, objectives, c, x, p, pt, en, glyph=glyph, items=items, category=category, weight=weight,
          repeatable=repeatable, requires=needs(objectives))
    if rep:
        QUESTS[-1][1]["rewards"]["reputation"] = rep


def v08_quests():
    t_city = lbl("target.v08.city_monsters", "monstros dentro da cidade", "monsters inside the city")
    t_horde_city = lbl("target.v08.horde_city", "a Horda perto das cidades", "the Horde near cities")
    t_horde_forest = lbl("target.v08.horde_forest", "a Horda nas florestas", "the Horde in forests")
    t_undead = lbl("target.v08.undead", "mortos-vivos", "undead")
    t_night = lbl("target.v08.night", "monstros abatidos à noite", "monsters slain at night")
    t_plant = lbl("target.v08.plant", "mudas plantadas", "saplings planted")
    t_reactions = lbl("target.v08.reactions", "reações elementais", "elemental reactions")
    t_electrocute = lbl("target.v08.electrocute", "Eletrocutar", "Electrocute")
    t_melt = lbl("target.v08.melt", "Derreter", "Melt")
    t_freeze = lbl("target.v08.freeze", "Congelar", "Freeze")
    t_overload = lbl("target.v08.overload", "Sobrecarga", "Overload")
    t_counter = lbl("target.v08.counter", "contramágicas bem-sucedidas", "successful counterspells")
    t_new_city = lbl("target.v08.new_city", "uma cidade nova", "a new city")
    t_capital = lbl("target.v08.capital", "a Capital, Aldória", "the Capital, Aldória")
    t_companion = lbl("target.v08.companion_kills", "abates dos seus companheiros", "kills by your companions")
    t_hired = lbl("target.v08.hired", "aventureiros contratados", "adventurers hired")
    t_legend = lbl("target.v08.legend", "uma Magia Lendária conjurada", "a Legendary Spell cast")
    t_light_kills = lbl("target.v08.light_kills", "abates com magia de Luz", "kills with Light magic")
    t_orcs = lbl("target.v08.orcs", "Orcs", "Orcs")
    t_raiders = lbl("target.v08.raiders", "Saqueadores e Vingadores", "Pillagers and Vindicators")
    t_witches = lbl("target.v08.witches", "Bruxas e Cultistas", "Witches and Cultists")
    cast_label = {e: lbl(f"target.v08.cast_{e}", f"magias de {pt}", f"{en} spells") for e, pt, en in (
        ("fire", "Fogo", "Fire"), ("water", "Água", "Water"), ("earth", "Terra", "Earth"), ("wind", "Vento", "Wind"),
        ("shadow", "Sombra", "Shadow"))}
    coin = M("guild_coin")

    # ----------------------------------------------------------------- Reino de Aldória (kingdom)
    K = "kingdom"
    fquest("kingdom_city_watch", K, "unfriendly", "F", [counter("guild.kill_city", 6, t_city, V("iron_sword"))], {K: 14},
           ("Ronda da Cidade", "A guarda está sobrecarregada. Limpe os monstros que entram nas muralhas — e recupere a confiança do Reino."),
           ("City Watch", "The guard is stretched thin. Clear the monsters that get inside the walls — and win back the Kingdom's trust."),
           category="defense")
    fquest("kingdom_wall_defense", K, "neutral", "E", [counter("guild.kill_city", 15, t_city, V("shield"))], {K: 18},
           ("Defesa das Muralhas", "Monstros rondam os portões toda noite. Proteja os cidadãos dentro da cidade."),
           ("Wall Defense", "Monsters prowl the gates every night. Protect the citizens inside the city."), category="defense")
    fquest("kingdom_horde_raiders", K, "neutral", "E", [counter("guild.kill_horde_city", 8, t_horde_city, M("goblin_ear"))], {K: 20},
           ("Saqueadores nos Arredores", "Bandos da Horda atacam as fazendas perto das cidades. Expulse-os."),
           ("Raiders on the Outskirts", "Horde bands raid the farms near the cities. Drive them off."), category="hunt")
    fquest("kingdom_bread_supply", K, "neutral", "F", [gather(V("bread"), 24)], {K: 10},
           ("Pão para o Quartel", "Os soldados não lutam de barriga vazia. Entregue pão ao intendente."),
           ("Bread for the Barracks", "Soldiers don't fight on empty stomachs. Deliver bread to the quartermaster."), category="delivery")
    fquest("kingdom_iron_supply", K, "neutral", "E", [gather(V("iron_ingot"), 12)], {K: 15},
           ("Ferro para a Forja Real", "A forja real precisa de ferro para as novas lanças da guarda."),
           ("Iron for the Royal Forge", "The royal forge needs iron for the guard's new spears."), category="delivery")
    fquest("kingdom_arrow_supply", K, "neutral", "F", [gather(V("arrow"), 48)], {K: 10},
           ("Flechas para as Torres", "As torres da muralha estão quase sem flechas."),
           ("Arrows for the Towers", "The wall towers are almost out of arrows."), category="delivery")
    fquest("kingdom_night_patrol", K, "neutral", "E", [counter("guild.kill_night", 12, t_night, V("lantern"))], {K: 15},
           ("Patrulha Noturna", "Acompanhe a patrulha da noite e abata o que sair da escuridão."),
           ("Night Patrol", "Join the night patrol and slay whatever comes out of the dark."), category="defense")
    fquest("kingdom_royal_messenger", K, "friendly", "E", [counter("visit.city", 1, t_new_city, V("filled_map"))], {K: 22},
           ("Mensageiro Real", "Leve uma carta do Lorde a uma cidade que você ainda não visitou."),
           ("Royal Messenger", "Carry a letter from the Lord to a city you haven't visited yet."), category="explore",
           items=[item(V("map"), 1)])
    fquest("kingdom_hobgoblin_captains", K, "friendly", "D", [kill(M("hobgoblin"), 4)], {K: 25},
           ("Capitães Hobgoblins", "Os hobgoblins comandam os ataques da Horda. Corte a cabeça da serpente."),
           ("Hobgoblin Captains", "Hobgoblins lead the Horde's raids. Cut off the serpent's head."), category="hunt")
    fquest("kingdom_orc_warband", K, "friendly", "C", [kill([M("orc"), M("orc_berserker")], 10, t_orcs)], {K: 30},
           ("Bando de Guerra Orc", "Um bando orc marcha para a fronteira. Detenha-o antes que alcance as aldeias."),
           ("Orc Warband", "An orc warband marches on the border. Stop it before it reaches the villages."), category="hunt", glyph=True)
    fquest("kingdom_outpost_assault", K, "honored", "C", [explore(structure=V("pillager_outpost")), kill(V("pillager"), 10)], {K: 45},
           ("Assalto ao Posto Avançado", "Os saqueadores ameaçam as estradas do Reino. Derrube o posto deles."),
           ("Outpost Assault", "Illagers threaten the Kingdom's roads. Bring down their outpost."), category="hunt")
    fquest("kingdom_raid_breakers", K, "honored", "B", [kill([V("pillager"), V("vindicator")], 15, t_raiders)], {K: 50},
           ("Quebra-Invasões", "Os saqueadores estão se organizando. Quebre a força deles antes da invasão."),
           ("Raid Breakers", "The illagers are organizing. Break their strength before the raid."), category="defense",
           items=[item(V("golden_apple"), 1)])
    fquest("kingdom_goblin_king", K, "honored", "C", [kill(M("goblin_king"), 1)], {K: 60},
           ("Por Ordem do Lorde: o Rei Goblin", "O Lorde exige a cabeça do Rei Goblin. A recompensa é generosa."),
           ("By the Lord's Order: the Goblin King", "The Lord demands the Goblin King's head. The reward is generous."),
           category="boss", glyph=True, coins=30)
    fquest("kingdom_orc_lord", K, "revered", "B", [kill(M("orc_lord"), 1)], {K: 80},
           ("Por Ordem do Lorde: o Lorde Orc", "O Lorde Orc ousou reivindicar terras do Reino. Mostre a ele o erro."),
           ("By the Lord's Order: the Orc Lord", "The Orc Lord dared to claim the Kingdom's lands. Show him his mistake."),
           category="boss", glyph=True, coins=45, items=[item(V("diamond"), 3)])
    fquest("kingdom_heroes_of_aldoria", K, "exalted", "A", [counter("guild.kill_horde_city", 40, t_horde_city, M("goblin_ear")),
                                                       kill([M("goblin_king"), M("orc_lord")], 1, lbl("target.v08.horde_lords",
                                                                                                        "um senhor da Horda", "a Horde lord"))],
           {K: 100}, ("Heróis de Aldória", "A Horda precisa aprender a temer o nome do Reino. Lidere o contra-ataque."),
           ("Heroes of Aldória", "The Horde must learn to fear the Kingdom's name. Lead the counterattack."), category="boss",
           glyph=True, items=[item(V("enchanted_golden_apple"), 1)])

    # ----------------------------------------------------------------- Academia Arcana (academy)
    Ac = "academy"
    for i, (e, rank) in enumerate((("fire", "F"), ("water", "F"), ("earth", "E"), ("wind", "E"))):
        names = {"fire": ("Fogo", "Fire"), "water": ("Água", "Water"), "earth": ("Terra", "Earth"), "wind": ("Vento", "Wind")}[e]
        fquest(f"academy_study_{e}", Ac, "neutral", rank, [counter(f"cast.{e}", 20, cast_label[e], M(f"essence_{e}"))], {Ac: 10},
               (f"Estudo Prático: {names[0]}", f"A Academia quer dados de campo sobre glifos de {names[0]}. Conjure e anote."),
               (f"Field Study: {names[1]}", f"The Academy wants field data on {names[1]} glyphs. Cast and take notes."),
               category="study")
    fquest("academy_reaction_lab", Ac, "neutral", "E", [counter("reaction.any", 10, t_reactions, V("brewing_stand"))], {Ac: 15},
           ("Laboratório de Reações", "Provoque reações elementais (Molhado + Raio, Em Chamas + Gelo...) para a pesquisa da Academia."),
           ("Reaction Lab", "Trigger elemental reactions (Wet + Lightning, Burning + Ice...) for the Academy's research."), category="study")
    fquest("academy_mana_crystals", Ac, "neutral", "E", [gather(M("mana_crystal"), 6)], {Ac: 15},
           ("Cristais para os Catalisadores", "Os catalisadores da torre se esgotaram. Entregue Cristais de Mana."),
           ("Crystals for the Catalysts", "The tower's catalysts are drained. Deliver Mana Crystals."), category="delivery")
    fquest("academy_electrocute", Ac, "friendly", "D", [counter("reaction.electrocute", 5, t_electrocute, V("lightning_rod"))], {Ac: 22},
           ("Tese: Condutividade", "Molhe os alvos e então use Raio. Documente cinco Eletrocuções."),
           ("Thesis: Conductivity", "Soak your targets, then strike with Lightning. Record five Electrocutions."), category="study")
    fquest("academy_thermal_shock", Ac, "friendly", "D", [counter("reaction.melt", 3, t_melt, V("magma_cream")),
                                                     counter("reaction.freeze", 3, t_freeze, V("blue_ice"))], {Ac: 25},
           ("Choque Térmico", "Derreta e congele inimigos para a cátedra de termodinâmica arcana."),
           ("Thermal Shock", "Melt and freeze enemies for the arcane thermodynamics chair."), category="study", glyph=True)
    fquest("academy_essence_research", Ac, "friendly", "D", [gather(M("essence_fire"), 2), gather(M("essence_water"), 2),
                                                        gather(M("essence_earth"), 2), gather(M("essence_wind"), 2)], {Ac: 25},
           ("Pesquisa das Essências", "Uma amostra de cada essência base para o catálogo da biblioteca."),
           ("Essence Research", "A sample of each base essence for the library catalogue."), category="delivery", glyph=True)
    fquest("academy_trial_exam", Ac, "friendly", "C", [trial_clear(1, rank=2)], {Ac: 30},
           ("Exame de Provação", "Prove o seu domínio numa Provação Elemental de rank II ou maior."),
           ("Trial Exam", "Prove your mastery in an Elemental Trial of rank II or higher."), category="chamber")
    fquest("academy_overload", Ac, "honored", "B", [counter("reaction.overload", 4, t_overload, V("tnt")),
                                               counter("reaction.any", 25, t_reactions, V("brewing_stand"))], {Ac: 40},
           ("Sobrecarga Controlada", "Em Chamas + Raio sem explodir a torre. Quase sempre."),
           ("Controlled Overload", "Burning + Lightning without blowing up the tower. Mostly."), category="study", glyph=True)
    fquest("academy_counterspell_thesis", Ac, "honored", "B", [counter("counter.success", 3, t_counter, V("echo_shard"))], {Ac: 45},
           ("Tese: a Pilha", "Responda a três conjurações inimigas com Contramágica. A banca quer ver a pilha desmoronar."),
           ("Thesis: the Stack", "Answer three enemy castings with a Counterspell. The board wants to see the stack collapse."),
           category="study", glyph=True)
    fquest("academy_trial_master", Ac, "revered", "A", [trial_clear(3, rank=3, distinct=True)], {Ac: 60},
           ("Mestre das Provações", "Provações de rank III+ em três elementos diferentes. Um feito digno de cátedra."),
           ("Master of Trials", "Rank III+ Trials in three different elements. A feat worthy of a professorship."),
           category="chamber", glyph=True)
    fquest("academy_legend_study", Ac, "exalted", "S", [counter("spell.legend", 1, t_legend, M("spell_scroll"))], {Ac: 100},
           ("Arquivo das Lendas", "Conjure uma Magia Lendária diante dos arquivistas. A Academia registrará cada runa."),
           ("Archive of Legends", "Cast a Legendary Spell before the archivists. The Academy will record every rune."),
           category="study", glyph=True, items=[item(M("mana_crystal"), 8)])

    # ----------------------------------------------------------------- Igreja da Aurora (church)
    Ch = "church"
    fquest("church_rest_for_the_dead", Ch, "unfriendly", "F", [counter("guild.kill_undead", 10, t_undead, V("bone"))], {Ch: 14},
           ("Penitência: Descanso aos Mortos", "Os mortos não descansam. Ajude-os — e a Aurora talvez perdoe você."),
           ("Penance: Rest for the Dead", "The dead cannot rest. Help them — and the Dawn may forgive you."), category="faith")
    fquest("church_undead_purge", Ch, "neutral", "E", [counter("guild.kill_undead", 25, t_undead, V("bone"))], {Ch: 18},
           ("Expurgo dos Mortos-Vivos", "Cemitérios e ruínas estão infestados. Devolva os mortos à terra."),
           ("Undead Purge", "Graveyards and ruins are infested. Return the dead to the earth."), category="faith")
    fquest("church_light_rites", Ch, "neutral", "F", [counter("kill.spell.light", 8, t_light_kills, M("essence_light"))], {Ch: 12},
           ("Ritos da Luz", "Purifique criaturas com magia de Luz, como mandam os ritos da Aurora."),
           ("Rites of Light", "Purify creatures with Light magic, as the Dawn's rites command."), category="faith")
    fquest("church_tithe", Ch, "neutral", "F", [gather(coin, 10)], {Ch: 15},
           ("Dízimo da Aurora", "Uma doação para o orfanato e a enfermaria da Igreja."),
           ("Tithe of the Dawn", "A donation for the Church's orphanage and infirmary."), category="faith", coins=0, xp=30)
    fquest("church_great_tithe", Ch, "friendly", "D", [gather(coin, 40), gather(V("gold_ingot"), 4)], {Ch: 40},
           ("Grande Oferta", "O vitral da catedral rachou. Uma oferta generosa paga o restauro."),
           ("Great Offering", "The cathedral's stained glass cracked. A generous offering pays the restoration."), category="faith",
           coins=0, xp=150)
    fquest("church_holy_water", Ch, "neutral", "E", [gather(V("glowstone_dust"), 16), gather(V("golden_carrot"), 6)], {Ch: 15},
           ("Ingredientes da Água Benta", "Pó luminoso e cenouras douradas para as poções das irmãs."),
           ("Holy Water Ingredients", "Glowstone dust and golden carrots for the sisters' potions."), category="delivery")
    fquest("church_phantom_night", Ch, "neutral", "E", [kill(V("phantom"), 5)], {Ch: 15},
           ("Asas da Insônia", "Fantasmas atormentam os fiéis que não dormem. Abata-os."),
           ("Wings of Insomnia", "Phantoms torment the sleepless faithful. Shoot them down."), category="hunt")
    fquest("church_witch_hunt", Ch, "friendly", "D", [kill([V("witch"), M("cultist")], 6, t_witches)], {Ch: 25},
           ("Caça às Feiticeiras", "Bruxas e cultistas corrompem as aldeias. A Aurora pede justiça."),
           ("Witch Hunt", "Witches and cultists corrupt the villages. The Dawn asks for justice."), category="hunt", glyph=True)
    fquest("church_wither_bones", Ch, "honored", "B", [kill(V("wither_skeleton"), 8)], {Ch: 40},
           ("Ossos Malditos", "Os esqueletos do Wither são uma heresia ambulante. Purifique a fortaleza do Nether."),
           ("Cursed Bones", "Wither skeletons are walking heresy. Purify the Nether fortress."), category="faith", glyph=True)
    fquest("church_lich", Ch, "revered", "A", [kill(M("lich"), 1)], {Ch: 70},
           ("A Filactéria Profana", "O Lich desafia a morte e a Aurora. Encerre a sua não-vida."),
           ("The Profane Phylactery", "The Lich defies death and the Dawn. End its unlife."), category="boss", glyph=True,
           items=[item(M("essence_light"), 3)])
    fquest("church_dawn_crusade", Ch, "exalted", "S", [counter("guild.kill_undead", 150, t_undead, V("totem_of_undying")),
                                                  kill(V("wither"), 1)], {Ch: 100},
           ("Cruzada da Aurora", "Leve a luz até o fim do mundo: cem e cinquenta mortos-vivos e a própria Ruína."),
           ("Crusade of the Dawn", "Carry the light to the end of the world: a hundred and fifty undead and the Ruin itself."),
           category="boss", glyph=True, items=[item(V("totem_of_undying"), 1)])

    # ----------------------------------------------------------------- Submundo (underworld)
    U = "underworld"
    fquest("underworld_night_contract", U, "neutral", "E", [counter("guild.kill_night", 15, t_night, V("iron_sword"))], {U: 15},
           ("Contrato Noturno", "Alguém paga bem para que certas criaturas não vejam o amanhecer. Sem perguntas."),
           ("Night Contract", "Someone pays well so that certain creatures don't see the dawn. No questions."), category="contract")
    fquest("underworld_hot_goods", U, "unfriendly", "F", [gather(V("emerald"), 8)], {U: 12},
           ("Mercadoria Quente", "Esmeraldas sem procedência. O receptador não pergunta de onde vieram."),
           ("Hot Goods", "Emeralds of unknown origin. The fence doesn't ask where they came from."), category="contract")
    fquest("underworld_shadow_work", U, "neutral", "F", [counter("cast.shadow", 15, cast_label["shadow"], M("essence_shadow"))], {U: 10},
           ("Trabalho nas Sombras", "Mostre que sabe agir sem ser visto: use magia de Sombra."),
           ("Shadow Work", "Show you can act unseen: use Shadow magic."), category="contract")
    fquest("underworld_pillager_bounty", U, "neutral", "E", [kill(V("pillager"), 8)], {U: 15},
           ("Recompensa por Saqueadores", "Os saqueadores atrapalham o contrabando nas estradas. Dê um jeito neles."),
           ("Pillager Bounty", "The pillagers disrupt the smuggling on the roads. Deal with them."), category="contract")
    fquest("underworld_rare_goods", U, "friendly", "D", [gather(V("diamond"), 3), gather(V("gold_ingot"), 8)], {U: 25},
           ("Encomenda Rara", "Um cliente exigente quer pedras e ouro. Discrição é o preço."),
           ("Rare Order", "A demanding client wants gems and gold. Discretion is the price."), category="delivery", glyph=True)
    fquest("underworld_golden_idol", U, "friendly", "C", [explore(structure=V("desert_pyramid")), gather(V("gold_block"), 1)], {U: 30},
           ("O Ídolo de Ouro", "\"Pegue\" o tesouro de uma pirâmide do deserto antes dos arqueólogos do Reino."),
           ("The Golden Idol", "\"Borrow\" a desert pyramid's treasure before the Kingdom's archaeologists."), category="contract")
    fquest("underworld_monument_heist", U, "honored", "B", [explore(structure=V("monument")), gather(V("gold_block"), 2),
                                                    gather(V("sponge"), 4)], {U: 45},
           ("Roubo no Monumento", "O ouro do monumento oceânico tem comprador. Os guardiões, não."),
           ("Monument Heist", "The ocean monument's gold has a buyer. The guardians don't."), category="contract", glyph=True)
    fquest("underworld_evoker_contract", U, "honored", "B", [kill(V("evoker"), 2)], {U: 40},
           ("Contrato: os Invocadores", "Dois invocadores sabem demais. Não devem mais saber nada."),
           ("Contract: the Evokers", "Two evokers know too much. They should know nothing anymore."), category="contract", glyph=True)
    fquest("underworld_mansion_job", U, "revered", "A", [explore(structure=V("mansion")), kill(V("vindicator"), 10)], {U: 60},
           ("O Trabalho da Mansão", "A mansão da floresta guarda segredos valiosos. Esvazie o lugar."),
           ("The Mansion Job", "The woodland mansion keeps valuable secrets. Clear the place out."), category="contract", glyph=True,
           items=[item(V("totem_of_undying"), 1)])
    fquest("underworld_kingpin", U, "exalted", "S", [kill([V("ender_dragon"), V("warden")], 1, lbl("target.v08.kingpin",
                                                                                          "o Dragão do Fim ou o Warden",
                                                                                          "the Ender Dragon or the Warden")),
                                            gather(V("netherite_ingot"), 2)], {U: 100},
           ("O Golpe do Século", "O Rei do Submundo quer um troféu lendário e netherita. O pagamento é à altura."),
           ("Heist of the Century", "The Underworld's King wants a legendary trophy and netherite. The pay matches."),
           category="contract", glyph=True, items=[item(V("enchanted_golden_apple"), 2)])

    # ----------------------------------------------------------------- Guilda (encomendas do Povo Selvagem e da v0.8)
    W = "wildfolk"
    gquest("guild_v8_plant_trees", "F", [counter("guild.plant", 12, t_plant, V("oak_sapling"))],
           ("Encomenda das Dríades: Mudas", "O Povo Selvagem pede que a Guilda replante as clareiras desmatadas."),
           ("Dryads' Request: Saplings", "The Wildfolk ask the Guild to replant the cleared groves."), rep={W: 15}, category="nature")
    gquest("guild_v8_reforest", "D", [counter("guild.plant", 40, t_plant, V("birch_sapling"))],
           ("Encomenda das Dríades: Reflorestar", "Uma floresta inteira para devolver à terra o que foi tirado."),
           ("Dryads' Request: Reforest", "A whole forest to give back what was taken from the land."), rep={W: 30}, category="nature",
           glyph=True)
    gquest("guild_v8_forest_horde", "E", [counter("guild.kill_horde_forest", 10, t_horde_forest, M("goblin_ear"))],
           ("Proteja as Fadas", "Goblins e orcs caçam fadas nas florestas. O Povo Selvagem paga a escolta."),
           ("Protect the Fairies", "Goblins and orcs hunt fairies in the forests. The Wildfolk pay for the escort."),
           rep={W: 20}, category="nature")
    gquest("guild_v8_fairy_guard", "C", [counter("guild.kill_horde_forest", 25, t_horde_forest, M("goblin_ear"))],
           ("Guardião do Bosque", "Torne-se o terror da Horda nas matas. As fadas cantarão o seu nome."),
           ("Grove Guardian", "Become the Horde's terror in the woods. The fairies will sing your name."), rep={W: 35},
           category="nature", glyph=True)
    gquest("guild_v8_city_tour", "F", [counter("visit.city", 1, t_new_city, V("filled_map"))],
           ("Conheça as Cidades", "Toda cidade do Reino tem uma filial da Guilda. Visite uma nova."),
           ("Tour the Cities", "Every city in the Kingdom has a Guild branch. Visit a new one."), category="explore")
    gquest("guild_v8_capital_visit", "F", [counter("visit.capital", 1, t_capital, V("bell"), absolute=True)],
           ("Rumo à Capital", "A sede da Guilda fica em Aldória, a Capital. Todo aventureiro deve conhecê-la."),
           ("To the Capital", "The Guild's headquarters is in Aldória, the Capital. Every adventurer should see it."),
           category="explore", repeatable=False, rep={"kingdom": 10})
    gquest("guild_v8_teamwork", "E", [counter("guild.hired", 1, t_hired, M("adventurer_spawn_egg"), absolute=True),
                               counter("guild.companion_kills", 10, t_companion, V("iron_sword"))],
           ("Trabalho em Equipe", "Contrate um aventureiro e lutem juntos. Ninguém sobe de rank sozinho."),
           ("Teamwork", "Hire an adventurer and fight together. Nobody ranks up alone."), category="kill")
    gquest("guild_v8_reaction_drill", "D", [counter("reaction.any", 15, t_reactions, V("blaze_powder"))],
           ("Treino de Combinações", "Os instrutores da Guilda querem ver você combinar elementos em combate."),
           ("Combination Drill", "The Guild's instructors want to see you combine elements in combat."), rep={"academy": 8},
           category="task")
    gquest("guild_v8_border_patrol", "C", [counter("guild.kill_horde_city", 15, t_horde_city, M("goblin_ear"))],
           ("Patrulha de Fronteira", "O Reino contratou a Guilda para limpar a Horda dos arredores das cidades."),
           ("Border Patrol", "The Kingdom hired the Guild to clear the Horde from around the cities."), rep={"kingdom": 15},
           category="kill")
    gquest("guild_v8_counter_drill", "B", [counter("counter.success", 2, t_counter, V("echo_shard"))],
           ("Resposta Imediata", "Interrompa duas conjurações inimigas com Contramágica antes que se completem."),
           ("Immediate Response", "Interrupt two enemy castings with a Counterspell before they complete."), category="task", glyph=True)


def v08_shop():
    def it(rank, price, i, n=1, potion=None, standing=None):
        o = {"rank": rank, "price": price, "item": item(i, n, potion)}
        if standing:
            o["standing"] = standing
        return o

    offers = [
        it("F", 18, M("mana_crystal"), 3, standing="friendly"),
        it("E", 6, V("potion"), 1, V("strong_healing"), standing="friendly"),
        it("D", 18, V("experience_bottle"), 16, standing="honored"),
        {"rank": "D", "price": 45, "glyph_tier": 3, "standing": "honored"},
        {"rank": "B", "price": 85, "glyph_tier": 4, "standing": "revered"},
        it("B", 45, V("totem_of_undying"), standing="revered"),
        it("A", 60, V("enchanted_golden_apple"), standing="exalted"),
        {"rank": "S", "price": 220, "legend_scroll": True, "standing": "exalted"},
    ]
    write_json(os.path.join(D, "guild_shop/standing.json"), {"offers": offers})


# ---------------------------------------------------------------- Diário: capítulo "Reinos e Facções"
def v08_journal():
    rep_any = {"type": "any", "conditions": [{"type": "counter", "key": f"rep.{f}", "min": 100}
                                             for f in ("guild", "kingdom", "academy", "church", "underworld", "wildfolk", "horde")]}
    chapter = {"chapters": [{
        "id": "kingdoms", "order": 22, "color": "#3F6FD8",
        "icon": [M("faction_board"), M("adventurer_spawn_egg"), V("bell")],
        "steps": [
            {"id": "reach_city", "xp": 60, "icon": V("bell"),
             "condition": {"type": "any", "conditions": [{"type": "counter", "key": "visit.capital", "min": 1},
                                                         {"type": "counter", "key": "visit.city", "min": 1}]},
             "locate": {"tag": f"{NS}:city", "dimension": "minecraft:overworld"},
             "requires": [M("city_heart")]},
            {"id": "hire_adventurer", "xp": 50, "icon": M("adventurer_spawn_egg"),
             "condition": {"type": "counter", "key": "guild.hired", "min": 1},
             "rewards": {"items": [{"id": M("guild_coin"), "count": 8}]}},
            {"id": "faction_quest", "xp": 50, "icon": M("faction_board"),
             "condition": {"type": "counter", "key": "guild.faction_quests", "min": 1}},
            {"id": "faction_friendly", "xp": 80, "icon": V("emerald"), "condition": rep_any},
            {"id": "reactions", "xp": 60, "icon": V("blaze_powder"),
             "condition": {"type": "counter", "key": "reaction.any", "min": 10}},
            {"id": "counterspell", "xp": 80, "icon": V("echo_shard"), "optional": True,
             "condition": {"type": "counter", "key": "counter.success", "min": 1},
             "requires": [M("counterspell")]},
        ],
        "rewards": {"xp": 450, "items": [{"id": M("guild_coin"), "count": 25}, {"id": M("mana_crystal"), "count": 6}],
                    "glyph_scrolls": 2, "glyph_tier": 3},
    }]}
    write_json(os.path.join(D, "journal/kingdoms.json"), chapter)
    # "Entre na Guilda": a seta agora aponta a cidade mais próxima (a Guilda fica nas cidades) ou a vila da Guilda.
    path = os.path.join(D, "journal/chapters.json")
    with open(path, encoding="utf-8") as fh:
        book = json.load(fh)
    for ch in book["chapters"]:
        for st in ch["steps"]:
            if ch["id"] == "guild_and_trials" and st["id"] == "join_guild":
                st["locate"] = {"dimension": "minecraft:overworld",
                                "any": [{"tag": f"{NS}:city"}, {"structure": f"{NS}:guild_hall_village"}]}
    write_json(path, book)

    J = f"journal.{NS}.kingdoms"
    tr(f"{J}.title", "Reinos e Facções", "Kingdoms and Factions")
    tr(f"{J}.lore", "O mundo não é feito só de monstros e santuários. Há muralhas, estandartes e gente que se lembra de quem "
                    "ajuda — e de quem trai. Reino, Academia, Igreja e até o Submundo têm trabalho para alguém como você.",
       "The world isn't made only of monsters and shrines. There are walls, banners and people who remember who helps them — "
       "and who betrays them. Kingdom, Academy, Church and even the Underworld have work for someone like you.")
    tr(f"{J}.outro", "Seu nome agora corre pelas tavernas. Algumas portas se abrem sozinhas; outras, nem a magia abre.",
       "Your name now runs through the taverns. Some doors open on their own; others not even magic will open.")
    steps = {
        "reach_city": (("Chegue a uma cidade", "Muralhas de pedra, estandartes azuis e o cheiro de pão quente. Civilização, enfim.",
                        "A seta aponta a cidade mais próxima (a Capital, Aldória, fica perto do ponto de nascimento). Entre pelos "
                        "portões: dentro das muralhas os monstros não nascem.", "Siga a seta até a cidade"),
                       ("Reach a city", "Stone walls, blue banners and the smell of warm bread. Civilization, at last.",
                        "The arrow points to the nearest city (the Capital, Aldória, is close to spawn). Walk through the gates: "
                        "monsters don't spawn inside the walls.", "Follow the arrow to the city")),
        "hire_adventurer": (("Contrate um aventureiro", "Um bom companheiro vale mais que uma espada nova.",
                             "Fale com um aventureiro (clique direito) e use \"Contratar\". Ele segue você por 3 dias, luta, usa "
                             "a habilidade da classe e sobe de rank com os abates. Agachar + clicar: seguir/esperar.",
                             "Clique num aventureiro"),
                            ("Hire an adventurer", "A good companion is worth more than a new sword.",
                             "Talk to an adventurer (right-click) and choose \"Hire\". They follow you for 3 days, fight, use their "
                             "class skill and rank up with kills. Sneak + click: follow/wait.", "Click an adventurer")),
        "faction_quest": (("Cumpra uma missão de facção", "Cada estandarte tem os seus pedidos — e a sua gratidão.",
                           "Os Quadros de Facção ficam na praça (Reino), na torre da Academia, na igreja e num beco escondido "
                           "(Submundo). As missões liberam conforme a sua reputação com a facção.", "Quadro de Facção"),
                          ("Complete a faction quest", "Every banner has its requests — and its gratitude.",
                           "Faction Boards stand in the plaza (Kingdom), the Academy tower, the church and a hidden alley "
                           "(Underworld). Quests unlock with your reputation with the faction.", "Faction Board")),
        "faction_friendly": (("Fique Amigável com uma facção", "Um sorriso do guarda, um desconto do mercador. Reputação é moeda.",
                              "Missões, defender a cidade e ajudar dão reputação. Veja a aba Reputação no menu (B). Com Amigável "
                              "os preços caem e novas missões aparecem.", "Ganhe reputação"),
                             ("Become Friendly with a faction", "A smile from the guard, a discount from the merchant. Reputation is currency.",
                              "Quests, defending the city and helping out give reputation. See the Reputation tab in the menu (B). "
                              "At Friendly prices drop and new quests appear.", "Earn reputation")),
        "reactions": (("Cause 10 reações elementais", "Água e raio. Fogo e gelo. A magia gosta de combinações.",
                       "Molhe um alvo e use Raio (Eletrocutar), queime e congele (Derreter), Em Chamas + Raio (Sobrecarga)... "
                       "Combine elementos diferentes no mesmo inimigo.", "Combine elementos"),
                      ("Trigger 10 elemental reactions", "Water and lightning. Fire and ice. Magic loves combinations.",
                       "Soak a target and use Lightning (Electrocute), burn and freeze (Melt), Burning + Lightning (Overload)... "
                       "Combine different elements on the same enemy.", "Combine elements")),
        "counterspell": (("Responda com Contramágica", "\"Em resposta...\" — e a magia do inimigo se desfaz no ar.",
                          "Quando um inimigo começa a conjurar algo grande (aparece no painel da Pilha à esquerda), use um glifo "
                          "de Contramágica nele antes que se complete.", "Contrarie uma conjuração"),
                         ("Answer with a Counterspell", "\"In response...\" — and the enemy's spell dissolves in the air.",
                          "When an enemy starts casting something big (it shows on the Stack panel on the left), hit it with a "
                          "Counterspell glyph before it completes.", "Counter a casting")),
    }
    for sid, (pt, en) in steps.items():
        tr(f"{J}.{sid}", pt[0], en[0])
        tr(f"{J}.{sid}.lore", pt[1], en[1])
        tr(f"{J}.{sid}.hint", pt[2], en[2])
        tr(f"{J}.{sid}.tip", pt[3], en[3])


# ---------------------------------------------------------------- traduções v0.8
def v08_translations():
    t = tr
    b = f"block.{NS}.faction_board"
    t(b, "Quadro de Facção", "Faction Board")
    for f, pt, en in (("guild", "Quadro da Guilda", "Guild Board"), ("kingdom", "Quadro do Reino", "Kingdom Board"),
                      ("academy", "Quadro da Academia", "Academy Board"), ("church", "Quadro da Igreja", "Church Board"),
                      ("underworld", "Quadro do Submundo", "Underworld Board")):
        t(f"{b}.{f}", pt, en)
    t(f"{b}.hint", "Missões da facção, liberadas pela reputação.", "The faction's quests, unlocked by reputation.")
    for v, pt, en in (("warrior", "Guerreiro", "Warrior"), ("archer", "Arqueiro", "Archer"), ("mage", "Mago", "Mage"),
                      ("rogue", "Ladino", "Rogue"), ("cleric", "Clérigo", "Cleric")):
        t(f"guild.{NS}.class.{v}", pt, en)
    t(f"entity.{NS}.adventurer.plate", "%s · %s [%s]", "%s · %s [%s]")
    t(f"entity.{NS}.adventurer.plate_hired", "⚔ %s · %s [%s]", "⚔ %s · %s [%s]")

    d = f"dialogue.{NS}.adventurer."
    lines = {
        "cleric.0": ("Que a Aurora ilumine o seu caminho.", "May the Dawn light your path."),
        "cleric.1": ("Ferido? Fique perto de mim, eu cuido disso.", "Hurt? Stay close to me, I'll take care of it."),
        "cleric.2": ("Mortos-vivos detestam a minha luz. É recíproco.", "The undead hate my light. The feeling is mutual."),
        "hired.0": ("Estou com você. Aponte e eu ataco.", "I'm with you. Point and I strike."),
        "hired.1": ("Se ficar feio, grite. Eu apareço.", "If it gets ugly, shout. I'll be there."),
        "hired.2": ("Contrato é contrato. Pode contar comigo.", "A contract is a contract. Count on me."),
        "hired.3": ("Já pensou em contratar mais um? Brincadeira... ou não.", "Ever thought of hiring one more? Just kidding... or not."),
        "hired.4": ("Ainda tenho fôlego para mais umas dez lutas.", "I've still got breath for another ten fights."),
        "hired.5": ("Agachar e clicar em mim: eu espero aqui. De novo: eu sigo.", "Sneak and click me: I'll wait here. Again: I'll follow."),
        "hired_other": ("Já estou contratado. Fale comigo quando o contrato acabar.", "I'm already hired. Talk to me when the contract ends."),
        "cold": ("A Guilda não fala bem de você. Melhor seguir caminho.", "The Guild doesn't speak well of you. Better move along."),
        "honored.0": ("É você! Os veteranos falam de você nas tavernas.", "It's you! The veterans talk about you in the taverns."),
        "honored.1": ("Seria uma honra lutar ao seu lado.", "It would be an honor to fight at your side."),
        "honored.2": ("Faço um preço especial para alguém como você.", "I'll make a special price for someone like you."),
        "friendly.0": ("Ei, colega! A Guilda anda falando bem de você.", "Hey, colleague! The Guild has been speaking well of you."),
        "friendly.1": ("Precisando de uma mão? Eu cobro barato para amigos.", "Need a hand? I charge little for friends."),
        "friendly.2": ("Bom te ver de novo por aqui.", "Good to see you around again."),
        "rumor.0": ("Dizem que a Capital, Aldória, fica perto de onde todos os heróis chegam.",
                    "They say the Capital, Aldória, lies close to where every hero arrives."),
        "rumor.1": ("Há um beco nas cidades onde um receptador compra de tudo. Procure a porta escondida.",
                    "There's an alley in the cities where a fence buys anything. Look for the hidden door."),
        "rumor.2": ("A Academia paga bem por quem provoca reações elementais. Coisa de cientista maluco.",
                    "The Academy pays well for anyone who triggers elemental reactions. Mad scientist stuff."),
        "rumor.3": ("A Igreja da Aurora caça mortos-vivos sem descanso. E paga em ouro.",
                    "The Church of the Dawn hunts the undead tirelessly. And pays in gold."),
        "rumor.4": ("A Horda anda rondando as cidades. O Reino recompensa quem a enfrenta.",
                    "The Horde has been prowling around the cities. The Kingdom rewards those who face it."),
        "rumor.5": ("Clérigos curam, magos explodem coisas. Um grupo bom tem os dois.",
                    "Clerics heal, mages blow things up. A good party has both."),
        "rumor.6": ("Ouvi dizer que um mago conjurou um meteoro e abriu uma cratera do tamanho de uma vila.",
                    "I heard a mage called down a meteor and left a crater the size of a village."),
        "rumor.7": ("As fadas das florestas lembram de quem protege as matas.",
                    "The forest fairies remember who protects the woods."),
        "hire.accept.0": ("Contrato fechado! Vamos lá.", "Deal! Let's go."),
        "hire.accept.1": ("Pode deixar. Ninguém encosta em você.", "Leave it to me. Nobody lays a finger on you."),
        "hire.accept.2": ("Três dias de aventura? Fechado!", "Three days of adventure? You're on!"),
        "hire.refuse_rep": ("Com a sua fama na Guilda? Nem por todo o ouro do Reino.",
                            "With your reputation at the Guild? Not for all the gold in the Kingdom."),
        "hire.too_many": ("Você já tem gente demais te seguindo. Não trabalho em multidão.",
                          "You already have too many people following you. I don't work in crowds."),
        "hire.no_coins": ("Sem moedas, sem espada. Nada pessoal.", "No coins, no sword. Nothing personal."),
        "hire.renew": ("Mais três dias? Ótimo, eu estava gostando.", "Three more days? Great, I was enjoying it."),
        "farewell.0": ("Foi uma boa jornada. Até a próxima!", "It was a good journey. Until next time!"),
        "farewell.1": ("Contrato cumprido. Me procure na Guilda.", "Contract fulfilled. Look for me at the Guild."),
        "farewell.2": ("Hora de seguir viagem. Cuide-se!", "Time to move on. Take care!"),
        "follow": ("Certo, estou logo atrás de você.", "Right, I'm right behind you."),
        "wait": ("Vou esperar aqui. Volte logo.", "I'll wait here. Come back soon."),
        "levelup.0": ("Rank %s! Eu sabia que esse dia chegaria!", "Rank %s! I knew this day would come!"),
        "levelup.1": ("Subi para o Rank %s! Obrigado pela confiança.", "I've reached Rank %s! Thanks for trusting me."),
        "levelup.2": ("Rank %s... e ainda estou só esquentando.", "Rank %s... and I'm just warming up."),
        "bye.0": ("Até mais, aventureiro.", "See you, adventurer."),
        "bye.1": ("Boa caçada!", "Good hunting!"),
        "bye.2": ("Volte quando precisar de uma espada.", "Come back when you need a sword."),
    }
    for k, (pt, en) in lines.items():
        t(d + k, pt, en)

    m = f"message.{NS}.guild."
    for k, pt, en in [
        ("faction_refuses", "%s não confia em você e recusa o trabalho.", "%s doesn't trust you and refuses the work."),
        ("standing_required", "Requer patamar %s com %s.", "Requires %s standing with %s."),
        ("too_many_faction", "Você já tem %s missões de facção ativas.", "You already have %s active faction quests."),
        ("wrong_board", "Entregue esta missão no Quadro de %s.", "Turn this quest in at the %s Board."),
        ("completed_faction", "Missão concluída: %s — %s agradece.", "Quest complete: %s — %s thanks you."),
        ("ready_faction", "Missão pronta: %s! Entregue no Quadro de %s.", "Quest ready: %s! Turn it in at the %s Board."),
        ("companion_follow", "%s está seguindo você.", "%s is following you."),
        ("companion_wait", "%s vai esperar aqui.", "%s will wait here."),
        ("companion_limit", "Você já tem o máximo de companheiros (%s). Com a Guilda em Honrado, 3.",
         "You already have the most companions (%s). With the Guild at Honored, 3."),
        ("companion_hired", "%s agora é seu companheiro por %s dias de jogo.", "%s is now your companion for %s game days."),
        ("companion_hint", "Ele segue, defende e ataca o que você ataca; os abates dele perto de você contam nas suas missões de caça. Agachar + clicar: seguir/esperar. Clique: renovar ou dispensar.",
         "They follow, defend you and attack what you attack; their kills near you count toward your hunting quests. Sneak + click: follow/wait. Click: renew or dismiss."),
        ("companion_renew_max", "O contrato já está renovado o bastante.", "The contract is already renewed enough."),
        ("companion_renewed", "Contrato com %s renovado por mais %s dias.", "Contract with %s renewed for %s more days."),
        ("companion_dismissed", "Você dispensou %s.", "You dismissed %s."),
        ("companion_ended", "O contrato com %s terminou.", "The contract with %s has ended."),
        ("companion_died", "Seu companheiro %s caiu em combate: %s", "Your companion %s fell in battle: %s"),
        ("companion_died_away", "Enquanto você estava fora, seu companheiro %s caiu em combate. A vaga no grupo foi liberada.",
         "While you were away, your companion %s fell in battle. Their party slot is free again."),
    ]:
        t(m + k, pt, en)
    to = f"toast.{NS}.guild."
    for k, pt, en in [
        ("faction_quest_done", "Missão de %s concluída!", "%s quest complete!"),
        ("hired", "Companheiro contratado!", "Companion hired!"),
        ("contract_ended", "Fim do contrato", "Contract ended"),
        ("companion_died", "%s caiu em combate", "%s fell in battle"),
        ("companion_rank", "Companheiro subiu ao Rank %s!", "Companion reached Rank %s!"),
    ]:
        t(to + k, pt, en)
    c = f"command.{NS}.guild."
    t(c + "bad_class", "Classe inválida (warrior, archer, mage, rogue, cleric).", "Invalid class (warrior, archer, mage, rogue, cleric).")
    t(c + "party", "%s aventureiros chegaram.", "%s adventurers arrived.")
    t(c + "contracts_expired", "%s contratos encerrados.", "%s contracts ended.")

    s = f"screen.{NS}.guild."
    for k, pt, en in [
        ("adv.title", "Aventureiro", "Adventurer"),
        ("adv.kills", "Abates: %s", "Kills: %s"),
        ("adv.xp", "Experiência: %s / %s (Rank %s)", "Experience: %s / %s (Rank %s)"),
        ("adv.max_rank", "Rank máximo — uma lenda viva.", "Maximum rank — a living legend."),
        ("adv.mine_following", "Seu companheiro · seguindo · %s", "Your companion · following · %s"),
        ("adv.mine_waiting", "Seu companheiro · esperando · %s", "Your companion · waiting · %s"),
        ("adv.other", "Contratado por outro aventureiro.", "Hired by another adventurer."),
        ("adv.refused", "Não aceita trabalhar para você (reputação com a Guilda).", "Won't work for you (Guild reputation)."),
        ("adv.offer", "Contrato: %s moedas por %s dias de jogo.", "Contract: %s coins for %s game days."),
        ("adv.slots", "Companheiros: %s / %s", "Companions: %s / %s"),
        ("adv.talk", "Conversar", "Talk"),
        ("adv.trade", "Trocar", "Trade"),
        ("adv.bye", "Despedir-se", "Farewell"),
        ("adv.renew", "Renovar (%s)", "Renew (%s)"),
        ("adv.wait", "Esperar aqui", "Wait here"),
        ("adv.follow", "Seguir-me", "Follow me"),
        ("adv.dismiss", "Dispensar", "Dismiss"),
        ("adv.hire", "Contratar — %s moedas", "Hire — %s coins"),
        ("adv.hire_tip", "Segue você por %s dias, luta, usa a habilidade da classe e sobe de rank.",
         "Follows you for %s days, fights, uses their class skill and ranks up."),
        ("adv.hire_tip2", "O preço depende do rank e da sua reputação com a Guilda.", "The price depends on rank and your Guild reputation."),
        ("adv.time_dh", "%sd %sh", "%sd %sh"),
        ("adv.time_h", "%sh", "%sh"),
        ("heading_faction", "Quadro — %s", "Board — %s"),
        ("price_mult", "Preços %s%%", "Prices %s%%"),
        ("board_title_faction", "Pedidos de %s", "Requests from %s"),
        ("faction_intro.kingdom", "O Reino paga quem defende as cidades, abastece o quartel e enfrenta a Horda.",
         "The Kingdom pays those who defend the cities, supply the barracks and face the Horde."),
        ("faction_intro.academy", "A Academia quer dados: magias conjuradas, reações provocadas, Provações vencidas.",
         "The Academy wants data: spells cast, reactions triggered, Trials won."),
        ("faction_intro.church", "A Igreja da Aurora pede luz contra os mortos — e ofertas para os necessitados.",
         "The Church of the Dawn asks for light against the dead — and offerings for the needy."),
        ("faction_intro.underworld", "Sem nomes, sem perguntas. O Submundo paga trabalhos noturnos e mercadorias raras.",
         "No names, no questions. The Underworld pays for night work and rare goods."),
        ("faction_intro.guild", "Pedidos da Guilda dos Aventureiros.", "Requests from the Adventurers' Guild."),
        ("state_refused", "Recusada", "Refused"),
        ("state_standing", "Requer: %s", "Requires: %s"),
        ("active_title_both", "Missões ativas — Guilda %s/%s · Facções %s/%s", "Active quests — Guild %s/%s · Factions %s/%s"),
        ("turn_in_at", "Entregar: %s", "Turn in: %s"),
        ("shop_standing", "Requer patamar %s com a Guilda", "Requires %s standing with the Guild"),
        ("reward_rep", "%s de reputação: %s", "%s reputation: %s"),
        ("companions_title", "Companheiros", "Companions"),
        ("category.defense", "Defesa", "Defense"),
        ("category.delivery", "Entrega", "Delivery"),
        ("category.study", "Estudo", "Study"),
        ("category.faith", "Fé", "Faith"),
        ("category.contract", "Contrato", "Contract"),
        ("category.nature", "Natureza", "Nature"),
        ("category.hunt", "Caçada", "Hunt"),
    ]:
        t(s + k, pt, en)
    g = f"guild.{NS}.tracker."
    for k, pt, en in [
        ("companion", "⚔ %s [%s]", "⚔ %s [%s]"),
        ("companion_following", "Seguindo", "Following"),
        ("companion_waiting", "Esperando", "Waiting"),
        ("companion_detail", "%s · %s", "%s · %s"),
        ("companion_far", "Longe (fora de alcance)", "Far away (out of range)"),
        ("ready_faction", "✔ Pronta! Entregue no Quadro de %s", "✔ Ready! Turn in at the %s Board"),
    ]:
        t(g + k, pt, en)


def v08():
    for f in FACTIONS:
        faction_board_front(f)
    faction_board_models()
    faction_board_data()
    v08_quests()
    for qid, data in QUESTS:
        if "faction" in data or qid.startswith("guild_v8_"):
            write_json(os.path.join(D, f"guild_quests/{qid}.json"), data)
    v08_shop()
    v08_journal()
    v08_translations()


# ------------------------------------------------------------------------------------------------
# v0.9 — Areas de Caca: estandarte da Guilda (GeckoLib) + traducoes
# ------------------------------------------------------------------------------------------------
GREEN0 = hex_rgba("16361D")
GREEN1 = hex_rgba("23502C")
GREEN2 = hex_rgba("2F6B3A")
GREEN3 = hex_rgba("3E8A4B")
SILVER = hex_rgba("D9DEE4")
SILVER_D = hex_rgba("8C949E")
RUNE = hex_rgba("FFE38A")
RUNE_D = hex_rgba("C9A24A")
AMBER = hex_rgba("FFB547")


def hunt_banner_geo():
    g = Geo("hunt_banner", 256, 128)
    g.bone("root")
    # Pedestal de pedra em dois degraus
    g.bone("base", "root", (0, 0, 0))
    g.cube("base", (-6, 0, -6), (12, 3, 12), "stone")
    g.cube("base", (-4, 3, -4), (8, 2, 8), "stone2")
    # Mastro com aneis de latao
    g.bone("pole", "root", (0, 5, 0))
    g.cube("pole", (-1, 5, -1), (2, 60, 2), "pole")
    g.cube("pole", (-2, 18, -2), (4, 1, 4), "brass")
    g.cube("pole", (-2, 44, -2), (4, 1, 4), "brass")
    # Travessa
    g.bone("crossbar", "pole", (0, 58, 0))
    g.cube("crossbar", (-10, 57, -1), (20, 2, 2), "bar")
    g.cube("crossbar", (-11, 56, -2), (1, 4, 4), "brass")
    g.cube("crossbar", (10, 56, -2), (1, 4, 4), "brass")
    # Pano (duas partes: o rabo de andorinha balanca mais)
    g.bone("cloth", "crossbar", (0, 57, 0))
    g.cube("cloth", (-8, 33, 1), (16, 24, 1), "cloth")
    g.bone("cloth_tail", "cloth", (0, 33, 0))
    g.cube("cloth_tail", (-8, 23, 1), (16, 10, 1), "cloth_tail")
    # Lanternas nas pontas da travessa
    for side, x in (("l", -9), ("r", 9)):
        g.bone(f"lantern_{side}", "crossbar", (x, 56, 0))
        g.cube(f"lantern_{side}", (x, 52, 0), (1, 4, 1), "chain")
        g.cube(f"lantern_{side}", (x - 2, 47, -2), (4, 5, 4), "lantern")
        g.cube(f"lantern_{side}", (x - 1, 46, -1), (2, 1, 2), "brass")
    # Ponta dourada e flamula
    g.bone("finial", "pole", (0, 65, 0))
    g.cube("finial", (-1, 65, -1), (2, 3, 2), "gold")
    g.cube("finial", (-2, 64, -2), (4, 1, 4), "brass")
    g.bone("pennant", "finial", (0, 66, 0))
    g.cube("pennant", (1, 63, 0), (9, 5, 1), "pennant")
    # Circulos runicos no chao (o renderer gira e aumenta: 48 px x 2,4 = ~7 blocos)
    g.bone("rune_circle", "root", (0, 0, 0))
    g.cube("rune_circle", (-24, 0.15, -24), (48, 0, 48), "runes")
    g.bone("rune_inner", "root", (0, 0, 0))
    g.cube("rune_inner", (-12, 0.3, -12), (24, 0, 24), "rune_inner")
    g.pack()
    return g


def _ring(c, r, cx, cy, radius, width, col_fn):
    """Anel (centro cx, cy) dentro do retangulo r = (x0, y0, x1, y1)."""
    x0, y0, x1, y1 = r
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if abs(d - radius) <= width / 2.0:
                col = col_fn(x - x0, y - y0, d)
                if col:
                    c.set(x, y, col)


def _line(c, r, ax, ay, bx, by, col):
    x0, y0, x1, y1 = r
    n = int(max(abs(bx - ax), abs(by - ay)) * 2) + 1
    for i in range(n + 1):
        t = i / n
        x, y = int(ax + (bx - ax) * t), int(ay + (by - ay) * t)
        if x0 <= x <= x1 and y0 <= y <= y1:
            c.set(x, y, col)


def runes_painter(size, glow=False, inner=False):
    """So a face de cima: aneis, estrela e runas (o resto transparente — a face de baixo some no cutout)."""
    def p(c, f, rnd):
        r = f["top"]
        x0, y0, x1, y1 = r
        cx, cy = (x0 + x1 + 1) / 2.0, (y0 + y1 + 1) / 2.0
        R = size / 2.0 - 1
        main = RUNE if glow else RUNE_D
        bright = lighten(RUNE, 0.4) if glow else RUNE

        def col(fx, fy, d):
            return bright if rnd.random() < 0.25 else main

        _ring(c, r, cx, cy, R, 1.6, col)
        _ring(c, r, cx, cy, R * (0.55 if inner else 0.78), 1.1, col)
        pts = 3 if inner else 6
        rr = R * (0.55 if inner else 0.78)
        corners = [(cx + math.cos(a) * rr, cy + math.sin(a) * rr)
                   for a in [i * 2 * math.pi / pts - math.pi / 2 for i in range(pts)]]
        if inner:
            for i in range(pts):
                _line(c, r, *corners[i], *corners[(i + 1) % pts], main)
        else:
            for i in range(pts):
                _line(c, r, *corners[i], *corners[(i + 2) % pts], main)
            # 12 runas pequenas entre os aneis
            for k in range(12):
                a = k * math.pi / 6
                rx, ry = cx + math.cos(a) * R * 0.89, cy + math.sin(a) * R * 0.89
                shape = [(0, -1), (0, 0), (0, 1), (-1, -1 + k % 3), (1, (k % 2))]
                for dx, dy in shape:
                    c.set(int(rx) + dx, int(ry) + dy, bright)
        # brilho no centro
        if not inner and glow:
            c.set(int(cx), int(cy), bright)
    return p


def stone_painter(color):
    color = hex_rgba(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = jitter(color, rnd, 0.1)
                if name == "top" and (fx in (0, fw - 1) or fy in (0, fh - 1)):
                    col = lighten(col, 0.12)
                if name != "top" and fy == fh - 1:
                    col = darken(col, 0.25)
                if name != "top" and (fx + fy * 3) % 7 == 0:
                    col = darken(col, 0.15)
                if rnd.random() < 0.06:
                    col = mix(col, hex_rgba("4F7A3A"), 0.5)  # musgo
                c.set(x, y, col)
    return p


def cloth_painter(tail=False, glow=False):
    """Pano verde da Guilda: borda dourada, emblema (escudo dourado + espadas cruzadas) e, no rabo, o corte em V."""
    def emblem(c, r, glow_only):
        x0, y0, x1, y1 = r
        w, h = x1 - x0 + 1, y1 - y0 + 1
        cx = x0 + w // 2
        top = y0 + 6
        # escudo
        for yy in range(9):
            half = 4 if yy < 5 else 4 - (yy - 4)
            for xx in range(-half, half):
                edge = xx in (-half, half - 1) or yy == 0
                if glow_only:
                    if edge:
                        c.set(cx + xx, top + yy, RUNE)
                    continue
                c.set(cx + xx, top + yy, GOLD3 if edge else (GOLD2 if (xx + yy) % 3 else GOLD1))
        if glow_only:
            return
        # espadas cruzadas (prata) sobre o escudo
        for i in range(10):
            c.set(cx - 5 + i, top - 1 + i, SILVER if i % 4 else SILVER_D)
            c.set(cx + 4 - i, top - 1 + i, SILVER if i % 4 else SILVER_D)
        for dx in (-6, -5, 4, 5):
            c.set(cx + dx, top + 9, WOOD3)
        # letras de rank (fileira de estrelas) embaixo
        for k in range(-2, 3):
            c.set(cx + k * 2, top + 13, GOLD3 if k == 0 else GOLD2)

    def p(c, f, rnd):
        for name, r in f.items():
            x0, y0, x1, y1 = r
            for x, y, fx, fy, fw, fh in px(r):
                if glow:
                    continue
                col = GREEN2 if (fx + fy) % 5 else GREEN1
                col = jitter(col, rnd, 0.04)
                if name in ("front", "back"):
                    if fx in (0, fw - 1):
                        col = GOLD2
                    elif fx in (1, fw - 2):
                        col = GREEN0
                    if not tail and fy in (0, 1):
                        col = GREEN0 if fy == 0 else GREEN3
                    if tail:
                        # corte em V no meio da barra
                        depth = 5 - abs(fx - (fw - 1) / 2.0) * 0.9
                        if fy >= fh - depth:
                            col = T
                        elif fy >= fh - depth - 1 and fx not in (0, fw - 1):
                            col = GOLD2
                    elif fy == fh - 1:
                        col = GREEN2
                c.set(x, y, col)
            if name in ("front", "back") and not tail:
                emblem(c, r, glow)
            if name in ("front", "back") and glow and tail:
                pass
    return p


def lantern_painter(glow=False):
    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                frame = fx in (0, fw - 1) or fy in (0, fh - 1) or name in ("top", "bottom")
                if glow:
                    if not frame:
                        c.set(x, y, lighten(AMBER, 0.3))
                    continue
                if frame:
                    c.set(x, y, jitter(hex_rgba("3A3F46"), rnd, 0.06))
                else:
                    c.set(x, y, AMBER if (fx + fy) % 2 else lighten(AMBER, 0.25))
    return p


def pennant_painter():
    red = hex_rgba("C9433A")

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name in ("front", "back"):
                    # triangulo: estreita para a ponta (fx alto, para fora do mastro)
                    tip = fx if name == "front" else fw - 1 - fx
                    half = (fh - 1) / 2.0 * (1 - tip / float(fw))
                    if abs(fy - (fh - 1) / 2.0) > half + 0.3:
                        continue
                    col = GOLD2 if tip == 0 else (darken(red, 0.15) if fy in (0, fh - 1) else red)
                    c.set(x, y, jitter(col, rnd, 0.04))
                else:
                    c.set(x, y, darken(red, 0.2))
    return p


def hunt_banner_painters(glow=False):
    if glow:
        return {
            "runes": runes_painter(48, glow=True),
            "rune_inner": runes_painter(24, glow=True, inner=True),
            "lantern": lantern_painter(glow=True),
            "cloth": cloth_painter(glow=True),
        }
    return {
        "stone": stone_painter("8A8D91"),
        "stone2": stone_painter("A3A6AA"),
        "pole": wood_rod("4A3220"),
        "bar": wood_rod("5E412A"),
        "brass": metal("C9A24A", rivets=False),
        "gold": metal("E8B84A", rivets=False),
        "chain": metal("4A4F56", rivets=False),
        "lantern": lantern_painter(),
        "cloth": cloth_painter(),
        "cloth_tail": cloth_painter(tail=True),
        "pennant": pennant_painter(),
        "runes": runes_painter(48),
        "rune_inner": runes_painter(24, inner=True),
    }


def hunt_banner_animations():
    p = "animation.hunt_banner"
    return {"format_version": "1.8.0", "animations": {
        f"{p}.idle": {"loop": True, "animation_length": 4.0, "bones": {
            "cloth": {"rotation": kf((0, [0, 0, 0]), (1.0, [4, 0, 1]), (2.0, [0, 0, 0]), (3.0, [-3, 0, -1]), (4.0, [0, 0, 0]))},
            "cloth_tail": {"rotation": kf((0, [0, 0, 0]), (1.0, [9, 0, 2]), (2.0, [2, 0, 0]), (3.0, [-6, 0, -2]), (4.0, [0, 0, 0]))},
            "lantern_l": {"rotation": kf((0, [0, 0, 3]), (2.0, [0, 0, -3]), (4.0, [0, 0, 3]))},
            "lantern_r": {"rotation": kf((0, [0, 0, -3]), (2.0, [0, 0, 3]), (4.0, [0, 0, -3]))},
            "pennant": {"rotation": kf((0, [0, 0, 0]), (0.5, [12, 0, 0]), (1.0, [-6, 0, 0]), (1.5, [10, 0, 0]), (2.0, [0, 0, 0]),
                                       (2.5, [-10, 0, 0]), (3.0, [8, 0, 0]), (3.5, [-4, 0, 0]), (4.0, [0, 0, 0]))},
        }},
        f"{p}.battle": {"loop": True, "animation_length": 1.2, "bones": {
            "cloth": {"rotation": kf((0, [0, 0, 0]), (0.3, [10, 0, 3]), (0.6, [-2, 0, 0]), (0.9, [-8, 0, -3]), (1.2, [0, 0, 0]))},
            "cloth_tail": {"rotation": kf((0, [0, 0, 0]), (0.3, [20, 0, 5]), (0.6, [4, 0, 0]), (0.9, [-16, 0, -5]), (1.2, [0, 0, 0]))},
            "lantern_l": {"rotation": kf((0, [4, 0, 9]), (0.6, [-4, 0, -9]), (1.2, [4, 0, 9]))},
            "lantern_r": {"rotation": kf((0, [-4, 0, -9]), (0.6, [4, 0, 9]), (1.2, [-4, 0, -9]))},
            "pennant": {"rotation": kf((0, [0, 0, 0]), (0.15, [22, 0, 0]), (0.3, [-14, 0, 0]), (0.45, [18, 0, 0]), (0.6, [0, 0, 0]),
                                       (0.75, [-20, 0, 0]), (0.9, [14, 0, 0]), (1.05, [-10, 0, 0]), (1.2, [0, 0, 0]))},
            "root": {"position": kf((0, [0, 0, 0]), (0.6, [0, 0.4, 0]), (1.2, [0, 0, 0]))},
        }},
        f"{p}.fold": {"loop": "hold_on_last_frame", "animation_length": 2.0, "bones": {
            "cloth": {"scale": kf((0, [1, 1, 1]), (0.8, [1, 0.25, 1]), (2.0, [1, 0.25, 1])),
                      "position": kf((0, [0, 0, 0]), (0.8, [0, 16, 0]), (2.0, [0, 16, 0]))},
            "cloth_tail": {"scale": kf((0, [1, 1, 1]), (0.6, [1, 0.01, 1]), (2.0, [1, 0.01, 1]))},
            "lantern_l": {"scale": kf((0, [1, 1, 1]), (0.8, [0.01, 0.01, 0.01]), (2.0, [0.01, 0.01, 0.01]))},
            "lantern_r": {"scale": kf((0, [1, 1, 1]), (0.8, [0.01, 0.01, 0.01]), (2.0, [0.01, 0.01, 0.01]))},
            "root": {"position": kf((0, [0, 0, 0]), (0.8, [0, 0, 0]), (2.0, [0, -70, 0]))},
        }},
    }}


def hunt_banner_files():
    g = hunt_banner_geo()
    write_json(os.path.join(A, "geo/entity/hunt_banner.geo.json"), g.to_json(bounds=(8, 6)))
    g.paint(hunt_banner_painters(), 90).save(os.path.join(A, "textures/entity/hunt_banner.png"))
    g.paint(hunt_banner_painters(glow=True), 90).save(os.path.join(A, "textures/entity/hunt_banner_glowmask.png"))
    write_json(os.path.join(A, "animations/entity/hunt_banner.animation.json"), hunt_banner_animations())


def v09_translations():
    t = tr
    t(f"entity.{NS}.hunt_banner", "Estandarte de Caça", "Hunting Banner")
    h = f"guild.{NS}.hunt."
    t(h + "place", "Área de Caça: %s", "Hunting Ground: %s")
    t(h + "creatures", "Criaturas", "Creatures")
    t(h + "marked", "⚑ A Guilda marcou a %s a %s blocos ao %s.",
      "⚑ The Guild marked the %s, %s blocks to the %s.")
    t(h + "marked_hint", "Siga a seta do rastreador (J). Ao chegar, as criaturas da missão vão surgir em ondas.",
      "Follow the tracker arrow (J). When you get there, the quest's creatures will come in waves.")
    t(h + "no_place", "A Guilda não achou rastros por perto: cace essas criaturas onde encontrá-las.",
      "The Guild found no tracks nearby: hunt these creatures wherever you find them.")
    for d, pt, en in (("n", "norte", "north"), ("ne", "nordeste", "northeast"), ("e", "leste", "east"), ("se", "sudeste", "southeast"),
                      ("s", "sul", "south"), ("sw", "sudoeste", "southwest"), ("w", "oeste", "west"), ("nw", "noroeste", "northwest")):
        t(h + "dir." + d, pt, en)
    t(h + "start", "⚔ As criaturas sentiram sua presença... Prepare-se!", "⚔ The creatures sensed you... Get ready!")
    t(h + "wave", "⚔ Onda %s/%s — %s", "⚔ Wave %s/%s — %s")
    t(h + "reinforcements", "⚔ Reforços — %s", "⚔ Reinforcements — %s")
    t(h + "next_wave", "Próxima onda em %s...", "Next wave in %s...")
    t(h + "hiding", "%s criatura(s) escondida(s) — agora elas brilham.", "%s creature(s) hiding — they glow now.")
    t(h + "no_room", "Sem espaço para as criaturas surgirem aqui: vá para um lugar mais aberto.",
      "No room for the creatures to appear here: move to open ground.")
    t(h + "alpha", "Alfa: %s", "Alpha %s")
    t(h + "peaceful", "No modo Pacífico os monstros não surgem na Área de Caça.",
      "On Peaceful difficulty no monsters come to the Hunting Ground.")
    t(h + "leaving", "Você está saindo da Área de Caça: novas ondas só vêm quando voltar (a %s blocos as criaturas recuam).",
      "You are leaving the Hunting Ground: new waves only come when you return (at %s blocks the creatures retreat).")
    t(h + "retreat", "Você se afastou da %s — as criaturas recuaram. Volte para continuar a caçada.",
      "You left the %s — the creatures retreated. Come back to continue the hunt.")
    t(h + "exhausted", "A %s se esgotou: não vêm mais criaturas. Termine a missão caçando pelo mundo.",
      "The %s is exhausted: no more creatures will come. Finish the quest hunting around the world.")
    t(h + "cleared", "✔ %s limpa! O saque da caçada foi para a sua bolsa.", "✔ %s cleared! The hunt's spoils went to your bag.")
    t(h + "cleared_toast", "Área de Caça limpa!", "Hunting Ground cleared!")
    t(h + "done_elsewhere", "Você já caçou o bastante: a %s foi desfeita.", "You've hunted enough: the %s was dismissed.")
    t(h + "expired", "A %s se dispersou (o prazo acabou). A missão continua: cace as criaturas onde encontrá-las.",
      "The %s dispersed (time ran out). The quest goes on: hunt the creatures wherever you find them.")
    t(h + "removed", "A %s foi desfeita.", "The %s was dismissed.")
    t(h + "status_fight", "Onda %s em andamento", "Wave %s in progress")
    t(h + "status_calm", "esperando o caçador", "waiting for the hunter")
    t(h + "status_owner", "(missão de %s)", "(%s's quest)")
    t(h + "status_left", "· some em %s min", "· vanishes in %s min")
    tk = f"guild.{NS}.tracker."
    t(tk + "hunt_go", "⚑ Vá à %s", "⚑ Go to the %s")
    t(tk + "hunt_ready", "⚔ Prepare-se — %s", "⚔ Get ready — %s")
    t(tk + "hunt_wave", "⚔ Onda %s/%s — %s", "⚔ Wave %s/%s — %s")
    t(tk + "hunt_reinforcements", "⚔ Reforços — %s", "⚔ Reinforcements — %s")
    s = f"screen.{NS}.guild.hunt."
    t(s + "offer", "⚑ Área de Caça", "⚑ Hunting Ground")
    t(s + "offer_desc", "Ao aceitar, a Guilda marca um lugar a 150–500 blocos no bioma das criaturas. Lá elas surgem em %s ondas; "
                        "os mobs da área soltam essências.",
      "When accepted, the Guild marks a spot 150–500 blocks away in the creatures' biome. There they come in %s waves; "
      "the area's mobs drop essences.")
    t(s + "distance", "%s blocos daqui", "%s blocks away")
    t(s + "fighting", "Onda %s de %s em andamento", "Wave %s of %s in progress")
    t(s + "travel", "Siga a seta do rastreador (J)", "Follow the tracker arrow (J)")
    c = f"command.{NS}.guild.hunt."
    t(c + "created", "Área de Caça criada aqui para: %s", "Hunting Ground created here for: %s")
    t(c + "none", "Nenhuma missão de caça ativa (sem área) para isso.", "No active hunting quest (without an area) for that.")
    t(c + "scouted", "%s Área(s) de Caça marcada(s).", "%s Hunting Ground(s) marked.")
    t(c + "line", "%s em %s %s %s (%s blocos) — %s", "%s at %s %s %s (%s blocks) — %s")
    t(c + "phase_travel", "esperando (ondas: %s)", "waiting (waves: %s)")
    t(c + "phase_fight", "em luta, onda %s", "fighting, wave %s")
    t(c + "empty", "Você não tem Áreas de Caça.", "You have no Hunting Grounds.")
    t(c + "cleared", "%s Área(s) de Caça apagada(s).", "%s Hunting Ground(s) removed.")
    t(c + "swept", "Limpeza: %s área(s) vencida(s)/órfã(s) removida(s).", "Sweep: %s expired/orphaned area(s) removed.")
    t(f"screen.{NS}.guild.category.hunt", "Caçada", "Hunt")


V09_WEAPONS = ["longsword", "holy_mace", "greataxe", "ki_gauntlets", "knight_blade",
               "longbow", "twin_daggers", "lute", "arcane_crossbow", "hunter_crossbow"]


def v09_shop():
    """Armas das 10 classes novas na loja da Guilda (ids do armory; o que nao existir some da loja sozinho)."""
    offers = [{"rank": "E", "price": 20, "item": {"id": M(f"{w}_t1"), "count": 1}} for w in V09_WEAPONS]
    offers.append({"rank": "D", "price": 35, "item": {"id": M("knight_shield"), "count": 1}})
    offers += [{"rank": "B", "price": 120, "item": {"id": M(f"{w}_t2"), "count": 1}} for w in V09_WEAPONS]
    write_json(os.path.join(D, "guild_shop/armory_classes.json"), {"offers": offers})


def v09():
    hunt_banner_files()
    v09_shop()
    v09_translations()


def merge_lang():
    """Modo "v09": junta as traducoes novas nos arquivos existentes (sem regerar o resto)."""
    for idx, lg in ((0, "pt_br"), (1, "en_us")):
        path = os.path.join(LANG, lg + ".json")
        cur = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
        for k, v in L.items():
            cur[k] = v[idx]
        write_json(path, dict(sorted(cur.items())))


def write_lang():
    pt = {k: v[0] for k, v in sorted(L.items())}
    en = {k: v[1] for k, v in sorted(L.items())}
    write_json(os.path.join(LANG, "pt_br.json"), pt)
    write_json(os.path.join(LANG, "en_us.json"), en)


def main():
    for r in RANKS:
        adventurer_card(r)
    guild_coin()
    quest_board_front(); quest_board_side(); quest_board_paper()
    counter_front(); counter_side(); counter_top(); counter_book()
    block_models()
    item_models()
    geckolib_files()
    build_quests()
    place_translations()
    shop()
    recipes()
    loot_tables()
    merge_tags()
    ui_translations()
    v07_translations()
    v08()
    v09()
    write_lang()
    print(f"guild: assets gerados ({len(QUESTS)} missoes, {len(L)} traducoes)")


if __name__ == "__main__":
    # "v09": so o estandarte das Areas de Caca + traducoes novas (mescladas), sem regerar missoes/diario/outros assets.
    if len(sys.argv) > 1 and sys.argv[1] == "v09":
        v09()
        merge_lang()
        print(f"guild v09: estandarte + {len(L)} traducoes mescladas")
    else:
        main()
