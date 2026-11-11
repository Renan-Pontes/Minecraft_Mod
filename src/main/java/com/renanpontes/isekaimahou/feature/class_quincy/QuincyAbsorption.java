package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Absorção (o sistema da classe): campos ativos da forma "Absorção" e a regra comum de "engolir" um projétil.
 * <ul>
 *   <li>Um campo por conjurador (conjurar de novo substitui). A cada 2 ticks, projéteis INIMIGOS no raio (flechas,
 *   bolas de fogo, magias dos outros) somem e viram carga; a cada segundo, se houver inimigo dentro, puxa Reishi do
 *   ambiente (+1 carga).</li>
 *   <li>Ao acabar, devolve tudo como uma rajada: 1 flecha espiritual por carga (mín. 1, máx. 8), cada uma aplicando
 *   os efeitos da magia, mirando os inimigos mais próximos.</li>
 * </ul>
 * Estado só em memória (some ao sair/morrer/parar o servidor).
 */
public final class QuincyAbsorption {
    public static final int MAX_RELEASE = 8;

    private static final class Field {
        final SpellContext ctx;
        final ServerLevel level;
        final long end;
        final float radius;
        int charges;
        int age;

        Field(SpellContext ctx, ServerLevel level, long end, float radius) {
            this.ctx = ctx;
            this.level = level;
            this.end = end;
            this.radius = radius;
        }
    }

    private static final Map<UUID, Field> FIELDS = new HashMap<>();

    public static void startField(SpellContext ctx, int duration, float radius) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        Field old = FIELDS.remove(caster.getUUID());
        if (old != null) release(old, caster);
        FIELDS.put(caster.getUUID(), new Field(ctx, ctx.level(), ctx.level().getGameTime() + duration, radius));
        QuincyFx.sound(ctx.level(), caster.position(), SoundEvents.BEACON_ACTIVATE, 0.9F, 1.5F);
    }

    public static boolean hasField(Entity e) {
        return FIELDS.containsKey(e.getUUID());
    }

    static void tick(MinecraftServer server) {
        if (FIELDS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Field>> it = FIELDS.entrySet().iterator();
        List<Runnable> releases = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Field> e = it.next();
            Field f = e.getValue();
            Entity ent = f.level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity caster) || !caster.isAlive()) {
                it.remove();
                continue;
            }
            f.age++;
            Vec3 c = caster.position();
            if (f.age % 2 == 0) {
                for (Projectile p : enemyProjectiles(f.level, caster, f.radius)) {
                    if (f.charges >= 12) break;
                    swallow(f.level, caster, p);
                    f.charges++;
                }
            }
            if (f.age % 20 == 0 && !f.ctx.enemiesAround(c, f.radius).isEmpty()) {
                // Reishi do ambiente: puxa um pouco da energia de quem está dentro
                LivingEntity near = f.ctx.enemiesAround(c, f.radius).get(0);
                QuincyFx.stream(f.level, near.position().add(0, near.getBbHeight() / 2, 0), c.add(0, 1, 0), 6);
                f.charges = Math.min(12, f.charges + 1);
            }
            if (f.age % 4 == 0) {
                QuincyFx.ring(f.level, c.add(0, 0.15, 0), f.radius, 20, f.age * 0.08F, QuincyFx.REISHI_SMALL);
                QuincyFx.ring(f.level, c.add(0, 1.2, 0), 0.8, 8, -f.age * 0.15F, QuincyFx.WHITE);
            }
            if (f.level.getGameTime() >= f.end) {
                it.remove();
                releases.add(() -> release(f, caster));
            }
        }
        releases.forEach(Runnable::run);
    }

    /** Projéteis no raio que não são do conjurador nem de aliados dele (e ainda voando). */
    static List<Projectile> enemyProjectiles(ServerLevel level, LivingEntity holder, double radius) {
        List<Projectile> out = new ArrayList<>();
        AABB box = holder.getBoundingBox().inflate(radius);
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive() && !p.isRemoved())) {
            Entity owner = p.getOwner();
            if (owner == holder) continue;
            if (owner != null && (owner.isAlliedTo(holder) || holder.isAlliedTo(owner))) continue;
            if (owner instanceof LivingEntity lo && Boolean.TRUE.equals(com.renanpontes.isekaimahou.core.api.Party.allyOverride(holder, lo))) continue;
            if (p instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow && arrow.getDeltaMovement().lengthSqr() < 0.01) continue; // presa no chão
            out.add(p);
        }
        return out;
    }

    /** Engole um projétil: some num fluxo de luz até {@code holder}. Quincy jogador ganha Foco, Reishi e o feito. */
    public static void swallow(ServerLevel level, LivingEntity holder, Projectile p) {
        Vec3 from = p.position();
        p.discard();
        QuincyFx.stream(level, from, holder.position().add(0, 1.1, 0), 5);
        level.sendParticles(QuincyFx.WHITE, from.x, from.y, from.z, 6, 0.15, 0.15, 0.15, 0.02);
        QuincyFx.sound(level, from, SoundEvents.AMETHYST_BLOCK_HIT, 0.8F, 1.9F);
        if (holder instanceof ServerPlayer sp) onAbsorbed(sp);
    }

    /** Recompensa de uma absorção: 3% do Foco máximo, +1 Reishi guardado e o contador do rank. */
    public static void onAbsorbed(ServerPlayer sp) {
        Quincy.gainFocus(sp, 0.03F);
        Quincy.addReishi(sp, 1);
        if (Quincy.isQuincy(sp)) MagicApi.addCounter(sp, Quincy.ABSORBED, 1);
        sp.displayClientMessage(Quincy.msg("absorbed").append(" · ").append(Quincy.reishiLine(MagicApi.get(sp))).withColor(Quincy.RGB), true);
    }

    /** Devolve a carga como flechas espirituais (com os efeitos da magia) nos inimigos mais próximos. */
    private static void release(Field f, LivingEntity caster) {
        int n = Math.max(1, Math.min(MAX_RELEASE, f.charges));
        ServerLevel level = f.level;
        List<LivingEntity> targets = new ArrayList<>(f.ctx.enemiesAround(caster.position(), 16));
        targets.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(caster)));
        QuincyFx.burst(level, caster.position().add(0, 1.2, 0), 20, 0.6);
        QuincyFx.sound(level, caster.position(), SoundEvents.BEACON_DEACTIVATE, 0.9F, 1.7F);
        for (int i = 0; i < n; i++) {
            final int k = i;
            SpellScheduler.schedule(1 + i * 2, () -> {
                if (!caster.isAlive()) return;
                LivingEntity t = targets.isEmpty() ? null : targets.get(k % targets.size());
                fireAt(f.ctx, caster, t, k, n);
            });
        }
    }

    /** Uma flecha da devolução: em leque em volta do conjurador, rumo ao alvo (ou para onde ele olha). */
    static void fireAt(SpellContext ctx, LivingEntity caster, @Nullable LivingEntity target, int index, int total) {
        double a = Math.toRadians(-60 + 120.0 * (total == 1 ? 0.5 : index / (double) (total - 1)));
        Vec3 look = caster.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 from = caster.getEyePosition().add(side.scale(Math.sin(a) * 1.2)).add(0, 0.4 + Math.cos(a) * 0.4, 0);
        Vec3 dir = target != null ? target.position().add(0, target.getBbHeight() / 2, 0).subtract(from).normalize() : look;
        SpiritArrowEntity arrow = SpiritArrowEntity.spell(ctx, caster, from).homing(target != null);
        arrow.shoot(dir.x, dir.y, dir.z, 2.8F, 0.5F);
        ctx.level().addFreshEntity(arrow);
        ctx.level().sendParticles(QuincyFx.WHITE, from.x, from.y, from.z, 3, 0.05, 0.05, 0.05, 0.0);
        QuincyFx.sound(ctx.level(), from, SoundEvents.ARROW_SHOOT, 0.5F, 1.8F);
    }

    static void forget(UUID id) {
        FIELDS.remove(id);
    }

    static void clear() {
        FIELDS.clear();
    }

    private QuincyAbsorption() {}
}
