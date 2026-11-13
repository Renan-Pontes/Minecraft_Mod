package com.renanpontes.isekaimahou.feature.origins_a.curse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Virar Sapo: troca um mob comum (não chefe, vida máxima ≤ 40) por um sapo do vanilla. O NBT completo do mob
 * fica guardado nos dados persistentes do sapo; quando o tempo acaba ele volta igualzinho (vida, equipamento,
 * nome...). Se o sapo morrer, a criatura original volta só para morrer no lugar (dropa o loot dela).
 */
public final class Frogify {
    private static final String KEY = "isekai_mahou:frogified";
    public static final float MAX_HEALTH = 40.0F;

    /** Sapos amaldiçoados carregados (uuid → dimensão), para conferir o tempo. */
    private static final Map<UUID, ResourceKey<Level>> ACTIVE = new HashMap<>();

    public static boolean isFrogified(Entity e) {
        return e instanceof Frog && e.getPersistentData().contains(KEY);
    }

    public static boolean canFrogify(LivingEntity e) {
        return e instanceof Mob && !(e instanceof Frog) && e.isAlive() && !e.getType().is(Tags.EntityTypes.BOSSES)
                && e.getMaxHealth() <= MAX_HEALTH && e.getType().canSerialize() && !(e instanceof Player);
    }

    public static boolean transform(ServerLevel level, Mob mob, int duration) {
        Frog frog = EntityType.FROG.create(level);
        if (frog == null) return false;
        mob.ejectPassengers();
        mob.stopRiding();
        CompoundTag data = mob.saveWithoutId(new CompoundTag());
        data.putString("id", EntityType.getKey(mob.getType()).toString());

        frog.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0);
        frog.setVariant(variantFor(level, mob));
        frog.setPersistenceRequired();
        if (mob.hasCustomName()) frog.setCustomName(mob.getCustomName());
        CompoundTag k = new CompoundTag();
        k.put("Data", data);
        k.putLong("Until", level.getGameTime() + duration);
        frog.getPersistentData().put(KEY, k);

        Vec3 c = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
        mob.discard();
        level.addFreshEntity(frog);
        ACTIVE.put(frog.getUUID(), level.dimension());
        level.sendParticles(Vfx.poof(), c.x, c.y, c.z, 24, 0.4, 0.4, 0.4, 0.05);
        level.sendParticles(Vfx.witch(), c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.1);
        level.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y, c.z, 12, 0.4, 0.3, 0.4, 0.1);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.9F, 1.2F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.FROG_AMBIENT, SoundSource.NEUTRAL, 1.2F, 0.9F);
        return true;
    }

    /** Sapo "da cor" do mob: frio para mobs gelados/aquáticos, quente para os do Nether/deserto. */
    private static Holder<FrogVariant> variantFor(ServerLevel level, Mob mob) {
        ResourceKey<FrogVariant> key = FrogVariant.TEMPERATE;
        if (mob.fireImmune()) key = FrogVariant.WARM;
        else if (!mob.canFreeze() || mob.isInWaterOrBubble()) key = FrogVariant.COLD;
        return BuiltInRegistries.FROG_VARIANT.getHolderOrThrow(key);
    }

    /** Desfaz a maldição. {@code killedBy} != null: o sapo morreu e o original morre no lugar. */
    public static void revert(ServerLevel level, Frog frog, DamageSource killedBy) {
        CompoundTag k = frog.getPersistentData().getCompound(KEY);
        frog.getPersistentData().remove(KEY);
        ACTIVE.remove(frog.getUUID());
        Optional<Entity> opt = EntityType.create(k.getCompound("Data"), level);
        Vec3 c = frog.position().add(0, 0.3, 0);
        if (opt.isEmpty()) {
            if (killedBy == null) frog.setPersistenceRequired();
            return;
        }
        Entity original = opt.get();
        original.moveTo(frog.getX(), frog.getY(), frog.getZ(), frog.getYRot(), original.getXRot());
        original.setDeltaMovement(Vec3.ZERO);
        if (killedBy == null) frog.discard();
        // Evita conflito de UUID com uma cópia que já esteja no mundo
        if (level.getEntity(original.getUUID()) != null) original.setUUID(UUID.randomUUID());
        level.addFreshEntity(original);
        level.sendParticles(Vfx.poof(), c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.05);
        level.sendParticles(Vfx.witch(), c.x, c.y, c.z, 12, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (killedBy != null && original instanceof LivingEntity le) {
            if (killedBy.getEntity() instanceof Player p) le.setLastHurtByPlayer(p);
            le.setHealth(0.0F);
            le.die(killedBy);
        }
    }

    // ---------------- Rastreamento ----------------
    public static void onJoin(Entity e, ServerLevel level) {
        if (isFrogified(e)) ACTIVE.put(e.getUUID(), level.dimension());
    }

    public static void onLeave(Entity e) {
        ACTIVE.remove(e.getUUID());
    }

    /** A cada 10 ticks: volta os que venceram e solta brilho de maldição nos sapos. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty() || server.getTickCount() % 10 != 0) return;
        List<Runnable> reverts = new ArrayList<>();
        for (Map.Entry<UUID, ResourceKey<Level>> en : new ArrayList<>(ACTIVE.entrySet())) {
            ServerLevel level = server.getLevel(en.getValue());
            if (level == null || !(level.getEntity(en.getKey()) instanceof Frog frog) || !isFrogified(frog)) {
                ACTIVE.remove(en.getKey());
                continue;
            }
            long until = frog.getPersistentData().getCompound(KEY).getLong("Until");
            if (level.getGameTime() >= until) {
                reverts.add(() -> revert(level, frog, null));
            } else if (server.getTickCount() % 20 == 0) {
                level.sendParticles(Vfx.witch(), frog.getX(), frog.getY() + 0.6, frog.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
        }
        reverts.forEach(Runnable::run);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private Frogify() {}
}
