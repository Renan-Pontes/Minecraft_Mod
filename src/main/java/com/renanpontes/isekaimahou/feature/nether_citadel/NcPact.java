package com.renanpontes.isekaimahou.feature.nether_citadel;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Regras do Altar do Pacto. Pacto: 5 min (Bruxo/Warlock: 8 min) de poder alto, com preço de sangue na hora (até 4 de vida,
 * nunca mata), vida máxima menor e fome durante. Quando acaba (ou é removido), vem a Dívida Infernal: 3 min (Warlock:
 * 1 min) — a não ser que o pacto tenha sido selado com o Selo do Lorde Demônio.
 */
public final class NcPact {
    public static final String FREE_KEY = "isekai_mahou.demon_pact_free";
    public static final int COLOR = 0xB0122A;

    public static int pactTicks(Player p) {
        return NcProgress.isWarlock(p) ? 9600 : 6000;
    }

    public static int debtTicks(Player p) {
        return NcProgress.isWarlock(p) ? 1200 : 3600;
    }

    /** Motivo de recusa (chave de tradução) ou null se o pacto pode ser feito. */
    @Nullable
    public static String refusal(Player p) {
        if (p.hasEffect(NcRegistries.DEMON_DEBT)) return "block.isekai_mahou.demon_pact_altar.in_debt";
        if (p.hasEffect(NcRegistries.DEMON_PACT)) return "block.isekai_mahou.demon_pact_altar.already";
        return null;
    }

    /** Sela o pacto (a oferenda já foi validada/consumida por quem chamou). */
    public static void seal(ServerPlayer sp, boolean free) {
        ServerLevel level = sp.serverLevel();
        if (!sp.isCreative()) {
            float price = Math.min(4.0F, sp.getHealth() - 1.0F);
            if (price > 0) sp.hurt(sp.damageSources().magic(), price);
        }
        sp.getPersistentData().putBoolean(FREE_KEY, free);
        sp.addEffect(new MobEffectInstance(NcRegistries.DEMON_PACT, pactTicks(sp), 0, false, true, true));
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sp.getX(), sp.getY() + 1.0, sp.getZ(), 40, 0.5, 0.9, 0.5, 0.03);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, sp.getX(), sp.getY() + 1.0, sp.getZ(), 30, 0.8, 1.0, 0.8, 0.0);
        level.playSound(null, sp.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.6F);
        level.playSound(null, sp.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
        String key = free ? "block.isekai_mahou.demon_pact_altar.sealed_free"
                : NcProgress.isWarlock(sp) ? "block.isekai_mahou.demon_pact_altar.sealed_warlock" : "block.isekai_mahou.demon_pact_altar.sealed";
        sp.sendSystemMessage(Component.translatable(key).withColor(COLOR));
        NcProgress.flag(sp, NcProgress.FLAG_PACT);
    }

    /** O pacto terminou (expirou ou foi removido): cobra a dívida. */
    public static void onPactEnded(LivingEntity e) {
        if (!(e instanceof ServerPlayer sp)) return;
        boolean free = sp.getPersistentData().getBoolean(FREE_KEY);
        sp.getPersistentData().remove(FREE_KEY);
        if (free || sp.isCreative() || !sp.isAlive()) return;
        // v1.0 (correção): este método é chamado de DENTRO da lista de efeitos do jogador (pacto expirando, leite, totem).
        // Dar a Dívida ali mesmo mexia na lista durante a iteração: ao beber leite/usar totem o servidor caía
        // (ConcurrentModificationException) e, ao expirar, o pacto durava um tick a mais e a mensagem saía em dobro.
        // Por isso a Dívida entra no tick seguinte.
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (sp.isRemoved() || !sp.isAlive() || sp.hasEffect(NcRegistries.DEMON_PACT) || sp.hasEffect(NcRegistries.DEMON_DEBT)) return;
            sp.addEffect(new MobEffectInstance(NcRegistries.DEMON_DEBT, debtTicks(sp), 0, false, true, true));
            sp.sendSystemMessage(Component.translatable("block.isekai_mahou.demon_pact_altar.debt").withStyle(ChatFormatting.DARK_RED));
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
        }));
    }

    private NcPact() {}
}
