package com.renanpontes.isekaimahou.feature.espada.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.espada.EspadaRegistries;
import com.renanpontes.isekaimahou.feature.espada.EspadaSounds;
import com.renanpontes.isekaimahou.feature.espada.aging.Aging;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projétil de energia dos Espada (sem gravidade, some sozinho):
 * <ul>
 *   <li>{@link #BALA}: tiro verde rápido e fraco de Ulquiorra (em rajada);</li>
 *   <li>{@link #LANCE}: lança de Luz de la Luna — explode em área pequena ao tocar;</li>
 *   <li>{@link #DART}: dardo de Garra de la Pantera — faz sangrar;</li>
 *   <li>{@link #AXE}: Gran Caída arremessada por Barragan — envelhece quem acerta;</li>
 *   <li>{@link #LANZA}: Lanza del Relámpago (v1.3.2) — explode NO IMPACTO (entidade, bloco ou fim do alcance) numa
 *       grande explosão de raios verdes ({@link Ulquiorra#lanzaBlast});</li>
 *   <li>{@link #CLAW}: lâmina de Desgarrón (v1.3.2) — garra azul gigante que ATRAVESSA os alvos (acerta cada um uma vez),
 *       faz sangrar e só para em parede.</li>
 * </ul>
 * O visual é um risco brilhante desenhado pelo renderer do cliente (cor e tamanho pelo tipo).
 * <p>
 * w10: a garra e a Lanza também saem das Magias Lendárias dos jogadores ({@code EspadaTechniques}): com uma
 * {@link SpellContext} ligada ({@link #spell}), os alvos e o dano seguem as regras da magia (aliados, grupo, PvP, poder).
 */
public class EspadaBolt extends ThrowableProjectile {
    public static final int BALA = 0, LANCE = 1, DART = 2, AXE = 3, LANZA = 4, CLAW = 5;
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(EspadaBolt.class, EntityDataSerializers.INT);
    private static final int MAX_LIFE = 80;

    private float damage = 4F;
    private int life = MAX_LIFE;
    /** Garra: quem já foi cortado por esta lâmina (só servidor). */
    private final Set<Integer> clawHits = new HashSet<>();
    private boolean exploded;
    /** w10: magia do jogador que lançou (só servidor; não é salva — se o chunk descarregar, a lâmina some com ela). */
    private SpellContext spell;
    /** w10: no máximo quantos alvos cada garra corta (limite de desempenho). */
    private static final int MAX_CLAW_HITS = 12;

    public EspadaBolt(EntityType<? extends EspadaBolt> type, Level level) {
        super(type, level);
    }

    /** Dispara de {@code from} na direção {@code dir}; some depois de ~{@code range} blocos. */
    public static EspadaBolt shoot(ServerLevel level, LivingEntity owner, int kind, Vec3 from, Vec3 dir, float speed, float damage, double range) {
        EspadaBolt b = new EspadaBolt(EspadaRegistries.BOLT.get(), level);
        b.setOwner(owner);
        b.entityData.set(DATA_KIND, kind);
        b.setPos(from.x, from.y, from.z);
        b.damage = damage;
        b.life = Math.max(4, Math.min(MAX_LIFE, (int) Math.ceil(range / Math.max(0.1F, speed)) + 2));
        b.shoot(dir.x, dir.y, dir.z, speed, 0.0F);
        level.addFreshEntity(b);
        return b;
    }

    /** w10: liga a magia do jogador (alvos e dano pela magia). Devolve o próprio projétil. */
    public EspadaBolt spell(SpellContext ctx) {
        this.spell = ctx;
        return this;
    }

    public int kind() {
        return entityData.get(DATA_KIND);
    }

    public int color() {
        return switch (kind()) {
            case DART, CLAW -> EspadaFx.GRIMM_BLUE;
            case AXE -> EspadaFx.RESPIRA_LIGHT;
            default -> EspadaFx.ULQ_GREEN;
        };
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, BALA);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int k = kind();
            if (k == LANZA) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0, 0, 0);
                if (tickCount % 2 == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
            } else if (k == CLAW) {
                if (tickCount % 2 == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY() + (random.nextDouble() - 0.5) * 2.4, getZ(), 0, 0, 0);
            } else if (tickCount % 2 == 0) {
                level().addParticle(k == AXE ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
            }
            return;
        }
        if (isRemoved()) return;
        if (kind() == CLAW) clawSweep((ServerLevel) level());
        if (tickCount > life) {
            if (kind() == LANCE) burst((ServerLevel) level(), position());
            if (kind() == LANZA) explodeLanza((ServerLevel) level(), position());
            discard();
        }
    }

    /** Garra de Desgarrón: corta quem a lâmina (alta, ~3 blocos) atravessa — uma vez por alvo. */
    private void clawSweep(ServerLevel sl) {
        if (clawHits.size() >= MAX_CLAW_HITS) return;
        LivingEntity owner = getOwner() instanceof LivingEntity lo ? lo : null;
        // A lâmina é alta (marca de garra de ~4 blocos): pega quem está no chão e quem pulou.
        AABB box = getBoundingBox().inflate(1.0, 2.0, 1.0);
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != owner && !clawHits.contains(e.getId())
                && validTarget(e))) {
            if (le instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) continue;
            if (clawHits.size() >= MAX_CLAW_HITS) break;
            clawHits.add(le.getId());
            boolean hit;
            if (spell != null) hit = spell.damage(le, damage, spell.formStats(), this, Element.WIND) > 0F;
            else hit = le.hurt(damageSources().mobProjectile(this, owner), damage);
            if (hit) {
                Statuses.apply(le, Statuses.Kind.BLEEDING, 2, 120, owner, 1F);
                Vec3 push = getDeltaMovement().multiply(1, 0, 1).normalize().scale(0.45);
                le.push(push.x, 0.15, push.z);
                le.hurtMarked = true;
                EspadaFx.spark(sl, le.position().add(0, le.getBbHeight() * 0.6, 0), EspadaFx.GRIMM_BLUE);
                sl.playSound(null, le.getX(), le.getY(), le.getZ(), EspadaSounds.CLAW.get(), net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 1.3F);
            }
        }
    }

    private void explodeLanza(ServerLevel sl, Vec3 at) {
        if (exploded) return;
        exploded = true;
        LivingEntity owner = getOwner() instanceof LivingEntity lo ? lo : null;
        if (spell != null) Ulquiorra.lanzaBlast(sl, at, owner, this, damage, spell);
        else Ulquiorra.lanzaBlast(sl, at, owner, this, damage);
    }

    /**
     * Quem este projétil pode ferir: com magia de jogador, os inimigos da magia; lançado por um jogador sem a magia
     * (chunk recarregado), nada de jogadores nem animais de estimação dele; senão (chefe), tudo menos os Espada e os
     * lacaios deles.
     */
    private boolean validTarget(Entity e) {
        if (spell != null) return !spell.isAlly(e) && !spell.ignores(e);
        if (getOwner() instanceof net.minecraft.world.entity.player.Player) {
            return !(e instanceof net.minecraft.world.entity.player.Player) && !(e instanceof net.minecraft.world.entity.OwnableEntity);
        }
        return !(e instanceof EspadaBoss) && !EspadaBoss.isMinion(e);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        // A garra atravessa as entidades (o corte é feito em clawSweep); só para em blocos.
        if (kind() == CLAW) return false;
        return !(target instanceof EspadaBolt) && validTarget(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Entity e = result.getEntity();
        LivingEntity owner = getOwner() instanceof LivingEntity lo ? lo : null;
        if (kind() == LANCE) {
            burst((ServerLevel) level(), position());
        } else if (kind() == LANZA) {
            // Explode no ponto do impacto (um pouco antes do corpo, para a explosão não "sumir" dentro dele).
            explodeLanza((ServerLevel) level(), result.getLocation());
        } else if (e instanceof LivingEntity le) {
            if (le.hurt(damageSources().mobProjectile(this, owner), damage)) {
                if (kind() == DART) Statuses.apply(le, Statuses.Kind.BLEEDING, 1, 100, owner, 1F);
                if (kind() == AXE) Aging.apply(le, 2, owner);
            }
            EspadaFx.spark((ServerLevel) level(), position(), color());
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide) return;
        if (kind() == LANCE) burst((ServerLevel) level(), result.getLocation());
        else if (kind() == LANZA) explodeLanza((ServerLevel) level(), result.getLocation());
        else if (kind() == CLAW) EspadaFx.explosion((ServerLevel) level(), result.getLocation(), 1.4F, EspadaFx.GRIMM_BLUE, EspadaFx.STYLE_BLUE);
        else EspadaFx.spark((ServerLevel) level(), result.getLocation(), color());
        discard();
    }

    /** Explosão pequena da lança (raio 2,5; sem quebrar blocos). */
    private void burst(ServerLevel sl, Vec3 at) {
        LivingEntity owner = getOwner() instanceof LivingEntity lo ? lo : null;
        List<LivingEntity> hits = sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5),
                le -> le.isAlive() && !(le instanceof EspadaBoss) && !EspadaBoss.isMinion(le) && le.distanceToSqr(at) < 6.25);
        for (LivingEntity le : hits) {
            if (le.hurt(damageSources().mobProjectile(this, owner), damage)) {
                Vec3 push = le.position().subtract(at).normalize().scale(0.6);
                le.push(push.x, 0.3, push.z);
                le.hurtMarked = true;
            }
        }
        EspadaFx.explosion(sl, at, 2.5F, EspadaFx.ULQ_GREEN, EspadaFx.STYLE_GREEN);
        sl.playSound(null, at.x, at.y, at.z, EspadaSounds.CERO_FIRE.get(), net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.6F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", kind());
        tag.putFloat("Damage", damage);
        tag.putInt("Life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_KIND, tag.getInt("Kind"));
        damage = tag.contains("Damage") ? tag.getFloat("Damage") : 4F;
        life = tag.contains("Life") ? Math.min(MAX_LIFE, tag.getInt("Life")) : MAX_LIFE;
    }
}
