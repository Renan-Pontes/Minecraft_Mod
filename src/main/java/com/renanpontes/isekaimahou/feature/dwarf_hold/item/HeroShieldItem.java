package com.renanpontes.isekaimahou.feature.dwarf_hold.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.dwarf_hold.DwarfRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * Escudo do Herói (inspirado no Herói do Escudo): escudo de Mithril que DEVOLVE parte de todo golpe corpo a corpo
 * bloqueado ({@link #REFLECT}). Quando o portador está ferido (≤ {@link #RAGE_HEALTH} da vida), o escudo desperta o
 * <b>Escudo da Ira</b>: devolve mais ({@link #RAGE_REFLECT}) e incendeia quem bate — custando {@link #RAGE_MANA} de mana
 * a cada golpe (sem mana, volta ao modo normal).
 */
public class HeroShieldItem extends ShieldItem {
    public static final int DURABILITY = 1344;
    public static final float REFLECT = 0.3F;
    public static final float RAGE_REFLECT = 0.6F;
    public static final float RAGE_HEALTH = 0.5F;
    public static final float RAGE_MANA = 4F;
    private static final ResourceLocation ARMOR_ID = IsekaiMahou.id("hero_shield_armor");
    private static final ResourceLocation TOUGH_ID = IsekaiMahou.id("hero_shield_toughness");
    private static final String TIP = "tooltip.isekai_mahou.dwarf_hold.hero_shield.";

    public HeroShieldItem(Properties props) {
        super(props.stacksTo(1).durability(DURABILITY).fireResistant().attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, 2.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGH_ID, 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.OFFHAND)
                .build()));
    }

    /** O portador está no modo Escudo da Ira? (vida baixa) */
    public static boolean enraged(LivingEntity holder) {
        return holder.getHealth() <= holder.getMaxHealth() * RAGE_HEALTH;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId(); // não muda de nome com estandarte
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(DwarfRegistry.MITHRIL_INGOT.get());
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 18;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        // Brasas em volta enquanto bloqueia furioso
        if (level.isClientSide && holder instanceof LivingEntity le && le.isBlocking() && le.getUseItem() == stack && enraged(le)
                && le.tickCount % 3 == 0) {
            double a = le.getRandom().nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.FLAME, le.getX() + Math.cos(a) * 0.7, le.getY() + 0.4 + le.getRandom().nextDouble(),
                    le.getZ() + Math.sin(a) * 0.7, 0, 0.03, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(TIP + "desc").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(TIP + "reflect", Math.round(REFLECT * 100)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(TIP + "rage", Math.round(RAGE_HEALTH * 100)).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable(TIP + "rage.desc", Math.round(RAGE_REFLECT * 100), (int) RAGE_MANA)
                .withStyle(ChatFormatting.GRAY));
    }

    // =====================================================================================
    //  Evento: golpe bloqueado
    // =====================================================================================
    public static void onShieldBlock(LivingShieldBlockEvent e) {
        if (!e.getBlocked() || !(e.getEntity() instanceof Player p) || p instanceof FakePlayer) return;
        if (!(p.getUseItem().getItem() instanceof HeroShieldItem)) return;
        DamageSource src = e.getDamageSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || attacker == p || src.getDirectEntity() != attacker) return;
        // espinhos não refletem de novo (dois Escudos do Herói em PvP fariam um pingue-pongue infinito)
        if (src.is(DamageTypes.THORNS) || src.is(DamageTypeTags.IS_PROJECTILE) || src.is(DamageTypeTags.IS_EXPLOSION)
                || !(p.level() instanceof ServerLevel sl)) return;
        float blocked = e.getBlockedDamage();
        if (blocked <= 0) return;
        boolean rage = enraged(p) && p instanceof ServerPlayer sp && spendMana(sp);
        float back = Mth.clamp(blocked * (rage ? RAGE_REFLECT : REFLECT), 1F, rage ? 20F : 12F);
        attacker.hurt(sl.damageSources().thorns(p), back);
        if (rage) {
            attacker.igniteForSeconds(5F);
            sl.sendParticles(ParticleTypes.FLAME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
                    24, 0.3, 0.5, 0.3, 0.04);
            sl.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.3, 0.3, 0);
            sl.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 0.7F);
        } else {
            sl.sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
                    10, 0.3, 0.4, 0.3, 0.2);
            sl.playSound(null, p.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.35F, 1.6F);
        }
    }

    private static boolean spendMana(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        return d.consumeMana(RAGE_MANA);
    }
}
