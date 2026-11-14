package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.feature.transform_laws.TransformForge;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * As duas formas finais do Quincy (formas G), estado só em memória:
 * <ul>
 *   <li><b>Quincy: Letzt Stil</b> (homenagem à "forma final" do Quincy): 30 s de poder ENORME — técnicas Quincy ×2,
 *   Força II, Velocidade II, Resistência I, Regeneração I, plana, engole todo projétil a 8 blocos e a cada 0,75 s um
 *   leque de 6 flechas teleguiadas gigantes cai sobre os inimigos (os efeitos da magia). O PREÇO: ao acabar, a vida cai
 *   para 50%, Fraqueza II e 10 minutos SEM PODER QUINCY;</li>
 *   <li><b>O Todo-Poderoso</b> (homenagem a "The Almighty", Letra A) — v1.1, corpo da Transformação Suprema: por
 *   5 minutos você VÊ O FUTURO — todo ataque de uma criatura é desviado; com o contra-ataque pronto você aparece atrás
 *   de quem atacou, golpeia e diz "Eu vi tudo..." (cada contra-ataque custa 30 s da transformação; depois, 5 s só
 *   desviando). 10 contra-ataques ou o tempo acabar = fim. Quem vai atacar você brilha e mostra a trajetória antes.</li>
 * </ul>
 * v1.1: as duas são CORPOS de transformação ({@code feature/transform_laws/TransformBody}): começam dentro da forma
 * Transformação Suprema e acabam junto com ela ({@link #stopLetzt}, {@link #stopAlmighty}).
 */
public final class QuincyFinalForms {
    public static final ResourceLocation GRAVITY = IsekaiMahou.id("quincy_letzt_stil_gravity");
    public static final float LETZT_POWER = 2.0F;
    /** O Todo-Poderoso: alcance do contra-ataque (blocos até quem atacou). */
    public static final double COUNTER_RANGE = 24.0;
    /** Dano base do golpe do contra-ataque (× poder do corpo, como um efeito de dano). */
    public static final float COUNTER_DAMAGE = 14F;
    /** Cada contra-ataque tira 30 s da transformação. */
    public static final int COUNTER_COST = 30 * 20;
    /** Depois de um contra-ataque: 5 s só desviando (sem teleportar). */
    public static final int COUNTER_GAP = 5 * 20;
    /** 10 contra-ataques = fim (5 min / 30 s). */
    public static final int MAX_COUNTERS = 10;
    /** Fala do contra-ataque (balão sobre a cabeça). */
    public static final String SAW_KEY = "message.isekai_mahou.quincy.almighty_saw";
    private static final DustParticleOptions GOLD = QuincyFx.dust(0xFFE89A, 1.2F);
    private static final DustParticleOptions EYE = QuincyFx.dust(0x101018, 1.0F);
    private static final DustParticleOptions WARN = QuincyFx.dust(0xFF3A3A, 0.8F);

    private static final class State {
        final SpellContext ctx;
        final ServerLevel level;
        /** Fim (tick do mundo). O Todo-Poderoso encurta a cada contra-ataque. */
        long end;
        final float radius;
        int age;
        /** Aumentos do corpo (poder do golpe do contra-ataque). */
        SpellStats stats = new SpellStats();
        /** O Todo-Poderoso: próximo contra-ataque permitido / próxima esquiva com efeito visual. */
        long nextCounter, nextDodge;
        int counters;

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
        if (caster instanceof ServerPlayer sp && !ctx.formStats().silent && !com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.inForge(ctx)) {
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

    /**
     * v1.1: a Transformação que tinha a Letzt Stil como corpo acabou (tempo, reconjurou, comando) — as asas se desfazem
     * já, e o PREÇO é cobrado (não dá para fugir dele encerrando a transformação antes).
     */
    public static void stopLetzt(LivingEntity e) {
        State s = LETZT.remove(e.getUUID());
        if (s == null) return;
        QuincyFxNet.form(e, QuincyFxNet.FORM_NONE, 0);
        finishLetzt(s, e, e.isAlive());
    }

    // =====================================================================================
    //  O Todo-Poderoso (v1.1: corpo de transformação — 5 min, contra-ataques "Eu vi tudo...")
    // =====================================================================================
    /**
     * Começa O Todo-Poderoso em quem conjura. Dentro da Transformação Suprema, a sessão dura o mesmo tempo e o ícone do
     * efeito "Transformação" mostra quanto falta.
     */
    public static void startAlmighty(SpellContext ctx, SpellStats stats, int ticks) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        State s = new State(ctx, level, level.getGameTime() + ticks, 24F);
        s.stats = stats;
        ALMIGHTY.put(caster.getUUID(), s);
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
        if (caster instanceof ServerPlayer sp && !ctx.formStats().silent && !com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.inForge(ctx)) {
            Announce.send(sp, Quincy.msg("almighty_shout", sp.getDisplayName()), 0x1A1A2A, Announce.STYLE_ULTIMATE);
        }
    }

    /**
     * Acaba O Todo-Poderoso. {@code endTransformation}: foi o próprio corpo que acabou (tempo ou 10 contra-ataques) —
     * a Transformação inteira acaba junto. false = a Transformação acabou por fora (não chama de volta).
     */
    public static void stopAlmighty(LivingEntity e, boolean endTransformation) {
        State s = ALMIGHTY.remove(e.getUUID());
        if (s != null) almightyGone(s, e, endTransformation);
    }

    /** Os olhos se fecham (estado já tirado do mapa). */
    private static void almightyGone(State s, LivingEntity e, boolean endTransformation) {
        QuincyFxNet.form(e, QuincyFxNet.FORM_NONE, 0);
        QuincyFx.sound(s.level, e.position(), SoundEvents.ENDER_EYE_DEATH, 0.6F, 1.2F);
        if (e instanceof ServerPlayer sp) {
            if (e.isAlive()) sp.displayClientMessage(Quincy.msg("almighty_end").withStyle(ChatFormatting.GRAY), true);
            if (endTransformation) TransformForge.endCast(sp);
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
     * Ataque chegando em quem está no Todo-Poderoso. TODO ataque vindo de uma criatura (corpo a corpo, projétil, magia)
     * é anulado — vazio, /kill e o ambiente (lava, queda, fogo, afogar) continuam valendo. Com o contra-ataque pronto:
     * aparece ATRÁS de quem atacou (até {@value #COUNTER_RANGE} blocos, num lugar seguro), golpeia uma vez
     * ({@value #COUNTER_DAMAGE} × poder, também em chefes, sem quebrar blocos), diz "Eu vi tudo..." e a transformação
     * perde {@value #COUNTER_COST} ticks; depois, {@value #COUNTER_GAP} ticks só desviando (de graça).
     * {@value #MAX_COUNTERS} contra-ataques = fim da transformação.
     */
    static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity holder = e.getEntity();
        State s = ALMIGHTY.get(holder.getUUID());
        if (s == null) return;
        DamageSource src = e.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity by = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
        if (by == null || by == holder) return;
        e.setCanceled(true);
        long now = s.level.getGameTime();
        LivingEntity attacker = src.getEntity() instanceof LivingEntity le && le != holder ? le : null;
        if (attacker != null && attacker.isAlive() && now >= s.nextCounter && holder.level() == attacker.level()
                && holder.distanceToSqr(attacker) <= COUNTER_RANGE * COUNTER_RANGE && !s.ctx.isAlly(attacker)) {
            Vec3 spot = behind(s.level, holder, attacker);
            if (spot != null) {
                counter(s, holder, attacker, spot, now);
                return;
            }
        }
        // sem contra-ataque (recarga de 5 s, longe demais, sem lugar seguro): só desvia, sem custo
        if (now >= s.nextDodge) {
            s.nextDodge = now + 8;
            dodge(s.level, holder, by);
        }
    }

    /** O contra-ataque: some, aparece atrás de quem atacou olhando para ele e golpeia. */
    private static void counter(State s, LivingEntity holder, LivingEntity attacker, Vec3 spot, long now) {
        ServerLevel l = s.level;
        // o preço: 30 s a menos; 5 s sem teleportar (só desvia). Marcado ANTES do golpe: se quem atacou também estiver
        // no Todo-Poderoso, o contra-ataque dele cai no "só desvia" daqui (sem ping-pong infinito)
        s.counters++;
        s.nextCounter = now + COUNTER_GAP;
        s.end -= COUNTER_COST;
        Vec3 from = holder.position();
        // a imagem que fica para trás
        for (int i = 0; i < 14; i++) {
            l.sendParticles(QuincyFx.WHITE, from.x, from.y + l.random.nextDouble() * holder.getBbHeight(), from.z, 1, 0.15, 0.05, 0.15, 0);
        }
        l.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y + 1, from.z, 10, 0.25, 0.4, 0.25, 0.02);
        Vec3 look = attacker.getEyePosition().subtract(spot.add(0, holder.getEyeHeight(), 0));
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        if (holder instanceof ServerPlayer sp) {
            sp.teleportTo(l, spot.x, spot.y, spot.z, yaw, pitch);
        } else {
            holder.teleportTo(spot.x, spot.y, spot.z);
            holder.setYRot(yaw);
            holder.setYHeadRot(yaw);
            holder.setXRot(pitch);
        }
        holder.fallDistance = 0;
        holder.setDeltaMovement(Vec3.ZERO);
        holder.hurtMarked = true;
        holder.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        // o golpe (dano de magia: vale em chefes; nada de blocos)
        attacker.invulnerableTime = 0;
        s.ctx.damage(attacker, COUNTER_DAMAGE, s.stats, holder);
        Vec3 a = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
        QuincyFx.line(l, spot.add(0, holder.getBbHeight() * 0.6, 0), a, EYE, 0.3);
        l.sendParticles(ParticleTypes.SWEEP_ATTACK, a.x, a.y, a.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.CRIT, a.x, a.y, a.z, 16, 0.3, 0.3, 0.3, 0.4);
        QuincyFxNet.burst(holder, QuincyFxNet.EYES, a, Vec3.ZERO, 1.6F, 0x6FA8FF);
        QuincyFx.sound(l, spot, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.6F);
        QuincyFx.sound(l, a, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.7F);
        QuincyFx.sound(l, a, SoundEvents.ENDER_EYE_DEATH, 0.8F, 1.6F);
        // "Eu vi tudo..." — balão de fala, como o cântico (sem chat)
        Announce.chant(holder, net.minecraft.locale.Language.getInstance().getOrDefault(SAW_KEY, "Eu vi tudo..."), 40);
        if (ALMIGHTY.get(holder.getUUID()) != s) return; // o golpe encerrou o estado (morte do outro lado, comando...)
        int left = (int) Math.max(0, s.end - now);
        if (s.counters >= MAX_COUNTERS || left <= 0) {
            stopAlmighty(holder, true);
            return;
        }
        QuincyFxNet.form(holder, QuincyFxNet.FORM_ALMIGHTY, left);
        if (holder instanceof ServerPlayer sp) {
            TransformForge.setCastRemaining(sp, left);
            sp.displayClientMessage(Quincy.msg("almighty_counter", MAX_COUNTERS - s.counters, left / 20 / 60,
                    String.format(java.util.Locale.ROOT, "%02d", left / 20 % 60)).withColor(0x9FC4FF), true);
        }
    }

    /**
     * Um lugar seguro bem ATRÁS de quem atacou (as costas dele), sem atravessar blocos e com chão a até 4 blocos.
     * null = não há (aí só desvia).
     */
    @Nullable
    private static Vec3 behind(ServerLevel l, LivingEntity holder, LivingEntity att) {
        Vec3 fwd = att.getLookAngle().multiply(1, 0, 1);
        if (fwd.lengthSqr() < 1.0E-4) fwd = att.position().subtract(holder.position()).multiply(1, 0, 1).scale(-1);
        if (fwd.lengthSqr() < 1.0E-4) fwd = new Vec3(0, 0, 1);
        fwd = fwd.normalize();
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        double back = att.getBbWidth() * 0.5 + holder.getBbWidth() * 0.5 + 0.8;
        Vec3[] offs = {fwd.scale(-back), fwd.scale(-back).add(side.scale(0.9)), fwd.scale(-back).add(side.scale(-0.9)),
                fwd.scale(-back - 1.2), side.scale(back), side.scale(-back)};
        for (Vec3 o : offs) {
            for (double dy : new double[] {0, 1, -1}) {
                Vec3 p = att.position().add(o).add(0, dy, 0);
                AABB box = holder.getBoundingBox().move(p.subtract(holder.position()));
                if (!l.noCollision(holder, box) || !l.getWorldBorder().isWithinBounds(BlockPos.containing(p))) continue;
                if (!grounded(l, p)) continue;
                return p;
            }
        }
        return null;
    }

    private static boolean grounded(ServerLevel l, Vec3 p) {
        BlockPos at = BlockPos.containing(p.x, p.y + 0.01, p.z);
        for (int k = 1; k <= 4; k++) {
            BlockPos b = at.below(k);
            if (!l.getBlockState(b).getCollisionShape(l, b).isEmpty()) return true;
        }
        return false;
    }

    /** Esquiva: um passo para o lado (se houver espaço), deixando uma imagem residual. */
    private static void dodge(ServerLevel l, LivingEntity holder, Entity attacker) {
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
                    // v1.1: o tempo do Todo-Poderoso É o da transformação — acabou um, acaba o outro
                    else after.add(() -> almightyGone(s, le, true));
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
