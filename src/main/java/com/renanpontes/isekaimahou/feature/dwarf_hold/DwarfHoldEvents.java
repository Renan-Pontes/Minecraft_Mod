package com.renanpontes.isekaimahou.feature.dwarf_hold;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Eventos de jogo do dwarf_hold: chegada à cidade (diário), contador de Mithril minerado e bônus do conjunto. */
public final class DwarfHoldEvents {
    public static final ResourceKey<Structure> CITY = ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id("dwarf_hold"));
    public static final String FLAG_ENTERED = "dwarf_hold.entered";
    public static final String COUNTER_MITHRIL = "dwarf_hold.mithril";

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if ((p.tickCount + p.getId()) % 40 != 0) return;
        if (p.getY() < 0 && fullMithril(p)) {
            // Bênção da Montanha: o conjunto de Mithril dá Pressa I no subterrâneo profundo
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 80, 0, true, false, true));
        }
        if (p.getY() < 10 && p.level().dimension() == Level.OVERWORLD) checkCity(p);
    }

    private static void checkCity(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        if (d.hasFlag(FLAG_ENTERED)) return;
        ServerLevel sl = p.serverLevel();
        Structure s = sl.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CITY);
        if (s == null || !sl.structureManager().getStructureWithPieceAt(p.blockPosition(), s).isValid()) return;
        d.setFlag(FLAG_ENTERED, true);
        // letreiro só para quem chegou
        p.connection.send(new ClientboundSetTitlesAnimationPacket(15, 70, 25));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.isekai_mahou.dwarf_hold.found")
                .withStyle(s2 -> s2.withColor(0xE0A040))));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.isekai_mahou.dwarf_hold.found.title_sub")
                .withStyle(ChatFormatting.GRAY)));
        p.displayClientMessage(Component.translatable("message.isekai_mahou.dwarf_hold.found.sub").withStyle(ChatFormatting.GOLD), false);
        sl.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.0F, 0.6F);
    }

    public static boolean fullMithril(ServerPlayer p) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getItemBySlot(slot);
            if (!(s.getItem() instanceof ArmorItem a) || !a.getMaterial().is(DwarfRegistry.MITHRIL_MATERIAL.getKey())) return false;
        }
        return true;
    }

    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && !sp.isCreative()
                && e.getState().is(DwarfRegistry.DEEPSLATE_MITHRIL_ORE.get())) {
            MagicApi.addCounter(sp, COUNTER_MITHRIL, 1);
        }
    }

    private DwarfHoldEvents() {}
}
