package com.renanpontes.isekaimahou.feature.legends_b.glyph;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.legends_b.LbFx;
import com.renanpontes.isekaimahou.feature.legends_b.LbUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tempestade de Cortes (VENTO): 6–10 cortes invisíveis em ~1 s no alvo e em quem estiver colado nele — cada corte
 * aparece como um risco branco e deixa marcas nos blocos em volta (rachaduras que somem depois de alguns segundos;
 * com a destruição ligada, grama/flores/folhas são cortadas e metade dos cortes abre um sulco fino e reto no terreno). Os cortes não empurram.
 * Num Domínio (pulso a cada 1 s) vira uma tempestade contínua, com cortes extras rasgando o chão em volta.
 */
public class SlashStormEffect extends EffectGlyph {
    /** Dano de cada corte (antes do poder). */
    private static final float CUT_DAMAGE = 1.5F;
    /** Duração da tempestade em ticks (os cortes se espalham nesse tempo). */
    private static final int SPAN = 20;
    private static final int WHITE = 0xFFFFFF;
    /** Ids das "rachaduras" de bloco (negativos: nunca coincidem com uma entidade). */
    private static final AtomicInteger MARK_IDS = new AtomicInteger(-0x3000000);
    /** Marcas vivas (teto, para Domínios gigantes não encherem o mundo de rachaduras). */
    private static final AtomicInteger ACTIVE_MARKS = new AtomicInteger();
    private static final int MAX_MARKS = 320;

    public SlashStormEffect() {
        super("slash_storm", 24);
        element(Element.WIND);
        tier(4);
        minCircle(3);
    }

    /** Servidor parou: as tarefas agendadas (que apagariam as marcas) somem junto, então o contador zera. */
    public static void resetMarks() {
        ACTIVE_MARKS.set(0);
        TERRAIN_CUTS.set(0);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity victim) || !victim.isAlive()) return;
        ServerLevel sl = ctx.level();
        RandomSource rnd = sl.random;
        int cuts = Mth.clamp(6 + 2 * Math.max(0, stats.amplify) + Math.max(0, stats.split) + Math.max(0, stats.charge), 6, 10);
        float area = stats.radius(1.5F);
        boolean griefing = LbUtil.erasureEnabled(ctx);
        for (int i = 0; i < cuts; i++) {
            int delay = i == 0 ? 0 : Math.max(1, Math.round(i * SPAN / (float) cuts) + rnd.nextInt(2));
            boolean first = i == 0;
            Runnable cut = () -> {
                if (victim.level() != sl) return;
                // O alvo caiu no meio da tempestade: os cortes seguintes vão para quem estava colado nele
                LivingEntity t = first && victim.isAlive() ? victim : pick(ctx, victim, area, rnd);
                if (t != null) cut(ctx, sl, t, stats, griefing);
            };
            if (delay == 0) cut.run();
            else SpellScheduler.schedule(delay, cut);
        }
        // No Domínio: cortes extras rasgando o terreno em volta (puro visual + marcas)
        if (isDomain(ctx)) {
            for (int i = 0; i < 3; i++) {
                SpellScheduler.schedule(2 + rnd.nextInt(SPAN), () -> {
                    if (victim.level() != sl) return;
                    Vec3 around = victim.position().add((rnd.nextDouble() - 0.5) * 10, 0.3 + rnd.nextDouble() * 1.5,
                            (rnd.nextDouble() - 0.5) * 10);
                    ambientCut(ctx, sl, around, griefing);
                });
            }
        }
    }

    /** Sensível: o vento corta as superfícies atingidas. */
    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        ServerLevel sl = ctx.level();
        boolean griefing = LbUtil.erasureEnabled(ctx);
        for (int i = 0; i < 4; i++) {
            SpellScheduler.schedule(1 + i * 4, () -> ambientCut(ctx, sl, hit.getLocation().add(0, 0.3, 0), griefing));
        }
    }

    private static boolean isDomain(SpellContext ctx) {
        String form = ctx.spell().form().id().getPath();
        return form.equals("domain_expansion") || form.equals("simple_domain");
    }

    /** O alvo do próximo corte: o atingido (se vivo) ou alguém (inimigo) colado nele; null se não sobrou ninguém. */
    @Nullable
    private static LivingEntity pick(SpellContext ctx, LivingEntity victim, float area, RandomSource rnd) {
        List<LivingEntity> near = new ArrayList<>();
        if (victim.isAlive()) near.add(victim);
        for (LivingEntity e : ctx.enemiesAround(victim.position(), area)) {
            if (e != victim && e.isAlive() && LbUtil.affects(ctx, e)) near.add(e);
        }
        return near.isEmpty() ? null : near.get(rnd.nextInt(near.size()));
    }

    /** Um corte: risco branco atravessando o corpo, dano sem empurrão, marca num bloco perto. */
    private static void cut(SpellContext ctx, ServerLevel sl, LivingEntity t, SpellStats stats, boolean griefing) {
        RandomSource rnd = sl.random;
        Vec3 c = LbUtil.center(t).add((rnd.nextDouble() - 0.5) * t.getBbWidth() * 0.6, (rnd.nextDouble() - 0.5) * t.getBbHeight() * 0.5,
                (rnd.nextDouble() - 0.5) * t.getBbWidth() * 0.6);
        Vec3 dir = randomCutDir(rnd);
        float len = t.getBbWidth() + 1.6F + rnd.nextFloat();
        LbFx.slash(sl, c, dir, len, WHITE, 6, 0.09F + 0.03F * Math.max(0, stats.amplify));
        LbFx.send(sl, ParticleTypes.SWEEP_ATTACK, c, 1, 0.1, 0);
        LbFx.send(sl, Vfx.crit(), c, 4, 0.25, 0.2);

        // Corte "invisível": sem empurrão (guarda e devolve o movimento) e sem tempo de invulnerabilidade
        Vec3 motion = t.getDeltaMovement();
        t.invulnerableTime = 0;
        ctx.damage(t, CUT_DAMAGE, stats, null);
        if (t.isAlive()) {
            t.setDeltaMovement(motion.scale(0.6));
            t.hurtMarked = true;
        }
        LbFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.5F + rnd.nextFloat() * 0.4F);
        if (rnd.nextInt(3) == 0) LbFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 0.5F, 1.7F);
        if (rnd.nextInt(2) == 0) markNear(ctx, sl, c, dir, griefing);
    }

    /** Corte no ar (Domínio/Sensível): risco + marca no chão mais perto. */
    private static void ambientCut(SpellContext ctx, ServerLevel sl, Vec3 at, boolean griefing) {
        RandomSource rnd = sl.random;
        Vec3 dir = randomCutDir(rnd);
        LbFx.slash(sl, at, dir, 2.0F + rnd.nextFloat() * 1.5F, WHITE, 6, 0.08F);
        LbFx.send(sl, ParticleTypes.SWEEP_ATTACK, at, 1, 0.1, 0);
        LbFx.sound(sl, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.8F);
        markNear(ctx, sl, at, dir, griefing);
    }

    /** Direção de corte: diagonais e horizontais (como golpes de lâmina), raramente vertical. */
    private static Vec3 randomCutDir(RandomSource rnd) {
        double yaw = rnd.nextDouble() * Math.PI * 2;
        double pitch = (rnd.nextDouble() - 0.5) * Math.PI * 0.8;
        return new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
    }

    /**
     * Marca de corte no bloco sólido mais perto do corte (chão embaixo, ou parede ao longo do risco): rachadura que
     * some sozinha em ~6 s. Folhagem no caminho é cortada se a destruição de blocos estiver permitida.
     */
    private static void markNear(SpellContext ctx, ServerLevel sl, Vec3 c, Vec3 dir, boolean griefing) {
        BlockPos target = null;
        Direction face = Direction.UP;
        for (Vec3 probe : new Vec3[]{c.add(dir.scale(1.5)), c.subtract(dir.scale(1.5)), c.add(0, -2.5, 0)}) {
            BlockHitResult bh = LbUtil.clip(sl, c, probe, net.minecraft.world.level.ClipContext.Fluid.NONE, null);
            if (bh.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                target = bh.getBlockPos();
                face = bh.getDirection();
                break;
            }
        }
        if (griefing) cutFoliage(ctx, sl, c, dir);
        if (target == null) return;
        BlockState state = sl.getBlockState(target);
        if (state.isAir()) return;
        Vec3 onFace = Vec3.atCenterOf(target).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.52));
        // Risco desenhado rente à face do bloco
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 along = dir.subtract(n.scale(dir.dot(n)));
        if (along.lengthSqr() < 1.0E-3) along = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        along = along.normalize();
        LbFx.slash(sl, onFace, along, 1.2F, WHITE, 8, 0.06F);
        LbFx.send(sl, new BlockParticleOption(ParticleTypes.BLOCK, state), onFace, 6, 0.2, 0.1);
        // v0.8: o corte entra no terreno — um sulco fino e reto (fila do núcleo; respeita a regra de destruição)
        if (griefing && sl.random.nextInt(2) == 0 && TERRAIN_CUTS.get() < MAX_TERRAIN_CUTS) carve(ctx, sl, onFace, along, n);
        if (ACTIVE_MARKS.get() >= MAX_MARKS) return;
        int id = MARK_IDS.decrementAndGet();
        BlockPos pos = target.immutable();
        ACTIVE_MARKS.incrementAndGet();
        sl.destroyBlockProgress(id, pos, 5 + sl.random.nextInt(3));
        SpellScheduler.schedule(60, () -> sl.destroyBlockProgress(id, pos, 2));
        SpellScheduler.schedule(120, () -> {
            sl.destroyBlockProgress(id, pos, -1);
            ACTIVE_MARKS.decrementAndGet();
        });
    }

    /** Sulcos de terreno em andamento (teto: um Domínio gigante não abre mil valas por segundo). */
    private static final AtomicInteger TERRAIN_CUTS = new AtomicInteger();
    private static final int MAX_TERRAIN_CUTS = 24;

    /** Sulco: linha de ~5 blocos ao longo do risco, 0,6 de largura, entrando ~1,5 bloco na superfície. */
    private static void carve(SpellContext ctx, ServerLevel sl, Vec3 onFace, Vec3 along, Vec3 normal) {
        LivingEntity caster = ctx.caster();
        if (caster != null && caster.level() == sl && caster.position().distanceToSqr(onFace) < 9) return;
        double len = 2.0 + sl.random.nextDouble() * 1.5;
        Vec3 mid = onFace.subtract(normal.scale(0.8));
        Vec3 from = mid.subtract(along.scale(len)), to = mid.add(along.scale(len));
        int queued = Destruction.line(sl, from, to, 0.6F, 1.6F, caster, Destruction.Mode.ERASE);
        if (queued <= 0) return;
        TERRAIN_CUTS.incrementAndGet();
        SpellScheduler.schedule(10, TERRAIN_CUTS::decrementAndGet);
        LbFx.slash(sl, mid, along, (float) len, WHITE, 10, 0.08F);
        LbFx.sound(sl, mid, SoundEvents.STONE_BREAK, 0.7F, 1.6F);
    }

    /** Corta grama/flores/folhas ao longo do risco (sem drops). */
    private static void cutFoliage(SpellContext ctx, ServerLevel sl, Vec3 c, Vec3 dir) {
        for (int i = -2; i <= 2; i++) {
            BlockPos p = BlockPos.containing(c.add(dir.scale(i * 0.6)));
            BlockState s = sl.getBlockState(p);
            if (LbUtil.canCutFoliage(ctx, p, s)) sl.destroyBlock(p, false);
        }
    }
}
