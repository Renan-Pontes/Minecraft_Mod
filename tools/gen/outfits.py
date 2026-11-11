"""
Gera os assets e dados do modulo outfits (roupas 3D das classes, GeckoLib).
Uso (na raiz do projeto):  py -I tools/gen/outfits.py

Para cada classe (Magicas: mage, witch, warlock, druid, necromancer; v0.9 Guerreiras: warrior, paladin, barbarian, monk,
knight; v0.9 A Distancia: ranger, rogue, bard, artificer, hunter) e cada grau (I = sem sufixo; adept, master, archmage) gera:
  - geo/item/armor/outfit_<classe>[_<grau>].geo.json   (modelo Bedrock 1.12.0 com os ossos de armadura do GeckoLib)
  - textures/item/armor/outfit_<classe>[_<grau>].png   (atlas 64x64 montado automaticamente: UV por face)
  - textures/item/armor/outfit_<classe>[_<grau>]_glowmask.png (pixels que brilham; obrigatorio nos graus II-IV)
  - textures/models/armor/outfit_<classe>[_<grau>]_layer_1/2.png (reserva no formato vanilla)
  - icones 16x16 das 4 pecas + modelos de item (os dos graus sao o icone do grau I recolorido + gema/brilhos)
  - receitas: costura do zero (grau I), "reforma" para outra classe do mesmo grau (isekai_mahou:outfit_retailor),
    melhoria de grau na Mesa de Ferraria (modelo isekai_mahou:arcane_upgrade_template do armory; se ele nao existir,
    Essencia Arcana), as conquistas invisiveis do livro de receitas e as tags (outfits/..., armaduras vanilla no merge)
  - (v0.9) as traducoes das 10 classes novas em src/main/lang/outfits (nomes das pecas e frases de cada grau)
Graus: py -I tools/gen/outfits.py [classe ...] gera so os graus das classes dadas (o grau I das Magicas e as receitas
sempre; as classes novas so as dadas).

Convencoes do arquivo .geo.json (Bedrock): frente da entidade = -z, lado DIREITO da entidade = -x, y para cima.
Faces: north = frente, south = costas, east = lado direito, west = lado esquerdo.
Rotacao (graus): rx > 0 inclina o topo para a frente; rz > 0 joga a parte de baixo para a direita da entidade.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix, item_model  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/outfits")
T = (0, 0, 0, 0)
YELLOW_CORE = (242, 200, 58, 255)
FACES = ("north", "south", "east", "west", "up", "down")
OMIT = "-"


def H(h, a=255):
    return hex_rgba(h, a)


def pal(*hexes):
    return [H(h) for h in hexes]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  Pintura de regioes (cada face de cubo recebe um retangulo do atlas)
# =====================================================================================
class Region:
    def __init__(self, cv, gl, x, y, w, h, seed):
        self.cv, self.gl, self.x, self.y, self.w, self.h = cv, gl, x, y, w, h
        self.rnd = random.Random(seed)

    def inside(self, i, j):
        return 0 <= i < self.w and 0 <= j < self.h

    def set(self, i, j, c):
        if self.inside(i, j):
            self.cv.set(self.x + i, self.y + j, c)

    def get(self, i, j):
        return self.cv.get(self.x + i, self.y + j) if self.inside(i, j) else T

    def glow(self, i, j, c):
        """Pixel que brilha no escuro (vai para a glowmask tambem)."""
        if self.inside(i, j):
            self.cv.set(self.x + i, self.y + j, c)
            self.gl.set(self.x + i, self.y + j, c)

    def clear(self, i, j):
        if self.inside(i, j):
            self.cv.set(self.x + i, self.y + j, T)
            self.gl.set(self.x + i, self.y + j, T)

    def fill(self, c):
        for j in range(self.h):
            for i in range(self.w):
                self.set(i, j, c)

    def row(self, j, c):
        j = j if j >= 0 else self.h + j
        for i in range(self.w):
            self.set(i, j, c)

    def col(self, i, c):
        i = i if i >= 0 else self.w + i
        for j in range(self.h):
            self.set(i, j, c)

    def map(self, fn):
        for j in range(self.h):
            for i in range(self.w):
                p = self.get(i, j)
                if p[3] > 0:
                    self.set(i, j, fn(i, j, p))

    def vshade(self, top=0.10, bottom=0.18):
        """Luz de cima: topo um pouco mais claro, base mais escura."""
        if self.h < 2:
            return
        def f(i, j, p):
            t = j / (self.h - 1)
            k = top * (1 - t) - bottom * t
            return lighten(p, k) if k > 0 else darken(p, -k)
        self.map(f)

    def noise(self, amt=0.05):
        rnd = self.rnd
        def f(i, j, p):
            k = (rnd.random() - 0.5) * 2 * amt
            return lighten(p, k) if k > 0 else darken(p, -k)
        self.map(f)

    def folds(self, dark, light, period=4, phase=None):
        """Pregas verticais do tecido (coluna de sombra + coluna de brilho)."""
        if phase is None:
            phase = self.rnd.randrange(period)
        for i in range(self.w):
            k = (i + phase) % period
            if k == 0:
                self.col(i, dark)
            elif k == 1 and period >= 4:
                self.col(i, light)

    def ragged(self, depth, sym=True, chance=0.65):
        """Barra esfarrapada: rasgos transparentes de baixo para cima (simetrico para frente/costas baterem)."""
        cols = [0] * self.w
        for i in range(self.w):
            if self.rnd.random() < chance:
                cols[i] = self.rnd.randint(1, depth)
        if sym:
            for i in range(self.w // 2):
                cols[self.w - 1 - i] = cols[i]
        for i, d in enumerate(cols):
            for k in range(d):
                self.clear(i, self.h - 1 - k)
        return cols

    def stars(self, n, core, glow_core=None, arm=None, margin=1):
        placed = []
        for _ in range(n * 6):
            if len(placed) >= n:
                break
            i = self.rnd.randint(margin, max(margin, self.w - 1 - margin))
            j = self.rnd.randint(margin, max(margin, self.h - 1 - margin))
            if any(abs(i - a) + abs(j - b) < 4 for a, b in placed):
                continue
            placed.append((i, j))
            if glow_core is not None:
                self.glow(i, j, glow_core)
            else:
                self.set(i, j, core)
            if arm is not None:
                for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if self.get(i + di, j + dj)[3] > 0:
                        self.set(i + di, j + dj, arm)
        return placed


def trim_rows(r, G, rows, at_bottom=True):
    """Debrum dourado/colorido de 'rows' linhas (G = paleta escuro->claro)."""
    for k in range(rows):
        j = r.h - 1 - k if at_bottom else k
        if rows == 1:
            c = G[2]
        elif at_bottom:
            c = G[1] if k == 0 else (G[3] if k == rows - 1 else G[2])
        else:
            c = G[3] if k == 0 else (G[1] if k == rows - 1 else G[2])
        r.row(j, c)
    for i in range(0, r.w, 3):
        j = r.h - 1 - (rows // 2) if at_bottom else rows // 2
        if rows >= 2 and r.get(i, j)[3] > 0:
            r.set(i, j, G[4] if len(G) > 4 else G[3])


def cloth(r, P, period=4, base=2, top=0.10, bottom=0.18, amt=0.035, folds=True):
    r.fill(P[base])
    if folds and r.w >= 3:
        r.folds(P[base - 1], P[base + 1] if base + 1 < len(P) else P[base], period)
    r.vshade(top, bottom)
    r.noise(amt)


def leather(r, L, base=2, seams=True, speckle=0.10):
    r.fill(L[base])
    for j in range(r.h):
        for i in range(r.w):
            x = r.rnd.random()
            if x < speckle:
                r.set(i, j, L[base - 1])
            elif x < speckle * 1.4:
                r.set(i, j, L[base + 1])
    if seams and r.h >= 4:
        for i in range(0, r.w, 2):
            r.set(i, 1, L[base + 1])
    r.vshade(0.08, 0.15)


def leaves(r, LF, flowers=None, flower_chance=0.10):
    """Folhas em escamas: fileiras desencontradas de folhas 4x3 com ponta para baixo (as de baixo cobrem as de cima)."""
    r.fill(LF[0])
    shape = [(1, 0, 3), (2, 0, 2), (0, 1, 3), (1, 1, 2), (2, 1, 2), (3, 1, 1), (1, 2, 2), (2, 2, 1)]
    row = 0
    for j0 in range(-1, r.h, 2):
        off = 2 if row % 2 else 0
        for i0 in range(-off, r.w, 4):
            tone = r.rnd.choice((0, 0, 1, -1))
            if flowers and r.rnd.random() < flower_chance:
                fl = r.rnd.choice(flowers)
                for dx, dy in ((1, 0), (2, 0), (1, 1), (2, 1), (0, 1), (3, 1), (1, 2), (2, 2)):
                    r.set(i0 + dx, j0 + dy, fl[1])
                r.set(i0 + 1, j0 + 1, fl[2]); r.set(i0 + 2, j0 + 1, YELLOW_CORE)
            else:
                for dx, dy, k in shape:
                    r.set(i0 + dx, j0 + dy, LF[max(1, min(4, k + tone))])
        row += 1
    r.vshade(0.08, 0.14)
    r.noise(0.02)


def bark(r, B):
    r.fill(B[2])
    for i in range(r.w):
        if r.rnd.random() < 0.45:
            r.col(i, B[1] if r.rnd.random() < 0.6 else B[3])
    for j in range(r.h):
        for i in range(r.w):
            if r.rnd.random() < 0.12:
                r.set(i, j, B[0])
    r.vshade(0.1, 0.12)


def bone(r, BN, horizontal=False):
    """Osso: tom claro com pontas mais escuras e rachaduras."""
    r.fill(BN[3])
    if horizontal:
        for j in range(r.h):
            r.set(0, j, BN[2]); r.set(r.w - 1, j, BN[2])
        if r.h >= 2:
            r.row(0, BN[4]); r.row(r.h - 1, BN[2])
    else:
        for i in range(r.w):
            r.set(i, 0, BN[2]); r.set(i, r.h - 1, BN[2])
        if r.w >= 2:
            r.col(0, BN[4]); r.col(r.w - 1, BN[2])
    for _ in range(max(1, r.w * r.h // 12)):
        r.set(r.rnd.randrange(r.w), r.rnd.randrange(r.h), BN[2])
    r.noise(0.03)


def metal(r, I, rivets=True):
    r.fill(I[2])
    r.row(0, I[4]) if r.h > 1 else None
    r.row(-1, I[1]) if r.h > 1 else None
    if r.w > 2:
        r.col(0, I[3]); r.col(-1, I[1])
    if rivets and r.w >= 3 and r.h >= 3:
        for (i, j) in ((1, 1), (r.w - 2, 1), (1, r.h - 2), (r.w - 2, r.h - 2)):
            r.set(i, j, I[4])
    r.noise(0.03)


def solid(c):
    def f(r):
        r.fill(c)
        r.noise(0.02)
    return f


def stripes_h(r, cols, period=2):
    for j in range(r.h):
        c = cols[(j // period) % len(cols)]
        r.row(j, c)
    r.noise(0.03)


def sigil(r, cx, cy, c, glow=True):
    """Selo runico 5x5 centrado: anel com cruz (centro mais claro)."""
    ring = [(-1, -2), (0, -2), (1, -2), (-2, -1), (2, -1), (-2, 0), (2, 0), (-2, 1), (2, 1), (-1, 2), (0, 2), (1, 2)]
    cross = [(0, -1), (-1, 0), (1, 0), (0, 1)]
    for dx, dy in ring + cross:
        (r.glow if glow else r.set)(cx + dx, cy + dy, c)
    (r.glow if glow else r.set)(cx, cy, lighten(c, 0.45))


def rune_glyph(r, i, j, c, kind, glow=True):
    """Runas 3x4 diferentes (riscos verticais/diagonais)."""
    shapes = [
        [(1, 0), (1, 1), (1, 2), (1, 3), (0, 1), (2, 2)],
        [(0, 0), (0, 1), (0, 2), (0, 3), (1, 1), (2, 0), (2, 2)],
        [(0, 0), (1, 1), (2, 2), (1, 3), (2, 0), (0, 2)],
        [(1, 0), (0, 1), (2, 1), (1, 2), (1, 3)],
        [(0, 0), (2, 0), (1, 1), (1, 2), (0, 3), (2, 3)],
    ]
    for dx, dy in shapes[kind % len(shapes)]:
        (r.glow if glow else r.set)(i + dx, j + dy, c)


# =====================================================================================
#  Modelo (ossos + cubos) e montagem do atlas
# =====================================================================================
def face_dims(face, size):
    sx, sy, sz = size
    if face in ("north", "south"):
        return sx, sy
    if face in ("east", "west"):
        return sz, sy
    return sx, sz


def fc(all=None, side=None, n=None, s=None, e=None, w=None, u=None, d=None, ew=None, ns=None):
    """Mapa face -> material. OMIT ('-') remove a face."""
    m = {f: all for f in FACES}
    if side is not None:
        for f in ("north", "south", "east", "west"):
            m[f] = side
    if ew is not None:
        m["east"] = m["west"] = ew
    if ns is not None:
        m["north"] = m["south"] = ns
    for k, f in (("n", "north"), ("s", "south"), ("e", "east"), ("w", "west"), ("u", "up"), ("d", "down")):
        v = locals()[k]
        if v is not None:
            m[f] = v
    return {f: (None if v in (None, OMIT) else v) for f, v in m.items()}


class Model:
    def __init__(self, name):
        self.name = name
        self.bones = []
        self.by = {}

    def bone(self, name, pivot, parent=None, rot=None):
        b = {"name": name, "pivot": [float(v) for v in pivot], "cubes": []}
        if parent:
            b["parent"] = parent
        if rot:
            b["rotation"] = [float(v) for v in rot]
        self.bones.append(b)
        self.by[name] = b
        return name

    def box(self, bone, origin, size, faces, inflate=0.0, rot=None, pivot=None):
        faces = dict(faces)
        for f in FACES:
            w, h = face_dims(f, size)
            if w <= 0 or h <= 0:
                faces[f] = None
        self.by[bone]["cubes"].append({"origin": [float(v) for v in origin], "size": [float(v) for v in size],
                                       "faces": faces, "inflate": inflate,
                                       "rotation": [float(v) for v in rot] if rot else None,
                                       "pivot": [float(v) for v in pivot] if pivot else None})

    def mirror(self, src, dst):
        """Copia os cubos de um osso do lado direito para o esquerdo (espelha em x)."""
        for c in self.by[src]["cubes"]:
            o, s = c["origin"], c["size"]
            f = dict(c["faces"])
            f["east"], f["west"] = c["faces"]["west"], c["faces"]["east"]
            rot = None
            if c["rotation"]:
                rx, ry, rz = c["rotation"]
                rot = [rx, -ry, -rz]
            piv = None
            if c["pivot"]:
                piv = [-c["pivot"][0], c["pivot"][1], c["pivot"][2]]
            self.by[dst]["cubes"].append({"origin": [-(o[0] + s[0]), o[1], o[2]], "size": list(s), "faces": f,
                                          "inflate": c["inflate"], "rotation": rot, "pivot": piv})

    def seg(self, bone, start, d, length, t, faces, tz=None, overlap=0.0):
        """Segmento (chifre, galhada, raiz): caixa t x length x tz alinhada a direcao d. Devolve a ponta."""
        tz = t if tz is None else tz
        n = math.sqrt(sum(v * v for v in d))
        dx, dy, dz = (v / n for v in d)
        # recua 'overlap' na direcao do segmento para emendar com o anterior sem fresta
        sx, sy, sz = start[0] - dx * overlap, start[1] - dy * overlap, start[2] - dz * overlap
        length += overlap
        if dy >= 0:
            rx = -math.degrees(math.asin(max(-1, min(1, dz))))
            rz = math.degrees(math.atan2(dx, dy))
            origin = [sx - t / 2, sy, sz - tz / 2]
        else:
            # caixa cresce para baixo do pivo: alinha o eixo -y local com d
            rx = math.degrees(math.asin(max(-1, min(1, dz))))
            rz = math.degrees(math.atan2(-dx, -dy))
            origin = [sx - t / 2, sy - length, sz - tz / 2]
        self.box(bone, origin, [t, length, tz], faces, rot=[rx, 0, rz], pivot=[sx, sy, sz])
        return [sx + dx * length, sy + dy * length, sz + dz * length]


def skeleton(m):
    m.bone("armorHead", [0, 24, 0])
    m.bone("armorBody", [0, 24, 0])
    m.bone("armorRightArm", [-5, 22, 0])
    m.bone("armorLeftArm", [5, 22, 0])
    m.bone("armorRightLeg", [-1.9, 12, 0])
    m.bone("armorLeftLeg", [1.9, 12, 0])
    m.bone("armorRightBoot", [-1.9, 12, 0])
    m.bone("armorLeftBoot", [1.9, 12, 0])


def waist(m, slot):
    """Osso de cintura (segue o tronco) com os 4 paineis de saia; o renderer abre os paineis com o passo."""
    root = "waist" + slot
    m.bone(root, [0, 24, 0])
    low = slot.lower()
    m.bone(low + "SkirtFront", [0, 12, -2.75], parent=root)
    m.bone(low + "SkirtBack", [0, 12, 2.75], parent=root)
    m.bone(low + "SkirtRight", [-4.75, 12, 0], parent=root)
    m.bone(low + "SkirtLeft", [4.75, 12, 0], parent=root)
    return root


def skirt(m, slot, bottom, front, back, side, lining, edge, hem=None, flare=5.0, depth=1.0, width=10.5, top=12.0):
    """Saia de 4 paineis (frente/costas mais largas que os lados para nao abrir buraco nas quinas)."""
    low = slot.lower()
    hgt = top - bottom
    half = width / 2
    m.box(low + "SkirtFront", [-half, bottom, -2.25 - depth], [width, hgt, depth],
          fc(n=front, s=lining, ew=edge, u=edge + "@2x1" if "@" not in edge else edge, d=hem or OMIT),
          rot=[-flare, 0, 0], pivot=[0, top, -2.75])
    m.box(low + "SkirtBack", [-half, bottom, 2.25], [width, hgt, depth],
          fc(s=back, n=lining, ew=edge, u=edge + "@2x1" if "@" not in edge else edge, d=hem or OMIT),
          rot=[flare, 0, 0], pivot=[0, top, 2.75])
    m.box(low + "SkirtRight", [-half, bottom, -2.25], [depth, hgt, 4.5],
          fc(e=side, w=lining, ns=edge, u=OMIT, d=hem or OMIT),
          rot=[0, 0, flare], pivot=[-half + depth / 2, top, 0])
    m.mirror(low + "SkirtRight", low + "SkirtLeft")


def pack(reqs, W, Hh):
    sky = [0] * W
    pos = {}
    for key, w, h in sorted(reqs, key=lambda r: (-r[2], -r[1], str(r[0]))):
        if w > W:
            return None
        best = None
        for x in range(0, W - w + 1):
            y = max(sky[x:x + w])
            if y + h <= Hh and (best is None or y < best[1]):
                best = (x, y)
        if best is None:
            return None
        x, y = best
        for i in range(x, x + w):
            sky[i] = y + h
        pos[key] = (x, y)
    return pos


def region_key(mat, fw, fh):
    if "@" in mat:
        name, dims = mat.split("@")
        rw, rh = (int(v) for v in dims.split("x"))
        return (name, rw, rh)
    return (mat, max(1, math.ceil(fw - 1e-6)), max(1, math.ceil(fh - 1e-6)))


def r4(v):
    v = round(v, 4)
    return int(v) if v == int(v) else v


def build(m, mats, size=(64, 64)):
    """Monta o atlas (UV por face), pinta as regioes e escreve geo.json + png (+ glowmask)."""
    keys = {}
    for b in m.bones:
        for c in b["cubes"]:
            for f, mat in c["faces"].items():
                if mat is None:
                    continue
                fw, fh = face_dims(f, c["size"])
                k = region_key(mat, fw, fh)
                if k[0] not in mats:
                    raise KeyError(f"[{m.name}] material desconhecido: {k[0]}")
                keys[k] = (k, k[1], k[2])
    W, Hh = size
    pos = None
    for cand in (size, (128, 64), (128, 128), (256, 128), (256, 256)):
        pos = pack(list(keys.values()), *cand)
        if pos is not None:
            W, Hh = cand
            break
    if pos is None:
        raise RuntimeError(f"[{m.name}] atlas nao coube")
    used = sum(k[1] * k[2] for k in keys)
    print(f"  {m.name}: {len(keys)} regioes, {used} texels, atlas {W}x{Hh}")

    cv = Canvas(W, Hh)
    gl = Canvas(W, Hh)
    for k, (x, y) in sorted(pos.items(), key=lambda kv: str(kv[0])):
        seed = sum(ord(ch) * (i + 7) for i, ch in enumerate(f"{m.name}:{k}"))
        mats[k[0]](Region(cv, gl, x, y, k[1], k[2], seed))

    bones = []
    for b in m.bones:
        jb = {"name": b["name"]}
        if "parent" in b:
            jb["parent"] = b["parent"]
        jb["pivot"] = [r4(v) for v in b["pivot"]]
        if "rotation" in b:
            jb["rotation"] = [r4(v) for v in b["rotation"]]
        cubes = []
        for c in b["cubes"]:
            jc = {"origin": [r4(v) for v in c["origin"]], "size": [r4(v) for v in c["size"]]}
            if c["inflate"]:
                jc["inflate"] = r4(c["inflate"])
            if c["rotation"] and any(abs(v) > 1e-6 for v in c["rotation"]):
                jc["pivot"] = [r4(v) for v in (c["pivot"] or c["origin"])]
                jc["rotation"] = [r4(v) for v in c["rotation"]]
            uv = {}
            for f in FACES:
                mat = c["faces"].get(f)
                if mat is None:
                    continue
                fw, fh = face_dims(f, c["size"])
                k = region_key(mat, fw, fh)
                x, y = pos[k]
                uv[f] = {"uv": [x, y], "uv_size": [k[1], k[2]]}
            if not uv:
                continue
            jc["uv"] = uv
            cubes.append(jc)
        if cubes:
            jb["cubes"] = cubes
        bones.append(jb)

    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + m.name, "texture_width": W, "texture_height": Hh,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    write_json(os.path.join(A, "geo/item/armor", m.name + ".geo.json"), geo)
    cv.save(os.path.join(A, "textures/item/armor", m.name + ".png"))
    has_glow = any(gl.px[y][x][3] > 0 for y in range(Hh) for x in range(W))
    gpath = os.path.join(A, "textures/item/armor", m.name + "_glowmask.png")
    if has_glow:
        gl.save(gpath)
    elif os.path.exists(gpath):
        os.remove(gpath)
    return cv, has_glow


# =====================================================================================
#  MAGO: chapeu pontudo de aba larga com estrelas, robe azul com debrum dourado, saia longa, botas pontudas
# =====================================================================================
NAVY = pal("0B1230", "162254", "213379", "2F48A3", "4D6BCC")
GOLD = pal("5E3F0E", "94691E", "C4962E", "EBC45C", "FFEBA6")
VIOLET = pal("1C1236", "2E2058", "443384", "5C4AA8")
MBROWN = pal("1E140E", "33241A", "4A3626", "624A36")


def mage_mats(NAVY=NAVY, GOLD=GOLD, VIOLET=VIOLET, MBROWN=MBROWN):
    M = {}

    def hat(r):
        cloth(r, NAVY, period=3, base=2, top=0.12, bottom=0.10)
        r.stars(max(1, r.w * r.h // 16), GOLD[4], glow_core=GOLD[4] if r.w * r.h > 12 else None, arm=GOLD[3])

    def hat_plain(r):
        cloth(r, NAVY, period=3, base=2)

    def band(r, front=False):
        cloth(r, NAVY, period=3)
        trim_rows(r, GOLD, 2)
        if front:
            cx = r.w // 2
            for (i, j) in ((cx - 1, 0), (cx, 0), (cx - 2, 1), (cx - 2, 2), (cx - 1, 2), (cx, 2)):
                r.set(i, j, GOLD[4])
            r.glow(cx - 1, 1, H("FFF6C8"))

    def brim_top(r):
        r.fill(NAVY[2])
        for j in range(r.h):
            for i in range(r.w):
                d = min(i, j, r.w - 1 - i, r.h - 1 - j)
                if d == 0:
                    r.set(i, j, GOLD[2])
                elif d == 1 and r.w > 4 and r.h > 4:
                    r.set(i, j, NAVY[3])
        r.noise(0.04)
        if r.w >= 6 and r.h >= 6:
            r.stars(r.w * r.h // 22, GOLD[4], glow_core=GOLD[4], arm=GOLD[3], margin=2)

    def brim_strip_top(r):
        r.fill(NAVY[2])
        if r.w >= r.h:
            r.row(0, GOLD[2])
        else:
            r.col(0, GOLD[2])
        r.noise(0.04)

    M["mage_hat"] = hat
    M["mage_hat_plain"] = hat_plain
    M["mage_band"] = band
    M["mage_band_front"] = lambda r: band(r, True)
    M["mage_brim_top"] = brim_top
    M["mage_brim_strip"] = brim_strip_top
    M["mage_brim_edge"] = lambda r: (r.fill(GOLD[2]), r.row(0, GOLD[3]) if r.h > 1 else None, r.noise(0.04))
    M["mage_brim_under"] = solid(NAVY[1])

    def robe(r, kind):
        cloth(r, NAVY, period=4, base=2)
        trim_rows(r, GOLD, 2)
        if kind == "front":
            # abertura central com debrum, gola em V e broche de estrela
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, GOLD[3]); r.set(cx, j, GOLD[1])
            for k in range(3):
                r.set(cx - 2 - k, k, GOLD[2]); r.set(cx + 1 + k, k, GOLD[2])
                for q in range(cx - 1 - k, cx + 1 + k):
                    r.set(q, k, VIOLET[2])
            r.set(cx - 1, 0, VIOLET[1]); r.set(cx, 0, VIOLET[1])
            for (i, j) in ((cx - 3, 5), (cx - 4, 5), (cx - 2, 5), (cx - 3, 4), (cx - 3, 6)):
                r.set(i, j, GOLD[3])
            r.glow(cx - 3, 5, H("FFF6C8"))
            r.row(8, GOLD[1])
        elif kind == "back":
            cx = r.w // 2
            for (i, j) in ((cx - 1, 3), (cx, 3), (cx - 1, 4), (cx, 4), (cx - 2, 4), (cx + 1, 4), (cx - 1, 5), (cx, 5),
                           (cx - 1, 2), (cx, 6)):
                r.set(i, j, GOLD[2])
            r.glow(cx - 1, 4, GOLD[4]); r.glow(cx, 4, GOLD[4])
        elif kind == "side":
            r.row(8, GOLD[1]) if r.h > 9 else None

    M["mage_robe_front"] = lambda r: robe(r, "front")
    M["mage_robe_back"] = lambda r: robe(r, "back")
    M["mage_robe_side"] = lambda r: robe(r, "side")
    M["mage_robe_top"] = lambda r: (cloth(r, NAVY, period=4, base=3, folds=False), r.row(0, GOLD[2]), r.row(-1, GOLD[2]))
    M["mage_dark"] = solid(NAVY[0])
    M["mage_lining"] = lambda r: cloth(r, VIOLET, period=4, base=1, folds=False)

    def belt(r, front=False):
        r.fill(GOLD[1])
        r.row(0, GOLD[3])
        r.noise(0.03)
        if front:
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, GOLD[4]); r.set(cx, j, GOLD[3])
            r.glow(cx - 1, r.h // 2, H("8FD3FF"))

    M["mage_belt"] = belt
    M["mage_belt_front"] = lambda r: belt(r, True)

    def capelet(r, outer):
        if outer:
            cloth(r, NAVY, period=3, base=2, top=0.10, bottom=0.08)
            r.col(0, GOLD[2]); r.col(-1, GOLD[2])
            cx = r.w // 2
            for (i, j) in ((cx, 2), (cx, 3), (cx, 5), (cx, 6), (cx - 2, 4), (cx - 1, 4), (cx + 1, 4), (cx + 2, 4),
                           (cx - 1, 3), (cx + 1, 3), (cx - 1, 5), (cx + 1, 5)):
                r.set(i, j, GOLD[3])
            r.glow(cx, 4, H("FFF6C8"))
        else:
            cloth(r, VIOLET, period=3, base=2, folds=False)
            r.col(0, GOLD[2]); r.col(-1, GOLD[2])
        # barra recortada em pontas (triangulos) com debrum
        for i in range(r.w):
            k = abs((i % 4) - 1.5)
            depth = 0 if k > 1 else 1
            for d in range(depth):
                r.clear(i, r.h - 1 - d)
            r.set(i, r.h - 1 - depth, GOLD[2])
            r.set(i, r.h - 2 - depth, GOLD[3] if outer else VIOLET[3])

    M["mage_capelet"] = lambda r: capelet(r, True)
    M["mage_capelet_in"] = lambda r: capelet(r, False)
    M["mage_gold"] = lambda r: (r.fill(GOLD[2]), r.noise(0.04))

    def sleeve(r):
        cloth(r, NAVY, period=4, base=2)
        r.row(0, NAVY[3])

    def bell(r):
        cloth(r, NAVY, period=3, base=2, top=0.06, bottom=0.12)
        trim_rows(r, GOLD, 2)
        if r.h > 3:
            r.row(1, NAVY[3])
            for i in range(1, r.w, 3):
                r.set(i, 1, GOLD[3])

    M["mage_sleeve"] = sleeve
    M["mage_sleeve_top"] = lambda r: (cloth(r, NAVY, base=3, folds=False), r.row(0, GOLD[2]))
    M["mage_bell"] = bell
    M["mage_bell_inside"] = lambda r: (r.fill(VIOLET[0]), r.noise(0.03))

    def skirt_face(r, kind):
        cloth(r, NAVY, period=3 if kind == "side" else 4, base=2, top=0.04, bottom=0.14)
        trim_rows(r, GOLD, 2)
        if kind == "front":
            cx = r.w // 2
            for j in range(r.h - 2):
                r.set(cx - 1, j, GOLD[3]); r.set(cx, j, GOLD[1])
            r.stars(3, GOLD[4], glow_core=GOLD[4], arm=GOLD[3], margin=1)
        elif kind == "back":
            r.stars(3, GOLD[4], glow_core=GOLD[4], arm=GOLD[3], margin=1)
        if r.h > 4:
            r.row(r.h - 3, NAVY[1])

    M["mage_skirt_front"] = lambda r: skirt_face(r, "front")
    M["mage_skirt_back"] = lambda r: skirt_face(r, "back")
    M["mage_skirt_side"] = lambda r: skirt_face(r, "side")
    M["mage_skirt_edge"] = lambda r: (r.fill(NAVY[1]), trim_rows(r, GOLD, 2))
    M["mage_trousers"] = lambda r: (cloth(r, NAVY, period=4, base=1, top=0.05, bottom=0.10))

    def boot(r, front=False):
        cloth(r, NAVY, period=4, base=1, top=0.12, bottom=0.05, folds=False)
        r.row(-1, MBROWN[1])
        if front and r.w >= 4:
            cx = r.w // 2
            for j in range(1, r.h - 1, 2):
                r.set(cx - 1, j, GOLD[3]); r.set(cx, j, GOLD[2])

    M["mage_boot"] = boot
    M["mage_boot_front"] = lambda r: boot(r, True)
    M["mage_boot_toe"] = lambda r: (cloth(r, NAVY, base=1, folds=False), r.row(0, NAVY[3]))
    M["mage_toe_tip"] = lambda r: (r.fill(GOLD[2]), r.row(0, GOLD[4]), r.noise(0.03))
    M["mage_sole"] = solid(MBROWN[1])

    def cuff(r):
        r.fill(NAVY[3])
        r.row(0, GOLD[3])
        r.row(-1, GOLD[1])
        r.noise(0.04)

    M["mage_cuff"] = cuff
    return M


def mage_model(name="outfit_mage"):
    m = Model(name)
    skeleton(m)
    hd = "armorHead"
    # Aba larga "arredondada": placa central + 4 faixas
    m.box(hd, [-7, 29, -7], [14, 1, 14], fc(side="mage_brim_edge", u="mage_brim_top", d="mage_brim_under@4x4"))
    for o, s, omit in (([-5, 29, -8], [10, 1, 1], "south"), ([-5, 29, 7], [10, 1, 1], "north"),
                       ([-8, 29, -5], [1, 1, 10], "west"), ([7, 29, -5], [1, 1, 10], "east")):
        f = fc(side="mage_brim_edge", u="mage_brim_strip", d="mage_brim_under@4x4")
        f[omit] = None
        m.box(hd, o, s, f)
    m.box(hd, [-4.5, 30, -4.5], [9, 3, 9], fc(side="mage_band", n="mage_band_front", u="mage_hat_plain", d=OMIT))
    m.bone("hatMid", [0, 33, 0], parent=hd, rot=[-8, 0, 0])
    m.box("hatMid", [-3.5, 33, -3.5], [7, 3.5, 7], fc(side="mage_hat", u="mage_hat_plain", d=OMIT))
    m.box("hatMid", [-2.5, 36.5, -2.5], [5, 3.5, 5], fc(side="mage_hat", u="mage_hat_plain", d=OMIT))
    m.bone("hatTip", [0, 40, 0], parent="hatMid", rot=[-20, 0, 0])
    m.box("hatTip", [-1.5, 40, -1.5], [3, 3, 3], fc(side="mage_hat_plain", u="mage_hat_plain", d=OMIT))
    m.box("hatTip", [-1, 42.6, -1], [2, 2.2, 2], fc(side="mage_hat_plain", u="mage_hat_plain", d=OMIT),
          rot=[-28, 0, 0], pivot=[0, 42.6, 1])
    m.box("hatTip", [-0.5, 44.4, 0.6], [1, 1, 1], fc(all="mage_toe_tip"), rot=[-40, 0, 0], pivot=[0, 44.4, 1])

    bd = "armorBody"
    m.box(bd, [-4, 12, -2], [8, 12, 4], fc(n="mage_robe_front", s="mage_robe_back", ew="mage_robe_side",
                                         u="mage_robe_top", d="mage_dark@2x2"), inflate=0.6)
    m.box(bd, [-4, 12, -2], [8, 2, 4], fc(n="mage_belt_front", s="mage_belt", ew="mage_belt", u="mage_gold@2x2",
                                        d="mage_gold@2x2"), inflate=0.95)
    # Capinha curta nas costas (mexe com o vento/corrida pelo renderer)
    m.bone("outfitCape", [0, 24, 2.65], parent=bd)
    m.box("outfitCape", [-4.5, 13.5, 2.65], [9, 10.5, 0.6], fc(s="mage_capelet", n="mage_capelet_in",
                                                             ew="mage_gold@1x6", u="mage_gold@3x1", d=OMIT))

    ra = "armorRightArm"
    m.box(ra, [-8, 17, -2], [4, 7, 4], fc(side="mage_sleeve", u="mage_sleeve_top", d=OMIT), inflate=0.6)
    m.box(ra, [-8.5, 13, -2.5], [5, 5, 5], fc(side="mage_bell", u="mage_sleeve_top@3x3", d="mage_bell_inside@2x2"),
          inflate=0.6)
    m.mirror(ra, "armorLeftArm")

    waist(m, "Legs")
    skirt(m, "Legs", 1.0, "mage_skirt_front", "mage_skirt_back", "mage_skirt_side", "mage_lining@4x4",
          "mage_skirt_edge", hem="mage_gold@2x2", flare=5)
    rl = "armorRightLeg"
    m.box(rl, [-3.9, 0, -2], [4, 12, 4], fc(side="mage_trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(rl, "armorLeftLeg")

    rb = "armorRightBoot"
    m.box(rb, [-3.9, 0, -2], [4, 5, 4], fc(side="mage_boot", n="mage_boot_front", u=OMIT, d="mage_sole@2x2"),
          inflate=0.55)
    m.box(rb, [-3.9, 4.3, -2], [4, 1.6, 4], fc(side="mage_cuff", u="mage_dark@2x2", d="mage_dark@2x2"), inflate=0.85)
    m.box(rb, [-3.4, -0.55, -4.6], [3, 2, 2.6], fc(n="mage_boot_toe", ew="mage_boot_toe", u="mage_boot_toe",
                                                 d="mage_sole@2x2", s=OMIT))
    m.box(rb, [-2.4, 0.45, -6.0], [1, 1, 1.8], fc(all="mage_toe_tip", s=OMIT), rot=[-35, 0, 0],
          pivot=[-1.9, 0.45, -4.2])
    m.mirror(rb, "armorLeftBoot")
    return m


# =====================================================================================
#  BRUXA: chapeu torto com fivela, vestido roxo com corpete e avental de ervas, meias listradas, sapatos com fivela
# =====================================================================================
WHAT = pal("0C0910", "17121D", "241C2C", "33293E", "463A55")
PURP = pal("26103A", "3E1A58", "5A2A7E", "7A3EA4", "9E62CA")
LACE = pal("7A7060", "AAA08A", "D2C9B0", "EEE8D6")
APRON = pal("6A5F4A", "968A6C", "BFB18E", "DDD2B2")
HERB = pal("27441A", "3A6426", "568A36", "80B652")
SILVER = pal("4E4E58", "7E7E8A", "AEAEBA", "D8D8E2", "F6F6FC")
POTION = pal("1E6A2A", "34A848", "6CF080", "C8FFD0")
WBROWN = pal("2E1E12", "4A321E", "684A2E", "8A6642")


def witch_mats(WHAT=WHAT, PURP=PURP, LACE=LACE, APRON=APRON, HERB=HERB, SILVER=SILVER, POTION=POTION, WBROWN=WBROWN,
               GOLD=GOLD, STRIPE=H("B98AE0")):
    M = {}

    def hat(r, patch=False):
        cloth(r, WHAT, period=3, base=2, top=0.14, bottom=0.08)
        if patch and r.w >= 5 and r.h >= 3:
            x0, y0 = r.w // 2 - 1, max(0, r.h // 2 - 1)
            for j in range(y0, y0 + 2):
                for i in range(x0, x0 + 3):
                    r.set(i, j, PURP[1])
            for i in range(x0, x0 + 3, 2):
                r.set(i, y0, LACE[1])
            r.set(x0 + 1, y0 + 1, LACE[1])

    M["witch_hat"] = hat
    M["witch_hat_patch"] = lambda r: hat(r, True)

    def brim(r):
        r.fill(WHAT[2])
        for j in range(r.h):
            for i in range(r.w):
                if min(i, j, r.w - 1 - i, r.h - 1 - j) == 0:
                    r.set(i, j, WHAT[3])
                elif r.rnd.random() < 0.15:
                    r.set(i, j, WHAT[1])
        r.noise(0.04)

    M["witch_brim"] = brim
    M["witch_brim_edge"] = lambda r: (r.fill(WHAT[3]), r.noise(0.04))
    M["witch_brim_under"] = solid(WHAT[0])

    def band(r, front=False):
        r.fill(PURP[2])
        r.row(0, PURP[3])
        r.row(-1, PURP[1])
        r.noise(0.03)

    M["witch_band"] = band

    def buckle(r):
        r.fill(SILVER[2])
        for j in range(r.h):
            for i in range(r.w):
                if 0 < i < r.w - 1 and 0 < j < r.h - 1:
                    r.set(i, j, PURP[1])
        r.set(0, 0, SILVER[4])
        r.noise(0.02)

    M["witch_buckle"] = buckle
    M["witch_silver"] = lambda r: (r.fill(SILVER[2]), r.noise(0.03))

    def sprig(r):
        # raminho de ervas (recorte): caule + folhas alternadas + florzinha lilas
        cx = r.w // 2
        for j in range(r.h):
            r.set(cx, j, HERB[1])
        for j in range(0, r.h - 1, 1):
            side = -1 if j % 2 == 0 else 1
            r.set(cx + side, j, HERB[3] if j < 2 else HERB[2])
        r.set(cx, 0, PURP[4])

    M["witch_sprig"] = sprig

    def dress(r, kind):
        cloth(r, PURP, period=4, base=2)
        if kind == "front":
            cx = r.w // 2
            # gola de renda
            r.row(0, LACE[2])
            for i in range(cx - 2, cx + 2):
                r.set(i, 1, LACE[3] if i % 2 else LACE[2])
            r.set(cx - 1, 2, LACE[2]); r.set(cx, 2, LACE[2])
            # colar com pingente verde
            r.glow(cx - 1, 3, POTION[2])
        elif kind == "back":
            r.row(0, LACE[2])
        r.row(-1, PURP[1])

    M["witch_dress_front"] = lambda r: dress(r, "front")
    M["witch_dress_back"] = lambda r: dress(r, "back")
    M["witch_dress_side"] = lambda r: dress(r, "side")
    M["witch_dress_top"] = lambda r: (cloth(r, PURP, base=3, folds=False), r.row(0, LACE[2]))

    def corset(r, front=False):
        r.fill(WHAT[2])
        r.row(0, WHAT[4]); r.row(-1, WHAT[1])
        for i in range(0, r.w, 2):
            for j in range(1, r.h - 1):
                if r.get(i, j) == WHAT[2]:
                    r.set(i, j, WHAT[3])
        if front:
            cx = r.w // 2
            for j in range(1, r.h - 1):
                r.set(cx - 1 + (j % 2), j, SILVER[3])
                r.set(cx - (j % 2), j, SILVER[1])
        r.noise(0.03)

    M["witch_corset"] = corset
    M["witch_corset_front"] = lambda r: corset(r, True)
    M["witch_dark"] = solid(WHAT[1])

    def puff(r):
        cloth(r, PURP, period=2, base=2, top=0.16, bottom=0.12)
        r.row(-1, PURP[1])

    M["witch_puff"] = puff
    M["witch_sleeve"] = lambda r: cloth(r, PURP, period=4, base=1, top=0.06, bottom=0.08)

    def lacecuff(r):
        r.fill(LACE[2])
        r.row(0, LACE[3])
        for i in range(r.w):
            if i % 2:
                r.set(i, r.h - 1, LACE[1])
        r.noise(0.02)

    M["witch_lace"] = lacecuff
    M["witch_lace_flat"] = solid(LACE[2])

    def wskirt(r, kind):
        cloth(r, PURP, period=3, base=2, top=0.05, bottom=0.12)
        # barra de renda e faixa escura
        r.row(-1, LACE[2])
        if r.h > 3:
            r.row(-2, PURP[1])
            for i in range(0, r.w, 2):
                r.set(i, r.h - 1, LACE[3])
        if kind == "back" and r.h > 4:
            # laco do corpete caindo sobre a saia
            cx = r.w // 2
            for (i, j) in ((cx - 2, 0), (cx - 1, 0), (cx, 0), (cx + 1, 0), (cx - 1, 1), (cx, 1), (cx - 1, 2), (cx, 3)):
                r.set(i, j, WHAT[3])

    M["witch_skirt_front"] = lambda r: wskirt(r, "front")
    M["witch_skirt_back"] = lambda r: wskirt(r, "back")
    M["witch_skirt_side"] = lambda r: wskirt(r, "side")
    M["witch_skirt_edge"] = lambda r: (r.fill(PURP[1]), r.row(-1, LACE[2]))
    M["witch_lining"] = solid(PURP[0])
    M["witch_hem"] = solid(LACE[1])

    def apron(r):
        r.fill(APRON[2])
        r.folds(APRON[1], APRON[3], 3)
        r.row(0, APRON[3])
        for i in range(r.w):
            if i % 2 == 0:
                r.set(i, r.h - 1, APRON[1])
        # bolso com ervas
        x0, y0 = 1, max(1, r.h - 4)
        for i in range(x0, min(r.w - 1, x0 + 3)):
            r.set(i, y0, APRON[1])
            r.set(i, y0 + 1, APRON[2])
            r.set(i, y0 + 2, APRON[2])
        r.set(x0, y0 - 1, HERB[3]); r.set(x0 + 1, y0 - 1, HERB[2]); r.set(x0 + 1, y0 - 2, HERB[3])
        r.set(x0 + 2, y0 - 1, PURP[4])
        # raminhos bordados
        for (i, j) in ((r.w - 3, 1), (r.w - 2, 2), (r.w - 3, 3), (r.w - 2, 4)):
            r.set(i, j, HERB[2])
        r.set(r.w - 3, 2, HERB[3])
        r.noise(0.03)

    M["witch_apron"] = apron
    M["witch_apron_edge"] = solid(APRON[1])

    def stocking(r):
        stripes_h(r, [STRIPE, WHAT[1]], period=1)
        r.map(lambda i, j, p: darken(p, 0.12) if i in (0, r.w - 1) else p)

    M["witch_stocking"] = stocking

    def shoe(r, front=False):
        r.fill(WHAT[2])
        r.row(0, WHAT[4])
        r.row(-1, WHAT[0])
        r.noise(0.04)

    M["witch_shoe"] = shoe
    M["witch_shoe_toe"] = lambda r: (r.fill(WHAT[2]), r.row(0, WHAT[4]), r.noise(0.04))
    M["witch_sole"] = solid(WHAT[0])

    def gold_buckle(r):
        r.fill(GOLD[3])
        for j in range(1, r.h - 1):
            for i in range(1, r.w - 1):
                r.set(i, j, WHAT[1])
        r.set(0, 0, GOLD[4])

    M["witch_shoe_buckle"] = gold_buckle
    M["witch_gold"] = lambda r: (r.fill(GOLD[2]), r.noise(0.03))

    def vial(r):
        r.fill(POTION[1])
        for j in range(r.h):
            for i in range(r.w):
                if j >= 1:
                    r.glow(i, j, POTION[2] if (i + j) % 3 else POTION[3])
        r.row(0, WBROWN[2])

    M["witch_vial"] = vial
    M["witch_cork"] = solid(WBROWN[2])
    M["witch_pouch"] = lambda r: (leather(r, WBROWN), r.row(0, WBROWN[3]))
    return M


def witch_model(name="outfit_witch"):
    m = Model(name)
    skeleton(m)
    hd = "armorHead"
    piv = [0, 29.5, 0]
    tilt = [5, 0, 0]
    m.box(hd, [-6.5, 29, -6.5], [13, 1, 13], fc(side="witch_brim_edge", u="witch_brim", d="witch_brim_under@4x4"),
          rot=tilt, pivot=piv)
    for o, s, omit in (([-4.5, 29, -7.5], [9, 1, 1], "south"), ([-4.5, 29, 6.5], [9, 1, 1], "north"),
                       ([-7.5, 29, -4.5], [1, 1, 9], "west"), ([6.5, 29, -4.5], [1, 1, 9], "east")):
        f = fc(side="witch_brim_edge", u="witch_brim_edge", d="witch_brim_under@4x4")
        f[omit] = None
        m.box(hd, o, s, f, rot=tilt, pivot=piv)
    m.box(hd, [-4.5, 29.8, -4.5], [9, 1.8, 9], fc(side="witch_band", u=OMIT, d=OMIT))
    m.box(hd, [-1.5, 29.6, -4.95], [3, 2.2, 0.5], fc(n="witch_buckle", ew="witch_silver@1x2", u="witch_silver@2x1",
                                                   d="witch_silver@2x1", s=OMIT))
    m.box(hd, [4.55, 30.4, -1.5], [0, 4, 3], fc(ew="witch_sprig"), rot=[0, 0, 18], pivot=[4.55, 30.4, 0])
    m.box(hd, [-4.25, 31.5, -4.25], [8.5, 3, 8.5], fc(side="witch_hat", u="witch_hat", d=OMIT))
    m.bone("hatMid", [0, 34.5, 0], parent=hd, rot=[-10, 0, 10])
    m.box("hatMid", [-3.25, 34.5, -3.25], [6.5, 3.5, 6.5], fc(side="witch_hat", w="witch_hat_patch", u="witch_hat",
                                                            d=OMIT))
    m.box("hatMid", [-2.25, 38, -2.25], [4.5, 3.5, 4.5], fc(side="witch_hat", u="witch_hat", d=OMIT))
    m.bone("hatTip", [0, 41.5, 0], parent="hatMid", rot=[-14, 0, -34])
    m.box("hatTip", [-1.25, 41.5, -1.25], [2.5, 3, 2.5], fc(side="witch_hat", u="witch_hat", d=OMIT))
    m.box("hatTip", [-0.75, 44.3, -0.75], [1.5, 2.2, 1.5], fc(side="witch_hat", u="witch_hat", d=OMIT),
          rot=[0, 0, -30], pivot=[0, 44.3, 0])

    bd = "armorBody"
    m.box(bd, [-4, 12, -2], [8, 12, 4], fc(n="witch_dress_front", s="witch_dress_back", ew="witch_dress_side",
                                         u="witch_dress_top", d="witch_dark@2x2"), inflate=0.55)
    m.box(bd, [-4, 12.4, -2], [8, 5.6, 4], fc(n="witch_corset_front", s="witch_corset", ew="witch_corset",
                                            u="witch_dark@2x2", d=OMIT), inflate=0.85)

    ra = "armorRightArm"
    m.box(ra, [-8.5, 19, -2.5], [5, 5, 5], fc(side="witch_puff", u="witch_puff", d="witch_dark@2x2"), inflate=0.45)
    m.box(ra, [-8, 12, -2], [4, 7.5, 4], fc(side="witch_sleeve", u=OMIT, d=OMIT), inflate=0.4)
    m.box(ra, [-8, 12, -2], [4, 1.4, 4], fc(side="witch_lace", u="witch_lace_flat@2x2", d="witch_lace_flat@2x2"),
          inflate=0.75)
    m.mirror(ra, "armorLeftArm")

    # Saia do vestido (vai com o peitoral) ate o joelho, avental na frente, frasco e bolsinha na cintura
    root = waist(m, "Chest")
    skirt(m, "Chest", 6.5, "witch_skirt_front", "witch_skirt_back", "witch_skirt_side", "witch_lining@4x4",
          "witch_skirt_edge", hem="witch_hem@2x2", flare=8, depth=1.05)
    m.box("chestSkirtFront", [-3.5, 6.9, -3.7], [7, 5.1, 0.4], fc(n="witch_apron", ew="witch_apron_edge@1x4",
                                                            d="witch_apron_edge@2x1", u=OMIT, s=OMIT),
          rot=[-8, 0, 0], pivot=[0, 12, -2.75])
    m.box(root, [-5.9, 9.2, -0.8], [1.6, 2.2, 1.6], fc(side="witch_vial", u="witch_cork@2x2", d="witch_cork@2x2"))
    m.box(root, [-5.6, 11.4, -0.5], [1, 0.8, 1], fc(all="witch_cork@1x1"))
    m.box(root, [4.4, 8.8, -1.2], [1.5, 2.4, 2.4], fc(side="witch_pouch", u="witch_pouch", d="witch_cork@2x2"))

    rl = "armorRightLeg"
    m.box(rl, [-3.9, 0, -2], [4, 12, 4], fc(side="witch_stocking", u=OMIT, d=OMIT), inflate=0.4)
    m.mirror(rl, "armorLeftLeg")

    rb = "armorRightBoot"
    m.box(rb, [-3.9, 0, -2], [4, 2.4, 4], fc(side="witch_shoe", u="witch_sole@2x2", d="witch_sole@2x2"), inflate=0.55)
    m.box(rb, [-3.4, -0.55, -4.3], [3, 1.8, 2.3], fc(n="witch_shoe_toe", ew="witch_shoe_toe", u="witch_shoe_toe",
                                                   d="witch_sole@2x2", s=OMIT))
    m.box(rb, [-2.9, 0.9, -2.85], [2, 1.5, 0.5], fc(n="witch_shoe_buckle", ew="witch_gold@1x2", u="witch_gold@2x1",
                                                  d="witch_gold@2x1", s=OMIT))
    m.box(rb, [-3.4, -0.55, 1.7], [3, 1.2, 1.1], fc(all="witch_sole@2x2", n=OMIT))
    m.mirror(rb, "armorLeftBoot")
    return m


# =====================================================================================
#  WARLOCK: capuz preto com chifres, vestes negras com runas vermelhas, capa curta, calca com faixas, botas com placas
# =====================================================================================
BLK = pal("070609", "100E14", "1A171F", "26222C", "34303C")
RED = pal("3E0507", "6E0D10", "A81A1A", "E8392C", "FF8F70")
HORN = pal("221810", "3C2C20", "665240", "A08C72", "DCCFB4")
IRON = pal("1C1C22", "32323A", "4A4A56", "6C6C7A", "989AAA")
WLEATH = pal("1C130E", "2E2018", "422D22", "58402E")


def warlock_mats(BLK=BLK, RED=RED, HORN=HORN, IRON=IRON, WLEATH=WLEATH):
    M = {}

    def hood(r, kind):
        cloth(r, BLK, period=3, base=2, top=0.16, bottom=0.06)
        if kind == "front":
            # abertura do rosto (transparente) com sombra do capuz na testa
            for j in range(1, r.h):
                for i in range(1, r.w - 1):
                    r.clear(i, j)
            for i in range(1, r.w - 1):
                r.set(i, 1, BLK[0])
            r.set(1, 2, BLK[0]); r.set(r.w - 2, 2, BLK[0])
            r.col(0, BLK[3]); r.col(-1, BLK[3])
        elif kind == "back":
            cx = r.w // 2
            rune_glyph(r, cx - 1, 2, RED[3], 3)
        elif kind == "side":
            for j in range(r.h):
                r.set(0 if kind == "side" else r.w - 1, j, BLK[3])
        if kind != "front":
            r.row(-1, RED[1])

    M["warlock_hood_front"] = lambda r: hood(r, "front")
    M["warlock_hood_back"] = lambda r: hood(r, "back")
    M["warlock_hood_side"] = lambda r: hood(r, "side")
    M["warlock_hood_top"] = lambda r: (cloth(r, BLK, period=3, base=3, folds=False))
    M["warlock_hood_tail"] = lambda r: (cloth(r, BLK, period=3, base=2), r.row(-1, RED[2]))

    def horn(level):
        def f(r):
            base = [1, 2, 3, 4][level]
            r.fill(HORN[base])
            for j in range(r.h):
                if j % 2 == 1:
                    r.row(j, HORN[max(0, base - 1)])
            r.col(0, HORN[min(4, base + 1)])
            r.noise(0.04)
        return f

    for k in range(4):
        M[f"warlock_horn{k}"] = horn(k)

    def robe(r, kind):
        cloth(r, BLK, period=4, base=2)
        trim_rows(r, RED[:4] + [RED[3]], 1)
        cx = r.w // 2
        if kind == "front":
            # circulo runico no peito e runas descendo o centro
            for (i, j) in ((cx - 2, 2), (cx - 1, 1), (cx, 1), (cx + 1, 2), (cx + 1, 3), (cx, 4), (cx - 1, 4),
                           (cx - 2, 3)):
                r.glow(i, j, RED[2])
            r.glow(cx - 1, 2, RED[3]); r.glow(cx, 3, RED[3]); r.glow(cx - 1, 3, RED[4])
            for k, j in enumerate(range(6, r.h - 2, 4)):
                rune_glyph(r, cx - 1, j, RED[3], k)
        elif kind == "back":
            sigil(r, cx - 1, 4, RED[3])
            for k, j in enumerate(range(8, r.h - 2, 4)):
                rune_glyph(r, cx - 1, j, RED[2], k + 2)
        elif kind == "side":
            for k, j in enumerate(range(2, r.h - 3, 5)):
                rune_glyph(r, max(0, cx - 1), j, RED[2], k + 1)

    M["warlock_robe_front"] = lambda r: robe(r, "front")
    M["warlock_robe_back"] = lambda r: robe(r, "back")
    M["warlock_robe_side"] = lambda r: robe(r, "side")
    M["warlock_robe_top"] = lambda r: cloth(r, BLK, base=3, folds=False)
    M["warlock_dark"] = solid(BLK[0])
    M["warlock_red"] = solid(RED[1])

    def cowl(r):
        cloth(r, BLK, period=3, base=3, top=0.12, bottom=0.04)
        r.row(-1, RED[2])
        for i in range(0, r.w, 3):
            r.set(i, r.h - 1, RED[3])

    M["warlock_cowl"] = cowl

    def belt(r, front=False):
        leather(r, WLEATH, seams=False)
        r.row(0, WLEATH[3])
        if front:
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, IRON[3]); r.set(cx, j, IRON[2])
            r.glow(cx - 1, r.h // 2, RED[3])

    M["warlock_belt"] = belt
    M["warlock_belt_front"] = lambda r: belt(r, True)

    def cape(r, outer):
        if outer:
            cloth(r, BLK, period=4, base=2, top=0.05, bottom=0.10)
            cx = r.w // 2
            sigil(r, cx - 1 + (r.w % 2), 4, RED[3])
            for (i, j) in ((cx - 1, 1), (cx, 1)):
                r.glow(i, j, RED[2])
            r.row(0, BLK[4])
        else:
            cloth(r, RED, period=4, base=1, top=0.02, bottom=0.12)
        r.rnd.seed(777)
        r.ragged(3, sym=True, chance=0.7)

    M["warlock_cape_out"] = lambda r: cape(r, True)
    M["warlock_cape_in"] = lambda r: cape(r, False)
    M["warlock_cape_edge"] = lambda r: (r.fill(BLK[3]), r.noise(0.03))

    def plate(r, rune=True):
        metal(r, IRON)
        if rune and r.w >= 4 and r.h >= 3:
            cx = r.w // 2
            r.glow(cx - 1, r.h // 2, RED[3]); r.glow(cx, r.h // 2, RED[2])

    M["warlock_plate"] = plate
    M["warlock_plate_plain"] = lambda r: plate(r, False)
    M["warlock_iron"] = lambda r: (r.fill(IRON[2]), r.noise(0.03))
    M["warlock_spike"] = lambda r: (r.fill(IRON[3]), r.row(0, IRON[4]), r.noise(0.03))

    def sleeve(r):
        cloth(r, BLK, period=4, base=2)
        if r.h > 6:
            for i in range(r.w):
                r.glow(i, r.h - 5, RED[2] if i % 2 else RED[3])

    M["warlock_sleeve"] = sleeve

    def bracer(r):
        leather(r, WLEATH, seams=False)
        r.row(0, IRON[3]); r.row(-1, IRON[1])
        for i in range(1, r.w, 2):
            r.set(i, r.h // 2, IRON[3])

    M["warlock_bracer"] = bracer
    M["warlock_leather"] = lambda r: leather(r, WLEATH, seams=False)

    def pants(r):
        cloth(r, BLK, period=4, base=3, top=0.04, bottom=0.10)
        # faixas diagonais (bandagens) cruzando
        for j in range(r.h):
            i = (j * 2) % (r.w + 3) - 1
            r.set(i, j, RED[1]); r.set(i + 1, j, RED[1])

    M["warlock_pants"] = pants

    def band(r):
        r.fill(RED[1])
        r.row(0, RED[2])
        for i in range(0, r.w, 2):
            r.set(i, r.h - 1, RED[0])
        r.noise(0.04)

    M["warlock_band"] = band

    def tabard(r, outer):
        if outer:
            cloth(r, BLK, period=5, base=2, top=0.04, bottom=0.10)
            r.col(0, RED[1]); r.col(-1, RED[1])
            cx = r.w // 2
            sigil(r, cx, 3, RED[3])
        else:
            cloth(r, RED, period=5, base=1, folds=False)
        r.rnd.seed(4242)
        r.ragged(2, sym=True, chance=0.7)

    M["warlock_tabard"] = lambda r: tabard(r, True)
    M["warlock_tabard_in"] = lambda r: tabard(r, False)

    def boot(r, front=False):
        leather(r, WLEATH)
        r.row(-1, BLK[0])

    M["warlock_boot"] = boot
    M["warlock_sole"] = solid(BLK[0])
    return M


def warlock_model(name="outfit_warlock"):
    m = Model(name)
    skeleton(m)
    hd = "armorHead"
    m.box(hd, [-4, 24, -4], [8, 8, 8], fc(n="warlock_hood_front", s="warlock_hood_back", ew="warlock_hood_side",
                                        u="warlock_hood_top", d=OMIT), inflate=0.8)
    # ponta do capuz caindo para tras
    end = m.seg(hd, [0, 32.2, 3.0], (0, -0.45, 1), 3.0, 5.0, fc(side="warlock_hood_tail", u=OMIT,
                                                                 d="warlock_dark@2x2"), tz=3.2)
    m.seg(hd, end, (0, -1, 0.9), 2.4, 3.0, fc(side="warlock_hood_tail", u=OMIT, d="warlock_dark@2x2"), tz=1.8,
          overlap=1.0)
    # chifres: curvam para fora, sobem e voltam para tras
    p = [-4.0, 29.6, -0.2]
    segs = [((-1.0, 0.42, 0.08), 3.0, 2.3), ((-0.55, 1.0, 0.2), 2.8, 1.9), ((-0.12, 1.0, 0.6), 2.4, 1.45),
            ((0.12, 0.6, 1.0), 2.0, 1.0)]
    for k, (d, L, t) in enumerate(segs):
        p = m.seg(hd, p, d, L, t, fc(side=f"warlock_horn{k}", u=f"warlock_horn{k}@1x1",
                                     d=f"warlock_horn{k}@1x1"), overlap=0.0 if k == 0 else t * 0.45)
    # espelha os cubos dos chifres (os 4 ultimos) para o lado esquerdo
    horn_cubes = m.by[hd]["cubes"][-4:]
    tmp = m.bone("tmpHorn", [0, 24, 0])
    m.by[tmp]["cubes"] = horn_cubes
    m.mirror(tmp, hd)
    m.bones.remove(m.by[tmp])
    del m.by[tmp]

    bd = "armorBody"
    m.box(bd, [-4, 12, -2], [8, 12, 4], fc(n="warlock_robe_front", s="warlock_robe_back", ew="warlock_robe_side",
                                         u="warlock_robe_top", d="warlock_dark@2x2"), inflate=0.6)
    m.box(bd, [-4, 21, -2], [8, 3.2, 4], fc(side="warlock_cowl", u="warlock_dark@2x2", d="warlock_dark@2x2"),
          inflate=1.05)
    m.box(bd, [-4, 12, -2], [8, 1.6, 4], fc(n="warlock_belt_front", s="warlock_belt", ew="warlock_belt",
                                          u="warlock_leather@2x2", d=OMIT), inflate=0.95)
    m.bone("outfitCape", [0, 24, 2.65], parent=bd)
    m.box("outfitCape", [-4.5, 12.5, 2.65], [9, 11.5, 0.6], fc(s="warlock_cape_out", n="warlock_cape_in",
                                                        ew="warlock_cape_edge@1x8", u="warlock_cape_edge@2x1",
                                                        d=OMIT))

    ra = "armorRightArm"
    m.box(ra, [-8, 12, -2], [4, 12, 4], fc(side="warlock_sleeve", u=OMIT, d=OMIT), inflate=0.45)
    m.box(ra, [-8, 12.5, -2], [4, 3.5, 4], fc(side="warlock_bracer", u="warlock_leather@2x2",
                                            d="warlock_leather@2x2"), inflate=0.75)
    m.box(ra, [-9.2, 21.2, -2.8], [5.4, 2.6, 5.6], fc(side="warlock_plate_plain", u="warlock_plate",
                                                    d="warlock_dark@2x2"), rot=[0, 0, -15], pivot=[-6.5, 22.5, 0])
    m.seg(ra, [-7.4, 23.4, 0], (-0.45, 1, 0), 2.2, 1.0, fc(side="warlock_spike", u="warlock_spike@1x1", d=OMIT))
    m.seg(ra, [-7.0, 23.2, -1.8], (-0.35, 1, -0.2), 1.5, 0.8, fc(side="warlock_spike", u="warlock_spike@1x1",
                                                                 d=OMIT))
    m.mirror(ra, "armorLeftArm")

    waist(m, "Legs")
    m.box("waistLegs", [-4, 11, -2], [8, 1.2, 4], fc(side="warlock_band", u=OMIT, d=OMIT), inflate=0.78)
    m.box("legsSkirtFront", [-2.5, 3.5, -3.2], [5, 8.5, 0.5], fc(n="warlock_tabard", s="warlock_tabard_in",
                                                               ew="warlock_red@1x4", u=OMIT, d=OMIT),
          rot=[-4, 0, 0], pivot=[0, 12, -2.75])
    m.box("legsSkirtBack", [-2.5, 3.5, 2.7], [5, 8.5, 0.5], fc(s="warlock_tabard", n="warlock_tabard_in",
                                                             ew="warlock_red@1x4", u=OMIT, d=OMIT),
          rot=[4, 0, 0], pivot=[0, 12, 2.75])
    rl = "armorRightLeg"
    m.box(rl, [-3.9, 0, -2], [4, 12, 4], fc(side="warlock_pants", u=OMIT, d=OMIT), inflate=0.42)
    m.box(rl, [-3.9, 7.5, -2], [4, 1, 4], fc(side="warlock_band", u=OMIT, d=OMIT), inflate=0.62)
    m.box(rl, [-3.9, 9.5, -2], [4, 1, 4], fc(side="warlock_band", u=OMIT, d=OMIT), inflate=0.62)
    m.mirror(rl, "armorLeftLeg")

    rb = "armorRightBoot"
    m.box(rb, [-3.9, 0, -2], [4, 6, 4], fc(side="warlock_boot", u=OMIT, d="warlock_sole@2x2"), inflate=0.55)
    m.box(rb, [-3.6, 2, -3.05], [3.4, 4.2, 0.6], fc(n="warlock_plate", ew="warlock_iron@1x4", u="warlock_iron@3x1",
                                                  d="warlock_iron@3x1", s=OMIT))
    m.box(rb, [-3.65, -0.55, -3.3], [3.5, 2, 1.5], fc(n="warlock_plate_plain", ew="warlock_iron@2x2",
                                                    u="warlock_iron@3x1", d="warlock_sole@2x2", s=OMIT))
    m.box(rb, [-3.9, 5.5, -2], [4, 1, 4], fc(side="warlock_plate_plain", u="warlock_dark@2x2",
                                           d="warlock_dark@2x2"), inflate=0.8)
    m.mirror(rb, "armorLeftBoot")
    return m


# =====================================================================================
#  DRUIDA: coroa de galhos/chifres de cervo com folhas, manto de folhas com flores, calca de couro com cipos, sandalias
# =====================================================================================
LEAF = pal("183210", "28501A", "3E7426", "5E9E36", "92CC62")
BARK = pal("26180E", "3A2716", "563A22", "765434", "9A764A")
DLEATH = pal("38240F", "573A1F", "77542F", "996F44")
ANTLER = pal("54412C", "786042", "A1855E", "C8AC86", "E8D4B0")
PINK = pal("A0305E", "D9669A", "FFB2D2")
YELLOW = pal("B07E0E", "EDC234", "FFF09E")
WHITEF = pal("A8A89E", "E2E0D6", "FFFFFA")
VINE = pal("1F3E16", "2F5E22", "467E32", "66A246")


def druid_mats(LEAF=LEAF, BARK=BARK, DLEATH=DLEATH, ANTLER=ANTLER, PINK=PINK, YELLOW=YELLOW, WHITEF=WHITEF, VINE=VINE):
    M = {}
    FL = [PINK, YELLOW, WHITEF]

    def circlet(r):
        bark(r, BARK)
        for i in range(0, r.w, 3):
            r.set(i, 0, LEAF[3])
            r.set(i + 1, r.h - 1, LEAF[2])

    M["druid_circlet"] = circlet

    def leaf_cut(r, n=None, flower=None):
        """Folhas recortadas (transparencia): cada folha e uma gota diagonal com nervura."""
        r.fill(T)
        n = n or max(1, (r.w * r.h) // 5)
        for _ in range(n):
            cx, cy = r.rnd.randrange(r.w), r.rnd.randrange(r.h)
            sh = r.rnd.choice((0, 1))
            pts = [(0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)] if sh else [(0, 0), (1, 1), (0, 1), (1, 0),
                                                                                   (2, 1), (1, 2)]
            for dx, dy in pts:
                r.set(cx + dx, cy + dy, LEAF[r.rnd.choice((2, 3, 3, 4))])
            r.set(cx, cy, LEAF[1])
        if flower:
            i, j = r.w // 2, r.h // 2
            r.set(i, j, flower[2])
            for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                r.set(i + di, j + dj, flower[1])

    M["druid_leaf_cluster"] = lambda r: leaf_cut(r, flower=PINK)
    M["druid_leaf_row"] = lambda r: leaf_cut(r)
    M["druid_leaf_small"] = lambda r: leaf_cut(r, n=3)

    def flower(F):
        def f(r):
            r.fill(F[1])
            r.set(0, 0, F[2])
            r.noise(0.02)
        return f

    M["druid_flower_pink"] = flower(PINK)
    M["druid_flower_yellow"] = flower(YELLOW)
    M["druid_flower_white"] = flower(WHITEF)
    M["druid_flower_center"] = flower(YELLOW)

    def antler(r):
        r.fill(ANTLER[2])
        r.col(0, ANTLER[3])
        r.col(-1, ANTLER[1]) if r.w > 1 else None
        for j in range(0, r.h, 2):
            r.set(r.rnd.randrange(r.w), j, ANTLER[1])
        r.noise(0.04)

    M["druid_antler"] = antler
    M["druid_antler_tip"] = lambda r: (r.fill(ANTLER[4]), r.noise(0.02))

    def tunic(r, kind):
        leather(r, DLEATH, base=2, seams=False, speckle=0.03)
        r.folds(DLEATH[1], DLEATH[3], 4)
        r.vshade(0.06, 0.10)
        cx = r.w // 2
        if kind == "front":
            # cinto de cipo e broche de folha
            j = r.h - 3
            for i in range(r.w):
                r.set(i, j, VINE[2] if i % 2 else VINE[1])
                r.set(i, j + 1, VINE[1])
            r.set(cx - 1, j, BARK[3]); r.set(cx, j, BARK[3])
            r.set(cx - 1, j + 1, BARK[2]); r.set(cx, j + 1, BARK[2])
        r.row(-1, DLEATH[0])

    M["druid_tunic_front"] = lambda r: tunic(r, "front")
    M["druid_tunic_back"] = lambda r: tunic(r, "back")
    M["druid_tunic_side"] = lambda r: tunic(r, "side")
    M["druid_tunic_top"] = lambda r: leather(r, DLEATH, base=2, seams=False, speckle=0.03)
    M["druid_dark"] = solid(DLEATH[0])

    def mantle(r, ragged=True, toggle=False):
        leaves(r, LEAF, flowers=FL)
        if ragged:
            r.rnd.seed(901 + r.w)
            r.ragged(2, sym=True, chance=0.6)
        if toggle:
            cx = r.w // 2
            r.set(cx - 1, 2, BARK[3]); r.set(cx, 2, BARK[3]); r.set(cx - 1, 3, BARK[2]); r.set(cx, 3, BARK[2])

    M["druid_mantle_front"] = lambda r: mantle(r, True, True)
    M["druid_mantle"] = lambda r: mantle(r, True)
    M["druid_mantle_top"] = lambda r: mantle(r, False)
    M["druid_mantle_under"] = solid(LEAF[0])

    def cape(r, outer):
        if outer:
            leaves(r, LEAF, flowers=FL, flower_chance=0.07)
        else:
            leather(r, DLEATH, base=1, seams=False, speckle=0.04)
        r.rnd.seed(1313)
        r.ragged(3, sym=True, chance=0.75)

    M["druid_cape_out"] = lambda r: cape(r, True)
    M["druid_cape_in"] = lambda r: cape(r, False)
    M["druid_cape_edge"] = solid(LEAF[2])

    def sleeve(r):
        leather(r, DLEATH, base=2, seams=False, speckle=0.03)
        r.vshade(0.05, 0.08)
        # cipo enrolado em espiral (diagonal continua) com folhinhas
        for j in range(r.h):
            i = (j // 2 + (r.h - j) // 3) % r.w
            r.set(i, j, VINE[2])
            r.set((i + 1) % r.w, j, VINE[1])
            if j % 4 == 1:
                r.set((i + 2) % r.w, j, LEAF[3])

    M["druid_sleeve"] = sleeve

    def wrap(r):
        r.fill(VINE[1])
        for i in range(r.w):
            if i % 2:
                r.set(i, 0, VINE[3])
        r.row(-1, BARK[1])
        r.noise(0.04)

    M["druid_wrap"] = wrap

    def pants(r):
        leather(r, DLEATH, base=2, speckle=0.04)
        r.folds(DLEATH[1], DLEATH[3], 4)
        for j in range(r.h):
            i = (j * 2 + (j // 3)) % (r.w + 2) - 1
            r.set(i, j, VINE[2])
            r.set(i + 1, j, VINE[1])
            if j % 4 == 1:
                r.set(i + 2, j - 1, LEAF[3])
                r.set(i + 2, j, LEAF[2])

    M["druid_pants"] = pants

    def belt(r, front=False):
        leather(r, DLEATH, base=1, seams=False)
        for i in range(0, r.w, 2):
            r.set(i, 0, VINE[2])
        if front:
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, BARK[4]); r.set(cx, j, BARK[3])

    M["druid_belt"] = belt
    M["druid_belt_front"] = lambda r: belt(r, True)
    M["druid_pouch"] = lambda r: (leather(r, DLEATH, base=2), r.row(0, DLEATH[3]), r.set(r.w // 2, 1, BARK[4]))
    M["druid_root"] = lambda r: bark(r, BARK)
    M["druid_root_top"] = solid(BARK[3])

    def root_wrap(r):
        r.fill(BARK[2])
        for i in range(r.w):
            r.set(i, (i // 2) % r.h, BARK[3])
            if r.rnd.random() < 0.25:
                r.set(i, r.rnd.randrange(r.h), BARK[1])
        r.noise(0.04)

    M["druid_root_wrap"] = root_wrap
    return M


def druid_model(name="outfit_druid"):
    m = Model(name)
    skeleton(m)
    hd = "armorHead"
    m.box(hd, [-4.5, 28.6, -4.5], [9, 1.6, 9], fc(side="druid_circlet", u=OMIT, d=OMIT))
    m.box(hd, [-2.5, 29.4, -4.75], [5, 3.4, 0], fc(n="druid_leaf_cluster"))
    m.box(hd, [-4.75, 29.0, -3], [0, 3, 5.5], fc(e="druid_leaf_row"))
    m.box(hd, [4.75, 29.0, -3], [0, 3, 5.5], fc(w="druid_leaf_row"))
    m.box(hd, [-3, 29.0, 4.75], [6, 2.6, 0], fc(s="druid_leaf_row"))
    m.box(hd, [-3.5, 30.2, -5.1], [1, 1, 1], fc(all="druid_flower_pink@1x1"))
    m.box(hd, [2.4, 29.9, -5.1], [1, 1, 1], fc(all="druid_flower_yellow@1x1"))
    m.box(hd, [-5.1, 29.7, 1.6], [1, 1, 1], fc(all="druid_flower_white@1x1"))
    m.box(hd, [4.1, 30.0, 2.2], [1, 1, 1], fc(all="druid_flower_pink@1x1"))
    # galhada de cervo (lado direito, depois espelhada)
    n0 = len(m.by[hd]["cubes"])
    AF = fc(side="druid_antler", u="druid_antler_tip@1x1", d="druid_antler@1x1")
    TF = fc(side="druid_antler", u="druid_antler_tip@1x1", d="druid_antler@1x1")
    b1 = m.seg(hd, [-3.6, 29.6, 0.4], (-0.8, 1, 0.2), 4.0, 1.3, AF)
    mid1 = [(-3.6 + b1[0]) / 2, (29.6 + b1[1]) / 2, (0.4 + b1[2]) / 2]
    m.seg(hd, mid1, (-0.25, 1, -0.75), 2.6, 0.8, TF, overlap=0.4)
    b2 = m.seg(hd, b1, (-0.35, 1, 0.45), 3.4, 1.1, AF, overlap=0.5)
    m.seg(hd, b1, (-1, 0.7, -0.15), 2.2, 0.75, TF, overlap=0.4)
    m.seg(hd, b2, (-0.05, 1, 0.65), 2.4, 0.85, TF, overlap=0.45)
    m.seg(hd, b2, (-0.75, 0.9, -0.3), 2.0, 0.7, TF, overlap=0.4)
    m.box(hd, [-6.6, 30.4, -0.6], [2.4, 2.4, 0], fc(n="druid_leaf_small"), rot=[0, 0, 20], pivot=[-5, 30.4, -0.6])
    tmp = m.bone("tmpAnt", [0, 24, 0])
    m.by[tmp]["cubes"] = m.by[hd]["cubes"][n0:]
    m.mirror(tmp, hd)
    m.bones.remove(m.by[tmp])
    del m.by[tmp]

    bd = "armorBody"
    m.box(bd, [-4, 12, -2], [8, 12, 4], fc(n="druid_tunic_front", s="druid_tunic_back", ew="druid_tunic_side",
                                         u="druid_tunic_top", d="druid_dark@2x2"), inflate=0.55)
    m.box(bd, [-4, 17.6, -2], [8, 6.4, 4], fc(n="druid_mantle_front", s="druid_mantle", ew="druid_mantle",
                                            u="druid_mantle_top", d="druid_mantle_under@2x2"), inflate=1.0)
    for o, f in (([-3.6, 24.9, -1.6], "druid_flower_pink@1x1"), ([2.4, 24.9, 0.4], "druid_flower_yellow@1x1"),
                 ([1.6, 21.2, -3.45], "druid_flower_white@1x1"), ([-3.2, 19.4, -3.45], "druid_flower_yellow@1x1")):
        m.box(bd, o, [1, 1, 1], fc(all=f))
    m.bone("outfitCape", [0, 23.5, 3.05], parent=bd)
    m.box("outfitCape", [-4.5, 7, 3.05], [9, 16.5, 0.6], fc(s="druid_cape_out", n="druid_cape_in",
                                                          ew="druid_cape_edge@1x8", u="druid_cape_edge@2x1", d=OMIT))

    ra = "armorRightArm"
    m.box(ra, [-8, 12, -2], [4, 12, 4], fc(side="druid_sleeve", u=OMIT, d=OMIT), inflate=0.4)
    m.box(ra, [-8, 19.4, -2], [4, 4.6, 4], fc(side="druid_mantle", u="druid_mantle_top", d="druid_mantle_under@2x2"),
          inflate=0.9)
    m.box(ra, [-8, 12.3, -2], [4, 1.5, 4], fc(side="druid_wrap", u="druid_wrap@2x2", d="druid_wrap@2x2"),
          inflate=0.62)
    m.box(ra, [-7.2, 24.6, -0.4], [1, 1, 1], fc(all="druid_flower_pink@1x1"))
    m.mirror(ra, "armorLeftArm")

    waist(m, "Legs")
    m.box("waistLegs", [-4, 11.2, -2], [8, 1.3, 4], fc(n="druid_belt_front", s="druid_belt", ew="druid_belt",
                                                     u=OMIT, d=OMIT), inflate=0.75)
    m.box("waistLegs", [-5.9, 8.6, -1.2], [1.6, 2.6, 2.4], fc(side="druid_pouch", u="druid_pouch",
                                                            d="druid_dark@2x2"))
    rl = "armorRightLeg"
    m.box(rl, [-3.9, 0, -2], [4, 12, 4], fc(side="druid_pants", u=OMIT, d=OMIT), inflate=0.42)
    m.box(rl, [-4.45, 6.5, -1.6], [0, 3, 3.2], fc(e="druid_leaf_small"), rot=[0, 0, -12], pivot=[-4.45, 9.5, 0])
    m.mirror(rl, "armorLeftLeg")

    rb = "armorRightBoot"
    m.box(rb, [-4.15, -0.6, -2.7], [4.5, 1, 5.2], fc(side="druid_root", u="druid_root_top@2x2",
                                                   d="druid_root_top@2x2"))
    m.box(rb, [-3.9, 0.4, -2], [4, 1.4, 4], fc(side="druid_root_wrap", u=OMIT, d=OMIT), inflate=0.5)
    m.box(rb, [-3.9, 3.2, -2], [4, 1.2, 4], fc(side="druid_root_wrap", u=OMIT, d=OMIT), inflate=0.5)
    RF = fc(side="druid_root", u="druid_root_top@1x1", d="druid_root_top@1x1")
    m.seg(rb, [-1.4, 0.4, -2.55], (0.15, 1, -0.05), 3.2, 0.6, RF)
    m.seg(rb, [-3.2, 0.6, -2.5], (-0.25, 1, 0), 3.0, 0.55, RF)
    m.seg(rb, [-2.2, -0.2, -2.9], (0.3, 0.35, -1), 1.6, 0.6, RF)
    m.seg(rb, [-3.4, -0.2, -2.7], (-0.4, 0.3, -1), 1.3, 0.5, RF)
    m.box(rb, [-1.0, 3.6, -2.75], [2.4, 2.4, 0], fc(n="druid_leaf_small"), rot=[0, 0, -15], pivot=[-1, 3.6, -2.75])
    m.mirror(rb, "armorLeftBoot")
    return m


# =====================================================================================
#  NECROMANTE: capuz-cranio, manto esfarrapado cinza-escuro com costelas, saia esfarrapada, botas de osso
# =====================================================================================
GRAY = pal("0E0E12", "18181E", "24242C", "32323C", "454552")
BONE = pal("564F40", "817862", "ABA286", "D0C8AC", "EEE8D2")
SOUL = pal("146A3C", "2CCB78", "8CFFC0", "E0FFF0")
WRAPC = pal("1E1E24", "2C2C34", "3E3E48", "52525E")
NPURP = pal("1E1428", "30203E", "45305A")


def necro_mats(GRAY=GRAY, BONE=BONE, SOUL=SOUL, WRAPC=WRAPC, NPURP=NPURP):
    M = {}

    def hood(r, kind):
        cloth(r, GRAY, period=3, base=2, top=0.12, bottom=0.08)
        if kind == "front":
            for j in range(1, r.h):
                for i in range(1, r.w - 1):
                    r.clear(i, j)
            for i in range(1, r.w - 1):
                r.set(i, 1, GRAY[0])
            r.col(0, GRAY[3]); r.col(-1, GRAY[3])
        else:
            r.row(-1, GRAY[1])

    M["necro_hood_front"] = lambda r: hood(r, "front")
    M["necro_hood_back"] = lambda r: hood(r, "back")
    M["necro_hood_side"] = lambda r: hood(r, "side")
    M["necro_hood_top"] = lambda r: cloth(r, GRAY, period=3, base=2, folds=False)

    def skull_face(r):
        bone(r, BONE)
        r.fill(BONE[3])
        r.row(0, BONE[4])
        r.noise(0.03)
        cx = r.w // 2
        # orbitas escuras com brilho de alma
        for ox in (-3, 1):
            for j in (r.h - 4, r.h - 3):
                for i in (cx + ox, cx + ox + 1):
                    r.set(i, j, BONE[0])
            r.set(cx + ox - 1 if ox < 0 else cx + ox + 2, r.h - 4, BONE[1])
            r.glow(cx + ox + (1 if ox < 0 else 0), r.h - 3, SOUL[2])
        r.set(cx - 1, r.h - 1, BONE[0]); r.set(cx, r.h - 1, BONE[0])
        r.set(cx - 1, r.h - 2, BONE[1])
        # rachadura
        for (i, j) in ((cx + 2, 1), (cx + 3, 2), (cx + 3, 3)):
            r.set(i, j, BONE[1])

    M["necro_skull_face"] = skull_face

    def skull(r, kind):
        r.fill(BONE[3])
        r.vshade(0.10, 0.18)
        r.noise(0.03)
        if kind == "top":
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx + (1 if (j // 2) % 2 else 0), j, BONE[1])
        elif kind == "side":
            r.set(1, r.h - 2, BONE[1]); r.set(2, r.h - 2, BONE[1]); r.set(2, r.h - 3, BONE[0])
        elif kind == "back":
            r.row(-1, BONE[2])

    M["necro_skull_top"] = lambda r: skull(r, "top")
    M["necro_skull_side"] = lambda r: skull(r, "side")
    M["necro_skull_back"] = lambda r: skull(r, "back")
    M["necro_bone_dark"] = solid(BONE[1])
    M["necro_bone_flat"] = lambda r: (r.fill(BONE[3]), r.noise(0.03))

    def teeth(r):
        r.fill(BONE[3])
        for i in range(r.w):
            if i % 2 == 1:
                r.set(i, r.h - 1, BONE[0])
        r.row(0, BONE[4])

    M["necro_teeth"] = teeth

    def robe(r, kind):
        cloth(r, GRAY, period=4, base=2)
        cx = r.w // 2
        if kind == "front":
            for j in range(2, r.h - 2):
                r.set(cx - 1, j, GRAY[1])
        elif kind == "back":
            # coluna (vertebras) bordada
            for j in range(1, r.h - 1):
                r.set(cx - 1, j, BONE[2] if j % 2 else BONE[1])
                r.set(cx, j, BONE[3] if j % 2 else BONE[1])
        # remendos/rasgos
        for _ in range(2):
            i, j = r.rnd.randrange(r.w), r.rnd.randrange(2, max(3, r.h - 1))
            r.set(i, j, GRAY[0]); r.set(i, j + 1, GRAY[0])
        r.row(-1, NPURP[1])

    M["necro_robe_front"] = lambda r: robe(r, "front")
    M["necro_robe_back"] = lambda r: robe(r, "back")
    M["necro_robe_side"] = lambda r: robe(r, "side")
    M["necro_robe_top"] = lambda r: cloth(r, GRAY, base=3, folds=False)
    M["necro_dark"] = solid(GRAY[0])
    M["necro_bone_v"] = lambda r: bone(r, BONE)
    M["necro_bone_h"] = lambda r: bone(r, BONE, horizontal=True)

    def shawl(r):
        cloth(r, GRAY, period=3, base=3, top=0.10, bottom=0.04)
        r.rnd.seed(73 + r.w)
        r.ragged(2, sym=True, chance=0.6)

    M["necro_shawl"] = shawl

    def mini_skull(r):
        r.fill(BONE[3]); r.row(0, BONE[4])
        if r.w >= 3 and r.h >= 3:
            r.set(0, 1, BONE[0]); r.set(2, 1, BONE[0])
            r.glow(0, 1, SOUL[1]); r.glow(2, 1, SOUL[1])
            r.set(1, 2, BONE[1])
        r.noise(0.02)

    M["necro_mini_skull_face"] = mini_skull
    M["necro_mini_skull"] = lambda r: (r.fill(BONE[3]), r.row(0, BONE[4]), r.noise(0.03))

    def sleeve(r):
        cloth(r, GRAY, period=3, base=2, top=0.06, bottom=0.06)
        r.rnd.seed(99 + r.w * 7)
        r.ragged(4, sym=False, chance=0.7)

    M["necro_sleeve"] = sleeve

    def bone_ring(r):
        r.fill(BONE[3])
        for i in range(0, r.w, 2):
            r.col(i, BONE[2])
        r.row(0, BONE[4]) if r.h > 1 else None
        r.noise(0.03)

    M["necro_bone_ring"] = bone_ring

    def tskirt(r, kind):
        cloth(r, GRAY, period=3, base=2, top=0.04, bottom=0.10)
        for _ in range(3):
            i, j = r.rnd.randrange(r.w), r.rnd.randrange(1, max(2, r.h - 3))
            r.set(i, j, GRAY[0])
        if kind == "front" and r.h > 6:
            cx = r.w // 2
            r.set(cx - 1, 2, NPURP[2]); r.set(cx, 2, NPURP[2])
        r.rnd.seed(5150 + r.w)
        r.ragged(4, sym=True, chance=0.75)

    M["necro_skirt_front"] = lambda r: tskirt(r, "front")
    M["necro_skirt_back"] = lambda r: tskirt(r, "back")
    M["necro_skirt_side"] = lambda r: tskirt(r, "side")
    M["necro_skirt_edge"] = solid(GRAY[1])
    M["necro_lining"] = solid(GRAY[0])

    def wraps(r):
        r.fill(WRAPC[2])
        for j in range(r.h):
            for i in range(r.w):
                if (i + j * 2) % 5 == 0:
                    r.set(i, j, WRAPC[1])
                elif (i + j * 2) % 5 == 1:
                    r.set(i, j, WRAPC[3])
        r.noise(0.03)

    M["necro_wraps"] = wraps

    def boot(r):
        r.fill(GRAY[1])
        for j in range(r.h):
            if j % 2:
                r.row(j, GRAY[2])
        r.row(-1, GRAY[0])
        r.noise(0.03)

    M["necro_boot"] = boot
    M["necro_sole"] = solid(GRAY[0])
    M["necro_claw"] = lambda r: (r.fill(BONE[2]), r.row(0, BONE[3]), r.noise(0.03))
    return M


def necro_model(name="outfit_necromancer"):
    m = Model(name)
    skeleton(m)
    hd = "armorHead"
    m.box(hd, [-4, 24, -4], [8, 8, 8], fc(n="necro_hood_front", s="necro_hood_back", ew="necro_hood_side",
                                        u="necro_hood_top", d=OMIT), inflate=0.75)
    m.box(hd, [-5.1, 29.6, -5.4], [10.2, 5.4, 9.4], fc(n="necro_skull_face", s="necro_skull_back",
                                                     ew="necro_skull_side", u="necro_skull_top",
                                                     d="necro_bone_dark@2x2"))
    m.box(hd, [-3.6, 28.6, -5.7], [7.2, 1.2, 1.3], fc(n="necro_teeth", ew="necro_bone_flat@1x1",
                                                    u=OMIT, d="necro_bone_dark@2x1", s=OMIT))
    m.box(hd, [-5.3, 28.4, -5.5], [1.6, 1.6, 3.2], fc(all="necro_bone_flat@2x2"))
    m.box(hd, [3.7, 28.4, -5.5], [1.6, 1.6, 3.2], fc(all="necro_bone_flat@2x2"))

    bd = "armorBody"
    m.box(bd, [-4, 12, -2], [8, 12, 4], fc(n="necro_robe_front", s="necro_robe_back", ew="necro_robe_side",
                                         u="necro_robe_top", d="necro_dark@2x2"), inflate=0.55)
    m.box(bd, [-0.6, 15.2, -3.0], [1.2, 7.2, 0.5], fc(n="necro_bone_v", ew="necro_bone_flat@1x2",
                                                    u="necro_bone_flat@1x1", d="necro_bone_flat@1x1", s=OMIT))
    for y in (21.0, 19.4, 17.8, 16.2):
        m.box(bd, [-3.7, y, -2.95], [3.1, 0.8, 0.45], fc(n="necro_bone_h", ew="necro_bone_flat@1x1",
                                                       u="necro_bone_flat@2x1", d="necro_bone_flat@2x1", s=OMIT),
              rot=[0, 0, -12], pivot=[-0.6, y + 0.4, -2.7])
        m.box(bd, [0.6, y, -2.95], [3.1, 0.8, 0.45], fc(n="necro_bone_h", ew="necro_bone_flat@1x1",
                                                      u="necro_bone_flat@2x1", d="necro_bone_flat@2x1", s=OMIT),
              rot=[0, 0, 12], pivot=[0.6, y + 0.4, -2.7])
    m.box(bd, [-4, 21.5, -2], [8, 2.6, 4], fc(side="necro_shawl", u="necro_dark@2x2", d=OMIT), inflate=0.95)

    ra = "armorRightArm"
    m.box(ra, [-8.4, 12.5, -2.4], [4.8, 11.5, 4.8], fc(side="necro_sleeve", u="necro_hood_top@3x3", d=OMIT),
          inflate=0.35)
    m.box(ra, [-8, 13.4, -2], [4, 1, 4], fc(side="necro_bone_ring", u="necro_bone_flat@2x2",
                                          d="necro_bone_flat@2x2"), inflate=0.85)
    m.mirror(ra, "armorLeftArm")
    # escapula no ombro direito, cranio pequeno no ombro esquerdo
    m.box(ra, [-9.2, 23.2, -2.4], [4.6, 1, 4.8], fc(side="necro_bone_flat@2x1", u="necro_bone_h",
                                                  d="necro_bone_dark@2x2"), rot=[0, 0, -12], pivot=[-6.5, 23.7, 0])
    la = "armorLeftArm"
    m.box(la, [5.0, 24.6, -1.6], [3, 3, 3], fc(n="necro_mini_skull_face", s="necro_mini_skull",
                                             ew="necro_mini_skull", u="necro_mini_skull", d="necro_bone_dark@2x2"))
    m.box(la, [5.3, 24.0, -1.8], [2.4, 0.8, 2.2], fc(all="necro_bone_flat@2x1"))

    waist(m, "Legs")
    skirt(m, "Legs", 1.5, "necro_skirt_front", "necro_skirt_back", "necro_skirt_side", "necro_lining@4x4",
          "necro_skirt_edge@1x4", hem=None, flare=6)
    m.box("waistLegs", [-4, 11, -2], [8, 1.3, 4], fc(side="necro_bone_ring", u=OMIT, d=OMIT), inflate=0.8)
    for x, h in ((-2.9, 2.3), (2.2, 2.0)):
        m.box("legsSkirtFront", [x, 12 - 3.5 - h + 1.0, -3.7], [0.6, h, 0.6], fc(all="necro_bone_flat@1x2"),
              rot=[-5, 0, 0], pivot=[0, 12, -2.75])
    m.box("legsSkirtFront", [-0.8, 8.0, -3.95], [1.6, 1.6, 1.2], fc(n="necro_mini_skull_face",
                                                                  ew="necro_mini_skull", u="necro_mini_skull",
                                                                  d="necro_bone_dark@2x2", s=OMIT),
          rot=[-5, 0, 0], pivot=[0, 12, -2.75])
    rl = "armorRightLeg"
    m.box(rl, [-3.9, 0, -2], [4, 12, 4], fc(side="necro_wraps", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(rl, "armorLeftLeg")

    rb = "armorRightBoot"
    m.box(rb, [-3.9, 0, -2], [4, 5, 4], fc(side="necro_boot", u=OMIT, d="necro_sole@2x2"), inflate=0.5)
    m.box(rb, [-2.7, 1.4, -2.9], [1.6, 3.8, 0.6], fc(n="necro_bone_v", ew="necro_bone_flat@1x2",
                                                   u="necro_bone_flat@1x1", d="necro_bone_flat@1x1", s=OMIT))
    m.box(rb, [-3.1, 4.9, -3.05], [2.4, 1.0, 0.9], fc(all="necro_bone_flat@2x1", s=OMIT))
    m.box(rb, [-3.9, 4.6, -2], [4, 1, 4], fc(side="necro_bone_ring", u="necro_dark@2x2", d="necro_dark@2x2"),
          inflate=0.8)
    m.box(rb, [-3.65, -0.5, -3.0], [3.5, 1.6, 1.1], fc(n="necro_bone_h", ew="necro_bone_flat@1x2",
                                                     u="necro_bone_flat@3x1", d="necro_sole@2x2", s=OMIT))
    for x in (-3.55, -2.35, -1.15):
        m.box(rb, [x, -0.5, -4.3], [0.8, 0.8, 1.4], fc(all="necro_claw@1x1", s=OMIT), rot=[-15, 0, 0],
              pivot=[x + 0.4, -0.1, -3.0])
    m.mirror(rb, "armorLeftBoot")
    return m


# =====================================================================================
#  Texturas de reserva no formato vanilla (so usadas se o GeckoLib nao desenhar a peca)
# =====================================================================================
def vanilla_layers(cid, main, trim, dark):
    c1 = Canvas(64, 32)
    regions = [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]
    rnd = random.Random(len(cid))
    for (x0, y0, w, h) in regions:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = main if (x + y) % 5 else dark
                if rnd.random() < 0.08:
                    c = trim
                c1.set(x, y, c)
    for x in range(0, 64):
        c1.set(x, 31, trim)
    c1.save(os.path.join(A, "textures/models/armor", f"outfit_{cid}_layer_1.png"))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(A, "textures/models/armor", f"outfit_{cid}_layer_2.png"))


# =====================================================================================
#  Icones 16x16 (pixel art a mao: cada letra e uma cor da paleta do icone)
# =====================================================================================
ICONS = {}

ICONS["mage_helmet"] = ({
    "o": "0A0F26", "d": "162254", "m": "2A3F8E", "l": "4462BE", "s": "FFE07A", "y": "8A5E14", "g": "D4A43A",
    "G": "FFE38A"}, [
    "................",
    "..........ooo...",
    ".........oldo...",
    "........olmdo...",
    "........olsdo...",
    ".......olmmdo...",
    ".......olmmmdo..",
    "......olmmsmdo..",
    "......olsmmmdo..",
    ".....olmmmmmmdo.",
    ".....oygGGGggyo.",
    "..oollmmmmmmmddo",
    ".ollmmmsmmmmmmdo",
    ".oddmmmmmmmsdddo",
    "..oooddddddddoo.",
    "....oooooooooo..",
])

ICONS["mage_chestplate"] = ({
    "o": "0A0F26", "d": "162254", "m": "2A3F8E", "l": "4462BE", "y": "8A5E14", "g": "D4A43A", "G": "FFE38A",
    "v": "443384"}, [
    "................",
    "....oo....oo....",
    "..ooldovvodlo...",
    ".oldmmogGomddo..",
    "oldmmmlgGlmmddo.",
    "olmmo.lgGl.ommdo",
    "omdo..lgGl..odmo",
    "omgo..mgGm..ogmo",
    "oyGo..mgGm..oGyo",
    ".oo...ogGmo..oo.",
    "......omgGmo....",
    ".....odmgGmdo...",
    ".....odmgGmmdo..",
    "....oyggGGggyyo.",
    "....oooooooooooo",
    "................",
])

ICONS["mage_leggings"] = ({
    "o": "0A0F26", "d": "162254", "m": "2A3F8E", "l": "4462BE", "y": "8A5E14", "g": "D4A43A", "G": "FFE38A",
    "s": "FFE07A"}, [
    "................",
    "...oooooooooo...",
    "...oyggGGggyo...",
    "...olmmgGmmdo...",
    "...olmmgGmmdo...",
    "..olmmmgGmmmdo..",
    "..olmsmgGmmmdo..",
    "..olmmmgGmmsdo..",
    ".olmmmmgGmmmmdo.",
    ".olmmmmgGmmmmdo.",
    ".olmsmmgGmmmmdo.",
    "olmmmmmgGmmmmmdo",
    "olmmmmmgGmmmsmdo",
    "oyggGGggGGGggyyo",
    "oooooooooooooooo",
    "................",
])

ICONS["mage_boots"] = ({
    "o": "0A0F26", "d": "162254", "m": "2A3F8E", "l": "4462BE", "y": "8A5E14", "g": "D4A43A", "G": "FFE38A",
    "b": "33241A"}, [
    "................",
    "................",
    "..oooooo........",
    "..oGGggo........",
    "..olmmdo........",
    "..olmmdo........",
    "..olmmdo.oooooo.",
    "..olmmdo.oGGggo.",
    ".olmmmdo.olmmdo.",
    "olmmmmdo.olmmdo.",
    "olmmmmdoolmmmdoG",
    "oddmmmdoolmmmmdo",
    ".obbbboolmmmmmdo",
    "..oooo.oddmmmdo.",
    ".......obbbbbo..",
    "........ooooo...",
])

ICONS["witch_helmet"] = ({
    "o": "08060B", "d": "17121D", "m": "2A2234", "l": "403452", "p": "5E2C7C", "P": "8E52B8", "b": "C8C8D2",
    "B": "F2F2FA", "h": "5C8E3A"}, [
    "..ooo...........",
    ".olldo..........",
    "..oldo..........",
    "...olmo.........",
    "....olmo........",
    "....olmdo.......",
    ".....olmdo......",
    ".....olmmdo.....",
    "....olmmmmdo.h..",
    "....oppbBbppo.h.",
    "..ooplpbdbpmpoo.",
    ".ollmmmbbbmmmddo",
    "olmmmmmmmmmmmmdo",
    ".oddmmmmmmmmddo.",
    "..oooddddddooo..",
    ".....oooooo.....",
])

ICONS["witch_chestplate"] = ({
    "o": "120818", "d": "3E1A58", "m": "5E2C7C", "l": "8E52B8", "c": "F0EAD8", "k": "17121D", "s": "C8C8D2",
    "a": "D6CBA8", "A": "B5A784", "h": "5C8E3A", "g": "6CF080"}, [
    "................",
    "..ooo.oooo.ooo..",
    ".ollmoccccomllo.",
    "olllmocgccomllmo",
    "olmmdommmmodmmdo",
    ".oddookskskoddo.",
    ".occookskskooco.",
    "....okskskko....",
    "....oaaaaaao....",
    "...omahhaaAmo...",
    "...omaahaaAmo...",
    "..olmaaaaaAmdo..",
    "..olmaaaaAAmdo..",
    ".olmmAAAAAAmmdo.",
    ".occcccccccccco.",
    "..oooooooooooo..",
])

ICONS["witch_leggings"] = ({
    "o": "08060B", "p": "8E52B8", "q": "5E2C7C", "k": "17121D", "r": "D2C9B0"}, [
    "................",
    "...oooo..oooo...",
    "...orrro.orrro..",
    "...opppo.opppo..",
    "...okkko.okkko..",
    "...opppo.opppo..",
    "...okkko.okkko..",
    "...opppo.opppo..",
    "...okkko.okkko..",
    "...opppo.opppo..",
    "...okkko.okkko..",
    "...opppo.opppo..",
    "...okkko.okkko..",
    "...opqpo.opqpo..",
    "...okkko.okkko..",
    "...ooooo.ooooo..",
])

ICONS["witch_boots"] = ({
    "o": "08060B", "d": "17121D", "m": "2A2234", "l": "403452", "g": "E8B84A", "G": "FFE38A"}, [
    "................",
    "................",
    "................",
    "................",
    "....oooo........",
    "....olmo........",
    "....olmo...oooo.",
    "....olmo...olmo.",
    "...olmmdo..olmo.",
    "..olgGgmdoolmmo.",
    ".olmgdgmmdolmmdo",
    "olmmgggmmdolgGgo",
    "oddmmmmmddolgdgo",
    ".ooooooooooolggo",
    "...........odddo",
    "...........ooooo",
])

ICONS["warlock_helmet"] = ({
    "o": "050407", "d": "100E14", "m": "1E1A24", "l": "34303C", "h": "665240", "H": "A08C72", "W": "DCCFB4",
    "r": "E8392C", "R": "A81A1A", "s": "08070A"}, [
    "W..............W",
    "Ho............oH",
    "Hho..........ohH",
    ".hHo........oHh.",
    "..hho.oooo.ohh..",
    "...hhoddddohh...",
    "...odmlllmmdo...",
    "..odmlmmmmmmdo..",
    "..odmo....omdo..",
    ".odmo.sssso.omdo",
    ".odmo.s..so.omdo",
    ".odmo.s..so.omdo",
    ".odmmo....ommdo.",
    "..oRmmo..ommRo..",
    "...oRRrrrrRRo...",
    "....oooooooo....",
])

ICONS["warlock_chestplate"] = ({
    "o": "050407", "d": "100E14", "m": "1E1A24", "l": "34303C", "r": "E8392C", "R": "A81A1A", "i": "6C6C7A",
    "I": "989AAA"}, [
    "................",
    ".oiIo.oooo.oIio.",
    "oiIIioddddoiIIio",
    "oiiiomlmmmmoiiio",
    "odmmomrRRrmomdmo",
    "odmo.mRmmRm.omdo",
    "omdo.mrRRrm.odmo",
    "omRo.mmmmmm.oRmo",
    "oRro.mmrmmm.orRo",
    ".oo..mmRmmm..oo.",
    ".....oiIIio.....",
    ".....odmrmdo....",
    "....odmmRmmdo...",
    "....odmmmmmmdo..",
    "....oRrRrRrRro..",
    "....ooooooooooo.",
])

ICONS["warlock_leggings"] = ({
    "o": "050407", "d": "100E14", "m": "26222C", "l": "34303C", "R": "6E0D10", "r": "A81A1A", "e": "E8392C"}, [
    "................",
    "..oooooooooooo..",
    "..oRrrrrrrrrRo..",
    "..olmmmeemmmdo..",
    "..olmmoeeommdo..",
    "..olmmo..omddo..",
    "..oRRmo..oRRdo..",
    "..olmRo..olmRo..",
    "..olmmo..olmmo..",
    "..oRrRo..oRrRo..",
    "..olmmo..olmmo..",
    "..olRmo..olmRo..",
    "..oRrRo..oRrRo..",
    "..olmmo..olmmo..",
    "..odddo..odddo..",
    "..ooooo..ooooo..",
])

ICONS["warlock_boots"] = ({
    "o": "050407", "d": "1C130E", "m": "2E2018", "l": "422D22", "i": "4A4A56", "I": "989AAA", "r": "E8392C"}, [
    "................",
    "................",
    "...oooooo.......",
    "...oIIIIo.......",
    "...oliIdo.......",
    "...olIrdo.oooooo",
    "...oliIdo.oIIIIo",
    "...olIido.oliIdo",
    "..olmIIdo.olIrdo",
    ".olmmmmdo.oliIdo",
    "oiIIimmdo.olIido",
    "oiIIimddoolmIIdo",
    "oiiiidddooiIIimo",
    ".ooooooo.oiIIido",
    ".........oiiiido",
    "..........oooooo",
])

ICONS["druid_helmet"] = ({
    "o": "2A1E10", "a": "A1855E", "A": "C8AC86", "W": "E8D4B0", "b": "563A22", "B": "765434", "g": "3E7426",
    "G": "5E9E36", "L": "92CC62", "p": "D9669A", "P": "FFB2D2", "y": "EDC234"}, [
    ".A..........A...",
    ".aA.W....W.Aa...",
    "..aAa....aAa..W.",
    "W..aa.A.Aaa..aA.",
    "Aa.aaAa.aAa.aa..",
    ".aAaaa...aaaAa..",
    "...aaa...aaa....",
    "...oaa...aao....",
    "..GoLgG.GgLoG...",
    ".gLGgpPgLyGgLg..",
    ".gbBbBpbBbBbBg..",
    ".obBbBbBbBbBbo..",
    "..gGo.....oGg...",
    "...g.......g....",
    "................",
    "................",
])

ICONS["druid_chestplate"] = ({
    "o": "142A0C", "d": "28501A", "m": "3E7426", "l": "5E9E36", "L": "92CC62", "b": "573A1F", "B": "77542F",
    "p": "D9669A", "P": "FFB2D2", "y": "EDC234", "w": "FFFFFA", "v": "467E32"}, [
    "................",
    "...oo.oooo.oo...",
    "..olLodmmdoLlo..",
    ".olLmlmLpmlmLmo.",
    "olmymLmPmLmmlmdo",
    "odlmlmlmmLmlmdmo",
    "omLo.oBbbBo.oLmo",
    "omdo.oBbbBo.odmo",
    "oLmo.oBbbBo.omLo",
    ".oo..ovvvvo..oo.",
    ".....oBbbBo.....",
    "....oBbbbbBo....",
    "....oBbBbbBo....",
    "...oLmlmLlmLo...",
    "...omLdpmLdmo...",
    "....oooooooo....",
])

ICONS["druid_leggings"] = ({
    "o": "2A1A0C", "d": "573A1F", "m": "77542F", "l": "996F44", "v": "2F5E22", "V": "467E32", "L": "92CC62",
    "w": "38240F"}, [
    "................",
    "..oooooooooooo..",
    "..owwlwwwwlwwo..",
    "..olmmmVmmmmdo..",
    "..olmmVvmVmmdo..",
    "..olmVo..oVLdo..",
    "..olVmo..olVdo..",
    "..oLvmo..olmVo..",
    "..olVmo..oVvdo..",
    "..olmVo..oVmdo..",
    "..olmvL..olVdo..",
    "..olVmo..oVmdo..",
    "..oVmmo..olvLo..",
    "..olmdo..olmdo..",
    "..odddo..odddo..",
    "..ooooo..ooooo..",
])

ICONS["druid_boots"] = ({
    "o": "1A100A", "b": "3A2716", "B": "563A22", "r": "765434", "R": "9A764A", "g": "3E7426", "L": "92CC62"}, [
    "................",
    "................",
    "................",
    "...LgL..........",
    "...rRr..........",
    "...rbRr....LgL..",
    "...RrbR....rRr..",
    "...rRrr....rbRr.",
    "...rBRrr...RrbR.",
    "..RrrrRrr..rRrr.",
    ".rRRrrRrRR.rBRrr",
    "rrBBBBBBBRrRrrRr",
    "oooooooooorRBBBR",
    "..........oooooo",
    "................",
    "................",
])

ICONS["necromancer_helmet"] = ({
    "o": "101014", "d": "18181E", "m": "26262E", "l": "3A3A44", "b": "817862", "B": "ABA286", "W": "D0C8AC",
    "H": "EEE8D2", "e": "2CCB78", "E": "8CFFC0", "k": "2A261E"}, [
    "................",
    "....oooooooo....",
    "...oHHWWWWWBo...",
    "..oHWWWWWWWWBo..",
    "..oWWWWWBWWWBo..",
    "..oWkkWWWWkkBo..",
    "..oWkEkWWkEkBo..",
    "..oWWkWbbWkWBo..",
    "..odWWBkkBWWdo..",
    ".odmoWkWWkWomdo.",
    ".odmo......omdo.",
    ".odmo......omdo.",
    ".odmmo....ommdo.",
    "..odmmo..ommdo..",
    "...ommo..ommo...",
    "....oo....oo....",
])

ICONS["necromancer_chestplate"] = ({
    "o": "0A0A0E", "d": "18181E", "m": "26262E", "l": "3A3A44", "b": "817862", "B": "ABA286", "W": "D0C8AC",
    "H": "EEE8D2", "e": "2CCB78"}, [
    "................",
    ".oBWo.oooo.oWBo.",
    "oBWHWoddddoWHWBo",
    "oBWWoWWHWWWoWWBo",
    "odmmomllWllmomdo",
    "odmo.mWWHWWm.omdo"[:16],
    "omdo.mllWllm.odmo"[:16],
    "omdo.mWWHWWm.odmo"[:16],
    "omdo.mllWllm.odmo"[:16],
    "omdo.mmmWmmm.odmo"[:16],
    "o.do.mmmmmmm.od.o",
    ".o.o.mlmmmlm.o.o.",
    ".....mmmmmmm.....",
    ".....m.mm.mm.....",
    ".....o.mo.mo.....",
    "................",
])

ICONS["necromancer_leggings"] = ({
    "o": "0A0A0E", "d": "18181E", "m": "26262E", "l": "3A3A44", "B": "ABA286", "W": "D0C8AC", "H": "EEE8D2"}, [
    "................",
    "..oooooooooooo..",
    "..oWBWBWBWBWBo..",
    "..olmmmmmmmmdo..",
    "..olmmmWmmmmdo..",
    ".olmmmWHWmmmmdo.",
    ".olmmmWWWmmmmdo.",
    ".olmmmmmmmmmmdo.",
    "olmmmmmmmmmmmmdo",
    "olmmmBmmmmmBmmdo",
    "olmmmBmmmmmBmmdo",
    "olmmmmmmmmmmmmdo",
    "olm.mmmm.mmm.mdo",
    "om..mm.m..mm..do",
    "o...m..m...m...o",
    "................",
])

ICONS["necromancer_boots"] = ({
    "o": "0A0A0E", "d": "18181E", "m": "26262E", "l": "3A3A44", "B": "ABA286", "W": "D0C8AC", "H": "EEE8D2",
    "b": "817862"}, [
    "................",
    "................",
    "...oooooo.......",
    "...oWHWBo.......",
    "...olWmdo.......",
    "...olWmdo.oooooo",
    "...olHmdo.oWHWBo",
    "...olWmdo.olWmdo",
    "..olmWmdo.olWmdo",
    ".olmmBmdo.olHmdo",
    "oWWHWWmdo.olWmdo",
    "oWHWWWddoolmWmdo",
    "oBWBWBddooWWHWdo",
    "HbHbHbo..oWHWWdo",
    ".........oBWBWBo",
    "........HbHbHbo.",
])


def draw_icon(name, palette, rows):
    c = Canvas(16, 16)
    pal_ = {k: H(v) for k, v in palette.items()}
    for y, row in enumerate(rows[:16]):
        row = (row + "." * 16)[:16]
        for x, ch in enumerate(row):
            if ch != "." and ch in pal_:
                c.set(x, y, pal_[ch])
    c.save(os.path.join(A, "textures/item", name + ".png"))
    item_model(A, name)


# =====================================================================================
#  Receitas e tags
# =====================================================================================
SETS = {
    # classe: (la, acentos para costura, item tematico da reforma)
    "mage": ("minecraft:blue_wool", "minecraft:gold_nugget", "minecraft:lapis_lazuli"),
    "witch": ("minecraft:purple_wool", "minecraft:iron_nugget", "minecraft:fern"),
    "warlock": ("minecraft:black_wool", "minecraft:redstone", "minecraft:goat_horn"),
    "druid": ("minecraft:green_wool", "minecraft:oak_leaves", "minecraft:stick"),
    "necromancer": ("minecraft:gray_wool", "minecraft:bone", "minecraft:bone_meal"),
}
PIECES = ("helmet", "chestplate", "leggings", "boots")


def ing(x):
    return {"tag": x[1:]} if x.startswith("#") else {"item": x}


def unlock_advancement(recipe, item):
    """Conquista invisivel (padrao vanilla) que coloca a receita no livro quando o jogador pega 'item'."""
    rid = f"isekai_mahou:{recipe}"
    write_json(os.path.join(D, "advancement/recipes/equipment", recipe + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": rid}},
            "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": [rid]},
    })


def recipes():
    rdir = os.path.join(D, "recipe")
    shaped = {
        "mage": {
            "helmet": ([" A ", "WBW", "W W"], {"W": "minecraft:blue_wool", "A": "minecraft:gold_nugget",
                                             "B": "minecraft:lapis_lazuli"}),
            "chestplate": (["W W", "GBG", "WWW"], {"W": "minecraft:blue_wool", "G": "minecraft:gold_nugget",
                                                 "B": "minecraft:lapis_lazuli"}),
            "leggings": (["WGW", "W W", "W W"], {"W": "minecraft:blue_wool", "G": "minecraft:gold_nugget"}),
            "boots": (["W W", "L L"], {"W": "minecraft:blue_wool", "L": "minecraft:leather"}),
        },
        "witch": {
            "helmet": ([" W ", "WIW", "WWW"], {"W": "minecraft:black_wool", "I": "minecraft:iron_nugget"}),
            "chestplate": (["W W", "WSW", "FPF"], {"W": "minecraft:purple_wool", "S": "minecraft:string",
                                                 "F": "minecraft:fern", "P": "minecraft:white_wool"}),
            "leggings": (["PBP", "W W", "B B"], {"W": "minecraft:white_wool", "P": "minecraft:purple_wool",
                                               "B": "minecraft:black_wool"}),
            "boots": (["I I", "L L"], {"I": "minecraft:iron_nugget", "L": "minecraft:leather"}),
        },
        "warlock": {
            "helmet": (["HWH", "W W"], {"W": "minecraft:black_wool", "H": "minecraft:goat_horn"}),
            "chestplate": (["W W", "WRW", "WLW"], {"W": "minecraft:black_wool", "R": "minecraft:redstone",
                                                 "L": "minecraft:leather"}),
            "leggings": (["WRW", "W W", "C C"], {"W": "minecraft:black_wool", "R": "minecraft:redstone",
                                               "C": "minecraft:red_wool"}),
            "boots": (["I I", "L L"], {"I": "minecraft:iron_ingot", "L": "minecraft:leather"}),
        },
        "druid": {
            "helmet": (["S S", "LFL"], {"S": "minecraft:stick", "L": "#minecraft:leaves",
                                       "F": "#minecraft:small_flowers"}),
            "chestplate": (["L L", "WFW", "LLL"], {"L": "#minecraft:leaves", "W": "minecraft:green_wool",
                                                 "F": "#minecraft:small_flowers"}),
            "leggings": (["HVH", "H H", "H H"], {"H": "minecraft:leather", "V": "minecraft:vine"}),
            "boots": (["S S", "H H"], {"S": "minecraft:stick", "H": "minecraft:leather"}),
        },
        "necromancer": {
            "helmet": (["BWB", "W W"], {"W": "minecraft:gray_wool", "B": "minecraft:bone"}),
            "chestplate": (["W W", "BBB", "WWW"], {"W": "minecraft:gray_wool", "B": "minecraft:bone"}),
            "leggings": (["WBW", "W W", "W W"], {"W": "minecraft:gray_wool", "B": "minecraft:bone"}),
            "boots": (["W W", "B B"], {"W": "minecraft:gray_wool", "B": "minecraft:bone"}),
        },
    }
    for cid, pieces in {**shaped, **V9_SHAPED}.items():
        for piece, (pattern, key) in pieces.items():
            write_json(os.path.join(rdir, f"{cid}_{piece}.json"), {
                "type": "minecraft:crafting_shaped", "category": "equipment", "group": f"isekai_mahou:outfit_{piece}",
                "pattern": pattern, "key": {k: ing(v) for k, v in key.items()},
                "result": {"id": f"isekai_mahou:{cid}_{piece}", "count": 1}})
            # livro de receitas: libera ao pegar o material principal (o simbolo mais usado no padrao)
            main = max(key, key=lambda k: sum(row.count(k) for row in pattern))
            unlock_advancement(f"{cid}_{piece}", key[main])
    # Reforma: qualquer peca de roupa de classe + tecido + item tematico -> mesma peca de outra classe.
    # Tipo proprio (OutfitRetailorRecipe): a peca nova herda desgaste, encantamentos e nome da antiga.
    for cid, (wool, _accent, theme) in SETS.items():
        for piece in PIECES:
            write_json(os.path.join(rdir, f"{cid}_{piece}_retailor.json"), {
                "type": "isekai_mahou:outfit_retailor", "category": "equipment",
                "group": f"isekai_mahou:outfit_retailor_{piece}",
                "ingredients": [{"tag": f"isekai_mahou:outfits/{piece}"}, {"item": wool}, {"item": theme}],
                "result": {"id": f"isekai_mahou:{cid}_{piece}", "count": 1}})
            # quem tem qualquer roupa de classe (o kit inicial) ja ve as reformas no livro de receitas
            unlock_advancement(f"{cid}_{piece}_retailor", f"#isekai_mahou:outfits/{piece}")
    for piece in PIECES:
        write_json(os.path.join(D, "tags/item/outfits", piece + ".json"),
                   {"values": [f"isekai_mahou:{c}_{piece}" for c in SETS]})
    write_json(os.path.join(D, "tags/item/outfits/all.json"),
               {"values": [f"#isekai_mahou:outfits/{p}" for p in PIECES]})
    vanilla = {"helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor", "boots": "foot_armor"}
    for piece, tag in vanilla.items():
        write_json(os.path.join(MERGE, "data/minecraft/tags/item", tag + ".json"),
                   {"values": [f"isekai_mahou:{c}_{piece}" for c in SETS]})


# =====================================================================================
#  GRAUS II-IV (v0.7): Adepto (Circulo 3), Mestre (C5), Arquimago (C8)
#  Mesmo esqueleto e nomes de ossos do grau I (o renderer e o mesmo); a cada grau a paleta fica mais nobre e o
#  modelo ganha ornamentos: ombreiras, coroa/tiara, gemas que brilham, capa maior e runas no glowmask.
#  Arquivos: geo/item/armor/outfit_<classe>_<grau>.geo.json + textures/item/armor/outfit_<classe>_<grau>(_glowmask).png
# =====================================================================================
TIERS = ("adept", "master", "archmage")
TLV = {"adept": 1, "master": 2, "archmage": 3}


def mirror_new(m, src, dst, n0):
    """Espelha em 'dst' so os cubos de 'src' criados a partir do indice n0 (os ornamentos recem-adicionados)."""
    tmp = m.bone("tmpMirror", [0, 24, 0])
    m.by[tmp]["cubes"] = m.by[src]["cubes"][n0:]
    m.mirror(tmp, dst)
    m.bones.remove(m.by[tmp])
    del m.by[tmp]


def strip(m, bone, pred):
    """Remove cubos do modelo base que o grau substitui (ombreiras antigas, capinha curta...)."""
    m.by[bone]["cubes"] = [c for c in m.by[bone]["cubes"] if not pred(c)]


def gem(m, bone, center, size, mat="o_gem@2x2", rot=None, pivot=None):
    sx, sy, sz = (size, size, size) if isinstance(size, (int, float)) else size
    m.box(bone, [center[0] - sx / 2, center[1] - sy / 2, center[2] - sz / 2], [sx, sy, sz], fc(all=mat),
          rot=rot, pivot=pivot)


def seg_end(start, d, length):
    n = math.sqrt(sum(v * v for v in d))
    return [start[i] + d[i] / n * length for i in range(3)]


def ring(m, bone, x0, x1, z0, z1, y, h, t, side="o_crown", cap="o_crown_top"):
    """Aro retangular (coroa, tiara, cinto) em volta de [x0..x1] x [z0..z1], de y ate y+h, espessura t."""
    f = fc(side=side, u=cap + "@2x1", d=cap + "@2x1")
    m.box(bone, [x0 - t, y, z0 - t], [x1 - x0 + 2 * t, h, t], f)
    m.box(bone, [x0 - t, y, z1], [x1 - x0 + 2 * t, h, t], f)
    m.box(bone, [x0 - t, y, z0], [t, h, z1 - z0], f)
    m.box(bone, [x1, y, z0], [t, h, z1 - z0], f)


def crown_points(m, bone, x0, x1, z0, z1, y, t, xs, zs, w, h, mat="o_point", gem_mat=None, tall=None):
    """Pontas da coroa sobre o aro: xs nas faces frente/costas, zs nas laterais. Cada ponta = base + bico."""
    tall = tall or {}

    def point(o, size, axis_w, hh):
        m.box(bone, o, size, fc(side=mat, u=mat + "@1x1", d=OMIT))
        # bico mais fino em cima
        if axis_w == "x":
            m.box(bone, [o[0] + size[0] * 0.25, o[1] + size[1], o[2]], [size[0] * 0.5, hh * 0.55, size[2]],
                  fc(side=mat, u=mat + "@1x1", d=OMIT))
        else:
            m.box(bone, [o[0], o[1] + size[1], o[2] + size[2] * 0.25], [size[0], hh * 0.55, size[2] * 0.5],
                  fc(side=mat, u=mat + "@1x1", d=OMIT))

    for x in xs:
        hh = tall.get(("x", x), h)
        for z in (z0 - t, z1):
            point([x - w / 2, y, z], [w, hh, t], "x", hh)
            if gem_mat and ("x", x) in tall:
                gem(m, bone, [x, y + hh * 0.45, z + (-0.15 if z < 0 else t + 0.15)], 0.7, gem_mat)
    for z in zs:
        hh = tall.get(("z", z), h)
        for x in (x0 - t, x1):
            point([x, y, z - w / 2], [t, hh, w], "z", hh)


# Ombreiras: placas em cascata no ombro DIREITO (depois espelhadas). rz < 0 baixa a borda de fora.
PAULDRON = [([-9.3, 21.9, -3.0], [5.6, 2.6, 6.0], -14, [-6.5, 23.2, 0]),
            ([-9.9, 20.3, -3.2], [4.6, 2.2, 6.4], -26, [-7.2, 21.4, 0]),
            ([-10.3, 18.8, -3.3], [3.8, 1.9, 6.6], -38, [-7.8, 19.8, 0])]


def pauldrons(m, layers, side="o_paul_side", top="o_paul_top", under="o_dark@2x2", gem_mat=None, gem_size=1.4,
              fin=None, spikes=0, spike_mat="o_spike", spike_len=2.2, spike_tip=None, extra=None):
    ra = "armorRightArm"
    n0 = len(m.by[ra]["cubes"])
    for k in range(layers):
        o, s, rz, piv = PAULDRON[k]
        m.box(ra, o, s, fc(side=side, u=top if k == 0 else side, d=under), rot=[0, 0, rz], pivot=piv)
    o, s, rz, piv = PAULDRON[0]
    top_y = o[1] + s[1]
    if fin:
        m.box(ra, [-7.0, top_y - 0.1, -2.6], [0.9, 1.5, 5.2], fc(side=fin, u=fin + "@1x3", d=OMIT),
              rot=[0, 0, rz], pivot=piv)
    if gem_mat:
        gem(m, ra, [-6.4, top_y + 0.35, -1.6 if fin else 0.0], gem_size, gem_mat, rot=[0, 0, rz], pivot=piv)
    for k in range(spikes):
        z = (-1.9, 0.0, 1.9)[k] if spikes == 3 else (-1.2, 1.2)[k] if spikes == 2 else 0.0
        ln = spike_len * (1.15 if (spikes == 3 and k == 1) else 1.0)
        end = m.seg(ra, [-7.6, top_y - 0.2, z], (-0.5, 1, 0.05 * z), ln, 1.0 if k != 1 else 1.15,
                    fc(side=spike_mat, u=spike_mat + "@1x1", d=OMIT))
        if spike_tip:
            gem(m, ra, end, 0.55, spike_tip)
    if extra:
        extra(m, ra, top_y, rz, piv)
    mirror_new(m, ra, "armorLeftArm", n0)


def cape(m, top, bottom, z, width, out="o_cape_out", inn="o_cape_in", edge="o_cape_edge", flare=0.0, thick=0.6):
    """Capa nova (ou substitui a do grau I) no osso outfitCape, filho do tronco: o renderer a levanta ao correr."""
    if "outfitCape" in m.by:
        b = m.by["outfitCape"]
        b["cubes"] = []
        b["pivot"] = [0.0, float(top), float(z)]
    else:
        m.bone("outfitCape", [0, top, z], parent="armorBody")
        b = m.by["outfitCape"]
    if flare:
        b["rotation"] = [float(flare), 0.0, 0.0]
    m.box("outfitCape", [-width / 2, bottom, z], [width, top - bottom, thick],
          fc(s=out, n=inn, ew=edge + "@1x8", u=edge + "@2x1", d=OMIT))


# ------------------------------------------------------------------ materiais comuns dos ornamentos
def gem_mat(G):
    """Gema lapidada: tudo brilha (vai para o glowmask), borda mais escura e reflexo no canto."""
    def f(r):
        for j in range(r.h):
            for i in range(r.w):
                edge = (i in (0, r.w - 1) or j in (0, r.h - 1)) and r.w > 2 and r.h > 2
                r.glow(i, j, G[1] if edge else G[2])
        r.glow(0, 0, G[3])
    return f


def glow_fill(c, core=0.45):
    def f(r):
        for j in range(r.h):
            for i in range(r.w):
                edge = (i in (0, r.w - 1) or j in (0, r.h - 1)) and r.w > 2 and r.h > 2
                r.glow(i, j, c if edge else lighten(c, core))
    return f


def runed(fn, c, rows_from_bottom=3, step=4, start=1, seed=0):
    """Envolve um material: pinta o original e borda uma fileira de runas que brilham perto da barra."""
    def f(r):
        fn(r)
        j = r.h - rows_from_bottom - 4
        if j < 0 or r.w < 4:
            return
        for k, i in enumerate(range(start, r.w - 2, step)):
            rune_glyph(r, i, j, c, k + seed)
    return f


def notch(r, i):
    """Barra em pontas simetrica (frente e verso da capa casam)."""
    k = min(i, r.w - 1 - i)
    return 1 if abs((k % 4) - 1.5) <= 1 else 0


def orn_common(M, MT, GEM, GL):
    """Materiais compartilhados: gema, metais, coroa, pontas, runas, corrente, estrela."""
    M["o_gem"] = gem_mat(GEM)
    M["o_glow"] = glow_fill(GL)
    M["o_star"] = glow_fill(lighten(GL, 0.25), 0.6)
    M["o_metal"] = lambda r: metal(r, MT)
    M["o_metal_plain"] = lambda r: metal(r, MT, rivets=False)
    M["o_metal_flat"] = lambda r: (r.fill(MT[2]), r.row(0, MT[3]) if r.h > 1 else None, r.noise(0.03))
    M["o_dark"] = solid(darken(MT[0], 0.35))

    def crown(r):
        r.fill(MT[2])
        r.row(0, MT[4])
        if r.h > 1:
            r.row(-1, MT[1])
        if r.h >= 3:
            for i in range(r.w):
                r.set(i, r.h // 2, MT[3] if i % 3 == 1 else (MT[1] if i % 3 == 2 else MT[2]))
        r.noise(0.03)

    M["o_crown"] = crown
    M["o_crown_top"] = lambda r: (r.fill(MT[3]), r.noise(0.03))
    M["o_point"] = lambda r: (r.fill(MT[3]), r.col(0, MT[4]), r.col(-1, MT[2]) if r.w > 1 else None,
                              r.noise(0.03))

    def rune_band(r):
        r.fill(MT[1])
        r.row(0, MT[3])
        if r.h > 1:
            r.row(-1, MT[0])
        for k, i in enumerate(range(1, r.w - 1, 3)):
            r.glow(i, min(r.h - 1, max(0, r.h // 2)), GL if k % 2 else lighten(GL, 0.35))
        r.noise(0.02)

    M["o_rune_band"] = rune_band

    def chain(r):
        r.fill(T)
        for j in range(r.h):
            for i in range(r.w):
                link = (j // 2) % 2
                if (link == 0 and i in (0, r.w - 1)) or (link == 1 and 0 < i < r.w - 1) or j % 2 == 0:
                    r.set(i, j, MT[3] if (i + j) % 3 else MT[1])
        r.noise(0.03)

    M["o_chain"] = chain


def trimmed(P, TR, base=2, glow_studs=None, studs_c=None):
    """Tecido com debrum de metal (placas de ombreira de pano, gola, punho)."""
    def f(r):
        cloth(r, P, period=3, base=base, folds=False)
        r.row(0, TR[4])
        if r.h > 1:
            r.row(-1, TR[1])
        if r.h >= 4:
            r.row(-2, TR[2])
        if r.w > 3:
            r.col(0, TR[2]); r.col(-1, TR[2])
        for i in range(2, r.w - 2, 3):
            j = 1 if r.h > 3 else 0
            if glow_studs is not None:
                r.glow(i, j, glow_studs)
            elif studs_c is not None:
                r.set(i, j, studs_c)
    return f


def plate_top(MT, center, glow=False):
    """Topo de placa: metal com losango gravado e gema/runa no meio."""
    def f(r):
        metal(r, MT, rivets=False)
        cx, cy = r.w // 2, r.h // 2
        for (i, j) in ((cx, cy - 1), (cx - 1, cy), (cx + 1, cy), (cx, cy + 1)):
            r.set(i, j, MT[4])
        (r.glow if glow else r.set)(cx, cy, center)
    return f


def lining_hem(P, TR):
    def f(r):
        cloth(r, P, period=4, base=2, folds=False)
        for i in range(r.w):
            d = notch(r, i)
            for q in range(d):
                r.clear(i, r.h - 1 - q)
            r.set(i, r.h - 1 - d, TR[2])
    return f


def ragged_lining(P, seed):
    def f(r):
        cloth(r, P, period=4, base=1, folds=False)
        r.rnd.seed(seed)
        r.ragged(3, sym=True, chance=0.7)
    return f


# ------------------------------------------------------------------ MAGO: Safira (II), Purpura Real (III), Celestial (IV)
SILVER_M = pal("4A4E5C", "7A8090", "AEB4C4", "D8DEEA", "F6FAFF")
GOLD_R = pal("6A4508", "A06C12", "D49A26", "F5C84A", "FFF0A8")
GOLD_B = pal("7A5208", "B8860F", "E8B830", "FFD95A", "FFF6C8")
PAGES = pal("8A7E62", "B8AC8C", "DCD2B4", "F4ECD6")

MAGE_T = {
    "adept": dict(pal=dict(NAVY=pal("0E1F4A", "173275", "2147A3", "2F62CC", "5A8CF0"), GOLD=SILVER_M,
                           VIOLET=pal("0E2A3A", "164660", "20627E", "2E80A0"), MBROWN=MBROWN),
                  METAL=SILVER_M, GEM=pal("0A2A6A", "1E5ACC", "5AA0FF", "D8ECFF"), GLOW=H("7FC8FF")),
    "master": dict(pal=dict(NAVY=pal("1A0B2E", "2C1450", "43207A", "5C30A2", "8458CC"), GOLD=GOLD_R,
                            VIOLET=pal("3A0A16", "5E1224", "861C34", "A82A48"),
                            MBROWN=pal("1A0E10", "2C1A1C", "40282A", "563A3C")),
                   METAL=GOLD_R, GEM=pal("4A0612", "A0142C", "FF4A6A", "FFD0DA"), GLOW=H("FFD95A")),
    "archmage": dict(pal=dict(NAVY=pal("5A5670", "8C88A2", "C2BED4", "E2DFEC", "FFFFFF"), GOLD=GOLD_B,
                              VIOLET=pal("0E2E66", "1A4A9A", "2A68C8", "4A8CF0"),
                              MBROWN=pal("3A2E1C", "5A4A30", "7A6646", "9A8460")),
                     METAL=GOLD_B, GEM=pal("0A3A8A", "1E7AE8", "6AD0FF", "E0FAFF"), GLOW=H("8AE0FF")),
}


def mage_tier_mats(tier):
    T_ = MAGE_T[tier]
    lv = TLV[tier]
    P = T_["pal"]
    NAVY_, GOLD_, VIOLET_ = P["NAVY"], P["GOLD"], P["VIOLET"]
    MT, GEM, GL = T_["METAL"], T_["GEM"], T_["GLOW"]
    M = mage_mats(**P)
    orn_common(M, MT, GEM, GL)
    if lv >= 3:
        # runas azuis bordadas perto da barra da saia e nas costas do robe
        for k in ("mage_skirt_front", "mage_skirt_back", "mage_skirt_side"):
            M[k] = runed(M[k], GL, rows_from_bottom=3, seed=len(k))
        M["mage_robe_side"] = runed(M["mage_robe_side"], GL, rows_from_bottom=3, step=5)

    def cape_out(r):
        cloth(r, NAVY_, period=4, base=2, top=0.08, bottom=0.14)
        r.col(0, GOLD_[2]); r.col(-1, GOLD_[2])
        r.stars(max(2, r.w * r.h // 24), GOLD_[4], glow_core=GOLD_[4], arm=GOLD_[3], margin=2)
        sigil(r, r.w // 2, 4, GL if lv >= 2 else GOLD_[4])
        if lv >= 3:
            for k, i in enumerate(range(1, r.w - 2, 4)):
                rune_glyph(r, i, r.h - 9, GL, k)
        for i in range(r.w):
            d = notch(r, i)
            for q in range(d):
                r.clear(i, r.h - 1 - q)
            r.set(i, r.h - 1 - d, GOLD_[2]); r.set(i, r.h - 2 - d, GOLD_[3])

    M["o_cape_out"] = cape_out
    M["o_cape_in"] = lining_hem(VIOLET_, GOLD_)
    M["o_cape_edge"] = lambda r: (r.fill(GOLD_[2]), r.noise(0.03))
    M["o_paul_side"] = trimmed(NAVY_, MT, base=3, glow_studs=GL if lv >= 3 else None, studs_c=GOLD_[3])
    M["o_paul_top"] = plate_top(MT, GEM[2], glow=True)
    M["o_fin"] = lambda r: (r.fill(MT[3]), r.row(0, MT[4]), r.noise(0.03))

    def book_cover(r):
        r.fill(VIOLET_[1] if lv < 3 else NAVY_[3])
        r.row(0, MT[3]); r.row(-1, MT[1]); r.col(0, MT[2]); r.col(-1, MT[2])
        r.glow(r.w // 2, r.h // 2, GL)

    M["o_book_cover"] = book_cover
    M["o_book_pages"] = lambda r: ([r.row(j, PAGES[2] if j % 2 else PAGES[3]) for j in range(r.h)], r.noise(0.02))

    def wing(r):
        shape = [".WWWW.", "WWGGWW", ".WGGW.", "..WW.."]
        for j, row in enumerate(shape):
            for i, ch in enumerate(row):
                if ch == "W":
                    r.set(i, j, GOLD_[4] if j == 0 else GOLD_[3])
                elif ch == "G":
                    r.glow(i, j, GL)

    M["o_wing"] = wing
    return M


def mage_tier_model(tier):
    lv = TLV[tier]
    m = mage_model(f"outfit_mage_{tier}")
    hd = "armorHead"
    # ---- chapeu: gema na faixa, tiara (II) ou coroa com pontas (III/IV)
    if lv == 1:
        ring(m, hd, -4.5, 4.5, -4.5, 4.5, 30.0, 0.7, 0.3)
        gem(m, hd, [0, 31.8, -4.8], [1.5, 1.5, 0.6])
    else:
        h = 1.3 if lv == 2 else 1.7
        ring(m, hd, -4.5, 4.5, -4.5, 4.5, 30.0, h, 0.45)
        xs = (-3.0, 0.0, 3.0) if lv == 2 else (-3.4, -1.7, 0.0, 1.7, 3.4)
        zs = (0.0,) if lv == 2 else (-2.4, 0.0, 2.4)
        tall = {("x", 0.0): 2.4} if lv == 2 else {("x", 0.0): 2.9, ("x", -3.4): 2.0, ("x", 3.4): 2.0}
        crown_points(m, hd, -4.5, 4.5, -4.5, 4.5, 30.0 + h, 0.45, xs, zs, 0.9, 1.3, tall=tall,
                     gem_mat="o_gem@1x1" if lv == 3 else None)
        gem(m, hd, [0, 30.0 + h / 2, -5.15], [1.7, 1.5, 0.6])
    if lv == 3:
        # estrela na ponta do chapeu e runas orbitando a parte do meio
        sx, sy, sz = 0.0, 45.9, 2.1
        m.box("hatTip", [sx - 1.1, sy - 0.22, sz - 0.22], [2.2, 0.44, 0.44], fc(all="o_star@1x1"))
        m.box("hatTip", [sx - 0.22, sy - 1.1, sz - 0.22], [0.44, 2.2, 0.44], fc(all="o_star@1x1"))
        m.box("hatTip", [sx - 0.22, sy - 0.22, sz - 1.1], [0.44, 0.44, 2.2], fc(all="o_star@1x1"))
        gem(m, "hatTip", [sx, sy, sz], 0.8, "o_star@1x1")
        for k in range(8):
            a = k * math.pi / 4
            gem(m, "hatMid", [5.4 * math.cos(a), 35.2 + (0.5 if k % 2 else 0.0), 5.4 * math.sin(a)], 0.7,
                "o_glow@1x1")

    # ---- tronco: gema no cinto, capa longa, ombreiras
    bd = "armorBody"
    gem(m, bd, [0, 13.0, -3.2], [1.4, 1.4, 0.5])
    cape(m, 24.0, {1: 7.0, 2: 4.0, 3: 1.5}[lv], 2.9, 9.4, flare=5)
    pauldrons(m, lv, gem_mat="o_gem@2x2", gem_size=1.2 + 0.2 * lv, fin="o_fin" if lv == 3 else None)
    if lv >= 2:
        # punhos de metal acima da manga em sino
        ra = "armorRightArm"
        n0 = len(m.by[ra]["cubes"])
        m.box(ra, [-8, 18.0, -2], [4, 0.9, 4], fc(side="o_rune_band" if lv == 3 else "o_metal_flat",
                                                 u="o_metal_flat@2x2", d="o_metal_flat@2x2"), inflate=0.72)
        mirror_new(m, ra, "armorLeftArm", n0)
        # grimorio pendurado no quadril direito
        m.box("waistLegs", [-7.2, 7.2, -1.4], [1.2, 3.2, 2.6], fc(ew="o_book_cover", ns="o_book_pages",
                                                                  u="o_book_pages", d="o_book_pages"))
        m.box("waistLegs", [-6.75, 10.4, -0.15], [0.3, 1.8, 0.3], fc(all="o_metal_flat@1x2"))
    if lv == 3:
        rb = "armorRightBoot"
        n0 = len(m.by[rb]["cubes"])
        m.box(rb, [-4.85, 2.8, -0.9], [0, 2.4, 3.2], fc(ew="o_wing@6x4"))
        mirror_new(m, rb, "armorLeftBoot", n0)
    return m


# ------------------------------------------------------------------ BRUXA: Lunar (II), Esmeralda (III), Rainha Bruxa (IV)
WITCH_T = {
    "adept": dict(pal=dict(WHAT=pal("0A0E1C", "121A30", "1C2846", "283A60", "3A5080"),
                           PURP=pal("1E1650", "2E2478", "40349E", "5848C4", "7E70E0"),
                           LACE=pal("7A7C88", "A8AAB8", "D0D2DE", "EEF0F8"),
                           APRON=pal("4A4E66", "6A6E8A", "9296B0", "B8BCD2"), HERB=HERB, SILVER=SILVER,
                           POTION=pal("1A3A8A", "2A62D0", "6AA8FF", "D0E6FF"), WBROWN=WBROWN, GOLD=SILVER_M,
                           STRIPE=H("8FA8E0")),
                  METAL=SILVER, GEM=pal("1A2A6A", "3A5ACC", "8AB0FF", "E0ECFF"), GLOW=H("B8C8FF")),
    "master": dict(pal=dict(WHAT=pal("080A08", "101410", "1A201A", "262E26", "343E34"),
                            PURP=pal("0A2A1A", "124428", "1C6438", "2A8A4E", "4CB870"),
                            LACE=pal("8A7040", "B89A5A", "E0C47A", "F6E2A8"),
                            APRON=pal("2A2A22", "3E3E32", "56564A", "706E60"),
                            HERB=pal("1A3A1A", "2A5A2A", "3E7E3E", "5AA65A"), SILVER=GOLD_R,
                            POTION=pal("0E6A3A", "22B060", "6AF0A0", "D0FFE0"), WBROWN=WBROWN, GOLD=GOLD_R,
                            STRIPE=H("3CA060")),
                   METAL=GOLD_R, GEM=pal("0A4A2A", "1A9A50", "5AF09A", "D0FFE4"), GLOW=H("6AF0A0")),
    "archmage": dict(pal=dict(WHAT=pal("0E0612", "1A0A22", "2A1236", "3C1C4C", "522866"),
                              PURP=pal("3A1060", "5A1A8A", "7E2AB8", "A24EDC", "C888FF"),
                              LACE=pal("8A6A2A", "C09A40", "ECCB6A", "FFF0B8"),
                              APRON=pal("4A2A5A", "6A3E80", "8E58A8", "B07ACC"),
                              HERB=pal("3A1A4A", "5A2A6A", "8A4A9A", "C080D0"), SILVER=GOLD_B,
                              POTION=pal("8A1A6A", "D02A9A", "FF6AD0", "FFD0F0"),
                              WBROWN=pal("2A1A22", "42283A", "5E3A52", "7A506A"), GOLD=GOLD_B, STRIPE=H("E0A0FF")),
                     METAL=GOLD_B, GEM=pal("5A0A6A", "A02AC0", "E88AFF", "FAE0FF"), GLOW=H("FF6AD0")),
}


def moon_mat(MT, GL):
    """Lua crescente recortada (plano), prateada/dourada e brilhando."""
    shape = ["..MMM.", ".MM...", "MM....", "MM....", "MM....", ".MM...", "..MMM."]

    def f(r):
        for j, row in enumerate(shape):
            for i, ch in enumerate(row):
                if ch == "M":
                    r.glow(i, j, lighten(GL, 0.35) if i <= 1 else MT[4])
    return f


def witch_tier_mats(tier):
    T_ = WITCH_T[tier]
    lv = TLV[tier]
    P = T_["pal"]
    WHAT_, PURP_, LACE_ = P["WHAT"], P["PURP"], P["LACE"]
    MT, GEM, GL = T_["METAL"], T_["GEM"], T_["GLOW"]
    M = witch_mats(**P)
    orn_common(M, MT, GEM, GL)
    if lv >= 3:
        for k in ("witch_skirt_front", "witch_skirt_back", "witch_skirt_side"):
            M[k] = runed(M[k], GL, rows_from_bottom=3, seed=len(k))
    M["o_moon"] = moon_mat(MT, GL)
    M["o_paul_side"] = trimmed(PURP_, MT, base=2, glow_studs=GL if lv >= 3 else None, studs_c=LACE_[3])
    M["o_paul_top"] = plate_top(MT, GEM[2], glow=True)

    def lace_ruffle(r):
        r.fill(LACE_[2])
        r.row(0, LACE_[3])
        for i in range(r.w):
            if i % 2:
                r.set(i, r.h - 1, LACE_[1])
        r.noise(0.02)

    M["o_lace"] = lace_ruffle

    def feather(r):
        r.fill(T)
        for j in range(r.h):
            for i in range(r.w):
                if abs(i - (r.w - 1) / 2) <= (r.w / 2) * (1 - abs(j - r.h * 0.55) / (r.h * 0.6)):
                    r.set(i, j, WHAT_[3] if (i + j) % 3 else PURP_[2])
        for j in range(r.h):
            r.set(r.w // 2, j, MT[3])

    M["o_feather"] = feather

    def cape_out(r):
        cloth(r, PURP_, period=4, base=2, top=0.06, bottom=0.14)
        r.col(0, MT[2]); r.col(-1, MT[2])
        cx = r.w // 2
        # lua e estrelinhas bordadas
        for (i, j) in ((cx - 1, 2), (cx - 2, 3), (cx - 2, 4), (cx - 2, 5), (cx - 1, 6)):
            r.glow(i, j, lighten(GL, 0.3))
        r.stars(max(2, r.w * r.h // 30), MT[4], glow_core=MT[4], margin=2)
        if lv >= 3:
            for k, i in enumerate(range(1, r.w - 2, 4)):
                rune_glyph(r, i, r.h - 7, GL, k + 1)
        r.row(-1, LACE_[2])
        for i in range(0, r.w, 2):
            r.set(i, r.h - 1, LACE_[3])
        r.row(-2, MT[2])

    M["o_cape_out"] = cape_out
    M["o_cape_in"] = lambda r: (cloth(r, WHAT_, period=4, base=2, folds=False), r.row(-1, LACE_[2]))
    M["o_cape_edge"] = lambda r: (r.fill(MT[2]), r.noise(0.03))
    return M


def witch_tier_model(tier):
    lv = TLV[tier]
    m = witch_model(f"outfit_witch_{tier}")
    hd = "armorHead"
    gem(m, hd, [0, 30.7, -5.2], [1.2, 1.2, 0.5])
    if lv == 1:
        # lua crescente presa do lado direito do chapeu
        m.box(hd, [-4.62, 31.0, -1.6], [0, 3.5, 3.0], fc(ew="o_moon@6x7"))
    if lv >= 2:
        ring(m, hd, -4.5, 4.5, -4.5, 4.5, 29.8, 0.9 if lv == 2 else 1.4, 0.35)
        for x in (-4.95, 4.95):
            gem(m, hd, [x, 30.4 if lv == 2 else 30.6, 0], [0.5, 1.0, 1.0])
        # penas de corvo no lado direito
        m.box(hd, [-4.72, 31.0, 0.4], [0, 5.0, 1.8], fc(ew="o_feather@3x8"), rot=[0, 0, -18], pivot=[-4.72, 31.0, 1.3])
        m.box(hd, [-4.72, 30.8, 1.6], [0, 4.2, 1.5], fc(ew="o_feather@3x8"), rot=[-14, 0, -26], pivot=[-4.72, 30.8, 2.3])
    if lv == 3:
        crown_points(m, hd, -4.5, 4.5, -4.5, 4.5, 31.2, 0.35, (-3.0, -1.0, 1.0, 3.0), (-2.0, 2.0), 0.8, 1.6,
                     tall={("x", -1.0): 2.2, ("x", 1.0): 2.2}, gem_mat="o_gem@1x1")
        # luazinha pendurada na ponta torta do chapeu
        m.box("hatTip", [-1.2, 45.4, -0.1], [2.4, 2.8, 0], fc(ns="o_moon@6x7"))

    bd = "armorBody"
    gem(m, bd, [0, 22.6, -2.78], [1.3, 1.3, 0.5])
    # segundo frasco atras do primeiro (pocao na cor do grau)
    m.box("waistChest", [-5.9, 9.0, 1.0], [1.4, 2.0, 1.4], fc(side="witch_vial", u="witch_cork@2x2", d="witch_cork@2x2"))
    m.box("waistChest", [-5.6, 11.0, 1.2], [0.8, 0.7, 0.8], fc(all="witch_cork@1x1"))
    if lv == 1:
        pauldrons(m, 1, top="o_lace", under="witch_dark@2x2")
    else:
        pauldrons(m, lv, gem_mat="o_gem@2x2", gem_size=1.2 + 0.2 * lv)
        cape(m, 24.0, 9.0 if lv == 2 else 1.5, 3.2, 9.4, flare=8)
    return m


# ------------------------------------------------------------------ WARLOCK: Carmesim (II), Pacto Dourado (III), Infernal (IV)
STEEL = pal("141418", "26262E", "3A3A46", "5A5A6A", "8A8CA0")
WARLOCK_T = {
    "adept": dict(pal=dict(BLK=pal("12050A", "1E0A10", "2C1018", "3C1822", "4E2230"),
                           RED=pal("4A0610", "7E0E1C", "B81E2A", "F0443A", "FF9A80"), HORN=HORN, IRON=STEEL,
                           WLEATH=WLEATH),
                  METAL=STEEL, GEM=pal("4A0408", "A01020", "FF4A3A", "FFC8B8"), GLOW=H("FF4A3A")),
    "master": dict(pal=dict(BLK=BLK, RED=pal("4A0610", "7E0E1C", "B81E2A", "F0443A", "FF9A80"),
                            HORN=pal("120E14", "241C28", "3A3040", "5A4C64", "8A7C9A"),
                            IRON=pal("3A2606", "6A4810", "9A6E1C", "CA9A30", "F0D070"),
                            WLEATH=pal("1A0E0E", "2C1818", "3E2222", "52302E")),
                   METAL=pal("3A2606", "6A4810", "9A6E1C", "CA9A30", "F0D070"),
                   GEM=pal("4A0408", "A01020", "FF4A3A", "FFC8B8"), GLOW=H("F0D070")),
    "archmage": dict(pal=dict(BLK=pal("08050C", "120A18", "1C1026", "281838", "3A2450"),
                              RED=pal("4A0E02", "8A2206", "D04A0A", "FF8A1E", "FFD27A"),
                              HORN=pal("0E0A08", "1E1612", "3A2C20", "6A5030", "C8A050"),
                              IRON=pal("4A3008", "7A5612", "B0821E", "E0B440", "FFE890"),
                              WLEATH=pal("140A10", "22121A", "321A26", "442434")),
                     METAL=pal("4A3008", "7A5612", "B0821E", "E0B440", "FFE890"),
                     GEM=pal("5A1A02", "C04A08", "FF9A2A", "FFE8A0"), GLOW=H("FF8A1E")),
}
WARLOCK_HORN = [((-1.0, 0.42, 0.08), 3.0, 2.3), ((-0.55, 1.0, 0.2), 2.8, 1.9), ((-0.12, 1.0, 0.6), 2.4, 1.45),
                ((0.12, 0.6, 1.0), 2.0, 1.0)]


def flame_mat(R):
    """Chama/espinho que brilha: base escura, meio laranja, ponta clara."""
    def f(r):
        for j in range(r.h):
            t = j / max(1, r.h - 1)
            c = R[4] if t < 0.3 else (R[3] if t < 0.65 else R[2])
            for i in range(r.w):
                r.glow(i, j, c)
    return f


def warlock_tier_mats(tier):
    T_ = WARLOCK_T[tier]
    lv = TLV[tier]
    P = T_["pal"]
    BLK_, RED_, IRON_ = P["BLK"], P["RED"], P["IRON"]
    MT, GEM, GL = T_["METAL"], T_["GEM"], T_["GLOW"]
    M = warlock_mats(**P)
    orn_common(M, MT, GEM, GL)

    def paul_side(r):
        metal(r, MT, rivets=True)
        if r.w >= 4 and r.h >= 2:
            for i in range(1, r.w - 1):
                r.set(i, r.h - 1, RED_[1])
            cx = r.w // 2
            r.glow(cx - 1, r.h // 2, RED_[3]); r.glow(cx, r.h // 2, RED_[2] if lv < 3 else RED_[4])

    M["o_paul_side"] = paul_side

    def paul_top(r):
        metal(r, MT, rivets=False)
        cx, cy = r.w // 2, r.h // 2
        sigil(r, cx, cy, RED_[3] if lv < 3 else RED_[4], glow=True) if r.w >= 5 and r.h >= 5 else \
            r.glow(cx, cy, RED_[3])

    M["o_paul_top"] = paul_top
    M["o_spike"] = lambda r: (r.fill(MT[3]), r.row(0, MT[4]), r.col(0, MT[4]), r.noise(0.03))
    M["o_flame"] = flame_mat(RED_)
    M["o_fin"] = M["o_spike"]

    def eye(r):
        for j in range(r.h):
            for i in range(r.w):
                r.glow(i, j, RED_[3])
        r.glow(r.w // 2, r.h // 2, H("1A0A04"))
        r.glow(0, 0, RED_[4])

    M["o_eye"] = eye

    def cape_out(r):
        cloth(r, BLK_, period=4, base=2, top=0.05, bottom=0.10)
        r.col(0, MT[2] if lv >= 2 else RED_[1]); r.col(-1, MT[2] if lv >= 2 else RED_[1])
        cx = r.w // 2
        sigil(r, cx - 1 + (r.w % 2), 5, RED_[3] if lv < 3 else RED_[4])
        for k, j in enumerate(range(10, r.h - 5, 5)):
            rune_glyph(r, cx - 1, j, RED_[2] if lv < 3 else RED_[3], k)
        if lv >= 3:
            for j in range(2, r.h - 4, 3):
                r.glow(1, j, RED_[3]); r.glow(r.w - 2, j, RED_[3])
        r.row(0, BLK_[4])
        r.rnd.seed(777 + r.w)
        r.ragged(3, sym=True, chance=0.7)

    M["o_cape_out"] = cape_out
    M["o_cape_in"] = ragged_lining(RED_, 777 + 9)
    M["o_cape_edge"] = lambda r: (r.fill(BLK_[3]), r.noise(0.03))
    return M


def warlock_tier_model(tier):
    lv = TLV[tier]
    m = warlock_model(f"outfit_warlock_{tier}")
    hd = "armorHead"
    # ombreiras do grau I saem (placa + espinhos acima do cotovelo) e entram as novas
    for arm in ("armorRightArm", "armorLeftArm"):
        strip(m, arm, lambda c: c["origin"][1] > 20.0 or (c["pivot"] is not None and c["pivot"][1] > 20.0))
    if lv == 1:
        gem(m, hd, [0, 32.2, -4.95], [1.3, 1.3, 0.5])
    else:
        ring(m, hd, -4.8, 4.8, -4.8, 4.8, 31.0, 0.9, 0.3)
        if lv == 3:
            gem(m, hd, [0, 31.45, -5.35], [1.9, 1.1, 0.5], "o_eye@3x2")
        else:
            gem(m, hd, [0, 31.45, -5.3], [1.4, 1.4, 0.5])
        # aneis de metal na base dos chifres
        n0 = len(m.by[hd]["cubes"])
        p0, (d0, _, _) = [-4.0, 29.6, -0.2], WARLOCK_HORN[0]
        m.seg(hd, seg_end(p0, d0, 0.9), d0, 0.75, 2.75, fc(side="o_crown", u="o_crown_top@2x2", d="o_crown_top@2x2"))
        if lv == 3:
            p = p0
            for d, ln, _t in WARLOCK_HORN:
                p = seg_end(p, d, ln)
            # chifres mais longos que sobem e terminam numa brasa
            p = m.seg(hd, p, (0.2, 0.9, 0.6), 1.8, 0.75, fc(side="warlock_horn3", u="warlock_horn3@1x1",
                                                             d="warlock_horn3@1x1"), overlap=0.4)
            gem(m, hd, p, 0.7, "o_flame@1x2")
        mirror_new(m, hd, hd, n0)
    if lv == 3:
        # coroa de chamas entre os chifres
        for x, hgt in ((-2.0, 1.4), (-1.0, 2.1), (0.0, 2.8), (1.0, 2.1), (2.0, 1.4)):
            m.box(hd, [x - 0.35, 32.6, -2.6], [0.7, hgt, 0.7], fc(side="o_flame@1x3", u="o_flame@1x1", d=OMIT))

    bd = "armorBody"
    cape(m, 24.0, {1: 7.0, 2: 4.0, 3: 1.5}[lv], 2.9, 9.4, flare=6)
    pauldrons(m, min(3, lv + 1), gem_mat="o_gem@2x2" if lv >= 2 else None, gem_size=1.5,
              fin="o_fin" if lv == 3 else None, spikes=2 if lv == 1 else 3,
              spike_len=2.2 + 0.5 * (lv - 1), spike_tip="o_flame@1x1" if lv == 3 else None)
    if lv >= 2:
        # corrente atravessando o peito (ombro direito -> quadril esquerdo)
        m.box(bd, [-0.4, 11.95, -3.3], [0.8, 12.6, 0.4], fc(ns="o_chain@2x12", ew="o_metal_flat@1x4", u=OMIT, d=OMIT),
              rot=[0, 0, -33.7], pivot=[0, 18.25, -3.1])
        ra = "armorRightArm"
        n0 = len(m.by[ra]["cubes"])
        m.box(ra, [-8, 15.6, -2], [4, 1.2, 4], fc(side="o_rune_band", u="o_metal_flat@2x2", d="o_metal_flat@2x2"),
              inflate=0.8)
        mirror_new(m, ra, "armorLeftArm", n0)
    if lv == 3:
        rb = "armorRightBoot"
        n0 = len(m.by[rb]["cubes"])
        m.seg(rb, [-1.9, 6.0, -3.0], (0, 0.55, -1), 1.6, 0.8, fc(side="o_spike", u="o_spike@1x1", d=OMIT))
        mirror_new(m, rb, "armorLeftBoot", n0)
    return m


# ------------------------------------------------------------------ DRUIDA: Primavera (II), Outono (III), Bosque Eterno (IV)
DRUID_T = {
    "adept": dict(pal=dict(LEAF=pal("1E4A14", "347A22", "4EA832", "78D048", "B4F07A"), BARK=BARK,
                           DLEATH=pal("4A3016", "6E4C26", "926A3A", "B48A52"),
                           ANTLER=ANTLER, PINK=PINK, YELLOW=YELLOW, WHITEF=WHITEF,
                           VINE=pal("1F4A16", "2F6E22", "469032", "66B446")),
                  METAL=pal("3A2716", "563A22", "8A6A3A", "B89650", "E8CC80"),
                  GEM=pal("0E4A1A", "2A9A3A", "8AF07A", "E0FFD8"), GLOW=H("A0DC6C")),
    "master": dict(pal=dict(LEAF=pal("3A1A08", "6A300C", "A04E14", "D8781E", "F8B048"),
                            BARK=pal("2A1A0C", "3E2814", "5A3C20", "7A5630", "9E7A4C"),
                            DLEATH=pal("3A220E", "5A361C", "7A4E2C", "9C6A40"),
                            ANTLER=pal("6A4A1A", "8E6A2A", "B8903E", "DCB860", "F4DC94"),
                            PINK=pal("8A1A10", "C83A1E", "FF7A4A"), YELLOW=pal("B0700E", "EDB234", "FFE09E"),
                            WHITEF=pal("A88A5E", "E2C89A", "FFF2D6"), VINE=pal("3A3A10", "5A5A1A", "7A7A26", "9A9A36")),
                   METAL=GOLD_R, GEM=pal("6A3A04", "C87A10", "FFC04A", "FFF0C0"), GLOW=H("F8B048")),
    "archmage": dict(pal=dict(LEAF=pal("0A2A1E", "124632", "1C6A4A", "2E9468", "5CC896"),
                              BARK=pal("2E2A26", "48423A", "6A6256", "8E8676", "B4AC9C"),
                              DLEATH=pal("2A2622", "443E36", "5E564A", "7A7060"),
                              ANTLER=pal("6A6A62", "8E8E84", "B8B8AC", "DCDCD2", "F8F8F2"),
                              PINK=pal("2A8A8A", "5AD8D0", "C8FFF8"), YELLOW=pal("8AA020", "C8E04A", "F0FFA0"),
                              WHITEF=pal("A8B8C8", "E2ECF6", "FFFFFF"),
                              VINE=pal("0E3A2A", "16563E", "227A58", "34A078")),
                     METAL=pal("5A5A62", "8A8A94", "B8B8C4", "DCDCE6", "F8F8FF"),
                     GEM=pal("0A5A4A", "1AB894", "7FFFD4", "E0FFF6"), GLOW=H("7FFFD4")),
}
# pontas da galhada do grau I (lado direito): mesmas contas do druid_model
_B1 = seg_end([-3.6, 29.6, 0.4], (-0.8, 1, 0.2), 4.0)
_MID1 = [(-3.6 + _B1[0]) / 2, (29.6 + _B1[1]) / 2, (0.4 + _B1[2]) / 2]
_B2 = seg_end(_B1, (-0.35, 1, 0.45), 3.4)
DRUID_TIPS = [seg_end(_MID1, (-0.25, 1, -0.75), 2.6), seg_end(_B1, (-1, 0.7, -0.15), 2.2),
              seg_end(_B2, (-0.05, 1, 0.65), 2.4), seg_end(_B2, (-0.75, 0.9, -0.3), 2.0)]


def druid_tier_mats(tier):
    T_ = DRUID_T[tier]
    lv = TLV[tier]
    P = T_["pal"]
    LEAF_, BARK_, VINE_ = P["LEAF"], P["BARK"], P["VINE"]
    FL = [P["PINK"], P["YELLOW"], P["WHITEF"]]
    MT, GEM, GL = T_["METAL"], T_["GEM"], T_["GLOW"]
    M = druid_mats(**P)
    orn_common(M, MT, GEM, GL)

    def bark_plate(r):
        bark(r, BARK_)
        r.row(0, LEAF_[3])
        for i in range(0, r.w, 2):
            r.set(i, 1 if r.h > 2 else 0, LEAF_[2])
        if lv >= 3:
            for j in range(1, r.h - 1, 2):
                r.glow((j * 3) % r.w, j, GL)

    M["o_paul_side"] = bark_plate
    M["o_paul_top"] = lambda r: leaves(r, LEAF_, flowers=FL, flower_chance=0.12 + 0.05 * lv)
    M["o_mush_cap"] = lambda r: (r.fill(H("B8281E") if lv < 3 else GL), r.set(0, 0, H("F4E8D8")),
                                 [r.glow(i, j, r.get(i, j)) for j in range(r.h) for i in range(r.w)] if lv >= 3 else None)
    M["o_mush_stem"] = solid(H("E8DCC4"))
    M["o_blossom"] = glow_fill(P["PINK"][2] if lv >= 3 else P["PINK"][1], 0.5)
    M["o_firefly"] = glow_fill(GL, 0.7)

    def cape_out(r):
        leaves(r, LEAF_, flowers=FL, flower_chance=0.07 + 0.03 * lv)
        if lv >= 2:
            # nervuras de seiva (Mestre) / de espirito (Arquimago) que brilham
            cx = r.w // 2
            for j in range(2, r.h - 3):
                r.glow(cx, j, GL if j % 3 else lighten(GL, 0.4))
                if j % 5 == 2:
                    for k in range(1, 4):
                        r.glow(cx - k, j + k, GL); r.glow(cx + k, j + k, GL)
        r.rnd.seed(1313 + r.w)
        r.ragged(3, sym=True, chance=0.75)

    M["o_cape_out"] = cape_out
    M["o_cape_in"] = lambda r: (leather(r, P["DLEATH"], base=1, seams=False, speckle=0.04), r.rnd.seed(1313 + r.w),
                                r.ragged(3, sym=True, chance=0.75))
    M["o_cape_edge"] = solid(LEAF_[2])
    return M


def druid_tier_model(tier):
    lv = TLV[tier]
    m = druid_model(f"outfit_druid_{tier}")
    hd = "armorHead"
    # flores extras na tiara
    for o, f in (([-1.2, 30.6, -5.1], "druid_flower_white@1x1"), ([0.9, 31.2, -5.1], "druid_flower_pink@1x1"),
                 ([-5.1, 30.4, -2.4], "druid_flower_yellow@1x1"), ([4.1, 30.5, -2.0], "druid_flower_white@1x1")):
        m.box(hd, o, [1, 1, 1], fc(all=f))
    gem(m, hd, [0, 29.4, -4.95], [1.3, 1.3, 0.5])
    if lv >= 2:
        # aro de metal na base da tiara de galhos
        ring(m, hd, -4.5, 4.5, -4.5, 4.5, 28.2, 0.6, 0.3)
        n0 = len(m.by[hd]["cubes"])
        # galhos extras na galhada
        AF = fc(side="druid_antler", u="druid_antler_tip@1x1", d="druid_antler@1x1")
        m.seg(hd, _B2, (-0.9, 0.5, 0.6), 2.0, 0.6, AF, overlap=0.3)
        m.seg(hd, _B1, (-0.3, 0.6, 1.0), 1.8, 0.55, AF, overlap=0.3)
        if lv == 3:
            for tip in DRUID_TIPS + [_B2]:
                gem(m, hd, tip, 0.8, "o_blossom@1x1")
        else:
            # cogumelinhos crescendo na galhada
            m.box(hd, [_B1[0] - 0.3, _B1[1] - 0.2, _B1[2] - 0.3], [0.6, 0.6, 0.6], fc(all="o_mush_stem@1x1"))
            m.box(hd, [_B1[0] - 0.6, _B1[1] + 0.4, _B1[2] - 0.6], [1.2, 0.5, 1.2], fc(all="o_mush_cap@2x2"))
        mirror_new(m, hd, hd, n0)
    if lv == 3:
        # vagalumes espirituais em volta da galhada
        for (x, y, z) in ((-7.5, 36.5, 1.0), (6.8, 37.8, -0.5), (-5.0, 39.6, 2.6), (4.6, 34.6, 3.0),
                          (0.6, 38.6, -2.4), (-8.2, 33.4, -1.2)):
            gem(m, hd, [x, y, z], 0.45, "o_firefly@1x1")

    gem(m, "waistLegs", [0, 11.85, -3.0], [1.2, 1.2, 0.5])
    cape(m, 23.5, {1: 5.5, 2: 3.0, 3: 1.5}[lv], 3.05, 9.4, flare=5)

    def mushrooms(mm, ra, top_y, rz, piv):
        if lv != 2:
            return
        for (x, z, s) in ((-8.0, -1.4, 1.2), (-6.6, 1.2, 0.9)):
            mm.box(ra, [x - 0.2, top_y, z - 0.2], [0.4, 0.6, 0.4], fc(all="o_mush_stem@1x1"), rot=[0, 0, rz], pivot=piv)
            mm.box(ra, [x - s / 2, top_y + 0.6, z - s / 2], [s, 0.45, s], fc(all="o_mush_cap@2x2"), rot=[0, 0, rz],
                   pivot=piv)

    pauldrons(m, lv, under="druid_dark@2x2", gem_mat="o_gem@1x2" if lv == 3 else None, gem_size=(0.8, 1.8, 0.8),
              extra=mushrooms)
    if lv >= 2:
        rb = "armorRightBoot"
        n0 = len(m.by[rb]["cubes"])
        m.box(rb, [-4.7, 2.2, 0.2], [0.5, 0.5, 0.5], fc(all="o_mush_stem@1x1"))
        m.box(rb, [-5.0, 2.7, -0.1], [1.1, 0.45, 1.1], fc(all="o_mush_cap@2x2"))
        mirror_new(m, rb, "armorLeftBoot", n0)
    return m


# ------------------------------------------------------------------ NECROMANTE: Ceifador (II), Lich (III), Rei dos Mortos (IV)
NECRO_T = {
    "adept": dict(pal=dict(GRAY=pal("08100C", "0E1A14", "16261E", "20342A", "2E463A"), BONE=BONE,
                           SOUL=pal("1A8A4A", "3AE890", "A0FFD0", "F0FFF8"),
                           WRAPC=pal("161A1A", "222828", "323A3A", "445050"), NPURP=pal("0E2A1E", "184432", "226048")),
                  METAL=BONE, GEM=pal("0E5A2E", "2ACB70", "8CFFC0", "E8FFF2"), GLOW=H("3AE890")),
    "master": dict(pal=dict(GRAY=pal("0E0A14", "18121E", "241A2E", "322440", "463258"),
                            BONE=pal("4E4632", "766A4E", "9E9070", "C2B694", "E0D8BC"),
                            SOUL=pal("4A1A8A", "7A3AD0", "B88AFF", "EEDCFF"),
                            WRAPC=pal("1A1420", "282030", "383044", "4A405A"), NPURP=pal("2A1440", "40205E", "5A2E80")),
                   METAL=pal("4E4632", "766A4E", "9E9070", "C2B694", "E0D8BC"),
                   GEM=pal("3A0A7A", "7A3AD0", "C8A0FF", "F4ECFF"), GLOW=H("B88AFF")),
    "archmage": dict(pal=dict(GRAY=pal("060608", "0E0E12", "18181E", "24242C", "34343E"),
                              BONE=pal("6A5A30", "9A844A", "C8B070", "E8D49A", "FFF4D0"),
                              SOUL=pal("0A5A6A", "1AB0C8", "7AF0FF", "E0FCFF"),
                              WRAPC=pal("101014", "1C1C22", "2A2A32", "3A3A44"), NPURP=pal("6A4A10", "A0781E", "D4A838")),
                     METAL=GOLD_B, GEM=pal("0A4A5A", "1AB0C8", "7AF0FF", "E0FCFF"), GLOW=H("7AF0FF")),
}


def necro_tier_mats(tier):
    T_ = NECRO_T[tier]
    lv = TLV[tier]
    P = T_["pal"]
    GRAY_, BONE_, SOUL_, NP = P["GRAY"], P["BONE"], P["SOUL"], P["NPURP"]
    MT, GEM, GL = T_["METAL"], T_["GEM"], T_["GLOW"]
    M = necro_mats(**P)
    orn_common(M, MT, GEM, GL)
    if lv >= 3:
        for k in ("necro_skirt_front", "necro_skirt_back"):
            M[k] = runed(M[k], GL, rows_from_bottom=5, seed=len(k))

    def bone_plate(r):
        bone(r, BONE_, horizontal=True)
        for i in range(1, r.w - 1, 2):
            r.set(i, r.h - 1, BONE_[1])
        if lv >= 3:
            r.row(0, MT[3])
            r.glow(r.w // 2, r.h // 2, GL)

    M["o_paul_side"] = bone_plate
    M["o_paul_top"] = lambda r: (bone(r, BONE_), r.glow(r.w // 2, r.h // 2, SOUL_[2]) if lv >= 2 else None)
    M["o_spike"] = lambda r: (r.fill(BONE_[3]), r.row(0, BONE_[4]), r.col(-1, BONE_[2]) if r.w > 1 else None,
                              r.noise(0.03))
    M["o_soul_tip"] = flame_mat([SOUL_[0], SOUL_[0], SOUL_[1], SOUL_[2], SOUL_[3]])

    def phylactery(r):
        for j in range(r.h):
            for i in range(r.w):
                edge = i in (0, r.w - 1)
                if edge:
                    r.set(i, j, BONE_[2] if j % 2 else BONE_[3])
                else:
                    r.glow(i, j, SOUL_[2] if (i + j) % 3 else SOUL_[3])

    M["o_phylactery"] = phylactery

    def lantern(r):
        for j in range(r.h):
            for i in range(r.w):
                frame = i in (0, r.w - 1) or j in (0, r.h - 1)
                if frame:
                    r.set(i, j, MT[3] if (i + j) % 2 else MT[2])
                else:
                    r.glow(i, j, SOUL_[2])

    M["o_lantern"] = lantern

    def cape_out(r):
        cloth(r, GRAY_, period=4, base=2, top=0.05, bottom=0.12)
        cx = r.w // 2
        # coluna de vertebras bordada + cranio no alto
        for j in range(5, r.h - 4):
            r.set(cx - 1, j, BONE_[2] if j % 2 else BONE_[1]); r.set(cx, j, BONE_[3] if j % 2 else BONE_[1])
        for (i, j) in ((cx - 2, 1), (cx - 1, 1), (cx, 1), (cx + 1, 1), (cx - 2, 2), (cx + 1, 2), (cx - 1, 3), (cx, 3)):
            r.set(i, j, BONE_[3])
        r.glow(cx - 1, 2, SOUL_[2]); r.glow(cx, 2, SOUL_[2])
        r.row(0, NP[1])
        if lv >= 3:
            for k, i in enumerate(range(1, r.w - 2, 4)):
                rune_glyph(r, i, r.h - 9, GL, k)
        r.rnd.seed(5150 + r.w)
        r.ragged(4, sym=True, chance=0.75)

    M["o_cape_out"] = cape_out
    M["o_cape_in"] = lambda r: (cloth(r, NP, period=4, base=1, folds=False), r.rnd.seed(5150 + r.w),
                                r.ragged(4, sym=True, chance=0.75))
    M["o_cape_edge"] = solid(GRAY_[1])
    return M


def necro_tier_model(tier):
    lv = TLV[tier]
    m = necro_model(f"outfit_necromancer_{tier}")
    hd = "armorHead"
    # escapula e cranio assimetricos do grau I saem: as ombreiras novas sao simetricas
    for arm in ("armorRightArm", "armorLeftArm"):
        strip(m, arm, lambda c: c["origin"][1] > 22.5)
    # coroa de ossos no alto do cranio
    if lv == 1:
        spikes = [(-3.2, 1.0), (-1.6, 1.4), (0.0, 1.9), (1.6, 1.4), (3.2, 1.0)]
    elif lv == 2:
        spikes = [(-3.9, 1.0), (-2.6, 1.5), (-1.3, 2.0), (0.0, 2.7), (1.3, 2.0), (2.6, 1.5), (3.9, 1.0)]
    else:
        spikes = [(-4.0, 1.4), (-3.0, 1.9), (-2.0, 2.4), (-1.0, 2.9), (0.0, 3.6), (1.0, 2.9), (2.0, 2.4),
                  (3.0, 1.9), (4.0, 1.4)]
    base_y = 35.0
    if lv == 3:
        ring(m, hd, -5.1, 5.1, -5.4, 4.0, 34.0, 1.3, 0.35)
        base_y = 35.3
    for x, hgt in spikes:
        w = 0.8 if lv < 3 else 0.7
        m.box(hd, [x - w / 2, base_y - 0.3, -4.6], [w, hgt, w], fc(side="o_spike", u="o_spike@1x1", d=OMIT))
        if lv == 3:
            gem(m, hd, [x, base_y - 0.3 + hgt + 0.35, -4.6 + w / 2], 0.6, "o_soul_tip@1x2")
    if lv >= 2:
        gem(m, hd, [0, 33.6, -5.62], [1.4, 1.4, 0.5])
    if lv == 3:
        for k in range(6):
            a = k * math.pi / 3 + 0.3
            gem(m, hd, [6.6 * math.cos(a), 36.2 + (0.6 if k % 2 else 0.0), 6.6 * math.sin(a)], 0.55,
                "o_soul_tip@1x2")

    bd = "armorBody"
    gem(m, bd, [0, 22.7, -3.2], [1.3, 1.3, 0.5])
    cape(m, 24.0, {1: 12.5, 2: 5.0, 3: 1.5}[lv], 3.1, 9.4, flare=8)

    def skulls(mm, ra, top_y, rz, piv):
        if lv < 2:
            return
        s = 2.6 if lv == 2 else 3.2
        mm.box(ra, [-8.0 - s / 2, top_y - 0.2, -s / 2], [s, s, s], fc(n="necro_mini_skull_face", s="necro_mini_skull",
                                                                    ew="necro_mini_skull", u="necro_mini_skull",
                                                                    d="necro_bone_dark@2x2"), rot=[0, 0, rz], pivot=piv)

    pauldrons(m, lv, under="necro_dark@2x2", spikes=2 if lv == 3 else 0, spike_len=2.0, extra=skulls,
              spike_tip="o_soul_tip@1x2" if lv == 3 else None)
    if lv >= 2:
        wl = "waistLegs"
        m.box(wl, [-7.3, 7.6, -0.7], [1.4, 2.4, 1.4], fc(side="o_phylactery@2x4", u="necro_bone_flat@2x2",
                                                         d="necro_bone_flat@2x2"))
        m.box(wl, [-7.5, 10.0, -0.9], [1.8, 0.6, 1.8], fc(all="necro_bone_flat@2x1"))
        m.box(wl, [-6.75, 10.6, -0.15], [0.3, 1.4, 0.3], fc(all="necro_bone_flat@1x2"))
    if lv == 3:
        wl = "waistLegs"
        m.box(wl, [5.6, 7.2, -0.9], [1.8, 2.6, 1.8], fc(side="o_lantern@3x4", u="o_metal_flat@2x2",
                                                        d="o_metal_flat@2x2"))
        m.box(wl, [6.35, 9.8, -0.15], [0.3, 1.8, 0.3], fc(all="o_metal_flat@1x2"))
    return m


TIER_BUILDERS = {
    "mage": (mage_tier_mats, mage_tier_model),
    "witch": (witch_tier_mats, witch_tier_model),
    "warlock": (warlock_tier_mats, warlock_tier_model),
    "druid": (druid_tier_mats, druid_tier_model),
    "necromancer": (necro_tier_mats, necro_tier_model),
}
TIER_THEMES = {"mage": MAGE_T, "witch": WITCH_T, "warlock": WARLOCK_T, "druid": DRUID_T, "necromancer": NECRO_T}


# ------------------------------------------------------------------ icones dos graus (recolore o icone do grau I + gema)
ICON_FAMILIES = {
    "mage": ["NAVY", "GOLD", "VIOLET", "MBROWN"],
    "witch": ["WHAT", "PURP", "LACE", "APRON", "HERB", "SILVER", "POTION", "WBROWN", "GOLD"],
    "warlock": ["BLK", "RED", "HORN", "IRON", "WLEATH"],
    "druid": ["LEAF", "BARK", "DLEATH", "ANTLER", "PINK", "YELLOW", "WHITEF", "VINE"],
    "necromancer": ["GRAY", "BONE", "SOUL", "WRAPC", "NPURP"],
}
# pixel da gema em cada icone (faixa do chapeu, broche, fivela, cinto...)
ICON_GEM = {
    "mage_helmet": (9, 10), "mage_chestplate": (8, 5), "mage_leggings": (8, 2), "mage_boots": (12, 7),
    "witch_helmet": (8, 10), "witch_chestplate": (7, 3), "witch_leggings": (5, 2), "witch_boots": (5, 10),
    "warlock_helmet": (7, 6), "warlock_chestplate": (7, 5), "warlock_leggings": (7, 3), "warlock_boots": (6, 5),
    "druid_helmet": (7, 10), "druid_chestplate": (7, 9), "druid_leggings": (7, 2), "druid_boots": (4, 4),
    "necromancer_helmet": (8, 3), "necromancer_chestplate": (7, 3), "necromancer_leggings": (7, 2),
    "necromancer_boots": (5, 3),
}


def icon_recolor(cid, tier, palette):
    """Cada cor do icone vai para o MESMO tom da familia correspondente na paleta do grau (mantendo o desvio)."""
    fams = ICON_FAMILIES[cid]
    base = {f: globals()[f] for f in fams}
    tp = TIER_THEMES[cid][tier]["pal"]
    cols = {k: H(v) for k, v in palette.items()}
    out = {}
    for k, c in cols.items():
        best = None
        for f in fams:
            for idx, bc in enumerate(base[f]):
                d = math.sqrt(sum((c[q] - bc[q]) ** 2 for q in range(3)))
                if best is None or d < best[0]:
                    best = (d, f, idx)
        d, f, idx = best
        if d > 70:
            out[k] = c
            continue
        tc, bc = tp[f][idx], base[f][idx]
        out[k] = tuple(max(0, min(255, tc[q] + c[q] - bc[q])) for q in range(3)) + (255,)
    darkest = min(cols, key=lambda k: sum(cols[k][:3]))
    if sum(out[darkest][:3]) > 120:
        out[darkest] = darken(out[darkest], 0.6)  # contorno continua escuro mesmo no traje claro
    return out


def sparkle(c, corner, col):
    """Brilho de 4 pontas num canto vazio do icone (grau IV)."""
    cx0, cy0 = corner
    spots = sorted(((x, y) for x in range(1, 15) for y in range(1, 15)),
                   key=lambda p: (p[0] - cx0) ** 2 + (p[1] - cy0) ** 2)
    for x, y in spots:
        area = [(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)]
        if all(c.get(px, py)[3] == 0 for px, py in area):
            c.set(x, y, (255, 255, 255, 255))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                c.set(x + dx, y + dy, col)
            return


def tier_pip(c, corner, G):
    """Gema-marcador do grau num canto vazio (II: uma, III: duas): no inventario o grau se le de relance,
    mesmo quando a paleta nova fica parecida com a do grau I (Druida/Warlock Adepto)."""
    cx0, cy0 = corner
    spots = sorted(((x, y) for x in range(1, 15) for y in range(1, 15)
                    if (x - cx0) ** 2 + (y - cy0) ** 2 <= 16),
                   key=lambda p: (p[0] - cx0) ** 2 + (p[1] - cy0) ** 2)
    x, y = cx0, cy0  # icone sem canto livre (calcas que ocupam o quadro todo): a gema fica cravada no canto
    for sx, sy in spots:
        area = [(sx + dx, sy + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)]
        if all(c.get(px, py)[3] == 0 for px, py in area):
            x, y = sx, sy
            break
    c.set(x, y, G[3])
    for dx, dy in ((1, 0), (0, -1)):
        c.set(x + dx, y + dy, G[2])
    for dx, dy in ((-1, 0), (0, 1)):
        c.set(x + dx, y + dy, G[1])


def draw_tier_icon(cid, piece, tier):
    palette, rows = ICONS[f"{cid}_{piece}"]
    cols = icon_recolor(cid, tier, palette)
    c = Canvas(16, 16)
    for y, row in enumerate(rows[:16]):
        row = (row + "." * 16)[:16]
        for x, ch in enumerate(row):
            if ch != "." and ch in cols:
                c.set(x, y, cols[ch])
    th = TIER_THEMES[cid][tier]
    G, lv = th["GEM"], TLV[tier]
    gx, gy = ICON_GEM[f"{cid}_{piece}"]
    if lv >= 2:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if c.get(gx + dx, gy + dy)[3] > 0:
                c.set(gx + dx, gy + dy, G[1] if lv == 2 else G[2])
    c.set(gx, gy, G[2] if lv == 1 else G[3])
    if lv == 3:
        glow_c = lighten(th["GLOW"], 0.2)
        sparkle(c, (14, 1), glow_c)
        sparkle(c, (1, 14), glow_c)
    else:
        tier_pip(c, (14, 1), G)
        if lv == 2:
            tier_pip(c, (1, 14), G)
    name = f"{cid}_{tier}_{piece}"
    c.save(os.path.join(A, "textures/item", name + ".png"))
    item_model(A, name)


def tier_layers(cid, tier):
    p = TIER_THEMES[cid][tier]["pal"]
    mt = TIER_THEMES[cid][tier]["METAL"]
    main_, dark_ = {"mage": ("NAVY", "NAVY"), "witch": ("PURP", "WHAT"), "warlock": ("BLK", "BLK"),
                    "druid": ("LEAF", "LEAF"), "necromancer": ("GRAY", "GRAY")}[cid]
    vanilla_layers(f"{cid}_{tier}", p[main_][2], mt[2], p[dark_][0])


# ------------------------------------------------------------------ receitas dos graus
UPGRADE_FROM = {"adept": None, "master": "adept", "archmage": "master"}
UPGRADE_MATERIALS = {
    "adept": ["isekai_mahou:mana_crystal"],
    "master": ["minecraft:diamond"],
    "archmage": ["minecraft:netherite_ingot"] + [f"isekai_mahou:guardian_core_{e}"
                                                 for e in ("fire", "water", "earth", "wind", "light", "shadow")],
}
UPGRADE_TEMPLATE = "isekai_mahou:arcane_upgrade_template"


def tier_recipes():
    """Melhoria na Mesa de Ferraria (modelo do armory; sem ele, Essencia Arcana) e reforma entre classes do mesmo grau."""
    rdir = os.path.join(D, "recipe")
    has_tmpl = {"type": "neoforge:item_exists", "item": UPGRADE_TEMPLATE}
    for tier in TIERS:
        write_json(os.path.join(D, "tags/item/outfits/upgrade", tier + ".json"), {"values": UPGRADE_MATERIALS[tier]})
        for piece in PIECES:
            write_json(os.path.join(D, "tags/item/outfits", tier, piece + ".json"),
                       {"values": [f"isekai_mahou:{c}_{tier}_{piece}" for c in SETS]})
        for cid, (wool, _accent, theme) in SETS.items():
            for piece in PIECES:
                prev = UPGRADE_FROM[tier]
                base = f"isekai_mahou:{cid}_{piece}" if prev is None else f"isekai_mahou:{cid}_{prev}_{piece}"
                body = {"type": "minecraft:smithing_transform", "base": {"item": base},
                        "addition": {"tag": f"isekai_mahou:outfits/upgrade/{tier}"},
                        "result": {"id": f"isekai_mahou:{cid}_{tier}_{piece}", "count": 1}}
                write_json(os.path.join(rdir, f"{cid}_{tier}_{piece}_upgrade.json"),
                           {"neoforge:conditions": [has_tmpl], "template": {"item": UPGRADE_TEMPLATE}, **body})
                write_json(os.path.join(rdir, f"{cid}_{tier}_{piece}_upgrade_essence.json"),
                           {"neoforge:conditions": [{"type": "neoforge:not", "value": has_tmpl}],
                            "template": {"item": "isekai_mahou:essence_arcane"}, **body})
                write_json(os.path.join(rdir, f"{cid}_{tier}_{piece}_retailor.json"), {
                    "type": "isekai_mahou:outfit_retailor", "category": "equipment",
                    "group": f"isekai_mahou:outfit_retailor_{tier}_{piece}",
                    "ingredients": [{"tag": f"isekai_mahou:outfits/{tier}/{piece}"}, {"item": wool}, {"item": theme}],
                    "result": {"id": f"isekai_mahou:{cid}_{tier}_{piece}", "count": 1}})
                unlock_advancement(f"{cid}_{tier}_{piece}_retailor", f"#isekai_mahou:outfits/{tier}/{piece}")
    write_json(os.path.join(D, "tags/item/outfits/all.json"),
               {"values": [f"#isekai_mahou:outfits/{p}" for p in PIECES]
                + [f"#isekai_mahou:outfits/{t}/{p}" for t in TIERS for p in PIECES]})
    vanilla = {"helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor", "boots": "foot_armor"}
    for piece, tag in vanilla.items():
        write_json(os.path.join(MERGE, "data/minecraft/tags/item", tag + ".json"),
                   {"values": [f"isekai_mahou:{c}_{piece}" for c in SETS]
                    + [f"isekai_mahou:{c}_{t}_{piece}" for t in TIERS for c in SETS]})


def build_tiers(only=None):
    print("== graus II-IV: modelos e atlas")
    for cid, (mats_fn, model_fn) in TIER_BUILDERS.items():
        if only and cid not in only:
            continue
        for tier in TIERS:
            _, glow = build(model_fn(tier), mats_fn(tier))
            if not glow:
                # o Java liga a camada de brilho em todo grau acima do I
                raise RuntimeError(f"outfit_{cid}_{tier} sem glowmask")
            tier_layers(cid, tier)
            for piece in PIECES:
                draw_tier_icon(cid, piece, tier)


# =====================================================================================
#  v0.9: CLASSES GUERREIRAS E A DISTANCIA (10 trajes x 4 graus)
#  Cada classe tem: temas por grau (familias MAIN, SEC, TRIM, LEATH, ACC + METAL/GEM/GLOW dos ornamentos), uma funcao de
#  materiais mats(P, T, lv) e uma de modelo model(name, lv) — lv 0 = grau I, 1..3 = graus II..IV.
#  Os ossos sao os mesmos das Magicas (o renderer e o mesmo): hatMid/hatTip balancam (plumas, penas, fitas, ponta do
#  capuz), outfitCape levanta ao correr (capas, faixas, cachecol, aljava), waistChest/waistLegs + paineis abrem com o
#  passo (tabardos, saias de malha, tangas, aventais, abas do casaco).
#  Papeis das familias (usados tambem nos icones): MAIN cor dominante, SEC segunda cor (tabardo, malha, forro),
#  TRIM metal dos detalhes, LEATH couro, ACC destaque (pluma, osso, presa, contas, penas).
# =====================================================================================
V9_ORDER = ("warrior", "paladin", "barbarian", "monk", "knight", "ranger", "rogue", "bard", "artificer", "hunter")
V9_GLOW_I = {"paladin", "artificer"}  # igual a OutfitSet.glows no Java (o grau I tambem tem glowmask)
V9 = {}
HD, BD, RA, LA, RL, LL, RB, LB = ("armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg",
                                  "armorLeftLeg", "armorRightBoot", "armorLeftBoot")
LEATHER_STD = pal("2A1A10", "45301C", "64462A", "80603A", "A07E50")
LEATHER_DARK = pal("1A120C", "2A1E14", "3C2C1E", "52402C", "6A543C")
IRON_R = pal("2A2A2E", "44444A", "626268", "86868E", "AEAEB6")
BRASS = pal("4A3410", "7A5A1C", "A8822E", "D4AE4A", "F2D888")
STEEL_P = pal("30323A", "50545E", "7A808C", "A8AEB8", "E0E4EA")
IVORY = pal("6A6250", "948A72", "BEB496", "DCD4B8", "F4EEDC")
CREAM = pal("8A8478", "B4AE9E", "D8D2C2", "F0ECE0", "FFFFFA")


def theme(MAIN, SEC, TRIM, LEATH, ACC, METAL, GEM, GLOW):
    return dict(pal=dict(MAIN=MAIN, SEC=SEC, TRIM=TRIM, LEATH=LEATH, ACC=ACC), METAL=METAL, GEM=GEM, GLOW=H(GLOW))


def put(r, lit):
    """Pinta pixel que brilha (glowmask) so quando o traje tem brilho; no grau I sem brilho vira pixel comum."""
    return r.glow if lit else r.set


# ------------------------------------------------------------------ pintores genericos
def chainmail(r, S):
    """Cota de malha opaca: aneis 2x2 desencontrados (reflexo em cima, sombra embaixo)."""
    for j in range(r.h):
        for i in range(r.w):
            a = (i + (j // 2)) % 2
            b = j % 2
            r.set(i, j, S[3] if (a, b) == (0, 0) else (S[0] if (a, b) == (1, 1) else S[2]))
    r.vshade(0.05, 0.16)
    r.noise(0.03)


def plate(r, P, TR=None, rivets=True, ridge=False, shine=True, edge="full"):
    """Placa de armadura: reflexo diagonal, luz de cima, borda de metal e rebites nos cantos.
    edge="top": so um filete de metal em cima (placas grandes sem ficar tudo dourado)."""
    r.fill(P[2])
    if shine and r.w >= 3 and r.h >= 3:
        for j in range(r.h):
            i = int(round(r.w * 0.2)) + j // 2
            r.set(i, j, P[3])
            r.set(i + 1, j, P[4] if j < r.h / 2 else P[3])
    r.vshade(0.10, 0.16)
    if ridge and r.w >= 5:
        cx = r.w // 2
        r.col(cx, P[4])
        r.col(cx - 1, P[1])
    if TR is not None and edge == "top":
        r.row(0, TR[3])
        if r.h > 2:
            r.row(-1, P[0])
    elif TR is not None:
        r.row(0, TR[3])
        if r.h > 2:
            r.row(-1, TR[1])
        if r.w > 3:
            r.col(0, TR[2])
            r.col(-1, TR[2])
        if rivets and r.w >= 4 and r.h >= 4:
            for (i, j) in ((1, 1), (r.w - 2, 1), (1, r.h - 2), (r.w - 2, r.h - 2)):
                r.set(i, j, TR[4])
    r.noise(0.02)


def fur(r, F, ragged=0, seed=0):
    """Pelagem: mechas verticais desencontradas, pontas claras; 'ragged' rasga a barra."""
    offs = [r.rnd.randrange(4) for _ in range(r.w)]
    for i in range(r.w):
        for j in range(r.h):
            k = (j + offs[i]) % 4
            c = F[1] if k == 0 else (F[3] if k == 2 else F[2])
            if r.rnd.random() < 0.07:
                c = F[4]
            r.set(i, j, c)
    r.vshade(0.10, 0.18)
    if ragged and r.h > ragged + 1:
        r.rnd.seed(seed + r.w * 7 + r.h)
        r.ragged(ragged, sym=True, chance=0.75)


def wraps(r, W, period=3):
    """Faixas de pano enroladas (diagonais com sombra entre as voltas)."""
    for j in range(r.h):
        for i in range(r.w):
            k = (i + j) % period
            r.set(i, j, W[1] if k == 0 else (W[3] if k == 1 else W[2]))
    r.vshade(0.06, 0.12)
    r.noise(0.02)


def feather(r, F, tip=None, lit=False):
    """Pena/pluma: haste clara no meio e barbas em diagonal; ponta opcional (que brilha nos graus altos)."""
    cx = r.w // 2
    for j in range(r.h):
        for i in range(r.w):
            d = abs(i - cx)
            r.set(i, j, F[2] if (j + d) % 2 else F[3])
        r.set(cx, j, F[4])
    r.vshade(0.10, 0.12)
    if tip is not None:
        for i in range(r.w):
            put(r, lit)(i, 0, tip)


def slashed(r, A, B, period=3):
    """Gibao com talhos: tecido A com fendas verticais mostrando o forro B (estilo renascentista)."""
    cloth(r, A, period=4, base=2, folds=False)
    for i in range(1, r.w, period):
        for j in range(r.h):
            if j % 5 not in (0,):
                r.set(i, j, B[3] if j % 5 == 1 else B[2])
    r.vshade(0.08, 0.14)


def gear_face(MT, hole=None):
    """Face de engrenagem: aro claro, sombra, furo do eixo."""
    def f(r):
        r.fill(MT[2])
        if r.w >= 3 and r.h >= 3:
            r.row(0, MT[3]); r.col(0, MT[3]); r.row(-1, MT[1]); r.col(-1, MT[1])
            r.set(r.w // 2, r.h // 2, hole if hole is not None else MT[0])
        r.noise(0.03)
    return f


def sun(r, cx, cy, core, ray, lit, big=True):
    """Sol de 4/8 raios (emblema do Paladino)."""
    p = put(r, lit)
    for (dx, dy) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
        p(cx + dx, cy + dy, core)
    p(cx, cy, lighten(core, 0.5))
    rays = [(0, -3), (0, -2), (0, 2), (0, 3), (-3, 0), (-2, 0), (2, 0), (3, 0)]
    if big:
        rays += [(-2, -2), (2, -2), (-2, 2), (2, 2)]
    for dx, dy in rays:
        p(cx + dx, cy + dy, ray)


def ragged_bottom(r, depth, seed):
    r.rnd.seed(seed + r.w * 13 + r.h)
    r.ragged(depth, sym=True, chance=0.7)


def leaf_hem(r, c_edge, depth=2):
    """Barra recortada em folhas (pontas arredondadas), simetrica."""
    for i in range(r.w):
        k = min(i, r.w - 1 - i) % 3
        d = depth if k == 1 else (1 if k == 0 else 0)
        for q in range(d):
            r.clear(i, r.h - 1 - q)
        r.set(i, r.h - 1 - d, c_edge)


def v9_common(M, P, T, lv):
    """Materiais que toda classe nova tem (couro, cinto, sola, metal dos ornamentos, ombreiras padrao)."""
    orn_common(M, T["METAL"], T["GEM"], T["GLOW"])
    MAIN, SEC, TRIM, LEATH, ACC = (P[k] for k in ("MAIN", "SEC", "TRIM", "LEATH", "ACC"))
    MT = T["METAL"]
    M["dark"] = solid(darken(MAIN[0], 0.35))
    M["sole"] = solid(LEATH[0])
    M["leather"] = lambda r: leather(r, LEATH)
    M["leather_top"] = lambda r: (r.fill(LEATH[2]), r.noise(0.04))
    M["trim_flat"] = lambda r: (r.fill(TRIM[2]), r.row(0, TRIM[3]) if r.h > 1 else None, r.noise(0.03))
    M["trim_plate"] = lambda r: plate(r, TRIM, rivets=False)

    def belt(r, front=False):
        r.fill(LEATH[1])
        r.row(0, LEATH[3])
        if r.h > 2:
            r.row(-1, LEATH[0])
        r.noise(0.04)
        for i in range(1, r.w - 1, 3):
            r.set(i, r.h // 2, LEATH[2])
        if front:
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, TRIM[3]); r.set(cx, j, TRIM[2]); r.set(cx + 1, j, TRIM[1])
            if r.h >= 3:
                r.set(cx, r.h // 2, LEATH[0])

    M["belt"] = belt
    M["belt_front"] = lambda r: belt(r, True)
    M["strap"] = lambda r: (r.fill(LEATH[2]), r.col(0, LEATH[3]) if r.w > 1 else None,
                            r.col(-1, LEATH[1]) if r.w > 2 else None, r.noise(0.04))
    M["o_paul_side"] = lambda r: plate(r, MT, rivets=True)
    M["o_paul_top"] = plate_top(MT, T["GEM"][2], glow=True)
    M["o_fin"] = lambda r: (r.fill(MT[3]), r.row(0, MT[4]), r.noise(0.03))
    M["o_spike"] = lambda r: (r.fill(MT[3]), r.row(0, MT[4]), r.col(0, MT[4]), r.noise(0.03))
    M["o_cape_edge"] = lambda r: (r.fill(MT[2]), r.noise(0.03))
    return MAIN, SEC, TRIM, LEATH, ACC


# ------------------------------------------------------------------ pecas de geometria reaproveitadas
def v9_sym(m, src, dst, fn):
    """Cria cubos no lado direito com fn() e espelha SO os novos no lado esquerdo."""
    n0 = len(m.by[src]["cubes"])
    fn()
    mirror_new(m, src, dst, n0)


def beads_u(m, bone, x_half, y_top, y_low, z, n, size, mat, center_mat=None, center_size=None):
    """Colar em U na frente do peito (contas, presas): n contas de -x_half a +x_half."""
    for k in range(n):
        t = -1 + 2 * k / (n - 1)
        x = t * x_half
        y = y_low + (y_top - y_low) * t * t
        if center_mat and k == n // 2:
            s = center_size or size * 1.6
            gem(m, bone, [x, y - s * 0.25, z - 0.1], s, center_mat)
        else:
            gem(m, bone, [x, y, z], size, mat)


def fangs_u(m, bone, x_half, y_top, y_low, z, n, mat, center_mat=None):
    """Colar de presas: dentes finos pendurados ao longo de um U."""
    for k in range(n):
        t = -1 + 2 * k / (n - 1)
        x = t * x_half
        y = y_low + (y_top - y_low) * t * t
        if center_mat and k == n // 2:
            gem(m, bone, [x, y - 0.4, z - 0.1], [1.0, 1.0, 0.5], center_mat)
            continue
        m.box(bone, [x - 0.22, y - 1.1, z - 0.2], [0.44, 1.1, 0.4], fc(all=mat), rot=[0, 0, -t * 18],
              pivot=[x, y, z])


def strap_diag(m, bone, mat, z_front, sign=1, width=0.8, edge="strap"):
    """Correia em diagonal no peito (ombro -> quadril oposto). sign=+1: ombro direito -> quadril esquerdo."""
    m.box(bone, [-width / 2, 11.95, z_front - 0.4], [width, 12.6, 0.4], fc(ns=mat, ew=edge + "@1x4", u=OMIT, d=OMIT),
          rot=[0, 0, -33.7 * sign], pivot=[0, 18.25, z_front - 0.2])


def gear(m, bone, center, size, mat="gear", depth=0.35, axis="z"):
    """Engrenagem de 8 dentes: dois quadrados girados 45 graus (um ve o outro pelas pontas)."""
    cx, cy, cz = center
    if axis == "z":
        o, s = [cx - size / 2, cy - size / 2, cz - depth / 2], [size, size, depth]
        f = fc(ns=mat + "@3x3", ew="trim_flat@1x2", u="trim_flat@2x1", d="trim_flat@2x1")
        m.box(bone, o, s, f)
        m.box(bone, o, s, f, rot=[0, 0, 45], pivot=center)
    else:
        o, s = [cx - depth / 2, cy - size / 2, cz - size / 2], [depth, size, size]
        f = fc(ew=mat + "@3x3", ns="trim_flat@1x2", u="trim_flat@1x2", d="trim_flat@1x2")
        m.box(bone, o, s, f)
        m.box(bone, o, s, f, rot=[45, 0, 0], pivot=center)


def bead_ring(m, bone, center, radius, n, size, mat, plane="xy"):
    """Anel de contas/gemas (aureola, rosario flutuante)."""
    cx, cy, cz = center
    for k in range(n):
        a = 2 * math.pi * k / n
        if plane == "xy":
            p = [cx + radius * math.cos(a), cy + radius * math.sin(a), cz]
        else:
            p = [cx + radius * math.cos(a), cy, cz + radius * math.sin(a)]
        gem(m, bone, p, size, mat)


def v9_cape_lengths(lv, base):
    """Capa cresce a cada grau (mais longa no Arquimago)."""
    return {0: base, 1: base - 0.8, 2: base - 2.0, 3: base - 3.5}[lv]

# ------------------------------------------------------------------ GUERREIRO: cota de malha, sobreveste e capa vermelhas
# I Cota de Malha · II Legionario (carmesim e bronze) · III Campeao (aco escuro e ouro) · IV Senhor da Guerra (brasas)
WARRIOR_T = {
    "base": theme(pal("3A0A0C", "5E1216", "8A1E20", "B23A30", "D86A50"), pal("24262E", "3E424C", "60666F", "8E95A0", "C9CED6"),
                  STEEL_P, LEATHER_STD, BRASS, STEEL_P, pal("5A0A0A", "A01818", "E84A3A", "FFC0A8"), "FF8A3A"),
    "adept": theme(pal("4A0A10", "74121C", "A41E26", "D0383A", "F07060"), pal("2C3038", "4A505C", "707886", "A0A8B4", "DCE2EA"),
                   pal("4A2A10", "7A4A1C", "A8702E", "D49A4A", "F2C888"), LEATHER_STD, pal("4A2A10", "7A4A1C", "A8702E", "D49A4A", "F2C888"),
                   pal("4A2A10", "7A4A1C", "A8702E", "D49A4A", "F2C888"), pal("5A2A04", "B0560E", "FF8A3A", "FFD8A8"), "FF8A3A"),
    "master": theme(pal("3A0610", "600C1E", "8C162C", "B42A40", "D85A68"), pal("1A1C22", "2E3038", "4A4E58", "70768A", "A8AEC0"),
                    GOLD_R, pal("1E120C", "34201A", "4A3026", "624030", "805A44"), GOLD_R, GOLD_R,
                    pal("4A3004", "A07010", "FFD24A", "FFF0B0"), "FFD24A"),
    "archmage": theme(pal("1E0606", "3A0A08", "5E120C", "8A2010", "C04018"), pal("0E0E12", "1C1C22", "2E2E36", "4A4A56", "7A7A88"),
                      pal("5A2A04", "9A520A", "D88A18", "FFB83A", "FFE89A"), LEATHER_DARK, pal("5A2A04", "9A520A", "D88A18", "FFB83A", "FFE89A"),
                      pal("5A2A04", "9A520A", "D88A18", "FFB83A", "FFE89A"), pal("5A1002", "C0300A", "FF5A1E", "FFC890"), "FF5A1E"),
}


def warrior_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    lit = lv > 0
    GL = T["GLOW"]
    M["chain"] = lambda r: chainmail(r, SEC)
    M["chain_dark"] = lambda r: (chainmail(r, SEC), r.map(lambda i, j, p: darken(p, 0.35)))

    def chain_skirt(r):
        chainmail(r, SEC)
        r.row(-1, ACC[2])
        for i in range(0, r.w, 2):
            r.set(i, r.h - 1, ACC[3])

    M["chain_skirt"] = chain_skirt

    def coif_front(r):
        chainmail(r, SEC)
        for j in range(2, r.h):
            for i in range(1, r.w - 1):
                r.clear(i, j)
        for i in range(1, r.w - 1):
            r.set(i, 1, LEATH[2])

    M["coif_front"] = coif_front

    def helm_band(r, front=False):
        plate(r, TRIM, rivets=False)
        if r.h > 3:
            r.row(-2, TRIM[3])
        r.row(-1, TRIM[1])
        for i in range(1, r.w, 2):
            r.set(i, r.h - 2, TRIM[4])
        if front and lv >= 1:
            cx = r.w // 2
            r.glow(cx - 1, 1, GL); r.glow(cx, 1, lighten(GL, 0.3))

    M["helm_band"] = helm_band
    M["helm_band_front"] = lambda r: helm_band(r, True)

    def helm_top(r):
        plate(r, TRIM, rivets=False, shine=False)
        cx = r.w // 2
        r.col(cx, TRIM[4]); r.col(cx - 1, TRIM[3])

    M["helm_top"] = helm_top
    M["nasal"] = lambda r: (r.fill(TRIM[3]), r.col(0, TRIM[4]), r.col(-1, TRIM[1]) if r.w > 1 else None, r.noise(0.02))

    def surcoat(r, front):
        cloth(r, MAIN, period=4, base=2)
        trim_rows(r, ACC, 1)
        # laterais transparentes: a malha aparece dos lados
        for j in range(r.h):
            r.clear(0, j); r.clear(r.w - 1, j)
        cx = r.w // 2
        if front:
            # espadas cruzadas bordadas no peito
            for k in range(5):
                r.set(cx - 2 + k, 2 + k, ACC[3]); r.set(cx + 1 - k, 2 + k, ACC[3])
            r.set(cx - 1, 4, ACC[4]); r.set(cx, 4, ACC[4])
            if lit:
                r.glow(cx - 1, 4, GL); r.glow(cx, 4, lighten(GL, 0.3))
        else:
            for j in range(2, 7):
                r.set(cx - 1, j, ACC[2]); r.set(cx, j, ACC[3])
            for i in range(cx - 2, cx + 2):
                r.set(i, 3, ACC[3])

    M["surcoat_front"] = lambda r: surcoat(r, True)
    M["surcoat_back"] = lambda r: surcoat(r, False)

    def flap(r, front):
        cloth(r, MAIN, period=3, base=2, top=0.04, bottom=0.12)
        trim_rows(r, ACC, 2)
        r.col(0, ACC[2]); r.col(-1, ACC[2])
        if lv >= 2:
            for k, i in enumerate(range(1, r.w - 2, 4)):
                rune_glyph(r, i, r.h - 7, GL, k + (0 if front else 2))

    M["flap_front"] = lambda r: flap(r, True)
    M["flap_back"] = lambda r: flap(r, False)
    M["flap_in"] = lambda r: cloth(r, MAIN, period=4, base=1, folds=False)
    M["flap_edge"] = lambda r: (r.fill(ACC[2]), r.noise(0.03))

    def cape_out(r):
        cloth(r, MAIN, period=4, base=2, top=0.06, bottom=0.14)
        r.col(0, ACC[2]); r.col(-1, ACC[2])
        trim_rows(r, ACC, 2)
        cx = r.w // 2
        # leao/espada nas costas: espada vertical com guarda
        for j in range(3, min(r.h - 4, 12)):
            r.set(cx - 1, j, ACC[3]); r.set(cx, j, ACC[2])
        for i in range(cx - 3, cx + 3):
            r.set(i, 5, ACC[3])
        if lv >= 2:
            sigil(r, cx - 1 + (r.w % 2), r.h - 8, GL)
        if lv >= 3:
            for j in range(3, r.h - 4, 3):
                r.glow(1, j, GL); r.glow(r.w - 2, j, GL)
        if lv >= 1:
            r.glow(cx - 1, 5, GL); r.glow(cx, 5, GL)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (cloth(r, MAIN, period=4, base=1, folds=False), trim_rows(r, ACC, 1))
    M["cape_edge"] = lambda r: (r.fill(ACC[2]), r.noise(0.03))

    def bracer(r):
        leather(r, LEATH, seams=False)
        r.row(0, TRIM[3]); r.row(-1, TRIM[1])
        for j in range(1, r.h - 1, 2):
            r.set(r.w // 2, j, LEATH[3])

    M["bracer"] = bracer
    M["spaulder"] = lambda r: plate(r, TRIM, rivets=True)
    M["spaulder_top"] = lambda r: plate(r, TRIM, rivets=False, ridge=True)
    M["trousers"] = lambda r: (cloth(r, pal("2A1A14", "42281E", "5E3A2A", "7A4E38", "9A6A4C"), period=3, base=2,
                                     top=0.04, bottom=0.10))

    def kneecop(r):
        plate(r, TRIM, rivets=False)
        r.set(r.w // 2, r.h // 2, TRIM[4])

    M["kneecop"] = kneecop
    M["boot"] = lambda r: (leather(r, LEATH, seams=False), r.row(-1, LEATH[0]))

    def boot_front(r):
        leather(r, LEATH, seams=False)
        r.row(-1, LEATH[0])
        cx = r.w // 2
        for j in range(1, r.h - 1, 2):
            r.set(cx - 1, j, LEATH[3]); r.set(cx, j, LEATH[3])

    M["boot_front"] = boot_front
    M["boot_cuff"] = lambda r: (r.fill(LEATH[3]), r.row(0, LEATH[4]), r.row(-1, LEATH[1]), r.noise(0.04))
    M["toecap"] = lambda r: plate(r, TRIM, rivets=False, shine=False)

    def crest(r):
        for i in range(r.w):
            for j in range(r.h):
                r.set(i, j, MAIN[3] if (i + j) % 3 == 0 else (MAIN[2] if (i + j) % 3 == 1 else MAIN[1]))
        r.vshade(0.15, 0.1)

    M["crest"] = crest
    M["horn"] = lambda r: (r.fill(ACC[2]), r.col(0, ACC[3]), r.row(0, ACC[4]), r.noise(0.03))
    if lv >= 1:
        M["o_paul_side"] = lambda r: (plate(r, T["METAL"], rivets=True), [r.set(i, r.h - 1, MAIN[2]) for i in range(r.w)])
    return M


def warrior_model(name, lv):
    m = Model(name)
    skeleton(m)
    # coifa de malha (rosto aberto) + bacinete com aro de rebites + protetor nasal
    m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="coif_front", s="chain", ew="chain", u="chain", d=OMIT), inflate=0.5)
    m.box(HD, [-4, 28.6, -4], [8, 3.4, 8], fc(side="helm_band", n="helm_band_front", u="helm_top", d=OMIT), inflate=0.85)
    m.box(HD, [-3.2, 32.4, -3.2], [6.4, 1.1, 6.4], fc(side="helm_top", u="helm_top", d=OMIT))
    m.box(HD, [-0.55, 25.4, -5.05], [1.1, 3.6, 0.5], fc(all="nasal"))
    if lv == 1:
        m.box(HD, [-0.5, 33.4, -3.6], [1, 1.3, 7.2], fc(side="crest", u="crest@1x7", d=OMIT))
    if lv >= 2:
        # crista de crina que balanca (pluma) e cai atras do elmo
        m.bone("hatMid", [0, 33.5, 0], parent=HD)
        m.box("hatMid", [-0.6, 33.4, -3.8], [1.2, 1.8 + 0.5 * lv, 7.6], fc(side="crest", u="crest@1x8", d=OMIT))
        m.bone("hatTip", [0, 35.0, 3.6], parent="hatMid")
        m.box("hatTip", [-0.55, 30.2, 3.4], [1.1, 4.8, 1.2], fc(side="crest", u="crest@1x1", d="crest@1x1"),
              rot=[22, 0, 0], pivot=[0, 35.0, 3.6])
    if lv == 3:
        # chifres de aco do Senhor da Guerra
        def horns():
            p = m.seg(HD, [-4.6, 30.6, -0.4], (-1, 0.35, -0.1), 1.8, 1.3, fc(side="horn", u="horn@1x1", d="horn@1x1"))
            p = m.seg(HD, p, (-0.35, 1, -0.25), 2.0, 1.0, fc(side="horn", u="horn@1x1", d="horn@1x1"), overlap=0.3)
            gem(m, HD, m.seg(HD, p, (0.2, 1, 0), 1.2, 0.6, fc(side="horn", u="horn@1x1", d=OMIT), overlap=0.2), 0.6,
                "o_gem@1x1")
        v9_sym(m, HD, HD, horns)
    if lv >= 1:
        gem(m, HD, [0, 30.3, -5.05], [1.2, 1.2, 0.5])

    # cota de malha + sobreveste vermelha (a malha aparece nas laterais) + cinto
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(side="chain", u="chain", d="dark@2x2"), inflate=0.55)
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="surcoat_front", s="surcoat_back", ew=OMIT, u=OMIT, d=OMIT), inflate=0.8)
    m.box(BD, [-4, 12.2, -2], [8, 1.6, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=1.0)
    if lv >= 1:
        gem(m, BD, [0, 13.0, -3.15], [1.3, 1.3, 0.5])
    waist(m, "Chest")
    skirt(m, "Chest", 7.5, "chain_skirt", "chain_skirt", "chain_skirt", "chain_dark@4x4", "chain_skirt",
          flare=4, depth=0.6, width=9.4)
    m.box("chestSkirtFront", [-2.6, 5.0, -3.55], [5.2, 7.0, 0.45], fc(n="flap_front", s="flap_in", ew="flap_edge@1x7",
                                                                       u=OMIT, d="flap_edge@2x1"),
          rot=[-4, 0, 0], pivot=[0, 12, -2.75])
    m.box("chestSkirtBack", [-2.6, 5.0, 3.1], [5.2, 7.0, 0.45], fc(s="flap_back", n="flap_in", ew="flap_edge@1x7",
                                                                    u=OMIT, d="flap_edge@2x1"),
          rot=[4, 0, 0], pivot=[0, 12, 2.75])
    cape(m, 24.0, v9_cape_lengths(lv, 5.5), 3.0, 9.0, out="cape_out", inn="cape_in", edge="cape_edge", flare=4)

    # mangas de malha + bracadeiras; ombreira simples no grau I, ombreiras de placas nos graus altos
    m.box(RA, [-8, 15.5, -2], [4, 8.5, 4], fc(side="chain", u="chain", d=OMIT), inflate=0.5)
    m.box(RA, [-8, 12, -2], [4, 4.2, 4], fc(side="bracer", u="leather_top@2x2", d="dark@2x2"), inflate=0.7)
    if lv == 0:
        m.box(RA, [-8.9, 21.6, -2.8], [5.0, 2.4, 5.6], fc(side="spaulder", u="spaulder_top", d="dark@2x2"),
              rot=[0, 0, -12], pivot=[-6.5, 23, 0])
    if lv >= 2:
        m.box(RA, [-8, 15.0, -2], [4, 0.9, 4], fc(side="o_rune_band", u="o_metal_flat@2x2", d="o_metal_flat@2x2"),
              inflate=0.75)
    m.mirror(RA, LA)
    if lv >= 1:
        pauldrons(m, lv, gem_mat="o_gem@2x2" if lv >= 2 else None, gem_size=1.3, fin="o_fin" if lv == 3 else None,
                  spikes=3 if lv == 3 else 0, spike_len=1.8, spike_tip="o_gem@1x1" if lv == 3 else None)

    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.box(RL, [-3.5, 4.4, -2.85], [3.2, 2.4, 0.9], fc(n="kneecop", ew="kneecop", u="trim_flat@2x1", d="trim_flat@2x1",
                                                    s=OMIT))
    m.mirror(RL, LL)

    m.box(RB, [-3.9, 0, -2], [4, 5, 4], fc(side="boot", n="boot_front", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(RB, [-3.9, 4.4, -2], [4, 1.4, 4], fc(side="boot_cuff", u="dark@2x2", d="dark@2x2"), inflate=0.8)
    m.box(RB, [-3.6, -0.5, -3.3], [3.4, 2.0, 1.4], fc(n="toecap", ew="toecap", u="toecap", d="sole@2x2", s=OMIT))
    if lv == 3:
        m.seg(RB, [-1.9, 5.6, -3.0], (0, 0.55, -1), 1.5, 0.8, fc(side="o_spike", u="o_spike@1x1", d=OMIT))
    m.mirror(RB, LB)
    return m


# ------------------------------------------------------------------ PALADINO: placas brancas e douradas, tabardo com sol
# I Escudeiro da Luz · II Cruzado da Aurora (prata e azul) · III Templario Solar (ouro e carmesim) · IV Campeao Celestial
PALADIN_T = {
    "base": theme(pal("8A8C96", "B4B8C2", "D8DCE4", "F0F2F6", "FFFFFF"), pal("8A7A5A", "B8A880", "DCD0AE", "F0E8D0", "FFFAEC"),
                  GOLD_R, LEATHER_STD, STEEL_P, GOLD_R, pal("6A4A04", "C08A10", "FFD24A", "FFF4C0"), "FFD95A"),
    "adept": theme(pal("8A90A0", "B8BECC", "DCE2EC", "F0F4FA", "FFFFFF"), pal("1E3A6A", "2E5494", "4474C0", "6E9AE0", "A8C8FF"),
                   SILVER_M, LEATHER_STD, SILVER_M, SILVER_M, pal("0A3A6A", "1E6AB0", "9AD8FF", "E0F6FF"), "9AD8FF"),
    "master": theme(pal("8A8C96", "B4B8C2", "D8DCE4", "F0F2F6", "FFFFFF"), pal("4A0A14", "7A1424", "A8203A", "D0405A", "F07A8A"),
                    GOLD_B, pal("2E1C10", "4A2E1A", "6A4426", "8C5C34", "AE7A4A"), GOLD_R, GOLD_B,
                    pal("6A4004", "C08010", "FFD95A", "FFF6C8"), "FFD95A"),
    "archmage": theme(pal("A89870", "D0C49C", "F0E8CC", "FFF8E6", "FFFFFF"), pal("A08A50", "C8B478", "E8DCA8", "FFF4D2", "FFFFF0"),
                      GOLD_B, pal("3A2A14", "5A4424", "7A6036", "9A7C4A", "BA9A64"), GOLD_B, GOLD_B,
                      pal("8A6A10", "E0B030", "FFF2A0", "FFFFF0"), "FFF2A0"),
}


def paladin_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]
    SUN = (GEM[2], TRIM[3])

    def helm_front(r):
        plate(r, MAIN, TRIM, rivets=False, shine=True)
        cx = r.w // 2
        # cruz dourada com fenda dos olhos (T) e furos de respiro
        for j in range(2, r.h - 1):
            r.set(cx - 1, j, TRIM[3]); r.set(cx, j, TRIM[2])
        for i in range(1, r.w - 1):
            if i not in (cx - 1, cx):
                r.set(i, 3, H("101014"))
        for (i, j) in ((cx - 3, 6), (cx + 2, 6), (cx - 3, 7), (cx + 2, 7)):
            r.set(i, j, darken(MAIN[1], 0.4))

    M["helm_front"] = helm_front
    M["helm_side"] = lambda r: plate(r, MAIN, TRIM, rivets=True)
    M["helm_top"] = lambda r: plate(r, MAIN, TRIM, rivets=False, ridge=True)

    def sun_mat(r):
        r.fill(TRIM[2])
        r.row(0, TRIM[3]); r.col(0, TRIM[3]); r.row(-1, TRIM[1]); r.col(-1, TRIM[1])
        sun(r, r.w // 2, r.h // 2, SUN[0], TRIM[4], True, big=r.w >= 7)

    M["sun"] = sun_mat

    def cuirass(r, kind):
        plate(r, MAIN, TRIM, rivets=kind != "front", ridge=kind == "front")
        if kind == "front":
            r.row(5, TRIM[2])
        elif kind == "back" and r.w >= 6:
            sun(r, r.w // 2, 4, TRIM[3], TRIM[2], lv >= 1, big=False)

    M["cuirass_front"] = lambda r: cuirass(r, "front")
    M["cuirass_back"] = lambda r: cuirass(r, "back")
    M["cuirass_side"] = lambda r: cuirass(r, "side")
    M["plate_top"] = lambda r: plate(r, MAIN, TRIM, rivets=False)

    def tabard(r, front, sun_y=None):
        cloth(r, SEC, period=4, base=2, top=0.06, bottom=0.10)
        r.col(0, TRIM[2]); r.col(-1, TRIM[2])
        trim_rows(r, TRIM, 2)
        if sun_y is not None and r.w >= 6:
            sun(r, r.w // 2, sun_y, SUN[0], TRIM[3], True, big=True)

    def tabard_body(r, front):
        tabard(r, front, sun_y=5 if front else None)
        for j in range(r.h):
            r.clear(0, j); r.clear(1, j); r.clear(r.w - 1, j); r.clear(r.w - 2, j)
        if not front and r.w >= 6:
            for j in range(3, 8):
                r.set(r.w // 2 - 1, j, TRIM[3]); r.set(r.w // 2, j, TRIM[2])

    M["tabard_front"] = lambda r: tabard_body(r, True)
    M["tabard_back"] = lambda r: tabard_body(r, False)

    def tabard_flap(r, front):
        tabard(r, front)
        cx = r.w // 2
        for j in range(1, r.h - 3):
            r.set(cx - 1, j, TRIM[3]); r.set(cx, j, TRIM[2])
        for i in range(cx - 2, cx + 2):
            r.set(i, 2, TRIM[3])
        if lv >= 2:
            for k, i in enumerate(range(1, r.w - 2, 4)):
                rune_glyph(r, i, r.h - 7, GL, k)

    M["tabard_flap_front"] = lambda r: tabard_flap(r, True)
    M["tabard_flap_back"] = lambda r: tabard_flap(r, False)
    M["tabard_in"] = lambda r: cloth(r, SEC, period=4, base=1, folds=False)
    M["tabard_edge"] = lambda r: (r.fill(TRIM[2]), r.noise(0.03))
    M["paul_side"] = lambda r: plate(r, MAIN, TRIM, rivets=True)
    M["paul_top"] = lambda r: (plate(r, MAIN, TRIM, rivets=False), sun(r, r.w // 2, r.h // 2, TRIM[3], TRIM[2], False,
                                                                       big=False) if r.w >= 5 and r.h >= 5 else None)
    M["o_paul_side"] = lambda r: plate(r, MAIN, TRIM, rivets=True)
    M["o_paul_top"] = lambda r: (plate(r, MAIN, TRIM, rivets=False), sun(r, r.w // 2, r.h // 2, SUN[0], TRIM[3], True,
                                                                         big=False) if r.w >= 5 and r.h >= 5 else
                                 r.glow(r.w // 2, r.h // 2, SUN[0]))
    M["arm_plate"] = lambda r: plate(r, MAIN, TRIM, rivets=False, edge="top")
    M["vambrace"] = lambda r: (plate(r, MAIN, TRIM, rivets=True), r.row(1, TRIM[2]) if r.h > 3 else None)
    M["mail"] = lambda r: chainmail(r, ACC)
    M["gorget"] = lambda r: (plate(r, MAIN, TRIM, rivets=False), r.row(r.h // 2, TRIM[3]))
    M["kneecop"] = lambda r: (plate(r, TRIM, rivets=False), r.set(r.w // 2, r.h // 2, SUN[0]))
    M["greave"] = lambda r: (plate(r, MAIN, TRIM, rivets=False, ridge=True, edge="top"))
    M["sabaton"] = lambda r: (plate(r, MAIN, rivets=False, shine=False),
                              [r.set(i, 0, TRIM[3]) for i in range(r.w)])

    def cape_out(r):
        cloth(r, MAIN, period=4, base=3, top=0.06, bottom=0.10)
        r.col(0, TRIM[2]); r.col(-1, TRIM[2])
        trim_rows(r, TRIM, 2)
        if r.w >= 7:
            sun(r, r.w // 2, 6, SUN[0], TRIM[3], True, big=True)
        if lv >= 3:
            for k, i in enumerate(range(1, r.w - 2, 4)):
                rune_glyph(r, i, r.h - 8, GL, k)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (cloth(r, SEC, period=4, base=2, folds=False), trim_rows(r, TRIM, 1))
    M["cape_edge"] = lambda r: (r.fill(TRIM[2]), r.noise(0.03))

    def wing(r):
        # asinha dourada (frente/verso iguais): penas em degraus que brilham na ponta
        shape = ["..WWWW", ".WWGGW", "WWGGW.", "WGGW..", "GW...."]
        for j, row in enumerate(shape):
            for i, ch in enumerate(row):
                if ch == "W":
                    r.set(i, j, TRIM[4] if j == 0 else TRIM[3])
                elif ch == "G":
                    r.glow(i, j, GL)

    M["o_wing"] = wing

    def big_feather(r):
        feather(r, MAIN, tip=GL, lit=True)
        r.col(0, TRIM[2])

    M["o_feather"] = big_feather
    return M


def paladin_model(name, lv):
    m = Model(name)
    skeleton(m)
    # elmo fechado branco com cruz dourada, crista e o SOL na testa (brilha ja no grau I)
    m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="helm_front", ew="helm_side", s="helm_side", u="helm_top", d=OMIT),
          inflate=0.9)
    m.box(HD, [-0.5, 32.8, -4.4], [1, 0.9 + 0.3 * lv, 8.8], fc(all="trim_plate@1x4"))
    s = 3.2 + 0.4 * min(lv, 2)
    m.box(HD, [-s / 2, 30.6 - s / 2 + 0.6, -5.3], [s, s, 0.5], fc(n="sun@7x7", s=OMIT, ew="trim_flat@1x2",
                                                                  u="trim_flat@2x1", d="trim_flat@2x1"))
    if lv >= 2:
        # asas douradas dos lados do elmo
        v9_sym(m, HD, HD, lambda: m.box(HD, [-5.0, 28.0, -1.5], [0, 4.6, 5.4], fc(ew="o_wing@6x5"),
                                        rot=[0, 18, 0], pivot=[-4.9, 28, 0]))
    if lv == 3:
        # aureola de luz atras da cabeca
        bead_ring(m, HD, [0, 29.0, 5.4], 5.0, 14, 0.75, "o_star@1x1")

    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="cuirass_front", s="cuirass_back", ew="cuirass_side", u="plate_top",
                                         d="dark@2x2"), inflate=0.85)
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="tabard_front", s="tabard_back", ew=OMIT, u=OMIT, d=OMIT), inflate=1.05)
    m.box(BD, [-4.3, 22.6, -2.3], [8.6, 1.5, 4.6], fc(side="gorget", u="trim_flat@2x2", d=OMIT))
    m.box(BD, [-4, 12.1, -2], [8, 1.5, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=1.15)
    if lv >= 1:
        gem(m, BD, [0, 23.35, -2.6], [1.3, 1.1, 0.5])
    waist(m, "Chest")
    for bone, z0, sgn, mat in (("chestSkirtFront", -3.55, -1, "tabard_flap_front"),
                               ("chestSkirtBack", 3.1, 1, "tabard_flap_back")):
        faces = fc(n=mat if sgn < 0 else "tabard_in", s=mat if sgn > 0 else "tabard_in", ew="tabard_edge@1x8",
                   u=OMIT, d="tabard_edge@2x1")
        m.box(bone, [-2.9, 3.0, z0], [5.8, 9.0, 0.45], faces, rot=[4 * sgn, 0, 0], pivot=[0, 12, 2.75 * sgn])
    if lv >= 1:
        cape(m, 24.0, {1: 6.0, 2: 4.0, 3: 2.0}[lv], 3.15, 9.2, out="cape_out", inn="cape_in", edge="cape_edge", flare=4)
    if lv == 3:
        # asas de penas nas costas (fixas no tronco, por cima da capa)
        def wings():
            for k, (ln, ang) in enumerate(((7.5, 28), (6.2, 48), (5.0, 68))):
                m.box(BD, [-1.2 - ln, 19.5 - k * 0.6, 3.9 + k * 0.15], [ln, 1.6, 0.4],
                      fc(ns="o_feather@8x2", ew="o_metal_flat@1x1", u="o_metal_flat@4x1", d="o_metal_flat@4x1"),
                      rot=[0, 0, -ang + 50], pivot=[-1.2, 20.3 - k * 0.6, 4.0])
        v9_sym(m, BD, BD, wings)

    m.box(RA, [-8, 16.5, -2], [4, 7.5, 4], fc(side="arm_plate", u="plate_top@2x2", d=OMIT), inflate=0.6)
    m.box(RA, [-8, 12, -2], [4, 4.6, 4], fc(side="vambrace", u="trim_flat@2x2", d="dark@2x2"), inflate=0.75)
    m.box(RA, [-8.2, 15.8, -1.4], [0.8, 1.6, 2.8], fc(all="trim_plate@1x2"))
    if lv >= 2:
        m.box(RA, [-8, 13.6, -2], [4, 0.8, 4], fc(side="o_rune_band", u="o_metal_flat@2x2", d="o_metal_flat@2x2"),
              inflate=0.9)
    m.mirror(RA, LA)
    if lv == 0:
        pauldrons(m, 1, side="paul_side", top="paul_top", under="dark@2x2")
    else:
        pauldrons(m, min(3, lv + 1), gem_mat="o_gem@2x2", gem_size=1.2 + 0.2 * lv, fin="o_fin" if lv >= 2 else None)

    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="mail", u=OMIT, d=OMIT), inflate=0.3)
    m.box(RL, [-3.9, 6.2, -2], [4, 5.8, 4], fc(side="greave", u=OMIT, d="dark@2x2"), inflate=0.55)
    m.box(RL, [-3.45, 4.3, -2.95], [3.1, 2.5, 1.0], fc(n="kneecop", ew="trim_plate@1x2", u="trim_flat@2x1",
                                                     d="trim_flat@2x1", s=OMIT))
    m.mirror(RL, LL)

    m.box(RB, [-3.9, 0, -2], [4, 5.2, 4], fc(side="greave", u=OMIT, d="sole@2x2"), inflate=0.6)
    m.box(RB, [-3.9, 4.8, -2], [4, 0.9, 4], fc(side="trim_plate@2x1", u="dark@2x2", d="dark@2x2"), inflate=0.85)
    m.box(RB, [-3.6, -0.55, -3.5], [3.4, 1.8, 1.6], fc(n="sabaton", ew="sabaton", u="sabaton", d="sole@2x2", s=OMIT))
    if lv >= 1:
        gem(m, RB, [-1.9, 2.6, -2.75], [1.0, 1.0, 0.4])
    if lv == 3:
        m.box(RB, [-4.65, 2.6, -0.9], [0, 2.4, 3.2], fc(ew="o_wing@6x5"))
    m.mirror(RB, LB)
    return m


# ------------------------------------------------------------------ BARBARO: peles, ossos, ombreira de cranio
# I Pele de Lobo · II Saqueador (lobo cinzento e ferro) · III Chefe Tribal (lobo branco, ouro, pintura) · IV Furia Ancestral
BARBARIAN_T = {
    "base": theme(pal("2E2014", "4A3420", "6A4C30", "8C6A46", "B08C64"), pal("2A1810", "42281A", "5E3A26", "7A5034", "986A48"),
                  IRON_R, LEATHER_STD, pal("564F40", "817862", "ABA286", "D0C8AC", "EEE8D2"), IRON_R,
                  pal("5A0A04", "A0200A", "FF6A2A", "FFC8A0"), "FF6A2A"),
    "adept": theme(pal("1E1E22", "34343A", "4E4E56", "6E6E78", "9A9AA4"), pal("2A1810", "42281A", "5E3A26", "7A5034", "986A48"),
                   pal("1E1E22", "34343C", "50505A", "747480", "A0A0AC"), LEATHER_STD,
                   pal("564F40", "817862", "ABA286", "D0C8AC", "EEE8D2"), pal("1E1E22", "34343C", "50505A", "747480", "A0A0AC"),
                   pal("5A1404", "A8300A", "FF6A2A", "FFD0A8"), "FF6A2A"),
    "master": theme(pal("8A8A86", "B4B2AC", "D6D4CE", "ECEAE4", "FFFFFA"), pal("3A0A08", "621410", "8C2018", "B03A2A", "D06A50"),
                    GOLD_R, LEATHER_STD, pal("5E5540", "8A8064", "B4AA8A", "D8D0B2", "F2ECD8"), GOLD_R,
                    pal("4A0404", "A01010", "E8302A", "FFB0A0"), "E8302A"),
    "archmage": theme(pal("0C0A0A", "1A1414", "2A2020", "3E2E2A", "5A4238"), pal("1A0A06", "2E140C", "461E12", "602A18", "7E3A20"),
                      pal("5A2A04", "9A520A", "D88A18", "FFB83A", "FFE89A"), LEATHER_DARK,
                      pal("2A2420", "4A4038", "6E6254", "948670", "B8AA90"), pal("5A2A04", "9A520A", "D88A18", "FFB83A", "FFE89A"),
                      pal("5A2002", "C05A0A", "FF9A1E", "FFE0A0"), "FF9A1E"),
}


def barbarian_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]
    M["fur"] = lambda r: fur(r, MAIN)
    M["fur_ragged"] = lambda r: fur(r, MAIN, ragged=2, seed=31)

    def pelt_face(r):
        fur(r, MAIN)
        for j in range(3, r.h):
            for i in range(1, r.w - 1):
                r.clear(i, j)

    M["pelt_face"] = pelt_face

    def wolf_front(r):
        fur(r, MAIN)
        # olhos do lobo (vazios no grau I, brasas nos graus altos)
        for i in (1, r.w - 2):
            if lv >= 2:
                r.glow(i, r.h // 2, GL)
            else:
                r.set(i, r.h // 2, H("140C08"))

    M["wolf_front"] = wolf_front

    def snout_front(r):
        fur(r, MAIN)
        cx = r.w // 2
        r.set(cx - 1, 0, H("120C0A")); r.set(cx, 0, H("120C0A"))
        for i in range(r.w):
            if i % 2 == 0:
                r.set(i, r.h - 1, ACC[4])

    M["snout_front"] = snout_front
    M["horn"] = lambda r: (r.fill(ACC[3]), r.col(0, ACC[4]), r.col(-1, ACC[2]) if r.w > 1 else None,
                           r.row(-1, ACC[1]) if r.h > 2 else None, r.noise(0.04))
    M["horn_tip"] = (lambda r: [r.glow(i, j, GL) for j in range(r.h) for i in range(r.w)]) if lv >= 3 else M["horn"]

    def vest(r, front):
        leather(r, SEC, seams=False)
        if front:
            # colete aberto: peito nu no meio, so as tiras cruzadas
            for j in range(r.h):
                for i in range(2, r.w - 2):
                    r.clear(i, j)
        r.row(0, MAIN[3])
        if lv >= 2:
            # pintura de guerra que brilha
            for j in range(1, r.h - 1, 3):
                r.glow(0 if front else 2, j, GL)
                r.glow(r.w - 1 if front else r.w - 3, j, GL)

    M["vest_front"] = lambda r: vest(r, True)
    M["vest_back"] = lambda r: vest(r, False)
    M["vest_side"] = lambda r: leather(r, SEC, seams=False)

    def bone_band(r):
        r.fill(ACC[3])
        for i in range(0, r.w, 2):
            r.set(i, 0, ACC[4])
            r.set(i, r.h - 1, ACC[1]) if r.h > 1 else None
        if lv >= 3:
            for i in range(1, r.w, 3):
                r.glow(i, r.h // 2, GL)
        r.noise(0.03)

    M["bone_band"] = bone_band
    M["bone"] = lambda r: bone(r, ACC)

    def skull(r, face):
        bone(r, ACC)
        if face and r.w >= 4 and r.h >= 4:
            cx = r.w // 2
            ey = r.h // 2 - 1
            for i in (cx - 2, cx + 1):
                if lv >= 3:
                    r.glow(i, ey, GL); r.glow(i + 1, ey, GL)
                else:
                    r.set(i, ey, H("16100C")); r.set(i + 1, ey, H("16100C"))
            r.set(cx, ey + 2, ACC[1]); r.set(cx - 1, ey + 2, ACC[1])
            for i in range(cx - 2, cx + 2):
                r.set(i, r.h - 1, ACC[4] if i % 2 else ACC[1])

    M["skull_face"] = lambda r: skull(r, True)
    M["skull"] = lambda r: skull(r, False)
    M["fur_cuff"] = lambda r: fur(r, MAIN)
    M["hide_trousers"] = lambda r: (leather(r, SEC, seams=False), r.folds(SEC[1], SEC[3], 4))

    def loin(r):
        fur(r, MAIN, ragged=2, seed=77)
        r.row(0, LEATH[3])

    M["loin"] = loin
    M["loin_in"] = lambda r: (leather(r, LEATH, seams=False), ragged_bottom(r, 2, 77))
    M["fur_boot"] = lambda r: fur(r, MAIN)

    def fang_row(r):
        r.fill(LEATH[1])
        for i in range(0, r.w, 2):
            r.set(i, r.h - 1, ACC[4])
        r.noise(0.03)

    M["fang_row"] = fang_row
    M["fang"] = lambda r: (r.fill(ACC[3]), r.row(0, ACC[4]), r.noise(0.03))

    def cape_out(r):
        fur(r, MAIN, ragged=3, seed=91)
        if lv >= 2:
            cx = r.w // 2
            for (i, j) in ((cx - 2, 3), (cx - 1, 4), (cx, 4), (cx + 1, 3), (cx - 1, 6), (cx, 6)):
                r.glow(i, j, GL)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (leather(r, SEC, seams=False), ragged_bottom(r, 3, 91))
    M["cape_edge"] = lambda r: fur(r, MAIN)
    M["o_paul_side"] = lambda r: fur(r, MAIN)
    M["o_paul_top"] = lambda r: (fur(r, MAIN), r.glow(r.w // 2, r.h // 2, GEM[2]))
    return M


def barbarian_model(name, lv):
    m = Model(name)
    skeleton(m)
    # capuz de pele de lobo: cabeca do lobo por cima, focinho com presas, orelhas e chifres de osso
    m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="pelt_face", s="fur", ew="fur", u="fur", d=OMIT), inflate=0.7)
    m.box(HD, [-3.0, 31.6, -4.6], [6, 2.6, 6.2], fc(n="wolf_front", s="fur", ew="fur", u="fur", d="fur"))
    m.box(HD, [-1.8, 31.4, -7.0], [3.6, 2.0, 2.5], fc(n="snout_front", ew="fur", u="fur", d="fang_row@3x2", s=OMIT))
    hs = 1.0 + 0.25 * lv

    def head_sym():
        m.box(HD, [-2.8, 34.1, -2.6], [1.3, 1.6, 0.9], fc(all="fur@1x2"), rot=[0, 0, 12], pivot=[-2.1, 34.1, -2.1])
        p = m.seg(HD, [-4.5, 30.2, -0.5], (-1, 0.45, -0.2), 2.0 * hs, 1.25, fc(side="horn", u="horn@1x1", d="horn@1x1"))
        p = m.seg(HD, p, (-0.3, 1, -0.35), 2.0 * hs, 0.95, fc(side="horn", u="horn@1x1", d="horn@1x1"), overlap=0.3)
        m.seg(HD, p, (0.2, 1, 0.1), 1.3 * hs, 0.6, fc(side="horn_tip@1x2", u="horn_tip@1x1", d=OMIT), overlap=0.2)

    v9_sym(m, HD, HD, head_sym)
    if lv >= 1:
        gem(m, HD, [0, 33.0, -4.85], [1.1, 1.1, 0.5])

    # colete de couro aberto (peito nu), manto de pele nos ombros, tiras cruzadas com ossos, cinto com cranio
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="vest_front", s="vest_back", ew="vest_side", u=OMIT, d=OMIT), inflate=0.5)
    m.box(BD, [-4.7, 20.6, -2.7], [9.4, 3.8, 5.4], fc(n="fur_ragged", s="fur_ragged", ew="fur_ragged", u="fur",
                                                      d="dark@2x2"))
    strap_diag(m, BD, "strap", -2.65, 1)
    strap_diag(m, BD, "strap", -2.65, -1)
    for (x, y) in ((-2.0, 20.0), (2.0, 20.0), (0.0, 17.2)):
        gem(m, BD, [x, y, -3.05], [1.0, 0.7, 0.4], "bone@2x1")
    m.box(BD, [-4, 12.0, -2], [8, 2.0, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=0.9)
    m.box(BD, [-1.2, 11.7, -3.4], [2.4, 2.4, 0.9], fc(n="skull_face@4x4", ew="skull@1x2", u="skull@2x1", d="skull@2x1",
                                                    s=OMIT))
    if lv >= 1:
        m.box(BD, [-3.6, 22.3, -2.95], [7.2, 0.9, 0.5], fc(n="fang_row@8x2", ew="fang@1x1", u="fang@4x1", d=OMIT))
    if lv >= 2:
        cape(m, 24.0, {2: 8.0, 3: 4.0}[lv], 2.8, 9.4, out="cape_out", inn="cape_in", edge="cape_edge", flare=5,
             thick=0.8)

    # bracos nus: braceletes de osso e punhos de pele
    m.box(RA, [-8, 12.4, -2], [4, 2.6, 4], fc(side="fur_cuff", u="fur@2x2", d="dark@2x2"), inflate=0.75)
    m.box(RA, [-8, 15.6, -2], [4, 0.9, 4], fc(side="bone_band", u="bone@2x2", d="bone@2x2"), inflate=0.55)
    if lv >= 2:
        m.box(RA, [-8, 17.4, -2], [4, 0.9, 4], fc(side="bone_band", u="bone@2x2", d="bone@2x2"), inflate=0.5)
    m.mirror(RA, LA)
    # ombreira de CRANIO no ombro direito (olhando para fora) e almofada de pele no esquerdo
    m.box(RA, [-9.5, 22.0, -2.2], [4.4, 3.6, 4.4], fc(e="skull_face@6x6", side="skull", u="skull", d="dark@2x2"),
          rot=[0, 0, -8], pivot=[-7.3, 23.8, 0])
    hl = 1.2 + 0.3 * lv
    for z in (-1.6, 1.6):
        p = m.seg(RA, [-9.0, 25.2, z], (-0.4, 1, 0.25 * z), hl, 0.6, fc(side="horn", u="horn@1x1", d="horn@1x1"))
        m.seg(RA, p, (0.25, 1, 0.1 * z), hl * 0.6, 0.4, fc(side="horn_tip@1x1", u="horn_tip@1x1", d=OMIT), overlap=0.15)
    m.box(LA, [4.4, 21.4, -2.6], [4.4, 3.0, 5.2], fc(side="fur_ragged", u="fur", d="dark@2x2"), rot=[0, 0, 10],
          pivot=[6.5, 23, 0])
    if lv >= 1:
        # cranio menor no ombro esquerdo (olhando para fora = face oeste)
        m.box(LA, [5.0, 24.2, -1.8], [3.4, 2.2, 3.6], fc(side="skull", w="skull_face@4x4", u="skull", d="dark@2x2"),
              rot=[0, 0, 10], pivot=[6.5, 23, 0])

    # calca de couro cru + tanga de pele (frente e costas abrem com o passo)
    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="hide_trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(RL, LL)
    waist(m, "Legs")
    m.box("waistLegs", [-4, 10.6, -2], [8, 1.4, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.75)
    m.box("legsSkirtFront", [-2.9, 3.8, -3.15], [5.8, 8.2, 0.5], fc(n="loin", s="loin_in", ew="fur@1x6", u=OMIT,
                                                                     d=OMIT), rot=[-4, 0, 0], pivot=[0, 12, -2.75])
    m.box("legsSkirtBack", [-2.9, 4.6, 2.65], [5.8, 7.4, 0.5], fc(s="loin", n="loin_in", ew="fur@1x6", u=OMIT, d=OMIT),
          rot=[4, 0, 0], pivot=[0, 12, 2.75])

    # botas embrulhadas em pele com tiras
    m.box(RB, [-3.9, 0, -2], [4, 6, 4], fc(side="fur_boot", u=OMIT, d="sole@2x2"), inflate=0.7)
    for y in (1.6, 3.9):
        m.box(RB, [-3.9, y, -2], [4, 0.6, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.85)
    m.box(RB, [-3.6, -0.6, -3.4], [3.4, 1.6, 1.5], fc(n="fur", ew="fur", u="fur", d="sole@2x2", s=OMIT))
    if lv >= 2:
        m.seg(RB, [-1.9, 5.8, -3.0], (0, 0.5, -1), 1.3, 0.7, fc(side="fang", u="fang@1x1", d=OMIT))
    m.mirror(RB, LB)
    return m

# ------------------------------------------------------------------ MONGE: gi, faixa com pontas, contas de oracao, ataduras
# I Discipulo (acafrao) · II Disciplina do Vento (branco e verde-agua) · III Mestre do Templo (carmesim e ouro)
# IV Punho Celestial (branco celeste, ki que brilha)
MONK_T = {
    "base": theme(pal("6A2A04", "9A4406", "C86A10", "E8902A", "FFB85A"), pal("1A120C", "2C1E14", "42301E", "5A422C", "74583C"),
                  BRASS, pal("2E1C10", "4A2E1A", "6A4426", "8A5C34", "AA7A4A"), pal("8A8070", "B4AA98", "D8D0BE", "EEE8DA", "FFFCF2"),
                  BRASS, pal("0A4A44", "10847A", "3AD8C8", "B8FFF4"), "7AF0E0"),
    "adept": theme(pal("8A8E94", "B4B8BE", "D8DCE0", "EEF0F2", "FFFFFF"), pal("0A3A36", "10564F", "1A7A70", "2AA096", "5ACABC"),
                   SILVER_M, pal("2E1C10", "4A2E1A", "6A4426", "8A5C34", "AA7A4A"), pal("7A8078", "A8AEA4", "CCD2C8", "E6ECE2", "FAFFF6"),
                   SILVER_M, pal("0A4A44", "10847A", "7AF0E0", "D0FFF8"), "7AF0E0"),
    "master": theme(pal("4A0A0C", "74121A", "A01E26", "C83A3A", "E8706A"), pal("5A3A06", "8A5E10", "B88A1E", "E0B43A", "FFE08A"),
                    GOLD_R, pal("2A140C", "421E12", "5E2C1A", "7A3C24", "9A5232"), pal("8A7A60", "B4A488", "D8CAA8", "EEE2C8", "FFF8E8"),
                    GOLD_R, pal("5A3A04", "B07810", "FFC24A", "FFF0B8"), "FFC24A"),
    "archmage": theme(pal("6A7A90", "9AACC4", "C8D8EC", "E6F0FA", "FFFFFF"), pal("0A1A4A", "122A72", "1E40A0", "3060C8", "5A8CF0"),
                      GOLD_B, pal("1A1A2A", "2A2A42", "3E3E5E", "56567E", "74749E"), pal("8A96A8", "B4C0D0", "D8E2EE", "EEF4FA", "FFFFFF"),
                      GOLD_B, pal("0A4A5A", "1A8AA0", "9AF4FF", "E0FCFF"), "9AF4FF"),
}


def monk_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]

    def gi(r, kind):
        cloth(r, MAIN, period=4, base=2, top=0.08, bottom=0.12)
        if kind == "front":
            # golas cruzadas (esquerda sobre direita) com a camiseta clara no V
            cx = r.w // 2
            for j in range(r.h):
                a = cx - 3 + j // 2
                b = cx + 2 - j // 2
                if a < b:
                    for i in range(a + 1, b):
                        r.set(i, j, ACC[2])
                    r.set(b, j, SEC[3])
                r.set(a, j, SEC[2])
        elif kind == "back" and r.w >= 6:
            # circulo do templo bordado nas costas (vira ki nos graus altos)
            cx, cy = r.w // 2, 4
            for (dx, dy) in ((-1, -2), (0, -2), (-2, -1), (1, -1), (-2, 0), (1, 0), (-1, 1), (0, 1)):
                (r.glow if lv >= 2 else r.set)(cx + dx, cy + dy, GL if lv >= 2 else SEC[2])
        r.row(-1, SEC[1])

    M["gi_front"] = lambda r: gi(r, "front")
    M["gi_back"] = lambda r: gi(r, "back")
    M["gi_side"] = lambda r: gi(r, "side")
    M["gi_top"] = lambda r: (cloth(r, MAIN, period=4, base=3, folds=False), r.row(0, SEC[2]))

    def gi_skirt(r):
        cloth(r, MAIN, period=3, base=2, top=0.02, bottom=0.14)
        r.row(-1, SEC[2])

    M["gi_skirt"] = gi_skirt
    M["gi_skirt_in"] = lambda r: cloth(r, MAIN, period=4, base=1, folds=False)
    M["gi_skirt_edge"] = lambda r: (r.fill(MAIN[1]), r.row(-1, SEC[2]))

    def sash(r, front=False):
        cloth(r, SEC, period=2, base=2, folds=True, top=0.1, bottom=0.1)
        if lv >= 1:
            r.row(0, TRIM[2])
        if front and lv >= 2:
            r.glow(r.w // 2, r.h // 2, GL)

    M["sash"] = sash
    M["sash_front"] = lambda r: sash(r, True)
    M["sash_tail"] = lambda r: (cloth(r, SEC, period=2, base=2), trim_rows(r, TRIM, 1) if lv >= 1 else r.row(-1, SEC[3]))

    def headband(r, front=False):
        cloth(r, SEC, period=3, base=2, folds=False)
        r.row(0, SEC[3])
        if front:
            cx = r.w // 2
            r.set(cx - 1, r.h // 2, TRIM[3]); r.set(cx, r.h // 2, TRIM[2])
            if lv >= 1:
                r.glow(cx - 1, r.h // 2, GL)

    M["headband"] = headband
    M["headband_front"] = lambda r: headband(r, True)

    def bead(r):
        r.fill(LEATH[2])
        r.set(0, 0, LEATH[4])
        if r.w > 1:
            r.set(r.w - 1, r.h - 1, LEATH[0])

    M["bead"] = bead
    M["bead_big"] = (lambda r: gem_mat(GEM)(r)) if lv >= 1 else (lambda r: (r.fill(TRIM[2]), r.set(0, 0, TRIM[4])))
    M["wraps"] = lambda r: wraps(r, ACC)

    def gi_sleeve(r):
        cloth(r, MAIN, period=3, base=2)
        r.row(-1, SEC[2])

    M["gi_sleeve"] = gi_sleeve

    def gi_pants(r):
        cloth(r, MAIN, period=3, base=1, top=0.04, bottom=0.08)
        r.row(0, SEC[1])

    M["gi_pants"] = gi_pants
    M["sandal"] = lambda r: (r.fill(LEATH[1]), r.row(0, LEATH[3]), r.noise(0.03))

    def ki_wrap(r):
        wraps(r, ACC)
        if lv >= 3:
            for j in range(0, r.h, 3):
                r.glow((j // 3) % r.w, j, GL)

    M["fist_wrap"] = ki_wrap

    def cape_out(r):
        cloth(r, SEC, period=2, base=2, top=0.04, bottom=0.12)
        trim_rows(r, TRIM, 1)
        if lv >= 3:
            for j in range(2, r.h - 2, 4):
                r.glow(r.w // 2, j, GL)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: cloth(r, SEC, period=3, base=1, folds=False)
    M["cape_edge"] = lambda r: (r.fill(SEC[1]), r.noise(0.03))
    M["o_paul_side"] = lambda r: (cloth(r, MAIN, period=3, base=3, folds=False), r.row(-1, TRIM[2]), r.row(0, TRIM[3]))
    M["o_paul_top"] = lambda r: (cloth(r, MAIN, period=3, base=3, folds=False), r.glow(r.w // 2, r.h // 2, GL))
    return M


def monk_model(name, lv):
    m = Model(name)
    skeleton(m)
    # faixa na testa com no atras e duas pontas que esvoacam (hatMid)
    m.box(HD, [-4, 28.0, -4], [8, 1.5, 8], fc(side="headband", n="headband_front", u=OMIT, d=OMIT), inflate=0.6)
    m.box(HD, [-0.9, 27.8, 4.4], [1.8, 1.9, 0.9], fc(all="headband@2x2"))
    m.bone("hatMid", [0, 28.8, 4.9], parent=HD, rot=[12, 0, 0])
    tl = 4.5 + 1.2 * lv
    m.box("hatMid", [-1.5, 28.8 - tl, 4.7], [1.0, tl, 0.3], fc(ns="sash_tail@2x6", ew="headband@1x4", u=OMIT, d=OMIT),
          rot=[0, 0, -10], pivot=[-0.5, 28.8, 4.85])
    m.box("hatMid", [0.5, 28.8 - tl * 0.8, 4.7], [1.0, tl * 0.8, 0.3], fc(ns="sash_tail@2x5", ew="headband@1x4", u=OMIT,
                                                                         d=OMIT), rot=[0, 0, 12], pivot=[0.5, 28.8, 4.85])

    # gi cruzado, faixa larga com no e pontas na frente, saia curta do gi, contas de oracao
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="gi_front", s="gi_back", ew="gi_side", u="gi_top", d="dark@2x2"),
          inflate=0.55)
    m.box(BD, [-4, 12.0, -2], [8, 2.6, 4], fc(n="sash_front", s="sash", ew="sash", u="sash@2x2", d="sash@2x2"),
          inflate=0.95)
    m.box(BD, [1.0, 12.3, -3.45], [2.0, 2.0, 0.8], fc(all="sash@2x2"))
    beads_u(m, BD, 3.1, 23.4, 17.8, -2.85, 11, 0.85, "bead@2x2", center_mat="bead_big@2x2", center_size=1.35)
    waist(m, "Chest")
    skirt(m, "Chest", 9.0, "gi_skirt", "gi_skirt", "gi_skirt", "gi_skirt_in@4x4", "gi_skirt_edge", flare=4, depth=0.5,
          width=9.4)
    for x, h in ((1.2, 5.6 + 0.6 * lv), (2.1, 4.6 + 0.6 * lv)):
        m.box("chestSkirtFront", [x, 12.2 - h, -3.55], [0.9, h, 0.35], fc(ns="sash_tail@2x6", ew="sash@1x4", u=OMIT,
                                                                         d="sash@1x1"),
              rot=[-6, 0, 0], pivot=[0, 12, -2.75])
    if lv >= 2:
        # rosario grande flutuando atras dos ombros
        bead_ring(m, BD, [0, 19.5, 3.6], 3.8, 12, 0.9 if lv == 2 else 1.0, "o_glow@1x1" if lv == 3 else "bead@2x2")
        gem(m, BD, [0, 19.5, 3.6], 1.3, "o_gem@2x2")
    if lv == 3:
        cape(m, 13.5, 3.0, 2.7, 3.2, out="cape_out", inn="cape_in", edge="cape_edge", flare=6, thick=0.35)

    m.box(RA, [-8, 18.5, -2], [4, 5.5, 4], fc(side="gi_sleeve", u="gi_top@2x2", d="gi_skirt_in@2x2"), inflate=0.6)
    m.box(RA, [-8, 12, -2], [4, 6.5, 4], fc(side="fist_wrap", u=OMIT, d="wraps@2x2"), inflate=0.4)
    if lv >= 1:
        m.box(RA, [-8, 16.2, -2], [4, 0.8, 4], fc(side="o_rune_band" if lv >= 2 else "o_metal_flat",
                                                 u="o_metal_flat@2x2", d="o_metal_flat@2x2"), inflate=0.6)
    m.mirror(RA, LA)
    if lv >= 2:
        pauldrons(m, 1, gem_mat="o_gem@2x2", gem_size=1.1)

    m.box(RL, [-3.9, 3.2, -2], [4, 8.8, 4], fc(side="gi_pants", u=OMIT, d="gi_skirt_in@2x2"), inflate=0.55)
    m.box(RL, [-3.9, 0, -2], [4, 3.6, 4], fc(side="wraps", u=OMIT, d=OMIT), inflate=0.4)
    m.mirror(RL, LL)

    m.box(RB, [-3.9, 0, -2], [4, 3.0, 4], fc(side="wraps", u=OMIT, d="sole@2x2"), inflate=0.45)
    m.box(RB, [-3.9, -0.3, -2.6], [4, 0.6, 4.8], fc(side="sandal@4x1", u="sandal@2x2", d="sole@2x2"), inflate=0.3)
    m.box(RB, [-2.2, 0.3, -3.05], [0.6, 1.6, 0.5], fc(all="sandal@1x2"))
    if lv >= 1:
        gem(m, RB, [-1.9, 2.4, -2.55], [0.8, 0.8, 0.3])
    m.mirror(RB, LB)
    return m


# ------------------------------------------------------------------ CAVALEIRO: armadura completa, elmo com pluma, tabardo
# I Cavaleiro de Aco · II Cavaleiro da Ordem (prata e azul) · III Cavaleiro Real (ouro e purpura) · IV Lorde Bastiao
KNIGHT_T = {
    "base": theme(pal("2A2E36", "464C56", "6A727E", "98A0AC", "D0D6DE"), pal("0E1A40", "182C66", "24408E", "3A5CB8", "6A88DC"),
                  BRASS, LEATHER_STD, pal("4A0A0C", "7A121A", "A81E26", "D23A3A", "F07060"), STEEL_P,
                  pal("0A1E5A", "1A3AA0", "5A8AFF", "D0E0FF"), "8AB4FF"),
    "adept": theme(pal("3A4048", "5A626E", "828C9A", "B0BAC6", "E4EAF2"), pal("0E1E50", "1A3280", "2A4CB0", "4470D8", "7A9CF0"),
                   SILVER_M, LEATHER_STD, CREAM, SILVER_M, pal("0A1E6A", "1A44C0", "8AB4FF", "E0ECFF"), "8AB4FF"),
    "master": theme(pal("2E323A", "4C525E", "727A88", "A2AAB8", "D8DEE8"), pal("1E0A36", "34125A", "4E1E82", "6A30A8", "9258D0"),
                    GOLD_R, LEATHER_STD, pal("5A3A06", "8A5E10", "B88A1E", "E0B43A", "FFE08A"), GOLD_R,
                    pal("4A3004", "A07010", "FFD24A", "FFF0B0"), "FFD24A"),
    "archmage": theme(pal("14161C", "24262E", "383C46", "565C6A", "8A92A2"), pal("1A1008", "2E1E0E", "463016", "604620", "806030"),
                      GOLD_B, LEATHER_DARK, pal("5A2A04", "9A520A", "D88A18", "FFB040", "FFE0A0"), GOLD_B,
                      pal("5A3004", "B06A0A", "FFB040", "FFE8B0"), "FFB040"),
}


def knight_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]

    def helm_front(r):
        plate(r, MAIN, TRIM, rivets=False, ridge=True, edge="top")
        cx = r.w // 2
        # duas fendas dos olhos (brilham no Lorde Bastiao) e furos de respiro em cruz
        for i in range(1, r.w - 1):
            if i not in (cx - 1, cx):
                if lv >= 3:
                    r.glow(i, 3, GL)
                else:
                    r.set(i, 3, H("0A0A0E"))
        for (i, j) in ((cx - 2, 5), (cx + 1, 5), (cx - 2, 6), (cx + 1, 6), (cx - 3, 6), (cx + 2, 6)):
            r.set(i, j, darken(MAIN[0], 0.3))

    M["helm_front"] = helm_front
    M["helm_side"] = lambda r: plate(r, MAIN, TRIM, rivets=True, edge="top")
    M["helm_top"] = lambda r: plate(r, MAIN, rivets=False, ridge=True)

    def plume(r):
        feather(r, ACC)
        if lv >= 2:
            for i in range(r.w):
                r.set(i, r.h - 1, TRIM[3])

    M["plume"] = plume
    M["plume_tip"] = (lambda r: feather(r, ACC, tip=GL, lit=True)) if lv >= 3 else plume

    def cuirass(r, kind):
        plate(r, MAIN, TRIM, rivets=kind == "side", ridge=kind == "front", edge="top")
        if lv >= 3 and kind == "back" and r.w >= 6:
            sigil(r, r.w // 2 - 1 + (r.w % 2), 4, GL)

    M["cuirass_front"] = lambda r: cuirass(r, "front")
    M["cuirass_back"] = lambda r: cuirass(r, "back")
    M["cuirass_side"] = lambda r: cuirass(r, "side")
    M["plate_top"] = lambda r: plate(r, MAIN, TRIM, rivets=False)

    def tower(r, cx, cy, c, lit):
        """Torre do brasao (ameias + porta)."""
        for j in range(cy - 1, cy + 3):
            for i in range(cx - 1, cx + 1):
                r.set(i, j, c)
        r.set(cx - 2, cy - 1, c); r.set(cx + 1, cy - 1, c); r.set(cx - 2, cy - 2, c); r.set(cx + 1, cy - 2, c)
        r.set(cx - 1, cy + 2, SEC[0]); r.set(cx, cy + 2, SEC[0])
        if lit:
            r.glow(cx - 1, cy, GL); r.glow(cx, cy, GL)

    def tabard(r, front):
        cloth(r, SEC, period=4, base=2, top=0.06, bottom=0.10)
        r.col(1, SEC[3]); r.col(-2, SEC[1])
        for j in range(r.h):
            r.clear(0, j); r.clear(r.w - 1, j)
        if front and r.w >= 6:
            tower(r, r.w // 2, 5, TRIM[3], lv >= 1)

    M["tabard_front"] = lambda r: tabard(r, True)
    M["tabard_back"] = lambda r: tabard(r, False)

    def tabard_flap(r, front):
        cloth(r, SEC, period=4, base=2, top=0.04, bottom=0.12)
        r.col(0, TRIM[2]); r.col(-1, TRIM[2])
        trim_rows(r, TRIM, 2)
        # barra em pontas (estandarte)
        for i in range(r.w):
            d = notch(r, i)
            for q in range(d):
                r.clear(i, r.h - 1 - q)
        if lv >= 2:
            for k, i in enumerate(range(1, r.w - 2, 4)):
                rune_glyph(r, i, r.h - 8, GL, k + 1)

    M["tabard_flap_front"] = lambda r: tabard_flap(r, True)
    M["tabard_flap_back"] = lambda r: tabard_flap(r, False)
    M["tabard_in"] = lambda r: cloth(r, SEC, period=4, base=1, folds=False)
    M["tabard_edge"] = lambda r: (r.fill(TRIM[2]), r.noise(0.03))
    M["fauld"] = lambda r: (plate(r, MAIN, TRIM, rivets=False, edge="top"), [r.set(i, j, MAIN[1]) for j in range(2, r.h, 3)
                                                                 for i in range(r.w)])
    M["fauld_in"] = lambda r: (r.fill(MAIN[0]), r.noise(0.03))
    M["paul_side"] = lambda r: plate(r, MAIN, TRIM, rivets=True, edge="top")
    M["paul_top"] = lambda r: plate(r, MAIN, rivets=False, ridge=True)
    M["o_paul_side"] = lambda r: plate(r, MAIN, TRIM, rivets=True, edge="top")
    M["o_paul_top"] = plate_top(MAIN, GEM[2], glow=True)
    M["arm_plate"] = lambda r: plate(r, MAIN, TRIM, rivets=False, edge="top")
    M["gauntlet"] = lambda r: (plate(r, MAIN, TRIM, rivets=True, edge="top"), [r.set(i, r.h - 2, MAIN[1]) for i in range(r.w)]
                               if r.h > 3 else None)
    M["chain"] = lambda r: chainmail(r, MAIN)
    M["gorget"] = lambda r: (plate(r, MAIN, TRIM, rivets=False, edge="top"), r.row(r.h // 2, MAIN[1]))
    M["leg_plate"] = lambda r: plate(r, MAIN, TRIM, rivets=True, ridge=True, edge="top")
    M["knee_plate"] = lambda r: (plate(r, MAIN, TRIM, rivets=False, edge="top"), r.set(r.w // 2, r.h // 2, TRIM[4]))
    M["greave"] = lambda r: plate(r, MAIN, TRIM, rivets=True, ridge=True, edge="top")
    M["sabaton"] = lambda r: (plate(r, MAIN, rivets=False, shine=False),
                              [r.set(i, j, MAIN[1]) for j in range(1, r.h, 2) for i in range(r.w)])

    def cape_out(r):
        cloth(r, SEC, period=4, base=2, top=0.06, bottom=0.12)
        r.col(0, TRIM[2]); r.col(-1, TRIM[2])
        trim_rows(r, TRIM, 2)
        if r.w >= 7:
            tower(r, r.w // 2, 6, TRIM[3], True)
        if lv >= 3:
            for j in range(3, r.h - 4, 3):
                r.glow(1, j, GL); r.glow(r.w - 2, j, GL)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (cloth(r, SEC, period=4, base=1, folds=False), trim_rows(r, TRIM, 1))
    M["cape_edge"] = lambda r: (r.fill(TRIM[2]), r.noise(0.03))
    return M


def knight_model(name, lv):
    m = Model(name)
    skeleton(m)
    # elmo de balde com fendas, crista e PLUMA (hatMid/hatTip balancam ao andar)
    m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="helm_front", ew="helm_side", s="helm_side", u="helm_top", d=OMIT),
          inflate=1.0)
    m.box(HD, [-0.5, 24.6, -5.35], [1, 6.6, 0.5], fc(all="trim_plate@1x4"))
    m.box(HD, [-0.8, 33.0, -0.8], [1.6, 0.9, 1.6], fc(all="trim_plate@2x1"))
    m.bone("hatMid", [0, 33.8, 0], parent=HD, rot=[-6, 0, 0])
    p = m.seg("hatMid", [0, 33.6, 0.2], (0, 1, 0.35), 2.6 + 0.4 * lv, 1.6 + 0.2 * lv,
              fc(side="plume", u="plume@2x2", d="plume@2x2"), tz=1.2)
    m.bone("hatTip", p, parent="hatMid", rot=[20, 0, 0])
    p2 = m.seg("hatTip", p, (0, 0.5, 1), 2.4 + 0.3 * lv, 1.5 + 0.2 * lv, fc(side="plume", u="plume@2x2", d="plume@2x2"),
               tz=1.1, overlap=0.4)
    m.seg("hatTip", p2, (0, -0.6, 1), 2.4 + 0.4 * lv, 1.3 + 0.2 * lv, fc(side="plume_tip@2x4", u="plume_tip@2x2",
                                                                          d="plume_tip@2x2"), tz=1.0, overlap=0.4)
    if lv == 1:
        ring(m, HD, -5.0, 5.0, -5.0, 5.0, 30.4, 0.6, 0.25, side="o_metal_flat", cap="o_metal_flat")
        gem(m, HD, [0, 30.7, -5.4], [1.1, 1.1, 0.4])
    if lv >= 2:
        h = 1.0 if lv == 2 else 1.3
        ring(m, HD, -5.0, 5.0, -5.0, 5.0, 31.7, h, 0.3)
        crown_points(m, HD, -5.0, 5.0, -5.0, 5.0, 31.7 + h, 0.3, (-3.2, 3.2) if lv == 2 else (-3.4, -1.7, 1.7, 3.4),
                     (0.0,) if lv == 2 else (-2.6, 2.6), 0.9, 1.1)
        gem(m, HD, [0, 31.7 + h / 2, -5.45], [1.4, 1.2, 0.4])

    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="cuirass_front", s="cuirass_back", ew="cuirass_side", u="plate_top",
                                         d="dark@2x2"), inflate=0.9)
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="tabard_front", s="tabard_back", ew=OMIT, u=OMIT, d=OMIT), inflate=1.1)
    m.box(BD, [-4.4, 22.6, -2.4], [8.8, 1.8, 4.8], fc(side="gorget", u="trim_flat@2x2", d=OMIT))
    m.box(BD, [-4, 12.1, -2], [8, 1.5, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=1.2)
    if lv >= 1:
        gem(m, BD, [0, 12.85, -3.35], [1.3, 1.3, 0.5])
    waist(m, "Chest")
    skirt(m, "Chest", 8.6, "fauld", "fauld", "fauld", "fauld_in@4x4", "fauld", flare=6, depth=0.7, width=9.6)
    for bone_, z0, sgn, mat in (("chestSkirtFront", -3.85, -1, "tabard_flap_front"),
                                ("chestSkirtBack", 3.4, 1, "tabard_flap_back")):
        faces = fc(n=mat if sgn < 0 else "tabard_in", s=mat if sgn > 0 else "tabard_in", ew="tabard_edge@1x8",
                   u=OMIT, d=OMIT)
        m.box(bone_, [-2.6, 4.0, z0], [5.2, 8.0, 0.45], faces, rot=[5 * sgn, 0, 0], pivot=[0, 12, 2.75 * sgn])
    if lv >= 1:
        cape(m, 24.0, {1: 6.0, 2: 4.0, 3: 2.0}[lv], 3.2, 9.4, out="cape_out", inn="cape_in", edge="cape_edge", flare=4)

    m.box(RA, [-8, 16, -2], [4, 8, 4], fc(side="arm_plate", u="plate_top@2x2", d=OMIT), inflate=0.65)
    m.box(RA, [-8, 12, -2], [4, 4.2, 4], fc(side="gauntlet", u="trim_flat@2x2", d="dark@2x2"), inflate=0.85)
    m.box(RA, [-8.3, 15.0, -1.5], [0.8, 2.2, 3.0], fc(all="paul_top@1x2"))
    if lv >= 2:
        m.box(RA, [-8, 13.4, -2], [4, 0.8, 4], fc(side="o_rune_band", u="o_metal_flat@2x2", d="o_metal_flat@2x2"),
              inflate=1.0)
    m.mirror(RA, LA)
    if lv == 0:
        pauldrons(m, 2, side="paul_side", top="paul_top", under="dark@2x2")
    else:
        pauldrons(m, 3, gem_mat="o_gem@2x2", gem_size=1.2 + 0.2 * lv, fin="o_fin" if lv >= 2 else None,
                  spikes=2 if lv == 3 else 0, spike_len=1.6, spike_tip="o_gem@1x1" if lv == 3 else None)

    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="chain", u=OMIT, d=OMIT), inflate=0.3)
    m.box(RL, [-3.9, 5.6, -2], [4, 6.4, 4], fc(side="leg_plate", u=OMIT, d="dark@2x2"), inflate=0.55)
    m.box(RL, [-3.4, 4.2, -2.95], [3.0, 2.4, 1.0], fc(n="knee_plate", ew="knee_plate@1x2", u="trim_flat@2x1",
                                                    d="trim_flat@2x1", s=OMIT))
    m.mirror(RL, LL)

    m.box(RB, [-3.9, 0, -2], [4, 5.5, 4], fc(side="greave", u=OMIT, d="sole@2x2"), inflate=0.65)
    m.box(RB, [-3.9, 5.0, -2], [4, 0.8, 4], fc(side="trim_flat@2x1", u="dark@2x2", d="dark@2x2"), inflate=0.9)
    m.box(RB, [-3.6, -0.6, -3.6], [3.4, 1.9, 1.8], fc(n="sabaton", ew="sabaton", u="sabaton", d="sole@2x2", s=OMIT))
    if lv >= 1:
        m.box(RB, [-4.75, 1.8, -0.8], [0.6, 1.6, 1.6], fc(all="o_metal_flat@1x1"))
        gem(m, RB, [-4.8, 2.6, 0.0], 0.6, "o_gem@1x1")
    m.mirror(RB, LB)
    return m

# ------------------------------------------------------------------ PATRULHEIRO: capuz verde, capa, aljava nas costas
# I Patrulheiro · II Guardiao da Floresta (prata) · III Sentinela do Vento (verde-agua) · IV Lenda Verdejante (esmeralda)
RANGER_T = {
    "base": theme(pal("14260E", "203E16", "2E5820", "427830", "64A048"), pal("1E2410", "32381A", "4A5026", "646C34", "848C48"),
                  SILVER, LEATHER_STD, CREAM, SILVER, pal("1A4A10", "2E8A1E", "7AE85A", "D8FFC8"), "9AF07A"),
    "adept": theme(pal("0E2414", "163A1E", "22562C", "34763E", "52A05C"), pal("1A2412", "2A381C", "3E5028", "566C36", "74904A"),
                   SILVER_M, pal("22160E", "3A2618", "543822", "6E4C30", "8C6644"), CREAM, SILVER_M,
                   pal("1A5A10", "36A020", "9AF07A", "E0FFD0"), "9AF07A"),
    "master": theme(pal("102A2A", "1A4242", "285E5C", "3A807C", "5AAAA2"), pal("1C2228", "2C343C", "404A54", "58646E", "76848E"),
                    pal("3A4A5A", "5E7286", "8AA0B4", "B8CCDC", "E8F4FF"), pal("22160E", "3A2618", "543822", "6E4C30", "8C6644"),
                    pal("7A8A8E", "A8B8BC", "CCDCE0", "E6F2F4", "FFFFFF"), pal("3A4A5A", "5E7286", "8AA0B4", "B8CCDC", "E8F4FF"),
                    pal("0A4A42", "14907E", "7AE8D0", "D8FFF6"), "7AE8D0"),
    "archmage": theme(pal("06301C", "0C4A2C", "146C40", "22925A", "3ABE7A"), pal("3A3410", "5A5018", "7A6E24", "9C8E34", "C0B04C"),
                      GOLD_B, pal("2A1A0E", "44301A", "604428", "7E5C38", "9E784C"), pal("7A6420", "B0922E", "E0C04A", "FFE07A", "FFF6C8"),
                      GOLD_B, pal("0A5A2A", "18A04A", "6AFF9A", "D8FFE6"), "6AFF9A"),
}


def ranger_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]
    M["hood"] = lambda r: cloth(r, MAIN, period=3, base=2, top=0.10, bottom=0.12)

    def hood_front(r):
        cloth(r, MAIN, period=3, base=2)
        for j in range(2, r.h):
            for i in range(1, r.w - 1):
                r.clear(i, j)
        for i in range(1, r.w - 1):
            r.set(i, 1, MAIN[1])
        r.set(0, r.h - 1, MAIN[3]); r.set(r.w - 1, r.h - 1, MAIN[3])

    M["hood_front"] = hood_front

    def mantle(r):
        cloth(r, MAIN, period=3, base=2, top=0.08, bottom=0.10)
        leaf_hem(r, MAIN[3], 2)
        if lv >= 2:
            for i in range(1, r.w - 1, 4):
                r.glow(i, max(0, r.h - 4), GL)

    M["mantle"] = mantle

    def tunic(r, kind):
        cloth(r, SEC, period=4, base=2)
        if kind == "front":
            cx = r.w // 2
            for j in range(0, 5):
                r.set(cx - 1, j, LEATH[3] if j % 2 else SEC[0]); r.set(cx, j, SEC[0] if j % 2 else LEATH[3])

    M["tunic_front"] = lambda r: tunic(r, "front")
    M["tunic"] = lambda r: tunic(r, "side")

    def jerkin(r, front):
        leather(r, LEATH, seams=False)
        if front:
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, LEATH[0]); r.set(cx, j, LEATH[0])
                if j % 2 == 0:
                    r.set(cx - 2, j, ACC[2]); r.set(cx + 1, j, ACC[2])
        r.row(0, LEATH[3])

    M["jerkin_front"] = lambda r: jerkin(r, True)
    M["jerkin"] = lambda r: jerkin(r, False)

    def tunic_skirt(r):
        cloth(r, SEC, period=3, base=2, top=0.03, bottom=0.12)
        leaf_hem(r, SEC[3], 2)

    M["tunic_skirt"] = tunic_skirt
    M["tunic_in"] = lambda r: cloth(r, SEC, period=4, base=1, folds=False)

    def cape_out(r):
        cloth(r, MAIN, period=4, base=2, top=0.06, bottom=0.14)
        # nervuras de folha bordadas (brilham nos graus altos)
        cx = r.w // 2
        for j in range(4, r.h - 4):
            (r.glow if lv >= 3 else r.set)(cx, j, GL if lv >= 3 else MAIN[3])
            if j % 3 == 0:
                for k in range(1, 3):
                    (r.glow if lv >= 3 else r.set)(cx - k, j - k, GL if lv >= 3 else MAIN[3])
                    (r.glow if lv >= 3 else r.set)(cx + k, j - k, GL if lv >= 3 else MAIN[3])
        leaf_hem(r, MAIN[3], 3)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (cloth(r, SEC, period=4, base=1, folds=False), leaf_hem(r, SEC[2], 3))
    M["cape_edge"] = lambda r: (r.fill(MAIN[1]), r.noise(0.03))

    def quiver(r):
        leather(r, LEATH, seams=False)
        r.row(0, TRIM[3]); r.row(-1, TRIM[1])
        for j in range(2, r.h - 1, 3):
            r.row(j, LEATH[3])

    M["quiver"] = quiver
    M["quiver_top"] = lambda r: (r.fill(LEATH[0]), r.noise(0.02))
    M["shaft"] = lambda r: (r.fill(LEATH[3]), r.noise(0.02))

    def fletch(r):
        feather(r, ACC, tip=GL if lv >= 2 else None, lit=lv >= 2)

    M["fletch"] = fletch
    M["bracer"] = lambda r: (leather(r, LEATH, seams=False), r.row(0, TRIM[3]), r.row(-1, TRIM[1]),
                             [r.set(r.w // 2, j, LEATH[3]) for j in range(1, r.h - 1, 2)])

    def armguard(r):
        leather(r, LEATH, seams=False)
        r.col(0, TRIM[2]); r.col(-1, TRIM[2])
        r.set(r.w // 2, r.h // 2, TRIM[4])
        if lv >= 1:
            r.glow(r.w // 2, r.h // 2, GEM[2])

    M["armguard"] = armguard
    M["sleeve"] = lambda r: cloth(r, SEC, period=3, base=2)
    M["trousers"] = lambda r: (leather(r, LEATH, seams=False), r.folds(LEATH[1], LEATH[3], 4))
    M["boot"] = lambda r: (leather(r, LEATH, seams=False), r.row(-1, LEATH[0]))
    M["boot_cuff"] = lambda r: (cloth(r, MAIN, period=2, base=2, folds=False), r.row(0, MAIN[3]))

    def leaf(r):
        r.fill(TRIM[2])
        r.col(r.w // 2, TRIM[4])
        if lv >= 2:
            r.glow(r.w // 2, r.h // 2, GL)

    M["leaf"] = leaf
    M["o_paul_side"] = lambda r: (leather(r, LEATH, seams=False), r.row(0, TRIM[3]), r.row(-1, TRIM[1]))
    M["o_paul_top"] = lambda r: (leather(r, LEATH, seams=False), r.glow(r.w // 2, r.h // 2, GEM[2]))

    def wing(r):
        # asinha de folha nas botas da Lenda Verdejante
        shape = ["..WWWW", ".WWGGW", "WWGGW.", "WGGW..", "GW...."]
        for j, row in enumerate(shape):
            for i, ch in enumerate(row):
                if ch == "W":
                    r.set(i, j, TRIM[3])
                elif ch == "G":
                    r.glow(i, j, GL)

    M["o_wing"] = wing
    return M


def ranger_model(name, lv):
    m = Model(name)
    skeleton(m)
    # capuz verde com a ponta caida para tras (balanca)
    m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="hood_front", s="hood", ew="hood", u="hood", d=OMIT), inflate=0.75)
    m.box(HD, [-3.4, 31.2, -4.1], [6.8, 1.2, 1.0], fc(all="hood@4x1"))
    m.bone("hatMid", [0, 31.0, 4.2], parent=HD, rot=[28, 0, 0])
    m.box("hatMid", [-2.2, 27.6, 4.0], [4.4, 3.6, 1.6], fc(side="hood", u="hood@3x2", d="hood@3x2"))
    m.bone("hatTip", [0, 27.8, 4.8], parent="hatMid", rot=[22, 0, 0])
    m.box("hatTip", [-1.1, 25.2, 4.2], [2.2, 2.8, 1.2], fc(side="hood", u="hood@2x1", d="hood@2x1"))
    if lv >= 2:
        # tiara de folhas de prata (ouro no grau IV) por cima do capuz
        ring(m, HD, -4.8, 4.8, -4.8, 4.8, 30.6, 0.6, 0.25, side="o_metal_flat", cap="o_metal_flat")
        for x in ((-3.0, 0.0, 3.0) if lv == 2 else (-3.4, -1.7, 0.0, 1.7, 3.4)):
            m.box(HD, [x - 0.45, 31.0, -5.35], [0.9, 1.6 + (0.6 if x == 0 else 0), 0.3], fc(ns="leaf@1x3",
                                                                                           ew="o_metal_flat@1x1",
                                                                                           u=OMIT, d=OMIT),
                  rot=[0, 0, -x * 6], pivot=[x, 31.0, -5.2])

    # tunica + gibao de couro amarrado, manto curto com barra de folhas, cinto, alca da aljava
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="tunic_front", s="tunic", ew="tunic", u="tunic", d="dark@2x2"),
          inflate=0.45)
    m.box(BD, [-4, 13.5, -2], [8, 8.5, 4], fc(n="jerkin_front", s="jerkin", ew="jerkin", u=OMIT, d=OMIT), inflate=0.7)
    m.box(BD, [-4.6, 20.6, -2.7], [9.2, 3.6, 5.4], fc(n="mantle", s="mantle", ew="mantle", u="hood@4x4", d="dark@2x2"))
    gem(m, BD, [-2.4, 22.4, -2.95], [1.2, 1.2, 0.4], "o_gem@2x2" if lv >= 1 else "trim_plate@2x2")
    m.box(BD, [-4, 12.0, -2], [8, 1.5, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=0.95)
    strap_diag(m, BD, "strap", -3.0, -1, width=0.7)
    waist(m, "Chest")
    skirt(m, "Chest", 8.4, "tunic_skirt", "tunic_skirt", "tunic_skirt", "tunic_in@4x4", "tunic_in", flare=4, depth=0.5,
          width=9.2)
    # capa com a aljava presa por cima (os dois balancam juntos ao correr)
    cape(m, 24.0, v9_cape_lengths(lv, 5.0), 2.9, 9.0, out="cape_out", inn="cape_in", edge="cape_edge", flare=4)
    qc = "outfitCape"
    m.box(qc, [0.6, 12.5, 3.5], [2.8, 9.2, 2.4], fc(side="quiver", u="quiver_top@2x2", d="quiver@2x2"),
          rot=[0, 0, -22], pivot=[2.0, 17.0, 4.7])
    for k, (dx, dh) in enumerate(((0.0, 0.0), (0.9, 0.7), (-0.8, 0.4))):
        x = 2.0 + dx
        m.box(qc, [x - 0.2, 21.4, 4.5 + 0.2 * k], [0.4, 2.2 + dh, 0.4], fc(all="shaft@1x2"), rot=[0, 0, -22],
              pivot=[2.0, 17.0, 4.7])
        m.box(qc, [x - 0.55, 23.0 + dh, 4.45 + 0.2 * k], [1.1, 1.7, 0.5], fc(ns="fletch@2x3", ew="fletch@1x3",
                                                                            u="fletch@1x1", d=OMIT),
              rot=[0, 0, -22], pivot=[2.0, 17.0, 4.7])

    m.box(RA, [-8, 15.5, -2], [4, 8.5, 4], fc(side="sleeve", u="tunic@2x2", d=OMIT), inflate=0.5)
    m.box(RA, [-8, 12, -2], [4, 4.2, 4], fc(side="bracer", u="leather_top@2x2", d="dark@2x2"), inflate=0.7)
    m.mirror(RA, LA)
    # protetor de arqueiro no antebraco esquerdo
    m.box(LA, [4, 12.2, -2.2], [4, 5.0, 4.4], fc(side="armguard", u="leather_top@2x2", d="dark@2x2"), inflate=0.85)
    if lv >= 1:
        pauldrons(m, min(lv, 2), gem_mat="o_gem@2x2" if lv >= 2 else None, gem_size=1.1)

    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(RL, LL)

    m.box(RB, [-3.9, 0, -2], [4, 6, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(RB, [-3.9, 5.3, -2], [4, 1.6, 4], fc(side="boot_cuff", u="dark@2x2", d="dark@2x2"), inflate=0.85)
    m.box(RB, [-3.9, 2.4, -2], [4, 0.6, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.7)
    if lv == 3:
        m.box(RB, [-4.75, 3.0, -0.6], [0, 2.4, 3.0], fc(ew="o_wing@6x5"))
    m.mirror(RB, LB)
    return m



# ------------------------------------------------------------------ LADINO: capuz escuro, mascara, cintos de adagas
# I Ladino · II Sombra Urbana (carmesim) · III Mestre das Sombras (violeta e prata) · IV Lamina do Crepusculo
ROGUE_T = {
    "base": theme(pal("0C0C10", "16161C", "22222A", "30303A", "44444F"), pal("16121A", "221C28", "302838", "403650", "56486A"),
                  pal("2E3038", "4A4E58", "70768A", "A0A8B8", "DCE2EA"), LEATHER_DARK,
                  pal("3A0608", "600A10", "8A1218", "B0222A", "D84A50"), pal("2E3038", "4A4E58", "70768A", "A0A8B8", "DCE2EA"),
                  pal("4A040A", "90101C", "FF4A5A", "FFC0C8"), "FF4A5A"),
    "adept": theme(pal("0E1016", "181C24", "242A34", "343C48", "4A5462"), pal("1A1418", "2A2026", "3C2E36", "524048", "6A5660"),
                   pal("2E3038", "4A4E58", "70768A", "A0A8B8", "DCE2EA"), LEATHER_DARK,
                   pal("4A0408", "7A0A12", "A8141E", "D0283A", "F05A6A"), pal("2E3038", "4A4E58", "70768A", "A0A8B8", "DCE2EA"),
                   pal("4A040A", "A0101C", "FF4A5A", "FFC8D0"), "FF4A5A"),
    "master": theme(pal("0E0814", "1A1024", "281838", "38224E", "4E3268"), pal("140E1C", "201830", "2E2444", "40325C", "564478"),
                    SILVER_M, pal("140E14", "221822", "322432", "443244", "5A445A"),
                    pal("2A0E4A", "441872", "6226A0", "8240C8", "A870E8"), SILVER_M,
                    pal("2A0A5A", "5A1EA8", "C08AFF", "ECDCFF"), "C08AFF"),
    "archmage": theme(pal("06040A", "0E0A14", "18121E", "241A2C", "34263E"), pal("0E0A14", "1A1222", "281C34", "38284A", "4C3864"),
                      pal("3A2A50", "5E4880", "8A70B0", "B8A0DC", "E8DCFF"), pal("0E0A0E", "1A141A", "281E28", "382A38", "4C3A4C"),
                      pal("2A0848", "48107A", "6A1AAC", "9030D8", "B45AFF"), pal("3A2A50", "5E4880", "8A70B0", "B8A0DC", "E8DCFF"),
                      pal("2A0650", "5A14A8", "B45AFF", "E8CCFF"), "B45AFF"),
}


def rogue_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]
    M["hood"] = lambda r: cloth(r, MAIN, period=3, base=2, top=0.08, bottom=0.12)

    def hood_front(r):
        cloth(r, MAIN, period=3, base=2)
        # fenda dos olhos aberta; mascara de pano cobrindo nariz e boca
        for j in range(3, 5):
            for i in range(1, r.w - 1):
                r.clear(i, j)
        for j in range(5, r.h):
            for i in range(1, r.w - 1):
                r.set(i, j, ACC[2] if (i + j) % 3 else ACC[1])
        for i in range(1, r.w - 1):
            r.set(i, 5, ACC[3])
        if lv >= 3:
            for i in (1, r.w - 2):
                r.glow(i, 2, GL)

    M["hood_front"] = hood_front

    def jerkin(r, kind):
        leather(r, SEC, seams=False)
        if kind == "front":
            # tachas de metal em losango
            for j in range(1, r.h - 1, 3):
                for i in range(1 + (j // 3) % 2, r.w - 1, 3):
                    r.set(i, j, TRIM[3])
        r.row(0, SEC[3])

    M["jerkin_front"] = lambda r: jerkin(r, "front")
    M["jerkin"] = lambda r: jerkin(r, "side")
    M["scarf"] = lambda r: (cloth(r, ACC, period=2, base=2, folds=True), r.row(0, ACC[3]))

    def scarf_tail(r):
        cloth(r, ACC, period=2, base=2)
        ragged_bottom(r, 2, 23)
        if lv >= 2:
            r.glow(r.w // 2, 2, GL)

    M["scarf_tail"] = scarf_tail
    M["scarf_in"] = lambda r: (cloth(r, ACC, period=2, base=1, folds=False), ragged_bottom(r, 2, 23))

    def hilt(r):
        r.fill(LEATH[2])
        for j in range(0, r.h, 2):
            r.row(j, LEATH[3])
        if lv >= 2:
            r.glow(r.w // 2, 0, GEM[2])
        else:
            r.set(r.w // 2, 0, TRIM[4])

    M["hilt"] = hilt
    M["guard"] = lambda r: (r.fill(TRIM[3]), r.row(0, TRIM[4]), r.noise(0.02))
    M["sheath"] = lambda r: (r.fill(LEATH[1]), r.col(0, LEATH[3]), r.row(-1, TRIM[2]), r.noise(0.03))

    def blade(r):
        r.fill(TRIM[3])
        r.col(0, TRIM[4])
        if lv >= 3:
            for j in range(r.h):
                r.glow(r.w - 1, j, GL)

    M["blade"] = blade
    M["pouch"] = lambda r: (leather(r, LEATH, seams=False), r.row(0, LEATH[3]), r.set(r.w // 2, 1, TRIM[3]))
    M["sleeve"] = lambda r: cloth(r, SEC, period=3, base=2)
    M["bracer"] = lambda r: (leather(r, LEATH, seams=False), [r.row(j, LEATH[0]) for j in range(1, r.h, 2)])
    M["trousers"] = lambda r: (cloth(r, MAIN, period=3, base=2, top=0.04, bottom=0.08))
    M["boot"] = lambda r: (leather(r, LEATH, seams=False), [r.row(j, LEATH[3]) for j in range(1, r.h - 1, 2)],
                           r.row(-1, LEATH[0]))

    def tasset(r):
        leather(r, SEC, seams=False)
        r.row(-1, TRIM[2])
        for i in range(1, r.w - 1, 3):
            r.set(i, 1, TRIM[3])

    M["tasset"] = tasset
    M["tasset_in"] = lambda r: (r.fill(SEC[0]), r.noise(0.02))

    def cape_out(r):
        cloth(r, MAIN, period=3, base=2, top=0.04, bottom=0.14)
        ragged_bottom(r, 4, 61)
        if lv >= 3:
            cx = r.w // 2
            for k, j in enumerate(range(3, r.h - 6, 5)):
                rune_glyph(r, cx - 1, j, GL, k + 3)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (cloth(r, ACC, period=4, base=1, folds=False), ragged_bottom(r, 4, 61))
    M["cape_edge"] = lambda r: (r.fill(MAIN[1]), r.noise(0.03))
    M["o_paul_side"] = lambda r: (leather(r, SEC, seams=False), r.row(0, TRIM[3]), r.row(-1, TRIM[1]))
    M["o_paul_top"] = lambda r: (leather(r, SEC, seams=False), r.glow(r.w // 2, r.h // 2, GEM[2]))
    return M


def dagger(m, bone, base, rot, pivot, blade_len=1.6):
    """Adaga embainhada presa numa correia: bainha, guarda e cabo (o pomo brilha nos graus altos)."""
    x, y, z = base
    m.box(bone, [x - 0.35, y - blade_len, z - 0.25], [0.7, blade_len, 0.5], fc(all="sheath@1x2"), rot=rot, pivot=pivot)
    m.box(bone, [x - 0.65, y, z - 0.3], [1.3, 0.35, 0.6], fc(all="guard@2x1"), rot=rot, pivot=pivot)
    m.box(bone, [x - 0.22, y + 0.35, z - 0.22], [0.44, 1.0, 0.44], fc(all="hilt@1x2"), rot=rot, pivot=pivot)


def rogue_model(name, lv):
    m = Model(name)
    skeleton(m)
    m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="hood_front", s="hood", ew="hood", u="hood", d=OMIT), inflate=0.7)
    m.box(HD, [-3.4, 31.1, -4.0], [6.8, 1.0, 1.0], fc(all="hood@4x1"))
    m.bone("hatMid", [0, 30.6, 4.1], parent=HD, rot=[30, 0, 0])
    m.box("hatMid", [-1.8, 27.6, 3.9], [3.6, 3.0, 1.4], fc(side="hood", u="hood@2x2", d="hood@2x2"))

    # gibao tachado, cachecol no pescoco com pontas (capa curta), cintos cruzados com adagas, bolsinhas
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="jerkin_front", s="jerkin", ew="jerkin", u="jerkin", d="dark@2x2"),
          inflate=0.55)
    m.box(BD, [-4.3, 22.3, -2.4], [8.6, 1.9, 4.8], fc(side="scarf", u="scarf@4x2", d=OMIT))
    strap_diag(m, BD, "strap", -2.75, 1, width=0.75)
    strap_diag(m, BD, "strap", -2.75, -1, width=0.75)
    for (bx, by) in ((-2.4, 20.6), (-0.9, 18.4)):
        dagger(m, BD, [bx, by, -3.15], [0, 0, 33.7], [bx, by, -3.15], 1.3)
    for (bx, by) in ((2.4, 20.6),):
        dagger(m, BD, [bx, by, -3.15], [0, 0, -33.7], [bx, by, -3.15], 1.3)
    m.box(BD, [-4, 12.0, -2], [8, 1.4, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=0.85)
    m.box(BD, [-4.9, 11.0, -1.0], [1.3, 2.0, 2.0], fc(side="pouch@2x3", u="leather_top@2x2", d="sole@2x2"))
    m.box(BD, [3.6, 11.0, -1.4], [1.3, 2.0, 2.0], fc(side="pouch@2x3", u="leather_top@2x2", d="sole@2x2"))
    if lv >= 1:
        gem(m, BD, [0, 12.7, -2.95], [1.0, 1.0, 0.4])
    waist(m, "Chest")
    skirt(m, "Chest", 9.4, "tasset", "tasset", "tasset", "tasset_in@4x4", "tasset", flare=4, depth=0.5, width=9.2)
    if lv <= 1:
        # duas pontas do cachecol voando nas costas
        m.bone("outfitCape", [0, 23, 2.75], parent=BD, rot=[8, 0, 0])
        for x0, low, rz in ((-2.3, 14.5 - 2.0 * lv, -6), (0.3, 16.0 - 2.0 * lv, 8)):
            m.box("outfitCape", [x0, low, 2.75], [2.0, 23 - low, 0.3], fc(s="scarf_tail", n="scarf_in",
                                                                          ew="cape_edge@1x4", u=OMIT, d=OMIT),
                  rot=[0, 0, rz], pivot=[x0 + 1.0, 23, 2.9])
    else:
        # capa esfarrapada das sombras (o cachecol vira a gola dela)
        cape(m, 24.0, {2: 6.0, 3: 3.0}[lv], 2.8, 8.6, out="cape_out", inn="cape_in", edge="cape_edge", flare=5)

    m.box(RA, [-8, 15, -2], [4, 9, 4], fc(side="sleeve", u="jerkin@2x2", d=OMIT), inflate=0.5)
    m.box(RA, [-8, 12, -2], [4, 4, 4], fc(side="bracer", u="leather_top@2x2", d="dark@2x2"), inflate=0.68)
    m.mirror(RA, LA)
    # lamina escondida no pulso direito
    m.box(RA, [-6.4, 11.4, -3.2], [0.4, 0.4, 2.4], fc(all="blade@1x2"))
    if lv >= 2:
        pauldrons(m, 1, gem_mat="o_gem@2x2", gem_size=1.0)

    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.box(RL, [-3.9, 8.6, -2], [4, 0.8, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.55)
    dagger(m, RL, [-4.45, 8.4, -0.3], None, None, 2.4)
    m.mirror(RL, LL)

    m.box(RB, [-3.9, 0, -2], [4, 5.5, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(RB, [-3.9, 5.0, -2], [4, 1.0, 4], fc(side="leather@4x1", u="dark@2x2", d="dark@2x2"), inflate=0.75)
    if lv == 3:
        m.box(RB, [-4.7, 1.5, -0.25], [0.5, 3.0, 0.5], fc(all="blade@1x2"))
    m.mirror(RB, LB)
    return m


# ------------------------------------------------------------------ BARDO: chapeu de pluma, gibao colorido, meias partidas
# I Bardo Errante · II Trovador (verde-agua e coral) · III Menestrel Real (carmesim e ouro) · IV Voz Celestial (rosa)
BARD_T = {
    "base": theme(pal("3A0A1C", "5E122E", "8A1E44", "B2345E", "D8607E"), pal("6A4A0A", "9A7012", "C8981E", "ECC040", "FFE47A"),
                  GOLD_R, LEATHER_STD, CREAM, GOLD_R, pal("0A3A4A", "1A6A8A", "3AC8E8", "C8F4FF"), "6AE8D8"),
    "adept": theme(pal("0A2E30", "12484A", "1E6A6A", "2E9090", "52BAB4"), pal("5A1A14", "8A2E22", "C0483A", "E8705A", "FFA088"),
                   SILVER_M, LEATHER_STD, pal("1A5A56", "2A8A84", "4AB8B0", "7ADCD4", "C0FFF8"), SILVER_M,
                   pal("0A4A44", "14907E", "6AE8D8", "D0FFF8"), "6AE8D8"),
    "master": theme(pal("4A060C", "760E16", "A41A22", "CC3236", "EC6458"), pal("0A1A4A", "142C72", "2242A0", "3A60C8", "6A8CEC"),
                    GOLD_R, pal("2A140C", "421E12", "5E2C1A", "7A3C24", "9A5232"), pal("8A8478", "B4AE9E", "D8D2C2", "F0ECE0", "FFFFFA"),
                    GOLD_R, pal("4A3004", "A07010", "FFD24A", "FFF0B0"), "FFD24A"),
    "archmage": theme(pal("8A7480", "B49AA8", "D8C0CE", "F0E0EA", "FFFFFF"), pal("6A1A4A", "9A2A6E", "C84494", "E870B8", "FFA8DC"),
                      GOLD_B, pal("3A2A2A", "5A4444", "7A6060", "9A7E7E", "BA9C9C"), pal("8A5A7A", "B87AA0", "E0A0C8", "F8C8E4", "FFF0FA"),
                      GOLD_B, pal("6A1A5A", "C040A0", "FF8AD8", "FFE0F4"), "FF8AD8"),
}


def bard_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]
    M["hat"] = lambda r: (cloth(r, MAIN, period=3, base=2, top=0.12, bottom=0.08))

    def hat_band(r):
        r.fill(SEC[2])
        r.row(0, SEC[3])
        if r.h > 1:
            r.row(-1, SEC[1])
        for i in range(0, r.w, 3):
            r.set(i, r.h // 2, TRIM[3])
        r.noise(0.03)

    M["hat_band"] = hat_band
    M["brim_top"] = lambda r: (cloth(r, MAIN, period=4, base=2, folds=False), r.row(0, MAIN[3]), r.col(0, MAIN[3]),
                               r.row(-1, MAIN[1]), r.col(-1, MAIN[1]))
    M["brim_under"] = solid(MAIN[1])
    M["brim_edge"] = lambda r: (r.fill(MAIN[1]), r.row(0, TRIM[2]) if lv >= 2 else None, r.noise(0.03))
    M["plume"] = lambda r: feather(r, ACC)
    M["plume_tip"] = (lambda r: feather(r, ACC, tip=GL, lit=True)) if lv >= 2 else (lambda r: feather(r, ACC))

    def doublet(r, kind):
        slashed(r, MAIN, SEC, period=3)
        if kind == "front":
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, SEC[3] if j % 2 else MAIN[1])
                r.set(cx, j, TRIM[3] if j % 2 == 0 else SEC[2])
        elif kind == "back" and lv >= 3:
            for (i, j) in ((r.w // 2 - 1, 3), (r.w // 2, 2), (r.w // 2, 3), (r.w // 2, 4), (r.w // 2 - 1, 5)):
                r.glow(i, j, GL)
        r.row(-1, TRIM[2])

    M["doublet_front"] = lambda r: doublet(r, "front")
    M["doublet_back"] = lambda r: doublet(r, "back")
    M["doublet_side"] = lambda r: doublet(r, "side")
    M["puff"] = lambda r: (slashed(r, SEC, MAIN, period=2), r.row(0, TRIM[2]), r.row(-1, TRIM[2]))
    M["sleeve"] = lambda r: cloth(r, MAIN, period=3, base=2)

    def lace(r):
        r.fill(ACC[3])
        for i in range(0, r.w, 2):
            r.set(i, r.h - 1, ACC[2])
            r.set(i, 0, ACC[4])
        r.noise(0.02)

    M["lace"] = lace
    M["ruff"] = lambda r: ([r.col(i, ACC[3] if i % 2 else ACC[2]) for i in range(r.w)], r.row(0, ACC[4]),
                           r.noise(0.02))

    def peplum(r):
        slashed(r, MAIN, SEC, period=3)
        trim_rows(r, TRIM, 1)

    M["peplum"] = peplum
    M["peplum_in"] = lambda r: cloth(r, SEC, period=4, base=1, folds=False)
    M["trunk"] = lambda r: (slashed(r, SEC, MAIN, period=2), r.row(-1, TRIM[2]))
    M["trunk_in"] = lambda r: cloth(r, MAIN, period=4, base=1, folds=False)
    M["hose_a"] = lambda r: stripes_h(r, [MAIN[2], MAIN[1]], 2)
    M["hose_b"] = lambda r: stripes_h(r, [SEC[2], SEC[1]], 2)
    M["boot"] = lambda r: (leather(r, LEATH, seams=False), r.row(-1, LEATH[0]))
    M["boot_cuff"] = lambda r: (leather(r, LEATH, seams=False), r.row(0, LEATH[4]), r.row(-1, LEATH[1]))
    M["buckle"] = lambda r: (r.fill(TRIM[3]), r.set(r.w // 2, r.h // 2, LEATH[0]) if r.w > 2 and r.h > 2 else None,
                             r.noise(0.02))
    M["sash"] = lambda r: (cloth(r, SEC, period=2, base=2), r.col(0, SEC[3]) if r.w > 1 else None)

    def cape_out(r):
        cloth(r, SEC, period=3, base=2, top=0.06, bottom=0.12)
        r.col(0, TRIM[2]); r.col(-1, TRIM[2])
        trim_rows(r, TRIM, 2)
        # notas musicais bordadas (brilham nos graus altos)
        p = r.glow if lv >= 1 else r.set
        c = GL if lv >= 1 else TRIM[3]
        for (nx, ny) in ((2, 3), (r.w - 4, 7), (3, 11)):
            if ny + 2 < r.h - 2:
                p(nx, ny, c); p(nx + 1, ny, c); p(nx + 1, ny - 1, c); p(nx + 1, ny - 2, c); p(nx + 2, ny - 2, c)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (cloth(r, MAIN, period=4, base=1, folds=False), trim_rows(r, TRIM, 1))
    M["cape_edge"] = lambda r: (r.fill(TRIM[2]), r.noise(0.03))
    M["o_paul_side"] = lambda r: (slashed(r, SEC, MAIN, period=2), r.row(-1, TRIM[2]))
    M["o_paul_top"] = lambda r: (slashed(r, SEC, MAIN, period=2), r.glow(r.w // 2, r.h // 2, GEM[2]))
    return M


def bard_model(name, lv):
    m = Model(name)
    skeleton(m)
    # chapeu de aba larga levemente torto, faixa e PLUMA grande (hatMid/hatTip)
    m.box(HD, [-6.4, 29.6, -6.4], [12.8, 0.8, 12.8], fc(side="brim_edge", u="brim_top", d="brim_under@4x4"),
          rot=[0, 0, -7], pivot=[0, 30, 0])
    m.box(HD, [-3.7, 30.2, -3.7], [7.4, 3.0, 7.4], fc(side="hat", u="hat", d=OMIT), rot=[0, 0, -7], pivot=[0, 30, 0])
    m.box(HD, [-3.7, 30.2, -3.7], [7.4, 1.0, 7.4], fc(side="hat_band", u=OMIT, d=OMIT), inflate=0.15, rot=[0, 0, -7],
          pivot=[0, 30, 0])
    gem(m, HD, [-3.9, 31.0, -1.8], [0.5, 1.2, 1.2], "o_gem@1x2" if lv >= 1 else "buckle@1x2")
    m.bone("hatMid", [-3.6, 31.4, -0.8], parent=HD, rot=[0, 0, 8])
    p = m.seg("hatMid", [-3.6, 31.4, -0.8], (-0.35, 1, 0.6), 3.0 + 0.3 * lv, 0.5, fc(ew="plume@4x6", ns="plume@1x6",
                                                                                     u=OMIT, d=OMIT), tz=1.8)
    m.bone("hatTip", p, parent="hatMid", rot=[18, 0, 0])
    m.seg("hatTip", p, (-0.15, 0.3, 1), 2.6 + 0.4 * lv, 0.45, fc(ew="plume_tip@4x5", ns="plume@1x5", u=OMIT, d=OMIT),
          tz=1.5, overlap=0.3)
    if lv >= 2:
        m.seg("hatMid", [-3.4, 31.2, -1.6], (-0.6, 1, 0.3), 2.6, 0.45, fc(ew="plume_tip@3x5", ns="plume@1x5", u=OMIT,
                                                                          d=OMIT), tz=1.3)

    # gibao com talhos, gola de renda, faixa na diagonal, saiote do gibao, meia capa no ombro esquerdo
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="doublet_front", s="doublet_back", ew="doublet_side", u="doublet_side",
                                         d="dark@2x2"), inflate=0.55)
    m.box(BD, [-4.4, 22.8, -2.6], [8.8, 1.5, 5.2], fc(side="ruff", u="lace@4x2", d=OMIT))
    strap_diag(m, BD, "sash", -2.8, 1, width=1.2, edge="sash")
    m.box(BD, [-4, 12.0, -2], [8, 1.3, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=0.9)
    if lv >= 1:
        gem(m, BD, [0, 22.6, -2.95], [1.2, 1.2, 0.4])
    if lv >= 2:
        m.box(BD, [-0.4, 11.95, -3.25], [0.8, 12.6, 0.4], fc(ns="o_chain@2x12", ew="o_metal_flat@1x4", u=OMIT, d=OMIT),
              rot=[0, 0, 33.7], pivot=[0, 18.25, -3.05])
    waist(m, "Chest")
    skirt(m, "Chest", 9.6, "peplum", "peplum", "peplum", "peplum_in@4x4", "peplum_in", flare=10, depth=0.5, width=9.6)
    m.bone("outfitCape", [2.2, 24, 2.7], parent=BD)
    low = {0: 12.5, 1: 11.0, 2: 8.5, 3: 6.0}[lv]
    m.box("outfitCape", [-1.6, low, 2.75], [7.2, 24 - low, 0.5], fc(s="cape_out", n="cape_in", ew="cape_edge@1x8",
                                                                   u="cape_edge@2x1", d=OMIT))

    m.box(RA, [-8.4, 17.5, -2.4], [4.8, 6.5, 4.8], fc(side="puff", u="puff@3x3", d="puff@3x3"), inflate=0.3)
    m.box(RA, [-8, 12, -2], [4, 5.6, 4], fc(side="sleeve", u=OMIT, d="dark@2x2"), inflate=0.45)
    m.box(RA, [-8, 12, -2], [4, 1.2, 4], fc(side="lace", u=OMIT, d=OMIT), inflate=0.85)
    m.mirror(RA, LA)
    m.box(LA, [3.8, 20.8, -2.8], [5.0, 3.4, 5.6], fc(side="cape_out@4x3", u="cape_out@4x4", d="cape_in@2x2"),
          rot=[0, 0, 12], pivot=[6, 23, 0])
    if lv >= 1:
        pauldrons(m, 1, gem_mat="o_gem@2x2" if lv >= 2 else None, gem_size=1.0)

    # meias partidas (cada perna de uma cor) + calcoes bufantes que abrem com o passo
    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="hose_a", u=OMIT, d=OMIT), inflate=0.3)
    m.box(LL, [-0.1, 0, -2], [4, 12, 4], fc(side="hose_b", u=OMIT, d=OMIT), inflate=0.3)
    waist(m, "Legs")
    skirt(m, "Legs", 7.6, "trunk", "trunk", "trunk", "trunk_in@4x4", "trunk_in", flare=12, depth=0.7, width=10.0)

    m.box(RB, [-3.9, 0, -2], [4, 4.6, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(RB, [-3.9, 4.0, -2], [4, 1.8, 4], fc(side="boot_cuff", u="dark@2x2", d="dark@2x2"), inflate=1.0)
    m.box(RB, [-2.6, 1.0, -2.85], [1.4, 1.2, 0.4], fc(all="buckle@2x2"))
    m.box(RB, [-3.6, -0.5, -3.4], [3.4, 1.6, 1.5], fc(n="boot", ew="boot", u="boot", d="sole@2x2", s=OMIT))
    if lv >= 2:
        gem(m, RB, [-1.9, 1.6, -3.15], [0.6, 0.6, 0.3])
    m.mirror(RB, LB)
    return m

# ------------------------------------------------------------------ ARTIFICE: oculos, avental de couro, engrenagens, mochila
# I Aprendiz de Oficina (lentes ambar ja brilham) · II Engenheiro Arcano (azul) · III Mestre Inventor (forja)
# IV Arquiteto Etereo (nucleos ciano)
ARTIFICER_T = {
    "base": theme(pal("2E1C10", "4A2E1A", "6A4426", "8C5C34", "AE7A4A"), pal("1E2A36", "2E4052", "42586E", "5A7490", "7C96B0"),
                  BRASS, LEATHER_DARK, pal("4A1E0E", "7A3418", "A84E24", "D0703A", "EC9A64"), BRASS,
                  pal("6A2A04", "B05A0A", "FFA02A", "FFE0A0"), "FFA02A"),
    "adept": theme(pal("2A1C14", "44301E", "60442A", "7E5A38", "9E764C"), pal("0E2244", "183670", "244E9E", "3A6EC8", "6496EC"),
                   BRASS, LEATHER_DARK, pal("4A1E0E", "7A3418", "A84E24", "D0703A", "EC9A64"), BRASS,
                   pal("0A2A5A", "1A5AA0", "6AD8FF", "D8F4FF"), "6AD8FF"),
    "master": theme(pal("3A1408", "5E2210", "863418", "A84E26", "C87040"), pal("8A8478", "B4AE9E", "D8D2C2", "F0ECE0", "FFFFFA"),
                    GOLD_R, pal("1E120C", "34201A", "4A3026", "624030", "805A44"), pal("5A1E08", "8A3412", "BA521E", "E07A34", "FFAA60"),
                    GOLD_R, pal("6A1A04", "B04A0A", "FF9A3A", "FFE0B0"), "FF9A3A"),
    "archmage": theme(pal("0E1220", "1A2034", "283050", "3A4470", "566498"), pal("8A96A8", "B4C0D0", "D8E2EE", "EEF4FA", "FFFFFF"),
                      GOLD_B, pal("0E0E16", "1A1A26", "28283A", "3A3A52", "52526E"), GOLD_B, GOLD_B,
                      pal("0A4A4A", "10847A", "7AFFF0", "D8FFFA"), "7AFFF0"),
}


def artificer_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]
    M["cap"] = lambda r: (leather(r, LEATH, seams=True), r.row(-1, LEATH[0]))

    def cap_front(r):
        leather(r, LEATH, seams=False)
        for j in range(3, r.h):
            for i in range(1, r.w - 1):
                r.clear(i, j)

    M["cap_front"] = cap_front
    M["goggle_strap"] = lambda r: (r.fill(LEATH[1]), r.row(0, LEATH[3]), r.noise(0.03))

    def lens(r):
        # lente de vidro ambar/arcano: brilha sempre (o Artifice tem glowmask ja no grau I)
        for j in range(r.h):
            for i in range(r.w):
                edge = i in (0, r.w - 1) or j in (0, r.h - 1)
                r.glow(i, j, GEM[1] if edge else GEM[2])
        r.glow(1 if r.w > 2 else 0, 1 if r.h > 2 else 0, GEM[3])

    M["lens"] = lens
    M["lens_frame"] = lambda r: (r.fill(TRIM[2]), r.row(0, TRIM[3]), r.row(-1, TRIM[1]), r.noise(0.02))
    M["gear"] = gear_face(TRIM)
    M["shirt"] = lambda r: (cloth(r, SEC, period=3, base=2), r.row(0, SEC[3]))

    def apron(r, kind):
        leather(r, MAIN, seams=False)
        r.col(0, MAIN[1]); r.col(-1, MAIN[1])
        if kind == "bib":
            r.row(0, MAIN[3])
            # bolso com ferramentas (cabo de chave e lapis) e rebites de latao
            for i in range(1, r.w - 1):
                r.set(i, r.h - 4, MAIN[3])
            for (i, j) in ((1, r.h - 4), (r.w - 2, r.h - 4)):
                r.set(i, j, TRIM[3])
            r.set(2, r.h - 6, ACC[3]); r.set(2, r.h - 5, ACC[2])
            r.set(r.w - 3, r.h - 7, LEATH[4]); r.set(r.w - 3, r.h - 6, LEATH[3]); r.set(r.w - 3, r.h - 5, LEATH[3])
        else:
            r.row(-1, MAIN[0])
            for i in range(2, r.w - 2):
                r.set(i, 3, MAIN[3])
            if lv >= 2:
                for k, i in enumerate(range(1, r.w - 2, 4)):
                    rune_glyph(r, i, r.h - 7, GL, k)

    M["apron_bib"] = lambda r: apron(r, "bib")
    M["apron_low"] = lambda r: apron(r, "low")
    M["apron_in"] = lambda r: (r.fill(MAIN[1]), r.noise(0.03))
    M["apron_edge"] = lambda r: (r.fill(MAIN[1]), r.noise(0.02))

    def pack(r, back=False):
        leather(r, LEATH, seams=False)
        r.row(0, TRIM[3]); r.row(-1, TRIM[1]); r.col(0, TRIM[2]); r.col(-1, TRIM[2])
        if back and r.w >= 5 and r.h >= 5:
            # manometro: brilha (o Artifice tem brilho ja no grau I)
            cx, cy = r.w // 2, r.h // 2
            for (dx, dy) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
                r.set(cx + dx, cy + dy, TRIM[3])
            r.glow(cx, cy, GEM[2])
            r.set(cx, cy - 1, TRIM[4])

    M["pack"] = lambda r: pack(r)
    M["pack_back"] = lambda r: pack(r, True)
    M["pipe"] = lambda r: (r.fill(ACC[2]), r.col(0, ACC[3]), r.col(-1, ACC[1]) if r.w > 2 else None,
                           [r.set(i, j, ACC[4]) for j in range(0, r.h, 3) for i in range(r.w)], r.noise(0.02))
    M["pipe_top"] = lambda r: (r.fill(ACC[0]), r.noise(0.02))

    def core(r):
        for j in range(r.h):
            for i in range(r.w):
                edge = i in (0, r.w - 1) or j in (0, r.h - 1)
                r.glow(i, j, GEM[1] if edge else GL)
        r.glow(r.w // 2, r.h // 2, lighten(GL, 0.5))

    M["core"] = core
    M["glove"] = lambda r: (leather(r, LEATH, seams=False), r.row(0, LEATH[3]), r.row(-1, LEATH[0]))
    M["roll"] = lambda r: (cloth(r, SEC, period=2, base=3, folds=False), r.row(-1, SEC[1]))
    M["pants"] = lambda r: (cloth(r, pal("1E1A16", "2E2822", "423A30", "585042", "726856"), period=3, base=2,
                                  top=0.04, bottom=0.10))
    M["knee_pad"] = lambda r: (leather(r, MAIN, seams=False), r.set(r.w // 2, r.h // 2, TRIM[3]))
    M["pocket"] = lambda r: (leather(r, MAIN, seams=False), r.row(0, MAIN[3]), r.set(r.w // 2, 1, TRIM[3]))
    M["boot"] = lambda r: (leather(r, LEATH, seams=False), r.row(-1, LEATH[0]),
                           [r.set(r.w // 2, j, TRIM[3]) for j in range(1, r.h - 1, 2)])
    M["toecap"] = lambda r: plate(r, TRIM, rivets=False, shine=False)
    M["wrench"] = lambda r: (r.fill(IRON_R[3]), r.col(0, IRON_R[4]), r.noise(0.02))
    M["o_paul_side"] = lambda r: (plate(r, TRIM, rivets=True), [r.set(i, r.h - 1, ACC[2]) for i in range(r.w)])
    M["o_paul_top"] = lambda r: (plate(r, TRIM, rivets=False), r.glow(r.w // 2, r.h // 2, GEM[2]))
    return M


def artificer_model(name, lv):
    m = Model(name)
    skeleton(m)
    # touca de aviador com OCULOS DE PROTECAO na testa (lentes brilham)
    m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="cap_front", s="cap", ew="cap", u="cap", d=OMIT), inflate=0.55)
    ring(m, HD, -4.6, 4.6, -4.6, 4.6, 29.0, 1.2, 0.3, side="goggle_strap", cap="goggle_strap")
    for x0 in (-3.6, 0.6):
        m.box(HD, [x0, 28.6, -5.9], [3.0, 2.2, 1.0], fc(n="lens@3x2", ew="lens_frame@1x2", u="lens_frame@3x1",
                                                       d="lens_frame@3x1", s=OMIT))
        m.box(HD, [x0 - 0.2, 28.4, -5.4], [3.4, 2.6, 0.5], fc(side="lens_frame@3x1", n=OMIT, u="lens_frame@3x1",
                                                             d="lens_frame@3x1", s=OMIT))
    m.box(HD, [-0.6, 29.2, -5.3], [1.2, 0.8, 0.6], fc(all="lens_frame@1x1"))
    if lv >= 1:
        # lente de aumento extra num braco articulado (lado direito)
        m.box(HD, [-5.3, 28.8, -3.0], [0.6, 0.6, 2.8], fc(all="lens_frame@1x2"))
        m.box(HD, [-5.4, 28.3, -4.4], [1.1, 1.6, 1.6], fc(n="lens@2x2", ew="lens_frame@1x2", u="lens_frame@1x1",
                                                        d="lens_frame@1x1", s="lens_frame@1x1"))
    if lv >= 2:
        gear(m, HD, [4.85, 30.0, 0.0], 2.2, axis="x")

    # camisa, avental de couro (peito + saia ate os joelhos), engrenagens, cinto de ferramentas e MOCHILA com chamine
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(side="shirt", u="shirt", d="dark@2x2"), inflate=0.45)
    m.box(BD, [-3.4, 12.2, -2.9], [6.8, 10.6, 0.35], fc(n="apron_bib", s="apron_in", ew="apron_edge@1x6", u="apron_edge@3x1",
                                                       d=OMIT))
    for x in (-3.0, 2.4):
        m.box(BD, [x, 22.6, -2.75], [0.6, 1.4, 0.5], fc(all="strap@1x2"))
    gear(m, BD, [1.7, 19.6, -3.15], 1.8)
    gear(m, BD, [-1.3, 17.3, -3.15], 1.2)
    m.box(BD, [-4, 12.0, -2], [8, 1.5, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=0.9)
    m.box(BD, [-4.9, 10.6, -1.8], [1.4, 2.4, 2.0], fc(side="pocket@2x3", u="leather_top@2x2", d="sole@2x2"))
    m.box(BD, [3.5, 10.6, -1.0], [1.4, 2.4, 2.0], fc(side="pocket@2x3", u="leather_top@2x2", d="sole@2x2"))
    m.box(BD, [4.5, 8.4, 1.0], [0.5, 3.6, 0.8], fc(all="wrench@1x3"), rot=[0, 0, -8], pivot=[4.7, 12, 1.4])
    # mochila: caixa de couro com manometro, caldeira de cobre e chamine (o vapor sai dela)
    m.box(BD, [-3.2, 14.0, 2.4], [6.4, 8.2, 2.8], fc(s="pack_back", n="pack", ew="pack", u="pack@4x2", d="pack@4x2"))
    m.box(BD, [-2.6, 22.2, 2.8], [2.6, 2.2, 2.0], fc(side="pipe@3x2", u="pipe_top@2x2", d=OMIT))
    m.box(BD, [1.2, 22.2, 3.3], [1.1, 4.0 + 0.4 * lv, 1.1], fc(side="pipe@1x4", u="pipe_top@1x1", d=OMIT))
    m.box(BD, [3.2, 15.0, 3.2], [0.6, 6.0, 0.6], fc(all="pipe@1x4"))
    if lv >= 1:
        gem(m, BD, [0, 18.1, 5.3], [1.6, 1.6, 0.4], "core@3x3")
    if lv >= 3:
        # nucleo etereo no peito e nucleos laterais na mochila
        gem(m, BD, [0, 21.0, -3.2], [1.8, 1.8, 0.5], "core@3x3")
        for x in (-3.5, 3.5):
            gem(m, BD, [x, 16.0, 3.8], [0.6, 1.8, 1.4], "core@1x3")
    waist(m, "Chest")
    m.box("chestSkirtFront", [-3.4, 3.6, -3.25], [6.8, 8.4, 0.35], fc(n="apron_low", s="apron_in", ew="apron_edge@1x6",
                                                                      u=OMIT, d="apron_edge@3x1"),
          rot=[-3, 0, 0], pivot=[0, 12, -2.75])

    m.box(RA, [-8, 16.5, -2], [4, 7.5, 4], fc(side="shirt", u="shirt@2x2", d=OMIT), inflate=0.45)
    m.box(RA, [-8, 16.0, -2], [4, 1.2, 4], fc(side="roll", u="roll@2x2", d="roll@2x2"), inflate=0.75)
    m.box(RA, [-8, 12, -2], [4, 4.4, 4], fc(side="glove", u="leather_top@2x2", d="dark@2x2"), inflate=0.65)
    m.box(RA, [-8, 15.4, -2], [4, 0.6, 4], fc(side="trim_flat@4x1", u=OMIT, d=OMIT), inflate=0.8)
    if lv >= 2:
        # tala mecanica no antebraco
        m.box(RA, [-8.95, 12.6, -0.4], [0.5, 3.4, 0.8], fc(all="trim_flat@1x3"))
        gear(m, RA, [-8.95, 14.3, 0.0], 1.4, axis="x")
    m.mirror(RA, LA)
    # engrenagem-ombreira no ombro direito
    gear(m, RA, [-6.0, 24.6, 0.0], 2.4 + 0.3 * lv, axis="x", depth=0.5)
    if lv >= 1:
        pauldrons(m, min(lv, 2), gem_mat="o_gem@2x2" if lv >= 2 else None, gem_size=1.1)

    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="pants", u=OMIT, d=OMIT), inflate=0.35)
    m.box(RL, [-3.45, 4.2, -2.75], [3.1, 2.6, 0.8], fc(n="knee_pad", ew="knee_pad@1x2", u="leather_top@2x1",
                                                     d="leather_top@2x1", s=OMIT))
    m.box(RL, [-4.6, 6.8, -1.2], [0.8, 2.6, 2.4], fc(side="pocket@2x3", u="leather_top@2x2", d="sole@2x2"))
    m.mirror(RL, LL)

    m.box(RB, [-3.9, 0, -2], [4, 5, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.6)
    m.box(RB, [-3.9, 4.4, -2], [4, 1.0, 4], fc(side="trim_flat@4x1", u="dark@2x2", d="dark@2x2"), inflate=0.85)
    m.box(RB, [-3.6, -0.6, -3.4], [3.4, 1.9, 1.6], fc(n="toecap", ew="toecap", u="toecap", d="sole@2x2", s=OMIT))
    if lv >= 3:
        gem(m, RB, [-4.7, 2.4, 0.0], [0.5, 1.4, 1.4], "core@1x2")
    m.mirror(RB, LB)
    return m


# ------------------------------------------------------------------ CACADOR: peles, chapeu de cacador, presas
# I Cacador · II Rastreador (lobo cinzento, lua) · III Cacador de Feras (pele branca, ouro) · IV Senhor da Cacada
HUNTER_T = {
    "base": theme(pal("2A2414", "42381E", "5E502C", "7A6A3C", "9A8A54"), pal("3A3028", "5A4A3C", "7A6650", "9A8468", "BAA488"),
                  IRON_R, LEATHER_STD, IVORY, IRON_R, pal("5A3004", "A0600A", "FFB22A", "FFE8A8"), "FFB22A"),
    "adept": theme(pal("1A2416", "2A3822", "3C5030", "526A42", "6E8A58"), pal("2E3034", "4A4E54", "6A7078", "8E96A0", "B8C0CA"),
                   pal("3A4048", "5A626E", "828C9A", "B0BAC6", "E4EAF2"), LEATHER_STD, IVORY,
                   pal("3A4048", "5A626E", "828C9A", "B0BAC6", "E4EAF2"), pal("2A3A5A", "4A6AA0", "BFD8FF", "F0F6FF"), "BFD8FF"),
    "master": theme(pal("3A1408", "5E2210", "863418", "A84E26", "C87040"), pal("8A8A86", "B4B2AC", "D6D4CE", "ECEAE4", "FFFFFA"),
                    GOLD_R, pal("2A140C", "421E12", "5E2C1A", "7A3C24", "9A5232"), pal("6A4A10", "A07818", "D0A42E", "F0CC5A", "FFF0A8"),
                    GOLD_R, pal("5A0A04", "B02A0A", "FF5A3A", "FFC8B0"), "FF5A3A"),
    "archmage": theme(pal("1A0A0A", "2E1210", "461C16", "62281E", "82382A"), pal("0C0A0A", "1A1414", "2A2020", "3E302C", "58443C"),
                      GOLD_B, LEATHER_DARK, pal("5A4A30", "8A7650", "B8A274", "DCCA9C", "F8ECCC"), GOLD_B,
                      pal("5A3004", "B06A0A", "FFB22A", "FFECB0"), "FFB22A"),
}


def hunter_mats(P, T, lv):
    M = {}
    MAIN, SEC, TRIM, LEATH, ACC = v9_common(M, P, T, lv)
    GL, GEM = T["GLOW"], T["GEM"]
    M["hat"] = lambda r: (leather(r, LEATH, seams=False), r.vshade(0.1, 0.1))

    def hat_band(r):
        r.fill(SEC[1])
        for i in range(0, r.w, 2):
            r.set(i, r.h - 1, ACC[4])
        r.row(0, SEC[3])
        r.noise(0.03)

    M["hat_band"] = hat_band
    M["brim_top"] = lambda r: (leather(r, LEATH, seams=False), r.row(0, LEATH[3]), r.col(0, LEATH[3]),
                               r.row(-1, LEATH[1]), r.col(-1, LEATH[1]))
    M["brim_under"] = solid(LEATH[0])
    M["brim_edge"] = lambda r: (r.fill(LEATH[1]), r.noise(0.03))
    M["feather"] = lambda r: feather(r, pal("1E1A16", "3A2E22", "6A5238", "A08460", "D8C098"),
                                     tip=GL if lv >= 2 else None, lit=lv >= 2)

    def coat(r, kind):
        leather(r, MAIN, seams=False)
        r.folds(MAIN[1], MAIN[3], 4)
        if kind == "front":
            cx = r.w // 2
            # lapela, botoes de chifre e bolsos
            for j in range(r.h):
                r.set(cx - 1, j, MAIN[0])
            for j in range(1, r.h - 1, 3):
                r.set(cx, j, ACC[3])
            for i in range(1, cx - 2):
                r.set(i, r.h - 4, MAIN[3])
            for i in range(cx + 2, r.w - 1):
                r.set(i, r.h - 4, MAIN[3])

    M["coat_front"] = lambda r: coat(r, "front")
    M["coat"] = lambda r: coat(r, "side")
    M["fur"] = lambda r: fur(r, SEC)
    M["fur_ragged"] = lambda r: fur(r, SEC, ragged=2, seed=17)
    M["fang"] = lambda r: (r.fill(ACC[3]), r.row(0, ACC[4]), r.row(-1, ACC[2]) if r.h > 2 else None, r.noise(0.03))
    M["fang_gem"] = (lambda r: gem_mat(GEM)(r)) if lv >= 1 else (lambda r: (r.fill(ACC[2]), r.set(0, 0, ACC[4])))

    def coat_tail(r):
        leather(r, MAIN, seams=False)
        r.folds(MAIN[1], MAIN[3], 4)
        r.row(-1, SEC[3])
        r.row(-2, SEC[2])

    M["coat_tail"] = coat_tail
    M["coat_in"] = lambda r: (r.fill(MAIN[0]), r.noise(0.03))

    def cape_out(r):
        fur(r, SEC, ragged=3, seed=53)
        if lv >= 3:
            # olhos da fera no alto do manto
            cx = r.w // 2
            for i in (cx - 3, cx + 2):
                r.glow(i, 3, GL); r.glow(i + 1, 3, GL)

    M["cape_out"] = cape_out
    M["cape_in"] = lambda r: (leather(r, LEATH, seams=False), ragged_bottom(r, 3, 53))
    M["cape_edge"] = lambda r: fur(r, SEC)
    M["trousers"] = lambda r: (leather(r, LEATH, seams=False), r.folds(LEATH[1], LEATH[3], 4))
    M["boot"] = lambda r: (leather(r, LEATH, seams=False), r.row(-1, LEATH[0]))
    M["knife"] = lambda r: (r.fill(IRON_R[3]), r.col(0, IRON_R[4]), r.noise(0.02))
    M["pouch"] = lambda r: (leather(r, LEATH, seams=False), r.row(0, LEATH[3]), r.set(r.w // 2, 1, ACC[3]))
    M["o_paul_side"] = lambda r: fur(r, SEC)
    M["o_paul_top"] = lambda r: (fur(r, SEC), r.glow(r.w // 2, r.h // 2, GEM[2]))
    M["o_spike"] = lambda r: (r.fill(ACC[3]), r.row(0, ACC[4]), r.col(0, ACC[4]), r.noise(0.03))
    return M


def hunter_model(name, lv):
    m = Model(name)
    skeleton(m)
    # chapeu de cacador de aba larga caida na frente, faixa de presas e uma pena presa do lado
    m.box(HD, [-6.4, 29.8, -6.4], [12.8, 0.7, 12.8], fc(side="brim_edge", u="brim_top", d="brim_under@4x4"),
          rot=[-6, 0, 0], pivot=[0, 30, 0])
    m.box(HD, [-3.8, 30.3, -3.8], [7.6, 2.9, 7.6], fc(side="hat", u="hat", d=OMIT), rot=[-6, 0, 0], pivot=[0, 30, 0])
    m.box(HD, [-3.8, 30.3, -3.8], [7.6, 0.9, 7.6], fc(side="hat_band", u=OMIT, d=OMIT), inflate=0.15, rot=[-6, 0, 0],
          pivot=[0, 30, 0])
    m.box(HD, [-2.2, 33.1, -2.6], [4.4, 0.4, 4.6], fc(all="hat@3x3"), rot=[-6, 0, 0], pivot=[0, 30, 0])
    for x in (-1.6, 0.0, 1.6):
        m.box(HD, [x - 0.2, 29.3, -4.25], [0.4, 1.1, 0.35], fc(all="fang@1x2"), rot=[-6, 0, 0], pivot=[0, 30, 0])
    if lv >= 1:
        gem(m, HD, [0, 30.75, -4.35], [0.9, 0.9, 0.35], "fang_gem@2x2")
    m.bone("hatMid", [3.9, 31.0, 0.5], parent=HD)
    p = m.seg("hatMid", [3.9, 31.0, 0.5], (0.35, 1, 0.55), 3.4 + 0.3 * lv, 0.45, fc(ew="feather@3x6", ns="feather@1x6",
                                                                                   u=OMIT, d=OMIT), tz=1.4)
    if lv >= 2:
        m.bone("hatTip", p, parent="hatMid", rot=[15, 0, 0])
        m.seg("hatTip", p, (0.1, 0.6, 1), 2.0, 0.4, fc(ew="feather@3x4", ns="feather@1x4", u=OMIT, d=OMIT), tz=1.2,
              overlap=0.2)

    # casaco de couro com gola de pele, colar de presas, cinto com faca e bolsa, manto de pele nas costas
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="coat_front", s="coat", ew="coat", u="coat", d="dark@2x2"), inflate=0.55)
    m.box(BD, [-4.8, 20.8, -2.8], [9.6, 3.6, 5.6], fc(n="fur_ragged", s="fur_ragged", ew="fur_ragged", u="fur",
                                                      d="dark@2x2"))
    fangs_u(m, BD, 3.0, 22.0, 17.6, -3.05, 9, "fang@1x2", center_mat="fang_gem@2x2")
    m.box(BD, [-4, 12.0, -2], [8, 1.5, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2",
                                            d="leather_top@2x2"), inflate=0.9)
    m.box(BD, [-4.9, 10.5, -1.8], [1.3, 2.4, 2.2], fc(side="pouch@2x3", u="leather_top@2x2", d="sole@2x2"))
    m.box(BD, [3.9, 8.6, 0.6], [0.5, 3.6, 0.9], fc(all="knife@1x3"), rot=[0, 0, 10], pivot=[4.1, 12, 1.0])
    m.box(BD, [3.75, 10.6, 0.45], [0.8, 2.2, 1.2], fc(all="leather@1x2"), rot=[0, 0, 10], pivot=[4.1, 12, 1.0])
    waist(m, "Chest")
    for bone_, z0, sgn in (("chestSkirtFront", -3.0, -1), ("chestSkirtBack", 2.5, 1)):
        for x0 in (-3.7, 0.4):
            m.box(bone_, [x0, 4.6, z0], [3.3, 7.4, 0.5], fc(n="coat_tail" if sgn < 0 else "coat_in",
                                                         s="coat_tail" if sgn > 0 else "coat_in", ew="coat_in@1x6",
                                                         u=OMIT, d="fur@3x1"),
                  rot=[4 * sgn, 0, 0], pivot=[0, 12, 2.75 * sgn])
    cape(m, 24.0, v9_cape_lengths(lv, 12.0) if lv < 2 else {2: 7.0, 3: 4.0}[lv], 3.05, 9.4, out="cape_out",
         inn="cape_in", edge="cape_edge", flare=5, thick=0.7)

    m.box(RA, [-8, 13.5, -2], [4, 10.5, 4], fc(side="coat", u="coat@2x2", d=OMIT), inflate=0.5)
    m.box(RA, [-8, 12, -2], [4, 2.0, 4], fc(side="fur", u="fur@2x2", d="dark@2x2"), inflate=0.8)
    m.mirror(RA, LA)
    if lv >= 2:
        pauldrons(m, lv - 1, gem_mat="o_gem@2x2", gem_size=1.1, spikes=2, spike_len=1.4 + 0.3 * lv,
                  spike_tip="o_gem@1x1" if lv == 3 else None)

    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.box(RL, [-3.9, 7.8, -2], [4, 0.7, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.55)
    m.mirror(RL, LL)

    m.box(RB, [-3.9, 0, -2], [4, 5.4, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.6)
    m.box(RB, [-3.9, 4.8, -2], [4, 1.6, 4], fc(side="fur", u="fur@2x2", d="dark@2x2"), inflate=0.95)
    m.box(RB, [-3.9, 2.0, -2], [4, 0.6, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.75)
    m.box(RB, [-3.6, -0.6, -3.4], [3.4, 1.7, 1.5], fc(n="boot", ew="boot", u="boot", d="sole@2x2", s=OMIT))
    if lv >= 1:
        m.box(RB, [-4.65, 3.0, -0.3], [0.4, 1.4, 0.4], fc(all="fang@1x2"))
    m.mirror(RB, LB)
    return m


# ------------------------------------------------------------------ registro das 10 classes novas
V9.update({
    "warrior": dict(T=WARRIOR_T, mats=warrior_mats, model=warrior_model),
    "paladin": dict(T=PALADIN_T, mats=paladin_mats, model=paladin_model),
    "barbarian": dict(T=BARBARIAN_T, mats=barbarian_mats, model=barbarian_model),
    "monk": dict(T=MONK_T, mats=monk_mats, model=monk_model),
    "knight": dict(T=KNIGHT_T, mats=knight_mats, model=knight_model),
    "ranger": dict(T=RANGER_T, mats=ranger_mats, model=ranger_model),
    "rogue": dict(T=ROGUE_T, mats=rogue_mats, model=rogue_model),
    "bard": dict(T=BARD_T, mats=bard_mats, model=bard_model),
    "artificer": dict(T=ARTIFICER_T, mats=artificer_mats, model=artificer_model),
    "hunter": dict(T=HUNTER_T, mats=hunter_mats, model=hunter_model),
})
V9_TIER_KEYS = {"": "base", "adept": "adept", "master": "master", "archmage": "archmage"}
V9_LV = {"base": 0, "adept": 1, "master": 2, "archmage": 3}

# ------------------------------------------------------------------ icones das classes novas
# Letras -> familia do tema do grau (o mesmo desenho serve para os 4 graus, so muda a paleta):
#   o contorno, n quase preto, k/d/m/l/L MAIN, a/b/c/C SEC, t/g/h/G TRIM, e/f/F LEATH, v/u/w/W ACC, x/X GEM.
V9_HELMETS = {
    "warrior": ([
        "................", "......oooo......", "....ooGhhgoo....", "...oGhhggggto...", "..oGhggggggtto..",
        "..ohggggggggto..", "..otGtGtGtGtto..", "..ocbonnhnnobco.", "..obconnhnnocbo.", "..ocbbonhnobbco.",
        "..obcbbnnnbbcbo.", "..ocbcbbbbbcbco.", "...obcbcbcbcbo..", "....oobbbbboo...", "......ooooo.....",
        "................"], (8, 6)),
    "paladin": ([
        "................", ".....oooooo.....", "....oLlGGlmo....", "...oLlGxxGmmo...", "...olmlGGlmmdo..",
        "...olmmhhmmmdo..", "...olmmhhmmmdo..", "...onnnhhnnndo..", "...olmmhhmmmdo..", "...olmghhgmmdo..",
        "...olmmhhmmmdo..", "...olmmhhmmmdo..", "...otgggggggto..", "....ooooooooo...", "................",
        "................"], (7, 3)),
    "barbarian": ([
        "................", ".W............W.", ".wv..........vw.", "..wv.oo..oo.vw..", "..wvolmoomlovw..",
        "...vomllllmov...", "...odmllllmdo...", "..odxmmllmmxdo..", "..odmmmllmmmdo..", "..odmmoooommdo..",
        "...odomllmodo...", "...ooWnnnnWoo...", "....oWnnnnWo....", "....oouuuuoo....", "......oooo......",
        "................"], (4, 7)),
    "monk": ([
        "................", "................", "................", "................", "................",
        "..oooooooooooo..", ".oabbbbhhbbbbao.", ".oabbbhxXhbbbao.", ".oaaaaahhaaaaao.", "..oooooooooooaao",
        "............oabo", "...........oabo.", "...........oabo.", "..........oabo..", "..........oaao..",
        "...........oo..."], (7, 7)),
    "knight": ([
        "..........vwW...", ".........vwWw...", "........vuwWv...", "......oohgGvo...", "....oollmmmmoo..",
        "...olLlmmmmmmdo.", "...olllmhmmmmdo.", "...onnnnhnnnndo.", "...olllmhmmmmdo.", "...olmnmhmnmmdo.",
        "...olmmnhnmmmdo.", "...olmmmhmmmmdo.", "...otgggggggtgo.", "....oooooooooo..", "................",
        "................"], (9, 3)),
    "ranger": ([
        "................", "......oooo......", ".....olmmdo.....", "....olmmmmdo....", "...olmmmmmmdo...",
        "...olmdddddmdo..", "..olmdnnnnnndo..", "..olmdnnnnnndo..", "..olmdnnnnnndo..", "..olmdnnnnnndo..",
        "..oldddnnnndddo.", ".olmmmmdddmmmmdo", ".olmmmmmmmmmmmdo", "..oddmmmgxmmddo.", "...ooooooooooo..",
        "................"], (9, 13)),
    "rogue": ([
        "................", "......oooo......", ".....olmmdo.....", "....olmmmmdo....", "...olmmmmmmdo...",
        "...olmdddddmdo..", "..olmdnnnnnndo..", "..olmdnxnnxndo..", "..olmduuuuuudo..", "..olmuwuuuuwudo.",
        "..oluuuuuuuuuuo.", ".olmmuuuuuuuumdo", ".olmmmmuuuummmdo", "..oddmmmmmmmddo.", "...ooooooooooo..",
        "................"], (7, 7)),
    "bard": ([
        "...........W....", "..........wWo...", ".........wWwo...", "........wWwo....", ".....ooowWwoo...",
        "....olmwWwmmdo..", "....olmwWmmmdo..", "....olmmmmmmdo..", "....obbhxbhbbo..", "..oolmmmmmmmmdoo",
        ".ollmmmmmmmmmmdo", ".oddmmmmmmmmmddo", "..oodddddddddoo.", "....oooooooooo..", "................",
        "................"], (8, 8)),
    "artificer": ([
        "................", ".....oooooo.....", "....oFffffeo....", "...oFfffffffeo..", "..oFffffffffeeo.",
        "..oeeeeeeeeeeeo.", ".oghhgoeeoghhgo.", ".ohxXhgggghxXho.", ".ohxxhoeeohxxho.", ".oghhgoeeoghhgo.",
        "..oef......feo..", "..oef......feo..", "..oeeo....oeeo..", "...oo......oo...", "................",
        "................"], (8, 7)),
    "hunter": ([
        "................", "...........oFo..", "..........oFfo..", ".....oooooFfo...", "....oFfffFfeo...",
        "....oFfffffeo...", "....oFfffffeeo..", "....oaWaWaWaao..", "..oooFfffffeeooo", ".oFFfffffffffeeo",
        ".oeeffffffffeeeo", "..ooeeeeeeeeeoo.", "....ooooooooo...", "................", "................",
        "................"], (8, 7)),
}


def _mirror_rows(halves):
    return [h + h[::-1] for h in halves]


# silhuetas-base (zonas): peito S ombros/mangas, T tronco; calcas B cinto, P pernas; botas C punho, K cano, S sola
V9_CHEST_MASK = _mirror_rows(["........", "..oooo..", ".oSSSSoo", "oSSSSSTT", "oSSSSSTT", "oSSSoTTT", "oSSSoTTT",
                              "oSSSoTTT", "oSSSoTTT", ".ooooTTT", "....oTTT", "....oTTT", "....oTTT", "....oooo",
                              "........", "........"])
V9_LEGS_MASK = _mirror_rows(["........", "..oooooo", "..oBBBBB", "..oPPPPP", "..oPPPPP", "..oPPPPo", "..oPPPPo",
                             "..oPPPPo", ".oPPPPo.", ".oPPPPo.", ".oPPPPo.", ".oPPPPo.", ".oPPPPo.", ".oooooo.",
                             "........", "........"])
V9_BOOTS_MASK = ["................", "................", "................", "................", "................",
                 ".ooooo....ooooo.", ".oCCCo....oCCCo.", ".oKKKo....oKKKo.", ".oKKKo....oKKKo.", ".oKKKo....oKKKo.",
                 ".oKKKo....oKKKo.", "oKKKKo....oKKKKo", "oKKKKKo..oKKKKKo", "oSSSSSo..oSSSSSo", ".ooooo....ooooo.",
                 "................"]


def _sh(x, a, b, letters):
    t = (x - a) / max(1, b - a)
    return letters[0] if t < 0.34 else (letters[1] if t < 0.67 else letters[2])


def _chk(x, y, a, b):
    return a if (x + y) % 2 else b


def _arm(x):
    """Coluna relativa dentro da manga (0 = lado de fora)."""
    return x if x < 8 else 15 - x


# preenchimento das zonas por classe: fn(x, y, zona) -> letra
def _chest_warrior(x, y, z):
    if z == "S":
        return "h" if y <= 3 and lv_pad(x) else _chk(x, y, "b", "c")
    if y == 10:
        return "g" if x in (7, 8) else "e"
    if 6 <= x <= 9:
        if (x, y) in ((7, 5), (8, 5)):
            return "w"
        return "lmmd"[x - 6]
    return _chk(x, y, "b", "c")


def lv_pad(x):
    return _arm(x) in (1, 2, 3)


def _chest_plate(sec_center):
    def f(x, y, z):
        if z == "S":
            return "g" if y == 2 or _arm(x) == 0 else _sh(_arm(x), 1, 4, "Llm")
        if y == 10:
            return "h" if x in (7, 8) else "g"
        if 6 <= x <= 9 and y >= 4:
            if (x, y) in ((7, 6), (8, 6)):
                return "h"
            if (x, y) in ((7, 5), (8, 7), (7, 7), (8, 5)):
                return "G" if sec_center == "sun" else "h"
            return "cbba"[x - 6]
        return "g" if y == 3 else _sh(x, 5, 10, "lmd")
    return f


def _chest_barbarian(x, y, z):
    if z == "S":
        return "lmdm"[(x + y * 3) % 4] if y <= 4 else "a"
    if y == 10:
        return "w" if x in (7, 8) else "e"
    if y <= 3:
        return "lmd"[(x + y) % 3]
    if x - 5 == y - 4 or 10 - x == y - 4:
        return "F"
    return "a" if x in (5, 10) else "k"


def _chest_monk(x, y, z):
    if z == "S":
        return "b" if y == 8 else _sh(_arm(x), 0, 3, "lmd")
    if y in (10, 11):
        return "a" if y == 10 else "b"
    a, b = 5 + (y - 3) // 2, 10 - (y - 3) // 2
    if x == a:
        return "b"
    if a < x < b and y < 7:
        return "w"
    if y == 8 and 6 <= x <= 9:
        return "f" if x % 2 else "F"
    return _sh(x, 5, 10, "lmd")


def _chest_knight(x, y, z):
    if z == "S":
        return "g" if y == 2 or _arm(x) == 0 else _sh(_arm(x), 1, 4, "Llm")
    if y == 10:
        return "h" if x in (7, 8) else "g"
    if 6 <= x <= 9 and y >= 4:
        if (y in (5, 6, 7) and x in (7, 8)) or (y == 4 and x in (6, 9)):
            return "h"
        return "cbba"[x - 6]
    return "g" if y == 3 else _sh(x, 5, 10, "lmd")


def _chest_ranger(x, y, z):
    if y <= 4:
        return _sh(x, 0, 15, "lmd") if z == "S" or y <= 3 else "m"
    if z == "S":
        return "e" if y >= 7 else _sh(_arm(x), 0, 3, "cba")
    if y == 10:
        return "G" if x == 8 else "e"
    if x == 7 or x == 8:
        return "e" if y % 2 else "w"
    if x + y == 15:
        return "e"
    return _sh(x, 5, 10, "Ffe")


def _chest_rogue(x, y, z):
    if y <= 3:
        return _sh(x, 0, 15, "wuv")
    if z == "S":
        return "e" if y >= 7 else _sh(_arm(x), 0, 3, "cba")
    if y == 10:
        return "g" if x in (7, 8) else "e"
    if x - 5 == y - 4 or 10 - x == y - 4:
        return "h" if y in (5, 6) else "e"
    return _sh(x, 5, 10, "cba")


def _chest_bard(x, y, z):
    if y <= 3 and 4 <= x <= 11:
        return "W" if (x + y) % 2 else "w"
    if z == "S":
        return "l" if _arm(x) % 2 else "c"
    if y == 10:
        return "g"
    if x in (7, 8):
        return "h" if y % 2 and x == 8 else ("b" if x == 7 else "m")
    return ("m" if x % 2 else "b") if y % 3 else ("d" if x % 2 else "a")


def _chest_artificer(x, y, z):
    if z == "S":
        return "e" if y >= 7 else _sh(_arm(x), 0, 3, "cba")
    if y == 10:
        return "g" if x in (7, 8) else "e"
    if (x, y) in ((8, 5), (7, 6), (9, 6), (8, 7)):
        return "h"
    if (x, y) == (8, 6):
        return "t"
    return "a" if x in (5, 10) else _sh(x, 6, 9, "lmd")


def _chest_hunter(x, y, z):
    if y <= 4:
        return "cbab"[(x + y) % 4]
    if z == "S":
        return "c" if y >= 8 else _sh(_arm(x), 0, 3, "lmd")
    if y == 10:
        return "g" if x in (7, 8) else "e"
    if y in (6, 7) and 5 <= x <= 10 and (x + y) % 2 == 0 and not (y == 7 and x in (5, 10)):
        return "W"
    if x == 7:
        return "k"
    return _sh(x, 5, 10, "lmd")


def _legs(belt, pants, extra=None):
    def f(x, y, z):
        if z == "B":
            return belt(x)
        if extra:
            r = extra(x, y)
            if r:
                return r
        return pants(x, y)
    return f


def _leg_col(x):
    """Coluna relativa dentro da perna (0 = lado de fora)."""
    return x - 2 if x < 8 else 13 - x


V9_ICON_FILL = {
    "warrior": dict(
        chestplate=_chest_warrior,
        leggings=_legs(lambda x: "g" if x in (7, 8) else "e",
                       lambda x, y: "g" if y == 9 and _leg_col(x) in (1, 2) else _sh(_leg_col(x), 0, 3, "Ffe"),
                       lambda x, y: (_chk(x, y, "b", "c") if not 6 <= x <= 9 else "lmmd"[x - 6]) if y <= 6 else None),
        boots=dict(C="F", K=lambda x, y: "h" if y >= 11 and x in (0, 1, 14, 15) else _sh(x % 10, 1, 4, "Ffe"), S="e"),
        gems=dict(chestplate=(7, 5), leggings=(7, 2), boots=(2, 6))),
    "paladin": dict(
        chestplate=_chest_plate("sun"),
        leggings=_legs(lambda x: "h" if x in (7, 8) else "g",
                       lambda x, y: "h" if y == 8 and _leg_col(x) in (1, 2) else (
                           _chk(x, y, "u", "w") if y >= 11 else _sh(_leg_col(x), 0, 3, "Llm")),
                       lambda x, y: ("cbba"[x - 6] if 6 <= x <= 9 else None) if y <= 6 else None),
        boots=dict(C="g", K=lambda x, y: _sh(x % 10, 1, 4, "Llm"), S="e"),
        gems=dict(chestplate=(7, 6), leggings=(7, 2), boots=(2, 6))),
    "barbarian": dict(
        chestplate=_chest_barbarian,
        leggings=_legs(lambda x: "w" if x in (7, 8) else "e", lambda x, y: _sh(_leg_col(x), 0, 3, "cba"),
                       lambda x, y: "lmd"[(x + y * 2) % 3] if 5 <= x <= 10 and y <= 8 + (x % 2) else None),
        boots=dict(C=lambda x, y: "l", K=lambda x, y: "e" if y in (8, 10) else "lmd"[(x + y) % 3], S="e"),
        gems=dict(chestplate=(7, 10), leggings=(7, 2), boots=(2, 6))),
    "monk": dict(
        chestplate=_chest_monk,
        leggings=_legs(lambda x: "b" if x % 3 else "a",
                       lambda x, y: ("w" if (x + y) % 3 else "u") if y >= 11 else _sh(_leg_col(x), 0, 3, "lmd")),
        boots=dict(C="w", K=lambda x, y: "w" if (x + y) % 3 else "u", S="f"),
        gems=dict(chestplate=(7, 8), leggings=(7, 2), boots=(2, 6))),
    "knight": dict(
        chestplate=_chest_knight,
        leggings=_legs(lambda x: "h" if x in (7, 8) else "g",
                       lambda x, y: "h" if y == 8 and _leg_col(x) in (1, 2) else (
                           _chk(x, y, "m", "d") if y >= 11 else _sh(_leg_col(x), 0, 3, "Llm")),
                       lambda x, y: ("cbba"[x - 6] if 6 <= x <= 9 else None) if y <= 7 else None),
        boots=dict(C="g", K=lambda x, y: _sh(x % 10, 1, 4, "Llm"), S="e"),
        gems=dict(chestplate=(7, 10), leggings=(7, 2), boots=(2, 6))),
    "ranger": dict(
        chestplate=_chest_ranger,
        leggings=_legs(lambda x: "G" if x == 8 else "e", lambda x, y: _sh(_leg_col(x), 0, 3, "Ffe"),
                       lambda x, y: ("cba"[min(2, abs(x - 7) // 2)] if 4 <= x <= 11 else None) if y <= 4 else None),
        boots=dict(C="m", K=lambda x, y: "e" if y == 9 else _sh(x % 10, 1, 4, "Ffe"), S="e"),
        gems=dict(chestplate=(4, 3), leggings=(8, 2), boots=(2, 6))),
    "rogue": dict(
        chestplate=_chest_rogue,
        leggings=_legs(lambda x: "g" if x in (7, 8) else "e",
                       lambda x, y: "e" if y == 7 else _sh(_leg_col(x), 0, 3, "lmd"),
                       lambda x, y: "h" if _leg_col(x) == 0 and y in (5, 6) else None),
        boots=dict(C="f", K=lambda x, y: "F" if y % 2 else _sh(x % 10, 1, 4, "fee"), S="e"),
        gems=dict(chestplate=(7, 10), leggings=(7, 2), boots=(2, 6))),
    "bard": dict(
        chestplate=_chest_bard,
        leggings=_legs(lambda x: "g",
                       lambda x, y: ("lm"[y % 2] if x < 8 else "cb"[y % 2]),
                       lambda x, y: ("m" if x % 2 else "b") if y <= 5 else None),
        boots=dict(C="F", K=lambda x, y: "h" if y == 10 and x in (3, 12) else _sh(x % 10, 1, 4, "Ffe"), S="e"),
        gems=dict(chestplate=(7, 2), leggings=(7, 2), boots=(2, 6))),
    "artificer": dict(
        chestplate=_chest_artificer,
        leggings=_legs(lambda x: "g" if x in (7, 8) else "e",
                       lambda x, y: "m" if y in (8, 9) and _leg_col(x) in (1, 2) else _sh(_leg_col(x), 0, 3, "fek"),
                       lambda x, y: "f" if _leg_col(x) == 0 and y in (5, 6) else None),
        boots=dict(C="g", K=lambda x, y: "h" if y >= 11 and x in (0, 1, 14, 15) else _sh(x % 10, 1, 4, "Ffe"), S="e"),
        gems=dict(chestplate=(8, 6), leggings=(7, 2), boots=(2, 6))),
    "hunter": dict(
        chestplate=_chest_hunter,
        leggings=_legs(lambda x: "g" if x in (7, 8) else "e",
                       lambda x, y: "e" if y == 8 else _sh(_leg_col(x), 0, 3, "Ffe")),
        boots=dict(C=lambda x, y: "cb"[(x + y) % 2], K=lambda x, y: "e" if y == 9 else _sh(x % 10, 1, 4, "Ffe"), S="e"),
        gems=dict(chestplate=(7, 7), leggings=(7, 2), boots=(2, 6))),
}


def v9_icon_rows(cid, piece):
    """Linhas de letras do icone (capacete desenhado a mao; outras pecas = silhueta + preenchimento da classe)."""
    if piece == "helmet":
        return V9_HELMETS[cid][0]
    spec = V9_ICON_FILL[cid]
    mask = {"chestplate": V9_CHEST_MASK, "leggings": V9_LEGS_MASK, "boots": V9_BOOTS_MASK}[piece]
    out = []
    for y, row in enumerate(mask):
        line = ""
        for x, ch in enumerate(row):
            if ch in ".o":
                line += ch
            elif piece == "boots":
                v = spec["boots"][{"C": "C", "K": "K", "S": "S"}[ch]]
                line += v(x, y) if callable(v) else v
            else:
                line += spec[piece](x, y, ch)
        out.append(line)
    return out


def v9_icon_palette(th):
    P, G = th["pal"], th["GEM"]
    MAIN, SEC, TRIM, LEATH, ACC = (P[k] for k in ("MAIN", "SEC", "TRIM", "LEATH", "ACC"))
    darkest = min((MAIN[0], SEC[0], LEATH[0]), key=lambda c: sum(c[:3]))
    return {"o": darken(darkest, 0.55), "n": H("0C0A0E"), "k": MAIN[0], "d": MAIN[1], "m": MAIN[2], "l": MAIN[3],
            "L": MAIN[4], "a": SEC[1], "b": SEC[2], "c": SEC[3], "C": SEC[4], "t": TRIM[1], "g": TRIM[2], "h": TRIM[3],
            "G": TRIM[4], "e": LEATH[1], "f": LEATH[2], "F": LEATH[3], "v": ACC[1], "u": ACC[2], "w": ACC[3],
            "W": ACC[4], "x": G[2], "X": G[3]}


def v9_draw_icon(cid, piece, tkey):
    th = V9[cid]["T"][tkey]
    lv = V9_LV[tkey]
    cols = v9_icon_palette(th)
    c = Canvas(16, 16)
    for y, row in enumerate(v9_icon_rows(cid, piece)[:16]):
        row = (row + "." * 16)[:16]
        for x, ch in enumerate(row):
            if ch != ".":
                c.set(x, y, cols[ch])
    G = th["GEM"]
    if lv >= 1:
        gx, gy = V9_HELMETS[cid][1] if piece == "helmet" else V9_ICON_FILL[cid]["gems"][piece]
        if lv >= 2:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if c.get(gx + dx, gy + dy)[3] > 0:
                    c.set(gx + dx, gy + dy, G[1] if lv == 2 else G[2])
        c.set(gx, gy, G[2] if lv == 1 else G[3])
        if lv == 3:
            glow_c = lighten(th["GLOW"], 0.2)
            sparkle(c, (14, 1), glow_c)
            sparkle(c, (1, 14), glow_c)
        else:
            tier_pip(c, (14, 1), G)
            if lv == 2:
                tier_pip(c, (1, 14), G)
    name = f"{cid}_{piece}" if lv == 0 else f"{cid}_{tkey}_{piece}"
    c.save(os.path.join(A, "textures/item", name + ".png"))
    item_model(A, name)


# ------------------------------------------------------------------ montagem dos modelos, reservas vanilla e icones
def build_v09(only=None):
    print("== v0.9: trajes das classes Guerreiras e A Distancia")
    for cid in V9_ORDER:
        if only and cid not in only:
            continue
        spec = V9[cid]
        for tkey in ("base", "adept", "master", "archmage"):
            lv = V9_LV[tkey]
            th = spec["T"][tkey]
            name = f"outfit_{cid}" if lv == 0 else f"outfit_{cid}_{tkey}"
            _, glow = build(spec["model"](name, lv), spec["mats"](th["pal"], th, lv))
            want = lv > 0 or cid in V9_GLOW_I
            if glow != want:
                # o Java liga a camada de brilho conforme OutfitSet.glows / grau: os dois precisam bater
                raise RuntimeError(f"{name}: glowmask={glow}, esperado {want}")
            P = th["pal"]
            vanilla_layers(cid if lv == 0 else f"{cid}_{tkey}", P["MAIN"][2], P["TRIM"][2], P["MAIN"][0])
            for piece in PIECES:
                v9_draw_icon(cid, piece, tkey)

# ------------------------------------------------------------------ receitas de costura (grau I) das classes novas
# Formatos escolhidos para nao colidir com as armaduras vanilla (sempre ha um item diferente no meio).
V9_SHAPED = {
    "warrior": {
        "helmet": (["ICI", "C C"], {"I": "minecraft:iron_ingot", "C": "minecraft:chain"}),
        "chestplate": (["C C", "RCR", "CLC"], {"C": "minecraft:chain", "R": "minecraft:red_wool", "L": "minecraft:leather"}),
        "leggings": (["CLC", "C C", "R R"], {"C": "minecraft:chain", "L": "minecraft:leather", "R": "minecraft:red_wool"}),
        "boots": (["L L", "I I"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot"}),
    },
    "paladin": {
        "helmet": (["IGI", "I I"], {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot"}),
        "chestplate": (["I I", "WGW", "III"], {"I": "minecraft:iron_ingot", "W": "minecraft:white_wool",
                                              "G": "minecraft:gold_ingot"}),
        "leggings": (["IGI", "W W", "I I"], {"I": "minecraft:iron_ingot", "G": "minecraft:gold_nugget",
                                            "W": "minecraft:white_wool"}),
        "boots": (["G G", "I I"], {"G": "minecraft:gold_nugget", "I": "minecraft:iron_ingot"}),
    },
    "barbarian": {
        "helmet": (["BLB", "L L"], {"B": "minecraft:bone", "L": "minecraft:leather"}),
        "chestplate": (["L L", "BLB", "LLL"], {"L": "minecraft:leather", "B": "minecraft:bone"}),
        "leggings": (["LBL", "L L", "H H"], {"L": "minecraft:leather", "B": "minecraft:bone", "H": "minecraft:rabbit_hide"}),
        "boots": (["H H", "L L"], {"H": "minecraft:rabbit_hide", "L": "minecraft:leather"}),
    },
    "monk": {
        "helmet": (["OSO"], {"O": "minecraft:orange_wool", "S": "minecraft:string"}),
        "chestplate": (["O O", "OBO", "OOO"], {"O": "minecraft:orange_wool", "B": "minecraft:brown_wool"}),
        "leggings": (["OBO", "O O", "S S"], {"O": "minecraft:orange_wool", "B": "minecraft:brown_wool",
                                            "S": "minecraft:string"}),
        "boots": (["S S", "W W"], {"S": "minecraft:string", "W": "minecraft:white_wool"}),
    },
    "knight": {
        "helmet": (["IFI", "I I"], {"I": "minecraft:iron_ingot", "F": "minecraft:feather"}),
        "chestplate": (["I I", "IBI", "III"], {"I": "minecraft:iron_ingot", "B": "minecraft:blue_wool"}),
        "leggings": (["III", "B B", "I I"], {"I": "minecraft:iron_ingot", "B": "minecraft:blue_wool"}),
        "boots": (["B B", "I I"], {"B": "minecraft:blue_wool", "I": "minecraft:iron_ingot"}),
    },
    "ranger": {
        "helmet": (["GLG", "G G"], {"G": "minecraft:green_wool", "L": "minecraft:leather"}),
        "chestplate": (["G G", "LAL", "GLG"], {"G": "minecraft:green_wool", "L": "minecraft:leather",
                                              "A": "minecraft:arrow"}),
        "leggings": (["LGL", "L L", "L L"], {"L": "minecraft:leather", "G": "minecraft:green_wool"}),
        "boots": (["G G", "L L"], {"G": "minecraft:green_wool", "L": "minecraft:leather"}),
    },
    "rogue": {
        "helmet": (["KNK", "K K"], {"K": "minecraft:black_wool", "N": "minecraft:iron_nugget"}),
        "chestplate": (["K K", "LNL", "KLK"], {"K": "minecraft:black_wool", "L": "minecraft:leather",
                                              "N": "minecraft:iron_nugget"}),
        "leggings": (["KNK", "L L", "K K"], {"K": "minecraft:black_wool", "N": "minecraft:iron_nugget",
                                            "L": "minecraft:leather"}),
        "boots": (["K K", "L L"], {"K": "minecraft:black_wool", "L": "minecraft:leather"}),
    },
    "bard": {
        "helmet": (["PFP", "P P"], {"P": "minecraft:pink_wool", "F": "minecraft:feather"}),
        "chestplate": (["P P", "YPY", "PYP"], {"P": "minecraft:pink_wool", "Y": "minecraft:yellow_wool"}),
        "leggings": (["PYP", "P P", "Y Y"], {"P": "minecraft:pink_wool", "Y": "minecraft:yellow_wool"}),
        "boots": (["N N", "L L"], {"N": "minecraft:gold_nugget", "L": "minecraft:leather"}),
    },
    "artificer": {
        "helmet": (["CGC", "L L"], {"C": "minecraft:copper_ingot", "G": "minecraft:glass", "L": "minecraft:leather"}),
        "chestplate": (["L L", "CLC", "LRL"], {"L": "minecraft:leather", "C": "minecraft:copper_ingot",
                                              "R": "minecraft:redstone"}),
        "leggings": (["LCL", "L L", "B B"], {"L": "minecraft:leather", "C": "minecraft:copper_ingot",
                                            "B": "minecraft:brown_wool"}),
        "boots": (["C C", "L L"], {"C": "minecraft:copper_ingot", "L": "minecraft:leather"}),
    },
    "hunter": {
        "helmet": (["HLH", "L L"], {"H": "minecraft:rabbit_hide", "L": "minecraft:leather"}),
        "chestplate": (["H H", "LBL", "LHL"], {"H": "minecraft:rabbit_hide", "L": "minecraft:leather",
                                              "B": "minecraft:bone"}),
        "leggings": (["LHL", "L L", "L L"], {"L": "minecraft:leather", "H": "minecraft:rabbit_hide"}),
        "boots": (["L L", "H H"], {"L": "minecraft:leather", "H": "minecraft:rabbit_hide"}),
    },
}
# reforma (peca de qualquer classe + tecido + item tematico): (tecido, acento, item tematico)
V9_SETS = {
    "warrior": ("minecraft:red_wool", "minecraft:iron_nugget", "minecraft:chain"),
    "paladin": ("minecraft:white_wool", "minecraft:gold_nugget", "minecraft:sunflower"),
    "barbarian": ("minecraft:brown_wool", "minecraft:leather", "minecraft:bone"),
    "monk": ("minecraft:orange_wool", "minecraft:string", "minecraft:bamboo"),
    "knight": ("minecraft:blue_wool", "minecraft:iron_nugget", "minecraft:iron_ingot"),
    "ranger": ("minecraft:green_wool", "minecraft:leather", "minecraft:arrow"),
    "rogue": ("minecraft:black_wool", "minecraft:iron_nugget", "minecraft:ink_sac"),
    "bard": ("minecraft:pink_wool", "minecraft:gold_nugget", "minecraft:note_block"),
    "artificer": ("minecraft:brown_wool", "minecraft:copper_ingot", "minecraft:copper_ingot"),
    "hunter": ("minecraft:brown_wool", "minecraft:leather", "minecraft:rabbit_hide"),
}


# ------------------------------------------------------------------ traducoes das classes novas (pt_br + en_us)
#  classe: (pecas pt, pecas en, sufixos pt por grau I..IV, prefixos en por grau I..IV)
V9_NAMES = {
    "warrior": (("Elmo Nasal", "Cota de Malha e Capa", "Calças Acolchoadas", "Botas Ferradas"),
                ("Nasal Helm", "Chainmail and Cape", "Padded Trousers", "Hobnailed Boots"),
                ("de Guerreiro", "do Legionário", "do Gladiador", "do Senhor da Guerra"),
                ("Warrior's", "Legionnaire's", "Gladiator's", "Warlord's")),
    "paladin": (("Elmo Solar", "Peitoral com Tabardo do Sol", "Coxotes de Placas", "Escarpes de Placas"),
                ("Sun Helm", "Sun Tabard Cuirass", "Plate Cuisses", "Plate Sabatons"),
                ("do Escudeiro da Luz", "do Cruzado da Aurora", "do Templário Solar", "do Campeão Celestial"),
                ("Lightbearer's", "Dawn Crusader's", "Solar Templar's", "Celestial Champion's")),
    "barbarian": (("Capuz de Pele de Lobo", "Colete de Couro e Crânio", "Tanga de Pele", "Botas de Pele"),
                  ("Wolf Pelt Hood", "Hide Vest and Skull", "Fur Loincloth", "Fur Boots"),
                  ("de Bárbaro", "do Saqueador", "do Chefe Tribal", "da Fúria Ancestral"),
                  ("Barbarian's", "Raider's", "Tribal Chief's", "Ancestral Fury")),
    "monk": (("Faixa de Testa", "Gi com Contas de Oração", "Calças do Gi", "Ataduras de Pés"),
             ("Headband", "Prayer-Bead Gi", "Gi Trousers", "Foot Wraps"),
             ("de Discípulo", "da Disciplina do Vento", "do Mestre do Templo", "do Punho Celestial"),
             ("Disciple's", "Wind Discipline", "Temple Master's", "Celestial Fist")),
    "knight": (("Elmo de Pluma", "Armadura com Tabardo", "Grevas de Placas", "Escarpes de Aço"),
               ("Plumed Great Helm", "Tabard Plate Armor", "Plate Greaves", "Steel Sabatons"),
               ("de Cavaleiro", "do Cavaleiro da Ordem", "do Cavaleiro Real", "do Lorde Bastião"),
               ("Knight's", "Order Knight's", "Royal Knight's", "Bastion Lord's")),
    "ranger": (("Capuz Verde", "Gibão com Aljava e Capa", "Calças de Couro", "Botas de Trilha"),
               ("Green Hood", "Quiver and Cloak Jerkin", "Leather Trousers", "Trail Boots"),
               ("de Patrulheiro", "do Guardião da Floresta", "da Sentinela do Vento", "da Lenda Verdejante"),
               ("Ranger's", "Forest Warden's", "Wind Sentinel's", "Verdant Legend's")),
    "rogue": (("Capuz e Máscara", "Gibão de Adagas", "Calças de Sombra", "Botas Silenciosas"),
              ("Hood and Mask", "Dagger-Belt Jerkin", "Shadow Trousers", "Silent Boots"),
              ("de Ladino", "da Sombra Urbana", "do Mestre das Sombras", "da Lâmina do Crepúsculo"),
              ("Rogue's", "Alley Shadow's", "Shadowmaster's", "Twilight Blade's")),
    "bard": (("Chapéu de Pluma", "Gibão Colorido", "Meias Partidas e Calções", "Botas de Fivela"),
             ("Plumed Hat", "Colorful Doublet", "Mi-Parti Hose", "Buckled Boots"),
             ("de Bardo", "do Trovador", "do Menestrel Real", "da Voz Celestial"),
             ("Bard's", "Troubadour's", "Royal Minstrel's", "Celestial Voice")),
    "artificer": (("Touca com Óculos de Proteção", "Avental de Engrenagens e Mochila", "Calças de Oficina",
                   "Botas de Bico de Latão"),
                  ("Goggled Cap", "Gear Apron and Pack", "Workshop Trousers", "Brass-Toed Boots"),
                  ("de Artífice", "do Engenheiro Arcano", "do Mestre Inventor", "do Arquiteto Etéreo"),
                  ("Artificer's", "Arcane Engineer's", "Master Inventor's", "Ethereal Architect's")),
    "hunter": (("Chapéu de Aba Larga", "Casaco com Manto de Pele", "Calças de Couro Curtido", "Botas Forradas de Pele"),
               ("Brimmed Hat", "Fur-Mantled Coat", "Tanned Leather Trousers", "Fur-Lined Boots"),
               ("de Caçador", "do Rastreador", "do Caçador de Feras", "do Senhor da Caçada"),
               ("Hunter's", "Tracker's", "Beast Hunter's", "Huntlord's")),
}
V9_FLAVOR = {
    "warrior": (("Anéis de aço que já pararam mais lâminas do que você imagina.",
                 "O carmesim da legião: cada fivela de bronze foi ganha em batalha.",
                 "Aço escurecido e ouro de gladiador; a arena inteira grita seu nome.",
                 "As brasas da guerra dormem nas runas — e acordam quando você avança."),
                ("Steel rings that have stopped more blades than you'd think.",
                 "Legion crimson: every bronze buckle was earned in battle.",
                 "Blackened steel and gladiator's gold; the whole arena chants your name.",
                 "The embers of war sleep in the runes — and wake when you charge.")),
    "paladin": (("Placas brancas e um sol que nunca se põe sobre a testa.",
                 "Prata da aurora: a primeira luz do dia guarda quem a veste.",
                 "O sol do templo arde no peito; a escuridão recua um passo.",
                 "Asas, auréola e ouro celeste — a luz desceu para lutar ao seu lado."),
                ("White plates and a sun that never sets upon the brow.",
                 "Dawn silver: the first light of day guards its wearer.",
                 "The temple sun burns on the chest; darkness steps back.",
                 "Wings, halo and celestial gold — the light came down to fight beside you.")),
    "barbarian": (("O lobo que vestia esta pele perdeu a briga. Você não vai perder.",
                   "Pelagem cinzenta e ferro de saque; cheira a fumaça de acampamento.",
                   "Lobo branco e pintura de guerra: a tribo inteira segue seus passos.",
                   "Fúria antiga ardendo nos ossos — o chão treme antes do seu grito."),
                  ("The wolf that wore this pelt lost the fight. You won't.",
                   "Grey fur and plundered iron; it smells of campfire smoke.",
                   "White wolf and war paint: the whole tribe follows your steps.",
                   "Ancient fury burning in the bones — the ground shakes before your roar.")),
    "monk": (("Tecido simples, contas gastas e a disciplina de mil manhãs.",
              "Leve como o vento do mosteiro; o ki corre pelas faixas.",
              "O carmesim dos mestres do templo: cada conta guarda uma lição.",
              "O rosário flutua sozinho; seu punho carrega a calma do céu."),
             ("Simple cloth, worn beads and the discipline of a thousand mornings.",
              "Light as the monastery wind; ki flows through the wraps.",
              "Temple master crimson: every bead keeps a lesson.",
              "The rosary floats on its own; your fist carries the calm of the sky.")),
    "knight": (("Aço completo, pluma ao vento e a torre do brasão no peito.",
                "O azul da Ordem: juramentos gravados em prata.",
                "Púrpura e ouro do reino; até os dragões respeitam a coroa no elmo.",
                "Um bastião que anda: as fendas do elmo brilham como brasas de forja."),
               ("Full steel, plume in the wind and the tower crest on the chest.",
                "Order blue: oaths engraved in silver.",
                "Royal purple and gold; even dragons respect the crown on the helm.",
                "A walking bastion: the visor slits glow like forge embers.")),
    "ranger": (("Verde de folha, flechas prontas e o silêncio da trilha.",
                "Os guardiões da floresta costuram folhas de prata no capuz.",
                "Leve como a brisa; as flechas acertam antes do vento mudar.",
                "As folhas brilham com a seiva da floresta antiga — a mata te chama de lenda."),
               ("Leaf green, arrows ready and the silence of the trail.",
                "Forest wardens stitch silver leaves into the hood.",
                "Light as the breeze; arrows land before the wind changes.",
                "The leaves glow with ancient sap — the woods call you a legend.")),
    "rogue": (("Ninguém viu quem foi. Era você.",
               "Carmesim de beco: lâminas escondidas em cada costura.",
               "Violeta e prata das sombras; os guardas juram que você não estava lá.",
               "As adagas brilham como o crepúsculo — a última cor que o alvo vê."),
              ("Nobody saw who it was. It was you.",
               "Alley crimson: blades hidden in every seam.",
               "Shadow violet and silver; the guards swear you were never there.",
               "The daggers glow like dusk — the last color the target sees.")),
    "bard": (("Cores demais, pluma demais — e uma canção para cada uma.",
              "O trovador veste o mar e o coral; as notas dançam na capa.",
              "Carmesim e ouro da corte: a plateia real pede bis.",
              "Uma voz celestial bordada em rosa; até o silêncio para para ouvir."),
             ("Too many colors, too many plumes — and a song for each.",
              "The troubadour wears sea and coral; notes dance on the cape.",
              "Court crimson and gold: the royal audience calls for an encore.",
              "A celestial voice stitched in rose; even silence stops to listen.")),
    "artificer": (("Óculos de proteção, avental queimado e uma ideia brilhante por minuto.",
                   "Engrenagens arcanas giram sozinhas; o manômetro brilha azul.",
                   "Cobre de forja e ouro de inventor — a oficina inteira cabe nas costas.",
                   "Núcleos etéreos pulsam no peito: a máquina e a magia viraram uma só."),
                  ("Goggles, a scorched apron and one brilliant idea per minute.",
                   "Arcane gears turn on their own; the gauge glows blue.",
                   "Forge copper and inventor's gold — the whole workshop fits on your back.",
                   "Ethereal cores pulse in the chest: machine and magic became one.")),
    "hunter": (("Couro curtido, presas de troféu e o cheiro da floresta.",
                "Pele de lobo cinzento; o rastreador lê a lua como um mapa.",
                "Pele branca e presas de ouro: as feras sabem quem chegou.",
                "Os olhos do manto brilham no escuro — a caçada começou."),
               ("Tanned leather, trophy fangs and the smell of the forest.",
                "Grey wolf pelt; the tracker reads the moon like a map.",
                "White fur and golden fangs: the beasts know who has arrived.",
                "The mantle's eyes glow in the dark — the hunt has begun.")),
}
V9_LANG_EXTRA = {
    "pt_br": {
        "outfit.isekai_mahou.tier.apprentice.martial": "Escudeiro",
        "outfit.isekai_mahou.tier.adept.martial": "Veterano",
        "outfit.isekai_mahou.tier.master.martial": "Mestre de Armas",
        "outfit.isekai_mahou.tier.archmage.martial": "Campeão",
        "outfit.isekai_mahou.tier.apprentice.ranged": "Novato",
        "outfit.isekai_mahou.tier.adept.ranged": "Batedor",
        "outfit.isekai_mahou.tier.master.ranged": "Mestre",
        "outfit.isekai_mahou.tier.archmage.ranged": "Lenda",
        "outfit.isekai_mahou.stat.resource": "+%s %s",
        "outfit.isekai_mahou.stat.regen": "+%s%% Regeneração de %s",
        "outfit.isekai_mahou.stat.spell_power": "+%s%% Poder Mágico",
        "outfit.isekai_mahou.stat.max_health": "+%s de Vida",
        "outfit.isekai_mahou.stat.attack_damage": "+%s de Dano Corpo a Corpo",
        "outfit.isekai_mahou.stat.movement_speed": "+%s%% de Velocidade",
        "outfit.isekai_mahou.stat.sneaking_speed": "+%s%% Andando Agachado",
        "outfit.isekai_mahou.stat.safe_fall_distance": "+%s Blocos de Queda Segura",
        "message.isekai_mahou.outfits.stats_head": "Conjunto completo: Defesa %s · Resistência %s",
        "message.isekai_mahou.outfits.sep": " · ",
        "command.isekai_mahou.outfit.unknown": "Traje desconhecido. Use: mage, witch, warlock, druid, necromancer, warrior, paladin, barbarian, monk, knight, ranger, rogue, bard, artificer ou hunter.",
    },
    "en_us": {
        "outfit.isekai_mahou.tier.apprentice.martial": "Squire",
        "outfit.isekai_mahou.tier.adept.martial": "Veteran",
        "outfit.isekai_mahou.tier.master.martial": "Weapon Master",
        "outfit.isekai_mahou.tier.archmage.martial": "Champion",
        "outfit.isekai_mahou.tier.apprentice.ranged": "Novice",
        "outfit.isekai_mahou.tier.adept.ranged": "Scout",
        "outfit.isekai_mahou.tier.master.ranged": "Master",
        "outfit.isekai_mahou.tier.archmage.ranged": "Legend",
        "outfit.isekai_mahou.stat.resource": "+%s %s",
        "outfit.isekai_mahou.stat.regen": "+%s%% %s Regeneration",
        "outfit.isekai_mahou.stat.spell_power": "+%s%% Spell Power",
        "outfit.isekai_mahou.stat.max_health": "+%s Max Health",
        "outfit.isekai_mahou.stat.attack_damage": "+%s Melee Damage",
        "outfit.isekai_mahou.stat.movement_speed": "+%s%% Speed",
        "outfit.isekai_mahou.stat.sneaking_speed": "+%s%% Sneaking Speed",
        "outfit.isekai_mahou.stat.safe_fall_distance": "+%s Safe Fall Blocks",
        "message.isekai_mahou.outfits.stats_head": "Full set: Armor %s · Toughness %s",
        "message.isekai_mahou.outfits.sep": " · ",
        "command.isekai_mahou.outfit.unknown": "Unknown outfit. Use: mage, witch, warlock, druid, necromancer, warrior, paladin, barbarian, monk, knight, ranger, rogue, bard, artificer or hunter.",
    },
}
V9_LANG_REMOVED = ("message.isekai_mahou.outfits.stats",)


def v09_lang():
    """Junta as chaves novas nos arquivos de traducao do modulo (mantendo as antigas e a ordem delas)."""
    tiers = ("apprentice", "adept", "master", "archmage")
    for lang in ("pt_br", "en_us"):
        path = os.path.join(ROOT, "src/main/lang/outfits", lang + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for k in V9_LANG_REMOVED:
            data.pop(k, None)
        for cid in V9_ORDER:
            pt_pieces, en_pieces, pt_suf, en_pre = V9_NAMES[cid]
            flav = V9_FLAVOR[cid][0 if lang == "pt_br" else 1]
            for t, tier in enumerate(tiers):
                for p, piece in enumerate(PIECES):
                    item = f"item.isekai_mahou.{cid}_{piece}" if t == 0 else f"item.isekai_mahou.{cid}_{tier}_{piece}"
                    data[item] = f"{pt_pieces[p]} {pt_suf[t]}" if lang == "pt_br" else f"{en_pre[t]} {en_pieces[p]}"
                key = f"tooltip.isekai_mahou.outfit.{cid}" if t == 0 else f"tooltip.isekai_mahou.outfit.{cid}_{tier}"
                data[key] = flav[t]
        data.update(V9_LANG_EXTRA[lang])
        write_json(path, data)


SETS.update(V9_SETS)


# ------------------------------------------------------------------ v1.0: trajes da v0.9 avulsos + inflacao
#  As classes da v0.9 sairam: os 10 trajes delas ficam como equipamento AVULSO (sem classe). Os numeros (defesa x1,5,
#  resistencia, mana x3, vida x2) ficam no Java (OutfitTier/OutfitStats); aqui so os textos novos.
V10_LANG = {
    "pt_br": {
        "tooltip.isekai_mahou.outfit.generic": "Traje avulso (%s) — não pertence a nenhuma classe",
        "command.isekai_mahou.outfit.done_generic": "Traje avulso vestido: %s (%s · %s).",
    },
    "en_us": {
        "tooltip.isekai_mahou.outfit.generic": "Standalone outfit (%s) — not tied to any class",
        "command.isekai_mahou.outfit.done_generic": "Standalone outfit equipped: %s (%s · %s).",
    },
}


def v10_lang():
    """Junta as chaves da v1.0 nos arquivos de traducao do modulo (mantendo o resto)."""
    for lang in ("pt_br", "en_us"):
        path = os.path.join(ROOT, "src/main/lang/outfits", lang + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        data.update(V10_LANG[lang])
        write_json(path, data)


def main(only=None):
    print("== outfits: modelos e atlas")
    built = {}
    for cid, mats_fn, model_fn in (("mage", mage_mats, mage_model), ("witch", witch_mats, witch_model),
                                   ("warlock", warlock_mats, warlock_model), ("druid", druid_mats, druid_model),
                                   ("necromancer", necro_mats, necro_model)):
        m = model_fn()
        _, glow = build(m, mats_fn())
        built[cid] = glow
    print("  glowmask:", {k: v for k, v in built.items()})
    print("== reserva vanilla")
    vanilla_layers("mage", H("22337A"), H("C4962E"), H("162254"))
    vanilla_layers("witch", H("5A2A7E"), H("C8C8D2"), H("17121D"))
    vanilla_layers("warlock", H("1A171F"), H("A81A1A"), H("070609"))
    vanilla_layers("druid", H("3E7426"), H("77542F"), H("28501A"))
    vanilla_layers("necromancer", H("26262E"), H("D0C8AC"), H("101014"))
    print("== icones")
    for name, (palette, rows) in ICONS.items():
        draw_icon(name, palette, rows)
    print("== receitas e tags")
    recipes()
    build_tiers(only)
    build_v09(only)
    print("== receitas e tags dos graus")
    tier_recipes()
    print("== traducoes das classes novas")
    v09_lang()
    print("== traducoes da v1.0")
    v10_lang()
    print("ok")


if __name__ == "__main__":
    # py -I tools/gen/outfits.py [classe ...]  -> sem argumentos gera tudo; com classes, so os graus delas
    # py -I tools/gen/outfits.py --lang-v10     -> so as traducoes da v1.0
    if "--lang-v10" in sys.argv:
        v10_lang()
    else:
        main(set(sys.argv[1:]) or None)
