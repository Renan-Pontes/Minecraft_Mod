"""
Quebra-cabecas das masmorras (v1.0 RELEASE) — biblioteca usada por depths.py, nether_citadel.py,
end_library_struct.py e dwarf_hold.py.

  * ids/estados das pecas (Selo Runico, Lampada, Placa, Pedra Runica, Espelho, Grade Selada);
  * "carimbos" que montam cada enigma dentro de um Structure (tools/structgen.py);
  * texturas, modelos e moldes de teste das pecas (assets() / demo_rooms()), chamados pelo depths.py.

Regras que o codigo Java espera (feature/depths/puzzle/PuzzleCoreBlockEntity):
  * o Selo acha as pecas num raio "Range" (x/z) e de -3 a +7 blocos em y; nao deixe dois enigmas se sobreporem;
  * tipo do enigma = pecas presentes: espelhos -> LUZ; pedras -> SEQUENCIA; placas -> PLACAS; alavancas+lampadas -> ALAVANCAS;
  * resolvido = TODAS as Grades Seladas da area somem (o bau fica dentro de uma gaiola de grades).
"""
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten, mix  # noqa: E402
from structgen import Structure, Int  # noqa: E402

NS = "isekai_mahou"
GATE = f"{NS}:depths_puzzle_gate"
CHAIN = "minecraft:chain[axis=y,waterlogged=false]"
AIR = "minecraft:air"
RUNE_COLORS = ("#FF7A3A", "#5AE0FF", "#8CE06A", "#C08CFF")


def core(facing="north"):
    return f"{NS}:depths_puzzle_core[facing={facing},solved=false]"


def lamp():
    return f"{NS}:depths_rune_lamp[lit=false]"


def tile():
    return f"{NS}:depths_rune_tile[lit=false]"


def mirror(flipped):
    return f"{NS}:depths_light_mirror[flipped={'true' if flipped else 'false'}]"


def stone(rune):
    return f"{NS}:depths_rune_stone[lit=false,rune={rune % 4}]"


def wall_lever(facing):
    """Alavanca presa na parede; 'facing' e para onde ela aponta (o bloco de apoio fica do lado oposto)."""
    return f"minecraft:lever[face=wall,facing={facing},powered=false]"


def put_core(s, x, y, z, rng, facing="north"):
    s.set(x, y, z, core(facing), nbt={"Range": Int(rng)})


def cage(s, x, y, z, loot, facing="south"):
    """Bau trancado numa gaiola 3x3x2 de Grades Seladas (some quando o enigma e resolvido). loot = id completo."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            s.set(x + dx, y, z + dz, GATE)
            s.set(x + dx, y + 1, z + dz, GATE)
    s.set(x, y, z, f"minecraft:chest[facing={facing},type=single,waterlogged=false]", nbt={"LootTable": loot})


# =====================================================================================
#  CARIMBOS — (cx, cz) = centro da area; y = primeiro bloco de ar acima do piso
# =====================================================================================
def levers_console(s, cx, y, cz, accent, n=5, rng=6):
    """Painel de alavancas: parede de 3 de altura (apoio, apoio+alavanca, lampada) com o Selo em cima, no meio."""
    half = n // 2
    for i in range(n):
        x = cx - half + i
        s.set(x, y, cz - 1, accent)
        s.set(x, y + 1, cz - 1, accent)
        s.set(x, y + 2, cz - 1, lamp())
        s.set(x, y + 1, cz, wall_lever("south"))
    put_core(s, cx, y + 3, cz - 1, rng)


def levers_room(s, cx, y, cz, loot, accent, n=5, rng=6):
    levers_console(s, cx, y, cz, accent, n, rng)
    cage(s, cx + 3, y, cz + 3, loot, "north")


def _hamiltonian(cells):
    """Existe um caminho que passa por todas as celulas uma vez so, comecando na borda da grade 5x5?"""
    cells = set(cells)
    total = len(cells)
    # paridade do xadrez: sem isso nem adianta procurar (e a busca cega seria lenta)
    even = sum(1 for c in cells if (c[0] + c[1]) % 2 == 0)
    if abs(total - 2 * even) > 1:
        return False
    nbrs = {c: [(c[0] + dx, c[1] + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (c[0] + dx, c[1] + dz) in cells]
            for c in cells}

    def dfs(cur, seen):
        if len(seen) == total:
            return True
        for nx in nbrs[cur]:
            if nx not in seen:
                seen.add(nx)
                if dfs(nx, seen):
                    return True
                seen.discard(nx)
        return False

    for start in sorted(cells):
        if max(abs(start[0]), abs(start[1])) == 2 and dfs(start, {start}):
            return True
    return False


def tiles_room(s, cx, y, cz, loot, accent, rnd, rng=6):
    """
    Grade 5x5 de Placas Runicas no piso: acenda todas sem pisar duas vezes na mesma nem sair da grade.
    O centro e o pedestal do Selo, o canto sudeste fica debaixo da gaiola e 2 celulas viram obstaculos (sorteados de
    modo que exista solucao).
    """
    fixed = {(0, 0), (2, 2)}
    grid = [(dx, dz) for dx in range(-2, 3) for dz in range(-2, 3)]
    extra = set()
    for _ in range(200):
        pick = set(rnd.sample([c for c in grid if c not in fixed], 2))
        if _hamiltonian([c for c in grid if c not in fixed and c not in pick]):
            extra = pick
            break
    for (dx, dz) in grid:
        if (dx, dz) in fixed or (dx, dz) in extra:
            s.set(cx + dx, y - 1, cz + dz, accent)
        else:
            s.set(cx + dx, y - 1, cz + dz, tile())
    for (dx, dz) in extra:
        s.set(cx + dx, y, cz + dz, accent)
    s.set(cx, y, cz, accent)
    put_core(s, cx, y + 1, cz, rng)
    cage(s, cx + 3, y, cz + 3, loot, "north")


def light_room(s, cx, y, cz, loot, accent, rng=8):
    """
    Feixe sai do Selo (canto noroeste, virado para leste), precisa quicar em 3 espelhos e acender a Lampada.
    Os espelhos nascem todos virados errado. A gaiola fica no centro, cercada pelo caminho da luz.
    """
    put_core(s, cx - 4, y, cz - 3, rng, facing="east")
    s.set(cx + 3, y, cz - 3, mirror(False))   # certo: leste -> sul  (flipped=true)
    s.set(cx + 3, y, cz + 3, mirror(True))    # certo: sul -> oeste  (flipped=false)
    s.set(cx - 3, y, cz + 3, mirror(False))   # certo: oeste -> norte (flipped=true)
    s.set(cx - 3, y, cz - 2, lamp())
    for (dx, dz) in ((3, -3), (3, 3), (-3, 3), (-3, -2)):
        s.set(cx + dx, y - 1, cz + dz, accent)
    cage(s, cx, y, cz, loot, "north")


def sequence_room(s, cx, y, cz, loot, accent, ceiling, rng=6, spread=3):
    """4 Pedras Runicas em pedestais nos cantos; o Selo fica pendurado sobre a gaiola central (clique nele para ver a ordem)."""
    cage(s, cx, y, cz, loot, "north")
    put_core(s, cx, y + 2, cz, rng)
    for yy in range(y + 3, ceiling):
        s.set(cx, yy, cz, CHAIN)
    for k, (dx, dz) in enumerate(((-spread, -spread), (spread, -spread), (spread, spread), (-spread, spread))):
        s.set(cx + dx, y, cz + dz, accent)
        s.set(cx + dx, y + 1, cz + dz, stone(k))


# =====================================================================================
#  MOLDES DE TESTE  (/mahou depths puzzle demo <tipo>)
# =====================================================================================
def demo_rooms(data_dir):
    wall, floor, accent = "minecraft:stone_bricks", "minecraft:polished_andesite", "minecraft:chiseled_stone_bricks"
    loot = f"{NS}:chests/depths_puzzle"
    for kind in ("levers", "tiles", "light", "sequence"):
        s = Structure(11, 9, 11)
        s.fill(0, 0, 0, 10, 0, 10, floor)
        s.fill(0, 8, 0, 10, 8, 10, wall)
        s.walls(0, 1, 0, 10, 7, 10, wall)
        s.fill(1, 1, 1, 9, 7, 9, AIR)
        s.fill(4, 1, 0, 6, 3, 0, AIR)  # porta (norte)
        for (x, z) in ((1, 1), (9, 1), (1, 9), (9, 9)):
            s.set(x, 7, z, "minecraft:lantern[hanging=true,waterlogged=false]")
        if kind == "levers":
            levers_room(s, 5, 1, 5, loot, accent)
        elif kind == "tiles":
            tiles_room(s, 5, 1, 5, loot, accent, random.Random("depths/demo_tiles"))
        elif kind == "light":
            light_room(s, 5, 1, 5, loot, accent)
        else:
            sequence_room(s, 5, 1, 5, loot, accent, 8)
        n = s.save(os.path.join(data_dir, f"structure/depths_dungeon/puzzle_{kind}.nbt"), keep_air=True)
        print("  estrutura depths_dungeon/puzzle_%s %d blocos" % (kind, n))


# =====================================================================================
#  TEXTURAS E MODELOS
# =====================================================================================
def H(h, a=255):
    return hex_rgba(h, a)


def _stone_bg(seed, base="#2E2E38", frame="#1A1A22"):
    c = Canvas(16, 16, H(base))
    c.noise(0.10, seed)
    c.outline_rect(0, 0, 15, 15, H(frame))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, H("#4A4A58"))
    return c


def _glow(c, pts, col, on):
    """Desenha os pontos; aceso = cor cheia + halo claro em volta, apagado = cor escura."""
    main = H(col) if on else darken(H(col), 0.62)
    if on:
        halo = mix(H(col), H("#2E2E38"), 0.55)
        for (x, y) in pts:
            for (ox, oy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + ox, y + oy) not in pts and 1 <= x + ox <= 14 and 1 <= y + oy <= 14:
                    c.set(x + ox, y + oy, halo)
    for (x, y) in pts:
        c.set(x, y, main)
    if on:
        for (x, y) in list(pts)[::3]:
            c.set(x, y, lighten(H(col), 0.55))


def _line_pts(x0, y0, x1, y1):
    tmp = Canvas(16, 16)
    tmp.line(x0, y0, x1, y1, (1, 1, 1, 255))
    return [(x, y) for y in range(16) for x in range(16) if tmp.get(x, y)[3] > 0]


RUNES = (
    # 0: triangulo (fogo)
    lambda: _line_pts(8, 3, 3, 12) + _line_pts(8, 3, 12, 12) + _line_pts(3, 12, 12, 12) + [(7, 9), (8, 9), (8, 8)],
    # 1: circulo com ponto (agua)
    lambda: [(x, y) for x in range(16) for y in range(16) if 16 <= (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 27] + [(7, 7), (8, 8), (7, 8), (8, 7)],
    # 2: raio (vento)
    lambda: _line_pts(10, 3, 5, 8) + _line_pts(5, 8, 10, 8) + _line_pts(10, 8, 5, 13) + [(10, 4), (5, 12)],
    # 3: estrela/cruz (sombra)
    lambda: _line_pts(8, 3, 8, 12) + _line_pts(3, 7, 12, 7) + _line_pts(4, 4, 11, 11) + _line_pts(11, 4, 4, 11),
)


def assets(assets_dir):
    tex = os.path.join(assets_dir, "textures/block")
    bs, bm, im = (os.path.join(assets_dir, p) for p in ("blockstates", "models/block", "models/item"))

    def save(c, name):
        c.save(os.path.join(tex, name + ".png"))

    def wj(path, obj):
        import json
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(obj, f, indent=2, ensure_ascii=False)
            f.write("\n")

    def cube(name, texture=None):
        wj(os.path.join(bm, f"{name}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{texture or name}"}})

    # ---- Pedras Runicas (4 desenhos x apagada/acesa)
    for k in range(4):
        pts = set(RUNES[k]())
        for on in (False, True):
            c = _stone_bg(40 + k)
            _glow(c, pts, RUNE_COLORS[k], on)
            name = f"depths_rune_stone_{k}" + ("_on" if on else "")
            save(c, name)
            cube(name)
    wj(os.path.join(bs, "depths_rune_stone.json"), {"variants": {
        f"lit={'true' if on else 'false'},rune={k}": {"model": f"{NS}:block/depths_rune_stone_{k}" + ("_on" if on else "")}
        for k in range(4) for on in (False, True)}})
    wj(os.path.join(im, "depths_rune_stone.json"), {"parent": f"{NS}:block/depths_rune_stone_0"})

    # ---- Lampada Runica: cristal em losango numa moldura
    for on in (False, True):
        c = _stone_bg(51, "#26303A", "#141A20")
        diamond = [(x, y) for x in range(16) for y in range(16) if abs(x - 7.5) + abs(y - 7.5) <= 5]
        for (x, y) in diamond:
            d = abs(x - 7.5) + abs(y - 7.5)
            col = H("#9AF0FF") if on else H("#2A5560")
            c.set(x, y, lighten(col, 0.5) if on and d <= 2 else (darken(col, 0.25) if d > 4 else col))
        if on:
            for (x, y) in ((6, 5), (7, 4), (5, 6)):
                c.set(x, y, H("#FFFFFF"))
            for (x, y) in ((2, 7), (13, 8), (7, 2), (8, 13)):
                c.set(x, y, H("#5AE0FF"))
        name = "depths_rune_lamp" + ("_on" if on else "")
        save(c, name)
        cube(name)
    wj(os.path.join(bs, "depths_rune_lamp.json"), {"variants": {
        "lit=false": {"model": f"{NS}:block/depths_rune_lamp"}, "lit=true": {"model": f"{NS}:block/depths_rune_lamp_on"}}})
    wj(os.path.join(im, "depths_rune_lamp.json"), {"parent": f"{NS}:block/depths_rune_lamp"})

    # ---- Placa Runica: laje com quadrados gravados
    for on in (False, True):
        c = _stone_bg(61, "#34343C", "#202028")
        pts = set()
        for (a, b) in ((3, 12), (6, 9)):
            for i in range(a, b + 1):
                pts.update({(i, a), (i, b), (a, i), (b, i)})
        _glow(c, pts, "#FFC85A", on)
        name = "depths_rune_tile" + ("_on" if on else "")
        save(c, name)
        cube(name)
    wj(os.path.join(bs, "depths_rune_tile.json"), {"variants": {
        "lit=false": {"model": f"{NS}:block/depths_rune_tile"}, "lit=true": {"model": f"{NS}:block/depths_rune_tile_on"}}})
    wj(os.path.join(im, "depths_rune_tile.json"), {"parent": f"{NS}:block/depths_rune_tile"})

    # ---- Selo Runico: lados com anel de runas; frente com a "lente" de onde sai o feixe
    for solved in (False, True):
        col = "#FFE27A" if solved else "#C08CFF"
        side = _stone_bg(71, "#2A2238", "#161020")
        ring = {(x, y) for x in range(16) for y in range(16) if 20 <= (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 31}
        ring = {p for i, p in enumerate(sorted(ring)) if i % 4 != 3}
        _glow(side, ring, col, True)
        for (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
            side.set(x, y, H(col))
        save(side, "depths_puzzle_core_side" + ("_solved" if solved else ""))
        front = _stone_bg(72, "#2A2238", "#161020")
        front.circle(8, 8, 5.2, darken(H(col), 0.45))
        front.circle(8, 8, 3.8, H(col))
        front.circle(8, 8, 2.0, lighten(H(col), 0.6))
        front.set(6, 6, H("#FFFFFF"))
        save(front, "depths_puzzle_core_front" + ("_solved" if solved else ""))
        suf = "_solved" if solved else ""
        wj(os.path.join(bm, f"depths_puzzle_core{suf}.json"), {"parent": "minecraft:block/orientable", "textures": {
            "top": f"{NS}:block/depths_puzzle_core_side{suf}", "side": f"{NS}:block/depths_puzzle_core_side{suf}",
            "front": f"{NS}:block/depths_puzzle_core_front{suf}"}})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for f, r in rot.items():
        for solved in (False, True):
            v = {"model": f"{NS}:block/depths_puzzle_core" + ("_solved" if solved else "")}
            if r:
                v["y"] = r
            variants[f"facing={f},solved={'true' if solved else 'false'}"] = v
    wj(os.path.join(bs, "depths_puzzle_core.json"), {"variants": variants})
    wj(os.path.join(im, "depths_puzzle_core.json"), {"parent": f"{NS}:block/depths_puzzle_core"})

    # ---- Grade Selada: barras de ferro escuro com um cadeado de runa (recorte)
    c = Canvas(16, 16)
    bar, bar_l, bar_d = H("#3A3A46"), H("#5E5E70"), H("#1E1E28")
    for x0 in (1, 5, 9, 13):
        for y in range(16):
            c.set(x0, y, bar_l)
            c.set(x0 + 1, y, bar)
    for y0 in (2, 12):
        for x in range(16):
            c.set(x, y0, bar_l)
            c.set(x, y0 + 1, bar_d)
    c.rect(6, 6, 9, 9, bar)
    c.outline_rect(6, 6, 9, 9, bar_d)
    c.set(7, 7, H("#C08CFF")); c.set(8, 8, H("#C08CFF")); c.set(7, 8, H("#8A5AD0")); c.set(8, 7, H("#E0C8FF"))
    save(c, "depths_puzzle_gate")
    wj(os.path.join(bm, "depths_puzzle_gate.json"), {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
                                                     "textures": {"all": f"{NS}:block/depths_puzzle_gate"}})
    wj(os.path.join(bs, "depths_puzzle_gate.json"), {"variants": {"": {"model": f"{NS}:block/depths_puzzle_gate"}}})
    wj(os.path.join(im, "depths_puzzle_gate.json"), {"parent": f"{NS}:block/depths_puzzle_gate"})

    # ---- Espelho: base de pedra + placa prateada na diagonal (flipped=false e a "/" vista de cima)
    c = Canvas(16, 16, H("#8FD8E8"))
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            c.set(x, y, mix(H("#D8F8FF"), H("#4A9AB8"), t))
    for k in range(-4, 20, 7):
        for i in range(16):
            if 0 <= k + i < 16:
                c.set(k + i, 15 - i, H("#FFFFFF"))
    c.outline_rect(0, 0, 15, 15, H("#C8A03A"))
    c.outline_rect(1, 1, 14, 14, H("#8A6A1E"))
    save(c, "depths_light_mirror")
    plate = {"from": [7.25, 2, 0], "to": [8.75, 15, 16],
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": -45, "rescale": True},
             "faces": {f: {"texture": "#mirror"} for f in ("north", "south", "east", "west", "up", "down")}}
    base = {"from": [3, 0, 3], "to": [13, 2, 13],
            "faces": {f: {"texture": "#base"} for f in ("north", "south", "east", "west", "up", "down")}}
    wj(os.path.join(bm, "depths_light_mirror.json"), {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"particle": f"{NS}:block/depths_light_mirror", "mirror": f"{NS}:block/depths_light_mirror",
                     "base": "minecraft:block/chiseled_deepslate"},
        "elements": [base, plate]})
    wj(os.path.join(bs, "depths_light_mirror.json"), {"variants": {
        "flipped=false": {"model": f"{NS}:block/depths_light_mirror"},
        "flipped=true": {"model": f"{NS}:block/depths_light_mirror", "y": 90}}})
    wj(os.path.join(im, "depths_light_mirror.json"), {"parent": f"{NS}:block/depths_light_mirror"})
