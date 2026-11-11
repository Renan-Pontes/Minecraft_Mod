"""
Pacote de tecnicas de anime do class_enchanter (Oito Portoes, Kaioken, Segunda/Quarta Marcha, Instinto Superior).
Uso (na raiz do projeto):  py -I tools/gen/class_enchanter_anime.py

Saidas:
  assets/isekai_mahou/textures/mob_effect/enchanter_<transformacao>.png   icones 18x18 dos 5 efeitos
  src/main/lang/class_enchanter/{pt_br,en_us}.json                         traducoes (todas as do modulo, regeneradas)
As traducoes novas ficam em tools/gen/class_enchanter_anime_lang.py (incluidas pelo class_enchanter_lang.py).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
import class_enchanter as CE  # noqa: E402  (so as funcoes: main() nao roda no import)
from pixelart import lighten, darken  # noqa: E402

H = CE.H


def _gates(c, b, l, d):
    # oito marcas (os portoes) em volta de um punho/chama verde
    import math
    for i in range(8):
        a = math.pi * 2 * i / 8
        x, y = int(round(8.5 + math.cos(a) * 6.5)), int(round(8.5 + math.sin(a) * 6.5))
        c.rect(x - 1, y - 1, x, y, l if i % 2 == 0 else b)
    c.circle(9, 9, 3.4, b)
    c.circle(9, 9, 2.0, l)
    c.set(9, 9, H("ffffff"))
    c.line(9, 12, 9, 15, d)


def _kaioken(c, b, l, d):
    # chama vermelha com o "×"
    for y in range(3, 17):
        w = max(1, int((y - 2) * 0.45))
        c.rect(9 - w, y, 9 + w, y, b if y > 7 else l)
    c.rect(8, 1, 9, 3, l)
    c.line(6, 8, 11, 13, H("ffffff"))
    c.line(11, 8, 6, 13, H("ffffff"))
    c.rect(4, 15, 13, 16, d)


def _gear2(c, b, l, d):
    # engrenagem rosada soltando vapor
    import math
    c.circle(9, 11, 5, b)
    for i in range(6):
        a = math.pi * 2 * i / 6
        x, y = int(round(9 + math.cos(a) * 6)), int(round(11 + math.sin(a) * 6))
        c.rect(x - 1, y - 1, x, y, b)
    c.circle(9, 11, 2, d)
    for x, y in ((4, 3), (6, 1), (12, 2), (14, 4)):
        c.rect(x, y, x + 1, y + 1, H("f4f4f4"))


def _gear4(c, b, l, d):
    # punho gigante preto e vermelho
    c.rect(3, 4, 14, 14, H("1a1a1a"))
    for x in (3, 6, 9, 12):
        c.rect(x, 2, x + 2, 5, H("2a2a2a"))
    c.rect(4, 7, 13, 8, b)
    c.rect(4, 11, 13, 12, b)
    c.rect(1, 8, 2, 13, d)
    c.set(5, 5, l)


def _ultra(c, b, l, d):
    # silhueta prateada com rastros
    c.circle(9, 4, 2.2, l)
    c.rect(7, 6, 11, 12, b)
    c.rect(7, 13, 8, 16, b)
    c.rect(10, 13, 11, 16, b)
    for y in (6, 9, 12, 15):
        c.rect(1, y, 4, y, lighten(b, 0.3))
        c.rect(14, y - 1, 16, y - 1, darken(b, 0.1))


EFFECTS = [
    ("enchanter_eight_gates", "6fe08a", _gates), ("enchanter_kaioken", "ff3b30", _kaioken),
    ("enchanter_gear_second", "ff8fa0", _gear2), ("enchanter_gear_fourth", "c8303c", _gear4),
    ("enchanter_ultra_instinct", "c8d4f0", _ultra),
]


def main():
    print("== class_enchanter (anime): icones dos efeitos")
    for name, col, fn in EFFECTS:
        CE.effect_icon(name, col, fn)
    CE.build_lang()
    print("ok")


if __name__ == "__main__":
    main()
