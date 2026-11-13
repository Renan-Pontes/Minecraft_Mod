package com.renanpontes.isekaimahou.feature.settlements;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Planejamento da cidade fundada (v1.4): o governante (ou um conselheiro) escolhe o que vai em CADA terreno da grade —
 * construir (com o estilo escolhido), trocar um prédio por outro, melhorar o nível (1→5, nunca acima do centro),
 * consertar o que um ataque danificou, demolir e aplainar o terreno numa altura escolhida. Tudo de graça, mas leva o
 * tempo dos construtores (bloco a bloco, {@link ConstructionEngine}).
 * <p>Sem duplicar itens: trocar/melhorar/demolir um prédio só é aceito com os baús/barris do terreno VAZIOS.
 */
public final class CityPlanning {
    /** Meia-largura do terreno inteiro (15×15 dentro da célula de 16). */
    public static final int PLOT_HALF = 7;
    /** Desnível máximo do aplainar, em relação ao piso da praça. */
    public static final int MAX_TERRAIN_OFFSET = 4;

    private static boolean msg(ServerPlayer sp, String key, Object... args) {
        sp.displayClientMessage(SettlementsLang.tr(key, args).withColor(0xE8735E), true);
        sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
        return false;
    }

    private static boolean fail(ServerPlayer sp, int cell, String key) {
        return msg(sp, key, "plan.locked".equals(key) ? CityLayout.unlockLevel(cell) : SettlementService.MAX_QUEUE);
    }

    private static void ok(ServerPlayer sp, Settlement s, Component text) {
        sp.displayClientMessage(text.copy().withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static boolean validCell(int cell) {
        return CityLayout.valid(cell) && cell != CityLayout.CENTER_CELL;
    }

    public static boolean unlocked(Settlement s, int cell) {
        return CityLayout.unlocked(s, cell);
    }

    /** Problema comum a toda obra num terreno (null = pode). */
    @Nullable
    private static String plotProblem(Settlement s, int cell) {
        if (!s.own()) return "plan.own_only";
        // v1.0 (city_plan): terreno da grade OU lote livre (posição escolhida pelo jogador) — regras em CityPlots
        String where = CityPlots.cellProblem(s, cell, cell);
        if (where != null) return where;
        if (!s.has(Blueprint.PLAZA)) return "bp.need_plaza";
        if (s.projectAt(cell) != null) return "plan.busy";
        if (s.projects.size() >= SettlementService.MAX_QUEUE) return "plan.queue_full";
        return null;
    }

    // =====================================================================================
    //  Ações
    // =====================================================================================
    /** Constrói (ou troca) o prédio de um terreno. */
    public static boolean build(ServerPlayer sp, Settlement s, String key, int cell, String styleId) {
        return build(sp, s, key, cell, styleId, null);
    }

    /**
     * Onda 7 (pedido do dono: "ao mandar construir com a altura -1, ele não cava... deveria cavar e fazer"): com
     * {@code offset} (−4..+4 em relação ao piso da praça) o prédio novo fica NESSA altura — a obra corta/aterra o lote
     * inteiro ali, como o "Aplainar" faria antes. null = altura automática (ou a que o lote já tem).
     */
    public static boolean build(ServerPlayer sp, Settlement s, String key, int cell, String styleId, @Nullable Integer offset) {
        String pr = plotProblem(s, cell);
        if (pr != null) return fail(sp, cell, pr);
        SettlementStyle style = SettlementStyle.byId(styleId);
        if (style == null) style = s.style;
        Blueprint bp = Blueprints.get(style, key);
        if (bp == null) return msg(sp, "plan.no_style");
        if (!bp.usesCell()) return msg(sp, "plan.bad_cell");
        if (bp.minLevel() > s.level) return msg(sp, "bp.need_level", bp.minLevel());
        String old = s.buildings.get(cell);
        int count = s.count(key) + s.inProgress(key) - (key.equals(old) ? 1 : 0);
        if (count >= CityGrowth.maxCount(s, bp)) return msg(sp, "bp.maxed");
        boolean replace = old != null;
        if (replace && !checkContainers(sp, s, cell)) return false;
        if (replace) clearCell(s, cell);
        int variant = Math.floorMod(cell, Blueprints.variants(key));
        Project.Piece first = piece(s, cell, bp.template(variant, 1));
        // altura escolhida pelo jogador (só em lote sem prédio: trocar um prédio mantém o nível do lote)
        if (offset != null && !replace) {
            // onda 8 (water_city): na água nada fica abaixo do piso da praça (cavar ali abriria um buraco no mar)
            first.baseY = s.heart.getY() - 1 + WaterCity.offset(s, Math.max(-MAX_TERRAIN_OFFSET, Math.min(MAX_TERRAIN_OFFSET, offset)));
        }
        Project p = new Project(key, cell, List.of(first));
        p.kind = Project.KIND_BUILD;
        p.level = 1;
        p.style = style.id;
        // v1.0: toda obra aplaina o lote inteiro no nível da rua (CityTerrain)
        p.clearHalf = CityTerrain.LEVEL_HALF;
        s.projects.add(p);
        ok(sp, s, SettlementsLang.tr(replace ? "plan.replacing" : "plan.queued", bp.displayName()));
        return true;
    }

    /** Melhora o prédio de um terreno em 1 nível (até o nível do centro). */
    public static boolean upgrade(ServerPlayer sp, Settlement s, int cell) {
        String pr = plotProblem(s, cell);
        if (pr != null) return fail(sp, cell, pr);
        Blueprint bp = s.blueprintAt(cell);
        if (bp == null) return msg(sp, "plan.empty");
        int lvl = s.levelOf(cell);
        if (lvl >= Settlement.MAX_BUILDING_LEVEL) return msg(sp, "plan.max_level");
        // onda 6: monumentos têm 3 níveis
        if (lvl >= CityMonuments.maxLevel(bp.key())) return msg(sp, "mon.max_level", CityMonuments.MAX_LEVEL);
        if (lvl + 1 > s.level) return msg(sp, "plan.center_cap", s.level);
        if (s.damaged.contains(cell)) return msg(sp, "plan.repair_first");
        if (!checkContainers(sp, s, cell)) return false;
        int variant = Math.floorMod(cell, Blueprints.variants(bp.key()));
        Project p = new Project(bp.key(), cell, List.of(piece(s, cell, bp.template(variant, lvl + 1))));
        p.kind = Project.KIND_UPGRADE;
        p.level = lvl + 1;
        p.style = s.styleOf(cell).id;
        p.clearHalf = CityTerrain.LEVEL_HALF;
        s.projects.add(p);
        ok(sp, s, SettlementsLang.tr("plan.upgrading", bp.displayName(), lvl + 1));
        return true;
    }

    /**
     * Onda 5 (city_styles): refaz o prédio de um terreno no ESTILO DA CIDADE, no mesmo nível (depois de uma Reforma de
     * estilo). Mesmas regras de uma melhoria (terreno livre de obra, baús vazios) e custa moedas do cofre.
     */
    public static boolean restyle(ServerPlayer sp, Settlement s, int cell) {
        String pr = plotProblem(s, cell);
        if (pr != null) return fail(sp, cell, pr);
        Blueprint old = s.blueprintAt(cell);
        if (old == null) return msg(sp, "plan.empty");
        if (s.styleOf(cell) == s.style) return msg(sp, "reform.b.same");
        // castelo e maravilhas só existem no estilo de origem: ficam como são
        Blueprint bp = Blueprints.get(s.style, old.key());
        if (bp == null) return msg(sp, "reform.b.unique");
        if (s.damaged.contains(cell)) return msg(sp, "plan.repair_first");
        int lvl = s.levelOf(cell);
        int cost = CityReform.buildingCost(lvl, s.level);
        if (s.treasury < cost) return msg(sp, "reform.poor", cost, s.treasury);
        if (!checkContainers(sp, s, cell)) return false;
        s.treasury -= cost;
        int variant = Math.floorMod(cell, Blueprints.variants(bp.key()));
        Project p = new Project(bp.key(), cell, List.of(piece(s, cell, bp.template(variant, lvl))));
        p.kind = Project.KIND_RESTYLE;
        p.level = lvl;
        p.style = s.style.id;
        p.clearHalf = CityTerrain.LEVEL_HALF;
        s.projects.add(p);
        ok(sp, s, SettlementsLang.tr("reform.b.queued", bp.displayName(), s.style.displayName()));
        return true;
    }

    /** Conserta um prédio danificado (recoloca os blocos do molde; não limpa nada). */
    public static boolean repair(ServerPlayer sp, Settlement s, int cell) {
        String pr = plotProblem(s, cell);
        if (pr != null) return fail(sp, cell, pr);
        Blueprint bp = s.blueprintAt(cell);
        if (bp == null || !s.damaged.contains(cell)) return msg(sp, "plan.not_damaged");
        int variant = Math.floorMod(cell, Blueprints.variants(bp.key()));
        // onda 8: o conserto recoloca o molde em que o prédio FOI erguido (visual antigo ou novo), nunca outro por cima
        Project p = new Project(bp.key(), cell, List.of(piece(s, cell, CityLooks.standing(s, cell, bp))));
        p.kind = Project.KIND_REPAIR;
        p.level = s.levelOf(cell);
        p.style = s.styleOf(cell).id;
        p.phase = Project.PHASE_BUILD;
        // o conserto não limpa nem aplaina nada (começa nos blocos); isto só faz o acabamento seguir as regras novas
        // de terreno (escada só onde é inevitável, sempre no mesmo lugar) em vez de pôr degraus na porta
        p.clearHalf = CityTerrain.LEVEL_HALF;
        // conserto vai para a frente da fila
        s.projects.add(Math.min(s.projects.size(), firstFreeSlot(s)), p);
        ok(sp, s, SettlementsLang.tr("plan.repairing", bp.displayName()));
        return true;
    }

    /** Demole o prédio (vira terreno aplainado na mesma altura). */
    public static boolean demolish(ServerPlayer sp, Settlement s, int cell) {
        String pr = plotProblem(s, cell);
        if (pr != null) return fail(sp, cell, pr);
        if (!s.buildings.containsKey(cell)) return msg(sp, "plan.empty");
        if (!checkContainers(sp, s, cell)) return false;
        Integer base = s.plotBase.get(cell);
        clearCell(s, cell);
        int y = base != null ? base : s.heart.getY() - 1;
        queueTerrain(s, cell, y);
        ok(sp, s, SettlementsLang.tr("plan.demolishing"));
        return true;
    }

    /** Aplaina um terreno vazio na altura escolhida (−4..+4 do piso da praça). */
    public static boolean terrain(ServerPlayer sp, Settlement s, int cell, int offset) {
        String pr = plotProblem(s, cell);
        if (pr != null) return fail(sp, cell, pr);
        if (s.buildings.containsKey(cell)) return msg(sp, "plan.terrain_occupied");
        int off = WaterCity.offset(s, Math.max(-MAX_TERRAIN_OFFSET, Math.min(MAX_TERRAIN_OFFSET, offset)));
        queueTerrain(s, cell, s.heart.getY() - 1 + off);
        ok(sp, s, SettlementsLang.tr("plan.terraforming", off >= 0 ? "+" + off : Integer.toString(off)));
        return true;
    }

    /**
     * v1.0 (city_magic): Moldar Lote — as MESMAS regras do aplainar (lote liberado, vazio, sem obra, fila com vaga,
     * altura a até {@link #MAX_TERRAIN_OFFSET} do piso da praça), mas com a altura já escolhida pela magia. Põe a obra
     * de terreno na fila e a devolve (quem chama a termina na hora); null = recusado (o jogador já foi avisado).
     */
    @Nullable
    public static Project terrainByMagic(ServerPlayer sp, Settlement s, int cell, int y) {
        String pr = plotProblem(s, cell);
        if (pr != null) {
            fail(sp, cell, pr);
            return null;
        }
        if (s.buildings.containsKey(cell)) {
            msg(sp, "plan.terrain_occupied");
            return null;
        }
        int plaza = s.heart.getY() - 1;
        queueTerrain(s, cell, Math.max(s.water ? plaza : plaza - MAX_TERRAIN_OFFSET, Math.min(plaza + MAX_TERRAIN_OFFSET, y)));
        return s.projects.get(s.projects.size() - 1);
    }

    static void queueTerrain(Settlement s, int cell, int y) {
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        Project p = new Project("terrain", cell, List.of(new Project.Piece(Templates.procId(s.style, "pad", 1), c.getX(), c.getZ(),
                Rotation.NONE, Project.Piece.BUILDING, y)));
        p.kind = Project.KIND_TERRAIN;
        p.clearHalf = CityTerrain.LEVEL_HALF;
        s.projects.add(p);
    }

    // =====================================================================================
    //  Expansão (a cidade cresce um anel de terrenos)
    // =====================================================================================
    /** Nível do centro pedido para abrir o anel {@code ring}. */
    public static int expandLevel(int ring) {
        return CityGrowth.needLevel(ring);
    }

    /** Terrenos com prédio pedidos para abrir o próximo anel (v1.0: metade dos que a cidade já tem). */
    public static int expandBuildings(Settlement s) {
        return CityGrowth.needBuildings(s);
    }

    public static int builtPlots(Settlement s) {
        int n = 0;
        for (int c : s.buildings.keySet()) if (c != CityLayout.CENTER_CELL) n++;
        return n;
    }

    /** Por que a cidade ainda não pode crescer (null = pode). */
    @Nullable
    public static Component expandProblem(net.minecraft.server.MinecraftServer server, Settlement s) {
        if (!s.own()) return SettlementsLang.tr("plan.own_only");
        if (s.radius >= CityLayout.MAX_RADIUS) return SettlementsLang.tr("expand.max", 2 * s.radius + 1);
        int ring = s.radius + 1;
        // v1.0: gente + felicidade + prédios (+ o porte do centro) — nada de espera
        for (CityEconomy.Req r : CityGrowth.expandReqs(server, s)) {
            if (!r.ok()) return SettlementsLang.tr("expand.missing", r.text());
        }
        ServerLevel sl = SettlementService.level(server, s);
        if (sl == null) return SettlementsLang.tr("plan.unloaded");
        net.minecraft.world.level.levelgen.structure.BoundingBox box = CityLayout.territory(s, ring);
        for (com.renanpontes.isekaimahou.core.api.Places.Place p : com.renanpontes.isekaimahou.core.api.Places.all(sl)) {
            if (p.dim() != s.dim || p.id().equals(s.id)) continue;
            boolean town = SettlementService.isCity(p) || "village".equals(p.type());
            if (town && p.box().intersects(box)) return SettlementsLang.tr("expand.blocked", p.name());
        }
        // onda 7 (sky_island): a ilha não cresce por cima de cidade/vila dos outros (sombra) nem atravessa outro lugar do céu
        if (s.sky) {
            net.minecraft.world.level.levelgen.structure.BoundingBox isle = s.isles ? CityLayout.territory(s, ring) : SkyCity.box(s.heart, ring);
            net.minecraft.world.level.levelgen.structure.BoundingBox shadow = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    isle.minX(), sl.getMinBuildHeight(), isle.minZ(), isle.maxX(), isle.maxY(), isle.maxZ());
            for (com.renanpontes.isekaimahou.core.api.Places.Place p : com.renanpontes.isekaimahou.core.api.Places.all(sl)) {
                if (p.dim() != s.dim || p.id().equals(s.id)) continue;
                boolean town = SettlementService.isCity(p) || "village".equals(p.type());
                if (town && p.box().intersects(shadow)) return SettlementsLang.tr("expand.blocked", p.name());
            }
            if (SkyCity.obstacle(sl, s.heart, ring, false) != null) return SettlementsLang.tr("sky.expand.blocked");
        }
        return null;
    }

    /** Abre o próximo anel de terrenos (o território, a caixa do lugar e a planta crescem). */
    public static boolean expand(ServerPlayer sp, Settlement s) {
        Component pr = expandProblem(sp.server, s);
        if (pr != null) {
            sp.displayClientMessage(pr.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
            return false;
        }
        s.radius++;
        ServerLevel sl = SettlementService.level(sp.server, s);
        if (sl != null) {
            SettlementService.registerPlace(sl, s);
            sl.playSound(null, s.heart, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.0F, 0.8F);
            // onda 7 (sky_island): a Ilha Celeste começa a ganhar o anel novo de terra (os lotes liberam conforme ela aparece)
            SkyCity.radiusChanged(sl, s);
        }
        int size = 2 * s.radius + 1;
        for (ServerPlayer g : CityEconomy.governors(sp.server, s)) {
            com.renanpontes.isekaimahou.core.api.Rewards.banner(g, SettlementsLang.tr("expand.done",
                    SettlementService.cityName(sp.server, s), size, size), s.style.color);
        }
        return true;
    }

    /** Sobe o nível do centro (Coração/Prefeitura): requisitos cumpridos → obra de reforma da praça. */
    public static boolean upgradeCenter(ServerPlayer sp, Settlement s) {
        if (!s.own()) return msg(sp, "plan.own_only");
        if (s.level >= Blueprints.MAX_LEVEL) return msg(sp, "center.max");
        for (Project p : s.projects) if (Project.KIND_CENTER.equals(p.kind)) return msg(sp, "center.busy");
        if (!CityEconomy.centerReady(sp.server, s)) return msg(sp, "center.not_ready");
        if (s.projects.size() >= SettlementService.MAX_QUEUE) return msg(sp, "plan.queue_full", SettlementService.MAX_QUEUE);
        // onda 5: subir de degrau custa o cofre (o requisito já garantiu que há moedas)
        int cost = CityTier.cost(s.level + 1);
        s.treasury = Math.max(0, s.treasury - cost);
        Project p = centerProject(s, s.level + 1);
        s.projects.add(Math.min(s.projects.size(), firstFreeSlot(s)), p);
        ok(sp, s, SettlementsLang.tr("rank.upgrading", CityTier.name(s.level + 1), cost));
        return true;
    }

    public static Project centerProject(Settlement s, int level) {
        Project p = new Project(Blueprint.PLAZA, -1, List.of(new Project.Piece(
                Templates.leveledId(Blueprint.template(s.style, Blueprint.PLAZA), s.style, level), s.heart.getX(), s.heart.getZ(),
                s.plazaRotation(), Project.Piece.BUILDING, s.heart.getY() - 1)));
        p.kind = Project.KIND_CENTER;
        p.level = level;
        p.phase = Project.PHASE_BUILD;
        return p;
    }

    /** Posição logo depois das obras que já estão andando (consertos e o centro passam na frente da fila). */
    private static int firstFreeSlot(Settlement s) {
        return Math.min(s.projects.size(), Math.max(1, s.population(Settlement.Role.BUILDER)));
    }

    private static Project.Piece piece(Settlement s, int cell, net.minecraft.resources.ResourceLocation template) {
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        Integer base = s.plotBase.get(cell);
        return new Project.Piece(template, c.getX(), c.getZ(), CityLayout.facingHeart(cell), Project.Piece.BUILDING,
                base != null ? base : Integer.MIN_VALUE);
    }

    /** Tira o prédio do terreno (efeitos param na hora). Mantém a altura do terreno. */
    static void clearCell(Settlement s, int cell) {
        s.buildings.remove(cell);
        s.bLooks.remove(cell);
        s.bLevels.remove(cell);
        s.bStyles.remove(cell);
        s.damaged.remove(cell);
    }

    /**
     * Baús, barris, fornalhas... com itens dentro do terreno impedem a obra (a limpeza derrubaria os itens no chão).
     * Também recusa com o terreno descarregado (não dá para conferir).
     */
    public static boolean checkContainers(ServerPlayer sp, Settlement s, int cell) {
        ServerLevel sl = SettlementService.level(sp.server, s);
        if (sl == null) return msg(sp, "plan.unloaded");
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        int x0 = c.getX() - PLOT_HALF - 1, x1 = c.getX() + PLOT_HALF + 1, z0 = c.getZ() - PLOT_HALF - 1, z1 = c.getZ() + PLOT_HALF + 1;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                if (!sl.hasChunk(cx, cz)) return msg(sp, "plan.unloaded");
                LevelChunk ch = sl.getChunk(cx, cz);
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (p.getX() < x0 || p.getX() > x1 || p.getZ() < z0 || p.getZ() > z1) continue;
                    if (p.getY() < s.heart.getY() - 16 || p.getY() > s.heart.getY() + 48) continue;
                    if (be instanceof Container ct && !ct.isEmpty()) {
                        return msg(sp, "plan.containers", p.getX(), p.getY(), p.getZ());
                    }
                }
            }
        }
        return true;
    }

    // =====================================================================================
    //  Obras prontas
    // =====================================================================================
    /** Uma obra do planejamento terminou: atualiza o terreno. Devolve false se for uma obra "normal" (prédio novo). */
    public static boolean onDone(ServerLevel sl, Settlement s, Project p) {
        int base = p.pieces.isEmpty() ? Integer.MIN_VALUE : p.pieces.get(0).baseY;
        // onda 8 (city_looks): anota com que geração de visual o prédio ficou (antes de a rua/fachada serem refeitas)
        CityLooks.mark(s, p);
        switch (p.kind) {
            case Project.KIND_TERRAIN -> {
                if (base != Integer.MIN_VALUE) s.plotBase.put(p.cell, base);
                // lote livre sem prédio não existe (não aparece na planta): o chão fica aplainado, o registro some
                if (CityLayout.isFree(p.cell)) s.plotBase.remove(p.cell);
                announce(sl, s, SettlementsLang.tr("plan.done.terrain"));
                return true;
            }
            case Project.KIND_CENTER -> {
                int from = s.level;
                s.level = Math.max(s.level, Math.min(Blueprints.MAX_LEVEL, p.level));
                // onda 5: nome novo no letreiro, anúncio, cerimônia e a reforma da cidade inteira
                CityTier.celebrate(sl, s, from);
                return true;
            }
            case Project.KIND_TIER -> {
                // reforma de muralha pronta: anota o grau/estilo novo (a próxima muralha entra na fila sozinha)
                int k = CityLayout.wallOf(p);
                s.wallLooks.put(k, (p.style.isEmpty() ? s.style.id : p.style) + ":" + p.level);
                announce(sl, s, SettlementsLang.tr("rank.wall.done", SettlementService.roman(k), SettlementsLang.tr("rank.wall." + p.level)));
                return true;
            }
            case Project.KIND_RESTYLE -> {
                // prédio refeito no estilo da cidade: mesmo nível, sem prosperidade nova
                if (p.style.isEmpty() || p.style.equals(s.style.id)) s.bStyles.remove(p.cell);
                else s.bStyles.put(p.cell, p.style);
                if (base != Integer.MIN_VALUE) s.plotBase.put(p.cell, base);
                announce(sl, s, SettlementsLang.tr("reform.b.done", projectName(s, p)));
                return true;
            }
            case Project.KIND_REPAIR -> {
                s.damaged.remove(p.cell);
                announce(sl, s, SettlementsLang.tr("plan.done.repair", projectName(s, p)));
                return true;
            }
            case Project.KIND_UPGRADE -> {
                s.bLevels.put(p.cell, Math.max(1, Math.min(Settlement.MAX_BUILDING_LEVEL, p.level)));
                if (base != Integer.MIN_VALUE) s.plotBase.put(p.cell, base);
                Blueprint bp = s.blueprintAt(p.cell);
                if (bp != null) s.points += bp.points();
                // onda 6: quem anuncia é a inauguração (CityPace.finished), com o "antes → depois" do efeito
                return true;
            }
            default -> {
                if (p.cell >= 0) {
                    // prédio novo = nível 1; prédio mudado de lugar (CityPlots.move) volta no nível que tinha
                    s.bLevels.put(p.cell, Math.max(1, Math.min(Settlement.MAX_BUILDING_LEVEL, p.level)));
                    if (!p.style.isEmpty() && !p.style.equals(s.style.id)) s.bStyles.put(p.cell, p.style);
                    else s.bStyles.remove(p.cell);
                    if (base != Integer.MIN_VALUE) s.plotBase.put(p.cell, base);
                }
                return false;
            }
        }
    }

    private static void announce(ServerLevel sl, Settlement s, Component text) {
        for (ServerPlayer g : CityEconomy.governors(sl.getServer(), s)) g.displayClientMessage(text.copy().withColor(s.style.color), false);
    }

    /** Nome de uma obra para letreiros e telas. */
    public static Component projectName(Settlement s, Project p) {
        if (Project.KIND_TERRAIN.equals(p.kind)) return SettlementsLang.tr("plan.work.terrain");
        if (Project.KIND_CENTER.equals(p.kind)) return SettlementsLang.tr("rank.work.center", CityTier.name(p.level));
        if (Project.KIND_TIER.equals(p.kind)) return SettlementsLang.tr("rank.work.wall", SettlementService.roman(CityLayout.wallOf(p)));
        SettlementStyle st = SettlementStyle.byId(p.style);
        Blueprint bp = Blueprints.get(st == null ? s.style : st, p.key);
        if (bp == null) bp = Blueprints.get(s.style, p.key);
        Component name = bp == null ? Component.literal(p.key) : bp.displayName();
        return switch (p.kind) {
            case Project.KIND_UPGRADE -> SettlementsLang.tr("plan.work.upgrade", name, p.level);
            case Project.KIND_REPAIR -> SettlementsLang.tr("plan.work.repair", name);
            case Project.KIND_RESTYLE -> SettlementsLang.tr(CityLooks.isRedo(s, p) ? "looks.work" : "reform.work", name);
            default -> name;
        };
    }

    // =====================================================================================
    //  Terreno (aviso na tela: íngreme / água)
    // =====================================================================================
    /** 0 = bom, 1 = íngreme (desnível > 5), 2 = água, -1 = não carregado. */
    public static int terrainStatus(ServerLevel sl, Settlement s, int cell) {
        // onda 7 (sky_island): a Ilha Celeste é plana e seca (se a terra do lote já existe: SkyCity.landReady)
        if (s.sky) return 0;
        // onda 8 (water_city): na Cidade das Águas o lote sobre a água é o normal (ganha deque), não um aviso
        if (s.water) return 0;
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        int[][] pts = {{0, 0}, {-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
        for (int[] o : pts) {
            int x = c.getX() + o[0], z = c.getZ() + o[1];
            if (!sl.hasChunk(x >> 4, z >> 4)) return -1;
            int h = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (!sl.getFluidState(new BlockPos(x, h, z)).isEmpty()) wet++;
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (wet >= 2) return 2;
        return max - min > 5 ? 1 : 0;
    }

    private CityPlanning() {}
}
