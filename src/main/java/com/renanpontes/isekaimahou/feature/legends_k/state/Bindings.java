package com.renanpontes.isekaimahou.feature.legends_k.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Ligações entre DUAS criaturas, com tick próprio:
 * <ul>
 *   <li><b>Possessão das Sombras</b> (Shikamaru, Patrulheiro): a sombra do conjurador prende o alvo, que passa a
 *       repetir os movimentos dele (anda quando ele anda, para quando ele para). Quebra a 16 blocos.</li>
 *   <li><b>Aschente</b> (No Game No Life, Apostador): um jogo de 8 s contra o alvo — quem causar MAIS dano ao outro
 *       vence. Perder custa caro (o alvo: 25% da vida máxima e Fraqueza; você: Fraqueza e 20% do mana).</li>
 * </ul>
 */
public final class Bindings {
    // ---------- Possessão ----------
    private record Possess(UUID caster, ResourceKey<Level> level, long until) {}

    private static final Map<UUID, Possess> POSSESSED = new HashMap<>();

    // ---------- Aschente ----------
    private static final class Game {
        final UUID target;
        final ResourceKey<Level> level;
        final long until;
        final float power;
        float byCaster, byTarget;

        Game(UUID target, ResourceKey<Level> level, long until, float power) {
            this.target = target;
            this.level = level;
            this.until = until;
            this.power = power;
        }
    }

    private static final Map<UUID, Game> GAMES = new HashMap<>();

    public static void possess(LivingEntity caster, LivingEntity target, int ticks) {
        POSSESSED.put(target.getUUID(), new Possess(caster.getUUID(), caster.level().dimension(), caster.level().getGameTime() + ticks));
        if (target instanceof Mob m) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
    }

    public static boolean possessed(Entity e) {
        return POSSESSED.containsKey(e.getUUID());
    }

    /** Começa um jogo (um por conjurador; um novo substitui o antigo sem resultado). */
    public static boolean aschente(LivingEntity caster, LivingEntity target, int ticks, float power) {
        GAMES.put(caster.getUUID(), new Game(target.getUUID(), caster.level().dimension(), caster.level().getGameTime() + ticks, power));
        return true;
    }

    /** Conta o dano de verdade (depois de armadura e defesas) entre os dois jogadores do jogo. */
    public static void onDamage(LivingDamageEvent.Post e) {
        if (GAMES.isEmpty()) return;
        Entity attacker = e.getSource().getEntity();
        if (attacker == null) return;
        LivingEntity v = e.getEntity();
        Game g = GAMES.get(attacker.getUUID());
        if (g != null && g.target.equals(v.getUUID())) g.byCaster += e.getNewDamage();
        g = GAMES.get(v.getUUID());
        if (g != null && g.target.equals(attacker.getUUID())) g.byTarget += e.getNewDamage();
    }

    public static void tick(MinecraftServer server) {
        if (!POSSESSED.isEmpty()) tickPossession(server);
        if (!GAMES.isEmpty() && server.getTickCount() % 5 == 0) tickGames(server);
    }

    private static void tickPossession(MinecraftServer server) {
        for (Map.Entry<UUID, Possess> en : new ArrayList<>(POSSESSED.entrySet())) {
            Possess p = en.getValue();
            ServerLevel l = server.getLevel(p.level());
            Entity te = l == null ? null : l.getEntity(en.getKey());
            Entity ce = l == null ? null : l.getEntity(p.caster());
            if (!(te instanceof LivingEntity t) || !(ce instanceof LivingEntity c) || !t.isAlive() || !c.isAlive()
                    || l.getGameTime() >= p.until() || t.distanceToSqr(c) > 16 * 16) {
                POSSESSED.remove(en.getKey());
                if (ce != null && l != null && l.getGameTime() < p.until()) LkUtil.say(ce, "possession_broken", ChatFormatting.GRAY);
                continue;
            }
            // o alvo repete o movimento do conjurador (só no plano; a gravidade continua)
            Vec3 move = new Vec3(c.getX() - c.xo, 0, c.getZ() - c.zo);
            Vec3 cur = t.getDeltaMovement();
            if (t instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
                t.setDeltaMovement(move.x, cur.y, move.z);
                t.setYRot(c.getYRot());
                t.setYHeadRot(c.getYHeadRot());
            } else if (t instanceof Player) {
                if (l.getGameTime() % 2 == 0) LkUtil.impulse(t, new Vec3(move.x, cur.y, move.z));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false));
            }
            // a linha de sombra no chão (a cada 5 ticks)
            if (l.getGameTime() % 5 == 0) {
                LkFx.line(l, c.position().add(0, 0.05, 0), t.position().add(0, 0.05, 0), LkFx.SHADOW, 0.35F);
            }
        }
    }

    private static void tickGames(MinecraftServer server) {
        for (Map.Entry<UUID, Game> en : new ArrayList<>(GAMES.entrySet())) {
            Game g = en.getValue();
            ServerLevel l = server.getLevel(g.level);
            Entity ce = l == null ? null : l.getEntity(en.getKey());
            Entity te = l == null ? null : l.getEntity(g.target);
            if (!(ce instanceof LivingEntity c) || !(te instanceof LivingEntity t) || !c.isAlive()) {
                GAMES.remove(en.getKey());
                continue;
            }
            if (!t.isAlive()) {
                // o alvo caiu antes do fim: vitória
                GAMES.remove(en.getKey());
                LkUtil.say(c, "aschente_win", ChatFormatting.LIGHT_PURPLE);
                continue;
            }
            if (l.getGameTime() % 20 == 0) {
                LkFx.ring(l, t.position().add(0, t.getBbHeight() + 0.4, 0), 0.4, LkFx.ASCHENTE, 0.25F, 8);
                LkFx.ring(l, c.position().add(0, c.getBbHeight() + 0.4, 0), 0.4, LkFx.ASCHENTE, 0.25F, 8);
            }
            if (l.getGameTime() < g.until) continue;
            GAMES.remove(en.getKey());
            finish(l, c, t, g);
        }
    }

    private static void finish(ServerLevel l, LivingEntity c, LivingEntity t, Game g) {
        Vec3 tc = LkUtil.center(t);
        if (g.byCaster > g.byTarget) {
            float frac = LkUtil.boss(t) ? 0.05F : 0.25F;
            t.hurt(l.damageSources().magic(), Math.max(2F, t.getMaxHealth() * frac * Math.min(2F, g.power)));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            LkFx.swirl(l, tc, 0.9, LkFx.ASCHENTE, 24);
            LkUtil.sound(l, tc, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.6F);
            LkUtil.say(c, "aschente_win", ChatFormatting.LIGHT_PURPLE);
            LkUtil.say(t, "aschente_lose", ChatFormatting.DARK_PURPLE);
        } else if (g.byTarget > g.byCaster) {
            c.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            if (c instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
                var d = com.renanpontes.isekaimahou.core.MagicApi.get(sp);
                d.consumeMana(d.mana() * 0.2F);
            }
            LkUtil.sound(l, LkUtil.center(c), SoundEvents.VILLAGER_NO, 0.8F, 0.8F);
            LkUtil.say(c, "aschente_lose", ChatFormatting.DARK_PURPLE);
            LkUtil.say(t, "aschente_win", ChatFormatting.LIGHT_PURPLE);
        } else {
            LkUtil.say(c, "aschente_draw", ChatFormatting.GRAY);
        }
    }

    public static void forget(UUID id) {
        POSSESSED.remove(id);
        POSSESSED.values().removeIf(p -> p.caster().equals(id));
        GAMES.remove(id);
    }

    public static void clear() {
        POSSESSED.clear();
        GAMES.clear();
    }

    private Bindings() {}
}
