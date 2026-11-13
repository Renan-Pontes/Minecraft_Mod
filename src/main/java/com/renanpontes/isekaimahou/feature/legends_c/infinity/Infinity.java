package com.renanpontes.isekaimahou.feature.legends_c.infinity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.api.Statuses.Kind;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcRegistry;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.feature.legends_c.entity.InfinityFieldEntity;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Infinito (Mugen) — servidor.
 * <ul>
 *   <li><b>Espaço infinito</b>: tudo que vem na direção do dono desacelera assintoticamente a partir de
 *       {@link #RADIUS} blocos e PARA a {@link #STOP} bloco da pele dele. Criaturas ganham Lentidão crescente e têm a
 *       velocidade de aproximação cortada (mobs são mantidos fora da parede); projéteis são freados e ficam SUSPENSOS
 *       no ar (presos à esfera, acompanham o dono) até o Infinito acabar — aí caem.</li>
 *   <li><b>Golpes anulados</b>: dano de magia ({@link SpellDamageEvent.Pre}) e ataques corpo a corpo/projéteis/explosões
 *       ({@link LivingIncomingDamageEvent}) são cancelados, EXCETO quando o dono está sob Acerto Garantido
 *       ({@link Domains#sureHitOn}) — o Infinito para de funcionar por completo — e golpes de chefes, que passam com 25%.</li>
 *   <li><b>Custo</b>: 2 de mana por segundo (jogador fora do criativo); sem mana, desfaz.</li>
 * </ul>
 * <b>Condições de status e reações</b>: o núcleo roda {@code ElementReactions.onSpellHit} (aplica Em Chamas, Molhado,
 * reações com Atordoado/Exposto...) ANTES do {@code SpellDamageEvent.Pre}. Para o golpe anulado não deixar nada, guardamos
 * uma FOTO das condições de cada dono do Infinito (no fim de cada tick do servidor, ao ganhar o Infinito e depois de cada
 * golpe que passou de verdade) e, quando anulamos um golpe, devolvemos as condições exatamente à foto
 * ({@link #restoreStatuses}): o que o golpe acabou de aplicar some e o que uma reação consumiu volta.
 */
public final class Infinity {
    /** Distância (da pele do dono) onde o freio começa. */
    public static final double RADIUS = 4.5;
    /** Distância onde tudo para. */
    public static final double STOP = 1.5;
    /** Fração do dano dos chefes que atravessa. */
    public static final float BOSS_FRACTION = 0.25F;
    /** Manutenção do Infinito (v0.8: 2/s; ×{@link ResourceTraining#MANA_SCALE} com a mana da v1.0). */
    public static final int MANA_PER_SECOND = Math.round(2 * ResourceTraining.MANA_SCALE);
    public static final int MIN_TICKS = 160;
    public static final int MAX_TICKS = 400;

    private record Snap(int stacks, int ticks, float power) {}

    /** Donos ativos (servidor). */
    private static final Set<LivingEntity> HOLDERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<UUID, InfinityFieldEntity> FIELDS = new HashMap<>();
    private static final Map<LivingEntity, EnumMap<Kind, Snap>> SNAPSHOTS = new WeakHashMap<>();
    /** Alvos cujo golpe de magia já foi liberado no Pre (o LivingIncomingDamage não decide de novo). */
    private static final Set<Integer> PASS = new HashSet<>();
    private static final Map<UUID, Long> PIERCE_MSG = new HashMap<>();

    public static boolean has(LivingEntity e) {
        return e.hasEffect(LcRegistry.MUGEN);
    }

    /** O Infinito está protegendo agora? (tem o efeito e NÃO está sob Acerto Garantido) */
    public static boolean protecting(LivingEntity e) {
        return has(e) && !Domains.sureHitOn(e);
    }

    /** Dá (ou renova) o Infinito. */
    public static void grant(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        int dur = Mth.clamp(ticks, MIN_TICKS, MAX_TICKS);
        boolean fresh = !has(e);
        e.addEffect(new MobEffectInstance(LcRegistry.MUGEN, dur, 0, false, false, true));
        HOLDERS.add(e);
        snapshot(e);
        field(sl, e);
        Vec3 c = LcUtil.center(e);
        if (fresh) {
            LcFx.sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.4F, 0.5F);
            LcFx.sound(sl, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.5F);
            LcFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.6F);
        }
        LcFx.wave(sl, c, new Vec3(0, 1, 0), (float) (RADIUS + e.getBbWidth()), LcFx.BLUE_LIGHT, 18);
        LcFx.flash(sl, c, 2.5F, LcFx.BLUE_LIGHT, 8);
    }

    /** A cada tick no servidor (chamado pelo efeito). */
    static void tick(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        HOLDERS.add(e);
        if (e instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator() && e.tickCount % 20 == 0) {
            if (!MagicApi.get(sp).consumeMana(MANA_PER_SECOND)) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_c.infinity_no_mana"), true);
                // Tirar o efeito DENTRO do tick dos efeitos daria ConcurrentModification: no próximo tick
                SpellScheduler.schedule(1, () -> sp.removeEffect(LcRegistry.MUGEN));
                return;
            }
        }
        InfinityFieldEntity f = field(sl, e);
        boolean pierced = Domains.sureHitOn(e);
        f.setPierced(pierced);
        if (pierced) {
            if (e instanceof ServerPlayer sp) {
                Long last = PIERCE_MSG.get(sp.getUUID());
                if (last == null || sl.getGameTime() - last > 100) {
                    PIERCE_MSG.put(sp.getUUID(), sl.getGameTime());
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_c.infinity_pierced"), true);
                }
            }
            return;
        }
        repel(sl, e, f);
        if (e.tickCount % 40 == 0) LcFx.sound(sl, e.position(), SoundEvents.BEACON_AMBIENT, 0.5F, 0.5F);
    }

    /** A esfera visual que acompanha o dono (recria se sumiu). */
    private static InfinityFieldEntity field(ServerLevel sl, LivingEntity e) {
        InfinityFieldEntity f = FIELDS.get(e.getUUID());
        if (f == null || f.isRemoved() || f.level() != sl) {
            f = InfinityFieldEntity.spawn(sl, e);
            FIELDS.put(e.getUUID(), f);
        }
        return f;
    }

    /** O campo some: esquece. */
    public static void forget(UUID owner, InfinityFieldEntity f) {
        FIELDS.remove(owner, f);
    }

    /** Distância da pele do dono até {@code p}. */
    private static double skin(LivingEntity owner, Vec3 p) {
        return p.distanceTo(LcUtil.center(owner)) - owner.getBbWidth() * 0.5;
    }

    private static boolean allied(LivingEntity owner, @Nullable Entity other) {
        if (other == null) return false;
        if (other == owner || other.getUUID().equals(owner.getUUID())) return true;
        if (other instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) return true;
        return owner.isAlliedTo(other);
    }

    /** Freio assintótico de criaturas e projéteis que se aproximam. */
    private static void repel(ServerLevel sl, LivingEntity e, InfinityFieldEntity f) {
        Vec3 c = LcUtil.center(e);
        AABB box = e.getBoundingBox().inflate(RADIUS + 1.5);
        for (Entity o : sl.getEntities(e, box, o -> o != f && o.isAlive() && !o.isSpectator())) {
            if (o instanceof Projectile p) {
                // Projéteis de chefes atravessam (o golpe chega com 25% — ver onIncomingDamage)
                if (p.getOwner() instanceof LivingEntity po && BossKit.isBossLike(po)) continue;
                if (!allied(e, p.getOwner()) && !f.isHeld(p)) slowProjectile(sl, e, f, p, c);
                continue;
            }
            if (!(o instanceof LivingEntity le) || allied(e, le) || le.isPassengerOfSameVehicle(e)) continue;
            Vec3 oc = LcUtil.center(le);
            Vec3 n = oc.subtract(c);
            double d = n.length() - e.getBbWidth() * 0.5 - le.getBbWidth() * 0.5;
            if (d > RADIUS || n.lengthSqr() < 1.0E-6) continue;
            n = n.normalize();
            Vec3 moved = le.position().subtract(le.xo, le.yo, le.zo);
            double approach = -moved.dot(n);
            if (approach <= 0.004 && d >= STOP) continue;
            double k = Mth.clamp((d - STOP) / (RADIUS - STOP), 0.0, 1.0);
            Vec3 v = le.getDeltaMovement();
            double vr = -v.dot(n);
            if (vr > 0) v = v.add(n.scale(vr * (1.0 - k)));
            if (d < STOP) v = v.add(n.scale(0.1 * (STOP - d) + 0.03));
            le.setDeltaMovement(v);
            if (le instanceof Player) {
                le.hurtMarked = true;
            } else if (d < STOP - 0.05) {
                // Mobs ficam fora da parede (a IA deles empurra a velocidade de volta todo tick)
                Vec3 push = LcUtil.flat(n).scale(STOP - d);
                le.setPos(le.getX() + push.x, le.getY(), le.getZ() + push.z);
            }
            int amp = k < 0.12 ? 6 : k < 0.35 ? 4 : k < 0.65 ? 2 : 0;
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, amp, false, false, false));
            if ((le.tickCount + le.getId()) % 8 == 0) {
                Vec3 at = c.add(n.scale(e.getBbWidth() * 0.5 + Math.max(STOP, d) * 0.9));
                LcFx.ripple(sl, at, n, 0.5F + le.getBbHeight() * 0.3F, LcFx.BLUE_LIGHT);
                sl.sendParticles(Vfx.enchant(), at.x, at.y, at.z, 3, 0.15, 0.25, 0.15, 0.02);
            }
        }
    }

    private static void slowProjectile(ServerLevel sl, LivingEntity e, InfinityFieldEntity f, Projectile p, Vec3 c) {
        Vec3 n = p.position().subtract(c);
        double d = n.length() - e.getBbWidth() * 0.5;
        if (d > RADIUS || n.lengthSqr() < 1.0E-6) return;
        n = n.normalize();
        Vec3 v = p.getDeltaMovement();
        double vr = -v.dot(n);
        if (vr <= 0 && d > STOP + 0.3) return;
        double k = Mth.clamp((d - STOP) / (RADIUS - STOP), 0.0, 1.0);
        Vec3 slowed = v.scale(k * k);
        if (d <= STOP + 0.25 || slowed.length() < 0.03) {
            if (f.hold(p)) {
                LcFx.ripple(sl, p.position(), n, 0.6F, LcFx.BLUE_LIGHT);
                LcFx.sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.5F);
            } else {
                p.setDeltaMovement(Vec3.ZERO);
            }
        } else {
            p.setDeltaMovement(slowed);
            p.hurtMarked = true;
        }
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================

    static void onSpellDamagePre(SpellDamageEvent.Pre event) {
        LivingEntity t = event.target();
        if (!has(t) || t.level().isClientSide) return;
        LivingEntity caster = event.ctx().caster();
        if (caster != null && allied(t, caster)) return;
        if (Domains.sureHitOn(t)) {
            PASS.add(t.getId());
            return;
        }
        if (caster != null && BossKit.isBossLike(caster)) {
            event.setAmount(event.getAmount() * BOSS_FRACTION);
            PASS.add(t.getId());
            return;
        }
        event.setCanceled(true);
        restoreStatuses(t);
        Vec3 from = caster != null ? caster.getEyePosition() : event.ctx().eyePos();
        nullifiedFx(t, from);
    }

    static void onSpellDamagePost(SpellDamageEvent.Post event) {
        if (event.landed() && has(event.target())) snapshot(event.target());
    }

    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity t = event.getEntity();
        if (t.level().isClientSide || !has(t)) return;
        if (PASS.remove(t.getId())) return;
        DamageSource s = event.getSource();
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity attacker = s.getEntity();
        Entity direct = s.getDirectEntity();
        boolean attack = attacker != null || direct != null || s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.IS_PROJECTILE);
        if (!attack || attacker == t) return;
        if (Domains.sureHitOn(t)) return;
        if (attacker instanceof LivingEntity la && BossKit.isBossLike(la)) {
            event.setAmount(event.getAmount() * BOSS_FRACTION);
            return;
        }
        event.setCanceled(true);
        Vec3 from = direct != null ? direct.position() : attacker != null ? attacker.position() : s.getSourcePosition();
        nullifiedFx(t, from);
    }

    private static void nullifiedFx(LivingEntity t, @Nullable Vec3 from) {
        if (!(t.level() instanceof ServerLevel sl)) return;
        Vec3 c = LcUtil.center(t);
        Vec3 n = from == null ? new Vec3(0, 0, 1) : from.subtract(c);
        n = n.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : n.normalize();
        Vec3 at = c.add(n.scale(t.getBbWidth() * 0.5 + STOP));
        LcFx.ripple(sl, at, n, 0.9F, LcFx.BLUE_LIGHT);
        sl.sendParticles(Vfx.enchant(), at.x, at.y, at.z, 8, 0.2, 0.3, 0.2, 0.05);
        LcFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 0.6F);
    }

    // =====================================================================================
    //  Foto das condições (ver o javadoc da classe)
    // =====================================================================================

    private static void snapshot(LivingEntity e) {
        EnumMap<Kind, Snap> m = new EnumMap<>(Kind.class);
        for (Kind k : Kind.values()) {
            int s = Statuses.stacks(e, k);
            if (s > 0) m.put(k, new Snap(s, Statuses.ticksLeft(e, k), Statuses.power(e, k)));
        }
        SNAPSHOTS.put(e, m);
    }

    /** Devolve as condições do dono à foto (o golpe anulado não deixa status nem consome os antigos). */
    static void restoreStatuses(LivingEntity e) {
        EnumMap<Kind, Snap> m = SNAPSHOTS.get(e);
        if (m == null) return;
        // 1º passo: tira TUDO o que mudou. Só depois devolve o que havia — senão devolver (ex.) Em Chamas com o Molhado
        // que o golpe acabou de pôr ainda presente dispararia a reação de novo.
        List<Kind> restore = new ArrayList<>();
        for (Kind k : Kind.values()) {
            Snap before = m.get(k);
            int b = before == null ? 0 : before.stacks;
            if (Statuses.stacks(e, k) == b) continue;
            Statuses.clear(e, k);
            if (b > 0) restore.add(k);
        }
        // 2º passo: devolve. Chefes recebem metade dos acúmulos e da duração no apply: compensamos para voltar igual.
        boolean boss = Statuses.isBossLike(e);
        for (Kind k : restore) {
            Snap before = m.get(k);
            int ticks = before.ticks < 0 ? 20 * 60 * 60 : Math.max(20, before.ticks);
            int stacks = before.stacks;
            if (boss) {
                stacks = stacks * 2 - 1;
                ticks *= 2;
            }
            Statuses.apply(e, k, stacks, ticks, null, before.power);
        }
    }

    // =====================================================================================
    //  Ciclo
    // =====================================================================================

    static void onServerTick(ServerTickEvent.Post event) {
        PASS.clear();
        if (HOLDERS.isEmpty()) return;
        List<LivingEntity> gone = new ArrayList<>();
        for (LivingEntity e : HOLDERS) {
            if (!e.isAlive() || e.isRemoved() || !has(e)) gone.add(e);
            else snapshot(e);
        }
        for (LivingEntity e : gone) {
            HOLDERS.remove(e);
            SNAPSHOTS.remove(e);
        }
    }

    public static void clear() {
        HOLDERS.clear();
        FIELDS.clear();
        SNAPSHOTS.clear();
        PASS.clear();
        PIERCE_MSG.clear();
    }

    private Infinity() {}
}
