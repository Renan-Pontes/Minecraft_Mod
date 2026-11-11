"""
Gera os assets do modulo afterlife (Beira da Morte / Corpos).
Uso (na raiz do projeto):  py -I tools/gen/afterlife.py

Saidas:
  - textures/entity/afterlife_reaper.png       (64x64) Ceifador: capuz, manto rasgado, bracos e foice
  - textures/entity/afterlife_reaper_eyes.png  (64x64) olhos brilhantes (camada emissiva)
  - shaders/post/afterlife_spirit.json         tela dessaturada da alma (color_convolve do vanilla)
O mapa UV segue client/ReaperModel.java (mesmos texOffs).
"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba, lighten, darken  # noqa: E402

A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")

ROBE = hex_rgba("1c1a24")
ROBE_HI = hex_rgba("2c2838")
ROBE_LO = hex_rgba("100e16")
VOID = hex_rgba("050407")
WOOD = hex_rgba("4a3b2e")
WOOD_LO = hex_rgba("2e241b")
STEEL = hex_rgba("8c96a3")
STEEL_HI = hex_rgba("d6e0ea")
STEEL_LO = hex_rgba("4e5560")
BONE = hex_rgba("c9c2b0")
CLEAR = (0, 0, 0, 0)


def box(c, u, v, w, h, d, side, top, front=None):
    """Pinta um cubo do mapa UV de caixa do Minecraft (texOffs u,v; tamanho w,h,d)."""
    c.rect(u + d, v, u + d + w - 1, v + d - 1, top)                  # cima
    c.rect(u + d + w, v, u + d + 2 * w - 1, v + d - 1, darken(top, 0.3))  # baixo
    c.rect(u, v + d, u + 2 * d + 2 * w - 1, v + d + h - 1, side)      # laterais
    if front is not None:
        c.rect(u + d, v + d, u + d + w - 1, v + d + h - 1, front)


def folds(c, x0, y0, x1, y1, seed):
    """Dobras verticais do manto (faixas claras/escuras)."""
    for x in range(x0, x1 + 1):
        k = (x * 7 + seed) % 5
        col = ROBE_HI if k == 0 else (ROBE_LO if k == 3 else None)
        if col is None:
            continue
        for y in range(y0, y1 + 1):
            c.set(x, y, col)


def reaper():
    c = Canvas(64, 64)
    # capuz 9x9x9 em (0,0): frente (9,9)-(17,17) com a abertura escura
    box(c, 0, 0, 9, 9, 9, ROBE, ROBE_HI)
    folds(c, 0, 9, 35, 17, 1)
    c.rect(9, 9, 17, 17, ROBE)
    c.rect(10, 10, 16, 17, VOID)          # abertura do capuz
    c.rect(11, 10, 15, 10, ROBE_LO)
    c.set(10, 10, ROBE)
    c.set(16, 10, ROBE)
    c.rect(12, 15, 14, 16, hex_rgba("15121c"))   # sombra do queixo
    # corpo 9x12x5 em (0,18)
    box(c, 0, 18, 9, 12, 5, ROBE, ROBE_HI)
    folds(c, 0, 23, 27, 34, 3)
    c.rect(9, 23, 9, 34, ROBE_LO)          # costura frontal
    c.rect(5, 23, 13, 24, ROBE_HI)         # gola
    # saia 11x10x7 em (0,35): rasgada embaixo
    box(c, 0, 35, 11, 10, 7, ROBE, ROBE_HI)
    folds(c, 0, 42, 35, 51, 5)
    for x in range(0, 36):
        cut = (x * 5 + 3) % 4
        for y in range(52 - cut, 52):
            c.set(x, y, CLEAR)
    # bracos 3x12x3 em (36,18): mangas largas + mão ossuda
    box(c, 36, 18, 3, 12, 3, ROBE, ROBE_HI)
    folds(c, 36, 21, 47, 32, 2)
    c.rect(36, 30, 47, 32, BONE)
    c.rect(36, 32, 47, 32, darken(BONE, 0.3))
    c.rect(42, 18, 44, 20, BONE)           # palma (base do cubo)
    # cabo 1x26x1 em (48,0)
    box(c, 48, 0, 1, 26, 1, WOOD, lighten(WOOD, 0.2))
    for y in range(1, 27, 3):
        c.rect(48, y, 51, y, WOOD_LO)
    # lâmina 13x2x1 em (0,54) e ponta 3x2x1 em (30,54)
    box(c, 0, 54, 13, 2, 1, STEEL, STEEL_HI)
    c.rect(0, 55, 27, 55, STEEL)
    c.rect(0, 56, 27, 56, STEEL_LO)
    c.rect(1, 55, 13, 55, STEEL_HI)        # fio
    box(c, 30, 54, 3, 2, 1, STEEL, STEEL_HI)
    c.rect(30, 56, 37, 56, STEEL_LO)
    c.noise(0.06, seed=7)
    c.save(os.path.join(A, "textures", "entity", "afterlife_reaper.png"))


def reaper_eyes():
    c = Canvas(64, 64)
    glow = hex_rgba("bffcff")
    core = hex_rgba("ffffff")
    for x0 in (11, 14):
        c.rect(x0, 13, x0 + 1, 13, glow)
        c.set(x0 + (0 if x0 == 11 else 1), 13, core)
        c.set(x0, 14, hex_rgba("5fe6e6", 160))
        c.set(x0 + 1, 14, hex_rgba("5fe6e6", 160))
    c.save(os.path.join(A, "textures", "entity", "afterlife_reaper_eyes.png"))


def spirit_shader():
    data = {
        "targets": ["swap"],
        "passes": [
            {
                "name": "color_convolve",
                "intarget": "minecraft:main",
                "outtarget": "swap",
                "uniforms": [
                    {"name": "RedMatrix", "values": [1.0, 0.0, 0.0]},
                    {"name": "GreenMatrix", "values": [0.0, 1.0, 0.0]},
                    {"name": "BlueMatrix", "values": [0.0, 0.0, 1.0]},
                    {"name": "ColorScale", "values": [0.86, 0.95, 1.08]},
                    {"name": "Saturation", "values": [0.15]}
                ]
            },
            {"name": "blit", "intarget": "swap", "outtarget": "minecraft:main"}
        ]
    }
    p = os.path.join(A, "shaders", "post", "afterlife_spirit.json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4)


if __name__ == "__main__":
    os.makedirs(os.path.join(A, "textures", "entity"), exist_ok=True)
    reaper()
    reaper_eyes()
    spirit_shader()
    print("afterlife: assets gerados")
