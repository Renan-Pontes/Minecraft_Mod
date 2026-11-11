package com.renanpontes.isekaimahou.feature.legends_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_a.LegendFx;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ConjuredBladeEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Efeito "Arma Conjurada" (Arcano, tier 3): uma arma (espada, lança, machado...) surge no ar, do lado de onde a
 * magia veio, e voa no alvo: 7 de dano, perfura mais 1 inimigo (+1 por Perfurar) e fica cravada no chão.
 * Dividir: +1 arma por alvo. Disparada por um Portão do Arsenal, a própria arma do portão é a lâmina: o dano é direto.
 * Com Sensível, crava armas no bloco atingido (o caminho delas fere quem estiver nele).
 */
public class ConjuredWeaponEffect extends EffectGlyph {
    public static final float DAMAGE = 7.0F;

    public ConjuredWeaponEffect() {
        super("conjured_weapon", 14);
        element(Element.ARCANE);
        // v1.0: o caminho das lâminas é do Ferreiro (glifo de origem: qualquer elemento; outras classes só por Lição)
        origin(MagicPath.SMITH);
        tier(3);
        minCircle(3);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        ServerLevel sl = ctx.level();
        // Dentro de um Portão do Arsenal a arma do portão JÁ é a lâmina conjurada: golpe direto (sem invocar outra
        // por cima). A checagem pela forma cobre também Atraso/Eco no efeito (rodam depois, fora da lâmina).
        if (ConjuredBladeEntity.isCarrierHit() || ctx.spell().form() instanceof ArsenalGateForm) {
            ctx.damage(target, DAMAGE, stats, ConjuredBladeEntity.currentCarrier());
            Vec3 c = LegendFx.center(target);
            sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0.25);
            return;
        }
        Vec3 tc = LegendFx.center(target);
        Vec3 src = ctx.eyePos();
        Vec3 back = new Vec3(tc.x - src.x, 0, tc.z - src.z);
        if (back.lengthSqr() < 1.0E-3) back = new Vec3(ctx.look().x, 0, ctx.look().z);
        if (back.lengthSqr() < 1.0E-3) back = new Vec3(0, 0, 1);
        back = back.normalize();
        Vec3 right = new Vec3(-back.z, 0, back.x);
        int n = stats.count();
        int color = LegendFx.colorOf(ctx, LegendFx.GOLD);
        boolean capped = ConjuredBladeEntity.countFor(sl, ctx.casterId()) + n > ConjuredBladeEntity.MAX_PER_OWNER;
        for (int i = 0; i < n; i++) {
            double side = (i - (n - 1) / 2.0) * 1.6;
            Vec3 from = clearSpawn(sl, tc, tc.subtract(back.scale(5.0 + i * 0.8)).add(right.scale(side)).add(0, 3.2 + i * 0.4, 0));
            Vec3 dir = tc.subtract(from);
            if (capped) {
                // Limite de lâminas no mundo: o golpe acontece sem a entidade (só o rastro)
                ctx.damage(target, DAMAGE, stats, null);
                trail(sl, from, tc, color);
                continue;
            }
            int pierce = 1 + Math.max(0, stats.pierce);
            ConjuredBladeEntity blade = ConjuredBladeEntity.striker(ctx, stats, DAMAGE, from, dir,
                    LegendFx.conjuredWeapon(sl.random), color, 1.35F, pierce, 1.6F);
            sl.addFreshEntity(blade);
            sl.sendParticles(LegendFx.dust(color, 1.2F), from.x, from.y, from.z, 10, 0.3, 0.3, 0.3, 0);
            sl.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 2, 0.2, 0.2, 0.2, 0.02);
        }
        LegendFx.sound(sl, tc, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 1.5F);
        if (target instanceof LivingEntity) {
            LegendFx.sound(sl, tc, SoundEvents.TRIDENT_THROW, 0.6F, 1.2F);
        }
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        ServerLevel sl = ctx.level();
        if (ConjuredBladeEntity.countFor(sl, ctx.casterId()) >= ConjuredBladeEntity.MAX_PER_OWNER) return;
        Vec3 at = hit.getLocation();
        Vec3 src = ctx.eyePos();
        Vec3 back = new Vec3(at.x - src.x, 0, at.z - src.z);
        if (back.lengthSqr() < 1.0E-3) back = new Vec3(0, 0, 1);
        Vec3 from = clearSpawn(sl, at.add(0, 0.3, 0), at.subtract(back.normalize().scale(3.5)).add(0, 4.0, 0));
        int color = LegendFx.colorOf(ctx, LegendFx.GOLD);
        sl.addFreshEntity(ConjuredBladeEntity.striker(ctx, stats, DAMAGE, from, at.subtract(from), LegendFx.conjuredWeapon(sl.random),
                color, 1.35F, 1 + Math.max(0, stats.pierce), 1.6F));
        sl.sendParticles(LegendFx.dust(color, 1.2F), from.x, from.y, from.z, 10, 0.3, 0.3, 0.3, 0);
    }

    /** Ponto de surgimento sem parede entre ele e o alvo (em cavernas/casas a arma surge logo antes do teto). */
    private static Vec3 clearSpawn(ServerLevel sl, Vec3 target, Vec3 wanted) {
        BlockHitResult hit = sl.clip(new ClipContext(target, wanted, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) return wanted;
        Vec3 back = target.subtract(hit.getLocation());
        return back.lengthSqr() < 1.0E-4 ? hit.getLocation() : hit.getLocation().add(back.normalize().scale(0.6));
    }

    private static void trail(ServerLevel sl, Vec3 from, Vec3 to, int color) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * 3));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            sl.sendParticles(LegendFx.dust(color, 0.9F), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        sl.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 8, 0.25, 0.3, 0.25, 0.25);
    }
}
