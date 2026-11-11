package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.AnimeTricks;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * TEXTURA SURPRESA (homenagem à "textura" de aura do mágico das cartas, que cobre qualquer superfície com outra
 * aparência): uma folha de ilusão que muda o que os outros veem.
 * <ul>
 *   <li><b>Em você</b> (Si Mesmo): DISFARCE por 6 s (Estender) — invisível, os monstros que miravam você perdem o
 *   rastro e só o notam a 2,5 blocos (ou se você os ferir); o primeiro golpe/magia saindo do disfarce é uma SURPRESA:
 *   +60% de dano, e o disfarce acaba.</li>
 *   <li><b>Num inimigo</b>: TEXTURA FALSA — ele passa a "parecer" um inimigo para os outros monstros por perto (até 3,
 *   +1 por Amplificar, a 10 blocos), que o atacam; e fica cego, enjoado e Azarado por pouco tempo (3 de dano).</li>
 * </ul>
 */
public class TextureSurpriseEffect extends EffectGlyph {
    public static final float DAMAGE = 3F;
    public static final int DISGUISE_TICKS = 120;

    public TextureSurpriseEffect() {
        super("texture_surprise", 16);
        origin(MagicPath.GAMBLER);
        tier(3);
        minCircle(3);
    }

    @Override
    public float baseDamage() {
        return DAMAGE;
    }

    @Override
    public int hintDuration() {
        return DISGUISE_TICKS;
    }

    @Override
    public float hintRadius() {
        return 10F;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        if (target == caster) {
            disguise(ctx, caster, stats);
            return;
        }
        if (ctx.isAlly(target)) return;
        ctx.damage(le, DAMAGE, stats, null);
        ctx.status(le, MobEffects.BLINDNESS, 60, 0, stats);
        ctx.status(le, MobEffects.CONFUSION, 100, 0, stats);
        ctx.status(le, GbRegistries.JINXED, 100, 0, stats);
        int max = 3 + Math.max(0, stats.amplify);
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(le.blockPosition()).inflate(10),
                m -> m != le && m.isAlive() && m instanceof Enemy && !AnimeTricks.isBoss(m) && !ctx.isAlly(m))) {
            if (n++ >= max) break;
            m.setTarget(le);
            Vec3 h = m.position().add(0, m.getBbHeight() + 0.4, 0);
            level.sendParticles(GbFx.dust(0xB05AE0, 0.9F), h.x, h.y, h.z, 4, 0.1, 0.1, 0.1, 0);
        }
        sheet(level, le, 0xB05AE0);
        GbFx.sound(level, le.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.3F);
        GbFx.sound(level, le.position(), SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.7F);
    }

    private static void disguise(SpellContext ctx, LivingEntity caster, SpellStats stats) {
        ServerLevel level = ctx.level();
        int ticks = stats.duration(DISGUISE_TICKS);
        caster.addEffect(new MobEffectInstance(GbRegistries.DISGUISE, ticks, 0, false, true, true), caster);
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true), caster);
        AnimeTricks.dropAggro(caster, 24);
        sheet(level, caster, 0xFF6EC7);
        level.sendParticles(ParticleTypes.POOF, caster.getX(), caster.getY() + 1, caster.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
        GbFx.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.2F);
        GbFx.sound(level, caster.position(), SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.5F);
        if (caster instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.disguise_on", ticks / 20), true);
        }
    }

    /** A "folha de textura": cartas de ilusão girando em volta do corpo e se descolando. */
    static void sheet(ServerLevel level, LivingEntity e, int rgb) {
        double h = e.getBbHeight();
        double r = Math.max(0.6, e.getBbWidth());
        for (int i = 0; i < 28; i++) {
            double a = i * 0.45;
            double y = e.getY() + h * (i / 28.0);
            Vec3 p = new Vec3(e.getX() + Math.cos(a) * r, y, e.getZ() + Math.sin(a) * r);
            level.sendParticles(GbFx.dust(i % 4 == 0 ? 0xFFFFFF : rgb, 1.0F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY() + h * 0.5, e.getZ(), 10, r * 0.5, h * 0.4, r * 0.5, 0.02);
    }
}
