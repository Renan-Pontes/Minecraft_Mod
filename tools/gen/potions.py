"""
Assets do módulo potions (v1.0): 36 frascos (vida/mana × beber/arremessável/prolongada × 6 tiers), ícones dos efeitos
Fluxo Vital / Fluxo Arcano, modelos, traduções, receitas (bancada + Caldeirão da Bruxa), tags e o loot modifier global.

Uso:  py -I tools/gen/potions.py   (na raiz do projeto)
"""
import json
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, mix, lighten, darken, item_model  # noqa: E402

RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "isekai_mahou")
DATA = os.path.join(RES, "data", "isekai_mahou")
LANG = os.path.join(ROOT, "src", "main", "lang", "potions")
MERGE = os.path.join(ROOT, "src", "main", "merge", "potions")
NS = "isekai_mahou"

TIERS = ["lesser", "common", "greater", "superior", "supreme", "legendary"]
KINDS = ["health", "mana"]
FORMS = ["", "splash_", "long_"]

# cores do líquido (iguais às de PotionTier.java)
LIQUID = {
    "health": ["#F06A7A", "#E8384F", "#D41E3C", "#B5122F", "#FF2E63", "#FF4A3D"],
    "mana": ["#6FB6FF", "#4A8CFF", "#3C63F0", "#5A3CF0", "#8A3CFF", "#B05CFF"],
}
GLASS = (205, 228, 242, 120)
GLASS_RIM = (176, 198, 214, 255)
OUTLINE = (38, 36, 56, 255)
CORK = hex_rgba("#8B5E3C")
CORK_HI = hex_rgba("#B07B4F")
GOLD = hex_rgba("#F2C23A")
GOLD_DK = hex_rgba("#B5841C")
WHITE = (255, 255, 255, 255)


def item_id(form, kind, tier):
    return f"{form}{kind}_potion_{tier}"


# ---------------------------------------------------------------------------------------------- formas dos frascos
def shape(tier):
    """Devolve (body, neck, rim, cap, cap_gold) como conjuntos de pixels (x, y)."""
    body, neck, rim, cap = set(), set(), set(), set()
    gold_cap = tier in ("supreme", "legendary")

    def circle(cx, cy, r, ymin):
        for y in range(16):
            for x in range(16):
                if y >= ymin and (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    body.add((x, y))

    if tier == "lesser":            # frasquinho fino
        cap |= {(x, y) for x in (7, 8) for y in (1, 2)}
        rim |= {(x, 3) for x in range(6, 10)}
        neck |= {(x, y) for x in (7, 8) for y in (4, 5)}
        body |= {(x, y) for x in range(6, 10) for y in range(6, 14)}
        body |= {(7, 14), (8, 14)}
    elif tier == "common":          # frasco redondo
        cap |= {(x, y) for x in (7, 8) for y in (1, 2)}
        rim |= {(x, 3) for x in range(6, 10)}
        neck |= {(x, y) for x in (7, 8) for y in (4, 5)}
        circle(8.0, 10.5, 4.6, 6)
    elif tier == "greater":         # redondo grande
        cap |= {(x, y) for x in (7, 8) for y in (0, 1)}
        rim |= {(x, 2) for x in range(6, 10)}
        neck |= {(x, y) for x in (7, 8) for y in (3, 4)}
        circle(8.0, 10.2, 5.6, 5)
    elif tier == "superior":        # erlenmeyer
        cap |= {(x, y) for x in (7, 8) for y in (0, 1)}
        rim |= {(x, 2) for x in range(6, 10)}
        neck |= {(x, y) for x in (7, 8) for y in range(3, 7)}
        for y in range(7, 15):
            half = 1.0 + (y - 7) * 0.72
            for x in range(16):
                if abs(x + 0.5 - 8) <= half:
                    body.add((x, y))
    elif tier == "supreme":         # frasco lapidado (losango)
        cap |= {(x, y) for x in (7, 8) for y in (0, 1)}
        rim |= {(x, 2) for x in range(6, 10)}
        neck |= {(x, y) for x in (7, 8) for y in (3, 4)}
        for y in range(5, 16):
            for x in range(16):
                if abs(x + 0.5 - 8) / 6.4 + abs(y + 0.5 - 10.3) / 5.4 <= 1.0:
                    body.add((x, y))
    else:                           # legendary: gota ornamentada com coroa
        cap |= {(x, 1) for x in range(6, 10)} | {(6, 0), (9, 0)}
        rim |= {(x, 2) for x in range(6, 10)}
        neck |= {(x, y) for x in (7, 8) for y in (3, 4)}
        circle(8.0, 10.6, 5.0, 7)
        body |= {(7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6)}
    return body, neck, rim, cap, gold_cap


def draw_bottle(kind, tier_index, form):
    tier = TIERS[tier_index]
    liquid = hex_rgba(LIQUID[kind][tier_index])
    body, neck, rim, cap, gold_cap = shape(tier)
    c = Canvas(16, 16)
    ys = [y for _, y in body]
    top, bottom = min(ys), max(ys)
    fill = top + max(1, int((bottom - top) * 0.3))

    for (x, y) in body:
        row = [px for (px, py) in body if py == y]
        lo, hi = min(row), max(row)
        if y < fill:
            col = GLASS
        else:
            t = (x - lo) / max(1, hi - lo)
            col = lighten(liquid, 0.25 * (1 - t)) if t < 0.45 else darken(liquid, 0.35 * (t - 0.45))
            col = darken(col, 0.18 * (y - fill) / max(1, bottom - fill))
            if y == fill:
                col = lighten(liquid, 0.45)
        c.set(x, y, col)
    # brilho do vidro (coluna da esquerda)
    for y in range(top + 1, bottom - 1):
        row = [px for (px, py) in body if py == y]
        if len(row) >= 4:
            x = min(row) + 1
            c.set(x, y, mix(c.get(x, y), (255, 255, 255, 255), 0.55))
    # bolhas no líquido
    for (bx, by) in ((9, bottom - 2), (10, bottom - 4)):
        if (bx, by) in body and by > fill:
            c.set(bx, by, lighten(liquid, 0.38))
    for p in neck:
        c.set(*p, GLASS)
    for p in rim:
        c.set(*p, GOLD if gold_cap else GLASS_RIM)
    for (x, y) in cap:
        if gold_cap:
            c.set(x, y, GOLD if y == min(py for _, py in cap) else GOLD_DK)
        else:
            c.set(x, y, CORK_HI if y == min(py for _, py in cap) else CORK)
    if tier == "legendary":         # gema na coroa
        c.set(7, 1, lighten(liquid, 0.3))
        c.set(8, 1, liquid)
    if tier == "supreme":           # cinta dourada na parte mais larga
        row = sorted(px for (px, py) in body if py == 10)
        for x in (row[0], row[1], row[-2], row[-1]):
            c.set(x, 10, GOLD)

    if form == "splash_":           # alça de corda para arremessar
        rope = hex_rgba("#D9B07A")
        rope_dk = hex_rgba("#9C7444")
        ny = min(py for _, py in neck)
        c.set(6, ny, rope)
        c.set(9, ny, rope_dk)
        for (x, y) in ((10, ny - 1), (11, ny - 2), (11, ny - 3), (10, ny - 4), (9, ny - 4)):
            c.set(x, y, rope if y % 2 else rope_dk)

    c.outline(OUTLINE)

    if tier == "legendary":         # filigrana dourada nas laterais
        for y in (8, 12):
            row = [px for (px, py) in body if py == y]
            if row:
                c.set(min(row) - 1, y, GOLD)
                c.set(max(row) + 1, y, GOLD)
        for (sx, sy) in ((2, 4), (13, 3), (14, 13), (1, 12)):
            star = (255, 248, 200, 255) if kind == "health" else (200, 245, 255, 255)
            c.set(sx, sy, star)
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if c.get(sx + ox, sy + oy)[3] == 0:
                    c.set(sx + ox, sy + oy, (star[0], star[1], star[2], 140))

    if form == "long_":             # ampulheta: efeito prolongado
        sand = hex_rgba("#F4D27A")
        frame = hex_rgba("#6B4A2B")
        hg = ["fffff",
              ".sss.",
              "..s..",
              ".wsw.",
              ".sss.",
              "fffff"]
        ox, oy = 11, 10
        cells = set()
        for y, row in enumerate(hg):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                cells.add((ox + x, oy + y))
                col = frame if ch == "f" else sand if ch == "s" else (250, 245, 230, 255)
                c.set(ox + x, oy + y, col)
        for (x, y) in list(cells):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, y + dy)
                if n not in cells and 0 <= n[0] < 16 and 0 <= n[1] < 16:
                    c.set(n[0], n[1], OUTLINE)
    return c


# ---------------------------------------------------------------------------------------------- ícones dos efeitos
def effect_icons():
    out = os.path.join(ASSETS, "textures", "mob_effect")
    # Fluxo Vital: coração com redemoinho claro
    c = Canvas(18, 18)
    heart = [
        "..xxx....xxx..",
        ".xxxxx..xxxxx.",
        "xxxxxxxxxxxxxx",
        "xxxxxxxxxxxxxx",
        "xxxxxxxxxxxxxx",
        ".xxxxxxxxxxxx.",
        "..xxxxxxxxxx..",
        "...xxxxxxxx...",
        "....xxxxxx....",
        ".....xxxx.....",
        "......xx......",
    ]
    c.from_rows(heart, {"x": hex_rgba("#E8384F")}, 2, 3)
    c.shade_sphere(8, 7, 8, light=0.5, dark=0.4)
    for (x, y) in ((5, 6), (6, 5), (7, 5), (8, 6), (9, 7), (10, 7), (11, 6)):
        c.set(x, y, (255, 220, 225, 255))
    for (x, y) in ((6, 9), (7, 10), (8, 10), (9, 9), (10, 10), (11, 10)):
        c.set(x, y, (255, 180, 190, 255))
    c.outline((90, 14, 28, 255))
    c.save(os.path.join(out, "vital_flow.png"))

    # Fluxo Arcano: gota azul-violeta com faísca
    c = Canvas(18, 18)
    for y in range(18):
        for x in range(18):
            dx, dy = x + 0.5 - 9, y + 0.5 - 11
            if dy >= 0 and dx * dx + dy * dy <= 20:
                c.set(x, y, hex_rgba("#5A7BFF"))
            elif -9 <= dy < 0 and abs(dx) <= 4.4 * (1 + dy / 9):
                c.set(x, y, hex_rgba("#5A7BFF"))
    c.shade_sphere(9, 11, 5.5, light=0.55, dark=0.45)
    for (x, y) in ((7, 10), (7, 11), (8, 12)):
        c.set(x, y, (215, 230, 255, 255))
    c.outline((26, 22, 90, 255))
    for (x, y, a) in ((14, 3, 255), (13, 3, 150), (15, 3, 150), (14, 2, 150), (14, 4, 150), (3, 6, 200)):
        c.set(x, y, (200, 240, 255, a))
    c.save(os.path.join(out, "arcane_flow.png"))


# ---------------------------------------------------------------------------------------------- traduções
PT_TIER_F = {"lesser": "Menor", "common": "Comum", "greater": "Maior", "superior": "Superior", "supreme": "Suprema"}
EN_TIER = {"lesser": "Lesser", "common": "Common", "greater": "Greater", "superior": "Superior", "supreme": "Supreme"}
PT_KIND = {"health": "Vida", "mana": "Mana"}
EN_KIND = {"health": "Health", "mana": "Mana"}


def names(form, kind, tier):
    if tier == "legendary":
        pt = f"Elixir Lendário de {PT_KIND[kind]}"
        pt += {"": "", "splash_": " Arremessável", "long_": " Prolongado"}[form]
        en = f"Legendary {EN_KIND[kind]} Elixir"
    else:
        pt = f"Poção de {PT_KIND[kind]} {PT_TIER_F[tier]}"
        pt += {"": "", "splash_": " Arremessável", "long_": " Prolongada"}[form]
        en = f"{EN_TIER[tier]} {EN_KIND[kind]} Potion"
    en = {"": "", "splash_": "Splash ", "long_": "Prolonged "}[form] + en
    return pt, en


def lang():
    pt, en = {}, {}
    for kind in KINDS:
        for tier in TIERS:
            for form in FORMS:
                i = item_id(form, kind, tier)
                pt[f"item.{NS}.{i}"], en[f"item.{NS}.{i}"] = names(form, kind, tier)
    extra = {
        "effect.isekai_mahou.vital_flow": ("Fluxo Vital", "Vital Flow"),
        "effect.isekai_mahou.arcane_flow": ("Fluxo Arcano", "Arcane Flow"),
        "entity.isekai_mahou.thrown_mahou_potion": ("Frasco Arremessado", "Thrown Flask"),
        "tooltip.isekai_mahou.potions.heal": ("Cura na hora: %s + %s%% da vida máxima",
                                              "Instant heal: %s + %s%% of max health"),
        "tooltip.isekai_mahou.potions.heal_pct": ("Cura na hora: %s%% da vida máxima",
                                                  "Instant heal: %s%% of max health"),
        "tooltip.isekai_mahou.potions.restore": ("Restaura na hora: %s%% do seu %s",
                                                 "Instantly restores %s%% of your %s"),
        "tooltip.isekai_mahou.potions.resource_any": ("recurso (Mana/Vigor/Foco)", "resource (Mana/Vigor/Focus)"),
        "tooltip.isekai_mahou.potions.flow_health": ("%s: +%s de vida por segundo por %ss",
                                                     "%s: +%s health per second for %ss"),
        "tooltip.isekai_mahou.potions.flow_mana": ("%s: +%s%% do recurso por segundo por %ss",
                                                   "%s: +%s%% resource per second for %ss"),
        "tooltip.isekai_mahou.potions.cleanse_health": ("Purifica: remove todos os efeitos negativos",
                                                        "Purifies: removes every harmful effect"),
        "tooltip.isekai_mahou.potions.cleanse_mana": ("Purifica: remove Exaustão Mágica e Silêncio",
                                                      "Purifies: removes Magic Exhaustion and Silence"),
        "tooltip.isekai_mahou.potions.splash_health": ("Arremessável: cura aliados num raio de %s blocos (nunca monstros)",
                                                       "Splash: heals allies within %s blocks (never monsters)"),
        "tooltip.isekai_mahou.potions.splash_mana": ("Arremessável: restaura os jogadores num raio de %s blocos",
                                                     "Splash: restores players within %s blocks"),
        "tooltip.isekai_mahou.potions.long": ("Prolongada: 40%% na hora, fluxo 3× mais longo",
                                              "Prolonged: 40%% instantly, flow lasts 3× longer"),
        "tooltip.isekai_mahou.potions.cooldown": ("Recarga compartilhada entre as poções: %ss",
                                                  "Cooldown shared by all potions: %ss"),
        "message.isekai_mahou.potions.full_health": ("Sua vida já está cheia.", "Your health is already full."),
        "message.isekai_mahou.potions.full_resource": ("Seu recurso já está no máximo.", "Your resource is already full."),
    }
    for k, (p, e) in extra.items():
        pt[k], en[k] = p, e
    os.makedirs(LANG, exist_ok=True)
    for name, m in (("pt_br", pt), ("en_us", en)):
        with open(os.path.join(LANG, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(m, f, ensure_ascii=False, indent=2, sort_keys=True)


# ---------------------------------------------------------------------------------------------- dados
def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def ing(x):
    return {"tag": x[1:]} if x.startswith("#") else {"item": x}


def shapeless(name, items, result, count=1):
    write(os.path.join(DATA, "recipe", f"potions_{name}.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "group": "isekai_mahou_potions",
        "ingredients": [ing(i) for i in items], "result": {"id": f"{NS}:{result}", "count": count}})


def shaped(name, pattern, key, result, count=1):
    write(os.path.join(DATA, "recipe", f"potions_{name}.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "group": "isekai_mahou_potions",
        "pattern": pattern, "key": {k: ing(v) for k, v in key.items()}, "result": {"id": f"{NS}:{result}", "count": count}})


def recipes():
    m = lambda i: f"{NS}:{i}"  # noqa: E731
    # bancada — tiers baixos (sem precisar do Nether)
    shapeless("health_lesser", ["minecraft:glass_bottle", "minecraft:apple", "minecraft:sweet_berries"], "health_potion_lesser")
    shapeless("mana_lesser", ["minecraft:glass_bottle", "minecraft:lapis_lazuli", "minecraft:sugar"], "mana_potion_lesser")
    shapeless("health_common", [m("health_potion_lesser"), "minecraft:glistering_melon_slice"], "health_potion_common")
    shapeless("mana_common", [m("mana_potion_lesser"), "minecraft:amethyst_shard"], "mana_potion_common")
    shapeless("health_greater", [m("health_potion_common"), m("essence_light"), "minecraft:golden_carrot"], "health_potion_greater")
    shapeless("mana_greater", [m("mana_potion_common"), m("essence_arcane"), "minecraft:glow_berries"], "mana_potion_greater")
    shapeless("health_superior", [m("health_potion_greater"), m("essence_light"), m("essence_light"), "minecraft:golden_apple"],
              "health_potion_superior")
    shapeless("mana_superior", [m("mana_potion_greater"), m("essence_arcane"), m("essence_arcane"), m("mana_crystal")],
              "mana_potion_superior")
    # bancada — tiers altos com essências e itens de chefe
    shaped("health_supreme", ["E E", "GPG", "ETE"],
           {"E": m("essence_light"), "G": "minecraft:golden_apple", "P": m("health_potion_superior"),
            "T": f"#{NS}:potions_boss_trophies"}, "health_potion_supreme")
    shaped("mana_supreme", ["E E", "GPG", "ETE"],
           {"E": m("essence_arcane"), "G": m("mana_crystal"), "P": m("mana_potion_superior"),
            "T": f"#{NS}:potions_boss_trophies"}, "mana_potion_supreme")
    shaped("health_legendary", ["EKE", "TPT", "EGE"],
           {"E": m("essence_light"), "K": f"#{NS}:potions_legendary_catalysts", "T": f"#{NS}:potions_boss_trophies",
            "P": m("health_potion_supreme"), "G": "minecraft:golden_apple"}, "health_potion_legendary")
    shaped("mana_legendary", ["EKE", "TPT", "EGE"],
           {"E": m("essence_arcane"), "K": f"#{NS}:potions_legendary_catalysts", "T": f"#{NS}:potions_boss_trophies",
            "P": m("mana_potion_supreme"), "G": m("mana_crystal")}, "mana_potion_legendary")
    # formas: + pólvora = arremessável; + redstone = prolongada (todas as combinações)
    for kind in KINDS:
        for tier in TIERS:
            base = m(item_id("", kind, tier))
            shapeless(f"splash_{kind}_{tier}", [base, "minecraft:gunpowder"], item_id("splash_", kind, tier))
            shapeless(f"long_{kind}_{tier}", [base, "minecraft:redstone"], item_id("long_", kind, tier))

    # Caldeirão da Bruxa (lotes; ingredientes NA ORDEM)
    witch = {
        "potions_health_common": dict(ingredients=["minecraft:sweet_berries", "minecraft:glistering_melon_slice", "minecraft:apple"],
                                      stirs=2, heat="low", result={"id": m("health_potion_common"), "count": 3},
                                      xp=6, color="#E8384F", element="light"),
        "potions_mana_common": dict(ingredients=["minecraft:lapis_lazuli", "minecraft:sugar", "minecraft:amethyst_shard"],
                                    stirs=2, heat="low", result={"id": m("mana_potion_common"), "count": 3},
                                    xp=6, color="#4A8CFF", element="water"),
        "potions_health_greater": dict(ingredients=[m("essence_light"), "minecraft:glistering_melon_slice", "minecraft:golden_carrot"],
                                       stirs=3, heat="medium", result={"id": m("health_potion_greater"), "count": 3},
                                       xp=10, color="#D41E3C", element="light"),
        "potions_mana_greater": dict(ingredients=[m("essence_arcane"), "minecraft:lapis_lazuli", "minecraft:glow_berries"],
                                     stirs=3, heat="medium", result={"id": m("mana_potion_greater"), "count": 3},
                                     xp=10, color="#3C63F0", element="water"),
        "potions_health_superior": dict(ingredients=[m("essence_light"), "minecraft:golden_apple", "minecraft:ghast_tear", m("essence_light")],
                                        stirs=4, heat="high", moon_phases=[0, 1, 7],
                                        result={"id": m("health_potion_superior"), "count": 2},
                                        xp=16, color="#B5122F", element="light"),
        "potions_mana_superior": dict(ingredients=[m("essence_arcane"), m("mana_crystal"), "minecraft:glow_berries", m("essence_arcane")],
                                      stirs=4, heat="high", moon_phases=[3, 4, 5],
                                      result={"id": m("mana_potion_superior"), "count": 2},
                                      xp=16, color="#5A3CF0", element="water"),
    }
    for name, obj in witch.items():
        write(os.path.join(DATA, "witch_recipes", name + ".json"), obj)

    # tags de ingredientes de chefe (entradas de outros módulos são opcionais)
    opt = lambda i: {"id": i, "required": False}  # noqa: E731
    write(os.path.join(DATA, "tags", "item", "potions_boss_trophies.json"), {"replace": False, "values": [
        "minecraft:nether_star", "minecraft:dragon_breath",
        *[opt(f"{NS}:guardian_core_{e}") for e in ("fire", "water", "earth", "wind", "light", "shadow")],
        opt(f"{NS}:false_hydra_heart"), opt(f"{NS}:manticore_stinger"), opt(f"{NS}:arrancar_soul_shard"),
        opt(f"{NS}:twilight_heart"), opt(f"{NS}:primordial_dragon_heart"),
    ]})
    write(os.path.join(DATA, "tags", "item", "potions_legendary_catalysts.json"), {"replace": False, "values": [
        "minecraft:nether_star", "minecraft:enchanted_golden_apple",
        opt(f"{NS}:twilight_heart"), opt(f"{NS}:primordial_dragon_heart"),
    ]})

    # loot modifier global (baús + chefes)
    write(os.path.join(DATA, "loot_modifiers", "potions_loot.json"), {
        "type": f"{NS}:potions_loot", "conditions": [],
        "mod_chest_chance": 0.45, "other_chest_chance": 0.2, "boss_drops": 2})
    write(os.path.join(MERGE, "data", "neoforge", "loot_modifiers", "global_loot_modifiers.json"),
          {"replace": False, "entries": [f"{NS}:potions_loot"]})


def textures():
    for kind in KINDS:
        for ti, tier in enumerate(TIERS):
            for form in FORMS:
                i = item_id(form, kind, tier)
                draw_bottle(kind, ti, form).save(os.path.join(ASSETS, "textures", "item", i + ".png"))
                item_model(ASSETS, i)
    effect_icons()


if __name__ == "__main__":
    textures()
    lang()
    recipes()
    print("potions: assets gerados")
