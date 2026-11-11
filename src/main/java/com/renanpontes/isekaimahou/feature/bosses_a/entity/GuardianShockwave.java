package com.renanpontes.isekaimahou.feature.bosses_a.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.bosses_a.BossSpells;
import com.renanpontes.isekaimahou.feature.bosses_a.BossesARegistry;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Onda de choque que se expande pelo chão (anel inteiro ou um arco numa direção).
 * Usada por todos os Guardiões: onda de fogo (Ignis), maré (Maré), terremoto (Golem), rajada (Zéfiro).
 * Cada alvo é atingido uma vez. Só servidor (o visual são partículas); não é salva no mundo.
 */
public class GuardianShockwave extends Entity {
    private UUID ownerId;
    private Element element = Element.ARCANE;
    private float radius;
    private float maxRadius = 10F;
    private float speed = 0.6F;
    private float damage = 8F;
    /** Metade da abertura do arco (radianos); PI = anel completo. */
    private double halfAngle = Math.PI;
    private double dirYaw;
    private int igniteSeconds;
    private int soakTicks;
    private int stunTicks;
    private double knock = 0.6;
    private double lift = 0.3;
    /** Terremoto: só atinge quem está no chão (pular desvia). */
    private boolean groundOnly;
    /** Chamas azuis (Ignis na fase 2+). */
    private boolean blue;
    /** Poupa quem ainda está atordoado (2ª onda: atordoado não consegue pular, então não pode levar de novo). */
    private boolean spareStunned;
    private final Set<UUID> hit = new HashSet<>();

    public GuardianShockwave(EntityType<? extends GuardianShockwave> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Cria a onda (ainda não adicionada ao mundo). Configure e chame {@link #launch()}. */
    public static GuardianShockwave create(LivingEntity owner, Vec3 center, Element element) {
        GuardianShockwave w = new GuardianShockwave(BossesARegistry.SHOCKWAVE.get(), owner.level());
        w.ownerId = owner.getUUID();
        w.element = element;
        w.setPos(center.x, center.y, center.z);
        return w;
    }

    public GuardianShockwave radius(float max, float speed) { this.maxRadius = max; this.speed = speed; return this; }
    public GuardianShockwave damage(float d) { this.damage = d; return this; }
    public GuardianShockwave arc(Vec3 dir, double halfAngleDeg) {
        this.dirYaw = Math.atan2(dir.z, dir.x);
        this.halfAngle = Math.toRadians(halfAngleDeg);
        return this;
    }
    public GuardianShockwave ignite(int seconds) { this.igniteSeconds = seconds; return this; }
    public GuardianShockwave soak(int ticks) { this.soakTicks = ticks; return this; }
    public GuardianShockwave stun(int ticks) { this.stunTicks = ticks; return this; }
    public GuardianShockwave knock(double strength, double lift) { this.knock = strength; this.lift = lift; return this; }
    public GuardianShockwave groundOnly() { this.groundOnly = true; return this; }
    public GuardianShockwave blue(boolean b) { this.blue = b; return this; }
    public GuardianShockwave spareStunned() { this.spareStunned = true; return this; }

    public void launch() {
        level().addFreshEntity(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        if (!(owner instanceof LivingEntity caster)) {
            discard();
            return;
        }
        float inner = radius;
        radius += speed;
        if (radius > maxRadius) {
            discard();
            return;
        }
        drawRing(level);
        hitRing(level, caster, inner);
    }

    private boolean inArc(double dx, double dz) {
        if (halfAngle >= Math.PI) return true;
        double a = Math.atan2(dz, dx) - dirYaw;
        return Math.abs(Mth.wrapDegrees(Math.toDegrees(a))) <= Math.toDegrees(halfAngle);
    }

    private void drawRing(ServerLevel level) {
        double span = halfAngle >= Math.PI ? Math.PI * 2 : halfAngle * 2;
        // Cada ponto é um pacote de partículas por jogador: densidade moderada (cada pacote já espalha 2–4 partículas)
        int points = Mth.clamp((int) (radius * span * 1.0), 8, 90);
        double start = halfAngle >= Math.PI ? 0 : dirYaw - halfAngle;
        for (int i = 0; i < points; i++) {
            double a = start + span * (i + random.nextDouble() * 0.5) / points;
            double x = getX() + Math.cos(a) * radius;
            double z = getZ() + Math.sin(a) * radius;
            double y = BossSpells.groundY(level, x, getY() + 0.5, z, 2, 4);
            spawnPoint(level, x, y, z, Math.cos(a), Math.sin(a));
        }
    }

    private void spawnPoint(ServerLevel level, double x, double y, double z, double ox, double oz) {
        switch (element) {
            case FIRE -> {
                level.sendParticles(blue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y + 0.2, z, 2, 0.15, 0.25, 0.15, 0.02);
                if (random.nextInt(4) == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.4, z, 1, 0.1, 0.1, 0.1, 0.01);
                if (random.nextInt(10) == 0) level.sendParticles(ParticleTypes.LAVA, x, y + 0.1, z, 1, 0, 0, 0, 0);
            }
            case WATER -> {
                level.sendParticles(ParticleTypes.SPLASH, x, y + 0.3, z, 4, 0.2, 0.3, 0.2, 0.1);
                level.sendParticles(ElementFx.dust(Element.WATER, 1.5F), x, y + 0.2 + random.nextDouble() * 1.4, z, 2, 0.15, 0.3, 0.15, 0);
                if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.5, z, 2, 0.2, 0.3, 0.2, 0.02);
            }
            case EARTH -> {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BossSpells.groundState(level, new Vec3(x, y, z))),
                        x, y + 0.15, z, 4, 0.2, 0.1, 0.2, 0.15);
                if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.POOF, x, y + 0.2, z, 1, 0.1, 0.05, 0.1, 0.02);
            }
            case WIND -> {
                level.sendParticles(ParticleTypes.CLOUD, x, y + 0.4, z, 1, 0.1, 0.2, 0.1, 0.0);
                level.sendParticles(ElementFx.dust(Element.WIND, 1.2F), x, y + 0.3 + random.nextDouble(), z, 1, 0.1, 0.2, 0.1, 0);
                if (random.nextInt(5) == 0) level.sendParticles(ParticleTypes.SMALL_GUST, x, y + 0.6, z, 1, 0, 0, 0, 0);
            }
            default -> ElementFx.burst(level, new Vec3(x, y + 0.2, z), element, 2, 0.1);
        }
    }

    private void hitRing(ServerLevel level, LivingEntity caster, float inner) {
        AABB box = new AABB(getX() - radius - 1, getY() - 3, getZ() - radius - 1, getX() + radius + 1, getY() + 4, getZ() + radius + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> BossSpells.isFoe(caster, e))) {
            if (hit.contains(e.getUUID())) continue;
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            double pad = e.getBbWidth() * 0.5 + 0.6;
            if (d < inner - pad || d > radius + pad || !inArc(dx, dz)) continue;
            if (groundOnly && !e.onGround()) continue;
            if (spareStunned && e.hasEffect(CoreRegistries.STUNNED)) continue;
            hit.add(e.getUUID());
            BossSpells.hit(caster, e, element, damage);
            if (igniteSeconds > 0) e.igniteForSeconds(igniteSeconds);
            if (soakTicks > 0) e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, soakTicks, 0), caster);
            // Não atordoa de novo quem ainda está atordoado (2º terremoto da fase 3 não vira prisão)
            if (stunTicks > 0 && !e.hasEffect(CoreRegistries.STUNNED)) {
                e.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, stunTicks, 0), caster);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, stunTicks + 20, 0), caster);
            }
            Vec3 out = d < 1.0E-3 ? new Vec3(Math.cos(dirYaw), 0, Math.sin(dirYaw)) : new Vec3(dx, 0, dz);
            BossSpells.knock(e, out, knock, lift);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
