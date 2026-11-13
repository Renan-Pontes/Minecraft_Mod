package com.renanpontes.isekaimahou.feature.settlements;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ações novas da tela da cidade (v1.4): planejamento (governante e conselheiros), entregas (membros), conselho
 * (só o governante). Chamado pelo {@code default} de {@link SettlementService#handleAction}; o pacote é o mesmo
 * (cidade, ação, argumento em texto).
 */
public final class CityActions {

    /** Trata a ação. Devolve true para mandar o estado atualizado de volta. */
    public static boolean handle(ServerPlayer sp, Settlement s, String action, String arg) {
        boolean owner = sp.getUUID().equals(s.owner);
        boolean planner = s.canPlan(sp.getUUID());
        boolean member = CityEconomy.isMember(sp.server, s, sp);
        // v1.0 (city_magic): quem planeja uma obra e já conhece a magia de obra recebe a dica (uma vez)
        if (planner && s.own() && (action.equals("plan_build") || action.equals("plan_free"))) {
            com.renanpontes.isekaimahou.feature.settlements.magic.CityMagic.hint(sp);
        }
        // onda 6 (city_pace): na primeira obra planejada, a dica de que as obras levam dias (uma vez)
        if (planner && s.own() && action.startsWith("plan_") && !action.equals("plan_expand")) {
            com.renanpontes.isekaimahou.feature.ui_hud.Hints.show(sp, "city_pace");
        }
        switch (action) {
            case "plan_build" -> {
                String[] a = arg.split("\\|");
                // onda 7: 4º campo opcional = altura escolhida (−4..+4 da praça); sem ele, automática
                if (planner && a.length >= 2) {
                    CityPlanning.build(sp, s, a[0], parse(a[1], -1), a.length > 2 ? a[2] : "", heightArg(a, 3));
                }
            }
            case "plan_upgrade" -> {
                if (planner) CityPlanning.upgrade(sp, s, parse(arg, -1));
            }
            case "plan_repair" -> {
                if (planner) CityPlanning.repair(sp, s, parse(arg, -1));
            }
            case "plan_demolish" -> {
                if (planner) CityPlanning.demolish(sp, s, parse(arg, -1));
            }
            case "plan_terrain" -> {
                String[] a = arg.split("\\|");
                if (planner && a.length >= 2) CityPlanning.terrain(sp, s, parse(a[0], -1), parse(a[1], 0));
            }
            // ---- v1.0 (city_plan): posição livre — "chave|dx|dz|rotação|estilo" e mudar um prédio "lote|dx|dz|rotação"
            case "plan_free" -> {
                String[] a = arg.split("\\|");
                if (planner && a.length >= 4) {
                    CityPlots.build(sp, s, a[0], a.length > 4 ? a[4] : "", parse(a[1], 0), parse(a[2], 0), rotation(parse(a[3], 0)),
                            heightArg(a, 5));
                }
            }
            case "plan_move" -> {
                String[] a = arg.split("\\|");
                if (planner && a.length >= 4) CityPlots.move(sp, s, parse(a[0], -1), parse(a[1], 0), parse(a[2], 0), rotation(parse(a[3], 0)));
            }
            case "plan_expand" -> {
                if (planner) CityPlanning.expand(sp, s);
            }
            case "plan_wall" -> {
                if (planner && s.own()) SettlementService.queueWall(sp, s, parse(arg, -1));
            }
            // ---- onda 5 (city_styles): Reforma de estilo (só o Lorde) e refazer um prédio no estilo da cidade
            case "reform_style" -> {
                if (owner) CityReform.start(sp, s, arg);
            }
            case "plan_restyle" -> {
                if (planner) CityReform.building(sp, s, parse(arg, -1));
            }
            // ---- onda 8 (city_looks): refazer um prédio de mundo antigo no visual novo
            case "plan_relook" -> {
                if (planner) CityLooks.redo(sp, s, parse(arg, -1), false);
            }
            case "center_upgrade" -> {
                if (planner) CityPlanning.upgradeCenter(sp, s);
            }
            case "deposit" -> {
                if (!member) {
                    sp.displayClientMessage(SettlementsLang.tr("needs.deposit.member").withColor(0xE8735E), true);
                } else if (!inCity(sp, s)) {
                    sp.displayClientMessage(SettlementsLang.tr("needs.deposit.far").withColor(0xE8735E), true);
                } else {
                    CityEconomy.depositAll(sp, s);
                }
            }
            case "donate" -> {
                if (!inCity(sp, s)) sp.displayClientMessage(SettlementsLang.tr("needs.deposit.far").withColor(0xE8735E), true);
                else CityEconomy.donate(sp, s);
            }
            case "deliver" -> {
                if (!member) sp.displayClientMessage(SettlementsLang.tr("needs.deposit.member").withColor(0xE8735E), true);
                else if (!inCity(sp, s)) sp.displayClientMessage(SettlementsLang.tr("needs.deposit.far").withColor(0xE8735E), true);
                else CityEconomy.deliver(sp, s, parse(arg, -1));
            }
            case "rush" -> {
                if (planner) rush(sp, s, parse(arg, -1));
            }
            // ---- onda 6 (city_pace): entregar materiais a uma obra (qualquer membro, dentro da cidade)
            case "pace_deliver" -> {
                if (s.own()) CityPace.deliver(sp, s, parse(arg, -1));
            }
            // ---- onda 7 (city_pause): pausar/retomar uma obra ou todas ("índice" ou "all") — quem planeja a cidade
            case "work_pause", "work_resume" -> {
                if (planner && s.own()) CityPause.handle(sp, s, action, arg);
            }
            case "council" -> {
                if (owner) toggleCouncil(sp, s, arg);
            }
            // ---- onda 6 (city_monuments): consagrar/adormecer um monumento (quem planeja a cidade)
            case "monument_toggle" -> {
                if (planner && s.own()) CityMonuments.toggle(sp, s, parse(arg, -1));
            }
            // ---- v1.0 (city_life)
            case "caravan_send" -> {
                String[] a = arg.split("\\|");
                if (planner && a.length >= 3) CityCaravans.send(sp, s, a[0], a[1], parse(a[2], 0));
            }
            case "collect" -> {
                // coleta fácil: impostos (só o governante) + mercadorias guardadas, de qualquer lugar da cidade
                if (!planner) break;
                if (!inCity(sp, s)) {
                    sp.displayClientMessage(SettlementsLang.tr("needs.deposit.far").withColor(0xE8735E), true);
                } else {
                    if (owner && s.treasury > 0) Lordship.withdraw(sp, s, 640);
                    CityStash.collect(sp, s);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static net.minecraft.world.level.block.Rotation rotation(int index) {
        return net.minecraft.world.level.block.Rotation.values()[Math.floorMod(index, 4)];
    }

    /** Dentro do território da cidade (ou a 8 blocos do coração). */
    public static boolean inCity(ServerPlayer sp, Settlement s) {
        if (sp.level().dimension() != s.dim) return false;
        if (sp.blockPosition().distSqr(s.heart) <= 64) return true;
        Places.Place p = SettlementService.cityAt(sp.serverLevel(), sp.blockPosition());
        return p != null && p.id().equals(s.id);
    }

    /** Custo (do cofre) para terminar um conserto na hora. */
    public static int rushCost(Settlement s) {
        return 15 * Math.max(1, s.level);
    }

    /** Paga do cofre para os construtores terminarem um CONSERTO na hora (depois de um ataque). */
    private static void rush(ServerPlayer sp, Settlement s, int index) {
        if (index < 0 || index >= s.projects.size()) return;
        Project p = s.projects.get(index);
        if (!Project.KIND_REPAIR.equals(p.kind)) return;
        int cost = rushCost(s);
        if (s.treasury < cost) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("settlements.isekai_mahou.treasury.poor", cost)
                    .withColor(0xE8735E), true);
            return;
        }
        ServerLevel sl = SettlementService.level(sp.server, s);
        if (sl == null || !sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) {
            sp.displayClientMessage(SettlementsLang.tr("plan.unloaded").withColor(0xE8735E), true);
            return;
        }
        if (ConstructionEngine.finishOne(sl, s, p)) s.treasury -= cost;
    }

    private static void toggleCouncil(ServerPlayer sp, Settlement s, String arg) {
        UUID u;
        try {
            u = UUID.fromString(arg);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (u.equals(s.owner)) return;
        if (s.council.remove(u)) return;
        if (!Allegiance.citizens(sp.server, s.id).contains(u)) {
            sp.displayClientMessage(SettlementsLang.tr("council.citizen_only").withColor(0xE8735E), true);
            return;
        }
        if (s.council.size() >= 6) return;
        s.council.add(u);
        ServerPlayer target = sp.server.getPlayerList().getPlayer(u);
        if (target != null) {
            target.displayClientMessage(SettlementsLang.tr("council.joined", SettlementService.cityName(sp.server, s)).withColor(0xFFD86B), false);
        }
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Altura escolhida no campo {@code i} do pedido (número de −4 a +4), ou null = automática. */
    private static Integer heightArg(String[] a, int i) {
        if (a.length <= i || a[i].isBlank()) return null;
        int v = parse(a[i], Integer.MIN_VALUE);
        return v == Integer.MIN_VALUE ? null : v;
    }

    private CityActions() {}
}
