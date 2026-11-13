package com.renanpontes.isekaimahou.feature.arcane;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Visual contínuo dos estados aplicados pelos glifos arcanos (os efeitos do núcleo não têm partículas próprias):
 * estrelas girando (Atordoado), fumaça cinza (Silenciado) e mira vermelha (Exposto/Marcado).
 * Só acompanha as entidades que o módulo afetou, então é barato.
 */
public final class StatusAuras {
    public enum Kind { STUN, SILENCE, MARK }

    private record Entry(UUID id, ResourceKey<Level> dim, Kind kind, long until) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final ParticleOptions STAR = ArcaneUtil.dust(0xFFF27A, 0.9F);
    private static final ParticleOptions MUTE = ArcaneUtil.dust(0x9A9AB0, 1.0F);
    private static final ParticleOptions MARK = ArcaneUtil.dust(0xFF4040, 0.8F);

    public static void track(LivingEntity e, Kind kind, int ticks) {
        if (!(e.level() instanceof ServerLevel level)) return;
        ENTRIES.removeIf(x -> x.id.equals(e.getUUID()) && x.kind == kind);
        ENTRIES.add(new Entry(e.getUUID(), level.dimension(), kind, level.getGameTime() + ticks));
    }

    private static Holder<MobEffect> effectOf(Kind k) {
        return switch (k) {
            case STUN -> CoreRegistries.STUNNED;
            case SILENCE -> CoreRegistries.SILENCED;
            case MARK -> CoreRegistries.EXPOSED;
        };
    }

    /** A entidade está marcada/atordoada/silenciada por um glifo arcano (e ainda com o efeito)? */
    public static boolean has(LivingEntity e, Kind kind) {
        if (ENTRIES.isEmpty() || !e.hasEffect(effectOf(kind))) return false;
        long now = e.level().getGameTime();
        for (Entry en : ENTRIES) {
            if (en.kind == kind && en.until >= now && en.id.equals(e.getUUID())) return true;
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (ENTRIES.isEmpty() || tick % 3 != 0) return;
        Iterator<Entry> it = ENTRIES.iterator();
        while (it.hasNext()) {
            Entry en = it.next();
            ServerLevel level = server.getLevel(en.dim);
            Entity raw = level == null ? null : level.getEntity(en.id);
            if (!(raw instanceof LivingEntity e) || !e.isAlive() || level.getGameTime() > en.until || !e.hasEffect(effectOf(en.kind))) {
                it.remove();
                continue;
            }
            double top = e.getY() + e.getBbHeight() + 0.25;
            double a = level.getGameTime() * 0.3;
            switch (en.kind) {
                case STUN -> {
                    for (int i = 0; i < 3; i++) {
                        double ang = a + i * Math.PI * 2 / 3;
                        double r = Math.max(0.35, e.getBbWidth() * 0.5);
                        level.sendParticles(STAR, e.getX() + Math.cos(ang) * r, top, e.getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
                    }
                    if (tick % 12 == 0) {
                        level.sendParticles(Vfx.crit(), e.getX(), top, e.getZ(), 2, 0.2, 0.05, 0.2, 0.05);
                    }
                }
                case SILENCE -> {
                    level.sendParticles(MUTE, e.getX(), top, e.getZ(), 2, 0.15, 0.05, 0.15, 0);
                    if (tick % 9 == 0) {
                        level.sendParticles(Vfx.smoke(), e.getX(), e.getEyeY(), e.getZ(), 2, 0.1, 0.05, 0.1, 0.01);
                    }
                }
                case MARK -> {
                    double cy = e.getY() + e.getBbHeight() * 0.55;
                    double r = e.getBbWidth() * 0.5 + 0.35;
                    for (int i = 0; i < 4; i++) {
                        double ang = -a + i * Math.PI / 2;
                        level.sendParticles(MARK, e.getX() + Math.cos(ang) * r, cy, e.getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }

    private StatusAuras() {}
}
