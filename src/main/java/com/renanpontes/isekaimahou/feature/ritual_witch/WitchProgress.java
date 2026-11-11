package com.renanpontes.isekaimahou.feature.ritual_witch;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Conhecimento de receitas e a subida de rank da Bruxa (Poções de Revelação). */
public final class WitchProgress {
    /** Contador do rank do ritual da Bruxa (quantas Revelações já foram absorvidas). */
    public static final String RANK_COUNTER = "ritual_rank.witch";
    /** Contador geral de "subidas" de ritual (convenção entre módulos). */
    public static final String GLOBAL_RANK_COUNTER = "ritual_rank";
    /** XP de nível mágico por tier de Revelação (1..5). */
    private static final int[] REVELATION_XP = {150, 400, 900, 1800, 3500};
    private static final int REVELATION_RGB = 0xB57EDC;
    /** Peso da Revelação do próximo grau no sorteio da página rasgada (as outras receitas pesam 1). */
    private static final int REVELATION_PAGE_WEIGHT = 4;

    public static int rank(MagicData d) {
        return d.counter(RANK_COUNTER);
    }

    public static boolean knows(MagicData d, ResourceLocation recipe) {
        return d.hasFlag(WitchRecipe.knowledgeFlag(recipe));
    }

    /** Ensina uma receita (marcador em MagicData). Retorna false se já conhecia. */
    public static boolean learn(ServerPlayer player, ResourceLocation recipe, boolean announce) {
        MagicData d = MagicApi.get(player);
        String flag = WitchRecipe.knowledgeFlag(recipe);
        if (d.hasFlag(flag)) return false;
        d.setFlag(flag, true);
        if (announce) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.witch.recipe_learned",
                    WitchRecipe.displayName(recipe).withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY), false);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 1.0F, 0.9F);
        }
        return true;
    }

    /**
     * Receitas que uma página em branco pode revelar: as desconhecidas, sendo que Revelações só aparecem
     * até o próximo tier (para não estragar a progressão).
     */
    public static List<WitchRecipe> unknownForRandomPage(MagicData d) {
        int nextTier = rank(d) + 1;
        List<WitchRecipe> out = new ArrayList<>();
        for (WitchRecipe r : WitchRecipes.all()) {
            if (knows(d, r.id())) continue;
            int t = r.revelationTier();
            if (t > nextTier) continue;
            out.add(r);
        }
        return out;
    }

    /**
     * Sorteia uma receita desconhecida. A Revelação do próximo grau pesa mais: com ~12 elixires/maldições no sorteio,
     * um peso igual faria a progressão da Bruxa depender de achar uma dúzia de páginas.
     */
    public static WitchRecipe pickRandomUnknown(MagicData d, RandomSource random) {
        List<WitchRecipe> list = unknownForRandomPage(d);
        if (list.isEmpty()) return null;
        int nextTier = rank(d) + 1;
        int total = 0;
        for (WitchRecipe r : list) total += pageWeight(r, nextTier);
        int roll = random.nextInt(total);
        for (WitchRecipe r : list) {
            roll -= pageWeight(r, nextTier);
            if (roll < 0) return r;
        }
        return list.get(list.size() - 1);
    }

    private static int pageWeight(WitchRecipe r, int nextTier) {
        return r.revelationTier() == nextTier ? REVELATION_PAGE_WEIGHT : 1;
    }

    // =====================================================================================
    //  Revelação
    // =====================================================================================

    /** Aplica a Revelação de {@code tier} (já validado: tier == rank + 1). */
    public static void reveal(ServerPlayer player, int tier) {
        ServerLevel level = player.serverLevel();
        MagicApi.addCounter(player, RANK_COUNTER, 1);
        MagicApi.addCounter(player, GLOBAL_RANK_COUNTER, 1);

        Component title = Component.translatable("message.isekai_mahou.witch.revelation_title", roman(tier));
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.witch.revelation", roman(tier))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        Cutscenes.play(player, "ascension", player, title,
                Component.translatable("message.isekai_mahou.witch.revelation_subtitle"), REVELATION_RGB, 60, false);

        // Efeito visual e sonoro: espiral de bruxa subindo em volta do jogador
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 8;
            double y = player.getY() + i * 0.05;
            level.sendParticles(ParticleTypes.WITCH, player.getX() + Math.cos(a) * 1.1, y, player.getZ() + Math.sin(a) * 1.1,
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 80, 0.6, 0.8, 0.6, 1.0);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.5, 0.6, 0.5, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6F, 1.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, false));

        // XP grande
        MagicApi.giveXp(player, REVELATION_XP[Math.max(0, Math.min(REVELATION_XP.length - 1, tier - 1))], "ritual");

        // Despertar dos elementos de afinidade da Bruxa: Água no 1º rank, Sombra no 3º
        Element awaken = tier == 1 ? Element.WATER : tier == 3 ? Element.SHADOW : null;
        if (awaken != null && !MagicApi.awakenElement(player, awaken)) {
            MagicApi.giveElementXp(player, awaken, 60);
        }
        if (tier >= 2) {
            MagicApi.giveElementXp(player, Element.WATER, 20 * tier);
            MagicApi.giveElementXp(player, Element.SHADOW, 20 * tier);
        }

        // Glifos de Origem da Bruxa (Maldições) até este tier (cobre glifos que ficaram para trás)
        int taught = 0;
        MagicData d = MagicApi.get(player);
        for (Glyph g : GlyphRegistry.all()) {
            if (g.origin() != MagicPath.WITCH || g.tier() > tier || d.knowsGlyph(g.id())) continue;
            if (MagicApi.learnGlyph(player, g.id())) taught++;
        }
        if (taught == 0) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.witch.revelation_no_glyph").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Tier em algarismos romanos (I..V). */
    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(tier);
        };
    }

    private WitchProgress() {}
}
