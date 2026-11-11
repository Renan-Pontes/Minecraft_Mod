"""
Gera TODOS os assets do modulo mounts (rode da raiz do projeto: py -I tools/gen/mounts.py):
  - modelos GeckoLib (.geo.json), animacoes (.animation.json), texturas + glowmask das 4 montarias
    (griffin, sky_ray, pegasus, wyvern)
  - item Apito de Montaria (textura 16x16 + modelo), modelos dos ovos
  - loot tables, receita, biome modifiers, tags (merge) e traducoes pt_br / en_us

Convencoes (conferidas no codigo do GeckoLib 4.9.3):
  * o modelo olha para -Z; +X (Bedrock) e o lado ESQUERDO da criatura;
  * rotacao X positiva abaixa a frente (membro para baixo vai para tras); Y positiva vira para a
    direita da criatura (asa esquerda +Y = para frente); Z positiva ABAIXA o lado esquerdo (+X) e
    LEVANTA o direito (-X) -> bater asa para cima: esquerda -Z, direita +Z;
  * UV de caixa: nas faces de cima/baixo a linha j=0 e a de TRAS (+Z) e a ultima e a da FRENTE;
    i=0 e o lado direito da criatura (-X). Lado "left" (+X): i=0 = frente. Lado "right" (-X):
    ultima coluna = frente. Face da frente: i=0 = lado direito da criatura.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten, mix, item_model  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/mounts")
LANG = os.path.join(ROOT, "src/main/lang/mounts")
NS = "isekai_mahou"
TAU = 2 * math.pi


def H(h, a=255):
    return hex_rgba(h, a)


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================
#  Modelo + textura (UV de caixa empacotado automaticamente)
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


SIDES = ("front", "back", "left", "right")
FACE_SHADE = {"top": 0.12, "front": 0.0, "left": -0.06, "right": -0.06, "back": -0.1, "bottom": -0.2}


def shade(col, face, k=1.0):
    f = FACE_SHADE[face] * k
    return lighten(col, f) if f > 0 else darken(col, -f)


def pattern_px(col, pattern, i, j, rnd):
    if pattern is None:
        return col
    r = rnd.random()
    if pattern == "fur":
        if r < 0.2:
            return darken(col, 0.11)
        if r < 0.3:
            return lighten(col, 0.08)
        return col
    if pattern == "feather":
        if j % 3 == 2 and (i + j // 3) % 2 == 0:
            return darken(col, 0.16)
        if j % 3 == 0 and i % 2 == 0:
            return lighten(col, 0.08)
        return col
    if pattern == "scales":
        if j % 2 == 0 and (i + j // 2) % 2 == 0:
            return darken(col, 0.14)
        if j % 2 == 1 and r < 0.35:
            return lighten(col, 0.07)
        return col
    if pattern == "smooth":
        if r < 0.08:
            return lighten(col, 0.06)
        return col
    if pattern == "hair":
        if i % 2 == 0:
            return darken(col, 0.1 + 0.06 * (j % 3 == 0))
        return lighten(col, 0.05) if r < 0.3 else col
    return col


class Model:
    def __init__(self, ident, tw=128, th=256):
        self.id = ident
        self.tw = tw
        self.th_max = th
        self.c = Canvas(tw, th)
        self.g = Canvas(tw, th)
        self.bones = []
        self.bmap = {}
        self.cx = 0
        self.cy = 0
        self.shelf = 0
        self.maxy = 0
        self.has_glow = False
        self.rnd = random.Random(sum(ord(ch) * (k + 1) for k, ch in enumerate(ident)))

    def bone(self, name, pivot, parent=None, rot=None):
        b = {"name": name, "pivot": [float(x) for x in pivot]}
        if parent:
            b["parent"] = parent
        if rot:
            b["rotation"] = [float(x) for x in rot]
        b["cubes"] = []
        self.bones.append(b)
        self.bmap[name] = b
        return name

    def alloc(self, w, h, d):
        w, h, d = int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))
        bw = max(1, 2 * (d + w))
        bh = max(1, d + h)
        if self.cx + bw > self.tw:
            self.cx = 0
            self.cy += self.shelf + 1
            self.shelf = 0
        u, v = self.cx, self.cy
        self.cx += bw + 1
        self.shelf = max(self.shelf, bh)
        self.maxy = max(self.maxy, v + bh)
        if self.maxy > self.th_max:
            raise SystemExit(f"{self.id}: textura estourou ({self.maxy} > {self.th_max})")
        return u, v

    def cube(self, bone, origin, size, col, col2=None, belly=None, top=None, pattern=None, glow=False, edge=None,
             inflate=0.0, shade_k=1.0, bottom=None):
        """Cubo com pintura base: gradiente vertical (col -> col2) nos lados, barriga, topo e padrao."""
        w, h, d = size
        u, v = self.alloc(w, h, d)
        cube = {"origin": [float(x) for x in origin], "size": [float(x) for x in size], "uv": [u, v]}
        if inflate:
            cube["inflate"] = inflate
        self.bmap[bone]["cubes"].append(cube)
        f = box_faces(u, v, int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d)))
        if glow:
            self.has_glow = True
        for face, (x, y, fw, fh) in f.items():
            for j in range(fh):
                for i in range(fw):
                    t = j / max(1, fh - 1)
                    base = col
                    if col2 is not None and face in SIDES:
                        base = mix(col, col2, t)
                    if top is not None and face == "top":
                        base = top
                    if bottom is not None and face == "bottom":
                        base = bottom
                    if belly is not None:
                        if face == "bottom":
                            base = belly
                        elif face in SIDES and fh >= 3 and j >= fh - max(1, fh // 3):
                            base = mix(base, belly, 0.7)
                    base = pattern_px(base, pattern, i, j, self.rnd)
                    base = shade(base, face, shade_k)
                    if edge is not None and face in SIDES and (i == 0 or i == fw - 1):
                        base = mix(base, edge, 0.5)
                    self.c.set(x + i, y + j, base)
                    if glow:
                        self.g.set(x + i, y + j, base)
        return f

    def px(self, rect, i, j, col, glow=False):
        x, y, w, h = rect
        if 0 <= i < w and 0 <= j < h:
            self.c.set(x + i, y + j, col)
            if glow:
                self.g.set(x + i, y + j, col)
                self.has_glow = True

    def clear(self, rect, i, j):
        x, y, w, h = rect
        if 0 <= i < w and 0 <= j < h:
            self.c.set(x + i, y + j, (0, 0, 0, 0))
            self.g.set(x + i, y + j, (0, 0, 0, 0))

    def hline(self, rect, j, col, glow=False, i0=0, i1=None):
        for i in range(i0, rect[2] if i1 is None else i1):
            self.px(rect, i, j, col, glow)

    def vline(self, rect, i, col, glow=False, j0=0, j1=None):
        for j in range(j0, rect[3] if j1 is None else j1):
            self.px(rect, i, j, col, glow)

    def save(self, visible=(3, 3, 1.0)):
        th = 16
        while th < self.maxy:
            th *= 2
        for cv in (self.c, self.g):
            cv.h = th
            cv.px = cv.px[:th]
        bones = []
        for b in self.bones:
            b = dict(b)
            if not b["cubes"]:
                del b["cubes"]
            bones.append(b)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.id, "texture_width": self.tw, "texture_height": th,
                            "visible_bounds_width": visible[0], "visible_bounds_height": visible[1],
                            "visible_bounds_offset": [0, visible[2], 0]},
            "bones": bones}]}
        save_json(os.path.join(A, "geo/entity", self.id + ".geo.json"), geo)
        self.c.save(os.path.join(A, "textures/entity", self.id + ".png"))
        gpath = os.path.join(A, "textures/entity", self.id + "_glowmask.png")
        if self.has_glow:
            self.g.save(gpath)
        elif os.path.exists(gpath):
            os.remove(gpath)


# =====================================================================================
#  Asas
# =====================================================================================
def feather_wing(m, bone, origin, size, side, out0, out1, base, dark, light, under, notch=True, glow_tips=None):
    """Asa de penas (1 px de espessura). Pinta cima e baixo iguais (dobrada, o lado de baixo fica para fora).
    out0/out1 = posicao da raiz/ponta deste pedaco ao longo da asa inteira (0 = ombro, 1 = ponta)."""
    f = m.cube(bone, origin, size, base, shade_k=0.4)
    for face in ("top", "bottom"):
        x, y, fw, fh = f[face]
        for j in range(fh):
            lead = j / max(1, fh - 1)          # 0 = borda de tras, 1 = borda de ataque
            for i in range(fw):
                a = i / max(1, fw - 1)
                if side < 0:
                    a = 1 - a
                out = out0 + (out1 - out0) * a  # 0 ombro .. 1 ponta
                col = base
                if lead > 0.72:                  # coberteiras (claras, escamadas)
                    col = light if (i + j) % 3 else mix(light, base, 0.4)
                elif lead > 0.45:
                    col = mix(base, light, 0.35) if (i // 2 + j) % 2 == 0 else base
                else:                             # remiges: hastes ao longo do comprimento
                    col = darken(base, 0.12) if i % 2 == 0 else base
                if out > 0.62 and lead <= 0.72:  # primarias escuras
                    col = mix(col, dark, min(1.0, (out - 0.62) * 2.2))
                if lead < 0.16:                   # pontas das penas
                    col = darken(col, 0.18)
                if face == "bottom":
                    col = mix(col, under, 0.35)
                col = lighten(col, 0.05) if face == "top" else col
                m.c.set(x + i, y + j, col)
                if glow_tips is not None and lead < 0.12 and out > 0.55 and i % 2 == 0:
                    m.g.set(x + i, y + j, glow_tips)
                    m.has_glow = True
            # recortes entre as penas na borda de tras
        if notch:
            for i in range(fw):
                if i % 2 == 1:
                    m.clear(f[face], i, 0)
    return f


def membrane_wing(m, bone, origin, size, side, out0, out1, memb, vein, bone_col, glow_col=None):
    """Membrana de morcego/dragao com nervuras e borda de tras recortada."""
    f = m.cube(bone, origin, size, memb, shade_k=0.4)
    for face in ("top", "bottom"):
        x, y, fw, fh = f[face]
        for j in range(fh):
            lead = j / max(1, fh - 1)
            for i in range(fw):
                a = i / max(1, fw - 1)
                if side < 0:
                    a = 1 - a
                out = out0 + (out1 - out0) * a
                col = mix(memb, darken(memb, 0.25), lead * 0.5 + out * 0.3)
                ii = i if side > 0 else fw - 1 - i
                if ii % 5 == 4:
                    col = vein
                if j == fh - 1:
                    col = bone_col
                if face == "bottom":
                    col = lighten(col, 0.06)
                m.c.set(x + i, y + j, col)
                if glow_col is not None and ii % 5 == 4 and lead > 0.3 and (j % 3 == 0):
                    m.g.set(x + i, y + j, glow_col)
                    m.has_glow = True
        # borda de tras em arcos entre as nervuras
        for i in range(fw):
            ii = i if side > 0 else fw - 1 - i
            k = ii % 5
            if k in (1, 2, 3):
                depth = 2 if k == 2 else 1
                for jj in range(depth):
                    m.clear(f[face], i, jj)
    return f


# =====================================================================================
#  Animacoes
# =====================================================================================
def r3(v):
    return [round(x, 3) for x in v]


def sampled(length, steps, fn):
    out = {}
    for i in range(steps + 1):
        t = length * i / steps
        out[str(round(t, 4))] = r3(fn(t))
    return out


def keys(*pairs):
    return {str(round(t, 4)): r3(v) for t, v in pairs}


def anim(L, bones, loop=True):
    return {"loop": loop, "animation_length": L, "bones": bones}


def save_anims(ident, anims):
    save_json(os.path.join(A, "animations/entity", ident + ".animation.json"), {
        "format_version": "1.8.0",
        "animations": {f"animation.{ident}.{k}": v for k, v in anims.items()}})


def rot(L, steps, fx=None, fy=None, fz=None):
    return {"rotation": sampled(L, steps, lambda t: [fx(t) if fx else 0, fy(t) if fy else 0, fz(t) if fz else 0])}


def const_rot(v):
    return {"rotation": keys((0, v))}


def S(L, amp, cycles=1.0, phase=0.0, base=0.0):
    return lambda t: base + amp * math.sin(TAU * cycles * t / L + phase)


def C(L, amp, cycles=1.0, phase=0.0, base=0.0):
    return lambda t: base + amp * math.cos(TAU * cycles * t / L + phase)


FOLD_L = [0, -90, -95]
FOLD_R = [0, 90, 95]
FOLD_TIP_L = [0, -10, -25]
FOLD_TIP_R = [0, 10, 25]


def folded_wings(extra=None):
    d = {"left_wing": const_rot(FOLD_L), "right_wing": const_rot(FOLD_R),
         "left_wing_tip": const_rot(FOLD_TIP_L), "right_wing_tip": const_rot(FOLD_TIP_R)}
    if extra:
        d.update(extra)
    return d


def breathing_wings(L, amp=2.0):
    """Asas dobradas que acompanham a respiracao."""
    return {"left_wing": rot(L, 8, fy=lambda t: FOLD_L[1], fz=S(L, amp, base=FOLD_L[2])),
            "right_wing": rot(L, 8, fy=lambda t: FOLD_R[1], fz=S(L, -amp, base=FOLD_R[2])),
            "left_wing_tip": const_rot(FOLD_TIP_L), "right_wing_tip": const_rot(FOLD_TIP_R)}


def flap_wings(F, up=48, down=26, tip=22, sweep=8, steps=16):
    """Batida de asas: t=0 asas no alto; F/2 embaixo. Pontas atrasadas."""
    mid = (down - up) / 2.0     # centro do ciclo (graus Z da asa esquerda; negativo = para cima)
    amp = (down + up) / 2.0
    left = lambda t: mid - amp * math.cos(TAU * t / F)       # noqa: E731  t=0 -> -up
    tipl = lambda t: -tip * math.cos(TAU * t / F - 0.9)       # noqa: E731
    swl = lambda t: sweep * math.sin(TAU * t / F)             # noqa: E731  para frente na descida
    return {"left_wing": rot(F, steps, fy=swl, fz=left),
            "right_wing": rot(F, steps, fy=lambda t: -swl(t), fz=lambda t: -left(t)),
            "left_wing_tip": rot(F, steps, fz=tipl),
            "right_wing_tip": rot(F, steps, fz=lambda t: -tipl(t))}


def glide_wings(G, dihedral=-6, wobble=3, tip=-4):
    return {"left_wing": rot(G, 8, fz=S(G, wobble, base=dihedral)),
            "right_wing": rot(G, 8, fz=S(G, -wobble, base=-dihedral)),
            "left_wing_tip": rot(G, 8, fz=S(G, wobble * 0.6, phase=-0.8, base=tip)),
            "right_wing_tip": rot(G, 8, fz=S(G, -wobble * 0.6, phase=-0.8, base=-tip))}


def merge(*dicts):
    out = {}
    for d in dicts:
        for k, v in d.items():
            out.setdefault(k, {}).update(v)
    return out


# =====================================================================================
#  1. GRIFO
# =====================================================================================
def griffin():
    m = Model("griffin")
    white = H("F4F0E6"); white2 = H("D9D0BE"); fur = H("C08F52"); fur2 = H("8E6433"); belly = H("E2C48E")
    wingc = H("8A5A2E"); wdark = H("4E3018"); wlight = H("C9A877"); wunder = H("E8DCC4")
    beak = H("F2C14E"); beak2 = H("B07A1A"); talon = H("E6AE3E"); claw = H("2A1E14"); eye = H("FFB300")
    tuftc = H("4A2E18")

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 15, 0], "root")
    t = m.cube("body", [-6, 10, -8], [12, 11, 20], fur2, col2=fur, belly=belly, top=fur2, pattern="fur")
    # listras escuras do leão no dorso
    for j in range(2, 20, 4):
        m.hline(t["top"], j, darken(fur2, 0.15), i0=3, i1=9)
    ch = m.cube("body", [-6.5, 9, -14], [13, 13, 7], white, col2=white2, pattern="feather")
    # transição penas -> pelo na traseira do peito
    for s in ("left", "right"):
        for j in range(13):
            m.px(ch[s], 6 if s == "left" else 0, j, mix(white2, fur, 0.5))

    m.bone("neck", [0, 18, -13], "body", rot=[22, 0, 0])
    m.cube("neck", [-4, 17, -17], [8, 9, 7], white, col2=white2, pattern="feather")
    m.cube("neck", [-5, 16, -16], [10, 4, 6], white2, col2=wlight, pattern="feather")   # gola de penas
    m.bone("look", [0, 25, -14], "neck")          # osso do olhar (GeckoLib gira este, não a cabeça)
    m.bone("head", [0, 25, -14], "look", rot=[-22, 0, 0])
    hd = m.cube("head", [-4.5, 24, -20], [9, 7, 8], white, col2=white2, pattern="feather")
    # olhos (frente da face esquerda = i 0; direita = ultima coluna) + sobrancelha escura
    for s, ii in (("left", 1), ("right", 6)):
        nx = ii + (1 if s == "left" else -1)
        m.px(hd[s], ii, 2, eye); m.px(hd[s], nx, 2, eye)
        m.px(hd[s], ii, 3, H("1A1208")); m.px(hd[s], nx, 3, darken(eye, 0.3))
        m.hline(hd[s], 1, H("5A4A36"), i0=max(0, min(ii, nx) - 1), i1=max(ii, nx) + 2)
    ub = m.cube("head", [-2.5, 25.5, -25], [5, 3.5, 5], beak, col2=beak2, top=lighten(beak, 0.15))
    m.cube("head", [-2, 23.5, -26], [4, 3, 2], beak2, col2=darken(beak2, 0.3))
    m.cube("head", [-2, 24, -24], [4, 1.5, 4], darken(beak, 0.15))
    for s in ("left", "right"):
        m.hline(ub[s], 2, darken(beak2, 0.35))
    # penacho: duas camadas de penas apontando para trás
    m.cube("head", [-3.5, 28.5, -13], [7, 2, 6], white2, col2=wlight, pattern="feather")
    m.cube("head", [-2.5, 29.5, -9], [5, 1.5, 5], wlight, col2=wingc, pattern="feather")

    # patas da frente (águia)
    for name, x, sgn in (("leg_fl", 4, 1), ("leg_fr", -4, -1)):
        m.bone(name, [x, 12, -10], "body")
        ox = 2 if sgn > 0 else -7
        m.cube(name, [ox, 7, -12.5], [5, 6, 5], white, col2=white2, pattern="feather")
        sh = m.cube(name, [ox + 0.5, 2, -11.5], [3, 6, 3], talon, col2=darken(talon, 0.15), pattern="scales")
        ft = m.cube(name, [ox, 0, -13.5], [4, 2, 5], talon, col2=darken(talon, 0.2))
        for i in range(4):
            m.px(ft["front"], i, 1, claw)
            m.px(ft["front"], i, 0, darken(talon, 0.25) if i % 2 else claw)
        m.hline(ft["top"], 4, claw, i0=0, i1=4)
    # patas de trás (leão)
    for name, x, sgn in (("leg_bl", 4, 1), ("leg_br", -4, -1)):
        m.bone(name, [x, 13, 8], "body")
        ox = 2 if sgn > 0 else -7
        m.cube(name, [ox, 5, 5], [5, 9, 7], fur2, col2=fur, pattern="fur")
        m.cube(name, [ox + 0.5, 1, 8.5], [4, 6, 3], fur, col2=darken(fur, 0.1), pattern="fur")
        pw = m.cube(name, [ox, 0, 7], [5, 2, 5], darken(fur, 0.12))
        for i in (0, 2, 4):
            m.px(pw["front"], i, 1, darken(fur2, 0.4))

    m.bone("tail1", [0, 19, 12], "body", rot=[-35, 0, 0])
    m.cube("tail1", [-1.5, 17.5, 12], [3, 3, 8], fur2, col2=fur, pattern="fur")
    m.bone("tail2", [0, 19, 20], "tail1", rot=[-15, 0, 0])
    m.cube("tail2", [-1, 18, 20], [2, 2, 8], fur2, col2=fur, pattern="fur")
    m.bone("tail_tuft", [0, 19, 28], "tail2")
    m.cube("tail_tuft", [-2, 17, 27], [4, 4, 5], tuftc, col2=darken(tuftc, 0.2), pattern="hair")

    # asas
    for side, sgn in (("left", 1), ("right", -1)):
        wb = side + "_wing"
        tb = side + "_wing_tip"
        m.bone(wb, [6 * sgn, 20, -6], "body")
        ox = 6 if sgn > 0 else -20
        feather_wing(m, wb, [ox, 19.5, -9], [14, 1, 12], sgn, 0.0, 0.45, wingc, wdark, wlight, wunder)
        m.bone(tb, [20 * sgn, 20, -6], wb)
        ox = 20 if sgn > 0 else -35
        feather_wing(m, tb, [ox, 19.5, -8], [15, 1, 12], sgn, 0.45, 0.9, wingc, wdark, wlight, wunder)
        ox = 33 if sgn > 0 else -39
        feather_wing(m, tb, [ox, 19.6, -6.5], [6, 1, 9], sgn, 0.85, 1.0, wingc, wdark, wlight, wunder)

    saddle(m, "body", 21, 6.5, z0=-4, depth=9, belly_y=9.5)
    m.save((6, 3, 1.0))

    # ---------------- animações ----------------
    L = 3.0
    idle = anim(L, merge(breathing_wings(L), {
        "body": {"position": sampled(L, 8, lambda t: [0, 0.25 * math.sin(TAU * t / L), 0])},
        "neck": rot(L, 8, fx=S(L, 2.0)),
        "head": rot(L, 12, fx=S(L, 3.0, phase=1.0), fy=S(L, 10.0, 0.5)),
        "tail1": rot(L, 8, fy=S(L, 8.0)), "tail2": rot(L, 8, fy=S(L, 10.0, phase=-0.8)),
        "tail_tuft": rot(L, 8, fy=S(L, 12.0, phase=-1.6)),
    }))
    W = 1.0
    walk = anim(W, merge(folded_wings(), {
        "leg_fl": rot(W, 8, fx=S(W, 28)), "leg_br": rot(W, 8, fx=S(W, 26)),
        "leg_fr": rot(W, 8, fx=S(W, -28)), "leg_bl": rot(W, 8, fx=S(W, -26)),
        "body": {"position": sampled(W, 8, lambda t: [0, 0.4 * abs(math.sin(TAU * t / W)), 0]),
                 "rotation": sampled(W, 8, lambda t: [0, 0, 1.5 * math.sin(TAU * t / W)])},
        "neck": rot(W, 8, fx=S(W, 4, 2.0)), "head": rot(W, 8, fx=S(W, -4, 2.0)),
        "tail1": rot(W, 8, fy=S(W, 10)), "tail2": rot(W, 8, fy=S(W, 12, phase=-0.8)),
    }))
    R = 0.6
    run = anim(R, merge(folded_wings({"left_wing": rot(R, 8, fy=lambda t: FOLD_L[1], fz=S(R, 6, base=FOLD_L[2] + 10)),
                                     "right_wing": rot(R, 8, fy=lambda t: FOLD_R[1], fz=S(R, -6, base=FOLD_R[2] - 10))}), {
        "leg_fl": rot(R, 8, fx=S(R, 45)), "leg_fr": rot(R, 8, fx=S(R, 45, phase=0.5)),
        "leg_bl": rot(R, 8, fx=S(R, -45, phase=0.9)), "leg_br": rot(R, 8, fx=S(R, -45, phase=1.4)),
        "body": {"rotation": sampled(R, 8, lambda t: [4 * math.sin(TAU * t / R), 0, 0]),
                 "position": sampled(R, 8, lambda t: [0, 0.8 * abs(math.sin(TAU * t / R)), 0])},
        "neck": rot(R, 8, fx=S(R, 6, base=8)), "head": rot(R, 8, fx=S(R, -6, base=-8)),
        "tail1": rot(R, 8, fx=S(R, 8, base=20)),
    }))
    F = 0.9
    tuck = {"leg_fl": const_rot([45, 0, 0]), "leg_fr": const_rot([45, 0, 0]),
            "leg_bl": const_rot([65, 0, 0]), "leg_br": const_rot([65, 0, 0]),
            "neck": const_rot([22, 0, 0]), "head": const_rot([-20, 0, 0])}
    fly = anim(F, merge(flap_wings(F), tuck, {
        "body": {"position": sampled(F, 16, lambda t: [0, -1.4 * math.cos(TAU * t / F), 0])},
        "tail1": rot(F, 8, fx=S(F, 5, base=30)), "tail2": rot(F, 8, fx=S(F, 6, phase=-0.8, base=10)),
    }))
    G = 2.4
    glide = anim(G, merge(glide_wings(G), tuck, {
        "tail1": rot(G, 8, fx=S(G, 2, base=32), fy=S(G, 4)), "tail2": rot(G, 8, fx=lambda t: 12, fy=S(G, 5, phase=-0.8)),
        "head": rot(G, 8, fx=lambda t: -20, fy=S(G, 8, 0.5)),
    }))
    Dv = 0.5
    dive = anim(Dv, merge({"left_wing": rot(Dv, 4, fy=S(Dv, 3, base=-38), fz=lambda t: -12),
                           "right_wing": rot(Dv, 4, fy=S(Dv, -3, base=38), fz=lambda t: 12),
                           "left_wing_tip": const_rot([0, -25, 6]), "right_wing_tip": const_rot([0, 25, -6])}, tuck, {
        "neck": const_rot([30, 0, 0]), "head": const_rot([-26, 0, 0]),
        "leg_fl": const_rot([-40, 0, 0]), "leg_fr": const_rot([-40, 0, 0]),   # garras à frente para agarrar
        "tail1": const_rot([35, 0, 0]),
    }))
    sit = anim(3.0, merge(breathing_wings(3.0, 1.5), {
        "body": {"position": keys((0, [0, -6.5, 0]))},
        "leg_fl": const_rot([-78, 0, 0]), "leg_fr": const_rot([-78, 0, 0]),
        "leg_bl": const_rot([-80, 0, 0]), "leg_br": const_rot([-80, 0, 0]),
        "neck": rot(3.0, 6, fx=S(3.0, 2, base=-6)), "head": rot(3.0, 8, fx=lambda t: 6, fy=S(3.0, 14, 0.5)),
        "tail1": const_rot([30, 35, 0]), "tail2": const_rot([0, 40, 0]), "tail_tuft": rot(3.0, 6, fy=S(3.0, 12)),
    }))
    attack = anim(0.6, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [-18, 0, 0]), (0.35, [8, 0, 0]), (0.6, [0, 0, 0]))},
        "leg_fl": {"rotation": keys((0, [0, 0, 0]), (0.2, [-80, 0, -10]), (0.35, [-20, 0, 0]), (0.6, [0, 0, 0]))},
        "leg_fr": {"rotation": keys((0, [0, 0, 0]), (0.25, [-85, 0, 10]), (0.4, [-15, 0, 0]), (0.6, [0, 0, 0]))},
        "neck": {"rotation": keys((0, [0, 0, 0]), (0.2, [-15, 0, 0]), (0.35, [25, 0, 0]), (0.6, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.35, [12, 0, 0]), (0.6, [0, 0, 0]))},
    }, loop=False)
    screech = anim(1.0, {
        "neck": {"rotation": keys((0, [0, 0, 0]), (0.25, [-30, 0, 0]), (0.75, [-30, 0, 0]), (1.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [-25, 0, 0]), (0.5, [-30, 8, 0]), (0.75, [-25, -8, 0]), (1.0, [0, 0, 0]))},
        "tail_tuft": {"scale": keys((0, [1, 1, 1]), (0.3, [1.3, 1.3, 1.3]), (1.0, [1, 1, 1]))},
    }, loop=False)
    save_anims("griffin", {"idle": idle, "walk": walk, "run": run, "fly": fly, "glide": glide, "dive": dive,
                           "sit": sit, "attack": attack, "screech": screech})


def saddle(m, parent, top_y, half_w, z0, depth, belly_y):
    """Sela de couro (osso 'saddle', escondido sem sela): assento, arções, cilha e estribos."""
    leather = H("6B3A1E"); leather2 = H("4A2412"); gold = H("E8C25A"); blanket = H("8E2A35")
    m.bone("saddle", [0, top_y, 0], parent)
    st = m.cube("saddle", [-half_w + 1, top_y, z0], [2 * half_w - 2, 1, depth], leather, col2=leather2)
    m.hline(st["top"], 0, gold); m.hline(st["top"], depth - 1, gold)
    m.cube("saddle", [-half_w + 0.5, top_y - 0.5, z0 - 1], [2 * half_w - 1, 1, depth + 2], blanket, col2=darken(blanket, 0.2),
           edge=gold)
    m.cube("saddle", [-half_w + 2, top_y + 1, z0 + depth - 1], [2 * half_w - 4, 2, 1], leather, col2=leather2)
    m.cube("saddle", [-1.5, top_y + 1, z0], [3, 2, 1], leather2, top=gold)
    m.cube("saddle", [-half_w - 0.5, belly_y, z0 + depth // 2 - 1], [2 * half_w + 1, top_y - belly_y + 0.5, 2], leather2,
           col2=darken(leather2, 0.2))
    for x in (half_w + 0.5, -half_w - 1.5):
        stp = m.cube("saddle", [x, belly_y + 2, z0 + depth // 2 - 0.5], [1, 4, 1], H("9A9AA6"), col2=H("5A5A66"))
        for s in SIDES:
            m.px(stp[s], 0, 3, gold)


# =====================================================================================
#  2. ARRAIA DO CÉU
# =====================================================================================
def sky_ray():
    m = Model("sky_ray")
    top = H("1F5F8A"); top2 = H("2E7FB0"); edge = H("4FB6D8"); bellyc = H("DDF6FF"); spot = H("B8FFFF")
    glowc = H("7FF5E6"); eye = H("E6FFFF"); dark = H("123A57")

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 6, 0], "root")
    b = m.cube("body", [-5, 4, -10], [10, 4, 20], top, col2=top2, top=top, bottom=bellyc, pattern="smooth")
    # constelação de pintas brilhantes no dorso
    rnd = random.Random(7)
    for _ in range(14):
        i, j = rnd.randrange(1, 9), rnd.randrange(1, 19)
        m.px(b["top"], i, j, spot, True)
    m.hline(b["top"], 10, glowc, True, i0=4, i1=6)
    for j in range(3, 17, 2):
        m.px(b["top"], 4 + (j // 2) % 2, j, lighten(top2, 0.25), True)
    hl = m.cube("body", [-4, 4.5, -13], [8, 3, 3], top2, col2=top, bottom=bellyc)
    for s, ii in (("left", 0), ("right", 2)):
        m.px(hl[s], ii, 0, eye, True); m.px(hl[s], ii, 1, glowc, True)
    m.hline(hl["front"], 2, darken(dark, 0.2), i0=2, i1=6)   # boca larga
    for name, x in (("horn_l", 2.5), ("horn_r", -4.5)):
        m.bone(name, [x + 1, 6, -13], "body")
        hr = m.cube(name, [x, 4.5, -17], [2, 2, 4], top2, col2=edge, bottom=bellyc)
        m.px(hr["front"], 0, 0, glowc, True); m.px(hr["front"], 1, 0, glowc, True)

    for side, sgn in (("left", 1), ("right", -1)):
        segs = (("_fin", [5, 5, -9], [10, 2, 16], 0.0, 0.4, 5), ("_fin_mid", [15, 5.5, -7], [9, 1, 11], 0.4, 0.75, 15),
                ("_fin_tip", [24, 5.5, -4], [6, 1, 5], 0.75, 1.0, 24))
        parent = "body"
        for suf, o, s, a0, a1, px_ in segs:
            name = side + suf
            m.bone(name, [px_ * sgn, 6, -2], parent)
            ox = o[0] if sgn > 0 else -(o[0] + s[0])
            f = m.cube(name, [ox, o[1], o[2]], s, top, top=top, bottom=bellyc, pattern="smooth")
            fw, fh = f["top"][2], f["top"][3]
            for j in range(fh):
                for i in range(fw):
                    a = i / max(1, fw - 1)
                    if sgn < 0:
                        a = 1 - a
                    out = a0 + (a1 - a0) * a
                    c = mix(top, edge, out * 0.8)
                    if j == fh - 1 or j == 0:
                        c = mix(c, glowc, 0.5)
                    m.c.set(f["top"][0] + i, f["top"][1] + j, c)
                    m.c.set(f["bottom"][0] + i, f["bottom"][1] + j, mix(bellyc, edge, out * 0.5))
            # linha brilhante na borda de ataque + pintas
            m.hline(f["top"], fh - 1, glowc, True)
            for k in range(2, fw - 1, 3):
                m.px(f["top"], k, fh // 2, spot, True)
            parent = name

    m.bone("tail1", [0, 6, 10], "body")
    m.cube("tail1", [-1, 5.25, 10], [2, 1.5, 12], top, col2=dark, bottom=bellyc)
    m.bone("tail2", [0, 6, 22], "tail1")
    t2 = m.cube("tail2", [-0.5, 5.5, 22], [1, 1, 12], dark, top=top)
    for s in ("top", "left", "right"):
        r = t2[s]
        for i in range(r[2]):
            for j in range(r[3]):
                # ponta brilhante (últimos 4 px)
                along = i if s == "top" else (i if s == "left" else r[2] - 1 - i)
                if (s == "top" and j >= r[3] - 4) or (s != "top" and along >= r[2] - 4):
                    m.px(r, i, j, glowc, True)
    m.px(t2["back"], 0, 0, H("FFFFFF"), True)

    saddle(m, "body", 8, 4.5, z0=-4, depth=8, belly_y=3.5)
    m.save((5, 1.5, 0.3))

    L = 2.6
    up = lambda amp, ph, base=0: S(L, amp, phase=ph, base=base)  # noqa: E731
    swim = anim(L, {
        "left_fin": rot(L, 16, fz=up(-16, 0)), "right_fin": rot(L, 16, fz=up(16, 0)),
        "left_fin_mid": rot(L, 16, fz=up(-20, -0.7)), "right_fin_mid": rot(L, 16, fz=up(20, -0.7)),
        "left_fin_tip": rot(L, 16, fz=up(-24, -1.4)), "right_fin_tip": rot(L, 16, fz=up(24, -1.4)),
        "body": {"position": sampled(L, 16, lambda t: [0, 0.8 * math.sin(TAU * t / L + 1.2), 0]),
                 "rotation": sampled(L, 16, lambda t: [2.5 * math.sin(TAU * t / L + 0.6), 0, 0])},
        "tail1": rot(L, 12, fx=S(L, 6, phase=-1.0), fy=S(L, 8, 0.5)),
        "tail2": rot(L, 12, fx=S(L, 9, phase=-1.8), fy=S(L, 12, 0.5, phase=-0.8)),
        "horn_l": rot(L, 8, fy=S(L, 6)), "horn_r": rot(L, 8, fy=S(L, -6)),
    })
    Lf = 1.2
    fast = anim(Lf, {
        "left_fin": rot(Lf, 16, fz=S(Lf, -26)), "right_fin": rot(Lf, 16, fz=S(Lf, 26)),
        "left_fin_mid": rot(Lf, 16, fz=S(Lf, -30, phase=-0.7)), "right_fin_mid": rot(Lf, 16, fz=S(Lf, 30, phase=-0.7)),
        "left_fin_tip": rot(Lf, 16, fz=S(Lf, -34, phase=-1.4)), "right_fin_tip": rot(Lf, 16, fz=S(Lf, 34, phase=-1.4)),
        "body": {"position": sampled(Lf, 16, lambda t: [0, 1.2 * math.sin(TAU * t / Lf + 1.2), 0])},
        "tail1": rot(Lf, 12, fx=S(Lf, 4, phase=-1.0)), "tail2": rot(Lf, 12, fx=S(Lf, 7, phase=-1.8)),
        "horn_l": const_rot([0, -12, 0]), "horn_r": const_rot([0, 12, 0]),
    })
    Lr = 4.0
    rest = anim(Lr, {
        "left_fin": rot(Lr, 12, fz=S(Lr, -5)), "right_fin": rot(Lr, 12, fz=S(Lr, 5)),
        "left_fin_mid": rot(Lr, 12, fz=S(Lr, -7, phase=-0.7)), "right_fin_mid": rot(Lr, 12, fz=S(Lr, 7, phase=-0.7)),
        "left_fin_tip": rot(Lr, 12, fz=S(Lr, -9, phase=-1.4)), "right_fin_tip": rot(Lr, 12, fz=S(Lr, 9, phase=-1.4)),
        "body": {"position": sampled(Lr, 8, lambda t: [0, 0.5 * math.sin(TAU * t / Lr), 0])},
        "tail1": rot(Lr, 8, fy=S(Lr, 10)), "tail2": rot(Lr, 8, fy=S(Lr, 14, phase=-0.9)),
        "horn_l": rot(Lr, 8, fy=S(Lr, 8)), "horn_r": rot(Lr, 8, fy=S(Lr, -8)),
    })
    surge = anim(0.8, {
        "left_fin": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, 0, -55]), (0.45, [0, 0, 40]), (0.8, [0, 0, 0]))},
        "right_fin": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, 0, 55]), (0.45, [0, 0, -40]), (0.8, [0, 0, 0]))},
        "left_fin_mid": {"rotation": keys((0, [0, 0, 0]), (0.25, [0, 0, -40]), (0.5, [0, 0, 35]), (0.8, [0, 0, 0]))},
        "right_fin_mid": {"rotation": keys((0, [0, 0, 0]), (0.25, [0, 0, 40]), (0.5, [0, 0, -35]), (0.8, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [-8, 0, 0]), (0.5, [6, 0, 0]), (0.8, [0, 0, 0]))},
        "tail2": {"rotation": keys((0, [0, 0, 0]), (0.3, [25, 0, 0]), (0.8, [0, 0, 0]))},
    }, loop=False)
    save_anims("sky_ray", {"swim": swim, "swim_fast": fast, "rest": rest, "surge": surge})


# =====================================================================================
#  3. PÉGASO
# =====================================================================================
def pegasus():
    m = Model("pegasus")
    coat = H("F6F6FA"); coat2 = H("D6DEEF"); mane = H("BFD7FF"); mane2 = H("8FB4F0"); hoof = H("E8C25A")
    wingc = H("F4F2EC"); wdark = H("D8C690"); wlight = H("FFFFFF"); wunder = H("E6ECF8"); eye = H("7FD4FF")
    nose = H("C9B8C8")

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 15, 0], "root")
    m.cube("body", [-5, 11, -10], [10, 10, 22], coat, col2=coat2, belly=coat2, pattern="smooth")

    m.bone("neck", [0, 18, -8], "body", rot=[30, 0, 0])
    m.cube("neck", [-2.5, 16, -11], [5, 12, 6], coat, col2=coat2, pattern="smooth")
    mn = m.cube("neck", [-1, 17, -5], [2, 14, 2], mane, col2=mane2, pattern="hair", glow=False)
    for j in range(0, 14, 3):
        m.px(mn["left"], 0, j, lighten(mane, 0.4), True); m.px(mn["right"], 1, j, lighten(mane, 0.4), True)
    m.bone("look", [0, 27, -8], "neck")
    m.bone("head", [0, 27, -8], "look")
    hd = m.cube("head", [-3, 25, -15], [6, 6, 8], coat, col2=coat2, pattern="smooth")
    for s, ii in (("left", 1), ("right", 6)):
        m.px(hd[s], ii, 2, eye, True); m.px(hd[s], ii + (1 if s == "left" else -1), 2, lighten(eye, 0.4), True)
        m.px(hd[s], ii, 3, darken(eye, 0.5))
    mz = m.cube("head", [-2, 25, -20], [4, 5, 5], coat, col2=coat2)
    m.px(mz["front"], 0, 2, nose); m.px(mz["front"], 3, 2, nose)
    m.hline(mz["front"], 4, darken(coat2, 0.15))
    for x in (-2.5, 1):
        m.cube("head", [x, 31, -9.5], [1.5, 3, 1.5], coat, col2=coat2)
    m.cube("head", [-1, 30, -14], [2, 2, 5], mane, col2=mane2, pattern="hair")

    for name, x, z in (("leg_fl", 3, -7), ("leg_fr", -3, -7), ("leg_bl", 3, 9), ("leg_br", -3, 9)):
        m.bone(name, [x, 11, z], "body")
        ox = x - 1.5
        lg = m.cube(name, [ox, 3, z - 1.5], [3, 9, 3], coat, col2=coat2, pattern="smooth")
        hf = m.cube(name, [ox - 0.25, 0, z - 1.75], [3.5, 3, 3.5], hoof, col2=darken(hoof, 0.25), top=coat2)
        for s in SIDES:
            m.hline(hf[s], 0, lighten(coat2, 0.2))

    m.bone("tail", [0, 20, 12], "body", rot=[30, 0, 0])
    tl = m.cube("tail", [-1.5, 8, 11], [3, 12, 3], mane, col2=mane2, pattern="hair")
    for s in SIDES:
        for j in range(1, 12, 3):
            m.px(tl[s], j % 3, j, lighten(mane, 0.45), True)

    for side, sgn in (("left", 1), ("right", -1)):
        wb, tb = side + "_wing", side + "_wing_tip"
        m.bone(wb, [5 * sgn, 20, -6], "body")
        ox = 5 if sgn > 0 else -17
        feather_wing(m, wb, [ox, 19.5, -8], [12, 1, 10], sgn, 0.0, 0.4, wingc, wdark, wlight, wunder)
        m.bone(tb, [17 * sgn, 20, -6], wb)
        ox = 17 if sgn > 0 else -30
        feather_wing(m, tb, [ox, 19.5, -7], [13, 1, 11], sgn, 0.4, 0.85, wingc, wdark, wlight, wunder,
                     glow_tips=H("FFF2C0"))
        ox = 28 if sgn > 0 else -34
        feather_wing(m, tb, [ox, 19.6, -5.5], [6, 1, 9], sgn, 0.8, 1.0, wingc, wdark, wlight, wunder,
                     glow_tips=H("FFF2C0"))

    saddle(m, "body", 21, 5.5, z0=-4, depth=9, belly_y=10.5)
    m.save((5, 3, 1.0))

    L = 3.0
    idle = anim(L, merge(breathing_wings(L, 1.5), {
        "neck": rot(L, 8, fx=S(L, 3)), "head": rot(L, 8, fx=S(L, 3, phase=1.0)),
        "tail": rot(L, 12, fz=S(L, 10, 1.0), fx=S(L, 4, 0.5)),
        "body": {"position": sampled(L, 8, lambda t: [0, 0.2 * math.sin(TAU * t / L), 0])},
    }))
    W = 1.1
    walk = anim(W, merge(folded_wings(), {
        "leg_fl": rot(W, 8, fx=S(W, 24)), "leg_br": rot(W, 8, fx=S(W, 22, phase=0.4)),
        "leg_fr": rot(W, 8, fx=S(W, -24)), "leg_bl": rot(W, 8, fx=S(W, -22, phase=0.4)),
        "neck": rot(W, 8, fx=S(W, 4, 2.0)), "tail": rot(W, 8, fz=S(W, 6)),
        "body": {"position": sampled(W, 8, lambda t: [0, 0.3 * abs(math.sin(TAU * t / W)), 0])},
    }))
    Gp = 0.55
    gallop = anim(Gp, merge(folded_wings({"left_wing": rot(Gp, 8, fy=lambda t: FOLD_L[1], fz=S(Gp, 8, base=FOLD_L[2] + 15)),
                                         "right_wing": rot(Gp, 8, fy=lambda t: FOLD_R[1], fz=S(Gp, -8, base=FOLD_R[2] - 15))}), {
        "leg_fl": rot(Gp, 8, fx=S(Gp, 50)), "leg_fr": rot(Gp, 8, fx=S(Gp, 50, phase=0.6)),
        "leg_bl": rot(Gp, 8, fx=S(Gp, -45, phase=1.2)), "leg_br": rot(Gp, 8, fx=S(Gp, -45, phase=1.8)),
        "body": {"rotation": sampled(Gp, 8, lambda t: [5 * math.sin(TAU * t / Gp), 0, 0]),
                 "position": sampled(Gp, 8, lambda t: [0, 1.0 * abs(math.sin(TAU * t / Gp)), 0])},
        "neck": rot(Gp, 8, fx=S(Gp, -8, base=-6)), "tail": rot(Gp, 8, fx=S(Gp, 10, base=25)),
    }))
    F = 0.8
    tuck = {"leg_fl": const_rot([-55, 0, 0]), "leg_fr": const_rot([-65, 0, 0]),
            "leg_bl": const_rot([45, 0, 0]), "leg_br": const_rot([55, 0, 0]),
            "neck": const_rot([-10, 0, 0])}
    fly = anim(F, merge(flap_wings(F, up=52, down=28), tuck, {
        "body": {"position": sampled(F, 16, lambda t: [0, -1.2 * math.cos(TAU * t / F), 0])},
        "tail": rot(F, 8, fx=S(F, 6, base=45)),
    }))
    G = 2.4
    glide = anim(G, merge(glide_wings(G), tuck, {
        "tail": rot(G, 8, fx=lambda t: 48, fz=S(G, 6)), "head": rot(G, 8, fy=S(G, 6, 0.5)),
    }))
    graze = anim(2.0, merge(breathing_wings(2.0, 1.0), {
        "neck": rot(2.0, 8, fx=S(2.0, 3, base=65)), "head": rot(2.0, 8, fx=S(2.0, 5, 2.0, base=5)),
        "tail": rot(2.0, 8, fz=S(2.0, 12)),
    }))
    rear = anim(1.0, merge(folded_wings({"left_wing": rot(1.0, 8, fy=lambda t: FOLD_L[1] + 50, fz=S(1.0, 15, base=-40)),
                                         "right_wing": rot(1.0, 8, fy=lambda t: FOLD_R[1] - 50, fz=S(1.0, -15, base=40)),
                                         "left_wing_tip": const_rot([0, 0, 0]), "right_wing_tip": const_rot([0, 0, 0])}), {
        "body": {"rotation": keys((0, [-32, 0, 0])), "position": keys((0, [0, 3, 3]))},
        "leg_bl": const_rot([30, 0, 0]), "leg_br": const_rot([30, 0, 0]),
        "leg_fl": rot(1.0, 8, fx=S(1.0, 30, 2.0, base=-50)), "leg_fr": rot(1.0, 8, fx=S(1.0, -30, 2.0, base=-50)),
        "neck": const_rot([-15, 0, 0]),
    }))
    bless = anim(1.2, {
        "neck": {"rotation": keys((0, [0, 0, 0]), (0.3, [-25, 0, 0]), (0.6, [10, 0, 0]), (1.2, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.7, [5, 0, 0]), (1.2, [0, 0, 0]))},
        "tail": {"rotation": keys((0, [0, 0, 0]), (0.4, [40, 0, 0]), (1.2, [0, 0, 0]))},
    }, loop=False)
    save_anims("pegasus", {"idle": idle, "walk": walk, "gallop": gallop, "fly": fly, "glide": glide, "graze": graze,
                           "rear": rear, "bless": bless})


# =====================================================================================
#  4. WYVERN
# =====================================================================================
def wyvern():
    m = Model("wyvern")
    sc = H("3A2550"); sc2 = H("1E1428"); bellyc = H("6B4A8A"); memb = H("4A1E66"); vein = H("2A0F3A")
    bonec = H("2B1A36"); horn = H("D8CDB8"); horn2 = H("8F8270"); eye = H("FF4FE0"); rune = H("B06CFF")
    spine = H("140C1C")

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 17, 0], "root")
    t = m.cube("body", [-6, 12, -9], [12, 11, 20], sc, col2=sc2, belly=bellyc, top=sc2, pattern="scales")
    for j in range(2, 19, 3):     # faixas de runas na barriga
        m.hline(t["bottom"], j, rune, True, i0=4, i1=8)
    for z in (-7, -3, 1, 5, 9):
        m.cube("body", [-0.5, 23, z], [1, 2, 2], spine, col2=sc)

    m.bone("neck", [0, 19, -9], "body", rot=[-25, 0, 0])
    n = m.cube("neck", [-3, 16, -19], [6, 6, 10], sc, col2=sc2, belly=bellyc, pattern="scales")
    m.hline(n["bottom"], 2, rune, True, i0=2, i1=4); m.hline(n["bottom"], 6, rune, True, i0=2, i1=4)
    for z in (-17, -13):
        m.cube("neck", [-0.5, 22, z], [1, 1.5, 2], spine)
    m.bone("look", [0, 19, -19], "neck")
    m.bone("head", [0, 19, -19], "look", rot=[20, 0, 0])
    hd = m.cube("head", [-4, 16, -27], [8, 7, 8], sc, col2=sc2, pattern="scales")
    for s, ii in (("left", 1), ("right", 6)):
        m.px(hd[s], ii, 2, eye, True); m.px(hd[s], ii + (1 if s == "left" else -1), 2, eye, True)
        m.px(hd[s], ii, 1, darken(sc2, 0.3))
    sn = m.cube("head", [-3, 16.5, -33], [6, 4, 6], sc, col2=sc2, pattern="scales")
    m.px(sn["front"], 1, 0, H("0A0610")); m.px(sn["front"], 4, 0, H("0A0610"))
    for s in ("left", "right"):
        for i in range(0, 6, 2):
            m.px(sn[s], i, 3, horn)    # dentes
    m.bone("jaw", [0, 16.5, -26], "head")
    jw = m.cube("jaw", [-2.75, 14, -33], [5.5, 2.5, 7], sc2, belly=bellyc)
    for s in ("left", "right"):
        for i in range(0, 7, 2):
            m.px(jw[s], i, 0, horn)
    m.hline(jw["top"], 3, rune, True)
    m.bone("horns", [0, 22, -21], "head", rot=[25, 0, 0])
    for x in (1.5, -3):
        m.cube("horns", [x, 21.5, -22], [1.5, 1.5, 8], horn, col2=horn2)
        m.cube("horns", [x + (0.5 if x > 0 else 0), 22.5, -16], [1, 1, 3], horn2)

    for name, x, sgn in (("leg_l", 5, 1), ("leg_r", -5, -1)):
        m.bone(name, [x, 15, 3], "body")
        ox = 3.5 if sgn > 0 else -8.5
        m.cube(name, [ox, 7, 0], [5, 10, 8], sc, col2=sc2, pattern="scales")
        m.cube(name, [ox + 0.5, 2, 4], [4, 6, 3], sc2, pattern="scales")
        ft = m.cube(name, [ox, 0, 0], [5, 2, 7], sc2)
        for i in range(0, 5, 2):
            m.px(ft["front"], i, 0, horn); m.px(ft["front"], i, 1, horn2)

    m.bone("tail1", [0, 18, 11], "body", rot=[-8, 0, 0])
    m.cube("tail1", [-3.5, 15, 11], [7, 6, 10], sc, col2=sc2, belly=bellyc, pattern="scales")
    m.bone("tail2", [0, 18, 21], "tail1", rot=[-4, 0, 0])
    m.cube("tail2", [-2.5, 15.5, 21], [5, 5, 10], sc, col2=sc2, belly=bellyc, pattern="scales")
    m.bone("tail3", [0, 18, 31], "tail2", rot=[4, 0, 0])
    m.cube("tail3", [-1.5, 16, 31], [3, 4, 10], sc, col2=sc2, pattern="scales")
    m.bone("tail4", [0, 18, 41], "tail3", rot=[4, 0, 0])
    m.cube("tail4", [-1, 16.5, 41], [2, 3, 7], sc2)
    sp = m.cube("tail4", [-3.5, 17, 46], [7, 1, 5], bonec, top=sc2)
    for i in range(7):
        m.px(sp["top"], i, 0, rune, True)
    m.px(sp["top"], 3, 2, rune, True); m.px(sp["top"], 3, 3, rune, True)
    for z in (13, 17, 23, 27, 33):
        par = "tail1" if z < 21 else ("tail2" if z < 31 else "tail3")
        y = 21 if z < 21 else (20.5 if z < 31 else 20)
        m.cube(par, [-0.5, y, z], [1, 1.5, 2], spine)

    for side, sgn in (("left", 1), ("right", -1)):
        wb, tb = side + "_wing", side + "_wing_tip"
        m.bone(wb, [6 * sgn, 22, -6], "body")
        ox = 6 if sgn > 0 else -22
        m.cube(wb, [ox, 21, -7.5], [16, 2, 2], bonec, col2=sc2)
        membrane_wing(m, wb, [ox, 21.5, -6], [16, 1, 14], sgn, 0.0, 0.47, memb, vein, bonec, glow_col=H("7A2EA8"))
        m.bone(tb, [22 * sgn, 22, -6], wb)
        ox = 22 if sgn > 0 else -40
        m.cube(tb, [ox, 21.25, -7], [18, 1.5, 1.5], bonec, col2=sc2)
        membrane_wing(m, tb, [ox, 21.5, -6], [18, 1, 13], sgn, 0.47, 1.0, memb, vein, bonec, glow_col=H("7A2EA8"))
        cx = 21 if sgn > 0 else -23
        m.cube(tb, [cx, 21, -9.5], [2, 2, 2.5], horn, col2=horn2)   # garra do pulso

    saddle(m, "body", 23, 6.5, z0=-4, depth=9, belly_y=11.5)
    m.save((7, 4, 1.2))

    L = 3.2
    idle = anim(L, merge(breathing_wings(L, 2.0), {
        "body": {"position": sampled(L, 8, lambda t: [0, 0.3 * math.sin(TAU * t / L), 0])},
        "neck": rot(L, 8, fx=S(L, 3), fy=S(L, 6, 0.5)), "head": rot(L, 8, fx=S(L, 3, phase=1.0)),
        "jaw": rot(L, 8, fx=lambda t: 2 + 2 * max(0.0, math.sin(TAU * t / L))),
        "tail1": rot(L, 8, fy=S(L, 6)), "tail2": rot(L, 8, fy=S(L, 8, phase=-0.7)),
        "tail3": rot(L, 8, fy=S(L, 10, phase=-1.4)), "tail4": rot(L, 8, fy=S(L, 12, phase=-2.1)),
    }))
    W = 1.3
    walk = anim(W, merge(folded_wings({"left_wing": rot(W, 8, fy=lambda t: FOLD_L[1], fz=S(W, 4, base=FOLD_L[2])),
                                      "right_wing": rot(W, 8, fy=lambda t: FOLD_R[1], fz=S(W, -4, base=FOLD_R[2]))}), {
        "leg_l": rot(W, 8, fx=S(W, 30)), "leg_r": rot(W, 8, fx=S(W, -30)),
        "body": {"rotation": sampled(W, 8, lambda t: [0, 5 * math.sin(TAU * t / W), 3 * math.sin(TAU * t / W)]),
                 "position": sampled(W, 8, lambda t: [0, 0.6 * abs(math.sin(TAU * t / W)), 0])},
        "neck": rot(W, 8, fy=S(W, -6)), "head": rot(W, 8, fy=S(W, -4)),
        "tail1": rot(W, 8, fy=S(W, -8)), "tail2": rot(W, 8, fy=S(W, -10, phase=-0.7)),
        "tail3": rot(W, 8, fy=S(W, -12, phase=-1.4)), "tail4": rot(W, 8, fy=S(W, -14, phase=-2.1)),
    }))
    F = 1.2
    tuck = {"leg_l": const_rot([55, 0, 0]), "leg_r": const_rot([55, 0, 0]),
            "neck": const_rot([18, 0, 0]), "head": const_rot([-14, 0, 0])}
    fly = anim(F, merge(flap_wings(F, up=50, down=30, tip=26), tuck, {
        "body": {"position": sampled(F, 16, lambda t: [0, -1.6 * math.cos(TAU * t / F), 0])},
        "tail1": rot(F, 8, fx=S(F, 4, base=8)), "tail2": rot(F, 8, fx=S(F, 5, phase=-0.8, base=4)),
        "tail3": rot(F, 8, fx=S(F, 6, phase=-1.6)), "tail4": rot(F, 8, fx=S(F, 8, phase=-2.4)),
    }))
    G = 2.8
    glide = anim(G, merge(glide_wings(G, dihedral=-4, wobble=2.5), tuck, {
        "tail1": rot(G, 8, fx=lambda t: 8, fy=S(G, 4)), "tail2": rot(G, 8, fy=S(G, 6, phase=-0.8)),
        "tail3": rot(G, 8, fy=S(G, 8, phase=-1.6)), "tail4": rot(G, 8, fy=S(G, 10, phase=-2.4)),
    }))
    sit = anim(3.2, merge(breathing_wings(3.2, 1.5), {
        "body": {"position": keys((0, [0, -7, 0]))},
        "leg_l": const_rot([-70, 0, 0]), "leg_r": const_rot([-70, 0, 0]),
        "neck": rot(3.2, 6, fx=S(3.2, 2, base=12)), "head": rot(3.2, 8, fx=lambda t: -8, fy=S(3.2, 12, 0.5)),
        "tail1": const_rot([10, 25, 0]), "tail2": const_rot([0, 30, 0]), "tail3": const_rot([0, 30, 0]),
        "tail4": rot(3.2, 6, fy=S(3.2, 15, base=25)),
    }))
    attack = anim(0.7, {
        "neck": {"rotation": keys((0, [0, 0, 0]), (0.25, [-20, 0, 0]), (0.4, [25, 0, 0]), (0.7, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [-10, 0, 0]), (0.4, [10, 0, 0]), (0.7, [0, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.25, [40, 0, 0]), (0.42, [0, 0, 0]), (0.7, [0, 0, 0]))},
    }, loop=False)
    breath = anim(2.0, {
        "neck": {"rotation": keys((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.5, [12, 0, 0]), (1.7, [12, 0, 0]), (2.0, [0, 0, 0]))},
        "head": {"rotation": sampled(2.0, 16, lambda t: [8 if 0.5 < t < 1.7 else 0, 4 * math.sin(t * 9) if 0.5 < t < 1.7 else 0, 0])},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [15, 0, 0]), (0.5, [42, 0, 0]), (1.7, [42, 0, 0]), (2.0, [0, 0, 0]))},
    }, loop=False)
    roar = anim(1.5, {
        "neck": {"rotation": keys((0, [0, 0, 0]), (0.4, [-35, 0, 0]), (1.1, [-35, 0, 0]), (1.5, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-20, 0, 0]), (1.1, [-20, 0, 0]), (1.5, [0, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.4, [45, 0, 0]), (1.1, [45, 0, 0]), (1.5, [0, 0, 0]))},
    }, loop=False)
    save_anims("wyvern", {"idle": idle, "walk": walk, "fly": fly, "glide": glide, "sit": sit, "attack": attack,
                          "breath": breath, "roar": roar})


# =====================================================================================
#  Item: Apito de Montaria
# =====================================================================================
def whistle():
    # Apito de prata em diagonal (bocal dourado no alto), cordão vermelho e uma pena de grifo pendurada
    rows = [
        "................",
        "...........ooo..",
        "..........oGGgo.",
        ".........oHSSGo.",
        "........oHSSso..",
        ".......oHSSso...",
        "......oHSSso....",
        "....ooHSSso.....",
        "...oHHSSSso.....",
        "..oHHSSDDso.....",
        "..oHSSSDDso.....",
        "..oSSSSSsso.....",
        "..oSSsssso......",
        "..RosssoF.......",
        ".R..ooooFFf.....",
        "..RRRRRRRFf.....",
    ]
    pal = {"o": H("1E1A24"), "S": H("C9CCD6"), "s": H("8A8E9C"), "H": H("EEF0F6"), "D": H("3A3D48"),
           "G": H("E8C25A"), "g": H("B08A2A"), "R": H("8E2A35"), "F": H("F2EEE6"), "f": H("C08F52")}
    c = Canvas(16, 16)
    c.from_rows(rows, pal)
    c.save(os.path.join(A, "textures/item/mount_whistle.png"))
    item_model(A, "mount_whistle")


# =====================================================================================
#  Dados: ovos, loot, receita, spawns, tags, traduções
# =====================================================================================
MOUNTS = ["griffin", "sky_ray", "pegasus", "wyvern"]


def item_entry(name, cmin=1, cmax=1, looting=None, chance=None, player_kill=False):
    funcs = []
    if (cmin, cmax) != (1, 1):
        funcs.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": cmin, "max": cmax}})
    if looting:
        funcs.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                      "count": {"type": "minecraft:uniform", "min": 0, "max": looting}})
    pool = {"rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": name, **({"functions": funcs} if funcs else {})}]}
    conds = []
    if chance is not None:
        conds.append({"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                      "unenchanted_chance": chance,
                      "enchanted_chance": {"type": "minecraft:linear", "base": min(1.0, chance + 0.05), "per_level_above_first": 0.05}})
    if player_kill:
        conds.append({"condition": "minecraft:killed_by_player"})
    if conds:
        pool["conditions"] = conds
    return pool


ESS = lambda e: f"{NS}:essence_{e}"  # noqa: E731

LOOT = {
    "griffin": [item_entry("minecraft:feather", 2, 5, 2), item_entry("minecraft:leather", 0, 2, 1),
                item_entry(ESS("wind"), 1, 1, chance=0.35)],
    "sky_ray": [item_entry(ESS("wind"), 1, 2, 1), item_entry("minecraft:phantom_membrane", 1, 2, 1),
                item_entry("minecraft:prismarine_crystals", 0, 3, 1)],
    "pegasus": [item_entry("minecraft:leather", 0, 2, 1), item_entry("minecraft:feather", 1, 3, 1),
                item_entry(ESS("light"), 1, 1, chance=0.5)],
    "wyvern": [item_entry(ESS("shadow"), 1, 2, 1), item_entry("minecraft:phantom_membrane", 1, 3, 1),
               item_entry("minecraft:bone", 1, 3, 1), item_entry("minecraft:echo_shard", 1, 1, chance=0.08, player_kill=True)],
}

SPAWNS = [
    # (arquivo, entidade, biomas, peso, min, max)
    ("mounts_griffin_peaks", "griffin", "#minecraft:is_mountain", 5, 1, 2),
    ("mounts_griffin_windswept", "griffin", ["minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                                             "minecraft:windswept_forest"], 3, 1, 1),
    ("mounts_sky_ray", "sky_ray", f"#{NS}:is_elemental_plane", 10, 1, 3),
    ("mounts_pegasus", "pegasus", f"#{NS}:is_celestial", 10, 2, 4),
    ("mounts_wyvern", "wyvern", f"#{NS}:is_abyss", 6, 1, 1),
]

NAMES = {
    "griffin": ("Grifo", "Griffin"),
    "sky_ray": ("Arraia do Céu", "Sky Ray"),
    "pegasus": ("Pégaso", "Pegasus"),
    "wyvern": ("Wyvern", "Wyvern"),
}

EGG_PT = {"griffin": "Ovo Gerador de Grifo", "sky_ray": "Ovo Gerador de Arraia do Céu",
          "pegasus": "Ovo Gerador de Pégaso", "wyvern": "Ovo Gerador de Wyvern"}

EXTRA_LANG = {
    "item.isekai_mahou.mount_whistle": ("Apito de Montaria", "Mount Whistle"),
    "item.isekai_mahou.mount_whistle.desc": ("Chama as suas montarias voadoras num raio de 128 blocos.",
                                             "Calls your flying mounts within 128 blocks."),
    "key.isekai_mahou.mount_ability": ("Habilidade da montaria", "Mount ability"),
    "ability.isekai_mahou.griffin_dive": ("Investida", "Dive Strike"),
    "ability.isekai_mahou.sky_ray_current": ("Corrente Celeste", "Sky Current"),
    "ability.isekai_mahou.pegasus_gallop": ("Galope Celestial", "Celestial Gallop"),
    "ability.isekai_mahou.wyvern_breath": ("Sopro Sombrio", "Shadow Breath"),
    "hud.isekai_mahou.mount_ability": ("[%s] %s", "[%s] %s"),
    "hud.isekai_mahou.mount_ready": ("Pronta!", "Ready!"),
    "hud.isekai_mahou.mount_cooldown": ("Recarregando: %s s", "Recharging: %s s"),
    "hud.isekai_mahou.mount_altitude": ("Altura: %s m", "Altitude: %s m"),
    "hud.isekai_mahou.mount_altitude_high": ("Altura: +64 m", "Altitude: 64+ m"),
    "message.isekai_mahou.mount_controls": ("[%1$s] subir · [%2$s] descer · [%3$s] %4$s",
                                            "[%1$s] ascend · [%2$s] descend · [%3$s] %4$s"),
    "message.isekai_mahou.mount_tamed": ("%s agora é sua companheira! Coloque uma sela para montar.",
                                         "%s is now your companion! Put a saddle on it to ride."),
    "message.isekai_mahou.mount_saddled": ("%s está selada. Clique com a mão vazia para montar.",
                                           "%s is saddled. Use an empty hand to ride."),
    "message.isekai_mahou.mount_sit": ("%s vai esperar aqui.", "%s will wait here."),
    "message.isekai_mahou.mount_follow": ("%s vai seguir você.", "%s will follow you."),
    "message.isekai_mahou.mount_not_owner": ("Esta montaria pertence a outra pessoa.", "This mount belongs to someone else."),
    "message.isekai_mahou.mount_ability_cooldown": ("%s: recarregando (%s s)", "%s: recharging (%s s)"),
    "message.isekai_mahou.griffin_angry": ("O Grifo está irritado demais para aceitar comida.",
                                           "The Griffin is too angry to take food."),
    "message.isekai_mahou.pegasus_distrust": ("O Pégaso desconfia de você. Ofereça-lhe uma Maçã Dourada.",
                                              "The Pegasus doesn't trust you. Offer it a Golden Apple."),
    "message.isekai_mahou.pegasus_trust": ("O Pégaso aceita o presente. Confiança: %s%%",
                                           "The Pegasus accepts the gift. Trust: %s%%"),
    "message.isekai_mahou.wyvern_calmed": ("A Wyvern devora a carne podre e se acalma... ofereça-lhe uma Essência Sombria.",
                                           "The Wyvern devours the rotten flesh and calms down... offer it a Shadow Essence."),
    "message.isekai_mahou.wyvern_needs_calm": ("A Wyvern está agitada demais. Acalme-a primeiro com carne podre.",
                                               "The Wyvern is too agitated. Calm it with rotten flesh first."),
    "message.isekai_mahou.wyvern_distrust": ("A Wyvern absorve a essência, mas ainda desconfia de você.",
                                             "The Wyvern absorbs the essence, but still distrusts you."),
    "message.isekai_mahou.whistle_none": ("Nenhuma montaria sua ouviu o apito.", "None of your mounts heard the whistle."),
    "message.isekai_mahou.whistle_called": ("%s montaria(s) ouviram o seu chamado!", "%s mount(s) heard your call!"),
}


def data():
    for c in MOUNTS:
        save_json(os.path.join(A, "models/item", c + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
        save_json(os.path.join(D, "loot_table/entities", c + ".json"), {
            "type": "minecraft:entity", "pools": LOOT[c], "random_sequence": f"{NS}:entities/{c}"})

    bm = os.path.join(D, "neoforge/biome_modifier")
    for fname, ent, biomes, w, mn, mx in SPAWNS:
        save_json(os.path.join(bm, fname + ".json"), {
            "type": "neoforge:add_spawns", "biomes": biomes,
            "spawners": [{"type": f"{NS}:{ent}", "weight": w, "minCount": mn, "maxCount": mx}]})

    save_json(os.path.join(D, "recipe/mount_whistle.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" G", "IF", "S "],
        "key": {"G": {"item": "minecraft:gold_nugget"}, "I": {"item": "minecraft:iron_ingot"},
                "F": {"item": "minecraft:feather"}, "S": {"item": "minecraft:string"}},
        "result": {"id": f"{NS}:mount_whistle", "count": 1}})

    def tag(path, ids):
        save_json(os.path.join(MERGE, "data/minecraft/tags/entity_type", path + ".json"),
                  {"replace": False, "values": [f"{NS}:{i}" for i in ids]})
    tag("fall_damage_immune", MOUNTS)
    tag("dismounts_underwater", ["griffin", "pegasus", "wyvern"])

    pt, en = {}, {}
    for c in MOUNTS:
        p, e = NAMES[c]
        pt[f"entity.{NS}.{c}"] = p
        en[f"entity.{NS}.{c}"] = e
        pt[f"item.{NS}.{c}_spawn_egg"] = EGG_PT[c]
        en[f"item.{NS}.{c}_spawn_egg"] = f"{e} Spawn Egg"
    for k, (p, e) in EXTRA_LANG.items():
        pt[k] = p
        en[k] = e
    save_json(os.path.join(LANG, "pt_br.json"), pt)
    save_json(os.path.join(LANG, "en_us.json"), en)


if __name__ == "__main__":
    griffin()
    sky_ray()
    pegasus()
    wyvern()
    whistle()
    data()
    print("mounts: assets gerados")
