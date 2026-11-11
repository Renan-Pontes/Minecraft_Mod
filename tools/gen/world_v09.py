"""Gera os dados do módulo "world_v09" (v0.9): estruturas novas do Overworld e o Veio de Mana.

Uso (na raiz do projeto):  py -I tools/gen/world_v09.py [--preview <estrutura>]

Gera:
  - moldes .nbt em data/isekai_mahou/structure/world_v09/<id>.nbt (um por estrutura, peça única de jigsaw)
  - worldgen: structure, structure_set (isekai_mahou:random_spread_multi longe das cidades), template_pool,
    processor_list; minério (configured/placed feature) e aglomerados perto de santuários + biome modifiers
  - loot tables dos baús (chests/<id>) e dos blocos; tags de estrutura (#isekai_mahou:boss_lair, portal_site,
    mana_crystal_sites e uma por estrutura) e de bioma (has_structure/<id>) no merge do módulo
  - texturas/modelos/blockstates dos blocos (base de pedra/ardósia lida do jar do Minecraft, se existir)
  - traduções (src/main/lang/world_v09/{pt_br,en_us}.json) — inclusive as dos letreiros dos moldes
No fim CONFERE os moldes: pedra de teleporte com 2 de ar em cima, chefe nos covis, molduras de portal válidas
(mesma regra do FrameShape), Farol sobre ouro com céu livre, Altar de Ascensão completo com {PublicAltar:1b} e
todas as chaves dos letreiros traduzidas. Qualquer erro = sai com código 1.

Convenções dos moldes: x = leste, z = sul, y = cima; a entrada fica no SUL (o jigsaw gira a peça ao acaso).
"ground" de cada estrutura = primeira camada de AR onde se pisa (V09Site.ground no Java usa o mesmo número).
"""
import glob
import json
import math
import os
import random
import re
import struct
import sys
import zipfile
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from structgen import Structure, Byte, Double, Float, Int  # noqa: E402
from pixelart import Canvas, mix, lighten, darken  # noqa: E402

NS = "isekai_mahou"
RES = os.path.join(ROOT, "src/main/resources")
D = os.path.join(RES, "data", NS)
A = os.path.join(RES, "assets", NS)
MERGE = os.path.join(ROOT, "src/main/merge/world_v09")
LANG = os.path.join(ROOT, "src/main/lang/world_v09")
STRUCT_DIR = os.path.join(D, "structure/world_v09")

ERRORS = []


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =================================================================================================
#  TRADUÇÕES (pt_br, en_us) — tudo que o módulo mostra
# =================================================================================================
LANG_PT = {}
LANG_EN = {}


def tr(key, pt, en):
    LANG_PT[key] = pt
    LANG_EN[key] = en


SITES = ["hydra_lair", "wyvern_peak", "basilisk_tomb", "colossus_ruins", "elemental_gate", "abyssal_chasm",
         "celestial_stairway", "twilight_rupture", "ascension_sanctum"]
LAIRS = SITES[:4]
PORTALS = SITES[4:8]

NAMES = {
    "hydra_lair": ("Covil da Hidra do Pântano", "Swamp Hydra's Lair"),
    "wyvern_peak": ("Ninho do Rei Wyvern", "Wyvern King's Peak"),
    "basilisk_tomb": ("Tumba do Basilisco", "Basilisk Tomb"),
    "colossus_ruins": ("Ruínas do Colosso", "Colossus Ruins"),
    "elemental_gate": ("Portão Elemental", "Elemental Gate"),
    "abyssal_chasm": ("Fenda Abissal", "Abyssal Chasm"),
    "celestial_stairway": ("Escadaria Celeste", "Celestial Stairway"),
    "twilight_rupture": ("Ruptura do Crepúsculo", "Twilight Rupture"),
    "ascension_sanctum": ("Santuário de Ascensão", "Ascension Sanctum"),
}
HINTS = {
    "hydra_lair": ("O pântano borbulha... a Hidra dorme na ilha do meio. Fogo impede as cabeças de crescerem de novo.",
                   "The bog bubbles... the Hydra sleeps on the central isle. Fire stops its heads from growing back."),
    "wyvern_peak": ("Raios estalam sobre o ninho. O Rei Wyvern vigia os picos — fique longe da água na tempestade.",
                    "Lightning crackles over the nest. The Wyvern King rules the peaks — stay dry in the storm."),
    "basilisk_tomb": ("Não encare o Basilisco: o olhar dele petrifica. Quebre o contato visual atrás das colunas.",
                      "Do not stare at the Basilisk: its gaze petrifies. Break line of sight behind the pillars."),
    "colossus_ruins": ("Um gigante de pedra dorme no centro da arena. Quando ele erguer o punho, saia da sombra!",
                       "A stone giant sleeps in the middle of the arena. When it raises its fist, leave the shadow!"),
    "elemental_gate": ("A moldura elemental dorme. Círculo 3: clique nela com um Cristal de Mana (e 50 de mana).",
                       "The elemental frame sleeps. Circle 3: use a Mana Crystal on it (plus 50 mana)."),
    "abyssal_chasm": ("Lá no fundo, a moldura sombria espera. Círculo 6: ofereça uma Essência de Sombra.",
                      "Down below, the shadow frame waits. Circle 6: offer an Essence of Shadow."),
    "celestial_stairway": ("O Farol repousa sobre o ouro. Círculo 6: ative-o ao MEIO-DIA com uma Essência de Luz.",
                           "The Beacon rests on gold. Circle 6: light it at NOON with an Essence of Light."),
    "twilight_rupture": ("A Fenda só deixa passar quem venceu o Soberano do Abismo e o Serafim (Círculo 9).",
                         "The Rift only lets through those who beat the Abyss Sovereign and the Seraph (Circle 9)."),
    "ascension_sanctum": ("Altar de Ascensão público: ponha o tributo do seu Círculo nos pedestais e ative o núcleo.",
                          "Public Ascension Altar: put your Circle's tribute on the pedestals and activate the core."),
}
for _s in SITES:
    tr(f"structure.{NS}.{_s}", *NAMES[_s])
    tr(f"message.{NS}.world_v09.hint.{_s}", *HINTS[_s])

tr(f"block.{NS}.mana_vein_ore", "Veio de Mana", "Mana Vein")
tr(f"block.{NS}.deepslate_mana_vein_ore", "Veio de Mana de Ardósia", "Deepslate Mana Vein")
tr(f"block.{NS}.mana_crystal_cluster", "Aglomerado de Cristal de Mana", "Mana Crystal Cluster")
tr(f"command.{NS}.world_v09.sites.header", "Estruturas mais próximas (clique para sugerir o /tp — só funciona no criativo; no jogo normal, use as Pedras de Teleporte):",
   "Nearest structures (click to suggest /tp — creative only; in normal play, use the Teleport Stones):")
tr(f"command.{NS}.world_v09.sites.found", " — %s, %s (%s blocos)", " — %s, %s (%s blocks)")
tr(f"command.{NS}.world_v09.sites.none", " — nenhuma por perto (ou não gera neste mundo)",
   " — none nearby (or it does not generate in this world)")
tr(f"command.{NS}.world_v09.sites.unknown", "Estrutura desconhecida: %s", "Unknown structure: %s")

# Letreiros dos moldes: cada placa = lista de linhas (máx. ~15 letras por linha).
SIGNS = {
    "hydra_lair.gate": [("~ Covil da ~", "~ Lair of the ~"), ("Hidra do", "Swamp"), ("Pântano", "Hydra"),
                        ("Volte vivo!", "Come back alive!")],
    "hydra_lair.loot": [("Oferendas", "Offerings"), ("dos que", "of those who"), ("não voltaram", "never returned")],
    "wyvern_peak.gate": [("~ Ninho do ~", "~ Nest of the ~"), ("Rei Wyvern", "Wyvern King"), ("Cuidado com", "Beware of"),
                         ("os raios!", "the lightning!")],
    "basilisk_tomb.gate": [("~ Tumba do ~", "~ Tomb of the ~"), ("Basilisco", "Basilisk"), ("Não olhe nos", "Do not meet"),
                           ("olhos dele!", "its eyes!")],
    "basilisk_tomb.hall": [("Eles olharam.", "They looked."), ("Viraram pedra.", "Turned to stone.")],
    "colossus_ruins.gate": [("~ Ruínas do ~", "~ Ruins of the ~"), ("Colosso", "Colossus"), ("O gigante", "The giant"),
                            ("só dorme.", "only sleeps.")],
    "elemental_gate.gate": [("~ Portão ~", "~ Elemental ~"), ("Elemental", "Gate"), ("Círculo 3 +", "Circle 3 +"),
                            ("Cristal de Mana", "Mana Crystal")],
    "elemental_gate.frame": [("Toque a moldura", "Touch the frame"), ("com um Cristal", "with a Mana"),
                             ("de Mana.", "Crystal.")],
    "abyssal_chasm.gate": [("~ Fenda ~", "~ Abyssal ~"), ("Abissal", "Chasm"), ("A trilha desce", "The path goes"),
                           ("pela direita", "down the side")],
    "abyssal_chasm.frame": [("Círculo 6 +", "Circle 6 +"), ("Essência de", "Essence of"), ("Sombra", "Shadow"),
                            ("na moldura", "on the frame")],
    "celestial_stairway.gate": [("~ Escadaria ~", "~ Celestial ~"), ("Celeste", "Stairway"), ("Suba até", "Climb up to"),
                                ("o Farol", "the Beacon")],
    "celestial_stairway.beacon": [("Ao MEIO-DIA,", "At NOON,"), ("Essência de", "Essence of"), ("Luz no Farol", "Light on"),
                                  ("(Círculo 6)", "the Beacon (C6)")],
    "twilight_rupture.gate": [("~ Ruptura do ~", "~ Twilight ~"), ("Crepúsculo", "Rupture"), ("Círculo 9 e", "Circle 9 and"),
                              ("os 2 núcleos", "both cores")],
    "twilight_rupture.light": [("Luz:", "Light:"), ("vença o", "defeat the"), ("Serafim", "Seraph")],
    "twilight_rupture.shadow": [("Sombra:", "Shadow:"), ("vença o", "defeat the"), ("Soberano", "Sovereign")],
    "ascension_sanctum.gate": [("~ Santuário ~", "~ Ascension ~"), ("de Ascensão", "Sanctum"), ("Altar público", "Public altar"),
                               ("Traga o tributo", "Bring a tribute")],
}
for _k, _lines in SIGNS.items():
    for _i, (_pt, _en) in enumerate(_lines, 1):
        tr(f"sign.{NS}.world_v09.{_k}.{_i}", _pt, _en)


# =================================================================================================
#  ESTADOS DE BLOCO
# =================================================================================================
AIR = "minecraft:air"
WATER = "minecraft:water[level=0]"
DIRV = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}

TRAVEL_STONE = f"{NS}:travel_stone"
EL_FRAME = f"{NS}:elemental_portal_frame"
AB_FRAME = f"{NS}:abyss_portal_frame"
BEACON = f"{NS}:celestial_beacon"
RIFT = f"{NS}:twilight_rift"
CLUSTER = f"{NS}:mana_crystal_cluster[facing=up,waterlogged=false]"
ASC_CORE = f"{NS}:ascension_core"
ASC_PEDESTAL = f"{NS}:ascension_pedestal"
RUNE_STONE = f"{NS}:rune_stone"
# Padrão do AscensionPattern (igual ao das cidades): pedestais em cruz a 2 e diagonais a 3; Pedras Rúnicas nas
# diagonais a 2, em cruz a 3 e no anel (4,±2)/(±2,4). Cantos (±4,±4) sem cristal (v0.9).
ALTAR_PEDESTALS = [(2, 0), (-2, 0), (0, 2), (0, -2), (3, 3), (3, -3), (-3, 3), (-3, -3)]
ALTAR_RUNES = [(2, 2), (2, -2), (-2, 2), (-2, -2), (3, 0), (-3, 0), (0, 3), (0, -3),
               (4, 2), (4, -2), (-4, 2), (-4, -2), (2, 4), (-2, 4), (2, -4), (-2, -4)]
# Raio da arena (BossKit.setArena): o chefe do bosses_f volta para casa a raio+3 — tem que caber a luta inteira
# (padrão do bosses_f: 40, Wyvern voador 52). Raio pequeno = chefe "voltando" no meio da luta = kite fácil.
BOSSES = {"hydra_lair": ("swamp_hydra", 38.0), "wyvern_peak": ("wyvern_king", 49.0),
          "basilisk_tomb": ("desert_basilisk", 34.0), "colossus_ruins": ("ruin_colossus", 37.0)}


def mc(n):
    return n if ":" in n else "minecraft:" + n


def st(name, **props):
    name = mc(name)
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


def stairs(block, facing, half="bottom", shape="straight"):
    return st(block, facing=facing, half=half, shape=shape, waterlogged="false")


def slab(block, t="bottom"):
    return st(block, type=t, waterlogged="false")


def log(block, axis="y"):
    return st(block, axis=axis)


def lantern(hanging=False, soul=False):
    return st("soul_lantern" if soul else "lantern", hanging="true" if hanging else "false", waterlogged="false")


def campfire(lit=True, soul=False, facing="north"):
    return st("soul_campfire" if soul else "campfire", facing=facing, lit="true" if lit else "false",
              signal_fire="false", waterlogged="false")


def candle(n=1, color=None, lit=True):
    return st(f"{color}_candle" if color else "candle", candles=n, lit="true" if lit else "false", waterlogged="false")


def chain(axis="y"):
    return st("chain", axis=axis, waterlogged="false")


def skull(rot=0, kind="skeleton_skull"):
    return st(kind, rotation=rot)


def outward(dx, dz):
    """Direção horizontal dominante de (dx, dz)."""
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


# =================================================================================================
#  RUÍDO E GEOMETRIA
# =================================================================================================
def _hash(ix, iz, seed):
    n = (ix * 374761393 + iz * 668265263 + seed * 982451653) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, z, scale, seed):
    """Ruído de valor suave em [0, 1]."""
    fx, fz = x / scale, z / scale
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = fx - ix, fz - iz
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a, b = _hash(ix, iz, seed), _hash(ix + 1, iz, seed)
    c, d = _hash(ix, iz + 1, seed), _hash(ix + 1, iz + 1, seed)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def disk(cx, cz, r):
    for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 2):
        for z in range(int(math.floor(cz - r)) - 1, int(math.ceil(cz + r)) + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                yield x, z, d


def ring_points(cx, cz, r, n, phase=0.0):
    out = []
    for i in range(n):
        a = phase + 2 * math.pi * i / n
        out.append((int(round(cx + r * math.cos(a))), int(round(cz + r * math.sin(a))), a))
    return out


# =================================================================================================
#  CONSTRUTOR
# =================================================================================================
class S(Structure):
    def __init__(self, name, sx, sy, sz, ground):
        super().__init__(sx, sy, sz)
        self.name = name
        self.ground = ground
        self.rnd = random.Random("world_v09/" + name)

    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def soft(self, x, y, z, state, nbt=None):
        if self.get(x, y, z) is None:
            self.set(x, y, z, state, nbt)

    def pick(self, weighted):
        tot = sum(w for _, w in weighted)
        v = self.rnd.random() * tot
        for s, w in weighted:
            v -= w
            if v <= 0:
                return s
        return weighted[-1][0]

    def column(self, x, z, y0, y1, state):
        for y in range(y0, y1 + 1):
            self.set(x, y, z, state)

    def clear(self, cells, y0, y1):
        """Ar explícito (limpa terreno/árvores) só onde nada foi definido."""
        for x, z in cells:
            for y in range(y0, y1 + 1):
                if self.inside(x, y, z) and self.get(x, y, z) is None:
                    self.set(x, y, z, AIR)

    def chest(self, x, y, z, site, facing="south", suffix=""):
        self.set(x, y, z, st("chest", facing=facing, type="single", waterlogged="false"),
                 nbt={"LootTable": f"{NS}:chests/{site}{suffix}"})

    def sign(self, x, y, z, key, rotation=0, wood="dark_oak", wall=None, color="black", glow=False):
        lines = SIGNS[key]
        msgs = [json.dumps({"translate": f"sign.{NS}.world_v09.{key}.{i}"}) for i in range(1, len(lines) + 1)]
        msgs += ['""'] * (4 - len(msgs))
        state = st(f"{wood}_wall_sign", facing=wall, waterlogged="false") if wall else \
            st(f"{wood}_sign", rotation=rotation, waterlogged="false")
        self.set(x, y, z, state, nbt={
            "front_text": {"messages": msgs, "color": color, "has_glowing_text": Byte(1 if glow else 0)},
            "back_text": {"messages": msgs, "color": color, "has_glowing_text": Byte(1 if glow else 0)},
            "is_waxed": Byte(1)})

    def banner(self, x, y, z, state, patterns):
        self.set(x, y, z, state, nbt={"patterns": [{"pattern": mc(p), "color": c} for p, c in patterns]})

    def mob(self, x, y, z, eid, yaw=0.0, extra=None):
        nbt = {"PersistenceRequired": Byte(1), "Rotation": [Float(yaw), Float(0.0)]}
        if extra:
            nbt.update(extra)
        self.entity(x, y, z, mc(eid), nbt)

    def boss(self, x, y, z, yaw=0.0):
        eid, radius = BOSSES[self.name]
        self.mob(x, y, z, f"{NS}:{eid}", yaw, {
            "Tags": ["isekai_mahou.lair_boss"],
            "NeoForgeData": {"isekai_mahou.lair": {"r": Double(radius), "site": self.name}}})

    def travel_stone(self, x, y, z, plinth, ring=None):
        """Pedra de Teleporte (bloco do módulo travel) sobre um plinto, com 2 blocos de ar livres em cima."""
        self.set(x, y - 1, z, plinth)
        self.set(x, y, z, TRAVEL_STONE)
        self.set(x, y + 1, z, AIR)
        self.set(x, y + 2, z, AIR)
        if ring:
            for dx, dz in ((-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)):
                self.set(x + dx, y - 1, z + dz, ring)
                if self.get(x + dx, y, z + dz) is None:
                    self.set(x + dx, y, z + dz, AIR)

    def lamp_post(self, x, y, z, post="minecraft:stone_brick_wall", h=2, soul=False):
        for dy in range(h):
            self.set(x, y + dy, z, post)
        self.set(x, y + h, z, lantern(soul=soul))


# ---------------------------------------------------------------- conexões de cercas/painéis/muros
NON_FULL = ("slab", "stairs", "fence", "pane", "bars", "lantern", "torch", "carpet", "button", "plate", "chain",
            "candle", "rod", "cluster", "bud", "roots", "vine", "sign", "banner", "pot", "chest", "campfire",
            "bell", "water", "lava", "air", "door", "ladder", "bed", "skull", "head", "rail", "snow", "egg",
            "cobweb", "leaves", "cauldron", "anvil", "lectern", "dripstone", "flower", "poppy", "dandelion", "tulip",
            "allium", "orchid", "daisy", "cornflower", "lily", "bush", "grass", "fern", "sapling", "mushroom",
            "jigsaw", "spawner", "wall", "propagule", "sea_pickle", "end_rod", "glass", "portal", "rift", "beacon",
            "travel_stone", "pedestal", "fire", "azalea", "pad")


def base_name(state):
    return state.split("[", 1)[0] if state else ""


def is_full(state):
    if not state:
        return False
    n = base_name(state).split(":", 1)[1]
    if n in ("mud_bricks",):
        return True
    if n.endswith("_glass") or n == "glass":
        return True
    return not any(k in n for k in NON_FULL)


def is_wall(n):
    n = n.split(":", 1)[1] if ":" in n else n
    return n.endswith("_wall") and not any(k in n for k in ("banner", "torch", "sign", "skull", "head", "fan"))


def is_pane(n):
    return n.endswith("_pane") or n == "minecraft:iron_bars"


def with_props(state, props):
    name = base_name(state)
    cur = {}
    if "[" in state:
        for kv in state[:-1].split("[", 1)[1].split(","):
            if kv:
                k, v = kv.split("=")
                cur[k] = v
    cur.update(props)
    return name + "[" + ",".join(f"{k}={v}" for k, v in cur.items()) + "]"


def fix_connections(s):
    """Peças de jigsaw não recalculam formas: liga muros e painéis aqui."""
    for (x, y, z), (state, nbt) in list(s.blocks.items()):
        n = base_name(state)
        wal, pan = is_wall(n), is_pane(n)
        if not (wal or pan):
            continue
        props, con = {}, {}
        for d, (dx, dz) in DIRV.items():
            ns = s.get(x + dx, y, z + dz)
            nn = base_name(ns) if ns else ""
            ok = bool(ns) and (is_wall(nn) or is_pane(nn) or is_full(ns))
            props[d] = ("low" if ok else "none") if wal else ("true" if ok else "false")
            con[d] = ok
        if wal:
            above = s.get(x, y + 1, z)
            straight = (con["north"] and con["south"] and not con["east"] and not con["west"]) or \
                       (con["east"] and con["west"] and not con["north"] and not con["south"])
            props["up"] = "true" if (above and above != AIR) or not straight else "false"
        props["waterlogged"] = "false"
        s.blocks[(x, y, z)] = (with_props(state, props), nbt)


BUILT = []


def save(s):
    fix_connections(s)
    n = s.save(os.path.join(STRUCT_DIR, s.name + ".nbt"), keep_air=True)
    BUILT.append(s)
    return n


# =================================================================================================
#  COVIL DA HIDRA DO PÂNTANO — lagoa rasa num templo afundado; a Hidra dorme na ilha do meio
#  (start_height 0, beard_thin: y=0 fundo da lagoa/chão, y=1 margem e água, pisa-se em y=2)
# =================================================================================================
def ribcage(s, x0, z0, y, length):
    """Esqueleto de uma presa gigante meio afundado: espinha + costelas em arco + crânio."""
    for i in range(length):
        s.set(x0 + i, y, z0, st("bone_block", axis="x"))
        if i % 2 == 1 and 1 <= i <= length - 2:
            for side in (-1, 1):
                s.set(x0 + i, y + 1, z0 + 2 * side, st("bone_block", axis="y"))
                s.set(x0 + i, y + 2, z0 + 2 * side, st("bone_block", axis="y"))
                s.set(x0 + i, y + 3, z0 + side, st("bone_block", axis="z"))
    hx = x0 + length
    for dz in (-1, 0, 1):
        s.set(hx, y, z0 + dz, st("bone_block", axis="x"))
        s.set(hx + 1, y, z0 + dz, st("bone_block", axis="x"))
    s.set(hx, y + 1, z0, st("bone_block", axis="y"))
    s.set(hx + 1, y + 1, z0 - 1, st("bone_block", axis="y"))
    s.set(hx + 1, y + 1, z0 + 1, st("bone_block", axis="y"))


def vines_on(s, x, y_top, y_bot, z):
    """Cipós descendo pelas faces livres de uma coluna."""
    for face, (dx, dz) in DIRV.items():
        if s.rnd.random() < 0.45:
            vx, vz = x + dx, z + dz
            prop = OPP[face]
            for y in range(y_top, max(y_bot, y_top - s.rnd.randint(2, 5)) - 1, -1):
                if s.inside(vx, y, vz) and s.get(vx, y, vz) is None:
                    s.set(vx, y, vz, st("vine", **{prop: "true"}))


def hydra_lair():
    N, H = 47, 24
    s = S("hydra_lair", N, H, N, ground=2)
    r = s.rnd
    c = 23
    Y0, YR = 0, 1
    R_OUT, R_POOL, R_ISLE = 22.5, 17.0, 6.0
    foot = []
    for x, z, d in disk(c, c, R_OUT + 1):
        nd = d + (vnoise(x, z, 4.0, 11) - 0.5) * 3.0
        if nd > R_OUT or not s.inside(x, 0, z):
            continue
        foot.append((x, z))
        if d <= R_ISLE:
            continue
        if nd < R_POOL:
            s.set(x, Y0, z, s.pick([("minecraft:mud", 6), ("minecraft:packed_mud", 2), ("minecraft:mud_bricks", 1),
                                    (st("verdant_froglight", axis="y"), 0.45), ("minecraft:clay", 1)]))
            s.set(x, YR, z, WATER)
        elif nd < R_POOL + 1.3:
            s.set(x, Y0, z, "minecraft:mud_bricks")
            s.set(x, YR, z, s.pick([("minecraft:mossy_stone_bricks", 5), ("minecraft:cracked_stone_bricks", 2),
                                    ("minecraft:mud_bricks", 2)]))
        else:
            s.set(x, Y0, z, "minecraft:mud")
            s.set(x, YR, z, s.pick([("minecraft:moss_block", 4), ("minecraft:mud", 4), ("minecraft:packed_mud", 1),
                                    (st("muddy_mangrove_roots", axis="y"), 1), ("minecraft:mossy_cobblestone", 1)]))
    # ilha central (a Hidra fica no meio; o anel externo é baixo para ela entrar e sair da água)
    for x, z, d in disk(c, c, R_ISLE):
        s.set(x, Y0, z, "minecraft:mud")
        s.set(x, YR, z, s.pick([("minecraft:mossy_stone_bricks", 4), ("minecraft:mud_bricks", 3),
                                ("minecraft:moss_block", 2), ("minecraft:cracked_stone_bricks", 1)]))
        if d <= 4.2:
            ring = abs(d - 3.0) < 0.6
            s.set(x, YR + 1, z, "minecraft:chiseled_stone_bricks" if ring and (x + z) % 2 == 0 else
                  s.pick([("minecraft:mossy_stone_bricks", 3), ("minecraft:moss_block", 2), ("minecraft:mud_bricks", 2)]))
    s.set(c, YR + 1, c, st("verdant_froglight", axis="y"))
    for px, pz, _a in ring_points(c, c, 5.3, 6, 0.3):
        h = r.randint(1, 3)
        s.column(px, pz, YR + 1, YR + h, s.pick([("minecraft:mossy_stone_bricks", 3), ("minecraft:cracked_stone_bricks", 1)]))
        s.set(px, YR + h + 1, pz, skull(r.randrange(16)) if r.random() < 0.6 else candle(r.randint(2, 4), "green"))
    # anel de colunas quebradas do templo afundado (três tombadas ao longo da margem)
    for i, (px, pz, a) in enumerate(ring_points(c, c, 19.6, 12, 0.13)):
        if i in (2, 7, 10):
            tdir = outward(-math.sin(a), math.cos(a))
            dx, dz = DIRV[tdir]
            for k in range(5):
                for w in (0, 1):
                    bx = px + dx * k + (w if dz else 0)
                    bz = pz + dz * k + (w if dx else 0)
                    if s.inside(bx, YR + 1, bz):
                        s.set(bx, YR + 1, bz, s.pick([("minecraft:mossy_stone_bricks", 3), ("minecraft:stone_bricks", 1),
                                                      ("minecraft:cracked_stone_bricks", 1)]))
            continue
        h = r.randint(3, 10)
        for ox in (0, 1):
            for oz in (0, 1):
                s.set(px + ox, YR, pz + oz, "minecraft:mossy_stone_bricks")
                top = YR + h - (1 if ox + oz == 2 and r.random() < 0.5 else 0)
                for y in range(YR + 1, top + 1):
                    s.set(px + ox, y, pz + oz, s.pick([("minecraft:mossy_stone_bricks", 4), ("minecraft:stone_bricks", 2),
                                                       ("minecraft:cracked_stone_bricks", 2)]))
        if h >= 8:
            for ox in (0, 1):
                for oz in (0, 1):
                    s.set(px + ox, YR + 1 + h, pz + oz, "minecraft:chiseled_stone_bricks")
        for ox in (0, 1):
            for oz in (0, 1):
                vines_on(s, px + ox, YR + h, YR + 1, pz + oz)
    # costelas de uma presa gigante na água (nordeste)
    ribcage(s, c + 5, c - 9, YR, 7)
    # tesouro: santuário partido ao norte
    for x in range(c - 4, c + 5):
        for z in range(2, 8):
            s.set(x, YR, z, s.pick([("minecraft:mossy_stone_bricks", 3), ("minecraft:cracked_stone_bricks", 1)]))
            s.set(x, Y0, z, "minecraft:mud_bricks")
            for y in range(YR + 1, YR + 5):
                if s.get(x, y, z) is not None and z > 2:
                    s.set(x, y, z, AIR)
        top = YR + (4 if abs(x - c) <= 1 else r.randint(2, 4))
        for y in range(YR + 1, top + 1):
            s.set(x, y, 2, "minecraft:mossy_stone_bricks" if (x + y) % 3 else "minecraft:cracked_stone_bricks")
    for y in range(YR + 1, YR + 4):
        s.set(c - 4, y, 3, "minecraft:mossy_stone_bricks")
        s.set(c + 4, y, 3, "minecraft:mossy_stone_bricks")
    s.set(c, YR + 3, 2, AIR)  # janela partida
    s.chest(c - 2, YR + 1, 4, "hydra_lair", "south")
    s.chest(c + 2, YR + 1, 4, "hydra_lair", "south")
    s.set(c, YR + 1, 4, "minecraft:mossy_stone_brick_wall")
    s.set(c, YR + 2, 4, skull(8))
    for x in (c - 1, c + 1):
        s.set(x, YR + 1, 3, candle(r.randint(2, 4), "green"))
    s.sign(c, YR + 1, 6, "hydra_lair.loot", rotation=0, wood="mangrove")
    for x in range(c - 3, c + 4):
        if s.get(x, YR + 1, 5) is None and r.random() < 0.6:
            s.set(x, YR + 1, 5, "minecraft:moss_carpet")
    # passarela do sul até a ilha + portal de entrada
    for z in range(c + 6, N):
        for x in (c - 1, c, c + 1):
            s.set(x, YR, z, s.pick([("minecraft:mossy_stone_bricks", 4), ("minecraft:cracked_stone_bricks", 2),
                                    ("minecraft:mud_bricks", 2)]))
            s.set(x, Y0, z, "minecraft:mud")
            if z > c + 7:
                s.set(x, YR + 1, z, AIR)
        if z % 4 == 0 and c + 8 <= z < N - 4:
            for x in (c - 2, c + 2):
                s.set(x, YR, z, "minecraft:mossy_stone_bricks")
                s.set(x, Y0, z, "minecraft:mud")
                s.lamp_post(x, YR + 1, z, "minecraft:mossy_stone_brick_wall", 1)
    gz = c + 19
    for gx in (c - 3, c + 3):
        s.set(gx, YR, gz, "minecraft:chiseled_stone_bricks")
        s.set(gx, Y0, gz, "minecraft:mud_bricks")
        s.column(gx, gz, YR + 1, YR + 6, "minecraft:mossy_stone_bricks")
        s.set(gx, YR + 8, gz, skull(0))
        s.banner(gx, YR + 4, gz + 1, st("green_wall_banner", facing="south"),
                 [("skull", "black"), ("border", "black"), ("gradient", "lime")])
    for x in range(c - 3, c + 4):
        s.set(x, YR + 7, gz, "minecraft:chiseled_stone_bricks" if x == c else "minecraft:mossy_stone_bricks")
    s.set(c, YR + 6, gz, lantern(hanging=True))
    s.set(c + 2, YR, c + 21, "minecraft:mossy_stone_bricks")
    s.sign(c + 2, YR + 1, c + 21, "hydra_lair.gate", rotation=0, wood="mangrove")
    # Pedra de Teleporte na chegada
    s.travel_stone(c - 4, YR + 1, N - 3, "minecraft:chiseled_stone_bricks", ring="minecraft:mossy_stone_bricks")
    for (dx, dz) in ((-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 0), (0, 1), (1, -1), (1, 0), (1, 1)):
        s.set(c - 4 + dx, Y0, N - 3 + dz, "minecraft:mud")
    s.lamp_post(c - 6, YR + 1, N - 3, "minecraft:mossy_stone_brick_wall", 1)
    s.set(c - 6, YR, N - 3, "minecraft:mossy_stone_bricks")
    # margem: raízes, ossos, musgo; lagoa: vitórias-régias
    for x, z in foot:
        d = math.hypot(x - c, z - c)
        if s.get(x, YR + 1, z) is not None:
            continue
        if s.get(x, YR, z) == WATER:
            if r.random() < 0.10:
                s.set(x, YR + 1, z, "minecraft:lily_pad")
        elif d > R_POOL + 1.3:
            v = r.random()
            if v < 0.05:
                s.set(x, YR + 1, z, "minecraft:mangrove_roots[waterlogged=false]")
                if r.random() < 0.5:
                    s.set(x, YR + 2, z, "minecraft:mangrove_roots[waterlogged=false]")
            elif v < 0.08:
                s.set(x, YR + 1, z, st("bone_block", axis=r.choice("xyz")))
            elif v < 0.10:
                s.set(x, YR + 1, z, skull(r.randrange(16)))
            elif v < 0.22:
                s.set(x, YR + 1, z, "minecraft:moss_carpet")
            elif v < 0.26:
                s.set(x, YR + 1, z, "minecraft:short_grass" if r.random() < 0.6 else "minecraft:fern")
    # guardas: dois slimes do pântano na passarela (esqueletos "bogged" queimariam no 1º dia: o covil é a céu aberto)
    s.mob(c - 1 + 0.5, YR + 1, c + 15 + 0.5, "minecraft:slime", YAW["south"], {"Size": Int(1)})
    s.mob(c + 1 + 0.5, YR + 1, c + 11 + 0.5, "minecraft:slime", YAW["south"], {"Size": Int(1)})
    s.boss(c + 0.5, YR + 2, c + 0.5, YAW["south"])
    s.clear(foot, YR + 1, H - 1)
    save(s)


# =================================================================================================
#  NINHO DO REI WYVERN — coroa de pedra no topo do pico sobre um pilar de rocha; ninho de troncos no meio
#  (start_height -14, sem adaptação de terreno: y=14 é o piso, pisa-se em y=15)
# =================================================================================================
def spire(s, px, y0, pz, h, broken=False):
    r = s.rnd
    for k in range(h):
        y = y0 + k
        if k < 4:
            cells = [(dx, dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
        elif k < 8:
            cells = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]
        else:
            cells = [(0, 0)]
        for dx, dz in cells:
            if broken and k >= h - 2 and r.random() < 0.5:
                continue
            mat = "minecraft:chiseled_stone_bricks" if k == 0 and abs(dx) + abs(dz) == 2 else \
                "minecraft:polished_andesite" if k == 4 else \
                "minecraft:deepslate_tiles" if k >= 8 else \
                s.pick([("minecraft:stone_bricks", 4), ("minecraft:cracked_stone_bricks", 1), ("minecraft:mossy_stone_bricks", 1)])
            s.set(px + dx, y, pz + dz, mat)
    if not broken:
        s.set(px, y0 + h, pz, "minecraft:weathered_cut_copper")
        s.set(px, y0 + h + 1, pz, st("lightning_rod", facing="up", powered="false", waterlogged="false"))
    else:
        for _ in range(5):
            s.soft(px + r.randint(-3, 3), y0, pz + r.randint(-3, 3),
                   r.choice(["minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:mossy_cobblestone"]))
    s.banner(px, y0 + 3, pz + 2, st("light_blue_wall_banner", facing="south"),
             [("guster", "white"), ("triangles_bottom", "blue"), ("border", "blue")])


def wyvern_peak():
    N, H, G = 41, 38, 14
    s = S("wyvern_peak", N, H, N, ground=G + 1)
    r = s.rnd
    c = 20
    R = 19.0
    rock = [("minecraft:stone", 6), ("minecraft:andesite", 3), ("minecraft:cobblestone", 2), ("minecraft:tuff", 2),
            ("minecraft:packed_ice", 0.3)]
    # pilar de rocha afunilado: some dentro da montanha e segura a coroa quando a encosta é íngreme
    for y in range(G):
        t = y / G
        rr = 6.0 + (t ** 1.5) * (R - 5.0)
        for x, z, d in disk(c, c, rr + 2):
            nd = d + (vnoise(x + y * 3.1, z - y * 1.7, 3.5, 23) - 0.5) * 3.0
            if nd <= rr and s.inside(x, y, z):
                s.set(x, y, z, s.pick(rock))
    plat = []
    for x, z, d in disk(c, c, R + 0.5):
        nd = d + (vnoise(x, z, 4.0, 21) - 0.5) * 2.6
        if nd > R or not s.inside(x, G, z):
            continue
        plat.append((x, z))
        if d <= 7.6:
            floor = "minecraft:hay_block[axis=y]" if d < 3.5 else s.pick([("minecraft:coarse_dirt", 3), ("minecraft:podzol", 2),
                                                                         ("minecraft:hay_block[axis=y]", 1)])
        elif abs(d - 11.0) < 0.7 or abs(d - 15.5) < 0.6:
            floor = "minecraft:polished_andesite"
        else:
            floor = s.pick([("minecraft:stone_bricks", 6), ("minecraft:cracked_stone_bricks", 2),
                            ("minecraft:mossy_stone_bricks", 1), ("minecraft:andesite", 1), ("minecraft:packed_ice", 0.4)])
        s.set(x, G, z, floor)
        if s.get(x, G - 1, z) is None and d < R - 0.8:
            s.set(x, G - 1, z, "minecraft:stone_bricks")
        # parapeito partido (vãos livres nos 4 lados)
        ang = math.degrees(math.atan2(z - c, x - c)) % 90
        if d >= R - 1.3 and 12 < ang < 78 and r.random() < 0.65:
            s.set(x, G + 1, z, "minecraft:stone_brick_wall" if r.random() < 0.75 else "minecraft:mossy_stone_brick_wall")
    # torres com para-raios nas diagonais (uma caída)
    for i, (px, pz, _a) in enumerate(ring_points(c, c, 14.5, 4, math.pi / 4)):
        spire(s, px, G + 1, pz, r.randint(11, 15) if i != 2 else 6, broken=(i == 2))
    # ninho: tigela de troncos com uma abertura ao sul
    logs = ["minecraft:spruce_log", "minecraft:dark_oak_log", "minecraft:stripped_spruce_log", "minecraft:stripped_dark_oak_log"]
    for x, z, d in disk(c, c, 7.6):
        if d < 5.0 or (x in (c - 1, c, c + 1) and z > c):
            continue
        s.set(x, G + 1, z, log(r.choice(logs), r.choice("xz")))
        if d >= 5.8 and r.random() < 0.8:
            s.set(x, G + 2, z, log(r.choice(logs), r.choice("xz")))
        if d >= 6.6 and r.random() < 0.45:
            s.set(x, G + 3, z, st("bone_block", axis=r.choice("xz")) if r.random() < 0.2 else log(r.choice(logs), r.choice("xz")))
    for ex, ez in ((c - 2, c - 1), (c + 2, c + 1), (c - 1, c + 2)):
        s.set(ex, G + 1, ez, st("sniffer_egg", hatch=0))
    s.set(c + 3, G + 1, c - 2, "minecraft:raw_gold_block")
    s.set(c - 3, G + 1, c + 1, "minecraft:gold_block")
    s.set(c + 1, G + 1, c - 3, st("bone_block", axis="x"))
    s.set(c - 2, G + 1, c - 3, skull(5))
    s.chest(c + 3, G + 1, c + 2, "wyvern_peak", "west")
    s.chest(c - 3, G + 1, c - 2, "wyvern_peak", "east")
    for x, z in plat:
        if s.get(x, G + 1, z) is None and is_full(s.get(x, G, z)) and r.random() < 0.16:
            s.set(x, G + 1, z, st("snow", layers=r.randint(1, 2)))
    s.sign(c + 2, G + 1, c + 16, "wyvern_peak.gate", rotation=0, wood="spruce")
    s.travel_stone(c - 2, G + 1, c + 16, "minecraft:chiseled_stone_bricks", ring="minecraft:polished_andesite")
    s.boss(c + 0.5, G + 1, c + 0.5, YAW["south"])
    s.clear([(x, z) for x, z, _d in disk(c, c, R + 1) if s.inside(x, 0, z)], G + 1, H - 1)
    save(s)


# =================================================================================================
#  TUMBA DO BASILISCO — pátio de arenito com obeliscos e vítimas petrificadas; portal-mastaba sobre uma escadaria
#  que desce até um salão de colunas enterrado (o Basilisco). start_height -16, sem adaptação: y=16 é o pátio.
# =================================================================================================
def petrified(s, x, y, z, broken=False):
    """Estátua de alguém que encarou o Basilisco."""
    r = s.rnd
    s.set(x, y, z, "minecraft:andesite_wall")
    s.set(x, y + 1, z, "minecraft:polished_andesite")
    if broken:
        return
    if r.random() < 0.8:
        s.set(x + 1, y + 1, z, st("stone_button", face="wall", facing="east", powered="false"))
    if r.random() < 0.8:
        s.set(x - 1, y + 1, z, st("stone_button", face="wall", facing="west", powered="false"))
    s.set(x, y + 2, z, skull(r.randrange(16)))


def basilisk_tomb():
    N, H, G = 45, 30, 16
    s = S("basilisk_tomb", N, H, N, ground=G + 1)
    r = s.rnd
    c = 22
    LO, HI = c - 14, c + 14          # interior do salão (x e z)
    WLO, WHI = c - 16, c + 16        # casca (paredes de 2)
    FLOOR, CEIL = 2, 14              # piso do salão (bloco) e teto (14..15)
    court = []
    for x, z, d in disk(c, c, 20.5):
        if s.inside(x, 0, z):
            court.append((x, z))
    for x in range(c - 5, c + 6):
        for z in range(c + 15, N):
            if (x, z) not in court:
                court.append((x, z))
    court_set = set(court)
    # casca do salão (paredes e teto de 2 blocos; interior de ar explícito)
    for x in range(WLO, WHI + 1):
        for z in range(WLO, WHI + 1):
            for y in range(0, G):
                hall = LO <= x <= HI and LO <= z <= HI
                if hall and FLOOR < y < CEIL:
                    s.set(x, y, z, AIR)
                elif hall and y == FLOOR:
                    continue  # piso: mosaico abaixo
                else:
                    s.set(x, y, z, "minecraft:cut_sandstone" if y in (CEIL, CEIL + 1) else "minecraft:sandstone")
    # alicerce do pátio fora da casca
    for x, z in court:
        if WLO <= x <= WHI and WLO <= z <= WHI:
            continue
        for y in range(0, G):
            s.set(x, y, z, "minecraft:sand" if y >= G - 2 else "minecraft:sandstone")
    # piso do salão: mosaico
    for x in range(LO, HI + 1):
        for z in range(LO, HI + 1):
            d = math.hypot(x - c, z - c)
            if d < 1.5:
                b = "minecraft:chiseled_sandstone"
            elif d < 3:
                b = "minecraft:orange_terracotta"
            elif d < 4:
                b = "minecraft:cyan_terracotta"
            elif d < 5.5:
                b = "minecraft:yellow_terracotta"
            elif d < 6.5:
                b = "minecraft:blue_terracotta"
            elif (x - LO) % 5 == 0 or (z - LO) % 5 == 0:
                b = "minecraft:cut_sandstone"
            else:
                b = "minecraft:smooth_sandstone"
            s.set(x, FLOOR, z, b)
    # paredes internas: pilastras, faixas e tochas
    for k in range(LO, HI + 1):
        for (wx, wz, tx, tz, face) in ((LO - 1, k, LO, k, "east"), (HI + 1, k, HI, k, "west"),
                                       (k, LO - 1, k, LO, "south"), (k, HI + 1, k, HI, "north")):
            for y in range(FLOOR + 1, CEIL):
                if (k - LO) % 4 == 2:
                    s.set(wx, y, wz, "minecraft:chiseled_sandstone" if y in (FLOOR + 1, CEIL - 1) else "minecraft:smooth_sandstone")
                elif y in (6, 10):
                    s.set(wx, y, wz, "minecraft:orange_terracotta")
                else:
                    s.set(wx, y, wz, "minecraft:cut_sandstone")
            if (k - LO) % 8 == 2:
                s.set(tx, 8, tz, st("wall_torch", facing=face))
    # colunas 2x2
    for (px, pz) in ((c - 10, c - 10), (c + 9, c - 10), (c - 10, c - 1), (c + 9, c - 1), (c - 10, c + 8), (c + 9, c + 8)):
        for ox in (0, 1):
            for oz in (0, 1):
                for y in range(FLOOR + 1, CEIL):
                    b = "minecraft:chiseled_sandstone" if y in (FLOOR + 1, CEIL - 1) else \
                        "minecraft:orange_terracotta" if y == 8 else "minecraft:smooth_sandstone"
                    s.set(px + ox, y, pz + oz, b)
    # sarcófagos nas paredes leste e oeste
    for i, z in enumerate((c - 9, c - 5, c - 1, c + 3)):
        for (x0, side) in ((LO + 1, 1), (HI - 2, -1)):
            for dx in (0, 1):
                s.set(x0 + dx, FLOOR + 1, z, "minecraft:smooth_sandstone")
                if i == 2 and side == 1:
                    continue
                s.set(x0 + dx, FLOOR + 2, z, slab("smooth_sandstone_slab"))
            if i == 2 and side == 1:
                s.set(x0, FLOOR + 2, z, skull(4))
                s.set(x0 + 1, FLOOR + 2, z, st("bone_block", axis="x"))
            cx = x0 + (2 if side == 1 else -1)
            s.soft(cx, FLOOR + 1, z, candle(r.randint(1, 3), "orange"))
    # o olho do Basilisco na parede norte, sobre o nicho do tesouro
    eye = ["..YYY..", ".YYBYY.", "..YYY.."]
    for row, line in enumerate(eye):
        for i, ch in enumerate(line):
            if ch != ".":
                s.set(c - 3 + i, 10 - row, LO - 1, "minecraft:obsidian" if ch == "B" else "minecraft:yellow_terracotta")
    s.set(c, 9, LO - 1, st("verdant_froglight", axis="y"))
    for x in range(c - 2, c + 3):
        for z in (LO - 3, LO - 2, LO - 1):
            for y in (FLOOR + 1, FLOOR + 2, FLOOR + 3):
                s.set(x, y, z, AIR)
            s.set(x, FLOOR, z, "minecraft:cut_sandstone")
    s.chest(c - 1, FLOOR + 1, LO - 3, "basilisk_tomb", "south")
    s.chest(c + 1, FLOOR + 1, LO - 3, "basilisk_tomb", "south")
    s.set(c, FLOOR + 1, LO - 3, "minecraft:gold_block")
    s.set(c - 2, FLOOR + 1, LO - 2, st("decorated_pot", facing="south", cracked="false", waterlogged="false"))
    s.set(c + 2, FLOOR + 1, LO - 2, st("decorated_pot", facing="south", cracked="false", waterlogged="false"))
    s.sign(c - 3, FLOOR + 1, LO, "basilisk_tomb.hall", wall="south", wood="birch")
    # lanternas penduradas no teto
    for (lx, lz) in ((c - 6, c - 6), (c + 6, c - 6), (c - 6, c + 5), (c + 6, c + 5), (c - 12, c - 12), (c + 12, c - 12),
                     (c - 12, c + 12), (c + 12, c + 12)):
        s.set(lx, CEIL - 1, lz, chain())
        s.set(lx, CEIL - 2, lz, lantern(hanging=True))
    # vítimas petrificadas
    for (vx, vz, broken) in ((c - 6, c + 3, False), (c + 5, c - 6, False), (c - 4, c - 7, True), (c + 7, c + 4, False),
                             (c - 8, c - 3, False), (c + 4, c + 9, True)):
        petrified(s, vx, FLOOR + 1, vz, broken)
    for _ in range(16):
        x, z = r.randint(LO, HI), r.randint(LO, HI)
        y = r.choice((CEIL - 1, CEIL - 2))
        if s.get(x, y, z) == AIR and (x in (LO, HI) or z in (LO, HI) or r.random() < 0.3):
            s.set(x, y, z, "minecraft:cobweb")
    # escadaria: desce do pátio (z=c+20) até o salão (z=c+7); quem sobe anda para o sul
    for k in range(0, 14):
        z, y = c + 20 - k, G - k
        for x in (c - 1, c, c + 1):
            if k > 0:
                s.set(x, y, z, stairs("sandstone_stairs", "south"))
            for hy in range(y + 1, y + 5):
                if LO <= z <= HI and hy >= CEIL:
                    continue
                s.set(x, hy, z, AIR)
            for fy in range(FLOOR + 1, y):
                s.set(x, fy, z, "minecraft:smooth_sandstone")
        for x in (c - 2, c + 2):
            top = min(y + 4, G) if z > HI else y
            for fy in range(FLOOR + 1, top + 1):
                if LO <= z <= HI and fy >= CEIL:
                    continue
                s.set(x, fy, z, "minecraft:chiseled_sandstone" if fy == y and z <= HI else "minecraft:cut_sandstone")
        if z > HI and y + 5 <= G:
            for x in (c - 1, c, c + 1):
                s.set(x, y + 5, z, "minecraft:smooth_sandstone")
    # pátio
    for x, z in court:
        d = math.hypot(x - c, z - c)
        if abs(x - c) <= 1 and z >= c + 20:
            b = "minecraft:smooth_sandstone"
        elif d < 15:
            b = "minecraft:cut_sandstone" if (x + z) % 2 == 0 else "minecraft:smooth_sandstone"
        else:
            b = "minecraft:sand" if vnoise(x, z, 3.0, 41) > 0.35 else "minecraft:cut_sandstone"
        if s.get(x, G, z) is None or s.get(x, G, z) == "minecraft:sand":
            s.set(x, G, z, b)
    # mastaba de entrada (degraus) sobre a escadaria
    for L in range(4):
        y = G + 1 + L
        for x in range(c - 4 + L, c + 5 - L):
            for z in range(c + 15 + L, c + 22 - L):
                if s.get(x, y, z) == AIR:
                    continue
                edge = x in (c - 4 + L, c + 4 - L) and z in (c + 15 + L, c + 21 - L)
                b = "minecraft:chiseled_sandstone" if edge else "minecraft:orange_terracotta" if L == 1 else "minecraft:cut_sandstone"
                s.set(x, y, z, b)
    s.set(c, G + 5, c + 18, "minecraft:chiseled_sandstone")
    s.set(c, G + 6, c + 18, "minecraft:gold_block")
    for x in (c - 1, c, c + 1):
        for y in (G + 1, G + 2, G + 3):
            s.set(x, y, c + 21, AIR)
    for bx in (c - 3, c + 3):
        s.banner(bx, G + 2, c + 21, st("yellow_wall_banner", facing="south"),
                 [("circle", "black"), ("stripe_middle", "orange"), ("border", "orange")])
    # obeliscos
    for (ox, oz) in ((c - 13, c + 9), (c + 13, c + 9), (c - 12, c - 11), (c + 12, c - 11)):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                s.set(ox + dx, G + 1, oz + dz, "minecraft:cut_sandstone" if abs(dx) + abs(dz) == 2 else "minecraft:sandstone")
        for y in range(G + 2, G + 10):
            s.set(ox, y, oz, "minecraft:orange_terracotta" if y == G + 5 else "minecraft:smooth_sandstone")
        s.set(ox, G + 10, oz, "minecraft:chiseled_sandstone")
        s.set(ox, G + 11, oz, "minecraft:chiseled_red_sandstone")
    # vítimas no pátio + arbustos secos
    for (vx, vz) in ((c - 8, c + 6), (c + 8, c + 5), (c - 15, c - 2), (c + 14, c - 5), (c - 3, c - 13), (c + 5, c - 14)):
        petrified(s, vx, G + 1, vz, broken=r.random() < 0.3)
    for x, z in court:
        if s.get(x, G + 1, z) is None and s.get(x, G, z) == "minecraft:sand" and r.random() < 0.05:
            s.set(x, G + 1, z, "minecraft:dead_bush")
    s.sign(c - 3, G + 1, c + 22, "basilisk_tomb.gate", rotation=0, wood="birch")
    s.travel_stone(c + 5, G + 1, c + 19, "minecraft:chiseled_sandstone", ring="minecraft:smooth_sandstone")
    s.mob(c - 11 + 0.5, FLOOR + 1, c + 6 + 0.5, "minecraft:husk", YAW["east"])
    s.mob(c + 11 + 0.5, FLOOR + 1, c - 7 + 0.5, "minecraft:husk", YAW["west"])
    s.boss(c + 0.5, FLOOR + 1, c - 2 + 0.5, YAW["south"])
    s.clear(court, G + 1, H - 1)
    save(s)


# =================================================================================================
#  RUÍNAS DO COLOSSO — anfiteatro em ruínas com arquibancadas, muralha de arcos e pedaços de uma estátua gigante
#  (start_height 0, beard_thin: y=0 é o chão da arena, pisa-se em y=1)
# =================================================================================================
def colossus_ruins():
    N, H = 51, 24
    s = S("colossus_ruins", N, H, N, ground=1)
    r = s.rnd
    c = 25
    bricks = [("minecraft:stone_bricks", 5), ("minecraft:mossy_stone_bricks", 2), ("minecraft:cracked_stone_bricks", 2),
              ("minecraft:tuff_bricks", 1)]
    foot = []
    for x, z, d in disk(c, c, 24.0):
        if not s.inside(x, 0, z):
            continue
        foot.append((x, z))
        a = math.atan2(z - c, x - c)
        if d < 14.5:
            s.set(x, 0, z, s.pick([("minecraft:coarse_dirt", 3), ("minecraft:cracked_stone_bricks", 3),
                                   ("minecraft:stone_bricks", 2), ("minecraft:gravel", 1), ("minecraft:andesite", 1),
                                   ("minecraft:mossy_stone_bricks", 1)]))
        elif d < 20.5:
            s.set(x, 0, z, "minecraft:stone_bricks")
            t = int((d - 14.5) / 1.5)
            if vnoise(a * 6.0, 0.5, 1.0, 33) < 0.28:
                if r.random() < 0.5:
                    s.set(x, 1, z, r.choice(["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:gravel",
                                             slab("stone_brick_slab")]))
                continue
            for y in range(1, t + 1):
                s.set(x, y, z, s.pick(bricks))
            s.set(x, t + 1, z, stairs("stone_brick_stairs", outward(x - c, z - c)))
        elif d < 23.0:
            hw = 4 + int(9 * vnoise(a * 3.0, 0.0, 1.0, 31))
            phase = (a / (2 * math.pi) * 18) % 1.0
            for y in range(0, hw + 1):
                if 0.3 < phase < 0.7 and 1 <= y <= 4:
                    continue
                if 0.42 < phase < 0.58 and 8 <= y <= 9:
                    continue
                b = "minecraft:chiseled_stone_bricks" if (y == 5 and 0.3 < phase < 0.7) else s.pick(bricks)
                s.set(x, y, z, b)
        else:
            if r.random() < 0.3:
                s.set(x, 0, z, r.choice(["minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:gravel"]))
    # portão sul: passagem, torres e lintel
    for x in range(c - 2, c + 3):
        for z in range(c + 13, N):
            s.set(x, 0, z, s.pick([("minecraft:stone_bricks", 3), ("minecraft:cobblestone", 2), ("minecraft:gravel", 1)]))
            for y in range(1, 7):
                s.set(x, y, z, AIR)
    for tx in (c - 4, c + 4):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for y in range(0, 12):
                    s.set(tx + dx, y, c + 21 + dz, s.pick(bricks))
                if (dx + dz) % 2 == 0:
                    s.set(tx + dx, 12, c + 21 + dz, s.pick(bricks))
        s.banner(tx, 8, c + 23, st("gray_wall_banner", facing="south"),
                 [("piglin", "black"), ("bricks", "light_gray"), ("border", "black")])
    for x in range(c - 3, c + 4):
        s.set(x, 7, c + 21, "minecraft:chiseled_stone_bricks")
        s.set(x, 8, c + 21, slab("stone_brick_slab"))
    # cabeça caída do colosso
    hx, hy, hz = c - 8, 2, c - 7
    for x, z, d in disk(hx, hz, 3.2):
        for y in range(0, 6):
            if math.sqrt((x - hx) ** 2 + (y - hy) ** 2 * 1.2 + (z - hz) ** 2) <= 2.9:
                s.set(x, y, z, s.pick([("minecraft:stone", 4), ("minecraft:andesite", 2), ("minecraft:cobblestone", 1),
                                       ("minecraft:moss_block", 1 if y >= 4 else 0)]))
    s.set(hx - 1, 3, hz + 2, "minecraft:obsidian")
    s.set(hx + 1, 3, hz + 2, "minecraft:obsidian")
    s.set(hx, 2, hz + 3, "minecraft:andesite")
    # mão gigante
    for x in range(c + 6, c + 10):
        for z in range(c + 5, c + 8):
            s.set(x, 1, z, "minecraft:polished_andesite")
            s.set(x, 2, z, "minecraft:polished_andesite")
    for z, h in zip(range(c + 4, c + 8), (3, 4, 4, 3)):
        for y in range(1, h + 1):
            s.set(c + 10, y, z, "minecraft:polished_andesite")
        s.set(c + 10, h + 1, z, slab("andesite_slab"))
    s.set(c + 7, 1, c + 8, "minecraft:polished_andesite")
    s.set(c + 7, 2, c + 8, "minecraft:polished_andesite")
    # espada partida cravada no chão
    for y in range(1, 6):
        s.set(c + 3, y, c - 10, "minecraft:iron_block")
    for x in (c + 2, c + 4):
        s.set(x, 6, c - 10, "minecraft:iron_bars")
    s.set(c + 3, 6, c - 10, "minecraft:polished_deepslate")
    s.set(c + 3, 7, c - 10, "minecraft:polished_deepslate")
    s.set(c + 3, 8, c - 10, "minecraft:chiseled_deepslate")
    # câmaras laterais com os baús
    for side in (1, -1):
        x0 = c + 15 * side
        for i in range(5):
            x = x0 + i * side
            for z in range(c - 2, c + 3):
                for y in range(0, 5):
                    wall = i in (0, 4) or z in (c - 2, c + 2) or y in (0, 4)
                    s.set(x, y, z, s.pick(bricks) if wall else AIR)
        s.set(x0, 1, c, AIR)
        s.set(x0, 2, c, AIR)
        s.chest(x0 + 3 * side, 1, c, "colossus_ruins", "west" if side == 1 else "east")
        s.set(x0 + 3 * side, 1, c - 1, lantern())
        s.set(x0 + 2 * side, 3, c + 1, "minecraft:cobweb")
    # entulho, braseiros apagados, plantas
    for _ in range(26):
        x, z = c + r.randint(-13, 13), c + r.randint(-13, 13)
        if math.hypot(x - c, z - c) > 4 and s.get(x, 1, z) is None:
            s.set(x, 1, z, r.choice(["minecraft:cobblestone", "minecraft:mossy_cobblestone", slab("stone_brick_slab"),
                                     slab("mossy_cobblestone_slab"), "minecraft:gravel"]))
    for px, pz, _a in ring_points(c, c, 13.0, 4, math.pi / 8):
        s.set(px, 1, pz, "minecraft:chiseled_stone_bricks")
        s.set(px, 2, pz, campfire(lit=False))
    for x, z in foot:
        if s.get(x, 1, z) is None and s.get(x, 0, z) in ("minecraft:coarse_dirt", "minecraft:gravel") and r.random() < 0.12:
            s.set(x, 1, z, r.choice(["minecraft:short_grass", "minecraft:fern", "minecraft:short_grass", "minecraft:poppy"]))
        top = None
        for y in range(H - 1, 0, -1):
            v = s.get(x, y, z)
            if v and v != AIR:
                top = y
                break
        if top and is_full(s.get(x, top, z)) and s.get(x, top + 1, z) is None and r.random() < 0.12:
            s.set(x, top + 1, z, "minecraft:moss_carpet")
    # chegada: Pedra de Teleporte e placa fora do portão
    for x in range(c - 1, c + 2):
        for z in range(c + 23, N):
            s.set(x, 0, z, s.pick([("minecraft:gravel", 2), ("minecraft:cobblestone", 2), ("minecraft:stone_bricks", 1)]))
    s.travel_stone(c + 5, 1, N - 3, "minecraft:chiseled_stone_bricks", ring="minecraft:stone_bricks")
    s.set(c - 4, 0, N - 2, "minecraft:stone_bricks")
    s.sign(c - 4, 1, N - 2, "colossus_ruins.gate", rotation=0, wood="spruce")
    s.boss(c + 0.5, 1, c + 0.5, YAW["south"])
    extra = [(x, z) for x in range(c - 7, c + 8) for z in range(c + 22, N)]
    s.clear(foot + extra, 1, H - 1)
    save(s)


# =================================================================================================
#  PORTÃO ELEMENTAL — círculo de pedras com 4 obeliscos elementais; no meio, um estrado com a moldura
#  elemental ADORMECIDA (interior 3x4 de ar). start_height 0, beard_thin: y=0 chão, pisa-se em y=1.
# =================================================================================================
ELEMENT_STYLE = {
    "fire": ("minecraft:nether_bricks", "minecraft:red_nether_bricks", "minecraft:shroomlight"),
    "water": ("minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:sea_lantern"),
    "earth": ("minecraft:polished_granite", "minecraft:mud_bricks", st("ochre_froglight", axis="y")),
    "wind": ("minecraft:calcite", "minecraft:quartz_bricks", st("pearlescent_froglight", axis="y")),
}


def obelisk(s, ox, oz, element):
    base, trim, light = ELEMENT_STYLE[element]
    brick = f"{NS}:elemental_brick_{element}"
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            s.set(ox + dx, 0, oz + dz, trim)
            s.set(ox + dx, 1, oz + dz, trim)
            s.set(ox + dx, 2, oz + dz, trim)
            s.set(ox + dx, 3, oz + dz, brick if abs(dx) + abs(dz) == 2 else base)
    for y in range(4, 13):
        s.set(ox, y, oz, brick if y == 8 else base)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        s.set(ox + dx, 8, oz + dz, trim)
        s.set(ox + dx, 4, oz + dz, trim)
    s.set(ox, 13, oz, light)
    s.set(ox, 14, oz, brick)
    if element == "fire":
        s.set(ox, 15, oz, campfire(lit=True))
        for dx, dz in ((2, 0), (0, 2)):
            s.set(ox + dx, 0, oz + dz, "minecraft:netherrack")
            s.set(ox + dx, 1, oz + dz, campfire(lit=True))
    elif element == "water":
        s.set(ox, 15, oz, "minecraft:prismarine_wall")
        for dx, dz in ((2, 0), (0, 2), (2, 1), (1, 2)):
            s.set(ox + dx, 0, oz + dz, WATER)
            s.set(ox + dx, 1, oz + dz, AIR)
    elif element == "earth":
        s.set(ox, 15, oz, "minecraft:moss_block")
        s.set(ox, 16, oz, "minecraft:flowering_azalea")
        for dx, dz in ((2, 0), (0, 2), (2, 2)):
            s.set(ox + dx, 0, oz + dz, "minecraft:moss_block")
            s.set(ox + dx, 1, oz + dz, "minecraft:azalea" if (dx + dz) == 2 else "minecraft:moss_carpet")
    else:
        s.set(ox, 15, oz, st("lightning_rod", facing="up", powered="false", waterlogged="false"))
        for dx, dz in ((2, 0), (0, 2)):
            s.set(ox + dx, 1, oz + dz, st("end_rod", facing="up"))
            s.set(ox + dx, 0, oz + dz, "minecraft:calcite")


def portal_frame(s, x0, y0, z, frame, interior_w=3, interior_h=4):
    """Moldura retangular no eixo X (como a do Nether, com cantos), interior de AR explícito."""
    for i in range(interior_w + 2):
        s.set(x0 + i, y0, z, frame)
        s.set(x0 + i, y0 + interior_h + 1, z, frame)
    for y in range(y0 + 1, y0 + interior_h + 1):
        s.set(x0, y, z, frame)
        s.set(x0 + interior_w + 1, y, z, frame)
        for i in range(1, interior_w + 1):
            s.set(x0 + i, y, z, AIR)


def elemental_gate():
    N, H = 43, 22
    s = S("elemental_gate", N, H, N, ground=1)
    r = s.rnd
    c = 21
    foot = [(x, z) for x, z, d in disk(c, c, 20.5) if s.inside(x, 0, z)]
    # caminhos em cruz até o estrado
    for k in range(N):
        for w in (-1, 0, 1):
            for (x, z) in ((c + w, k), (k, c + w)):
                if math.hypot(x - c, z - c) <= 20.5 and s.inside(x, 0, z):
                    s.set(x, 0, z, s.pick([("minecraft:stone_bricks", 3), ("minecraft:polished_andesite", 2),
                                           ("minecraft:cobblestone", 1), ("minecraft:mossy_stone_bricks", 1)]))
    for x, z, d in disk(c, c, 9.3):
        s.set(x, 0, z, "minecraft:polished_andesite" if d > 8.3 else
              ("minecraft:stone_bricks" if int(d) % 2 else "minecraft:andesite"))
    # estrado em 3 degraus
    quad = {(1, -1): "fire", (1, 1): "water", (-1, 1): "earth", (-1, -1): "wind"}
    for x, z, d in disk(c, c, 6.6):
        dx, dz = x - c, z - c
        inward = OPP[outward(dx, dz)] if (dx or dz) else "north"
        if d > 5.0:
            s.set(x, 1, z, stairs("deepslate_tile_stairs", inward))
        elif d > 3.6:
            s.set(x, 1, z, "minecraft:polished_deepslate")
            s.set(x, 2, z, stairs("deepslate_tile_stairs", inward))
        else:
            s.set(x, 1, z, "minecraft:polished_deepslate")
            s.set(x, 2, z, "minecraft:polished_deepslate")
            if d > 2.5 and dx and dz:
                s.set(x, 3, z, f"{NS}:elemental_brick_{quad[(1 if dx > 0 else -1, 1 if dz > 0 else -1)]}")
            else:
                s.set(x, 3, z, "minecraft:chiseled_stone_bricks" if (x + z) % 2 else "minecraft:polished_deepslate")
    # a moldura adormecida (base na altura do topo do estrado)
    portal_frame(s, c - 2, 3, c, EL_FRAME)
    for px in (c - 4, c + 4):
        s.set(px, 3, c, "minecraft:chiseled_stone_bricks")
        s.set(px, 4, c, CLUSTER)
    s.sign(c + 2, 4, c + 2, "elemental_gate.frame", rotation=0, wood="birch")
    s.chest(c - 2, 4, c - 2, "elemental_gate", "south")
    # obeliscos elementais nas diagonais
    for (dx, dz), el in quad.items():
        obelisk(s, c + 8 * dx, c + 8 * dz, el)
    # círculo de pedras (vãos nos caminhos) com algumas vergas
    stones = []
    for i in range(20):
        a = math.radians(i * 18)
        deg = (i * 18) % 90
        if deg < 10 or deg > 80:
            stones.append(None)
            continue
        px, pz = int(round(c + 18.5 * math.cos(a))), int(round(c + 18.5 * math.sin(a)))
        tx, tz = int(round(-math.sin(a))), int(round(math.cos(a)))
        h = r.randint(4, 6)
        for (bx, bz) in ((px, pz), (px + tx, pz + tz)):
            s.set(bx, 0, bz, "minecraft:stone")
            for y in range(1, h + 1):
                s.set(bx, y, bz, s.pick([("minecraft:stone", 4), ("minecraft:andesite", 2), ("minecraft:mossy_cobblestone", 1),
                                         ("minecraft:cobblestone", 1)]))
        stones.append((px, pz, h))
    for i in range(20):
        a, b = stones[i], stones[(i + 1) % 20]
        if a and b and r.random() < 0.55:
            y = min(a[2], b[2]) + 1
            for t in (0.0, 0.25, 0.5, 0.75, 1.0):
                lx = int(round(a[0] + (b[0] - a[0]) * t))
                lz = int(round(a[1] + (b[1] - a[1]) * t))
                s.set(lx, y, lz, "minecraft:smooth_stone")
    # luzes ao longo dos caminhos
    for k in (12, 16):
        for (lx, lz) in ((c + 2, c + k), (c - 2, c - k), (c + k, c - 2), (c - k, c + 2)):
            s.lamp_post(lx, 1, lz)
    s.set(c - 2, 0, c + 17, "minecraft:polished_andesite")
    s.sign(c - 2, 1, c + 17, "elemental_gate.gate", rotation=0, wood="birch")
    s.travel_stone(c + 4, 1, c + 15, "minecraft:chiseled_stone_bricks", ring="minecraft:polished_andesite")
    for x, z in foot:
        if s.get(x, 1, z) is None and s.get(x, 0, z) is None and r.random() < 0.06:
            s.set(x, 1, z, r.choice(["minecraft:short_grass", "minecraft:cornflower", "minecraft:oxeye_daisy"]))
            s.set(x, 0, z, "minecraft:grass_block[snowy=false]")
    s.clear(foot, 1, H - 1)
    save(s)


# =================================================================================================
#  FENDA ABISSAL — cânion fundo de pedra-negra; uma trilha desce pela parede até a moldura sombria
#  ADORMECIDA no fundo. start_height -30, sem adaptação: y=30 é a superfície, o fundo pisa-se em y=3.
# =================================================================================================
def abyssal_chasm():
    NX, NZ, H, G = 31, 47, 40, 30
    s = S("abyssal_chasm", NX, H, NZ, ground=G + 1)
    r = s.rnd
    cx = 15
    Z0, Z1 = 4, 42
    PZ = 36  # z da moldura

    def xc(z):
        return cx + 2.0 * math.sin(z * 0.21) + (vnoise(0, z, 5.0, 51) - 0.5) * 1.5

    def hw(y, z):
        taper = min(1.0, (z - Z0) / 5.0, (Z1 - z) / 5.0)
        if taper <= 0:
            return -1.0
        top = 6.0 + 1.5 * math.sin(math.pi * (z - Z0) / (Z1 - Z0)) + (vnoise(z, 0, 4.0, 52) - 0.5) * 2.0
        bot = 4.8 if PZ - 3 <= z <= PZ + 4 else 3.4
        t = max(0.0, min(1.0, (y - 2) / (G - 2)))
        return (bot + (top - bot) * t ** 0.8) * (0.35 + 0.65 * taper) + (vnoise(z * 1.3, y * 1.7, 2.5, 53) - 0.5) * 1.2

    def shell():
        return s.pick([("minecraft:blackstone", 4), ("minecraft:deepslate", 3), (st("basalt", axis="y"), 2),
                       ("minecraft:tuff", 1), ("minecraft:obsidian", 0.3), ("minecraft:crying_obsidian", 0.25)])

    air_cells = []
    top_cells = set()
    for z in range(NZ):
        xz = xc(z)
        for y in range(2, H):
            w = hw(min(y, G), z)
            if w <= 0:
                continue
            for x in range(NX):
                if abs(x - xz) <= w:
                    if y == 2:
                        s.set(x, y, z, s.pick([("minecraft:soul_soil", 3), ("minecraft:blackstone", 2),
                                               ("minecraft:soul_sand", 1), ("minecraft:magma_block", 0.4),
                                               ("minecraft:sculk", 0.6)]))
                    else:
                        s.set(x, y, z, AIR)
                        if y <= G:
                            air_cells.append((x, y, z))
                        if y == G:
                            top_cells.add((x, z))
    # casca: tudo que encosta no vão (até 2 blocos) vira pedra-negra/ardósia/basalto
    for (x, y, z) in air_cells:
        for dx in (-2, -1, 0, 1, 2):
            for dz in (-2, -1, 0, 1, 2):
                for dy in (-1, 0):
                    nx, ny, nz = x + dx, y + dy, z + dz
                    if 1 <= ny <= G and s.inside(nx, ny, nz) and s.get(nx, ny, nz) is None:
                        s.set(nx, ny, nz, "minecraft:coarse_dirt" if ny == G and r.random() < 0.3 else shell())
    for x in range(NX):
        for z in range(NZ):
            if s.get(x, 2, z) and s.get(x, 2, z) != AIR and s.get(x, 1, z) is None:
                s.set(x, 1, z, "minecraft:blackstone")
    for (x, y, z) in air_cells:
        if y == 3 and s.get(x, 2, z) is None:
            s.set(x, 2, z, "minecraft:blackstone")
    # trilha: desce pela parede leste, do norte (z=6) até o fundo
    k = 0
    while True:
        z, y = 6 + k, G - k
        if y < 3:
            break
        xe = int(math.floor(xc(z) + hw(y, z)))
        while xe > 0 and s.get(xe, y, z) != AIR:
            xe -= 1
        for x in (xe - 1, xe):
            s.set(x, y, z, stairs("polished_blackstone_brick_stairs", "north"))
            if s.get(x, y - 1, z) in (None, AIR):
                s.set(x, y - 1, z, "minecraft:polished_blackstone_bricks")
            for hy in range(y + 1, y + 4):
                if s.get(x, hy, z) not in (None, AIR) and hy <= G:
                    s.set(x, hy, z, AIR)
        if k % 5 == 2 and s.get(xe + 1, y + 2, z) not in (None, AIR):
            s.set(xe, y + 2, z, st("soul_wall_torch", facing="west"))
        k += 1
    # moldura sombria adormecida no fundo, com pilares de obsidiana chorosa e um altar atrás
    xp = int(round(xc(PZ)))
    for x in range(xp - 4, xp + 5):
        for z in range(PZ - 2, PZ + 5):
            if s.get(x, 3, z) == AIR:
                s.set(x, 2, z, "minecraft:polished_blackstone_bricks" if abs(x - xp) <= 2 else s.get(x, 2, z))
    portal_frame(s, xp - 2, 3, PZ, AB_FRAME)
    for px in (xp - 3, xp + 3):
        for y in range(3, 9):
            s.set(px, y, PZ, "minecraft:crying_obsidian" if y % 2 else "minecraft:obsidian")
        s.set(px, 9, PZ, lantern(soul=True))
    for x in range(xp - 2, xp + 3):
        for z in range(PZ + 1, PZ + 5):
            s.set(x, 2, z, "minecraft:polished_blackstone_bricks")
            for y in range(3, 7):
                s.set(x, y, z, AIR)
    s.chest(xp, 3, PZ + 3, "abyssal_chasm", "north")
    s.set(xp - 1, 3, PZ + 3, skull(8, "wither_skeleton_skull"))
    s.set(xp + 1, 3, PZ + 3, skull(8, "wither_skeleton_skull"))
    for lx in (xp - 3, xp + 3):
        s.set(lx, 3, PZ - 2, "minecraft:polished_blackstone_brick_wall")
        s.set(lx, 4, PZ - 2, lantern(soul=True))
    s.sign(xp + 2, 3, PZ - 3, "abyssal_chasm.frame", rotation=8, wood="crimson")
    for _ in range(10):
        x, z = r.randint(0, NX - 1), r.randint(Z0, PZ - 4)
        if s.get(x, 3, z) == AIR and s.get(x, 2, z) == "minecraft:soul_soil":
            s.set(x, 3, z, "minecraft:soul_fire")
        elif s.get(x, 3, z) == AIR and r.random() < 0.5:
            s.set(x, 3, z, st("bone_block", axis=r.choice("xz")))
    # superfície: patamar ao norte com a Pedra de Teleporte, pilares partidos na borda
    for x in range(cx - 6, cx + 7):
        for z in range(0, 6):
            if s.get(x, G, z) != AIR and s.inside(x, G, z):
                s.set(x, G, z, s.pick([("minecraft:polished_blackstone_bricks", 3),
                                       ("minecraft:cracked_polished_blackstone_bricks", 1)]))
                if s.get(x, G - 1, z) is None:
                    s.set(x, G - 1, z, "minecraft:blackstone")
    s.travel_stone(cx - 3, G + 1, 2, "minecraft:chiseled_polished_blackstone", ring="minecraft:polished_blackstone")
    s.sign(cx + 3, G + 1, 2, "abyssal_chasm.gate", rotation=0, wood="crimson")
    for z in (12, 22, 32):
        for side in (-1, 1):
            px = int(round(xc(z) + side * (hw(G, z) + 3)))
            if not s.inside(px, G, z):
                continue
            s.set(px, G, z, "minecraft:polished_blackstone_bricks")
            h = r.randint(2, 5)
            for y in range(G + 1, G + 1 + h):
                s.set(px, y, z, "minecraft:polished_blackstone_bricks" if y % 2 else "minecraft:cracked_polished_blackstone_bricks")
            s.set(px, G + 1 + h, z, chain())
            s.set(px, G + h, z, "minecraft:chiseled_polished_blackstone")
    rim = set()
    for (x, z) in top_cells:
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                rim.add((x + dx, z + dz))
    rim |= {(x, z) for x in range(cx - 6, cx + 7) for z in range(0, 6)}
    s.clear([p for p in rim if s.inside(p[0], 0, p[1])], G + 1, H - 1)
    save(s)


# =================================================================================================
#  ESCADARIA CELESTE — base de quartzo, grande escadaria e um templo redondo a céu aberto com o Farol
#  Celestial (ADORMECIDO) sobre um bloco de ouro. start_height 0, beard_thin: y=0 chão da praça.
# =================================================================================================
def celestial_stairway():
    NX, NZ, H = 33, 50, 30
    s = S("celestial_stairway", NX, H, NZ, ground=1)
    r = s.rnd
    cx, tz = 16, 14            # centro do templo
    X0, X1, Z0, Z1, P = 3, 29, 3, 25, 14
    # plinto (fachada de calcita com pilastras de quartzo, faixas e janelas que brilham)
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            face = x in (X0, X1) or z in (Z0, Z1)
            for y in range(0, P):
                if not face:
                    s.set(x, y, z, "minecraft:stone" if y < P - 1 else "minecraft:smooth_quartz")
                    continue
                along = (z - Z0) if x in (X0, X1) else (x - X0)
                if y in (4, 9):
                    b = "minecraft:smooth_quartz"
                elif along % 4 == 0:
                    b = st("quartz_pillar", axis="y")
                elif y in (6, 7) and along % 4 == 2:
                    b = "minecraft:yellow_stained_glass"
                else:
                    b = "minecraft:calcite"
                s.set(x, y, z, b)
            # janelas: luz atrás do vidro
            if face:
                ix = x + (1 if x == X0 else -1 if x == X1 else 0)
                iz = z + (1 if z == Z0 else -1 if z == Z1 else 0)
                for y in (6, 7):
                    if s.get(x, y, z) == "minecraft:yellow_stained_glass":
                        s.set(ix, y, iz, st("pearlescent_froglight", axis="y"))
    # piso do topo
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            d = math.hypot(x - cx, z - tz)
            if 3.5 <= d <= 4.5:
                b = "minecraft:yellow_stained_glass"
                s.set(x, P - 1, z, st("pearlescent_froglight", axis="y"))
            elif 8.5 <= d <= 9.5:
                b = "minecraft:chiseled_quartz_block"
            elif d < 9:
                b = "minecraft:quartz_bricks" if (x + z) % 2 else "minecraft:smooth_quartz"
            else:
                b = "minecraft:smooth_quartz"
            s.set(x, P, z, b)
            if (x in (X0, X1) or z in (Z0, Z1)) and not (z == Z1 and 12 <= x <= 20):
                s.set(x, P + 1, z, slab("smooth_quartz_slab"))
    # estrado: ouro + Farol adormecido; nada em cima (precisa de céu e 4 de ar)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            s.set(cx + dx, P + 1, tz + dz, "minecraft:gold_block" if dx == dz == 0 else "minecraft:chiseled_quartz_block")
            if dx and dz:
                s.set(cx + dx, P + 2, tz + dz, lantern())
    s.set(cx, P + 2, tz, BEACON)
    for y in range(P + 3, H):
        s.set(cx, y, tz, AIR)
    # colunata redonda
    for px, pz, _a in ring_points(cx, tz, 9.0, 12, math.pi / 12):
        s.set(px, P + 1, pz, "minecraft:chiseled_quartz_block")
        for y in range(P + 2, P + 8):
            s.set(px, y, pz, st("quartz_pillar", axis="y"))
        s.set(px, P + 8, pz, "minecraft:chiseled_quartz_block")
        s.set(px, P + 10, pz, st("end_rod", facing="up"))
        inward = OPP[outward(px - cx, pz - tz)]
        ddx, ddz = DIRV[inward]
        s.banner(px + ddx, P + 6, pz + ddz, st("white_wall_banner", facing=inward),
                 [("flower", "yellow"), ("circle", "yellow"), ("border", "yellow")])
    for x, z, d in disk(cx, tz, 9.6):
        if 8.5 <= d <= 9.5:
            s.set(x, P + 9, z, "minecraft:quartz_bricks")
    s.chest(cx, P + 1, tz - 8, "celestial_stairway", "south")
    s.sign(cx, P + 1, tz + 4, "celestial_stairway.beacon", rotation=0, wood="birch")
    # grande escadaria (sobe para o norte)
    for k in range(1, P + 1):
        z, y = Z1 + 15 - k, k
        for x in range(12, 21):
            for fy in range(0, y):
                s.set(x, fy, z, "minecraft:calcite")
            if 13 <= x <= 19:
                s.set(x, y, z, stairs("quartz_stairs", "north"))
                for hy in range(y + 1, y + 6):
                    s.set(x, hy, z, AIR)
            else:
                s.set(x, y, z, "minecraft:calcite")
                s.set(x, y + 1, z, "minecraft:diorite_wall")
                if k % 4 == 2:
                    s.set(x, y + 2, z, lantern())
    # praça da base
    for x in range(5, 28):
        for z in range(Z1 + 15, NZ):
            edge = x in (5, 27) or z == NZ - 1
            s.set(x, 0, z, "minecraft:chiseled_quartz_block" if edge else
                  ("minecraft:quartz_bricks" if (x + z) % 2 else "minecraft:smooth_quartz"))
    for x0 in (6, 24):
        for x in range(x0, x0 + 3):
            for z in range(Z1 + 17, NZ - 2):
                s.set(x, 0, z, "minecraft:grass_block[snowy=false]")
                s.set(x, 1, z, r.choice(["minecraft:lily_of_the_valley", "minecraft:white_tulip", "minecraft:oxeye_daisy",
                                         "minecraft:azure_bluet", "minecraft:short_grass"]))
    for ax in (10, 22):
        az = NZ - 6
        s.set(ax, 1, az, "minecraft:chiseled_quartz_block")
        s.set(ax, 2, az, st("quartz_pillar", axis="y"))
        s.set(ax, 3, az, st("quartz_pillar", axis="y"))
        s.set(ax, 4, az, st("pearlescent_froglight", axis="y"))
        for wx in (ax - 1, ax + 1):
            s.set(wx, 3, az, "minecraft:white_stained_glass_pane")
            s.set(wx, 4, az, "minecraft:white_stained_glass_pane")
    s.travel_stone(cx, 1, NZ - 5, "minecraft:chiseled_quartz_block", ring="minecraft:smooth_quartz")
    s.sign(cx - 3, 1, NZ - 3, "celestial_stairway.gate", rotation=0, wood="birch")
    # limpa o terreno em volta (margem de 1) e por cima de tudo
    cells = [(x, z) for x in range(X0 - 1, X1 + 2) for z in range(Z0 - 1, Z1 + 2)]
    cells += [(x, z) for x in range(4, 29) for z in range(Z1 + 1, NZ)]
    s.clear([p for p in cells if s.inside(p[0], 0, p[1])], 1, H - 1)
    save(s)


# =================================================================================================
#  RUPTURA DO CREPÚSCULO — ilha de pedra rachada no mar; da fenda brilhante sobe o rasgo (twilight_rift).
#  Sem projeção: start_height 43 → y=0 do molde = 42; y=20 = topo da água (62); pisa-se no cais em y=21.
# =================================================================================================
def twilight_rupture():
    N, H, W = 45, 40, 20
    s = S("twilight_rupture", N, H, N, ground=W + 1)
    r = s.rnd
    c = 22
    R = 17.0
    ux, uz = 24.0, 20.0
    L = math.hypot(ux, uz)
    ux, uz = ux / L, uz / L
    nx, nz = -uz, ux

    def along_perp(x, z):
        return (x - c) * ux + (z - c) * uz, (x - c) * nx + (z - c) * nz

    tops = {}
    for x, z, d in disk(c, c, R + 3):
        nd = d + (vnoise(x, z, 5.0, 61) - 0.5) * 5.0
        if nd > R or not s.inside(x, 0, z):
            continue
        f = 1.0 - nd / R
        if nd > R - 2.0:
            top = W - 1 + (1 if r.random() < 0.5 else 0)
        else:
            top = W + 1 + int(round(7 * f ** 0.8 + (vnoise(x, z, 3.0, 62) - 0.5) * 3))
        bottom = max(0, W - int(4 + 16 * f ** 0.6))
        for y in range(bottom, top + 1):
            depth = top - y
            if top <= W:
                b = s.pick([("minecraft:gravel", 2), ("minecraft:sand", 1)]) if depth == 0 else "minecraft:stone"
            elif depth == 0:
                b = s.pick([("minecraft:mycelium[snowy=false]", 3), ("minecraft:podzol[snowy=false]", 2),
                            ("minecraft:moss_block", 1), ("minecraft:coarse_dirt", 1), ("minecraft:calcite", 0.4)])
            elif depth <= 2:
                b = s.pick([("minecraft:dirt", 2), ("minecraft:tuff", 1)])
            else:
                b = s.pick([("minecraft:deepslate", 3), ("minecraft:tuff", 2), ("minecraft:stone", 2), ("minecraft:blackstone", 1)])
            s.set(x, y, z, b)
        tops[(x, z)] = top
    # a rachadura: fundo brilhante de obsidiana chorosa e ametista, paredes de obsidiana
    for (x, z), top in list(tops.items()):
        nd = math.hypot(x - c, z - c)
        al, pe = along_perp(x, z)
        if abs(al) > 11 or nd > R - 4:
            continue
        width = 1.6 + (vnoise(x, z, 2.0, 63) - 0.5) * 1.2
        if abs(pe) < width:
            s.set(x, W - 4, z, "minecraft:crying_obsidian" if (x + z) % 3 else "minecraft:amethyst_block")
            for y in range(W - 3, max(top, W) + 1):
                s.set(x, y, z, AIR)
            tops[(x, z)] = W - 4
        elif abs(pe) < width + 1.2:
            for y in range(W - 4, top + 1):
                s.set(x, y, z, s.pick([("minecraft:obsidian", 3), ("minecraft:crying_obsidian", 1), ("minecraft:blackstone", 2)]))
            if r.random() < 0.25:
                s.set(x, top + 1, z, st("amethyst_cluster", facing="up", waterlogged="false"))
    # o rasgo: coluna irregular de twilight_rift saindo da fenda
    jitter = [0, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 0, 0, 1, 0]
    for i, y in enumerate(range(W - 3, W + 13)):
        o = jitter[i % len(jitter)]
        s.set(c + o, y, c + (o if i % 3 == 0 else 0), RIFT)
    s.set(c + 2, W + 9, c - 1, RIFT)
    s.set(c - 1, W + 11, c + 2, RIFT)
    s.set(c + 1, W + 12, c + 2, RIFT)
    for _ in range(14):
        a = r.random() * 2 * math.pi
        dist = 3 + r.random() * 3.5
        fx, fz = int(round(c + dist * math.cos(a))), int(round(c + dist * math.sin(a)))
        fy = W + r.randint(5, 15)
        if s.inside(fx, fy, fz) and s.get(fx, fy, fz) is None:
            s.set(fx, fy, fz, s.pick([("minecraft:crying_obsidian", 2), ("minecraft:obsidian", 2), ("minecraft:blackstone", 1),
                                      ("minecraft:amethyst_block", 1), ("minecraft:purpur_block", 1)]))
    # arcos partidos dos dois lados do rasgo
    for side in (1, -1):
        px, pz = int(round(c + side * 5 * nx)), int(round(c + side * 5 * nz))
        base = tops.get((px, pz), W + 2)
        for y in range(base + 1, W + 11):
            s.set(px, y, pz, st("purpur_pillar", axis="y") if y % 3 else "minecraft:polished_blackstone_bricks")
        for k in (1, 2):
            s.set(int(round(px - side * k * nx)), W + 10, int(round(pz - side * k * nz)), "minecraft:polished_blackstone_bricks")
        s.set(px, W + 11, pz, st("end_rod", facing="up"))
    # pedestais da Luz e da Sombra (as duas metades da chave)
    for side, key, block, top_block in ((1, "twilight_rupture.light", "minecraft:chiseled_quartz_block", st("end_rod", facing="up")),
                                        (-1, "twilight_rupture.shadow", "minecraft:chiseled_polished_blackstone", lantern(soul=True))):
        px, pz = int(round(c + side * 7.5 * nx)), int(round(c + side * 7.5 * nz))
        base = tops.get((px, pz), W + 2)
        s.set(px, base + 1, pz, block)
        s.set(px, base + 2, pz, top_block)
        sx, sz = px + 1, pz
        sb = tops.get((sx, sz), base)
        s.set(sx, sb, sz, "minecraft:polished_blackstone")
        s.sign(sx, sb + 1, sz, key, rotation=0, wood="dark_oak")
        if side == -1:
            s.chest(px - 1, tops.get((px - 1, pz), base) + 1, pz, "twilight_rupture", "south")
            s.set(px - 1, tops.get((px - 1, pz), base), pz, "minecraft:polished_blackstone")
    # praça ao sul com a Pedra de Teleporte, trilha até o cais
    yt = W + 3
    for x in range(c - 2, c + 3):
        for z in range(c + 9, c + 14):
            for y in range(max(0, W - 3), yt):
                if s.get(x, y, z) is None or s.get(x, y, z) == AIR:
                    s.set(x, y, z, "minecraft:tuff")
            s.set(x, yt, z, "minecraft:polished_blackstone_bricks" if (x + z) % 2 else "minecraft:polished_blackstone")
            for y in range(yt + 1, yt + 5):
                s.set(x, y, z, AIR)
    s.travel_stone(c, yt + 1, c + 11, "minecraft:chiseled_polished_blackstone")
    s.set(c + 2, yt + 1, c + 13, AIR)
    s.sign(c + 2, yt + 1, c + 13, "twilight_rupture.gate", rotation=0, wood="dark_oak")
    for lx in (c - 2, c + 2):
        s.lamp_post(lx, yt + 1, c + 9, "minecraft:polished_blackstone_wall", 1, soul=True)
    z = c + 14
    py = yt
    while z < N:
        land = any((x, z) in tops and tops[(x, z)] >= W for x in (c - 1, c, c + 1))
        if not land:
            break
        py = max(W, py - 1)
        for x in (c - 1, c, c + 1):
            for y in range(max(0, W - 3), py):
                if s.get(x, y, z) is None or s.get(x, y, z) == AIR:
                    s.set(x, y, z, "minecraft:tuff")
            s.set(x, py, z, stairs("polished_blackstone_brick_stairs", "north") if py > W else "minecraft:polished_blackstone_bricks")
            for y in range(py + 1, py + 5):
                s.set(x, y, z, AIR)
        z += 1
    dock_start = z
    for zz in range(dock_start, N):
        for x in (c - 1, c, c + 1):
            s.set(x, W, zz, "minecraft:dark_oak_planks")
            s.set(x, W + 1, zz, AIR)
        if (zz - dock_start) % 3 == 0:
            for x in (c - 1, c + 1):
                for y in range(max(0, W - 6), W):
                    s.set(x, y, zz, log("minecraft:dark_oak_log"))
    for x in (c - 1, c + 1):
        s.set(x, W + 1, N - 1, "minecraft:dark_oak_fence")
        s.set(x, W + 2, N - 1, lantern(soul=True))
    # ruínas: lápides e colunas de purpur pela ilha
    placed = 0
    for _ in range(60):
        if placed >= 9:
            break
        x, z = c + r.randint(-13, 13), c + r.randint(-13, 13)
        t = tops.get((x, z))
        if t is None or t <= W or s.get(x, t + 1, z) is not None:
            continue
        if r.random() < 0.5:
            s.set(x, t + 1, z, "minecraft:polished_blackstone_brick_wall")
            if r.random() < 0.5:
                s.set(x, t + 2, z, lantern(soul=True))
        else:
            for y in range(t + 1, t + 1 + r.randint(1, 3)):
                s.set(x, y, z, st("purpur_pillar", axis="y"))
        placed += 1
    save(s)


# =================================================================================================
#  SANTUÁRIO DE ASCENSÃO — plataforma redonda de ardósia com colunata de calcita e ametista; no meio o Altar
#  de Ascensão PRONTO e público ({PublicAltar:1b}). Aglomerados de cristal de mana em volta. Zona segura.
#  start_height 0, beard_thin: y=0 chão, plataforma em y=1, pisa-se em y=2 (o altar fica em y=2).
# =================================================================================================
def ascension_sanctum():
    N, H = 35, 22
    s = S("ascension_sanctum", N, H, N, ground=1)
    r = s.rnd
    c = 17
    foot = [(x, z) for x, z, d in disk(c, c, 16.5) if s.inside(x, 0, z)]
    for x, z, d in disk(c, c, 13.3):
        dx, dz = x - c, z - c
        s.set(x, 0, z, "minecraft:polished_deepslate")
        if d > 12.3:
            s.set(x, 1, z, stairs("deepslate_tile_stairs", OPP[outward(dx, dz)]))
            continue
        ang = math.degrees(math.atan2(dz, dx)) % 30
        if d <= 5.5:
            b = "minecraft:chiseled_deepslate" if d < 1 else "minecraft:deepslate_tiles"
        elif d <= 6.5:
            b = "minecraft:amethyst_block"
        elif d <= 9.3:
            b = "minecraft:polished_deepslate" if (x + z) % 2 else "minecraft:deepslate_bricks"
        elif d <= 10.3:
            b = st("pearlescent_froglight", axis="y") if ang < 6 else "minecraft:calcite"
        else:
            b = "minecraft:deepslate_tiles"
        s.set(x, 1, z, b)
    # o Altar de Ascensão (núcleo público + 8 pedestais + 16 Pedras Rúnicas), tudo na altura do núcleo
    s.set(c, 2, c, ASC_CORE, nbt={"PublicAltar": Byte(1), "NeoForgeData": {"PublicAltar": Byte(1)}})
    for dx, dz in ALTAR_PEDESTALS:
        s.set(c + dx, 2, c + dz, ASC_PEDESTAL)
    for dx, dz in ALTAR_RUNES:
        s.set(c + dx, 2, c + dz, RUNE_STONE)
    for dx, dz in ((4, 4), (4, -4), (-4, 4), (-4, -4)):
        s.set(c + dx, 2, c + dz, CLUSTER)
    for y in range(3, H):
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                s.set(c + dx, y, c + dz, AIR)
    # colunata
    for i, (px, pz, _a) in enumerate(ring_points(c, c, 11.0, 12, math.pi / 12)):
        s.set(px, 2, pz, "minecraft:chiseled_deepslate")
        for y in range(3, 9):
            s.set(px, y, pz, "minecraft:calcite")
        s.set(px, 9, pz, "minecraft:amethyst_block")
        s.set(px, 11, pz, st("amethyst_cluster", facing="up", waterlogged="false"))
        if i % 3 == 1:
            inward = OPP[outward(px - c, pz - c)]
            ddx, ddz = DIRV[inward]
            s.banner(px + ddx, 7, pz + ddz, st("purple_wall_banner", facing=inward),
                     [("rhombus", "white"), ("circle", "magenta"), ("border", "magenta")])
    for x, z, d in disk(c, c, 11.6):
        if 10.5 <= d <= 11.5:
            s.set(x, 10, z, "minecraft:deepslate_tiles")
    # fontes (norte, leste e oeste)
    for fx, fz in ((c, c - 8), (c + 8, c), (c - 8, c)):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                s.set(fx + dx, 2, fz + dz, WATER if dx == dz == 0 else "minecraft:polished_deepslate")
        s.set(fx - 1, 3, fz - 1, CLUSTER)
        s.set(fx + 1, 3, fz + 1, CLUSTER)
    s.chest(c + 6, 2, c - 6, "ascension_sanctum", "south")
    # geodos de cristal fora da plataforma
    for px, pz, a in ring_points(c, c, 14.8, 8, math.pi / 8):
        if pz > c + 10 and abs(px - c) < 5:
            continue
        s.set(px, 0, pz, "minecraft:calcite")
        s.set(px, 1, pz, "minecraft:amethyst_block")
        s.set(px, 2, pz, CLUSTER)
        for (ddx, ddz) in ((1, 0), (0, 1)):
            if r.random() < 0.7 and s.get(px + ddx, 1, pz + ddz) is None:
                s.set(px + ddx, 0, pz + ddz, "minecraft:calcite")
                s.set(px + ddx, 1, pz + ddz, "minecraft:calcite")
                s.set(px + ddx, 2, pz + ddz, CLUSTER)
    # caminho, chegada e placa
    for x in range(c - 1, c + 2):
        for z in range(c + 13, N):
            s.set(x, 0, z, "minecraft:deepslate_tiles" if z % 2 else "minecraft:polished_deepslate")
    s.travel_stone(c + 4, 1, c + 15, "minecraft:chiseled_deepslate", ring="minecraft:polished_deepslate")
    s.set(c - 4, 0, c + 15, "minecraft:polished_deepslate")
    s.sign(c - 4, 1, c + 15, "ascension_sanctum.gate", rotation=0, wood="cherry")
    for lx in (c - 2, c + 2):
        s.set(lx, 0, c + 17, "minecraft:polished_deepslate")
        s.lamp_post(lx, 1, c + 17, "minecraft:polished_deepslate_wall", 1, soul=True)
    s.clear(foot, 1, H - 1)
    save(s)


# =================================================================================================
#  WORLDGEN (structure, structure_set, template_pool, processor_list) + TAGS
# =================================================================================================
EXCLUSIONS = [{"other_set": "minecraft:villages", "chunk_count": 4},
              {"other_set": f"{NS}:guild_city", "chunk_count": 10},
              {"other_set": f"{NS}:capital_city", "chunk_count": 13},
              {"other_set": f"{NS}:capital_sahra", "chunk_count": 13},
              {"other_set": f"{NS}:capital_nordheim", "chunk_count": 13}]

PLAINSY = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:birch_forest",
           "minecraft:flower_forest", "minecraft:savanna", "minecraft:old_growth_birch_forest"]
# id: (biomas, start_height, projeta no relevo?, adaptação, espaçamento, separação, salt)
WORLDGEN = {
    "hydra_lair": (["minecraft:swamp", "minecraft:mangrove_swamp"], 0, True, "beard_thin", 30, 10, 1946300901),
    "wyvern_peak": (["minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks", "minecraft:snowy_slopes"],
                    -14, True, "none", 34, 12, 1946300902),
    "basilisk_tomb": (["minecraft:desert", "minecraft:badlands"], -16, True, "none", 34, 12, 1946300903),
    "colossus_ruins": (PLAINSY, 0, True, "beard_thin", 52, 20, 1946300904),
    "elemental_gate": (PLAINSY + ["minecraft:savanna_plateau", "minecraft:taiga", "minecraft:snowy_plains", "minecraft:cherry_grove"],
                       0, True, "beard_thin", 48, 18, 1946300905),
    "abyssal_chasm": (["minecraft:dark_forest", "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands",
                       "minecraft:plains", "minecraft:taiga", "minecraft:old_growth_spruce_taiga", "minecraft:snowy_plains",
                       "minecraft:windswept_hills"], -30, True, "none", 56, 20, 1946300906),
    "celestial_stairway": (["minecraft:meadow", "minecraft:cherry_grove", "minecraft:grove", "minecraft:snowy_slopes",
                            "minecraft:windswept_hills", "minecraft:windswept_forest"], 0, True, "beard_thin", 44, 16, 1946300907),
    "twilight_rupture": (["minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
                          "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:warm_ocean"],
                         43, False, "none", 48, 18, 1946300908),
    "ascension_sanctum": (PLAINSY + ["minecraft:cherry_grove", "minecraft:taiga", "minecraft:snowy_plains", "minecraft:dark_forest"],
                          0, True, "beard_thin", 34, 12, 1946300909),
}


def rule(block, out, p):
    return {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": block, "probability": p},
            "location_predicate": {"predicate_type": "minecraft:always_true"},
            "output_state": {"Name": out}}


PROCESSORS = {
    "hydra_lair": [rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.3),
                   rule("minecraft:mossy_stone_bricks", "minecraft:mossy_cobblestone", 0.06)],
    "wyvern_peak": [rule("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.1)],
    "colossus_ruins": [rule("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.12),
                       rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.12)],
    "elemental_gate": [rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.1)],
    "basilisk_tomb": [rule("minecraft:cut_sandstone", "minecraft:sandstone", 0.08)],
}


def gen_worldgen():
    for sid in SITES:
        biomes, sh, project, adapt, spacing, sep, salt = WORLDGEN[sid]
        proc = f"{NS}:world_v09_{sid}_age" if sid in PROCESSORS else "minecraft:empty"
        if sid in PROCESSORS:
            write_json(os.path.join(D, f"worldgen/processor_list/world_v09_{sid}_age.json"),
                       {"processors": [{"processor_type": "minecraft:rule", "rules": PROCESSORS[sid]}]})
        write_json(os.path.join(D, f"worldgen/template_pool/world_v09/{sid}.json"), {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                   "location": f"{NS}:world_v09/{sid}", "processors": proc,
                                                   "projection": "rigid"}}]})
        st_json = {"type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/{sid}", "step": "surface_structures",
                   "terrain_adaptation": adapt, "spawn_overrides": {}, "start_pool": f"{NS}:world_v09/{sid}", "size": 1,
                   "start_height": {"absolute": sh}}
        if project:
            st_json["project_start_to_heightmap"] = "WORLD_SURFACE_WG"
        st_json["max_distance_from_center"] = 80
        st_json["use_expansion_hack"] = False
        write_json(os.path.join(D, f"worldgen/structure/{sid}.json"), st_json)
        excl = EXCLUSIONS if sid != "twilight_rupture" else EXCLUSIONS[1:]
        write_json(os.path.join(D, f"worldgen/structure_set/{sid}.json"), {
            "structures": [{"structure": f"{NS}:{sid}", "weight": 1}],
            "placement": {"type": f"{NS}:random_spread_multi", "spacing": spacing, "separation": sep, "salt": salt,
                          "exclusion_zones": excl}})
        write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/biome/has_structure/{sid}.json"), {"values": biomes})
        write_json(os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure/{sid}.json"), {"values": [f"{NS}:{sid}"]})
    tags = os.path.join(MERGE, f"data/{NS}/tags/worldgen/structure")
    write_json(os.path.join(tags, "boss_lair.json"), {"values": [f"{NS}:{s}" for s in LAIRS]})
    write_json(os.path.join(tags, "portal_site.json"), {"values": [f"{NS}:{s}" for s in PORTALS]})
    write_json(os.path.join(tags, "world_v09_sites.json"), {"values": [f"{NS}:{s}" for s in SITES]})
    write_json(os.path.join(tags, "mana_crystal_sites.json"), {"values": [
        f"{NS}:ascension_sanctum", f"{NS}:elemental_gate",
        {"id": f"#{NS}:elemental_shrines", "required": False},
        {"id": f"{NS}:druid_circle", "required": False},
        {"id": f"{NS}:arcane_ruins", "required": False},
        {"id": f"{NS}:fairy_lake", "required": False}]})
    # Veio de Mana (minério comum, y −16..80) e aglomerados perto de santuários
    write_json(os.path.join(D, "worldgen/configured_feature/mana_vein_ore.json"), {
        "type": "minecraft:ore", "config": {"size": 6, "discard_chance_on_air_exposure": 0.0, "targets": [
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
             "state": {"Name": f"{NS}:mana_vein_ore"}},
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
             "state": {"Name": f"{NS}:deepslate_mana_vein_ore"}}]}})
    write_json(os.path.join(D, "worldgen/placed_feature/mana_vein_ore.json"), {
        "feature": f"{NS}:mana_vein_ore", "placement": [
            {"type": "minecraft:count", "count": 8}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -16},
                                                          "max_inclusive": {"absolute": 80}}},
            {"type": "minecraft:biome"}]})
    write_json(os.path.join(D, "worldgen/configured_feature/mana_crystal_site.json"),
               {"type": f"{NS}:mana_crystal_site", "config": {}})
    write_json(os.path.join(D, "worldgen/placed_feature/mana_crystal_site.json"),
               {"feature": f"{NS}:mana_crystal_site", "placement": []})
    write_json(os.path.join(D, "neoforge/biome_modifier/world_v09_mana_vein.json"), {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld", "features": f"{NS}:mana_vein_ore",
        "step": "underground_ores"})
    write_json(os.path.join(D, "neoforge/biome_modifier/world_v09_mana_crystal_sites.json"), {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld", "features": f"{NS}:mana_crystal_site",
        "step": "vegetal_decoration"})
    # tags de bloco/item
    blocks = [f"{NS}:mana_vein_ore", f"{NS}:deepslate_mana_vein_ore"]
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"),
               {"values": blocks + [f"{NS}:mana_crystal_cluster"]})
    write_json(os.path.join(MERGE, "data/minecraft/tags/block/needs_stone_tool.json"), {"values": blocks})
    write_json(os.path.join(MERGE, "data/c/tags/block/ores.json"), {"values": blocks})
    write_json(os.path.join(MERGE, "data/c/tags/item/ores.json"), {"values": blocks})


# =================================================================================================
#  LOOT
# =================================================================================================
def cnt(lo, hi):
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def item(name, w=1, lo=1, hi=1, fns=None):
    e = {"type": "minecraft:item", "name": mc(name), "weight": w}
    f = [cnt(lo, hi)] if (lo, hi) != (1, 1) else []
    if fns:
        f += fns
    if f:
        e["functions"] = f
    return e


def book(w, lo=15, hi=30):
    return item("minecraft:book", w, fns=[{"function": "minecraft:enchant_with_levels",
                                            "levels": {"type": "minecraft:uniform", "min": lo, "max": hi}}])


def sub(table, w):
    return {"type": "minecraft:loot_table", "value": f"{NS}:world_v09/{table}", "weight": w}


def ess(el, w, lo, hi):
    return item(f"{NS}:essence_{el}", w, lo, hi)


def pool(lo, hi, entries):
    return {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "bonus_rolls": 0, "entries": entries}


CRYSTAL = f"{NS}:mana_crystal"
LOOT = {
    "hydra_lair": [pool(5, 8, [item("minecraft:gold_ingot", 5, 3, 8), item("minecraft:diamond", 3, 1, 3),
                               item("minecraft:emerald", 4, 2, 6), item(CRYSTAL, 6, 3, 6), ess("water", 5, 2, 4),
                               ess("earth", 3, 1, 3), item("minecraft:slime_ball", 3, 2, 6), book(3),
                               item("minecraft:golden_apple", 2), item("minecraft:experience_bottle", 3, 3, 8),
                               sub("guild_coins", 3)]),
                   pool(1, 1, [item("minecraft:enchanted_golden_apple", 1), item("minecraft:diamond", 3, 2, 4),
                               item(CRYSTAL, 4, 4, 8)])],
    "wyvern_peak": [pool(5, 8, [item("minecraft:gold_ingot", 6, 4, 10), item("minecraft:raw_gold", 4, 3, 8),
                                item("minecraft:diamond", 3, 2, 4), item("minecraft:emerald", 3, 2, 6), item(CRYSTAL, 5, 3, 6),
                                ess("wind", 5, 2, 4), item("minecraft:phantom_membrane", 3, 1, 3), book(3),
                                item("minecraft:golden_carrot", 3, 3, 8), item("minecraft:feather", 2, 4, 12),
                                sub("guild_coins", 3)]),
                    pool(1, 1, [item("minecraft:trident", 1, fns=[{"function": "minecraft:set_damage",
                                                                   "damage": {"type": "minecraft:uniform", "min": 0.3, "max": 0.7}}]),
                                item("minecraft:diamond", 3, 2, 4), item(CRYSTAL, 4, 4, 8)])],
    "basilisk_tomb": [pool(5, 9, [item("minecraft:gold_ingot", 6, 3, 9), item("minecraft:lapis_lazuli", 4, 4, 12),
                                  item("minecraft:diamond", 3, 1, 3), item("minecraft:emerald", 4, 2, 6), item(CRYSTAL, 5, 3, 6),
                                  ess("earth", 5, 2, 4), ess("light", 2, 1, 2), book(3), item("minecraft:golden_apple", 2),
                                  item("minecraft:spyglass", 1), sub("guild_coins", 3)]),
                      pool(1, 1, [item("minecraft:enchanted_golden_apple", 1), item("minecraft:diamond", 3, 2, 4),
                                  item(CRYSTAL, 4, 4, 8)])],
    "colossus_ruins": [pool(5, 8, [item("minecraft:iron_ingot", 6, 4, 12), item("minecraft:iron_block", 2),
                                   item("minecraft:diamond", 3, 1, 3), item("minecraft:emerald", 3, 2, 6), item(CRYSTAL, 5, 3, 6),
                                   ess("earth", 5, 2, 4), book(3), item("minecraft:golden_apple", 2),
                                   item("minecraft:experience_bottle", 3, 3, 8), sub("guild_coins", 3)]),
                       pool(1, 1, [item("minecraft:netherite_upgrade_smithing_template", 1), item("minecraft:diamond", 3, 2, 4),
                                   item(CRYSTAL, 4, 4, 8)])],
    "elemental_gate": [pool(3, 5, [item(CRYSTAL, 6, 2, 5), ess("fire", 3, 1, 3), ess("water", 3, 1, 3), ess("earth", 3, 1, 3),
                                   ess("wind", 3, 1, 3), item("minecraft:gold_nugget", 3, 4, 12),
                                   item("minecraft:experience_bottle", 3, 2, 6), sub("guild_coins", 2)])],
    "abyssal_chasm": [pool(3, 5, [ess("shadow", 6, 2, 5), item(CRYSTAL, 5, 2, 5), item("minecraft:crying_obsidian", 3, 2, 5),
                                  item("minecraft:ender_pearl", 3, 1, 3), item("minecraft:echo_shard", 1),
                                  item("minecraft:gold_ingot", 3, 2, 6), book(2), sub("guild_coins", 2)])],
    "celestial_stairway": [pool(3, 5, [ess("light", 6, 2, 5), item(CRYSTAL, 5, 2, 5), item("minecraft:gold_ingot", 3, 2, 6),
                                       item("minecraft:glowstone_dust", 3, 4, 10), item("minecraft:golden_apple", 2),
                                       item("minecraft:honey_bottle", 2, 1, 3), book(2), sub("guild_coins", 2)])],
    "twilight_rupture": [pool(3, 5, [ess("light", 4, 1, 3), ess("shadow", 4, 1, 3), item(CRYSTAL, 5, 3, 6),
                                     item("minecraft:amethyst_shard", 3, 3, 8), item("minecraft:ender_pearl", 3, 1, 3),
                                     item("minecraft:diamond", 2, 1, 2), item("minecraft:experience_bottle", 3, 3, 8),
                                     sub("guild_coins", 2)])],
    "ascension_sanctum": [pool(3, 5, [ess("arcane", 6, 2, 5), item(CRYSTAL, 6, 3, 6), item("minecraft:amethyst_shard", 3, 3, 8),
                                      item("minecraft:lapis_lazuli", 3, 4, 10), item("minecraft:experience_bottle", 4, 3, 8),
                                      sub("guild_coins", 2)])],
}

SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def block_loot(bid, children):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
        {"type": "minecraft:alternatives", "children": children}]}], "random_sequence": f"{NS}:blocks/{bid}"}


def gen_loot():
    for sid, pools in LOOT.items():
        write_json(os.path.join(D, f"loot_table/chests/{sid}.json"),
                   {"type": "minecraft:chest", "pools": pools, "random_sequence": f"{NS}:chests/{sid}"})
    write_json(os.path.join(D, "loot_table/world_v09/guild_coins.json"), {
        "neoforge:conditions": [{"type": "neoforge:item_exists", "item": f"{NS}:guild_coin"}],
        "type": "minecraft:chest",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [item(f"{NS}:guild_coin", 1, 3, 10)]}]})
    fortune = {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}
    decay = {"function": "minecraft:explosion_decay"}
    for ore in ("mana_vein_ore", "deepslate_mana_vein_ore"):
        write_json(os.path.join(D, f"loot_table/blocks/{ore}.json"), block_loot(ore, [
            {"type": "minecraft:item", "name": f"{NS}:{ore}", "conditions": [SILK]},
            {"type": "minecraft:item", "name": CRYSTAL, "functions": [cnt(1, 3), fortune, decay]}]))
    write_json(os.path.join(D, "loot_table/blocks/mana_crystal_cluster.json"), block_loot("mana_crystal_cluster", [
        {"type": "minecraft:item", "name": f"{NS}:mana_crystal_cluster", "conditions": [SILK]},
        {"type": "minecraft:item", "name": CRYSTAL,
         "conditions": [{"condition": "minecraft:match_tool", "predicate": {"items": "#minecraft:cluster_max_harvestables"}}],
         "functions": [{"function": "minecraft:set_count", "count": 2.0, "add": False}, fortune]},
        {"type": "minecraft:item", "name": CRYSTAL,
         "functions": [{"function": "minecraft:set_count", "count": 1.0, "add": False}, decay]}]))


# =================================================================================================
#  TEXTURAS E MODELOS
# =================================================================================================
def read_png(data):
    """Leitor mínimo de PNG (8 bits; tons de cinza, RGB, paleta, cinza+alfa, RGBA; sem entrelaçamento)."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, plte, trns = 8, b"", None, None
    w = h = bd = ct = 0
    while pos < len(data):
        ln, typ = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", body[:10])
        elif typ == b"PLTE":
            plte = body
        elif typ == b"tRNS":
            trns = body
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    bits = ch * bd
    stride = (w * bits + 7) // 8
    bpp = max(1, bits // 8)
    raw = zlib.decompress(idat)
    rows, prev = [], bytearray(stride)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    px = []
    for y in range(h):
        line = rows[y]
        out = []
        for x in range(w):
            if bd < 8:
                bitpos = x * bd
                v = (line[bitpos // 8] >> (8 - bd - bitpos % 8)) & ((1 << bd) - 1)
                vals = [v]
            else:
                vals = list(line[x * ch:(x + 1) * ch])
            if ct == 3:
                i = vals[0]
                a = trns[i] if trns and i < len(trns) else 255
                out.append((plte[i * 3], plte[i * 3 + 1], plte[i * 3 + 2], a))
            elif ct == 0:
                g = vals[0] * 255 // ((1 << bd) - 1)
                out.append((g, g, g, 255))
            elif ct == 4:
                out.append((vals[0], vals[0], vals[0], vals[1]))
            elif ct == 2:
                out.append((vals[0], vals[1], vals[2], 255))
            else:
                out.append(tuple(vals))
        px.append(out)
    return w, h, px


def vanilla_texture(name):
    """Textura de bloco do Minecraft (jar do cliente no cache do Gradle) ou None."""
    pats = [os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_*_client.jar"),
            os.path.expanduser("~/.gradle/caches/**/client-extra*.jar")]
    for pat in pats:
        for jar in sorted(glob.glob(pat, recursive=True)):
            try:
                with zipfile.ZipFile(jar) as z:
                    return read_png(z.read(f"assets/minecraft/textures/block/{name}.png"))
            except (KeyError, zipfile.BadZipFile, OSError, AssertionError):
                continue
    return None


MANA = [(58, 30, 122, 255), (106, 63, 208, 255), (155, 107, 255, 255), (226, 210, 255, 255)]  # do Cristal de Mana


def base_canvas(name, fallback):
    c = Canvas(16, 16)
    tex = vanilla_texture(name)
    if tex and tex[0] == 16 and tex[1] == 16:
        for y in range(16):
            for x in range(16):
                c.set(x, y, tex[2][y][x])
    else:
        rnd = random.Random(name)
        for y in range(16):
            for x in range(16):
                c.set(x, y, darken(fallback, rnd.random() * 0.25))
    return c


GEM = [".H.", "LMD", "MDD", ".D."]
GEM2 = [".LH", "LMD", "DD."]


def ore_texture(base_name, fallback, out, deep=False):
    c = base_canvas(base_name, fallback)
    shapes = [(GEM, 2, 2), (GEM2, 10, 1), (GEM, 7, 8), (GEM2, 12, 11), (GEM2, 2, 12)]
    pal = {"D": MANA[0], "M": MANA[1], "L": MANA[2], "H": MANA[3]}
    if deep:
        pal["D"] = darken(MANA[0], 0.15)
    for shape, ox, oy in shapes:
        # contorno escuro em volta (como os minérios do vanilla)
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = ox + x + dx, oy + y + dy
                    inside = 0 <= ny - oy < len(shape) and 0 <= nx - ox < len(shape[0]) and shape[ny - oy][nx - ox] != "."
                    if not inside:
                        c.set(nx, ny, darken(c.get(nx, ny), 0.35))
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch != ".":
                    c.set(ox + x, oy + y, pal[ch])
    c.save(out)


def cluster_texture(out):
    c = Canvas(16, 16)
    D_, M_, L_, H_ = MANA

    def shard(x0, base_y, height, width, lean):
        for k in range(height):
            y = base_y - k
            t = k / max(1, height - 1)
            wcur = max(1, int(round(width * (1 - t * 0.75))))
            xs = x0 + int(round(lean * k))
            for i in range(wcur):
                col = L_ if i == 0 else (M_ if i < wcur - 1 or wcur == 1 else D_)
                if k == height - 1:
                    col = H_
                c.set(xs + i, y, col)
    shard(6, 15, 14, 4, 0.0)
    shard(2, 15, 9, 3, -0.15)
    shard(11, 15, 11, 3, 0.12)
    shard(9, 15, 6, 2, 0.3)
    shard(4, 15, 5, 2, -0.3)
    for (x, y) in ((7, 4), (3, 9), (12, 7)):
        c.set(x, y, H_)
    c.save(out)


def gen_assets():
    tex = os.path.join(A, "textures/block")
    os.makedirs(tex, exist_ok=True)
    ore_texture("stone", (125, 125, 125, 255), os.path.join(tex, "mana_vein_ore.png"))
    ore_texture("deepslate", (77, 77, 82, 255), os.path.join(tex, "deepslate_mana_vein_ore.png"), deep=True)
    cluster_texture(os.path.join(tex, "mana_crystal_cluster.png"))
    for ore in ("mana_vein_ore", "deepslate_mana_vein_ore"):
        write_json(os.path.join(A, f"blockstates/{ore}.json"), {"variants": {"": {"model": f"{NS}:block/{ore}"}}})
        write_json(os.path.join(A, f"models/block/{ore}.json"),
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{ore}"}})
        write_json(os.path.join(A, f"models/item/{ore}.json"), {"parent": f"{NS}:block/{ore}"})
    rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "east": {"x": 90, "y": 90},
           "west": {"x": 90, "y": 270}}
    write_json(os.path.join(A, "blockstates/mana_crystal_cluster.json"), {"variants": {
        f"facing={f}": {"model": f"{NS}:block/mana_crystal_cluster", **v} for f, v in rot.items()}})
    write_json(os.path.join(A, "models/block/mana_crystal_cluster.json"),
               {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                "textures": {"cross": f"{NS}:block/mana_crystal_cluster"}})
    write_json(os.path.join(A, "models/item/mana_crystal_cluster.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/mana_crystal_cluster"}})


def gen_lang():
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(LANG_PT.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(LANG_EN.items())))


# =================================================================================================
#  CONFERÊNCIA DOS MOLDES
# =================================================================================================
OWN_OR_KNOWN = {TRAVEL_STONE, EL_FRAME, AB_FRAME, BEACON, RIFT, ASC_CORE, ASC_PEDESTAL, RUNE_STONE,
                f"{NS}:mana_crystal_cluster"} | {f"{NS}:elemental_brick_{e}" for e in ("fire", "water", "earth", "wind")}


def err(s, msg):
    ERRORS.append(f"{s.name}: {msg}")


def check_frame(s, frame):
    cells = [(x, y, z) for (x, y, z), (st_, _n) in s.blocks.items() if base_name(st_) == frame]
    if not cells:
        err(s, f"sem moldura {frame}")
        return
    xs = [p[0] for p in cells]
    ys = [p[1] for p in cells]
    zs = {p[2] for p in cells}
    if len(zs) != 1:
        err(s, "moldura fora de um único plano z")
        return
    z = zs.pop()
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    w, h = x1 - x0 - 1, y1 - y0 - 1
    if not (2 <= w <= 21 and 3 <= h <= 21):
        err(s, f"interior da moldura {w}x{h} fora do limite (mín. 2x3)")
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            v = s.get(x, y, z)
            if edge and base_name(v or "") != frame and not (x in (x0, x1) and y in (y0, y1)):
                err(s, f"moldura com falha em {(x, y, z)}")
            if not edge and v != AIR:
                err(s, f"interior da moldura não é ar em {(x, y, z)}: {v}")


def check_all():
    by = {s.name: s for s in BUILT}
    for name in SITES:
        s = by.get(name)
        if s is None:
            ERRORS.append(f"{name}: molde não gerado")
            continue
        stones = [p for p, (v, _n) in s.blocks.items() if v == TRAVEL_STONE]
        if len(stones) != 1:
            err(s, f"{len(stones)} pedras de teleporte (esperado 1)")
        for (x, y, z) in stones:
            if s.get(x, y + 1, z) != AIR or s.get(x, y + 2, z) != AIR:
                err(s, "pedra de teleporte sem 2 blocos de ar em cima")
            if not s.get(x, y - 1, z) or s.get(x, y - 1, z) == AIR:
                err(s, "pedra de teleporte sem apoio")
        if name in BOSSES:
            want = f"{NS}:{BOSSES[name][0]}"
            if not any(e["nbt"]["id"] == want and "isekai_mahou.lair_boss" in e["nbt"].get("Tags", []) for e in s.entities):
                err(s, f"sem o chefe {want} no molde")
        for (x, y, z), (v, nbt) in s.blocks.items():
            n = base_name(v)
            if n.startswith(NS + ":") and n not in OWN_OR_KNOWN:
                err(s, f"bloco do mod desconhecido {n}")
            if n.endswith("_sign") and "wall" not in n:
                below = s.get(x, y - 1, z)
                if not below or below == AIR:
                    err(s, f"placa sem apoio em {(x, y, z)}")
            if nbt and "front_text" in nbt:
                for m in nbt["front_text"]["messages"]:
                    k = json.loads(m).get("translate") if m != '""' else None
                    if k and (k not in LANG_PT or k not in LANG_EN):
                        err(s, f"letreiro sem tradução {k}")
            if nbt and "LootTable" in nbt:
                lt = nbt["LootTable"].split(":", 1)[1]
                if not os.path.exists(os.path.join(D, f"loot_table/{lt}.json")):
                    err(s, f"loot table inexistente {nbt['LootTable']}")
            if n in ("minecraft:wall_torch", "minecraft:soul_wall_torch") or n.endswith("_wall_sign") or n.endswith("_wall_banner"):
                face = v.split("facing=", 1)[1].split(",")[0].split("]")[0]
                dx, dz = DIRV[face]
                behind = s.get(x - dx, y, z - dz)
                if not behind or behind == AIR or not is_full(behind):
                    err(s, f"{n} sem parede atrás em {(x, y, z)}")
    if "elemental_gate" in by:
        check_frame(by["elemental_gate"], EL_FRAME)
    if "abyssal_chasm" in by:
        check_frame(by["abyssal_chasm"], AB_FRAME)
    s = by.get("celestial_stairway")
    if s:
        bc = [p for p, (v, _n) in s.blocks.items() if base_name(v) == BEACON]
        if len(bc) != 1:
            err(s, "esperado 1 Farol Celestial")
        for (x, y, z) in bc:
            if s.get(x, y - 1, z) != "minecraft:gold_block":
                err(s, "Farol sem ouro embaixo")
            for yy in range(y + 1, s.size[1]):
                if s.get(x, yy, z) not in (None, AIR):
                    err(s, f"algo acima do Farol em y={yy}")
            if any(s.get(x, y + i, z) != AIR for i in range(1, 5)):
                err(s, "Farol sem 4 blocos de ar explícitos em cima")
    # o "ground" de cada molde tem que bater com V09Site.ground no Java (centro do lugar registrado)
    java = open(os.path.join(ROOT, "src/main/java/com/renanpontes/isekaimahou/feature/world_v09/V09Site.java"),
                encoding="utf-8").read()
    for name, s in by.items():
        m = re.search(r'"%s", Kind\.[A-Z]+, 0x[0-9A-Fa-f]+, (\d+)\)' % name, java)
        if not m or int(m.group(1)) != s.ground:
            err(s, f"V09Site.ground no Java difere do molde ({m.group(1) if m else '?'} != {s.ground})")
    s = by.get("twilight_rupture")
    if s and not any(base_name(v) == RIFT for v, _n in s.blocks.values()):
        err(s, "sem a Fenda (twilight_rift)")
    s = by.get("ascension_sanctum")
    if s:
        cores = [(p, n) for p, (v, n) in s.blocks.items() if v == ASC_CORE]
        if len(cores) != 1:
            err(s, "esperado 1 núcleo de ascensão")
        for (x, y, z), nbt in cores:
            if not nbt or nbt.get("PublicAltar") != 1 or nbt.get("NeoForgeData", {}).get("PublicAltar") != 1:
                err(s, "núcleo sem PublicAltar:1b")
            for dx, dz in ALTAR_PEDESTALS:
                if s.get(x + dx, y, z + dz) != ASC_PEDESTAL:
                    err(s, f"pedestal faltando em {(dx, dz)}")
            for dx, dz in ALTAR_RUNES:
                if s.get(x + dx, y, z + dz) != RUNE_STONE:
                    err(s, f"pedra rúnica faltando em {(dx, dz)}")
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    for yy in range(y + 1, s.size[1]):
                        if s.get(x + dx, yy, z + dz) not in (None, AIR):
                            err(s, f"bloco sobre o altar em {(dx, yy, dz)}")


# =================================================================================================
#  PRÉVIA ASCII
# =================================================================================================
def preview(s, ys):
    def ch(v):
        if v is None:
            return " "
        if v == AIR:
            return "."
        n = base_name(v).split(":", 1)[1]
        for k, c in (("travel_stone", "T"), ("portal_frame", "#"), ("beacon", "B"), ("rift", "R"), ("ascension_core", "C"),
                     ("pedestal", "P"), ("rune_stone", "r"), ("chest", "$"), ("water", "~"), ("sign", "s"),
                     ("lantern", "*"), ("cluster", "+"), ("stairs", "/"), ("slab", "_"), ("wall", "|"), ("log", "L")):
            if k in n:
                return c
        return "#" if is_full(v) else "o"
    for y in ys:
        print(f"-- {s.name} y={y}")
        for z in range(s.size[2]):
            print("".join(ch(s.get(x, y, z)) for x in range(s.size[0])))


def main():
    hydra_lair()
    wyvern_peak()
    basilisk_tomb()
    colossus_ruins()
    elemental_gate()
    abyssal_chasm()
    celestial_stairway()
    twilight_rupture()
    ascension_sanctum()
    gen_worldgen()
    gen_loot()
    gen_assets()
    gen_lang()
    check_all()
    for s in BUILT:
        print(f"{s.name}: {s.size} {len(s.blocks)} blocos, {len(s.entities)} entidades")
    if "--preview" in sys.argv:
        name = sys.argv[sys.argv.index("--preview") + 1]
        for s in BUILT:
            if s.name == name:
                preview(s, sorted({s.ground - 1, s.ground, s.ground + 1, s.ground + 3}))
    if ERRORS:
        print("ERROS:")
        for e in ERRORS:
            print("  " + e)
        sys.exit(1)
    print("OK: moldes, worldgen, loot, texturas e traduções conferidos.")


if __name__ == "__main__":
    main()
