package com.renanpontes.isekaimahou.feature.auto_glyphs.glyph;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.auto_glyphs.Delivery;
import com.renanpontes.isekaimahou.feature.auto_glyphs.Filters;
import com.renanpontes.isekaimahou.feature.auto_glyphs.QuarryData;
import com.renanpontes.isekaimahou.feature.auto_glyphs.WorkMana;
import com.renanpontes.isekaimahou.feature.auto_glyphs.WorkProbe;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.Pedestals;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.glyph.AreaEffect;
import com.renanpontes.isekaimahou.feature.automation.glyph.AutoArea;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pedreira (Arcano t2, Círculo 2): escava uma área QUADRADA camada por camada, "indo descendo".
 * <ul>
 *   <li>Cada conjuração quebra os próximos blocos da camada atual (6 por vez; +6 por Amplificar; teto 48). Quando a
 *       camada esvazia, desce uma (até 4 camadas vazias por conjuração).</li>
 *   <li>Lembra o progresso: por pedestal (posição do pedestal) ou, na mão, pela coluna atingida.</li>
 *   <li>Área: 7×7 (Expandir aumenta, até 25×25). Profundidade: 48 camadas (+32 por Estender; Encurtar: 16;
 *       Persistente: até o fundo do mundo). Para na camada que tem rocha-mãe.</li>
 *   <li>Custo: a magia já cobre ~4 blocos de pedra; o resto paga mana POR BLOCO conforme a dureza
 *       ({@link WorkMana#blockPoints}): do mana das Baterias do pedestal ou do seu.</li>
 *   <li>Nunca quebra: rocha-mãe/inquebráveis, blocos com inventário/dados, áreas protegidas, prédios de cidade, onde
 *       você (ou o dono do pedestal) não pode quebrar. Líquidos da camada são drenados (1 de mana cada); se a água
 *       voltar sem parar (oceano do lado), desce mesmo assim depois de 3 tentativas.</li>
 *   <li>Drops: Coletor Arcano → baús do pedestal → topo do pedestal (na mão: seu inventário). Fortuna/Toque Suave valem.</li>
 *   <li>FILTRO do pedestal (um bloco): só escava ESSE bloco (procura-o descendo). Com Inverter: escava tudo MENOS ele.</li>
 * </ul>
 */
public class QuarryGlyph extends AreaEffect implements WorkProbe {
    public static final String ID = "quarry";
    public static final float BASE_RADIUS = 3.0F;
    /** Pontos de trabalho cobertos pelo custo da magia (~4 pedras). */
    private static final float INCLUDED = 12F;
    private static final float DRAIN_POINTS = 1F;
    private static final int MAX_LAYERS_PER_CAST = 4;
    private static final int STUCK_LIMIT = 3;
    private static final int FX_BLOCKS = 6;

    public QuarryGlyph() {
        super(ID, 10);
        element(Element.ARCANE);
        tier(2);
        minCircle(2);
    }

    @Override
    public float hintRadius() {
        return BASE_RADIUS;
    }

    @Override
    protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
        if (!AutoArea.firstThisTick(ctx, ID)) return; // um passo de pedreira por conjuração
        ServerLevel level = ctx.level();
        RunicPedestalBlockEntity pedestal = Pedestals.of(ctx, at);
        int r = AutoArea.radius(stats, BASE_RADIUS);
        String key = pedestal != null ? QuarryData.pedestalKey(pedestal.getBlockPos()) : QuarryData.handKey(center.getX(), center.getZ(), r);
        QuarryData data = QuarryData.get(level);
        long now = level.getGameTime();
        QuarryData.Quarry q = data.get(key);
        if (q == null || q.cx != center.getX() || q.cz != center.getZ() || q.r != r) {
            q = start(level, center, r, stats);
            data.put(key, q);
        }
        q.last = now;
        data.setDirty();
        if (q.done) {
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 6, 0.2, 0.2, 0.2, 0.01);
            AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.auto_glyphs.quarry_done", q.layer)
                    .withStyle(ChatFormatting.GOLD));
            return;
        }

        ItemStack filter = Filters.of(ctx, false);
        Block only = filter.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
        int maxBlocks = Mth.clamp(Math.round((6 + 6 * Math.max(0, stats.amplify)) * ctx.powerMult()), 2, 48);
        FarmTools tools = new FarmTools(level, stats);
        WorkMana mana = WorkMana.open(ctx, pedestal, INCLUDED);
        Delivery out = Delivery.open(ctx, pedestal, at);
        boolean city = FarmTools.nearCity(level, center);
        Entity caster = ctx.caster();

        List<ItemStack> drops = new ArrayList<>();
        int done = 0, broken = 0, xp = 0, fx = 0;
        boolean noMana = false;
        int startLayer = q.layer;
        for (int pass = 0; pass < MAX_LAYERS_PER_CAST && !q.done && !noMana; pass++) {
            int y = q.layer;
            boolean left = false, blocked = false, sources = false;
            for (int dx = -q.r; dx <= q.r && !noMana; dx++) {
                for (int dz = -q.r; dz <= q.r; dz++) {
                    BlockPos p = new BlockPos(q.cx + dx, y, q.cz + dz);
                    if (!level.isLoaded(p)) {
                        blocked = true;
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    if (s.is(Blocks.BEDROCK)) {
                        q.bedrock = true;
                        continue;
                    }
                    if (s.getBlock() instanceof LiquidBlock) {
                        boolean source = s.getFluidState().isSource();
                        if (done >= maxBlocks) {
                            if (source) sources = true;
                            continue;
                        }
                        if (!AutoArea.mayEdit(ctx, p) || FarmTools.untouchable(level, p, s, caster, city)) continue;
                        if (!mana.pay(DRAIN_POINTS)) {
                            noMana = true;
                            left = true;
                            break;
                        }
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        if (source) sources = true; // pode voltar: confere de novo na próxima
                        done++;
                        continue;
                    }
                    if (only != null && s.is(only) == stats.inverted) continue;
                    if (FarmTools.untouchable(level, p, s, caster, city)) continue;
                    ItemStack tool = tools.forState(s);
                    if (tool == null) continue;
                    if (done >= maxBlocks) {
                        left = true;
                        continue;
                    }
                    if (!AutoArea.mayBreak(ctx, p, s)) continue;
                    if (!mana.pay(WorkMana.blockPoints(s.getDestroySpeed(level, p)))) {
                        noMana = true;
                        left = true;
                        break;
                    }
                    drops.addAll(Block.getDrops(s, level, p, null, caster, tool));
                    xp += s.getExpDrop(level, p, null, caster, tool);
                    if (fx++ < FX_BLOCKS) level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(s));
                    level.removeBlock(p, false);
                    done++;
                    broken++;
                }
            }
            if (blocked || left) break; // parte da área descarregada, ou ainda há o que quebrar nesta camada
            if (sources && ++q.stuck < STUCK_LIMIT) break; // a água pode ter voltado: tenta de novo
            // Camada limpa: desce
            q.stuck = 0;
            if (q.bedrock || q.layer - 1 < q.bottom) {
                q.done = true;
                break;
            }
            q.layer--;
            if (done >= maxBlocks) break;
        }
        int extra = mana.commit();
        q.mined += broken;
        out.give(drops);
        out.xp(xp);

        Vec3 layerCenter = new Vec3(q.cx + 0.5, startLayer + 0.6, q.cz + 0.5);
        if (done > 0) {
            AutoFx.ring(level, layerCenter, q.r + 0.4, ElementFx.dust(ctx.element(), 1.0F), 12 + q.r * 6);
            level.sendParticles(ParticleTypes.CLOUD, layerCenter.x, layerCenter.y, layerCenter.z, 4 + broken / 2, q.r * 0.5, 0.2, q.r * 0.5, 0.01);
            AutoFx.sound(level, layerCenter, SoundEvents.STONE_BREAK, 0.9F, 0.8F);
            out.trail(ctx, layerCenter);
        }
        if (q.done) {
            AutoFx.sound(level, layerCenter, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.7F);
            AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.auto_glyphs.quarry_done", q.layer)
                    .withStyle(ChatFormatting.GOLD));
        } else if (noMana && broken == 0) {
            AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.auto_glyphs.no_work_mana").withStyle(ChatFormatting.RED));
        } else {
            AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.auto_glyphs.quarry_progress", q.layer, broken, extra)
                    .withColor(ctx.element().color));
        }
    }

    /** Começa uma pedreira: a camada inicial é o bloco atingido (ou a de baixo, se ele é ar/planta/pedestal). */
    private static QuarryData.Quarry start(ServerLevel level, BlockPos center, int r, SpellStats stats) {
        QuarryData.Quarry q = new QuarryData.Quarry();
        q.cx = center.getX();
        q.cz = center.getZ();
        q.r = r;
        BlockState cs = level.getBlockState(center);
        q.top = cs.isAir() || cs.canBeReplaced() || cs.hasBlockEntity() ? center.getY() - 1 : center.getY();
        q.layer = q.top;
        int depth = stats.persistent ? 4096 : stats.duration < 0 ? 16 : 48 + 32 * stats.duration;
        q.bottom = Math.max(level.getMinBuildHeight(), q.top - depth + 1);
        return q;
    }

    /** Há trabalho: a pedreira deste pedestal ainda não chegou ao fundo (na mão: sempre). */
    @Override
    public boolean hasWork(ServerLevel level, BlockPos center, @Nullable BlockPos pedestal, SpellStats stats) {
        if (pedestal == null) return true;
        QuarryData.Quarry q = QuarryData.get(level).get(QuarryData.pedestalKey(pedestal));
        return q == null || !q.done;
    }
}
