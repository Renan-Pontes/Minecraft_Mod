package com.renanpontes.isekaimahou.feature.settlements;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.settlements.block.SettlementHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * REFORMA DE ESTILO (onda 5, city_styles): o Lorde troca o estilo de uma cidade que já existe — "com custo alto e obra
 * gradual". Nada é derrubado na hora:
 * <ul>
 *   <li>a cidade passa a ser do estilo novo (cor, bônus e ponto fraco, moradores e guardas, catálogo de prédios);</li>
 *   <li>a <b>praça</b> entra na fila de obras para ser refeita com o molde do estilo novo;</li>
 *   <li><b>ruas, postes, monumento, fontes e acabamento</b> das fachadas são trocados aos poucos pelos construtores
 *       ({@link CityRenewal}) e cada <b>muralha</b> é reformada na fila, uma por vez (só sai o bloco que os
 *       construtores puseram);</li>
 *   <li>os <b>prédios</b> que já estão de pé FICAM no estilo antigo (anotado por lote) — o jogador reforma um por um
 *       quando quiser ({@link #building}), pagando do cofre, com as mesmas regras de uma melhoria (baús vazios).
 *       Castelo e maravilhas, que só existem num estilo, continuam como são.</li>
 * </ul>
 * O custo sai do cofre e cresce com o degrau da cidade (trocar a cara de uma Capital é caro).
 */
public final class CityReform {

    /** Moedas do cofre para trocar o estilo da cidade no degrau em que ela está. */
    public static int cost(Settlement s) {
        int c = 150;
        for (int l = 2; l <= CityTier.clamp(s.level); l++) c += 2 * CityTier.cost(l);
        return c;
    }

    /** Moedas do cofre para refazer UM prédio no estilo da cidade. */
    public static int buildingCost(int buildingLevel, int cityLevel) {
        return 10 * Math.max(1, buildingLevel) + 5 * Math.max(1, cityLevel);
    }

    /** Prédios de pé que ainda estão em outro estilo e têm planta no estilo da cidade. */
    public static int pending(Settlement s) {
        int n = 0;
        for (var e : s.buildings.entrySet()) {
            int cell = e.getKey();
            if (cell == CityLayout.CENTER_CELL || s.styleOf(cell) == s.style) continue;
            if (Blueprints.get(s.style, e.getValue()) != null) n++;
        }
        return n;
    }

    /** Por que a cidade não pode ser reformada agora (null = pode). */
    @Nullable
    public static Component problem(Settlement s, @Nullable SettlementStyle target) {
        if (!s.own()) return SettlementsLang.tr("plan.own_only");
        if (target == null || target == s.style) return SettlementsLang.tr("reform.same");
        if (!s.has(Blueprint.PLAZA)) return SettlementsLang.tr("bp.need_plaza");
        for (Project p : s.projects) {
            if (Project.KIND_CENTER.equals(p.kind) || Project.KIND_TIER.equals(p.kind)) return SettlementsLang.tr("reform.busy");
        }
        if (s.projects.size() >= SettlementService.MAX_QUEUE) return SettlementsLang.tr("plan.queue_full", SettlementService.MAX_QUEUE);
        int cost = cost(s);
        if (s.treasury < cost) return SettlementsLang.tr("reform.poor", cost, s.treasury);
        return null;
    }

    /** O Lorde pede a reforma: confere, cobra do cofre e começa. */
    public static boolean start(ServerPlayer sp, Settlement s, String styleId) {
        SettlementStyle target = SettlementStyle.byId(styleId);
        Component pr = problem(s, target);
        if (pr != null) {
            sp.displayClientMessage(pr.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
            return false;
        }
        ServerLevel sl = SettlementService.level(sp.server, s);
        if (sl == null || !sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) {
            sp.displayClientMessage(SettlementsLang.tr("plan.unloaded").withColor(0xE8735E), true);
            return false;
        }
        s.treasury -= cost(s);
        apply(sl, s, target, true);
        Hints.show(sp, "city_reform");
        return true;
    }

    /**
     * Troca o estilo da cidade e põe a obra gradual para andar (também usado pelo comando de teste, sem custo).
     *
     * @param announce avisa os governantes e toca o sino
     */
    public static void apply(ServerLevel sl, Settlement s, SettlementStyle target, boolean announce) {
        MinecraftServer server = sl.getServer();
        SettlementStyle old = s.style;
        if (target == old) return;
        // o que já está de pé continua sendo do estilo em que foi feito (a planta e o molde dependem disso)
        for (int cell : s.buildings.keySet()) {
            if (cell != CityLayout.CENTER_CELL && !s.bStyles.containsKey(cell)) s.bStyles.put(cell, old.id);
        }
        s.bStyles.values().removeIf(v -> v.equals(target.id));
        // muralha sem anotação = moldes antigos do estilo velho (grau 0): a reforma precisa saber o que tirar
        for (int k : s.walls) s.wallLooks.putIfAbsent(k, old.id + ":0");
        // obras na fila terminam no estilo em que foram pedidas
        for (Project p : s.projects) if (p.style.isEmpty()) p.style = old.id;
        clearPlazaCorners(sl, s, old);
        s.style = target;
        s.points = Math.max(s.points, Blueprints.pointsFor(target, s.level));
        // coração (cor do cristal), lugar no mapa e quem mora na cidade
        if (sl.getBlockEntity(s.heart) instanceof SettlementHeartBlockEntity be) be.bind(s.id, target);
        SettlementService.registerPlace(sl, s);
        double r = CityLayout.half(s) + 48;
        for (SettlementNpc npc : sl.getEntitiesOfClass(SettlementNpc.class, new AABB(s.heart).inflate(r, 96, r),
                n -> s.id.equals(n.cityId()))) {
            npc.restyle(target);
        }
        // a praça é refeita com o molde novo (obra do centro no MESMO degrau: sem festa) e o resto vem aos poucos
        if (s.has(Blueprint.PLAZA)) {
            s.projects.add(Math.min(s.projects.size(), 1), CityPlanning.centerProject(s, s.level));
        }
        CityRenewal.restart(s);
        if (!announce) return;
        Component city = SettlementService.cityName(server, s);
        for (ServerPlayer g : CityEconomy.governors(server, s)) {
            Rewards.banner(g, SettlementsLang.tr("reform.started", city, target.displayName()), target.color);
            g.displayClientMessage(SettlementsLang.tr("reform.started.info").withColor(target.color), false);
        }
        sl.playSound(null, s.heart, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.0F, 0.9F);
    }

    /**
     * Os canteiros das diagonais da praça são o que mais muda de um estilo para o outro (flores, palmeiras, braseiros,
     * cerejeiras): tira os do estilo VELHO antes de a praça ser refeita — só o que ainda for o bloco do molde (o que o
     * jogador pôs ali fica).
     */
    private static void clearPlazaCorners(ServerLevel sl, Settlement s, SettlementStyle old) {
        Templates.Tpl tpl = Templates.get(sl, Blueprint.template(old, Blueprint.PLAZA));
        if (tpl == null) return;
        Integer level = CityTerrain.levelOf(s, CityLayout.CENTER_CELL);
        int base = level == null ? s.heart.getY() - 1 : level;
        Rotation rot = s.plazaRotation();
        BlockPos origin = new BlockPos(s.heart.getX() - tpl.width(rot) / 2, base, s.heart.getZ() - tpl.depth(rot) / 2);
        for (int i = tpl.entries().size() - 1; i >= 0; i--) {
            Templates.Entry e = tpl.entries().get(i);
            if (e.pos().getY() < 1 || !Templates.plazaCorner(tpl, e)) continue;
            BlockPos p = origin.offset(tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), rot));
            if (!p.equals(s.heart) && sl.getBlockState(p).is(e.state().getBlock())) sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    /** Refaz um prédio no estilo da cidade (ação do painel). */
    public static boolean building(ServerPlayer sp, Settlement s, int cell) {
        return CityPlanning.restyle(sp, s, cell);
    }

    private CityReform() {}
}
