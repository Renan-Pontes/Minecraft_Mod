package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Bankai (a forma G do Shinigami): a transformação final da zanpakutō. Enquanto dura: Força II, Resistência I,
 * Velocidade II e Salto; golpes com a espada ×1,6 com a assinatura reforçada; e, a cada segundo, um pulso no raio da
 * forma aplica os EFEITOS da magia e o campo do arquétipo em todos os inimigos (Peso: gravidade esmagadora; Pétalas:
 * tempestade de mil lâminas; Gelo: campo congelante; Chama: inferno; Pincel: domínio de tinta que apaga nomes).
 * Ao acabar, o corpo cobra o preço (Fraqueza e Cansaço). Estado só em memória.
 */
public final class BankaiState {
    private static final int PULSE = 20;

    private static final class Active {
        final SpellContext ctx;
        final ServerLevel level;
        final Archetype archetype;
        final long end;
        final float radius;
        int age;

        Active(SpellContext ctx, ServerLevel level, Archetype archetype, long end, float radius) {
            this.ctx = ctx;
            this.level = level;
            this.archetype = archetype;
            this.end = end;
            this.radius = radius;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    /** Começa a Bankai. Sem arquétipo (Lição para outra classe ou mob), usa o Pincel Branco. */
    public static void start(SpellContext ctx, int duration, float radius) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        Archetype a = caster instanceof ServerPlayer sp ? Shinigami.archetype(MagicApi.get(sp)) : null;
        if (a == null) a = Archetype.BRUSH;
        ServerLevel level = ctx.level();
        ACTIVE.put(caster.getUUID(), new Active(ctx, level, a, level.getGameTime() + duration, radius));
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 1, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 1, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.JUMP, duration, 1, false, false, true));
        ShinigamiFxNet.burst(caster, ShinigamiFxNet.BANKAI, caster.position(), Vec3.ZERO, Math.max(8F, radius), a.rgb,
                caster instanceof ServerPlayer ? a.bankaiName() : null);
        if (caster instanceof ServerPlayer sp) {
            Release.beginBankai(sp, duration);
            // Senbonzakura Kageyoshi: as lâminas afundam no chão e viram o oceano de pétalas
            if (a == Archetype.PETALS && Shinigami.isShinigami(sp)) PetalSwarm.bankai(sp);
            if (Shinigami.isShinigami(sp)) {
                Announce.send(sp, Shinigami.msg("bankai_shout", sp.getDisplayName(), a.bankaiName()), a.rgb, Announce.STYLE_ULTIMATE);
            }
        }
        // transformação: coluna de Reiatsu, onda de choque e o campo do arquétipo
        Vec3 at = caster.position();
        ShinigamiFx.pillar(level, at, 14.0, 160, ShinigamiFx.dust(a.rgb, 1.6F));
        ShinigamiFx.pillar(level, at, 10.0, 60, ShinigamiFx.BLACK);
        for (int k = 0; k < 3; k++) ShinigamiFx.ring(level, at.add(0, 0.2 + k * 0.4, 0), 1.5 + k * 1.6, 28 + k * 8, k * 0.4F, ShinigamiFx.dust(a.rgb, 1.4F));
        ShinigamiFx.signature(level, a, at.add(0, 1, 0), 60, 2.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        ShinigamiFx.sound(level, at, SoundEvents.WITHER_SPAWN, 0.7F, 1.4F);
        ShinigamiFx.sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 0.9F, 0.8F);
        for (LivingEntity e : ctx.enemiesAround(at, radius)) {
            Vec3 push = e.position().subtract(at);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0.5) : push.normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.2, 0.5, push.z * 1.2));
            e.hurtMarked = true;
        }
    }

    public static boolean active(Entity e) {
        return ACTIVE.containsKey(e.getUUID());
    }

    @Nullable
    public static Archetype archetypeOf(Entity e) {
        Active a = ACTIVE.get(e.getUUID());
        return a == null ? null : a.archetype;
    }

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> e = it.next();
            Active a = e.getValue();
            Entity ent = a.level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity caster) || !caster.isAlive() || a.level.getGameTime() >= a.end) {
                if (ent instanceof ServerPlayer sp) Release.seal(sp, false);
                it.remove();
                continue;
            }
            a.age++;
            if (a.age % 2 == 0) aura(a, caster);
            if (a.age % PULSE == 0) pulse(a, caster);
        }
    }

    /** O campo visual do arquétipo em volta do conjurador. */
    private static void aura(Active a, LivingEntity caster) {
        ServerLevel l = a.level;
        Vec3 c = caster.position();
        float t = a.age * 0.12F;
        switch (a.archetype) {
            case WEIGHT -> {
                ShinigamiFx.ring(l, c.add(0, 0.05, 0), (a.age % 40) / 40.0 * a.radius, 20, t, ShinigamiFx.dust(0x3A3F4E, 1.6F));
                if (a.age % 6 == 0) l.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, c.x, c.y + 4, c.z, 6, a.radius * 0.4, 0.5, a.radius * 0.4, 0);
            }
            case PETALS -> {
                for (int k = 0; k < (caster instanceof ServerPlayer ? 2 : 6); k++) {
                    double ang = t + k * Mth.TWO_PI / 6;
                    double r = 1.6 + (k % 3) * (a.radius / 4);
                    l.sendParticles(ParticleTypes.CHERRY_LEAVES, c.x + Math.cos(ang) * r, c.y + 0.5 + (k % 3) * 0.7, c.z + Math.sin(ang) * r,
                            2, 0.15, 0.15, 0.15, 0.01);
                }
            }
            case FROST -> {
                ShinigamiFx.ring(l, c.add(0, 0.1, 0), 1.6, 12, t, ShinigamiFx.dust(0xA8ECFF, 1.2F));
                l.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1.5, c.z, 6, a.radius * 0.4, 1.0, a.radius * 0.4, 0.01);
            }
            case FLAME -> {
                ShinigamiFx.ring(l, c.add(0, 0.1, 0), 1.4, 12, t, ParticleTypes.FLAME);
                l.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.2, c.z, 6, a.radius * 0.35, 0.1, a.radius * 0.35, 0.01);
                if (a.age % 10 == 0) l.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 2.2, c.z, 4, 0.6, 0.4, 0.6, 0.01);
            }
            case BRUSH -> {
                ShinigamiFx.ring(l, c.add(0, 0.05, 0), 1.8 + Math.sin(t) * 0.3, 16, t, ShinigamiFx.INK);
                l.sendParticles(ShinigamiFx.INK, c.x, c.y + 0.05, c.z, 5, a.radius * 0.4, 0.0, a.radius * 0.4, 0);
            }
        }
        // a silhueta ganha um contorno de Reiatsu negro com brilho do arquétipo
        l.sendParticles(ShinigamiFx.BLACK, c.x, c.y + caster.getBbHeight() * 0.6, c.z, 2, 0.35, 0.6, 0.35, 0.01);
        l.sendParticles(ShinigamiFx.dust(a.archetype.rgb, 0.9F), c.x, c.y + caster.getBbHeight() * 0.6, c.z, 1, 0.4, 0.6, 0.4, 0.01);
    }

    /** Um pulso: efeitos da magia + campo do arquétipo em todos os inimigos do raio. */
    private static void pulse(Active a, LivingEntity caster) {
        int hit = 0;
        for (LivingEntity e : a.ctx.enemiesAround(caster.position(), a.radius)) {
            if (hit++ >= 24) break;
            a.ctx.resolveOnEntity(e, caster);
            Signatures.apply(caster, e, a.archetype, 1.4F, 80);
            if (a.archetype == Archetype.WEIGHT) {
                Vec3 v = e.getDeltaMovement();
                e.setDeltaMovement(v.x * 0.2, Math.min(v.y, -0.8), v.z * 0.2);
                e.hurtMarked = true;
            } else if (a.archetype == Archetype.PETALS) {
                a.ctx.damage(e, 2.0F, a.ctx.formStats(), caster);
            } else if (a.archetype == Archetype.FROST && e.hasEffect(CoreRegistries.FROZEN)) {
                a.level.sendParticles(ParticleTypes.ITEM_SNOWBALL, e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
            }
        }
        ShinigamiFx.ring(a.level, caster.position().add(0, 0.15, 0), a.radius, Math.min(64, (int) (a.radius * 6)), a.age * 0.05F,
                ShinigamiFx.dust(a.archetype.rgb, 1.2F));
        ShinigamiFx.sound(a.level, caster.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.25F, 1.8F);
    }

    static void forget(UUID id) {
        ACTIVE.remove(id);
    }

    static void clear() {
        ACTIVE.clear();
    }

    private BankaiState() {}
}
