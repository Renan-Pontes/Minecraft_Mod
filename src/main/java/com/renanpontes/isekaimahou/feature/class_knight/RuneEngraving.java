package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.weapon.MeleeSwings;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponStrikes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Lâminas Rúnicas: gravar uma magia na espada (forma {@code knight_engrave}) e soltar a runa a cada golpe principal.
 * <ul>
 *   <li><b>Gravar</b>: a receita INTEIRA que contém a forma é escrita na lâmina da mão principal (espaços conforme a
 *   arma, ver {@link KnightSwords}); a mesma receita só se renova; sem espaço, a runa mais antiga se apaga. Sem lâmina
 *   na mão, a magia vale só para o próximo golpe (como a forma Golpe).</li>
 *   <li><b>Golpe rúnico</b>: o golpe principal (barra de ataque ≥ 70%) contra um inimigo solta a PRÓXIMA runa (rodízio):
 *   paga uma fração do custo da magia (22–28%, Lendárias 15%, metade com Aura de Espada) e aplica os efeitos no alvo
 *   com os aumentos, ligações e estágios da receita. Intervalo mínimo de 8 ticks.</li>
 * </ul>
 */
public final class RuneEngraving {
    /** Intervalo mínimo entre golpes rúnicos (ticks). */
    private static final int PROC_GAP = 8;
    private static final Map<UUID, Long> LAST_PROC = new HashMap<>();
    private static final Map<UUID, Long> LAST_WARN = new HashMap<>();
    /**
     * Rodízio das runas por jogador (fica no servidor, não no item: mudar o item a cada golpe faria a espada
     * "re-equipar" na mão do jogador).
     */
    private static final Map<UUID, Integer> ROTATION = new HashMap<>();

    /** Grava a magia do contexto na lâmina do jogador (ou arma o próximo golpe, sem lâmina). */
    public static void engrave(ServerPlayer player, SpellContext ctx) {
        ItemStack stack = player.getMainHandItem();
        MagicData data = MagicApi.get(player);
        int slots = KnightSwords.runeSlots(stack, data);
        ServerLevel level = player.serverLevel();
        int rgb = ctx.color() & 0xFFFFFF;
        if (slots <= 0) {
            WeaponStrikes.arm(player, ctx, 1, 160);
            player.displayClientMessage(Component.translatable("message.isekai_mahou.class_knight.engrave_no_blade")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        SpellRecipe recipe = ctx.recipe();
        RuneData.Result r = KnightSwords.runes(stack).engrave(recipe, slots);
        KnightSwords.setRunes(stack, r.data());
        // círculo de runas no chão + runas subindo pela lâmina
        Vec3 feet = player.position();
        KnightFx.runeCircle(level, feet, 1.4, rgb, 28, player.tickCount * 0.1F);
        Vec3 hand = WeaponFx.handPos(player);
        for (int i = 0; i < 10; i++) {
            level.sendParticles(KnightFx.dust(i % 2 == 0 ? rgb : KnightFx.GOLD, 0.8F), hand.x, hand.y + i * 0.08, hand.z, 1, 0.05, 0.02, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y + 0.4, hand.z, 24, 0.2, 0.4, 0.2, 0.8);
        KnightFx.sound(level, hand, SoundEvents.ANVIL_USE, 0.5F, 1.6F);
        KnightFx.sound(level, hand, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.1F);
        Component name = recipe.name().isBlank() ? Component.translatable("tooltip.isekai_mahou.class_knight.rune_unnamed")
                : Component.literal(recipe.name());
        player.displayClientMessage(Component.translatable(r.replaced() ? "message.isekai_mahou.class_knight.engraved_replaced"
                : "message.isekai_mahou.class_knight.engraved", name, r.index() + 1, slots).withColor(rgb), true);
    }

    /** Este jogador soltou uma runa NESTE tick? (a Marca Rúnica não acumula em dobro). */
    public static boolean procThisTick(LivingEntity e) {
        Long t = LAST_PROC.get(e.getUUID());
        return t != null && t == e.level().getGameTime();
    }

    /** Golpe corpo a corpo principal de um jogador com lâmina gravada: solta a próxima runa. */
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player) || src.getDirectEntity() != player || player == target) return;
        if (!src.is(DamageTypes.PLAYER_ATTACK)) return;
        ItemStack stack = player.getMainHandItem();
        RuneData runes = KnightSwords.runes(stack);
        if (runes.isEmpty()) return;
        long now = level.getGameTime();
        float strength = MeleeSwings.mainHitStrength(player, target, now);
        if (strength < 0.7F) return;
        Long last = LAST_PROC.get(player.getUUID());
        if (last != null && now - last < PROC_GAP) return;

        MagicData data = MagicApi.get(player);
        boolean creative = player.getAbilities().instabuild;
        int turn = ROTATION.getOrDefault(player.getUUID(), 0);
        int index = Math.floorMod(turn, runes.size());
        SpellRecipe recipe = runes.runes().get(index);
        SpellParser.Result parsed = SpellParser.parse(recipe, creative ? null : data);
        // gira para a próxima runa mesmo se esta falhar (o jogador não fica preso numa runa inválida)
        ROTATION.put(player.getUUID(), turn + 1);
        if (!parsed.ok()) {
            warn(player, now, Component.translatable("message.isekai_mahou.class_knight.rune_invalid", runeName(recipe)));
            return;
        }
        SpellContext probe = new SpellContext(level, player, parsed.spell().primary(), recipe, parsed.spell());
        if (probe.isAlly(target)) return;
        float frac = KnightSwords.procCost(stack);
        if (player.hasEffect(KnightRegistries.BLADE_AURA)) frac *= 0.5F;
        float cost = Math.max(1F, parsed.spell().manaCost() * frac);
        if (!creative && !data.consumeMana(cost)) {
            warn(player, now, MagicApi.noResourceMessage(player).copy().withStyle(ChatFormatting.RED));
            return;
        }
        LAST_PROC.put(player.getUUID(), now);
        KnightCounters.addProc(player);

        SpellContext ctx = probe;
        ctx.markPlayerCast();
        ctx.setPowerMult(KnightSwords.procPower(stack));
        int rgb = ctx.color() & 0xFFFFFF;
        Vec3 at = KnightFx.center(target);
        Vec3 dir = at.subtract(player.getEyePosition());
        WeaponFx.slash(level, player, at, ctx.element(), rgb);
        KnightFx.runeCircle(level, at.subtract(0, target.getBbHeight() * 0.45, 0), 0.8, rgb, 14, now * 0.2F);
        KnightFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.5F + level.random.nextFloat() * 0.2F);
        ElementFx.castSound(level, player, ctx.element());
        // próximo tick: depois do dano do golpe (sem a invencibilidade dele engolir o dano da runa)
        SpellScheduler.schedule(1, () -> {
            if (target.level() != level || !player.isAlive()) return;
            ctx.impact(KnightFx.center(target), dir, target);
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                ctx.resolveOnEntity(target, player);
            }
        });
        if (runes.size() > 1) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.class_knight.rune_proc", runeName(recipe),
                    index + 1, runes.size()).withColor(rgb), true);
        }
    }

    private static Component runeName(SpellRecipe r) {
        return r.name().isBlank() ? Component.translatable("tooltip.isekai_mahou.class_knight.rune_unnamed") : Component.literal(r.name());
    }

    /** Aviso na barra de ação, no máximo a cada 3 s. */
    private static void warn(ServerPlayer p, long now, Component msg) {
        Long last = LAST_WARN.get(p.getUUID());
        if (last != null && now - last < 60) return;
        LAST_WARN.put(p.getUUID(), now);
        p.displayClientMessage(msg, true);
        KnightFx.sound(p.serverLevel(), p.position(), SoundEvents.FIRE_EXTINGUISH, 0.4F, Mth.clamp(1.6F, 0.5F, 2F));
    }

    public static void forget(UUID id) {
        ROTATION.remove(id);
        LAST_PROC.remove(id);
        LAST_WARN.remove(id);
    }

    public static void clear() {
        ROTATION.clear();
        LAST_PROC.clear();
        LAST_WARN.clear();
    }

    private RuneEngraving() {}
}
