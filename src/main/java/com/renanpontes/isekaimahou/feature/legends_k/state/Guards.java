package com.renanpontes.isekaimahou.feature.legends_k.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkGlyphs;
import com.renanpontes.isekaimahou.feature.legends_k.LkNet;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * As DEFESAS do legends_k (estado do servidor em mapas, tudo some ao sair do jogo):
 * <ul>
 *   <li><b>Ponto Zero</b> (Réquiem, Steel Ball): o próximo ataque OU técnica vinda de alguém é anulado e quem atacou
 *       "volta a zero" (ação cancelada, Atordoado, empurrado). Um uso; recarga longa ({@link #ZERO_LOCK}).</li>
 *   <li><b>Santen Kesshun</b> (Orihime, Encantador): escudo triangular que bloqueia N golpes.</li>
 *   <li><b>Calmaria</b> (Respiração da Água, 11ª forma, Cavaleiro): por 3 s, todo golpe corpo a corpo e projétil que
 *       chega é aparado e quem atacou leva o corte de volta. Você fica lento enquanto isso.</li>
 *   <li><b>Reflexão de Vetores</b> (Accelerator, Mago): projéteis voltam para quem atirou; golpes corpo a corpo perdem
 *       40% e o empurrão vai para o atacante.</li>
 *   <li><b>Roda de Mahoraga</b> (Bruxo): cada golpe do MESMO tipo de dano gira a roda — −20% por giro (até −80%).</li>
 *   <li><b>Retorno pela Morte</b> (Re:Zero, Bruxa): um ponto de retorno; se você morrer dentro do prazo, volta para lá
 *       com a vida que tinha. Depois de usado, só de novo em 10 min ({@link #RBD_LOCK}).</li>
 * </ul>
 */
public final class Guards {
    /** Recarga do Ponto Zero (a partir de quando é armado). */
    public static final int ZERO_LOCK = 1200;
    /** Recarga do Retorno pela Morte (a partir de quando salva você). */
    public static final int RBD_LOCK = 12000;

    // ---------- Ponto Zero ----------
    private static final Map<UUID, Long> ZERO = new HashMap<>();
    private static final Map<UUID, Long> ZERO_LOCKED = new HashMap<>();
    // ---------- Santen Kesshun ----------
    private record Shield(int charges, long until) {}

    private static final Map<UUID, Shield> SHIELD = new HashMap<>();
    // ---------- Calmaria ----------
    private record Calm(long until, float power, UUID caster) {}

    private static final Map<UUID, Calm> CALM = new HashMap<>();
    // ---------- Vetores ----------
    private static final Map<UUID, Long> VECTOR = new HashMap<>();
    // ---------- Mahoraga ----------
    private static final class Wheel {
        long until;
        final Map<String, Integer> turns = new HashMap<>();
    }

    private static final Map<UUID, Wheel> WHEEL = new HashMap<>();
    // ---------- Retorno pela Morte ----------
    private record Checkpoint(ResourceKey<Level> level, Vec3 pos, float yRot, float xRot, float health, int food, long until) {}

    private static final Map<UUID, Checkpoint> RBD = new HashMap<>();
    private static final Map<UUID, Long> RBD_LOCKED = new HashMap<>();

    private static final ThreadLocal<Boolean> COUNTERING = ThreadLocal.withInitial(() -> false);

    // =====================================================================================
    //  Armar
    // =====================================================================================

    public static void armZero(LivingEntity e, int ticks) {
        ZERO.put(e.getUUID(), e.level().getGameTime() + ticks);
    }

    /** A recarga longa vale para quem CONJUROU (mesmo que tenha armado um aliado). */
    public static void lockZero(LivingEntity caster) {
        ZERO_LOCKED.put(caster.getUUID(), caster.level().getGameTime() + ZERO_LOCK);
    }

    public static boolean zeroArmed(Entity e) {
        Long until = ZERO.get(e.getUUID());
        return until != null && e.level().getGameTime() < until;
    }

    public static void shield(LivingEntity e, int charges, int ticks) {
        SHIELD.put(e.getUUID(), new Shield(charges, e.level().getGameTime() + ticks));
    }

    public static void calm(LivingEntity e, int ticks, float power) {
        CALM.put(e.getUUID(), new Calm(e.level().getGameTime() + ticks, power, e.getUUID()));
    }

    public static void vector(LivingEntity e, int ticks) {
        VECTOR.put(e.getUUID(), e.level().getGameTime() + ticks);
    }

    public static void wheel(LivingEntity e, int ticks) {
        Wheel w = WHEEL.computeIfAbsent(e.getUUID(), k -> new Wheel());
        w.until = e.level().getGameTime() + ticks;
    }

    /** Salva o ponto de retorno do jogador (por cima do anterior). */
    public static void checkpoint(ServerPlayer sp, int ticks) {
        RBD.put(sp.getUUID(), new Checkpoint(sp.level().dimension(), sp.position(), sp.getYRot(), sp.getXRot(),
                Math.max(2F, sp.getHealth()), sp.getFoodData().getFoodLevel(), sp.level().getGameTime() + ticks));
    }

    private static boolean active(Map<UUID, Long> map, Entity e) {
        Long until = map.get(e.getUUID());
        if (until == null) return false;
        if (e.level().getGameTime() < until) return true;
        map.remove(e.getUUID());
        return false;
    }

    // =====================================================================================
    //  Recargas longas: a magia nem sai (sem gastar mana) enquanto a técnica recarrega
    // =====================================================================================

    public static void onCastPre(SpellCastEvent.Pre e) {
        LivingEntity c = e.getCaster();
        if (c == null || (ZERO_LOCKED.isEmpty() && RBD_LOCKED.isEmpty())) return;
        long now = c.level().getGameTime();
        if (has(e.getContext().rootSpell(), LkGlyphs.RETURN_TO_ZERO)) {
            Long until = ZERO_LOCKED.get(c.getUUID());
            if (until != null && now < until) {
                e.setCanceled(true);
                LkUtil.say(c, "zero_cooldown", ChatFormatting.GOLD, (until - now + 19) / 20);
                return;
            }
        }
        if (has(e.getContext().rootSpell(), LkGlyphs.RETURN_BY_DEATH)) {
            Long until = RBD_LOCKED.get(c.getUUID());
            if (until != null && now < until) {
                e.setCanceled(true);
                LkUtil.say(c, "rbd_cooldown", ChatFormatting.LIGHT_PURPLE, (until - now + 1199) / 1200);
            }
        }
    }

    private static boolean has(ParsedSpell root, ResourceLocation id) {
        for (ParsedSpell s : root.stages()) {
            if (s == null) continue;
            for (ParsedSpell.EffectEntry en : s.effects()) if (en.effect().id().equals(id)) return true;
        }
        return false;
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================

    public static void onIncoming(LivingIncomingDamageEvent e) {
        if (COUNTERING.get()) return;
        LivingEntity v = e.getEntity();
        DamageSource src = e.getSource();
        if (LkUtil.unstoppable(src)) return;
        Entity attacker = src.getEntity();
        Entity direct = src.getDirectEntity();
        boolean someone = attacker != null || direct != null;

        // 1) Ponto Zero: anula qualquer coisa vinda de alguém
        if (someone && attacker != v && !ZERO.isEmpty() && zeroArmed(v)) {
            e.setCanceled(true);
            returnToZero(v, attacker != null ? attacker : direct, direct);
            return;
        }
        // 2) Santen Kesshun: um golpe a menos no escudo
        if (someone && attacker != v && !SHIELD.isEmpty()) {
            Shield s = SHIELD.get(v.getUUID());
            if (s != null && v.level().getGameTime() < s.until() && s.charges() > 0) {
                e.setCanceled(true);
                int left = s.charges() - 1;
                if (left <= 0) SHIELD.remove(v.getUUID());
                else SHIELD.put(v.getUUID(), new Shield(left, s.until()));
                if (v.level() instanceof ServerLevel l) {
                    Entity from = direct != null ? direct : attacker;
                    Vec3 dir = from != null ? from.position().subtract(v.position()) : v.getLookAngle();
                    Vec3 c = LkUtil.center(v).add(new Vec3(dir.x, 0, dir.z).normalize().scale(0.9));
                    LkFx.triangle(l, c, dir, 0.9, LkFx.RIKKA);
                    LkUtil.sound(l, c, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.6F);
                    if (left <= 0) LkUtil.sound(l, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.2F);
                }
                if (direct instanceof Projectile p && !(direct instanceof LivingEntity)) p.discard();
                return;
            } else if (s != null && v.level().getGameTime() >= s.until()) {
                SHIELD.remove(v.getUUID());
            }
        }
        // 3) Calmaria: corpo a corpo e projéteis são aparados, e o atacante leva o corte
        if (someone && attacker != v && !CALM.isEmpty()) {
            Calm c = CALM.get(v.getUUID());
            if (c != null && v.level().getGameTime() < c.until()) {
                boolean melee = direct == attacker && attacker != null && attacker.distanceToSqr(v) <= 4.5 * 4.5;
                boolean shot = direct instanceof Projectile;
                if (melee || shot) {
                    e.setCanceled(true);
                    if (shot) direct.discard();
                    if (attacker instanceof LivingEntity a && v.level() instanceof ServerLevel l) counterCut(l, v, a, c.power());
                    return;
                }
            } else if (c != null) {
                CALM.remove(v.getUUID());
            }
        }
        // 4) Reflexão de vetores: corpo a corpo perde 40% e o atacante voa
        if (attacker instanceof LivingEntity a && direct == attacker && a != v && !VECTOR.isEmpty() && active(VECTOR, v)) {
            e.setAmount(e.getAmount() * 0.6F);
            Vec3 away = a.position().subtract(v.position());
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() < 1e-4) away = v.getLookAngle();
            float k = LkUtil.boss(a) ? 0.4F : 1.3F;
            LkUtil.impulse(a, away.normalize().scale(k).add(0, 0.35, 0));
            if (v.level() instanceof ServerLevel l) {
                LkFx.line(l, LkUtil.center(v), LkUtil.center(a), LkFx.VECTOR, 0.25F);
                LkUtil.sound(l, v.position(), SoundEvents.BREEZE_DEFLECT, 1.0F, 1.0F);
            }
        }
        // 5) Mahoraga: a roda gira para o tipo de dano
        if (!WHEEL.isEmpty()) {
            Wheel w = WHEEL.get(v.getUUID());
            if (w != null) {
                if (v.level().getGameTime() >= w.until) {
                    WHEEL.remove(v.getUUID());
                } else {
                    String type = src.getMsgId();
                    int turns = w.turns.getOrDefault(type, 0);
                    if (turns > 0) e.setAmount(e.getAmount() * (1F - 0.2F * turns));
                    if (turns < 4) {
                        w.turns.put(type, turns + 1);
                        if (v.level() instanceof ServerLevel l) {
                            LkFx.wheel(l, v, turns + 1);
                            LkUtil.sound(l, v.position(), SoundEvents.CHAIN_PLACE, 1.0F, 0.6F + 0.15F * turns);
                            if (turns + 1 == 4) LkUtil.sound(l, v.position(), SoundEvents.BELL_BLOCK, 0.9F, 0.7F);
                        }
                        LkUtil.say(v, "wheel_turn", ChatFormatting.GOLD, turns + 1, (turns + 1) * 20);
                    }
                }
            }
        }
    }

    /** "Você nunca chegará à verdade": anula, atordoa e empurra quem tentou. */
    private static void returnToZero(LivingEntity v, @Nullable Entity attacker, @Nullable Entity direct) {
        ZERO.remove(v.getUUID());
        if (direct instanceof Projectile p && !(direct instanceof LivingEntity)) p.discard();
        if (!(v.level() instanceof ServerLevel l)) return;
        Vec3 c = LkUtil.center(v);
        LkFx.ring(l, c, 1.2, LkFx.REQUIEM, 0.4F, 28);
        LkFx.ring(l, c.add(0, 0.4, 0), 0.8, 0xFFFFFF, 0.3F, 18);
        LkUtil.sound(l, c, SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.6F);
        LkUtil.sound(l, c, SoundEvents.BELL_RESONATE, 0.8F, 1.4F);
        LkUtil.say(v, "zero_trigger", ChatFormatting.GOLD);
        if (attacker instanceof LivingEntity a && a != v) {
            LkFx.line(l, c, LkUtil.center(a), LkFx.REQUIEM, 0.25F);
            Statuses.apply(a, Statuses.Kind.STUNNED, 1, LkUtil.boss(a) ? 15 : 40, v, 1F);
            if (a instanceof Mob m) {
                m.setTarget(null);
                m.getNavigation().stop();
            }
            Vec3 away = a.position().subtract(v.position());
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() < 1e-4) away = v.getLookAngle();
            LkUtil.impulse(a, away.normalize().scale(LkUtil.boss(a) ? 0.5 : 1.4).add(0, 0.3, 0));
            LkUtil.say(a, "zero_victim", ChatFormatting.GOLD);
        }
    }

    /** O corte de volta da Calmaria. */
    private static void counterCut(ServerLevel l, LivingEntity v, LivingEntity a, float power) {
        COUNTERING.set(true);
        try {
            DamageSource ds = v instanceof net.minecraft.world.entity.player.Player p ? l.damageSources().playerAttack(p)
                    : l.damageSources().mobAttack(v);
            a.hurt(ds, 6F * power);
        } finally {
            COUNTERING.set(false);
        }
        Vec3 from = LkUtil.center(v);
        Vec3 to = LkUtil.center(a);
        LkFx.line(l, from, to, LkFx.CALM, 0.3F);
        l.sendParticles(ParticleTypes.SPLASH, to.x, to.y, to.z, 8, 0.3, 0.3, 0.3, 0.1);
        LkUtil.sound(l, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
    }

    /** Projéteis: Reflexão de vetores devolve; Calmaria corta (o dano em si é cuidado em {@link #onIncoming}). */
    public static void onProjectile(ProjectileImpactEvent e) {
        if (VECTOR.isEmpty() || !(e.getRayTraceResult() instanceof EntityHitResult hit)) return;
        Entity v = hit.getEntity();
        if (!(v instanceof LivingEntity le) || !active(VECTOR, le)) return;
        Projectile p = e.getProjectile();
        if (p.getOwner() == le) return;
        e.setCanceled(true);
        p.deflect(ProjectileDeflection.REVERSE, le, le, le instanceof net.minecraft.world.entity.player.Player);
        p.setDeltaMovement(p.getDeltaMovement().scale(1.25));
        if (le.level() instanceof ServerLevel l) {
            LkFx.ring(l, p.position(), 0.5, LkFx.VECTOR, 0.25F, 10);
            LkUtil.sound(l, p.position(), SoundEvents.BREEZE_DEFLECT, 1.0F, 1.3F);
        }
    }

    /** Ponto Zero também anula efeitos ruins vindos de alguém. */
    public static void onEffect(MobEffectEvent.Applicable e) {
        if (ZERO.isEmpty()) return;
        LivingEntity v = e.getEntity();
        Entity src = e.getEffectSource();
        MobEffectInstance inst = e.getEffectInstance();
        if (src == null || src == v || inst == null || inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        if (!zeroArmed(v)) return;
        e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        returnToZero(v, src, null);
    }

    /** Reflexão de vetores: o empurrão não te move. */
    public static void onKnockback(LivingKnockBackEvent e) {
        if (!VECTOR.isEmpty() && active(VECTOR, e.getEntity())) e.setCanceled(true);
    }

    /** Retorno pela Morte: a morte é desfeita e você volta ao ponto salvo. */
    public static void onDeath(LivingDeathEvent e) {
        if (RBD.isEmpty() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Checkpoint cp = RBD.remove(sp.getUUID());
        if (cp == null || sp.level().getGameTime() >= cp.until() || LkUtil.unstoppable(e.getSource())) return;
        ServerLevel dest = sp.server.getLevel(cp.level());
        if (dest == null) return;
        e.setCanceled(true);
        sp.setHealth(Math.min(sp.getMaxHealth(), cp.health()));
        sp.getFoodData().setFoodLevel(Math.max(sp.getFoodData().getFoodLevel(), cp.food()));
        sp.clearFire();
        sp.fallDistance = 0;
        sp.setDeltaMovement(Vec3.ZERO);
        for (MobEffectInstance inst : new ArrayList<>(sp.getActiveEffects())) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) sp.removeEffect(inst.getEffect());
        }
        sp.teleportTo(dest, cp.pos().x, cp.pos().y, cp.pos().z, cp.yRot(), cp.xRot());
        RBD_LOCKED.put(sp.getUUID(), dest.getGameTime() + RBD_LOCK);
        LkNet.screen(sp, LkNet.RBD, 40);
        LkUtil.sound(dest, cp.pos(), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.8F);
        LkFx.swirl(dest, cp.pos().add(0, 1, 0), 1.0, LkFx.RBD, 24);
        LkUtil.say(sp, "rbd_return", ChatFormatting.LIGHT_PURPLE);
    }

    public static void forget(UUID id) {
        ZERO.remove(id);
        SHIELD.remove(id);
        CALM.remove(id);
        VECTOR.remove(id);
        WHEEL.remove(id);
        RBD.remove(id);
    }

    public static void clear() {
        ZERO.clear();
        ZERO_LOCKED.clear();
        SHIELD.clear();
        CALM.clear();
        VECTOR.clear();
        WHEEL.clear();
        RBD.clear();
        RBD_LOCKED.clear();
    }

    /** Tick (1×/s): visuais leves das defesas ativas e limpeza do que venceu. */
    public static void tickVisuals(net.minecraft.server.MinecraftServer server) {
        if (SHIELD.isEmpty() && CALM.isEmpty() && ZERO.isEmpty() && VECTOR.isEmpty()) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ServerLevel l = sp.serverLevel();
            long now = l.getGameTime();
            Shield s = SHIELD.get(sp.getUUID());
            if (s != null) {
                if (now >= s.until()) SHIELD.remove(sp.getUUID());
                else LkFx.triangle(l, LkUtil.center(sp).add(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8)),
                        sp.getLookAngle(), 0.7, LkFx.RIKKA);
            }
            Calm c = CALM.get(sp.getUUID());
            if (c != null) {
                if (now >= c.until()) CALM.remove(sp.getUUID());
                else LkFx.ring(l, sp.position().add(0, 0.05, 0), 2.6, LkFx.CALM, 0.3F, 24);
            }
            if (ZERO.containsKey(sp.getUUID())) {
                if (!zeroArmed(sp)) ZERO.remove(sp.getUUID());
                else LkFx.ring(l, sp.position().add(0, 0.1, 0), 0.7, LkFx.REQUIEM, 0.2F, 10);
            }
        }
    }

    private Guards() {}
}
