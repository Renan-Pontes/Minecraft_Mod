package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Marca Rúnica: cada aplicação (o efeito {@code knight_rune_mark} ou um golpe de espada do dono da marca) soma um
 * acúmulo; no 3º a runa se ROMPE — dano forte no alvo, metade em volta (2,5 blocos) e Exposto. A marca guarda o
 * contexto da magia que a criou (para o dano da ruptura sair com o poder certo) e some em 10 s (Estender) ou ao romper.
 */
public final class KnightMarks {
    public static final int MAX_STACKS = 3;
    private static final int CAP = 256;
    /** Dano base da ruptura (alvo) — a área leva a metade. */
    public static final float RUPTURE_DAMAGE = 8F;

    private static final class Mark {
        final LivingEntity target;
        SpellContext ctx;
        SpellStats stats;
        UUID owner;
        int stacks;
        long until;
        int duration;

        Mark(LivingEntity target) {
            this.target = target;
        }
    }

    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    /** Soma {@code add} acúmulos (a magia que marcou define o poder da ruptura). */
    public static void add(LivingEntity target, SpellContext ctx, SpellStats stats, int add, int duration) {
        if (!(target.level() instanceof ServerLevel level)) return;
        Mark m = MARKS.get(target.getUUID());
        if (m == null) {
            if (MARKS.size() >= CAP) return;
            m = new Mark(target);
            MARKS.put(target.getUUID(), m);
        }
        m.ctx = ctx;
        m.stats = stats;
        m.owner = ctx.casterId();
        m.duration = duration;
        bump(level, m, add);
    }

    /** Golpe de espada do dono da marca: +1 acúmulo (se a lâmina não soltou runa neste golpe). */
    public static void onMelee(LivingEntity attacker, LivingEntity target) {
        Mark m = MARKS.get(target.getUUID());
        if (m == null || m.owner == null || !m.owner.equals(attacker.getUUID())) return;
        if (!(target.level() instanceof ServerLevel level) || RuneEngraving.procThisTick(attacker)) return;
        bump(level, m, 1);
    }

    private static void bump(ServerLevel level, Mark m, int add) {
        m.stacks = Math.min(MAX_STACKS, m.stacks + Math.max(1, add));
        m.until = level.getGameTime() + m.duration;
        LivingEntity t = m.target;
        Vec3 top = t.position().add(0, t.getBbHeight() + 0.45, 0);
        level.sendParticles(KnightFx.dust(KnightFx.RUNE_BLUE, 1.0F), top.x, top.y, top.z, 6 * m.stacks, 0.25, 0.08, 0.25, 0);
        KnightFx.sound(level, top, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.9F + 0.25F * m.stacks);
        if (m.stacks >= MAX_STACKS) {
            rupture(level, m);
            return;
        }
        t.addEffect(new MobEffectInstance(KnightRegistries.RUNE_MARK, m.duration, m.stacks - 1, false, true, true),
                m.ctx != null ? m.ctx.caster() : null);
    }

    /** A runa se rompe: dano no alvo e em volta, Exposto e uma explosão de luz azul e dourada (sem quebrar blocos). */
    private static void rupture(ServerLevel level, Mark m) {
        MARKS.remove(m.target.getUUID());
        LivingEntity t = m.target;
        t.removeEffect(KnightRegistries.RUNE_MARK);
        SpellContext ctx = m.ctx;
        Vec3 c = KnightFx.center(t);
        KnightFx.runeCircle(level, t.position(), 1.8, KnightFx.RUNE_BLUE, 32, level.getGameTime() * 0.3F);
        level.sendParticles(KnightFx.dust(KnightFx.GOLD, 1.6F), c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 24, 0.5, 0.5, 0.5, 0.4);
        KnightFx.sound(level, c, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.5F, 1.8F);
        KnightFx.sound(level, c, SoundEvents.GLASS_BREAK, 1.0F, 0.7F);
        if (ctx == null || !t.isAlive()) return;
        float mult = KnightPower.mult(ctx);
        t.invulnerableTime = 0;
        ctx.damage(t, RUPTURE_DAMAGE * mult, m.stats, null);
        Statuses.apply(t, Statuses.Kind.EXPOSED, 1, 100, ctx.caster(), ctx.powerMult());
        for (LivingEntity e : ctx.enemiesAround(c, 2.5)) {
            if (e == t) continue;
            e.invulnerableTime = 0;
            ctx.damage(e, RUPTURE_DAMAGE * 0.5F * mult, m.stats, null);
        }
    }

    public static int stacks(LivingEntity e) {
        Mark m = MARKS.get(e.getUUID());
        return m == null ? 0 : m.stacks;
    }

    /** A cada segundo: tira marcas vencidas ou de alvos que sumiram. */
    public static void tick(MinecraftServer server) {
        if (MARKS.isEmpty() || server.getTickCount() % 20 != 0) return;
        Iterator<Mark> it = MARKS.values().iterator();
        while (it.hasNext()) {
            Mark m = it.next();
            LivingEntity t = m.target;
            if (t.isRemoved() || !t.isAlive() || t.level().getGameTime() > m.until) it.remove();
        }
    }

    public static void clear() {
        MARKS.clear();
    }

    private KnightMarks() {}
}
