"""
Gera os assets e dados do modulo armory (Arsenal: armas de classe, acessorios, molde de melhoria, marcos de nivel).
Uso (na raiz do projeto):  py -I tools/gen/armory.py            (opcional: --preview <pasta> desenha as armas em PNG)

Saidas:
  assets/isekai_mahou/models/item/<arma>.json              icone 2D no inventario/quadro + modelo 3D na mao/chao
                                                            (loader neoforge:separate_transforms)
  assets/isekai_mahou/models/item/armory/<arma>_held.json  modelo 3D (estilo Blockbench, "elements")
  assets/isekai_mahou/textures/item/armory/<arma>.png      atlas 32x32 do modelo 3D (gerado junto com os UVs)
  assets/isekai_mahou/textures/item/<id>.png               icones 16x16 (armas, acessorios, molde, diamante, espacos vazios)
  data/isekai_mahou/recipe/armory/*.json                   receitas (tier I, acessorios, molde) + smithing_transform
  data/isekai_mahou/advancement/recipes/armory/*.json      desbloqueio no livro de receitas
  data/isekai_mahou/guild_shop/armory.json                 ofertas da loja da Guilda por rank (v0.7; so e criado se faltar)
  data/isekai_mahou/guild_shop/armory_classes.json         ofertas das armas das classes v0.9 + Escudo do Cavaleiro
  models/item/armory/<arma>_<estado>.json                  v0.9: estados do arco (pulling_0..2), das bestas (pulling_0..2,
                                                            arrow, firework), do alaude (playing) e do escudo (blocking)
  src/main/merge/armory/...                                tags do Curios (charm/ring/necklace), minecraft:swords,
                                                            enchantable/bow|crossbow|durability, c:tools/*, #isekai_mahou:instruments
  src/main/lang/armory/{pt_br,en_us}.json                  traducoes

Modelos 3D: cada arma e montada com caixas de materiais nomeados. O atlas e gerado automaticamente: cada face
distinta (material + tamanho) ganha um retangulo proprio pintado pelo "pintor" do material, com ~1 texel por unidade
(como o vanilla). Gemas/runas brilham no escuro ("neoforge_data" com luz 15, sem sombra).
Espaco do modelo (unidades 0..16 = 1 bloco; o jogo aceita -16..32): X/Z centrados em 8, Y para cima, a EMPUNHADURA
perto de y=8 (o centro do modelo vai na mao). Na mao em 3a pessoa, +Y do item aponta para a FRENTE e +Z para CIMA;
por isso cajados usam rotacao X de 75 graus (em pe, levemente inclinados para frente).
"""
import math
import os
import random
import sys
import zlib

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from pixelart import Canvas, lighten, darken, mix  # noqa: E402


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# =====================================================================================================
#  Pintores de material: pintam um retangulo (x, y, w, h) do atlas
# =====================================================================================================
class Mat:
    """Material: estilo de pintura + paleta (hi, lt, md, dk) + se brilha."""

    def __init__(self, style, hi, lt, md, dk, glow=False, accent=None):
        self.style = style
        self.hi, self.lt, self.md, self.dk = rgb(hi), rgb(lt), rgb(md), rgb(dk)
        self.glow = glow
        self.accent = rgb(accent) if accent else self.hi

    def paint(self, c, x0, y0, w, h, seed):
        rnd = random.Random(seed)
        getattr(self, "_" + self.style)(c, x0, y0, w, h, rnd)

    # ---- estilos ----
    def _wood(self, c, x0, y0, w, h, rnd):
        vertical = h >= w
        n = w if vertical else h
        cols = [rnd.choice((self.md, self.md, self.lt, self.dk)) for _ in range(max(1, n))]
        for yy in range(h):
            for xx in range(w):
                i = xx if vertical else yy
                col = cols[i]
                if rnd.random() < 0.12:
                    col = self.dk if col != self.dk else self.md
                c.set(x0 + xx, y0 + yy, col)
        # nos da madeira
        for _ in range(max(1, (w * h) // 40)):
            kx, ky = x0 + rnd.randrange(w), y0 + rnd.randrange(h)
            c.set(kx, ky, darken(self.dk, 0.2))

    def _bark(self, c, x0, y0, w, h, rnd):
        self._wood(c, x0, y0, w, h, rnd)
        vertical = h >= w
        for _ in range(max(1, (w * h) // 12)):
            kx, ky = x0 + rnd.randrange(w), y0 + rnd.randrange(h)
            c.set(kx, ky, self.dk)
            if vertical and ky + 1 < y0 + h:
                c.set(kx, ky + 1, self.dk)
            elif not vertical and kx + 1 < x0 + w:
                c.set(kx + 1, ky, self.dk)
        for _ in range(max(1, (w * h) // 30)):
            c.set(x0 + rnd.randrange(w), y0 + rnd.randrange(h), self.accent)

    def _metal(self, c, x0, y0, w, h, rnd):
        for yy in range(h):
            for xx in range(w):
                col = self.md
                if yy == 0 or xx == 0:
                    col = self.lt
                if yy == h - 1 or xx == w - 1:
                    col = self.dk
                if (yy == 0 and xx == 0) or (w > 2 and h > 2 and xx == 1 and yy == 1):
                    col = self.hi
                if col == self.md and rnd.random() < 0.15:
                    col = self.lt if rnd.random() < 0.5 else darken(self.md, 0.08)
                c.set(x0 + xx, y0 + yy, col)

    def _gem(self, c, x0, y0, w, h, rnd):
        tot = max(1, w + h - 2)
        for yy in range(h):
            for xx in range(w):
                t = (xx + yy) / tot
                if t < 0.25:
                    col = self.lt
                elif t < 0.6:
                    col = self.md
                elif t < 0.85:
                    col = mix(self.md, self.dk, 0.5)
                else:
                    col = self.dk
                c.set(x0 + xx, y0 + yy, col)
        c.set(x0, y0, self.hi)
        if w > 2 and h > 2:
            c.set(x0 + 1, y0, self.hi)
            c.set(x0 + w - 2, y0 + h - 2, lighten(self.md, 0.15))

    def _glow(self, c, x0, y0, w, h, rnd):
        for yy in range(h):
            for xx in range(w):
                edge = xx in (0, w - 1) or yy in (0, h - 1)
                c.set(x0 + xx, y0 + yy, self.md if edge and w > 2 and h > 2 else self.lt)
        cx, cy = x0 + w // 2, y0 + h // 2
        c.set(cx, cy, self.hi)

    def _wrap(self, c, x0, y0, w, h, rnd):
        vertical = h >= w
        for yy in range(h):
            for xx in range(w):
                k = (yy if vertical else xx) + (xx if vertical else yy)
                col = self.md if k % 3 else self.dk
                if k % 3 == 1:
                    col = self.lt
                c.set(x0 + xx, y0 + yy, col)

    def _bone(self, c, x0, y0, w, h, rnd):
        for yy in range(h):
            for xx in range(w):
                col = self.lt if rnd.random() < 0.6 else self.md
                c.set(x0 + xx, y0 + yy, col)
        for _ in range(max(1, (w * h) // 18)):
            c.set(x0 + rnd.randrange(w), y0 + rnd.randrange(h), self.dk)
        c.set(x0, y0, self.hi)

    def _rune(self, c, x0, y0, w, h, rnd):
        for yy in range(h):
            for xx in range(w):
                c.set(x0 + xx, y0 + yy, self.dk)
        vertical = h > w
        n = h if vertical else w
        for i in range(n):
            if rnd.random() < 0.55:
                if vertical:
                    c.set(x0 + rnd.randrange(w), y0 + i, self.lt if rnd.random() < 0.7 else self.hi)
                else:
                    c.set(x0 + i, y0 + rnd.randrange(h), self.lt if rnd.random() < 0.7 else self.hi)

    def _blade(self, c, x0, y0, w, h, rnd):
        vertical = h >= w
        for yy in range(h):
            for xx in range(w):
                i = xx if vertical else yy
                n = w if vertical else h
                if i == 0:
                    col = self.hi
                elif i == n - 1:
                    col = self.lt
                elif i == 1:
                    col = self.lt
                else:
                    col = self.md
                if col == self.md and rnd.random() < 0.1:
                    col = self.dk
                c.set(x0 + xx, y0 + yy, col)

    def _cloth(self, c, x0, y0, w, h, rnd):
        for yy in range(h):
            for xx in range(w):
                col = self.md if (xx + yy) % 2 else self.lt
                if rnd.random() < 0.1:
                    col = self.dk
                c.set(x0 + xx, y0 + yy, col)

    def _leaf(self, c, x0, y0, w, h, rnd):
        for yy in range(h):
            for xx in range(w):
                col = self.md
                if xx == w // 2 or yy == h // 2:
                    col = self.dk
                elif rnd.random() < 0.3:
                    col = self.lt
                c.set(x0 + xx, y0 + yy, col)
        c.set(x0, y0, self.hi)

    def avg(self):
        return self.md


# =====================================================================================================
#  Modelo + atlas
# =====================================================================================================
FACES = ("north", "south", "east", "west", "up", "down")


class Model:
    def __init__(self, name, atlas=32):
        self.name = name
        self.atlas = atlas
        self.elements = []

    def box(self, frm, to, mat, rot=None, faces=FACES, shade=True):
        """Caixa de frm=(x,y,z) a to=(x,y,z). rot=(eixo, angulo, (ox,oy,oz)). mat: Mat."""
        x0, y0, z0 = frm
        x1, y1, z1 = to
        frm = (min(x0, x1), min(y0, y1), min(z0, z1))
        to = (max(x0, x1), max(y0, y1), max(z0, z1))
        for v in frm + to:
            assert -16 <= v <= 32, (self.name, frm, to)
        if rot:
            assert rot[1] in (-45, -22.5, 0, 22.5, 45), rot
        self.elements.append({"from": frm, "to": to, "mat": mat, "rot": rot, "faces": faces, "shade": shade})
        return self

    def cbox(self, cx, cy, cz, sx, sy, sz, mat, rot_y45=False, rot=None, faces=FACES, shade=True):
        """Caixa centrada em (cx,cy,cz) com tamanho (sx,sy,sz); rot_y45 gira 45 graus em Y no proprio centro."""
        if rot_y45:
            rot = ("y", 45, (cx, cy, cz))
        return self.box((cx - sx / 2, cy - sy / 2, cz - sz / 2), (cx + sx / 2, cy + sy / 2, cz + sz / 2), mat,
                        rot=rot, faces=faces, shade=shade)

    # ------------------------------------------------------------------ atlas
    @staticmethod
    def _face_size(frm, to, face):
        dx, dy, dz = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
        if face in ("north", "south"):
            return dx, dy
        if face in ("east", "west"):
            return dz, dy
        return dx, dz

    def build(self, texture_id, seed=1):
        """Gera (json_do_modelo_sem_display, Canvas do atlas)."""
        for density in (1.0, 0.75, 0.5, 0.35):
            ok, packed = self._pack(density)
            if ok:
                break
        else:
            raise RuntimeError("atlas cheio: " + self.name)
        canvas = Canvas(self.atlas, self.atlas)
        rects = {}
        for i, (key, (px, py, pw, ph)) in enumerate(packed.items()):
            mat = key[0]
            mat.paint(canvas, px, py, pw, ph, seed * 7919 + i * 31)
            rects[key] = (px, py, pw, ph)
        s = 16.0 / self.atlas
        elems = []
        for el in self.elements:
            faces = {}
            for f in el["faces"]:
                w, h = self._face_size(el["from"], el["to"], f)
                if w <= 0 or h <= 0:
                    continue
                key = self._key(el["mat"], w, h, density)
                px, py, pw, ph = rects[key]
                face = {"uv": [round(px * s, 4), round(py * s, 4), round((px + pw) * s, 4), round((py + ph) * s, 4)],
                        "texture": "#0"}
                faces[f] = face
            e = {"from": [round(v, 4) for v in el["from"]], "to": [round(v, 4) for v in el["to"]], "faces": faces}
            if el["rot"]:
                axis, ang, origin = el["rot"]
                e["rotation"] = {"angle": ang, "axis": axis, "origin": [round(v, 4) for v in origin]}
            if el["mat"].glow:
                e["shade"] = False
                e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
            elif not el["shade"]:
                e["shade"] = False
            elems.append(e)
        model = {
            "credit": "Isekai Mahou - tools/gen/armory.py",
            "texture_size": [self.atlas, self.atlas],
            "textures": {"0": texture_id, "particle": texture_id},
            "elements": elems,
        }
        return model, canvas

    @staticmethod
    def _key(mat, w, h, density):
        tw = max(1, min(32, int(math.ceil(w * density - 0.01))))
        th = max(1, min(32, int(math.ceil(h * density - 0.01))))
        return (mat, tw, th)

    def _pack(self, density):
        keys = []
        for el in self.elements:
            for f in el["faces"]:
                w, h = self._face_size(el["from"], el["to"], f)
                if w <= 0 or h <= 0:
                    continue
                k = self._key(el["mat"], w, h, density)
                if k not in keys:
                    keys.append(k)
        # prateleiras: os mais altos primeiro
        order = sorted(keys, key=lambda k: (-k[2], -k[1]))
        packed = {}
        x = y = shelf_h = 0
        for k in order:
            _, w, h = k
            if w > self.atlas or h > self.atlas:
                return False, None
            if x + w > self.atlas:
                x = 0
                y += shelf_h
                shelf_h = 0
            if y + h > self.atlas:
                return False, None
            packed[k] = (x, y, w, h)
            x += w
            shelf_h = max(shelf_h, h)
        return True, packed


# =====================================================================================================
#  Pre-visualizacao (renderizador de software simples) para conferir as formas
# =====================================================================================================
def _rot_point(p, rot):
    if not rot:
        return p
    axis, ang, o = rot
    a = math.radians(ang)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    ca, sa = math.cos(a), math.sin(a)
    if axis == "x":
        y, z = y * ca - z * sa, y * sa + z * ca
    elif axis == "y":
        x, z = x * ca + z * sa, -x * sa + z * ca
    else:
        x, y = x * ca - y * sa, x * sa + y * ca
    return (x + o[0], y + o[1], z + o[2])


def preview(model, path, yaw=30.0, pitch=15.0, scale=6, size=(260, 300), center=(8, 12, 8)):
    """Desenha o modelo (faces com a cor media do material e sombreamento simples) num PNG de conferencia."""
    W, H = size
    img = Canvas(W, H, (40, 40, 48, 255))
    zbuf = [[-1e9] * W for _ in range(H)]
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p[0] - center[0], p[1] - center[1], p[2] - center[2]
        x, z = x * cy_ + z * sy_, -x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return (W / 2 + x * scale, H / 2 - y * scale, z)

    for el in model.elements:
        f, t = el["from"], el["to"]
        corners = {}
        for i, xx in enumerate((f[0], t[0])):
            for j, yy in enumerate((f[1], t[1])):
                for k, zz in enumerate((f[2], t[2])):
                    corners[(i, j, k)] = view(_rot_point((xx, yy, zz), el["rot"]))
        quads = {
            "north": [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], "south": [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
            "west": [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], "east": [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)],
            "down": [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
        }
        shadef = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
        for face, q in quads.items():
            pts = [corners[c] for c in q]
            col = el["mat"].avg()
            if not el["mat"].glow:
                col = darken(col, 1 - shadef[face])
            else:
                col = el["mat"].lt
            _fill_quad(img, zbuf, pts, col)
    img.save(path)


def _fill_quad(img, zbuf, pts, col):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    minx, maxx = max(0, int(min(xs))), min(img.w - 1, int(max(xs)) + 1)
    miny, maxy = max(0, int(min(ys))), min(img.h - 1, int(max(ys)) + 1)
    tris = [(pts[0], pts[1], pts[2]), (pts[0], pts[2], pts[3])]
    for a, b, c in tris:
        den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(den) < 1e-9:
            continue
        for y in range(miny, maxy + 1):
            for x in range(minx, maxx + 1):
                px, py = x + 0.5, y + 0.5
                l1 = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / den
                l2 = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / den
                l3 = 1 - l1 - l2
                if l1 < 0 or l2 < 0 or l3 < 0:
                    continue
                z = l1 * a[2] + l2 * b[2] + l3 * c[2]
                if z > zbuf[y][x]:
                    zbuf[y][x] = z
                    img.set(x, y, col)


# =====================================================================================================
#  Materiais (paletas)
# =====================================================================================================
def M(style, hi, lt, md, dk, glow=False, accent=None):
    return Mat(style, hi, lt, md, dk, glow=glow, accent=accent)


# madeiras e cascas
OAK = M("wood", "d8b07a", "b48a55", "8f6a3e", "5e4226")
WALNUT = M("wood", "93683f", "7a5233", "5e3d24", "3b2414")
EBONY = M("wood", "6a5a80", "463a5c", "2e2640", "17121f")
IVORY = M("wood", "fffdf6", "f4ecda", "e0d4b8", "b8a888")
BIRCH = M("wood", "ffffff", "f0ebde", "d8d0bc", "5a5048")
CHERRY = M("wood", "e8a0a8", "b8606a", "8a3e4a", "4e1e28")
OBSIDIAN = M("wood", "6a4a8a", "3a2850", "241834", "100a18")
DARK_OAK = M("wood", "8a6a48", "5e4630", "433020", "241a10")
YOUNG_BARK = M("bark", "b89a70", "9a7a52", "7a5a38", "4e3820", accent="6aa040")
OLD_BARK = M("bark", "8a7458", "6e5a44", "54432f", "33281b", accent="4f8a3a")
SILVER_BARK = M("bark", "ffffff", "eeeae2", "cfc8b8", "8a8070", accent="ffd860")
BONE = M("bone", "ffffff", "f2ecdc", "dad1b6", "8a8068")

# metais
IRON = M("metal", "ffffff", "dcdcdc", "a8a8a8", "5e5e5e")
GOLD = M("metal", "fff6b0", "f5cc4a", "d49a22", "8f5a12")
SILVER = M("metal", "ffffff", "e6eef8", "aab6c8", "667086")
DARK_IRON = M("metal", "9a9aa8", "62626e", "41414c", "22222a")
BRONZE = M("metal", "ffe0a8", "d8a060", "a8703a", "5e3a18")

# empunhaduras / tecidos
LEATHER = M("wrap", "a87a52", "8a5a3a", "6b4228", "3e2414")
RED_WRAP = M("wrap", "ff8a8a", "b8303c", "801c26", "4a0e14")
BLUE_WRAP = M("wrap", "8ab8ff", "4a78d8", "2f55a8", "1a2f66")
ROYAL_WRAP = M("wrap", "a8c8ff", "3a5ad0", "24389a", "121c50")
PURPLE_WRAP = M("wrap", "e0a8ff", "a45ad8", "7a3aa8", "44205e")
BLACK_WRAP = M("wrap", "8a5a6a", "4a2a34", "2e1820", "140a0e")
VINE_WRAP = M("wrap", "c8f0a0", "7ab84a", "4f8a2f", "2e5a1a")
MOSS = M("cloth", "b8e890", "7ab84a", "4f8a2f", "2e5a1a")
SOUL_WRAP = M("wrap", "a8fff0", "2f8a88", "1e5a5c", "0e2e30")

# laminas
STEEL_BLADE = M("blade", "ffffff", "e0e6ee", "a8b2c0", "6a7484")
DARK_BLADE = M("blade", "e6e6f2", "a4a4b8", "6a6a7e", "3a3a48")
OBSIDIAN_BLADE = M("blade", "c8a8ff", "6a5084", "33244a", "180f26")
CRIMSON_METAL = M("metal", "ffb0b0", "c83a44", "8a1e28", "4a0a12")

# gemas (brilham)
GEM_PALE_BLUE = M("gem", "ffffff", "c8eaff", "7cc4ff", "3a7fd0", glow=True)
GEM_ARCANE = M("gem", "ffffff", "e3ccff", "b98cff", "6a3cc0", glow=True)
GEM_CYAN = M("gem", "ffffff", "e8fbff", "9feaff", "4fb8e0", glow=True)
GEM_PINK = M("gem", "ffffff", "ffd0f2", "f070d0", "a02c8c", glow=True)
GEM_VIOLET = M("gem", "ffffff", "e9b8ff", "c060ff", "7020b0", glow=True)
GEM_MAGENTA = M("gem", "ffffff", "ffb8e8", "ff4fc0", "a0107a", glow=True)
GEM_RED = M("gem", "ffffff", "ff9a9a", "e8303a", "8a0c16", glow=True)
GEM_GREEN = M("gem", "ffffff", "c8ffb8", "5fd85a", "2a8a2a", glow=True)
GEM_AMBER = M("gem", "fffbe0", "ffe08a", "ffb028", "b06a10", glow=True)
GEM_SUN = M("gem", "ffffff", "fffbd0", "ffe27a", "d0a020", glow=True)
GEM_SOUL = M("gem", "ffffff", "c8fff8", "4fe0d0", "1a8a88", glow=True)
PETAL = M("gem", "ffffff", "ffd8ea", "ff9ac8", "c85a90")

# runas e brilhos
RUNE_BLUE = M("rune", "ffffff", "8fd8ff", "3a6ab0", "1a2440", glow=True)
RUNE_GOLD = M("rune", "ffffff", "ffe680", "c89a30", "3a2a10", glow=True)
RUNE_VIOLET = M("rune", "ffffff", "e0a0ff", "8040c0", "24103a", glow=True)
RUNE_MAGENTA = M("rune", "ffffff", "ff9ae0", "c02090", "300a24", glow=True)
RUNE_CRIMSON = M("rune", "ffe0e0", "ff5a5a", "a01020", "2a0608", glow=True)
RUNE_GREEN = M("rune", "ffffff", "b0ff90", "40a030", "102a0c", glow=True)
RUNE_SOUL = M("rune", "ffffff", "8ffff0", "20a0a0", "082a2c", glow=True)
GLOW_CRIMSON = M("glow", "fff0f0", "ff5a5a", "c01a28", "600010", glow=True)
GLOW_SOUL = M("glow", "ffffff", "8ffff0", "30c8c0", "106060", glow=True)
GLOW_EYE = M("glow", "ffffff", "aaffff", "40e0e0", "108080", glow=True)
DARK = M("metal", "3a3a3a", "202020", "141414", "0a0a0a")

# folhas
LEAF = M("leaf", "c8ff9a", "8fe06a", "4fa83a", "2f6a22")
LEAF_DEEP = M("leaf", "a8e888", "5fa840", "3a7a28", "1e4a14")
LEAF_GOLD = M("leaf", "fff6c0", "ffe680", "e0b030", "906010")


# =====================================================================================================
#  Pecas reutilizaveis
# =====================================================================================================
def shaft(m, y0, y1, mat, th=2.0, ox=0.0, oz=0.0):
    h = th / 2
    m.box((8 - h + ox, y0, 8 - h + oz), (8 + h + ox, y1, 8 + h + oz), mat)


def band(m, y0, y1, mat, th=2.6, rot45=False):
    m.cbox(8, (y0 + y1) / 2, 8, th, y1 - y0, th, mat, rot_y45=rot45)


def gem(m, cx, cy, cz, w, h, mat, caps=False):
    """Gema girada 45 graus (losango visto de lado); caps = pontas em cima/embaixo (octaedro)."""
    m.cbox(cx, cy, cz, w, h, w, mat, rot_y45=True)
    if caps:
        cw = w * 0.55
        ch = max(0.6, h * 0.25)
        m.cbox(cx, cy + h / 2 + ch / 2, cz, cw, ch, cw, mat, rot_y45=True)
        m.cbox(cx, cy - h / 2 - ch / 2, cz, cw, ch, cw, mat, rot_y45=True)


def prong(m, x, z, y0, length, axis, ang, mat, th=1.0):
    """Dente/galho que sai de (x, y0, z) para cima, inclinado `ang` em torno de `axis` (pivo na base)."""
    m.box((x - th / 2, y0, z - th / 2), (x + th / 2, y0 + length, z + th / 2), mat, rot=(axis, ang, (x, y0, z)))


def claw(m, y0, length, off, mat, ang=22.5, th=1.0):
    """4 dentes em volta da gema, inclinados para fora."""
    prong(m, 8 - off, 8, y0, length, "z", ang, mat, th)
    prong(m, 8 + off, 8, y0, length, "z", -ang, mat, th)
    prong(m, 8, 8 - off, y0, length, "x", -ang, mat, th)
    prong(m, 8, 8 + off, y0, length, "x", ang, mat, th)


def diamond_frame(m, cx, cy, r, mat, th=1.0, depth=1.0, plane="xy", cz=8.0):
    """Moldura em losango (quadrado girado 45 graus) de 'raio' r, no plano XY (gira em Z) ou ZY (gira em X)."""
    L = r * math.sqrt(2) + th
    for sx, sy, ang in ((1, 1, -45), (-1, 1, 45), (-1, -1, -45), (1, -1, 45)):
        a = cx + sx * r / 2
        b = cy + sy * r / 2
        if plane == "xy":
            m.box((a - L / 2, b - th / 2, cz - depth / 2), (a + L / 2, b + th / 2, cz + depth / 2), mat,
                  rot=("z", ang, (a, b, cz)))
        else:
            # no plano ZY o eixo "horizontal" e Z; girar em X com o sinal trocado mantem o losango
            m.box((cx - depth / 2, b - th / 2, a - L / 2), (cx + depth / 2, b + th / 2, a + L / 2), mat,
                  rot=("x", -ang, (cx, b, a)))


def wing(m, x, y, side, length, height, ang, mat, depth=1.0):
    """Asa/ornamento que sai de (x,y) para o lado (side=-1 esquerda, +1 direita) subindo `ang` graus."""
    if side < 0:
        m.box((x - length, y - height / 2, 8 - depth / 2), (x, y + height / 2, 8 + depth / 2), mat, rot=("z", -ang, (x, y, 8)))
    else:
        m.box((x, y - height / 2, 8 - depth / 2), (x + length, y + height / 2, 8 + depth / 2), mat, rot=("z", ang, (x, y, 8)))


def twig(m, x, z, y0, l1, l2, axis, sign, mat, th=1.0, ang=22.5):
    """Galho que sai inclinado para fora e depois curva de volta (2 segmentos)."""
    a1 = sign * ang
    prong(m, x, z, y0, l1, axis, a1, mat, th)
    r = math.radians(a1)
    if axis == "z":
        tx, ty, tz = x - math.sin(r) * l1, y0 + math.cos(r) * l1, z
    else:
        tx, ty, tz = x, y0 + math.cos(r) * l1, z + math.sin(r) * l1
    prong(m, tx, tz, ty - 0.3, l2, axis, -a1, mat, th * 0.85)


def leaf(m, x, y, side, size, mat, ang=22.5, axis="z"):
    """Folha: placa fina inclinada."""
    if axis == "z":
        if side < 0:
            m.box((x - size, y - 0.2, 8 - size * 0.45), (x, y + 0.2, 8 + size * 0.45), mat, rot=("z", -ang, (x, y, 8)))
        else:
            m.box((x, y - 0.2, 8 - size * 0.45), (x + size, y + 0.2, 8 + size * 0.45), mat, rot=("z", ang, (x, y, 8)))
    else:
        if side < 0:
            m.box((8 - size * 0.45, y - 0.2, x - size), (8 + size * 0.45, y + 0.2, x), mat, rot=("x", ang, (8, y, x)))
        else:
            m.box((8 - size * 0.45, y - 0.2, x), (8 + size * 0.45, y + 0.2, x + size), mat, rot=("x", -ang, (8, y, x)))


def scythe_blade(m, start, segs, mat, edge, direction=-1, depth=1.0):
    """Lamina curva: segmentos (comprimento, angulo de queda, altura) a partir de `start` (canto de cima, perto do cabo).
    direction=-1 vai para -X; +1 para +X. A borda de corte (edge) fica embaixo de cada segmento."""
    px, py = start
    for (L, ang, h) in segs:
        a = ang if direction < 0 else -ang
        if direction < 0:
            frm, to = (px - L, py - h, 8 - depth / 2), (px, py, 8 + depth / 2)
            efrm, eto = (px - L, py - h - 0.05, 8 - depth / 2 - 0.05), (px, py - h + 0.6, 8 + depth / 2 + 0.05)
        else:
            frm, to = (px, py - h, 8 - depth / 2), (px + L, py, 8 + depth / 2)
            efrm, eto = (px, py - h - 0.05, 8 - depth / 2 - 0.05), (px + L, py - h + 0.6, 8 + depth / 2 + 0.05)
        rot = ("z", a, (px, py, 8)) if a else None
        m.box(frm, to, mat, rot=rot)
        m.box(efrm, eto, edge, rot=rot)
        r = math.radians(ang)
        dx, dy = math.cos(r) * (L - 0.35), math.sin(r) * (L - 0.35)
        px = px - dx if direction < 0 else px + dx
        py = py - dy


# =====================================================================================================
#  Armas: 5 tipos x 4 tiers
# =====================================================================================================
def mage_staff(t):
    m = Model("mage_staff_t%d" % t)
    if t == 1:
        band(m, -6.5, -5, IRON, 2.6)
        shaft(m, -5, 5, OAK)
        band(m, 5, 11, LEATHER, 2.4)
        shaft(m, 11, 20, OAK)
        band(m, 20, 21, IRON, 3.0)
        claw(m, 20.6, 3.4, 1.35, IRON, ang=22.5)
        gem(m, 8, 23.0, 8, 2.6, 3.4, GEM_PALE_BLUE)
    elif t == 2:
        band(m, -6.8, -5.2, GOLD, 2.6)
        shaft(m, -5.2, 5, WALNUT)
        band(m, 0.8, 1.6, GOLD, 2.4)
        band(m, 5, 11, BLUE_WRAP, 2.4)
        band(m, 11, 11.8, GOLD, 2.6)
        shaft(m, 11.8, 20.5, WALNUT)
        band(m, 20.5, 21.5, GOLD, 3.2)
        band(m, 21.5, 22.5, GOLD, 2.4, rot45=True)
        claw(m, 21.6, 4.4, 1.6, GOLD, ang=22.5)
        gem(m, 8, 24.6, 8, 3.4, 4.0, GEM_ARCANE, caps=True)
    elif t == 3:
        m.cbox(8, -7.5, 8, 1.2, 1.0, 1.2, SILVER, rot_y45=True)
        band(m, -7, -5.5, SILVER, 2.8)
        shaft(m, -5.5, 5, EBONY)
        band(m, 0, 2, RUNE_BLUE, 2.2)
        band(m, 5, 11, PURPLE_WRAP, 2.4)
        band(m, 11, 11.6, SILVER, 2.6)
        shaft(m, 11.6, 21, EBONY)
        band(m, 14, 16, RUNE_BLUE, 2.2)
        band(m, 20.5, 22, SILVER, 3.4)
        band(m, 22, 23, SILVER, 2.6, rot45=True)
        diamond_frame(m, 8, 26.6, 3.6, SILVER, th=1.0, depth=1.0)
        gem(m, 8, 26.6, 8, 2.8, 2.8, GEM_CYAN, caps=True)
        gem(m, 3.0, 25.0, 8, 1.2, 1.6, GEM_CYAN)
        gem(m, 13.0, 25.0, 8, 1.2, 1.6, GEM_CYAN)
        m.cbox(8, 30.8, 8, 1.0, 1.4, 1.0, SILVER, rot_y45=True)
    else:
        m.cbox(8, -8.25, 8, 1.2, 1.5, 1.2, GOLD, rot_y45=True)
        band(m, -7.5, -6, GOLD, 3.0)
        shaft(m, -6, 4.2, IVORY)
        band(m, -2.5, -1, RUNE_GOLD, 2.2)
        band(m, 4.2, 5, GOLD, 2.8)
        band(m, 5, 11, ROYAL_WRAP, 2.4)
        band(m, 11, 11.8, GOLD, 2.8)
        shaft(m, 11.8, 20.5, IVORY)
        band(m, 14, 15.5, RUNE_GOLD, 2.2)
        band(m, 17.5, 19, RUNE_GOLD, 2.2)
        band(m, 20.4, 21.6, GOLD, 3.6)
        wing(m, 7.0, 21.6, -1, 4.6, 1.2, 22.5, GOLD)
        wing(m, 9.0, 21.6, 1, 4.6, 1.2, 22.5, GOLD)
        wing(m, 6.8, 22.8, -1, 3.2, 0.8, 45, GOLD)
        wing(m, 9.2, 22.8, 1, 3.2, 0.8, 45, GOLD)
        diamond_frame(m, 8, 26.6, 4.6, GOLD, th=1.0, depth=1.0)
        diamond_frame(m, 8, 26.6, 3.4, SILVER, th=0.6, depth=0.6, plane="zy")
        gem(m, 8, 26.6, 8, 3.4, 3.4, GEM_CYAN, caps=True)
        gem(m, 1.6, 26.6, 8, 1.2, 1.8, GEM_ARCANE)
        gem(m, 14.4, 26.6, 8, 1.2, 1.8, GEM_ARCANE)
        gem(m, 8, 24.0, 3.0, 1.0, 1.4, GEM_ARCANE)
        gem(m, 8, 24.0, 13.0, 1.0, 1.4, GEM_ARCANE)
        m.box((7.5, 31.2, 7.5), (8.5, 32, 8.5), GOLD)
    return m


def witch_wand(t):
    m = Model("witch_wand_t%d" % t)
    if t == 1:
        m.cbox(8, 0.75, 8, 2.0, 1.5, 2.0, DARK_OAK)
        shaft(m, 1.5, 7.5, DARK_OAK, th=1.6)
        band(m, 7.5, 8.5, IRON, 2.0)
        shaft(m, 8.5, 15, BIRCH, th=1.2)
        shaft(m, 15, 18.5, BIRCH, th=1.0)
        gem(m, 8, 19.4, 8, 1.4, 1.8, GEM_VIOLET)
    elif t == 2:
        gem(m, 8, 0.8, 8, 1.6, 1.6, GEM_PINK)
        band(m, 1.6, 7.5, PURPLE_WRAP, 1.8)
        band(m, 7.5, 8.5, SILVER, 2.2)
        shaft(m, 8.5, 16, CHERRY, th=1.3)
        band(m, 13, 13.6, SILVER, 1.6)
        shaft(m, 16, 18.5, CHERRY, th=1.0)
        # meia-lua de prata abracando a gema
        m.box((9.1, 18.8, 7.6), (9.9, 21.2, 8.4), SILVER)
        m.cbox(8.9, 21.7, 8, 1.6, 0.7, 0.8, SILVER, rot=("z", 45, (8.9, 21.7, 8)))
        m.cbox(8.9, 18.3, 8, 1.6, 0.7, 0.8, SILVER, rot=("z", -45, (8.9, 18.3, 8)))
        gem(m, 8, 20.0, 8, 1.8, 2.2, GEM_PINK)
    elif t == 3:
        m.cbox(8, 0.6, 8, 1.0, 1.2, 1.0, SILVER, rot_y45=True)
        band(m, 1.2, 2.1, SILVER, 2.0)
        shaft(m, 2.1, 7.6, EBONY, th=1.8)
        band(m, 4, 5.5, RUNE_VIOLET, 2.0)
        band(m, 7.6, 8.6, SILVER, 2.6, rot45=True)
        shaft(m, 8.6, 13.5, EBONY, th=1.3)
        band(m, 11, 12, RUNE_VIOLET, 1.5)
        shaft(m, 13.5, 17.5, EBONY, th=1.1)
        prong(m, 7.4, 8, 17.2, 2.0, "z", 22.5, SILVER, 0.6)
        prong(m, 8.6, 8, 17.2, 2.0, "z", -22.5, SILVER, 0.6)
        # eclipse: disco escuro em losango atras + gema na frente
        m.cbox(8, 20.4, 8.5, 2.8, 2.8, 0.6, OBSIDIAN, rot=("z", 45, (8, 20.4, 8.5)))
        gem(m, 8, 20.4, 7.9, 1.8, 2.0, GEM_VIOLET)
    else:
        gem(m, 8, 0.3, 8, 1.2, 1.2, GEM_MAGENTA)
        band(m, 0.9, 2.0, GOLD, 2.2)
        shaft(m, 2.0, 7.6, OBSIDIAN, th=1.8)
        band(m, 3, 3.8, RUNE_MAGENTA, 2.0)
        band(m, 5.6, 6.4, RUNE_MAGENTA, 2.0)
        band(m, 7.6, 8.6, GOLD, 3.0, rot45=True)
        wing(m, 7.0, 8.4, -1, 2.6, 0.8, 22.5, GOLD, depth=0.8)
        wing(m, 9.0, 8.4, 1, 2.6, 0.8, 22.5, GOLD, depth=0.8)
        shaft(m, 8.6, 15, OBSIDIAN, th=1.3)
        band(m, 12, 12.6, GOLD, 1.7)
        shaft(m, 15, 18, OBSIDIAN, th=1.1)
        prong(m, 7.3, 8, 17.6, 2.2, "z", 22.5, GOLD, 0.6)
        prong(m, 8.7, 8, 17.6, 2.2, "z", -22.5, GOLD, 0.6)
        prong(m, 8, 7.3, 17.6, 2.2, "x", -22.5, GOLD, 0.6)
        gem(m, 8, 20.6, 8, 2.4, 3.0, GEM_MAGENTA, caps=True)
        m.box((10.0, 18.6, 7.6), (10.8, 22.6, 8.4), GOLD)
        m.cbox(9.8, 23.1, 8, 1.8, 0.7, 0.8, GOLD, rot=("z", 45, (9.8, 23.1, 8)))
        m.cbox(9.8, 18.1, 8, 1.8, 0.7, 0.8, GOLD, rot=("z", -45, (9.8, 18.1, 8)))
        gem(m, 5.6, 22.2, 8, 0.6, 0.8, GEM_PINK)
        gem(m, 6.2, 17.4, 8, 0.5, 0.7, GEM_PINK)
    return m


def pact_blade(t):
    m = Model("pact_blade_t%d" % t)
    if t == 1:
        m.cbox(8, 3, 8, 2.0, 2.0, 2.0, DARK_IRON)
        band(m, 4, 11, RED_WRAP, 1.6)
        m.box((4.2, 11, 7.1), (11.8, 12.5, 8.9), DARK_IRON)
        m.cbox(8, 11.75, 8, 1.0, 1.0, 1.9, GEM_RED, rot=("z", 45, (8, 11.75, 8)))
        m.box((6.4, 12.5, 7.6), (9.6, 28.5, 8.4), DARK_BLADE)
        m.box((7.1, 28.5, 7.65), (8.9, 30, 8.35), DARK_BLADE)
        m.box((7.6, 30, 7.7), (8.4, 31, 8.3), DARK_BLADE)
    elif t == 2:
        gem(m, 8, 1.6, 8, 1.0, 1.2, GEM_RED)
        m.cbox(8, 2.9, 8, 2.2, 1.6, 2.2, DARK_IRON)
        band(m, 3.7, 11, RED_WRAP, 1.7)
        m.box((5, 11, 7.1), (11, 12.6, 8.9), DARK_IRON)
        prong(m, 5.2, 8, 11.6, 2.4, "z", 22.5, DARK_IRON)
        prong(m, 10.8, 8, 11.6, 2.4, "z", -22.5, DARK_IRON)
        m.cbox(8, 11.8, 8, 1.4, 1.4, 2.0, GEM_RED, rot=("z", 45, (8, 11.8, 8)))
        m.box((6.3, 12.6, 7.6), (9.7, 28.5, 8.4), DARK_BLADE)
        m.box((7.6, 13.2, 7.5), (8.4, 26, 8.5), CRIMSON_METAL)
        m.box((7.1, 28.5, 7.65), (8.9, 30, 8.35), DARK_BLADE)
        m.box((7.6, 30, 7.7), (8.4, 31, 8.3), DARK_BLADE)
    elif t == 3:
        m.cbox(8, 1.4, 8, 1.2, 2.0, 1.2, DARK_IRON, rot_y45=True)
        m.cbox(8, 3.0, 8, 2.2, 1.4, 2.2, DARK_IRON)
        band(m, 3.7, 11, BLACK_WRAP, 1.7)
        m.cbox(8, 11.8, 8, 3.0, 1.6, 2.0, DARK_IRON)
        wing(m, 6.6, 11.9, -1, 3.0, 1.0, 22.5, DARK_IRON)
        wing(m, 9.4, 11.9, 1, 3.0, 1.0, 22.5, DARK_IRON)
        wing(m, 6.4, 12.9, -1, 2.4, 0.7, 45, DARK_IRON, depth=0.8)
        wing(m, 9.6, 12.9, 1, 2.4, 0.7, 45, DARK_IRON, depth=0.8)
        m.cbox(8, 11.8, 8, 1.2, 1.2, 2.2, GEM_RED, rot=("z", 45, (8, 11.8, 8)))
        m.box((6.4, 12.6, 7.6), (9.6, 29, 8.4), OBSIDIAN_BLADE)
        m.box((7.65, 13.5, 7.5), (8.35, 27, 8.5), RUNE_CRIMSON)
        m.box((6.9, 29, 7.65), (9.1, 30.4, 8.35), OBSIDIAN_BLADE)
        m.box((7.5, 30.4, 7.7), (8.5, 31.6, 8.3), OBSIDIAN_BLADE)
    else:
        gem(m, 8, 1.0, 8, 1.2, 1.6, GEM_RED)
        m.cbox(8, 2.6, 8, 2.4, 1.6, 2.4, GOLD)
        band(m, 3.4, 11, RED_WRAP, 1.7)
        band(m, 6.8, 7.4, GOLD, 2.0)
        m.cbox(8, 11.9, 8, 3.6, 1.8, 2.2, GOLD)
        wing(m, 6.2, 12.0, -1, 2.6, 1.0, 22.5, GOLD)
        wing(m, 9.8, 12.0, 1, 2.6, 1.0, 22.5, GOLD)
        prong(m, 3.9, 8, 12.6, 2.6, "z", -22.5, CRIMSON_METAL, 0.8)
        prong(m, 12.1, 8, 12.6, 2.6, "z", 22.5, CRIMSON_METAL, 0.8)
        m.cbox(8, 11.9, 8, 1.6, 1.6, 2.4, GEM_RED, rot=("z", 45, (8, 11.9, 8)))
        m.box((6.0, 12.8, 7.6), (10.0, 28, 8.4), OBSIDIAN_BLADE)
        for y in (15.5, 18.5, 21.5, 24.5):
            m.cbox(5.8, y, 8, 0.8, 0.9, 0.7, DARK_BLADE, rot=("z", 45, (5.8, y, 8)))
            m.cbox(10.2, y, 8, 0.8, 0.9, 0.7, DARK_BLADE, rot=("z", 45, (10.2, y, 8)))
        m.box((7.6, 13.5, 7.45), (8.4, 27, 8.55), GLOW_CRIMSON)
        m.box((6.6, 28, 7.65), (9.4, 29.6, 8.35), OBSIDIAN_BLADE)
        m.box((7.2, 29.6, 7.7), (8.8, 30.8, 8.3), OBSIDIAN_BLADE)
        m.box((7.7, 30.8, 7.75), (8.3, 31.8, 8.25), OBSIDIAN_BLADE)
    return m


def druid_staff(t):
    m = Model("druid_staff_t%d" % t)
    if t == 1:
        band(m, -6.2, -4.8, YOUNG_BARK, 2.6)
        shaft(m, -4.8, 5, YOUNG_BARK)
        band(m, 5, 10.5, VINE_WRAP, 2.4)
        shaft(m, 10.5, 14.5, YOUNG_BARK, ox=0.3)
        shaft(m, 14.5, 20.5, YOUNG_BARK, ox=-0.3)
        m.cbox(9.4, 0.5, 8, 1.0, 1.2, 1.0, YOUNG_BARK)
        m.cbox(6.3, 16.5, 8.3, 1.0, 1.0, 1.0, YOUNG_BARK)
        prong(m, 7.2, 8, 20.2, 3.6, "z", 22.5, YOUNG_BARK)
        prong(m, 8.8, 8, 20.2, 3.6, "z", -22.5, YOUNG_BARK)
        prong(m, 8, 8.8, 20.2, 3.4, "x", 22.5, YOUNG_BARK)
        gem(m, 8, 22.8, 8, 2.4, 2.8, GEM_GREEN)
        leaf(m, 9.6, 23.4, 1, 2.4, LEAF)
        leaf(m, 6.6, 18.0, -1, 2.2, LEAF)
    elif t == 2:
        band(m, -6.4, -4.8, YOUNG_BARK, 2.8)
        shaft(m, -4.8, 5, YOUNG_BARK)
        band(m, -1, 0, VINE_WRAP, 2.4)
        band(m, 5, 10.5, VINE_WRAP, 2.4)
        shaft(m, 10.5, 15, YOUNG_BARK, ox=0.3)
        band(m, 15, 16, VINE_WRAP, 2.4)
        shaft(m, 16, 20.5, YOUNG_BARK, ox=-0.2)
        twig(m, 7.2, 8, 20.2, 3.2, 2.0, "z", 1, YOUNG_BARK)
        twig(m, 8.8, 8, 20.2, 3.2, 2.0, "z", -1, YOUNG_BARK)
        twig(m, 8, 7.2, 20.2, 3.0, 1.8, "x", -1, YOUNG_BARK)
        twig(m, 8, 8.8, 20.2, 3.0, 1.8, "x", 1, YOUNG_BARK)
        gem(m, 8, 23.4, 8, 3.0, 3.4, GEM_GREEN, caps=True)
        leaf(m, 10.0, 22.0, 1, 2.6, LEAF)
        leaf(m, 6.0, 21.0, -1, 2.4, LEAF)
        leaf(m, 6.8, 13.0, -1, 2.0, LEAF)
        for (x, y, z) in ((5.7, 23.6, 8), (10.3, 24.4, 8), (8, 22.0, 5.4), (9.2, 14.8, 8.4)):
            m.cbox(x, y, z, 0.9, 0.9, 0.9, PETAL, rot_y45=True)
    elif t == 3:
        band(m, -7, -5.2, OLD_BARK, 3.0)
        shaft(m, -5.2, 5, OLD_BARK, th=2.4)
        band(m, -2, -0.8, RUNE_GREEN, 2.6)
        band(m, 5, 10.8, MOSS, 2.8)
        shaft(m, 10.8, 15, OLD_BARK, th=2.4, ox=0.3)
        band(m, 13, 14, RUNE_GREEN, 2.7)
        shaft(m, 15, 20.5, OLD_BARK, th=2.4, ox=-0.3)
        m.cbox(9.8, 1.5, 8, 1.2, 1.4, 1.2, OLD_BARK)
        m.cbox(6.1, 17.0, 8.4, 1.2, 1.2, 1.2, MOSS)
        twig(m, 7.0, 8, 20.2, 4.0, 2.6, "z", 1, OLD_BARK, th=1.1)
        twig(m, 9.0, 8, 20.2, 4.0, 2.6, "z", -1, OLD_BARK, th=1.1)
        twig(m, 8, 7.0, 20.2, 3.8, 2.4, "x", -1, OLD_BARK, th=1.1)
        twig(m, 8, 9.0, 20.2, 3.8, 2.4, "x", 1, OLD_BARK, th=1.1)
        gem(m, 8, 24.0, 8, 3.0, 3.4, GEM_GREEN, caps=True)
        gem(m, 10.6, 21.4, 8, 1.0, 1.4, GEM_AMBER)
        gem(m, 5.6, 22.2, 8, 1.0, 1.4, GEM_AMBER)
        leaf(m, 10.6, 25.2, 1, 3.0, LEAF_DEEP)
        leaf(m, 5.4, 25.0, -1, 3.0, LEAF_DEEP)
        leaf(m, 5.6, 25.4, -1, 2.6, LEAF_DEEP, axis="x")
        leaf(m, 10.4, 25.4, 1, 2.6, LEAF_DEEP, axis="x")
    else:
        m.cbox(8, -7.4, 8, 1.4, 1.2, 1.4, GOLD, rot_y45=True)
        band(m, -6.8, -5.2, SILVER_BARK, 3.0)
        shaft(m, -5.2, 5, SILVER_BARK, th=2.4)
        band(m, -2.5, -1.3, RUNE_GOLD, 2.6)
        band(m, 4.4, 5, GOLD, 2.8)
        band(m, 5, 10.8, VINE_WRAP, 2.8)
        band(m, 10.8, 11.4, GOLD, 2.8)
        shaft(m, 11.4, 15.5, SILVER_BARK, th=2.4, ox=0.3)
        band(m, 13.5, 14.5, RUNE_GOLD, 2.7)
        shaft(m, 15.5, 20.5, SILVER_BARK, th=2.4, ox=-0.3)
        twig(m, 7.0, 8, 20.2, 4.4, 3.0, "z", 1, SILVER_BARK, th=1.1)
        twig(m, 9.0, 8, 20.2, 4.4, 3.0, "z", -1, SILVER_BARK, th=1.1)
        twig(m, 8, 7.0, 20.2, 4.2, 2.8, "x", -1, SILVER_BARK, th=1.1)
        twig(m, 8, 9.0, 20.2, 4.2, 2.8, "x", 1, SILVER_BARK, th=1.1)
        gem(m, 8, 24.6, 8, 3.4, 3.6, GEM_SUN, caps=True)
        leaf(m, 11.0, 23.0, 1, 3.2, LEAF_GOLD)
        leaf(m, 5.0, 23.0, -1, 3.2, LEAF_GOLD)
        leaf(m, 11.0, 26.8, 1, 2.6, LEAF_GOLD, ang=45)
        leaf(m, 5.0, 26.8, -1, 2.6, LEAF_GOLD, ang=45)
        leaf(m, 5.0, 24.8, -1, 2.6, LEAF_GOLD, axis="x")
        leaf(m, 11.0, 24.8, 1, 2.6, LEAF_GOLD, axis="x")
        m.box((7.6, 28.6, 7.6), (8.4, 30.2, 8.4), LEAF_GOLD)
        for (x, y, z) in ((3.2, 27.0, 8), (12.8, 21.4, 8), (8, 28.8, 4.0)):
            gem(m, x, y, z, 0.7, 0.9, GEM_GREEN)
    return m


def reaper_scythe(t):
    m = Model("reaper_scythe_t%d" % t)
    if t == 1:
        band(m, -6.5, -5.5, IRON, 2.4)
        shaft(m, -5.5, 24.5, DARK_OAK)
        band(m, 5, 11, LEATHER, 2.4)
        m.cbox(8, 25.5, 8, 2.8, 2.6, 2.8, IRON)
        scythe_blade(m, (8.6, 26.6), [(7.5, 0, 3.0), (5.2, 22.5, 2.4), (3.8, 45, 1.5), (1.8, 45, 0.7)], IRON, STEEL_BLADE)
        m.cbox(10.0, 25.8, 8, 2.2, 1.0, 1.0, IRON)
    elif t == 2:
        band(m, -6.5, -5.5, DARK_IRON, 2.4)
        shaft(m, -5.5, 24.5, BONE)
        for y in (-1, 2.5, 14, 17.5, 21):
            band(m, y, y + 0.8, BONE, 2.6)
        band(m, 5, 11, BLACK_WRAP, 2.4)
        m.cbox(8, 26.0, 8, 3.2, 3.0, 3.2, BONE)
        m.cbox(6.35, 26.3, 7.2, 0.2, 0.8, 0.8, DARK)
        m.cbox(6.35, 26.3, 8.8, 0.2, 0.8, 0.8, DARK)
        scythe_blade(m, (8.6, 27.2), [(8.2, 0, 3.2), (5.8, 22.5, 2.6), (4.2, 45, 1.7), (2.2, 45, 0.8)], DARK_IRON, STEEL_BLADE)
        m.cbox(10.2, 26.2, 8, 2.4, 1.0, 1.0, BONE)
    elif t == 3:
        m.cbox(8, -7.0, 8, 1.2, 2.0, 1.2, DARK_IRON, rot_y45=True)
        band(m, -6.2, -5.4, DARK_IRON, 2.6)
        shaft(m, -5.4, 24.5, EBONY)
        band(m, -2, -0.6, RUNE_SOUL, 2.2)
        band(m, 15, 16.4, RUNE_SOUL, 2.2)
        band(m, 19, 20.4, RUNE_SOUL, 2.2)
        band(m, 5, 11, SOUL_WRAP, 2.4)
        m.cbox(8, 26.2, 8, 3.4, 3.2, 3.4, BONE)
        m.cbox(6.25, 26.5, 7.2, 0.2, 0.8, 0.8, GLOW_EYE)
        m.cbox(6.25, 26.5, 8.8, 0.2, 0.8, 0.8, GLOW_EYE)
        scythe_blade(m, (8.6, 27.4), [(8.8, 0, 3.5), (6.4, 22.5, 2.8), (4.6, 45, 1.9), (2.6, 45, 0.9)], OBSIDIAN_BLADE, GLOW_SOUL)
        scythe_blade(m, (9.6, 26.6), [(2.4, 0, 1.0), (1.6, 22.5, 0.7)], DARK_IRON, DARK_IRON, direction=1, depth=0.8)
    else:
        m.cbox(8, -7.25, 8, 1.4, 2.5, 1.4, SILVER, rot_y45=True)
        band(m, -6.2, -5.2, SILVER, 2.8)
        shaft(m, -5.2, 24.5, OBSIDIAN)
        for y in (-3, 2, 14, 17, 20):
            band(m, y, y + 1.0, RUNE_SOUL, 2.2)
        band(m, 4.4, 5, SILVER, 2.8)
        band(m, 5, 11, SOUL_WRAP, 2.4)
        band(m, 11, 11.6, SILVER, 2.8)
        m.cbox(8, 26.4, 8, 3.6, 3.4, 3.6, BONE)
        m.cbox(6.15, 26.7, 7.1, 0.2, 0.9, 0.9, GLOW_EYE)
        m.cbox(6.15, 26.7, 8.9, 0.2, 0.9, 0.9, GLOW_EYE)
        for (x, z) in ((6.8, 8), (8, 8), (9.2, 8)):
            m.cbox(x, 28.8, z, 0.6, 1.4 if x == 8 else 1.0, 0.6, SILVER)
        scythe_blade(m, (8.6, 27.6), [(9.5, 0, 3.8), (7.0, 22.5, 3.0), (5.0, 45, 2.0), (3.0, 45, 1.0)], OBSIDIAN_BLADE, GLOW_SOUL)
        scythe_blade(m, (9.8, 26.8), [(3.0, 0, 1.4), (2.0, 22.5, 0.9), (1.2, 45, 0.5)], OBSIDIAN_BLADE, GLOW_SOUL,
                     direction=1, depth=0.8)
        for (x, y) in ((-1.0, 29.0), (3.6, 29.4), (-7.0, 26.2)):
            gem(m, x, y, 8, 0.7, 0.9, GEM_SOUL)
    return m


DESIGNS = {
    "mage_staff": mage_staff,
    "witch_wand": witch_wand,
    "pact_blade": pact_blade,
    "druid_staff": druid_staff,
    "reaper_scythe": reaper_scythe,
}

# Transformacoes de exibicao por formato (ver comentario no topo: na mao, +Y do item = para frente, +Z = para cima)
DISPLAY = {
    "staff": {
        "thirdperson_righthand": {"rotation": [75, 0, 0], "translation": [0, 0.5, 1.5], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [-15, 0, 10], "translation": [1.5, -1.5, 0], "scale": [0.68, 0.68, 0.68]},
        "ground": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    },
    "scythe": {
        "thirdperson_righthand": {"rotation": [75, -90, 0], "translation": [0, 0.5, 1.5], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [-15, -20, 10], "translation": [1.5, -1.5, 0], "scale": [0.68, 0.68, 0.68]},
        "ground": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    },
    "wand": {
        "thirdperson_righthand": {"rotation": [20, 0, 0], "translation": [0, 2.5, 1], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [-60, 0, 10], "translation": [1, 0.5, -1], "scale": [0.75, 0.75, 0.75]},
        "ground": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    },
    "blade": {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 1, 0.5], "scale": [0.85, 0.85, 0.85]},
        # como a espada do vanilla (item/handheld): ponta inclinada 25 graus para FRENTE, longe do rosto
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.1, -1, -0.4], "scale": [0.55, 0.55, 0.55]},
        "ground": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    },
}
STYLE = {"mage_staff": "staff", "witch_wand": "wand", "pact_blade": "blade", "druid_staff": "staff", "reaper_scythe": "scythe"}


# =====================================================================================================
#  v0.9: armas das 10 classes Guerreiras e A Distancia (+ Escudo do Cavaleiro)
# =====================================================================================================
YEW = M("wood", "e0b070", "bf8c4c", "9a6a38", "5e3c1e")
ELM = M("wood", "d0b894", "ad9472", "8a7052", "56442e")
SPRUCE = M("wood", "c09060", "9a6e42", "74522e", "46301a")
MAPLE = M("wood", "f6dcae", "e0bc80", "bc945a", "7e5c32")
ROSEWOOD = M("wood", "b0624e", "8a4436", "662e26", "3c1814")
BRASS = M("metal", "fff4c0", "e8c870", "b8903c", "6a4c18")
STEEL = M("metal", "ffffff", "e4e8f0", "b0b8c8", "687080")
BLUE_STEEL = M("metal", "e8f0ff", "a8c0e8", "6e88b8", "34466e")
WHITE_GOLD = M("metal", "ffffff", "fff8e0", "e8d8a8", "a89458")
ENAMEL_BLUE = M("metal", "b8d0ff", "4a70d0", "2e4ea8", "182a66")
STRING = M("wrap", "ffffff", "f6f2e8", "e0d8c4", "b0a488")
FEATHER = M("cloth", "ffffff", "f4f4f4", "d8d8d8", "a0a0a0")
FEATHER_RED = M("cloth", "ffc0b0", "e8584a", "b8302a", "701810")
FEATHER_GREEN = M("cloth", "d8ffc0", "8ad860", "5aa838", "2e6a1a")
FUR = M("cloth", "fffaf0", "ece2d0", "cfc2aa", "8e8068")
DARK_FUR = M("cloth", "a08a70", "7a6450", "5a4836", "342818")
TEAL_WRAP = M("wrap", "b0fff0", "4ac0b0", "2a8a80", "145048")
WHITE_WRAP = M("wrap", "ffffff", "f0f0f0", "d0d0d0", "9a9a9a")
GREEN_WRAP = M("wrap", "c8f0a0", "5a9a3a", "3e7028", "1e4014")
GOLD_WRAP = M("wrap", "fff6c0", "f0c850", "c89a28", "7a5a10")
VIOLET_WRAP = M("wrap", "e8c8ff", "a070e0", "7048b0", "3a2066")
RUNE_FIRE = M("rune", "fffbe0", "ffb050", "e05a10", "3a1004", glow=True)
RUNE_CYAN = M("rune", "ffffff", "a0fff4", "30c0b0", "08302c", glow=True)
GLOW_FIRE = M("glow", "fffbe0", "ffc060", "ff7020", "a03000", glow=True)
GLOW_HOLY = M("glow", "ffffff", "fff4b0", "ffd84a", "b08a10", glow=True)
GLOW_KI = M("glow", "ffffff", "b0fff4", "50e0d0", "108070", glow=True)
GLOW_WIND = M("glow", "ffffff", "e0ffd0", "98f070", "40a020", glow=True)
GLOW_VIOLET = M("glow", "ffffff", "e0c8ff", "a878ff", "5a28c0", glow=True)
GLOW_EMBER = M("glow", "fff0d0", "ffa040", "e05010", "802000", glow=True)
GEM_JADE = M("gem", "ffffff", "c0ffe0", "50d8a0", "1a8a5a", glow=True)

SQ = math.sqrt(0.5)
SNAP = (-45.0, -22.5, 0.0, 22.5, 45.0)


def seg(m, p, q, th, mat, depth=1.0, cz=8.0, extend=0.0):
    """Barra reta de p=(x,y) a q=(x,y) no plano XY, girada em Z (o angulo precisa ser multiplo de 22,5 graus)."""
    dx, dy = q[0] - p[0], q[1] - p[1]
    length = math.hypot(dx, dy) + extend
    ang = math.degrees(math.atan2(dy, dx))
    while ang <= -90:
        ang += 180
    while ang > 90:
        ang -= 180
    cx, cy = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
    if abs(ang) <= 45.01:
        a, along_x = ang, True
    else:
        a, along_x = (ang - 90 if ang > 0 else ang + 90), False
    s = min(SNAP, key=lambda v: abs(v - a))
    assert abs(s - a) < 2.5, (m.name, p, q, ang)
    rot = ("z", s, (cx, cy, cz)) if s else None
    if along_x:
        m.box((cx - length / 2, cy - th / 2, cz - depth / 2), (cx + length / 2, cy + th / 2, cz + depth / 2), mat, rot=rot)
    else:
        m.box((cx - th / 2, cy - length / 2, cz - depth / 2), (cx + th / 2, cy + length / 2, cz + depth / 2), mat, rot=rot)


def uv(G, u, v):
    """Ponto do referencial do arco/besta: u ao longo dos bracos (diagonal 45 graus), v para a FRENTE (135 graus)."""
    return (G[0] + (u - v) * SQ, G[1] + (u + v) * SQ)


def polyline(segs, start=(0.0, 0.0)):
    pts = [start]
    u, v = start
    for phi, length in segs:
        r = math.radians(phi)
        u += length * math.cos(r)
        v += length * math.sin(r)
        pts.append((u, v))
    return pts


def limbs(m, G, segs, ths, mats, depth, cz):
    """Os dois bracos (espelhados em u) de um arco/besta; devolve a ponta de cima (u, v)."""
    pts = polyline(segs)
    for mirror in (1, -1):
        for i in range(len(pts) - 1):
            (u0, v0), (u1, v1) = pts[i], pts[i + 1]
            seg(m, uv(G, u0 * mirror, v0), uv(G, u1 * mirror, v1), ths[i], mats[i], depth, cz, extend=ths[i] * 0.5)
    return pts[-1]


def string_lines(m, G, tip, nock_v, mat, cz, th=0.3):
    """Corda: reta entre as pontas (nock_v = v da ponta) ou em V ate o ponto puxado."""
    tu, tv = tip
    if abs(nock_v - tv) < 1e-6:
        seg(m, uv(G, -tu, tv), uv(G, tu, tv), th, mat, th, cz)
    else:
        for s in (1, -1):
            seg(m, uv(G, tu * s, tv), uv(G, 0, nock_v), th, mat, th, cz, extend=0.2)


def arrow_model(m, G, back_v, front_v, shaft, head, fletch, cz, th=0.5):
    """Flecha ao longo de v (de back_v ate front_v): haste, ponta em losango e penas."""
    seg(m, uv(G, 0, back_v), uv(G, 0, front_v), th, shaft, th, cz)
    hx, hy = uv(G, 0, front_v + 0.4)
    m.cbox(hx, hy, cz, 1.1, 1.1, 0.35, head, rot=("z", 45, (hx, hy, cz)))
    a, b = uv(G, 0, back_v + 0.2), uv(G, 0, back_v + 2.6)
    seg(m, a, b, 1.3, fletch, 0.15, cz)
    seg(m, a, b, 0.15, fletch, 1.3, cz)


# ---------------------------------------------------------------- Guerreiras
def longsword(t):
    """Espada Longa do Guerreiro: cabo longo de duas maos, guarda larga, lamina comprida (fogo e vento)."""
    m = Model("longsword_t%d" % t)
    blade = (STEEL_BLADE, STEEL_BLADE, STEEL_BLADE, STEEL_BLADE)[t - 1]
    guard = (IRON, GOLD, DARK_IRON, GOLD)[t - 1]
    wrap = (LEATHER, RED_WRAP, BLACK_WRAP, RED_WRAP)[t - 1]
    fuller = (DARK_BLADE, IRON, RUNE_FIRE, GLOW_FIRE)[t - 1]
    gemm = (None, GEM_RED, GEM_AMBER, GEM_RED)[t - 1]
    # pomo e cabo de duas maos
    if gemm:
        gem(m, 8, -3.0, 8, 1.2, 1.4, gemm)
    m.cbox(8, -1.4, 8, 2.2, 1.8, 2.2, guard, rot_y45=t >= 3)
    band(m, -0.5, 8.6, wrap, 1.7)
    if t >= 2:
        band(m, 3.6, 4.3, guard, 2.0)
    # guarda
    gw = (8.4, 9.2, 9.6, 10.6)[t - 1]
    m.box((8 - gw / 2, 8.6, 7.1), (8 + gw / 2, 10.2, 8.9), guard)
    if t >= 2:
        prong(m, 8 - gw / 2 + 0.4, 8, 9.2, 2.0, "z", 22.5, guard, 0.9)
        prong(m, 8 + gw / 2 - 0.4, 8, 9.2, 2.0, "z", -22.5, guard, 0.9)
    if t >= 3:
        wing(m, 7.0, 10.4, -1, 2.6, 0.8, 45, guard, depth=0.8)
        wing(m, 9.0, 10.4, 1, 2.6, 0.8, 45, guard, depth=0.8)
    if gemm:
        m.cbox(8, 9.4, 8, 1.3, 1.3, 2.0, gemm, rot=("z", 45, (8, 9.4, 8)))
    # lamina
    bw = 2.8 if t < 4 else 3.2
    m.box((8 - bw / 2, 10.2, 7.6), (8 + bw / 2, 29.0, 8.4), blade)
    m.box((8 - bw / 2 + 0.45, 29.0, 7.65), (8 + bw / 2 - 0.45, 30.6, 8.35), blade)
    m.box((7.6, 30.6, 7.7), (8.4, 31.8, 8.3), blade)
    m.box((7.65, 11.0, 7.5), (8.35, 26.5 if t < 3 else 27.5, 8.5), fuller)
    if t == 4:
        # chamas subindo pela base da lamina
        for (x, y, s) in ((6.0, 12.4, -1), (10.0, 12.4, 1), (5.9, 15.4, -1), (10.1, 15.4, 1)):
            prong(m, x, 8, y, 1.8, "z", -22.5 * s, GLOW_FIRE, 0.6)
    return m


def holy_mace(t):
    """Maca Sagrada do Paladino: cabo curto, cabeca de flanges (8 no tier II+), sol dourado no tier IV."""
    m = Model("holy_mace_t%d" % t)
    haft = (OAK, WALNUT, IVORY, IVORY)[t - 1]
    head = (IRON, SILVER, WHITE_GOLD, GOLD)[t - 1]
    metal = (IRON, GOLD, GOLD, GOLD)[t - 1]
    wrap = (LEATHER, WHITE_WRAP, GOLD_WRAP, WHITE_WRAP)[t - 1]
    m.cbox(8, -3.6, 8, 1.6, 1.4, 1.6, metal, rot_y45=True)
    band(m, -3.0, -2.2, metal, 2.2)
    shaft(m, -2.2, 15.0, haft, th=1.6)
    band(m, -1.8, 6.6, wrap, 2.0)
    if t >= 2:
        band(m, 2.0, 2.6, metal, 2.2)
    if t >= 3:
        band(m, 9.5, 10.5, RUNE_GOLD, 1.9)
        band(m, 12.0, 13.0, RUNE_GOLD, 1.9)
    band(m, 14.6, 15.6, metal, 2.4)
    band(m, 15.6, 16.2, metal, 2.9, rot45=True)
    # cabeca: nucleo + flanges em cruz (+ diagonais)
    hy = 19.0
    m.cbox(8, hy, 8, 3.0, 5.0, 3.0, head)
    fl = (6.6, 7.2, 7.6, 8.0)[t - 1]
    m.cbox(8, hy, 8, fl, 4.4, 0.7, head)
    m.cbox(8, hy, 8, 0.7, 4.4, fl, head)
    if t >= 2:
        m.cbox(8, hy, 8, fl - 0.8, 3.8, 0.7, head, rot=("y", 45, (8, hy, 8)))
        m.cbox(8, hy, 8, 0.7, 3.8, fl - 0.8, head, rot=("y", 45, (8, hy, 8)))
    m.cbox(8, hy + 2.9, 8, 1.4, 0.8, 1.4, metal)
    m.cbox(8, hy + 3.7, 8, 0.9, 1.0, 0.9, metal, rot_y45=True)
    if t >= 2:
        # simbolo sagrado (losango) nas faces da cabeca
        gemm = (None, GEM_SUN, GEM_SUN, GEM_SUN)[t - 1]
        m.cbox(8, hy, 6.4, 1.2, 1.2, 0.4, gemm, rot=("z", 45, (8, hy, 6.4)))
        m.cbox(8, hy, 9.6, 1.2, 1.2, 0.4, gemm, rot=("z", 45, (8, hy, 9.6)))
    if t == 4:
        # aureola solar atras da cabeca e asas no colar
        diamond_frame(m, 8, hy, 5.2, GLOW_HOLY, th=0.6, depth=0.5)
        diamond_frame(m, 8, hy, 3.6, GOLD, th=0.6, depth=0.6, plane="zy")
        wing(m, 7.0, 15.2, -1, 3.0, 0.8, 22.5, GOLD, depth=0.8)
        wing(m, 9.0, 15.2, 1, 3.0, 0.8, 22.5, GOLD, depth=0.8)
        gem(m, 8, hy + 5.0, 8, 0.9, 1.2, GEM_SUN, caps=True)
    return m


def greataxe(t):
    """Machado Grande do Barbaro: cabo de duas maos, lamina em meia-lua (dupla no tier III+), peles e ossos."""
    m = Model("greataxe_t%d" % t)
    haft = (DARK_OAK, BONE, EBONY, OBSIDIAN)[t - 1]
    metal = (IRON, DARK_IRON, DARK_IRON, DARK_IRON)[t - 1]
    bit = (IRON, DARK_IRON, OBSIDIAN_BLADE, OBSIDIAN_BLADE)[t - 1]
    edge = (STEEL_BLADE, STEEL_BLADE, RUNE_CRIMSON, GLOW_EMBER)[t - 1]
    wrap = (LEATHER, RED_WRAP, BLACK_WRAP, RED_WRAP)[t - 1]
    m.cbox(8, -8.6, 8, 1.4, 1.6, 1.4, metal, rot_y45=True)
    band(m, -8.0, -7.0, metal, 2.4)
    shaft(m, -7.0, 25.0, haft, th=2.0)
    band(m, -6.0, -1.0, wrap, 2.4)
    band(m, 3.0, 9.0, wrap, 2.4)
    if t >= 2:
        band(m, 16.6, 18.2, FUR if t < 4 else DARK_FUR, 3.0)
    if t >= 3:
        band(m, 11.0, 12.0, RUNE_CRIMSON, 2.3)
    hy = 23.0
    m.cbox(8, hy, 8, 2.8, 6.0, 2.8, metal)
    m.cbox(8, hy + 3.6, 8, 1.2, 1.6, 1.2, metal, rot_y45=True)

    def blade(sign, scale):
        rows = [(1.4, 1.6, 4.0), (3.0, 1.6, 6.2), (4.6, 1.4, 8.4)]
        x = 8 + sign * 1.4
        for (_, w, h) in rows:
            w *= scale
            h *= scale
            x0, x1 = (x, x + w) if sign > 0 else (x - w, x)
            m.box((x0, hy - h / 2, 7.4), (x1, hy + h / 2, 8.6), bit)
            x = x1 if sign > 0 else x0
        h = 8.4 * scale + 0.6
        x0, x1 = (x, x + 0.8) if sign > 0 else (x - 0.8, x)
        m.box((x0, hy - h / 2, 7.55), (x1, hy + h / 2, 8.45), edge)
        # pontas da meia-lua
        tip_x = x - sign * 0.6
        m.box((min(tip_x, tip_x + sign * 1.2), hy + h / 2 - 0.2, 7.55), (max(tip_x, tip_x + sign * 1.2), hy + h / 2 + 1.2, 8.45), bit)
        m.box((min(tip_x, tip_x + sign * 1.2), hy - h / 2 - 1.2, 7.55), (max(tip_x, tip_x + sign * 1.2), hy - h / 2 + 0.2, 8.45), bit)

    blade(1, 1.0 if t < 4 else 1.08)
    if t >= 3:
        blade(-1, 0.85 if t == 3 else 1.0)
    else:
        m.box((4.6, hy - 0.7, 7.6), (6.6, hy + 0.7, 8.4), metal)  # espigao de tras
        m.box((3.8, hy - 0.4, 7.7), (4.6, hy + 0.4, 8.3), metal)
    if t >= 2:
        # cranio de fera no encaixe
        m.cbox(8, hy - 1.0, 9.6, 2.0, 2.0, 0.8, BONE)
        m.cbox(7.4, hy - 0.8, 10.05, 0.5, 0.5, 0.2, GLOW_EMBER if t == 4 else DARK)
        m.cbox(8.6, hy - 0.8, 10.05, 0.5, 0.5, 0.2, GLOW_EMBER if t == 4 else DARK)
    if t == 4:
        for y in (hy - 3.6, hy + 3.2):
            band(m, y, y + 0.5, GOLD, 3.0)
    return m


def ki_gauntlets(t):
    """Manopla de Ki (uma; a outra mao recebe a copia no cliente). Envolve o punho: o eixo do braco e +Z, o punho
    termina em z=8, as costas da mao ficam em +Y (o centro do braco fica 2 unidades atras da origem do item)."""
    m = Model("ki_gauntlets_t%d" % t)
    cloth = (WHITE_WRAP, TEAL_WRAP, TEAL_WRAP, WHITE_WRAP)[t - 1]
    plate = (LEATHER, BRONZE, SILVER, GOLD)[t - 1]
    accent = (LEATHER, GOLD, RUNE_CYAN, GLOW_KI)[t - 1]
    cy = 6.0
    m.box((5.4, cy - 2.6, 7.4), (10.6, cy + 2.6, 12.2), cloth)
    m.box((5.6, cy - 2.4, 6.6), (10.4, cy + 2.4, 7.4), plate)
    # nos dos dedos
    for x in (6.2, 7.4, 8.6, 9.8):
        m.box((x - 0.45, cy + 0.4, 6.0), (x + 0.45, cy + 1.6, 6.6), plate if t < 3 else accent if t == 3 else GOLD)
        if t >= 3:
            m.box((x - 0.25, cy + 0.7, 5.2), (x + 0.25, cy + 1.3, 6.0), SILVER if t == 3 else GOLD)
    # placa das costas da mao + gema de jade
    m.box((6.0, cy + 2.6, 8.0), (10.0, cy + 3.2, 11.6), plate)
    if t >= 2:
        m.cbox(8, cy + 3.4, 9.8, 1.4, 0.6, 1.4, GEM_JADE, rot_y45=True)
    # punho (cuff)
    m.box((5.0, cy - 3.0, 12.2), (11.0, cy + 3.0, 14.8), plate if t >= 2 else cloth)
    m.box((4.9, cy - 3.1, 13.4), (11.1, cy + 3.1, 14.0), accent)
    if t == 4:
        m.box((4.7, cy - 3.3, 12.4), (11.3, cy + 3.3, 12.8), GLOW_KI)
        for s in (1, -1):
            x = 11.0 if s > 0 else 5.0
            m.box((min(x, x + s * 2.0), cy - 0.4, 12.0), (max(x, x + s * 2.0), cy + 0.4, 14.6), GOLD,
                  rot=("y", -22.5 * s, (x, cy, 13.3)))
    return m


def knight_blade(t):
    """Lamina do Cavaleiro: espada de guarda larga e reta, pomo em roda, lamina larga (terra e luz)."""
    m = Model("knight_blade_t%d" % t)
    metal = (IRON, SILVER, BLUE_STEEL, GOLD)[t - 1]
    wrap = (LEATHER, BLUE_WRAP, ROYAL_WRAP, ROYAL_WRAP)[t - 1]
    fuller = (DARK_BLADE, DARK_BLADE, RUNE_GOLD, GLOW_HOLY)[t - 1]
    gemm = (None, GEM_PALE_BLUE, GEM_SUN, GEM_SUN)[t - 1]
    m.cbox(8, 2.0, 8, 2.6, 2.4, 1.4, metal)
    m.cbox(8, 2.0, 8, 1.8, 3.0, 1.2, metal)
    if gemm:
        m.cbox(8, 2.0, 8, 1.0, 1.0, 1.8, gemm, rot=("z", 45, (8, 2.0, 8)))
    band(m, 3.2, 9.6, wrap, 1.7)
    m.box((3.0, 9.6, 7.2), (13.0, 11.0, 8.8), metal)
    if t >= 2:
        m.cbox(3.0, 10.3, 8, 1.2, 1.8, 1.8, metal, rot=("z", 45, (3.0, 10.3, 8)))
        m.cbox(13.0, 10.3, 8, 1.2, 1.8, 1.8, metal, rot=("z", 45, (13.0, 10.3, 8)))
        m.cbox(8, 11.0, 8, 2.6, 2.6, 2.0, metal)
        m.cbox(8, 11.0, 8, 1.2, 1.2, 2.2, gemm, rot=("z", 45, (8, 11.0, 8)))
    if t == 4:
        wing(m, 6.8, 11.6, -1, 3.4, 1.0, 22.5, GOLD)
        wing(m, 9.2, 11.6, 1, 3.4, 1.0, 22.5, GOLD)
        wing(m, 6.6, 12.6, -1, 2.4, 0.7, 45, WHITE_GOLD, depth=0.8)
        wing(m, 9.4, 12.6, 1, 2.4, 0.7, 45, WHITE_GOLD, depth=0.8)
    m.box((6.2, 11.0, 7.6), (9.8, 27.0, 8.4), STEEL_BLADE)
    m.box((6.8, 27.0, 7.65), (9.2, 28.6, 8.35), STEEL_BLADE)
    m.box((7.4, 28.6, 7.7), (8.6, 29.8, 8.3), STEEL_BLADE)
    m.box((7.8, 29.8, 7.75), (8.2, 30.6, 8.25), STEEL_BLADE)
    m.box((7.65, 12.0, 7.5), (8.35, 24.5, 8.5), fuller)
    if t >= 2:
        m.box((6.1, 12.0, 7.55), (9.9, 13.0, 8.45), metal)  # ricasso
    return m


def twin_daggers(t):
    """Uma adaga das Adagas Gemeas (a outra mao recebe a copia no cliente): curta, guarda curva, sombra no tier IV."""
    m = Model("twin_daggers_t%d" % t)
    blade = (STEEL_BLADE, STEEL_BLADE, DARK_BLADE, OBSIDIAN_BLADE)[t - 1]
    metal = (IRON, DARK_IRON, SILVER, DARK_IRON)[t - 1]
    wrap = (LEATHER, BLACK_WRAP, PURPLE_WRAP, BLACK_WRAP)[t - 1]
    m.cbox(8, 3.4, 8, 1.6, 1.2, 1.6, metal, rot_y45=t >= 2)
    if t >= 3:
        gem(m, 8, 2.4, 8, 0.8, 1.0, GEM_VIOLET)
    band(m, 4.0, 8.2, wrap, 1.4)
    m.box((5.8, 8.2, 7.3), (10.2, 9.0, 8.7), metal)
    if t >= 2:
        prong(m, 6.1, 8, 8.6, 1.6, "z", -22.5, metal, 0.7)
        prong(m, 9.9, 8, 8.6, 1.6, "z", 22.5, metal, 0.7)
    m.box((7.0, 9.0, 7.65), (9.0, 17.0, 8.35), blade)
    m.box((7.4, 17.0, 7.7), (8.6, 18.4, 8.3), blade)
    m.box((7.8, 18.4, 7.75), (8.2, 19.4, 8.25), blade)
    if t >= 3:
        # serrilha nas costas da lamina
        for y in (11.0, 12.6, 14.2):
            m.cbox(9.05, y, 8, 0.6, 0.6, 0.5, blade, rot=("z", 45, (9.05, y, 8)))
    edge = (None, None, RUNE_VIOLET, GLOW_VIOLET)[t - 1]
    if edge:
        m.box((7.75, 9.6, 7.55), (8.25, 16.4, 8.45), edge)
    if t == 4:
        m.cbox(8, 8.6, 8, 1.0, 1.0, 1.6, GEM_VIOLET, rot=("z", 45, (8, 8.6, 8)))
    return m


def knight_shield():
    """Escudo do Cavaleiro (escudo-pipa) no MESMO espaco do modelo do escudo do vanilla (placa em x -6..6, y -11..11,
    z 1..2; alca atras), para usar as mesmas transformacoes de mao e de bloqueio."""
    m = Model("knight_shield", atlas=64)
    # costas de madeira e alca
    m.box((-5.2, -5.6, 0.6), (5.2, 10.2, 1.0), DARK_OAK)
    m.cbox(0, -5.6, 0.8, 6.6, 6.6, 0.4, DARK_OAK, rot=("z", 45, (0, -5.6, 0.8)))
    m.box((-1.0, -3.0, -4.0), (1.0, 3.0, 0.6), LEATHER)
    m.box((-1.2, -3.2, -4.6), (1.2, 3.2, -4.0), DARK_IRON)
    # face azul (placa + ponta em losango)
    m.box((-5.5, -6.0, 1.0), (5.5, 10.6, 2.0), ENAMEL_BLUE)
    m.cbox(0, -6.0, 1.5, 7.64, 7.64, 1.0, ENAMEL_BLUE, rot=("z", 45, (0, -6.0, 1.5)))
    # borda de aco
    m.box((-5.9, 10.4, 1.9), (5.9, 11.1, 2.4), STEEL)
    m.box((-5.9, -6.0, 1.9), (-5.2, 11.1, 2.4), STEEL)
    m.box((5.2, -6.0, 1.9), (5.9, 11.1, 2.4), STEEL)
    seg(m, (-5.55, -6.0), (0.0, -11.55), 0.7, STEEL, depth=0.5, cz=2.15)
    seg(m, (5.55, -6.0), (0.0, -11.55), 0.7, STEEL, depth=0.5, cz=2.15)
    # cruz dourada e o sol
    m.box((-0.55, -10.0, 2.0), (0.55, 10.4, 2.3), GOLD)
    m.box((-5.2, 4.3, 2.0), (5.2, 5.4, 2.3), GOLD)
    m.cbox(0, 4.85, 2.5, 3.2, 3.2, 0.6, GOLD, rot=("z", 45, (0, 4.85, 2.5)))
    m.cbox(0, 4.85, 2.5, 3.0, 3.0, 0.5, WHITE_GOLD)
    m.cbox(0, 4.85, 2.95, 1.4, 1.4, 0.5, GEM_SUN, rot=("z", 45, (0, 4.85, 2.95)))
    for (x, y) in ((-4.6, 9.8), (4.6, 9.8), (-4.6, -5.0), (4.6, -5.0), (0, -9.6)):
        m.cbox(x, y, 2.45, 0.6, 0.6, 0.3, SILVER)
    return m


# ---------------------------------------------------------------- A Distancia
BOW_G = (4.0, 10.0)
BOW_LIMB = [(0, 3.5), (-22.5, 4.0), (-45, 3.5)]
BOW_LIMB_DRAWN = [(0, 3.5), (-22.5, 4.0), (-67.5, 3.5)]
BOW_ARROW = 16.0


def longbow(t, state=-1):
    """Arco Longo no MESMO desenho do arco do vanilla (diagonal no plano XY, empunhadura em (4,10)), maior.
    state: -1 parado; 0 flecha encaixada; 1 meio puxado; 2 puxada completa."""
    name = "longbow_t%d" % t + ("" if state < 0 else "_pulling_%d" % state)
    m = Model(name, atlas=32)
    G = BOW_G
    wood = (YEW, ELM, MAPLE, IVORY)[t - 1]
    tipm = (YEW, BONE, SILVER, GOLD)[t - 1]
    grip = (LEATHER, GREEN_WRAP, GREEN_WRAP, GOLD_WRAP)[t - 1]
    stringm = STRING if t < 4 else GLOW_WIND
    cz = 8.0
    segs = BOW_LIMB_DRAWN if state == 2 else BOW_LIMB
    tip = limbs(m, G, segs, (1.3, 1.1, 0.9), (wood, wood, wood), 1.2, cz)
    # empunhadura e descanso da flecha
    seg(m, uv(G, -1.8, 0.05), uv(G, 1.8, 0.05), 1.9, grip, 1.6, cz)
    rx, ry = uv(G, 0.9, 0.6)
    m.cbox(rx, ry, cz, 0.7, 0.7, 1.0, tipm, rot=("z", 45, (rx, ry, cz)))
    for s in (1, -1):
        px, py = uv(G, tip[0] * s, tip[1])
        m.cbox(px, py, cz, 1.0, 1.0, 1.3, tipm, rot=("z", 45, (px, py, cz)))
    if t >= 3:
        # runas ao longo dos bracos
        for s in (1, -1):
            a, b = uv(G, 3.8 * s, -0.2), uv(G, 5.6 * s, -0.95)
            seg(m, a, b, 1.25, RUNE_GREEN if t == 3 else GLOW_WIND, 1.3, cz)
    if t >= 2:
        gx, gy = uv(G, 0, 0.75)
        m.cbox(gx, gy, cz, 0.9, 0.9, 1.8, GEM_GREEN if t < 4 else GEM_CYAN, rot=("z", 45, (gx, gy, cz)))
    if t == 4:
        # penas presas nas pontas
        for s in (1, -1):
            a = uv(G, tip[0] * s, tip[1])
            b = uv(G, (tip[0] - 1.0) * s, tip[1] - 2.4)
            seg(m, a, b, 0.9, FEATHER_GREEN, 0.25, cz)
    # corda e flecha
    if state < 0:
        string_lines(m, G, tip, tip[1], stringm, cz)
    else:
        delta = (0.0, 22.5, 45.0)[state]
        nock = tip[1] - tip[0] * math.tan(math.radians(delta))
        string_lines(m, G, tip, nock, stringm, cz)
        head = IRON if (state < 2 or t < 3) else (GLOW_WIND if t == 4 else RUNE_GREEN)
        arrow_model(m, G, nock, nock + BOW_ARROW, OAK, head, FEATHER if t < 4 else FEATHER_GREEN, cz)
    return m


XBOW_G = (8.0, 8.0)
XBOW_FRONT = 6.5
XBOW_PROD = [(0, 2.6), (-22.5, 2.6), (-45, 2.2)]


def crossbow(kind, t, state="idle"):
    """Bestas no MESMO desenho da besta do vanilla (coronha na diagonal, arco na frente em cima-esquerda; +Z = em cima).
    state: idle, pulling_0 (corda solta), pulling_1, pulling_2 (corda na noz), arrow, firework."""
    name = "%s_t%d" % (kind, t) + ("" if state == "idle" else "_" + state)
    m = Model(name, atlas=64)
    C = uv(XBOW_G, 0, XBOW_FRONT)
    arcane = kind == "arcane_crossbow"
    if arcane:
        stock = (ROSEWOOD, ROSEWOOD, EBONY, EBONY)[t - 1]
        metal = (BRASS, BRASS, BRASS, GOLD)[t - 1]
        prod = (DARK_IRON, BRASS, DARK_IRON, OBSIDIAN)[t - 1]
        accent = (GEM_VIOLET, GEM_VIOLET, RUNE_VIOLET, GLOW_VIOLET)[t - 1]
        stringm = STRING if t < 3 else GLOW_VIOLET
    else:
        stock = (DARK_OAK, WALNUT, WALNUT, EBONY)[t - 1]
        metal = (IRON, IRON, DARK_IRON, STEEL)[t - 1]
        prod = (OAK, IRON, STEEL, STEEL)[t - 1]
        accent = (LEATHER, FUR, BONE, GLOW_EMBER)[t - 1]
        stringm = STRING
    # coronha (frente v=+1.4 ate a soleira v=-14.5), trilho em cima, gatilho embaixo
    seg(m, uv(C, 0, 1.4), uv(C, 0, -10.0), 2.0, stock, 2.0, 8.0)
    seg(m, uv(C, 0, -10.0), uv(C, 0, -14.6), 2.8, stock, 2.4, 8.0)
    seg(m, uv(C, 0, -14.2), uv(C, 0, -14.9), 3.0, metal, 2.6, 8.0)
    seg(m, uv(C, 0, 1.0), uv(C, 0, -9.0), 0.8, metal, 0.4, 9.15)
    tx, ty = uv(C, 0, -7.2)
    m.cbox(tx, ty, 6.6, 0.8, 0.8, 1.2, metal, rot=("z", 45, (tx, ty, 6.6)))
    fx, fy = uv(C, 0, 0.8)
    m.cbox(fx, fy, 8.0, 2.6, 2.6, 2.4, metal, rot=("z", 45, (fx, fy, 8.0)))
    if not arcane and t >= 2:
        seg(m, uv(C, 0, -10.6), uv(C, 0, -13.4), 3.0, accent, 2.8, 8.0)  # pele/osso na soleira
    if arcane:
        # cristal na frente e engrenagem no lado
        m.cbox(fx, fy, 9.6, 1.2, 1.2, 1.2, accent if t < 3 else GEM_VIOLET, rot=("z", 45, (fx, fy, 9.6)))
        gx, gy = uv(C, 1.3, -5.0)
        m.cbox(gx, gy, 8.0, 2.0, 2.0, 1.0, metal)
        m.cbox(gx, gy, 8.0, 2.0, 2.0, 1.0, metal, rot=("z", 45, (gx, gy, 8.0)))
        m.cbox(gx, gy, 8.0, 0.8, 0.8, 1.3, accent)
        if t >= 3:
            seg(m, uv(C, 0, -2.0), uv(C, 0, -8.0), 0.5, RUNE_VIOLET, 2.1, 8.0)
    else:
        sx, sy = uv(C, 0, -4.0)
        m.cbox(sx, sy, 9.6, 0.6, 0.6, 0.8, accent if t == 4 else metal)  # mira
    # arco (prod) em cima da frente
    pz = 9.0
    thick = 1.2 if arcane else 1.5
    tip = limbs(m, C, XBOW_PROD, (thick, thick * 0.9, thick * 0.8), (prod, prod, prod), 0.9, pz)
    for s in (1, -1):
        px, py = uv(C, tip[0] * s, tip[1])
        m.cbox(px, py, pz, 0.9, 0.9, 1.0, metal if not (arcane and t == 4) else GLOW_VIOLET, rot=("z", 45, (px, py, pz)))
    if t == 4 and not arcane:
        for s in (1, -1):
            a, b = uv(C, tip[0] * s, tip[1]), uv(C, (tip[0] - 0.83) * s, tip[1] - 2.0)
            seg(m, a, b, 0.7, FEATHER_RED, 0.2, pz)
    # corda
    delta = {"idle": 0.0, "pulling_0": 0.0, "pulling_1": 22.5}.get(state, 45.0)
    nock = tip[1] - tip[0] * math.tan(math.radians(delta))
    string_lines(m, C, tip, nock, stringm, pz + 0.15)
    if delta >= 45:
        nx, ny = uv(C, 0, nock)
        m.cbox(nx, ny, 9.3, 0.9, 0.9, 0.6, metal, rot=("z", 45, (nx, ny, 9.3)))
    if state == "arrow":
        head = IRON if t < 3 else (GLOW_VIOLET if arcane else GLOW_EMBER if t == 4 else STEEL)
        arrow_model(m, C, nock, 3.0, OAK if not arcane else EBONY, head, FEATHER if not arcane else FEATHER, 9.6, th=0.55)
    elif state == "firework":
        seg(m, uv(C, 0, nock), uv(C, 0, 2.0), 1.3, RED_WRAP, 1.3, 9.8)
        hx, hy = uv(C, 0, 2.6)
        m.cbox(hx, hy, 9.8, 1.0, 1.0, 1.0, PURPLE_WRAP, rot=("z", 45, (hx, hy, 9.8)))
    return m


def lute(t):
    """Alaude do Bardo, montado na posicao de TOCAR: corpo perto da origem, braco para +X, tampo (cordas) em +Z."""
    m = Model("lute_t%d" % t, atlas=64)
    body = (SPRUCE, MAPLE, EBONY, IVORY)[t - 1]
    face = (MAPLE, MAPLE, MAPLE, WHITE_GOLD)[t - 1]
    neck = (SPRUCE, ROSEWOOD, EBONY, IVORY)[t - 1]
    trim = (DARK_OAK, GOLD, SILVER, GOLD)[t - 1]
    rosette = (DARK, GOLD, RUNE_VIOLET, GLOW_HOLY)[t - 1]
    stringm = STRING if t < 3 else GOLD_WRAP if t == 3 else GLOW_HOLY
    cx, cy = 5.0, 8.0
    # bojo (camadas para tras) e tampo arredondado
    m.box((1.4, cy - 2.6, 8.4), (8.6, cy + 2.6, 9.2), face)
    m.box((2.0, cy - 3.3, 8.4), (8.0, cy + 3.3, 9.2), face)
    m.box((2.8, cy - 3.8, 8.4), (7.2, cy + 3.8, 9.2), face)
    m.box((8.6, cy - 1.9, 8.4), (9.6, cy + 1.9, 9.2), face)
    m.box((8.2, cy - 1.6, 7.2), (9.4, cy + 1.6, 8.4), body)
    m.box((1.8, cy - 3.0, 7.0), (8.2, cy + 3.0, 8.4), body)
    m.box((2.4, cy - 3.5, 7.0), (7.6, cy + 3.5, 8.4), body)
    m.box((2.6, cy - 2.6, 5.8), (7.4, cy + 2.6, 7.0), body)
    m.box((3.4, cy - 1.8, 5.0), (6.6, cy + 1.8, 5.8), body)
    # filete na borda do tampo
    m.box((1.3, cy - 2.7, 9.2), (8.7, cy - 2.4, 9.35), trim)
    m.box((1.3, cy + 2.4, 9.2), (8.7, cy + 2.7, 9.35), trim)
    # roseta (boca) e cavalete
    m.cbox(cx + 0.8, cy, 9.25, 1.8, 1.8, 0.2, rosette, rot=("z", 45, (cx + 0.8, cy, 9.25)))
    m.box((2.4, cy - 1.6, 9.2), (3.0, cy + 1.6, 9.7), trim)
    # braco, escala e cravelhal dobrado para tras
    m.box((8.4, cy - 0.85, 8.2), (17.0, cy + 0.85, 9.3), neck)
    m.box((8.6, cy - 0.75, 9.3), (17.0, cy + 0.75, 9.5), EBONY if t < 4 else GOLD)
    for x in (10.5, 12.5, 14.5):
        m.box((x, cy - 0.8, 9.5), (x + 0.2, cy + 0.8, 9.6), trim)
    m.box((16.8, cy - 1.0, 8.0), (17.4, cy + 1.0, 9.6), trim)
    m.box((17.2, cy - 0.8, 8.2), (20.6, cy + 0.8, 9.2), neck, rot=("y", 22.5, (17.2, cy, 8.7)))
    for i, x in enumerate((17.9, 18.8, 19.7)):
        side = 1 if i % 2 == 0 else -1
        m.box((x, cy + side * 0.8, 8.1), (x + 0.4, cy + side * 1.7, 8.5), trim, rot=("y", 22.5, (17.2, cy, 8.7)))
    # cordas
    for y in (cy - 0.45, cy, cy + 0.45):
        m.box((2.7, y - 0.07, 9.65), (16.9, y + 0.07, 9.75), stringm, shade=False)
    if t >= 3:
        # runas que acendem no bojo
        m.box((2.2, cy - 3.6, 7.6), (7.8, cy - 3.45, 8.2), RUNE_VIOLET if t == 3 else GLOW_HOLY)
        m.box((2.2, cy + 3.45, 7.6), (7.8, cy + 3.6, 8.2), RUNE_VIOLET if t == 3 else GLOW_HOLY)
    if t == 4:
        # asas no cravelhal e gema no tampo
        m.box((20.0, cy + 0.6, 8.4), (22.4, cy + 1.2, 8.9), GOLD, rot=("z", 22.5, (20.0, cy + 0.9, 8.65)))
        m.box((20.0, cy - 1.2, 8.4), (22.4, cy - 0.6, 8.9), GOLD, rot=("z", -22.5, (20.0, cy - 0.9, 8.65)))
        m.cbox(7.6, cy, 9.35, 0.8, 0.8, 0.3, GEM_SUN, rot=("z", 45, (7.6, cy, 9.35)))
    return m


V09_DESIGNS = {
    "longsword": longsword,
    "holy_mace": holy_mace,
    "greataxe": greataxe,
    "ki_gauntlets": ki_gauntlets,
    "knight_blade": knight_blade,
    "longbow": longbow,
    "twin_daggers": twin_daggers,
    "lute": lute,
    "arcane_crossbow": lambda t: crossbow("arcane_crossbow", t),
    "hunter_crossbow": lambda t: crossbow("hunter_crossbow", t),
}
V09_KINDS = list(V09_DESIGNS)

# Transformacoes de exibicao. Arco/besta/escudo: as do vanilla (os modelos usam o mesmo espaco do item do vanilla).
VANILLA_BOW = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
}
VANILLA_CROSSBOW = {
    "thirdperson_righthand": {"rotation": [-90, 0, -60], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-90, 0, 30], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [-90, 0, -55], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [-90, 0, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
}
VANILLA_SHIELD = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
}
VANILLA_SHIELD_BLOCKING = {
    "thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.49, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.51, 7, 2.5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
}
DISPLAY.update({
    "axe": {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 2, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.1, 0, -0.4], "scale": [0.5, 0.5, 0.5]},
        "ground": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    },
    "dagger": {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 0.5, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.1, -0.5, -0.4], "scale": [0.6, 0.6, 0.6]},
        "ground": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
    },
    # Manopla: identidade = envolve o punho (ver ki_gauntlets); em 1a pessoa o punho aponta para a frente
    "fist": {
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "firstperson_righthand": {"rotation": [-6, -10, 0], "translation": [-1, 3, -1], "scale": [0.9, 0.9, 0.9]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
    },
    # Alaude parado: segurado pelo braco, corpo para baixo, tampo para fora
    "lute": {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 0, -1], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.1, -1.5, -0.4], "scale": [0.55, 0.55, 0.55]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    },
    # Alaude tocando (pose de dois bracos): braco do alaude para a esquerda e para cima, tampo para a frente
    "lute_playing": {
        "thirdperson_righthand": {"rotation": [180, 0, -20], "translation": [2, -1, 1], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, 0, 160], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    },
    "bow": VANILLA_BOW,
    "crossbow": VANILLA_CROSSBOW,
    "shield": VANILLA_SHIELD,
    "shield_blocking": VANILLA_SHIELD_BLOCKING,
})
STYLE.update({"longsword": "blade", "holy_mace": "blade", "greataxe": "axe", "ki_gauntlets": "fist",
              "knight_blade": "blade", "longbow": "bow", "twin_daggers": "dagger", "lute": "lute",
              "arcane_crossbow": "crossbow", "hunter_crossbow": "crossbow"})


# =====================================================================================================
#  Icones 2D (16x16) do inventario
# =====================================================================================================
OUT = rgb("1a1420")


def P(mat):
    """Paleta (hi, lt, md, dk) de um material."""
    return (mat.hi, mat.lt, mat.md, mat.dk)


def diag(c, x0, y0, n, lt, dk, thick=2):
    """Haste diagonal (de baixo-esquerda para cima-direita): n passos a partir de (x0, y0)."""
    for i in range(n):
        x, y = x0 + i, y0 - i
        c.set(x, y, lt)
        if thick >= 2:
            c.set(x + 1, y, dk)


def blob(c, cx, cy, r, mat, sparkle=True):
    """Gema redonda sombreada."""
    hi, lt, md, dk = P(mat)
    c.circle(cx, cy, r, md)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r and c.get(x, y) == md:
                t = ((x + 0.5 - cx) + (y + 0.5 - cy)) / (2 * r)
                if t < -0.3:
                    c.set(x, y, lt)
                elif t > 0.35:
                    c.set(x, y, dk)
    if sparkle:
        c.set(int(cx - r * 0.45), int(cy - r * 0.45), hi)


def finish(c, glow_pixels=()):
    c.outline(OUT)
    for (x, y, col) in glow_pixels:
        c.set(x, y, col)
    return c


def icon_staff(t, wood, metal, wrap, gemm, accent=None, druid=False):
    c = Canvas(16, 16)
    hi, lt, md, dk = P(wood)
    # haste
    diag(c, 2, 13, 9, lt, dk)
    if druid:
        # galho torto: alguns nos
        c.set(4, 12, dk); c.set(7, 7, lt); c.set(6, 9, md)
    # ponteira
    mh, ml, mm, mdk = P(metal)
    c.set(1, 14, ml); c.set(2, 14, mdk); c.set(2, 13, mm)
    # empunhadura
    wh, wl, wm, wd = P(wrap)
    for i in (3, 4, 5):
        c.set(2 + i, 13 - i, wl)
        c.set(3 + i, 13 - i, wd)
    # colar
    c.set(10, 5, ml); c.set(11, 5, mm); c.set(11, 4, mdk)
    if t >= 2:
        c.set(9, 6, mh) if not druid else c.set(9, 6, ml)
    # cabeca
    r = {1: 1.6, 2: 2.1, 3: 2.1, 4: 2.5}[t]
    gx, gy = (12.4, 2.6) if t < 4 else (12.0, 3.0)
    if druid:
        # galhos abracando a gema
        c.set(10, 3, lt); c.set(10, 2, md); c.set(13, 5, lt); c.set(14, 4, md)
        if t >= 2:
            c.set(11, 1, lt); c.set(14, 2, md)
    else:
        c.set(10, 3, ml); c.set(13, 5, ml)
        if t >= 2:
            c.set(10, 2, mm); c.set(14, 5, mm)
    blob(c, gx, gy, r, gemm)
    if druid and t == 2:
        # flores do Cajado de Galhos Floridos
        for (x, y) in ((9, 2), (14, 3), (5, 9)):
            c.set(x, y, PETAL.md)
        c.set(10, 1, PETAL.lt)
    glow = []
    if t >= 3 and accent is not None:
        ah, al, am, ad = P(accent)
        # moldura em losango / folhas em volta
        for (x, y) in ((12, 0), (15, 3), (9, 3), (12, 6)) if t == 4 else ((14, 0), (15, 2), (9, 1)):
            c.set(x, y, al if not druid else am)
        # runas na haste
        c.set(5, 11, al); c.set(8, 8, al)
    if t == 4:
        glow = [(15, 0, (255, 255, 255, 255)), (8, 1, P(gemm)[1])]
    return finish(c, glow)


def icon_wand(t, wood, handle, metal, gemm, accent=None):
    c = Canvas(16, 16)
    hi, lt, md, dk = P(wood)
    hh, hl, hm, hd = P(handle)
    mh, ml, mm, mdk = P(metal)
    # cabo (2px) e haste fina (1px)
    for i in range(5):
        c.set(3 + i, 13 - i, hl)
        c.set(4 + i, 13 - i, hd)
    c.set(2, 14, mm)
    c.set(8, 8, ml); c.set(9, 8, mdk); c.set(8, 7, mm)
    for i in range(4):
        c.set(9 + i, 7 - i, lt if i % 2 == 0 else md)
    r = {1: 1.2, 2: 1.5, 3: 1.6, 4: 1.9}[t]
    blob(c, 13.6, 2.4, r, gemm)
    glow = []
    if t >= 2:
        # meia-lua
        c.set(15, 4, ml); c.set(15, 3, ml)
    if t >= 3 and accent is not None:
        ah, al, am, ad = P(accent)
        c.set(5, 11, al); c.set(10, 5, al)
    if t == 4:
        c.set(6, 8, ml); c.set(10, 11, ml)  # asinhas da guarda
        glow = [(11, 0, (255, 255, 255, 255)), (15, 6, P(gemm)[1])]
    return finish(c, glow)


def icon_blade(t, blade, guard, wrap, gemm, fuller=None):
    c = Canvas(16, 16)
    bh, bl, bm, bd = P(blade)
    gh, gl, gm, gd = P(guard)
    wh, wl, wm, wd = P(wrap)
    # lamina larga diagonal
    width = 3 if t < 4 else 4
    for i in range(9):
        x, y = 5 + i, 10 - i
        c.set(x, y, bm)
        c.set(x, y - 1, bh if i < 8 else bl)
        c.set(x + 1, y, bd)
        if width >= 4 and i < 8:
            c.set(x + 1, y - 1, bl)
    c.set(14, 0, bl); c.set(15, 0, bm)
    if fuller is not None:
        fh, fl, fm, fd = P(fuller)
        for i in range(1, 8):
            c.set(5 + i, 10 - i, fl)
    # guarda (anti-diagonal)
    for (x, y) in ((3, 9), (4, 10), (5, 11), (6, 12), (7, 13)):
        c.set(x, y, gl)
    c.set(4, 9, gh); c.set(7, 12, gd)
    if t >= 2:
        c.set(2, 8, gm); c.set(8, 14, gm)
    if t >= 3:
        c.set(1, 7, gd); c.set(9, 15, gd)
    # cabo
    c.set(3, 12, wl); c.set(2, 13, wd); c.set(4, 12, wm); c.set(3, 13, wm)
    # pomo / gema
    blob(c, 5.5, 11.5, 0.9, gemm, sparkle=False)
    c.set(1, 14, gl); c.set(1, 15, gd); c.set(0, 15, gm)
    glow = []
    if t == 4:
        glow = [(15, 1, (255, 255, 255, 255))]
    return finish(c, glow)


def icon_scythe(t, pole, blade, wrap, edge, extra=None):
    c = Canvas(16, 16)
    ph, pl, pm, pd = P(pole)
    bh, bl, bm, bd = P(blade)
    eh, el, em, ed = P(edge)
    wh, wl, wm, wd = P(wrap)
    diag(c, 2, 14, 10, pl, pd)
    for i in (3, 4, 5):
        c.set(2 + i, 14 - i, wl)
        c.set(3 + i, 14 - i, wd)
    # lamina: arco do topo do cabo para a esquerda e para baixo
    spine = [(12, 3), (11, 2), (10, 1), (9, 1), (8, 1), (7, 1), (6, 2), (5, 2), (4, 3), (3, 4), (2, 5)]
    inner = [(11, 3), (10, 2), (9, 2), (8, 2), (7, 2), (6, 3), (5, 3), (4, 4), (3, 5)]
    edge_px = [(10, 3), (9, 3), (8, 3), (7, 3), (6, 4), (5, 4), (4, 5), (3, 6)]
    if t >= 3:
        spine = [(13, 3), (12, 2), (11, 1), (10, 0), (9, 0), (8, 0), (7, 0), (6, 1), (5, 1), (4, 2), (3, 3), (2, 4), (1, 5)]
        inner = [(12, 3), (11, 2), (10, 1), (9, 1), (8, 1), (7, 1), (6, 2), (5, 2), (4, 3), (3, 4), (2, 5)]
        edge_px = [(10, 2), (9, 2), (8, 2), (7, 2), (6, 3), (5, 3), (4, 4), (3, 5), (2, 6)]
    for (x, y) in spine:
        c.set(x, y, bl)
    for (x, y) in inner:
        c.set(x, y, bm)
    for (x, y) in edge_px:
        c.set(x, y, el)
    c.set(spine[0][0], spine[0][1] + 1, pm)
    if extra is not None:
        xh, xl, xm, xd = P(extra)
        # cranio/juncao
        c.set(12, 4, xl); c.set(13, 4, xm); c.set(12, 5, xm)
    glow = []
    if t == 4:
        glow = [(14, 2, el), (15, 1, (255, 255, 255, 255))]
    return finish(c, glow)


ICONS = {
    ("mage_staff", 1): lambda: icon_staff(1, OAK, IRON, LEATHER, GEM_PALE_BLUE),
    ("mage_staff", 2): lambda: icon_staff(2, WALNUT, GOLD, BLUE_WRAP, GEM_ARCANE),
    ("mage_staff", 3): lambda: icon_staff(3, EBONY, SILVER, PURPLE_WRAP, GEM_CYAN, accent=RUNE_BLUE),
    ("mage_staff", 4): lambda: icon_staff(4, IVORY, GOLD, ROYAL_WRAP, GEM_CYAN, accent=GOLD),
    ("witch_wand", 1): lambda: icon_wand(1, BIRCH, DARK_OAK, IRON, GEM_VIOLET),
    ("witch_wand", 2): lambda: icon_wand(2, CHERRY, PURPLE_WRAP, SILVER, GEM_PINK),
    ("witch_wand", 3): lambda: icon_wand(3, EBONY, EBONY, SILVER, GEM_VIOLET, accent=RUNE_VIOLET),
    ("witch_wand", 4): lambda: icon_wand(4, OBSIDIAN, OBSIDIAN, GOLD, GEM_MAGENTA, accent=RUNE_MAGENTA),
    ("pact_blade", 1): lambda: icon_blade(1, DARK_BLADE, DARK_IRON, RED_WRAP, GEM_RED),
    ("pact_blade", 2): lambda: icon_blade(2, DARK_BLADE, DARK_IRON, RED_WRAP, GEM_RED, fuller=CRIMSON_METAL),
    ("pact_blade", 3): lambda: icon_blade(3, OBSIDIAN_BLADE, DARK_IRON, BLACK_WRAP, GEM_RED, fuller=RUNE_CRIMSON),
    ("pact_blade", 4): lambda: icon_blade(4, OBSIDIAN_BLADE, GOLD, RED_WRAP, GEM_RED, fuller=GLOW_CRIMSON),
    ("druid_staff", 1): lambda: icon_staff(1, YOUNG_BARK, YOUNG_BARK, VINE_WRAP, GEM_GREEN, druid=True),
    ("druid_staff", 2): lambda: icon_staff(2, YOUNG_BARK, YOUNG_BARK, VINE_WRAP, GEM_GREEN, druid=True),
    ("druid_staff", 3): lambda: icon_staff(3, OLD_BARK, OLD_BARK, MOSS, GEM_GREEN, accent=LEAF_DEEP, druid=True),
    ("druid_staff", 4): lambda: icon_staff(4, SILVER_BARK, GOLD, VINE_WRAP, GEM_SUN, accent=LEAF_GOLD, druid=True),
    ("reaper_scythe", 1): lambda: icon_scythe(1, DARK_OAK, IRON, LEATHER, STEEL_BLADE),
    ("reaper_scythe", 2): lambda: icon_scythe(2, BONE, DARK_IRON, BLACK_WRAP, STEEL_BLADE, extra=BONE),
    ("reaper_scythe", 3): lambda: icon_scythe(3, EBONY, OBSIDIAN_BLADE, SOUL_WRAP, GLOW_SOUL, extra=BONE),
    ("reaper_scythe", 4): lambda: icon_scythe(4, OBSIDIAN, OBSIDIAN_BLADE, SOUL_WRAP, GLOW_SOUL, extra=BONE),
}


# ---------------------------------------------------------------- icones v0.9 (formas por poligono, sombreadas)
def _inside(px, py, pts):
    inside = False
    n = len(pts)
    for i in range(n):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % n]
        if (y0 > py) != (y1 > py):
            xi = x0 + (py - y0) * (x1 - x0) / (y1 - y0)
            if px < xi:
                inside = not inside
    return inside


def poly(c, pts, mat, light=True):
    """Preenche o poligono (centro do pixel dentro) com o tom medio; borda de cima/esquerda clara, de baixo/direita escura."""
    hi, lt, md, dk = P(mat)
    cells = {(x, y) for y in range(16) for x in range(16) if _inside(x + 0.5, y + 0.5, pts)}
    for (x, y) in cells:
        col = md
        if light:
            up = (x, y - 1) not in cells or (x - 1, y) not in cells
            dn = (x, y + 1) not in cells or (x + 1, y) not in cells
            if up and not dn:
                col = lt
            elif dn and not up:
                col = dk
        c.set(x, y, col)
    return cells


def bar(c, p0, p1, w, mat):
    """Barra grossa de p0 a p1 (largura w em pixels)."""
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    n = math.hypot(dx, dy) or 1.0
    ox, oy = -dy / n * w / 2, dx / n * w / 2
    return poly(c, [(p0[0] + ox, p0[1] + oy), (p1[0] + ox, p1[1] + oy), (p1[0] - ox, p1[1] - oy), (p0[0] - ox, p0[1] - oy)], mat)


def px_line(c, p0, p1, col):
    c.line(int(p0[0]), int(p0[1]), int(p1[0]), int(p1[1]), col)


def icon_longsword(t, blade, guard, wrap, gemm, fuller):
    c = Canvas(16, 16)
    bh, bl, bm, bd = P(blade)
    # lamina fina e comprida (2 px) da guarda ate a ponta em (15,0)
    for i in range(10):
        x, y = 5 + i, 10 - i
        c.set(x, y, bm)
        c.set(x, y - 1, bh if i % 3 else bl)
        c.set(x + 1, y, bd)
    c.set(15, 0, bl)
    if fuller is not None:
        fh, fl, fm, fd = P(fuller)
        for i in range(1, 8):
            c.set(5 + i, 10 - i, fl if fuller.glow else fm)
    gh, gl, gm, gd = P(guard)
    for (x, y) in ((2, 8), (3, 9), (4, 10), (5, 11), (6, 12), (7, 13)):
        c.set(x, y, gl)
    c.set(3, 8, gh); c.set(6, 13, gd); c.set(7, 12, gd)
    if t >= 3:
        c.set(1, 7, gm); c.set(8, 14, gm)
    wh, wl, wm, wd = P(wrap)
    for (x, y) in ((3, 12), (2, 13)):
        c.set(x, y, wl); c.set(x + 1, y, wd)
    c.set(4, 11, wm)
    c.set(1, 14, gm); c.set(0, 15, gd); c.set(1, 15, gd)
    if gemm is not None:
        blob(c, 5.0, 11.0, 0.8, gemm, sparkle=False)
    glow = [(14, 2, (255, 255, 255, 255))] if t == 4 else []
    if t == 4:
        fh, fl, fm, fd = P(GLOW_FIRE)
        for (x, y) in ((6, 7), (8, 5), (9, 7)):
            c.set(x, y, fl)
    return finish(c, glow)


def icon_mace(t, haft, head, wrap, gemm, halo=False):
    c = Canvas(16, 16)
    hh, hl, hm, hd = P(haft)
    diag(c, 1, 14, 9, hl, hd)
    wh, wl, wm, wd = P(wrap)
    for i in (1, 2, 3, 4):
        c.set(1 + i, 14 - i, wl); c.set(2 + i, 14 - i, wd)
    if halo:
        gh, gl, gm, gd = P(GLOW_HOLY)
        for (x, y) in ((11, 0), (15, 4), (11, 9), (7, 4), (13, 1), (14, 2), (9, 1), (8, 2), (14, 7), (13, 8), (8, 7), (9, 8)):
            c.set(x, y, gl)
    # cabeca com flanges
    eh, el, em, ed = P(head)
    c.circle(11.5, 4.5, 2.6, em)
    c.shade_sphere(11.5, 4.5, 2.6, 0.35, 0.35)
    for (x, y, col) in ((11, 1, el), (12, 1, em), (14, 4, em), (14, 5, ed), (11, 8, ed), (12, 8, ed), (8, 4, el), (8, 5, em)):
        c.set(x, y, col)
    if t >= 2:
        for (x, y, col) in ((9, 2, el), (14, 2, em), (14, 7, ed), (9, 7, em)):
            c.set(x, y, col)
    c.set(12, 0, el)
    if gemm is not None:
        sh, sl, sm, sd = P(gemm)
        c.set(11, 4, sl); c.set(12, 5, sm)
    return finish(c, [(15, 0, (255, 255, 255, 255))] if t == 4 else [])


def icon_axe(t, haft, bit, edge, wrap, double):
    c = Canvas(16, 16)
    hh, hl, hm, hd = P(haft)
    diag(c, 1, 14, 11, hl, hd)
    wh, wl, wm, wd = P(wrap)
    for i in (1, 2, 3):
        c.set(1 + i, 14 - i, wl); c.set(2 + i, 14 - i, wd)
    for i in (6, 7):
        c.set(1 + i, 14 - i, wl); c.set(2 + i, 14 - i, wd)
    # meia-lua para cima-esquerda do topo do cabo
    poly(c, [(8.5, 4.5), (6.0, 1.0), (8.0, -0.2), (11.5, 0.2), (12.5, 2.0), (11.0, 5.5)], bit)
    eh, el, em, ed = P(edge)
    for (x, y) in ((6, 1), (7, 0), (8, 0), (9, 0), (5, 2) if t >= 2 else (6, 2)):
        c.set(x, y, el if not edge.glow else em)
    if double:
        poly(c, [(12.5, 5.0), (15.6, 6.5), (15.6, 10.0), (14.0, 11.0), (11.5, 8.5)], bit)
        for (x, y) in ((15, 7), (15, 8), (15, 9)):
            c.set(x, y, el if not edge.glow else em)
    else:
        c.set(13, 4, hm); c.set(14, 5, hd)
    return finish(c, [(10, 0, (255, 255, 255, 255))] if t == 4 else [])


def icon_gauntlet(t, cloth, plate, gemm, glow):
    """Punho fechado com nos de metal, placa nas costas da mao e punho largo; a segunda manopla aparece atras."""
    c = Canvas(16, 16)
    rows = ["..KkKkKkKk..",
            ".fFfFfFfFfd.",
            ".fffffffffd.",
            ".ffdffdffdd.",
            "tttttffffdd.",
            ".dttttfffdd.",
            "..pPPPPPpq..",
            "..pPPgPPpq..",
            "..pPPPPPpq..",
            "..ppppppqq..",
            ".uUUUUUUUUu.",
            "uuuuuuuuuuuu",
            "uuuuuuuuuuuu",
            ".uuuuuuuuuu."]
    ph, pl, pm, pd = P(plate)
    ch, cl, cm, cd = P(cloth)
    knuckle = P(SILVER if t == 3 else GOLD if t == 4 else plate)
    gem_col = P(gemm)[1] if gemm is not None else pl
    accent = P(glow)[1] if glow is not None else (pl if t >= 2 else cl)
    cuff = pm if t >= 2 else cm
    pal = {"K": knuckle[1], "k": knuckle[2], "f": cm, "F": cl, "d": cd, "t": cl, "p": pm, "P": pl, "q": pd, "g": gem_col,
           "u": cuff, "U": accent}
    front = Canvas(16, 16)
    front.from_rows(rows, pal, ox=4, oy=1)
    front = finish(front)
    for y in range(16):
        for x in range(16):
            col = front.get(x, y)
            if col[3] > 0 and x >= 4:
                c.set(x - 4, y + 1, darken(col, 0.4))
    for y in range(16):
        for x in range(16):
            col = front.get(x, y)
            if col[3] > 0:
                c.set(x, y, col)
    return c


def icon_daggers(t, blade, guard, wrap, edge):
    c = Canvas(16, 16)
    bh, bl, bm, bd = P(blade)
    gh, gl, gm, gd = P(guard)
    wh, wl, wm, wd = P(wrap)
    # adaga 1: cabo embaixo-esquerda, ponta cima-direita; adaga 2 espelhada
    for mirror in (False, True):
        def S(x, y, col):
            c.set(15 - x if mirror else x, y, col)
        for i in range(6):
            S(7 + i, 7 - i, bm)
            S(7 + i, 6 - i, bh if i % 2 == 0 else bl)
            S(8 + i, 7 - i, bd)
        S(13, 0, bl)
        if edge is not None:
            S(9, 5, P(edge)[1]); S(10, 4, P(edge)[1])
        for (x, y) in ((5, 7), (6, 8), (7, 9)):
            S(x, y, gl)
        S(5, 6, gm); S(8, 10, gd)
        for (x, y) in ((5, 9), (4, 10), (3, 11)):
            S(x, y, wl); S(x + 1, y, wd)
        S(2, 12, gm); S(2, 13, gd)
    return finish(c, [(13, 0, (255, 255, 255, 255))] if t == 4 else [])


def icon_bow(t, wood, tipm, grip, stringm, gemm):
    """Mesmo desenho do arco do vanilla (empunhadura em cima-esquerda, corda na diagonal), arco mais longo."""
    c = Canvas(16, 16)
    wh, wl, wm, wd = P(wood)
    # bracos: curva de (15,0) passando por (2,4)/(4,2) ate (0,15)
    curve = [(14, 0), (13, 0), (12, 1), (11, 1), (10, 1), (9, 1), (8, 1), (7, 2), (6, 2), (5, 3), (4, 3), (4, 4), (3, 4),
             (3, 5), (2, 6), (2, 7), (1, 8), (1, 9), (1, 10), (1, 11), (1, 12), (0, 13), (0, 14)]
    for i, (x, y) in enumerate(curve):
        c.set(x, y, wm)
        c.set(x + 1, y + 1, wd)
        if i % 4 == 0:
            c.set(x, y, wl)
    sh, sl, sm, sd = P(stringm)
    px_line(c, (14, 1), (1, 14), sl if not stringm.glow else sm)
    gh, gl, gm, gd = P(grip)
    for (x, y) in ((4, 4), (3, 5), (5, 3), (4, 5), (5, 4)):
        c.set(x, y, gl if (x + y) % 2 else gd)
    th, tl, tm, td = P(tipm)
    c.set(15, 0, tm); c.set(14, 0, tl); c.set(0, 15, tm); c.set(0, 14, tl)
    if gemm is not None:
        eh, el, em, ed = P(gemm)
        c.set(3, 3, el)
    if t >= 3:
        rl = P(GLOW_WIND if t == 4 else RUNE_GREEN)[1]
        c.set(9, 1, rl); c.set(1, 9, rl)
    return finish(c, [(15, 1, (255, 255, 255, 255))] if t == 4 else [])


def icon_crossbow(t, stock, prod, metal, stringm, accent, arcane):
    """Mesmo desenho da besta do vanilla: coronha na diagonal ate embaixo-direita, arco em cima-esquerda."""
    c = Canvas(16, 16)
    bar(c, (3.0, 3.0), (14.5, 14.5), 3.0, stock)
    mh, ml, mm, md_ = P(metal)
    for (x, y) in ((13, 14), (14, 13), (14, 14), (15, 14), (14, 15)):
        c.set(x, y, mm)
    ph, pl, pm, pd = P(prod)
    arc = [(13, 1), (12, 1), (11, 1), (10, 1), (9, 1), (8, 1), (7, 2), (6, 2), (5, 2), (4, 3), (3, 4), (2, 5), (2, 6), (2, 7),
           (1, 8), (1, 9), (1, 10), (1, 11), (1, 12), (1, 13)]
    for i, (x, y) in enumerate(arc):
        c.set(x, y, pm)
        c.set(x, y + 1 if i < 7 else y, pl if i % 3 == 0 else pd)
    sh, sl, sm, sd = P(stringm)
    px_line(c, (13, 2), (8, 7), sl if not stringm.glow else sm)
    px_line(c, (2, 13), (7, 8), sl if not stringm.glow else sm)
    c.set(4, 4, mm); c.set(3, 3, ml)
    ah, al, am, ad = P(accent)
    if arcane:
        c.set(4, 3, al); c.set(3, 4, am)
        c.set(10, 9, ml); c.set(9, 10, mm)  # engrenagem
        if t >= 3:
            for (x, y) in ((7, 6), (9, 8)):
                c.set(x, y, P(RUNE_VIOLET)[1])
    else:
        for (x, y) in ((11, 12), (12, 11), (12, 12)):
            c.set(x, y, am)
        c.set(9, 9, mm)
    return finish(c, [(1, 7, (255, 255, 255, 255))] if t == 4 else [])


def icon_lute(t, body, face, neck, trim, rosette):
    c = Canvas(16, 16)
    bh, bl, bm, bd = P(body)
    fh, fl, fm, fd = P(face)
    # braco da caixa (embaixo-esquerda) ate o cravelhal (cima-direita)
    nh, nl, nm, nd = P(neck)
    for i in range(7):
        c.set(7 + i, 8 - i, nl)
        c.set(8 + i, 8 - i, nd)
    th, tl, tm, td = P(trim)
    c.set(14, 1, tm); c.set(15, 1, td); c.set(14, 0, tl); c.set(13, 0, tm); c.set(15, 2, td)
    # caixa (bojo) em pera
    c.circle(5.0, 10.5, 4.6, fm)
    c.circle(7.4, 8.2, 2.2, fm)
    c.shade_sphere(5.0, 10.5, 4.6, 0.3, 0.35)
    # roseta e cavalete
    rh, rl, rm, rd = P(rosette)
    for (x, y) in ((5, 9), (6, 9), (5, 10), (6, 10)):
        c.set(x, y, rm if not rosette.glow else rl)
    c.set(5, 9, rd if not rosette.glow else rh)
    c.set(3, 12, td); c.set(4, 13, td); c.set(2, 12, tm)
    # cordas
    px_line(c, (3, 12), (12, 3), (255, 250, 235, 255) if t < 4 else P(GLOW_HOLY)[1])
    # filete
    for (x, y) in ((1, 9), (1, 10), (9, 13), (8, 14)):
        c.set(x, y, bd)
    return finish(c, [(14, 1, (255, 255, 255, 255))] if t == 4 else [])


def icon_knight_shield():
    c = Canvas(16, 16)
    ph, pl, pm, pd = P(ENAMEL_BLUE)
    poly(c, [(2.0, 0.6), (14.0, 0.6), (14.0, 8.0), (8.0, 15.6), (2.0, 8.0)], ENAMEL_BLUE)
    sh, sl, sm, sd = P(STEEL)
    for x in range(2, 14):
        c.set(x, 0, sl)
    for y in range(0, 9):
        c.set(2, y, sl); c.set(13, y, sm)
    for i in range(7):
        c.set(2 + i, 8 + i, sl if i < 3 else sm); c.set(13 - i, 8 + i, sd)
    gh, gl, gm, gd = P(GOLD)
    for y in range(1, 14):
        c.set(7, y, gl); c.set(8, y, gm)
    for x in range(3, 13):
        c.set(x, 5, gl); c.set(x, 6, gm)
    blob(c, 8.0, 6.0, 1.7, GEM_SUN)
    return finish(c, [(12, 1, (255, 255, 255, 255))])


V09_ICONS = {
    ("longsword", 1): lambda: icon_longsword(1, STEEL_BLADE, IRON, LEATHER, None, DARK_BLADE),
    ("longsword", 2): lambda: icon_longsword(2, STEEL_BLADE, GOLD, RED_WRAP, GEM_RED, IRON),
    ("longsword", 3): lambda: icon_longsword(3, STEEL_BLADE, DARK_IRON, BLACK_WRAP, GEM_AMBER, RUNE_FIRE),
    ("longsword", 4): lambda: icon_longsword(4, STEEL_BLADE, GOLD, RED_WRAP, GEM_RED, GLOW_FIRE),
    ("holy_mace", 1): lambda: icon_mace(1, OAK, IRON, LEATHER, None),
    ("holy_mace", 2): lambda: icon_mace(2, WALNUT, SILVER, WHITE_WRAP, GEM_SUN),
    ("holy_mace", 3): lambda: icon_mace(3, IVORY, WHITE_GOLD, GOLD_WRAP, GEM_SUN),
    ("holy_mace", 4): lambda: icon_mace(4, IVORY, GOLD, WHITE_WRAP, GEM_SUN, halo=True),
    ("greataxe", 1): lambda: icon_axe(1, DARK_OAK, IRON, STEEL_BLADE, LEATHER, False),
    ("greataxe", 2): lambda: icon_axe(2, BONE, DARK_IRON, STEEL_BLADE, RED_WRAP, False),
    ("greataxe", 3): lambda: icon_axe(3, EBONY, OBSIDIAN_BLADE, RUNE_CRIMSON, BLACK_WRAP, True),
    ("greataxe", 4): lambda: icon_axe(4, OBSIDIAN, OBSIDIAN_BLADE, GLOW_EMBER, RED_WRAP, True),
    ("ki_gauntlets", 1): lambda: icon_gauntlet(1, WHITE_WRAP, LEATHER, None, None),
    ("ki_gauntlets", 2): lambda: icon_gauntlet(2, TEAL_WRAP, BRONZE, GEM_JADE, None),
    ("ki_gauntlets", 3): lambda: icon_gauntlet(3, TEAL_WRAP, SILVER, GEM_JADE, RUNE_CYAN),
    ("ki_gauntlets", 4): lambda: icon_gauntlet(4, WHITE_WRAP, GOLD, GEM_JADE, GLOW_KI),
    ("knight_blade", 1): lambda: icon_blade(1, STEEL_BLADE, IRON, LEATHER, GEM_PALE_BLUE, fuller=DARK_BLADE),
    ("knight_blade", 2): lambda: icon_blade(2, STEEL_BLADE, SILVER, BLUE_WRAP, GEM_PALE_BLUE, fuller=DARK_BLADE),
    ("knight_blade", 3): lambda: icon_blade(3, STEEL_BLADE, BLUE_STEEL, ROYAL_WRAP, GEM_SUN, fuller=RUNE_GOLD),
    ("knight_blade", 4): lambda: icon_blade(4, STEEL_BLADE, GOLD, ROYAL_WRAP, GEM_SUN, fuller=GLOW_HOLY),
    ("longbow", 1): lambda: icon_bow(1, YEW, YEW, LEATHER, STRING, None),
    ("longbow", 2): lambda: icon_bow(2, ELM, BONE, GREEN_WRAP, STRING, GEM_GREEN),
    ("longbow", 3): lambda: icon_bow(3, MAPLE, SILVER, GREEN_WRAP, STRING, GEM_GREEN),
    ("longbow", 4): lambda: icon_bow(4, IVORY, GOLD, GOLD_WRAP, GLOW_WIND, GEM_CYAN),
    ("twin_daggers", 1): lambda: icon_daggers(1, STEEL_BLADE, IRON, LEATHER, None),
    ("twin_daggers", 2): lambda: icon_daggers(2, STEEL_BLADE, DARK_IRON, BLACK_WRAP, None),
    ("twin_daggers", 3): lambda: icon_daggers(3, DARK_BLADE, SILVER, PURPLE_WRAP, RUNE_VIOLET),
    ("twin_daggers", 4): lambda: icon_daggers(4, OBSIDIAN_BLADE, DARK_IRON, BLACK_WRAP, GLOW_VIOLET),
    ("lute", 1): lambda: icon_lute(1, SPRUCE, MAPLE, SPRUCE, DARK_OAK, DARK),
    ("lute", 2): lambda: icon_lute(2, MAPLE, MAPLE, ROSEWOOD, GOLD, GOLD),
    ("lute", 3): lambda: icon_lute(3, EBONY, MAPLE, EBONY, SILVER, RUNE_VIOLET),
    ("lute", 4): lambda: icon_lute(4, IVORY, WHITE_GOLD, IVORY, GOLD, GLOW_HOLY),
    ("arcane_crossbow", 1): lambda: icon_crossbow(1, ROSEWOOD, DARK_IRON, BRASS, STRING, GEM_VIOLET, True),
    ("arcane_crossbow", 2): lambda: icon_crossbow(2, ROSEWOOD, BRASS, BRASS, STRING, GEM_VIOLET, True),
    ("arcane_crossbow", 3): lambda: icon_crossbow(3, EBONY, DARK_IRON, BRASS, GLOW_VIOLET, GEM_VIOLET, True),
    ("arcane_crossbow", 4): lambda: icon_crossbow(4, EBONY, OBSIDIAN, GOLD, GLOW_VIOLET, GLOW_VIOLET, True),
    ("hunter_crossbow", 1): lambda: icon_crossbow(1, DARK_OAK, OAK, IRON, STRING, LEATHER, False),
    ("hunter_crossbow", 2): lambda: icon_crossbow(2, WALNUT, IRON, IRON, STRING, FUR, False),
    ("hunter_crossbow", 3): lambda: icon_crossbow(3, WALNUT, STEEL, DARK_IRON, STRING, BONE, False),
    ("hunter_crossbow", 4): lambda: icon_crossbow(4, EBONY, STEEL, STEEL, STRING, GLOW_EMBER, False),
}
ICONS.update(V09_ICONS)


def icon_vitality_charm():
    c = Canvas(16, 16)
    gh, gl, gm, gd = P(GOLD)
    # cordao
    for (x, y) in ((4, 1), (5, 2), (6, 3), (11, 1), (10, 2), (9, 3)):
        c.set(x, y, rgb("c8a070"))
    c.set(7, 4, gl); c.set(8, 4, gm)
    # coracao em moldura de ouro
    heart = ["..##.##..",
             ".#######.",
             ".#######.",
             "..#####..",
             "...###...",
             "....#...."]
    for y, row in enumerate(heart):
        for x, ch in enumerate(row):
            if ch == "#":
                c.set(3 + x, 5 + y, gm)
    inner = ["...#.#...",
             "..#####..",
             "...###...",
             "....#...."]
    red = P(GEM_RED)
    for y, row in enumerate(inner):
        for x, ch in enumerate(row):
            if ch == "#":
                c.set(3 + x, 6 + y, red[2])
    c.set(6, 7, red[1]); c.set(7, 7, red[0]); c.set(9, 8, red[3])
    c.set(4, 6, gh); c.set(10, 7, gd)
    return finish(c)


def icon_ward_ring():
    c = Canvas(16, 16)
    sh, sl, sm, sd = P(SILVER)
    # aro visto em perspectiva
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 5.5, (y + 0.5 - 9.5) / 3.6
            d = dx * dx + dy * dy
            if 0.55 <= d <= 1.0:
                c.set(x, y, sl if y < 9 else (sm if y < 11 else sd))
    blob(c, 8, 5, 2.4, GEM_CYAN)
    c.set(5, 6, sh); c.set(11, 6, sm)
    return finish(c)


def icon_archmage_sigil():
    c = Canvas(16, 16)
    gh, gl, gm, gd = P(GOLD)
    for (x, y) in ((3, 0), (4, 1), (5, 2), (12, 0), (11, 1), (10, 2)):
        c.set(x, y, gm)
    # disco dourado com estrela
    c.circle(8, 9, 5.5, gm)
    c.circle(8, 9, 4.3, gd)
    star = [(8, 5), (8, 6), (7, 7), (8, 7), (9, 7), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8),
            (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (7, 10), (8, 10), (9, 10), (6, 11), (7, 11), (9, 11), (10, 11),
            (5, 12), (11, 12)]
    for (x, y) in star:
        c.set(x, y, gl)
    blob(c, 8, 9, 1.6, GEM_ARCANE)
    c.set(5, 5, gh); c.set(4, 7, gh)
    return finish(c)


def icon_template():
    c = Canvas(16, 16)
    base = rgb("3a3550"); mid = rgb("4e4870"); lt = rgb("6a6494")
    for y in range(1, 15):
        for x in range(3, 13):
            c.set(x, y, mid if (x + y) % 5 else base)
    for x in range(3, 13):
        c.set(x, 1, lt)
    for y in range(1, 15):
        c.set(3, y, lt)
        c.set(12, y, base)
    rh, rl, rm, rd = P(RUNE_VIOLET)
    # runa em losango e linhas douradas
    for (x, y) in ((8, 3), (7, 4), (9, 4), (6, 5), (10, 5), (7, 6), (9, 6), (8, 7)):
        c.set(x, y, rl)
    c.set(8, 5, rh)
    gh, gl, gm, gd = P(GOLD)
    for x in range(5, 11):
        c.set(x, 10, gm)
    for x in range(6, 10):
        c.set(x, 12, gl)
    c.set(8, 9, gl)
    return finish(c)


def icon_arcane_diamond():
    c = Canvas(16, 16)
    rows = ["....######....",
            "...#hlllmmd#..",
            "..#hlllmmmdd#.",
            ".############.",
            "..#lllmmmmd#..",
            "...#llmmmd#...",
            "....#lmmd#....",
            ".....#md#.....",
            "......##......"]
    pal = {"#": rgb("4fb8e0"), "h": rgb("ffffff"), "l": rgb("c8f4ff"), "m": rgb("9fe0ff"), "d": rgb("b98cff")}
    c.from_rows(rows, pal, ox=1, oy=3)
    c.set(13, 1, rgb("e3ccff")); c.set(2, 12, rgb("e3ccff"))
    return finish(c, [(14, 0, (255, 255, 255, 255))])


def slot_icon(kind):
    """Silhueta clara semitransparente para os espacos vazios da Mesa de Ferraria."""
    c = Canvas(16, 16)
    f = (255, 255, 255, 110)
    if kind == "staff":
        diag(c, 2, 13, 9, f, f)
        c.circle(12.4, 2.6, 2.2, f)
    elif kind == "wand":
        for i in range(5):
            c.set(3 + i, 13 - i, f); c.set(4 + i, 13 - i, f)
        for i in range(4):
            c.set(9 + i, 7 - i, f)
        c.circle(13.5, 2.5, 1.6, f)
    elif kind == "blade":
        for i in range(9):
            c.set(5 + i, 10 - i, f); c.set(5 + i, 9 - i, f); c.set(6 + i, 10 - i, f)
        for (x, y) in ((3, 9), (4, 10), (5, 11), (6, 12), (7, 13), (3, 12), (2, 13), (1, 14)):
            c.set(x, y, f)
    elif kind == "scythe":
        diag(c, 2, 14, 10, f, f)
        for (x, y) in ((12, 3), (11, 2), (10, 1), (9, 1), (8, 1), (7, 1), (6, 2), (5, 2), (4, 3), (3, 4), (2, 5),
                       (10, 2), (9, 2), (8, 2), (7, 2), (6, 3), (5, 3), (4, 4), (3, 5)):
            c.set(x, y, f)
    elif kind == "bow":
        for (x, y) in ((13, 1), (12, 1), (11, 1), (10, 1), (9, 2), (8, 2), (7, 2), (6, 3), (5, 3), (4, 4), (3, 5), (3, 6), (2, 7),
                       (2, 8), (2, 9), (1, 10), (1, 11), (1, 12), (1, 13)):
            c.set(x, y, f); c.set(x + 1, y + 1, f)
        c.line(14, 2, 2, 14, f)
    elif kind == "axe":
        diag(c, 2, 14, 10, f, f)
        rows = ["..####..", ".######.", "#######.", "######..", ".####..."]
        c.from_rows(rows, {"#": f}, ox=5, oy=0)
    elif kind == "crystal":
        for (x, y, h) in ((5, 6, 8), (8, 3, 11), (11, 7, 7)):
            for yy in range(y, y + h):
                c.set(x, yy, f); c.set(x + 1, yy, f)
            c.set(x, y - 1, f)
    elif kind == "diamond":
        rows = ["..####..", ".######.", "########", ".######.", "..####..", "...##..."]
        c.from_rows(rows, {"#": f}, ox=4, oy=5)
    elif kind == "core":
        c.circle(8, 8, 5, f)
        for (x, y) in ((8, 1), (8, 14), (1, 8), (14, 8)):
            c.set(x, y, f)
    return c


# =====================================================================================================
#  Saidas
# =====================================================================================================
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "isekai_mahou")
DATA = os.path.join(RES, "data", "isekai_mahou")
LANG = os.path.join(ROOT, "src", "main", "lang", "armory")
MERGE = os.path.join(ROOT, "src", "main", "merge", "armory")
NS = "isekai_mahou"
KINDS = ["mage_staff", "witch_wand", "pact_blade", "druid_staff", "reaper_scythe"]


def write_json(path, obj):
    import json
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def ns(p):
    return p if ":" in p else NS + ":" + p


def gen_weapons(preview_dir=None):
    for kind in KINDS:
        for t in range(1, 5):
            iid = "%s_t%d" % (kind, t)
            model = DESIGNS[kind](t)
            tex = ns("item/armory/" + iid)
            held, atlas = model.build(tex, seed=zlib.crc32(iid.encode()) & 0xFFFF)
            held["display"] = DISPLAY[STYLE[kind]]
            atlas.save(os.path.join(ASSETS, "textures", "item", "armory", iid + ".png"))
            write_json(os.path.join(ASSETS, "models", "item", "armory", iid + "_held.json"), held)
            # icone 2D no inventario e no quadro; modelo 3D na mao e no chao
            ICONS[(kind, t)]().save(os.path.join(ASSETS, "textures", "item", iid + ".png"))
            flat = {"parent": "minecraft:item/generated", "textures": {"layer0": ns("item/" + iid)}}
            write_json(os.path.join(ASSETS, "models", "item", iid + ".json"), {
                "loader": "neoforge:separate_transforms",
                "gui_light": "front",
                "textures": {"particle": ns("item/" + iid)},
                "base": {"parent": ns("item/armory/" + iid + "_held")},
                "perspectives": {"gui": flat, "fixed": flat},
            })
            if preview_dir:
                preview(model, os.path.join(preview_dir, iid + "_a.png"), yaw=35, pitch=12)
                preview(model, os.path.join(preview_dir, iid + "_b.png"), yaw=-60, pitch=20)


def gen_misc_items():
    simple = {
        "vitality_charm": icon_vitality_charm,
        "ward_ring": icon_ward_ring,
        "archmage_sigil": icon_archmage_sigil,
        "arcane_upgrade_template": icon_template,
        "arcane_diamond": icon_arcane_diamond,
    }
    for iid, fn in simple.items():
        fn().save(os.path.join(ASSETS, "textures", "item", iid + ".png"))
        write_json(os.path.join(ASSETS, "models", "item", iid + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": ns("item/" + iid)}})
    for k in ("staff", "wand", "blade", "scythe", "bow", "axe", "crystal", "diamond", "core"):
        slot_icon(k).save(os.path.join(ASSETS, "textures", "item", "empty_slot_armory_" + k + ".png"))


# ------------------------------------------------------------------ receitas
def item(i):
    return {"item": ns(i)}


ESSENCES_ANY = [item("essence_" + e) for e in ("arcane", "fire", "water", "earth", "wind", "light", "shadow")]
CORES_ANY = [item("guardian_core_" + e) for e in ("fire", "water", "earth", "wind", "light", "shadow")]


def shaped(name, pattern, key, result, count=1, unlock="mana_crystal"):
    write_json(os.path.join(DATA, "recipe", "armory", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "group": "isekai_mahou_armory",
        "pattern": pattern, "key": key, "result": {"id": ns(result), "count": count}})
    rid = ns("armory/" + name)
    write_json(os.path.join(DATA, "advancement", "recipes", "armory", name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": rid}},
            "has_item": {"trigger": "minecraft:inventory_changed",
                         "conditions": {"items": [{"items": ns(unlock)}]}},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": [rid]},
    })


def smithing(name, base, addition, result, conditions=None):
    obj = {}
    if conditions:
        obj["neoforge:conditions"] = conditions
    obj.update({"type": "minecraft:smithing_transform", "template": item("arcane_upgrade_template"),
                "base": item(base), "addition": addition, "result": {"id": ns(result), "count": 1}})
    write_json(os.path.join(DATA, "recipe", "armory", name + ".json"), obj)


def gen_recipes():
    stick = {"item": "minecraft:stick"}
    shaped("mage_staff_t1", [" GC", " SG", "S  "],
           {"G": {"item": "minecraft:gold_ingot"}, "C": item("mana_crystal"), "S": stick}, "mage_staff_t1")
    shaped("witch_wand_t1", ["  A", " E ", "S  "],
           {"A": {"item": "minecraft:amethyst_shard"}, "E": item("essence_water"), "S": stick}, "witch_wand_t1",
           unlock="essence_water")
    shaped("pact_blade_t1", [" I ", "EIE", " S "],
           {"I": {"item": "minecraft:iron_ingot"}, "E": item("essence_shadow"), "S": stick}, "pact_blade_t1",
           unlock="essence_shadow")
    shaped("druid_staff_t1", ["  E", " L ", "S  "],
           {"E": item("essence_earth"), "L": {"tag": "minecraft:leaves"}, "S": stick}, "druid_staff_t1",
           unlock="essence_earth")
    shaped("reaper_scythe_t1", ["IIE", " B ", "B  "],
           {"I": {"item": "minecraft:iron_ingot"}, "E": item("essence_shadow"), "B": {"item": "minecraft:bone"}},
           "reaper_scythe_t1", unlock="essence_shadow")
    shaped("vitality_charm", [" S ", "GAG", " E "],
           {"S": {"item": "minecraft:string"}, "G": {"item": "minecraft:gold_ingot"},
            "A": {"item": "minecraft:golden_apple"}, "E": item("essence_earth")}, "vitality_charm", unlock="essence_earth")
    shaped("ward_ring", ["EDE", "I I", " I "],
           {"E": item("essence_earth"), "D": {"item": "minecraft:diamond"}, "I": {"item": "minecraft:iron_ingot"}},
           "ward_ring", unlock="essence_earth")
    shaped("archmage_sigil", ["S S", "GDG", " C "],
           {"S": {"item": "minecraft:string"}, "G": {"item": "minecraft:gold_ingot"}, "D": item("arcane_diamond"),
            "C": CORES_ANY}, "archmage_sigil", unlock="arcane_diamond")
    shaped("arcane_upgrade_template", ["GAG", "ACA", "GAG"],
           {"G": {"item": "minecraft:gold_ingot"}, "A": item("essence_arcane"), "C": item("mana_crystal")},
           "arcane_upgrade_template", unlock="essence_arcane")
    shaped("arcane_upgrade_template_copy", [" A ", "ATA", " C "],
           {"A": item("essence_arcane"), "T": item("arcane_upgrade_template"), "C": item("mana_crystal")},
           "arcane_upgrade_template", count=2, unlock="arcane_upgrade_template")
    shaped("arcane_diamond", [" E ", "EDE", " E "],
           {"E": ESSENCES_ANY, "D": {"item": "minecraft:diamond"}}, "arcane_diamond", unlock="essence_arcane")

    block = "isekai_mahou:mana_crystal_block"
    for kind in KINDS:
        smithing("%s_t2_upgrade" % kind, "%s_t1" % kind, item(block), "%s_t2" % kind,
                 conditions=[{"type": "neoforge:item_exists", "item": block}])
        # reserva se o bloco de cristal (modulo ascension) nao existir
        smithing("%s_t2_upgrade_alt" % kind, "%s_t1" % kind, {"item": "minecraft:amethyst_block"}, "%s_t2" % kind,
                 conditions=[{"type": "neoforge:not", "value": {"type": "neoforge:item_exists", "item": block}}])
        smithing("%s_t3_upgrade" % kind, "%s_t2" % kind, item("arcane_diamond"), "%s_t3" % kind)
        smithing("%s_t4_upgrade" % kind, "%s_t3" % kind, CORES_ANY, "%s_t4" % kind)


# ------------------------------------------------------------------ tags, loja
def gen_tags_and_shop():
    write_json(os.path.join(MERGE, "data", "curios", "tags", "item", "charm.json"),
               {"replace": False, "values": [ns("vitality_charm")]})
    write_json(os.path.join(MERGE, "data", "curios", "tags", "item", "ring.json"),
               {"replace": False, "values": [ns("ward_ring")]})
    write_json(os.path.join(MERGE, "data", "curios", "tags", "item", "necklace.json"),
               {"replace": False, "values": [ns("archmage_sigil")]})
    # lamina e foice sao espadas: encantamentos de espada, varrer, etc.
    write_json(os.path.join(MERGE, "data", "minecraft", "tags", "item", "swords.json"),
               {"replace": False, "values": [ns("%s_t%d" % (k, t)) for k in ("pact_blade", "reaper_scythe") for t in range(1, 5)]})

    def offer(rank, price, iid, count=1):
        return {"rank": rank, "price": price, "item": {"id": ns(iid), "count": count}}

    offers = []
    for k in KINDS:
        offers.append(offer("E", 20, k + "_t1"))
    offers += [
        offer("D", 25, "arcane_upgrade_template"),
        offer("D", 45, "vitality_charm"),
        offer("C", 40, "arcane_diamond"),
        offer("C", 80, "ward_ring"),
    ]
    for k in KINDS:
        offers.append(offer("B", 120, k + "_t2"))
    offers += [
        offer("A", 70, "arcane_upgrade_template", 2),
        offer("A", 260, "archmage_sigil"),
    ]
    shop = os.path.join(DATA, "guild_shop", "armory.json")
    if not os.path.exists(shop):
        write_json(shop, {"offers": offers})


# ------------------------------------------------------------------ traducoes
WEAPON_NAMES = {
    "mage_staff": [("Cajado de Aprendiz", "Apprentice's Staff"), ("Cajado do Adepto", "Adept's Staff"),
                   ("Cajado do Mestre Arcano", "Arcane Master's Staff"), ("Cajado do Arquimago", "Archmage's Staff")],
    "witch_wand": [("Varinha de Bétula", "Birch Wand"), ("Varinha da Lua Crescente", "Crescent Moon Wand"),
                   ("Varinha do Eclipse", "Eclipse Wand"), ("Varinha da Bruxa Ancestral", "Elder Witch's Wand")],
    "pact_blade": [("Lâmina do Pacto Menor", "Lesser Pact Blade"), ("Lâmina do Pacto Selado", "Sealed Pact Blade"),
                   ("Lâmina do Pacto Infernal", "Infernal Pact Blade"), ("Lâmina do Pacto Soberano", "Sovereign Pact Blade")],
    "druid_staff": [("Cajado de Galhos Jovens", "Young Branch Staff"), ("Cajado de Galhos Floridos", "Blossoming Branch Staff"),
                    ("Cajado do Bosque Ancestral", "Ancient Grove Staff"), ("Cajado da Árvore-Mãe", "Mother Tree Staff")],
    "reaper_scythe": [("Foice do Coveiro", "Gravedigger's Scythe"), ("Foice Ossuária", "Ossuary Scythe"),
                      ("Foice do Ceifador", "Reaper's Scythe"), ("Foice do Senhor da Morte", "Deathlord's Scythe")],
}
WEAPON_LORE = {
    "mage_staff": [("Carvalho simples e um cristal azul: o primeiro foco de todo estudante.",
                    "Plain oak and a blue crystal: every student's first focus."),
                   ("Nogueira com anéis de ouro; o cristal arcano zumbe baixinho.",
                    "Walnut ringed in gold; the arcane crystal hums softly."),
                   ("Ébano rúnico e uma moldura de prata que guarda luas de cristal.",
                    "Runic ebony and a silver frame cradling crystal moons."),
                   ("Marfim e ouro, um halo de estrelas: o símbolo de quem domina a magia.",
                    "Ivory and gold, a halo of stars: the mark of a true master.")],
    "witch_wand": [("Bétula do bosque das bruxas, com uma lasca de ametista na ponta.",
                    "Birch from the witches' grove, tipped with an amethyst shard."),
                   ("Cerejeira e prata: brilha mais forte nas noites de lua.",
                    "Cherry wood and silver: it shines brighter under the moon."),
                   ("Um disco de sombra engole a luz; as runas violetas nunca dormem.",
                    "A shadow disc swallows the light; its violet runes never sleep."),
                   ("Obsidiana, ouro e um coração de cristal que pulsa com maldições antigas.",
                    "Obsidian, gold and a crystal heart that beats with old curses.")],
    "pact_blade": [("Aço escurecido selado com uma gota de sangue.",
                    "Darkened steel sealed with a drop of blood."),
                   ("Os chifres da guarda lembram quem fez o pacto.",
                    "The horned guard remembers who made the pact."),
                   ("Forjada no calor do abismo; o olho na guarda nunca pisca.",
                    "Forged in the heat of the abyss; the eye on the guard never blinks."),
                   ("A lâmina de um soberano dos pactos: tem sede, e sempre é saciada.",
                    "A pact sovereign's blade: it thirsts, and it is always sated.")],
    "druid_staff": [("Um galho jovem que ainda dá folhas na sua mão.",
                     "A young branch that still sprouts leaves in your hand."),
                    ("Floresce na primavera e em qualquer lugar onde houver magia.",
                     "It blooms in spring, and wherever there is magic."),
                    ("Musgo, âmbar e runas verdes: o bosque inteiro sussurra por ele.",
                     "Moss, amber and green runes: the whole grove whispers through it."),
                    ("Cortado com permissão da Árvore-Mãe; folhas de ouro e um sol no coração.",
                     "Cut with the Mother Tree's blessing; golden leaves and a sun at its heart.")],
    "reaper_scythe": [("Ferramenta de coveiro que aprendeu a colher outra coisa.",
                       "A gravedigger's tool that learned to reap something else."),
                      ("Cabo de vértebras e um crânio de guardião.",
                       "A spine for a handle and a guardian's skull."),
                      ("Os olhos do crânio seguem as almas que ainda não partiram.",
                       "The skull's eyes follow souls that have not yet departed."),
                      ("Coroada de prata, ceifa duas almas onde outras ceifam uma.",
                       "Crowned in silver, it reaps two souls where others reap one.")],
}

LANG_PT = {
    "itemGroup.isekai_mahou.armory": "Arsenal Arcano",
    "item.isekai_mahou.vitality_charm": "Talismã da Vitalidade",
    "item.isekai_mahou.vitality_charm.desc": "Um coração de rubi que bate junto com o seu: +2 corações.",
    "item.isekai_mahou.ward_ring": "Anel da Guarda",
    "item.isekai_mahou.ward_ring.desc": "Uma barreira fina como um véu envolve quem o usa.",
    "item.isekai_mahou.archmage_sigil": "Sigilo do Arquimago",
    "item.isekai_mahou.archmage_sigil.desc": "O selo dos grandes magos: todas as suas magias ficam mais fortes.",
    "item.isekai_mahou.arcane_upgrade_template": "Molde de Ferraria Arcano",
    "item.isekai_mahou.arcane_upgrade_template.applies_to": "Armas do Arsenal e trajes de classe",
    "item.isekai_mahou.arcane_upgrade_template.ingredients": "Material do próximo tier (veja a dica da peça)",
    "item.isekai_mahou.arcane_upgrade_template.upgrade": "Melhoria Arcana",
    "item.isekai_mahou.arcane_upgrade_template.base_slot": "Coloque uma arma do Arsenal ou uma peça de traje de classe",
    "item.isekai_mahou.arcane_upgrade_template.addition_slot": "Armas: II Bloco de Cristal de Mana · III Diamante Arcano · IV Núcleo de Guardião",
    "item.isekai_mahou.arcane_diamond": "Diamante Arcano",
    "item.isekai_mahou.arcane_diamond.desc": "Material da Melhoria Arcana: leva uma arma do Arsenal do tier II ao III.",
    "tooltip.isekai_mahou.armory.tier": "Tier %s · %s",
    "tooltip.isekai_mahou.armory.tier_name.1": "Aprendiz",
    "tooltip.isekai_mahou.armory.tier_name.2": "Adepto",
    "tooltip.isekai_mahou.armory.tier_name.3": "Mestre",
    "tooltip.isekai_mahou.armory.tier_name.4": "Arquimago",
    "tooltip.isekai_mahou.armory.class_weapon": "Arma de %s",
    "tooltip.isekai_mahou.armory.reaper_scythe.harvest": "Abates com golpes ceifam almas para o Filactério",
    "tooltip.isekai_mahou.armory.passive": "✦ Passiva: %s",
    "tooltip.isekai_mahou.armory.mage_staff.passive": "Eco Arcano",
    "tooltip.isekai_mahou.armory.mage_staff.passive.desc": "10% de chance de devolver todo o mana de cada magia.",
    "tooltip.isekai_mahou.armory.witch_wand.passive": "Maldição Persistente",
    "tooltip.isekai_mahou.armory.witch_wand.passive.desc": "Acertos de magia deixam o alvo Corrompido II por 5 s (dano contínuo).",
    "tooltip.isekai_mahou.armory.pact_blade.passive": "Sede do Pacto",
    "tooltip.isekai_mahou.armory.pact_blade.passive.desc": "Golpes roubam 25% do dano como vida (até 2,5 corações).",
    "tooltip.isekai_mahou.armory.druid_staff.passive": "Seiva Viva",
    "tooltip.isekai_mahou.armory.druid_staff.passive.desc": "A cada 2 s cura meio coração de você e dos aliados a até 6 blocos.",
    "tooltip.isekai_mahou.armory.reaper_scythe.passive": "Colheita Dupla",
    "tooltip.isekai_mahou.armory.reaper_scythe.passive.desc": "Cada abate colhe uma alma extra; sem Filactério, ela vira 6 de mana.",
    "tooltip.isekai_mahou.armory.upgrade_hint": "Melhore na Mesa de Ferraria: Molde Arcano + %s",
    "tooltip.isekai_mahou.armory.material.t2": "Bloco de Cristal de Mana",
    "tooltip.isekai_mahou.armory.material.t3": "Diamante Arcano",
    "tooltip.isekai_mahou.armory.material.t4": "Núcleo de Guardião",
    "message.isekai_mahou.armory.mana_echo": "✦ Eco Arcano: %s de mana devolvido!",
    "message.isekai_mahou.armory.soul_harvest": "☠ Ceifa: +%s alma(s) de %s",
    "message.isekai_mahou.armory.soul_mana": "☠ A alma extra virou %s de mana",
    "message.isekai_mahou.armory.level_reward": "✦ Recompensa de nível %s: %s",
    "message.isekai_mahou.armory.bundle": "✦ Suprimentos do nível %s: %s",
    "message.isekai_mahou.armory.catch_up": "✦ O Arsenal entregou as recompensas dos níveis que você já tinha alcançado!",
    "message.isekai_mahou.armory.kit": "✦ Recompensa de nível %s: você já tinha %s, então ganhou um Kit de Melhoria: %s",
    "toast.isekai_mahou.armory.kit": "Kit de Melhoria Arcana",
    "toast.isekai_mahou.armory.level_reward": "Recompensa de nível %s!",
    "toast.isekai_mahou.armory.level_rewards": "Recompensas dos níveis %s–%s!",
    "toast.isekai_mahou.armory.bundle": "Suprimentos arcanos",
    "toast.isekai_mahou.armory.bundle_scroll": "Suprimentos + %s",
    "toast.isekai_mahou.armory.bundle_scrolls": "Suprimentos + %s pergaminhos",
}
LANG_EN = {
    "itemGroup.isekai_mahou.armory": "Arcane Arsenal",
    "item.isekai_mahou.vitality_charm": "Charm of Vitality",
    "item.isekai_mahou.vitality_charm.desc": "A ruby heart that beats along with yours: +2 hearts.",
    "item.isekai_mahou.ward_ring": "Ward Ring",
    "item.isekai_mahou.ward_ring.desc": "A barrier as thin as a veil surrounds its wearer.",
    "item.isekai_mahou.archmage_sigil": "Archmage's Sigil",
    "item.isekai_mahou.archmage_sigil.desc": "The seal of the great mages: all your spells grow stronger.",
    "item.isekai_mahou.arcane_upgrade_template": "Arcane Smithing Template",
    "item.isekai_mahou.arcane_upgrade_template.applies_to": "Arsenal weapons and class outfits",
    "item.isekai_mahou.arcane_upgrade_template.ingredients": "Next tier's material (see the item's tooltip)",
    "item.isekai_mahou.arcane_upgrade_template.upgrade": "Arcane Upgrade",
    "item.isekai_mahou.arcane_upgrade_template.base_slot": "Add an Arsenal weapon or a class outfit piece",
    "item.isekai_mahou.arcane_upgrade_template.addition_slot": "Weapons: II Mana Crystal Block · III Arcane Diamond · IV Guardian Core",
    "item.isekai_mahou.arcane_diamond": "Arcane Diamond",
    "item.isekai_mahou.arcane_diamond.desc": "Arcane Upgrade material: takes an Arsenal weapon from tier II to III.",
    "tooltip.isekai_mahou.armory.tier": "Tier %s · %s",
    "tooltip.isekai_mahou.armory.tier_name.1": "Apprentice",
    "tooltip.isekai_mahou.armory.tier_name.2": "Adept",
    "tooltip.isekai_mahou.armory.tier_name.3": "Master",
    "tooltip.isekai_mahou.armory.tier_name.4": "Archmage",
    "tooltip.isekai_mahou.armory.class_weapon": "%s weapon",
    "tooltip.isekai_mahou.armory.reaper_scythe.harvest": "Melee kills reap souls into your Phylactery",
    "tooltip.isekai_mahou.armory.passive": "✦ Passive: %s",
    "tooltip.isekai_mahou.armory.mage_staff.passive": "Arcane Echo",
    "tooltip.isekai_mahou.armory.mage_staff.passive.desc": "10% chance to refund all the mana of each spell.",
    "tooltip.isekai_mahou.armory.witch_wand.passive": "Lingering Curse",
    "tooltip.isekai_mahou.armory.witch_wand.passive.desc": "Spell hits leave the target Corrupted II for 5 s (damage over time).",
    "tooltip.isekai_mahou.armory.pact_blade.passive": "Pact Thirst",
    "tooltip.isekai_mahou.armory.pact_blade.passive.desc": "Strikes steal 25% of the damage as health (up to 2.5 hearts).",
    "tooltip.isekai_mahou.armory.druid_staff.passive": "Living Sap",
    "tooltip.isekai_mahou.armory.druid_staff.passive.desc": "Every 2 s heals half a heart to you and allies within 6 blocks.",
    "tooltip.isekai_mahou.armory.reaper_scythe.passive": "Double Harvest",
    "tooltip.isekai_mahou.armory.reaper_scythe.passive.desc": "Each kill reaps an extra soul; without a Phylactery it becomes 6 mana.",
    "tooltip.isekai_mahou.armory.upgrade_hint": "Upgrade at a Smithing Table: Arcane Template + %s",
    "tooltip.isekai_mahou.armory.material.t2": "Mana Crystal Block",
    "tooltip.isekai_mahou.armory.material.t3": "Arcane Diamond",
    "tooltip.isekai_mahou.armory.material.t4": "Guardian Core",
    "message.isekai_mahou.armory.mana_echo": "✦ Arcane Echo: %s mana refunded!",
    "message.isekai_mahou.armory.soul_harvest": "☠ Reaped: +%s soul(s) of %s",
    "message.isekai_mahou.armory.soul_mana": "☠ The extra soul became %s mana",
    "message.isekai_mahou.armory.level_reward": "✦ Level %s reward: %s",
    "message.isekai_mahou.armory.bundle": "✦ Level %s supplies: %s",
    "message.isekai_mahou.armory.catch_up": "✦ The Arsenal delivered the rewards for the levels you had already reached!",
    "message.isekai_mahou.armory.kit": "✦ Level %s reward: you already had %s, so you got an Upgrade Kit: %s",
    "toast.isekai_mahou.armory.kit": "Arcane Upgrade Kit",
    "toast.isekai_mahou.armory.level_reward": "Level %s reward!",
    "toast.isekai_mahou.armory.level_rewards": "Level %s–%s rewards!",
    "toast.isekai_mahou.armory.bundle": "Arcane supplies",
    "toast.isekai_mahou.armory.bundle_scroll": "Supplies + %s",
    "toast.isekai_mahou.armory.bundle_scrolls": "Supplies + %s scrolls",
}


def gen_lang():
    pt, en = {}, {}
    for kind in KINDS:
        for t in range(1, 5):
            iid = "%s_t%d" % (kind, t)
            pt["item.isekai_mahou." + iid] = WEAPON_NAMES[kind][t - 1][0]
            en["item.isekai_mahou." + iid] = WEAPON_NAMES[kind][t - 1][1]
            pt["item.isekai_mahou." + iid + ".desc"] = WEAPON_LORE[kind][t - 1][0]
            en["item.isekai_mahou." + iid + ".desc"] = WEAPON_LORE[kind][t - 1][1]
    pt.update(LANG_PT)
    en.update(LANG_EN)
    lang_v09(pt, en)
    lang_v10(pt, en)
    assert set(pt) == set(en), set(pt) ^ set(en)
    write_json(os.path.join(LANG, "pt_br.json"), pt)
    write_json(os.path.join(LANG, "en_us.json"), en)


# ------------------------------------------------------------------ v0.9: modelos com estados (arco, besta, escudo, alaude)
BOW_STATES = [({"pulling": 1}, 0), ({"pulling": 1, "pull": 0.65}, 1), ({"pulling": 1, "pull": 0.9}, 2)]
XBOW_STATES = [({"pulling": 1}, "pulling_0"), ({"pulling": 1, "pull": 0.58}, "pulling_1"), ({"pulling": 1, "pull": 1.0}, "pulling_2"),
               ({"charged": 1}, "arrow"), ({"charged": 1, "firework": 1}, "firework")]


def build_held(model, name, display, preview_dir=None, center=(8, 12, 8)):
    """Grava o modelo 3D (elements + atlas) de `name`; atlas maior se o de 32 nao couber."""
    try:
        held, atlas = model.build(ns("item/armory/" + name), seed=zlib.crc32(name.encode()) & 0xFFFF)
    except RuntimeError:
        model.atlas = 64
        held, atlas = model.build(ns("item/armory/" + name), seed=zlib.crc32(name.encode()) & 0xFFFF)
    held["display"] = display
    atlas.save(os.path.join(ASSETS, "textures", "item", "armory", name + ".png"))
    write_json(os.path.join(ASSETS, "models", "item", "armory", name + "_held.json"), held)
    if preview_dir:
        preview(model, os.path.join(preview_dir, name + "_a.png"), yaw=35, pitch=12, center=center)
        preview(model, os.path.join(preview_dir, name + "_b.png"), yaw=-60, pitch=20, center=center)


def wrapper(icon_id, held_name, overrides=None):
    """Modelo do item: icone 2D no inventario/quadro, modelo 3D na mao/chao, e os estados (overrides)."""
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": ns("item/" + icon_id)}}
    obj = {
        "loader": "neoforge:separate_transforms",
        "gui_light": "front",
        "textures": {"particle": ns("item/" + icon_id)},
        "base": {"parent": ns("item/armory/" + held_name + "_held")},
        "perspectives": {"gui": flat, "fixed": flat},
    }
    if overrides:
        obj["overrides"] = overrides
    return obj


def gen_weapons_v09(preview_dir=None):
    for kind in V09_KINDS:
        style = STYLE[kind]
        for t in range(1, 5):
            iid = "%s_t%d" % (kind, t)
            center = {"longbow": (5, 8, 8), "lute": (11, 8, 8), "ki_gauntlets": (8, 6, 10)}.get(kind, (8, 12, 8))
            if kind in ("arcane_crossbow", "hunter_crossbow"):
                center = (8, 8, 8)
            build_held(V09_DESIGNS[kind](t), iid, DISPLAY[style], preview_dir, center)
            ICONS[(kind, t)]().save(os.path.join(ASSETS, "textures", "item", iid + ".png"))
            overrides = []
            if kind == "longbow":
                for pred, st in BOW_STATES:
                    name = "%s_pulling_%d" % (iid, st)
                    build_held(longbow(t, st), name, DISPLAY[style], preview_dir if st == 2 else None, center)
                    write_json(os.path.join(ASSETS, "models", "item", "armory", name + ".json"), wrapper(iid, name))
                    overrides.append({"predicate": pred, "model": ns("item/armory/" + name)})
            elif kind in ("arcane_crossbow", "hunter_crossbow"):
                for pred, st in XBOW_STATES:
                    name = "%s_%s" % (iid, st)
                    build_held(crossbow(kind, t, st), name, DISPLAY[style], preview_dir if st == "arrow" else None, center)
                    write_json(os.path.join(ASSETS, "models", "item", "armory", name + ".json"), wrapper(iid, name))
                    overrides.append({"predicate": pred, "model": ns("item/armory/" + name)})
            elif kind == "lute":
                # tocando: mesma geometria, outra pose na mao (herda os elements do modelo parado)
                name = iid + "_playing"
                write_json(os.path.join(ASSETS, "models", "item", "armory", name + "_held.json"),
                           {"parent": ns("item/armory/" + iid + "_held"), "display": DISPLAY["lute_playing"]})
                write_json(os.path.join(ASSETS, "models", "item", "armory", name + ".json"), wrapper(iid, name))
                overrides.append({"predicate": {ns("playing"): 1}, "model": ns("item/armory/" + name)})
            write_json(os.path.join(ASSETS, "models", "item", iid + ".json"), wrapper(iid, iid, overrides))
    # Escudo do Cavaleiro (+ pose de bloqueio)
    build_held(knight_shield(), "knight_shield", DISPLAY["shield"], preview_dir, center=(0, 0, 0))
    write_json(os.path.join(ASSETS, "models", "item", "armory", "knight_shield_blocking_held.json"),
               {"parent": ns("item/armory/knight_shield_held"), "display": DISPLAY["shield_blocking"]})
    write_json(os.path.join(ASSETS, "models", "item", "armory", "knight_shield_blocking.json"),
               wrapper("knight_shield", "knight_shield_blocking"))
    write_json(os.path.join(ASSETS, "models", "item", "knight_shield.json"), wrapper("knight_shield", "knight_shield", [
        {"predicate": {"blocking": 1}, "model": ns("item/armory/knight_shield_blocking")}]))
    icon_knight_shield().save(os.path.join(ASSETS, "textures", "item", "knight_shield.png"))


def gen_recipes_v09():
    stick = {"item": "minecraft:stick"}
    iron = {"item": "minecraft:iron_ingot"}
    string = {"item": "minecraft:string"}
    leather = {"item": "minecraft:leather"}
    planks = {"tag": "minecraft:planks"}
    shaped("longsword_t1", [" I ", "FIF", " S "], {"I": iron, "F": item("essence_fire"), "S": stick}, "longsword_t1",
           unlock="essence_fire")
    shaped("holy_mace_t1", [" G ", "ILI", " S "], {"G": {"item": "minecraft:gold_ingot"}, "I": iron,
                                                 "L": item("essence_light"), "S": stick}, "holy_mace_t1", unlock="essence_light")
    shaped("greataxe_t1", ["IIE", "IS ", " S "], {"I": iron, "E": item("essence_earth"), "S": stick}, "greataxe_t1",
           unlock="essence_earth")
    shaped("ki_gauntlets_t1", ["L L", "IWI"], {"L": leather, "I": iron, "W": item("essence_wind")}, "ki_gauntlets_t1",
           unlock="essence_wind")
    shaped("knight_blade_t1", [" I ", "EIE", " S "], {"I": iron, "E": item("essence_earth"), "S": stick}, "knight_blade_t1",
           unlock="essence_earth")
    shaped("knight_shield", ["IEI", "IPI", " I "], {"I": iron, "E": item("essence_light"), "P": planks}, "knight_shield",
           unlock="essence_light")
    shaped("longbow_t1", [" SX", "E X", " SX"], {"S": stick, "X": string, "E": item("essence_wind")}, "longbow_t1",
           unlock="essence_wind")
    shaped("twin_daggers_t1", ["I I", "ESE"], {"I": iron, "E": item("essence_shadow"), "S": stick}, "twin_daggers_t1",
           unlock="essence_shadow")
    shaped("lute_t1", [" S ", "XNX", "PLP"], {"S": stick, "X": string, "N": {"item": "minecraft:note_block"}, "P": planks,
                                            "L": item("essence_light")}, "lute_t1", unlock="essence_light")
    shaped("arcane_crossbow_t1", ["SCS", "XTX", " S "], {"S": stick, "C": item("mana_crystal"), "X": string,
                                                       "T": {"item": "minecraft:tripwire_hook"}}, "arcane_crossbow_t1")
    shaped("hunter_crossbow_t1", ["LIL", "XTX", " S "], {"L": leather, "I": iron, "X": string, "S": stick,
                                                       "T": {"item": "minecraft:tripwire_hook"}}, "hunter_crossbow_t1",
           unlock="essence_earth")
    block = "isekai_mahou:mana_crystal_block"
    for kind in V09_KINDS:
        smithing("%s_t2_upgrade" % kind, "%s_t1" % kind, item(block), "%s_t2" % kind,
                 conditions=[{"type": "neoforge:item_exists", "item": block}])
        smithing("%s_t2_upgrade_alt" % kind, "%s_t1" % kind, {"item": "minecraft:amethyst_block"}, "%s_t2" % kind,
                 conditions=[{"type": "neoforge:not", "value": {"type": "neoforge:item_exists", "item": block}}])
        smithing("%s_t3_upgrade" % kind, "%s_t2" % kind, item("arcane_diamond"), "%s_t3" % kind)
        smithing("%s_t4_upgrade" % kind, "%s_t3" % kind, CORES_ANY, "%s_t4" % kind)


V09_MELEE = ["longsword", "holy_mace", "greataxe", "ki_gauntlets", "knight_blade", "twin_daggers"]
V09_BOWS = ["longbow"]
V09_XBOWS = ["arcane_crossbow", "hunter_crossbow"]


def tiers(kinds):
    return [ns("%s_t%d" % (k, t)) for k in kinds for t in range(1, 5)]


def tag(path, values):
    write_json(os.path.join(MERGE, "data", *path.split("/")) + ".json", {"replace": False, "values": values})


def gen_tags_and_shop_v09():
    # armas de golpe sao espadas (afiacao, saque, aspecto de fogo...); arcos/bestas com os encantamentos deles
    tag("minecraft/tags/item/swords", tiers(["pact_blade", "reaper_scythe"] + V09_MELEE))
    tag("minecraft/tags/item/enchantable/bow", tiers(V09_BOWS))
    tag("minecraft/tags/item/enchantable/crossbow", tiers(V09_XBOWS))
    tag("minecraft/tags/item/enchantable/durability", tiers(V09_BOWS + V09_XBOWS) + [ns("knight_shield")])
    tag("c/tags/item/tools/bow", tiers(V09_BOWS))
    tag("c/tags/item/tools/crossbow", tiers(V09_XBOWS))
    tag("c/tags/item/tools/ranged_weapon", tiers(V09_BOWS + V09_XBOWS))
    tag("c/tags/item/tools/melee_weapon", tiers(["pact_blade", "reaper_scythe"] + V09_MELEE))
    tag("c/tags/item/tools/shield", [ns("knight_shield")])
    # instrumentos: "tocando" = usando um item desta tag (o classes_ranged le por id)
    tag("isekai_mahou/tags/item/instruments", tiers(["lute"]))

    def offer(rank, price, iid, count=1):
        return {"rank": rank, "price": price, "item": {"id": ns(iid), "count": count}}

    offers = [offer("E", 20, k + "_t1") for k in V09_KINDS]
    offers.append(offer("E", 25, "knight_shield"))
    offers += [offer("B", 120, k + "_t2") for k in V09_KINDS]
    write_json(os.path.join(DATA, "guild_shop", "armory_classes.json"), {"offers": offers})


V09_NAMES = {
    "longsword": [("Espada Longa de Ferro", "Iron Longsword"), ("Espada Longa do Veterano", "Veteran's Longsword"),
                  ("Espada Longa da Brasa", "Ember Longsword"), ("Espada Longa do Herói", "Hero's Longsword")],
    "holy_mace": [("Maça do Acólito", "Acolyte's Mace"), ("Maça Consagrada", "Consecrated Mace"),
                  ("Maça da Alvorada", "Dawnbringer Mace"), ("Maça do Sol Invicto", "Mace of the Unconquered Sun")],
    "greataxe": [("Machado Grande de Ferro", "Iron Greataxe"), ("Machado do Caçador de Feras", "Beastslayer Greataxe"),
                 ("Machado Rúnico de Guerra", "Runic War Greataxe"), ("Machado da Fúria Primordial", "Greataxe of Primal Fury")],
    "ki_gauntlets": [("Faixas de Ki", "Ki Wraps"), ("Manoplas de Jade", "Jade Gauntlets"),
                     ("Manoplas da Maré", "Tidal Gauntlets"), ("Manoplas do Vendaval Celeste", "Celestial Gale Gauntlets")],
    "knight_blade": [("Lâmina do Escudeiro", "Squire's Blade"), ("Lâmina do Cavaleiro", "Knight's Blade"),
                     ("Lâmina da Guarda Real", "Royal Guard Blade"), ("Lâmina do Paladino Solar", "Solar Paladin Blade")],
    "longbow": [("Arco Longo de Teixo", "Yew Longbow"), ("Arco Longo do Bosque", "Woodland Longbow"),
                ("Arco Longo Élfico", "Elven Longbow"), ("Arco do Vento Eterno", "Bow of the Everlasting Wind")],
    "twin_daggers": [("Adagas Gêmeas", "Twin Daggers"), ("Adagas da Noite", "Night Daggers"),
                     ("Adagas Serrilhadas", "Serrated Daggers"), ("Adagas do Eclipse", "Eclipse Daggers")],
    "lute": [("Alaúde de Pinho", "Pine Lute"), ("Alaúde do Menestrel", "Minstrel's Lute"),
             ("Alaúde Encantado", "Enchanted Lute"), ("Alaúde da Aurora", "Lute of the Dawn")],
    "arcane_crossbow": [("Besta de Latão", "Brass Crossbow"), ("Besta Arcana", "Arcane Crossbow"),
                        ("Besta Rúnica do Artífice", "Artificer's Runic Crossbow"), ("Besta do Grande Engenho", "Grand Engine Crossbow")],
    "hunter_crossbow": [("Besta do Caçador", "Hunter's Crossbow"), ("Besta de Peles", "Furclad Crossbow"),
                        ("Besta de Osso e Aço", "Bone-and-Steel Crossbow"), ("Besta do Grande Caçador", "Great Hunter's Crossbow")],
}
V09_LORE = {
    "longsword": [("Aço simples e um cabo longo: a espada de quem começa a trilhar o caminho do guerreiro.",
                   "Plain steel and a long grip: the sword of anyone starting down the warrior's road."),
                  ("Guarda dourada e uma gema vermelha: carrega as marcas de cem duelos.",
                   "A golden guard and a red gem: it bears the marks of a hundred duels."),
                  ("Runas de brasa correm pelo sulco; a lâmina nunca esfria por completo.",
                   "Ember runes run down the fuller; the blade never fully cools."),
                  ("Chamas vivas sobem da guarda alada: a espada dos heróis das canções.",
                   "Living flames rise from the winged guard: the sword of the heroes in the songs.")],
    "holy_mace": [("Ferro e carvalho abençoados no templo da vila.",
                   "Iron and oak blessed at the village temple."),
                  ("Prata consagrada com o símbolo do sol em cada face.",
                   "Consecrated silver with the sun's symbol on each face."),
                  ("Ouro branco e runas douradas: os mortos-vivos fogem do seu brilho.",
                   "White gold and golden runes: the undead flee from its glow."),
                  ("Uma auréola de luz gira atrás da cabeça: o sol nunca se põe para quem a empunha.",
                   "A halo of light turns behind the head: the sun never sets for whoever wields it.")],
    "greataxe": [("Pesado, simples e honesto: resolve qualquer discussão.",
                  "Heavy, simple and honest: it settles any argument."),
                 ("Cabo de osso e pele de fera; o crânio no encaixe ainda rosna.",
                  "A bone haft and beast fur; the skull on the socket still growls."),
                 ("Duas lâminas de obsidiana com runas de sangue.",
                  "Two obsidian blades etched with blood runes."),
                 ("O fio queima como brasa e os olhos do crânio ardem com a sua fúria.",
                  "The edge burns like embers and the skull's eyes blaze with your fury.")],
    "ki_gauntlets": [("Faixas de pano e couro: o primeiro passo do caminho do punho.",
                      "Cloth wraps and leather: the first step on the way of the fist."),
                     ("Bronze e uma pedra de jade que guarda o fôlego do monge.",
                      "Bronze and a jade stone that holds the monk's breath."),
                     ("Placas de prata com runas de maré: cada soco flui como água.",
                      "Silver plates with tide runes: every punch flows like water."),
                     ("Anéis de ki brilhante e barbatanas de ouro: o vento obedece aos seus punhos.",
                      "Rings of bright ki and golden fins: the wind obeys your fists.")],
    "knight_blade": [("A primeira espada de quem jurou proteger os outros.",
                      "The first sword of one who swore to protect others."),
                     ("Prata e couro azul, com o brasão da ordem no pomo.",
                      "Silver and blue leather, with the order's crest on the pommel."),
                     ("Aço azulado e runas douradas da guarda do rei.",
                      "Bluish steel and the golden runes of the king's guard."),
                     ("Asas de ouro e um sulco de luz: nenhuma muralha cai enquanto ela estiver de pé.",
                      "Golden wings and a fuller of light: no wall falls while it stands.")],
    "longbow": [("Teixo curvado ao fogo e corda de linho.",
                 "Fire-bent yew and a linen string."),
                ("Pontas de osso e a gema verde dos guardiões da floresta.",
                 "Bone tips and the green gem of the forest wardens."),
                ("Madeira élfica com runas que sussurram a direção do vento.",
                 "Elven wood with runes that whisper the wind's direction."),
                ("A corda é feita de vento; as flechas não erram o que o olho de falcão vê.",
                 "The string is made of wind; its arrows never miss what the hawk's eye sees.")],
    "twin_daggers": [("Duas lâminas curtas: uma distrai, a outra resolve.",
                      "Two short blades: one distracts, the other settles it."),
                     ("Escurecidas para não brilhar na noite.",
                      "Darkened so they never shine at night."),
                     ("Serrilhadas e com fio de runas violetas.",
                      "Serrated and edged with violet runes."),
                     ("Forjadas num eclipse; a sombra delas corta antes da lâmina.",
                      "Forged during an eclipse; their shadow cuts before the blade.")],
    "lute": [("Pinho, tripa e uma boa vontade: toca as canções da taverna.",
              "Pine, gut strings and goodwill: it plays the tavern songs."),
             ("Bordo e ouro; o menestrel jura que ele afina sozinho.",
              "Maple and gold; the minstrel swears it tunes itself."),
             ("Ébano com runas que acendem a cada acorde.",
              "Ebony with runes that light up with every chord."),
             ("Marfim, ouro e cordas de luz: a canção dele cura e dá coragem.",
              "Ivory, gold and strings of light: its song heals and gives courage.")],
    "arcane_crossbow": [("Latão e jacarandá montados na bancada de um aprendiz de artífice.",
                         "Brass and rosewood assembled on an apprentice artificer's bench."),
                        ("Uma engrenagem arcana tensiona a corda num instante.",
                         "An arcane gear tensions the string in an instant."),
                        ("Runas violetas guiam os virotes; a corda brilha de mana.",
                         "Violet runes guide the bolts; the string glows with mana."),
                        ("O grande engenho do artífice: cada virote é um pequeno feitiço.",
                         "The artificer's grand engine: every bolt is a small spell.")],
    "hunter_crossbow": [("Robusta e confiável, feita para longas caçadas.",
                         "Sturdy and reliable, made for long hunts."),
                        ("Pele de lobo na coronha e um arco de ferro que não cede.",
                         "Wolf fur on the stock and an iron prod that never gives."),
                        ("Osso e aço: os virotes derrubam até ursos.",
                         "Bone and steel: its bolts drop even bears."),
                        ("A mira em brasa nunca perde a presa marcada.",
                         "The ember sight never loses the marked prey.")],
}
V09_TRAITS = {
    "longsword": (("Lâmina Equilibrada", "Golpes cheios varrem os inimigos em volta, como uma espada.",
                   "Ímpeto Heroico", "Golpes cheios acumulam Ímpeto (até 3, somem em 4 s); com 3, o próximo golpe cheio causa +60% e solta um corte de fogo."),
                  ("Balanced Blade", "Full strikes sweep nearby enemies, like a sword.",
                   "Heroic Momentum", "Full strikes build Momentum (up to 3, fades after 4 s); at 3, your next full strike deals +60% and unleashes a fire slash.")),
    "holy_mace": (("Abençoada", "+25% de dano contra mortos-vivos; acertos neles erguem um pilar de luz.",
                   "Julgamento Sagrado", "Contra mortos-vivos o bônus vira +50%, e golpes cheios curam você e os aliados a até 6 blocos (no máx. 1× a cada 2 s)."),
                  ("Blessed", "+25% damage against the undead; hits on them raise a pillar of light.",
                   "Holy Judgement", "The bonus against undead becomes +50%, and full strikes heal you and allies within 6 blocks (at most once every 2 s).")),
    "greataxe": (("Quebra-Escudos", "Como um machado, um golpe derruba a guarda de quem está se defendendo com escudo.",
                  "Fúria Sanguinária", "+1% de dano para cada 2% de vida que você perdeu (até +45%)."),
                 ("Shieldbreaker", "Like an axe, a strike knocks down the guard of anyone blocking with a shield.",
                  "Bloodfury", "+1% damage for every 2% of health you have lost (up to +45%).")),
    "ki_gauntlets": (("Punhos Velozes", "Socos rápidos e passos leves: mais velocidade de ataque e de movimento. Com a outra mão vazia, você usa as duas manoplas.",
                      "Palma do Vendaval", "A cada 4 golpes cheios seguidos, uma onda de ki fere (5) e empurra os inimigos em volta do alvo."),
                     ("Swift Fists", "Quick punches and light steps: more attack and movement speed. With your other hand empty, you wear both gauntlets.",
                      "Gale Palm", "Every 4 full strikes in a row, a ki wave damages (5) and pushes back the enemies around your target.")),
    "knight_blade": (("Guarda do Cavaleiro", "Armadura e firmeza contra empurrões; feita para lutar com o Escudo do Cavaleiro.",
                      "Bastião", "Bloquear um golpe com escudo devolve 40% do dano bloqueado ao atacante e dá Absorção."),
                     ("Knight's Guard", "Armor and footing against knockback; made to fight with the Knight's Shield.",
                      "Bastion", "Blocking a hit with a shield returns 40% of the blocked damage to the attacker and grants Absorption.")),
    "longbow": (("Corda Pesada", "Puxada mais longa e zoom maior; flechas bem mais rápidas e fortes. Puxada completa solta um estalo de vento.",
                 "Olho de Falcão", "Tiros com puxada completa causam +25% a 12+ blocos e +50% a 24+ blocos."),
                ("Heavy String", "Longer draw and stronger zoom; much faster, stronger arrows. A full draw releases with a gust of wind.",
                 "Hawk Eye", "Fully drawn shots deal +25% at 12+ blocks and +50% at 24+ blocks.")),
    "twin_daggers": (("Golpe Duplo", "Golpes cheios acertam de novo com a outra adaga (35% do dano). Com a outra mão vazia, você empunha as duas.",
                      "Lâminas Envenenadas", "O golpe duplo envenena (Veneno II por 3 s) e golpes pelas costas causam +40%."),
                     ("Double Strike", "Full strikes hit again with the other dagger (35% damage). With your other hand empty, you wield both.",
                      "Venomous Blades", "The double strike poisons (Poison II for 3 s) and strikes from behind deal +40%.")),
    "lute": (("Instrumento", "Segure o botão direito para tocar: +1 de Foco por segundo, e conta como foco mágico do Bardo.",
              "Balada Ressonante", "Enquanto toca, a cada 2 s você e os aliados a até 8 blocos ganham Regeneração I e Velocidade I."),
             ("Instrument", "Hold right-click to play: +1 Focus per second, and it counts as the Bard's spellcasting focus.",
              "Resonant Ballad", "While playing, every 2 s you and allies within 8 blocks gain Regeneration I and Speed I.")),
    "arcane_crossbow": (("Virotes Arcanos", "Carrega mais rápido e o dano dos virotes é multiplicado pelo seu Poder Mágico (até ×2).",
                         "Virote Explosivo", "Os virotes explodem no impacto: 5 de dano nos inimigos a até 2,5 blocos, sem quebrar blocos."),
                        ("Arcane Bolts", "Loads faster and bolt damage is multiplied by your Spell Power (up to ×2).",
                         "Explosive Bolt", "Bolts burst on impact: 5 damage to enemies within 2.5 blocks, without breaking blocks.")),
    "hunter_crossbow": (("Virotes Pesados", "Virotes mais fortes que deixam o alvo com Lentidão I por 2 s.",
                         "Presa Marcada", "O alvo atingido brilha por 8 s e recebe +25% de dano de você e das suas criaturas."),
                        ("Heavy Bolts", "Stronger bolts that leave the target Slowed I for 2 s.",
                         "Marked Prey", "The target glows for 8 s and takes +25% damage from you and your creatures.")),
}
V09_LANG_PT = {
    "item.isekai_mahou.knight_shield": "Escudo do Cavaleiro",
    "item.isekai_mahou.knight_shield.desc": "Aço azul e o sol da ordem: firme como uma muralha.",
    "tooltip.isekai_mahou.armory.trait": "◆ %s",
    "tooltip.isekai_mahou.armory.knight_shield.trait": "Muralha",
    "tooltip.isekai_mahou.armory.knight_shield.trait.desc": "Bloquear um golpe corpo a corpo empurra o atacante. Na mão secundária: +2 de armadura e +1 de resistência.",
    "tooltip.isekai_mahou.armory.projectile_damage": "%s de dano dos disparos",
    "tooltip.isekai_mahou.armory.projectile_speed": "%s de velocidade dos disparos",
    "tooltip.isekai_mahou.armory.draw_time": "Puxada completa: %s s",
    "tooltip.isekai_mahou.armory.charge_time": "Recarga: %s s",
}
V09_LANG_EN = {
    "item.isekai_mahou.knight_shield": "Knight's Shield",
    "item.isekai_mahou.knight_shield.desc": "Blue steel and the order's sun: steady as a wall.",
    "tooltip.isekai_mahou.armory.trait": "◆ %s",
    "tooltip.isekai_mahou.armory.knight_shield.trait": "Rampart",
    "tooltip.isekai_mahou.armory.knight_shield.trait.desc": "Blocking a melee hit pushes the attacker back. In the off hand: +2 armor and +1 toughness.",
    "tooltip.isekai_mahou.armory.projectile_damage": "%s shot damage",
    "tooltip.isekai_mahou.armory.projectile_speed": "%s shot speed",
    "tooltip.isekai_mahou.armory.draw_time": "Full draw: %s s",
    "tooltip.isekai_mahou.armory.charge_time": "Reload: %s s",
}


def lang_v09(pt, en):
    for kind in V09_KINDS:
        for t in range(1, 5):
            iid = "%s_t%d" % (kind, t)
            pt["item.isekai_mahou." + iid] = V09_NAMES[kind][t - 1][0]
            en["item.isekai_mahou." + iid] = V09_NAMES[kind][t - 1][1]
            pt["item.isekai_mahou." + iid + ".desc"] = V09_LORE[kind][t - 1][0]
            en["item.isekai_mahou." + iid + ".desc"] = V09_LORE[kind][t - 1][1]
        (pt_tr, pt_trd, pt_ps, pt_psd), (en_tr, en_trd, en_ps, en_psd) = V09_TRAITS[kind]
        base = "tooltip.isekai_mahou.armory." + kind
        pt[base + ".trait"], pt[base + ".trait.desc"], pt[base + ".passive"], pt[base + ".passive.desc"] = pt_tr, pt_trd, pt_ps, pt_psd
        en[base + ".trait"], en[base + ".trait.desc"], en[base + ".passive"], en[base + ".passive.desc"] = en_tr, en_trd, en_ps, en_psd
    pt.update(V09_LANG_PT)
    en.update(V09_LANG_EN)


# ------------------------------------------------------------------ v1.0: armas da v0.9 avulsas + inflacao
#  As classes da v0.9 sairam: as 10 armas delas e o Escudo do Cavaleiro viram equipamento AVULSO (sem classe). Os
#  numeros (armadura x1,5, mana x3, vida x2) ficam no Java (WeaponKind/ArmoryItems/KnightShieldItem); aqui os textos.
V10_LANG_PT = {
    "tooltip.isekai_mahou.armory.generic_weapon": "Arma avulsa (%s) — qualquer classe usa",
    "item.isekai_mahou.vitality_charm.desc": "Um coração de rubi que bate junto com o seu: +4 corações.",
    "tooltip.isekai_mahou.armory.knight_shield.trait.desc": "Bloquear um golpe corpo a corpo empurra o atacante. Na mão secundária: +3 de armadura e +1,5 de resistência.",
    "tooltip.isekai_mahou.armory.pact_blade.passive.desc": "Golpes roubam 25% do dano como vida (até 5 corações).",
    "tooltip.isekai_mahou.armory.druid_staff.passive.desc": "A cada 2 s cura um coração de você e dos aliados a até 6 blocos.",
    "tooltip.isekai_mahou.armory.reaper_scythe.passive.desc": "Cada abate colhe uma alma extra; sem Filactério, ela vira 18 de mana.",
    "tooltip.isekai_mahou.armory.ki_gauntlets.passive.desc": "A cada 4 golpes cheios seguidos, uma onda de ki fere (7,5) e empurra os inimigos em volta do alvo.",
    "tooltip.isekai_mahou.armory.arcane_crossbow.passive.desc": "Os virotes explodem no impacto: 7,5 de dano nos inimigos a até 2,5 blocos, sem quebrar blocos.",
}
V10_LANG_EN = {
    "tooltip.isekai_mahou.armory.generic_weapon": "Standalone weapon (%s) — any class can use it",
    "item.isekai_mahou.vitality_charm.desc": "A ruby heart that beats along with yours: +4 hearts.",
    "tooltip.isekai_mahou.armory.knight_shield.trait.desc": "Blocking a melee hit pushes the attacker back. In the off hand: +3 armor and +1.5 toughness.",
    "tooltip.isekai_mahou.armory.pact_blade.passive.desc": "Strikes steal 25% of the damage as health (up to 5 hearts).",
    "tooltip.isekai_mahou.armory.druid_staff.passive.desc": "Every 2 s heals one heart to you and allies within 6 blocks.",
    "tooltip.isekai_mahou.armory.reaper_scythe.passive.desc": "Each kill reaps an extra soul; without a Phylactery it becomes 18 mana.",
    "tooltip.isekai_mahou.armory.ki_gauntlets.passive.desc": "Every 4 full strikes in a row, a ki wave damages (7.5) and pushes back the enemies around your target.",
    "tooltip.isekai_mahou.armory.arcane_crossbow.passive.desc": "Bolts burst on impact: 7.5 damage to enemies within 2.5 blocks, without breaking blocks.",
}


def lang_v10(pt, en):
    pt.update(V10_LANG_PT)
    en.update(V10_LANG_EN)


def main():
    preview_dir = None
    if "--preview" in sys.argv:
        preview_dir = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(preview_dir, exist_ok=True)
    gen_weapons(preview_dir)
    gen_weapons_v09(preview_dir)
    gen_misc_items()
    gen_recipes()
    gen_recipes_v09()
    gen_tags_and_shop()
    gen_tags_and_shop_v09()
    gen_lang()
    print("armory: assets, receitas, tags, loja e traducoes gerados")


if __name__ == "__main__":
    # py -I tools/gen/armory.py          -> gera tudo
    # py -I tools/gen/armory.py --lang   -> so as traducoes
    if "--lang" in sys.argv:
        gen_lang()
    else:
        main()
