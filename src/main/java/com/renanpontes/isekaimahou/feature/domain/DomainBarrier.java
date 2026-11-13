package com.renanpontes.isekaimahou.feature.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * A barreira do Domínio (§11.1 do design) — "quebra por fora, não por dentro; invertida é o contrário".
 * <ul>
 *   <li>{@link Domains.BarrierHandler} registrado no núcleo: a integridade mora no domo ({@link DomainDomeEntity});</li>
 *   <li>detecção dos golpes na parede: magias cujo conjurador e alvo estão em lados opostos (o golpe para na parede),
 *       golpes corpo a corpo através da parede, explosões perto da parede (e a parede protege quem está do outro
 *       lado), socos no ar mirando o domo (pacote do cliente) — os projéteis são tratados pelo próprio domo;</li>
 *   <li>a regra dos lados é sempre a do núcleo ({@link Domains#hitBarrier}); o dono e os aliados atravessam a própria
 *       barreira e nunca a danificam.</li>
 * </ul>
 */
public final class DomainBarrier implements Domains.BarrierHandler {
    public static final DomainBarrier INSTANCE = new DomainBarrier();
    /** Alcance do soco na parede (validação no servidor). */
    public static final double PUNCH_REACH = 5.0;
    private static final int PUNCH_COOLDOWN = 4;

    /** Ponto exato do golpe em andamento (para o efeito de rachadura sair no lugar certo). */
    @Nullable private static Vec3 pendingHit;
    private static final Map<UUID, Long> LAST_PUNCH = new HashMap<>();
    /**
     * Magia de área com vários alvos atrás da mesma parede: a parede leva UM golpe por autor por tick (o maior), não um
     * por alvo. Chave = id do domo + UUID do autor; valor = tick do mundo.
     */
    private static final Map<String, Long> SPELL_HITS = new HashMap<>();

    private DomainBarrier() {}

    static void register() {
        Domains.setBarrierHandler(INSTANCE);
    }

    static void onServerStopped(ServerStoppedEvent e) {
        LAST_PUNCH.clear();
        SPELL_HITS.clear();
        pendingHit = null;
    }

    // =====================================================================================
    //  BarrierHandler (núcleo → domo)
    // =====================================================================================
    @Override
    public float integrity(int domainId) {
        DomainDomeEntity d = DomainDomeEntity.byId(domainId);
        return d == null || !d.barrierUp() ? 0F : d.integrity();
    }

    @Override
    public float maxIntegrity(int domainId) {
        DomainDomeEntity d = DomainDomeEntity.byId(domainId);
        return d == null ? 0F : d.maxIntegrity();
    }

    @Override
    public float damage(int domainId, float amount, boolean fromInside, @Nullable Entity attacker) {
        DomainDomeEntity d = DomainDomeEntity.byId(domainId);
        if (d == null) return 0F;
        return d.damageBarrier(amount, fromInside, attacker, pendingHit);
    }

    /**
     * Golpe na parede de {@code dome} em {@code hit} (deve estar sobre a parede), vindo de {@code from}. Passa pela
     * regra do núcleo (lados, dono/aliados, avisos).
     * @return integridade tirada
     */
    public static float hitAt(ServerLevel sl, DomainDomeEntity dome, Vec3 hit, Vec3 from, @Nullable Entity attacker, float amount) {
        Vec3 prev = pendingHit;
        pendingHit = hit;
        try {
            return Domains.hitBarrier(sl, hit, from, attacker, amount);
        } finally {
            pendingHit = prev;
        }
    }

    // =====================================================================================
    //  Geometria
    // =====================================================================================
    /** Onde o segmento {@code a → b} cruza a casca do domo (ou o ponto da parede mais perto do meio do segmento). */
    public static Vec3 wallPoint(DomainDomeEntity dome, Vec3 a, Vec3 b) {
        Vec3 c = dome.center();
        double r = dome.radius();
        Vec3 d = b.subtract(a);
        double qa = d.lengthSqr();
        if (qa > 1.0E-8) {
            Vec3 f = a.subtract(c);
            double qb = 2 * f.dot(d);
            double qc = f.lengthSqr() - r * r;
            double disc = qb * qb - 4 * qa * qc;
            if (disc >= 0) {
                double s = Math.sqrt(disc);
                double t1 = (-qb - s) / (2 * qa), t2 = (-qb + s) / (2 * qa);
                double t = t1 >= 0 && t1 <= 1 ? t1 : t2 >= 0 && t2 <= 1 ? t2 : -1;
                if (t >= 0) return a.add(d.scale(t));
            }
        }
        Vec3 mid = a.add(b).scale(0.5).subtract(c);
        if (mid.lengthSqr() < 1.0E-6) mid = new Vec3(0, 1, 0);
        return c.add(mid.normalize().scale(r));
    }

    /** Ponto da parede virado para {@code e} (ou o topo do domo). */
    public static Vec3 wallTowards(DomainDomeEntity dome, @Nullable Entity e) {
        Vec3 c = dome.center();
        Vec3 dir = e == null ? new Vec3(0, 1, 0) : e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(c);
        if (dir.lengthSqr() < 1.0E-6) dir = new Vec3(0, 1, 0);
        return c.add(dir.normalize().scale(dome.radius()));
    }

    /** Distância ao longo do raio {@code eye + t·dir} até a casca do domo (dentro: até a saída), ou -1. */
    public static double rayToWall(DomainDomeEntity dome, Vec3 eye, Vec3 dir, double maxDist) {
        Vec3 f = eye.subtract(dome.center());
        double r = dome.radius();
        double b = f.dot(dir);
        double c = f.lengthSqr() - r * r;
        double disc = b * b - c;
        if (disc < 0) return -1;
        double s = Math.sqrt(disc);
        double t = c < 0 ? -b + s : -b - s;
        return t > 0 && t <= maxDist ? t : -1;
    }

    /** Dano de um projétil contra a parede. */
    public static float projectileDamage(Projectile p) {
        if (p instanceof AbstractArrow a) {
            return Mth.clamp((float) (a.getBaseDamage() * p.getDeltaMovement().length()), 2F, 30F);
        }
        if (p instanceof AbstractHurtingProjectile) return 10F;
        // Projétil de magia: maior = mais forte (bola pequena ~10, esfera grande até ~22)
        if (p instanceof com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity sp) {
            return Mth.clamp(10F + 8F * sp.size(), 10F, 22F);
        }
        if (p instanceof ThrowableItemProjectile) return 1F;
        return 8F;
    }

    // =====================================================================================
    //  Efeitos
    // =====================================================================================
    /** Rachadura no ponto do golpe: vidro, faíscas e um estalo que fica mais grave quanto mais rachada. */
    public static void crackFx(ServerLevel sl, DomainDomeEntity dome, Vec3 at, float fraction, boolean fromInside) {
        BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
        DomainFx.far(sl, glass, at.x, at.y, at.z, 14, 0.5, 0.5, 0.5, 0.12);
        DomainFx.far(sl, Vfx.electric(), at.x, at.y, at.z, 12, 0.4, 0.4, 0.4, 0.25);
        DomainFx.far(sl, dome.isInverted() ? Vfx.reversePortal() : ElementFx.dust(dome.element(), 1.8F),
                at.x, at.y, at.z, 10, 0.6, 0.6, 0.6, 0.02);
        float pitch = 0.45F + 0.6F * fraction;
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.6F, pitch);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.4F, pitch * 0.8F);
        if (fraction < 0.3F) {
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F + fraction);
            DomainFx.far(sl, Vfx.crit(), at.x, at.y, at.z, 10, 0.8, 0.8, 0.8, 0.3);
        }
    }

    /** A barreira aguentou (lado que não quebra, ou invertida contra o domínio de fora). */
    public static void holdFx(ServerLevel sl, DomainDomeEntity dome, Vec3 at) {
        sl.sendParticles(Vfx.electric(), at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.15);
        sl.sendParticles(dome.isInverted() ? Vfx.reversePortal() : Vfx.smoke(), at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 0.5F);
    }

    // =====================================================================================
    //  Golpes através da parede
    // =====================================================================================
    /**
     * A barreira de pé que separa {@code a} e {@code b} (um dentro, outro fora) e que {@code author} NÃO pode
     * atravessar (não é o dono nem aliado). A de menor raio, se houver mais de uma.
     */
    @Nullable
    public static DomainDomeEntity separating(ServerLevel sl, Vec3 a, Vec3 b, @Nullable Entity author) {
        DomainDomeEntity best = null;
        for (DomainDomeEntity d : DomainDomeEntity.all(sl)) {
            if (!d.barrierUp()) continue;
            if (d.contains(a, 0) == d.contains(b, 0)) continue;
            if (author != null && d.isFriendly(author)) continue;
            if (best == null || d.radius() < best.radius()) best = d;
        }
        return best;
    }

    private static Vec3 bodyCenter(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Magia (dano de glifo) de um lado da parede para o outro: o golpe para na barreira. */
    static void onSpellDamage(SpellDamageEvent.Pre event) {
        SpellContext c = event.ctx();
        LivingEntity target = event.target();
        if (!(target.level() instanceof ServerLevel sl) || DomainDomeEntity.all(sl).isEmpty()) return;
        LivingEntity caster = c.caster();
        Entity author = caster != null ? caster : event.direct();
        if (author == null || author.level() != sl) return;
        Vec3 from = caster != null ? caster.getEyePosition() : author.position();
        Vec3 to = bodyCenter(target);
        DomainDomeEntity wall = separating(sl, from, to, author);
        if (wall == null) return;
        event.setCanceled(true);
        long now = sl.getGameTime();
        String key = wall.getId() + "/" + author.getUUID();
        Long last = SPELL_HITS.get(key);
        if (last != null && last == now) return;
        SPELL_HITS.put(key, now);
        if (SPELL_HITS.size() > 128) SPELL_HITS.values().removeIf(t -> t != now);
        Vec3 hit = wallPoint(wall, from, to);
        hitAt(sl, wall, hit, from, author, Math.max(1F, event.getAmount()));
    }

    /** Golpe corpo a corpo (ou dano direto de criatura) através da parede: bate na barreira. */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel sl)) return;
        DamageSource src = event.getSource();
        if (!(src.getDirectEntity() instanceof LivingEntity attacker) || attacker != src.getEntity() || attacker == target) return;
        if (DomainDomeEntity.all(sl).isEmpty()) return;
        Vec3 from = attacker.getEyePosition();
        Vec3 to = bodyCenter(target);
        DomainDomeEntity wall = separating(sl, from, to, attacker);
        if (wall == null) return;
        event.setCanceled(true);
        hitAt(sl, wall, wallPoint(wall, from, to), from, attacker, Math.max(1F, event.getAmount()));
    }

    /**
     * Explosões: perto da parede (≤ 3 blocos + metade do raio) batem nela (raio × 8 de dano); e a parede protege quem
     * está do outro lado da explosão (a menos que a explosão seja do dono/aliados).
     */
    static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        List<DomainDomeEntity> domes = DomainDomeEntity.all(sl);
        if (domes.isEmpty()) return;
        Explosion ex = event.getExplosion();
        Vec3 at = ex.center();
        float power = ex.radius();
        Entity author = ex.getIndirectSourceEntity() != null ? ex.getIndirectSourceEntity() : ex.getDirectSourceEntity();
        if (author instanceof TraceableEntity t && t.getOwner() != null) author = t.getOwner();
        for (DomainDomeEntity d : domes) {
            if (!d.barrierUp()) continue;
            boolean friendly = author != null && d.isFriendly(author);
            if (!friendly) {
                boolean inside = d.contains(at, 0);
                event.getAffectedEntities().removeIf(e -> e != d && d.contains(e, 0) != inside);
            }
            double gap = Math.abs(at.distanceTo(d.center()) - d.radius());
            if (gap > 3.0 + power * 0.5) continue;
            Vec3 dir = at.subtract(d.center());
            if (dir.lengthSqr() < 1.0E-4) continue;
            Vec3 hit = d.center().add(dir.normalize().scale(d.radius()));
            hitAt(sl, d, hit, at, author, Math.max(4F, power * 8F));
        }
    }

    /**
     * Soco no ar mirando o domo (pacote do cliente). Validação: o domo existe e está de pé, o olhar do jogador acerta a
     * parede a até {@link #PUNCH_REACH} blocos, sem bloco no caminho, e a recarga do ataque.
     */
    static void punch(ServerPlayer sp, int domeId) {
        if (sp instanceof FakePlayer || !sp.isAlive() || sp.isSpectator()) return;
        DomainDomeEntity d = DomainDomeEntity.byId(domeId);
        if (d == null || d.level() != sp.level() || !d.barrierUp() || !(sp.level() instanceof ServerLevel sl)) return;
        // O dono e os aliados atravessam a própria barreira: um soco no ar perto dela não é um golpe (sem faísca/som)
        if (d.isFriendly(sp)) return;
        long now = sl.getGameTime();
        Long last = LAST_PUNCH.get(sp.getUUID());
        if (last != null && now - last >= 0 && now - last < PUNCH_COOLDOWN) return;
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F);
        double reach = Math.min(PUNCH_REACH, sp.entityInteractionRange() + 1.5);
        double t = rayToWall(d, eye, look, reach);
        if (t < 0) return;
        Vec3 hit = eye.add(look.scale(t));
        // Bloco no caminho: o soco acertou o bloco, não a parede
        var clip = sl.clip(new net.minecraft.world.level.ClipContext(eye, hit, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
        if (clip.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && clip.getLocation().distanceToSqr(eye) < t * t - 0.01) return;
        LAST_PUNCH.put(sp.getUUID(), now);
        if (LAST_PUNCH.size() > 64) LAST_PUNCH.values().removeIf(v -> now - v > 200 || v > now);
        float scale = sp.getAttackStrengthScale(0.5F);
        sp.resetAttackStrengthTicker();
        float dmg = (float) sp.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + 0.8F * scale * scale);
        if (dmg <= 0.05F) return;
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
        sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, 0.8F);
        // Lado: de onde está o olho do jogador (dentro = preso, não quebra uma barreira normal)
        hitAt(sl, d, hit, eye, sp, dmg);
    }
}
