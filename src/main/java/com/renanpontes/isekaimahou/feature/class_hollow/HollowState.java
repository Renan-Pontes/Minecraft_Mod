package com.renanpontes.isekaimahou.feature.class_hollow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Estado EM MEMÓRIA do Hollow (não precisa ser salvo: tudo dura segundos): o Hierro concentrado e a RESURRECCIÓN ativa.
 * <ul>
 *   <li><b>Hierro</b>: do Vasto Lorde em diante a pele de ferro é passiva (−25% de dano; a aba desliga); a técnica
 *   concentra por alguns segundos (−30% antes do Vasto Lorde, −50% e sem empurrão depois);</li>
 *   <li><b>Resurrección</b> (forma G do Arrancar): a forma liberada do arquétipo escolhido (ver {@link Hollow.Release}):
 *   Pantera (velocidade, garras que rasgam em volta), Murciélago (voo, a Lança no lugar do Cero), Serpiente (veneno,
 *   regeneração, alcance). Ao acabar, o corpo cobra (Fraqueza e Vigor zerado).</li>
 * </ul>
 */
public final class HollowState {
    private static final class State {
        long hierroUntil;
        boolean hierroStrong;
        long releasedUntil;
        @Nullable
        Hollow.Release release;
        boolean grantedFlight;
        /** v1.4 — Negación: até quando a coluna de luz protege (0 = nenhuma). */
        long negacionUntil;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    // =====================================================================================
    //  Hierro
    // =====================================================================================
    /** Concentra o Hierro por {@code ticks}. */
    public static void hierro(ServerPlayer sp, int ticks, boolean strong) {
        State s = state(sp);
        s.hierroUntil = sp.serverLevel().getGameTime() + ticks;
        s.hierroStrong = strong;
        HollowEvents.syncLook(sp);
    }

    public static boolean hierroActive(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && p.level().getGameTime() < s.hierroUntil;
    }

    /**
     * Fração do dano que a pele de ferro tira (0 = nenhuma). v1.4: o Hierro PASSIVO cresce com o corpo — Gillian 10%,
     * Adjuchas 15%, Vasto Lorde 25%, Arrancar 30% (a aba desliga); concentrado: 30% (50% do Vasto Lorde em diante).
     */
    public static float hierroReduction(Player p, MagicData d) {
        State s = STATES.get(p.getUUID());
        if (s != null && p.level().getGameTime() < s.hierroUntil) return s.hierroStrong ? 0.5F : 0.3F;
        if (d.hasFlag(Hollow.FLAG_HIERRO_OFF)) return 0F;
        return passiveHierro(Hollow.rank(d));
    }

    /** O Hierro passivo do rank (0 no Hollow recém-nascido). */
    public static float passiveHierro(int rank) {
        return switch (rank) {
            case Hollow.GILLIAN -> 0.10F;
            case Hollow.ADJUCHAS -> 0.15F;
            case Hollow.VASTO_LORDE -> 0.25F;
            case Hollow.ARRANCAR -> 0.30F;
            default -> 0F;
        };
    }

    // =====================================================================================
    //  Negación (v1.4)
    // =====================================================================================
    public static void negacion(ServerPlayer sp, int ticks) {
        state(sp).negacionUntil = sp.serverLevel().getGameTime() + ticks;
    }

    /** Dentro da coluna de luz da Negación: nada o toca (e ele não ataca). */
    public static boolean inNegacion(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && p.level().getGameTime() < s.negacionUntil;
    }

    public static boolean hierroStrong(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.hierroStrong && p.level().getGameTime() < s.hierroUntil;
    }

    // =====================================================================================
    //  Resurrección
    // =====================================================================================
    @Nullable
    public static Hollow.Release released(Player p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.release == null || p.level().getGameTime() >= s.releasedUntil) return null;
        return s.release;
    }

    /** Ticks que faltam da forma liberada (0 = selado). */
    public static int releaseTicksLeft(Player p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.release == null) return 0;
        return (int) Math.max(0, s.releasedUntil - p.level().getGameTime());
    }

    public static void release(ServerPlayer sp, Hollow.Release r, int ticks) {
        State s = state(sp);
        s.release = r;
        s.releasedUntil = sp.serverLevel().getGameTime() + ticks;
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0, false, false, true));
        switch (r) {
            case PANTERA -> {
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 2, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 2, false, false, true));
            }
            case MURCIELAGO -> {
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks + 20, 0, false, false, true));
                if (!sp.getAbilities().mayfly) {
                    sp.getAbilities().mayfly = true;
                    sp.onUpdateAbilities();
                    s.grantedFlight = true;
                }
            }
            case SERPIENTE -> {
                sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 1, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0, false, false, true));
            }
        }
        HollowEvents.syncLook(sp);
        HollowRelease.fireStart(sp, r);
    }

    /** Uma vez por tick do servidor: fim da Resurrección (com o preço) e do Hierro. */
    static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> me = it.next();
            State s = me.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(me.getKey());
            if (sp == null) {
                it.remove();
                continue;
            }
            long now = sp.serverLevel().getGameTime();
            if (s.release != null && now >= s.releasedUntil) {
                Hollow.Release was = s.release;
                s.release = null;
                endRelease(sp, s, was, true);
            }
            if (s.hierroUntil != 0 && now >= s.hierroUntil) {
                s.hierroUntil = 0;
                HollowEvents.syncLook(sp);
            }
            if (s.negacionUntil != 0 && now >= s.negacionUntil) s.negacionUntil = 0;
            if (s.release == null && s.hierroUntil == 0 && s.negacionUntil == 0) it.remove();
        }
    }

    private static void endRelease(ServerPlayer sp, State s, Hollow.Release was, boolean price) {
        if (s.grantedFlight) {
            s.grantedFlight = false;
            if (!sp.isCreative() && !sp.isSpectator()) {
                sp.getAbilities().mayfly = false;
                sp.getAbilities().flying = false;
                sp.onUpdateAbilities();
                sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
            }
        }
        HollowEvents.syncLook(sp);
        HollowRelease.fireEnd(sp, was, price);
        if (!price || !sp.isAlive()) return;
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        sl.sendParticles(HollowFx.dust(was.rgb, 1.4F), at.x, at.y + 1, at.z, 30, 0.5, 0.9, 0.5, 0.05);
        sl.sendParticles(HollowFx.BONE, at.x, at.y + 1.6, at.z, 20, 0.3, 0.3, 0.3, 0.08);
        HollowFx.sound(sl, at, SoundEvents.GLASS_BREAK, 1.0F, 0.6F);
        sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
        MagicData d = MagicApi.get(sp);
        d.setMana(d.mana() * 0.25F);
        sp.displayClientMessage(Hollow.msg("release_end").withStyle(ChatFormatting.GRAY), true);
    }

    /** Saiu do jogo / trocou de classe: acaba tudo sem preço (devolve o voo se tinha dado). */
    static void forget(ServerPlayer sp) {
        State s = STATES.remove(sp.getUUID());
        if (s != null && s.release != null) endRelease(sp, s, s.release, false);
    }

    static void clear() {
        STATES.clear();
    }

    private HollowState() {}
}
