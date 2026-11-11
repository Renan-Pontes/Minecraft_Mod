package com.renanpontes.isekaimahou.feature.ritual_warlock.ritual;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Dádivas dos Patronos (nível 1..3 = nº de pactos selados com aquele Patrono, máx. 3) e o sacrifício
 * de corações. Os modificadores são transitórios e reaplicados ao entrar/renascer e a cada poucos segundos.
 * <ul>
 *   <li>Lorde das Brasas — Pele de Brasa: queima por menos tempo (atributo) e sofre menos dano de fogo (evento).</li>
 *   <li>Senhora do Vazio — Olhos do Vazio: visão noturna à noite; nível 2: Endermen não se irritam; nível 3: queda mais segura.</li>
 *   <li>Rei Afogado — Fôlego do Rei: mais fôlego, nado e mineração submersa.</li>
 * </ul>
 */
public final class PactBoons {
    private static final ResourceLocation EMBER_BURN = IsekaiMahou.id("warlock_boon_ember_burn");
    private static final ResourceLocation DROWNED_OXYGEN = IsekaiMahou.id("warlock_boon_drowned_oxygen");
    private static final ResourceLocation DROWNED_SWIM = IsekaiMahou.id("warlock_boon_drowned_swim");
    private static final ResourceLocation DROWNED_MINING = IsekaiMahou.id("warlock_boon_drowned_mining");
    private static final ResourceLocation VOID_FALL = IsekaiMahou.id("warlock_boon_void_fall");
    private static final ResourceLocation SACRIFICE = IsekaiMahou.id("warlock_sacrifice");

    public static int level(ServerPlayer player, PatronKind kind) {
        return Math.min(3, MagicApi.get(player).counter("warlock.pact." + kind.id));
    }

    /** Redução de dano de fogo da Pele de Brasa (0..0.36). */
    public static float fireReduction(ServerPlayer player) {
        return 0.12F * level(player, PatronKind.EMBER_LORD);
    }

    public static boolean endermenIgnore(ServerPlayer player) {
        return level(player, PatronKind.VOID_LADY) >= 2;
    }

    /** Reaplica todos os modificadores de atributo (dádivas + sacrifício). */
    public static void refresh(ServerPlayer player) {
        int ember = level(player, PatronKind.EMBER_LORD);
        set(player, Attributes.BURNING_TIME, EMBER_BURN, -0.2 * ember, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ember > 0);

        int drowned = level(player, PatronKind.DROWNED_KING);
        set(player, Attributes.OXYGEN_BONUS, DROWNED_OXYGEN, 1.0 * drowned, AttributeModifier.Operation.ADD_VALUE, drowned > 0);
        set(player, Attributes.WATER_MOVEMENT_EFFICIENCY, DROWNED_SWIM, 0.15 * drowned, AttributeModifier.Operation.ADD_VALUE, drowned > 0);
        set(player, Attributes.SUBMERGED_MINING_SPEED, DROWNED_MINING, 0.25 * drowned, AttributeModifier.Operation.ADD_VALUE, drowned > 0);

        int voidLvl = level(player, PatronKind.VOID_LADY);
        set(player, Attributes.SAFE_FALL_DISTANCE, VOID_FALL, 4.0, AttributeModifier.Operation.ADD_VALUE, voidLvl >= 3);

        applySacrifice(player, PactRitual.state(player));
    }

    /** Aplica (ou remove, se acabou) o modificador de corações cedidos. */
    public static void applySacrifice(ServerPlayer player, PactState s) {
        boolean on = s.sacrificeHearts > 0 && s.sacrificeUntil > PactRitual.now(player.serverLevel());
        set(player, Attributes.MAX_HEALTH, SACRIFICE, -2.0 * s.sacrificeHearts, AttributeModifier.Operation.ADD_VALUE, on);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Chamado a cada segundo: visão noturna da Senhora do Vazio e fim do sacrifício. */
    public static void tick(ServerPlayer player) {
        if (level(player, PatronKind.VOID_LADY) >= 1 && PactRitual.isRitualTime(player.level())) {
            MobEffectInstance cur = player.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || (cur.getDuration() < 260 && cur.isAmbient())) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
            }
        }
        PactState s = PactRitual.state(player);
        if (s.sacrificeHearts > 0 && s.sacrificeUntil <= PactRitual.now(player.serverLevel())) {
            s.sacrificeHearts = 0;
            s.sacrificeUntil = 0;
            applySacrifice(player, s);
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.warlock.sacrifice_ended").withStyle(ChatFormatting.GREEN));
        }
    }

    private static void set(ServerPlayer player, Holder<Attribute> attr, ResourceLocation id, double amount,
                            AttributeModifier.Operation op, boolean on) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        if (on) inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        else inst.removeModifier(id);
    }

    private PactBoons() {}
}
