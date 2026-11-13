package com.renanpontes.isekaimahou.feature.arcana_items;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.arcana_items.item.RecipeScrollItem;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Pergaminho de Receita — "é válido, mas dificulta fazer os pergaminhos pra não ser só reseta e farma de outro".
 * Gravar é caro: além do pergaminho em branco (materiais raros), custa níveis de experiência e boa parte da barra de
 * mana. Ler é uso único e exige conhecer TODOS os glifos da magia.
 */
public final class RecipeScrolls {
    /** Fração da barra (do máximo) gasta ao gravar. */
    public static final float MANA_FRACTION = 0.60F;
    private static final int LEVELS_BASE = 8, LEVELS_PER_GLYPH = 2, LEVELS_MAX = 30;
    private static final int RGB = 0xC9A0FF;

    /** Níveis de experiência para gravar esta magia (cresce com o número de glifos). */
    public static int levelCost(SpellRecipe recipe) {
        return Math.min(LEVELS_MAX, LEVELS_BASE + LEVELS_PER_GLYPH * recipe.glyphs().size());
    }

    public static int manaCost(MagicData d) {
        return Math.round(d.maxMana() * MANA_FRACTION);
    }

    /** Glifos da receita que o jogador ainda não conhece. */
    public static List<ResourceLocation> missing(MagicData d, SpellRecipe recipe) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : recipe.glyphs()) {
            if (!d.knowsGlyph(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    // =====================================================================================
    //  Gravar (pedido do cliente: índice da magia na biblioteca)
    // =====================================================================================
    static void inscribe(ServerPlayer sp, int index, boolean offhand) {
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack blank = sp.getItemInHand(hand);
        if (!blank.is(ArcanaContent.BLANK_RECIPE_SCROLL.get())) return;
        MagicData d = MagicApi.get(sp);
        if (index < 0 || index >= d.library().size()) return;
        SpellRecipe recipe = d.library().get(index);
        if (recipe.isEmpty()) return;
        // Só se grava o que se domina: ninguém vira "copiadora" de magias que não consegue conjurar
        if (!missing(d, recipe).isEmpty()) {
            fail(sp, Component.translatable("message.isekai_mahou.arcana_items.scroll_author_missing"));
            return;
        }
        boolean free = sp.getAbilities().instabuild;
        int levels = levelCost(recipe);
        int mana = manaCost(d);
        if (!free && sp.experienceLevel < levels) {
            fail(sp, Component.translatable("message.isekai_mahou.arcana_items.scroll_no_levels", levels));
            return;
        }
        if (!free && !d.consumeMana(mana)) {
            fail(sp, Component.translatable("message.isekai_mahou.arcana_items.scroll_no_mana", MagicApi.resourceName(sp), mana));
            return;
        }
        if (!free) sp.giveExperienceLevels(-levels);
        blank.consume(1, sp);
        Rewards.give(sp, RecipeScrollItem.of(recipe, sp.getGameProfile().getName()));
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.arcana_items.scroll_written", name(recipe)).withColor(RGB), true);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.4, sp.getZ(), 40, 0.4, 0.4, 0.4, 0.8);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 0.9F);
    }

    // =====================================================================================
    //  Ler (uso único). true = aprendeu (o item deve ser consumido)
    // =====================================================================================
    public static boolean read(ServerPlayer sp, SpellRecipe recipe) {
        MagicData d = MagicApi.get(sp);
        List<ResourceLocation> miss = missing(d, recipe);
        if (!miss.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < Math.min(3, miss.size()); i++) {
                if (i > 0) names.append(", ");
                Glyph g = GlyphRegistry.get(miss.get(i));
                names.append(g == null ? Component.literal(miss.get(i).getPath()) : g.displayName());
            }
            if (miss.size() > 3) names.append("...");
            fail(sp, Component.translatable("message.isekai_mahou.arcana_items.scroll_missing", miss.size(), names));
            return false;
        }
        for (SpellRecipe r : d.library()) {
            if (r.glyphs().equals(recipe.glyphs())) {
                fail(sp, Component.translatable("message.isekai_mahou.arcana_items.scroll_known"));
                return false;
            }
        }
        if (!d.addToLibrary(recipe)) {
            fail(sp, Component.translatable("message.isekai_mahou.library_full"));
            return false;
        }
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.arcana_items.scroll_learned", name(recipe)).withColor(RGB), true);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.4, sp.getZ(), 30, 0.4, 0.4, 0.4, 0.8);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    private static Component name(SpellRecipe r) {
        return r.name().isEmpty() ? Component.translatable("screen.isekai_mahou.arcana_items.inscribe.unnamed") : Component.literal(r.name());
    }

    private static void fail(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
        sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    private RecipeScrolls() {}
}
