package com.renanpontes.isekaimahou.feature.legends_d;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Utilitários de servidor do legends_d: partículas para longe, sons, chão, dano que ignora armadura. */
public final class LdUtil {
    /** Até onde os jogadores recebem as partículas destas magias (elas acontecem a 40+ blocos). */
    private static final double FAR = 160.0;
    private static final double NEAR = 32.0;

    public static Vector3f vec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    public static DustColorTransitionOptions fade(int from, int to, float size) {
        return new DustColorTransitionOptions(vec(from), vec(to), Mth.clamp(size, 0.05F, 4.0F));
    }

    /** Partículas para todos até {@link #FAR} blocos (longe: como "longa distância", para não sumirem). */
    public static void send(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                            double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : level.players()) {
            double d2 = sp.distanceToSqr(x, y, z);
            if (d2 > FAR * FAR) continue;
            level.sendParticles(sp, p, d2 > NEAR * NEAR, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static void send(ServerLevel level, ParticleOptions p, Vec3 pos, int count, double spread, double speed) {
        send(level, p, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    /** Uma partícula com velocidade (count 0: dx/dy/dz são a direção). */
    public static void moving(ServerLevel level, ParticleOptions p, Vec3 pos, Vec3 velocity, double speed) {
        send(level, p, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, speed);
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch * (0.95F + level.random.nextFloat() * 0.1F));
    }

    public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch * (0.95F + level.random.nextFloat() * 0.1F));
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static boolean isBoss(Entity e) {
        return BossKit.isBossLike(e);
    }

    /** Aviso na barra de ação (só jogadores). */
    public static void actionbar(Entity to, Component text) {
        if (to instanceof Player p) p.displayClientMessage(text, true);
    }

    /** Primeiro chão sólido de {@code p} para baixo (até {@code depth} blocos); se estiver dentro de um bloco, sobe. */
    public static Vec3 groundBelow(ServerLevel level, Vec3 p, int depth) {
        BlockPos pos = BlockPos.containing(p);
        for (int i = 0; i < 8 && level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
            pos = pos.above();
        }
        for (int i = 0; i < depth; i++) {
            BlockPos below = pos.below();
            if (!level.isLoaded(below) || below.getY() < level.getMinBuildHeight()) break;
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return new Vec3(p.x, pos.getY(), p.z);
            pos = below;
        }
        return new Vec3(p.x, Math.max(level.getMinBuildHeight(), pos.getY()), p.z);
    }

    // =====================================================================================
    //  Dano que ignora armadura (mantém o dano mágico do núcleo: reações, Ao Abater, XP por magia)
    // =====================================================================================

    /**
     * Causa {@code base} × poder (Amplificar, Tudo ou Nada, atributo de poder mágico) de dano que IGNORA a armadura do
     * alvo, pelo {@code ctx.damage} (crítico, reações e os eventos de dano do núcleo continuam valendo).
     */
    public static float pierceDamage(SpellContext ctx, LivingEntity target, float base, SpellStats stats) {
        float mult = powerOf(ctx, stats);
        if (mult <= 0.001F || base <= 0) return 0F;
        float raw = armorPierce(target, base * mult);
        return ctx.damage(target, raw / mult, stats, null);
    }

    /** Multiplicador total que {@code ctx.damage} aplicaria (aumentos do glifo × poder da conjuração × atributo). */
    public static float powerOf(SpellContext ctx, SpellStats stats) {
        return stats.power() * ctx.powerMult() * ctx.casterSpellPower();
    }

    /** Quanto dano "bruto" é preciso para sobrar {@code wanted} depois da armadura (fórmula do vanilla, sem encantos). */
    public static float armorPierce(LivingEntity target, float wanted) {
        float armor = target.getArmorValue();
        if (armor <= 0 || wanted <= 0) return wanted;
        float tough = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float lo = wanted, hi = wanted * 6.0F;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) * 0.5F;
            if (afterArmor(mid, armor, tough) < wanted) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    private static float afterArmor(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float r = Mth.clamp(armor - damage / f, armor * 0.2F, 20.0F) / 25.0F;
        return damage * (1.0F - r);
    }

    public static float easeOut(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return 1F - (1F - t) * (1F - t) * (1F - t);
    }

    private LdUtil() {}
}
