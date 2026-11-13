"""Onda 7 (sky_island) — arte e dados do NÚCLEO CELESTE (item `sky_core`), o custo de fundar numa Ilha Celeste (SkyLand).

Uso (na raiz do projeto):  py -I tools/gen/settlements_w7_sky_island.py

Gera SÓ arquivos novos deste trabalho (nada dos outros geradores é reescrito; NÃO roda o icons.py):
  - assets/isekai_mahou/textures/item/sky_core.png   (16x16: cristal de céu pousado numa nuvem)
  - assets/isekai_mahou/models/item/sky_core.json
  - data/isekai_mahou/recipe/sky_core.json            (meio de jogo: coisas "do céu" + mana do mod)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, item_model  # noqa: E402

ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
DATA = os.path.join(ROOT, "src/main/resources/data/isekai_mahou")

# o = contorno, a/b/c = azul do fundo para a luz, w = brilho, k/s = nuvem e a sombra dela
PAL = {
    "o": hex_rgba("1B3A5C"),
    "a": hex_rgba("2F6FB8"),
    "b": hex_rgba("4FB2E8"),
    "c": hex_rgba("9FE3FF"),
    "w": hex_rgba("FFFFFF"),
    "k": hex_rgba("EEF6FC"),
    "s": hex_rgba("B4C8DC"),
}

ROWS = [
    "............w...",
    ".......oo.......",
    "......ocbo...w..",
    ".....ocwbao.....",
    "....ocwcbbao....",
    "...ocwccbbbao...",
    "..ocwcccbbbaao..",
    "..oabcccbbaaao..",
    "...oabccbbaao...",
    "....oabcbaao....",
    ".kk..oabbao..kk.",
    "kkkk..oaao..kkkk",
    "skkkkk.oo.kkkkks",
    ".sskkkkkkkkkkss.",
    "...ssskkkksss...",
    "................",
]


def texture():
    assert len(ROWS) == 16 and all(len(r) == 16 for r in ROWS), "o desenho tem de ser 16x16"
    c = Canvas(16, 16)
    c.from_rows(ROWS, PAL)
    c.save(os.path.join(ASSETS, "textures/item/sky_core.png"))


def recipe():
    # onda 9 ("fundar mais fácil"): 2 penas + 2 fragmentos de ametista em volta de um Cristal de Mana — começo de jogo
    # (antes: membranas de phantom e Essência do Vento). O Alquimista da cidade inicial também vende.
    rec = {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" F ", "AMA", " F "],
        "key": {
            "A": {"item": "minecraft:amethyst_shard"},
            "M": {"item": "isekai_mahou:mana_crystal"},
            "F": {"item": "minecraft:feather"},
        },
        "result": {"id": "isekai_mahou:sky_core", "count": 1},
    }
    path = os.path.join(DATA, "recipe/sky_core.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(rec, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    texture()
    item_model(ASSETS, "sky_core")
    recipe()
    print("sky_core: textura, modelo e receita gravados")
