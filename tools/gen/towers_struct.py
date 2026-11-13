"""
Moldes .nbt das Torres de Desafio (modulo towers): 6 variantes, 7 andares internos + pinaculo.
Importado por tools/gen/towers.py (nao rodar sozinho).

v1.0 (release) — "Melhore o design do que for possivel!": as torres deixaram de ser caixas de tijolo. Por fora agora tem
base em talude, alpendre com telhado, contrafortes em tres degraus, torreoes redondos nos cantos com telhado em agulha,
janelas com vitral (peitoril e pingadeira), sacadas na fachada sul, estandartes, cornija e uma coroa (pavilhao com
telhado) sobre o pinaculo. Variantes novas: floresta, submersa (meio dentro do mar) e flutuante (numa ilha no ceu).

O INTERIOR nao mudou (o Nucleo do Andar e os comandos contam com ele). Coordenadas "da torre" (as de sempre):
  - paredes em x=2/22 e z=2/22; interior 3..21 (19 x 19); centro (12, 12); porta ao sul (z=22);
  - laje do andar k em y = 2 + 8k (k = 0..7; k=7 e o teto/pinaculo); ar do andar em Yk+1..Yk+7;
  - andares pares: escada (8 degraus) na parede leste (x 20..21) subindo para o norte, buraco no teto em z 13..15;
    andares impares: escada na parede oeste (x 3..4) subindo para o sul, buraco no teto em z 9..11;
  - o buraco do teto e fechado por Selos da Torre (tower_seal), que o Nucleo do Andar remove quando o andar e vencido;
  - Nucleo do Andar (tower_floor_core, com o tipo do andar no NBT) no centro de cada andar, em Yk+1.
O molde tem uma margem `off` em volta (para o que fica do lado de fora) e, na flutuante, `base` camadas de ilha embaixo:
bloco da torre (x, y, z) -> molde (x + off, y + base, z + off). O centro continua no meio do molde.
"""
import json
import math
import random

from structgen import Structure, Byte

NS = "isekai_mahou"
# Os moldes da v1.0 ficam numa pasta nova. Os tres antigos (structure/towers/tower_{stone,frost,sand}.nbt, 25x68x25, da
# v1.6) CONTINUAM no projeto e nao sao mais gerados: torres ja sorteadas em mundos salvos (inicio gravado, chunks
# vizinhos ainda por gerar) guardam o nome do molde antigo, e trocar o arquivo deixaria a torre cortada ao meio.
TEMPLATE_DIR = "towers/v10"
FLOORS = 7                       # andares internos (o 8o e o pinaculo)
AIR = "minecraft:air"
SY = 80                          # altura da torre (com agulhas e coroa)
ISLAND_H = 14                    # camadas de ilha embaixo da Torre Flutuante (= TowerData.FLOATING_BASE no Java)
SEA = 25                         # submersa: altura (da torre) do ultimo bloco de agua (mundo y=62 com a torre em y=37)
SUNKEN_Y = 62 - SEA              # altura absoluta do molde da submersa
WELL_Z = 28                      # flutuante: z (da torre) do centro do poco da Corrente Ascendente (x = 12)

# Tipo de cada andar: (tipo, ondas, numero de runas)
PLAN = [("combat", 2, 0), ("puzzle", 0, 4), ("combat", 3, 0), ("treasure", 1, 0),
        ("puzzle", 0, 6), ("combat", 3, 0), ("elite", 3, 0), ("boss", 0, 0)]
BALCONIES = (2, 4, 6)            # andares com sacada na fachada sul

VARIANTS = {
    "stone": dict(
        wall=[("minecraft:stone_bricks", 6), ("minecraft:mossy_stone_bricks", 2), ("minecraft:cracked_stone_bricks", 1)],
        trim="minecraft:chiseled_stone_bricks", corner="minecraft:polished_andesite",
        floor_a="minecraft:polished_andesite", floor_b="minecraft:stone_bricks", light="minecraft:glowstone",
        stairs="minecraft:stone_brick_stairs", slab="minecraft:stone_brick_slab", rail="minecraft:stone_brick_wall",
        pillar="minecraft:chiseled_stone_bricks", sign="minecraft:spruce_wall_sign", cap="minecraft:deepslate_tiles",
        cap_stairs="minecraft:deepslate_tile_stairs", found="minecraft:cobblestone", banner="minecraft:gray_wall_banner",
        deco=["minecraft:barrel[facing=up]", "minecraft:cobweb", "minecraft:chiseled_bookshelf"], sign_color="white",
        glass="minecraft:orange_stained_glass"),
    "frost": dict(
        wall=[("minecraft:polished_diorite", 5), ("minecraft:calcite", 3), ("minecraft:diorite", 1)],
        trim="minecraft:packed_ice", corner="minecraft:quartz_pillar[axis=y]",
        floor_a="minecraft:polished_diorite", floor_b="minecraft:smooth_quartz", light="minecraft:sea_lantern",
        stairs="minecraft:polished_diorite_stairs", slab="minecraft:polished_diorite_slab", rail="minecraft:diorite_wall",
        pillar="minecraft:packed_ice", sign="minecraft:spruce_wall_sign", cap="minecraft:blue_ice",
        cap_stairs="minecraft:prismarine_brick_stairs", found="minecraft:packed_ice", banner="minecraft:light_blue_wall_banner",
        deco=["minecraft:barrel[facing=up]", "minecraft:powder_snow_cauldron[level=3]", "minecraft:blue_ice"], sign_color="light_blue",
        glass="minecraft:light_blue_stained_glass", cap_block="minecraft:prismarine_bricks"),
    "sand": dict(
        wall=[("minecraft:cut_sandstone", 5), ("minecraft:sandstone", 4), ("minecraft:smooth_sandstone", 1)],
        trim="minecraft:chiseled_sandstone", corner="minecraft:cut_red_sandstone",
        floor_a="minecraft:smooth_sandstone", floor_b="minecraft:cut_red_sandstone", light="minecraft:shroomlight",
        stairs="minecraft:sandstone_stairs", slab="minecraft:sandstone_slab", rail="minecraft:sandstone_wall",
        pillar="minecraft:chiseled_red_sandstone", sign="minecraft:birch_wall_sign", cap="minecraft:orange_terracotta",
        cap_stairs="minecraft:red_sandstone_stairs", found="minecraft:sandstone", banner="minecraft:orange_wall_banner",
        deco=["minecraft:barrel[facing=up]", "minecraft:decorated_pot", "minecraft:chiseled_sandstone"], sign_color="orange",
        glass="minecraft:yellow_stained_glass", cap_block="minecraft:red_sandstone"),
    # ---------------- v1.0
    "forest": dict(
        wall=[("minecraft:mossy_stone_bricks", 5), ("minecraft:mossy_cobblestone", 3), ("minecraft:tuff_bricks", 2),
              ("minecraft:moss_block", 1)],
        trim="minecraft:chiseled_tuff_bricks", corner="minecraft:polished_tuff",
        floor_a="minecraft:polished_tuff", floor_b="minecraft:mossy_stone_bricks", light="minecraft:verdant_froglight",
        stairs="minecraft:mossy_stone_brick_stairs", slab="minecraft:mossy_stone_brick_slab",
        rail="minecraft:mossy_stone_brick_wall", pillar="minecraft:stripped_dark_oak_log[axis=y]",
        sign="minecraft:jungle_wall_sign", cap="minecraft:dark_prismarine", cap_stairs="minecraft:dark_prismarine_stairs",
        found="minecraft:mossy_cobblestone", banner="minecraft:green_wall_banner",
        deco=["minecraft:barrel[facing=up]", "minecraft:flowering_azalea", "minecraft:moss_block"], sign_color="lime",
        glass="minecraft:lime_stained_glass", overgrown=True),
    "sunken": dict(
        wall=[("minecraft:prismarine_bricks", 5), ("minecraft:prismarine", 3), ("minecraft:dark_prismarine", 1)],
        trim="minecraft:dark_prismarine", corner="minecraft:dark_prismarine",
        floor_a="minecraft:prismarine_bricks", floor_b="minecraft:dark_prismarine", light="minecraft:sea_lantern",
        stairs="minecraft:prismarine_brick_stairs", slab="minecraft:prismarine_brick_slab", rail="minecraft:prismarine_wall",
        pillar="minecraft:dark_prismarine", sign="minecraft:warped_wall_sign", cap="minecraft:oxidized_cut_copper",
        cap_stairs="minecraft:oxidized_cut_copper_stairs", found="minecraft:prismarine", banner="minecraft:cyan_wall_banner",
        deco=["minecraft:barrel[facing=up]", "minecraft:dried_kelp_block", "minecraft:prismarine"], sign_color="cyan",
        glass="minecraft:cyan_stained_glass", off=8, dry_from=3, sea=SEA),
    "floating": dict(
        wall=[("minecraft:quartz_bricks", 5), ("minecraft:calcite", 3), ("minecraft:smooth_quartz", 2)],
        trim="minecraft:chiseled_quartz_block", corner="minecraft:quartz_pillar[axis=y]",
        floor_a="minecraft:smooth_quartz", floor_b="minecraft:purpur_block", light="minecraft:pearlescent_froglight",
        stairs="minecraft:quartz_stairs", slab="minecraft:quartz_slab", rail="minecraft:diorite_wall",
        pillar="minecraft:purpur_pillar[axis=y]", sign="minecraft:cherry_wall_sign", cap="minecraft:purpur_block",
        cap_stairs="minecraft:purpur_stairs", found="minecraft:smooth_quartz", banner="minecraft:magenta_wall_banner",
        deco=["minecraft:barrel[facing=up]", "minecraft:amethyst_block", "minecraft:chiseled_bookshelf"], sign_color="magenta",
        glass="minecraft:magenta_stained_glass", off=13, base=ISLAND_H, island=True),
}

# Faces da torre: pos(c, d) -> (x, z), com c ao longo da parede (2..22) e d = distancia para FORA da parede (0 = parede).
FACES = {
    "north": dict(pos=lambda c, d: (c, 2 - d), out="north", inn="south"),
    "south": dict(pos=lambda c, d: (c, 22 + d), out="south", inn="north"),
    "west": dict(pos=lambda c, d: (2 - d, c), out="west", inn="east"),
    "east": dict(pos=lambda c, d: (22 + d, c), out="east", inn="west"),
}
TURRETS = ((2, 2), (22, 2), (2, 22), (22, 22))
BUTTRESS_C = (9, 15)


def disc(r):
    """Celulas (dx, dz) de um disco arredondado de raio r."""
    return [(dx, dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) if dx * dx + dz * dz <= r * r + r]


class T(Structure):
    def __init__(self, variant):
        self.v = VARIANTS[variant]
        self.variant = variant
        self.off = self.v.get("off", 4)
        self.base = self.v.get("base", 0)
        self.sea = self.v.get("sea")             # ultimo y (da torre) com agua, ou None
        self.dry_from = self.v.get("dry_from", 0)  # primeiro andar acima da agua
        super().__init__(25 + 2 * self.off, SY + self.base, 25 + 2 * self.off)
        self.rnd = random.Random("towers/" + variant)

    # coordenadas da torre -> molde
    def set(self, x, y, z, state, nbt=None):
        super().set(x + self.off, y + self.base, z + self.off, state, nbt)

    def get(self, x, y, z):
        return super().get(x + self.off, y + self.base, z + self.off)

    def empty(self, x, y, z):
        g = self.get(x, y, z)
        return g is None or g == AIR

    def wall(self):
        tot = sum(w for _, w in self.v["wall"])
        r = self.rnd.random() * tot
        for s, w in self.v["wall"]:
            r -= w
            if r <= 0:
                return s
        return self.v["wall"][0][0]

    def wet(self, y):
        """Bloco de FORA que fica embaixo d'agua (submersa): escadas/muros ja nascem alagados."""
        return self.sea is not None and y <= self.sea

    def xstair(self, x, y, z, facing, half="bottom", mat=None):
        """Escada do lado de fora (alagada se estiver embaixo d'agua)."""
        wl = "true" if self.wet(y) else "false"
        self.set(x, y, z, f"{mat or self.v['stairs']}[facing={facing},half={half},shape=straight,waterlogged={wl}]")

    def pane(self, x, y, z, dry=True):
        """Vitral: vidraca acima da agua, bloco de vidro inteiro embaixo (nao pode vazar)."""
        if dry:
            self.set(x, y, z, self.v["glass"] + "_pane[east=false,north=false,south=false,west=false,waterlogged=false]")
        else:
            self.set(x, y, z, self.v["glass"])

    def chest(self, x, y, z, loot, facing):
        self.set(x, y, z, f"minecraft:chest[facing={facing},type=single,waterlogged=false]",
                 nbt={"LootTable": f"{NS}:chests/{loot}"})

    def sign(self, x, y, z, facing, keys):
        msgs = [json.dumps({"translate": k}) for k in keys] + ['""'] * (4 - len(keys))
        self.set(x, y, z, f"{self.v['sign']}[facing={facing},waterlogged=false]", nbt={
            "front_text": {"messages": msgs, "color": self.v["sign_color"], "has_glowing_text": Byte(1)},
            "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": Byte(0)},
            "is_waxed": Byte(1)})

    def seal(self, x, y, z):
        self.set(x, y, z, f"{NS}:tower_seal")


def Y(k):
    return 2 + 8 * k


# =====================================================================================
#  ILHA (usada pela Torre Flutuante e pelas Ilhas Voadoras de towers_sky.py)
# =====================================================================================
STONES = [("minecraft:stone", 66), ("minecraft:andesite", 10), ("minecraft:tuff", 6), ("minecraft:calcite", 5),
          ("minecraft:copper_ore", 3), ("minecraft:iron_ore", 3), ("minecraft:amethyst_block", 2),
          ("minecraft:gold_ore", 1.5), ("minecraft:emerald_ore", 0.5), ("minecraft:mossy_cobblestone", 3)]
FLOWERS = ["minecraft:short_grass"] * 6 + ["minecraft:dandelion", "minecraft:poppy", "minecraft:azure_bluet",
                                             "minecraft:oxeye_daisy", "minecraft:cornflower"]


def _pick(rnd, table):
    tot = sum(w for _, w in table)
    r = rnd.random() * tot
    for s, w in table:
        r -= w
        if r <= 0:
            return s
    return table[0][0]


def island(s, cx, cz, top, radius, depth, seed, solid=()):
    """
    Ilha voadora: disco irregular com a barriga em cone para baixo. `top` = y do bloco de grama; a parte mais grossa tem
    `depth` blocos. `solid` = lista de (x, z, r): circulos onde o chao e garantido (poco, ruinas na borda).
    Devolve {(x, z): espessura}.
    """
    rnd = random.Random(seed)
    ph = [rnd.random() * math.tau for _ in range(3)]
    cols = {}
    for x in range(cx - radius - 3, cx + radius + 4):
        for z in range(cz - radius - 3, cz + radius + 4):
            dx, dz = x - cx, z - cz
            d = math.hypot(dx, dz)
            ang = math.atan2(dz, dx)
            r = radius * (1 + 0.09 * math.sin(3 * ang + ph[0]) + 0.06 * math.sin(5 * ang + ph[1]) + 0.03 * math.sin(9 * ang + ph[2]))
            forced = any(math.hypot(x - fx, z - fz) <= fr for fx, fz, fr in solid)
            if d > r and not forced:
                continue
            f = min(1.0, d / r)
            t = 2 + (depth - 2) * (1 - f ** 1.8) ** 0.85 + (rnd.random() - 0.5) * 1.6
            t = max(3 if forced else 1, min(depth, int(round(t))))
            cols[(x, z)] = t
            for i in range(t):
                if i == 0:
                    b = "minecraft:grass_block[snowy=false]"
                elif i <= 2:
                    b = "minecraft:dirt"
                else:
                    b = _pick(rnd, STONES)
                s.set(x, top - i, z, b)
            if t >= 4 and rnd.random() < 0.12:
                s.set(x, top - t, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]")
    return cols


def meadow(s, cols, top, seed, density=0.22, keep_clear=()):
    """Capim e flores em cima da grama livre. `keep_clear` = (x, z, r) sem plantas."""
    rnd = random.Random(seed)
    for (x, z) in sorted(cols):
        if s.get(x, top, z) != "minecraft:grass_block[snowy=false]" or s.get(x, top + 1, z) is not None:
            continue
        if any(math.hypot(x - fx, z - fz) <= fr for fx, fz, fr in keep_clear):
            continue
        if rnd.random() < density:
            s.set(x, top + 1, z, rnd.choice(FLOWERS))


def tree(s, x, y, z, log="minecraft:oak_log", leaves="minecraft:oak_leaves", height=4, seed=0):
    """Arvore pequena (tronco + copa). y = primeiro bloco do tronco."""
    rnd = random.Random(seed)
    lv = f"{leaves}[distance=1,persistent=true,waterlogged=false]"
    for i in range(height):
        s.set(x, y + i, z, f"{log}[axis=y]")
    for dy, r in ((height - 2, 2), (height - 1, 2), (height, 1), (height + 1, 1)):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r and (r == 1 or rnd.random() < 0.6):
                    continue
                if (dx, dz) == (0, 0) and dy < height:
                    continue
                if s.get(x + dx, y + dy, z + dz) is None:
                    s.set(x + dx, y + dy, z + dz, lv)


def well(s, cx, cz, top, bottom, ring, corner, light):
    """
    Poco da Corrente Ascendente: coluna 3x3 de ar atravessando a ilha (o Java levita quem entra nela por baixo) com um
    aro 5x5 no nivel do chao. O meio de cada lado do aro fica LIVRE (e ali que o jogador pousa: centro + 2).
    """
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            edge = max(abs(x - cx), abs(z - cz)) == 2
            if edge:
                s.set(x, top, z, ring)
                s.set(x, top - 1, z, ring)
                for y in (top + 1, top + 2, top + 3):
                    s.set(x, y, z, AIR)
            else:
                for y in range(bottom, top + 4):
                    s.set(x, y, z, AIR)
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        s.set(cx + dx, top + 1, cz + dz, corner)
        s.set(cx + dx, top + 2, cz + dz, light)


# =====================================================================================
#  TORRE
# =====================================================================================
def build(variant):
    s = T(variant)
    v = s.v
    top = Y(FLOORS)  # 58: teto do ultimo andar = chao do pinaculo
    sunken = s.sea is not None

    # ---------------- ilha (flutuante): antes de tudo, a torre vai por cima
    cols = None
    if v.get("island"):
        cols = island(s, 12, 12, 1, 22, ISLAND_H, "towers/island", solid=[(12, WELL_Z, 4.5)])

    # ---------------- fundacao e praca em volta (y 0..1)
    for x in range(-1, 26):
        for z in range(-1, 26):
            if (x in (-1, 25)) and (z in (-1, 25)):
                continue
            s.set(x, 0, z, v["found"])
            inner = 3 <= x <= 21 and 3 <= z <= 21
            s.set(x, 1, z, v["floor_b"] if inner or (x + z) % 3 == 0 else v["found"])
    if not sunken:
        # limpa o ar na frente da porta (sem arvore/terra no caminho)
        s.fill(6, 2, 23, 18, 8, 27, AIR)
        for x in range(10, 15):
            s.set(x, 2, 23, f"{v['stairs']}[facing=north,half=bottom,shape=straight,waterlogged=false]")

    # ---------------- paredes externas e cintas em cada laje
    for y in range(2, top + 1):
        for i in range(2, 23):
            for (x, z) in ((i, 2), (i, 22), (2, i), (22, i)):
                band = (y - 2) % 8 == 0
                s.set(x, y, z, v["trim"] if band else s.wall())

    # ---------------- lajes (todas antes do conteudo: os selos do buraco ficam POR CIMA da laje de cima)
    for k in range(FLOORS + 1):
        y0 = Y(k)
        for x in range(3, 22):
            for z in range(3, 22):
                s.set(x, y0, z, v["floor_a"] if ((x // 2 + z // 2) % 2 == 0) else v["floor_b"])
        for lx in (6, 12, 18):
            for lz in (6, 12, 18):
                if (lx, lz) != (12, 12):
                    s.set(lx, y0, lz, v["light"])

    # ---------------- andares
    for k in range(FLOORS):
        y0 = Y(k)
        s.fill(3, y0 + 1, 3, 21, y0 + 7, 21, AIR)
        # pilares com tochas
        for (px, pz) in ((7, 7), (17, 7), (7, 17), (17, 17)):
            for y in range(y0 + 1, y0 + 8):
                s.set(px, y, pz, v["pillar"])
            s.set(px, y0 + 4, pz - 1, "minecraft:wall_torch[facing=north]")
            s.set(px, y0 + 4, pz + 1, "minecraft:wall_torch[facing=south]")
        windows(s, k)
        stairs_floor(s, k)
        if k > 0:
            arrival_rail(s, Y(k) + 1, (k - 1) % 2 == 0)
        furnish(s, k)
        kind, waves, _ = PLAN[k]
        core(s, variant, 12, y0 + 1, 12, k, "combat" if kind in ("treasure", "elite") else kind, waves, kind == "elite")

    # ---------------- porta (andar 0, parede sul)
    s.fill(11, 3, 22, 13, 5, 22, AIR)
    s.set(11, 6, 22, v["trim"]); s.set(12, 6, 22, v["trim"]); s.set(13, 6, 22, v["trim"])
    door = [f"sign.{NS}.towers.door_1", f"sign.{NS}.towers.door_2.{variant}", f"sign.{NS}.towers.door_3"]

    # ---------------- lado de fora
    plinth(s)
    turrets(s, top)
    buttresses(s)
    cornice(s, top)
    banners(s)
    for k in BALCONIES:
        if k >= s.dry_from:
            balcony(s, k)
    if sunken:
        tube(s, door)
    else:
        porch(s, door)

    pinnacle(s, top)
    crown(s, top)
    if v.get("overgrown"):
        overgrow(s, top)
    if cols is not None:
        # poco da Corrente Ascendente na frente da porta (SkySites.LAYOUTS no Java: x = 12 + off, z = WELL_Z + off)
        well(s, 12, WELL_Z, 1, 1 - ISLAND_H - 1, v["trim"], v["corner"], v["light"])
        for i, (tx, tz) in enumerate(((-6, 20), (30, 18), (29, 2), (-5, 4), (1, 30), (23, 30), (12, -6))):
            if cols.get((tx, tz), 0) >= 3 and s.empty(tx, 2, tz):
                tree(s, tx, 2, tz, "minecraft:cherry_log", "minecraft:cherry_leaves", 4 + i % 2, seed=i)
        meadow(s, cols, 1, "towers/island/meadow", keep_clear=[(12, WELL_Z, 3.5)])
    return s


def windows(s, k):
    """Janelas do andar k: frestas com vitral nos lados e uma janela grande (ou sacada/porta) no meio de cada face."""
    v = s.v
    y0 = Y(k)
    dry = k >= s.dry_from
    bars = "minecraft:iron_bars[east=false,north=false,south=false,west=false,waterlogged=false]"
    for name, f in FACES.items():
        pos, inn = f["pos"], f["inn"]
        # frestas laterais (no terreo, grades de fortaleza)
        for c in (7, 17):
            for wy in range(y0 + 3, y0 + 6):
                x, z = pos(c, 0)
                if k == 0 and dry:
                    s.set(x, wy, z, bars)
                else:
                    s.pane(x, wy, z, dry)
            x, z = pos(c, 1)
            s.xstair(x, y0 + 2, z, inn, "top")
            s.xstair(x, y0 + 6, z, inn, "bottom")
        # meio da face
        if name == "south" and (k == 0 or (k in BALCONIES and dry)):
            continue                                     # porta / sacada
        if name == "south" and s.sea is not None and k <= s.dry_from:
            continue                                     # submersa: o tubo e o quiosque do cais ficam ali
        big = k > 0
        if name == "north" and PLAN[k][0] == "puzzle":
            continue                                     # as runas ficam nessa parede
        if (name == "east" and k % 2 == 0) or (name == "west" and k % 2 == 1):
            big = False                                  # parede da escada: so a fresta
        if big:
            for c in (11, 12, 13):
                for wy in range(y0 + 3, y0 + 6):
                    x, z = pos(c, 0)
                    s.pane(x, wy, z, dry)
                x, z = pos(c, 1)
                s.xstair(x, y0 + 2, z, inn, "top")
            x, z = pos(12, 0)
            s.pane(x, y0 + 6, z, dry)
            for c in (11, 13):
                x, z = pos(c, 1)
                s.xstair(x, y0 + 6, z, inn, "bottom")
            x, z = pos(12, 1)
            s.xstair(x, y0 + 7, z, inn, "bottom")
        else:
            for wy in range(y0 + 3, y0 + 6):
                x, z = pos(12, 0)
                if k == 0 and dry:
                    s.set(x, wy, z, bars)
                else:
                    s.pane(x, wy, z, dry)


def stairs_floor(s, k):
    """Escada do andar k ate o buraco do teto (fechado por selos)."""
    v = s.v
    y0 = Y(k)
    if k % 2 == 0:
        xs, facing = (20, 21), "north"
        zs = [20 - i for i in range(1, 9)]          # 19..12 (o 8o degrau fica na laje de cima)
        hole = (13, 14, 15)
    else:
        xs, facing = (3, 4), "south"
        zs = [4 + i for i in range(1, 9)]           # 5..12
        hole = (9, 10, 11)
    for i, z in enumerate(zs, start=1):
        for x in xs:
            for y in range(y0 + 1, y0 + i):
                s.set(x, y, z, v["floor_b"])
            s.set(x, y0 + i, z, f"{v['stairs']}[facing={facing},half=bottom,shape=straight,waterlogged=false]")
    for x in xs:
        for z in hole:
            s.seal(x, y0 + 8, z)


def core(s, variant, x, y, z, floor, kind, waves, elite):
    s.set(x, y, z, f"{NS}:tower_floor_core[cleared=false]", nbt={
        "Variant": variant, "Floor": floor, "Kind": kind, "Waves": waves, "Elite": Byte(1 if elite else 0)})


def arrival_rail(s, y, east):
    """Parapeito em volta do buraco por onde se chega ao andar (vindo de baixo)."""
    v = s.v
    rail = v["rail"] + "[east=none,north=none,south=none,west=none,up=true,waterlogged=false]"
    if east:   # chegou pela escada leste
        for z in (13, 14, 15, 16):
            s.set(19, y, z, rail)
        for x in (20, 21):
            s.set(x, y, 16, rail)
    else:                  # chegou pela escada oeste
        for z in (8, 9, 10, 11):
            s.set(5, y, z, rail)
        for x in (3, 4):
            s.set(x, y, 8, rail)


def furnish(s, k):
    v = s.v
    y = Y(k) + 1
    kind = PLAN[k][0]
    rnd = s.rnd
    # cantos com tralha (sem atrapalhar escadas e buracos)
    for (cx, cz) in ([(4, 20), (4, 4)] if k % 2 == 0 else [(20, 20), (20, 4)]):
        s.set(cx, y, cz, rnd.choice(v["deco"]))
    # estandartes nas paredes norte e sul (lado de dentro)
    s.set(9, y + 4, 3, v["banner"] + "[facing=south]")
    s.set(15, y + 4, 3, v["banner"] + "[facing=south]")
    if kind == "puzzle":
        n = PLAN[k][2]
        x0 = 12 - n // 2
        for i in range(n):
            s.set(x0 + i, y + 2, 2, f"{NS}:tower_rune_switch[lit=true]")
            s.set(x0 + i, y + 3, 2, s.v["trim"])
        s.sign(12, y + 1, 3, "south", [f"sign.{NS}.towers.puzzle_1", f"sign.{NS}.towers.puzzle_2", f"sign.{NS}.towers.puzzle_3"])
        for bx in range(5, 20):
            if bx not in range(x0 - 1, x0 + n + 1) and bx not in (7, 17):
                s.set(bx, y, 3, "minecraft:bookshelf")
                s.set(bx, y + 1, 3, "minecraft:bookshelf")
        chest_floor(s, k, y)
    elif kind == "treasure":
        s.chest(10, y, 3, "towers_treasure", "south")
        s.chest(14, y, 3, "towers_treasure", "south")
        s.set(12, y, 3, "minecraft:gold_block")
        s.set(12, y + 1, 3, "minecraft:lantern[hanging=false,waterlogged=false]")
        chest_floor(s, k, y)
    elif k in (2, 5, 6):
        chest_floor(s, k, y)
    # alvo de treino (decoracao)
    if kind in ("combat", "elite") and k != 0:
        s.set(12, y, 4, "minecraft:target")


def chest_floor(s, k, y):
    if k % 2 == 0:
        s.chest(4, y, 19, "towers_floor", "east")
    else:
        s.chest(20, y, 19, "towers_floor", "west")


# =====================================================================================
#  LADO DE FORA (v1.0)
# =====================================================================================
def plinth(s):
    """Base em talude: dois degraus de pedra em volta da torre (aberta na frente da porta)."""
    v = s.v
    for name, f in FACES.items():
        pos, inn = f["pos"], f["inn"]
        for c in range(0, 25):
            if name == "south" and 9 <= c <= 15:
                continue
            x, z = pos(c, 1)
            s.set(x, 2, z, v["found"])
            s.set(x, 3, z, v["corner"])
            s.xstair(x, 4, z, inn)
            x, z = pos(c, 2)
            s.set(x, 2, z, v["found"])
            s.xstair(x, 3, z, inn)


def turrets(s, top):
    """Torreoes redondos nos quatro cantos, com frestas iluminadas e telhado em agulha."""
    v = s.v
    cap = v["cap"]
    for (cx, cz) in TURRETS:
        for (dx, dz) in disc(2):
            x, z = cx + dx, cz + dz
            inside = 3 <= x <= 21 and 3 <= z <= 21
            for y in range(1, top + 8):
                if inside and y <= top:
                    continue                             # nao invade as salas
                s.set(x, y, z, v["trim"] if (y - 2) % 8 == 0 or y == top + 7 else v["corner"])
        # frestas com luz atras do vidro, uma por andar, viradas para fora
        ox = -1 if cx < 12 else 1
        oz = -1 if cz < 12 else 1
        for k in range(FLOORS + 1):
            for dy in (4, 5):
                y = Y(k) + dy
                if y > top + 6:
                    continue
                s.set(cx + 2 * ox, y, cz, v["glass"]); s.set(cx + ox, y, cz, v["light"])
                s.set(cx, y, cz + 2 * oz, v["glass"]); s.set(cx, y, cz + oz, v["light"])
        # agulha
        y0 = top + 8
        for (dx, dz) in disc(3):
            s.set(cx + dx, y0, cz + dz, cap)
        for dy, r in ((1, 2), (2, 2), (3, 1), (4, 1), (5, 1), (6, 0), (7, 0), (8, 0)):
            for (dx, dz) in disc(r):
                s.set(cx + dx, y0 + dy, cz + dz, cap)
        for r, dy in ((3, 1), (2, 3), (1, 6)):
            for (dx, dz, facing) in ((r, 0, "west"), (-r, 0, "east"), (0, r, "north"), (0, -r, "south")):
                s.set(cx + dx, y0 + dy, cz + dz, f"{v['cap_stairs']}[facing={facing},half=bottom,shape=straight,waterlogged=false]")
        s.set(cx, y0 + 9, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")


def buttresses(s):
    """Contrafortes em tres degraus (3, 2 e 1 bloco de fundo) ladeando o meio de cada face."""
    v = s.v
    stages = ((3, 10), (2, 26), (1, 42))                 # (fundo, ultimo y)
    for f in FACES.values():
        pos, inn = f["pos"], f["inn"]
        for c in BUTTRESS_C:
            for depth, ymax in stages:
                for d in range(1, depth + 1):
                    x, z = pos(c, d)
                    for y in range(2, ymax + 1):
                        s.set(x, y, z, v["trim"] if (y - 2) % 8 == 0 else v["corner"])
                x, z = pos(c, depth)
                s.xstair(x, ymax + 1, z, inn)


def cornice(s, top):
    """Cornija saliente embaixo das ameias (maticulo): msulas, friso e capa."""
    v = s.v
    slab = v["slab"] + "[type=bottom,waterlogged=false]"
    for f in FACES.values():
        pos, inn = f["pos"], f["inn"]
        for c in range(5, 20):
            x, z = pos(c, 1)
            s.xstair(x, top - 1, z, inn, "top")
            s.set(x, top, z, v["trim"])
            s.set(x, top + 1, z, slab if c % 2 else v["trim"])


def banners(s):
    """Estandartes compridos do lado de fora, entre o torreao e a primeira janela."""
    v = s.v
    for f in FACES.values():
        pos, out = f["pos"], f["out"]
        for k in (1, 3, 5, 7):
            for c in (5, 19):
                x, z = pos(c, 1)
                y = Y(k) - 2 if k == 7 else Y(k) + 5
                if s.sea is not None and y <= s.sea + 1:
                    continue
                s.set(x, y, z, f"{v['banner']}[facing={out}]")


def balcony(s, k):
    """Sacada na fachada sul do andar k: laje saliente com msulas, parapeito, porta envidracada e estandartes."""
    v = s.v
    y0 = Y(k)
    rail = v["rail"] + "[east=none,north=none,south=none,west=none,up=true,waterlogged=false]"
    for x in range(10, 15):
        for z in (23, 24, 25):
            s.set(x, y0, z, v["floor_b"])
            for y in (y0 + 1, y0 + 2, y0 + 3):
                s.set(x, y, z, AIR)
        s.xstair(x, y0 - 1, 23, "north", "top")
        if x in (10, 12, 14):
            s.xstair(x, y0 - 1, 24, "north", "top")
        s.set(x, y0 + 1, 25, rail)
    for z in (23, 24):
        s.set(10, y0 + 1, z, rail)
        s.set(14, y0 + 1, z, rail)
    # porta e vidros
    s.set(12, y0 + 1, 22, AIR); s.set(12, y0 + 2, 22, AIR)
    s.pane(12, y0 + 3, 22)
    for x in (11, 13):
        s.pane(x, y0 + 2, 22); s.pane(x, y0 + 3, 22)
    s.set(11, y0 + 4, 22, v["trim"]); s.set(12, y0 + 4, 22, v["trim"]); s.set(13, y0 + 4, 22, v["trim"])
    s.xstair(12, y0 + 5, 23, "north")
    # estandartes pendurados na frente e lanterna embaixo
    s.set(11, y0, 26, v["banner"] + "[facing=south]")
    s.set(13, y0, 26, v["banner"] + "[facing=south]")
    s.set(12, y0 - 1, 25, "minecraft:lantern[hanging=true,waterlogged=false]")


def porch(s, door):
    """Alpendre da porta: telhado inclinado entre os dois contrafortes, placa, estandartes e lanternas."""
    v = s.v
    cs = v["cap_stairs"] + "[facing=north,half=bottom,shape=straight,waterlogged=false]"
    cap = v.get("cap_block", v["cap"])
    for x in range(10, 15):
        s.set(x, 7, 25, cs)
        s.set(x, 7, 24, cap); s.set(x, 8, 24, cs)
        s.set(x, 7, 23, cap); s.set(x, 8, 23, cap); s.set(x, 9, 23, cs)
    s.sign(12, 6, 23, "south", door)
    s.set(10, 6, 23, v["banner"] + "[facing=south]")
    s.set(14, 6, 23, v["banner"] + "[facing=south]")
    s.set(10, 6, 25, "minecraft:lantern[hanging=true,waterlogged=false]")
    s.set(14, 6, 25, "minecraft:lantern[hanging=true,waterlogged=false]")


def tube(s, door):
    """
    Torre Submersa: a porta fica no fundo do mar. Um tubo de vidro com escada em caracol (24 degraus, 3 voltas) desce do
    cais, na superficie, ate ela — seco por dentro, com o mar em volta. Parede do tubo inteira ate 1 bloco acima da agua.
    """
    v = s.v
    dock = s.sea + 1                                      # bloco do cais (1 acima da agua); pes em dock + 1
    # casca: x 10..14, z 23..26 (o lado norte e a propria parede da torre)
    for y in range(1, dock + 1):
        for x in range(10, 15):
            for z in range(23, 27):
                if 11 <= x <= 13 and z <= 25:
                    continue
                post = (x in (10, 14) and z == 26)
                s.set(x, y, z, v["corner"] if post else v["trim"] if (y - 2) % 8 == 0 else v["glass"])
    for x in range(11, 14):
        for z in range(23, 26):
            s.set(x, 2, z, v["floor_b"])
            for y in range(3, dock + 4):
                s.set(x, y, z, AIR)
    # pilar central com luz
    for y in range(3, dock + 4):
        s.set(12, y, 24, v["light"] if y % 4 == 0 else v["pillar"])
    # caracol: o jogador entra pela porta (z=22), contorna pelo leste ate (12,25) e sobe no sentido horario
    ring = [((12, 23), "east"), ((13, 23), "east"), ((13, 24), "south"), ((13, 25), "south"),
            ((12, 25), "west"), ((11, 25), "west"), ((11, 24), "north"), ((11, 23), "north")]
    steps = dock + 1 - 3                                  # dos pes no fundo (y=3) aos pes no cais
    assert steps % 8 == 0, steps                          # termina em (12,25), de frente para a saida sul
    for n in range(1, steps + 1):
        (x, z), facing = ring[(4 + n) % 8]
        s.set(x, 2 + n, z, f"{v['stairs']}[facing={facing},half=bottom,shape=straight,waterlogged=false]")
    # cais: plataforma em volta do tubo e para o sul, com postes ate o fundo e lanternas
    for x in range(7, 18):
        for z in range(23, 32):
            if 10 <= x <= 14 and z <= 26:
                continue
            s.set(x, dock, z, v["floor_b"] if (x + z) % 2 else v["floor_a"])
    for (px, pz) in ((7, 27), (17, 27), (7, 31), (17, 31), (12, 31)):
        for y in range(dock - 6, dock + 1):
            s.set(px, y, pz, v["corner"])
        if (px, pz) != (12, 31):
            s.set(px, dock + 1, pz, v["rail"] + "[east=none,north=none,south=none,west=none,up=true,waterlogged=false]")
            s.set(px, dock + 2, pz, "minecraft:lantern[hanging=false,waterlogged=false]")
    # quiosque em cima do tubo: quatro pilares e telhado
    for (px, pz) in ((10, 26), (14, 26), (10, 23), (14, 23)):
        for y in range(dock + 1, dock + 4):
            s.set(px, y, pz, v["pillar"])
    for x in range(9, 16):
        for z in range(23, 28):
            edge = x in (9, 15) or z == 27
            s.set(x, dock + 4, z, (v["slab"] + "[type=bottom,waterlogged=false]") if edge else v.get("cap_block", v["cap"]))
    for x in range(11, 14):
        for z in range(24, 26):
            s.set(x, dock + 5, z, v["slab"] + "[type=bottom,waterlogged=false]")
    s.set(12, dock + 3, 25, "minecraft:lantern[hanging=true,waterlogged=false]")
    s.sign(12, dock + 3, 23, "south", door)


def pinnacle(s, top):
    v = s.v
    y = top + 1
    # ameias
    for i in range(2, 23):
        for (x, z) in ((i, 2), (i, 22), (2, i), (22, i)):
            s.set(x, y, z, s.wall())
            s.set(x, y + 1, z, s.wall())
            if i % 2 == 0:
                s.set(x, y + 2, z, v["trim"])
    # parapeito do buraco da escada (o ultimo andar e par: buraco leste)
    arrival_rail(s, y, (FLOORS - 1) % 2 == 0)
    # nucleo do pinaculo: invoca o guardiao
    core(s, s.variant, 12, y, 12, FLOORS, "boss", 0, False)
    # braseiros nos cantos internos
    for (bx, bz) in ((4, 4), (20, 4), (4, 20)):
        s.set(bx, y, bz, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    # cofre do pinaculo: bau cercado por selos (abre quando o guardiao cai)
    s.chest(12, y, 4, "towers_summit", "south")
    for (x, z) in ((11, 4), (13, 4), (12, 5)):
        s.seal(x, y, z)
    for x in (11, 12, 13):
        for z in (3, 4, 5):
            s.seal(x, y + 1, z)
    s.set(11, y, 3, v["pillar"]); s.set(12, y, 3, v["pillar"]); s.set(13, y, 3, v["pillar"])
    s.sign(12, y + 2, 6, "south", [f"sign.{NS}.towers.summit_1", f"sign.{NS}.towers.summit_2"])
    s.set(12, y + 2, 5, v["trim"])


def crown(s, top):
    """Coroa do pinaculo: pavilhao aberto (quatro pilares, vigas, telhado em piramide e lustre) sobre a arena do guardiao."""
    v = s.v
    cs = v["cap_stairs"]
    for (px, pz) in ((7, 7), (17, 7), (7, 17), (17, 17)):
        for y in range(top + 1, top + 8):
            s.set(px, y, pz, v["pillar"])
    # vigas entre os pilares, com msulas
    for i in range(7, 18):
        for (x, z) in ((i, 7), (i, 17), (7, i), (17, i)):
            s.set(x, top + 8, z, v["trim"])
            s.set(x, top + 9, z, v["trim"])
    for (px, pz) in ((7, 7), (17, 7), (7, 17), (17, 17)):
        sx = 1 if px < 12 else -1
        sz = 1 if pz < 12 else -1
        s.set(px + sx, top + 7, pz, f"{v['stairs']}[facing={'west' if sx > 0 else 'east'},half=top,shape=straight,waterlogged=false]")
        s.set(px, top + 7, pz + sz, f"{v['stairs']}[facing={'north' if sz > 0 else 'south'},half=top,shape=straight,waterlogged=false]")
    # telhado: aneis de escada subindo para o centro (beiral 2 blocos alem das vigas)
    for j in range(7):
        r = 7 - j
        y = top + 8 + j
        for i in range(-r, r + 1):
            for (dx, dz, facing) in ((i, -r, "south"), (i, r, "north"), (-r, i, "east"), (r, i, "west")):
                x, z = 12 + dx, 12 + dz
                s.set(x, y, z, f"{cs}[facing={facing},half=bottom,shape=straight,waterlogged=false]")
    s.set(12, top + 15, 12, v["cap"])
    s.set(12, top + 16, 12, v["rail"] + "[east=none,north=none,south=none,west=none,up=true,waterlogged=false]")
    s.set(12, top + 17, 12, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    # lustre
    for y in range(top + 10, top + 15):
        s.set(12, y, 12, "minecraft:chain[axis=y,waterlogged=false]")
    s.set(12, top + 9, 12, "minecraft:lantern[hanging=true,waterlogged=false]")


def overgrow(s, top):
    """Torre da Floresta: trepadeiras descendo pelas paredes e tufos de folhas nos telhados e contrafortes."""
    rnd = random.Random("towers/overgrow")
    leaves = "minecraft:jungle_leaves[distance=1,persistent=true,waterlogged=false]"
    side = {"north": "south", "south": "north", "west": "east", "east": "west"}
    for name, f in FACES.items():
        pos = f["pos"]
        attach = side[name]
        for c in range(5, 20):
            if c in BUTTRESS_C or rnd.random() > 0.45:
                continue
            x, z = pos(c, 1)
            y = rnd.randint(14, top - 2)
            for _ in range(rnd.randint(3, 9)):
                wx, wz = pos(c, 0)
                back = s.get(wx, y, wz)
                if y < 6 or not s.empty(x, y, z) or back is None or back == AIR or "glass" in back or "bars" in back:
                    break
                props = ",".join(f"{d}={'true' if d == attach else 'false'}" for d in ("east", "north", "south", "up", "west"))
                s.set(x, y, z, f"minecraft:vine[{props}]")
                y -= 1
    # folhas: em cima dos contrafortes e na beira dos telhados dos torreoes
    for f in FACES.values():
        pos = f["pos"]
        for c in BUTTRESS_C:
            for depth, ymax in ((3, 10), (2, 26), (1, 42)):
                x, z = pos(c, depth)
                if rnd.random() < 0.7 and s.empty(x, ymax + 2, z):
                    s.set(x, ymax + 2, z, leaves)
    for (cx, cz) in TURRETS:
        for (dx, dz) in disc(3):
            if rnd.random() < 0.3 and s.empty(cx + dx, top + 9, cz + dz):
                s.set(cx + dx, top + 9, cz + dz, leaves)


# =====================================================================================
#  FORMAS PRONTAS
# =====================================================================================
# As pecas de jigsaw sao colocadas com "known shape": o jogo NAO recalcula vizinhos. Vidracas, grades e muros que
# nascem com tudo "false/none" viram postes soltos, e escada de canto fica reta. Entao o molde ja sai com as ligacoes
# certas (mesmas regras do vanilla, simplificadas).
_DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
_CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}
_OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
_SOFT = ("air", "torch", "banner", "sign", "lantern", "chain", "vine", "leaves", "short_grass", "dandelion", "poppy",
         "azure_bluet", "oxeye_daisy", "cornflower", "chest", "ladder", "trapdoor", "campfire", "candle", "lightning_rod",
         "slab", "stairs", "cobweb", "pointed_dripstone", "water", "lily_pad", "decorated_pot", "cauldron", "azalea")


def _split(state):
    if "[" not in state:
        return state, {}
    name, props = state[:-1].split("[", 1)
    return name, dict(kv.split("=") for kv in props.split(",") if kv)


def _join(name, props):
    return name + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")


def _thin(name):
    return name.endswith("_pane") or name.endswith("iron_bars")


def _solid(state):
    """Bloco cheio, daqueles em que vidraca e muro se ligam."""
    if state is None:
        return False
    name = state.split("[")[0].split(":")[1]
    return not any(k in name for k in _SOFT) and not _thin(name) and not name.endswith("_wall")


def bake_shapes(struct):
    """Liga vidracas/grades/muros aos vizinhos e acerta a forma das escadas de canto. Mexe direto em struct.blocks."""
    blocks = struct.blocks
    out = {}
    for (x, y, z), (state, nbt) in blocks.items():
        name, props = _split(state)
        if _thin(name) or (name.endswith("_wall") and "up" in props):
            wall = name.endswith("_wall")
            links = {}
            for d, (dx, dz) in _DIRS.items():
                nb = blocks.get((x + dx, y, z + dz))
                nstate = nb[0] if nb else None
                nname = nstate.split("[")[0] if nstate else ""
                links[d] = _solid(nstate) or _thin(nname) or nname.endswith("_wall")
            if wall:
                above = blocks.get((x, y + 1, z))
                covered = _solid(above[0]) if above else False
                for d, on in links.items():
                    props[d] = ("tall" if covered else "low") if on else "none"
                straight = (links["north"] and links["south"] and not links["east"] and not links["west"]) or \
                           (links["east"] and links["west"] and not links["north"] and not links["south"])
                props["up"] = "false" if straight and (above is None or above[0] == AIR) else "true"
            else:
                for d, on in links.items():
                    props[d] = "true" if on else "false"
            out[(x, y, z)] = (_join(name, props), nbt)
        elif name.endswith("_stairs") and props.get("shape") == "straight":
            f, half = props["facing"], props["half"]

            def stair(d):
                dx, dz = _DIRS[d]
                nb = blocks.get((x + dx, y, z + dz))
                if not nb:
                    return None
                nn, np_ = _split(nb[0])
                return np_ if nn.endswith("_stairs") and "facing" in np_ else None

            def can_take(d):
                o = stair(d)
                return o is None or o["facing"] != f or o["half"] != half

            shape = "straight"
            back = stair(f)
            front = stair(_OPP[f])
            if back and back["half"] == half and back["facing"] not in (f, _OPP[f]) and can_take(_OPP[back["facing"]]):
                shape = "outer_left" if back["facing"] == _CCW[f] else "outer_right"
            elif front and front["half"] == half and front["facing"] not in (f, _OPP[f]) and can_take(front["facing"]):
                shape = "inner_left" if front["facing"] == _CCW[f] else "inner_right"
            if shape != "straight":
                props["shape"] = shape
                out[(x, y, z)] = (_join(name, props), nbt)
    blocks.update(out)


def build_all(data_dir):
    import os
    for variant in VARIANTS:
        s = build(variant)
        bake_shapes(s)
        n = s.save(os.path.join(data_dir, f"structure/{TEMPLATE_DIR}/tower_{variant}.nbt"), keep_air=True)
        print(f"  estrutura {TEMPLATE_DIR}/tower_{variant}: {n} blocos, molde {s.size}")
