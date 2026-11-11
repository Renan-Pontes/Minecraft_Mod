package com.renanpontes.isekaimahou.feature.bosses_b.item;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import org.joml.Vector3f;

/**
 * Coração do Crepúsculo (troféu do chefe final). Segurar o uso por 2 s absorve o coração UMA vez por jogador:
 * +60 de mana máximo e +10% de poder mágico permanentes, mana e cargas de ultimate cheias, muito XP.
 */
public class TwilightHeartItem extends Item {
    public static final String FLAG = "feat.twilight_heart";
    private static final ResourceLocation MANA_ID = IsekaiMahou.id("twilight_heart_mana");
    private static final ResourceLocation POWER_ID = IsekaiMahou.id("twilight_heart_power");

    public TwilightHeartItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 40;
    }

    /** Já absorveu? No cliente o anexo do jogador não é sincronizado: a cópia fica em {@link ClientMagic}. */
    private static boolean absorbed(Level level, Player player) {
        MagicData d = level.isClientSide ? ClientMagic.data() : MagicApi.get(player);
        return d != null && d.hasFlag(FLAG);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (absorbed(level, player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.twilight_heart.already")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide || remaining % 2 != 0) return;
        double a = remaining * 0.5;
        for (int i = 0; i < 2; i++) {
            double aa = a + i * Math.PI;
            int rgb = i == 0 ? 0xFFE27A : 0x8A4FC8;
            level.addParticle(new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 1.2F),
                    entity.getX() + Math.cos(aa) * 0.9, entity.getY() + 0.4 + (40 - remaining) * 0.04, entity.getZ() + Math.sin(aa) * 0.9, 0, 0, 0);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return stack;
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(FLAG)) return stack;
        d.setFlag(FLAG, true);
        applyBonuses(sp);
        MagicApi.giveXp(sp, 1500, "twilight_heart");
        MagicApi.addCounter(sp, FLAG, 1);
        for (Element e : Element.values()) d.setCharge(e, Progression.MAX_CHARGE);
        d.fillMana();
        d.markDirty();

        sl.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1, sp.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
        sl.sendParticles(ParticleTypes.SQUID_INK, sp.getX(), sp.getY() + 1, sp.getZ(), 40, 0.6, 1.0, 0.6, 0.1);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1, sp.getZ(), 50, 0.6, 1.0, 0.6, 0.4);
        sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
        sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
        Announce.send(sp, Component.translatable("announce.isekai_mahou.twilight_heart.absorbed", sp.getDisplayName()), 0xC06AA0,
                Announce.STYLE_SYSTEM);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.twilight_heart.used").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }

    /** +60 de mana máximo e +10% de poder mágico (idempotente). */
    private static void applyBonuses(ServerPlayer sp) {
        addPermanent(sp.getAttribute(CoreRegistries.MAX_MANA), MANA_ID, 60.0);
        addPermanent(sp.getAttribute(CoreRegistries.SPELL_POWER), POWER_ID, 0.1);
    }

    /**
     * Ao renascer (morte ou volta do End) o jogador é uma entidade nova e o vanilla só copia o valor BASE dos
     * atributos: os modificadores do coração somem, mas a marca continua. Reaplica aqui (e ao entrar, por segurança).
     */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && MagicApi.get(sp).hasFlag(FLAG)) applyBonuses(sp);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && MagicApi.get(sp).hasFlag(FLAG)) applyBonuses(sp);
    }

    private static void addPermanent(AttributeInstance attr, ResourceLocation id, double amount) {
        if (attr != null && !attr.hasModifier(id)) {
            attr.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.twilight_heart.desc").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.translatable("item.isekai_mahou.twilight_heart.use").withStyle(ChatFormatting.GRAY));
    }
}
