package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;
import com.renanpontes.isekaimahou.feature.settlements.build.TierLook;
import com.renanpontes.isekaimahou.feature.settlements.build.TierWalls;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Onda 8 (city_looks) — "tem que ter mudanças mais significativas visuais do lvl 1 pro 2... até o 5": a ponte entre a
 * cidade e o visual novo dos prédios ({@link LookBuildings}).
 * <ul>
 *   <li><b>Geração do visual por lote</b> ({@link Settlement#bLooks}): prédio de mundo antigo continua com o molde em que
 *       foi erguido (consertos e a reforma da rua usam {@link #standing}); toda obra nova — construir, melhorar, reformar
 *       de estilo, mudar de lugar — já sai no visual novo ({@link #mark} anota quando ela termina).</li>
 *   <li><b>"Refazer no visual novo"</b> ({@link #redo}): obra barata do planejador para quem não quer esperar a próxima
 *       melhoria. Mesmas regras de uma melhoria (baús vazios, lote sem obra); nunca roda sozinha.</li>
 *   <li><b>Vitrine</b> ({@code /mahou city showcase}): ergue os níveis lado a lado num terreno de teste.</li>
 * </ul>
 */
public final class CityLooks {

    // =====================================================================================
    //  Geração do visual de cada lote
    // =====================================================================================
    /** O prédio do lote ainda está no molde de antes da onda 8 (e a planta tem visual novo)? */
    public static boolean old(Settlement s, int cell) {
        String key = s.buildings.get(cell);
        return key != null && cell != CityLayout.CENTER_CELL && s.bLooks.getOrDefault(cell, 0) <= 0 && LookBuildings.supports(key);
    }

    /** Molde do prédio que ESTÁ de pé no lote (o visual em que foi erguido, com a variante que foi usada). */
    public static ResourceLocation standing(Settlement s, int cell, Blueprint bp) {
        int stored = s.bLooks.getOrDefault(cell, 0);
        int level = s.levelOf(cell);
        if (stored <= 0) return bp.template(Math.floorMod(cell, Blueprints.variants(bp.key())), level, Blueprint.LOOK_OLD);
        return bp.template(stored >> 4, level, Blueprint.LOOK_NEW);
    }

    /** Uma obra de prédio terminou: anota a geração do visual (e a variante) com que ele ficou. Conserto não muda nada. */
    public static void mark(Settlement s, Project p) {
        if (p.cell < 0 || p.pieces.isEmpty()) return;
        if (!(Project.KIND_BUILD.equals(p.kind) || Project.KIND_UPGRADE.equals(p.kind) || Project.KIND_RESTYLE.equals(p.kind))) return;
        ResourceLocation id = p.pieces.get(0).template;
        if (!LookBuildings.isLook(id)) {
            s.bLooks.remove(p.cell);
            return;
        }
        int variant = 0;
        try {
            String path = id.getPath();
            variant = Math.max(0, Integer.parseInt(path.substring(path.lastIndexOf('/') + 1)));
        } catch (RuntimeException e) {
            // id estranho: variante 0
        }
        s.bLooks.put(p.cell, Blueprint.LOOK_NEW | variant << 4);
    }

    /** Quantos prédios da cidade ainda estão no visual antigo. */
    public static int pending(Settlement s) {
        int n = 0;
        for (int cell : s.buildings.keySet()) if (old(s, cell)) n++;
        return n;
    }

    /** Custo (moedas do cofre) de refazer um prédio no visual novo: o mesmo de refazê-lo no estilo da cidade. */
    public static int cost(Settlement s, int cell) {
        return CityReform.buildingCost(s.levelOf(cell), s.level);
    }

    // =====================================================================================
    //  "Refazer no visual novo"
    // =====================================================================================
    private static boolean no(ServerPlayer sp, String key, Object... args) {
        sp.displayClientMessage(SettlementsLang.tr(key, args).withColor(0xE8735E), true);
        sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
        return false;
    }

    /**
     * Refaz o prédio do lote no visual novo, no MESMO nível e estilo. É uma obra como a de reforma de estilo (limpa o
     * lote e reconstrói; pode ser pausada/cancelada com devolução), por isso exige os baús do lote vazios.
     *
     * @param free comando de teste: sem custo
     */
    public static boolean redo(ServerPlayer sp, Settlement s, int cell, boolean free) {
        if (!s.own()) return no(sp, "plan.own_only");
        String where = CityPlots.cellProblem(s, cell, cell);
        if (where != null) return no(sp, where, "plan.locked".equals(where) ? CityLayout.unlockLevel(cell) : SettlementService.MAX_QUEUE);
        if (s.projectAt(cell) != null) return no(sp, "plan.busy");
        if (s.projects.size() >= SettlementService.MAX_QUEUE) return no(sp, "plan.queue_full", SettlementService.MAX_QUEUE);
        Blueprint bp = s.blueprintAt(cell);
        if (bp == null) return no(sp, "plan.empty");
        if (!old(s, cell)) return no(sp, "looks.redo.same");
        if (s.damaged.contains(cell)) return no(sp, "plan.repair_first");
        int lvl = s.levelOf(cell);
        int cost = free ? 0 : cost(s, cell);
        if (s.treasury < cost) return no(sp, "reform.poor", cost, s.treasury);
        if (!CityPlanning.checkContainers(sp, s, cell)) return false;
        s.treasury -= cost;
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        Integer base = s.plotBase.get(cell);
        int variant = Math.floorMod(cell, Blueprints.variants(bp.key()));
        Project p = new Project(bp.key(), cell, List.of(new Project.Piece(bp.template(variant, lvl), c.getX(), c.getZ(),
                CityLayout.facingHeart(cell), Project.Piece.BUILDING, base != null ? base : Integer.MIN_VALUE)));
        // mesma obra da reforma de estilo, mas o estilo é o que o prédio já tem (só o visual muda)
        p.kind = Project.KIND_RESTYLE;
        p.level = lvl;
        p.style = s.styleOf(cell).id;
        p.clearHalf = CityTerrain.LEVEL_HALF;
        s.projects.add(p);
        sp.displayClientMessage(SettlementsLang.tr("looks.redo.queued", bp.displayName()).withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** A obra é um "Refazer no visual novo" (reforma no estilo que o prédio já tem)? */
    public static boolean isRedo(Settlement s, Project p) {
        return Project.KIND_RESTYLE.equals(p.kind) && p.cell >= 0 && s.buildings.containsKey(p.cell) && p.style.equals(s.styleOf(p.cell).id);
    }

    // =====================================================================================
    //  Vitrine de teste: /mahou city showcase <prédio|wall|clear> [estilo]
    // =====================================================================================
    /** Blocos postos pela última vitrine (só em memória: {@code showcase clear} tira o que ainda for o bloco posto). */
    private static final List<BlockPos> SHOWN = new ArrayList<>();
    private static final List<BlockState> SHOWN_STATE = new ArrayList<>();
    @Nullable private static ServerLevel shownIn;

    public static LiteralArgumentBuilder<CommandSourceStack> showcase() {
        return Commands.literal("showcase")
                .then(Commands.literal("clear").executes(CityLooks::clearShow))
                .then(Commands.argument("key", StringArgumentType.word()).executes(c -> show(c, null))
                        .then(Commands.argument("style", StringArgumentType.word()).executes(c -> show(c, StringArgumentType.getString(c, "style")))));
    }

    /** {@code /mahou city looks} (quantos prédios estão no visual antigo) e {@code looks redo} (refaz o do lote onde você está, sem custo). */
    public static LiteralArgumentBuilder<CommandSourceStack> looks() {
        return Commands.literal("looks").executes(c -> {
            Settlement s = city(c);
            if (s == null) return 0;
            int n = pending(s);
            c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.looks.info", s.buildings.size(), n), false);
            return 1;
        }).then(Commands.literal("redo").executes(c -> {
            Settlement s = city(c);
            if (s == null) return 0;
            ServerPlayer p = c.getSource().getPlayerOrException();
            int cell = CityPlots.plotAt(s, p.getBlockX(), p.getBlockZ());
            if (!CityLayout.isFree(cell)) {
                int dx = CityLayout.indexAt(s, p.getBlockX() - s.heart.getX()), dz = CityLayout.indexAt(s, p.getBlockZ() - s.heart.getZ());
                cell = dx == Integer.MIN_VALUE || dz == Integer.MIN_VALUE ? -1 : CityLayout.cellAt(s, dx, dz);
            }
            boolean ok = cell >= 0 && redo(p, s, cell, true);
            if (ok) SettlementService.refreshViewers(p.server, s);
            return ok ? 1 : 0;
        }));
    }

    @Nullable
    private static Settlement city(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Places.Place at = SettlementService.cityAt(p.serverLevel(), p.blockPosition());
        Settlement s = at == null ? null : SettlementService.city(p.server, at.id());
        if (s == null || !s.own()) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.founded_only"));
            return null;
        }
        return s;
    }

    private static int clearShow(CommandContext<CommandSourceStack> c) {
        int n = wipe();
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.showcase.cleared", n), false);
        return 1;
    }

    private static int wipe() {
        int n = 0;
        if (shownIn != null) {
            for (int i = SHOWN.size() - 1; i >= 0; i--) {
                BlockPos pos = SHOWN.get(i);
                if (shownIn.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && shownIn.getBlockState(pos) == SHOWN_STATE.get(i)) {
                    shownIn.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    n++;
                }
            }
        }
        SHOWN.clear();
        SHOWN_STATE.clear();
        shownIn = null;
        return n;
    }

    private static int show(CommandContext<CommandSourceStack> c, @Nullable String styleId) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel sl = p.serverLevel();
        String key = StringArgumentType.getString(c, "key");
        SettlementStyle st = styleId == null ? null : SettlementStyle.byId(styleId);
        if (st == null) {
            Places.Place at = SettlementService.cityAt(sl, p.blockPosition());
            Settlement s = at == null ? null : SettlementService.city(p.server, at.id());
            st = s != null ? s.style : SettlementStyle.KINGDOM;
        }
        boolean wall = Blueprint.WALL.equals(key);
        Blueprint bp = Blueprints.get(st, key);
        if (bp == null && !wall) {
            // maravilha de outro estilo: procura a planta em qualquer estilo
            for (SettlementStyle o : SettlementStyle.values()) {
                if (Blueprints.get(o, key) != null) {
                    st = o;
                    bp = Blueprints.get(o, key);
                    break;
                }
            }
        }
        if (!wall && (bp == null || !bp.usesCell())) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.showcase.unknown", key));
            return 0;
        }
        // terreno de teste: nunca dentro de uma cidade/lugar protegido nem no Lar
        int span = wall ? 44 : 5 * 15;
        BlockPos start = p.blockPosition().offset(2, -1, 3);
        if ("home_realm".equals(sl.dimension().location().getPath()) || Places.at(sl, start) != null
                || Places.at(sl, start.offset(span, 0, 14)) != null || Places.at(sl, start.offset(span / 2, 0, 7)) != null) {
            c.getSource().sendFailure(SettlementsLang.tr("cmd.showcase.where"));
            return 0;
        }
        wipe();
        shownIn = sl;
        int placed = 0;
        if (wall) {
            // 4 graus de muralha, um atrás do outro: torre, trecho, portão, trecho, torre
            for (int wt = 1; wt <= TierLook.WALL_MAX; wt++) {
                int z = start.getZ() + (wt - 1) * 9;
                int[] xs = {0, 5, 14, 21, 30};
                String[] pieces = {TierWalls.TOWER, TierWalls.SEGMENT, TierWalls.GATE, TierWalls.SEGMENT, TierWalls.TOWER};
                for (int i = 0; i < pieces.length; i++) {
                    Templates.Tpl t = TierWalls.build(st, wt, pieces[i]);
                    placed += put(sl, t, start.getX() + xs[i], start.getY(), z + (5 - t.sz()) / 2);
                }
            }
        } else {
            int max = CityMonuments.maxLevel(key);
            for (int lv = 1; lv <= max; lv++) {
                Templates.Tpl t = Templates.get(sl, bp.template(0, lv));
                if (t != null) placed += put(sl, t, start.getX() + (lv - 1) * 15, start.getY(), start.getZ());
            }
        }
        int n = placed;
        Component what = wall ? SettlementsLang.tr("bp." + st.id + ".wall") : bp.displayName();
        c.getSource().sendSuccess(() -> SettlementsLang.tr("cmd.showcase.done", what, n), false);
        return 1;
    }

    /**
     * Põe um molde no mundo (canto mínimo em x, z; chão em y). Só troca ar, mato, terreno natural, troncos e folhas —
     * bloco que alguém pôs fica; cada bloco posto é anotado para o {@code showcase clear}.
     */
    private static int put(ServerLevel sl, Templates.Tpl t, int x0, int y0, int z0) {
        BlockState found = SettlementStyle.KINGDOM.foundation();
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // limpa o que for natural dentro da caixa do molde (árvores, morros) e depois põe os blocos
        for (int x = 0; x < t.sx(); x++) {
            for (int z = 0; z < t.sz(); z++) {
                if (!sl.hasChunk((x0 + x) >> 4, (z0 + z) >> 4)) continue;
                for (int y = 1; y < t.sy() + 2; y++) {
                    BlockState cur = sl.getBlockState(m.set(x0 + x, y0 + y, z0 + z));
                    if (!cur.isAir() && natural(cur, found)) sl.setBlock(m, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        for (Templates.Entry e : t.entries()) {
            BlockPos pos = new BlockPos(x0 + e.pos().getX(), y0 + e.pos().getY(), z0 + e.pos().getZ());
            if (!sl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || e.state().is(Blocks.STRUCTURE_VOID)) continue;
            BlockState cur = sl.getBlockState(pos);
            if (!cur.isAir() && !natural(cur, found)) continue;
            sl.setBlock(pos, e.state(), 2 | 16);
            SHOWN.add(pos);
            SHOWN_STATE.add(e.state());
            n++;
        }
        return n;
    }

    private static boolean natural(BlockState st, BlockState found) {
        return CityTerrain.ground(st, found) || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES);
    }

    private CityLooks() {}
}
