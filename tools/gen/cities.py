"""Gera os assets do modulo "cities" (vida das cidades: NPCs, coracao da cidade, efeitos, traducoes).

Uso (na raiz do projeto):  py -I tools/gen/cities.py

Gera:
  - modelo GeckoLib unico dos NPCs (geo/entity/city_npc.geo.json) com pecas opcionais (elmo, lanca, escudo, coroa,
    capuz, bone, capelo, oculos, avental, saia, bolsa...) e as animacoes (idle/walk/run/talk/attack/wave/cheer/bow/work);
  - texturas por variante em textures/entity/city_npc/: guard_0..3, citizen_0..9, merchant_<profissao>_0..1, lord_0..1;
  - bloco city_heart (pedestal) + textura do cristal (renderer), item do bloco, ovos;
  - icones dos efeitos wanted e aurora_blessing;
  - v0.9 (reinos): guardas e lordes de Sahra/Nordheim (guard_<reino>_0..3, lord_<reino>_0..1), brasoes das faccoes
    novas em textures/gui/faction/kingdom_sahra.png e kingdom_nordheim.png (mesma receita do tools/gen/icons.py);
  - traducoes src/main/lang/cities/{pt_br,en_us}.json (falas, nomes, servicos, rumores, faccoes dos reinos...).
A ordem das variantes do cidadao (LOOKS) precisa bater com entity/Citizen.java; as quantidades de falas com CityLines.java.
"""
import json
import os
import random
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixelart import Canvas, hex_rgba, lighten, darken, mix  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
LANG = os.path.join(ROOT, "src/main/lang/cities")
NS = "isekai_mahou"
T = (0, 0, 0, 0)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def jitter(col, rnd, amount):
    f = (rnd.random() - 0.5) * 2 * amount
    return lighten(col, f) if f > 0 else darken(col, -f)


def H(c):
    return hex_rgba(c) if isinstance(c, str) else c


# =================================================================================================
#  GeckoLib: construtor de modelo com box-UV automatico + pintura por peca
# =================================================================================================
def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, u + d + w - 1, v + d - 1),
        "bottom": (u + d + w, v, u + d + 2 * w - 1, v + d - 1),
        "right": (u, v + d, u + d - 1, v + d + h - 1),
        "front": (u + d, v + d, u + d + w - 1, v + d + h - 1),
        "left": (u + d + w, v + d, u + 2 * d + w - 1, v + d + h - 1),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w - 1, v + d + h - 1),
    }


def px(rect):
    x0, y0, x1, y1 = rect
    fw, fh = x1 - x0 + 1, y1 - y0 + 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            yield x, y, x - x0, y - y0, fw, fh


class Geo:
    def __init__(self, ident, tw=128, th=128):
        self.ident, self.tw, self.th = ident, tw, th
        self.bones, self.cubes = [], []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)

    def cube(self, bone, origin, size, key, inflate=0.0):
        self.cubes.append({"bone": bone, "origin": list(origin), "size": list(size), "key": key, "inflate": inflate})

    def pack(self):
        order = sorted(range(len(self.cubes)), key=lambda i: (-(self.cubes[i]["size"][2] + self.cubes[i]["size"][1]),
                                                               -self.cubes[i]["size"][0]))
        x = y = row = 0
        for i in order:
            w, h, d = self.cubes[i]["size"]
            bw, bh = 2 * d + 2 * w, d + h
            if x + bw > self.tw:
                x, y, row = 0, y + row, 0
            self.cubes[i]["uv"] = (x, y)
            x += bw
            row = max(row, bh)
        assert y + row <= self.th, f"{self.ident}: textura pequena demais ({y + row} > {self.th})"

    def to_json(self, bounds=(3, 3.5)):
        bones = []
        for b in self.bones:
            out = dict(b)
            cubes = []
            for c in self.cubes:
                if c["bone"] != b["name"]:
                    continue
                e = {"origin": c["origin"], "size": c["size"], "uv": list(c["uv"])}
                if c["inflate"]:
                    e["inflate"] = c["inflate"]
                cubes.append(e)
            if cubes:
                out["cubes"] = cubes
            bones.append(out)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.ident}", "texture_width": self.tw, "texture_height": self.th,
                            "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1],
                            "visible_bounds_offset": [0, bounds[1] / 2 - 0.5, 0]},
            "bones": bones}]}

    def paint(self, painters, seed):
        c = Canvas(self.tw, self.th)
        rnd = random.Random(seed)
        for cube in self.cubes:
            w, h, d = cube["size"]
            u, v = cube["uv"]
            p = painters.get(cube["key"])
            if p:
                p(c, box_faces(u, v, w, h, d), rnd)
        return c


# =================================================================================================
#  Pintores
# =================================================================================================
WHITE = hex_rgba("F4F4F4")
GOLD = hex_rgba("E8B84A")
GOLD_D = hex_rgba("A57A22")
GOLD_L = hex_rgba("FFE38A")
STEEL = hex_rgba("9AA4B0")
KINGDOM = hex_rgba("3F6FD8")


def solid(color, amount=0.05, shade=True):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = color
                if shade and name in ("front", "back", "left", "right") and fh > 3:
                    col = lighten(color, 0.08) if fy == 0 else (darken(color, 0.12) if fy == fh - 1 else color)
                if name == "bottom":
                    col = darken(color, 0.2)
                c.set(x, y, jitter(col, rnd, amount))
    return p


def head(skin, hair, style="short", eye="3A6BC9", lashes=False, blush=False, stubble=False, scar=False, brows=None,
         mouth="B5655A", freckles=False, wrinkles=False):
    skin, hair, eye, mouth = H(skin), H(hair), H(eye), H(mouth)
    brow = H(brows) if brows else darken(hair, 0.3)
    skin_d = darken(skin, 0.14)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = jitter(skin, rnd, 0.03)
                hairy = False
                if name == "top":
                    hairy = style != "bald"
                elif name == "back":
                    hairy = style != "bald" or 3 <= fy <= 5
                    if style == "short" and fy == fh - 1:
                        hairy = False
                elif name in ("right", "left"):
                    back = fx <= 1 if name == "right" else fx >= fw - 2
                    if style == "bald":
                        hairy = (back and 3 <= fy <= 5) or (fy in (3, 4) and (fx <= 3 if name == "right" else fx >= fw - 4))
                    else:
                        hairy = fy <= 1 or (back and fy <= (fh - 1 if style == "long" else 5))
                        if style == "long" and fy <= 2:
                            hairy = True
                    if not hairy and fy == 4 and fx in (3, 4):
                        col = skin_d
                elif name == "front":
                    if style != "bald":
                        hairy = fy == 0 or (fy == 1 and fx in (0, 1, 2, 5, 6, 7))
                        if style == "long" and fx in (0, 7) and fy <= 4:
                            hairy = True
                    if fy == 3 and fx in (1, 2, 5, 6):
                        col = brow
                    if fy == 4:
                        if fx in (1, 6):
                            col = WHITE
                        elif fx in (2, 5):
                            col = eye
                    if lashes and fy == 3 and fx in (1, 6):
                        col = hex_rgba("2A1A14")
                    if fy == 5 and fx in (3, 4):
                        col = skin_d
                    if fy == 6 and fx in (3, 4):
                        col = mouth
                    if blush and fy == 5 and fx in (1, 6):
                        col = mix(skin, hex_rgba("E88A8A"), 0.5)
                    if freckles and fy == 5 and fx in (1, 2, 5, 6) and (fx + fy) % 2 == 0:
                        col = darken(skin, 0.25)
                    if wrinkles and fy == 2 and 2 <= fx <= 5:
                        col = darken(skin, 0.12)
                    if stubble and fy >= 6 and fx not in (3, 4):
                        col = mix(skin, hair, 0.35)
                    if scar and fx == 2 and 2 <= fy <= 5 and fy != 4:
                        col = hex_rgba("B86A5E")
                if hairy:
                    col = jitter(hair, rnd, 0.08)
                    if rnd.random() < 0.12:
                        col = darken(col, 0.18)
                c.set(x, y, col)
    return p


def hair_overlay(hair, style="short", hat=False):
    hair = H(hair)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                on = False
                if name == "top":
                    on = style != "bald" and not hat
                elif name == "back":
                    on = style == "long" or (style == "short" and fy <= 4)
                elif name in ("right", "left"):
                    back = fx <= 2 if name == "right" else fx >= fw - 3
                    on = style != "bald" and (fy == 0 or (back and fy <= (fh - 2 if style == "long" else 3)))
                elif name == "front":
                    on = style != "bald" and fy == 0 and not hat
                    if style == "long" and fx in (0, 7) and fy <= 3:
                        on = True
                if on:
                    col = jitter(lighten(hair, 0.06), rnd, 0.1)
                    if rnd.random() < 0.15:
                        col = darken(col, 0.2)
                    c.set(x, y, col)
    return p


def long_hair(hair, braid=False):
    hair = H(hair)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = jitter(hair, rnd, 0.1)
                if fx % 2 == 1:
                    col = darken(col, 0.12)
                if braid and name == "back" and fy % 3 == 2:
                    col = darken(hair, 0.3)
                if fy == fh - 1 and name in ("front", "back", "left", "right") and rnd.random() < 0.35:
                    continue
                c.set(x, y, col)
    return p


def body(shirt, vest=None, belt=None, buckle="E8B84A", collar=None, tie=None, buttons=None, armor=None,
         robe_trim=None, emblem=None, straps=None, sash=None, tabard=None, tabard_trim=None, stripes=None):
    shirt = H(shirt)
    vest = H(vest) if vest else None
    belt = H(belt) if belt else None
    buckle = H(buckle)
    collar = H(collar) if collar else None
    tie = H(tie) if tie else None
    buttons = H(buttons) if buttons else None
    armor = H(armor) if armor else None
    robe_trim = H(robe_trim) if robe_trim else None
    emblem = H(emblem) if emblem else None
    straps = H(straps) if straps else None
    sash = H(sash) if sash else None
    tabard = H(tabard) if tabard else None
    tabard_trim = H(tabard_trim) if tabard_trim else GOLD
    stripes = H(stripes) if stripes else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = shirt
                mid = (fw - 1) / 2
                if stripes is not None and name in ("front", "back") and fy % 3 == 0:
                    col = stripes
                if vest is not None:
                    if name in ("back", "left", "right", "top") or (name == "front" and (fx <= 1 or fx >= fw - 2)):
                        col = vest
                if collar is not None and name == "front" and fy <= 2 and abs(fx - mid) <= 1.6 - fy * 0.5:
                    col = collar
                if tie is not None and name == "front" and abs(fx - mid) < 1 and 1 <= fy <= 6:
                    col = tie
                if buttons is not None and name == "front" and abs(fx - mid) < 1 and fy % 3 == 1 and 2 < fy < fh - 4:
                    col = buttons
                if armor is not None and name in ("front", "back", "left", "right") and fy <= fh - 4:
                    col = lighten(armor, 0.18) if fy == 0 else (darken(armor, 0.2) if fx in (0, fw - 1) else armor)
                    if (fx, fy) in ((1, 2), (fw - 2, 2)):
                        col = lighten(armor, 0.4)
                if tabard is not None and name in ("front", "back") and 2 <= fx <= fw - 3:
                    col = tabard
                    if fx in (2, fw - 3) or fy == fh - 1:
                        col = tabard_trim
                    if name == "front" and emblem is not None and 3 <= fy <= 6 and abs(fx - mid) <= (1.5 if fy < 6 else 0.5):
                        col = emblem if (fx + fy) % 2 else lighten(emblem, 0.3)
                elif emblem is not None and tabard is None and name == "front" and fx in (1, 2) and fy in (2, 3):
                    col = emblem if (fx + fy) % 2 else lighten(emblem, 0.3)
                if straps is not None and name in ("front", "back") and abs(fx - (fy * 0.6) % fw) < 0.8 and fy < fh - 4:
                    col = straps
                if robe_trim is not None and name == "front" and abs(fx - mid) < 1:
                    col = robe_trim
                if sash is not None and fy in (fh - 4, fh - 3):
                    col = sash
                if belt is not None and fy in (fh - 4, fh - 3):
                    col = belt
                    if name == "front" and abs(fx - mid) < 1:
                        col = buckle
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def arm(sleeve, hand, cuff=None, sleeve_rows=8, glove=None, bracer=None):
    sleeve, hand = H(sleeve), H(hand)
    cuff = H(cuff) if cuff else None
    glove = H(glove) if glove else None
    bracer = H(bracer) if bracer else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "top":
                    col = sleeve
                elif name == "bottom":
                    col = glove if glove is not None else hand
                elif fy < sleeve_rows:
                    col = sleeve if fy > 0 else lighten(sleeve, 0.1)
                elif fy == sleeve_rows and cuff is not None:
                    col = cuff
                else:
                    col = glove if glove is not None else hand
                if bracer is not None and name not in ("top", "bottom") and sleeve_rows - 2 <= fy <= fh - 3:
                    col = bracer if fy != sleeve_rows - 2 else lighten(bracer, 0.2)
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def leg(pants, boot, boot_rows=4, cuff=None, patch=None, greave=None):
    pants, boot = H(pants), H(boot)
    cuff = H(cuff) if cuff else lighten(boot, 0.15)
    patch = H(patch) if patch else None
    greave = H(greave) if greave else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "top":
                    col = pants
                elif name == "bottom":
                    col = darken(boot, 0.3)
                elif fy >= fh - boot_rows:
                    col = cuff if fy == fh - boot_rows else boot
                    if fy == fh - 1:
                        col = darken(boot, 0.25)
                else:
                    col = pants
                    if patch is not None and name == "front" and fy in (fh - boot_rows - 3, fh - boot_rows - 2) and 1 <= fx <= fw - 2:
                        col = patch
                    if greave is not None and name == "front" and fy >= fh - boot_rows - 4:
                        col = lighten(greave, 0.15) if fy == fh - boot_rows - 4 else greave
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def skirt(color, hem, pattern=None):
    color, hem = H(color), H(hem)
    pattern = H(pattern) if pattern else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = color
                if name in ("front", "back", "left", "right"):
                    if fx % 2 == 1:
                        col = darken(color, 0.15)
                    if pattern is not None and fy == fh - 3:
                        col = pattern
                    if fy == fh - 1:
                        col = hem
                    if fy == 0:
                        col = darken(color, 0.3)
                if name == "top":
                    continue
                if name == "bottom":
                    col = darken(color, 0.45)
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def cape(color, lining, trim=None, emblem=None, ermine=False):
    color, lining = H(color), H(lining)
    trim = H(trim) if trim else None
    emblem = H(emblem) if emblem else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "back":
                    col = lighten(color, 0.12 * (1 - fy / fh))
                    if trim is not None and (fx in (0, fw - 1) or fy == fh - 1):
                        col = trim
                    if emblem is not None and 3 <= fy <= 8 and abs(fx - (fw - 1) / 2) <= (2.5 if fy < 6 else 8 - fy):
                        col = emblem if (fx + fy) % 3 else lighten(emblem, 0.3)
                    if fx % 3 == 1 and fy > 9 and col not in (trim,):
                        col = darken(col, 0.1)
                    if ermine and fy <= 1:
                        col = WHITE if (fx + fy) % 3 else hex_rgba("1A1A1A")
                elif name == "front":
                    col = lining
                    if ermine and fy <= 1:
                        col = WHITE
                else:
                    col = trim if trim is not None else darken(color, 0.2)
                    if ermine and fy <= 1:
                        col = WHITE
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def hood(color, trim=None):
    color = H(color)
    trim = H(trim) if trim else darken(color, 0.15)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "front":
                    if not (fy == 0 or fx in (0, fw - 1) or (fy == 1 and fx in (1, fw - 2))):
                        continue
                    col = trim
                elif name == "bottom":
                    continue
                else:
                    col = color
                    if name in ("left", "right") and fy == fh - 1:
                        col = darken(color, 0.2)
                    if name == "back" and fx == (fw - 1) // 2 and fy < 5:
                        col = darken(color, 0.25)
                c.set(x, y, jitter(col, rnd, 0.06))
    return p


def helmet(metal, trim=None):
    metal = H(metal)
    trim = H(trim) if trim else GOLD

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "bottom":
                    continue
                col = metal
                if name == "front":
                    # abertura do rosto, com protetor de nariz
                    if 3 <= fy <= 6 and 1 <= fx <= fw - 2 and not (fx in (3, 4) and fy <= 5):
                        continue
                    if fy == fh - 1 or fy == 2:
                        col = trim
                if name == "top":
                    col = lighten(metal, 0.2)
                    if fx in (3, 4):
                        col = trim
                if name in ("left", "right", "back") and fy == 2:
                    col = trim
                if (fx + fy) % 7 == 0 and name != "top":
                    col = lighten(col, 0.25)
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def metal(color, rivets=True):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = lighten(color, 0.25) if (name == "top" or fy == 0) else color
                if name == "bottom":
                    col = darken(color, 0.35)
                if rivets and name in ("front", "back", "left", "right") and fy == 1 and fx in (1, fw - 2):
                    col = hex_rgba("E8E8E8")
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def wood_rod(color):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = jitter(color, rnd, 0.08)
                if fy % 5 == 0:
                    col = darken(col, 0.2)
                if fy % 9 == 3:
                    col = lighten(col, 0.15)
                c.set(x, y, col)
    return p


def gem(color):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = lighten(color, 0.5) if (fx == 0 and fy == 0) or name == "top" else color
                if fx == fw - 1 or fy == fh - 1:
                    col = darken(color, 0.3)
                c.set(x, y, col)
    return p


def crown(gold, jewel):
    gold, jewel = H(gold), H(jewel)

    def p(c, f, rnd):
        for name, r in f.items():
            if name in ("top", "bottom"):
                continue
            for x, y, fx, fy, fw, fh in px(r):
                col = lighten(gold, 0.2) if fy == 0 else gold
                if fy == fh - 1:
                    col = darken(gold, 0.25)
                if fx % 3 == 1 and fy == 0:
                    col = jewel if (fx // 3) % 2 == 0 else hex_rgba("6FA8FF")
                c.set(x, y, jitter(col, rnd, 0.03))
    return p


def glasses(frame, lens=None):
    frame = H(frame)
    lens = H(lens) if lens else None

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "front":
                    in_lens = fy == 1 and fx in (1, 2, 5, 6)
                    if in_lens:
                        if lens is not None:
                            c.set(x, y, lens)
                        continue
                    if fy == 1 and fx in (0, 3, 4, 7) or fy == 0 and fx not in (3, 4):
                        c.set(x, y, frame)
                elif name in ("left", "right") and fy == 0:
                    c.set(x, y, frame)
    return p


def mask(color):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            if name in ("top", "bottom"):
                continue
            for x, y, fx, fy, fw, fh in px(r):
                col = color if (fx + fy) % 4 else darken(color, 0.25)
                if fy == 0:
                    col = lighten(color, 0.12)
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def apron(color, pocket=None, stains=False):
    color = H(color)
    pocket = H(pocket) if pocket else darken(color, 0.2)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = color
                if name == "front":
                    if 7 <= fy <= 9 and 2 <= fx <= fw - 3:
                        col = pocket
                    if fy == 0:
                        col = darken(color, 0.25)
                    if stains and rnd.random() < 0.06:
                        col = darken(color, 0.35)
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


def shield(face, rim, emblem):
    face, rim, emblem = H(face), H(rim), H(emblem)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = rim
                if name in ("left", "right"):
                    if 1 <= fx <= fw - 2 and 1 <= fy <= fh - 2:
                        col = face
                        mx = (fw - 1) / 2
                        # leão/coroa estilizado: cruz + diamante dourados
                        if abs(fx - mx) < 1 or fy == fh // 2 - 1:
                            col = emblem
                        if abs(fx - mx) + abs(fy - (fh // 2 - 1)) <= 1.5:
                            col = lighten(emblem, 0.35)
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def satchel(color, strap):
    color, strap = H(color), H(strap)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = color
                if fy == 0 or name == "top":
                    col = strap
                if name == "front" and fy == 1 and fx == fw // 2:
                    col = GOLD
                c.set(x, y, jitter(col, rnd, 0.06))
    return p


def book(cover, pages="EDE3C8", trim="E8B84A"):
    cover, pages, trim = H(cover), H(pages), H(trim)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = cover
                if name in ("top", "bottom", "front"):
                    col = pages if name != "front" else cover
                if name in ("left", "right") and (fy in (0, fh - 1) or fx in (0, fw - 1)):
                    col = trim
                if name == "front" and fy % 2 == 1:
                    col = pages
                c.set(x, y, jitter(col, rnd, 0.04))
    return p


def glass(liquid):
    liquid = H(liquid)
    glass_c = hex_rgba("CFE8F0", 200)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = glass_c if fy <= 1 and name != "bottom" else liquid
                if fx == 0 and name != "bottom":
                    col = lighten(col, 0.4)
                c.set(x, y, col)
    return p


def plume(color):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                col = jitter(color, rnd, 0.12)
                if (fx + fy) % 3 == 0:
                    col = lighten(col, 0.18)
                c.set(x, y, col)
    return p


def basket(color):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "top":
                    col = [hex_rgba("C9433A"), hex_rgba("E8B84A"), hex_rgba("6BD66B")][(fx + fy) % 3]
                else:
                    col = color if (fx + fy) % 2 else darken(color, 0.2)
                c.set(x, y, jitter(col, rnd, 0.06))
    return p


def beard(color):
    color = H(color)

    def p(c, f, rnd):
        for name, r in f.items():
            for x, y, fx, fy, fw, fh in px(r):
                if name == "front" and fy == 0 and 2 <= fx <= fw - 3:
                    col = hex_rgba("3A2018") if fx in (3,) else jitter(lighten(color, 0.1), rnd, 0.08)
                elif fy == fh - 1 and name != "top" and fx % 2 == 0:
                    continue
                else:
                    col = jitter(color, rnd, 0.1)
                    if (fx + fy * 2) % 5 == 0:
                        col = darken(col, 0.18)
                c.set(x, y, col)
    return p


def cap_top(color, band_c=None):
    color = H(color)
    band_c = H(band_c) if band_c else darken(color, 0.25)

    def p(c, f, rnd):
        for name, r in f.items():
            if name == "bottom":
                continue
            for x, y, fx, fy, fw, fh in px(r):
                col = lighten(color, 0.08) if name == "top" else (band_c if fy == fh - 1 else color)
                c.set(x, y, jitter(col, rnd, 0.05))
    return p


# =================================================================================================
#  Modelo city_npc
# =================================================================================================
def npc_geo():
    g = Geo("city_npc")
    g.bone("root")
    g.bone("body", "root", (0, 24, 0))
    g.cube("body", (-4, 12, -2), (8, 12, 4), "body")
    g.bone("head", "body", (0, 24, 0))
    g.cube("head", (-4, 24, -4), (8, 8, 8), "head")
    g.cube("head", (-4, 24, -4), (8, 8, 8), "hair", 0.5)
    # cabelo comprido (atrás), barba, capacete, pluma, coroa, capuz, boné, capelo, óculos, máscara
    g.bone("hair_long", "head", (0, 27, 4))
    g.cube("hair_long", (-4.5, 17, 3.6), (9, 10, 2), "hair_long")
    g.bone("beard", "head", (0, 24, -4))
    g.cube("beard", (-3.5, 21, -5), (7, 5, 1), "beard")
    g.bone("helmet", "head", (0, 24, 0))
    g.cube("helmet", (-4, 24, -4), (8, 8, 8), "helmet", 1.0)
    g.cube("helmet", (-5.5, 26, -5.5), (11, 1, 11), "helmet_rim")
    g.bone("plume", "head", (0, 33, 0))
    g.cube("plume", (-0.5, 33, -3), (1, 4, 7), "plume")
    g.bone("crown", "head", (0, 32, 0))
    g.cube("crown", (-4.5, 31.5, -4.5), (9, 2, 9), "crown")
    g.cube("crown", (-0.5, 33.5, -4.6), (1, 2, 1), "crown_pt")
    g.cube("crown", (-4.6, 33.5, -0.5), (1, 2, 1), "crown_pt")
    g.cube("crown", (3.6, 33.5, -0.5), (1, 2, 1), "crown_pt")
    g.cube("crown", (-0.5, 33.5, 3.6), (1, 2, 1), "crown_pt")
    g.bone("hood", "head", (0, 24, 0))
    g.cube("hood", (-4, 24, -4), (8, 8, 8), "hood", 0.75)
    g.cube("hood", (-4.5, 18, 3.5), (9, 6, 1), "hood_drape")
    g.bone("cap", "head", (0, 32, 0))
    g.cube("cap", (-4.5, 31.6, -4.5), (9, 2, 9), "cap_top")
    g.cube("cap", (-4, 31.6, -7.5), (8, 1, 3), "cap_brim")
    g.bone("mortarboard", "head", (0, 32, 0))
    g.cube("mortarboard", (-4.5, 31.6, -4.5), (9, 1, 9), "mb_base")
    g.cube("mortarboard", (-6, 32.6, -6), (12, 1, 12), "mb_board")
    g.cube("mortarboard", (5, 29.6, -0.5), (1, 3, 1), "tassel")
    g.bone("glasses", "head", (0, 28, -4))
    g.cube("glasses", (-4, 27, -4.8), (8, 2, 1), "glasses")
    g.bone("goggles", "head", (0, 28, -4))
    g.cube("goggles", (-4.5, 27, -4.9), (9, 2, 1), "goggles")
    g.cube("goggles", (-4.6, 27.5, -4.6), (9, 1, 9), "goggle_strap", 0.15)
    g.bone("mask", "head", (0, 25, -4))
    g.cube("mask", (-4, 24, -4.8), (8, 3, 1), "mask")
    # braços e itens nas mãos
    g.bone("right_arm", "body", (-5, 22, 0))
    g.cube("right_arm", (-8, 12, -2), (4, 12, 4), "arm_r")
    g.bone("left_arm", "body", (5, 22, 0))
    g.cube("left_arm", (4, 12, -2), (4, 12, 4), "arm_l")
    g.bone("right_item", "right_arm", (-6, 12.5, -1))
    g.bone("left_item", "left_arm", (6, 12.5, -1))
    g.bone("spear", "right_arm", (-6, 13, 0))
    g.cube("spear", (-6.5, 0, -0.5), (1, 36, 1), "spear_shaft")
    g.cube("spear", (-7, 36, -1), (2, 5, 2), "spear_tip")
    g.cube("spear", (-7.5, 33, -1.5), (3, 1, 3), "spear_guard")
    g.bone("shield", "left_arm", (8, 17, 0))
    g.cube("shield", (8, 9, -4), (1, 11, 8), "shield")
    g.bone("hammer", "right_arm", (-6, 12, 0))
    g.cube("hammer", (-6.5, 4, -0.5), (1, 9, 1), "hammer_handle")
    g.cube("hammer", (-7.5, 1, -2), (3, 3, 4), "hammer_head")
    g.bone("book", "left_arm", (6, 12, 0))
    g.cube("book", (4.5, 8.5, -3), (3, 5, 4), "book")
    g.bone("flask", "left_arm", (6, 12, 0))
    g.cube("flask", (5, 8, -1.5), (2, 4, 2), "flask")
    g.cube("flask", (5.5, 12, -1), (1, 1, 1), "flask_neck")
    g.bone("scepter", "right_arm", (-6, 12, 0))
    # cetro em pé, segurado na frente da mão (orbe na altura do ombro)
    g.cube("scepter", (-6.5, 10, -3.5), (1, 16, 1), "scepter_rod")
    g.cube("scepter", (-7.5, 26, -4.5), (3, 3, 3), "scepter_orb")
    g.bone("mug", "left_arm", (6, 12, 0))
    g.cube("mug", (4.5, 8.5, -2.5), (3, 4, 3), "mug")
    g.cube("mug", (7.5, 9.5, -1.5), (1, 2, 1), "mug_handle")
    g.bone("dagger", "right_arm", (-6, 12, 0))
    g.cube("dagger", (-6.5, 5, -0.5), (1, 7, 1), "dagger_blade")
    g.cube("dagger", (-7.5, 11, -1), (3, 1, 2), "dagger_guard")
    g.bone("basket", "left_arm", (6, 12, 0))
    g.cube("basket", (3.5, 6, -3), (5, 4, 5), "basket")
    g.bone("pauldron_r", "right_arm", (-5, 22, 0))
    g.cube("pauldron_r", (-9, 21, -3), (6, 4, 6), "pauldron")
    g.bone("pauldron_l", "left_arm", (5, 22, 0))
    g.cube("pauldron_l", (3, 21, -3), (6, 4, 6), "pauldron")
    # roupas por cima do corpo
    g.bone("cape", "body", (0, 24, 2))
    g.cube("cape", (-4.5, 4, 2.1), (9, 20, 1), "cape")
    g.bone("apron", "body", (0, 21, -2))
    g.cube("apron", (-3.5, 6, -2.8), (7, 15, 1), "apron")
    g.bone("skirt", "body", (0, 12, 0))
    g.cube("skirt", (-4.5, 1, -2.5), (9, 12, 5), "skirt")
    g.bone("satchel", "body", (4, 13, 0))
    g.cube("satchel", (4.2, 8, -1.5), (2, 4, 3), "satchel")
    # pernas
    g.bone("right_leg", "root", (-1.9, 12, 0))
    g.cube("right_leg", (-3.9, 0, -2), (4, 12, 4), "leg")
    g.bone("left_leg", "root", (1.9, 12, 0))
    g.cube("left_leg", (-0.1, 0, -2), (4, 12, 4), "leg")
    g.pack()
    return g


# -------------------------------------------------------------------------------------------------
#  Kit de acessórios (cores neutras; cada variante troca o que precisa)
# -------------------------------------------------------------------------------------------------
def base_kit():
    return {
        "helmet": helmet("A8B2BE"), "helmet_rim": metal("8E99A8", rivets=False), "plume": plume("C9433A"),
        "crown": crown("E8B84A", "C9433A"), "crown_pt": gem("FFD86B"),
        "hood": hood("4A3A5A"), "hood_drape": solid("4A3A5A"),
        "cap_top": cap_top("6B4A2E"), "cap_brim": solid("5A3E26"),
        "mb_base": solid("1E1E2A"), "mb_board": solid("22222E"), "tassel": solid("E8B84A"),
        "glasses": glasses("3A2A1E"), "goggles": glasses("A57A22", hex_rgba("F0A030", 230)),
        "goggle_strap": solid("4A3220"),
        "mask": mask("2A2626"),
        "spear_shaft": wood_rod("6B4A2E"), "spear_tip": metal("D8DEE6", rivets=False), "spear_guard": metal("E8B84A", rivets=False),
        "shield": shield("3F6FD8", "8E99A8", "E8B84A"),
        "hammer_handle": wood_rod("5A3E26"), "hammer_head": metal("6E7782"),
        "book": book("6A2A3A"), "flask": glass("6BD66B"), "flask_neck": solid("8A6A4A"),
        "scepter_rod": metal("E8B84A", rivets=False), "scepter_orb": gem("6FA8FF"),
        "mug": solid("8A6A4A"), "mug_handle": solid("6B4A2E"),
        "dagger_blade": metal("C8CED6", rivets=False), "dagger_guard": solid("2A2020"),
        "basket": basket("B9875A"),
        "pauldron": metal("9AA4B0"),
        "cape": cape("3F6FD8", "1E3570", trim="E8B84A"),
        "apron": apron("D9CFB8"), "skirt": skirt("6B4A7A", "E8B84A"), "satchel": satchel("7A5636", "4A3220"),
    }


def person(skin, hair, style, eye, shirt_painter, sleeve, pants, boots, female=False, **head_kw):
    kit = base_kit()
    kit.update({
        "head": head(skin, hair, style, eye=eye, lashes=female, blush=female, **head_kw),
        "hair": hair_overlay(hair, style),
        "hair_long": long_hair(hair),
        "beard": beard(darken(H(hair), 0.05)),
        "body": shirt_painter,
        "arm_r": arm(sleeve, skin, sleeve_rows=8),
        "arm_l": arm(sleeve, skin, sleeve_rows=8),
        "leg": leg(pants, boots),
    })
    return kit


SKINS = ["F2CFB0", "E0AC85", "C98E64", "A86E48", "7A4A30", "F5D9C0"]


# ---------------------------------------------------------------------------------------- guardas
def guard_painters(i):
    skin = ["E0AC85", "C98E64", "F2CFB0", "7A4A30"][i]
    hair = ["5A3A22", "1E1A1A", "B07030", "141010"][i]
    kit = person(skin, hair, "long" if i == 2 else "short", ["4A6B3A", "3A4A6B", "3A7A5A", "5A3A22"][i],
                 body("2B3A67", armor="9AA4B0", tabard="3F6FD8", tabard_trim="E8B84A", emblem="E8B84A",
                      belt="3A2A1E"),
                 "8E99A8", "3A3A4A", "5A4A3E", female=(i == 2), stubble=(i in (1, 3)), scar=(i == 3))
    kit["arm_r"] = arm("8E99A8", skin, sleeve_rows=9, glove="4A3A2E", bracer="9AA4B0")
    kit["arm_l"] = arm("8E99A8", skin, sleeve_rows=9, glove="4A3A2E", bracer="9AA4B0")
    kit["leg"] = leg("2B3A67", "4A3A2E", boot_rows=5, greave="9AA4B0")
    kit["plume"] = plume(["C9433A", "3F6FD8", "E8B84A", "C9433A"][i])
    kit["helmet"] = helmet(["A8B2BE", "B8C0CA", "A8B2BE", "9AA4B0"][i])
    return kit


# ---------------------------------------------------------------------------------------- cidadãos
# (feminina, barba, bolsa, boné) — mesma ordem de Citizen.LOOKS
CITIZENS = [
    dict(f=False, skin="E0AC85", hair="5A3A22", style="short", eye="4A6B3A", shirt="8E5A3A", vest="5A3E26", pants="4A4A5A", cap="6B8E3A"),
    dict(f=True, skin="F2CFB0", hair="C97A3A", style="long", eye="3A7A3A", shirt="E8D8B0", dress="7A4AA0", hem="E8B84A", freckles=True),
    dict(f=False, skin="C98E64", hair="3A2A22", style="short", eye="3A4A6B", shirt="3E6B35", vest="6B4A2E", pants="5A4632", beard=True),
    dict(f=True, skin="A86E48", hair="1E1A1A", style="long", eye="5A3A22", shirt="F0E8D8", dress="2E6A8A", hem="F0E8D8"),
    dict(f=False, skin="F5D9C0", hair="D9C07A", style="short", eye="3A6BC9", shirt="5A6BA8", pants="3A3A4A"),
    dict(f=True, skin="E0AC85", hair="6B3A22", style="long", eye="4A6B3A", shirt="C9433A", dress="8A2B2B", hem="E8D8B0", cap="D9CFB8"),
    dict(f=False, skin="7A4A30", hair="141010", style="short", eye="3A2A22", shirt="D9A040", vest="7A5636", pants="4A3A2E", beard=True, cap="3A3A4A"),
    dict(f=True, skin="F2CFB0", hair="E8D8A0", style="long", eye="3A8ACF", shirt="E0B0C8", dress="B05A8A", hem="FFFFFF", freckles=True),
    dict(f=False, skin="A86E48", hair="8A8580", style="bald", eye="3A4A6B", shirt="6B6B7A", vest="3A3A4A", pants="2A2A3A", wrinkles=True),
    dict(f=True, skin="C98E64", hair="4A2A1A", style="long", eye="3A6B3A", shirt="7AA84A", dress="4A7A3A", hem="D9C07A"),
]


def citizen_painters(c):
    female = c["f"]
    head_kw = {}
    if c.get("freckles"):
        head_kw["freckles"] = True
    if c.get("wrinkles"):
        head_kw["wrinkles"] = True
    kit = person(c["skin"], c["hair"], c["style"], c["eye"],
                 body(c["shirt"], vest=c.get("vest"), belt="3A2A1E" if not female else None,
                      sash=c.get("hem") if female else None, collar=lighten(H(c["shirt"]), 0.3) if female else None),
                 c["shirt"], c.get("pants", "3A3A4A"), "3E2E22", female=female, **head_kw)
    if female:
        kit["skirt"] = skirt(c["dress"], c["hem"], pattern=lighten(H(c["dress"]), 0.25))
        kit["arm_r"] = arm(c["shirt"], c["skin"], sleeve_rows=6, cuff=c["hem"])
        kit["arm_l"] = arm(c["shirt"], c["skin"], sleeve_rows=6, cuff=c["hem"])
    if c.get("cap"):
        kit["cap_top"] = cap_top(c["cap"])
        kit["cap_brim"] = solid(darken(H(c["cap"]), 0.2))
    kit["satchel"] = satchel(["7A5636", "5A3E26", "8A6A4A"][sum(map(ord, c["shirt"])) % 3], "3A2A1E")
    return kit


# ---------------------------------------------------------------------------------------- mercadores
PROFESSIONS = ["blacksmith", "alchemist", "scribe", "jeweler", "innkeeper", "priest", "fence", "grocer", "tailor"]


def merchant_painters(prof, v):
    female = v == 1
    skin = {"blacksmith": ["C98E64", "E0AC85"], "alchemist": ["F2CFB0", "A86E48"], "scribe": ["F5D9C0", "C98E64"],
            "jeweler": ["E0AC85", "F2CFB0"], "innkeeper": ["E0AC85", "C98E64"], "priest": ["F2CFB0", "7A4A30"],
            "fence": ["A86E48", "F2CFB0"], "grocer": ["7A4A30", "E0AC85"], "tailor": ["F5D9C0", "A86E48"]}[prof][v]
    hair = {"blacksmith": ["2A1E16", "8A3A1A"], "alchemist": ["D9D9E6", "1E1A1A"], "scribe": ["6B5A4A", "C9A15A"],
            "jeweler": ["8A8580", "1E1A1A"], "innkeeper": ["A84A2A", "E0B060"], "priest": ["D9C07A", "1E1A1A"],
            "fence": ["141010", "6A1A2A"], "grocer": ["3A2A22", "C97A3A"], "tailor": ["1E1A1A", "B05A8A"]}[prof][v]
    eye = ["3A6BC9", "4A6B3A", "5A3A22", "6A4FD0"][(PROFESSIONS.index(prof) + v) % 4]
    style = "long" if female else ("bald" if prof in ("blacksmith",) else "short")
    if prof == "blacksmith":
        kit = person(skin, hair, style, eye, body("6B4A32", belt="2A1E16", buckle="9AA4B0"), "6B4A32", "3A2C22", "241A14",
                     female=female, stubble=not female, scar=not female)
        kit["arm_r"] = arm("6B4A32", skin, sleeve_rows=3, glove="3A2A1E")
        kit["arm_l"] = arm("6B4A32", skin, sleeve_rows=3, glove="3A2A1E")
        kit["apron"] = apron("4A3220", pocket="3A2618", stains=True)
        kit["leg"] = leg("3A2C22", "241A14", boot_rows=5)
    elif prof == "alchemist":
        kit = person(skin, hair, style, eye, body("2E6A4A", robe_trim="D9C07A", sash="6B3FA0", collar="1E4A30"),
                     "2E6A4A", "26403A", "2A1E2A", female=female)
        kit["arm_r"] = arm("2E6A4A", skin, cuff="D9C07A", sleeve_rows=9)
        kit["arm_l"] = arm("2E6A4A", skin, cuff="D9C07A", sleeve_rows=9)
        kit["flask"] = glass(["6BD66B", "C04AE0"][v])
        kit["skirt"] = skirt("2E6A4A", "D9C07A")
        kit["satchel"] = satchel("5A3E26", "3A2A1E")
    elif prof == "scribe":
        kit = person(skin, hair, style, eye, body("4B2A7A", robe_trim="E8B84A", collar="EDEFF5", emblem="B98CFF"),
                     "4B2A7A", "2A1E3A", "1E1428", female=female, wrinkles=not female)
        kit["arm_r"] = arm("4B2A7A", skin, cuff="E8B84A", sleeve_rows=9)
        kit["arm_l"] = arm("4B2A7A", skin, cuff="E8B84A", sleeve_rows=9)
        kit["skirt"] = skirt("4B2A7A", "E8B84A", pattern="B98CFF")
        kit["book"] = book("2A3A8A", trim="B98CFF")
        kit["glasses"] = glasses("C9A23A")
    elif prof == "jeweler":
        kit = person(skin, hair, style, eye, body("1E5A6A", vest="2A2A3A", buttons="E8B84A", collar="EDEFF5"),
                     "1E5A6A", "2A2A3A", "1A1A22", female=female)
        kit["cape"] = cape("6A1A4A", "2A0A1E", trim="E8B84A")
        kit["glasses"] = glasses("E8B84A", hex_rgba("9FE8FF", 120))
        kit["satchel"] = satchel("2A2A3A", "E8B84A")
        kit["skirt"] = skirt("1E5A6A", "E8B84A")
    elif prof == "innkeeper":
        kit = person(skin, hair, style, eye, body("D9CFB8", vest="7A2A2A", belt="3A2A1E"), "D9CFB8", "4A3A2E", "3A2A1E",
                     female=female)
        kit["arm_r"] = arm("D9CFB8", skin, sleeve_rows=5)
        kit["arm_l"] = arm("D9CFB8", skin, sleeve_rows=5)
        kit["apron"] = apron("F0E8D8", pocket="D9C07A")
        kit["skirt"] = skirt("7A2A2A", "F0E8D8")
    elif prof == "priest":
        kit = person(skin, hair, style, eye, body("F0E8D8", robe_trim="E8B84A", sash="E8B84A", emblem="FFE38A"),
                     "F0E8D8", "D9CFB8", "8A6A4A", female=female)
        kit["arm_r"] = arm("F0E8D8", skin, cuff="E8B84A", sleeve_rows=10)
        kit["arm_l"] = arm("F0E8D8", skin, cuff="E8B84A", sleeve_rows=10)
        kit["hood"] = hood("F0E8D8", trim="E8B84A")
        kit["hood_drape"] = solid("E8DCC4")
        kit["skirt"] = skirt("F0E8D8", "E8B84A", pattern="FFE38A")
        kit["book"] = book("F0E8D8", trim="E8B84A")
    elif prof == "fence":
        kit = person(skin, hair, style, eye, body("2A2626", vest="1E1A1A", belt="4A4444", buckle="9AA4B0", straps="4A4444"),
                     "2A2626", "302C2C", "141212", female=female, scar=not female)
        kit["arm_r"] = arm("2A2626", skin, sleeve_rows=7, glove="141212")
        kit["arm_l"] = arm("2A2626", skin, sleeve_rows=7, glove="141212")
        kit["hood"] = hood("6A1A2A" if female else "262222")
        kit["hood_drape"] = solid("1E1A1A")
        kit["mask"] = mask("3A3434")
    elif prof == "grocer":
        kit = person(skin, hair, style, eye, body("7AA84A", vest="5A3E26", belt="3A2A1E"), "7AA84A", "4A3A2E", "3A2A1E",
                     female=female, freckles=True)
        kit["apron"] = apron("E8D8B0", pocket="7AA84A")
        kit["cap_top"] = cap_top("D9C07A", band_c="7AA84A")
        kit["cap_brim"] = solid("C9A860")
        kit["skirt"] = skirt("5A7A3A", "E8D8B0")
    else:  # tailor
        kit = person(skin, hair, style, eye, body("B05A8A", vest="2A2A3A", buttons="E8B84A", collar="FFFFFF",
                                                  stripes="C97AA8"),
                     "B05A8A", "2A2A3A", "1A1A22", female=female)
        kit["cap_top"] = cap_top("2A2A3A", band_c="B05A8A")
        kit["cap_brim"] = solid("2A2A3A")
        kit["cape"] = cape("5A2A6A", "2A1236", trim="E0A0C8")
        kit["satchel"] = satchel("E070A8", "2A2A3A")
        kit["skirt"] = skirt("B05A8A", "FFFFFF", pattern="2A2A3A")
    return kit


# ---------------------------------------------------------------------------------------- lorde
def lord_painters(v):
    if v == 0:
        skin, hair = "E0AC85", "8A6A4A"
        kit = person(skin, hair, "short", "3A4A8A", body("6E1622", robe_trim="E8B84A", belt="2A1A0A", buckle="FFE38A",
                                                          collar="FFFFFF", emblem="FFD86B"),
                     "6E1622", "2A1E3A", "1E1428", wrinkles=True)
        kit["beard"] = beard("7A5A3E")
    else:
        skin, hair = "F2CFB0", "E8C870"
        kit = person(skin, hair, "long", "3A8A6A", body("2A3A8A", robe_trim="E8B84A", collar="FFFFFF", emblem="FFD86B",
                                                         sash="E8B84A"),
                     "2A3A8A", "1E2A6A", "1E1428", female=True)
        kit["skirt"] = skirt("2A3A8A", "E8B84A", pattern="FFD86B")
        kit["hair_long"] = long_hair(hair, braid=True)
    kit["arm_r"] = arm(["6E1622", "2A3A8A"][v], skin, cuff="E8B84A", sleeve_rows=9)
    kit["arm_l"] = arm(["6E1622", "2A3A8A"][v], skin, cuff="E8B84A", sleeve_rows=9)
    kit["cape"] = cape("8A1A2A" if v == 0 else "4A1A6A", "3A0A12", trim="E8B84A", emblem="FFD86B", ermine=True)
    kit["crown"] = crown("FFD86B", "C9433A" if v == 0 else "5FD8E8")
    return kit


# ---------------------------------------------------------------------------------------- reinos (v0.9)
# Sahra: império do deserto — tabardo laranja-sol sobre cota dourada, elmo de bronze com pluma branca, capa de
# areia com barra dourada e o sol no escudo. Nordheim: reino da neve — tabardo azul-gelo com cruz branca, elmo de
# aço escuro, capa azul-noite forrada de pele e barbas trançadas.
KINGDOM_LOOK = {
    "sahra": dict(skins=["C98E64", "A86E48", "E0AC85", "7A4A30"], hair=["1E1A1A", "2A1E16", "141010", "3A2A22"],
                  eyes=["5A3A22", "3A2A22", "4A6B3A", "2A1E16"],
                  tunic="7A3A10", armor="D9B060", tabard="E07B2A", trim="FFD86B", emblem="FFE38A", belt="5A3418",
                  sleeve="D9B060", pants="F0E0C0", boots="8A5A2E", greave="C9A050", glove="6A4020",
                  helmet=["C9A050", "D9B060", "C9A050", "B89040"], plume=["FFFFFF", "E07B2A", "FFFFFF", "C9433A"],
                  cape=("D98A3A", "7A3A10", "FFD86B"), shield=("E07B2A", "C9A050", "FFE38A"),
                  pauldron="D9B060", spear_tip="F0E0C0", spear_guard="FFD86B"),
    "nordheim": dict(skins=["F5D9C0", "F2CFB0", "F5D9C0", "E0AC85"], hair=["D9C07A", "B0502A", "E8D8A0", "8A6A4A"],
                     eyes=["3A8ACF", "4A6B8A", "3A7A9A", "4A6B3A"],
                     tunic="1E3A5A", armor="7A8696", tabard="7FC4E8", trim="FFFFFF", emblem="FFFFFF", belt="3A2A1E",
                     sleeve="6A7686", pants="2A3A4A", boots="4A3A2E", greave="7A8696", glove="5A4632",
                     helmet=["7A8696", "8A96A6", "7A8696", "6A7686"], plume=["FFFFFF", "7FC4E8", "FFFFFF", "1E3A5A"],
                     cape=("1E3A5A", "0E1E30", "E8E8F0"), shield=("7FC4E8", "6A7686", "FFFFFF"),
                     pauldron="8A96A6", spear_tip="E0E8F0", spear_guard="C0C8D0"),
}


def kingdom_guard_painters(kingdom, i):
    L = KINGDOM_LOOK[kingdom]
    skin, hair = L["skins"][i], L["hair"][i]
    female = i == 2
    kit = person(skin, hair, "long" if female else "short", L["eyes"][i],
                 body(L["tunic"], armor=L["armor"], tabard=L["tabard"], tabard_trim=L["trim"], emblem=L["emblem"],
                      belt=L["belt"]),
                 L["sleeve"], L["pants"], L["boots"], female=female, stubble=(i in (1, 3)), scar=(i == 3))
    kit["arm_r"] = arm(L["sleeve"], skin, sleeve_rows=9, glove=L["glove"], bracer=L["armor"])
    kit["arm_l"] = arm(L["sleeve"], skin, sleeve_rows=9, glove=L["glove"], bracer=L["armor"])
    kit["leg"] = leg(L["pants"], L["boots"], boot_rows=5, greave=L["greave"])
    kit["plume"] = plume(L["plume"][i])
    kit["helmet"] = helmet(L["helmet"][i], trim=L["trim"])
    kit["helmet_rim"] = metal(darken(H(L["helmet"][i]), 0.1), rivets=False)
    kit["cape"] = cape(L["cape"][0], L["cape"][1], trim=L["cape"][2], emblem=L["emblem"])
    kit["shield"] = shield(*L["shield"])
    kit["pauldron"] = metal(L["pauldron"])
    kit["spear_tip"] = metal(L["spear_tip"], rivets=False)
    kit["spear_guard"] = metal(L["spear_guard"], rivets=False)
    if kingdom == "nordheim":
        kit["beard"] = beard(darken(H(hair), 0.08))
    return kit


def kingdom_lord_painters(kingdom, v):
    if kingdom == "sahra":
        if v == 0:   # Sultão: túnica laranja e ouro, barba negra, manto de seda vinho
            skin, hair = "A86E48", "141010"
            kit = person(skin, hair, "short", "3A2A22", body("C9601E", robe_trim="FFD86B", sash="7A1A3A", collar="FFE38A",
                                                              emblem="FFD86B"),
                         "C9601E", "F0E0C0", "8A5A2E")
            kit["beard"] = beard("1E1A1A")
            kit["cape"] = cape("7A1A3A", "3A0A1A", trim="FFD86B", emblem="FFE38A")
            kit["crown"] = crown("FFD86B", "2EC8B0")
        else:        # Sultana: vestido turquesa e ouro, véu dourado
            skin, hair = "C98E64", "2A1E16"
            kit = person(skin, hair, "long", "5A3A22", body("1E8A8A", robe_trim="FFD86B", collar="FFE38A", emblem="FFD86B",
                                                             sash="E07B2A"),
                         "1E8A8A", "1E6A6A", "6A4020", female=True)
            kit["skirt"] = skirt("1E8A8A", "FFD86B", pattern="E07B2A")
            kit["hair_long"] = long_hair(hair, braid=True)
            kit["cape"] = cape("E07B2A", "7A3A10", trim="FFD86B", emblem="FFE38A")
            kit["crown"] = crown("FFD86B", "C9433A")
        sleeve = ["C9601E", "1E8A8A"][v]
        cuff = "FFD86B"
    else:
        if v == 0:   # Jarl: gibão azul-noite, cota, barba ruiva trançada, manto de pele branca
            skin, hair = "F2CFB0", "B0502A"
            kit = person(skin, hair, "short", "3A7A9A", body("1E3A5A", armor="8A96A6", belt="3A2A1E", buckle="C0C8D0",
                                                              emblem="FFFFFF"),
                         "1E3A5A", "2A3A4A", "4A3A2E", wrinkles=True)
            kit["beard"] = beard("A0461E")
            kit["cape"] = cape("2A4A6A", "E8E8F0", trim="FFFFFF", emblem="7FC4E8", ermine=True)
            kit["crown"] = crown("C0C8D0", "7FC4E8")
        else:        # Jarl (senhora): vestido azul-gelo e prata, tranças loiras
            skin, hair = "F5D9C0", "E8D8A0"
            kit = person(skin, hair, "long", "3A8ACF", body("7FC4E8", robe_trim="FFFFFF", collar="FFFFFF", emblem="1E3A5A",
                                                             sash="1E3A5A"),
                         "7FC4E8", "4A7AA8", "4A3A2E", female=True)
            kit["skirt"] = skirt("7FC4E8", "FFFFFF", pattern="1E3A5A")
            kit["hair_long"] = long_hair(hair, braid=True)
            kit["cape"] = cape("1E3A5A", "E8E8F0", trim="FFFFFF", emblem="7FC4E8", ermine=True)
            kit["crown"] = crown("C0C8D0", "5FD8E8")
        sleeve = ["1E3A5A", "7FC4E8"][v]
        cuff = "FFFFFF"
    kit["arm_r"] = arm(sleeve, skin, cuff=cuff, sleeve_rows=9)
    kit["arm_l"] = arm(sleeve, skin, cuff=cuff, sleeve_rows=9)
    kit["scepter_rod"] = metal("FFD86B" if kingdom == "sahra" else "C0C8D0", rivets=False)
    kit["scepter_orb"] = gem("FFB347" if kingdom == "sahra" else "BFEFFF")
    return kit


# =================================================================================================
#  Brasões das facções dos reinos (v0.9) — mesma receita dos brasões da v0.8 (tools/gen/icons.py)
# =================================================================================================
def crest_sahra(d, icons):
    # Império de Sahra: sol NASCENTE atrás de duas dunas (meio sol de raios longos) e a estrela da caravana no alto
    icons.sun(d, 11.0, 14.0, 4.6, 12, 4.0, "p", "yellow")
    d.fill(lambda x, y: y >= 14.6 + 2.6 * ((x - 5.0) / 7.5) ** 2 and y <= 21.4 and 0.4 <= x <= 13.6, "sand", g=6)
    d.fill(lambda x, y: y >= 15.8 + 2.0 * ((x - 16.0) / 6.5) ** 2 and y <= 21.4 and 8.4 <= x <= 21.6, "sand.d", g=7)
    d.star(17.6, 3.6, 2.4, 0.8, 4, -90, "W", g=8)
    d.star(4.4, 5.6, 1.4, 0.5, 4, -90, "W", g=8)


def crest_nordheim(d, icons):
    # Reino de Nordheim: dois picos nevados, a cruz branca do estandarte no meio e a estrela do norte
    d.poly([(0.6, 21.0), (6.6, 9.0), (11.0, 14.0), (15.4, 7.0), (21.4, 21.0)], "p.d", g=0)
    d.poly([(4.6, 13.0), (6.6, 9.0), (8.8, 11.6), (7.6, 13.0), (6.4, 12.0)], "W", g=1)
    d.poly([(13.0, 11.4), (15.4, 7.0), (18.0, 11.6), (16.6, 12.8), (15.2, 11.6), (14.0, 12.6)], "W", g=1)
    d.rect(9, 12, 12, 21, "W", g=5)
    d.rect(5, 15, 16, 17, "W", g=5)
    d.star(11.0, 4.2, 3.2, 1.0, 4, -90, "W", g=6)
    d.star(11.0, 4.2, 1.6, 0.6, 4, -45, "p", g=7)


def faction_emblems():
    """Brasões 32x32 de kingdom_sahra e kingdom_nordheim. Sem o tools/gen/icons.py (ou se ele mudar), não gera nada."""
    try:
        import icons  # noqa: E402 - receita compartilhada dos brasões (aro, campo e pictograma)
    except Exception as e:  # pragma: no cover
        print("cities: brasões não gerados (icons.py indisponível: %s)" % e)
        return
    out_dir = os.path.join(A, "textures/gui/faction")
    for fid, col, metal_c, fn in (("kingdom_sahra", "E07B2A", "FFD878", crest_sahra),
                                  ("kingdom_nordheim", "7FC4E8", "D8DEF0", crest_nordheim)):
        fc = icons.rgb(col)
        out, fillmask = icons.draw_crest(fc, icons.rgb(metal_c))
        pic = icons.Pic(22, 22)
        fn(pic, icons)
        ramps = icons.ramps_for(fc, "arcane")
        ramps["p"] = icons.make_ramp(icons.elem_light(fc))
        h, s_, v = icons.to_hsv(fc)
        ramps["q"] = icons.make_ramp(icons.from_hsv(h, min(1, s_ * 1.1 + 0.15), 0.62))
        img = pic.render(ramps, icons.shade(fc, 0.07, 1.0, 0.1))
        ox, oy = 5, 4
        for y in range(22):
            for x in range(22):
                if img[y][x][3]:
                    X, Y = ox + x + 1, oy + y + 1
                    if 0 <= X < 32 and 0 <= Y < 32 and fillmask[Y][X]:
                        out[Y][X] = icons.blend(out[Y][X], (0, 0, 0, 255), 0.42)
        icons.paste(out, img, ox, oy)
        icons.save_px(out, os.path.join(out_dir, fid + ".png"))


# =================================================================================================
#  Animações
# =================================================================================================
def kf(*pairs):
    out = {}
    for t, v in pairs:
        out["0.0" if t == 0 else f"{t:.2f}".rstrip("0").rstrip(".")] = v
    return out


def animations():
    p = "animation.city_npc"
    a = {
        f"{p}.idle": {"loop": True, "animation_length": 3.0, "bones": {
            "body": {"rotation": kf((0, [0, 0, 0]), (1.5, [1.5, 0, 0]), (3.0, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (1.5, [-3, 0, 0]), (3.0, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, 3]), (1.5, [-3, 0, 6]), (3.0, [0, 0, 3]))},
            "left_arm": {"rotation": kf((0, [0, 0, -3]), (1.5, [-3, 0, -6]), (3.0, [0, 0, -3]))},
            "cape": {"rotation": kf((0, [3, 0, 0]), (1.5, [6, 0, 0]), (3.0, [3, 0, 0]))},
            "hair_long": {"rotation": kf((0, [2, 0, 0]), (1.5, [5, 0, 0]), (3.0, [2, 0, 0]))},
            "plume": {"rotation": kf((0, [0, 0, 0]), (1.5, [-6, 0, 0]), (3.0, [0, 0, 0]))},
        }},
        f"{p}.walk": {"loop": True, "animation_length": 1.0, "bones": {
            "right_leg": {"rotation": kf((0, [30, 0, 0]), (0.5, [-30, 0, 0]), (1.0, [30, 0, 0]))},
            "left_leg": {"rotation": kf((0, [-30, 0, 0]), (0.5, [30, 0, 0]), (1.0, [-30, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-25, 0, 3]), (0.5, [25, 0, 3]), (1.0, [-25, 0, 3]))},
            "left_arm": {"rotation": kf((0, [25, 0, -3]), (0.5, [-25, 0, -3]), (1.0, [25, 0, -3]))},
            "body": {"rotation": kf((0, [2, 3, 0]), (0.5, [2, -3, 0]), (1.0, [2, 3, 0]))},
            "cape": {"rotation": kf((0, [10, 0, 0]), (0.5, [20, 0, 0]), (1.0, [10, 0, 0]))},
            "skirt": {"rotation": kf((0, [0, 0, 2]), (0.5, [0, 0, -2]), (1.0, [0, 0, 2]))},
            "hair_long": {"rotation": kf((0, [6, 0, 0]), (0.5, [12, 0, 0]), (1.0, [6, 0, 0]))},
        }},
        f"{p}.run": {"loop": True, "animation_length": 0.6, "bones": {
            "right_leg": {"rotation": kf((0, [45, 0, 0]), (0.3, [-45, 0, 0]), (0.6, [45, 0, 0]))},
            "left_leg": {"rotation": kf((0, [-45, 0, 0]), (0.3, [45, 0, 0]), (0.6, [-45, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-45, 0, 6]), (0.3, [40, 0, 6]), (0.6, [-45, 0, 6]))},
            "left_arm": {"rotation": kf((0, [40, 0, -6]), (0.3, [-45, 0, -6]), (0.6, [40, 0, -6]))},
            "body": {"rotation": kf((0, [8, 4, 0]), (0.3, [8, -4, 0]), (0.6, [8, 4, 0]))},
            "cape": {"rotation": kf((0, [25, 0, 0]), (0.3, [38, 0, 0]), (0.6, [25, 0, 0]))},
            "hair_long": {"rotation": kf((0, [15, 0, 0]), (0.3, [25, 0, 0]), (0.6, [15, 0, 0]))},
            "skirt": {"rotation": kf((0, [-4, 0, 3]), (0.3, [-4, 0, -3]), (0.6, [-4, 0, 3]))},
        }},
        f"{p}.talk": {"loop": True, "animation_length": 1.6, "bones": {
            "right_arm": {"rotation": kf((0, [-20, 0, 8]), (0.4, [-45, 8, 14]), (0.8, [-28, -6, 8]), (1.2, [-40, 4, 12]),
                                         (1.6, [-20, 0, 8]))},
            "left_arm": {"rotation": kf((0, [0, 0, -4]), (0.8, [-12, 0, -8]), (1.6, [0, 0, -4]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.4, [5, 6, 0]), (0.8, [-2, -5, 0]), (1.2, [4, 3, 0]), (1.6, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.8, [2, 2, 0]), (1.6, [0, 0, 0]))},
        }},
        f"{p}.work": {"loop": True, "animation_length": 1.2, "bones": {
            "right_arm": {"rotation": kf((0, [-60, 0, 0]), (0.35, [-120, 0, 5]), (0.5, [-55, 0, 0]), (0.6, [-62, 0, 0]),
                                         (1.2, [-60, 0, 0]))},
            "left_arm": {"rotation": kf((0, [-35, 0, -8]), (1.2, [-35, 0, -8]))},
            "head": {"rotation": kf((0, [14, 0, 0]), (0.5, [18, 0, 0]), (1.2, [14, 0, 0]))},
            "body": {"rotation": kf((0, [4, 0, 0]), (0.5, [7, 0, 0]), (1.2, [4, 0, 0]))},
        }},
        f"{p}.attack": {"loop": False, "animation_length": 0.5, "bones": {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.12, [-20, 0, 10]), (0.25, [-95, 0, 0]), (0.5, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.12, [0, 15, 0]), (0.25, [6, -15, 0]), (0.5, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [-40, 0, -10]), (0.5, [0, 0, 0]))},
        }},
        f"{p}.wave": {"loop": False, "animation_length": 1.0, "bones": {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.15, [-160, 0, 18]), (0.35, [-160, 0, 40]), (0.55, [-160, 0, 15]),
                                         (0.75, [-160, 0, 38]), (1.0, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [-6, 0, 4]), (1.0, [0, 0, 0]))},
        }},
        f"{p}.cheer": {"loop": False, "animation_length": 1.4, "bones": {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.2, [-170, 0, 20]), (0.5, [-150, 0, 30]), (0.8, [-170, 0, 20]),
                                         (1.1, [-150, 0, 30]), (1.4, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.2, [-170, 0, -20]), (0.5, [-150, 0, -30]), (0.8, [-170, 0, -20]),
                                        (1.1, [-150, 0, -30]), (1.4, [0, 0, 0]))},
            "root": {"position": kf((0, [0, 0, 0]), (0.25, [0, 3, 0]), (0.45, [0, 0, 0]), (0.85, [0, 3, 0]), (1.05, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.25, [-15, 0, 0]), (1.4, [0, 0, 0]))},
        }},
        f"{p}.bow": {"loop": False, "animation_length": 1.0, "bones": {
            "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [28, 0, 0]), (0.6, [28, 0, 0]), (1.0, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [10, 0, 0]), (1.0, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-30, -20, 0]), (1.0, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [10, 0, -15]), (1.0, [0, 0, 0]))},
        }},
        # v1.0: abordagem da guarda - "Alto!" (mao espalmada a frente) e bronca (dedo em riste balancando, cabeca negando)
        f"{p}.halt": {"loop": False, "animation_length": 1.3, "bones": {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.15, [-100, -10, 8]), (0.25, [-92, -10, 6]), (0.95, [-95, -10, 6]),
                                         (1.3, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.2, [-15, 0, -8]), (1.3, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.15, [-4, 0, 0]), (0.95, [-3, 0, 0]), (1.3, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.15, [-8, 0, 0]), (0.95, [-6, 0, 0]), (1.3, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [3, 0, 0]), (0.2, [12, 0, 0]), (1.3, [3, 0, 0]))},
        }},
        f"{p}.scold": {"loop": False, "animation_length": 1.5, "bones": {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.2, [-125, 0, 10]), (0.4, [-120, 0, 22]), (0.6, [-125, 0, 2]),
                                         (0.8, [-120, 0, 22]), (1.0, [-125, 0, 2]), (1.2, [-118, 0, 14]), (1.5, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [-20, 0, -25]), (1.25, [-20, 0, -25]), (1.5, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [4, -14, 0]), (0.55, [4, 14, 0]), (0.8, [4, -12, 0]), (1.05, [4, 10, 0]),
                                    (1.3, [2, 0, 0]), (1.5, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.2, [5, 0, 0]), (1.3, [5, 0, 0]), (1.5, [0, 0, 0]))},
        }},
    }
    return {"format_version": "1.8.0", "animations": a}


def geckolib_files():
    g = npc_geo()
    write_json(os.path.join(A, "geo/entity/city_npc.geo.json"), g.to_json(bounds=(3, 4)))
    write_json(os.path.join(A, "animations/entity/city_npc.animation.json"), animations())
    out = os.path.join(A, "textures/entity/city_npc")
    for i in range(4):
        g.paint(guard_painters(i), 100 + i).save(os.path.join(out, f"guard_{i}.png"))
    for i, c in enumerate(CITIZENS):
        g.paint(citizen_painters(c), 200 + i).save(os.path.join(out, f"citizen_{i}.png"))
    for prof in PROFESSIONS:
        for v in range(2):
            g.paint(merchant_painters(prof, v), 300 + PROFESSIONS.index(prof) * 2 + v).save(
                os.path.join(out, f"merchant_{prof}_{v}.png"))
    for v in range(2):
        g.paint(lord_painters(v), 400 + v).save(os.path.join(out, f"lord_{v}.png"))
    # v0.9: guardas e governantes dos reinos de Sahra e Nordheim (Aldória usa os de cima)
    for ki, kingdom in enumerate(("sahra", "nordheim")):
        for i in range(4):
            g.paint(kingdom_guard_painters(kingdom, i), 500 + ki * 10 + i).save(os.path.join(out, f"guard_{kingdom}_{i}.png"))
        for v in range(2):
            g.paint(kingdom_lord_painters(kingdom, v), 520 + ki * 10 + v).save(os.path.join(out, f"lord_{kingdom}_{v}.png"))


# =================================================================================================
#  Bloco city_heart (pedestal), cristal do renderer, ovos, ícones dos efeitos
# =================================================================================================
STONE = ["8E8E96", "7A7A84", "6A6A74", "9A9AA4"]
RUNE = hex_rgba("6FB8FF")
RUNE_L = hex_rgba("BFE6FF")


def stone_tex(seed, runes=False, top=False):
    c = Canvas(16, 16)
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            col = hex_rgba(STONE[rnd.randrange(4)] if rnd.random() < 0.3 else STONE[0])
            c.set(x, y, jitter(col, rnd, 0.05))
    if top:
        # anel de runas em volta do encaixe do cristal
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if 5.0 <= d <= 6.2:
                    c.set(x, y, hex_rgba("5A5A64"))
                if 5.3 <= d <= 5.9 and (x + y) % 3 == 0:
                    c.set(x, y, RUNE)
                if d < 2.6:
                    c.set(x, y, mix(RUNE, RUNE_L, 0.5) if d < 1.5 else RUNE)
        for x in range(16):
            c.set(x, 0, hex_rgba("5A5A64"))
            c.set(x, 15, hex_rgba("4A4A54"))
    else:
        for x in range(16):
            c.set(x, 0, lighten(hex_rgba(STONE[0]), 0.15))
            c.set(x, 15, hex_rgba("4A4A54"))
        if runes:
            glyph = [".#...#.#..#...#.", "###.###.#.###.##", ".#...#..#..#..#."]
            for dy, row in enumerate(glyph):
                for dx, ch in enumerate(row):
                    if ch == "#":
                        c.set(dx, 6 + dy, RUNE if (dx + dy) % 2 else RUNE_L)
            for x in range(16):
                c.set(x, 4, hex_rgba("5A5A64"))
                c.set(x, 10, hex_rgba("5A5A64"))
    return c


def crystal_tex():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            t = y / 15
            base = mix(hex_rgba("E8F6FF"), hex_rgba("3F7FE0"), t)
            if (x + y) % 5 == 0:
                base = lighten(base, 0.35)
            if abs(x - 7.5) < 1 and y < 10:
                base = lighten(base, 0.5)
            c.set(x, y, (base[0], base[1], base[2], 230))
    return c


def heart_block():
    stone_tex(11).save(os.path.join(A, "textures/block/city_heart_side.png"))
    stone_tex(12, runes=True).save(os.path.join(A, "textures/block/city_heart_runes.png"))
    stone_tex(13, top=True).save(os.path.join(A, "textures/block/city_heart_top.png"))
    crystal_tex().save(os.path.join(A, "textures/entity/city_heart_crystal.png"))

    def el(frm, to, side, top="isekai_mahou:block/city_heart_top"):
        faces = {}
        for f in ("north", "south", "east", "west"):
            faces[f] = {"texture": "#side", "uv": [frm[0] if f in ("north", "south") else frm[2], 16 - to[1],
                                                    to[0] if f in ("north", "south") else to[2], 16 - frm[1]]}
        faces["up"] = {"texture": "#top", "uv": [frm[0], frm[2], to[0], to[2]]}
        faces["down"] = {"texture": "#bottom", "uv": [frm[0], frm[2], to[0], to[2]]}
        return {"from": list(frm), "to": list(to), "faces": faces}

    model = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "isekai_mahou:block/city_heart_side", "side": "isekai_mahou:block/city_heart_side",
                     "runes": "isekai_mahou:block/city_heart_runes", "top": "isekai_mahou:block/city_heart_top",
                     "bottom": "isekai_mahou:block/city_heart_side"},
        "elements": [el((1, 0, 1), (15, 3, 15), "side"), el((3, 3, 3), (13, 11, 13), "runes"), el((2, 11, 2), (14, 13, 14), "side")],
    }
    # coluna com runas
    for f in ("north", "south", "east", "west"):
        model["elements"][1]["faces"][f]["texture"] = "#runes"
    write_json(os.path.join(A, "models/block/city_heart.json"), model)
    write_json(os.path.join(A, "blockstates/city_heart.json"), {"variants": {"": {"model": f"{NS}:block/city_heart"}}})
    write_json(os.path.join(A, "models/item/city_heart.json"), {"parent": f"{NS}:block/city_heart"})


def eggs():
    for e in ("city_guard", "citizen", "city_merchant", "city_lord"):
        write_json(os.path.join(A, f"models/item/{e}_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def effect_icons():
    # Procurado: cartaz de papel com borda vermelha e silhueta
    c = Canvas(18, 18)
    rnd = random.Random(5)
    for y in range(1, 17):
        for x in range(2, 16):
            c.set(x, y, jitter(hex_rgba("E8D8B0"), rnd, 0.05))
    for x in range(2, 16):
        c.set(x, 1, hex_rgba("B0302E")); c.set(x, 2, hex_rgba("B0302E")); c.set(x, 16, hex_rgba("8A6A4A"))
    for y in range(1, 17):
        c.set(2, y, hex_rgba("8A6A4A")); c.set(15, y, hex_rgba("8A6A4A"))
    for (x, y) in ((8, 5), (9, 5), (7, 6), (8, 6), (9, 6), (10, 6), (8, 7), (9, 7)):
        c.set(x, y, hex_rgba("3A2A22"))
    for y in range(8, 12):
        for x in range(6, 12):
            c.set(x, y, hex_rgba("3A2A22"))
    for x in range(4, 14):
        if x % 2 == 0:
            c.set(x, 13, hex_rgba("B0302E"))
        c.set(x, 14, hex_rgba("6B5B45") if x % 3 else hex_rgba("E8D8B0"))
    c.outline(hex_rgba("2A1A10"))
    c.save(os.path.join(A, "textures/mob_effect/wanted.png"))
    # Bênção da Aurora: sol nascente dourado
    c = Canvas(18, 18)
    c.circle(9, 11, 5.5, hex_rgba("FFD86B"))
    c.circle(9, 11, 3.5, hex_rgba("FFF2B0"))
    c.shade_sphere(9, 11, 5.5, light=0.3, dark=0.2)
    for i in range(7):
        import math
        a = math.pi * (i / 6)
        for k in range(7, 9):
            x = int(round(9 - math.cos(a) * k))
            y = int(round(11 - math.sin(a) * k))
            c.set(x, y, hex_rgba("FFE38A"))
    for x in range(1, 17):
        c.set(x, 13, hex_rgba("E8A040"))
        c.set(x, 14, hex_rgba("C97A3A"))
    for y in range(15, 18):
        for x in range(18):
            c.set(x, y, T)
    c.outline(hex_rgba("7A4A10"))
    c.save(os.path.join(A, "textures/mob_effect/aurora_blessing.png"))


# =================================================================================================
#  Traduções (pt_br + en_us)
# =================================================================================================
PT, EN = {}, {}


def tr(key, pt, en):
    PT[key] = pt
    EN[key] = en


def lines(group, pairs):
    for i, (pt, en) in enumerate(pairs):
        tr(f"dialogue.{NS}.cities.{group}.{i}", pt, en)


MALE = ["Aldric", "Bram", "Cedric", "Doran", "Edmar", "Fenwick", "Gareth", "Hadrian", "Ivo", "Joran", "Kael", "Lucan",
        "Marek", "Nestor", "Osric", "Perrin", "Quill", "Rowan", "Silas", "Tobias", "Ulric", "Varrick", "Wendel", "Yorick",
        "Zander", "Belmiro", "Tibério", "Anselmo"]
FEMALE = ["Adela", "Brena", "Celina", "Dália", "Elowen", "Fiora", "Gisela", "Helena", "Isolde", "Jacinta", "Kira", "Lirien",
          "Maelis", "Nívea", "Odete", "Petra", "Rosalind", "Selene", "Talia", "Ursula", "Vesna", "Wilma", "Yara", "Zélia",
          "Amara", "Bianca", "Clarisse", "Iris"]
SAHRA_CITY_NAMES = [("Qadira", "Qadira"), ("Zahrabad", "Zahrabad"), ("Al-Farun", "Al-Farun"), ("Miraj", "Miraj"),
                    ("Saffira", "Saffira"), ("Oásis de Nur", "Nur Oasis"), ("Tamarisco", "Tamarisk"),
                    ("Dunas Rubras", "Crimson Dunes"), ("Kesh-Amun", "Kesh-Amun"), ("Beyrah", "Beyrah"),
                    ("Solmira", "Solmira"), ("Hazar", "Hazar"), ("Areia Branca", "Whitesand"), ("Jannat", "Jannat"),
                    ("Simum", "Simoom"), ("Tahlir", "Tahlir")]
NORDHEIM_CITY_NAMES = [("Skjoldvik", "Skjoldvik"), ("Frostheim", "Frostheim"), ("Isvaldr", "Isvaldr"),
                       ("Brumavik", "Mistvik"), ("Hvitfjell", "Hvitfjell"), ("Ulfsgard", "Ulfsgard"),
                       ("Kaldbrekka", "Kaldbrekka"), ("Ravnholm", "Ravnholm"), ("Snaefell", "Snaefell"),
                       ("Pedra-Gélida", "Coldstone"), ("Gelvik", "Gelvik"), ("Járnborg", "Jarnborg"),
                       ("Vargholt", "Vargholt"), ("Nordvik", "Nordvik"), ("Ymirsfjord", "Ymirsfjord"),
                       ("Hrimstad", "Hrimstad")]
SAHRA_MALE = ["Amir", "Bashir", "Darius", "Farid", "Hakim", "Idris", "Jamal", "Karim", "Malik", "Nasir", "Omar", "Rashid",
              "Samir", "Tariq", "Yusuf", "Zahir", "Kaveh", "Rostam", "Ciro", "Faisal"]
SAHRA_FEMALE = ["Amira", "Dalia", "Farah", "Habiba", "Jasmin", "Layla", "Nadia", "Rania", "Samira", "Yasmin", "Zara",
                "Shirin", "Parisa", "Soraya", "Leila", "Nur", "Azadeh", "Mina", "Roxana", "Sahar"]
NORDHEIM_MALE = ["Bjorn", "Erik", "Gunnar", "Halvard", "Ivar", "Leif", "Magnus", "Olaf", "Ragnar", "Sigurd", "Torvald",
                 "Ulf", "Vidar", "Arne", "Einar", "Harald", "Knut", "Sven", "Thorin", "Rolf"]
NORDHEIM_FEMALE = ["Astrid", "Brynja", "Dagny", "Freya", "Gudrun", "Helga", "Ingrid", "Kara", "Liv", "Ragnhild", "Sigrid",
                   "Solveig", "Thora", "Yrsa", "Eira", "Hilda", "Runa", "Signe", "Tove", "Alva"]
CITY_NAMES = [("Valdória", "Valdoria"), ("Eldrath", "Eldrath"), ("Pedralva", "Stonedawn"), ("Lunária", "Lunaria"),
              ("Brumafria", "Coldmist"), ("Sóis-de-Areia", "Sunsands"), ("Rocharrubra", "Redrock"), ("Vale das Fadas", "Fairvale"),
              ("Altamira", "Highwatch"), ("Ventania", "Galeford"), ("Corvalho", "Ravenhold"), ("Serra Dourada", "Goldridge"),
              ("Portoverde", "Greenhaven"), ("Lírio Branco", "Whitelily"), ("Ferrolha", "Ironbolt"), ("Monteclaro", "Brightmount"),
              ("Aurelim", "Aurelim"), ("Fendaluz", "Lightcleft"), ("Grifônia", "Gryphonia"), ("Torre-Alva", "Whitetower"),
              ("Bosquerreal", "Kingswood"), ("Marisol", "Marisol"), ("Escudovale", "Shieldvale"), ("Nevoeira", "Fogmere")]
STRUCTURES = {
    "abyss_throne": ("o Trono do Abismo", "the Abyss Throne"), "arcane_ruins": ("ruínas arcanas", "arcane ruins"),
    "celestial_cathedral": ("a Catedral Celestial", "the Celestial Cathedral"), "druid_circle": ("um círculo druídico", "a druid circle"),
    "fairy_lake": ("um lago das fadas", "a fairy lake"), "forgotten_crypt": ("uma cripta esquecida", "a forgotten crypt"),
    "goblin_fortress": ("um forte goblin", "a goblin fortress"), "goblin_village": ("uma aldeia goblin", "a goblin village"),
    "guardian_temple_earth": ("o Templo do Guardião da Terra", "the Earth Guardian's Temple"),
    "guardian_temple_fire": ("o Templo do Guardião do Fogo", "the Fire Guardian's Temple"),
    "guardian_temple_water": ("o Templo do Guardião da Água", "the Water Guardian's Temple"),
    "guardian_temple_wind": ("o Templo do Guardião do Vento", "the Wind Guardian's Temple"),
    "guild_hall_village": ("uma vila com salão da Guilda", "a village with a Guild hall"),
    "harpy_nest": ("um ninho de harpias", "a harpy nest"), "kobold_den": ("uma toca de kobolds", "a kobold den"),
    "lizardman_village": ("uma aldeia de homens-lagarto", "a lizardfolk village"), "mage_tower": ("uma torre de mago", "a mage tower"),
    "minotaur_labyrinth": ("o labirinto do minotauro", "the minotaur's labyrinth"), "orc_camp": ("um acampamento orc", "an orc camp"),
    "profane_altar": ("um altar profano", "a profane altar"), "renegade_tower": ("a torre de um arquimago renegado", "a renegade archmage's tower"),
    "shrine_earth": ("um santuário da Terra", "an Earth shrine"), "shrine_fire": ("um santuário do Fogo", "a Fire shrine"),
    "shrine_light": ("um santuário da Luz", "a Light shrine"), "shrine_shadow": ("um santuário da Sombra", "a Shadow shrine"),
    "shrine_water": ("um santuário da Água", "a Water shrine"), "shrine_wind": ("um santuário do Vento", "a Wind shrine"),
    "troll_bridge": ("uma ponte de troll", "a troll bridge"), "twilight_arena": ("a Arena do Crepúsculo", "the Twilight Arena"),
    "wandering_mage_camp": ("um acampamento de mago andarilho", "a wandering mage camp"),
    "witch_hut_ancient": ("uma cabana de bruxa antiga", "an ancient witch hut"),
    "guild_city": ("uma cidade", "a city"), "capital_city": ("a Capital", "the Capital"),
    # v0.9: capitais dos reinos, pontos da cidade (guias) e estruturas novas
    "capital_sahra": ("Sahra, a capital do Império", "Sahra, the imperial capital"),
    "capital_nordheim": ("Nordheim, a capital do norte", "Nordheim, the northern capital"),
    "ascension_altar": ("o Altar de Ascensão", "the Ascension Altar"),
    "travel_stone": ("a Pedra de Teleporte", "the Waystone"),
    "hydra_lair": ("o covil de uma hidra no pântano", "a hydra's lair in the swamp"),
    "wyvern_peak": ("o pico do Wyvern Rei", "the Wyvern King's peak"),
    "basilisk_tomb": ("a tumba de um basilisco", "a basilisk's tomb"),
    "colossus_ruins": ("as ruínas de um colosso", "the ruins of a colossus"),
    "elemental_gate": ("um Portão Elemental adormecido", "a dormant Elemental Gate"),
    "abyssal_chasm": ("uma fenda abissal", "an abyssal chasm"),
    "celestial_stairway": ("a Escadaria Celeste", "the Celestial Stairway"),
    "twilight_rupture": ("a Ruptura do Crepúsculo", "the Twilight Rupture"),
    "ascension_sanctum": ("um Santuário de Ascensão", "an Ascension Sanctum"),
    "false_hydra_lair": ("uma vila abandonada... estranha demais", "an abandoned village... far too quiet"),
    "mimic_vault": ("uma sala do tesouro esquecida", "a forgotten treasure vault"),
    "manticore_den": ("a toca de uma manticora", "a manticore's den"),
    "unknown": ("um lugar estranho", "a strange place"),
}
PROF_NAMES = {
    "blacksmith": (("Ferreiro", "Ferreira"), "Blacksmith"), "alchemist": (("Alquimista", "Alquimista"), "Alchemist"),
    "scribe": (("Escriba da Academia", "Escriba da Academia"), "Academy Scribe"), "jeweler": (("Joalheiro", "Joalheira"), "Jeweler"),
    "innkeeper": (("Estalajadeiro", "Estalajadeira"), "Innkeeper"), "priest": (("Sacerdote da Aurora", "Sacerdotisa da Aurora"), "Priest of the Dawn"),
    "fence": (("Receptador", "Receptadora"), "Fence"), "grocer": (("Quitandeiro", "Quitandeira"), "Grocer"),
    "tailor": (("Alfaiate", "Costureira"), "Tailor"),
}


def translations():
    # ---------------- registros
    tr(f"block.{NS}.city_heart", "Coração da Cidade", "City Heart")
    tr(f"entity.{NS}.city_guard", "Guarda da Cidade", "City Guard")
    tr(f"entity.{NS}.citizen", "Cidadão", "Citizen")
    tr(f"entity.{NS}.city_merchant", "Mercador da Cidade", "City Merchant")
    tr(f"entity.{NS}.city_lord", "Lorde da Cidade", "City Lord")
    tr(f"entity.{NS}.cities.siege_chief", "Chefe de Guerra da Horda", "Horde Warchief")
    for e, pt, en in (("city_guard", "Ovo de Guarda da Cidade", "City Guard Spawn Egg"), ("citizen", "Ovo de Cidadão", "Citizen Spawn Egg"),
                      ("city_merchant", "Ovo de Mercador da Cidade", "City Merchant Spawn Egg"),
                      ("city_lord", "Ovo de Lorde da Cidade", "City Lord Spawn Egg")):
        tr(f"item.{NS}.{e}_spawn_egg", pt, en)
    tr(f"effect.{NS}.wanted", "Procurado", "Wanted")
    tr(f"effect.{NS}.wanted.description", "Crime contra um reino: os guardas DAQUELE reino atacam à vista (os dos outros reinos não ligam). Pague a fiança a um guarda dele, limpe a ficha com o receptador ou espere.",
       "Crime against a kingdom: THAT kingdom's guards attack on sight (other kingdoms don't care). Pay bail to one of its guards, clear your record with the fence, or wait it out.")
    tr(f"effect.{NS}.aurora_blessing", "Bênção da Aurora", "Blessing of the Dawn")
    tr(f"effect.{NS}.aurora_blessing.description", "+4 de vida máxima, +2 de armadura, sorte e cura suave.",
       "+4 max health, +2 armor, luck and gentle healing.")

    # ---------------- nomes
    for i, n in enumerate(MALE):
        tr(f"npc.{NS}.cities.name.m.{i}", n, n)
    for i, n in enumerate(FEMALE):
        tr(f"npc.{NS}.cities.name.f.{i}", n, n)
    for i, (pt, en) in enumerate(CITY_NAMES):
        tr(f"place.{NS}.cities.city.{i}", pt, en)
    tr(f"place.{NS}.cities.capital", "Aldória", "Aldoria")
    # v0.9: os outros reinos (nome da capital = nome curto do reino)
    tr(f"place.{NS}.cities.capital.sahra", "Sahra", "Sahra")
    tr(f"place.{NS}.cities.capital.nordheim", "Nordheim", "Nordheim")
    for kingdom, names in (("sahra", SAHRA_CITY_NAMES), ("nordheim", NORDHEIM_CITY_NAMES)):
        for i, (pt, en) in enumerate(names):
            tr(f"place.{NS}.cities.city.{kingdom}.{i}", pt, en)
    for kingdom, male, female in (("sahra", SAHRA_MALE, SAHRA_FEMALE), ("nordheim", NORDHEIM_MALE, NORDHEIM_FEMALE)):
        for i, n in enumerate(male):
            tr(f"npc.{NS}.cities.name.{kingdom}.m.{i}", n, n)
        for i, n in enumerate(female):
            tr(f"npc.{NS}.cities.name.{kingdom}.f.{i}", n, n)
    for pid, ((m, f), en) in PROF_NAMES.items():
        tr(f"profession.{NS}.cities.{pid}", m, en)
        tr(f"profession.{NS}.cities.{pid}.f", f, en)
    tr(f"role.{NS}.cities.guard", "Guarda da Cidade", "City Guard")
    tr(f"role.{NS}.cities.guard.f", "Guarda da Cidade", "City Guard")
    tr(f"role.{NS}.cities.citizen", "Cidadão", "Citizen")
    tr(f"role.{NS}.cities.citizen.f", "Cidadã", "Citizen")
    tr(f"role.{NS}.cities.lord", "Lorde", "Lord")
    tr(f"role.{NS}.cities.lord.f", "Lady", "Lady")
    tr(f"role.{NS}.cities.merchant", "Mercador", "Merchant")
    tr(f"role.{NS}.cities.merchant.f", "Mercadora", "Merchant")
    tr(f"role.{NS}.cities.lord_of", "Lorde de %s", "Lord of %s")
    tr(f"role.{NS}.cities.lord_of.f", "Lady de %s", "Lady of %s")
    tr(f"role.{NS}.cities.lord.sahra", "Sultão", "Sultan")
    tr(f"role.{NS}.cities.lord.f.sahra", "Sultana", "Sultana")
    tr(f"role.{NS}.cities.lord_of.sahra", "Sultão de %s", "Sultan of %s")
    tr(f"role.{NS}.cities.lord_of.f.sahra", "Sultana de %s", "Sultana of %s")
    tr(f"role.{NS}.cities.lord.nordheim", "Jarl", "Jarl")
    tr(f"role.{NS}.cities.lord.f.nordheim", "Jarl", "Jarl")
    tr(f"role.{NS}.cities.lord_of.nordheim", "Jarl de %s", "Jarl of %s")
    tr(f"role.{NS}.cities.lord_of.f.nordheim", "Jarl de %s", "Jarl of %s")

    # ---------------- tela
    tr(f"screen.{NS}.cities.talk", "Conversar", "Talk")
    tr(f"screen.{NS}.cities.trade", "Trocar", "Trade")
    tr(f"screen.{NS}.cities.trade_locked", "Recusa-se a negociar com você (reputação baixa demais).",
       "Refuses to trade with you (reputation too low).")
    tr(f"screen.{NS}.cities.services", "Serviços", "Services")
    tr(f"screen.{NS}.cities.services_title", "✦ Serviços", "✦ Services")
    tr(f"screen.{NS}.cities.rumors", "Rumores", "Rumors")
    tr(f"screen.{NS}.cities.bye", "Despedir-se", "Farewell")
    tr(f"screen.{NS}.cities.back", "Voltar", "Back")
    tr(f"screen.{NS}.cities.coins", "Moedas: %s", "Coins: %s")
    tr(f"screen.{NS}.cities.service_cost", "%s — %s moedas", "%s — %s coins")
    tr(f"screen.{NS}.cities.no_coins", "Moedas da Guilda insuficientes.", "Not enough Guild Coins.")
    tr(f"screen.{NS}.cities.service_unavailable", "Não disponível agora.", "Not available right now.")

    # ---------------- serviços
    services = {
        "bail": (("Pagar a fiança", "Limpa o \"Procurado\" neste reino e os guardas dele param de te perseguir."),
                 ("Pay bail", "Clears \"Wanted\" in this kingdom and its guards stop chasing you.")),
        "audience": (("Pedir audiência", "Recompensas do Lorde ao chegar a Honrado e a Exaltado no Reino."),
                     ("Request an audience", "The Lord's rewards when you reach Honored and Exalted with the Kingdom.")),
        "heal": (("Cura completa", "Recupera toda a vida e apaga o fogo."), ("Full healing", "Restores all health and puts out fire.")),
        "cleanse": (("Purificação", "Remove condições, maldições e efeitos ruins (não apaga crimes)."),
                    ("Purification", "Removes conditions, curses and harmful effects (does not erase crimes).")),
        "blessing": (("Bênção da Aurora", "10 minutos: +4 de vida máxima, +2 de armadura, sorte e cura suave."),
                     ("Blessing of the Dawn", "10 minutes: +4 max health, +2 armor, luck and gentle healing.")),
        "room": (("Alugar um quarto", "A cama vira seu ponto de nascimento. Durma nela à noite e acorde Descansado."),
                 ("Rent a room", "The bed becomes your spawn point. Sleep in it at night and wake up Rested.")),
        "mana": (("Restaurar mana", "Um tônico arcano que enche seu mana."), ("Restore mana", "An arcane tonic that fills your mana.")),
        "repair": (("Consertar o item da mão", "Deixa como novo o item que você está segurando."),
                   ("Repair held item", "Makes the item in your hand as good as new.")),
        "clear_record": (("Limpar a ficha", "Um \"contrato\" discreto: o \"Procurado\" some em TODOS os reinos. O Submundo agradece."),
                         ("Clear your record", "A discreet \"contract\": \"Wanted\" disappears in EVERY kingdom. The Underworld thanks you.")),
        "altar_guide": (("Onde fica o Altar de Ascensão?", "De graça: uma seta no rastreador até o altar público da cidade. Leve o tributo do seu Círculo!"),
                        ("Where is the Ascension Altar?", "Free: an arrow on the tracker to the city's public altar. Bring your Circle's tribute!")),
        "stone_guide": (("Onde fica a Pedra de Teleporte?", "De graça: uma seta até a Pedra da praça. Toque nela para descobrir o destino e viajar."),
                        ("Where is the Waystone?", "Free: an arrow to the plaza's Waystone. Touch it to discover the destination and travel.")),
    }
    for sid, ((pl, pd), (el, ed)) in services.items():
        tr(f"service.{NS}.cities.{sid}", pl, el)
        tr(f"service.{NS}.cities.{sid}.desc", pd, ed)

    # ---------------- falas pontuais
    one = {
        "trade_busy": ("Estou atendendo outro cliente. Um momento!", "I'm with another customer. One moment!"),
        "crime.help": ("Socorro! Guardas!", "Help! Guards!"),
        "crime.thanks": ("Bela lâmina! O Reino agradece.", "Fine blade! The Kingdom thanks you."),
        "crime.thief": ("Ei! Ladrão! Tire as mãos daí!", "Hey! Thief! Hands off!"),
        "lord.audience.done": ("Você já tem toda a minha gratidão, herói. Aldória está em dívida com você.",
                               "You already have all my gratitude, hero. Aldoria is in your debt."),
        "lord.audience.next_exalted": ("Continue servindo o Reino. Quando for Exaltado, terei algo digno de um herói.",
                                       "Keep serving the Kingdom. When you are Exalted, I'll have something worthy of a hero."),
        "lord.audience.not_yet": ("Ainda não o conheço bem. Volte quando for %s no Reino — o Quadro do Reino na praça tem trabalho.",
                                  "I don't know you well yet. Come back when you are %s with the Kingdom — the Kingdom Board in the plaza has work."),
        "lord.city_news": ("Mensageiros contam de %1$s, a uns %2$s blocos %3$s daqui. Uma viagem que vale a pena.",
                           "Messengers speak of %1$s, about %2$s blocks %3$s of here. A journey worth making."),
        "lord.reward.exalted": ("Ajoelhe-se, %s... e levante-se HERÓI DE ALDÓRIA! Que estes tesouros sirvam à sua lenda.",
                                "Kneel, %s... and rise, HERO OF ALDORIA! May these treasures serve your legend."),
        "lord.audience.done.sahra": ("Sua lenda já ecoa pelas dunas, herói. O Império de Sahra está em dívida com você.",
                                     "Your legend already echoes across the dunes, hero. The Empire of Sahra is in your debt."),
        "lord.audience.done.nordheim": ("Seu nome já é cantado nos salões de hidromel, herói. Nordheim está em dívida com você.",
                                        "Your name is already sung in the mead halls, hero. Nordheim is in your debt."),
        "lord.reward.exalted.sahra": ("Ajoelhe-se, %s... e levante-se ESCUDO DO SOL DE SAHRA! Que o ouro do Império sirva à sua lenda.",
                                      "Kneel, %s... and rise, SHIELD OF THE SUN OF SAHRA! May the Empire's gold serve your legend."),
        "lord.reward.exalted.nordheim": ("Ajoelhe-se, %s... e levante-se HERÓI DE NORDHEIM! Os skalds cantarão seu nome por mil invernos.",
                                         "Kneel, %s... and rise, HERO OF NORDHEIM! The skalds will sing your name for a thousand winters."),
        "service.altar_guide.done": ("O Altar de Ascensão fica %1$s, a uns %2$s blocos. Siga a seta — leve o tributo do seu Círculo!",
                                     "The Ascension Altar is %1$s, about %2$s blocks away. Follow the arrow — bring your Circle's tribute!"),
        "service.stone_guide.done": ("A Pedra de Teleporte fica %1$s, a uns %2$s blocos, no jardim da praça. Toque nela!",
                                     "The Waystone is %1$s, about %2$s blocks away, in the plaza garden. Touch it!"),
        "lord.reward.honored": ("%s, sua honra é conhecida até nos salões do castelo. Aceite este presente do Reino.",
                                "%s, your honor is known even in the castle halls. Accept this gift from the Kingdom."),
        "rumor.again": ("Já te contei: procure %s. A seta não mente!", "I already told you: look for %s. Follow the arrow!"),
        "rumor.found.0": ("Dizem que há %1$s a uns %2$s blocos %3$s daqui. Cuidado por lá!",
                          "They say there's %1$s about %2$s blocks %3$s of here. Be careful out there!"),
        "rumor.found.1": ("Um viajante jurou ter visto %1$s, coisa de %2$s blocos %3$s. Eu não iria sozinho...",
                          "A traveler swore he saw %1$s, some %2$s blocks %3$s. I wouldn't go alone..."),
        "rumor.found.2": ("Psst... %1$s. Uns %2$s blocos %3$s. Tesouro, perigo, ou os dois.",
                          "Psst... %1$s. About %2$s blocks %3$s. Treasure, danger, or both."),
        "rumor.none": ("Hm... ultimamente não ouvi nada de interessante por perto.", "Hm... I haven't heard of anything interesting nearby lately."),
        "service.unavailable": ("Isso não posso fazer agora.", "I can't do that right now."),
        "service.no_coins": ("São %s Moedas da Guilda. Volte quando tiver.", "That's %s Guild Coins. Come back when you have them."),
        "service.bail.done": ("Fiança paga. Ande na linha daqui pra frente.", "Bail paid. Walk the line from now on."),
        "service.heal.done": ("Que a luz da Aurora feche suas feridas.", "May the Dawn's light close your wounds."),
        "service.cleanse.done": ("Pronto. Nada de impuro permanece em você.", "Done. Nothing impure remains in you."),
        "service.blessing.done": ("A Aurora caminha com você. Vá com coragem!", "The Dawn walks with you. Go with courage!"),
        "service.room.done": ("Seu quarto é o da cama em %s %s %s. Bom descanso!", "Your room is the bed at %s %s %s. Rest well!"),
        "service.room.none": ("Todos os quartos estão ocupados, desculpe!", "All rooms are taken, sorry!"),
        "service.mana.done": ("Beba de uma vez. Sentiu? Mana fresquinho!", "Drink it in one go. Feel that? Fresh mana!"),
        "service.repair.done": ("%s está como nova. Trabalho de mestre!", "%s is good as new. Master's work!"),
        "service.clear_record.done": ("Que ficha? Nunca ouvi falar de você.", "What record? Never heard of you."),
    }
    for k, (pt, en) in one.items():
        tr(f"dialogue.{NS}.cities.{k}", pt, en)

    # ---------------- cidadãos
    lines("citizen.ambient", [
        ("Bom dia! Que dia bonito.", "Good morning! Lovely day."),
        ("O pão da padaria saiu quentinho.", "The bakery's bread just came out warm."),
        ("Ouviu falar da Guilda? Dizem que pagam bem.", "Heard about the Guild? They say it pays well."),
        ("As ruas andam tranquilas, graças aos guardas.", "The streets are calm, thanks to the guards."),
        ("Preciso comprar fio na alfaiataria...", "I need to buy thread at the tailor's..."),
        ("Será que vai chover hoje?", "Will it rain today?"),
        ("Aventureiro novo na cidade? Bem-vindo!", "New adventurer in town? Welcome!"),
        ("Meu primo foi para a Academia. Agora só fala em glifos.", "My cousin joined the Academy. Now all he talks about is glyphs."),
        ("A taverna está cheia de histórias hoje.", "The tavern is full of stories today."),
        ("Cuidado com os goblins na estrada!", "Watch out for goblins on the road!"),
    ])
    lines("citizen.night", [
        ("Está tarde... vou para casa.", "It's late... I'm heading home."),
        ("À noite os monstros rondam as muralhas.", "At night monsters prowl the walls."),
        ("Feche bem a porta esta noite.", "Lock your door tight tonight."),
        ("Ouviu isso? Deve ser só o vento...", "Did you hear that? Must be the wind..."),
    ])
    lines("citizen.bad", [
        ("Hm. O que você quer?", "Hmph. What do you want?"),
        ("Andam falando mal de você por aí.", "People have been saying bad things about you."),
        ("Fale rápido, tenho o que fazer.", "Make it quick, I'm busy."),
        ("Não quero confusão com você.", "I don't want trouble with you."),
    ])
    lines("citizen.neutral", [
        ("Olá, %s. Precisa de alguma coisa?", "Hello, %s. Need something?"),
        ("Bem-vindo à nossa cidade, viajante.", "Welcome to our city, traveler."),
        ("A vida aqui é simples, mas é boa.", "Life here is simple, but good."),
        ("Já foi à praça? O chafariz é lindo à noite.", "Been to the plaza? The fountain is lovely at night."),
        ("O mercado vende de tudo um pouco.", "The market sells a bit of everything."),
        ("Dizem que o Lorde é justo. Espero que seja verdade.", "They say the Lord is fair. I hope it's true."),
    ])
    lines("citizen.good", [
        ("%s! Que bom te ver de novo!", "%s! Good to see you again!"),
        ("Todo mundo fala das suas façanhas!", "Everyone talks about your deeds!"),
        ("Graças a gente como você, dormimos em paz.", "Thanks to people like you, we sleep in peace."),
        ("Quer um conselho de amigo? Visite o sacerdote antes de partir.", "Friendly advice? Visit the priest before you leave."),
        ("Minha filha quer ser aventureira por sua causa!", "My daughter wants to be an adventurer because of you!"),
        ("Se precisar de algo, é só pedir!", "If you need anything, just ask!"),
    ])
    lines("citizen.exalted", [
        ("É... é você mesmo, o herói de %2$s?! Posso apertar sua mão?", "Is... is it really you, the hero of %2$s?! May I shake your hand?"),
        ("As crianças brincam de ser você na praça!", "The kids play pretend as you in the plaza!"),
    ])
    lines("citizen.hostile", [
        ("Fique longe de mim!", "Stay away from me!"),
        ("Guardas! É aquele bandido!", "Guards! It's that outlaw!"),
    ])
    lines("citizen.wanted", [
        ("Você... você é procurado! Não chegue perto!", "You... you're wanted! Don't come near!"),
        ("Pague sua fiança a um guarda antes que te prendam!", "Pay your bail to a guard before they lock you up!"),
    ])
    lines("citizen.hint", [
        ("O Salão da Guilda fica perto da praça. Lá tem missões e a Recepcionista.", "The Guild Hall is near the plaza. Quests and the Receptionist are there."),
        ("A taverna aluga quartos: dormir lá vira seu ponto de partida.", "The tavern rents rooms: sleeping there sets your spawn point."),
        ("O Quadro do Reino, na praça, tem trabalhos pagos pela Coroa.", "The Kingdom Board in the plaza has jobs paid by the Crown."),
        ("O sacerdote da Igreja da Aurora cura feridas e abençoa viajantes.", "The priest of the Church of the Dawn heals wounds and blesses travelers."),
        ("Dizem que o receptador se esconde no beco... e compra de tudo.", "They say the fence hides in the alley... and buys anything."),
        ("O escriba da Torre da Academia vende papel, tinta e livros. Glifos? Só explorando!", "The scribe at the Academy Tower sells paper, ink and books. Glyphs? Only by exploring!"),
        ("O ferreiro conserta qualquer arma. E vende as do Arsenal para quem tem fama.", "The blacksmith repairs any weapon. And sells Armory gear to the renowned."),
        ("Mexer nos baús das casas? Se alguém vir, o Reino não perdoa.", "Rummaging through house chests? If someone sees, the Kingdom won't forgive."),
    ])

    # ---------------- guardas
    lines("guard.ambient", [
        ("Tudo em ordem por aqui.", "All in order here."),
        ("Mantenha a paz, cidadão.", "Keep the peace, citizen."),
        ("Nenhum goblin passa por este portão.", "No goblin gets through this gate."),
        ("Minha lança está afiada. Os monstros que se cuidem.", "My spear is sharp. Monsters beware."),
        ("Turno longo hoje...", "Long shift today..."),
        ("Pela glória de %s!", "For the glory of %s!"),
    ])
    lines("guard.night", [
        ("Fique atento. À noite eles vêm.", "Stay alert. At night they come."),
        ("Os sinos tocam se a Horda atacar.", "The bells ring if the Horde attacks."),
        ("Não ande sozinho fora das muralhas.", "Don't walk alone outside the walls."),
    ])
    lines("guard.bad", [
        ("Estou de olho em você.", "I've got my eye on you."),
        ("Um passo em falso e você vai pro calabouço.", "One wrong step and you're off to the dungeon."),
        ("Circulando.", "Move along."),
    ])
    lines("guard.neutral", [
        ("Precisa de algo, viajante?", "Need something, traveler?"),
        ("Respeite as leis e seremos amigos.", "Respect the laws and we'll be friends."),
        ("A Guilda fica perto da praça, se procura trabalho.", "The Guild is near the plaza, if you're looking for work."),
        ("Viu algum monstro na estrada? Avise a guarda.", "Seen any monsters on the road? Tell the guard."),
    ])
    lines("guard.good", [
        ("Salve, %s! A guarda conta com você.", "Hail, %s! The guard counts on you."),
        ("Lutar ao seu lado seria uma honra.", "Fighting by your side would be an honor."),
        ("Se a Horda vier, quero você na muralha.", "If the Horde comes, I want you on the wall."),
        ("Os rapazes do quartel falam muito de você.", "The lads at the barracks talk a lot about you."),
    ])
    lines("guard.exalted", [
        ("Herói! *bate continência*", "Hero! *salutes*"),
        ("A cidade inteira está segura com você aqui.", "The whole city is safe with you here."),
    ])
    lines("guard.hostile", [
        ("Você não é bem-vindo aqui. Saia ou será preso!", "You're not welcome here. Leave or be arrested!"),
        ("Em nome do Reino, pare!", "In the name of the Kingdom, halt!"),
    ])
    lines("guard.wanted", [
        ("Alto lá! Você é procurado!", "Halt! You're wanted!"),
        ("Pague a fiança ou enfrente a lança!", "Pay your bail or face the spear!"),
        ("Criminoso! Renda-se!", "Criminal! Surrender!"),
    ])
    lines("guard.battle", [
        ("Monstro na cidade! Comigo!", "Monster in the city! With me!"),
        ("Pela Coroa!", "For the Crown!"),
        ("Protejam os cidadãos!", "Protect the citizens!"),
        ("Nenhum passo a mais!", "Not one step further!"),
    ])

    # ---------------- lorde
    lines("lord.ambient", [
        ("O reino prospera, mas a Horda não descansa.", "The realm prospers, but the Horde never rests."),
        ("Preciso de aventureiros de confiança.", "I need trustworthy adventurers."),
        ("Os impostos deste ano... hm.", "This year's taxes... hmm."),
        ("%s já resistiu a coisas piores.", "%s has weathered worse."),
    ])
    lines("lord.bad", [
        ("Seu nome chegou aos meus ouvidos — e não por bons motivos.", "Your name has reached my ears — and not for good reasons."),
        ("Prove seu valor ao Reino antes de pedir qualquer coisa.", "Prove your worth to the Kingdom before asking for anything."),
    ])
    lines("lord.neutral", [
        ("Seja bem-vindo, %s. O Reino sempre precisa de braços fortes.", "Welcome, %s. The Kingdom always needs strong arms."),
        ("O Quadro do Reino na praça tem missões. Cumpra-as e terá minha atenção.", "The Kingdom Board in the plaza has missions. Complete them and you'll have my attention."),
        ("Defenda esta cidade e será recompensado.", "Defend this city and you will be rewarded."),
    ])
    lines("lord.good", [
        ("%s! Sempre um prazer receber um amigo do Reino.", "%s! Always a pleasure to receive a friend of the Kingdom."),
        ("Seus feitos chegaram à corte. Continue assim.", "Your deeds have reached the court. Keep it up."),
        ("Se precisar de algo, peça uma audiência.", "If you need anything, request an audience."),
    ])
    lines("lord.exalted", [
        ("Herói de %2$s! Minhas portas estão sempre abertas.", "Hero of %2$s! My doors are always open."),
        ("Bardos já cantam sua história nas tavernas.", "Bards already sing your tale in the taverns."),
    ])
    lines("lord.hostile", [
        ("Guardas! Tirem esse criminoso da minha frente!", "Guards! Get this criminal out of my sight!"),
        ("Você ousa entrar na minha casa?!", "You dare enter my house?!"),
    ])
    lines("lord.news", [
        ("Batedores viram goblins se juntando nas florestas. Uma horda pode estar a caminho.", "Scouts saw goblins gathering in the forests. A horde may be coming."),
        ("A Academia Arcana pede ajuda para estudar as Câmaras de Provação.", "The Arcane Academy asks for help studying the Trial Chambers."),
        ("Os santuários elementais despertam heróis. Já visitou algum?", "The elemental shrines awaken heroes. Have you visited one?"),
        ("Dizem que os orcs têm um senhor da guerra novo. Fique atento.", "They say the orcs have a new warlord. Stay alert."),
        ("A Igreja da Aurora diz que mortos-vivos andam inquietos nas criptas.", "The Church of the Dawn says the undead are restless in the crypts."),
        ("O Submundo cresce nos becos. Não me agrada, mas... às vezes é útil.", "The Underworld grows in the alleys. I dislike it, but... sometimes it's useful."),
    ])

    # ---------------- mercadores (genérico)
    lines("merchant.bad", [("Hm. Tenho mercadoria, mas o preço é outro pra você.", "Hmph. I have goods, but the price is different for you."),
                           ("Pague adiantado.", "Pay up front.")])
    lines("merchant.neutral", [("Dê uma olhada, %s. Tudo de qualidade!", "Take a look, %s. All quality goods!"),
                               ("Bons produtos, preço justo.", "Good products, fair price."),
                               ("Volte sempre que precisar.", "Come back whenever you need.")])
    lines("merchant.good", [("Para um cliente como você, %s, o melhor da casa!", "For a customer like you, %s, the house's best!"),
                            ("Guardei umas peças especiais para você.", "I've saved some special pieces for you."),
                            ("Desconto de amigo, como sempre.", "Friend's discount, as always.")])
    lines("merchant.exalted", [("Uma honra atender você! Leve o que quiser.", "An honor to serve you! Take whatever you want."),
                               ("Até as peças raras são suas, herói.", "Even the rare pieces are yours, hero.")])
    lines("merchant.hostile", [("Não vendo nada para gente da sua laia. Saia!", "I don't sell to your kind. Out!"),
                               ("Vá embora antes que eu chame a guarda.", "Leave before I call the guard.")])
    lines("merchant.thanks", [("Negócio fechado!", "Deal!"), ("Obrigado, volte sempre!", "Thank you, come again!"),
                              ("Ótima escolha!", "Great choice!"), ("Que a sorte te acompanhe!", "May luck be with you!")])
    lines("merchant.refuse", [("%s não negocia com você. Melhore sua reputação primeiro.", "%s won't trade with you. Improve your reputation first."),
                              ("Nem pensar. Com a sua fama? Fora!", "No way. With your reputation? Out!")])
    lines("siege.cheer", [("Vitória! A cidade está salva!", "Victory! The city is saved!"), ("Viva! Viva o herói!", "Hooray! Long live the hero!"),
                          ("Eles fugiram! Fugiram!", "They ran! They ran!"), ("Pela Coroa! Vencemos!", "For the Crown! We won!")])
    lines("siege.fear", [("Os sinos! A Horda está vindo!", "The bells! The Horde is coming!"), ("Todos para dentro!", "Everyone inside!"),
                         ("Que a Aurora nos proteja...", "May the Dawn protect us..."), ("Defendam o portão!", "Defend the gate!")])

    # ---------------- mercadores por profissão
    per = {
        "blacksmith": ([("Aço bom não se faz com pressa, %s. Mas vende rápido!", "Good steel isn't made in a hurry, %s. But it sells fast!"),
                        ("Precisa de lâmina, armadura ou conserto?", "Need a blade, armor or a repair?")],
                       [("*clang* *clang*", "*clang* *clang*"), ("O fogo da forja está perfeito hoje.", "The forge fire is perfect today."),
                        ("Traga ferro e eu te pago bem.", "Bring iron and I'll pay you well.")]),
        "alchemist": ([("Cuidado onde encosta, %s! Algumas dessas explodem.", "Careful what you touch, %s! Some of these explode."),
                       ("Poções, elixires, tônicos de mana... o que procura?", "Potions, elixirs, mana tonics... what are you after?")],
                      [("Hm, mais uma gota de verruga...", "Hm, one more drop of wart..."), ("Essa fumaça é normal. Acho.", "This smoke is normal. I think."),
                       ("Olhos de aranha frescos? Eu compro!", "Fresh spider eyes? I'm buying!")]),
        "scribe": ([("Saudações, %s. A Academia compartilha saber com quem merece.", "Greetings, %s. The Academy shares knowledge with the worthy."),
                    ("Papel, tinta, livros... conhecimento tem preço.", "Paper, ink, books... knowledge has a price.")],
                   [("Este glifo está mal desenhado...", "This glyph is poorly drawn..."), ("Silêncio, por favor. Estou copiando.", "Silence, please. I'm copying."),
                    ("A Academia paga bem por papel.", "The Academy pays well for paper.")]),
        "jeweler": ([("Ah, %s! Veja como brilham estas pedras.", "Ah, %s! See how these stones shine."),
                     ("Amuletos, anéis, gemas... tudo com encanto verdadeiro.", "Amulets, rings, gems... all with true enchantment.")],
                    [("Hm, essa ametista tem uma falha.", "Hm, this amethyst has a flaw."), ("Ouro de verdade não mente.", "Real gold doesn't lie."),
                     ("Uma joia certa muda um destino.", "The right jewel changes a destiny.")]),
        "innkeeper": ([("Bem-vindo ao Grifo Bêbado, %s! Comida, bebida e cama quente.", "Welcome to the Drunken Griffin, %s! Food, drink and a warm bed."),
                       ("Sente-se! O ensopado está no fogo.", "Have a seat! The stew is on the fire.")],
                      [("Quem quer mais uma rodada?", "Who wants another round?"), ("Ouvi cada história hoje...", "I've heard some tales today..."),
                       ("Os quartos lá em cima estão limpinhos.", "The rooms upstairs are spotless.")]),
        "priest": ([("Que a luz da Aurora te guie, %s.", "May the Dawn's light guide you, %s."),
                    ("Feridas, maldições, fardos... a Igreja ajuda.", "Wounds, curses, burdens... the Church helps.")],
                   [("A Aurora sempre volta.", "The Dawn always returns."), ("Rezo pelos aventureiros que partiram.", "I pray for the adventurers who left."),
                    ("Os mortos-vivos temem a luz.", "The undead fear the light.")]),
        "fence": ([("Psst, %s. Não viu nada, não ouviu nada. Quer comprar?", "Psst, %s. Saw nothing, heard nothing. Wanna buy?"),
                   ("Coisas raras, perguntas nenhumas.", "Rare goods, no questions.")],
                  [("Não olhe pra mim assim.", "Don't look at me like that."), ("Esse beco é meu escritório.", "This alley is my office."),
                   ("Os guardas? Eles não vêm aqui.", "The guards? They don't come here.")]),
        "grocer": ([("Fresquinho do campo, %s!", "Fresh from the fields, %s!"),
                    ("Frutas, legumes, pão... e compro sua colheita!", "Fruits, vegetables, bread... and I buy your harvest!")],
                   [("Olha a maçã! Maçã fresquinha!", "Apples! Fresh apples!"), ("Cenoura boa, preço bom!", "Good carrots, good price!"),
                    ("A colheita foi farta este ano.", "The harvest was plentiful this year.")]),
        "tailor": ([("Que roupa é essa, %s? Deixa eu ajudar.", "What are you wearing, %s? Let me help."),
                    ("Tecidos finos, cores do Reino, couro curtido...", "Fine fabrics, Kingdom colors, tanned leather...")],
                   [("Hm, falta um botão aqui.", "Hm, a button's missing here."), ("Azul do Reino está na moda.", "Kingdom blue is in fashion."),
                    ("Traga lã e couro que eu pago.", "Bring wool and leather and I'll pay.")]),
    }
    for pid, (greet, amb) in per.items():
        for i, (pt, en) in enumerate(greet):
            tr(f"dialogue.{NS}.cities.merchant.{pid}.greet.{i}", pt, en)
        for i, (pt, en) in enumerate(amb):
            tr(f"dialogue.{NS}.cities.merchant.{pid}.ambient.{i}", pt, en)

    # ---------------- rumores
    for d, pt, en in (("n", "ao norte", "north"), ("ne", "a nordeste", "northeast"), ("e", "a leste", "east"), ("se", "a sudeste", "southeast"),
                      ("s", "ao sul", "south"), ("sw", "a sudoeste", "southwest"), ("w", "a oeste", "west"), ("nw", "a noroeste", "northwest")):
        tr(f"rumor.{NS}.cities.dir.{d}", pt, en)
    for sid, (pt, en) in STRUCTURES.items():
        tr(f"rumor.{NS}.cities.structure.{sid}", pt, en)

    # ---------------- rastreador, mensagens, cerco
    tr(f"objective.{NS}.cities.rumor", "Rumor: %s", "Rumor: %s")
    tr(f"objective.{NS}.cities.rumor.detail", "Siga a seta (rumor da cidade)", "Follow the arrow (city rumor)")
    tr(f"objective.{NS}.cities.wanted", "Procurado!", "Wanted!")
    tr(f"objective.{NS}.cities.wanted.detail", "Pague a fiança a um guarda · %s:%s", "Pay bail to a guard · %s:%s")
    tr(f"message.{NS}.cities.wanted", "Você agora é PROCURADO em %s! Os guardas de lá vão atrás de você.",
       "You are now WANTED in %s! Its guards are coming for you.")
    tr(f"objective.{NS}.cities.wanted.where", "Procurado em %s!", "Wanted in %s!")
    tr(f"objective.{NS}.cities.guide", "Ir até %s", "Go to %s")
    tr(f"objective.{NS}.cities.guide.detail", "Siga a seta (um morador mostrou o caminho)", "Follow the arrow (a resident showed you the way)")
    tr(f"message.{NS}.cities.guide_arrived", "✦ Você chegou: %s", "✦ You arrived: %s")
    tr(f"message.{NS}.cities.murder", "Assassinato! O Reino jamais esquecerá este crime.", "Murder! The Kingdom will never forget this crime.")
    tr(f"message.{NS}.cities.seen", "%s viu você mexendo no baú!", "%s saw you rummaging through the chest!")
    tr(f"message.{NS}.cities.rested", "Você acorda Descansado: vida, fôlego e mana renovados.", "You wake up Rested: health, stamina and mana renewed.")
    tr(f"message.{NS}.cities.room_night", "%s dormiu no quarto alugado da taverna — a noite passou.", "%s slept in a rented tavern room — the night passed.")
    tr(f"message.{NS}.cities.rumor_found", "✦ Você encontrou o lugar do rumor: %s!", "✦ You found the rumored place: %s!")
    tr(f"message.{NS}.cities.siege_failed", "A Horda saqueou %s e recuou para a escuridão...", "The Horde plundered %s and retreated into the dark...")
    tr(f"message.{NS}.cities.lord.hero", "✦ HERÓI DE ALDÓRIA ✦", "✦ HERO OF ALDORIA ✦")
    tr(f"message.{NS}.cities.lord.hero.sahra", "✦ ESCUDO DO SOL DE SAHRA ✦", "✦ SHIELD OF THE SUN OF SAHRA ✦")
    tr(f"message.{NS}.cities.lord.hero.nordheim", "✦ HERÓI DE NORDHEIM ✦", "✦ HERO OF NORDHEIM ✦")
    for kind, pt, en in (("guard", "Um novo guarda, %s, assumiu o posto em %s.", "A new guard, %s, took the post in %s."),
                         ("citizen", "%s chegou para morar em %s.", "%s moved to %s."),
                         ("merchant", "%s abriu a loja de novo em %s.", "%s reopened the shop in %s."),
                         ("lord", "%s assumiu o governo de %s.", "%s took over the rule of %s.")):
        tr(f"message.{NS}.cities.respawn.{kind}", pt, en)
    tr(f"title.{NS}.cities.siege", "⚔ CERCO À CIDADE ⚔", "⚔ CITY UNDER SIEGE ⚔")
    tr(f"title.{NS}.cities.siege.sub", "A Horda se aproxima pelo portão %s!", "The Horde approaches from the gate to the %s!")
    tr(f"bossbar.{NS}.cities.siege_warning", "Cerco a %s — a Horda se aproxima!", "Siege of %s — the Horde approaches!")
    tr(f"bossbar.{NS}.cities.siege_wave", "Cerco a %s — Onda %s/%s", "Siege of %s — Wave %s/%s")
    tr(f"bossbar.{NS}.cities.siege_break", "Cerco a %s — próxima onda (%s/%s)", "Siege of %s — next wave (%s/%s)")
    tr(f"announce.{NS}.cities.wave", "Onda %s de %s!", "Wave %s of %s!")
    tr(f"announce.{NS}.cities.wave_clear", "Onda %s repelida!", "Wave %s repelled!")
    tr(f"announce.{NS}.cities.chief", "O Chefe de Guerra da Horda chegou!", "The Horde Warchief has arrived!")
    tr(f"toast.{NS}.cities.siege_won", "Cerco repelido!", "Siege repelled!")
    tr(f"banner.{NS}.cities.siege_won", "%s está salva! O Reino agradece.", "%s is saved! The Kingdom thanks you.")
    tr(f"toast.{NS}.cities.lord.honored", "Presente do Lorde", "The Lord's Gift")
    tr(f"toast.{NS}.cities.lord.honored.sub", "Honrado no Reino", "Honored with the Kingdom")
    tr(f"toast.{NS}.cities.lord.honored.sub.sahra", "Honrado no Império de Sahra", "Honored with the Empire of Sahra")
    tr(f"toast.{NS}.cities.lord.honored.sub.nordheim", "Honrado em Nordheim", "Honored with Nordheim")
    tr(f"toast.{NS}.cities.lord.exalted", "Herói de Aldória!", "Hero of Aldoria!")
    tr(f"toast.{NS}.cities.lord.exalted.sahra", "Escudo do Sol de Sahra!", "Shield of the Sun of Sahra!")
    tr(f"toast.{NS}.cities.lord.exalted.nordheim", "Herói de Nordheim!", "Hero of Nordheim!")

    # ---------------- sabor dos reinos (balões soltos e do governante)
    flavor = {
        "aldoria": ([("Pela Coroa de Aldória!", "For the Crown of Aldoria!"),
                     ("Os campos dourados de Aldória alimentam o reino inteiro.", "Aldoria's golden fields feed the whole realm."),
                     ("Dizem que o rei de Aldória já foi aventureiro.", "They say Aldoria's king was once an adventurer."),
                     ("O sino da capital toca ao meio-dia. Dá para ouvir de longe!", "The capital's bell rings at noon. You can hear it for miles!"),
                     ("Azul e dourado: as cores de Aldória.", "Blue and gold: the colors of Aldoria."),
                     ("Mercadores de Sahra trazem especiarias até aqui. Caras, mas boas!", "Sahra's merchants bring spices all the way here. Pricey, but good!")],
                    [("A Coroa de Aldória protege quem a serve.", "The Crown of Aldoria protects those who serve it."),
                     ("Nordheim e Sahra brigam por rotas de comércio. Aldória fica de fora... por enquanto.", "Nordheim and Sahra squabble over trade routes. Aldoria stays out of it... for now."),
                     ("Que os campos de Aldória continuem dourados.", "May Aldoria's fields stay golden.")]),
        "sahra": ([("Que o sol de Sahra ilumine seu caminho.", "May the sun of Sahra light your way."),
                   ("As caravanas chegaram do oásis! Tâmaras fresquinhas!", "The caravans arrived from the oasis! Fresh dates!"),
                   ("A areia guarda segredos antigos... e basiliscos.", "The sand keeps ancient secrets... and basilisks."),
                   ("Água é ouro no deserto, viajante.", "Water is gold in the desert, traveler."),
                   ("O vento simum vem aí. Cubra o rosto!", "The simoom wind is coming. Cover your face!"),
                   ("Pelo Sol e pelo Império!", "For the Sun and the Empire!")],
                  [("O Império de Sahra mede o tempo pelas dunas, não pelos reis.", "The Empire of Sahra measures time by dunes, not by kings."),
                   ("Minhas caravanas precisam de escolta. A Guilda paga bem.", "My caravans need escorts. The Guild pays well."),
                   ("Nordheim manda ferro; nós mandamos ouro. Ainda assim, eles reclamam.", "Nordheim sends iron; we send gold. Still, they complain.")]),
        "nordheim": ([("Pelo martelo de Nordheim!", "By the hammer of Nordheim!"),
                      ("O inverno chega cedo nas montanhas.", "Winter comes early in the mountains."),
                      ("Hidromel quente espanta o frio. Quer um gole?", "Warm mead chases off the cold. Want a sip?"),
                      ("Os lobos uivaram a noite inteira.", "The wolves howled all night."),
                      ("Viu as luzes no céu ontem? Os antigos dizem que são os heróis.", "Did you see the lights in the sky last night? The elders say they're the heroes."),
                      ("Machado afiado, lareira acesa. É tudo de que preciso.", "A sharp axe and a lit hearth. That's all I need.")],
                     [("Nordheim não se curva ao frio nem aos monstros.", "Nordheim bows neither to the cold nor to monsters."),
                      ("Os skalds cantam os nomes de quem defende o norte.", "The skalds sing the names of those who defend the north."),
                      ("Sahra quer nosso ferro barato. Que venham buscar na neve!", "Sahra wants our iron cheap. Let them come fetch it in the snow!")]),
    }
    for kingdom, (amb, lord) in flavor.items():
        lines(f"kingdom.{kingdom}.ambient", amb)
        lines(f"kingdom.{kingdom}.lord", lord)

    # ---------------- facções dos reinos (v0.9): nome, descrição, vantagens por patamar e títulos
    factions = {
        "kingdom_sahra": (
            ("Império de Sahra", "Empire of Sahra"),
            ("O império do deserto: cidades de arenito, caravanas, oásis e o Sultão na capital Sahra. Defenda as cidades e não cometa crimes.",
             "The desert empire: sandstone cities, caravans, oases and the Sultan in the capital Sahra. Defend the cities and commit no crimes."),
            {"enemy": ("Os janízaros caçam você em todo o Império e ninguém negocia.", "Janissaries hunt you across the Empire and no one trades with you."),
             "hostile": ("Os guardas de Sahra atacam você. Mercadores do Império não negociam.", "Sahra's guards attack you. The Empire's merchants won't trade."),
             "unfriendly": ("Preços +25% com mercadores de Sahra. Os guardas ficam de olho.", "Prices +25% with Sahra's merchants. The guards keep watch."),
             "neutral": ("Preços normais nas cidades do Império.", "Normal prices in the Empire's cities."),
             "friendly": ("Preços −5% nas cidades de Sahra.", "Prices −5% in Sahra's cities."),
             "honored": ("Preços −10%. O Sultão tem um presente para você.", "Prices −10%. The Sultan has a gift for you."),
             "revered": ("Preços −20%. As caravanas saúdam você nas estradas.", "Prices −20%. Caravans hail you on the roads."),
             "exalted": ("Preços −30%. Fale com o Sultão para ser o Escudo do Sol.", "Prices −30%. Speak to the Sultan to become the Shield of the Sun.")},
            {"friendly": ("Amigo das Caravanas", "Friend of the Caravans"), "honored": ("Guardião do Oásis", "Warden of the Oasis"),
             "revered": ("Emir das Dunas", "Emir of the Dunes"), "exalted": ("Escudo do Sol de Sahra", "Shield of the Sun of Sahra")}),
        "kingdom_nordheim": (
            ("Reino de Nordheim", "Kingdom of Nordheim"),
            ("O reino das neves: fortalezas de pedra e madeira, salões de hidromel, lobos e o Jarl na capital Nordheim. Defenda as cidades e não cometa crimes.",
             "The kingdom of snow: stone-and-timber holds, mead halls, wolves and the Jarl in the capital Nordheim. Defend the cities and commit no crimes."),
            {"enemy": ("A guarda de Nordheim caça você e nenhum salão abre a porta.", "Nordheim's guard hunts you and no hall opens its door."),
             "hostile": ("Os guardas de Nordheim atacam você. Mercadores do norte não negociam.", "Nordheim's guards attack you. Northern merchants won't trade."),
             "unfriendly": ("Preços +25% com mercadores de Nordheim. Os guardas desconfiam.", "Prices +25% with Nordheim's merchants. The guards are wary."),
             "neutral": ("Preços normais nas cidades do norte.", "Normal prices in the northern cities."),
             "friendly": ("Preços −5% nas cidades de Nordheim.", "Prices −5% in Nordheim's cities."),
             "honored": ("Preços −10%. O Jarl tem um presente para você.", "Prices −10%. The Jarl has a gift for you."),
             "revered": ("Preços −20%. Os skalds começam a cantar seu nome.", "Prices −20%. The skalds begin to sing your name."),
             "exalted": ("Preços −30%. Fale com o Jarl para ser o Herói de Nordheim.", "Prices −30%. Speak to the Jarl to become the Hero of Nordheim.")},
            {"friendly": ("Amigo do Norte", "Friend of the North"), "honored": ("Escudeiro do Jarl", "Shieldbearer of the Jarl"),
             "revered": ("Campeão dos Salões", "Champion of the Halls"), "exalted": ("Herói de Nordheim", "Hero of Nordheim")}),
    }
    for fid, (name, desc, perks, titles) in factions.items():
        tr(f"faction.{NS}.{fid}", *name)
        tr(f"faction.{NS}.{fid}.desc", *desc)
        for st, (pt, en) in perks.items():
            tr(f"faction.{NS}.{fid}.perk.{st}", pt, en)
        for st, (pt, en) in titles.items():
            tr(f"reputation.{NS}.title.{fid}.{st}", pt, en)
    for k, pt, en in (("trade", "Comércio", "Trade"), ("service", "Serviço", "Service"), ("hurt", "Feriu um morador", "Hurt a resident"),
                      ("theft", "Visto mexendo num baú", "Seen rummaging a chest"), ("help_guard", "Ajudou a guarda", "Helped the guard"),
                      ("siege", "Defendeu %s", "Defended %s")):
        tr(f"reason.{NS}.cities.{k}", pt, en)

    # ---------------- comandos
    cmds = {
        "list_line": ("%1$s §7[%2$s] §f%3$s %4$s %5$s §7· população %6$s (repor: %7$s) · cercos %8$s/%9$s",
                      "%1$s §7[%2$s] §f%3$s %4$s %5$s §7· population %6$s (to refill: %7$s) · sieges %8$s/%9$s"),
        "list_empty": ("Nenhuma cidade conhecida neste mundo ainda (o coração da cidade registra ao carregar).",
                       "No known cities in this world yet (the city heart registers when loaded)."),
        "bad_type": ("Tipo inválido. Use: %s", "Invalid type. Use: %s"),
        "bad_profession": ("Profissão inválida.", "Invalid profession."),
        "spawned": ("%s chegou.", "%s arrived."),
        "not_in_city": ("Você não está dentro de uma cidade (ou o coração dela não está carregado).", "You're not inside a city (or its heart isn't loaded)."),
        "siege_started": ("O Cerco começou!", "The siege has begun!"),
        "siege_running": ("Já há um cerco nesta cidade.", "There's already a siege in this city."),
        "siege_stopped": ("Cerco encerrado.", "Siege ended."),
        "wanted_cleared": ("\"Procurado\" removido de %s jogador(es).", "\"Wanted\" removed from %s player(s)."),
        "no_structure": ("Estrutura %s não encontrada.", "Structure %s not found."),
        "place_failed": ("Não foi possível gerar a cidade aqui.", "Couldn't generate the city here."),
        "placed": ("%s gerada perto de X %s, Z %s. O coração dela registra o lugar ao carregar.", "%s generated near X %s, Z %s. Its heart registers the place when loaded."),
        "a_city": ("Uma cidade", "A city"),
        "info.head": ("%1$s — %2$s §7[%3$s]", "%1$s — %2$s §7[%3$s]"),
        "info.stone": ("Pedra de Teleporte: %s %s %s (registrada na Viagem)", "Waystone: %s %s %s (registered for Travel)"),
        "info.no_stone": ("Pedra de Teleporte: nenhuma achada ainda (a varredura procura na praça).", "Waystone: none found yet (the sweep looks in the plaza)."),
        "info.altar": ("Altar de Ascensão público: %s %s %s", "Public Ascension Altar: %s %s %s"),
        "info.no_altar": ("Altar de Ascensão público: nenhum achado ainda.", "Public Ascension Altar: none found yet."),
        "info.swept": ("Varredura completa: %s monstro(s) de outras estruturas removido(s).", "Sweep complete: %s monster(s) from other structures removed."),
        "info.sweeping": ("Varredura em andamento (chunks carregando): %s removido(s) até agora.", "Sweep in progress (chunks loading): %s removed so far."),
        "info.wanted": ("Você é procurado por: %s", "You are wanted by: %s"),
    }
    for k, (pt, en) in cmds.items():
        tr(f"command.{NS}.cities.{k}", pt, en)


def translations_v10():
    """v1.0: abordagem da guarda (saque, vandalismo, furto), persuasão/propina/devolver e guerra de cidades."""
    lines("guard.enemy", [("Inimigo de %s! Às armas!", "Enemy of %s! To arms!"),
                          ("Você não é bem-vindo aqui. Saque a arma ou dê meia-volta!", "You're not welcome here. Draw or turn back!"),
                          ("A guerra chegou ao portão. Não vai passar!", "The war has reached our gate. You shall not pass!")])
    lines("citizen.enemy", [("É um deles! Corram!", "It's one of them! Run!"),
                            ("Fique longe! %s está em guerra com a sua gente!", "Stay away! %s is at war with your people!"),
                            ("Guardas! O inimigo está na cidade!", "Guards! The enemy is in the city!")])
    lines("merchant.enemy", [("A banca está fechada. Para você, para sempre.", "The stall is closed. For you, forever."),
                             ("Não vendo nada a quem marcha contra nós.", "I sell nothing to those who march against us.")])
    lines("citizen.call_guard", [("Guardas! Ladrão! Ladrão!", "Guards! Thief! Thief!"),
                                 ("Ei! Isso não é seu! GUARDAAAS!", "Hey! That's not yours! GUAAARDS!"),
                                 ("Alguém chame a guarda, estão saqueando!", "Someone call the guard, they're looting!")])
    lines("confront.halt", [("Ei, você! Parado aí!", "Hey, you! Stop right there!"),
                            ("Alto lá, %s! Eu vi isso!", "Halt, %s! I saw that!"),
                            ("Não se mexa! Quero uma palavrinha com você.", "Don't move! I want a word with you."),
                            ("Pare em nome da lei!", "Stop in the name of the law!")])
    lines("confront.accuse.chest", [("Esse baú pertence à cidade. O que pensa que estava fazendo?", "That chest belongs to the city. What do you think you were doing?"),
                                    ("Eu vi você remexendo nas coisas dos outros. Explique-se.", "I saw you rummaging through other people's things. Explain yourself."),
                                    ("Saqueando a despensa do povo à luz do dia? Comece a falar.", "Looting the people's pantry in broad daylight? Start talking.")])
    lines("confront.accuse.block", [("Está desmontando as casas da cidade?! Isso é vandalismo!", "Are you tearing down the city's houses?! That's vandalism!"),
                                    ("Essas paredes têm dono. Por que estava quebrando tudo?", "These walls have owners. Why were you breaking everything?"),
                                    ("Picareta na casa alheia? Fala logo o que deu em você.", "A pickaxe on someone else's home? Out with it, what got into you?")])
    lines("confront.accuse.theft", [("Eu vi sua mão no bolso daquela pessoa. Ladrão!", "I saw your hand in that person's pocket. Thief!"),
                                    ("Mãos leves, hein? Não na minha ronda.", "Light fingers, eh? Not on my watch."),
                                    ("Furto em plena rua. Acha que a guarda é cega?", "Pickpocketing in the open street. Think the guard is blind?")])
    lines("confront.persuade_ok", [("Hm... pensando bem, deve ter sido engano. Circulando.", "Hm... come to think of it, must've been a misunderstanding. Move along."),
                                   ("Está bem, está bem. Acredito em você. Desta vez.", "Fine, fine. I believe you. This time."),
                                   ("Faz sentido. Desculpe o susto, %s. Bom dia.", "That makes sense. Sorry for the scare, %s. Good day.")])
    lines("confront.persuade_fail", [("Não me venha com histórias. Eu sei o que vi.", "Don't give me stories. I know what I saw."),
                                     ("Essa desculpa é velha até para um bêbado da taverna.", "That excuse is old even for a tavern drunk."),
                                     ("Mentir para a guarda só piora a sua situação.", "Lying to the guard only makes it worse.")])
    lines("confront.bribe_ok", [("...Eu não vi nada. E você nunca esteve aqui.", "...I saw nothing. And you were never here."),
                                ("Moedas falam mais alto que baús. Suma da minha frente.", "Coins speak louder than chests. Get out of my sight."),
                                ("Hm. A ronda está tranquila hoje. Muito tranquila.", "Hm. The patrol is quiet today. Very quiet.")])
    lines("confront.bribe_refuse", [("Está tentando me SUBORNAR?! Guarde isso antes que eu mude de ideia!", "Are you trying to BRIBE me?! Put that away before I change my mind!"),
                                    ("Eu jurei pela minha cidade. Seu ouro não compra isso.", "I swore an oath to my city. Your gold can't buy that."),
                                    ("Suborno é outro crime, sabia? Estou anotando.", "Bribery is another crime, you know? I'm writing it down.")])
    lines("confront.return_ok", [("Tudo de volta no lugar. Que não se repita.", "Everything back in its place. Don't let it happen again."),
                                 ("Honestidade tardia ainda é honestidade. Vá em paz.", "Late honesty is still honesty. Go in peace.")])
    lines("confront.return_clean", [("Bolsos limpos, nada faltando... Hm. Fique longe dos baús.", "Clean pockets, nothing missing... Hm. Stay away from the chests."),
                                    ("Nada sumiu. Desculpe, mas olho comprido em baú chama atenção.", "Nothing's gone. Sorry, but eyeing chests draws attention.")])
    lines("confront.flee", [("Volte aqui! Pega ladrão!", "Come back here! Stop, thief!"),
                            ("Fugindo, é? Agora você é Procurado!", "Running, are we? Now you're Wanted!"),
                            ("Guardas! Não deixem escapar!", "Guards! Don't let them get away!")])
    lines("confront.fight", [("Então vai ser do jeito difícil!", "So it's the hard way, then!"),
                             ("Resistindo à guarda? Erro seu!", "Resisting the guard? Your mistake!"),
                             ("Pela cidade! Às armas!", "For the city! To arms!")])
    lines("confront.impatient", [("Chega de silêncio! Você vem comigo!", "Enough silence! You're coming with me!"),
                                 ("Minha paciência acabou. Guardas!", "My patience is over. Guards!")])
    lines("confront.repeat", [("DE NOVO você?! Acabou a conversa!", "YOU again?! This conversation is over!"),
                              ("Eu avisei, %s. Reincidente vai para a cela!", "I warned you, %s. Repeat offenders go to the cell!"),
                              ("De novo hoje! Sem desculpas, sem moedas: em nome da lei!", "Again today! No excuses, no coins: in the name of the law!")])
    lines("confront.waiting", [("Estou esperando uma resposta, %s.", "I'm waiting for an answer, %s."),
                               ("Não me dê as costas! Ainda estamos conversando.", "Don't turn your back on me! We're still talking."),
                               ("E então? Fale logo antes que eu perca a paciência.", "Well? Speak up before I lose my patience.")])
    tr(f"dialogue.{NS}.cities.confront.no_coins", "Sem moedas? Então nem tente.", "No coins? Then don't even try.")
    tr(f"dialogue.{NS}.cities.confront.return_missing", "Devolver o quê? Ainda faltam: %s.", "Return what? Still missing: %s.")

    # tela de diálogo (modo abordagem)
    screen = {
        "at_war": ("Em guerra", "At war"),
        "confront.tag": ("⚠ Abordagem", "⚠ Confronted"),
        "confront.persuade": ("Persuadir (%s%%)", "Persuade (%s%%)"),
        "confront.persuade.desc": ("Convencer o guarda de que foi engano. Só uma tentativa; falhar tira 2 de reputação.",
                                   "Convince the guard it was a mistake. One try only; failing costs 2 reputation."),
        "confront.persuade.used": ("Você já tentou. Ele não vai cair de novo.", "You already tried. He won't fall for it again."),
        "confront.bonus.base": ("Base: %s", "Base: %s"),
        "confront.bonus.standing": ("Reputação (%s): %s", "Reputation (%s): %s"),
        "confront.bonus.class.rogue": ("Lábia de Ladino: %s", "Rogue's silver tongue: %s"),
        "confront.bonus.class.gambler": ("Cara de blefe do Apostador: %s", "Gambler's poker face: %s"),
        "confront.bonus.class.enchanter": ("Charme do Encantador: %s", "Enchanter's charm: %s"),
        "confront.bonus.class.knight": ("Palavra de Cavaleiro: %s", "A Knight's word: %s"),
        "confront.bonus.race.human": ("Humano, um dos nossos: %s", "Human, one of us: %s"),
        "confront.bonus.race.elf": ("Fala mansa élfica: %s", "Elven soft speech: %s"),
        "confront.bonus.race.dwarf": ("Irmão das montanhas (Nordheim): %s", "Mountain kin (Nordheim): %s"),
        "confront.bonus.race.dragonborn": ("Sangue de dragão, respeitado em Sahra: %s", "Dragon blood, respected in Sahra: %s"),
        "confront.bonus.race.half_orc": ("Cara de poucos amigos: %s", "Not a friendly face: %s"),
        "confront.bonus.race.demon": ("Desconfiam de demônios: %s", "They distrust demons: %s"),
        "confront.bonus.citizen": ("Morador desta cidade: %s", "Citizen of this city: %s"),
        "confront.bonus.allied": ("Aliado do reino: %s", "Ally of the realm: %s"),
        "confront.bonus.strikes": ("Reincidência: %s", "Repeat offense: %s"),
        "confront.bonus.crime": ("Gravidade do crime: %s", "Severity of the crime: %s"),
        "confront.bribe": ("Pagar propina", "Pay a bribe"),
        "confront.bribe.desc": ("Moedas fecham olhos... quase sempre. Guardas honestos se ofendem (−3 de reputação).",
                                "Coins close eyes... most of the time. Honest guards take offense (−3 reputation)."),
        "confront.bribe.refused": ("Este guarda é honesto. Nem tente de novo.", "This guard is honest. Don't try again."),
        "confront.return": ("Devolver o que pegou", "Give back what you took"),
        "confront.return.clean": ("Mostrar que não pegou nada", "Show you took nothing"),
        "confront.return.desc": ("Devolve %s e o guarda conserta %s bloco(s). Metade da reputação volta e a marca some.",
                                 "Returns %s and the guard repairs %s block(s). Half the reputation comes back and the mark is cleared."),
        "confront.return.clean_desc": ("Nada sumiu: você mostra os bolsos, a reputação volta toda e a marca some.",
                                       "Nothing is missing: you show your pockets, all reputation comes back and the mark is cleared."),
        "confront.return.missing": ("Você não tem tudo para devolver. Faltam: %s", "You don't have everything to give back. Missing: %s"),
        "confront.flee": ("Fugir", "Run"),
        "confront.flee.desc": ("Procurado por %s min neste reino. Você ganha alguns segundos de vantagem.",
                               "Wanted for %s min in this realm. You get a few seconds' head start."),
        "confront.fight": ("Brigar", "Fight"),
        "confront.fight.desc": ("Procurado por %s min neste reino e os guardas atacam. Fiança com qualquer guarda do reino.",
                                "Wanted for %s min in this realm and the guards attack. Bail with any guard of the realm."),
        "confront.note.chest": ("Visto mexendo num baú da cidade.", "Seen meddling with a city chest."),
        "confront.note.block": ("Visto quebrando um prédio da cidade.", "Seen breaking a city building."),
        "confront.note.theft": ("Visto furtando um morador.", "Seen pickpocketing a resident."),
        "confront.note.rep": ("(−%s de reputação)", "(−%s reputation)"),
        "confront.note.strikes": ("Marcas de reincidência: %s/%s (na 3ª: Procurado sem conversa).",
                                  "Repeat-offense marks: %s/%s (on the 3rd: Wanted, no talking)."),
        "confront.note.last": ("Marca %s/%s: ÚLTIMO AVISO, a próxima vira caso de cela.",
                               "Mark %s/%s: FINAL WARNING, the next one means the cell."),
        "confront.item": ("%s× %s", "%s× %s"),
        "confront.more": (" e mais %s", " and %s more"),
        "confront.nothing": ("nada", "nothing"),
    }
    for k, (pt, en) in screen.items():
        tr(f"screen.{NS}.cities.{k}", pt, en)

    msgs = {
        "confront.seen": ("%s viu você! Espere o guarda e explique-se, ou fuja (e vire Procurado).",
                          "%s saw you! Wait for the guard and explain yourself, or run (and become Wanted)."),
        "confront.repeat": ("Reincidente! Os guardas não querem conversa: você é Procurado.", "Repeat offender! The guards won't talk: you are Wanted."),
        "confront.lost": ("A guarda perdeu você de vista.", "The guard lost sight of you."),
        "confront.flee": ("Você fugiu da guarda: agora é Procurado neste reino!", "You fled the guard: you are now Wanted in this realm!"),
        "confront.fight": ("Você resistiu à guarda: agora é Procurado neste reino!", "You resisted the guard: you are now Wanted in this realm!"),
        "confront.impatient": ("Você não respondeu ao guarda: agora é Procurado neste reino!", "You didn't answer the guard: you are now Wanted in this realm!"),
    }
    for k, (pt, en) in msgs.items():
        tr(f"message.{NS}.cities.{k}", pt, en)

    reasons = {
        "vandalism": ("vandalismo na cidade", "vandalism in the city"),
        "repeat": ("crime reincidente", "repeat offense"),
        "persuaded": ("convenceu a guarda", "talked the guard down"),
        "lied": ("mentiu para a guarda", "lied to the guard"),
        "bribe_attempt": ("tentou subornar um guarda honesto", "tried to bribe an honest guard"),
        "bribe": ("propina paga", "bribe paid"),
        "innocent": ("não pegou nada", "took nothing"),
        "returned": ("devolveu o que pegou", "gave back what was taken"),
        "resisted": ("resistiu à guarda", "resisted the guard"),
        "fled": ("fugiu da guarda", "fled the guard"),
    }
    for k, (pt, en) in reasons.items():
        tr(f"reason.{NS}.cities.{k}", pt, en)

    tr(f"objective.{NS}.cities.confront", "Abordado por %s", "Confronted by %s")
    tr(f"objective.{NS}.cities.confront.wait", "Espere o guarda chegar (%ss); fugir = Procurado", "Wait for the guard (%ss); running = Wanted")
    tr(f"objective.{NS}.cities.confront.talk", "Responda ao guarda: clique nele (%ss)", "Answer the guard: click them (%ss)")

def check_counts():
    """As quantidades de falas precisam bater com CityLines.COUNTS (o Java sorteia pelo número)."""
    java = open(os.path.join(ROOT, "src/main/java/com/renanpontes/isekaimahou/feature/cities/CityLines.java"), encoding="utf-8").read()
    import re
    for group, n in re.findall(r'Map\.entry\("([a-z_.]+)", (\d+)\)', java):
        for i in range(int(n)):
            k = f"dialogue.{NS}.cities.{group}.{i}"
            assert k in PT and k in EN, f"falta a fala {k}"
        assert f"dialogue.{NS}.cities.{group}.{n}" not in PT, f"fala sobrando em {group} (o Java conta {n})"


def main():
    geckolib_files()
    heart_block()
    eggs()
    effect_icons()
    faction_emblems()
    translations()
    translations_v10()
    check_counts()
    write_json(os.path.join(LANG, "pt_br.json"), dict(sorted(PT.items())))
    write_json(os.path.join(LANG, "en_us.json"), dict(sorted(EN.items())))
    print(f"cities: {len(PT)} traduções, {len(CITIZENS)} cidadãos, {len(PROFESSIONS) * 2} mercadores")


if __name__ == "__main__":
    main()
