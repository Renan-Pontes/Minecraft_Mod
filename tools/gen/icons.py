"""
Isekai Mahou - gerador dos ICONES de interface (pixel art procedural, puro Python, sem PIL).

Gera (pasta assets/isekai_mahou/textures/gui/):
  glyph/<id>.png        32x32  um por glifo. Moldura pelo TIPO, gradiente na cor do ELEMENTO e um pictograma unico.
                               Moldura: Forma = anel runico (circulo) | Efeito = gema (losango lapidado)
                                        Aumento = placa quadrada chanfrada | Gatilho = triangulo
                                        Ligacao (link_*) = elo horizontal com setas duplas | Lei = hexagono
                               Glifos LENDARIOS ganham uma estrelinha dourada no canto superior direito.
                               Ja COLORIDOS (desenhar sem tingir). Origens usam a cor da classe: Mago azul-violeta,
                               Bruxa verde-pantano, Bruxo vermelho-escuro, Druida verde-folha, Necromante cinza-osso.
                               Nenhum icone se repete: o script confere (hash) e avisa "ATENCAO" se dois ficarem iguais.
  element/<id>.png      16x16  emblema de cada um dos 22 elementos (COLORIDO; fusoes = aro dividido nas cores dos pais).
  glyph_kind/<k>.png    16x16  form|effect|augment|trigger|link|law. Desenhados em BRANCO/CINZA-CLARO (tons de cinza
                               com relevo) para serem TINGIDOS no Java com g.setColor(r, g, b, a) antes do blit.
  tab/<id>.png          16x16  abas do menu (COLORIDOS, estilo icone de RPG; desenhar sem tingir). v0.8: + reputation.
  faction/<id>.png      32x32  (v0.8) brasao de cada faccao (guild kingdom academy church underworld wildfolk horde):
                               escudo heraldico com aro de metal, campo escuro no tom da faccao e o emblema claro.
  stack/<id>.png        16x16  (v0.8) marcas do HUD "Pilha": minor | major | ultimate | countered.
  ../mob_effect/<id>.png 18x18 (v0.8) efeitos novos do nucleo: burning chilled shocked bleeding petrified
                               (estilo vanilla: desenho solto com contorno escuro, sem fundo).
  Previas extras da v0.8: icons_preview_v08.png (so os glifos novos, 3x) e icons_preview_v08_ui.png (brasoes,
  efeitos, pilha e aba, 4x).
  v0.9 "Classes, Caminhos e o Dragao":
  glyph/<id>.png        + formas de arma do nucleo (strike arrow stance parry), formas guerreiras/a distancia e as 40
                               origens das 10 classes novas (gema na cor da classe: Guerreiro escarlate, Paladino dourado,
                               Barbaro laranja-queimado, Monge jade, Cavaleiro aco-azulado, Patrulheiro verde-floresta,
                               Ladino indigo, Bardo magenta, Artifice azul-celeste, Cacador verde-oliva).
  class/<id>.png        32x32  retrato das 15 classes numa janela em ARCO; aro no metal da categoria e gemas na cor
                               do recurso (Mana violeta, Vigor vermelho, Foco verde-agua).
  category/<id>.png     16x16  magic | martial | ranged (abas da tela de classe).
  resource/<id>.png     16x16  mana | vigor | focus.
  tab/travel.png, tab/class_trials.png  16x16 abas novas do menu B.
  Previas: icons_preview_v09.png (glifos novos 3x), icons_preview_v09_ui.png (classes/categorias/recursos/abas 4x)
  e icons_preview_v09_classes_2x.png.
  v1.0 "15 classes" (docs/V10_DESIGN.md §1, §2 art):
  class/<id>.png        32x32  as 15 classes = 5 Magicas (mage witch warlock druid necromancer) + 5 Fisicas (smith
                               rogue knight enchanter shinigami) + 5 Destreza (quincy gunslinger ranger gambler
                               steelball). Os retratos das 7 classes da v0.9 que sairam sao APAGADOS da pasta.
  category/<id>.png     16x16  magic | physical | dexterity (+ martial.png e ranged.png = copias, compatibilidade).
  race/<id>.png         32x32  medalhao redondo: human elf dwarf half_orc demon dragonborn beastfolk.
  social/<id>.png       16x16  party trade ally duel profile enemy (menu de interacao agachar + scroll, party, aliados).
  glyph/<id>.png        + pictogramas tematicos das classes novas (v10_*), escolhidos por palavra-chave para os glifos
                               de origem que os modulos de classe criarem (Anti-Magia e Restricao Celestial do
                               Encantador incluidos). Glifos traduzidos em src/main/lang/class_<id>/ ganham a cor da
                               classe (gema de origem) e tentam primeiro os desenhos da PROPRIA classe (V10_PIC_CLASS);
                               o id + nome contam antes da descricao. Glifos de fora das classes so caem num v10_* se
                               nenhuma palavra-chave geral servir.
  Previas: icons_preview_v10_ui.png (4x) e icons_preview_v10_portraits_2x.png (classes + racas no tamanho 2x).

Uso:
  py -I tools/gen/icons.py                      gera tudo + previas na pasta padrao
  py -I tools/gen/icons.py --preview <pasta>    muda a pasta das previas (icons_preview.png / icons_preview_small.png)
  py -I tools/gen/icons.py --debug <pasta>      tambem salva folhas 3x por grupo (para revisar o desenho)
  py -I tools/gen/icons.py --only id1,id2       gera so esses glifos (debug)
  py -I tools/gen/icons.py --core-effects       (v0.8, opcional) TAMBEM regrava os icones antigos do nucleo
                                                soaked frozen stunned rooted silenced corrupted exposed
                                                magic_exhaustion (hoje bolinhas lisas) no mesmo estilo dos novos

Ids de glifo: TODAS as chaves "glyph.isekai_mahou.<id>" (sem .desc) de src/main/lang/*/pt_br.json + os ids da
tabela GLYPHS abaixo (inclui os planejados da v0.7). Ids desconhecidos (novos) caem em guess_glyph(): tipo,
elemento e pictograma deduzidos por palavras-chave do id/nome/descricao - nunca falha.
"""
import colorsys
import glob
import hashlib
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
from pixelart import Canvas  # noqa: E402

ARGS = sys.argv[1:]


def _arg(name, default=None):
    if name in ARGS:
        i = ARGS.index(name)
        if i + 1 < len(ARGS):
            return ARGS[i + 1]
    return default


_flag_values = {_arg("--preview"), _arg("--debug"), _arg("--only")}
ROOT = next((a for a in ARGS if not a.startswith("--") and a not in _flag_values), os.path.dirname(TOOLS))
CORE_EFFECTS = "--core-effects" in ARGS   # v0.8: tambem redesenha os efeitos antigos do nucleo (soaked, frozen...)
GUI = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/textures/gui")
LANG = os.path.join(ROOT, "src/main/lang")
PREVIEW_DIR = _arg("--preview", os.path.join(os.environ.get("TEMP", ROOT), "isekai_icons_preview"))
DEBUG_DIR = _arg("--debug")
ONLY = set(filter(None, (_arg("--only") or "").split(",")))


# =====================================================================================================  CORES
def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def to_hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def from_hsv(h, s, v, a=255):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, clamp(s), clamp(v))
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)), a)


def hue_toward(h, target, amt):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return h + max(-amt, min(amt, d))


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(4))


def blend(under, over, alpha):
    """Compoe 'over' (rgb) sobre 'under' com opacidade alpha (0..1)."""
    if under[3] == 0:
        return (over[0], over[1], over[2], int(round(255 * alpha)))
    return (int(round(under[0] * (1 - alpha) + over[0] * alpha)),
            int(round(under[1] * (1 - alpha) + over[1] * alpha)),
            int(round(under[2] * (1 - alpha) + over[2] * alpha)), under[3])


def shade(c, v, s_mul=1.0, shift=0.0):
    """Mesma matiz com brilho v. shift>0 puxa a matiz para violeta (sombra), <0 para amarelo (luz)."""
    h, s, _ = to_hsv(c)
    if s > 0.08 and shift:
        h = hue_toward(h, 0.75, shift) if shift > 0 else hue_toward(h, 0.14, -shift)
    return from_hsv(h, s * s_mul, v)


def make_ramp(c):
    """Rampa de 5 tons a partir da cor base (meio-tom): o=contorno d=sombra m=meio l=luz h=brilho."""
    h, s, v = to_hsv(c)
    return {
        "o": shade(c, max(0.07, v * 0.17), 1.1, 0.09),
        "d": shade(c, v * 0.66, min(1.0, 1.08 + (0.1 if s < 0.6 else 0)), 0.045),
        "m": c,
        "l": from_hsv(hue_toward(h, 0.14, 0.025) if s > 0.08 else h, s * 0.72, min(1.0, v * 1.12 + 0.07)),
        "h": from_hsv(h, s * 0.28, 1.0),
    }


ELEMENTS = ["arcane", "fire", "water", "earth", "wind", "light", "shadow", "magma", "steam", "lightning", "sun",
            "hellfire", "nature", "ice", "purity", "abyss", "sand", "crystal", "gravity", "astral", "void", "twilight"]
ECOLOR = {"arcane": "B98CFF", "fire": "FF6A2B", "water": "3FA9FF", "earth": "A0743C", "wind": "9FF0D0",
          "light": "FFE27A", "shadow": "8A4FC8", "magma": "FF4500", "steam": "D8E6EE", "lightning": "7FDBFF",
          "sun": "FFB300", "hellfire": "C0182E", "nature": "4FBF4A", "ice": "A8E8FF", "purity": "E8F8FF",
          "abyss": "2A4F9A", "sand": "E3C77A", "crystal": "E07AF0", "gravity": "6B5BA8", "astral": "9DB4FF",
          "void": "5A2A8A", "twilight": "C06AA0",
          # Origens (glifos de classe): cor da classe
          "mage": "5B6CFF", "witch": "6F9A3A", "warlock": "A0182A", "druid": "5CC23A", "necromancer": "B8B09A",
          # v0.9 (sairam na v1.0; ficam so para os glifos antigos da tabela). "knight" e a classe da v1.0, abaixo
          "warrior": "E06040", "paladin": "F2C84A", "barbarian": "D07A2A", "monk": "36C6A6",
          "bard": "E04EA8", "artificer": "34A6E0", "hunter": "8C9A44",
          # v1.0: as 10 classes novas com a MESMA cor do MagicPath (rgb da classe no Java, A1) - aro do retrato,
          # campo e gema dos glifos de origem batem com o nome colorido na tela de classe
          "smith": "E8742C", "rogue": "8E5BD6", "knight": "7FA8FF", "enchanter": "5FE3C0", "shinigami": "D9483B",
          "quincy": "A8DFFF", "gunslinger": "D9A441", "ranger": "6BCB5A", "gambler": "E04A8C", "steelball": "B4C0CC"}
# v1.0: as 15 classes jogaveis (as 7 da v0.9 que sairam continuam no fim so para ordenar glifos antigos que sobrarem)
CLASS_IDS = ["mage", "witch", "warlock", "druid", "necromancer", "smith", "rogue", "knight", "enchanter", "shinigami",
             "quincy", "gunslinger", "ranger", "gambler", "steelball",
             "warrior", "paladin", "barbarian", "monk", "bard", "artificer", "hunter"]
PARENTS = {"magma": ("fire", "earth"), "steam": ("fire", "water"), "lightning": ("fire", "wind"),
           "sun": ("fire", "light"), "hellfire": ("fire", "shadow"), "nature": ("water", "earth"),
           "ice": ("water", "wind"), "purity": ("water", "light"), "abyss": ("water", "shadow"),
           "sand": ("earth", "wind"), "crystal": ("earth", "light"), "gravity": ("earth", "shadow"),
           "astral": ("wind", "light"), "void": ("wind", "shadow"), "twilight": ("light", "shadow")}

# Materiais naturais dos pictogramas (meio-tom). "p" = cor do elemento clareada (definida por icone).
MAT_BASE = {
    "fire": "FF7A1E", "flame": "FFD23A", "lava": "FF5A14", "ember": "FFB02E",
    "water": "3E9EFF", "aqua": "7FD4FF", "ice": "A6E4FF", "glass": "CFF4FF", "foam": "E8F6FF",
    "stone": "9C9CA8", "dirt": "9A6838", "wood": "A86E36", "sand": "E6C676", "obsidian": "5A3A86",
    "leaf": "5CC84A", "grass": "86D64A", "moss": "6E9A3A", "poison": "9AE63A",
    "gold": "FFC43A", "steel": "C4CEDC", "iron": "8E98A8", "copper": "D9884A",
    "blood": "D8283A", "heart": "FF4A5E", "pink": "FF8CC6", "flesh": "F0B48C",
    "bone": "EDE2C4", "white": "F2F2F8", "paper": "F0E2BC", "cloud": "E4EAF4", "storm": "7C849A",
    "void": "4A2A7A", "dark": "3E3650", "shadow": "7A50B8", "purple": "A452E0", "ink": "2C2C4A",
    "mana": "4F86FF", "violet": "9A6CFF", "cyan": "52E6F0", "teal": "36C2A8", "sky": "86C8FF",
    "red": "E83434", "orange": "FF9632", "yellow": "FFE04A", "green": "4ED860", "blue": "4A72FF",
    "crystal": "E496F8", "magenta": "E24AC8", "brown": "8A5A30", "black": "34303C", "silver": "D8DEE8",
    # v0.8
    "night": "2A3070", "pollen": "F4D84A", "deep": "2E50A0",
    # v0.9: cores dos recursos (MagicPath.Resource)
    "manac": "7A5CFF", "vigor": "E0563B", "focus": "3BC9A8",
}
K_FLAT = rgb("120E18")    # preto chapado (olhos, furos)
W_FLAT = rgb("FFFFFF")


def elem_light(ec):
    """Cor de pictograma derivada do elemento: clara e saturada o bastante para brilhar sobre o fundo escuro."""
    h, s, v = to_hsv(ec)
    return from_hsv(h, min(0.62, s * 0.85) if s > 0.12 else s, max(0.94, v))


# =====================================================================================================  PICTOGRAMA
class Pic:
    """Grade de materiais. Cada celula = (material, tom|None, grupo). Tom None = sombreado automatico."""

    def __init__(self, w=20, h=20):
        self.w, self.h = w, h
        self.m = [[None] * w for _ in range(h)]
        self.pending = []   # pixels "chapados" pos-sombreado

    # ---- infra
    def _spec(self, m, t):
        if m is None:
            return None, None
        if "." in m:
            m, t = m.split(".", 1)
        return m, t

    def put(self, x, y, m, t=None, g=0):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            m, t = self._spec(m, t)
            self.m[y][x] = None if m is None else (m, t, g)

    def fill(self, test, m, t=None, g=0):
        m, t = self._spec(m, t)
        for y in range(self.h):
            for x in range(self.w):
                if test(x + 0.5, y + 0.5):
                    self.m[y][x] = None if m is None else (m, t, g)
        return self

    def has(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h and self.m[y][x] is not None

    def recolor(self, test, m, t=None, g=0, only=None):
        """Repinta so os pixels JA pintados que passam no teste (recorte). only = material que pode ser trocado."""
        m, t = self._spec(m, t)
        for y in range(self.h):
            for x in range(self.w):
                c = self.m[y][x]
                if c is not None and (only is None or c[0] == only) and test(x + 0.5, y + 0.5):
                    self.m[y][x] = (m, t, g)
        return self

    # ---- formas (coordenadas em ponto flutuante; pixel (x,y) tem centro (x+.5, y+.5))
    def disc(self, cx, cy, r, m="p", t=None, g=0):
        return self.fill(lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r, m, t, g)

    def ellipse(self, cx, cy, rx, ry, m="p", t=None, g=0, rot=0.0):
        ca, sa = math.cos(math.radians(rot)), math.sin(math.radians(rot))

        def test(x, y):
            dx, dy = x - cx, y - cy
            u, v = dx * ca + dy * sa, -dx * sa + dy * ca
            return (u / rx) ** 2 + (v / ry) ** 2 <= 1.0
        return self.fill(test, m, t, g)

    def ring(self, cx, cy, r1, r0, m="p", t=None, g=0):
        return self.fill(lambda x, y: r0 * r0 <= (x - cx) ** 2 + (y - cy) ** 2 <= r1 * r1, m, t, g)

    def arc(self, cx, cy, r1, r0, a0, a1, m="p", t=None, g=0):
        """Setor de anel. Angulos em graus, 0 = direita, 90 = BAIXO (tela), sentido horario."""
        def test(x, y):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if not (r0 * r0 <= d2 <= r1 * r1):
                return False
            a = math.degrees(math.atan2(y - cy, x - cx)) % 360
            lo, hi = a0 % 360, a1 % 360
            return (lo <= a <= hi) if lo <= hi else (a >= lo or a <= hi)
        return self.fill(test, m, t, g)

    def rect(self, x0, y0, x1, y1, m="p", t=None, g=0):
        """Retangulo de pixels inclusivo (inteiros)."""
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                self.put(x, y, m, t, g)
        return self

    def poly(self, pts, m="p", t=None, g=0):
        n = len(pts)

        def test(px, py):
            inside = False
            j = n - 1
            for i in range(n):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                    inside = not inside
                j = i
            return inside
        return self.fill(test, m, t, g)

    def line(self, x0, y0, x1, y1, w=2.0, m="p", t=None, g=0):
        """Traco grosso (capsula) de largura w."""
        r2 = (w / 2.0) ** 2
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy or 1e-9

        def test(x, y):
            k = clamp(((x - x0) * dx + (y - y0) * dy) / L2)
            return (x - x0 - k * dx) ** 2 + (y - y0 - k * dy) ** 2 <= r2
        return self.fill(test, m, t, g)

    def taper(self, x0, y0, x1, y1, w=3.0, m="p", t=None, g=0, bow=0.0):
        """Traco afinado nas pontas (corte/lamina). bow = curvatura lateral (px)."""
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy or 1e-9
        L = math.sqrt(L2)
        px_, py_ = -dy / L, dx / L

        def test(x, y):
            k = ((x - x0) * dx + (y - y0) * dy) / L2
            if not (0.0 <= k <= 1.0):
                return False
            off = (x - x0) * px_ + (y - y0) * py_ - bow * math.sin(math.pi * k)
            return abs(off) <= (w / 2.0) * math.sin(math.pi * k) ** 0.7 + 0.15
        return self.fill(test, m, t, g)

    def path(self, pts, w=2.0, m="p", t=None, g=0):
        for a, b in zip(pts, pts[1:]):
            self.line(a[0], a[1], b[0], b[1], w, m, t, g)
        return self

    def curve(self, pts, w=2.0, m="p", t=None, g=0, steps=24):
        """Bezier (3 ou 4 pontos de controle) como traco grosso."""
        def bez(tt):
            if len(pts) == 3:
                (ax, ay), (bx, by), (cx, cy) = pts
                u = 1 - tt
                return (u * u * ax + 2 * u * tt * bx + tt * tt * cx, u * u * ay + 2 * u * tt * by + tt * tt * cy)
            (ax, ay), (bx, by), (cx, cy), (dx, dy) = pts
            u = 1 - tt
            return (u ** 3 * ax + 3 * u * u * tt * bx + 3 * u * tt * tt * cx + tt ** 3 * dx,
                    u ** 3 * ay + 3 * u * u * tt * by + 3 * u * tt * tt * cy + tt ** 3 * dy)
        samples = [bez(i / steps) for i in range(steps + 1)]
        r2 = (w / 2.0) ** 2

        def test(x, y):
            for i in range(len(samples) - 1):
                (x0, y0), (x1, y1) = samples[i], samples[i + 1]
                dx, dy = x1 - x0, y1 - y0
                L2 = dx * dx + dy * dy or 1e-9
                k = clamp(((x - x0) * dx + (y - y0) * dy) / L2)
                if (x - x0 - k * dx) ** 2 + (y - y0 - k * dy) ** 2 <= r2:
                    return True
            return False
        return self.fill(test, m, t, g)

    def star(self, cx, cy, ro, ri, n=5, rot=-90.0, m="p", t=None, g=0):
        pts = []
        for i in range(n * 2):
            r = ro if i % 2 == 0 else ri
            a = math.radians(rot + i * 180.0 / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        return self.poly(pts, m, t, g)

    def tri(self, a, b, c, m="p", t=None, g=0):
        return self.poly([a, b, c], m, t, g)

    def heart(self, cx, cy, s=1.0, m="heart", t=None, g=0):
        """Coracao centrado em (cx,cy); s=1 -> ~16 px de largura."""
        def test(x, y):
            u, v = (x - cx) / (8.6 * s), -(y - cy + 0.6 * s) / (8.0 * s)
            u *= 1.2
            v = v * 1.25 + 0.15
            return (u * u + v * v - 0.42) ** 3 - u * u * v ** 3 <= 0
        return self.fill(test, m, t, g)

    def arrow(self, x0, y0, x1, y1, w=2.0, head=4.0, m="p", t=None, g=0, hw=None):
        """Seta reta de (x0,y0) ate a ponta (x1,y1)."""
        L = math.hypot(x1 - x0, y1 - y0) or 1e-9
        ux, uy = (x1 - x0) / L, (y1 - y0) / L
        bx, by = x1 - ux * head, y1 - uy * head
        hw = hw if hw is not None else head * 0.9
        self.line(x0, y0, bx + ux * 0.5, by + uy * 0.5, w, m, t, g)
        self.poly([(x1 + ux * 0.4, y1 + uy * 0.4), (bx - uy * hw, by + ux * hw), (bx + uy * hw, by - ux * hw)], m, t, g)
        return self

    def arc_arrow(self, cx, cy, r, a0, a1, w=2.0, head=4.0, m="p", t=None, g=0):
        """Seta curva sobre um circulo, de a0 ate a1 (graus, horario se a1>a0)."""
        step = 1 if a1 >= a0 else -1
        span = abs(a1 - a0)
        hdeg = math.degrees(head / r)
        end = a1 - step * hdeg
        pts = []
        n = max(6, int(span / 6))
        for i in range(n + 1):
            a = math.radians(a0 + (end - a0) * i / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        self.path(pts, w, m, t, g)
        ae = math.radians(end)
        bx, by = cx + r * math.cos(ae), cy + r * math.sin(ae)
        tx, ty = -math.sin(ae) * step, math.cos(ae) * step     # tangente
        nx, ny = math.cos(ae), math.sin(ae)
        hw = head * 0.9
        tipx, tipy = bx + tx * head, by + ty * head
        self.poly([(tipx, tipy), (bx + nx * hw, by + ny * hw), (bx - nx * hw, by - ny * hw)], m, t, g)
        return self

    def sprite(self, rows, pal, ox=0, oy=0, g=0):
        """rows: strings; pal: char -> 'material[.tom]'. '.' e ' ' = vazio, '_' = apaga."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in ". ":
                    continue
                if ch == "_":
                    self.put(ox + x, oy + y, None)
                elif ch in pal:
                    self.put(ox + x, oy + y, pal[ch], None, g)
        return self

    def px(self, pts, m, t=None, g=0):
        for x, y in pts:
            self.put(x, y, m, t, g)
        return self

    def erase(self, test):
        return self.fill(test, None)

    def halo(self, m, r=1.5, g=90, test=None):
        """Pinta 'm' nos pixels VAZIOS a ate r de distancia do desenho atual (aura/brilho atras)."""
        filled = [(x, y) for y in range(self.h) for x in range(self.w) if self.m[y][x] is not None]
        mm, t = self._spec(m, None)
        ir = int(math.ceil(r))
        new = []
        for y in range(self.h):
            for x in range(self.w):
                if self.m[y][x] is not None or (test and not test(x + 0.5, y + 0.5)):
                    continue
                for (fx, fy) in filled:
                    if abs(fx - x) <= ir and abs(fy - y) <= ir and (fx - x) ** 2 + (fy - y) ** 2 <= r * r:
                        new.append((x, y))
                        break
        for (x, y) in new:
            self.m[y][x] = (mm, t, g)
        return self

    def mirror_x(self):
        for row in self.m:
            row.reverse()
        return self

    def shift(self, dx, dy):
        old = self.m
        self.m = [[None] * self.w for _ in range(self.h)]
        for y in range(self.h):
            for x in range(self.w):
                if old[y][x] is not None and 0 <= x + dx < self.w and 0 <= y + dy < self.h:
                    self.m[y + dy][x + dx] = old[y][x]
        return self

    # ---- render
    def render(self, ramps, outline):
        """Sombreia (luz no canto superior esquerdo), pinta e contorna. Devolve grade RGBA."""
        w, h, m = self.w, self.h, self.m
        out = [[(0, 0, 0, 0)] * w for _ in range(h)]
        for y in range(h):
            for x in range(w):
                cell = m[y][x]
                if cell is None:
                    continue
                mat, t, g = cell
                if mat == "k":
                    out[y][x] = K_FLAT
                    continue
                if mat == "W":
                    out[y][x] = W_FLAT
                    continue
                if t is None:
                    def same(nx, ny):
                        if not (0 <= nx < w and 0 <= ny < h):
                            return False
                        c2 = m[ny][nx]
                        return c2 is not None and c2[0] == mat and c2[2] == g and c2[0] not in ("k",)
                    lit = not same(x, y - 1) or not same(x - 1, y)
                    drk = not same(x, y + 1) or not same(x + 1, y)
                    t = "l" if lit and not drk else "d" if drk and not lit else "m"
                ramp = ramps.get(mat) or ramps["p"]
                out[y][x] = ramp.get(t, ramp["m"])
        # contorno 1px (vizinhanca 4)
        res = [row[:] for row in out]
        for y in range(h):
            for x in range(w):
                if out[y][x][3] == 0:
                    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + ox, y + oy
                        if 0 <= nx < w and 0 <= ny < h and out[ny][nx][3] > 0:
                            res[y][x] = outline
                            break
        return res


# =====================================================================================================  MOLDURAS
KINDS = ["form", "effect", "augment", "trigger", "link", "law"]
# metal da moldura por tipo (misturado com a cor do elemento)
KIND_METAL = {"form": rgb("D8DEF0"), "effect": rgb("FFD878"), "augment": rgb("B8C4D8"),
              "trigger": rgb("FF9A7A"), "link": rgb("8CF0E0"), "law": rgb("FFE6A0")}
PIC_OFFSET = {"form": (6, 6), "effect": (6, 6), "augment": (6, 6), "trigger": (6, 8), "link": (6, 6), "law": (6, 6)}


def kind_inside(kind, S=32):
    """Funcao de pertinencia (centro de pixel) da silhueta do tipo, numa tela SxS."""
    k = S / 32.0
    c = S / 2.0
    if kind == "form":
        return lambda x, y: (x - c) ** 2 + (y - c) ** 2 <= (15.6 * k) ** 2
    if kind == "effect":
        return lambda x, y: abs(x - c) + abs(y - c) <= 19.6 * k and abs(x - c) <= 15.6 * k and abs(y - c) <= 15.6 * k
    if kind == "augment":
        return lambda x, y: abs(x - c) <= 14.6 * k and abs(y - c) <= 14.6 * k and abs(x - c) + abs(y - c) <= 25.2 * k
    if kind == "trigger":
        def tri(x, y):
            # triangulo com pontas arredondadas (intersecao com um disco)
            yy = y - 0.4 * k
            if yy > 30.6 * k:
                return False
            half = (yy + 1.2 * k) * 0.55
            return abs(x - c) <= half and (x - c) ** 2 + (y - 18.6 * k) ** 2 <= (16.6 * k) ** 2
        return tri
    if kind == "link":
        def pill(x, y):
            r = 11.6 * k
            if abs(y - c) > r:
                return False
            dx = max(0.0, abs(x - c) - 4.4 * k)
            return dx * dx + (y - c) ** 2 <= r * r
        return pill
    if kind == "law":
        R = 16.2 * k

        def hexa(x, y):
            dx, dy = abs(x - c), abs(y - c)
            return dx <= R * 0.866 and dy <= R - dx * 0.57735
        return hexa
    if kind == "crest":
        # v0.8: escudo heraldico (brasao de faccao) - topo reto chanfrado, lados retos e ponta embaixo
        def heater(x, y):
            if y < 1.4 * k + max(0.0, abs(x - c) - 11.0 * k) * 0.9:
                return False
            if y <= 15.0 * k:
                return abs(x - c) <= 13.6 * k
            t = (y - 15.0 * k) / (15.8 * k)
            return t < 1.0 and abs(x - c) <= 13.6 * k * (1.0 - t ** 1.6) ** 0.85
        return heater
    if kind == "class":
        # v0.9: janela de retrato em ARCO (icones das classes) - base reta, topo semicircular
        R = 13.4 * k

        def arch(x, y):
            if abs(x - c) > R or y > 30.6 * k:
                return False
            return y >= 15.0 * k or (x - c) ** 2 + (y - 15.0 * k) ** 2 <= R * R
        return arch
    raise ValueError(kind)


_FRAME_CACHE = {}


def frame_geometry(kind, S=32):
    """Para cada pixel: None (fora) ou (dist_borda, normal_x, normal_y). Distancia euclidiana ate o pixel de fora."""
    key = (kind, S)
    if key in _FRAME_CACHE:
        return _FRAME_CACHE[key]
    ins = kind_inside(kind, S)
    inside = [[ins(x + 0.5, y + 0.5) for x in range(S)] for y in range(S)]
    outside = [(x, y) for y in range(-1, S + 1) for x in range(-1, S + 1)
               if not (0 <= x < S and 0 <= y < S) or not inside[y][x]]
    geo = [[None] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            if not inside[y][x]:
                continue
            best, bx, by = 1e9, 0, 0
            for (ox, oy) in outside:
                d = (ox - x) ** 2 + (oy - y) ** 2
                if d < best:
                    best, bx, by = d, ox, oy
            d = math.sqrt(best)
            # normal "para fora" estimada pela direcao ao pixel externo mais proximo (media dos vizinhos)
            nx, ny = (bx - x), (by - y)
            # suaviza pela direcao radial a partir do centro (evita normais "escadinha")
            rx, ry = x + 0.5 - S / 2, y + 0.5 - S / 2
            rl = math.hypot(rx, ry) or 1
            nl = math.hypot(nx, ny) or 1
            nx, ny = nx / nl * 0.6 + rx / rl * 0.4, ny / nl * 0.6 + ry / rl * 0.4
            nl = math.hypot(nx, ny) or 1
            geo[y][x] = (d, nx / nl, ny / nl)
    _FRAME_CACHE[key] = geo
    return geo


BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def fill_color(ec, t, kind):
    """Cor do preenchimento: t=0 escuro (baixo-direita), t=1 claro (centro/alto-esquerda)."""
    h, s, v = to_hsv(ec)
    s2 = min(0.92, s * 1.05 + 0.05) if s > 0.1 else s + 0.06
    if 0.25 < s < 0.7 and v > 0.85:
        # cores pastel (raio, gelo, vento, astral, arcano...) ficavam todas "ardosia" no fundo escuro:
        # mais saturacao no preenchimento para a matiz do elemento aparecer (raio azul-eletrico != gelo)
        s2 = max(s2, min(0.78, s * 1.55))
    vv = 0.17 + 0.47 * t
    hh = hue_toward(h, 0.75, 0.06 * (1 - t)) if s > 0.1 else h
    return from_hsv(hh, s2 * (0.9 + 0.1 * t), vv)


def rim_ramp(ec, kind):
    base = mix(elem_light(ec), KIND_METAL[kind], 0.45)
    h, s, v = to_hsv(base)
    return [from_hsv(hue_toward(h, 0.75, 0.05), min(1, s * 1.15 + 0.05), 0.42),
            from_hsv(h, s, 0.62),
            from_hsv(h, s * 0.9, 0.82),
            from_hsv(h, s * 0.65, 0.97),
            from_hsv(h, s * 0.25, 1.0)]


def draw_frame(kind, ec):
    """Moldura 32x32 do tipo, preenchida com o gradiente do elemento. Devolve (grade RGBA, mascara de preenchimento)."""
    S = 32
    geo = frame_geometry(kind, S)
    out = [[(0, 0, 0, 0)] * S for _ in range(S)]
    fillmask = [[False] * S for _ in range(S)]
    rim = rim_ramp(ec, kind)
    outline = shade(ec, 0.09, 1.0, 0.1)
    rim_w = 3.0 if kind != "law" else 3.0
    for y in range(S):
        for x in range(S):
            gp = geo[y][x]
            if gp is None:
                continue
            d, nx, ny = gp
            lightdot = -(nx + ny) / math.sqrt(2)          # +1 virado para cima-esquerda
            if d <= 1.0:
                out[y][x] = outline
            elif d <= rim_w:
                if kind == "effect":
                    # facetas de gema: 4 tons por direcao
                    q = 4 if lightdot > 0.55 else 3 if lightdot > 0.0 else 1 if lightdot > -0.55 else 0
                    q = min(q, 3) if d > 2.0 else q
                    out[y][x] = rim[q]
                else:
                    q = (lightdot + 1) / 2 * 3.99
                    q = int(q) + (1 if d <= 2.0 and q > 2.2 else 0)
                    out[y][x] = rim[max(0, min(4, q))]
            else:
                # preenchimento: radial + diagonal, em faixas com pontilhado Bayer
                cxr, cyr = (x + 0.5 - 16) / 13.0, (y + 0.5 - 15.0) / 13.0
                radial = clamp(1.0 - math.hypot(cxr, cyr))
                diag = clamp(0.5 - (x + y - 31) / 46.0)
                t = clamp(0.62 * radial + 0.48 * diag - 0.04)
                levels = 6
                tq = t * levels + (BAYER4[y % 4][x % 4] / 16.0 - 0.5) * 0.7
                tq = clamp(math.floor(tq + 0.5) / levels)
                out[y][x] = fill_color(ec, tq, kind)
                fillmask[y][x] = True
    # linha interna escura entre aro e preenchimento (profundidade)
    for y in range(S):
        for x in range(S):
            gp = geo[y][x]
            if gp and rim_w < gp[0] <= rim_w + 1.0:
                out[y][x] = mix(out[y][x], outline, 0.55)
    decorate_frame(kind, out, geo, rim, ec)
    # brilho no canto superior esquerdo (reflexo de vidro sobre o preenchimento)
    gloss = gloss_pixels(kind)
    for (x, y, a) in gloss:
        if out[y][x][3]:
            out[y][x] = blend(out[y][x], rgb("FFFFFF"), a)
    return out, fillmask


def gloss_pixels(kind):
    pts = {
        "form": [(8, 6, .55), (7, 7, .55), (6, 8, .55), (6, 9, .35), (9, 6, .35), (5, 10, .2), (10, 5, .2)],
        "effect": [(10, 6, .5), (9, 7, .5), (8, 8, .5), (7, 9, .35), (11, 5, .3)],
        "augment": [(5, 6, .5), (6, 5, .5), (5, 7, .35), (7, 5, .35), (5, 8, .2), (8, 5, .2)],
        "trigger": [(13, 9, .5), (12, 11, .45), (11, 13, .35), (10, 15, .2)],
        "link": [(9, 7, .5), (8, 8, .5), (10, 7, .35), (7, 9, .35), (11, 6, .2)],
        "law": [(9, 6, .5), (7, 7, .5), (6, 8, .4), (11, 5, .3), (5, 9, .25)],
    }
    return pts.get(kind, [])


def decorate_frame(kind, out, geo, rim, ec):
    S = 32
    dark = rim[0]
    bright = rim[4]
    if kind == "form":
        # marcas runicas no anel a cada 30 graus (traco curto escuro + ponto claro)
        for i in range(12):
            a = math.radians(i * 30 + 15)
            x = int(16 + 13.9 * math.cos(a))
            y = int(16 + 13.9 * math.sin(a))
            if geo[y][x] and geo[y][x][0] > 1.0:
                out[y][x] = dark if i % 3 else mix(dark, bright, 0.2)
        for i in range(4):
            a = math.radians(i * 90 - 90)
            x = int(16 + 14.0 * math.cos(a))
            y = int(16 + 14.0 * math.sin(a))
            if geo[y][x] and geo[y][x][0] > 1.0:
                out[y][x] = bright
    elif kind == "augment":
        # rebites nos 4 cantos chanfrados
        for (x, y, c) in ((5, 5, 4), (26, 5, 3), (5, 26, 2), (26, 26, 1)):
            out[y][x] = rim[c]
            out[y + 1][x + 1] = dark
    elif kind == "link":
        # setas duplas gravadas nas pontas (< >)
        for side in (-1, 1):
            cx = 16 + side * 14
            for dy in (-2, -1, 0, 1, 2):
                xx = cx - side * (2 - abs(dy))
                if 0 <= xx < S and geo[16 + dy][xx]:
                    out[16 + dy][xx] = bright if dy <= 0 else rim[3]
    elif kind == "law":
        # tachas nos 6 vertices
        for i in range(6):
            a = math.radians(-90 + i * 60)
            x = int(16 + 13.6 * math.cos(a))
            y = int(16 + 13.6 * math.sin(a))
            if geo[y][x]:
                out[y][x] = bright
    elif kind == "trigger":
        x, y = 15, 4
        for (ox, oy) in ((0, 0), (1, 0)):
            if geo[y + oy][x + ox]:
                out[y + oy][x + ox] = bright


# =====================================================================================================  ICONE DE GLIFO
def ramps_for(ec, elem):
    r = {k: make_ramp(rgb(v)) for k, v in MAT_BASE.items()}
    r["p"] = make_ramp(elem_light(ec))
    pa, pb = PARENTS.get(elem, (None, None))
    r["a"] = make_ramp(elem_light(rgb(ECOLOR[pa]))) if pa else r["p"]
    r["b"] = make_ramp(elem_light(rgb(ECOLOR[pb]))) if pb else r["p"]
    # "q": tom secundario do elemento (mais saturado / escuro) para detalhes
    h, s, v = to_hsv(ec)
    r["q"] = make_ramp(from_hsv(h, min(1, s * 1.1 + 0.15), 0.78))
    return r


def paste(dst, src, ox, oy, only=None):
    for y, row in enumerate(src):
        for x, c in enumerate(row):
            if c[3] == 0:
                continue
            X, Y = ox + x, oy + y
            if 0 <= X < len(dst[0]) and 0 <= Y < len(dst):
                if c[3] == 255 or dst[Y][X][3] == 0:
                    dst[Y][X] = c
                else:
                    dst[Y][X] = blend(dst[Y][X], c, c[3] / 255)


def make_glyph_icon(spec):
    kind, elem = spec["kind"], spec["elem"]
    ec = rgb(ECOLOR[elem])
    out, fillmask = draw_frame("link" if kind == "link" else kind, ec)
    pic = Pic(20, 20)
    PICS[spec["pic"]](pic, **spec.get("args", {}))
    ramps = ramps_for(ec, elem)
    outline_col = shade(ec, 0.07, 1.0, 0.1)
    img = pic.render(ramps, outline_col)
    ox, oy = PIC_OFFSET[kind]
    # sombra projetada do pictograma sobre o preenchimento
    for y in range(20):
        for x in range(20):
            if img[y][x][3]:
                X, Y = ox + x + 1, oy + y + 1
                if 0 <= X < 32 and 0 <= Y < 32 and fillmask[Y][X]:
                    out[Y][X] = blend(out[Y][X], (0, 0, 0, 255), 0.42)
    paste(out, img, ox, oy)
    for b in spec.get("badges", []):
        draw_badge(out, b, ramps, outline_col)
    if spec.get("legend"):
        draw_legend_star(out)
    return out


def draw_badge(out, badge, ramps, outline_col):
    """Mini-icone 11x11 num canto (diferencia glifos parecidos)."""
    name, corner = (badge, "br") if isinstance(badge, str) else badge
    p = Pic(11, 11)
    BADGES[name](p)
    img = p.render(ramps, outline_col)
    pos = {"br": (21, 21), "bl": (0, 21), "tr": (21, 0), "tl": (0, 0)}[corner]
    paste(out, img, *pos)


def draw_legend_star(out):
    gold, light, dark = rgb("FFE070"), rgb("FFFBE0"), rgb("8A5A10")
    pts = {(27, 0): gold, (27, 1): light, (26, 2): gold, (27, 2): light, (28, 2): gold, (25, 2): dark,
           (29, 2): dark, (27, 3): gold, (27, 4): dark, (26, 1): dark, (28, 1): dark, (26, 3): dark,
           (28, 3): dark, (27, -1): dark}
    for (x, y), c in pts.items():
        if 0 <= x < 32 and 0 <= y < 32:
            out[y][x] = c


# =====================================================================================================  BIBLIOTECA
PICS = {}
BADGES = {}


def pic(name):
    def deco(fn):
        PICS[name] = fn
        return fn
    return deco


def badge(name):
    def deco(fn):
        BADGES[name] = fn
        return fn
    return deco


# =====================================================================================================  PREVIAS (fonte 3x5)
FONT_ROWS = {
    "a": ".#. #.# ### #.# #.#", "b": "##. #.# ##. #.# ##.", "c": ".## #.. #.. #.. .##", "d": "##. #.# #.# #.# ##.",
    "e": "### #.. ##. #.. ###", "f": "### #.. ##. #.. #..", "g": ".## #.. #.# #.# .##", "h": "#.# #.# ### #.# #.#",
    "i": "### .#. .#. .#. ###", "j": "..# ..# ..# #.# .#.", "k": "#.# #.# ##. #.# #.#", "l": "#.. #.. #.. #.. ###",
    "m": "#.# ### ### #.# #.#", "n": "##. #.# #.# #.# #.#", "o": ".#. #.# #.# #.# .#.", "p": "##. #.# ##. #.. #..",
    "q": ".#. #.# #.# ##. .##", "r": "##. #.# ##. #.# #.#", "s": ".## #.. .#. ..# ##.", "t": "### .#. .#. .#. .#.",
    "u": "#.# #.# #.# #.# ###", "v": "#.# #.# #.# #.# .#.", "w": "#.# #.# ### ### #.#", "x": "#.# #.# .#. #.# #.#",
    "y": "#.# #.# .#. .#. .#.", "z": "### ..# .#. #.. ###", "0": "### #.# #.# #.# ###", "1": ".#. ##. .#. .#. ###",
    "2": "##. ..# .#. #.. ###", "3": "##. ..# .#. ..# ##.", "4": "#.# #.# ### ..# ..#", "5": "### #.. ##. ..# ##.",
    "6": ".## #.. ### #.# ###", "7": "### ..# .#. .#. .#.", "8": "### #.# ### #.# ###", "9": "### #.# ### ..# ##.",
    "_": "... ... ... ... ###", "-": "... ... ### ... ...", ".": "... ... ... ... .#.", " ": "... ... ... ... ...",
    "?": "##. ..# .#. ... .#.", "/": "..# ..# .#. #.. #..", ":": "... .#. ... .#. ...", "(": ".#. #.. #.. #.. .#.",
    ")": ".#. ..# ..# ..# .#.", "+": "... .#. ### .#. ...", "=": "... ### ... ### ...", ",": "... ... ... .#. #..",
}
FONT = {k: v.replace(" ", "") for k, v in FONT_ROWS.items()}
assert all(len(v) == 15 for v in FONT.values())


def text(canvas_px, s, x, y, col):
    for ch in s.lower():
        bits = FONT.get(ch, FONT["?"])
        for i, b in enumerate(bits):
            if b == "#":
                X, Y = x + i % 3, y + i // 3
                if 0 <= Y < len(canvas_px) and 0 <= X < len(canvas_px[0]):
                    canvas_px[Y][X] = col
        x += 4


def scaled(img, k):
    return [[img[y // k][x // k] for x in range(len(img[0]) * k)] for y in range(len(img) * k)]


def save_px(px, path):
    c = Canvas(len(px[0]), len(px))
    c.px = px
    c.save(path)


def wrap_label(s, maxc):
    if len(s) <= maxc:
        return [s]
    cut = [i for i, ch in enumerate(s) if ch == "_" and i <= maxc]
    i = min(cut, key=lambda i: abs(i - len(s) / 2)) if cut else maxc
    return [s[:i + (1 if cut else 0)], s[i + (1 if cut else 0):][:maxc]]


def sheet(items, scale, cols, path, bg=rgb("17141F"), label=True, cell_pad=6):
    """items: lista (nome, imagem RGBA). Grade com o nome embaixo (ate 2 linhas)."""
    if not items:
        return
    iw = max(len(img[0]) for _, img in items) * scale      # celula pelo maior icone (aceita tamanhos mistos)
    ih = max(len(img) for _, img in items) * scale
    maxc = max(4, (iw + 10) // 4)
    cw = max(iw, maxc * 4 - 1) + cell_pad
    chh = ih + (14 if label else 0) + cell_pad
    rows = (len(items) + cols - 1) // cols
    W, H = cols * cw + cell_pad, rows * chh + cell_pad
    px = [[bg] * W for _ in range(H)]
    for i, (name, img) in enumerate(items):
        cx = cell_pad + (i % cols) * cw
        cy = cell_pad + (i // cols) * chh
        big = scaled(img, scale) if scale != 1 else img
        bw, bh = len(big[0]), len(big)
        ox = cx + (cw - cell_pad - bw) // 2
        oy = cy + (ih - bh) // 2
        for y in range(bh):
            for x in range(bw):
                c = big[y][x]
                if c[3]:
                    px[oy + y][ox + x] = blend(px[oy + y][ox + x], c, c[3] / 255)
        if label:
            for j, ln in enumerate(wrap_label(name, maxc)):
                tx = cx + (cw - cell_pad - (len(ln) * 4 - 1)) // 2
                text(px, ln, tx, cy + ih + 2 + j * 6, rgb("D8D0E8"))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    save_px(px, path)


# =====================================================================================================  PICTOGRAMAS
# Tela 20x20, centro (10,10); conteudo em ~1..19 (contorno automatico em volta). Materiais: ver MAT_BASE,
# "p" = cor clara do elemento, "q" = tom forte do elemento, "a"/"b" = pais da fusao, "k" = preto, "W" = branco.
# Sufixo ".d/.m/.l/.h" fixa o tom (sem sombreado automatico).

def T(pts, cx, cy, s=1.0, flip=False):
    return [(cx + (-x if flip else x) * s, cy + y * s) for x, y in pts]


def flame(d, cx=10.0, base=19.0, s=1.0, outer="fire", inner="flame", core="W", flip=False, g=0):
    o = [(1, -18), (3.2, -14.5), (5.6, -11), (6.6, -7), (5.8, -3), (2.8, -0.4), (-2.8, -0.4), (-5.8, -3),
         (-6.6, -7), (-5.8, -10.8), (-4.2, -8.6), (-3.6, -12.2), (-1.4, -14.8)]
    i = [(0.6, -12.2), (2.8, -8.6), (3.8, -5.6), (2.3, -2.2), (-2.3, -2.2), (-3.8, -5.2), (-2.8, -7.8), (-1.2, -6.6)]
    c = [(0.2, -7.4), (1.7, -4.6), (0.9, -2.3), (-0.9, -2.3), (-1.7, -4.6)]
    d.poly(T(o, cx, base, s, flip), outer, g=g)
    if inner:
        d.poly(T(i, cx, base, s, flip), inner, g=g + 1)
    if core and s >= 0.7:
        d.poly(T(c, cx, base, s, flip), core, g=g + 2)


def drop(d, cx=10.0, cy=11.0, r=6.0, m="water", tip=1.7, shine=True, g=0):
    """Gota: disco + ponta para cima."""
    ccy = cy + r * 0.35
    D = r * tip
    d.disc(cx, ccy, r, m, g=g)
    th = math.acos(min(1.0, r / D))
    pts = [(cx, ccy - D), (cx + r * math.sin(th), ccy - r * math.cos(th)),
           (cx - r * math.sin(th), ccy - r * math.cos(th))]
    d.poly(pts, m, g=g)
    if shine:
        d.px([(int(cx - r * 0.45), int(ccy - r * 0.3)), (int(cx - r * 0.45), int(ccy - r * 0.3) + 1)],
             m.split(".")[0] + ".h")


def skull(d, cx=10.0, cy=10.0, s=1.0, m="bone", g=0, jaw=True):
    d.disc(cx, cy - 1.6 * s, 6.6 * s, m, g=g)
    if jaw:
        d.rect(int(cx - 3.6 * s), int(cy + 3 * s), int(cx + 3.6 * s) - 1, int(cy + 6.2 * s), m, g=g)
    d.disc(cx - 2.7 * s, cy - 0.4 * s, 1.75 * s, "k")
    d.disc(cx + 2.7 * s, cy - 0.4 * s, 1.75 * s, "k")
    d.tri((cx, cy + 1.6 * s), (cx - 1.1 * s, cy + 3.3 * s), (cx + 1.1 * s, cy + 3.3 * s), "k")
    if jaw and s >= 0.8:
        for k in (-2, 0, 2):
            d.put(int(cx + k * s * 0.9), int(cy + 5.2 * s), "k")


def sword(d, x0=3.0, y0=17.0, x1=17.0, y1=3.0, blade="steel", guard="gold", grip="wood", w=2.6, g=0):
    """Espada reta de (x0,y0) (pomo) ate (x1,y1) (ponta)."""
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    gx, gy = x0 + ux * 4.2, y0 + uy * 4.2          # guarda
    d.line(gx, gy, x1 - ux * 1.8, y1 - uy * 1.8, w, blade, g=g)
    d.tri((x1 + ux * 0.4, y1 + uy * 0.4), (x1 - ux * 2.6 - uy * w / 2, y1 - uy * 2.6 + ux * w / 2),
          (x1 - ux * 2.6 + uy * w / 2, y1 - uy * 2.6 - ux * w / 2), blade, g=g)
    d.line(gx - uy * 3.4, gy + ux * 3.4, gx + uy * 3.4, gy - ux * 3.4, 2.0, guard, g=g + 1)
    d.line(x0 + ux * 1.2, y0 + uy * 1.2, gx, gy, 1.8, grip, g=g + 2)
    d.disc(x0 + ux * 0.6, y0 + uy * 0.6, 1.4, guard, g=g + 1)


def orb(d, cx=10.0, cy=10.0, r=5.0, m="p", g=0, shine=True):
    d.disc(cx, cy, r, m, g=g)
    if shine:
        base = m.split(".")[0]
        d.disc(cx - r * 0.38, cy - r * 0.38, max(0.8, r * 0.28), base + ".h", g=g)


def bolt(d, cx=10.0, cy=10.0, s=1.0, m="yellow", g=0):
    pts = [(1.5, -9), (-4.5, 1), (-0.5, 1), (-2.5, 9), (4.5, -2), (0.5, -2), (2.8, -9)]
    d.poly(T(pts, cx, cy, s), m, g=g)


def leaf(d, cx=10.0, cy=10.0, s=1.0, rot=-45.0, m="leaf", vein=True, g=0):
    d.ellipse(cx, cy, 7.2 * s, 3.6 * s, m, g=g, rot=rot)
    if vein:
        a = math.radians(rot)
        d.line(cx - math.cos(a) * 6 * s, cy - math.sin(a) * 6 * s, cx + math.cos(a) * 5 * s,
               cy + math.sin(a) * 5 * s, 1.0, m.split(".")[0] + ".d", g=g)


def shield(d, cx=10.0, cy=10.0, s=1.0, m="steel", trim=None, g=0):
    pts = [(-7, -7.5), (0, -9), (7, -7.5), (7, -1), (5, 4), (0, 8.6), (-5, 4), (-7, -1)]
    d.poly(T(pts, cx, cy, s), m, g=g)
    if trim:
        pts2 = [(-4.6, -5.6), (0, -6.6), (4.6, -5.6), (4.6, -1), (3.2, 2.6), (0, 5.8), (-3.2, 2.6), (-4.6, -1)]
        d.poly(T(pts2, cx, cy, s), trim, g=g + 1)


def eye(d, cx=10.0, cy=10.0, s=1.0, white="white", iris="p", pupil="k", g=0):
    d.fill(lambda x, y: abs(x - cx) / (8.4 * s) + 0 <= 1 and abs(y - cy) <= 5.2 * s * (1 - ((x - cx) / (8.4 * s)) ** 2),
           white, g=g)
    d.disc(cx, cy, 3.3 * s, iris, g=g + 1)
    d.disc(cx, cy, 1.5 * s, pupil)
    d.put(int(cx - 1.4 * s), int(cy - 1.6 * s), "W")


def hourglass(d, cx=10.0, cy=10.0, s=1.0, frame="wood", glass="glass", sand="sand", top=0.4, bot=0.7, g=0):
    d.rect(int(cx - 6 * s), int(cy - 8.5 * s), int(cx + 6 * s) - 1, int(cy - 7 * s), frame, g=g)
    d.rect(int(cx - 6 * s), int(cy + 7 * s), int(cx + 6 * s) - 1, int(cy + 8.5 * s), frame, g=g)
    d.poly([(cx - 4.6 * s, cy - 7 * s), (cx + 4.6 * s, cy - 7 * s), (cx + 0.9 * s, cy), (cx + 4.6 * s, cy + 7 * s),
            (cx - 4.6 * s, cy + 7 * s), (cx - 0.9 * s, cy)], glass, g=g + 1)
    if top > 0:
        yt = cy - 7 * s + (1 - top) * 6 * s
        d.fill(lambda x, y: y >= yt and y <= cy - 0.6 and abs(x - cx) <= 4.6 * s * (cy - y) / (7 * s) + 0.3, sand, g=g + 2)
    if bot > 0:
        yb = cy + 7 * s - bot * 6.5 * s
        d.fill(lambda x, y: yb <= y <= cy + 7 * s and abs(x - cx) <= 4.6 * s * (y - cy) / (7 * s) + 0.2, sand, g=g + 2)
        d.line(cx, cy - 0.5, cx, yb, 1.0, sand, g=g + 2)


def figure(d, pose="stand", cx=10.0, m="p", g=0, oy=0):
    """Silhueta humana (sprite). Poses: stand, run, crouch, jump, swim, arms_up, float."""
    S = {
        "stand": ["...###...", "...###...", "...###...", "....#....", ".#######.", "#.#####.#", "#.#####.#",
                  "#.#####.#", "..#####..", "..##.##..", "..##.##..", "..##.##..", "..##.##..", ".###.###."],
        "arms_up": ["#..###..#", "#..###..#", "#..###..#", ".#..#..#.", "..#####..", "..#####..", "..#####..",
                    "..#####..", "..#####..", "..##.##..", "..##.##..", "..##.##..", "..##.##..", ".###.###."],
        "run": ["......###..", "......###..", "......###..", ".......#...", "...######..", "..#.####.#.", ".#..####..#",
                "....####...", "....#####..", "...##...##.", "..##.....##", ".##.......#", "##.........", "#.........."],
        "crouch": ["...........", "...........", "...........", "...........", "....###....", "....###....",
                   "....###....", "..######...", ".#.#####...", "...######..", "...#######.", "..##....##.", "..##.....#.", ".###....##."],
        "jump": ["...###...", "...###...", "...###...", "#...#...#", ".#######.", "...###...", "...###...",
                 "...###...", "..#####..", ".##...##.", "##.....##", "#.......#", ".........", "........."],
    }[pose]
    w = len(S[0])
    d.sprite(S, {"#": m}, int(cx - w / 2 + 0.5), 3 + oy, g=g)


def rot_pts(pts, cx, cy, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in pts]


def cloud(d, cx=10.0, cy=8.0, s=1.0, m="cloud", g=0):
    d.disc(cx - 4.6 * s, cy + 1.1 * s, 3.4 * s, m, g=g)
    d.disc(cx + 0.2 * s, cy - 1.0 * s, 4.6 * s, m, g=g)
    d.disc(cx + 4.8 * s, cy + 1.0 * s, 3.5 * s, m, g=g)
    d.fill(lambda x, y: cx - 4.8 * s <= x <= cx + 5.0 * s and cy + 0.5 * s <= y <= cy + 4.4 * s, m, g=g)


def burst(d, cx=10.0, cy=10.0, ro=9.0, ri=4.5, n=10, m="fire", rot=-90.0, jag=(1.0, 0.8, 0.95, 0.75), g=0):
    pts = []
    for i in range(n * 2):
        r = ro * jag[(i // 2) % len(jag)] if i % 2 == 0 else ri
        a = math.radians(rot + i * 180.0 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.poly(pts, m, g=g)


def explosion(d, cx=10.0, cy=10.0, s=1.0, outer="fire", mid="flame", core="W", g=0):
    burst(d, cx, cy, 9.4 * s, 5.0 * s, 9, outer, -90, (1.0, 0.78, 0.94, 0.72, 0.9), g)
    burst(d, cx, cy, 6.0 * s, 3.2 * s, 7, mid, -70, (1.0, 0.8, 0.92), g + 1)
    if core:
        d.disc(cx, cy, 1.9 * s, core, g=g + 2)


def crescent(d, cx, cy, r, ox, oy, r2, m="p", g=0):
    d.fill(lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r and (x - cx - ox) ** 2 + (y - cy - oy) ** 2 > r2 * r2,
           m, g=g)


def cube(d, cx=10.0, cy=10.0, s=1.0, m="stone", g=0, top=None):
    """Cubo isometrico (topo claro, esquerda meio, direita escura)."""
    A = (cx, cy - 7.4 * s)
    B = (cx + 7.6 * s, cy - 3.6 * s)
    C = (cx, cy + 0.2 * s)
    D = (cx - 7.6 * s, cy - 3.6 * s)
    E = (cx - 7.6 * s, cy + 4.8 * s)
    F = (cx, cy + 8.8 * s)
    G = (cx + 7.6 * s, cy + 4.8 * s)
    base = m.split(".")[0]
    d.poly([D, C, F, E], base + ".m", g=g)
    d.poly([C, B, G, F], base + ".d", g=g)
    d.poly([A, B, C, D], (top or base) + ".l", g=g)


def crystal(d, cx=10.0, cy=10.0, h=8.0, w=3.6, m="crystal", rot=0.0, g=0):
    pts = [(cx, cy - h), (cx + w, cy - h + w * 0.95), (cx + w, cy + h - w * 0.95), (cx, cy + h),
           (cx - w, cy + h - w * 0.95), (cx - w, cy - h + w * 0.95)]
    left = [(cx, cy - h), (cx, cy + h), (cx - w, cy + h - w * 0.95), (cx - w, cy - h + w * 0.95)]
    base = m.split(".")[0]
    d.poly(rot_pts(pts, cx, cy, rot), base + ".m", g=g)
    d.poly(rot_pts(left, cx, cy, rot), base + ".l", g=g)
    hl = rot_pts([(cx - w * 0.45, cy - h + w * 1.3), (cx - w * 0.45, cy + h * 0.25)], cx, cy, rot)
    d.line(hl[0][0], hl[0][1], hl[1][0], hl[1][1], 1.0, base + ".h", g=g)


def chestplate(d, cx=10.0, cy=10.0, m="steel", trim=None, g=0):
    pts = [(-8.4, -6.4), (-3.6, -8), (-2.2, -5.2), (2.2, -5.2), (3.6, -8), (8.4, -6.4), (8.2, -1.8), (5.4, -0.6),
           (5.4, 7.6), (-5.4, 7.6), (-5.4, -0.6), (-8.2, -1.8)]
    d.poly(T(pts, cx, cy), m, g=g)
    if trim:
        d.line(cx, cy - 4.8, cx, cy + 7.0, 1.4, trim, g=g + 1)
        d.line(cx - 5.0, cy + 2.5, cx + 5.0, cy + 2.5, 1.2, trim, g=g + 1)


def chain_link(d, cx, cy, rx, ry, w=1.6, rot=0.0, m="steel", g=0):
    ca, sa = math.cos(math.radians(rot)), math.sin(math.radians(rot))

    def test(x, y):
        dx, dy = x - cx, y - cy
        u, v = dx * ca + dy * sa, -dx * sa + dy * ca
        return (u / rx) ** 2 + (v / ry) ** 2 <= 1.0 and (u / (rx - w)) ** 2 + (v / (ry - w)) ** 2 > 1.0
    d.fill(test, m, g=g)


def spiral(d, cx=10.0, cy=10.0, r0=1.0, r1=8.0, turns=1.6, w=1.8, m="p", a0=0.0, g=0, cw=True):
    pts = []
    n = int(40 * turns)
    for i in range(n + 1):
        t = i / n
        a = math.radians(a0 + (360 * turns * t) * (1 if cw else -1))
        r = r0 + (r1 - r0) * t
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.path(pts, w, m, g=g)


def sun(d, cx=10.0, cy=10.0, r=4.6, rays=8, ray_len=3.2, m="yellow", core="flame", g=0):
    for i in range(rays):
        a = math.radians(i * 360 / rays - 90)
        x0, y0 = cx + math.cos(a) * (r + 1.0), cy + math.sin(a) * (r + 1.0)
        x1, y1 = cx + math.cos(a) * (r + ray_len), cy + math.sin(a) * (r + ray_len)
        d.line(x0, y0, x1, y1, 1.9 if i % 2 == 0 else 1.5, m, g=g + 1)
    d.disc(cx, cy, r, core, g=g)


def moon(d, cx=10.0, cy=10.0, r=7.0, m="bone", ox=3.4, oy=-2.4, g=0):
    crescent(d, cx, cy, r, ox, oy, r * 0.86, m, g)


def snowflake(d, cx=10.0, cy=10.0, r=8.6, m="ice", g=0, w=1.8):
    for i in range(3):
        a = math.radians(i * 60 + 90)
        dx, dy = math.cos(a) * r, math.sin(a) * r
        d.line(cx - dx, cy - dy, cx + dx, cy + dy, w, m, g=g)
        for sgn in (1, -1):
            bx, by = cx + sgn * dx * 0.55, cy + sgn * dy * 0.55
            for b in (-1, 1):
                aa = a + (math.pi if sgn < 0 else 0) + b * math.radians(50)
                d.line(bx, by, bx + math.cos(aa) * 3.0, by + math.sin(aa) * 3.0, 1.3, m, g=g)
    d.disc(cx, cy, 2.0, m.split(".")[0] + ".h", g=g + 1)


def ghost(d, cx=10.0, cy=10.0, s=1.0, m="white", g=0, eyes="k"):
    d.disc(cx, cy - 2.5 * s, 6.2 * s, m, g=g)
    d.rect(int(cx - 6.2 * s + 0.5), int(cy - 2.5 * s), int(cx + 6.2 * s - 0.5), int(cy + 5 * s), m, g=g)
    for i in range(3):
        x = cx - 4.2 * s + i * 4.2 * s
        d.disc(x, cy + 5.3 * s, 2.0 * s, m, g=g)
    for i in range(2):
        x = cx - 2.1 * s + i * 4.2 * s
        d.disc(x, cy + 7.2 * s, 1.5 * s, None)
    if eyes:
        d.ellipse(cx - 2.3 * s, cy - 2.4 * s, 1.2 * s, 1.8 * s, eyes)
        d.ellipse(cx + 2.3 * s, cy - 2.4 * s, 1.2 * s, 1.8 * s, eyes)


def cross(d, cx=10.0, cy=10.0, arm=7.0, w=3.0, m="W", g=0, latin=False):
    h = w / 2.0
    up = arm * (1.25 if latin else 1.0)
    d.fill(lambda x, y: (abs(x - cx) <= h and cy - up <= y <= cy + arm * (1.4 if latin else 1.0))
           or (abs(y - cy + (arm * 0.4 if latin else 0)) <= h and abs(x - cx) <= arm), m, g=g)


def plus(d, cx=10.0, cy=10.0, arm=7.5, w=4.0, m="p", g=0):
    h = w / 2.0
    d.fill(lambda x, y: (abs(x - cx) <= h and abs(y - cy) <= arm) or (abs(y - cy) <= h and abs(x - cx) <= arm), m, g=g)


def bone(d, x0, y0, x1, y1, m="bone", w=2.2, knob=1.6, g=0):
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    px_, py_ = -uy, ux
    d.line(x0, y0, x1, y1, w, m, g=g)
    for (x, y) in ((x0, y0), (x1, y1)):
        d.disc(x + px_ * knob * 0.75, y + py_ * knob * 0.75, knob, m, g=g)
        d.disc(x - px_ * knob * 0.75, y - py_ * knob * 0.75, knob, m, g=g)


def wave_curl(d, m="water", foam="foam", g=0, s=1.0, ox=0.0, oy=0.0):
    cx, cy = 9.0 * s + ox, 9.5 * s + oy
    d.fill(lambda x, y: ((x - cx) ** 2 + (y - cy) ** 2 <= (7.6 * s) ** 2 and
                         (x - cx - 3.2 * s) ** 2 + (y - cy + 1.2 * s) ** 2 > (3.9 * s) ** 2 and y <= cy + 4 * s)
           or (y > cy + 2 * s and y <= 18.2 * s + oy and x >= 1 + ox and x <= 18.6 * s + ox and
               y >= cy + 2 * s + 2.2 * math.sin((x - ox) * 0.5)), m, g=g)
    if foam:
        d.disc(cx + 4.8 * s, cy - 5.2 * s, 1.5 * s, foam, g=g + 1)
        d.disc(cx + 2.4 * s, cy - 6.9 * s, 1.3 * s, foam, g=g + 1)
        d.disc(cx - 0.2 * s, cy - 7.4 * s, 1.2 * s, foam, g=g + 1)


def waves(d, ys=(7, 11, 15), m="water", amp=1.4, w=2.0, x0=2.0, x1=18.0, g=0, period=6.0):
    for y in ys:
        pts = [(x0 + (x1 - x0) * i / 24.0, y + amp * math.sin((x0 + (x1 - x0) * i / 24.0) * 2 * math.pi / period))
               for i in range(25)]
        d.path(pts, w, m, g=g)


def chevron(d, cx, cy, s=4.0, w=2.4, dirx=1, diry=0, m="p", g=0):
    """Chevron (>) apontando para (dirx, diry)."""
    L = math.hypot(dirx, diry)
    ux, uy = dirx / L, diry / L
    px_, py_ = -uy, ux
    tip = (cx + ux * s * 0.5, cy + uy * s * 0.5)
    a = (cx - ux * s * 0.5 + px_ * s, cy - uy * s * 0.5 + py_ * s)
    b = (cx - ux * s * 0.5 - px_ * s, cy - uy * s * 0.5 - py_ * s)
    d.path([a, tip, b], w, m, g=g)


def sparkle(d, cx, cy, r=3.0, m="W", g=0):
    d.star(cx, cy, r, r * 0.3, 4, -90, m, g=g)


def flask(d, cx=10.0, cy=11.0, liquid="mana", glass="glass", cork="wood", g=0, level=0.55):
    d.rect(int(cx - 1.5), int(cy - 8), int(cx + 1.5), int(cy - 3), glass, g=g)
    d.disc(cx, cy + 2.5, 6.2, glass, g=g)
    yl = cy + 2.5 + 6.2 - level * 12.4
    d.fill(lambda x, y: (x - cx) ** 2 + (y - cy - 2.5) ** 2 <= 5.0 ** 2 and y >= yl, liquid, g=g + 1)
    d.rect(int(cx - 2), int(cy - 9.5), int(cx + 2) - 1, int(cy - 8), cork, g=g + 2)
    d.px([(int(cx - 3), int(cy))], glass.split(".")[0] + ".h")


def bag(d, cx=10.0, cy=11.0, m="wood", tie="gold", g=0):
    d.disc(cx, cy + 2.4, 6.6, m, g=g)
    d.poly([(cx - 3.0, cy - 3.0), (cx + 3.0, cy - 3.0), (cx + 5.6, cy + 1), (cx - 5.6, cy + 1)], m, g=g)
    d.poly([(cx - 3.8, cy - 7.4), (cx + 3.8, cy - 7.4), (cx + 1.6, cy - 3.6), (cx - 1.6, cy - 3.6)], m, g=g)
    d.rect(int(cx - 3), int(cy - 4), int(cx + 3) - 1, int(cy - 3), tie, g=g + 1)


def magnet(d, cx=10.0, cy=10.0, m="red", tip="steel", g=0):
    d.arc(cx, cy - 1, 7.4, 3.2, 180, 360, m, g=g)
    d.rect(int(cx - 7.4 + 0.5), int(cy - 1), int(cx - 3.2 - 0.5), int(cy + 4), m, g=g)
    d.rect(int(cx + 3.2 + 0.5), int(cy - 1), int(cx + 7.4 - 0.5), int(cy + 4), m, g=g)
    d.rect(int(cx - 7.4 + 0.5), int(cy + 5), int(cx - 3.2 - 0.5), int(cy + 7.5), tip, g=g + 1)
    d.rect(int(cx + 3.2 + 0.5), int(cy + 5), int(cx + 7.4 - 0.5), int(cy + 7.5), tip, g=g + 1)


def footprint(d, cx, cy, s=1.0, m="p", g=0, flip=False, rot=0.0):
    """Pegada de sapato: sola larga + salto separado, girada em 'rot' graus."""
    sole = rot_pts([(cx, cy - 2.2 * s)], cx, cy, rot)[0]
    heel = rot_pts([(cx, cy + 3.6 * s)], cx, cy, rot)[0]
    d.ellipse(sole[0], sole[1], 2.5 * s, 3.5 * s, m, g=g, rot=rot)
    d.ellipse(heel[0], heel[1], 2.0 * s, 1.6 * s, m, g=g + 1, rot=rot)


def feather(d, x0=4.0, y0=17.0, x1=16.0, y1=3.0, m="white", g=0):
    """Pena: palheta em gota alongada + haste (rachis) escura + entalhes."""
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    px_, py_ = -uy, ux

    def test(x, y):
        k = ((x - x0) * ux + (y - y0) * uy) / L
        if not (0.22 <= k <= 1.0):
            return False
        off = (x - x0) * px_ + (y - y0) * py_
        t = (k - 0.22) / 0.78
        half = 4.6 * math.sin(math.pi * min(1.0, t ** 0.75)) ** 0.9
        return -half * 0.85 <= off <= half
    d.fill(test, m, g=g)
    d.line(x0, y0, x0 + ux * L * 0.96, y0 + uy * L * 0.96, 1.2, m.split(".")[0] + ".d", g=g + 1)
    for k, side in ((0.5, 1), (0.72, -1)):
        bx, by = x0 + ux * L * k, y0 + uy * L * k
        d.line(bx + side * px_ * 1.2, by + side * py_ * 1.2, bx + side * px_ * 5.0 + ux * 1.4,
               by + side * py_ * 5.0 + uy * 1.4, 1.0, None)


def tentacle(d, x0=6.0, y0=18.0, m="purple", g=0, s=1.0, flip=False):
    for i in range(30):
        t = i / 29
        a = t * 4.2
        x = x0 + (6.5 * t + 3.2 * math.sin(a * 1.1)) * s * (-1 if flip else 1)
        y = y0 - 15.0 * t * s
        d.disc(x, y, (3.3 * (1 - t) + 0.9) * s, m, g=g)


def crown(d, cx=10.0, cy=11.0, m="gold", gem="red", g=0, s=1.0):
    pts = [(-7.5, 4.5), (-7.5, -4.5), (-4, -0.5), (0, -6.5), (4, -0.5), (7.5, -4.5), (7.5, 4.5)]
    d.poly(T(pts, cx, cy, s), m, g=g)
    d.rect(int(cx - 7.5 * s + 0.5), int(cy + 3 * s), int(cx + 7.5 * s - 0.5), int(cy + 5.6 * s), m, g=g + 1)
    for gx in (-4.2, 0, 4.2):
        d.disc(cx + gx * s, cy + 4.3 * s, 1.0 * s + 0.25, gem, g=g + 2)
    for (gx, gy) in ((-7.5, -4.8), (0, -6.8), (7.5, -4.8)):
        d.disc(cx + gx * s, cy + gy * s, 1.2 * s, m.split(".")[0] + ".l", g=g + 3)


def tree_canopy(d, cx=10.0, cy=7.0, m="leaf", g=0, s=1.0):
    d.disc(cx - 3.8 * s, cy + 1.5 * s, 4.0 * s, m, g=g)
    d.disc(cx + 3.8 * s, cy + 1.5 * s, 4.0 * s, m, g=g)
    d.disc(cx, cy - 1.5 * s, 4.8 * s, m, g=g)


def mushroom(d, cx=10.0, cy=10.0, s=1.0, cap="red", stem="bone", dots="W", g=0):
    d.rect(int(cx - 2.2 * s), int(cy), int(cx + 2.2 * s) - 1, int(cy + 7.8 * s), stem, g=g)
    d.fill(lambda x, y: ((x - cx) / (8.2 * s)) ** 2 + ((y - cy - 0.6 * s) / (7.6 * s)) ** 2 <= 1 and y <= cy + 0.8 * s,
           cap, g=g + 1)
    if dots:
        for (ox, oy) in ((-4, -2.5), (0.5, -4.8), (4.4, -2.0)):
            d.disc(cx + ox * s, cy + oy * s, 1.1 * s, dots, g=g + 2)


def flower(d, cx=10.0, cy=10.0, r=4.4, pr=3.2, n=5, petal="pink", center="yellow", g=0, rot=-90.0):
    for i in range(n):
        a = math.radians(rot + i * 360 / n)
        d.disc(cx + math.cos(a) * r, cy + math.sin(a) * r, pr, petal, g=g)
    d.disc(cx, cy, 2.4, center, g=g + 1)


HAND_ROWS = [
    "......##......",
    "...##.##.##...",
    "...##.##.##...",
    "...##.##.##.##",
    "...##.##.##.##",
    "...##.##.##.##",
    "...##.##.##.##",
    "##.###########",
    "##.###########",
    ".##.##########",
    "..############",
    "...##########.",
    "....#########.",
    "....########..",
    ".....######...",
    ".....######...",
]


def hand(d, ox=3, oy=2, m="flesh", g=0, flip=False, rows=None):
    rows = rows or HAND_ROWS
    if flip:
        rows = [r[::-1] for r in rows]
    d.sprite(rows, {"#": m}, ox, oy, g=g)


def boot(d, cx=10.0, cy=10.0, m="brown", g=0):
    d.rect(int(cx - 4), int(cy - 7), int(cx + 1), int(cy + 3), m, g=g)
    d.poly([(cx - 4, cy + 1), (cx + 1, cy + 1), (cx + 7.5, cy + 3.5), (cx + 7.5, cy + 7), (cx - 4, cy + 7)], m, g=g)
    d.rect(int(cx - 5), int(cy - 8), int(cx + 2), int(cy - 6), m.split(".")[0] + ".l", g=g + 1)


def wing(d, cx=10.0, cy=10.0, m="white", g=0, flip=False, s=1.0, feathers=3):
    sx = -1 if flip else 1

    def test(x, y):
        u = (x - cx) * sx / s
        v = (y - cy) / s
        if u < -1 or u > 8.5 or v < -7.5 or v > 5.5:
            return False
        top = -7.0 + u * 0.35
        bottom = 5.0 - u * 0.9 - (1.6 if int((u + 1) / 2.6) % 2 else 0)
        return top <= v <= bottom and u ** 2 / 81 + (v + 1) ** 2 / 46 <= 1.25
    d.fill(test, m, g=g)
    for i in range(1, feathers):
        y0 = cy + (-3 + i * 2.6) * s
        d.line(cx + sx * 1.5 * s, y0, cx + sx * (6.6 - i * 1.2) * s, y0 + 1.6 * s, 0.9, m.split(".")[0] + ".d", g=g)


def bat_wing(d, cx=10.0, cy=10.0, m="dark", g=0, flip=False, s=1.0):
    sx = -1 if flip else 1
    pts = [(0.2, -2.0), (2.6, -5.6), (5.0, -6.4), (9.8, -9.2), (9.6, 2.6), (8.2, -0.6), (6.6, 4.6), (5.0, 0.2),
           (3.2, 5.2), (1.8, 1.0), (0.2, 3.0)]
    d.poly([(cx + sx * x * s, cy + y * s) for x, y in pts], m, g=g)
    for (x, y) in ((9.6, 2.6), (6.6, 4.6), (3.2, 5.2)):
        d.line(cx + sx * 4.6 * s, cy - 5.8 * s, cx + sx * x * s, cy + y * s, 0.8, m.split(".")[0] + ".d", g=g + 1)
    d.line(cx + sx * 0.4 * s, cy - 2.0 * s, cx + sx * 4.6 * s, cy - 5.8 * s, 1.0, m.split(".")[0] + ".d", g=g + 1)
    d.tri((cx + sx * 4.4 * s, cy - 6.0 * s), (cx + sx * 4.0 * s, cy - 8.4 * s), (cx + sx * 5.6 * s, cy - 6.4 * s),
          "bone", g=g + 2)


def padlock(d, cx=10.0, cy=11.0, m="gold", shackle="steel", g=0):
    d.ring(cx, cy - 3, 5.0, 2.8, shackle, g=g)
    d.rect(int(cx - 7), int(cy - 2), int(cx + 7) - 1, int(cy + 7.5), m, g=g + 1)
    d.disc(cx, cy + 1.7, 1.5, "k")
    d.rect(int(cx), int(cy + 2), int(cx), int(cy + 5), "k")


def scroll(d, cx=10.0, cy=10.0, m="paper", g=0, lines=True):
    d.rect(int(cx - 6), int(cy - 6), int(cx + 6) - 1, int(cy + 6), m, g=g)
    d.ellipse(cx - 6, cy, 1.8, 7.2, m.split(".")[0] + ".d", g=g + 1)
    d.ellipse(cx + 6, cy, 1.8, 7.2, m.split(".")[0] + ".d", g=g + 1)
    if lines:
        for yy in (-3, 0, 3):
            d.rect(int(cx - 3.5), int(cy + yy), int(cx + 3.5) - 1, int(cy + yy), m.split(".")[0] + ".d")


def book(d, cx=10.0, cy=10.0, cover="red", pages="paper", g=0, emblem="gold"):
    d.rect(int(cx - 7), int(cy - 7.5), int(cx + 6), int(cy + 7.5), cover, g=g)
    d.rect(int(cx - 4.5), int(cy + 5), int(cx + 6), int(cy + 7), pages, g=g + 1)
    d.rect(int(cx - 7), int(cy - 7.5), int(cx - 5), int(cy + 7.5), cover.split(".")[0] + ".d", g=g + 2)
    if emblem:
        d.star(cx + 0.8, cy - 1.2, 3.6, 1.4, 4, -90, emblem, g=g + 3)


def ground(d, y=17, m="dirt", x0=1, x1=18, g=0):
    d.rect(x0, y, x1, 18, m, g=g)


# ---------------------------------------------------------------------------------------------- FORMAS
@pic("projectile")
def _(d):
    d.line(11.0, 9.0, 1.8, 18.2, 3.2, "p.d")
    d.line(13.2, 10.6, 6.6, 17.4, 1.8, "p")
    d.line(9.6, 6.8, 3.0, 12.6, 1.8, "p")
    orb(d, 13.0, 7.0, 4.8, "p", g=2)
    d.disc(13.0, 7.0, 2.4, "W")
    sparkle(d, 4.4, 4.6, 2.4, "p.h")


@pic("touch")
def _(d):
    hand(d, 3, 3, "p")
    for (x0, y0, x1, y1) in ((4.0, 3.0, 2.0, 1.0), (9.5, 1.6, 9.5, 0.2), (15.5, 3.0, 17.6, 1.0)):
        d.line(x0, y0, x1, y1, 1.2, "W")


@pic("self")
def _(d):
    figure(d, "stand", 10, "p", oy=1)
    chevron(d, 2.6, 10, 3.0, 1.8, 1, 0, "W")
    chevron(d, 17.4, 10, 3.0, 1.8, -1, 0, "W")
    d.ellipse(10, 18.0, 6.5, 1.3, "p.h", g=5)


@pic("beam")
def _(d):
    d.line(5.0, 15.0, 18.6, 1.4, 5.4, "p")
    d.line(5.0, 15.0, 18.6, 1.4, 2.2, "W")
    d.disc(5.0, 15.0, 4.4, "p", g=2)
    d.disc(5.0, 15.0, 2.2, "W")
    sparkle(d, 15.5, 13.5, 2.2, "p.h")


@pic("cone")
def _(d):
    d.arc(2.2, 10, 17.2, 0, -27, 27, "p")
    for r in (7.0, 12.0):
        d.arc(2.2, 10, r + 0.7, r - 0.7, -24, 24, "p.h")
    d.disc(2.6, 10, 2.2, "W")


@pic("slash")
def _(d):
    crescent(d, 7.0, 13.0, 11.6, -3.4, 3.4, 11.0, "p")
    crescent(d, 7.0, 13.0, 9.0, -2.6, 2.6, 8.7, "p.l")
    d.line(2.0, 4.0, 4.6, 2.6, 1.0, "W")


@pic("nova")
def _(d):
    burst(d, 10, 10, 9.8, 6.6, 8, "p", -90, (1.0, 0.8))
    d.disc(10, 10, 5.0, None)
    d.ring(10, 10, 4.9, 3.6, "p.d")
    d.disc(10, 10, 2.0, "W")


@pic("mark")
def _(d):
    d.ring(10, 10, 7.8, 5.8, "p")
    for (x0, y0, x1, y1) in ((10, 0.8, 10, 5.4), (10, 14.6, 10, 19.2), (0.8, 10, 5.4, 10), (14.6, 10, 19.2, 10)):
        d.line(x0, y0, x1, y1, 2.0, "p", g=1)
    d.disc(10, 10, 2.0, "red")


@pic("wave")
def _(d):
    # onda que CORRE pelo chao: seta ondulada sobre a linha do chao + tracos de velocidade
    d.rect(0, 17, 19, 18, "p.d", g=0)
    pts = [(1.0 + i * 0.5, 10.0 - 3.4 * math.sin((i * 0.5) / 13.0 * 2 * math.pi * 1.0)) for i in range(27)]
    d.path(pts, 2.6, "p.l", g=1)
    d.poly([(19.2, 10.0), (13.4, 5.4), (13.4, 14.6)], "p.l", g=1)
    for i, (x, y) in enumerate(((2, 14), (6, 15), (10, 14))):
        d.rect(x, y, x + 2, y, "p", g=3 + i)


@pic("fall")
def _(d):
    d.poly([(10.6, 16.6), (3.4, 9.4), (18.8, 1.2)], "fire")
    d.poly([(9.6, 14.6), (5.4, 10.4), (16.5, 3.5)], "flame", g=1)
    d.disc(7.0, 13.0, 4.8, "stone", g=2)
    d.path([(5.0, 12.0), (7.0, 13.4), (8.4, 11.6)], 1.0, "lava.l", g=3)
    d.path([(6.4, 15.6), (7.4, 14.0)], 1.0, "lava.l", g=3)


@pic("rain")
def _(d):
    cloud(d, 10, 6.0, 1.0, "p")
    for (x, y) in ((4.5, 13), (9.0, 15.5), (13.5, 13), (17.0, 16.0), (6.0, 18.0)):
        d.line(x + 1.2, y - 2.4, x - 0.2, y + 0.4, 1.6, "p.h", g=4)


@pic("zone")
def _(d):
    d.ellipse(10, 13.6, 9.2, 4.8, "p")
    d.ellipse(10, 13.6, 6.6, 3.0, "q")
    d.ellipse(10, 13.6, 3.6, 1.4, "p.l")
    for (x, y0, y1) in ((4.0, 10.0, 5.6), (10.0, 8.0, 1.6), (16.0, 10.0, 5.0)):
        d.line(x, y0, x, y1, 1.3, "p.h", g=3)


@pic("wall")
def _(d):
    gi = 0
    for row in range(4):
        y0 = 3 + row * 4
        off = 0 if row % 2 == 0 else -3
        x = 1 + off
        while x <= 18:
            gi += 1
            d.rect(max(1, x), y0, min(18, x + 4), y0 + 2, "p", g=gi)
            x += 6


@pic("rune")
def _(d):
    d.ring(10, 10, 8.8, 7.0, "p")
    d.path([(10, 3.6), (15.6, 13.4), (4.4, 13.4), (10, 3.6)], 1.6, "p.l", g=1)
    d.disc(10, 10.4, 1.8, "W")
    for a in (30, 150, 270):
        d.disc(10 + 7.9 * math.cos(math.radians(a)), 10 + 7.9 * math.sin(math.radians(a)), 1.2, "W")


@pic("pulse")
def _(d):
    d.disc(10, 10, 2.8, "p")
    d.ring(10, 10, 6.4, 4.8, "p", g=1)
    for a0 in (20, 110, 200, 290):
        d.arc(10, 10, 9.6, 8.2, a0, a0 + 60, "p.l", g=2)
    d.put(9, 9, "W")


@pic("orbit")
def _(d):
    chain_link(d, 10, 10, 9.0, 4.0, 1.2, -25, "p.d")
    orb(d, 10, 10, 3.8, "p")
    for t in (0.6, 3.6):
        u, v = 9.0 * math.cos(t), 4.0 * math.sin(t)
        a = math.radians(-25)
        x, y = 10 + u * math.cos(a) - v * math.sin(a), 10 + u * math.sin(a) + v * math.cos(a)
        orb(d, x, y, 2.3, "W" if t < 1 else "p.l", g=4)


@pic("aura")
def _(d):
    # conjurador dentro de um halo oval que irradia (aneis + raios curtos)
    for i in range(12):
        a = math.radians(i * 30)
        d.line(10 + 8.0 * math.cos(a), 10.4 + 8.8 * math.sin(a), 10 + 9.6 * math.cos(a), 10.4 + 10.0 * math.sin(a),
               1.2, "p.l", g=7)
    d.ellipse(10, 10.4, 7.4, 8.4, "p.l", g=1)
    d.ellipse(10, 10.4, 5.8, 6.8, "p.d", g=2)
    figure(d, "stand", 10, "W", oy=1)


@pic("seal")
def _(d):
    sword(d, 3, 17, 17, 3)
    d.ring(10.5, 9.5, 5.4, 4.0, "p", g=5)
    for a in (0, 90, 180, 270):
        d.disc(10.5 + 4.7 * math.cos(math.radians(a + 45)), 9.5 + 4.7 * math.sin(math.radians(a + 45)), 1.0, "W")


@pic("turret")
def _(d):
    d.poly([(4, 18.6), (16, 18.6), (14, 14.6), (6, 14.6)], "stone")
    d.rect(7, 13, 12, 14, "stone.l")
    crystal(d, 10, 6.6, 5.8, 3.2, "p")
    d.line(14.8, 6.6, 18.6, 6.6, 1.4, "W")
    d.line(1.4, 6.6, 5.2, 6.6, 1.4, "W")


@pic("conjure")
def _(d):
    chain_link(d, 10, 15.6, 9.0, 3.2, 1.6, 0, "p")
    ghost(d, 10, 7.4, 0.86, "p.l", eyes="k")
    d.px([(3, 13), (17, 13), (5, 11), (15, 11)], "W")


@pic("illusion")
def _(d):
    figure(d, "stand", 13.5, "p.d", oy=0)
    figure(d, "stand", 7.0, "p", oy=2, g=3)
    sparkle(d, 16.5, 16.0, 2.2, "W")


@pic("portal")
def _(d):
    d.ellipse(10, 10, 7.4, 9.6, "p")
    d.ellipse(10, 10, 4.8, 7.0, "q.d")
    spiral(d, 10, 10, 0.6, 4.4, 1.25, 1.4, "p.l", 0)


@pic("cataclysm")
def _(d):
    d.ellipse(10, 16.0, 9.6, 3.0, "p.d")
    d.ellipse(10, 16.0, 6.4, 1.8, "q")
    d.rect(7, 0, 12, 15, "p.l", g=2)
    d.rect(9, 0, 10, 15, "W")
    for (x, y) in ((3, 12), (16, 11), (2, 9), (17, 8)):
        d.rect(x, y, x + 1, y + 1, "stone", g=4)


@pic("domain_expansion")
def _(d):
    d.fill(lambda x, y: (x - 10) ** 2 + (y - 15) ** 2 <= 9.0 ** 2 and y <= 15, "q")
    d.fill(lambda x, y: 7.4 ** 2 <= (x - 10) ** 2 + (y - 15) ** 2 <= 9.0 ** 2 and y <= 15, "p", g=1)
    d.fill(lambda x, y: y <= 15 and abs(((x - 10) / 4.2) ** 2 + ((y - 15) / 8.4) ** 2 - 1) < 0.22, "p.l", g=2)
    d.rect(1, 15, 18, 16, "p", g=3)
    d.ellipse(10, 17.5, 8.6, 1.6, "p.d", g=4)
    d.disc(10, 7.0, 1.4, "W")


@pic("simple_domain")
def _(d):
    d.fill(lambda x, y: 7.2 ** 2 <= (x - 10) ** 2 + (y - 16.5) ** 2 <= 9.2 ** 2 and y <= 16.5, "p")
    vfig(d, 10, 16.4, 9.6, "W", g=3)
    d.ellipse(10, 17.6, 9.4, 1.5, "p.d", g=4)
    for a in (205, 270, 335):
        r = math.radians(a)
        d.disc(10 + 8.2 * math.cos(r), 16.5 + 8.2 * math.sin(r), 1.0, "W")


@pic("arsenal_gate")
def _(d):
    d.ellipse(10, 4.0, 9.6, 3.6, "gold")
    d.ellipse(10, 4.0, 7.6, 2.0, "void", g=1)
    for i, (x0, x1, y1) in enumerate(((5.0, 2.8, 15.0), (10.0, 10.0, 18.6), (15.0, 17.2, 15.0))):
        d.line(x0, 4.0, x1, y1 - 2.0, 2.6, "steel", g=2 + i)
        tri_tip(d, x1, y1 - 2.0, x0, 4.0, "steel", g=2 + i, size=3.0)
        gx = x0 + (x1 - x0) * 0.22
        d.line(gx - 2.0, 7.6, gx + 2.0, 7.6, 1.4, "gold", g=6 + i)
    d.recolor(lambda x, y: y < 4.6 and ((x - 10) / 7.6) ** 2 + ((y - 4.0) / 2.0) ** 2 > 1, "gold", only="steel")
    d.recolor(lambda x, y: y < 5.2 and ((x - 10) / 7.6) ** 2 + ((y - 4.0) / 2.0) ** 2 <= 1, "void", g=1, only="steel")
    sparkle(d, 1.6, 9.6, 1.8, "W", g=12)
    sparkle(d, 18.4, 9.0, 1.8, "W", g=12)


@pic("converge")
def _(d):
    d.curve([(4.5, 4.5), (10, 3.5), (13.5, 8.6)], 1.8, "red.l")
    d.curve([(4.5, 15.5), (10, 16.5), (13.5, 11.4)], 1.8, "blue.l")
    orb(d, 4.5, 4.5, 3.2, "red", g=2)
    orb(d, 4.5, 15.5, 3.2, "blue", g=3)
    orb(d, 13.6, 10.0, 5.4, "purple", g=4)
    d.disc(13.6, 10.0, 2.4, "magenta.h")


@pic("sky_lances")
def _(d):
    for i, tx in enumerate((2.6, 10.0, 17.4)):
        d.line(10, 4.4, tx, 16.6, 1.5, "p.h", g=1 + i)
        sparkle(d, tx, 16.8, 2.6, "yellow", g=5 + i)
    d.ellipse(10, 3.4, 7.6, 2.6, "p")
    d.ellipse(10, 3.4, 5.0, 1.4, "glass", g=9)


@pic("spiral_sphere")
def _(d):
    # esfera espiral GIRANDO sobre a palma da mao (nao confundir com o portal oval)
    d.poly([(3.0, 16.4), (17.0, 16.4), (15.4, 19.6), (4.6, 19.6)], "p.d", g=20)
    for i, x in enumerate((4.4, 7.6, 10.8, 14.0)):
        d.ellipse(x + 0.6, 15.6, 1.5, 1.3, "p.d", g=20)
    orb(d, 10, 8.0, 6.2, "sky", shine=False)
    spiral(d, 10, 8.0, 0.6, 5.4, 1.4, 1.4, "W", 200, g=3)
    d.arc(10, 8.0, 8.8, 7.8, 200, 250, "W", g=6)
    d.arc(10, 8.0, 8.8, 7.8, 20, 70, "W", g=7)
    d.arc(10, 8.0, 8.8, 7.8, 290, 320, "sky.l", g=8)


def tri_tip(d, x, y, fx, fy, m, g=0, size=3.0):
    """Ponta triangular em (x,y) apontando para longe de (fx,fy)."""
    L = math.hypot(x - fx, y - fy) or 1
    ux, uy = (x - fx) / L, (y - fy) / L
    d.poly([(x + ux * size, y + uy * size), (x - uy * 1.6, y + ux * 1.6), (x + uy * 1.6, y - ux * 1.6)], m, g=g)


# ---------------------------------------------------------------------------------------------- AUMENTOS
@pic("amplify")
def _(d):
    plus(d, 10, 10, 8.0, 5.0, "p")
    d.put(8, 3, "W")


@pic("dampen")
def _(d):
    d.rect(2, 8, 17, 11, "p")
    chevron(d, 10, 15.6, 3.4, 1.8, 0, 1, "p.d", g=1)
    chevron(d, 10, 4.4, 3.4, 1.8, 0, 1, "p.d", g=1)


@pic("concentrate")
def _(d):
    for (sx, sy) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        d.arrow(10 + sx * 9.0, 10 + sy * 9.0, 10 + sx * 3.4, 10 + sy * 3.4, 2.2, 3.6, "p")
    d.disc(10, 10, 2.0, "W")


@pic("expand")
def _(d):
    for (sx, sy) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        d.arrow(10 + sx * 2.6, 10 + sy * 2.6, 10 + sx * 9.0, 10 + sy * 9.0, 2.2, 3.8, "p")
    d.rect(8, 8, 11, 11, "W")


@pic("critical")
def _(d):
    burst(d, 10, 10, 9.6, 5.4, 8, "yellow", -90, (1.0, 0.82))
    d.rect(9, 4, 10, 10, "k")
    d.rect(9, 12, 10, 13, "k")


@pic("vampiric")
def _(d):
    d.poly([(1.4, 2.4), (18.6, 2.4), (17.0, 7.0), (3.0, 7.0)], "blood")
    d.tri((4.6, 6.0), (8.4, 6.0), (6.6, 13.0), "white", g=1)
    d.tri((11.6, 6.0), (15.4, 6.0), (13.4, 13.0), "white", g=2)
    drop(d, 13.4, 16.0, 2.4, "blood", 1.6, shine=False, g=3)


@pic("overload")
def _(d):
    d.heart(10, 10.6, 1.05, "heart")
    bolt(d, 10.4, 10.2, 0.78, "yellow", g=2)


@pic("efficient")
def _(d):
    drop(d, 7.6, 9.6, 5.6, "mana")
    d.arrow(16.0, 2.0, 16.0, 17.6, 2.4, 4.0, "p", g=3)


@pic("extend")
def _(d):
    hourglass(d, 9, 10, 0.95, top=0.75, bot=0.25)


@pic("shorten")
def _(d):
    hourglass(d, 9, 10, 0.95, top=0.12, bot=0.8)


@pic("persistent")
def _(d):
    d.ring(5.6, 10, 4.8, 2.4, "p")
    d.ring(14.4, 10, 4.8, 2.4, "p", g=1)
    d.put(9, 9, "W")


@pic("delay")
def _(d):
    d.rect(8, 0, 11, 1, "p.d")
    d.disc(10, 11, 8.0, "p")
    d.disc(10, 11, 6.0, "white", g=1)
    d.line(10, 11, 10, 6.4, 1.4, "k")
    d.line(10, 11, 13.6, 12.8, 1.4, "k")
    d.disc(10, 11, 1.2, "red", g=3)


@pic("echo")
def _(d):
    d.disc(3.8, 10, 2.8, "p")
    for i, (r, m) in enumerate(((6.8, "p"), (10.8, "p.m"), (14.6, "p.d"))):
        d.arc(3.0, 10, r + 1.0, r - 1.0, -48, 48, m, g=i + 1)


@pic("accelerate")
def _(d):
    chevron(d, 6.4, 10, 7.4, 3.4, 1, 0, "p")
    chevron(d, 13.4, 10, 7.4, 3.4, 1, 0, "p.l", g=1)


@pic("decelerate")
def _(d):
    d.fill(lambda x, y: 1.5 <= x <= 18.5 and 14 <= y <= 17 and not (x > 15 and y < 15 + (x - 15) * 0.7), "flesh")
    d.disc(16.6, 13.6, 2.0, "flesh")
    d.line(16.0, 12.5, 15.0, 9.0, 0.9, "flesh.d")
    d.line(17.4, 12.5, 18.2, 9.4, 0.9, "flesh.d")
    d.disc(8.6, 9.6, 6.4, "p", g=2)
    spiral(d, 8.6, 9.6, 0.5, 5.0, 1.6, 1.2, "p.d", 0, g=3)


@pic("split")
def _(d):
    d.line(10, 19, 10, 10, 2.6, "p")
    d.arrow(10, 11, 10, 1.4, 2.4, 4.0, "p", g=1)
    d.arrow(10, 11, 2.6, 3.4, 2.4, 4.0, "p", g=2)
    d.arrow(10, 11, 17.4, 3.4, 2.4, 4.0, "p", g=3)


@pic("scatter")
def _(d):
    d.disc(3.0, 16.0, 2.8, "p")
    for i, (x, y, r) in enumerate(((10, 14.6, 1.7), (9.0, 8.6, 1.7), (14.0, 4.0, 1.9), (16.6, 10.0, 1.9),
                                   (13.6, 16.4, 1.6), (5.8, 4.6, 1.5), (17.4, 16.6, 1.3))):
        d.disc(x, y, r, "p.l", g=i + 1)


@pic("pierce")
def _(d):
    d.line(1.4, 10, 9.0, 10, 2.2, "p")
    d.ellipse(10.0, 10.0, 2.6, 8.4, "wood", g=1)
    d.arrow(11.0, 10, 19.0, 10, 2.2, 4.2, "p", g=2)
    d.px([(13, 5), (14, 4), (13, 15), (14, 16)], "wood.l")


@pic("ricochet")
def _(d):
    d.rect(15, 1, 18, 18, "stone")
    d.line(15, 6, 18, 6, 1.0, "stone.d", g=1)
    d.line(15, 12, 18, 12, 1.0, "stone.d", g=1)
    d.line(2.0, 17.6, 13.4, 10.0, 2.2, "p", g=2)
    d.arrow(13.4, 10.0, 2.4, 2.6, 2.2, 4.0, "p", g=3)
    sparkle(d, 13.6, 10.0, 2.6, "W")


@pic("chain")
def _(d):
    chain_link(d, 5.0, 15.0, 5.2, 3.3, 1.8, -45, "steel")
    d.line(7.4, 12.6, 12.6, 7.4, 2.6, "steel", g=1)
    chain_link(d, 15.0, 5.0, 5.2, 3.3, 1.8, -45, "steel", g=2)


@pic("homing")
def _(d):
    d.ring(14.2, 6.0, 4.8, 3.2, "red")
    d.disc(14.2, 6.0, 1.6, "red", g=1)
    d.curve([(1.8, 17.5), (2.4, 6.0), (9.0, 8.5)], 2.2, "p", g=2)
    tri_tip(d, 9.4, 8.2, 6.4, 7.4, "p", g=2, size=3.4)


@pic("phantom")
def _(d):
    gi = 0
    for row in range(5):
        y0 = 1 + row * 4
        gi += 1
        d.rect(7, y0, 12, y0 + 2, "stone", g=gi)
    for x0 in (1.0, 5.0, 9.0):
        d.line(x0, 10, x0 + 2.4, 10, 2.2, "p.h", g=10)
    d.arrow(13.0, 10, 19.0, 10, 2.2, 4.2, "p", g=11)


@pic("arc")
def _(d):
    d.rect(1, 18, 18, 18, "p.d")
    d.curve([(2.6, 16.0), (9.0, -6.0), (15.6, 13.0)], 2.2, "p", g=1)
    tri_tip(d, 16.2, 15.0, 14.6, 11.0, "p", g=1, size=3.4)
    d.disc(2.6, 16.0, 1.8, "W", g=2)


@pic("sensitive")
def _(d):
    d.fill(lambda x, y: (x - 10) ** 2 + (y - 14) ** 2 <= 9.0 ** 2 and y <= 14.5, "p")
    d.fill(lambda x, y: (x - 10) ** 2 + (y - 14) ** 2 <= 6.6 ** 2 and y <= 14.5, "q.d", g=1)
    for a in range(200, 341, 35):
        r = math.radians(a)
        d.line(10 + 6.8 * math.cos(r), 14 + 6.8 * math.sin(r), 10 + 8.6 * math.cos(r), 14 + 8.6 * math.sin(r), 1.0, "W")
    d.line(10, 14, 5.0, 9.6, 1.5, "red", g=3)
    d.disc(10, 14, 1.8, "W")
    d.rect(1, 15, 18, 16, "p.d", g=4)


@pic("enemies_only")
def _(d):
    d.ring(10, 10.5, 8.8, 7.2, "red")
    d.disc(10, 11.2, 5.0, "dark", g=1)
    d.tri((5.6, 9.0), (4.0, 2.8), (8.2, 7.4), "dark", g=1)
    d.tri((14.4, 9.0), (16.0, 2.8), (11.8, 7.4), "dark", g=1)
    d.rect(7, 10, 8, 11, "red.l")
    d.rect(12, 10, 13, 11, "red.l")


@pic("allies_only")
def _(d):
    # par de "enemies_only": aro VERDE com um aliado (busto claro) e coracao
    d.ring(10, 10.5, 8.8, 7.2, "green")
    d.disc(10, 7.6, 2.8, "sky", g=1)
    d.fill(lambda x, y: ((x - 10) / 5.0) ** 2 + ((y - 15.6) / 4.2) ** 2 <= 1 and
           (x - 10) ** 2 + (y - 10.5) ** 2 <= 7.2 ** 2, "sky", g=2)
    d.heart(15.8, 15.6, 0.42, "heart", g=6)


@pic("invert")
def _(d):
    d.arrow(6.0, 18.6, 6.0, 1.6, 2.6, 4.4, "p")
    d.arrow(14.0, 1.4, 14.0, 18.4, 2.6, 4.4, "p.l", g=1)


@pic("charge")
def _(d):
    d.rect(2, 5, 15, 15, "steel")
    d.rect(16, 8, 17, 12, "steel.d", g=1)
    d.rect(4, 7, 13, 13, "k")
    for i, x in enumerate((4, 7, 10)):
        d.rect(x + 0, 7, x + 2, 13, "green", g=2 + i)


@pic("channel")
def _(d):
    orb(d, 14.6, 10, 4.4, "p", g=0)
    d.disc(14.6, 10, 2.0, "W", g=1)
    for i, (y0, ph) in enumerate(((3.0, 0.0), (10.0, 1.6), (17.0, 3.2))):
        pts = [(1.0 + t * 11.0, y0 + (10 - y0) * t ** 1.6 + 1.2 * math.sin(t * math.pi * 3 + ph) * (1 - t))
               for t in [k / 20 for k in range(21)]]
        d.path(pts, 1.8, "p.l" if i != 1 else "W", g=3 + i)


@pic("silent")
def _(d):
    d.rect(1, 7, 4, 12, "p")
    d.poly([(4, 7), (9.6, 2.4), (9.6, 17.6), (4, 13)], "p", g=1)
    d.line(12.0, 6.0, 18.4, 13.6, 2.2, "red", g=2)
    d.line(18.4, 6.0, 12.0, 13.6, 2.2, "red", g=2)


@pic("fortune")
def _(d):
    for (ox, oy) in ((-3.2, -3.2), (3.2, -3.2), (-3.2, 3.2), (3.2, 3.2)):
        d.disc(10 + ox, 9 + oy, 3.8, "green", g=1 if ox * oy > 0 else 2)
    d.disc(10, 9, 1.6, "green.d", g=3)
    d.curve([(10, 10), (11.5, 15), (15.5, 18.4)], 1.6, "green.d", g=4)


@pic("silk_touch")
def _(d):
    d.rect(6, 5, 13, 15, "pink")
    for yy in (7, 9, 11, 13):
        d.rect(6, yy, 13, yy, "pink.d")
    d.ellipse(9.5, 4.0, 6.4, 2.2, "wood", g=1)
    d.ellipse(9.5, 16.0, 6.4, 2.2, "wood", g=2)
    d.curve([(13.5, 12.0), (18.4, 12.5), (17.0, 18.6)], 1.2, "pink.l", g=3)


@pic("all_in")
def _(d):
    cube(d, 9.6, 10.6, 1.0, "white")
    for (x, y) in ((9.6, 3.2), (6.4, 4.6), (12.8, 4.6)):
        d.ellipse(x, y, 1.1, 0.7, "k")
    for (x, y) in ((4.4, 9.6), (6.0, 13.6), (14.8, 9.0), (12.8, 12.4), (11.2, 15.6)):
        d.disc(x, y, 0.9, "red" if x < 9.6 else "k", g=8)
    d.rect(17, 1, 18, 7, "yellow", g=9)
    d.rect(17, 9, 18, 10, "yellow", g=9)


@pic("colossal")
def _(d):
    d.fill(lambda x, y: 1 <= x <= 19 and 1 <= y <= 19 and not (3.6 <= x <= 16.4 and 3.6 <= y <= 16.4), "p.l")
    d.rect(1, 12, 8, 18, "p", g=1)
    d.arrow(7.6, 12.4, 15.4, 4.6, 2.2, 4.0, "W", g=2)


@pic("relentless")
def _(d):
    pts = [(10.0, 3.0), (16.4, 14.6), (3.6, 14.6)]
    cx, cy, r = 10.0, 10.6, 7.2
    for a0 in (-60, 60, 180):
        d.arc_arrow(cx, cy, r, a0 + 20, a0 + 95, 1.8, 3.2, "p")
    for i, (x, y) in enumerate(pts):
        d.disc(x, y, 2.4, "red", g=5 + i)


# ---------------------------------------------------------------------------------------------- GATILHOS
@pic("on_hurt")
def _(d):
    d.heart(10, 11.0, 0.86, "heart")
    d.path([(10.6, 5.6), (8.6, 9.0), (11.4, 11.4), (9.0, 15.4)], 1.2, "k")


@pic("on_low_health")
def _(d):
    d.heart(10, 11.0, 0.86, "dark")
    d.recolor(lambda x, y: y >= 13.0, "heart", g=1)
    d.rect(9, 5, 10, 10, "W")
    d.rect(9, 12, 10, 13, "W")


@pic("on_kill")
def _(d):
    skull(d, 10, 10.6, 0.86)


@pic("on_crit")
def _(d):
    sword(d, 4, 17, 14.4, 6.6, w=2.4)
    sparkle(d, 15.2, 5.2, 4.6, "yellow")
    d.disc(15.2, 5.2, 1.0, "W")


@pic("then")
def _(d):
    d.disc(5.0, 11.0, 4.0, "white")
    d.ring(5.0, 11.0, 4.0, 3.0, "p", g=1)
    d.line(5.0, 11.0, 5.0, 8.4, 1.0, "k")
    d.line(5.0, 11.0, 6.8, 11.0, 1.0, "k")
    d.arrow(10.4, 11.0, 18.8, 11.0, 2.4, 4.0, "p", g=3)


@pic("together")
def _(d):
    d.arrow(6.0, 18.0, 6.0, 4.0, 2.6, 4.0, "p")
    d.arrow(14.0, 18.0, 14.0, 4.0, 2.6, 4.0, "p", g=1)
    d.rect(4, 12, 15, 13, "W")


@pic("trigger_sneak")
def _(d):
    figure(d, "crouch", 10, "p", oy=2)
    for x in (2, 5):
        d.put(x, 17, "p.d")


@pic("trigger_sprint")
def _(d):
    figure(d, "run", 11.5, "p", oy=2)
    for (y, x0, x1) in ((8, 1, 4), (11, 0, 3), (14, 1, 3)):
        d.line(x0, y, x1, y, 1.0, "W")


@pic("trigger_air")
def _(d):
    figure(d, "jump", 10, "p", oy=0)
    d.ellipse(10, 17.6, 5.0, 1.0, "p.d", g=3)


@pic("trigger_water")
def _(d):
    d.disc(10, 7.0, 3.0, "p")
    d.line(4.0, 12.0, 7.4, 8.6, 2.2, "p", g=1)
    d.line(16.0, 12.0, 12.6, 8.6, 2.2, "p", g=1)
    waves(d, (12.4, 16.4), "water", 1.0, 2.0, 1.0, 19.0, g=3, period=5.0)


# ---------------------------------------------------------------------------------------------- LIGACOES
@pic("link_hit")
def _(d):
    burst(d, 13.4, 10, 6.4, 3.2, 8, "yellow", -90, (1.0, 0.75))
    d.disc(13.4, 10, 1.6, "W")
    d.arrow(0.6, 10, 8.0, 10, 2.4, 3.6, "p", g=2)


@pic("link_end")
def _(d):
    d.arrow(1.0, 10, 12.4, 10, 2.4, 4.0, "p")
    d.rect(14, 3, 16, 17, "p.l", g=1)


@pic("link_kill")
def _(d):
    bone(d, 3.0, 4.0, 17.0, 16.6, "bone", 2.0, 1.5)
    bone(d, 17.0, 4.0, 3.0, 16.6, "bone", 2.0, 1.5, g=1)
    skull(d, 10, 9.6, 0.78, g=2)


@pic("link_leap")
def _(d):
    d.disc(4.0, 14.6, 2.8, "red")
    d.disc(16.0, 14.6, 2.8, "red", g=1)
    d.arc_arrow(10, 14.6, 6.2, 180, 340, 2.0, 3.4, "p")


# ---------------------------------------------------------------------------------------------- LEIS
@pic("law_sure_hit")
def _(d):
    eye(d, 10, 10, 0.95)
    for (x0, y0, x1, y1) in ((10, 0.6, 10, 3.6), (10, 16.4, 10, 19.4), (0.4, 10, 1.6, 10), (18.4, 10, 19.6, 10)):
        d.line(x0, y0, x1, y1, 2.0, "red", g=4)


@pic("law_no_escape")
def _(d):
    padlock(d, 10, 11.0)


@pic("law_territory")
def _(d):
    d.ellipse(10, 17.0, 8.0, 2.2, "grass")
    d.rect(5, 2, 6, 17, "wood", g=1)
    d.poly([(7, 2.4), (17.6, 4.4), (14.4, 7.0), (17.6, 9.6), (7, 9.6)], "p", g=2)
    d.disc(5.5, 1.6, 1.3, "gold", g=3)


@pic("law_nullify")
def _(d):
    sparkle(d, 10, 10, 6.4, "p")
    d.ring(10, 10, 9.0, 7.0, "red", g=1)
    d.line(4.4, 15.6, 15.6, 4.4, 2.0, "red", g=1)


@pic("law_slow_time")
def _(d):
    d.disc(10, 10, 8.4, "white")
    d.ring(10, 10, 8.4, 7.0, "gold", g=1)
    spiral(d, 10, 10, 0.4, 5.6, 1.6, 1.0, "violet", 0, g=2)
    d.line(10, 10, 10, 4.2, 1.4, "k")
    d.line(10, 10, 14.2, 10, 1.4, "k")


@pic("law_gravity")
def _(d):
    for i, x in enumerate((4.0, 10.0, 16.0)):
        d.arrow(x, 0.8 + (1.6 if i != 1 else 0), x, 13.2, 2.2, 3.8, "p", g=i)
    d.rect(1, 15, 18, 17, "stone", g=5)


@pic("law_landscape")
def _(d):
    d.poly([(0.5, 17.6), (7.0, 5.6), (13.0, 17.6)], "stone")
    d.poly([(5.0, 8.6), (7.0, 5.6), (9.0, 8.6), (7.6, 8.0), (7.0, 9.2), (6.2, 8.0)], "white", g=1)
    d.poly([(8.0, 17.6), (13.6, 9.0), (19.5, 17.6)], "stone.d", g=2)
    d.disc(15.6, 4.0, 2.6, "yellow", g=3)
    d.rect(0, 17, 19, 18, "grass", g=4)


@pic("law_open_barrier")
def _(d):
    d.arc(10, 15, 8.2, 6.6, 180, 245, "p")
    d.arc(10, 15, 8.2, 6.6, 295, 360, "p")
    d.rect(1, 15, 18, 16, "p.d", g=2)
    d.arrow(10, 12, 10, 1.2, 2.2, 3.6, "W", g=3)


@pic("law_absolute")
def _(d):
    crown(d, 10, 11.0, "gold", "red")

def vfig(d, cx=10.0, fy=18.6, h=16.0, m="p", g=0, arms="down"):
    """Boneco vetorial escalavel (cabeca + tronco + bracos + pernas), pes em fy, altura h."""
    s = h / 16.0
    hr = 2.5 * s
    hy = fy - h + hr
    d.disc(cx, hy, hr, m, g=g)
    top = hy + hr + 0.4 * s
    hip = fy - 6.4 * s
    d.line(cx, top + 0.6 * s, cx, hip, 3.4 * s, m, g=g)
    d.line(cx - 0.7 * s, hip, cx - 2.6 * s, fy - 0.8 * s, 2.3 * s, m, g=g)
    d.line(cx + 0.7 * s, hip, cx + 2.6 * s, fy - 0.8 * s, 2.3 * s, m, g=g)
    if arms == "down":
        d.line(cx - 1.6 * s, top + 1.2 * s, cx - 4.0 * s, hip + 0.4 * s, 2.0 * s, m, g=g)
        d.line(cx + 1.6 * s, top + 1.2 * s, cx + 4.0 * s, hip + 0.4 * s, 2.0 * s, m, g=g)
    elif arms == "up":
        d.line(cx - 1.6 * s, top + 1.2 * s, cx - 4.6 * s, top - 3.0 * s, 2.0 * s, m, g=g)
        d.line(cx + 1.6 * s, top + 1.2 * s, cx + 4.6 * s, top - 3.0 * s, 2.0 * s, m, g=g)
    elif arms == "out":
        d.line(cx - 1.6 * s, top + 1.4 * s, cx - 5.2 * s, top + 1.0 * s, 2.0 * s, m, g=g)
        d.line(cx + 1.6 * s, top + 1.4 * s, cx + 5.2 * s, top + 1.0 * s, 2.0 * s, m, g=g)


def wheat(d, x0=10.0, y0=18.0, h=14.0, m="gold", g=0, lean=0.0):
    x1 = x0 + lean
    d.line(x0, y0, x1, y0 - h + 2, 1.2, "grass.d", g=g)
    for i in range(4):
        yy = y0 - h + 2.6 + i * 2.0
        xx = x1 - lean * (i * 2.0) / h
        d.ellipse(xx - 1.3, yy + 0.6, 1.05, 1.7, m, g=g + 1, rot=-30)
        d.ellipse(xx + 1.3, yy + 0.6, 1.05, 1.7, m, g=g + 2, rot=30)
    d.ellipse(x1, y0 - h + 1.2, 1.0, 1.8, m, g=g + 3)


def pickaxe(d, cx=10.0, cy=10.0, s=1.0, head="iron", handle="wood", g=0):
    d.line(cx - 6.5 * s, cy + 7.5 * s, cx + 3.5 * s, cy - 2.5 * s, 2.2 * s, handle, g=g)
    d.fill(lambda x, y: 6.2 * s <= math.hypot(x - cx + 1.8 * s, y - cy - 1.8 * s) <= 8.4 * s
           and (x - cx) + (y - cy) < -2.6 * s and abs((x - cx) - (y - cy)) < 13.0 * s, head, g=g + 1)


# ---------------------------------------------------------------------------------------------- ARCANO (efeitos)


@pic("arcane_damage")
def _(d):
    d.star(10, 10, 9.6, 3.0, 4, -90, "p")
    d.star(10, 10, 6.0, 2.2, 4, -45, "p.l", g=1)
    d.disc(10, 10, 2.0, "W")


@pic("push")
def _(d):
    # seta BRANCA batendo no bloco (empurra para longe) + tracos de impacto
    cube(d, 13.4, 9.6, 0.72, "p")
    d.arrow(0.4, 10.4, 7.4, 10.4, 3.0, 4.6, "W", g=5)
    d.line(7.6, 3.2, 6.0, 1.6, 1.0, "W", g=6)
    d.line(7.6, 17.6, 6.0, 19.2, 1.0, "W", g=7)


@pic("pull")
def _(d):
    # corda presa no bloco puxando para o conjurador: seta DOURADA apontando para FORA do bloco
    cube(d, 13.4, 9.6, 0.72, "p")
    d.line(5.0, 10.4, 8.4, 10.4, 1.2, "wood.l", g=4)
    d.arrow(6.4, 10.4, 0.4, 10.4, 2.6, 4.4, "gold", g=5)
    d.arc(13.4, 10.4, 6.8, 5.6, 100, 260, "wood.l", g=4)


@pic("slow_fall")
def _(d):
    feather(d, 3.0, 17.6, 14.6, 1.4, "white")
    chevron(d, 16.4, 12.0, 2.6, 1.6, 0, 1, "p", g=5)
    chevron(d, 16.4, 16.0, 2.6, 1.6, 0, 1, "p.l", g=6)


@pic("harvest")
def _(d):
    wheat(d, 15.0, 18.4, 15.0, "gold", g=0, lean=-1.0)
    d.fill(lambda x, y: (x - 9.4) ** 2 + (y - 9.0) ** 2 <= 7.4 ** 2 and (x - 7.6) ** 2 + (y - 10.8) ** 2 > 6.4 ** 2
           and y < 12.4, "steel", g=5)
    d.line(3.6, 17.6, 6.0, 12.4, 2.2, "wood", g=6)


@pic("conjure_light")
def _(d):
    d.ring(10, 2.6, 2.4, 1.1, "iron")
    d.rect(5, 4, 14, 5, "iron", g=1)
    d.rect(6, 6, 13, 15, "glass", g=2)
    flame(d, 9.5, 14.8, 0.46, "fire", "flame", None, g=4)
    d.rect(5, 6, 5, 15, "iron", g=7)
    d.rect(14, 6, 14, 15, "iron", g=7)
    d.rect(5, 16, 14, 17, "iron", g=8)
    for (x, y) in ((2, 9), (17, 9), (2, 12), (17, 12)):
        d.put(x, y, "yellow.l")


@pic("break")
def _(d):
    cube(d, 9.0, 10.6, 0.92, "stone")
    d.path([(4.4, 9.0), (6.0, 11.0), (5.0, 13.4), (6.8, 15.0)], 1.0, "k")
    d.path([(11.6, 12.0), (13.0, 10.6), (14.0, 12.8)], 1.0, "k")
    pickaxe(d, 12.6, 6.6, 0.72, "steel", "wood", g=5)


@pic("blink")
def _(d):
    vfig(d, 4.4, 18.6, 11.0, "p.d")
    for y in range(0, 20, 2):
        d.erase(lambda x, yy, y=y: int(yy) == y and x < 9)
    vfig(d, 15.0, 18.6, 12.0, "p", g=3)
    sparkle(d, 15.0, 4.0, 3.6, "W", g=5)
    for i, x in enumerate((7.0, 9.4, 11.8)):
        d.disc(x, 3.6 + abs(i - 1) * 0.8, 0.9, "p.l", g=6)


@pic("levitate")
def _(d):
    cube(d, 10, 7.6, 0.74, "p")
    for (x, m) in ((5.0, "p.l"), (10.0, "W"), (15.0, "p.l")):
        chevron(d, x, 15.0, 2.4, 1.5, 0, -1, m, g=6)
        chevron(d, x, 18.0, 2.4, 1.5, 0, -1, "p.d", g=7)


@pic("place_block")
def _(d):
    cube(d, 9.0, 10.4, 0.92, "p")
    plus(d, 16.0, 4.0, 3.4, 2.2, "W", g=5)


@pic("detect")
def _(d):
    d.disc(8.0, 8.0, 6.6, "gold")
    d.disc(8.0, 8.0, 4.8, "glass", g=1)
    sparkle(d, 8.0, 8.0, 3.2, "p", g=2)
    d.line(12.6, 12.6, 18.0, 18.0, 3.2, "wood", g=3)


@pic("stun")
def _(d):
    d.disc(10, 14.0, 5.4, "p")
    d.disc(8.0, 13.6, 1.0, "k")
    d.disc(12.0, 13.6, 1.0, "k")
    chain_link(d, 10, 6.0, 8.6, 2.6, 1.1, 0, "p.l")
    for i, (x, y) in enumerate(((3.0, 5.0), (10.0, 2.8), (16.6, 7.0))):
        d.star(x, y, 2.8, 1.2, 5, -90, "yellow", g=5 + i)


@pic("mark_target")
def _(d):
    d.ellipse(10, 17.4, 5.0, 1.4, "p.d")
    d.disc(10, 7.6, 6.2, "red", g=1)
    d.tri((4.6, 10.2), (15.4, 10.2), (10, 17.6), "red", g=1)
    d.disc(10, 7.6, 2.6, "white", g=2)


@pic("shield")
def _(d):
    shield(d, 10, 10.2, 1.05, "steel", "p")
    sparkle(d, 10, 9.0, 3.6, "W", g=5)


@pic("recall")
def _(d):
    d.disc(5.0, 13.2, 3.6, "red")
    d.tri((2.0, 14.8), (8.0, 14.8), (5.0, 19.0), "red")
    d.disc(5.0, 13.2, 1.4, "white", g=1)
    d.arc_arrow(10.6, 9.4, 6.4, 120, -150, 2.2, 3.6, "p")


@pic("telekinesis")
def _(d):
    d.disc(10.0, 5.6, 4.6, "stone")
    d.disc(8.6, 4.4, 1.2, "stone.l")
    for i, y in enumerate((12.0, 15.0, 18.0)):
        w = 8.0 - i * 1.6
        pts = [(10 - w + t * 2 * w, y + 0.9 * math.sin(t * math.pi * 4)) for t in [k / 16 for k in range(17)]]
        d.path(pts, 1.4, "p.l" if i == 0 else "p", g=2 + i)
    sparkle(d, 3.0, 3.6, 2.2, "W")
    sparkle(d, 17.0, 6.6, 2.0, "W")


@pic("swap")
def _(d):
    d.arc_arrow(10, 10, 6.6, 200, 340, 2.2, 3.6, "p")
    d.arc_arrow(10, 10, 6.6, 20, 160, 2.2, 3.6, "p.l")
    d.disc(10, 10, 2.2, "W")


@pic("silence")
def _(d):
    d.ellipse(10, 8.4, 8.8, 6.6, "white")
    d.tri((4.0, 12.0), (3.0, 18.6), (9.0, 13.6), "white")
    d.line(6.6, 5.0, 13.4, 11.8, 2.2, "red", g=2)
    d.line(13.4, 5.0, 6.6, 11.8, 2.2, "red", g=2)


@pic("disarm")
def _(d):
    sword(d, 15.0, 2.6, 5.0, 16.6, w=2.6)
    d.arc(10, 10, 9.6, 8.4, 200, 260, "W", g=6)
    d.arc(10, 10, 9.6, 8.4, 20, 80, "W", g=6)
    d.line(1.0, 17.6, 9.0, 17.6, 1.4, "p.d", g=7)


@pic("shrink")
def _(d):
    vfig(d, 5.6, 18.6, 16.0, "p.d")
    d.arrow(9.6, 10.0, 14.4, 10.0, 1.8, 2.8, "W", g=3)
    vfig(d, 16.0, 18.6, 7.0, "p", g=5)


@pic("grow")
def _(d):
    vfig(d, 3.6, 18.6, 7.0, "p.d")
    d.arrow(5.4, 8.0, 9.4, 4.0, 1.8, 2.8, "W", g=3)
    vfig(d, 13.6, 18.6, 17.0, "p", g=5)


@pic("mana_gift")
def _(d):
    d.rect(3, 9, 16, 18, "mana")
    d.rect(2, 7, 17, 9, "mana.l", g=1)
    d.rect(9, 7, 10, 18, "gold", g=2)
    chain_link(d, 7.2, 4.6, 3.0, 2.0, 1.2, 20, "gold", g=3)
    chain_link(d, 12.8, 4.6, 3.0, 2.0, 1.2, -20, "gold", g=3)


@pic("reflect")
def _(d):
    d.ellipse(8.0, 8.0, 5.8, 7.4, "gold")
    d.ellipse(8.0, 8.0, 4.0, 5.6, "glass", g=1)
    d.line(5.6, 5.0, 7.6, 2.8, 1.0, "W")
    d.line(5.0, 8.6, 9.0, 4.4, 1.0, "W")
    d.rect(7, 15, 8, 18, "gold", g=2)
    d.line(18.6, 17.0, 13.0, 12.6, 1.8, "p", g=4)
    d.arrow(13.0, 12.6, 18.6, 8.2, 1.8, 3.0, "p", g=5)


@pic("haste_time")
def _(d):
    d.rect(5, 0, 8, 1, "gold")
    d.disc(6.8, 9.8, 6.6, "gold", g=1)
    d.disc(6.8, 9.8, 5.0, "white", g=2)
    d.line(6.8, 9.8, 6.8, 5.8, 1.3, "k")
    d.line(6.8, 9.8, 9.6, 11.2, 1.3, "k")
    chevron(d, 13.4, 15.0, 3.6, 2.0, 1, 0, "yellow", g=5)
    chevron(d, 17.2, 15.0, 3.6, 2.0, 1, 0, "yellow", g=6)


@pic("slow_time")
def _(d):
    hourglass(d, 7.4, 9.4, 0.84, frame="wood", glass="glass", sand="aqua", top=0.6, bot=0.4)
    chevron(d, 17.2, 15.0, 3.6, 2.0, -1, 0, "aqua", g=6)
    chevron(d, 13.6, 15.0, 3.6, 2.0, -1, 0, "aqua", g=7)


@pic("dispel")
def _(d):
    for a0 in (10, 100, 190, 280):
        d.arc(10, 10, 8.6, 6.6, a0, a0 + 62, "p", g=a0)
    sparkle(d, 10, 10, 4.4, "W", g=1)
    for (x, y) in ((2, 2), (17, 3), (3, 17), (17, 17)):
        d.rect(x, y, x + 1, y + 1, "p.l", g=2)


@pic("mana_drain")
def _(d):
    drop(d, 6.6, 10.6, 5.4, "mana")
    for i, (x, y, r) in enumerate(((13.6, 9.0, 1.6), (16.0, 6.0, 1.3), (17.8, 3.0, 1.0))):
        d.disc(x, y, r, "mana.l", g=3 + i)
    d.arrow(11.0, 17.0, 18.6, 17.0, 1.8, 3.0, "red", g=7)


@pic("counterspell")
def _(d):
    d.arrow(0.6, 10, 7.0, 10, 2.4, 3.6, "p")
    d.arrow(19.4, 10, 13.0, 10, 2.4, 3.6, "red", g=1)
    burst(d, 10, 10, 5.6, 2.6, 8, "yellow", -90, (1.0, 0.7), g=2)
    d.disc(10, 10, 1.4, "W")


@pic("harvest_replant")
def _(d):
    wheat(d, 10.0, 17.0, 14.0, "gold")
    d.arc_arrow(10, 10, 8.4, 110, 390, 1.8, 3.2, "p", g=7)


@pic("collect_items")
def _(d):
    bag(d, 10, 11.6, "wood", "gold")
    d.arrow(1.2, 3.0, 5.0, 7.0, 1.6, 2.8, "p", g=4)
    d.arrow(18.8, 3.0, 15.0, 7.0, 1.6, 2.8, "p", g=5)
    d.rect(1, 12, 2, 13, "cyan", g=6)
    d.rect(17, 13, 18, 14, "green", g=7)


@pic("shear")
def _(d):
    cloud(d, 13.0, 13.6, 0.7, "white")
    d.line(2.0, 4.0, 11.0, 12.0, 2.2, "steel", g=2)
    d.line(2.0, 12.0, 11.0, 4.0, 2.2, "steel", g=3)
    d.ring(3.6, 3.2, 2.4, 1.2, "red", g=4)
    d.ring(3.6, 12.8, 2.4, 1.2, "red", g=5)


@pic("breed")
def _(d):
    # pintinho saindo do ovo + coracao (reproduzir animais)
    d.disc(8.8, 8.6, 3.9, "yellow", g=1)
    d.tri((12.0, 7.6), (15.2, 8.6), (12.0, 9.8), "orange", g=2)
    d.put(10, 7, "k")

    def zig(x):
        k = (x % 3.0) / 3.0
        return 11.2 + 1.8 * (k * 2 if k < 0.5 else (1 - k) * 2)
    d.fill(lambda x, y: ((x - 8.8) / 6.6) ** 2 + ((y - 12.4) / 6.4) ** 2 <= 1 and y >= zig(x), "white", g=3)
    d.px([(5, 15), (6, 16), (12, 14)], "white.d", g=3)
    d.heart(15.6, 4.0, 0.5, "heart", g=6)


@pic("sow")
def _(d):
    for i, y in enumerate((15, 17)):
        d.rect(1, y, 18, y, "dirt" if i == 0 else "dirt.d", g=i)
    d.rect(1, 18, 18, 18, "dirt.d", g=1)
    for i, (x, y) in enumerate(((4.0, 4.0), (9.0, 7.6), (14.0, 4.6), (6.6, 11.0), (12.0, 11.6))):
        d.ellipse(x, y, 1.1, 1.6, "wood.l", g=3 + i, rot=25)
    d.line(16.0, 14.6, 16.0, 11.6, 1.0, "grass", g=9)
    d.ellipse(17.2, 11.0, 1.6, 0.9, "grass", g=9, rot=-30)


@pic("conjured_weapon")
def _(d):
    d.ring(4.4, 15.6, 4.0, 2.6, "p.l")
    sword(d, 4.0, 16.0, 17.4, 2.6, blade="p", guard="gold", grip="p.d", w=2.8)
    for (x0, y0, x1, y1) in ((9.0, 15.6, 14.0, 10.6), (11.0, 17.4, 16.0, 12.4)):
        d.line(x0, y0, x1, y1, 1.0, "W", g=9)


@pic("blade_field")
def _(d):
    d.rect(0, 15, 19, 18, "dirt")
    d.rect(0, 15, 19, 15, "grass", g=1)
    for i, (x, tilt) in enumerate(((3.6, -1.4), (10.0, 0.0), (16.4, 1.4))):
        top = 1.0 if i == 1 else 3.2
        sword(d, x - tilt, top, x, 17.4, w=2.6, g=2 + i * 4)


# ---------------------------------------------------------------------------------------------- FOGO


@pic("ignite")
def _(d):
    flame(d, 10, 19.2, 1.02)


@pic("explosion")
def _(d):
    explosion(d, 10, 10, 1.0)


@pic("ember")
def _(d):
    flame(d, 6.4, 19.0, 0.55, g=0)
    flame(d, 14.0, 13.6, 0.48, g=4)
    flame(d, 15.6, 19.2, 0.3, g=8, core=None)
    for (x, y) in ((4, 4), (10, 2), (16, 3), (2, 9)):
        d.rect(x, y, x, y, "flame.l", g=12)


@pic("propulsion")
def _(d):
    d.poly([(5.0, 11.0), (15.0, 11.0), (13.2, 15.0), (10.0, 19.6), (6.8, 15.0)], "fire")
    d.poly([(7.4, 11.0), (12.6, 11.0), (11.2, 14.4), (10.0, 17.0), (8.8, 14.4)], "flame", g=1)
    d.arrow(10.0, 11.6, 10.0, 0.6, 4.0, 5.4, "p", g=2, hw=5.6)


@pic("smoke")
def _(d):
    for i, (x, y, r) in enumerate(((7.0, 14.0, 4.4), (12.6, 12.0, 4.2), (9.4, 7.4, 4.0), (13.4, 4.0, 2.8))):
        d.disc(x, y, r, "storm" if i % 2 == 0 else "stone", g=i)
    flame(d, 4.0, 19.4, 0.3, g=8, core=None)


@pic("fire_wall")
def _(d):
    d.rect(0, 17, 19, 18, "stone")
    flame(d, 4.0, 17.6, 0.56, g=1)
    flame(d, 16.0, 17.6, 0.56, g=4, flip=True)
    flame(d, 10.0, 17.6, 0.88, g=7)


@pic("combustion")
def _(d):
    d.disc(8.6, 12.0, 6.6, "black")
    d.rect(10, 4, 13, 6, "iron", g=1)
    d.curve([(12.0, 4.4), (14.0, 1.0), (16.6, 2.6)], 1.2, "wood.l", g=2)
    sparkle(d, 16.8, 2.8, 3.0, "yellow", g=3)
    d.disc(6.4, 9.6, 1.4, "black.h", g=4)


@pic("smelt")
def _(d):
    d.poly([(4.0, 4.0), (16.0, 4.0), (18.4, 9.6), (1.6, 9.6)], "gold")
    d.poly([(5.0, 4.0), (15.0, 4.0), (15.6, 5.4), (4.4, 5.4)], "gold.h", g=1)
    flame(d, 5.0, 19.0, 0.5, g=3)
    flame(d, 10.0, 19.0, 0.62, g=6)
    flame(d, 15.0, 19.0, 0.5, g=9, flip=True)


@pic("summon_salamander")
def _(d):
    d.curve([(7.0, 12.6), (3.0, 15.0), (4.0, 18.6)], 2.4, "orange")
    d.ellipse(10.0, 10.0, 5.2, 3.0, "orange", g=0, rot=-40)
    d.disc(14.0, 6.0, 3.0, "orange", g=0)
    for (x0, y0, x1, y1) in ((8.0, 8.6, 5.0, 7.0), (12.2, 12.0, 13.6, 15.4), (10.6, 7.0, 9.6, 3.6), (7.4, 11.6, 6.6, 14.0)):
        d.line(x0, y0, x1, y1, 1.6, "orange", g=0)
    for (x, y) in ((8.6, 10.0), (11.0, 8.6), (9.6, 12.0)):
        d.disc(x, y, 0.9, "yellow", g=2)
    d.put(15, 5, "k")
    d.put(13, 4, "k")
    flame(d, 3.6, 19.6, 0.32, g=5, core=None)


@pic("melt")
def _(d):
    # bloco de pedra cuja base vira lava e PINGA (gotas arredondadas) numa poca
    cube(d, 10.0, 6.4, 0.78, "stone")
    d.recolor(lambda x, y: y >= 9.0, "lava", g=1)
    for (x, y0, ln, r) in ((4.6, 10.0, 3.4, 1.3), (9.0, 12.0, 4.4, 1.5), (13.6, 10.6, 2.0, 1.2)):
        d.line(x, y0, x, y0 + ln, r * 1.6, "lava", g=1)
        d.disc(x, y0 + ln + 0.4, r, "lava", g=1)
    d.disc(16.4, 14.6, 1.1, "ember", g=4)
    d.ellipse(10.0, 18.4, 8.4, 1.5, "lava", g=1)


@pic("blue_flame")
def _(d):
    # chama AZUL sobre um queimador (anel de metal com jatos) - silhueta diferente de ignite
    flame(d, 10, 16.4, 0.84, "blue", "cyan", "W")
    d.fill(lambda x, y: ((x - 10) / 8.2) ** 2 + ((y - 17.4) / 2.2) ** 2 <= 1 and
           ((x - 10) / 5.6) ** 2 + ((y - 17.4) / 0.9) ** 2 > 1, "steel", g=5)
    for x in (3.2, 16.8):
        d.tri((x - 1.0, 15.6), (x + 1.0, 15.6), (x, 12.6), "cyan", g=7)
    sparkle(d, 16.4, 4.4, 1.8, "cyan.l", g=9)


@pic("overheat")
def _(d):
    d.rect(5, 1, 9, 13, "glass")
    d.rect(6, 5, 8, 14, "red", g=1)
    d.disc(7.0, 15.4, 3.6, "red", g=1)
    for (x0, y) in ((11.6, 4.0), (11.6, 9.0)):
        pts = [(x0 + t * 7.0, y + 1.2 * math.sin(t * math.pi * 3)) for t in [k / 14 for k in range(15)]]
        d.path(pts, 1.6, "orange", g=3)
    d.px([(5, 3), (5, 6), (5, 9)], "glass.d")


@pic("phoenix_rebirth")
def _(d):
    wing(d, 10.6, 8.4, "fire", flip=False, s=1.05)
    wing(d, 9.4, 8.4, "fire", flip=True, s=1.05, g=2)
    d.ellipse(10.0, 10.0, 2.6, 4.6, "flame", g=4)
    d.disc(10.0, 4.6, 2.2, "flame", g=4)
    d.tri((10.0, 2.6), (12.6, 3.6), (10.6, 4.6), "orange", g=5)
    d.put(9, 4, "k")
    for (x, s) in ((7.6, 0.34), (10.0, 0.44), (12.4, 0.34)):
        d.poly([(x - 2.4 * s * 2, 13.4), (x + 2.4 * s * 2, 13.4), (x, 13.4 + 14 * s)], "fire", g=6)


@pic("crater")
def _(d):
    d.ellipse(10, 17.2, 9.0, 2.2, "storm")
    d.rect(8, 8, 11, 16, "fire", g=1)
    d.ellipse(10, 12.6, 4.4, 1.4, "fire.l", g=2)
    d.ellipse(10, 6.0, 8.0, 4.8, "fire", g=3)
    d.ellipse(10, 5.2, 5.4, 2.8, "flame", g=4)
    d.ellipse(10, 4.6, 2.6, 1.3, "W", g=5)


@pic("smelt_items")
def _(d):
    d.rect(2, 3, 17, 18, "stone")
    d.rect(2, 3, 17, 4, "stone.l", g=1)
    d.rect(5, 9, 14, 16, "k")
    flame(d, 9.6, 16.4, 0.42, g=3)
    d.rect(5, 6, 6, 6, "stone.d")
    d.rect(13, 6, 14, 6, "stone.d")


# ---------------------------------------------------------------------------------------------- AGUA


@pic("soak")
def _(d):
    # gota CAINDO (com tracos de queda) + poca com respingos (mend = gota com cruz de cura)
    drop(d, 11.0, 5.6, 4.4, "water")
    for i, (x, y0) in enumerate(((5.6, 2.0), (16.4, 4.0))):
        d.line(x, y0, x, y0 + 3.0, 1.0, "aqua.l", g=12 + i)
    d.ellipse(10, 17.4, 8.6, 1.9, "water", g=3)
    d.ellipse(10, 17.2, 4.4, 0.8, "aqua.l", g=4)
    for i, (x, y) in enumerate(((2.6, 13.0), (5.4, 14.6), (14.6, 14.6), (17.4, 13.0))):
        d.disc(x, y, 1.0, "aqua", g=5 + i)


@pic("tide")
def _(d):
    wave_curl(d, "water", "foam")


@pic("bubble")
def _(d):
    for i, (x, y, r) in enumerate(((7.6, 11.6, 6.0), (15.4, 5.0, 3.4), (15.6, 15.0, 2.6))):
        d.disc(x, y, r, "glass", g=i * 3)
        d.ring(x, y, r, r - 1.2, "aqua", g=i * 3 + 1)
        d.disc(x - r * 0.4, y - r * 0.4, max(0.9, r * 0.24), "W")


@pic("water_blade")
def _(d):
    crescent(d, 7.0, 13.0, 11.6, -3.4, 3.4, 11.0, "water")
    crescent(d, 7.0, 13.0, 9.0, -2.6, 2.6, 8.7, "aqua", g=1)
    for (x, y) in ((15.0, 15.0), (12.0, 17.6), (17.6, 11.0)):
        drop(d, x, y, 1.3, "aqua", 1.6, shine=False, g=4)


@pic("extinguish")
def _(d):
    flame(d, 10, 19.4, 0.62, "storm", "stone", None)
    drop(d, 10, 4.6, 3.6, "water", g=5)
    for (x, y) in ((3.6, 9.6), (16.4, 9.6)):
        d.disc(x, y, 1.6, "cloud", g=8)


@pic("hydrate")
def _(d):
    d.rect(4, 8, 13, 16, "teal")
    d.arc(8.5, 8.0, 4.4, 2.8, 180, 360, "teal", g=1)
    d.line(13.0, 13.0, 17.6, 7.4, 2.0, "teal", g=2)
    d.ellipse(17.8, 6.8, 1.6, 1.0, "teal.d", g=3, rot=-50)
    for (x, y) in ((17.0, 10.6), (18.6, 13.4), (16.0, 14.6)):
        d.rect(x, y, x, y + 1, "aqua", g=5)


@pic("create_water")
def _(d):
    d.arc(10, 8.0, 7.6, 6.4, 180, 360, "iron")
    d.poly([(3.0, 8.0), (17.0, 8.0), (15.0, 18.4), (5.0, 18.4)], "iron", g=1)
    d.ellipse(10, 8.6, 6.4, 1.8, "water", g=2)
    drop(d, 10, 2.6, 2.0, "aqua", 1.6, shine=False, g=4)


@pic("water_breathing")
def _(d):
    d.disc(9.0, 11.0, 7.8, "glass")
    d.disc(9.0, 10.0, 3.2, "flesh", g=1)
    d.fill(lambda x, y: ((x - 9.0) / 5.4) ** 2 + ((y - 17.6) / 4.4) ** 2 <= 1 and y <= 18.2, "teal", g=2)
    d.ring(9.0, 11.0, 7.8, 6.6, "aqua", g=3)
    for (x, y, r) in ((17.0, 4.0, 2.0), (14.6, 1.6, 1.2), (18.6, 9.0, 1.2)):
        d.ring(x, y, r, r - 0.9, "aqua.l", g=5)


@pic("current")
def _(d):
    for i, y in enumerate((4.6, 10.0, 15.4)):
        pts = [(1.0 + t * 13.0, y + 1.3 * math.sin(t * math.pi * 2.6)) for t in [k / 16 for k in range(17)]]
        d.path(pts, 2.0, "water" if i != 1 else "aqua", g=i * 2)
        tri_tip(d, 15.4, pts[-1][1], 12.0, pts[-1][1], "water" if i != 1 else "aqua", g=i * 2, size=3.6)


@pic("mist")
def _(d):
    cloud(d, 10, 5.6, 0.8, "cloud")
    for i, (y, x0, x1) in enumerate(((11.0, 2.0, 15.0), (14.0, 5.0, 18.0), (17.0, 1.0, 13.0))):
        d.line(x0, y, x1, y, 2.0, "cloud" if i % 2 else "aqua.l", g=3 + i)


@pic("mend")
def _(d):
    drop(d, 10, 9.6, 6.6, "water", shine=False)
    plus(d, 10, 12.0, 3.6, 2.4, "W", g=3)


@pic("summon_undine")
def _(d):
    drop(d, 10, 9.0, 6.8, "aqua", shine=False)
    d.ellipse(7.6, 11.4, 1.0, 1.5, "k")
    d.ellipse(12.4, 11.4, 1.0, 1.5, "k")
    d.arc(10, 13.2, 2.0, 1.0, 20, 160, "water.d", g=3)
    waves(d, (17.4,), "water", 0.9, 1.8, 1.0, 19.0, g=5, period=5.0)


@pic("drown")
def _(d):
    d.rect(0, 6, 19, 18, "water")
    waves(d, (5.6,), "aqua", 1.0, 1.8, 0.0, 20.0, g=1, period=5.0)
    d.arrow(10, 7.6, 10, 18.4, 2.4, 3.6, "W", g=3)
    for (x, y, r) in ((4.4, 12.0, 1.8), (15.6, 10.0, 1.4), (5.6, 16.4, 1.2)):
        d.ring(x, y, r, r - 0.9, "aqua.l", g=5)


@pic("local_rain")
def _(d):
    cloud(d, 10, 6.4, 1.0, "cloud")
    for i, (x, y) in enumerate(((5.0, 14.6), (10.0, 16.6), (15.0, 14.6))):
        drop(d, x, y, 1.6, "water", 1.7, shine=False, g=4 + i)


@pic("water_mirror")
def _(d):
    d.disc(10, 10, 8.6, "silver")
    d.disc(10, 10, 6.6, "water", g=1)
    waves(d, (8.0, 12.4), "aqua", 0.8, 1.2, 4.6, 15.4, g=2, period=4.0)
    d.line(6.0, 6.6, 8.6, 4.0, 1.0, "W")


@pic("deluge")
def _(d):
    cloud(d, 10, 4.0, 0.9, "storm")
    for x in (3.6, 7.6, 11.6, 15.6):
        d.line(x + 1.0, 8.6, x - 0.4, 11.4, 1.3, "aqua", g=6)
    d.rect(0, 13, 19, 19, "water", g=8)
    waves(d, (13.0, 16.4), "aqua", 1.0, 1.6, 0.0, 20.0, g=9, period=5.0)


@pic("call_rain")
def _(d):
    d.fill(lambda x, y: (x - 10) ** 2 + (y - 9.6) ** 2 <= 8.6 ** 2 and y <= 9.6, "blue")
    for x in (3.6, 8.0, 12.0, 16.4):
        d.disc(x, 9.6, 2.2, None)
    d.line(10, 3.0, 10, 15.6, 1.4, "wood", g=2)
    d.arc(8.4, 15.6, 1.8, 0.6, 0, 180, "wood", g=2)
    for (x, y) in ((2.0, 13.0), (17.6, 13.4), (14.0, 17.0), (4.6, 17.6)):
        d.line(x + 0.6, y - 1.6, x, y, 1.2, "aqua", g=5)


# ---------------------------------------------------------------------------------------------- TERRA


@pic("rock")
def _(d):
    # rocha angulosa ARREMESSADA (facetas claras/escuras) + rastro de poeira - nada de chao (nao parecer lapide)
    for i, (y, x0, ln) in enumerate(((6.0, 0.4, 3.4), (10.4, 0.0, 4.2), (14.6, 1.0, 3.0))):
        d.line(x0, y, x0 + ln, y, 1.3, "dirt.l", g=10 + i)
    body = [(5.6, 8.4), (8.4, 3.4), (13.6, 1.6), (18.6, 5.2), (19.2, 11.6), (15.8, 17.0), (9.8, 18.0), (5.4, 14.0)]
    d.poly(body, "stone.m")
    d.poly([(5.6, 8.4), (8.4, 3.4), (13.6, 1.6), (12.6, 8.0), (7.2, 10.6)], "stone.l")
    d.poly([(12.6, 8.0), (19.2, 11.6), (15.8, 17.0), (11.2, 12.6)], "stone.d")
    d.px([(12, 3), (11, 4), (10, 4)], "stone.h")
    d.px([(9, 13), (10, 14), (16, 9)], "stone.d")


@pic("raise_pillar")
def _(d):
    d.rect(6, 4, 13, 17, "stone")
    d.rect(5, 2, 14, 4, "stone.l", g=1)
    for x in (8, 11):
        d.rect(x, 6, x, 15, "stone.d")
    d.rect(0, 17, 19, 18, "dirt", g=2)
    chevron(d, 2.6, 8.0, 2.4, 1.6, 0, -1, "W", g=3)
    chevron(d, 17.4, 8.0, 2.4, 1.6, 0, -1, "W", g=3)
    chevron(d, 2.6, 12.0, 2.4, 1.6, 0, -1, "p", g=4)
    chevron(d, 17.4, 12.0, 2.4, 1.6, 0, -1, "p", g=4)


@pic("stalagmite")
def _(d):
    d.tri((5.6, 18.0), (10.0, 0.8), (14.4, 18.0), "stone")
    d.tri((0.8, 18.0), (4.0, 7.4), (7.2, 18.0), "stone.d", g=1)
    d.tri((12.8, 18.0), (16.4, 9.0), (19.2, 18.0), "stone.d", g=2)
    d.line(8.6, 6.0, 7.4, 15.6, 1.0, "stone.h", g=3)
    d.rect(0, 18, 19, 18, "dirt", g=4)


@pic("stoneskin")
def _(d):
    shield(d, 10, 10.2, 1.05, "stone")
    d.path([(6.0, 4.6), (8.6, 8.0), (7.0, 11.6)], 1.0, "stone.d", g=3)
    d.path([(13.0, 6.0), (11.6, 9.6), (13.6, 12.6)], 1.0, "stone.d", g=3)
    d.path([(8.6, 8.0), (11.6, 9.6)], 1.0, "stone.d", g=3)


@pic("excavate")
def _(d):
    # pa VERTICAL cravada num buraco + torroes voando (till = enxada + sulcos; break = picareta + bloco)
    d.rect(0, 13, 19, 18, "dirt")
    d.rect(0, 12, 19, 12, "grass", g=1)
    d.fill(lambda x, y: ((x - 7.6) / 5.8) ** 2 + ((y - 15.2) / 2.9) ** 2 <= 1, "k")
    d.line(14.0, 1.6, 14.0, 9.6, 1.8, "wood", g=3)
    d.rect(12, 0, 16, 1, "wood", g=3)
    d.poly([(11.4, 9.4), (16.6, 9.4), (16.4, 13.6), (14.0, 16.0), (11.6, 13.6)], "iron", g=4)
    for i, (x, y) in enumerate(((2, 6), (5, 2), (8, 7), (3, 10), (9, 3))):
        d.rect(x, y, x + 1, y + 1, "dirt.l" if i % 2 else "dirt", g=6 + i)


@pic("fertilize")
def _(d):
    d.fill(lambda x, y: ((x - 10) / 9.0) ** 2 + ((y - 18.6) / 5.0) ** 2 <= 1, "dirt")
    d.line(10, 14.6, 10, 8.0, 1.4, "grass.d", g=1)
    leaf(d, 6.6, 8.6, 0.55, -20, "grass", g=2)
    leaf(d, 13.4, 7.0, 0.55, 20, "grass", g=3)
    for (x, y, r) in ((3.0, 4.0, 2.2), (16.6, 13.0, 1.8), (16.6, 2.6, 1.6)):
        sparkle(d, x, y, r, "W", g=6)


@pic("stone_wall")
def _(d):
    stones = [(1, 1, 7, 5), (8, 1, 13, 4), (14, 1, 18, 6), (1, 6, 5, 10), (6, 5, 12, 10), (13, 7, 18, 11),
              (1, 11, 8, 15), (9, 11, 13, 15), (14, 12, 18, 15), (1, 16, 18, 18)]
    for i, (x0, y0, x1, y1) in enumerate(stones):
        d.rect(x0, y0, x1, y1, "stone", g=i)
    d.rect(1, 16, 18, 18, "dirt", g=20)


@pic("root")
def _(d):
    d.rect(0, 15, 19, 18, "dirt")
    d.curve([(10, 18.0), (9.0, 10.0), (13.0, 2.0)], 2.4, "wood", g=1)
    d.curve([(5.0, 18.0), (2.0, 11.0), (5.0, 5.0)], 2.0, "wood", g=2)
    d.curve([(15.0, 18.0), (18.6, 12.0), (15.6, 7.0)], 2.0, "wood", g=3)
    d.curve([(9.6, 11.0), (6.6, 8.6), (7.0, 6.0)], 1.4, "wood", g=4)
    leaf(d, 14.4, 2.4, 0.4, -30, "leaf", g=6)


@pic("quicksand")
def _(d):
    hand(d, 3, 0, "flesh", rows=HAND_ROWS[:11])
    d.ellipse(10, 15.0, 9.6, 4.6, "sand", g=4)
    d.ellipse(10, 15.0, 6.4, 2.8, "sand.d", g=5)
    spiral(d, 10, 15.0, 0.4, 5.6, 1.3, 1.0, "dirt", 0, g=6)
    d.recolor(lambda x, y: ((x - 10) / 9.6) ** 2 + ((y - 15) / 4.6) ** 2 <= 1 and y > 12, "sand.d", only="flesh")


@pic("summon_golem")
def _(d):
    d.rect(3, 3, 16, 17, "stone")
    d.rect(3, 3, 16, 5, "moss", g=1)
    d.rect(5, 8, 8, 10, "cyan.l", g=2)
    d.rect(11, 8, 14, 10, "cyan.l", g=2)
    d.rect(7, 13, 12, 14, "stone.d")
    d.rect(0, 9, 2, 14, "stone", g=4)
    d.rect(17, 9, 19, 14, "stone", g=5)


@pic("fortress")
def _(d):
    d.rect(4, 6, 15, 18, "stone")
    for x in (4, 8, 12):
        d.rect(x, 3, x + 2, 5, "stone", g=1 + x)
    d.rect(14, 3, 15, 5, "stone", g=30)
    d.rect(8, 12, 11, 18, "k")
    d.arc(9.5, 12.5, 2.0, 0, 180, 360, "k")
    d.rect(9, 7, 10, 9, "k")
    d.rect(14, 0, 14, 3, "wood", g=40)
    d.rect(15, 0, 17, 1, "red", g=41)


@pic("tremor")
def _(d):
    d.rect(0, 11, 19, 18, "dirt")
    d.rect(0, 11, 19, 11, "grass", g=1)
    d.path([(3.0, 11.0), (5.0, 14.0), (4.0, 16.0), (6.0, 18.4)], 1.0, "k")
    d.path([(13.0, 11.0), (12.0, 13.6), (14.4, 15.4), (13.6, 18.4)], 1.0, "k")
    for (x, y) in ((4.0, 5.6), (10.0, 4.0), (16.0, 6.6)):
        d.rect(int(x) - 1, int(y), int(x), int(y) + 1, "stone", g=5)
    for i, x in enumerate((1.6, 18.4)):
        d.line(x, 3.0, x, 8.0, 1.2, "W", g=7)


@pic("weight")
def _(d):
    d.ring(10, 5.6, 4.6, 2.6, "iron")
    d.poly([(4.0, 8.0), (16.0, 8.0), (18.6, 18.4), (1.4, 18.4)], "iron", g=1)
    d.rect(8, 12, 11, 13, "iron.d")
    d.rect(9, 11, 10, 15, "iron.d")


@pic("magnetism")
def _(d):
    magnet(d, 8.6, 9.0)
    for (x, y) in ((16.6, 14.0), (17.4, 9.0), (14.0, 17.6)):
        d.rect(int(x) - 1, int(y) - 1, int(x), int(y), "iron", g=5)
    d.arc(8.6, 15.0, 6.6, 5.6, 20, 70, "W", g=7)


@pic("earthsplitter")
def _(d):
    d.poly([(0.0, 8.0), (9.0, 7.0), (7.4, 11.0), (9.6, 14.0), (7.0, 19.0), (0.0, 19.0)], "dirt")
    d.poly([(19.6, 8.0), (11.6, 7.0), (10.6, 11.0), (12.4, 14.0), (10.0, 19.0), (19.6, 19.0)], "dirt", g=1)
    d.poly([(9.0, 7.0), (11.6, 7.0), (10.6, 11.0), (12.4, 14.0), (10.0, 19.0), (7.0, 19.0), (9.6, 14.0), (7.4, 11.0)], "lava", g=2)
    d.rect(0, 7, 8, 7, "grass", g=3)
    d.rect(12, 7, 19, 7, "grass", g=4)
    for (x, y) in ((4.0, 3.0), (14.0, 2.0), (9.6, 1.0)):
        d.rect(int(x), int(y), int(x) + 1, int(y) + 1, "stone", g=6)


@pic("till")
def _(d):
    # ENXADA grande (lamina em L no alto) + canteiro de sulcos com agua entre eles
    d.rect(0, 16, 19, 18, "dirt.d", g=0)
    for i, cx in enumerate((2.6, 8.4, 14.2)):
        d.ellipse(cx + 1.6, 15.6, 2.9, 1.7, "dirt", g=1 + i)
    for x in (7, 13, 19):
        d.put(x - 0.5, 16, "water.l", g=6)
    d.line(3.4, 14.6, 14.8, 2.4, 2.0, "wood", g=8)
    d.rect(8, 0, 17, 2, "steel", g=9)
    d.rect(8, 3, 10, 5, "steel", g=9)


# ---------------------------------------------------------------------------------------------- VENTO




@pic("wind_blade")
def _(d):
    crescent(d, 7.0, 13.0, 11.6, -3.4, 3.4, 11.0, "aqua")
    crescent(d, 7.0, 13.0, 9.0, -2.6, 2.6, 8.7, "white", g=1)
    for (x0, y0, x1, y1) in ((1.0, 8.0, 4.6, 6.4), (2.0, 12.6, 6.0, 10.6)):
        d.line(x0, y0, x1, y1, 1.0, "aqua.l", g=4)


@pic("gust")
def _(d):
    d.line(1.0, 6.0, 12.0, 6.0, 2.0, "white")
    d.arc(12.0, 3.4, 3.6, 1.6, 270, 450, "white")
    d.line(3.0, 10.6, 16.0, 10.6, 2.0, "aqua", g=1)
    d.arc(16.0, 8.0, 3.6, 1.6, 270, 450, "aqua", g=1)
    d.line(1.0, 15.0, 10.0, 15.0, 2.0, "white", g=2)
    d.arc(10.0, 12.4, 3.6, 1.6, 270, 450, "white", g=2)


@pic("impulse")
def _(d):
    d.arrow(5.0, 10.0, 19.0, 10.0, 4.0, 6.0, "p", hw=6.0)
    for (y, x0, x1) in ((4.0, 0.6, 7.0), (16.0, 0.6, 7.0), (10.0, 0.4, 3.0)):
        d.line(x0, y, x1, y, 1.6, "white", g=3)


@pic("double_jump")
def _(d):
    chevron(d, 10, 4.0, 6.6, 3.0, 0, -1, "W")
    chevron(d, 10, 10.0, 6.6, 3.0, 0, -1, "aqua", g=1)
    cloud(d, 10, 15.4, 0.62, "cloud", g=3)


@pic("glide")
def _(d):
    d.poly([(10.0, 3.0), (19.4, 11.6), (10.0, 9.4), (0.6, 11.6)], "aqua")
    d.poly([(10.0, 3.0), (10.0, 9.4), (0.6, 11.6)], "aqua.l", g=1)
    d.line(10, 9.4, 10, 13.0, 1.0, "wood")
    vfig(d, 10, 19.4, 7.0, "p", g=3, arms="up")
    for (y, x0, x1) in ((15.0, 1.0, 4.0), (17.6, 15.6, 18.6)):
        d.line(x0, y, x1, y, 1.0, "white", g=5)


@pic("whirlwind")
def _(d):
    for i, a0 in enumerate((0, 120, 240)):
        d.arc(10, 10, 8.6, 6.8, a0, a0 + 80, "aqua", g=i)
        d.arc(10, 10, 5.4, 3.8, a0 + 60, a0 + 130, "white", g=3 + i)
    d.disc(10, 10, 1.4, "W")


@pic("launch")
def _(d):
    d.arrow(10, 14.0, 10, 0.6, 3.6, 5.0, "p", hw=5.4)
    for i, (rx, y) in enumerate(((7.6, 17.4), (5.0, 14.6))):
        d.ellipse(10, y, rx, 1.6, "aqua" if i == 0 else "white", g=3 + i)


@pic("swiftness")
def _(d):
    boot(d, 9.0, 10.0, "brown")
    wing(d, 4.0, 4.6, "white", flip=True, s=0.62, feathers=2, g=5)
    for (y, x0, x1) in ((12.0, 17.0, 19.0), (15.0, 17.6, 19.6)):
        d.line(x0, y, x1, y, 1.0, "aqua", g=8)


@pic("summon_sylph")
def _(d):
    wing(d, 11.0, 8.0, "white", flip=False, s=0.8, feathers=2)
    wing(d, 9.0, 8.0, "white", flip=True, s=0.8, feathers=2, g=2)
    d.disc(10, 6.0, 2.6, "aqua", g=4)
    spiral(d, 10, 13.0, 0.4, 4.6, 1.3, 1.8, "aqua", 270, g=5)
    d.put(9, 5, "k")
    d.put(11, 5, "k")


@pic("tornado")
def _(d):
    for i, (y, rx) in enumerate(((2.6, 8.8), (6.0, 7.0), (9.4, 5.4), (12.6, 3.8), (15.6, 2.6))):
        d.ellipse(10 + (i - 2) * 0.6, y, rx, 1.9, "aqua" if i % 2 == 0 else "white", g=i)
    d.disc(11.4, 18.2, 1.4, "white", g=8)


@pic("vacuum")
def _(d):
    spiral(d, 10, 10, 0.6, 6.4, 1.6, 1.8, "aqua", 0)
    for (sx, sy) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        d.arrow(10 + sx * 9.6, 10 + sy * 9.6, 10 + sx * 6.6, 10 + sy * 6.6, 1.6, 2.8, "white", g=4)


@pic("deflect")
def _(d):
    spiral(d, 13.6, 10.0, 0.6, 5.6, 1.5, 1.8, "aqua", 180)
    d.ring(13.6, 10.0, 6.8, 6.0, "white", g=2)
    d.line(0.8, 2.0, 6.4, 8.4, 2.0, "iron", g=3)
    d.arrow(6.4, 10.6, 1.0, 17.6, 2.0, 3.4, "iron", g=4)
    sparkle(d, 7.0, 9.6, 2.8, "W", g=6)


@pic("silent_steps")
def _(d):
    footprint(d, 5.6, 11.0, 1.0, "white", rot=-12)
    footprint(d, 13.4, 8.6, 1.0, "white", g=4, rot=12)
    for (y, x0, x1) in ((16.6, 12.0, 15.0), (18.4, 14.0, 18.0)):
        d.line(x0, y, x1, y, 1.0, "aqua", g=8)


@pic("sonar")
def _(d):
    d.disc(10, 10, 8.6, "teal.d")
    d.ring(10, 10, 8.6, 7.6, "teal", g=1)
    d.ring(10, 10, 5.2, 4.4, "teal", g=2)
    d.line(10, 10, 15.6, 4.4, 1.4, "green.l", g=3)
    d.disc(6.0, 13.6, 1.2, "red", g=4)
    d.disc(14.0, 13.0, 1.0, "red", g=4)
    d.disc(10, 10, 1.2, "W")


@pic("tempest")
def _(d):
    d.disc(10, 10, 3.0, "storm")
    d.arc(10, 10, 9.0, 5.0, 180, 290, "aqua", g=1)
    d.arc(10, 10, 9.0, 5.0, 0, 110, "aqua", g=2)
    d.arc(10, 10, 7.0, 5.0, 290, 340, "white", g=3)
    d.arc(10, 10, 7.0, 5.0, 110, 160, "white", g=4)
    bolt(d, 10.4, 10.4, 0.62, "yellow", g=6)


@pic("slash_storm")
def _(d):
    # tres cortes PARALELOS em arco (marcas de garra de vento) + rodopio
    for i, off in enumerate((-5.6, 0.0, 5.6)):
        d.taper(1.0 + off, 17.0 + off * 0.2, 17.0 + off * 0.2, 1.0 + off, 3.2, "white" if i != 1 else "aqua.l",
                g=i, bow=2.2)
    d.arc(15.6, 15.6, 3.6, 2.4, 180, 330, "aqua", g=8)
    d.arc(4.0, 4.0, 2.8, 1.8, 0, 150, "aqua", g=9)


# ---------------------------------------------------------------------------------------------- LUZ


@pic("holy_ray")
def _(d):
    d.ellipse(10, 17.0, 8.4, 2.0, "yellow")
    d.rect(7, 4, 12, 16, "yellow.l", g=1)
    d.rect(9, 4, 10, 16, "W")
    cloud(d, 10, 3.0, 0.68, "cloud", g=3)
    for (x, y) in ((3.0, 12.0), (16.6, 10.0)):
        sparkle(d, x, y, 2.0, "W", g=6)


@pic("heal")
def _(d):
    d.heart(10, 10.4, 1.05, "heart")
    plus(d, 10, 9.6, 4.0, 2.4, "W", g=3)


@pic("regenerate")
def _(d):
    d.heart(10, 10.6, 0.66, "heart")
    d.arc_arrow(10, 10, 8.6, -60, 230, 1.8, 3.2, "green", g=3)


@pic("blind")
def _(d):
    # CLARAO (raios brancos/dourados) + olho grande riscado em vermelho = "sem visao"
    burst(d, 10, 10, 9.9, 6.6, 8, "yellow", -90, (1.0, 0.72))
    eye(d, 10, 10, 0.98, iris="sky")
    d.line(2.6, 17.4, 17.4, 2.6, 2.2, "red", g=9)


@pic("reveal")
def _(d):
    for i in range(8):
        a = math.radians(i * 45)
        d.line(10 + 7.0 * math.cos(a), 10 + 7.0 * math.sin(a), 10 + 9.4 * math.cos(a), 10 + 9.4 * math.sin(a), 1.6,
               "yellow", g=5)
    eye(d, 10, 10, 0.8, iris="gold")


@pic("purify")
def _(d):
    for (rot, s, m, g) in ((-60, 0.9, "pink.l", 0), (60, 0.9, "pink.l", 1), (0, 1.0, "white", 2)):
        a = math.radians(rot - 90)
        cx, cy = 10 + math.cos(a) * 4.4 * s, 12.6 + math.sin(a) * 4.4 * s
        d.ellipse(cx, cy, 2.6 * s, 5.0 * s, m, g=g, rot=rot)
    d.ellipse(10, 15.6, 8.0, 2.0, "green", g=4)
    sparkle(d, 16.4, 3.6, 2.6, "W", g=6)
    sparkle(d, 3.4, 5.0, 2.0, "W", g=6)


@pic("blessing")
def _(d):
    chain_link(d, 10, 3.6, 6.6, 2.4, 1.6, 0, "gold")
    for (x0, x1) in ((7.0, 3.0), (10.0, 10.0), (13.0, 17.0)):
        d.taper(x0, 6.0, x1, 18.6, 3.4, "yellow.l", g=3)
    sparkle(d, 10, 12.0, 3.0, "W", g=6)


@pic("sunlight")
def _(d):
    sun(d, 10, 10, 4.8, 8, 3.6, "yellow", "flame")


@pic("summon_guardian_fairy")
def _(d):
    d.ellipse(6.0, 6.6, 3.6, 4.6, "cyan.l", rot=-30)
    d.ellipse(14.0, 6.6, 3.6, 4.6, "cyan.l", g=1, rot=30)
    d.ellipse(6.6, 13.0, 2.6, 3.2, "pink.l", g=2, rot=30)
    d.ellipse(13.4, 13.0, 2.6, 3.2, "pink.l", g=3, rot=-30)
    d.disc(10, 6.4, 2.2, "flesh", g=4)
    d.ellipse(10, 12.0, 1.8, 4.0, "white", g=5)
    d.line(13.0, 11.0, 17.0, 4.0, 1.0, "gold", g=6)
    sparkle(d, 17.0, 3.4, 2.4, "yellow", g=7)


@pic("sacred_barrier")
def _(d):
    d.fill(lambda x, y: (x - 10) ** 2 + (y - 17) ** 2 <= 9.4 ** 2 and y <= 17, "yellow.l")
    d.fill(lambda x, y: 7.8 ** 2 <= (x - 10) ** 2 + (y - 17) ** 2 <= 9.4 ** 2 and y <= 17, "gold", g=1)
    cross(d, 10, 11.6, 3.2, 1.8, "W", g=3, latin=True)
    d.rect(0, 17, 19, 18, "gold", g=4)


@pic("exorcise")
def _(d):
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        d.line(8 + 6.6 * math.cos(a), 10 + 6.6 * math.sin(a), 8 + 8.6 * math.cos(a), 10 + 8.6 * math.sin(a), 1.2,
               "yellow.l", g=6)
    cross(d, 8, 10, 4.6, 2.6, "gold", latin=True)
    ghost(d, 16.0, 15.4, 0.46, "dark", eyes="red")


@pic("prism")
def _(d):
    d.line(0.6, 12.6, 7.0, 10.6, 1.4, "W")
    for i, (m, y) in enumerate((("red", 5.0), ("yellow", 8.0), ("green", 11.0), ("blue", 14.0))):
        d.line(12.0, 10.6, 19.4, y, 1.4, m, g=2 + i)
    d.tri((10.0, 3.0), (16.4, 16.0), (3.6, 16.0), "glass", g=10)
    d.line(10.0, 4.6, 6.0, 14.6, 1.0, "W")


@pic("sanctuary")
def _(d):
    d.tri((10.0, 1.0), (19.0, 7.6), (1.0, 7.6), "gold")
    d.rect(2, 7, 17, 8, "white", g=1)
    for i, x in enumerate((3, 7, 11, 15)):
        d.rect(x, 9, x + 1, 15, "white", g=2 + i)
    d.rect(1, 16, 18, 18, "white", g=8)
    d.disc(10, 5.0, 1.2, "W")


@pic("resurrection")
def _(d):
    d.ring(10, 5.2, 4.2, 2.2, "gold")
    d.rect(9, 9, 11, 18, "gold", g=1)
    d.rect(3, 9, 17, 11, "gold", g=2)
    sparkle(d, 3.6, 3.6, 2.6, "W", g=4)
    sparkle(d, 16.4, 15.4, 2.6, "W", g=4)


@pic("clear_sky")
def _(d):
    sun(d, 12.6, 7.4, 4.2, 8, 2.8, "yellow", "flame")
    cloud(d, 6.0, 14.4, 0.56, "cloud", g=4)
    for (y, x0, x1) in ((13.0, 0.4, 1.8), (16.0, 0.4, 2.4)):
        d.line(x0, y, x1, y, 1.0, "W", g=8)


# ---------------------------------------------------------------------------------------------- SOMBRA


@pic("life_drain")
def _(d):
    d.heart(6.6, 7.0, 0.6, "heart")
    for i, (x, y, r) in enumerate(((10.4, 9.6, 1.3), (12.4, 12.0, 1.4), (13.6, 14.6, 1.0))):
        d.disc(x, y, r, "blood", g=3 + i)
    d.disc(15.2, 15.6, 3.4, "void", g=8)
    d.ring(15.2, 15.6, 3.4, 2.4, "shadow", g=9)


@pic("weaken")
def _(d):
    sword(d, 3.0, 17.0, 10.0, 10.0, w=2.8)
    d.line(11.6, 7.0, 15.0, 3.0, 2.8, "steel", g=6)
    d.tri((17.6, 1.0), (13.6, 2.4), (16.0, 4.6), "steel", g=6)
    d.arrow(17.0, 9.6, 17.0, 17.6, 1.8, 3.0, "red", g=8)


@pic("fear")
def _(d):
    d.ellipse(10, 10.0, 7.0, 8.6, "bone")
    d.ellipse(7.2, 7.6, 1.8, 2.6, "k")
    d.ellipse(12.8, 7.6, 1.8, 2.6, "k")
    d.ellipse(10, 14.0, 2.0, 3.0, "k")
    drop(d, 17.0, 5.0, 1.8, "aqua", 1.6, shine=False, g=4)


@pic("darkness")
def _(d):
    burst(d, 10, 10, 9.8, 7.0, 12, "shadow", -90, (1.0, 0.86))
    d.disc(10, 10, 6.4, "black", g=1)
    d.arc(10, 10, 6.4, 5.4, 200, 260, "purple", g=2)


@pic("shadow_step")
def _(d):
    vfig(d, 5.0, 18.6, 15.0, "shadow.d")
    vfig(d, 8.6, 18.6, 15.0, "shadow", g=2)
    vfig(d, 13.0, 18.6, 15.0, "dark", g=4, arms="out")
    d.put(13, 5, "red")


@pic("chains")
def _(d):
    # duas correntes em X (elos alternados de frente/de lado) com brilho sombrio: "acorrentar"
    def chain_line(x0, y0, x1, y1, n, g0):
        rot = math.degrees(math.atan2(y1 - y0, x1 - x0))
        for i in range(n):
            t = i / (n - 1)
            cx, cy = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            if i % 2 == 0:
                chain_link(d, cx, cy, 2.9, 1.9, 1.1, rot, "iron", g=g0 + i)
            else:
                ux, uy = math.cos(math.radians(rot)), math.sin(math.radians(rot))
                d.line(cx - ux * 1.8, cy - uy * 1.8, cx + ux * 1.8, cy + uy * 1.8, 1.4, "iron.l", g=g0 + i)
    chain_line(2.4, 2.4, 17.6, 17.6, 5, 0)
    chain_line(17.6, 2.4, 2.4, 17.6, 5, 10)
    d.disc(10, 10, 2.2, "purple", g=30)
    d.put(9, 9, "purple.h", g=30)
    d.halo("shadow.d", 1.0, g=90)


@pic("curse")
def _(d):
    spiral(d, 10, 8.0, 0.6, 6.8, 1.6, 2.0, "purple", 90)
    for i, (x, l) in enumerate(((5.0, 4.0), (10.0, 6.0), (15.0, 3.6))):
        d.line(x, 13.0, x, 13.0 + l, 2.0, "purple", g=3 + i)
        d.disc(x, 13.6 + l, 1.3, "purple", g=3 + i)


@pic("invisibility")
def _(d):
    figure(d, "stand", 10, "shadow.l", oy=2)
    inner = [(x, y) for y in range(20) for x in range(20)
             if d.m[y][x] and all(0 <= x + ox < 20 and 0 <= y + oy < 20 and d.m[y + oy][x + ox]
                                  for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for (x, y) in inner:
        d.m[y][x] = None
    for y in range(20):
        for x in range(20):
            if d.m[y][x] and (x + y) % 3 == 0:
                d.m[y][x] = None
    sparkle(d, 16.6, 5.0, 2.6, "W", g=5)


@pic("living_shadow")
def _(d):
    d.disc(10, 6.4, 4.4, "black")
    d.tri((6.0, 4.0), (5.0, 0.4), (8.4, 3.0), "black")
    d.tri((14.0, 4.0), (15.0, 0.4), (11.6, 3.0), "black")
    d.poly([(4.0, 10.0), (16.0, 10.0), (18.0, 19.0), (14.0, 16.6), (10.0, 19.4), (6.0, 16.6), (2.0, 19.0)], "black")
    d.rect(7, 6, 8, 6, "red.l", g=3)
    d.rect(12, 6, 13, 6, "red.l", g=3)


@pic("devour")
def _(d):
    d.disc(10, 10, 9.0, "shadow")
    d.ellipse(10, 11.4, 6.6, 4.4, "k")
    for x in (5.4, 8.4, 11.4, 14.4):
        d.tri((x - 1.3, 7.0), (x + 1.3, 7.0), (x, 10.6), "white", g=3)
    for x in (6.9, 9.9, 12.9):
        d.tri((x - 1.3, 15.8), (x + 1.3, 15.8), (x, 12.2), "white", g=4)
    d.rect(6, 3, 7, 4, "red.l", g=5)
    d.rect(12, 3, 13, 4, "red.l", g=5)


@pic("pain_link")
def _(d):
    d.heart(5.4, 5.6, 0.5, "heart")
    d.heart(14.6, 14.4, 0.5, "heart", g=2)
    chain_link(d, 8.6, 9.0, 2.8, 1.8, 1.1, 45, "iron", g=4)
    chain_link(d, 11.4, 11.2, 2.8, 1.8, 1.1, -45, "iron", g=5)


@pic("steal_blessing")
def _(d):
    d.star(12.6, 6.0, 5.6, 2.4, 5, -90, "gold")
    hand(d, 0, 6, "shadow", rows=["...##.##.##...", "...##.##.##.##", "...##.##.##.##", "##.###########",
                                    ".##.##########", "..############", "...##########.", "....#########.",
                                    ".....######..."], g=3)
    d.put(12, 6, "W")


@pic("nightmare")
def _(d):
    moon(d, 10, 10, 8.8, "purple", 4.2, -2.8)
    d.ellipse(7.0, 12.4, 2.2, 1.4, "yellow", g=3)
    d.ellipse(7.0, 12.4, 0.7, 1.3, "k")
    for (x, y) in ((15.0, 15.6), (17.0, 12.6)):
        d.rect(int(x), int(y), int(x) + 1, int(y), "W")


@pic("possession")
def _(d):
    d.rect(3, 1, 16, 2, "wood")
    d.rect(9, 0, 10, 5, "wood", g=1)
    for (x0, x1, y1) in ((4.0, 5.6, 10.6), (15.0, 14.4, 10.6), (9.5, 10.0, 6.0)):
        d.line(x0, 2.6, x1, y1, 0.9, "white", g=3)
    vfig(d, 10.0, 18.8, 13.6, "shadow", g=5, arms="out")


# ---------------------------------------------------------------------------------------------- MAGMA


@pic("molten_rock")
def _(d):
    d.poly([(3.0, 16.0), (2.0, 9.6), (6.0, 4.0), (12.0, 2.6), (17.0, 6.0), (18.4, 12.6), (14.6, 17.0)], "obsidian")
    d.path([(4.4, 10.0), (8.0, 9.0), (10.0, 12.0), (14.6, 10.6)], 1.2, "lava.l", g=1)
    d.path([(8.0, 9.0), (9.6, 5.0)], 1.2, "lava.l", g=1)
    d.path([(10.0, 12.0), (9.0, 15.6)], 1.2, "lava.l", g=1)


@pic("lava_pool")
def _(d):
    d.ellipse(10, 13.0, 9.6, 5.6, "stone")
    d.ellipse(10, 13.0, 7.6, 4.0, "lava", g=1)
    d.ellipse(9.0, 12.4, 4.0, 1.8, "flame", g=2)
    for i, (x, y, r) in enumerate(((13.6, 11.0, 1.4), (6.0, 14.6, 1.0), (11.0, 5.0, 1.6), (7.0, 3.0, 1.0))):
        d.ring(x, y, r, max(0, r - 0.9), "lava.l", g=4 + i)


@pic("obsidian")
def _(d):
    cube(d, 10, 10.6, 1.0, "obsidian")
    d.path([(4.0, 7.0), (6.6, 10.0), (5.0, 13.0)], 1.0, "violet")
    d.path([(14.0, 8.0), (12.6, 11.6), (15.4, 13.6)], 1.0, "violet")
    d.px([(9, 4), (10, 5)], "W")


@pic("magma_armor")
def _(d):
    chestplate(d, 10, 10.4, "obsidian")
    d.path([(5.0, 4.6), (8.0, 8.0), (10.0, 6.0), (12.0, 9.6), (15.0, 4.6)], 1.2, "lava.l", g=3)
    d.path([(10.0, 9.0), (10.0, 17.0)], 1.2, "lava.l", g=3)
    d.path([(6.6, 12.6), (13.4, 13.6)], 1.2, "lava", g=3)


@pic("eruption")
def _(d):
    d.poly([(0.6, 18.6), (6.6, 8.0), (13.4, 8.0), (19.4, 18.6)], "dirt")
    d.poly([(6.6, 8.0), (13.4, 8.0), (12.4, 10.0), (11.0, 9.0), (10.0, 12.6), (8.6, 9.6), (7.4, 10.6)], "lava", g=1)
    flame(d, 10, 8.6, 0.42, "lava", "flame", None, g=3)
    for (x, y) in ((4.0, 3.0), (16.0, 2.0), (6.4, 0.6), (14.0, 5.0)):
        d.rect(int(x), int(y), int(x) + 1, int(y) + 1, "lava", g=6)


# ---------------------------------------------------------------------------------------------- VAPOR


@pic("scald")
def _(d):
    drop(d, 10, 12.4, 5.2, "aqua")
    d.rect(8, 13, 11, 14, "red.l", g=4)
    for i, x in enumerate((5.0, 10.0, 15.0)):
        pts = [(x + 1.2 * math.sin(t * math.pi * 2.5), 7.0 - t * 6.4) for t in [k / 10 for k in range(11)]]
        if i != 1:
            d.path(pts, 1.4, "cloud", g=5 + i)
    d.path([(10 + 1.2 * math.sin(t * math.pi * 2.5), 3.6 - t * 3.0) for t in [k / 8 for k in range(9)]], 1.4, "cloud", g=8)


@pic("steam_propulsion")
def _(d):
    for i, (x, y, r) in enumerate(((5.6, 16.0, 3.6), (14.4, 16.0, 3.6), (10.0, 15.0, 4.2))):
        d.disc(x, y, r, "cloud", g=i)
    d.arrow(10, 11.0, 10, 0.6, 3.4, 4.6, "orange", g=4, hw=5.0)


@pic("hot_mist")
def _(d):
    cloud(d, 10, 6.6, 1.0, "cloud")
    for i, x in enumerate((5.0, 10.0, 15.0)):
        pts = [(x + 1.2 * math.sin(t * math.pi * 3), 13.0 + t * 6.0) for t in [k / 12 for k in range(13)]]
        d.path(pts, 1.4, "orange", g=4 + i)


@pic("geyser")
def _(d):
    d.ellipse(10, 17.4, 9.0, 2.0, "stone")
    d.rect(7, 6, 12, 16, "aqua", g=1)
    d.rect(9, 6, 10, 16, "W")
    cloud(d, 10, 4.0, 0.84, "foam", g=3)
    for (x, y) in ((2.0, 9.0), (17.0, 9.6), (4.0, 13.0), (15.6, 13.0)):
        drop(d, x, y, 1.2, "aqua", 1.6, shine=False, g=6)

# ---------------------------------------------------------------------------------------------- RAIO


@pic("lightning_bolt")
def _(d):
    bolt(d, 10, 10, 1.05, "yellow")


@pic("paralyze")
def _(d):
    vfig(d, 10, 18.8, 16.0, "p", arms="out")
    for i, (x, y) in enumerate(((3.0, 4.0), (17.0, 4.6), (2.4, 13.6), (17.6, 13.0))):
        d.path([(x - 1.4, y - 2.0), (x + 0.8, y - 0.4), (x - 0.8, y + 0.6), (x + 1.4, y + 2.4)], 1.2, "yellow", g=5 + i)


@pic("arc_lightning")
def _(d):
    pts = [(3.0, 15.0), (10.0, 5.0), (17.0, 14.0)]
    zig = [(3.0, 15.0), (5.6, 10.4), (4.6, 9.6), (7.6, 6.6), (10.0, 5.0), (12.0, 9.0), (13.6, 8.4), (15.0, 12.0), (17.0, 14.0)]
    d.path(zig, 1.8, "yellow")
    for i, (x, y) in enumerate(pts):
        d.disc(x, y, 2.6, "red" if i != 1 else "red", g=3 + i)
        d.ring(x, y, 2.6, 1.6, "cyan.l", g=6 + i)


@pic("magnet")
def _(d):
    # ELETROIMA deitado (abertura para a direita) com arco eletrico entre os polos
    # (magnetism, da Terra, e o ima vermelho em pe puxando blocos de ferro)
    cx, cy = 8.0, 10.0
    d.arc(cx, cy, 7.4, 3.2, 90, 270, "blue")
    d.rect(int(cx), 3, int(cx + 5), 6, "blue")
    d.rect(int(cx), 14, int(cx + 5), 16, "blue")
    for i, x in enumerate((9, 11)):
        d.rect(x, 3, x, 6, "copper", g=2)
        d.rect(x, 14, x, 16, "copper", g=2)
    d.rect(int(cx + 6), 3, int(cx + 8), 6, "steel", g=1)
    d.rect(int(cx + 6), 14, int(cx + 8), 16, "steel", g=1)
    d.path([(15.4, 7.0), (13.4, 8.8), (17.0, 10.4), (14.4, 12.6)], 1.3, "yellow", g=6)
    sparkle(d, 18.4, 9.6, 1.8, "W", g=8)


@pic("overcharge")
def _(d):
    bolt(d, 8.0, 10.0, 0.95, "yellow")
    chevron(d, 15.6, 6.0, 3.4, 1.8, 0, -1, "cyan", g=4)
    chevron(d, 15.6, 10.6, 3.4, 1.8, 0, -1, "cyan.l", g=5)
    chevron(d, 15.6, 15.2, 3.4, 1.8, 0, -1, "cyan.d", g=6)


@pic("thunderstorm")
def _(d):
    cloud(d, 10, 5.6, 1.0, "storm")
    bolt(d, 10.4, 13.6, 0.6, "yellow", g=4)
    for x in (3.6, 16.0):
        d.line(x + 0.6, 11.6, x - 0.4, 14.4, 1.2, "aqua", g=6)


@pic("call_thunder")
def _(d):
    d.rect(0, 17, 19, 18, "stone")
    d.rect(9, 5, 10, 17, "copper", g=1)
    d.tri((9.5, 2.0), (11.6, 6.0), (7.4, 6.0), "copper", g=1)
    d.path([(9.6, 2.0), (12.6, 0.0)], 1.0, "W")
    d.poly([(13.6, 0.0), (17.6, 0.0), (14.6, 4.6), (17.0, 4.6), (12.0, 11.0), (13.4, 6.4), (11.2, 6.4)], "yellow", g=3)
    for (x, y) in ((4.0, 15.0), (15.0, 15.0)):
        d.rect(x, y, x + 1, y, "yellow.l", g=5)


# ---------------------------------------------------------------------------------------------- SOL


@pic("solar_ray")
def _(d):
    sun(d, 10, 4.6, 3.4, 8, 2.4, "yellow", "flame")
    d.taper(10, 8.0, 10, 18.6, 6.0, "yellow.l", g=4)
    d.line(10, 9.0, 10, 17.6, 1.4, "W", g=5)
    d.ellipse(10, 18.4, 7.0, 1.2, "orange", g=6)


@pic("holy_flame")
def _(d):
    flame(d, 10, 19.2, 1.02, "gold", "W", None)
    cross(d, 10, 12.4, 2.6, 1.6, "orange", g=6, latin=True)


@pic("solar_crown")
def _(d):
    for i in range(7):
        a = math.radians(-90 + (i - 3) * 26)
        d.line(10 + 5.6 * math.cos(a), 10 + 5.6 * math.sin(a), 10 + 9.4 * math.cos(a), 10 + 9.4 * math.sin(a), 1.6,
               "yellow", g=1)
    crown(d, 10, 12.4, "gold", "orange", s=0.88)
    d.disc(10, 13.4, 1.8, "flame", g=8)


@pic("dawn")
def _(d):
    d.fill(lambda x, y: (x - 10) ** 2 + (y - 13) ** 2 <= 6.0 ** 2 and y <= 13, "flame")
    for i in range(5):
        a = math.radians(180 + 22 + i * 34)
        d.line(10 + 7.4 * math.cos(a), 13 + 7.4 * math.sin(a), 10 + 9.8 * math.cos(a), 13 + 9.8 * math.sin(a), 1.6,
               "yellow", g=2)
    d.rect(0, 13, 19, 14, "orange", g=3)
    d.rect(2, 16, 17, 16, "orange.d", g=4)
    d.rect(5, 18, 14, 18, "orange.d", g=5)


# ---------------------------------------------------------------------------------------------- INFERNO


@pic("black_flame")
def _(d):
    flame(d, 10, 19.2, 1.02, "black", "red", None)


@pic("infernal_chain")
def _(d):
    chain_link(d, 5.0, 15.0, 5.0, 3.2, 1.7, -45, "iron")
    d.line(7.4, 12.6, 12.6, 7.4, 2.6, "iron", g=1)
    chain_link(d, 15.0, 5.0, 5.0, 3.2, 1.7, -45, "iron", g=2)
    flame(d, 4.0, 10.6, 0.34, "fire", "flame", None, g=5)
    flame(d, 14.6, 17.6, 0.36, "fire", "flame", None, g=8)
    flame(d, 10.6, 6.0, 0.28, "fire", "flame", None, g=11)


@pic("blood_pact")
def _(d):
    scroll(d, 9.0, 8.6, "paper", lines=True)
    d.line(4.6, 18.6, 12.0, 11.0, 2.0, "steel", g=4)
    d.line(14.0, 9.0, 16.0, 7.0, 1.6, "gold", g=5)
    d.line(12.0, 11.0, 15.0, 8.0, 1.6, "wood", g=6)
    d.line(10.6, 9.6, 13.6, 12.6, 1.6, "gold", g=7)
    drop(d, 15.6, 15.6, 2.4, "blood", 1.6, shine=False, g=9)


@pic("hell_portal")
def _(d):
    d.ellipse(10, 11.0, 7.4, 8.4, "fire")
    d.ellipse(10, 11.0, 5.0, 6.2, "blood", g=1)
    spiral(d, 10, 11.0, 0.4, 4.2, 1.3, 1.2, "k", 0, g=2)
    d.tri((3.6, 6.0), (1.0, 0.6), (6.6, 3.6), "bone", g=4)
    d.tri((16.4, 6.0), (19.0, 0.6), (13.4, 3.6), "bone", g=5)


# ---------------------------------------------------------------------------------------------- NATUREZA


@pic("growth")
def _(d):
    d.fill(lambda x, y: ((x - 10) / 8.0) ** 2 + ((y - 19) / 3.6) ** 2 <= 1, "dirt")
    d.line(10, 16.0, 10, 7.0, 1.6, "grass.d", g=1)
    leaf(d, 5.6, 7.6, 0.72, -25, "leaf", g=2)
    leaf(d, 14.4, 5.6, 0.72, 25, "leaf", g=3)


@pic("thorns")
def _(d):
    d.curve([(2.0, 18.0), (6.0, 8.0), (16.0, 12.0), (17.6, 2.0)], 2.2, "leaf.d")
    for (x, y, dx, dy) in ((4.6, 12.0, -2.6, -0.6), (8.0, 9.6, 0.0, -2.6), (11.0, 10.4, 0.4, 2.6), (15.0, 9.6, 2.6, 0.4),
                           (16.4, 5.0, -2.6, -0.4)):
        d.tri((x - dy * 0.35 + dx * 0.0, y + dx * 0.35), (x + dy * 0.35, y - dx * 0.35), (x + dx, y + dy), "bone", g=4)
    leaf(d, 17.4, 2.0, 0.34, -40, "leaf", g=6)


@pic("spores")
def _(d):
    mushroom(d, 9.0, 10.6, 0.92, "purple", "bone", "pink.l")
    for i, (x, y) in enumerate(((16.0, 3.0), (17.6, 7.6), (14.0, 1.0), (3.0, 2.0), (1.6, 6.0))):
        d.disc(x, y, 1.0, "green.l", g=6 + i)


@pic("vines")
def _(d):
    spiral(d, 10, 9.0, 1.0, 6.6, 1.2, 1.8, "leaf.d", 180, g=0)
    d.curve([(3.4, 9.6), (2.6, 15.0), (6.0, 19.0)], 1.8, "leaf.d", g=0)
    for i, (x, y, r) in enumerate(((14.6, 4.6, -40), (16.0, 12.0, 30), (6.0, 3.6, 40), (8.6, 15.6, -30))):
        leaf(d, x, y, 0.42, r, "leaf", g=3 + i)


@pic("bloom")
def _(d):
    d.line(10, 12.0, 10, 19.0, 1.6, "grass.d")
    leaf(d, 13.4, 16.0, 0.42, 20, "leaf", g=1)
    flower(d, 10, 8.4, 4.4, 3.4, 5, "pink", "yellow", g=3)


@pic("summon_ent")
def _(d):
    d.rect(6, 9, 13, 18, "wood")
    tree_canopy(d, 10, 5.0, "leaf", g=1, s=1.0)
    d.rect(7, 11, 8, 12, "k")
    d.rect(11, 11, 12, 12, "k")
    d.rect(8, 14, 11, 15, "k")
    d.line(6.0, 13.0, 2.0, 9.6, 1.8, "wood", g=4)
    d.line(13.0, 13.0, 17.6, 10.0, 1.8, "wood", g=4)


@pic("photosynthesis")
def _(d):
    sun(d, 14.6, 5.0, 2.8, 8, 2.0, "yellow", "flame", g=5)
    leaf(d, 8.6, 12.0, 1.06, -45, "leaf")
    d.line(2.0, 18.4, 4.6, 15.6, 1.4, "leaf.d", g=8)


# ---------------------------------------------------------------------------------------------- GELO


@pic("freeze")
def _(d):
    snowflake(d, 10, 10, 9.0, "ice")


@pic("ice_lance")
def _(d):
    d.taper(1.0, 19.0, 19.0, 1.0, 6.0, "ice")
    d.taper(3.0, 17.0, 17.6, 2.4, 2.0, "white", g=1)
    for (x, y) in ((4.0, 8.0), (13.0, 17.0)):
        sparkle(d, x, y, 2.0, "W", g=3)


@pic("slippery_ground")
def _(d):
    d.poly([(0.6, 12.6), (14.0, 10.0), (19.4, 13.4), (6.0, 17.0)], "ice")
    d.line(4.0, 13.6, 11.0, 12.4, 1.0, "W")
    vfig(d, 12.0, 11.6, 9.6, "p", g=4, arms="up")
    for (y, x0, x1) in ((5.0, 2.0, 6.0), (8.0, 3.0, 7.0)):
        d.line(x0, y, x1, y, 1.0, "white", g=6)


@pic("ice_bridge")
def _(d):
    d.fill(lambda x, y: y >= 7.0 and y <= 11.0 + 0.0 and (x - 10) ** 2 / 100 + (y - 16) ** 2 / 81 <= 1.0, "ice")
    d.fill(lambda x, y: 0.5 <= x <= 19.5 and 7.0 <= y <= 9.6, "ice", g=0)
    d.fill(lambda x, y: (x - 10) ** 2 / 64 + (y - 18) ** 2 / 64 <= 1.0 and y > 9.6, None)
    d.rect(0, 9, 3, 18, "ice", g=1)
    d.rect(16, 9, 19, 18, "ice", g=2)
    d.line(1.0, 7.4, 18.0, 7.4, 1.0, "W")
    waves(d, (17.6,), "water", 0.8, 1.6, 4.0, 15.6, g=4, period=4.0)


@pic("ice_armor")
def _(d):
    chestplate(d, 10, 10.4, "ice")
    for (x, y) in ((6.0, 5.0), (13.6, 5.6), (10.0, 12.0)):
        d.line(x - 1.6, y + 1.6, x + 1.6, y - 1.6, 1.0, "W", g=4)


@pic("blizzard")
def _(d):
    cloud(d, 10, 4.6, 0.9, "cloud")
    for i, (x, y) in enumerate(((4.0, 13.0), (11.0, 15.6), (16.0, 11.0))):
        snowflake(d, x, y, 3.2, "ice", g=4 + i, w=1.2)
    for (y, x0, x1) in ((11.0, 0.6, 3.0), (17.6, 1.0, 6.0)):
        d.line(x0, y, x1, y, 1.0, "white", g=9)


@pic("ice_statue")
def _(d):
    # bloco de gelo com um boneco ESCURO preso dentro (contraste) + reflexos
    d.rect(3, 1, 16, 18, "ice")
    vfig(d, 9.6, 17.0, 14.6, "water.d", g=1, arms="up")
    d.line(5.0, 4.0, 8.0, 1.0, 1.0, "W")
    d.line(4.0, 10.0, 6.0, 8.0, 1.0, "W")
    d.line(14.0, 16.0, 15.6, 14.4, 1.0, "W")


# ---------------------------------------------------------------------------------------------- PUREZA


@pic("greater_heal")
def _(d):
    cross(d, 10, 10, 8.4, 5.0, "white")
    plus(d, 10, 10, 5.4, 2.0, "heart", g=2)
    for (x, y) in ((3.4, 3.4), (16.6, 16.6)):
        sparkle(d, x, y, 2.6, "aqua.l", g=5)


@pic("holy_water")
def _(d):
    flask(d, 10, 10.4, "aqua", "glass", "gold")
    cross(d, 10, 13.0, 2.4, 1.4, "W", g=6, latin=True)


@pic("cleanse")
def _(d):
    for i, (x, y, r) in enumerate(((7.0, 12.0, 5.4), (14.6, 7.0, 3.6), (14.0, 15.6, 2.4), (4.0, 4.0, 2.0))):
        d.disc(x, y, r, "glass", g=i * 3)
        d.ring(x, y, r, r - 1.0, "aqua.l", g=i * 3 + 1)
    sparkle(d, 7.0, 12.0, 3.0, "W", g=20)


@pic("protective_bubble")
def _(d):
    d.disc(10, 10, 9.2, "glass")
    vfig(d, 10, 17.0, 11.0, "p", g=2)
    d.ring(10, 10, 9.2, 8.2, "aqua", g=3)
    d.line(4.0, 6.0, 6.0, 3.6, 1.2, "W")


# ---------------------------------------------------------------------------------------------- ABISMO


@pic("venom")
def _(d):
    d.poly([(3.0, 1.0), (13.0, 1.0), (10.0, 6.0), (6.0, 6.0)], "white")
    d.tri((6.0, 5.6), (10.0, 5.6), (8.0, 12.0), "white", g=1)
    drop(d, 13.0, 13.4, 3.6, "poison", g=3)
    d.disc(8.0, 15.4, 1.3, "poison", g=5)


@pic("ink")
def _(d):
    d.disc(10, 10.6, 6.2, "ink")
    for i in range(7):
        a = math.radians(i * 51 + 10)
        r = 6.8 + (i % 3) * 1.2
        d.disc(10 + r * math.cos(a), 10.6 + r * math.sin(a), 1.4 + (i % 2) * 0.6, "ink", g=0)
    d.halo("violet.l", 1.0, g=60)
    d.disc(7.6, 8.0, 1.6, "ink.h", g=3)
    d.disc(12.6, 12.6, 0.8, "ink.l", g=3)


@pic("tentacles")
def _(d):
    pts = [(4.6 + 1.6 * math.sin(t * math.pi), 19.6 - 11.0 * t) for t in [i / 14 for i in range(15)]]
    cx, cy = 10.6, 7.6
    for i in range(1, 30):
        t = i / 29
        a = math.radians(180 + 400 * t)
        r = 5.0 * (1 - t) + 1.0
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    n = len(pts)
    for i, (x, y) in enumerate(pts):
        d.disc(x, y, 3.3 * (1 - i / n) + 0.8, "purple", g=0)
    for k in (3, 7, 11, 16):
        x, y = pts[k]
        d.disc(x + 1.4, y, 0.8, "pink.l", g=2)
    tentacle2(d, 16.4, 19.8, 7.0, "magenta", g=4, flip=True, r0=2.0)


@pic("abyssal_pressure")
def _(d):
    d.rect(1, 17, 18, 18, "sand")
    d.ellipse(10, 14.0, 5.4, 3.0, "aqua", g=1)
    d.ellipse(8.4, 13.0, 1.8, 0.8, "aqua.h", g=2)
    for i, x in enumerate((4.0, 10.0, 16.0)):
        d.arrow(x, 0.6, x, 9.6 if i == 1 else 11.0, 2.2, 3.4, "W", g=3 + i)


@pic("abyssal_drown")
def _(d):
    d.ring(10, 3.6, 2.6, 1.2, "iron")
    d.rect(9, 6, 10, 16, "iron", g=1)
    d.rect(5, 8, 14, 9, "iron", g=2)
    d.arc(10, 10, 7.6, 5.6, 20, 160, "iron", g=3)
    d.tri((1.0, 11.0), (4.6, 10.0), (3.0, 14.0), "iron", g=4)
    d.tri((19.0, 11.0), (15.4, 10.0), (17.0, 14.0), "iron", g=5)
    for (x, y, r) in ((15.6, 3.0, 1.4), (17.6, 6.4, 1.0)):
        d.ring(x, y, r, r - 0.8, "aqua.l", g=7)


# ---------------------------------------------------------------------------------------------- AREIA


@pic("vitrify")
def _(d):
    d.fill(lambda x, y: ((x - 10) / 9.6) ** 2 + ((y - 19.6) / 4.4) ** 2 <= 1, "sand")
    d.poly([(4.0, 15.0), (7.0, 3.0), (11.0, 4.0), (9.0, 15.0)], "glass", g=1)
    d.poly([(10.0, 15.6), (13.6, 6.0), (17.0, 8.0), (14.0, 16.0)], "glass", g=2)
    d.line(7.0, 5.0, 6.0, 10.0, 1.0, "W")
    d.line(14.0, 8.4, 13.0, 11.6, 1.0, "W")


@pic("dune")
def _(d):
    d.fill(lambda x, y: y >= 12.0 - 4.0 * math.sin((x - 2.0) / 7.0 * math.pi * 0.5) and y <= 19, "sand.d")
    d.fill(lambda x, y: y >= 14.0 - 6.0 * math.exp(-((x - 13.0) / 4.6) ** 2) and y <= 19, "sand", g=1)
    sun(d, 5.0, 5.0, 2.6, 8, 1.8, "orange", "yellow", g=4)


@pic("sandstorm")
def _(d):
    for i, (y, x0, x1, c) in enumerate(((4.0, 2.0, 15.0, 3.0), (9.6, 4.0, 18.0, 2.6), (15.0, 1.0, 13.0, 2.6))):
        d.line(x0, y, x1, y, 2.0, "sand" if i != 1 else "sand.l", g=i)
        d.arc(x1, y + c, c + 1.0, c - 1.0, 180, 300, "sand" if i != 1 else "sand.l", g=i)
    for (x, y) in ((17.0, 4.0), (2.0, 12.0), (16.0, 16.0), (8.0, 18.0), (11.0, 1.0)):
        d.rect(int(x), int(y), int(x), int(y), "dirt")


@pic("sinkhole")
def _(d):
    d.ellipse(10, 11.0, 9.6, 7.0, "sand")
    d.ellipse(10, 11.6, 7.0, 4.6, "dirt", g=1)
    d.ellipse(10, 12.6, 4.6, 2.6, "k")
    for (x, y) in ((6.0, 8.6), (13.6, 9.0), (10.0, 14.6)):
        d.rect(int(x), int(y), int(x) + 1, int(y), "stone", g=4)


@pic("mirage")
def _(d):
    d.line(9.0, 18.0, 11.0, 6.0, 2.0, "wood")
    for (a, l) in ((-150, 7.0), (-110, 6.0), (-60, 6.6), (-20, 6.0), (-180, 5.0)):
        r = math.radians(a)
        d.taper(11.0, 6.0, 11.0 + l * math.cos(r), 6.0 + l * math.sin(r) + 2.0, 3.0, "leaf", g=2)
    for i, y in enumerate((13.6, 16.6)):
        pts = [(1.0 + t * 18.0, y + 0.8 * math.sin(t * math.pi * 6)) for t in [k / 20 for k in range(21)]]
        d.path(pts, 1.0, "sky", g=6 + i)


# ---------------------------------------------------------------------------------------------- CRISTAL


@pic("shards")
def _(d):
    crystal(d, 6.0, 12.0, 6.0, 2.6, "crystal", rot=-20)
    crystal(d, 14.0, 7.0, 5.0, 2.2, "crystal", rot=35, g=3)
    crystal(d, 15.0, 16.0, 3.0, 1.6, "crystal", rot=-60, g=6)
    crystal(d, 4.0, 3.4, 2.4, 1.4, "crystal", rot=50, g=9)


@pic("crystallize")
def _(d):
    crystal(d, 10, 8.6, 8.0, 3.6, "crystal")
    crystal(d, 4.6, 12.6, 5.0, 2.4, "crystal", rot=-25, g=3)
    crystal(d, 15.4, 12.6, 5.0, 2.4, "crystal", rot=25, g=6)
    d.rect(1, 17, 18, 18, "stone", g=9)


@pic("crystal_armor")
def _(d):
    chestplate(d, 10, 10.4, "crystal")
    crystal(d, 10, 8.0, 3.6, 1.8, "white", g=4)


@pic("reflecting_prism")
def _(d):
    d.poly([(10.0, 2.0), (16.4, 10.0), (10.0, 18.0), (3.6, 10.0)], "crystal")
    d.poly([(10.0, 2.0), (10.0, 18.0), (3.6, 10.0)], "crystal.l", g=1)
    d.line(0.6, 3.0, 6.0, 6.6, 1.6, "W", g=3)
    d.arrow(13.4, 6.6, 19.4, 2.6, 1.6, 2.6, "yellow", g=4)
    d.arrow(13.4, 13.0, 19.4, 17.0, 1.6, 2.6, "cyan", g=5)


# ---------------------------------------------------------------------------------------------- GRAVIDADE


@pic("crush")
def _(d):
    d.poly([(3.0, 2.0), (17.0, 2.0), (15.0, 6.0), (13.0, 6.0), (13.0, 9.0), (17.0, 11.0), (3.0, 11.0), (7.0, 9.0),
            (7.0, 6.0), (5.0, 6.0)], "iron")
    d.rect(0, 16, 19, 18, "stone", g=1)
    d.path([(6.0, 16.0), (7.6, 18.6)], 1.0, "k")
    d.path([(13.0, 16.0), (12.0, 18.6)], 1.0, "k")
    for x in (4.0, 10.0, 16.0):
        chevron(d, x, 13.4, 2.4, 1.4, 0, 1, "p", g=4)


@pic("mass_levitate")
def _(d):
    cube(d, 5.0, 12.0, 0.5, "stone")
    cube(d, 14.6, 8.0, 0.5, "dirt", g=3)
    cube(d, 7.6, 4.0, 0.4, "p", g=6)
    for (x, y) in ((5.0, 18.6), (14.6, 15.0)):
        d.line(x - 2.0, y, x + 2.0, y, 1.2, "p.l", g=9)


@pic("debris_orbit")
def _(d):
    chain_link(d, 10, 10, 9.0, 4.4, 1.0, -20, "p.d")
    d.disc(10, 10, 3.4, "void")
    for i, (t, m) in enumerate(((0.2, "stone"), (2.2, "dirt"), (4.2, "stone"))):
        u, v = 9.0 * math.cos(t), 4.4 * math.sin(t)
        a = math.radians(-20)
        x, y = 10 + u * math.cos(a) - v * math.sin(a), 10 + u * math.sin(a) + v * math.cos(a)
        d.rect(int(x) - 1, int(y) - 1, int(x) + 1, int(y) + 1, m, g=3 + i)


@pic("attraction_point")
def _(d):
    d.ellipse(10, 10, 9.4, 3.6, "orange", rot=-15)
    d.ellipse(10, 10, 7.0, 2.2, "yellow", g=1, rot=-15)
    d.disc(10, 10, 4.6, "black", g=2)
    d.ring(10, 10, 4.6, 3.8, "violet", g=3)


@pic("invert_gravity")
def _(d):
    fig = Pic(20, 20)
    vfig(fig, 9.0, 18.6, 15.0, "p", arms="up")
    for y in range(20):
        for x in range(20):
            if fig.m[19 - y][x]:
                d.m[y][x] = fig.m[19 - y][x]
    d.arrow(16.6, 13.0, 16.6, 1.6, 1.8, 3.0, "W", g=5)
    d.rect(0, 0, 19, 0, "stone", g=8)
    d.rect(0, 19, 19, 19, "stone.d", g=9)


# ---------------------------------------------------------------------------------------------- ASTRAL


@pic("falling_star")
def _(d):
    d.taper(1.0, 19.0, 11.0, 9.0, 4.4, "sky")
    d.star(13.0, 7.0, 6.0, 2.6, 5, -90, "yellow", g=2)
    d.disc(13.0, 7.4, 1.2, "W")


@pic("zero_gravity")
def _(d):
    vfig(d, 10.6, 16.0, 13.0, "p", arms="out")
    for i, (x, y, r) in enumerate(((3.0, 3.0, 2.2), (17.0, 15.6, 2.2), (16.0, 3.0, 1.6), (3.6, 15.0, 1.6))):
        sparkle(d, x, y, r, "yellow" if i < 2 else "W", g=6 + i)
    d.ellipse(10.6, 18.6, 4.6, 0.8, "p.d", g=12)


@pic("star_teleport")
def _(d):
    d.star(13.4, 6.6, 6.0, 2.6, 5, -90, "yellow")
    for i, (x, y) in enumerate(((2.0, 18.0), (4.6, 15.4), (7.2, 12.8))):
        d.disc(x, y, 1.2, "sky", g=3 + i)
    vfig(d, 4.0, 13.0, 7.6, "sky.l", g=8)


@pic("constellation")
def _(d):
    pts = [(3.0, 15.0), (7.0, 8.0), (12.0, 10.0), (16.0, 3.0), (17.0, 14.0)]
    d.path(pts[:4], 1.0, "sky.d")
    d.path([pts[2], pts[4]], 1.0, "sky.d")
    for i, (x, y) in enumerate(pts):
        d.star(x, y, 2.8 if i in (1, 3) else 2.2, 1.0, 4, -90, "W" if i == 3 else "yellow.l", g=3 + i)


@pic("astral_projection")
def _(d):
    vfig(d, 7.0, 18.8, 11.0, "p.d")
    fig = Pic(20, 20)
    vfig(fig, 12.6, 13.0, 12.0, "sky.l", arms="up")
    for y in range(20):
        for x in range(20):
            if fig.m[y][x] and (x + y) % 2 == 0 or (fig.m[y][x] and y < 6):
                d.m[y][x] = ("sky", "l", 7)
    sparkle(d, 17.0, 16.0, 2.2, "W", g=9)


# ---------------------------------------------------------------------------------------------- VAZIO


@pic("erase")
def _(d):
    cube(d, 9.0, 10.6, 0.92, "p")
    for (x, y) in ((14, 4), (16, 6), (15, 9), (17, 3), (13, 7), (16, 11), (18, 8)):
        d.put(x, y, None)
    for (x, y) in ((17, 1), (18, 5), (19, 10), (16, 13)):
        d.rect(x, y, x, y, "p.l", g=8)
    d.recolor(lambda x, y: x > 13.0, "void", only="p")


@pic("rift")
def _(d):
    d.poly([(10.0, 0.6), (13.0, 5.0), (11.6, 7.0), (14.6, 11.0), (12.0, 13.0), (13.6, 19.4), (8.0, 13.6), (9.6, 11.6),
            (6.4, 8.0), (9.0, 5.6)], "purple")
    d.poly([(10.0, 3.0), (11.6, 5.6), (10.4, 7.4), (12.6, 11.0), (10.6, 12.6), (11.4, 16.6), (9.4, 13.4), (10.6, 11.6),
            (8.4, 8.0), (10.2, 5.6)], "k", g=1)
    for (x, y) in ((4.0, 4.0), (16.0, 15.0), (4.0, 15.0)):
        d.rect(int(x), int(y), int(x), int(y) + 1, "violet.l", g=3)


@pic("banish")
def _(d):
    d.ellipse(13.6, 10.0, 5.0, 8.6, "void")
    d.ellipse(13.6, 10.0, 3.2, 6.6, "k", g=1)
    vfig(d, 5.0, 16.0, 11.0, "p.d", g=4)
    d.arrow(1.0, 4.0, 10.0, 4.0, 1.8, 3.0, "purple.l", g=6)


@pic("warp")
def _(d):
    d.disc(10, 10, 9.0, "void")
    spiral(d, 10, 10, 0.6, 8.0, 2.2, 1.6, "violet", 0, g=2)
    d.disc(10, 10, 1.6, "W")


@pic("annihilate")
def _(d):
    d.disc(10, 10, 9.4, "magenta")
    d.disc(10, 10, 7.6, "purple", g=1)
    d.disc(10, 10, 6.0, "black", g=2)
    d.arc(10, 10, 6.0, 4.8, 200, 280, "void.l", g=3)
    for i in range(6):
        a = math.radians(i * 60 + 15)
        d.rect(int(10 + 9.0 * math.cos(a)), int(10 + 9.0 * math.sin(a)), int(10 + 9.0 * math.cos(a)),
               int(10 + 9.0 * math.sin(a)), "W", g=6)


# ---------------------------------------------------------------------------------------------- CREPUSCULO


@pic("equilibrium")
def _(d):
    d.rect(9, 2, 10, 17, "gold")
    d.rect(5, 17, 14, 18, "gold", g=1)
    d.rect(2, 4, 17, 5, "gold", g=2)
    for (x, m) in ((3.6, "yellow.l"), (16.4, "shadow")):
        d.line(x, 5.0, x - 2.4, 11.0, 0.9, "gold.d", g=3)
        d.line(x, 5.0, x + 2.4, 11.0, 0.9, "gold.d", g=3)
        d.fill(lambda xx, yy, x=x: (xx - x) ** 2 + (yy - 11.0) ** 2 <= 3.4 ** 2 and yy >= 11.0, m, g=4)


@pic("eclipse")
def _(d):
    burst(d, 10, 10, 9.6, 7.4, 12, "yellow", -90, (1.0, 0.85))
    d.disc(10, 10, 7.0, "flame", g=1)
    d.disc(12.0, 9.0, 6.6, "black", g=2)


@pic("duality")
def _(d):
    d.disc(10, 10, 8.8, "white")
    d.fill(lambda x, y: (x > 10 and not ((x - 10) ** 2 + (y - 5.6) ** 2 <= 4.4 ** 2))
           or ((x - 10) ** 2 + (y - 14.4) ** 2 <= 4.4 ** 2 and True) and (x - 10) ** 2 + (y - 10) ** 2 <= 8.8 ** 2
           and not (x <= 10 and (x - 10) ** 2 + (y - 14.4) ** 2 > 4.4 ** 2), "black", g=1)
    d.erase(lambda x, y: (x - 10) ** 2 + (y - 10) ** 2 > 8.8 ** 2)
    d.disc(10, 5.6, 1.4, "black", g=2)
    d.disc(10, 14.4, 1.4, "white", g=3)


@pic("final_judgment")
def _(d):
    for (x0, x1) in ((4.0, 1.0), (16.0, 19.0)):
        d.taper(x0, 0.0, x1, 14.0, 3.0, "yellow.l", g=1)
    sword(d, 10.0, 0.6, 10.0, 18.6, blade="W", guard="gold", grip="gold", w=3.4)
    d.ellipse(10, 18.8, 6.0, 1.0, "yellow", g=9)

def tentacle2(d, x0, y0, h=15.0, m="purple", g=0, flip=False, r0=3.0):
    sx = -1 if flip else 1
    pts = []
    for i in range(40):
        t = i / 39
        x = x0 + sx * (3.6 * math.sin(t * math.pi * 1.25))
        y = y0 - h * t
        pts.append((x, y, r0 * (1 - t) + 0.9))
    for (x, y, r) in pts:
        d.disc(x, y, r, m, g=g)
    tx, ty, _ = pts[-1]
    d.arc(tx - sx * 1.6, ty + 0.4, 2.2, 1.0, 180 if sx > 0 else 270, 360 if sx > 0 else 450, m, g=g)
    for k in (0.15, 0.35, 0.55):
        x, y, r = pts[int(k * 39)]
        d.disc(x - sx * r * 0.45, y, 0.8, "pink.l", g=g + 1)


# ---------------------------------------------------------------------------------------------- MAGO


@pic("explosive_seal")
def _(d):
    d.ring(10, 10, 9.0, 7.4, "p")
    explosion(d, 10, 10, 0.66, "fire", "flame", "W", g=2)
    for a in (45, 135, 225, 315):
        d.disc(10 + 8.2 * math.cos(math.radians(a)), 10 + 8.2 * math.sin(math.radians(a)), 1.1, "W")


@pic("teleport_circle")
def _(d):
    chain_link(d, 10, 15.6, 9.0, 3.4, 1.8, 0, "p")
    d.ellipse(10, 15.6, 5.0, 1.4, "p.d", g=1)
    d.arrow(10, 14.6, 10, 1.4, 3.0, 4.6, "W", g=2, hw=4.6)
    sparkle(d, 3.4, 6.0, 2.2, "p.l", g=4)
    sparkle(d, 16.6, 8.6, 2.2, "p.l", g=4)


@pic("return_rune")
def _(d):
    d.fill(lambda x, y: 3 <= x <= 17 and 2 <= y <= 18 and not ((x < 5 or x > 15) and (y < 4 or y > 16)), "stone")
    d.arc_arrow(10, 10.6, 4.2, -30, 250, 2.0, 3.0, "p", g=2)
    d.rect(4, 3, 4, 3, "stone.h")


@pic("ward")
def _(d):
    d.ring(10, 10, 9.4, 8.0, "p")
    d.path([(10, 2.6), (16.4, 13.7), (3.6, 13.7), (10, 2.6)], 1.6, "W", g=1)
    d.path([(10, 17.4), (3.6, 6.3), (16.4, 6.3), (10, 17.4)], 1.6, "p.l", g=2)
    d.disc(10, 10, 1.6, "W", g=3)


@pic("temp_enchant")
def _(d):
    sword(d, 3.0, 17.0, 17.0, 3.0, blade="steel", w=2.8)
    d.halo("p.l", 1.0, g=40)
    for (x, y, r) in ((5.0, 5.0, 2.8), (15.4, 13.6, 2.6), (12.0, 2.0, 1.8)):
        sparkle(d, x, y, r, "W", g=50)


# ---------------------------------------------------------------------------------------------- BRUXA


@pic("hex")
def _(d):
    d.ring(10, 10.4, 9.2, 7.8, "p")
    pts = [(10 + 7.6 * math.cos(math.radians(-90 + i * 144)), 10.4 + 7.6 * math.sin(math.radians(-90 + i * 144)))
           for i in range(6)]
    d.path(pts, 1.4, "purple.l", g=1)


@pic("voodoo_doll")
def _(d):
    d.disc(10, 6.0, 4.6, "sand")
    d.rect(6, 9, 13, 16, "sand", g=1)
    d.line(6.0, 10.6, 2.0, 12.6, 2.6, "sand", g=1)
    d.line(13.0, 10.6, 17.0, 12.6, 2.6, "sand", g=1)
    d.rect(7, 16, 8, 19, "sand", g=1)
    d.rect(11, 16, 12, 19, "sand", g=1)
    for (x, y) in ((8.0, 5.4), (12.0, 5.4)):
        d.line(x - 1.0, y - 1.0, x + 1.0, y + 1.0, 0.9, "k")
        d.line(x + 1.0, y - 1.0, x - 1.0, y + 1.0, 0.9, "k")
    d.rect(8, 8, 11, 8, "k")
    d.line(14.0, 2.0, 11.0, 12.0, 0.9, "steel", g=6)
    d.disc(14.4, 1.6, 1.3, "red", g=7)
    d.line(2.0, 16.0, 8.4, 13.0, 0.9, "steel", g=8)
    d.disc(1.6, 16.4, 1.3, "red", g=9)


@pic("evil_eye")
def _(d):
    d.disc(10, 10, 9.0, "blue")
    d.disc(10, 10, 6.6, "white", g=1)
    d.disc(10, 10, 4.4, "sky", g=2)
    d.disc(10, 10, 2.2, "k")
    d.put(8, 8, "W")


@pic("frogify")
def _(d):
    d.ellipse(10, 13.0, 8.6, 5.6, "green")
    d.disc(5.6, 7.6, 3.2, "green", g=0)
    d.disc(14.4, 7.6, 3.2, "green", g=0)
    d.disc(5.6, 7.4, 1.8, "white", g=1)
    d.disc(14.4, 7.4, 1.8, "white", g=2)
    d.rect(5, 7, 6, 8, "k")
    d.rect(14, 7, 15, 8, "k")
    d.arc(10, 12.0, 5.0, 4.0, 30, 150, "green.d", g=3)
    crown(d, 10, 3.2, "gold", "red", g=6, s=0.36)


@pic("familiar")
def _(d):
    d.ellipse(10.6, 14.4, 5.6, 4.8, "black")
    d.disc(8.0, 7.6, 4.4, "black")
    d.tri((4.2, 6.0), (4.6, 1.0), (7.6, 4.0), "black")
    d.tri((11.8, 6.0), (11.4, 1.0), (8.6, 4.0), "black")
    d.curve([(15.6, 16.6), (19.2, 14.0), (17.0, 8.6)], 1.8, "black")
    d.ellipse(6.4, 7.6, 1.1, 1.3, "yellow", g=3)
    d.ellipse(9.8, 7.6, 1.1, 1.3, "yellow", g=3)
    d.put(6, 7, "k")
    d.put(9, 7, "k")
    d.put(8, 9, "pink")


# ---------------------------------------------------------------------------------------------- DRUIDA


@pic("wolf_form")
def _(d):
    d.poly([(10.0, 18.6), (4.0, 12.0), (2.6, 6.0), (4.0, 1.0), (7.6, 5.0), (12.4, 5.0), (16.0, 1.0), (17.4, 6.0),
            (16.0, 12.0)], "iron")
    d.poly([(10.0, 18.6), (7.0, 13.0), (13.0, 13.0)], "white", g=1)
    d.tri((4.4, 3.0), (6.6, 5.6), (4.0, 7.0), "pink.d", g=2)
    d.tri((15.6, 3.0), (13.4, 5.6), (16.0, 7.0), "pink.d", g=3)
    d.poly([(5.6, 9.0), (8.4, 9.6), (6.4, 10.6)], "yellow", g=4)
    d.poly([(14.4, 9.0), (11.6, 9.6), (13.6, 10.6)], "yellow", g=5)
    d.rect(9, 15, 10, 16, "k")


@pic("bear_form")
def _(d):
    d.ellipse(10, 13.4, 6.4, 5.0, "brown")
    for i, (x, y) in enumerate(((3.4, 7.0), (7.6, 3.6), (12.4, 3.6), (16.6, 7.0))):
        d.ellipse(x, y, 2.4, 2.8, "brown", g=1 + i)
        d.tri((x - 1.0, y - 2.4), (x + 1.0, y - 2.4), (x, y - 4.4), "bone", g=6 + i)


@pic("dolphin_form")
def _(d):
    d.curve([(3.0, 15.0), (6.0, 4.0), (16.0, 6.0)], 4.4, "sky")
    d.disc(16.6, 6.6, 2.0, "sky", g=0)
    d.tri((17.0, 6.0), (19.6, 7.6), (17.0, 8.0), "sky", g=0)
    d.tri((8.0, 6.6), (9.6, 2.0), (11.0, 6.4), "sky.d", g=1)
    d.tri((3.0, 15.0), (0.4, 14.0), (2.0, 18.0), "sky.d", g=2)
    d.tri((3.0, 15.0), (5.6, 18.4), (1.6, 17.0), "sky.d", g=2)
    d.put(15, 5, "k")
    waves(d, (17.6,), "water", 0.8, 1.6, 6.0, 19.6, g=5, period=4.0)


@pic("falcon_form")
def _(d):
    d.disc(9.0, 9.6, 7.4, "brown")
    d.ellipse(8.6, 14.0, 5.4, 4.0, "white", g=1)
    d.poly([(14.6, 7.0), (19.6, 9.0), (18.6, 13.6), (15.6, 11.6), (13.6, 12.0)], "gold", g=2)
    d.disc(11.0, 7.6, 2.2, "yellow", g=3)
    d.disc(11.4, 7.6, 1.1, "k")
    d.line(8.0, 9.6, 6.6, 15.6, 1.4, "brown.d", g=5)


@pic("spider_form")
def _(d):
    for sx in (-1, 1):
        for i, (ex, ey) in enumerate(((9.0, -6.0), (9.6, -1.0), (9.0, 4.0), (7.0, 8.6))):
            kx, ky = 10 + sx * 5.0, 10 + ey * 0.4 - 2.0
            d.path([(10 + sx * 2.0, 10.6 + i * 0.6 - 1.0), (kx, ky), (10 + sx * ex, 10 + ey)], 1.2, "black", g=1)
    d.disc(10, 13.0, 4.8, "black", g=2)
    d.disc(10, 6.6, 3.0, "black", g=3)
    d.rect(9, 12, 10, 14, "red", g=4)
    d.px([(8, 6), (11, 6)], "red.l")


@pic("elemental_beast_form")
def _(d):
    for i, m in enumerate(("fire", "aqua", "leaf", "yellow", "white", "purple")):
        a0 = -90 + i * 60 - 30
        d.arc(10, 10, 9.6, 5.6, a0, a0 + 60, m, g=i)
    d.disc(10, 10.6, 5.6, "gold", g=10)
    d.ellipse(10, 13.6, 3.0, 2.2, "paper", g=11)
    d.rect(9, 12, 10, 12, "k")
    d.rect(7, 9, 8, 9, "k")
    d.rect(12, 9, 13, 9, "k")


# ---------------------------------------------------------------------------------------------- NECROMANTE


@pic("raise_dead")
def _(d):
    d.fill(lambda x, y: 2 <= x <= 11 and 3 <= y <= 15 and (x - 6.5) ** 2 + (y - 7.5) ** 2 <= 4.6 ** 2 or
           (2 <= x <= 11 and 7.5 <= y <= 15), "stone")
    d.rect(5, 8, 8, 8, "stone.d")
    d.rect(6, 6, 6, 11, "stone.d")
    d.rect(0, 15, 19, 18, "dirt", g=2)
    for (x0, y0, x1, y1) in ((13.0, 15.0, 13.0, 9.0), (15.0, 15.0, 15.6, 7.0), (17.0, 15.0, 18.0, 9.0),
                             (13.0, 12.0, 11.0, 10.0)):
        d.line(x0, y0, x1, y1, 1.4, "bone", g=4)
    d.rect(12, 13, 17, 15, "bone", g=4)


@pic("bone_shield")
def _(d):
    d.disc(10, 10, 9.0, "iron")
    for i in range(4):
        a = math.radians(i * 45)
        bone(d, 10 - 6.6 * math.cos(a), 10 - 6.6 * math.sin(a), 10 + 6.6 * math.cos(a), 10 + 6.6 * math.sin(a),
             "bone", 1.6, 1.2, g=2 + i)
    skull(d, 10, 10.4, 0.5, g=8)


@pic("soul_drain")
def _(d):
    skull(d, 13.0, 12.4, 0.82)
    for i, (x, y, r) in enumerate(((3.0, 3.4, 2.6), (6.6, 6.0, 2.0), (9.0, 8.4, 1.4))):
        drop(d, x, y + 1.0, r, "cyan", 1.5, shine=False, g=6 + i)
    d.put(2, 4, "k")
    d.put(4, 4, "k")


@pic("corpse_explosion")
def _(d):
    burst(d, 10, 10, 9.8, 5.6, 9, "fire", -90, (1.0, 0.8, 0.92))
    skull(d, 10, 10.6, 0.7, g=4)
    for (x, y) in ((2.0, 3.0), (17.0, 16.0), (17.0, 2.0)):
        d.rect(int(x), int(y), int(x) + 1, int(y), "bone", g=8)


@pic("bone_army")
def _(d):
    # FILEIRA de 3 caveiras com lancas erguidas atras (exercito)
    for i, x in enumerate((3.8, 10.0, 16.2)):
        d.line(x + 1.6, 9.0, x + 1.6, 2.4, 1.0, "wood", g=10 + i)
        d.tri((x + 1.6, -0.2), (x + 0.4, 2.8), (x + 2.8, 2.8), "steel", g=14 + i)
    for i, x in enumerate((3.8, 16.2, 10.0)):
        skull(d, x, 12.6 if x != 10.0 else 13.4, 0.52, "bone" if x == 10.0 else "bone.d" if False else "bone",
              g=i * 3)
    d.rect(0, 18, 19, 19, "bone.d", g=20)


@pic("lich_form")
def _(d):
    skull(d, 10, 12.0, 0.86)
    for (x, y) in ((7.6, 11.6), (12.4, 11.6)):
        d.disc(x, y, 1.0, "cyan.l", g=4)
    crown(d, 10, 3.6, "gold", "purple", g=6, s=0.56)


# ---------------------------------------------------------------------------------------------- BRUXO


@pic("summon_imp")
def _(d):
    # diabrete de CORPO INTEIRO (pequeno, asas de morcego, rabo de seta) - o demonio e so o rosto enorme
    bat_wing(d, 9.0, 10.6, "dark", g=20, flip=True, s=0.82)
    bat_wing(d, 11.0, 10.6, "dark", g=24, s=0.82)
    d.curve([(12.0, 16.0), (17.0, 18.6), (17.6, 13.6)], 1.2, "red.d", g=30)
    d.tri((17.6, 11.6), (16.0, 14.4), (19.4, 14.0), "red.d", g=30)
    d.ellipse(10, 13.6, 3.0, 3.6, "red", g=1)
    d.rect(8, 16, 8, 18, "red", g=1)
    d.rect(11, 16, 11, 18, "red", g=1)
    d.disc(10, 7.0, 3.6, "red", g=1)
    d.tri((7.2, 5.0), (6.6, 1.4), (8.8, 4.0), "bone", g=3)
    d.tri((12.8, 5.0), (13.4, 1.4), (11.2, 4.0), "bone", g=4)
    d.px([(8, 7), (11, 7)], "yellow.l", g=5)
    d.rect(9, 9, 10, 9, "k")


@pic("shadow_weapon")
def _(d):
    sword(d, 3.0, 17.0, 17.0, 3.0, blade="void", guard="red", grip="black", w=3.0)
    d.halo("purple", 1.0, g=40)
    for (x, y) in ((4.0, 6.0), (14.6, 15.0)):
        d.disc(x, y, 1.2, "purple.l", g=50)


@pic("death_contract")
def _(d):
    scroll(d, 10, 9.0, "paper", lines=True)
    d.disc(13.0, 14.6, 3.6, "blood", g=4)
    skull(d, 13.0, 14.8, 0.34, "blood.h", g=5, jaw=False)


@pic("demon_wings")
def _(d):
    bat_wing(d, 10.6, 11.0, "blood", flip=False, s=0.96)
    bat_wing(d, 9.4, 11.0, "blood", flip=True, s=0.96, g=3)
    d.ellipse(10, 11.6, 1.6, 4.2, "black", g=8)


@pic("summon_demon")
def _(d):
    d.poly([(5.0, 6.0), (15.0, 6.0), (16.4, 12.0), (13.0, 18.6), (7.0, 18.6), (3.6, 12.0)], "blood")
    d.curve([(5.6, 7.0), (1.0, 5.0), (2.0, 0.4)], 2.4, "bone", g=2)
    d.curve([(14.4, 7.0), (19.0, 5.0), (18.0, 0.4)], 2.4, "bone", g=3)
    d.poly([(5.6, 10.0), (9.0, 11.0), (6.0, 12.0)], "yellow", g=4)
    d.poly([(14.4, 10.0), (11.0, 11.0), (14.0, 12.0)], "yellow", g=5)
    d.rect(7, 15, 12, 16, "k")
    d.tri((7.4, 15.0), (8.6, 15.0), (8.0, 17.4), "W", g=7)
    d.tri((11.4, 15.0), (12.6, 15.0), (12.0, 17.4), "W", g=7)
    flame(d, 10, 6.6, 0.3, "fire", "flame", None, g=9)


# ======================================================================================================  v0.8
# "Reinos e Lendas": glifos novos de legends_c/d/e, ultimates, fusions_c/d, arcane_duel e power_up (law_ruin).
# Cada pictograma "le" a ideia do glifo; os parecidos com desenhos antigos foram afastados de proposito
# (meteor x fall, black_hole x attraction_point, yin_yang x duality, prism_cannon x prism, void_rift x rift...).

def lemniscate(d, cx, cy, rx=4.6, ry=2.0, w=1.4, m="W", g=0):
    """Simbolo do infinito (oito deitado) de meia-largura rx e meia-altura ry."""
    pts = []
    for i in range(65):
        t = i / 64 * 2 * math.pi
        den = 1 + math.sin(t) ** 2
        pts.append((cx + rx * math.cos(t) / den, cy + ry * 2.83 * math.sin(t) * math.cos(t) / den))
    d.path(pts, w, m, g=g)


def cut_line(d, x0, y0, x1, y1, w=3.0, edge="red", core="W", g=0, bow=0.0):
    """Corte afiado: lamina afinada nas pontas com miolo branco."""
    d.taper(x0, y0, x1, y1, w, edge, g=g, bow=bow)
    d.taper(x0, y0, x1, y1, max(0.9, w * 0.38), core, g=g + 1, bow=bow)


def move_cells(d, test, dx, dy):
    """Desloca (dx,dy) as celulas ja pintadas cujo centro passa no teste (abre uma fenda num desenho)."""
    old = [row[:] for row in d.m]
    picked = [(x, y) for y in range(d.h) for x in range(d.w) if old[y][x] is not None and test(x + 0.5, y + 0.5)]
    for (x, y) in picked:
        d.m[y][x] = None
    for (x, y) in picked:
        X, Y = x + dx, y + dy
        if 0 <= X < d.w and 0 <= Y < d.h:
            d.m[Y][X] = old[y][x]


def clock_face(d, cx, cy, r, face="white", rim="gold", ticks="k", hands=((0, 0.62),), hand="k", g=0, n=12):
    """Mostrador: aro, marcas (n) e ponteiros [(angulo_em_horas*30 a partir do 12, comprimento relativo)]."""
    d.disc(cx, cy, r, rim, g=g)
    d.disc(cx, cy, r - 1.4, face, g=g + 1)
    for i in range(n):
        a = math.radians(i * 360 / n - 90)
        rr = r - 2.2
        d.put(int(cx + rr * math.cos(a)), int(cy + rr * math.sin(a)), ticks)
    for (deg, ln) in hands:
        a = math.radians(deg - 90)
        d.line(cx, cy, cx + (r - 1.4) * ln * math.cos(a), cy + (r - 1.4) * ln * math.sin(a), 1.3, hand)


def meteor_rock(d, cx, cy, r, dx=-1.0, dy=-1.0, rock="stone", fire="fire", flame_="flame", g=0, trail=9.0):
    """Rocha em brasa com cauda de fogo apontando para (dx,dy) (de onde ela veio)."""
    L = math.hypot(dx, dy) or 1
    ux, uy = dx / L, dy / L
    px_, py_ = -uy, ux
    tx, ty = cx + ux * (r + trail), cy + uy * (r + trail)
    d.poly([(cx + px_ * r * 1.05, cy + py_ * r * 1.05), (tx, ty), (cx - px_ * r * 1.05, cy - py_ * r * 1.05)], fire, g=g)
    d.poly([(cx + px_ * r * 0.6, cy + py_ * r * 0.6), (cx + ux * (r + trail * 0.62), cy + uy * (r + trail * 0.62)),
            (cx - px_ * r * 0.6, cy - py_ * r * 0.6)], flame_, g=g + 1)
    d.disc(cx, cy, r, rock, g=g + 2)


# ---------------------------------------------------------------------------------------------- v0.8 LENDAS: Jujutsu
@pic("infinity")
def _(d):
    # Mugen: o simbolo do INFINITO grande no centro; duas flechas param no ar antes de tocar (barreira de pontos)
    lemniscate(d, 10, 12.4, 8.0, 3.2, 2.4, "sky.l", g=1)
    lemniscate(d, 10, 12.4, 8.0, 3.2, 0.9, "W", g=2)
    for i, x in enumerate((5.6, 14.4)):
        d.arrow(x, 0.2, x, 4.6, 1.5, 2.6, "red.l", g=4 + i)
        d.line(x - 2.2, 7.0, x + 2.2, 7.0, 0.9, "W", g=8)


@pic("lapse_blue")
def _(d):
    # Azul: ponto de atracao AZUL - rastros em espiral entrando e blocos arrancados sendo sugados
    for k in range(3):
        spiral(d, 10, 10, 3.0, 9.6, 0.55, 1.2, "sky.l", a0=k * 120, g=1 + k, cw=False)
    for i, (x, y, m) in enumerate(((2.6, 4.0, "stone"), (16.4, 3.2, "dirt"), (15.6, 16.0, "stone"))):
        d.rect(int(x) - 1, int(y) - 1, int(x) + 1, int(y) + 1, m, g=6 + i)
    orb(d, 10, 10, 4.0, "blue", g=10)
    d.disc(10, 10, 1.6, "W")


@pic("reversal_red")
def _(d):
    # Vermelho: orbe VERMELHO repelindo - ondas de choque em arco e estilhacos voando para fora
    for i, r in enumerate((7.0, 9.4)):
        for a0 in (-60, 60, 180):
            d.arc(10, 10, r, r - 1.1, a0 - 32 + i * 18, a0 + 32 + i * 18, "red.l" if i else "red", g=1 + i)
    for i, ang in enumerate((-100, 20, 140)):
        a = math.radians(ang)
        d.arrow(10 + math.cos(a) * 5.0, 10 + math.sin(a) * 5.0, 10 + math.cos(a) * 9.6, 10 + math.sin(a) * 9.6,
                1.3, 2.4, "W", g=5 + i)
    orb(d, 10, 10, 4.2, "red", g=10)
    d.disc(10, 10, 1.6, "W")


@pic("dismantle")
def _(d):
    # Desmantelar: um corte reto e LONGO, branco-avermelhado, que parte um bloco em dois
    cube(d, 10.0, 10.6, 0.86, "stone")
    move_cells(d, lambda x, y: y < 18.6 - x * 0.82, -1, -1)
    move_cells(d, lambda x, y: y >= 18.6 - x * 0.82, 1, 1)
    cut_line(d, 0.2, 19.2, 19.8, 1.2, 3.2, "red", "W", g=10)


@pic("cleave")
def _(d):
    # Clivar: a cabeca quadrada de um mob e PICOTADA por uma grade "#" de cortes - nove pedacos se afastam e as
    # fendas brilham em vermelho de ponta a ponta (Desmantelar parte um bloco com um corte so; Clivar retalha o alvo)
    for cx in range(3):
        for cy in range(3):
            x0, y0 = 2 + cx * 6, 2 + cy * 6
            d.rect(x0, y0, x0 + 3, y0 + 3, "moss", g=cx * 3 + cy)
    d.rect(4, 9, 5, 10, "k")
    d.rect(14, 9, 15, 10, "k")
    d.rect(9, 15, 10, 16, "k")
    for i, v in enumerate((7.0, 13.0)):
        d.taper(v, -0.5, v, 20.5, 2.2, "red", g=20 + i)
        d.taper(-0.5, v, 20.5, v, 2.2, "red", g=22 + i)
    for v in (7.0, 13.0):
        d.line(v, 3.0, v, 17.0, 0.6, "red.h", g=30)
        d.line(3.0, v, 17.0, v, 0.6, "red.h", g=30)


@pic("flame_arrow")
def _(d):
    # Fuga: ARCO feito de chamas (corda tensa) e uma flecha flamejante disparando para cima-direita
    d.arc(10.6, 9.4, 9.6, 7.8, 125, 325, "fire", g=1)
    d.arc(10.6, 9.4, 9.0, 8.2, 140, 310, "flame", g=2)
    d.line(5.0, 17.4, 16.4, 1.6, 0.9, "flame.h", g=3)
    d.taper(0.6, 19.4, 13.4, 6.6, 5.0, "fire", g=10)
    d.taper(1.4, 18.6, 13.0, 7.0, 2.4, "flame", g=11)
    d.line(4.0, 16.0, 15.4, 4.6, 1.2, "W", g=12)
    d.tri((19.6, 0.4), (14.0, 3.0), (17.0, 6.0), "W", g=13)
    d.tri((18.4, 1.6), (15.6, 3.2), (16.8, 4.4), "flame.l", g=14)


@pic("law_shrine")
def _(d):
    # Santuario Maligno: templo de telhado curvo com CHIFRES, o portal e uma BOCA de presas em zigue-zague
    # (garganta rubra), pilares rubros e cranios empilhados na escadaria
    d.rect(3, 8, 16, 17, "dark", g=1)
    d.rect(3, 8, 4, 17, "blood", g=2)
    d.rect(15, 8, 16, 17, "blood", g=2)
    d.ellipse(10, 12.6, 5.0, 3.8, "k")
    d.ellipse(10, 13.4, 2.6, 1.6, "blood.d", g=3)
    for x in (6.0, 8.7, 11.3, 14.0):
        d.tri((x - 1.4, 9.2), (x + 1.4, 9.2), (x, 12.4), "W", g=4)
    for x in (7.3, 10.0, 12.7):
        d.tri((x - 1.4, 16.4), (x + 1.4, 16.4), (x, 13.4), "W", g=5)
    d.poly([(1.0, 8.8), (3.0, 5.6), (10.0, 4.0), (17.0, 5.6), (19.0, 8.8), (10.0, 7.4)], "blood", g=6)
    d.curve([(6.4, 5.2), (4.2, 3.6), (3.4, 0.8)], 1.8, "bone", g=7)
    d.curve([(13.6, 5.2), (15.8, 3.6), (16.6, 0.8)], 1.8, "bone", g=8)
    d.disc(10, 5.6, 1.1, "yellow", g=9)
    d.put(10, 5, "k")
    d.rect(2, 18, 17, 18, "stone.d", g=10)
    for i, x in enumerate((4.0, 16.0)):
        d.disc(x, 17.0, 1.3, "bone", g=12 + i)
        d.put(int(x), 17, "k")


@pic("law_infinite_void")
def _(d):
    # Vazio Infinito: o interior vira COSMOS - galaxia espiral, estrelas e um conjurador flutuando pequeno
    d.disc(10, 10, 9.0, "night")
    for k in range(2):
        spiral(d, 10, 10, 0.8, 7.4, 0.7, 1.4, "violet.l" if k == 0 else "sky.l", a0=k * 180, g=2 + k)
    d.disc(10, 10, 1.8, "W")
    for (x, y) in ((3, 6), (15, 3), (16, 13), (4, 15), (12, 17), (7, 2)):
        d.put(x, y, "W")
    sparkle(d, 15.4, 6.6, 2.0, "yellow.l", g=8)
    sparkle(d, 5.0, 11.6, 1.6, "W", g=9)


# ---------------------------------------------------------------------------------------------- v0.8 LENDAS: Atomico/Tempo
@pic("atomic")
def _(d):
    # Eu Sou ATOMICO: o simbolo do ATOMO (tres orbitas brancas de 1 px, eletrons ciano, nucleo magenta em brasa)
    # sobre a esfera escura da explosao, rodeada por um anel de choque magenta de pontas finas
    burst(d, 10, 10, 9.8, 7.0, 14, "magenta", -90, (1.0, 0.86))
    d.disc(10, 10, 7.0, "night", g=1)
    for i, rot in enumerate((90, 30, 150)):
        r = math.radians(rot)
        pts = []
        for k in range(49):
            t = k / 48 * 2 * math.pi
            u, v = 9.2 * math.cos(t), 3.0 * math.sin(t)
            pts.append((10 + u * math.cos(r) - v * math.sin(r), 10 + u * math.sin(r) + v * math.cos(r)))
        d.path(pts, 0.9, "W", g=4 + i)
    for i, (x, y) in enumerate(((10.0, 1.2), (17.6, 14.4), (2.4, 14.4))):
        d.disc(x, y, 1.3, "cyan.l", g=8)
    d.disc(10, 10, 2.2, "magenta.l", g=9)
    d.put(9, 9, "W")


@pic("disintegration")
def _(d):
    # Desintegracao: uma coluna de luz pura desce sobre o alvo (um cranio escuro de olhos acesos) e a metade direita
    # dele ja se DESFAZ em quadradinhos que voam para longe; circulo magico sagrado dourado embaixo
    d.fill(lambda x, y: abs(x - 9.0) <= 5.2 and y <= 17.6, "yellow.l", g=1)
    d.fill(lambda x, y: abs(x - 9.0) <= 1.8 and y <= 17.6, "W", g=2)
    skull(d, 9.0, 10.6, 0.92, "dark", g=3, jaw=True)
    d.recolor(lambda x, y: True, "yellow", g=4, only="k")
    d.erase(lambda x, y: x > 10.6 and 2.0 < y < 17.0 and (int(x) * 3 + int(y) * 5) % 3 != 0 and
            d.m[int(y)][int(x)] is not None and d.m[int(y)][int(x)][0] == "dark")
    for (x, y) in ((15, 4), (16, 8), (17, 5), (16, 12), (18, 10), (17, 15), (19, 7), (15, 14)):
        d.put(x, y, "dark", g=5)
    chain_link(d, 9.0, 18.0, 9.0, 1.9, 1.1, 0, "gold", g=6)


@pic("time_stop")
def _(d):
    # Parar o Tempo: relogio PARADO no 12 com metade em cores INVERTIDAS (a onda negativa passando)
    for i, r in enumerate((9.6, 8.3)):
        d.ring(10, 10, r, r - 0.8, "p.l" if i == 0 else "p.d", g=1 + i)
    clock_face(d, 10, 10, 7.0, face="white", rim="gold", ticks="k", hands=((0, 0.75), (0, 0.45)), g=4)
    inv = {"white": "black", "k": "W", "gold": "violet", "black": "white"}
    for y in range(d.h):
        for x in range(d.w):
            c = d.m[y][x]
            if c and x >= 10:
                if c[0] in inv:
                    d.m[y][x] = (inv[c[0]], c[1], c[2] + 20)
                elif c[0] == "W":
                    d.m[y][x] = ("k", None, c[2])
                elif c[0] == "p":
                    d.m[y][x] = ("night", c[1], c[2] + 20)
    d.disc(10, 10, 1.0, "red", g=30)


@pic("goatify")
def _(d):
    # Transmutar em Cabra: cabeca de CABRA de frente (chifres para tras, olhos de pupila deitada, barbicha)
    d.curve([(7.4, 5.4), (4.6, 0.8), (1.0, 3.4)], 2.0, "bone.d", g=1)
    d.curve([(12.6, 5.4), (15.4, 0.8), (19.0, 3.4)], 2.0, "bone.d", g=2)
    d.ellipse(3.6, 8.4, 3.2, 1.4, "white", g=3, rot=20)
    d.ellipse(16.4, 8.4, 3.2, 1.4, "white", g=4, rot=-20)
    d.fill(lambda x, y: ((x - 10) / 4.6) ** 2 + ((y - 9.0) / 5.4) ** 2 <= 1 or
           (abs(x - 10) <= 3.0 - (y - 9.0) * 0.18 and 9.0 <= y <= 15.6), "white", g=5)
    d.rect(7, 8, 8, 8, "yellow")
    d.rect(12, 8, 13, 8, "yellow")
    d.put(7, 8, "k")
    d.put(13, 8, "k")
    d.ellipse(10, 14.4, 2.4, 1.6, "pink", g=6)
    d.tri((8.6, 15.8), (11.4, 15.8), (10.0, 19.6), "white.d", g=7)
    sparkle(d, 17.4, 15.0, 2.2, "p.h", g=9)


# ---------------------------------------------------------------------------------------------- v0.8 LENDAS: Supremas
@pic("super_tier")
def _(d):
    # Magia de Nivel Supremo: tres CIRCULOS MAGICOS enormes em camadas (perspectiva) e o conjurador parado
    for i, (y, rx, ry) in enumerate(((15.2, 9.8, 4.0), (8.4, 9.0, 3.2), (2.6, 7.0, 2.2))):
        chain_link(d, 10, y, rx, ry, 1.3, 0, "sky.l", g=1 + i * 3)
        chain_link(d, 10, y, rx - 2.4, ry - 1.0, 0.8, 0, "cyan", g=2 + i * 3)
        for k in range(6):
            a = math.radians(k * 60 + 30 * i)
            d.put(int(10 + (rx - 0.7) * math.cos(a)), int(y + (ry - 0.4) * math.sin(a)), "W")
    vfig(d, 10, 16.4, 7.0, "W", g=12, arms="up")


@pic("fallen_down")
def _(d):
    # Queda Final: pilar azul-branco DESCOMUNAL descendo de um circulo no ceu e vitrificando o chao
    d.ellipse(10, 17.6, 9.6, 2.2, "orange", g=0)
    d.ellipse(10, 17.6, 6.4, 1.3, "yellow.l", g=1)
    d.fill(lambda x, y: abs(x - 10) <= 3.4 + y * 0.16 and 2.0 <= y <= 17.6, "sky.l", g=2)
    d.fill(lambda x, y: abs(x - 10) <= 1.2 + y * 0.08 and 2.0 <= y <= 17.6, "W", g=3)
    chain_link(d, 10, 2.4, 8.6, 2.2, 1.2, 0, "cyan", g=4)
    for (x0, y0) in ((2.0, 11.0), (17.0, 9.6)):
        d.curve([(x0, y0 + 4), (x0 + 1.2, y0 + 2), (x0 - 0.6, y0)], 0.9, "orange.l", g=6)


@pic("black_goat")
def _(d):
    # Ia! Cabra Negra: a silhueta NEGRA de uma cabeca de cabra (focinho longo, orelhas caidas, chifres para tras,
    # pupilas amarelas deitadas e um terceiro olho) diante de uma LUA DE SANGUE; tentaculos sobem do chao.
    # Preto sobre o vermelho le bem mesmo no fundo escuro da Sombra.
    d.disc(10, 8.4, 8.6, "blood", g=0)
    d.disc(9.0, 7.4, 6.8, "red", g=1)
    for i, (x, fl) in enumerate(((3.0, True), (17.0, False))):
        tentacle2(d, x, 19.8, 6.4, "black", g=3 + i, flip=fl, r0=1.7)
    d.curve([(8.4, 5.0), (6.8, 0.8), (3.6, 2.0)], 1.9, "black", g=6)
    d.curve([(11.6, 5.0), (13.2, 0.8), (16.4, 2.0)], 1.9, "black", g=6)
    d.ellipse(5.0, 8.8, 2.6, 1.0, "black", g=6, rot=22)
    d.ellipse(15.0, 8.8, 2.6, 1.0, "black", g=6, rot=-22)
    d.fill(lambda x, y: ((x - 10) / 3.6) ** 2 + ((y - 7.4) / 3.4) ** 2 <= 1 or
           (abs(x - 10) <= 2.6 - (y - 7.4) * 0.16 and 7.4 <= y <= 15.4), "black", g=6)
    d.tri((8.8, 15.0), (11.2, 15.0), (10.0, 18.6), "black", g=6)
    d.rect(8, 8, 8, 8, "yellow.l")
    d.rect(11, 8, 12, 8, "yellow.l")
    d.put(7, 8, "yellow.l")
    d.put(10, 5, "red.l")


@pic("grasp_heart")
def _(d):
    # Agarrar o Coracao: uma GARRA espectral desce do alto e fecha os quatro dedos POR TRAS de um coracao
    # (como um engaste prendendo uma gema): as unhas brancas cravam embaixo e o coracao racha e respinga
    d.ellipse(10, 2.6, 5.6, 2.6, "violet", g=0)
    fingers = (((4.4, 3.0), (-0.2, 9.0), (4.2, 15.4)), ((7.8, 4.0), (5.6, 11.0), (7.6, 17.6)),
               ((12.2, 4.0), (14.4, 11.0), (12.4, 17.6)), ((15.6, 3.0), (20.2, 9.0), (15.8, 15.4)))
    for i, pts in enumerate(fingers):
        d.curve(list(pts), 3.0, "dark", g=1)
        d.curve(list(pts), 1.5, "violet.l", g=2 + i)
        x, y = pts[2]
        d.tri((x - 1.0, y - 0.6), (x + 1.0, y - 0.6), (x + (1.2 if x < 10 else -1.2), y + 1.8), "W", g=6)
    d.heart(10, 10.0, 1.0, "heart", g=8)
    d.path([(8.2, 6.4), (9.8, 8.6), (8.6, 10.8)], 0.9, "blood.d", g=9)
    for (x, y) in ((3.0, 18.8), (17.0, 18.8)):
        drop(d, x, y, 0.9, "blood", 1.6, shine=False, g=10)


@pic("death_clock")
def _(d):
    # O Fim de Toda Vida: relogio gigante de 12 marcas no ceu com um CRANIO no centro - o ponteiro chega ao 12
    clock_face(d, 10, 10, 9.4, face="dark", rim="bone", ticks="bone.h", hands=((-6, 0.82),), hand="red.l", g=1)
    skull(d, 10, 11.0, 0.5, "bone", g=6, jaw=True)
    d.put(10, 2, "red.l")


# ---------------------------------------------------------------------------------------------- v0.8 ULTIMATES
@pic("meteor")
def _(d):
    # Meteoro: rocha em brasa GRANDE (rachaduras de lava) caindo na marca vermelha do chao
    d.ellipse(13.0, 18.0, 6.0, 1.6, "red", g=0)
    d.ellipse(13.0, 18.0, 3.4, 0.7, None)
    meteor_rock(d, 11.6, 10.4, 5.4, -1.0, -1.15, "stone", "fire", "flame", g=1, trail=8.0)
    d.path([(8.6, 9.0), (10.6, 10.8), (9.8, 13.0)], 1.0, "lava.l", g=6)
    d.path([(12.6, 7.4), (13.6, 9.4), (15.6, 10.0)], 1.0, "lava.l", g=6)
    d.path([(11.6, 12.4), (13.8, 14.0)], 1.0, "lava.l", g=6)


@pic("meteor_shower")
def _(d):
    # Chuva de Meteoros: varios meteoros paralelos cortando um ceu vermelho ate o chao
    for i, (x, y, r) in enumerate(((5.0, 7.0, 2.4), (15.0, 4.4, 2.0), (12.6, 13.0, 2.6), (3.2, 15.6, 1.7))):
        meteor_rock(d, x, y, r, 1.0, -1.25, "stone", "fire", "flame", g=1 + i * 4, trail=4.6 + r)
    d.rect(0, 18, 19, 19, "dirt", g=30)
    for x in (6, 15):
        flame(d, x, 18.6, 0.24, g=40 + x, core=None)


@pic("transcend")
def _(d):
    # Transcendencia: o conjurador FLUTUA com aureola e asas de luz
    wing(d, 11.6, 9.0, "p.l", s=0.9)
    wing(d, 8.4, 9.0, "p.l", flip=True, s=0.9, g=2)
    d.ellipse(10, 2.0, 3.6, 1.2, "gold", g=4)
    d.ellipse(10, 2.0, 2.0, 0.4, None)
    vfig(d, 10, 16.0, 12.0, "W", g=5, arms="out")
    d.ellipse(10, 18.8, 4.0, 0.8, "p.d", g=7)
    for (x, y) in ((2, 15), (17, 15), (3, 3)):
        sparkle(d, x, y, 1.6, "yellow.l", g=9)


@pic("colossus")
def _(d):
    # Colosso: gigante ESPECTRAL de elmo e espada erguido atras de um conjurador pequeno
    d.curve([(9.0, 1.6), (12.0, -0.4), (15.4, 2.6)], 1.8, "red", g=5)
    d.fill(lambda x, y: ((x - 9.0) / 4.4) ** 2 + ((y - 5.4) / 4.6) ** 2 <= 1 and y <= 8.6, "p.l", g=1)
    d.rect(5, 8, 13, 9, "p.l", g=1)
    d.rect(6, 5, 12, 5, "k")
    d.rect(9, 6, 9, 8, "k")
    d.fill(lambda x, y: 1.0 <= x <= 17.0 and 10.0 <= y <= 19.0 and abs(x - 9.0) <= 8.0 - (y - 10.0) * 0.3, "p", g=2)
    d.rect(1, 10, 4, 12, "p.l", g=3)
    d.rect(14, 10, 17, 12, "p.l", g=4)
    sword(d, 17.6, 18.6, 17.6, 0.6, blade="sky.l", guard="gold", grip="p.d", w=2.2, g=6)
    vfig(d, 8.6, 19.4, 6.4, "W", g=12)


# ---------------------------------------------------------------------------------------------- v0.8 FUSOES (fusions_c)
@pic("lava_geyser")
def _(d):
    # Geiser de Lava: fenda no chao e uma COLUNA de lava jorrando, com coroa de respingos no alto
    d.rect(0, 16, 19, 19, "stone", g=0)
    d.path([(1.0, 17.0), (4.0, 18.6), (7.0, 17.2)], 1.0, "lava", g=1)
    d.path([(13.0, 17.2), (16.0, 18.6), (19.0, 17.0)], 1.0, "lava", g=1)
    d.fill(lambda x, y: abs(x - 10) <= 1.8 + (16 - y) * 0.16 + 0.6 * math.sin(y * 1.3) and 5.0 <= y <= 17.0,
           "lava", g=1)
    d.fill(lambda x, y: abs(x - 10 - 0.5 * math.sin(y)) <= 0.8 and 5.0 <= y <= 17.0, "ember.l", g=2)
    for (x, y, r) in ((7.0, 5.0, 2.4), (10.4, 3.4, 2.8), (13.4, 5.4, 2.2)):
        d.disc(x, y, r, "lava", g=1)
    d.disc(10.0, 3.0, 1.3, "ember.l", g=2)
    for i, (x, y, r) in enumerate(((3.0, 2.0, 1.4), (17.2, 1.6, 1.2), (2.4, 8.0, 1.0), (17.6, 8.6, 1.1))):
        d.disc(x, y, r, "ember", g=4 + i)
    d.line(5.0, 3.0, 3.6, 2.2, 0.8, "ember", g=4)
    d.line(15.4, 3.0, 16.6, 1.8, 0.8, "ember", g=5)


@pic("volcano")
def _(d):
    # Vulcao: cone de basalto com cratera em brasa cuspindo BOMBAS de lava em arco sobre marcas vermelhas
    d.poly([(0.6, 19.6), (7.4, 9.0), (12.6, 9.0), (19.4, 19.6)], "black", g=0)
    d.ellipse(10, 9.4, 2.8, 1.1, "lava.l", g=1)
    d.path([(8.6, 10.0), (7.4, 13.6), (8.2, 16.6), (7.0, 19.6)], 1.2, "lava", g=2)
    d.path([(11.8, 10.0), (13.2, 13.0), (12.4, 15.4)], 1.2, "lava", g=2)
    for i, (x, y, dx) in enumerate(((4.0, 4.6, 1.0), (16.0, 3.4, -1.0))):
        meteor_rock(d, x, y, 2.0, dx, 1.3, "lava", "fire", "flame", g=4 + i * 4, trail=3.6)
        d.put(int(x - 0.6), int(y - 0.6), "ember.l")
    for (x, y) in ((10, 6), (9, 3), (11, 1)):
        d.put(x, y, "ember", g=12)


@pic("steam_explosion")
def _(d):
    # Explosao de Vapor: gota d'agua no centro de um estouro BRANCO de vapor escaldante
    burst(d, 10, 10, 9.8, 5.6, 10, "white", -90, (1.0, 0.82, 0.95))
    for (x, y, r) in ((3.4, 4.0, 2.6), (16.6, 4.4, 2.4), (3.0, 16.0, 2.4), (16.8, 15.6, 2.7)):
        d.disc(x, y, r, "cloud", g=2)
    burst(d, 10, 10, 6.4, 3.8, 8, "orange.l", -70, (1.0, 0.8), g=3)
    drop(d, 10, 9.6, 3.0, "water", 1.7, g=5)


@pic("steam_cannon")
def _(d):
    # Canhao de Vapor: cano de COBRE com cintas de ferro (boca larga a direita) soltando um JATO de vapor em cone
    # que se abre ate a borda; ondas laranja de calor escaldante nas bordas do jato
    d.poly([(0.4, 8.0), (7.0, 7.0), (7.0, 14.0), (0.4, 13.0)], "copper", g=0)
    d.rect(1, 7, 2, 14, "iron", g=1)
    d.rect(4, 7, 5, 14, "iron", g=1)
    d.rect(7, 5, 9, 16, "iron", g=2)
    d.poly([(9.4, 9.0), (19.8, 3.0), (19.8, 18.0), (9.4, 12.0)], "cloud", g=3)
    for i, (x, y, r) in enumerate(((12.0, 7.8, 1.6), (15.6, 5.4, 2.2), (12.0, 13.4, 1.6), (15.6, 15.6, 2.2),
                                   (18.6, 3.2, 1.8), (18.6, 17.6, 1.8))):
        d.disc(x, y, r, "cloud", g=3)
    for (y0, y1) in ((10.5, 10.5), (10.0, 7.4), (11.0, 13.6)):
        d.line(10.4, y0, 19.4, y1 + (y1 - 10.5) * 0.6, 1.0, "W", g=5)
    for (x0, y0) in ((11.0, 1.6), (11.0, 19.0)):
        d.path([(x0 + t * 7.0, y0 + 0.9 * math.sin(t * 9.0)) for t in (0, 0.2, 0.4, 0.6, 0.8, 1.0)], 0.9,
               "orange.l", g=7)


@pic("railgun")
def _(d):
    # Canhao Eletrico: dois TRILHOS de metal e um feixe ciano reto que atravessa os alvos em linha
    d.rect(0, 5, 8, 6, "steel", g=0)
    d.rect(0, 14, 8, 15, "steel", g=1)
    for x in (2, 5):
        d.path([(x, 7), (x + 1, 9), (x, 11), (x + 1, 13)], 0.8, "cyan.l", g=2)
    for i, x in enumerate((11, 16)):
        d.rect(x - 1, 6, x + 1, 14, "stone", g=3 + i)
    d.line(1.0, 10.0, 19.6, 10.0, 3.2, "cyan", g=6)
    d.line(1.0, 10.0, 19.6, 10.0, 1.0, "W", g=7)
    sparkle(d, 11.0, 4.2, 1.8, "yellow", g=8)
    sparkle(d, 16.0, 16.0, 1.8, "yellow", g=9)


@pic("ball_lightning")
def _(d):
    # Bola de Raio: orbe de plasma lento disparando raios em zigue-zague nos alvos em volta
    for i, (x1, y1) in enumerate(((1.0, 2.0), (18.6, 4.0), (2.0, 18.4))):
        pts = [(9.6 + (x1 - 9.6) * t + (1.4 if k % 2 else -1.4) * (1 - t), 10.4 + (y1 - 10.4) * t)
               for k, t in enumerate((0.0, 0.3, 0.55, 0.8, 1.0))]
        d.path(pts, 1.1, "yellow.l", g=1 + i)
    orb(d, 10.6, 10.6, 5.0, "cyan", g=5)
    d.ring(10.6, 10.6, 3.4, 2.5, "W", g=6)
    d.disc(10.6, 10.6, 1.2, "W")
    d.ellipse(14.0, 18.6, 4.0, 0.8, "cyan.d", g=8)


@pic("absolute_zero")
def _(d):
    # Zero Absoluto: termometro no FUNDO da escala diante de uma onda de cristais de gelo que tomam o chao
    d.ellipse(10, 17.6, 9.8, 2.2, "ice", g=0)
    for i, (x, h) in enumerate(((2.6, 5.0), (5.4, 7.6), (15.0, 7.6), (17.6, 5.0))):
        d.tri((x - 1.6, 17.6), (x + 1.6, 17.6), (x + (0.6 if x > 10 else -0.6), 17.6 - h), "glass", g=2 + i)
    d.rect(8, 1, 12, 13, "glass", g=10)
    d.rect(9, 2, 11, 12, "white", g=11)
    d.disc(10, 15.0, 3.2, "glass", g=10)
    d.disc(10, 15.0, 2.2, "cyan", g=12)
    d.rect(10, 11, 10, 13, "cyan", g=12)
    for y in (3, 5, 7, 9):
        d.put(12, y, "sky.d")


@pic("ice_coffin")
def _(d):
    # Caixao de Gelo: o alvo preso num bloco de gelo em forma de CAIXAO que ja trinca para estilhacar
    d.poly([(6.0, 0.6), (14.0, 0.6), (16.6, 5.6), (14.6, 19.4), (5.4, 19.4), (3.4, 5.6)], "ice", g=0)
    d.poly([(7.0, 2.2), (13.0, 2.2), (14.8, 6.0), (13.2, 17.8), (6.8, 17.8), (5.2, 6.0)], "glass", g=1)
    vfig(d, 10, 17.0, 13.6, "sky.d", g=3)
    d.path([(14.6, 4.0), (12.0, 8.0), (13.6, 10.4), (11.0, 14.6)], 0.9, "W", g=5)
    d.path([(4.6, 12.0), (7.0, 13.6)], 0.9, "W", g=5)
    for i, (x, y) in enumerate(((1.0, 14.0), (18.0, 10.0), (17.4, 16.6))):
        d.tri((x, y), (x + 1.6, y + 0.8), (x + 0.4, y + 2.0), "glass", g=7 + i)


@pic("world_roots")
def _(d):
    # Raizes do Mundo: raizes GIGANTES rompem o chao em arco e esmagam o alvo no meio
    d.rect(0, 16, 19, 19, "dirt", g=0)
    d.rect(0, 16, 19, 16, "grass", g=1)
    vfig(d, 10.6, 16.0, 7.0, "stone", g=2)
    d.curve([(1.0, 18.6), (2.0, 3.0), (12.0, 4.0), (13.0, 12.6)], 3.0, "wood", g=4)
    d.curve([(19.0, 18.6), (18.6, 8.0), (11.0, 7.0), (8.0, 13.0)], 2.6, "wood", g=5)
    d.curve([(6.0, 18.6), (5.0, 12.0), (8.4, 10.6)], 2.0, "wood", g=6)
    leaf(d, 4.0, 3.6, 0.36, -30, "leaf", g=8)
    leaf(d, 17.0, 6.4, 0.32, 30, "leaf", g=9)
    leaf(d, 9.0, 3.0, 0.3, 0, "leaf", g=10)


@pic("pollen_storm")
def _(d):
    # Tempestade de Polen: flor soltando um REDEMOINHO de polen amarelo-esverdeado que faz dormir
    spiral(d, 11.0, 8.6, 1.4, 8.4, 1.3, 1.6, "pollen", a0=90, g=1)
    for i, (x, y) in enumerate(((4.0, 3.0), (16.6, 2.0), (18.0, 13.0), (2.0, 9.6), (13.0, 17.6))):
        d.disc(x, y, 1.0, "poison", g=3 + i)
    flower(d, 5.6, 15.0, 2.4, 1.8, 5, "pink", "yellow", g=10)
    d.path([(15.0, 2.6), (17.0, 2.6), (15.0, 4.6), (17.0, 4.6)], 0.8, "W", g=12)
    d.path([(11.0, 0.8), (12.4, 0.8), (11.0, 2.2), (12.4, 2.2)], 0.7, "W", g=13)


@pic("sand_coffin")
def _(d):
    # Caixao de Areia: um CASULO de areia em espiral aperta o alvo - so a cabeca escapa, e a areia comprime
    d.rect(0, 17, 19, 19, "sand", g=0)
    d.disc(10, 4.0, 2.8, "flesh", g=1)
    d.put(9, 4, "k")
    d.put(11, 4, "k")
    d.fill(lambda x, y: ((x - 10) / 6.4) ** 2 + ((y - 12.0) / 6.4) ** 2 <= 1 and y >= 6.4, "sand", g=3)
    for k in range(4):
        y0 = 8.0 + k * 2.6
        d.curve([(4.0, y0 + 1.6), (10.0, y0 - 1.2), (16.0, y0 + 1.6)], 0.9, "sand.d", g=3)
    for i, (x, y, dx) in enumerate(((1.6, 10.0, 1), (18.4, 10.0, -1), (1.6, 15.0, 1), (18.4, 15.0, -1))):
        chevron(d, x, y, 2.0, 1.0, dx, 0, "sand.h", g=8 + i)
    for (x, y) in ((3, 4), (16, 3), (17, 6)):
        d.put(x, y, "sand.l", g=12)


@pic("sand_tsunami")
def _(d):
    # Tsunami de Areia: onda alta de AREIA quebrando por cima de tudo, graos voando da crista
    wave_curl(d, "sand", "sand.h", s=1.0)
    d.rect(0, 18, 19, 19, "dirt", g=5)
    for (x, y) in ((17, 2), (18, 5), (15, 1), (19, 8)):
        d.put(x, y, "sand.l", g=6)
    vfig(d, 16.2, 18.0, 5.6, "stone", g=8)


@pic("supernova")
def _(d):
    # Supernova: estrela que EXPLODE - nucleo branco cegante, anel de choque e raios longos de lente
    burst(d, 10, 10, 9.6, 6.4, 16, "orange", -90, (1.0, 0.82, 0.92, 0.76))
    d.ring(10, 10, 6.6, 5.4, "yellow.l", g=1)
    d.disc(10, 10, 4.4, "flame", g=2)
    d.disc(10, 10, 2.6, "W")
    for (x0, y0, x1, y1) in ((10, 0.0, 10, 20.0), (0.0, 10, 20.0, 10)):
        d.line(x0, y0, x1, y1, 0.9, "W", g=4)


@pic("solar_flare")
def _(d):
    # Erupcao Solar: o sol solta um ARCO de plasma que se estica num feixe ate a mira
    for i in range(5):
        a = math.radians(-100 + i * 25)
        d.line(3.0 + math.cos(a) * 8.0, 17.0 + math.sin(a) * 8.0, 3.0 + math.cos(a) * 10.0,
               17.0 + math.sin(a) * 10.0, 1.4, "yellow", g=0)
    d.disc(3.0, 17.0, 7.0, "orange", g=1)
    d.disc(3.0, 17.0, 4.6, "flame", g=2)
    d.disc(3.0, 17.0, 2.2, "W", g=2)
    d.arc(5.4, 9.6, 4.4, 3.0, 180, 330, "orange", g=2)
    d.taper(7.4, 8.4, 16.0, 4.0, 3.6, "orange", g=3)
    d.line(8.0, 8.0, 15.4, 4.2, 1.0, "W", g=4)
    d.ring(16.4, 3.6, 3.2, 2.2, "red", g=5)
    d.put(16, 3, "W")


@pic("sacred_spring")
def _(d):
    # Fonte Sagrada: bacia de pedra branca com agua benta jorrando em arco, brilhos de cura
    d.poly([(2.0, 13.0), (18.0, 13.0), (15.6, 18.6), (4.4, 18.6)], "white", g=0)
    d.ellipse(10, 13.0, 8.0, 1.6, "aqua", g=1)
    d.rect(9, 4, 10, 12, "aqua", g=2)
    d.arc(6.0, 7.0, 4.0, 3.0, 180, 300, "aqua", g=3)
    d.arc(14.0, 7.0, 4.0, 3.0, 240, 360, "aqua", g=4)
    d.disc(9.6, 3.6, 1.6, "W", g=5)
    plus(d, 3.0, 4.0, 2.2, 1.2, "yellow.l", g=6)
    plus(d, 16.6, 3.0, 2.0, 1.2, "yellow.l", g=7)
    d.heart(10, 16.0, 0.26, "heart", g=8)


@pic("purge")
def _(d):
    # Purgar: a luz pura no centro EXPULSA os borroes de status ruins, que viram setas contra os inimigos
    sparkle(d, 10, 10, 6.0, "W", g=0)
    d.disc(10, 10, 2.0, "aqua.l", g=1)
    for i, ang in enumerate((-135, -45, 45, 135)):
        a = math.radians(ang)
        d.disc(10 + math.cos(a) * 5.4, 10 + math.sin(a) * 5.4, 1.7, "poison" if i % 2 else "dark", g=3 + i)
        d.arrow(10 + math.cos(a) * 7.0, 10 + math.sin(a) * 7.0, 10 + math.cos(a) * 12.6, 10 + math.sin(a) * 12.6,
                1.4, 2.4, "aqua.l", g=8 + i)


# ---------------------------------------------------------------------------------------------- v0.8 FUSOES (fusions_d)
@pic("crystal_lotus")
def _(d):
    # Lotus de Cristal: flor de petalas de cristal pontudas; estilhacos saem em espiral
    for i, ang in enumerate((-90, -150, -30, 165, 15)):
        a = math.radians(ang)
        crystal(d, 10 + math.cos(a) * 4.2, 12 + math.sin(a) * 4.2, 4.0, 1.8, "crystal", rot=ang + 90, g=1 + i)
    d.ellipse(10, 15.4, 7.0, 2.0, "green", g=8)
    d.disc(10, 12.0, 2.0, "W", g=9)
    for i, (x, y, r) in enumerate(((2.0, 4.0, 0), (17.6, 3.0, 40), (18.6, 9.0, 80))):
        crystal(d, x, y, 1.8, 0.9, "magenta", rot=r, g=12 + i)


@pic("prism_cannon")
def _(d):
    # Canhao Prismatico: cristal-canhao na base atira um feixe que se ABRE em sete cores
    rays = ["red", "orange", "yellow", "green", "cyan", "blue", "violet"]
    for i, m in enumerate(rays):
        a = math.radians(-78 + i * 10.5)
        d.line(7.6, 12.4, 7.6 + math.cos(a) * 15.0, 12.4 + math.sin(a) * 15.0, 1.3, m + ".l", g=1 + i)
    crystal(d, 4.6, 15.4, 5.2, 2.6, "crystal", rot=45, g=10)
    d.disc(7.6, 12.4, 1.7, "W", g=11)


@pic("black_hole")
def _(d):
    # Buraco Negro: disco de acrecao BRILHANTE e inclinado em volta de um centro negro, sugando estrelas e blocos
    # (o centro negro e GRANDE com anel de foton branco e o arco de lente por cima; estrelas esticadas caindo)
    d.arc(10, 10.6, 8.2, 6.8, 190, 350, "flame", g=1)
    d.ellipse(10, 11.6, 9.8, 2.6, "orange", g=2, rot=-6)
    d.ellipse(10, 11.6, 7.4, 1.4, "flame.l", g=3, rot=-6)
    d.disc(10, 10.6, 5.2, "W", g=4)
    d.disc(10, 10.6, 4.4, "k")
    d.fill(lambda x, y: abs((y - 11.6) - (x - 10) * -0.105) <= 1.0 and y > 10.6 and (x - 10) ** 2 + (y - 10.6) ** 2
           <= 5.2 ** 2, "flame.l", g=3)
    for i, (x0, y0, x1, y1) in enumerate(((1.0, 1.0, 4.0, 4.0), (19.0, 2.0, 16.0, 4.4), (2.0, 19.0, 4.4, 16.6))):
        d.line(x0, y0, x1, y1, 1.0, "sky.l", g=8 + i)
        d.put(int(x1), int(y1), "W")


@pic("starfall")
def _(d):
    # Chuva de Estrelas: varias estrelas caindo RETAS do ceu ate o chao, com brilhos no impacto
    for i, (x, y, r) in enumerate(((5.0, 9.6, 3.2), (15.0, 5.0, 2.6), (13.6, 14.4, 2.6))):
        for k, o in enumerate((-1.2, 1.2)):
            d.line(x + 2.0 + o * 0.6, y - 2.6 - o * 0.6, x + 2.0 + 5.0 + o * 0.6, y - 2.6 - 6.4 - o * 0.6, 0.9,
                   "sky.l", g=1 + i * 3)
        d.star(x, y, r + 0.8, r * 0.5, 5, -90, "yellow", g=2 + i * 3)
        d.put(int(x), int(y), "W")
    d.rect(0, 18, 19, 19, "night", g=20)
    sparkle(d, 3.0, 17.0, 2.0, "W", g=22)


@pic("black_flame_pillar")
def _(d):
    # Pilar de Chamas Negras: uma labareda ALTA e estreita de fogo NEGRO (borda rubra, linguas dos lados) que
    # sobe de um circulo infernal ate o alto; chamas menores na base e brasas voando
    d.ellipse(10, 17.6, 9.6, 2.2, "red", g=0)
    d.ellipse(10, 17.6, 6.6, 1.2, "blood.d", g=1)
    for i, (x, fl) in enumerate(((3.8, True), (16.2, False))):
        flame(d, x, 18.6, 0.4, "red", "black", None, flip=fl, g=2 + i * 3)
    o = [(1, -18), (3.2, -14.5), (5.6, -11), (6.6, -7), (5.8, -3), (2.8, -0.4), (-2.8, -0.4), (-5.8, -3),
         (-6.6, -7), (-5.8, -10.8), (-4.2, -8.6), (-3.6, -12.2), (-1.4, -14.8)]
    d.poly([(10 + x * 0.74, 18.8 + y * 1.02) for x, y in o], "red", g=10)
    d.poly([(10 + x * 0.48, 18.0 + y * 0.86) for x, y in o], "black", g=11)
    d.curve([(9.4, 16.6), (11.0, 13.0), (9.4, 10.0), (10.4, 6.6)], 1.0, "red", g=12)
    for (x, y) in ((3, 3), (17, 2), (2, 9), (18, 8), (15, 4)):
        d.put(x, y, "red.l", g=22)


@pic("soul_burn")
def _(d):
    # Queimar Alma: a ALMA (fantasma) ardendo em chamas negras e rubras que nao se apagam
    flame(d, 10, 20.0, 1.06, "red", "black", None, g=0)
    ghost(d, 10, 10.6, 0.7, "sky.l", g=6, eyes=None)
    d.ellipse(8.4, 9.0, 0.9, 1.3, "k")
    d.ellipse(11.6, 9.0, 0.9, 1.3, "k")
    d.ellipse(10, 12.0, 0.8, 0.8, "k")
    d.recolor(lambda x, y: y > 13.2, "red", g=8, only="sky")
    for (x, y) in ((4, 4), (16, 3), (17, 9)):
        d.put(x, y, "red.l", g=12)


@pic("kraken_grasp")
def _(d):
    # Garras do Kraken: tentaculos GIGANTES saindo do mar, um deles enrolando o alvo
    for i, (x, fl, h) in enumerate(((3.6, False, 15.0), (16.4, True, 13.0))):
        tentacle2(d, x, 19.4, h, "purple", g=1 + i, flip=fl, r0=2.4)
    d.curve([(10.0, 19.4), (8.0, 12.0), (13.6, 8.0), (11.6, 4.6)], 2.6, "purple", g=4)
    vfig(d, 11.6, 9.4, 6.4, "stone", g=6)
    d.ring(11.6, 7.0, 2.6, 1.6, "purple", g=7)
    for (x, y) in ((4, 9), (16, 11), (8, 15), (5, 13)):
        d.put(x, y, "pink.l")
    waves(d, (17.6,), "water", 0.8, 2.0, 0.0, 20.0, g=10, period=5.0)


@pic("abyssal_maw")
def _(d):
    # Bocarra Abissal: uma mandibula colossal sai do fundo escuro, cheia de presas, para engolir
    d.fill(lambda x, y: ((x - 10) / 9.6) ** 2 + ((y - 7.0) / 6.6) ** 2 <= 1 and y <= 8.0, "deep", g=0)
    d.fill(lambda x, y: ((x - 10) / 9.6) ** 2 + ((y - 15.0) / 5.0) ** 2 <= 1 and y >= 12.4, "deep", g=1)
    d.fill(lambda x, y: ((x - 10) / 7.6) ** 2 + ((y - 10.2) / 3.4) ** 2 <= 1, "k")
    for x in (4, 7, 10, 13, 16):
        d.tri((x - 1.0, 7.0), (x + 1.0, 7.0), (x, 10.4), "W", g=3)
    for x in (5.5, 8.5, 11.5, 14.5):
        d.tri((x - 1.0, 13.4), (x + 1.0, 13.4), (x, 10.6), "W", g=4)
    d.disc(5.0, 3.6, 1.0, "cyan.l", g=6)
    d.disc(15.0, 3.6, 1.0, "cyan.l", g=6)


@pic("planet_crush")
def _(d):
    # Esmagamento Planetario: uma ESFERA de rochas arrancadas do terreno prende tudo, com pedacos ainda chegando
    d.disc(10, 9.6, 7.4, "stone", g=0)
    for (x, y, r) in ((7.0, 7.0, 1.8), (12.6, 12.0, 2.2), (13.0, 5.6, 1.2), (6.4, 13.0, 1.2)):
        d.disc(x, y, r, "stone.d", g=1)
    d.recolor(lambda x, y: (x - 10) + (y - 9.6) > 7.0, "dirt", g=2, only="stone")
    for i, (x, y) in enumerate(((1.4, 2.0), (18.0, 3.0), (17.6, 17.4))):
        d.rect(int(x), int(y), int(x) + 1, int(y) + 1, "stone", g=5 + i)
    d.rect(0, 18, 19, 19, "dirt", g=9)
    d.rect(5, 18, 14, 19, "k")


@pic("gravity_well")
def _(d):
    # Poco Gravitacional: a malha do espaco AFUNDA num funil (aneis descendo) e tudo escorrega para dentro
    for i, (y, rx, ry) in enumerate(((5.0, 9.6, 3.0), (8.8, 7.0, 2.2), (12.0, 4.6, 1.5), (14.6, 2.4, 0.9))):
        chain_link(d, 10, y, rx, ry + 0.6, 1.0, 0, "p" if i % 2 == 0 else "p.l", g=1 + i)
    for sx in (-1, 1):
        d.curve([(10 + sx * 9.6, 5.0), (10 + sx * 5.0, 8.4), (10 + sx * 1.6, 15.0)], 0.9, "p.d", g=5)
    d.line(10, 7.6, 10, 15.0, 0.9, "p.d", g=5)
    d.disc(10, 16.4, 1.8, "black", g=6)
    d.disc(10, 16.4, 0.8, "violet.l", g=7)
    for x0, x1 in ((0.6, 4.6), (19.4, 15.4)):
        d.arrow(x0, 11.0, x1, 15.6, 1.4, 2.4, "W", g=8)


@pic("dimension_slash")
def _(d):
    # Corte Dimensional: o golpe RASGA o espaco - pela fenda diagonal aparece o cosmos negro e estrelado
    d.taper(0.4, 19.6, 19.6, 0.4, 7.4, "violet.l", g=0)
    d.taper(0.4, 19.6, 19.6, 0.4, 5.4, "black", g=1)
    for (x, y) in ((6, 13), (9, 10), (12, 8), (14, 5), (10, 12), (8, 11)):
        d.put(x, y, "W")
    sparkle(d, 10.6, 9.4, 1.6, "W", g=3)
    cut_line(d, 2.0, 2.0, 7.0, 7.0, 1.6, "violet.l", "W", g=5)
    cut_line(d, 13.0, 13.0, 18.0, 18.0, 1.6, "violet.l", "W", g=7)


@pic("void_rift")
def _(d):
    # Fenda do Vazio: o alvo e SUGADO por uma fenda e cuspido por outra, longe
    # entrada: fenda grande embaixo a esquerda (seta entrando) | saida: fenda pequena no alto a direita (alvo saindo)
    for (cx, cy, rx, ry, g0) in ((5.0, 12.6, 3.0, 7.0, 0), (15.6, 5.6, 2.2, 4.8, 3)):
        d.ellipse(cx, cy, rx + 1.0, ry + 0.6, "purple", g=g0, rot=12)
        d.ellipse(cx, cy, rx - 0.6, ry - 1.2, "k", g=g0 + 1, rot=12)
        d.put(int(cx), int(cy - ry * 0.4), "violet.l")
    d.arrow(17.4, 18.6, 8.6, 14.6, 1.4, 2.6, "W", g=6)
    d.curve([(6.0, 4.0), (8.0, 0.8), (12.0, 1.6)], 1.0, "violet.l", g=7)
    vfig(d, 15.4, 8.0, 5.6, "stone", g=10, arms="up")


@pic("ragnarok")
def _(d):
    # Ragnarok: pilares ALTERNADOS de luz e de sombra caindo sobre marcas no chao
    for i, x in enumerate((3.0, 7.6, 12.4, 17.0)):
        light = i % 2 == 0
        top = 0.0 if light else 2.0
        d.rect(int(x) - 1, int(top), int(x) + 1, 15, "yellow.l" if light else "purple", g=1 + i)
        d.rect(int(x), int(top), int(x), 15, "W" if light else "black", g=10 + i)
        d.ellipse(x, 17.0, 2.4, 1.0, "yellow" if light else "violet", g=20 + i)
    d.rect(0, 18, 19, 19, "stone", g=30)


@pic("yin_yang")
def _(d):
    # Equilibrio Absoluto: ORBE DUPLO - um sol dourado que cura (cruz branca) e um astro negro que fere
    # (espinhos), girando um atras do outro numa orbita comum, cada um com sua cauda (estrela binaria)
    d.ring(10, 10, 7.4, 6.6, "p.d", g=0)
    d.arc(10, 10, 8.4, 5.8, 120, 215, "yellow.l", g=1)
    d.arc(10, 10, 8.4, 5.8, 300, 395, "purple", g=2)
    d.disc(5.2, 5.2, 4.2, "gold", g=3)
    d.disc(5.2, 5.2, 3.0, "yellow.l", g=4)
    plus(d, 5.2, 5.2, 2.4, 1.2, "W", g=5)
    burst(d, 14.8, 14.8, 5.0, 3.0, 7, "magenta", -90, (1.0, 0.8), g=6)
    d.disc(14.8, 14.8, 3.0, "black", g=7)
    d.put(14, 14, "violet.l")


# ---------------------------------------------------------------------------------------------- v0.8 DUELO ARCANO (MTG)
@pic("mana_leak")
def _(d):
    # Vazamento de Mana: frasco de mana TRINCADO pingando - o mana escorre e se perde numa poca
    flask(d, 8.6, 8.4, "mana", "glass", "wood", level=0.62)
    d.path([(4.0, 8.6), (6.0, 10.6), (5.0, 12.6), (7.0, 14.4)], 0.9, "W", g=9)
    for i, (x, y, r) in enumerate(((13.6, 13.4, 1.2), (15.6, 16.4, 1.4))):
        drop(d, x, y, r, "mana", 1.7, shine=False, g=10 + i)
    d.ellipse(14.0, 19.0, 5.6, 1.0, "mana", g=12)
    d.path([(11.6, 12.0), (13.0, 11.6)], 1.0, "mana", g=13)


@pic("redirect")
def _(d):
    # Redirecionar: o projetil INIMIGO (vermelho) faz a curva em U e volta, ja violeta, para quem o lancou
    d.line(5.0, 5.0, 13.0, 5.0, 2.2, "red", g=0)
    d.arc(13.0, 10.0, 6.1, 3.9, 270, 450, "red", g=0)
    d.recolor(lambda x, y: y > 10.0, "p", g=1, only="red")
    d.arrow(13.0, 15.0, 5.6, 15.0, 2.2, 3.6, "p", g=1)
    orb(d, 4.0, 5.0, 2.6, "red", g=3)
    d.ring(2.6, 15.0, 2.6, 1.4, "red", g=5)


@pic("fork")
def _(d):
    # Copiar Magia: a magia que passou (vermelha) se DIVIDE - a copia violeta sai com a SUA mira
    d.line(0.6, 10.0, 8.0, 10.0, 2.4, "red", g=0)
    d.curve([(7.4, 10.0), (11.0, 10.0), (14.0, 5.0)], 2.2, "red", g=0)
    tri_tip(d, 14.8, 3.8, 13.0, 6.4, "red", g=0, size=3.6)
    d.curve([(7.4, 10.0), (11.0, 10.0), (14.0, 15.0)], 2.2, "p", g=2)
    tri_tip(d, 14.8, 16.2, 13.0, 13.6, "p", g=2, size=3.6)
    d.disc(7.6, 10.0, 1.8, "W", g=4)
    sparkle(d, 18.0, 11.0, 2.2, "p.h", g=5)


@pic("spell_steal")
def _(d):
    # Roubar Encanto: garra violeta arranca o ENCANTO (orbe verde com seta de bonus) do alvo e o puxa para si
    vfig(d, 16.0, 19.0, 10.0, "stone", g=0)
    orb(d, 9.6, 7.6, 3.8, "green", g=2)
    d.arrow(9.6, 10.0, 9.6, 5.0, 1.4, 2.4, "W", g=3)
    for i, (y0, y1) in enumerate(((2.6, 4.4), (7.6, 7.6), (12.6, 10.8))):
        d.curve([(0.6, y0 + (y0 - 7.6) * 0.3), (4.0, y0), (6.4, y1)], 1.4, "violet.l", g=5)
    d.disc(1.0, 7.6, 2.4, "violet.l", g=5)
    d.arc(12.0, 9.0, 5.6, 4.6, 200, 300, "green.l", g=7)


@pic("hexproof")
def _(d):
    # Resguardo: escudo com um HEXAGONO runico - feiticos inimigos batem nele e ricocheteiam
    shield(d, 8.6, 10.6, 0.98, "silver", None)
    hx = [(8.6 + 3.8 * math.cos(math.radians(a)), 9.8 + 3.8 * math.sin(math.radians(a))) for a in range(-90, 270, 60)]
    d.path(hx + [hx[0]], 1.3, "cyan", g=2)
    d.disc(8.6, 9.8, 1.2, "cyan.l", g=3)
    for i, (y, s) in enumerate(((4.0, -1), (14.0, 1))):
        d.line(19.6, y + s * 2.6, 16.6, y, 1.4, "red", g=5 + i)
        d.arrow(16.6, y, 19.6, y - s * 2.6, 1.4, 2.4, "red", g=5 + i)
        sparkle(d, 16.2, y, 1.6, "W", g=8)


@pic("exile")
def _(d):
    # Exilar: o alvo e EMPURRADO para dentro de uma porta para o vazio e some (sem loot)
    d.rect(10, 1, 18, 18, "p", g=0)
    d.rect(12, 3, 16, 17, "night", g=1)
    for (x, y) in ((13, 5), (15, 8), (14, 12), (13, 15)):
        d.put(x, y, "W")
    vfig(d, 9.6, 17.6, 12.6, "stone", g=3)
    d.erase(lambda x, y: x > 11.6 and 3 <= y <= 17 and x < 16.6)
    for (x, y) in ((12, 9), (13, 11), (14, 7)):
        d.put(x, y, "stone", g=3)
    d.arrow(0.6, 11.0, 5.0, 11.0, 1.6, 2.6, "W", g=6)


@pic("time_walk")
def _(d):
    # Passo no Tempo: relogio com a seta GIRANDO PARA TRAS (todas as recargas zeradas) e uma gota de mana com "+"
    # (o mana que volta)
    clock_face(d, 8.6, 8.6, 7.0, face="white", rim="p", ticks="k", hands=((-60, 0.6), (0, 0.4)), g=1)
    d.arc_arrow(8.6, 8.6, 8.6, 20, -200, 1.6, 3.0, "p.l", g=6)
    drop(d, 15.2, 15.0, 3.4, "mana", 1.7, g=10)
    plus(d, 15.2, 16.0, 1.8, 1.0, "W", g=12)


@pic("indestructible")
def _(d):
    # Indestrutivel: coracao numa aura DOURADA - a lamina que o atinge se parte
    d.ring(9.0, 11.0, 8.4, 6.6, "gold", g=0)
    d.heart(9.0, 11.6, 0.62, "heart", g=1)
    d.line(19.6, 0.4, 15.4, 4.6, 2.2, "steel", g=3)
    d.line(13.0, 7.0, 11.6, 8.4, 2.2, "steel", g=4)
    d.line(18.6, 6.4, 16.8, 8.2, 1.2, "steel.l", g=5)
    sparkle(d, 14.4, 5.6, 2.4, "W", g=7)
    sparkle(d, 3.0, 3.0, 1.6, "yellow.l", g=8)


@pic("wrath")
def _(d):
    # Ira Divina: o olho divino no ceu desce colunas de luz sobre TODAS as criaturas do chao
    for i, x in enumerate((3.2, 10.0, 16.8)):
        d.rect(int(x) - 1, 5, int(x) + 1, 15, "yellow.l", g=1 + i)
        d.rect(int(x), 5, int(x), 15, "W", g=1 + i)
    d.ellipse(10, 3.4, 9.6, 3.2, "gold", g=6)
    eye(d, 10, 3.4, 0.6, "white", "red", "k", g=7)
    for i, x in enumerate((3.2, 10.0, 16.8)):
        skull(d, x + 0.5, 17.2, 0.36, "bone", g=10 + i, jaw=False)


@pic("deathtouch")
def _(d):
    # Toque Mortal: a ponta de um dedo encosta num CRANIO envenenado - qualquer ferida mata
    skull(d, 11.6, 11.6, 0.82, "bone", g=0)
    d.recolor(lambda x, y: True, "poison", g=0, only="k")
    d.halo("black", 1.0, g=40)
    d.line(0.4, 0.4, 5.0, 5.0, 2.6, "flesh", g=3)
    d.disc(5.2, 5.2, 1.4, "flesh", g=3)
    sparkle(d, 6.8, 6.8, 2.0, "poison", g=5)
    drop(d, 15.4, 18.6, 1.0, "poison", 1.6, shine=False, g=6)


@pic("trample")
def _(d):
    # Atropelar: pegadas ENORMES de fera passam por cima - o dano que sobra segue adiante (seta)
    for i, (x, y) in enumerate(((5.0, 14.6), (11.6, 8.4))):
        d.ellipse(x, y + 0.8, 2.8, 2.2, "brown", g=i * 5)
        for k, (ox, oy) in enumerate(((-2.6, -2.4), (0.0, -3.4), (2.6, -2.4))):
            d.disc(x + ox, y + oy, 1.1, "brown", g=i * 5 + 1 + k)
    d.arrow(13.4, 5.4, 19.4, 0.6, 1.8, 3.0, "W", g=12)
    for (x0, y0) in ((0.6, 9.6), (2.0, 6.6)):
        d.line(x0, y0, x0 + 3.0, y0 - 2.4, 0.9, "p.l", g=14)


@pic("instant")
def _(d):
    # Instantaneo: um RAIO dourado grande com rastros de velocidade - a magia sai na hora e pula a Pilha
    # (nada de relogio: Atraso e Acelerar ja usam ampulheta/divisas)
    for i, (y, x0, x1) in enumerate(((5.0, 0.6, 6.6), (9.6, 0.4, 5.0), (14.2, 1.4, 6.0))):
        d.line(x0, y, x1, y, 1.2, "W" if i == 1 else "p.l", g=1 + i)
    bolt(d, 11.6, 10.0, 1.02, "yellow", g=5)
    d.line(13.4, 2.6, 9.4, 10.0, 0.9, "yellow.h", g=6)
    sparkle(d, 17.4, 3.4, 2.2, "W", g=9)


@pic("in_response")
def _(d):
    # Em Resposta: o selo de uma conjuracao INIMIGA (aro vermelho com "!") aparece no alto e a SUA magia
    # (seta violeta) sobe sozinha e o acerta antes de ele terminar - choque amarelo no encontro.
    # Tudo no miolo do triangulo do gatilho (estreito em cima).
    d.ring(10, 5.8, 3.8, 2.4, "red", g=0)
    d.disc(10, 5.8, 2.4, "blood.d", g=1)
    d.rect(10, 4, 10, 6, "W", g=2)
    d.put(10, 8, "W")
    d.arrow(10, 19.4, 10, 12.6, 2.6, 3.6, "p.l", g=6)
    for i, sx in enumerate((-1, 1)):
        d.line(10 + sx * 3.6, 18.8, 10 + sx * 2.8, 15.6, 1.0, "p", g=8 + i)
    burst(d, 10, 11.0, 3.4, 1.4, 6, "yellow", -90, (1.0, 0.7), g=12)
    d.put(10, 11, "W")


# ---------------------------------------------------------------------------------------------- v0.8 power_up / arcane
@pic("law_ruin")
def _(d):
    # Lei: Ruina - o chao do dominio RACHA e os blocos sao arrancados e lancados para cima
    d.rect(0, 13, 19, 19, "stone", g=0)
    d.rect(0, 13, 19, 13, "grass", g=1)
    crack = [(0.6, 18.6), (4.6, 15.6), (8.0, 17.4), (10.0, 13.0), (12.6, 16.6), (16.0, 14.6), (19.4, 18.0)]
    d.path(crack, 2.4, "k")
    d.path(crack, 0.9, "lava.l", g=2)
    for i, (x, y, s) in enumerate(((4.0, 8.6, 1.6), (10.4, 4.0, 2.2), (16.0, 7.6, 1.4), (7.0, 1.6, 1.0),
                                   (14.0, 1.0, 0.8))):
        d.rect(int(x - s), int(y - s), int(x + s), int(y + s), "dirt" if i % 2 else "stone", g=3 + i)
        if i < 3:
            d.rect(int(x - s), int(y - s), int(x + s), int(y - s), "grass", g=20 + i)
            d.line(x, y + s + 1.4, x, y + s + 3.4, 0.9, "stone.l", g=10)


@pic("counterspell")
def _(d):
    # Contramagica (v0.8): o circulo runico INIMIGO se estilhaca como vidro - runas quebradas voando
    d.ring(10, 10, 8.4, 6.6, "red")
    d.path([(10, 4.4), (14.8, 12.8), (5.2, 12.8), (10, 4.4)], 1.2, "red.l", g=1)
    for (x0, y0, x1, y1) in ((10, 10, 3.0, 1.0), (10, 10, 19.0, 6.0), (10, 10, 13.0, 19.4), (10, 10, 1.0, 14.0)):
        d.line(x0, y0, x1, y1, 1.6, None)
    for (x0, y0, x1, y1) in ((10, 10, 3.0, 1.0), (10, 10, 19.0, 6.0), (10, 10, 13.0, 19.4), (10, 10, 1.0, 14.0)):
        d.line(x0, y0, x0 + (x1 - x0) * 0.35, y0 + (y1 - y0) * 0.35, 0.9, "W", g=4)
    for i, (x, y) in enumerate(((2.0, 3.0), (17.4, 2.0), (18.0, 16.6), (2.4, 18.0))):
        d.tri((x, y), (x + 1.8, y + 0.8), (x + 0.6, y + 2.0), "glass", g=6 + i)
    sparkle(d, 10, 10, 3.0, "p.h", g=12)


# ======================================================================================================  v0.9
# "Classes, Caminhos e o Dragao" (docs/V09_DESIGN.md §2.3, §5, §6): formas de ARMA do nucleo (strike, arrow, stance,
# parry), formas das classes Guerreiras e A Distancia e as 40 origens das 10 classes novas (moldura de origem = gema
# na cor da classe, como as origens antigas). Cada pictograma le a tecnica; os parecidos foram afastados de proposito
# (strike x seal/temp_enchant, spin x whirlwind, mark_shot x mark, celestial_arrow_rain x sky_lances, smoke_bomb x
# arcane_bomb, beast_companion x wolf_form, step_of_wind x double_jump...).

def arrow_shaft(d, x0, y0, x1, y1, shaft="wood", head="steel", fletch="white", w=1.3, hs=3.4, hw=1.9, g=0):
    """Flecha de verdade: penas em (x0,y0), ponta farpada em (x1,y1)."""
    L = math.hypot(x1 - x0, y1 - y0) or 1e-9
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    px_, py_ = -uy, ux
    bx, by = x1 - ux * hs, y1 - uy * hs
    d.line(x0 + ux * 0.6, y0 + uy * 0.6, bx + ux * 0.6, by + uy * 0.6, w, shaft, g=g)
    if fletch:
        for k, side in enumerate((1, -1)):
            d.poly([(x0 + ux * 4.0, y0 + uy * 4.0), (x0 + ux * 1.8 + side * px_ * 1.9, y0 + uy * 1.8 + side * py_ * 1.9),
                    (x0 - ux * 0.4 + side * px_ * 1.9, y0 - uy * 0.4 + side * py_ * 1.9),
                    (x0 + ux * 0.4, y0 + uy * 0.4)], fletch, g=g + 2 + k)
    d.poly([(x1 + ux * 0.3, y1 + uy * 0.3), (bx + px_ * hw, by + py_ * hw), (bx + ux * 0.9, by + uy * 0.9),
            (bx - px_ * hw, by - py_ * hw)], head, g=g + 1)


def blade(d, x0, y0, x1, y1, m="steel", guard="gold", grip="wood", w=2.0, g=0, grip_len=2.6, guard_w=2.0):
    """Lamina curta (adaga): guarda em (x0,y0), ponta em (x1,y1), cabo para tras da guarda."""
    L = math.hypot(x1 - x0, y1 - y0) or 1e-9
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    px_, py_ = -uy, ux
    d.line(x0, y0, x1 - ux * 1.8, y1 - uy * 1.8, w, m, g=g)
    d.tri((x1 + ux * 0.3, y1 + uy * 0.3), (x1 - ux * 2.4 + px_ * w / 2, y1 - uy * 2.4 + py_ * w / 2),
          (x1 - ux * 2.4 - px_ * w / 2, y1 - uy * 2.4 - py_ * w / 2), m, g=g)
    if grip:
        d.line(x0 - ux * 0.6, y0 - uy * 0.6, x0 - ux * grip_len, y0 - uy * grip_len, 1.5, grip, g=g + 2)
    if guard:
        d.line(x0 + px_ * guard_w, y0 + py_ * guard_w, x0 - px_ * guard_w, y0 - py_ * guard_w, 1.3, guard, g=g + 1)


def fist(d, cx, cy, s=1.0, m="flesh", g=0, flip=False, sleeve=None):
    """Punho fechado socando para a direita (flip = para a esquerda)."""
    sx = -1 if flip else 1
    d.fill(lambda x, y: abs(y - cy) <= 3.4 * s and -3.8 * s <= (x - cx) * sx <= 2.2 * s, m, g=g)
    for k in range(4):
        d.disc(cx + sx * 2.2 * s, cy - 2.6 * s + k * 1.75 * s, 1.15 * s, m, g=g)
    d.ellipse(cx - sx * 0.4 * s, cy + 2.8 * s, 2.8 * s, 1.2 * s, m, g=g + 1)
    if s >= 0.8:
        for k in range(1, 4):
            yy = cy - 3.5 * s + k * 1.75 * s
            d.line(cx + sx * 0.8 * s, yy, cx + sx * 3.0 * s, yy, 1.0, m.split(".")[0] + ".d", g=g)
    if sleeve:
        d.line(cx - sx * 4.2 * s, cy, cx - sx * 7.0 * s, cy, 5.0 * s, sleeve, g=g + 2)


def fist_front(d, cx, cy, s=1.0, m="flesh", g=0, sleeve=None):
    """Punho fechado DE FRENTE (os nos dos dedos vindo na direcao de quem olha): 4 nos, vincos e o polegar."""
    d.fill(lambda x, y: abs(x - cx) <= 3.8 * s and cy - 2.4 * s <= y <= cy + 3.2 * s, m, g=g)
    for k in range(4):
        d.disc(cx - 2.85 * s + k * 1.9 * s, cy - 2.2 * s, 1.15 * s + 0.1, m, g=g)
    for k in range(1, 4):
        x = cx - 3.8 * s + k * 1.9 * s
        d.line(x, cy - 2.6 * s, x, cy + 0.4 * s, 1.0, m.split(".")[0] + ".d", g=g)
    d.fill(lambda x, y: cx - 3.8 * s <= x <= cx + 1.2 * s and cy + 0.9 * s <= y <= cy + 2.4 * s, m, g=g + 1)
    if sleeve:
        d.fill(lambda x, y: abs(x - cx) <= 3.0 * s and cy + 3.2 * s < y <= cy + 6.0 * s, sleeve, g=g + 2)


def note(d, x, y, m="W", s=1.0, g=0, flag=True, stem=7.0):
    """Colcheia: cabeca oval em (x,y), haste para cima e bandeirinha."""
    d.ellipse(x, y, 2.0 * s, 1.45 * s, m, g=g, rot=-25)
    d.line(x + 1.5 * s, y - 0.3 * s, x + 1.5 * s, y - stem * s, 1.2, m, g=g)
    if flag:
        d.curve([(x + 1.5 * s, y - stem * s), (x + 4.0 * s, y - stem * s + 2.0 * s),
                 (x + 3.4 * s, y - stem * s + 4.2 * s)], 1.1, m, g=g)


def gear(d, cx, cy, r, teeth=8, m="copper", hole=None, g=0, tw=1.8, rot=0.0):
    """Engrenagem: disco + dentes radiais; hole = material do furo (None = vazado)."""
    d.disc(cx, cy, r, m, g=g)
    for i in range(teeth):
        a = math.radians(rot + i * 360 / teeth)
        d.line(cx + math.cos(a) * r * 0.7, cy + math.sin(a) * r * 0.7, cx + math.cos(a) * (r + 1.5),
               cy + math.sin(a) * (r + 1.5), tw, m, g=g)
    d.disc(cx, cy, max(0.9, r * 0.4), hole)


def paw(d, cx, cy, s=1.0, m="p", g=0):
    """Pegada de fera: almofada + 4 dedos."""
    d.ellipse(cx, cy + 1.3 * s, 2.5 * s, 2.0 * s, m, g=g)
    for (ox, oy) in ((-2.7, -1.5), (-0.95, -3.1), (0.95, -3.1), (2.7, -1.5)):
        d.ellipse(cx + ox * s, cy + oy * s, 0.9 * s + 0.2, 1.15 * s + 0.2, m, g=g + 1)


def bomb(d, cx, cy, r, m="black", fuse="wood", spark="yellow", g=0):
    """Bomba redonda com bocal, pavio e faisca."""
    d.disc(cx, cy, r, m, g=g)
    bx, by = cx + r * 0.42, cy - r * 0.92
    d.rect(int(bx) - 1, int(by) - 1, int(bx) + 1, int(by), "iron", g=g + 1)
    d.curve([(bx, by - 1.0), (bx + 1.6, by - 3.2), (bx + 3.0, by - 2.6)], 1.0, fuse, g=g + 2)
    if spark:
        sparkle(d, bx + 3.4, by - 3.0, 2.4, spark, g=g + 3)
    d.disc(cx - r * 0.42, cy - r * 0.38, max(0.8, r * 0.2), m.split(".")[0] + ".h", g=g)


def dashed(d, pts, m="p.l", w=1.2, on=2, off=2, g=0):
    """Tracejado ao longo de uma polilinha (amostrada por pixel)."""
    samples = []
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(bx - ax, by - ay)))
        samples += [(ax + (bx - ax) * i / n, ay + (by - ay) * i / n) for i in range(n)]
    for i in range(len(samples) - 1):
        if i % (on + off) < on:
            d.line(samples[i][0], samples[i][1], samples[i + 1][0], samples[i + 1][1], w, m, g=g)


def wolf_head(d, cx, cy, s=1.0, m="iron", g=0, eye="yellow", collar=None, flip=False):
    """Cabeca de lobo de PERFIL (focinho para a direita; flip = para a esquerda)."""
    k = -1 if flip else 1

    def P(u, v):
        return (cx + k * u * s, cy + v * s)
    d.ellipse(cx - k * 0.8 * s, cy + 0.2 * s, 4.0 * s, 3.8 * s, m, g=g)
    d.poly([P(0.4, -2.6), P(5.0, -0.8), P(6.8, 0.4), P(6.6, 1.8), P(4.6, 2.6), P(0.4, 3.6)], m, g=g)
    d.poly([P(-3.6, -1.6), P(-2.2, -7.4), P(0.6, -2.6)], m, g=g + 1)
    d.poly([P(-2.8, -2.4), P(-2.2, -5.4), P(-0.6, -2.6)], "pink.d", g=g + 4)
    for v in (0.0, 1.8, 3.6):
        d.poly([P(-4.2, v - 1.0), P(-6.4, v + 0.4), P(-4.0, v + 1.2)], m, g=g)
    d.disc(*P(6.4, 0.6), 0.9 * s + 0.15, "black", g=g + 2)
    d.put(*P(1.6, -0.9), eye)
    d.put(*P(2.6, -1.0), eye)
    d.line(*P(2.6, 2.4), *P(6.0, 2.0), 0.7, m.split(".")[0] + ".d", g=g)
    if collar:
        d.line(*P(-3.4, 1.6), *P(-1.0, 4.4), 1.6 * s, collar, g=g + 3)


# ---------------------------------------------------------------------------------------------- v0.9 FORMAS DE ARMA (nucleo)
@pic("strike")
def _(d):
    # Golpe: espada no MEIO do golpe com o rastro de energia (cor do elemento) e o impacto na ponta
    d.arc(3.6, 17.4, 16.4, 12.6, 236, 304, "p.d", g=0)
    d.arc(3.6, 17.4, 15.6, 13.6, 246, 304, "p.l", g=1)
    sword(d, 3.6, 17.4, 14.0, 4.6, blade="steel", guard="gold", grip="wood", w=2.6, g=3)
    d.recolor(lambda x, y: x + y < 23.0 and x > 8.0, "p.h", g=3, only="steel")
    burst(d, 15.6, 3.4, 3.8, 1.4, 6, "W", -90, (1.0, 0.7), g=9)
    for (x, y) in ((18.4, 7.6), (11.4, 1.0)):
        d.put(x, y, "p.h", g=12)


@pic("arrow")
def _(d):
    # Flecha: flecha REAL (haste de madeira, ponta de aco, penas) atravessando o anel runico do elemento
    for i, (x0, y0) in enumerate(((0.8, 12.4), (6.6, 18.6), (2.6, 16.6))):
        d.line(x0, y0, x0 + 2.4, y0 - 2.4, 1.0, "p.l", g=4 + i)
    chain_link(d, 11.4, 8.6, 6.0, 2.6, 1.5, 45, "p", g=0)
    arrow_shaft(d, 3.6, 16.4, 17.6, 2.4, "wood", "steel", "white", 1.4, 3.8, 2.1, g=8)
    chain_link(d, 11.4, 8.6, 6.0, 2.6, 1.5, 45, "p.l", g=1)
    d.recolor(lambda x, y: (x - 11.4) + (y - 8.6) < -1.0, "p.d", g=0, only="p")
    d.put(16, 3, "W")


@pic("stance")
def _(d):
    # Postura: guerreiro firme de pernas abertas, espada erguida em guarda; pilares de energia subindo em volta
    d.ellipse(10, 17.6, 9.0, 2.2, "q", g=0)
    d.ellipse(10, 17.6, 6.0, 1.2, "p.l", g=1)
    for i, (x, y0, y1) in enumerate(((1.6, 15.6, 7.0), (18.4, 15.6, 7.0))):
        d.line(x, y0, x, y1, 1.2, "p.l", g=2 + i)
        chevron(d, x, 4.8, 1.6, 1.0, 0, -1, "p.h", g=7)
    m = "W"
    d.line(13.6, 8.6, 16.4, 0.6, 1.8, "steel", g=12)
    d.line(12.0, 9.0, 15.4, 10.2, 1.2, "gold", g=13)
    d.disc(9.0, 4.2, 2.2, m, g=10)
    d.line(9.0, 6.4, 9.4, 11.0, 2.6, m, g=10)
    d.path([(8.6, 11.0), (5.6, 13.6), (5.0, 17.4)], 1.8, m, g=10)
    d.path([(10.0, 11.0), (12.6, 13.6), (13.6, 17.4)], 1.8, m, g=10)
    d.path([(8.6, 7.4), (6.0, 9.0), (5.0, 6.6)], 1.4, m, g=10)
    d.path([(9.6, 7.4), (11.6, 9.0), (13.6, 9.6)], 1.4, m, g=10)
    d.disc(5.0, 6.2, 1.4, "p.l", g=14)


@pic("parry")
def _(d):
    # Aparar: a espada do conjurador DEITADA em guarda alta (lamina brilhando no elemento) e a lamina INIMIGA
    # (ferro escuro, guarda vermelha) descendo e batendo nela: faisca em estrela no choque e lascas voando.
    # (Revisao: o broquel redondo com bossa dourada lia como cadeado no tamanho real.)
    d.line(10.0, 0.4, 10.0, 7.4, 2.2, "iron", g=0)
    d.tri((8.8, 7.0), (11.2, 7.0), (10.0, 9.2), "iron", g=0)
    d.line(7.6, 0.8, 12.4, 0.8, 1.2, "red", g=1)
    sword(d, 1.0, 12.4, 19.0, 12.4, blade="steel", guard="gold", grip="wood", w=2.6, g=3)
    d.recolor(lambda x, y: x > 9.0, "p.h", g=3, only="steel")
    sparkle(d, 10.0, 10.4, 4.6, "yellow", g=8)
    d.disc(10.0, 10.4, 0.8, "W", g=9)
    for (x0, y0, x1, y1) in ((6.4, 7.6, 4.4, 5.8), (13.6, 7.6, 15.6, 5.8)):
        d.line(x0, y0, x1, y1, 1.0, "W", g=10)
    d.arc_arrow(10.0, 12.4, 6.4, 120, 165, 1.2, 2.2, "p.l", g=11)


@pic("guard")
def _(d):
    # Guarda (efeito, Resistencia): antebraco ERGUIDO em bloqueio com bracadeira de aco larga (frisos dourados),
    # punho cerrado no alto, o golpe (seta vermelha) quicando na bracadeira e a barreira do elemento atras.
    # (Revisao: a versao deitada virava um borrao no tamanho real.)
    d.arc(10, 12.0, 9.6, 8.4, 200, 340, "p", g=0)
    d.line(4.0, 19.4, 9.8, 9.4, 3.6, "flesh", g=2)
    d.line(5.0, 17.4, 9.2, 10.6, 5.0, "steel", g=3)
    d.line(4.6, 17.0, 6.0, 17.8, 1.0, "gold", g=4)
    d.line(8.4, 10.6, 9.8, 11.4, 1.0, "gold", g=4)
    fist_front(d, 11.4, 6.4, 0.82, "flesh", g=6)
    d.arrow(19.0, 4.4, 13.6, 12.4, 1.6, 2.8, "red", g=8)
    sparkle(d, 12.4, 13.6, 3.4, "yellow", g=10)
    d.disc(12.4, 13.6, 0.7, "W", g=11)


# ---------------------------------------------------------------------------------------------- v0.9 FORMAS: guerreiras
@pic("cleave_arc")
def _(d):
    # Arco de Lamina: uma meia-lua LARGA e horizontal de energia varrendo a frente; espadao embaixo
    crescent(d, 10, 15.0, 10.0, 0, 3.4, 9.2, "p")
    crescent(d, 10, 15.0, 8.4, 0, 2.8, 7.8, "p.h")
    for x in (1.4, 18.6):
        d.put(x, 9.6, "W")
    sword(d, 2.0, 17.4, 18.6, 17.4, blade="steel", guard="gold", grip="wood", w=2.4, g=6)


@pic("charge_rush")
def _(d):
    # Investida: guerreiro inclinado correndo de escudo na frente, linhas de velocidade e o impacto adiante
    for i, (x0, y) in enumerate(((0.4, 4.6), (0.0, 8.6), (0.4, 12.6))):
        d.line(x0, y, x0 + 3.4, y, 1.2, "p.l", g=1 + i)
    m = "W"
    d.disc(10.0, 4.4, 2.3, m, g=3)
    d.line(9.2, 6.6, 7.0, 11.2, 2.8, m, g=3)
    d.path([(7.0, 11.0), (4.0, 13.4), (2.0, 17.6)], 1.9, m, g=3)
    d.path([(7.4, 11.0), (10.6, 13.6), (10.2, 17.8)], 1.9, m, g=3)
    d.path([(8.6, 7.6), (5.0, 9.4), (4.0, 7.0)], 1.4, m, g=3)
    shield(d, 13.6, 9.6, 0.6, "steel", "p", g=6)
    burst(d, 18.8, 9.6, 2.8, 1.0, 5, "yellow", 0, (1.0, 0.7), g=9)


@pic("spin")
def _(d):
    # Giro: visto de cima - o guerreiro no centro com DUAS laminas abertas e os arcos do giro
    d.arc_arrow(10, 10, 8.6, 196, 286, 1.6, 3.0, "p.l", g=1)
    d.arc_arrow(10, 10, 8.6, 16, 106, 1.6, 3.0, "p.l", g=2)
    blade(d, 12.0, 8.0, 17.8, 2.2, "steel", "gold", None, 2.2, g=4)
    blade(d, 8.0, 12.0, 2.2, 17.8, "steel", "gold", None, 2.2, g=7)
    d.disc(10, 10, 3.0, "p", g=10)
    d.disc(10, 10, 1.4, "p.h", g=11)


@pic("war_cry")
def _(d):
    # Grito de Guerra: cabeca de perfil com elmo, boca aberta e ondas de som saindo para a frente
    for i, r in enumerate((4.8, 7.4, 10.0)):
        d.arc(10.6, 11.4, r, r - 1.4, -42, 42, "p.l" if i % 2 == 0 else "p", g=1 + i)
    d.disc(6.6, 11.0, 4.6, "flesh", g=5)
    d.fill(lambda x, y: (x - 6.6) ** 2 + (y - 11.0) ** 2 <= 5.0 ** 2 and y <= 9.6, "steel", g=6)
    d.rect(7, 8, 8, 12, "steel", g=6)
    d.line(1.6, 8.0, 0.6, 3.6, 1.6, "red", g=7)
    d.put(9, 10, "k")
    d.poly([(8.6, 12.0), (11.4, 11.4), (11.0, 14.6), (8.6, 13.6)], "k")
    d.put(10, 12, "W")


@pic("battle_avatar")
def _(d):
    # Avatar de Batalha (forma G): o gigante de energia armado atras do guerreiro, espadao erguido
    sword(d, 2.4, 18.6, 18.6, 1.0, blade="p.h", guard="gold", grip="wood", w=2.8, g=0)
    d.poly([(4.6, 9.0), (15.4, 9.0), (13.6, 17.6), (6.4, 17.6)], "p", g=4)
    d.disc(4.6, 9.8, 2.8, "p.l", g=5)
    d.disc(15.4, 9.8, 2.8, "p.l", g=6)
    d.disc(10, 5.4, 3.4, "p.l", g=7)
    d.rect(8, 5, 11, 5, "W")
    d.tri((6.8, 4.0), (5.6, 0.6), (8.4, 3.0), "gold", g=8)
    d.tri((13.2, 4.0), (14.4, 0.6), (11.6, 3.0), "gold", g=8)
    d.line(10, 10.0, 10, 16.0, 1.2, "q", g=9)
    vfig(d, 10, 19.8, 7.0, "W", g=12)


# ---------------------------------------------------------------------------------------------- v0.9 FORMAS: a distancia
@pic("volley")
def _(d):
    # Saraivada: tres flechas paralelas subindo juntas
    for i, (x0, y0) in enumerate(((1.0, 11.0), (4.6, 14.6), (8.2, 18.2))):
        arrow_shaft(d, x0, y0, x0 + 10.4, y0 - 10.4, "wood", "steel", "p.l", 1.2, 3.2, 1.8, g=i * 5)


@pic("throw")
def _(d):
    # Arremesso: a adaga girando no fim de uma trajetoria tracejada (marcas de rotacao em volta)
    dashed(d, [(1.0, 18.4), (3.6, 13.0), (7.0, 9.6), (10.0, 8.6)], "p.l", 1.4, 2, 2, g=0)
    blade(d, 11.6, 12.6, 17.8, 4.0, "steel", "gold", "wood", 2.2, g=3)
    d.arc(14.4, 8.4, 7.8, 6.6, 200, 260, "p", g=8)
    d.arc(14.4, 8.4, 7.8, 6.6, 20, 80, "p", g=9)


@pic("snare")
def _(d):
    # Armadilha: o laco de corda aberto no chao (no corredico) com a runa brilhando no meio e a corda puxada
    d.ellipse(9.6, 13.0, 8.6, 4.6, "p.d", g=0)
    d.path([(6.6, 10.6), (12.6, 10.6), (9.6, 15.6), (6.6, 10.6)], 1.0, "p.l", g=1)
    chain_link(d, 9.6, 13.0, 7.6, 3.8, 1.6, 0, "sand", g=3)
    d.ellipse(16.6, 11.6, 1.8, 1.4, "sand", g=4)
    d.curve([(17.6, 10.6), (19.4, 6.0), (15.0, 3.4), (17.6, 0.4)], 1.4, "sand", g=5)
    for (x, y) in ((3.0, 4.6), (9.6, 5.6)):
        sparkle(d, x, y, 2.2, "p.l", g=8)


@pic("song")
def _(d):
    # Cancao: par de colcheias unidas por barra, sobre a pauta ondulada que gira em volta (aura continua)
    d.arc(10, 10, 9.6, 8.4, 150, 390, "p.d", g=0)
    d.ellipse(5.6, 14.6, 2.2, 1.6, "p.l", g=2, rot=-25)
    d.ellipse(13.6, 12.6, 2.2, 1.6, "p.l", g=3, rot=-25)
    d.line(7.2, 14.2, 7.2, 4.8, 1.2, "p.l", g=2)
    d.line(15.2, 12.2, 15.2, 2.8, 1.2, "p.l", g=3)
    d.line(7.2, 4.6, 15.2, 2.6, 2.0, "p.l", g=4)
    d.put(4, 13, "W")


@pic("mark_shot")
def _(d):
    # Tiro Marcado: alvo (aneis) com a flecha cravada no centro
    d.disc(9.0, 11.0, 8.0, "p", g=0)
    d.disc(9.0, 11.0, 6.2, "white", g=1)
    d.disc(9.0, 11.0, 4.2, "red", g=2)
    d.disc(9.0, 11.0, 2.2, "white", g=3)
    arrow_shaft(d, 18.6, 1.4, 9.4, 10.6, "wood", None, "white", 1.6, 0.1, 0.1, g=5)
    d.put(9, 11, "k")


@pic("celestial_arrow_rain")
def _(d):
    # Chuva de Flechas Celestial (forma G): o halo celeste no alto e as flechas de luz caindo retas
    d.ellipse(10, 3.0, 9.4, 2.6, "gold", g=0)
    d.ellipse(10, 3.0, 7.4, 1.2, "yellow.h", g=1)
    d.star(10, 3.0, 3.4, 1.3, 4, -90, "W", g=2)
    for i, (x, y0) in enumerate(((2.6, 5.6), (7.4, 8.0), (12.6, 6.4), (17.4, 8.6))):
        arrow_shaft(d, x, y0, x, y0 + 10.4, "yellow", "W", "gold", 1.0, 2.8, 1.5, g=4 + i * 4)


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Guerreiro
@pic("riposte")
def _(d):
    # Contra-ataque: a espada aparou e VOLTA - seta curva em volta da lamina e faisca do bloqueio
    d.arc_arrow(10, 10, 7.8, 120, 380, 1.8, 3.2, "p.l", g=0)
    sword(d, 4.0, 16.0, 16.4, 3.6, blade="steel", guard="gold", grip="wood", w=2.6, g=3)
    sparkle(d, 5.0, 4.6, 3.0, "W", g=8)


@pic("blade_storm")
def _(d):
    # Tempestade de Laminas: cinco adagas girando em roda (catavento de aco) em volta do redemoinho
    spiral(d, 10, 10, 0.6, 3.4, 1.1, 1.2, "p.l", 0, g=0)
    for i in range(5):
        a = math.radians(i * 72 - 90)
        ux, uy = math.cos(a), math.sin(a)
        px_, py_ = -uy, ux
        gx, gy = 10 + ux * 7.0 - px_ * 2.2, 10 + uy * 7.0 - py_ * 2.2
        blade(d, gx, gy, gx + px_ * 6.0 - ux * 1.2, gy + py_ * 6.0 - uy * 1.2, "steel", "gold", None, 1.8,
              g=3 + i * 3, guard_w=1.3)


@pic("heroic_strike")
def _(d):
    # Golpe Heroico: espada descendo de ponta no chao, explosao de luz e pedras saltando
    d.rect(0, 17, 19, 19, "stone", g=0)
    burst(d, 10, 16.4, 8.6, 3.2, 8, "yellow", -90, (1.0, 0.7, 0.85), g=1)
    d.erase(lambda x, y: y > 17.2 and not (abs(x - 10) < 6.0))
    d.rect(0, 18, 19, 19, "stone", g=0)
    sword(d, 10, 0.6, 10, 15.6, blade="steel", guard="gold", grip="p", w=3.0, g=4)
    d.disc(10, 16.0, 1.8, "W", g=9)
    for (x, y) in ((2.6, 13.4), (17.0, 12.6)):
        d.rect(int(x), int(y), int(x) + 1, int(y) + 1, "stone", g=10)


@pic("second_wind")
def _(d):
    # Segundo Folego: coracao envolto por correntes de vento que o reacendem
    d.heart(10, 11.0, 0.66, "heart", g=0)
    d.curve([(1.0, 13.6), (2.4, 4.0), (10.0, 2.0), (15.4, 4.4)], 1.4, "p.l", g=2)
    d.curve([(19.0, 7.4), (18.6, 16.8), (10.0, 19.0), (4.4, 16.4)], 1.4, "p.l", g=3)
    d.tri((15.2, 2.6), (17.6, 5.0), (14.2, 6.0), "p.l", g=2)
    d.tri((4.6, 18.2), (2.2, 16.0), (5.4, 14.6), "p.l", g=3)
    d.put(7, 9, "W")


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Paladino
@pic("smite")
def _(d):
    # Castigo Divino: maca sagrada descendo com explosao de luz e cruz
    burst(d, 12.4, 7.0, 8.4, 3.0, 8, "yellow", -90, (1.0, 0.7), g=0)
    d.line(3.0, 18.6, 11.0, 9.0, 2.0, "wood", g=3)
    d.disc(2.8, 18.8, 1.3, "gold", g=4)
    d.disc(12.4, 7.0, 3.8, "steel", g=5)
    for a in (-90, -30, 30, 90, 150, 210):
        r = math.radians(a)
        d.tri((12.4 + math.cos(r) * 5.6, 7.0 + math.sin(r) * 5.6), (12.4 + math.cos(r + 0.5) * 3.2, 7.0 + math.sin(r + 0.5) * 3.2),
              (12.4 + math.cos(r - 0.5) * 3.2, 7.0 + math.sin(r - 0.5) * 3.2), "steel", g=6)
    cross(d, 12.4, 7.0, 2.4, 1.2, "gold", g=8)


@pic("lay_on_hands")
def _(d):
    # Imposicao de Maos: duas maos abertas erguidas em volta da luz que cura
    d.disc(10, 6.4, 4.8, "p.d", g=0)
    d.disc(10, 6.4, 3.6, "p.l", g=1)
    plus(d, 10, 6.4, 2.8, 1.6, "W", g=2)
    for a in (-160, -120, -60, -20):
        r = math.radians(a)
        d.line(10 + math.cos(r) * 5.8, 6.4 + math.sin(r) * 5.8, 10 + math.cos(r) * 7.8, 6.4 + math.sin(r) * 7.8, 1.0,
               "yellow", g=3)
    rows = ["..#.#.#.", ".##.#.#.", ".##.#.#.", ".#######", ".#######", "########", "#######.", ".######.",
            "..####..", "..####.."]
    d.sprite(rows, {"#": "flesh"}, 0, 10, g=5)
    d.sprite([r[::-1] for r in rows], {"#": "flesh"}, 12, 10, g=6)


@pic("divine_shield")
def _(d):
    # Escudo Divino: escudo com asas brancas e aureola dourada
    wing(d, 13.6, 9.6, "white", g=0, s=0.62)
    wing(d, 6.4, 9.6, "white", g=1, flip=True, s=0.62)
    shield(d, 10, 11.4, 0.72, "p", "gold", g=3)
    cross(d, 10, 11.0, 2.6, 1.4, "W", g=6)
    chain_link(d, 10, 2.6, 4.6, 1.6, 1.1, 0, "yellow", g=8)


@pic("aura_of_devotion")
def _(d):
    # Aura de Devocao: cruz sagrada com aureola sobre aneis de luz no chao, fagulhas subindo
    d.ellipse(10, 15.6, 9.4, 3.6, "p.d", g=0)
    d.ellipse(10, 15.6, 7.0, 2.4, "p", g=1)
    d.ellipse(10, 15.6, 4.2, 1.2, "p.l", g=2)
    d.ring(10, 6.6, 5.6, 4.6, "yellow", g=4)
    cross(d, 10, 7.4, 3.6, 2.2, "gold", g=10, latin=True)
    d.put(9, 5, "W")
    for (x, y) in ((2.6, 8.6), (17.4, 8.0)):
        sparkle(d, x, y, 2.0, "p.h", g=12)


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Barbaro
@pic("rage")
def _(d):
    # Furia: rosto berrando com sobrancelhas franzidas, chamas de raiva atras e a "veia" de raiva
    flame(d, 10, 19.6, 1.02, "fire", "flame", None, g=0)
    d.disc(10, 11.6, 5.6, "flesh", g=4)
    d.line(5.8, 8.6, 9.0, 10.2, 1.2, "k")
    d.line(14.2, 8.6, 11.0, 10.2, 1.2, "k")
    d.put(7, 11, "W")
    d.put(12, 11, "W")
    d.rect(7, 13, 12, 15, "k")
    d.rect(8, 13, 11, 13, "W")
    for (x, y) in ((15, 4), (17, 4), (16, 3), (16, 5)):
        d.put(x, y, "red", g=8)


@pic("reckless_attack")
def _(d):
    # Ataque Imprudente: machado grande em pleno giro e respingos de sangue (do inimigo e dele)
    d.line(3.0, 18.4, 13.6, 5.0, 2.0, "wood", g=0)
    d.fill(lambda x, y: (x - 13.6) ** 2 + (y - 5.0) ** 2 <= 5.6 ** 2 and x - 13.6 > 0.8, "iron", g=2)
    d.fill(lambda x, y: (x - 13.6) ** 2 + (y - 5.0) ** 2 <= 4.0 ** 2 and x - 13.6 < -0.8, "iron", g=3)
    d.line(18.8, 0.6, 18.8, 9.4, 0.9, "steel.h", g=2)
    d.line(13.4, 1.0, 13.8, 9.4, 1.4, "wood", g=4)
    for (x, y, r) in ((4.6, 5.0, 1.6), (7.0, 2.4, 1.0), (16.6, 14.6, 1.8), (13.6, 17.6, 1.1)):
        drop(d, x, y, r, "blood", 1.6, shine=False, g=6)


@pic("earth_shatter")
def _(d):
    # Estilhacar a Terra: o punho desce e o chao racha em estrela com rochas voando
    d.rect(0, 12, 19, 19, "dirt", g=0)
    d.rect(0, 12, 19, 12, "grass", g=1)
    for (x1, y1) in ((0.4, 15.0), (4.0, 19.4), (10.0, 19.4), (16.0, 19.4), (19.6, 15.4)):
        d.line(10, 13.0, x1, y1, 1.0, "k")
    d.disc(10, 13.0, 1.6, "k")
    for i, (x, y, s) in enumerate(((3.0, 9.0, 1.2), (16.8, 8.4, 1.4), (5.6, 4.8, 0.8), (14.6, 3.4, 0.8))):
        d.rect(int(x - s), int(y - s), int(x + s), int(y + s), "stone", g=4 + i)
    # punho de frente descendo (nos dos dedos para o chao), manga na cor da classe
    d.line(10, 0.0, 10, 4.6, 5.4, "p", g=10)
    d.rect(6, 5, 13, 10, "flesh", g=12)
    for x in (6.5, 8.5, 10.5, 12.5):
        d.disc(x + 0.5, 10.8, 1.15, "flesh", g=12)
    for x in (8, 10, 12):
        d.rect(x, 8, x, 11, "flesh.d")
    d.ellipse(5.6, 7.0, 1.2, 2.4, "flesh", g=13)


@pic("primal_roar")
def _(d):
    # Rugido Primal: cabeca de leao de juba rugindo de frente
    burst(d, 10, 10.0, 9.6, 6.6, 11, "orange", -90, (1.0, 0.86, 0.94), g=0)
    d.tri((4.6, 4.6), (5.4, 1.4), (8.0, 4.0), "sand", g=2)
    d.tri((15.4, 4.6), (14.6, 1.4), (12.0, 4.0), "sand", g=2)
    d.disc(10, 10.6, 5.6, "sand", g=3)
    d.ellipse(10, 13.4, 3.2, 3.0, "k")
    d.ellipse(10, 14.4, 1.8, 1.2, "red", g=5)
    d.tri((7.4, 11.4), (8.6, 11.4), (8.0, 13.6), "W", g=6)
    d.tri((11.4, 11.4), (12.6, 11.4), (12.0, 13.6), "W", g=6)
    d.ellipse(10, 9.8, 1.4, 0.9, "brown.d", g=7)
    d.put(7, 8, "yellow")
    d.put(12, 8, "yellow")
    d.line(5.6, 6.6, 8.6, 7.6, 0.9, "k")
    d.line(14.4, 6.6, 11.4, 7.6, 0.9, "k")


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Monge
@pic("flurry_of_blows")
def _(d):
    # Rajada de Golpes: uma chuva de punhos vindo de frente ao mesmo tempo (golpes rapidos demais para ver)
    burst(d, 10, 10, 9.6, 4.0, 8, "p.l", -90, (1.0, 0.7), g=0)
    for i, (x, y) in enumerate(((10.0, 4.6), (4.6, 9.0), (15.4, 9.0), (6.6, 15.0), (13.4, 15.0))):
        fist_front(d, x, y, 0.78, "flesh", g=2 + i * 3)


@pic("ki_palm")
def _(d):
    # Palma de Ki: mao aberta empurrando uma esfera de ki com aneis de choque
    d.arc(13.6, 9.6, 6.6, 5.6, -55, 55, "p", g=0)
    d.arc(13.6, 9.6, 8.6, 7.6, -45, 45, "p.l", g=1)
    hand(d, -1, 2, "flesh", g=3)
    orb(d, 13.4, 9.6, 3.6, "p", g=6)
    d.disc(13.4, 9.6, 1.6, "W", g=7)


@pic("step_of_wind")
def _(d):
    # Passo do Vento: monge saltando leve, com a imagem residual atras e o redemoinho sob os pes
    figure(d, "jump", 7.0, "p.d", g=0, oy=1)
    figure(d, "jump", 12.4, "W", g=3, oy=-1)
    d.curve([(3.0, 18.0), (8.6, 14.6), (13.0, 18.0), (18.0, 15.0)], 1.4, "p.l", g=6)
    d.curve([(6.0, 19.4), (11.0, 17.0), (16.4, 19.4)], 1.2, "p", g=7)


@pic("stunning_strike")
def _(d):
    # Golpe Atordoante: punho de frente acertando, estrelas girando em cima
    burst(d, 10.0, 12.0, 9.0, 5.0, 9, "yellow", -90, (1.0, 0.75), g=0)
    fist_front(d, 10.0, 12.4, 1.0, "flesh", g=3, sleeve="p")
    d.curve([(2.0, 4.4), (10.0, 6.4), (18.0, 4.4)], 0.9, "yellow.d", g=12)
    for i, (x, y) in enumerate(((3.4, 3.4), (10.0, 2.4), (16.6, 3.4))):
        d.star(x, y, 2.5, 1.0, 5, -90, "W" if i == 1 else "yellow.l", g=8 + i)


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Cavaleiro
@pic("shield_bash")
def _(d):
    # Golpe de Escudo: escudo avancando com linhas de movimento e o choque na frente
    for i, y in enumerate((5.0, 10.0, 15.0)):
        d.line(0.6, y, 4.6, y, 1.2, "p.l", g=1 + i)
    shield(d, 10.0, 10.0, 0.86, "iron", "p", g=4)
    d.disc(10.0, 9.4, 1.6, "gold", g=6)
    burst(d, 18.0, 10.0, 3.2, 1.2, 6, "W", 0, (1.0, 0.6), g=8)


@pic("taunt")
def _(d):
    # Provocar: elmo do cavaleiro com o "!" vermelho e setas puxando os inimigos para ele
    d.disc(10, 9.6, 5.0, "steel", g=0)
    d.rect(5, 9, 14, 17, "steel", g=0)
    d.rect(5, 11, 14, 11, "k")
    d.rect(9, 11, 10, 15, "iron", g=1)
    # (sem furos de respiro: no tamanho real os pontos formavam letras - "3", "B" - em vez de um elmo)
    d.rect(9, 0, 10, 2, "red", g=3)
    d.rect(9, 4, 10, 4, "red", g=3)
    chevron(d, 1.8, 13.0, 2.4, 1.4, 1, 0, "p.l", g=6)
    chevron(d, 18.2, 13.0, 2.4, 1.4, -1, 0, "p.l", g=7)


@pic("bulwark")
def _(d):
    # Baluarte: escudo de torre cravado no chao, flechas quebrando nele
    d.rect(0, 17, 19, 19, "stone", g=0)
    d.fill(lambda x, y: 4.0 <= x <= 14.0 and 2.0 <= y <= 18.0 and not ((x < 5.0 or x > 13.0) and y < 3.0), "iron", g=1)
    d.rect(5, 3, 13, 17, "iron", g=2)
    d.rect(8, 4, 10, 15, "p", g=3)
    d.rect(6, 8, 12, 9, "p", g=3)
    for i, y in enumerate((5.0, 12.0)):
        arrow_shaft(d, 19.6, y - 1.0, 15.2, y + 0.6, "wood", "steel", None, 1.0, 1.8, 1.2, g=6 + i * 2)
    d.put(15, 7, "W")
    d.put(15, 14, "W")


@pic("iron_fortress")
def _(d):
    # Fortaleza de Ferro: muralha de FERRO rebitada com ameias e o brasao no portao
    for x in (1, 6, 11, 16):
        d.rect(x, 2, x + 2, 5, "iron", g=1)
    d.rect(1, 5, 18, 18, "iron", g=0)
    for (x, y) in ((3, 8), (16, 8), (3, 15), (16, 15)):
        d.put(x, y, "steel.h")
    d.fill(lambda x, y: abs(x - 10) <= 3.4 and y >= 11.0 and (y >= 13.0 or (x - 10) ** 2 + (y - 13.0) ** 2 <= 3.4 ** 2),
           "k")
    shield(d, 10, 8.4, 0.42, "p", None, g=4)
    d.rect(1, 18, 18, 18, "stone", g=6)


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Patrulheiro
@pic("hunters_mark")
def _(d):
    # Marca do Cacador: cabeca de cervo sob a mira vermelha
    for sx in (-1, 1):
        d.path([(10 + sx * 2.0, 6.4), (10 + sx * 4.6, 2.4), (10 + sx * 7.4, 1.4)], 1.3, "wood", g=0)
        d.line(10 + sx * 4.0, 3.4, 10 + sx * 4.6, 0.4, 1.1, "wood", g=0)
        d.line(10 + sx * 5.8, 2.0, 10 + sx * 8.6, 3.6, 1.1, "wood", g=0)
    d.ellipse(10, 11.0, 3.6, 5.2, "brown", g=2)
    d.ellipse(6.0, 7.6, 1.8, 1.0, "brown", g=3, rot=-25)
    d.ellipse(14.0, 7.6, 1.8, 1.0, "brown", g=3, rot=25)
    d.ellipse(10, 15.0, 2.0, 1.4, "sand", g=4)
    d.put(8, 10, "k")
    d.put(11, 10, "k")
    d.ring(10, 11.0, 8.2, 7.0, "red", g=6)
    for (x0, y0, x1, y1) in ((10, 1.6, 10, 4.6), (10, 17.4, 10, 19.6), (0.6, 11, 3.6, 11), (16.4, 11, 19.4, 11)):
        d.line(x0, y0, x1, y1, 1.2, "red", g=7)


@pic("piercing_shot")
def _(d):
    # Tiro Perfurante: a flecha atravessa duas tabuas, lascas voando
    for i, x in enumerate((5, 11)):
        d.rect(x, 3, x + 1, 17, "wood", g=i)
        d.rect(x, 3, x + 1, 3, "wood.l", g=i)
    arrow_shaft(d, 0.4, 10.0, 19.4, 10.0, "wood", "steel", "p.l", 1.4, 3.4, 2.0, g=4)
    for (x, y) in ((8, 7), (8, 13), (14, 6), (14, 14)):
        d.put(x, y, "wood.l", g=9)


@pic("multishot")
def _(d):
    # Disparo Multiplo: o arco solta TRES flechas em leque
    d.arc(9.0, 10.0, 8.0, 6.6, 110, 250, "wood", g=0)
    d.line(6.4, 2.6, 6.4, 17.4, 0.8, "white", g=1)
    for i, (tx, ty) in enumerate(((18.6, 2.6), (19.4, 10.0), (18.6, 17.4))):
        arrow_shaft(d, 5.4, 10.0, tx, ty, "wood", "steel", "p.l" if i == 1 else None, 1.1, 2.8, 1.6, g=3 + i * 3)


@pic("wind_arrow")
def _(d):
    # Flecha de Vento: a flecha enrolada numa espiral de vento
    arrow_shaft(d, 2.0, 18.0, 18.2, 1.8, "wood", "steel", "white", 1.3, 3.6, 2.0, g=0)
    for i, t in enumerate((0.3, 0.52, 0.74)):
        cx, cy = 2.0 + 16.2 * t, 18.0 - 16.2 * t
        chain_link(d, cx, cy, 2.2 + i * 0.4, 3.8 - i * 0.4, 1.0, 45, "p.l", g=6 + i)
    d.curve([(1.0, 11.6), (3.0, 9.0), (6.0, 8.6)], 1.0, "cloud", g=10)
    d.curve([(8.6, 19.0), (11.2, 17.0), (11.4, 14.0)], 1.0, "cloud", g=11)


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Ladino
@pic("backstab")
def _(d):
    # Punhalada pelas Costas: o alvo de COSTAS, a adaga cravada entre os ombros e o sangue
    d.disc(8.6, 5.4, 3.4, "storm", g=0)
    d.fill(lambda x, y: ((x - 8.6) / 7.2) ** 2 + ((y - 19.4) / 10.0) ** 2 <= 1 and y >= 9.0, "storm", g=0)
    d.line(8.6, 10.0, 8.6, 19.6, 0.8, "storm.d", g=0)
    d.line(17.6, 2.4, 13.6, 6.6, 1.8, "black", g=4)
    d.line(11.6, 4.8, 15.4, 8.6, 1.3, "gold", g=5)
    d.line(13.4, 6.8, 11.0, 9.4, 2.0, "steel", g=6)
    burst(d, 10.6, 10.0, 3.0, 1.0, 6, "red", 0, (1.0, 0.6), g=8)
    drop(d, 9.4, 14.0, 1.4, "blood", 1.6, shine=False, g=10)
    drop(d, 12.4, 15.6, 1.1, "blood", 1.6, shine=False, g=11)


@pic("poison_blade")
def _(d):
    # Lamina Envenenada: adaga apontando para baixo, lamina banhada em veneno pingando
    blade(d, 10.0, 6.6, 10.0, 16.4, "steel", "gold", "black", 3.0, g=0, grip_len=5.4, guard_w=3.2)
    d.recolor(lambda x, y: y > 10.6, "poison", g=0, only="steel")
    d.put(9, 8, "steel.h")
    drop(d, 6.0, 15.6, 1.8, "poison", 1.6, shine=False, g=4)
    drop(d, 14.4, 17.6, 1.4, "poison", 1.6, shine=False, g=5)
    d.disc(10.0, 19.2, 0.9, "poison", g=6)


@pic("smoke_bomb")
def _(d):
    # Bomba de Fumaca: nuvens de fumaca cinza estourando de uma bombinha
    cloud(d, 11.4, 8.4, 0.92, "storm", g=0)
    d.disc(5.0, 6.0, 3.0, "storm", g=0)
    d.disc(15.6, 15.4, 3.4, "cloud", g=1)
    d.disc(11.6, 16.6, 2.6, "cloud", g=1)
    bomb(d, 5.6, 14.6, 3.6, "black", "wood", "yellow", g=4)


@pic("shadow_dagger")
def _(d):
    # Adaga das Sombras: adaga de sombra violeta voando com rastro de fumaca escura
    for i, (x0, y0) in enumerate(((0.6, 12.6), (2.6, 18.6), (6.4, 18.6))):
        d.curve([(x0, y0), (x0 + 3.0, y0 - 1.6), (x0 + 5.0, y0 - 4.4)], 1.6, "void" if i % 2 else "black", g=i)
    blade(d, 8.6, 11.4, 18.2, 1.8, "violet", "black", "black", 3.0, g=4, grip_len=3.8, guard_w=2.6)
    d.line(10.0, 9.2, 16.0, 3.2, 0.8, "violet.h", g=8)
    sparkle(d, 17.0, 11.0, 2.2, "purple.l", g=10)


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Bardo
@pic("inspire")
def _(d):
    # Inspirar: alaude tocando e a estrela/setas subindo (aliados inspirados)
    d.line(8.6, 12.4, 15.4, 5.4, 1.6, "wood", g=0)
    d.rect(15, 3, 17, 5, "wood.d", g=1)
    d.ellipse(6.4, 14.4, 5.0, 4.4, "wood", g=2, rot=-40)
    d.disc(6.8, 14.0, 1.3, "k")
    d.line(4.8, 16.0, 15.0, 5.6, 0.6, "white", g=5)
    d.star(4.2, 4.4, 3.6, 1.5, 5, -90, "gold", g=7)
    chevron(d, 16.6, 12.0, 2.4, 1.3, 0, -1, "p.l", g=9)
    chevron(d, 16.6, 15.8, 2.4, 1.3, 0, -1, "p.l", g=10)


@pic("healing_ballad")
def _(d):
    # Balada Curativa: nota musical abracando um coracao
    d.heart(11.6, 12.6, 0.5, "heart", g=0)
    note(d, 5.4, 15.4, "p.l", 1.0, g=3, stem=11.0)
    sparkle(d, 16.6, 3.6, 2.6, "W", g=6)
    plus(d, 15.4, 17.4, 1.8, 1.0, "W", g=7)


@pic("dissonant_chord")
def _(d):
    # Acorde Dissonante: a nota RACHADA ao meio soltando ondas de choque em zigue-zague
    note(d, 6.4, 15.0, "p.l", 1.1, g=0, stem=11.0)
    d.path([(4.0, 3.0), (6.6, 7.4), (5.2, 10.4), (7.6, 14.0), (6.0, 18.4)], 1.0, None)
    move_cells(d, lambda x, y: x > 6.0 + (y - 10) * 0.1, 1, 0)
    for i, x in enumerate((13.6, 17.4)):
        d.path([(x, 3.0 + i), (x - 1.6, 6.6 + i), (x + 1.2, 9.6 + i), (x - 1.4, 13.0 + i), (x + 0.6, 16.6 + i)], 1.2,
               "red" if i == 0 else "p", g=4 + i)


@pic("lullaby")
def _(d):
    # Cancao de Ninar: lua crescente dormindo, nota e os "z" do sono
    moon(d, 8.0, 10.4, 7.2, "yellow", 3.6, -2.6, g=0)
    d.put(4, 10, "k")
    d.put(5, 10, "k")
    note(d, 14.4, 17.0, "p.l", 0.8, g=3, stem=8.0)
    for (x, y, s) in ((13.0, 2.0, 1.0), (16.6, 5.6, 0.7)):
        d.path([(x, y), (x + 3 * s, y), (x, y + 3 * s), (x + 3 * s, y + 3 * s)], 1.0, "W", g=6)


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Artifice
@pic("arcane_bomb")
def _(d):
    # Bomba Arcana: bomba de ferro gravada com o circulo runico aceso e o pavio faiscando
    bomb(d, 9.0, 11.6, 7.0, "black", "wood", "p.l", g=0)
    d.ring(9.0, 11.6, 4.6, 3.6, "p", g=6)
    d.path([(9.0, 8.4), (11.8, 13.2), (6.2, 13.2), (9.0, 8.4)], 1.0, "p.l", g=7)


@pic("gear_turret")
def _(d):
    # Torreta de Engrenagem: engrenagem de base e o canhao-besta disparando um virote
    gear(d, 8.6, 14.6, 4.2, 8, "copper", None, g=0)
    d.rect(4, 6, 12, 10, "iron", g=2)
    d.rect(5, 6, 11, 6, "iron.l", g=2)
    d.rect(12, 7, 16, 9, "steel", g=3)
    d.disc(8.0, 8.0, 1.3, "p.l", g=5)
    arrow_shaft(d, 14.4, 3.4, 19.8, 3.4, "wood", "steel", None, 1.0, 2.2, 1.3, g=7)
    burst(d, 17.4, 8.0, 2.4, 0.9, 5, "p.l", 0, (1.0, 0.6), g=9)


@pic("infuse_item")
def _(d):
    # Imbuir Item: o cristal derrama energia numa espada deitada sobre a bigorna
    d.poly([(3.0, 13.0), (17.0, 13.0), (15.0, 15.4), (5.0, 15.4)], "iron", g=0)
    d.rect(7, 15, 12, 17, "iron", g=0)
    d.rect(5, 18, 14, 19, "iron.d", g=1)
    sword(d, 1.4, 11.4, 18.6, 11.4, blade="steel", guard="gold", grip="wood", w=2.2, g=3)
    for x in (9, 12, 15):
        d.put(x, 11, "p.l")
    crystal(d, 10.0, 3.6, 3.4, 1.8, "p", g=8)
    dashed(d, [(10.0, 7.4), (10.0, 10.0)], "p.l", 1.2, 1, 1, g=10)
    sparkle(d, 4.4, 6.6, 2.0, "W", g=12)
    sparkle(d, 16.0, 6.6, 2.0, "p.l", g=12)


@pic("flamethrower")
def _(d):
    # Lanca-Chamas: tanque de cobre com bocal soltando um cone de fogo
    d.rect(1, 8, 6, 17, "copper", g=0)
    d.rect(1, 8, 6, 8, "copper.l", g=0)
    d.rect(6, 11, 9, 13, "iron", g=1)
    d.line(3.6, 7.6, 3.6, 5.6, 1.4, "iron", g=2)
    d.poly([(9.0, 12.0), (19.6, 5.0), (19.6, 19.0)], "fire", g=4)
    d.poly([(9.4, 12.0), (18.0, 8.4), (18.0, 15.6)], "flame", g=5)
    d.poly([(9.6, 12.0), (14.4, 10.6), (14.4, 13.4)], "W", g=6)


# ---------------------------------------------------------------------------------------------- v0.9 ORIGENS: Cacador
@pic("beast_companion")
def _(d):
    # Companheiro Fera: lobo fiel de perfil com coleira e a pegada da dupla
    wolf_head(d, 8.0, 9.6, 1.25, "iron", g=0, collar="red")
    d.put(4, 15, "gold")
    paw(d, 15.4, 15.6, 0.7, "p.l", g=8)


@pic("bear_trap")
def _(d):
    # Armadilha de Urso: argola de aco aberta no chao com dentes para dentro, prato no meio e a corrente
    d.ellipse(10, 11.6, 9.0, 5.6, "steel", g=0)
    d.ellipse(10, 11.6, 6.8, 3.6, None)
    for i in range(10):
        a = math.radians(i * 36 + 18)
        bx, by = 10 + math.cos(a) * 7.0, 11.6 + math.sin(a) * 3.8
        tx, ty = 10 + math.cos(a) * 4.6, 11.6 + math.sin(a) * 2.2
        px_, py_ = -math.sin(a) * 1.0, math.cos(a) * 0.6
        d.tri((bx + px_, by + py_), (bx - px_, by - py_), (tx, ty), "white", g=2)
    d.ellipse(10, 11.6, 2.4, 1.3, "copper", g=4)
    d.rect(0, 10, 1, 13, "iron", g=5)
    d.rect(18, 10, 19, 13, "iron", g=5)
    for i, x in enumerate((13.0, 15.0, 17.0)):
        chain_link(d, x, 18.0, 1.4, 1.0, 0.7, 0, "iron", g=7 + i)


@pic("crossbow_bolt")
def _(d):
    # Virote de Besta: besta vista de cima com o virote na trilha
    d.line(10.0, 19.4, 10.0, 7.0, 2.6, "wood", g=0)
    d.arc(10.0, 13.4, 9.0, 7.8, 205, 335, "steel", g=1)
    d.line(2.0, 9.6, 10.0, 12.6, 0.8, "white", g=2)
    d.line(18.0, 9.6, 10.0, 12.6, 0.8, "white", g=2)
    arrow_shaft(d, 10.0, 13.6, 10.0, 0.8, "iron", "p.l", None, 1.2, 3.0, 1.8, g=4)
    d.rect(9, 16, 10, 17, "gold", g=7)


@pic("pack_tactics")
def _(d):
    # Taticas de Matilha: tres pegadas cercando a presa marcada
    d.ring(10, 10.6, 3.4, 2.2, "red", g=0)
    d.disc(10, 10.6, 1.0, "red", g=1)
    paw(d, 10, 3.6, 0.7, "bone", g=3)
    paw(d, 3.6, 15.6, 0.7, "bone", g=5)
    paw(d, 16.4, 15.6, 0.7, "bone", g=7)
    for (x0, y0, x1, y1) in ((10, 6.6, 10, 7.4), (5.6, 13.6, 7.4, 12.6), (14.4, 13.6, 12.6, 12.6)):
        d.line(x0, y0, x1, y1, 0.9, "W", g=9)


def render16(fn, elem="arcane", outline=None):
    p = Pic(16, 16)
    fn(p)
    ec = rgb(ECOLOR.get(elem, "B98CFF"))
    return p.render(ramps_for(ec, elem), outline or rgb("140F1C"))


def element_symbol(e, d):
    """Simbolo branco/claro do elemento numa tela 16x16 (centro 8,8)."""
    W = "white"
    if e == "arcane":
        d.star(8, 8, 5.6, 1.7, 4, -90, W)
        d.star(8, 8, 3.0, 1.0, 4, -45, "p", g=1)
    elif e == "fire":
        flame(d, 8, 13.4, 0.6, "white", "yellow.l", None)
    elif e == "water":
        drop(d, 8, 7.6, 3.6, "white", 1.75)
    elif e == "earth":
        d.poly([(2.6, 12.6), (7.0, 4.0), (9.0, 7.6), (10.6, 5.6), (13.6, 12.6)], W)
        d.poly([(6.0, 6.0), (7.0, 4.0), (8.0, 6.0), (7.0, 6.6)], "p.l", g=1)
    elif e == "wind":
        d.line(3.0, 6.0, 10.0, 6.0, 1.6, W)
        d.arc(10.0, 4.0, 2.8, 1.2, 270, 450, W)
        d.line(4.6, 10.6, 12.0, 10.6, 1.6, W, g=1)
        d.arc(12.0, 8.6, 2.8, 1.2, 270, 450, W, g=1)
    elif e == "light":
        for i in range(8):
            a = math.radians(i * 45)
            d.line(8 + 2.6 * math.cos(a), 8 + 2.6 * math.sin(a), 8 + 5.6 * math.cos(a), 8 + 5.6 * math.sin(a),
                   1.5 if i % 2 == 0 else 1.1, W)
        d.disc(8, 8, 2.4, "yellow.l", g=1)
    elif e == "shadow":
        crescent(d, 8, 8, 5.4, 2.4, -1.8, 4.6, W)
    elif e == "magma":
        d.poly([(2.4, 13.0), (6.0, 6.4), (10.0, 6.4), (13.6, 13.0)], W)
        d.poly([(6.0, 6.4), (10.0, 6.4), (9.0, 8.6), (8.0, 7.6), (7.0, 9.0)], "lava.l", g=1)
        d.rect(7, 2, 8, 4, "lava.l", g=2)
    elif e == "steam":
        d.fill(lambda x, y: ((x - 8) / 5.6) ** 2 + ((y - 10.4) / 3.4) ** 2 <= 1 and y >= 10.4, W)
        for i, x in enumerate((5.0, 8.0, 11.0)):
            pts = [(x + 1.0 * math.sin(t * math.pi * 2.0), 9.0 - t * 6.4) for t in [k / 10 for k in range(11)]]
            d.path(pts, 1.2, W, g=2 + i)
    elif e == "lightning":
        bolt(d, 8, 8, 0.62, W)
    elif e == "sun":
        for i in range(8):
            a = math.radians(i * 45 + 22.5)
            d.line(8 + 4.0 * math.cos(a), 8 + 4.0 * math.sin(a), 8 + 6.0 * math.cos(a), 8 + 6.0 * math.sin(a), 1.4, W)
        d.disc(8, 8, 3.4, "yellow.l", g=1)
    elif e == "hellfire":
        flame(d, 8, 13.6, 0.62, "white", "red.l", None)
        d.tri((3.6, 6.0), (2.6, 2.0), (5.4, 4.6), W, g=3)
        d.tri((12.4, 6.0), (13.4, 2.0), (10.6, 4.6), W, g=3)
    elif e == "nature":
        leaf(d, 8, 8, 0.72, -45, W, vein=False)
        d.line(4.6, 11.4, 11.0, 5.0, 1.0, "leaf.l", g=2)
    elif e == "ice":
        # floco LIMPO de 6 bracos (o snowflake() com galhos virava uma bolha em 16 px)
        for a in (90, 30, 150):
            r = math.radians(a)
            d.line(8 - 5.4 * math.cos(r), 8 - 5.4 * math.sin(r), 8 + 5.4 * math.cos(r), 8 + 5.4 * math.sin(r), 1.3,
                   "ice.l")
        # pontas brancas (cristal) e centro branco; azul-gelo para nao confundir com a estrela branca da Luz
        for a in range(30, 390, 60):
            r = math.radians(a)
            d.put(8 + 4.9 * math.cos(r), 8 + 4.9 * math.sin(r), "W")
        d.disc(8, 8, 1.2, "W", g=1)
    elif e == "purity":
        plus(d, 8, 8, 5.0, 2.6, W)
        d.disc(8, 8, 1.0, "aqua.l", g=1)
    elif e == "abyss":
        spiral(d, 8, 8, 0.4, 5.2, 1.6, 1.4, W, 180)
    elif e == "sand":
        hourglass(d, 8, 8, 0.66, frame="white", glass="sand.l", sand="sand")
    elif e == "crystal":
        crystal(d, 8, 8, 5.8, 3.0, "white")
    elif e == "gravity":
        d.arrow(8, 2.0, 8, 10.4, 1.8, 3.0, W)
        d.rect(3, 12, 12, 13, W, g=2)
    elif e == "astral":
        d.star(8, 8.4, 6.0, 2.4, 5, -90, W)
    elif e == "void":
        d.ring(8, 8, 5.4, 3.6, W)
        d.disc(8, 8, 1.2, "violet.l", g=1)
    elif e == "twilight":
        d.disc(8, 8, 5.2, W)
        d.recolor(lambda x, y: x > 8, "shadow.d", g=1)


def make_element_icons():
    out = []
    S = 16
    for e in ELEMENTS:
        ec = rgb(ECOLOR[e])
        px = [[(0, 0, 0, 0)] * S for _ in range(S)]
        pa, pb = PARENTS.get(e, (e, e))
        ca, cb = elem_light(rgb(ECOLOR[pa])), elem_light(rgb(ECOLOR[pb]))
        outline = shade(ec, 0.09, 1.0, 0.1)
        for y in range(S):
            for x in range(S):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8
                dd = math.hypot(dx, dy)
                if dd > 7.7:
                    continue
                if dd > 6.8:
                    px[y][x] = outline
                elif dd > 5.6:
                    # aro: fusoes = metade esquerda/direita na cor de cada pai
                    base = ca if (dx - dy * 0.35) < 0 else cb
                    lit = -(dx + dy) / (dd * 1.414 or 1)
                    v = 0.78 + 0.22 * lit
                    h, s, _ = to_hsv(base)
                    px[y][x] = from_hsv(h, s * 0.85, clamp(v))
                else:
                    t = clamp(0.75 - (dx + dy) / 16.0 - dd / 14.0)
                    tq = clamp(math.floor(t * 4 + 0.5) / 4)
                    px[y][x] = fill_color(ec, 0.15 + 0.55 * tq, "form")
        sym = Pic(16, 16)
        element_symbol(e, sym)
        img = sym.render(ramps_for(ec, e), shade(ec, 0.12, 1.0, 0.1))
        paste(px, img, 0, 0)
        # brilho
        for (x, y, a) in ((4, 3, 0.5), (3, 4, 0.5), (5, 3, 0.25), (3, 5, 0.25)):
            if px[y][x][3]:
                px[y][x] = blend(px[y][x], rgb("FFFFFF"), a)
        out.append((e, px))
    return out


GREY = {"o": rgb("3A3A44"), "d": rgb("9A9AA6"), "m": rgb("C8C8D0"), "l": rgb("E8E8EE"), "h": rgb("FFFFFF")}


def make_kind_icons():
    """Silhuetas dos 6 tipos em tons de cinza claros (para tingir no Java com setColor).
    Simbolo GRAVADO (escuro com realce embaixo/direita) para seguir legivel depois de tingido."""
    out = []
    S = 16
    for k in KINDS:
        geo = frame_geometry(k, S)
        px = [[(0, 0, 0, 0)] * S for _ in range(S)]
        for y in range(S):
            for x in range(S):
                gp = geo[y][x]
                if gp is None:
                    continue
                dist, nx, ny = gp
                lit = -(nx + ny) / math.sqrt(2)
                if dist <= 1.0:
                    px[y][x] = GREY["o"]
                elif dist <= 2.4:
                    px[y][x] = GREY["h"] if lit > 0.5 else GREY["l"] if lit > -0.2 else GREY["d"]
                else:
                    px[y][x] = GREY["m"]
        p = Pic(16, 16)
        if k == "form":
            p.ring(8, 8, 3.8, 2.4, "W")
            p.disc(8, 8, 1.0, "W")
        elif k == "effect":
            p.poly([(8, 4.2), (11.8, 8), (8, 11.8), (4.2, 8)], "W")
        elif k == "augment":
            plus(p, 8, 8, 3.8, 2.0, "W")
        elif k == "trigger":
            p.rect(7, 6, 8, 10, "W")
            p.rect(7, 12, 8, 13, "W")
        elif k == "link":
            chain_link(p, 6.0, 8, 3.2, 2.2, 1.0, 0, "W")
            chain_link(p, 10.0, 8, 3.2, 2.2, 1.0, 0, "W")
        elif k == "law":
            p.poly([(4.4, 10.6), (4.4, 5.4), (6.2, 7.6), (8, 4.6), (9.8, 7.6), (11.6, 5.4), (11.6, 10.6)], "W")
        sym = [(x, y) for y in range(S) for x in range(S) if p.m[y][x] is not None]
        sset = set(sym)
        for (x, y) in sym:
            if px[y][x][3]:
                px[y][x] = GREY["o"]
        for (x, y) in sym:
            for (ox, oy) in ((1, 1), (0, 1), (1, 0)):
                X, Y = x + ox, y + oy
                if (X, Y) not in sset and 0 <= X < S and 0 <= Y < S and geo[Y][X] and geo[Y][X][0] > 2.4:
                    px[Y][X] = GREY["h"]
        out.append((k, px))
    return out


def tab_status(d):
    d.disc(8, 4.6, 3.2, "flesh")
    d.rect(5, 2, 10, 3, "brown", g=1)
    d.fill(lambda x, y: ((x - 8) / 6.4) ** 2 + ((y - 15) / 6.6) ** 2 <= 1 and y <= 15.6, "blue", g=2)
    d.heart(12.6, 11.6, 0.42, "heart", g=4)


def tab_paths(d):
    pts = [(3.0, 13.0), (8.0, 9.0), (4.0, 4.0), (13.0, 5.0), (12.6, 12.6)]
    for (a, b) in ((0, 1), (1, 2), (1, 3), (1, 4)):
        d.line(pts[a][0], pts[a][1], pts[b][0], pts[b][1], 1.2, "gold.d")
    for i, (x, y) in enumerate(pts):
        d.disc(x, y, 2.2 if i == 1 else 1.7, ["green", "yellow", "red", "sky", "purple"][i], g=2 + i)


def tab_grimoire(d):
    d.rect(2, 2, 13, 14, "purple")
    d.rect(4, 12, 13, 14, "paper", g=1)
    d.rect(2, 2, 3, 14, "purple.d", g=2)
    d.ring(8.6, 7.0, 3.6, 2.4, "gold", g=3)
    d.star(8.6, 7.0, 2.0, 0.8, 4, -90, "gold.l", g=4)


def tab_loadouts(d):
    d.ring(8, 8, 7.0, 5.4, "iron")
    for i, m in enumerate(("fire", "water", "leaf", "yellow")):
        a = math.radians(-90 + i * 90)
        d.disc(8 + 6.2 * math.cos(a), 8 + 6.2 * math.sin(a), 2.0, m, g=2 + i)
    d.disc(8, 8, 2.2, "violet", g=8)


def tab_glyphs(d):
    d.fill(lambda x, y: 2 <= x <= 14 and 1 <= y <= 15 and not ((x < 4 or x > 12) and (y < 3 or y > 13)), "stone")
    d.path([(8, 3.6), (11.0, 8.0), (8, 12.6), (5.0, 8.0), (8, 3.6)], 1.2, "violet.l", g=1)
    d.disc(8, 8, 1.2, "cyan.l", g=2)


def tab_journal(d):
    d.rect(2, 3, 11, 15, "paper")
    for y in (6, 9, 12):
        d.rect(4, y, 9, y, "paper.d")
    d.line(14.6, 0.6, 7.6, 10.6, 1.4, "white", g=2)
    d.tri((14.6, 0.4), (10.6, 4.0), (13.0, 5.4), "white", g=3)
    d.line(7.6, 10.6, 6.6, 12.4, 1.0, "ink", g=4)


def tab_guild(d):
    d.rect(7, 0, 8, 15, "wood")
    d.poly([(1.6, 2.0), (14.4, 2.0), (14.4, 11.0), (8.0, 14.0), (1.6, 11.0)], "red", g=1)
    shield(d, 8, 7.2, 0.42, "gold", None, g=3)
    d.rect(1, 1, 14, 2, "gold", g=4)


def tab_bestiary(d):
    d.fill(lambda x, y: ((x - 8) / 7.0) ** 2 + ((y - 11.0) / 5.6) ** 2 <= 1 and y <= 15.0 or
           ((x - 8) / 4.4) ** 2 + ((y - 6.4) / 4.6) ** 2 <= 1, "green")
    d.ellipse(6.0, 9.6, 1.2, 1.6, "white", g=1)
    d.ellipse(10.0, 9.6, 1.2, 1.6, "white", g=1)
    d.rect(6, 10, 6, 10, "k")
    d.rect(10, 10, 10, 10, "k")
    d.rect(7, 13, 9, 13, "green.d")
    d.disc(5.0, 5.6, 1.0, "green.h", g=2)


def tab_trials(d):
    d.line(2.4, 13.6, 13.0, 3.0, 1.8, "steel")
    d.line(13.6, 13.6, 3.0, 3.0, 1.8, "steel", g=1)
    d.tri((14.6, 1.4), (11.6, 2.6), (13.4, 4.4), "steel", g=0)
    d.tri((1.4, 1.4), (4.4, 2.6), (2.6, 4.4), "steel", g=1)
    d.line(1.6, 10.6, 5.4, 14.4, 1.6, "gold", g=2)
    d.line(14.4, 10.6, 10.6, 14.4, 1.6, "gold", g=3)
    d.rect(1, 14, 2, 15, "red", g=4)
    d.rect(13, 14, 14, 15, "red", g=5)


def tab_legends(d):
    d.star(8, 8.6, 7.4, 3.2, 5, -90, "gold")
    d.star(8, 8.6, 3.6, 1.6, 5, -90, "yellow.l", g=1)
    d.put(7, 4, "W")


def tab_reputation(d):
    # v0.8: medalha de honra (fitas vermelha e azul + disco dourado com brasao) - "como as faccoes te veem"
    d.poly([(3.0, 0.0), (6.6, 0.0), (9.2, 6.6), (5.8, 6.6)], "red")
    d.poly([(9.4, 0.0), (13.0, 0.0), (10.2, 6.6), (6.8, 6.6)], "blue", g=1)
    d.disc(8, 10.6, 5.2, "gold", g=2)
    d.ring(8, 10.6, 3.9, 3.0, "gold.d", g=3)
    shield(d, 8, 10.8, 0.3, "silver", None, g=4)
    d.put(5, 8, "W")


TABS = [("status", tab_status), ("paths", tab_paths), ("grimoire", tab_grimoire), ("loadouts", tab_loadouts),
        ("glyphs", tab_glyphs), ("journal", tab_journal), ("guild", tab_guild), ("bestiary", tab_bestiary),
        ("trials", tab_trials), ("legends", tab_legends), ("reputation", tab_reputation)]


def make_tab_icons():
    return [(n, render16(fn)) for n, fn in TABS]


# =====================================================================================================  v0.8: EFEITOS
# textures/mob_effect/<id>.png 18x18 - estilo vanilla: desenho solto (sem fundo), contorno escuro, legivel pequeno.
def eff_burning(d):
    # Em Chamas: chama grande (laranja/amarela/miolo branco) e brasas soltas
    flame(d, 9.0, 17.6, 0.86, "fire", "flame", "W")
    for (x, y) in ((2, 6), (15, 3), (2, 12), (16, 10)):
        d.put(x, y, "ember.l")


def eff_chilled(d):
    # Gelado: floco de neve azul-gelo com pontas cristalinas (Congelado = o bloco de gelo do nucleo)
    for a in (90, 30, 150):
        r = math.radians(a)
        d.line(9 - 7.6 * math.cos(r), 9 - 7.6 * math.sin(r), 9 + 7.6 * math.cos(r), 9 + 7.6 * math.sin(r), 1.7, "ice")
    for a in range(30, 390, 60):
        r = math.radians(a)
        bx, by = 9 + 4.6 * math.cos(r), 9 + 4.6 * math.sin(r)
        for b in (-1, 1):
            aa = r + b * math.radians(55)
            d.line(bx, by, bx + math.cos(aa) * 2.4, by + math.sin(aa) * 2.4, 1.1, "ice")
    d.disc(9, 9, 2.0, "glass", g=1)
    d.put(8, 8, "W")


def eff_shocked(d):
    # Eletrizado: faisca branca no centro e arcos ciano em zigue-zague saltando para fora
    for i, ang in enumerate((-60, 30, 120, 210)):
        a = math.radians(ang)
        ux, uy = math.cos(a), math.sin(a)
        px_, py_ = -uy, ux
        pts = [(9 + ux * r + px_ * o, 9 + uy * r + py_ * o) for r, o in ((2.0, 0), (4.2, 1.4), (5.8, -1.2), (8.4, 0.8))]
        d.path(pts, 1.3, "cyan", g=1 + i)
    d.star(9, 9, 4.6, 1.6, 4, -45, "yellow", g=6)
    d.disc(9, 9, 1.4, "W")


def eff_bleeding(d):
    # Sangrando: um talho vermelho na diagonal pingando gotas
    cut_line(d, 1.0, 2.0, 16.6, 8.0, 3.0, "blood", "red.l", g=0)
    drop(d, 5.6, 12.4, 2.4, "blood", 1.7, g=3)
    drop(d, 12.0, 14.0, 2.0, "blood", 1.7, g=4)
    d.disc(16.0, 11.0, 1.1, "blood", g=5)


def eff_petrified(d):
    # Petrificacao: um corpo de PEDRA cinza com rachaduras, os pes ja presos numa base de rocha
    vfig(d, 9, 15.6, 14.4, "stone", g=0, arms="down")
    d.path([(8.0, 4.0), (9.6, 6.6), (8.4, 9.0), (10.0, 11.0)], 0.8, "stone.d", g=0)
    d.put(11, 5, "stone.d")
    d.poly([(1.6, 18.0), (3.0, 14.6), (15.0, 14.6), (16.4, 18.0)], "stone.d", g=2)
    d.rect(2, 15, 3, 15, "stone.l", g=3)
    for (x, y) in ((1, 11), (16, 10)):
        d.put(x, y, "stone", g=4)


EFFECTS_V08 = [("burning", eff_burning, "fire"), ("chilled", eff_chilled, "ice"), ("shocked", eff_shocked, "lightning"),
               ("bleeding", eff_bleeding, "fire"), ("petrified", eff_petrified, "earth")]


# PROPOSTAS para os efeitos ANTIGOS do nucleo (hoje bolinhas lisas de tools/gen_core_assets.py). Nao sao do trabalho
# "art" da v0.8: so vao para a previa (icons_preview_v08_core_effects.png). Gravar em mob_effect/ SO com a flag
# explicita --core-effects (decisao do coordenador), para combinarem com os novos sobre a cabeca dos mobs.
def eff_soaked(d):
    # Molhado: tres gotas d'agua (uma grande, duas pequenas) pingando
    drop(d, 6.6, 9.0, 4.6, "water", 1.7)
    drop(d, 14.8, 4.0, 2.2, "water", 1.7, shine=False)
    drop(d, 14.6, 13.0, 2.0, "aqua", 1.7, shine=False)


def eff_frozen(d):
    # Congelado: bloco de gelo translucido com um floco preso dentro
    cube(d, 9.0, 9.4, 1.02, "ice")
    for a in (90, 30, 150):
        r = math.radians(a)
        d.line(9 - 3.0 * math.cos(r), 10.6 - 3.0 * math.sin(r), 9 + 3.0 * math.cos(r), 10.6 + 3.0 * math.sin(r), 1.0,
               "W", g=5)


def eff_stunned(d):
    # Atordoado: estrelinhas girando numa orbita sobre a cabeca
    chain_link(d, 9, 9, 8.0, 3.4, 1.0, 0, "yellow.d", g=0)
    for i, (x, y, r) in enumerate(((3.0, 8.0, 3.0), (11.0, 5.6, 2.6), (14.0, 12.0, 3.2))):
        d.star(x, y, r, r * 0.45, 5, -90, "yellow", g=2 + i)


def eff_rooted(d):
    # Enraizado: raizes verdes e marrons enrolando uma bota presa no chao
    boot(d, 8.4, 8.0, "brown", g=0)
    for i, y in enumerate((5.0, 9.0, 13.0)):
        d.curve([(1.0, y + 2.0), (9.0, y - 1.6), (17.0, y + 1.0)], 1.4, "moss" if i % 2 else "wood", g=4 + i)
    leaf(d, 15.0, 3.0, 0.3, -30, "leaf", g=9)


def eff_silenced(d):
    # Silenciado: balao de fala riscado por um X vermelho
    d.ellipse(9, 7.6, 7.8, 5.6, "white", g=0)
    d.tri((4.0, 11.0), (8.0, 12.0), (2.6, 16.6), "white", g=0)
    d.line(5.4, 4.4, 12.6, 10.8, 2.0, "red", g=2)
    d.line(12.6, 4.4, 5.4, 10.8, 2.0, "red", g=2)


def eff_corrupted(d):
    # Corrompido: gosma violeta-negra com um olho e gotas escorrendo
    d.disc(9, 7.6, 6.4, "void", g=0)
    for x, ln in ((4.6, 5.0), (9.0, 7.6), (13.4, 4.0)):
        d.line(x, 10.0, x, 10.0 + ln, 2.0, "void", g=0)
        d.disc(x, 10.0 + ln, 1.3, "void", g=0)
    eye(d, 9, 7.0, 0.5, "magenta.l", "yellow", "k", g=3)


def eff_exposed(d):
    # Exposto: escudo RACHADO com uma mira vermelha por cima
    shield(d, 8.4, 9.4, 0.92, "steel", None)
    d.path([(8.0, 1.6), (9.6, 5.6), (7.4, 8.6), (9.4, 12.6)], 1.0, "k", g=2)
    d.ring(13.0, 12.6, 4.4, 3.2, "red", g=4)
    d.disc(13.0, 12.6, 1.0, "red", g=5)


def eff_magic_exhaustion(d):
    # Exaustao Magica: gota de mana VAZIA (so o contorno e um resto no fundo) e um "z"
    drop(d, 8.0, 9.6, 6.0, "mana.d", 1.7, shine=False)
    d.fill(lambda x, y: (x - 8.0) ** 2 + (y - 11.7) ** 2 <= 4.4 ** 2 and y <= 13.6, "ink", g=2)
    d.path([(13.4, 1.6), (16.6, 1.6), (13.4, 4.6), (16.6, 4.6)], 1.0, "W", g=4)


CORE_EFFECTS_PROPOSED = [("soaked", eff_soaked, "water"), ("frozen", eff_frozen, "ice"), ("stunned", eff_stunned, "light"),
                         ("rooted", eff_rooted, "nature"), ("silenced", eff_silenced, "arcane"),
                         ("corrupted", eff_corrupted, "void"), ("exposed", eff_exposed, "arcane"),
                         ("magic_exhaustion", eff_magic_exhaustion, "arcane")]


def make_effect_icons(table=None):
    out = []
    for name, fn, elem in (table or EFFECTS_V08):
        p = Pic(18, 18)
        fn(p)
        out.append((name, p.render(ramps_for(rgb(ECOLOR[elem]), elem), rgb("1A1220"))))
    return out


# =====================================================================================================  v0.8: FACCOES
# textures/gui/faction/<id>.png 32x32 - brasao (escudo heraldico) com aro de metal, campo escuro no tom da faccao
# e o emblema claro na cor da faccao (mesma receita das molduras dos glifos: aro + gradiente + pictograma).
def crest_guild(d):
    # Guilda dos Aventureiros: espada atras de um escudo dourado
    sword(d, 2.4, 19.6, 19.6, 2.4, blade="steel", guard="gold", grip="wood", w=2.6)
    shield(d, 11.0, 12.0, 0.9, "p", "q")
    d.star(11.0, 11.0, 2.8, 1.1, 4, -90, "W", g=5)


def crest_kingdom(d):
    # Reino de Aldoria: coroa azul com gemas
    crown(d, 11.0, 13.4, "p", "red", s=1.02)
    d.ellipse(11.0, 19.8, 7.0, 1.1, "q", g=8)
    sparkle(d, 11.0, 3.0, 2.0, "W", g=10)


def crest_academy(d):
    # Academia Arcana: livro aberto e o olho do conhecimento acima
    d.poly([(1.6, 13.0), (11.0, 15.0), (20.4, 13.0), (20.4, 19.6), (11.0, 21.0), (1.6, 19.6)], "p", g=0)
    d.poly([(2.6, 12.0), (10.6, 14.0), (10.6, 19.6), (2.6, 17.8)], "paper", g=1)
    d.poly([(19.4, 12.0), (11.4, 14.0), (11.4, 19.6), (19.4, 17.8)], "paper", g=2)
    for y in (14.6, 16.6):
        d.line(4.0, y, 9.0, y + 1.0, 0.8, "paper.d", g=1)
        d.line(18.0, y, 13.0, y + 1.0, 0.8, "paper.d", g=2)
    eye(d, 11.0, 6.4, 0.8, "white", "p", "k", g=4)
    for (x0, y0, x1, y1) in ((11, 0.4, 11, 1.8), (3.0, 2.0, 4.2, 3.0), (19.0, 2.0, 17.8, 3.0)):
        d.line(x0, y0, x1, y1, 1.0, "p.l", g=8)


def crest_church(d):
    # Igreja da Aurora: sol dourado de doze raios com uma estrela branca no centro
    sun(d, 11.0, 11.0, 5.0, 12, 3.6, "p", "yellow")
    d.star(11.0, 11.0, 3.4, 1.2, 4, -90, "W", g=4)


def crest_underworld(d):
    # Submundo: adaga atravessada atras de uma mascara vermelha (dominó)
    sword(d, 3.0, 19.0, 19.4, 2.6, blade="steel", guard="black", grip="black", w=2.2)
    d.fill(lambda x, y: (((x - 7.0) / 5.4) ** 2 + ((y - 11.0) / 3.6) ** 2 <= 1 or
                         ((x - 15.0) / 5.4) ** 2 + ((y - 11.0) / 3.6) ** 2 <= 1 or
                         (abs(x - 11) <= 2.2 and 8.6 <= y <= 11.6)), "p", g=4)
    d.ellipse(7.0, 11.0, 2.0, 1.2, "k")
    d.ellipse(15.0, 11.0, 2.0, 1.2, "k")
    d.curve([(1.6, 10.0), (0.6, 14.0), (2.6, 17.0)], 1.0, "p.d", g=6)


def crest_wildfolk(d):
    # Povo Selvagem: arvore frondosa com raizes e uma folha nova
    d.rect(10, 10, 12, 19, "wood", g=0)
    d.curve([(10.0, 18.0), (7.0, 19.4), (4.6, 20.4)], 1.2, "wood", g=0)
    d.curve([(12.0, 18.0), (15.0, 19.4), (17.4, 20.4)], 1.2, "wood", g=0)
    d.line(11.0, 12.0, 7.0, 9.0, 1.2, "wood", g=0)
    d.line(11.0, 11.0, 15.0, 8.6, 1.2, "wood", g=0)
    for i, (x, y, r) in enumerate(((5.6, 9.0, 3.4), (16.4, 8.6, 3.4), (11.0, 5.4, 4.4), (7.4, 4.6, 2.8),
                                   (14.8, 4.0, 2.8))):
        d.disc(x, y, r, "p", g=2 + i)
    leaf(d, 18.0, 15.6, 0.34, -30, "leaf", g=9)


def crest_horde(d):
    # A Horda: cranio de goblin (orelhas pontudas, presas) sobre dois machados cruzados
    for sx in (-1, 1):
        x0, x1 = 11 - sx * 8.0, 11 + sx * 7.0
        d.line(x0, 20.4, x1, 1.6, 1.6, "p", g=0 if sx < 0 else 1)
        # lamina em meia-lua virada para FORA, presa no alto do cabo
        d.fill(lambda x, y, x1=x1, sx=sx: (x - x1) ** 2 + (y - 4.4) ** 2 <= 4.2 ** 2 and (x - x1) * sx >= 0.6,
               "iron", g=2 if sx < 0 else 3)
        d.line(x1 + sx * 4.0, 1.4, x1 + sx * 4.0, 7.4, 0.9, "steel.h", g=2 if sx < 0 else 3)
    d.tri((5.6, 9.4), (2.4, 7.6), (6.4, 12.0), "moss.l", g=4)
    d.tri((16.4, 9.4), (19.6, 7.6), (15.6, 12.0), "moss.l", g=4)
    skull(d, 11.0, 12.0, 0.84, "bone", g=4)
    d.tri((8.0, 16.6), (9.4, 16.6), (8.4, 19.0), "W", g=6)
    d.tri((14.0, 16.6), (12.6, 16.6), (13.6, 19.0), "W", g=6)


FACTIONS_V08 = [("guild", "E8B84A", "FFE6A0", crest_guild), ("kingdom", "3F6FD8", "FFD878", crest_kingdom),
                ("academy", "B98CFF", "D8DEF0", crest_academy), ("church", "FFE9A8", "FFF4C8", crest_church),
                ("underworld", "8A2B3A", "8E98A8", crest_underworld), ("wildfolk", "4FBF4A", "C89A5A", crest_wildfolk),
                ("horde", "A0522D", "9A8A78", crest_horde)]


def draw_crest(fc, metal):
    """Brasao 32x32: aro de metal (cor da faccao + metal), campo em gradiente escuro do tom da faccao."""
    S = 32
    geo = frame_geometry("crest", S)
    out = [[(0, 0, 0, 0)] * S for _ in range(S)]
    fillmask = [[False] * S for _ in range(S)]
    base = mix(elem_light(fc), metal, 0.5)
    h, s, v = to_hsv(base)
    rim = [from_hsv(hue_toward(h, 0.75, 0.05), min(1, s * 1.15 + 0.05), 0.42), from_hsv(h, s, 0.62),
           from_hsv(h, s * 0.9, 0.82), from_hsv(h, s * 0.65, 0.97), from_hsv(h, s * 0.25, 1.0)]
    outline = shade(fc, 0.09, 1.0, 0.1)
    for y in range(S):
        for x in range(S):
            gp = geo[y][x]
            if gp is None:
                continue
            dd, nx, ny = gp
            lightdot = -(nx + ny) / math.sqrt(2)
            if dd <= 1.0:
                out[y][x] = outline
            elif dd <= 3.0:
                q = (lightdot + 1) / 2 * 3.99
                q = int(q) + (1 if dd <= 2.0 and q > 2.2 else 0)
                out[y][x] = rim[max(0, min(4, q))]
            else:
                cxr, cyr = (x + 0.5 - 16) / 13.0, (y + 0.5 - 13.0) / 13.0
                radial = clamp(1.0 - math.hypot(cxr, cyr))
                diag = clamp(0.5 - (x + y - 31) / 46.0)
                t = clamp(0.62 * radial + 0.48 * diag - 0.04)
                tq = t * 6 + (BAYER4[y % 4][x % 4] / 16.0 - 0.5) * 0.7
                out[y][x] = fill_color(fc, clamp(math.floor(tq + 0.5) / 6), "crest")
                fillmask[y][x] = True
    for y in range(S):
        for x in range(S):
            gp = geo[y][x]
            if gp and 3.0 < gp[0] <= 4.0:
                out[y][x] = mix(out[y][x], outline, 0.55)
    # faixa do "chefe" do brasao (linha clara sob o topo) e rebites nos cantos de cima
    for x in range(6, 26):
        if geo[5][x] and geo[5][x][0] > 3.0:
            out[5][x] = mix(out[5][x], rim[3], 0.35)
    for (x, y) in ((5, 4), (26, 4)):
        out[y][x] = rim[4]
        out[y + 1][x + 1] = rim[0]
    for (x, y, a) in ((6, 7, .5), (5, 8, .5), (5, 9, .35), (7, 6, .3)):
        if out[y][x][3]:
            out[y][x] = blend(out[y][x], rgb("FFFFFF"), a)
    return out, fillmask


def make_faction_icons():
    res = []
    for fid, col, metal, fn in FACTIONS_V08:
        fc = rgb(col)
        out, fillmask = draw_crest(fc, rgb(metal))
        p = Pic(22, 22)
        fn(p)
        ramps = ramps_for(fc, "arcane")
        ramps["p"] = make_ramp(elem_light(fc))
        h, s, v = to_hsv(fc)
        ramps["q"] = make_ramp(from_hsv(h, min(1, s * 1.1 + 0.15), 0.62))
        oc = shade(fc, 0.07, 1.0, 0.1)
        img = p.render(ramps, oc)
        ox, oy = 5, 4
        for y in range(22):
            for x in range(22):
                if img[y][x][3]:
                    X, Y = ox + x + 1, oy + y + 1
                    if 0 <= X < 32 and 0 <= Y < 32 and fillmask[Y][X]:
                        out[Y][X] = blend(out[Y][X], (0, 0, 0, 255), 0.42)
        paste(out, img, ox, oy)
        res.append((fid, out))
    return res


# =====================================================================================================  v0.8: PILHA
# textures/gui/stack/<id>.png 16x16 - marcas de tier das conjuracoes pendentes no HUD "Pilha" e a de contrariada.
def stack_minor(d):
    # MENOR: ficha azul-prata com UMA runa (losango)
    d.disc(8, 8, 6.6, "sky")
    d.disc(8, 8, 5.0, "ink", g=1)
    d.poly([(8, 4.4), (10.6, 8), (8, 11.6), (5.4, 8)], "sky.l", g=2)
    d.put(7, 6, "W")


def stack_major(d):
    # MAIOR: ficha dourada com DUAS divisas (chevrons) subindo
    d.disc(8, 8, 6.6, "gold")
    d.disc(8, 8, 5.0, "ink", g=1)
    chevron(d, 8, 6.0, 3.0, 1.6, 0, -1, "orange.l", g=2)
    chevron(d, 8, 10.2, 3.0, 1.6, 0, -1, "orange.l", g=3)


def stack_ultimate(d):
    # SUPREMA: estrela de pontas VERMELHA com um "!" branco (perigo maximo)
    burst(d, 8, 8, 7.8, 4.8, 8, "red", -90, (1.0, 0.86))
    d.disc(8, 8, 4.6, "blood.d", g=1)
    d.rect(7, 4, 8, 9, "W", g=2)
    d.rect(7, 11, 8, 12, "W", g=2)


def stack_countered(d):
    # CONTRARIADA: runa partida - aro violeta-arcano (a cor de "contrariada" no HUD da Pilha) quebrado ao meio,
    # as metades se afastando, corte branco e cacos voando
    d.ring(8, 8, 6.6, 4.8, "violet")
    d.disc(8, 8, 4.8, "ink", g=1)
    d.line(1.0, 15.0, 15.0, 1.0, 2.4, None)
    move_cells(d, lambda x, y: x + y < 16.0, -1, -1)
    d.line(2.4, 13.6, 13.6, 2.4, 1.0, "W", g=3)
    for (x, y) in ((13, 13), (1, 2), (14, 10)):
        d.tri((x, y), (x + 1.6, y + 0.6), (x + 0.6, y + 1.6), "violet.l", g=5)


STACK_V08 = [("minor", stack_minor), ("major", stack_major), ("ultimate", stack_ultimate),
             ("countered", stack_countered)]


def make_stack_icons():
    return [(n, render16(fn)) for n, fn in STACK_V08]


# ======================================================================================================  v1.0
# Pictogramas tematicos das 10 classes novas. Os ids dos glifos de origem sao escolhidos pelos modulos de classe
# (fase B), entao estes desenhos NAO geram arquivo sozinhos: entram pelas palavras-chave (KEYWORDS_V10, testadas
# ANTES das gerais) e o glifo herda a cor da classe pela pasta de traducao (src/main/lang/class_<id>/ -> origem).
# Os dois do Encantador sao obrigatorios (pedido do jogador, "Yuuki Kagurazaka" de Tensura): Anti-Magia e
# Restricao Celestial (homenagem Toji).

@pic("v10_anti_magic")
def _(d):
    # Anti-Magia: a estrela arcana violeta riscada pelo sinal de PROIBIDO (aro e barra vermelhos) - magia nao entra
    d.star(10, 10, 6.6, 2.4, 4, -90, "violet", g=0)
    d.star(10, 10, 3.0, 1.2, 4, -45, "violet.l", g=1)
    d.ring(10, 10, 9.2, 7.0, "red", g=4)
    d.line(4.0, 4.0, 16.0, 16.0, 2.4, "red", g=4)
    d.put(5, 3, "W", g=6)


@pic("v10_heavenly_restriction")
def _(d):
    # Restricao Celestial: punho cerrado de corpo absurdo rompendo as correntes (elos partidos dos dois lados) e a
    # gota de mana RISCADA (abre mao da magia em troca do corpo)
    fist_front(d, 9.0, 11.6, 1.45, "flesh", g=0, sleeve="black")
    for (cx, cy, rot) in ((2.6, 14.4, 35), (4.8, 12.2, 35)):
        chain_link(d, cx, cy, 1.9, 1.1, 0.9, rot, "steel", g=6)
    for (cx, cy, rot) in ((15.4, 12.6, -35), (17.4, 14.6, -35)):
        chain_link(d, cx, cy, 1.9, 1.1, 0.9, rot, "steel", g=7)
    d.put(6, 10, "W", g=8)
    d.put(14, 10, "W", g=8)
    drop(d, 16.0, 4.0, 2.6, "manac", 1.6, shine=False, g=10)
    d.line(13.0, 1.0, 19.0, 7.6, 1.2, "red", g=12)


@pic("v10_forge")
def _(d):
    # Forja Arcana: o martelo de forja erguido (cabeca de ferro, cabo claro) sobre a lamina em brasa na bigorna,
    # faiscas saltando
    d.poly([(2.0, 13.0), (18.0, 13.0), (18.0, 15.0), (14.0, 15.6), (13.0, 17.0), (15.6, 19.0), (4.4, 19.0),
            (7.0, 17.0), (6.0, 15.6), (2.0, 15.0)], "iron", g=0)
    d.rect(2, 13, 17, 13, "steel", g=1)
    d.line(4.0, 11.6, 14.0, 11.6, 1.6, "ember", g=2)
    d.tri((14.0, 10.6), (17.4, 11.6), (14.0, 12.6), "flame", g=2)
    d.line(8.0, 6.6, 18.4, 2.2, 1.8, "wood.l", g=4)
    d.rect(3, 3, 9, 9, "iron", g=5)
    d.rect(3, 3, 9, 3, "steel", g=5)
    d.rect(3, 9, 9, 9, "iron.d", g=5)
    d.rect(4, 4, 4, 7, "steel.h", g=5)
    for (x, y, m) in ((2.0, 10.6, "flame.l"), (1.0, 8.0, "yellow.l"), (14.0, 8.4, "W"), (16.4, 9.0, "ember"),
                      (12.4, 6.6, "yellow.l")):
        d.put(x, y, m, g=8)


@pic("v10_steal")
def _(d):
    # Roubo: a mao sorrateira saindo da sombra e agarrando a bolsa de moedas (moeda escapando)
    bag(d, 12.6, 12.0, "brown", "gold", g=0)
    d.disc(4.0, 4.6, 2.2, "gold", g=4)
    d.put(3, 3, "W", g=5)
    hand(d, 0, 6, "p", g=8, rows=None)


@pic("v10_rune_blade")
def _(d):
    # Lamina Runica: espada larga com as runas acesas na lamina e o brilho magico em volta
    d.line(4.0, 16.0, 16.4, 3.6, 4.6, "p.d", g=0)
    sword(d, 3.0, 17.0, 17.4, 2.6, blade="silver", guard="gold", grip="wood", w=3.0, g=2)
    for k in range(3):
        x, y = 7.6 + k * 2.8, 11.6 - k * 2.8
        d.put(x, y, "cyan.h", g=8)
        d.put(x + 1, y, "cyan.h", g=8)
    for (x, y) in ((16, 9), (17, 12), (8, 3)):
        d.put(x, y, "cyan.l", g=10)


@pic("v10_enchant_seal")
def _(d):
    # Encantamento: selo circular com o losango no meio e a seta de melhoria subindo (buff em si ou no aliado)
    d.ring(10, 11, 8.4, 6.8, "p", g=0)
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        d.put(10 + 7.6 * math.cos(a), 11 + 7.6 * math.sin(a), "W", g=1)
    d.poly([(10, 4.6), (15.0, 11.0), (10, 17.4), (5.0, 11.0)], "q", g=2)
    d.arrow(10.0, 15.0, 10.0, 4.0, 2.2, 4.0, "W", g=4)


@pic("v10_zanpakuto")
def _(d):
    # Zanpakuto: katana de guarda quadrada erguida diante da lua crescente, com a fita do cabo ao vento
    d.disc(6.4, 6.4, 5.4, "bone.l", g=0)
    d.disc(9.2, 4.4, 4.6, None)
    d.line(4.6, 15.4, 17.6, 2.4, 2.0, "steel", g=2)
    d.line(9.0, 11.0, 17.6, 2.4, 0.8, "W", g=3)
    d.rect(3, 14, 6, 17, "gold", g=4)
    d.line(1.0, 19.0, 4.0, 16.0, 1.8, "black", g=5)
    d.curve([(1.4, 18.6), (4.0, 19.6), (7.0, 18.6)], 0.9, "red", g=6)


@pic("v10_inner_world")
def _(d):
    # Mundo Interior: a figura meditando de pernas cruzadas sob a lua, com a espada espiritual de pe ao lado
    d.disc(15.0, 4.6, 3.6, "bone.l", g=0)
    d.disc(16.8, 3.4, 3.0, None)
    d.disc(8.0, 4.8, 2.3, "flesh", g=2)
    d.poly([(5.4, 8.0), (10.6, 8.0), (11.6, 14.6), (4.4, 14.6)], "black", g=3)
    d.poly([(6.6, 8.0), (9.4, 8.0), (8.0, 11.0)], "white", g=4)
    d.line(5.0, 9.4, 8.0, 13.0, 1.2, "black", g=3)
    d.line(11.0, 9.4, 8.0, 13.0, 1.2, "black", g=3)
    d.ellipse(8.0, 15.6, 6.4, 1.8, "black", g=5)
    d.disc(8.0, 12.8, 0.9, "p.l", g=6)
    d.line(16.0, 18.6, 16.0, 7.6, 1.4, "steel", g=5)
    d.rect(14, 15, 17, 15, "gold", g=6)
    d.ellipse(10.0, 18.6, 8.6, 1.0, "q", g=1)


@pic("v10_spirit_bow")
def _(d):
    # Arco Espiritual (Quincy): arco de luz azul em trelica com a flecha de energia armada e a cruz Quincy no punho
    d.arc(6.0, 10.0, 9.0, 7.4, 280, 440, "sky.l", g=0)
    d.arc(6.0, 10.0, 8.2, 7.8, 290, 430, "W", g=1)
    d.line(6.0, 1.2, 6.0, 18.8, 0.7, "cyan.h", g=2)
    d.arrow(2.0, 10.0, 18.6, 10.0, 1.4, 4.0, "cyan.h", g=4)
    d.star(14.2, 10.0, 2.4, 0.8, 5, -90, "W", g=6)


@pic("v10_absorb")
def _(d):
    # Absorver: particulas de energia espiralando para dentro da palma aberta (e voltando como tiro)
    d.disc(10, 10, 3.0, "p", g=0)
    for i in range(6):
        a = math.radians(i * 60)
        x0, y0 = 10 + 8.6 * math.cos(a), 10 + 8.6 * math.sin(a)
        x1, y1 = 10 + 4.4 * math.cos(a + 0.7), 10 + 4.4 * math.sin(a + 0.7)
        d.line(x0, y0, x1, y1, 1.2, "p.l", g=2 + i % 2)
        d.put(x0, y0, "W", g=4)
    d.disc(10, 10, 1.4, "W", g=6)


@pic("v10_bullet")
def _(d):
    # Bala Magica: revolver disparando - clarao no cano e a bala de energia saindo com rastro
    d.rect(2, 7, 10, 9, "steel", g=0)
    d.rect(5, 6, 9, 11, "iron", g=1)
    d.disc(7.0, 8.6, 1.4, "steel", g=2)
    d.poly([(3.0, 10.0), (6.0, 10.0), (5.6, 16.4), (2.0, 16.0)], "wood", g=3)
    burst(d, 12.0, 8.0, 3.4, 1.4, 6, "flame", g=6)
    d.line(13.0, 8.0, 17.0, 8.0, 1.6, "p.l", g=8)
    d.ellipse(17.6, 8.0, 1.8, 1.4, "gold", g=9)
    d.put(2, 7, "W", g=10)


@pic("v10_eagle")
def _(d):
    # Companheiro: aguia de cabeca branca com as asas abertas, garras amarelas mergulhando
    wing(d, 6.0, 9.0, "brown", g=0, flip=True, s=0.8)
    wing(d, 14.0, 9.0, "brown", g=1, s=0.8)
    d.ellipse(10.0, 11.4, 2.8, 4.4, "brown", g=2)
    d.disc(10.0, 6.4, 2.4, "white", g=3)
    d.tri((11.4, 6.0), (13.6, 7.2), (11.4, 8.0), "yellow", g=4)
    d.put(10, 6, "k")
    d.poly([(8.4, 15.0), (11.6, 15.0), (12.4, 18.4), (7.6, 18.4)], "white", g=5)
    d.rect(8, 15, 8, 16, "yellow", g=6)
    d.rect(11, 15, 11, 16, "yellow", g=6)


@pic("v10_card")
def _(d):
    # Carta Carregada: duas cartas arremessadas girando, a da frente com o coracao e a aura de energia
    d.poly([(2.0, 6.0), (9.0, 3.0), (12.4, 11.0), (5.4, 14.0)], "white.d", g=0)
    d.poly([(7.0, 6.6), (15.4, 5.0), (17.0, 15.4), (8.6, 17.0)], "white", g=2)
    d.heart(12.0, 11.0, 0.3, "red", g=4)
    d.put(8, 7, "red", g=5)
    d.put(15, 15, "red", g=5)
    for (x0, y0, x1, y1) in ((17.6, 2.6, 19.4, 1.0), (18.6, 6.0, 19.8, 5.4)):
        d.line(x0, y0, x1, y1, 1.0, "p.l", g=7)


@pic("v10_dice")
def _(d):
    # Sorte: dois dados rolando (um com o 6, outro de lado com o 1) e a estrelinha do jackpot
    d.rect(2, 7, 10, 15, "white", g=0)
    for (x, y) in ((3, 8), (3, 11), (3, 14), (9, 8), (9, 11), (9, 14)):
        d.put(x + (1 if x == 3 else -1), y, "k")
    d.poly([(10.6, 11.0), (16.4, 8.6), (18.8, 14.4), (13.0, 16.8)], "white.d", g=2)
    d.disc(14.7, 12.7, 1.0, "red", g=3)
    d.star(15.6, 3.6, 2.8, 1.1, 5, -90, "gold", g=6)


@pic("v10_roulette")
def _(d):
    # Roleta/Cassino: roda da roleta (casas vermelhas e pretas, aro dourado) com a bolinha branca
    d.disc(10, 10, 8.6, "gold", g=0)
    for i in range(12):
        a0 = i * 30
        d.arc(10, 10, 7.4, 3.4, a0, a0 + 30, "red" if i % 2 else "black", g=1 + i % 2)
    d.disc(10, 10, 3.0, "gold.d", g=4)
    d.line(10, 7.4, 10, 12.6, 0.9, "gold.l", g=5)
    d.line(7.4, 10, 12.6, 10, 0.9, "gold.l", g=5)
    d.disc(14.6, 5.4, 1.2, "W", g=6)


@pic("v10_spin_ball")
def _(d):
    # Rotacao: bola de aco com os sulcos dourados girando e os arcos de velocidade em volta
    d.arc(10, 10, 9.4, 8.2, 120, 220, "p.l", g=0)
    d.arc(10, 10, 9.4, 8.2, 300, 40, "p.l", g=0)
    d.disc(10, 10, 6.4, "iron", g=2)
    d.arc(10, 10, 5.2, 4.0, 190, 350, "gold", g=3)
    d.arc(10, 10, 3.4, 2.2, 10, 170, "gold", g=3)
    d.disc(10, 10, 1.0, "gold.d", g=3)
    d.rect(7, 6, 7, 7, "W", g=4)


@pic("v10_spin_nail")
def _(d):
    # Unha Giratoria (Atos): o dedo apontado disparando a unha que gira em espiral, com o buraco no alvo
    d.line(1.0, 15.0, 7.0, 12.0, 3.0, "flesh", g=0)
    d.ellipse(8.0, 11.6, 1.6, 1.1, "pink.l", g=1)
    spiral(d, 14.0, 8.0, 0.8, 5.4, 1.8, 1.3, "p", g=3)
    d.disc(14.0, 8.0, 1.0, "W", g=5)
    d.line(9.4, 11.0, 12.0, 9.4, 0.8, "p.l", g=6)


# =====================================================================================================  CLASSES (v0.9 -> v1.0)
# textures/gui/class/<id>.png 32x32 - retrato de cada uma das 15 classes numa moldura em ARCO (janela de retrato):
# aro no metal da CATEGORIA (Magica = prata-violeta, Fisica = aco, Destreza = bronze) misturado com a cor da
# classe, campo em gradiente escuro da cor da classe e uma gema da categoria (cor do recurso) no topo e embaixo.
# O retrato (24x24) e recortado dentro da janela. O chapeu/elmo/capuz e a "assinatura" de cada classe.
# v1.0: as 10 classes da v0.9 (warrior...hunter) sairam; entram smith rogue knight enchanter shinigami (PHYSICAL) e
# quincy gunslinger ranger gambler steelball (DEXTERITY). Os PNGs de classes que nao existem mais sao apagados.
CLASS_CATEGORY = {"mage": "magic", "witch": "magic", "warlock": "magic", "druid": "magic", "necromancer": "magic",
                  "smith": "physical", "rogue": "physical", "knight": "physical", "enchanter": "physical",
                  "shinigami": "physical", "quincy": "dexterity", "gunslinger": "dexterity", "ranger": "dexterity",
                  "gambler": "dexterity", "steelball": "dexterity"}
CAT_METAL = {"magic": rgb("CDBFFF"), "physical": rgb("D4D8E2"), "dexterity": rgb("E2C890")}
CAT_GEM = {"magic": rgb("7A5CFF"), "physical": rgb("E0563B"), "dexterity": rgb("3BC9A8")}   # = cores dos recursos
CAT_METAL["martial"], CAT_METAL["ranged"] = CAT_METAL["physical"], CAT_METAL["dexterity"]   # ids antigos (v0.9)
CAT_GEM["martial"], CAT_GEM["ranged"] = CAT_GEM["physical"], CAT_GEM["dexterity"]


def bust(d, body="p", skin="flesh", hx=12.0, hy=11.6, hr=4.4, g=0, shoulders=8.8, head=True):
    """Busto de retrato 24x24: ombros (elipse cortada pela moldura), pescoco e cabeca."""
    d.fill(lambda x, y: y >= hy + hr - 0.6 and ((x - hx) / shoulders) ** 2 + ((y - 25.0) / 7.4) ** 2 <= 1, body, g=g)
    d.rect(int(hx - 1.5), int(hy + hr - 1.2), int(hx + 1.5) - 1, int(hy + hr + 1.2), skin, g=g + 1)
    if head:
        d.disc(hx, hy, hr, skin, g=g + 2)


def face(d, hx=12.0, hy=11.6, eyes="k", mouth="flesh.d", brows=None, dy=0.4):
    d.put(hx - 1.8, hy + dy, eyes)
    d.put(hx + 1.6, hy + dy, eyes)
    if brows:
        d.put(hx - 2.2, hy + dy - 1.4, brows)
        d.put(hx - 1.4, hy + dy - 1.2, brows)
        d.put(hx + 1.2, hy + dy - 1.2, brows)
        d.put(hx + 2.0, hy + dy - 1.4, brows)
    if mouth:
        d.rect(int(hx - 1), int(hy + 2.6), int(hx), int(hy + 2.6), mouth)


def cls_mage(d):
    # Mago: chapeu pontudo de estrelas, barba branca e o cajado de orbe
    d.line(21.0, 23.6, 21.0, 9.0, 1.4, "wood", g=30)
    orb(d, 21.0, 7.0, 2.4, "cyan", g=31)
    bust(d, "p", hy=12.8)
    face(d, hy=12.4, mouth=None)
    d.poly([(8.0, 14.4), (16.0, 14.4), (15.0, 19.4), (12.0, 23.0), (9.0, 19.4)], "white", g=5)
    d.line(9.4, 15.0, 14.6, 15.0, 1.2, "white.l", g=6)
    d.poly([(3.6, 10.6), (20.4, 10.6), (17.6, 8.6), (6.4, 8.6)], "q", g=8)
    d.poly([(7.6, 9.0), (16.4, 9.0), (13.6, 3.6), (18.4, 1.0), (11.4, 0.8)], "q", g=8)
    d.rect(8, 7, 15, 8, "gold", g=9)
    d.star(12.4, 4.6, 1.8, 0.7, 4, -90, "yellow.l", g=10)
    d.put(15, 2, "yellow.l", g=10)


def cls_witch(d):
    # Bruxa: chapeu torto com fivela, cabelo roxo, pele verde, nariz pontudo e a pocao borbulhando
    bust(d, "black", skin="moss.l", hy=12.6)
    d.disc(12, 19.6, 1.3, "p", g=3)
    for sx in (-1, 1):
        d.fill(lambda x, y, sx=sx: 9.0 <= y <= 19.6 and 4.0 <= (x - 12) * sx <= 6.4 - max(0.0, y - 15.0) * 0.35,
               "purple", g=1)
    d.fill(lambda x, y: 8.6 <= y <= 10.4 and abs(x - 12) <= 5.0, "purple", g=1)
    d.put(10.2, 12.6, "yellow")
    d.put(13.6, 12.6, "yellow")
    d.tri((12.0, 12.6), (14.6, 14.6), (12.0, 14.6), "moss.l", g=4)
    d.line(10.0, 16.0, 13.4, 15.6, 0.8, "k")
    d.poly([(2.4, 10.2), (21.6, 10.2), (19.0, 8.4), (5.0, 8.4)], "black", g=8)
    d.poly([(7.4, 8.8), (16.6, 8.8), (14.6, 4.6), (18.6, 0.4), (10.4, 3.4)], "black", g=8)
    d.rect(8, 7, 15, 8, "p", g=9)
    d.rect(11, 7, 12, 8, "gold", g=10)
    d.disc(20.0, 20.6, 2.8, "glass", g=12)
    d.fill(lambda x, y: (x - 20.0) ** 2 + (y - 20.6) ** 2 <= 2.2 ** 2 and y >= 20.0, "poison", g=13)
    d.rect(19, 16, 20, 17, "glass", g=12)
    d.put(19, 14, "poison.l")
    d.put(21, 13, "poison.l")


def cls_warlock(d):
    # Bruxo: capuz rubro com chifres, rosto na sombra com olhos acesos e o selo do pacto atras; chama na mao
    d.ring(12, 11.4, 9.6, 8.6, "p.d", g=0)
    pts = [(12 + 9.0 * math.cos(math.radians(-90 + i * 144)), 11.4 + 9.0 * math.sin(math.radians(-90 + i * 144)))
           for i in range(6)]
    d.path(pts, 0.9, "p.d", g=0)
    d.curve([(8.6, 7.6), (5.0, 5.0), (5.6, 0.6)], 1.8, "bone", g=2)
    d.curve([(15.4, 7.6), (19.0, 5.0), (18.4, 0.6)], 1.8, "bone", g=3)
    bust(d, "blood", hy=12.0, head=False)
    d.disc(12, 11.4, 6.0, "blood", g=5)
    d.tri((12, 3.0), (9.0, 7.0), (15.0, 7.0), "blood", g=5)
    d.disc(12, 12.6, 3.8, "k")
    d.rect(9, 12, 10, 12, "red.l")
    d.rect(13, 12, 14, 12, "red.l")
    flame(d, 20.2, 23.0, 0.42, "magenta", "pink", "W", g=10)


def cls_druid(d):
    # Druida: capuz de couro com galhada de cervo enfeitada de folhas, rosto sereno e barba castanha
    for sx in (-1, 1):
        d.path([(12 + sx * 3.0, 6.4), (12 + sx * 6.0, 3.4), (12 + sx * 9.0, 3.0)], 1.5, "wood", g=0)
        d.line(12 + sx * 5.4, 3.8, 12 + sx * 5.8, 0.4, 1.3, "wood", g=0)
        d.line(12 + sx * 7.6, 3.2, 12 + sx * 9.6, 0.8, 1.3, "wood", g=0)
        leaf(d, 12 + sx * 9.0, 5.6, 0.32, 30 * sx, "leaf", vein=False, g=1)
    bust(d, "moss.d", hy=12.2, head=False)
    d.disc(12, 11.6, 6.0, "brown", g=4)
    d.disc(12, 12.4, 4.0, "flesh", g=5)
    d.poly([(8.4, 13.6), (15.6, 13.6), (15.0, 17.6), (12.0, 21.4), (9.0, 17.6)], "wood", g=6)
    d.rect(11, 15, 12, 15, "wood.d")
    face(d, hy=11.8, mouth=None)
    leaf(d, 9.4, 6.6, 0.3, -20, "grass", vein=False, g=8)
    leaf(d, 14.6, 6.6, 0.3, 20, "grass", vein=False, g=9)
    d.disc(12, 6.0, 1.2, "pink", g=10)


def cls_necromancer(d):
    # Necromante: capuz negro com rosto de caveira de olhos cianos e uma alma presa flutuando
    bust(d, "black", hy=12.0, head=False)
    d.disc(12, 11.0, 6.2, "dark", g=3)
    d.tri((12, 2.6), (8.4, 7.0), (15.6, 7.0), "dark", g=3)
    skull(d, 12, 12.6, 0.6, "bone", g=5)
    d.put(10, 12, "cyan.l")
    d.put(13, 12, "cyan.l")
    ghost(d, 20.2, 6.6, 0.36, "cyan.l", g=10, eyes="k")
    d.line(4.0, 23.6, 3.0, 6.0, 1.3, "bone", g=12)
    skull(d, 3.0, 4.6, 0.3, "bone", g=13, jaw=False)


# ---- v1.0: as 10 classes novas (5 Fisicas + 5 de Destreza). Cada retrato tem o rosto + o objeto-assinatura grande
# o bastante para ler no tamanho real (32x32): martelo e bigorna, adaga e capuz, espada runica, selo de encantamento,
# zanpakuto com a lua, arco espiritual azul, revolver, arco e aguia, carta e dado, bola de aco girando.
def cls_smith(d):
    # Ferreiro: bandana vermelha, barba cheia, braco forte de avental; o martelo erguido atras e a BIGORNA na frente
    # com o lingote em brasa soltando faiscas
    d.line(20.0, 16.0, 20.0, 5.6, 1.8, "wood", g=0)
    d.rect(16, 1, 23, 5, "iron", g=1)
    d.rect(16, 1, 17, 5, "steel", g=2)
    d.rect(22, 1, 23, 5, "iron.d", g=3)
    bust(d, "flesh", hy=10.8, shoulders=9.6)
    d.poly([(8.0, 15.2), (16.0, 15.2), (17.4, 24.0), (6.6, 24.0)], "brown", g=4)
    d.line(8.4, 15.4, 6.6, 13.2, 1.0, "brown.d", g=5)
    d.line(15.6, 15.4, 17.4, 13.2, 1.0, "brown.d", g=5)
    d.poly([(7.8, 11.6), (16.2, 11.6), (15.8, 14.2), (12.0, 16.8), (8.2, 14.2)], "orange.d", g=6)
    d.rect(11, 13, 12, 13, "flesh.d", g=7)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 10.8) ** 2 <= 4.7 ** 2 and y <= 8.8, "red", g=8)
    d.rect(7, 8, 16, 8, "red.d", g=9)
    d.tri((7.0, 8.2), (4.4, 6.4), (4.8, 10.2), "red", g=10)
    face(d, hy=10.6, mouth=None, brows="orange.d")
    # bigorna de ferro escuro: mesa larga com chifre a esquerda, cintura e pe (tudo acima da base da janela)
    d.poly([(4.6, 16.6), (19.4, 16.6), (19.4, 18.6), (15.6, 19.2), (14.6, 20.4), (17.0, 22.6), (6.4, 22.6),
            (8.8, 20.4), (7.8, 19.2), (4.6, 18.6)], "black", g=12)
    d.poly([(4.8, 16.6), (0.6, 16.8), (2.0, 17.6), (4.8, 18.6)], "black", g=12)
    d.rect(5, 16, 19, 16, "steel", g=13)
    d.rect(8, 14, 13, 15, "ember", g=14)
    d.rect(9, 14, 12, 14, "flame.l", g=15)
    for (x, y, m) in ((3, 14, "flame.l"), (2, 12, "yellow.l"), (5, 12, "ember"), (17, 14, "flame"), (18, 12, "W")):
        d.put(x, y, m, g=16)


def cls_rogue(d):
    # Ladino: capuz fundo puxado sobre os olhos (so o brilho do olhar), lenco no rosto, a adaga erguida em
    # pegada invertida com o reflexo na lamina e a moeda roubada girando no ar
    d.disc(4.0, 5.4, 2.4, "gold", g=0)
    d.rect(3, 5, 4, 5, "gold.d", g=1)
    d.put(3, 4, "W", g=1)
    bust(d, "black", hy=12.0, head=False, shoulders=9.6)
    d.fill(lambda x, y: ((x - 12) / 9.0) ** 2 + ((y - 24.0) / 6.6) ** 2 <= 1 and y >= 17.4, "ink", g=2)
    d.disc(12, 11.2, 6.4, "ink", g=3)
    d.tri((12.0, 1.0), (7.8, 7.0), (16.2, 7.0), "ink", g=3)
    d.fill(lambda x, y: ((x - 12) / 4.0) ** 2 + ((y - 13.0) / 4.2) ** 2 <= 1, "black.d", g=4)
    d.fill(lambda x, y: ((x - 12) / 4.0) ** 2 + ((y - 13.0) / 4.2) ** 2 <= 1 and y >= 13.6, "p.d", g=5)
    d.line(8.6, 13.4, 15.4, 13.4, 0.9, "p", g=6)
    d.rect(9, 11, 10, 11, "yellow.l")
    d.rect(14, 11, 15, 11, "yellow.l")
    d.put(10, 11, "W")
    d.put(14, 11, "W")
    # adaga: guarda em baixo (mao), ponta para cima-direita
    blade(d, 17.4, 21.4, 21.6, 9.4, "steel", "gold", "black", 2.2, g=10, grip_len=2.8, guard_w=2.2)
    d.line(19.2, 17.4, 20.8, 12.4, 0.8, "W", g=14)
    d.disc(17.0, 22.6, 1.8, "flesh", g=15)


def cls_knight(d):
    # Cavaleiro Runico: grande elmo fechado (topo reto, fenda em cruz acesa em ciano) com o penacho vermelho saindo
    # por cima do aro; a ESPADA RUNICA de pe ao lado (como o cajado do Mago): lamina larga prateada com as runas cianas
    # acesas e a aura em volta, guarda dourada no ombro e o cabo na manopla
    d.curve([(10.4, 3.8), (6.4, 0.2), (2.4, 2.4)], 2.2, "red", g=0)
    d.put(3, 2, "red.d", g=0)
    bust(d, "p.d", hx=11.0, hy=12.4, head=False, shoulders=9.4)
    d.disc(3.6, 18.2, 3.2, "iron", g=2)
    d.disc(19.6, 18.6, 3.0, "iron", g=2)
    d.rect(2, 16, 4, 16, "steel", g=2)
    # elmo (tudo no mesmo grupo: sem contorno interno, os detalhes sao so cor)
    d.fill(lambda x, y: 6.4 <= x <= 15.6 and 4.2 <= y <= 15.4 and not (y < 5.4 and (x < 7.6 or x > 14.4)),
           "iron", g=4)
    d.fill(lambda x, y: 6.4 <= x <= 7.6 and 5 <= y <= 15.4, "iron.d", g=4)
    d.rect(10, 4, 11, 15, "steel", g=4)
    d.put(10, 4, "W", g=4)
    d.rect(7, 9, 14, 10, "k", g=4)
    d.rect(10, 9, 11, 13, "k", g=4)
    d.rect(8, 9, 9, 9, "cyan.h", g=4)
    d.rect(12, 9, 13, 9, "cyan.h", g=4)
    for y in (12, 14):
        d.put(8, y, "iron.d", g=4)
        d.put(13, y, "iron.d", g=4)
    d.rect(9, 16, 12, 18, "gold", g=6)
    d.put(10, 17, "cyan.l", g=7)
    # espada runica de pe (direita, longe do aro para nao se confundir com ele)
    d.fill(lambda x, y: 16.6 <= x <= 20.4 and 0.4 <= y <= 15.6, "cyan.d", g=9)
    d.rect(17, 2, 19, 15, "silver", g=10)
    d.tri((16.6, 2.4), (19.8, 2.4), (18.2, -0.8), "silver", g=10)
    d.rect(19, 2, 19, 15, "silver.d", g=10)
    for y in (4, 7, 10, 13):
        d.put(18, y, "cyan.h", g=10)
    d.rect(15, 16, 21, 16, "gold", g=12)
    d.rect(17, 17, 19, 20, "wood", g=13)
    d.disc(18.0, 20.6, 1.8, "iron", g=14)
    d.put(21, 3, "cyan.h", g=15)
    d.put(15, 6, "cyan.l", g=15)


def cls_enchanter(d):
    # Encantador: o selo de encantamento (circulo runico dourado) girando atras da cabeca, cabelo escuro curto, oculos
    # finos e sorriso calmo; a mao ergue um selo-losango aceso e faiscas douradas sobem (buffs/Anti-Magia)
    d.ring(12, 10.4, 10.4, 9.0, "gold", g=0)
    d.ring(12, 10.4, 7.6, 6.9, "gold.d", g=1)
    for i in range(12):
        a = math.radians(i * 30)
        x, y = 12 + 8.3 * math.cos(a), 10.4 + 8.3 * math.sin(a)
        d.put(x, y, "yellow.l" if i % 2 else "p", g=2)
    for i in range(4):
        a = math.radians(-90 + i * 90)
        d.star(12 + 9.7 * math.cos(a), 10.4 + 9.7 * math.sin(a), 1.8, 0.6, 4, -90, "yellow.l", g=3 + i)
    bust(d, "white", hy=12.0, shoulders=9.2)
    d.poly([(8.4, 16.8), (12.0, 21.0), (15.6, 16.8), (18.0, 24.0), (6.0, 24.0)], "ink", g=8)
    d.line(8.6, 17.2, 11.6, 20.8, 0.8, "gold", g=8)
    d.line(15.4, 17.2, 12.4, 20.8, 0.8, "gold", g=8)
    d.tri((10.6, 17.4), (13.4, 17.4), (12.0, 19.8), "white", g=9)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.6) ** 2 <= 4.8 ** 2 and y <= 10.0, "ink", g=10)
    d.tri((7.2, 9.6), (9.6, 9.6), (7.4, 12.6), "ink", g=10)
    d.tri((16.8, 9.6), (14.4, 9.6), (16.6, 12.6), "ink", g=10)
    d.tri((10.0, 9.2), (13.6, 9.2), (11.0, 11.0), "ink", g=10)
    face(d, hy=11.8, mouth=None)
    d.rect(9, 12, 11, 12, "silver", g=11)
    d.rect(13, 12, 15, 12, "silver", g=11)
    d.put(12, 12, "silver", g=11)
    d.put(10, 12, "k")
    d.put(14, 12, "k")
    d.arc(12, 13.2, 1.8, 1.0, 20, 160, "flesh.d")
    # selo-losango aceso na mao (frente, a direita)
    d.poly([(19.6, 13.0), (23.0, 17.0), (19.6, 21.0), (16.2, 17.0)], "violet.l", g=14)
    d.poly([(19.6, 15.0), (21.2, 17.0), (19.6, 19.0), (18.0, 17.0)], "magenta.l", g=15)
    d.put(19, 16, "W", g=16)
    d.put(19, 17, "W", g=16)
    d.disc(18.6, 22.2, 1.8, "flesh", g=17)
    for (x, y) in ((22.4, 12.0), (15.4, 13.4), (23.0, 21.4)):
        d.put(x, y, "violet.h", g=18)


def cls_shinigami(d):
    # Shinigami: a lua crescente atras (alto a esquerda), cabelo laranja espetado, quimono negro (shihakusho) com a
    # gola branca em V e a zanpakuto (katana de guarda quadrada) por cima do ombro, lamina reluzindo ao luar
    d.disc(6.0, 5.4, 4.8, "bone.l", g=0)
    d.disc(8.6, 3.6, 4.2, None)
    d.line(1.0, 23.0, 21.4, 1.6, 2.2, "steel", g=1)
    d.line(10.4, 12.8, 21.4, 1.6, 0.9, "W", g=2)
    bust(d, "black", hy=12.4, shoulders=9.6)
    d.poly([(8.8, 16.6), (12.0, 21.6), (15.2, 16.6), (13.8, 16.6), (12.0, 19.4), (10.2, 16.6)], "white", g=4)
    d.rect(10, 17, 13, 18, "flesh", g=5)
    d.disc(12, 12.0, 4.4, "flesh", g=6)
    for (x, y, h) in ((7.2, 10.6, 3.6), (9.2, 8.0, 4.2), (12.0, 7.0, 4.4), (14.8, 7.6, 4.0), (16.8, 10.2, 3.6)):
        d.tri((x - 1.6, y + 2.6), (x + 1.6, y + 2.6), (x + (x - 12) * 0.3, y + 2.6 - h), "orange", g=7)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 12.0) ** 2 <= 4.6 ** 2 and y <= 9.8, "orange", g=7)
    face(d, hy=12.0, mouth="flesh.d", brows="orange.d")
    # cabo e guarda (tsuba quadrada) da espada, na frente do ombro esquerdo
    d.rect(2, 19, 6, 22, "gold", g=10)
    d.line(1.0, 23.4, 3.6, 20.6, 1.6, "ink", g=11)
    d.put(3, 21, "W", g=12)


def cls_quincy(d):
    # Quincy: uniforme branco com a cruz Quincy azul no peito, cabelo escuro e oculos; o ARCO ESPIRITUAL de luz azul
    # erguido do lado direito (bojo para cima, corda puxada ate a mao) com a flecha de energia (Heilig Pfeil) armada
    # apontando para o alto e passando por cima do aro. O rosto fica um pouco a esquerda para o arco caber na janela.
    bust(d, "white", hx=9.8, hy=12.0, shoulders=9.2)
    d.line(7.0, 17.0, 12.6, 17.0, 1.0, "p", g=4)
    d.rect(9, 18, 10, 22, "p", g=5)
    d.rect(7, 19, 12, 20, "p", g=5)
    d.put(9, 19, "W", g=6)
    d.fill(lambda x, y: (x - 9.8) ** 2 + (y - 11.6) ** 2 <= 4.7 ** 2 and y <= 10.0, "ink", g=7)
    d.tri((11.8, 9.4), (14.6, 9.4), (14.2, 14.0), "ink", g=7)
    d.tri((5.0, 9.0), (7.4, 9.0), (5.2, 12.4), "ink", g=7)
    face(d, hx=9.8, hy=11.8, mouth="flesh.d")
    d.rect(7, 11, 9, 13, "glass", g=8)
    d.rect(11, 11, 13, 13, "glass", g=8)
    d.put(10, 12, "silver", g=9)
    d.put(8, 12, "k")
    d.put(12, 12, "k")
    d.put(7, 11, "W")
    d.put(11, 11, "W")
    # arco de luz, corda tensionada (duas metades ate a mao) e a flecha
    cx, cy = 18.6, 10.8
    d.arc(cx, cy, 5.2, 3.4, 190, 350, "cyan.h", g=10)
    d.arc(cx, cy, 4.6, 4.0, 205, 335, "W", g=10)
    for sx in (-1, 1):
        d.line(cx + sx * 4.4, 9.8, cx, 15.6, 0.7, "cyan.l", g=12)
    d.line(cx, 15.6, cx, 2.0, 1.0, "W", g=13)
    d.tri((cx - 1.8, 2.4), (cx + 1.8, 2.4), (cx, -0.8), "cyan.h", g=14)
    d.disc(cx, 16.6, 1.6, "flesh", g=15)
    for (x, y) in ((15, 3), (22, 4), (23, 13)):
        d.put(x, y, "cyan.l", g=16)


def cls_gunslinger(d):
    # Pistoleiro: chapeu de caubói de aba larga, lenco vermelho no pescoco, barba por fazer e o REVOLVER erguido ao
    # lado do rosto (cano para cima, tambor e cabo de madeira) com fumaca saindo do cano
    bust(d, "brown", hy=12.6, shoulders=9.4)
    d.poly([(8.0, 16.4), (16.0, 16.4), (12.0, 20.4)], "red", g=4)
    d.tri((12.0, 18.4), (10.6, 22.0), (13.4, 22.0), "red.d", g=5)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 12.6) ** 2 <= 4.5 ** 2 and y >= 14.2, "brown.d", g=6)
    face(d, hy=12.2, mouth=None, brows="k")
    d.rect(11, 15, 12, 15, "flesh.d", g=7)
    # chapeu: copa com amassado no meio e aba larga curvada
    d.fill(lambda x, y: abs(x - 12) <= 4.4 - max(0.0, 5.4 - y) * 0.3 and 2.2 <= y <= 8.4
           and not (y < 3.4 and abs(x - 12) < 1.2), "wood", g=9)
    d.rect(8, 7, 16, 7, "k", g=10)
    d.fill(lambda x, y: ((x - 12) / 10.6) ** 2 + ((y - 9.0) / 1.7) ** 2 <= 1
           or (abs(x - 12) > 7.6 and 7.0 <= y <= 9.0 and abs(x - 12) <= 10.4), "wood.d", g=8)
    # revolver (cano para cima) seguro na mao direita do retrato
    d.rect(18, 3, 19, 11, "steel", g=12)
    d.rect(17, 11, 21, 15, "iron", g=13)
    d.disc(19.0, 13.0, 1.4, "steel", g=14)
    d.put(19, 13, "k")
    d.poly([(17.4, 15.0), (20.8, 15.0), (21.8, 20.0), (19.0, 20.6)], "wood", g=15)
    d.disc(19.6, 19.6, 1.8, "flesh", g=16)
    d.put(18, 3, "W", g=17)
    for (x, y, r) in ((18.2, 1.0, 0.9), (16.4, 0.4, 0.8)):
        d.disc(x, y, r, "cloud", g=18)


def cls_ranger(d):
    # Ranger: capuz verde de patrulheiro, olhar atento, o arco longo nas costas e a AGUIA companheira pousada no
    # ombro esquerdo do retrato, de perfil (cabeca branca, bico amarelo em gancho, asas marrons dobradas)
    d.curve([(22.2, 22.4), (21.6, 6.0), (8.0, 0.4)], 1.8, "wood", g=0)
    d.line(22.0, 21.6, 8.6, 1.0, 0.7, "white", g=1)
    bust(d, "moss", hx=13.0, hy=12.2, head=False, shoulders=9.4)
    d.disc(13, 11.8, 6.0, "leaf.d", g=3)
    d.tri((13.0, 2.4), (9.6, 7.6), (16.4, 7.4), "leaf.d", g=3)
    d.disc(13, 13.0, 3.8, "flesh", g=4)
    d.recolor(lambda x, y: y < 11.8, "flesh.d", g=4, only="flesh")
    d.put(11.2, 12.8, "k")
    d.put(14.6, 12.8, "k")
    d.fill(lambda x, y: ((x - 13) / 6.4) ** 2 + ((y - 17.6) / 1.8) ** 2 <= 1, "brown", g=6)
    d.disc(13, 17.6, 1.0, "gold", g=7)
    # aguia de perfil (olhando para a esquerda): cauda, corpo, asa dobrada, cabeca branca e bico em gancho
    d.poly([(4.8, 19.0), (7.6, 19.0), (8.4, 23.6), (5.0, 23.6)], "brown.d", g=10)
    d.ellipse(6.2, 16.0, 2.8, 4.0, "brown", g=11)
    d.ellipse(7.2, 16.6, 1.8, 3.6, "brown.d", g=12, rot=-10)
    d.disc(5.6, 11.0, 2.6, "white", g=13)
    d.fill(lambda x, y: (x - 6.2) ** 2 + (y - 12.8) ** 2 <= 2.6 ** 2 and y >= 12.6 and x <= 8.2, "white", g=13)
    d.poly([(3.6, 10.0), (1.4, 10.6), (1.0, 12.8), (2.4, 12.0), (3.6, 12.0)], "yellow", g=14)
    d.put(1, 12, "yellow.d", g=15)
    d.put(4, 10, "k", g=15)
    d.put(5, 9, "white.d", g=15)
    d.rect(5, 19, 7, 19, "yellow.d", g=16)


def cls_gambler(d):
    # Apostador: cabelo penteado para tras, sorriso de canto com dente de ouro, terno magenta e gravata vermelha; a
    # carta (As de copas) erguida ao lado do rosto e o par de dados rolando na frente
    bust(d, "p.d", hy=12.0, shoulders=9.2)
    d.poly([(9.6, 16.6), (14.4, 16.6), (12.0, 20.8)], "white", g=4)
    d.poly([(11.2, 17.0), (12.8, 17.0), (12.6, 21.4), (12.0, 22.4), (11.4, 21.4)], "red", g=5)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.6) ** 2 <= 4.8 ** 2 and y <= 9.8, "ink", g=6)
    d.curve([(8.0, 9.0), (10.4, 6.0), (16.6, 7.8)], 1.6, "ink", g=6)
    face(d, hy=11.6, mouth=None, brows="ink")
    d.arc(12, 12.6, 2.2, 1.3, 15, 150, "k")
    d.put(13, 14, "gold.l")
    # carta erguida (a direita, inclinada)
    d.poly([(16.4, 4.0), (22.0, 5.2), (20.6, 13.4), (15.0, 12.2)], "white", g=8)
    d.heart(18.6, 8.6, 0.22, "red", g=9)
    d.put(16, 5, "red", g=10)
    d.disc(17.4, 13.6, 1.8, "flesh", g=11)
    # dados (frente, a esquerda): um de frente com o 5 e outro inclinado com o 3
    d.rect(1, 15, 6, 20, "white", g=12)
    for (x, y) in ((2, 16), (5, 16), (3, 17), (2, 19), (5, 19)):
        d.put(x + (1 if (x, y) == (3, 17) else 0), y + (1 if (x, y) == (3, 17) else 0), "k")
    d.poly([(5.6, 18.0), (9.0, 16.8), (10.4, 20.2), (7.0, 21.6)], "white", g=13)
    for (x, y) in ((6.8, 18.4), (7.8, 19.2), (8.8, 20.0)):
        d.put(x, y, "red")


def cls_steelball(d):
    # Steel Ball: chapeu de abas com a faixa e a estrela, cabelo loiro preso, sorriso com dentes de ouro e a BOLA DE
    # ACO girando na mao (sulcos dourados da rotacao + arcos de velocidade em volta)
    bust(d, "green.d", hy=12.0, shoulders=9.2)
    d.rect(10, 17, 13, 23, "white", g=4)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.6) ** 2 <= 4.8 ** 2 and y <= 10.4, "yellow", g=5)
    d.line(7.4, 11.0, 6.2, 16.6, 1.6, "yellow", g=5)
    face(d, hy=11.8, mouth=None)
    d.rect(10, 14, 13, 14, "W")
    d.put(11, 14, "gold")
    d.put(12, 14, "gold")
    # chapeu: copa baixa e aba que sobe dos lados
    d.fill(lambda x, y: abs(x - 12) <= 5.2 and 3.8 <= y <= 8.2, "green", g=7)
    d.rect(7, 7, 17, 7, "gold", g=8)
    d.star(12.0, 5.4, 1.8, 0.7, 5, -90, "yellow.l", g=9)
    d.curve([(2.6, 6.0), (6.0, 9.6), (18.0, 9.6), (21.4, 6.0)], 1.6, "green.d", g=10)
    # bola de aco em rotacao (na mao, a direita)
    d.arc(18.0, 18.0, 6.6, 5.6, 140, 250, "yellow.l", g=11)
    d.arc(18.0, 18.0, 6.6, 5.6, 320, 50, "yellow.l", g=11)
    d.disc(18.0, 18.0, 4.2, "iron", g=12)
    d.arc(18.0, 18.0, 3.4, 2.6, 190, 340, "gold", g=13)
    d.arc(18.0, 18.0, 2.2, 1.4, 10, 160, "gold", g=13)
    d.disc(18.0, 18.0, 0.7, "gold.d", g=13)
    d.rect(16, 15, 16, 16, "W", g=14)
    d.disc(14.0, 21.6, 1.6, "flesh", g=16)


CLASSES_V10 = [("mage", cls_mage), ("witch", cls_witch), ("warlock", cls_warlock), ("druid", cls_druid),
               ("necromancer", cls_necromancer),
               ("smith", cls_smith), ("rogue", cls_rogue), ("knight", cls_knight), ("enchanter", cls_enchanter),
               ("shinigami", cls_shinigami),
               ("quincy", cls_quincy), ("gunslinger", cls_gunslinger), ("ranger", cls_ranger),
               ("gambler", cls_gambler), ("steelball", cls_steelball)]
CLASSES_V09 = CLASSES_V10          # nome antigo (compatibilidade de quem importa o modulo)
CLASS_FILE_IDS = [cid for cid, _ in CLASSES_V10]


def draw_class_frame(cc, metal, gem):
    """Moldura em arco 32x32 (retrato): aro de metal da categoria + cor da classe, campo em gradiente da classe."""
    return draw_portrait_frame("class", cc, metal, gem)


def draw_portrait_frame(shape, cc, metal, gem):
    """Moldura de retrato 32x32. shape: "class" (janela em arco, gemas no topo e na base) ou "form" (medalhao
    redondo das RACAS, v1.0: gemas nas laterais). Devolve (grade RGBA, mascara do campo, geometria)."""
    S = 32
    geo = frame_geometry(shape, S)
    out = [[(0, 0, 0, 0)] * S for _ in range(S)]
    fillmask = [[False] * S for _ in range(S)]
    base = mix(elem_light(cc), metal, 0.5)
    h, s, v = to_hsv(base)
    rim = [from_hsv(hue_toward(h, 0.75, 0.05), min(1, s * 1.15 + 0.05), 0.42), from_hsv(h, s, 0.62),
           from_hsv(h, s * 0.9, 0.82), from_hsv(h, s * 0.65, 0.97), from_hsv(h, s * 0.25, 1.0)]
    outline = shade(cc, 0.09, 1.0, 0.1)
    for y in range(S):
        for x in range(S):
            gp = geo[y][x]
            if gp is None:
                continue
            dd, nx, ny = gp
            lightdot = -(nx + ny) / math.sqrt(2)
            if dd <= 1.0:
                out[y][x] = outline
            elif dd <= 3.0:
                q = (lightdot + 1) / 2 * 3.99
                q = int(q) + (1 if dd <= 2.0 and q > 2.2 else 0)
                out[y][x] = rim[max(0, min(4, q))]
            else:
                cxr, cyr = (x + 0.5 - 16) / 13.0, (y + 0.5 - 13.0) / 13.0
                radial = clamp(1.0 - math.hypot(cxr, cyr))
                diag = clamp(0.5 - (x + y - 31) / 46.0)
                t = clamp(0.62 * radial + 0.48 * diag - 0.04)
                tq = t * 6 + (BAYER4[y % 4][x % 4] / 16.0 - 0.5) * 0.7
                out[y][x] = fill_color(cc, clamp(math.floor(tq + 0.5) / 6), "class")
                fillmask[y][x] = True
    for y in range(S):
        for x in range(S):
            gp = geo[y][x]
            if gp and 3.0 < gp[0] <= 4.0:
                out[y][x] = mix(out[y][x], outline, 0.55)
    gl, gd = mix(gem, rgb("FFFFFF"), 0.45), shade(gem, 0.45)
    if shape == "class":
        # gemas da categoria: "pedra-chave" no topo do arco e na base
        for (x, y, c) in ((15, 1, gl), (16, 1, gem), (15, 2, gem), (16, 2, gd),
                          (14, 29, gl), (15, 29, gl), (16, 29, gem), (17, 29, gem),
                          (14, 30, gem), (15, 30, gem), (16, 30, gd), (17, 30, gd)):
            out[y][x] = c
        for (x, y) in ((13, 29), (18, 29), (13, 30), (18, 30), (14, 28), (15, 28), (16, 28), (17, 28), (14, 0),
                       (15, 0), (16, 0), (17, 0), (14, 1), (17, 1), (14, 2), (17, 2), (15, 3), (16, 3)):
            out[y][x] = outline
    else:
        # medalhao: duas gemas-losango nas laterais (esquerda e direita, na altura do queixo)
        for cx in (2, 29):
            for (dx, dy, c) in ((0, -1, gl), (-1, 0, gl), (0, 0, gem), (1, 0, gd), (0, 1, gd)):
                out[17 + dy][cx + dx] = c
            for (dx, dy) in ((0, -2), (-1, -1), (1, -1), (-2, 0), (2, 0), (-1, 1), (1, 1), (0, 2)):
                out[17 + dy][cx + dx] = outline
    for (x, y, a) in ((6, 9, .5), (6, 10, .5), (7, 7, .45), (8, 6, .35), (6, 11, .3), (9, 5, .25)):
        if fillmask[y][x]:
            out[y][x] = blend(out[y][x], rgb("FFFFFF"), a)
    return out, fillmask, geo


def compose_portrait(out, fillmask, geo, fn, cc, elem="arcane", ox=4, oy=5, over_top=14, ramps=None):
    """Desenha o retrato 24x24 (fn) dentro da moldura: sombra projetada no campo + recorte. Fora da moldura nunca;
    nas laterais/base fica DENTRO da janela (ombros cortados pelo aro), mas chapeus, chifres, orelhas e galhadas
    acima da linha 'over_top' podem "sair" por cima do aro (mais vivo, como um retrato heroico)."""
    p = Pic(24, 24)
    fn(p)
    if ramps is None:
        ramps = ramps_for(cc, elem)
    oc = shade(cc, 0.07, 1.0, 0.1)
    img = p.render(ramps, oc)
    for y in range(24):
        for x in range(24):
            X, Y = ox + x, oy + y
            if not (0 <= X < 32 and 0 <= Y < 32) or not geo[Y][X] or (geo[Y][X][0] <= 3.0 and Y >= over_top):
                img[y][x] = (0, 0, 0, 0)
    for y in range(24):
        for x in range(24):
            if img[y][x][3]:
                X, Y = ox + x + 1, oy + y + 1
                if 0 <= X < 32 and 0 <= Y < 32 and fillmask[Y][X]:
                    out[Y][X] = blend(out[Y][X], (0, 0, 0, 255), 0.42)
    paste(out, img, ox, oy)
    return out


def make_class_icons():
    res = []
    for cid, fn in CLASSES_V10:
        cc = rgb(ECOLOR[cid])
        cat = CLASS_CATEGORY[cid]
        out, fillmask, geo = draw_class_frame(cc, CAT_METAL[cat], CAT_GEM[cat])
        res.append((cid, compose_portrait(out, fillmask, geo, fn, cc, cid)))
    return res


# =====================================================================================================  v0.9: CATEGORIAS
# textures/gui/category/<id>.png 16x16 - abas da tela de classe (COLORIDOS, desenhar sem tingir).
def cat_magic(d):
    # Magicas: cajado de madeira com o cristal arcano no alto e faiscas
    d.line(3.0, 15.0, 10.0, 8.0, 1.8, "wood")
    d.ring(11.0, 5.0, 3.6, 2.4, "gold", g=1)
    crystal(d, 11.0, 5.0, 4.4, 2.2, "manac", g=2)
    sparkle(d, 3.5, 4.5, 2.8, "manac.l", g=5)     # centrado no pixel (em 4.0 so a haste vertical aparecia)
    sparkle(d, 14.4, 11.6, 2.0, "W", g=6)


def cat_physical(d):
    # Fisicas (v1.0, recurso Vigor): espada longa e martelo de forja cruzados com o estouro de forca vermelho no meio
    d.line(13.6, 14.6, 4.6, 5.6, 1.6, "wood", g=0)
    d.poly([(1.0, 3.4), (4.4, 0.0), (8.2, 3.8), (4.8, 7.2)], "iron", g=1)
    d.line(1.6, 3.2, 4.4, 0.6, 0.9, "steel.h", g=2)
    sword(d, 1.8, 14.2, 14.4, 1.6, blade="steel", guard="gold", grip="wood", w=2.2, g=4)
    burst(d, 8.0, 8.2, 4.4, 2.0, 8, "vigor", g=8)
    d.disc(8.0, 8.2, 1.4, "ember.l", g=9)
    d.put(7, 7, "W", g=10)


def cat_dexterity(d):
    # Destreza (v1.0, recurso Foco): alvo de tiro (aneis vermelho e branco) com a flecha de penas verde-agua cravada
    # bem no centro e os tracos de velocidade
    d.disc(9.6, 6.6, 6.0, "red", g=0)
    d.disc(9.6, 6.6, 4.4, "white", g=1)
    d.disc(9.6, 6.6, 2.6, "red", g=2)
    d.disc(9.6, 6.6, 1.0, "yellow", g=3)
    arrow_shaft(d, 1.6, 14.6, 9.8, 6.4, "wood", "wood", "focus", 1.2, 0.6, 0.6, g=5)
    for (x0, y0, x1, y1) in ((0.4, 10.4, 2.4, 10.4), (4.6, 15.4, 6.4, 15.4)):
        d.line(x0, y0, x1, y1, 0.9, "focus.l", g=8)


def cat_martial(d):
    # (v0.9) Guerreiras: escudo vermelho com a espada atravessada por tras. Nao gera mais arquivo (ver CATEGORIES_V10).
    sword(d, 1.6, 14.4, 14.6, 1.4, blade="steel", guard="gold", grip="wood", w=2.2, g=0)
    shield(d, 8.0, 9.0, 0.66, "vigor", "gold", g=4)
    d.rect(7, 6, 8, 11, "gold.l", g=6)


def cat_ranged(d):
    # (v0.9) A Distancia: arco tensionado com a flecha encaixada. Nao gera mais arquivo (ver CATEGORIES_V10).
    d.arc(4.6, 11.4, 9.6, 8.0, 225, 405, "wood", g=0)
    d.line(-0.2, 4.6, 11.6, 16.2, 0.8, "white", g=1)
    arrow_shaft(d, 2.4, 13.6, 14.8, 1.2, "wood", "steel", "focus", 1.2, 3.2, 1.8, g=3)


# v1.0: Category { MAGIC, PHYSICAL, DEXTERITY }. martial.png/ranged.png continuam como COPIAS de physical/dexterity
# (compatibilidade com quem ainda pede o id antigo).
CATEGORIES_V10 = [("magic", cat_magic), ("physical", cat_physical), ("dexterity", cat_dexterity),
                  ("martial", cat_physical), ("ranged", cat_dexterity)]
CATEGORIES_V09 = CATEGORIES_V10


# textures/gui/resource/<id>.png 16x16 - recurso por categoria (cores = MagicPath.Resource: Mana 7A5CFF, Vigor E0563B,
# Foco 3BC9A8). Mesma familia: a "gota" de mana, a chama-coracao do vigor e o olho-mira do foco.
def res_mana(d):
    drop(d, 8.0, 7.6, 5.0, "manac", 1.7)
    sparkle(d, 9.4, 10.0, 2.0, "W", g=3)


def res_vigor(d):
    # Vigor: punho cerrado em brasa (vermelho-alaranjado) com tracos de forca dos lados
    for (x0, y0, x1, y1) in ((0.6, 4.0, 2.6, 5.6), (15.4, 4.0, 13.4, 5.6), (0.6, 10.6, 2.4, 10.0), (15.4, 10.6, 13.6, 10.0)):
        d.line(x0, y0, x1, y1, 1.1, "ember", g=8)
    fist_front(d, 8.0, 7.4, 1.25, "vigor", g=0, sleeve="vigor.d")
    d.put(5, 4, "W")


def res_focus(d):
    for (x0, y0, x1, y1) in ((8, 0.4, 8, 3.2), (8, 12.8, 8, 15.6), (0.4, 8, 3.2, 8), (12.8, 8, 15.6, 8)):
        d.line(x0, y0, x1, y1, 1.2, "focus", g=0)
    d.ring(8, 8, 5.6, 4.4, "focus", g=1)
    d.disc(8, 8, 4.4, "white", g=2)
    d.disc(8, 8, 2.6, "focus", g=3)
    d.disc(8, 8, 1.1, "k")
    d.put(7, 6, "W")


RESOURCES_V09 = [("mana", res_mana), ("vigor", res_vigor), ("focus", res_focus)]


def make_category_icons():
    return [(n, render16(fn)) for n, fn in CATEGORIES_V10]


def make_resource_icons():
    return [(n, render16(fn)) for n, fn in RESOURCES_V09]


# =====================================================================================================  v1.0: RACAS
# textures/gui/race/<id>.png 32x32 - retrato de cada raca num MEDALHAO redondo (outra silhueta que a janela em arco
# das classes, para nao confundir): aro de bronze misturado com a cor da raca, campo em gradiente escuro da raca e
# duas gemas na cor da raca nas laterais. Orelhas, chifres e o elmo podem passar por cima do aro no alto.
RACE_METAL = rgb("E6D2A8")


def race_human(d):
    # Humano: aventureiro jovem de cabelo castanho com franja, sorriso confiante, tunica vermelha com a gola e a alca
    # de couro da mochila (o "padrao" de isekai: versatil, sem nada exotico)
    bust(d, "red", hy=11.4, shoulders=9.4)
    d.poly([(9.4, 16.2), (14.6, 16.2), (12.0, 19.4)], "flesh", g=4)
    d.line(15.8, 15.6, 9.0, 23.6, 1.4, "brown", g=5)
    d.put(13, 19, "gold", g=6)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.0) ** 2 <= 4.8 ** 2 and y <= 9.2, "brown", g=7)
    for (x0, x1) in ((8.0, 10.6), (10.4, 13.2), (13.0, 15.6)):
        d.tri((x0, 8.4), (x1, 8.4), (x0 + 0.6, 10.6), "brown", g=7)
    d.rect(7, 9, 7, 12, "brown", g=7)
    d.rect(16, 9, 16, 12, "brown", g=7)
    face(d, hy=11.2, mouth=None, brows="brown.d")
    d.arc(12, 12.6, 2.0, 1.2, 20, 160, "flesh.d")


def race_elf(d):
    # Elfo: orelhas longas e pontudas saindo do cabelo loiro comprido, diadema de folha com a gema verde,
    # olhos verdes serenos e o manto de folhas
    for sx in (-1, 1):
        d.poly([(12 + sx * 3.6, 10.6), (12 + sx * 10.8, 5.0), (12 + sx * 4.4, 13.0)], "flesh", g=1 if sx < 0 else 2)
        d.line(12 + sx * 5.0, 10.8, 12 + sx * 8.6, 7.4, 0.8, "flesh.d", g=1 if sx < 0 else 2)
    bust(d, "leaf.d", hy=11.2, shoulders=9.0)
    for sx in (-1, 1):
        d.fill(lambda x, y, sx=sx: 8.4 <= y <= 21.0 and 3.6 <= (x - 12) * sx <= 5.8 - max(0.0, y - 17.0) * 0.3,
               "yellow", g=3)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 10.8) ** 2 <= 4.9 ** 2 and y <= 9.0, "yellow", g=3)
    d.poly([(9.6, 15.6), (14.4, 15.6), (12.0, 18.6)], "leaf", g=5)
    d.rect(8, 8, 15, 8, "gold", g=6)
    d.tri((12.0, 5.2), (10.6, 8.0), (13.4, 8.0), "gold", g=6)
    d.put(12, 7, "green.l", g=7)
    face(d, hy=11.2, mouth="flesh.d", eyes="green.d")
    leaf(d, 6.0, 20.6, 0.26, -30, "leaf", vein=False, g=9)
    leaf(d, 18.0, 20.6, 0.26, 30, "leaf", vein=False, g=10)


def race_dwarf(d):
    # Anao: elmo de ferro com aba e chifrinhos, nariz grande, sobrancelhas grossas e a BARBA ruiva enorme trancada
    # cobrindo o peito (argolas douradas nas trancas)
    bust(d, "brown", hy=11.0, shoulders=10.0)
    d.disc(4.8, 18.4, 3.0, "iron", g=3)
    d.disc(19.2, 18.4, 3.0, "iron", g=3)
    d.poly([(7.0, 11.0), (17.0, 11.0), (17.4, 16.0), (14.6, 20.0), (12.0, 23.8), (9.4, 20.0), (6.6, 16.0)],
           "orange", g=5)
    d.line(10.2, 16.0, 10.6, 22.0, 1.0, "orange.d", g=6)
    d.line(13.8, 16.0, 13.4, 22.0, 1.0, "orange.d", g=6)
    d.rect(9, 20, 11, 20, "gold", g=7)
    d.rect(13, 20, 15, 20, "gold", g=7)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 10.4) ** 2 <= 5.4 ** 2 and y <= 8.6, "iron", g=8)
    d.rect(6, 8, 17, 9, "steel", g=9)
    d.tri((7.0, 6.8), (4.0, 2.2), (8.8, 5.2), "bone", g=10)
    d.tri((17.0, 6.8), (20.0, 2.2), (15.2, 5.2), "bone", g=10)
    d.rect(8, 10, 10, 10, "orange.d", g=11)
    d.rect(14, 10, 16, 10, "orange.d", g=11)
    d.put(9.2, 11.4, "k")
    d.put(14.6, 11.4, "k")
    d.disc(12, 12.6, 1.5, "flesh", g=12)
    d.put(12, 13, "flesh.d", g=12)


def race_half_orc(d):
    # Meio-Orc: pele verde, presas brancas saindo do labio, coque preto no alto, cicatriz no olho e a pele de
    # lobo nos ombros
    bust(d, "brown", hy=11.6, skin="moss.l", shoulders=9.8)
    for i, x in enumerate((4.0, 7.4, 16.6, 20.0)):
        d.disc(x, 18.0, 2.2, "sand", g=3 + i % 2)
    d.ellipse(12, 12.0, 5.2, 4.8, "moss.l", g=5)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.8) ** 2 <= 5.2 ** 2 and y <= 8.4, "black", g=6)
    d.disc(12, 4.6, 2.0, "black", g=7)
    d.rect(11, 6, 12, 7, "red", g=8)
    d.rect(8, 10, 10, 10, "moss.d", g=9)
    d.rect(14, 10, 16, 10, "moss.d", g=9)
    d.put(9, 11, "yellow")
    d.put(15, 11, "yellow")
    d.line(15.6, 8.6, 14.0, 13.4, 0.7, "red.d", g=10)
    d.rect(10, 14, 14, 14, "moss.d", g=11)
    d.rect(10, 13, 10, 13, "W", g=12)
    d.rect(14, 13, 14, 13, "W", g=12)
    d.put(10, 12, "W", g=12)
    d.put(14, 12, "W", g=12)


def race_demon(d):
    # Demonio: chifres curvos negros, cabelo preto, pele palida, olhos vermelhos acesos, caninos e a gola alta do
    # manto negro com o forro vermelho
    for sx in (-1, 1):
        d.curve([(12 + sx * 3.4, 7.6), (12 + sx * 8.4, 6.6), (12 + sx * 8.0, 0.8), (12 + sx * 5.6, -0.4)], 2.0,
                "black", g=1 if sx < 0 else 2)
    bust(d, "black", hy=11.6, skin="silver", shoulders=9.6)
    for sx in (-1, 1):
        d.poly([(12 + sx * 4.6, 13.0), (12 + sx * 7.4, 12.0), (12 + sx * 6.6, 19.0), (12 + sx * 3.0, 17.0)], "black",
               g=4 if sx < 0 else 5)
        d.line(12 + sx * 4.4, 14.0, 12 + sx * 6.0, 18.0, 0.8, "red", g=6)
    d.poly([(10.0, 17.0), (14.0, 17.0), (12.0, 20.6)], "red", g=7)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.2) ** 2 <= 4.8 ** 2 and y <= 9.4, "ink", g=8)
    d.tri((9.4, 9.0), (12.4, 9.0), (10.0, 11.6), "ink", g=8)
    d.rect(9, 11, 10, 11, "red.l")
    d.rect(14, 11, 15, 11, "red.l")
    d.put(9, 11, "W")
    d.put(14, 11, "W")
    d.rect(11, 14, 12, 14, "silver.d")
    d.put(10, 14, "W")
    d.put(13, 14, "W")
    d.put(12, 3, "magenta.l", g=9)


def race_dragonborn(d):
    # Dragonato: cabeca de dragao de frente (focinho com narinas, escamas verde-esmeralda, olhos amarelos de pupila
    # em fenda), chifres curtos para tras, crista na testa, babados laterais e o peito de escamas claras
    for sx in (-1, 1):
        d.curve([(12 + sx * 3.4, 6.4), (12 + sx * 6.4, 3.6), (12 + sx * 7.4, 0.6)], 1.8, "bone",
                g=1 if sx < 0 else 2)
        d.tri((12 + sx * 4.6, 8.4), (12 + sx * 9.0, 10.0), (12 + sx * 5.0, 12.6), "q", g=3)
    bust(d, "p.d", hy=11.6, head=False, shoulders=9.6)
    d.fill(lambda x, y: ((x - 12) / 4.0) ** 2 + ((y - 20.4) / 4.0) ** 2 <= 1, "sand", g=4)
    for y in (18, 20, 22):
        d.rect(10, y, 13, y, "sand.d", g=5)
    d.ellipse(12, 9.4, 5.0, 4.2, "p", g=6)
    d.poly([(8.0, 10.6), (16.0, 10.6), (15.4, 15.6), (13.6, 17.4), (10.4, 17.4), (8.6, 15.6)], "p", g=6)
    d.tri((12.0, 3.4), (10.6, 7.6), (13.4, 7.6), "q", g=7)
    d.line(9.0, 16.0, 15.0, 16.0, 0.8, "q", g=8)
    d.put(10, 14, "k")
    d.put(13, 14, "k")
    d.rect(8, 9, 10, 10, "yellow", g=9)
    d.rect(14, 9, 16, 10, "yellow", g=9)
    d.rect(9, 9, 9, 10, "k")
    d.rect(15, 9, 15, 10, "k")
    d.rect(8, 8, 10, 8, "q", g=10)
    d.rect(14, 8, 16, 8, "q", g=10)
    for (x, y) in ((11, 6), (13, 6), (12, 12), (10, 12), (14, 12)):
        d.put(x, y, "p.l", g=11)


def race_beastfolk(d):
    # Bestial: orelhas de lobo/raposa no alto (pelo laranja com o miolo rosado), cabelo selvagem, olhos ambar de
    # fera, caninos e a gola de pelo branco felpuda
    for sx in (-1, 1):
        g = 1 if sx < 0 else 2
        d.tri((12 + sx * 1.6, 7.0), (12 + sx * 8.0, 0.0), (12 + sx * 6.8, 8.6), "orange", g=g)
        d.tri((12 + sx * 3.4, 6.6), (12 + sx * 7.2, 2.2), (12 + sx * 6.2, 7.4), "pink", g=g + 2)
    bust(d, "brown.d", hy=11.6, shoulders=9.4)
    d.fill(lambda x, y: ((x - 12) / 6.8) ** 2 + ((y - 17.0) / 2.0) ** 2 <= 1, "white", g=4)
    for i in range(5):
        d.tri((6.6 + i * 2.7, 18.0), (9.0 + i * 2.7, 18.0), (7.8 + i * 2.7, 20.6), "white", g=4)
    d.fill(lambda x, y: (x - 12) ** 2 + (y - 11.2) ** 2 <= 4.9 ** 2 and y <= 9.4, "orange", g=6)
    for (x0, x1) in ((7.6, 10.2), (10.0, 12.8), (12.6, 15.4), (14.4, 16.6)):
        d.tri((x0, 8.8), (x1, 8.8), (x0 + 1.4, 11.2), "orange", g=6)
    d.tri((7.0, 9.0), (8.6, 9.0), (6.4, 14.4), "orange", g=6)
    d.tri((17.0, 9.0), (15.4, 9.0), (17.6, 14.4), "orange", g=6)
    d.rect(9, 11, 10, 11, "yellow")
    d.rect(14, 11, 15, 11, "yellow")
    d.put(10, 11, "k")
    d.put(14, 11, "k")
    d.put(12, 13, "k")
    d.arc(12, 13.4, 1.8, 1.0, 20, 160, "flesh.d")
    d.put(10, 15, "W")
    d.put(14, 15, "W")


# id, cor da raca (gemas, campo e o "p"), desenho
RACES_V10 = [("human", "6A8CC8", race_human), ("elf", "7ED87A", race_elf), ("dwarf", "C8823A", race_dwarf),
             ("half_orc", "6E9A3A", race_half_orc), ("demon", "B0204A", race_demon),
             ("dragonborn", "2FA88A", race_dragonborn), ("beastfolk", "E08A40", race_beastfolk)]


def make_race_icons():
    res = []
    for rid, col, fn in RACES_V10:
        rc = rgb(col)
        out, fillmask, geo = draw_portrait_frame("form", rc, RACE_METAL, rc)
        res.append((rid, compose_portrait(out, fillmask, geo, fn, rc, "arcane", ox=4, oy=5, over_top=12)))
    return res


# =====================================================================================================  v1.0: SOCIAL
# textures/gui/social/<id>.png 16x16 - menu de interacao entre jogadores (agachar + scroll) e lista de party/aliados.
# COLORIDOS (desenhar sem tingir), mesma familia das abas: Party, Troca, Aliado, Duelo, Perfil, Inimigo.
def soc_party(d):
    # Party: tres aventureiros juntos (o lider no meio, maior, com a estrelinha dourada)
    for (cx, m, g) in ((2.8, "green", 0), (13.2, "red", 2)):
        d.disc(cx, 7.8, 1.9, "flesh", g=g)
        d.fill(lambda x, y, cx=cx: ((x - cx) / 3.0) ** 2 + ((y - 14.8) / 4.6) ** 2 <= 1 and y <= 15.6, m, g=g + 1)
    d.disc(8.0, 5.6, 2.5, "flesh", g=4)
    d.fill(lambda x, y: ((x - 8) / 3.8) ** 2 + ((y - 14.6) / 6.0) ** 2 <= 1 and y <= 15.6, "blue", g=5)
    d.rect(6, 3, 10, 4, "brown", g=6)
    d.star(8.0, 1.4, 1.6, 0.6, 5, -90, "gold", g=7)


def soc_trade(d):
    # Troca: seta verde indo para a direita em cima, seta azul voltando embaixo e a moeda dourada entre as duas
    d.arrow(0.8, 3.2, 15.2, 3.2, 1.8, 3.4, "green", g=0)
    d.arrow(15.2, 12.8, 0.8, 12.8, 1.8, 3.4, "sky", g=1)
    d.disc(8.0, 8.0, 2.6, "gold", g=2)
    d.rect(8, 7, 8, 9, "gold.d", g=3)
    d.put(7, 6, "W", g=3)


def soc_ally(d):
    # Aliado: aperto de mao (manga azul de um lado, verde do outro) sobre o coracao/escudo verde de amizade
    d.poly([(2.0, 2.0), (14.0, 2.0), (14.0, 9.0), (8.0, 14.6), (2.0, 9.0)], "green.d", g=0)
    d.line(0.4, 10.6, 4.6, 8.0, 3.0, "blue", g=2)
    d.line(15.6, 10.6, 11.4, 8.0, 3.0, "teal", g=3)
    d.ellipse(8.0, 7.6, 3.8, 2.4, "flesh", g=4)
    for x in (6, 8, 10):
        d.put(x, 7, "flesh.d", g=5)
    d.rect(5, 5, 7, 5, "flesh", g=6)
    d.put(4, 3, "W", g=7)


def soc_duel(d):
    # Duelo: duas espadas cruzadas com o estouro vermelho do choque no meio
    sword(d, 1.4, 14.6, 14.2, 1.8, blade="steel", guard="gold", grip="wood", w=2.0, g=0)
    sword(d, 14.6, 14.6, 1.8, 1.8, blade="steel", guard="gold", grip="wood", w=2.0, g=4)
    burst(d, 8.0, 7.6, 3.8, 1.6, 6, "red", g=8)
    d.put(8, 7, "yellow.l", g=9)


def soc_profile(d):
    # Perfil: carteirinha de aventureiro (retrato a esquerda, linhas de texto e a medalha de rank)
    d.rect(0, 3, 15, 13, "paper", g=0)
    d.rect(0, 3, 15, 4, "blue", g=1)
    d.rect(2, 6, 6, 11, "sky.d", g=2)
    d.disc(4.0, 7.8, 1.3, "flesh", g=3)
    d.fill(lambda x, y: 2 <= x <= 7 and y >= 9.6 and ((x - 4.4) / 2.4) ** 2 + ((y - 11.8) / 2.4) ** 2 <= 1, "blue", g=4)
    for (y, x1) in ((7, 13), (9, 12), (11, 10)):
        d.rect(8, y, x1, y, "paper.d", g=5)
    d.disc(13.0, 11.0, 1.4, "gold", g=6)


def soc_enemy(d):
    # Inimigo: caveira de olhos vermelhos sobre o estandarte rasgado vermelho-escuro
    d.poly([(1.4, 0.6), (14.6, 0.6), (14.6, 13.6), (11.6, 11.6), (8.0, 15.4), (4.4, 11.6), (1.4, 13.6)], "blood", g=0)
    skull(d, 8.0, 7.2, 0.5, "bone", g=2)
    d.put(6, 7, "red.l", g=4)
    d.put(9, 7, "red.l", g=4)
    d.line(4.0, 2.6, 6.2, 4.2, 0.9, "k")
    d.line(12.0, 2.6, 9.8, 4.2, 0.9, "k")


SOCIAL_V10 = [("party", soc_party), ("trade", soc_trade), ("ally", soc_ally), ("duel", soc_duel),
              ("profile", soc_profile), ("enemy", soc_enemy)]


def make_social_icons():
    return [(n, render16(fn)) for n, fn in SOCIAL_V10]


# =====================================================================================================  v0.9: ABAS
def tab_travel(d):
    # Viagem: mapa dobrado com a rota tracejada e o alfinete vermelho do destino
    d.poly([(0.6, 3.0), (5.4, 1.4), (10.4, 3.0), (15.4, 1.4), (15.4, 13.0), (10.4, 14.6), (5.4, 13.0), (0.6, 14.6)],
           "paper")
    d.poly([(5.4, 1.4), (10.4, 3.0), (10.4, 14.6), (5.4, 13.0)], "sand", g=1)   # dobra do meio (paper.d saia cinza)
    dashed(d, [(2.4, 12.6), (4.6, 9.0), (8.0, 10.6), (10.6, 7.4)], "red", 1.0, 1, 1, g=3)
    d.tri((10.4, 4.8), (13.6, 4.8), (12.0, 8.6), "red", g=5)
    d.disc(12.0, 4.4, 2.4, "red", g=5)
    d.put(11, 3, "W")


def tab_class_trials(d):
    # Provas de Classe: taca dourada com a estrela do rank (diferente das Provacoes = espadas cruzadas)
    d.ring(2.8, 5.0, 2.4, 1.2, "gold", g=1)
    d.ring(13.2, 5.0, 2.4, 1.2, "gold", g=1)
    d.fill(lambda x, y: ((x - 8) / 5.6) ** 2 + ((y - 2.6) / 7.0) ** 2 <= 1 and y >= 1.4, "gold")
    d.rect(7, 9, 8, 11, "gold", g=2)
    d.rect(4, 11, 11, 12, "gold.d", g=3)
    d.rect(3, 13, 12, 15, "wood", g=4)
    d.star(8, 4.8, 2.8, 1.2, 5, -90, "red", g=6)
    d.put(4, 2, "W")


TABS += [("travel", tab_travel), ("class_trials", tab_class_trials)]


# =====================================================================================================  TABELA DE GLIFOS
# id -> tipo, elemento (ou origem), lendario. O pictograma tem o MESMO nome do id (PICS[id]).
GLYPHS = {}


def _g(kind, elem, ids, legend=""):
    leg = set(legend.split())
    for i in ids.split():
        GLYPHS[i] = {"kind": kind, "elem": elem, "legend": i in leg}


_g("form", "arcane", "projectile touch self beam cone slash nova mark wave fall rain zone wall rune pulse orbit aura "
   "seal turret conjure illusion portal cataclysm domain_expansion simple_domain arsenal_gate converge sky_lances "
   "spiral_sphere", "cataclysm domain_expansion arsenal_gate converge sky_lances spiral_sphere")
_g("augment", "arcane", "amplify dampen concentrate critical vampiric overload efficient expand extend shorten "
   "persistent delay echo accelerate decelerate split scatter pierce ricochet chain homing phantom arc sensitive "
   "enemies_only allies_only invert charge channel silent fortune silk_touch all_in colossal relentless",
   "all_in colossal")
_g("trigger", "arcane", "on_hurt on_low_health on_kill on_crit then together trigger_sneak trigger_sprint "
   "trigger_air trigger_water")
_g("link", "arcane", "link_hit link_end link_kill link_leap")
_g("law", "arcane", "law_sure_hit law_no_escape law_territory law_nullify law_slow_time law_gravity law_landscape "
   "law_open_barrier law_absolute", "law_absolute")
_g("effect", "arcane", "arcane_damage push pull slow_fall harvest conjure_light break blink levitate place_block "
   "detect stun mark_target shield recall telekinesis swap silence disarm shrink grow mana_gift reflect haste_time "
   "slow_time dispel mana_drain counterspell harvest_replant collect_items shear breed sow conjured_weapon "
   "blade_field", "conjured_weapon blade_field")       # v0.8: counterspell virou tier 3 (sem estrela)
_g("effect", "fire", "ignite explosion ember propulsion smoke fire_wall combustion smelt summon_salamander melt "
   "blue_flame overheat phoenix_rebirth crater smelt_items", "phoenix_rebirth crater")
_g("effect", "water", "soak tide bubble water_blade extinguish hydrate create_water water_breathing current mist mend "
   "summon_undine drown local_rain water_mirror deluge call_rain", "deluge")
_g("effect", "earth", "rock raise_pillar stalagmite stoneskin excavate fertilize stone_wall root quicksand "
   "summon_golem fortress tremor weight magnetism earthsplitter till", "earthsplitter")
_g("effect", "wind", "wind_blade gust impulse double_jump glide whirlwind launch swiftness summon_sylph tornado "
   "vacuum deflect silent_steps sonar tempest slash_storm", "tempest slash_storm")
_g("effect", "light", "holy_ray heal regenerate blind reveal purify blessing sunlight summon_guardian_fairy "
   "sacred_barrier exorcise prism sanctuary resurrection clear_sky", "resurrection")
_g("effect", "shadow", "life_drain weaken fear darkness shadow_step chains curse invisibility living_shadow devour "
   "pain_link steal_blessing nightmare possession", "possession")
_g("effect", "magma", "molten_rock lava_pool obsidian magma_armor eruption")
_g("effect", "steam", "scald steam_propulsion hot_mist geyser")
_g("effect", "lightning", "lightning_bolt paralyze arc_lightning magnet overcharge thunderstorm call_thunder")
_g("effect", "sun", "solar_ray holy_flame solar_crown dawn")
_g("effect", "hellfire", "black_flame infernal_chain blood_pact hell_portal")
_g("effect", "nature", "growth thorns spores vines bloom summon_ent photosynthesis")
_g("effect", "ice", "freeze ice_lance slippery_ground ice_bridge ice_armor blizzard ice_statue")
_g("effect", "purity", "greater_heal holy_water cleanse protective_bubble")
_g("effect", "abyss", "venom ink tentacles abyssal_pressure abyssal_drown")
_g("effect", "sand", "vitrify dune sandstorm sinkhole mirage")
_g("effect", "crystal", "shards crystallize crystal_armor reflecting_prism")
_g("effect", "gravity", "crush mass_levitate debris_orbit attraction_point invert_gravity")
_g("effect", "astral", "falling_star zero_gravity star_teleport constellation astral_projection")
_g("effect", "void", "erase rift banish warp annihilate", "annihilate")
_g("effect", "twilight", "equilibrium eclipse duality final_judgment", "final_judgment")
_g("effect", "mage", "explosive_seal teleport_circle return_rune ward temp_enchant", "temp_enchant")
_g("effect", "witch", "hex voodoo_doll evil_eye frogify familiar", "familiar")
_g("effect", "druid", "wolf_form bear_form dolphin_form falcon_form spider_form elemental_beast_form",
   "elemental_beast_form")
_g("effect", "necromancer", "raise_dead bone_shield soul_drain corpse_explosion bone_army lich_form", "lich_form")
_g("effect", "warlock", "summon_imp shadow_weapon death_contract demon_wings summon_demon", "summon_demon")
# selos extras onde dois pictogramas sao parentes proximos (ampulheta cheia/vazia)
GLYPHS["extend"]["badges"] = ["b_plus"]
GLYPHS["shorten"]["badges"] = ["b_minus"]

# ---- v0.8 "Reinos e Lendas" (docs/V08_DESIGN.md §7-§11, §13). Estrela = lendario (tier 5) ou ultimate (G).
V08_START = len(GLYPHS)
# legends_c (Jujutsu)
_g("effect", "arcane", "infinity lapse_blue reversal_red", "infinity")
_g("effect", "wind", "dismantle cleave")
_g("effect", "fire", "flame_arrow", "flame_arrow")
_g("law", "arcane", "law_shrine law_infinite_void", "law_shrine law_infinite_void")
# legends_d (Atomico, Desintegracao, Tempo, Cabra)
_g("effect", "arcane", "atomic goatify", "atomic")
_g("effect", "light", "disintegration", "disintegration")
_g("form", "arcane", "time_stop", "time_stop")
# legends_e (Magias de Nivel Supremo)
_g("form", "arcane", "super_tier", "super_tier")
_g("effect", "light", "fallen_down", "fallen_down")
_g("effect", "shadow", "black_goat grasp_heart death_clock", "black_goat death_clock")
# ultimates (meteor e uma forma normal; as outras sao G)
_g("form", "arcane", "meteor meteor_shower transcend colossus", "meteor_shower transcend colossus")
# fusions_c
_g("effect", "magma", "lava_geyser volcano")
_g("effect", "steam", "steam_explosion steam_cannon")
_g("effect", "lightning", "railgun ball_lightning")
_g("effect", "ice", "absolute_zero ice_coffin")
_g("effect", "nature", "world_roots pollen_storm")
_g("effect", "sand", "sand_coffin sand_tsunami")
_g("effect", "sun", "supernova solar_flare")
_g("effect", "purity", "sacred_spring purge")
# fusions_d
_g("effect", "crystal", "crystal_lotus prism_cannon")
_g("effect", "astral", "black_hole starfall")
_g("effect", "hellfire", "black_flame_pillar soul_burn")
_g("effect", "abyss", "kraken_grasp abyssal_maw")
_g("effect", "gravity", "planet_crush gravity_well")
_g("effect", "void", "dimension_slash void_rift")
_g("effect", "twilight", "ragnarok yin_yang")
# arcane_duel (Contramagica e mecanicas de Magic: The Gathering)
_g("effect", "arcane", "mana_leak redirect fork spell_steal hexproof exile time_walk", "time_walk")
_g("augment", "arcane", "deathtouch trample instant")
_g("effect", "light", "indestructible wrath", "wrath")
_g("trigger", "arcane", "in_response")
# power_up
_g("law", "arcane", "law_ruin")
V08_IDS = list(GLYPHS)[V08_START:] + ["counterspell"]   # counterspell: desenho novo na v0.8

# ---- v0.9 "Classes, Caminhos e o Dragao" (docs/V09_DESIGN.md §2.3, §5, §6). Estrela = forma G da categoria.
V09_START = len(GLYPHS)
_g("form", "arcane", "strike arrow stance")                                       # nucleo A1: formas de arma
_g("trigger", "arcane", "parry")                                                  # nucleo A1: gatilho reativo
_g("effect", "arcane", "guard")                                                   # nucleo A1: Resistencia (Postura Firme)
_g("form", "arcane", "cleave_arc charge_rush spin war_cry battle_avatar", "battle_avatar")             # guerreiras
_g("form", "arcane", "volley throw snare song mark_shot celestial_arrow_rain", "celestial_arrow_rain")  # a distancia
_g("effect", "warrior", "riposte blade_storm heroic_strike second_wind")
_g("effect", "paladin", "smite lay_on_hands divine_shield aura_of_devotion")
_g("effect", "barbarian", "rage reckless_attack earth_shatter primal_roar")
_g("effect", "monk", "flurry_of_blows ki_palm step_of_wind stunning_strike")
_g("effect", "knight", "shield_bash taunt bulwark iron_fortress")
_g("effect", "ranger", "hunters_mark piercing_shot multishot wind_arrow")
_g("effect", "rogue", "backstab poison_blade smoke_bomb shadow_dagger")
_g("effect", "bard", "inspire healing_ballad dissonant_chord lullaby")
_g("effect", "artificer", "arcane_bomb gear_turret infuse_item flamethrower")
_g("effect", "hunter", "beast_companion bear_trap crossbow_bolt pack_tactics")
V09_IDS = list(GLYPHS)[V09_START:]
# O id "charge" JA EXISTE (aumento "Carregar"); a forma "Investida" do classes_martial precisa de outro id. O desenho
# fica em "charge_rush"; se o modulo escolher um destes nomes, o icone sai com o mesmo desenho, sem selo.
PIC_ALIASES = {"rush": "charge_rush", "onrush": "charge_rush", "martial_charge": "charge_rush",
               "charge_attack": "charge_rush", "bull_rush": "charge_rush", "onslaught": "charge_rush",
               "warrior_charge": "charge_rush", "charging_strike": "charge_rush", "dash_strike": "charge_rush",
               "lunge": "charge_rush", "trap": "snare", "bow_shot": "arrow", "weapon_strike": "strike"}


# ------------------------------------------------------------------------------------- ids novos: palavras-chave
# Regex sobre "id nome descricao" (minusculas, SEM acentos) -> pictograma base. A primeira que casar vence.
# Primeiro a lista do TIPO deduzido, depois a geral, depois o padrao do tipo. Cuidado com "ar": use \bar\b
# (senao casa com todo verbo terminado em -ar).
KIND_KEYWORDS = {
    "link": [(r"acert|\bhit\b|impact|crit", "link_hit"), (r"termin|\bend\b|\bfim\b|acab", "link_end"),
             (r"abat|kill|morr|mort", "link_kill"), (r"salt|leap|pul", "link_leap")],
    "trigger": [(r"vida baixa|pouca vida|low health", "on_low_health"), (r"abat|\bkill|matar|morr", "on_kill"),
                (r"crit", "on_crit"), (r"dano|hurt|ferid|levar|golpead|bloque|block", "on_hurt"),
                (r"mesmo tempo|together|junt", "together"), (r"depois|\bthen\b|em seguida|sequ", "then"),
                (r"agach|sneak", "trigger_sneak"), (r"corr|sprint", "trigger_sprint"),
                (r"\bno ar\b|pul|salt|\bair\b", "trigger_air"), (r"agua|water|nad", "trigger_water")],
    "law": [(r"silenc|anul|nullif|cancel", "law_nullify"), (r"acerto|sure|garant|infalivel", "law_sure_hit"),
            (r"fuga|escape|prend|aprision|jaula", "law_no_escape"), (r"territ|dominan", "law_territory"),
            (r"tempo|\btime\b|lent", "law_slow_time"), (r"gravid|peso|gravity", "law_gravity"),
            (r"paisag|terreno|landscape|chao", "law_landscape"), (r"abert|\bopen\b|sem parede", "law_open_barrier")],
    "form": [(r"feixe|beam|laser", "beam"), (r"\bcone\b", "cone"), (r"corte|slash", "slash"),
             (r"\bnova\b|em volta", "nova"), (r"\bmarca|\bmark", "mark"), (r"\bonda|wave", "wave"),
             (r"queda|\bfall\b|meteor", "fall"), (r"chuva|\brain\b", "rain"), (r"zona|zone|circulo no chao", "zone"),
             (r"muro|wall|parede|barreira", "wall"), (r"runa|rune|armadilha|trap", "rune"), (r"pulso|pulse", "pulse"),
             (r"orbit|gira em volta", "orbit"), (r"aura", "aura"), (r"selo|seal|\barma\b", "seal"),
             (r"torre|turret|cristal que dispara", "turret"), (r"invoc|espirit|summon", "conjure"),
             (r"ilus|clone", "illusion"), (r"portal|portao|gate", "portal"),
             (r"dominio|domain|cupula", "domain_expansion"), (r"toque|touch", "touch"), (r"si mesmo|\bself\b", "self"),
             (r"esfera|sphere|orbe", "spiral_sphere"), (r"lanca|lance|\bceu\b", "sky_lances"), (r"proj", "projectile")],
    "augment": [(r"tudo ou nada|todo o mana", "all_in"), (r"colossal|gigant", "colossal"),
                (r"dividir|split|multipl|\bx ?\d|tripl|dupl", "split"), (r"perfur|pierce|atravessa", "pierce"),
                (r"ricoch|quica", "ricochet"), (r"encade|\bchain|corrent|insaci", "chain"),
                (r"teleguiad|homing|persegu", "homing"), (r"fantasma|phantom|paredes", "phantom"),
                (r"atraso|delay|espera", "delay"), (r"\beco\b|echo|repet", "echo"), (r"critic", "critical"),
                (r"vampir|roub.*vida|drena", "vampiric"), (r"eficien|economi|menos mana", "efficient"),
                (r"silenc|silent", "silent"), (r"sorte|fortune", "fortune"), (r"carreg|charge", "charge"),
                (r"canal|channel", "channel"), (r"inverte|invert", "invert"), (r"inimig|enem", "enemies_only"),
                (r"alia|ally|allies", "allies_only"), (r"veloc|speed|acel|rapid", "accelerate"),
                (r"lent|slow|desacel", "decelerate"), (r"durac|extend|prolong", "extend"),
                (r"encurt|shorten|reduz", "shorten"), (r"area|expand|raio maior", "expand"),
                (r"concentr|foco", "concentrate"), (r"dano|power|poder|ampli|forc", "amplify"),
                (r"enfraq|dampen|menos dano", "dampen")],
}
KEYWORDS = [
    # v0.8 (antes das gerais: ids novos de contramagica/lendas caem no desenho certo)
    (r"contramag|counter|responde", "counterspell"), (r"infinit|mugen", "infinity"), (r"atomic|atomo|nuclear", "atomic"),
    (r"desintegr|disintegr", "disintegration"), (r"cabra|\bgoat", "goatify"), (r"supernova", "supernova"),
    (r"vulcao|volcano", "volcano"), (r"buraco negro|black hole|singular", "black_hole"),
    (r"kraken|polvo", "kraken_grasp"), (r"exil|\bexile", "exile"), (r"copi|\bfork|duplic", "fork"),
    (r"explos|bomb|detona", "explosion"), (r"meteor|cometa", "meteor"),
    (r"chama|fogo|\bfire\b|flame|queim|incend|brasa", "ignite"), (r"lava|magma|vulc", "eruption"),
    (r"vapor|steam|nevoa quente", "hot_mist"), (r"raio|trov|lightning|thunder|eletr|choque", "lightning_bolt"),
    (r"gelo|\bice\b|frost|congel|neve|snow|gelid", "freeze"), (r"chuva|\brain\b", "local_rain"),
    (r"\bonda|wave|\bmare\b|tide", "tide"), (r"agua|water|\bgota|\bdrop\b|aqua", "soak"), (r"bolha|bubble", "bubble"),
    (r"pedra|rocha|\brock|stone|terra\b|earth", "rock"), (r"areia|\bsand|duna", "dune"),
    (r"cristal|crystal|\bgema|\bgem\b|prisma", "crystallize"), (r"vento|wind|\bar\b|\bair\b|rajada|gust", "gust"),
    (r"tornado|furac|ciclone", "tornado"),
    (r"corte|slash|lamina|blade|espada|sword|\barma\b|weapon", "conjured_weapon"),
    (r"lanca|spear|lance", "ice_lance"), (r"flecha|arrow|\bseta", "homing"),
    (r"escudo|shield|barreira|barrier|protec", "shield"), (r"cura|heal|regener", "heal"),
    (r"\bluz\b|light|sagrad|holy|divin", "holy_ray"), (r"\bsol\b|\bsun\b|solar", "sunlight"),
    (r"\blua\b|moon|noite|night|pesadelo", "nightmare"), (r"estrela|\bstar|astral|cosm", "falling_star"),
    (r"sombra|shadow|trev|\bdark|escur", "darkness"), (r"maldi|curse|\bhex\b|praga", "curse"),
    (r"veneno|poison|venom|toxi", "venom"), (r"caveira|skull|necro|morto|\bdead|undead|zumbi", "on_kill"),
    (r"\bosso|bone|esquelet", "bone_shield"), (r"\balma\b|\bsoul|espirito|spirit|fantasma|ghost", "conjure"),
    (r"demon|inferno|\bhell", "summon_demon"), (r"vazio|\bvoid\b|apag|erase|aniquil", "erase"),
    (r"gravid|gravity|\bpeso\b|weight|esmag", "crush"), (r"buraco negro|black hole", "attraction_point"),
    (r"portal|teleport|telet|warp", "portal"), (r"tempo|\btime\b|relog|clock", "delay"),
    (r"\bolho|\beye|detect|revel", "reveal"), (r"cogumelo|mushroom|esporo|spore", "spores"),
    (r"vinha|\bvine|\braiz|\broot|espinho|thorn", "vines"),
    (r"planta|\bplant|folha|\bleaf|natur|\bflor\b|flower|semente|seed|colh|harvest|cresc|grow", "growth"),
    (r"lobo|wolf|urso|bear|fera|beast|animal", "wolf_form"), (r"aranha|spider", "spider_form"),
    (r"dragao|dragon|serpe|wyvern|\bave\b|bird|falc", "falcon_form"),
    (r"invoc|summon|criatura|familiar", "conjure"), (r"corrente|\bchain|\belo\b|acorrent", "chain"),
    (r"empurr|\bpush|repel", "push"), (r"puxa|\bpull", "pull"), (r"veloc|speed|rapid|swift", "swiftness"),
    (r"\blent|\bslow", "slow_time"), (r"\bpulo|salto|jump|leap", "double_jump"),
    (r"\bvoo\b|voar|\bfly|\basa\b|wing|plana", "glide"), (r"\bmana\b", "mana_gift"),
    (r"silenc|silent|\bmudo", "silence"), (r"atordo|\bstun|paralis", "stun"), (r"\bmedo\b|fear|terror", "fear"),
    (r"zona|zone|\barea\b|campo|field|dominio|domain", "zone"), (r"aura", "aura"), (r"feixe|beam|laser", "beam"),
    (r"proj|\borbe|\borb\b|\bbola|ball|missil|missile", "projectile"), (r"toque|touch|\bmao\b|hand", "touch"),
    (r"\bvida\b|\blife\b|coracao|heart", "heal"), (r"ampli|poder|power|forc", "amplify"),
]
# v1.0: temas das 10 classes novas - testados ANTES de tudo (inclusive das listas por tipo), para o glifo de origem
# de um modulo de classe cair no desenho certo. Os dois primeiros sao os encantamentos obrigatorios do Encantador.
KEYWORDS_V10 = [
    # \b no comeco das palavras curtas: "gun" nao pode pegar "seGUNdos", "spin" nao pega "eSPINho", "unha" nao pega
    # "pUNHAl", "dado" nao pega "cuiDADO"/"solDADO", "carta" nao pega "desCARTAr", "bet" nao pega "alfaBETo"
    (r"anti.?magi|antimagia|anula(r)? (a |as )?magia|magias nao funcionam|imun\w* a magia|spell immun", "v10_anti_magic"),
    (r"restricao celestial|heavenly restriction|\btoji\b|abre mao d[ao] (mana|magia)", "v10_heavenly_restriction"),
    (r"zanpakuto|shikai|bankai|getsuga|\bkatana", "v10_zanpakuto"),
    (r"mundo interior|inner world|meditac|\bmeditar", "v10_inner_world"),
    (r"quincy|heilig|arco espiritual|spirit bow|\breishi", "v10_spirit_bow"),
    (r"\babsorv|\babsorb|auswahl|\bdevolv", "v10_absorb"),
    (r"\bunhas?\b|\bnails?\b|\btusk\b|\bato (?:[1-4]|i{1,3}|iv)\b|\bact (?:[1-4]|i{1,3}|iv)\b", "v10_spin_nail"),
    (r"\brotac|\bspin|\bgiro|\bgirat|steel ?ball|bola de aco", "v10_spin_ball"),
    (r"\broleta|roulette|\bcassino|\bcasino|jackpot|caca.?niquel|\bslots?\b", "v10_roulette"),
    (r"\bdados?\b|\bdice\b|\bsorte\b|\bluck|\baposta|\bbet\b|\bgambl", "v10_dice"),
    (r"\bcartas?\b|\bcards?\b|\bbaralho|\bdeck\b", "v10_card"),
    (r"\bguns?\b|\bgunslinger|\bbalas?\b|\bbullets?\b|\brevolver|\bpistol|\bescopeta|\bshotgun|\bfuzil|\brifle|"
     r"\btiros?\b|\bdisparo|\brecarg|\breload", "v10_bullet"),
    (r"\baguia|\beagle|\bcompanheir|\bcompanion", "v10_eagle"),
    (r"\brun[ao]s?\b|\brunes?\b|\brunic|\bexcalibur|\bcaliburn|espada do lago", "v10_rune_blade"),
    (r"\broub|\bsteal|\bpilhag|\bbolsos?\b|pickpocket", "v10_steal"),
    (r"\bforj|\bforge|\bbigorna|\banvil|\bmartel|\bhammer|\bferreir|\bsmith", "v10_forge"),
    (r"\bencant|\benchant|\bimbu|bencao do aliado|\bsobrecarga", "v10_enchant_seal"),
]
# v1.0: classe dona de cada pictograma tematico - um glifo de origem de uma classe tenta PRIMEIRO os desenhos da
# propria classe (ex.: "Lamina de forja runica" do Ferreiro vira martelo e bigorna, nao a espada do Cavaleiro)
V10_PIC_CLASS = {"v10_anti_magic": "enchanter", "v10_heavenly_restriction": "enchanter",
                 "v10_enchant_seal": "enchanter", "v10_zanpakuto": "shinigami", "v10_inner_world": "shinigami",
                 "v10_spirit_bow": "quincy", "v10_absorb": "quincy", "v10_spin_nail": "steelball",
                 "v10_spin_ball": "steelball", "v10_roulette": "gambler", "v10_dice": "gambler", "v10_card": "gambler",
                 "v10_bullet": "gunslinger", "v10_eagle": "ranger", "v10_rune_blade": "knight", "v10_steal": "rogue",
                 "v10_forge": "smith"}


def keyword_order(kind, origin=None):
    """v1.0: ordem das palavras-chave. Glifo de origem de uma classe: desenhos da propria classe, depois os das outras
    classes, depois os gerais. Glifo de fora das classes: os gerais primeiro e os tematicos so como ultimo recurso
    (uma descricao comum com "companheiros" ou "devolve" nao vira aguia/absorcao)."""
    general = KIND_KEYWORDS.get(kind, []) + KEYWORDS
    if origin:
        own = [kw for kw in KEYWORDS_V10 if V10_PIC_CLASS.get(kw[1]) == origin]
        return own + [kw for kw in KEYWORDS_V10 if kw not in own] + general
    return general + KEYWORDS_V10


KIND_DEFAULT = {"form": "projectile", "augment": "amplify", "trigger": "then", "link": "link_hit",
                "law": "law_absolute", "effect": "arcane_damage"}


def plain(s):
    """Minusculas sem acentos (as regex acima nao usam acento)."""
    import unicodedata
    return "".join(ch for ch in unicodedata.normalize("NFD", s.lower()) if unicodedata.category(ch) != "Mn")


def guess_kind(gid, text):
    if gid.startswith("link_"):
        return "link"
    if gid.startswith("law_"):
        return "law"
    if gid.startswith("trigger_") or gid.startswith("on_"):
        return "trigger"
    t = text
    if re.search(r"\bligacao\b|\bliga dois estagios\b|proximo estagio", t):
        return "link"
    if re.search(r"\blei\b|\blaw\b", t):
        return "law"
    if re.search(r"^forma\b|\bforma:|\b(forma|form)\b.*(dispara|lanca|cria)", t):
        return "form"
    if re.search(r"\baumento\b|\baugment\b|modifica (a|o) (magia|forma|efeito)|multiplica|\bx ?\d", t):
        return "augment"
    if re.search(r"\bquando\b|\bao (ser|levar|matar|acertar|bloquear)\b|\bgatilho\b|\btrigger\b", t):
        return "trigger"
    return "effect"


def guess_elem(text):
    order = [("hellfire", r"infern|hellfire|chama negra"), ("magma", r"magma|lava|vulc"), ("steam", r"vapor|steam"),
             ("lightning", r"raio|trov|eletr|lightning|thunder"), ("sun", r"\bsol\b|solar|\bsun\b"),
             ("nature", r"natur|planta|\bflor\b|folha|vinha|esporo"), ("ice", r"gelo|gelid|\bice\b|neve|congel|frost"),
             ("purity", r"pureza|purity|purific"), ("abyss", r"abism|abyss|tentac|tinta"),
             ("sand", r"areia|\bsand|duna"), ("crystal", r"cristal|crystal"), ("gravity", r"gravid|gravity"),
             ("astral", r"astral|estrela|\bstar\b|cosm"), ("void", r"vazio|\bvoid\b"),
             ("twilight", r"crepusc|twilight|eclips"),
             ("fire", r"fogo|\bfire\b|chama|queim|meteor|cometa|explos|brasa|incend"),
             ("water", r"agua|water|chuva|\bmar\b"), ("earth", r"terra\b|earth|pedra|rocha"),
             ("wind", r"vento|wind|\bar\b"), ("light", r"\bluz\b|light|sagrad|holy"),
             ("shadow", r"sombra|shadow|trevas|escurid")]
    for e, rx in order:
        if re.search(rx, text):
            return e
    return "arcane"


def guess_glyph(gid, name="", desc="", used=None, origin=None):
    """Glifo sem entrada na tabela: deduz tipo, elemento e pictograma (+ selo de variacao). Nunca falha.
    'used' = assinaturas (pictograma, selos) ja usadas: o selo muda ate o icone ficar unico.
    'origin' (v1.0) = id da classe dona do glifo (pasta class_<id>): os desenhos dessa classe tem prioridade.
    O id + nome sao testados ANTES da descricao (o nome e mais especifico; a descricao da Restricao Celestial cita
    "magia" e nao pode virar Anti-Magia)."""
    text = plain(gid + " " + gid.replace("_", " ") + " " + name + " " + desc)
    kind = guess_kind(gid, text)
    elem = guess_elem(text)
    base = None
    head = plain(gid + " " + gid.replace("_", " ") + " " + name)
    order = keyword_order(kind, origin)
    for t in (head, text):
        for rx, p in order:
            if p in PICS and re.search(rx, t):
                base = p
                break
        if base is not None:
            break
    if base is None:
        base = KIND_DEFAULT.get(kind, "arcane_damage")
    if base not in PICS:
        base = next(iter(PICS))
    h = int(hashlib.md5(gid.encode("utf-8")).hexdigest(), 16)
    used = used if used is not None else set()
    n = len(BADGE_CYCLE)
    # v1.0: o primeiro uso de um desenho sai LIMPO (sem selo); so as repeticoes ganham o selo que as diferencia
    options = [[]] + [[BADGE_CYCLE[(h + i) % n]] for i in range(n)]
    options += [[(BADGE_CYCLE[(h + i) % n], "bl")] for i in range(n)]
    options += [[BADGE_CYCLE[(h + i) % n], (BADGE_CYCLE[(h + j) % n], "bl")] for i in range(n) for j in range(n)]
    badges = options[0]
    for opt in options:
        if (base, tuple(opt)) not in used:
            badges = opt
            break
    return {"kind": kind, "elem": elem, "pic": base, "legend": False, "badges": badges, "guessed": True}


BADGE_CYCLE = ["b_spark", "b_plus", "b_up", "b_dot", "b_ring", "b_star", "b_diamond", "b_wave"]


# =====================================================================================================  SELOS (11x11)
@badge("b_plus")
def _(d):
    plus(d, 5.5, 5.5, 4.4, 2.6, "W")


@badge("b_minus")
def _(d):
    d.rect(1, 4, 9, 6, "W")


@badge("b_up")
def _(d):
    d.arrow(5.5, 10, 5.5, 0.6, 2.2, 3.6, "W")


@badge("b_down")
def _(d):
    d.arrow(5.5, 0.6, 5.5, 10.2, 2.2, 3.6, "W")


@badge("b_spark")
def _(d):
    sparkle(d, 5.5, 5.5, 5.0, "yellow")


@badge("b_dot")
def _(d):
    orb(d, 5.5, 5.5, 3.6, "p")


@badge("b_ring")
def _(d):
    d.ring(5.5, 5.5, 4.4, 2.6, "W")


@badge("b_star")
def _(d):
    d.star(5.5, 5.8, 5.0, 2.2, 5, -90, "gold")


@badge("b_diamond")
def _(d):
    d.poly([(5.5, 0.8), (10.0, 5.5), (5.5, 10.2), (1.0, 5.5)], "cyan")


@badge("b_wave")
def _(d):
    waves(d, (3.5, 7.5), "aqua", 1.0, 1.6, 0.5, 10.5, period=4.0)


@badge("b_x")
def _(d):
    d.line(1.5, 1.5, 9.5, 9.5, 2.2, "red")
    d.line(9.5, 1.5, 1.5, 9.5, 2.2, "red")


# =====================================================================================================  COLETA DE IDS
GLYPH_SOURCE = {}   # v1.0: id -> pasta de traducao onde o glifo apareceu (ex.: "class_enchanter")


def class_of_source(folder):
    """v1.0: pasta de traducao de um modulo de classe (class_<id>) -> id da classe (cor de origem), ou None."""
    m = re.fullmatch(r"class_([a-z0-9_]+)", folder or "")
    return m.group(1) if m and m.group(1) in CLASS_CATEGORY and m.group(1) in ECOLOR else None


def lang_glyphs():
    """{id: (nome, descricao)} de todos os src/main/lang/*/pt_br.json. Tambem preenche GLYPH_SOURCE."""
    out = {}
    GLYPH_SOURCE.clear()
    for f in sorted(glob.glob(os.path.join(LANG, "*", "pt_br.json"))):
        try:
            with open(f, encoding="utf-8") as fh:
                data = json.load(fh)
        except Exception as ex:  # noqa: BLE001
            print("aviso: nao li", f, ex)
            continue
        for k, v in data.items():
            m = re.fullmatch(r"glyph\.isekai_mahou\.([a-z0-9_]+)", k)
            if m:
                gid = m.group(1)
                desc = data.get(k + ".desc", "")
                out[gid] = (str(v), str(desc))
                folder = os.path.basename(os.path.dirname(f))
                if gid not in GLYPH_SOURCE or class_of_source(folder):
                    GLYPH_SOURCE[gid] = folder
    return out


def build_specs():
    names = lang_glyphs()
    aliased = {PIC_ALIASES[a] for a in names if a in PIC_ALIASES and a not in GLYPHS}
    ids = sorted((set(names) | set(GLYPHS)) - {t for t in aliased if t not in names})
    specs, guessed = {}, []
    used = {(g, tuple(GLYPHS[g].get("badges", []))) for g in GLYPHS if g in PICS}
    for gid in ids:
        if gid in GLYPHS and gid in PICS:
            s = dict(GLYPHS[gid])
            s["pic"] = gid
        elif gid in PIC_ALIASES and PIC_ALIASES[gid] in GLYPHS and PIC_ALIASES[gid] not in names:
            # v0.9: id alternativo de um desenho novo (ex.: a Investida sem poder usar "charge")
            s = dict(GLYPHS[PIC_ALIASES[gid]])
            s["pic"] = PIC_ALIASES[gid]
        else:
            n, ds = names.get(gid, ("", ""))
            origin = class_of_source(GLYPH_SOURCE.get(gid))
            s = guess_glyph(gid, n, ds, used, origin)
            if origin and gid not in GLYPHS:
                s["elem"] = origin       # v1.0: glifo de origem de um modulo de classe = gema na cor da classe
            used.add((s["pic"], tuple(s["badges"])))
            if gid in GLYPHS:          # na tabela mas sem pictograma proprio: herda tipo/elemento
                s["kind"], s["elem"], s["legend"] = GLYPHS[gid]["kind"], GLYPHS[gid]["elem"], GLYPHS[gid]["legend"]
            guessed.append(gid)
        # v1.0: um id antigo da tabela com cor de CLASSE (ex.: "backstab" do Ladino da v0.9, "riposte" do Guerreiro
        # que saiu) reaproveitado por um modulo de classe da v1.0 passa a usar a cor da classe que o traduz agora
        origin = class_of_source(GLYPH_SOURCE.get(gid))
        if origin and s["elem"] in CLASS_IDS and s["elem"] != origin:
            s["elem"] = origin
        specs[gid] = s
    return specs, guessed, names


KIND_ORDER = {k: i for i, k in enumerate(KINDS)}
ELEM_ORDER = {e: i for i, e in enumerate(ELEMENTS + CLASS_IDS)}


def sort_key(item):
    gid, s = item
    return (KIND_ORDER.get(s["kind"], 9), ELEM_ORDER.get(s["elem"], 99), gid)


def main():
    specs, guessed, names = build_specs()
    todo = [(g, s) for g, s in sorted(specs.items(), key=sort_key) if not ONLY or g in ONLY]
    out_dir = os.path.join(GUI, "glyph")
    icons = []
    hashes = {}
    for gid, s in todo:
        img = make_glyph_icon(s)
        icons.append((gid, img))
        h = hashlib.md5(repr(img).encode()).hexdigest()
        hashes.setdefault(h, []).append(gid)
        if not ONLY:
            save_px(img, os.path.join(out_dir, gid + ".png"))
    dups = [v for v in hashes.values() if len(v) > 1]
    # pictogramas repetidos (mesmo desenho base + mesmos selos) tambem contam como repeticao
    sig = {}
    for gid, s in todo:
        sig.setdefault((s["pic"], tuple(s.get("badges", []))), []).append(gid)
    pic_dups = [v for v in sig.values() if len(v) > 1]

    extra = {}
    if not ONLY:
        for name, fn in (("element", "make_element_icons"), ("glyph_kind", "make_kind_icons"), ("tab", "make_tab_icons"),
                         ("faction", "make_faction_icons"), ("stack", "make_stack_icons"),
                         ("class", "make_class_icons"), ("category", "make_category_icons"),
                         ("resource", "make_resource_icons"), ("race", "make_race_icons"),
                         ("social", "make_social_icons")):
            if fn in globals():
                extra[name] = globals()[fn]()
                for iid, img in extra[name]:
                    save_px(img, os.path.join(GUI, name, iid + ".png"))
        # v1.0: apaga os retratos das classes da v0.9 que sairam (warrior, paladin, barbarian, monk, bard, artificer,
        # hunter...) - o Java cai na reserva se pedir um id que nao existe mais
        removed = []
        for f in sorted(glob.glob(os.path.join(GUI, "class", "*.png"))):
            if os.path.splitext(os.path.basename(f))[0] not in CLASS_FILE_IDS:
                os.remove(f)
                removed.append(os.path.basename(f))
        if removed:
            print("class: apagados os retratos antigos:", ", ".join(removed))
        # v0.8: icones 18x18 dos efeitos novos do nucleo (Em Chamas, Gelado, Eletrizado, Sangrando, Petrificacao)
        extra["mob_effect"] = make_effect_icons()
        for iid, img in extra["mob_effect"]:
            save_px(img, os.path.join(os.path.dirname(GUI), "mob_effect", iid + ".png"))
        # propostas para os efeitos antigos (bolinhas): so na previa, a menos que pecam --core-effects
        extra["core_effect"] = make_effect_icons(CORE_EFFECTS_PROPOSED)
        if CORE_EFFECTS:
            for iid, img in extra["core_effect"]:
                save_px(img, os.path.join(os.path.dirname(GUI), "mob_effect", iid + ".png"))

    os.makedirs(PREVIEW_DIR, exist_ok=True)
    sheet(icons, 2, 14, os.path.join(PREVIEW_DIR, "icons_preview.png"))
    sheet_small(icons, extra, os.path.join(PREVIEW_DIR, "icons_preview_small.png"))
    if extra:
        allx = []
        for name in ("element", "glyph_kind", "tab"):
            allx += extra.get(name, [])
        sheet(allx, 4, 11, os.path.join(PREVIEW_DIR, "icons_preview_ui.png"))
    # v0.8: folha so com o que e novo (glifos 3x; brasoes, efeitos, pilha e aba 4x) para revisar de perto
    new_icons = [(g, img) for g, img in icons if g in V08_IDS]
    if new_icons:
        sheet(new_icons, 3, 10, os.path.join(PREVIEW_DIR, "icons_preview_v08.png"))
    if extra:
        v08_ui = (extra.get("faction", []) + extra.get("mob_effect", []) + extra.get("stack", [])
                  + [t for t in extra.get("tab", []) if t[0] == "reputation"])
        sheet(v08_ui, 4, 9, os.path.join(PREVIEW_DIR, "icons_preview_v08_ui.png"))
        if extra.get("core_effect"):
            sheet(extra["mob_effect"] + extra["core_effect"], 4, 7,
                  os.path.join(PREVIEW_DIR, "icons_preview_v08_core_effects.png"), bg=rgb("C6C6C6"))
    # v0.9: so o que e novo - glifos 3x; retratos das classes, categorias, recursos e abas novas 4x
    new09 = [(g, img) for g, img in icons if specs[g]["pic"] in V09_IDS]
    if new09:
        sheet(new09, 3, 10, os.path.join(PREVIEW_DIR, "icons_preview_v09.png"))
    if extra:
        v09_ui = (extra.get("class", []) + extra.get("category", []) + extra.get("resource", [])
                  + [t for t in extra.get("tab", []) if t[0] in ("travel", "class_trials")])
        sheet(v09_ui, 4, 8, os.path.join(PREVIEW_DIR, "icons_preview_v09_ui.png"))
        # tamanho real sobre o fundo escuro do jogo (1x e 2x) para conferir a leitura pequena
        sheet(extra.get("class", []), 2, 15, os.path.join(PREVIEW_DIR, "icons_preview_v09_classes_2x.png"), label=False,
              cell_pad=4)
    # v1.0: retratos das 15 classes (5 Magicas + 5 Fisicas + 5 Destreza), categorias, racas e social - 4x e real
    if extra:
        v10_ui = (extra.get("class", []) + [c for c in extra.get("category", []) if c[0] in ("magic", "physical",
                                                                                            "dexterity")]
                  + extra.get("race", []) + extra.get("social", []))
        sheet(v10_ui, 4, 8, os.path.join(PREVIEW_DIR, "icons_preview_v10_ui.png"))
        sheet(extra.get("class", []) + extra.get("race", []), 2, 11,
              os.path.join(PREVIEW_DIR, "icons_preview_v10_portraits_2x.png"), label=False, cell_pad=4)
    if DEBUG_DIR:
        groups = {}
        for gid, img in icons:
            s = specs[gid]
            groups.setdefault(s["kind"] if s["kind"] != "effect" else "effect_" + s["elem"], []).append((gid, img))
        page, n = [], 0
        for key in sorted(groups, key=lambda k: (KIND_ORDER.get(k.split("_")[0], 9), k)):
            for it in groups[key]:
                page.append(it)
                if len(page) == 40:
                    sheet(page, 3, 10, os.path.join(DEBUG_DIR, "dbg_%02d.png" % n))
                    page, n = [], n + 1
        if page:
            sheet(page, 3, 10, os.path.join(DEBUG_DIR, "dbg_%02d.png" % n))

    print("glifos: %d icones (%s)" % (len(icons), ", ".join("%s=%d" % (k, sum(1 for _, s in todo if s["kind"] == k))
                                                            for k in KINDS)))
    for name in ("element", "glyph_kind", "tab", "faction", "stack", "mob_effect", "class", "category", "resource",
                 "race", "social"):
        if name in extra:
            print("%s: %d icones" % (name, len(extra[name])))
    v08_missing = [g for g in V08_IDS if g not in PICS]
    print("v0.8: %d glifos novos/redesenhados com pictograma proprio%s" % (
        len(V08_IDS) - len(v08_missing), (" | SEM pictograma: " + ", ".join(v08_missing)) if v08_missing else ""))
    v09_missing = [g for g in V09_IDS if g not in PICS]
    print("v0.9: %d glifos novos com pictograma proprio%s" % (
        len(V09_IDS) - len(v09_missing), (" | SEM pictograma: " + ", ".join(v09_missing)) if v09_missing else ""))
    print("ids sem tabela explicita (palavras-chave): %s" % (", ".join(guessed) or "nenhum"))
    missing = [g for g in GLYPHS if g not in names]
    print("ids da tabela ainda sem traducao (planejados): %d" % len(missing))
    if dups:
        print("ATENCAO icones identicos:", dups)
    if pic_dups:
        print("ATENCAO pictogramas repetidos:", pic_dups)
    print("previas em", PREVIEW_DIR)


def sheet_small(icons, extra, path):
    """Tamanho real sobre fundo escuro (como no jogo), 24 por linha, e os icones de UI embaixo."""
    bg = rgb("101018")
    cols, cell = 24, 34
    rows = (len(icons) + cols - 1) // cols
    ui = []
    for name in ("element", "glyph_kind", "tab"):
        ui += [img for _, img in extra.get(name, [])]
    ui_rows = (len(ui) + 39) // 40
    W = cols * cell + 4
    H = rows * cell + 4 + ui_rows * 20 + (8 if ui else 0)
    px = [[bg] * W for _ in range(H)]
    for i, (_, img) in enumerate(icons):
        ox, oy = 3 + (i % cols) * cell, 3 + (i // cols) * cell
        for y in range(32):
            for x in range(32):
                c = img[y][x]
                if c[3]:
                    px[oy + y][ox + x] = blend(px[oy + y][ox + x], c, c[3] / 255)
    base = rows * cell + 10
    for i, img in enumerate(ui):
        ox, oy = 3 + (i % 40) * 20, base + (i // 40) * 20
        for y in range(16):
            for x in range(16):
                c = img[y][x]
                if c[3] and ox + x < W:
                    px[oy + y][ox + x] = blend(px[oy + y][ox + x], c, c[3] / 255)
    save_px(px, path)


if __name__ == "__main__":
    main()
