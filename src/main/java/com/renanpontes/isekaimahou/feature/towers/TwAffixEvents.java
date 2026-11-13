package com.renanpontes.isekaimahou.feature.towers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Afixos que agem "na hora": golpe de criatura marcada (Vampíricos, Ardentes, Gélidos), golpe NELA (Espinhos) e morte
 * (Voláteis — estouro 1 s depois, sem quebrar bloco nenhum). Só olha entidades com a marca do afixo, então custa uma
 * consulta de tag por golpe.
 */
public final class TwAffixEvents {
    /** Estouro agendado de um Volátil. */
    private record Blast(ResourceKey<Level> dim, Vec3 pos, long due) {}

    private static final List<Blast> BLASTS = new ArrayList<>();
    private static final int BLAST_DELAY = 20;
    private static final int MAX_BLASTS = 32;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TwAffixEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TwAffixEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(TwAffixEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> BLASTS.clear());
    }

    private static boolean has(Entity e, TowerAffix affix) {
        return e.getTags().contains(affix.tag());
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel sl) || event.getNewDamage() <= 0F) return;
        DamageSource src = event.getSource();
        Entity attacker = src.getEntity();
        // Criatura com afixo acertou alguém
        if (attacker instanceof Mob mob && !mob.getTags().isEmpty() && attacker != victim) {
            if (has(mob, TowerAffix.VAMPIRIC) && mob.isAlive()) {
                mob.heal(event.getNewDamage() * 0.5F);
                sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(),
                        3, 0.2, 0.2, 0.2, 0.0);
            }
            if (has(mob, TowerAffix.BURNING)) victim.igniteForSeconds(3F);
            if (has(mob, TowerAffix.CHILLING)) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
                victim.setTicksFrozen(Math.min(victim.getTicksRequiredToFreeze(), victim.getTicksFrozen() + 60));
            }
        }
        // Alguém acertou de perto uma criatura com Espinhos (projéteis e a própria devolução não contam)
        if (victim instanceof Mob thorny && has(thorny, TowerAffix.THORNS) && attacker instanceof LivingEntity hitter
                && attacker == src.getDirectEntity() && hitter != victim
                && !src.is(DamageTypeTags.IS_PROJECTILE) && !src.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)) {
            float back = Math.min(4F, event.getNewDamage() * 0.15F);
            if (back >= 0.5F) hitter.hurt(sl.damageSources().thorns(thorny), back);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (event.isCanceled() || !(e.level() instanceof ServerLevel sl) || !has(e, TowerAffix.VOLATILE)) return;
        if (BLASTS.size() >= MAX_BLASTS) return;
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
        BLASTS.add(new Blast(sl.dimension(), at, sl.getGameTime() + BLAST_DELAY));
        // Aviso: chiado e faíscas no lugar onde vai estourar
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 1.3F);
        sl.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.02);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (BLASTS.isEmpty()) return;
        for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext();) {
            Blast b = it.next();
            ServerLevel sl = event.getServer().getLevel(b.dim());
            if (sl == null) {
                it.remove();
                continue;
            }
            if (sl.getGameTime() < b.due()) {
                if (sl.getGameTime() % 4 == 0) sl.sendParticles(ParticleTypes.LAVA, b.pos().x, b.pos().y, b.pos().z, 2, 0.2, 0.2, 0.2, 0.0);
                continue;
            }
            it.remove();
            // ExplosionInteraction.NONE: fere, mas não quebra bloco nenhum
            sl.explode(null, b.pos().x, b.pos().y, b.pos().z, 2.4F, false, Level.ExplosionInteraction.NONE);
        }
    }

    private TwAffixEvents() {}
}
