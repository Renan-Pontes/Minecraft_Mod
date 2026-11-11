"""
Modelos GeckoLib do modulo class_steelball (usado por tools/gen/class_steelball.py — rode aquele).

  - entidades: bola de aco (aco/ouro), unha giratoria (rosa/ouro), Turbilhao Giratorio (aco/ouro), espiral da Rotacao
    Infinita e o Mestre Rotari (humanoide com chapeu de aba, sobretudo, cachecol e barba; 7 animacoes);
  - roupa do Steel Ball em 4 graus (mesmos ossos de armadura das roupas de classe + outfitCape e hatBand, que o
    SbOutfitRenderer anima), glowmask nos graus III e IV e camadas vanilla de reserva.
Convencoes do .geo.json (Bedrock): frente = -z, lado DIREITO = -x, y para cima.
"""
import math
import os

import outfits as O  # montador de modelos/atlas (mesmo das roupas de classe)
import class_steelball as S
from pixelart import Canvas, lighten, darken, mix

A, H, pal, write_json = S.A, S.H, S.pal, S.write_json
STEEL, GOLDP, NAILP = S.STEEL, S.GOLDP, S.NAILP
fc = O.fc
OMIT = O.OMIT


# =====================================================================================
#  Montagem de entidades (mesmo conjunto de regioes = mesmo UV: da para pintar a mesma geometria com outra paleta)
# =====================================================================================
def build_entity(m, mats, tex=None, bounds=(4, 3)):
    keys_ = {}
    for b in m.bones:
        for c in b["cubes"]:
            for f, mat in c["faces"].items():
                if mat is None:
                    continue
                fw, fh = O.face_dims(f, c["size"])
                k = O.region_key(mat, fw, fh)
                if k[0] not in mats:
                    raise KeyError(f"[{m.name}] material desconhecido: {k[0]}")
                keys_[k] = (k, k[1], k[2])
    pos, W, Hh = None, 64, 64
    for cand in ((32, 32), (64, 32), (64, 64), (128, 64), (128, 128), (256, 128), (256, 256)):
        pos = O.pack(list(keys_.values()), *cand)
        if pos is not None:
            W, Hh = cand
            break
    if pos is None:
        raise RuntimeError(f"[{m.name}] atlas nao coube")
    name = tex or m.name
    print(f"  {name}: {len(keys_)} regioes, atlas {W}x{Hh}")
    cv, gl = Canvas(W, Hh), Canvas(W, Hh)
    for k, (x, y) in sorted(pos.items(), key=lambda kv: str(kv[0])):
        seed = sum(ord(ch) * (i + 7) for i, ch in enumerate(f"{m.name}:{k}"))
        mats[k[0]](O.Region(cv, gl, x, y, k[1], k[2], seed))
    bones = []
    for b in m.bones:
        jb = {"name": b["name"]}
        if "parent" in b:
            jb["parent"] = b["parent"]
        jb["pivot"] = [O.r4(v) for v in b["pivot"]]
        if "rotation" in b:
            jb["rotation"] = [O.r4(v) for v in b["rotation"]]
        cubes = []
        for c in b["cubes"]:
            jc = {"origin": [O.r4(v) for v in c["origin"]], "size": [O.r4(v) for v in c["size"]]}
            if c["inflate"]:
                jc["inflate"] = O.r4(c["inflate"])
            if c["rotation"] and any(abs(v) > 1e-6 for v in c["rotation"]):
                jc["pivot"] = [O.r4(v) for v in (c["pivot"] or c["origin"])]
                jc["rotation"] = [O.r4(v) for v in c["rotation"]]
            uv = {}
            for f in O.FACES:
                mat = c["faces"].get(f)
                if mat is None:
                    continue
                fw, fh = O.face_dims(f, c["size"])
                k = O.region_key(mat, fw, fh)
                x, y = pos[k]
                uv[f] = {"uv": [x, y], "uv_size": [k[1], k[2]]}
            if uv:
                jc["uv"] = uv
                cubes.append(jc)
        if cubes:
            jb["cubes"] = cubes
        bones.append(jb)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + m.name, "texture_width": W, "texture_height": Hh,
                        "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1],
                        "visible_bounds_offset": [0, bounds[1] / 2, 0]},
        "bones": bones}]}
    write_json(os.path.join(A, "geo/entity", m.name + ".geo.json"), geo)
    cv.save(os.path.join(A, "textures/entity", name + ".png"))
    gpath = os.path.join(A, "textures/entity", name + "_glowmask.png")
    if os.path.exists(gpath):
        os.remove(gpath)


def anim(name, anims):
    write_json(os.path.join(A, "animations/entity", name + ".animation.json"),
               {"format_version": "1.8.0", "animations": anims})


def keys(*pairs):
    """keys((0, [..]), (0.5, [..])) -> keyframes {"0.0": [..], "0.5": [..]}."""
    return {str(round(float(t), 4)): v for t, v in pairs}


# v1.0 — Tecnicas do Santo
TUSKP = pal("2E1450", "5A2E8E", "8E52D0", "B88AFF", "EED8FF")
SCAN = pal("0E3A4E", "1E6A8A", "3FA8D0", "7FE8FF", "E0FCFF")
WORM = pal("1A0830", "3A1460", "6A2EA8", "B45CFF", "F0D8FF")


def scan_metal(P, ring=True):
    """Bola Escaner: o aco da bola com luzes ciano em grade (brilham no escuro) e uma faixa de leitura."""
    base = sphere_metal(P, groove=False)

    def f(r):
        base(r)
        for j in range(r.h):
            for i in range(r.w):
                if (i + 2 * j) % 4 == 0 and 0 < i < r.w - 1 and 0 < j < r.h - 1:
                    r.glow(i, j, SCAN[3])
        if ring and r.h > 2:
            for i in range(r.w):
                r.glow(i, r.h // 2, SCAN[4] if i % 2 == 0 else SCAN[2])
    return f


# =====================================================================================
#  Materiais comuns
# =====================================================================================
def sphere_metal(P, groove=True, gold=False):
    """Face de esfera metalica: brilho em cima a esquerda, sombra embaixo, sulco da costura em diagonal."""
    def f(r):
        for j in range(r.h):
            for i in range(r.w):
                t = (i + j) / max(1, r.w + r.h - 2)
                c = lighten(P[3], 0.15) if t < 0.25 else (P[3] if t < 0.5 else (P[2] if t < 0.8 else P[1]))
                r.set(i, j, c)
        if groove:
            for i in range(r.w):
                j = int(round(i * (r.h - 1) / max(1, r.w - 1) * 0.6 + r.h * 0.2))
                r.set(i, j, P[0])
        r.set(0, 0, P[4])
        if r.w > 2:
            r.set(1, 0, P[4])
        if gold:
            r.set(r.w // 2, r.h // 2, lighten(P[4], 0.5))
        r.noise(0.03)
    return f


def translucent(P, alpha, core_bright=True):
    """Faixa translucida que brilha (centro claro, borda um pouco mais apagada)."""
    def f(r):
        for j in range(r.h):
            for i in range(r.w):
                edge = min(i, j, r.w - 1 - i, r.h - 1 - j)
                c = P[4] if (edge > 0 and core_bright) else P[3]
                r.set(i, j, (c[0], c[1], c[2], alpha if edge > 0 else int(alpha * 0.7)))
    return f


def spiral_px(r, cx, cy, rad, c, glow=True, turns=1.6):
    """Espiral de Arquimedes (o giro) desenhada em pixels."""
    pts = set()
    n = int(40 * turns)
    for k in range(n):
        t = k / max(1, n - 1)
        a = t * turns * 2 * math.pi
        rr = rad * t
        pts.add((int(round(cx + math.cos(a) * rr)), int(round(cy + math.sin(a) * rr))))
    for (i, j) in pts:
        (r.glow if glow else r.set)(i, j, c)


def golden_rect_px(r, x0, y0, w, h, c, glow=True):
    """Retangulo Dourado: moldura + divisoes em quadrados (cada vez menores) + a espiral nos cantos."""
    put = r.glow if glow else r.set
    for i in range(x0, x0 + w):
        put(i, y0, c)
        put(i, y0 + h - 1, c)
    for j in range(y0, y0 + h):
        put(x0, j, c)
        put(x0 + w - 1, j, c)
    # primeira divisao (quadrado h x h) e segunda (horizontal)
    sq = min(h, w - 1)
    for j in range(y0, y0 + h):
        put(x0 + sq - 1, j, c)
    if h > 4:
        for i in range(x0 + sq - 1, x0 + w):
            put(i, y0 + h // 2, c)
    # arco da espiral no quadrado maior
    for k in range(12):
        a = math.pi / 2 * k / 11
        i = int(round(x0 + sq - 1 - math.cos(a) * (sq - 2)))
        j = int(round(y0 + h - 1 - math.sin(a) * (h - 2)))
        put(i, j, lighten(c, 0.4))


# =====================================================================================
#  Bola, unha, turbilhao e espiral infinita
# =====================================================================================
def ball_entity():
    m = O.Model("steelball_ball")
    m.bone("ball", [0, 2.8, 0])
    m.box("ball", [-2.5, 0.3, -2.5], [5, 5, 5], fc(all="ball_core"))
    m.box("ball", [-3, 0.8, -2], [6, 4, 4], fc(all="ball"))
    m.box("ball", [-2, 0.8, -3], [4, 4, 6], fc(all="ball"))
    m.box("ball", [-2, -0.2, -2], [4, 6, 4], fc(all="ball"))
    for tex, P, gold in (("steelball_ball", STEEL, False), ("steelball_ball_gold", GOLDP, True)):
        build_entity(m, {"ball": sphere_metal(P, gold=gold), "ball_core": sphere_metal(P, groove=False)}, tex, (1, 1))
    build_entity(m, {"ball": scan_metal(STEEL), "ball_core": scan_metal(STEEL, ring=False)}, "steelball_scan_ball", (1, 1))
    anim("steelball_ball", {"animation.steelball_ball.spin": {
        "loop": True, "animation_length": 0.25,
        "bones": {"ball": {"rotation": keys((0, [0, 0, 0]), (0.125, [-180, 0, 0]), (0.25, [-360, 0, 0]))}}}})


def nail_entity():
    m = O.Model("steelball_nail")
    m.bone("nail", [0, 2, 0])
    # unha: lamina apontando para a frente (-z), raiz mais larga e ponta afiada
    m.box("nail", [-0.75, 1.6, -1.0], [1.5, 0.8, 3.0], fc(all="nail", n="nail_tip@1x1"))
    m.box("nail", [-0.5, 1.7, -2.6], [1.0, 0.6, 1.6], fc(all="nail_tip", s=OMIT))
    m.box("nail", [-0.25, 1.8, -3.5], [0.5, 0.4, 0.9], fc(all="nail_tip", s=OMIT))
    # rastro em cruz (duas aletas finas) que gira junto e desenha a espiral no ar
    m.box("nail", [-1.4, 1.95, 1.0], [2.8, 0.1, 1.6], fc(u="trail", d="trail", side=OMIT))
    m.box("nail", [-0.05, 0.6, 1.0], [0.1, 2.8, 1.6], fc(e="trail", w="trail", n=OMIT, s=OMIT, u=OMIT, d=OMIT))
    for tex, P in (("steelball_nail", NAILP), ("steelball_nail_gold", GOLDP), ("steelball_tusk_nail", TUSKP)):
        def nail(r, P=P):
            r.fill(P[3])
            r.row(0, P[4])
            r.row(-1, P[1])
            if r.w > 1:
                r.col(0, P[4])
            r.noise(0.03)

        def tip(r, P=P):
            r.fill(P[4])
            r.set(0, 0, lighten(P[4], 0.6))

        def trail(r, P=P):
            for j in range(r.h):
                for i in range(r.w):
                    a = int(160 * (1 - j / max(1, r.h)))
                    c = P[3]
                    r.set(i, j, (c[0], c[1], c[2], max(50, a)))
        build_entity(m, {"nail": nail, "nail_tip": tip, "trail": trail}, tex, (1, 1))
    anim("steelball_nail", {"animation.steelball_nail.spin": {
        "loop": True, "animation_length": 0.2,
        "bones": {"nail": {"rotation": keys((0, [0, 0, 0]), (0.1, [0, 0, 180]), (0.2, [0, 0, 360]))}}}})


def vortex_entity():
    m = O.Model("steelball_vortex")
    m.bone("root", [0, 0, 0])
    m.bone("arms", [0, 0, 0], parent="root")
    m.bone("rings", [0, 0, 0], parent="root")
    m.bone("core", [0, 3, 0], parent="root")
    flat = fc(u="arm", d="arm", side=OMIT)
    # 3 bracos em espiral (raio 6 -> 54 px = ~3,4 blocos na escala 1)
    for arm in range(3):
        for k in range(18):
            t = k / 17
            a = arm * 2 * math.pi / 3 + t * math.pi * 1.6
            rad = 6 + 48 * t
            w = 2.0 + 2.5 * t
            x, z = math.cos(a) * rad, math.sin(a) * rad
            m.box("arms", [x - w / 2, 0.25 + 0.02 * k, z - w / 2], [w, 0.1, w], flat)
    ring = fc(u="ring", d="ring", side=OMIT)
    for k in range(28):
        if k % 4 == 3:
            continue
        a = k * 2 * math.pi / 28
        m.box("rings", [math.cos(a) * 54 - 3, 0.12, math.sin(a) * 54 - 3], [6, 0.1, 6], ring)
    for k in range(14):
        a = k * 2 * math.pi / 14
        m.box("rings", [math.cos(a) * 22 - 2.2, 0.18, math.sin(a) * 22 - 2.2], [4.4, 0.1, 4.4], ring)
    # a bola cravada no centro (meio enterrada)
    m.box("core", [-3, 0, -3], [6, 6, 6], fc(all="ball_core"))
    m.box("core", [-3.6, 0.6, -2.4], [7.2, 4.8, 4.8], fc(all="ball"))
    m.box("core", [-2.4, 0.6, -3.6], [4.8, 4.8, 7.2], fc(all="ball"))
    for tex, P in (("steelball_vortex", STEEL), ("steelball_vortex_gold", GOLDP)):
        build_entity(m, {"arm": translucent(P, 170), "ring": translucent(P, 120, False),
                         "ball": sphere_metal(P), "ball_core": sphere_metal(P, groove=False)}, tex, (8, 2))
    anim("steelball_vortex", {"animation.steelball_vortex.spin": {
        "loop": True, "animation_length": 1.0,
        "bones": {
            "arms": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, -180, 0]), (1.0, [0, -360, 0]))},
            "rings": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 90, 0]), (1.0, [0, 180, 0])),
                      "scale": keys((0, [1, 1, 1]), (0.5, [1.06, 1, 1.06]), (1.0, [1, 1, 1]))},
            "core": {"rotation": keys((0, [0, 0, 0]), (0.25, [0, -360, 0]), (0.5, [0, -720, 0]),
                                      (0.75, [0, -1080, 0]), (1.0, [0, -1440, 0]))}}}})


def infinite_entity():
    m = O.Model("steelball_infinite_spin")
    m.bone("root", [0, 0, 0])
    m.bone("helix", [0, 0, 0], parent="root")
    m.bone("rings", [0, 13, 0], parent="root")
    tile = fc(all="gold")
    # 3 fios de espiral subindo pelo corpo do alvo (raio 11 -> 8 px, altura 0 -> 26 px)
    for strand in range(3):
        for k in range(16):
            t = k / 15
            a = strand * 2 * math.pi / 3 + t * math.pi * 3
            rad = 11 - 3 * t
            m.box("helix", [math.cos(a) * rad - 0.8, 26 * t, math.sin(a) * rad - 0.8], [1.6, 1.6, 1.6], tile)
    # aneis dourados (base e topo) e, no meio, os quadrados que crescem pela razao aurea
    flat = fc(u="ring", d="ring", side="ring_edge")
    for (y, rad, n) in ((0.4, 12.5, 18), (27.5, 9.5, 14)):
        for k in range(n):
            a = k * 2 * math.pi / n
            m.box("rings", [math.cos(a) * rad - 1.3, y, math.sin(a) * rad - 1.3], [2.6, 0.3, 2.6], flat)
    phi = 1.6180339887
    s = 1.2
    for k in range(5):
        a = k * math.pi / 2
        rr = 1.3 * phi ** k
        m.box("rings", [math.cos(a) * rr - s / 2, 13, math.sin(a) * rr - s / 2], [s, 0.3, s], fc(all="core"))
        s *= 1.25
    G = GOLDP
    build_entity(m, {"gold": translucent(G, 210), "ring": translucent(G, 150), "ring_edge": translucent(G, 200, False),
                     "core": translucent(pal("FFFFFF", "FFF6D0", "FFEFA0", "FFE680", "FFFFFF"), 230)},
                 "steelball_infinite_spin", (3, 3))
    anim("steelball_infinite_spin", {"animation.steelball_infinite_spin.spin": {
        "loop": True, "animation_length": 1.0,
        "bones": {
            "helix": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 180, 0]), (1.0, [0, 360, 0]))},
            "rings": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, -180, 0]), (1.0, [0, -360, 0])),
                      "scale": keys((0, [1, 1, 1]), (0.5, [1.08, 1, 1.08]), (1.0, [1, 1, 1]))}}}})


# =====================================================================================
#  v1.0 — Buraco Espiral (Ato 2 dourado / Ato 3 roxo) e Esfera Quebrantadora
# =====================================================================================
def spiral_hole_entity():
    """Disco espiral rente ao chao: 4 bracos que se enrolam ate o centro escuro (o buraco), aro e brilho."""
    m = O.Model("steelball_spiral_hole")
    m.bone("root", [0, 0, 0])
    m.bone("arms", [0, 0, 0], parent="root")
    m.bone("rim", [0, 0, 0], parent="root")
    flat = fc(u="arm", d="arm", side=OMIT)
    for arm in range(4):
        for k in range(12):
            t = k / 11
            a = arm * math.pi / 2 + t * math.pi * 1.8
            rad = 2 + 9 * t
            w = 1.2 + 1.4 * t
            m.box("arms", [math.cos(a) * rad - w / 2, 0.12 + 0.01 * k, math.sin(a) * rad - w / 2], [w, 0.1, w], flat)
    m.box("root", [-2.2, 0.05, -2.2], [4.4, 0.1, 4.4], fc(u="hole", d="hole", side=OMIT))
    ring = fc(u="rim", d="rim", side=OMIT)
    for k in range(20):
        a = k * 2 * math.pi / 20
        m.box("rim", [math.cos(a) * 12 - 1.4, 0.08, math.sin(a) * 12 - 1.4], [2.8, 0.1, 2.8], ring)

    def hole(P):
        def f(r):
            for j in range(r.h):
                for i in range(r.w):
                    d = ((i - (r.w - 1) / 2) ** 2 + (j - (r.h - 1) / 2) ** 2) ** 0.5
                    c = P[0] if d < r.w * 0.3 else P[1]
                    r.set(i, j, (c[0], c[1], c[2], 235))
        return f

    for tex, P in (("steelball_spiral_hole", GOLDP), ("steelball_wormhole", WORM)):
        build_entity(m, {"arm": translucent(P, 200), "rim": translucent(P, 140, False), "hole": hole(P)}, tex, (2, 1))
    anim("steelball_spiral_hole", {"animation.steelball_spiral_hole.spin": {
        "loop": True, "animation_length": 0.5,
        "bones": {
            "arms": {"rotation": keys((0, [0, 0, 0]), (0.25, [0, -180, 0]), (0.5, [0, -360, 0]))},
            "rim": {"rotation": keys((0, [0, 0, 0]), (0.25, [0, 90, 0]), (0.5, [0, 180, 0])),
                    "scale": keys((0, [1, 1, 1]), (0.25, [1.1, 1, 1.1]), (0.5, [1, 1, 1]))}}}})


def ball_breaker_entity():
    """O espectro da bola dourada: esfera translucida grande, nucleo claro e dois aneis que giram em eixos cruzados."""
    m = O.Model("steelball_ball_breaker")
    m.bone("root", [0, 0, 0])
    m.bone("sphere", [0, 8, 0], parent="root")
    m.bone("ring_a", [0, 8, 0], parent="root")
    m.bone("ring_b", [0, 8, 0], parent="root")
    m.box("sphere", [-3, 5, -3], [6, 6, 6], fc(all="core"))
    m.box("sphere", [-6, 4, -4], [12, 8, 8], fc(all="shell"))
    m.box("sphere", [-4, 4, -6], [8, 8, 12], fc(all="shell"))
    m.box("sphere", [-4, 2, -4], [8, 12, 8], fc(all="shell"))
    # sulco da costura (a bola de aco) e quatro rebites
    m.box("sphere", [-6.3, 7.6, -0.6], [12.6, 0.8, 1.2], fc(all="seam"))
    for (x, z) in ((-6.4, -2.0), (5.4, 1.0), (-2.0, -6.4), (1.0, 5.4)):
        m.box("sphere", [x, 9.5, z], [1.0, 1.0, 1.0], fc(all="seam"))
    flat = fc(u="ring", d="ring", side="ring")
    for k in range(22):
        a = k * 2 * math.pi / 22
        m.box("ring_a", [math.cos(a) * 9.5 - 0.8, 7.85, math.sin(a) * 9.5 - 0.8], [1.6, 0.3, 1.6], flat)
        m.box("ring_b", [math.cos(a) * 11 - 0.7, 8 + math.sin(a) * 11 - 0.7, -0.15], [1.4, 1.4, 0.3], fc(all="ring"))
    build_entity(m, {"core": translucent(pal("FFFFFF", "FFF6D0", "FFEFA0", "FFE680", "FFFFFF"), 230),
                     "shell": translucent(GOLDP, 110),
                     "seam": translucent(pal("5A3A04", "9A6A10", "C88A1E", "E8A82E", "FFD24A"), 220, False),
                     "ring": translucent(GOLDP, 190)}, "steelball_ball_breaker", (2, 2))
    anim("steelball_ball_breaker", {"animation.steelball_ball_breaker.spin": {
        "loop": True, "animation_length": 2.0,
        "bones": {
            "sphere": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 180, 0]), (2.0, [0, 360, 0])),
                       "scale": keys((0, [1, 1, 1]), (1.0, [1.06, 1.06, 1.06]), (2.0, [1, 1, 1]))},
            "ring_a": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, -360, 0]), (2.0, [0, -720, 0]))},
            "ring_b": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 180, 0]), (2.0, [0, 360, 0]))}}}})


# =====================================================================================
#  Mestre Rotari (NPC): velho cavaleiro de chapeu de aba, sobretudo verde-musgo, cachecol e barba grisalha
# =====================================================================================
SKIN = pal("6A4430", "8E5E42", "B07C5A", "C99872", "E0B48E")
BEARD = pal("6E6A66", "8E8A84", "B2AEA6", "D4D0C8", "F0ECE6")
MOSS = pal("1A2418", "26341F", "34462A", "4A603A", "64804E")
HATP = pal("1E1610", "2E2218", "403024", "564232", "70584A")
SCARFP = pal("4A1414", "6E1E1E", "962A2A", "BC3E3A", "DC6A5E")
LEATH = pal("1E140C", "2E2014", "422E1C", "5A4028", "765838")
SHIRT = pal("8A8270", "AAA08A", "C8BEA4", "E0D8BE", "F2ECD8")


def mentor_mats():
    M = {}

    def face(r):
        r.fill(SKIN[3])
        r.vshade(0.06, 0.12)
        cx = r.w // 2
        # olhos (claros, aco), sobrancelhas grossas, bigode e barba cobrindo a parte de baixo
        for i in (cx - 3, cx - 2):
            r.set(i, 3, BEARD[2])
        for i in (cx + 1, cx + 2):
            r.set(i, 3, BEARD[2])
        r.set(cx - 3, 4, H("FFFFFF")); r.set(cx - 2, 4, H("5A7A9A"))
        r.set(cx + 1, 4, H("5A7A9A")); r.set(cx + 2, 4, H("FFFFFF"))
        r.set(cx - 1, 5, SKIN[2]); r.set(cx, 5, SKIN[2])
        for i in range(cx - 3, cx + 3):
            r.set(i, 6, BEARD[3])
        for j in range(7, r.h):
            for i in range(1, r.w - 1):
                r.set(i, j, BEARD[2] if (i + j) % 3 else BEARD[3])
        r.set(cx - 1, 6, SKIN[1]); r.set(cx, 6, SKIN[1])

    def head_side(r):
        r.fill(SKIN[3])
        for j in range(0, 3):
            r.row(j, BEARD[1])
        for j in range(5, r.h):
            for i in range(0, 3):
                r.set(i, j, BEARD[2])
        r.set(r.w // 2, 4, SKIN[1])
        r.vshade(0.05, 0.12)

    def head_back(r):
        r.fill(BEARD[1])
        for j in range(r.h):
            for i in range(r.w):
                if (i * 3 + j) % 5 == 0:
                    r.set(i, j, BEARD[2])
        r.vshade(0.05, 0.12)

    M["face"] = face
    M["head_side"] = head_side
    M["head_back"] = head_back
    M["head_top"] = lambda r: (r.fill(BEARD[1]), r.noise(0.04))
    M["neck"] = lambda r: (r.fill(SKIN[2]), r.noise(0.03))
    M["beard"] = lambda r: (r.fill(BEARD[2]), [r.set(i, j, BEARD[3]) for j in range(r.h) for i in range(r.w)
                                                if (i + 2 * j) % 3 == 0], r.row(-1, BEARD[1]))

    def hat(r):
        O.cloth(r, HATP, period=3, base=2, top=0.12, bottom=0.08)

    def band(r):
        r.fill(LEATH[1])
        r.row(0, LEATH[3])
        cx = r.w // 2
        r.set(cx, r.h // 2, STEEL[4])
        r.set(cx - 1, r.h // 2, STEEL[3])
        r.noise(0.03)

    M["hat"] = hat
    M["hat_top"] = lambda r: (r.fill(HATP[2]), r.row(r.h // 2, HATP[1]) if r.h > 2 else None, r.noise(0.04))
    M["hat_band"] = band
    M["brim_top"] = lambda r: (r.fill(HATP[2]), [r.set(i, j, HATP[1]) for j in range(r.h) for i in range(r.w)
                                                  if min(i, j, r.w - 1 - i, r.h - 1 - j) == 0], r.noise(0.04))
    M["brim_under"] = lambda r: (r.fill(HATP[1]), r.noise(0.03))
    M["brim_edge"] = lambda r: (r.fill(HATP[1]), r.noise(0.03))

    def coat(r, kind):
        O.cloth(r, MOSS, period=4, base=2)
        if kind == "front":
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, MOSS[1]); r.set(cx, j, MOSS[3])
            for j in range(2, r.h, 3):
                r.set(cx - 2, j, STEEL[3])
            # camisa clara aparecendo no peito
            for j in range(0, 4):
                for i in range(cx - 2 + j // 2, cx + 2 - j // 2):
                    r.set(i, j, SHIRT[3])
        elif kind == "back":
            S_ = r.w // 2
            spiral_px(r, S_ - 1, r.h // 2, min(r.w, r.h) / 3, MOSS[4], glow=False, turns=1.4)

    M["coat_front"] = lambda r: coat(r, "front")
    M["coat_back"] = lambda r: coat(r, "back")
    M["coat_side"] = lambda r: coat(r, "side")
    M["coat_top"] = lambda r: (O.cloth(r, MOSS, base=3, folds=False))
    M["coat_dark"] = O.solid(MOSS[0])

    def belt(r):
        r.fill(LEATH[2])
        r.row(0, LEATH[3])
        for i in range(1, r.w, 3):
            r.set(i, r.h // 2, STEEL[3])
        r.noise(0.03)

    M["belt"] = belt

    def scarf(r):
        r.fill(SCARFP[2])
        for j in range(r.h):
            if j % 3 == 0:
                r.row(j, SCARFP[1])
        r.col(0, SCARFP[3]) if r.w > 2 else None
        r.noise(0.04)

    M["scarf"] = scarf
    M["scarf_end"] = lambda r: (scarf(r), r.row(-1, SCARFP[4]))

    def tail(r, outer):
        O.cloth(r, MOSS if outer else LEATH, period=3, base=2, folds=outer)
        if outer:
            r.row(-1, MOSS[1])
            spiral_px(r, r.w // 2, r.h // 3, min(r.w, r.h) / 3, MOSS[4], glow=False, turns=1.2)

    M["tail_out"] = lambda r: tail(r, True)
    M["tail_in"] = lambda r: tail(r, False)
    M["tail_edge"] = O.solid(MOSS[1])
    M["sleeve"] = lambda r: (O.cloth(r, MOSS, period=3, base=2), r.row(-1, MOSS[1]))
    M["sleeve_top"] = lambda r: O.cloth(r, MOSS, base=3, folds=False)

    def glove(r):
        O.leather(r, LEATH, base=2)
        r.row(0, LEATH[4])

    M["glove"] = glove
    M["hand"] = lambda r: (O.leather(r, LEATH, base=2, seams=False))
    M["pants"] = lambda r: (O.cloth(r, pal("3A3428", "4E4636", "625A46", "7A705A", "948A72"), period=4, base=2))
    M["pants_top"] = O.solid(H("4E4636"))

    def boot(r):
        O.leather(r, LEATH, base=1)
        r.row(0, LEATH[3])
        r.row(1, LEATH[2])

    M["boot"] = boot
    M["sole"] = O.solid(LEATH[0])
    M["spur"] = lambda r: (r.fill(STEEL[3]), r.set(0, 0, STEEL[4]))
    M["ball"] = sphere_metal(STEEL)
    return M


def mentor_entity():
    m = O.Model("steelball_mentor")
    m.bone("body", [0, 12, 0])
    m.bone("head", [0, 24, 0], parent="body")
    m.bone("hat", [0, 31, 0], parent="head")
    m.bone("scarf", [0, 23, 0], parent="body")
    m.bone("scarf_tail", [2, 23, 2.6], parent="scarf")
    m.bone("coat_tail", [0, 12.5, 2.4], parent="body")
    m.bone("right_arm", [-5, 22, 0], parent="body")
    m.bone("right_item", [-6, 11, -0.5], parent="right_arm")
    m.bone("left_arm", [5, 22, 0], parent="body")
    m.bone("right_leg", [-1.9, 12, 0])
    m.bone("left_leg", [1.9, 12, 0])
    # cabeca: rosto com barba, barba saindo do queixo, bigode
    m.box("head", [-4, 24, -4], [8, 8, 8], fc(n="face", s="head_back", ew="head_side", u="head_top", d="neck"))
    m.box("head", [-3, 22.4, -4.6], [6, 2.4, 1.6], fc(all="beard", s=OMIT))
    m.box("head", [-2, 21.2, -4.4], [4, 1.4, 1.2], fc(all="beard", s=OMIT))
    # chapeu de aba larga com faixa (dobra no topo)
    m.box("hat", [-7, 30.6, -7], [14, 0.8, 14], fc(side="brim_edge", u="brim_top", d="brim_under@4x4"))
    m.box("hat", [-4.5, 31.2, -4.5], [9, 1.2, 9], fc(side="hat_band", u=OMIT, d=OMIT))
    m.box("hat", [-4.2, 32.4, -4.2], [8.4, 3.2, 8.4], fc(side="hat", u="hat_top", d=OMIT))
    m.box("hat", [-1.6, 35.4, -3.6], [3.2, 0.8, 7.2], fc(side="hat", u="hat_top", d=OMIT))
    # tronco: sobretudo, cinto, cachecol
    m.box("body", [-4, 12, -2], [8, 12, 4], fc(n="coat_front", s="coat_back", ew="coat_side", u="coat_top",
                                            d="coat_dark@2x2"), inflate=0.35)
    m.box("body", [-4, 12, -2], [8, 1.6, 4], fc(side="belt", u="belt@2x2", d="belt@2x2"), inflate=0.6)
    m.box("body", [-5.6, 11.8, -1.0], [1.8, 1.8, 1.8], fc(all="ball"))
    m.box("body", [3.8, 11.8, -1.0], [1.8, 1.8, 1.8], fc(all="ball"))
    m.box("scarf", [-4.4, 22, -2.4], [8.8, 2, 4.8], fc(side="scarf", u="scarf@4x4", d=OMIT), inflate=0.3)
    m.box("scarf_tail", [1, 15, 2.4], [2.2, 8, 0.6], fc(n="scarf", s="scarf_end", ew="scarf@1x8", u=OMIT, d="scarf@2x1"))
    m.box("coat_tail", [-4.4, 3.5, 2.2], [8.8, 9, 0.6], fc(s="tail_out", n="tail_in", ew="tail_edge@1x9",
                                                         u=OMIT, d="tail_edge@4x1"))
    # bracos (manga do sobretudo, luva)
    m.box("right_arm", [-8, 15, -2], [4, 9, 4], fc(side="sleeve", u="sleeve_top", d=OMIT), inflate=0.3)
    m.box("right_arm", [-8, 12, -2], [4, 3.2, 4], fc(side="glove", u=OMIT, d="hand@2x2"), inflate=0.15)
    m.box("left_arm", [4, 15, -2], [4, 9, 4], fc(side="sleeve", u="sleeve_top", d=OMIT), inflate=0.3)
    m.box("left_arm", [4, 12, -2], [4, 3.2, 4], fc(side="glove", u=OMIT, d="hand@2x2"), inflate=0.15)
    # pernas: calca de montaria e botas altas com espora
    for leg, x in (("right_leg", -3.9), ("left_leg", -0.1)):
        m.box(leg, [x, 6, -2], [4, 6, 4], fc(side="pants", u="pants_top@2x2", d=OMIT))
        m.box(leg, [x, 0, -2], [4, 6.5, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.2)
        m.box(leg, [x + 1.5, 1, 2.2], [1, 0.6, 1.2], fc(all="spur"))
    build_entity(m, mentor_mats(), "steelball_mentor", (2, 3))
    BASE = {"right_leg": [0, 0, 0], "left_leg": [0, 0, 0]}
    anim("steelball_mentor", {
        "animation.steelball_mentor.idle": {
            "loop": True, "animation_length": 3.0,
            "bones": {
                "body": {"rotation": keys((0, [0, 0, 0]), (1.5, [1.5, 0, 0]), (3.0, [0, 0, 0]))},
                "head": {"rotation": keys((0, [0, 0, 0]), (1.5, [-2, 0, 0]), (3.0, [0, 0, 0]))},
                "right_arm": {"rotation": keys((0, [0, 0, 2]), (1.5, [-3, 0, 3]), (3.0, [0, 0, 2]))},
                "left_arm": {"rotation": keys((0, [0, 0, -2]), (1.5, [-2, 0, -3]), (3.0, [0, 0, -2]))},
                "scarf_tail": {"rotation": keys((0, [6, 0, 0]), (1.5, [12, 0, 4]), (3.0, [6, 0, 0]))},
                "coat_tail": {"rotation": keys((0, [4, 0, 0]), (1.5, [7, 0, 0]), (3.0, [4, 0, 0]))}}},
        "animation.steelball_mentor.walk": {
            "loop": True, "animation_length": 1.0,
            "bones": {
                "right_leg": {"rotation": keys((0, [28, 0, 0]), (0.5, [-28, 0, 0]), (1.0, [28, 0, 0]))},
                "left_leg": {"rotation": keys((0, [-28, 0, 0]), (0.5, [28, 0, 0]), (1.0, [-28, 0, 0]))},
                "right_arm": {"rotation": keys((0, [-24, 0, 0]), (0.5, [24, 0, 0]), (1.0, [-24, 0, 0]))},
                "left_arm": {"rotation": keys((0, [24, 0, 0]), (0.5, [-24, 0, 0]), (1.0, [24, 0, 0]))},
                "coat_tail": {"rotation": keys((0, [14, 0, 0]), (0.25, [22, 0, 0]), (0.5, [14, 0, 0]), (0.75, [22, 0, 0]),
                                               (1.0, [14, 0, 0]))},
                "scarf_tail": {"rotation": keys((0, [24, 0, 0]), (0.5, [32, 0, 6]), (1.0, [24, 0, 0]))}}},
        "animation.steelball_mentor.fight": {
            "loop": True, "animation_length": 1.5,
            "bones": {
                "body": {"rotation": keys((0, [6, 12, 0]), (0.75, [8, 14, 0]), (1.5, [6, 12, 0]))},
                "right_arm": {"rotation": keys((0, [-50, 10, 0]), (0.75, [-56, 14, 0]), (1.5, [-50, 10, 0]))},
                "left_arm": {"rotation": keys((0, [-30, -10, -12]), (0.75, [-26, -10, -14]), (1.5, [-30, -10, -12]))},
                "right_leg": {"rotation": keys((0, [-14, 0, 4]), (1.5, [-14, 0, 4]))},
                "left_leg": {"rotation": keys((0, [12, 0, -4]), (1.5, [12, 0, -4]))},
                "scarf_tail": {"rotation": keys((0, [20, 0, 0]), (0.75, [28, 0, 5]), (1.5, [20, 0, 0]))},
                "coat_tail": {"rotation": keys((0, [10, 0, 0]), (0.75, [14, 0, 0]), (1.5, [10, 0, 0]))}}},
        "animation.steelball_mentor.throw": {
            "loop": False, "animation_length": 0.55,
            "bones": {
                "right_arm": {"rotation": keys((0, [-50, 10, 0]), (0.15, [-175, 20, 10]), (0.3, [-70, -10, 0]),
                                               (0.55, [-50, 10, 0]))},
                "body": {"rotation": keys((0, [6, 12, 0]), (0.15, [-4, 30, 0]), (0.3, [14, -10, 0]), (0.55, [6, 12, 0]))}}},
        "animation.steelball_mentor.nails": {
            "loop": False, "animation_length": 0.6,
            "bones": {
                "left_arm": {"rotation": keys((0, [-30, -10, -12]), (0.1, [-90, -5, 0]), (0.2, [-84, -5, 0]),
                                              (0.3, [-90, -5, 0]), (0.4, [-84, -5, 0]), (0.5, [-90, -5, 0]),
                                              (0.6, [-30, -10, -12]))},
                "body": {"rotation": keys((0, [6, 12, 0]), (0.1, [4, -20, 0]), (0.5, [4, -20, 0]), (0.6, [6, 12, 0]))}}},
        "animation.steelball_mentor.talk": {
            "loop": False, "animation_length": 1.2,
            "bones": {
                "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [8, 0, 0]), (0.6, [-4, 0, 0]), (0.9, [6, 0, 0]),
                                          (1.2, [0, 0, 0]))},
                "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-40, 20, 10]), (0.8, [-46, 26, 14]),
                                               (1.2, [0, 0, 0]))}}},
        "animation.steelball_mentor.bow": {
            "loop": False, "animation_length": 1.6,
            "bones": {
                "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [32, 0, 0]), (1.1, [32, 0, 0]), (1.6, [0, 0, 0]))},
                "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [18, 0, 0]), (1.1, [18, 0, 0]), (1.6, [0, 0, 0]))},
                "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-150, -20, 0]), (1.1, [-150, -20, 0]),
                                               (1.6, [0, 0, 0]))},
                "right_leg": {"rotation": keys((0, BASE["right_leg"]), (0.4, [-8, 0, 0]), (1.1, [-8, 0, 0]),
                                               (1.6, [0, 0, 0]))}}},
    })


# =====================================================================================
#  ROUPA DO STEEL BALL (4 graus) — chapeu de aba com faixa e fitas, colete/sobretudo, calca de montaria e botas
#  com esporas; II ganha cachecol e cartucheira de bolas; III espirais gravadas que brilham; IV ouro e capa longa
#  com o Retangulo Dourado no peito.
# =====================================================================================
TIERS = ["jockey", "plains", "rotation", "saint"]


def outfit_mats(tier):
    P = S.OUT_PAL[tier]
    HT, BD, CT, PT, BT, MT, GL = P["hat"], P["band"], P["coat"], P["pants"], P["boot"], P["metal"], P["glow"]
    lv = TIERS.index(tier)
    M = {}

    def deco(r, c, glow):
        """Gravacao do grau: espirais (III) e linhas douradas (IV)."""
        if lv >= 2 and r.w >= 5 and r.h >= 5:
            spiral_px(r, r.w // 2, r.h // 2, min(r.w, r.h) / 2.6, c, glow=glow, turns=1.3)

    M["hat"] = lambda r: (O.cloth(r, HT, period=3, base=2, top=0.12, bottom=0.08))
    M["hat_top"] = lambda r: (r.fill(HT[2]), r.row(r.h // 2, HT[1]) if r.h > 2 else None, r.noise(0.04))
    M["brim_top"] = lambda r: (r.fill(HT[2]), [r.set(i, j, BD[2] if lv == 3 else HT[1]) for j in range(r.h)
                                                for i in range(r.w) if min(i, j, r.w - 1 - i, r.h - 1 - j) == 0],
                               r.noise(0.04))
    M["brim_under"] = lambda r: (r.fill(HT[1]), r.noise(0.03))
    M["brim_edge"] = lambda r: (r.fill(BD[2] if lv == 3 else HT[1]), r.noise(0.03))

    def band(r, front):
        r.fill(BD[2])
        r.row(0, BD[3])
        r.row(-1, BD[1])
        if front:
            cx = r.w // 2
            if lv >= 2:
                r.glow(cx - 1, r.h // 2, GL)
                r.glow(cx, r.h // 2, lighten(GL, 0.4))
            else:
                r.set(cx - 1, r.h // 2, MT[3])
                r.set(cx, r.h // 2, MT[4])
        r.noise(0.03)

    M["band"] = lambda r: band(r, False)
    M["band_front"] = lambda r: band(r, True)

    def ribbon(r):
        r.fill(BD[2])
        r.col(0, BD[3])
        r.row(-1, BD[1])
        if lv >= 2:
            r.glow(r.w // 2, r.h - 2, GL)

    M["ribbon"] = ribbon

    def coat(r, kind):
        O.cloth(r, CT, period=4, base=2)
        cx = r.w // 2
        if kind == "front":
            # abertura do colete/casaco com botoes de metal e camisa clara no peito
            for j in range(r.h):
                r.set(cx - 1, j, CT[1]); r.set(cx, j, CT[3])
            for j in range(0, min(4, r.h)):
                for i in range(cx - 2 + j // 2, cx + 2 - j // 2):
                    r.set(i, j, H("EEE6D0"))
            for j in range(3, r.h - 1, 3):
                r.set(cx - 2, j, MT[4]); r.set(cx + 1, j, MT[4])
            if lv == 3:
                golden_rect_px(r, 1, 4, min(r.w - 2, 6), 4, GL, glow=True)
            elif lv == 2:
                spiral_px(r, cx + 2, r.h // 2, 2.2, GL, glow=True, turns=1.2)
        elif kind == "back":
            if lv >= 2:
                spiral_px(r, cx - 1, r.h // 2, min(r.w, r.h) / 2.6, GL, glow=True, turns=1.6)
            else:
                spiral_px(r, cx - 1, r.h // 2, min(r.w, r.h) / 3, CT[4], glow=False, turns=1.2)
        if lv == 3:
            O.trim_rows(r, BD, 1)

    M["coat_front"] = lambda r: coat(r, "front")
    M["coat_back"] = lambda r: coat(r, "back")
    M["coat_side"] = lambda r: coat(r, "side")
    M["coat_top"] = lambda r: O.cloth(r, CT, base=3, folds=False)
    M["dark"] = O.solid(CT[0])
    M["collar"] = lambda r: (r.fill(CT[3]), r.row(0, CT[4]), r.row(-1, CT[1]), r.noise(0.03))

    def belt(r, front):
        r.fill(S.OUT_PAL["jockey"]["boot"][2] if lv < 3 else BD[1])
        r.row(0, lighten(r.get(0, 0), 0.2))
        if front:
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, MT[3]); r.set(cx, j, MT[4])
        r.noise(0.03)

    M["belt"] = lambda r: belt(r, False)
    M["belt_front"] = lambda r: belt(r, True)
    M["ball"] = sphere_metal(MT if lv in (1, 3) else STEEL)
    M["bandolier"] = lambda r: (r.fill(S.OUT_PAL["jockey"]["boot"][3]),
                                [r.set(i, r.h // 2, MT[4]) for i in range(1, r.w, 2)], r.noise(0.03))
    M["scarf"] = lambda r: (r.fill(BD[2]), [r.row(j, BD[1]) for j in range(0, r.h, 3)], r.noise(0.04))

    def tail(r, outer):
        O.cloth(r, CT if outer else PT, period=3, base=2, folds=outer)
        if outer:
            r.row(-1, CT[1])
            deco(r, GL if lv >= 2 else CT[4], lv >= 2)
            if lv == 3:
                O.trim_rows(r, BD, 2)
                r.glow(1, r.h - 1, GL)
                r.glow(r.w - 2, r.h - 1, GL)

    M["tail_out"] = lambda r: tail(r, True)
    M["tail_in"] = lambda r: tail(r, False)
    M["tail_edge"] = O.solid(BD[2] if lv == 3 else CT[1])
    M["sleeve"] = lambda r: (O.cloth(r, CT, period=3, base=2), r.row(-1, CT[1]))
    M["sleeve_top"] = lambda r: O.cloth(r, CT, base=3, folds=False)

    def glove(r):
        O.leather(r, BT, base=2)
        r.row(0, MT[3] if lv >= 1 else BT[4])
        if lv >= 2:
            r.glow(r.w // 2, r.h // 2, GL)

    M["glove"] = glove

    def shoulder(r):
        O.metal(r, MT)
        if lv >= 2:
            r.glow(r.w // 2, r.h // 2, GL)

    M["shoulder"] = shoulder
    M["shoulder_top"] = lambda r: (O.metal(r, MT, rivets=False), deco(r, GL, True))

    def pants(r):
        O.cloth(r, PT, period=4, base=2)
        if r.w >= 3:
            r.col(0, PT[1])
        if lv >= 2:
            for j in range(1, r.h, 3):
                r.set(r.w - 1, j, MT[4])

    M["pants"] = pants
    M["pants_top"] = O.solid(PT[2])

    def boot(r, front):
        O.leather(r, BT, base=2)
        r.row(0, BT[4])
        if front and r.w >= 3:
            cx = r.w // 2
            for j in range(1, r.h - 1, 2):
                r.set(cx - 1, j, MT[3])
        if lv >= 2 and front:
            r.glow(r.w // 2, 2, GL)

    M["boot"] = lambda r: boot(r, False)
    M["boot_front"] = lambda r: boot(r, True)
    M["boot_cuff"] = lambda r: (r.fill(BT[3]), r.row(0, BT[4]), r.row(-1, BT[1]), r.noise(0.03))
    M["sole"] = O.solid(BT[0])

    def spur(r):
        r.fill(MT[3])
        r.set(0, 0, MT[4])
        if lv >= 2:
            r.glow(r.w // 2, r.h // 2, GL)

    M["spur"] = spur
    M["toe"] = lambda r: (O.leather(r, BT, base=2, seams=False), r.row(0, BT[3]))
    return M


def outfit_model(tier):
    lv = TIERS.index(tier)
    m = O.Model("steelball_outfit_" + tier)
    O.skeleton(m)
    hd = "armorHead"
    # chapeu de aba (a aba da frente desce um pouco), copa com vinco e faixa
    brim = 14 if lv < 3 else 15
    m.box(hd, [-brim / 2, 30.6, -brim / 2], [brim, 0.8, brim], fc(side="brim_edge", u="brim_top", d="brim_under@4x4"))
    m.box(hd, [-4.6, 31.2, -4.6], [9.2, 1.4, 9.2], fc(side="band", n="band_front", u=OMIT, d=OMIT))
    m.box(hd, [-4.3, 32.6, -4.3], [8.6, 3.0 + 0.3 * lv, 8.6], fc(side="hat", u="hat_top", d=OMIT))
    m.box(hd, [-1.6, 35.4 + 0.3 * lv, -3.8], [3.2, 0.8, 7.6], fc(side="hat", u="hat_top", d=OMIT))
    # fitas da faixa (tremulam no renderer)
    m.bone("hatBand", [0, 32.2, 4.7], parent=hd)
    m.box("hatBand", [0.4, 28.4 - lv * 0.5, 4.6], [1.2, 3.8 + lv * 0.5, 0.3], fc(side="ribbon", u=OMIT, d=OMIT))
    m.box("hatBand", [-1.8, 29.0 - lv * 0.5, 4.6], [1.2, 3.2 + lv * 0.5, 0.3], fc(side="ribbon", u=OMIT, d=OMIT))

    bd = "armorBody"
    m.box(bd, [-4, 12, -2], [8, 12, 4], fc(n="coat_front", s="coat_back", ew="coat_side", u="coat_top", d="dark@2x2"),
          inflate=0.55)
    m.box(bd, [-4, 22.4, -2], [8, 1.6, 4], fc(side="collar", u="collar@2x2", d=OMIT), inflate=0.8)
    m.box(bd, [-4, 12, -2], [8, 1.6, 4], fc(n="belt_front", s="belt", ew="belt", u="belt@2x2", d="belt@2x2"), inflate=0.85)
    # bolas de aco presas no cinto (a arma da classe sempre a mao)
    m.box(bd, [-5.9, 11.6, -1.0], [1.9, 1.9, 1.9], fc(all="ball"))
    m.box(bd, [4.0, 11.6, -1.0], [1.9, 1.9, 1.9], fc(all="ball"))
    if lv >= 1:
        # cartucheira cruzando o peito e cachecol no pescoco
        m.box(bd, [-5, 16.6, -2.9], [10, 1.4, 0.5], fc(n="bandolier", s="bandolier", ew="bandolier@1x1", u="bandolier@4x1",
                                                      d="bandolier@4x1"), rot=[0, 0, 38], pivot=[0, 17.3, -2.65])
        m.box(bd, [-4.4, 21.6, -2.4], [8.8, 2.2, 4.8], fc(side="scarf", u="scarf@4x4", d=OMIT), inflate=0.85)
    # abas do casaco (ou capa, no IV): mexem com a corrida e voam a cavalo (SbOutfitRenderer)
    if lv == 3:
        m.bone("outfitCape", [0, 24, 2.65], parent=bd)
        m.box("outfitCape", [-4.8, 2.0, 2.65], [9.6, 22, 0.6], fc(s="tail_out", n="tail_in", ew="tail_edge@1x8",
                                                                u="tail_edge@3x1", d="tail_edge@3x1"))
    else:
        top = 12.6
        bottom = [8.5, 4.0, 3.0][lv]
        m.bone("outfitCape", [0, top, 2.6], parent=bd)
        m.box("outfitCape", [-4.6, bottom, 2.4], [9.2, top - bottom, 0.6], fc(s="tail_out", n="tail_in",
                                                                            ew="tail_edge@1x6", u=OMIT, d="tail_edge@3x1"))
        if lv >= 1:
            # pontas do sobretudo na frente (abertas)
            m.box(bd, [-4.6, bottom + 1.5, -2.95], [3.4, top - bottom - 1.5, 0.5], fc(n="tail_out", s="tail_in",
                                                                                   ew="tail_edge@1x6", u=OMIT, d="tail_edge@2x1"))
            m.box(bd, [1.2, bottom + 1.5, -2.95], [3.4, top - bottom - 1.5, 0.5], fc(n="tail_out", s="tail_in",
                                                                                  ew="tail_edge@1x6", u=OMIT, d="tail_edge@2x1"))

    ra = "armorRightArm"
    m.box(ra, [-8, 15, -2], [4, 9, 4], fc(side="sleeve", u="sleeve_top", d=OMIT), inflate=0.5)
    m.box(ra, [-8, 12, -2], [4, 3, 4], fc(side="glove", u=OMIT, d="dark@2x2"), inflate=0.65)
    if lv >= 2:
        m.box(ra, [-8.7, 21.6, -2.7], [5.4, 2.6, 5.4], fc(side="shoulder", u="shoulder_top", d="dark@2x2"))
    m.mirror(ra, "armorLeftArm")

    rl = "armorRightLeg"
    m.box(rl, [-3.9, 5, -2], [4, 7, 4], fc(side="pants", u=OMIT, d=OMIT), inflate=0.35)
    m.mirror(rl, "armorLeftLeg")

    rb = "armorRightBoot"
    m.box(rb, [-3.9, 0, -2], [4, 7, 4], fc(side="boot", n="boot_front", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(rb, [-3.9, 6.2, -2], [4, 1.4, 4], fc(side="boot_cuff", u="dark@2x2", d="dark@2x2"), inflate=0.85)
    m.box(rb, [-3.6, -0.5, -3.7], [3.4, 1.8, 1.6], fc(n="toe", ew="toe", u="toe", d="sole@2x2", s=OMIT))
    # espora: haste no calcanhar + rosca (a "bolinha" que gira)
    m.box(rb, [-2.4, 1.0, 2.5], [1, 0.6, 1.4], fc(all="spur"))
    m.box(rb, [-2.6, 0.6, 3.8], [1.4, 1.4, 0.4], fc(all="spur"))
    m.mirror(rb, "armorLeftBoot")
    return m


def vanilla_layers(name, main, trim, dark):
    """Camadas vanilla (reserva se o GeckoLib nao desenhar): textures/models/armor/<name>_layer_1/2.png."""
    import random
    c1 = Canvas(64, 32)
    rnd = random.Random(len(name))
    for (x0, y0, w, h) in [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = main if (x + y) % 5 else dark
                if rnd.random() < 0.08:
                    c = trim
                c1.set(x, y, c)
    for x in range(64):
        c1.set(x, 31, trim)
    c1.save(os.path.join(A, "textures/models/armor", f"{name}_layer_1.png"))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(A, "textures/models/armor", f"{name}_layer_2.png"))


def outfits():
    for tier in TIERS:
        m = outfit_model(tier)
        _, glow = O.build(m, outfit_mats(tier))
        if tier in ("rotation", "saint") and not glow:
            raise RuntimeError(f"grau {tier} precisa de glowmask (SbOutfitTier.glows)")
        P = S.OUT_PAL[tier]
        vanilla_layers("steelball_outfit_" + tier, P["coat"][2], P["band"][2], P["coat"][0])


def run():
    print("== entidades")
    ball_entity()
    nail_entity()
    vortex_entity()
    infinite_entity()
    spiral_hole_entity()
    ball_breaker_entity()
    mentor_entity()
    print("== roupa (4 graus)")
    outfits()
