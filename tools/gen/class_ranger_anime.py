"""
Pacote de tecnicas de anime do class_ranger (Flecha Sagrada, Disparo Triplo Elfico, Correntes Conjuradas, Linha de
Bala e o Despertar da Deusa Loba).
Uso (na raiz do projeto):  py -I tools/gen/class_ranger_anime.py

Saidas:
  assets/isekai_mahou/textures/entity/ranger_wolf_3.png (+ _glowmask)   a Deusa Loba: o Lobo Alfa repintado de branco,
                                                                          com as marcas brilhando em carmim
  src/main/lang/class_ranger/{pt_br,en_us}.json                         traducoes (todas as do modulo, regeneradas)
As traducoes novas ficam em tools/gen/class_ranger_anime_lang.py (incluidas pelo write_lang do class_ranger.py).
"""
import colorsys
import os
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
import class_ranger as CR  # noqa: E402  (so as funcoes: main() nao roda no import)
from pixelart import Canvas  # noqa: E402

TEX = os.path.join(CR.ASSETS, "textures/entity")


def read_png(path):
    """Le um PNG RGBA de 8 bits (o formato que o pixelart grava)."""
    data = open(path, "rb").read()
    pos, w, h, idat = 8, 0, 0, b""
    while pos < len(data):
        ln = int.from_bytes(data[pos:pos + 4], "big")
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h = int.from_bytes(body[0:4], "big"), int.from_bytes(body[4:8], "big")
            if body[8] != 8 or body[9] != 6:
                raise RuntimeError("PNG precisa ser RGBA 8 bits: " + path)
        elif typ == b"IDAT":
            idat += body
        pos += 12 + ln
    raw = zlib.decompress(idat)
    stride = w * 4
    px, prev, i = [], [0] * stride, 0
    for _ in range(h):
        ft = raw[i]
        i += 1
        line = list(raw[i:i + stride])
        i += stride
        for k in range(stride):
            a = line[k - 4] if k >= 4 else 0
            b = prev[k]
            c = prev[k - 4] if k >= 4 else 0
            if ft == 1:
                line[k] = (line[k] + a) & 255
            elif ft == 2:
                line[k] = (line[k] + b) & 255
            elif ft == 3:
                line[k] = (line[k] + (a + b) // 2) & 255
            elif ft == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[k] = (line[k] + pr) & 255
        px.append([tuple(line[x * 4:x * 4 + 4]) for x in range(w)])
        prev = line
    return w, h, px


def goddess(p):
    """Pelo -> branco/prata (mantendo o sombreado); cores fortes -> carmim (as marcas da deusa)."""
    r, g, b, a = p
    if a == 0:
        return p
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    if s > 0.45 and v > 0.45:
        nr, ng, nb = colorsys.hsv_to_rgb(0.98, 0.85, min(1.0, 0.55 + v * 0.45))
    else:
        lum = 0.62 + 0.38 * v
        nr, ng, nb = lum * 0.97, lum * 0.97, lum
    return (int(nr * 255), int(ng * 255), int(nb * 255), a)


def glow(p):
    r, g, b, a = p
    if a == 0:
        return p
    return (230, 60, 72, a)


def recolor(src, dst, fn):
    w, h, px = read_png(os.path.join(TEX, src))
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            c.set(x, y, fn(px[y][x]))
    c.save(os.path.join(TEX, dst))


def main():
    print("== class_ranger (anime): Deusa Loba")
    recolor("ranger_wolf_2.png", "ranger_wolf_3.png", goddess)
    recolor("ranger_wolf_2_glowmask.png", "ranger_wolf_3_glowmask.png", glow)
    CR.write_lang()
    print("ok")


if __name__ == "__main__":
    main()
