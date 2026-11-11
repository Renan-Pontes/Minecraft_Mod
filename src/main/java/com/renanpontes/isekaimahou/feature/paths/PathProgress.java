package com.renanpontes.isekaimahou.feature.paths;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lado servidor dos Caminhos: libera nós (Despertar + Provações concluídas), entrega os bônus dos feitos,
 * ensina glifos (caminho de cada elemento e caminho Arcano por NÍVEL), prepara o elemento de afinidade da
 * classe e cuida do despertar por afinidade.
 */
public final class PathProgress {
    /** Jogadores com progresso novo: verificados no próximo tick (evita rodar dentro de outros eventos). */
    private static final Set<UUID> PENDING = new HashSet<>();

    public static void markPending(ServerPlayer player) {
        PENDING.add(player.getUUID());
    }

    public static boolean takePending(ServerPlayer player) {
        return PENDING.remove(player.getUUID());
    }

    public static void clearPending() {
        PENDING.clear();
    }

    // =====================================================================================
    //  Verificação automática
    // =====================================================================================

    /** Acima disto, os nós liberados de uma vez viram uma mensagem só (ex.: depois de /mahou paths node all). */
    private static final int MAX_SEPARATE_ANNOUNCES = 3;

    private record Unlocked(Element element, PathNode node, List<Glyph> learned) {}

    /**
     * Libera todos os nós cumpridos (Despertar e nós de Provação concluída — vencer o rank N vale para os
     * ranks abaixo), entrega os feitos-bônus prontos e avisa sobre despertar por afinidade.
     */
    public static void check(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        List<Unlocked> done = new ArrayList<>();
        for (Element e : PathRules.pathElements()) {
            if (!d.isAwakened(e)) continue;
            for (PathNode node : PathNode.values()) {
                if (d.hasNode(e, node.id) || !PathRules.allMet(e, node, d, player)) continue;
                List<Glyph> learned = new ArrayList<>();
                if (apply(player, e, node, learned)) done.add(new Unlocked(e, node, learned));
            }
        }
        if (done.size() <= MAX_SEPARATE_ANNOUNCES) {
            for (Unlocked u : done) announceNode(player, u.element(), u.node(), u.learned());
        } else {
            announceSummary(player, done);
        }
        checkFeats(player, d);
        hintAffinity(player, d);
    }

    /**
     * Libera um nó sem checar requisitos (comando): marca no MagicData, ensina os glifos e (se pedido) anuncia.
     * Retorna false se o elemento não está despertado ou o nó já existe.
     */
    public static boolean unlock(ServerPlayer player, Element e, PathNode node, boolean announce) {
        List<Glyph> learned = new ArrayList<>();
        if (!apply(player, e, node, learned)) return false;
        if (announce) announceNode(player, e, node, learned);
        return true;
    }

    private static boolean apply(ServerPlayer player, Element e, PathNode node, List<Glyph> learned) {
        MagicData d = MagicApi.get(player);
        if (!d.isAwakened(e) || d.hasNode(e, node.id)) return false;
        d.addNode(e, node.id);
        // Nó liberado por uma Provação MAIOR (mundo antigo, comando): grava também a flag deste rank, para a
        // corrente de ranks do TrialsApi (isCleared por rank) continuar coerente — repetir vira Desafio.
        int rank = node.trialRank();
        if (rank > 0 && !TrialsApi.isCleared(d, e, rank)) d.setFlag(TrialsApi.flag(e, rank), true);
        learned.addAll(teachNode(player, e, node));
        // Contadores para outros módulos reagirem por texto (Diário, Guilda...): path_node.<nó> e path_node.total.
        MagicApi.addCounter(player, "path_node." + node.id, 1);
        MagicApi.addCounter(player, "path_node.total", 1);
        return true;
    }

    // =====================================================================================
    //  Feitos (bônus)
    // =====================================================================================

    private record Feat(Element element, int stage, int amount) {}

    /** Acima disto, os feitos cumpridos de uma vez viram uma mensagem só (ex.: mundo da v0.6 com muitos abates). */
    private static final int MAX_SEPARATE_FEATS = 2;

    /** Os antigos requisitos de Aprendiz/Adepto/Mestre viraram feitos: cumprir dá um bolo de XP do elemento. */
    private static void checkFeats(ServerPlayer player, MagicData d) {
        List<Feat> done = new ArrayList<>();
        for (Element e : PathRules.pathElements()) {
            if (!d.isAwakened(e)) continue;
            for (int stage = 1; stage <= PathRules.FEAT_STAGES; stage++) {
                String flag = PathRules.featFlag(e, stage);
                if (d.hasFlag(flag) || !PathRules.featMet(e, stage, d, player)) continue;
                d.setFlag(flag, true);
                int base = PathRules.featBonus(stage);
                // giveElementXp aplica a afinidade da classe; "amount" é só o número mostrado ao jogador.
                int amount = Math.max(1, Math.round(base * d.path().affinity(e).xpMult));
                MagicApi.giveElementXp(player, e, base);
                MagicApi.addCounter(player, "path_feat.total", 1);
                done.add(new Feat(e, stage, amount));
            }
        }
        if (done.size() <= MAX_SEPARATE_FEATS) {
            for (Feat f : done) announceFeat(player, d, f.element(), f.stage(), f.amount());
        } else {
            announceFeatSummary(player, done);
        }
    }

    /** Vários feitos de uma vez: uma linha (detalhes ao passar o mouse), um toast e um efeito. */
    private static void announceFeatSummary(ServerPlayer player, List<Feat> done) {
        MutableComponent hover = Component.empty();
        int total = 0;
        for (int i = 0; i < done.size(); i++) {
            Feat f = done.get(i);
            if (i > 0) hover.append("\n");
            hover.append(Component.translatable("message.isekai_mahou.paths.feat_summary_line", f.element().displayName(),
                    TrialsApi.roman(f.stage()), f.amount()).withColor(f.element().color));
            total += f.amount();
        }
        MutableComponent count = Component.literal(String.valueOf(done.size())).withStyle(s -> s.withBold(true)
                .withUnderlined(true).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        player.sendSystemMessage(Component.literal("★ ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("message.isekai_mahou.paths.feat_summary", count, total).withStyle(ChatFormatting.GOLD)));
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.paths.feat_summary", done.size()),
                Component.translatable("toast.isekai_mahou.paths.feat_summary_xp", total),
                PathRules.elementIcon(done.get(0).element()), Rewards.GOLD);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        for (Feat f : done) ElementFx.burst(level, player.position().add(0, 1.0, 0), f.element(), 10, 0.5);
    }

    private static void announceFeat(ServerPlayer player, MagicData d, Element e, int stage, int amount) {
        MutableComponent what = Component.empty();
        List<PathReq> reqs = PathRules.feats(e, stage, d.path());
        for (int i = 0; i < reqs.size(); i++) {
            if (i > 0) what.append(Component.literal(" + "));
            what.append(reqs.get(i).label());
        }
        player.sendSystemMessage(Component.literal("★ ").withColor(e.color)
                .append(Component.translatable("message.isekai_mahou.paths.feat", e.displayName(), what.withStyle(ChatFormatting.WHITE),
                        amount).withStyle(ChatFormatting.GOLD)));
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.paths.feat", e.displayName()),
                Component.translatable("toast.isekai_mahou.paths.feat_xp", amount, e.displayName()), PathRules.elementIcon(e), e.color);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.5F);
        ElementFx.burst(level, player.position().add(0, 1.0, 0), e, 20, 0.5);
    }

    // =====================================================================================
    //  Glifos
    // =====================================================================================

    /** Ensina os glifos do nó. Retorna os realmente novos (para a mensagem). */
    private static List<Glyph> teachNode(ServerPlayer player, Element e, PathNode node) {
        MagicData d = MagicApi.get(player);
        List<Glyph> learned = new ArrayList<>();
        if (node == PathNode.FLUIDITY) {
            ResourceLocation trigger = PathRules.nextTrigger(d);
            Glyph g = trigger == null ? null : GlyphRegistry.get(trigger);
            if (g != null) {
                learn(player, g, learned);
                d.setFlag(PathRules.FLUIDITY_FLAG + e.id + "." + trigger.getPath(), true);
            }
            return learned;
        }
        for (Glyph g : PathRules.nodeGlyphs(e, node, d)) {
            if (PathRules.teachableNow(g, d)) learn(player, g, learned);
        }
        return learned;
    }

    private static void learn(ServerPlayer player, Glyph g, List<Glyph> out) {
        MagicData d = MagicApi.get(player);
        if (d.knowsGlyph(g.id())) return;
        MagicApi.learnGlyphSilently(player, g.id());
        if (d.knowsGlyph(g.id())) out.add(g);
    }

    /**
     * Caminho Arcano: ensina os glifos neutros (Arcano, sem Origem) cujo tier o NÍVEL do jogador já liberou
     * ({@link Progression#arcaneUnlockLevel}: 1, 5, 10, 18, 28), respeitando o Círculo mínimo de cada glifo.
     * Roda ao escolher a classe, a cada nível, a cada Círculo, no login e periodicamente.
     */
    public static void teachArcane(ServerPlayer player, boolean announce) {
        MagicData d = MagicApi.get(player);
        if (d.path() == MagicPath.NONE) return;
        List<Glyph> learned = new ArrayList<>();
        for (int tier = 1; tier <= PathRules.ARCANE_TIERS; tier++) {
            if (d.level() < Progression.arcaneUnlockLevel(tier)) break;
            for (Glyph g : PathRules.arcaneGlyphs(tier)) {
                if (PathRules.arcaneReady(g, d)) learn(player, g, learned);
            }
        }
        if (!announce || learned.isEmpty()) return;
        player.sendSystemMessage(Component.literal("✧ ").withColor(Element.ARCANE.color)
                .append(Component.translatable("message.isekai_mahou.paths.arcane", d.level(), glyphList(learned))
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.paths.arcane"),
                Component.translatable("toast.isekai_mahou.paths.glyphs", learned.size()), new ItemStack(Items.ENCHANTED_BOOK),
                Element.ARCANE.color);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 1.1F);
    }

    /** Quem já tem um nó Domínio recebe as leis que o Círculo novo liberou. */
    public static void teachLaws(ServerPlayer player, boolean announce) {
        MagicData d = MagicApi.get(player);
        boolean hasDomain = false;
        for (Element e : PathRules.pathElements()) {
            if (d.hasNode(e, PathNode.DOMAIN.id)) {
                hasDomain = true;
                break;
            }
        }
        if (!hasDomain) return;
        List<Glyph> learned = new ArrayList<>();
        for (Glyph g : PathRules.laws()) {
            if (g.minCircle() <= d.circle()) learn(player, g, learned);
        }
        if (announce && !learned.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.paths.laws", glyphList(learned))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /**
     * Login: migra mundos antigos (nós da v0.6 contam como Provações concluídas; o elemento de afinidade da
     * classe é preparado) e garante, em silêncio, os glifos de todos os nós já liberados — cobre glifos
     * adicionados por módulos depois que o jogador liberou o nó.
     */
    public static void ensureAll(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        migrateTrialFlags(d);
        for (Element e : PathRules.pathElements()) {
            if (!d.isAwakened(e)) continue;
            for (PathNode node : PathNode.values()) {
                if (!d.hasNode(e, node.id) || node == PathNode.FLUIDITY) continue;
                for (Glyph g : PathRules.nodeGlyphs(e, node, d)) {
                    if (PathRules.teachableNow(g, d) && !d.knowsGlyph(g.id())) MagicApi.learnGlyphSilently(player, g.id());
                }
            }
        }
        // Fluidez: cada nó vale um gatilho de movimento. Nós sem gatilho anotado recebem o próximo que faltar.
        List<Element> unassigned = new ArrayList<>();
        int fluidity = 0;
        for (Element e : PathRules.pathElements()) {
            if (!d.hasNode(e, PathNode.FLUIDITY.id)) continue;
            fluidity++;
            if (PathRules.fluidityTrigger(d, e) == null) unassigned.add(e);
        }
        int known = 0;
        for (ResourceLocation id : PathRules.moveTriggers()) if (d.knowsGlyph(id)) known++;
        while (known < fluidity) {
            ResourceLocation next = PathRules.nextTrigger(d);
            if (next == null) break;
            MagicApi.learnGlyphSilently(player, next);
            if (!unassigned.isEmpty()) {
                d.setFlag(PathRules.FLUIDITY_FLAG + unassigned.remove(0).id + "." + next.getPath(), true);
            }
            known++;
        }
        // Com aviso: num mundo antigo os Arcanos passam a vir por nível e o jogador pode ganhar vários de uma vez.
        teachArcane(player, true);
        startAffinity(player, false);
    }

    /**
     * Nós de Provação liberados na v0.6 (por feitos) viram a flag da Provação correspondente, para a corrente
     * de ranks continuar de onde o jogador estava (o próximo rank pede o anterior concluído).
     */
    private static void migrateTrialFlags(MagicData d) {
        for (Element e : PathRules.pathElements()) {
            if (!d.isAwakened(e)) continue;
            for (PathNode node : PathNode.values()) {
                int rank = node.trialRank();
                if (rank > 0 && d.hasNode(e, node.id) && !TrialsApi.isCleared(d, e, rank)) d.setFlag(TrialsApi.flag(e, rank), true);
            }
        }
    }

    // =====================================================================================
    //  Elemento de afinidade da classe
    // =====================================================================================

    /**
     * Uma vez por jogador (flag {@link PathRules#START_FLAG}): desperta o 1º elemento de afinidade da classe,
     * libera o Despertar (glifos tier 1), registra na Roda e grava a magia pronta no slot Z do loadout 1 desse
     * elemento — só se o slot estiver vazio. {@code fresh} = acabou de escolher a classe: a Roda já gira para o
     * elemento e o slot X recebe a Onda de Choque (se vazio), para as duas teclas funcionarem de cara.
     * As magias Arcanas do núcleo não são tocadas.
     */
    public static void startAffinity(ServerPlayer player, boolean fresh) {
        MagicData d = MagicApi.get(player);
        if (d.path() == MagicPath.NONE || d.hasFlag(PathRules.START_FLAG)) return;
        d.setFlag(PathRules.START_FLAG, true);
        Element e = PathRules.primaryAffinity(d.path());
        if (e == null) return;
        boolean awakenedNow = MagicApi.awakenElement(player, e);
        if (!d.isAwakened(e)) return;

        List<Glyph> learned = new ArrayList<>();
        if (!apply(player, e, PathNode.AWAKENING, learned)) {
            for (Glyph g : PathRules.tierGlyphs(e, 1)) learn(player, g, learned);
        }

        SpellRecipe spell = PathRules.starterSpell(e, player.clientInformation().language().toLowerCase(Locale.ROOT));
        boolean placed = false;
        if (spell != null && d.peekSlot(e, 0, 0).isEmpty()) {
            d.slot(e, 0, 0).set(MoveTrigger.NORMAL, spell);
            placed = true;
        }
        boolean switched = false, shockwave = false;
        if (fresh && placed && d.registeredElements().contains(e)) {
            if (d.peekSlot(e, 0, 1).isEmpty()) {
                d.slot(e, 0, 1).set(MoveTrigger.NORMAL, CoreGameplay.STARTER_SHOCKWAVE);
                shockwave = true;
            }
            d.setActiveLoadout(e, 0);
            d.setActiveWheel(d.wheel().indexOf(e));
            switched = true;
        }
        d.markDirty();
        if (!awakenedNow && !placed && learned.isEmpty()) return;

        if (awakenedNow) {
            player.sendSystemMessage(Component.literal("✦ ").withColor(e.color)
                    .append(Component.translatable("message.isekai_mahou.paths.affinity_start", d.path().displayName(),
                            e.displayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        }
        if (!learned.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.paths.glyphs_learned", glyphList(learned))
                    .withStyle(ChatFormatting.AQUA));
        }
        Component slotKey = Component.keybind("key.isekai_mahou.slot1").withStyle(ChatFormatting.YELLOW);
        Component wheelKey = Component.keybind("key.isekai_mahou.wheel_modifier").withStyle(ChatFormatting.YELLOW);
        if (placed) {
            Component spellName = Component.literal(spell.name()).withColor(spell.color());
            String key = !switched ? "message.isekai_mahou.paths.starter_spell"
                    : shockwave ? "message.isekai_mahou.paths.starter_switched" : "message.isekai_mahou.paths.starter_switched_solo";
            Component shockKey = Component.keybind("key.isekai_mahou.slot2").withStyle(ChatFormatting.YELLOW);
            Component shockName = Component.literal(CoreGameplay.STARTER_SHOCKWAVE.name()).withColor(CoreGameplay.STARTER_SHOCKWAVE.color());
            player.sendSystemMessage(Component.translatable(key, e.displayName(), slotKey, spellName, wheelKey, shockKey, shockName)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        // Só para quem ainda não venceu nenhuma Provação do elemento (mundos antigos já passaram dessa fase).
        if (TrialsApi.clearedRank(d, e) == 0) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.paths.first_steps", e.displayName(),
                    Progression.trialElementLevel(1), TrialsApi.title(e, 1),
                    Component.keybind("key.isekai_mahou.menu").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
        }
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.paths.affinity", e.displayName()),
                placed ? Component.translatable("toast.isekai_mahou.paths.starter", spell.name())
                        : Component.translatable("toast.isekai_mahou.paths.glyphs", learned.size()),
                PathRules.elementIcon(e), e.color);

        ServerLevel level = player.serverLevel();
        ElementFx.ring(level, player.position(), e, 1.6, 36);
        ElementFx.burst(level, player.position().add(0, 1.0, 0), e, 36, 0.6);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ElementFx.castSoundOf(e), SoundSource.PLAYERS, 0.8F, 1.0F);
        markPending(player);
    }

    // =====================================================================================
    //  Despertar por afinidade (botão da aba)
    // =====================================================================================

    /** Pedido do botão da aba: valida no servidor e desperta. */
    public static void awakenByAffinity(ServerPlayer player, Element e) {
        MagicData d = MagicApi.get(player);
        if (e == null || !e.isBase()) return;
        if (!PathRules.canAwakenByAffinity(d, e)) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.paths.affinity_denied")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (MagicApi.awakenElement(player, e)) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.paths.affinity_awakened",
                    d.path().displayName(), e.displayName()).withStyle(ChatFormatting.GOLD));
            ServerLevel level = player.serverLevel();
            ElementFx.ring(level, player.position(), e, 1.6, 36);
            ElementFx.burst(level, player.position().add(0, 1.0, 0), e, 30, 0.6);
            markPending(player);
        }
    }

    /** Avisa (uma vez por elemento) quando um despertar por afinidade fica disponível. */
    private static void hintAffinity(ServerPlayer player, MagicData d) {
        for (Element e : PathRules.affinityElements(d.path())) {
            String flag = "path_affinity_hint." + e.id;
            if (d.hasFlag(flag) || !PathRules.canAwakenByAffinity(d, e)) continue;
            d.setFlag(flag, true);
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.paths.affinity_ready",
                    e.displayName(), Component.keybind("key.isekai_mahou.menu")).withStyle(ChatFormatting.YELLOW));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    // =====================================================================================
    //  Anúncio
    // =====================================================================================

    private static void announceNode(ServerPlayer player, Element e, PathNode node, List<Glyph> learned) {
        player.sendSystemMessage(Component.literal(node.major ? "✦ " : "◆ ").withColor(e.color)
                .append(Component.translatable("message.isekai_mahou.paths.node_unlocked", e.displayName(),
                        node.displayName().withStyle(ChatFormatting.BOLD)).withStyle(ChatFormatting.GOLD)));
        if (!learned.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.paths.glyphs_learned", glyphList(learned))
                    .withStyle(ChatFormatting.AQUA));
        } else if (node == PathNode.FLUIDITY) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.paths.no_trigger").withStyle(ChatFormatting.GRAY));
        }
        PacketDistributor.sendToPlayer(player, new CorePayloads.AnnounceText(player.getId(),
                Component.translatable("announce.isekai_mahou.paths.node", e.displayName(), node.displayName()),
                e.color, Announce.STYLE_SYSTEM));
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.paths.node", e.displayName(), node.displayName()),
                learned.isEmpty() ? node.description() : Component.translatable("toast.isekai_mahou.paths.glyphs", learned.size()),
                PathRules.elementIcon(e), e.color);

        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        ElementFx.ring(level, pos, e, 1.3, 28);
        ElementFx.burst(level, pos.add(0, 1.0, 0), e, node.major ? 40 : 18, 0.6);
        level.playSound(null, pos.x, pos.y, pos.z, ElementFx.castSoundOf(e), SoundSource.PLAYERS, 0.6F, 1.2F);
        if (node.major) {
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 30, 0.4, 0.9, 0.4, 0.05);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
            if (node == PathNode.ARCHMAGE) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.2, pos.z, 60, 0.6, 1.0, 0.6, 0.3);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.7F);
            }
        } else {
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    /** Muitos nós de uma vez: uma linha com a contagem (nós ao passar o mouse) e um único efeito. */
    private static void announceSummary(ServerPlayer player, List<Unlocked> done) {
        MutableComponent hover = Component.empty();
        int glyphs = 0;
        for (int i = 0; i < done.size(); i++) {
            Unlocked u = done.get(i);
            if (i > 0) hover.append("\n");
            hover.append(Component.translatable("message.isekai_mahou.paths.summary_line", u.element().displayName(),
                    u.node().displayName().withStyle(ChatFormatting.WHITE)));
            glyphs += u.learned().size();
        }
        MutableComponent count = Component.literal(String.valueOf(done.size())).withStyle(s -> s.withBold(true)
                .withUnderlined(true).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        player.sendSystemMessage(Component.literal("✦ ").withColor(Element.ARCANE.color)
                .append(Component.translatable("message.isekai_mahou.paths.summary", count, glyphs).withStyle(ChatFormatting.GOLD)));
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.paths.summary", done.size()),
                Component.translatable("toast.isekai_mahou.paths.glyphs", glyphs), new ItemStack(Items.ENCHANTED_BOOK), Rewards.GOLD);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 0.8F, 1.1F);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.5, 0.8, 0.5, 0.04);
    }

    /** "A, B, C" com a cor de cada glifo e a descrição ao passar o mouse no chat. */
    public static MutableComponent glyphList(List<Glyph> glyphs) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph g = glyphs.get(i);
            if (i > 0) out.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            MutableComponent hover = g.displayName().withColor(g.element().color).append("\n")
                    .append(kindName(g.kind()).withStyle(ChatFormatting.DARK_GRAY))
                    .append("\n").append(g.description().withStyle(ChatFormatting.GRAY));
            out.append(g.displayName().withStyle(s -> s.withColor(g.element().color)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))));
        }
        return out;
    }

    /** Nome do tipo de glifo (tradução do núcleo: glyph_kind.isekai_mahou.<tipo>). */
    public static MutableComponent kindName(GlyphKind kind) {
        return Component.translatable(KIND_KEY_PREFIX + kind.name().toLowerCase(Locale.ROOT));
    }

    private static final String KIND_KEY_PREFIX = "glyph_kind.isekai_mahou.";

    private PathProgress() {}
}
