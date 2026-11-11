package com.renanpontes.isekaimahou.feature.class_gunslinger.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsRegistries;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunCombat;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunMarks;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bala de mana: projétil MUITO rápido das armas do Pistoleiro, com dois modos:
 * <ul>
 *   <li><b>Magia</b> (formas Bala, Rajada, Tiro Carregado, Tiro Ricochete...): guarda o {@link SpellContext}, avisa
 *       {@code impact}/{@code end} (magias programáveis) e aplica os efeitos ao acertar. Vale Perfurar, Ricochete (nas
 *       paredes), Teleguiado, Fantasma, Arco, Sensível e Expandir.</li>
 *   <li><b>Simples</b> (clique direito com a arma, Forasteiro do duelo): dano fixo de projétil.</li>
 * </ul>
 * Tiro na CABEÇA = ×1,5 para o Pistoleiro (×1,25 para os outros) e alvo marcado pelo atirador = ×1,35. A bala não
 * para em aliados (atravessa). Pode "ricochetear" de inimigo em inimigo (Tiro Ricochete). Vive no máximo 2 s e não é
 * salva no mundo (some ao recarregar o chunk).
 */
public class ManaBulletEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ManaBulletEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(ManaBulletEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> STYLE = SynchedEntityData.defineId(ManaBulletEntity.class, EntityDataSerializers.BYTE);

    public static final byte STYLE_NORMAL = 0;
    public static final byte STYLE_HEAVY = 1;
    public static final byte STYLE_PELLET = 2;
    public static final byte STYLE_RICOCHET = 3;
    /** Esfera de energia do Dedo Espiritual (grande, explode). */
    public static final byte STYLE_SPIRIT = 4;

    /** Alcance da busca do próximo inimigo no Tiro Ricochete. */
    public static final double SEEK_RANGE = 14.0;

    // ---------------- estado do servidor (não salvo: a bala vive ~2 s)
    @Nullable private SpellContext ctx;
    private float plainDamage;
    private int pierceLeft;
    private int wallBounces;
    private int seekBounces;
    private boolean homing;
    private boolean ghost;
    private boolean arc;
    private boolean sensitive;
    private float impactRadius;
    private float knockback;
    private int lifetime = 40;
    private boolean initialized;
    private Vec3 origin = Vec3.ZERO;
    private final Set<UUID> hit = new HashSet<>();

    public ManaBulletEntity(EntityType<? extends ManaBulletEntity> type, Level level) {
        super(type, level);
    }

    // =====================================================================================
    //  Fábricas
    // =====================================================================================

    /** Bala de MAGIA: copia os aumentos da forma. Depois: {@link #fire} e {@code addFreshEntity}. */
    public static ManaBulletEntity spell(SpellContext ctx, LivingEntity shooter, Vec3 from, byte style) {
        ManaBulletEntity b = base(ctx.level(), shooter, from, ctx.color() & 0xFFFFFF, style);
        b.ctx = ctx;
        SpellStats s = ctx.formStats();
        b.pierceLeft = Math.max(0, s.pierce);
        b.wallBounces = Math.max(0, s.ricochet);
        b.homing = s.homing;
        b.ghost = s.ghost;
        b.arc = s.arc;
        b.sensitive = s.sensitive;
        b.impactRadius = s.aoe > 0 ? s.radius(1.5F) : 0F;
        float base = style == STYLE_SPIRIT ? 3.2F : style == STYLE_HEAVY ? 1.7F : 1.0F;
        b.entityData.set(SIZE, base + 0.2F * Math.max(0, s.amplify + s.charge));
        if (ctx.previousTarget() != null) b.hit.add(ctx.previousTarget());
        return b;
    }

    /** Bala SIMPLES (tiro básico da arma, Forasteiro): dano já calculado. */
    public static ManaBulletEntity plain(ServerLevel level, LivingEntity shooter, Vec3 from, float damage, int rgb, byte style) {
        ManaBulletEntity b = base(level, shooter, from, rgb, style);
        b.plainDamage = damage;
        b.entityData.set(SIZE, style == STYLE_PELLET ? 0.6F : style == STYLE_HEAVY ? 1.4F : 1.0F);
        return b;
    }

    private static ManaBulletEntity base(ServerLevel level, LivingEntity shooter, Vec3 from, int rgb, byte style) {
        ManaBulletEntity b = new ManaBulletEntity(GsRegistries.MANA_BULLET.get(), level);
        b.setOwner(shooter);
        b.setPos(from.x, from.y - 0.125, from.z);
        b.origin = shooter.position();
        b.initialized = true;
        b.entityData.set(COLOR, rgb);
        b.entityData.set(STYLE, style);
        // conta como "tiro" para o Foco (ClassResources: +Foco por projétil que acerta)
        b.addTag(ClassResources.FOCUS_SHOT_TAG);
        return b;
    }

    /**
     * Dispara na direção dada. A imprecisão do vanilla desvia cada eixo em até 0,0172 × imprecisão — para ângulos
     * pequenos isso é ≈ 1 grau por unidade, então a dispersão em graus vai direto.
     */
    public ManaBulletEntity fire(Vec3 dir, float speed, float spreadDeg) {
        shoot(dir.x, dir.y, dir.z, speed, Math.max(0F, spreadDeg));
        return this;
    }

    public ManaBulletEntity pierce(int n) { this.pierceLeft = Math.max(this.pierceLeft, n); return this; }
    public ManaBulletEntity seek(int bounces) { this.seekBounces = Math.max(0, bounces); return this; }
    public ManaBulletEntity knockback(float k) { this.knockback = k; return this; }
    public ManaBulletEntity lifetime(int ticks) { this.lifetime = ticks; return this; }
    /** Explode ao acertar (raio mínimo), como o Expandir. */
    public ManaBulletEntity blast(float radius) { this.impactRadius = Math.max(this.impactRadius, radius); return this; }

    public int color() { return entityData.get(COLOR); }
    public float size() { return entityData.get(SIZE); }
    public byte style() { return entityData.get(STYLE); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, GsFx.GOLD);
        builder.define(SIZE, 1.0F);
        builder.define(STYLE, STYLE_NORMAL);
    }

    @Override
    protected double getDefaultGravity() {
        return arc ? 0.05 : 0.004;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        if (!level().isClientSide && !initialized) {
            discard(); // carregada do disco: a magia não foi salva
            return;
        }
        super.tick();
        if (level().isClientSide) {
            clientTrail();
            return;
        }
        if (isRemoved()) return;
        if (tickCount > lifetime) {
            if (ctx != null) ctx.end(position(), getDeltaMovement());
            discard();
            return;
        }
        if (homing && tickCount > 2) steer(18.0, 0.25);
        if (ghost) noPhysics = true;
    }

    /** Faíscas de pó no rastro (poucas; menos com "partículas reduzidas"). */
    private void clientTrail() {
        boolean low;
        try {
            low = com.renanpontes.isekaimahou.core.MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            low = false;
        }
        if (tickCount % (low ? 4 : 2) != 0) return;
        Vec3 v = getDeltaMovement();
        Vec3 at = position().add(0, getBbHeight() * 0.5, 0).subtract(v.scale(0.3));
        level().addParticle(GsFx.dust(color(), 0.5F * size()), at.x, at.y, at.z, 0, 0, 0);
    }

    private void steer(double range, double weight) {
        LivingEntity best = nearestEnemy(range);
        if (best == null) return;
        Vec3 to = best.position().add(0, best.getBbHeight() * 0.6, 0).subtract(position()).normalize();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.normalize().lerp(to, weight).normalize().scale(v.length()));
        hasImpulse = true;
    }

    @Nullable
    private LivingEntity nearestEnemy(double range) {
        LivingEntity best = null;
        double bestD = range * range;
        Entity owner = getOwner();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), LivingEntity::isAlive)) {
            if (e == owner || hit.contains(e.getUUID()) || isFriend(e) || e.isSpectator()) continue;
            if (ctx == null && !(e instanceof net.minecraft.world.entity.monster.Enemy) && !(e instanceof net.minecraft.world.entity.player.Player)) continue;
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private boolean isFriend(Entity e) {
        if (ctx != null) return ctx.isAlly(e);
        return GunCombat.isAlly(getOwner(), e);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        if (target == owner || hit.contains(target.getUUID())) return false;
        if (target instanceof ManaBulletEntity) return false;
        // Aliados (grupo, facções aliadas, bichos do atirador): a bala passa por eles
        if (isFriend(target)) return false;
        return super.canHitEntity(target);
    }

    // =====================================================================================
    //  Acertos
    // =====================================================================================
    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel level)) return;
        Entity target = result.getEntity();
        hit.add(target.getUUID());
        Vec3 at = result.getLocation();
        Entity owner = getOwner();
        boolean head = target instanceof LivingEntity le && le.getBbHeight() > 0.6F && at.y >= le.getEyeY() - 0.3;
        boolean marked = GunMarks.marked(target, owner, level.getGameTime());
        float mult = 1.0F;
        if (head) mult *= GunCombat.headshotMultiplier(owner);
        if (marked) mult *= 1.35F;

        target.invulnerableTime = 0; // rajadas/leques: cada bala conta
        if (ctx != null) {
            SpellContext use = mult > 1.001F ? ctx.copy(ctx.powerMult() * mult) : ctx;
            ctx.impact(at, getDeltaMovement(), target);
            if (impactRadius > 0) {
                use.resolveArea(at, impactRadius, this, sensitive);
                GsFx.impact(level, at, color(), true);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            } else {
                use.resolveOnEntity(target, this);
            }
        } else if (plainDamage > 0) {
            DamageSource src = owner instanceof LivingEntity lo ? level.damageSources().mobProjectile(this, lo)
                    : level.damageSources().magic();
            target.hurt(src, plainDamage * mult);
        }
        if (knockback > 0 && target instanceof LivingEntity le) {
            Vec3 v = getDeltaMovement().multiply(1, 0, 1);
            if (v.lengthSqr() > 1.0E-4) {
                v = v.normalize().scale(knockback);
                le.knockback(knockback, -v.x, -v.z);
            }
        }
        GsFx.impact(level, at, color(), true);
        if (head) {
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.4);
            GsFx.sound(level, at, SoundEvents.ARROW_HIT_PLAYER, 0.8F, 1.6F);
        }
        if (owner instanceof ServerPlayer sp && target instanceof LivingEntity) GunCombat.onBulletHit(sp, target, head);

        // Tiro Ricochete: procura o próximo inimigo e muda de rumo
        if (seekBounces > 0) {
            LivingEntity next = nearestEnemy(SEEK_RANGE);
            if (next != null) {
                seekBounces--;
                Vec3 to = next.position().add(0, next.getBbHeight() * 0.6, 0).subtract(at).normalize();
                setPos(at.x, at.y, at.z);
                setDeltaMovement(to.scale(getDeltaMovement().length()));
                hasImpulse = true;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
                GsFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.9F);
                tickCount = Math.min(tickCount, lifetime - 20); // ganha fôlego para chegar
                return;
            }
        }
        if (pierceLeft > 0) {
            pierceLeft--;
            return;
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!(level() instanceof ServerLevel level) || ghost) return;
        Vec3 at = result.getLocation();
        if (wallBounces > 0) {
            wallBounces--;
            Vec3 v = getDeltaMovement();
            Vec3 n = Vec3.atLowerCornerOf(result.getDirection().getNormal());
            setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))));
            hasImpulse = true;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 5, 0.05, 0.05, 0.05, 0.15);
            GsFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 2.0F);
            return;
        }
        if (ctx != null) {
            ctx.impact(at, getDeltaMovement(), null);
            if (impactRadius > 0) ctx.resolveArea(at, impactRadius, this, true);
            else ctx.resolveOnBlock(result, this);
        }
        GsFx.impact(level, at, color(), false);
        discard();
    }

    /** Onde a bala nasceu (para abates de longe). */
    public Vec3 origin() {
        return origin;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // nada: a bala não sobrevive a salvar/carregar (ver tick)
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        initialized = false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }
}
