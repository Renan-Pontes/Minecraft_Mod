"""
Pacote de tecnicas de anime do class_gambler (Dados do Destino, Goma Elastica, Textura Surpresa, Aposta Total e o
Dominio da Morte Ociosa).
Uso (na raiz do projeto):  py -I tools/gen/class_gambler_anime.py

Saidas:
  assets/isekai_mahou/textures/mob_effect/gambler_disguise.png      icone do efeito Disfarce
  assets/isekai_mahou/textures/entity/idle_death_gamble.png         o salao do Cassino repintado em neon (pachinko)
  src/main/lang/class_gambler/{pt_br,en_us}.json                     traducoes (roda o class_gambler_lang.py)
"""
import colorsys
import os
import runpy
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, hex_rgba, darken  # noqa: E402

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")


def H(h, a=255):
    return hex_rgba(h, a)


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


def neon(p):
    """Feltro verde -> roxo escuro; ouro -> verde neon; vermelho/rosa -> rosa choque; azul -> ciano; brancos ficam."""
    r, g, b, a = p
    if a == 0:
        return p
    hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    deg = hh * 360
    if s < 0.18:
        nh, ns, nv = hh, s, v
    elif 70 <= deg < 190:
        nh, ns, nv = 0.76, min(1.0, s * 1.1), v * 0.55
    elif 25 <= deg < 70:
        nh, ns, nv = 0.40, 0.85, min(1.0, v * 1.1)
    elif 190 <= deg < 280:
        nh, ns, nv = 0.50, 0.8, min(1.0, v * 1.05)
    else:
        nh, ns, nv = 0.90, 0.75, min(1.0, v * 1.1)
    nr, ng, nb = colorsys.hsv_to_rgb(nh, ns, nv)
    return (int(nr * 255), int(ng * 255), int(nb * 255), a)


def idle_death_texture():
    src = os.path.join(A, "textures/entity/casino_domain.png")
    w, h, px = read_png(src)
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            c.set(x, y, neon(px[y][x]))
    c.save(os.path.join(A, "textures/entity/idle_death_gamble.png"))


def disguise_icon():
    c = Canvas(18, 18)
    card, edge = H("f4eefc"), H("b05ae0")
    c.rect(3, 2, 12, 15, edge)
    c.rect(4, 3, 11, 14, card)
    # mascara de ilusao sobre a carta
    mask = H("ff6ec7")
    c.rect(5, 7, 14, 10, mask)
    c.set(7, 8, H("1a0f2a"))
    c.set(8, 8, H("1a0f2a"))
    c.set(11, 8, H("1a0f2a"))
    c.set(12, 8, H("1a0f2a"))
    c.rect(14, 6, 15, 7, darken(mask, 0.2))
    c.set(6, 12, edge)
    c.set(9, 4, edge)
    c.outline(H("1a0f2a"))
    os.makedirs(os.path.join(A, "textures/mob_effect"), exist_ok=True)
    c.save(os.path.join(A, "textures/mob_effect/gambler_disguise.png"))


def main():
    print("== class_gambler (anime)")
    disguise_icon()
    idle_death_texture()
    runpy.run_path(os.path.join(HERE, "class_gambler_lang.py"), run_name="__main__")
    print("ok")


if __name__ == "__main__":
    main()
