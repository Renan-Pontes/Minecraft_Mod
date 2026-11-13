package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * As duas formas finais do Quincy (formas G), estado só em memória:
 * <ul>
 *   <li><b>Quincy: Letzt Stil</b> (homenagem à "forma final" do Quincy): 30 s de poder ENORME — técnicas Quincy ×2,
 *   Força II, Velocidade II, Resistência I, Regeneração I, plana, engole todo projétil a 8 blocos e a cada 0,75 s um
 *   leque de 6 flechas teleguiadas gigantes cai sobre os inimigos (os efeitos da magia). O PREÇO: ao acabar, a vida cai
 *   para 50%, Fraqueza II e 10 minutos SEM PODER QUINCY;</li>
 *   <li><b>O Todo-Poderoso</b> (homenagem a "The Almighty", Letra A): por 20 s você VÊ O FUTURO — 45% de chance de
 *   esquivar de cada golpe (passo para o lado com imagem residual), os inimigos que vão atacar você brilham e mostram a
 *   trajetória antes, e a cada 5 s o primeiro golpe pesado (≥ 20% da vida ou letal) é REESCRITO: anulado e devolvido a
 *   quem atacou (com os efeitos da magia).</li>
 * </ul>
 */
public final class QuincyFinalForms {
    public static final ResourceLocation GRAVITY = IsekaiMahou.id("quincy_letzt_stil_gravity");
    public static final float LETZT_POWER = 2.0F;
    public static final float DODGE_CHANCE = 0.45F;
    public static final int REWRITE_COOLDOWN = 100;
    private static final DustParticleOptions GOLD = QuincyFx.dust(0xFFE89A, 1.2F);
    private static final DustParticleOptions EYE = QuincyFx.dust(0x101018, 1.0F);
    private static final DustParticleOptions WARN = QuincyFx.dust(0xFF3A3A, 0.8F);

    private static final class State {
        final SpellContext ctx;
        final ServerLevel level;
        final long end;
        final float radius;
        int age;
        long nextRewrite;

        State(SpellContext ctx, ServerLevel level, long end, float radius) {
            this.ctx = ctx;
            this.level = level;
            this.end = end;
            this.radius = radius;
        }
    }

    private static final Map<UUID, State> LETZT = new HashMap<>();
    private static final Map<UUID, State> ALMIGHTY = new HashMap<>();

    public static boolean letzt(Entity e) {
        return LETZT.containsKey(e.getUUID());
    }

    public static boolean almighty(Entity e) {
        return ALMIGHTY.containsKey(e.getUUID());
    }

    /** Multiplicador das técnicas Quincy na Letzt Stil. */
    public static float castPower(Entity e) {
        return letzt(e) ? LETZT_POWER : 1.0F;
    }

    // =====================================================================================
    //  Letzt Stil
    // =====================================================================================
    public static void startLetzt(SpellContext ctx, int ticks, float radius) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        // as flechas da própria forma já saem com o poder ×2
        ctx.setPowerMult(ctx.powerMult() * LETZT_POWER);
        LETZT.put(caster.getUUID(), new State(ctx, level, level.getGameTime() + ticks, radius));
        QuincyFxNet.form(caster, QuincyFxNet.FORM_LETZT, ticks);
        QuincyFxNet.burst(caster, QuincyFxNet.ASCEND, caster.position(), Vec3.ZERO, 4.5F, 0xFFE89A);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks + 40, 0, false, false, false));
        AttributeInstance g = caster.getAttribute(Attributes.GRAVITY);
        if (g != null) {
            g.removeModifier(GRAVITY);
            g.addTransientModifier(new AttributeModifier(GRAVITY, -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        caster.setDeltaMovement(caster.getDeltaMovement().add(0, 0.8, 0));
        caster.hurtMarked = true;
        Vec3 at = caster.position();
        QuincyFx.pentacle(level, at.add(0, 0.1, 0), 4.0, 0F);
        QuincyFx.pentacle(level, at.add(0, 0.1, 0), 2.0, 0.6F);
        QuincyFx.pillar(level, at, 20.0, 220);
        for (int k = 0; k < 40; k++) {
            double a = k * Mth.TWO_PI / 40;
            level.sendParticles(GOLD, at.x + Math.cos(a) * 3, at.y + 2.5, at.z + Math.sin(a) * 3, 1, 0, 0.05, 0, 0.01);
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.5, at.z, 1, 0, 0, 0, 0);
        QuincyFx.sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.6F);
        QuincyFx.sound(level, at, SoundEvents.TOTEM_USE, 0.8F, 1.3F);
        QuincyFx.sound(level, at, SoundEvents.ELYTRA_FLYING, 0.6F, 1.2F);
        if (caster instanceof ServerPlayer sp && !ctx.formStats().silent) {
            Announce.send(sp, Quincy.msg("letzt_shout", sp.getDisplayName()), 0xFFF2C0, Announce.STYLE_ULTIMATE);
        }
    }

    private static void letztTick(State s, LivingEntity e) {
        ServerLevel l = s.level;
        // as asas e a auréola são desenhadas no cliente (QuincyFxNet); aqui só um brilho de partículas de vez em quando
        if (s.age % 6 == 0) wings(l, e, s.age);
        if (s.age % 40 == 0) QuincyFxNet.form(e, QuincyFxNet.FORM_LETZT, (int) Math.max(1, s.end - l.getGameTime()));
        if (s.age % 2 == 0) {
            for (Projectile p : QuincyAbsorption.enemyProjectiles(l, e, 8)) {
                QuincyAbsorption.swallow(l, e, p);
                e.heal(1.0F);
            }
        }
        if (s.age % 20 == 0 && e instanceof ServerPlayer sp) Quincy.addReishi(sp, 1);
        if (s.age % 15 == 0) {
            List<LivingEntity> targets = new ArrayList<>(s.ctx.enemiesAround(e.position(), s.radius));
            if (targets.isEmpty()) return;
            targets.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(e)));
            Vec3 halo = e.position().add(0, e.getBbHeight() + 1.6, 0);
            for (int i = 0; i < 6; i++) {
                LivingEntity t = targets.get(i % targets.size());
                double ang = i * Mth.TWO_PI / 6 + s.age * 0.05;
                Vec3 from = halo.add(Math.cos(ang) * 1.6, Math.sin(ang * 2) * 0.3, Math.sin(ang) * 1.6);
                Vec3 dir = t.position().add(0, t.getBbHeight() / 2, 0).subtract(from).normalize();
                SpiritArrowEntity a = SpiritArrowEntity.spell(s.ctx, e, from).homing(true).size(1.8F).pierce(1);
                a.shoot(dir.x, dir.y, dir.z, 3.2F, 0.0F);
                l.addFreshEntity(a);
            }
            QuincyFx.ring(l, halo, 1.6, 18, s.age * 0.1F, GOLD);
            QuincyFx.sound(l, halo, SoundEvents.ARROW_SHOOT, 1.0F, 1.4F);
        }
    }

    /** Asas enormes de luz dourada (cinco penas por lado), a cruz nas costas e a auréola larga. */
    private static void wings(ServerLevel level, LivingEntity caster, int age) {
        float yaw = (float) Math.toRadians(caster.yBodyRot);
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(0.4);
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 base = caster.position().add(0, caster.getBbHeight() * 0.72, 0).add(back);
        float flap = Mth.sin(age * 0.2F) * 0.2F;
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 5; f++) {
                double ang = Math.toRadians(10 + f * 20) + flap;
                double len = 3.2 - f * 0.35;
                for (int k = 1; k <= 7; k++) {
                    double t = k / 7.0 * len;
                    Vec3 p = base.add(right.scale(side * Math.cos(ang) * t)).add(0, Math.sin(ang) * t, 0).add(back.scale(t * 0.5));
                    level.sendParticles(k >= 6 ? QuincyFx.WHITE : (f % 2 == 0 ? GOLD : QuincyFx.REISHI), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (age % 4 == 0) {
            // a cruz Quincy nas costas
            QuincyFx.line(level, base.add(0, 0.9, 0), base.add(0, -0.9, 0), GOLD, 0.2);
            QuincyFx.line(level, base.add(right.scale(0.5)).add(0, 0.4, 0), base.add(right.scale(-0.5)).add(0, 0.4, 0), GOLD, 0.2);
            QuincyFx.ring(level, caster.position().add(0, caster.getBbHeight() + 0.45, 0), 0.6, 12, age * 0.2F, GOLD);
        }
    }

    private static void finishLetzt(State s, LivingEntity e, boolean natural) {
        // onda 5: a receita moldada para a Letzt Stil (aba Votos) acaba junto com a forma
        if (e instanceof ServerPlayer lp) {
            com.renanpontes.isekaimahou.glyph.TransformLaws.end(lp, com.renanpontes.isekaimahou.IsekaiMahou.id(Quincy.LETZT_STIL));
        }
        AttributeInstance g = e.getAttribute(Attributes.GRAVITY);
        if (g != null) g.removeModifier(GRAVITY);
        QuincyFx.burst(s.level, e.position().add(0, 1.2, 0), 50, 1.0);
        QuincyFx.sound(s.level, e.position(), SoundEvents.BEACON_DEACTIVATE, 1.2F, 0.6F);
        if (!natural || !e.isAlive()) return;
        float floor = Math.max(1F, e.getMaxHealth() * 0.5F);
        if (e.getHealth() > floor) e.setHealth(floor);
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1));
        if (e instanceof ServerPlayer sp) {
            QuincyTechniques.startPowerless(sp, QuincyTechniques.LETZT_POWERLESS);
        }
    }

    // =====================================================================================
    //  O Todo-Poderoso
    // =====================================================================================
    public static void startAlmighty(SpellContext ctx, int ticks) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        ALMIGHTY.put(caster.getUUID(), new State(ctx, level, level.getGameTime() + ticks, 24F));
        QuincyFxNet.form(caster, QuincyFxNet.FORM_ALMIGHTY, ticks);
        QuincyFxNet.burst(caster, QuincyFxNet.EYES, caster.getEyePosition(), Vec3.ZERO, 3F, 0x6FA8FF);
        Vec3 eye = caster.getEyePosition();
        // w10: os olhos que se abrem, a tinta que espirra e as sombras são desenhados no cliente (QuincyFxClient, pelo
        // preset gráfico); aqui só o clarão e os sons.
        level.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        QuincyFx.sound(level, eye, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.9F, 0.5F);
        QuincyFx.sound(level, eye, SoundEvents.ENDER_EYE_DEATH, 1.0F, 0.5F);
        QuincyFx.sound(level, eye, SoundEvents.BELL_RESONATE, 1.2F, 0.7F);
        QuincyFx.sound(level, eye, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.4F);
        if (caster instanceof ServerPlayer sp && !ctx.formStats().silent) {
            Announce.send(sp, Quincy.msg("almighty_shout", sp.getDisplayName()), 0x1A1A2A, Announce.STYLE_ULTIMATE);
        }
    }

    private static void almightyTick(State s, LivingEntity e) {
        ServerLevel l = s.level;
        if (s.age % 40 == 0) QuincyFxNet.form(e, QuincyFxNet.FORM_ALMIGHTY, (int) Math.max(1, s.end - l.getGameTime()));
        // w10: os olhos e a sombra em volta são do cliente (QuincyFxClient) — o servidor não manda mais partículas aqui.
        if (s.age % 10 != 0) return;
        // "vê os ataques antes": quem mira você brilha e mostra a linha do golpe; projéteis mostram a trajetória
        for (Mob m : l.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(s.radius), m -> m.isAlive() && m.getTarget() == e)) {
            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 14, 0, false, false));
            QuincyFx.line(l, m.getEyePosition(), e.position().add(0, e.getBbHeight() * 0.6, 0), WARN, 0.8);
            l.sendParticles(EYE, m.getX(), m.getY() + m.getBbHeight() + 0.5, m.getZ(), 3, 0.1, 0.05, 0.1, 0);
        }
        for (Projectile p : QuincyAbsorption.enemyProjectiles(l, e, 16)) {
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.01) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.position());
            if (v.normalize().dot(to.normalize()) < 0.8) continue;
            Vec3 pos = p.position();
            for (int i = 1; i <= 6; i++) {
                Vec3 q = pos.add(v.scale(i * 2));
                l.sendParticles(WARN, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Dano chegando em quem está no Todo-Poderoso: o golpe pesado é reescrito (anulado e devolvido) a cada 5 s; os
     * outros têm 45% de chance de esquiva. Cancela o evento quando age.
     */
    static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity holder = e.getEntity();
        State s = ALMIGHTY.get(holder.getUUID());
        if (s == null || !(e.getSource().getEntity() instanceof LivingEntity attacker) || attacker == holder) return;
        float amount = e.getAmount();
        long now = s.level.getGameTime();
        boolean heavy = amount >= holder.getMaxHealth() * 0.2F || amount >= holder.getHealth();
        if (heavy && now >= s.nextRewrite) {
            e.setCanceled(true);
            s.nextRewrite = now + REWRITE_COOLDOWN;
            rewrite(s, holder, attacker, amount);
            return;
        }
        if (holder.getRandom().nextFloat() < DODGE_CHANCE) {
            e.setCanceled(true);
            dodge(s.level, holder, attacker);
        }
    }

    /** O futuro é reescrito: o golpe volta para quem atacou. */
    private static void rewrite(State s, LivingEntity holder, LivingEntity attacker, float amount) {
        ServerLevel l = s.level;
        Vec3 h = holder.position().add(0, holder.getBbHeight() * 0.6, 0);
        Vec3 a = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
        QuincyFx.line(l, h, a, EYE, 0.3);
        QuincyFx.line(l, h, a, QuincyFx.WHITE, 0.5);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, a.x, a.y, a.z, 30, 0.4, 0.5, 0.4, 0.1);
        QuincyFx.sound(l, h, SoundEvents.ENDER_EYE_DEATH, 1.0F, 1.6F);
        QuincyFx.sound(l, a, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.6F);
        if (attacker.isAlive() && !s.ctx.isAlly(attacker)) {
            s.ctx.damage(attacker, Math.min(amount, 60F) / Math.max(1F, SpellCost.damageScale()), s.ctx.formStats(), holder);
            if (attacker.isAlive()) s.ctx.resolveOnEntity(attacker, holder);
        }
        if (holder instanceof ServerPlayer sp) sp.displayClientMessage(Quincy.msg("almighty_rewrite").withStyle(ChatFormatting.AQUA), true);
    }

    /** Esquiva: um passo para o lado (se houver espaço), deixando uma imagem residual. */
    private static void dodge(ServerLevel l, LivingEntity holder, LivingEntity attacker) {
        Vec3 from = holder.position();
        Vec3 away = from.subtract(attacker.position());
        Vec3 side = new Vec3(-away.z, 0, away.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        if (holder.getRandom().nextBoolean()) side = side.scale(-1);
        Vec3 to = from.add(side.scale(1.6));
        if (l.noCollision(holder, holder.getBoundingBox().move(to.subtract(from)))) {
            holder.teleportTo(to.x, to.y, to.z);
        }
        for (int i = 0; i < 12; i++) {
            l.sendParticles(QuincyFx.WHITE, from.x, from.y + l.random.nextDouble() * holder.getBbHeight(), from.z, 1, 0.15, 0.05, 0.15, 0);
        }
        l.sendParticles(EYE, from.x, from.y + holder.getBbHeight() * 0.8, from.z, 2, 0.05, 0.05, 0.05, 0);
        QuincyFx.sound(l, from, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.8F);
    }

    // =====================================================================================
    //  Tick e limpeza
    // =====================================================================================
    static void tick(MinecraftServer server) {
        tickMap(server, LETZT, true);
        tickMap(server, ALMIGHTY, false);
    }

    private static void tickMap(MinecraftServer server, Map<UUID, State> map, boolean letzt) {
        if (map.isEmpty()) return;
        List<Runnable> after = new ArrayList<>();
        Iterator<Map.Entry<UUID, State>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> me = it.next();
            State s = me.getValue();
            Entity ent = s.level.getEntity(me.getKey());
            // trocou de dimensão: a forma acaba (com o preço)
            if (ent == null) ent = server.getPlayerList().getPlayer(me.getKey());
            if (!(ent instanceof LivingEntity e) || !e.isAlive() || e.level() != s.level || s.level.getGameTime() >= s.end) {
                it.remove();
                if (ent instanceof LivingEntity le) {
                    QuincyFxNet.form(le, QuincyFxNet.FORM_NONE, 0);
                    if (letzt) after.add(() -> finishLetzt(s, le, le.isAlive()));
                    else after.add(() -> QuincyFx.sound(s.level, le.position(), SoundEvents.ENDER_EYE_DEATH, 0.6F, 1.2F));
                }
                continue;
            }
            s.age++;
            if (letzt) letztTick(s, e);
            else almightyTick(s, e);
        }
        for (Runnable r : after) r.run();
    }

    /** Saiu do jogo: acaba já (o preço da Letzt Stil é cobrado mesmo assim). */
    static void forget(Entity e) {
        State s = LETZT.remove(e.getUUID());
        if (s != null && e instanceof LivingEntity le) finishLetzt(s, le, le.isAlive());
        ALMIGHTY.remove(e.getUUID());
    }

    static void clear() {
        LETZT.clear();
        ALMIGHTY.clear();
    }

    private QuincyFinalForms() {}
}
