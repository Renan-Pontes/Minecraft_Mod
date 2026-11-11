package com.renanpontes.isekaimahou.feature.fusions_a;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.effect.FaEffects;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Eventos de jogo das fusões (conjunto A): blocos temporários, armaduras elementais, Bolha Protetora,
 * Estátua de Gelo, Sono dos Esporos, fim da Sobrecarga e amortecimento de queda dos impulsos.
 */
public final class FaEvents {
    /** Persistent data: tick do mundo até quando a queda não machuca (Propulsão a Vapor / Gêiser em si mesmo). */
    public static final String CUSHION_KEY = "isekai_mahou.fa_cushion";

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FaEvents::levelTick);
        NeoForge.EVENT_BUS.addListener(FaEvents::blockBreak);
        NeoForge.EVENT_BUS.addListener(FaEvents::incomingDamage);
        NeoForge.EVENT_BUS.addListener(FaEvents::damagePre);
        NeoForge.EVENT_BUS.addListener(FaEvents::damagePost);
        NeoForge.EVENT_BUS.addListener(FaEvents::effectApplicable);
        NeoForge.EVENT_BUS.addListener(FaEvents::effectExpired);
        NeoForge.EVENT_BUS.addListener(FaEvents::effectRemoved);
        NeoForge.EVENT_BUS.addListener(FaEvents::fall);
    }

    /** Protege da próxima queda por {@code ticks}. */
    public static void cushion(LivingEntity e, int ticks) {
        e.fallDistance = 0;
        e.getPersistentData().putLong(CUSHION_KEY, e.level().getGameTime() + ticks);
    }

    // =====================================================================================
    //  Blocos temporários
    // =====================================================================================

    private static void levelTick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel sl) FaTempBlocks.tick(sl);
    }

    private static void blockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        if (FaTempBlocks.isTracked(sl, e.getPos())) {
            // Quebrar desfaz a magia sem soltar item (nada de obsidiana/gelo grátis)
            e.setCanceled(true);
            FaTempBlocks.restoreNow(sl, e.getPos());
        }
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================

    /** Quem está atacando "com o corpo" (golpe direto ou projétil disparado por ele). */
    private static LivingEntity attackerOf(DamageSource src) {
        Entity atk = src.getEntity();
        if (!(atk instanceof LivingEntity le)) return null;
        Entity direct = src.getDirectEntity();
        if (direct == atk || (direct instanceof Projectile p && p.getOwner() == atk)) return le;
        return null;
    }

    /** Golpe corpo a corpo (direto e de perto). */
    private static LivingEntity meleeAttackerOf(DamageSource src, LivingEntity victim) {
        if (src.is(DamageTypeTags.IS_PROJECTILE) || src.is(DamageTypes.THORNS) || src.is(CoreRegistries.SPELL_DAMAGE)) return null;
        Entity atk = src.getEntity();
        if (atk instanceof LivingEntity le && src.getDirectEntity() == atk && le != victim && le.distanceToSqr(victim) < 36) return le;
        return null;
    }

    private static void incomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        DamageSource src = e.getSource();
        if (!(victim.level() instanceof ServerLevel sl)) return;

        // Congelado em estátua ou dormindo: não ataca
        LivingEntity atk = attackerOf(src);
        if (atk != null && atk != victim && (atk.hasEffect(FaRegistries.ICE_STATUE) || atk.hasEffect(FaRegistries.SPORE_SLEEP))) {
            e.setCanceled(true);
            return;
        }
        // Sobrecarga: golpes elétricos
        if (atk != null && atk.hasEffect(FaRegistries.OVERCHARGE) && src.getDirectEntity() == atk
                && !src.is(CoreRegistries.SPELL_DAMAGE)) {
            e.setAmount(e.getAmount() * 1.2F);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                    victim.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        }
        if (src.is(DamageTypeTags.IS_FIRE)) {
            // Armadura de Magma: imune a fogo e lava
            if (victim.hasEffect(FaRegistries.MAGMA_ARMOR)) {
                victim.clearFire();
                e.setCanceled(true);
                return;
            }
            // Armadura de Gelo: o fogo derrete a armadura (e metade do dano vira vapor)
            if (victim.hasEffect(FaRegistries.ICE_ARMOR)) {
                e.setAmount(e.getAmount() * 0.5F);
                removeLater(victim, FaRegistries.ICE_ARMOR);
                victim.clearFire();
                FaUtil.around(sl, victim, ParticleTypes.CLOUD, 16, 0.05);
                FaUtil.sound(sl, victim.position(), SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.2F);
            }
        }
    }

    private static void damagePre(LivingDamageEvent.Pre e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel sl) || !victim.hasEffect(FaRegistries.PROTECTIVE_BUBBLE)) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float pool = FaEffects.ProtectiveBubble.pool(victim);
        float dmg = e.getNewDamage();
        if (pool <= 0 || dmg <= 0) return;
        float absorbed = Math.min(pool, dmg);
        e.setNewDamage(dmg - absorbed);
        pool -= absorbed;
        FaEffects.ProtectiveBubble.setPool(victim, pool);
        var c = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        double r = Math.max(victim.getBbWidth(), victim.getBbHeight()) * 0.5 + 0.55;
        if (pool <= 0.01F) {
            // A bolha estoura (o escudo já zerou; o efeito sai na próxima tick)
            removeLater(victim, FaRegistries.PROTECTIVE_BUBBLE);
            FaUtil.sphere(sl, c, ParticleTypes.SPLASH, r, 40);
            FaUtil.sphere(sl, c, ParticleTypes.BUBBLE_POP, r, 25);
            FaUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.5F, 0.7F);
            FaUtil.sound(sl, c, SoundEvents.PLAYER_SPLASH, 0.8F, 1.4F);
        } else {
            FaUtil.sphere(sl, c, FaUtil.dust(0xBFF0FF, 1.0F), r, 14);
            FaUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.8F, 1.3F);
        }
    }

    /** Danos contínuos do ambiente/efeitos que não interrompem o Sono dos Esporos. */
    private static boolean wakesUp(DamageSource src) {
        return !(src.is(DamageTypes.MAGIC) || src.is(DamageTypes.WITHER) || src.is(DamageTypes.FREEZE)
                || src.is(DamageTypes.STARVE) || src.is(DamageTypes.DROWN) || src.is(DamageTypes.IN_WALL)
                || src.is(DamageTypes.CRAMMING));
    }

    /**
     * Remove um efeito na próxima tick. O dano pode chegar de dentro do tick de efeitos do vanilla (veneno, Chama
     * Sagrada...): mexer na lista de efeitos ali interrompe a iteração e os outros efeitos deixam de contar.
     */
    private static void removeLater(LivingEntity le, Holder<MobEffect> eff) {
        SpellScheduler.schedule(1, () -> {
            if (le.isAlive() && le.hasEffect(eff)) le.removeEffect(eff);
        });
    }

    private static void damagePost(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel sl) || e.getNewDamage() <= 0) return;
        DamageSource src = e.getSource();

        // Sono dos Esporos: acorda ao levar dano (de verdade: veneno e outros danos "de fundo" não acordam)
        if (victim.hasEffect(FaRegistries.SPORE_SLEEP) && wakesUp(src)) {
            removeLater(victim, FaRegistries.SPORE_SLEEP);
            FaUtil.around(sl, victim, ParticleTypes.SPORE_BLOSSOM_AIR, 10, 0.02);
        }

        LivingEntity atk = meleeAttackerOf(src, victim);
        if (atk == null) return;
        MobEffectInstance magma = victim.getEffect(FaRegistries.MAGMA_ARMOR);
        if (magma != null && !atk.hasEffect(FaRegistries.MAGMA_ARMOR)) {
            atk.igniteForSeconds(4 + magma.getAmplifier());
            atk.hurt(victim.damageSources().thorns(victim), 2.0F + magma.getAmplifier());
            sl.sendParticles(ParticleTypes.LAVA, atk.getX(), atk.getY() + atk.getBbHeight() * 0.5, atk.getZ(), 6, 0.3, 0.3, 0.3, 0);
            sl.sendParticles(ParticleTypes.FLAME, atk.getX(), atk.getY() + atk.getBbHeight() * 0.5, atk.getZ(), 10, 0.3, 0.3, 0.3, 0.03);
            FaUtil.sound(sl, atk.position(), SoundEvents.MAGMA_CUBE_SQUISH, 0.9F, 0.8F);
        }
        MobEffectInstance ice = victim.getEffect(FaRegistries.ICE_ARMOR);
        if (ice != null && !atk.hasEffect(FaRegistries.ICE_ARMOR)) {
            atk.addEffect(new MobEffectInstance(CoreRegistries.FROZEN, 40 + 20 * ice.getAmplifier(), 0), victim);
            atk.hurt(victim.damageSources().thorns(victim), 1.5F + ice.getAmplifier());
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    atk.getX(), atk.getY() + atk.getBbHeight() * 0.5, atk.getZ(), 14, 0.3, 0.3, 0.3, 0.1);
            FaUtil.sound(sl, atk.position(), SoundEvents.GLASS_BREAK, 0.7F, 1.5F);
        }
    }

    // =====================================================================================
    //  Efeitos
    // =====================================================================================

    private static void effectApplicable(MobEffectEvent.Applicable e) {
        Holder<MobEffect> eff = e.getEffectInstance().getEffect();
        LivingEntity le = e.getEntity();
        // Armadura de Gelo protege de congelar; Armadura de Magma também
        if (is(eff, CoreRegistries.FROZEN) && (le.hasEffect(FaRegistries.ICE_ARMOR) || le.hasEffect(FaRegistries.MAGMA_ARMOR))) {
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
        // Chefes não dormem nem viram estátua
        if ((is(eff, FaRegistries.SPORE_SLEEP) || is(eff, FaRegistries.ICE_STATUE)) && FaUtil.isBoss(le)) {
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static void effectExpired(MobEffectEvent.Expired e) {
        MobEffectInstance inst = e.getEffectInstance();
        if (inst == null) return;
        onEffectEnd(e.getEntity(), inst.getEffect(), true);
    }

    private static void effectRemoved(MobEffectEvent.Remove e) {
        if (e.getEntity().hasEffect(e.getEffect())) onEffectEnd(e.getEntity(), e.getEffect(), false);
    }

    private static boolean is(Holder<MobEffect> eff, Holder<MobEffect> other) {
        return eff.value() == other.value();
    }

    private static void onEffectEnd(LivingEntity le, Holder<MobEffect> eff, boolean expired) {
        if (!(le.level() instanceof ServerLevel sl)) return;
        if (is(eff, FaRegistries.PROTECTIVE_BUBBLE)) {
            FaEffects.ProtectiveBubble.setPool(le, 0);
        } else if (is(eff, FaRegistries.BEWILDERED)) {
            FaEffects.Bewildered.clear(le);
        } else if (is(eff, FaRegistries.ICE_STATUE)) {
            // A estátua se despedaça (e a casca de gelo some junto)
            // Só paredes e teto: o chão embaixo pode ser outra magia (ex.: uma Ponte de Gelo)
            BlockPos min = BlockPos.containing(le.getBoundingBox().minX - 1, le.getBoundingBox().minY, le.getBoundingBox().minZ - 1);
            BlockPos max = BlockPos.containing(le.getBoundingBox().maxX + 1, le.getBoundingBox().maxY + 1, le.getBoundingBox().maxZ + 1);
            FaTempBlocks.expireSoon(sl, min, max, FaRegistries.FROST_ICE.get(), 0);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 30, 0.4, 0.5, 0.4, 0.15);
            FaUtil.sound(sl, le.position(), SoundEvents.GLASS_BREAK, 1.0F, 0.8F);
            le.setTicksFrozen(0);
        } else if (is(eff, FaRegistries.OVERCHARGE) && expired) {
            // A energia acaba e o corpo cansa (aplicado na próxima tick: não mexer na lista durante o tick de efeitos)
            SpellScheduler.schedule(1, () -> {
                if (!le.isAlive()) return;
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                FaUtil.around(sl, le, ParticleTypes.SMOKE, 10, 0.02);
                FaUtil.sound(sl, le.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
            });
        }
    }

    // =====================================================================================
    //  Queda
    // =====================================================================================

    private static void fall(LivingFallEvent e) {
        LivingEntity le = e.getEntity();
        long until = le.getPersistentData().getLong(CUSHION_KEY);
        if (until == 0) return;
        if (le.level().getGameTime() <= until) {
            e.setDamageMultiplier(0F);
            if (le.level() instanceof ServerLevel sl && e.getDistance() > 3) {
                sl.sendParticles(ParticleTypes.CLOUD, le.getX(), le.getY() + 0.1, le.getZ(), 12, 0.4, 0.05, 0.4, 0.03);
            }
        }
        le.getPersistentData().remove(CUSHION_KEY);
    }

    private FaEvents() {}
}
