"""Gera as texturas/modelos/dados do NUCLEO (itens, icones de efeito, tipo de dano)."""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixelart import Canvas, hex_rgba, lighten, darken, item_model

root = sys.argv[1] if len(sys.argv) > 1 else "."
A = os.path.join(root, "src/main/resources/assets/isekai_mahou")
D = os.path.join(root, "src/main/resources/data/isekai_mahou")

ELEMENTS = {"arcane": "B98CFF", "fire": "FF6A2B", "water": "3FA9FF", "earth": "A0743C",
            "wind": "9FF0D0", "light": "FFE27A", "shadow": "8A4FC8"}

# Essencias: orbe brilhante com nucleo claro
for el, col in ELEMENTS.items():
    c = Canvas(16, 16)
    base = hex_rgba(col)
    c.circle(8, 8.5, 5.2, darken(base, 0.15))
    c.circle(8, 8.5, 4.2, base)
    c.shade_sphere(8, 8.5, 5.2, light=0.6, dark=0.5)
    c.circle(6.6, 7, 1.3, lighten(base, 0.85))
    c.set(11, 3, lighten(base, 0.7)); c.set(3, 12, lighten(base, 0.5)); c.set(12, 13, lighten(base, 0.6))
    c.outline(darken(base, 0.6))
    c.save(os.path.join(A, "textures/item/essence_" + el + ".png"))
    item_model(A, "essence_" + el)

# Nucleos dos Guardioes: gema facetada grande
for el, col in ELEMENTS.items():
    if el == "arcane":
        continue
    c = Canvas(16, 16)
    base = hex_rgba(col)
    rows = [
        "......aaaa......",
        "....aabbbbaa....",
        "...abbccccbba...",
        "..abccddddccba..",
        ".abccddeeddccba.",
        ".abcddeeeeddcba.",
        "abccdeeffeedccba",
        "abcddeffffeddcba",
        "abcddeffffeddcba",
        "abccdeeffeedccba",
        ".abcddeeeeddcba.",
        ".abccddeeddccba.",
        "..abccddddccba..",
        "...abbccccbba...",
        "....aabbbbaa....",
        "......aaaa......",
    ]
    pal = {"a": darken(base, 0.65), "b": darken(base, 0.35), "c": darken(base, 0.1), "d": base,
           "e": lighten(base, 0.35), "f": lighten(base, 0.8)}
    c.from_rows(rows, pal)
    c.save(os.path.join(A, "textures/item/guardian_core_" + el + ".png"))
    item_model(A, "guardian_core_" + el)

# Cristal de mana
c = Canvas(16, 16)
rows = [
    "................",
    ".......a........",
    "......aba.......",
    "......abba......",
    ".....abcba......",
    ".....abccba.....",
    "....abcccba.....",
    "....abccdcba....",
    "...abccddcba....",
    "...abccddccba...",
    "...abcccdccba...",
    "....abcccccba...",
    "....aabcccbaa...",
    "......abbba.....",
    ".......aaa......",
    "................",
]
pal = {"a": hex_rgba("3A1E7A"), "b": hex_rgba("6A3FD0"), "c": hex_rgba("9B6BFF"), "d": hex_rgba("E2D2FF")}
c.from_rows(rows, pal)
c.save(os.path.join(A, "textures/item/mana_crystal.png"))
item_model(A, "mana_crystal")


def scroll(name, ribbon):
    c = Canvas(16, 16)
    rows = [
        "................",
        "..oooooooooooo..",
        ".oqqqqqqqqqqqqo.",
        "..oppppppppppo..",
        "..opttttttttpo..",
        "..oppppppppppo..",
        "..opttttttttpo..",
        "..oppppppppppo..",
        "..opttttttppppo.",
        "..oppppppprrpo..",
        "..opttttprRRro..",
        "..oppppprRRRro..",
        "..opppppprRrpo..",
        ".oqqqqqqqqrqqqo.",
        "..oooooooooooo..",
        "................",
    ]
    rows = [r[:16].ljust(16, ".") for r in rows]
    pal = {"o": hex_rgba("5b3d1c"), "q": hex_rgba("c4a86a"), "p": hex_rgba("ead9a8"), "t": hex_rgba("8a7650"),
           "R": hex_rgba(ribbon), "r": darken(hex_rgba(ribbon), 0.45)}
    c.from_rows(rows, pal)
    c.save(os.path.join(A, "textures/item/" + name + ".png"))
    item_model(A, name)


scroll("glyph_scroll", "7A3FD0")
scroll("spell_scroll", "C4162F")

# Icones de efeitos 18x18
EFFECTS = {"soaked": "3FA9FF", "frozen": "A8E8FF", "stunned": "FFF27A", "rooted": "6B8E23",
           "corrupted": "5A1E7A", "exposed": "FFD86B", "magic_exhaustion": "555577", "silenced": "9A9AB0"}
for eff, col in EFFECTS.items():
    c = Canvas(18, 18)
    base = hex_rgba(col)
    c.circle(9, 9, 7.5, darken(base, 0.2))
    c.circle(9, 9, 6.2, base)
    c.shade_sphere(9, 9, 7.5, light=0.5, dark=0.5)
    c.outline(darken(base, 0.7))
    c.save(os.path.join(A, "textures/mob_effect/" + eff + ".png"))

# Tipo de dano das magias
os.makedirs(os.path.join(D, "damage_type"), exist_ok=True)
json.dump({"message_id": "isekai_mahou.spell", "scaling": "when_caused_by_living_non_player", "exhaustion": 0.1},
          open(os.path.join(D, "damage_type", "spell.json"), "w"), indent=2)
print("core assets ok")
