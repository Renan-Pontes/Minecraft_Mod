package com.renanpontes.isekaimahou.feature.legends_d.goat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;
import com.renanpontes.isekaimahou.feature.legends_d.LdRegistry;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Transmutar em Cabra. Mobs (não-chefes): viram uma cabra do vanilla por um tempo; o NBT COMPLETO do original fica
 * guardado nos dados persistentes da cabra (salvo junto com o mundo) e ele volta igualzinho no fim. Se a cabra
 * morrer, o original morre no lugar (loot normal, abate creditado ao conjurador). Jogadores: Maldição da Cabra
 * (efeito {@code goat_curse}): sem atacar nem conjurar, berrando — e os outros o veem como cabra.
 */
public final class Goatify {
    private static final String KEY = "isekai_mahou:legends_d_goat";
    private static final net.minecraft.resources.ResourceLocation HEALTH_ID = IsekaiMahou.id("legends_d_goat_health");
    /** Cabras amaldiçoadas carregadas (uuid → dimensão), para conferir o tempo. */
    private static final Map<UUID, ResourceKey<Level>> ACTIVE = new HashMap<>();

    public static boolean isGoat(Entity e) {
        return e instanceof Goat && e.getPersistentData().contains(KEY);
    }

    public static boolean canTransform(LivingEntity e) {
        return e instanceof Mob && !(e instanceof Goat) && e.isAlive() && !BossKit.isBossLike(e)
                && e.getType().canSerialize() && !(e instanceof Player);
    }

    /** Troca o mob por uma cabra por {@code duration} ticks. */
    public static boolean transform(ServerLevel level, Mob mob, int duration, @Nullable LivingEntity caster) {
        Goat goat = EntityType.GOAT.create(level);
        if (goat == null) return false;
        mob.ejectPassengers();
        mob.stopRiding();
        float ratio = mob.getHealth() / Math.max(1F, mob.getMaxHealth());
        float origMax = mob.getMaxHealth();
        CompoundTag data = mob.saveWithoutId(new CompoundTag());
        data.putString("id", EntityType.getKey(mob.getType()).toString());

        goat.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0);
        goat.setYHeadRot(mob.getYHeadRot());
        goat.setScreamingGoat(true);
        goat.setPersistenceRequired();
        if (mob.hasCustomName()) goat.setCustomName(mob.getCustomName());
        // Cabra com metade da vida máxima do original (mín. a de uma cabra), na mesma proporção de vida
        AttributeInstance hp = goat.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && origMax * 0.5F > hp.getBaseValue()) {
            hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, origMax * 0.5F - hp.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
        }
        goat.setHealth(Math.max(1F, goat.getMaxHealth() * ratio));
        CompoundTag k = new CompoundTag();
        k.put("Data", data);
        k.putLong("Until", level.getGameTime() + duration);
        if (caster != null) k.putUUID("Caster", caster.getUUID());
        goat.getPersistentData().put(KEY, k);

        Vec3 c = LdUtil.center(mob);
        mob.discard();
        level.addFreshEntity(goat);
        ACTIVE.put(goat.getUUID(), level.dimension());
        puff(level, c, 1.0F);
        LdUtil.sound(level, c, SoundEvents.GOAT_SCREAMING_AMBIENT, 1.6F, 1.0F);
        LdUtil.sound(level, c, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.3F);
        return true;
    }

    /** "Puf": fumaça, brilho de feitiço e lã voando. */
    public static void puff(ServerLevel level, Vec3 c, float size) {
        LdUtil.send(level, ParticleTypes.POOF, c, (int) (28 * size), 0.45 * size, 0.06);
        LdUtil.send(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, c, (int) (6 * size), 0.35 * size, 0.02);
        LdUtil.send(level, ParticleTypes.WITCH, c, (int) (18 * size), 0.5 * size, 0.1);
        LdUtil.send(level, LdUtil.dust(0xF2EBDD, 1.4F), c, (int) (14 * size), 0.5 * size, 0);
    }

    /** Desfaz a transmutação. {@code killedBy} != null: a cabra morreu e o original morre no lugar. */
    public static void revert(ServerLevel level, Goat goat, @Nullable DamageSource killedBy) {
        CompoundTag k = goat.getPersistentData().getCompound(KEY);
        goat.getPersistentData().remove(KEY);
        ACTIVE.remove(goat.getUUID());
        Optional<Entity> opt = EntityType.create(k.getCompound("Data"), level);
        Vec3 c = LdUtil.center(goat);
        // O que a cabra sofreu fica no original (na mesma proporção da vida)
        float goatRatio = goat.getHealth() / Math.max(1F, goat.getMaxHealth());
        goat.discard();
        puff(level, c, 0.8F);
        if (opt.isEmpty()) return;
        Entity original = opt.get();
        original.moveTo(goat.getX(), goat.getY(), goat.getZ(), goat.getYRot(), original.getXRot());
        original.setDeltaMovement(Vec3.ZERO);
        // Evita conflito de UUID com uma cópia que já esteja no mundo
        if (level.getEntity(original.getUUID()) != null) original.setUUID(UUID.randomUUID());
        if (killedBy == null && original instanceof LivingEntity le0 && le0.getHealth() > 0F) {
            le0.setHealth(Math.max(1F, Math.min(le0.getHealth(), le0.getMaxHealth() * goatRatio)));
        }
        level.addFreshEntity(original);
        LdUtil.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.0F);
        if (killedBy != null && original instanceof LivingEntity le) {
            ServerPlayer caster = k.hasUUID("Caster") ? level.getServer().getPlayerList().getPlayer(k.getUUID("Caster")) : null;
            DamageSource source = killedBy;
            // O abate é de quem conjurou (a não ser que outro JOGADOR tenha matado a cabra): queda, mob, fogo...
            if (!(killedBy.getEntity() instanceof Player) && caster != null) {
                source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE), caster, caster);
            }
            if (source.getEntity() instanceof Player p) le.setLastHurtByPlayer(p);
            else if (caster != null) le.setLastHurtByPlayer(caster);
            le.setHealth(0.0F);
            le.die(source);
        }
    }

    // =====================================================================================
    //  Jogadores: Maldição da Cabra
    // =====================================================================================
    public static void curse(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(LdRegistry.GOAT_CURSE, ticks, 0, false, false, true));
        LdNet.near(p.serverLevel(), p.position(), 96, new LdNet.GoatCurse(p.getId(), ticks));
        puff(p.serverLevel(), LdUtil.center(p), 1.0F);
        LdUtil.sound(p.serverLevel(), p.position(), SoundEvents.GOAT_SCREAMING_AMBIENT, 1.6F, 1.0F);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.legends_d.goat_cursed").withStyle(ChatFormatting.GOLD), true);
    }

    private static boolean cursed(@Nullable Entity e) {
        return e instanceof LivingEntity le && le.hasEffect(LdRegistry.GOAT_CURSE);
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && isGoat(e.getEntity())) ACTIVE.put(e.getEntity().getUUID(), e.getLevel().dimension());
    }

    public static void onLeave(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide()) ACTIVE.remove(e.getEntity().getUUID());
    }

    /** A cabra morreu: o original morre no lugar (sem cadáver de cabra, sem loot de cabra). */
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Goat goat) || !(goat.level() instanceof ServerLevel sl) || !isGoat(goat)) return;
        e.setCanceled(true);
        revert(sl, goat, e.getSource());
    }

    public static void onAttack(AttackEntityEvent e) {
        if (!e.getEntity().isCreative() && cursed(e.getEntity())) e.setCanceled(true);
    }

    public static void onCast(SpellCastEvent.Pre e) {
        LivingEntity c = e.getCaster();
        if (c == null || c.level().isClientSide || !cursed(c)) return;
        e.setCanceled(true);
        LdUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_d.goat_no_cast").withStyle(ChatFormatting.GOLD));
        if (c.level() instanceof ServerLevel sl) LdUtil.sound(sl, c.position(), SoundEvents.GOAT_SCREAMING_AMBIENT, 1.2F, 1.2F);
    }

    /** Quem começa a ver um jogador amaldiçoado precisa saber (para desenhar a cabra). */
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof Player target) || !(e.getEntity() instanceof ServerPlayer viewer)) return;
        MobEffectInstance inst = target.getEffect(LdRegistry.GOAT_CURSE);
        if (inst != null) LdNet.to(viewer, new LdNet.GoatCurse(target.getId(), inst.getDuration()));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MobEffectInstance inst = sp.getEffect(LdRegistry.GOAT_CURSE);
        if (inst != null) LdNet.to(sp, new LdNet.GoatCurse(sp.getId(), inst.getDuration()));
    }

    /** Renasceu (o efeito não vem junto): quem via a cabra volta a ver o jogador. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MobEffectInstance inst = sp.getEffect(LdRegistry.GOAT_CURSE);
        LdNet.near(sp.serverLevel(), sp.position(), 128, new LdNet.GoatCurse(sp.getId(), inst != null ? inst.getDuration() : 0));
    }

    /** Trocou de dimensão: o cliente limpou o estado; manda de novo (para ele e quem está perto). */
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MobEffectInstance inst = sp.getEffect(LdRegistry.GOAT_CURSE);
        if (inst != null) LdNet.near(sp.serverLevel(), sp.position(), 128, new LdNet.GoatCurse(sp.getId(), inst.getDuration()));
    }

    /** Leite/fim do efeito: a cabra some para todo mundo. */
    public static void onEffectRemoved(MobEffectEvent.Remove e) {
        if (e.getEffect() != null && e.getEffect().is(LdRegistry.GOAT_CURSE.getKey()) && e.getEntity() instanceof ServerPlayer sp) {
            LdNet.near(sp.serverLevel(), sp.position(), 128, new LdNet.GoatCurse(sp.getId(), 0));
        }
    }

    public static void onEffectExpired(MobEffectEvent.Expired e) {
        MobEffectInstance inst = e.getEffectInstance();
        if (inst != null && inst.getEffect().is(LdRegistry.GOAT_CURSE.getKey()) && e.getEntity() instanceof ServerPlayer sp) {
            LdNet.near(sp.serverLevel(), sp.position(), 128, new LdNet.GoatCurse(sp.getId(), 0));
            puff(sp.serverLevel(), LdUtil.center(sp), 0.7F);
        }
    }

    /** A cada 10 ticks: volta os que venceram e solta brilho de maldição nas cabras. */
    public static void tick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (ACTIVE.isEmpty() || server.getTickCount() % 10 != 0) return;
        List<Runnable> reverts = new ArrayList<>();
        for (Map.Entry<UUID, ResourceKey<Level>> en : new ArrayList<>(ACTIVE.entrySet())) {
            ServerLevel level = server.getLevel(en.getValue());
            if (level == null || !(level.getEntity(en.getKey()) instanceof Goat goat) || !isGoat(goat)) {
                ACTIVE.remove(en.getKey());
                continue;
            }
            long until = goat.getPersistentData().getCompound(KEY).getLong("Until");
            if (level.getGameTime() >= until) {
                reverts.add(() -> revert(level, goat, null));
            } else if (server.getTickCount() % 20 == 0) {
                level.sendParticles(ParticleTypes.WITCH, goat.getX(), goat.getY() + 1.0, goat.getZ(), 2, 0.25, 0.25, 0.25, 0.0);
            }
        }
        reverts.forEach(Runnable::run);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private Goatify() {}
}
