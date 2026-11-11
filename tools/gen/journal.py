"""
Gera os assets e dados do modulo journal (rode da raiz do projeto: py -I tools/gen/journal.py):
  - Espirito Guia: modelo GeckoLib (.geo.json, UV empacotado), animacoes (idle/appear/vanish), textura + glowmask
  - modelo do ovo (guide_spirit_spawn_egg)
  - capitulos do Diario: data/isekai_mahou/journal/chapters.json (rota-guia v0.9: 12 capitulos, todos os chefes e
    portais com "locate", missoes "A Ascensao" com o tributo pronto)
  - traducoes (pt_br/en_us) em src/main/lang/journal: diario, passos, dicas (longa e curta do rastreador), telas,
    bestiario (todas as criaturas do catalogo)
  - v1.0: rota-guia POR CLASSE (data/isekai_mahou/journal/classes.json, 15 capitulos paralelos — tools/gen/journal_classes.py),
    capitulo do Mundo Oco (chefes do hueco_mundo) antes do Dragao, passo SECRETO 150->200 depois dele, textos sem a Roda
    Elemental (9 loadouts), categorias Magicas/Fisicas/Destreza e as 15 classes novas.
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixelart import Canvas, darken, lighten, mix  # noqa: E402
import journal_classes  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
LANG = os.path.join(ROOT, "src/main/lang/journal")
TAU = 2 * math.pi


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  Modelo com UV de caixa empacotado automaticamente
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


class Model:
    def __init__(self, ident, tw):
        self.ident, self.tw, self.th = ident, tw, 0
        self.bones, self.cubes = [], []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name}
        if parent:
            b["parent"] = parent
        b["pivot"] = [float(v) for v in pivot]
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        self.bones.append(b)
        return name

    def box(self, bone, origin, size, paint):
        self.cubes.append({"bone": bone, "origin": [float(v) for v in origin], "size": [int(s) for s in size],
                           "paint": paint, "uv": None})

    def pack(self):
        items = sorted(self.cubes, key=lambda cb: (-(cb["size"][2] + cb["size"][1]), -(cb["size"][2] + cb["size"][0])))
        x = y = rowh = 0
        for cb in items:
            w, h, d = cb["size"]
            W, Hh = 2 * (d + w), d + h
            if x + W > self.tw:
                x, y, rowh = 0, y + rowh, 0
            cb["uv"] = [x, y]
            x += W
            rowh = max(rowh, Hh)
        self.th = max(32, ((y + rowh + 15) // 16) * 16)

    def build(self, vb_w, vb_h, vb_off_y):
        self.pack()
        c, g = Canvas(self.tw, self.th), Canvas(self.tw, self.th)
        for cb in self.cubes:
            w, h, d = cb["size"]
            cb["paint"](c, g, box_faces(cb["uv"][0], cb["uv"][1], w, h, d))
        bones = []
        for b in self.bones:
            o = dict(b)
            cubes = [{"origin": cb["origin"], "size": cb["size"], "uv": cb["uv"]} for cb in self.cubes if cb["bone"] == b["name"]]
            if cubes:
                o["cubes"] = cubes
            bones.append(o)
        save_json(os.path.join(A, "geo/entity", self.ident + ".geo.json"), {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {"identifier": "geometry." + self.ident, "texture_width": self.tw, "texture_height": self.th,
                                "visible_bounds_width": vb_w, "visible_bounds_height": vb_h,
                                "visible_bounds_offset": [0, vb_off_y, 0]},
                "bones": bones}]})
        return c, g


def fill_face(c, rect, fn):
    """Pinta cada pixel da face com fn(i, j, w, h) -> rgba (ou None para pular)."""
    x, y, w, h = rect
    for j in range(h):
        for i in range(w):
            p = fn(i, j, w, h)
            if p is not None:
                c.set(x + i, y + j, p)


def solid(col, glow=None, top=0.25, bottom=0.2, edge=0.0):
    """Pintor simples: cor com topo mais claro, base mais escura e (opcional) borda reforcada; glow pinta a glowmask."""
    def paint(c, g, faces):
        for k, r in faces.items():
            base = lighten(col, top) if k == "top" else darken(col, bottom) if k == "bottom" else col

            def px(i, j, w, h, base=base):
                p = mix(lighten(base, 0.12), darken(base, 0.08), j / max(1, h - 1))
                if edge and (i in (0, w - 1) or j in (0, h - 1)):
                    p = (p[0], p[1], p[2], min(255, p[3] + edge))
                return p
            fill_face(c, r, px)
            if glow:
                fill_face(g, r, lambda i, j, w, h: glow)
    return paint


# =====================================================================================
#  ESPIRITO GUIA
# =====================================================================================
SHELL = (214, 246, 255, 150)
CORE = (255, 246, 205, 255)
GOLD = (255, 216, 107, 255)
FLAME = (255, 232, 150, 235)
MOTE = (225, 250, 255, 255)
EYE = (32, 42, 86, 255)


def paint_shell(c, g, faces):
    for k, (x, y, w, h) in faces.items():
        for j in range(h):
            for i in range(w):
                # Brilho de "bolha": borda mais opaca, centro mais transparente, topo claro.
                cx, cy = (i + 0.5) / w - 0.5, (j + 0.5) / h - 0.5
                r = min(1.0, math.hypot(cx, cy) * 1.6)
                a = int(105 + 110 * r)
                col = lighten(SHELL, 0.35 * (1 - (j / max(1, h - 1)))) if k != "bottom" else darken(SHELL, 0.1)
                if k == "top":
                    col = lighten(SHELL, 0.45)
                c.set(x + i, y + j, (col[0], col[1], col[2], a))
        if k == "front":
            # Rostinho: olhos de 2 px (topo azulado = reflexo), bochechas rosadas e um sorriso pequeno
            for ex in (2, 5):
                c.set(x + ex, y + 3, (78, 104, 182, 255))
                c.set(x + ex, y + 4, EYE)
            c.set(x + 1, y + 5, (255, 165, 200, 175))
            c.set(x + 6, y + 5, (255, 165, 200, 175))
            c.set(x + 3, y + 6, (96, 116, 176, 210))
            c.set(x + 4, y + 6, (96, 116, 176, 210))
        if k in ("left", "right", "back"):
            # Faíscas de mana presas na "bolha"
            for sx, sy in ((1, 2), (5, 5), (3, 1)) if k != "back" else ((2, 2), (6, 4), (4, 6)):
                if sx < w and sy < h:
                    c.set(x + sx, y + sy, (255, 255, 255, 215))
                    g.set(x + sx, y + sy, (255, 255, 255, 200))
        if k == "top":
            # Reflexo de luz
            c.set(x + 2, y + 2, (255, 255, 255, 230))
            c.set(x + 3, y + 2, (255, 255, 255, 200))
            c.set(x + 2, y + 3, (255, 255, 255, 190))


def paint_core(c, g, faces):
    for k, (x, y, w, h) in faces.items():
        for j in range(h):
            for i in range(w):
                d = math.hypot((i + 0.5) / w - 0.5, (j + 0.5) / h - 0.5)
                col = mix(lighten(CORE, 0.6), mix(CORE, GOLD, 0.35), min(1.0, d * 1.8))
                c.set(x + i, y + j, col)
                g.set(x + i, y + j, col)


def paint_tail(alpha_top, alpha_bottom, hue_shift=0.0):
    def paint(c, g, faces):
        for k, (x, y, w, h) in faces.items():
            for j in range(h):
                t = j / max(1, h - 1)
                a = int(alpha_top + (alpha_bottom - alpha_top) * t)
                base = mix(SHELL, (170, 225, 255, 255), hue_shift + 0.3 * t)
                if k == "top":
                    base = lighten(base, 0.3)
                for i in range(w):
                    edge = 25 if i in (0, w - 1) else 0
                    c.set(x + i, y + j, (base[0], base[1], base[2], min(255, a + edge)))
    return paint


def paint_flame(c, g, faces):
    for k, (x, y, w, h) in faces.items():
        for j in range(h):
            t = j / max(1, h - 1)   # 0 = ponta (topo), 1 = base
            col = mix((255, 255, 236, 240), FLAME, t)
            for i in range(w):
                c.set(x + i, y + j, col)
                g.set(x + i, y + j, (col[0], col[1], col[2], 255))


def paint_halo(c, g, faces):
    for k, (x, y, w, h) in faces.items():
        for j in range(h):
            for i in range(w):
                col = lighten(GOLD, 0.35) if k == "top" else GOLD if k != "bottom" else darken(GOLD, 0.2)
                if (i + j) % 4 == 0:
                    col = lighten(col, 0.4)
                c.set(x + i, y + j, col)
                g.set(x + i, y + j, col)


def paint_mote(c, g, faces):
    for k, (x, y, w, h) in faces.items():
        fill_face(c, (x, y, w, h), lambda i, j, w, h: MOTE)
        fill_face(g, (x, y, w, h), lambda i, j, w, h: MOTE)


def guide_spirit():
    m = Model("guide_spirit", 64)
    m.bone("root", pivot=(0, 0, 0))
    m.bone("body", "root", pivot=(0, 11, 0))
    m.box("body", (-4, 7, -4), (8, 8, 8), paint_shell)
    m.bone("core", "body", pivot=(0, 11, 0))
    m.box("core", (-2, 9, -2), (4, 4, 4), paint_core)
    m.bone("crest", "body", pivot=(0, 15, 0))
    m.box("crest", (-1, 15, -1), (2, 4, 2), paint_flame)
    m.bone("flame_l", "crest", pivot=(2, 15, 0), rotation=(0, 0, -22))
    m.box("flame_l", (1.5, 15, -0.5), (1, 3, 1), paint_flame)
    m.bone("flame_r", "crest", pivot=(-2, 15, 0), rotation=(0, 0, 22))
    m.box("flame_r", (-2.5, 15, -0.5), (1, 3, 1), paint_flame)
    m.bone("arm_l", "body", pivot=(5.5, 12.5, 0))
    m.box("arm_l", (5, 8.5, -1), (2, 4, 2), paint_tail(150, 70, 0.1))
    m.bone("arm_r", "body", pivot=(-5.5, 12.5, 0))
    m.box("arm_r", (-7, 8.5, -1), (2, 4, 2), paint_tail(150, 70, 0.1))
    m.bone("tail1", "root", pivot=(0, 7.5, 0))
    m.box("tail1", (-3, 5, -3), (6, 3, 6), paint_tail(165, 120))
    m.bone("tail2", "tail1", pivot=(0, 5, 0.5))
    m.box("tail2", (-2, 2.5, -1.5), (4, 3, 4), paint_tail(130, 85, 0.15))
    m.bone("tail3", "tail2", pivot=(0, 2.5, 1))
    m.box("tail3", (-1, 0, 0), (2, 3, 2), paint_tail(100, 45, 0.3))
    m.bone("halo", "root", pivot=(0, 19.5, 0))
    m.box("halo", (-4, 19, -4), (8, 1, 1), paint_halo)
    m.box("halo", (-4, 19, 3), (8, 1, 1), paint_halo)
    m.box("halo", (-4, 19, -3), (1, 1, 6), paint_halo)
    m.box("halo", (3, 19, -3), (1, 1, 6), paint_halo)
    m.bone("orbit", "root", pivot=(0, 11, 0))
    m.box("orbit", (6.5, 11, -0.5), (1, 1, 1), paint_mote)
    m.box("orbit", (-4, 13, 5.5), (1, 1, 1), paint_mote)
    m.box("orbit", (-4, 9, -6.5), (1, 1, 1), paint_mote)
    c, g = m.build(2, 2.5, 0.75)
    c.save(os.path.join(A, "textures/entity/guide_spirit.png"))
    g.save(os.path.join(A, "textures/entity/guide_spirit_glowmask.png"))

    # ---------------- animacoes ----------------
    def r3(v):
        return [round(x, 3) for x in v]

    def sampled(length, steps, fn):
        return {str(round(length * i / steps, 4)): r3(fn(length * i / steps)) for i in range(steps + 1)}

    L = 4.0

    def S(t, k=1.0, ph=0.0):
        return math.sin(TAU * k * t / L + ph)

    idle = {"loop": True, "animation_length": L, "bones": {
        "root": {"position": sampled(L, 16, lambda t: (0, 1.2 * S(t), 0))},
        "body": {"rotation": sampled(L, 16, lambda t: (2 * S(t, 1, 1.0), 0, 3 * S(t, 1, 0.3))),
                 "scale": sampled(L, 16, lambda t: (1 + 0.025 * S(t, 2), 1 + 0.035 * S(t, 2, 1.2), 1 + 0.025 * S(t, 2)))},
        "core": {"scale": sampled(L, 32, lambda t: (1.05 + 0.12 * S(t, 3),) * 3)},
        "crest": {"scale": sampled(L, 40, lambda t: (1, 1.08 + 0.1 * S(t, 4) + 0.04 * S(t, 7, 0.5), 1))},
        "flame_l": {"rotation": sampled(L, 16, lambda t: (0, 0, -6 * S(t, 2, 0.6)))},
        "flame_r": {"rotation": sampled(L, 16, lambda t: (0, 0, 6 * S(t, 2, 1.4)))},
        "arm_l": {"rotation": sampled(L, 16, lambda t: (6 * S(t, 1, 0.8), 0, -8 - 7 * S(t, 1)))},
        "arm_r": {"rotation": sampled(L, 16, lambda t: (6 * S(t, 1, 2.4), 0, 8 + 7 * S(t, 1, 0.4)))},
        "tail1": {"rotation": sampled(L, 16, lambda t: (5 * S(t, 1, 0.0), 0, 4 * S(t, 1, 1.6)))},
        "tail2": {"rotation": sampled(L, 16, lambda t: (9 * S(t, 1, -0.9), 0, 7 * S(t, 1, 0.7)))},
        "tail3": {"rotation": sampled(L, 16, lambda t: (14 * S(t, 1, -1.8), 0, 11 * S(t, 1, -0.2)))},
        "halo": {"rotation": sampled(L, 8, lambda t: (0, 360 * t / L, 0)),
                 "position": sampled(L, 16, lambda t: (0, 0.6 * S(t, 1, 0.9), 0))},
        "orbit": {"rotation": sampled(L, 8, lambda t: (0, -360 * t / L, 0)),
                  "position": sampled(L, 16, lambda t: (0, 0.8 * S(t, 2), 0))},
    }}
    appear = {"loop": False, "animation_length": 1.0, "bones": {
        "root": {"scale": {"0.0": [0, 0, 0], "0.55": [1.2, 1.2, 1.2], "0.8": [0.95, 0.95, 0.95], "1.0": [1, 1, 1]},
                 "rotation": {"0.0": [0, -270, 0], "0.8": [0, 10, 0], "1.0": [0, 0, 0]},
                 "position": {"0.0": [0, -6, 0], "0.6": [0, 1, 0], "1.0": [0, 0, 0]}},
        "halo": {"scale": {"0.0": [0, 0, 0], "0.6": [0, 0, 0], "0.85": [1.3, 1.3, 1.3], "1.0": [1, 1, 1]}},
        "core": {"scale": {"0.0": [2, 2, 2], "0.5": [1.6, 1.6, 1.6], "1.0": [1, 1, 1]}},
    }}
    vanish = {"loop": "hold_on_last_frame", "animation_length": 1.5, "bones": {
        "root": {"scale": {"0.0": [1, 1, 1], "0.3": [1.15, 1.15, 1.15], "1.5": [0, 0, 0]},
                 "rotation": {"0.0": [0, 0, 0], "1.5": [0, 540, 0]},
                 "position": {"0.0": [0, 0, 0], "1.5": [0, 12, 0]}},
        "halo": {"scale": {"0.0": [1, 1, 1], "0.4": [1.6, 1.6, 1.6], "0.9": [0, 0, 0]}},
        "core": {"scale": {"0.0": [1, 1, 1], "0.5": [1.8, 1.8, 1.8], "1.5": [0.2, 0.2, 0.2]}},
    }}
    save_json(os.path.join(A, "animations/entity/guide_spirit.animation.json"), {
        "format_version": "1.8.0",
        "animations": {"animation.guide_spirit.idle": idle, "animation.guide_spirit.appear": appear,
                       "animation.guide_spirit.vanish": vanish}})

    save_json(os.path.join(A, "models/item/guide_spirit_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


# =====================================================================================
#  CAPITULOS (v0.9: rota-guia de TODOS os chefes e portais — 12 capitulos neste arquivo + "kingdoms", da guilda)
# =====================================================================================
def step(sid, cond, optional=False, xp=0, icon=None, locate=None, rewards=None, requires=None, hint_by=None,
         reward_unless=None):
    s = {"id": sid, "condition": cond}
    if optional:
        s["optional"] = True
    if xp:
        s["xp"] = xp
    if icon:
        s["icon"] = icon
    if locate:
        s["locate"] = locate
    if rewards:
        s["rewards"] = rewards
    if reward_unless:
        s["reward_unless"] = reward_unless
    if requires:
        s["requires"] = requires
    if hint_by:
        s["hint_by"] = hint_by
    return s


def items(*pairs):
    return [{"id": i if ":" in i else "isekai_mahou:" + i, "count": n} for i, n in pairs]


def bosses(*ids, n=1):
    return {"type": "bosses", "ids": list(ids), "min": n}


GUARDIANS = ["ignis", "tide_guardian", "primordial_golem", "zephyr"]
GUARDIAN_TEMPLES = {"fire": "ignis", "water": "tide_guardian", "earth": "primordial_golem", "wind": "zephyr"}
TITANS = ["swamp_hydra", "desert_basilisk", "ruin_colossus", "wyvern_king"]
HORRORS = ["false_hydra", "manticore", "mimic_king"]
CLASS_BOSSES = ["corrupted_ent", "renegade_archmage", "corrupted_witch", "lich", "imprisoned_patron"]
SHRINES = ["fire", "water", "earth", "wind", "light", "shadow"]
SLIMES = ["slime", "fire_slime", "water_slime", "stone_slime", "wind_slime", "poison_jelly",
          "minecraft:slime", "minecraft:magma_cube"]
TRIALS = ["isekai_mahou:trial_altar"]
GUILD = ["isekai_mahou:adventurer_card"]

# Para onde a seta do rastreador aponta (o servidor procura a estrutura — ou o bioma — mais próximo)
OVERWORLD = "minecraft:overworld"
LOC_CITY = {"tag": "isekai_mahou:city", "dimension": OVERWORLD}
LOC_GUILD = {"dimension": OVERWORLD, "any": [{"tag": "isekai_mahou:city"},
                                             {"structure": "isekai_mahou:guild_hall_village"}]}
# Altares de Ascensão PRONTOS: igreja/academia das cidades e o Santuário de Ascensão (world_v09)
LOC_ALTAR = {"dimension": OVERWORLD, "any": [{"tag": "isekai_mahou:city"},
                                             {"structure": "isekai_mahou:ascension_sanctum"}]}
LOC_SHRINE_NEW = {"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:shrine_" + e,
                           "unless": {"type": "counter", "key": "shrine." + e}} for e in SHRINES]}
LOC_SHRINE_ELEMENT = {"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:shrine_" + e,
                               "unless": {"type": "element", "element": e}} for e in SHRINES]}
LOC_GUARDIANS = {"dimension": "isekai_mahou:elemental_plane",
                 "any": [{"structure": "isekai_mahou:guardian_temple_" + e, "unless": {"type": "bosses", "ids": [b]}}
                         for e, b in GUARDIAN_TEMPLES.items()]}
LOC_THRONE = {"structure": "isekai_mahou:abyss_throne", "dimension": "isekai_mahou:abyss"}
LOC_CATHEDRAL = {"structure": "isekai_mahou:celestial_cathedral", "dimension": "isekai_mahou:celestial_sanctuary"}
LOC_ARENA = {"structure": "isekai_mahou:twilight_arena", "dimension": "isekai_mahou:twilight_rift"}
# v1.0: o Palácio da Noite Eterna é um lugar do Places no Mundo Oco (montado na 1ª visita)
LOC_PALACE = {"place": "hollow_palace", "dimension": "isekai_mahou:hollow_world"}
# O Rei Slime não tem covil: nasce à noite nos pântanos (a seta aponta o pântano mais próximo)
LOC_KING_SLIME = {"dimension": OVERWORLD, "any": [{"biome": "#c:is_swamp"}]}


def lair(structure, *boss_ids):
    """Covil de um chefe no Mundo Normal (sai da busca quando o chefe já foi derrotado)."""
    return {"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:" + structure,
                                             "unless": {"type": "bosses", "ids": list(boss_ids)}}]}


def lairs(pairs):
    return {"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:" + s, "unless": {"type": "bosses", "ids": [b]}}
                                            for s, b in pairs]}


def portal_site(structure):
    """Estrutura de portal no Mundo Normal (portal adormecido pronto)."""
    return {"structure": "isekai_mahou:" + structure, "dimension": OVERWORLD}


LOC_TITANS = lairs([("hydra_lair", "swamp_hydra"), ("basilisk_tomb", "desert_basilisk"),
                    ("colossus_ruins", "ruin_colossus"), ("wyvern_peak", "wyvern_king")])


# v1.0: teto de nível de cada Círculo (C1..C8 = 10..80, C9 = 100 — o Círculo 10 chega no nível 100)
LEVEL_CAP = {1: 10, 2: 20, 3: 30, 4: 40, 5: 50, 6: 60, 7: 70, 8: 80, 9: 100}


def ascension(n, objective, optional=False):
    """'A Ascensão → Círculo N': no limite do Círculo anterior + um feito da rota, o Diário entrega o Tributo do
    Círculo N pronto (item do módulo ascension). Quem já está no Círculo N não ganha o tributo à toa (reward_unless)."""
    # Quem chegou ao Círculo N por outro caminho (altar próprio, mundo antigo) cumpre o passo na hora — sem tributo.
    cond = {"type": "any", "conditions": [{"type": "circle", "min": n},
                                          {"type": "all", "conditions": [{"type": "level", "min": LEVEL_CAP[n - 1]}, objective]}]}
    return step("ascension_" + str(n), cond,
                optional=optional, xp=40 * n, icon="isekai_mahou:ascension_tribute_" + str(n),
                rewards={"items": items(("ascension_tribute_" + str(n), 1))},
                reward_unless={"type": "circle", "min": n}, requires=["isekai_mahou:ascension_tribute_" + str(n)])


def circle(sid, n, xp, optional=False):
    return step(sid, {"type": "circle", "min": n}, optional=optional, xp=xp, icon="isekai_mahou:ascension_core",
                locate=LOC_ALTAR)


CHAPTERS = [
    {"id": "awakening", "order": 10, "color": "#B98CFF", "intro": True,
     "icon": ["isekai_mahou:essence_arcane", "isekai_mahou:spell_scroll"],
     "steps": [
         step("choose_path", {"type": "path"}, icon="isekai_mahou:essence_arcane"),
         step("open_grimoire", {"type": "any", "conditions": [
             {"type": "flag", "flag": "journal.tutorial.grimoire"},
             {"type": "flag", "flag": "journal.tutorial.spell_built"}]}, xp=10, icon="minecraft:book"),
         step("build_spell", {"type": "flag", "flag": "journal.tutorial.spell_built"}, xp=20,
              icon="isekai_mahou:spell_scroll", hint_by="category"),
         step("first_spell", {"type": "counter_prefix", "prefix": "cast.", "min": 1}, xp=10,
              icon="isekai_mahou:glyph_scroll", hint_by="category"),
         step("awaken_element", {"type": "elements", "set": "any", "min": 1}, xp=15,
              icon="isekai_mahou:essence_fire"),
         step("slime_hunt", {"type": "kills", "ids": SLIMES, "min": 5}, xp=30, icon="isekai_mahou:slime_gel",
              rewards={"items": items(("mana_crystal", 2))}),
         # Ritual da classe: tese/caldeirão/pacto/muda/ossuário (mágicas) ou Provas de Classe (guerreiras/à distância)
         step("first_ritual", {"type": "counter", "key": "ritual_rank", "min": 1}, optional=True, xp=60,
              icon="minecraft:writable_book", hint_by="path"),
     ],
     "rewards": {"xp": 120, "items": items(("mana_crystal", 4), ("essence_arcane", 3)),
                 "glyph_scrolls": 1, "glyph_tier": 1}},

    {"id": "guild_and_trials", "order": 15, "color": "#6FD3A0", "intro": True,
     "icon": ["isekai_mahou:adventurer_card", "isekai_mahou:guild_coin"],
     "steps": [
         step("element_level", {"type": "element_level", "min": 3}, xp=30, icon="minecraft:experience_bottle"),
         step("level_5", {"type": "level", "min": 5}, xp=30, icon="minecraft:experience_bottle"),
         step("reach_city", {"type": "any", "conditions": [{"type": "counter", "key": "visit.city", "min": 1},
                                                           {"type": "counter", "key": "visit.capital", "min": 1}]},
              xp=50, icon="minecraft:bell", locate=LOC_CITY, requires=["isekai_mahou:city_heart"]),
         step("discover_waystone", {"type": "any", "conditions": [
             {"type": "counter", "key": "visit.waystone", "min": 1},
             {"type": "counter", "key": "travel.count", "min": 1},
             {"type": "flag_prefix", "prefix": "travel.", "exclude": ["travel.hub_"], "min": 1}]},
              xp=50, icon="isekai_mahou:travel_stone", locate=LOC_CITY, requires=["isekai_mahou:travel_stone"]),
         step("join_guild", {"type": "counter", "key": "guild_rank", "min": 1}, xp=40, icon="isekai_mahou:adventurer_card",
              locate=LOC_GUILD, rewards={"items": items(("guild_coin", 10))}, requires=GUILD),
         step("trial_one", {"type": "trial", "rank": 1}, xp=80, icon="minecraft:iron_sword",
              rewards={"items": items(("mana_crystal", 3))}, requires=TRIALS),
         step("guild_quest", {"type": "counter", "key": "guild_quests", "min": 1}, xp=40,
              icon="isekai_mahou:quest_board", requires=GUILD),
         step("king_slime", bosses("king_slime"), optional=True, xp=150, icon="isekai_mahou:king_slime_crown",
              locate=LOC_KING_SLIME, rewards={"items": items(("minecraft:golden_apple", 1))},
              requires=["isekai_mahou:king_slime"]),
         step("level_10", {"type": "level", "min": 10}, xp=40, icon="minecraft:experience_bottle"),
         # v1.0: Limite de Mana treinável — esvaziar 100% do recurso conta um treino (até 3 por dia de jogo)
         step("train_resource", {"type": "counter", "key": "resource_training", "min": 1}, optional=True, xp=40,
              icon="isekai_mahou:mana_crystal"),
         ascension(2, {"type": "any", "conditions": [{"type": "trial", "rank": 1}, bosses("king_slime")]}),
     ],
     "rewards": {"xp": 300, "items": items(("mana_crystal", 6), ("guild_coin", 15)), "element_essences": 2,
                 "glyph_scrolls": 2, "glyph_tier": 2}},

    {"id": "six_shrines", "order": 20, "color": "#FFB347", "intro": True,
     "icon": ["isekai_mahou:essence_fire", "isekai_mahou:essence_water", "isekai_mahou:essence_earth",
              "isekai_mahou:essence_wind", "isekai_mahou:essence_light", "isekai_mahou:essence_shadow"],
     "steps": [
         circle("second_circle", 2, 60),
         step("shrine_trial", {"type": "counter_prefix", "prefix": "shrine.", "min": 1, "distinct": True}, xp=80,
              icon="minecraft:lodestone", locate=LOC_SHRINE_NEW),
         step("second_element", {"type": "elements", "set": "any", "min": 2}, xp=60, icon="isekai_mahou:essence_water",
              locate=LOC_SHRINE_ELEMENT),
         step("goblin_king", bosses("goblin_king"), xp=200, icon="isekai_mahou:goblin_king_crown",
              locate=lair("goblin_fortress", "goblin_king"), rewards={"items": items(("minecraft:golden_apple", 2))},
              requires=["isekai_mahou:goblin_king"]),
         step("multi_spell", {"type": "counter", "key": "spell.multi_element", "min": 1}, xp=50,
              icon="isekai_mahou:spell_scroll"),
         step("trial_two", {"type": "trial", "rank": 2}, xp=100, icon="minecraft:iron_sword", requires=TRIALS),
         ascension(3, {"type": "any", "conditions": [bosses("goblin_king"), {"type": "trial", "rank": 2}]}),
         circle("third_circle", 3, 80),
     ],
     "rewards": {"xp": 550, "items": items(("mana_crystal", 8), ("guild_coin", 20), ("minecraft:golden_apple", 2),
                                            ("arcane_upgrade_template", 1)),
                 "element_essences": 3, "glyph_scrolls": 3, "glyph_tier": 2}},

    # ("kingdoms", ordem 22: data/isekai_mahou/journal/kingdoms.json, gerado pelo tools/gen/guild.py)

    {"id": "monster_lords", "order": 23, "color": "#D2783C", "intro": True,
     "icon": ["isekai_mahou:orc_lord_cleaver", "isekai_mahou:minotaur_labrys"],
     "steps": [
         step("orc_lord", bosses("orc_lord"), xp=250, icon="isekai_mahou:orc_lord_cleaver",
              locate=lair("orc_camp", "orc_lord"), rewards={"items": items(("minecraft:golden_apple", 2))},
              requires=["isekai_mahou:orc_lord"]),
         step("minotaur", bosses("minotaur"), xp=300, icon="isekai_mahou:minotaur_labrys",
              locate=lair("minotaur_labyrinth", "minotaur"), rewards={"items": items(("minecraft:golden_apple", 2))},
              requires=["isekai_mahou:minotaur"]),
         step("corrupted_ent", bosses("corrupted_ent"), optional=True, xp=300, icon="isekai_mahou:ancestral_sapling",
              locate=lair("druid_circle", "corrupted_ent"), rewards={"items": items(("mana_crystal", 4))},
              requires=["isekai_mahou:corrupted_ent"]),
         ascension(4, bosses("orc_lord", "minotaur", "corrupted_ent")),
         circle("fourth_circle", 4, 120),
     ],
     "rewards": {"xp": 700, "items": items(("mana_crystal", 8), ("guild_coin", 20), ("minecraft:golden_apple", 2)),
                 "element_essences": 2, "glyph_scrolls": 2, "glyph_tier": 3}},

    {"id": "programmed_magic", "order": 25, "color": "#5FC8FF", "intro": True,
     "icon": ["isekai_mahou:spell_scroll", "isekai_mahou:glyph_scroll"],
     "steps": [
         step("learn_link", {"type": "glyph", "ids": ["link_hit", "link_end", "link_kill"], "min": 1}, xp=30,
              icon="isekai_mahou:glyph_scroll"),
         # Saltar já vem no nível 10 (Caminho Arcano); o prêmio aqui é a Bateria de Mana que o passo do Pedestal usa
         # (sem o módulo automation o item é pulado na entrega e na aba).
         step("program_spell", {"type": "counter", "key": "spell.linked", "min": 1}, xp=80,
              icon="isekai_mahou:spell_scroll",
              rewards={"items": items(("mana_battery", 1), ("minecraft:paper", 4))}),
         step("leap_spell", {"type": "counter", "key": "spell.leap", "min": 1}, xp=100,
              icon="minecraft:chain", requires=["link_leap"]),
         step("renegade_archmage", bosses("renegade_archmage"), xp=350, icon="isekai_mahou:research_note",
              locate=lair("mage_tower", "renegade_archmage"), rewards={"items": items(("mana_crystal", 5))},
              requires=["isekai_mahou:renegade_archmage"]),
         step("trial_three", {"type": "trial", "rank": 3}, xp=140, icon="minecraft:diamond_sword", requires=TRIALS),
         step("corrupted_witch", bosses("corrupted_witch"), optional=True, xp=300, icon="isekai_mahou:witch_recipe_page",
              locate=lair("witch_hut_ancient", "corrupted_witch"), rewards={"items": items(("mana_crystal", 4))},
              requires=["isekai_mahou:corrupted_witch"]),
         # Contador do módulo automation: +1 a cada conjuração de um pedestal do jogador. Opcional porque o servidor
         # pode desligar os pedestais (limite por chunk 0) e isso não pode travar a história.
         step("pedestal", {"type": "counter", "key": "pedestal.cast", "min": 1}, optional=True, xp=150,
              icon="isekai_mahou:runic_pedestal", rewards={"items": items(("mana_crystal", 4))},
              requires=["isekai_mahou:runic_pedestal"]),
     ],
     "rewards": {"xp": 900, "items": items(("mana_crystal", 10), ("guild_coin", 30), ("arcane_upgrade_template", 1)),
                 "element_essences": 3, "glyph_scrolls": 3, "glyph_tier": 3}},

    {"id": "dark_masters", "order": 27, "color": "#8E6BD8", "intro": True,
     "icon": ["isekai_mahou:phylactery", "isekai_mahou:ritual_chalk_item"],
     "steps": [
         step("lich", bosses("lich"), xp=450, icon="isekai_mahou:phylactery",
              locate=lair("forgotten_crypt", "lich"), rewards={"items": items(("minecraft:golden_apple", 2))},
              requires=["isekai_mahou:lich"]),
         step("imprisoned_patron", bosses("imprisoned_patron"), xp=450, icon="isekai_mahou:ritual_chalk_item",
              locate=lair("profane_altar", "imprisoned_patron"), rewards={"items": items(("minecraft:golden_apple", 2))},
              requires=["isekai_mahou:imprisoned_patron"]),
         step("class_rank_3", {"type": "counter", "key": "ritual_rank", "min": 3}, optional=True, xp=250,
              icon="minecraft:writable_book"),
         # v1.0: glifos de origem só passam para outras classes por Lição (o professor ganha XP)
         step("write_lesson", {"type": "counter", "key": "lesson.written", "min": 1}, optional=True, xp=150,
              icon="isekai_mahou:technique_lesson", requires=["isekai_mahou:technique_lesson"]),
         ascension(5, bosses(*CLASS_BOSSES)),
         circle("fifth_circle", 5, 150),
     ],
     "rewards": {"xp": 1100, "items": items(("mana_crystal", 12), ("guild_coin", 30), ("arcane_upgrade_template", 1)),
                 "element_essences": 3, "glyph_scrolls": 2, "glyph_tier": 3, "legend_scrolls": 1}},

    {"id": "wild_titans", "order": 28, "color": "#5DB86A", "intro": True,
     "icon": ["isekai_mahou:swamp_hydra_spawn_egg", "isekai_mahou:desert_basilisk_spawn_egg",
              "isekai_mahou:ruin_colossus_spawn_egg", "isekai_mahou:wyvern_king_spawn_egg", "minecraft:dragon_head"],
     "steps": [
         step("two_titans", bosses(*TITANS, n=2), xp=400, icon="minecraft:dragon_head", locate=LOC_TITANS,
              rewards={"items": items(("minecraft:golden_apple", 3))}, requires=["isekai_mahou:" + t for t in TITANS]),
         step("swamp_hydra", bosses("swamp_hydra"), optional=True, xp=350, icon="isekai_mahou:swamp_hydra_spawn_egg",
              locate=lair("hydra_lair", "swamp_hydra"), requires=["isekai_mahou:swamp_hydra"]),
         step("desert_basilisk", bosses("desert_basilisk"), optional=True, xp=350,
              icon="isekai_mahou:desert_basilisk_spawn_egg", locate=lair("basilisk_tomb", "desert_basilisk"),
              requires=["isekai_mahou:desert_basilisk"]),
         step("ruin_colossus", bosses("ruin_colossus"), optional=True, xp=350, icon="isekai_mahou:ruin_colossus_spawn_egg",
              locate=lair("colossus_ruins", "ruin_colossus"), requires=["isekai_mahou:ruin_colossus"]),
         step("wyvern_king", bosses("wyvern_king"), optional=True, xp=350, icon="isekai_mahou:wyvern_king_spawn_egg",
              locate=lair("wyvern_peak", "wyvern_king"), requires=["isekai_mahou:wyvern_king"]),
         step("trial_four", {"type": "trial", "rank": 4}, xp=180, icon="minecraft:diamond_sword", requires=TRIALS),
     ],
     "rewards": {"xp": 1400, "items": items(("mana_crystal", 12), ("minecraft:golden_apple", 3),
                                             ("arcane_upgrade_template", 1)),
                 "element_essences": 3, "glyph_scrolls": 2, "glyph_tier": 4}},

    {"id": "hidden_horrors", "order": 29, "color": "#B04A6E", "intro": True,
     "icon": ["isekai_mahou:false_hydra_spawn_egg", "isekai_mahou:manticore_spawn_egg",
              "isekai_mahou:mimic_king_spawn_egg", "isekai_mahou:mimic_tooth"],
     "steps": [
         step("false_hydra", bosses("false_hydra"), xp=500, icon="isekai_mahou:false_hydra_spawn_egg",
              locate=lair("false_hydra_lair", "false_hydra"), rewards={"items": items(("minecraft:golden_apple", 2))},
              requires=["isekai_mahou:false_hydra"]),
         step("manticore", bosses("manticore"), xp=450, icon="isekai_mahou:manticore_spawn_egg",
              locate=lair("manticore_den", "manticore"), rewards={"items": items(("minecraft:golden_apple", 2))},
              requires=["isekai_mahou:manticore"]),
         step("mimic_king", bosses("mimic_king"), optional=True, xp=450, icon="isekai_mahou:mimic_king_spawn_egg",
              locate=lair("mimic_vault", "mimic_king"), rewards={"items": items(("minecraft:gold_block", 2))},
              requires=["isekai_mahou:mimic_king"]),
         step("element_level_12", {"type": "element_level", "min": 12}, xp=200, icon="minecraft:experience_bottle"),
     ],
     "rewards": {"xp": 1600, "items": items(("mana_crystal", 14), ("guild_coin", 40),
                                             ("minecraft:enchanted_golden_apple", 1)),
                 "element_essences": 3, "glyph_scrolls": 2, "glyph_tier": 4, "legend_scrolls": 1}},

    {"id": "elemental_plane", "order": 30, "color": "#7FD4FF", "intro": True,
     "icon": ["isekai_mahou:guardian_core_fire", "isekai_mahou:guardian_core_water",
              "isekai_mahou:guardian_core_earth", "isekai_mahou:guardian_core_wind"],
     "steps": [
         step("enter_plane", {"type": "visit", "dimension": "isekai_mahou:elemental_plane"}, xp=150,
              icon="isekai_mahou:elemental_portal_frame", locate=portal_site("elemental_gate")),
         step("first_guardian", bosses(*GUARDIANS), xp=300,
              icon="isekai_mahou:guardian_core_fire", locate=LOC_GUARDIANS,
              rewards={"items": items(("minecraft:golden_apple", 2))}),
         step("two_masters", {"type": "node", "node": "master", "min": 2}, xp=150, icon="minecraft:enchanted_book"),
         step("first_fusion", {"type": "elements", "set": "fusion", "min": 1}, xp=200,
              icon="isekai_mahou:convergence_altar"),
         ascension(6, {"type": "any", "conditions": [bosses(*GUARDIANS), {"type": "trial", "rank": 5}]}),
         circle("sixth_circle", 6, 180),
         step("all_guardians", bosses(*GUARDIANS, n=4), optional=True, xp=600,
              icon="isekai_mahou:guardian_core_wind", locate=LOC_GUARDIANS,
              rewards={"items": items(("minecraft:golden_apple", 3))}),
     ],
     "rewards": {"xp": 1800, "items": items(("mana_crystal", 14), ("minecraft:golden_apple", 3),
                                             ("arcane_upgrade_template", 1)),
                 "element_essences": 4, "glyph_scrolls": 2, "glyph_tier": 4, "legend_scrolls": 1}},

    {"id": "light_and_shadow", "order": 40, "color": "#E8C46A", "intro": True,
     "icon": ["isekai_mahou:guardian_core_light", "isekai_mahou:guardian_core_shadow"],
     "steps": [
         step("enter_abyss", {"type": "visit", "dimension": "isekai_mahou:abyss"}, xp=200,
              icon="isekai_mahou:abyss_portal_frame", locate=portal_site("abyssal_chasm")),
         step("abyss_sovereign", bosses("abyss_sovereign"), xp=500,
              icon="isekai_mahou:guardian_core_shadow", locate=LOC_THRONE),
         step("enter_sanctuary", {"type": "visit", "dimension": "isekai_mahou:celestial_sanctuary"}, xp=200,
              icon="isekai_mahou:celestial_beacon", locate=portal_site("celestial_stairway")),
         step("seraph", bosses("seraph"), xp=500, icon="isekai_mahou:guardian_core_light", locate=LOC_CATHEDRAL),
         step("trial_five", {"type": "trial", "rank": 5}, xp=250, icon="minecraft:netherite_sword", requires=TRIALS),
         ascension(7, bosses("abyss_sovereign", "seraph")),
         circle("seventh_circle", 7, 250),
         # Opcional: cada lenda é de um elemento e pede glifos de nós altos — nem todo jogador consegue conjurar já.
         # O Diário entrega um Pergaminho Lendário que o jogador consiga usar (JournalService.legendScroll).
         step("cast_legend", {"type": "counter", "key": "spell.legend", "min": 1}, optional=True, xp=250,
              icon="isekai_mahou:spell_scroll", requires=["legends"]),
         step("domain", {"type": "node", "node": "domain", "min": 1}, optional=True, xp=600, icon="minecraft:end_crystal"),
     ],
     "rewards": {"xp": 2400, "items": items(("mana_crystal", 16), ("minecraft:enchanted_golden_apple", 1),
                                             ("minecraft:totem_of_undying", 1), ("arcane_upgrade_template", 1)),
                 "glyph_scrolls": 2, "glyph_tier": 4, "legend_scrolls": 1}},

    {"id": "twilight", "order": 50, "color": "#C06AA0", "intro": True,
     "icon": ["isekai_mahou:twilight_rift", "minecraft:nether_star"],
     "steps": [
         ascension(8, bosses("abyss_sovereign", "seraph", n=2)),
         circle("eighth_circle", 8, 300),
         ascension(9, {"type": "any", "conditions": [{"type": "trial", "rank": 6}, bosses(*GUARDIANS, n=4),
                                                     bosses(*HORRORS, n=3)]}),
         circle("ninth_circle", 9, 400),
         step("enter_rift", {"type": "visit", "dimension": "isekai_mahou:twilight_rift"}, xp=300,
              icon="isekai_mahou:twilight_rift", locate=portal_site("twilight_rupture")),
         step("herald", bosses("twilight_herald"), xp=1000, icon="minecraft:nether_star", locate=LOC_ARENA),
         step("archmage", {"type": "node", "node": "archmage", "min": 1}, optional=True, xp=1000,
              icon="minecraft:dragon_breath"),
     ],
     "rewards": {"xp": 3000, "items": items(("mana_crystal", 20), ("minecraft:enchanted_golden_apple", 1)),
                 "glyph_scrolls": 1, "glyph_tier": 5, "legend_scrolls": 1}},

    # v1.0: Mundo Oco (hueco_mundo) — os chefes do palácio e o Reino das Fadas antes do Dragão
    {"id": "hollow_world", "order": 55, "color": "#B8C8E8", "intro": True,
     "icon": ["isekai_mahou:hollow_mask_fragment", "isekai_mahou:garganta_key", "isekai_mahou:menos_core"],
     "steps": [
         step("garganta", {"type": "visit", "dimension": "isekai_mahou:hollow_world"}, xp=400,
              icon="isekai_mahou:garganta_key", requires=["isekai_mahou:garganta_altar"],
              locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:garganta_site"}]}),
         step("hollow_hunt", {"type": "kills", "ids": ["hollow_crawler", "hollow_brute", "hollow_wraith"], "min": 20}, xp=300,
              icon="isekai_mahou:hollow_mask_fragment", rewards={"items": items(("mana_crystal", 6))}),
         step("menos", {"type": "kills", "ids": ["menos_giant"], "min": 1}, optional=True, xp=400,
              icon="isekai_mahou:menos_core"),
         step("arrancar", {"type": "kills", "ids": ["arrancar"], "min": 3}, optional=True, xp=400,
              icon="isekai_mahou:arrancar_soul_shard"),
         step("sand_empress", bosses("sand_empress"), xp=1200, icon="isekai_mahou:tide_sand_blade",
              locate=LOC_PALACE, requires=["isekai_mahou:sand_empress"],
              rewards={"items": items(("minecraft:golden_apple", 3))}),
         step("hollow_king", bosses("hollow_king"), xp=1500, icon="isekai_mahou:decay_axe",
              locate=LOC_PALACE, requires=["isekai_mahou:hollow_king"],
              rewards={"items": items(("minecraft:enchanted_golden_apple", 1))}),
         step("fey_realm", {"type": "visit", "dimension": "isekai_mahou:fey_realm"}, optional=True, xp=300,
              icon="isekai_mahou:fey_dust", requires=["isekai_mahou:fey_ring_stone"],
              locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:fey_ring"}]}),
         step("thorn_queen", bosses("thorn_queen"), optional=True, xp=1200, icon="isekai_mahou:queen_wings",
              requires=["isekai_mahou:thorn_queen"],
              locate={"place": "fey_court", "dimension": "isekai_mahou:fey_realm"}),
     ],
     "rewards": {"xp": 3500, "items": items(("mana_crystal", 20), ("minecraft:enchanted_golden_apple", 1)),
                 "glyph_scrolls": 1, "glyph_tier": 5, "legend_scrolls": 1}},

    {"id": "primordial", "order": 60, "color": "#FF8A3D", "intro": True, "finale": True, "epilogue": 7,
     "icon": ["isekai_mahou:primordial_dragon_heart", "minecraft:dragon_egg", "minecraft:nether_star"],
     "steps": [
         step("primordial_dragon", bosses("primordial_dragon"), xp=3000, icon="isekai_mahou:primordial_dragon_heart",
              locate=LOC_ARENA, requires=["isekai_mahou:primordial_dragon"]),
         ascension(10, bosses("twilight_herald", "primordial_dragon"), optional=True),
         circle("tenth_circle", 10, 1500, optional=True),
     ],
     "rewards": {"xp": 6000, "items": items(("mana_crystal", 32), ("minecraft:nether_star", 1),
                                             ("minecraft:enchanted_golden_apple", 2)),
                 "glyph_scrolls": 1, "glyph_tier": 5, "legend_scrolls": 2}},

    # v1.0: passo SECRETO — escondido no Diário até o Dragão cair (o limite de nível vai de 150 a 200)
    {"id": "beyond_limit", "order": 70, "color": "#FFE27A", "intro": True, "secret": True,
     "icon": ["minecraft:nether_star", "isekai_mahou:primordial_dragon_heart", "minecraft:dragon_breath"],
     "steps": [
         step("level_160", {"type": "level", "min": 160}, xp=2000, icon="minecraft:experience_bottle"),
         step("dragon_again", {"type": "counter", "key": "boss.primordial_dragon", "min": 2}, optional=True, xp=3000,
              icon="isekai_mahou:primordial_dragon_heart", locate=LOC_ARENA),
         step("class_master", {"type": "counter", "key": "ritual_rank", "min": 5}, optional=True, xp=2000,
              icon="minecraft:writable_book"),
         step("level_180", {"type": "level", "min": 180}, xp=3000, icon="minecraft:experience_bottle"),
         step("hollow_again", {"type": "counter", "key": "boss.hollow_king", "min": 2}, optional=True, xp=2500,
              icon="isekai_mahou:decay_axe", locate=LOC_PALACE, requires=["isekai_mahou:hollow_king"]),
         step("level_200", {"type": "level", "min": 200}, xp=5000, icon="minecraft:nether_star"),
     ],
     "rewards": {"xp": 10000, "items": items(("minecraft:nether_star", 2), ("minecraft:enchanted_golden_apple", 3),
                                              ("mana_crystal", 64)),
                 "legend_scrolls": 2}},
]

# Textos de cada capitulo: (pt, en) para title, lore, outro; passos: (titulo, lore, dica, dica curta do rastreador)
CH_TEXT = {
    "awakening": {
        "title": ("Despertar", "Awakening"),
        "lore": (
            "Você abre os olhos sob um céu que não conhece. Duas luas pálidas se apagam no amanhecer e o ar vibra com algo "
            "que o seu antigo mundo nunca teve: mana. Uma voz suave sussurra que você foi invocado por um motivo — mas, "
            "antes de salvar qualquer coisa, precisa sobreviver. Escolha sua origem, aprenda a escrever magias com glifos no "
            "Grimório e prove a sua força contra as criaturas mais simples deste mundo.",
            "You open your eyes beneath a sky you do not know. Two pale moons fade into the dawn and the air hums with "
            "something your old world never had: mana. A gentle voice whispers that you were summoned for a reason — but "
            "before saving anything, you must survive. Choose your origin, learn to write spells with glyphs in the Grimoire "
            "and prove your strength against this world's simplest creatures."),
        "outro": (
            "A mana agora corre por suas veias como um segundo sangue. Nas tavernas, falam de uma Guilda de Aventureiros que "
            "paga bem por trabalho honesto — e de Câmaras de Provação onde os elementos testam quem ousa reivindicá-los. "
            "O Espírito Guia sorri: \"O verdadeiro caminho começa agora.\"",
            "Mana now runs through your veins like a second blood. In the taverns, people speak of an Adventurers' Guild that "
            "pays well for honest work — and of Trial Chambers where the elements test those who dare to claim them. "
            "The Guide Spirit smiles: \"The true path begins now.\""),
        "steps": {
            "choose_path": (
                ("Escolha sua origem", "Choose your origin"),
                ("Mago, Bruxa, Bruxo, Druida ou Necromante. Neste mundo a origem não te deixa mais forte — ela decide o ritual com "
                 "que você cresce e os elementos que entendem a sua alma.",
                 "Mage, Witch, Warlock, Druid or Necromancer. In this world your origin does not make you stronger — it decides "
                 "the ritual you grow with and the elements that understand your soul."),
                ("A escolha de origem aparece quando você entra no mundo. Se a fechou sem escolher, saia e entre de novo para vê-la.",
                 "The origin choice appears when you join the world. If you closed it without choosing, leave and rejoin to see it again."),
                ("Aparece ao entrar no mundo", "Shown when you join")),
            "open_grimoire": (
                ("Abra o Grimório (B)", "Open the Grimoire (B)"),
                ("A Janela de Status mostra tudo o que você é: nível, Círculo, caminhos, magias. O Grimório é o coração dela — "
                 "o caderno onde cada magia é escrita, glifo por glifo.",
                 "The Status Window shows everything you are: level, Circle, paths, spells. The Grimoire is its heart — the "
                 "notebook where every spell is written, glyph by glyph."),
                ("Aperte B para abrir a Janela de Status e clique na aba Grimório. As abas também trocam com a roda do mouse "
                 "sobre a barra de abas.",
                 "Press B to open the Status Window and click the Grimoire tab. You can also switch tabs with the mouse wheel "
                 "over the tab bar."),
                ("B → aba Grimório", "B → Grimoire tab")),
            "build_spell": (
                ("Monte a sua 1ª magia", "Build your first spell"),
                ("Aqui ninguém nasce sabendo feitiços prontos: magias são escritas com glifos. Uma Forma diz como a magia sai; "
                 "os Efeitos dizem o que ela faz; os Aumentos mudam o jeito. O resto é criatividade.",
                 "Nobody here is born knowing ready-made spells: magic is written with glyphs. A Form says how the spell leaves "
                 "you; Effects say what it does; Augments change how. The rest is creativity."),
                ("No Grimório, junte uma Forma (como Projétil ou Toque) e um Efeito (como Dano Arcano ou Empurrar), dê um nome e "
                 "salve num slot livre (C) ou na Biblioteca. As magias prontas em Z e X servem de exemplo.",
                 "In the Grimoire, combine a Form (like Projectile or Touch) with an Effect (like Arcane Damage or Push), name it "
                 "and save it in a free slot (C) or in the Library. The ready spells in Z and X are good examples."),
                ("Forma + Efeito → salvar", "Form + Effect → save")),
            "first_spell": (
                ("Lance uma magia", "Cast a spell"),
                ("A primeira conjuração é sempre desajeitada: a mana escapa pelos dedos, a luz treme. Mas ela sai. E isso muda tudo.",
                 "The first casting is always clumsy: mana slips through your fingers, the light trembles. But it comes out. And "
                 "that changes everything."),
                ("Segure Z, X ou C para conjurar a magia do slot (algumas magias precisam de um tempinho de conjuração). "
                 "G é o slot da magia suprema.",
                 "Hold Z, X or C to cast the spell in that slot (some spells take a moment to cast). G is the ultimate slot."),
                ("Segure Z, X ou C", "Hold Z, X or C")),
            "awaken_element": (
                ("Desperte um elemento", "Awaken an element"),
                ("Fogo, Água, Terra, Vento, Luz e Sombra dormem no mundo, esperando quem os mereça. O primeiro elemento que "
                 "desperta costuma dizer muito sobre quem você é.",
                 "Fire, Water, Earth, Wind, Light and Shadow sleep within the world, waiting for someone worthy. The first element "
                 "to awaken usually says a lot about who you are."),
                ("O elemento de afinidade da sua classe desperta ao escolhê-la. O elemento de uma magia vem dos glifos dela: "
                 "ponha um efeito de Fogo e ela é de Fogo. Se nada despertou, procure um Santuário Elemental.",
                 "Your class's affinity element awakens when you choose it. A spell's element comes from its glyphs: add a "
                 "Fire effect and it is a Fire spell. If nothing awakened, look for an Elemental Shrine."),
                ("Afinidade da sua origem", "Your origin's affinity")),
            "slime_hunt": (
                ("Derrote 5 slimes", "Defeat 5 slimes"),
                ("Todo herói invocado começa assim: espada de madeira, magia trêmula e uma gelatina azul olhando torto para você.",
                 "Every summoned hero starts like this: a wooden sword, a trembling spell and a blue jelly giving you a funny look."),
                ("Slimes vivem em planícies, florestas e pântanos (os de água perto de rios; os de vento nas montanhas). "
                 "Abates com magia dão mais XP.",
                 "Slimes live in plains, forests and swamps (water slimes near rivers; wind slimes in the mountains). "
                 "Spell kills give more XP."),
                ("Planícies e pântanos", "Plains, forests, swamps")),
            "first_ritual": (
                ("1º ritual da origem", "First origin ritual"),
                ("Cada origem cresce de um jeito: Magos defendem teses, Bruxas cozinham receitas, Bruxos firmam pactos, Druidas "
                 "plantam árvores ancestrais e Necromantes colhem almas.",
                 "Each origin grows its own way: Mages defend theses, Witches brew recipes, Warlocks seal pacts, Druids plant "
                 "ancestral trees and Necromancers harvest souls."),
                ("Cada origem tem o seu ritual (veja o kit inicial). Mago: Mesa de Pesquisa. Bruxa: Caldeirão. Bruxo: giz ritual e "
                 "Altar de Oferendas. Druida: Muda Ancestral. Necromante: Altar Ossuário.",
                 "Each origin has its own ritual (check your starting kit). Mage: Research Table. Witch: Cauldron. Warlock: ritual "
                 "chalk and Offering Altar. Druid: Ancestral Sapling. Necromancer: Ossuary Altar."),
                ("Veja o seu kit inicial", "Check your starting kit")),
        },
    },
    "guild_and_trials": {
        "title": ("Guilda e Provações", "Guild and Trials"),
        "lore": (
            "Para os nativos, um invocado é uma lenda ambulante — ou uma encrenca ambulante. A Guilda dos Aventureiros não liga "
            "para nenhuma das duas: só quer o seu nome no registro e trabalho bem feito. Já as Câmaras de Provação são outra "
            "história. Ninguém sabe quem as construiu, mas todo mago que quer crescer num elemento um dia atravessa as suas "
            "portas, enfrenta ondas de criaturas e um Avatar Elemental... e volta mais forte.",
            "To the natives, a summoned hero is a walking legend — or walking trouble. The Adventurers' Guild cares for neither: "
            "it only wants your name in the register and a job well done. The Trial Chambers are another story. Nobody knows who "
            "built them, but every mage who wants to grow in an element eventually crosses their gates, faces waves of creatures "
            "and an Elemental Avatar... and comes back stronger."),
        "outro": (
            "Com um cartão de aventureiro no bolso e a primeira Provação vencida, você bate no teto do Primeiro Círculo. A mana "
            "pede mais espaço. Nas bibliotecas antigas, fala-se de um Ritual de Ascensão — e de seis santuários que guardam a "
            "essência do mundo.",
            "With an adventurer card in your pocket and your first Trial won, you hit the ceiling of the First Circle. Your mana "
            "asks for more room. Old libraries speak of an Ascension Ritual — and of six shrines that guard the world's essence."),
        "steps": {
            "element_level": (
                ("Elemento no nível 3", "Element at level 3"),
                ("Cada elemento tem o seu próprio caminho. Quanto mais você o usa, mais ele te entende — e mais portas se abrem.",
                 "Each element has its own path. The more you use it, the better it understands you — and the more doors open."),
                ("Conjurar magias de um elemento e vencer inimigos com elas dá XP desse elemento. Monte magias com efeitos do "
                 "elemento (são 9 loadouts: Alt ou Mouse 5 + rolagem troca). Veja o nível na aba Caminhos.",
                 "Casting an element's spells and defeating enemies with them gives that element XP. Build spells with that "
                 "element's effects (9 loadouts: Alt or Mouse 5 + scroll switches). Check the level in the Paths tab."),
                ("Use magias do elemento", "Use the element's spells")),
            "level_5": (
                ("Alcance o nível 5", "Reach level 5"),
                ("Nível 5: a mana já não treme. Os glifos Arcanos mais avançados começam a responder ao seu chamado.",
                 "Level 5: your mana no longer trembles. The more advanced Arcane glyphs begin to answer your call."),
                ("Conjurar e vencer criaturas dá XP. Em grupo, todos a até 24 blocos ganham o XP do abate inteiro.",
                 "Casting and defeating creatures gives XP. In a group, everyone within 24 blocks gets the full kill XP."),
                ("Abates e magias dão XP", "Kills and spells give XP")),
            "join_guild": (
                ("Entre na Guilda", "Join the Guild"),
                ("Todo herói invocado que se preze começa com um cartão de aventureiro de rank F e uma recepcionista sorridente.",
                 "Every self-respecting summoned hero starts with a rank F adventurer card and a smiling receptionist."),
                ("A seta no canto da tela aponta a vila da Guilda dos Aventureiros mais próxima. Fale com a recepcionista no balcão "
                 "para receber o seu Cartão. Chame o Espírito Guia no Diário: ele voa mostrando o caminho.",
                 "The arrow in the corner of the screen points to the nearest Adventurers' Guild village. Talk to the receptionist "
                 "at the counter to get your Card. Call the Guide Spirit from the Journal: it flies ahead showing the way."),
                ("Siga a seta até a vila", "Follow the arrow")),
            "trial_one": (
                ("Vença a Provação I", "Win Trial I"),
                ("A Câmara de Provação engole a luz do dia. Uma recepção silenciosa, um altar, e do outro lado uma arena onde "
                 "o seu elemento vai testar se você é digno dele.",
                 "The Trial Chamber swallows the daylight. A silent lobby, an altar, and beyond it an arena where your element "
                 "will test whether you are worthy of it."),
                ("Abra a aba Provações (B), escolha o elemento e clique em Iniciar Provação (ou /mahou trial start <elemento>). "
                 "Na recepção, convide amigos e toque o Altar. Vença as ondas e o Avatar Elemental. Se cair, ninguém perde itens.",
                 "Open the Trials tab (B), pick the element and click Start Trial (or /mahou trial start <element>). In the lobby, "
                 "invite friends and touch the Altar. Beat the waves and the Elemental Avatar. If you fall, nobody loses items."),
                ("B → aba Provações", "B → Trials tab")),
            "guild_quest": (
                ("Conclua uma missão", "Complete a quest"),
                ("Exterminar ratos gigantes não é exatamente épico. Mas paga, e a Guilda nunca esquece quem cumpre a palavra.",
                 "Exterminating giant rats is not exactly epic. But it pays, and the Guild never forgets who keeps their word."),
                ("No Quadro de Missões (ou com a recepcionista), aceite uma missão, cumpra o pedido e volte à Guilda para "
                 "entregar. Missões rendem moedas, XP e pontos de rank.",
                 "At the Quest Board (or with the receptionist), accept a quest, fulfil it and return to the Guild to turn it in. "
                 "Quests give coins, XP and rank points."),
                ("Quadro de Missões", "Quest Board")),
            "level_10": (
                ("Alcance o nível 10", "Reach level 10"),
                ("Nível 10: o teto do Primeiro Círculo. A alma pressiona as próprias bordas, querendo mais.",
                 "Level 10: the ceiling of the First Circle. Your soul presses against its own edges, wanting more."),
                ("No nível 10 você chega ao limite do Primeiro Círculo. O XP que passar fica guardado e volta depois da Ascensão.",
                 "At level 10 you reach the First Circle's limit. Extra XP is banked and comes back after the Ascension."),
                ("Limite do 1º Círculo", "First Circle's limit")),
        },
    },
    "six_shrines": {
        "title": ("Os Seis Santuários", "The Six Shrines"),
        "lore": (
            "Dizem que, antes da Grande Ruptura, seis sábios selaram a essência do mundo em seis santuários. Hoje, só os dignos "
            "atravessam suas provas. Cada elemento despertado entra nos seus glifos, e cada Círculo rompido torna a sua alma "
            "mais vasta. Mas cuidado: algo nas fronteiras do mundo observa a sua ascensão com interesse demais.",
            "They say that before the Great Rupture, six sages sealed the essence of the world in six shrines. Today only the worthy "
            "pass their trials. Each awakened element joins your glyphs, and each Circle you break makes your soul vaster. "
            "But beware: something at the edges of the world watches your ascension with far too much interest."),
        "outro": (
            "Com dois elementos correndo nas suas magias e o Terceiro Círculo rompido, o véu entre os mundos fica fino para você. Num "
            "pergaminho antigo achado num santuário, glifos estranhos ligam uma magia à outra, como frases de um feitiço vivo...",
            "With two elements flowing through your spells and the Third Circle broken, the veil between worlds grows thin for you. On an "
            "old scroll found in a shrine, strange glyphs bind one spell to the next, like sentences of a living incantation..."),
        "steps": {
            "second_circle": (
                ("Ascensão: 2º Círculo", "Ascension: 2nd Circle"),
                ("A cada dez níveis a alma bate num teto invisível. Só o Ritual de Ascensão o rompe — e ele cobra um preço: encarar a "
                 "própria Sombra.",
                 "Every ten levels the soul hits an invisible ceiling. Only the Ascension Ritual breaks it — and it asks a price: "
                 "facing your own Shadow."),
                ("No nível 10, monte o Ritual de Ascensão: o Núcleo de Ascensão cercado pelos pedestais com os componentes pedidos. "
                 "Sobreviva à Tribulação. O XP guardado volta todo depois.",
                 "At level 10, build the Ascension Ritual: the Ascension Core surrounded by pedestals holding the requested "
                 "components. Survive the Tribulation. Your banked XP comes back afterwards."),
                ("Ritual de Ascensão", "Ascension Ritual")),
            "shrine_trial": (
                ("Vença um Santuário", "Conquer a Shrine"),
                ("Os santuários não se impressionam com títulos nem com histórias de outro mundo. Só com coragem e mana.",
                 "The shrines are not impressed by titles or stories from another world. Only by courage and mana."),
                ("A seta aponta o Santuário Elemental mais próximo que você ainda não venceu: Brasas (badlands), Submerso (oceano), "
                 "Caverna Ancestral, Pico dos Ventos (montanhas), Catedral em Ruínas e Trevas. Ative o altar e sobreviva.",
                 "The arrow points to the nearest Elemental Shrine you have not conquered yet: Embers (badlands), Sunken (ocean), "
                 "Ancestral Cavern, Windswept Peak (mountains), Ruined Cathedral and Darkness. Activate the altar and survive."),
                ("Siga a seta: santuário", "Follow the arrow")),
            "second_element": (
                ("Desperte o 2º elemento", "Awaken a 2nd element"),
                ("Entre os nativos, quem controla dois elementos já é chamado de prodígio.",
                 "Among the natives, anyone who controls two elements is already called a prodigy."),
                ("Vença o santuário de um elemento que você ainda não tem (a seta aponta o mais próximo), ou suba os rituais da "
                 "sua origem e use Despertar na aba Caminhos.",
                 "Conquer the shrine of an element you do not have yet (the arrow points to the nearest), or raise your origin's "
                 "rituals and use Awaken in the Paths tab."),
                ("Santuário ou aba Caminhos", "Shrine or Paths tab")),
            "multi_spell": (
                ("Magia de 2 elementos", "Two-element spell"),
                ("Fogo que congela. Vento que queima. Os magos antigos chamavam isso de heresia; os invocados chamam de terça-feira.",
                 "Fire that freezes. Wind that burns. Ancient mages called it heresy; summoned heroes call it Tuesday."),
                ("No Grimório, use glifos dos seus dois elementos na mesma magia (ex.: Projétil + um efeito de cada). O slot define "
                 "o elemento principal; cada efeito fere com o próprio elemento.",
                 "In the Grimoire, use glyphs from both your elements in the same spell (e.g. Projectile + one effect of each). The "
                 "slot sets the main element; each effect hits with its own element."),
                ("Misture glifos no Grimório", "Mix glyphs in the Grimoire")),
            "trial_two": (
                ("Vença a Provação II", "Win Trial II"),
                ("Na segunda Provação, as ondas chegam com Elites e o Avatar já não hesita.",
                 "In the second Trial, the waves bring Elites and the Avatar no longer hesitates."),
                ("Leve um elemento ao nível 6 e inicie a Provação II na aba Provações (B). Observe o chão: os ataques do Avatar são "
                 "anunciados por círculos que se enchem antes de explodir.",
                 "Raise an element to level 6 and start Trial II in the Trials tab (B). Watch the ground: the Avatar's attacks are "
                 "announced by circles that fill up before they explode."),
                ("B → aba Provações", "B → Trials tab")),
            "third_circle": (
                ("Ascensão: 3º Círculo", "Ascension: 3rd Circle"),
                ("O Terceiro Círculo é o limiar entre aprendizes e magos de verdade. É também a chave dos portais elementais.",
                 "The Third Circle is the threshold between apprentices and true mages. It is also the key to the elemental portals."),
                ("Repita a Ascensão ao chegar ao nível 20. Cada Círculo pede componentes mais raros e uma Sombra mais forte.",
                 "Repeat the Ascension when you reach level 20. Each Circle asks for rarer components and a stronger Shadow."),
                ("Nível 20 + Ascensão", "Level 20 + Ascension")),
            "world_boss": (
                ("Derrote um chefão", "Defeat a world boss"),
                ("O Rei Slime, o Rei Goblin, o Lorde Orc e o Minotauro aterrorizam as terras. Os bardos pagariam bem por uma boa história.",
                 "The King Slime, the Goblin King, the Orc Lord and the Minotaur terrorize the lands. Bards would pay well for a good story."),
                ("A seta aponta a fortaleza goblin, o acampamento orc ou o Labirinto do Minotauro mais próximo. O Rei Slime vive "
                 "nos pântanos. Leve aliados.",
                 "The arrow points to the nearest goblin fortress, orc camp or Minotaur Labyrinth. The King Slime lives in swamps. "
                 "Bring allies."),
                ("Siga a seta até o covil", "Follow the arrow")),
        },
    },
    "programmed_magic": {
        "title": ("Magia Programável", "Programmed Magic"),
        "lore": (
            "O pergaminho do santuário não descreve uma magia: descreve uma SEQUÊNCIA. Um feixe que, ao acertar, faz nascer uma "
            "zona de fogo. Um raio que pula de inimigo em inimigo enquanto houver mana. Os glifos de Ligação transformam o "
            "Grimório num livro de programas — e quem domina isso luta como um exército.",
            "The shrine's scroll does not describe a spell: it describes a SEQUENCE. A beam that, on hitting, gives birth to a zone "
            "of fire. A bolt that jumps from enemy to enemy while there is mana. Link glyphs turn the Grimoire into a book of "
            "programs — and whoever masters them fights like an army."),
        "outro": (
            "Suas magias agora pensam por si: acertam, ramificam, saltam. Uma noite, um cristal no seu bolso começa a zumbir e "
            "aponta para o céu. Lá em cima, além das nuvens, ilhas flutuam sobre um oceano suspenso. O Plano Elemental chama.",
            "Your spells now think for themselves: they hit, branch, leap. One night a crystal in your pocket starts to hum and "
            "points at the sky. Up there, beyond the clouds, islands float over a suspended ocean. The Elemental Plane is calling."),
        "steps": {
            "learn_link": (
                ("Aprenda uma Ligação", "Learn a Link glyph"),
                ("Ao Acertar, Ao Terminar, Ao Abater: três palavras que costuram uma magia na outra.",
                 "On Hit, On End, On Kill: three words that stitch one spell into the next."),
                ("O Caminho Arcano ensina as Ligações sozinho: \"Ao Acertar\" e \"Ao Terminar\" no nível 5, \"Ao Abater\" e "
                 "\"Saltar\" no nível 10. Elas aparecem na paleta do Grimório, no grupo das Ligações. Pergaminhos de Glifo também ensinam.",
                 "The Arcane Path teaches Links by itself: \"On Hit\" and \"On End\" at level 5, \"On Kill\" and \"Leap\" at "
                 "level 10. They show up in the Grimoire palette, in the Links group. Glyph Scrolls teach them too."),
                ("Liberada no nível 5", "Unlocked at level 5")),
            "program_spell": (
                ("Magia programável", "Programmed spell"),
                ("Feixe → Ao Acertar → Zona. Simples assim. O inimigo nem entende de onde veio o segundo golpe.",
                 "Beam → On Hit → Zone. That simple. The enemy never understands where the second blow came from."),
                ("Monte um \"programa\": Forma + Efeito + Ao Acertar + outra Forma + Efeito (ex.: Projétil + Dano Arcano + Ao Acertar "
                 "+ Toque + Empurrar). Depois de uma Ligação vem SEMPRE uma Forma.",
                 "Build a \"program\": Form + Effect + On Hit + another Form + Effect (e.g. Projectile + Arcane Damage + On Hit + Touch "
                 "+ Push). A Link is ALWAYS followed by a Form."),
                ("Forma → Ligação → Forma", "Form → Link → Form")),
            "leap_spell": (
                ("Corrente com Saltar", "Chain with Leap"),
                ("Um raio que pula de inimigo em inimigo até não sobrar ninguém — ou até a sua mana secar.",
                 "A bolt that jumps from enemy to enemy until nobody is left — or until your mana runs dry."),
                ("Coloque o glifo \"Saltar\" (liberado no nível 10) no FIM da magia: Projétil + Dano Arcano + Saltar. Cada salto "
                 "custa um pouco de mana. Experimente contra um grupo de monstros!",
                 "Put the \"Leap\" glyph (unlocked at level 10) at the END of the spell: Projectile + Arcane Damage + Leap. Each "
                 "jump costs a bit of mana. Try it against a group of monsters!"),
                ("Saltar no fim da magia", "Leap at the spell's end")),
            "trial_three": (
                ("Vença a Provação III", "Win Trial III"),
                ("Quatro ondas, Elites furiosos e um Avatar que já conjura magias programáveis contra você.",
                 "Four waves, furious Elites and an Avatar that already casts programmed spells against you."),
                ("Leve um elemento ao nível 9 (Círculo 2) e inicie a Provação III na aba Provações (B). Chame amigos: cada um que entra "
                 "deixa a arena mais cheia, mas o grupo fica bem mais forte.",
                 "Raise an element to level 9 (Circle 2) and start Trial III in the Trials tab (B). Bring friends: each one adds enemies, "
                 "but the group gets much stronger."),
                ("B → aba Provações", "B → Trials tab")),
            "pedestal": (
                ("Ative um Pedestal Rúnico", "Power a Runic Pedestal"),
                ("Um mago esperto não colhe trigo com as mãos. Um Pedestal Rúnico conjura a sua magia sozinho, sem descanso.",
                 "A clever mage does not harvest wheat by hand. A Runic Pedestal casts your spell on its own, tirelessly."),
                ("Faça um Pedestal Rúnico e ponha uma Bateria de Mana a até 8 blocos (agache + clique nela para passar mana). "
                 "Grave uma magia num Pergaminho de Magia no Grimório e clique com ele no pedestal. Quando ele conjurar "
                 "sozinho, o passo se completa.",
                 "Craft a Runic Pedestal and place a Mana Battery within 8 blocks (sneak + click it to pour in mana). Write a "
                 "spell onto a Spell Scroll in the Grimoire and click the pedestal with it. Once it casts on its own, the "
                 "step is done."),
                ("Pedestal + bateria", "Pedestal + battery")),
        },
    },
    "elemental_plane": {
        "title": ("O Plano Elemental", "The Elemental Plane"),
        "lore": (
            "Além do céu existe um arquipélago impossível: chamas que ardem sobre um oceano suspenso, montanhas que flutuam entre "
            "tempestades eternas. Ali reinam os quatro Guardiões Elementais — Ignis, Maré, o Golem Primordial e Zéfiro —, antigos o "
            "bastante para lembrar quem selou os santuários. Prove o seu valor diante deles e aprenda a fundir elementos, como só os "
            "grandes magos da história conseguiram.",
            "Beyond the sky lies an impossible archipelago: flames burning above a suspended ocean, mountains drifting between eternal "
            "storms. There reign the four Elemental Guardians — Ignis, Tide, the Primordial Golem and Zephyr — old enough to remember "
            "who sealed the shrines. Prove your worth before them and learn to fuse elements, as only history's greatest mages ever did."),
        "outro": (
            "Os Guardiões caídos deixam núcleos pulsando com poder primordial. Em seus últimos suspiros, todos contam a mesma história: "
            "a Luz e a Sombra, separadas desde a Grande Ruptura, despertaram. E algo entre elas — o Crepúsculo — está faminto. "
            "No meio dos tesouros, um pergaminho dourado brilha: uma Magia Lendária.",
            "The fallen Guardians leave behind cores pulsing with primordial power. With their last breaths, they all tell the same story: "
            "Light and Shadow, apart since the Great Rupture, have awakened. And something between them — the Twilight — is hungry. "
            "Among the treasures, a golden scroll glows: a Legendary Spell."),
        "steps": {
            "enter_plane": (
                ("Vá ao Plano Elemental", "To the Elemental Plane"),
                ("Um portal de cristais elementais rasga o céu e abre caminho para o arquipélago flutuante.",
                 "A portal of elemental crystals tears the sky open and leads to the floating archipelago."),
                ("A partir do Terceiro Círculo, construa a moldura do Portal Elemental e ative-a com um Cristal de Mana. Leve "
                 "blocos: as ilhas ficam longe umas das outras.",
                 "From the Third Circle on, build the Elemental Portal frame and activate it with a Mana Crystal. Bring blocks: "
                 "the islands are far apart."),
                ("Portal Elemental", "Elemental Portal")),
            "first_guardian": (
                ("Derrote um Guardião", "Defeat a Guardian"),
                ("Ignis nas Ilhas de Brasa, Maré no Mar Suspenso, o Golem Primordial nos Picos Terrosos e Zéfiro nos Céus de Tempestade.",
                 "Ignis in the Ember Isles, Tide in the Suspended Sea, the Primordial Golem in the Earthen Peaks and Zephyr in the Storm Skies."),
                ("No Plano, a seta aponta o Templo do Guardião mais próximo que você ainda não venceu. Os golpes grandes são marcados "
                 "no chão antes de cair: saia do círculo! O Bestiário mostra as fraquezas.",
                 "In the Plane, the arrow points to the nearest Guardian Temple you have not beaten yet. Big attacks are marked on "
                 "the ground before they land: leave the circle! The Bestiary shows their weaknesses."),
                ("Siga a seta até o templo", "Follow the arrow")),
            "trial_four": (
                ("Vença a Provação IV", "Win Trial IV"),
                ("A Provação do Mestre. Quem a vence deixa de ser aluno do elemento e passa a conversar com ele de igual para igual.",
                 "The Master's Trial. Whoever wins it stops being the element's student and starts speaking to it as an equal."),
                ("Leve um elemento ao nível 12 (Círculo 3) e inicie a Provação IV na aba Provações (B). Ela libera o nó Mestre do elemento.",
                 "Raise an element to level 12 (Circle 3) and start Trial IV in the Trials tab (B). It unlocks the element's Master node."),
                ("B → aba Provações", "B → Trials tab")),
            "two_masters": (
                ("Mestre em 2 elementos", "Master of 2 elements"),
                ("Dois elementos dominados por completo. Só assim a fusão deixa de ser um sonho perigoso.",
                 "Two elements fully mastered. Only then does fusion stop being a dangerous dream."),
                ("O nó Mestre de cada elemento vem da Provação IV dele. Vença a Provação IV em dois elementos base.",
                 "Each element's Master node comes from its Trial IV. Win Trial IV with two base elements."),
                ("Provação IV em 2 elementos", "Trial IV with 2 elements")),
            "first_fusion": (
                ("Desperte uma fusão", "Awaken a fusion"),
                ("Fogo e Terra viram Magma; Água e Vento, Gelo; Luz e Sombra, Crepúsculo. Fundir elementos é reescrever as regras do mundo.",
                 "Fire and Earth become Magma; Water and Wind, Ice; Light and Shadow, Twilight. Fusing elements is rewriting the world's rules."),
                ("No Plano Elemental, use o Altar da Convergência com dois elementos Mestres, essências dos dois e um Bloco de "
                 "Cristal de Mana.",
                 "In the Elemental Plane, use the Convergence Altar with two Master elements, essences of both and a Mana Crystal "
                 "Block."),
                ("Altar da Convergência", "Convergence Altar")),
            "fourth_circle": (
                ("Ascensão: 4º Círculo", "Ascension: 4th Circle"),
                ("No Quarto Círculo nascem as magias supremas: o slot G passa a guardar o seu golpe final.",
                 "At the Fourth Circle ultimate spells are born: the G slot now holds your finishing blow."),
                ("Chegue ao nível 30 e faça a Ascensão. Núcleos de Guardião e essências do Plano são ótimos componentes.",
                 "Reach level 30 and perform the Ascension. Guardian cores and Plane essences make great components."),
                ("Nível 30 + Ascensão", "Level 30 + Ascension")),
            "all_guardians": (
                ("Derrote os 4 Guardiões", "Defeat all 4 Guardians"),
                ("Quem vence os quatro Guardiões é lembrado nas canções do Plano Elemental como \"o que veio de longe\".",
                 "Whoever defeats all four Guardians is remembered in the songs of the Elemental Plane as \"the one from afar\"."),
                ("A seta aponta o próximo templo que falta. Fogo, Água, Terra e Vento — um de cada vez, ou com amigos.",
                 "The arrow points to the next temple left. Fire, Water, Earth and Wind — one at a time, or with friends."),
                ("Siga a seta até o templo", "Follow the arrow")),
        },
    },
    "light_and_shadow": {
        "title": ("Luz e Sombra", "Light and Shadow"),
        "lore": (
            "Abaixo do mundo, o Abismo pulsa como um coração negro, governado pelo Soberano que devora estrelas. Acima das nuvens, o "
            "Santuário Celestial brilha em cidades de cristal, vigiado pelo Serafim de seis asas. Os dois acreditam que você é a chave "
            "para encerrar a guerra que os separa. Com uma Magia Lendária nas mãos e o Sexto Círculo à vista, os dois mundos "
            "abrirão as portas.",
            "Below the world, the Abyss throbs like a black heart, ruled by the Sovereign who devours stars. Above the clouds, the "
            "Celestial Sanctuary shines with crystal cities, watched by the six-winged Seraph. Both believe you are the key to ending "
            "the war that divides them. With a Legendary Spell in hand and the Sixth Circle in sight, both worlds will open their gates."),
        "outro": (
            "O Soberano e o Serafim caíram, e com eles o equilíbrio que mantinha o Crepúsculo adormecido. Uma fenda se abre no horizonte, "
            "sangrando luz violeta. O Espírito Guia, pela primeira vez, parece ter medo: \"Foi para isto que você foi invocado.\"",
            "The Sovereign and the Seraph have fallen, and with them the balance that kept the Twilight asleep. A rift opens on the horizon, "
            "bleeding violet light. For the first time, the Guide Spirit seems afraid: \"This is what you were summoned for.\""),
        "steps": {
            "trial_five": (
                ("Vença a Provação V", "Win Trial V"),
                ("Na Provação do Cataclismo, quem espera na arena não é um Avatar: é o próprio Guardião do elemento.",
                 "In the Cataclysm Trial, what waits in the arena is not an Avatar: it is the element's Guardian itself."),
                ("Leve um elemento ao nível 15 (Círculo 4) e inicie a Provação V na aba Provações (B). Cinco ondas e um Guardião: vá "
                 "em grupo se puder.",
                 "Raise an element to level 15 (Circle 4) and start Trial V in the Trials tab (B). Five waves and a Guardian: go as a "
                 "group if you can."),
                ("B → aba Provações", "B → Trials tab")),
            "cast_legend": (
                ("Conjure uma Lenda", "Cast a Legend"),
                ("Explosão. Portão do Tesouro. Vazio Roxo. Magias que viraram canção — e agora estão escritas no seu Grimório.",
                 "Explosion. Gate of Treasure. Hollow Purple. Spells that became songs — and are now written in your Grimoire."),
                ("Use o Pergaminho Lendário do capítulo anterior (a magia vai para a Biblioteca do Grimório), coloque-a num slot do "
                 "elemento dela e conjure. Se faltar algum glifo, o Grimório mostra qual: eles vêm dos nós do caminho (Provações). "
                 "Cuidado: algumas gastam TODO o mana.",
                 "Use the Legendary Scroll from the previous chapter (the spell goes to the Grimoire's Library), put it in a slot "
                 "of its element and cast it. If a glyph is missing, the Grimoire tells you which: they come from path nodes "
                 "(Trials). Careful: some spend ALL your mana."),
                ("Pergaminho Lendário → slot", "Legendary Scroll → slot")),
            "sixth_circle": (
                ("Ascensão: 6º Círculo", "Ascension: 6th Circle"),
                ("No Sexto Círculo nasce a Expansão de Domínio: um mundo dentro do mundo, onde as suas regras valem mais que as da realidade.",
                 "At the Sixth Circle the Domain Expansion is born: a world within the world, where your rules outweigh reality's."),
                ("Continue subindo de nível e fazendo Ascensões (níveis 40 e 50). Componentes raros vêm dos Guardiões, do Plano "
                 "Elemental, das Provações e da Guilda.",
                 "Keep leveling up and performing Ascensions (levels 40 and 50). Rare components come from the Guardians, the "
                 "Elemental Plane, the Trials and the Guild."),
                ("Ascensão nv. 40 e 50", "Ascend at lv. 40 & 50")),
            "enter_abyss": (
                ("Desça ao Abismo", "Descend into the Abyss"),
                ("Nas Profundezas Abissais a escuridão tem peso, e os olhos que observam do Vazio nunca piscam.",
                 "In the Abyssal Depths darkness has weight, and the eyes watching from the Void never blink."),
                ("Construa a moldura do Portal do Abismo e ative-a oferecendo uma Essência de Sombra (Círculo 6). Leve fontes de "
                 "luz — muitas.",
                 "Build the Abyss Portal frame and activate it by offering a Shadow Essence (Circle 6). Bring light sources — "
                 "lots of them."),
                ("Portal do Abismo", "Abyss Portal")),
            "abyss_sovereign": (
                ("Derrote o Soberano", "Defeat the Sovereign"),
                ("O Soberano governa o Abismo do alto de um trono feito de estrelas mortas.",
                 "The Sovereign rules the Abyss from a throne made of dead stars."),
                ("No Abismo, a seta aponta o Trono do Abismo. A Luz é a sua melhor arma lá embaixo; fique de olho nas marcas no chão.",
                 "In the Abyss, the arrow points to the Abyss Throne. Light is your best weapon down there; watch the marks on the ground."),
                ("Siga a seta até o trono", "Follow the arrow")),
            "enter_sanctuary": (
                ("Suba ao Santuário", "Reach the Sanctuary"),
                ("Cidades de cristal sobre jardins de nuvens, guardadas por anjos que não toleram intrusos.",
                 "Crystal cities over cloud gardens, guarded by angels who do not tolerate intruders."),
                ("Construa o Farol Celestial sobre ouro, a céu aberto, e ative-o ao meio-dia com uma Essência de Luz (Círculo 6) "
                 "para subir a Escada de Luz.",
                 "Build the Celestial Beacon on gold, under open sky, and activate it at noon with a Light Essence (Circle 6) to "
                 "climb the Stairway of Light."),
                ("Farol Celestial, meio-dia", "Celestial Beacon at noon")),
            "seraph": (
                ("Derrote o Serafim", "Defeat the Seraph"),
                ("Seis asas, mil olhos e uma convicção absoluta de que a ordem vale mais do que a liberdade.",
                 "Six wings, a thousand eyes and an absolute conviction that order is worth more than freedom."),
                ("No Santuário, a seta aponta a Catedral Celestial. A Sombra fere o que a Luz protege.",
                 "In the Sanctuary, the arrow points to the Celestial Cathedral. Shadow wounds what Light protects."),
                ("Siga a seta até a catedral", "Follow the arrow")),
            "domain": (
                ("Libere o seu Domínio", "Unlock your Domain"),
                ("\"Expansão de Domínio.\" Duas palavras que fazem até chefes recuarem um passo.",
                 "\"Domain Expansion.\" Two words that make even bosses take a step back."),
                ("O nó Domínio de um elemento vem da Provação VI dele (nível 19, Círculo 6).",
                 "An element's Domain node comes from its Trial VI (level 19, Circle 6)."),
                ("Provação VI (nível 19)", "Trial VI (level 19)")),
        },
    },
    "twilight": {
        "title": ("Crepúsculo", "Twilight"),
        "lore": (
            "A Fenda do Crepúsculo é uma ferida no tecido do mundo, onde Luz e Sombra se misturam em algo que não deveria existir. Do "
            "outro lado espera o Arauto do Crepúsculo — o ser que provocou a Grande Ruptura e que, há mil anos, invocou o primeiro herói "
            "de outro mundo... e o devorou. Agora ele chama por você. Rompa o Nono Círculo e encerre esta história.",
            "The Twilight Rift is a wound in the fabric of the world, where Light and Shadow blend into something that should not exist. "
            "On the other side waits the Twilight Herald — the being who caused the Great Rupture and who, a thousand years ago, summoned "
            "the first hero from another world... and devoured them. Now it calls for you. Break the Ninth Circle and end this story."),
        "outro": (
            "O Arauto do Crepúsculo se desfaz em mil fragmentos de luz e sombra.",
            "The Twilight Herald shatters into a thousand fragments of light and shadow."),
        "epilogue": [
            ("Por um instante, você ouve todas as vozes que ele devorou ao longo de mil anos — e todas elas agradecem.",
             "For an instant you hear every voice it devoured over a thousand years — and every one of them gives thanks."),
            ("A Fenda se fecha. Os seis elementos voltam a girar em harmonia, e as duas luas brilham juntas pela primeira vez desde a Grande Ruptura.",
             "The Rift closes. The six elements spin in harmony again, and the two moons shine together for the first time since the Great Rupture."),
            ("Na Guilda, a recepcionista atualiza o seu cartão com as mãos tremendo. Não existe rank acima de S... mas ela escreve um mesmo assim.",
             "At the Guild, the receptionist updates your card with trembling hands. There is no rank above S... but she writes one anyway."),
            ("Nas vilas, os bardos já cantam sobre o forasteiro de outro mundo que rompeu nove Círculos e enfrentou o fim de todas as coisas.",
             "In the villages, bards already sing of the outsider from another world who broke nine Circles and faced the end of all things."),
            ("O Espírito Guia surge uma última vez, mais brilhante do que nunca: \"Você poderia voltar para casa agora... mas este mundo já é a sua casa, não é?\"",
             "The Guide Spirit appears one last time, brighter than ever: \"You could go home now... but this world is already your home, isn't it?\""),
            ("A sua lenda está escrita. O que vem depois, só você decide.",
             "Your legend is written. What comes next is yours alone to decide."),
        ],
        "steps": {
            "seventh_circle": (
                ("Ascensão: 7º Círculo", "Ascension: 7th Circle"),
                ("Sete Círculos. Os nativos já não te chamam de invocado: chamam de \"Senhor\" e abaixam a cabeça.",
                 "Seven Circles. The natives no longer call you summoned: they call you \"Lord\" and bow their heads."),
                ("Chegue ao nível 60 e faça a Ascensão. Núcleos do Soberano e do Serafim e tesouros do Abismo e do Céu ajudam muito.",
                 "Reach level 60 and perform the Ascension. Cores of the Sovereign and the Seraph and treasures of the Abyss and "
                 "Heaven help a lot."),
                ("Nível 60 + Ascensão", "Level 60 + Ascension")),
            "ninth_circle": (
                ("Ascensão: 9º Círculo", "Ascension: 9th Circle"),
                ("No Nono Círculo, a sua mana deixa de ser um rio e vira um oceano. A Fenda começa a responder à sua presença.",
                 "At the Ninth Circle your mana stops being a river and becomes an ocean. The Rift begins to answer your presence."),
                ("Os últimos Círculos (níveis 70 e 80) exigem os componentes mais raros: núcleos de Guardiões e tesouros do Abismo "
                 "e do Céu.",
                 "The last Circles (levels 70 and 80) demand the rarest components: Guardian cores and treasures from the Abyss "
                 "and Heaven."),
                ("Ascensão nv. 70 e 80", "Ascend at lv. 70 & 80")),
            "enter_rift": (
                ("Atravesse a Fenda", "Cross the Rift"),
                ("Do outro lado da Fenda o tempo não passa: só existe o crepúsculo eterno e uma arena à sua espera.",
                 "Beyond the Rift time does not pass: there is only eternal twilight and an arena awaiting you."),
                ("Com o Nono Círculo e o Soberano e o Serafim derrotados, a Fenda do Crepúsculo pode ser aberta. Vá preparado.",
                 "With the Ninth Circle and the Sovereign and the Seraph defeated, the Twilight Rift can be opened. Go prepared."),
                ("Fenda do Crepúsculo", "Twilight Rift")),
            "herald": (
                ("Derrote o Arauto", "Defeat the Herald"),
                ("Ele também expande o próprio Domínio. Quando os dois Domínios colidirem, só uma vontade vai prevalecer.",
                 "It also expands its own Domain. When the two Domains collide, only one will can prevail."),
                ("Leve as suas melhores magias, uma Lenda, um Domínio pronto e cura de sobra. Quando ele expandir o Domínio dele, "
                 "responda com o seu.",
                 "Bring your best spells, a Legend, a ready Domain and plenty of healing. When it expands its Domain, answer with yours."),
                ("A arena além da Fenda", "The arena beyond the Rift")),
            "archmage": (
                ("Torne-se Arquimago", "Become an Archmage"),
                ("O título que nenhum nativo alcança há séculos. Para um invocado, talvez seja só o começo.",
                 "The title no native has reached in centuries. For a summoned one, it may be just the beginning."),
                ("O nó Arquimago vem da Provação VII de um elemento (nível 24, Círculo 8): o Guardião e o Avatar juntos.",
                 "The Archmage node comes from an element's Trial VII (level 24, Circle 8): the Guardian and the Avatar together."),
                ("Provação VII (nível 24)", "Trial VII (level 24)")),
        },
    },
}


# =====================================================================================
#  v0.9: textos da rota-guia (chefes, portais como estruturas, "A Ascensão", cidades e Pedras de Teleporte),
#  falas curtas do Espírito Guia na abertura de cada capítulo e dicas por classe/categoria.
#  Dica/dica curta podem ser um dict {"": padrão, "<classe ou categoria>": texto} (passos com "hint_by").
# =====================================================================================
ST = lambda ch: CH_TEXT[ch]["steps"]  # noqa: E731


def _move(src, dst, sid):
    ST(dst)[sid] = ST(src).pop(sid)


# Passos que saíram da rota (os marcadores antigos continuam no save e não atrapalham)
ST("six_shrines").pop("world_boss")
ST("twilight").pop("seventh_circle")
ST("light_and_shadow").pop("sixth_circle")
CH_TEXT["twilight"].pop("epilogue")
ST("elemental_plane").pop("fourth_circle")  # o 4º Círculo agora fecha "Senhores dos Monstros"

ALTAR_PT = "um Altar de Ascensão pronto (igreja das cidades, Academia ou Santuário — a seta aponta o mais perto)"
ALTAR_EN = "a ready Ascension Altar (city churches, the Academy or a Sanctum — the arrow points to the nearest)"
ORD_PT = {2: "2º", 3: "3º", 4: "4º", 5: "5º", 6: "6º", 7: "7º", 8: "8º", 9: "9º", 10: "10º"}
ORD_EN = {2: "2nd", 3: "3rd", 4: "4th", 5: "5th", 6: "6th", 7: "7th", 8: "8th", 9: "9th", 10: "10th"}


def ascension_text(n, deed_pt, deed_en, tip_pt, tip_en, lore_pt, lore_en):
    lv = LEVEL_CAP[n - 1]
    return (
        ("A Ascensão → Círculo %d" % n, "Ascension → Circle %d" % n),
        (lore_pt, lore_en),
        ("Chegue ao nível %d (o teto do Círculo atual) e %s. O Diário te entrega o Tributo do Círculo %d: é só "
         "levá-lo a %s." % (lv, deed_pt, n, ALTAR_PT),
         "Reach level %d (your current Circle's ceiling) and %s. The Journal hands you the Circle %d Tribute: just "
         "bring it to %s." % (lv, deed_en, n, ALTAR_EN)),
        (tip_pt, tip_en))


def circle_text(n, lore_pt, lore_en):
    return (
        ("Ascensão: %s Círculo" % ORD_PT[n], "Ascension: %s Circle" % ORD_EN[n]),
        (lore_pt, lore_en),
        ("Leve o Tributo do Círculo %d a %s. Ponha-o no altar, ative o Núcleo de Ascensão e vença a sua Sombra na "
         "Tribulação. O XP guardado volta todo depois." % (n, ALTAR_PT),
         "Bring the Circle %d Tribute to %s. Place it on the altar, activate the Ascension Core and defeat your Shadow "
         "in the Tribulation. All banked XP comes back afterwards." % (n, ALTAR_EN)),
        ("Tributo → Altar pronto", "Tribute → ready Altar"))


def boss_text(title, lore, hint, tip):
    return (title, lore, hint, tip)


# ----- Despertar: 15 classes em 3 categorias --------------------------------------------------------------------
ST("awakening")["choose_path"] = (
    ("Escolha sua classe", "Choose your class"),
    ("Mágicas, Físicas ou de Destreza: quinze classes, cada uma com o seu recurso (Mana, Vigor ou Foco) e um sistema "
     "próprio — a Forja, o Roubo, a Zanpakutō, a Sorte... A classe decide como você luta, como cresce e quais elementos "
     "entendem a sua alma.",
     "Magic, Physical or Dexterity: fifteen classes, each with its own resource (Mana, Vigor or Focus) and its own system "
     "— the Forge, Theft, the Zanpakutō, Luck... Your class decides how you fight, how you grow and which elements "
     "understand your soul."),
    ("A escolha de classe aparece quando você entra no mundo. Se a fechou sem escolher, saia e entre de novo para vê-la.",
     "The class choice appears when you join the world. If you closed it without choosing, leave and rejoin to see it."),
    ("Aparece ao entrar no mundo", "Shown when you join"))
ST("awakening")["build_spell"] = (
    ("Monte a sua 1ª magia", "Build your first spell"),
    ST("awakening")["build_spell"][1],
    {"": ("No Grimório, junte uma Forma (como Projétil ou Toque) e um Efeito (como Dano Arcano ou Empurrar), dê um nome "
          "e salve num slot livre (C) ou na Biblioteca. As magias prontas em Z e X servem de exemplo.",
          "In the Grimoire, combine a Form (like Projectile or Touch) with an Effect (like Arcane Damage or Push), name it "
          "and save it in a free slot (C) or in the Library. The ready spells in Z and X are good examples."),
     "magic": ("No Grimório, junte uma Forma (como Projétil ou Toque) e um Efeito (como Dano Arcano ou Empurrar), dê "
               "um nome e salve num slot livre (C) ou na Biblioteca. As magias prontas em Z e X servem de exemplo.",
               "In the Grimoire, combine a Form (like Projectile or Touch) with an Effect (like Arcane Damage or Push), "
               "name it and save it in a free slot (C) or in the Library. The ready spells in Z and X are good examples."),
     "physical": ("Técnicas também são glifos. No Grimório, junte uma Forma da sua classe (ou o Golpe: o próximo ataque "
                  "carrega os efeitos) com um Efeito e salve num slot livre (C). Tudo combina com os seus elementos.",
                  "Techniques are glyphs too. In the Grimoire, combine a Form from your class (or Strike: your next hit "
                  "carries the effects) with an Effect and save it in a free slot (C). Everything combines with your "
                  "elements."),
     "dexterity": ("Técnicas também são glifos. No Grimório, junte uma Forma da sua classe (bala, carta, bola de aço, "
                   "flecha...) com um Efeito, como Empurrar ou Dano Arcano, e salve num slot livre (C).",
                   "Techniques are glyphs too. In the Grimoire, combine a Form from your class (bullet, card, steel ball, "
                   "arrow...) with an Effect, like Push or Arcane Damage, and save it in a free slot (C).")},
    {"": ("Forma + Efeito → salvar", "Form + Effect → save"),
     "magic": ("Forma + Efeito → salvar", "Form + Effect → save"),
     "physical": ("Forma da classe + Efeito", "Class Form + Effect"),
     "dexterity": ("Forma da classe + Efeito", "Class Form + Effect")})
ST("awakening")["first_spell"] = (
    ("Use uma magia ou técnica", "Use a spell or technique"),
    ST("awakening")["first_spell"][1],
    {"": ("Segure Z, X ou C para usar o que está no slot (algumas magias precisam de um tempinho de conjuração). G é o "
          "slot da magia suprema.",
          "Hold Z, X or C to use what is in that slot (some spells take a moment to cast). G is the ultimate slot."),
     "magic": ("Segure Z, X ou C para conjurar a magia do slot (algumas precisam de um tempinho de conjuração). Ela gasta "
               "Mana. G é o slot da magia suprema.",
               "Hold Z, X or C to cast the spell in that slot (some take a moment to cast). It spends Mana. G is the "
               "ultimate slot."),
     "physical": ("Segure Z, X ou C para usar a técnica do slot — cada classe tem as suas prontas (o Ferreiro martela, o "
                  "Ladino furta, o Shinigami corta...). Técnicas gastam Vigor, que volta a cada golpe que acerta.",
                  "Hold Z, X or C to use the technique in that slot — each class has its own ready (the Smith hammers, the "
                  "Rogue steals, the Shinigami cuts...). Techniques spend Vigor, which comes back with every hit."),
     "dexterity": ("Segure Z, X ou C para usar a técnica do slot — cada classe tem as suas (o Pistoleiro atira, o "
                   "Apostador joga cartas, o Steel Ball gira...). Técnicas gastam Foco, que volta a cada acerto.",
                   "Hold Z, X or C to use the technique in that slot — each class has its own (the Gunslinger shoots, the "
                   "Gambler throws cards, Steel Ball spins...). Techniques spend Focus, which comes back with every hit.")},
    {"": ("Segure Z, X ou C", "Hold Z, X or C"),
     "magic": ("Segure Z, X ou C", "Hold Z, X or C"),
     "physical": ("Segure Z, X ou C", "Hold Z, X or C"),
     "dexterity": ("Segure Z, X ou C", "Hold Z, X or C")})
RITUAL_HINTS = {
    "mage": ("Coloque a Mesa de Pesquisa do seu kit, escolha uma tese e cumpra a tarefa pedida para defendê-la.",
             "Place the Research Table from your kit, pick a thesis and complete its task to defend it.",
             "Mesa de Pesquisa do kit", "Research Table (kit)"),
    "witch": ("Coloque o Caldeirão do kit sobre fogo, siga uma receita do livro e cozinhe a poção do ritual.",
              "Place the Cauldron from your kit over a fire, follow a recipe from the book and brew the ritual potion.",
              "Caldeirão do kit", "Cauldron (kit)"),
    "warlock": ("Desenhe o círculo com o giz ritual em volta do Altar de Oferendas e firme o seu primeiro pacto.",
                "Draw the circle with ritual chalk around the Offering Altar and seal your first pact.",
                "Giz ritual + Altar", "Chalk + Offering Altar"),
    "druid": ("Plante a Muda Ancestral do kit, faça a oferenda e cumpra a tarefa de estação até ela crescer.",
              "Plant the Ancestral Sapling from your kit, make the offering and finish the season task until it grows.",
              "Muda Ancestral do kit", "Ancestral Sapling (kit)"),
    "necromancer": ("Coloque o Altar Ossuário do kit e ofereça a ele as almas que você colher dos inimigos.",
                    "Place the Ossuary Altar from your kit and offer it the souls you reap from enemies.",
                    "Altar Ossuário do kit", "Ossuary Altar (kit)"),
    "smith": ("Coloque a Bigorna Arcana do kit, forje e remodele uma lâmina e leve 8 ferros à bigorna: Prova da Forja I.",
              "Place the Arcane Anvil from your kit, forge and reshape a blade and bring 8 iron to the anvil: Forge Trial I.",
              "Bigorna Arcana do kit", "Arcane Anvil (kit)"),
    "rogue": ("Furte com Mão Leve, golpeie pelas costas e use o Livro-Razão do kit para subir ao rank I (Gatuno).",
              "Steal with Light Hand, strike from behind and use the Ledger from your kit to reach rank I (Cutpurse).",
              "Livro-Razão do kit", "Ledger (kit)"),
    "knight": ("Coloque o Altar do Juramento do kit, grave magias na Lâmina Rúnica e preste o Juramento I no altar.",
               "Place the Oath Altar from your kit, engrave spells on the Runic Blade and swear Oath I at the altar.",
               "Altar do Juramento do kit", "Oath Altar (kit)"),
    "enchanter": ("Encante você e os aliados, anule magias e use o Códex numa mesa de encantamento para a Inscrição I.",
                  "Enchant yourself and allies, nullify spells and use the Codex on an enchanting table for Inscription I.",
                  "Códex + mesa de encantar", "Codex + enchanting table"),
    "shinigami": ("Medite com a Zanpakutō (agachado e parado), use Kidō e suba o rank I na aba do Shinigami (B).",
                  "Meditate with the Zanpakutō (crouch still), use Kidō and reach rank I in the Shinigami tab (B).",
                  "Meditar + Kidō", "Meditate + Kidō"),
    "quincy": ("Absorva projéteis, derrote inimigos com o Arco Espiritual e suba o rank I na aba Quincy (B).",
               "Absorb projectiles, defeat enemies with the Spirit Bow and reach rank I in the Quincy tab (B).",
               "Arco + absorção", "Bow + absorption"),
    "gunslinger": ("Acerte 60 balas, recarregue 15 vezes (F) e conclua o rank I na aba Caminho do Gatilho (B).",
                   "Land 60 shots, reload 15 times (F) and claim rank I in the Trigger Path tab (B).",
                   "Tiros + recarga (F)", "Shots + reload (F)"),
    "ranger": ("Chame a matilha, derrube inimigos com flechas e conclua o rank I na aba Trilha do Ranger (B).",
               "Call the pack, take enemies down with arrows and claim rank I in the Ranger's Trail tab (B).",
               "Matilha + flechas", "Pack + arrows"),
    "gambler": ("Role a sorte, acerte cartas e vença a Rodada da Casa na aba Mesa da Casa (B): Ficha de Bronze.",
                "Roll the dice, land cards and win the House Round in the House Table tab (B): Bronze Chip.",
                "Rodada da Casa (B)", "House Round (B)"),
    "steelball": ("Acerte 30 vezes com a bola de aço e fale com o Mestre Rotari (a Carta do kit o chama): 1ª lição.",
                  "Hit 30 times with the steel ball and talk to Master Rotari (the kit's Letter calls him): 1st lesson.",
                  "Bola de aço + Mestre", "Steel ball + Master"),
}
ST("awakening")["first_ritual"] = (
    ("1º rank da sua classe", "Your class's first rank"),
    ("Cada classe cresce de um jeito: teses, receitas, pactos, árvores e almas; a Forja, o Livro-Razão, os Juramentos, a "
     "Inscrição, a Zanpakutō, o Auswählen, o Gatilho, a Trilha, as Fichas da Casa e a Rotação. A Rota da Classe no Diário "
     "guia tudo.",
     "Each class grows its own way: theses, recipes, pacts, trees and souls; the Forge, the Ledger, the Oaths, the "
     "Inscription, the Zanpakutō, the Auswählen, the Trigger, the Trail, the House Chips and the Spin. The Class Route in "
     "the Journal guides it all."),
    dict({"": ("Cada classe tem o seu ritual — veja a Rota da Classe no topo do Diário (B → Diário).",
               "Each class has its own ritual — see the Class Route at the top of the Journal (B → Journal).")},
         **{p: (v[0], v[1]) for p, v in RITUAL_HINTS.items()}),
    dict({"": ("Veja a Rota da Classe", "See the Class Route")}, **{p: (v[2], v[3]) for p, v in RITUAL_HINTS.items()}))

# ----- Guilda e Provações: cidades, Pedras de Teleporte, Rei Slime e a 1ª Ascensão ------------------------------
ST("guild_and_trials")["join_guild"] = (
    ST("guild_and_trials")["join_guild"][0], ST("guild_and_trials")["join_guild"][1],
    ("A seta aponta a cidade (ou vila da Guilda) mais próxima. Fale com a recepcionista da Guilda dos Aventureiros para "
     "receber o seu Cartão. O Espírito Guia, chamado pelo Diário, voa mostrando o caminho.",
     "The arrow points to the nearest city (or Guild village). Talk to the Adventurers' Guild receptionist to get your "
     "Card. The Guide Spirit, called from the Journal, flies ahead showing the way."),
    ("Siga a seta até a Guilda", "Follow the arrow"))
ST("guild_and_trials")["train_resource"] = (
    ("Treine o seu recurso", "Train your resource"),
    ("Mana, Vigor ou Foco: quem esvazia o tanque até a última gota volta com um tanque maior.",
     "Mana, Vigor or Focus: whoever empties the tank to the last drop comes back with a bigger tank."),
    ("Gaste 100% do seu recurso em combate: cada vez conta um treino (até 3 por dia de jogo) e sobe o Limite do recurso, "
     "até o teto do seu nível. Depois de zerar, a regeneração fica lenta por um minuto.",
     "Spend 100% of your resource in combat: each time counts as one training (up to 3 per in-game day) and raises the "
     "resource Limit, up to your level's ceiling. After hitting zero, regeneration is slow for a minute."),
    ("Esvazie 100% do recurso", "Empty 100% of resource"))
ST("guild_and_trials")["level_10"] = (
    ST("guild_and_trials")["level_10"][0], ST("guild_and_trials")["level_10"][1],
    ("No nível 10 você chega ao limite do 1º Círculo. O XP que passar fica guardado e volta depois da Ascensão — e a "
     "missão \"A Ascensão\" deste capítulo te dá o tributo pronto.",
     "At level 10 you reach the First Circle's limit. Extra XP is banked and comes back after the Ascension — and this "
     "chapter's \"Ascension\" quest hands you the tribute ready to use."),
    ("Limite do 1º Círculo", "First Circle's limit"))
ST("guild_and_trials")["reach_city"] = (
    ("Chegue a uma Cidade", "Reach a City"),
    ("Muralhas, sinos, mercados e uma Guilda em cada praça. Três reinos dividem estas terras — Aldória, Sahra e Nordheim — "
     "e todos eles adoram um herói que paga as próprias bebidas.",
     "Walls, bells, markets and a Guild on every square. Three kingdoms share these lands — Aldoria, Sahra and Nordheim — "
     "and all of them love a hero who pays for their own drinks."),
    ("A seta aponta a cidade mais próxima. Lá ficam a Guilda, lojas, um Altar de Ascensão pronto na igreja e uma Pedra "
     "de Teleporte no jardim.",
     "The arrow points to the nearest city. There you will find the Guild, shops, a ready Ascension Altar in the church "
     "and a Waystone in the garden."),
    ("Siga a seta: cidade", "Follow the arrow: city"))
ST("guild_and_trials")["discover_waystone"] = (
    ("Ache a Pedra de Teleporte", "Find a Waystone"),
    ("Pedras antigas, com uma runa que brilha ao seu toque. Quem as tocou uma vez pode voltar a elas num piscar de olhos "
     "— pagando em mana, é claro.",
     "Ancient stones with a rune that glows at your touch. Whoever touched one can return to it in the blink of an eye "
     "— paid in mana, of course."),
    ("Toque uma Pedra de Teleporte (cidades, vilas da Guilda, santuários, covis e portais têm uma) para descobri-la. "
     "Use-a para viajar às já descobertas, ou viaje de qualquer lugar pela aba Viagem (B).",
     "Touch a Waystone (cities, Guild villages, shrines, lairs and portals have one) to discover it. Use it to travel to "
     "the ones you found, or travel from anywhere through the Travel tab (B)."),
    ("Toque a Pedra na cidade", "Touch the city's Waystone"))
ST("guild_and_trials")["king_slime"] = (
    ("Derrote o Rei Slime", "Defeat the King Slime"),
    ("Uma montanha de gelatina com coroa. Ele pula, esmaga e se divide em pedaços que também querem te comer.",
     "A mountain of jelly wearing a crown. It leaps, crushes and splits into pieces that also want to eat you."),
    ("O Rei Slime aparece à noite nos pântanos (a seta aponta o mais próximo). Quando ele saltar, saia da sombra dele! "
     "Fogo funciona bem.",
     "The King Slime shows up at night in swamps (the arrow points to the nearest). When it leaps, get out of its "
     "shadow! Fire works well."),
    ("Pântanos, à noite", "Swamps, at night"))
ST("guild_and_trials")["ascension_2"] = ascension_text(
    2, "vença a Provação I (ou o Rei Slime)", "win Trial I (or the King Slime)",
    "Nível 10 + Provação I", "Level 10 + Trial I",
    "A primeira Ascensão é a mais assustadora: a alma se rompe para crescer. Ninguém deveria fazê-la sem um tributo.",
    "The first Ascension is the scariest: the soul breaks so it can grow. Nobody should face it without a tribute.")

# ----- Os Seis Santuários: Rei Goblin e a 2ª Ascensão ----------------------------------------------------------
ST("six_shrines")["second_circle"] = circle_text(
    2, ST("six_shrines")["second_circle"][1][0], ST("six_shrines")["second_circle"][1][1])
ST("six_shrines")["third_circle"] = circle_text(
    3, ST("six_shrines")["third_circle"][1][0], ST("six_shrines")["third_circle"][1][1])
ST("six_shrines")["goblin_king"] = (
    ("Derrote o Rei Goblin", "Defeat the Goblin King"),
    ("Na fortaleza de paliçadas, o Rei Goblin conta moedas roubadas, grita ordens e jura que a coroa é dele por direito.",
     "In the palisade fortress, the Goblin King counts stolen coins, barks orders and swears the crown is his by right."),
    ("A seta aponta a Fortaleza Goblin mais próxima. Derrube os arqueiros nas torres antes e cuidado com o salto do "
     "rei: ele esmaga o chão onde cai.",
     "The arrow points to the nearest Goblin Fortress. Take the archers in the towers out first and beware the king's "
     "leap: he smashes the ground where he lands."),
    ("Siga a seta: fortaleza", "Follow the arrow: fortress"))
ST("six_shrines")["ascension_3"] = ascension_text(
    3, "derrote o Rei Goblin (ou vença a Provação II)", "defeat the Goblin King (or win Trial II)",
    "Nível 20 + Rei Goblin", "Level 20 + Goblin King",
    "A coroa do Rei Goblin tem mais mana do que ouro. Os sábios das cidades sabem transformá-la em tributo.",
    "The Goblin King's crown holds more mana than gold. City sages know how to turn it into a tribute.")

# ----- Magia Programável: Arquimago Renegado e Bruxa Corrompida -------------------------------------------------
ST("programmed_magic")["renegade_archmage"] = (
    ("Derrote o Arquimago", "Defeat the Archmage"),
    ("Expulso da Academia por \"pesquisas proibidas\", o Arquimago Renegado tomou uma torre inteira — e as ilusões dele "
     "sorriem do mesmo jeito que ele.",
     "Expelled from the Academy for \"forbidden research\", the Renegade Archmage took a whole tower — and his illusions "
     "smile just like he does."),
    ("A seta aponta a Torre do Mago. Quebre o escudo arcano dele para atordoá-lo; das cópias, só a verdadeira não é "
     "levemente transparente.",
     "The arrow points to the Mage Tower. Break his arcane shield to stagger him; of the copies, only the real one is "
     "not slightly see-through."),
    ("Siga a seta: torre", "Follow the arrow: tower"))
ST("programmed_magic")["corrupted_witch"] = (
    ("Derrote a Bruxa Corrompida", "Defeat the Corrupted Witch"),
    ("Na cabana antiga do pântano, uma bruxa que trocou a própria alma por um caldeirão sem fundo.",
     "In the ancient swamp hut, a witch who traded her own soul for a bottomless cauldron."),
    ("A seta aponta a Cabana Antiga. Desvie das garrafas amaldiçoadas, derrube os gatos e ataque quando ela pousar da "
     "vassoura ou beber a poção de cura.",
     "The arrow points to the Ancient Hut. Dodge the cursed bottles, take down the cats and strike when she lands from "
     "her broom or drinks her healing potion."),
    ("Siga a seta: cabana", "Follow the arrow: hut"))

# ----- O Plano Elemental: o Portão é uma estrutura; 6º Círculo ---------------------------------------------------
ST("elemental_plane")["enter_plane"] = (
    ("Vá ao Plano Elemental", "To the Elemental Plane"),
    ("Num círculo de pedra com quatro obeliscos, um portal de cristais elementais dorme há séculos, esperando quem o "
     "acorde.",
     "In a stone circle with four obelisks, a portal of elemental crystals has slept for centuries, waiting for someone "
     "to wake it."),
    ("A seta aponta o Portão Elemental (ruínas com 4 obeliscos). Com o 3º Círculo, ative o portal adormecido com a "
     "oferenda pedida. Leve blocos: as ilhas do Plano ficam longe umas das outras.",
     "The arrow points to the Elemental Gate (ruins with 4 obelisks). With the 3rd Circle, activate the dormant portal "
     "with the requested offering. Bring blocks: the Plane's islands are far apart."),
    ("Siga a seta: Portão", "Follow the arrow: Gate"))
ST("elemental_plane")["sixth_circle"] = circle_text(
    6, "No Sexto Círculo nasce a Expansão de Domínio: um mundo dentro do mundo, onde as suas regras valem mais que as "
       "da realidade.",
    "At the Sixth Circle the Domain Expansion is born: a world within the world, where your rules outweigh reality's.")
ST("elemental_plane")["ascension_6"] = ascension_text(
    6, "derrote um Guardião (ou vença a Provação V)", "defeat a Guardian (or win Trial V)",
    "Nível 50 + um Guardião", "Level 50 + a Guardian",
    "Um núcleo de Guardião ainda quente pulsa na sua mão. Os altares sabem o que fazer com ele.",
    "A Guardian core, still warm, throbs in your hand. The altars know what to do with it.")

# ----- Luz e Sombra: portais como estruturas; 7º Círculo -------------------------------------------------------
ST("light_and_shadow")["enter_abyss"] = (
    ST("light_and_shadow")["enter_abyss"][0], ST("light_and_shadow")["enter_abyss"][1],
    ("A seta aponta a Fenda Abissal: um cânion fundo com o portal sombrio adormecido lá embaixo. Com o 6º Círculo, "
     "ative-o oferecendo uma Essência de Sombra. Leve luz — muita.",
     "The arrow points to the Abyssal Chasm: a deep canyon with the dormant shadow portal at the bottom. With the 6th "
     "Circle, activate it by offering a Shadow Essence. Bring light — lots of it."),
    ("Siga a seta: Fenda Abissal", "Follow the arrow: Chasm"))
ST("light_and_shadow")["enter_sanctuary"] = (
    ST("light_and_shadow")["enter_sanctuary"][0], ST("light_and_shadow")["enter_sanctuary"][1],
    ("A seta aponta a Escadaria Celeste, um templo no topo da montanha com o Farol Celestial. Com o 6º Círculo, "
     "ative-o ao meio-dia com uma Essência de Luz para subir a Escada de Luz.",
     "The arrow points to the Celestial Stairway, a mountaintop temple holding the Celestial Beacon. With the 6th Circle, "
     "activate it at noon with a Light Essence to climb the Stairway of Light."),
    ("Siga a seta: Escadaria", "Follow the arrow: Stairway"))
ST("light_and_shadow")["seventh_circle"] = circle_text(
    7, "Sete Círculos. Os nativos já não te chamam de invocado: chamam de \"Senhor\" e abaixam a cabeça.",
    "Seven Circles. The natives no longer call you summoned: they call you \"Lord\" and bow their heads.")
ST("light_and_shadow")["ascension_7"] = ascension_text(
    7, "derrote o Soberano ou o Serafim", "defeat the Sovereign or the Seraph",
    "Nv. 60 + Soberano/Serafim", "Lv 60 + Sovereign/Seraph",
    "Luz de estrela morta ou pena de seis asas: os dois servem de tributo para quem ousou tomá-los.",
    "Dead starlight or a six-winged feather: either serves as tribute for whoever dared to take it.")

# ----- Crepúsculo: a Ruptura, o Arauto... e o que dorme além dele ----------------------------------------------
CH_TEXT["twilight"]["title"] = ("A Ruptura do Crepúsculo", "The Twilight Rupture")
CH_TEXT["twilight"]["outro"] = (
    "O Arauto do Crepúsculo se desfaz em mil fragmentos de luz e sombra... mas a arena não se acalma. O chão racha, o céu "
    "violeta ferve, e de dentro da Fenda algo muito maior abre os olhos. O Arauto nunca foi o fim: ele era o selo.",
    "The Twilight Herald shatters into a thousand fragments of light and shadow... but the arena does not calm down. The "
    "ground cracks, the violet sky boils, and from inside the Rift something far greater opens its eyes. The Herald was "
    "never the end: it was the seal.")
ST("twilight")["ninth_circle"] = circle_text(
    9, "No Nono Círculo, a sua mana deixa de ser um rio e vira um oceano. A Ruptura começa a responder à sua presença.",
    "At the Ninth Circle your mana stops being a river and becomes an ocean. The Rupture begins to answer your presence.")
ST("twilight")["eighth_circle"] = circle_text(
    8, "Oito Círculos. Os elementos já não obedecem: eles conversam com você, e às vezes pedem conselhos.",
    "Eight Circles. The elements no longer obey: they talk to you, and sometimes ask for advice.")
ST("twilight")["ascension_8"] = ascension_text(
    8, "derrote o Soberano E o Serafim", "defeat the Sovereign AND the Seraph",
    "Nível 70 + os dois", "Level 70 + both",
    "Luz e Sombra, finalmente juntas no mesmo tributo. Os sábios dizem que só um invocado conseguiria carregá-lo.",
    "Light and Shadow, finally together in the same tribute. The sages say only a summoned one could carry it.")
ST("twilight")["ascension_9"] = ascension_text(
    9, "vença a Provação VI (ou derrote os 4 Guardiões, ou os 3 Horrores Ocultos)",
    "win Trial VI (or defeat all 4 Guardians, or the 3 Hidden Horrors)",
    "Nível 80 + Provação VI", "Level 80 + Trial VI",
    "O último tributo antes da Ruptura. Pesa como uma montanha — e brilha como a primeira magia que você lançou.",
    "The last tribute before the Rupture. It weighs like a mountain — and glows like the first spell you ever cast.")
ST("twilight")["enter_rift"] = (
    ("Atravesse a Ruptura", "Cross the Rupture"),
    ST("twilight")["enter_rift"][1],
    ("A seta aponta a Ruptura do Crepúsculo, uma ilha de pedra rachada. Com o 9º Círculo e os núcleos do Soberano e do "
     "Serafim, a Fenda se abre. Vá preparado.",
     "The arrow points to the Twilight Rupture, a cracked stone island. With the 9th Circle and the cores of the "
     "Sovereign and the Seraph, the Rift opens. Go prepared."),
    ("Siga a seta: Ruptura", "Follow the arrow: Rupture"))
ST("twilight")["herald"] = (
    ST("twilight")["herald"][0], ST("twilight")["herald"][1],
    ("Leve as suas melhores magias, uma Lenda, um Domínio pronto e cura de sobra. Quando ele expandir o Domínio dele, "
     "responda com o seu. E não guarde tudo: a luta não termina quando ele cair.",
     "Bring your best spells, a Legend, a ready Domain and plenty of healing. When it expands its Domain, answer with "
     "yours. And don't spend everything: the fight does not end when it falls."),
    ("A arena além da Fenda", "The arena beyond the Rift"))

# ----- Capítulos novos ------------------------------------------------------------------------------------------
CH_TEXT["monster_lords"] = {
    "title": ("Senhores dos Monstros", "Lords of the Monsters"),
    "lore": (
        "Com o Rei Goblin caído, as outras tribos sentiram o cheiro do medo — e decidiram que era hora de mostrar quem "
        "manda. Nas planícies, o Lorde Orc reúne a Horda sob um machado do tamanho de uma porta. Num labirinto que muda "
        "de lugar, o Minotauro conta os passos de quem entra. E na floresta antiga, um Ent que já foi guardião dos "
        "druidas apodrece por dentro, espalhando raízes que estrangulam tudo o que vive.",
        "With the Goblin King fallen, the other tribes smelled fear — and decided it was time to show who is in charge. "
        "On the plains, the Orc Lord gathers the Horde under an axe the size of a door. In a maze that shifts, the "
        "Minotaur counts the steps of whoever enters. And in the ancient forest, an Ent who once guarded the druids "
        "rots from within, spreading roots that strangle everything alive."),
    "outro": (
        "O machado do Lorde Orc e o machado duplo do Minotauro agora enfeitam a parede da Guilda. Mas o Quarto Círculo "
        "trouxe algo mais: nas noites sem lua você sente olhares vindos de criptas, torres e altares profanos. Os mestres "
        "das trevas também sabem contar.",
        "The Orc Lord's axe and the Minotaur's labrys now decorate the Guild's wall. But the Fourth Circle brought "
        "something else: on moonless nights you feel gazes from crypts, towers and profane altars. The dark masters can "
        "count too."),
    "intro": (
        "Os senhores dos monstros estão se movendo: o Lorde Orc nas planícies, o Minotauro no labirinto. Vamos atrás "
        "deles — eu mostro o caminho!",
        "The monster lords are on the move: the Orc Lord on the plains, the Minotaur in the labyrinth. Let's go after "
        "them — I'll show you the way!"),
    "steps": {
        "orc_lord": (
            ("Derrote o Lorde Orc", "Defeat the Orc Lord"),
            ("Dizem que o Lorde Orc nunca perdeu um duelo. Também dizem que ele nunca enfrentou alguém de outro mundo.",
             "They say the Orc Lord has never lost a duel. They also say he has never faced someone from another world."),
            ("A seta aponta o Acampamento Orc. Ele carrega o machado, gira e arremessa: quando ele erguer o machado, saia "
             "da frente. O Bestiário mostra as fraquezas.",
             "The arrow points to the Orc Camp. He charges with the axe, spins and throws it: when he raises the axe, get "
             "out of the way. The Bestiary shows his weaknesses."),
            ("Siga a seta: acampamento", "Follow the arrow: camp")),
        "minotaur": (
            ("Derrote o Minotauro", "Defeat the Minotaur"),
            ("No labirinto não se constrói, não se quebra e não se pula muro. Só existe você, os corredores e o bufar dele "
             "cada vez mais perto.",
             "In the labyrinth you cannot build, break or jump the walls. There is only you, the corridors and his "
             "snorting, closer and closer."),
            ("A seta aponta o Labirinto do Minotauro. Quando ele baixar os chifres para investir, desvie para o lado: se "
             "ele bater na parede, fica atordoado. Esse é o momento!",
             "The arrow points to the Minotaur's Labyrinth. When he lowers his horns to charge, sidestep: if he hits a "
             "wall, he is stunned. That's your moment!"),
            ("Siga a seta: labirinto", "Follow the arrow: maze")),
        "corrupted_ent": (
            ("Derrote o Ent Corrompido", "Defeat the Corrupted Ent"),
            ("Ele protegeu o Círculo Druídico por mil anos. Agora a podridão fala pela boca dele.",
             "He protected the Druid Circle for a thousand years. Now rot speaks through his mouth."),
            ("A seta aponta o Círculo Druídico. Fogo dói muito nele, e em cima de terra e grama ele se regenera: afaste-o "
             "da relva. Quando o chão tremer, pule — vêm raízes.",
             "The arrow points to the Druid Circle. Fire hurts him badly, and on dirt and grass he regenerates: lure him "
             "off the grass. When the ground shakes, jump — roots are coming."),
            ("Siga a seta: Círculo", "Follow the arrow: Circle")),
        "ascension_4": ascension_text(
            4, "derrote o Lorde Orc ou o Minotauro", "defeat the Orc Lord or the Minotaur",
            "Nível 30 + um dos senhores", "Level 30 + a monster lord",
            "Um chifre de Minotauro ou uma presa de Lorde Orc: troféus que viram mana nas mãos certas.",
            "A Minotaur horn or an Orc Lord's tusk: trophies that become mana in the right hands."),
        "fourth_circle": circle_text(
            4, "No Quarto Círculo nascem as magias supremas: o slot G passa a guardar o seu golpe final.",
            "At the Fourth Circle ultimate spells are born: the G slot now holds your finishing blow."),
    },
}

CH_TEXT["dark_masters"] = {
    "title": ("Mestres das Trevas", "Masters of Darkness"),
    "lore": (
        "Nem todo poder deste mundo nasceu limpo. Numa cripta esquecida, um Lich guarda a própria alma num filactério e "
        "levanta os mortos como quem acende velas. Num altar profano, um Patrono acorrentado sussurra pactos para quem "
        "passar perto — e cada corrente que se parte o deixa mais livre. Os dois sentiram a sua mana crescer. E os dois "
        "querem um pedaço dela.",
        "Not every power in this world was born clean. In a forgotten crypt, a Lich keeps its own soul in a phylactery "
        "and raises the dead as one lights candles. On a profane altar, a chained Patron whispers pacts to anyone who "
        "walks by — and every chain that breaks sets it freer. Both have felt your mana grow. And both want a piece of it."),
    "outro": (
        "O filactério virou pó e as correntes do Patrono enferrujaram de vez. Mas, ao fechar a cripta, você ouve rugidos "
        "que não são de nenhum monstro conhecido: nos pântanos, desertos, ruínas e picos, os Titãs Selvagens acordaram.",
        "The phylactery crumbled to dust and the Patron's chains rusted for good. But as you close the crypt, you hear "
        "roars that belong to no known monster: in swamps, deserts, ruins and peaks, the Wild Titans have awakened."),
    "intro": (
        "Sente esse frio? Um Lich numa cripta esquecida e um Patrono acorrentado num altar profano. Eles já sabem quem "
        "você é...",
        "Feel that chill? A Lich in a forgotten crypt and a chained Patron on a profane altar. They already know who you "
        "are..."),
    "steps": {
        "lich": (
            ("Derrote o Lich da Cripta", "Defeat the Crypt Lich"),
            ("Ele já morreu uma vez. Não gostou e resolveu não repetir a experiência.",
             "He died once already. He did not like it and decided not to repeat the experience."),
            ("A seta aponta a Cripta Esquecida. Enquanto o Filactério existir, o Lich se recompõe: quebre o Filactério "
             "(Luz dói nele) quando o Lich desabar. Quebre a linha de visão para cortar o dreno.",
             "The arrow points to the Forgotten Crypt. While the Phylactery exists, the Lich reforms: break the "
             "Phylactery (Light hurts it) when the Lich collapses. Break line of sight to cut its drain."),
            ("Siga a seta: cripta", "Follow the arrow: crypt")),
        "imprisoned_patron": (
            ("Derrote o Patrono", "Defeat the Patron"),
            ("Quatro correntes o prendem ao altar. Cada uma que você quebra o deixa mais forte — e mais perto de você.",
             "Four chains bind it to the altar. Each one you break makes it stronger — and closer to you."),
            ("A seta aponta o Altar Profano. Ele só alcança até onde as correntes deixam: lute na borda e fuja da linha de "
             "fogo do Agarrão. Sem correntes, ele fica livre: guarde cura para o fim.",
             "The arrow points to the Profane Altar. It only reaches as far as the chains allow: fight at the edge and "
             "avoid the Grip's line of fire. With no chains left it breaks free: save healing for the end."),
            ("Siga a seta: altar profano", "Follow the arrow: altar")),
        "class_rank_3": (
            ("Rank III da sua classe", "Your class's Rank III"),
            ("Um mestre não se faz só derrubando monstros. O ritual da sua classe tem lições que nenhum chefe ensina.",
             "A master is not made by felling monsters alone. Your class's ritual holds lessons no boss can teach."),
            ("Avance a progressão da sua classe até o rank III. A Rota da Classe, no topo do Diário, diz exatamente o que "
             "falta em cada rank (e o rastreador mostra o próximo passo dela logo abaixo da história).",
             "Advance your class progression to rank III. The Class Route, at the top of the Journal, says exactly what each "
             "rank needs (and the tracker shows its next step right below the story)."),
            ("Veja a Rota da Classe", "See the Class Route")),
        "write_lesson": (
            ("Escreva uma Lição", "Write a Lesson"),
            ("Os glifos da sua classe são só seus — a não ser que você decida ensinar.",
             "Your class's glyphs are yours alone — unless you decide to teach."),
            ("Uma Lição (papel, tinta e essência) grava um glifo de origem que você conhece. Quem a ler aprende o glifo se "
             "tiver o Círculo — e você ganha XP. Ótimo para trocar técnicas com os amigos.",
             "A Lesson (paper, ink and essence) records an origin glyph you know. Whoever reads it learns the glyph if they "
             "have the Circle — and you earn XP. Great for trading techniques with friends."),
            ("Papel + tinta + essência", "Paper + ink + essence")),
        "ascension_5": ascension_text(
            5, "derrote um Mestre das Trevas (Lich, Patrono, Arquimago, Bruxa ou Ent)",
            "defeat a Dark Master (Lich, Patron, Archmage, Witch or Ent)",
            "Nível 40 + um mestre", "Level 40 + a dark master",
            "Poder roubado de quem roubou poder. Os altares não fazem perguntas.",
            "Power stolen from those who stole power. The altars ask no questions."),
        "fifth_circle": circle_text(
            5, "Cinco Círculos: metade do caminho. A mana já não cabe só em você — ela transborda para o mundo ao redor.",
            "Five Circles: halfway there. Your mana no longer fits only inside you — it spills into the world around you."),
    },
}

CH_TEXT["wild_titans"] = {
    "title": ("Titãs Selvagens", "Wild Titans"),
    "lore": (
        "Quatro feras tão antigas quanto os santuários dormiam sob o mundo, e a sua ascensão as acordou. A Hidra do Pântano "
        "regenera cabeças mais rápido do que se pode cortá-las. O Basilisco do Deserto transforma em pedra quem cruza o "
        "olhar dele. O Colosso das Ruínas é um golem do tamanho de uma torre. E o Wyvern Rei dos Picos traz raios nas asas. "
        "Ninguém espera que você vença todos... mas os bardos torcem.",
        "Four beasts as old as the shrines slept beneath the world, and your ascension woke them. The Swamp Hydra regrows "
        "heads faster than they can be cut. The Desert Basilisk turns to stone whoever meets its gaze. The Ruin Colossus "
        "is a golem the size of a tower. And the Wyvern King of the Peaks carries lightning in its wings. Nobody expects "
        "you to beat them all... but the bards are cheering."),
    "outro": (
        "Os Titãs caídos viram lenda nas tavernas. Mas um viajante assustado conta outra história: numa vila abandonada, "
        "uma canção apaga a memória de quem a ouve. E ninguém lembra de ter visto o que canta.",
        "The fallen Titans become tavern legends. But a frightened traveler tells another story: in an abandoned village, "
        "a song erases the memory of whoever hears it. And nobody remembers seeing what sings."),
    "intro": (
        "A terra está tremendo: quatro Titãs acordaram. Você não precisa vencer todos — dois já bastam para seguirmos. "
        "Escolha o seu caminho!",
        "The ground is shaking: four Titans have awakened. You don't need to beat them all — two are enough for us to "
        "move on. Pick your path!"),
    "steps": {
        "two_titans": (
            ("Derrote 2 Titãs", "Defeat 2 Titans"),
            ("Duas feras primordiais caídas pelas suas mãos. O mundo começa a te levar a sério.",
             "Two primordial beasts fallen by your hand. The world begins to take you seriously."),
            ("A seta aponta o covil mais próximo de um Titã que você ainda não venceu: pântano (Hidra), deserto "
             "(Basilisco), ruínas (Colosso) ou picos (Wyvern). Vá em grupo se puder.",
             "The arrow points to the nearest lair of a Titan you have not beaten yet: swamp (Hydra), desert (Basilisk), "
             "ruins (Colossus) or peaks (Wyvern). Go as a group if you can."),
            ("Siga a seta: covil", "Follow the arrow: lair")),
        "swamp_hydra": (
            ("Derrote a Hidra do Pântano", "Defeat the Swamp Hydra"),
            ("Corte uma cabeça e nascem duas. A não ser que o toco queime.",
             "Cut off one head and two grow back. Unless the stump burns."),
            ("O covil fica num pântano. Cabeças cortadas voltam a crescer: queime o toco com Fogo para impedir. O veneno "
             "dela se acumula — leve leite ou cura.",
             "The lair is in a swamp. Severed heads grow back: burn the stump with Fire to stop it. Her poison stacks "
             "up — bring milk or healing."),
            ("Covil no pântano", "Lair in the swamp")),
        "desert_basilisk": (
            ("Derrote o Basilisco", "Defeat the Basilisk"),
            ("No túmulo sob as dunas, estátuas de aventureiros ainda seguram as espadas erguidas.",
             "In the tomb beneath the dunes, statues of adventurers still hold their swords raised."),
            ("O covil é um túmulo no deserto. Quando os olhos dele brilharem, vire o rosto: o olhar Petrifica. Ele também "
             "se enterra e salta da areia.",
             "The lair is a tomb in the desert. When its eyes glow, look away: its gaze Petrifies. It also burrows and "
             "bursts out of the sand."),
            ("Túmulo no deserto", "Tomb in the desert")),
        "ruin_colossus": (
            ("Derrote o Colosso", "Defeat the Colossus"),
            ("Um golem construído para proteger uma cidade que não existe mais. Ele ainda não sabe disso.",
             "A golem built to protect a city that no longer exists. It does not know that yet."),
            ("O covil são ruínas na planície ou floresta. Os socos dele abrem crateras de verdade: saia da marca no chão e "
             "ataque as costas depois de cada golpe.",
             "The lair is a ruin on the plains or in a forest. Its punches open real craters: leave the mark on the ground "
             "and hit its back after every blow."),
            ("Ruínas na planície", "Ruins on the plains")),
        "wyvern_king": (
            ("Derrote o Wyvern Rei", "Defeat the Wyvern King"),
            ("Ele reina sobre os picos e as tempestades. Os raios caem onde ele aponta.",
             "It reigns over the peaks and the storms. Lightning falls where it points."),
            ("O covil fica no alto dos picos. Ele voa e investe: acerte-o quando pousar. Molhado, você vira para-raios — "
             "fique seco!",
             "The lair is high in the peaks. It flies and dives: hit it when it lands. If you are Wet you become a "
             "lightning rod — stay dry!"),
            ("Pico das montanhas", "Mountain peak")),
    },
}
_move("elemental_plane", "wild_titans", "trial_four")

CH_TEXT["hidden_horrors"] = {
    "title": ("Horrores Ocultos", "Hidden Horrors"),
    "lore": (
        "Há monstros que rugem e monstros que esperam. Sob uma vila abandonada, a Falsa Hidra canta uma canção que apaga a "
        "memória — e as cabeças dela só existem quando ninguém olha. Num cofre esquecido, o Rei Mímico transformou cada "
        "baú num estômago. E nos desfiladeiros, a Manticora caça com uma cauda de espinhos e um rugido que gela a coragem.",
        "There are monsters that roar and monsters that wait. Beneath an abandoned village, the False Hydra sings a song "
        "that erases memory — and its heads only exist when nobody looks. In a forgotten vault, the Mimic King turned "
        "every chest into a stomach. And in the canyons, the Manticore hunts with a tail of spikes and a roar that "
        "freezes courage."),
    "outro": (
        "A canção se calou e você ainda se lembra de tudo — talvez de coisas demais. Com o mundo mais seguro, o caminho "
        "fica livre: nas ruínas de pedra com quatro obeliscos, o Portão Elemental começou a zumbir.",
        "The song fell silent and you still remember everything — maybe too much. With the world safer, the path is clear: "
        "in the stone ruins with four obelisks, the Elemental Gate has begun to hum."),
    "intro": (
        "Isto é diferente: chefes que se escondem. Uma Hidra que apaga memórias, uma Manticora nos desfiladeiros... "
        "Fique perto de mim e olhe bem por onde anda.",
        "This is different: bosses that hide. A Hydra that erases memories, a Manticore in the canyons... Stay close to "
        "me and watch where you step."),
    "steps": {
        "false_hydra": (
            ("Derrote a Falsa Hidra", "Defeat the False Hydra"),
            ("Os moradores da vila esqueceram que um dia moraram lá. A Hidra não esqueceu deles.",
             "The villagers forgot they ever lived there. The Hydra did not forget them."),
            ("A seta aponta a vila abandonada. As cabeças só aparecem quando você não olha; o canto esconde o HUD, embaralha "
             "a barra de itens e a cura. Ache o corpo enterrado!",
             "The arrow points to the abandoned village. The heads only appear when you are not looking; the song hides "
             "the HUD, scrambles your hotbar and heals it. Find the buried body!"),
            ("Siga a seta: vila", "Follow the arrow: village")),
        "manticore": (
            ("Derrote a Manticora", "Defeat the Manticore"),
            ("Corpo de leão, asas de morcego e uma cauda que dispara espinhos como uma besta de cerco.",
             "A lion's body, bat wings and a tail that fires spikes like a siege crossbow."),
            ("A seta aponta a toca da Manticora. A cauda abre um leque de espinhos: fique de lado, não na frente. O rugido "
             "amedronta — mantenha distância quando ela inflar o peito.",
             "The arrow points to the Manticore's den. Its tail fans out spikes: stay to the side, not in front. Its roar "
             "frightens — keep your distance when it puffs its chest."),
            ("Siga a seta: toca", "Follow the arrow: den")),
        "mimic_king": (
            ("Derrote o Rei Mímico", "Defeat the Mimic King"),
            ("Um cofre cheio de tesouros. Bom demais para ser verdade — e é exatamente isso.",
             "A vault full of treasure. Too good to be true — and that is exactly the point."),
            ("A seta aponta o Cofre. Os baús viram mímicos: não abra nada antes de limpar a sala. Quando a língua do rei "
             "brilhar, saia da linha: ele engole e cospe jogadores.",
             "The arrow points to the Vault. The chests turn into mimics: open nothing before clearing the room. When the "
             "king's tongue glows, leave its line: it swallows and spits out players."),
            ("Siga a seta: cofre", "Follow the arrow: vault")),
        "element_level_12": (
            ("Elemento no nível 12", "Element at level 12"),
            ("Nível 12 num elemento: a porta da Provação do Mestre. Os Guardiões do Plano Elemental só respeitam mestres.",
             "Level 12 in an element: the gate to the Master's Trial. The Elemental Plane's Guardians only respect masters."),
            ("Use as magias e técnicas de um elemento e vença inimigos com elas. A aba Caminhos (B) mostra o nível de cada "
             "elemento.",
             "Use an element's spells and techniques and defeat enemies with them. The Paths tab (B) shows each element's "
             "level."),
            ("Use magias do elemento", "Use the element's spells")),
    },
}

CH_TEXT["primordial"] = {
    "title": ("O Dragão Primordial", "The Primordial Dragon"),
    "lore": (
        "Antes dos santuários, antes dos Guardiões e antes até das duas luas, existia um dragão que era todos os elementos "
        "ao mesmo tempo. Os seis sábios não conseguiram matá-lo: apenas selá-lo além do Crepúsculo, com o Arauto como "
        "carcereiro. Ao derrotar o Arauto você quebrou o selo. Agora o Dragão Primordial está acordado — e ele lembra de "
        "cada um que o prendeu.",
        "Before the shrines, before the Guardians, before even the two moons, there was a dragon that was every element at "
        "once. The six sages could not kill it: only seal it beyond the Twilight, with the Herald as its jailer. By "
        "defeating the Herald you broke the seal. Now the Primordial Dragon is awake — and it remembers everyone who "
        "imprisoned it."),
    "outro": (
        "O Dragão Primordial cai, e com ele as cinco tempestades — fogo, gelo, pedra, raio e crepúsculo — se desfazem "
        "num silêncio que o mundo nunca tinha ouvido.",
        "The Primordial Dragon falls, and with it the five storms — fire, ice, stone, lightning and twilight — dissolve "
        "into a silence the world had never heard."),
    "intro": (
        "É ele... o Dragão Primordial! Fogo, Água, Terra, Vento e o próprio Crepúsculo: cinco fases. Não desista. Esta é a "
        "luta para a qual você foi invocado!",
        "It's him... the Primordial Dragon! Fire, Water, Earth, Wind and the Twilight itself: five phases. Don't give up. "
        "This is the fight you were summoned for!"),
    "epilogue": [
        ("Por um instante você ouve o coração do mundo: seis elementos batendo no mesmo ritmo, pela primeira vez desde a "
         "Grande Ruptura.",
         "For an instant you hear the world's heart: six elements beating to the same rhythm, for the first time since the "
         "Great Rupture."),
        ("A Fenda se fecha. As duas luas brilham juntas, e nos pântanos, desertos e picos os monstros dormem tranquilos.",
         "The Rift closes. The two moons shine together, and in swamps, deserts and peaks the monsters sleep in peace."),
        ("Na Guilda, a recepcionista atualiza o seu cartão com as mãos tremendo. Não existe rank acima de S... mas ela "
         "escreve um mesmo assim.",
         "At the Guild, the receptionist updates your card with trembling hands. There is no rank above S... but she "
         "writes one anyway."),
        ("Nos três reinos, os bardos já cantam sobre o forasteiro que derrubou chefes, titãs, guardiões e, por fim, o "
         "próprio Dragão.",
         "In the three kingdoms, bards already sing of the outsider who felled bosses, titans, guardians and, at last, the "
         "Dragon itself."),
        ("Do coração do Dragão sobra uma brasa morna, que pulsa nas cores de todos os elementos — como se ainda esperasse "
         "alguma coisa.",
         "From the Dragon's heart remains a warm ember, pulsing in the colors of every element — as if it were still "
         "waiting for something."),
        ("O Espírito Guia surge uma última vez, mais brilhante do que nunca: \"Você poderia voltar para casa agora... mas "
         "este mundo já é a sua casa, não é?\"",
         "The Guide Spirit appears one last time, brighter than ever: \"You could go home now... but this world is "
         "already your home, isn't it?\""),
        ("A sua lenda está escrita. O que vem depois, só você decide.",
         "Your legend is written. What comes next is yours alone to decide."),
    ],
    "steps": {
        "primordial_dragon": (
            ("Vença o Dragão Primordial", "Defeat the Dragon"),
            ("Vinte blocos de escamas, cinco tempestades e mil anos de raiva. O último chefe deste mundo.",
             "Twenty blocks of scales, five storms and a thousand years of rage. This world's final boss."),
            ("Ele surge quando o Arauto cai, na arena da Fenda (o Arauto volta um dia depois de vencido). Cada fase é um "
             "elemento: saia das marcas no chão, contrarie os golpes grandes e responda à ultimate final com o seu Domínio.",
             "It emerges when the Herald falls, in the Rift arena (the Herald returns a day after being defeated). Each phase "
             "is an element: leave the marks on the ground, counter the big blows and answer the final ultimate with your Domain."),
            ("Arena da Fenda", "The Rift arena")),
        "ascension_10": ascension_text(
            10, "derrote o Arauto ou o Dragão", "defeat the Herald or the Dragon",
            "Nível 100 + Arauto/Dragão", "Level 100 + Herald/Dragon",
            "O Décimo Círculo. Nenhum mortal o rompeu; talvez porque nenhum mortal tenha vencido um dragão primordial.",
            "The Tenth Circle. No mortal has ever broken it; perhaps because no mortal ever defeated a primordial dragon."),
        "tenth_circle": circle_text(
            10, "Dez Círculos. A mana não corre mais em você: você é que corre dentro dela.",
            "Ten Circles. Mana no longer runs through you: you run within it."),
    },
}

# Falas curtas do Espírito Guia ao abrir cada capítulo (caixa de diálogo; a crônica longa fica no Diário)
INTROS = {
    "awakening": ("Bem-vindo a este mundo! Primeiro, o básico: magias, um elemento e alguns slimes. Eu fico por perto.",
                  "Welcome to this world! First, the basics: spells, an element and a few slimes. I'll stay close."),
    "guild_and_trials": ("Hora de virar aventureiro de verdade! Vamos a uma cidade: lá ficam a Guilda, uma Pedra de "
                         "Teleporte e o Altar de Ascensão.",
                         "Time to become a real adventurer! Let's head to a city: the Guild, a Waystone and the Ascension "
                         "Altar are all there."),
    "six_shrines": ("Seis santuários guardam a essência do mundo, e o Rei Goblin anda roubando por aí. Vamos cuidar dos "
                    "dois!",
                    "Six shrines guard the world's essence, and the Goblin King is out stealing. Let's deal with both!"),
    "programmed_magic": ("Magias podem se ligar umas às outras como frases! E na Torre do Mago, um Arquimago Renegado "
                         "não quer que você aprenda isso.",
                         "Spells can link to each other like sentences! And in the Mage Tower, a Renegade Archmage does "
                         "not want you to learn that."),
    "elemental_plane": ("O Portão Elemental acordou! Nas ruínas com quatro obeliscos, o caminho para os Guardiões está "
                        "aberto. Vamos?",
                        "The Elemental Gate has awakened! In the ruins with four obelisks, the path to the Guardians is "
                        "open. Shall we?"),
    "light_and_shadow": ("Um cânion sem fundo e um templo acima das nuvens: o Abismo e o Santuário Celestial esperam por "
                         "você.",
                         "A bottomless canyon and a temple above the clouds: the Abyss and the Celestial Sanctuary await "
                         "you."),
    "twilight": ("A Ruptura do Crepúsculo... é para lá que tudo leva. Suba os últimos Círculos e enfrente o Arauto.",
                 "The Twilight Rupture... everything leads there. Climb the last Circles and face the Herald."),
}
for _cid, _txt in INTROS.items():
    CH_TEXT[_cid]["intro"] = _txt



# =====================================================================================
#  v1.0: Mundo Oco, o Dragão para 6 jogadores, o passo secreto 150→200 e as rotas de classe
# =====================================================================================
CH_TEXT["hollow_world"] = {
    "title": ("O Mundo Oco", "The Hollow World"),
    "lore": (
        "Há um rasgo no céu que só abre à noite. Do outro lado, um deserto de areia branca sob uma lua que nunca se põe, "
        "árvores de quartzo e ossadas do tamanho de torres. Ali vivem os Vazios — almas que perderam o coração e usam "
        "máscaras de osso no lugar do rosto — e, no Palácio da Noite Eterna, os dois que governam a fome deles: o Rei "
        "Vazio e a Imperatriz das Areias. Antes de encarar o Dragão, o selo pede que você prove que não teme o vazio.",
        "There is a tear in the sky that only opens at night. Beyond it, a desert of white sand under a moon that never "
        "sets, quartz trees and bones the size of towers. There live the Hollows — souls who lost their hearts and wear "
        "bone masks instead of faces — and, in the Palace of Eternal Night, the two who rule their hunger: the Hollow King "
        "and the Sand Empress. Before facing the Dragon, the seal asks you to prove you do not fear the void."),
    "outro": (
        "O Rei Vazio cai, e a máscara dele se parte em mil pedaços que voam para a lua. Pela primeira vez em eras, o "
        "deserto branco fica em silêncio. Na Fenda, alguma coisa enorme abre um olho.",
        "The Hollow King falls, and his mask shatters into a thousand pieces that fly toward the moon. For the first time "
        "in ages, the white desert falls silent. In the Rift, something enormous opens an eye."),
    "intro": (
        "Sente esse vazio no peito? É a Garganta chamando. Um rasgo no céu, um deserto branco, e dois soberanos num "
        "palácio que nunca vê o sol. Vamos?",
        "Feel that emptiness in your chest? It's the Garganta calling. A tear in the sky, a white desert, and two rulers in "
        "a palace that never sees the sun. Shall we?"),
    "steps": {
        "garganta": (
            ("Atravesse a Garganta", "Cross the Garganta"),
            ("O altar de osso no meio do nada. À noite, com a oferenda certa, o céu rasga como papel preto.",
             "A bone altar in the middle of nowhere. At night, with the right offering, the sky tears like black paper."),
            ("A seta aponta a Fenda da Garganta. À noite, com Essência das Sombras ou Fragmento de Máscara na mão, use o "
             "altar: o portal abre por 30 s. Shinigami e Quincy têm atalhos (Senkaimon, Ponte de Reishi).",
             "The arrow points to the Garganta Rift. At night, holding a Shadow Essence or Mask Fragment, use the altar: "
             "the portal opens for 30 s. Shinigami and Quincy have shortcuts (Senkaimon, Reishi Bridge)."),
            ("Siga a seta: Garganta", "Follow the arrow: Garganta")),
        "hollow_hunt": (
            ("Derrote 20 Vazios", "Defeat 20 Hollows"),
            ("Rastejantes, Brutamontes e Alados: rápidos, famintos e sempre em bando.",
             "Crawlers, Brutes and Wraiths: fast, hungry and always in packs."),
            ("Os Vazios rondam as dunas e as ruínas do Mundo Oco. Fragmentos de Máscara caem deles (servem de oferenda na "
             "Garganta). Luz dói neles.",
             "Hollows roam the dunes and ruins of the Hollow World. Mask Fragments drop from them (they work as the "
             "Garganta's offering). Light hurts them."),
            ("Dunas e ruínas", "Dunes and ruins")),
        "menos": (
            ("Derrube um Menos", "Topple a Menos"),
            ("Um gigante de manto negro e nariz comprido, da altura de uma torre. Ele não anda: ele acontece.",
             "A black-robed giant with a long nose, as tall as a tower. It does not walk: it happens."),
            ("Os Menos Gigantes aparecem no deserto aberto. Saia da frente do raio que ele carrega na boca e mire na máscara. "
             "Vale um Núcleo de Menos.",
             "Menos Giants appear in the open desert. Get out of the way of the beam it charges in its mouth and aim for the "
             "mask. Worth a Menos Core."),
            ("Deserto aberto", "Open desert")),
        "arrancar": (
            ("Vença 3 Arrancar", "Defeat 3 Arrancar"),
            ("Vazios que arrancaram a própria máscara e ganharam forma humana — e nome, e orgulho.",
             "Hollows who tore off their own masks and gained a human form — and a name, and pride."),
            ("Os Arrancar de elite guardam o caminho do palácio. Usam passos rápidos e raios: lute com o grupo e guarde o "
             "contra-ataque para depois do avanço deles.",
             "Elite Arrancar guard the palace road. They use quick steps and beams: fight as a party and save your "
             "counter for after their dash."),
            ("Caminho do palácio", "Palace road")),
        "sand_empress": (
            ("Derrote a Imperatriz", "Defeat the Empress"),
            ("A areia obedece a ela como a maré obedece à lua. Cada grão é uma lâmina.",
             "The sand obeys her as the tide obeys the moon. Every grain is a blade."),
            ("No Palácio da Noite Eterna (centro do Mundo Oco; a seta aponta lá dentro). Ela afunda e reaparece: fique "
             "longe dos redemoinhos. A arena é protegida durante a luta. Leve o grupo.",
             "In the Palace of Eternal Night (center of the Hollow World; the arrow points inside). She sinks and "
             "resurfaces: stay away from the whirlpools. The arena is protected during the fight. Bring your party."),
            ("Palácio da Noite Eterna", "Palace of Eternal Night")),
        "hollow_king": (
            ("Derrote o Rei Vazio", "Defeat the Hollow King"),
            ("Tudo o que ele toca envelhece. Coroa de osso, machado de cinza, e uma fome do tamanho do deserto.",
             "Everything he touches ages. A bone crown, an ash axe, and a hunger the size of the desert."),
            ("No trono do palácio. O toque dele decai armaduras e vida: não fique colado. Quando ele cair de joelhos, a "
             "segunda fase começa — e ele volta um dia depois de vencido.",
             "On the palace throne. His touch decays armor and health: don't hug him. When he drops to his knees the "
             "second phase begins — and he returns a day after being defeated."),
            ("Trono do palácio", "Palace throne")),
        "fey_realm": (
            ("Entre no Reino das Fadas", "Enter the Fey Realm"),
            ("Colinas num crepúsculo que nunca acaba, cogumelos que brilham e risadas que vêm de lugar nenhum.",
             "Hills in a twilight that never ends, glowing mushrooms and laughter coming from nowhere."),
            ("A seta aponta o Círculo das Fadas. À noite, com Essência da Luz, Pó de Fada ou Pétala-da-Lua na mão, use a "
             "pedra do círculo: o anel de pétalas abre por 30 s.",
             "The arrow points to the Fairy Ring. At night, holding a Light Essence, Fey Dust or Moonpetal, use the ring "
             "stone: the petal ring opens for 30 s."),
            ("Siga a seta: Círculo", "Follow the arrow: Ring")),
        "thorn_queen": (
            ("Derrote a Rainha", "Defeat the Thorn Queen"),
            ("Linda, cruel e cercada de espinhos que dançam ao som da risada dela.",
             "Beautiful, cruel and surrounded by thorns that dance to the sound of her laughter."),
            ("Na Corte das Fadas, no centro do reino. Fadas Travessas e Cães de Espinhos protegem a Rainha: limpe-os antes. "
             "As Asas da Rainha são o prêmio.",
             "At the Fey Court, in the center of the realm. Mischievous Fairies and Bramble Hounds protect the Queen: clear "
             "them first. The Queen's Wings are the prize."),
            ("Corte das Fadas", "Fey Court")),
    },
}

CH_TEXT["beyond_limit"] = {
    "title": ("Além do Limite", "Beyond the Limit"),
    "lore": (
        "A brasa do coração do Dragão não esfriou. Ela pulsa no seu peito, e de repente você entende: o nível 150 nunca "
        "foi o fim — era só a última porta que alguém tinha visto. Atrás dela há mais cinquenta degraus, e ninguém neste "
        "mundo, nem os seis sábios, subiu algum deles.",
        "The ember from the Dragon's heart has not cooled. It beats in your chest, and suddenly you understand: level 150 "
        "was never the end — just the last door anyone had seen. Behind it are fifty more steps, and no one in this "
        "world, not even the six sages, ever climbed one."),
    "outro": (
        "Nível 200. A mana não corre mais em você nem você nela: vocês são a mesma coisa. Em algum lugar além das "
        "estrelas, uma voz antiga diz o seu nome com respeito. O mundo agora conta a sua história como conta a dos "
        "elementos.",
        "Level 200. Mana no longer runs through you, nor you through it: you are the same thing. Somewhere beyond the "
        "stars, an ancient voice speaks your name with respect. The world now tells your story the way it tells the "
        "elements'."),
    "intro": (
        "Você ainda sente a brasa do Dragão, não sente? O limite se rompeu: o nível 150 não é mais o teto. Há cinquenta "
        "degraus que ninguém subiu. Vamos ver até onde você vai.",
        "You still feel the Dragon's ember, don't you? The limit has broken: level 150 is no longer the ceiling. There "
        "are fifty steps nobody has climbed. Let's see how far you go."),
    "steps": {
        "level_160": (
            ("Alcance o nível 160", "Reach level 160"),
            ("O primeiro degrau acima do impossível. O ar fica mais leve; a mana, mais densa.",
             "The first step above the impossible. The air grows lighter; the mana, denser."),
            ("Depois do Dragão, o teto do Décimo Círculo vira 200 e o XP guardado no 150 volta. Cada nível acima do 100 "
             "aumenta o Limite de Mana, os glifos por magia e o Poder.",
             "After the Dragon, the Tenth Circle's ceiling becomes 200 and the XP banked at 150 comes back. Every level "
             "above 100 raises your Mana Limit, glyphs per spell and Power."),
            ("Acima do 150", "Above 150")),
        "dragon_again": (
            ("Vença o Dragão de novo", "Defeat the Dragon again"),
            ("Ele volta. Sempre volta. E agora outros heróis precisam de alguém que já o derrubou.",
             "It comes back. It always does. And now other heroes need someone who has already brought it down."),
            ("O Dragão Primordial renasce na arena da Fenda. Ajude outro grupo (até 6 jogadores) a vencê-lo — quem já "
             "conhece as contramágicas salva vidas.",
             "The Primordial Dragon is reborn in the Rift arena. Help another party (up to 6 players) defeat it — someone "
             "who already knows the counterspells saves lives."),
            ("Arena da Fenda", "The Rift arena")),
        "class_master": (
            ("Rank V da sua classe", "Your class's Rank V"),
            ("Um lendário sem domínio da própria classe é só um sortudo.",
             "A legend without mastery of their own class is just lucky."),
            ("Complete a Rota da Classe até o rank V — as formas G e as Lendas da classe brilham com os níveis acima de "
             "150.",
             "Complete the Class Route up to rank V — the class's G forms and Legends shine with levels above 150."),
            ("Rota da Classe: rank V", "Class Route: rank V")),
        "level_180": (
            ("Alcance o nível 180", "Reach level 180"),
            ("Os bardos param de cantar quando você entra na taverna. Não sabem mais que rimas usar.",
             "The bards stop singing when you walk into the tavern. They no longer know which rhymes to use."),
            ("Os chefes que voltam (Arauto, Dragão, Rei Vazio, guardiões) e as Provações altas dão muito XP. Treine o "
             "recurso todo dia: o Limite sobe junto.",
             "Returning bosses (Herald, Dragon, Hollow King, guardians) and high Trials give a lot of XP. Train your "
             "resource every day: the Limit rises with it."),
            ("Chefes e Provações altas", "Bosses and high Trials")),
        "hollow_again": (
            ("O Rei Vazio, outra vez", "The Hollow King, again"),
            ("Coroas de osso não ficam quebradas por muito tempo.", "Bone crowns don't stay broken for long."),
            ("O Rei Vazio volta ao trono um dia depois de vencido. Vença-o de novo — de preferência ensinando o caminho a "
             "alguém mais novo.",
             "The Hollow King returns to his throne a day after being defeated. Beat him again — preferably showing the "
             "way to someone newer."),
            ("Trono do palácio", "Palace throne")),
        "level_200": (
            ("Alcance o nível 200", "Reach level 200"),
            ("O último degrau. Não há placa, não há porta. Só você e o silêncio de quem chegou.",
             "The last step. There is no sign, no door. Only you and the silence of one who has arrived."),
            ("O máximo absoluto. Tudo o que vem depois é seu: cidades para governar, facções, amigos para ensinar e magias "
             "que ninguém ainda imaginou.",
             "The absolute maximum. Everything after this is yours: cities to rule, factions, friends to teach and spells "
             "no one has imagined yet."),
            ("O último degrau", "The last step")),
    },
}

# O Dragão da v1.0: para 6 jogadores no nível máximo, com contramágica e "coisas piores que a morte"
ST("primordial")["primordial_dragon"] = (
    ("Vença o Dragão Primordial", "Defeat the Dragon"),
    ("Vinte blocos de escamas, cinco tempestades e mil anos de raiva. O último chefe deste mundo — e ele não luta contra "
     "um herói: luta contra um exército.",
     "Twenty blocks of scales, five storms and a thousand years of rage. This world's final boss — and it does not fight a "
     "hero: it fights an army."),
    ("Feito para até 6 jogadores no nível máximo. Ele não mata na hora: petrifica, acorrenta, drena vida, mana e nível, "
     "troca de lugar e apaga loadouts. Os ultimates dele pedem CONTRAMÁGICA. Surge quando o Arauto cai, na Fenda.",
     "Made for up to 6 max-level players. It doesn't kill outright: it petrifies, chains, drains health, mana and level, "
     "swaps places and erases loadouts. Its ultimates demand a COUNTERSPELL. It rises when the Herald falls, in the Rift."),
    ("Arena da Fenda · grupo 6", "Rift arena · party of 6"))
CH_TEXT["primordial"]["intro"] = (
    "É ele... o Dragão Primordial! Cinco fases, nenhuma morte rápida: estátua, correntes, dreno. Junte o seu grupo e "
    "guardem contramágica para os ultimates. É para isso que você foi invocado!",
    "It's him... the Primordial Dragon! Five phases, no quick deaths: statues, chains, drains. Gather your party and save "
    "counterspells for the ultimates. This is what you were summoned for!")
CH_TEXT["primordial"]["epilogue"][5] = (
    "O Espírito Guia surge, mais brilhante do que nunca: \"Você poderia voltar para casa agora... mas este mundo já é a "
    "sua casa, não é?\"",
    "The Guide Spirit appears, brighter than ever: \"You could go home now... but this world is already your home, "
    "isn't it?\"")
CH_TEXT["primordial"]["epilogue"][6] = (
    "A sua lenda está escrita. Ou quase: a brasa do Dragão ainda pulsa no seu peito, como uma porta que acabou de abrir.",
    "Your legend is written. Or almost: the Dragon's ember still beats in your chest, like a door that has just opened.")

# Rotas de classe (15 capítulos paralelos — tools/gen/journal_classes.py)
CLASS_CHAPTERS, CLASS_TEXT = journal_classes.build(step, items)
CH_TEXT.update(CLASS_TEXT)

# =====================================================================================
#  BESTIARIO: descricoes (pt, en) de todas as criaturas/chefes do catalogo
# =====================================================================================
BESTIARY = {
    # --- creatures_a ---
    "slime": ("Uma gota de gelatina azul, curiosa e inofensiva — até levar o primeiro golpe. Muitos invocados juram que a primeira amizade neste mundo foi um slime.",
              "A blob of blue jelly, curious and harmless — until it takes the first hit. Many summoned heroes swear their first friend in this world was a slime."),
    "fire_slime": ("Slime que absorveu brasas vulcânicas. Queima quem toca e deixa chamas ao pousar; a água e a chuva o fazem chiar de dor.",
                   "A slime that absorbed volcanic embers. It burns whatever it touches and leaves flames where it lands; water and rain make it hiss in pain."),
    "water_slime": ("Gelatina translúcida que encharca quem toca e se regenera dentro d'água. Magia de Água só a deixa mais forte.",
                    "Translucent jelly that soaks whoever it touches and regenerates in water. Water magic only makes it stronger."),
    "stone_slime": ("Slime com pedrinhas no corpo: aguenta metade do dano físico e empurra com força surpreendente.",
                    "A slime full of pebbles: it shrugs off half of physical damage and shoves with surprising strength."),
    "wind_slime": ("Leve como uma bolha, salta alto e longe, levado por correntes de ar que só ele sente.",
                   "Light as a bubble, it leaps high and far, carried by air currents only it can feel."),
    "poison_jelly": ("Gelatina roxa e venenosa dos pântanos. Não toque, não pise, não prove.",
                     "A purple, venomous jelly of the swamps. Do not touch, do not step on, do not taste."),
    "king_slime": ("O monarca gelatinoso. Salta para esmagar inimigos com uma onda pegajosa e, a cada golpe sério, se divide em súditos menores.",
                   "The gelatinous monarch. It leaps to crush foes with a sticky shockwave and, with every serious wound, splits into smaller subjects."),
    "goblin": ("Pequeno, verde e covarde sozinho — perigoso em bando. Rouba o que encontra no chão e foge quando a coisa aperta.",
               "Small, green and cowardly alone — dangerous in a pack. It steals whatever lies on the ground and flees when things get rough."),
    "goblin_archer": ("Goblin que aprendeu que flechas doem mais de longe. Mantém distância e recua se você se aproximar.",
                      "A goblin who learned that arrows hurt more from afar. It keeps its distance and backs away if you close in."),
    "goblin_shaman": ("Conjura magias de glifos de verdade e cura o bando. Derrube o xamã primeiro e o resto desmorona.",
                      "Casts real glyph spells and heals its pack. Take the shaman down first and the rest falls apart."),
    "goblin_wolf_rider": ("Um goblin montado num lobo enorme: rápido, salta sobre a presa e ataca com mordida e lança ao mesmo tempo.",
                          "A goblin riding a huge wolf: fast, it pounces on prey and strikes with bite and spear at once."),
    "hobgoblin": ("O sargento do bando: maior, de armadura e escudo. Bloqueia golpes de frente — machados quebram a sua guarda.",
                  "The pack's sergeant: bigger, armored and shielded. It blocks blows from the front — axes break its guard."),
    "goblin_king": ("Senhor da fortaleza goblin. Machado enorme, saltos esmagadores e uma corneta que traz o reino inteiro para a batalha.",
                    "Lord of the goblin fortress. A huge axe, crushing leaps and a horn that brings the whole kingdom into battle."),
    # --- creatures_b ---
    "kobold": ("Pequeno reptiliano minerador das cavernas. Cava minérios, guarda tudo numa bolsa e chama os parentes quando é atacado.",
               "A small reptilian miner of the caves. It digs ores, keeps everything in a pouch and calls its kin when attacked."),
    "orc": ("Guerreiro forte de cutelo pesado, anda em hordas e ataca jogadores, aldeões e golens sem distinção.",
            "A strong warrior with a heavy cleaver; it roams in hordes and attacks players, villagers and golems alike."),
    "orc_berserker": ("Com pouca vida, entra em fúria: ruge, solta vapor vermelho e fica muito mais rápido e forte. Termine a luta rápido.",
                      "When badly hurt it goes berserk: roars, vents red steam and becomes much faster and stronger. End the fight quickly."),
    "orc_lord": ("Líder do acampamento orc. Golpes no chão abrem ondas de choque e o seu grito de guerra fortalece a horda inteira.",
                 "Leader of the orc camp. Its ground slams send out shockwaves and its war cry empowers the whole horde."),
    "ogre": ("Enorme e lento. Ergue o porrete com as duas mãos antes de esmagar o chão — quando vir o aviso, saia da frente.",
             "Huge and slow. It raises its club with both hands before smashing the ground — when you see the warning, get out of the way."),
    "troll": ("Regenera assustadoramente rápido... a menos que esteja queimando. Fogo causa o dobro de dano e trava a regeneração.",
              "Regenerates frighteningly fast... unless it is burning. Fire deals double damage and stops the regeneration."),
    "lizardman": ("Guerreiro dos pântanos com lança de longo alcance. Nada muito bem e respira debaixo d'água.",
                  "A swamp warrior with a long-reaching spear. It swims very well and breathes underwater."),
    "minotaur": ("Guardião do labirinto. Investidas que atordoam e um machado duplo em arco — se ele bater numa parede, é a sua chance.",
                 "Guardian of the labyrinth. Stunning charges and a sweeping double axe — if it slams into a wall, that is your chance."),
    "werewolf": ("Só aparece em noites de lua cheia. Muito rápido, uiva chamando os lobos da região e some ao amanhecer.",
                 "Appears only on full-moon nights. Very fast, it howls to call the local wolves and vanishes at dawn."),
    "cultist": ("Encapuzado que conjura magias de glifos de Sombra, Fogo ou Arcano. Interrompa a conjuração batendo antes que termine.",
                "A hooded caster of Shadow, Fire or Arcane glyph spells. Interrupt the casting by striking before it finishes."),
    "wandering_mage": ("Comerciante ambulante de magia: vende Pergaminhos de Glifo, cristais e essências. Defende-se bem e some depois de um tempo.",
                       "A traveling magic merchant: sells Glyph Scrolls, crystals and essences. It defends itself well and leaves after a while."),
    # --- creatures_c ---
    "dire_wolf": ("Lobo gigante das taigas. Neutro, mas domável com carne — e, com sela, vira uma montaria de saltos impressionantes.",
                  "A giant taiga wolf. Neutral, but tameable with meat — and, saddled, it becomes a mount with impressive leaps."),
    "harpy": ("Circula a presa no alto das montanhas e mergulha em rasantes com as garras. O seu grito estridente deixa todos lentos.",
              "It circles its prey high in the mountains and dives with its talons. Its shrill scream slows everyone nearby."),
    "basilisk": ("Quem o encarar por tempo demais vira pedra. Uma abóbora na cabeça protege do olhar; a luz do sol o enfraquece.",
                 "Whoever stares at it for too long turns to stone. A pumpkin on your head protects from its gaze; sunlight weakens it."),
    "mimic": ("Um baú perfeito... perfeito demais. A tampa vira uma bocarra cheia de dentes quando você chega perto.",
              "A perfect chest... too perfect. Its lid turns into a toothy maw when you get close."),
    "gargoyle": ("Estátua imóvel enquanto alguém a observa. À noite, sem olhares, acorda e caça voando. Magia de Terra a quebra.",
                 "A motionless statue while someone watches it. At night, unobserved, it wakes and hunts from the air. Earth magic breaks it."),
    "fairy": ("Criaturinha pacífica dos campos floridos que cura os feridos. Quem lhe der flores pode ganhar uma amiga para sempre.",
              "A peaceful little creature of the flower fields that heals the wounded. Give it flowers and you may gain a friend for life."),
    # --- creatures_d ---
    "salamander": ("Lagarto de fogo dos desertos e do Nether. Cospe bolas de fogo e morde incendiando; detesta água e chuva.",
                   "A fire lizard of deserts and the Nether. It spits fireballs and bites with flame; it hates water and rain."),
    "ember_imp": ("Diabinho voador que arranha queimando, foge rindo e dispara rajadas de brasas.",
                  "A flying imp that scratches with burning claws, flees laughing and fires bursts of embers."),
    "undine": ("Espírito de água dos rios e mares. Neutra, mas defende as irmãs com jatos d'água e canções que curam.",
               "A water spirit of rivers and seas. Neutral, but it defends its sisters with water jets and healing songs."),
    "tide_serpent": ("Caçadora dos oceanos: persegue quem nada ou navega, morde puxando para o fundo e derruba gente de barcos.",
                     "Hunter of the oceans: it chases swimmers and sailors, bites to drag them down and knocks people off boats."),
    "moss_golem": ("Guardião lento das cavernas verdejantes. Regenera na luz e na chuva e enraíza quem o provoca.",
                   "A slow guardian of lush caves. It regenerates in light and rain and roots in place whoever provokes it."),
    "crystal_mole": ("Tímida, com cristais de ametista nas costas. Provocada, some na terra e brota atrás do alvo.",
                     "Shy, with amethyst crystals on its back. When provoked it burrows away and pops up behind its target."),
    "sylph": ("Espírito brincalhão do vento. Às vezes dança em volta de viajantes e os abençoa com velocidade.",
              "A playful wind spirit. Sometimes it dances around travelers and blesses them with speed."),
    "storm_hawk": ("Predador dos picos que chama raios: um círculo de faíscas marca o chão segundos antes da descarga.",
                   "A peak predator that calls lightning: a ring of sparks marks the ground seconds before the strike."),
    "lumen_sprite": ("Espírito de luz pacífico que só aparece de dia. Alimentado com pó de pedra luminosa, retribui com uma essência.",
                     "A peaceful light spirit that only appears by day. Fed glowstone dust, it repays with an essence."),
    "solar_sentinel": ("Construto dourado que carrega raios de sol cegantes. À noite adormece sob a areia.",
                       "A golden construct that charges blinding sunbeams. At night it sleeps beneath the sand."),
    "creeping_shade": ("Quase invisível no escuro, ataca com botes que corrompem e um véu de trevas. O sol a dissolve.",
                       "Almost invisible in the dark, it strikes with corrupting lunges and a veil of darkness. The sun dissolves it."),
    "void_bat": ("Caçador das cavernas profundas que rouba vida e pisca para longe quando ferido.",
                 "A hunter of the deep caves that drains life and blinks away when wounded."),
    "wandering_spirit": ("Alma arcana com uma lanterna violeta. Quem fica em paz ao seu lado recebe um presente antes que ela se desfaça.",
                         "An arcane soul with a violet lantern. Those who stay peacefully by its side receive a gift before it fades."),
    "arcane_golem": ("Construto rúnico que guarda ruínas antigas. Ignora visitantes educados; provocado, acende as runas e luta.",
                     "A runic construct guarding ancient ruins. It ignores polite visitors; provoked, it lights its runes and fights."),
    # --- creatures_e ---
    "greater_fire_elemental": ("Torso de magma sobre um vórtice de chamas. Socos incendiários, bolas de magma e uma nova de fogo quando cercado.",
                               "A magma torso above a vortex of flames. Burning punches, magma balls and a fire nova when surrounded."),
    "greater_water_elemental": ("Corpo de água viva, sempre encharcado — o que torna o Raio devastador contra ele. Puxa todos com redemoinhos.",
                                "A body of living water, always soaked — which makes Lightning devastating against it. It pulls everyone in with whirlpools."),
    "greater_earth_elemental": ("Colosso de pedra, musgo e cristal. Terremotos enraízam quem está no chão e ele se regenera tocando a terra.",
                                "A colossus of stone, moss and crystal. Its earthquakes root anyone on the ground and it heals while touching the earth."),
    "greater_air_elemental": ("Um tornado vivo que corta com lâminas de vento e levanta inimigos num ciclone. A queda dói.",
                              "A living tornado that slices with wind blades and lifts enemies in a cyclone. The fall hurts."),
    "lesser_phoenix": ("Ave de fogo que renasce uma vez das próprias cinzas, explodindo em chamas. Derrote-a duas vezes.",
                       "A bird of fire that is reborn once from its own ashes, bursting into flames. Defeat it twice."),
    "storm_wyvern": ("Drake voador das tempestades. Imune a raios — eles o curam — e mais forte durante temporais.",
                     "A flying storm drake. Immune to lightning — it heals it — and stronger during storms."),
    "void_crawler": ("Artrópode de sombra que escala paredes e reaparece atrás da presa.",
                     "A shadow arthropod that climbs walls and reappears behind its prey."),
    "watcher_eye": ("Olho flutuante com tentáculos. Carrega um raio de sombra — quebre a linha de visão antes que dispare.",
                    "A floating eye with tentacles. It charges a shadow beam — break line of sight before it fires."),
    "young_kraken": ("Cefalópode anfíbio que agarra e esmaga. Um golpe forte faz ele soltar a presa.",
                     "An amphibious cephalopod that grabs and crushes. A strong blow makes it let go of its prey."),
    "angel_sentinel": ("Guarda alada da Cidade de Cristal. Lança de luz, julgamentos do céu e escudo divino quando ferida.",
                       "A winged guard of the Crystal City. A spear of light, judgments from the sky and a divine shield when wounded."),
    "cherub": ("Pequeno celestial de suporte: não ataca, mas cura os anjos e cega agressores com um clarão.",
               "A small support celestial: it does not attack, but heals angels and blinds attackers with a flash."),
    "crystal_golem": ("Guardião de cristal que reflete projéteis — até magias — de volta para quem atirou. Picaretas o quebram.",
                      "A crystal guardian that reflects projectiles — even spells — back at the shooter. Pickaxes break it."),
    # --- bosses ---
    "ignis": ("Guardião do Fogo das Ilhas de Brasa. Chuvas de meteoros, poças de lava e ondas de chamas. Odeia água.",
              "Fire Guardian of the Ember Isles. Meteor showers, lava pools and waves of flame. It hates water."),
    "tide_guardian": ("Maré, Guardiã da Água do Mar Suspenso. Redemoinhos, tsunamis e uma paciência tão funda quanto o oceano.",
                      "Tide, Water Guardian of the Suspended Sea. Whirlpools, tsunamis and patience as deep as the ocean."),
    "primordial_golem": ("Guardião da Terra dos Picos Terrosos. Arremessa rochas onde você vai estar e faz o chão tremer.",
                         "Earth Guardian of the Earthen Peaks. It hurls boulders where you are going to be and makes the ground quake."),
    "zephyr": ("Zéfiro, Guardião do Vento dos Céus de Tempestade. Rápido como uma ideia, invoca tornados e lâminas de ar.",
               "Zephyr, Wind Guardian of the Storm Skies. Fast as a thought, it summons tornadoes and air blades."),
    "abyss_sovereign": ("Senhor do Abismo, sentado num trono de estrelas mortas. Devora a luz ao redor. Luz é a sua fraqueza.",
                        "Lord of the Abyss, seated on a throne of dead stars. It devours the light around it. Light is its weakness."),
    "seraph": ("O Serafim de seis asas que guarda o Santuário Celestial. A Sombra fere o que a Luz protege.",
               "The six-winged Seraph who guards the Celestial Sanctuary. Shadow wounds what Light protects."),
    "twilight_herald": ("O Arauto do Crepúsculo, causa da Grande Ruptura. Expande o próprio Domínio e mistura Luz e Sombra em algo impossível.",
                        "The Twilight Herald, cause of the Great Rupture. It expands its own Domain and blends Light and Shadow into something impossible."),
    "renegade_archmage": ("Um arquimago que trocou a ética pelo poder. Conhece magias que nenhum grimório ousa registrar.",
                          "An archmage who traded ethics for power. It knows spells no grimoire dares to record."),
    "corrupted_witch": ("A bruxa anciã que provou da própria maldição. Seu caldeirão ferve com pragas.",
                        "The elder witch who tasted her own curse. Her cauldron boils with plagues."),
    "imprisoned_patron": ("Um patrono demoníaco acorrentado num altar profano. As correntes não vão segurá-lo para sempre.",
                          "A demonic patron chained to an unholy altar. The chains will not hold it forever."),
    "corrupted_ent": ("Árvore ancestral envenenada pela corrupção. Suas raízes ainda lembram como era proteger a floresta.",
                      "An ancestral tree poisoned by corruption. Its roots still remember what it was like to protect the forest."),
    "lich": ("Necromante que venceu a morte e esqueceu como viver. Sua filactéria guarda o segredo da sua imortalidade.",
             "A necromancer who defeated death and forgot how to live. Its phylactery holds the secret of its immortality."),
    # --- v0.7: chefes das Câmaras de Provação (bosses_d) ---
    "trial_avatar": ("Avatar Elemental: o guardião das Câmaras de Provação, um corpo de placas flutuando em volta de um núcleo de cristal. "
                     "Tem a cor e as magias do elemento testado — inclusive magias programáveis — e fica mais perigoso a cada fase. "
                     "Todo golpe grande é marcado no chão antes de cair.",
                     "Elemental Avatar: the warden of the Trial Chambers, a body of plates floating around a crystal core. It takes "
                     "the color and spells of the element being tested — programmed spells included — and grows deadlier with each "
                     "phase. Every big attack is marked on the ground before it lands."),
    "trial_sentinel": ("Sentinela da Provação: elite de escudo erguido que protege o Avatar nas provas mais altas. Investe em linha "
                       "reta e golpeia em área — fique fora das marcas no chão.",
                       "Trial Sentinel: a shield-bearing elite that guards the Avatar in the highest trials. It charges in a straight "
                       "line and strikes in an area — stay out of the marks on the ground."),
    # --- fora do catalogo, mas criaturas de verdade (categoria MONSTER): aparecem em "Outros" ---
    "archmage_illusion": ("Cópia ilusória do Arquimago Renegado, quase perfeita: só olhos atentos notam a leve transparência. Dispara mísseis fracos e se desfaz com um único golpe — o difícil é descobrir qual delas é a verdadeira.",
                          "An illusory copy of the Renegade Archmage, almost perfect: only sharp eyes notice the faint transparency. It fires weak missiles and shatters with a single blow — the hard part is finding out which one is real."),
    "hex_cat": ("Gato preto amaldiçoado, invocado pela Bruxa Corrompida. Salta no alvo sem aviso e some numa nuvem de fumaça pouco depois. Ninguém nunca conseguiu domar um.",
                "A cursed black cat summoned by the Corrupted Witch. It pounces on its target without warning and vanishes in a puff of smoke soon after. No one has ever tamed one."),
    "ascension_shadow": ("A Sombra da Tribulação: o seu próprio reflexo sombrio, que surge no Ritual de Ascensão. Conhece as magias que você preparou e só usa a ultimate quando está perdendo. Vencê-la é vencer a si mesmo.",
                         "The Shadow of Tribulation: your own dark reflection, born from the Ascension Ritual. It knows the spells you prepared and only uses its ultimate when it is losing. To defeat it is to defeat yourself."),
    "patron_ember_lord": ("Patrono das chamas infernais. Exige oferendas ardentes de quem firma pactos com ele.",
                          "Patron of infernal flames. It demands burning offerings from those who seal pacts with it."),
    "patron_void_lady": ("Patrona do Vazio, cujos sussurros prometem conhecimento proibido em troca de pedaços da alma.",
                         "Patron of the Void, whose whispers promise forbidden knowledge in exchange for pieces of the soul."),
    "patron_drowned_king": ("O Rei Afogado, patrono das profundezas. Sua coroa enferrujada ainda comanda as marés.",
                            "The Drowned King, patron of the depths. Its rusted crown still commands the tides."),
    # --- montarias ---
    "griffin": ("Metade águia, metade leão, orgulho dos céus. Conquistar a confiança de um grifo é conquistar o céu.",
                "Half eagle, half lion, pride of the skies. To win a griffin's trust is to win the sky."),
    "sky_ray": ("Arraia gigante que plana entre as ilhas do Plano Elemental como se o céu fosse um oceano.",
                "A giant ray that glides between the islands of the Elemental Plane as if the sky were an ocean."),
    "pegasus": ("Cavalo alado do Santuário Celestial. Dizem que só aceita cavaleiros de coração sincero.",
                "A winged horse of the Celestial Sanctuary. They say it only accepts riders with a sincere heart."),
    "wyvern": ("Dragão menor do Abismo. Selvagem, territorial e veloz — uma montaria temível para quem a domar.",
               "A lesser dragon of the Abyss. Wild, territorial and swift — a fearsome mount for whoever tames it."),
    # --- aliados / NPCs / invocações ---
    "guild_receptionist": ("A recepcionista da Guilda dos Aventureiros: registra novatos, distribui missões e nunca perde o sorriso.",
                           "The Adventurers' Guild receptionist: registers newcomers, hands out quests and never loses her smile."),
    "guild_master": ("O Mestre da Guilda, veterano de mil batalhas. É ele quem aplica as provas de promoção de rank.",
                     "The Guild Master, veteran of a thousand battles. He is the one who runs the rank promotion trials."),
    "adventurer": ("Aventureiros que vagam pelo mundo atrás de missões. Conversam, lutam ao seu lado e às vezes trocam itens.",
                   "Adventurers roaming the world in search of quests. They chat, fight by your side and sometimes trade items."),
    "familiar_crow": ("Corvo familiar ligado à alma do seu mestre. Vê longe e traz notícias.",
                      "A familiar crow bound to its master's soul. It sees far and brings news."),
    "familiar_cat": ("Gato familiar, misterioso como todo gato. Pressente o perigo antes de todos.",
                     "A familiar cat, as mysterious as any cat. It senses danger before anyone else."),
    "summoned_imp": ("Diabrete invocado por um pacto. Pequeno, irritante e surpreendentemente útil em batalha.",
                     "An imp summoned through a pact. Small, annoying and surprisingly useful in battle."),
    "summoned_demon": ("Demônio invocado das profundezas para lutar por quem o chamou — enquanto o pacto durar.",
                       "A demon summoned from the depths to fight for its caller — for as long as the pact lasts."),
    "undead_servant": ("Servo morto-vivo erguido por um necromante. Leal, incansável e sem opinião própria.",
                       "An undead servant raised by a necromancer. Loyal, tireless and without opinions of its own."),
    "bone_warrior": ("Guerreiro de ossos que serve ao necromante com escudo e espada.",
                     "A warrior of bones who serves the necromancer with shield and sword."),
    "animal_spirit": ("Espírito ancestral dos animais que responde ao chamado dos druidas.",
                      "An ancestral animal spirit that answers the druids' call."),
    "summoned_salamander": ("Salamandra invocada pela magia de Fogo para lutar ao lado do conjurador.",
                            "A salamander summoned by Fire magic to fight alongside its caster."),
    "summoned_undine": ("Ondina invocada pela magia de Água, que protege e cura o conjurador.",
                        "An undine summoned by Water magic, protecting and healing its caster."),
    "summoned_stone_golem": ("Golem de pedra erguido pela magia de Terra. Lento, mas uma muralha viva.",
                             "A stone golem raised by Earth magic. Slow, but a living wall."),
    "summoned_sylph": ("Sílfide invocada pela magia de Vento, rápida como uma rajada.",
                       "A sylph summoned by Wind magic, fast as a gust."),
    "summoned_guardian_fairy": ("Fada guardiã invocada pela magia de Luz para proteger o conjurador.",
                                "A guardian fairy summoned by Light magic to protect its caster."),
    "living_shadow": ("A sombra do conjurador, desprendida pela magia das Trevas para lutar por conta própria.",
                      "The caster's own shadow, torn loose by Shadow magic to fight on its own."),
    "summoned_ent": ("Ent invocado pela magia da Natureza. Antigo, paciente e muito, muito forte.",
                     "An ent summoned by Nature magic. Ancient, patient and very, very strong."),
}

UI = {
    "entity.isekai_mahou.guide_spirit": ("Espírito Guia", "Guide Spirit"),
    "item.isekai_mahou.guide_spirit_spawn_egg": ("Ovo de Espírito Guia", "Guide Spirit Spawn Egg"),
    # Abas e Diário
    "screen.isekai_mahou.journal.tab": ("Diário", "Journal"),
    "screen.isekai_mahou.journal.header": ("Diário do Isekai", "Isekai Journal"),
    "screen.isekai_mahou.journal.progress": ("%s/%s capítulos", "%s/%s chapters"),
    "screen.isekai_mahou.journal.chapter_n": ("Capítulo %s", "Chapter %s"),
    "screen.isekai_mahou.journal.state.locked": ("Bloqueado", "Locked"),
    "screen.isekai_mahou.journal.state.active": ("Em andamento", "In progress"),
    "screen.isekai_mahou.journal.state.done": ("Concluído", "Completed"),
    "screen.isekai_mahou.journal.locked_title": ("???", "???"),
    "screen.isekai_mahou.journal.locked_text": ("As páginas seguintes ainda estão em branco... Conclua o capítulo anterior para que a história as escreva.",
                                                "The next pages are still blank... Finish the previous chapter so the story can write them."),
    "screen.isekai_mahou.journal.objectives": ("Objetivos: %s/%s", "Objectives: %s/%s"),
    "screen.isekai_mahou.journal.optional": ("(opcional)", "(optional)"),
    "screen.isekai_mahou.journal.bonus_xp": ("+%s XP", "+%s XP"),
    "screen.isekai_mahou.journal.rewards": ("Recompensas:", "Rewards:"),
    "screen.isekai_mahou.journal.reward_xp": ("%s XP", "%s XP"),
    "screen.isekai_mahou.journal.reward_item": ("%sx %s", "%sx %s"),
    "screen.isekai_mahou.journal.reward_scrolls": ("%sx Pergaminho de Glifo (até tier %s)", "%sx Glyph Scroll (up to tier %s)"),
    "screen.isekai_mahou.journal.reward_essences": ("%sx Essência de cada elemento seu", "%sx Essence of each of your elements"),
    "screen.isekai_mahou.journal.reward_legends": ("%sx Pergaminho Lendário", "%sx Legendary Scroll"),
    "screen.isekai_mahou.journal.hint_label": ("Dica:", "Hint:"),
    "screen.isekai_mahou.journal.located": ("⌖ %sm", "⌖ %sm"),
    # Rastreador de objetivos (canto da tela)
    "screen.isekai_mahou.journal.tracker.chapter": ("Cap. %s · %s", "Ch. %s · %s"),
    "screen.isekai_mahou.journal.tracker.not_found": ("Nada por perto: explore", "Nothing nearby: explore"),
    "screen.isekai_mahou.journal.tracker.elsewhere": ("Fica em outra dimensão", "It lies in another dimension"),
    "screen.isekai_mahou.journal.tracker.trial_ready": ("Pronta: %s", "Ready: %s"),
    "screen.isekai_mahou.journal.tracker.trial_need": ("Elemento nv. %s · Círculo %s", "Element lv. %s · Circle %s"),
    # Rosa dos ventos (Espírito Guia)
    "screen.isekai_mahou.journal.dir.n": ("Norte", "North"),
    "screen.isekai_mahou.journal.dir.ne": ("Nordeste", "Northeast"),
    "screen.isekai_mahou.journal.dir.e": ("Leste", "East"),
    "screen.isekai_mahou.journal.dir.se": ("Sudeste", "Southeast"),
    "screen.isekai_mahou.journal.dir.s": ("Sul", "South"),
    "screen.isekai_mahou.journal.dir.sw": ("Sudoeste", "Southwest"),
    "screen.isekai_mahou.journal.dir.w": ("Oeste", "West"),
    "screen.isekai_mahou.journal.dir.nw": ("Noroeste", "Northwest"),
    "screen.isekai_mahou.journal.chronicle": ("Crônica", "Chronicle"),
    "screen.isekai_mahou.journal.chronicle_done": ("Crônica (capítulo concluído)", "Chronicle (chapter completed)"),
    "screen.isekai_mahou.journal.epilogue": ("Epílogo", "Epilogue"),
    "screen.isekai_mahou.journal.empty": ("O diário está em branco... (os capítulos ainda não chegaram do servidor)",
                                          "The journal is blank... (the chapters have not arrived from the server yet)"),
    "screen.isekai_mahou.journal.call_guide": ("Chamar Espírito Guia", "Call Guide Spirit"),
    "screen.isekai_mahou.journal.call_guide.tooltip": ("O Espírito Guia surge ao seu lado e, se o objetivo tiver um lugar, voa na direção dele deixando um rastro de luz. Clique nele para ouvir a dica.",
                                                   "The Guide Spirit appears by your side and, if the objective has a place, flies toward it leaving a trail of light. Click it to hear a hint."),
    "screen.isekai_mahou.journal.call_guide.wait": ("Espírito Guia (%ss)", "Guide Spirit (%ss)"),
    # Bestiário
    "screen.isekai_mahou.bestiary.tab": ("Bestiário", "Bestiary"),
    "screen.isekai_mahou.bestiary.header": ("Bestiário", "Bestiary"),
    "screen.isekai_mahou.bestiary.discovered": ("Registradas: %s/%s", "Recorded: %s/%s"),
    "screen.isekai_mahou.bestiary.search": ("Buscar criatura...", "Search creature..."),
    "screen.isekai_mahou.bestiary.empty": ("Nenhuma criatura encontrada.", "No creatures found."),
    "screen.isekai_mahou.bestiary.unknown": ("???", "???"),
    "screen.isekai_mahou.bestiary.drag_hint": ("arraste para girar", "drag to rotate"),
    "screen.isekai_mahou.bestiary.kills": ("Abates: %s", "Kills: %s"),
    "screen.isekai_mahou.bestiary.defeated": ("Batalhas vencidas: %s", "Battles won: %s"),
    "screen.isekai_mahou.bestiary.health": ("Vida: %s", "Health: %s"),
    "screen.isekai_mahou.bestiary.affinities": ("Afinidades elementais", "Elemental affinities"),
    "screen.isekai_mahou.bestiary.weak": ("Fraco ×%s", "Weak ×%s"),
    "screen.isekai_mahou.bestiary.resist": ("Resiste ×%s", "Resists ×%s"),
    "screen.isekai_mahou.bestiary.immune": ("Imune", "Immune"),
    "screen.isekai_mahou.bestiary.normal": ("Normal", "Normal"),
    "screen.isekai_mahou.bestiary.locked_desc": ("Ainda não há registro desta criatura. Derrote-a (ou, se for pacífica, aproxime-se e interaja com ela) para preencher esta página.",
                                                 "There is no record of this creature yet. Defeat it (or, if it is peaceful, approach and interact with it) to fill in this page."),
    "screen.isekai_mahou.bestiary.no_desc": ("Pouco se sabe sobre esta criatura... os estudiosos da Guilda ainda discutem o que ela é.",
                                             "Little is known about this creature... the Guild's scholars still argue about what it is."),
    "screen.isekai_mahou.bestiary.category.world": ("Criaturas do Mundo", "Creatures of the World"),
    "screen.isekai_mahou.bestiary.category.elemental": ("Elementais", "Elementals"),
    "screen.isekai_mahou.bestiary.category.dimension": ("Criaturas das Dimensões", "Dimensional Creatures"),
    "screen.isekai_mahou.bestiary.category.boss": ("Chefes", "Bosses"),
    "screen.isekai_mahou.bestiary.category.ally": ("Aliados e Invocações", "Allies and Summons"),
    "screen.isekai_mahou.bestiary.category.other": ("Outros", "Others"),
    # Mensagens
    "message.isekai_mahou.journal.chapter_begin": ("✦ %s: %s", "✦ %s: %s"),
    "message.isekai_mahou.journal.open_hint": ("Abra o Diário no menu (%s). O objetivo atual fica no canto da tela (%s mostra/esconde).",
                                               "Open the Journal in the menu (%s). The current objective stays in the corner of the screen (%s shows/hides it)."),
    "message.isekai_mahou.journal.step_done": ("✔ Missão concluída: %s", "✔ Quest completed: %s"),
    "message.isekai_mahou.journal.chapter_done": ("✦ Capítulo concluído: %s", "✦ Chapter completed: %s"),
    "message.isekai_mahou.journal.rewards": ("Recompensas: %s", "Rewards: %s"),
    "message.isekai_mahou.journal.step_rewards": ("   Recompensa: %s", "   Reward: %s"),
    "message.isekai_mahou.journal.steps_more": ("✔ ...e mais %s missões concluídas (%s XP no total)", "✔ ...and %s more quests completed (%s XP in total)"),
    "message.isekai_mahou.journal.reward_legend": ("✦ %s", "✦ %s"),
    "message.isekai_mahou.journal.spirit_follow": ("\"Siga o meu rastro! O objetivo fica a ~%s blocos — direção: %s.\"",
                                                   "\"Follow my trail! The objective is ~%s blocks away — direction: %s.\""),
    "message.isekai_mahou.journal.spirit_direction": ("\"O lugar fica a ~%s blocos — direção: %s. A seta no canto da tela mostra o caminho.\"",
                                                      "\"The place is ~%s blocks away — direction: %s. The arrow in the corner of the screen shows the way.\""),
    "message.isekai_mahou.journal.spirit_elsewhere": ("\"O que você procura está em outro mundo. Siga a dica para chegar lá primeiro.\"",
                                                      "\"What you seek lies in another world. Follow the hint to get there first.\""),
    "message.isekai_mahou.journal.spirit_lost": ("\"Não sinto esse lugar por perto... Explore mais longe (uns 500 blocos) e me chame de novo.\"",
                                                 "\"I can't sense that place nearby... Explore farther (about 500 blocks) and call me again.\""),
    "message.isekai_mahou.journal.reward_glyph": ("Pergaminho: %s", "Scroll: %s"),
    "message.isekai_mahou.journal.spirit": ("✧ Espírito Guia: %s", "✧ Guide Spirit: %s"),
    "message.isekai_mahou.journal.spirit_greet": ("\"Estou aqui, viajante. Toque em mim quando precisar de uma dica.\"",
                                                  "\"I am here, traveler. Touch me whenever you need a hint.\""),
    "message.isekai_mahou.journal.hint_objective": ("Objetivo atual: %s", "Current objective: %s"),
    "message.isekai_mahou.journal.hint_complete": ("\"A sua lenda já está completa. Agora, viva-a como quiser.\"",
                                                   "\"Your legend is already complete. Now live it however you wish.\""),
    "message.isekai_mahou.journal.hint_none": ("\"As páginas do seu destino ainda estão em branco... Volte a me chamar mais tarde.\"",
                                               "\"The pages of your destiny are still blank... Call me again later.\""),
    "message.isekai_mahou.journal.hint_wait": ("\"Você já fez tudo o que este capítulo pedia. Espere um instante: a história está virando a página.\"",
                                               "\"You have done everything this chapter asked. Wait a moment: the story is turning the page.\""),
    "message.isekai_mahou.journal.guide_cooldown": ("O Espírito Guia ainda está se recompondo (%ss).", "The Guide Spirit is still gathering itself (%ss)."),
    "message.isekai_mahou.journal.guide_called": ("O Espírito Guia atende ao seu chamado.", "The Guide Spirit answers your call."),
    "message.isekai_mahou.journal.bestiary_new": ("✦ Nova entrada no Bestiário: %s", "✦ New Bestiary entry: %s"),
    "message.isekai_mahou.journal.legend_broadcast": ("✦ %s derrotou o Arauto do Crepúsculo e completou o Diário do Isekai! Uma nova lenda nasceu neste mundo.",
                                                      "✦ %s defeated the Twilight Herald and completed the Isekai Journal! A new legend is born in this world."),
    "message.isekai_mahou.journal.the_end": ("— Fim do Diário do Isekai —", "— The End of the Isekai Journal —"),
    # Anúncios e títulos
    "announce.isekai_mahou.journal.updated": ("Missão atualizada: %s", "Quest updated: %s"),
    "announce.isekai_mahou.journal.chapter_done": ("Capítulo concluído: %s", "Chapter completed: %s"),
    "announce.isekai_mahou.journal.next": ("Próximo objetivo: %s", "Next objective: %s"),
    "title.isekai_mahou.journal.chapter_done": ("Capítulo Concluído", "Chapter Complete"),
    "title.isekai_mahou.journal.finale": ("O Crepúsculo Termina", "The Twilight Ends"),
    "title.isekai_mahou.journal.finale_sub": ("A sua lenda está escrita", "Your legend is written"),
    "toast.isekai_mahou.journal.chapter_done": ("Capítulo concluído!", "Chapter complete!"),
    "toast.isekai_mahou.journal.step_reward": ("Recompensa de missão", "Quest reward"),
    # Comandos
    "command.isekai_mahou.journal.empty": ("Nenhum capítulo carregado (data/<ns>/journal).", "No chapters loaded (data/<ns>/journal)."),
    "command.isekai_mahou.journal.status": ("Diário de %s: %s/%s capítulos", "%s's Journal: %s/%s chapters"),
    "command.isekai_mahou.journal.no_chapter": ("%s não tem capítulo em andamento.", "%s has no chapter in progress."),
    "command.isekai_mahou.journal.completed": ("Capítulo \"%s\" concluído para %s.", "Chapter \"%s\" completed for %s."),
    "command.isekai_mahou.journal.reset": ("Diário de %s apagado (%s marcadores).", "%s's Journal reset (%s markers)."),
    "command.isekai_mahou.journal.locate_none": ("O objetivo atual não tem um lugar para localizar.", "The current objective has no place to locate."),
    "command.isekai_mahou.journal.locate_missing": ("Nenhum lugar para \"%s\" a até ~1600 blocos (ou nesta dimensão).",
                                                    "No place for \"%s\" within ~1600 blocks (or in this dimension)."),
    "command.isekai_mahou.journal.locate_found": ("\"%s\": %s %s %s (~%s blocos)", "\"%s\": %s %s %s (~%s blocks)"),
}


# ----- v0.9: chefes novos no Bestiário (bosses_e/f/g; sem o módulo, a entrada simplesmente não aparece) --------
BESTIARY.update({
    "primordial_dragon": ("O dragão que era todos os elementos antes de os elementos terem nome. Selado além do Crepúsculo pelos seis sábios, "
                          "acordou quando o Arauto caiu. Cinco fases: Fogo, Água, Terra, Vento e o próprio Crepúsculo.",
                          "The dragon that was every element before the elements had names. Sealed beyond the Twilight by the six sages, "
                          "it woke when the Herald fell. Five phases: Fire, Water, Earth, Wind and the Twilight itself."),
    "swamp_hydra": ("Cada cabeça cortada vira duas — a não ser que o toco queime. O veneno dela se acumula em quem fica perto demais.",
                    "Every severed head becomes two — unless the stump burns. Its poison builds up in whoever stays too close."),
    "desert_basilisk": ("Rei dos túmulos sob as dunas. Encarar os olhos dele é virar estátua; ele também nada na areia como num lago.",
                        "King of the tombs beneath the dunes. Meeting its eyes means becoming a statue; it also swims through sand like a lake."),
    "ruin_colossus": ("Golem do tamanho de uma torre, construído para proteger uma cidade que não existe mais. Cada soco abre uma cratera.",
                      "A golem the size of a tower, built to protect a city that no longer exists. Every punch opens a crater."),
    "wyvern_king": ("Senhor das tempestades dos picos. Mergulha do céu, cospe raios e prefere presas molhadas.",
                    "Lord of the peak storms. It dives from the sky, spits lightning and prefers wet prey."),
    "false_hydra": ("Vive sob vilas abandonadas e canta para que ninguém se lembre dela. As cabeças só existem quando ninguém olha.",
                    "It lives beneath abandoned villages and sings so nobody remembers it. Its heads only exist when nobody looks."),
    "manticore": ("Leão com asas de morcego e uma cauda que dispara leques de espinhos. O rugido dela gela a coragem.",
                  "A lion with bat wings and a tail that fires fans of spikes. Its roar freezes courage."),
    "mimic_king": ("O baú dos baús. Transforma o próprio cofre em armadilha, engole aventureiros e cospe o que não gosta.",
                   "The chest of chests. It turns its own vault into a trap, swallows adventurers and spits out what it dislikes."),
})

# ----- v0.9: textos de interface (tutorial do Espírito Guia, falas na caixa de diálogo, final do Dragão) ---------
UI.pop("message.isekai_mahou.journal.spirit")
UI.update({
    # Tutorial (Dialogue "journal.tutorial")
    "journal.isekai_mahou.tutorial.story1": (
        "Ah, você acordou! Eu sou o Espírito Guia. Você foi arrancado do seu mundo e invocado para este — um lugar onde o "
        "próprio ar é feito de mana.",
        "Ah, you're awake! I am the Guide Spirit. You were torn from your world and summoned to this one — a place where "
        "the very air is made of mana."),
    "journal.isekai_mahou.tutorial.story2": (
        "Há eras, um Dragão Primordial que dominava todos os elementos foi selado além do Crepúsculo. Agora o selo está "
        "rachando: chefes antigos acordam e portais esquecidos voltam a pulsar.",
        "Ages ago, a Primordial Dragon that ruled every element was sealed beyond the Twilight. Now the seal is cracking: "
        "ancient bosses awaken and forgotten portals pulse again."),
    "journal.isekai_mahou.tutorial.story3": (
        "A sua classe é %s. Você ainda não é forte — mas cada chefe que cair te deixa mais perto de detê-lo. Eu mostro o "
        "caminho. Primeiro, o básico!",
        "Your class is %s. You are not strong yet — but every boss that falls brings you closer to stopping it. I'll show "
        "you the way. First, the basics!"),
    "journal.isekai_mahou.tutorial.press_j": (
        "O Diário marca o seu próximo objetivo num cantinho da tela, com uma seta para os lugares. Aperte %s para mostrar "
        "o rastreador!",
        "The Journal marks your next objective in a corner of the screen, with an arrow to places. Press %s to show the "
        "tracker!"),
    "journal.isekai_mahou.tutorial.press_b": (
        "Ótimo! Agora abra o Menu com %s. Status, Caminhos, Diário, Bestiário, Viagem... tudo o que você é fica ali. "
        "Dê uma olhada e feche com Esc.",
        "Great! Now open the Menu with %s. Status, Paths, Journal, Bestiary, Travel... everything you are lives there. "
        "Take a look, then close it with Esc."),
    "journal.isekai_mahou.tutorial.grimoire": (
        "Abra o Menu de novo e clique na aba Grimório. É aqui que glifos viram magias: uma Forma (como sai) + Efeitos "
        "(o que faz). Você já tem algumas prontas nos slots!",
        "Open the Menu again and click the Grimoire tab. This is where glyphs become spells: a Form (how it leaves you) + "
        "Effects (what it does). You already have a few ready in your slots!"),
    "journal.isekai_mahou.tutorial.cast.magic": (
        "Agora feche o menu e segure %s para conjurar a magia do 1º slot. Ela gasta %s — a barra ao lado mostra quanto "
        "resta.",
        "Now close the menu and hold %s to cast the spell in the 1st slot. It spends %s — the bar on the side shows how "
        "much is left."),
    "journal.isekai_mahou.tutorial.cast.martial": (
        "Feche o menu e segure %s: o Golpe Arcano encanta a sua arma e o PRÓXIMO golpe solta a magia. Técnicas gastam %s, "
        "que volta a cada acerto.",
        "Close the menu and hold %s: Arcane Strike enchants your weapon and your NEXT hit releases the spell. Techniques "
        "spend %s, which comes back with every hit."),
    "journal.isekai_mahou.tutorial.cast.ranged": (
        "Feche o menu e segure %s: sai a Flecha Arcana! Com arco ou besta e flechas, é uma flecha de verdade; sem eles, "
        "uma flecha conjurada. Técnicas gastam %s, que volta a cada acerto.",
        "Close the menu and hold %s: out flies an Arcane Arrow! With a bow or crossbow and arrows it is a real arrow; "
        "without them, a conjured one. Techniques spend %s, which comes back with every hit."),
    "journal.isekai_mahou.tutorial.end": (
        "Perfeito! Siga o rastreador: ele aponta o caminho de chefe em chefe. Se se perder, me chame pelo Diário. A sua "
        "lenda começa agora!",
        "Perfect! Follow the tracker: it points the way from boss to boss. If you get lost, call me from the Journal. Your "
        "legend begins now!"),
    "journal.isekai_mahou.tutorial.resume": (
        "Que bom que você voltou! Vamos terminar o que começamos — falta pouco.",
        "Good to have you back! Let's finish what we started — it won't take long."),
    "message.isekai_mahou.journal.tutorial_skipped": ("Tutorial pulado. Aperte %s para ver o objetivo atual no canto da tela.",
                                                      "Tutorial skipped. Press %s to see the current objective in the corner of the screen."),
    # Falas do Espírito Guia (caixa de diálogo: sem aspas, o nome dele já aparece na aba)
    "message.isekai_mahou.journal.spirit_follow": ("Siga o meu rastro! O objetivo fica a ~%s blocos — direção: %s.",
                                                   "Follow my trail! The objective is ~%s blocks away — direction: %s."),
    "message.isekai_mahou.journal.spirit_direction": ("O lugar fica a ~%s blocos — direção: %s. A seta no canto da tela mostra o caminho.",
                                                      "The place is ~%s blocks away — direction: %s. The arrow in the corner of the screen shows the way."),
    "message.isekai_mahou.journal.spirit_elsewhere": ("O que você procura está em outro mundo. Siga a dica para chegar lá primeiro.",
                                                      "What you seek lies in another world. Follow the hint to get there first."),
    "message.isekai_mahou.journal.spirit_lost": ("Não sinto esse lugar por perto... Explore mais longe (uns 500 blocos) e me chame de novo.",
                                                 "I can't sense that place nearby... Explore farther (about 500 blocks) and call me again."),
    "message.isekai_mahou.journal.spirit_greet": ("Um novo capítulo começa! Toque em mim quando precisar de uma dica — a crônica completa fica no Diário (B).",
                                                  "A new chapter begins! Touch me whenever you need a hint — the full chronicle is in the Journal (B)."),
    "message.isekai_mahou.journal.hint_objective": ("Objetivo atual: %s", "Current objective: %s"),
    "message.isekai_mahou.journal.hint_complete": ("A sua lenda já está completa. Agora, viva-a como quiser.",
                                                   "Your legend is already complete. Now live it however you wish."),
    "message.isekai_mahou.journal.hint_none": ("As páginas do seu destino ainda estão em branco... Volte a me chamar mais tarde.",
                                               "The pages of your destiny are still blank... Call me again later."),
    "message.isekai_mahou.journal.hint_wait": ("Você já fez tudo o que este capítulo pedia. Espere um instante: a história está virando a página.",
                                               "You have done everything this chapter asked. Wait a moment: the story is turning the page."),
    "message.isekai_mahou.journal.legend_broadcast": ("✦ %s derrotou o Dragão Primordial e completou o Diário do Isekai! Uma nova lenda nasceu neste mundo.",
                                                      "✦ %s defeated the Primordial Dragon and completed the Isekai Journal! A new legend is born in this world."),
    "title.isekai_mahou.journal.finale": ("O Último Dragão", "The Last Dragon"),
    "title.isekai_mahou.journal.finale_sub": ("A sua lenda está escrita", "Your legend is written"),
    "toast.isekai_mahou.journal.step_done": ("Missão concluída", "Quest completed"),
    "command.isekai_mahou.journal.tutorial": ("Tutorial do Espírito Guia reiniciado para %s.", "Guide Spirit tutorial restarted for %s."),
    "command.isekai_mahou.journal.tutorial_none": ("%s ainda não tem classe: o tutorial começa depois da escolha.",
                                                   "%s has no class yet: the tutorial starts after choosing one."),
})

# ----- v1.0: criaturas e chefes novos (Mundo Oco, Reino das Fadas, companheiros e mestres das classes) ------------
BESTIARY.update({
    "hollow_crawler": ("Um Vazio rasteiro de seis patas e máscara de osso rachada. Ataca em bando e some na areia branca entre um bote e outro.",
                       "A low six-legged Hollow with a cracked bone mask. It hunts in packs and vanishes into the white sand between lunges."),
    "hollow_brute": ("Um Vazio do tamanho de uma carroça, todo braço e fome. A máscara dele aguenta golpes que quebrariam um escudo.",
                     "A Hollow the size of a cart, all arms and hunger. Its mask withstands blows that would shatter a shield."),
    "hollow_wraith": ("Vazio alado que mergulha do céu sem lua. Dizem que o grito dele é o eco da última palavra que disse em vida.",
                      "A winged Hollow that dives from the moonless sky. They say its shriek echoes the last word it spoke in life."),
    "menos_giant": ("Gigante de manto negro, nariz comprido e máscara branca, alto como uma torre. Carrega um raio na boca que apaga dunas inteiras.",
                    "A black-robed giant with a long nose and a white mask, tall as a tower. It charges a beam in its mouth that erases whole dunes."),
    "arrancar": ("Um Vazio que arrancou a própria máscara e ganhou forma humana. Rápido como um piscar, orgulhoso como um rei, e com um nome que exige respeito.",
                 "A Hollow that tore off its own mask and gained a human form. Fast as a blink, proud as a king, with a name that demands respect."),
    "fey_pixie": ("Fada travessa do crepúsculo eterno. Ri, rouba, troca as coisas de lugar e foge — e às vezes acerta um feitiço que dói de verdade.",
                  "A mischievous pixie of the eternal twilight. It laughs, steals, swaps things around and flees — and sometimes lands a spell that really hurts."),
    "bramble_hound": ("Cão feito de galhos e espinhos, de olhos de vaga-lume. Fareja intrusos na Corte das Fadas e não larga a presa.",
                      "A hound of branches and thorns with firefly eyes. It sniffs out intruders at the Fey Court and never lets go."),
    "sand_empress": ("Soberana do deserto branco. A areia a obedece como a maré obedece à lua: ela afunda, reaparece e transforma cada grão numa lâmina.",
                     "Sovereign of the white desert. The sand obeys her as the tide obeys the moon: she sinks, resurfaces and turns every grain into a blade."),
    "hollow_king": ("O Rei Vazio, no trono do Palácio da Noite Eterna. Tudo o que ele toca envelhece e se desfaz. A coroa de osso volta a crescer um dia depois de quebrada.",
                    "The Hollow King, on the throne of the Palace of Eternal Night. Everything he touches ages and crumbles. His bone crown grows back a day after breaking."),
    "thorn_queen": ("A Rainha dos Espinhos governa a Corte das Fadas com um sorriso e mil espinhos que dançam ao som da risada dela.",
                    "The Thorn Queen rules the Fey Court with a smile and a thousand thorns that dance to the sound of her laughter."),
    "gunslinger_outlaw": ("O Forasteiro Espectral aparece ao meio-dia para quem anda com um revólver de mana. Só aceita duelo limpo: quem saca antes do sino perde.",
                          "The Spectral Stranger appears at high noon for anyone carrying a mana revolver. It only accepts a clean duel: whoever draws before the bell loses."),
    "quincy_npc": ("Arqueiros de branco e cruz azul que vivem em acampamentos. Lutam com arcos de luz e, rendidos, podem entregar o próprio poder pelo Auswählen.",
                   "Archers in white with blue crosses who live in camps. They fight with bows of light and, once surrendered, may hand over their power through the Auswählen."),
    "ranger_wolf": ("O lobo do Ranger: leal, rápido e sempre o primeiro a chegar na presa. Cresce junto com o dono.",
                    "The Ranger's wolf: loyal, fast and always first to reach the prey. It grows alongside its master."),
    "ranger_eagle": ("A águia batedora do Ranger. Do alto, marca os alvos para a matilha inteira.",
                     "The Ranger's scout eagle. From above, it marks targets for the whole pack."),
    "ranger_steed": ("O corcel do Ranger: atende ao chamado, aguenta longas trilhas e deixa o dono atirar a galope.",
                     "The Ranger's steed: it answers the call, endures long trails and lets its rider shoot at full gallop."),
    "steelball_mentor": ("Mestre Rotari, o andarilho do chapéu largo. Ensina a Rotação em cinco lições — e só quando o aluno está pronto.",
                         "Master Rotari, the wanderer in the wide hat. He teaches the Spin in five lessons — and only when the student is ready."),
    "zanpakuto_spirit": ("O espírito da Zanpakutō, que vive no Mundo Interior do Shinigami. Fala, testa e às vezes luta: é a outra metade da alma da espada.",
                         "The Zanpakutō's spirit, who lives in the Shinigami's Inner World. It talks, tests and sometimes fights: the other half of the sword's soul."),
})

# ----- v1.0: interface (rota da classe, passo secreto, tutorial com loadouts e a rota) ------------------------------
UI.update({
    "screen.isekai_mahou.journal.section.class": ("✦ Rota do %s", "✦ %s Route"),
    "screen.isekai_mahou.journal.section.story": ("História", "Story"),
    "screen.isekai_mahou.journal.class_n": ("Rota · %s", "Route · %s"),
    "screen.isekai_mahou.journal.class_header": ("Rota da Classe · %s", "Class Route · %s"),
    "screen.isekai_mahou.journal.tracker.class": ("Rota do %s", "%s Route"),
    "toast.isekai_mahou.journal.class_begin": ("Rota do %s", "%s Route"),
    "message.isekai_mahou.journal.class_begin": ("✦ Rota do %s: %s", "✦ %s Route: %s"),
    "message.isekai_mahou.journal.spirit_class_greet": ("A sua classe, %s, tem um caminho só dela. Eu marco os passos no rastreador, logo abaixo da história.",
                                                        "Your class, %s, has a path of its own. I'll mark the steps on the tracker, right below the story."),
    "message.isekai_mahou.journal.hint_class_objective": ("Rota do %s: %s", "%s Route: %s"),
    "title.isekai_mahou.journal.class_done": ("Rota da Classe concluída!", "Class Route completed!"),
    "announce.isekai_mahou.journal.class_done": ("Rota do %s concluída: %s", "%s Route completed: %s"),
    "command.isekai_mahou.journal.no_class_chapter": ("%s não tem capítulo da rota da classe em andamento.",
                                                      "%s has no class route chapter in progress."),
    "journal.isekai_mahou.tutorial.cast.physical": (
        "Feche o menu e segure %s para usar a técnica do 1º slot — cada classe tem as suas prontas. Técnicas gastam %s, "
        "que volta a cada golpe que acerta.",
        "Close the menu and hold %s to use the technique in the 1st slot — every class has its own ready. Techniques "
        "spend %s, which comes back with every hit that lands."),
    "journal.isekai_mahou.tutorial.cast.dexterity": (
        "Feche o menu e segure %s para usar a técnica do 1º slot — tiro, carta, bola de aço, flecha... depende da sua "
        "classe. Técnicas gastam %s, que volta a cada acerto.",
        "Close the menu and hold %s to use the technique in the 1st slot — shot, card, steel ball, arrow... it depends on "
        "your class. Techniques spend %s, which comes back with every hit."),
    "journal.isekai_mahou.tutorial.loadouts": (
        "Você tem 9 loadouts, cada um com Z, X, C e G. Segure %s (ou %s) e gire a rolagem do mouse para trocar — "
        "monte um para cada tipo de luta!",
        "You have 9 loadouts, each with Z, X, C and G. Hold %s (or %s) and turn the mouse wheel to switch — build one "
        "for each kind of fight!"),
    "journal.isekai_mahou.tutorial.class_route": (
        "A sua classe, %s, tem uma rota própria: ranks, itens e segredos só seus. Abra o Menu (%s) e clique na aba Diário "
        "— ela fica no topo.",
        "Your class, %s, has a route of its own: ranks, items and secrets only yours. Open the Menu (%s) and click the "
        "Journal tab — it's at the top."),
})
UI.update({
    "journal.isekai_mahou.tutorial.end": (
        "Perfeito! O rastreador mostra a história (de chefe em chefe) e, logo abaixo, a rota da sua classe. Se se perder, "
        "me chame pelo Diário. A sua lenda começa agora!",
        "Perfect! The tracker shows the story (from boss to boss) and, right below, your class route. If you get lost, "
        "call me from the Journal. Your legend begins now!"),
})
# Os textos do tutorial da v0.9 por categoria antiga saíram (martial/ranged → physical/dexterity)
UI.pop("journal.isekai_mahou.tutorial.cast.martial", None)
UI.pop("journal.isekai_mahou.tutorial.cast.ranged", None)


def _variants(value):
    """Texto (pt, en) ou dict {sufixo: (pt, en)} → lista de (sufixo da chave, pt, en). Sufixo "" = chave base."""
    if isinstance(value, dict):
        return [("" if suf == "" else "." + suf, p, e) for suf, (p, e) in value.items()]
    return [("", value[0], value[1])]


def build_lang():
    pt, en = {}, {}
    for k, (p, e) in UI.items():
        pt[k], en[k] = p, e
    for cid, t in CH_TEXT.items():
        base = "journal.isekai_mahou." + cid
        for field in ("title", "lore", "outro", "intro"):
            if field in t:
                pt[base + "." + field], en[base + "." + field] = t[field]
        for i, (p, e) in enumerate(t.get("epilogue", []), 1):
            pt[base + ".epilogue." + str(i)], en[base + ".epilogue." + str(i)] = p, e
        for sid, (title, lore, hint, tip) in t["steps"].items():
            k = base + "." + sid
            pt[k], en[k] = title
            pt[k + ".lore"], en[k + ".lore"] = lore
            for suf, p, e in _variants(hint):
                pt[k + ".hint" + suf], en[k + ".hint" + suf] = p, e
            for suf, p, e in _variants(tip):
                pt[k + ".tip" + suf], en[k + ".tip" + suf] = p, e
    for eid, (p, e) in BESTIARY.items():
        k = "bestiary.isekai_mahou." + eid + ".desc"
        pt[k], en[k] = p, e
    save_json(os.path.join(LANG, "pt_br.json"), dict(sorted(pt.items())))
    save_json(os.path.join(LANG, "en_us.json"), dict(sorted(en.items())))
    return pt


CLASS_IDS = ["mage", "witch", "warlock", "druid", "necromancer", "smith", "rogue", "knight", "enchanter", "shinigami",
             "quincy", "gunslinger", "ranger", "gambler", "steelball"]
CATEGORY_IDS = ["magic", "physical", "dexterity"]
# Caixa de diálogo: ~5 linhas de ~60 caracteres (o resto é cortado com "…")
MAX_DIALOGUE = 300


def check_consistency():
    """Cada passo dos capitulos precisa de texto (e das variantes por classe/categoria de "hint_by"), cada capitulo com
    epilogo precisa das linhas e cada "intro" do JSON precisa da fala. Titulos e dicas curtas cabem no rastreador
    (painel de ~160 px com icone: ~26 caracteres; ~24 na rota da classe, que ganha um "✦ "); dicas e falas cabem na
    caixa de dialogo. v1.0: 15 rotas de classe (uma por classe, no maximo 10 passos para caber na aba)."""
    total = 0
    paths = [c["path"] for c in CLASS_CHAPTERS]
    if sorted(paths) != sorted(CLASS_IDS):
        raise SystemExit(f"rotas de classe: esperado {sorted(CLASS_IDS)}, veio {sorted(paths)}")
    for ch in CLASS_CHAPTERS:
        if len(ch["steps"]) > 10:
            raise SystemExit(f"{ch['id']}: {len(ch['steps'])} passos (maximo 10 na aba)")
        if not any(not s.get("optional") for s in ch["steps"]):
            raise SystemExit(f"{ch['id']}: sem passos obrigatorios")
        for s in ch["steps"]:
            title = CH_TEXT[ch["id"]]["steps"][s["id"]][0]
            for text in title:
                if len(text) > 24:
                    raise SystemExit(f"{ch['id']}.{s['id']}: titulo longo demais para o rastreador (rota): {text!r}")
    for ch in CHAPTERS + CLASS_CHAPTERS:
        total += len(ch["steps"])
        t = CH_TEXT[ch["id"]]
        ids = [s["id"] for s in ch["steps"]]
        missing = [s for s in ids if s not in t["steps"]]
        extra = [s for s in t["steps"] if s not in ids]
        if missing or extra:
            raise SystemExit(f"capitulo {ch['id']}: textos faltando {missing} / sobrando {extra}")
        if ch.get("epilogue", 0) != len(t.get("epilogue", [])):
            raise SystemExit(f"capitulo {ch['id']}: epilogo com {len(t.get('epilogue', []))} linhas, JSON pede {ch.get('epilogue', 0)}")
        if ch.get("intro") and "intro" not in t:
            raise SystemExit(f"capitulo {ch['id']}: \"intro\" sem fala do Espirito Guia")
        for text in t.get("intro", ()):
            if len(text) > MAX_DIALOGUE:
                raise SystemExit(f"{ch['id']}.intro: fala longa demais ({len(text)}): {text!r}")
        for s in ch["steps"]:
            title, _lore, hint, tip = t["steps"][s["id"]]
            for text in title:
                if len(text) > 26:
                    raise SystemExit(f"{ch['id']}.{s['id']}: titulo longo demais para o rastreador: {text!r}")
            for _suf, p, e in _variants(tip):
                for text in (p, e):
                    if len(text) > 26:
                        raise SystemExit(f"{ch['id']}.{s['id']}: dica curta longa demais: {text!r}")
            for _suf, p, e in _variants(hint):
                for text in (p, e):
                    if len(text) > MAX_DIALOGUE:
                        raise SystemExit(f"{ch['id']}.{s['id']}: dica longa demais para a caixa ({len(text)}): {text!r}")
            by = s.get("hint_by")
            if by:
                need = [""] + (CLASS_IDS if by == "path" else CATEGORY_IDS)
                for field, value in (("hint", hint), ("tip", tip)):
                    have = [suf.lstrip(".") for suf, _p, _e in _variants(value)]
                    lack = [n for n in need if n not in have]
                    if lack:
                        raise SystemExit(f"{ch['id']}.{s['id']}: {field} sem as variantes {lack}")
    for k, (p, e) in UI.items():
        if k.startswith("journal.isekai_mahou.tutorial.") and max(len(p), len(e)) > MAX_DIALOGUE:
            raise SystemExit(f"{k}: fala longa demais para a caixa")
    main = sum(len(c["steps"]) for c in CHAPTERS)
    if not 80 <= main <= 110:
        raise SystemExit(f"historia com {main} passos (esperado 80-110)")
    return total


if __name__ == "__main__":
    steps = check_consistency()
    guide_spirit()
    save_json(os.path.join(D, "journal/chapters.json"), {"chapters": CHAPTERS})
    save_json(os.path.join(D, "journal/classes.json"), {"chapters": CLASS_CHAPTERS})
    pt = build_lang()
    print(f"journal: modelo/texturas/animacoes do Espirito Guia, {len(CHAPTERS)} capitulos + {len(CLASS_CHAPTERS)} rotas "
          f"de classe ({steps} passos), {len(pt)} traducoes, {len(BESTIARY)} entradas de bestiario")
