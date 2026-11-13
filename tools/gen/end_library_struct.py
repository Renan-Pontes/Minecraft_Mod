"""
Molde da Biblioteca do Vazio (modulo end_library): torre-biblioteca em ruinas sobre uma ilha do End.
  - alicerce circular (r 21) com mosaico de purpura;
  - Salao de Leitura (terreo, r 16): estantes, mesas com velas, atris, baus, geradores de Escribas;
  - Arquivo (1o andar, r 14): fileiras radiais de estantes, baus melhores, geradores;
  - Observatorio (topo, r 14, ceu aberto): mapa estelar no chao, arcos de cupula quebrados, telescopio e o
    Devorador de Estrelas adormecido no centro (NBT "Lair": "end_library");
  - fragmentos flutuantes em volta (um com bau do Arquivo).
Escadas retas ligam os andares. Gera data/isekai_mahou/structure/end_library/library.nbt.

Rodar a partir da raiz do projeto:  py -I tools/gen/end_library_struct.py
"""
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from structgen import Structure, Byte, Float  # noqa: E402
import depths_puzzles as PZ  # noqa: E402

NS = "isekai_mahou"
OUT = os.path.join(ROOT, "src/main/resources/data/isekai_mahou/structure/end_library/library.nbt")
SX, SY, SZ = 47, 56, 47
C = 23
AIR = "minecraft:air"
LOOT_HALL = f"{NS}:chests/end_library"
LOOT_ARCHIVE = f"{NS}:chests/end_library_archive"
SCRIBE = f"{NS}:void_scribe"
WISP = f"{NS}:star_wisp"

Y_FLOOR = 3      # piso do salao (alicerce)
Y_B = 15         # piso do Arquivo
Y_C = 27         # piso do Observatorio

rnd = random.Random("end_library")
s = Structure(SX, SY, SZ)


def mc(n):
    return n if ":" in n else "minecraft:" + n


def dist(x, z):
    return math.hypot(x - C, z - C)


def ang(x, z):
    return math.degrees(math.atan2(z - C, x - C)) % 360


def adiff(a, b):
    d = abs(a - b) % 360
    return min(d, 360 - d)


def polar(a, r):
    return C + int(round(math.cos(math.radians(a)) * r)), C + int(round(math.sin(math.radians(a)) * r))


def disk(r):
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if d <= r:
                yield x, z, d


def shelf():
    return mc(f"{NS}:void_bookshelf") if rnd.random() < 0.22 else mc("bookshelf")


def wall_block():
    k = rnd.random()
    return mc("purpur_block") if k < 0.12 else mc("end_stone_bricks")


DOORS = (90.0, 270.0)


# ------------------------------------------------------------------ alicerce
def foundation():
    for y, r in enumerate((15, 17.5, 19.5)):
        for x, z, d in disk(r):
            s.set(x, y, z, mc("end_stone"))
    for x, z, d in disk(21):
        if d > 19.5 and rnd.random() < 0.18:
            continue  # borda quebrada
        a = ang(x, z)
        if d < 2.5:
            st = mc("crying_obsidian")
        elif d > 19.5:
            st = mc("purpur_block")
        elif int(d) % 5 == 0 or (d < 15 and min(adiff(a, k * 45) for k in range(8)) < 3.0):
            st = mc("purpur_block")
        else:
            st = mc("end_stone_bricks")
        s.set(x, Y_FLOOR, z, st)
    # tocos de pilares caidos em volta do salao
    for k in range(10):
        a = k * 36 + rnd.uniform(-8, 8)
        x, z = polar(a, 19)
        for y in range(Y_FLOOR + 1, Y_FLOOR + 1 + rnd.randint(1, 3)):
            s.set(x, y, z, mc("purpur_pillar[axis=y]"))


# ------------------------------------------------------------------ paredes circulares com janelas, portas e rombos
def ring_wall(y0, y1, r, windows, doors=(), breach=None):
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if not (r - 0.5 < d <= r + 0.5):
                continue
            a = ang(x, z)
            pillar = min(adiff(a, k * 45) for k in range(8)) < 2.6
            for y in range(y0, y1 + 1):
                if any(adiff(a, da) < 6.5 for da in doors) and y <= y0 + 3:
                    s.set(x, y, z, AIR)
                    continue
                if breach and adiff(a, breach[0]) < breach[1] and y >= breach[2]:
                    continue
                if y >= y1 - 2 and rnd.random() < 0.18:
                    continue  # topo carcomido
                if pillar:
                    s.set(x, y, z, mc("purpur_pillar[axis=y]"))
                elif windows[0] <= y <= windows[1] and min(adiff(a, 22.5 + k * 45) for k in range(8)) < 5.5:
                    s.set(x, y, z, mc("purple_stained_glass"))
                else:
                    s.set(x, y, z, wall_block())
            # arco sobre as portas
            if any(adiff(a, da) < 6.5 for da in doors):
                s.set(x, y0 + 4, z, mc("purpur_block"))


def clear_inside(y0, y1, r):
    for x, z, d in disk(r):
        for y in range(y0, y1 + 1):
            s.set(x, y, z, AIR)


def floor_disk(y, r, hole=()):
    for x, z, d in disk(r):
        if (x, z) in hole:
            s.set(x, y, z, AIR)
            continue
        s.set(x, y, z, mc("purpur_block") if int(d) % 4 == 0 else mc("end_stone_bricks"))


def stairs(x_cols, z_from, y_from, steps, facing, dz):
    """Escada reta: sobe 1 bloco por passo andando dz em z; suporte embaixo de cada degrau."""
    for i in range(steps):
        z = z_from + dz * i
        for x in x_cols:
            s.set(x, y_from + i, z, mc(f"end_stone_brick_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]"))
            if i > 0:
                s.set(x, y_from + i - 1, z, mc("end_stone_bricks"))
            for h in (1, 2, 3):
                if s.get(x, y_from + i + h, z) not in (None, AIR):
                    s.set(x, y_from + i + h, z, AIR)


def hanging_lantern(x, y_ceiling, z, length=1):
    for i in range(length):
        s.set(x, y_ceiling - 1 - i, z, mc("chain[axis=y,waterlogged=false]"))
    s.set(x, y_ceiling - 1 - length, z, mc("soul_lantern[hanging=true,waterlogged=false]"))


# ------------------------------------------------------------------ Salao de Leitura (terreo)
def reading_hall():
    y0, y1 = Y_FLOOR + 1, Y_B - 1
    clear_inside(y0, y1, 15.5)
    ring_wall(y0, y1, 16, (7, 10), DOORS, breach=(140, 9, 10))
    # estantes forrando a parede (sem tapar portas, janelas e pilares)
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if not (14.5 < d <= 15.5):
                continue
            a = ang(x, z)
            if any(adiff(a, da) < 9 for da in DOORS) or (adiff(a, 140) < 10):
                continue
            top = y0 + 2 if min(adiff(a, 22.5 + k * 45) for k in range(8)) < 8 else y0 + 4
            for y in range(y0, top + 1):
                s.set(x, y, z, shelf())
            if top == y0 + 4 and rnd.random() < 0.25:
                s.set(x, top + 1, z, mc("end_rod[facing=up]"))
    # tapete do caminho das portas ate a coluna e anel em volta dela
    for x, z, d in disk(4.5):
        if d > 3.0:
            s.set(x, y0, z, mc("purple_carpet"))
    for zz in list(range(C + 4, C + 16)) + list(range(C - 15, C - 3)):
        for xx in (C - 1, C, C + 1):
            s.set(xx, y0, zz, mc("magenta_carpet") if xx == C else mc("purple_carpet"))
    # mesas de leitura com velas, atris e cadeiras
    for a in (30, 150, 210, 330):
        tx, tz = polar(a, 9)
        for dx in (-1, 0, 1):
            s.set(tx + dx, y0, tz, mc("purpur_slab[type=top,waterlogged=false]"))
        s.set(tx, y0 + 1, tz, mc("purple_candle[candles=3,lit=true,waterlogged=false]"))
        s.set(tx - 1, y0 + 1, tz, mc("candle[candles=1,lit=true,waterlogged=false]"))
        s.set(tx, y0, tz - 1, mc("purpur_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"))
        s.set(tx, y0, tz + 1, mc("purpur_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"))
        side = -2 if math.cos(math.radians(a)) > 0 else 2
        s.set(tx + side, y0, tz, mc("lectern[facing=%s,has_book=false,powered=false]" % ("east" if side < 0 else "west")))
    # baus e geradores de Escribas
    for a, r in ((0, 12), (180, 12), (120, 11)):
        x, z = polar(a, r)
        s.chest(x, y0, z, LOOT_HALL, facing="north")
    for a in (60, 240):
        x, z = polar(a, 7)
        s.spawner(x, y0, z, SCRIBE)
    # luz: lanternas penduradas
    for a in (0, 90, 180, 270):
        x, z = polar(a + 45, 8)
        hanging_lantern(x, Y_B, z, 2)
    # escada A->B (leste), subindo para o norte
    stairs((C + 10, C + 11), C + 6, y0, 11, "north", -1)


# ------------------------------------------------------------------ Arquivo (1o andar)
def archive():
    y0, y1 = Y_B + 1, Y_C - 1
    hole = {(x, z) for x in (C + 10, C + 11) for z in range(C - 4, C - 1)}
    floor_disk(Y_B, 16.5, hole)
    clear_inside(y0, y1, 13.5)
    ring_wall(y0, y1, 14, (19, 22), (), breach=(300, 12, 21))
    # fileiras radiais de estantes (corredores entre elas)
    for k in range(8):
        a = 22.5 + k * 45
        for t in range(4, 10):
            x, z = polar(a, t)
            for y in range(y0, y0 + 3):
                s.set(x, y, z, shelf() if t < 9 else mc("chiseled_bookshelf[facing=north,slot_0_occupied=true,slot_1_occupied=false,"
                                                       "slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,"
                                                       "slot_5_occupied=true]"))
            if t == 7:
                s.set(x, y0 + 3, z, mc("end_rod[facing=up]"))
    for a in (67.5, 292.5):
        x, z = polar(a, 10.5)
        s.chest(x, y0, z, LOOT_ARCHIVE, facing="north")
    x, z = polar(112.5, 11)
    s.spawner(x, y0, z, SCRIBE)
    x, z = polar(247.5, 11)
    s.spawner(x, y0, z, WISP)
    for a in (45, 135, 225, 315):
        x, z = polar(a, 11)
        hanging_lantern(x, Y_C, z, 1)
    # escada B->C (oeste), subindo para o sul
    stairs((C - 11, C - 10), C - 6, y0, 11, "south", 1)


def central_column():
    """Pilar de purpura envolto em estantes no centro, do salao ate o piso do observatorio."""
    for x, z, d in disk(2.5):
        for y in range(Y_FLOOR + 1, Y_C):
            if d <= 1.5:
                s.set(x, y, z, mc("purpur_pillar[axis=y]"))
            elif y < Y_B - 2 or Y_B < y < Y_C - 1:
                s.set(x, y, z, shelf())


# ------------------------------------------------------------------ v1.0: enigma do Arquivo (sequencia de runas)
def archive_puzzle():
    """
    Quatro Pedras Runicas engastadas na coluna central (uma por ponto cardeal), o Selo Runico logo acima da pedra sul
    e um dos baus do Arquivo trancado numa gaiola de Grades Seladas. Tocar no Selo mostra a ordem das runas.
    """
    y0 = Y_B + 1
    for k, (dx, dz) in enumerate(((0, -2), (2, 0), (0, 2), (-2, 0))):
        s.set(C + dx, y0, C + dz, mc("purpur_pillar[axis=y]"))
        s.set(C + dx, y0 + 1, C + dz, PZ.stone(k))
        s.set(C + dx, y0 + 2, C + dz, mc("purpur_block"))
    PZ.put_core(s, C, y0 + 3, C + 2, 9, facing="south")
    x, z = polar(67.5, 10.5)
    PZ.cage(s, x, y0, z, LOOT_ARCHIVE, "north")


# ------------------------------------------------------------------ Observatorio (topo)
def observatory():
    hole = {(x, z) for x in (C - 11, C - 10) for z in range(C + 2, C + 5)}
    crng = random.Random("end_library/stars")
    for x, z, d in disk(14.5):
        if (x, z) in hole:
            s.set(x, Y_C, z, AIR)
            continue
        if d > 12.5:
            st = mc("end_stone_bricks")
        elif d < 1.6:
            st = mc("crying_obsidian")
        elif abs(d - 6) < 0.5 or abs(d - 10) < 0.5:
            st = mc("purpur_block")
        else:
            k = crng.random()
            st = mc("sea_lantern") if k < 0.035 else (mc("amethyst_block") if k < 0.07 else mc("polished_blackstone"))
        s.set(x, Y_C, z, st)
    # constelacoes: linhas de ametista ligando "estrelas"
    for (a1, r1), (a2, r2) in (((20, 4), (60, 8)), ((60, 8), (100, 11)), ((200, 5), (250, 9)), ((250, 9), (280, 4))):
        x1, z1 = polar(a1, r1)
        x2, z2 = polar(a2, r2)
        n = max(abs(x2 - x1), abs(z2 - z1))
        for i in range(n + 1):
            x = round(x1 + (x2 - x1) * i / max(1, n))
            z = round(z1 + (z2 - z1) * i / max(1, n))
            s.set(x, Y_C, z, mc("amethyst_block"))
        s.set(x1, Y_C, z1, mc("sea_lantern"))
        s.set(x2, Y_C, z2, mc("sea_lantern"))
    # parapeito
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if 13.5 < d <= 14.5 and (x, z) not in hole:
                if rnd.random() < 0.12:
                    continue
                s.set(x, Y_C + 1, z, mc("end_stone_bricks"))
                if (x + z) % 2 == 0:
                    s.set(x, Y_C + 2, z, mc("end_stone_brick_slab[type=bottom,waterlogged=false]"))
    # arcos da cupula (quebrados)
    for k in range(8):
        a = k * 45 + 22.5
        stop = 60 if k % 3 else rnd.randint(18, 40)
        for step in range(0, stop + 1, 2):
            t = math.radians(step)
            r = 13.6 * math.cos(t)
            y = Y_C + 1 + int(round(11.5 * math.sin(t)))
            x, z = polar(a, r)
            s.set(x, y, z, mc("purpur_pillar[axis=y]") if step < 30 else mc("purpur_block"))
        if stop >= 50:
            x, z = polar(a, 13.6 * math.cos(math.radians(stop)))
            s.set(x, Y_C + 1 + int(round(11.5 * math.sin(math.radians(stop)))) - 1, z, mc("end_rod[facing=down]"))
    # telescopio
    tx, tz = polar(305, 10)
    s.set(tx, Y_C + 1, tz, mc("polished_blackstone_bricks"))
    s.set(tx, Y_C + 2, tz, mc("polished_blackstone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]"))
    s.set(tx, Y_C + 3, tz, mc("tinted_glass"))
    s.set(tx + 1, Y_C + 4, tz - 1, mc("tinted_glass"))
    s.set(tx + 2, Y_C + 5, tz - 2, mc("end_rod[facing=up]"))
    s.set(tx - 1, Y_C + 1, tz, mc("lectern[facing=east,has_book=false,powered=false]"))
    # o chefe dorme no centro
    s.entity(C + 0.5, Y_C + 1, C + 0.5, f"{NS}:star_devourer",
             {"PersistenceRequired": Byte(1), "Lair": "end_library", "Rotation": [Float(180.0), Float(0.0)]})


# ------------------------------------------------------------------ fragmentos flutuantes
def floating_fragments():
    spots = [(35, 20, 19, False), (110, 21, 32, False), (165, 20, 24, True), (215, 20, 42, False), (290, 20, 37, False),
             (340, 21, 46, True)]
    for a, r, y, chest in spots:
        cx, cz = polar(a, r)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(-2, 1):
                    rr = math.sqrt(dx * dx + dz * dz + (dy * 1.6) ** 2)
                    if rr <= 2.4 and 0 <= cx + dx < SX and 0 <= cz + dz < SZ:
                        s.set(cx + dx, y + dy, cz + dz, mc("end_stone") if dy < 0 else mc("end_stone_bricks"))
        s.set(cx, y + 1, cz, shelf())
        s.set(cx + 1, y + 1, cz, shelf())
        if chest:
            s.chest(cx - 1, y + 1, cz, LOOT_ARCHIVE, facing="south")
        else:
            s.set(cx - 1, y + 1, cz, mc("amethyst_cluster[facing=up,waterlogged=false]"))


def main():
    foundation()
    reading_hall()
    archive()
    central_column()
    archive_puzzle()
    observatory()
    floating_fragments()
    n = s.save(OUT, keep_air=True)
    print("end_library: molde salvo (%d blocos) em %s" % (n, os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
