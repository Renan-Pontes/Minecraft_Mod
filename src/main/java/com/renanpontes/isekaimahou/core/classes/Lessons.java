package com.renanpontes.isekaimahou.core.classes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.item.LessonData;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lições (docs/V10_DESIGN.md §1 e A1.6): o único jeito de aprender um glifo de ORIGEM de outra classe.
 * <ol>
 *   <li>O professor faz uma Lição em branco (papel + tinta + essência), segura e usa: abre a tela com os glifos de
 *       origem da classe DELE que ele conhece ({@link #openWriter}); escolhe um e a Lição é escrita ({@link #write}).</li>
 *   <li>Quem lê ({@link #read}) aprende o glifo se tiver o Círculo pedido ({@link Progression#lessonCircle}: +1 para
 *       quem é de outra classe) e as essências do elemento ({@link Progression#lessonEssenceCost}: 2 para multiclasse).
 *       A Lição é consumida.</li>
 *   <li>O professor ganha XP ({@link Progression#lessonTeacherXp}) — na hora se estiver online, senão no próximo login.</li>
 * </ol>
 */
public final class Lessons {
    /** Cor das mensagens/toasts de Lição. */
    public static final int RGB = 0xE8C27A;

    /** Glifos de origem da classe do jogador que ele conhece (o que pode ensinar), em ordem de tier e nome. */
    public static List<ResourceLocation> teachable(MagicData d) {
        List<Glyph> out = new ArrayList<>();
        MagicPath path = d.path();
        if (path == null || path == MagicPath.NONE) return List.of();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.origin() == path && d.knowsGlyph(g.id())) out.add(g);
        }
        out.sort(Comparator.comparingInt((Glyph g) -> g.tier()).thenComparing((Glyph g) -> g.id().getPath()));
        return out.stream().map(Glyph::id).toList();
    }

    // =====================================================================================
    //  Escrever
    // =====================================================================================

    /** Lição em branco usada: abre a tela de escolha no cliente (ou explica por que não dá). */
    public static void openWriter(ServerPlayer sp, InteractionHand hand) {
        MagicData d = MagicApi.get(sp);
        if (d.path() == MagicPath.NONE) {
            GlyphLearning.deny(sp, Component.translatable("message.isekai_mahou.classes.lesson_no_class"));
            return;
        }
        List<ResourceLocation> glyphs = teachable(d);
        if (glyphs.isEmpty()) {
            GlyphLearning.deny(sp, Component.translatable("message.isekai_mahou.classes.lesson_nothing", d.path().displayName()));
            return;
        }
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
        PacketDistributor.sendToPlayer(sp, new ClassesPayloads.OpenLessonWriter(hand.ordinal(), glyphs));
    }

    /** Pacote {@code WriteLesson}: confere tudo e grava o glifo numa Lição em branco da mão informada. */
    static void write(ServerPlayer sp, int handIndex, ResourceLocation glyphId) {
        if (handIndex < 0 || handIndex >= InteractionHand.values().length) return;
        InteractionHand hand = InteractionHand.values()[handIndex];
        ItemStack stack = sp.getItemInHand(hand);
        if (!stack.is(ClassesRegistries.TECHNIQUE_LESSON.get()) || stack.has(ClassesRegistries.LESSON.get())) {
            GlyphLearning.deny(sp, Component.translatable("message.isekai_mahou.classes.lesson_need_blank"));
            return;
        }
        MagicData d = MagicApi.get(sp);
        Glyph g = GlyphRegistry.get(glyphId);
        if (g == null || g.origin() != d.path() || d.path() == MagicPath.NONE || !d.knowsGlyph(glyphId)) {
            GlyphLearning.deny(sp, Component.translatable("message.isekai_mahou.classes.lesson_cannot_teach"));
            return;
        }
        LessonData data = new LessonData(glyphId, sp.getGameProfile().getName(), sp.getUUID(), d.path().id);
        ItemStack written = stack.split(1);
        written.set(ClassesRegistries.LESSON.get(), data);
        if (stack.isEmpty()) sp.setItemInHand(hand, written);
        else if (!sp.getInventory().add(written)) sp.drop(written, false);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.classes.lesson_written", g.displayName())
                .withColor(RGB), true);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.9F, 1.1F);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.1, sp.getZ(), 24, 0.35, 0.4, 0.35, 0.5);
        MagicApi.addCounter(sp, "lesson.written", 1);
    }

    // =====================================================================================
    //  Ler
    // =====================================================================================

    /** Requisitos de quem LÊ a Lição: Círculo e essências (multiclasse paga mais). */
    public record Requirement(int circle, int essences, boolean otherClass) {}

    /** O que o leitor {@code reader} precisa para aprender o glifo {@code g} por Lição. */
    public static Requirement requirement(Glyph g, MagicPath reader) {
        boolean other = g.origin() != null && g.origin() != MagicPath.NONE && g.origin() != reader;
        return new Requirement(Progression.lessonCircle(g.tier(), g.minCircle(), other), Progression.lessonEssenceCost(other), other);
    }

    /** Lição escrita usada: aprende (ou explica o que falta). Retorna true se a Lição foi consumida. */
    public static boolean read(ServerPlayer sp, ItemStack stack, LessonData data) {
        Glyph g = GlyphRegistry.get(data.glyph());
        if (g == null) {
            GlyphLearning.deny(sp, Component.translatable("message.isekai_mahou.classes.lesson_unknown_glyph"));
            return false;
        }
        MagicData d = MagicApi.get(sp);
        if (d.knowsGlyph(g.id())) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.glyph_known").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        boolean free = GlyphLearning.free(sp);
        Requirement req = requirement(g, d.path());
        if (!free && d.circle() < req.circle()) {
            GlyphLearning.deny(sp, Component.translatable(req.otherClass()
                    ? "message.isekai_mahou.classes.lesson_circle_multiclass" : "message.isekai_mahou.classes.lesson_circle",
                    req.circle(), g.displayName()));
            return false;
        }
        Element pay = free ? null : GlyphLearning.pickEssence(sp, g, req.essences());
        if (!free && pay == null) {
            GlyphLearning.deny(sp, GlyphLearning.missingEssence(sp, g, req.essences()));
            return false;
        }
        if (!MagicApi.learnGlyph(sp, g.id())) return false;
        if (pay != null) GlyphLearning.consume(sp, pay, req.essences());
        ItemStack icon = stack.copyWithCount(1);
        stack.consume(1, sp);

        int rgb = g.origin() != null ? g.origin().rgb() : RGB;
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.classes.lesson_learned", g.displayName(),
                Component.literal(data.author()).withColor(rgb)).withColor(RGB));
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.classes.lesson_learned"), g.displayName(), icon, rgb);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(), 40, 0.5, 0.7, 0.5, 0.8);
        sp.serverLevel().sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 12, 0.4, 0.6, 0.4, 0.03);
        MagicApi.addCounter(sp, "lesson.learned", 1);
        if (req.otherClass()) MagicApi.addCounter(sp, "lesson.multiclass", 1);
        rewardTeacher(sp, data, g);
        return true;
    }

    /** XP do professor: na hora se estiver online (e não for o próprio leitor), senão guardado para o próximo login. */
    private static void rewardTeacher(ServerPlayer reader, LessonData data, Glyph g) {
        if (data.authorId().equals(reader.getUUID())) return;
        int xp = Progression.lessonTeacherXp(g.tier());
        ServerPlayer teacher = reader.server.getPlayerList().getPlayer(data.authorId());
        if (teacher == null) {
            LessonLedger.of(reader.server).add(data.authorId(), xp);
            return;
        }
        MagicApi.addCounter(teacher, "lesson.taught", 1);
        teacher.sendSystemMessage(Component.translatable("message.isekai_mahou.classes.lesson_taught",
                reader.getDisplayName(), g.displayName(), xp).withColor(RGB));
        teacher.serverLevel().playSound(null, teacher.getX(), teacher.getY(), teacher.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        MagicApi.giveXp(teacher, xp, "lesson");
    }

    /** Login: entrega o XP de professor que ficou guardado. */
    static void onLogin(ServerPlayer sp) {
        int[] pending = LessonLedger.of(sp.server).take(sp.getUUID());
        if (pending == null || pending[0] <= 0) return;
        MagicApi.addCounter(sp, "lesson.taught", Math.max(1, pending[1]));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.classes.lesson_taught_offline", pending[1], pending[0])
                .withColor(RGB));
        MagicApi.giveXp(sp, pending[0], "lesson");
    }

    /** Glifo da Lição (ou null se em branco / glifo não existe mais). */
    @Nullable
    public static Glyph glyphOf(ItemStack stack) {
        LessonData data = stack.get(ClassesRegistries.LESSON.get());
        return data == null ? null : GlyphRegistry.get(data.glyph());
    }

    private Lessons() {}
}
