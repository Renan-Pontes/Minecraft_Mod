package com.renanpontes.isekaimahou.feature.legends_k.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Auras e campos que pulsam (1 pulso a cada 10 ou 20 ticks, no máximo 12 criaturas por pulso):
 * <ul>
 *   <li><b>Pressão Espiritual</b> (Shinigami): em volta de você, criaturas mais fracas que a sua pressão ficam
 *       pesadas; as MUITO mais fracas ajoelham (Enraizadas e lentíssimas). Chefes só sentem um leve peso. Jogadores só
 *       no PvP, e só sentem o peso (Lentidão I).</li>
 *   <li><b>Campo de Flores</b> (Frieren, Druida): um campo florido que cura os aliados devagar e "acalma" os monstros
 *       comuns (perdem o alvo e andam devagar, como quem para para cheirar as flores).</li>
 * </ul>
 * O contexto da magia fica guardado só para as regras de aliado/inimigo (Modo Paz, grupos, PvP).
 */
public final class Auras {
    public static final int MAX_TARGETS = 12;

    private record Pressure(SpellContext ctx, ResourceKey<Level> level, long until, float radius, float pressure) {}

    private static final Map<UUID, Pressure> PRESSURE = new HashMap<>();

    private record Field(SpellContext ctx, ResourceKey<Level> level, Vec3 center, long until, float radius, float heal) {}

    private static final List<Field> FIELDS = new ArrayList<>();
    /** Campos ao mesmo tempo (o mais velho sai). */
    public static final int MAX_FIELDS = 24;

    public static void pressure(SpellContext ctx, LivingEntity owner, int ticks, float radius, float pressure) {
        PRESSURE.put(owner.getUUID(), new Pressure(ctx, owner.level().dimension(), owner.level().getGameTime() + ticks, radius, pressure));
    }

    /** Aplica a pressão em UMA criatura (glifo acertando alguém diretamente) — mesma regra do pulso. */
    public static void press(SpellContext ctx, LivingEntity source, LivingEntity t, float pressure, int ticks) {
        float ratio = pressure / Math.max(1F, t.getMaxHealth());
        if (t instanceof Player) {
            // PvP: só o peso
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, false, true));
            return;
        }
        if (LkUtil.boss(t)) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, false, true));
            return;
        }
        if (ratio >= 2F) {
            // ajoelha: preso ao chão e sem forças
            Statuses.applyUpTo(t, Statuses.Kind.ROOTED, 1, ticks, source, 1F, 1);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 4, false, true));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, true));
            if (t instanceof Mob m && m.getTarget() != null && m.getTarget() == source && t.getRandom().nextInt(3) == 0) {
                m.getNavigation().stop();
            }
        } else if (ratio >= 1F) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, true));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, true));
        } else if (ratio >= 0.5F) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, false, true));
        }
    }

    public static void field(SpellContext ctx, Vec3 center, int ticks, float radius, float heal) {
        if (FIELDS.size() >= MAX_FIELDS) FIELDS.remove(0);
        FIELDS.add(new Field(ctx, ctx.level().dimension(), center, ctx.level().getGameTime() + ticks, radius, heal));
    }

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (!PRESSURE.isEmpty() && tick % 10 == 0) tickPressure(server);
        if (!FIELDS.isEmpty() && tick % 20 == 0) tickFields(server);
    }

    private static void tickPressure(MinecraftServer server) {
        for (Map.Entry<UUID, Pressure> en : new ArrayList<>(PRESSURE.entrySet())) {
            Pressure p = en.getValue();
            ServerLevel l = server.getLevel(p.level());
            LivingEntity owner = l == null ? null : (l.getEntity(en.getKey()) instanceof LivingEntity le ? le : null);
            if (owner == null || !owner.isAlive() || l.getGameTime() >= p.until()) {
                PRESSURE.remove(en.getKey());
                continue;
            }
            Vec3 c = owner.position();
            int n = 0;
            for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(p.radius()),
                    t -> t != owner && LkUtil.isFoe(p.ctx(), t) && t.distanceToSqr(c) <= p.radius() * p.radius())) {
                if (n++ >= MAX_TARGETS) break;
                press(p.ctx(), owner, t, p.pressure(), 30);
            }
            // a distorção da pressão: um anel que "pesa" no chão a cada segundo
            if (l.getGameTime() % 20 == 0) {
                ElementFx.shockwave(l, c.add(0, 0.1, 0), LkFx.REIATSU, p.radius(), 18, null);
                l.sendParticles(ParticleTypes.CRIMSON_SPORE, c.x, c.y + 1.0, c.z, 6, 0.5, 0.8, 0.5, 0.01);
            }
        }
    }

    private static void tickFields(MinecraftServer server) {
        Iterator<Field> it = FIELDS.iterator();
        while (it.hasNext()) {
            Field f = it.next();
            ServerLevel l = server.getLevel(f.level());
            if (l == null || l.getGameTime() >= f.until()) {
                it.remove();
                continue;
            }
            Vec3 c = f.center();
            float r = f.radius();
            // pétalas (poucas: o cliente multiplica pelo preset)
            l.sendParticles(ParticleTypes.CHERRY_LEAVES, c.x, c.y + 1.2, c.z, 6, r * 0.45, 0.4, r * 0.45, 0.0);
            l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 0.4, c.z, 4, r * 0.4, 0.2, r * 0.4, 0.0);
            int n = 0;
            for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 3, r),
                    t -> t.isAlive() && !t.isSpectator() && t.distanceToSqr(c) <= r * r + 4)) {
                if (n++ >= MAX_TARGETS * 2) break;
                boolean ally = t == f.ctx().caster() || f.ctx().isAlly(t);
                if (ally) {
                    if (t.getHealth() < t.getMaxHealth()) t.heal(f.heal());
                } else if (t instanceof Mob m && LkUtil.ordinaryMob(m)) {
                    m.setTarget(null);
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));
                } else if (t instanceof Mob m && LkUtil.boss(m)) {
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false));
                }
            }
            if (l.getGameTime() % 60 == 0) LkUtil.sound(l, c, SoundEvents.AZALEA_LEAVES_PLACE, 0.4F, 1.4F);
        }
    }

    public static boolean hasPressure(LivingEntity e) {
        return PRESSURE.containsKey(e.getUUID());
    }

    public static void forget(UUID id) {
        PRESSURE.remove(id);
    }

    public static void clear() {
        PRESSURE.clear();
        FIELDS.clear();
    }

    /** Usado por jogadores que saem: nada de campo órfão no meio da cidade. */
    public static void forgetFieldsOf(ServerPlayer sp) {
        FIELDS.removeIf(f -> sp.getUUID().equals(f.ctx().casterId()));
    }

    private Auras() {}
}
