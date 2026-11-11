package com.renanpontes.isekaimahou.feature.outfits;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Entrega automática dos trajes de grau: ao alcançar o Círculo 3 / 5 / 8 o jogador recebe o traje da SUA classe
 * no grau Adepto / Mestre / Arquimago (uma vez; flag {@code gear.armor.<grau>}).
 * <p>
 * v1.0 — o traje é procurado no REGISTRO, não num enum fixo ({@link #outfitFor}): as 5 Mágicas usam o traje deste módulo;
 * as 10 classes novas trazem o traje no próprio módulo {@code class_<id>} (que o registra no {@link ClassKits} e cuida
 * das entregas dele), então aqui não recebem nada. Só se o kit da classe NÃO tiver armadura (módulo da classe ausente
 * ou sem traje) entra a reserva avulsa da categoria ({@link OutfitSet#fallbackFor}): vestida ao escolher a classe e
 * entregue nos Círculos como as outras.
 * <ul>
 *   <li>Peças vão direto para o corpo quando o espaço está vazio ou tem um traje de grau menor sem encantamentos
 *   (a peça antiga vai para o inventário); senão vão para o inventário.</li>
 *   <li>Se o jogador pular graus (comando, mundo antigo), recebe só o maior e os menores ficam marcados.</li>
 *   <li>Também confere ao escolher a classe e ao entrar no mundo (saves da v0.6 que já passaram do Círculo).</li>
 * </ul>
 */
public final class OutfitRewards {
    /** Jogadores que acabaram de entrar: espera alguns ticks para o cliente estar pronto para o toast. */
    private static final Map<UUID, Integer> LOGIN_DELAY = new HashMap<>();
    private static final int LOGIN_TICKS = 60;

    static void register() {
        NeoForge.EVENT_BUS.addListener(OutfitRewards::onCircleChanged);
        NeoForge.EVENT_BUS.addListener(OutfitRewards::onPathChosen);
        NeoForge.EVENT_BUS.addListener(OutfitRewards::onLogin);
        NeoForge.EVENT_BUS.addListener(OutfitRewards::onRespawn);
        NeoForge.EVENT_BUS.addListener(OutfitRewards::onLogout);
        NeoForge.EVENT_BUS.addListener(OutfitRewards::onPlayerTick);
    }

    private static void onCircleChanged(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle > e.oldCircle) grantPending(e.getPlayer());
    }

    private static void onPathChosen(MagicProgressEvent.PathChosen e) {
        dressFallbackKit(e.getPlayer());
        grantPending(e.getPlayer());
    }

    /** Flag da reserva do kit (uma vez por jogador, como o kit do núcleo). */
    public static final String FALLBACK_KIT_FLAG = "gear.armor.fallback_kit";

    /**
     * Traje deste módulo que a classe usa, procurado por registro: o da própria classe (Mágicas); senão, se o kit da
     * classe no {@link ClassKits} não tem nenhuma armadura, o traje avulso de reserva; senão null (o módulo da classe
     * entrega o traje dele).
     */
    @Nullable
    public static OutfitSet outfitFor(MagicPath path) {
        if (path == null || path == MagicPath.NONE) return null;
        OutfitSet own = OutfitSet.byPath(path);
        if (own != null) return own;
        return ClassKits.armor(path).isEmpty() ? OutfitSet.fallbackFor(path) : null;
    }

    /**
     * Classe sem traje no kit (módulo ausente): veste o traje avulso de reserva no grau I nos espaços vazios (o resto
     * vai para o inventário), uma vez. Classes com traje próprio não passam por aqui.
     */
    private static void dressFallbackKit(ServerPlayer player) {
        MagicData data = MagicApi.get(player);
        MagicPath path = data.path();
        if (path == null || OutfitSet.byPath(path) != null || data.hasFlag(FALLBACK_KIT_FLAG)) return;
        OutfitSet set = outfitFor(path);
        if (set == null) return;
        data.setFlag(FALLBACK_KIT_FLAG, true);
        for (ArmorItem.Type type : OutfitRegistries.TYPES) {
            ItemStack fresh = new ItemStack(OutfitRegistries.piece(set, OutfitTier.APPRENTICE, type).get());
            if (player.getItemBySlot(type.getSlot()).isEmpty()) player.setItemSlot(type.getSlot(), fresh);
            else Rewards.give(player, fresh);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) LOGIN_DELAY.put(sp.getUUID(), LOGIN_TICKS);
    }

    /** Se o Círculo subiu enquanto o jogador estava morto (ou ele entrou morto), a entrega sai ao renascer. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !e.isEndConquered()) LOGIN_DELAY.put(sp.getUUID(), 20);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LOGIN_DELAY.remove(e.getEntity().getUUID());
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (LOGIN_DELAY.isEmpty() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Integer left = LOGIN_DELAY.get(sp.getUUID());
        if (left == null) return;
        if (left > 0) {
            LOGIN_DELAY.put(sp.getUUID(), left - 1);
            return;
        }
        if (!sp.isAlive()) return; // morto na tela de morte: espera renascer (o contador fica em 0)
        LOGIN_DELAY.remove(sp.getUUID());
        grantPending(sp);
    }

    /** Entrega o maior grau já alcançado e ainda não recebido (se o jogador tem classe). */
    public static void grantPending(ServerPlayer player) {
        if (!player.isAlive()) return;
        MagicData data = MagicApi.get(player);
        OutfitSet set = outfitFor(data.path());
        if (set == null) return; // sem classe ainda (a escolha confere de novo) ou o módulo da classe entrega o traje dele
        OutfitTier best = null;
        for (OutfitTier tier : OutfitTier.REWARDS) {
            if (data.circle() < tier.circle || data.hasFlag(tier.flag())) continue;
            data.setFlag(tier.flag(), true);
            best = tier;
        }
        if (best != null) deliver(player, set, best);
    }

    /** Entrega o traje inteiro com festa: peças, toast, mensagem, som e partículas. */
    public static void deliver(ServerPlayer player, OutfitSet set, OutfitTier tier) {
        // traje avulso (reserva) aparece com o nome e o recurso da classe de quem recebe
        MagicPath owner = set.isGeneric() ? MagicApi.get(player).path() : set.path;
        int equipped = 0;
        for (ArmorItem.Type type : OutfitRegistries.TYPES) {
            ItemStack fresh = new ItemStack(OutfitRegistries.piece(set, tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = player.getItemBySlot(slot);
            if (canReplace(worn, tier)) {
                if (!worn.isEmpty()) Rewards.give(player, worn.copy());
                player.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(player, fresh);
            }
        }
        ItemStack icon = new ItemStack(OutfitRegistries.piece(set, tier, ArmorItem.Type.CHESTPLATE).get());
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.outfits.title", set.tierName(tier)),
                Component.translatable("toast.isekai_mahou.outfits.subtitle", owner.displayName(), tier.circle), icon, tier.color);

        player.sendSystemMessage(Component.translatable("message.isekai_mahou.outfits.granted", tier.circle,
                        set.tierName(tier).withStyle(tier.chatColor()), owner.displayName())
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(statsLine(set, tier, owner));
        Component placed = equipped == OutfitRegistries.TYPES.length
                ? Component.translatable("message.isekai_mahou.outfits.equipped")
                : equipped == 0 ? Component.translatable("message.isekai_mahou.outfits.inventory")
                : Component.translatable("message.isekai_mahou.outfits.partial");
        player.sendSystemMessage(placed.copy().withStyle(ChatFormatting.GRAY));
        if (tier.next() != null) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.outfits.upgrade_hint",
                    OutfitArmorItem.templateName()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        celebrate(player, tier);
    }

    /** Espaço vazio, ou traje de grau menor sem encantamentos (não troca o que o jogador investiu). */
    private static boolean canReplace(ItemStack worn, OutfitTier tier) {
        if (worn.isEmpty()) return true;
        return worn.getItem() instanceof OutfitArmorItem old && old.tier().ordinal() < tier.ordinal() && !worn.isEnchanted();
    }

    private static void celebrate(ServerPlayer player, OutfitTier tier) {
        ServerLevel level = player.serverLevel();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F + 0.1F * tier.number);
        level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
        int rgb = tier.color;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                (rgb & 0xFF) / 255F), 1.2F);
        // espiral subindo em volta do jogador, na cor do grau
        for (int i = 0; i < 48; i++) {
            float a = i * 0.45F;
            double r = 0.9 - i * 0.008;
            level.sendParticles(dust, x + Mth.cos(a) * r, y + i * 0.045, z + Mth.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 1.2, z, 60, 0.4, 0.7, 0.4, 0.8);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 12 + 6 * tier.number, 0.35, 0.6, 0.35, 0.04);
    }

    /** "Conjunto completo: Defesa 30 · Resistência 20 · +180 Vigor · +20 de Vida · ..." (os bônus da categoria). */
    public static MutableComponent statsLine(OutfitSet set, OutfitTier tier) {
        return statsLine(set, tier, set.path);
    }

    /** Igual, com o recurso (Mana/Vigor/Foco) nomeado pela classe {@code shownAs}. */
    public static MutableComponent statsLine(OutfitSet set, OutfitTier tier, MagicPath shownAs) {
        MagicPath named = shownAs == null || shownAs == MagicPath.NONE ? set.path : shownAs;
        MutableComponent line = Component.translatable("message.isekai_mahou.outfits.stats_head",
                OutfitStats.totalDefense(set, tier), OutfitStats.fmt(OutfitStats.toughness(set, tier) * 4));
        for (OutfitStats.Bonus b : OutfitStats.totals(set, tier)) {
            line.append(Component.translatable("message.isekai_mahou.outfits.sep")).append(b.stat().describe(b.amount(), named));
        }
        return line.withStyle(ChatFormatting.GRAY);
    }

    private OutfitRewards() {}
}
