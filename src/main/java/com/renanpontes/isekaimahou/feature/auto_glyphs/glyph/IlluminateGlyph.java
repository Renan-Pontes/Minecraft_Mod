package com.renanpontes.isekaimahou.feature.auto_glyphs.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.auto_glyphs.Filters;
import com.renanpontes.isekaimahou.feature.auto_glyphs.WorkProbe;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.glyph.AreaEffect;
import com.renanpontes.isekaimahou.feature.automation.glyph.AutoArea;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Iluminar (Arcano t1): coloca tochas nos pontos ESCUROS da área (luz de bloco 0, onde monstros nascem), espaçadas,
 * tirando-as do seu inventário — no pedestal, dos baús encostados nele. Ótimo para deixar fazendas e a Pedreira
 * livres de monstros.
 * <ul>
 *   <li>Até 3 luzes por conjuração (+2 por Amplificar, até 9). Área: raio 6 (Expandir aumenta), 3 blocos acima e abaixo.</li>
 *   <li>Qualquer bloco que brilha serve (tocha, tocha de almas, lanterna, pedra luminosa...). FILTRO do pedestal / mão
 *       secundária: usa só essa luz.</li>
 *   <li>Não põe luz em terra arada, folhas, água nem onde você não pode construir.</li>
 * </ul>
 */
public class IlluminateGlyph extends AreaEffect implements WorkProbe {
    public static final String ID = "illuminate";
    public static final float BASE_RADIUS = 6.0F;
    /** Distância mínima (soma dos eixos) entre duas luzes postas na mesma conjuração. */
    private static final int SPACING = 9;
    private static final int MIN_EMISSION = 10;

    public IlluminateGlyph() {
        super(ID, 4);
        element(Element.ARCANE);
        tier(1);
    }

    @Override
    public float hintRadius() {
        return BASE_RADIUS;
    }

    @Override
    protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
        ServerLevel level = ctx.level();
        int r = AutoArea.radius(stats, BASE_RADIUS);
        List<BlockPos> dark = darkSpots(level, center, r, ctx);
        if (dark.isEmpty()) return;
        int max = Mth.clamp(3 + 2 * Math.max(0, stats.amplify), 1, 9);
        ItemStack filter = Filters.of(ctx, true);
        BlockItem forced = filter.getItem() instanceof BlockItem bi && isLight(bi) ? bi : null;
        List<IItemHandler> sources = AutoArea.sources(ctx, at);
        boolean creative = AutoArea.creative(ctx);
        List<BlockPos> placed = new ArrayList<>();
        boolean noLight = false;
        for (BlockPos p : dark) {
            if (placed.size() >= max) break;
            if (tooClose(p, placed) || !AutoArea.firstTouch(ctx, p)) continue;
            ItemStack light = forced != null
                    ? AutoArea.take(sources, st -> st.is(forced) && stateFor(st, level, p) != null, creative)
                    : AutoArea.take(sources, st -> stateFor(st, level, p) != null, creative);
            if (light.isEmpty()) {
                noLight = true;
                break;
            }
            BlockState state = stateFor(light, level, p);
            if (state == null) continue;
            level.setBlock(p, state, Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.15, 0.2, 0.15, 0.01);
            AutoFx.line(level, Vec3.atCenterOf(center).add(0, 0.8, 0), Vec3.atCenterOf(p), ElementFx.dust(Element.LIGHT, 0.6F), 0.7);
            placed.add(p);
        }
        if (!placed.isEmpty()) {
            Vec3 c = Vec3.atBottomCenterOf(center).add(0, 1.05, 0);
            AutoFx.ring(level, c, r * 0.6, ElementFx.dust(Element.LIGHT, 1.0F), 12 + r * 3);
            AutoFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.6F);
            AutoFx.sound(level, c, SoundEvents.WOOD_PLACE, 0.8F, 1.0F);
        }
        if (noLight && AutoArea.firstThisTick(ctx, "illuminate_none")) {
            AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.auto_glyphs.no_lights").withStyle(ChatFormatting.RED));
        }
    }

    /** Pontos de ar com chão firme e luz de bloco 0, do mais perto do centro ao mais longe. */
    private static List<BlockPos> darkSpots(ServerLevel level, BlockPos center, int r, @Nullable SpellContext ctx) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : AutoArea.disk(center, r, 3, 3)) {
            if (!level.isLoaded(p) || !level.isEmptyBlock(p)) continue;
            if (level.getBrightness(LightLayer.BLOCK, p) > 0) continue;
            BlockPos below = p.below();
            BlockState ground = level.getBlockState(below);
            if (!ground.isFaceSturdy(level, below, Direction.UP) || ground.is(Blocks.FARMLAND) || ground.is(BlockTags.LEAVES)
                    || !ground.getFluidState().isEmpty()) continue;
            if (ctx != null && !AutoArea.mayEdit(ctx, p)) continue;
            out.add(p);
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return out;
    }

    private static boolean tooClose(BlockPos p, List<BlockPos> placed) {
        for (BlockPos o : placed) if (p.distManhattan(o) < SPACING) return true;
        return false;
    }

    private static boolean isLight(BlockItem bi) {
        BlockState s = bi.getBlock().defaultBlockState();
        return s.getLightEmission() >= MIN_EMISSION && !s.hasBlockEntity() && s.getFluidState().isEmpty();
    }

    /** O bloco de luz deste item que fica em pé aqui (null = não serve). */
    @Nullable
    private static BlockState stateFor(ItemStack stack, ServerLevel level, BlockPos p) {
        if (!(stack.getItem() instanceof BlockItem bi) || !isLight(bi)) return null;
        BlockState s = bi.getBlock().defaultBlockState();
        return s.canSurvive(level, p) ? s : null;
    }

    @Override
    public boolean hasWork(ServerLevel level, BlockPos center, @Nullable BlockPos pedestal, SpellStats stats) {
        return !darkSpots(level, center, AutoArea.radius(stats, BASE_RADIUS), null).isEmpty();
    }
}
