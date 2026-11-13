package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Vollständig (a forma G do Quincy): asas de luz e uma auréola. Enquanto dura, o Quincy plana (gravidade quase zero),
 * fica com Resistência I, engole projéteis a 6 blocos (curando um pouco) e, a cada segundo, uma saraivada cai da
 * auréola sobre até 4 inimigos (+1 por Dividir) num raio de 18, cada flecha com os efeitos da magia.
 * Estado só em memória; ao acabar (ou morrer/sair) a gravidade volta ao normal.
 * <p>
 * Release 1.0: a forma tem um espaço de receita de Transformação ({@link QuincyForge}) — a receita escolhida pelo jogador
 * entra por cima quando as asas abrem e sai quando elas se desfazem. Sem receita, nada muda.
 */
public final class QuincyVollstandig {
    public static final ResourceLocation GRAVITY = IsekaiMahou.id("quincy_vollstandig_gravity");
    private static final int PULSE = 20;

    private static final class Active {
        final SpellContext ctx;
        final ServerLevel level;
        final long end;
        final float radius;
        final int volley;
        int age;

        Active(SpellContext ctx, ServerLevel level, long end, float radius, int volley) {
            this.ctx = ctx;
            this.level = level;
            this.end = end;
            this.radius = radius;
            this.volley = volley;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    public static void start(SpellContext ctx, int duration, float radius, int volley) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ACTIVE.put(caster.getUUID(), new Active(ctx, ctx.level(), ctx.level().getGameTime() + duration, radius, volley));
        QuincyFxNet.form(caster, QuincyFxNet.FORM_VOLL, duration);
        QuincyFxNet.burst(caster, QuincyFxNet.ASCEND, caster.position(), Vec3.ZERO, 3.0F, Quincy.REISHI_RGB);
        AttributeInstance g = caster.getAttribute(Attributes.GRAVITY);
        if (g != null) {
            g.removeModifier(GRAVITY);
            g.addTransientModifier(new AttributeModifier(GRAVITY, -0.85, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration + 40, 0, false, false, false));
        caster.setDeltaMovement(caster.getDeltaMovement().add(0, 0.6, 0));
        caster.hurtMarked = true;
        ServerLevel level = ctx.level();
        QuincyFx.pentacle(level, caster.position().add(0, 0.1, 0), 3.0, 0F);
        QuincyFx.pillar(level, caster.position(), 8.0, 120);
        QuincyFx.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, 0.9F);
        QuincyFx.sound(level, caster.position(), SoundEvents.ELYTRA_FLYING, 0.5F, 1.6F);
        // a receita moldada pelo jogador (se houver) entra por cima do Vollständig
        if (caster instanceof ServerPlayer sp) QuincyForge.begin(sp, duration);
    }

    public static boolean active(Entity e) {
        return ACTIVE.containsKey(e.getUUID());
    }

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> e = it.next();
            Active a = e.getValue();
            Entity ent = a.level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity caster) || !caster.isAlive() || a.level.getGameTime() >= a.end) {
                if (ent instanceof LivingEntity le) finish(a.level, le);
                it.remove();
                continue;
            }
            a.age++;
            // asas/auréola desenhadas no cliente; as partículas ficam só como brilho
            if (a.age % 6 == 0) wings(a.level, caster, a.age);
            if (a.age % 40 == 0) QuincyFxNet.form(caster, QuincyFxNet.FORM_VOLL, (int) Math.max(1, a.end - a.level.getGameTime()));
            if (a.age % 3 == 0) {
                for (Projectile p : QuincyAbsorption.enemyProjectiles(a.level, caster, 6)) {
                    QuincyAbsorption.swallow(a.level, caster, p);
                    caster.heal(1.0F);
                }
            }
            if (a.age % PULSE == 0) volley(a, caster);
        }
    }

    /** Uma saraivada da auréola: uma flecha por alvo (até {@code volley}). */
    private static void volley(Active a, LivingEntity caster) {
        List<LivingEntity> targets = new ArrayList<>(a.ctx.enemiesAround(caster.position(), a.radius));
        targets.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(caster)));
        Vec3 halo = caster.position().add(0, caster.getBbHeight() + 1.4, 0);
        int n = Math.min(a.volley, targets.size());
        for (int i = 0; i < n; i++) {
            LivingEntity t = targets.get(i);
            double ang = i * Mth.TWO_PI / Math.max(1, n);
            Vec3 from = halo.add(Math.cos(ang) * 0.9, 0, Math.sin(ang) * 0.9);
            Vec3 dir = t.position().add(0, t.getBbHeight() / 2, 0).subtract(from).normalize();
            SpiritArrowEntity arrow = SpiritArrowEntity.spell(a.ctx, caster, from).homing(true).size(1.3F);
            arrow.shoot(dir.x, dir.y, dir.z, 3.0F, 0.0F);
            a.level.addFreshEntity(arrow);
        }
        QuincyFx.ring(a.level, halo, 0.9, 12, a.age * 0.1F, QuincyFx.WHITE);
        if (n > 0) QuincyFx.sound(a.level, halo, SoundEvents.ARROW_SHOOT, 0.8F, 1.7F);
    }

    /** Asas de luz atrás do Quincy (duas penas grandes em leque) e a auréola girando. */
    private static void wings(ServerLevel level, LivingEntity caster, int age) {
        float yaw = (float) Math.toRadians(caster.yBodyRot);
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(0.35);
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 base = caster.position().add(0, caster.getBbHeight() * 0.72, 0).add(back);
        float flap = Mth.sin(age * 0.25F) * 0.25F;
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 3; f++) {
                double ang = Math.toRadians(25 + f * 22) + flap;
                double len = 1.9 - f * 0.35;
                for (int k = 1; k <= 5; k++) {
                    double t = k / 5.0 * len;
                    Vec3 p = base.add(right.scale(side * Math.cos(ang) * t)).add(0, Math.sin(ang) * t, 0).add(back.scale(t * 0.6));
                    level.sendParticles(k == 5 ? QuincyFx.WHITE : QuincyFx.REISHI, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
        Vec3 halo = caster.position().add(0, caster.getBbHeight() + 0.35, 0);
        if (age % 4 == 0) QuincyFx.ring(level, halo, 0.45, 10, age * 0.2F, QuincyFx.WHITE);
        if (age % 10 == 0) level.sendParticles(ParticleTypes.END_ROD, base.x, base.y, base.z, 2, 0.4, 0.4, 0.4, 0.02);
    }

    private static void finish(ServerLevel level, LivingEntity caster) {
        if (caster instanceof ServerPlayer sp) QuincyForge.end(sp);
        QuincyFxNet.form(caster, QuincyFxNet.FORM_NONE, 0);
        AttributeInstance g = caster.getAttribute(Attributes.GRAVITY);
        if (g != null) g.removeModifier(GRAVITY);
        QuincyFx.burst(level, caster.position().add(0, 1.2, 0), 30, 0.8);
        QuincyFx.sound(level, caster.position(), SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.2F);
    }

    /** Sair/morrer: tira o modificador e esquece. */
    static void forget(LivingEntity e) {
        if (ACTIVE.remove(e.getUUID()) != null) {
            AttributeInstance g = e.getAttribute(Attributes.GRAVITY);
            if (g != null) g.removeModifier(GRAVITY);
        }
    }

    static void clear() {
        ACTIVE.clear();
    }

    private QuincyVollstandig() {}
}
