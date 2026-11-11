package com.renanpontes.isekaimahou.feature.class_shinigami.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.Meditation;
import com.renanpontes.isekaimahou.feature.class_shinigami.PetalSwarm;
import com.renanpontes.isekaimahou.feature.class_shinigami.Release;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Zanpakutō — a espada espiritual ligada à alma do Shinigami (arma do kit, 4 graus para a Guilda/Provações).
 * <ul>
 *   <li><b>Agachado + usar</b>: medita (regeneração espiritual; ficar parado leva ao Mundo Interior);</li>
 *   <li><b>Usar</b> (com a Shikai liberada, rank III): grita o comando e libera a Shikai (ou sela de novo);</li>
 *   <li><b>Senbonzakura</b> (arquétipo Pétalas, liberada): SEGURAR usar controla o enxame de pétalas pela mira
 *   ({@link PetalSwarm}); um toque rápido sela; na Bankai, agachado + usar = Senkei;</li>
 *   <li><b>Ligada à alma</b>: fica com o dono ao morrer, volta voando se for jogada fora e retorna ao dono se outro
 *   jogador a pegar.</li>
 * </ul>
 * O estado visível (selada/Shikai/Bankai e o arquétipo) fica no {@code custom_data} da pilha, lido pelo modelo
 * (propriedades {@code isekai_mahou:release} e {@code isekai_mahou:archetype}).
 */
public class ZanpakutoItem extends SwordItem {
    /** Prefixo das dicas de estado ({@code .0} selada, {@code .1} Shikai, {@code .2} Bankai). */
    public static final String STATE_KEY = "tooltip.isekai_mahou.zanpakuto.state.";
    public static final String TAG_RELEASE = "isekai_mahou_release";
    public static final String TAG_ARCH = "isekai_mahou_arch";
    public static final String TAG_UNTIL = "isekai_mahou_until";
    public static final String TAG_SOUL = "isekai_mahou_soul";
    /** Ticks até uma zanpakutō jogada fora começar a voltar. */
    private static final int RETURN_DELAY = 40;

    private final int grade;

    public ZanpakutoItem(Tier tier, int grade, Properties props) {
        super(tier, props);
        this.grade = grade;
    }

    public int grade() {
        return grade;
    }

    // =====================================================================================
    //  Estado no custom_data
    // =====================================================================================
    private static CompoundTag peek(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.getUnsafe();
    }

    /** 0 = selada, 1 = Shikai, 2 = Bankai. */
    public static int release(ItemStack stack) {
        CompoundTag t = peek(stack);
        return t == null ? 0 : t.getInt(TAG_RELEASE);
    }

    /** Arquétipo gravado (1..5; 0 = nenhum). */
    public static int archetype(ItemStack stack) {
        CompoundTag t = peek(stack);
        return t == null ? 0 : t.getInt(TAG_ARCH);
    }

    /** Tick do jogo em que a liberação acaba (para o HUD). */
    public static long until(ItemStack stack) {
        CompoundTag t = peek(stack);
        return t == null ? 0L : t.getLong(TAG_UNTIL);
    }

    @Nullable
    public static UUID soul(ItemStack stack) {
        CompoundTag t = peek(stack);
        return t != null && t.hasUUID(TAG_SOUL) ? t.getUUID(TAG_SOUL) : null;
    }

    /** Grava o estado de liberação (só escreve se mudou). */
    public static void setRelease(ItemStack stack, int release, int archetype, long until) {
        if (release(stack) == release && archetype(stack) == archetype && until(stack) == until) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (release == 0) {
                tag.remove(TAG_RELEASE);
                tag.remove(TAG_UNTIL);
            } else {
                tag.putInt(TAG_RELEASE, release);
                tag.putLong(TAG_UNTIL, until);
            }
            if (archetype > 0) tag.putInt(TAG_ARCH, archetype);
            else tag.remove(TAG_ARCH);
        });
    }

    /** Liga a lâmina à alma de um jogador (só escreve se mudou). */
    public static void bind(ItemStack stack, Player owner) {
        UUID cur = soul(stack);
        if (owner.getUUID().equals(cur)) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(TAG_SOUL, owner.getUUID()));
    }

    // =====================================================================================
    //  Uso
    // =====================================================================================
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) {
            // cliente: com a Senbonzakura liberada, segurar usar controla as pétalas (o servidor confere de novo)
            if (!player.isShiftKeyDown() && release(stack) > 0 && archetype(stack) == Archetype.PETALS.counterValue()) {
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!Shinigami.isShinigami(sp)) {
            sp.displayClientMessage(Shinigami.msg("not_shinigami_blade").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.pass(stack);
        }
        if (sp.isShiftKeyDown()) {
            // Bankai das pétalas: agachado + usar = Senkei (o anel de mil espadas)
            if (Release.mode(sp) == Release.Mode.BANKAI && PetalSwarm.petals(sp)) {
                PetalSwarm.senkei(sp);
                return InteractionResultHolder.consume(stack);
            }
            Meditation.toggle(sp);
            return InteractionResultHolder.consume(stack);
        }
        // Senbonzakura liberada: segurar = o enxame segue a mira ("Chire"); um toque rápido sela (releaseUsing)
        if (PetalSwarm.petals(sp)) {
            sp.startUsingItem(hand);
            PetalSwarm.beginChannel(sp);
            return InteractionResultHolder.consume(stack);
        }
        Release.toggleShikai(sp, hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide || !(user instanceof ServerPlayer sp)) return;
        int used = getUseDuration(stack, user) - timeLeft;
        PetalSwarm.endChannel(sp);
        // toque rápido: sela a Shikai como antes (a Bankai sela sozinha no fim)
        if (used < 5 && Release.mode(sp) == Release.Mode.SHIKAI) Release.toggleShikai(sp, InteractionHand.MAIN_HAND);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level.isClientSide || !(holder instanceof ServerPlayer sp) || sp.tickCount % 20 != (slot & 15)) return;
        MagicData d = MagicApi.get(sp);
        UUID owner = soul(stack);
        if (d.path() == MagicPath.SHINIGAMI) {
            if (owner == null) bind(stack, sp);
            else if (!owner.equals(sp.getUUID()) && returnToOwner(sp, stack, owner)) return;
            Release.syncStack(sp, stack);
        } else if (owner != null && !owner.equals(sp.getUUID())) {
            returnToOwner(sp, stack, owner);
        } else if (release(stack) != 0) {
            setRelease(stack, 0, archetype(stack), 0L);
        }
    }

    /** A lâmina de outra alma volta ao dono (se ele estiver online). */
    private static boolean returnToOwner(ServerPlayer holder, ItemStack stack, UUID owner) {
        ServerPlayer target = holder.server.getPlayerList().getPlayer(owner);
        if (target == null || target == holder || !target.isAlive()) return false;
        ItemStack moving = stack.copy();
        stack.setCount(0);
        setRelease(moving, 0, archetype(moving), 0L);
        Rewards.give(target, moving);
        holder.displayClientMessage(Shinigami.msg("blade_returned_other").withStyle(ChatFormatting.GRAY), true);
        target.displayClientMessage(Shinigami.msg("blade_returned").withColor(Shinigami.RGB), true);
        ServerLevel sl = holder.serverLevel();
        ShinigamiFx.burst(sl, holder.position().add(0, 1, 0), 12, 0.3, ShinigamiFx.REIATSU);
        ShinigamiFx.sound(sl, holder.position(), SoundEvents.CHAIN_BREAK, 0.6F, 1.6F);
        return true;
    }

    /** Jogada fora: depois de 2 s voa de volta ao dono Shinigami (mesmo mundo, até 64 blocos). */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level().isClientSide || entity.getAge() < RETURN_DELAY) return false;
        UUID owner = soul(stack);
        if (owner == null || !(entity.level() instanceof ServerLevel sl)) return false;
        Player p = sl.getPlayerByUUID(owner);
        if (!(p instanceof ServerPlayer sp) || !sp.isAlive() || !Shinigami.isShinigami(sp) || sp.distanceToSqr(entity) > 64 * 64) return false;
        Vec3 to = sp.position().add(0, sp.getBbHeight() * 0.6, 0).subtract(entity.position());
        double dist = to.length();
        if (dist < 1.2) {
            ItemStack back = stack.copy();
            setRelease(back, 0, archetype(back), 0L);
            Rewards.give(sp, back);
            entity.discard();
            ShinigamiFx.sound(sl, sp.position(), SoundEvents.ITEM_PICKUP, 0.7F, 0.7F);
            sp.displayClientMessage(Shinigami.msg("blade_returned").withColor(Shinigami.RGB), true);
            return true;
        }
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.setDeltaMovement(to.normalize().scale(Math.min(1.2, 0.35 + dist * 0.05)));
        entity.move(net.minecraft.world.entity.MoverType.SELF, entity.getDeltaMovement());
        if (entity.tickCount % 2 == 0) sl.sendParticles(ShinigamiFx.REIATSU_SMALL, entity.getX(), entity.getY() + 0.2, entity.getZ(), 2, 0.1, 0.1, 0.1, 0);
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return release(stack) == 2 || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int rel = release(stack);
        Archetype a = Archetype.byIndex(archetype(stack) - 1);
        MagicData d = ClientMagic.data();
        if (a == null && d != null && d.path() == MagicPath.SHINIGAMI) a = Shinigami.archetype(d);
        tooltip.add(Component.translatable("tooltip.isekai_mahou.zanpakuto.grade", Shinigami.roman(grade)).withColor(Shinigami.RGB));
        tooltip.add(Component.translatable(STATE_KEY + rel).withStyle(rel == 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        if (a != null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.zanpakuto.spirit", a.spiritName(), a.displayName()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.zanpakuto.nameless").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.zanpakuto.use").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.zanpakuto.meditate").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.zanpakuto.soul").withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.zanpakuto.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
