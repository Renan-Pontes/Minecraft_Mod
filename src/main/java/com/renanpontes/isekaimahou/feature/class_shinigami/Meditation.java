package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.InnerWorld;
import com.renanpontes.isekaimahou.feature.class_shinigami.inner.SpiritTrials;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Meditação do Shinigami.
 * <ul>
 *   <li><b>Jinzen</b> (agachado + usar a zanpakutō, ou o botão da aba): a alma desce ao Mundo Interior depois de 5 s
 *   imóvel (3 s para voltar). Mexer-se, levar dano ou estar em combate cancela;</li>
 *   <li><b>Regeneração espiritual</b> (bônus de classe): enquanto medita do jeito normal do núcleo (agachado e parado),
 *   o Shinigami cura vida (1 + 0,5 por rank por segundo), recupera 1% do Vigor por segundo a mais e solta um efeito
 *   ruim a cada 3 s.</li>
 * </ul>
 */
public final class Meditation {
    public static final int DESCEND_TICKS = 100;
    public static final int RETURN_TICKS = 60;

    private static final class Session {
        final Vec3 start;
        final boolean returning;
        int ticks;

        Session(Vec3 start, boolean returning) {
            this.start = start;
            this.returning = returning;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean active(ServerPlayer sp) {
        return SESSIONS.containsKey(sp.getUUID());
    }

    /** Começa (ou cancela) o Jinzen. */
    public static void toggle(ServerPlayer sp) {
        if (SESSIONS.remove(sp.getUUID()) != null) {
            sp.displayClientMessage(Shinigami.msg("jinzen_cancel").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        begin(sp);
    }

    public static void begin(ServerPlayer sp) {
        if (!Shinigami.isShinigami(sp)) return;
        boolean returning = InnerWorld.is(sp.level());
        if (returning && SpiritTrials.inTrial(sp)) {
            sp.displayClientMessage(Shinigami.msg("jinzen_trial").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!returning) {
            if (sp.isPassenger() || !sp.onGround()) {
                sp.displayClientMessage(Shinigami.msg("jinzen_ground").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            if (sp.tickCount - sp.getLastHurtByMobTimestamp() < 100 && sp.getLastHurtByMob() != null) {
                sp.displayClientMessage(Shinigami.msg("jinzen_combat").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        SESSIONS.put(sp.getUUID(), new Session(sp.position(), returning));
        ShinigamiFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BEACON_AMBIENT, 1.0F, 1.4F);
        sp.displayClientMessage(Shinigami.msg(returning ? "jinzen_return_start" : "jinzen_start").withColor(Shinigami.REIATSU_RGB), true);
    }

    static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        List<UUID> done = new ArrayList<>();
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            Session s = e.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp == null || !sp.isAlive()) {
                it.remove();
                continue;
            }
            if (sp.position().distanceToSqr(s.start) > 0.25 || sp.hurtTime > 0) {
                it.remove();
                sp.displayClientMessage(Shinigami.msg("jinzen_broken").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            s.ticks++;
            int total = s.returning ? RETURN_TICKS : DESCEND_TICKS;
            ServerLevel sl = sp.serverLevel();
            // fitas de Reiatsu descendo em espiral até o peito
            double ang = s.ticks * 0.35;
            double r = 1.4 * (1 - s.ticks / (double) total) + 0.3;
            Vec3 c = sp.position();
            for (int k = 0; k < 2; k++) {
                double a = ang + k * Math.PI;
                sl.sendParticles(ShinigamiFx.SPIRIT, c.x + Math.cos(a) * r, c.y + 0.2 + 1.4 * (s.ticks % 20) / 20.0, c.z + Math.sin(a) * r,
                        1, 0, 0, 0, 0);
            }
            if (s.ticks % 10 == 0) {
                int filled = Math.round(10F * s.ticks / total);
                String bar = "▮".repeat(filled) + "▯".repeat(10 - filled);
                sp.displayClientMessage(Shinigami.msg(s.returning ? "jinzen_return_bar" : "jinzen_bar", bar).withColor(Shinigami.REIATSU_RGB), true);
                ShinigamiFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 0.6F + 0.08F * filled);
            }
            if (s.ticks >= total) {
                it.remove();
                done.add(e.getKey());
            }
        }
        for (UUID id : done) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) continue;
            if (InnerWorld.is(sp.level())) InnerWorld.leave(sp);
            else InnerWorld.enter(sp);
        }
    }

    /** Bônus de classe: regeneração espiritual durante a meditação do núcleo (chamado 1× por segundo). */
    static void spiritualRegen(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (!d.meditating) return;
        int rank = Shinigami.rank(d);
        if (sp.getHealth() < sp.getMaxHealth()) sp.heal((1.0F + 0.5F * rank) * Progression.HEAL_SCALE);
        Shinigami.gainVigor(sp, 0.01F);
        int t = d.stillTicks;
        if (t > 0 && t % 60 < 20) {
            for (MobEffectInstance inst : sp.getActiveEffects()) {
                Holder<MobEffect> h = inst.getEffect();
                if (h.value().getCategory() == MobEffectCategory.HARMFUL) {
                    sp.removeEffect(h);
                    break;
                }
            }
        }
        ServerLevel sl = sp.serverLevel();
        double a = sp.tickCount * 0.1;
        sl.sendParticles(ShinigamiFx.SPIRIT, sp.getX() + Mth.cos((float) a) * 0.7, sp.getY() + 0.3, sp.getZ() + Mth.sin((float) a) * 0.7,
                2, 0.05, 0.3, 0.05, 0.0);
    }

    static void forget(UUID id) {
        SESSIONS.remove(id);
    }

    static void clear() {
        SESSIONS.clear();
    }

    /** Linha para a aba: como meditar. */
    public static Component hint() {
        return Shinigami.msg("jinzen_hint");
    }

    private Meditation() {}
}
