"""
LOVE TRAIN (modulo class_steelball): regrava SO as traducoes do modulo e o icone do glifo novo, sem mexer no resto
dos assets (o class_steelball.py completo regrava itens, modelos, estrutura .nbt etc.).

Uso (na raiz do projeto):  py -I tools/gen/class_steelball_love_train.py

Gera:
  - src/main/lang/class_steelball/{pt_br,en_us}.json  (via class_steelball_lang.py);
  - assets/isekai_mahou/textures/gui/glyph/steelball_love_train.png  (32x32, moldura de FORMA na cor do Steel Ball,
    estrela de lendario; pictograma proprio: o Coracao do Santo no meio de laminas de luz com frestas e raios).
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen"))

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
LANG = os.path.join(ROOT, "src/main/lang/class_steelball")
GLYPH = "steelball_love_train"


def love_train_pic(d):
    """Pictograma 20x20: laminas verticais de luz (com frestas) atras, o Coracao no centro e um brilho em cima."""
    import icons as I
    # cortina: laminas finas de luz de alturas diferentes, com frestas entre elas (dos lados do Coracao)
    for i, (x, top, bot) in enumerate(((1, 5, 17), (4, 2, 19), (15, 2, 19), (18, 5, 17))):
        d.rect(x, top, x, bot, "yellow.l" if i in (0, 3) else "white", g=i)
    # brilho atras do Coracao e os raios que sobem
    d.ring(10.0, 10.6, 7.4, 6.5, "yellow.l", g=4)
    d.line(10.0, 0.0, 10.0, 3.0, 1.0, "W", g=5)
    d.line(7.0, 1.0, 8.0, 4.0, 0.6, "yellow.l", g=5)
    d.line(13.0, 1.0, 12.0, 4.0, 0.6, "yellow.l", g=5)
    # o Coracao do Cadaver Santo
    d.heart(10.0, 11.0, 1.0, "heart", g=6)
    d.disc(7.2, 8.6, 1.0, "W", g=7)
    I.sparkle(d, 15.8, 16.4, 2.2, "W", g=9)


def glyph_icon():
    sys.argv = sys.argv[:1]
    import icons as I  # noqa: E402
    I.PICS[GLYPH] = love_train_pic
    spec = {"kind": "form", "elem": "steelball", "pic": GLYPH, "legend": True, "badges": []}
    out = os.path.join(A, "textures/gui/glyph", GLYPH + ".png")
    I.save_px(I.make_glyph_icon(spec), out)
    print("  icone:", os.path.relpath(out, ROOT))


def main():
    import class_steelball_lang as lang
    print("== traducoes (class_steelball)")
    lang.write(LANG)
    print("== icone do Love Train")
    glyph_icon()
    print("ok")


if __name__ == "__main__":
    main()
