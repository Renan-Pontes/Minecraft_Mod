package com.renanpontes.isekaimahou.feature.class_smith.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Martelo do Ferreiro (arma do kit {@code smith_hammer} e os graus {@code smith_hammer_t1..t4}). Arma pesada: cada
 * golpe tem chance de ATORDOAR e solta fagulhas. Clique direito com um item gasto na outra mão faz o "Reparo de Campo"
 * (só o Ferreiro): recupera 12% (+3% por grau) da durabilidade gastando 8% do Vigor. Segurando o martelo, os glifos de
 * forja mexem na Lâmina Forjada da outra mão.
 */
public class SmithHammerItem extends Item {
    public static final ResourceLocation KNOCKBACK_ID = IsekaiMahou.id("smith_hammer_knockback");
    /** Dano (modificador) por grau: kit, I, II, III, IV. */
    public static final float[] DAMAGE = {5.0F, 5.5F, 6.5F, 7.5F, 8.5F};
    public static final int[] DURABILITY = {500, 700, 1200, 2000, 3000};
    private static final int REPAIR_COOLDOWN = 40;

    private final int tier;

    public SmithHammerItem(int tier, Properties props) {
        super(props.durability(DURABILITY[tier]).attributes(attributes(tier)));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    private static ItemAttributeModifiers attributes(int tier) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DAMAGE[tier],
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.9F,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(KNOCKBACK_ID, 0.4F + 0.1F * tier,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** Chance de atordoar por golpe. */
    public float stunChance() {
        return 0.12F + 0.03F * tier;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel sl) {
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
            sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 2 + tier, 0.2, 0.2, 0.2, 0);
            sl.sendParticles(SmithFx.spark(0xFFB040), c.x, c.y, c.z, 8 + 2 * tier, 0.3, 0.3, 0.3, 0.05);
            SmithFx.sound(sl, c, SoundEvents.ANVIL_PLACE, 0.25F, 1.6F + 0.1F * sl.random.nextFloat());
            if (sl.random.nextFloat() < stunChance()) {
                target.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 20 + 4 * tier, 0), attacker);
                sl.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.4, c.z, 10, 0.3, 0.2, 0.3, 0.1);
            }
        }
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack hammer = player.getItemInHand(hand);
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack target = player.getItemInHand(other);
        if (target.isEmpty() || !target.isDamageableItem() || !target.isDamaged()) return InteractionResultHolder.pass(hammer);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(hammer);
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.SMITH) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_smith.repair_smith_only")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(hammer);
        }
        float cost = d.maxMana() * 0.08F;
        if (!sp.isCreative() && !d.consumeMana(cost)) {
            sp.displayClientMessage(MagicApi.noResourceMessage(sp), true);
            return InteractionResultHolder.fail(hammer);
        }
        int amount = Math.max(1, Math.round(target.getMaxDamage() * (0.12F + 0.03F * tier)));
        target.setDamageValue(Math.max(0, target.getDamageValue() - amount));
        d.markDirty();
        sp.getCooldowns().addCooldown(this, REPAIR_COOLDOWN);
        sp.swing(hand, true);
        ServerLevel sl = sp.serverLevel();
        Vec3 c = sp.position().add(sp.getLookAngle().scale(0.6)).add(0, 1.1, 0);
        sl.sendParticles(SmithFx.spark(0xFFC060), c.x, c.y, c.z, 18, 0.25, 0.2, 0.25, 0.08);
        SmithFx.sound(sl, c, SoundEvents.ANVIL_USE, 0.7F, 1.3F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_smith.repaired", target.getHoverName(), amount)
                .withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.consume(hammer);
    }

    /** Ferreiro: o próprio martelo gasta metade (bônus de classe "o equipamento dura muito mais"). */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @javax.annotation.Nullable T entity,
                                                   java.util.function.Consumer<Item> onBroken) {
        if (entity instanceof ServerPlayer sp && MagicApi.get(sp).path() == MagicPath.SMITH && amount > 0) {
            int kept = 0;
            for (int i = 0; i < amount; i++) if (sp.getRandom().nextBoolean()) kept++;
            return amount - kept;
        }
        return amount;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(net.minecraft.world.item.Items.IRON_INGOT) || super.isValidRepairItem(stack, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.smith_hammer.flavor" + (tier == 0 ? "" : "_t" + tier))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.hammer.stun", Math.round(stunChance() * 100))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.hammer.repair", 12 + 3 * tier)
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_smith.hammer.forge").withStyle(ChatFormatting.DARK_GRAY));
    }
}
