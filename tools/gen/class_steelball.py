"""
Gera os assets e dados do modulo class_steelball (STEEL BALL — a classe da Rotacao).
Uso (na raiz do projeto):  py -I tools/gen/class_steelball.py

Gera:
  - itens 16x16 (bola de aco e graus, Relicario, 9 Partes do Cadaver Santo, Bussola do Santo, Carta do Mestre, Apito do
    Corcel, icones da roupa) + modelos de item + ovo do Mestre;
  - entidades GeckoLib (geo + animacao + textura): bola de aco (aco/ouro), unha giratoria (rosa/ouro), turbilhao,
    espiral da Rotacao Infinita e o Mestre Rotari;
  - roupa de 4 graus (GeckoLib, mesmo esquema de ossos de armadura das roupas de classe) + glowmask + camadas vanilla;
  - icone do efeito Girado e da aba "Rotacao";
  - Sepultura do Santo (.nbt + worldgen random_spread_multi + tag de bioma/estrutura no merge + loot);
  - receitas e as traducoes (pt_br + en_us) em src/main/lang/class_steelball.
"""
import json
import math
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402
from structgen import Structure, Byte  # noqa: E402
import outfits as O  # noqa: E402  (montador de modelos/atlas dos trajes e das entidades)

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
D = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")
MERGE = os.path.join(ROOT, "src/main/merge/class_steelball")
LANG = os.path.join(ROOT, "src/main/lang/class_steelball")
T = (0, 0, 0, 0)
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


def item_model(item_id, texture=None, parent="minecraft:item/generated"):
    write_json(os.path.join(A, "models/item", item_id + ".json"),
               {"parent": parent, "textures": {"layer0": "isekai_mahou:item/" + (texture or item_id)}})


def save(c, rel):
    c.save(os.path.join(A, "textures", rel))


def jit(c, rnd, amt):
    k = (rnd.random() - 0.5) * 2 * amt
    return lighten(c, k) if k > 0 else darken(c, -k)


# =====================================================================================
#  ITENS 16x16
# =====================================================================================
STEEL = pal("2E343C", "4E5864", "7A8694", "A8B4C2", "DCE6F0")
GOLDP = pal("5A3A04", "9A6A10", "D8A22E", "FFD24A", "FFF2B0")
NAILP = pal("5A1E5E", "8E3A92", "C462C8", "E58AE8", "FFD0FF")
OUTLINE = H("15121C")


def ball_icon(P, studs=None, band=None, glow=None, seed=1):
    """Bola de aco 16x16: esfera com sulcos em espiral (o giro), brilho no canto e contorno."""
    c = Canvas(16, 16)
    c.circle(8, 8, 6.2, P[2])
    c.shade_sphere(8, 8, 6.2, light=0.5, dark=0.55)
    rnd = random.Random(seed)
    # sulcos: duas faixas em arco (a "costura" da bola)
    for t in range(0, 40):
        a = t / 40 * math.pi * 1.6 - 0.4
        x = 8 + math.cos(a) * 3.6
        y = 8 + math.sin(a) * 3.6 * 0.55 - 1.0
        c.set(int(x), int(y), darken(c.get(int(x), int(y)), 0.35) if c.get(int(x), int(y))[3] else T)
    for t in range(0, 30):
        a = t / 30 * math.pi * 1.4 + 2.4
        x = 8 + math.cos(a) * 4.4 * 0.6 + 1
        y = 8 + math.sin(a) * 4.4
        p = c.get(int(x), int(y))
        if p[3]:
            c.set(int(x), int(y), darken(p, 0.3))
    if band:
        for x in range(3, 14):
            y = 8 + int(round(math.sin((x - 3) / 10 * math.pi) * 0.0))
            p = c.get(x, y)
            if p[3]:
                c.set(x, y, band[3] if x % 3 else band[4])
    if studs:
        for (x, y) in ((5, 5), (11, 6), (6, 11), (10, 10), (8, 3)):
            if c.get(x, y)[3]:
                c.set(x, y, studs)
    c.set(5, 4, P[4])
    c.set(6, 4, P[4])
    c.set(5, 5, lighten(P[4], 0.4))
    if glow:
        for (x, y) in ((8, 8), (9, 8), (8, 9)):
            c.set(x, y, glow)
    c.outline(OUTLINE)
    c.noise(0.03, seed)
    return c


def relic_icon():
    """Relicario Selado: caixinha de madeira escura com cinta dourada e uma cruz de luz no selo."""
    c = Canvas(16, 16)
    W = pal("2A1A10", "4A2E1C", "6A4428", "8A5E3A", "B07E52")
    c.rect(3, 6, 12, 13, W[2])
    c.rect(3, 4, 12, 6, W[3])
    c.rect(3, 4, 12, 4, W[4])
    for x in range(3, 13):
        c.set(x, 13, W[1])
    c.rect(7, 4, 8, 13, GOLDP[3])
    c.rect(3, 8, 12, 8, GOLDP[2])
    c.rect(6, 7, 9, 10, GOLDP[3])
    c.set(7, 8, H("FFFFFF"))
    c.set(8, 8, GOLDP[4])
    c.set(7, 9, GOLDP[4])
    c.set(8, 9, H("FFFFFF"))
    c.outline(OUTLINE)
    c.noise(0.04, 5)
    return c


# cada parte: (forma em linhas, paleta {letra: cor})
PART_BASE = {"o": OUTLINE, "g": GOLDP[3], "G": GOLDP[4], "d": GOLDP[1]}
BONE = pal("6A6250", "948A72", "BEB496", "DCD4B8", "F4EEDC")
PARTS = {
    "eyes": (["................",
              "................",
              "................",
              "...oooo..oooo...",
              "..oWWWWooWWWWo..",
              ".oWWbBWWWWbBWWo.",
              ".oWbkkBWWbkkBWo.",
              ".oWbkkBWWbkkBWo.",
              ".oWWbbWWWWbbWWo.",
              "..oWWWWooWWWWo..",
              "...oooo..oooo...",
              "......gGGg......",
              ".....g....g.....",
              "................",
              "................",
              "................"], {"W": H("F4F0E6"), "b": H("3A8AC8"), "B": H("7FD8FF"), "k": H("101018")}),
    "heart": (["................",
               "................",
               "...ooo....ooo...",
               "..orrRo..oRrro..",
               ".orrRRRooRRrrro.",
               ".orrRRRRRRRrrro.",
               ".orrrRRRRRrrrro.",
               ".odrrrRRRrrrrdo.",
               "..odrrrrrrrrdo..",
               "...odrrrrrrdo...",
               "....odrrrrdo....",
               ".....odrrdo.....",
               "......oddo......",
               ".......oo.......",
               "......gGGg......",
               "................"], {"r": H("C0303E"), "R": H("F06070"), "d": H("7A1622")}),
    "left_arm": (["................",
                  "..........oo....",
                  ".........obbo...",
                  "........obBbo...",
                  ".......obBbo....",
                  "......obBbo.....",
                  ".....obBbo......",
                  "....obBbo.......",
                  "...obBbo........",
                  "..ossso.........",
                  ".osSsso.........",
                  ".oSpSpo.........",
                  ".opPpPo.........",
                  "..oPPo..........",
                  "...oo...........",
                  "................"], {"b": BONE[2], "B": BONE[4], "s": H("8E3A92"), "S": H("C462C8"), "p": NAILP[3], "P": NAILP[4]}),
    "right_arm": (["................",
                   "....oo..........",
                   "...obbo.........",
                   "...obBbo........",
                   "....obBbo.......",
                   ".....obBbo......",
                   "......obBbo.....",
                   ".......obBbo....",
                   "........obBbo...",
                   ".........ossso..",
                   ".........osSsso.",
                   "........oaAoSso.",
                   ".......oaAAo.o..",
                   ".......oAAao....",
                   "........ooo.....",
                   "................"], {"b": BONE[2], "B": BONE[4], "s": H("9A6A3A"), "S": H("C89A62"), "a": STEEL[2], "A": STEEL[4]}),
    "spine": (["................",
               ".......oo.......",
               "......obBo......",
               ".....oobBoo.....",
               "......obBo......",
               ".....oobBoo.....",
               "......obBo......",
               ".....oobBoo.....",
               "......obBo......",
               ".....oobBoo.....",
               "......obBo......",
               ".....oobBoo.....",
               "......obBo......",
               ".......oo.......",
               "......gGGg......",
               "................"], {"b": BONE[2], "B": BONE[4]}),
    "ears": (["................",
              "................",
              "...ooo....ooo...",
              "..osSso..osSso..",
              ".osSkSo..oSkSso.",
              ".osSkkSooSkkSso.",
              ".osSkSo..oSkSso.",
              ".osSSo....oSSso.",
              "..osso....osso..",
              "...oo......oo...",
              "................",
              "....gGG..GGg....",
              "................",
              "................",
              "................",
              "................"], {"s": H("C8946A"), "S": H("E8B88A"), "k": H("7A4A32")}),
    "legs": (["................",
              "....oooooooo....",
              "....obBbbBbo....",
              "....obBo.oBbo...",
              "....obBo..obBo..",
              "....obBo..obBo..",
              "...obBo...obBo..",
              "...obBo...obBo..",
              "...obBo...obBo..",
              "...obBo...obBo..",
              "..obBo.....obBo.",
              "..ooBo.....obBo.",
              "..obbBo....obbBo",
              "..ooooo....ooooo",
              "................",
              "................"], {"b": BONE[2], "B": BONE[4]}),
    "torso": (["................",
               "......oooo......",
               "...ooobBBbooo...",
               "..obBobBBbobBo..",
               "..oboobBBboobo..",
               "..obBobBBbobBo..",
               "..oboobBBboobo..",
               "..obBobBBbobBo..",
               "...ooobBBbooo...",
               "......obbo......",
               ".....obBBbo.....",
               "....obbggbbo....",
               "....oooGGooo....",
               "................",
               "................",
               "................"], {"b": BONE[2], "B": BONE[4]}),
    "skull": (["................",
               "....oooooooo....",
               "...obBBBBBBbo...",
               "..obBBBBBBBBbo..",
               "..obBBBBBBBBbo..",
               "..obkkBBBBkkbo..",
               "..obkkkBBkkkbo..",
               "..obbkBBBBkbbo..",
               "...obBBkkBBbo...",
               "....obBBBBbo....",
               "....obtbtbto....",
               ".....oooooo.....",
               "....g.gGGg.g....",
               "................",
               "................",
               "................"], {"b": BONE[2], "B": BONE[4], "k": H("2A2018"), "t": BONE[3]}),
}


def part_icon(pid):
    rows, p = PARTS[pid]
    c = Canvas(16, 16)
    palette = dict(PART_BASE)
    palette.update(p)
    c.from_rows(rows, palette)
    # halo dourado suave atras da reliquia
    halo = H("FFE89A", 90)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14), (0, 8), (15, 8), (8, 0)):
        if c.get(x, y)[3] == 0:
            c.set(x, y, halo)
    c.noise(0.035, len(pid) * 7)
    return c


def compass_icon():
    c = Canvas(16, 16)
    c.circle(8, 8, 6.5, GOLDP[2])
    c.circle(8, 8, 5.2, H("2A2A36"))
    c.circle(8, 8, 4.4, H("E8E0C8"))
    for i in range(4):
        a = i * math.pi / 2
        c.set(int(8 + math.cos(a) * 4), int(8 + math.sin(a) * 4), H("8A7A5A"))
    # agulha: rosa (aponta) e aco
    for k in range(0, 4):
        c.set(8 + k // 2, 8 - k, H("C462C8"))
        c.set(8 - k // 2, 8 + k, STEEL[2])
    c.set(8, 8, GOLDP[4])
    c.set(5, 4, GOLDP[4])
    c.outline(OUTLINE)
    c.noise(0.03, 9)
    return c


def letter_icon():
    c = Canvas(16, 16)
    paper = pal("A89878", "C8B898", "E8DCC0", "F6EEDC", "FFFFF4")
    c.rect(2, 4, 13, 12, paper[2])
    for x in range(2, 14):
        c.set(x, 12, paper[1])
    c.line(2, 4, 8, 9, paper[1])
    c.line(13, 4, 8, 9, paper[1])
    c.line(2, 4, 8, 8, paper[3])
    c.rect(7, 8, 9, 10, H("A0262E"))
    c.set(8, 9, H("E0475A"))
    c.outline(OUTLINE)
    c.noise(0.03, 12)
    return c


def whistle_icon():
    c = Canvas(16, 16)
    c.rect(3, 7, 10, 10, STEEL[2])
    c.rect(3, 7, 10, 7, STEEL[4])
    c.circle(11.5, 9, 2.6, STEEL[2])
    c.shade_sphere(11.5, 9, 2.6)
    c.rect(4, 8, 5, 8, H("101018"))
    c.line(2, 6, 1, 3, H("6A4428"))
    c.line(1, 3, 3, 1, H("6A4428"))
    c.set(3, 6, GOLDP[3])
    c.outline(OUTLINE)
    c.noise(0.03, 13)
    return c


# ---------------------------------------------------------------- icones da roupa (grau x peca)
OUT_TIERS = ["jockey", "plains", "rotation", "saint"]
# por grau: (feltro/chapeu, faixa, casaco, calca, bota, metal, brilho)
OUT_PAL = {
    "jockey": dict(hat=pal("2A1A10", "4A2E1C", "6A4428", "8A5E3A", "A87A50"), band=pal("1E3A26", "2E5A3A", "3E7A4A", "5A9A62", "8AC890"),
                   coat=pal("4A3418", "6A4C26", "8A6838", "A8864E", "C8A870"), pants=pal("7A6A4A", "9A8A66", "BAAA84", "D4C6A2", "EEE2C4"),
                   boot=pal("1A120C", "2E2014", "46301E", "5E422A", "7A583A"), metal=STEEL, glow=None),
    "plains": dict(hat=pal("16161C", "26262E", "3A3A44", "52525E", "6E6E7C"), band=pal("0E3A3A", "185A58", "2E8A86", "4AB0AA", "86DAD2"),
                   coat=pal("16261C", "223A2A", "2E4A36", "426A4C", "608E68"), pants=pal("5A4E36", "7A6A4A", "9A8A62", "B8A87E", "D6C8A0"),
                   boot=pal("0E0E12", "1C1C22", "2C2C34", "40404A", "585864"), metal=pal("4A3410", "7A5A1C", "A8822E", "D4AE4A", "F2D888"), glow=None),
    "rotation": dict(hat=pal("1E242C", "2E3844", "44525E", "5E6E7C", "7E90A0"), band=pal("0E3A2E", "186A50", "2EA07A", "5ADCA8", "A8F8D8"),
                     coat=pal("2A3440", "3E4A58", "5A6878", "7A8A9C", "A2B2C4"), pants=pal("1A1E24", "2A3038", "3C444E", "525C68", "6E7A88"),
                     boot=pal("0E1014", "1A1E24", "282E36", "3A424C", "525C68"), metal=STEEL, glow=H("7FE0B0")),
    "saint": dict(hat=pal("8A8478", "B4AE9E", "D8D2C2", "F0ECE0", "FFFFFA"), band=GOLDP,
                  coat=pal("9A9282", "C2BAA6", "E2DAC4", "F4EEDC", "FFFCF0"), pants=pal("6A6250", "948A72", "BEB496", "DCD4B8", "F4EEDC"),
                  boot=pal("6A5A3A", "8A7A52", "B0A070", "D4C496", "F0E6C4"), metal=GOLDP, glow=H("FFD24A")),
}

ICON_ROWS = {
    "helmet": ["................",
               "................",
               "......kkkk......",
               ".....kHHHHk.....",
               ".....kHhHHk.....",
               ".....kHHHHk.....",
               ".....bBBBBb.....",
               "..kkkkkkkkkkkk..",
               ".kHHHHHHHHHHHHk.",
               "..kkkkkkkkkkkk..",
               "................",
               "................",
               "................",
               "................",
               "................",
               "................"],
    "chestplate": ["................",
                   "...kkk....kkk...",
                   "..kCCCkkkkCCCk..",
                   ".kCCcCCwwCCcCCk.",
                   ".kCCcCCwwCCcCCk.",
                   ".kkkkCCwwCCkkkk.",
                   "....kCCwwCCk....",
                   "....kCmwwmCk....",
                   "....kLLLLLLk....",
                   "....kCsCCsCk....",
                   "....kCsCCsCk....",
                   "....kCCkkCCk....",
                   "....kCCk.kCCk...",
                   "....kkk...kkk...",
                   "................",
                   "................"],
    "leggings": ["................",
                 "....kkkkkkkk....",
                 "....kLLmmLLk....",
                 "....kPPPPPPk....",
                 "....kPPkkPPk....",
                 "....kPsk.kPPk...",
                 "....kPsk..kPsk..",
                 "...kPPsk..kPsk..",
                 "...kPsk...kPPk..",
                 "...kPsk...kPsk..",
                 "...kPPk...kPsk..",
                 "...kkkk...kkkk..",
                 "................",
                 "................",
                 "................",
                 "................"],
    "boots": ["................",
              "................",
              "................",
              "...kkkk..kkkk...",
              "...kmmk..kmmk...",
              "...kOOk..kOOk...",
              "...kOOk..kOOk...",
              "...kOOk..kOOk...",
              "...kOOk..kOOk...",
              "..kOOOk.kOOOk...",
              ".kOOOOkkOOOOk...",
              ".kkkkkk.kkkkkk..",
              ".m.....m........",
              "................",
              "................",
              "................"],
}


def outfit_icon(tier, piece):
    P = OUT_PAL[tier]
    palette = {"k": OUTLINE, "H": P["hat"][2], "h": P["hat"][4], "b": P["band"][3], "B": P["band"][2],
               "C": P["coat"][2], "c": P["coat"][3], "w": H("F0ECE0") if tier != "saint" else GOLDP[3],
               "m": P["metal"][3], "L": P["boot"][2], "s": P["pants"][4] if tier != "jockey" else P["pants"][1],
               "P": P["pants"][2], "O": P["boot"][2]}
    c = Canvas(16, 16)
    c.from_rows(ICON_ROWS[piece], palette)
    c.noise(0.04, hash((tier, piece)) & 0xFFFF)
    if tier in ("rotation", "saint"):
        # gema/espiral no canto: o grau brilha
        g = P["glow"]
        for (x, y) in ((13, 2), (14, 3), (13, 4), (12, 3)):
            c.set(x, y, g)
        c.set(13, 3, H("FFFFFF"))
    if tier == "plains":
        c.set(13, 3, P["metal"][4])
    return c


def gen_items():
    save(ball_icon(STEEL, seed=1), "item/steel_ball.png")
    save(ball_icon(STEEL, band=STEEL, seed=2), "item/steel_ball_t1.png")
    save(ball_icon(STEEL, studs=GOLDP[3], band=GOLDP, seed=3), "item/steel_ball_t2.png")
    save(ball_icon(GOLDP, studs=STEEL[4], seed=4), "item/steel_ball_t3.png")
    save(ball_icon(GOLDP, band=pal("5A1E5E", "8E3A92", "C462C8", "E58AE8", "FFD0FF"), glow=H("FFFFFF"), seed=5), "item/steel_ball_t4.png")
    for i in ("steel_ball", "steel_ball_t1", "steel_ball_t2", "steel_ball_t3", "steel_ball_t4"):
        item_model(i, parent="minecraft:item/handheld")
    save(relic_icon(), "item/steelball_relic.png")
    item_model("steelball_relic")
    for pid in PARTS:
        save(part_icon(pid), f"item/steelball_part_{pid}.png")
        item_model(f"steelball_part_{pid}")
    save(compass_icon(), "item/steelball_saint_compass.png")
    item_model("steelball_saint_compass")
    save(letter_icon(), "item/steelball_mentor_letter.png")
    item_model("steelball_mentor_letter")
    save(whistle_icon(), "item/steelball_horse_whistle.png")
    item_model("steelball_horse_whistle")
    write_json(os.path.join(A, "models/item/steelball_mentor_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    for t in OUT_TIERS:
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            name = f"steelball_outfit_{t}_{piece}"
            save(outfit_icon(t, piece), f"item/{name}.png")
            item_model(name)


def gen_effect_and_tab():
    c = Canvas(18, 18)
    c.circle(9, 9, 7, STEEL[2])
    c.shade_sphere(9, 9, 7)
    for t in range(48):
        a = t / 48 * math.pi * 4
        r = 1 + t / 48 * 6
        x, y = 9 + math.cos(a) * r, 9 + math.sin(a) * r
        if c.get(int(x), int(y))[3]:
            c.set(int(x), int(y), H("1E2430"))
    c.outline(OUTLINE)
    save(c, "mob_effect/steelball_spun.png")
    tab = ball_icon(STEEL, band=GOLDP, seed=7)
    save(tab, "gui/tab/steelball.png")


def main():
    print("== itens e icones")
    gen_items()
    gen_effect_and_tab()
    import class_steelball_models as models
    import class_steelball_world as world
    import class_steelball_lang as lang
    models.run()
    world.run()
    print("== traducoes")
    lang.write(LANG)
    print("ok")


if __name__ == "__main__":
    main()
