"""
Gerador de TODOS os assets/dados do modulo hueco_mundo (Mundo Oco + Reino das Fadas).
Uso:  py -I tools/gen/hueco_mundo.py
  - hueco_mundo_models.py : modelos GeckoLib, texturas (+ glowmask) e animacoes das criaturas e chefes
  - hueco_mundo_art.py    : texturas/modelos de blocos e itens, portais, projetil e ceu
  - hueco_mundo_world.py  : dimensoes, biomas, features, moldes (.nbt), estruturas, loot, receitas e tags
  - hueco_mundo_lang.py   : traducoes pt_br + en_us
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import hueco_mundo_art  # noqa: E402
import hueco_mundo_lang  # noqa: E402
import hueco_mundo_models  # noqa: E402
import hueco_mundo_world  # noqa: E402


def main():
    print("hueco_mundo:")
    hueco_mundo_models.build_all()
    hueco_mundo_art.build_all()
    hueco_mundo_world.build_all()
    hueco_mundo_lang.build_all()
    print("ok")


if __name__ == "__main__":
    main()
