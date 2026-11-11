package com.renanpontes.isekaimahou.core.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.classes.ClassesRegistries;
import com.renanpontes.isekaimahou.core.classes.GlyphLearning;
import com.renanpontes.isekaimahou.core.classes.Lessons;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Lição ({@code technique_lesson}, v1.0): o jeito de um jogador ENSINAR um glifo de origem da classe dele.
 * <ul>
 *   <li>Em branco (sem o componente {@code isekai_mahou:lesson}): usar abre a tela para escolher o glifo a escrever.</li>
 *   <li>Escrita: quem usa estuda e aprende o glifo (Círculo e essências; multiclasse paga mais) — ver {@link Lessons}.</li>
 * </ul>
 */
public class TechniqueLessonItem extends Item {
    public TechniqueLessonItem(Properties properties) {
        super(properties);
    }

    /** Lição escrita? */
    public static boolean isWritten(ItemStack stack) {
        return stack.has(ClassesRegistries.LESSON.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);
        LessonData data = stack.get(ClassesRegistries.LESSON.get());
        if (data == null) {
            Lessons.openWriter(sp, hand);
            return InteractionResultHolder.success(stack);
        }
        return Lessons.read(sp, stack, data) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isWritten(stack) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        Glyph g = Lessons.glyphOf(stack);
        if (g != null) return Component.translatable("item.isekai_mahou.technique_lesson.named", g.displayName());
        if (isWritten(stack)) return Component.translatable("item.isekai_mahou.technique_lesson.lost");
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        LessonData data = stack.get(ClassesRegistries.LESSON.get());
        if (data == null) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.classes.lesson_blank").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.classes.lesson_blank2").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Glyph g = GlyphRegistry.get(data.glyph());
        MagicPath teacherPath = data.teacherPath();
        tooltip.add(Component.translatable("tooltip.isekai_mahou.classes.lesson_author", data.author(), teacherPath.displayName())
                .withStyle(ChatFormatting.GRAY));
        if (g == null) return;
        tooltip.add(Component.translatable("glyph_kind.isekai_mahou." + g.kind().name().toLowerCase()).withStyle(ChatFormatting.GOLD));
        tooltip.add(g.description().withStyle(ChatFormatting.GRAY));
        // Requisitos do LEITOR: só o cliente sabe quem está olhando; sem isso, mostra o caso multiclasse
        MagicPath viewer = ctx.level() != null && ctx.level().isClientSide ? ClientMagic.data().path() : MagicPath.NONE;
        Lessons.Requirement req = Lessons.requirement(g, viewer);
        int circle = ctx.level() != null && ctx.level().isClientSide ? ClientMagic.data().circle() : 0;
        tooltip.add(Component.translatable("tooltip.isekai_mahou.classes.lesson_circle", req.circle())
                .withStyle(circle >= req.circle() ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.classes.scroll_cost", GlyphLearning.costText(g, req.essences()))
                .withStyle(ChatFormatting.DARK_AQUA));
        if (req.otherClass()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.classes.lesson_multiclass").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.isekai_mahou.classes.lesson_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
