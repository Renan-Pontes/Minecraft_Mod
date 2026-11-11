package com.renanpontes.isekaimahou.feature.origins_a.rune;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Selo Explosivo: runa no chão que arma em 1s e explode quando um inimigo pisa nela.
 * A explosão é mágica (dano do elemento da magia, com reações) e NÃO quebra blocos nem fere aliados.
 */
public class ExplosiveSealEntity extends OriginRuneEntity {
    public static final int MAX_PER_OWNER = 6;
    private static final float TRIGGER = 1.1F;

    private int armTicks = 20;
    private float damage = 7.0F;
    private float radius = 2.5F;
    private Element element = Element.FIRE;
    private CompoundTag spell;

    public ExplosiveSealEntity(EntityType<? extends ExplosiveSealEntity> type, Level level) {
        super(type, level);
    }

    /** Cria e coloca um selo (removendo os mais antigos acima do limite do dono). */
    public static ExplosiveSealEntity place(SpellContext ctx, BlockPos at, SpellStats stats, int armTicks) {
        ServerLevel level = ctx.level();
        UUID owner = ctx.casterId();
        Vec3 pos = Vec3.atBottomCenterOf(at).add(0, 0.02, 0);
        // Não empilha selos no mesmo lugar
        if (!level.getEntitiesOfClass(ExplosiveSealEntity.class, new AABB(pos, pos).inflate(0.9),
                e -> owner != null && owner.equals(e.ownerId())).isEmpty()) return null;
        enforceLimit(level, OaRegistries.EXPLOSIVE_SEAL.get(), owner, MAX_PER_OWNER);
        ExplosiveSealEntity seal = new ExplosiveSealEntity(OaRegistries.EXPLOSIVE_SEAL.get(), level);
        seal.moveTo(pos.x, pos.y, pos.z, ctx.caster() != null ? ctx.caster().getYRot() : 0, 0);
        seal.setOwner(owner);
        seal.setColor(ctx.color());
        seal.element = ctx.element();
        seal.damage = 7.0F * stats.power();
        seal.radius = stats.radius(2.5F);
        seal.armTicks = armTicks;
        seal.setLifetime(stats.duration(6000));
        seal.spell = ctx.save();
        level.addFreshEntity(seal);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
        ElementFx.ring(level, pos, ctx.element(), 0.6, 12);
        return seal;
    }

    public boolean armed() { return age >= armTicks; }

    @Override
    protected void serverTick(ServerLevel level) {
        if (age == armTicks) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 0.6F, 0.8F);
        }
        if (!armed() || age % 2 != 0) return;
        UUID owner = ownerId();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(TRIGGER, 1.2, TRIGGER))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double dy = e.getY() - getY();
            // Só quem está pisando (não quem passa no andar de baixo)
            if (dx * dx + dz * dz <= TRIGGER * TRIGGER && dy > -0.5 && dy < 1.0 && OaUtil.isThreat(level, owner, e)) {
                detonate(level);
                return;
            }
        }
    }

    private void detonate(ServerLevel level) {
        UUID owner = ownerId();
        Vec3 c = position().add(0, 0.3, 0);
        SpellContext ctx = SpellContext.load(level, spell);
        SpellStats flat = new SpellStats();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius))) {
            if (e.distanceToSqr(c) > radius * radius || !OaUtil.isThreat(level, owner, e)) continue;
            float falloff = 1.0F - 0.4F * (float) (Math.sqrt(e.distanceToSqr(c)) / radius);
            if (ctx != null) ctx.damage(e, damage * falloff, flat, this);
            else e.hurt(level.damageSources().explosion(this, null), damage * falloff);
            Vec3 push = e.position().subtract(c).normalize().scale(0.7);
            e.push(push.x, 0.45, push.z);
            e.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.2, 0.3, 0);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 30, radius * 0.3, 0.3, radius * 0.3, 0.08);
        ElementFx.burst(level, c, element, 30, radius * 0.35);
        ElementFx.ring(level, position(), element, radius, Math.max(16, (int) (radius * 10)));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.1F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 0.8F, 0.8F);
        discard();
    }

    @Override
    protected void onExpire(ServerLevel level) {
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 8, 0.35, 0.02, 0.35, 0.01);
    }

    @Override
    protected void clientTick() {
        // Brasas raras: a armadilha é quase invisível para quem não sabe que ela está ali
        if (random.nextInt(armed() ? 18 : 4) != 0) return;
        int c = color();
        Vector3f rgb = new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F);
        double a = random.nextDouble() * Math.PI * 2;
        double d = 0.25 + random.nextDouble() * 0.3;
        level().addParticle(new DustParticleOptions(rgb, 0.6F), getX() + Math.cos(a) * d, getY() + 0.05,
                getZ() + Math.sin(a) * d, 0, 0.015, 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Arm", armTicks);
        tag.putFloat("Damage", damage);
        tag.putFloat("Radius", radius);
        tag.putString("Element", element.id);
        if (spell != null) tag.put("Spell", spell);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        armTicks = tag.getInt("Arm");
        damage = tag.contains("Damage") ? tag.getFloat("Damage") : 7.0F;
        radius = tag.contains("Radius") ? tag.getFloat("Radius") : 2.5F;
        Element e = Element.byId(tag.getString("Element"));
        element = e != null ? e : Element.FIRE;
        spell = tag.contains("Spell") ? tag.getCompound("Spell") : null;
    }
}
