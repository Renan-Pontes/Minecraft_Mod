"""
Assets das TECNICAS AVANCADAS do modulo class_shinigami (pacote de tecnicas iconicas, homenagens a Bleach).
Uso (na raiz do projeto):  py -I tools/gen/class_shinigami_techniques.py

Gera (assets/isekai_mahou/textures/entity/):
  - shinigami_getsuga.png / shinigami_getsuga_black.png  lua crescente enorme do Getsuga Tensho (branca de brilho azul;
    negra de borda rubra) - desenhadas JA COLORIDAS (o Java nao tinge)
  - shinigami_mugetsu.png       parede de trevas do Mugetsu (negra, borda violeta rasgada)
  - shinigami_black_coffin.png  paredes do Hado n. 90 (negro com veios violeta; repete a cada bloco)
  - shinigami_cero_beam.png     faixa do feixe do Cero (claro no meio, transparente nas bordas; tingido no Java)
  - shinigami_hollow_mask.png   mascara do Vazio (caveira branca de listras rubras) - layout 64x32 do HollowMaskLayer
  - shinigami_vasto_mask.png    mascara do Vasto Lorde + chifres + buraco no peito (mesmo layout)
  - zanpakuto_spirit_hollow.png (+ _glowmask) a aparencia do VAZIO INTERIOR no espirito da zanpakuto (mesmo modelo
    GeckoLib do tools/gen/class_shinigami.py: pele e roupa brancas, olhos negros e dourados, lamina negra)
Traducoes: tools/gen/class_shinigami_lang.py
"""
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402
import class_shinigami as CS  # noqa: E402  (modelo do espirito)

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
ENT = os.path.join(A, "textures/entity")


def H(h, a=255):
    return hex_rgba(h, a)


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(v)))


def crescent(name, core_of, seed, ragged=0.0):
    """Lua crescente 64x32 (a curva para a frente = topo da imagem). core_of(t, u) -> (r,g,b,a_mult)."""
    rnd = random.Random(seed)
    c = Canvas(64, 32)
    jag = [rnd.uniform(-ragged, ragged) for _ in range(64)]
    for y in range(32):
        for x in range(64):
            u = (x + 0.5 - 32) / 32.0
            v = (y + 0.5) / 32.0
            outer = 0.12 + 0.72 * (u * u) + jag[x] * 0.5
            inner = outer + 0.40 * (1 - abs(u)) ** 0.65 + jag[x]
            if outer <= v <= inner and abs(u) < 0.985:
                t = (v - outer) / max(1e-3, inner - outer)
                r, g, b, am = core_of(t, u)
                a = 255 * am * (1 - abs(u) ** 4)
                c.set(x, y, (clamp(r), clamp(g), clamp(b), clamp(a)))
            elif abs(u) < 0.95 and outer - 0.06 <= v < outer:
                # brilho fino na frente do corte
                r, g, b, _ = core_of(0.0, u)
                c.set(x, y, (clamp(r), clamp(g), clamp(b), clamp(90 * (1 - abs(u) ** 2))))
    c.save(os.path.join(ENT, name + ".png"))


def getsuga_white(t, u):
    # frente branca, corpo branco-azulado, fundo azul esmaecendo
    col = mix(H("FFFFFF"), H("8FE0FF"), min(1.0, t * 1.4))
    col = mix(col, H("3A8AD8"), max(0.0, t - 0.6) * 1.5)
    return col[0], col[1], col[2], (1 - t) ** 0.55


def getsuga_black(t, u):
    # fio rubro na frente, corpo negro, cauda vermelho-escura
    if t < 0.12:
        col = mix(H("FF4A5A"), H("C0102A"), t / 0.12)
    elif t < 0.75:
        col = mix(H("1A060C"), H("0A0306"), (t - 0.12) / 0.63)
    else:
        col = mix(H("0A0306"), H("5A0414"), (t - 0.75) / 0.25)
    return col[0], col[1], col[2], (1 - t) ** 0.4


def mugetsu(t, u):
    if t < 0.08:
        col = mix(H("9A6AE0"), H("3A1450"), t / 0.08)
    else:
        col = mix(H("0C0810"), H("030205"), min(1.0, (t - 0.08) * 2))
    return col[0], col[1], col[2], min(1.0, (1 - t) ** 0.3 + 0.1)


def coffin():
    """32x32: negro com placas, rebites e veios violeta (repete a cada bloco)."""
    rnd = random.Random(90)
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            n = rnd.uniform(-0.04, 0.04)
            base = H("0C0A12") if (x // 8 + y // 8) % 2 == 0 else H("120E1A")
            c.set(x, y, lighten(base, n) if n > 0 else darken(base, -n))
    # moldura das placas
    for k in range(0, 32, 8):
        for i in range(32):
            c.set(k, i, H("2A1640"))
            c.set(i, k, H("2A1640"))
    # veios violeta (rachaduras que brilham)
    for _ in range(5):
        x, y = rnd.randrange(32), rnd.randrange(32)
        for _ in range(14):
            c.set(x % 32, y % 32, H("8A3AE0"))
            if rnd.random() < 0.5:
                c.set((x + 1) % 32, y % 32, H("5A1AA0"))
            x += rnd.choice((-1, 0, 1))
            y += 1
    # bordas de cima e de baixo mais claras (a "tampa")
    for i in range(32):
        c.set(i, 0, H("4A2A70"))
        c.set(i, 31, H("4A2A70"))
    c.save(os.path.join(ENT, "shinigami_black_coffin.png"))


def cero_beam():
    """16x16: v atravessa o feixe (claro no meio), u corre ao longo (estrias para o movimento)."""
    rnd = random.Random(31)
    c = Canvas(16, 16)
    streak = [rnd.uniform(0.75, 1.0) for _ in range(16)]
    for y in range(16):
        d = abs((y + 0.5) - 8) / 8.0
        prof = max(0.0, 1 - d ** 1.6)
        for x in range(16):
            v = prof * streak[(x + y // 3) % 16]
            w = clamp(200 + 55 * v)
            c.set(x, y, (w, w, w, clamp(255 * v)))
    c.save(os.path.join(ENT, "shinigami_cero_beam.png"))


BONE = [H("8A867C"), H("C8C4B8"), H("E8E4DA"), H("F6F4EE"), H("FFFFFF")]
RED = H("C0102A")
DARK_RED = H("6A0414")
VOID = H("0A0608")


def mask_sheet(name, vasto):
    """Layout do HollowMaskLayer (64x32): mascara 9x9x1 em (0,0); chifres em (32,0)/(32,8)/(32,14); buracos em (16,16)."""
    c = Canvas(64, 32)
    # bloco da mascara: bordas (topo, lados, verso) em osso sombreado
    for y in range(0, 11):
        for x in range(0, 20):
            c.set(x, y, BONE[1] if (x + y) % 3 else BONE[0])
    # frente 9x9 em (1,1)
    for y in range(9):
        for x in range(9):
            cx, cy = x - 4, y - 4
            shade = BONE[3] if abs(cx) + abs(cy) < 6 else BONE[2]
            if y == 8 or x in (0, 8):
                shade = BONE[1]
            c.set(1 + x, 1 + y, shade)
    f = lambda x, y, col: c.set(1 + x, 1 + y, col)  # noqa: E731
    # olhos (vazados, negros), nariz e a boca de dentes
    for (x, y) in ((1, 3), (2, 3), (3, 3), (2, 4), (5, 3), (6, 3), (7, 3), (6, 4)):
        f(x, y, VOID)
    f(4, 5, BONE[1])
    for x in range(1, 8):
        f(x, 6, VOID if x % 2 == 0 else BONE[4])
        f(x, 7, BONE[2] if x % 2 == 0 else VOID)
    if not vasto:
        # listras rubras do lado esquerdo (de cima ate o queixo, passando pelo olho)
        for y in range(0, 9):
            if y in (3, 4):
                continue
            f(1, y, RED)
            if y < 3 or y > 6:
                f(2, y, RED)
        f(3, 0, RED)
    else:
        # Vasto Lorde: marcas rubras saindo dos olhos para cima (rumo aos chifres) e o queixo escurecido
        for (x, y) in ((1, 2), (2, 1), (2, 0), (3, 2), (7, 2), (6, 1), (6, 0), (5, 2)):
            f(x, y, RED)
        for (x, y) in ((1, 5), (7, 5)):
            f(x, y, DARK_RED)
        for x in range(1, 8):
            f(x, 8, BONE[0])
    # chifres (osso -> ponta escura)
    for (u0, v0, w, h, d0, d1) in ((32, 0, 14, 7, 0.0, 0.4), (32, 8, 10, 5, 0.4, 0.75), (32, 14, 8, 4, 0.75, 1.0)):
        for y in range(h):
            for x in range(w):
                t = d0 + (d1 - d0) * (x / max(1, w - 1))
                col = mix(BONE[3], H("2A2622"), max(0.0, t - 0.55) / 0.45) if vasto else BONE[2]
                if (x + y) % 4 == 0:
                    col = darken(col, 0.12)
                c.set(u0 + x, v0 + y, col)
    # buraco no peito: disco negro com borda rubra irregular, fundo transparente (frente e verso de cada placa)
    rnd = random.Random(7)
    for (u0, v0) in ((16, 16), (20, 16), (24, 16), (28, 16)):
        for y in range(4):
            for x in range(4):
                d = math.hypot(x + 0.5 - 2, y + 0.5 - 2)
                if d < 1.4:
                    c.set(u0 + x, v0 + y, VOID)
                elif d < 2.1 and rnd.random() < 0.85:
                    c.set(u0 + x, v0 + y, DARK_RED if rnd.random() < 0.5 else H("2A0006"))
    c.save(os.path.join(ENT, name + ".png"))


def hollow_spirit():
    """O espirito tomado pelo Vazio Interior: o mesmo modelo, cores invertidas, olhos negros e dourados."""
    v = dict(robe="#EEECE6", trim="#16141C", hair="#F6F6F2", skin="#E4E2DC", eyes="#FFC830", blade="#16121C",
             grip="#F0EEE8", detail="#E01030", beard=False, hood=False, pattern=None)
    m = CS.spirit_model(v)
    m.layout()
    m.paint(91)
    head = m.faces(m.cubes[3])["front"]
    # esclera negra em volta das pupilas douradas e uma marca rubra sob o olho
    head.set(1, 3, VOID)
    head.set(head.w - 2, 3, VOID)
    head.set(1, 4, H("7A0A1A"))
    head.set(head.w - 2, 4, H("7A0A1A"))
    m.save_texture("zanpakuto_spirit_hollow")


ICONS = {  # glifo -> (tipo da moldura, pictograma do tools/gen/icons.py)
    "zanpakuto_getsuga": ("form", "cleave_arc"),
    "kido_hado_90": ("form", "sand_coffin"),
    "shinigami_visored": ("form", "nightmare"),
    "shinigami_cero": ("form", "beam"),
    "shinigami_vasto_lorde": ("form", "primal_roar"),
    "zanpakuto_mugetsu": ("form", "eclipse"),
}


def glyph_icons():
    """Icones 32x32 dos glifos novos (mesmo estilo do tools/gen/icons.py, com a moldura certa de cada tipo)."""
    sys.argv = sys.argv[:1]
    import icons as I  # noqa: E402
    specs, _, _ = I.build_specs()
    for gid, (kind, pic) in ICONS.items():
        spec = dict(specs.get(gid) or {"elem": "shadow"})
        spec["kind"], spec["pic"] = kind, pic
        spec.pop("args", None)
        I.save_px(I.make_glyph_icon(spec), os.path.join(A, "textures/gui/glyph", gid + ".png"))


def main():
    print("== tecnicas avancadas do Shinigami")
    crescent("shinigami_getsuga", getsuga_white, 11)
    crescent("shinigami_getsuga_black", getsuga_black, 12, ragged=0.012)
    crescent("shinigami_mugetsu", mugetsu, 13, ragged=0.03)
    coffin()
    cero_beam()
    mask_sheet("shinigami_hollow_mask", False)
    mask_sheet("shinigami_vasto_mask", True)
    hollow_spirit()
    glyph_icons()
    print("ok")


if __name__ == "__main__":
    main()
