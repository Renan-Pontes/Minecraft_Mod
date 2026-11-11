package com.renanpontes.isekaimahou.feature.legends_e;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.event.PendingCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.feature.legends_e.entity.SupremeCircleEntity;
import com.renanpontes.isekaimahou.feature.legends_e.glyph.SuperTierForm;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Regras da Magia de Nível Supremo fora do glifo: usos por dia (1 + Círculo/3, checados ANTES de pagar mana e carga),
 * a Ampulheta Arcana (conjuração instantânea), a trava de movimento do conjurador durante os 8 s e o comando
 * {@code /mahou supreme}.
 */
public final class SupremeCasting {
    /** Chave dos usos diários ({@code MagicApi.tryUseDaily}). */
    public static final String DAILY_KEY = "super_tier";
    private static final ResourceLocation SUPER_TIER = IsekaiMahou.id("super_tier");
    /** Modificador que prende o conjurador no lugar enquanto os círculos se formam. */
    private static final ResourceLocation LOCK_ID = IsekaiMahou.id("legends_e_supreme_channel");
    /** Conjuradores presos agora → id da entidade dos círculos. */
    private static final Map<UUID, Integer> LOCKED = new HashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SupremeCasting::onPreCast);
        NeoForge.EVENT_BUS.addListener(SupremeCasting::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SupremeCasting::onPending);
        MahouCommands.extend((root, buildCtx) -> root.then(Commands.literal("supreme")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> usesLine(p), false);
                    return 1;
                })
                .then(MahouCommands.op("reset").then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                    int n = 0;
                    for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                        int used = MagicApi.dailyUses(p, DAILY_KEY);
                        if (used > 0) MagicApi.addCounter(p, "daily." + DAILY_KEY, -used);
                        n++;
                    }
                    int count = n;
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.legends_e.reset", count), true);
                    return n;
                })))));
    }

    public static int maxUses(ServerPlayer p) {
        return 1 + MagicApi.get(p).circle() / 3;
    }

    private static Component usesLine(ServerPlayer p) {
        int max = maxUses(p);
        int used = Math.min(max, MagicApi.dailyUses(p, DAILY_KEY));
        return Component.translatable("message.isekai_mahou.legends_e.uses", max - used, max)
                .withStyle(max - used > 0 ? ChatFormatting.AQUA : ChatFormatting.RED);
    }

    /**
     * Antes de pagar (prioridade mais baixa: ninguém cancela depois): gasta um uso do dia. Sem usos, a conjuração
     * é cancelada com aviso claro — não gasta mana nem a carga de ultimate.
     */
    private static void onPreCast(SpellCastEvent.Pre e) {
        if (e.isCanceled() || !(e.getCaster() instanceof ServerPlayer sp)) return;
        if (e.getContext().stage() != 0 || !(e.getContext().spell().form() instanceof SuperTierForm)) return;
        int max = maxUses(sp);
        if (!MagicApi.tryUseDaily(sp, DAILY_KEY, max)) {
            e.setCanceled(true);
            noUses(sp, max);
            return;
        }
        if (!sp.getAbilities().instabuild) sp.displayClientMessage(usesLine(sp), true);
    }

    /**
     * Aviso cedo: a conjuração do jogador (tecla segurada) entrou na Pilha e é uma Magia de Nível Supremo sem usos
     * hoje → interrompe já no começo, em vez de deixar carregar à toa. O {@link #onPreCast} continua sendo a regra.
     */
    private static void onPending(PendingCastEvent e) {
        SpellStack.Pending p = e.getPending();
        if (!p.fromPlayerCast() || !(p.caster() instanceof ServerPlayer sp)) return;
        MagicData data = MagicApi.get(sp);
        SpellRecipe recipe = data.channelRecipe;
        if (recipe == null || !recipe.glyphs().contains(SUPER_TIER)) return;
        if (!sp.getAbilities().instabuild) {
            int max = maxUses(sp);
            if (MagicApi.dailyUses(sp, DAILY_KEY) >= max) {
                SpellCaster.interrupt(sp);
                noUses(sp, max);
                return;
            }
        }
        // Ampulheta Arcana: a conjuração inteira é instantânea — nem o tempo de conjuração pelo custo (o núcleo põe
        // ~1 s antes da forma) precisa ser segurado. A conjuração sai no próximo tick e a ampulheta é gasta na forma.
        if (data.channelSlot >= 0 && hasHourglass(sp)) data.channelTicks = 0;
    }

    /** Tem uma Ampulheta Arcana no inventário (sem gastar). */
    private static boolean hasHourglass(ServerPlayer p) {
        if (p.getOffhandItem().is(LeRegistry.ARCANE_HOURGLASS.get())) return true;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(LeRegistry.ARCANE_HOURGLASS.get())) return true;
        }
        return false;
    }

    private static void noUses(ServerPlayer sp, int max) {
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_e.no_uses", max)
                .withStyle(ChatFormatting.RED), true);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /**
     * Ampulheta Arcana: se houver uma no inventário (mão secundária primeiro), ela se desfaz e a magia sai na hora.
     * Criativo: vale sem gastar.
     */
    public static boolean consumeHourglass(ServerPlayer p) {
        ItemStack off = p.getOffhandItem();
        if (off.is(LeRegistry.ARCANE_HOURGLASS.get())) {
            if (!p.getAbilities().instabuild) off.shrink(1);
            return true;
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(LeRegistry.ARCANE_HOURGLASS.get())) {
                if (!p.getAbilities().instabuild) s.shrink(1);
                return true;
            }
        }
        return false;
    }

    // =====================================================================================
    //  Trava de movimento
    // =====================================================================================

    public static void lock(LivingEntity e, int circleId) {
        LOCKED.put(e.getUUID(), circleId);
        apply(e.getAttribute(Attributes.MOVEMENT_SPEED));
        apply(e.getAttribute(Attributes.JUMP_STRENGTH));
        apply(e.getAttribute(Attributes.FLYING_SPEED));
    }

    public static void unlock(LivingEntity e) {
        LOCKED.remove(e.getUUID());
        remove(e.getAttribute(Attributes.MOVEMENT_SPEED));
        remove(e.getAttribute(Attributes.JUMP_STRENGTH));
        remove(e.getAttribute(Attributes.FLYING_SPEED));
    }

    private static void apply(AttributeInstance a) {
        if (a == null || a.getModifier(LOCK_ID) != null) return;
        a.addTransientModifier(new AttributeModifier(LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void remove(AttributeInstance a) {
        if (a != null) a.removeModifier(LOCK_ID);
    }

    /** Este conjurador já está formando círculos (Eco/Repetir na forma não abre uma segunda Magia de Nível Supremo). */
    public static boolean isChanneling(LivingEntity e) {
        Integer id = LOCKED.get(e.getUUID());
        return id != null && e.level().getEntity(id) instanceof SupremeCircleEntity c && c.isAlive()
                && c.state() == SupremeCircleEntity.CHANNELING;
    }

    /** Segurança: jogador preso sem círculos ativos (entidade sumiu sem avisar) é solto. */
    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity().level().isClientSide || e.getEntity().tickCount % 20 != 0) return;
        AttributeInstance a = e.getEntity().getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null || a.getModifier(LOCK_ID) == null) return;
        Integer id = LOCKED.get(e.getEntity().getUUID());
        if (id == null || !(e.getEntity().level().getEntity(id) instanceof SupremeCircleEntity c) || !c.isAlive()) {
            unlock(e.getEntity());
        }
    }

    static void clear() {
        LOCKED.clear();
    }

    private SupremeCasting() {}
}
