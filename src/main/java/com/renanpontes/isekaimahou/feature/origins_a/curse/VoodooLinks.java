package com.renanpontes.isekaimahou.feature.origins_a.curse;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Boneco de Vodu: liga o conjurador a um alvo. Os próximos golpes que o conjurador RECEBERIA são
 * transferidos para o alvo (como dano de magia do conjurador). O vínculo acaba com as cargas, o tempo,
 * a morte do alvo ou se ele estiver longe demais (48 blocos / outra dimensão: aí o golpe não é desviado).
 */
public final class VoodooLinks {
    private static final DustParticleOptions THREAD = new DustParticleOptions(new Vector3f(0.62F, 0.08F, 0.16F), 0.8F);
    private static final double MAX_RANGE = 48.0;

    private record Link(UUID target, ResourceKey<Level> dim, int charges, long expires, float mult) {}

    private static final Map<UUID, Link> LINKS = new HashMap<>();
    /** Evita recursão (dois jogadores com vodu um no outro). */
    private static boolean transferring;

    public static void bind(ServerLevel level, LivingEntity caster, LivingEntity target, int charges, int duration, float mult) {
        Link old = LINKS.put(caster.getUUID(), new Link(target.getUUID(), level.dimension(), charges, level.getGameTime() + duration, mult));
        // Trocou de boneco: o antigo perde a marca (o vínculo é um só por conjurador)
        if (old != null && !old.target().equals(target.getUUID()) && level.getServer().getLevel(old.dim()) instanceof ServerLevel ol
                && ol.getEntity(old.target()) instanceof LivingEntity prev) {
            prev.removeEffect(OaRegistries.VOODOO_BOUND);
        }
        line(level, caster.position().add(0, caster.getBbHeight() * 0.6, 0), target.position().add(0, target.getBbHeight() * 0.6, 0), 0.25);
        level.sendParticles(Vfx.witch(), target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 16, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.8F, 1.3F);
        if (caster instanceof Player p) OaUtil.actionbar(p, "voodoo_bound", target.getDisplayName(), charges);
    }

    /** Dano chegando em alguém: se for um conjurador ligado, o golpe vai para o boneco. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (transferring || !(victim.level() instanceof ServerLevel level)) return;
        Link link = LINKS.get(victim.getUUID());
        if (link == null) return;
        DamageSource src = event.getSource();
        if (event.getAmount() <= 0 || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // O evento chega antes da checagem de invencibilidade: golpe que o vanilla ignoraria (i-frames) não gasta carga
        if (victim.invulnerableTime > 10 && !src.is(DamageTypeTags.BYPASSES_COOLDOWN)) return;
        if (level.getGameTime() > link.expires()) {
            LINKS.remove(victim.getUUID());
            return;
        }
        if (!level.dimension().equals(link.dim()) || !(level.getEntity(link.target()) instanceof LivingEntity target)
                || !target.isAlive() || target.distanceToSqr(victim) > MAX_RANGE * MAX_RANGE) {
            return;
        }
        float amount = event.getAmount() * link.mult();
        event.setCanceled(true);
        transferring = true;
        try {
            DamageSource spell = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE), victim, victim);
            target.hurt(spell, amount);
        } finally {
            transferring = false;
        }
        line(level, victim.position().add(0, victim.getBbHeight() * 0.6, 0), target.position().add(0, target.getBbHeight() * 0.6, 0), 0.3);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                6, 0.3, 0.2, 0.3, 0.1);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.6F, 1.5F);

        int left = link.charges() - 1;
        if (left <= 0) {
            LINKS.remove(victim.getUUID());
            target.removeEffect(OaRegistries.VOODOO_BOUND);
            if (victim instanceof Player p) OaUtil.actionbar(p, "voodoo_spent");
        } else {
            LINKS.put(victim.getUUID(), new Link(link.target(), link.dim(), left, link.expires(), link.mult()));
            if (victim instanceof Player p) OaUtil.actionbar(p, "voodoo_left", left);
        }
    }

    /** A cada 10 ticks: limpa vínculos vencidos e desenha o "fio" entre o conjurador e o boneco. */
    public static void tick(MinecraftServer server) {
        if (LINKS.isEmpty() || server.getTickCount() % 10 != 0) return;
        Iterator<Map.Entry<UUID, Link>> it = LINKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Link> en = it.next();
            Link l = en.getValue();
            ServerLevel level = server.getLevel(l.dim());
            if (level == null || level.getGameTime() > l.expires()) {
                it.remove();
                continue;
            }
            LivingEntity target = level.getEntity(l.target()) instanceof LivingEntity le ? le : null;
            if (target != null && !target.isAlive()) {
                it.remove();
                continue;
            }
            LivingEntity caster = OaUtil.owner(level, en.getKey());
            if (target != null && caster != null && caster.level() == level && caster.distanceToSqr(target) < 32 * 32) {
                line(level, caster.position().add(0, caster.getBbHeight() * 0.6, 0),
                        target.position().add(0, target.getBbHeight() * 0.6, 0), 1.2);
            }
        }
    }

    private static void line(ServerLevel level, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            level.sendParticles(THREAD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    public static void clear() {
        LINKS.clear();
    }

    private VoodooLinks() {}
}
