package com.renanpontes.isekaimahou.feature.class_enchanter;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * EQUIPAMENTO ENCANTADO ("encantar itens temporariamente"): enquanto o encantamento dura, a arma, as ferramentas e a
 * armadura de quem o recebe ficam encantadas — brilham como itens encantados e, por nível: +1 de dano no golpe (+0,75 em
 * projéteis), −5% de dano recebido e +25% de velocidade de mineração. Ao acabar, o brilho some sozinho dos itens
 * (inventário conferido a cada 2 s; item jogado no chão perde o brilho na hora).
 */
public final class GearEnchant {
    /** Marca no item (CUSTOM_DATA) de que o brilho é temporário (posto por nós). */
    public static final String MARK = "isekai_mahou_enchanter_glint";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    static void register() {
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, GearEnchant::onIncoming);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.BreakSpeed.class, GearEnchant::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, GearEnchant::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ItemTossEvent.class, e -> {
            ItemStack s = e.getEntity().getItem();
            if (strip(s)) e.getEntity().setItem(s);
        });
    }

    /** Põe o brilho temporário nos itens equipados (só nos que ainda não brilham). */
    public static void mark(ServerPlayer sp) {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = sp.getItemBySlot(slot);
            if (s.isEmpty() || s.isEnchanted() || s.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) continue;
            CustomData.update(DataComponents.CUSTOM_DATA, s, tag -> tag.putBoolean(MARK, true));
            s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
    }

    /** Tira o brilho posto por nós. @return true se mexeu no item */
    public static boolean strip(ItemStack s) {
        if (s.isEmpty()) return false;
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.contains(MARK)) return false;
        CustomData.update(DataComponents.CUSTOM_DATA, s, tag -> tag.remove(MARK));
        CustomData after = s.get(DataComponents.CUSTOM_DATA);
        if (after != null && after.isEmpty()) s.remove(DataComponents.CUSTOM_DATA);
        s.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return true;
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 40 != 7) return;
        if (sp.hasEffect(EnchRegistries.GEAR)) return;
        var inv = sp.getInventory();
        for (List<ItemStack> list : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack s : list) strip(s);
        }
        strip(sp.containerMenu.getCarried());
    }

    private static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide) return;
        DamageSource src = e.getSource();
        int def = Enchants.level(target, EnchRegistries.GEAR);
        if (def > 0) e.setAmount(e.getAmount() * (1F - 0.05F * def));
        if (!(src.getEntity() instanceof LivingEntity attacker) || attacker == target || AntiMagic.isMagic(src)) return;
        int lvl = Enchants.level(attacker, EnchRegistries.GEAR);
        if (lvl <= 0) return;
        if (src.getDirectEntity() == attacker) e.setAmount(e.getAmount() + 1.0F * lvl);
        else if (src.getDirectEntity() instanceof Projectile) e.setAmount(e.getAmount() + 0.75F * lvl);
    }

    private static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        int lvl = Enchants.level(e.getEntity(), EnchRegistries.GEAR);
        if (lvl > 0) e.setNewSpeed(e.getNewSpeed() * (1F + 0.25F * lvl));
    }

    private GearEnchant() {}
}
