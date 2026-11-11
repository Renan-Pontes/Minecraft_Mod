package com.renanpontes.isekaimahou.feature.ritual_witch.item;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** Elixir da Bruxa: bebível, aplica vários efeitos de uma vez (e alguns têm um efeito especial). */
public class WitchElixirItem extends Item {
    /** Efeitos especiais além dos efeitos de status. */
    public enum Special { NONE, MANA, REVEAL }

    private static final float MANA_RESTORE = 0.6F;
    private static final double REVEAL_RADIUS = 24.0;

    private final Supplier<List<MobEffectInstance>> effects;
    private final Special special;

    public WitchElixirItem(Supplier<List<MobEffectInstance>> effects, Special special, Properties properties) {
        super(properties);
        this.effects = effects;
        this.special = special;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player p ? p : null;
        if (player instanceof ServerPlayer sp) CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
        if (level instanceof ServerLevel sl) {
            for (MobEffectInstance e : effects.get()) {
                if (e.getEffect().value().isInstantenous()) {
                    e.getEffect().value().applyInstantenousEffect(entity, entity, entity, e.getAmplifier(), 1.0);
                } else {
                    entity.addEffect(e);
                }
            }
            applySpecial(sl, entity);
            sl.sendParticles(ParticleTypes.WITCH, entity.getX(), entity.getY() + 1.0, entity.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
        }
        if (player == null || !player.hasInfiniteMaterials()) {
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (player != null && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            }
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    private void applySpecial(ServerLevel level, LivingEntity entity) {
        switch (special) {
            case MANA -> {
                if (entity instanceof ServerPlayer sp) {
                    MagicData d = MagicApi.get(sp);
                    d.addMana(d.maxMana() * MANA_RESTORE);
                    level.sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(), 30, 0.4, 0.5, 0.4, 0.8);
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
                }
            }
            case REVEAL -> {
                List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(REVEAL_RADIUS),
                        e -> e != entity && e.isAlive());
                for (LivingEntity e : near) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false), entity);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8F, 1.3F);
            }
            case NONE -> { }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        switch (special) {
            case MANA -> tooltip.add(Component.translatable("tooltip.isekai_mahou.witch_elixir.mana", Math.round(MANA_RESTORE * 100))
                    .withStyle(ChatFormatting.AQUA));
            case REVEAL -> tooltip.add(Component.translatable("tooltip.isekai_mahou.witch_elixir.reveal", (int) REVEAL_RADIUS)
                    .withStyle(ChatFormatting.AQUA));
            case NONE -> { }
        }
        PotionContents.addPotionTooltip(effects.get(), tooltip::add, 1.0F, ctx.tickRate());
    }
}
