package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiTasks;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiTechniques;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ReishiWaveEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mugetsu (homenagem ao Getsuga Tenshō Final) — forma G / ultimate de ÚLTIMO RECURSO. Você se ergue, o mundo em volta
 * mergulha na escuridão total (Escuridão nos jogadores, Cegueira e Lentidão nos inimigos a 40 blocos) e, 1 s depois,
 * uma parede de trevas de 18 blocos de altura (+2 por Expandir) corta TUDO por ~65 blocos: atravessa blocos e cada
 * inimigo leva dano próprio enorme (30) além dos efeitos da magia. O PREÇO: depois disso você fica SEM PODER
 * ESPIRITUAL por 10 minutos (sem técnicas Shinigami, sem Shikai/Bankai e o Vigor quase parado). Funciona sem efeitos.
 * Rank V + 150 Getsuga + 6 chefes → a última lição do espírito no Mundo Interior.
 */
public class MugetsuForm extends FormGlyph {
    public static final float DAMAGE = 30F;

    public MugetsuForm() {
        super("zanpakuto_mugetsu", 140);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(5);
        minCircle(9);
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public boolean requiresEffect() {
        return false;
    }

    @Override
    public int baseCastTicks() {
        return 40;
    }

    @Override
    public int baseCooldown() {
        return 2400;
    }

    @Override
    public float hintRange() {
        return 65F;
    }

    @Override
    public float hintRadius() {
        return 9F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 at = caster.position();
        // erguer-se e a escuridão total
        caster.setDeltaMovement(caster.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, 0.8, 0));
        caster.hurtMarked = true;
        caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, false));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(40), LivingEntity::isAlive)) {
            if (e == caster) continue;
            if (e instanceof Player) {
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
            } else if (!ctx.isAlly(e)) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), caster);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), caster);
            }
        }
        // a tela de quem está perto escurece; 1 s depois, um único corte
        com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFxNet.burstNear(level,
                com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFxNet.MUGETSU, at, caster.getLookAngle(), 20F, 0x3A1450,
                caster.getId(), 64);
        ShinigamiFx.pillar(level, at, 24.0, 220, ShinigamiFx.BLACK);
        for (int k = 0; k < 5; k++) ShinigamiFx.ring(level, at.add(0, 0.2 + k * 0.6, 0), 3 + k * 3.0, 36 + k * 8, k * 0.4F, ShinigamiFx.BLACK);
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.5, at.z, 80, 3.0, 2.0, 3.0, 0.05);
        ShinigamiFx.sound(level, at, SoundEvents.WITHER_SPAWN, 1.0F, 0.4F);
        ShinigamiFx.sound(level, at, SoundEvents.BELL_RESONATE, 1.2F, 0.4F);
        ShinigamiFx.sound(level, at, SoundEvents.AMBIENT_CAVE.value(), 1.5F, 0.5F);
        if (caster instanceof ServerPlayer sp && !s.silent) {
            Announce.send(sp, Shinigami.msg("mugetsu_shout"), 0x3A1450, Announce.STYLE_ULTIMATE);
        }
        float width = 18F + 2F * Math.max(0, s.aoe);
        float speed = 1.5F * s.speedMult();
        int life = Math.max(20, Math.round(65 / speed));
        ShinigamiTasks.later(20, () -> {
            if (!caster.isAlive() || caster.level() != level) return;
            Vec3 look = caster.getLookAngle();
            Vec3 flat = new Vec3(look.x, look.y * 0.3, look.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
            Vec3 from = caster.position().add(0, caster.getBbHeight() * 0.5, 0).add(flat.scale(1.5));
            ReishiWaveEntity w = ReishiWaveEntity.spell(ctx, caster, from, ReishiWaveEntity.MUGETSU, width, life, 0x0A0608).bonus(DAMAGE, true);
            w.shoot(flat.x, flat.y, flat.z, speed, 0F);
            level.addFreshEntity(w);
            ShinigamiFx.slashArc(level, from, flat, 3.0, 100, 6F, ShinigamiFx.BLACK);
            level.sendParticles(ParticleTypes.SONIC_BOOM, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            ShinigamiFx.sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.3F);
            ShinigamiFx.sound(level, from, SoundEvents.ENDER_DRAGON_GROWL, 0.6F, 0.4F);
            caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            // o preço: sem poder espiritual por 10 minutos
            if (caster instanceof ServerPlayer sp) ShinigamiTechniques.startPowerless(sp, ShinigamiTechniques.MUGETSU_POWERLESS);
        });
    }
}
