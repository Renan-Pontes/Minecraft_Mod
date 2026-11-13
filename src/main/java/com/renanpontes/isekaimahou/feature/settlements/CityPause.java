package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;
import com.renanpontes.isekaimahou.feature.settlements.entity.BuilderNpc;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * PAUSAR E DESISTIR DE OBRAS (onda 7 — "quero poder parar as construções no meio...").
 * <ul>
 *   <li><b>Pausar / retomar</b> ({@link #pause}, {@link #resume}, {@link #pauseAll}, {@link #resumeAll}): qualquer obra
 *       da fila. Pausada ({@link Project#paused}, salvo) ela não recebe dias ({@link CityPace#working} a pula — também
 *       na recuperação de dias com a cidade descarregada), não aceita materiais, marteladas nem magia, não segura
 *       construtor ({@link #crewed}: a equipe vai para as outras obras) e o canteiro fica como está, com a placa
 *       "Pausada · dia X de Y" ({@link #signTitle});</li>
 *   <li><b>Desistir no meio</b> ({@link #cancel}): em qualquer etapa, quando desfazer é seguro ({@link #cancelProblem}
 *       diz o motivo quando não é). Obra que ainda não mexeu em nada só sai da fila. Obra já começada vira
 *       <i>desmonte</i> ({@link Project#undo}): a equipe tira, de trás para a frente e dentro do orçamento de blocos do
 *       {@link ConstructionEngine}, SÓ os blocos que os construtores tinham posto (o bloco tem de ser o do molde — nada
 *       do jogador é apagado). Prédio novo/muralha: o lote volta a ficar livre. Melhoria/reforma de estilo: depois do
 *       desmonte entra sozinho um conserto que recoloca o prédio como era (mesmo nível e estilo);</li>
 *   <li><b>Devolução</b> ({@link #refund}): a parte do custo em moedas proporcional ao que faltava da obra.</li>
 * </ul>
 * Nada aqui cava: numa Ilha Celeste um lote cancelado depois de rebaixado tem o piso refeito ({@link #sealFloor}).
 */
public final class CityPause {
    /** Blocos tirados por ação (5 ticks) em cada canteiro sendo desmontado (ainda dentro do orçamento da cidade). */
    public static final int UNDO_RATE = 24;

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    /** Alguma obra da cidade está pausada? */
    public static boolean any(Settlement s) {
        for (Project p : s.projects) if (p.paused) return true;
        return false;
    }

    /** Há obras e TODAS estão pausadas? */
    public static boolean allPaused(Settlement s) {
        if (s.projects.isEmpty()) return false;
        for (Project p : s.projects) if (!p.paused) return false;
        return true;
    }

    /**
     * As obras que têm equipe agora: as primeiras da fila que não estão pausadas nem esperando a terra do lote, uma
     * por construtor. É a mesma conta do {@link ConstructionEngine}; a tela usa para o "em obra".
     */
    public static List<Project> crewed(Settlement s) {
        int n = Math.min(s.projects.size(), Math.max(1, s.population(Settlement.Role.BUILDER)));
        List<Project> out = new ArrayList<>(n);
        for (Project p : s.projects) {
            if (out.size() >= n) break;
            if (!p.paused && !SkyCity.waitingLand(s, p)) out.add(p);
        }
        return out;
    }

    /** A obra já mexeu no mundo (limpou, fundou ou pôs algum bloco)? */
    public static boolean touched(Project p) {
        if (p.pieceIdx > 0 || p.cursor > 0) return true;
        // degrau, conserto e reforma de muralha nascem na fase dos blocos; as outras, na limpeza
        boolean startsAtBuild = Project.KIND_CENTER.equals(p.kind) || Project.KIND_REPAIR.equals(p.kind) || Project.KIND_TIER.equals(p.kind);
        return p.phase != (startsAtBuild ? Project.PHASE_BUILD : Project.PHASE_CLEAR);
    }

    /** Algum bloco do MOLDE já foi posto (o que o desmonte precisa tirar)? */
    private static boolean placed(Project p) {
        return p.pieceIdx > 0 || p.phase == Project.PHASE_FINISH || p.phase == Project.PHASE_BUILD && p.cursor > 0;
    }

    // =====================================================================================
    //  Pausar e retomar
    // =====================================================================================
    /** Pausa uma obra (falso = já estava pausada ou está sendo desmontada). Quem chama escreve no Diário. */
    public static boolean pause(Project p) {
        if (p.paused || p.undo) return false;
        p.paused = true;
        // a equipe é liberada na próxima ação do coração; nada de impulso de magia guardado
        p.builder = null;
        p.waiting = 0;
        p.accum = 0F;
        p.magicUntil = 0L;
        return true;
    }

    public static boolean resume(Project p) {
        if (!p.paused) return false;
        p.paused = false;
        return true;
    }

    /** Pausa a obra de índice {@code index} da fila e anota no Diário. */
    public static boolean pause(MinecraftServer server, Settlement s, int index) {
        if (index < 0 || index >= s.projects.size()) return false;
        Project p = s.projects.get(index);
        if (!pause(p)) return false;
        CityPace.ensure(server, s, p);
        if (touched(p)) sealFloor(SettlementService.level(server, s), s, p);
        CityPace.diary(server, s, SettlementsLang.tr("pause.diary.pause", CityPlanning.projectName(s, p), CityPace.dayNumber(p), CityPace.totalDays(p)));
        return true;
    }

    public static boolean resume(MinecraftServer server, Settlement s, int index) {
        if (index < 0 || index >= s.projects.size()) return false;
        Project p = s.projects.get(index);
        if (!resume(p)) return false;
        CityPace.diary(server, s, SettlementsLang.tr("pause.diary.resume", CityPlanning.projectName(s, p)));
        return true;
    }

    /** "Pausar tudo": todas as obras da fila (uma linha só no Diário). Devolve quantas pausou. */
    public static int pauseAll(MinecraftServer server, Settlement s) {
        int n = 0;
        ServerLevel sl = s.sky ? SettlementService.level(server, s) : null;
        for (Project p : s.projects) {
            if (!pause(p)) continue;
            n++;
            if (touched(p)) sealFloor(sl, s, p);
        }
        if (n > 0) CityPace.diary(server, s, SettlementsLang.tr("pause.diary.pause_all", n));
        return n;
    }

    public static int resumeAll(MinecraftServer server, Settlement s) {
        int n = 0;
        for (Project p : s.projects) if (resume(p)) n++;
        if (n > 0) CityPace.diary(server, s, SettlementsLang.tr("pause.diary.resume_all", n));
        return n;
    }

    // =====================================================================================
    //  Desistir (cancelar)
    // =====================================================================================
    /**
     * Por que esta obra NÃO pode ser cancelada (null = pode). São os casos em que desfazer não é seguro — só dá para
     * pausar; o texto vai para o botão.
     */
    @Nullable
    public static Component cancelProblem(Settlement s, Project p) {
        if (p.undo) return SettlementsLang.tr("pause.no.undo");
        // a praça da fundação é obrigatória
        if (Blueprint.PLAZA.equals(p.key) && !Project.KIND_CENTER.equals(p.kind)) return SettlementsLang.tr("pause.no.plaza");
        switch (p.kind) {
            case Project.KIND_REPAIR -> {
                return SettlementsLang.tr("pause.no.repair");
            }
            case Project.KIND_TIER -> {
                return SettlementsLang.tr("pause.no.tier");
            }
            case Project.KIND_CENTER -> {
                // praça refeita por uma Reforma de estilo (mesmo degrau): a cidade já mudou de estilo
                if (p.level <= s.level) return SettlementsLang.tr("pause.no.reform");
                // subida de degrau: só enquanto os construtores não mexeram na praça
                return touched(p) ? SettlementsLang.tr("pause.no.center") : null;
            }
            default -> { }
        }
        // prédio mudando de lugar: o antigo já foi (ou está sendo) desmontado e pago
        if (p.moved || p.after != Integer.MIN_VALUE) return SettlementsLang.tr("pause.no.moved");
        if (Project.KIND_TERRAIN.equals(p.kind)) {
            for (Project q : s.projects) if (q != p && q.after == p.cell) return SettlementsLang.tr("pause.no.moved");
        }
        return null;
    }

    /** Moedas que voltam ao cofre se a obra for cancelada agora: o custo pago × a fração que faltava. */
    public static int refund(Settlement s, Project p) {
        int cost = switch (p.kind) {
            case Project.KIND_RESTYLE -> CityReform.buildingCost(p.level, s.level);
            case Project.KIND_CENTER -> p.level > s.level ? CityTier.cost(p.level) : 0;
            default -> 0;
        };
        if (cost <= 0) return 0;
        // obra que ainda não mexeu em nada devolve tudo; depois disso, só a parte que faltava
        return touched(p) ? Math.round(cost * (1F - CityPace.fraction(p))) : cost;
    }

    /** O que o cancelamento vai fazer com esta obra (texto da dica do botão e da confirmação). */
    public static MutableComponent cancelEffect(Settlement s, Project p) {
        if (!touched(p)) return SettlementsLang.tr("pause.cancel.tip.queue");
        if (Project.KIND_TERRAIN.equals(p.kind)) return SettlementsLang.tr("pause.cancel.tip.terrain");
        if (Project.KIND_UPGRADE.equals(p.kind)) return SettlementsLang.tr("pause.cancel.tip.upgrade", Math.max(1, p.level - 1));
        if (Project.KIND_RESTYLE.equals(p.kind)) return SettlementsLang.tr("pause.cancel.tip.restyle", s.styleOf(p.cell).displayName());
        if (Blueprint.WALL.equals(p.key)) return SettlementsLang.tr("pause.cancel.tip.wall");
        return SettlementsLang.tr("pause.cancel.tip.build");
    }

    /**
     * Cancela a obra de índice {@code index}, em qualquer etapa. Devolve o motivo quando não pode (null = cancelada).
     * Não confere quem pediu (isso é de quem chama: as mesmas regras de planejar obras).
     */
    @Nullable
    public static Component cancel(MinecraftServer server, Settlement s, int index) {
        if (index < 0 || index >= s.projects.size()) return SettlementsLang.tr("pause.no.index");
        Project p = s.projects.get(index);
        Component problem = cancelProblem(s, p);
        if (problem != null) return problem;
        CityPace.ensure(server, s, p);
        ServerLevel sl = SettlementService.level(server, s);
        boolean loaded = sl != null && sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4);
        boolean touched = touched(p);
        // obra já começada: o canteiro só é desmontado com a cidade carregada (os blocos precisam ser conferidos)
        if (touched && !loaded) return SettlementsLang.tr("plan.unloaded");
        Component name = CityPlanning.projectName(s, p);
        int back = Math.max(0, Math.min(refund(s, p), Settlement.MAX_TREASURY - s.treasury));
        s.treasury += back;
        p.paused = false;
        p.builder = null;
        p.magicUntil = 0L;
        boolean rebuild = Project.KIND_UPGRADE.equals(p.kind) || Project.KIND_RESTYLE.equals(p.kind);
        if (!touched) {
            s.projects.remove(index);
        } else if (placed(p) && !Project.KIND_TERRAIN.equals(p.kind)) {
            // os construtores desmontam o que puseram (ConstructionEngine chama undoStep); o lote segue "ocupado" até o fim
            ConstructionEngine.dropScaffold(sl, p);
            p.undo = true;
            if (p.phase == Project.PHASE_FINISH) p.cursor = Integer.MAX_VALUE;
            else if (p.phase != Project.PHASE_BUILD) p.cursor = 0;
            p.phase = Project.PHASE_BUILD;
            p.accum = 0F;
            // o desmonte passa na frente: a equipe começa já (leva segundos, não dias)
            s.projects.remove(index);
            s.projects.add(0, p);
        } else {
            // só terreno mexido (limpeza/fundação): nada do molde para tirar
            ConstructionEngine.dropScaffold(sl, p);
            Project back2 = rebuild ? restore(s, p) : null;
            if (back2 != null) s.projects.set(index, back2);
            else s.projects.remove(index);
            sealFloor(sl, s, p);
        }
        CityPace.diary(server, s, back > 0 ? SettlementsLang.tr("pause.diary.cancel.refund", name, back) : SettlementsLang.tr("pause.diary.cancel", name));
        if (loaded) ConstructionEngine.clearSigns(sl, s);
        return null;
    }

    /**
     * O conserto que recoloca um prédio como ele era antes de uma melhoria/reforma de estilo cancelada: mesmo lugar,
     * nível e estilo que a cidade ainda tem anotados (a obra cancelada nunca chegou a gravar o resultado).
     */
    @Nullable
    private static Project restore(Settlement s, Project p) {
        Blueprint bp = p.cell >= 0 ? s.blueprintAt(p.cell) : null;
        Project.Piece old = p.pieces.isEmpty() ? null : p.pieces.get(0);
        if (bp == null || old == null || !old.resolved()) return null;
        int lvl = s.levelOf(p.cell);
        int variant = Math.floorMod(p.cell, Blueprints.variants(bp.key()));
        // onda 8 (city_looks): o prédio de antes volta no visual em que estava (antigo ou novo)
        Project r = new Project(bp.key(), p.cell, List.of(new Project.Piece(CityLooks.standing(s, p.cell, bp), old.cx, old.cz, old.rotation,
                Project.Piece.BUILDING, old.baseY)));
        r.kind = Project.KIND_REPAIR;
        r.level = lvl;
        r.style = s.styleOf(p.cell).id;
        r.phase = Project.PHASE_BUILD;
        r.clearHalf = CityTerrain.LEVEL_HALF;
        return r;
    }

    // =====================================================================================
    //  Desmonte (obra cancelada no meio)
    // =====================================================================================
    /**
     * Um passo do desmonte de uma obra cancelada, chamado pelo {@link ConstructionEngine} no lugar do passo de obra.
     * Tira até {@code max} blocos (de trás para a frente: última peça, último bloco posto) e devolve quantos mudou —
     * o motor desconta do orçamento da cidade. Quando acaba, a obra sai da fila (ou vira o conserto que devolve o
     * prédio de antes). Chunk descarregado: espera.
     */
    public static int undoStep(ServerLevel sl, Settlement s, Project p, List<BuilderNpc> crew, int max) {
        int changed = 0;
        BlockPos last = null;
        for (int guard = 0; guard < max * 8 && changed < max; guard++) {
            if (p.pieceIdx >= p.pieces.size()) {
                p.pieceIdx = p.pieces.size() - 1;
                p.cursor = Integer.MAX_VALUE;
            }
            if (p.pieceIdx < 0) break;
            Project.Piece piece = p.pieces.get(p.pieceIdx);
            Templates.Tpl tpl = piece.resolved() ? Templates.get(sl, piece.template) : null;
            if (tpl == null) p.cursor = 0;
            else p.cursor = Math.min(p.cursor, tpl.entries().size());
            if (p.cursor <= 0) {
                if (tpl != null && piece.kind == Project.Piece.GATE) forgetGate(sl, s, piece, tpl);
                p.pieceIdx--;
                p.cursor = Integer.MAX_VALUE;
                continue;
            }
            BlockPos origin = ConstructionEngine.origin(piece, tpl);
            Templates.Entry e = tpl.entries().get(p.cursor - 1);
            BlockPos pos = origin.offset(tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), piece.rotation));
            if (!sl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) break;
            p.cursor--;
            if (unplace(sl, s, p, piece, e, pos)) {
                changed++;
                last = pos;
            }
        }
        if (p.pieceIdx < 0) {
            finishUndo(sl, s, p);
            return changed;
        }
        // a equipe fica no canteiro, olhando o que está saindo
        Project.Piece piece = p.pieces.get(Math.min(p.pieceIdx, p.pieces.size() - 1));
        if (piece.resolved()) {
            BlockPos site = new BlockPos(piece.cx, piece.baseY + 1, piece.cz);
            for (BuilderNpc b : crew) b.assign(site, last != null ? last : site);
        }
        if (last != null && sl.random.nextInt(3) == 0) {
            sl.playSound(null, last, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.5F, 0.9F + sl.random.nextFloat() * 0.3F);
        }
        return changed;
    }

    /**
     * Desmonta agora, de uma vez, o que falta (comando de teste "terminar tudo"). Devolve true quando a obra cancelada
     * deixou a fila (saiu ou virou o conserto); false = ficou esperando chunk carregar.
     */
    public static boolean undoNow(ServerLevel sl, Settlement s, Project p) {
        for (int guard = 0; guard < 4096 && p.undo && s.projects.contains(p); guard++) {
            int before = p.pieceIdx * 1_000_000 + Math.min(p.cursor, 999_999);
            undoStep(sl, s, p, List.of(), 4096);
            if (!s.projects.contains(p)) return true;
            if (p.pieceIdx * 1_000_000 + Math.min(p.cursor, 999_999) == before) return false;
        }
        return !s.projects.contains(p);
    }

    /**
     * Tira um bloco do molde — só se o que está lá AINDA é o bloco que os construtores puseram. O piso (camada 0 do
     * molde, na altura do chão) nunca vira buraco: num lote de prédio novo volta a ser quintal; no resto fica.
     */
    private static boolean unplace(ServerLevel sl, Settlement s, Project p, Project.Piece piece, Templates.Entry e, BlockPos pos) {
        if (pos.equals(s.heart) || e.state().is(Blocks.STRUCTURE_VOID)) return false;
        BlockState want = e.state().rotate(piece.rotation);
        BlockState cur = sl.getBlockState(pos);
        if (cur.isAir() || !cur.is(want.getBlock())) return false;
        BlockState to = Blocks.AIR.defaultBlockState();
        if (e.pos().getY() <= 0) {
            boolean lot = piece.kind == Project.Piece.BUILDING && p.cell >= 0 && Project.KIND_BUILD.equals(p.kind);
            if (!lot) return false;
            to = CityTerrain.yard(s);
            if (cur.is(to.getBlock())) return false;
        }
        // fonte de água/lava avisa os vizinhos (a água que correu seca); o resto sai sem derrubar o que está encostado
        sl.setBlock(pos, to, cur.getFluidState().isEmpty() ? 2 | 16 : 3);
        if (sl.random.nextInt(4) == 0) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, cur), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    4, 0.25, 0.25, 0.25, 0.05);
        }
        return true;
    }

    /** Portão desmontado: a grade dele deixa de existir para o decreto dos portões (e as barras saem, se estava fechado). */
    private static void forgetGate(ServerLevel sl, Settlement s, Project.Piece piece, Templates.Tpl tpl) {
        BlockPos origin = ConstructionEngine.origin(piece, tpl);
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        BlockState bars = s.style.portcullis();
        s.gateBlocks.removeIf(g -> {
            if (g.getX() < origin.getX() || g.getX() >= origin.getX() + w || g.getZ() < origin.getZ() || g.getZ() >= origin.getZ() + d) return false;
            if (sl.hasChunk(g.getX() >> 4, g.getZ() >> 4) && sl.getBlockState(g).is(bars.getBlock())) {
                sl.setBlock(g, Blocks.AIR.defaultBlockState(), 3);
            }
            return true;
        });
    }

    /** O desmonte acabou: a obra sai da fila; melhoria/reforma cancelada dá lugar ao conserto que devolve o prédio de antes. */
    private static void finishUndo(ServerLevel sl, Settlement s, Project p) {
        MinecraftServer server = sl.getServer();
        ConstructionEngine.dropScaffold(sl, p);
        p.pieceIdx = 0;
        p.cursor = 0;
        int index = s.projects.indexOf(p);
        boolean rebuild = Project.KIND_UPGRADE.equals(p.kind) || Project.KIND_RESTYLE.equals(p.kind);
        Project back = rebuild ? restore(s, p) : null;
        if (index >= 0) {
            if (back != null) s.projects.set(index, back);
            else s.projects.remove(index);
        }
        p.undo = false;
        sealFloor(sl, s, p);
        Component name = CityPlanning.projectName(s, p);
        CityPace.diary(server, s, SettlementsLang.tr(back != null ? "pause.diary.undone.back" : "pause.diary.undone", name));
        Component line = SettlementsLang.tr(back != null ? "pause.undone.back" : "pause.undone", name).withColor(s.style.color);
        for (ServerPlayer g : CityEconomy.governors(server, s)) g.displayClientMessage(line, false);
        ConstructionEngine.clearSigns(sl, s);
        SettlementService.refreshViewers(server, s);
    }

    /**
     * Ilha Celeste: um lote pausado ou cancelado depois de REBAIXADO (altura escolhida abaixo do topo) pode ter ficado
     * sem piso onde a ilha é fina. Refaz o piso do lote nas colunas que têm terra e estão vazias — só ar/mato/fluido é
     * preenchido (é o que a fase de fundação faria), no máximo um lote (17×17) por vez. Cidade no chão: não faz nada.
     */
    private static void sealFloor(@Nullable ServerLevel sl, Settlement s, Project p) {
        if (sl == null || !s.sky || p.pieces.isEmpty()) return;
        Project.Piece piece = p.pieces.get(0);
        if (piece.kind != Project.Piece.BUILDING || !piece.resolved() || piece.baseY >= SkyCity.topY(s)) return;
        int half = Math.max(1, Math.min(CityTerrain.LEVEL_HALF, p.clearHalf));
        BlockState floor = s.style.foundation();
        for (int x = piece.cx - half; x <= piece.cx + half; x++) {
            for (int z = piece.cz - half; z <= piece.cz + half; z++) {
                if (!sl.hasChunk(x >> 4, z >> 4) || !SkyCity.hasLand(s, x, z)) continue;
                BlockPos pos = new BlockPos(x, piece.baseY, z);
                BlockState cur = sl.getBlockState(pos);
                if (!pos.equals(s.heart) && (cur.isAir() || cur.canBeReplaced() || !cur.getFluidState().isEmpty())) sl.setBlock(pos, floor, 2 | 16);
            }
        }
    }

    // =====================================================================================
    //  Ações do painel (quem planeja a cidade: Senhor e conselho)
    // =====================================================================================
    /** {@code work_pause} / {@code work_resume}: {@code arg} = índice da obra na fila, ou "all". */
    public static void handle(ServerPlayer sp, Settlement s, String action, String arg) {
        MinecraftServer server = sp.server;
        boolean all = "all".equals(arg.trim());
        int index = all ? -1 : parse(arg);
        boolean pause = action.equals("work_pause");
        if (all) {
            int n = pause ? pauseAll(server, s) : resumeAll(server, s);
            if (n > 0) say(sp, s, SettlementsLang.tr(pause ? "pause.all.paused" : "pause.all.resumed", n), pause);
        } else if (index >= 0 && index < s.projects.size()) {
            Component name = CityPlanning.projectName(s, s.projects.get(index));
            if (pause ? pause(server, s, index) : resume(server, s, index)) {
                say(sp, s, SettlementsLang.tr(pause ? "pause.paused" : "pause.resumed", name), pause);
            }
        }
        if (pause) Hints.show(sp, "city_pause");
    }

    /** Ação {@code cancel} do painel (já confirmada na tela). */
    public static void cancelFromPanel(ServerPlayer sp, Settlement s, int index) {
        if (index < 0 || index >= s.projects.size()) return;
        Project p = s.projects.get(index);
        Component name = CityPlanning.projectName(s, p);
        boolean dismantle = touched(p) && placed(p) && !Project.KIND_TERRAIN.equals(p.kind);
        int back = cancelProblem(s, p) == null ? refund(s, p) : 0;
        Component problem = cancel(sp.server, s, index);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
            return;
        }
        MutableComponent text = SettlementsLang.tr(dismantle ? "pause.cancelled.undo" : "pause.cancelled", name);
        if (back > 0) text.append(" ").append(SettlementsLang.tr("pause.cancelled.refund", back));
        sp.displayClientMessage(text.withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.8F, 0.9F);
    }

    private static void say(ServerPlayer sp, Settlement s, MutableComponent text, boolean pause) {
        sp.displayClientMessage(text.withColor(s.style.color), true);
        sp.playNotifySound(pause ? SoundEvents.UI_BUTTON_CLICK.value() : SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS,
                0.7F, pause ? 0.8F : 1.0F);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // =====================================================================================
    //  Placa do canteiro e tela
    // =====================================================================================
    /** Título da placa de um canteiro pausado ("Mercado — Pausada · dia 2 de 3") ou sendo desmontado. */
    public static Component signTitle(Settlement s, Project p, Component name) {
        if (p.undo) return SettlementsLang.tr("pause.sign.undo", name);
        return SettlementsLang.tr("pause.sign", name, CityPace.dayNumber(p), CityPace.totalDays(p));
    }

    /**
     * Campos de uma obra para a lista "Obras": pausada, desmontando, se dá para cancelar agora (e o que acontece, quanto
     * volta) ou o motivo de só dar para pausar. Roda DEPOIS do {@link CityPace#viewProject} (corrige "Help"/"Stage").
     */
    public static void viewProject(CompoundTag e, Settlement s, Project p, HolderLookup.Provider reg) {
        Component problem = cancelProblem(s, p);
        e.putBoolean("Cancel", problem == null);
        if (problem != null) {
            e.putString("NoCancel", Component.Serializer.toJson(problem, reg));
        } else {
            e.putString("CancelTip", Component.Serializer.toJson(cancelEffect(s, p), reg));
            e.putInt("Refund", refund(s, p));
        }
        e.putBoolean("CanPause", !p.undo);
        if (p.paused || p.undo) {
            // obra parada não aceita materiais nem conserto pago
            e.putBoolean("Help", false);
            e.putBoolean("Rush", false);
        }
        if (p.paused) e.putBoolean("Paused", true);
        if (p.undo) {
            e.putBoolean("Undo", true);
            e.putBoolean("Started", true);
            e.putString("Stage", Component.Serializer.toJson(SettlementsLang.tr("pause.stage.undo"), reg));
        }
    }

    /** Linhas do {@code /mahou city pause|resume|cancel}: a fila numerada, com o estado de cada obra. */
    public static List<Component> debug(MinecraftServer server, Settlement s) {
        List<Component> out = new ArrayList<>();
        List<Project> crew = crewed(s);
        for (int i = 0; i < s.projects.size(); i++) {
            Project p = s.projects.get(i);
            CityPace.ensure(server, s, p);
            Component problem = cancelProblem(s, p);
            String mark = p.undo ? "✖" : p.paused ? "⏸" : crew.contains(p) ? "▶" : "·";
            out.add(Component.literal("  " + (i + 1) + ". " + mark + " ").append(SettlementsLang.tr("cmd.pause.work", CityPlanning.projectName(s, p),
                    CityPace.dayNumber(p), CityPace.totalDays(p), Math.round(CityPace.fraction(p) * 100),
                    problem == null ? SettlementsLang.tr("cmd.pause.can", refund(s, p)) : problem)));
        }
        if (out.isEmpty()) out.add(SettlementsLang.tr("cmd.pause.none"));
        return out;
    }

    private CityPause() {}
}
