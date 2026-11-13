package com.renanpontes.isekaimahou.feature.glyphs_elements_b;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Auxiliares do módulo: recargas internas, dano extra sem laço de eventos, alvos próximos e som. */
public final class GebUtil {
    /** > 0 enquanto o módulo causa dano extra (os ouvintes dos aumentos ignoram esse dano: sem laço infinito). */
    static int guard;
    /** Aumentos "neutros" para o dano extra: poder 1, sem crítico nem Vampírico. */
    private static final SpellStats PLAIN = new SpellStats();
    /** Recargas internas (chave -> tick de jogo em que libera). Limpas a cada 30 s. */
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    /**
     * Libera a ação se a chave não estiver em recarga (e já marca a recarga de {@code ticks}).
     * @return true = pode agir agora
     */
    public static boolean ready(String key, long now, int ticks) {
        Long until = COOLDOWNS.get(key);
        if (until != null && now < until) return false;
        COOLDOWNS.put(key, now + ticks);
        return true;
    }

    /**
     * Dano mágico extra de {@code amount} (valor FINAL, já com o poder da magia), do elemento informado. Passa pelas
     * reações e condições do elemento ({@link SpellContext#damage}), mas os aumentos deste módulo não reagem a ele.
     */
    public static float hit(SpellContext ctx, LivingEntity target, float amount, Element el) {
        if (ctx == null || target == null || !target.isAlive() || !(amount > 0F)) return 0F;
        float scale = ctx.powerMult() * ctx.casterSpellPower() * SpellCost.damageScale();
        float base = amount / Math.max(0.01F, scale);
        guard++;
        try {
            return ctx.damage(target, base, PLAIN, null, el);
        } finally {
            guard--;
        }
    }

    /** Inimigos vivos do conjurador perto de {@code center}, do mais próximo ao mais longe, sem {@code except}. */
    public static List<LivingEntity> enemiesNear(SpellContext ctx, Vec3 center, double radius, @Nullable Entity except, int max) {
        List<LivingEntity> list = new ArrayList<>(ctx.enemiesAround(center, radius));
        list.removeIf(e -> e == except || e.isSpectator());
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Limpeza periódica (recargas vencidas). */
    static void sweep(long now) {
        COOLDOWNS.values().removeIf(t -> t <= now);
    }

    static void clear() {
        COOLDOWNS.clear();
        guard = 0;
    }

    private GebUtil() {}
}
