"""
Pequena biblioteca de pixel art procedural para o Isekai Mahou (sem dependencias externas).
Uso pelos agentes/modulos para gerar texturas PNG (itens 16x16, entidades 64x64, etc.).

    import sys; sys.path.insert(0, "tools"); from pixelart import Canvas
    c = Canvas(16, 16)
    c.circle(8, 8, 5, (255, 100, 40, 255)); c.shade_sphere(8, 8, 5)
    c.save("src/main/resources/assets/isekai_mahou/textures/item/foo.png")
"""
import struct
import zlib
import os
import math
import random


def hex_rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] * (1 - t) + c2[i] * t)) for i in range(4))


def lighten(c, f):
    return (min(255, int(c[0] + (255 - c[0]) * f)), min(255, int(c[1] + (255 - c[1]) * f)),
            min(255, int(c[2] + (255 - c[2]) * f)), c[3])


def darken(c, f):
    return (int(c[0] * (1 - f)), int(c[1] * (1 - f)), int(c[2] * (1 - f)), c[3])


class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [[fill for _ in range(w)] for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return (0, 0, 0, 0)

    def rect(self, x0, y0, x1, y1, c):
        """Retangulo preenchido, inclusivo."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def outline_rect(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.set(x, y0, c); self.set(x, y1, c)
        for y in range(y0, y1 + 1):
            self.set(x0, y, c); self.set(x1, y, c)

    def circle(self, cx, cy, r, c):
        for y in range(self.h):
            for x in range(self.w):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.set(x, y, c)

    def shade_sphere(self, cx, cy, r, light=0.45, dark=0.45):
        """Sombreia pixels ja pintados dentro do circulo como uma esfera (luz no canto superior esquerdo)."""
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r and self.px[y][x][3] > 0:
                    lx = (x + 0.5 - (cx - r * 0.35)) / r
                    ly = (y + 0.5 - (cy - r * 0.35)) / r
                    t = min(1.0, math.hypot(lx, ly))
                    base = self.px[y][x]
                    self.px[y][x] = lighten(base, light * (1 - t)) if t < 0.5 else darken(base, dark * (t - 0.5))

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy; x0 += sx
            if e2 <= dx:
                err += dx; y0 += sy

    def outline(self, c):
        """Contorno de 1px em volta de tudo que nao e transparente."""
        src = [row[:] for row in self.px]
        for y in range(self.h):
            for x in range(self.w):
                if src[y][x][3] == 0:
                    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + ox, y + oy
                        if 0 <= nx < self.w and 0 <= ny < self.h and src[ny][nx][3] > 0:
                            self.px[y][x] = c
                            break

    def noise(self, amount=0.08, seed=1):
        """Variacao leve de brilho nos pixels pintados (textura menos 'chapada')."""
        rnd = random.Random(seed)
        for y in range(self.h):
            for x in range(self.w):
                p = self.px[y][x]
                if p[3] > 0:
                    f = (rnd.random() - 0.5) * 2 * amount
                    self.px[y][x] = lighten(p, f) if f > 0 else darken(p, -f)

    def from_rows(self, rows, palette, ox=0, oy=0):
        """Desenha a partir de strings ('.' = transparente) e uma paleta {char: rgba}."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != "." and ch in palette:
                    self.set(ox + x, oy + y, palette[ch])

    def save(self, path):
        raw = b""
        for row in self.px:
            raw += b"\x00" + b"".join(bytes(p) for p in row)

        def chunk(t, d):
            return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)

        data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)


def item_model(assets_dir, item_id, texture=None, parent="minecraft:item/generated"):
    """Escreve models/item/<item_id>.json apontando para textures/item/<texture or item_id>."""
    import json
    p = os.path.join(assets_dir, "models", "item", item_id + ".json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump({"parent": parent, "textures": {"layer0": "isekai_mahou:item/" + (texture or item_id)}}, f, indent=2)
