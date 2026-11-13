package com.renanpontes.isekaimahou.feature.mana_core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SOBRECARGA de mana: mana guardado ACIMA do seu máximo (vem de retirar do Núcleo de Mana além do limite).
 * <ul>
 *   <li>Fica num "anexo" do jogador ({@link ManaCoreRegistries#OVERCHARGE}, salvo; some ao morrer).</li>
 *   <li>É gasto PRIMEIRO: a cada tick, se o mana normal baixou, a sobrecarga completa (o mana fica cheio até ela acabar).</li>
 *   <li>Escoa sozinha devagar: 1% por segundo (no mínimo 1).</li>
 *   <li>Cada {@link #STEP} completos = 1 nível de Intoxicação por Mana (dano até ficar com 1 de vida; nunca mata).</li>
 *   <li>Teto: {@link #cap} (o seu máximo de mana, no mínimo 600).</li>
 * </ul>
 * O valor do jogador local fica em {@link #clientAmount} (HUD), mandado pelo pacote {@link ManaCoreNet.OverchargeSync}.
 */
public final class Overcharge {
    /** Cada 300 acima do máximo = 1 nível de Intoxicação. */
    public static final int STEP = 300;
    public static final int MAX_LEVEL = 10;
    /** Cor (magenta) da sobrecarga: HUD, mensagens, efeito. */
    public static final int RGB = 0xE04BFF;
    private static final float BLEED_RATE = 0.01F, BLEED_MIN = 1F;

    /** Cliente: sobrecarga do jogador local (HUD). */
    public static volatile float clientAmount;

    /** Último valor mandado a cada jogador (para só mandar quando muda). */
    private static final Map<UUID, Float> SENT = new HashMap<>();

    public static float get(Player p) {
        // Sem o anexo = 0 (não cria o anexo em todo jogador só por ler)
        return p.hasData(ManaCoreRegistries.OVERCHARGE.get()) ? p.getData(ManaCoreRegistries.OVERCHARGE.get()) : 0F;
    }

    public static void set(Player p, float value) {
        float v = Math.max(0F, value);
        if (v < 0.5F) v = 0F;
        if (v == 0F && !p.hasData(ManaCoreRegistries.OVERCHARGE.get())) return;
        p.setData(ManaCoreRegistries.OVERCHARGE.get(), v);
    }

    /** Máximo de sobrecarga que dá para segurar. */
    public static int cap(MagicData d) {
        return Math.max(STEP * 2, d.maxMana());
    }

    /** Nível de Intoxicação para esta sobrecarga (0 = nenhum). */
    public static int level(float over) {
        return Math.min(MAX_LEVEL, (int) (over / STEP));
    }

    // =====================================================================================
    //  Tick do servidor
    // =====================================================================================
    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.isFakePlayer()) return;
        float over = get(sp);
        if (over > 0F) {
            MagicData d = MagicApi.get(sp);
            // 1) Gasta a sobrecarga primeiro: o que faltar no mana normal sai dela
            int max = d.maxMana();
            if (d.mana() < max) {
                float take = Math.min(over, max - d.mana());
                d.addMana(take);
                over -= take;
            }
            // 2) A cada segundo: escoa, respeita o teto e aplica a Intoxicação
            if (sp.tickCount % 20 == 0) {
                over -= Math.max(BLEED_MIN, over * BLEED_RATE);
                over = Math.min(over, cap(d));
                applyIntoxication(sp, level(over));
            }
            set(sp, over);
        }
        if (sp.tickCount % 4 == 0) sync(sp, false);
    }

    /** Mantém o efeito no nível certo enquanto houver sobrecarga (dura 4 s depois que ela baixa). */
    private static void applyIntoxication(ServerPlayer sp, int lvl) {
        if (lvl <= 0) return;
        var effect = ManaCoreRegistries.MANA_INTOXICATION;
        MobEffectInstance cur = sp.getEffect(effect);
        int amp = lvl - 1;
        if (cur != null && cur.getAmplifier() == amp && cur.getDuration() > 40) return;
        if (cur != null && cur.getAmplifier() != amp) sp.removeEffect(effect);
        if (cur == null || cur.getAmplifier() < amp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.mana_core.intoxicated", lvl)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, sp.getSoundSource(), 0.25F, 1.6F);
        }
        sp.addEffect(new MobEffectInstance(effect, 80, amp, false, true, true));
    }

    // =====================================================================================
    //  Sincronização
    // =====================================================================================
    public static void sync(ServerPlayer sp, boolean force) {
        float v = get(sp);
        Float last = SENT.get(sp.getUUID());
        if (!force && last != null && Math.abs(last - v) < 0.5F) return;
        SENT.put(sp.getUUID(), v);
        PacketDistributor.sendToPlayer(sp, new ManaCoreNet.OverchargeSync(v));
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp, true);
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp, true);
    }

    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp, true);
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SENT.remove(e.getEntity().getUUID());
    }

    private Overcharge() {}
}
