package com.renanpontes.isekaimahou.feature.ritual_druid;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.AncestralSaplingBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_druid.entity.AnimalSpiritEntity;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Regras do Ritual do Druida: rank, conferência dos requisitos (Harmonia, oferenda, tarefa de estação),
 * a subida de estágio com as recompensas e as falas dos Espíritos Animais.
 */
public final class DruidProgress {
    private static final int RITUAL_RGB = 0x7BE495;

    /** Rank do Druida = maior estágio da Árvore Coração já conquistado (0..10). */
    public static int rank(ServerPlayer player) {
        return Math.max(0, Math.min(DruidStages.MAX_STAGE, MagicApi.get(player).counter(DruidStages.RANK_COUNTER)));
    }

    /**
     * O jogador já tem uma Árvore Coração (ou Muda germinando) viva? Confere o bloco de verdade; se o registro
     * estiver velho (árvore destruída com o dono offline), limpa.
     */
    public static boolean hasLivingTree(ServerPlayer player) {
        DruidGrove grove = DruidGrove.of(player);
        GlobalPos tree = grove.tree();
        if (tree == null) return false;
        ServerLevel level = player.server.getLevel(tree.dimension());
        if (level != null) {
            BlockEntity be = level.getBlockEntity(tree.pos());
            if (be instanceof HeartTreeCoreBlockEntity core && player.getUUID().equals(core.owner())) return true;
            if (be instanceof AncestralSaplingBlockEntity sap && player.getUUID().equals(sap.owner())) return true;
        }
        grove.setTree(null);
        return false;
    }

    // =====================================================================================
    //  Requisitos
    // =====================================================================================
    /** Situação do próximo estágio. {@code taskDone} = -1 quando não dá para saber (dono offline). */
    public record Status(DruidStages.Stage next, Harmony harmony, int[] delivered, int taskDone) {
        public boolean harmonyOk() { return next != null && harmony.total() >= next.harmony(); }
        public boolean taskOk() { return next != null && taskDone >= next.amount(); }
        public boolean offeringOk() {
            if (next == null) return false;
            for (int i = 0; i < next.offerings().size(); i++) if (delivered[i] < next.offerings().get(i).count()) return false;
            return true;
        }
        public boolean ready() { return harmonyOk() && offeringOk() && taskOk(); }
    }

    public static Status status(ServerLevel level, HeartTreeCoreBlockEntity core, ServerPlayer owner, boolean freshHarmony) {
        DruidStages.Stage next = DruidStages.get(core.stage() + 1);
        Harmony h = core.harmony(level, freshHarmony);
        int[] delivered = new int[next == null ? 0 : next.offerings().size()];
        for (int i = 0; i < delivered.length; i++) delivered[i] = core.offered(i);
        int task = next == null || owner == null ? -1 : taskProgress(owner, next);
        return new Status(next, h, delivered, task);
    }

    public static boolean offeringComplete(HeartTreeCoreBlockEntity core, DruidStages.Stage next) {
        for (int i = 0; i < next.offerings().size(); i++) if (core.offered(i) < next.offerings().get(i).count()) return false;
        return true;
    }

    /** Progresso da tarefa de estação do estágio: diferença do contador desde que a tarefa começou. */
    public static int taskProgress(ServerPlayer player, DruidStages.Stage stage) {
        DruidGrove grove = DruidGrove.of(player);
        int now = MagicApi.get(player).counter(stage.task().counter);
        if (grove.taskStage() != stage.number()) grove.startTask(stage.number(), now);
        return Math.max(0, now - grove.taskBase());
    }

    // =====================================================================================
    //  Subida de estágio
    // =====================================================================================
    /** Botão "Crescer" da tela: confere tudo de novo no servidor e sobe o estágio. */
    public static void tryAdvance(ServerPlayer player, HeartTreeCoreBlockEntity core) {
        if (!(core.getLevel() instanceof ServerLevel level) || !core.isOwner(player)) return;
        if (core.isGrowing()) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.still_growing")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        Status st = status(level, core, player, true);
        if (st.next() == null) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.complete")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (!st.ready()) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.ritual_druid.not_ready")
                    .withStyle(ChatFormatting.RED), true);
            DruidNetwork.sendView(player, core, level);
            return;
        }
        advance(player, core, st.next());
    }

    /** Aplica a subida para {@code stage} (requisitos já conferidos, ou forçado por comando). */
    public static void advance(ServerPlayer player, HeartTreeCoreBlockEntity core, DruidStages.Stage stage) {
        // Efeitos e o espírito na dimensão da ÁRVORE (o comando de op pode ser usado de outra dimensão)
        ServerLevel level = core.getLevel() instanceof ServerLevel sl ? sl : player.serverLevel();
        BlockPos pos = core.getBlockPos();
        int n = stage.number();

        MagicApi.addCounter(player, DruidStages.RANK_COUNTER, 1);
        MagicApi.addCounter(player, DruidStages.GLOBAL_RANK_COUNTER, 1);
        core.completeStage(n);

        // Efeito: explosão de vida em volta do núcleo
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        level.sendParticles(Vfx.totem(), x, y + 1, z, 80, 0.6, 1.2, 0.6, 0.4);
        level.sendParticles(Vfx.happy(), x, y, z, 50, 3.0, 1.0, 3.0, 0.0);
        level.sendParticles(Vfx.endRod(), x, y + 1, z, 40, 0.5, 2.0, 0.5, 0.06);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 0.5F + n * 0.05F);

        Component title = Component.translatable("message.isekai_mahou.ritual_druid.stage_title", DruidStages.roman(n));
        Component subtitle = Component.translatable("message.isekai_mahou.ritual_druid.stage_subtitle", stage.spirit().displayName());
        Cutscenes.play(player, "ascension", player, title, subtitle, RITUAL_RGB, 60, false);
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.stage_up", DruidStages.roman(n))
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

        MagicApi.giveXp(player, stage.xp(), "ritual");

        // Despertar dos elementos de afinidade do Druida: Terra no 1º, Água no 3º, Luz no 5º
        Element awaken = n == 1 ? Element.EARTH : n == 3 ? Element.WATER : n == 5 ? Element.LIGHT : null;
        if (awaken != null && !MagicApi.awakenElement(player, awaken)) MagicApi.giveElementXp(player, awaken, 60);
        for (Element e : new Element[] {Element.EARTH, Element.WATER, Element.LIGHT}) MagicApi.giveElementXp(player, e, 12 * n);

        teachGlyphs(player, stage.glyphTier());

        // A tarefa do próximo estágio começa a contar agora
        DruidStages.Stage next = DruidStages.get(n + 1);
        if (next != null) taskProgress(player, next);

        AnimalSpiritEntity.summon(level, pos, player, stage.spirit(), n);
    }

    /** Glifos de Origem do Druida (Metamorfose) até o tier — também cobre glifos que ficaram para trás. */
    private static void teachGlyphs(ServerPlayer player, int tier) {
        MagicData d = MagicApi.get(player);
        int taught = 0;
        for (Glyph g : GlyphRegistry.all()) {
            if (g.origin() != MagicPath.DRUID || g.tier() > tier || d.knowsGlyph(g.id())) continue;
            if (MagicApi.learnGlyph(player, g.id())) {
                taught++;
                player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.glyph_gift", g.displayName())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        if (taught == 0) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.no_glyph").withStyle(ChatFormatting.GRAY));
        }
    }

    /** A muda germinou: registra a árvore, começa a tarefa e o primeiro espírito explica o ritual. */
    public static void onGerminated(ServerPlayer player, HeartTreeCoreBlockEntity core) {
        if (!(core.getLevel() instanceof ServerLevel level)) return;
        DruidGrove.of(player).setTree(GlobalPos.of(level.dimension(), core.getBlockPos()));
        int rank = rank(player);
        DruidStages.Stage next = DruidStages.get(rank + 1);
        if (next != null) taskProgress(player, next);
        if (rank == 0) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.germinated").withStyle(ChatFormatting.GREEN));
            AnimalSpiritEntity.summon(level, core.getBlockPos(), player, AnimalSpiritEntity.Kind.DEER, 0);
        } else {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.regrowing", DruidStages.roman(rank))
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    // =====================================================================================
    //  Espíritos: falas e bênção
    // =====================================================================================
    private static MutableComponent spiritPrefix(AnimalSpiritEntity spirit) {
        return Component.literal("[").append(spirit.kind().displayName()).append("] ");
    }

    /** Chave da fala do espírito para o estágio alcançado (0..10). */
    private static String lineKey(int reached) {
        return "message.isekai_mahou.ritual_druid.spirit.line." + Math.max(0, Math.min(DruidStages.MAX_STAGE, reached));
    }

    /** Fala do espírito: a mensagem do estágio alcançado e a próxima tarefa (oferenda, Harmonia, estação). */
    public static void spiritSpeech(ServerPlayer player, AnimalSpiritEntity spirit, int reached) {
        player.sendSystemMessage(spiritPrefix(spirit).append(Component.translatable(lineKey(reached))
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE)));
        DruidStages.Stage next = DruidStages.get(reached + 1);
        if (next == null) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.spirit.final").withStyle(ChatFormatting.GOLD));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.spirit.next", DruidStages.roman(next.number()),
                next.season().displayName()).withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("  • ").append(next.task().describe(next.amount())).withStyle(ChatFormatting.GRAY));
        MutableComponent offer = Component.empty();
        for (int i = 0; i < next.offerings().size(); i++) {
            DruidStages.Offering o = next.offerings().get(i);
            if (i > 0) offer.append(", ");
            offer.append(Component.literal(o.count() + "x ")).append(o.stack().getHoverName());
        }
        player.sendSystemMessage(Component.literal("  • ").append(Component.translatable("message.isekai_mahou.ritual_druid.spirit.offering", offer))
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  • ").append(Component.translatable("message.isekai_mahou.ritual_druid.spirit.harmony",
                next.harmony())).withStyle(ChatFormatting.GRAY));
    }

    /** Espírito sem dono (ovo): só observa e deixa uma pequena bênção. */
    public static void wanderingSpiritSpeech(ServerPlayer player, AnimalSpiritEntity spirit) {
        player.sendSystemMessage(spiritPrefix(spirit).append(Component.translatable("message.isekai_mahou.ritual_druid.spirit.wander")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE)));
        player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 0, true, true, true));
    }

    /** Bênção do espírito: Regeneração e Sorte (mais fortes/longas nos estágios altos) + um bônus do animal. */
    public static void bless(ServerPlayer player, int stage, AnimalSpiritEntity.Kind kind) {
        int regenAmp = stage >= 6 ? 1 : 0;
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400 + stage * 40, regenAmp, true, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.LUCK, 3600 + stage * 1200, stage >= 8 ? 1 : 0, true, true, true));
        switch (kind) {
            case DEER -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 0, true, true, true));
            case WOLF -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2400, 0, true, true, true));
            case HAWK -> player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2400, 0, true, true, true));
        }
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.ritual_druid.blessing", kind.displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private DruidProgress() {}
}
