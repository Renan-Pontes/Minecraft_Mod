"""
Gera os assets e dados do modulo class_rogue (Ladino).
Uso (na raiz do projeto):  py -I tools/gen/class_rogue.py

Saidas:
  - Adagas: rogue_dagger (kit) e rogue_dagger_t1..t4 -> modelo 3D na mao (models/item/class_rogue/<id>_held.json +
    textures/item/class_rogue/<id>.png, mesmo motor do armory) + icone 2D (textures/item/<id>.png), loader
    neoforge:separate_transforms
  - Faca de Arremesso (3D + icone), Grimorio Roubado e Livro-Razao do Ladrao (icones)
  - Traje das Sombras (4 graus, GeckoLib, mesmo motor do outfits): geo/item/armor/rogue_shade_t<N>.geo.json,
    textures/item/armor/rogue_shade_t<N>.png (+ _glowmask nos graus II-IV), reservas vanilla
    (textures/models/armor/rogue_shade_t<N>_layer_1/2.png) e os 16 icones das pecas
  - Icone da aba do menu B (textures/gui/tab/rogue.png)
  - Receitas (data/isekai_mahou/recipe/class_rogue/*.json) e tags no merge (src/main/merge/class_rogue)
As traducoes ficam em src/main/lang/class_rogue (escritas a mao).
"""
import json
import math
import os
import sys
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))
from pixelart import Canvas, lighten, darken, mix, item_model  # noqa: E402
import armory as AR  # noqa: E402
import outfits as OF  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
D = os.path.join(ROOT, "src", "main", "resources", "data", "isekai_mahou")
MERGE = os.path.join(ROOT, "src", "main", "merge", "class_rogue")
NS = "isekai_mahou"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# =====================================================================================================
#  Adagas (motor de modelos 3D do armory)
# =====================================================================================================
M = AR.M
PHANTOM_BLADE = M("blade", "ffffff", "f4f0ff", "d8d0ec", "9a90b8")
SHADOW_WRAP = M("wrap", "b8a0e0", "5a4088", "3a2858", "1c1230")
CRIMSON_WRAP = AR.RED_WRAP

DAGGER_IDS = ["rogue_dagger", "rogue_dagger_t1", "rogue_dagger_t2", "rogue_dagger_t3", "rogue_dagger_t4"]
DAGGER_LOOK = [
    # lamina,          metal,        empunhadura,    fio que brilha,  gema do pomo
    (AR.STEEL_BLADE, AR.IRON, AR.LEATHER, None, None),
    (AR.STEEL_BLADE, AR.DARK_IRON, AR.BLACK_WRAP, None, None),
    (AR.DARK_BLADE, AR.SILVER, SHADOW_WRAP, AR.RUNE_VIOLET, AR.GEM_VIOLET),
    (AR.OBSIDIAN_BLADE, AR.DARK_IRON, CRIMSON_WRAP, AR.GLOW_CRIMSON, AR.GEM_RED),
    (PHANTOM_BLADE, AR.GOLD, AR.WHITE_WRAP, AR.GLOW_VIOLET, AR.GEM_VIOLET),
]


def dagger_model(t):
    """Adaga do Ladino: lamina de ponta caida (dorso reto, fio curvo), guarda curva, pomo em anel nos tiers altos."""
    blade, metal, wrap, edge, gemm = DAGGER_LOOK[t]
    m = AR.Model(DAGGER_IDS[t])
    # pomo: bola (t0/t1) ou anel (t2+) com gema
    if t >= 2:
        m.box((7.0, 2.2, 7.6), (9.0, 2.8, 8.4), metal)
        m.box((7.0, 4.0, 7.6), (9.0, 4.6, 8.4), metal)
        m.box((6.6, 2.2, 7.6), (7.2, 4.6, 8.4), metal)
        m.box((8.8, 2.2, 7.6), (9.4, 4.6, 8.4), metal)
        if gemm:
            AR.gem(m, 8, 3.4, 8, 0.8, 1.0, gemm)
    else:
        m.cbox(8, 3.6, 8, 1.6, 1.2, 1.6, metal, rot_y45=t >= 1)
    AR.band(m, 4.4, 8.2, wrap, 1.4)
    # guarda curva: barra + pontas inclinadas para a lamina
    m.box((6.0, 8.2, 7.35), (10.0, 9.0, 8.65), metal)
    AR.prong(m, 6.2, 8, 8.5, 1.5 if t else 1.1, "z", 22.5, metal, 0.7)
    AR.prong(m, 9.8, 8, 8.5, 1.5 if t else 1.1, "z", -22.5, metal, 0.7)
    if t == 4:
        # guarda em losango (cartao de visita)
        m.cbox(8, 8.6, 8, 1.6, 1.6, 1.5, AR.GOLD, rot=("z", 45, (8, 8.6, 8)))
        AR.gem(m, 8, 8.6, 7.1, 0.8, 0.8, AR.GEM_VIOLET)
    # lamina: dorso reto a direita (x 9.0), fio a esquerda curvando ate a ponta
    long = 1.0 if t >= 3 else 0.0
    m.box((6.9, 9.0, 7.65), (9.0, 13.0, 8.35), blade)
    m.box((7.1, 13.0, 7.67), (9.0, 15.6 + long * 0.5, 8.33), blade)
    m.box((7.5, 15.6 + long * 0.5, 7.7), (9.0, 17.6 + long, 8.3), blade)
    m.box((8.0, 17.6 + long, 7.73), (9.0, 18.8 + long, 8.27), blade)
    m.box((8.5, 18.8 + long, 7.76), (9.0, 19.6 + long, 8.24), blade)
    if t >= 3:
        # serrilha no dorso
        for y in (10.4, 11.8, 13.2, 14.6):
            m.cbox(9.05, y, 8, 0.6, 0.6, 0.5, blade, rot=("z", 45, (9.05, y, 8)))
    if edge:
        m.box((7.25, 9.6, 7.6), (7.65, 15.0, 8.4), edge)
    return m


def icon_dagger(t):
    """Icone de UMA adaga na diagonal (cabo embaixo-esquerda, ponta em cima-direita)."""
    blade, metal, wrap, edge, gemm = DAGGER_LOOK[t]
    c = Canvas(16, 16)
    bh, bl, bm, bd = AR.P(blade)
    gh, gl, gm, gd = AR.P(metal)
    wh, wl, wm, wd = AR.P(wrap)
    n = 7 + (1 if t >= 3 else 0)
    for i in range(n):
        x, y = 6 + i, 9 - i
        c.set(x, y, bm)
        c.set(x, y - 1, bh if i % 2 == 0 else bl)
        if i < n - 2:
            c.set(x + 1, y, bd)
    c.set(6 + n, 9 - n, bl)
    if edge is not None:
        eh, el, em, ed = AR.P(edge)
        for i in (1, 2, 3):
            c.set(6 + i, 8 - i, el)
    if t >= 3:
        for i in (1, 3, 5):
            c.set(8 + i, 10 - i, bd)
    # guarda (anti-diagonal)
    for (x, y) in ((4, 8), (5, 9), (6, 10), (7, 11)):
        c.set(x, y, gl)
    c.set(4, 7, gm)
    c.set(8, 11, gd)
    # cabo
    for (x, y) in ((4, 11), (3, 12), (2, 13)):
        c.set(x, y, wl)
        c.set(x + 1, y, wd)
    # pomo
    if gemm is not None:
        AR.blob(c, 2.0, 14.0, 1.1, gemm, sparkle=False)
    else:
        c.set(1, 14, gl); c.set(2, 14, gm); c.set(1, 15, gd)
    glow = [(6 + n, 9 - n, (255, 255, 255, 255))] if t == 4 else []
    return AR.finish(c, glow)


def knife_model():
    """Faca de arremesso: anel no pomo, cabo enrolado, lamina em folha."""
    m = AR.Model("rogue_throwing_knife")
    metal, wrap, blade = AR.DARK_IRON, AR.BLACK_WRAP, AR.STEEL_BLADE
    m.box((7.2, 3.0, 7.7), (8.8, 3.5, 8.3), metal)
    m.box((7.2, 4.5, 7.7), (8.8, 5.0, 8.3), metal)
    m.box((6.9, 3.0, 7.7), (7.4, 5.0, 8.3), metal)
    m.box((8.6, 3.0, 7.7), (9.1, 5.0, 8.3), metal)
    AR.band(m, 5.0, 8.4, wrap, 1.1)
    m.box((7.1, 8.4, 7.6), (8.9, 9.0, 8.4), metal)
    m.box((7.2, 9.0, 7.7), (8.8, 11.4, 8.3), blade)
    m.box((7.0, 11.4, 7.72), (9.0, 13.2, 8.28), blade)
    m.box((7.4, 13.2, 7.75), (8.6, 14.6, 8.25), blade)
    m.box((7.8, 14.6, 7.78), (8.2, 15.6, 8.22), blade)
    return m


def icon_knife():
    c = Canvas(16, 16)
    bh, bl, bm, bd = AR.P(AR.STEEL_BLADE)
    gh, gl, gm, gd = AR.P(AR.DARK_IRON)
    wh, wl, wm, wd = AR.P(AR.BLACK_WRAP)
    # lamina em folha
    pts = [(9, 6), (10, 5), (11, 4), (12, 3), (13, 2)]
    for i, (x, y) in enumerate(pts):
        c.set(x, y, bm)
        c.set(x, y - 1, bh)
        c.set(x + 1, y, bd if i < 4 else bl)
    c.set(10, 6, bl); c.set(11, 5, bm)
    c.set(8, 7, gl); c.set(9, 8, gm); c.set(7, 6, gm)
    for (x, y) in ((7, 8), (6, 9), (5, 10)):
        c.set(x, y, wl); c.set(x + 1, y, wd)
    # anel
    for (x, y) in ((3, 11), (4, 11), (3, 12), (2, 12), (2, 13), (3, 13), (4, 12)):
        c.set(x, y, gl)
    c.set(3, 12, (0, 0, 0, 0))
    return AR.finish(c)


def gen_weapons():
    disp = AR.DISPLAY["dagger"]
    items = [(DAGGER_IDS[t], dagger_model(t), icon_dagger(t)) for t in range(5)]
    items.append(("rogue_throwing_knife", knife_model(), icon_knife()))
    for iid, model, icon in items:
        tex = NS + ":item/class_rogue/" + iid
        held, atlas = model.build(tex, seed=zlib.crc32(iid.encode()) & 0xFFFF)
        held["display"] = disp
        atlas.save(os.path.join(A, "textures", "item", "class_rogue", iid + ".png"))
        write_json(os.path.join(A, "models", "item", "class_rogue", iid + "_held.json"), held)
        icon.save(os.path.join(A, "textures", "item", iid + ".png"))
        flat = {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + iid}}
        write_json(os.path.join(A, "models", "item", iid + ".json"), {
            "loader": "neoforge:separate_transforms",
            "gui_light": "front",
            "textures": {"particle": NS + ":item/" + iid},
            "base": {"parent": NS + ":item/class_rogue/" + iid + "_held"},
            "perspectives": {"gui": flat, "fixed": flat},
        })
    print("  adagas e faca: %d modelos" % len(items))


# =====================================================================================================
#  Icones: Grimorio Roubado, Livro-Razao e aba
# =====================================================================================================
def icon_grimoire():
    c = Canvas(16, 16)
    cover = [rgb("1C1230"), rgb("2E2058"), rgb("443384"), rgb("5C4AA8")]
    page = [rgb("B8AC8C"), rgb("DCD2B4"), rgb("F4ECD6")]
    silver = [rgb("4A4E5C"), rgb("AEB4C4"), rgb("F6FAFF")]
    # paginas (lombada a esquerda)
    c.rect(4, 3, 13, 13, page[1])
    for y in range(4, 13, 2):
        c.rect(5, y, 12, y, page[2])
    # capa
    c.rect(2, 2, 11, 13, cover[1])
    c.rect(3, 3, 10, 12, cover[2])
    c.rect(2, 2, 3, 13, cover[0])
    for x in range(3, 11):
        c.set(x, 2, cover[3])
    # fecho de prata com cadeado
    c.rect(10, 6, 13, 9, silver[1])
    c.set(13, 7, silver[2]); c.set(13, 8, silver[0])
    c.set(11, 7, silver[0]); c.set(11, 8, silver[0])
    # sigilo que brilha (olho/losango roxo)
    glow = rgb("C08AFF")
    for (x, y) in ((6, 5), (5, 6), (7, 6), (4, 7), (8, 7), (5, 8), (7, 8), (6, 9)):
        c.set(x, y, glow)
    c.set(6, 7, rgb("FFFFFF"))
    # pagina rasgada saindo por baixo
    c.set(12, 14, page[1]); c.set(13, 14, page[0]); c.set(11, 14, page[2])
    return AR.finish(c)


def icon_ledger():
    c = Canvas(16, 16)
    cover = [rgb("2A1A10"), rgb("45301C"), rgb("64462A"), rgb("80603A")]
    page = [rgb("B8AC8C"), rgb("DCD2B4"), rgb("F4ECD6")]
    gold = [rgb("6A4508"), rgb("D49A26"), rgb("F5C84A"), rgb("FFF0A8")]
    c.rect(3, 3, 13, 13, page[1])
    c.rect(2, 2, 12, 13, cover[1])
    c.rect(3, 3, 11, 12, cover[2])
    c.rect(2, 2, 3, 13, cover[0])
    for x in range(3, 12):
        c.set(x, 2, cover[3])
    # linhas de contas na capa
    for y in (5, 7, 9):
        for x in range(5, 10):
            c.set(x, y, cover[0] if x % 2 else cover[3])
    # moeda
    c.circle(11.5, 11.5, 2.3, gold[1])
    c.set(11, 10, gold[3]); c.set(10, 11, gold[2]); c.set(12, 12, gold[0]); c.set(11, 11, gold[2])
    # pena
    feather = [rgb("A0A0A0"), rgb("E0E0E0"), rgb("FFFFFF")]
    for i in range(5):
        c.set(9 + i, 5 - i, feather[2] if i % 2 else feather[1])
        c.set(10 + i, 5 - i, feather[0])
    c.set(8, 6, rgb("2A2A2A"))
    return AR.finish(c)


def icon_tab():
    """Capuz com dois olhos roxos (aba Ladino)."""
    c = Canvas(16, 16)
    hood = [rgb("0E0A14"), rgb("1E1428"), rgb("30203E"), rgb("45305A")]
    rows = [
        "......oooo......",
        "....oo3333oo....",
        "...o33322333o...",
        "..o3322222233o..",
        "..o3221111223o..",
        ".o322100001223o.",
        ".o32100000012 3o",
        ".o3210e00e0123o.",
        ".o32100000012 3o",
        ".o3221000012 23o",
        "..o322111122 3o.",
        "..o33222222 33o.",
        "...o333333333o..",
        "....oooooooooo..",
        "................",
        "................",
    ]
    pal = {"o": rgb("07050C"), "0": hood[0], "1": hood[1], "2": hood[2], "3": hood[3], "e": rgb("C08AFF"), " ": hood[2]}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            if ch != ".":
                c.set(x, y, pal[ch])
    return c


def gen_icons():
    for iid, fn in (("rogue_stolen_grimoire", icon_grimoire), ("rogue_ledger", icon_ledger)):
        fn().save(os.path.join(A, "textures", "item", iid + ".png"))
        item_model(A, iid)
    icon_tab().save(os.path.join(A, "textures", "gui", "tab", "rogue.png"))
    print("  icones: grimorio, livro-razao, aba")


# =====================================================================================================
#  Traje das Sombras (motor de trajes do outfits)
# =====================================================================================================
pal = OF.pal
HD, BD, RA, LA, RL, LL, RB, LB = OF.HD, OF.BD, OF.RA, OF.LA, OF.RL, OF.LL, OF.RB, OF.LB
fc, OMIT = OF.fc, OF.OMIT

IRON_T = pal("2A2A2E", "44444A", "626268", "86868E", "AEAEB6")
SILVER_T = OF.SILVER_M
GOLD_T = OF.GOLD_R
THEMES = {
    # I Gatuno: couro escuro e pano carvão, cachecol ameixa
    "t1": OF.theme(pal("141012", "1E181A", "2A2226", "3A3034", "4E4448"), pal("1E140E", "2E2018", "422D22", "58402E", "70563E"),
                   IRON_T, OF.LEATHER_DARK, pal("2E0E22", "4A1636", "662250", "84346C", "A44E8A"), IRON_T,
                   pal("2A0A3A", "5A1E8A", "B07EE8", "E8D4FF"), "B07EE8"),
    # II Larápio Sombrio: azul meia-noite e ardósia, violeta
    "t2": OF.theme(pal("0A0E1A", "121A2C", "1C2842", "2A3A5C", "3E5278"), pal("161A22", "222834", "323A4A", "465062", "5E687C"),
                   pal("30323A", "50545E", "7A808C", "A8AEB8", "E0E4EA"), OF.LEATHER_DARK,
                   pal("22104A", "361A72", "5028A0", "6E40C8", "9A70E8"), pal("30323A", "50545E", "7A808C", "A8AEB8", "E0E4EA"),
                   pal("2A0A6A", "5A2EC8", "8E7BFF", "DCD4FF"), "8E7BFF"),
    # III Mestre-Ladrao: preto-ametista, prata e carmesim
    "t3": OF.theme(pal("08060C", "120E18", "1E1626", "2C2036", "3E2E4C"), pal("160E1E", "241830", "362446", "4A3260", "62447C"),
                   SILVER_T, pal("140E14", "221822", "322432", "443244", "5A445A"),
                   pal("3A0610", "600A1C", "8A1428", "B02440", "D84A64"), SILVER_T,
                   pal("2A0A5A", "5A1EA8", "C08AFF", "ECDCFF"), "C08AFF"),
    # IV Ladrao Fantasma: casaca branca, forro azul real, ouro e monoculo
    "t4": OF.theme(pal("9A96A8", "C2BED0", "E2DEEC", "F4F2FA", "FFFFFF"), pal("6E6A80", "8E8AA2", "B0ACC4", "D0CCE0", "ECEAF6"),
                   GOLD_T, pal("16141E", "24202E", "34304A", "48425E", "5E5878"),
                   pal("0A1648", "142A7A", "2042B0", "3A62D8", "6A8CF0"), GOLD_T,
                   pal("3A1A6A", "7A4AD8", "F2ECFF", "FFFFFF"), "E8DCFF"),
}
LV = {"t1": 0, "t2": 1, "t3": 2, "t4": 3}
GLOWS = {"t1": False, "t2": True, "t3": True, "t4": True}
PIECES = ("helmet", "chestplate", "leggings", "boots")


def shade_mats(th, lv):
    P = th["pal"]
    Mt = OF.rogue_mats(P, th, lv)
    MAIN, SEC, TRIM, LEATH, ACC = (P[k] for k in ("MAIN", "SEC", "TRIM", "LEATH", "ACC"))
    GL, GEM = th["GLOW"], th["GEM"]
    # cartola do Ladrao Fantasma (grau IV)
    Mt["hat_crown"] = lambda r: OF.cloth(r, MAIN, period=4, base=2, top=0.06, bottom=0.10, folds=False)
    Mt["hat_top"] = lambda r: (r.fill(MAIN[3]), r.noise(0.02))
    Mt["hat_brim"] = lambda r: (r.fill(MAIN[2]), r.row(0, MAIN[4]), r.noise(0.02))
    Mt["hat_brim_top"] = lambda r: (r.fill(MAIN[3]), r.noise(0.02))
    Mt["hat_brim_under"] = lambda r: (r.fill(MAIN[1]), r.noise(0.02))

    def band(r):
        r.fill(ACC[2])
        r.row(0, ACC[3])
        if r.h > 1:
            r.row(-1, ACC[1])
        if lv >= 3:
            for i in range(1, r.w - 1, 4):
                r.glow(i, r.h // 2, GL)
    Mt["hat_band"] = band
    Mt["tie"] = lambda r: (r.fill(OF.H("B0202A")), r.col(0, OF.H("E0404A")), r.noise(0.03))
    Mt["monocle"] = OF.gem_mat(GEM)

    def coat_out(r):
        OF.cloth(r, MAIN, period=4, base=2, top=0.04, bottom=0.10)
        OF.trim_rows(r, TRIM, 2)
        if lv >= 3:
            cx = r.w // 2
            for k, j in enumerate(range(3, r.h - 6, 5)):
                OF.rune_glyph(r, cx - 1, j, GL, k + 1)
    Mt["coat_out"] = coat_out
    Mt["coat_in"] = lambda r: OF.cloth(r, ACC, period=4, base=2, folds=False)

    def knife_belt(r):
        r.fill(LEATH[1])
        r.row(0, LEATH[3])
        for i in range(1, r.w - 1, 2):
            r.set(i, r.h // 2, TRIM[3])
        r.noise(0.03)
    Mt["knife_belt"] = knife_belt
    return Mt


def coat_tails(m, lv):
    """Abas da casaca do grau IV: duas caudas longas atras (osso outfitCape, sobe ao correr)."""
    m.bone("outfitCape", [0, 13.0, 2.6], parent=BD, rot=[6, 0, 0])
    for x0 in (-3.9, 0.1):
        m.box("outfitCape", [x0, 2.0, 2.6], [3.8, 11.0, 0.5],
              fc(s="coat_out", n="coat_in", ew="cape_edge@1x8", u="cape_edge@2x1", d=OMIT))


def shade_model(name, lv):
    m = OF.Model(name)
    OF.skeleton(m)
    # ---------------- cabeça: capuz com máscara (I-III) / cartola com monóculo (IV)
    if lv < 3:
        m.box(HD, [-4, 24, -4], [8, 8, 8], fc(n="hood_front", s="hood", ew="hood", u="hood", d=OMIT), inflate=0.7)
        m.box(HD, [-3.4, 31.1, -4.0], [6.8, 1.0, 1.0], fc(all="hood@4x1"))
        m.bone("hatMid", [0, 30.6, 4.1], parent=HD, rot=[30, 0, 0])
        m.box("hatMid", [-1.8, 27.6, 3.9], [3.6, 3.0 + 0.8 * lv, 1.4], fc(side="hood", u="hood@2x2", d="hood@2x2"))
        if lv >= 2:
            OF.gem(m, HD, [0, 31.3, -4.85], 0.8)
    else:
        m.box(HD, [-5.6, 31.5, -5.6], [11.2, 0.6, 11.2], fc(side="hat_brim", u="hat_brim_top", d="hat_brim_under"))
        m.box(HD, [-3.7, 32.1, -3.7], [7.4, 5.6, 7.4], fc(side="hat_crown", u="hat_top", d=OMIT))
        m.box(HD, [-3.8, 32.1, -3.8], [7.6, 1.3, 7.6], fc(side="hat_band", u=OMIT, d=OMIT))
        OF.gem(m, HD, [-1.9, 28.4, -4.35], [1.7, 1.7, 0.3], mat="monocle@2x2")
        m.box(HD, [-2.85, 24.6, -4.32], [0.3, 3.2, 0.3], fc(all="o_chain@1x3"))
    # ---------------- tronco: gibão, gola alta, bandoleira de facas, cinto com bolsinhas
    m.box(BD, [-4, 12, -2], [8, 12, 4], fc(n="jerkin_front", s="jerkin", ew="jerkin", u="jerkin", d="dark@2x2"), inflate=0.55)
    m.box(BD, [-4.4, 22.0, -2.5], [8.8, 2.4, 5.0], fc(side="scarf", u="scarf@4x2", d=OMIT))
    if lv >= 3:
        m.box(BD, [-0.6, 19.4, -2.98], [1.2, 2.6, 0.4], fc(all="tie@1x2"))
    OF.strap_diag(m, BD, "strap", -2.75, 1, width=0.8)
    for (bx, by) in ((-2.4, 20.6), (-0.9, 18.4), (0.6, 16.2)) if lv >= 1 else ((-2.4, 20.6), (-0.9, 18.4)):
        OF.dagger(m, BD, [bx, by, -3.15], [0, 0, 33.7], [bx, by, -3.15], 1.2)
    m.box(BD, [-4, 12.0, -2], [8, 1.4, 4], fc(n="belt_front", s="belt", ew="belt", u="leather_top@2x2", d="leather_top@2x2"),
          inflate=0.85)
    m.box(BD, [-4.9, 11.0, -1.0], [1.3, 2.0, 2.0], fc(side="pouch@2x3", u="leather_top@2x2", d="sole@2x2"))
    m.box(BD, [3.6, 11.0, -1.4], [1.3, 2.0, 2.0], fc(side="pouch@2x3", u="leather_top@2x2", d="sole@2x2"))
    m.box(BD, [-1.0, 11.0, 2.55], [2.0, 2.0, 1.1], fc(side="pouch@2x3", u="leather_top@2x2", d="sole@2x2"))
    if lv >= 1:
        OF.gem(m, BD, [0, 12.7, -2.95], [1.0, 1.0, 0.4])
    OF.waist(m, "Chest")
    OF.skirt(m, "Chest", 9.4 - 0.6 * lv, "tasset", "tasset", "tasset", "tasset_in@4x4", "tasset", flare=4, depth=0.5, width=9.2)
    # costas: pontas do cachecol (I), capa curta (II), capa longa com runas (III), caudas da casaca (IV)
    if lv == 0:
        m.bone("outfitCape", [0, 23, 2.75], parent=BD, rot=[8, 0, 0])
        for x0, low, rz in ((-2.3, 14.5, -6), (0.3, 16.0, 8)):
            m.box("outfitCape", [x0, low, 2.75], [2.0, 23 - low, 0.3],
                  fc(s="scarf_tail", n="scarf_in", ew="cape_edge@1x4", u=OMIT, d=OMIT), rot=[0, 0, rz], pivot=[x0 + 1.0, 23, 2.9])
    elif lv < 3:
        OF.cape(m, 24.0, {1: 11.0, 2: 4.0}[lv], 2.8, 8.6, out="cape_out", inn="cape_in", edge="cape_edge", flare=5)
    else:
        coat_tails(m, lv)
    # ---------------- braços: mangas, braçadeiras, lâmina escondida no pulso, ombreiras
    m.box(RA, [-8, 15, -2], [4, 9, 4], fc(side="sleeve", u="jerkin@2x2", d=OMIT), inflate=0.5)
    m.box(RA, [-8, 12, -2], [4, 4, 4], fc(side="bracer", u="leather_top@2x2", d="dark@2x2"), inflate=0.68)
    m.mirror(RA, LA)
    if lv >= 1:
        m.box(RA, [-6.4, 11.2, -3.4], [0.4, 0.4, 2.6], fc(all="blade@1x2"))
    if lv >= 2:
        OF.pauldrons(m, 1 if lv == 2 else 2, gem_mat="o_gem@2x2", gem_size=1.0)
    # ---------------- pernas: calças, correia no joelho, bainha na coxa direita
    m.box(RL, [-3.9, 0, -2], [4, 12, 4], fc(side="trousers", u=OMIT, d=OMIT), inflate=0.35)
    m.box(RL, [-3.9, 8.6, -2], [4, 0.8, 4], fc(side="strap@4x1", u=OMIT, d=OMIT), inflate=0.55)
    m.box(RL, [-3.9, 4.6, -2], [4, 0.6, 4], fc(side="knife_belt@4x1", u=OMIT, d=OMIT), inflate=0.55)
    OF.dagger(m, RL, [-4.45, 8.4, -0.3], None, None, 2.4)
    m.mirror(RL, LL)
    # ---------------- botas
    m.box(RB, [-3.9, 0, -2], [4, 5.5, 4], fc(side="boot", u=OMIT, d="sole@2x2"), inflate=0.55)
    m.box(RB, [-3.9, 5.0, -2], [4, 1.0, 4], fc(side="leather@4x1", u="dark@2x2", d="dark@2x2"), inflate=0.75)
    if lv >= 2:
        m.box(RB, [-3.6, 0.2, -2.9], [3.4, 1.4, 0.6], fc(side="trim_plate@3x1", u="trim_flat@3x1", d=OMIT))
    if lv == 3:
        m.box(RB, [-4.7, 1.5, -0.25], [0.5, 3.0, 0.5], fc(all="blade@1x2"))
    m.mirror(RB, LB)
    return m


# ------------------------------------------------------------------ icones das pecas (desenho do Ladino do outfits)
TOPHAT = [
    "................",
    "....oooooooo....",
    "....oLLlllmo....",
    "....olllllmo....",
    "....olllllmo....",
    "....ouuwuuvo....",
    "..oooooooooooo..",
    ".oLLllllllllmmo.",
    "..oooooooooooo..",
    "................",
    ".....oxXo.......",
    ".....oXxo.......",
    "......oo........",
    "......h.........",
    "......h.........",
    "................",
]


def draw_piece_icon(tkey, piece):
    th = THEMES[tkey]
    lv = LV[tkey]
    cols = OF.v9_icon_palette(th)
    c = Canvas(16, 16)
    rows = TOPHAT if (piece == "helmet" and lv == 3) else OF.v9_icon_rows("rogue", piece)
    for y, row in enumerate(rows[:16]):
        row = (row + "." * 16)[:16]
        for x, ch in enumerate(row):
            if ch != "." and ch in cols:
                c.set(x, y, cols[ch])
    G = th["GEM"]
    if lv >= 1 and not (piece == "helmet" and lv == 3):
        gx, gy = OF.V9_HELMETS["rogue"][1] if piece == "helmet" else OF.V9_ICON_FILL["rogue"]["gems"][piece]
        if lv >= 2:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if c.get(gx + dx, gy + dy)[3] > 0:
                    c.set(gx + dx, gy + dy, G[1] if lv == 2 else G[2])
        c.set(gx, gy, G[2] if lv == 1 else G[3])
    if lv == 3:
        glow_c = lighten(th["GLOW"], 0.2)
        OF.sparkle(c, (14, 1), glow_c)
        OF.sparkle(c, (1, 14), glow_c)
    elif lv >= 1:
        OF.tier_pip(c, (14, 1), G)
        if lv == 2:
            OF.tier_pip(c, (1, 14), G)
    name = "rogue_shade_%s_%s" % (tkey, piece)
    c.save(os.path.join(A, "textures", "item", name + ".png"))
    item_model(A, name)


def vanilla_layers(name, main, trim, dark):
    """Reserva no formato vanilla (so aparece se o GeckoLib faltar)."""
    import random
    c1 = Canvas(64, 32)
    rnd = random.Random(len(name))
    for (x0, y0, w, h) in [(0, 0, 32, 16), (16, 16, 24, 16), (40, 16, 16, 16), (0, 16, 16, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                col = main if (x + y) % 5 else dark
                if rnd.random() < 0.08:
                    col = trim
                c1.set(x, y, col)
    c1.save(os.path.join(A, "textures", "models", "armor", name + "_layer_1.png"))
    c2 = Canvas(64, 32)
    for (x0, y0, w, h) in [(0, 16, 16, 16), (16, 16, 24, 16)]:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c2.set(x, y, dark if (x // 2 + y) % 4 == 0 else main)
    c2.save(os.path.join(A, "textures", "models", "armor", name + "_layer_2.png"))


def gen_armor():
    # o motor do outfits escreve em OF.A (mesma pasta de assets)
    for tkey in ("t1", "t2", "t3", "t4"):
        lv = LV[tkey]
        th = THEMES[tkey]
        name = "rogue_shade_" + tkey
        _, glow = OF.build(shade_model(name, lv), shade_mats(th, lv))
        if glow != GLOWS[tkey]:
            raise RuntimeError("%s: glowmask=%s, esperado %s (RogueGear.glows)" % (name, glow, GLOWS[tkey]))
        P = th["pal"]
        vanilla_layers(name, P["MAIN"][2], P["TRIM"][2], P["MAIN"][0])
        for piece in PIECES:
            draw_piece_icon(tkey, piece)
    print("  traje: 4 graus, 16 icones")


# =====================================================================================================
#  Receitas e tags
# =====================================================================================================
def item(i):
    return {"item": i if ":" in i else NS + ":" + i}


def shaped(name, pattern, key, result, count=1):
    # o 1.21 recusa receita cuja chave define simbolo que o padrao nao usa: so entram os simbolos usados
    used = set("".join(pattern)) - {" "}
    write_json(os.path.join(D, "recipe", "class_rogue", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": pattern, "key": {k: item(v) for k, v in key.items() if k in used},
        "result": {"id": NS + ":" + result, "count": count}})


def shapeless(name, ingredients, result, count=1):
    write_json(os.path.join(D, "recipe", "class_rogue", name + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": "equipment",
        "ingredients": [item(i) for i in ingredients],
        "result": {"id": NS + ":" + result, "count": count}})


def gen_recipes():
    shaped("rogue_dagger", ["I", "L"], {"I": "minecraft:iron_ingot", "L": "minecraft:leather"}, "rogue_dagger")
    shapeless("rogue_throwing_knife", ["minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:stick"], "rogue_throwing_knife", 4)
    shapeless("rogue_ledger", ["minecraft:book", "minecraft:gold_nugget", "minecraft:ink_sac", "minecraft:feather"], "rogue_ledger")
    shapeless("rogue_stolen_grimoire", ["minecraft:book", "minecraft:ender_pearl", "minecraft:black_dye", "essence_shadow"],
              "rogue_stolen_grimoire")
    keys = {"L": "minecraft:leather", "K": "minecraft:black_wool", "S": "minecraft:string"}
    shaped("rogue_shade_t1_helmet", ["LKL", "S S"], keys, "rogue_shade_t1_helmet")
    shaped("rogue_shade_t1_chestplate", ["L L", "KSK", "LKL"], keys, "rogue_shade_t1_chestplate")
    shaped("rogue_shade_t1_leggings", ["KSK", "L L", "L L"], keys, "rogue_shade_t1_leggings")
    shaped("rogue_shade_t1_boots", ["K K", "L L"], keys, "rogue_shade_t1_boots")
    print("  receitas: 8")


def tag(path, values):
    write_json(os.path.join(MERGE, "data", path), {"replace": False, "values": [NS + ":" + v for v in values]})


def gen_tags():
    daggers = ["rogue_dagger", "rogue_dagger_t1", "rogue_dagger_t2", "rogue_dagger_t3", "rogue_dagger_t4"]
    tag("minecraft/tags/item/swords.json", daggers)
    tag("c/tags/item/tools/melee_weapon.json", daggers)
    for piece, t in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        tag("minecraft/tags/item/%s.json" % t, ["rogue_shade_%s_%s" % (g, piece) for g in ("t1", "t2", "t3", "t4")])
    print("  tags (merge)")


def main():
    print("== class_rogue")
    gen_weapons()
    gen_icons()
    gen_armor()
    gen_recipes()
    gen_tags()
    print("class_rogue: assets, receitas e tags gerados")


if __name__ == "__main__":
    main()
