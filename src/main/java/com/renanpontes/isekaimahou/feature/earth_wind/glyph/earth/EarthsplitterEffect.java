package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwTasks;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.TempBlocks;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fenda (lendario): a terra se abre numa fissura em linha reta na direcao do alvo (16 blocos, 3 de largura,
 * 5 de fundo). Quem esta no caminho leva dano, fica Atordoado e cai no abismo. Depois de 6 s a fenda se fecha
 * na mesma ordem em que abriu, ESMAGANDO quem ficou la dentro (e cuspindo de volta a superficie).
 * Os blocos voltam exatamente como eram.
 */
public class EarthsplitterEffect extends EffectGlyph {
    public EarthsplitterEffect() {
        super("earthsplitter", 65);
        element(Element.EARTH);
        legendary();
        minCircle(8);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        split(ctx, target == ctx.caster() ? null : target.position(), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        split(ctx, hit.getLocation(), stats);
    }

    private static void split(SpellContext ctx, Vec3 aim, SpellStats stats) {
        if (!EwUtil.once(ctx, "earthsplitter")) return;
        ServerLevel level = ctx.level();
        LivingEntity caster = ctx.caster();
        int length = Math.round(16 * (1 + 0.25F * Math.max(0, stats.aoe)));
        Vec3 origin;
        Vec3 dir;
        if (caster != null) {
            origin = caster.position();
            dir = EwUtil.horizontal(aim != null ? aim.subtract(origin) : ctx.look());
            if (dir.lengthSqr() < 1.0E-4) dir = EwUtil.horizontal(ctx.look());
            // alvo muito longe (projetil): a fenda passa pelo alvo em vez de sair dos seus pes
            if (aim != null && aim.distanceTo(origin) > length * 0.75) origin = aim.subtract(dir.normalize().scale(length * 0.5));
        } else {
            origin = aim != null ? aim : Vec3.ZERO;
            dir = EwUtil.horizontal(ctx.look());
        }
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        final Vec3 forward = dir.normalize();
        final Vec3 side = new Vec3(-forward.z, 0, forward.x);
        final Vec3 start = origin;
        final int halfWidth = stats.concentrate ? 0 : 1;
        final int depth = 5 + Math.max(0, stats.amplify);
        final int open = stats.duration(120);
        final Set<UUID> hit = new HashSet<>();
        final double startY = origin.y;

        EwFx.sound(level, origin, SoundEvents.WARDEN_EMERGE, 1.5F, 0.5F);
        // v1.3: a terra treme e racha numa linha de impactos de pedra ao longo da fenda
        com.renanpontes.isekaimahou.core.ElementFx.shake(level, origin, 48, 1.8F, 40);
        for (int i = 0; i <= 4; i++) {
            Vec3 p = start.add(forward.scale(length * i / 4.0));
            com.renanpontes.isekaimahou.core.ElementFx.impactFx(level, p.add(0, 0.2, 0), Element.EARTH, ctx.color(), i == 0 ? 4 : 3, null, 1.4F);
        }
        EwFx.sound(level, origin, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.4F);
        EwUtil.shake(level, origin, 32, 1.6F, 40);

        EwTasks.repeat(length, step -> {
            Vec3 p = start.add(forward.scale(2 + step));
            BlockPos surface = EwUtil.groundBelow(level, BlockPos.containing(p.x, startY + 3, p.z), 8);
            if (surface == null) return true;
            int minY = surface.getY() + 1;
            int maxY = minY;
            int carved = 0;
            for (int w = -halfWidth; w <= halfWidth; w++) {
                Vec3 q = p.add(side.scale(w));
                BlockPos top = EwUtil.groundBelow(level, BlockPos.containing(q.x, surface.getY() + 2, q.z), 5);
                if (top == null) continue;
                int column = 0;
                for (int d = 0; d < depth; d++) {
                    BlockPos pos = top.below(d);
                    if (!canSplit(level, pos) || !EwUtil.canBreak(ctx, pos, level.getBlockState(pos))) break;
                    if (TempBlocks.carve(level, pos, open)) {
                        column++;
                        minY = Math.min(minY, pos.getY());
                    }
                }
                if (column == 0) continue;
                carved += column;
                // mato/flores em cima cairiam no buraco e virariam item: somem junto e voltam depois
                BlockPos above = top.above();
                BlockState plant = level.getBlockState(above);
                if (!plant.isAir() && plant.canBeReplaced() && plant.getFluidState().isEmpty() && !plant.hasBlockEntity()) {
                    TempBlocks.carve(level, above, open);
                }
                maxY = Math.max(maxY, top.getY() + 1);
            }
            // quem esta no caminho leva dano, fica atordoado e cai
            Vec3 surf = new Vec3(p.x, surface.getY() + 1.0, p.z);
            for (LivingEntity e : ctx.enemiesAround(surf, 2.2)) {
                if (!hit.add(e.getUUID())) continue;
                ctx.damage(e, 12.0F, stats, null);
                ctx.status(e, CoreRegistries.STUNNED, 40, 0, stats);
                EwUtil.push(e, forward.scale(0.1), 0.25);
            }
            BlockState gs = EwFx.groundState(level, surface.below(depth));
            EwFx.dustPillar(level, surf, gs, 14);
            EwFx.debris(level, surf.add(0, 0.5, 0), gs, 18, 0.8);
            level.sendParticles(Vfx.largeSmoke(), surf.x, surf.y, surf.z, 4, 0.6, 0.2, 0.6, 0.02);
            if (step % 3 == 0) {
                EwFx.sound(level, surf, SoundEvents.DEEPSLATE_BREAK, 1.5F, 0.5F);
                EwFx.sound(level, surf, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 0.5F + level.random.nextFloat() * 0.2F);
            }
            // fechamento: esmaga quem ficou dentro deste trecho um pouco antes dos blocos voltarem
            if (carved == 0) return true;
            final AABB gap = new AABB(p.x - 1.6, minY, p.z - 1.6, p.x + 1.6, maxY, p.z + 1.6);
            final int crushStep = step;
            EwTasks.later(Math.max(1, open - 2), () -> crush(ctx, level, gap, stats, crushStep));
            return true;
        });
    }

    private static void crush(SpellContext ctx, ServerLevel level, AABB gap, SpellStats stats, int step) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, gap, e -> e.isAlive() && !ctx.isAlly(e))) {
            ctx.damage(e, 10.0F, stats, null);
            ctx.status(e, CoreRegistries.ROOTED, 30, 0, stats);
        }
        Vec3 c = gap.getCenter();
        if (step % 2 == 0) {
            EwFx.sound(level, c, SoundEvents.DEEPSLATE_BREAK, 1.4F, 0.4F);
            EwFx.debris(level, new Vec3(c.x, gap.maxY, c.z), EwFx.groundState(level, BlockPos.containing(c.x, gap.minY, c.z)), 12, 0.8);
        }
        if (step == 0) EwUtil.shake(level, c, 28, 1.0F, 25);
    }

    private static boolean canSplit(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) return false;
        float hardness = s.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness < 50.0F;
    }
}
