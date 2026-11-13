package com.renanpontes.isekaimahou.feature.fusions_b;

import com.renanpontes.isekaimahou.feature.fusions_b.effect.Prism;
import com.renanpontes.isekaimahou.feature.fusions_b.glyph.AstralGlyphs;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Eventos de jogo das fusões B: sequências, Projeção Astral, Armadura de Cristal e Prisma Refletor. */
public final class FbEvents {

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> FbTasks.tick());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            FbTasks.clear();
            AstralGlyphs.Constellation.clearOpen();
        });
        NeoForge.EVENT_BUS.addListener(FbEvents::playerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) AstralProjection.returnToBody(sp);
        });
        // Volta ao corpo antes de o jogador ser salvo (o evento de saída roda antes do save)
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) AstralProjection.returnToBody(sp);
        });
        NeoForge.EVENT_BUS.addListener(FbEvents::crystalArmor);
        NeoForge.EVENT_BUS.addListener(FbEvents::prismImpact);
    }

    private static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) AstralProjection.tick(sp);
    }

    /** Armadura de Cristal: devolve parte do dano corpo a corpo para quem bateu. */
    private static void crystalArmor(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel sl)) return;
        MobEffectInstance armor = victim.getEffect(FbRegistries.CRYSTAL_ARMOR);
        if (armor == null || e.getNewDamage() <= 0) return;
        DamageSource src = e.getSource();
        if (src.is(DamageTypes.THORNS) || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.IS_PROJECTILE)) return;
        if (!(src.getEntity() instanceof LivingEntity attacker) || attacker == victim || src.getDirectEntity() != attacker) return;
        if (!attacker.isAlive() || attacker.distanceToSqr(victim) > 8 * 8) return;
        float back = Math.max(1.0F, e.getNewDamage() * (0.3F + 0.1F * armor.getAmplifier()));
        attacker.hurt(victim.damageSources().thorns(victim), back);
        Vec3 at = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
        sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.AMETHYST_SHARD)), at.x, at.y, at.z,
                10, 0.25, 0.25, 0.25, 0.12);
        sl.sendParticles(Vfx.waxOn(), at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.05);
        FbFx.sound(sl, victim.position(), SoundEvents.AMETHYST_BLOCK_HIT, 1.2F, 1.2F);
    }

    /** Prisma Refletor: projétil que ia acertar o protegido é devolvido (complementa o tick do efeito). */
    private static void prismImpact(ProjectileImpactEvent e) {
        Projectile p = e.getProjectile();
        if (!(p.level() instanceof ServerLevel sl)) return;
        if (!(e.getRayTraceResult() instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity le)) return;
        if (!le.hasEffect(FbRegistries.PRISM_REFLECTION) || Prism.friendly(p.getOwner(), le)) return;
        e.setCanceled(true);
        // Acabou de ser devolvido e ainda está saindo de dentro do protegido: só deixa passar
        if (!Prism.recentlyReflected(p)) Prism.reflect(sl, p, le);
    }

    private FbEvents() {}
}
