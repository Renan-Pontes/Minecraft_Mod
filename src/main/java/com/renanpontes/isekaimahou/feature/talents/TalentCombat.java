package com.renanpontes.isekaimahou.feature.talents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.anim.DodgeService;
import com.renanpontes.isekaimahou.core.anim.GuardEvent;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * As PASSIVAS de combate dos talentos ({@link TalentStats}): os números (dano, resistência) e, na release 1.0, as
 * MECÂNICAS dos nós notáveis/finais e das especializações (ao abater, ao acertar um crítico, ao conjurar, ao aparar, com
 * a vida baixa, com invocações...). Tudo aqui só mexe em dano/cura/recurso de criaturas — nenhum efeito toca em blocos
 * (cidades são sagradas) e nada dá XP. Nada roda por tick: só em eventos de dano/abate/conjuração/guarda (e uma
 * conferida a cada 5 s para a regeneração e a barreira).
 * <p>
 * Tetos fixos, para a soma de talentos + especialização + Maestria não quebrar chefes: ver as constantes {@code MAX_*}.
 */
final class TalentCombat {
    // ---- tetos (o que passar disso não faz efeito) ----
    static final double MAX_DODGE = 0.25, MAX_RESIST = 0.5;
    static final float LIFESTEAL_PER_SECOND = 6F;
    /** Soma de TODOS os bônus de dano que se somam (antes de crítico/sorte) e multiplicador final de um golpe. */
    static final double MAX_BONUS = 1.0, MAX_MULT = 4.0;
    /** Teto do que a Sequência (golpes seguidos no mesmo alvo) soma no total — lutas de chefe ficam nela o tempo todo. */
    static final double MAX_COMBO_BONUS = 0.4;
    /** Teto do bônus próprio das magias (elemento + "todas as magias"), que multiplica à parte dos outros bônus. */
    static final double MAX_SPELL_BONUS = 0.6;
    static final double MAX_CRIT = 0.5, MAX_CRIT_DAMAGE = 1.0, MAX_LUCKY = 0.15, MAX_ECHO = 0.3, MAX_REFUND = 0.3,
            MAX_CULL = 0.12, MAX_MANA_SHIELD = 0.5, MAX_CRIT_CDR = 2.0, MAX_KILL_CDR = 5.0, MAX_KILL_CHARGE = 5.0,
            MAX_BARRIER = 20.0, MAX_RECKLESS = 0.3, MAX_WILD = 0.3, MAX_CHEAT_HEAL = 0.5;
    static final float MAX_THORNS = 8F, MAX_REFLECT = 20F, MAX_BURST = 60F, ECHO_PART = 0.5F, REFUND_PART = 0.5F;
    static final int MAX_COMBO = 5, MAX_FURY = 3, MAX_SURGE = 5;

    static final float CRIT_BASE = 1.5F;
    /** Vida do alvo abaixo disto = "Executar"; vida do jogador abaixo de {@link #LOW_HEALTH} = poder de vida baixa. */
    static final float EXECUTE_HEALTH = 0.3F, LOW_HEALTH = 0.35F, FULL_HEALTH = 0.9F, HIGH_MANA = 0.75F;
    static final double NEAR_SQR = 5 * 5, FAR_SQR = 12 * 12;
    static final int SECOND_WIND_COOLDOWN = 20 * 120, CHEAT_DEATH_COOLDOWN = 20 * 300, PROC_COOLDOWN = 10, COMBAT_TICKS = 200,
            ON_HIT_TICKS = 80, COMBO_TICKS = 80, FURY_TICKS = 160, CRIT_FURY_TICKS = 100, PARRY_TICKS = 120,
            REVENGE_TICKS = 80, DODGE_TICKS = 100, SURGE_TICKS = 120, ULT_TICKS = 300, MARK_TICKS = 160,
            FORTIFY_TICKS = 100, WARD_TICKS = 80, BOND_TICKS = 200;

    private static final class State {
        long lastProc = Long.MIN_VALUE, lastManaHit = Long.MIN_VALUE, secondWindAt = Long.MIN_VALUE, lastCombat = Long.MIN_VALUE;
        long stealSecond = Long.MIN_VALUE;
        float stolen;
        // release 1.0 — mecânicas
        long cheatAt = Long.MIN_VALUE, summonAt = Long.MIN_VALUE, critAt = Long.MIN_VALUE, lastCritProc = Long.MIN_VALUE,
                lastEcho = Long.MIN_VALUE, lastCastProc = Long.MIN_VALUE, comboAt = Long.MIN_VALUE;
        long furyUntil, critFuryUntil, parryUntil, revengeUntil, dodgeUntil, surgeUntil, ultUntil, markUntil, fortifyUntil, wardUntil;
        int furyStacks, surgeStacks, comboStacks, hits;
        UUID comboTarget, markTarget;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Dano causado pelos próprios talentos (eco, explosão, execução): não ganha bônus nem dispara outra mecânica. */
    private static boolean busy;
    /** Dentro da explosão de abate: quem morre nela não explode de novo (sem reação em cadeia). */
    private static boolean bursting;
    /** Condições do mod que a Praga pode passar adiante (nunca as que prendem: congelar, petrificar, atordoar...). */
    private static final java.util.Set<Statuses.Kind> SPREADABLE = java.util.EnumSet.of(Statuses.Kind.WET, Statuses.Kind.BURNING,
            Statuses.Kind.CHILLED, Statuses.Kind.SHOCKED, Statuses.Kind.BLEEDING, Statuses.Kind.CORRUPTED);

    static void init(IEventBus bus) {
        bus.addListener(TalentCombat::onIncoming);
        bus.addListener(TalentCombat::onDamagePre);
        bus.addListener(TalentCombat::onDamagePost);
        bus.addListener(TalentCombat::onDeath);
        bus.addListener(TalentCombat::onHeal);
        bus.addListener(TalentCombat::onSpellDamage);
        bus.addListener(TalentCombat::onSpellDamagePost);
        bus.addListener(TalentCombat::onSpellCast);
        bus.addListener(TalentCombat::onVanillaCrit);
        bus.addListener(TalentCombat::onParry);
        bus.addListener(TalentCombat::onBlock);
    }

    static void forget(UUID player) {
        STATES.remove(player);
    }

    static void clear() {
        STATES.clear();
    }

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    /** O jogador por trás do dano: ele mesmo, ou o dono da invocação/companheiro que bateu. */
    @Nullable
    private static ServerPlayer owner(@Nullable Entity attacker) {
        if (attacker instanceof ServerPlayer sp) return sp instanceof FakePlayer ? null : sp;
        if (attacker instanceof OwnableEntity own && own.getOwnerUUID() != null && attacker.getServer() != null) {
            return attacker.getServer().getPlayerList().getPlayer(own.getOwnerUUID());
        }
        return null;
    }

    // =====================================================================================
    //  Antes do dano: bônus de quem bate, esquiva e redução de quem apanha
    // =====================================================================================

    private static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getAmount() <= 0F) return;
        DamageSource src = event.getSource();
        boolean thorns = src.is(DamageTypes.THORNS);
        long now = victim.level().getGameTime();

        Entity attacker = src.getEntity();
        ServerPlayer owner = thorns ? null : owner(attacker);
        if (owner != null && owner != victim) {
            State st = state(owner);
            st.lastCombat = now;
            if (attacker != owner) st.summonAt = now;
            TalentStats s = Talents.stats(owner);
            if (!s.isEmpty() && !busy) {
                event.setAmount(event.getAmount() * attackMultiplier(owner, attacker != owner, victim, src, s, st, now));
            }
        }

        if (!(victim instanceof ServerPlayer vp) || vp instanceof FakePlayer || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        State st = state(vp);
        st.lastCombat = now;
        TalentStats s = Talents.stats(vp);
        if (s.isEmpty()) return;
        boolean foe = attacker != null && attacker != vp;
        // Esquiva: só de golpes de alguém (nunca de queda, fogo, fome...)
        double dodge = Math.min(MAX_DODGE, s.get(TalentStats.DODGE));
        if (dodge > 0 && foe && vp.getRandom().nextDouble() < dodge) {
            event.setCanceled(true);
            st.dodgeUntil = now + DODGE_TICKS;
            vp.level().playSound(null, vp.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.5F, 1.4F);
            return;
        }
        // A esquiva do próprio jogador (rolamento do núcleo) também arma o contra-golpe
        if (foe && s.get(TalentStats.DODGE_POWER) > 0 && DodgeService.isDodging(vp)) st.dodgeUntil = now + DODGE_TICKS;
        if (foe && s.get(TalentStats.REVENGE) > 0) st.revengeUntil = now + REVENGE_TICKS;

        float hp = vp.getHealth(), max = vp.getMaxHealth();
        double resist = s.get(TalentStats.DAMAGE_RESIST);
        boolean spell = src.is(CoreRegistries.SPELL_DAMAGE);
        if (spell || src.is(DamageTypeTags.WITCH_RESISTANT_TO)) resist += s.get(TalentStats.MAGIC_RESIST);
        if (src.is(DamageTypeTags.IS_PROJECTILE)) resist += s.get(TalentStats.PROJECTILE_RESIST);
        if (src.is(DamageTypeTags.IS_FALL)) resist += s.get(TalentStats.FALL_RESIST);
        if (src.is(DamageTypeTags.IS_FIRE)) resist += s.get(TalentStats.FIRE_RESIST);
        if (melee(src) && !spell) resist += s.get(TalentStats.MELEE_RESIST);
        if (hp <= max * LOW_HEALTH) resist += s.get(TalentStats.LOW_HEALTH_RESIST);
        if (hp >= max * FULL_HEALTH) resist += s.get(TalentStats.FULL_HEALTH_RESIST);
        if (now < st.fortifyUntil) resist += s.get(TalentStats.GUARD_FORTIFY);
        if (now < st.wardUntil) resist += s.get(TalentStats.CAST_WARD);
        if (st.summonAt != Long.MIN_VALUE && now - st.summonAt < BOND_TICKS) resist += s.get(TalentStats.SUMMON_BOND);
        double bossResist = s.get(TalentStats.BOSS_RESIST);
        if (bossResist > 0 && BossKit.isBossLike(attacker)) resist += bossResist;
        float amount = event.getAmount();
        if (resist > 0) amount *= (float) (1.0 - Math.min(MAX_RESIST, resist));
        // Temerário: o preço do dano a mais é apanhar mais (não entra no teto de resistência)
        double reckless = s.capped(TalentStats.RECKLESS);
        if (reckless > 0) amount *= (float) (1.0 + reckless * 0.5);
        event.setAmount(amount);
    }

    /** Golpe direto de uma criatura (não projétil, não explosão). */
    private static boolean melee(DamageSource src) {
        return src.getEntity() instanceof LivingEntity && src.getDirectEntity() == src.getEntity()
                && !src.is(DamageTypeTags.IS_PROJECTILE) && !src.is(DamageTypeTags.IS_EXPLOSION);
    }

    /** O alvo está sob algum efeito ruim (veneno, lentidão, condições do mod...) ou pegando fogo? */
    private static boolean afflicted(LivingEntity e) {
        if (e.isOnFire()) return true;
        for (MobEffectInstance fx : e.getActiveEffects()) {
            if (fx.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return true;
        }
        return false;
    }

    private static float attackMultiplier(ServerPlayer owner, boolean viaSummon, LivingEntity victim, DamageSource src,
                                          TalentStats s, State st, long now) {
        double bonus = s.get(TalentStats.ALL_DAMAGE);
        double boss = s.get(TalentStats.BOSS_DAMAGE);
        if (boss > 0 && BossKit.isBossLike(victim)) bonus += boss;
        if (viaSummon) {
            bonus += s.get(TalentStats.SUMMON_DAMAGE);
            if (now < st.markUntil && victim.getUUID().equals(st.markTarget)) bonus += s.get(TalentStats.SUMMON_MARK);
            return (float) (1.0 + Math.min(MAX_BONUS, bonus));
        }

        boolean spell = src.is(CoreRegistries.SPELL_DAMAGE);
        boolean direct = !spell && src.getDirectEntity() == owner;
        if (!spell && src.getDirectEntity() instanceof Projectile) bonus += s.get(TalentStats.RANGED_DAMAGE);
        if (direct) bonus += s.get(TalentStats.MELEE_DAMAGE);
        float selfHp = owner.getHealth(), selfMax = owner.getMaxHealth();
        if (selfHp <= selfMax * LOW_HEALTH) bonus += s.get(TalentStats.LOW_HEALTH_POWER);
        if (selfHp >= selfMax * FULL_HEALTH) bonus += s.get(TalentStats.FULL_HEALTH_POWER);
        float hp = victim.getHealth(), max = victim.getMaxHealth();
        if (hp <= max * EXECUTE_HEALTH) bonus += s.get(TalentStats.EXECUTE);
        if (hp >= max) bonus += s.get(TalentStats.FIRST_STRIKE);
        if (victim.getType().is(EntityTypeTags.UNDEAD)) bonus += s.get(TalentStats.UNDEAD_DAMAGE);
        double night = s.get(TalentStats.NIGHT_POWER), day = s.get(TalentStats.DAY_POWER);
        if ((night > 0 || day > 0) && owner.level().dimensionType().hasSkyLight()) {
            bonus += owner.level().isNight() ? night : day;
        }
        double back = s.get(TalentStats.BACKSTAB);
        if (back > 0 && direct && behind(owner, victim)) bonus += back;

        double near = s.get(TalentStats.NEAR_DAMAGE), far = s.get(TalentStats.FAR_DAMAGE);
        if (near > 0 || far > 0) {
            double d2 = owner.distanceToSqr(victim);
            if (d2 <= NEAR_SQR) bonus += near;
            else if (d2 >= FAR_SQR) bonus += far;
        }
        double aff = s.get(TalentStats.AFFLICTED_DAMAGE);
        if (aff > 0 && afflicted(victim)) bonus += aff;
        double high = s.get(TalentStats.HIGH_MANA_POWER);
        if (high > 0) {
            MagicData md = MagicApi.get(owner);
            if (md.mana() >= md.maxMana() * HIGH_MANA) bonus += high;
        }
        if (owner.isShiftKeyDown()) bonus += s.get(TalentStats.AIM_POWER);
        if (owner.isSprinting()) bonus += s.get(TalentStats.RUN_POWER);

        // Estados armados por outros eventos (abate, crítico, aparo, esquiva, conjuração, suprema)
        if (st.comboStacks > 0 && now - st.comboAt <= COMBO_TICKS && victim.getUUID().equals(st.comboTarget)) {
            bonus += Math.min(MAX_COMBO_BONUS, s.get(TalentStats.COMBO) * st.comboStacks);
        }
        if (now < st.furyUntil) bonus += s.get(TalentStats.KILL_FURY) * st.furyStacks;
        if (now < st.critFuryUntil) bonus += s.get(TalentStats.CRIT_FURY);
        if (now < st.revengeUntil) bonus += s.get(TalentStats.REVENGE);
        if (now < st.dodgeUntil) bonus += s.get(TalentStats.DODGE_POWER);
        if (now < st.surgeUntil) bonus += s.get(TalentStats.SURGE) * st.surgeStacks;
        if (now < st.ultUntil) bonus += s.get(TalentStats.ULT_POWER);
        if (now < st.parryUntil) {
            // o contra-golpe do aparo vale para UM golpe
            bonus += s.get(TalentStats.PARRY_POWER);
            st.parryUntil = 0;
        }
        bonus += s.capped(TalentStats.RECKLESS);

        double mult = 1.0 + Math.min(MAX_BONUS, bonus);
        double wild = s.capped(TalentStats.WILD);
        if (wild > 0) mult *= 1.0 - wild + owner.getRandom().nextDouble() * 3.0 * wild;

        // Crítico de talento (qualquer golpe ou magia), o "5º golpe" e o "golpe de sorte" do Apostador
        double critMult = CRIT_BASE + s.capped(TalentStats.CRIT_DAMAGE);
        double fifth = s.get(TalentStats.FIFTH_HIT);
        boolean flash = false;
        if (fifth > 0 && ++st.hits >= 5) {
            st.hits = 0;
            mult *= critMult + fifth;
            flash = true;
        } else {
            double crit = s.capped(TalentStats.CRIT_CHANCE);
            if (crit > 0 && owner.getRandom().nextDouble() < crit) {
                mult *= critMult;
                flash = true;
            }
        }
        double lucky = s.capped(TalentStats.LUCKY);
        if (lucky > 0 && owner.getRandom().nextDouble() < lucky) {
            mult *= 2.0;
            flash = true;
        }
        if (flash) {
            st.critAt = now;
            if (victim.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
                sl.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.5F, 1.25F);
            }
        }
        return (float) Math.min(MAX_MULT, mult);
    }

    /** O atacante está nas costas do alvo? */
    private static boolean behind(LivingEntity attacker, LivingEntity victim) {
        Vec3 to = attacker.position().subtract(victim.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() < 1.0E-4) return false;
        Vec3 look = Vec3.directionFromRotation(0, victim.getYHeadRot());
        return flat.normalize().dot(look) < -0.3;
    }

    /** Dano de magia: o bônus por elemento (fusões valem pelo melhor dos dois pais) e o de todas as magias. */
    private static void onSpellDamage(SpellDamageEvent.Pre event) {
        ServerPlayer sp = event.ctx().casterPlayer();
        if (sp == null || busy) return;
        TalentStats s = Talents.stats(sp);
        if (s.isEmpty()) return;
        double best = 0;
        Element el = event.element();
        if (el != null) {
            for (Element base : Element.BASE) {
                if (el.matches(base)) best = Math.max(best, s.get(TalentStats.SPELL_DAMAGE_PREFIX + base.getSerializedName()));
            }
        }
        best += s.get(TalentStats.SPELL_DAMAGE_ALL);
        if (best > 0) event.setAmount(event.getAmount() * (float) (1.0 + Math.min(MAX_SPELL_BONUS, best)));
    }

    /** Eco: a magia que acertou bate de novo, mais fraca. */
    private static void onSpellDamagePost(SpellDamageEvent.Post event) {
        ServerPlayer sp = event.ctx().casterPlayer();
        if (sp == null || busy || !event.landed() || event.killed() || event.dealt() <= 0F) return;
        double echo = Talents.stats(sp).capped(TalentStats.ECHO);
        if (echo <= 0) return;
        State st = state(sp);
        long now = sp.level().getGameTime();
        LivingEntity target = event.target();
        if (now - st.lastEcho < PROC_COOLDOWN || !target.isAlive() || sp.getRandom().nextDouble() >= echo) return;
        st.lastEcho = now;
        boolean was = busy;
        busy = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(event.source(), event.dealt() * ECHO_PART);
        } finally {
            busy = was;
        }
        burst(target, ParticleTypes.ENCHANTED_HIT, 8, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F);
    }

    /** Ao conjurar (só a conjuração do próprio jogador): Sobrecarga, suprema, proteção, cura e devolução de custo. */
    private static void onSpellCast(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        TalentStats s = Talents.stats(p);
        if (s.isEmpty()) return;
        State st = state(p);
        long now = p.level().getGameTime();
        ParsedSpell spell = event.getContext().spell();
        boolean ultimate = spell != null && spell.isUltimate();
        if (s.get(TalentStats.SURGE) > 0) {
            st.surgeStacks = now < st.surgeUntil ? Math.min(MAX_SURGE, st.surgeStacks + 1) : 1;
            st.surgeUntil = now + SURGE_TICKS;
        }
        if (ultimate && s.get(TalentStats.ULT_POWER) > 0) st.ultUntil = now + ULT_TICKS;
        if (s.get(TalentStats.CAST_WARD) > 0) st.wardUntil = now + WARD_TICKS;
        if (now - st.lastCastProc < 20) return;
        double heal = s.get(TalentStats.HEAL_ON_CAST);
        double refund = s.capped(TalentStats.CAST_REFUND);
        if (heal <= 0 && refund <= 0) return;
        st.lastCastProc = now;
        if (heal > 0 && p.isAlive()) p.heal((float) heal);
        if (refund > 0 && !ultimate && spell != null && spell.manaCost() > 0 && p.getRandom().nextDouble() < refund) {
            ClassResources.gain(p, spell.manaCost() * REFUND_PART);
            burst(p, ParticleTypes.END_ROD, 6, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5F);
        }
    }

    /** Crítico normal do Minecraft (golpe caindo): soma o dano crítico dos talentos e conta como crítico para as mecânicas. */
    private static void onVanillaCrit(CriticalHitEvent event) {
        if (!event.isCriticalHit() || !(event.getEntity() instanceof ServerPlayer sp)) return;
        TalentStats s = Talents.stats(sp);
        if (s.isEmpty()) return;
        state(sp).critAt = sp.level().getGameTime();
        double bonus = s.capped(TalentStats.CRIT_DAMAGE);
        if (bonus > 0) event.setDamageMultiplier(event.getDamageMultiplier() + (float) bonus);
    }

    // =====================================================================================
    //  Guarda: aparo e bloqueio
    // =====================================================================================

    private static void onParry(GuardEvent.Parry event) {
        ServerPlayer p = event.getPlayer();
        TalentStats s = Talents.stats(p);
        if (s.isEmpty()) return;
        State st = state(p);
        long now = p.level().getGameTime();
        if (s.get(TalentStats.PARRY_POWER) > 0) st.parryUntil = now + PARRY_TICKS;
        double heal = s.get(TalentStats.PARRY_HEAL), mana = s.get(TalentStats.PARRY_MANA);
        if (heal > 0) p.heal((float) heal);
        if (mana > 0) ClassResources.gain(p, (float) mana);
        guarded(p, s, st, now, event.getAttacker(), event.getDamage());
    }

    private static void onBlock(GuardEvent.Block event) {
        ServerPlayer p = event.getPlayer();
        TalentStats s = Talents.stats(p);
        if (s.isEmpty()) return;
        guarded(p, s, state(p), p.level().getGameTime(), event.getAttacker(), Math.max(0F, event.getDamage() - event.getReduced()));
    }

    /** Aparo ou bloqueio: fortalece a guarda e devolve parte do golpe segurado. */
    private static void guarded(ServerPlayer p, TalentStats s, State st, long now, @Nullable LivingEntity attacker, float held) {
        if (s.get(TalentStats.GUARD_FORTIFY) > 0) st.fortifyUntil = now + FORTIFY_TICKS;
        double reflect = s.get(TalentStats.GUARD_REFLECT);
        if (reflect > 0 && held > 0F && attacker != null && attacker != p && attacker.isAlive()) {
            attacker.hurt(p.damageSources().thorns(p), Math.min(MAX_REFLECT, held * (float) reflect));
        }
    }

    // =====================================================================================
    //  Dano final: Pele de Mana e Recusar a Morte (antes); roubo de vida / efeitos / espinhos / Segundo Fôlego (depois)
    // =====================================================================================

    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer vp) || vp instanceof FakePlayer || event.getNewDamage() <= 0F) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        TalentStats s = Talents.stats(vp);
        if (s.isEmpty()) return;
        double shield = s.capped(TalentStats.MANA_SHIELD);
        if (shield > 0) {
            // Parte do dano vira gasto de recurso (2 por ponto de vida); sem recurso, o dano passa inteiro
            float absorbed = event.getNewDamage() * (float) shield;
            MagicData md = MagicApi.get(vp);
            if (absorbed > 0.05F && md.consumeMana(absorbed * 2F)) event.setNewDamage(event.getNewDamage() - absorbed);
        }
        // Recusar a Morte: o golpe que mataria é anulado (uma vez a cada 5 min)
        double cheat = s.get(TalentStats.CHEAT_DEATH);
        if (cheat <= 0 || event.getNewDamage() < vp.getHealth()) return;
        State st = state(vp);
        long now = vp.level().getGameTime();
        if (st.cheatAt != Long.MIN_VALUE && now - st.cheatAt < CHEAT_DEATH_COOLDOWN) return;
        st.cheatAt = now;
        event.setNewDamage(0F);
        vp.setHealth(Math.max(vp.getHealth(), vp.getMaxHealth() * (float) Math.min(MAX_CHEAT_HEAL, cheat)));
        vp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, true, false, true));
        burst(vp, ParticleTypes.TOTEM_OF_UNDYING, 30, SoundEvents.TOTEM_USE, 1.2F);
        vp.displayClientMessage(Component.translatable("message.isekai_mahou.talents.cheat_death").withColor(0xFFD86B), true);
    }

    private static void onDamagePost(LivingDamageEvent.Post event) {
        float dealt = event.getNewDamage();
        LivingEntity victim = event.getEntity();
        if (dealt <= 0F || victim.level().isClientSide) return;
        DamageSource src = event.getSource();
        long now = victim.level().getGameTime();
        boolean thorns = src.is(DamageTypes.THORNS);

        Entity attacker = src.getEntity();
        if (!thorns && !busy && attacker != victim) {
            if (attacker instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
                TalentStats s = Talents.stats(p);
                if (!s.isEmpty()) afterHit(p, victim, dealt, now, s);
            } else if (attacker instanceof OwnableEntity) {
                // Elo de sangue: parte do dano das invocações cura o dono
                ServerPlayer p = owner(attacker);
                if (p != null && p != victim && p.isAlive()) steal(p, state(p), dealt, Talents.stat(p, TalentStats.SUMMON_LIFESTEAL), now);
            }
        }

        if (victim instanceof ServerPlayer vp && !(vp instanceof FakePlayer) && vp.isAlive()) {
            TalentStats s = Talents.stats(vp);
            if (s.isEmpty()) return;
            boolean struck = !thorns && melee(src) && attacker instanceof LivingEntity && attacker != vp && attacker.isAlive();
            if (struck) {
                LivingEntity foe = (LivingEntity) attacker;
                double th = s.get(TalentStats.THORNS);
                if (th > 0) foe.hurt(vp.damageSources().thorns(vp), Math.min(MAX_THORNS, dealt * (float) th));
                // Quem bate no jogador leva um efeito (uma rolagem a cada meio segundo)
                State st = state(vp);
                if (now - st.lastProc >= PROC_COOLDOWN && rollEffects(s, TalentStats.ON_STRUCK_PREFIX, vp, foe)) st.lastProc = now;
            }
            double wind = s.get(TalentStats.SECOND_WIND);
            State st = state(vp);
            if (wind > 0 && vp.getHealth() <= vp.getMaxHealth() * EXECUTE_HEALTH && now - st.secondWindAt >= SECOND_WIND_COOLDOWN) {
                st.secondWindAt = now;
                // A absorção só "cabe" enquanto o efeito existe (ele é que sobe o limite): efeito primeiro, valor depois
                float before = vp.getAbsorptionAmount();
                int amp = Math.max(0, Mth.ceil(wind / 4.0) - 1);
                vp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 20, amp, true, false, true));
                vp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 0, true, false, true));
                vp.setAbsorptionAmount(Math.max(before, (float) wind));
                vp.level().playSound(null, vp.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.25F, 1.6F);
            }
        }
    }

    /** Cura por dano causado, com o teto por segundo (dividido entre roubo de vida, elo das invocações e cura de crítico). */
    private static void steal(ServerPlayer p, State st, float dealt, double fraction, long now) {
        if (fraction <= 0 || !p.isAlive()) return;
        long second = now / 20;
        if (st.stealSecond != second) {
            st.stealSecond = second;
            st.stolen = 0F;
        }
        float heal = Math.min(dealt * (float) fraction, LIFESTEAL_PER_SECOND - st.stolen);
        if (heal > 0.01F) {
            st.stolen += heal;
            p.heal(heal);
        }
    }

    private static void afterHit(ServerPlayer p, LivingEntity victim, float dealt, long now, TalentStats s) {
        State st = state(p);
        steal(p, st, dealt, s.get(TalentStats.LIFESTEAL), now);

        // Sequência no mesmo alvo e marca para as invocações
        if (s.get(TalentStats.COMBO) > 0) {
            boolean same = victim.getUUID().equals(st.comboTarget) && now - st.comboAt <= COMBO_TICKS;
            st.comboStacks = same ? Math.min(MAX_COMBO, st.comboStacks + 1) : 1;
            st.comboTarget = victim.getUUID();
            st.comboAt = now;
        }
        if (s.get(TalentStats.SUMMON_MARK) > 0) {
            st.markTarget = victim.getUUID();
            st.markUntil = now + MARK_TICKS;
        }

        // Este golpe foi crítico (de talento, de sorte ou do Minecraft): mecânicas "ao acertar um crítico"
        if (st.critAt == now) {
            if (s.get(TalentStats.CRIT_FURY) > 0) st.critFuryUntil = now + CRIT_FURY_TICKS;
            if (now - st.lastCritProc >= 20) {
                st.lastCritProc = now;
                double mana = s.get(TalentStats.CRIT_MANA), heal = s.get(TalentStats.CRIT_HEAL), cdr = s.capped(TalentStats.CRIT_CDR);
                if (mana > 0) ClassResources.gain(p, (float) mana);
                if (heal > 0) steal(p, st, (float) heal, 1.0, now);
                if (cdr > 0) reduceCooldowns(p, cdr, now);
            }
        }

        double manaHit = s.get(TalentStats.MANA_ON_HIT);
        if (manaHit > 0 && now - st.lastManaHit >= PROC_COOLDOWN) {
            st.lastManaHit = now;
            ClassResources.gain(p, (float) manaHit);
        }

        // Execução: inimigo comum quase morto cai na hora (chefes e jogadores nunca)
        double cull = s.capped(TalentStats.CULL);
        if (cull > 0 && victim.isAlive() && !(victim instanceof Player) && victim.getHealth() <= victim.getMaxHealth() * cull
                && !BossKit.isBossLike(victim)) {
            boolean was = busy;
            busy = true;
            try {
                victim.invulnerableTime = 0;
                victim.hurt(p.damageSources().indirectMagic(p, p), victim.getMaxHealth() * 20F + 1000F);
            } finally {
                busy = was;
            }
            burst(victim, ParticleTypes.SWEEP_ATTACK, 3, SoundEvents.PLAYER_ATTACK_STRONG, 0.7F);
            return;
        }

        // Efeitos ao acertar: no máximo uma rolagem a cada meio segundo (magias em área não viram metralhadora)
        if (now - st.lastProc < PROC_COOLDOWN || !victim.isAlive()) return;
        if (rollEffects(s, TalentStats.ON_HIT_PREFIX, p, victim)) st.lastProc = now;
    }

    /** Rola as passivas {@code <prefixo><efeito>} do jogador contra o alvo. Devolve se havia alguma para rolar. */
    private static boolean rollEffects(TalentStats s, String prefix, ServerPlayer p, LivingEntity target) {
        boolean rolled = false;
        for (Map.Entry<String, Double> e : s.all().entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            rolled = true;
            if (p.getRandom().nextDouble() >= e.getValue()) continue;
            String kind = e.getKey().substring(prefix.length());
            switch (kind) {
                case "burn" -> {
                    if (!target.fireImmune()) target.igniteForSeconds(3F);
                }
                case "bleed" -> Statuses.apply(target, Statuses.Kind.BLEEDING, 1, ON_HIT_TICKS + 20, p, 1F);
                case "chill" -> Statuses.apply(target, Statuses.Kind.CHILLED, 1, ON_HIT_TICKS + 20, p, 1F);
                case "shock" -> Statuses.apply(target, Statuses.Kind.SHOCKED, 1, ON_HIT_TICKS + 20, p, 1F);
                default -> {
                    Holder<MobEffect> effect = switch (kind) {
                        case "poison" -> MobEffects.POISON;
                        case "slowness" -> MobEffects.MOVEMENT_SLOWDOWN;
                        case "weakness" -> MobEffects.WEAKNESS;
                        case "wither" -> MobEffects.WITHER;
                        default -> null;
                    };
                    if (effect != null) target.addEffect(new MobEffectInstance(effect, ON_HIT_TICKS, 0), p);
                }
            }
        }
        return rolled;
    }

    /** Adianta TODAS as recargas do jogador em {@code seconds} (e avisa o cliente de cada uma). */
    private static void reduceCooldowns(ServerPlayer p, double seconds, long now) {
        MagicData md = MagicApi.get(p);
        if (md.cooldowns.isEmpty()) return;
        long cut = Math.round(seconds * 20.0);
        for (Map.Entry<String, Long> e : md.cooldowns.entrySet()) {
            long left = e.getValue() - now;
            if (left <= 0) continue;
            long newLeft = Math.max(0, left - cut);
            e.setValue(now + newLeft);
            PacketDistributor.sendToPlayer(p, new CorePayloads.Cooldown(e.getKey(), (int) newLeft));
        }
    }

    /** Inimigo de verdade do jogador (monstro, ou criatura que o está atacando) — nunca jogadores, NPCs ou mascotes. */
    private static boolean foe(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e instanceof Player || e instanceof OwnableEntity own && own.getOwnerUUID() != null) return false;
        return e instanceof Enemy || e instanceof Mob m && m.getTarget() == p;
    }

    private static void burst(LivingEntity at, ParticleOptions particle, int count, SoundEvent sound, float pitch) {
        if (!(at.level() instanceof ServerLevel sl)) return;
        sl.sendParticles(particle, at.getX(), at.getY(0.5), at.getZ(), count, 0.35, 0.4, 0.35, 0.05);
        sl.playSound(null, at.blockPosition(), sound, SoundSource.PLAYERS, 0.45F, pitch);
    }

    // =====================================================================================
    //  Abate, cura recebida e regeneração fora de combate
    // =====================================================================================

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;
        ServerPlayer p = owner(event.getSource().getEntity());
        if (p == null || p == dead || !p.isAlive()) return;
        TalentStats s = Talents.stats(p);
        if (s.isEmpty()) return;
        State st = state(p);
        long now = dead.level().getGameTime();
        double mana = s.get(TalentStats.MANA_ON_KILL);
        if (mana > 0) ClassResources.gain(p, (float) mana);
        double heal = s.get(TalentStats.HEAL_ON_KILL);
        if (heal > 0) p.heal((float) heal);
        double speed = s.get(TalentStats.KILL_SPEED);
        if (speed > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Mth.ceil(speed * 20), 0, true, false, true));
        double charge = s.capped(TalentStats.CHARGE_ON_KILL);
        if (charge > 0) MagicApi.get(p).addCharge((float) charge);
        double cdr = s.capped(TalentStats.KILL_CDR);
        if (cdr > 0) reduceCooldowns(p, cdr, now);
        if (s.get(TalentStats.KILL_FURY) > 0) {
            st.furyStacks = now < st.furyUntil ? Math.min(MAX_FURY, st.furyStacks + 1) : 1;
            st.furyUntil = now + FURY_TICKS;
        }
        if (bursting || !(dead.level() instanceof ServerLevel)) return;

        // Explosão e praga: procuram vizinhos SÓ no momento do abate
        double burstPart = s.get(TalentStats.KILL_BURST);
        double plague = s.capped(TalentStats.PLAGUE);
        boolean spread = plague > 0 && afflicted(dead) && p.getRandom().nextDouble() < plague;
        if (burstPart <= 0 && !spread) return;
        List<LivingEntity> near = dead.level().getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(4.5),
                e -> e != dead && foe(p, e));
        if (near.isEmpty()) return;
        if (spread) {
            List<MobEffectInstance> bad = new ArrayList<>();
            for (MobEffectInstance fx : dead.getActiveEffects()) {
                if (fx.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(fx);
            }
            for (LivingEntity e : near) {
                for (MobEffectInstance fx : bad) {
                    Statuses.Kind kind = Statuses.Kind.of(fx.getEffect());
                    if (kind != null) {
                        if (SPREADABLE.contains(kind)) Statuses.apply(e, kind, 1, ON_HIT_TICKS + 20, p, 1F);
                    } else if (fx.getEffect().unwrapKey().map(k -> "minecraft".equals(k.location().getNamespace())).orElse(false)) {
                        // só os efeitos do Minecraft (veneno, lentidão, fraqueza, decomposição...): os de outros mods ficam
                        e.addEffect(new MobEffectInstance(fx.getEffect(), Mth.clamp(fx.getDuration(), 60, 200), Math.min(1, fx.getAmplifier())), p);
                    }
                }
                if (dead.isOnFire() && !e.fireImmune()) e.igniteForSeconds(4F);
            }
            burst(dead, ParticleTypes.WITCH, 16, SoundEvents.WITCH_CELEBRATE, 0.7F);
        }
        if (burstPart > 0) {
            float dmg = Math.min(MAX_BURST, dead.getMaxHealth() * (float) burstPart);
            boolean was = busy;
            busy = true;
            bursting = true;
            try {
                for (LivingEntity e : near) e.hurt(p.damageSources().indirectMagic(p, p), dmg);
            } finally {
                busy = was;
                bursting = false;
            }
            burst(dead, ParticleTypes.EXPLOSION, 2, SoundEvents.GENERIC_EXPLODE.value(), 1.5F);
        }
    }

    private static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getAmount() <= 0F) return;
        double bonus = Talents.stat(p, TalentStats.HEALING_RECEIVED);
        if (bonus > 0) event.setAmount(event.getAmount() * (float) (1.0 + bonus));
    }

    /** A cada 5 s: regeneração e barreira dos talentos, só fora de combate. */
    static void slowTick(ServerPlayer p) {
        TalentStats s = Talents.stats(p);
        if (s.isEmpty() || !p.isAlive()) return;
        double regen = s.get(TalentStats.REGEN_HEALTH), barrier = s.capped(TalentStats.BARRIER);
        if (regen <= 0 && barrier <= 0) return;
        State st = STATES.get(p.getUUID());
        long now = p.level().getGameTime();
        if (st != null && st.lastCombat != Long.MIN_VALUE && now - st.lastCombat < COMBAT_TICKS) return;
        if (regen > 0 && p.getHealth() < p.getMaxHealth()) p.heal((float) regen);
        // A barreira "cabe" porque o nó também sobe o atributo de absorção máxima
        if (barrier > 0 && p.getAbsorptionAmount() < barrier) p.setAbsorptionAmount((float) barrier);
    }

    private TalentCombat() {}
}
