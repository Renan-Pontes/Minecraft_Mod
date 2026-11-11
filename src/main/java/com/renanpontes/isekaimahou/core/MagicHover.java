package com.renanpontes.isekaimahou.core;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Servidores com "allow-flight=false" expulsam quem fica no ar por ~4 s ("Não é permitido voar nesse servidor").
 * Magias seguram jogadores no ar de propósito (Atômico, Parar o Tempo, Levitar, Transcendência, prisões...), então
 * o jogador fica liberado dessa checagem (ver {@code ServerGamePacketListenerImplMixin}) enquanto:
 * <ul><li>estiver sob QUALQUER efeito do Isekai Mahou;</li>
 * <li>tiver conjurado uma magia nos últimos 10 s; ou</li>
 * <li>um módulo pedir com {@link #allow(Player, int)}.</li></ul>
 */
public final class MagicHover {
    private static final String KEY = IsekaiMahou.MODID + ".hover_until";
    private static final int AFTER_CAST = 200;

    /** Libera o jogador da checagem de voo por {@code ticks}. */
    public static void allow(Player player, int ticks) {
        long until = player.level().getGameTime() + Math.max(1, ticks);
        if (player.getPersistentData().getLong(KEY) < until) player.getPersistentData().putLong(KEY, until);
    }

    public static boolean exempt(ServerPlayer player) {
        if (player.getPersistentData().getLong(KEY) >= player.level().getGameTime()) return true;
        for (MobEffectInstance inst : player.getActiveEffects()) {
            Holder<MobEffect> h = inst.getEffect();
            if (h.unwrapKey().map(k -> IsekaiMahou.MODID.equals(k.location().getNamespace())).orElse(false)) return true;
        }
        return false;
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((SpellCastEvent.Post e) -> {
            if (e.getCaster() instanceof Player p && !p.level().isClientSide) allow(p, AFTER_CAST);
        });
    }

    private MagicHover() {}
}
