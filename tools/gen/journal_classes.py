"""
Rota-guia por CLASSE do Diário (v1.0) — importado por tools/gen/journal.py.

Cada uma das 15 classes tem um capítulo próprio ("path": "<classe>") que corre EM PARALELO com a história principal:
guia a progressão do sistema da classe (ritual/ranks, itens, Partes do Cadáver, Mundo Interior, Auswählen, Forja,
cidades para o Ladino...) com os contadores/flags que os módulos de classe gravam. Os passos de rank aceitam o contador
próprio do módulo OU o geral ``ritual_rank`` (convenção de todas as classes).

Limites (o rastreador mostra "✦ " antes do título): título ≤ 24, dica curta ≤ 26, dica e fala ≤ 300 caracteres;
no máximo 10 passos por capítulo (cabem na aba do Diário sem rolagem).
"""

ROMAN = {1: "I", 2: "II", 3: "III", 4: "IV", 5: "V"}
OVERWORLD = "minecraft:overworld"


def _counter(key, n):
    return {"type": "counter", "key": key, "min": n}


def _any(*conds):
    return {"type": "any", "conditions": list(conds)}


def _all(*conds):
    return {"type": "all", "conditions": list(conds)}


# ---------------------------------------------------------------------------------------------------------------
#  Como cada classe grava o rank (contador/flag do módulo; o "ritual_rank" geral sempre vale também)
# ---------------------------------------------------------------------------------------------------------------
def rank_counter(key):
    return lambda n: _any(_counter(key, n), _counter("ritual_rank", n))


def rank_flag(flag_fmt, key):
    return lambda n: _any({"type": "flag", "flag": flag_fmt % n}, _counter(key, n), _counter("ritual_rank", n))


def rank_generic(n):
    return _counter("ritual_rank", n)


# ---------------------------------------------------------------------------------------------------------------
#  Montagem
# ---------------------------------------------------------------------------------------------------------------
def S(sid, cond, title, lore, hint, tip, xp=40, icon=None, optional=False, locate=None, rewards=None):
    return {"kind": "step", "id": sid, "cond": cond, "text": (title, lore, hint, tip), "xp": xp, "icon": icon,
            "optional": optional, "locate": locate, "rewards": rewards}


def R(n, lore, hint, tip, title=None):
    return {"kind": "rank", "n": n, "text": (title, lore, hint, tip)}


def build(step, items):
    """Devolve (capitulos, textos) no formato do journal.py (CHAPTERS / CH_TEXT)."""
    chapters, texts = [], {}
    for order, c in enumerate(CLASSES, 1):
        cid = "class_" + c["id"]
        steps, st = [], {}
        for s in c["steps"]:
            if s["kind"] == "rank":
                n = s["n"]
                title = s["text"][0] or (c["rank_title"][0] % ROMAN[n], c["rank_title"][1] % ROMAN[n])
                rewards = {"items": items(("mana_crystal", 2 + 2 * n))}
                if n >= 3:
                    rewards["items"] += items(("minecraft:golden_apple", 1))
                steps.append(step("rank_" + str(n), c["rank"](n), xp=60 * n, icon=c["rank_icon"], rewards=rewards,
                                  requires=c["requires"]))
                st["rank_" + str(n)] = (title, s["text"][1], s["text"][2], s["text"][3])
            else:
                steps.append(step(s["id"], s["cond"], optional=s["optional"], xp=s["xp"], icon=s["icon"] or c["rank_icon"],
                                  locate=s["locate"], rewards=s["rewards"], requires=c["requires"]))
                st[s["id"]] = s["text"]
        chapters.append({
            "id": cid, "path": c["id"], "order": order, "color": c["color"], "intro": True, "icon": c["icons"],
            "steps": steps,
            "rewards": {"xp": 2500, "items": items(("mana_crystal", 16), ("minecraft:enchanted_golden_apple", 1)),
                        "glyph_scrolls": 2, "glyph_tier": 4, "legend_scrolls": 1}})
        texts[cid] = {"title": c["title"], "lore": c["lore"], "outro": c["outro"], "intro": c["intro"], "steps": st}
    return chapters, texts


LESSON_NOTE_PT = "Glifos de origem são seus: outras classes só os aprendem por uma Lição que você escrever."
LESSON_NOTE_EN = "Origin glyphs are yours: other classes only learn them from a Lesson you write."

# =================================================================================================================
#  AS 15 CLASSES
# =================================================================================================================
CLASSES = [
    # -------------------------------------------------------------------------------------------- FERREIRO
    {"id": "smith", "color": "#E8742C", "rank": rank_counter("smith.rank"), "requires": ["isekai_mahou:smith_hammer"],
     "icons": ["isekai_mahou:smith_hammer", "isekai_mahou:smith_arcane_anvil", "isekai_mahou:smith_star_metal"],
     "rank_icon": "isekai_mahou:smith_hammer", "rank_title": ("Prova da Forja %s", "Forge Trial %s"),
     "title": ("A Forja Arcana", "The Arcane Forge"),
     "lore": ("Entre o martelo e a bigorna, o metal escuta. O Ferreiro não compra armas: conversa com o aço até ele nascer "
              "— a adaga que vira espada, a lâmina que aprende runas. Cada golpe na bigorna é um glifo, e cada arma é uma "
              "magia com cabo. Cinco Provas da Forja separam o aprendiz do Forjador de Lendas.",
              "Between hammer and anvil, the metal listens. A Smith does not buy weapons: they talk to the steel until it "
              "is born — the dagger that becomes a sword, the blade that learns runes. Every strike on the anvil is a "
              "glyph, and every weapon is a spell with a handle. Five Forge Trials stand between apprentice and Legend "
              "Forger."),
     "outro": ("O Aço Estelar canta quando você o toca. Nas forjas dos três reinos, os mestres tiram o avental quando você "
               "passa: o Forjador de Lendas chegou.",
               "Star Steel sings at your touch. In the forges of the three kingdoms, the masters take off their aprons "
               "as you pass: the Legend Forger has arrived."),
     "intro": ("Ferreiro! A sua rota é a Forja Arcana: erga a bigorna do kit, forje, remodele e vença as cinco Provas da "
               "Forja. Eu marco cada passo no rastreador, logo abaixo da história.",
               "Smith! Your route is the Arcane Forge: set up the anvil from your kit, forge, reshape and pass the five "
               "Forge Trials. I'll mark every step on the tracker, right below the story."),
     "steps": [
         S("anvil", _any({"type": "placed", "block": "smith_arcane_anvil"}, _counter("smith.forged", 1)),
           ("Erga a Bigorna Arcana", "Set up the Arcane Anvil"),
           ("A bigorna do kit não é ferro comum: veios de mana brilham nela sempre que o martelo desce.",
            "The anvil from your kit is no common iron: veins of mana glow in it whenever the hammer falls."),
           ("Coloque a Bigorna Arcana do seu kit num lugar seguro (a sua base é ótima). Clique nela para abrir a tela de "
            "montagem: é ali que as lâminas nascem e sobem de rank.",
            "Place the Arcane Anvil from your kit somewhere safe (your base is great). Click it to open the assembly "
            "screen: that is where blades are born and where you rank up."),
           ("Bigorna do kit no chão", "Place the kit's anvil"), xp=30, icon="isekai_mahou:smith_arcane_anvil"),
         S("forge", _counter("smith.forged", 1), ("Forje uma lâmina nova", "Forge a new blade"),
           ("Comprimento, largura, curva, ponta, serrilha, empunhadura e runa: a arma é montada como uma magia, peça por "
            "peça.",
            "Length, width, curve, tip, serration, grip and rune: the weapon is assembled like a spell, piece by piece."),
           ("Na Bigorna Arcana, escolha a família (adaga, espada, machado, lança, martelo, foice...) e gaste os pontos de "
            "forja nas peças. Confirme com os lingotes no inventário. Depois, remodele com os glifos de forja (Alongar, "
            "Alargar...) com a lâmina na mão.",
            "At the Arcane Anvil, pick the family (dagger, sword, axe, spear, hammer, scythe...) and spend forge points on "
            "the parts. Confirm with ingots in your inventory. Then reshape it with the forge glyphs (Lengthen, Widen...) "
            "with the blade in hand."),
           ("Bigorna → forjar", "Anvil → forge"), icon="isekai_mahou:smith_forged_blade"),
         R(1, ("Aprendiz da Forja: o metal já reconhece a sua mão.", "Forge Apprentice: the metal already knows your hand."),
           ("Nível 5, Círculo 1, 1 lâmina forjada, 5 remodelagens e 20 abates com Lâmina Forjada ou martelo. Leve 8 lingotes de "
            "ferro e clique na Bigorna Arcana. As tarefas ficam na aba Forja (B). Ganha Alargar e Serrilhar.",
            "Level 5, Circle 1, 1 forged blade, 5 reshapes and 20 kills with a Forged Blade or hammer. Bring 8 iron ingots and click "
            "the Arcane Anvil. The tasks are in the Forge tab (B). You learn Widen and Serrate."),
           ("Nv 5 · 8 ferros · Bigorna", "Lv 5 · 8 iron · Anvil")),
         S("rune", _counter("smith.runes", 1), ("Grave uma runa", "Engrave a rune"),
           ("Uma runa gravada a quente guarda magia no aço: a lâmina passa a lembrar o elemento de quem a forjou.",
            "A rune engraved while hot keeps magic in the steel: the blade remembers the element of whoever forged it."),
           ("Grave uma runa numa lâmina pela Bigorna Arcana (o glifo Gravar Runa chega no rank III). Para o rank II também "
            "conta minerar 64 minérios: o Ferreiro sente cada veio de metal.",
            "Engrave a rune on a blade at the Arcane Anvil (the Engrave Rune glyph comes at rank III). For rank II you "
            "also need to mine 64 ores: a Smith feels every metal vein."),
           ("Runa pela Bigorna", "Rune at the Anvil"), xp=60, icon="minecraft:lapis_lazuli"),
         R(2, ("Artesão: as suas armas já têm fama nas tavernas.", "Artisan: your weapons are already famous in taverns."),
           ("Nível 15, Círculo 2, 64 minérios, 1 runa, 20 remodelagens e 80 abates com as suas armas. Oferenda na bigorna: 8 ferros "
            "e 4 ouros. Ganha Fosquear, Chuva de Fagulhas e a Arma Conjurada.",
            "Level 15, Circle 2, 64 ores, 1 rune, 20 reshapes and 80 kills with your weapons. Anvil offering: 8 iron and 4 gold. "
            "You learn Matte, Spark Shower and Conjured Weapon."),
           ("Nv 15 · ferro + ouro", "Lv 15 · iron + gold")),
         S("families", {"type": "flag_prefix", "prefix": "smith.family.", "min": 3},
           ("Forje 3 famílias", "Forge 3 weapon families"),
           ("Um mestre não tem arma favorita: tem a arma certa para cada inimigo.",
            "A master has no favorite weapon: only the right weapon for each enemy."),
           ("Forje armas de famílias diferentes (adaga, espada, machado, lança, martelo, foice...). Para o rank III também "
            "contam Círculo 4, uma lâmina de Diamante, 5 elites vencidos com as suas armas e 1 chefe.",
            "Forge weapons of different families (dagger, sword, axe, spear, hammer, scythe...). For rank III you also "
            "need Circle 4, a Diamond blade, 5 elites defeated with your weapons and 1 boss."),
           ("3 tipos de arma", "3 weapon types"), xp=80, icon="minecraft:iron_axe"),
         R(3, ("Mestre Ferreiro: o Campo de Lâminas responde ao seu chamado.",
               "Master Smith: the Blade Field answers your call."),
           ("Nível 30, Círculo 4, 3 famílias, lâmina de Diamante, 5 elites, 1 chefe e 200 abates. Oferenda: 4 diamantes. Ganha Gravar Runa, "
            "Reforjar e o Campo de Lâminas (monte-os no Grimório com os seus elementos!).",
            "Level 30, Circle 4, 3 families, a Diamond blade, 5 elites, 1 boss and 200 kills. Offering: 4 diamonds. You learn Engrave Rune, "
            "Reforge and Blade Field (build them in the Grimoire with your elements!)."),
           ("Nv 30 · 4 diamantes", "Lv 30 · 4 diamonds")),
         R(4, ("Grão-Mestre da Bigorna: a Forja Estelar acende no céu.", "Anvil Grandmaster: the Star Forge lights the sky."),
           ("Nível 50, Círculo 6, lâmina de Netherite, 20 elites, 150 magias com técnicas do Ferreiro e 2 chefes. Oferenda: 2 "
            "netherites. Ganha o Portão do Arsenal, a forma G Forja Estelar e 2 Aços Estelares.",
            "Level 50, Circle 6, a Netherite blade, 20 elites, 150 casts of Smith techniques and 2 bosses. Offering: 2 netherite. "
            "You learn Arsenal Gate, the G form Star Forge and get 2 Star Steel."),
           ("Nv 50 · 2 netherites", "Lv 50 · 2 netherite")),
         S("masterwork", {"type": "flag", "flag": "smith.masterwork"}, ("Crie uma Obra-Prima", "Craft a Masterwork"),
           ("Cinco estrelas, nenhum ponto de forja desperdiçado. Uma arma assim ganha nome próprio.",
            "Five stars, not a single forge point wasted. A weapon like this earns its own name."),
           ("Forje uma lâmina ★★★★★ usando todos os pontos de forja (o Aço Estelar do rank IV ajuda muito). Para o rank V: "
            "Círculo 8, lâmina de Aço Estelar, 4 chefes diferentes e 600 abates.",
            "Forge a ★★★★★ blade using every forge point (the Star Steel from rank IV helps a lot). For rank V: a Star "
            "Steel blade, Circle 8, 4 different bosses and 600 kills."),
           ("★★★★★ na Bigorna", "★★★★★ at the Anvil"), xp=300, icon="isekai_mahou:smith_star_metal"),
         R(5, ("Forjador de Lendas: o seu martelo entra para a história.", "Legend Forger: your hammer enters history."),
           ("Nível 70, Círculo 8, lâmina de Aço Estelar, Obra-Prima, 4 chefes e 600 abates. Oferenda: 1 Estrela do Nether. Ganha o "
            "martelo de grau IV e as 2 Lendas do Ferreiro. " + LESSON_NOTE_PT,
            "Level 70, Circle 8, a Star Steel blade, a Masterwork, 4 bosses and 600 kills. Offering: 1 Nether Star. You get the "
            "tier IV hammer and both Smith Legends. " + LESSON_NOTE_EN),
           ("Nv 70 · Estrela do Nether", "Lv 70 · Nether Star")),
     ]},

    # -------------------------------------------------------------------------------------------- LADINO
    {"id": "rogue", "color": "#8E5BD6", "rank": rank_counter("ritual_rank.rogue"), "requires": ["isekai_mahou:rogue_dagger"],
     "icons": ["isekai_mahou:rogue_dagger", "isekai_mahou:rogue_ledger", "isekai_mahou:rogue_stolen_grimoire"],
     "rank_icon": "isekai_mahou:rogue_ledger", "rank_title": ("Livro-Razão: rank %s", "Ledger: rank %s"),
     "title": ("O Livro-Razão", "The Thief's Ledger"),
     "lore": ("Tudo neste mundo tem dono — até as magias. O Ladino discorda. Bolsos de goblins, a mana de um mago, a bênção "
              "de um paladino, o feitiço que acabaram de lançar contra você: tudo cabe no Livro-Razão do Ladrão, desde que "
              "ninguém veja. Cinco ranks, do Gatuno ao Rei dos Ladrões.",
              "Everything in this world has an owner — even spells. The Rogue disagrees. Goblin pockets, a mage's mana, a "
              "paladin's blessing, the spell just cast at you: it all fits in the Thief's Ledger, as long as nobody "
              "sees. Five ranks, from Cutpurse to King of Thieves."),
     "outro": ("Nenhum cofre de reino está seguro, e todos sabem disso. Mas ninguém sabe o seu rosto: o Rei dos Ladrões não "
               "precisa de coroa.",
               "No royal vault is safe, and everyone knows it. But nobody knows your face: the King of Thieves needs no "
               "crown."),
     "intro": ("Ladino! O seu Livro-Razão está no kit: furte, apunhale pelas costas, roube mana e até magias. Cuidado com "
               "os guardas das cidades... ou aprenda a convencê-los.",
               "Rogue! Your Ledger is in your kit: steal, backstab, drain mana and even spells. Watch out for city "
               "guards... or learn to talk them down."),
     "steps": [
         S("pickpocket", _counter("rogue.steal", 1), ("Bata a 1ª carteira", "Pick your first pocket"),
           ("Todo mundo carrega alguma coisa. O segredo é chegar perto sem que o dono perceba.",
            "Everyone carries something. The secret is getting close without the owner noticing."),
           ("Use Mão Leve (glifo do Ladino nos seus slots) num mob ou NPC: leva um item do saque ou do bolso dele. Pelas "
            "costas a chance é maior. Cada alvo tem um bolso só.",
            "Use Light Hand (Rogue glyph in your slots) on a mob or NPC: it takes an item from their loot or pocket. "
            "From behind the chance is higher. Each target has a single pocket."),
           ("Mão Leve num alvo", "Light Hand on a target"), icon="minecraft:emerald"),
         S("backstab", _counter("rogue.backstab", 20), ("20 golpes pelas costas", "20 backstabs"),
           ("Um passo de sombra, e o inimigo descobre tarde demais de que lado você estava.",
            "One shadow step, and the enemy learns too late which side you were on."),
           ("Acerte inimigos pelas costas (ou distraídos, ou com você invisível): o dano é ×2,5. O Passo das Sombras te "
            "teleporta para trás do alvo. Para o rank I: nível 5, 10 furtos e 20 pelas costas.",
            "Hit enemies from behind (or distracted, or while you are invisible): damage is ×2.5. Shadow Step teleports "
            "you behind the target. For rank I: level 5, 10 thefts and 20 backstabs."),
           ("Ataque pelas costas", "Strike from behind"), icon="isekai_mahou:rogue_dagger"),
         R(1, ("Gatuno: o primeiro nome no Livro-Razão é o seu.", "Cutpurse: the first name in the Ledger is yours."),
           ("Nível 5, Círculo 1, 10 furtos e 20 golpes pelas costas. Use o Livro-Razão (ou a aba Ladino no B) para subir. "
            "Ganha Ponto Vital e Sifão de Mana. Cada rank também dá +2% de crítico.",
            "Level 5, Circle 1, 10 thefts and 20 backstabs. Use the Ledger (or the Rogue tab in B) to rank up. You learn "
            "Vital Point and Mana Siphon. Every rank also gives +2% crit."),
           ("Nv 5 · use o Livro-Razão", "Lv 5 · use the Ledger")),
         S("city", _all(_any(_counter("visit.city", 1), _counter("visit.capital", 1)), _counter("rogue.steal", 40)),
           ("Bolsos da cidade", "City pockets"),
           ("Mercadores gordos, nobres distraídos e guardas atentos. A cidade é o paraíso — e a prisão — do Ladino.",
            "Fat merchants, distracted nobles and watchful guards. The city is a Rogue's paradise — and prison."),
           ("Chegue a 40 furtos com a cidade no caminho. Roubar um morador à vista de um guarda é crime: −reputação, e ele "
            "vem tirar satisfação. Fale com ele para Persuadir, pagar propina ou devolver o item.",
            "Reach 40 thefts with a city on your way. Robbing a resident in front of a guard is a crime: −reputation, and "
            "they come to confront you. Talk to them to Persuade, bribe or give the item back."),
           ("40 furtos · olho no guarda", "40 thefts · mind guards"), xp=80, icon="minecraft:bell"),
         R(2, ("Larápio das Sombras: até a mana dos outros vira sua.", "Shadow Filcher: even other people's mana is yours."),
           ("Nível 15, Círculo 2, 40 furtos, 80 pelas costas, 1000 de recurso roubado (Sifão) e 15 Pontos Vitais. Ganha "
            "Roubar Magia, Roubar Bênção, Página Roubada e a Presa da Sombra (adaga II).",
            "Level 15, Circle 2, 40 thefts, 80 backstabs, 1000 resource stolen (Siphon) and 15 Vital Points. You learn "
            "Spell Theft, Blessing Theft, Stolen Page and get Shadow Fang (dagger II)."),
           ("Nv 15 · C2 · Livro-Razão", "Lv 15 · C2 · Ledger")),
         S("spells", _counter("rogue.spell_stolen", 8), ("Roube 8 magias", "Steal 8 spells"),
           ("A magia que o inimigo levou anos para escrever, você leva num segundo para copiar.",
            "The spell an enemy took years to write, you copy in a second."),
           ("Roubar Magia copia a receita de quem acabou de conjurar contra você (mob, chefe ou jogador) para o Grimório "
            "Roubado; Página Roubada conjura essas páginas. Para o rank III: 6 bênçãos roubadas e 1 chefe furtado.",
            "Spell Theft copies the recipe of whoever just cast at you (mob, boss or player) into the Stolen Grimoire; "
            "Stolen Page casts those pages. For rank III: 6 blessings stolen and 1 boss pickpocketed."),
           ("Roubar Magia em magos", "Spell Theft on casters"), xp=120,
           icon="isekai_mahou:rogue_stolen_grimoire"),
         R(3, ("Sombra da Guilda: os outros ladrões abaixam a cabeça.", "Guild Shadow: other thieves lower their heads."),
           ("Nível 35, Círculo 4, 100 furtos, 200 pelas costas, 8 magias e 6 bênçãos roubadas e 1 chefe furtado (Mão Leve "
            "no chefe durante a luta). Ganha o Leque de Facas.",
            "Level 35, Circle 4, 100 thefts, 200 backstabs, 8 spells and 6 blessings stolen and 1 boss pickpocketed (Light "
            "Hand on the boss mid-fight). You learn Knife Fan."),
           ("Nv 35 · C4 · furtar chefe", "Lv 35 · C4 · rob a boss")),
         R(4, ("Mestre-Ladrão: o Golpe do Ladrão Fantasma.", "Master Thief: the Phantom Heist."),
           ("Nível 55, Círculo 6, 200 furtos, 400 pelas costas, 20 magias roubadas, 15 Páginas conjuradas e 2 chefes "
            "furtados. Ganha a forma G Golpe do Ladrão Fantasma e o Ferrão do Mestre-Ladrão (adaga III).",
            "Level 55, Circle 6, 200 thefts, 400 backstabs, 20 spells stolen, 15 Pages cast and 2 bosses pickpocketed. "
            "You learn the G form Phantom Heist and get the Master Thief's Sting (dagger III)."),
           ("Nv 55 · C6 · 2 chefes", "Lv 55 · C6 · 2 bosses")),
         S("heist", _counter("rogue.heist", 10), ("10 Golpes Fantasma", "10 Phantom Heists"),
           ("Um salto de sombra em sombra, de bolso em bolso, e você reaparece rindo do outro lado da sala.",
            "A leap from shadow to shadow, pocket to pocket, and you reappear laughing on the other side of the room."),
           ("Monte a forma G Golpe do Ladrão Fantasma (com os efeitos que quiser) e use-a em grupos de inimigos. Para o "
            "rank V: 350 furtos, 800 pelas costas, 40 magias roubadas e 4 chefes furtados.",
            "Build the G form Phantom Heist (with any effects you like) and use it on groups of enemies. For rank V: 350 "
            "thefts, 800 backstabs, 40 spells stolen and 4 bosses pickpocketed."),
           ("Forma G no slot G", "G form in the G slot"), xp=250, icon="isekai_mahou:rogue_dagger"),
         R(5, ("Rei dos Ladrões: dois bolsos por alvo, e o mundo inteiro como alvo.",
               "King of Thieves: two pockets per target, and the whole world as a target."),
           ("Nível 75, Círculo 8 e os feitos do Livro-Razão. Ganha as 2 Lendas do Ladino, a Lâmina Fantasma (adaga IV), a "
            "4ª página do Grimório Roubado e bolso duplo. " + LESSON_NOTE_PT,
            "Level 75, Circle 8 and the Ledger deeds. You get both Rogue Legends, the Phantom Blade (dagger IV), the 4th "
            "page of the Stolen Grimoire and double pockets. " + LESSON_NOTE_EN),
           ("Nv 75 · C8 · Livro-Razão", "Lv 75 · C8 · Ledger")),
     ]},

    # -------------------------------------------------------------------------------------------- CAVALEIRO RÚNICO
    {"id": "knight", "color": "#7FA8FF", "rank": rank_counter("knight.oath"), "requires": ["isekai_mahou:knight_oath_altar"],
     "icons": ["isekai_mahou:runic_blade", "isekai_mahou:knight_oath_altar"],
     "rank_icon": "isekai_mahou:knight_oath_altar", "rank_title": ("Juramento %s", "Oath %s"),
     "title": ("Os Juramentos", "The Oaths"),
     "lore": ("A lâmina rúnica guarda magias como uma bainha guarda o aço: grave um feitiço nela e cada golpe solta uma "
              "runa. Cinco Juramentos no Altar transformam o escudeiro em Rei Cavaleiro — e seis espadas lendárias "
              "esperam quem fizer por merecê-las, da pedra de Caliburn ao lago onde dorme a verdadeira.",
              "A runic blade holds spells the way a sheath holds steel: engrave a spell on it and every swing releases a "
              "rune. Five Oaths at the Altar turn a squire into a Knight King — and six legendary swords await whoever "
              "earns them, from Caliburn's stone to the lake where the true one sleeps."),
     "outro": ("A Coroa Rúnica pesa menos do que você imaginava. Talvez porque as seis lâminas a carreguem junto.",
               "The Runic Crown weighs less than you expected. Perhaps because six blades carry it with you."),
     "intro": ("Cavaleiro Rúnico! Coloque o Altar do Juramento do kit, grave magias na sua lâmina e preste os cinco "
               "Juramentos. As espadas lendárias também nascem nesse altar.",
               "Runic Knight! Place the Oath Altar from your kit, engrave spells on your blade and swear the five Oaths. "
               "The legendary swords are born at that altar too."),
     "steps": [
         S("altar", _any({"type": "placed", "block": "knight_oath_altar"}, _counter("knight.oath", 1)),
           ("Erga o Altar", "Raise the Oath Altar"),
           ("Pedra branca, runas douradas e um encaixe em forma de espada: o altar espera a sua palavra.",
            "White stone, golden runes and a sword-shaped socket: the altar awaits your word."),
           ("Coloque o Altar do Juramento do seu kit num lugar que você vai visitar sempre (os Juramentos e as espadas "
            "lendárias são feitos nele). Clique nele para ver o que falta.",
            "Place the Oath Altar from your kit somewhere you will visit often (Oaths and legendary swords are made "
            "there). Click it to see what is missing."),
           ("Altar do kit no chão", "Place the kit's altar"), xp=30),
         S("engrave", _counter("knight.rune_proc", 25), ("25 golpes rúnicos", "25 runic strikes"),
           ("Uma magia inteira dentro da espada: o golpe sai, a runa brilha, o feitiço explode.",
            "A whole spell inside the sword: the blow lands, the rune glows, the spell bursts."),
           ("Conjure Gravar na Espada com uma magia montada: cada golpe da Lâmina Rúnica solta a próxima runa (um golpe "
            "rúnico). Para o Juramento I: 25 golpes rúnicos e 15 abates de espada.",
            "Cast Engrave on Sword with a built spell: every hit of the Runic Blade releases the next rune (a runic "
            "strike). For Oath I: 25 runic strikes and 15 sword kills."),
           ("Grave magias e golpeie", "Engrave, then strike"), icon="isekai_mahou:runic_blade"),
         R(1, ("Escudeiro Rúnico: a lâmina aprende a guardar magia.", "Runic Squire: the blade learns to hold magic."),
           ("Círculo 1, 25 golpes rúnicos e 15 abates de espada. Oferenda no altar: 8 ferros e 2 Essências da Luz. Ganha "
            "Aura de Espada e Investida Rúnica. O progresso fica na aba Juramentos (B).",
            "Circle 1, 25 runic strikes and 15 sword kills. Altar offering: 8 iron and 2 Light Essences. You learn Blade "
            "Aura and Runic Thrust. Progress is in the Oaths tab (B)."),
           ("C1 · ferro + luz · Altar", "C1 · iron + light · Altar")),
         R(2, ("Cavaleiro Gravado: a pedra do altar começa a ceder.", "Engraved Knight: the altar stone begins to give."),
           ("Círculo 2, 150 golpes rúnicos e 80 abates de espada. Oferenda: 6 ouros, 4 Essências da Luz e 4 da Terra. "
            "Ganha Égide Rúnica, Chuva de Espadas e a Lâmina Rúnica II.",
            "Circle 2, 150 runic strikes and 80 sword kills. Offering: 6 gold, 4 Light and 4 Earth Essences. You learn "
            "Runic Aegis, Sword Rain and get the Runic Blade II."),
           ("C2 · ouro + essências", "C2 · gold + essences")),
         S("caliburn", {"type": "flag", "flag": "knight.sword.knight_caliburn"}, ("Desperte Caliburn", "Awaken Caliburn"),
           ("A espada na pedra só sai para quem já venceu um chefe com a lâmina na mão.",
            "The sword in the stone only comes out for one who has beaten a boss blade in hand."),
           ("Derrote um chefe segurando a sua lâmina e volte ao Altar do Juramento: clique nele com a oferenda para "
            "despertar Caliburn. As outras espadas pedem o núcleo do guardião do elemento e abates com magias dele.",
            "Defeat a boss while holding your blade and return to the Oath Altar: click it with the offering to awaken "
            "Caliburn. The other swords ask for the element guardian's core and kills with that element's spells."),
           ("Chefe com a lâmina → Altar", "Boss with blade → Altar"), xp=300, icon="minecraft:golden_sword"),
         R(3, ("Cavaleiro da Távola: EXCALIBUR desperta como forma G.", "Knight of the Table: EXCALIBUR awakens as a G form."),
           ("Círculo 4, 400 golpes rúnicos, 200 abates de espada, 1 chefe e 1 espada lendária. Oferenda: 4 diamantes e 6 "
            "Cristais de Mana. Ganha a forma G Excalibur e a Lâmina Rúnica III.",
            "Circle 4, 400 runic strikes, 200 sword kills, 1 boss and 1 legendary sword. Offering: 4 diamonds and 6 Mana "
            "Crystals. You learn the G form Excalibur and get the Runic Blade III."),
           ("C4 · diamantes · Altar", "C4 · diamonds · Altar")),
         R(4, ("Paladino das Runas: a Sentença do Juramento cai do céu.", "Rune Paladin: the Oath's Sentence falls from the sky."),
           ("Círculo 6, 700 golpes rúnicos, 450 abates, 2 chefes e 2 espadas lendárias. Oferenda: 1 netherite, 8 "
            "Essências da Luz e 8 da Terra. Ganha a Sentença do Juramento, +1 espaço de runa e a Lâmina IV.",
            "Circle 6, 700 runic strikes, 450 kills, 2 bosses and 2 legendary swords. Offering: 1 netherite, 8 Light and "
            "8 Earth Essences. You learn Oath's Sentence, +1 rune slot and get the Blade IV."),
           ("C6 · 2 espadas lendárias", "C6 · 2 legendary swords")),
         S("four_swords", _counter("knight.legend_swords", 3), ("3 espadas lendárias", "3 legendary swords"),
           ("Carmesim, Tempestade, Gelo Eterno, Trevas: cada uma pede um feito do seu elemento.",
            "Crimson, Storm, Eternal Frost, Darkness: each one asks for a deed of its element."),
           ("Para cada espada elemental: o núcleo do guardião do elemento (Plano Elemental) e abates com magias dele; "
            "algumas pedem o altar num lugar certo (tempestade, gelo, noite). O Juramento V pede 3. O altar diz o que falta.",
            "For each elemental sword: the element guardian's core (Elemental Plane) and kills with its spells; some "
            "need the altar in the right place (storm, ice, night). Oath V asks for 3. The altar tells you what is missing."),
           ("Núcleos dos guardiões", "Guardian cores"), xp=500, icon="isekai_mahou:guardian_core_fire"),
         S("lake", {"type": "flag", "flag": "knight.sword.knight_lake_sword"}, ("A Espada do Lago", "The Sword of the Lake"),
           ("Caliburn se parte no altar cercado de água, e a Dama do Lago devolve a lâmina verdadeira.",
            "Caliburn breaks on the altar surrounded by water, and the Lady of the Lake returns the true blade."),
           ("Com Caliburn despertada, cerque o Altar do Juramento de água e clique nele com a oferenda. A Espada do Lago "
            "potencializa a sua forma G Excalibur.",
            "With Caliburn awakened, surround the Oath Altar with water and click it with the offering. The Sword of the "
            "Lake empowers your G form Excalibur."),
           ("Altar cercado de água", "Altar surrounded by water"), xp=600, optional=True, icon="minecraft:trident"),
         R(5, ("Rei Cavaleiro Rúnico: a Coroa Rúnica é sua.", "Runic Knight King: the Runic Crown is yours."),
           ("Círculo 8, 1200 golpes rúnicos, 800 abates, 4 chefes e 3 espadas lendárias. Oferenda: 1 Estrela do Nether e "
            "16 Cristais de Mana. Ganha a Coroa Rúnica (+15% nas técnicas, +armadura). " + LESSON_NOTE_PT,
            "Circle 8, 1200 runic strikes, 800 kills, 4 bosses and 3 legendary swords. Offering: 1 Nether Star and 16 "
            "Mana Crystals. You earn the Runic Crown (+15% techniques, +armor). " + LESSON_NOTE_EN),
           ("C8 · Estrela do Nether", "C8 · Nether Star")),
     ]},

    # -------------------------------------------------------------------------------------------- ENCANTADOR
    {"id": "enchanter", "color": "#5FE3C0", "rank": rank_counter("ritual_rank.enchanter"),
     "requires": ["isekai_mahou:enchanter_codex"],
     "icons": ["isekai_mahou:enchanter_gauntlets", "isekai_mahou:enchanter_codex", "minecraft:enchanting_table"],
     "rank_icon": "isekai_mahou:enchanter_codex", "rank_title": ("Inscrição %s", "Inscription %s"),
     "title": ("A Inscrição", "The Inscription"),
     "lore": ("Força, celeridade, égide, vitalidade: o Encantador escreve poder em si e nos aliados como quem escreve num "
              "livro. E guarda duas páginas proibidas — a Anti-Magia, que faz feitiços morrerem na pele, e a Restrição "
              "Celestial, que troca toda a mana por um corpo que nenhuma magia enxerga.",
              "Might, haste, aegis, vitality: the Enchanter writes power onto themselves and their allies like writing in "
              "a book. And keeps two forbidden pages — Anti-Magic, which makes spells die on the skin, and Heavenly "
              "Restriction, which trades all mana for a body no magic can see."),
     "outro": ("O Soberano dos Encantos não precisa erguer a voz: os aliados ficam mais fortes só de estar perto, e os "
               "inimigos mais fracos só de lembrar o seu nome.",
               "The Sovereign of Enchantments never needs to raise their voice: allies grow stronger just by standing "
               "near, and enemies weaker just by remembering the name."),
     "intro": ("Encantador! O Códex do kit e uma mesa de encantamento são o seu altar: encante você e os aliados, anule "
               "magias com a Anti-Magia e grave os cinco ranks da Inscrição.",
               "Enchanter! The Codex from your kit and an enchanting table are your altar: enchant yourself and allies, "
               "nullify spells with Anti-Magic and engrave the five ranks of the Inscription."),
     "steps": [
         S("apply", _counter("enchanter.applied", 25), ("Aplique 25 encantos", "Apply 25 enchantments"),
           ("Um toque, uma runa na pele, e o corpo lembra que pode mais.",
            "One touch, a rune on the skin, and the body remembers it can do more."),
           ("Use Força, Celeridade e os outros encantamentos (glifos do Encantador nos seus slots) em você ou num aliado. "
            "Eles acumulam com limite. Para o rank I: nível 5, 25 aplicados e 15 abates encantado.",
            "Use Might, Haste and the other enchantments (Enchanter glyphs in your slots) on yourself or an ally. They "
            "stack up to a limit. For rank I: level 5, 25 applied and 15 kills while enchanted."),
           ("Encante você ou aliados", "Enchant self or allies"), icon="isekai_mahou:enchanter_gauntlets"),
         S("anti_magic", _counter("enchanter.nullified", 60), ("Anule 60 de magia", "Nullify 60 magic"),
           ("A bola de fogo bate no seu peito e vira fumaça. O mago inimigo olha para as próprias mãos sem entender.",
            "The fireball hits your chest and turns to smoke. The enemy mage stares at their own hands in confusion."),
           ("Mantenha a Anti-Magia ativa e encare conjuradores (bruxas, magos, chefes): ela anula boa parte do dano e dos "
            "efeitos de magias inimigas — mas também segura as suas magias ofensivas.",
            "Keep Anti-Magic active and face casters (witches, mages, bosses): it cancels much of the damage and effects "
            "of enemy spells — but also holds back your own offensive spells."),
           ("Anti-Magia contra magos", "Anti-Magic vs casters"), xp=60, icon="minecraft:shield"),
         R(1, ("Inscritor: a primeira runa se grava no seu braço.", "Inscriber: the first rune etches into your arm."),
           ("Círculo 1, nível 5, 25 encantos e 15 abates encantado. Fique ao lado de uma mesa de encantamento com 12 "
            "lápis e 4 ametistas e use o Códex nela (5 s de rito). Ganha Égide, Vitalidade e o Elo de Encantamento.",
            "Circle 1, level 5, 25 enchantments and 15 enchanted kills. Stand by an enchanting table with 12 lapis and 4 "
            "amethyst and use the Codex on it (5 s rite). You learn Aegis, Vitality and Enchantment Link."),
           ("Códex na mesa · lápis", "Codex on table · lapis")),
         S("ally", _counter("enchanter.ally_applied", 8), ("Encante 8 aliados", "Enchant 8 allies"),
           ("Um Encantador sozinho é forte. Um Encantador com um grupo é um exército.",
            "An Enchanter alone is strong. An Enchanter with a party is an army."),
           ("Monte um grupo (agache + scroll num jogador → Convidar) e use os encantamentos neles — o Elo de Encantamento "
            "espalha pelo grupo. Para o rank II: 80 encantos, 60 de magia anulada e 50 abates.",
            "Form a party (crouch + scroll on a player → Invite) and use enchantments on them — Enchantment Link spreads "
            "them to the party. For rank II: 80 enchantments, 60 magic nullified and 50 kills."),
           ("Grupo + encantos", "Party + enchantments"), xp=80, icon="minecraft:golden_apple"),
         R(2, ("Tecelão de Encantos: o Vento desperta.", "Enchantment Weaver: Wind awakens."),
           ("Círculo 2, nível 15, 80 encantos, 8 em aliados, 60 de magia anulada e 50 abates. Oferenda: 24 lápis, 2 "
            "Essências da Luz e 2 do Vento. Ganha Imbuir Arma, Sobrecarga e desperta o Vento.",
            "Circle 2, level 15, 80 enchantments, 8 on allies, 60 magic nullified and 50 kills. Offering: 24 lapis, 2 "
            "Light and 2 Wind Essences. You learn Imbue Weapon, Overdrive and awaken Wind."),
           ("C2 · Nv 15 · Códex", "C2 · Lv 15 · Codex")),
         S("overdrive", _counter("enchanter.overdrive", 5), ("5 Sobrecargas", "5 Overdrives"),
           ("Por alguns segundos tudo dobra — e depois o corpo cobra a conta.",
            "For a few seconds everything doubles — and then the body sends the bill."),
           ("Use Sobrecarga em você ou num aliado em lutas de verdade. Para o rank III: 200 encantos, 300 de magia anulada, "
            "100 abates e 1 chefe. A recompensa é a Restrição Celestial.",
            "Use Overdrive on yourself or an ally in real fights. For rank III: 200 enchantments, 300 magic nullified, "
            "100 kills and 1 boss. The reward is Heavenly Restriction."),
           ("Sobrecarga em combate", "Overdrive in combat"), xp=100, icon="minecraft:blaze_powder"),
         R(3, ("Quebra-Feitiço: a Restrição Celestial é sua.", "Spellbreaker: Heavenly Restriction is yours."),
           ("Círculo 4, nível 25, as tarefas do rank e 1 chefe. Oferenda: 32 lápis, 4 Cristais de Mana e 1 diamante. Ganha a "
            "Restrição Celestial (sem mana, corpo absurdo, invisível à detecção mágica) e a lenda Punho do Imperador.",
            "Circle 4, level 25, the rank tasks and 1 boss. Offering: 32 lapis, 4 Mana Crystals and 1 diamond. You learn Heavenly "
            "Restriction (no mana, absurd body, invisible to magic detection) and the Emperor's Fist legend."),
           ("C4 · Nv 25 · diamante", "C4 · Lv 25 · diamond")),
         S("heavenly", _counter("enchanter.restricted_kills", 30), ("30 abates na Restrição", "30 Restriction kills"),
           ("Nenhum feitiço, nenhuma mana. Só o corpo — e ele basta.", "No spell, no mana. Just the body — and it is enough."),
           ("Ative a Restrição Celestial e lute só no corpo a corpo: enquanto ela dura você não conjura, mas fica imune a "
            "acertos garantidos que miram mana. Para o rank IV: 400 encantos, 900 anulados, 25 em aliados e 2 chefes.",
            "Activate Heavenly Restriction and fight in melee only: while it lasts you cannot cast, but you are immune to "
            "sure-hits that target mana. For rank IV: 400 enchantments, 900 nullified, 25 on allies and 2 bosses."),
           ("Restrição + corpo a corpo", "Restriction + melee"), xp=200, icon="minecraft:netherite_chestplate"),
         R(4, ("Corpo Celestial: o equipamento também aprende a ser encantado.",
               "Celestial Body: your gear learns to be enchanted too."),
           ("Círculo 6, nível 45, as tarefas do rank e 2 chefes. Oferenda: 2 diamantes, 8 Cristais de Mana, 4 Essências da Luz e 4 "
            "do Vento. Ganha Equipamento Encantado e a lenda Corpo Celestial.",
            "Circle 6, level 45, the rank tasks and 2 bosses. Offering: 2 diamonds, 8 Mana Crystals, 4 Light and 4 Wind Essences. "
            "You learn Enchanted Gear and the Celestial Body legend."),
           ("C6 · Nv 45 · Códex", "C6 · Lv 45 · Codex")),
         R(5, ("Soberano dos Encantos: o Santuário do Soberano se abre.",
               "Sovereign of Enchantments: the Sovereign's Sanctum opens."),
           ("Círculo 8, nível 75, 800 encantos, 2500 anulados, 100 abates na Restrição e 4 chefes. Oferenda: 3 diamantes, "
            "16 cristais e 6 essências Arcana, Luz e Vento. Ganha a forma G Santuário do Soberano e uma lenda.",
            "Circle 8, level 75, 800 enchantments, 2500 nullified, 100 Restriction kills and 4 bosses. Offering: 3 "
            "diamonds, 16 crystals and 6 Arcane, Light and Wind essences. You learn the G form Sovereign's Sanctum and a "
            "legend."),
           ("C8 · Nv 75 · 4 chefes", "C8 · Lv 75 · 4 bosses")),
     ]},

    # -------------------------------------------------------------------------------------------- SHINIGAMI
    {"id": "shinigami", "color": "#D9483B", "rank": rank_counter("ritual_rank.shinigami"),
     "requires": ["isekai_mahou:zanpakuto"],
     "icons": ["isekai_mahou:zanpakuto", "minecraft:netherite_sword"],
     "rank_icon": "isekai_mahou:zanpakuto", "rank_title": ("Zanpakutō: rank %s", "Zanpakutō: rank %s"),
     "title": ("A Zanpakutō", "The Zanpakutō"),
     "lore": ("A sua espada tem nome — você só ainda não o ouviu. Meditando, o Shinigami desce ao próprio Mundo Interior, "
              "onde o espírito da lâmina espera para conversar... ou para lutar. Selada, Shikai, Bankai: cada liberação "
              "é uma promessa entre a alma e o aço. E o Kidō, Hadō e Bakudō, cabe inteiro no Grimório.",
              "Your sword has a name — you just haven't heard it yet. Meditating, the Shinigami descends into their own "
              "Inner World, where the blade's spirit waits to talk... or to fight. Sealed, Shikai, Bankai: each release "
              "is a promise between soul and steel. And Kidō, Hadō and Bakudō, fits whole in the Grimoire."),
     "outro": ("Quando você diz \"Bankai\", o ar muda de peso. O espírito da espada sorri no fundo da sua alma: vocês dois, "
               "finalmente, falam a mesma língua.",
               "When you say \"Bankai\", the air changes weight. The sword's spirit smiles deep in your soul: the two of "
               "you finally speak the same language."),
     "intro": ("Shinigami! A sua Zanpakutō tem alma. Medite agachado e parado para descer ao Mundo Interior, use Kidō e "
               "suba os ranks até a Shikai — e um dia, a Bankai.",
               "Shinigami! Your Zanpakutō has a soul. Meditate (crouch and stand still) to descend into the Inner World, "
               "use Kidō and climb the ranks to Shikai — and one day, Bankai."),
     "steps": [
         S("meditate", _any(_counter("shinigami.meditations", 1), {"type": "visit", "dimension": "isekai_mahou:inner_world"}),
           ("Medite com a Zanpakutō", "Meditate with the blade"),
           ("Silêncio, respiração lenta, e a espada no colo. Do outro lado dos olhos fechados, há um mundo inteiro.",
            "Silence, slow breathing, the sword on your lap. On the other side of closed eyes, there is a whole world."),
           ("Com a Zanpakutō na mão, fique agachado e parado: o Shinigami medita (regeneração espiritual bem maior) e, "
            "concentrado, desce ao Mundo Interior para falar com o espírito da espada.",
            "With the Zanpakutō in hand, crouch and stand still: the Shinigami meditates (much stronger spiritual "
            "regeneration) and, focused, descends into the Inner World to speak with the sword's spirit."),
           ("Agache parado c/ a espada", "Crouch still, sword out"), xp=40),
         S("kido", _counter("shinigami.kido", 10), ("Use Kidō 10 vezes", "Use Kidō 10 times"),
           ("Hadō para ferir, Bakudō para prender. Palavras antigas que viram luz nas suas mãos.",
            "Hadō to harm, Bakudō to bind. Ancient words that turn into light in your hands."),
           ("Os Kidō são glifos do Shinigami: monte o Hadō nº 4 (e os outros quando aprender) no Grimório, com os "
            "efeitos que quiser, e use-o em combate. Os ranks pedem abates, Kidō e meditações.",
            "Kidō are Shinigami glyphs: build Hadō #4 (and the others as you learn them) in the Grimoire, with any "
            "effects you like, and use it in combat. Ranks ask for kills, Kidō and meditations."),
           ("Hadō no Grimório", "Hadō in the Grimoire"), xp=60, icon="minecraft:blaze_rod"),
         R(1, ("A lâmina selada responde ao seu chamado.", "The sealed blade answers your call."),
           ("Cumpra os feitos do rank I (abates com a Zanpakutō, Kidō e meditação) e suba pela aba do Shinigami no menu B. "
            "Cada rank deixa as técnicas Shinigami mais fortes (+4% por rank).",
            "Complete the rank I deeds (Zanpakutō kills, Kidō and meditation) and rank up from the Shinigami tab in menu "
            "B. Each rank makes Shinigami techniques stronger (+4% per rank)."),
           ("Feitos → aba Shinigami (B)", "Deeds → Shinigami tab (B)")),
         R(2, ("O espírito da espada aceita conversar.", "The sword's spirit agrees to talk."),
           ("Mais abates, Kidō e meditações. No rank II o Mundo Interior se abre de verdade: lá você ouve o nome da espada "
            "e escolhe o arquétipo da sua Shikai.",
            "More kills, Kidō and meditations. At rank II the Inner World truly opens: there you hear the sword's name "
            "and choose your Shikai archetype."),
           ("Feitos → aba Shinigami", "Deeds → Shinigami tab")),
         S("name", _counter("shinigami.archetype", 1), ("Ouça o nome da espada", "Hear the sword's name"),
           ("Peso, Pétalas de Lâmina, Gelo, Chama Ancestral ou Pincel Branco. A espada já sabia; faltava você saber.",
            "Weight, Blade Petals, Frost, Ancestral Flame or White Brush. The sword already knew; you just had to learn."),
           ("Medite até descer ao Mundo Interior e fale com o espírito da espada (ou vença-o, se ele exigir). Escolha o "
            "arquétipo: Peso deixa o alvo mais pesado a cada corte; Pincel Branco apaga as magias dele por um tempo.",
            "Meditate until you reach the Inner World and talk to the sword's spirit (or defeat it, if it demands). "
            "Choose the archetype: Weight makes the target heavier with every cut; White Brush erases their spells."),
           ("Mundo Interior: arquétipo", "Inner World: archetype"), xp=150, icon="minecraft:ender_eye"),
         R(3, ("Shikai: diga o comando e a lâmina muda de forma.", "Shikai: speak the command and the blade changes shape."),
           ("Cumpra os feitos do rank III. A Shikai do seu arquétipo vira um glifo de origem: monte-a no Grimório com "
            "estágios e elementos, como qualquer magia.",
            "Complete the rank III deeds. Your archetype's Shikai becomes an origin glyph: build it in the Grimoire with "
            "stages and elements, like any spell."),
           ("Feitos → aba Shinigami", "Deeds → Shinigami tab")),
         S("release", _counter("shinigami.releases", 1), ("Libere a Shikai", "Release your Shikai"),
           ("O nome dito em voz alta, a lâmina se desfaz e renasce. O campo de batalha muda de dono.",
            "The name spoken aloud, the blade dissolves and is reborn. The battlefield changes owner."),
           ("Use a liberação da Shikai em combate (cortes com a Shikai contam para os próximos ranks). Senkaimon: os "
            "Shinigami têm atalho para o Mundo Oco.",
            "Use the Shikai release in combat (Shikai cuts count for the next ranks). Senkaimon: Shinigami have a "
            "shortcut to the Hollow World."),
           ("Liberação em combate", "Release in combat"), xp=200, icon="isekai_mahou:zanpakuto"),
         R(4, ("A espada pede a última prova.", "The sword asks for the final trial."),
           ("Muitos cortes com a Shikai, liberações e chefes vencidos. Volte ao Mundo Interior quando a aba disser que "
            "está pronto.",
            "Many Shikai cuts, releases and bosses defeated. Return to the Inner World when the tab says you are ready."),
           ("Feitos → aba Shinigami", "Deeds → Shinigami tab")),
         S("hollow", {"type": "visit", "dimension": "isekai_mahou:hollow_world"}, ("Atravesse o Senkaimon", "Cross the Senkaimon"),
           ("Do outro lado do portão de papel e madeira, o deserto branco do Mundo Oco.",
            "Beyond the paper-and-wood gate, the white desert of the Hollow World."),
           ("Os Shinigami abrem o Portão Senkai (glifo de atalho) para o Mundo Oco, onde os Vazios rondam a lua eterna. "
            "Leve um grupo: os chefes do palácio não perdoam.",
            "Shinigami open the Senkai Gate (shortcut glyph) to the Hollow World, where Hollows roam under the eternal "
            "moon. Bring a party: the palace bosses show no mercy."),
           ("Portão Senkai", "Senkai Gate"), xp=300, optional=True, icon="isekai_mahou:hollow_mask_fragment"),
         R(5, ("Bankai.", "Bankai."),
           ("Os feitos do rank V. A Bankai do seu arquétipo vira a sua forma mais forte — monte-a no slot G. "
            + LESSON_NOTE_PT,
            "The rank V deeds. Your archetype's Bankai becomes your strongest form — build it in the G slot. "
            + LESSON_NOTE_EN),
           ("Feitos → Bankai", "Deeds → Bankai"), title=("Bankai", "Bankai")),
     ]},

    # -------------------------------------------------------------------------------------------- QUINCY
    {"id": "quincy", "color": "#A8DFFF", "rank": rank_counter("ritual_rank.quincy"), "requires": ["isekai_mahou:quincy_cross"],
     "icons": ["isekai_mahou:quincy_cross", "isekai_mahou:quincy_seele_schneider", "isekai_mahou:quincy_silver_tube"],
     "rank_icon": "isekai_mahou:quincy_cross", "rank_title": ("Auswählen: rank %s", "Auswählen: rank %s"),
     "title": ("O Auswählen", "The Auswählen"),
     "lore": ("Um Quincy não cria energia: ele a reúne. Do ar, dos projéteis inimigos, das magias que voam na sua direção "
              "— tudo vira flecha no arco espiritual. E há um ritual antigo, o Auswählen, que absorve o poder de outros "
              "Quincy rendidos. Cinco ranks, uma Letra, e no fim a forma completa: Vollständig.",
              "A Quincy does not create energy: they gather it. From the air, from enemy projectiles, from spells flying "
              "their way — all of it becomes arrows on the spirit bow. And there is an ancient rite, the Auswählen, that "
              "absorbs the power of surrendered Quincy. Five ranks, one Letter, and at the end the complete form: "
              "Vollständig."),
     "outro": ("As asas de luz se abrem e o céu inteiro vira arsenal. Em algum acampamento distante, um velho Quincy "
               "ajoelha: o Rei voltou.",
               "The wings of light unfold and the whole sky becomes an arsenal. In some distant camp, an old Quincy kneels: "
               "the King has returned."),
     "intro": ("Quincy! A Cruz do kit vira o seu Arco Espiritual. Absorva projéteis, devolva tudo em flechas, e procure os "
               "acampamentos Quincy: o Auswählen é o seu caminho para crescer.",
               "Quincy! The Cross from your kit becomes your Spirit Bow. Absorb projectiles, send it all back as arrows, "
               "and seek the Quincy camps: the Auswählen is your path to grow."),
     "steps": [
         S("absorb", _counter("quincy.absorbed", 15), ("Absorva 15 projéteis", "Absorb 15 projectiles"),
           ("Uma flecha de esqueleto vem na sua direção e some num brilho azul. Agora ela é sua.",
            "A skeleton's arrow flies at you and vanishes in a blue glow. Now it's yours."),
           ("A passiva Quincy engole projéteis que te acertam; a forma Absorção (glifo) puxa tudo por perto e devolve como "
            "flechas. Esqueletos e magos são ótimos para treinar.",
            "The Quincy passive swallows projectiles that hit you; the Absorption form (glyph) pulls in everything nearby "
            "and returns it as arrows. Skeletons and mages are great for training."),
           ("Deixe projéteis acertarem", "Let projectiles hit you"), icon="minecraft:spectral_arrow"),
         S("bow", _counter("quincy.kills", 20), ("20 abates Quincy", "20 Quincy kills"),
           ("A Cruz brilha e se abre num arco de luz. Não há corda: só a sua vontade.",
            "The Cross glows and opens into a bow of light. There is no string: only your will."),
           ("Derrote inimigos com técnicas Quincy (Arco Espiritual e os glifos de origem). Para o rank I: Círculo 1, 20 "
            "abates e 15 absorções; suba pela aba Quincy (B).",
            "Defeat enemies with Quincy techniques (Spirit Bow and the origin glyphs). For rank I: Circle 1, 20 kills and "
            "15 absorptions; rank up in the Quincy tab (B)."),
           ("Arco Espiritual", "Spirit Bow"), icon="isekai_mahou:quincy_cross"),
         R(1, ("Aprendiz do Arco: a luz obedece.", "Bow Apprentice: the light obeys."),
           ("Círculo 1, 20 abates Quincy e 15 absorções. Suba na aba Quincy (B). Ganha Absorção, Liberação e 8 Tubos de "
            "Prata.",
            "Circle 1, 20 Quincy kills and 15 absorptions. Rank up in the Quincy tab (B). You learn Absorption, Release "
            "and get 8 Silver Tubes."),
           ("C1 · aba Quincy (B)", "C1 · Quincy tab (B)")),
         S("camp", _counter("quincy.auswahlen", 1), ("Faça um Auswählen", "Perform an Auswählen"),
           ("Tendas brancas com cruzes azuis: um acampamento Quincy. Eles não entregam o poder de graça.",
            "White tents with blue crosses: a Quincy camp. They do not hand over their power for free."),
           ("A seta aponta o Acampamento Quincy mais perto. Vença os Quincy até se renderem e faça o Auswählen neles (fique "
            "perto enquanto a luz flui). Jogadores Quincy também podem aceitar o seu.",
            "The arrow points to the nearest Quincy Camp. Defeat the Quincy until they surrender and perform the "
            "Auswählen on them (stay close while the light flows). Quincy players can also accept yours."),
           ("Siga a seta: acampamento", "Follow the arrow: camp"), xp=150, icon="minecraft:white_banner",
           locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:quincy_camp"}]}),
         R(2, ("Caçador Espiritual: a Seele Schneider canta.", "Spirit Hunter: the Seele Schneider sings."),
           ("Círculo 2, 80 abates Quincy, 60 absorções e 1 Auswählen. Ganha Seele Schneider (espada de energia), Blut, "
            "Tubo de Prata (glifo) e a Seele Schneider em item.",
            "Circle 2, 80 Quincy kills, 60 absorptions and 1 Auswählen. You learn Seele Schneider (energy blade), Blut, "
            "Silver Tube (glyph) and get the Seele Schneider item."),
           ("C2 · 1 Auswählen", "C2 · 1 Auswählen")),
         R(3, ("Portador da Letra: escolha o seu Schrift.", "Letter Bearer: choose your Schrift."),
           ("Círculo 4, 200 abates, 2 Auswählen e 1 chefe. Ganha Chuva de Luz e o poder de escolher a sua Letra (um "
            "poder modesto, só seu).",
            "Circle 4, 200 kills, 2 Auswählen and 1 boss. You learn Rain of Light and the right to choose your Letter (a "
            "modest power of your own)."),
           ("C4 · 2 Auswählen · chefe", "C4 · 2 Auswählen · boss")),
         S("letter", _counter("quincy.letter", 1), ("Escolha a sua Letra", "Choose your Letter"),
           ("Uma letra do alfabeto antigo queima na sua alma. Ela diz quem você é em batalha.",
            "A letter of the ancient alphabet burns in your soul. It says who you are in battle."),
           ("Na aba Quincy (B), escolha a Letra (Schrift). Depois use o glifo Letra em combate: o rank IV pede 40 usos, "
            "200 absorções, 4 Auswählen e 2 chefes (Círculo 6).",
            "In the Quincy tab (B), choose your Letter (Schrift). Then use the Letter glyph in combat: rank IV asks for 40 "
            "uses, 200 absorptions, 4 Auswählen and 2 bosses (Circle 6)."),
           ("Aba Quincy → Letra", "Quincy tab → Letter"), xp=150, icon="minecraft:writable_book"),
         R(4, ("Cavaleiro Estelar: a tempestade de luz.", "Star Knight: the storm of light."),
           ("Círculo 6, 200 absorções, 4 Auswählen, 40 usos da Letra e 2 chefes. Ganha o pergaminho de uma Lenda Quincy.",
            "Circle 6, 200 absorptions, 4 Auswählen, 40 Letter uses and 2 bosses. You get a Quincy Legend scroll."),
           ("C6 · 4 Auswählen", "C6 · 4 Auswählen")),
         S("reishi", {"type": "visit", "dimension": "isekai_mahou:hollow_world"}, ("A Ponte de Reishi", "The Reishi Bridge"),
           ("Os Quincy caçam Vazios há gerações. A ponte de partículas leva direto ao deserto deles.",
            "Quincy have hunted Hollows for generations. The bridge of particles leads straight to their desert."),
           ("Os Quincy abrem a Ponte de Reishi (glifo de atalho) para o Mundo Oco. Os Vazios e os Menos rendem muita "
            "absorção — e os chefes do palácio contam para os ranks.",
            "Quincy open the Reishi Bridge (shortcut glyph) to the Hollow World. Hollows and Menos give plenty of "
            "absorption — and the palace bosses count for your ranks."),
           ("Ponte de Reishi", "Reishi Bridge"), xp=300, optional=True, icon="isekai_mahou:reishi_crystal"),
         R(5, ("Vollständig: a forma completa.", "Vollständig: the complete form."),
           ("Círculo 8, 600 abates Quincy, 6 Auswählen e 4 chefes. Ganha a forma G Vollständig e a segunda Lenda. "
            + LESSON_NOTE_PT,
            "Circle 8, 600 Quincy kills, 6 Auswählen and 4 bosses. You learn the G form Vollständig and the second "
            "Legend. " + LESSON_NOTE_EN),
           ("C8 · 6 Auswählen · chefes", "C8 · 6 Auswählen · bosses")),
     ]},

    # -------------------------------------------------------------------------------------------- PISTOLEIRO
    {"id": "gunslinger", "color": "#D9A441", "rank": rank_flag("gunslinger.rank.%d", "ritual_rank.gunslinger"),
     "requires": ["isekai_mahou:mana_revolver"],
     "icons": ["isekai_mahou:mana_revolver", "minecraft:fire_charge"],
     "rank_icon": "isekai_mahou:mana_revolver", "rank_title": ("Gatilho: rank %s", "Trigger: rank %s"),
     "title": ("O Gatilho", "The Trigger"),
     "lore": ("A sua mana não sai em raios nem em chamas: sai pelo cano. Revólver, pistola, escopeta, pistolas duplas, "
              "fuzil — cada arma é feita de mana, e cada bala carrega a magia que você montar. O Caminho do Gatilho tem "
              "cinco ranks, e dois deles terminam num duelo ao meio-dia.",
              "Your mana does not leave as bolts or flames: it leaves through the barrel. Revolver, pistol, shotgun, dual "
              "pistols, rifle — every gun is made of mana, and every bullet carries the spell you build. The Trigger Path "
              "has five ranks, and two of them end in a duel at high noon."),
     "outro": ("Doze badaladas. O sino toca, o vento para, e só um revólver sai do coldre a tempo. O seu.",
               "Twelve tolls. The bell rings, the wind stops, and only one revolver clears its holster in time. Yours."),
     "intro": ("Pistoleiro! O Revólver de Mana do kit é só o começo. Acerte, recarregue (F) e suba o Caminho do Gatilho: "
               "cada rank libera uma arma nova. Ao meio-dia, cuidado com o Forasteiro.",
               "Gunslinger! The Mana Revolver in your kit is only the start. Hit, reload (F) and climb the Trigger Path: "
               "each rank unlocks a new gun. At noon, beware the Stranger."),
     "steps": [
         S("hits", _counter("gunslinger.hits", 60), ("Acerte 60 balas", "Land 60 shots"),
           ("O primeiro tiro sai torto. O sexagésimo acerta o olho do goblin a trinta passos.",
            "The first shot goes wide. The sixtieth hits a goblin's eye at thirty paces."),
           ("Atire com o Revólver de Mana (botão de usar) ou com a forma Bala nos seus slots. As balas carregam os efeitos "
            "que você montar no Grimório.",
            "Fire the Mana Revolver (use button) or the Bullet form in your slots. Bullets carry the effects you build in "
            "the Grimoire."),
           ("Revólver ou forma Bala", "Revolver or Bullet form"), icon="isekai_mahou:mana_bullet"),
         S("reload", _counter("gunslinger.reloads", 15), ("Recarregue 15 vezes", "Reload 15 times"),
           ("Seis tiros, um giro do tambor, e de novo. O ritmo é metade da pontaria.",
            "Six shots, a spin of the cylinder, and again. Rhythm is half of aim."),
           ("Aperte F para recarregar o tambor (gasta Foco). O gatilho Ao Recarregar solta magias na recarga. Para o rank "
            "I: nível 8, Círculo 1, 60 acertos e 15 recargas.",
            "Press F to reload the cylinder (spends Focus). The On Reload trigger casts spells as you reload. For rank I: "
            "level 8, Circle 1, 60 hits and 15 reloads."),
           ("Tecla F", "F key"), icon="minecraft:gunpowder"),
         R(1, ("A pistola nasce na sua mão.", "The pistol is born in your hand."),
           ("Nível 8, Círculo 1, 60 acertos e 15 recargas. Conclua na aba Caminho do Gatilho (B). Ganha Rajada, Ao "
            "Recarregar e a Pistola.",
            "Level 8, Circle 1, 60 hits and 15 reloads. Claim it in the Trigger Path tab (B). You learn Burst, On Reload "
            "and get the Pistol."),
           ("Nv 8 · aba do Gatilho", "Lv 8 · Trigger tab")),
         R(2, ("A escopeta abre caminho.", "The shotgun clears the way."),
           ("Nível 20, Círculo 2, 20 tiros na cabeça e 80 abates com bala. Ganha Tiro Carregado, Bala Incendiária e a "
            "Escopeta.",
            "Level 20, Circle 2, 20 headshots and 80 bullet kills. You learn Charged Shot, Incendiary Round and get the "
            "Shotgun."),
           ("Nv 20 · cabeça · abates", "Lv 20 · heads · kills")),
         S("duel", _counter("gunslinger.duel", 1), ("Duelo ao Meio-Dia", "High Noon Duel"),
           ("3... 2... 1... SAQUE! Quem atira antes do sinal perde a honra. Quem atira depois perde a vida.",
            "3... 2... 1... DRAW! Whoever fires before the signal loses their honor. Whoever fires after loses their life."),
           ("Entre 10h e 14h do jogo, a céu aberto no Mundo Normal, peça o duelo na aba do Gatilho: o Forasteiro Espectral "
            "aparece. Espere o SAQUE! — atirar antes é falta. Acertar logo depois = Saque Perfeito.",
            "Between 10:00 and 14:00 in-game, under open sky in the Overworld, request the duel in the Trigger tab: the "
            "Spectral Stranger appears. Wait for DRAW! — firing early is a foul. Hitting right after = Perfect Draw."),
           ("Meio-dia · aba do Gatilho", "Noon · Trigger tab"), xp=250, icon="minecraft:clock"),
         R(3, ("As pistolas duplas e uma lenda no bolso.", "Dual pistols and a legend in your pocket."),
           ("Nível 35, Círculo 4, o Duelo ao Meio-Dia, 60 tiros na cabeça e 10 abates de longe (28+ blocos). Ganha "
            "Ricochete, Bala Explosiva, as Pistolas Duplas e uma Lenda.",
            "Level 35, Circle 4, the High Noon Duel, 60 headshots and 10 long kills (28+ blocks). You learn Ricochet, "
            "Explosive Round, get the Dual Pistols and a Legend."),
           ("Nv 35 · duelo · longe", "Lv 35 · duel · long")),
         S("longshots", _counter("gunslinger.longshots", 40), ("40 abates de longe", "40 long-range kills"),
           ("Um ponto no horizonte, um estalo, e o ponto cai.", "A dot on the horizon, a crack, and the dot falls."),
           ("Abates com bala a 28 blocos ou mais. Para o rank IV também: 500 abates, 200 tiros na cabeça e 2 chefes. O "
            "Fuzil (rank IV) foi feito para isso.",
            "Bullet kills from 28 blocks or more. For rank IV also: 500 kills, 200 headshots and 2 bosses. The Rifle (rank "
            "IV) was made for this."),
           ("Abates a 28+ blocos", "Kills at 28+ blocks"), xp=200, icon="minecraft:spyglass"),
         R(4, ("O fuzil e o Leque do Cão.", "The rifle and Fan the Hammer."),
           ("Nível 50, Círculo 6, 500 abates, 200 tiros na cabeça, 40 de longe e 2 chefes. Ganha Bala Perfurante, Leque do Cão e o "
            "Fuzil.",
            "Level 50, Circle 6, 500 kills, 200 headshots, 40 long kills and 2 bosses. You learn Piercing Round, Fan the Hammer and "
            "get the Rifle."),
           ("Nv 50 · C6 · 2 chefes", "Lv 50 · C6 · 2 bosses")),
         S("duel2", _counter("gunslinger.duel2", 1), ("O Ceifador do Meio-Dia", "The Noon Reaper"),
           ("O segundo duelo não tem forasteiro: tem a Morte, de chapéu, esperando o sino.",
            "The second duel has no stranger: only Death in a hat, waiting for the bell."),
           ("Depois do rank IV, peça o duelo de grau II ao meio-dia: o Ceifador saca mais rápido. Para o rank V: 700 "
            "abates, 100 de longe e 4 chefes.",
            "After rank IV, request the tier II duel at noon: the Reaper draws faster. For rank V: 700 kills, 100 "
            "long kills and 4 bosses."),
           ("Meio-dia · duelo II", "Noon · duel II"), xp=400, icon="minecraft:wither_skeleton_skull"),
         R(5, ("Doze Badaladas.", "Twelve Tolls."),
           ("Nível 70, Círculo 8, o Ceifador vencido, 700 abates, 100 de longe e 4 chefes. Ganha a forma G Doze Badaladas e a "
            "segunda Lenda. " + LESSON_NOTE_PT,
            "Level 70, Circle 8, the Reaper defeated, 700 kills, 100 long kills and 4 bosses. You learn the G form Twelve Tolls "
            "and the second Legend. " + LESSON_NOTE_EN),
           ("Nv 70 · C8 · Ceifador", "Lv 70 · C8 · Reaper")),
     ]},

    # -------------------------------------------------------------------------------------------- RANGER
    {"id": "ranger", "color": "#6BCB5A", "rank": rank_flag("ranger.rank.%d", "ritual_rank.ranger"),
     "requires": ["isekai_mahou:ranger_bow"],
     "icons": ["isekai_mahou:ranger_bow", "minecraft:arrow", "minecraft:bone"],
     "rank_icon": "isekai_mahou:ranger_bow", "rank_title": ("Trilha: rank %s", "Trail: rank %s"),
     "title": ("A Trilha", "The Trail"),
     "lore": ("O Ranger nunca caça sozinho: a águia marca o alvo, o lobo derruba, o corcel leva para longe. E cada flecha é "
              "montada como uma magia — explosiva, teleguiada, de corrente, perfurante. A Trilha do Ranger vai do Batedor "
              "ao Senhor das Feras, e os companheiros crescem junto.",
              "A Ranger never hunts alone: the eagle marks the target, the wolf brings it down, the steed carries you "
              "away. And every arrow is built like a spell — explosive, seeking, chaining, piercing. The Ranger's Trail "
              "goes from Scout to Lord of Beasts, and the companions grow with you."),
     "outro": ("Mil penas cobrem o céu. Lá embaixo, a matilha uiva o seu nome.",
               "A thousand feathers cover the sky. Below, the pack howls your name."),
     "intro": ("Ranger! O arco do kit, flechas montadas no Grimório e companheiros que evoluem. Chame a matilha, marque as "
               "presas e siga a Trilha na aba do Ranger.",
               "Ranger! The bow from your kit, arrows built in the Grimoire and companions that evolve. Call the pack, mark "
               "your prey and follow the Trail in the Ranger tab."),
     "steps": [
         S("pack", _counter("ranger.companion_kill", 10), ("A matilha caça junto", "Hunt with the pack"),
           ("Um assobio e o mato se mexe: o lobo chegou antes de você terminar de mirar.",
            "A whistle and the brush stirs: the wolf arrives before you finish aiming."),
           ("Use o Chamado da Matilha (glifo do Ranger nos slots) para chamar o Lobo e a Águia. Os abates deles contam. Para "
            "o rank I: nível 8, 25 abates com flechas e 10 dos companheiros.",
            "Use Pack Call (Ranger glyph in your slots) to summon the Wolf and the Eagle. Their kills count. For rank I: "
            "level 8, 25 arrow kills and 10 companion kills."),
           ("Chamado da Matilha", "Pack Call"), icon="minecraft:bone"),
         S("arrows", _counter("ranger.arrow_kill", 25), ("25 abates com flechas", "25 arrow kills"),
           ("A Flecha Explosiva do kit é só uma ideia. Monte a sua.", "The Explosive Arrow from your kit is just one idea. Build your own."),
           ("Derrote inimigos com o Arco do Ranger ou com as formas de flecha. No Grimório, junte a Flecha com efeitos "
            "(explosão, corrente, marca...) — tudo combina com os seus elementos.",
            "Defeat enemies with the Ranger's Bow or the arrow forms. In the Grimoire, combine the Arrow with effects "
            "(explosion, chain, mark...) — it all combines with your elements."),
           ("Arco do Ranger", "Ranger's Bow"), icon="minecraft:arrow"),
         R(1, ("Batedor: a trilha começa.", "Scout: the trail begins."),
           ("Nível 8, Círculo 1, 25 abates com flechas e 10 dos companheiros. Conclua na aba Trilha do Ranger (B). Ganha "
            "Flecha-Rede e Teleguiada.",
            "Level 8, Circle 1, 25 arrow kills and 10 companion kills. Claim it in the Ranger's Trail tab (B). You learn "
            "Net Arrow and Seeking."),
           ("Nv 8 · aba da Trilha", "Lv 8 · Trail tab")),
         S("mark", _counter("ranger.mark_kill", 20), ("20 presas marcadas", "20 marked prey"),
           ("A Marca do Caçador brilha sobre o alvo. A matilha inteira sabe quem morre primeiro.",
            "The Hunter's Mark glows over the target. The whole pack knows who dies first."),
           ("Marque alvos com a Marca do Caçador (a Águia também marca) e derrote-os. Para o rank II: nível 20, 25 abates "
            "de longe (20+ blocos), 20 marcados e 40 dos companheiros.",
            "Mark targets with the Hunter's Mark (the Eagle marks too) and defeat them. For rank II: level 20, 25 long "
            "kills (20+ blocks), 20 marked and 40 companion kills."),
           ("Marca do Caçador", "Hunter's Mark"), xp=80, icon="minecraft:target"),
         R(2, ("Rastreador: o Corcel atende ao chamado.", "Tracker: the Steed answers the call."),
           ("Nível 20, Círculo 2, 25 abates de longe, 20 marcados e 40 dos companheiros. Ganha Flecha em Corrente, o "
            "Chamado do Corcel e o Arco II.",
            "Level 20, Circle 2, 25 long kills, 20 marked and 40 companion kills. You learn Chain Arrow, Steed Call and "
            "get the Bow II."),
           ("Nv 20 · C2 · aba da Trilha", "Lv 20 · C2 · Trail tab")),
         S("ride", _counter("ranger.ride_dist", 1500), ("Cavalgue 1500 blocos", "Ride 1500 blocks"),
           ("Vento na cara, arco na mão, e o horizonte ficando para trás.",
            "Wind in your face, bow in hand, and the horizon falling behind."),
           ("Chame o Corcel e viaje montado nele (conta a distância percorrida). Montado, você atira e conjura normalmente.",
            "Summon the Steed and travel on it (the distance ridden counts). Mounted, you shoot and cast normally."),
           ("Montado no Corcel", "Riding the Steed"), xp=100, icon="minecraft:saddle"),
         S("prey", _counter("ranger.great_prey", 3), ("3 grandes presas", "3 great prey"),
           ("Chefes e feras de 150+ de vida. As histórias de caçada começam aqui.",
            "Bosses and beasts with 150+ health. Hunting stories start here."),
           ("Derrote chefes ou criaturas enormes (150+ de vida) com a matilha. Para o rank III: nível 35, Círculo 4, 1500 "
            "blocos montado, 250 abates com flechas e 3 grandes presas.",
            "Defeat bosses or huge creatures (150+ health) with the pack. For rank III: level 35, Circle 4, 1500 blocks "
            "ridden, 250 arrow kills and 3 great prey."),
           ("Chefes e feras grandes", "Bosses and big beasts"), xp=150, icon="minecraft:dragon_head"),
         R(3, ("Guardião da Mata: chuva de flechas.", "Forest Warden: a rain of arrows."),
           ("Nível 35, Círculo 4, 1500 blocos montado, 250 abates com flechas e 3 grandes presas. Ganha Flecha Perfurante, "
            "Chuva de Flechas e o Arco III.",
            "Level 35, Circle 4, 1500 blocks ridden, 250 arrow kills and 3 great prey. You learn Piercing Arrow, Arrow "
            "Rain and get the Bow III."),
           ("Nv 35 · C4 · aba da Trilha", "Lv 35 · C4 · Trail tab")),
         R(4, ("Mestre das Feras: a Tempestade das Mil Penas.", "Beastmaster: the Storm of a Thousand Feathers."),
           ("Nível 55, Círculo 6, 200 abates dos companheiros, 150 marcados e 6 grandes presas. Ganha Saque Rápido, a "
            "forma G Tempestade das Mil Penas e o Arco IV.",
            "Level 55, Circle 6, 200 companion kills, 150 marked and 6 great prey. You learn Quickdraw, the G form Storm "
            "of a Thousand Feathers and get the Bow IV."),
           ("Nv 55 · C6 · 6 presas", "Lv 55 · C6 · 6 prey")),
         R(5, ("Senhor das Feras: as duas Lendas do Ranger.", "Lord of Beasts: both Ranger Legends."),
           ("Nível 75, Círculo 8, 250 abates de longe, 12 grandes presas e 400 dos companheiros. Ganha as 2 Lendas do "
            "Ranger. " + LESSON_NOTE_PT,
            "Level 75, Circle 8, 250 long kills, 12 great prey and 400 companion kills. You get both Ranger Legends. "
            + LESSON_NOTE_EN),
           ("Nv 75 · C8 · 12 presas", "Lv 75 · C8 · 12 prey")),
     ]},

    # -------------------------------------------------------------------------------------------- APOSTADOR
    {"id": "gambler", "color": "#E04A8C", "rank": rank_counter("gambler.rank"),
     "requires": ["isekai_mahou:gambler_deck"],
     "icons": ["isekai_mahou:gambler_deck", "isekai_mahou:casino_chip", "isekai_mahou:gambler_card"],
     "rank_icon": "isekai_mahou:casino_chip", "rank_title": ("Ficha %s", "Chip %s"),
     "title": ("Fichas da Casa", "House Chips"),
     "lore": ("Toda magia do Apostador rola a sorte: de −99% a +100% de poder, com tudo no meio. Azar existe — mas a "
              "Tensão sobe a cada rolagem ruim, e quando o JACKPOT vem, a sorte fica infinita por alguns segundos. "
              "Cartas, dados, roleta, caça-níquel e, no fim, um Cassino inteiro como domínio. A Casa sempre ganha... a "
              "menos que você seja a Casa.",
              "Every Gambler spell rolls the dice: from −99% to +100% power, and everything in between. Bad luck exists — "
              "but Tension rises with every poor roll, and when the JACKPOT comes, luck turns infinite for a few seconds. "
              "Cards, dice, roulette, slot machine and, at the end, a whole Casino as a domain. The House always wins... "
              "unless you are the House."),
     "outro": ("A ficha de diamante gira no seu dedo. Nas mesas de todos os reinos, ninguém aceita apostar contra você.",
               "The diamond chip spins on your finger. At tables across every kingdom, nobody will bet against you."),
     "intro": ("Apostador! Cada magia sua rola a sorte. Jogue cartas, role dados, junte Fichas de Cassino e vença a "
               "Rodada da Casa na aba Mesa da Casa para subir de ficha.",
               "Gambler! Every spell of yours rolls the dice. Throw cards, roll dice, gather Casino Chips and win the "
               "House Round in the House Table tab to climb the chips."),
     "steps": [
         S("rolls", _counter("gambler.rolls", 40), ("Role a sorte 40 vezes", "Roll the dice 40 times"),
           ("−99%? +100%? O coração acelera a cada conjuração. É disso que você gosta.",
            "−99%? +100%? Your heart races with every cast. That's what you love."),
           ("Toda magia ou técnica sua rola a sorte (o número aparece na tela). Rolagens ruins enchem a Tensão, que "
            "melhora as próximas. Use qualquer magia — as cartas do kit também contam.",
            "Every spell or technique you use rolls luck (the number shows on screen). Bad rolls fill Tension, which "
            "improves the next ones. Use any spell — the cards in your kit count too."),
           ("Conjure qualquer magia", "Cast any spell"), icon="isekai_mahou:gambler_deck"),
         S("cards", _counter("gambler.card_hit", 25), ("Acerte 25 cartas", "Land 25 cards"),
           ("Uma carta carregada de energia corta o ar e explode no alvo. Às de espadas, de preferência.",
            "An energy-charged card cuts the air and bursts on the target. Ace of spades, preferably."),
           ("Arremesse cartas carregadas (Carta de Baralho e a forma Carta) nos inimigos. Para a Ficha de Bronze: nível 5, "
            "40 rolagens, 25 cartas e a Rodada da Casa (6 abates em 60 s, aposta de 1 ficha).",
            "Throw charged cards (Playing Card and the Card form) at enemies. For the Bronze Chip: level 5, 40 rolls, 25 "
            "cards and the House Round (6 kills in 60 s, 1-chip stake)."),
           ("Arremesse cartas", "Throw cards"), icon="isekai_mahou:gambler_card"),
         R(1, ("Ficha de Bronze: a Casa nota a sua presença.", "Bronze Chip: the House notices you."),
           ("Nível 5, Círculo 1, 40 rolagens e 25 cartas. Na aba Mesa da Casa (B), aposte 1 Ficha de Cassino e vença a "
            "Rodada: 6 abates em 60 s (vencer devolve a aposta em dobro). Ganha Dados e Dobrar a Aposta.",
            "Level 5, Circle 1, 40 rolls and 25 cards. In the House Table tab (B), bet 1 Casino Chip and win the Round: 6 "
            "kills in 60 s (winning pays back double). You learn Dice and Double Down."),
           ("Nv 5 · Rodada da Casa", "Lv 5 · House Round"), title=("Ficha de Bronze", "Bronze Chip")),
         S("jackpot", _counter("gambler.jackpot", 2), ("Tire 2 JACKPOTS", "Hit 2 JACKPOTS"),
           ("Três sete. Sinos, luzes, chuva de moedas — e por alguns segundos você simplesmente não erra.",
            "Three sevens. Bells, lights, a rain of coins — and for a few seconds you simply cannot miss."),
           ("O JACKPOT vem de rolagens altíssimas (a Tensão ajuda): sorte infinita por alguns segundos, com poder e regen "
            "no máximo. Para a Ficha de Prata: 200 rolagens, 2 jackpots e 20 grandes vitórias.",
            "The JACKPOT comes from very high rolls (Tension helps): infinite luck for a few seconds, with power and regen "
            "maxed. For the Silver Chip: 200 rolls, 2 jackpots and 20 big wins."),
           ("Role alto · Tensão ajuda", "Roll high · Tension helps"), xp=150, icon="minecraft:gold_block"),
         R(2, ("Ficha de Prata: a roleta gira para você.", "Silver Chip: the roulette spins for you."),
           ("Nível 15, Círculo 2, 200 rolagens, 2 jackpots e 20 grandes vitórias; Rodada: 10 abates em 75 s, 2 fichas. "
            "Ganha Roleta, Dados Viciados e o Baralho II.",
            "Level 15, Circle 2, 200 rolls, 2 jackpots and 20 big wins; Round: 10 kills in 75 s, 2 chips. You learn "
            "Roulette, Loaded Dice and get the Deck II."),
           ("Nv 15 · Rodada · 2 fichas", "Lv 15 · Round · 2 chips"), title=("Ficha de Prata", "Silver Chip")),
         S("lucky", _counter("gambler.lucky_kill", 40), ("40 abates de sorte", "40 lucky kills"),
           ("Às vezes a sorte não ajuda: ela mata.", "Sometimes luck doesn't help: it kills."),
           ("Abates com magias que rolaram sorte boa. Para a Ficha de Ouro: nível 35, Círculo 4, 500 rolagens, 6 jackpots, "
            "40 abates de sorte, 1 chefe e a Rodada (15 abates em 90 s, 4 fichas).",
            "Kills with spells that rolled good luck. For the Gold Chip: level 35, Circle 4, 500 rolls, 6 jackpots, 40 "
            "lucky kills, 1 boss and the Round (15 kills in 90 s, 4 chips)."),
           ("Abata com sorte alta", "Kill with high luck"), xp=150, icon="minecraft:rabbit_foot"),
         R(3, ("Ficha de Ouro: o Cassino abre as portas.", "Gold Chip: the Casino opens its doors."),
           ("Nível 35, Círculo 4, as tarefas da ficha e 1 chefe. Ganha Caça-Níquel, Mão Real e o Domínio: Cassino da Fortuna — "
            "monte-o no slot G.",
            "Level 35, Circle 4, the chip tasks and 1 boss. You learn Slot Machine, Royal Flush and the Domain: Casino of Fortune "
            "— build it in the G slot."),
           ("Nv 35 · C4 · Rodada", "Lv 35 · C4 · Round"), title=("Ficha de Ouro", "Gold Chip")),
         S("casino", _counter("gambler.casino", 3), ("Abra o Cassino 3 vezes", "Open the Casino 3 times"),
           ("Feltro verde, luzes douradas, e todos os inimigos dentro jogam com as SUAS regras.",
            "Green felt, golden lights, and every enemy inside plays by YOUR rules."),
           ("Use o Domínio Cassino da Fortuna em lutas grandes. Para a Ficha de Platina: nível 55, Círculo 6, 1000 "
            "rolagens, 14 jackpots, 3 Cassinos, 2 chefes e a Rodada (20 abates em 90 s, 7 fichas).",
            "Use the Domain Casino of Fortune in big fights. For the Platinum Chip: level 55, Circle 6, 1000 rolls, 14 "
            "jackpots, 3 Casinos, 2 bosses and the Round (20 kills in 90 s, 7 chips)."),
           ("Domínio no slot G", "Domain in the G slot"), xp=250, icon="minecraft:emerald_block"),
         R(4, ("Ficha de Platina: Ao Jackpot.", "Platinum Chip: On Jackpot."),
           ("Nível 55, Círculo 6, as tarefas da ficha e 2 chefes. Ganha o gatilho Ao Jackpot (magias que disparam quando a sorte "
            "explode) e o Baralho III.",
            "Level 55, Circle 6, the chip tasks and 2 bosses. You learn the On Jackpot trigger (spells that fire when luck explodes) "
            "and get the Deck III."),
           ("Nv 55 · C6 · Rodada", "Lv 55 · C6 · Round"), title=("Ficha de Platina", "Platinum Chip")),
         R(5, ("Ficha de Diamante: você é a Casa.", "Diamond Chip: you are the House."),
           ("Nível 75, Círculo 8, 2000 rolagens, 30 jackpots e 4 chefes; Rodada: 30 abates em 120 s, 12 fichas. Ganha o "
            "Baralho IV e as 2 Lendas do Apostador. " + LESSON_NOTE_PT,
            "Level 75, Circle 8, 2000 rolls, 30 jackpots and 4 bosses; Round: 30 kills in 120 s, 12 chips. You get the "
            "Deck IV and both Gambler Legends. " + LESSON_NOTE_EN),
           ("Nv 75 · C8 · 12 fichas", "Lv 75 · C8 · 12 chips"), title=("Ficha de Diamante", "Diamond Chip")),
     ]},

    # -------------------------------------------------------------------------------------------- STEEL BALL
    {"id": "steelball", "color": "#B4C0CC", "rank": rank_counter("ritual_rank.steelball"),
     "requires": ["isekai_mahou:steel_ball"],
     "icons": ["isekai_mahou:steel_ball", "isekai_mahou:steelball_saint_compass", "isekai_mahou:steelball_mentor_letter"],
     "rank_icon": "isekai_mahou:steel_ball", "rank_title": ("Lição do Mestre %s", "Master's Lesson %s"),
     "title": ("A Rotação", "The Spin"),
     "lore": ("Uma bola de aço que gira não é uma pedra arremessada: é uma lição de geometria. O Mestre Rotari ensina a "
              "Rotação em cinco lições, e nove Partes do Cadáver Santo, escondidas em sepulturas pelo mundo, despertam a "
              "unha giratória — Ato 1, 2, 3... até a Rotação Infinita, que nada consegue parar.",
              "A spinning steel ball is not a thrown stone: it is a lesson in geometry. Master Rotari teaches the Spin in "
              "five lessons, and nine Parts of the Holy Corpse, hidden in tombs across the world, awaken the spinning "
              "nail — Act 1, 2, 3... up to the Infinite Rotation, which nothing can stop."),
     "outro": ("A proporção áurea gira no seu dedo. O Mestre tira o chapéu: \"Não há mais nada que eu possa ensinar.\"",
               "The golden ratio spins on your finger. The Master tips his hat: \"There's nothing more I can teach you.\""),
     "intro": ("Steel Ball! A bola do kit gira e volta para a mão. O Mestre Rotari aparece de dia quando uma lição está "
               "pronta (ou use a Carta do Mestre), e a Bússola do Santo aponta as Sepulturas.",
               "Steel Ball! The ball in your kit spins and comes back to your hand. Master Rotari appears by day when a "
               "lesson is ready (or use the Master's Letter), and the Saint's Compass points to the Tombs."),
     "steps": [
         S("mentor", {"type": "flag", "flag": "steelball.mentor_met"}, ("Conheça o Mestre Rotari", "Meet Master Rotari"),
           ("Chapéu de abas largas, sorriso torto e duas bolas de aço girando sozinhas na mão.",
            "A wide-brimmed hat, a crooked smile and two steel balls spinning by themselves in his hand."),
           ("Use a Carta do Mestre do kit (ou espere: ele aparece de dia no Mundo Normal na primeira vez). Converse com ele "
            "— é ele quem ensina as cinco lições.",
            "Use the Master's Letter from your kit (or wait: he appears by day in the Overworld the first time). Talk to "
            "him — he is the one who teaches the five lessons."),
           ("Carta do Mestre (kit)", "Master's Letter (kit)"), xp=30, icon="isekai_mahou:steelball_mentor_letter"),
         R(1, ("A Bola que Gira.", "The Ball that Spins."),
           ("Círculo 1 e 30 acertos com a bola de aço (arremesse com o botão de usar: ela volta). Fale com o Mestre. Ganha "
            "Unha Giratória e o Giro que Cura — o Ato 1.",
            "Circle 1 and 30 hits with the steel ball (throw it with the use button: it comes back). Talk to the Master. "
            "You learn Spinning Nail and Healing Spin — Act 1."),
           ("C1 · 30 acertos · Mestre", "C1 · 30 hits · Master"), title=("A Bola que Gira", "The Ball that Spins")),
         S("parts2", _counter("steelball.parts", 2), ("2 Partes do Cadáver", "2 Corpse Parts"),
           ("Uma sepultura antiga, um baú selado e algo que pulsa lá dentro como um coração.",
            "An ancient tomb, a sealed chest and something inside that pulses like a heart."),
           ("A seta aponta a Sepultura do Santo mais perto (a Bússola do Santo também). Abra o baú e absorva a Parte: cada "
            "uma dá um poder passivo. Raramente aparecem em outros baús.",
            "The arrow points to the nearest Saint's Tomb (the Saint's Compass too). Open the chest and absorb the Part: "
            "each one grants a passive power. Rarely, they show up in other chests."),
           ("Siga a seta: sepultura", "Follow the arrow: tomb"), xp=150, icon="isekai_mahou:steelball_saint_compass",
           locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:steelball_saint_tomb",
                                                    "unless": _counter("steelball.parts", 9)}]}),
         R(2, ("O Cavalo e o Giro.", "The Horse and the Spin."),
           ("Círculo 2, 2 Partes, 25 abates montado e 120 acertos com a bola. Ganha Galope Giratório e Trava Muscular — "
            "o Ato 2. Montado, a bola bate 30% mais forte.",
            "Circle 2, 2 Parts, 25 mounted kills and 120 ball hits. You learn Galloping Spin and Muscle Lock — Act 2. "
            "Mounted, the ball hits 30% harder."),
           ("C2 · 2 Partes · montado", "C2 · 2 Parts · mounted"), title=("O Cavalo e o Giro", "The Horse and the Spin")),
         S("parts4", _counter("steelball.parts", 3), ("3 Partes do Cadáver", "3 Corpse Parts"),
           ("Olhos, coração, braços, coluna... o Santo está se recompondo dentro de você.",
            "Eyes, heart, arms, spine... the Saint is putting itself back together inside you."),
           ("Mais Sepulturas (siga a seta). Para a lição III: Círculo 4, 3 Partes, 150 acertos de unha, 15 curas com o "
            "giro e 1 chefe.",
            "More Tombs (follow the arrow). For lesson III: Circle 4, 3 Parts, 150 nail hits, 15 spin heals and 1 boss."),
           ("Siga a seta: sepulturas", "Follow the arrow: tombs"), xp=200, icon="isekai_mahou:steelball_saint_compass",
           locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:steelball_saint_tomb",
                                                    "unless": _counter("steelball.parts", 9)}]}),
         R(3, ("O Giro que Cura e Perfura.", "The Spin that Heals and Pierces."),
           ("Círculo 4, 3 Partes, 150 acertos de unha, 15 curas com o giro e 1 chefe. Ganha Vórtice e Broca Giratória — o "
            "Ato 3.",
            "Circle 4, 3 Parts, 150 nail hits, 15 spin heals and 1 boss. You learn Vortex and Spin Drill — Act 3."),
           ("C4 · 3 Partes · 1 chefe", "C4 · 3 Parts · 1 boss"), title=("O Giro que Perfura", "The Spin that Pierces")),
         S("duel", _counter("steelball.mentor_duel", 1), ("Vença o Mestre", "Defeat the Master"),
           ("\"Mostre o que aprendeu.\" Duas bolas giram ao mesmo tempo, e só uma volta para a mão.",
            "\"Show me what you've learned.\" Two balls spin at once, and only one returns to its hand."),
           ("Com Círculo 6 e 5 Partes, o Mestre propõe o desafio da lição IV. Vença-o no duelo (ele cede quando quase cai). "
            "A lição IV também pede 2 chefes.",
            "With Circle 6 and 5 Parts, the Master offers the lesson IV challenge. Beat him in the duel (he yields when "
            "nearly down). Lesson IV also asks for 2 bosses."),
           ("C6 · 5 Partes · Mestre", "C6 · 5 Parts · Master"), xp=400, icon="isekai_mahou:steel_ball"),
         R(4, ("O Desafio do Mestre: o Retângulo Dourado.", "The Master's Challenge: the Golden Rectangle."),
           ("Círculo 6, 5 Partes, o Mestre vencido e 2 chefes. Ganha o Retângulo Dourado.",
            "Circle 6, 5 Parts, the Master defeated and 2 bosses. You learn the Golden Rectangle."),
           ("C6 · Mestre vencido", "C6 · Master defeated"), title=("O Desafio do Mestre", "The Master's Challenge")),
         S("parts8", _counter("steelball.parts", 6), ("6 Partes do Cadáver", "6 Corpse Parts"),
           ("Quase inteiro. A última parte sussurra de algum lugar distante.",
            "Almost whole. The last part whispers from somewhere far away."),
           ("Para a lição V: Círculo 8, 6 Partes, 60 acertos dourados (Retângulo Dourado), 150 abates montado e 4 chefes. "
            "As outras Partes são bônus — e valem a pena.",
            "For lesson V: Circle 8, 6 Parts, 60 golden hits (Golden Rectangle), 150 mounted kills and 4 bosses. The other "
            "Parts are a bonus — and worth it."),
           ("Siga a seta: sepulturas", "Follow the arrow: tombs"), xp=400, icon="isekai_mahou:steelball_saint_compass",
           locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:steelball_saint_tomb",
                                                    "unless": _counter("steelball.parts", 9)}]}),
         R(5, ("A Proporção Áurea: Rotação Infinita.", "The Golden Ratio: Infinite Rotation."),
           ("Círculo 8, 6 Partes, 60 acertos dourados, 150 abates montado e 4 chefes. Ganha a forma G Rotação Infinita — "
            "o Ato 4, que nada consegue parar. " + LESSON_NOTE_PT,
            "Circle 8, 6 Parts, 60 golden hits, 150 mounted kills and 4 bosses. You learn the G form Infinite Rotation — "
            "Act 4, which nothing can stop. " + LESSON_NOTE_EN),
           ("C8 · 6 Partes · 4 chefes", "C8 · 6 Parts · 4 bosses"), title=("A Proporção Áurea", "The Golden Ratio")),
     ]},
]


# -------------------------------------------------------------------------------------------- AS 5 MÁGICAS
def magic_class(cid, color, block, icons, title, rank_title, lore, outro, intro, place, rank_hints, extra):
    """Classe mágica (ritual da v0.8, só o ritual_rank geral): bloco do kit, 5 ranks e passos de sabor."""
    steps = [S("kit", _any({"type": "placed", "block": block}, _counter("ritual_rank", 1)),
               place[0], place[1], place[2], place[3], xp=30, icon="isekai_mahou:" + (icons[0].split(":")[1]))]
    for n in range(1, 6):
        steps.append(R(n, rank_hints[n - 1][0], rank_hints[n - 1][1], rank_hints[n - 1][2]))
        for e in extra:
            if e[0] == n:
                steps.append(e[1])
    return {"id": cid, "color": color, "rank": rank_generic, "requires": ["isekai_mahou:" + block],
            "icons": icons, "rank_icon": icons[0], "rank_title": rank_title, "title": title, "lore": lore,
            "outro": outro, "intro": intro, "steps": steps}


def _mrank(n, lore, where_pt, where_en, gift_pt, gift_en, tip):
    return (lore,
            (("Rank %s do ritual: %s %s" % (ROMAN[n], where_pt, gift_pt)).strip(),
             ("Ritual rank %s: %s %s" % (ROMAN[n], where_en, gift_en)).strip()), tip)


CLASSES += [
    magic_class(
        "mage", "#7C86FF", "research_table", ["isekai_mahou:research_table", "isekai_mahou:mage_staff_t1", "isekai_mahou:arcane_lens"],
        ("As Teses", "The Theses"), ("Tese: rank %s", "Thesis: rank %s"),
        ("O Mago não reza nem pactua: estuda. Na Mesa de Pesquisa, cada tese é um problema de mana — circuitos, runas, "
         "notas — e cada tese defendida vira um glifo novo e um Círculo mais largo de compreensão.",
         "A Mage neither prays nor bargains: they study. At the Research Table, every thesis is a mana problem — "
         "circuits, runes, notes — and every thesis defended becomes a new glyph and a wider Circle of understanding."),
        ("A banca se levanta e aplaude. Nenhum mago vivo tem tantas teses defendidas quanto você.",
         "The panel rises and applauds. No living mage has defended as many theses as you."),
        ("Mago! A Mesa de Pesquisa do kit é o seu laboratório: escolha uma tese, cumpra a tarefa e defenda-a. Cada "
         "rank traz glifos novos.",
         "Mage! The Research Table in your kit is your laboratory: pick a thesis, complete its task and defend it. "
         "Each rank brings new glyphs."),
        (("Monte a Mesa de Pesquisa", "Set up the Research Desk"),
         ("Pergaminhos, lentes e uma pena que escreve sozinha quando a mana se agita.",
          "Scrolls, lenses and a quill that writes by itself when mana stirs."),
         ("Coloque a Mesa de Pesquisa do seu kit. Clique nela para ver as teses disponíveis, o que cada uma pede e o que "
          "ela desperta.",
          "Place the Research Table from your kit. Click it to see the available theses, what each one asks for and "
          "what it awakens."),
         ("Mesa de Pesquisa do kit", "Research Table (kit)")),
        [_mrank(1, ("Primeira tese defendida.", "First thesis defended."),
                "na Mesa de Pesquisa, escolha uma tese e cumpra a tarefa pedida (circuito de runas, notas, materiais).",
                "at the Research Table, pick a thesis and complete the requested task (rune circuit, notes, materials).",
                "Defender a tese grava o rank e ensina glifos.", "Defending the thesis records the rank and teaches glyphs.",
                ("Mesa → 1ª tese", "Table → 1st thesis")),
         _mrank(2, ("A academia começa a citar o seu nome.", "The academy starts quoting your name."),
                "a Mesa mostra as próximas teses; algumas pedem Círculo, essências ou notas de pesquisa.",
                "the Table shows the next theses; some ask for a Circle, essences or research notes.",
                "Mais teses, mais glifos.", "More theses, more glyphs.", ("Mesa → próxima tese", "Table → next thesis")),
         _mrank(3, ("Professor do Arcano.", "Professor of the Arcane."),
                "teses mais difíceis pedem circuitos maiores e elementos despertados.",
                "harder theses ask for bigger circuits and awakened elements.",
                "Ganhe glifos de nível alto.", "Earn high-tier glyphs.", ("Mesa → tese III", "Table → thesis III")),
         _mrank(4, ("Arquimago em formação.", "Archmage in the making."),
                "as teses avançadas cruzam elementos e pedem materiais raros dos chefes.",
                "advanced theses cross elements and ask for rare boss materials.",
                "", "", ("Mesa → tese IV", "Table → thesis IV")),
         _mrank(5, ("A tese final.", "The final thesis."),
                "a última tese reúne tudo o que você aprendeu.", "the last thesis brings together everything you learned.",
                LESSON_NOTE_PT, LESSON_NOTE_EN, ("Mesa → tese final", "Table → final thesis"))],
        [(2, S("glyphs", {"type": "glyphs", "min": 25}, ("Conheça 25 glifos", "Know 25 glyphs"),
               ("Um mago se mede pelo tamanho do vocabulário.", "A mage is measured by the size of their vocabulary."),
               ("Pergaminhos de Glifo (cada um gasta 1 essência do elemento do glifo), teses e recompensas do Diário. "
                "Glifos de outras classes só por Lição.",
                "Glyph Scrolls (each costs 1 essence of the glyph's element), theses and Journal rewards. Other classes' "
                "glyphs only via Lesson."),
               ("Pergaminhos + essência", "Scrolls + essence"), xp=100, icon="isekai_mahou:glyph_scroll")),
         (3, S("master", {"type": "node", "node": "master", "min": 1}, ("Mestre de um elemento", "Master an element"),
               ("Dominar um elemento é ouvir a voz dele antes de chamá-lo.",
                "Mastering an element means hearing its voice before you call it."),
               ("Suba um elemento no caminho dele (aba Caminhos) até o nó Mestre. Magias e abates do elemento dão XP "
                "dele; as Provações liberam os nós mais altos.",
                "Raise an element along its path (Paths tab) to the Master node. That element's spells and kills give it "
                "XP; Trials unlock the higher nodes."),
               ("Aba Caminhos → Mestre", "Paths tab → Master"), xp=200, icon="minecraft:enchanted_book"))]),

    magic_class(
        "witch", "#F070F0", "witch_cauldron", ["isekai_mahou:witch_cauldron", "isekai_mahou:witch_wand_t1", "isekai_mahou:witch_recipe_page"],
        ("O Caldeirão", "The Cauldron"), ("Revelação %s", "Revelation %s"),
        ("A Bruxa não pede poder: cozinha. Ervas, essências, olhos de coisas que você não quer saber — no caldeirão, "
         "tudo vira receita, e cada receita revelada abre uma porta da sua magia.",
         "A Witch does not ask for power: she brews it. Herbs, essences, eyes of things you'd rather not know — in the "
         "cauldron, everything becomes a recipe, and every recipe revealed opens a door of her magic."),
        ("O vapor do caldeirão desenha o seu rosto no ar. Nos três reinos, as mães mandam os filhos dormirem cedo.",
         "The cauldron's steam draws your face in the air. In the three kingdoms, mothers send their children to bed early."),
        ("Bruxa! O Caldeirão do kit sobre o fogo é o seu altar. Siga as receitas do livro, cozinhe as poções do ritual e "
         "colecione as cinco Revelações.",
         "Witch! The Cauldron from your kit over a fire is your altar. Follow the recipes in the book, brew the ritual "
         "potions and collect the five Revelations."),
        (("Acenda o Caldeirão", "Light the Cauldron"),
         ("Ferro preto, borbulhas verdes e um cheiro que espanta até zumbi.",
          "Black iron, green bubbles and a smell that scares off even zombies."),
         ("Coloque o Caldeirão do seu kit sobre fogo (fogueira ou lava). As receitas do ritual ficam nas Páginas de "
          "Receita; a Colher Ritual mexe a poção.",
          "Place the Cauldron from your kit over fire (campfire or lava). Ritual recipes are on the Recipe Pages; the "
          "Ritual Spoon stirs the potion."),
         ("Caldeirão sobre fogo", "Cauldron over fire")),
        [_mrank(1, ("Primeira Revelação.", "First Revelation."),
                "cozinhe a poção do ritual seguindo uma receita do livro.", "brew the ritual potion following a recipe from the book.",
                "", "", ("Caldeirão → 1ª receita", "Cauldron → 1st recipe")),
         _mrank(2, ("A segunda página se abre.", "The second page opens."),
                "receitas novas pedem ingredientes de criaturas e essências.", "new recipes ask for creature ingredients and essences.",
                "", "", ("Caldeirão → receita II", "Cauldron → recipe II")),
         _mrank(3, ("Bruxa de verdade.", "A true Witch."),
                "as receitas do meio pedem Círculo e ingredientes raros (pântanos e covis).",
                "mid recipes ask for a Circle and rare ingredients (swamps and lairs).",
                "", "", ("Caldeirão → receita III", "Cauldron → recipe III")),
         _mrank(4, ("A Revelação proibida.", "The forbidden Revelation."),
                "as receitas avançadas usam partes de chefes.", "advanced recipes use boss parts.",
                "", "", ("Caldeirão → receita IV", "Cauldron → recipe IV")),
         _mrank(5, ("A última Revelação.", "The last Revelation."),
                "a receita final é a mais difícil do livro.", "the final recipe is the hardest in the book.",
                LESSON_NOTE_PT, LESSON_NOTE_EN, ("Caldeirão → receita final", "Cauldron → final recipe"))],
        [(2, S("water_shadow", _all({"type": "element", "element": "water"}, {"type": "element", "element": "shadow"}),
               ("Água e Sombra", "Water and Shadow"),
               ("As duas afinidades da Bruxa: a que cura e a que amaldiçoa.", "The Witch's two affinities: one heals, one curses."),
               ("Desperte a Água e a Sombra (santuários elementais ou rituais). Juntas, elas fazem as melhores maldições — "
                "e a fusão das duas vale a pena.",
                "Awaken Water and Shadow (elemental shrines or rituals). Together they make the best curses — and their "
                "fusion is worth it."),
               ("Santuários: Água e Sombra", "Shrines: Water, Shadow"), xp=120, icon="isekai_mahou:essence_shadow"))]),

    magic_class(
        "warlock", "#D04545", "offering_altar", ["isekai_mahou:offering_altar", "isekai_mahou:pact_blade_t1", "isekai_mahou:ritual_chalk_item"],
        ("Os Pactos", "The Pacts"), ("Pacto: rank %s", "Pact: rank %s"),
        ("O Bruxo desenha o círculo com giz, acende as velas e chama alguém do outro lado. Patronos cobram caro — almas, "
         "tarefas, juramentos — mas pagam melhor. Cada pacto firmado é um rank; cada pacto quebrado, uma briga.",
         "The Warlock draws the circle in chalk, lights the candles and calls someone from the other side. Patrons charge "
         "dearly — souls, tasks, oaths — but they pay better. Every pact sealed is a rank; every broken pact, a fight."),
        ("Os patronos agora pedem audiência a VOCÊ. O círculo de giz brilha sozinho quando você passa.",
         "The patrons now ask YOU for an audience. The chalk circle glows by itself as you pass."),
        ("Bruxo! Desenhe o círculo com o giz do kit em volta do Altar de Oferendas e chame um Patrono. Cumpra o que ele "
         "pedir — ou prepare-se para lutar.",
         "Warlock! Draw the circle with the chalk from your kit around the Offering Altar and call a Patron. Do what it "
         "asks — or get ready to fight."),
        (("Prepare o círculo", "Prepare the circle"),
         ("Oito runas de giz, um altar no centro e o silêncio antes da voz.",
          "Eight chalk runes, an altar in the center and the silence before the voice."),
         ("Coloque o Altar de Oferendas do kit e desenhe as runas em volta com o Giz Ritual. Ponha a oferenda no altar e "
          "ative o círculo: o Patrono aparece preso nele.",
          "Place the Offering Altar from your kit and draw the runes around it with the Ritual Chalk. Put the offering "
          "on the altar and activate the circle: the Patron appears trapped inside."),
         ("Altar + Giz Ritual", "Altar + Ritual Chalk")),
        [_mrank(1, ("O primeiro pacto.", "The first pact."),
                "converse com o Patrono no círculo e aceite o pacto.", "talk to the Patron in the circle and accept the pact.",
                "Cumpra a exigência dele no prazo; recusar ou falhar liberta o Patrono para lutar.",
                "Meet its demand in time; refusing or failing frees the Patron to fight.", ("Círculo → pacto", "Circle → pact")),
         _mrank(2, ("Dois patronos, duas vozes.", "Two patrons, two voices."),
                "firme mais pactos (há três Patronos: Brasas, Vazio e Afogado).",
                "seal more pacts (there are three Patrons: Embers, Void and Drowned).",
                "", "", ("Círculo → novo pacto", "Circle → new pact")),
         _mrank(3, ("O preço sobe.", "The price goes up."),
                "as exigências ficam maiores (almas, chefes, lugares).", "demands grow (souls, bosses, places).",
                "", "", ("Círculo → pacto III", "Circle → pact III")),
         _mrank(4, ("O Patrono ajoelha.", "The Patron kneels."),
                "pactos avançados pedem Círculos altos e feitos raros.", "advanced pacts ask for high Circles and rare deeds.",
                "", "", ("Círculo → pacto IV", "Circle → pact IV")),
         _mrank(5, ("Senhor dos Pactos.", "Lord of Pacts."),
                "o último pacto exige tudo o que você é.", "the last pact demands everything you are.",
                LESSON_NOTE_PT, LESSON_NOTE_EN, ("Círculo → pacto final", "Circle → final pact"))],
        [(2, S("pacts", _counter("warlock.pacts", 2), ("Firme 2 pactos", "Seal 2 pacts"),
               ("Um patrono é poder. Dois são uma negociação.", "One patron is power. Two is a negotiation."),
               ("Chame Patronos diferentes no círculo de giz e aceite os pactos. Patrono liberto (pacto recusado ou "
                "falho) vira um minichefe — vencê-lo também rende.",
                "Call different Patrons into the chalk circle and accept their pacts. A freed Patron (refused or failed "
                "pact) becomes a mini-boss — defeating it pays too."),
               ("Círculo de giz", "Chalk circle"), xp=150, icon="isekai_mahou:ritual_chalk_item")),
         (4, S("patron_boss", {"type": "bosses", "ids": ["imprisoned_patron"]}, ("O Patrono Aprisionado", "The Imprisoned Patron"),
               ("No Altar Profano, um patrono que ninguém ousou libertar.", "At the Profane Altar, a patron nobody dared to free."),
               ("Vença o Patrono Aprisionado no Altar Profano (a história também passa por ele). Patronos vencidos "
                "respeitam o Bruxo.",
                "Defeat the Imprisoned Patron at the Profane Altar (the story passes there too). Defeated patrons respect "
                "the Warlock."),
               ("Altar Profano", "Profane Altar"), xp=300, icon="isekai_mahou:ritual_chalk_item", optional=True,
               locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:profane_altar",
                                                        "unless": {"type": "bosses", "ids": ["imprisoned_patron"]}}]}))]),

    magic_class(
        "druid", "#3FBF5A", "ancestral_sapling", ["isekai_mahou:ancestral_sapling", "isekai_mahou:druid_staff_t1"],
        ("A Muda Ancestral", "The Ancestral Tree"), ("Estágio %s", "Stage %s"),
        ("O Druida planta a própria magia. A Muda Ancestral cresce com oferendas e com o cuidado da floresta — e cada "
         "estágio da árvore é um rank, um espírito animal e um pedaço do coração do mundo que passa a bater no seu peito.",
         "The Druid plants their own magic. The Ancestral Sapling grows with offerings and with care for the forest — and "
         "every stage of the tree is a rank, an animal spirit and a piece of the world's heart that starts beating in "
         "your chest."),
        ("A Árvore-Coração está completa. Os animais da floresta param quando você passa, só para te ver.",
         "The Heart Tree is complete. The forest's animals stop as you pass, just to watch you."),
        ("Druida! Plante a Muda Ancestral do kit, faça as oferendas e cumpra as tarefas de estação. A árvore cresce, e "
         "você com ela.",
         "Druid! Plant the Ancestral Sapling from your kit, make the offerings and finish the season tasks. The tree "
         "grows, and so do you."),
        (("Plante a Muda Ancestral", "Plant the Sapling"),
         ("Uma folha só, mas brilhando como esmeralda.", "A single leaf, but glowing like an emerald."),
         ("Plante a Muda Ancestral do seu kit num lugar aberto (ela vai crescer MUITO). Clique nela para ver a oferenda e "
          "a tarefa do estágio.",
          "Plant the Ancestral Sapling from your kit in an open spot (it will grow A LOT). Click it to see the offering "
          "and the stage task."),
         ("Muda do kit no chão", "Plant the kit sapling")),
        [_mrank(1, ("A muda desperta.", "The sapling awakens."),
                "faça a oferenda e cumpra a tarefa de estação até a muda crescer.",
                "make the offering and finish the season task until the sapling grows.",
                "", "", ("Muda → oferenda", "Sapling → offering")),
         _mrank(2, ("Raízes fundas.", "Deep roots."),
                "a árvore pede novas oferendas e tarefas da floresta.", "the tree asks for new offerings and forest tasks.",
                "", "", ("Árvore → estágio II", "Tree → stage II")),
         _mrank(3, ("A copa se abre.", "The canopy opens."),
                "o estágio do meio pede Círculo e oferendas maiores.", "the middle stage asks for a Circle and bigger offerings.",
                "", "", ("Árvore → estágio III", "Tree → stage III")),
         _mrank(4, ("A Árvore-Coração pulsa.", "The Heart Tree pulses."),
                "o núcleo da árvore pede essências e feitos raros.", "the tree's core asks for essences and rare deeds.",
                "", "", ("Árvore → estágio IV", "Tree → stage IV")),
         _mrank(5, ("A floresta inteira responde.", "The whole forest answers."),
                "o último estágio completa a Árvore-Coração.", "the last stage completes the Heart Tree.",
                LESSON_NOTE_PT, LESSON_NOTE_EN, ("Árvore → estágio final", "Tree → final stage"))],
        [(2, S("trees", _counter("feat.trees_planted", 10), ("Plante 10 árvores", "Plant 10 trees"),
               ("Quem planta uma floresta nunca caminha sozinho.", "Whoever plants a forest never walks alone."),
               ("Plante mudas (de qualquer árvore) pelo mundo: a Muda Ancestral sente cada uma. Algumas tarefas de estação "
                "pedem isso.",
                "Plant saplings (of any tree) around the world: the Ancestral Sapling feels each one. Some season tasks "
                "ask for it."),
               ("Plante mudas", "Plant saplings"), xp=100, icon="minecraft:oak_sapling")),
         (4, S("ent", {"type": "bosses", "ids": ["corrupted_ent"]}, ("Cure o Ent Corrompido", "Cleanse the Ent"),
               ("No Círculo Druídico, uma árvore antiga apodreceu por dentro.", "At the Druid Circle, an ancient tree rotted from within."),
               ("Vença o Ent Corrompido no Círculo Druídico (a seta aponta). A floresta agradece com uma Muda Ancestral "
                "nova.",
                "Defeat the Corrupted Ent at the Druid Circle (the arrow points there). The forest thanks you with a new "
                "Ancestral Sapling."),
               ("Seta: Círculo Druídico", "Arrow: Druid Circle"), xp=300, icon="isekai_mahou:ancestral_sapling",
               optional=True,
               locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:druid_circle",
                                                        "unless": {"type": "bosses", "ids": ["corrupted_ent"]}}]}))]),

    magic_class(
        "necromancer", "#2EC4C4", "ossuary_altar", ["isekai_mahou:ossuary_altar", "isekai_mahou:reaper_scythe_t1", "isekai_mahou:phylactery"],
        ("O Ossuário", "The Ossuary"), ("Rito Ossuário %s", "Ossuary Rite %s"),
        ("O Necromante colhe o que os outros deixam para trás. Almas presas na foice, ossos no altar, e à meia-noite o "
         "Rito Ossuário: cada rito cumprido é um rank, e cada rank, um servo a mais esperando as suas ordens.",
         "The Necromancer reaps what others leave behind. Souls caught on the scythe, bones on the altar, and at midnight "
         "the Ossuary Rite: every rite fulfilled is a rank, and every rank one more servant awaiting your orders."),
        ("Os mortos não descansam quando você passa — eles se levantam e fazem reverência.",
         "The dead do not rest when you pass — they rise and bow."),
        ("Necromante! Coloque o Altar Ossuário do kit, colha almas dos inimigos e, à meia-noite, faça o Rito. Cada rito "
         "é um rank.",
         "Necromancer! Place the Ossuary Altar from your kit, reap souls from enemies and, at midnight, perform the Rite. "
         "Each rite is a rank."),
        (("Erga o Altar Ossuário", "Raise the Ossuary Altar"),
         ("Crânios, velas negras e um frio que não vem do vento.", "Skulls, black candles and a chill that doesn't come from the wind."),
         ("Coloque o Altar Ossuário do seu kit. Inimigos derrotados por perto (ou com a foice) deixam almas que você "
          "oferece no altar.",
          "Place the Ossuary Altar from your kit. Enemies defeated nearby (or with the scythe) leave souls that you offer "
          "at the altar."),
         ("Altar Ossuário do kit", "Ossuary Altar (kit)")),
        [_mrank(1, ("O primeiro rito.", "The first rite."),
                "com as almas pedidas, crânios no altar e à meia-noite, comece o Rito Ossuário.",
                "with the requested souls, skulls on the altar and at midnight, begin the Ossuary Rite.",
                "Clicar no altar mostra a lista ✔/✘.", "Clicking the altar shows the ✔/✘ list.", ("Meia-noite · almas", "Midnight · souls")),
         _mrank(2, ("O segundo rito.", "The second rite."),
                "ritos novos pedem mais almas e de criaturas diferentes.", "new rites ask for more souls from different creatures.",
                "", "", ("Altar → rito II", "Altar → rite II")),
         _mrank(3, ("Senhor dos ossos.", "Lord of bones."),
                "os ritos do meio pedem Círculo e almas raras.", "middle rites ask for a Circle and rare souls.",
                "", "", ("Altar → rito III", "Altar → rite III")),
         _mrank(4, ("A filactéria pulsa.", "The phylactery pulses."),
                "ritos avançados pedem almas de chefes.", "advanced rites ask for boss souls.",
                "", "", ("Altar → rito IV", "Altar → rite IV")),
         _mrank(5, ("O último rito.", "The last rite."),
                "o rito final faz de você o senhor da morte.", "the final rite makes you lord of death.",
                LESSON_NOTE_PT, LESSON_NOTE_EN, ("Altar → rito final", "Altar → final rite"))],
        [(2, S("souls", _counter("necro.souls", 50), ("Colha 50 almas", "Reap 50 souls"),
               ("Cada alma tem um sussurro. Cinquenta delas são um coral.", "Every soul has a whisper. Fifty of them are a choir."),
               ("Derrote inimigos perto do Altar Ossuário ou com a Foice: as almas vão para você. Ritos pedem tipos "
                "certos — o altar mostra quais.",
                "Defeat enemies near the Ossuary Altar or with the Scythe: their souls go to you. Rites ask for specific "
                "kinds — the altar shows which."),
               ("Abates perto do altar", "Kills near the altar"), xp=120, icon="minecraft:soul_lantern")),
         (4, S("lich", {"type": "bosses", "ids": ["lich"]}, ("Supere o Lich", "Surpass the Lich"),
               ("Na Cripta Esquecida, um necromante que não soube parar.", "In the Forgotten Crypt, a necromancer who didn't know when to stop."),
               ("Vença o Lich na Cripta Esquecida (a seta aponta). A Filactéria dele vale muito para os ritos.",
                "Defeat the Lich in the Forgotten Crypt (the arrow points there). Its Phylactery is worth a lot for "
                "rites."),
               ("Siga a seta: Cripta", "Follow the arrow: Crypt"), xp=300, icon="isekai_mahou:phylactery", optional=True,
               locate={"dimension": OVERWORLD, "any": [{"structure": "isekai_mahou:forgotten_crypt",
                                                        "unless": {"type": "bosses", "ids": ["lich"]}}]}))]),
]

# Ordem final na tela de classe: Mágicas, Físicas, Destreza (a ordem só importa para o "order" dos capítulos)
_ORDER = ["mage", "witch", "warlock", "druid", "necromancer", "smith", "rogue", "knight", "enchanter", "shinigami",
          "quincy", "gunslinger", "ranger", "gambler", "steelball"]
CLASSES.sort(key=lambda c: _ORDER.index(c["id"]))
