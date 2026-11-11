"""
Traducoes do modulo class_smith (Ferreiro / Forja Arcana): src/main/lang/class_smith/{pt_br,en_us}.json.
Uso (na raiz do projeto):  py -I tools/gen/class_smith_lang.py

Cada entrada e (chave, pt_br, en_us). O script confere que todo '%' e um marcador valido do Minecraft
(%s, %N$s ou %%) — um '%' solto quebra a traducao inteira no jogo.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src/main/lang/class_smith")

M = "message.isekai_mahou.class_smith."
S = "screen.isekai_mahou.class_smith."
T = "tooltip.isekai_mahou.class_smith."
G = "glyph.isekai_mahou."

E = []


def e(key, pt, en):
    E.append((key, pt, en))


# ------------------------------------------------------------------ itens, bloco, entidades, lendas, aba
e("item.isekai_mahou.smith_hammer", "Martelo do Ferreiro", "Smith's Hammer")
e("item.isekai_mahou.smith_hammer_t1", "Martelo do Aprendiz", "Apprentice's Hammer")
e("item.isekai_mahou.smith_hammer_t2", "Martelo do Artesão", "Artisan's Hammer")
e("item.isekai_mahou.smith_hammer_t3", "Martelo do Mestre da Bigorna", "Anvil Master's Hammer")
e("item.isekai_mahou.smith_hammer_t4", "Martelo Estelar", "Starforged Hammer")
e("item.isekai_mahou.smith_hammer.flavor", "Pesado, honesto e quente da fornalha.", "Heavy, honest and still warm from the furnace.")
e("item.isekai_mahou.smith_hammer.flavor_t1", "O primeiro martelo que é seu de verdade.", "The first hammer that is truly yours.")
e("item.isekai_mahou.smith_hammer.flavor_t2", "Runas no cabo: cada pancada lembra a forma certa.",
  "Runes on the haft: every blow remembers the right shape.")
e("item.isekai_mahou.smith_hammer.flavor_t3", "A bigorna canta quando ele bate.", "The anvil sings when it strikes.")
e("item.isekai_mahou.smith_hammer.flavor_t4", "Forjado com metal que caiu do céu. Ainda brilha à noite.",
  "Forged from metal that fell from the sky. It still glows at night.")
e("item.isekai_mahou.smith_forged_blade", "Lâmina Forjada", "Forged Blade")
e("item.isekai_mahou.smith_star_metal", "Aço Estelar", "Star Steel")
e("block.isekai_mahou.smith_arcane_anvil", "Bigorna Arcana", "Arcane Anvil")
e("entity.isekai_mahou.smith_spark", "Fagulha de Forja", "Forge Spark")
e("entity.isekai_mahou.smith_star_hammer", "Martelo da Forja Estelar", "Starforge Hammer")
e("legend.isekai_mahou.starforge_god", "Martelo do Deus da Forja", "Hammer of the Forge God")
e("legend.isekai_mahou.broken_phantasm", "Fantasmas Partidos", "Broken Phantasms")
e("tab.isekai_mahou.smith_forge", "Forja", "Forge")

# roupa (4 graus x 4 pecas)
TIERS = [
    ("apprentice", "", "Aprendiz da Forja", "Forge Apprentice",
     "Couro grosso, fuligem e vontade de aprender.", "Thick leather, soot and the will to learn."),
    ("adept", "adept_", "Artesão Rúnico", "Runic Artisan",
     "As rebites brilham com runas de calor.", "The rivets glow with heat runes."),
    ("master", "master_", "Mestre da Bigorna", "Anvil Master",
     "Placas temperadas que ele mesmo bateu, uma a uma.", "Tempered plates he hammered himself, one by one."),
    ("archmage", "archmage_", "Forjador de Lendas", "Legendsmith",
     "Aço estelar e brasa viva: a armadura de quem forja lendas.", "Star steel and living embers: armor of one who forges legends."),
]
PIECES = [
    ("helmet", "Óculos de Forja", "Forge Goggles"),
    ("chestplate", "Avental de Forja", "Forge Apron"),
    ("leggings", "Calças de Forja", "Forge Trousers"),
    ("boots", "Botas Ferradas", "Hobnailed Boots"),
]
for tid, prefix, tpt, ten, fpt, fen in TIERS:
    e("outfit.isekai_mahou.smith.tier." + tid, tpt, ten)
    e("outfit.isekai_mahou.smith.flavor." + tid, fpt, fen)
    for piece, ppt, pen in PIECES:
        short_pt = "Aprendiz" if tid == "apprentice" else tpt
        short_en = "Apprentice" if tid == "apprentice" else ten
        e("item.isekai_mahou.smith_" + prefix + piece, ppt + " do " + short_pt, short_en + " " + pen)

# ------------------------------------------------------------------ partes da lamina
for k, pt, en in [("blade", "Lâmina", "Blade"), ("axe", "Machado", "Axe"), ("spear", "Lança", "Spear"),
                  ("hammer", "Martelo", "Hammer"), ("scythe", "Foice", "Scythe")]:
    e("blade.isekai_mahou.family." + k, pt, en)
for k, pt, en in [("dagger", "Adaga", "Dagger"), ("sword", "Espada", "Sword"), ("longsword", "Espada Longa", "Longsword"),
                  ("greatsword", "Montante", "Greatsword"), ("axe", "Machado", "Axe"), ("spear", "Lança", "Spear"),
                  ("hammer", "Martelo de Guerra", "Warhammer"), ("scythe", "Foice", "Scythe")]:
    e("blade.isekai_mahou.type." + k, pt, en)
for k, pt, en in [("iron", "Ferro", "Iron"), ("gold", "Ouro", "Gold"), ("diamond", "Diamante", "Diamond"),
                  ("netherite", "Netherite", "Netherite"), ("starmetal", "Aço Estelar", "Star Steel")]:
    e("blade.isekai_mahou.material." + k, pt, en)
for k, pt, en in [("standard", "Couro", "Leather"), ("long", "Cabo Longo", "Long Haft"), ("light", "Cordão Leve", "Light Cord")]:
    e("blade.isekai_mahou.grip." + k, pt, en)
for k, pt, en in [("bright", "Brilhante", "Bright"), ("matte", "Fosco", "Matte"), ("runic", "Rúnico", "Runic")]:
    e("blade.isekai_mahou.finish." + k, pt, en)
for k, pt, en in [("length", "Comprimento", "Length"), ("width", "Largura", "Width"), ("curve", "Curva", "Curve"),
                  ("tip", "Ponta", "Tip"), ("serration", "Serrilha", "Serration")]:
    e("blade.isekai_mahou.part." + k, pt, en)
for k, pt, en in [("light", "leve", "light"), ("medium", "médio", "medium"), ("heavy", "pesado", "heavy"),
                  ("colossal", "colossal", "colossal")]:
    e("blade.isekai_mahou.weight." + k, pt, en)
e("blade.isekai_mahou.name", "%s de %s ⚒", "%2$s %1$s ⚒")
e("blade.isekai_mahou.name_masterwork", "%s ★ Obra-Prima", "%s ★ Masterwork")

# ------------------------------------------------------------------ dicas dos itens
e(T + "blade.quality", "Qualidade %s (%s/5)", "Quality %s (%s/5)")
e(T + "blade.points", "Pontos de forja: %s/%s", "Forge points: %s/%s")
e(T + "blade.parts", "Comprimento %s · Largura %s · Curva %s · Ponta %s · Serrilha %s",
  "Length %s · Width %s · Curve %s · Tip %s · Serration %s")
e(T + "blade.style", "Empunhadura %s · %s · peso %s", "%s grip · %s · %s weight")
e(T + "blade.rune", "Runa de %s: dano extra do elemento em cada golpe", "%s rune: extra elemental damage on every hit")
e(T + "blade.tip", "Ponta: até +%s%% de dano contra armadura · %s%% de golpe certeiro",
  "Tip: up to +%s%% damage against armor · %s%% chance of a clean hit")
e(T + "blade.serration", "Serrilha %s: cada golpe faz sangrar (acumula)", "Serration %s: every hit causes bleeding (stacks)")
e(T + "blade.family.blade", "Lâmina: o comprimento decide se é adaga, espada, espada longa ou montante.",
  "Blade: length decides whether it is a dagger, sword, longsword or greatsword.")
e(T + "blade.family.axe", "Machado: dano alto, quebra escudos e parte armaduras pesadas (+15%%).",
  "Axe: high damage, breaks shields and splits heavy armor (+15%%).")
e(T + "blade.family.spear", "Lança: alcance longo; cada degrau de ponta alonga mais.",
  "Spear: long reach; every tip step reaches further.")
e(T + "blade.family.hammer", "Martelo de Guerra: lento e pesado, quebra escudos, empurra e pode atordoar.",
  "Warhammer: slow and heavy, breaks shields, knocks back and may stun.")
e(T + "blade.family.scythe", "Foice: corte em área e cada golpe drena um pouco de vida.",
  "Scythe: sweeping cuts and every hit drains a little life.")
e(T + "blade.finish.bright", "Brilhante: +20%% de dano contra mortos-vivos.", "Bright: +20%% damage against the undead.")
e(T + "blade.finish.matte", "Fosco: +25%% de dano em golpes pelas costas.", "Matte: +25%% damage when striking from behind.")
e(T + "blade.finish.runic", "Rúnico: a runa queima 50%% mais forte.", "Runic: the rune burns 50%% stronger.")
e(T + "blade.smith", "Forjada por %s", "Forged by %s")
e(T + "blade.more", "Segure Shift para ver como forjar", "Hold Shift to see how to forge")
e(T + "blade.how", "Bigorna Arcana (clique direito) para montar a arma. Os glifos de forja (Alongar, Alargar, Afiar Ponta, "
  "Serrilhar, Fosquear, Gravar Runa, Reforjar) conjurados com a forma Si Mesmo remodelam a lâmina da mão em qualquer lugar.",
  "Use an Arcane Anvil (right click) to assemble the weapon. Forge glyphs (Lengthen, Widen, Sharpen Tip, Serrate, Matte, "
  "Engrave Rune, Reforge) cast with the Self form reshape the blade in your hand anywhere.")
e(T + "hammer.stun", "%s%% de chance de atordoar a cada golpe", "%s%% chance to stun on every hit")
e(T + "hammer.repair", "Ferreiro — clique direito com um item gasto na outra mão: Reparo de Campo (+%s%% de durabilidade, gasta Vigor)",
  "Smith — right click with a worn item in the other hand: Field Repair (+%s%% durability, costs Vigor)")
e(T + "hammer.forge", "Segurando o martelo, os glifos de forja moldam a lâmina da outra mão (grau III ou IV: +1 degrau).",
  "While holding the hammer, forge glyphs shape the blade in your other hand (tier III or IV: +1 step).")
e(T + "outfit.tier", "Grau %s: %s (Círculo %s)", "Tier %s: %s (Circle %s)")
e(T + "outfit.class", "Roupa de classe: %s", "Class outfit: %s")
e(T + "outfit.fire", "−%s%% de dano de fogo e lava por peça", "−%s%% fire and lava damage per piece")

# ------------------------------------------------------------------ mensagens
e(M + "converted", "A forja desmontou %s e remontou como %s.", "The forge took apart %s and rebuilt it as %s.")
e(M + "no_blade", "Segure uma Lâmina Forjada (ou uma espada, machado, enxada ou maça para converter).",
  "Hold a Forged Blade (or a sword, axe, hoe or mace to convert).")
e(M + "no_blade_glyph", "A magia de forja precisa de uma arma na mão: Lâmina Forjada, espada, machado, enxada ou maça.",
  "Forge magic needs a weapon in hand: Forged Blade, sword, axe, hoe or mace.")
e(M + "no_change", "Nada mudou na lâmina.", "Nothing changed on the blade.")
e(M + "capacity", "Faltam pontos de forja: %s/%s na qualidade %s. Tempere a lâmina ou desfaça outra parte.",
  "Not enough forge points: %s/%s at quality %s. Temper the blade or undo another part.")
e(M + "part_max", "%s já está no máximo.", "%s is already at maximum.")
e(M + "part_min", "%s já está no mínimo.", "%s is already at minimum.")
e(M + "part_change", "%s %s → %s", "%s %s → %s")
e(M + "family_change", "Reforjada em %s", "Reforged into %s")
e(M + "reshaped", "⚒ %s — agora: %s (%s/%s pontos)", "⚒ %s — now: %s (%s/%s points)")
e(M + "runic_needs_rune", "O acabamento Rúnico precisa de uma runa gravada.", "The Runic finish needs an engraved rune.")
e(M + "rune_engraved", "Runa de %s gravada", "%s rune engraved")
e(M + "rune_removed", "Runa apagada", "Rune erased")
e(M + "rune_no_essence_kind", "Não há essência para gravar uma runa de %s.", "There is no essence to engrave a %s rune.")
e(M + "rune_needs_essence", "Gravar a runa custa 1× %s.", "Engraving the rune costs 1× %s.")
e(M + "material_change", "Metal trocado para %s", "Metal changed to %s")
e(M + "missing_material", "Faltam materiais: %s.", "Missing materials: %s.")
e(M + "quality_cap", "Você só consegue temperar até a qualidade %s★ (suba nas Provas da Forja).",
  "You can only temper up to quality %s★ (advance in the Forge Trials).")
e(M + "tempered", "Temperada: %s", "Tempered: %s")
e(M + "forged_new", "Forjada: %s!", "Forged: %s!")
e(M + "rank_smith_only", "Só um Ferreiro pode fazer as Provas da Forja.", "Only a Smith can take the Forge Trials.")
e(M + "rank_max", "Você já é Forjador de Lendas: todas as Provas estão concluídas.",
  "You are already a Legendsmith: every Trial is complete.")
e(M + "rank_tasks", "Ainda faltam tarefas deste rank (veja a aba Forja do menu B).",
  "This rank still has unfinished tasks (see the Forge tab in the B menu).")
e(M + "rank_offering", "A oferenda está incompleta: %s.", "The offering is incomplete: %s.")
e(M + "rank_need_anvil", "Fique a até 6 blocos de uma Bigorna Arcana para fazer a oferenda.",
  "Stand within 6 blocks of an Arcane Anvil to make the offering.")
e(M + "rank_up", "PROVAS DA FORJA — RANK %s: %s", "FORGE TRIALS — RANK %s: %s")
e(M + "glyph_learned", "Técnica aprendida: %s", "Technique learned: %s")
e(M + "next_hint", "Próximo rank: veja as tarefas na aba Forja do menu (B).", "Next rank: check the tasks in the Forge tab of the menu (B).")
e(M + "all_done", "Você completou as Provas da Forja. Seu nome entra para as lendas da bigorna!",
  "You completed the Forge Trials. Your name joins the legends of the anvil!")
e(M + "outfit_granted", "Círculo %s alcançado: você ganhou a roupa %s do Ferreiro!", "Circle %s reached: you earned the Smith's %s outfit!")
e(M + "outfit_equipped", "As peças já foram vestidas.", "The pieces were equipped.")
e(M + "outfit_inventory", "Algumas peças foram para o inventário (os espaços já estavam ocupados).",
  "Some pieces went to your inventory (those slots were taken).")
e(M + "repair_smith_only", "Só um Ferreiro sabe fazer o Reparo de Campo.", "Only a Smith knows how to do a Field Repair.")
e(M + "repaired", "Reparo de Campo: %s +%s de durabilidade", "Field Repair: %s +%s durability")
e(M + "spell_preset", "Nova magia do Ferreiro guardada nos rascunhos do Grimório: %s", "New Smith spell saved to the Grimoire drafts: %s")
e("toast.isekai_mahou.class_smith.rank_up", "Provas da Forja: rank %s", "Forge Trials: rank %s")
e("toast.isekai_mahou.class_smith.outfit", "Roupa nova: %s", "New outfit: %s")
e("toast.isekai_mahou.class_smith.outfit_sub", "Círculo %s", "Circle %s")

# ------------------------------------------------------------------ comandos
e("command.isekai_mahou.class_smith.info", "%s — Provas da Forja: rank %s (%s)", "%s — Forge Trials: rank %s (%s)")
e("command.isekai_mahou.class_smith.not_smith", "%s não é Ferreiro.", "%s is not a Smith.")
e("command.isekai_mahou.class_smith.rank_set", "Provas da Forja: %s jogador(es) levados ao rank %s.",
  "Forge Trials: %s player(s) raised to rank %s.")
e("command.isekai_mahou.class_smith.blades", "Uma lâmina de cada família foi entregue.", "One blade of each family was handed out.")
e("command.isekai_mahou.class_smith.starter", "Técnicas e magias prontas do Ferreiro refeitas para %s jogador(es).",
  "Smith techniques and ready spells redone for %s player(s).")

# ------------------------------------------------------------------ tela da Bigorna Arcana
e(S + "anvil", "Bigorna Arcana", "Arcane Anvil")
e(S + "row.family", "Família", "Family")
e(S + "row.grip", "Empunhadura", "Grip")
e(S + "row.finish", "Acabamento", "Finish")
e(S + "row.rune", "Runa", "Rune")
e(S + "none", "nenhuma", "none")
e(S + "points", "Pontos: %s/%s", "Points: %s/%s")
e(S + "quality", "Qualidade %s", "Quality %s")
e(S + "swap", "Trocar metal", "Swap metal")
e(S + "temper", "Temperar", "Temper")
e(S + "temper_tip", "Temperar até %s★: %s pontos de forja. Custa um Cristal de Mana por estrela da qualidade nova.",
  "Temper to %s★: %s forge points. Costs one Mana Crystal per star of the new quality.")
e(S + "cost", "Custo: %s", "Cost: %s")
e(S + "convert", "Converter a arma da mão", "Convert the weapon in hand")
e(S + "forge_new", "Forjar uma lâmina nova (adaga):", "Forge a new blade (dagger):")
e(S + "no_blade", "Nenhuma Lâmina Forjada na mão.", "No Forged Blade in hand.")
e(S + "no_blade_hint", "Segure uma espada, machado, enxada ou maça para convertê-la. Dica: os glifos de forja (Alongar, Alargar, "
  "Afiar Ponta...) também remodelam a lâmina em qualquer lugar — conjure-os com a forma Si Mesmo.",
  "Hold a sword, axe, hoe or mace to convert it. Tip: forge glyphs (Lengthen, Widen, Sharpen Tip...) also reshape the blade "
  "anywhere — cast them with the Self form.")
e(S + "stats", "Dano %s · %s golpes/s · alcance %s · peso %s", "Damage %s · %s hits/s · reach %s · %s weight")
e(S + "step_cost", "Cada ajuste custa %s de %s", "Each adjustment costs %s %s")

# ------------------------------------------------------------------ aba Forja (Provas da Forja)
e(S + "tab_title", "Provas da Forja", "Forge Trials")
e(S + "rank_none", "Sem rank — complete as tarefas do rank I", "No rank yet — complete the rank I tasks")
e(S + "rank_now", "Rank %s: %s", "Rank %s: %s")
e(S + "rank_header", "Rank %s — %s", "Rank %s — %s")
e(S + "offering", "Oferenda:", "Offering:")
e(S + "offer", "Oferendar na bigorna", "Offer at the anvil")
e(S + "rewards", "Recompensas: %s XP · técnicas: %s", "Rewards: %s XP · techniques: %s")
e(S + "rewards_items", "nenhuma técnica nova", "no new technique")
e(S + "state.done", "Concluído", "Completed")
e(S + "state.ready", "Pronto! Vá a uma Bigorna Arcana e oferende.", "Ready! Go to an Arcane Anvil and make the offering.")
e(S + "state.progress", "Em andamento", "In progress")
e(S + "state.locked", "Bloqueado: conclua o rank anterior", "Locked: finish the previous rank")
e(S + "reward_items.1", "Itens: Martelo do Aprendiz, 4 Cristais de Mana, 2 Essências de Fogo. Qualidade máxima: ★★.",
  "Items: Apprentice's Hammer, 4 Mana Crystals, 2 Fire Essences. Max quality: ★★.")
e(S + "reward_items.2", "Itens: Martelo do Artesão, 3 Essências de Terra, Maçã Dourada. Magia: Chuva de Fagulhas. Qualidade máxima: ★★★.",
  "Items: Artisan's Hammer, 3 Earth Essences, Golden Apple. Spell: Spark Shower. Max quality: ★★★.")
e(S + "reward_items.3", "Itens: Martelo do Mestre da Bigorna (forja +1 degrau), 6 Essências Arcanas, 8 Cristais de Mana. "
  "Magia: Runa da Forja. Qualidade máxima: ★★★★.",
  "Items: Anvil Master's Hammer (forging +1 step), 6 Arcane Essences, 8 Mana Crystals. Spell: Forge Rune. Max quality: ★★★★.")
e(S + "reward_items.4", "Itens: 2 Aço Estelar, pergaminho da lenda Campo de Lâminas Infinitas, 2 Maçãs Douradas. "
  "Magia: Forja Estelar (G). Qualidade máxima: ★★★★★.",
  "Items: 2 Star Steel, Infinite Blade Field legend scroll, 2 Golden Apples. Spell: Starforge (G). Max quality: ★★★★★.")
e(S + "reward_items.5", "Itens: Martelo Estelar, pergaminhos das lendas Martelo do Deus da Forja e Fantasmas Partidos, "
  "Maçã Dourada Encantada.",
  "Items: Starforged Hammer, legend scrolls Hammer of the Forge God and Broken Phantasms, Enchanted Golden Apple.")
e(S + "bonus_title", "Bônus de classe", "Class bonuses")
e(S + "bonus.damage", "Lâminas Forjadas e martelos: +%s%% de dano", "Forged Blades and hammers: +%s%% damage")
e(S + "bonus.repair", "Equipamento vestido e nas mãos se repara: +%s a cada 5 s", "Worn and held gear repairs itself: +%s every 5 s")
e(S + "bonus.wear", "Lâminas Forjadas e martelos gastam %s%% menos", "Forged Blades and hammers wear %s%% less")
e(S + "bonus.fire", "Pele de forja: −%s%% de dano de fogo e lava", "Forge skin: −%s%% fire and lava damage")
e(S + "bonus.quality", "Qualidade máxima ao temperar: %s★", "Max tempering quality: %s★")
e(S + "only_smith", "A Forja Arcana é a arte do Ferreiro", "The Arcane Forge is the Smith's art")
e(S + "only_smith_hint", "Qualquer um pode usar uma Bigorna Arcana para forjar e ajustar Lâminas Forjadas (até ★★), mas só o "
  "Ferreiro faz as Provas da Forja, tempera obras-primas e ganha os bônus da classe. Os glifos de forja de outra classe só se "
  "aprendem com uma Lição escrita por um Ferreiro.",
  "Anyone can use an Arcane Anvil to forge and adjust Forged Blades (up to ★★), but only the Smith takes the Forge Trials, "
  "tempers masterworks and gets the class bonuses. Other classes only learn the forge glyphs from a Lesson written by a Smith.")

# ------------------------------------------------------------------ Provas da Forja: ranks e tarefas
for n, pt, en in [(1, "Aprendiz da Forja", "Forge Apprentice"), (2, "Artesão", "Artisan"), (3, "Mestre Ferreiro", "Master Smith"),
                  (4, "Grão-Mestre da Bigorna", "Grandmaster of the Anvil"), (5, "Forjador de Lendas", "Legendsmith")]:
    e("smith.isekai_mahou.rank." + str(n), pt, en)
TK = "smith.isekai_mahou.task."
e(TK + "level", "Chegar ao nível %s", "Reach level %s")
e(TK + "circle", "Alcançar o Círculo %s", "Reach Circle %s")
e(TK + "forged", "Forjar %s lâmina nova na Bigorna Arcana", "Forge %s new blade at the Arcane Anvil")
e(TK + "reshaped", "Remodelar lâminas %s vezes (glifos de forja ou bigorna)", "Reshape blades %s times (forge glyphs or anvil)")
e(TK + "blade_kills", "Derrotar %s criaturas com Lâmina Forjada ou martelo", "Defeat %s creatures with a Forged Blade or hammer")
e(TK + "ores", "Minerar %s minérios", "Mine %s ores")
e(TK + "runes", "Gravar %s runa numa lâmina", "Engrave %s rune on a blade")
e(TK + "families", "Forjar %s famílias de arma diferentes", "Forge %s different weapon families")
e(TK + "diamond", "Ter uma lâmina de Diamante", "Own a Diamond blade")
e(TK + "elite_kills", "Derrotar %s inimigos de elite ou chefes com suas armas", "Defeat %s elite enemies or bosses with your weapons")
e(TK + "netherite", "Ter uma lâmina de Netherite", "Own a Netherite blade")
e(TK + "casts", "Conjurar %s magias com técnicas do Ferreiro", "Cast %s spells using Smith techniques")
e(TK + "bosses", "Derrotar %s chefe(s) diferente(s)", "Defeat %s different boss(es)")
e(TK + "starmetal", "Ter uma lâmina de Aço Estelar", "Own a Star Steel blade")
e(TK + "masterwork", "Criar uma Obra-Prima (★★★★★ com todos os pontos de forja usados)",
  "Create a Masterwork (★★★★★ with every forge point used)")

# ------------------------------------------------------------------ glifos de origem do Ferreiro
e(G + "smith_hammer_slam", "Martelada", "Hammer Slam")
e(G + "smith_hammer_slam.desc", "FORMA do Ferreiro. O martelo bate no chão e uma onda de choque corre 7 blocos à frente, seguindo o "
  "relevo: cada criatura no caminho recebe os efeitos uma vez e é lançada para cima. Expandir = onda mais longa; Acelerar = mais "
  "rápida; Dividir = mais ondas em leque (até 7). Não quebra blocos. Pode ser estágio.",
  "Smith FORM. The hammer strikes the ground and a shockwave runs 7 blocks ahead, following the terrain: every creature in its "
  "path takes the effects once and is tossed upward. Expand = longer wave; Accelerate = faster; Split = more waves in a fan "
  "(up to 7). Does not break blocks. Can be a stage.")
e(G + "smith_spark_shower", "Chuva de Fagulhas", "Spark Shower")
e(G + "smith_spark_shower.desc", "FORMA do Ferreiro (Círculo 2). Uma martelada no ar solta um leque de 4 fagulhas incandescentes "
  "(+2 por Dividir, até 12) que voam ~14 blocos em arco; cada uma aplica os efeitos com 40%% do poder. Os efeitos custam por "
  "fagulha. Espalhar abre o leque; Acelerar deixa as fagulhas mais rápidas.",
  "Smith FORM (Circle 2). A hammer blow in the air releases a fan of 4 glowing sparks (+2 per Split, up to 12) that fly ~14 "
  "blocks in an arc; each one applies the effects at 40%% power. Effects cost per spark. Spread widens the fan; Accelerate "
  "makes the sparks faster.")
e(G + "smith_starforge", "Forja Estelar", "Starforge")
e(G + "smith_starforge.desc", "FORMA G do Ferreiro (Círculo 5, ultimate). Um martelo colossal de aço estelar surge sobre o ponto "
  "mirado (até 32 blocos) e bate TRÊS vezes, aplicando os efeitos numa área de 5 blocos (Expandir aumenta); a terceira bate com "
  "150%% do poder e lança tudo para o alto. Tremor e ondas de choque — o cenário fica intacto. Os efeitos custam por martelada.",
  "Smith G FORM (Circle 5, ultimate). A colossal star-steel hammer appears over the aimed point (up to 32 blocks) and slams "
  "THREE times, applying the effects in a 5-block area (Expand increases it); the third slam hits at 150%% power and throws "
  "everything up. Tremors and shockwaves — the scenery stays intact. Effects cost per slam.")
e(G + "smith_molten", "Metal Fundido", "Molten Metal")
e(G + "smith_molten.desc", "EFEITO do Ferreiro (Fogo). Um jorro de metal derretido: 5 de dano, Em Chamas e Lentidão por 3 s. "
  "Armadura não protege: alvos com 6+ de armadura levam +30%%. Amplificar = mais chamas. Inverter: metal ESFRIADO — sem fogo, "
  "mas Lentidão II e Exposto.",
  "Smith EFFECT (Fire). A gush of molten metal: 5 damage, Burning and Slowness for 3 s. Armor does not help: targets with 6+ "
  "armor take +30%%. Amplify = more flames. Invert: COOLED metal — no fire, but Slowness II and Exposed.")
e(G + "forge_lengthen", "Alongar", "Lengthen")
e(G + "forge_lengthen.desc", "FORJA do Ferreiro. Em você (forma Si Mesmo): +1 de comprimento na Lâmina Forjada da mão — a adaga "
  "vira espada, espada longa, montante (cabo/haste nas outras famílias). Uma espada, machado, enxada ou maça vanilla vira Lâmina "
  "Forjada. Amplificar = 2 degraus; martelo grau III+ na outra mão = +1; Inverter = encurtar. Num inimigo: estocada de 5 de "
  "dano que também fere quem está na linha (60%%).",
  "Smith FORGE glyph. On yourself (Self form): +1 length on the Forged Blade in hand — the dagger becomes a sword, longsword, "
  "greatsword (haft on other families). A vanilla sword, axe, hoe or mace becomes a Forged Blade. Amplify = 2 steps; tier III+ "
  "hammer in the other hand = +1; Invert = shorten. On an enemy: a 5-damage thrust that also hits anyone on the line (60%%).")
e(G + "forge_widen", "Alargar", "Widen")
e(G + "forge_widen.desc", "FORJA do Ferreiro. Em você: +1 de largura na lâmina (mais dano e repulsão, mais peso). Inverter = "
  "afinar. Num inimigo: lâmina larga — 4 de dano e um corte em volta do alvo (2,5 blocos, 60%%) que empurra todos.",
  "Smith FORGE glyph. On yourself: +1 width on the blade (more damage and knockback, more weight). Invert = thin it. On an "
  "enemy: broad blade — 4 damage and a sweep around the target (2.5 blocks, 60%%) that pushes everyone back.")
e(G + "forge_sharpen", "Afiar Ponta", "Sharpen Tip")
e(G + "forge_sharpen.desc", "FORJA do Ferreiro. Em você: +1 de ponta (perfura armadura e chance de golpe certeiro; na lança, mais "
  "alcance). Inverter = arredondar. Num inimigo: fio que acha a fresta — 4,5 de dano + 1 por 4 de armadura do alvo (até +6) e "
  "Exposto por 4 s.",
  "Smith FORGE glyph. On yourself: +1 tip (pierces armor and chance of a clean hit; on a spear, more reach). Invert = blunt it. "
  "On an enemy: an edge that finds the gap — 4.5 damage + 1 per 4 target armor (up to +6) and Exposed for 4 s.")
e(G + "forge_serrate", "Serrilhar", "Serrate")
e(G + "forge_serrate.desc", "FORJA do Ferreiro. Em você: +1 de serrilha (os golpes da lâmina fazem sangrar, acumulando). Inverter "
  "= alisar o fio. Num inimigo: serra que abre feridas — 2 de dano e 2 acúmulos de Sangrando por 6 s (+1 por Amplificar).",
  "Smith FORGE glyph. On yourself: +1 serration (blade hits cause stacking bleeding). Invert = smooth the edge. On an enemy: a "
  "saw that opens wounds — 2 damage and 2 Bleeding stacks for 6 s (+1 per Amplify).")
e(G + "forge_matte", "Fosquear", "Matte Finish")
e(G + "forge_matte.desc", "FORJA do Ferreiro. Em você: acabamento Fosco (não reflete luz: +25%% em golpes pelas costas). "
  "Inverter = polir para Brilhante (+20%% contra mortos-vivos). Num inimigo: reflexo ofuscante — 2 de dano, Cegueira 3 s e "
  "Fraqueza 4 s.",
  "Smith FORGE glyph. On yourself: Matte finish (no glare: +25%% on hits from behind). Invert = polish to Bright (+20%% against "
  "the undead). On an enemy: a blinding glint — 2 damage, Blindness 3 s and Weakness 4 s.")
e(G + "forge_rune", "Gravar Runa", "Engrave Rune")
e(G + "forge_rune.desc", "FORJA do Ferreiro (Círculo 3). Em você: grava na lâmina a runa do elemento da magia (magia Arcana: o da "
  "Essência na outra mão) gastando 1 essência — cada golpe causa dano extra e o toque do elemento (queima, molha, prende, "
  "empurra, murcha). Amplificar também deixa o acabamento Rúnico (+50%%); Inverter apaga. Num inimigo: marca rúnica que explode "
  "em 1,5 s — 6 de dano e 50%% a quem estiver a 2,5 blocos.",
  "Smith FORGE glyph (Circle 3). On yourself: engraves the spell's element as a rune on the blade (Arcane spell: the element of "
  "the Essence in your other hand), spending 1 essence — every hit deals extra damage plus the element's touch (burn, soak, "
  "root, push, wither). Amplify also gives the Runic finish (+50%%); Invert erases it. On an enemy: a rune mark that explodes "
  "after 1.5 s — 6 damage and 50%% to anyone within 2.5 blocks.")
e(G + "forge_reforge", "Reforjar", "Reforge")
e(G + "forge_reforge.desc", "FORJA do Ferreiro (Círculo 3). Em você: refaz a lâmina em outra família, mantendo as partes — Lâmina "
  "→ Machado → Lança → Martelo de Guerra → Foice → Lâmina (Amplificar com martelo grau III+ pula duas). Inverter = volta uma. "
  "Num inimigo: arremessa uma cópia espectral da arma da mão (dano pela arma, 4 a 14).",
  "Smith FORGE glyph (Circle 3). On yourself: remakes the blade into another family, keeping its parts — Blade → Axe → Spear "
  "→ Warhammer → Scythe → Blade (Amplify with a tier III+ hammer jumps two). Invert = go back one. On an enemy: hurls a "
  "spectral copy of your held weapon (damage by the weapon, 4 to 14).")

# ------------------------------------------------------------------ pacote de técnicas de anime (arqueiro de lâminas)
e("item.isekai_mahou.smith_projection", "Projeção", "Projection")
e("item.isekai_mahou.smith_projection.of", "Projeção: %s", "Projection: %s")
e(T + "projection", "Cópia de mana de uma arma que você viu: bate igual à original.",
  "A mana copy of a weapon you saw: it strikes just like the original.")
e(T + "projection.fade", "Some sozinha quando o tempo acaba ou se for largada.", "Fades on its own when time runs out or if dropped.")
e("entity.isekai_mahou.smith_blade", "Arma Projetada", "Projected Weapon")
e("entity.isekai_mahou.smith_rho_aias", "Rho Aias", "Rho Aias")
e("entity.isekai_mahou.smith_blade_world", "Mundo das Lâminas", "World of Blades")
e(M + "trace_on", "Traçar: ligar — %s projetada na sua mão.", "Trace on — %s projected into your hand.")
e(M + "trace_full", "Traçar: sem espaço no inventário para projetar %s.", "Trace: no inventory space to project %s.")
e(M + "rho_aias", "Rho Aias: %s pétalas de pé.", "Rho Aias: %s petals standing.")
e(M + "tech_catchup", "Técnicas novas da Forja aprendidas: %s.", "New Forge techniques learned: %s.")
e("announce.isekai_mahou.smith_trace_on", "Traçar: Ligar!", "Trace On!")
e("announce.isekai_mahou.smith_rho_aias", "Rho Aias — os Sete Anéis!", "Rho Aias — the Seven Rings!")
e("announce.isekai_mahou.smith_caladbolg", "Caladbolg II!", "Caladbolg II!")
e("announce.isekai_mahou.smith_twin_falchions", "Kanshō e Bakuya!", "Kanshou and Bakuya!")
e("announce.isekai_mahou.smith_twin_falchions.over", "Kanshō e Bakuya — Sobrefio!", "Kanshou and Bakuya — Overedge!")
e("announce.isekai_mahou.smith_blade_world", "MUNDO DAS LÂMINAS", "WORLD OF BLADES")
e("announce.isekai_mahou.smith_blade_world.1", "Eu sou o osso da minha espada.", "I am the bone of my sword.")
e("announce.isekai_mahou.smith_blade_world.2", "Brasa corre onde havia sangue; aço sustenta onde havia carne.",
  "Embers run where blood once ran; steel holds where flesh once held.")
e("announce.isekai_mahou.smith_blade_world.3", "Mil lâminas passaram pela minha bigorna, e nenhuma voltou vencida.",
  "A thousand blades crossed my anvil, and none came back defeated.")
e("announce.isekai_mahou.smith_blade_world.4", "Sozinho no alto da colina de espadas, sigo forjando.",
  "Alone atop the hill of swords, I keep on forging.")
e("announce.isekai_mahou.smith_blade_world.5", "Então que se abra...", "So let it open...")
e("legend.isekai_mahou.smith_bone_of_my_sword", "Eu Sou o Osso da Minha Espada", "I Am the Bone of My Sword")
e("legend.isekai_mahou.smith_caladbolg_phantasm", "Caladbolg II — Fantasma Partido", "Caladbolg II — Broken Phantasm")
e("legend.isekai_mahou.smith_crane_wings", "Asas do Grou: Três Golpes", "Crane Wings: Three Strikes")

e(G + "smith_trace_on", "Traçar: Ligar", "Trace On")
e(G + "smith_trace_on.desc", "EFEITO do Ferreiro (Arcano; homenagem ao \"Trace On\"). PROJEÇÃO. Em você (Si Mesmo): lê a arma de quem "
  "você está olhando (criatura, item no chão ou em quadro, até 24 blocos) e projeta uma CÓPIA na sua mão — bate igual à original "
  "(dano e encantamentos) e some em 30 s (Estender +); sem nada na mira, projeta uma espada de ferro. Até 3 projeções. Num "
  "inimigo: copia a arma DELE e a lança de volta (6 de dano, +50%% se ele estava armado) e a cópia fica com você. "
  "Desbloqueio: Provas da Forja, rank I.",
  "Smith EFFECT (Arcane; homage to \"Trace On\"). PROJECTION. On yourself (Self): reads the weapon of whatever you are looking at "
  "(creature, item on the ground or in a frame, up to 24 blocks) and projects a COPY into your hand — it strikes like the "
  "original (damage and enchantments) and fades after 30 s (Extend +); with nothing in sight it projects an iron sword. Up to 3 "
  "projections. On an enemy: copies THEIR weapon and hurls it back (6 damage, +50%% if they were armed) and you keep the copy. "
  "Unlock: Forge Trials, rank I.")
e(G + "smith_twin_falchions", "Kanshō e Bakuya", "Kanshou and Bakuya")
e(G + "smith_twin_falchions.desc", "FORMA do Ferreiro (Círculo 2; homenagem às lâminas gêmeas negra e branca). Projeta o par e "
  "arremessa os dois em arcos espelhados (12 blocos; Acelerar = mais longe). Eles cortam quem estiver no caminho, giram no fim e "
  "VOLTAM para a sua mão cruzando um com o outro — as gêmeas se atraem. Cada lâmina aplica os efeitos uma vez por alvo. "
  "Inverter = SOBREFIO: lâminas enormes que cortam também na volta. Dividir = mais pares em leque. Custo: 2 cópias por par "
  "(4 com Sobrefio). Pode ser estágio. Desbloqueio: Provas da Forja, rank II.",
  "Smith FORM (Circle 2; homage to the black and white twin blades). Projects the pair and throws both in mirrored arcs (12 "
  "blocks; Accelerate = farther). They cut everything in their path, spin at the end and RETURN to your hand crossing each other "
  "— the twins attract. Each blade applies the effects once per target. Invert = OVEREDGE: huge blades that also cut on the way "
  "back. Split = more pairs in a fan. Cost: 2 copies per pair (4 with Overedge). Can be a stage. Unlock: Forge Trials, rank II.")
e(G + "smith_rho_aias", "Rho Aias — os Sete Anéis", "Rho Aias — the Seven Rings")
e(G + "smith_rho_aias.desc", "EFEITO do Ferreiro (Terra, benéfico, Círculo 3; homenagem ao escudo de sete pétalas). Ergue na frente "
  "do alvo (você com Si Mesmo, ou aliados) um escudo de 7 pétalas de luz que acompanha o olhar por 8 s (Estender +). Cada pétala "
  "segura 15 de dano (× poder) vindo da FRENTE — golpes, flechas, magias, explosões — e se parte; projéteis inimigos param no ar "
  "ao tocar o escudo. Reaplicar renova as pétalas. Desbloqueio: Provas da Forja, rank III.",
  "Smith EFFECT (Earth, beneficial, Circle 3; homage to the seven-petal shield). Raises in front of the target (you with Self, or "
  "allies) a shield of 7 light petals that follows the gaze for 8 s (Extend +). Each petal holds 15 damage (× power) coming from "
  "the FRONT — blows, arrows, spells, explosions — then shatters; enemy projectiles stop in mid-air when they touch the shield. "
  "Recasting renews the petals. Unlock: Forge Trials, rank III.")
e(G + "smith_ephemeral_sword", "Espada Mágica Efêmera", "Ephemeral Magic Sword")
e(G + "smith_ephemeral_sword.desc", "EFEITO do Ferreiro (Círculo 3; homenagem às espadas mágicas do clã de ferreiros que soltam magia "
  "de exército e se partem). Uma espada rúnica do elemento da magia surge sobre você e é arremessada: no impacto, explosão "
  "elemental — 12 de dano no alvo e metade a até 3 blocos (Expandir +), com o toque do elemento (Fogo queima, Água encharca, "
  "Terra prende, Vento lança, Luz cega, Sombra murcha). Depois a lâmina se ESTILHAÇA. Desbloqueio: Provas da Forja, rank III.",
  "Smith EFFECT (Circle 3; homage to the magic swords of the cursed smith clan, which unleash army-scale magic and shatter). A "
  "runic sword of the spell's element appears above you and is hurled: on impact, an elemental blast — 12 damage to the target "
  "and half within 3 blocks (Expand +), with the element's touch (Fire burns, Water soaks, Earth roots, Wind launches, Light "
  "blinds, Shadow withers). Then the blade SHATTERS. Unlock: Forge Trials, rank III.")
e(G + "smith_caladbolg", "Caladbolg II", "Caladbolg II")
e(G + "smith_caladbolg.desc", "FORMA do Ferreiro (Círculo 5; homenagem à espada-flecha espiral). Você projeta uma espada torcida "
  "como broca, puxa como flecha (1,2 s) e dispara: ela atravessa até 3 criaturas (Perfurar +1) aplicando os efeitos e, no último "
  "alvo, num bloco ou no fim do voo (48 blocos), se DESFAZ numa explosão de mana de 4 blocos (Expandir +) que aplica os efeitos "
  "de novo com 120%% do poder — o \"Fantasma Partido\". Não quebra blocos. Dividir = mais flechas em leque. Custo: 2 cópias por "
  "flecha. Pode ser estágio. Desbloqueio: Provas da Forja, rank IV.",
  "Smith FORM (Circle 5; homage to the spiral sword-arrow). You project a sword twisted like a drill, draw it like an arrow (1.2 "
  "s) and fire: it pierces up to 3 creatures (Pierce +1) applying the effects and, at the last target, a block or the end of its "
  "flight (48 blocks), it BREAKS into a 4-block mana explosion (Expand +) that applies the effects again at 120%% power — the "
  "\"Broken Phantasm\". Does not break blocks. Split = more arrows in a fan. Cost: 2 copies per arrow. Can be a stage. Unlock: "
  "Forge Trials, rank IV.")
e(G + "smith_blade_world", "Mundo das Lâminas", "World of Blades")
e(G + "smith_blade_world.desc", "FORMA G do Ferreiro (Círculo 8, ultimate; homenagem ao \"Eu sou o osso da minha espada\"). Você "
  "recita o cântico e um anel de fogo corre pelo chão até 14 blocos (Expandir +). Quando fecha, o seu mundo interior se sobrepõe à "
  "realidade por 10 s (Estender +): céu de cinzas, engrenagens no alto e um campo de espadas cravadas. A cada 0,5 s até 6 "
  "espadas se arrancam do chão (ou caem do céu) e voam nos inimigos lá dentro, cada uma aplicando os efeitos; quem tenta sair é "
  "empurrado de volta pela borda. Não quebra blocos. Custo: pulsos moderados (1 + √rajadas). Desbloqueio: Provas da Forja, rank V.",
  "Smith G FORM (Circle 8, ultimate; homage to \"I am the bone of my sword\"). You recite the chant and a ring of fire runs "
  "across the ground up to 14 blocks (Expand +). When it closes, your inner world overlays reality for 10 s (Extend +): an ash "
  "sky, gears turning above and a field of planted swords. Every 0.5 s up to 6 swords tear free from the ground (or fall from "
  "the sky) and fly at the enemies inside, each applying the effects; anyone trying to leave is pushed back by the border. Does "
  "not break blocks. Cost: moderate pulses (1 + √volleys). Unlock: Forge Trials, rank V.")

# ------------------------------------------------------------------ escrita
VALID = re.compile(r"%(?:\d+\$)?s|%%")


def check(text, key):
    rest = VALID.sub("", text)
    if "%" in rest:
        sys.exit("'%' solto em " + key + ": " + text)


def main():
    keys = set()
    pt, en = {}, {}
    for key, p, n in E:
        if key in keys:
            sys.exit("chave repetida: " + key)
        keys.add(key)
        check(p, key)
        check(n, key)
        pt[key] = p
        en[key] = n
    os.makedirs(OUT, exist_ok=True)
    for name, data in (("pt_br", pt), ("en_us", en)):
        with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("class_smith: %d chaves de traducao" % len(E))


if __name__ == "__main__":
    main()
