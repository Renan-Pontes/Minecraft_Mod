package com.renanpontes.isekaimahou.feature.class_steelball.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_steelball.SbEvents;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbLoveTrain;
import com.renanpontes.isekaimahou.feature.class_steelball.SbPart;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Parte do Cadáver Santo. Botão direito: a parte se funde ao corpo (uma vez por parte por personagem) e dá o seu
 * poder passivo. Qualquer classe pode absorver; para o Steel Ball, as partes também liberam as Lições do Mestre (e com
 * elas os Atos). Parte repetida não é absorvida — dá para trocar com amigos.
 */
public class CorpsePartItem extends Item {
    private final SbPart part;

    public CorpsePartItem(SbPart part, Properties props) {
        super(props);
        this.part = part;
    }

    public SbPart part() {
        return part;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MagicData d = level.isClientSide ? ClientMagic.data() : MagicApi.get(player);
        if (part.owned(d)) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.part_owned", part.displayName())
                        .withStyle(ChatFormatting.GRAY), true);
                SbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.VILLAGER_NO, 0.5F, 1.2F);
            }
            return InteractionResultHolder.fail(stack);
        }
        // O Coração só se funde a um corpo que já carrega as outras 8 partes
        if (part.isFinal() && SbPart.countCommon(d) < SbPart.values().length - 1) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.heart_rejects",
                        SbPart.countCommon(d), SbPart.values().length - 1).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                SbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.6F);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer sp) absorb(sp, part);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Funde a parte ao corpo (servidor): flag, contador, atributos, festa. */
    public static void absorb(ServerPlayer sp, SbPart part) {
        MagicData d = MagicApi.get(sp);
        if (part.owned(d)) return;
        d.setFlag(part.flag(), true);
        int n = MagicApi.addCounter(sp, SbPart.COUNTER, 1);
        SbEvents.syncParts(sp);
        ServerLevel sl = sp.serverLevel();
        Vec3 p = sp.position();
        SbFx.sound(sl, p, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
        SbFx.sound(sl, p, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.0F);
        SbFx.spiral(sl, p, part.rgb, 1.0, 2.2, 36, 0F);
        SbFx.spiral(sl, p, SbFx.GOLD, 0.8, 2.2, 28, 3.1F);
        sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1.1, p.z, 30, 0.3, 0.6, 0.3, 0.06);
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.steelball.part", part.displayName()),
                part.bonus(), new ItemStack(com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry.PARTS.get(part).get()), part.rgb);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.part_absorbed", part.displayName(), n,
                SbPart.values().length).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(part.bonus().withStyle(ChatFormatting.YELLOW));
        if (d.path() == MagicPath.STEELBALL) {
            SbProgress.Rank next = SbProgress.next(d);
            if (next != null && n >= next.parts()) {
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.part_lesson_ready", next.title())
                        .withStyle(ChatFormatting.AQUA));
            }
            com.renanpontes.isekaimahou.feature.class_steelball.SbTechniques.check(sp);
        }
        if (part.isFinal() && !SbPart.complete(d)) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.heart_incomplete")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        // O Santo inteiro (o Coração por último): desperta o LOVE TRAIN
        SbLoveTrain.check(sp);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(part.flavor().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(part.bonus().withStyle(ChatFormatting.BLUE));
        MagicData d = ClientMagic.data();
        if (part.isFinal()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.part.final").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.part.love_train").withStyle(ChatFormatting.YELLOW));
        }
        if (part.owned(d)) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.part.owned").withStyle(ChatFormatting.DARK_GREEN));
        } else if (part.isFinal() && SbPart.countCommon(d) < SbPart.values().length - 1) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.part.final_use", SbPart.countCommon(d),
                    SbPart.values().length - 1).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.part.use").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.part.count", SbPart.count(d), SbPart.values().length)
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
