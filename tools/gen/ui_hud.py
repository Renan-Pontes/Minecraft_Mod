"""
Gera a folha de sprites do HUD (modulo ui_hud):
  assets/isekai_mahou/textures/gui/ui_hud_sprites.png  (64x64)

Sprites em tons de cinza sao TINGIDOS no Java (GuiGraphics.setColor) com a cor do elemento/magia.
Coordenadas (x, y, w, h) — manter em sincronia com HudDraw.java:
  ORB_S   (0, 0, 7, 7)     esfera pequena (roda elemental, nos)
  ORB_L   (8, 0, 9, 9)     esfera grande (elemento ativo)
  RING    (18, 0, 13, 13)  anel de destaque do elemento ativo
  SNEAK   (0, 16, 7, 7)    versao agachado
  SPRINT  (8, 16, 7, 7)    versao correndo
  AIR     (16, 16, 7, 7)   versao no ar
  WATER   (24, 16, 7, 7)   versao na agua
  LOCK    (32, 16, 7, 7)   cadeado (slot G antes do Circulo 4)
  STAR    (40, 16, 7, 7)   brilho (ultimate pronta)
  CRYSTAL (48, 16, 7, 9)   cristal de mana (colorido, nao tingido)
  NODE_ON (0, 32, 5, 5)    no do caminho alcancado
  NODE_OFF(6, 32, 5, 5)    no do caminho nao alcancado
  WARN    (12, 32, 5, 7)   exclamacao (magia invalida)
  -- v0.7: Olho Analitico, barras de vida e banners --
  HEART   (0, 40, 7, 7)    coracao (vida)
  SHIELD  (8, 40, 7, 7)    escudo (armadura)
  EYE     (16, 40, 9, 7)   olho (Olho Analitico)
  CROWN   (26, 40, 9, 7)   coroa (selo de Chefe)
  ELITE   (36, 40, 7, 7)   estrela de pontas (selo de Elite)
  UP      (44, 40, 5, 5)   seta para cima (fraqueza: leva mais dano)
  DOWN    (50, 40, 5, 5)   seta para baixo (resistencia)
  IMMUNE  (56, 40, 5, 5)   circulo cortado (imune)
  TABLET  (0, 48, 7, 7)    tabuleta runica (glifos por magia)
  SKULL   (8, 48, 7, 7)    caveira (alvo derrotado)
  -- v0.8: lugares, faccoes, condicoes --
  PIN     (16, 48, 7, 7)   marcador de mapa (lugar atual)
  WANTED  (24, 48, 7, 7)   mira (selo Procurado)
  BANNER  (32, 48, 7, 7)   estandarte (faccao sem emblema)
  HOURGLASS (40, 48, 5, 7) ampulheta (tempo restante)
Rodar:  py -I tools/gen/ui_hud.py
"""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas  # noqa: E402

OUT = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/gui/ui_hud_sprites.png")

c = Canvas(64, 64)


def grey(v, a=255):
    return (v, v, v, a)


def orb(ox, oy, size):
    """Esfera tipo gema em cinza: contorno escuro, corpo sombreado, brilho no canto superior esquerdo."""
    r = size / 2.0
    cx, cy = r, r
    lx, ly = cx - r * 0.38, cy - r * 0.40  # fonte de luz
    for y in range(size):
        for x in range(size):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if d > r:
                continue
            if d > r - 1.0:
                c.set(ox + x, oy + y, grey(70))
                continue
            t = min(1.0, math.hypot(px - lx, py - ly) / (r * 1.25))
            v = int(250 - 120 * t)
            # brilho de borda inferior (reflexo) deixa a gema com volume
            if py - cy > r * 0.35 and abs(px - cx) < r * 0.45:
                v = min(255, v + 25)
            c.set(ox + x, oy + y, grey(v))
    # ponto de brilho
    hx, hy = int(lx), int(ly)
    c.set(ox + hx, oy + hy, grey(255))


def ring(ox, oy, size):
    r = size / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - r, y + 0.5 - r)
            if r - 1.15 <= d <= r:
                c.set(ox + x, oy + y, grey(255))
            elif r - 2.0 <= d < r - 1.15:
                c.set(ox + x, oy + y, grey(255, 90))
    # brilhos nos pontos cardeais
    m = size // 2
    for (x, y) in ((m, 0), (m, size - 1), (0, m), (size - 1, m)):
        c.set(ox + x, oy + y, grey(255))


def rows(ox, oy, pattern, palette):
    c.from_rows(pattern, palette, ox, oy)


W = {"#": grey(255), "+": grey(190), "-": grey(255, 120)}

orb(0, 0, 7)
orb(8, 0, 9)
ring(18, 0, 13)

# --- versoes de movimento (brancas, tingidas no Java) ---
rows(0, 16, [
    "...#...",
    "...#...",
    ".#.#.#.",
    "..###..",
    "...#...",
    ".......",
    "#######",
], W)  # agachado: seta para baixo + chao
rows(8, 16, [
    "#..#...",
    ".#..#..",
    "..#..#.",
    "...#..#",
    "..#..#.",
    ".#..#..",
    "#..#...",
], W)  # correndo: >>
rows(16, 16, [
    "...#...",
    "..#+#..",
    ".#...#.",
    "...#...",
    "..#+#..",
    ".#...#.",
    "-.....-",
], W)  # no ar: salto (setas para cima)
rows(24, 16, [
    "...#...",
    "...#...",
    "..###..",
    ".##+##.",
    ".#+####",
    ".######",
    "..###..",
], W)  # na agua: gota
rows(32, 16, [
    "..###..",
    ".#...#.",
    ".#...#.",
    "#######",
    "###+###",
    "###+###",
    "#######",
], W)  # cadeado
rows(40, 16, [
    "...#...",
    "...#...",
    "..#+#..",
    "##+++##",
    "..#+#..",
    "...#...",
    "...#...",
], W)  # brilho

# --- cristal de mana (colorido) ---
P = {
    "o": (40, 18, 80, 255),
    "H": (245, 235, 255, 255),
    "L": (200, 170, 255, 255),
    "b": (150, 110, 240, 255),
    "d": (95, 55, 190, 255),
}
rows(48, 16, [
    "...o...",
    "..oHo..",
    ".oHLbo.",
    "oHLbbdo",
    "oLbbbdo",
    "obbbddo",
    ".obddo.",
    "..odo..",
    "...o...",
], P)

# --- nos dos caminhos ---
rows(0, 32, [
    "..#..",
    ".#+#.",
    "#+++#",
    ".#+#.",
    "..#..",
], W)
rows(6, 32, [
    "..#..",
    ".#.#.",
    "#...#",
    ".#.#.",
    "..#..",
], W)
rows(12, 32, [
    ".###.",
    ".###.",
    ".###.",
    "..#..",
    "..#..",
    ".....",
    "..#..",
], W)

# --- v0.7: Olho Analitico / barras de vida / banners (cinza, tingidos no Java) ---
S = {"H": grey(255), "#": grey(220), "d": grey(160), "o": grey(95), "-": grey(255, 110)}
rows(0, 40, [
    ".##.##.",
    "#H####d",
    "#H####d",
    "######d",
    ".####d.",
    "..##d..",
    "...d...",
], S)  # coracao
rows(8, 40, [
    "HHHHHHd",
    "HH##ddd",
    "HH##ddd",
    "HH##ddd",
    ".HH#dd.",
    "..H#d..",
    "...d...",
], S)  # escudo
rows(16, 40, [
    "...HHH...",
    ".HH---HH.",
    "H--###--H",
    "H-##o##-H",
    "H--###--H",
    ".HH---HH.",
    "...HHH...",
], S)  # olho
rows(26, 40, [
    "H...H...H",
    "#H.###.H#",
    "##H###H##",
    "#########",
    "#o#d#o#d#",
    "#########",
    "ddddddddd",
], S)  # coroa
rows(36, 40, [
    "...H...",
    "...H...",
    "HHH##dd",
    ".H###d.",
    "..H#d..",
    ".H#.#d.",
    ".H...d.",
], S)  # estrela de elite
rows(44, 40, [
    "..H..",
    ".HHH.",
    "HHHHH",
    ".HHH.",
    ".HHH.",
], S)  # seta para cima
rows(50, 40, [
    ".HHH.",
    ".HHH.",
    "HHHHH",
    ".HHH.",
    "..H..",
], S)  # seta para baixo
rows(56, 40, [
    ".HHH.",
    "H..HH",
    "H.H.H",
    "HH..H",
    ".HHH.",
], S)  # imune (circulo cortado)
rows(0, 48, [
    ".ooooo.",
    "o#####o",
    "o#H#H#o",
    "o##H##o",
    "o#H#H#o",
    "o#####o",
    ".ooooo.",
], S)  # tabuleta runica
rows(8, 48, [
    ".#####.",
    "#H#####",
    "#o##o##",
    "#######",
    ".#d#d#.",
    ".#.#.#.",
    ".......",
], S)  # caveira

# --- v0.8 ---
rows(16, 48, [
    ".HHHHd.",
    "HH##ddd",
    "H#oo#dd",
    "H#oo#dd",
    ".H##dd.",
    "..H#d..",
    "...d...",
], S)  # marcador de mapa
rows(24, 48, [
    "...H...",
    ".HH#dd.",
    ".H...d.",
    "HH.H.dd",
    ".H...d.",
    ".Hd#dd.",
    "...d...",
], S)  # mira (procurado)
rows(32, 48, [
    "HHHHHHd",
    "H####dd",
    "H#HH#dd",
    "H####dd",
    "H##.#dd",
    "H#...dd",
    "H......",
], S)  # estandarte
rows(40, 48, [
    "HHHHd",
    ".H#d.",
    "..H..",
    "..#..",
    ".H.d.",
    ".###.",
    "HHHHd",
], S)  # ampulheta

c.save(OUT)
print("ok:", OUT)
