package com.renanpontes.isekaimahou.core.item;

import java.util.List;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.classes.GlyphLearning;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Pergaminho de Glifo: clique direito para aprender o glifo guardado. Achado em ruínas, loja da Guilda etc.
 * v1.0: aprender GASTA 1 essência do elemento do glifo (Arcano = arcana; fusão = de um dos pais) e glifos de origem de
 * OUTRA classe não saem de pergaminho — só com uma Lição ({@link TechniqueLessonItem}). Regras em {@link GlyphLearning}.
 */
public class GlyphScrollItem extends Item {
    /** Essências gastas por pergaminho. */
    public static final int ESSENCE_COST = 1;

    public GlyphScrollItem(Properties properties) {
        super(properties);
    }

    /** Cria um pergaminho para o glifo informado. */
    public static ItemStack of(ResourceLocation glyph) {
        ItemStack stack = new ItemStack(CoreRegistries.GLYPH_SCROLL.get());
        stack.set(CoreRegistries.GLYPH.get(), glyph);
        return stack;
    }

    public static void addAllToTab(CreativeModeTab.Output out) {
        for (Glyph g : GlyphRegistry.all()) out.accept(of(g.id()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation id = stack.get(CoreRegistries.GLYPH.get());
        Glyph g = id == null ? null : GlyphRegistry.get(id);
        if (g == null) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.fail(stack);

        if (MagicApi.get(sp).knowsGlyph(id)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.glyph_known").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean free = GlyphLearning.free(sp);
        MagicPath path = MagicApi.get(sp).path();
        if (!free && GlyphLearning.isForeignOrigin(g, path)) {
            GlyphLearning.deny(sp, Component.translatable("message.isekai_mahou.classes.scroll_foreign_origin",
                    g.displayName(), g.origin().displayName()));
            return InteractionResultHolder.fail(stack);
        }
        Element pay = free ? null : GlyphLearning.pickEssence(sp, g, ESSENCE_COST);
        if (!free && pay == null) {
            GlyphLearning.deny(sp, GlyphLearning.missingEssence(sp, g, ESSENCE_COST));
            return InteractionResultHolder.fail(stack);
        }
        if (!MagicApi.learnGlyph(sp, id)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.glyph_known").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (pay != null) GlyphLearning.consume(sp, pay, ESSENCE_COST);
        stack.consume(1, player);
        int rgb = (pay != null ? pay : g.element()).color;
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(), 30, 0.4, 0.6, 0.4, 0.6);
        sp.serverLevel().sendParticles(new DustParticleOptions(
                new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 1.2F),
                sp.getX(), sp.getY() + 1.0, sp.getZ(), 18, 0.35, 0.5, 0.35, 0.02);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = stack.get(CoreRegistries.GLYPH.get());
        Glyph g = id == null ? null : GlyphRegistry.get(id);
        return g == null ? super.getName(stack)
                : Component.translatable("item.isekai_mahou.glyph_scroll.named", g.displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = stack.get(CoreRegistries.GLYPH.get());
        Glyph g = id == null ? null : GlyphRegistry.get(id);
        if (g == null) return;
        tooltip.add(Component.translatable("glyph_kind.isekai_mahou." + g.kind().name().toLowerCase()).withStyle(ChatFormatting.GOLD));
        tooltip.add(g.element().displayName());
        tooltip.add(g.description().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.classes.scroll_cost", GlyphLearning.costText(g, ESSENCE_COST))
                .withStyle(ChatFormatting.DARK_AQUA));
        if (g.origin() != null && g.origin() != MagicPath.NONE) {
            // Só o cliente sabe a classe de quem olha; no servidor (ex.: busca de receitas) mostra a regra geral
            MagicPath viewer = ctx.level() != null && ctx.level().isClientSide ? ClientMagic.data().path() : MagicPath.NONE;
            tooltip.add(Component.translatable(viewer == g.origin()
                    ? "tooltip.isekai_mahou.classes.origin_own" : "tooltip.isekai_mahou.classes.origin_foreign",
                    g.origin().displayName()).withStyle(viewer == g.origin() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
