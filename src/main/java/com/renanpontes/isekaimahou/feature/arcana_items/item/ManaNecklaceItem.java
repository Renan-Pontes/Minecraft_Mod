package com.renanpontes.isekaimahou.feature.arcana_items.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.arcana_items.ArcanaContent;
import com.renanpontes.isekaimahou.feature.mana_core.Overcharge;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * <b>Colar de Mana</b> — "uma forma de acumular mana em item tipo colar e gastar o sobressalente".
 * <ul>
 *   <li>VESTIDO (espaço de colar do Curios): com a barra cheia, guarda uma parte da regeneração que seria desperdiçada;
 *       quando a barra baixa, o colar a completa primeiro (é gasto ANTES da barra). Também absorve a Sobrecarga do Núcleo
 *       de Mana (o que alivia a Intoxicação).</li>
 *   <li>NA MÃO (botão direito): despeja um pouco da sua barra no colar — serve para carregar o colar de um amigo.</li>
 * </ul>
 * Não cria mana: tudo o que entra veio da sua regeneração, da sua barra ou da Sobrecarga, sempre com perda ou 1 para 1.
 */
public class ManaNecklaceItem extends Item implements ICurioItem {
    /** Cor da barrinha do item e das mensagens (azul de mana). */
    public static final int RGB = 0x5AA8FF;
    /** Depósito pela mão: fração da barra por clique e o mínimo que sempre fica na barra. */
    private static final float DEPOSIT_FRACTION = 0.25F, DEPOSIT_KEEP = 0.10F;
    /** Fração da capacidade que o colar absorve da Sobrecarga por tick. */
    private static final float OVERCHARGE_RATE = 0.01F;

    /** Resto fracionário por jogador + último tick em que um colar guardou regeneração (dois colares não dobram). */
    private static final Map<UUID, float[]> STATE = new ConcurrentHashMap<>();

    private final int tier;
    private final int capacity;
    private final float efficiency;

    public ManaNecklaceItem(Properties props, int tier, int capacity, float efficiency) {
        super(props);
        this.tier = tier;
        this.capacity = capacity;
        this.efficiency = efficiency;
    }

    public int tier() { return tier; }
    public int capacity() { return capacity; }

    public static int stored(ItemStack stack) {
        Integer v = stack.get(ArcanaContent.STORED_MANA.get());
        return v == null ? 0 : v;
    }

    public void setStored(ItemStack stack, int value) {
        int v = Mth.clamp(value, 0, capacity);
        if (v != stored(stack)) stack.set(ArcanaContent.STORED_MANA.get(), v);
    }

    public static void forget(UUID player) {
        STATE.remove(player);
    }

    // =====================================================================================
    //  Vestido: guarda o que sobraria e é gasto antes da barra
    // =====================================================================================
    @Override
    public void curioTick(SlotContext ctx, ItemStack stack) {
        if (!(ctx.entity() instanceof ServerPlayer sp) || sp.isSpectator() || sp.isDeadOrDying()) return;
        MagicData d = MagicApi.get(sp);
        int max = d.maxMana();
        if (max <= 0) return;
        float[] st = STATE.computeIfAbsent(sp.getUUID(), u -> new float[] {0F, -1F});
        float amount = stored(stack) + st[0];
        float before = amount;

        if (d.mana() < max - 0.01F) {
            // 1) a barra baixou: o colar completa primeiro
            if (amount > 0F) {
                float take = Math.min(amount, max - d.mana());
                d.addMana(take);
                amount -= take;
            }
        } else if (amount < capacity && st[1] != sp.tickCount && !sp.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) {
            // 2) barra cheia: uma parte da regeneração que seria perdida vai para o colar (mesma conta do núcleo)
            float regen = Progression.regenPerTick(d.level(), d.circle());
            var attr = sp.getAttribute(CoreRegistries.MANA_REGEN);
            if (attr != null) regen *= (float) attr.getValue();
            if (d.meditating) regen *= Progression.MEDITATION_REGEN_MULT;
            amount += Math.max(0F, regen) * efficiency;
            st[1] = sp.tickCount;
        }
        // 3) Sobrecarga do Núcleo de Mana: o colar bebe o excesso (1 para 1), aliviando a Intoxicação
        if (amount < capacity) {
            float over = Overcharge.get(sp);
            if (over > 0F) {
                float move = Math.min(over, Math.min(capacity - amount, Math.max(1F, capacity * OVERCHARGE_RATE)));
                Overcharge.set(sp, over - move);
                amount += move;
            }
        }
        if (amount == before) return;
        amount = Mth.clamp(amount, 0F, capacity);
        int whole = (int) amount;
        st[0] = amount - whole;
        setStored(stack, whole);
    }

    @Override
    public void onEquip(SlotContext ctx, ItemStack prevStack, ItemStack stack) {
        if (ctx.entity() instanceof ServerPlayer sp && sp.tickCount > 20 && !ItemStack.isSameItem(prevStack, stack)) {
            Hints.show(sp, "arcana_items.mana_necklace");
        }
    }

    @Override
    public void onUnequip(SlotContext ctx, ItemStack newStack, ItemStack stack) {
        if (ctx.entity() instanceof ServerPlayer sp && !ItemStack.isSameItem(newStack, stack)) forget(sp.getUUID());
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
    }

    // =====================================================================================
    //  Na mão: despeja mana da barra no colar
    // =====================================================================================
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        MagicData d = MagicApi.get(sp);
        int max = d.maxMana();
        int have = stored(stack);
        float spare = d.mana() - max * DEPOSIT_KEEP;
        int move = (int) Math.min(Math.min(spare, max * DEPOSIT_FRACTION), capacity - have);
        if (have >= capacity) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.arcana_items.necklace_full").withColor(RGB), true);
            return InteractionResultHolder.fail(stack);
        }
        if (move < 1 || !d.consumeMana(move)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.arcana_items.necklace_no_mana",
                    MagicApi.resourceName(sp)).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        setStored(stack, have + move);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.arcana_items.necklace_deposit", have + move, capacity)
                .withColor(RGB), true);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(), 12, 0.3, 0.3, 0.3, 0.4);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.4F);
        sp.getCooldowns().addCooldown(this, 10);
        Hints.show(sp, "arcana_items.mana_necklace");
        return InteractionResultHolder.consume(stack);
    }

    // =====================================================================================
    //  Visual: barrinha no item + tooltip
    // =====================================================================================
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stored(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * Math.min(1F, stored(stack) / (float) capacity));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return RGB;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.necklace.stored", stored(stack), capacity).withColor(RGB));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.necklace.desc", Math.round(efficiency * 100))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.necklace.hand").withStyle(ChatFormatting.DARK_GRAY));
    }
}
