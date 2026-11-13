package com.renanpontes.isekaimahou.feature.ritual_mage.research;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.block.ResearchTableBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Conclusão de uma Tese na Mesa de Pesquisa: valida tudo no servidor, consome e recompensa. */
public final class ThesisService {
    /** Bônus de XP por runa fixa usada como ponte no circuito. */
    public static final int BRIDGE_BONUS_PERCENT = 15;

    public static void tryComplete(ServerPlayer player, ResearchTableBlockEntity table) {
        Thesis thesis = table.thesis();
        ThesisPuzzle puzzle = table.puzzle();
        if (thesis == null || puzzle == null || !(player.level() instanceof ServerLevel level)) return;
        MageResearchData research = player.getData(MageRegistries.RESEARCH);
        if (research.hasThesis(thesis.id())) {
            fail(player, Component.translatable("message.isekai_mahou.mage.thesis_known"));
            return;
        }
        if (research.thesisCount() < thesis.requiredTheses()) {
            fail(player, Component.translatable("message.isekai_mahou.mage.thesis_locked", thesis.requiredTheses()));
            return;
        }
        Element[] placed = table.placedElements();
        ThesisPuzzle.Evaluation ev = puzzle.evaluate(placed, thesis);
        if (!ev.valid()) {
            fail(player, Component.translatable("message.isekai_mahou.mage.circuit_invalid"));
            return;
        }
        table.refreshShelves();
        if (table.shelves() < thesis.shelves()) {
            fail(player, Component.translatable("message.isekai_mahou.mage.need_shelves", table.shelves(), thesis.shelves()));
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && ResearchTableBlockEntity.countPaper(table.items()) < thesis.paper()) {
            fail(player, Component.translatable("message.isekai_mahou.mage.need_paper", thesis.paper()));
            return;
        }
        int[] need = new int[Element.values().length];
        for (Element e : placed) if (e != null) need[e.ordinal()]++;
        for (Element e : Element.values()) {
            if (need[e.ordinal()] > ResearchTableBlockEntity.countNotes(table.items(), e)) {
                fail(player, Component.translatable("message.isekai_mahou.mage.need_notes"));
                return;
            }
        }

        // ---------- Tudo certo: consome e recompensa
        table.consumeNotes(placed);
        if (!creative) table.consumePaper(thesis.paper());
        table.resetThesis();
        reward(player, level, table.getBlockPos(), thesis, ev.bridges, research, placed);
    }

    private static void fail(ServerPlayer player, MutableComponent msg) {
        player.sendSystemMessage(msg.withStyle(ChatFormatting.RED));
        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    private static void reward(ServerPlayer player, ServerLevel level, BlockPos pos, Thesis thesis, int bridges,
                               MageResearchData research, Element[] placed) {
        research.completeThesis(thesis.id());
        MagicApi.addCounter(player, "ritual_rank", 1);
        MagicApi.addCounter(player, "ritual_rank.mage", 1);
        MagicApi.addCounter(player, "mage_theses", 1);
        // O nível das Runas segue o Atlas (o contador continua somando mesmo após /mahou mage_research reset)
        int done = research.thesisCount();
        MagicData magic = MagicApi.get(player);

        int xp = thesis.xp() * (100 + BRIDGE_BONUS_PERCENT * bridges) / 100;
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.thesis_complete",
                thesis.displayName().withStyle(ChatFormatting.BOLD)).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.thesis_xp", xp, bridges)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        MagicApi.giveXp(player, xp, "ritual");

        // XP dos elementos usados (só conta para elementos já despertados)
        Set<Element> used = new LinkedHashSet<>();
        for (Element e : placed) {
            if (e == null) continue;
            if (e.isBase()) used.add(e);
            else if (e.isFusion()) { used.add(e.parentA); used.add(e.parentB); }
        }
        for (Element e : used) MagicApi.giveElementXp(player, e, 20 + 8 * thesis.index());

        // Despertar: teses elementais despertam o elemento para quem é Mago (atalho da classe)
        Element awaken = thesis.awakens();
        if (awaken != null && magic.path() == MagicPath.MAGE && MagicPath.MAGE.affinity(awaken) == MagicPath.Affinity.AFFINITY
                && !magic.isAwakened(awaken)) {
            MagicApi.awakenElement(player, awaken);
        }

        // Runas (Magia de Origem do Mago) até o nível = nº de teses concluídas (máx. 5)
        int tier = Thesis.runeTierFor(done);
        List<Component> learned = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.origin() == MagicPath.MAGE && g.tier() <= tier && !magic.knowsGlyph(g.id()) && MagicApi.learnGlyph(player, g.id())) {
                learned.add(g.displayName().withStyle(ChatFormatting.AQUA));
            }
        }
        if (!learned.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.glyphs_learned",
                    joined(learned)).withStyle(ChatFormatting.DARK_AQUA));
        }

        // Teses além da 5ª: materiais raros em vez de novas runas
        if (done > 5) {
            ItemStack essence = new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), done - 4);
            ItemStack crystal = new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 2);
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.bonus_items",
                    joined(List.of(essence.getHoverName().copy().append(" ×" + essence.getCount()),
                            crystal.getHoverName().copy().append(" ×" + crystal.getCount())))).withStyle(ChatFormatting.GOLD));
            give(player, essence);
            give(player, crystal);
        }
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.mage.rank", research.thesisCount(), Thesis.ALL.size())
                .withStyle(ChatFormatting.GRAY));
        MagePayloads.syncAtlas(player);

        Announce.send(player, Component.translatable("announce.isekai_mahou.mage_thesis", thesis.displayName()),
                thesis.color(), Announce.STYLE_SYSTEM);
        effects(player, level, pos, thesis);
    }

    private static MutableComponent joined(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            out.append(parts.get(i));
        }
        return out;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** Coluna de runas subindo da mesa, anéis nos elementos da tese e o som de conquista. */
    private static void effects(ServerPlayer player, ServerLevel level, BlockPos pos, Thesis thesis) {
        Vec3 c = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        level.sendParticles(Vfx.enchant(), c.x, c.y + 0.5, c.z, 120, 0.9, 0.8, 0.9, 1.2);
        level.sendParticles(Vfx.endRod(), c.x, c.y + 0.4, c.z, 30, 0.2, 1.2, 0.2, 0.06);
        level.sendParticles(Vfx.totem(), c.x, c.y + 0.8, c.z, 40, 0.5, 0.6, 0.5, 0.25);
        Set<Element> rings = new LinkedHashSet<>();
        rings.add(thesis.entry());
        rings.add(thesis.exit());
        for (Thesis.Req r : thesis.reqs()) if (r.element() != null) rings.add(r.element());
        double radius = 1.0;
        for (Element e : rings) {
            ElementFx.ring(level, c.add(0, -0.5, 0), e, radius, 28);
            radius += 0.45;
        }
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.9F);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.3F);
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    private ThesisService() {}
}
