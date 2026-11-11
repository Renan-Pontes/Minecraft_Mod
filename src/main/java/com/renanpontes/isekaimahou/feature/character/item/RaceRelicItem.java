package com.renanpontes.isekaimahou.feature.character.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.feature.character.RaceGlyphs;
import com.renanpontes.isekaimahou.glyph.Glyph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Relíquia Racial — a ÚNICA forma de um jogador de OUTRA raça aprender as skills raciais (regra do jogador).
 * <p>
 * Clique direito: ensina a próxima skill daquela raça (na ordem de despertar) que o jogador ainda não conhece, se ele
 * tiver o nível pedido pela skill; a relíquia é consumida e a skill fica marcada ({@code race_relic.<glifo>}) para
 * nunca ser esquecida (nem ao renascer). Quem já é da raça: "você já carrega esta linhagem" — agachado, a relíquia
 * vira XP.
 */
public class RaceRelicItem extends Item {
    /** XP dado ao converter uma relíquia da própria raça (agachado + clique direito). */
    public static final int SAME_RACE_XP = 250;

    private final Race race;

    public RaceRelicItem(Race race, Properties props) {
        super(props);
        this.race = race;
    }

    public Race race() {
        return race;
    }

    /** Id do item da relíquia de cada raça. */
    public static String itemId(Race r) {
        return switch (r) {
            case HUMAN -> "relic_human_emblem";
            case ELF -> "relic_elven_tear";
            case DWARF -> "relic_dwarven_rune";
            case HALF_ORC -> "relic_orc_fang";
            case DEMON -> "relic_demon_horn";
            case DRAGONBORN -> "relic_dragon_scale";
            case BEASTFOLK -> "relic_beast_claw";
            case ARACHNID -> "relic_arachne_silk";
            case PHANTOM -> "relic_phantom_ectoplasm";
            case ANGEL -> "relic_seraph_feather";
            case SLIME -> "relic_slime_core";
        };
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp) || sp instanceof FakePlayer) return InteractionResultHolder.pass(stack);
        CharacterData d = CharacterContent.data(sp);
        MagicData md = MagicApi.get(sp);
        int rgb = race.rgb;

        if (!d.created()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.not_created").withColor(0xE07A7A), true);
            return InteractionResultHolder.fail(stack);
        }
        // Mesma raça: as skills já vêm com o sangue
        if (d.activeRace() == race) {
            if (sp.isShiftKeyDown()) {
                consume(sp, stack);
                MagicApi.giveXp(sp, SAME_RACE_XP, "race_relic");
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.relic_xp", SAME_RACE_XP)
                        .withColor(rgb), true);
                RaceFx.dustBurst(sp.serverLevel(), sp.position().add(0, 1.0, 0), rgb, 24, 0.5);
                sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS, 0.8F, 0.9F);
                sp.getCooldowns().addCooldown(this, 10);
                return InteractionResultHolder.consume(stack);
            }
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.relic_same_race", race.displayName())
                    .withColor(0xD8C9A0), true);
            return InteractionResultHolder.fail(stack);
        }
        RaceGlyphs.Skill next = RaceGlyphs.nextUnknown(md, race);
        if (next == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.relic_all_known", race.displayName())
                    .withColor(0xD8C9A0), true);
            return InteractionResultHolder.fail(stack);
        }
        Glyph g = next.glyph();
        if (md.level() < next.level()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.relic_level",
                    g == null ? Component.literal(next.path()) : g.displayName().withColor(rgb), next.level()).withColor(0xE07A7A), true);
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_HIT,
                    SoundSource.PLAYERS, 0.8F, 0.6F);
            return InteractionResultHolder.fail(stack);
        }
        consume(sp, stack);
        RaceGlyphs.teachByRelic(sp, next);
        sp.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1.0, sp.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
        sp.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.consume(stack);
    }

    private static void consume(ServerPlayer sp, ItemStack stack) {
        if (!sp.getAbilities().instabuild) stack.shrink(1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String base = "item.isekai_mahou." + itemId(race);
        tooltip.add(Component.translatable(base + ".desc").withStyle(ChatFormatting.ITALIC).withColor(0xA89EC0));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.character.relic_race", race.displayName()).withColor(0xFFD86B));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.character.relic_teaches").withColor(0xC8C0D8));
        for (RaceGlyphs.Skill s : RaceGlyphs.of(race)) {
            Glyph g = s.glyph();
            Component name = g == null ? Component.literal(s.path()) : g.displayName().withColor(race.rgb);
            tooltip.add(Component.literal("  • ").withColor(0x8A82A0).append(name)
                    .append(Component.translatable("tooltip.isekai_mahou.character.relic_skill_level", s.level()).withColor(0x8A82A0)));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.character.relic_use").withColor(0x8CE07A));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.character.relic_forever").withColor(0x9AD0FF));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.character.relic_same", SAME_RACE_XP).withColor(0x8A82A0));
    }
}
