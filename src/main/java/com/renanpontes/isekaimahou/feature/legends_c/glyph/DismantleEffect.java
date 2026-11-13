package com.renanpontes.isekaimahou.feature.legends_c.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Desmantelar (VENTO, t4, C4) — o corte invisível do Rei das Maldições: um corte RETO e LONGO (24 blocos, +50% por
 * Expandir) que passa pelo alvo (ou sai dos seus olhos, com Si Mesmo) e corta tudo no caminho: criaturas (dano +
 * Sangrando 3) e o TERRENO (fatia fina apagada + rachadura no chão, via {@link Destruction} — respeita a regra
 * {@code mahouSpellDestruction}). Dividir: cortes em leque. Colossal: o "Corte que Divide o Mundo" (lâmina de 18+
 * blocos de altura e ~100 de comprimento). Um domínio FECHADO de outro dono no caminho segura o corte na parede (e
 * leva o dano na barreira, pela regra do núcleo).
 */
public class DismantleEffect extends EffectGlyph {
    public static final float BASE_LENGTH = 24F;
    public static final float DAMAGE = 14F;
    public static final float COLOSSAL_DAMAGE = 30F;

    public DismantleEffect() {
        super("dismantle", 22);
        element(Element.WIND);
        tier(4);
        minCircle(4);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        boolean self = LcUtil.selfTarget(ctx, target);
        fan(ctx, stats, self ? null : LcUtil.center(target));
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (LcUtil.selfBlock(ctx, hit)) return;
        fan(ctx, stats, hit.getLocation());
    }

    /** Um ou mais cortes (Dividir = leque de 10° entre eles) passando por {@code through} (ou saindo dos olhos). */
    private void fan(SpellContext ctx, SpellStats stats, @Nullable Vec3 through) {
        boolean colossal = LcUtil.colossal(stats);
        float len = stats.radius(BASE_LENGTH) * (colossal ? 1.6F : 1F);
        float width = colossal ? 1.2F : 0.6F;
        float height = colossal ? 18F + 4F * Math.max(0, stats.aoe - 3) : 3.5F;
        Vec3 eye = ctx.eyePos();
        Vec3 dir = through == null ? ctx.look() : through.subtract(eye);
        dir = dir.lengthSqr() < 1.0E-4 ? ctx.look() : dir.normalize();
        int count = Math.min(7, stats.count());
        for (int i = 0; i < count; i++) {
            float yaw = (i - (count - 1) * 0.5F) * 10F * Mth.DEG_TO_RAD;
            Vec3 d = dir.yRot(yaw);
            Vec3 from;
            if (through == null || eye.distanceTo(through) < len * 0.3F) {
                from = eye.add(d.scale(0.8));
            } else {
                from = through.subtract(d.scale(len * 0.3F));
            }
            cut(ctx, stats, from, d, len, width, height, colossal ? COLOSSAL_DAMAGE : DAMAGE, colossal ? 5 : 3, true);
        }
        if (colossal && ctx.caster() != null) {
            ServerLevel l = ctx.level();
            if (LcUtil.budget(l, ctx.caster(), "world_cut_announce", 1)) {
                com.renanpontes.isekaimahou.core.api.Announce.send(ctx.caster(),
                        net.minecraft.network.chat.Component.translatable("announce.isekai_mahou.legends_c.world_cut"),
                        LcFx.RED, com.renanpontes.isekaimahou.core.api.Announce.STYLE_ULTIMATE);
            }
            LcFx.sound(l, eye, SoundEvents.WARDEN_SONIC_BOOM, 3F, 0.6F);
            com.renanpontes.isekaimahou.core.boss.BossKit.shake(l, eye, 64, 1.6F, 20);
        }
    }

    /**
     * Um corte do Desmantelar de {@code from} na direção {@code dir}. Usado também pelo Santuário Maligno (com
     * {@code damage = 0} = só terreno). Respeita barreiras de domínio no caminho e o orçamento de cortes por tick.
     */
    public static void cut(SpellContext ctx, SpellStats stats, Vec3 from, Vec3 dir, double len, float width, float height,
                           float damage, int bleed, boolean terrain) {
        ServerLevel l = ctx.level();
        LivingEntity caster = ctx.caster();
        if (!LcUtil.budget(l, caster, "dismantle", 12)) return;
        Vec3 to = from.add(dir.scale(len));
        LcUtil.Wall wall = LcUtil.firstWall(l, from, to, caster);
        if (wall != null) {
            to = wall.point();
            float amount = Math.max(4F, damage) * stats.power() * ctx.powerMult();
            LcUtil.strikeWall(l, wall, caster != null ? caster.position() : from, caster, amount);
            l.sendParticles(Vfx.crit(), to.x, to.y, to.z, 16, 0.3, 0.6, 0.3, 0.4);
        }
        Vec3 seg = to.subtract(from);
        double L = seg.length();
        if (L < 0.5) return;
        Vec3 u = seg.scale(1.0 / L);
        Vec3 side = u.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(u).normalize();

        if (damage > 0) {
            AABB box = new AABB(from, to).inflate(height * 0.5 + 1.5);
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
                if (e == caster || ctx.isAlly(e)) continue;
                Vec3 p = LcUtil.center(e);
                double t = Mth.clamp(p.subtract(from).dot(u), 0.0, L);
                Vec3 off = p.subtract(from.add(u.scale(t)));
                if (Math.abs(off.dot(side)) > width * 0.5 + e.getBbWidth() * 0.5 + 0.7) continue;
                if (Math.abs(off.dot(up)) > height * 0.5 + e.getBbHeight() * 0.5) continue;
                // Protegido pela barreira fechada de outro domínio (o conjurador está do outro lado): não leva o corte
                if (LcUtil.behindWall(l, ctx.eyePos(), e, caster)) continue;
                ctx.damage(e, damage, stats, null);
                Statuses.apply(e, Statuses.Kind.BLEEDING, bleed, stats.duration(120), caster, ctx.powerMult());
                l.sendParticles(LcFx.dust(0x9A0A14, 1.4F), p.x, p.y, p.z, 14, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3,
                        e.getBbWidth() * 0.4, 0.1);
            }
        }
        if (terrain) {
            Destruction.line(l, from, to, width, height, caster, Destruction.Mode.ERASE);
            groundCrack(l, from, to, caster);
        }
        LcFx.slash(l, from, to, height, LcFx.SLASH_CORE, height > 8F ? 26 : 14);
        int steps = Math.min(16, (int) (L / 3));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.add(u.scale(L * i / (steps + 1)));
            l.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        Vec3 mid = from.add(u.scale(L * 0.5));
        LcFx.sound(l, mid, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 1.6F);
        LcFx.sound(l, from, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.8F);
    }

    /** Rachadura fina no chão sob o corte (quando o corte passa a até 6 blocos do chão). */
    private static void groundCrack(ServerLevel l, Vec3 from, Vec3 to, @Nullable LivingEntity caster) {
        if (!Destruction.allowed(l, caster)) return;
        Vec3 d = to.subtract(from);
        double len = d.length();
        int n = Math.max(1, Math.min(32, (int) (len / 4)));
        Vec3 prev = null;
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            Vec3 g = LcUtil.ground(l, p, 6);
            Vec3 crack = g == p ? null : new Vec3(g.x, g.y - 0.8, g.z);
            if (prev != null && crack != null && Math.abs(prev.y - crack.y) < 4) {
                Destruction.line(l, prev, crack, 0.5F, 2.0F, caster, Destruction.Mode.ERASE);
            }
            prev = crack;
        }
    }
}
