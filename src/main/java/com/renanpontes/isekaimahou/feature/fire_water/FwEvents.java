package com.renanpontes.isekaimahou.feature.fire_water;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.fire_water.effect.FwMobEffects;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.entity.SpellProjectileEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Eventos de jogo do módulo: Renascer da Fênix (morte), Espelho d'Água (projéteis), amortecer a queda
 * depois da Propulsão, Afogar (respiração) e o estouro da Bolha.
 */
public final class FwEvents {
    private static final String CUSHION_KEY = IsekaiMahou.MODID + "_fw_cushion";

    public static void register() {
        // Alta prioridade: se a Fênix salvar, os contadores de abate do núcleo nem chegam a ver a morte
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, FwEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(FwEvents::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(FwEvents::onFall);
        NeoForge.EVENT_BUS.addListener(FwEvents::onBreathe);
        NeoForge.EVENT_BUS.addListener(FwEvents::onEffectExpired);
        // Por último: se outro mod cancelar a remoção, a corrente continua valendo
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FwEvents::onEffectRemoved);
    }

    /** Protege da próxima queda durante alguns ticks (Propulsão). */
    public static void cushion(LivingEntity e, int ticks) {
        e.getPersistentData().putLong(CUSHION_KEY, e.level().getGameTime() + ticks);
        e.resetFallDistance();
    }

    // =====================================================================================
    //  Renascer da Fênix
    // =====================================================================================

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity le = event.getEntity();
        if (!(le.level() instanceof ServerLevel sl)) return;
        MobEffectInstance blessing = le.getEffect(FwRegistries.PHOENIX_BLESSING);
        if (blessing == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        rebirth(sl, le, blessing.getAmplifier(), event.getSource().getEntity());
    }

    private static void rebirth(ServerLevel sl, LivingEntity le, int amp, Entity killer) {
        le.removeEffect(FwRegistries.PHOENIX_BLESSING);
        le.setHealth(le.getMaxHealth() * 0.5F);
        List<Holder<MobEffect>> bad = le.getActiveEffects().stream()
                .filter(i -> i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                .map(MobEffectInstance::getEffect).toList();
        bad.forEach(le::removeEffect);
        le.clearFire();
        le.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        le.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        le.invulnerableTime = 30;

        // Explosão de chamas: queima e afasta quem estava lutando contra o renascido
        Vec3 c = le.position().add(0, le.getBbHeight() * 0.5, 0);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, le.getBoundingBox().inflate(5.0),
                t -> t != le && t.isAlive() && !t.isAlliedTo(le) && !le.isAlliedTo(t)
                        && (t instanceof Enemy || t == killer || (t instanceof Mob m && m.getTarget() == le)))) {
            t.invulnerableTime = 0;
            t.hurt(FwUtil.summonSpellSource(le, le), 6.0F + 2.0F * amp);
            t.igniteForSeconds(5.0F);
            Vec3 push = t.position().subtract(le.position()).multiply(1, 0, 1);
            if (push.lengthSqr() > 1.0E-4) FwUtil.addMotion(t, push.normalize().scale(1.1).add(0, 0.45, 0));
        }
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(Vfx.totem(), c.x, c.y, c.z, 70, 0.4, 0.7, 0.4, 0.5);
        sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 20, 0.6, 0.4, 0.6, 0);
        FwUtil.shockwave(sl, le.position().add(0, 0.2, 0), Vfx.flame(), 48, 0.45);
        FwUtil.shockwave(sl, c, Vfx.flame(), 32, 0.3);
        // Asas de fogo abrindo atrás do renascido
        float yaw = le.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(0.4);
        for (int i = 1; i <= 10; i++) {
            double t = i / 10.0;
            double h = Math.sin(t * Math.PI) * 0.9 + t * 0.4;
            for (int s = -1; s <= 1; s += 2) {
                Vec3 p = c.add(back).add(right.scale(s * t * 1.8)).add(0, h, 0);
                sl.sendParticles(Vfx.flame(), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.01);
                sl.sendParticles(FwUtil.dust(0xFFC23D, 1.5F), p.x, p.y - 0.2, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        FwUtil.sound(sl, c, SoundEvents.TOTEM_USE, 1.0F, 1.1F);
        FwUtil.sound(sl, c, SoundEvents.BLAZE_SHOOT, 1.2F, 0.6F);
        FwUtil.sound(sl, c, SoundEvents.FIRECHARGE_USE, 1.0F, 0.7F);

        if (le instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.phoenix_rebirth")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            MagicApi.addCounter(sp, "feat.phoenix_rebirth", 1);
        }
    }

    // =====================================================================================
    //  Espelho d'Água
    // =====================================================================================

    private static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile p = event.getProjectile();
        // Anzol não é ataque: refletir trocaria o dono do anzol e quebraria a pesca de quem lançou
        if (p instanceof FishingHook) return;
        if (!(p.level() instanceof ServerLevel sl) || !(event.getRayTraceResult() instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof LivingEntity holder) || !holder.hasEffect(FwRegistries.WATER_MIRROR)) return;
        Entity shooter = p.getOwner();
        // Projétil próprio ou de aliado (amigo, invocação do próprio portador): o espelho não gasta a carga
        if (shooter == holder) return;
        if (shooter != null && (holder.isAlliedTo(shooter)
                || shooter instanceof OwnableEntity own && holder.getUUID().equals(own.getOwnerUUID()))) return;
        event.setCanceled(true);
        holder.removeEffect(FwRegistries.WATER_MIRROR);

        Vec3 v = p.getDeltaMovement();
        double speed = Math.max(0.8, v.length());
        Vec3 dir = shooter != null && shooter.isAlive() && shooter.level() == sl
                ? shooter.getEyePosition().subtract(p.position()).normalize()
                : v.lengthSqr() > 1.0E-6 ? v.normalize().scale(-1) : holder.getLookAngle();
        Vec3 out = p.position().add(dir.scale(holder.getBbWidth() * 0.5 + 0.8));

        if (p instanceof SpellProjectileEntity spell) {
            reflectSpell(sl, spell, holder, out, dir, speed);
        } else {
            p.setOwner(holder);
            p.setPos(out.x, out.y, out.z);
            p.setDeltaMovement(dir.scale(speed));
            p.hasImpulse = true;
        }

        Vec3 c = holder.position().add(0, holder.getBbHeight() * 0.5, 0);
        FwUtil.ring(sl, c, FwUtil.dust(0xBFF4FF, 1.3F), holder.getBbWidth() * 0.5 + 0.6, 20, 0);
        sl.sendParticles(Vfx.splash(), out.x, out.y, out.z, 20, 0.3, 0.3, 0.3, 0.2);
        FwUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.6F);
        FwUtil.sound(sl, c, SoundEvents.PLAYER_SPLASH, 0.7F, 1.4F);
        if (holder instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.water_mirror_reflect")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Projétil de magia: recria a mesma magia com o refletor como conjurador (para atingir quem lançou). */
    private static void reflectSpell(ServerLevel sl, SpellProjectileEntity spell, LivingEntity holder, Vec3 at, Vec3 dir, double speed) {
        CompoundTag tag = new CompoundTag();
        spell.saveWithoutId(tag);
        SpellContext old = SpellContext.load(sl, tag.getCompound("Spell"));
        spell.discard();
        if (old == null) return;
        SpellContext mirrored = new SpellContext(sl, holder, old.element(), old.recipe(), old.spell());
        mirrored.setPowerMult(old.powerMult());
        SpellProjectileEntity copy = SpellProjectileEntity.create(mirrored, holder);
        copy.setPos(at.x, at.y, at.z);
        copy.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0F);
        sl.addFreshEntity(copy);
    }

    // =====================================================================================
    //  Propulsão: queda amortecida
    // =====================================================================================

    private static void onFall(LivingFallEvent event) {
        LivingEntity le = event.getEntity();
        CompoundTag data = le.getPersistentData();
        if (!data.contains(CUSHION_KEY)) return;
        long until = data.getLong(CUSHION_KEY);
        data.remove(CUSHION_KEY);
        if (le.level().getGameTime() <= until) event.setDamageMultiplier(0.0F);
    }

    // =====================================================================================
    //  Afogar: sem respirar enquanto o efeito durar
    // =====================================================================================

    private static void onBreathe(LivingBreatheEvent event) {
        LivingEntity le = event.getEntity();
        MobEffectInstance choke = le.getEffect(FwRegistries.WATER_CHOKE);
        if (choke == null || immuneToDrowning(le)) return;
        event.setCanBreathe(false);
        event.setConsumeAirAmount(Math.max(event.getConsumeAirAmount(), 5 + 2 * choke.getAmplifier()));
    }

    /** Criaturas que respiram na água, Respiração Aquática/Conduíte e jogadores invulneráveis não se afogam. */
    @SuppressWarnings("deprecation")
    public static boolean immuneToDrowning(LivingEntity le) {
        return le.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER) || MobEffectUtil.hasWaterBreathing(le)
                || (le instanceof Player p && p.getAbilities().invulnerable);
    }

    // =====================================================================================
    //  Bolha: estoura no fim
    // =====================================================================================

    /** Removido por leite, Apagar, morte etc.: limpa a corrente guardada da Correnteza. */
    private static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect().is(FwRegistries.UNDERTOW)) FwMobEffects.Undertow.clearFlow(event.getEntity());
    }

    private static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance inst = event.getEffectInstance();
        LivingEntity le = event.getEntity();
        if (inst != null && inst.is(FwRegistries.UNDERTOW)) FwMobEffects.Undertow.clearFlow(le);
        if (inst == null || !inst.is(FwRegistries.BUBBLE_PRISON) || !(le.level() instanceof ServerLevel sl)) return;
        Vec3 c = le.position().add(0, le.getBbHeight() * 0.5, 0);
        FwUtil.sphere(sl, c, ParticleTypes.BUBBLE_POP, Math.max(le.getBbWidth(), le.getBbHeight()) * 0.5 + 0.3, 24);
        sl.sendParticles(Vfx.splash(), c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.1);
        FwUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.2F, 0.9F);
        FwUtil.sound(sl, c, SoundEvents.GENERIC_SPLASH, 0.6F, 1.3F);
    }

    private FwEvents() {}
}
