"""Onda 10 (vehicles_mechanic) -- textos do MECANICO, da OFICINA MECANICA e dos veiculos melhorados.

Uso (na raiz do projeto):  py -I tools/gen/vehicles_w10_lang.py

So ACRESCENTA/atualiza as chaves abaixo; o resto de cada arquivo fica como esta:
  - src/main/lang/vehicles/*     veiculos novos, pecas, kit, vida, servicos do Mecanico, dicas;
  - src/main/lang/settlements/*  Oficina Mecanica (7 estilos) e o oficio Mecanico (arquivo ORDENADO, como os outros
                                 geradores do modulo gravam);
  - src/main/lang/cities/*       Mecanico das cidades do mundo (profissao, falas, servicos);
  - src/main/lang/story/* e src/main/lang/mounts/*: textos que diziam "feitos na bancada" REESCRITOS no lugar.
Se algum gerador antigo (vehicles.py, mounts.py, mounts_v1.py, settlements_*_lang.py, cities.py, journal_story.py)
for rodado de novo, rode este depois. Se o arquivo mudar entre ler e gravar (outro gerador), o script tenta de novo.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang")
NS = "isekai_mahou"

# ---------------------------------------------------------------------------------------------- vehicles
V = {}


def veh(ident, pt, en, dpt, den):
    V[f"entity.{NS}.{ident}"] = (pt, en)
    V[f"item.{NS}.{ident}"] = (pt, en)
    V[f"item.{NS}.{ident}.desc"] = (dpt, den)


veh("mana_rover", "Triciclo de Mana", "Mana Rover",
    "Rápido por terra, sobe degraus e leva o garupa. Bagageiro pequeno (agachado + clique).",
    "Fast over land, climbs steps and carries a passenger. Small luggage rack (sneak + click).")
veh("tide_skiff", "Lancha das Marés", "Tide Skiff",
    "Corre nos rios e no mar com 4 lugares e um porão (agachado + clique). Ideal para as cidades das águas.",
    "Races over rivers and sea with 4 seats and a hold (sneak + click). Made for water cities.")
veh("sky_galleon", "Galeão Celeste", "Sky Galleon",
    "Navio voador para 5, com porão grande (agachado + clique). Ideal para as cidades no céu.",
    "Flying ship for 5, with a large hold (sneak + click). Made for sky cities.")
V.update({
    f"item.{NS}.vehicle_part_engine": ("Motor Rúnico", "Runic Engine"),
    f"item.{NS}.vehicle_part_engine.desc": ("Melhoria de veículo: +10% de velocidade e de aceleração por nível.",
                                             "Vehicle upgrade: +10% top speed and acceleration per level."),
    f"item.{NS}.vehicle_part_armor": ("Blindagem Arcana", "Arcane Plating"),
    f"item.{NS}.vehicle_part_armor.desc": ("Melhoria de veículo: +40% de vida e −15% de dano recebido por nível.",
                                            "Vehicle upgrade: +40% health and −15% damage taken per level."),
    f"item.{NS}.vehicle_part_storage": ("Porão Expandido", "Expanded Hold"),
    f"item.{NS}.vehicle_part_storage.desc": ("Melhoria de veículo: +9 espaços de carga por nível (veículo sem baú ganha um).",
                                              "Vehicle upgrade: +9 cargo slots per level (a vehicle without a chest gets one)."),
    f"item.{NS}.vehicle_part.use": ("Clique no SEU veículo para instalar (até o nível %s). Fica nele mesmo guardado.",
                                    "Click YOUR vehicle to install (up to level %s). It stays even when packed."),
    f"item.{NS}.vehicle_repair_kit": ("Kit de Reparos", "Repair Kit"),
    f"item.{NS}.vehicle_repair_kit.desc": ("Clique num veículo: devolve %s%% da vida dele.", "Click a vehicle: restores %s%% of its health."),
    f"item.{NS}.vehicle.cargo": ("Carga: %s espaços", "Cargo: %s slots"),
    f"item.{NS}.vehicle.health": ("Vida: %s%%", "Health: %s%%"),
    f"item.{NS}.vehicle.upgrade": ("%s: nível %s/%s", "%s: level %s/%s"),
    f"message.{NS}.vehicle_whole": ("O veículo já está inteiro.", "The vehicle is already in one piece."),
    f"message.{NS}.vehicle_repaired": ("Reparado: vida %s%%", "Repaired: health %s%%"),
    f"message.{NS}.vehicle_part_max": ("%s já está no nível máximo neste veículo.", "%s is already at max level on this vehicle."),
    f"message.{NS}.vehicle_part_done": ("%s instalado: nível %s/%s", "%s installed: level %s/%s"),
    f"message.{NS}.vehicle_critical": ("Veículo muito avariado! Conserte com um Kit de Reparos ou no Mecânico.",
                                       "Vehicle badly damaged! Fix it with a Repair Kit or at a Mechanic."),
    f"message.{NS}.vehicle_wrecked": ("Seu %s foi destruído em %s %s %s. A carga ficou no chão; um Mecânico pode reconstruí-lo.",
                                      "Your %s was destroyed at %s %s %s. The cargo is on the ground; a Mechanic can rebuild it."),
    f"message.{NS}.vehicle_recalled": ("O Mecânico já resgatou este veículo: esta cópia antiga se desfez.",
                                       "The Mechanic already recovered this vehicle: this old copy fell apart."),
    f"message.{NS}.mechanic.ask.vehicle_repair": ("Consertar %1$s (vida %2$s%%) por %3$s moedas? Clique de novo para confirmar.",
                                                  "Repair %1$s (health %2$s%%) for %3$s coins? Click again to confirm."),
    f"message.{NS}.mechanic.ask.vehicle_rebuild": ("Reconstruir %1$s, que foi destruído, por %3$s moedas? Clique de novo para confirmar.",
                                                   "Rebuild %1$s, which was destroyed, for %3$s coins? Click again to confirm."),
    f"message.{NS}.mechanic.ask.vehicle_recover": ("Resgatar %1$s (vida %2$s%%), que ficou longe, por %3$s moedas? A cópia antiga some. Clique de novo para confirmar.",
                                                   "Recover %1$s (health %2$s%%), left far away, for %3$s coins? The old copy vanishes. Click again to confirm."),
    f"message.{NS}.mechanic.done.repair": ("%s está novinho em folha!", "%s is as good as new!"),
    f"message.{NS}.mechanic.done.rebuild": ("%s foi reconstruído: está no seu inventário.", "%s was rebuilt: it is in your inventory."),
    f"message.{NS}.mechanic.done.recover": ("%s foi resgatado: está no seu inventário.", "%s was recovered: it is in your inventory."),
    f"message.{NS}.mechanic.summary": ("Seus veículos: %s no mundo, %s guardados, %s destruídos.",
                                       "Your vehicles: %s out in the world, %s packed, %s destroyed."),
    f"message.{NS}.mechanic.summary.none": ("Você ainda não tem nenhum veículo no meu caderno.", "You have no vehicles in my logbook yet."),
    f"message.{NS}.mechanic.line.out": ("%1$s — no mundo em %3$s (%4$s) · vida %2$s%%", "%1$s — out at %3$s (%4$s) · health %2$s%%"),
    f"message.{NS}.mechanic.line.packed": ("%s — guardado · vida %s%%", "%s — packed · health %s%%"),
    f"message.{NS}.mechanic.line.wrecked": ("%s — destruído", "%s — destroyed"),
    f"hud.{NS}.vehicle_health": ("Vida: %s%%", "Health: %s%%"),
    f"hint.{NS}.vehicles.mechanic": (
        "Mecânico! Vende veículos (mais tipos a cada nível da Oficina), peças de melhoria e Kits de Reparos. %s + clique: conserta, reconstrói um destruído ou resgata um perdido.",
        "Mechanic! Sells vehicles (more kinds with each Workshop level), upgrade parts and Repair Kits. %s + click: repairs, rebuilds a destroyed one or recovers a lost one."),
    f"hint.{NS}.vehicles.mechanic.world": (
        "Mecânico: veículos e peças em Trocar (mais veículos com mais reputação no reino). Em Serviços ele conserta, reconstrói um veículo destruído e resgata um perdido.",
        "Mechanic: vehicles and parts under Trade (more vehicles with more standing in the kingdom). Under Services he repairs, rebuilds a destroyed vehicle and recovers a lost one."),
    f"hint.{NS}.vehicles.wrecked": (
        "Veículo destruído! A carga caiu no chão. Qualquer Mecânico o reconstrói, com as melhorias — na praça das cidades do mundo ou na Oficina Mecânica da sua cidade.",
        "Vehicle destroyed! The cargo fell on the ground. Any Mechanic rebuilds it, upgrades included — on the plaza of world cities or in your city's Mechanic's Workshop."),
})

# ---------------------------------------------------------------------------------------------- settlements
SP = "settlements.isekai_mahou."
STYLES = {
    "kingdom": ("Oficina Mecânica", "Mechanic's Workshop"),
    "desert": ("Oficina das Caravanas", "Caravan Workshop"),
    "nordic": ("Oficina do Estaleiro", "Shipwright's Workshop"),
    "oriental": ("Oficina dos Artífices", "Artificers' Workshop"),
    "elven": ("Oficina dos Ventos", "Windwrights' Workshop"),
    "gothic": ("Oficina das Engrenagens", "Gearworks"),
    "dwarven": ("Oficina dos Engenhos", "Engine Workshop"),
}
S = {}
for st, (pt, en) in STYLES.items():
    S[f"bp.{st}.garage"] = (pt, en)
    S[f"bp.{st}.garage.desc"] = (
        "Traz o Mecânico: veículos (mais tipos a cada nível), peças de melhoria e Kits de Reparos; conserta, reconstrói e resgata veículos.",
        "Brings the Mechanic: vehicles (more kinds each level), upgrade parts and Repair Kits; repairs, rebuilds and recovers vehicles.")
S.update({
    "trade.job.mechanic": ("Mecânico", "Mechanic"),
    "trade.job.mechanic.f": ("Mecânica", "Mechanic"),
    "trade.line.mechanic.ambient.0": ("Esse motor rúnico ronrona que é uma beleza.", "This runic engine purrs like a cat."),
    "trade.line.mechanic.ambient.1": ("Quem deixou a carroça sem cristal de novo?", "Who left the cart without a crystal again?"),
    "trade.line.mechanic.ambient.2": ("Hoje sai um tapete novinho da baia.", "A brand new carpet leaves the bay today."),
    "trade.line.mechanic.hello.0": ("Veículo novo ou conserto?", "New vehicle or a repair?"),
    "trade.line.mechanic.hello.1": ("Bem-vindo à oficina! Cuidado com a mana derramada.", "Welcome to the workshop! Mind the spilled mana."),
    "trade.line.mechanic.thanks.0": ("Bom proveito, e devagar nas curvas!", "Enjoy it, and easy on the turns!"),
    "trade.line.mechanic.thanks.1": ("Volte se ele fizer barulho estranho.", "Come back if it makes a funny noise."),
    "trade.talk.mechanic": (
        "Vendo veículos — mais tipos conforme a Oficina cresce —, peças de melhoria e Kits de Reparos. Agache e clique: conserto o veículo da sua mão ou o seu estacionado aqui perto, reconstruo um destruído e resgato um perdido.",
        "I sell vehicles — more kinds as the Workshop grows —, upgrade parts and Repair Kits. Sneak and click: I repair the vehicle in your hand or yours parked nearby, rebuild a destroyed one and recover a lost one."),
    "ui.shops.tip.sells.mechanic": (
        "Veículos (Barco e Carroça no nível 1; Tapete no 2; Vassoura e Triciclo no 3; Dirigível e Lancha no 4; Galeão no 5), peças de melhoria, Kits de Reparos e Cristais de Mana. Serviços: consertar, reconstruir, resgatar.",
        "Vehicles (Boat and Cart at level 1; Carpet at 2; Broom and Rover at 3; Airship and Skiff at 4; Galleon at 5), upgrade parts, Repair Kits and Mana Crystals. Services: repair, rebuild, recover."),
})

# ---------------------------------------------------------------------------------------------- cities
C = {
    f"profession.{NS}.cities.mechanic": ("Mecânico", "Mechanic"),
    f"profession.{NS}.cities.mechanic.f": ("Mecânica", "Mechanic"),
    f"dialogue.{NS}.cities.merchant.mechanic.greet.0": ("Precisa de rodas, %s? Ou de asas?", "Need wheels, %s? Or wings?"),
    f"dialogue.{NS}.cities.merchant.mechanic.greet.1": ("Veículos novos, peças e consertos. O que vai ser?",
                                                       "New vehicles, parts and repairs. What will it be?"),
    f"dialogue.{NS}.cities.merchant.mechanic.ambient.0": ("*clanc* Pronto, mais um parafuso rúnico.", "*clank* There, one more runic bolt."),
    f"dialogue.{NS}.cities.merchant.mechanic.ambient.1": ("Um Galeão Celeste... um dia eu monto um.", "A Sky Galleon... one day I'll build one."),
    f"dialogue.{NS}.cities.merchant.mechanic.ambient.2": ("Cristal no tanque e o mundo é seu.", "A crystal in the tank and the world is yours."),
    f"service.{NS}.cities.vehicle_repair": ("Consertar veículo", "Repair vehicle"),
    f"service.{NS}.cities.vehicle_repair.desc": ("O veículo da sua mão, ou o seu estacionado aqui perto (até 16 blocos), fica inteiro.",
                                                 "The vehicle in your hand, or yours parked nearby (up to 16 blocks), is made whole."),
    f"service.{NS}.cities.vehicle_rebuild": ("Reconstruir veículo", "Rebuild vehicle"),
    f"service.{NS}.cities.vehicle_rebuild.desc": ("Um veículo seu que foi destruído volta inteiro, com as melhorias (tanque vazio).",
                                                  "A vehicle of yours that was destroyed comes back whole, upgrades included (empty tank)."),
    f"service.{NS}.cities.vehicle_recover": ("Resgatar veículo", "Recover vehicle"),
    f"service.{NS}.cities.vehicle_recover.desc": ("Um veículo seu que ficou longe ou se perdeu volta para o inventário; a cópia antiga some onde estiver.",
                                                  "A vehicle of yours left far away or lost comes back to your inventory; the old copy vanishes wherever it is."),
}

# ---------------------------------------------------------------------------------------------- textos reescritos
STORY = {
    f"guide.{NS}.vehicles.where": (
        "Com o Mecânico: na Oficina Mecânica da sua cidade (mais veículos a cada nível) ou na praça das cidades do mundo (mais veículos com reputação no reino). Só o Barco Arcano ainda sai da bancada.",
        "From the Mechanic: in your city's Mechanic's Workshop (more vehicles each level) or on the plaza of world cities (more vehicles with standing in the kingdom). Only the Arcane Boat is still crafted."),
    f"guide.{NS}.vehicles.how": (
        "Andam com a sua mana (sempre deixam 25%) e depois com o tanque — carregue clicando com um Cristal de Mana (cristais no baú recarregam sozinhos). Monstros e explosões estragam: Kit de Reparos ou o Mecânico consertam, e um destruído ele reconstrói. Agachar + bater guarda o veículo como item, com carga, vida e melhorias.",
        "They run on your mana (always leaving 25%) and then on the tank — charge it by clicking with a Mana Crystal (crystals in the hold refill it by themselves). Monsters and explosions damage them: a Repair Kit or the Mechanic fixes them, and he rebuilds a destroyed one. Sneak + hit packs the vehicle into an item, with charge, health and upgrades."),
}
MOUNTS = {
    f"journal.{NS}.mounts.vehicle.hint": (
        "Compre com o Mecânico (praça das cidades do mundo ou Oficina Mecânica da sua cidade): Barco, Carroça, Tapete, Vassoura, Triciclo, Dirigível, Lancha e Galeão. Coloque no chão e clique para subir.",
        "Buy from the Mechanic (plaza of world cities or your city's Mechanic's Workshop): Boat, Cart, Carpet, Broom, Rover, Airship, Skiff and Galleon. Place it on the ground and click to board."),
}


def merge(folder, fname, idx, table, prefix="", sort=False, only_existing=False):
    path = os.path.join(LANG, folder, fname)
    for _ in range(5):
        stamp = os.stat(path).st_mtime_ns
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        added = 0
        for k, v in table.items():
            key = prefix + k
            if only_existing and key not in data:
                continue
            added += key not in data
            data[key] = v[idx]
        if os.stat(path).st_mtime_ns != stamp:
            continue
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(dict(sorted(data.items())) if sort else data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("%s/%s: %d chaves (%d novas)" % (folder, fname, len(table), added))
        return
    raise SystemExit("%s/%s mudou a cada tentativa: rode de novo" % (folder, fname))


if __name__ == "__main__":
    for i, name in enumerate(("pt_br.json", "en_us.json")):
        merge("vehicles", name, i, V)
        merge("settlements", name, i, S, prefix=SP, sort=True)
        merge("cities", name, i, C)
        merge("story", name, i, STORY, only_existing=True)
        merge("mounts", name, i, MOUNTS, only_existing=True)
