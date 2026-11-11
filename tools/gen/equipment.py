"""
Gera os assets e dados do modulo equipment (Curios + animacoes de conjuracao).
Uso (na raiz do projeto):  py -I tools/gen/equipment.py

Saidas:
  assets/isekai_mahou/textures/item/*.png, models/item/*.json   focos, aneis, amuleto, talisma
  assets/isekai_mahou/textures/slot/magic_focus.png             icone do espaco "Foco Magico"
  assets/isekai_mahou/player_animations/cast_*.json             animacoes (formato emotecraft v3)
  data/isekai_mahou/recipe/*.json, advancement/recipes/equipment  receitas (+ desbloqueio no livro)
  data/isekai_mahou/curios/slots/*.json, curios/entities/*.json espacos do Curios
  src/main/merge/equipment/data/curios/tags/item/*.json         itens aceitos em cada espaco
  src/main/lang/equipment/{pt_br,en_us}.json                    traducoes
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, lighten, darken, mix  # noqa: E402

RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "isekai_mahou")
DATA = os.path.join(RES, "data", "isekai_mahou")
LANG = os.path.join(ROOT, "src", "main", "lang", "equipment")
MERGE = os.path.join(ROOT, "src", "main", "merge", "equipment")
NS = "isekai_mahou"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


T = (0, 0, 0, 0)
WHITE = (255, 255, 255, 255)

# =====================================================================================================
#  Paletas
# =====================================================================================================
GOLD = {"hi": rgb("fff1a8"), "lt": rgb("f5cc4a"), "md": rgb("d49a22"), "dk": rgb("8f5a12"), "ol": rgb("4a2a08")}
SILVER = {"hi": rgb("ffffff"), "lt": rgb("dfe7f2"), "md": rgb("a9b4c6"), "dk": rgb("6b7488"), "ol": rgb("2c3040")}
WOOD = {"lt": rgb("9a6a3e"), "md": rgb("6e4526"), "dk": rgb("432612")}
EBONY = {"lt": rgb("7a66a0"), "md": rgb("4e3d6e"), "dk": rgb("2a1f42")}

# (luz, base, sombra, motivo, motivo_claro)
ORBS = {
    "fire": (rgb("ffd27a"), rgb("ff6a2b"), rgb("a8261a"), rgb("fff3b0"), rgb("ffe066")),
    "water": (rgb("c8eeff"), rgb("3fa9ff"), rgb("1a4fa8"), rgb("ffffff"), rgb("a8e4ff")),
    "earth": (rgb("f0cf8a"), rgb("c08a45"), rgb("6b4320"), rgb("8fe06a"), rgb("4fa83a")),
    "wind": (rgb("eafff6"), rgb("8fe8c6"), rgb("2f9a7a"), rgb("1f7a5e"), rgb("ffffff")),
    "light": (rgb("fffbe0"), rgb("ffe27a"), rgb("c79a26"), rgb("d08a10"), rgb("ffffff")),
    "shadow": (rgb("c49cf0"), rgb("7a3fc0"), rgb("2a0f4a"), rgb("1a0626"), rgb("e070ff")),
    "arcane": (rgb("eadcff"), rgb("b98cff"), rgb("5a2fa8"), rgb("6a3cc0"), rgb("ffffff")),
}

# Motivos 5x5 dentro do orbe ('M' motivo, 'm' motivo claro)
MOTIFS = {
    "fire": ["..m..",
             ".mM..",
             ".MMm.",
             ".mMM.",
             "..m.."],
    "water": [".....",
              ".m...",
              "M.M.M",
              "...m.",
              "....."],
    "earth": ["...M.",
              "..MM.",
              ".mM..",
              "..m..",
              "....."],
    "wind": [".mmm.",
             "m....",
             "m.MM.",
             ".m..M",
             "..mm."],
    "light": ["..m..",
              "..M..",
              "mMMMm",
              "..M..",
              "..m.."],
    "shadow": [".....",
               ".MMM.",
               "MmmmM",
               ".MMM.",
               "....."],
    "arcane": ["..M..",
               ".m.m.",
               "M.m.M",
               ".m.m.",
               "..M.."],
}

BASE = ["fire", "water", "earth", "wind", "light", "shadow"]


# =====================================================================================================
#  Desenho
# =====================================================================================================
def shade_orb(c, cx, cy, r, pal):
    """Esfera de vidro: 4 tons (luz no canto superior esquerdo), reflexo e luz refletida embaixo."""
    lt, base, dk = pal[0], pal[1], pal[2]
    for y in range(c.h):
        for x in range(c.w):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if d > r:
                continue
            lx = (px - (cx - r * 0.55)) / r
            ly = (py - (cy - r * 0.55)) / r
            t = math.hypot(lx, ly)
            if t < 0.38:
                col = lt
            elif t < 0.68:
                col = mix(lt, base, 0.6)
            elif t < 1.2:
                col = base
            else:
                col = mix(base, dk, 0.65)
            # borda de baixo-direita: luz refletida (vidro)
            if d > r - 1.0 and (px - cx) + (py - cy) > r * 0.9:
                col = mix(base, lt, 0.35)
            c.set(x, y, col)


def stamp(c, rows, ox, oy, palette):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                c.set(ox + x, oy + y, palette[ch])


def outline_painted(c, factor=0.62, only=None):
    """Contorno de 1px com um tom escuro da cor vizinha (mais natural que preto puro)."""
    src = [row[:] for row in c.px]
    for y in range(c.h):
        for x in range(c.w):
            if src[y][x][3] != 0:
                continue
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + ox, y + oy
                if 0 <= nx < c.w and 0 <= ny < c.h and src[ny][nx][3] > 0:
                    if only is not None and not only(nx, ny):
                        continue
                    n = src[ny][nx]
                    c.set(x, y, darken(n, factor))
                    break


def handle(c, wood, band, pommel=None):
    """Cabo diagonal (de cima-direita para baixo-esquerda), 2px de largura com sombreado."""
    pairs = [(11, 9), (12, 8), (13, 7), (14, 6), (15, 5)]
    for y, x in pairs:
        c.set(x, y, wood["lt"])
        c.set(x + 1, y, wood["dk"] if y > 11 else wood["md"])
    # faixa metalica no meio do cabo
    c.set(7, 13, band["lt"]); c.set(8, 13, band["md"])
    if pommel:
        c.set(4, 15, pommel[1]); c.set(4, 14, pommel[0]); c.set(3, 15, pommel[2])
    else:
        c.set(4, 15, band["md"]); c.set(4, 14, band["lt"])


def draw_focus(el, greater=False):
    pal = ORBS[el]
    c = Canvas(16, 16)
    if greater:
        cx, cy, r = 10.0, 5.0, 4.05
    else:
        cx, cy, r = 10.5, 4.5, 3.55
    c.circle(cx, cy, r, pal[1])
    shade_orb(c, cx, cy, r, pal)
    # motivo do elemento (centro do orbe)
    mo = MOTIFS[el]
    stamp(c, mo, int(cx) - 2, int(cy) - 2, {"M": pal[3], "m": pal[4]})
    # reflexo especular
    hx, hy = int(cx - r * 0.5), int(cy - r * 0.55)
    c.set(hx, hy, WHITE)
    c.set(hx + 1, hy, lighten(pal[0], 0.6))
    c.set(hx, hy + 1, lighten(pal[0], 0.6))
    outline_painted(c, 0.68)

    metal = SILVER if greater else GOLD
    if greater:
        # garras em crescente que abraçam o orbe + gemas laterais
        for (x, y, k) in [(5, 5, "lt"), (5, 6, "md"), (5, 7, "md"), (6, 8, "dk"), (6, 4, "hi"),
                          (15, 5, "md"), (15, 6, "dk"), (15, 7, "dk"), (14, 8, "dk"), (14, 4, "lt")]:
            c.set(x, y, metal[k])
        c.set(5, 3, pal[0]); c.set(15, 3, pal[1])
        for x in range(7, 14):
            c.set(x, 9, metal["lt"] if x < 10 else metal["md"])
        c.set(6, 9, metal["ol"]); c.set(14, 9, metal["ol"])
        for x in range(8, 13):
            c.set(x, 10, metal["md"] if x < 11 else metal["dk"])
        c.set(9, 11, metal["lt"]); c.set(10, 11, metal["dk"])
        c.set(10, 10, pal[1])  # gema no colar
        handle(c, EBONY, SILVER, (pal[1], pal[2], pal[0]))
        # brilhos (estrelas de 4 pontas)
        for (sx, sy, col) in [(2, 2, pal[0]), (13, 13, pal[0])]:
            c.set(sx, sy, WHITE)
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if c.get(sx + ox, sy + oy)[3] == 0:
                    c.set(sx + ox, sy + oy, col)
        c.set(3, 6, lighten(pal[0], 0.5))
    else:
        for (x, y, k) in [(6, 5, "lt"), (6, 6, "md"), (7, 7, "md"), (14, 5, "md"), (14, 6, "dk"), (13, 7, "dk")]:
            c.set(x, y, metal[k])
        for x in range(8, 14):
            c.set(x, 8, metal["lt"] if x < 11 else metal["md"])
        c.set(7, 8, metal["ol"]); c.set(14, 8, metal["ol"])
        for x in range(9, 13):
            c.set(x, 9, metal["md"] if x < 11 else metal["dk"])
        c.set(9, 10, metal["lt"]); c.set(10, 10, metal["dk"]); c.set(11, 10, metal["ol"])
        handle(c, WOOD, GOLD)
    return c


def ring_band(c, cx, cy, rx, ry, th, metal):
    """Aro de anel visto de cima/inclinado: parte de tras escura, frente clara com brilho."""
    for y in range(c.h):
        for x in range(c.w):
            px, py = x + 0.5, y + 0.5
            o = ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2
            i = ((px - cx) / (rx - th)) ** 2 + ((py - cy) / (ry - th * 0.7)) ** 2
            if o <= 1.0 and i > 1.0:
                if py < cy - 0.3:
                    col = metal["dk"] if px > cx else metal["md"]
                else:
                    col = metal["lt"] if px < cx + 1 else metal["md"]
                    if py > cy + ry - 1.2 and px < cx:
                        col = metal["hi"]
                c.set(x, y, col)


def gem(c, rows, ox, oy, lt, md, dk):
    stamp(c, rows, ox, oy, {"L": lt, "M": md, "D": dk, "W": WHITE})


GEM_ROWS = ["..W..",
            ".WLM.",
            "LLMMD",
            ".MMD.",
            "..D.."]


def draw_ring(metal, g_lt, g_md, g_dk, leaves=None):
    c = Canvas(16, 16)
    ring_band(c, 8.0, 10.5, 6.0, 4.0, 1.8, metal)
    # engaste
    for (x, y, k) in [(5, 6, "md"), (10, 6, "dk"), (6, 7, "lt"), (9, 7, "md"), (7, 7, "lt"), (8, 7, "md")]:
        c.set(x, y, metal[k])
    gem(c, GEM_ROWS, 5, 2, g_lt, g_md, g_dk)
    if leaves:
        c.set(3, 5, leaves[0]); c.set(4, 6, leaves[1]); c.set(4, 5, leaves[0])
        c.set(12, 5, leaves[0]); c.set(11, 6, leaves[1]); c.set(12, 4, leaves[1])
    outline_painted(c, 0.7)
    return c


def draw_amulet():
    c = Canvas(16, 16)
    # corrente (elos alternados)
    chain = [(3, 1), (4, 2), (4, 3), (5, 4), (6, 5), (6, 6), (7, 7)]
    for k, (x, y) in enumerate(chain):
        c.set(x, y, GOLD["lt"] if k % 2 == 0 else GOLD["dk"])
        c.set(15 - x, y, GOLD["md"] if k % 2 == 0 else GOLD["dk"])
    c.set(7, 8, GOLD["lt"]); c.set(8, 8, GOLD["md"])
    # medalhao
    c.circle(8.0, 11.5, 3.9, GOLD["md"])
    c.shade_sphere(8.0, 11.5, 3.9, light=0.5, dark=0.45)
    pal = ORBS["arcane"]
    c.circle(8.0, 11.5, 2.5, pal[1])
    shade_orb(c, 8.0, 11.5, 2.5, pal)
    c.set(7, 10, WHITE); c.set(8, 10, pal[0])
    c.set(8, 12, pal[4]); c.set(9, 11, pal[3])
    # pontas da moldura
    c.set(8, 15, GOLD["dk"]); c.set(4, 11, GOLD["lt"]); c.set(12, 12, GOLD["dk"])
    outline_painted(c, 0.7, only=lambda x, y: y >= 8)
    # brilho
    c.set(13, 8, WHITE); c.set(13, 7, pal[0]); c.set(12, 8, pal[0]); c.set(14, 8, pal[0]); c.set(13, 9, pal[0])
    return c


def draw_charm():
    c = Canvas(16, 16)
    red, red_dk, red_lt = rgb("c8283a"), rgb("7a1424"), rgb("f0606a")
    jade = {"hi": rgb("e8fff0"), "lt": rgb("b4ecc8"), "md": rgb("78cc9c"), "dk": rgb("3f8f66"), "ol": rgb("1d4a33")}
    # cordao
    c.set(7, 0, red); c.set(9, 0, red_dk); c.set(8, 1, red); c.set(8, 2, red_dk)
    # disco de jade (bi) com furo
    cx, cy, r = 8.0, 7.5, 4.6
    c.circle(cx, cy, r, jade["md"])
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if d <= r:
                t = ((px - cx) * -0.7 + (py - cy) * -0.7) / r
                c.set(x, y, jade["lt"] if t > 0.35 else (jade["md"] if t > -0.35 else jade["dk"]))
            if d <= 1.75:
                c.set(x, y, T)
    # entalhe de lotus em volta do furo
    for (x, y) in [(8, 4), (5, 7), (11, 8), (8, 11), (6, 5), (10, 5), (6, 10), (10, 10)]:
        c.set(x, y, jade["hi"] if y < 8 else jade["dk"])
    c.set(5, 5, jade["hi"])
    outline_painted(c, 0.62, only=lambda x, y: y >= 3 and math.hypot(x + 0.5 - cx, y + 0.5 - cy) > 2.6)
    # no dourado + borla vermelha
    c.set(7, 13, GOLD["lt"]); c.set(8, 13, GOLD["md"]); c.set(9, 13, GOLD["dk"])
    for x in (7, 8, 9):
        c.set(x, 14, red if x < 9 else red_dk)
    c.set(7, 15, red_lt); c.set(8, 15, red); c.set(9, 15, red_dk)
    c.set(8, 12, GOLD["dk"])
    return c


def draw_slot_icon():
    c = Canvas(16, 16)
    g = (85, 85, 85, 255)
    rows = ["................",
            "................",
            "......aaaa......",
            ".....a....a.....",
            "....a......a....",
            "....a.a....a....",
            "....a..a...a....",
            "....a......a....",
            ".....a....a.....",
            "....a.aaaa.a....",
            ".....a....a.....",
            "......a..a......",
            ".......aa.......",
            "......a..a......",
            ".....aaaaaa.....",
            "................"]
    c.from_rows(rows, {"a": g})
    return c


# =====================================================================================================
#  Itens
# =====================================================================================================
EL_PT = {"fire": "Fogo", "water": "Água", "earth": "Terra", "wind": "Vento", "light": "Luz", "shadow": "Sombra"}
EL_EN = {"fire": "Fire", "water": "Water", "earth": "Earth", "wind": "Wind", "light": "Light", "shadow": "Shadow"}


def gen_textures():
    tex = os.path.join(ASSETS, "textures", "item")
    models = os.path.join(ASSETS, "models", "item")
    ids = []
    for el in BASE:
        for greater in (False, True):
            iid = ("greater_focus_" if greater else "focus_") + el
            draw_focus(el, greater).save(os.path.join(tex, iid + ".png"))
            ids.append(iid)
    draw_focus("arcane").save(os.path.join(tex, "arcane_focus.png")); ids.append("arcane_focus")
    draw_ring(GOLD, rgb("d8f6ff"), rgb("5ab4ff"), rgb("2556c8")).save(os.path.join(tex, "mana_ring.png")); ids.append("mana_ring")
    draw_ring(SILVER, rgb("c8ffd0"), rgb("3cc86a"), rgb("15703a"), leaves=(rgb("7fe05a"), rgb("3c9a2e"))) \
        .save(os.path.join(tex, "regen_ring.png")); ids.append("regen_ring")
    draw_amulet().save(os.path.join(tex, "arcane_amulet.png")); ids.append("arcane_amulet")
    draw_charm().save(os.path.join(tex, "meditation_charm.png")); ids.append("meditation_charm")
    for iid in ids:
        write_json(os.path.join(models, iid + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + iid}})
    draw_slot_icon().save(os.path.join(ASSETS, "textures", "slot", "magic_focus.png"))
    return ids


# =====================================================================================================
#  Receitas
# =====================================================================================================
def shaped(rid, pattern, key, result, unlock, count=1):
    write_json(os.path.join(DATA, "recipe", rid + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": pattern,
        "key": {k: {"item": v} for k, v in key.items()},
        "result": {"id": NS + ":" + result, "count": count},
    })
    # Libera a receita no livro de receitas quando o jogador pega o material-chave
    write_json(os.path.join(DATA, "advancement", "recipes", "equipment", rid + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": NS + ":" + rid}},
            "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": unlock}]}},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": [NS + ":" + rid]},
    })


def gen_recipes():
    crystal = NS + ":mana_crystal"
    for el in BASE:
        ess = NS + ":essence_" + el
        shaped("focus_" + el, [" E ", "EME", " G "],
               {"E": ess, "M": crystal, "G": "minecraft:gold_ingot"}, "focus_" + el, ess)
        shaped("greater_focus_" + el, ["EDE", "MFM", "EDE"],
               {"E": ess, "D": "minecraft:diamond", "M": crystal, "F": NS + ":focus_" + el},
               "greater_focus_" + el, NS + ":focus_" + el)
    arc = NS + ":essence_arcane"
    shaped("arcane_focus", [" A ", "AMA", " G "],
           {"A": arc, "M": crystal, "G": "minecraft:gold_ingot"}, "arcane_focus", arc)
    shaped("mana_ring", ["AMA", "G G", " G "],
           {"A": arc, "M": crystal, "G": "minecraft:gold_ingot"}, "mana_ring", crystal)
    shaped("regen_ring", ["WME", "I I", " I "],
           {"W": NS + ":essence_water", "E": NS + ":essence_earth", "M": crystal,
            "I": "minecraft:iron_ingot"}, "regen_ring", crystal)
    shaped("arcane_amulet", ["S S", "G G", "ADA"],
           {"S": "minecraft:string", "G": "minecraft:gold_nugget", "A": arc, "D": "minecraft:diamond"},
           "arcane_amulet", arc)
    shaped("meditation_charm", [" S ", "AMA", " L "],
           {"S": "minecraft:string", "A": "minecraft:amethyst_shard", "M": crystal,
            "L": NS + ":essence_light"}, "meditation_charm", crystal)


# =====================================================================================================
#  Curios
# =====================================================================================================
def gen_curios(ids):
    write_json(os.path.join(DATA, "curios", "slots", "magic_focus.json"), {
        "order": 5, "size": 1, "icon": NS + ":slot/magic_focus", "add_cosmetic": False,
        "validators": ["curios:tag"]})
    # dois espacos de anel (o Curios junta com o ring.json dele e fica com o maior tamanho)
    write_json(os.path.join(DATA, "curios", "slots", "ring.json"), {"size": 2})
    write_json(os.path.join(DATA, "curios", "entities", "equipment_player.json"), {
        "entities": ["player"], "slots": ["magic_focus", "necklace", "ring", "charm"]})
    foci = [i for i in ids if "focus" in i]
    tags = {"magic_focus": foci, "ring": ["mana_ring", "regen_ring"], "necklace": ["arcane_amulet"],
            "charm": ["meditation_charm"]}
    for slot, items in tags.items():
        write_json(os.path.join(MERGE, "data", "curios", "tags", "item", slot + ".json"),
                   {"replace": False, "values": [NS + ":" + i for i in items]})


# =====================================================================================================
#  Animacoes do jogador (playerAnimator / emotecraft v3, angulos em graus)
#  Convencoes do modelo: pitch negativo levanta o braco para a frente (-90 = horizontal, -180 = para cima);
#  roll positivo abre o braco DIREITO para fora (no esquerdo, negativo); yaw negativo leva a mao direita
#  para o centro do corpo (no esquerdo, positivo).
# =====================================================================================================
def kf(tick, easing="EASEINOUTSINE", **parts):
    m = {"tick": tick, "easing": easing}
    for part, axes in parts.items():
        m[part] = axes
    return m


def anim(name, desc, end, stop, moves, loop=None):
    # easeBeforeKeyframe: a suavização de cada quadro vale para o trecho que CHEGA nele
    emote = {"beginTick": 0, "endTick": end, "stopTick": stop, "degrees": True, "nsfw": False,
             "easeBeforeKeyframe": True, "moves": moves}
    if loop is not None:
        emote["isLoop"] = True
        emote["returnTick"] = loop
    else:
        emote["isLoop"] = False
        emote["returnTick"] = 0
    write_json(os.path.join(ASSETS, "player_animations", name + ".json"), {
        "version": 3, "name": name, "author": "Isekai Mahou", "description": desc, "emote": emote})


def gen_animations():
    # Conjuracao rapida: puxa a mao, estende para a frente (mira), segura e abaixa.
    anim("cast_quick", "Mao estendida ao lancar uma magia", 14, 17, [
        kf(0, "LINEAR", rightArm={"pitch": -35, "yaw": 12, "roll": 14}, leftArm={"pitch": 8, "yaw": 0, "roll": -6}),
        kf(3, "EASEOUTBACK", rightArm={"pitch": -100, "yaw": -8, "roll": 2}, leftArm={"pitch": 24, "yaw": 0, "roll": -14}),
        kf(9, "EASEINOUTSINE", rightArm={"pitch": -88, "yaw": -6, "roll": 0}, leftArm={"pitch": 20, "yaw": 0, "roll": -12}),
        kf(14, "EASEINOUTSINE", rightArm={"pitch": -18, "yaw": 0, "roll": 6}, leftArm={"pitch": 4, "yaw": 0, "roll": -5}),
    ])
    # Canalizando: bracos erguidos a frente, maos afastadas, respirando em loop.
    anim("cast_channel", "Bracos erguidos durante a conjuracao", 40, 44, [
        kf(0, "LINEAR", rightArm={"pitch": -60, "yaw": 0, "roll": 10}, leftArm={"pitch": -60, "yaw": 0, "roll": -10}),
        kf(6, "EASEOUTQUAD", rightArm={"pitch": -124, "yaw": -12, "roll": 18}, leftArm={"pitch": -124, "yaw": 12, "roll": -18}),
        kf(8, "EASEINOUTSINE", rightArm={"pitch": -125, "yaw": -12, "roll": 18}, leftArm={"pitch": -125, "yaw": 12, "roll": -18}),
        kf(24, "EASEINOUTSINE", rightArm={"pitch": -133, "yaw": -8, "roll": 25}, leftArm={"pitch": -133, "yaw": 8, "roll": -25}),
        kf(40, "EASEINOUTSINE", rightArm={"pitch": -125, "yaw": -12, "roll": 18}, leftArm={"pitch": -125, "yaw": 12, "roll": -18}),
    ], loop=8)
    # Suprema: bracos ao ceu (cabeca para tras, pernas abertas), depois empurra as duas maos para a frente.
    legs0 = {"pitch": 0, "roll": 0}
    anim("cast_ultimate", "Pose dramatica de magia suprema", 36, 40, [
        kf(0, "LINEAR", rightArm={"pitch": -30, "yaw": 0, "roll": 10}, leftArm={"pitch": -30, "yaw": 0, "roll": -10},
           head={"pitch": 0}, rightLeg=dict(legs0), leftLeg=dict(legs0)),
        kf(8, "EASEOUTQUAD", rightArm={"pitch": -165, "yaw": 0, "roll": 28}, leftArm={"pitch": -165, "yaw": 0, "roll": -28},
           head={"pitch": -28}, rightLeg={"pitch": 0, "roll": 9}, leftLeg={"pitch": 0, "roll": -9}),
        kf(14, "EASEINOUTSINE", rightArm={"pitch": -172, "yaw": 0, "roll": 20}, leftArm={"pitch": -172, "yaw": 0, "roll": -20},
           head={"pitch": -32}, rightLeg={"pitch": 0, "roll": 10}, leftLeg={"pitch": 0, "roll": -10}),
        kf(18, "EASEOUTEXPO", rightArm={"pitch": -96, "yaw": -14, "roll": 0}, leftArm={"pitch": -96, "yaw": 14, "roll": 0},
           head={"pitch": 4}, rightLeg={"pitch": -12, "roll": 8}, leftLeg={"pitch": 14, "roll": -8}),
        kf(28, "EASEINOUTSINE", rightArm={"pitch": -90, "yaw": -12, "roll": 0}, leftArm={"pitch": -90, "yaw": 12, "roll": 0},
           head={"pitch": 0}, rightLeg={"pitch": -10, "roll": 7}, leftLeg={"pitch": 12, "roll": -7}),
        kf(36, "EASEINOUTSINE", rightArm={"pitch": -14, "yaw": 0, "roll": 5}, leftArm={"pitch": -14, "yaw": 0, "roll": -5},
           head={"pitch": 0}, rightLeg=dict(legs0), leftLeg=dict(legs0)),
    ])
    # Em si mesmo: cruza os bracos no peito (cabeca baixa) e abre de uma vez.
    anim("cast_self", "Bracos cruzados que se abrem ao conjurar em si mesmo", 22, 25, [
        kf(0, "LINEAR", rightArm={"pitch": -20, "yaw": 0, "roll": 6}, leftArm={"pitch": -20, "yaw": 0, "roll": -6}, head={"pitch": 0}),
        kf(4, "EASEOUTQUAD", rightArm={"pitch": -72, "yaw": -42, "roll": 0}, leftArm={"pitch": -72, "yaw": 42, "roll": 0}, head={"pitch": 16}),
        kf(9, "EASEINOUTSINE", rightArm={"pitch": -76, "yaw": -46, "roll": 0}, leftArm={"pitch": -76, "yaw": 46, "roll": 0}, head={"pitch": 20}),
        kf(13, "EASEOUTBACK", rightArm={"pitch": -32, "yaw": 0, "roll": 58}, leftArm={"pitch": -32, "yaw": 0, "roll": -58}, head={"pitch": -12}),
        kf(22, "EASEINOUTSINE", rightArm={"pitch": -4, "yaw": 0, "roll": 6}, leftArm={"pitch": -4, "yaw": 0, "roll": -6}, head={"pitch": 0}),
    ])


# =====================================================================================================
#  Traducoes
# =====================================================================================================
def gen_lang():
    pt, en = {}, {}
    for el in BASE:
        pt["item.isekai_mahou.focus_" + el] = "Foco de " + EL_PT[el]
        en["item.isekai_mahou.focus_" + el] = EL_EN[el] + " Focus"
        pt["item.isekai_mahou.greater_focus_" + el] = "Foco Maior de " + EL_PT[el]
        en["item.isekai_mahou.greater_focus_" + el] = "Greater " + EL_EN[el] + " Focus"
    pt.update({
        "item.isekai_mahou.arcane_focus": "Foco Arcano",
        "item.isekai_mahou.mana_ring": "Anel de Mana",
        "item.isekai_mahou.regen_ring": "Anel da Renovação",
        "item.isekai_mahou.arcane_amulet": "Amuleto Arcano",
        "item.isekai_mahou.meditation_charm": "Talismã da Meditação",
        "item.isekai_mahou.mana_ring.desc": "Um cristal de mana preso em ouro: o corpo guarda mais magia.",
        "item.isekai_mahou.regen_ring.desc": "Água e Terra em equilíbrio: a mana volta mais depressa.",
        "item.isekai_mahou.arcane_amulet.desc": "Um diamante lapidado em runas que amplifica toda magia.",
        "item.isekai_mahou.meditation_charm.desc": "Jade fria que acalma a mente durante a meditação.",
        "tooltip.isekai_mahou.equipment.focus_bonus": "+%s%% de poder em magias de %s",
        "tooltip.isekai_mahou.equipment.focus_fusions": "Também nas fusões: %s",
        "tooltip.isekai_mahou.equipment.arcane_focus_bonus": "+%s%% de poder em magias de qualquer elemento",
        "tooltip.isekai_mahou.equipment.focus_hint": "Equipe no espaço de Foco Mágico (botão dos Acessórios no inventário)",
        "tooltip.isekai_mahou.equipment.meditation_regen": "Meditando: +%s%% de regeneração de mana",
        "tooltip.isekai_mahou.equipment.meditation_heal": "Meditando: recupera vida a cada %s s",
        "tooltip.isekai_mahou.equipment.meditation_hint": "Para meditar: agache parado no chão",
        "message.isekai_mahou.equipment.meditation_calm": "A mente se aquieta…",
        "message.isekai_mahou.equipment.curios_key_moved": "Isekai Mahou: o inventário de Acessórios (Curios) agora abre "
                                                           "com %s, porque %s é a tecla da magia suprema. Dá para trocar "
                                                           "em Opções > Controles.",
        "curios.identifier.magic_focus": "Foco Mágico",
        "curios.modifiers.magic_focus": "Quando usado como foco:",
    })
    en.update({
        "item.isekai_mahou.arcane_focus": "Arcane Focus",
        "item.isekai_mahou.mana_ring": "Mana Ring",
        "item.isekai_mahou.regen_ring": "Ring of Renewal",
        "item.isekai_mahou.arcane_amulet": "Arcane Amulet",
        "item.isekai_mahou.meditation_charm": "Meditation Charm",
        "item.isekai_mahou.mana_ring.desc": "A mana crystal set in gold: your body holds more magic.",
        "item.isekai_mahou.regen_ring.desc": "Water and Earth in balance: mana flows back faster.",
        "item.isekai_mahou.arcane_amulet.desc": "A rune-cut diamond that amplifies every spell.",
        "item.isekai_mahou.meditation_charm.desc": "Cool jade that quiets the mind during meditation.",
        "tooltip.isekai_mahou.equipment.focus_bonus": "+%s%% power to %s spells",
        "tooltip.isekai_mahou.equipment.focus_fusions": "Also for fusions: %s",
        "tooltip.isekai_mahou.equipment.arcane_focus_bonus": "+%s%% power to spells of any element",
        "tooltip.isekai_mahou.equipment.focus_hint": "Equip it in the Magic Focus slot (Curios button in the inventory)",
        "tooltip.isekai_mahou.equipment.meditation_regen": "While meditating: +%s%% mana regeneration",
        "tooltip.isekai_mahou.equipment.meditation_heal": "While meditating: restores health every %s s",
        "tooltip.isekai_mahou.equipment.meditation_hint": "To meditate: crouch still on the ground",
        "message.isekai_mahou.equipment.meditation_calm": "Your mind grows still…",
        "message.isekai_mahou.equipment.curios_key_moved": "Isekai Mahou: the Accessories (Curios) inventory now opens "
                                                           "with %s, because %s is the ultimate spell key. You can change "
                                                           "it in Options > Controls.",
        "curios.identifier.magic_focus": "Magic Focus",
        "curios.modifiers.magic_focus": "When used as a focus:",
    })
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(pt.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(en.items())))


if __name__ == "__main__":
    item_ids = gen_textures()
    gen_recipes()
    gen_curios(item_ids)
    gen_animations()
    gen_lang()
    print("equipment: %d itens, receitas, curios, animacoes e traducoes gerados" % len(item_ids))
