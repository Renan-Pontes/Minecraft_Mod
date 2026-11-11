"""
Assets do núcleo de classes v1.0 (trilha A1a): a Lição (technique_lesson) em branco e escrita.
Uso: py -I tools/gen/core_classes.py  (a partir da raiz do projeto)
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, darken, lighten  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
TEX = os.path.join(ASSETS, "textures", "item")
MODELS = os.path.join(ASSETS, "models", "item")

PAPER = hex_rgba("efe2c0")
PAPER_SHADE = hex_rgba("d6c39a")
PAPER_DARK = hex_rgba("a88f62")
OUTLINE = hex_rgba("4a3520")
RIBBON = hex_rgba("b8322e")
RIBBON_DARK = hex_rgba("7a1c1a")
INK = hex_rgba("2c2440")
GOLD = hex_rgba("f2c14e")
GOLD_DARK = hex_rgba("a8781e")
SEAL = hex_rgba("7a4fd0")


def book(written):
    """Caderno de lições aberto em leque: capa de couro, páginas, fita marcadora; escrito = linhas de tinta + selo."""
    c = Canvas(16, 16)
    # Capa (couro) atrás das páginas
    leather = hex_rgba("6b3f22")
    c.rect(2, 3, 13, 14, darken(leather, 0.15))
    c.rect(3, 3, 13, 13, leather)
    c.line(3, 13, 13, 13, darken(leather, 0.35))
    # Páginas
    c.rect(3, 2, 12, 12, PAPER)
    c.rect(11, 2, 12, 12, PAPER_SHADE)
    c.line(3, 12, 12, 12, PAPER_DARK)
    c.line(4, 2, 12, 2, lighten(PAPER, 0.4))
    # Lombada dourada
    c.rect(2, 2, 2, 13, GOLD_DARK)
    c.set(2, 2, GOLD)
    c.set(2, 7, GOLD)
    c.set(2, 12, GOLD)
    if written:
        # Linhas de tinta (texto) e um glifo desenhado
        for y, x1 in ((4, 8), (6, 8), (8, 10), (10, 7)):
            c.line(4, y, x1, y, INK)
        # Glifo (losango) no canto da página
        c.set(10, 3, SEAL)
        c.set(9, 4, SEAL)
        c.set(11, 4, SEAL)
        c.set(10, 5, SEAL)
        c.set(10, 4, lighten(SEAL, 0.5))
        # Selo de cera
        c.circle(10, 11, 1, RIBBON)
        c.set(10, 11, lighten(RIBBON, 0.35))
        c.set(11, 12, RIBBON_DARK)
    else:
        # Linhas-guia apagadas
        for y in (4, 6, 8, 10):
            c.line(5, y, 10, y, PAPER_SHADE)
    # Fita marcadora
    c.line(7, 12, 7, 15, RIBBON)
    c.set(6, 15, RIBBON_DARK)
    c.set(8, 15, RIBBON_DARK)
    c.outline(OUTLINE)
    return c


def model(name, texture, overrides=None):
    data = {"parent": "minecraft:item/generated", "textures": {"layer0": "isekai_mahou:item/" + texture}}
    if overrides:
        data["overrides"] = overrides
    with open(os.path.join(MODELS, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    book(False).save(os.path.join(TEX, "technique_lesson.png"))
    book(True).save(os.path.join(TEX, "technique_lesson_written.png"))
    model("technique_lesson_written", "technique_lesson_written")
    model("technique_lesson", "technique_lesson",
          [{"predicate": {"isekai_mahou:written": 1}, "model": "isekai_mahou:item/technique_lesson_written"}])
    print("ok")


if __name__ == "__main__":
    main()
