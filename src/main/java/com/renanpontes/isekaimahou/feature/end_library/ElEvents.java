package com.renanpontes.isekaimahou.feature.end_library;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.end_library.item.StarGear;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Eventos de jogo do módulo:
 * <ul>
 *   <li>descobrir a Biblioteca (flag {@code end_library.found}, usada pelo Diário) ao entrar na estrutura no End;</li>
 *   <li>Equipamento Estelar exige Círculo 7: arma não acerta, picareta não quebra, armadura sai do corpo;</li>
 *   <li>conjunto estelar completo: visão no escuro e Passo Estelar (cair no vazio do End devolve ao último chão firme,
 *       recarga de 2 min); usar o conjunto com o Círculo certo marca {@code end_library.star_gear}.</li>
 * </ul>
 */
public final class ElEvents {
    public static final ResourceKey<Structure> STRUCTURE = ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id("end_library"));
    public static final String FLAG_FOUND = "end_library.found";
    public static final String FLAG_GEAR = "end_library.star_gear";
    private static final String DATA = "isekai_mahou.end_library";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int RESCUE_COOLDOWN = 2400;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ElEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ElEvents::onAttack);
        NeoForge.EVENT_BUS.addListener(ElEvents::onBreakSpeed);
    }

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(DATA)) root.put(DATA, new CompoundTag());
        return root.getCompound(DATA);
    }

    private static boolean worthy(Player p) {
        return p.getAbilities().instabuild || MagicApi.get(p).circle() >= StarGear.MIN_CIRCLE;
    }

    /** Aviso na barra de ação no máximo a cada 3 s. */
    private static void warn(Player p, String key) {
        CompoundTag d = data(p);
        long now = p.level().getGameTime();
        if (now < d.getLong("Warn")) return;
        d.putLong("Warn", now + 60);
        p.displayClientMessage(Component.translatable(key, StarGear.MIN_CIRCLE).withStyle(ChatFormatting.RED), true);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel sl)) return;
        if (sl.dimension() == Level.END && p.getY() < sl.getMinBuildHeight()) starStep(sl, p);
        if (p.tickCount % 20 != 0) return;
        if (sl.dimension() == Level.END) checkFound(sl, p);
        int pieces = 0;
        boolean worthy = worthy(p);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack worn = p.getItemBySlot(slot);
            if (!StarGear.isStar(worn)) continue;
            if (!worthy) {
                // A armadura estelar rejeita quem não tem Círculo: volta para o inventário (ou cai no chão).
                ItemStack off = worn.copy();
                p.setItemSlot(slot, ItemStack.EMPTY);
                if (!p.getInventory().add(off)) p.drop(off, false);
                warn(p, "message.isekai_mahou.end_library.star_rejects");
                continue;
            }
            pieces++;
        }
        if (pieces > 0 && worthy) {
            MagicData md = MagicApi.get(p);
            if (!md.hasFlag(FLAG_GEAR)) md.setFlag(FLAG_GEAR, true);
        }
        if (pieces == 4) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
            if (p.onGround() && !p.isSpectator()) {
                CompoundTag d = data(p);
                d.putString("SafeDim", sl.dimension().location().toString());
                d.putLong("SafePos", p.blockPosition().asLong());
            }
        }
    }

    /** Passo Estelar: com o conjunto completo, cair no vazio do End devolve ao último chão firme. */
    private static void starStep(ServerLevel sl, ServerPlayer p) {
        if (p.isSpectator() || p.isCreative()) return;
        for (EquipmentSlot slot : ARMOR) {
            if (!StarGear.isStar(p.getItemBySlot(slot))) return;
        }
        if (!worthy(p)) return;
        CompoundTag d = data(p);
        long now = sl.getGameTime();
        if (now < d.getLong("RescueUntil") || !sl.dimension().location().toString().equals(d.getString("SafeDim"))) return;
        BlockPos safe = BlockPos.of(d.getLong("SafePos"));
        d.putLong("RescueUntil", now + RESCUE_COOLDOWN);
        p.teleportTo(sl, safe.getX() + 0.5, safe.getY() + 0.1, safe.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.setDeltaMovement(0, 0, 0);
        p.hurtMarked = true;
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        sl.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.end_library.star_step").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void checkFound(ServerLevel sl, ServerPlayer p) {
        MagicData md = MagicApi.get(p);
        if (md.hasFlag(FLAG_FOUND)) return;
        Structure st = sl.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        if (st == null || !sl.structureManager().getStructureWithPieceAt(p.blockPosition(), st).isValid()) return;
        md.setFlag(FLAG_FOUND, true);
        MagicApi.addCounter(p, "visit.place.end_library", 1);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.end_library.found").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.5F, 0.5F);
    }

    private static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || !StarGear.isStar(p.getMainHandItem()) || worthy(p)) return;
        event.setCanceled(true);
        warn(p, "message.isekai_mahou.end_library.star_too_weak");
    }

    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player p = event.getEntity();
        // Decide no servidor (onde o Círculo é confiável): ele recusa a quebra e o cliente desfaz o bloco.
        if (p.level().isClientSide || !StarGear.isStar(p.getMainHandItem()) || worthy(p)) return;
        event.setCanceled(true);
        warn(p, "message.isekai_mahou.end_library.star_too_weak");
    }

    private ElEvents() {}
}
