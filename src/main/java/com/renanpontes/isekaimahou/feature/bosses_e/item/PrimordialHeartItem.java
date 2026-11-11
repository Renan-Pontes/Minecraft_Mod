package com.renanpontes.isekaimahou.feature.bosses_e.item;

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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import org.joml.Vector3f;

/**
 * Coração do Dragão Primordial (item lendário, troféu do chefe final). Segurar o uso por 3 s absorve o coração UMA
 * vez por jogador: desperta todos os elementos base que faltam, +16 de vida máxima, +240 de mana máxima e +15% de poder
 * mágico permanentes (números da inflação v1.0), mana e carga de ultimate cheias e muito XP. Depois continua um troféu.
 */
public class PrimordialHeartItem extends Item {
    public static final String FLAG = "feat.primordial_heart";
    private static final ResourceLocation HEALTH_ID = IsekaiMahou.id("primordial_heart_health");
    private static final ResourceLocation MANA_ID = IsekaiMahou.id("primordial_heart_mana");
    private static final ResourceLocation POWER_ID = IsekaiMahou.id("primordial_heart_power");
    private static final int[] COLORS = {0xFF6A2B, 0x3FA9FF, 0xA0743C, 0x9FF0D0, 0xFFE27A, 0x8A4FC8};

    public PrimordialHeartItem(Properties props) {
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
        return 60;
    }

    private static boolean absorbed(Level level, Player player) {
        MagicData d = level.isClientSide ? ClientMagic.data() : MagicApi.get(player);
        return d != null && d.hasFlag(FLAG);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (absorbed(level, player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.primordial_heart.already").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide || remaining % 2 != 0) return;
        double a = remaining * 0.45;
        for (int i = 0; i < 3; i++) {
            double aa = a + i * Math.PI * 2 / 3;
            int rgb = COLORS[(remaining / 4 + i) % COLORS.length];
            level.addParticle(new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 1.3F),
                    entity.getX() + Math.cos(aa) * 1.0, entity.getY() + 0.3 + (60 - remaining) * 0.03, entity.getZ() + Math.sin(aa) * 1.0, 0, 0, 0);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return stack;
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(FLAG)) return stack;
        d.setFlag(FLAG, true);
        for (Element e : Element.BASE) {
            if (!d.isAwakened(e)) MagicApi.awakenElement(sp, e);
        }
        applyBonuses(sp);
        sp.setHealth(sp.getMaxHealth());
        MagicApi.giveXp(sp, 15000, "primordial_heart");
        MagicApi.addCounter(sp, FLAG, 1);
        d.setCharge(Progression.MAX_CHARGE);
        d.fillMana();
        d.markDirty();

        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1, sp.getZ(), 80, 0.8, 1.2, 0.8, 0.5);
        sl.sendParticles(ParticleTypes.FLAME, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.8, 1.2, 0.8, 0.1);
        sl.sendParticles(ParticleTypes.SNOWFLAKE, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.8, 1.2, 0.8, 0.1);
        sl.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.8, 1.2, 0.8, 0.1);
        sl.sendParticles(ParticleTypes.SQUID_INK, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.8, 1.2, 0.8, 0.1);
        sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2F, 1.2F);
        sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
        Announce.send(sp, Component.translatable("announce.isekai_mahou.primordial_heart.absorbed", sp.getDisplayName()), 0xFFB347,
                Announce.STYLE_SYSTEM);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.primordial_heart.used").withStyle(ChatFormatting.GOLD), false);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        return stack;
    }

    /** +16 de vida, +240 de mana máximo e +15% de poder mágico (idempotente; troca o valor antigo da v0.9). */
    private static void applyBonuses(ServerPlayer sp) {
        addPermanent(sp.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID, 16.0);
        addPermanent(sp.getAttribute(CoreRegistries.MAX_MANA), MANA_ID, 240.0);
        addPermanent(sp.getAttribute(CoreRegistries.SPELL_POWER), POWER_ID, 0.15);
    }

    /** Ao renascer o vanilla só copia o valor base: os modificadores somem e a marca fica. Reaplica. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && MagicApi.get(sp).hasFlag(FLAG)) applyBonuses(sp);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && MagicApi.get(sp).hasFlag(FLAG)) applyBonuses(sp);
    }

    private static void addPermanent(AttributeInstance attr, ResourceLocation id, double amount) {
        if (attr == null) return;
        AttributeModifier cur = attr.getModifier(id);
        if (cur != null && cur.amount() == amount) return;
        attr.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.primordial_dragon_heart.desc").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.isekai_mahou.primordial_dragon_heart.bonus").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("item.isekai_mahou.primordial_dragon_heart.use").withStyle(ChatFormatting.GRAY));
    }
}
