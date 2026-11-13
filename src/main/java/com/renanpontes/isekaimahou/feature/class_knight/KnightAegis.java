package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Égide Rúnica: placas de pedra rúnica orbitam o alvo e cada uma ANULA um golpe inteiro (qualquer dano que não ignore
 * invencibilidade). Quando a última placa quebra, elas explodem em volta e empurram os inimigos. Some ao acabar o
 * tempo. Uma égide por entidade (reaplicar renova e soma até o máximo).
 */
public final class KnightAegis {
    public static final int MAX_PLATES = 5;
    private static final int CAP = 128;

    private static final class Plates {
        final LivingEntity e;
        int plates;
        long until;
        @Nullable
        SpellContext ctx;
        @Nullable
        SpellStats stats;

        Plates(LivingEntity e) {
            this.e = e;
        }
    }

    private static final Map<UUID, Plates> ACTIVE = new HashMap<>();

    public static void apply(LivingEntity target, @Nullable SpellContext ctx, @Nullable SpellStats stats, int plates, int duration) {
        if (!(target.level() instanceof ServerLevel level)) return;
        Plates p = ACTIVE.get(target.getUUID());
        if (p == null) {
            if (ACTIVE.size() >= CAP) return;
            p = new Plates(target);
            ACTIVE.put(target.getUUID(), p);
        }
        p.plates = Math.min(MAX_PLATES, Math.max(p.plates, plates));
        p.until = level.getGameTime() + duration;
        p.ctx = ctx;
        p.stats = stats;
        Vec3 c = KnightFx.center(target);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.POLISHED_DEEPSLATE.defaultBlockState()),
                c.x, c.y, c.z, 10, 0.6, 0.6, 0.6, 0.1);
        KnightFxNet.on(target, KnightFxNet.AEGIS_UP, Vec3.ZERO, 1F, KnightFx.GOLD, p.plates);
        KnightFx.sound(level, c, SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 1.2F);
        KnightFx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
        if (target instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_knight.aegis", p.plates).withColor(KnightFx.GOLD), true);
        }
    }

    /** Anti-Magia: as placas se desfazem sem explodir. */
    public static void strip(LivingEntity e) {
        Plates p = ACTIVE.remove(e.getUUID());
        if (p != null && e.level() instanceof ServerLevel level) {
            Vec3 c = KnightFx.center(e);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHISELED_DEEPSLATE.defaultBlockState()),
                    c.x, c.y, c.z, 16, 0.5, 0.5, 0.5, 0.1);
            KnightFx.sound(level, c, SoundEvents.STONE_BREAK, 0.8F, 0.7F);
        }
    }

    public static int plates(LivingEntity e) {
        Plates p = ACTIVE.get(e.getUUID());
        return p == null ? 0 : p.plates;
    }

    /** Anula o golpe consumindo uma placa. */
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (ACTIVE.isEmpty()) return;
        LivingEntity e = event.getEntity();
        Plates p = ACTIVE.get(e.getUUID());
        if (p == null || !(e.level() instanceof ServerLevel level)) return;
        if (event.getAmount() <= 0F || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (level.getGameTime() > p.until) {
            ACTIVE.remove(e.getUUID());
            return;
        }
        event.setCanceled(true);
        p.plates--;
        Vec3 c = KnightFx.center(e);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHISELED_DEEPSLATE.defaultBlockState()),
                c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.15);
        KnightFxNet.on(e, KnightFxNet.AEGIS_HIT, Vec3.ZERO, 1F, KnightFx.GOLD, p.plates);
        KnightFx.sound(level, c, SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + level.random.nextFloat() * 0.2F);
        if (p.plates <= 0) shatter(level, p);
    }

    /** A última placa quebra: estilhaços empurram e ferem os inimigos em 3 blocos. */
    private static void shatter(ServerLevel level, Plates p) {
        ACTIVE.remove(p.e.getUUID());
        LivingEntity e = p.e;
        Vec3 c = KnightFx.center(e);
        KnightFxNet.on(e, KnightFxNet.AEGIS_BREAK, Vec3.ZERO, 3.0F, KnightFx.GOLD, 0);
        KnightFx.sound(level, c, SoundEvents.ANVIL_LAND, 0.8F, 0.6F);
        SpellContext ctx = p.ctx;
        for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(3.0),
                o -> o != e && o.isAlive() && (ctx == null ? !(o instanceof net.minecraft.world.entity.player.Player) : !ctx.isAlly(o)))) {
            Vec3 push = o.position().subtract(e.position()).multiply(1, 0, 1);
            if (push.lengthSqr() < 1.0E-4) push = new Vec3(1, 0, 0);
            push = push.normalize().scale(1.1);
            o.push(push.x, 0.35, push.z);
            o.hurtMarked = true;
            if (ctx != null && p.stats != null) {
                o.invulnerableTime = 0;
                ctx.damage(o, 4F * KnightPower.mult(ctx), p.stats, null);
            }
        }
    }

    /** Placas orbitando (a cada 3 ticks) e fim do tempo. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        int t = server.getTickCount();
        Iterator<Plates> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Plates p = it.next();
            LivingEntity e = p.e;
            if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level) || level.getGameTime() > p.until) {
                if (!e.isRemoved() && e.level() instanceof ServerLevel l) KnightFx.sound(l, e.position(), SoundEvents.STONE_BREAK, 0.5F, 1.4F);
                it.remove();
                continue;
            }
            if (t % 3 != 0) continue;
            double y = e.getY() + e.getBbHeight() * 0.55;
            double r = Math.max(0.8, e.getBbWidth() * 0.9);
            for (int i = 0; i < p.plates; i++) {
                float a = t * 0.18F + i * Mth.TWO_PI / p.plates;
                double x = e.getX() + Mth.cos(a) * r, z = e.getZ() + Mth.sin(a) * r;
                level.sendParticles(KnightFx.dust(0x6E7480, 1.3F), x, y, z, 1, 0, 0.15, 0, 0);
                level.sendParticles(KnightFx.dust(KnightFx.GOLD, 0.7F), x, y + 0.18, z, 1, 0, 0, 0, 0);
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private KnightAegis() {}
}
