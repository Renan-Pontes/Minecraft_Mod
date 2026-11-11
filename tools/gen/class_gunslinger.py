"""
Gera os assets do modulo class_gunslinger (PISTOLEIRO).
Uso (na raiz do projeto):  py -I tools/gen/class_gunslinger.py

Saidas:
  - Armas de mana (modelo 3D na mao + icone 2D no inventario, loader neoforge:separate_transforms; motor do armory):
      mana_revolver (kit) e mana_revolver_t1..t4, mana_pistol, mana_shotgun, mana_dual_pistols, mana_rifle
      -> models/item/<id>.json, models/item/class_gunslinger/<id>_held.json, textures/item/class_gunslinger/<id>.png,
         textures/item/<id>.png
  - Traje do Pistoleiro em 4 graus (GeckoLib, motor do tools/gen/outfits.py):
      geo/item/armor/outfit_gunslinger[_adept|_master|_archmage].geo.json + textures/item/armor/... (+ _glowmask II-IV),
      reservas vanilla textures/models/armor/outfit_gunslinger*_layer_1/2.png e os 16 icones das pecas
      Ossos extras lidos pelo GsArmorRenderer: waistChest + chestSkirtFront/Back/Right/Left (abas do guarda-po),
      outfitCape (poncho/manta) e hatMid (pena do chapeu).
  - Forasteiro Espectral (GeckoLib): geo/entity/gunslinger_outlaw.geo.json, animations/entity/gunslinger_outlaw.animation.json,
      textures/entity/gunslinger_outlaw.png e _reaper.png (+ glowmasks), ovo.
  - Bala de mana (textures/entity/mana_bullet.png, so de reserva: o renderer desenha a fita de luz).
  - Animacoes do jogador (playerAnimator): player_animations/gunslinger_{shoot,shoot_left,reload,fan,charge,noon}.json
  - Icone da aba do menu B: textures/gui/tab/gunslinger_path.png
  - Traducoes (pt_br + en_us) em src/main/lang/class_gunslinger (tools/gen/class_gunslinger_lang.py)

Convencoes do .geo.json (Bedrock/GeckoLib): frente = -z, lado DIREITO = -x, y para cima; X negativo levanta o braco
para a frente. Ossos animados nao tem rotacao inicial.
Armas (modelo de item): o cano aponta para -Z e o topo da arma para +Y; com o braco erguido (pose de mira) o -Z do
item vira a frente e o +Y vira para cima; com o braco abaixado a arma aponta para o chao.
"""
import json
import math
import os
import sys
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix, item_model  # noqa: E402
import armory as AR  # noqa: E402
import outfits as OF  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
NS = "isekai_mahou"
T = (0, 0, 0, 0)
fc, OMIT = OF.fc, OF.OMIT
HD, BD, RA, LA, RL, LL, RB, LB = ("armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg",
                                  "armorLeftLeg", "armorRightBoot", "armorLeftBoot")


def H(h, a=255):
    return hex_rgba(h, a)


def pal(*hexes):
    return [H(h) for h in hexes]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# =====================================================================================================
#  ARMAS DE MANA (motor de modelos 3D do armory)
# =====================================================================================================
M = AR.M
GUNMETAL = M("metal", "c8ccd8", "8a90a0", "5a6070", "2a2e38")
BLUED = M("metal", "c0d8ff", "6a8ac0", "3a5488", "1a2848")
NICKEL = M("metal", "ffffff", "e8eef6", "b8c2d0", "6a7484")
GLOW_GOLD = M("glow", "fffbe0", "ffe680", "f2c14e", "a07818", glow=True)
GLOW_CYAN = M("glow", "ffffff", "c8f6ff", "8fd8ff", "3a8ab0", glow=True)
GLOW_MINT = M("glow", "ffffff", "d8ffe6", "9af0b0", "3a9a60", glow=True)
GLOW_LILAC = M("glow", "ffffff", "ecdcff", "c08cff", "6a3ab0", glow=True)
GRIP_PEARL = M("wood", "ffffff", "f6f0ff", "dcd2ec", "9a90b0")

# Revólver: kit (0) e tiers I-IV: metal, cabo, detalhe (anel da boca/cão), runa (brilha), gema do cabo
REVOLVER_LOOK = [
    (GUNMETAL, AR.WALNUT, AR.BRASS, GLOW_GOLD, None),
    (AR.IRON, AR.WALNUT, AR.IRON, GLOW_GOLD, None),
    (AR.STEEL, AR.ROSEWOOD, AR.BRASS, AR.RUNE_GOLD, None),
    (NICKEL, AR.EBONY, AR.GOLD, AR.RUNE_GOLD, AR.GEM_AMBER),
    (AR.GOLD, AR.IVORY, AR.DARK_IRON, AR.GLOW_FIRE, AR.GEM_SUN),
]
REVOLVER_IDS = ["mana_revolver", "mana_revolver_t1", "mana_revolver_t2", "mana_revolver_t3", "mana_revolver_t4"]
GRIP_ROT = -22.5  # o cabo desce inclinado para tras (+Z)


def grip(m, x0, x1, y0, y1, z0, z1, wood, cap, pivot):
    rot = ("x", GRIP_ROT, pivot)
    m.box((x0, y0, z0), (x1, y1, z1), wood, rot=rot)
    m.box((x0 - 0.1, y0 - 0.6, z0 - 0.1), (x1 + 0.1, y0, z1 + 0.1), cap, rot=rot)


def trigger_guard(m, z0, z1, mat):
    m.box((7.6, 6.9, z0), (8.4, 7.3, z1), mat)
    m.box((7.6, 7.3, z0 - 0.2), (8.4, 8.0, z0 + 0.2), mat)
    m.box((7.8, 7.3, (z0 + z1) / 2), (8.2, 8.0, (z0 + z1) / 2 + 0.3), mat)


def revolver_model(t):
    metal, wood, detail, rune, gemm = REVOLVER_LOOK[t]
    m = AR.Model(REVOLVER_IDS[t], atlas=64)
    # armação, alça de cima e o tambor octogonal
    m.box((7.0, 8.0, 6.6), (9.0, 10.2, 11.0), metal)
    m.box((7.2, 10.2, 6.8), (8.8, 10.7, 10.6), metal)
    m.cbox(8, 9.6, 7.6, 2.6, 2.6, 2.8, metal)
    m.cbox(8, 9.6, 7.6, 2.6, 2.6, 2.8, metal, rot=("z", 45, (8, 9.6, 7.6)))
    # câmaras que brilham na frente do tambor (a mana carregada)
    for dx, dy in ((-0.7, 0.0), (0.7, 0.0), (0.0, 0.7), (0.0, -0.7)):
        m.cbox(8 + dx, 9.6 + dy, 6.15, 0.45, 0.45, 0.12, rune)
    # cano, vareta, alça de mira, anel da boca
    m.box((7.35, 9.55, -1.4), (8.65, 10.75, 6.4), metal)
    m.box((7.6, 8.8, 0.2), (8.4, 9.55, 6.4), AR.DARK_IRON)
    m.box((7.8, 10.75, -1.2), (8.2, 11.3, -0.4), detail)
    m.box((7.2, 9.4, -1.7), (8.8, 10.9, -1.0), detail)
    if t >= 2:
        m.box((7.28, 9.48, 2.0), (8.72, 10.82, 2.8), rune)
        m.box((7.28, 9.48, 4.2), (8.72, 10.82, 4.6), rune)
    # cão, guarda-mato e gatilho
    m.box((7.6, 10.2, 10.4), (8.4, 11.6, 11.2), detail, rot=("x", -22.5, (8, 10.2, 10.8)))
    trigger_guard(m, 7.4, 10.0, metal)
    # cabo (madeira) com coronha de metal
    grip(m, 7.1, 8.9, 3.8, 8.2, 9.2, 11.2, wood, metal, (8, 8.0, 10.2))
    if gemm:
        for x in (6.95, 9.05):
            m.cbox(x, 6.0, 10.2, 0.3, 0.9, 0.9, gemm, rot=("x", GRIP_ROT, (8, 8.0, 10.2)))
    if t == 4:
        # asas douradas na armação (o revólver da Lenda do Meio-Dia)
        m.box((6.6, 8.4, 8.6), (7.0, 9.8, 10.6), AR.GOLD)
        m.box((9.0, 8.4, 8.6), (9.4, 9.8, 10.6), AR.GOLD)
    return m


def pistol_model(name, slide, frame, gripm, rune, gem, blade=None):
    """Pistola semiautomática (ferrolho, carregador no cabo); com 'blade' ganha uma lâmina sob o cano."""
    m = AR.Model(name, atlas=64)
    m.box((7.1, 8.8, 1.0), (8.9, 10.6, 10.8), slide)
    m.box((7.05, 9.0, 8.6), (8.95, 10.4, 10.2), AR.DARK_IRON)  # serrilha
    m.box((7.2, 8.0, 1.6), (8.8, 8.8, 10.4), frame)
    m.box((7.6, 9.3, 0.4), (8.4, 10.1, 1.0), AR.DARK_IRON)  # boca
    m.box((7.08, 9.5, 2.0), (8.92, 9.8, 7.5), rune)  # runa no ferrolho
    m.box((7.8, 10.6, 1.4), (8.2, 11.0, 2.0), frame)
    m.box((7.5, 10.6, 10.0), (8.5, 11.0, 10.6), frame)
    trigger_guard(m, 6.4, 9.0, frame)
    grip(m, 7.2, 8.8, 3.6, 8.0, 8.6, 10.8, gripm, slide, (8, 8.0, 9.7))
    m.cbox(8, 9.7, 11.0, 0.8, 0.8, 0.4, gem)
    if blade:
        m.box((7.75, 7.4, -1.4), (8.25, 8.0, 4.0), blade)
        m.box((7.8, 7.6, -2.2), (8.2, 8.0, -1.4), blade)
    return m


def shotgun_model():
    """Escopeta de cano duplo serrado: dois canos, telha de madeira, cães duplos, cabo de pistola."""
    m = AR.Model("mana_shotgun", atlas=64)
    metal, wood, rune = AR.DARK_IRON, AR.OAK, AR.RUNE_FIRE
    m.box((6.7, 9.3, -3.2), (8.0, 10.6, 8.0), metal)
    m.box((8.0, 9.3, -3.2), (9.3, 10.6, 8.0), metal)
    m.box((7.8, 10.6, -3.0), (8.2, 10.9, 8.0), AR.IRON)
    m.box((6.6, 9.2, -3.0), (9.4, 10.7, -2.2), rune)
    m.box((6.6, 9.2, 2.6), (9.4, 10.7, 3.0), rune)
    m.box((7.0, 8.2, -0.6), (9.0, 9.3, 5.2), wood)
    m.box((6.8, 8.0, 8.0), (9.2, 10.7, 11.0), metal)
    for x in (6.9, 8.5):
        m.box((x, 10.4, 10.2), (x + 0.6, 11.4, 10.8), AR.IRON, rot=("x", -22.5, (x + 0.3, 10.4, 10.5)))
    m.cbox(9.25, 9.3, 9.5, 0.3, 1.1, 1.1, AR.GEM_AMBER, rot=("x", 45, (9.25, 9.3, 9.5)))
    trigger_guard(m, 8.4, 10.6, metal)
    grip(m, 7.1, 8.9, 3.6, 8.4, 10.0, 12.6, wood, metal, (8, 8.2, 11.2))
    return m


def rifle_model():
    """Fuzil de alavanca com luneta: cano longo com freio de boca, carregador tubular, coronha inteira."""
    m = AR.Model("mana_rifle", atlas=64)
    metal, wood, rune = AR.DARK_IRON, AR.WALNUT, AR.RUNE_GREEN
    m.box((7.4, 9.6, -11.0), (8.6, 10.6, 6.0), metal)
    m.box((7.2, 9.4, -11.6), (8.8, 10.8, -10.6), GUNMETAL)
    m.box((7.35, 9.9, -8.0), (8.65, 10.3, -2.5), rune)
    m.box((7.5, 8.7, -6.0), (8.5, 9.6, 6.0), GUNMETAL)
    m.box((7.1, 8.4, -2.0), (8.9, 9.8, 5.0), wood)
    m.box((7.0, 8.0, 6.0), (9.0, 10.6, 10.0), metal)
    # luneta com lentes que brilham
    m.box((7.4, 11.2, 1.5), (8.6, 12.4, 9.0), AR.DARK)
    m.box((7.3, 11.1, 1.2), (8.7, 12.5, 2.0), GUNMETAL)
    m.cbox(8, 11.8, 1.1, 1.0, 1.0, 0.3, AR.GEM_GREEN)
    m.cbox(8, 11.8, 9.15, 0.9, 0.9, 0.3, GLOW_MINT)
    m.box((7.7, 10.6, 3.0), (8.3, 11.2, 3.8), metal)
    m.box((7.7, 10.6, 7.0), (8.3, 11.2, 7.8), metal)
    # alavanca e gatilho
    m.box((7.7, 6.4, 7.0), (8.3, 7.0, 10.6), GUNMETAL)
    m.box((7.7, 7.0, 6.8), (8.3, 8.0, 7.2), GUNMETAL)
    m.box((7.7, 7.0, 10.2), (8.3, 8.0, 10.6), GUNMETAL)
    m.box((7.8, 7.0, 8.4), (8.2, 8.0, 8.7), metal)
    # punho e coronha
    m.box((7.2, 5.6, 9.8), (8.8, 8.4, 12.4), wood, rot=("x", GRIP_ROT, (8, 8.2, 11.0)))
    m.box((7.1, 5.0, 12.0), (8.9, 9.0, 19.0), wood)
    m.box((7.0, 4.8, 19.0), (9.0, 9.2, 19.6), GUNMETAL)
    m.cbox(8.95, 7.0, 15.0, 0.25, 1.0, 1.0, AR.GEM_GREEN, rot=("x", 45, (8.95, 7.0, 15.0)))
    m.cbox(7.05, 7.0, 15.0, 0.25, 1.0, 1.0, AR.GEM_GREEN, rot=("x", 45, (7.05, 7.0, 15.0)))
    return m


GUN_DISPLAY = {
    # braço erguido (pose de mira): -Z do item = frente, +Y = cima. O cabo fica na mão (centro do modelo).
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1.6, -1.6], "scale": [0.8, 0.8, 0.8]},
    "firstperson_righthand": {"rotation": [0, 4, 0], "translation": [0, 1.5, -1.0], "scale": [0.7, 0.7, 0.7]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 6, 0], "scale": [0.6, 0.6, 0.6]},
}
RIFLE_DISPLAY = dict(GUN_DISPLAY)
RIFLE_DISPLAY.update({
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1.4, -2.4], "scale": [0.8, 0.8, 0.8]},
    "firstperson_righthand": {"rotation": [0, 4, 0], "translation": [0, 1.2, -2.0], "scale": [0.62, 0.62, 0.62]},
})


# ------------------------------------------------------------------ ícones 16x16 (de lado, cano para a direita)
OUT = (26, 20, 32, 255)


def P(mat):
    return AR.P(mat)


def icon_revolver(t):
    metal, wood, detail, rune, gemm = REVOLVER_LOOK[t]
    c = Canvas(16, 16)
    mh, ml, mm, md = P(metal)
    wh, wl, wm, wd = P(wood)
    dh, dl, dm, dd = P(detail)
    rh, rl, rm, rd = P(rune)
    # cano
    c.rect(7, 5, 14, 6, mm)
    c.rect(7, 5, 14, 5, ml)
    c.set(14, 4, dl)  # alça de mira
    c.rect(15, 5, 15, 6, dm)
    c.rect(8, 7, 12, 7, md)  # vareta
    # tambor e armação
    c.rect(4, 4, 7, 8, mm)
    c.rect(4, 4, 7, 4, ml)
    c.set(5, 6, rm)
    c.set(6, 6, rl)
    c.set(5, 5, md)
    c.set(5, 7, md)
    c.rect(2, 5, 4, 8, mm)
    c.rect(2, 5, 3, 5, ml)
    c.set(2, 4, dm)
    c.set(1, 3, dl)  # cão
    if t >= 2:
        c.set(9, 5, rl)
        c.set(10, 6, rm)
    # guarda-mato e gatilho
    for (x, y) in ((5, 9), (6, 10), (7, 10), (8, 9)):
        c.set(x, y, md)
    c.set(6, 9, mm)
    # cabo inclinado
    for y, (x0, x1) in zip(range(9, 14), ((2, 4), (1, 4), (1, 3), (0, 3), (0, 2))):
        for x in range(x0, x1 + 1):
            c.set(x, y, wl if x == x0 else (wd if x == x1 else wm))
    c.set(0, 14, mm)
    c.set(1, 14, md)
    if gemm:
        gh, gl_, gm, gd = P(gemm)
        c.set(2, 11, gm)
        c.set(1, 12, gl_)
    glow = [(5, 6, rl)]
    if t == 4:
        glow.append((14, 4, (255, 255, 255, 255)))
    return AR.finish(c, glow)


def icon_pistol(slide, frame, gripm, rune, gem, blade=None, ox=0, oy=0, c=None, finish=True):
    c = c or Canvas(16, 16)
    sh, sl, sm, sd = P(slide)
    fh, fl, fm, fd = P(frame)
    gh, gl_, gm, gd = P(gripm)
    rh, rl, rm, rd = P(rune)
    S = lambda x, y, col: c.set(x + ox, y + oy, col)  # noqa: E731
    for x in range(3, 14):
        S(x, 5, sl)
        S(x, 6, sm)
        S(x, 7, sd if x > 10 else sm)
    for x in range(5, 10):
        S(x, 6, rl if x % 2 else rm)
    S(14, 6, fd)
    S(13, 4, fl)
    S(3, 4, fl)
    for x in range(4, 12):
        S(x, 8, fm)
    for (x, y) in ((7, 9), (8, 10), (9, 10), (10, 9)):
        S(x, y, fd)
    S(8, 9, fm)
    for y, (x0, x1) in zip(range(9, 14), ((4, 6), (3, 6), (3, 5), (2, 5), (2, 4))):
        for x in range(x0, x1 + 1):
            S(x, y, gl_ if x == x0 else (gd if x == x1 else gm))
    S(2, 14, sm)
    S(3, 14, sd)
    eh, el, em, ed = P(gem)
    S(2, 6, em)
    if blade:
        bh, bl, bm, bd = P(blade)
        for x in range(10, 15):
            S(x, 9, bl if x < 14 else bh)
        S(15, 9, bh)
    if finish:
        return AR.finish(c, [(2 + ox, 6 + oy, el)])
    return c


def icon_dual():
    c = Canvas(16, 16)
    look = DUAL_LOOK
    icon_pistol(*look, ox=1, oy=-3, c=c, finish=False)
    back = [row[:] for row in c.px]
    c2 = Canvas(16, 16)
    icon_pistol(*look, ox=-1, oy=2, c=c2, finish=False)
    # a de trás fica mais escura; a da frente por cima
    for y in range(16):
        for x in range(16):
            if back[y][x][3] and not c2.px[y][x][3]:
                c2.px[y][x] = darken(back[y][x], 0.25)
    gh, gl_, gm, gd = P(look[4])
    return AR.finish(c2, [(1, 8, gl_)])


def icon_shotgun():
    c = Canvas(16, 16)
    mh, ml, mm, md = P(AR.DARK_IRON)
    wh, wl, wm, wd = P(AR.OAK)
    rh, rl, rm, rd = P(AR.RUNE_FIRE)
    c.rect(5, 4, 15, 4, ml)
    c.rect(5, 5, 15, 5, mm)
    c.rect(5, 6, 15, 6, md)
    c.rect(5, 7, 15, 7, mm)
    c.rect(14, 4, 14, 7, rl)
    c.rect(9, 4, 9, 7, rm)
    c.rect(7, 8, 12, 8, wm)
    c.rect(7, 8, 12, 8, wl)
    c.rect(2, 4, 5, 8, mm)
    c.rect(2, 4, 5, 4, ml)
    c.set(2, 3, ml)
    c.set(3, 3, mh)
    c.set(4, 7, P(AR.GEM_AMBER)[2])
    for (x, y) in ((4, 9), (5, 10), (6, 10), (7, 9)):
        c.set(x, y, md)
    for y, (x0, x1) in zip(range(9, 15), ((1, 3), (0, 3), (0, 3), (0, 2), (0, 2), (0, 1))):
        for x in range(x0, x1 + 1):
            c.set(x, y, wl if x == x0 else (wd if x == x1 else wm))
    return AR.finish(c, [(14, 5, rh)])


def icon_rifle():
    c = Canvas(16, 16)
    mh, ml, mm, md = P(AR.DARK_IRON)
    wh, wl, wm, wd = P(AR.WALNUT)
    gh, gl_, gm, gd = P(AR.GEM_GREEN)
    rh, rl, rm, rd = P(AR.RUNE_GREEN)
    c.rect(6, 7, 15, 7, mm)
    c.rect(6, 6, 15, 6, ml)
    c.set(15, 5, ml)
    c.rect(10, 6, 13, 6, rl)
    c.rect(7, 8, 12, 8, wm)
    c.rect(4, 6, 6, 9, mm)
    c.rect(4, 6, 6, 6, ml)
    # luneta
    c.rect(5, 3, 11, 4, md)
    c.rect(5, 3, 11, 3, mm)
    c.set(12, 3, gl_)
    c.set(12, 4, gm)
    c.set(4, 3, gm)
    c.set(6, 5, md)
    c.set(10, 5, md)
    # alavanca
    for (x, y) in ((5, 10), (6, 11), (7, 10)):
        c.set(x, y, md)
    # coronha
    for y, (x0, x1) in zip(range(7, 13), ((0, 4), (0, 4), (0, 3), (0, 3), (0, 2), (0, 1))):
        for x in range(x0, x1 + 1):
            c.set(x, y, wl if y == 7 else (wd if x == x1 else wm))
    return AR.finish(c, [(12, 3, gh)])


PISTOL_LOOK = (BLUED, AR.STEEL, AR.DARK_IRON, AR.RUNE_BLUE, GLOW_CYAN)
DUAL_LOOK = (NICKEL, AR.DARK_IRON, AR.VIOLET_WRAP, AR.RUNE_VIOLET, GLOW_LILAC)


def gen_guns():
    items = []
    for t in range(5):
        items.append((REVOLVER_IDS[t], revolver_model(t), icon_revolver(t), GUN_DISPLAY))
    items.append(("mana_pistol", pistol_model("mana_pistol", *PISTOL_LOOK), icon_pistol(*PISTOL_LOOK), GUN_DISPLAY))
    items.append(("mana_dual_pistols", pistol_model("mana_dual_pistols", *DUAL_LOOK, blade=AR.STEEL_BLADE), icon_dual(),
                  GUN_DISPLAY))
    items.append(("mana_shotgun", shotgun_model(), icon_shotgun(), GUN_DISPLAY))
    items.append(("mana_rifle", rifle_model(), icon_rifle(), RIFLE_DISPLAY))
    for iid, model, icon, disp in items:
        tex = NS + ":item/class_gunslinger/" + iid
        held, atlas = model.build(tex, seed=zlib.crc32(iid.encode()) & 0xFFFF)
        held["display"] = disp
        atlas.save(os.path.join(A, "textures", "item", "class_gunslinger", iid + ".png"))
        write_json(os.path.join(A, "models", "item", "class_gunslinger", iid + "_held.json"), held)
        icon.save(os.path.join(A, "textures", "item", iid + ".png"))
        flat = {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + iid}}
        write_json(os.path.join(A, "models", "item", iid + ".json"), {
            "loader": "neoforge:separate_transforms",
            "gui_light": "front",
            "textures": {"particle": NS + ":item/" + iid},
            "base": {"parent": NS + ":item/class_gunslinger/" + iid + "_held"},
            "perspectives": {"gui": flat, "fixed": flat},
        })
    print("  armas: %d modelos 3D + icones" % len(items))


# =====================================================================================================
#  TRAJE DO PISTOLEIRO (4 graus, GeckoLib, motor do outfits)
# =====================================================================================================
# I Forasteiro (couro e bandana vermelha) · II Caçador de Recompensas (guarda-pó cinza, prata e azul)
# III Xerife Rubro (casaco carmesim, chapéu branco, ouro) · IV Lenda do Meio-Dia (preto e ouro, serape âmbar, brasas)
TIERS = {
    "apprentice": dict(
        name="outfit_gunslinger", lv=0,
        T=OF.theme(pal("2E1E10", "4A3220", "6A4A2E", "8A6440", "AC8458"), pal("4A0E0C", "7A1A14", "A82A20", "D04434", "F07A60"),
                   OF.BRASS, OF.LEATHER_STD, OF.CREAM, OF.BRASS, pal("5A3004", "A0600A", "FFB22A", "FFE8A8"), "FFB22A"),
        HAT=pal("2A1A0E", "45301C", "64462A", "80603A", "A07E50"),
        TROUSERS=pal("141C2A", "202C40", "2E3E58", "425674", "5E7494")),
    "adept": dict(
        name="outfit_gunslinger_adept", lv=1,
        T=OF.theme(pal("16161A", "26262C", "38383F", "4E4E58", "6A6A76"), pal("10202E", "1C3448", "2A4C66", "3E6A88", "6A94B0"),
                   OF.SILVER_M, OF.LEATHER_DARK, OF.IVORY, OF.SILVER_M, pal("2A3A5A", "4A6AA0", "BFD8FF", "F0F6FF"), "8FD8FF"),
        HAT=pal("101012", "1C1C20", "2A2A30", "3A3A42", "4E4E58"),
        TROUSERS=pal("1A140E", "2A2018", "3C2E22", "524030", "6A5440")),
    "master": dict(
        name="outfit_gunslinger_master", lv=2,
        T=OF.theme(pal("3A0C0A", "5E1612", "862420", "A83A30", "C85A48"), pal("6A6258", "948A7C", "BEB4A4", "DCD4C4", "F6F0E4"),
                   OF.GOLD_R, pal("2A140C", "421E12", "5E2C1A", "7A3C24", "9A5232"), OF.CREAM, OF.GOLD_R,
                   pal("5A0A04", "B02A0A", "FF5A3A", "FFC8B0"), "FF5A3A"),
        HAT=pal("8A8478", "B4AE9E", "D8D2C2", "F0ECE0", "FFFFFA"),
        TROUSERS=pal("1E1410", "2E201A", "423028", "5A4436", "765A48")),
    "archmage": dict(
        name="outfit_gunslinger_archmage", lv=3,
        T=OF.theme(pal("0C0A0C", "18141A", "241E28", "342C3A", "48404E"), pal("3A1A04", "6A3008", "A0500E", "D07A1E", "F8A848"),
                   OF.GOLD_B, OF.LEATHER_DARK, pal("5A4A30", "8A7650", "B8A274", "DCCA9C", "F8ECCC"), OF.GOLD_B,
                   pal("5A3004", "B06A0A", "FFB22A", "FFECB0"), "FFB22A"),
        HAT=pal("08070A", "121016", "1E1A24", "2C2634", "403848"),
        TROUSERS=pal("0E0C10", "1A161C", "28222C", "38303E", "4C4252")),
}
PIECES = ("helmet", "chestplate", "leggings", "boots")


def gs_mats(spec):
    th, lv = spec["T"], spec["lv"]
    P_ = th["pal"]
    Mt = {}
    MAIN, SEC, TRIM, LEATH, ACC = OF.v9_common(Mt, P_, th, lv)
    GL, GEM = th["GLOW"], th["GEM"]
    HAT, TR = spec["HAT"], spec["TROUSERS"]
    lit = OF.put  # put(r, lit) -> r.glow se brilha, senão r.set

    # ---- chapéu
    def felt(r, base=2):
        r.fill(HAT[base])
        for j in range(r.h):
            for i in range(r.w):
                x = r.rnd.random()
                if x < 0.08:
                    r.set(i, j, HAT[base - 1])
                elif x < 0.12:
                    r.set(i, j, HAT[base + 1])
        r.vshade(0.06, 0.14)

    Mt["hat"] = lambda r: felt(r)
    Mt["hat_top"] = lambda r: (felt(r, 3), r.col(r.w // 2, HAT[1]) if r.w > 2 else None)
    Mt["brim_top"] = lambda r: (felt(r, 2), r.row(0, HAT[3]), r.row(-1, HAT[1]))
    Mt["brim_under"] = lambda r: (r.fill(HAT[1]), r.noise(0.03))
    Mt["brim_edge"] = lambda r: (r.fill(HAT[2]), r.row(0, HAT[3]), r.noise(0.03))

    def band(r):
        r.fill(LEATH[1] if lv != 2 else MAIN[2])
        r.row(0, LEATH[3] if lv != 2 else MAIN[3])
        for k, i in enumerate(range(1, r.w - 1, 4)):
            j = r.h // 2
            # conchas de metal (graus II+ brilham)
            lit(r, lv >= 1)(i, j, TRIM[3] if lv == 0 else (GL if k % 2 == 0 else lighten(GL, 0.3)))
            if i + 1 < r.w:
                r.set(i + 1, j, TRIM[2])
        r.noise(0.02)

    Mt["hat_band"] = band

    def plume(r):
        F = pal("2A2218", "4A3C2A", "7A6448", "AA9070", "DCC8A8") if lv < 3 else pal("4A2A04", "8A5A10", "D09A2E", "F2C860", "FFF0B8")
        OF.feather(r, F, tip=GL if lv >= 2 else None, lit=lv >= 2)

    Mt["plume"] = plume

    # ---- casaco/guarda-pó
    def coat(r, kind):
        OF.leather(r, MAIN, seams=False, speckle=0.06)
        r.folds(MAIN[1], MAIN[3], 4)
        if kind == "front":
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx - 1, j, MAIN[0])  # abertura do guarda-pó
                r.set(cx, j, ACC[2] if j < r.h // 2 else MAIN[1])  # camisa aparecendo na gola
            for j in range(2, r.h - 2, 3):
                r.set(cx - 2, j, TRIM[3])
            if lv >= 2:
                for i in range(1, r.w - 1):
                    r.set(i, r.h - 2, TRIM[2])
        if lv >= 3 and kind == "back":
            cx = r.w // 2
            OF.rune_glyph(r, cx - 1, 2, GL, 2)

    Mt["coat_front"] = lambda r: coat(r, "front")
    Mt["coat"] = lambda r: coat(r, "side")
    Mt["coat_back"] = lambda r: coat(r, "back")
    Mt["coat_in"] = lambda r: (r.fill(MAIN[0]), r.noise(0.03))
    Mt["collar"] = lambda r: (OF.leather(r, MAIN, base=3, seams=False), r.col(0, MAIN[4]))

    def coat_tail(r):
        OF.leather(r, MAIN, seams=False, speckle=0.06)
        r.folds(MAIN[1], MAIN[3], 4)
        OF.trim_rows(r, TRIM, 1 if lv < 2 else 2)
        if lv >= 1 and r.h >= 8:
            # runas que brilham perto da barra (graus II+)
            for k, i in enumerate(range(1, r.w - 2, 4)):
                OF.rune_glyph(r, i, r.h - 7, GL, k + lv)

    Mt["coat_tail"] = coat_tail
    Mt["sleeve"] = lambda r: (OF.leather(r, MAIN, seams=False, speckle=0.06), r.folds(MAIN[1], MAIN[3], 3))

    def glove(r):
        OF.leather(r, LEATH, base=2, seams=False)
        r.row(0, LEATH[3])
        if lv >= 1:
            for i in range(1, r.w - 1, 3):
                r.set(i, 1, TRIM[3])

    Mt["glove"] = glove

    # ---- bandoleira e cinturão com cartuchos
    def cartridges(r, horizontal):
        r.fill(LEATH[1])
        if horizontal:
            r.row(0, LEATH[3])
            if r.h > 2:
                r.row(-1, LEATH[0])
        else:
            r.col(0, LEATH[3])
            if r.w > 2:
                r.col(-1, LEATH[0])
        n = r.w if horizontal else r.h
        for k in range(1, n - 1, 2):
            i, j = (k, r.h // 2) if horizontal else (r.w // 2, k)
            r.set(i, j, TRIM[3])
            # ponta da bala: brilha nos graus II+
            if horizontal:
                lit(r, lv >= 1)(i, max(0, j - 1), GL if lv >= 1 else TRIM[4])
            else:
                lit(r, lv >= 1)(min(r.w - 1, i + 1), j, GL if lv >= 1 else TRIM[4])
        r.noise(0.02)

    Mt["bandolier"] = lambda r: cartridges(r, False)
    Mt["gunbelt"] = lambda r: cartridges(r, True)

    def buckle(r):
        cartridges(r, True)
        cx = r.w // 2
        for j in range(r.h):
            r.set(cx - 1, j, TRIM[3])
            r.set(cx, j, TRIM[4] if j == 0 else TRIM[2])
            r.set(cx + 1, j, TRIM[1])

    Mt["gunbelt_front"] = buckle

    # ---- bandana/lenço e estrela
    def scarf(r):
        r.fill(SEC[2])
        for j in range(r.h):
            for i in range(r.w):
                if (i + j * 2) % 4 == 0:
                    r.set(i, j, SEC[4] if (i // 2 + j) % 2 else ACC[3])
        r.row(0, SEC[3])
        r.noise(0.03)

    Mt["scarf"] = scarf

    def badge(r):
        if lv == 0:
            r.fill(TRIM[2])
            r.set(0, 0, TRIM[4])
            return
        for j in range(r.h):
            for i in range(r.w):
                r.glow(i, j, lighten(GL, 0.25) if (i + j) % 2 == 0 else TRIM[3])
        r.glow(0, 0, (255, 255, 255, 255))

    Mt["badge"] = badge

    # ---- poncho/serape (outfitCape)
    def serape(r, inner=False):
        if inner:
            r.fill(SEC[0])
            r.noise(0.03)
            return
        stripes = [SEC[2], SEC[3], SEC[2], TRIM[2], SEC[1], ACC[3], SEC[1], TRIM[2]]
        OF.stripes_h(r, stripes, period=2)
        if lv >= 2:
            cx = r.w // 2
            for k, j in enumerate(range(3, r.h - 4, 6)):
                OF.rune_glyph(r, cx - 1, j, GL, k + 1)
        # franja na barra
        for i in range(r.w):
            if i % 2:
                r.clear(i, r.h - 1)

    Mt["poncho_out"] = lambda r: serape(r)
    Mt["poncho_in"] = lambda r: serape(r, True)
    Mt["poncho_edge"] = lambda r: (r.fill(SEC[1]), r.noise(0.03))

    # ---- pernas, coldre, botas e esporas
    Mt["trousers"] = lambda r: OF.cloth(r, TR, period=4, base=2)

    def chaps(r):
        OF.leather(r, LEATH, base=2, seams=False)
        for j in range(1, r.h, 3):
            r.set(0, j, LEATH[3])
            r.set(r.w - 1, j, LEATH[3])
        if lv >= 2:
            for j in range(2, r.h - 1, 4):
                r.set(r.w // 2, j, TRIM[3])

    Mt["chaps"] = chaps
    Mt["fringe"] = lambda r: ([r.set(i, j, LEATH[3] if (i + j) % 2 else LEATH[2]) for j in range(r.h) for i in range(r.w)],
                              [r.clear(i, r.h - 1) for i in range(0, r.w, 2)])

    def holster(r):
        OF.leather(r, LEATH, base=1, seams=False)
        for j in range(1, r.h - 1, 2):
            r.set(0, j, LEATH[3])  # costura
        if lv >= 1:
            lit(r, lv >= 1)(r.w // 2, 1, GEM[2])

    Mt["holster"] = holster
    Mt["gun_grip"] = lambda r: (r.fill(HAT[3] if lv == 2 else LEATH[3]), r.row(0, TRIM[3]), r.noise(0.04))
    Mt["boot"] = lambda r: (OF.leather(r, LEATH, seams=False), r.row(-1, LEATH[0]))

    def boot_top(r):
        r.fill(LEATH[3])
        r.row(0, LEATH[4])
        for i in range(1, r.w - 1, 2):
            r.set(i, r.h - 1, TRIM[2] if lv >= 1 else LEATH[2])
        r.noise(0.03)

    Mt["boot_top"] = boot_top
    Mt["spur"] = lambda r: (r.fill(TRIM[3]), r.set(0, 0, TRIM[4]), r.noise(0.02))

    def rowel(r):
        for j in range(r.h):
            for i in range(r.w):
                if (i + j) % 2 == 0:
                    lit(r, lv >= 2)(i, j, GL if lv >= 2 else TRIM[4])
                else:
                    r.set(i, j, TRIM[2])

    Mt["rowel"] = rowel
    return Mt


def gs_model(spec):
    lv = spec["lv"]
    m = OF.Model(spec["name"])
    OF.skeleton(m)
    # ---------------- chapéu de aba larga (abas laterais levantadas), copa vincada, faixa com conchas, pena
    m.box(HD, [-6.6, 30.6, -6.8], [13.2, 0.6, 13.6], fc(side="brim_edge", u="brim_top", d="brim_under@4x4"))
    for x0, rz in ((-7.9, 32), (6.7, -32)):
        m.box(HD, [x0, 30.7, -5.6], [1.2, 0.5, 11.2], fc(side="brim_edge", u="brim_top@2x6", d="brim_under@2x6"),
              rot=[0, 0, rz], pivot=[x0 + (1.2 if rz > 0 else 0), 30.9, 0])
    m.box(HD, [-3.9, 31.1, -4.2], [7.8, 3.7, 8.4], fc(side="hat", u="hat_top", d=OMIT))
    m.box(HD, [-3.2, 34.8, -3.4], [6.4, 0.5, 6.8], fc(side="hat@4x1", u="hat_top", d=OMIT))
    m.box(HD, [-3.9, 31.1, -4.2], [7.8, 1.0, 8.4], fc(side="hat_band", u=OMIT, d=OMIT), inflate=0.15)
    if lv >= 1:
        OF.gem(m, HD, [0, 31.6, -4.5], [1.1, 1.1, 0.3], "badge@2x2")
    m.bone("hatMid", [3.9, 31.6, 0.6], parent=HD)
    p = m.seg("hatMid", [3.9, 31.6, 0.6], (0.35, 1, 0.6), 3.0 + 0.4 * lv, 0.45,
              fc(ew="plume@3x6", ns="plume@1x6", u=OMIT, d=OMIT), tz=1.3)
    if lv >= 2:
        m.bone("hatTip", p, parent="hatMid", rot=[15, 0, 0])
        m.seg("hatTip", p, (0.1, 0.6, 1), 1.8, 0.4, fc(ew="plume@3x4", ns="plume@1x4", u=OMIT, d=OMIT), tz=1.1,
              overlap=0.2)

    # ---------------- tronco: guarda-pó, lapelas, bandana, bandoleira, estrela, cinturão de cartuchos
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="coat_front", s="coat_back", ew="coat", u="coat", d="dark@2x2"), inflate=0.55)
    for x0, rz in ((-3.0, -8), (0.9, 8)):
        m.box(BD, [x0, 17.2, -3.05], [2.1, 6.4, 0.45], fc(n="collar", s="coat_in", ew="collar@1x6", u="collar@2x1", d=OMIT),
              rot=[0, 0, rz], pivot=[x0 + 1.05, 23.6, -2.8])
    m.box(BD, [-4.4, 21.9, -2.6], [8.8, 2.4, 5.2], fc(side="scarf", u="scarf@4x2", d=OMIT))
    m.box(BD, [-1.8, 19.0, -2.95], [3.6, 3.0, 0.4], fc(n="scarf@3x3", s="scarf@3x3", ew="scarf@1x3", u=OMIT, d=OMIT))
    OF.strap_diag(m, BD, "bandolier", -2.75, 1, width=1.4, edge="strap")
    OF.gem(m, BD, [2.4, 20.0, -2.95], [1.4, 1.4, 0.35], "badge@2x2")
    m.box(BD, [-4, 11.4, -2], [8, 1.6, 4], fc(n="gunbelt_front", s="gunbelt", ew="gunbelt", u="leather_top@2x2",
                                             d="leather_top@2x2"), inflate=0.95)
    # abas do guarda-pó (abertas na frente; o renderer as abre com o passo)
    OF.waist(m, "Chest")
    bottom = {0: 5.0, 1: 3.0, 2: 2.0, 3: 0.6}[lv]
    hgt = 12.0 - bottom
    for x0 in (-4.7, 0.5):
        m.box("chestSkirtFront", [x0, bottom, -3.15], [4.2, hgt, 0.5],
              fc(n="coat_tail", s="coat_in", ew="coat_in@1x6", u=OMIT, d="coat_in@4x1"), rot=[-4, 0, 0], pivot=[0, 12, -2.75])
    m.box("chestSkirtBack", [-4.7, bottom, 2.65], [9.4, hgt, 0.5],
          fc(s="coat_tail", n="coat_in", ew="coat_in@1x6", u=OMIT, d="coat_in@4x1"), rot=[4, 0, 0], pivot=[0, 12, 2.75])
    m.box("chestSkirtRight", [-5.25, bottom, -2.4], [0.5, hgt, 4.8],
          fc(e="coat_in", w="coat_tail", ns="coat_in@1x6", u=OMIT, d="coat_in@1x4"), rot=[0, 0, 4], pivot=[-5.0, 12, 0])
    m.mirror("chestSkirtRight", "chestSkirtLeft")
    # costas: lenço solto (I), poncho curto (II), serape (III), serape longa com runas (IV)
    if lv == 0:
        m.bone("outfitCape", [0, 23.6, 2.7], parent=BD, rot=[8, 0, 0])
        for x0, low, rz in ((-1.9, 18.0, -7), (0.2, 19.0, 9)):
            m.box("outfitCape", [x0, low, 2.7], [1.7, 23.6 - low, 0.3],
                  fc(s="scarf", n="scarf", ew="scarf@1x4", u=OMIT, d=OMIT), rot=[0, 0, rz], pivot=[x0 + 0.85, 23.6, 2.85])
    else:
        OF.cape(m, 24.2, {1: 15.0, 2: 11.0, 3: 6.0}[lv], 2.9, 9.4, out="poncho_out", inn="poncho_in", edge="poncho_edge",
                flare=5, thick=0.6)

    # ---------------- braços: mangas, luvas de couro, ombreiras de serape (III-IV)
    m.box(RA, [-8, 13.5, -2], [4, 10.5, 4], fc(side="sleeve", u="coat@2x2", d=OMIT), inflate=0.5)
    m.box(RA, [-8, 12, -2], [4, 2.2, 4], fc(side="glove", u="leather_top@2x2", d="dark@2x2"), inflate=0.7)
    m.mirror(RA, LA)
    if lv >= 2:
        OF.pauldrons(m, 1 if lv == 2 else 2, side="poncho_out", top="poncho_out", under="o_dark@2x2",
                     gem_mat="badge@2x2" if lv == 3 else None, gem_size=0.9)

    # ---------------- pernas: calça, perneiras com franja (II+), coldre com revólver nas duas coxas
    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    if lv >= 1:
        m.box(RL, [-3.9, 1.5, -2], [4, 9.5, 4], fc(side="chaps", u=OMIT, d=OMIT), inflate=0.5)
        m.box(RL, [-4.75, 2.0, -1.6], [0.35, 8.5, 3.2], fc(ew="fringe@3x8", ns="fringe@1x8", u=OMIT, d=OMIT))
    m.box(RL, [-4.9, 5.6, -1.3], [1.0, 5.2, 2.6], fc(side="holster", u="leather_top@1x2", d="sole@1x2"))
    m.box(RL, [-4.8, 10.6, -0.7], [0.8, 1.7, 1.2], fc(side="gun_grip", u="gun_grip@1x1", d=OMIT), rot=[-15, 0, 0],
          pivot=[-4.4, 10.6, 0])
    m.box(RL, [-3.9, 6.6, -2], [4, 0.6, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.62)
    m.mirror(RL, LL)

    # ---------------- botas de cano alto com bico, salto e espora
    m.box(RB, [-3.9, 0, -2], [4, 5.6, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.6)
    m.box(RB, [-3.9, 5.0, -2], [4, 1.2, 4], fc(side="boot_top", u="dark@2x2", d="dark@2x2"), inflate=0.85)
    m.box(RB, [-3.6, -0.6, -3.5], [3.4, 1.6, 1.6], fc(n="boot", ew="boot", u="boot", d="sole@2x2", s=OMIT))
    m.box(RB, [-3.3, -0.6, 1.9], [2.8, 1.2, 0.9], fc(side="sole@2x1", u="sole@2x1", d="sole@2x1"))
    m.box(RB, [-2.1, 1.1, 2.4], [0.4, 0.4, 1.3], fc(all="spur@1x1"))
    m.box(RB, [-2.05, 0.65, 3.5], [0.3, 1.3, 1.3], fc(ew="rowel@2x2", ns="spur@1x1", u="spur@1x1", d="spur@1x1"))
    m.mirror(RB, LB)
    return m


# ------------------------------------------------------------------ ícones das peças
HAT_ROWS = [
    "................",
    "................",
    "................",
    "......oooo......",
    ".....oLllmo.....",
    "....oLlkklmo....",
    "....olllllmo....",
    "....oggxggmo....",
    "..ooolllllmooo..",
    ".oLo.olllmo.omo.",
    "oLlmoooooooomlmo",
    "olllllllllllllmo",
    ".ooomlllllllmooo",
    "....oooooooo....",
    "................",
    "................",
]


def piece_icon(tkey, piece):
    spec = TIERS[tkey]
    th, lv = spec["T"], spec["lv"]
    P_ = th["pal"]
    MAIN, SEC, TRIM, LEATH, ACC = (P_[k] for k in ("MAIN", "SEC", "TRIM", "LEATH", "ACC"))
    HAT, TR = spec["HAT"], spec["TROUSERS"]
    G = th["GEM"]
    out = darken(min((MAIN[0], LEATH[0], HAT[0]), key=lambda c: sum(c[:3])), 0.5)
    c = Canvas(16, 16)
    if piece == "helmet":
        cols = {"o": out, "k": HAT[1], "l": HAT[2], "L": HAT[4], "m": HAT[1], "g": LEATH[2] if lv != 2 else MAIN[2],
                "x": G[2] if lv >= 1 else TRIM[3]}
        c.from_rows(HAT_ROWS, cols)
    elif piece == "chestplate":
        mask = OF.V9_CHEST_MASK
        for y, row in enumerate(mask):
            for x, ch in enumerate(row):
                if ch == "o":
                    c.set(x, y, out)
                elif ch == "S":
                    a = OF._arm(x)
                    c.set(x, y, MAIN[3] if a <= 1 else MAIN[2])
                    if y >= 7 and y <= 8:
                        c.set(x, y, LEATH[2])  # luva
                elif ch == "T":
                    col = MAIN[2] if x < 8 else MAIN[1]
                    if x in (7, 8):
                        col = ACC[2] if y <= 6 else MAIN[0]
                    if y <= 4 and 5 <= x <= 10:
                        col = SEC[2] if (x + y) % 2 else SEC[3]  # bandana
                    # bandoleira em diagonal (ombro direito -> quadril esquerdo)
                    if abs((x - 5) - (y - 4) * 0.7) < 0.6 and y >= 4:
                        col = TRIM[3] if y % 2 else LEATH[1]
                    if y == 11:
                        col = TRIM[3] if x in (7, 8) else LEATH[2]
                    c.set(x, y, col)
        if lv >= 1:
            c.set(10, 6, G[3])
            c.set(10, 5, G[2])
    elif piece == "leggings":
        mask = OF.V9_LEGS_MASK
        for y, row in enumerate(mask):
            for x, ch in enumerate(row):
                if ch == "o":
                    c.set(x, y, out)
                elif ch == "B":
                    c.set(x, y, TRIM[3] if x in (7, 8) else (TRIM[2] if x % 2 else LEATH[1]))
                elif ch == "P":
                    col = TR[2] if (x in (3, 4, 11, 12)) else TR[1]
                    if lv >= 1 and x in (2, 3, 12, 13) and y >= 3:
                        col = LEATH[2]
                    c.set(x, y, col)
        # coldres com o cabo do revólver
        for (x, y) in ((1, 4), (1, 5), (1, 6), (14, 4), (14, 5), (14, 6)):
            c.set(x, y, LEATH[1])
        c.set(1, 3, LEATH[3])
        c.set(14, 3, LEATH[3])
    else:
        mask = OF.V9_BOOTS_MASK
        for y, row in enumerate(mask):
            for x, ch in enumerate(row):
                if ch == "o":
                    c.set(x, y, out)
                elif ch == "C":
                    c.set(x, y, LEATH[3])
                elif ch == "K":
                    c.set(x, y, LEATH[2] if x % 4 else LEATH[1])
                elif ch == "S":
                    c.set(x, y, LEATH[0])
        # esporas atrás do salto
        for x in (6, 9):
            c.set(x, 13, TRIM[3])
        c.set(7, 14, G[2] if lv >= 2 else TRIM[4])
        c.set(8, 14, G[2] if lv >= 2 else TRIM[4])
    if lv == 3:
        OF.sparkle(c, (14, 1), lighten(th["GLOW"], 0.2))
        OF.sparkle(c, (1, 14), lighten(th["GLOW"], 0.2))
    elif lv >= 1:
        OF.tier_pip(c, (14, 1), G)
        if lv == 2:
            OF.tier_pip(c, (1, 14), G)
    item_id = ("gunslinger_" + piece) if lv == 0 else ("gunslinger_%s_%s" % (tkey, piece))
    c.save(os.path.join(A, "textures", "item", item_id + ".png"))
    item_model(A, item_id)


def gen_outfit():
    for tkey, spec in TIERS.items():
        _, glow = OF.build(gs_model(spec), gs_mats(spec))
        want = spec["lv"] >= 1
        if want and not glow:
            raise RuntimeError("%s: os graus II-IV precisam de glowmask (GsOutfitTier.glows)" % spec["name"])
        if not want:
            gp = os.path.join(A, "textures", "item", "armor", spec["name"] + "_glowmask.png")
            if os.path.exists(gp):
                os.remove(gp)
        P_ = spec["T"]["pal"]
        OF.vanilla_layers(spec["name"][len("outfit_"):], P_["MAIN"][2], P_["TRIM"][2], P_["MAIN"][0])
        for piece in PIECES:
            piece_icon(tkey, piece)
    print("  traje: 4 graus, 16 icones")


# =====================================================================================================
#  FORASTEIRO ESPECTRAL (GeckoLib) — base (azul-fantasma) e grau II "Ceifador do Meio-Dia" (vermelho, caveira)
# =====================================================================================================
OUTLAW_LOOKS = {
    "gunslinger_outlaw": dict(
        COAT=pal("1A2230", "28344A", "3A4C66", "52688A", "7088AA"), PONCHO=pal("2A3A4A", "3E566E", "5A7A96", "84A4BC", "BCD8EA"),
        HAT=pal("141A22", "202A36", "2E3C4C", "425468", "5E7488"), SKIN=pal("0A0E14", "141C26", "1E2A38", "2C3C50", "40546C"),
        EYE=H("9AF4FF"), RUNE=H("8FD8FF"), METAL=pal("3A4250", "5E6878", "8A96A8", "B8C4D4", "E6EEF8"),
        BANDANA=pal("2A3A50", "3A5070", "4E6A90", "6A88B0", "9AB4D4")),
    "gunslinger_outlaw_reaper": dict(
        COAT=pal("1A0808", "2E0E0E", "481616", "662220", "8A3430"), PONCHO=pal("4A0A0A", "761410", "A8241C", "D04434", "F07A60"),
        HAT=pal("0C0808", "180E0E", "261616", "382020", "4E2E2C"), SKIN=pal("8A8070", "B0A690", "D2C8B0", "ECE4CE", "FFFAEA"),
        EYE=H("FF4A3A"), RUNE=H("FF7A3A"), METAL=pal("3A2A20", "6A4A2A", "A07830", "D8B048", "FFE890"),
        BANDANA=pal("1A0A0A", "2A1010", "3A1818", "4E2222", "6A3030")),
}


def outlaw_mats(L, reaper):
    C, PO, HT, SK, MT, BN = L["COAT"], L["PONCHO"], L["HAT"], L["SKIN"], L["METAL"], L["BANDANA"]
    EYE, RUNE = L["EYE"], L["RUNE"]
    Mt = {}

    def face(r):
        r.fill(SK[1])
        r.vshade(0.1, 0.2)
        # olhos que brilham embaixo da aba (grau II: órbitas de caveira)
        for x in (1, r.w - 3):
            if reaper:
                r.set(x, 3, SK[0]); r.set(x + 1, 3, SK[0]); r.set(x, 4, SK[0]); r.set(x + 1, 4, SK[0])
                r.glow(x, 3, EYE)
            else:
                r.glow(x, 3, EYE); r.glow(x + 1, 3, lighten(EYE, 0.4))
        if reaper:
            r.set(r.w // 2, 5, SK[0])
            r.set(r.w // 2 - 1, 5, SK[0])

    Mt["face"] = face
    Mt["head"] = lambda r: (r.fill(SK[1]), r.noise(0.03))

    def bandana(r):
        r.fill(BN[2])
        for j in range(r.h):
            for i in range(r.w):
                if (i + 2 * j) % 5 == 0:
                    r.set(i, j, BN[4])
        r.row(0, BN[3])
        r.noise(0.03)

    Mt["bandana"] = bandana
    Mt["hat"] = lambda r: (r.fill(HT[2]), r.vshade(0.06, 0.14), r.noise(0.04))
    Mt["hat_top"] = lambda r: (r.fill(HT[3]), r.col(r.w // 2, HT[1]) if r.w > 2 else None, r.noise(0.03))
    Mt["brim"] = lambda r: (r.fill(HT[2]), r.row(0, HT[3]), r.noise(0.03))
    Mt["brim_under"] = lambda r: (r.fill(HT[0]), r.noise(0.02))

    def band(r):
        r.fill(HT[1] if not reaper else MT[1])
        for i in range(1, r.w - 1, 3):
            if reaper:
                r.set(i, r.h // 2, SK[3])  # dentinhos de osso
            else:
                r.glow(i, r.h // 2, RUNE)
        r.noise(0.02)

    Mt["hat_band"] = band

    def coat(r, front=False):
        r.fill(C[2])
        r.folds(C[1], C[3], 4)
        r.vshade(0.08, 0.16)
        r.noise(0.04)
        if front:
            cx = r.w // 2
            for j in range(r.h):
                r.set(cx, j, C[0])
            for j in range(1, r.h - 1, 3):
                r.set(cx - 1, j, MT[3])

    Mt["coat"] = lambda r: coat(r)
    Mt["coat_front"] = lambda r: coat(r, True)

    def coat_tail(r):
        coat(r)
        for i in range(r.w):
            if r.rnd.random() < 0.5:
                r.clear(i, r.h - 1)  # barra puída (é um fantasma)
        r.row(r.h - 3, C[1])

    Mt["coat_tail"] = coat_tail

    def poncho(r):
        OF.stripes_h(r, [PO[2], PO[3], PO[2], PO[1], PO[4], PO[1]], period=2)
        cx = r.w // 2
        for j in range(2, r.h - 2, 4):
            r.glow(cx, j, RUNE)
        for i in range(0, r.w, 2):
            r.clear(i, r.h - 1)

    Mt["poncho"] = poncho
    Mt["poncho_top"] = lambda r: (r.fill(PO[2]), r.noise(0.04))
    Mt["poncho_in"] = lambda r: (r.fill(PO[0]), r.noise(0.03))
    Mt["sleeve"] = lambda r: (r.fill(C[2]), r.folds(C[1], C[3], 3), r.noise(0.04))
    Mt["glove"] = lambda r: (r.fill(HT[1]), r.row(0, HT[3]), r.noise(0.03))
    Mt["belt"] = lambda r: ([r.set(i, j, MT[3] if (i % 2 and j == r.h // 2) else HT[1]) for j in range(r.h) for i in range(r.w)],
                            r.row(0, HT[3]))
    Mt["buckle"] = lambda r: (r.fill(MT[3]), r.row(0, MT[4]), r.set(r.w // 2, r.h // 2, MT[1]))
    Mt["trousers"] = lambda r: (r.fill(C[1]), r.folds(C[0], C[2], 4), r.noise(0.04))
    Mt["boot"] = lambda r: (r.fill(HT[1]), r.row(0, HT[3]), r.row(-1, HT[0]), r.noise(0.04))
    Mt["sole"] = lambda r: (r.fill(HT[0]), r.noise(0.02))
    Mt["holster"] = lambda r: (r.fill(HT[2]), r.col(0, HT[3]), r.noise(0.03))
    Mt["spur"] = lambda r: (r.fill(MT[3]), r.glow(0, 0, RUNE))
    Mt["gun_metal"] = lambda r: (r.fill(MT[2]), r.row(0, MT[4]) if r.h > 1 else None, r.col(-1, MT[1]) if r.w > 1 else None,
                                 r.noise(0.03))
    Mt["gun_dark"] = lambda r: (r.fill(MT[0]), r.noise(0.03))
    Mt["gun_grip"] = lambda r: (r.fill(SK[3] if reaper else HT[3]), r.noise(0.05))
    Mt["gun_rune"] = lambda r: [r.glow(i, j, RUNE if (i + j) % 2 else lighten(RUNE, 0.4)) for j in range(r.h) for i in range(r.w)]
    return Mt


def outlaw_model():
    m = OF.Model("gunslinger_outlaw")
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 12, 0], parent="root")
    m.bone("head", [0, 24, 0], parent="body")
    m.bone("hat", [0, 31, 0], parent="head")
    m.bone("right_arm", [-5, 22, 0], parent="body")
    m.bone("gun", [-6, 11, 0], parent="right_arm")
    m.bone("left_arm", [5, 22, 0], parent="body")
    m.bone("poncho", [0, 24, 0], parent="body")
    m.bone("coat_tails", [0, 12, 2.2], parent="body")
    m.bone("right_leg", [-1.9, 12, 0], parent="root")
    m.bone("left_leg", [1.9, 12, 0], parent="root")
    # cabeça, lenço e chapéu
    m.box("head", [-4, 24, -4], [8, 8, 8], fc(n="face", side="head", u="head", d="head"))
    m.box("head", [-4.3, 23.8, -4.4], [8.6, 3.4, 8.8], fc(side="bandana", u=OMIT, d="bandana@4x4"))
    m.box("head", [-2.0, 22.0, -4.6], [4.0, 1.8, 0.4], fc(n="bandana@4x2", s="bandana@4x2", ew=OMIT, u=OMIT, d=OMIT))
    m.box("hat", [-7.5, 31.0, -7.5], [15, 0.7, 15], fc(side="brim", u="brim", d="brim_under"))
    m.box("hat", [-4.2, 31.6, -4.5], [8.4, 4.0, 9.0], fc(side="hat", u="hat_top", d=OMIT))
    m.box("hat", [-4.2, 31.6, -4.5], [8.4, 1.0, 9.0], fc(side="hat_band", u=OMIT, d=OMIT), inflate=0.15)
    for x0, rz in ((-8.6, 30), (7.4, -30)):
        m.box("hat", [x0, 31.1, -6.0], [1.2, 0.5, 12.0], fc(side="brim", u="brim@2x6", d="brim_under@2x6"),
              rot=[0, 0, rz], pivot=[x0 + (1.2 if rz > 0 else 0), 31.3, 0])
    # tronco, cinto e coldre (o revólver reserva fica no coldre esquerdo)
    m.box("body", [-4, 12, -2], [8, 12, 4], fc(n="coat_front", side="coat", u="coat", d="coat"))
    m.box("body", [-4.5, 11.4, -2.5], [9, 1.6, 5], fc(side="belt", u="belt", d="belt"))
    m.box("body", [-1.0, 11.2, -2.7], [2.0, 2.0, 0.4], fc(all="buckle@2x2"))
    m.box("body", [4.0, 6.6, -1.2], [1.0, 5.0, 2.4], fc(side="holster", u="holster@1x2", d="holster@1x2"))
    m.box("body", [4.1, 11.2, -0.6], [0.8, 1.8, 1.2], fc(all="gun_grip@1x2"), rot=[-15, 0, 0], pivot=[4.5, 11.2, 0])
    # poncho por cima dos ombros (frente e costas; o fundo é aberto)
    m.box("poncho", [-5.2, 15.4, -3.0], [10.4, 8.8, 6.0], fc(side="poncho", u="poncho_top", d=OMIT))
    m.box("poncho", [-5.0, 15.6, -2.8], [10.0, 8.4, 5.6], fc(side="poncho_in", u=OMIT, d=OMIT))
    # abas do casaco atrás
    m.box("coat_tails", [-4.2, 2.6, 2.2], [8.4, 9.4, 0.5], fc(ns="coat_tail", ew="coat@1x8", u=OMIT, d=OMIT))
    # braços
    for bone, x0 in (("right_arm", -8), ("left_arm", 4)):
        m.box(bone, [x0, 12, -2], [4, 12, 4], fc(side="sleeve", u="sleeve", d="glove"))
        m.box(bone, [x0, 12, -2], [4, 2.6, 4], fc(side="glove", u=OMIT, d="glove"), inflate=0.2)
    # revólver na mão direita: braço abaixado = cano para o chão; braço erguido (X -90) = cano para a frente
    m.box("gun", [-6.6, 10.4, -0.2], [1.2, 1.5, 2.8], fc(all="gun_grip"))
    m.box("gun", [-6.8, 8.4, -1.7], [1.6, 2.4, 2.0], fc(all="gun_metal"))
    m.box("gun", [-6.5, 3.4, -1.45], [1.0, 5.0, 1.0], fc(all="gun_metal"))
    m.box("gun", [-6.2, 3.4, -1.85], [0.4, 0.6, 0.4], fc(all="gun_dark@1x1"))
    m.box("gun", [-6.55, 5.0, -1.5], [1.1, 1.6, 1.1], fc(all="gun_rune@1x2"))
    m.box("gun", [-6.3, 10.2, 0.3], [0.6, 0.6, 1.0], fc(all="gun_dark@1x1"))
    # pernas e botas com espora
    for bone, x0 in (("right_leg", -3.9), ("left_leg", -0.1)):
        m.box(bone, [x0, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d="sole"))
        m.box(bone, [x0, 0, -2], [4, 4.5, 4], fc(side="boot", u="boot", d="sole"), inflate=0.25)
        m.box(bone, [x0 + 1.8, 0.8, 2.2], [0.4, 0.4, 1.2], fc(all="spur@1x1"))
    return m


def build_entity_variants(m, variants, size=(64, 64)):
    """Como o build do outfits, mas para entidade: um atlas (mesmo UV) e uma textura por variante."""
    first = variants[0][1]
    keys = {}
    for b in m.bones:
        for cbe in b["cubes"]:
            for f, mat in cbe["faces"].items():
                if mat is None:
                    continue
                fw, fh = OF.face_dims(f, cbe["size"])
                k = OF.region_key(mat, fw, fh)
                if k[0] not in first:
                    raise KeyError(f"[{m.name}] material desconhecido: {k[0]}")
                keys[k] = (k, k[1], k[2])
    W, Hh = size
    pos = None
    for cand in (size, (128, 64), (128, 128), (256, 128)):
        pos = OF.pack(list(keys.values()), *cand)
        if pos is not None:
            W, Hh = cand
            break
    if pos is None:
        raise RuntimeError(f"[{m.name}] atlas nao coube")
    for tex_name, mats in variants:
        cv, gl = Canvas(W, Hh), Canvas(W, Hh)
        for k, (x, y) in sorted(pos.items(), key=lambda kv: str(kv[0])):
            seed = sum(ord(ch) * (i + 7) for i, ch in enumerate(f"{m.name}:{k}"))
            mats[k[0]](OF.Region(cv, gl, x, y, k[1], k[2], seed))
        cv.save(os.path.join(A, "textures", "entity", tex_name + ".png"))
        gp = os.path.join(A, "textures", "entity", tex_name + "_glowmask.png")
        if any(gl.px[y][x][3] > 0 for y in range(Hh) for x in range(W)):
            gl.save(gp)
        elif os.path.exists(gp):
            os.remove(gp)
    bones = []
    for b in m.bones:
        jb = {"name": b["name"]}
        if "parent" in b:
            jb["parent"] = b["parent"]
        jb["pivot"] = [OF.r4(v) for v in b["pivot"]]
        if "rotation" in b:
            jb["rotation"] = [OF.r4(v) for v in b["rotation"]]
        cubes = []
        for cbe in b["cubes"]:
            jc = {"origin": [OF.r4(v) for v in cbe["origin"]], "size": [OF.r4(v) for v in cbe["size"]]}
            if cbe["inflate"]:
                jc["inflate"] = OF.r4(cbe["inflate"])
            if cbe["rotation"] and any(abs(v) > 1e-6 for v in cbe["rotation"]):
                jc["pivot"] = [OF.r4(v) for v in (cbe["pivot"] or cbe["origin"])]
                jc["rotation"] = [OF.r4(v) for v in cbe["rotation"]]
            uv = {}
            for f in OF.FACES:
                mat = cbe["faces"].get(f)
                if mat is None:
                    continue
                fw, fh = OF.face_dims(f, cbe["size"])
                k = OF.region_key(mat, fw, fh)
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
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.25, 0]},
        "bones": bones}]}
    write_json(os.path.join(A, "geo", "entity", m.name + ".geo.json"), geo)
    print(f"  {m.name}: {len(keys)} regioes, atlas {W}x{Hh}, {len(variants)} texturas")


def num(v):
    v = round(v, 4)
    return int(v) if v == int(v) else v


def keys(d):
    """Quadros-chave Bedrock: {"0.0": [x, y, z], "0.5": [...]} (tempo em segundos)."""
    return {str(round(float(t), 4)): [num(x) for x in v] for t, v in sorted(d.items())}


def K(*pairs):
    return keys({pairs[i]: pairs[i + 1] for i in range(0, len(pairs), 2)})


AIM_R = [-88, -6, 0]
AIM_L = [-42, 32, 0]


def outlaw_anims():
    a = {}

    def add(name, length, loop, bones):
        e = {"animation_length": length, "bones": bones}
        if loop:
            e["loop"] = True
        a[f"animation.gunslinger_outlaw.{name}"] = e

    add("idle", 3.0, True, {
        "body": {"rotation": K(0, [0, 0, 0], 1.5, [1.2, 0, 0], 3.0, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 1.2, [-2, 4, 0], 2.4, [1, -3, 0], 3.0, [0, 0, 0])},
        "right_arm": {"rotation": K(0, [0, 0, 4], 1.5, [-3, 0, 6], 3.0, [0, 0, 4])},
        "left_arm": {"rotation": K(0, [0, 0, -4], 1.5, [-3, 0, -6], 3.0, [0, 0, -4])},
        "poncho": {"rotation": K(0, [0, 0, 0], 1.5, [2.5, 0, 1], 3.0, [0, 0, 0])},
        "coat_tails": {"rotation": K(0, [3, 0, 0], 1.5, [6, 0, 0], 3.0, [3, 0, 0])},
    })
    add("walk", 1.0, True, {
        "right_leg": {"rotation": K(0, [28, 0, 0], 0.5, [-28, 0, 0], 1.0, [28, 0, 0])},
        "left_leg": {"rotation": K(0, [-28, 0, 0], 0.5, [28, 0, 0], 1.0, [-28, 0, 0])},
        "right_arm": {"rotation": K(0, [-14, 0, 4], 0.5, [14, 0, 4], 1.0, [-14, 0, 4])},
        "left_arm": {"rotation": K(0, [20, 0, -4], 0.5, [-20, 0, -4], 1.0, [20, 0, -4])},
        "poncho": {"rotation": K(0, [4, 0, 0], 0.5, [7, 0, 0], 1.0, [4, 0, 0])},
        "coat_tails": {"rotation": K(0, [10, 0, 0], 0.5, [16, 0, 0], 1.0, [10, 0, 0])},
        "head": {"rotation": K(0, [0, -3, 0], 0.5, [0, 3, 0], 1.0, [0, -3, 0])},
    })
    # contagem do duelo: mão pairando sobre o coldre, corpo inclinado, poncho ao vento; a arma ainda no coldre
    add("ready", 2.0, True, {
        "body": {"rotation": K(0, [6, 0, 0], 1.0, [7, 0, 0], 2.0, [6, 0, 0])},
        "head": {"rotation": K(0, [-6, 0, 0], 1.0, [-7, 0, 0], 2.0, [-6, 0, 0])},
        "right_arm": {"rotation": K(0, [6, 0, 14], 0.5, [8, 0, 16], 1.0, [6, 0, 14], 1.5, [8, 0, 13], 2.0, [6, 0, 14])},
        "left_arm": {"rotation": K(0, [0, 0, -10], 1.0, [2, 0, -12], 2.0, [0, 0, -10])},
        "right_leg": {"rotation": K(0, [0, 0, 6], 2.0, [0, 0, 6])},
        "left_leg": {"rotation": K(0, [0, 0, -6], 2.0, [0, 0, -6])},
        "poncho": {"rotation": K(0, [5, 0, 2], 0.7, [9, 0, -1], 1.4, [6, 0, 3], 2.0, [5, 0, 2])},
        "coat_tails": {"rotation": K(0, [10, 0, 0], 0.7, [18, 0, 0], 1.4, [12, 0, 0], 2.0, [10, 0, 0])},
        "gun": {"scale": K(0, [0, 0, 0], 2.0, [0, 0, 0])},
    })
    add("aim", 1.0, True, {
        "right_arm": {"rotation": K(0, AIM_R, 0.5, [-87, -6, 0], 1.0, AIM_R)},
        "left_arm": {"rotation": K(0, AIM_L, 0.5, [-40, 30, 0], 1.0, AIM_L)},
        "body": {"rotation": K(0, [0, 6, 0], 1.0, [0, 6, 0])},
        "poncho": {"rotation": K(0, [3, 0, 0], 0.5, [5, 0, 0], 1.0, [3, 0, 0])},
        "coat_tails": {"rotation": K(0, [6, 0, 0], 0.5, [9, 0, 0], 1.0, [6, 0, 0])},
    })
    add("aim_walk", 1.0, True, {
        "right_arm": {"rotation": K(0, AIM_R, 0.5, [-86, -6, 0], 1.0, AIM_R)},
        "left_arm": {"rotation": K(0, AIM_L, 0.5, [-38, 30, 0], 1.0, AIM_L)},
        "body": {"rotation": K(0, [0, 6, 0], 1.0, [0, 6, 0])},
        "right_leg": {"rotation": K(0, [22, 0, 0], 0.5, [-22, 0, 0], 1.0, [22, 0, 0])},
        "left_leg": {"rotation": K(0, [-22, 0, 0], 0.5, [22, 0, 0], 1.0, [-22, 0, 0])},
        "poncho": {"rotation": K(0, [5, 0, 0], 0.5, [8, 0, 0], 1.0, [5, 0, 0])},
        "coat_tails": {"rotation": K(0, [10, 0, 0], 0.5, [16, 0, 0], 1.0, [10, 0, 0])},
    })
    add("draw", 0.45, False, {
        "right_arm": {"rotation": K(0, [6, 0, 14], 0.08, [-55, 0, 6], 0.15, [-96, -6, 0], 0.45, AIM_R)},
        "left_arm": {"rotation": K(0, [0, 0, -10], 0.15, [-30, 20, 0], 0.45, AIM_L)},
        "gun": {"scale": K(0, [0, 0, 0], 0.05, [1, 1, 1], 0.45, [1, 1, 1]),
                "rotation": K(0, [0, 0, 0], 0.05, [180, 0, 0], 0.12, [360, 0, 0], 0.45, [360, 0, 0])},
    })
    add("shoot", 0.3, False, {
        "right_arm": {"rotation": K(0, AIM_R, 0.05, [-108, -6, 0], 0.3, AIM_R)},
        "body": {"rotation": K(0, [0, 6, 0], 0.05, [-3, 6, 0], 0.3, [0, 6, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.05, [-2, 0, 0], 0.3, [0, 0, 0])},
    })
    add("reload", 1.0, False, {
        "right_arm": {"rotation": K(0, AIM_R, 0.15, [-40, -22, 0], 0.8, [-40, -22, 0], 1.0, AIM_R)},
        "left_arm": {"rotation": K(0, AIM_L, 0.15, [-50, 30, 0], 0.3, [-62, 36, 0], 0.45, [-48, 26, 0], 0.6, [-62, 36, 0],
                                   0.75, [-48, 26, 0], 1.0, AIM_L)},
        "head": {"rotation": K(0, [0, 0, 0], 0.15, [14, 0, 0], 0.8, [14, 0, 0], 1.0, [0, 0, 0])},
        "gun": {"rotation": K(0, [0, 0, 0], 0.6, [0, 0, 0], 0.85, [-360, 0, 0], 1.0, [-360, 0, 0])},
    })
    add("fan", 0.6, False, {
        "right_arm": {"rotation": K(0, AIM_R, 0.6, AIM_R)},
        "left_arm": {"rotation": K(0, [-70, 40, 0], 0.08, [-86, 18, 12], 0.16, [-70, 40, 0], 0.24, [-86, 18, 12], 0.32, [-70, 40, 0],
                                   0.4, [-86, 18, 12], 0.48, [-70, 40, 0], 0.6, AIM_L)},
        "body": {"rotation": K(0, [0, 10, 0], 0.6, [0, 6, 0])},
    })
    add("tip_hat", 0.7, False, {
        "left_arm": {"rotation": K(0, [0, 0, -5], 0.2, [-160, 0, -12], 0.45, [-160, 0, -12], 0.7, [0, 0, -5])},
        "hat": {"rotation": K(0, [0, 0, 0], 0.25, [0, 0, 0], 0.35, [-16, 0, 0], 0.5, [0, 0, 0]),
                "position": K(0, [0, 0, 0], 0.35, [0, 0.6, 0], 0.5, [0, 0, 0])},
        "head": {"rotation": K(0, [0, 0, 0], 0.3, [10, 0, 0], 0.6, [0, 0, 0])},
    })
    write_json(os.path.join(A, "animations", "entity", "gunslinger_outlaw.animation.json"),
               {"format_version": "1.8.0", "animations": a})


def gen_outlaw():
    m = outlaw_model()
    build_entity_variants(m, [("gunslinger_outlaw", outlaw_mats(OUTLAW_LOOKS["gunslinger_outlaw"], False)),
                              ("gunslinger_outlaw_reaper", outlaw_mats(OUTLAW_LOOKS["gunslinger_outlaw_reaper"], True))])
    outlaw_anims()
    write_json(os.path.join(A, "models", "item", "gunslinger_outlaw_spawn_egg.json"),
               {"parent": "minecraft:item/template_spawn_egg"})
    # bala de mana (reserva; o BulletRenderer desenha a fita de luz)
    c = Canvas(16, 16)
    c.circle(8, 8, 4.5, H("D9A441"))
    c.circle(8, 8, 2.6, H("FFE8A8"))
    c.circle(8, 8, 1.2, H("FFFFFF"))
    c.save(os.path.join(A, "textures", "entity", "mana_bullet.png"))
    print("  forasteiro espectral: geo + 2 texturas + 10 animacoes + ovo; bala de mana")


# =====================================================================================================
#  ANIMAÇÕES DO JOGADOR (playerAnimator, formato emotecraft v3, graus)
# =====================================================================================================
def emote(name, desc, end, stop, moves, loop=False):
    body = {"version": 3, "name": name, "author": "Isekai Mahou", "description": desc,
            "emote": {"beginTick": 0, "endTick": end, "stopTick": stop, "degrees": True, "nsfw": False,
                      "easeBeforeKeyframe": True, "moves": moves, "isLoop": loop, "returnTick": 0}}
    write_json(os.path.join(A, "player_animations", name + ".json"), body)


def mv(tick, easing="EASEINOUTSINE", **parts):
    d = {"tick": tick, "easing": easing}
    for k, v in parts.items():
        d[k] = {"pitch": v[0], "yaw": v[1], "roll": v[2]}
    return d


def gen_player_anims():
    R_AIM, L_REST = (-90, -4, 0), (6, 0, -6)
    emote("gunslinger_shoot", "Tiro com coice (braco direito)", 10, 14, [
        mv(0, "EASEOUTQUAD", rightArm=R_AIM, leftArm=L_REST),
        mv(2, "EASEOUTQUAD", rightArm=(-112, -4, 4), leftArm=L_REST),
        mv(7, rightArm=R_AIM, leftArm=L_REST),
        mv(10, rightArm=R_AIM, leftArm=L_REST),
    ])
    emote("gunslinger_shoot_left", "Tiro com a mao esquerda (pistolas duplas)", 10, 14, [
        mv(0, "EASEOUTQUAD", leftArm=(-90, 4, 0), rightArm=(-80, -4, 0)),
        mv(2, "EASEOUTQUAD", leftArm=(-112, 4, -4), rightArm=(-80, -4, 0)),
        mv(7, leftArm=(-90, 4, 0), rightArm=(-80, -4, 0)),
        mv(10, leftArm=(-90, 4, 0), rightArm=(-80, -4, 0)),
    ])
    emote("gunslinger_reload", "Recarga: arma na frente do peito, a outra mao enche o tambor", 22, 26, [
        mv(0, rightArm=(-45, -28, 0), leftArm=(-40, 30, 0), head=(12, 0, 0)),
        mv(4, rightArm=(-48, -30, 6), leftArm=(-58, 36, 0), head=(16, 0, 0)),
        mv(8, rightArm=(-48, -30, 6), leftArm=(-44, 26, 0), head=(16, 0, 0)),
        mv(12, rightArm=(-48, -30, 6), leftArm=(-58, 36, 0), head=(16, 0, 0)),
        mv(16, "EASEOUTBACK", rightArm=(-70, -14, 0), leftArm=(-30, 20, 0), head=(8, 0, 0)),
        mv(22, rightArm=(-20, 0, 6), leftArm=(4, 0, -6), head=(0, 0, 0)),
    ])
    emote("gunslinger_fan", "Leque do cao: a mao de apoio bate no cao varias vezes", 14, 18, [
        mv(0, rightArm=R_AIM, leftArm=(-72, 40, 0)),
        mv(2, "EASEOUTQUAD", rightArm=(-100, -4, 0), leftArm=(-88, 16, 14)),
        mv(4, rightArm=R_AIM, leftArm=(-72, 40, 0)),
        mv(6, "EASEOUTQUAD", rightArm=(-100, -4, 0), leftArm=(-88, 16, 14)),
        mv(8, rightArm=R_AIM, leftArm=(-72, 40, 0)),
        mv(10, "EASEOUTQUAD", rightArm=(-100, -4, 0), leftArm=(-88, 16, 14)),
        mv(14, rightArm=R_AIM, leftArm=(-40, 24, 0)),
    ])
    emote("gunslinger_charge", "Tiro carregado: mira com as duas maos e coice forte", 20, 24, [
        mv(0, rightArm=(-90, -6, 0), leftArm=(-86, 34, 0), torso=(0, 0, 0)),
        mv(3, "EASEOUTQUAD", rightArm=(-120, -6, 0), leftArm=(-114, 34, 0), torso=(-6, 0, 0)),
        mv(12, rightArm=(-90, -6, 0), leftArm=(-86, 34, 0), torso=(0, 0, 0)),
        mv(20, rightArm=(-90, -6, 0), leftArm=(-50, 20, 0), torso=(0, 0, 0)),
    ])
    emote("gunslinger_noon", "Doze Badaladas: a mao pairando sobre o coldre ate o saque", 40, 46, [
        mv(0, rightArm=(8, 0, 14), leftArm=(0, 0, -12), head=(-4, 0, 0)),
        mv(10, rightArm=(10, 0, 16), leftArm=(0, 0, -12), head=(-4, 0, 0)),
        mv(20, rightArm=(8, 0, 13), leftArm=(0, 0, -12), head=(-4, 0, 0)),
        mv(29, rightArm=(10, 0, 16), leftArm=(0, 0, -12), head=(-4, 0, 0)),
        mv(31, "EASEOUTBACK", rightArm=(-96, -4, 0), leftArm=(-74, 40, 0), head=(0, 0, 0)),
        mv(40, rightArm=R_AIM, leftArm=(-60, 30, 0), head=(0, 0, 0)),
    ])
    print("  animacoes do jogador: 6")


# =====================================================================================================
#  ÍCONE DA ABA (16x16): revólver cruzado com uma estrela de xerife
# =====================================================================================================
def gen_tab():
    c = Canvas(16, 16)
    G = pal("6A4508", "A06C12", "D49A26", "F5C84A", "FFF0A8")
    rows = [
        ".......o........",
        "......oGo.......",
        "......oGo.......",
        "..ooooogoooooo..",
        "..oGGggxggggGo..",
        "...oGgxxxggGo...",
        "....ogxXxgo.....",
        "....oggxggo.....",
        "...oggo.oggo....",
        "...ogo...ogo....",
        "..ogo.....ogo...",
        "..oo.......oo...",
        "................",
        "................",
        "................",
        "................",
    ]
    c.from_rows(rows, {"o": OUT, "G": G[4], "g": G[2], "x": G[3], "X": H("FFFFFF")})
    # revólver pequeno embaixo
    M_ = pal("2A2E38", "5A6070", "8A90A0", "C8CCD8")
    for x in range(5, 15):
        c.set(x, 13, M_[2])
    c.set(14, 12, M_[3])
    c.rect(3, 12, 5, 14, M_[1])
    c.set(4, 13, H("F2C14E"))
    c.set(2, 15, H("6A4428"))
    c.set(3, 15, H("8A5E3A"))
    c.save(os.path.join(A, "textures", "gui", "tab", "gunslinger_path.png"))


def main():
    print("class_gunslinger:")
    gen_guns()
    gen_outfit()
    gen_outlaw()
    gen_player_anims()
    gen_tab()
    import class_gunslinger_lang
    class_gunslinger_lang.main()


if __name__ == "__main__":
    main()
