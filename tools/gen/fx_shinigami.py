"""
Texturas de EFEITOS (VFX) das classes Shinigami e Quincy (pacote "efeitos de anime").
Uso (na raiz do projeto):  py -I tools/gen/fx_shinigami.py

Gera em assets/isekai_mahou/textures/entity/ (todas BRANCAS/claras com alfa: o Java tinge pela cor do vertice):
  Shinigami
  - shinigami_fx_petal.png   petala-lamina da Senbonzakura (ponta afiada, entalhe de sakura)
  - shinigami_fx_glow.png    brilho radial suave
  - shinigami_fx_ring.png    anel de onda de choque (pressao espiritual)
  - shinigami_fx_sword.png   lamina gigante (Kageyoshi / Senkei) - ponta para BAIXO
  - shinigami_fx_ghost.png   silhueta humana (imagem residual do Shunpo)
  - shinigami_fx_slash.png   risco de corte afilado (Mugetsu na tela, linhas de velocidade)
  - shinigami_fx_mask.png    mascara do Vazio vista de frente (lampejo do Visored) - JA colorida
  - shinigami_fx_spike.png   lanca de trevas violeta (Kurohitsugi)
  - shinigami_fx_flame.png   lingua de chama/aura (Vasto Lorde, coluna de liberacao)
  Quincy
  - quincy_fx_glow.png, quincy_fx_wing.png (asa de penas de luz), quincy_fx_halo.png (aureola com raios),
    quincy_fx_star.png (Quincy Zeichen), quincy_fx_streak.png (rastro da flecha), quincy_fx_eye.png (olho do
    Todo-Poderoso, JA colorido), quincy_fx_beam.png (pilar de luz)
E o item: textures/item/zanpakuto_petals_hilt.png (so o cabo: a lamina virou petalas) + models/item/zanpakuto_petals_hilt.json
NAO mexe em nenhuma outra textura.
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
ENT = os.path.join(A, "textures/entity")
ITEM = os.path.join(A, "textures/item")


def save(im, name, folder=ENT):
    os.makedirs(folder, exist_ok=True)
    im.save(os.path.join(folder, name))
    print("ok", name)


def clamp(v):
    return max(0, min(255, int(round(v))))


def radial(size, power=2.0, inner=0.0):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = im.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / (size / 2)
            a = max(0.0, 1.0 - d)
            a = a ** power
            if d < inner:
                a = 1.0
            px[x, y] = (255, 255, 255, clamp(255 * a))
    return im


def petal():
    # petala-lamina: base arredondada embaixo, ponta afiada em cima, entalhe pequeno na base (sakura)
    s = 64
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    px = im.load()
    for y in range(s):
        for x in range(s):
            u = (x - s / 2 + 0.5) / (s / 2)        # -1..1
            v = (y + 0.5) / s                     # 0 (topo/ponta) .. 1 (base)
            # largura: 0 na ponta, maxima a 65%, arredondada na base
            if v < 0.65:
                w = math.sin(v / 0.65 * math.pi / 2) ** 1.4 * 0.62
            else:
                w = math.sqrt(max(0.0, 1 - ((v - 0.65) / 0.35) ** 2)) * 0.62
            if abs(u) > w:
                continue
            # entalhe da base
            if v > 0.86 and abs(u) < (v - 0.86) * 1.6:
                continue
            edge = 1 - abs(u) / max(w, 1e-3)
            vein = max(0.0, 1 - abs(u) * 14)
            r = 255
            g = clamp(232 + 23 * edge)
            b = clamp(240 + 15 * edge)
            a = 1.0 if edge > 0.18 else edge / 0.18
            light = 0.78 + 0.22 * edge + 0.15 * vein * (1 - v)
            px[x, y] = (clamp(r * light), clamp(g * light), clamp(b * light), clamp(255 * a))
    im = im.resize((16, 16), Image.LANCZOS)
    return im


def ring():
    s = 128
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    px = im.load()
    c = (s - 1) / 2
    for y in range(s):
        for x in range(s):
            d = math.hypot(x - c, y - c) / (s / 2)
            # banda forte perto da borda, rastro suave por dentro
            band = math.exp(-((d - 0.88) / 0.05) ** 2)
            trail = max(0.0, (d - 0.45) / 0.43) ** 2 * 0.35 if d < 0.88 else 0
            a = min(1.0, band + trail) if d <= 1.0 else 0
            px[x, y] = (255, 255, 255, clamp(255 * a))
    return im


def sword():
    # 32x128: cabo em cima, lamina descendo, ponta embaixo
    w, h = 32, 128
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx = w / 2
    # cabo
    d.rectangle([cx - 2.5, 0, cx + 2.5, 22], fill=(170, 160, 175, 255))
    for yy in range(2, 22, 4):
        d.line([cx - 2.5, yy, cx + 2.5, yy + 2], fill=(110, 100, 120, 255))
    # guarda
    d.rectangle([cx - 8, 22, cx + 8, 26], fill=(220, 215, 225, 255))
    # lamina (gradiente de brilho no fio)
    for yy in range(26, h):
        t = (yy - 26) / (h - 26)
        half = 6.5 * (1 - t ** 3) + 0.4
        for xx in range(w):
            u = (xx + 0.5 - cx) / half
            if abs(u) > 1:
                continue
            edge = 1 - abs(u)
            shine = 0.75 + 0.25 * (1 if u > 0.15 else edge)
            px = (clamp(235 * shine + 20), clamp(238 * shine + 17), 255, 255)
            im.putpixel((xx, yy), px)
    glow = im.filter(ImageFilter.GaussianBlur(2.2))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gp = glow.load()
    for yy in range(h):
        for xx in range(w):
            r, g, b, a = gp[xx, yy]
            out.putpixel((xx, yy), (255, 255, 255, clamp(a * 0.55)))
    out.alpha_composite(im)
    return out


def ghost():
    w, h = 32, 64
    im = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(im)
    cx = w / 2
    d.ellipse([cx - 5, 1, cx + 5, 12], fill=255)                     # cabeca
    d.polygon([(cx - 8, 13), (cx + 8, 13), (cx + 9, 34), (cx - 9, 34)], fill=255)   # tronco/haori
    d.polygon([(cx - 8, 14), (cx - 13, 31), (cx - 10, 33), (cx - 6, 18)], fill=255)  # braco esq
    d.polygon([(cx + 8, 14), (cx + 13, 31), (cx + 10, 33), (cx + 6, 18)], fill=255)  # braco dir
    d.polygon([(cx - 9, 33), (cx + 9, 33), (cx + 11, 62), (cx + 2, 62), (cx, 44), (cx - 2, 62), (cx - 11, 62)], fill=255)  # hakama
    im = im.filter(ImageFilter.GaussianBlur(1.2))
    out = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    out.putalpha(im)
    # mais transparente embaixo (some no chao)
    px = out.load()
    for y in range(h):
        f = 1.0 if y < 40 else max(0.25, 1 - (y - 40) / 30)
        for x in range(w):
            r, g, b, a = px[x, y]
            px[x, y] = (255, 255, 255, clamp(a * f))
    return out


def slash():
    w, h = 128, 16
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        for x in range(w):
            t = x / (w - 1)
            thick = math.sin(t * math.pi) ** 0.7 * 0.5
            v = abs((y + 0.5) / h - 0.5)
            if v > thick:
                continue
            core = 1 - v / max(thick, 1e-3)
            a = core ** 0.6
            px[x, y] = (255, 255, 255, clamp(255 * a))
    return im


def mask_front():
    s = 64
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([8, 2, 56, 50], fill=(242, 238, 230, 255))
    d.polygon([(14, 34), (50, 34), (44, 62), (20, 62)], fill=(242, 238, 230, 255))
    # olhos
    d.polygon([(16, 22), (29, 20), (28, 30), (18, 30)], fill=(10, 8, 10, 255))
    d.polygon([(48, 22), (35, 20), (36, 30), (46, 30)], fill=(10, 8, 10, 255))
    # listras rubras
    red = (200, 16, 42, 255)
    d.line([(22, 4), (19, 20)], fill=red, width=3)
    d.line([(42, 4), (45, 20)], fill=red, width=3)
    d.line([(20, 31), (24, 50)], fill=red, width=2)
    d.line([(44, 31), (40, 50)], fill=red, width=2)
    # dentes
    for k in range(6):
        x = 22 + k * 4
        d.line([(x, 48), (x, 58)], fill=(60, 50, 50, 255), width=1)
    d.line([(21, 53), (43, 53)], fill=(60, 50, 50, 255), width=1)
    return im


def spike():
    w, h = 16, 64
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        t = y / (h - 1)   # 0 = ponta, 1 = base
        half = 0.5 + 6.5 * t ** 0.8
        for x in range(w):
            u = (x + 0.5 - w / 2) / half
            if abs(u) > 1:
                continue
            edge = 1 - abs(u)
            c = 0.55 + 0.45 * edge
            px[x, y] = (clamp(200 * c + 40), clamp(120 * c), 255, clamp(255 * min(1, edge * 3)))
    return im


def flame():
    w, h = 32, 64
    rnd = random.Random(7)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        t = 1 - y / (h - 1)  # 0 base .. 1 topo
        half = (1 - t) ** 0.7 * 0.48 + 0.02
        sway = math.sin(t * 6.0) * 0.06 * t
        for x in range(w):
            u = (x + 0.5) / w - 0.5 - sway
            if abs(u) > half:
                continue
            edge = 1 - abs(u) / half
            a = edge ** 0.8 * (1 - t ** 2)
            px[x, y] = (255, 255, 255, clamp(255 * a))
    return im.filter(ImageFilter.GaussianBlur(0.8))


def wing():
    s = 128
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # raiz em (4, 28); penas abrindo para a direita e para baixo
    root = (4, 28)
    feathers = 9
    for k in range(feathers):
        ang = math.radians(-12 + k * 11)
        length = 118 - k * 7
        tip = (root[0] + math.cos(ang) * length, root[1] + math.sin(ang) * length)
        perp = (-math.sin(ang), math.cos(ang))
        wdt = 7 + k * 0.5
        mid = (root[0] + math.cos(ang) * length * 0.55, root[1] + math.sin(ang) * length * 0.55)
        poly = [root,
                (mid[0] + perp[0] * wdt, mid[1] + perp[1] * wdt),
                tip,
                (mid[0] - perp[0] * wdt * 0.6, mid[1] - perp[1] * wdt * 0.6)]
        shade = 200 + k * 6
        d.polygon(poly, fill=(255, 255, 255, min(255, shade)))
    # cobertura (penas pequenas perto da raiz)
    d.ellipse([0, 10, 50, 50], fill=(255, 255, 255, 255))
    blur = im.filter(ImageFilter.GaussianBlur(3))
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    bp = blur.load()
    op = out.load()
    for y in range(s):
        for x in range(s):
            op[x, y] = (255, 255, 255, clamp(bp[x, y][3] * 0.5))
    out.alpha_composite(im.filter(ImageFilter.GaussianBlur(0.6)))
    return out


def halo():
    s = 128
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    px = im.load()
    c = (s - 1) / 2
    for y in range(s):
        for x in range(s):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy) / (s / 2)
            ang = math.atan2(dy, dx)
            band = math.exp(-((d - 0.55) / 0.045) ** 2)
            rays = max(0.0, math.cos(ang * 12)) ** 8 * max(0.0, 1 - abs(d - 0.75) / 0.2) * (1 if d > 0.58 else 0)
            glow = math.exp(-((d - 0.55) / 0.16) ** 2) * 0.35
            a = min(1.0, band + rays * 0.9 + glow)
            px[x, y] = (255, 255, 255, clamp(255 * a))
    return im


def star():
    s = 128
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = s / 2
    r = s * 0.46
    d.ellipse([c - r, c - r, c + r, c + r], outline=(255, 255, 255, 255), width=4)
    d.ellipse([c - r * 0.9, c - r * 0.9, c + r * 0.9, c + r * 0.9], outline=(255, 255, 255, 140), width=2)
    pts = []
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        pts.append((c + math.cos(a) * r * 0.9, c + math.sin(a) * r * 0.9))
    order = [0, 2, 4, 1, 3, 0]
    for i in range(5):
        d.line([pts[order[i]], pts[order[i + 1]]], fill=(255, 255, 255, 255), width=4)
    glow = im.filter(ImageFilter.GaussianBlur(4))
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gp = glow.load()
    op = out.load()
    for y in range(s):
        for x in range(s):
            op[x, y] = (255, 255, 255, clamp(gp[x, y][3] * 0.8))
    out.alpha_composite(im)
    return out


def streak():
    w, h = 64, 16
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        for x in range(w):
            t = x / (w - 1)            # 0 = cauda .. 1 = cabeca
            v = abs((y + 0.5) / h - 0.5) * 2
            thick = 0.15 + 0.85 * t ** 1.5
            if v > thick:
                continue
            a = (1 - v / thick) ** 1.2 * t ** 0.8
            px[x, y] = (255, 255, 255, clamp(255 * a))
    return im


def eye():
    s = 64
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # olho amendoado
    pts_top = [(4 + i * 2, 32 - math.sin(i / 28 * math.pi) * 20) for i in range(29)]
    pts_bot = [(4 + i * 2, 32 + math.sin(i / 28 * math.pi) * 16) for i in range(28, -1, -1)]
    d.polygon(pts_top + pts_bot, fill=(235, 240, 255, 255))
    d.ellipse([18, 16, 46, 46], fill=(40, 70, 150, 255))
    d.ellipse([22, 20, 42, 42], fill=(90, 150, 230, 255))
    # varias pupilas (o Todo-Poderoso)
    for (x, y) in [(32, 31), (27, 26), (37, 26), (27, 36), (37, 36)]:
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(8, 8, 16, 255))
    d.line(pts_top, fill=(10, 10, 20, 255), width=2)
    d.line(pts_bot, fill=(10, 10, 20, 255), width=2)
    return im


def beam():
    w, h = 32, 128
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        t = y / (h - 1)              # 0 topo .. 1 base
        vert = min(1.0, t * 1.6) * (0.6 + 0.4 * t)
        for x in range(w):
            u = abs((x + 0.5) / w - 0.5) * 2
            core = math.exp(-(u / 0.18) ** 2)
            glow = math.exp(-(u / 0.55) ** 2) * 0.5
            a = min(1.0, core + glow) * vert
            px[x, y] = (255, 255, 255, clamp(255 * a))
    return im


def hilt():
    src = Image.open(os.path.join(ITEM, "zanpakuto_shikai_petals.png")).convert("RGBA")
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = x + (15 - y)
            p = src.getpixel((x, y))
            if p[3] > 0 and d <= 11:
                out.putpixel((x, y), p)
            elif p[3] > 0 and d <= 13:
                # onde a lamina se desfaz: pontinhos rosa
                if (x + y) % 2 == 0:
                    out.putpixel((x, y), (255, 150, 200, 200))
    return out


# Traduções dos efeitos novos (mescladas no arquivo do módulo; rodar de novo depois de tools/gen/class_shinigami_lang.py)
LANG = {
    "message.isekai_mahou.shinigami.petals_chire": (
        "Chire! — segure USAR e mire: as pétalas seguem o seu olhar. Solte para refazer a lâmina.",
        "Chire! — hold USE and aim: the petals follow your gaze. Release to reform the blade."),
    "message.isekai_mahou.shinigami.petals_gokei": ("Gōkei! A esfera de pétalas se fecha.", "Gōkei! The sphere of petals closes in."),
    "message.isekai_mahou.shinigami.petals_senkei": ("Senkei: mil espadas cercam o campo de batalha.",
                                                     "Senkei: a thousand swords surround the battlefield."),
    "message.isekai_mahou.shinigami.petals_senkei_cd": ("Senkei recarregando: %s s", "Senkei recharging: %ss"),
    "tooltip.isekai_mahou.zanpakuto.use": (
        "Usar: libera a Shikai (rank III) · toque de novo: sela · Pétalas: SEGURE usar para controlar o enxame",
        "Use: release the Shikai (rank III) · tap again: seal it · Petals: HOLD use to control the swarm"),
    "shinigami.isekai_mahou.archetype.petals.shikai": (
        "Cada corte abre feridas: Sangrando acumulado. SEGURE usar: a lâmina vira um enxame de pétalas que segue a sua mira e corta tudo no caminho.",
        "Every cut opens wounds: stacking Bleeding. HOLD use: the blade becomes a swarm of petals that follows your aim and cuts everything in its path."),
    "shinigami.isekai_mahou.archetype.petals.bankai": (
        "Lâminas gigantes afundam no chão e viram um oceano de pétalas que corta todos em volta. Segure usar: torrente pela mira; solte mirando um inimigo: Gōkei; agachado + usar: Senkei, o anel de mil espadas.",
        "Giant blades sink into the ground and become an ocean of petals that cuts everyone around. Hold use: a torrent that follows your aim; release while aiming at an enemy: Gōkei; sneak + use: Senkei, the ring of a thousand swords."),
}


def merge_lang():
    out = os.path.join(ROOT, "src/main/lang/class_shinigami")
    for idx, lang in ((0, "pt_br"), (1, "en_us")):
        path = os.path.join(out, lang + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for k, v in LANG.items():
            data[k] = v[idx]
        data = dict(sorted(data.items()))
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write(chr(10))
        print("ok lang", lang)


def main():
    save(petal(), "shinigami_fx_petal.png")
    save(radial(64, 2.2), "shinigami_fx_glow.png")
    save(ring(), "shinigami_fx_ring.png")
    save(sword(), "shinigami_fx_sword.png")
    save(ghost(), "shinigami_fx_ghost.png")
    save(slash(), "shinigami_fx_slash.png")
    save(mask_front(), "shinigami_fx_mask.png")
    save(spike(), "shinigami_fx_spike.png")
    save(flame(), "shinigami_fx_flame.png")
    save(radial(64, 2.2), "quincy_fx_glow.png")
    save(wing(), "quincy_fx_wing.png")
    save(halo(), "quincy_fx_halo.png")
    save(star(), "quincy_fx_star.png")
    save(streak(), "quincy_fx_streak.png")
    save(eye(), "quincy_fx_eye.png")
    save(beam(), "quincy_fx_beam.png")
    save(hilt(), "zanpakuto_petals_hilt.png", ITEM)
    model = {"parent": "minecraft:item/handheld", "textures": {"layer0": "isekai_mahou:item/zanpakuto_petals_hilt"}}
    with open(os.path.join(A, "models/item/zanpakuto_petals_hilt.json"), "w", encoding="utf-8") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    print("ok zanpakuto_petals_hilt.json")
    # a lamina "some" (vira petalas) enquanto a Senbonzakura e controlada: override em todas as zanpakuto
    for name in ["zanpakuto", "zanpakuto_t1", "zanpakuto_t2", "zanpakuto_t3", "zanpakuto_t4"]:
        path = os.path.join(A, "models/item", name + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        ov = data.setdefault("overrides", [])
        ov[:] = [o for o in ov if "isekai_mahou:petals_out" not in o.get("predicate", {})]
        ov.append({"predicate": {"isekai_mahou:petals_out": 1}, "model": "isekai_mahou:item/zanpakuto_petals_hilt"})
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print("ok override", name)
    merge_lang()


if __name__ == "__main__":
    main()
