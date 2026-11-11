"""Traduções do módulo "farm" (pt_br principal + en_us).

Uso (na raiz do projeto):  py -I tools/gen/farm_lang.py
Gera src/main/lang/farm/pt_br.json e en_us.json.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "src/main/lang/farm")

# chave: (pt_br, en_us)
T = {}


def k(key, pt, en):
    T[key] = (pt, en)


# ---------------------------------------------------------------------------------------------- Itens e blocos
k("item.isekai_mahou.construction_wand", "Varinha de Construção", "Construction Wand")
k("block.isekai_mahou.construction_site", "Canteiro de Obras", "Construction Site")
k("block.isekai_mahou.arcane_stone_generator", "Gerador Mágico de Pedra", "Arcane Stone Generator")
k("block.isekai_mahou.accelerated_tree", "Árvore Acelerada", "Accelerated Tree")
k("block.isekai_mahou.arcane_hatchery", "Criadouro Arcano", "Arcane Hatchery")
k("block.isekai_mahou.magic_fish_pond", "Lago de Peixes Mágico", "Magic Fish Pond")
k("block.isekai_mahou.elemental_pedestal", "Pedestal Elemental", "Elemental Pedestal")
k("block.isekai_mahou.elemental_pedestal.of", "Pedestal Elemental de %s", "Elemental Pedestal of %s")

BLUEPRINTS = {
    "small_house": ("Casa Pequena", "Small House",
                    "Casa 7×7 com toras nos cantos, telhado de duas águas, janelas, baú, bancada e fornalha.",
                    "7×7 house with log corners, gable roof, windows, chest, crafting table and furnace."),
    "tower": ("Torre de Vigia", "Watchtower",
              "Torre de pedra 5×5 com 16 de altura: escada de mão, dois andares, seteiras e ameias com tochas.",
              "16-tall 5×5 stone tower: ladder, two floors, arrow slits and torch-lit battlements."),
    "barn": ("Celeiro", "Barn",
             "Celeiro 9×11 com estrutura de toras, portão triplo, baias, feno e lanternas na viga central.",
             "9×11 log-framed barn with a triple gate, stalls, hay and lanterns hanging from the ridge beam."),
    "bridge": ("Ponte", "Bridge",
               "Ponte 3×15 para a frente: vigas de tora, guarda-corpo, lanternas e pilares a cada 4 blocos.",
               "3×15 bridge going forward: log beams, railings, lanterns and pillars every 4 blocks."),
    "farm_plot": ("Horta", "Farm Plot",
                  "Horta 9×9: moldura de toras, terra arada com água no meio, cerca com portão e tochas.",
                  "9×9 plot: log frame, farmland around a water source, fence with gate and torches."),
}
for bid, (pt, en, dpt, den) in BLUEPRINTS.items():
    k("item.isekai_mahou.blueprint_" + bid, "Planta: " + pt, "Blueprint: " + en)
    k("farm.isekai_mahou.blueprint." + bid, pt, en)
    k("farm.isekai_mahou.blueprint." + bid + ".desc", dpt, den)
k("farm.isekai_mahou.blueprint.size", "Tamanho: %s×%s, altura %s", "Size: %s×%s, height %s")
k("farm.isekai_mahou.blueprint.materials", "Materiais:", "Materials:")
k("farm.isekai_mahou.blueprint.material_line", "%s× %s", "%s× %s")
k("farm.isekai_mahou.blueprint.help",
  "Clique direito no chão: monta o canteiro (a obra fica na sua frente). Ponha baús com os blocos perto da estaca.",
  "Right-click the ground: places the site (the build goes in front of you). Put chests with the blocks near the stake.")

MATS = {
    "air": ("Limpeza do terreno", "Clearing the ground"),
    "planks": ("Tábuas", "Planks"),
    "log": ("Toras", "Logs"),
    "cobble": ("Pedregulho", "Cobblestone"),
    "stone_bricks": ("Tijolos de pedra", "Stone bricks"),
    "glass_pane": ("Painéis de vidro", "Glass panes"),
    "slab": ("Lajes de madeira", "Wooden slabs"),
    "stairs": ("Escadas de madeira", "Wooden stairs"),
    "fence": ("Cercas de madeira", "Wooden fences"),
    "gate": ("Portões de cerca", "Fence gates"),
    "door": ("Portas de madeira", "Wooden doors"),
    "torch": ("Tochas", "Torches"),
    "lantern": ("Lanternas", "Lanterns"),
    "ladder": ("Escadas de mão", "Ladders"),
    "chest": ("Baú", "Chest"),
    "crafting": ("Bancada de trabalho", "Crafting table"),
    "furnace": ("Fornalha", "Furnace"),
    "hay": ("Fardos de feno", "Hay bales"),
    "farmland": ("Terra (vira terra arada)", "Dirt (becomes farmland)"),
    "water": ("Baldes d'água (o balde volta)", "Water buckets (bucket comes back)"),
}
for mid, (pt, en) in MATS.items():
    k("farm.isekai_mahou.mat." + mid, pt, en)

# ---------------------------------------------------------------------------------------------- Varinha
MODES = {
    "fill": ("Preencher", "Fill", "Enche toda a caixa marcada com o material.", "Fills the whole marked box with the material."),
    "wall": ("Parede", "Walls", "Levanta as 4 paredes da caixa (sem teto nem chão).", "Raises the 4 walls of the box (no roof or floor)."),
    "floor": ("Piso", "Floor", "Cobre a camada de baixo da caixa.", "Covers the bottom layer of the box."),
    "swap": ("Trocar", "Swap", "Troca os blocos do mesmo tipo do 1º canto pelo material.", "Swaps every block of the 1st corner's type for the material."),
    "copy": ("Copiar", "Copy", "Copia a caixa (até 16 de lado) para a prancheta.", "Copies the box (up to 16 per side) to the clipboard."),
    "paste": ("Colar", "Paste", "Cola a prancheta no bloco olhado, girando com você.", "Pastes the clipboard on the targeted block, rotated to face you."),
    "undo": ("Desfazer", "Undo", "Desfaz a última obra (devolve os blocos).", "Undoes your last build (gives the blocks back)."),
}
for mid, (pt, en, hpt, hen) in MODES.items():
    k("farm.isekai_mahou.wand.mode." + mid, pt, en)
    k("farm.isekai_mahou.wand.mode." + mid + ".hint", hpt, hen)
k("farm.isekai_mahou.wand.mode", "Modo: %s", "Mode: %s")
k("farm.isekai_mahou.wand.material", "Material: %s", "Material: %s")
k("farm.isekai_mahou.wand.bad_material", "Esse bloco não serve de material.", "That block can't be used as material.")
k("farm.isekai_mahou.wand.linked", "Baú ligado: %s (funciona a até %s blocos).", "Chest linked: %s (works up to %s blocks away).")
k("farm.isekai_mahou.wand.unlinked", "Baú desligado: %s.", "Chest unlinked: %s.")
k("farm.isekai_mahou.wand.link_at", "Baú ligado em %s %s %s", "Linked chest at %s %s %s")
k("farm.isekai_mahou.wand.corner1", "1º canto: %s %s %s", "1st corner: %s %s %s")
k("farm.isekai_mahou.wand.corner2", "Área: %s×%s×%s (%s blocos)", "Area: %s×%s×%s (%s blocks)")
k("farm.isekai_mahou.wand.too_big", "Área grande demais (máx. %s por lado e %s blocos).", "Area too big (max %s per side and %s blocks).")
k("farm.isekai_mahou.wand.far", "Você está longe demais da área marcada.", "You are too far from the marked area.")
k("farm.isekai_mahou.wand.no_selection", "Marque os dois cantos primeiro (clique em blocos).", "Mark both corners first (click blocks).")
k("farm.isekai_mahou.wand.no_material", "Sem material: segure um bloco na mão secundária ou agache + clique num bloco.",
  "No material: hold a block in your off hand or sneak + click a block.")
k("farm.isekai_mahou.wand.swap_same", "Trocar: o 1º canto precisa ser o tipo de bloco a trocar (e diferente do material).",
  "Swap: the 1st corner must be the block type to replace (and differ from the material).")
k("farm.isekai_mahou.wand.nothing", "Nada para fazer nessa área.", "Nothing to do in that area.")
k("farm.isekai_mahou.wand.copy_too_big", "Área grande demais para copiar (máx. %s de lado).", "Area too big to copy (max %s per side).")
k("farm.isekai_mahou.wand.copied", "Copiado: %s×%s×%s (%s blocos). Modo Colar ativado.", "Copied: %s×%s×%s (%s blocks). Paste mode on.")
k("farm.isekai_mahou.wand.no_clip", "Prancheta vazia: use o modo Copiar primeiro.", "Empty clipboard: use Copy mode first.")
k("farm.isekai_mahou.wand.paste_aim", "Mire num bloco para colar.", "Aim at a block to paste.")
k("farm.isekai_mahou.wand.clip", "Prancheta: %s×%s×%s", "Clipboard: %s×%s×%s")
k("farm.isekai_mahou.wand.no_undo", "Nada para desfazer.", "Nothing to undo.")
k("farm.isekai_mahou.wand.undo_dimension", "A última obra foi em outra dimensão.", "Your last build was in another dimension.")
k("farm.isekai_mahou.wand.undoing", "Desfazendo %s blocos...", "Undoing %s blocks...")
k("farm.isekai_mahou.wand.help1", "Clique num bloco: marca os cantos · Clique no ar: executa",
  "Click a block: mark corners · Click the air: run")
k("farm.isekai_mahou.wand.help2", "Agache + clique no ar: troca o modo · Agache + clique num bloco: material",
  "Sneak + click the air: change mode · Sneak + click a block: material")
k("farm.isekai_mahou.wand.help3", "Agache + clique num baú: liga o baú (blocos extras, até 64 blocos)",
  "Sneak + click a chest: link it (extra blocks, up to 64 blocks away)")
k("farm.isekai_mahou.wand.cost", "Custo: %s de mana por bloco + os blocos.", "Cost: %s mana per block + the blocks.")

k("farm.isekai_mahou.hud.mode", "Varinha: %s", "Wand: %s")
k("farm.isekai_mahou.hud.size", "%s×%s×%s · %s blocos", "%s×%s×%s · %s blocks")
k("farm.isekai_mahou.hud.material", "Material: %s", "Material: %s")
k("farm.isekai_mahou.hud.no_clip", "Prancheta vazia (use Copiar)", "Empty clipboard (use Copy)")
k("farm.isekai_mahou.hud.undo", "Clique para desfazer a última obra", "Click to undo your last build")
k("farm.isekai_mahou.hud.pick1", "Clique num bloco: 1º canto", "Click a block: 1st corner")
k("farm.isekai_mahou.hud.pick2", "Clique num bloco: 2º canto", "Click a block: 2nd corner")

k("farm.isekai_mahou.build.started", "Construindo %s blocos...", "Building %s blocks...")
k("farm.isekai_mahou.build.done", "Construção concluída: %s blocos.", "Build finished: %s blocks.")
k("farm.isekai_mahou.build.missing", "Faltou %s — a construção parou (o que já foi feito fica).",
  "Ran out of %s — the build stopped (what was done stays).")
k("farm.isekai_mahou.build.no_mana", "Sem %s — a construção parou.", "Out of %s — the build stopped.")
k("farm.isekai_mahou.build.busy", "Você já tem obras demais em andamento — espere terminar.", "You have too many builds running — wait for them to finish.")
k("farm.isekai_mahou.glyph.no_material", "Construção Arcana: segure o bloco na mão secundária (ou escolha um material na Varinha).",
  "Arcane Construction: hold the block in your off hand (or pick a material with the Wand).")

# ---------------------------------------------------------------------------------------------- Canteiro
k("farm.isekai_mahou.site.placed", "Canteiro de \"%s\" montado! Ponha baús com os materiais a até %s blocos da estaca.",
  "\"%s\" site placed! Put chests with the materials within %s blocks of the stake.")
k("farm.isekai_mahou.site.blocked", "Não dá para montar o canteiro aqui.", "Can't place the site here.")
k("farm.isekai_mahou.site.height", "A obra não cabe na altura do mundo aqui.", "The build doesn't fit in the world height here.")
k("farm.isekai_mahou.site.progress", "%s: %s%% pronto.", "%s: %s%% done.")
k("farm.isekai_mahou.site.missing", "Faltando: %s", "Missing: %s")
k("farm.isekai_mahou.site.no_chest", "Nenhum baú por perto: ponha baús com os materiais a até %s blocos da estaca.",
  "No chest nearby: put chests with the materials within %s blocks of the stake.")
k("farm.isekai_mahou.site.boosted", "Acelerado: uma Bateria de Mana a até %s blocos (2 de mana por bloco).",
  "Boosted: a Mana Battery within %s blocks (2 mana per block).")
k("farm.isekai_mahou.site.normal", "Dica: uma Bateria de Mana a até %s blocos acelera a obra.", "Tip: a Mana Battery within %s blocks speeds up the build.")
k("farm.isekai_mahou.site.done", "A obra \"%s\" ficou pronta! A planta voltou para o baú.", "\"%s\" is finished! The blueprint went back to the chest.")
k("farm.isekai_mahou.site.label", "%s — %s%%", "%s — %s%%")
k("farm.isekai_mahou.site.label_boost", "» Acelerado pela Bateria de Mana «", "» Boosted by a Mana Battery «")

# ---------------------------------------------------------------------------------------------- Fazendas
MACHINES = {
    "arcane_stone_generator": (
        "Gera pedra devagar com mana. Com uma essência no catalisador gera minérios: Terra (minérios), Fogo (Nether), Água (mar), Sombra (obsidiana e End).",
        "Slowly makes stone with mana. With an essence as catalyst it makes ores: Earth (ores), Fire (Nether), Water (sea), Shadow (obsidian and End).",
        "Catalisador (opcional): Essência de Terra, Fogo, Água ou Sombra — cada uma dura 6 ciclos.",
        "Catalyst (optional): Earth, Fire, Water or Shadow Essence — each lasts 6 cycles."),
    "accelerated_tree": (
        "Uma muda ou semente cresce e é colhida sozinha, de novo e de novo (toras, frutos, colheitas).",
        "A sapling or seed grows and is harvested by itself, again and again (logs, fruit, crops).",
        "Uma muda (qualquer árvore, fungos do Nether) ou semente/planta (trigo, cenoura, abóbora, cana, bambu...). Não é gasta.",
        "A sapling (any tree, Nether fungi) or a seed/plant (wheat, carrot, pumpkin, sugar cane, bamboo...). Not consumed."),
    "arcane_hatchery": (
        "Traga um animal na guia e clique: ele fica guardado e, alimentado, produz o que daria (couro, lã, ovos...).",
        "Bring an animal on a lead and click: it's kept inside and, when fed, produces its drops (leather, wool, eggs...).",
        "Comida do animal guardado (trigo, cenoura, sementes...) — 1 por ciclo.",
        "Food for the stored animal (wheat, carrots, seeds...) — 1 per cycle."),
    "magic_fish_pond": (
        "Um lago encantado que pesca sozinho: peixes, tralhas e, às vezes, tesouros.",
        "An enchanted pond that fishes by itself: fish, junk and sometimes treasure.",
        "Vara de pesca (opcional): Isca e Sorte do Mar valem; gasta durabilidade.",
        "Fishing rod (optional): Lure and Luck of the Sea apply; uses durability."),
}
for mid, (hpt, hen, ipt, ien) in MACHINES.items():
    k("farm.isekai_mahou.machine." + mid + ".hint", hpt, hen)
    k("farm.isekai_mahou.machine." + mid + ".input", ipt, ien)
k("farm.isekai_mahou.machine.elemental_pedestal.hint", "Gerador de Essência de %s: produz essências com o tempo, gastando mana.",
  "%s Essence Generator: makes essences over time, using mana.")
k("farm.isekai_mahou.machine.elemental_pedestal.input", "Núcleo do Guardião do mesmo elemento (opcional): gera 2× mais rápido. Não é gasto.",
  "Guardian Core of the same element (optional): 2× faster. Not consumed.")
k("farm.isekai_mahou.machine.elemental_pedestal.origin", "Roubado de um Santuário Elemental com Toque Suave.",
  "Stolen from an Elemental Shrine with Silk Touch.")
k("farm.isekai_mahou.machine.input_line", "Entrada: %s", "Input: %s")
k("farm.isekai_mahou.machine.mana_line",
  "Mana: Baterias de Mana a até 8 blocos, cristais no espaço de combustível, ou agache com a mão vazia para canalizar o seu.",
  "Mana: Mana Batteries within 8 blocks, crystals in the fuel slot, or sneak with an empty hand to channel your own.")
k("farm.isekai_mahou.machine.output_line", "A produção sai por funis/baús embaixo. Redstone pausa.", "Output goes into hoppers/chests below. Redstone pauses it.")
k("farm.isekai_mahou.machine.full_mana", "A reserva já está cheia.", "The reserve is already full.")
k("farm.isekai_mahou.machine.channeled", "Você canalizou %s de %s. Reserva: %s/%s", "You channeled %s %s. Reserve: %s/%s")
k("farm.isekai_mahou.machine.mana", "Reserva: %s / %s de mana", "Reserve: %s / %s mana")
k("farm.isekai_mahou.machine.batteries", "Baterias de Mana por perto: %s (alcance %s)", "Mana Batteries nearby: %s (range %s)")
k("farm.isekai_mahou.machine.channel_hint", "Agache com a mão vazia na máquina para canalizar o seu recurso.",
  "Sneak with an empty hand on the machine to channel your own resource.")
k("farm.isekai_mahou.machine.input_title", "Entrada", "Input")
k("farm.isekai_mahou.machine.fuel_title", "Combustível arcano (vira mana)", "Arcane fuel (turns into mana)")
k("farm.isekai_mahou.machine.fuel_list",
  "Cristal de Mana 600 · Bloco de Cristal 5400 · Ametista 80 · Lápis 50 · Pó de pedra luminosa 40 · Redstone 25",
  "Mana Crystal 600 · Crystal Block 5400 · Amethyst 80 · Lapis 50 · Glowstone dust 40 · Redstone 25")
k("farm.isekai_mahou.machine.fuel_bonemeal", "Farinha de osso: o próximo ciclo cresce 2× mais rápido.", "Bone meal: the next cycle grows 2× faster.")
k("farm.isekai_mahou.machine.time_left", "Faltam %s s", "%s s left")
k("farm.isekai_mahou.machine.status", "Estado: %s", "Status: %s")
STATUS = {
    "idle": ("Parada", "Idle"),
    "working": ("Trabalhando", "Working"),
    "no_mana": ("Sem mana", "No mana"),
    "no_input": ("Falta a entrada", "Missing input"),
    "full": ("Produção cheia", "Output full"),
    "paused": ("Pausada (redstone)", "Paused (redstone)"),
    "no_animal": ("Sem animal (traga um na guia)", "No animal (bring one on a lead)"),
}
for sid, (pt, en) in STATUS.items():
    k("farm.isekai_mahou.machine.status." + sid, pt, en)
k("farm.isekai_mahou.hatchery.captured", "%s guardado(a) no Criadouro! Alimente para produzir.", "%s stored in the Hatchery! Feed it to produce.")

# ---------------------------------------------------------------------------------------------- Roubo do pedestal
k("farm.isekai_mahou.heist.progress", "Desprendendo o pedestal... %s/%s", "Prying the pedestal loose... %s/%s")
k("farm.isekai_mahou.heist.success", "Você roubou o Pedestal Elemental de %s!", "You stole the %s Elemental Pedestal!")
k("farm.isekai_mahou.heist.success.hint",
  "Coloque-o na sua base e dê mana (Baterias de Mana por perto): ele gera essências com o tempo.",
  "Place it at your base and give it mana (Mana Batteries nearby): it makes essences over time.")
k("farm.isekai_mahou.heist.not_worthy", "O santuário de %s ainda não te reconhece: vença a prova do altar primeiro.",
  "The %s shrine doesn't recognize you yet: beat the altar's trial first.")
k("farm.isekai_mahou.heist.trial_active", "A prova está acontecendo — espere acabar.", "The trial is running — wait for it to end.")
k("farm.isekai_mahou.heist.protected", "Este lugar está protegido.", "This place is protected.")
k("farm.isekai_mahou.heist.need_silk", "O altar está selado à terra. Só o Toque Suave consegue soltá-lo.",
  "The altar is sealed to the ground. Only Silk Touch can pry it loose.")

# ---------------------------------------------------------------------------------------------- Glifos
GLYPHS = {
    "arcane_wall": ("Erguer Parede", "Raise Wall",
                    "Levanta uma parede do seu material onde acertar. Expandir: mais larga · Amplificar: mais alta. Usa blocos do inventário/baú ligado.",
                    "Raises a wall of your material where it hits. Expand: wider · Amplify: taller. Uses blocks from your inventory/linked chest."),
    "arcane_floor": ("Pavimentar", "Pave",
                     "Cobre o chão em volta do impacto com o material, só onde está vazio (pontes e plataformas rápidas). Expandir: área maior.",
                     "Covers the ground around the impact with the material, only where empty (quick bridges and platforms). Expand: bigger area."),
    "arcane_fill": ("Preencher", "Fill",
                    "Enche buracos, poços e lagos numa esfera abaixo do impacto com o material. Expandir: esfera maior.",
                    "Fills holes, pits and ponds in a sphere below the impact with the material. Expand: bigger sphere."),
    "arcane_transmute": ("Transmutar Blocos", "Transmute Blocks",
                         "Troca os blocos do mesmo tipo do atingido, em volta, pelo material; os velhos voltam como item. Expandir: área maior.",
                         "Swaps nearby blocks of the hit block's type for the material; the old ones come back as items. Expand: bigger area."),
    "arcane_extract": ("Extração Arcana", "Arcane Extraction",
                       "Recolhe o bloco atingido (e os iguais em volta com Expandir) direto para o inventário. Com Toque Suave sai inteiro — e arranca até o altar dos Santuários Elementais.",
                       "Pulls the hit block (and matching ones nearby with Expand) straight into your inventory. With Silk Touch it comes out whole — even the Elemental Shrine altars."),
}
for gid, (pt, en, dpt, den) in GLYPHS.items():
    k("glyph.isekai_mahou." + gid, pt, en)
    k("glyph.isekai_mahou." + gid + ".desc", dpt, den)


def main():
    os.makedirs(OUT, exist_ok=True)
    for idx, lang in ((0, "pt_br"), (1, "en_us")):
        data = {key: v[idx] for key, v in sorted(T.items())}
        with open(os.path.join(OUT, lang + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("farm: %d chaves" % len(T))


if __name__ == "__main__":
    main()
