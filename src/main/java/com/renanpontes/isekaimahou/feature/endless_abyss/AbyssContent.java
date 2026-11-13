package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Itens do Abismo Infinito (só vêm de lá — nada de receita para o que é raro):
 * <ul>
 *   <li>{@code abyss_shard} — Estilhaço do Abismo: recompensa dos andares de chefe; 8 + 1 Cristal de Mana viram um Núcleo;</li>
 *   <li>{@code abyss_core} — Núcleo do Abismo (raro, marcos de 10 andares): ao usar, enche a mana e a carga das ultimates
 *       (consumível, com recarga) — poder de UMA luta, nada permanente;</li>
 *   <li>{@code shadow_fragment} — Fragmento de Sombra (Lutas das Sombras): ao usar, "Véu Sombrio" por 3 min
 *       (força I, velocidade I, visão noturna).</li>
 * </ul>
 */
public final class AbyssContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    public static final DeferredItem<Item> ABYSS_SHARD = ITEMS.register("abyss_shard",
            () -> new DescribedItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> ABYSS_CORE = ITEMS.register("abyss_core",
            () -> new AbyssCoreItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));
    public static final DeferredItem<Item> SHADOW_FRAGMENT = ITEMS.register("shadow_fragment",
            () -> new ShadowFragmentItem(new Item.Properties().rarity(Rarity.RARE)));

    /** Item com uma linha de descrição ({@code <chave do item>.desc}). */
    static class DescribedItem extends Item {
        DescribedItem(Properties props) {
            super(props);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Núcleo do Abismo: enche mana e carga das ultimates. Recarga de 2 min para não virar "mana infinita". */
    static final class AbyssCoreItem extends DescribedItem {
        private static final int COOLDOWN = 20 * 120;

        AbyssCoreItem(Properties props) {
            super(props);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
                MagicData d = MagicApi.get(sp);
                d.fillMana();
                for (Element e : Element.values()) d.setCharge(e, Progression.MAX_CHARGE);
                d.markDirty();
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 50, 0.4, 0.8, 0.4, 0.08);
                sl.sendParticles(ParticleTypes.SCULK_SOUL, sp.getX(), sp.getY() + 1, sp.getZ(), 14, 0.4, 0.6, 0.4, 0.03);
                sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1F, 0.7F);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.endless_abyss.core_used")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                if (!sp.getAbilities().instabuild) stack.shrink(1);
                sp.getCooldowns().addCooldown(this, COOLDOWN);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
    }

    /** Fragmento de Sombra: Véu Sombrio (bônus pequeno e temporário). */
    static final class ShadowFragmentItem extends DescribedItem {
        private static final int DURATION = 20 * 180, COOLDOWN = 20 * 30;

        ShadowFragmentItem(Properties props) {
            super(props);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 0, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, 0, false, false, true));
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, sp.getX(), sp.getY() + 1, sp.getZ(), 24, 0.35, 0.7, 0.35, 0.02);
                sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 1.3F);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.endless_abyss.fragment_used")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                if (!sp.getAbilities().instabuild) stack.shrink(1);
                sp.getCooldowns().addCooldown(this, COOLDOWN);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
    }

    private AbyssContent() {}
}
