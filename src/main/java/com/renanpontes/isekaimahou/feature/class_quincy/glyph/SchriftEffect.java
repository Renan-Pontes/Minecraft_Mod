package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Schrift (Letra): o poder da Letra do conjurador, modesto e montável em qualquer forma. Dano base 3; depois:
 * <ul>
 *   <li><b>E — Explosão</b>: 1 s depois, o alvo detona (raio 2,5, 3 de dano em volta; sem quebrar blocos);</li>
 *   <li><b>I — Ferro</b>: golpe pesado (+1 de dano e empurrão) e Resistência I no conjurador por 3 s;</li>
 *   <li><b>T — Trovão</b>: Choque no alvo e um raio de Reishi pula para 2 inimigos próximos (2 de dano);</li>
 *   <li><b>W — Vento</b>: arremessa o alvo para cima e para longe; o conjurador ganha Velocidade II por 3 s;</li>
 *   <li><b>F — Medo</b>: Lentidão II e Fraqueza por 4 s; criaturas largam o alvo e recuam;</li>
 *   <li><b>H — O Queimado</b>: incendeia o alvo (4 s) e uma labareda queima quem está a 2 blocos (1,5 de dano);</li>
 *   <li><b>J — A Prisão</b>: grades de luz prendem o alvo (Enraizado 2 s; chefes 0,75 s);</li>
 *   <li><b>X — O Eixo X</b>: um traço de Reishi atravessa o alvo e segue 10 blocos atrás dele (2,5 de dano em cada um);</li>
 *   <li><b>B — O Equilíbrio</b>: você cura 40% do dano causado e passa um efeito ruim seu para o alvo;</li>
 *   <li><b>D — O Golpe Mortal</b>: Veneno II e Murchar por 4 s.</li>
 * </ul>
 * Sem Letra (outras classes por Lição, ou mobs): só o dano de Reishi.
 */
public class SchriftEffect extends EffectGlyph {
    public SchriftEffect() {
        super("quincy_schrift", 12);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(3);
        minCircle(4);
    }

    @Override
    public float baseDamage() {
        return 3.0F;
    }

    @Override
    public float hintRadius() {
        return 2.5F;
    }

    @Override
    public int hintDuration() {
        return 80;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ServerLevel level = ctx.level();
        ServerPlayer sp = ctx.casterPlayer();
        Quincy.Letter letter = sp == null ? null : Quincy.letter(MagicApi.get(sp));
        LivingEntity caster = ctx.caster();
        Vec3 at = le.position().add(0, le.getBbHeight() / 2, 0);
        if (letter == null) {
            ctx.damage(le, 3.0F, stats, null);
            QuincyFx.burst(level, at, 10, 0.3);
            return;
        }
        level.sendParticles(QuincyFx.dust(letter.rgb, 1.3F), at.x, at.y, at.z, 14, 0.35, 0.4, 0.35, 0.02);
        switch (letter) {
            case EXPLODE -> {
                ctx.damage(le, 3.0F, stats, null);
                float r = stats.radius(2.5F);
                SpellScheduler.schedule(20, () -> {
                    // detona mesmo se o alvo já morreu (no ponto onde ele estava)
                    Vec3 c = le.isAlive() ? le.position().add(0, le.getBbHeight() / 2, 0) : at;
                    level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                    level.sendParticles(QuincyFx.dust(letter.rgb, 1.5F), c.x, c.y, c.z, 30, r * 0.4, r * 0.3, r * 0.4, 0.05);
                    QuincyFx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.4F);
                    for (LivingEntity e : ctx.enemiesAround(c, r)) ctx.damage(e, 3.0F, stats, null);
                });
            }
            case IRON -> {
                ctx.damage(le, 4.0F, stats, null);
                if (caster != null) {
                    Vec3 push = le.position().subtract(caster.position()).normalize().scale(0.9 * stats.power());
                    le.push(push.x, 0.25, push.z);
                    le.hurtMarked = true;
                    caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
                }
                QuincyFx.sound(level, at, SoundEvents.ANVIL_LAND, 0.4F, 1.6F);
            }
            case THUNDER -> {
                ctx.damage(le, 3.0F, stats, null);
                ctx.status(le, CoreRegistries.SHOCKED, 60, 0, stats);
                List<LivingEntity> near = ctx.enemiesAround(at, 5.0);
                int jumped = 0;
                for (LivingEntity e : near) {
                    if (e == le || jumped >= 2) continue;
                    jumped++;
                    QuincyFx.line(level, at, e.position().add(0, e.getBbHeight() / 2, 0), QuincyFx.dust(letter.rgb, 0.9F), 0.3);
                    ctx.damage(e, 2.0F, stats, null);
                }
                QuincyFx.sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35F, 1.8F);
            }
            case WIND -> {
                ctx.damage(le, 2.5F, stats, null);
                if (caster != null) {
                    Vec3 push = le.position().subtract(caster.position()).normalize().scale(1.1 * stats.power());
                    le.push(push.x, 0.6, push.z);
                    le.hurtMarked = true;
                    caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));
                }
                level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                QuincyFx.sound(level, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.6F, 1.3F);
            }
            case FEAR -> {
                ctx.damage(le, 2.0F, stats, null);
                ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 80, 1, stats);
                ctx.status(le, MobEffects.WEAKNESS, 80, 0, stats);
                if (le instanceof Mob mob && caster != null) {
                    mob.setTarget(null);
                    Vec3 away = mob.position().subtract(caster.position()).normalize().scale(6);
                    mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.3);
                }
                level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.4, at.z, 4, 0.3, 0.3, 0.3, 0.02);
                QuincyFx.sound(level, at, SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.4F);
            }
            case BURNT -> {
                ctx.damage(le, 3.0F, stats, null);
                le.igniteForSeconds(4.0F);
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 20, 0.6, 0.5, 0.6, 0.04);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 3, 0.3, 0.2, 0.3, 0.0);
                for (LivingEntity e : ctx.enemiesAround(at, 2.0)) {
                    if (e == le) continue;
                    ctx.damage(e, 1.5F, stats, null);
                    e.igniteForSeconds(2.0F);
                }
                QuincyFx.sound(level, at, SoundEvents.BLAZE_SHOOT, 0.7F, 1.2F);
            }
            case JAIL -> {
                ctx.damage(le, 2.0F, stats, null);
                int dur = com.renanpontes.isekaimahou.core.api.Statuses.isBossLike(le) ? 15 : stats.duration(40);
                le.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, dur, 0), caster);
                double r = le.getBbWidth() * 0.7 + 0.4;
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI * 2 / 8;
                    Vec3 b = le.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    QuincyFx.line(level, b, b.add(0, le.getBbHeight() + 0.4, 0), QuincyFx.dust(letter.rgb, 0.8F), 0.25);
                }
                QuincyFx.sound(level, at, SoundEvents.IRON_DOOR_CLOSE, 0.7F, 1.5F);
            }
            case XAXIS -> {
                ctx.damage(le, 3.0F, stats, null);
                Vec3 origin = caster != null ? caster.getEyePosition() : at.subtract(ctx.look().scale(4));
                Vec3 dir = at.subtract(origin);
                dir = dir.lengthSqr() < 1.0E-4 ? ctx.look() : dir.normalize();
                Vec3 end = at.add(dir.scale(10));
                QuincyFx.line(level, at, end, QuincyFx.dust(letter.rgb, 1.0F), 0.3);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, end).inflate(1.0),
                        x -> x.isAlive() && x != le && x != caster && !ctx.isAlly(x))) {
                    if (e.getBoundingBox().inflate(0.4).clip(at, end).isPresent()) ctx.damage(e, 2.5F, stats, null);
                }
                level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 10, 0.2, 0.2, 0.2, 0.3);
                QuincyFx.sound(level, at, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.6F, 1.6F);
            }
            case BALANCE -> {
                float dealt = ctx.damage(le, 2.5F, stats, null);
                if (caster != null) {
                    caster.heal(dealt * 0.4F);
                    MobEffectInstance worst = null;
                    for (MobEffectInstance inst : caster.getActiveEffects()) {
                        if (inst.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                            worst = inst;
                            break;
                        }
                    }
                    if (worst != null && le.isAlive()) {
                        caster.removeEffect(worst.getEffect());
                        le.addEffect(new MobEffectInstance(worst.getEffect(), Math.max(40, worst.getDuration()), worst.getAmplifier()), caster);
                    }
                    QuincyFx.line(level, caster.position().add(0, 1.0, 0), at, QuincyFx.dust(letter.rgb, 0.7F), 0.4);
                }
                QuincyFx.sound(level, at, SoundEvents.AMETHYST_CLUSTER_STEP, 0.8F, 1.0F);
            }
            case DEATHDEALING -> {
                ctx.damage(le, 2.0F, stats, null);
                ctx.status(le, MobEffects.POISON, 80, 1, stats);
                ctx.status(le, MobEffects.WITHER, 80, 0, stats);
                level.sendParticles(QuincyFx.dust(letter.rgb, 1.2F), at.x, at.y, at.z, 16, 0.4, 0.5, 0.4, 0.02);
                QuincyFx.sound(level, at, SoundEvents.SPIDER_AMBIENT, 0.6F, 1.6F);
            }
        }
    }
}
