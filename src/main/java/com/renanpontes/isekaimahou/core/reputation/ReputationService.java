package com.renanpontes.isekaimahou.core.reputation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Reputation.Faction;
import com.renanpontes.isekaimahou.core.api.Reputation.ItemReward;
import com.renanpontes.isekaimahou.core.api.Reputation.Standing;
import com.renanpontes.isekaimahou.core.api.Reputation.TierRewards;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.ReputationChangedEvent;
import com.renanpontes.isekaimahou.core.event.ReputationChangedEvent.Cause;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Regras da reputação no servidor: somar/definir, respingo, patamares, recompensas, títulos e limites diários. */
public final class ReputationService {
    /** Flag de título desbloqueado: {@code rep.title:<chave>}. */
    public static final String TITLE_FLAG = "rep.title:";
    /** Flag do título em uso: {@code rep.title_on:<chave>} (no máximo uma). */
    public static final String TITLE_SELECTED_FLAG = "rep.title_on:";
    /** Respingo nos rivais: −50% do ganho (× peso do rival). */
    static final float RIVAL_SHARE = 0.5F;
    /** Respingo nos aliados: +25% da mudança (× peso do aliado). */
    static final float ALLY_SHARE = 0.25F;
    /** Último valor visto de um contador com {@code counter_rewards}: {@code rep_seen.<chave>}. */
    static final String SEEN_PREFIX = "rep_seen.";
    /** Teto de "+1" pagos de uma vez (um contador que pula muito não vira uma enxurrada de reputação). */
    private static final int MAX_COUNTER_STEPS = 20;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    // =====================================================================================
    //  Somar / definir
    // =====================================================================================
    public static int add(ServerPlayer player, String factionId, int amount, @Nullable Component reason) {
        Faction f = known(factionId);
        if (f == null) return Reputation.get(player, factionId);
        if (amount != 0) {
            int delta = apply(player, f, amount, reason, Cause.DIRECT);
            if (delta != 0) spill(player, f, delta);
        }
        return Reputation.get(player, f.id());
    }

    public static void set(ServerPlayer player, String factionId, int value, @Nullable Component reason) {
        Faction f = known(factionId);
        if (f == null) return;
        apply(player, f, Reputation.clamp(value) - Reputation.get(player, f.id()), reason, Cause.SET);
    }

    /** Aplica a mudança SEM respingo. Devolve a mudança real (0 se já estava no limite). */
    private static int apply(ServerPlayer player, Faction f, int amount, @Nullable Component reason, Cause cause) {
        MagicData d = MagicApi.get(player);
        String key = Reputation.counterKey(f.id());
        int raw = d.counter(key);
        int old = Reputation.clamp(raw);
        int value = Reputation.clamp(old + amount);
        if (value != raw) MagicApi.addCounter(player, key, value - raw);
        int delta = value - old;
        if (delta == 0) return 0;

        NeoForge.EVENT_BUS.post(new ReputationChangedEvent(player, f.id(), old, value, cause, reason));
        PacketDistributor.sendToPlayer(player, new ReputationPayloads.Notify(f.id(), delta, value, Optional.ofNullable(reason)));
        Standing from = Standing.of(old), to = Standing.of(value);
        if (to.ordinal() > from.ordinal()) rose(player, d, f, from, to);
        else if (to.ordinal() < from.ordinal()) fell(player, f, from, to);
        return delta;
    }

    /**
     * Rivais perdem parte dos GANHOS; aliados acompanham parte de qualquer mudança. Não encadeia.
     * Arredonda para zero: ganhos miúdos (+1/+2 de abates) não espalham avisos por todas as facções.
     */
    private static void spill(ServerPlayer player, Faction source, int delta) {
        if (delta > 0) {
            for (Map.Entry<String, Float> e : source.rivals().entrySet()) {
                Faction rival = FactionRegistry.server().get(e.getKey());
                int s = -(int) (delta * RIVAL_SHARE * e.getValue());
                if (rival != null && s != 0) {
                    apply(player, rival, s, Component.translatable("reputation.isekai_mahou.reason.rival", source.coloredName()), Cause.SPILLOVER);
                }
            }
        }
        for (Map.Entry<String, Float> e : source.allies().entrySet()) {
            Faction ally = FactionRegistry.server().get(e.getKey());
            int s = (int) (delta * ALLY_SHARE * e.getValue());
            if (ally != null && s != 0) {
                apply(player, ally, s, Component.translatable("reputation.isekai_mahou.reason.ally", source.coloredName()), Cause.SPILLOVER);
            }
        }
    }

    @Nullable
    private static Faction known(@Nullable String id) {
        if (id == null) return null;
        Faction f = FactionRegistry.server().get(id);
        if (f == null && WARNED.add(id)) {
            IsekaiMahou.LOGGER.warn("Reputação: facção desconhecida '{}' (falta data/<ns>/factions/{}.json?)", id, id);
        }
        return f;
    }

    // =====================================================================================
    //  Subir / cair de patamar
    // =====================================================================================
    private static void rose(ServerPlayer player, MagicData d, Faction f, Standing from, Standing to) {
        List<Component> rewardLines = new ArrayList<>();
        for (Standing s : Standing.rewardTiers()) {
            if (s.ordinal() <= from.ordinal() || s.ordinal() > to.ordinal()) continue;
            String flag = Reputation.reachedFlag(f.id(), s);
            if (d.hasFlag(flag)) continue;
            d.setFlag(flag, true);
            rewardLines.addAll(grantRewards(player, d, f, s));
        }

        boolean good = to.atLeast(Standing.FRIENDLY);
        Rewards.banner(player, Component.translatable("reputation.isekai_mahou.banner.up", f.coloredName(), to.displayName()),
                good ? to.rgb : f.color());
        player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.chat.up", f.coloredName(), to.displayName())
                .withStyle(ChatFormatting.GREEN));
        if (good) {
            Rewards.toast(player, Component.translatable("reputation.isekai_mahou.toast.up", to.displayName()), f.name(), f.iconStack(), to.rgb);
            player.sendSystemMessage(f.perk(to).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (f.neutralWhenFriendly() && to.atLeast(Standing.FRIENDLY) && !from.atLeast(Standing.FRIENDLY)) {
            player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.chat.neutral_now", f.coloredName())
                    .withStyle(ChatFormatting.DARK_GREEN));
            ReputationEvents.pacify(player, f);
        }
        if (!rewardLines.isEmpty()) {
            player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.rewards.header").withStyle(ChatFormatting.GOLD));
            for (Component line : rewardLines) player.sendSystemMessage(Component.literal("  ").append(line));
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                good ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, good ? 0.8F : 0.6F, 1.1F);
    }

    private static void fell(ServerPlayer player, Faction f, Standing from, Standing to) {
        onStandingChanged(player, f.id(), to);
        player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.chat.down", f.coloredName(), to.displayName())
                .withStyle(ChatFormatting.RED));
        // Horda/Povo Selvagem abaixo de Amigável: nada muda no jogo (já atacavam) — sem banner na cara de quem só caça goblins;
        // o aviso vermelho fica no chat e no aviso lateral
        if (f.neutralWhenFriendly() && !from.atLeast(Standing.FRIENDLY)) return;
        Rewards.banner(player, Component.translatable("reputation.isekai_mahou.banner.down", f.coloredName(), to.displayName()), 0xFF5A4A);
        if (f.neutralWhenFriendly()) {
            // Horda/Povo Selvagem já atacam estranhos e não negociam: só importa perder a paz
            if (from.atLeast(Standing.FRIENDLY) && !to.atLeast(Standing.FRIENDLY)) {
                player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.warn.neutral_lost", f.coloredName())
                        .withStyle(ChatFormatting.RED));
            }
        } else if (!to.atLeast(Standing.UNFRIENDLY)) {
            player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.warn.hostile", f.coloredName())
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else if (to == Standing.UNFRIENDLY) {
            player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.warn.unfriendly", f.coloredName())
                    .withStyle(ChatFormatting.GOLD));
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    // =====================================================================================
    //  Recompensas
    // =====================================================================================
    private static List<Component> grantRewards(ServerPlayer player, MagicData d, Faction f, Standing s) {
        TierRewards r = f.rewards(s);
        List<Component> lines = new ArrayList<>();
        if (r.isEmpty()) return lines;
        if (r.xp() > 0) {
            MagicApi.giveXp(player, r.xp(), "reputation");
            lines.add(Component.translatable("reputation.isekai_mahou.rewards.xp", r.xp()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        for (ItemReward ir : r.items()) {
            ItemStack stack = ir.stack();
            if (stack.isEmpty()) continue;
            giveSplit(player, stack);
            lines.add(Component.translatable("reputation.isekai_mahou.rewards.item", stack.getCount(), stack.getHoverName())
                    .withStyle(ChatFormatting.YELLOW));
        }
        List<ResourceLocation> taught = new ArrayList<>();
        for (int i = 0; i < r.glyphScrolls(); i++) {
            Glyph g = randomGlyph(player, d, r.glyphTier(), taught);
            if (g != null) {
                taught.add(g.id());
                Rewards.give(player, GlyphScrollItem.of(g.id()));
                lines.add(Component.translatable("reputation.isekai_mahou.rewards.glyph", g.displayName()).withStyle(ChatFormatting.AQUA));
            } else {
                ItemStack crystals = new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 3);
                Rewards.give(player, crystals);
                lines.add(Component.translatable("reputation.isekai_mahou.rewards.item", 3, crystals.getHoverName())
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        if (r.legend() != null) {
            ItemStack scroll = "random".equals(r.legend())
                    ? Legends.randomScroll(player.getRandom(), d.circle())
                    : Legends.scroll(r.legend());
            if (!scroll.isEmpty()) {
                Rewards.give(player, scroll);
                lines.add(Component.translatable("reputation.isekai_mahou.rewards.legend", scroll.getHoverName()).withStyle(ChatFormatting.GOLD));
            }
        }
        if (r.title() != null) {
            grantTitle(player, r.title(), false);
            lines.add(Component.translatable("reputation.isekai_mahou.rewards.title",
                    Component.translatable(r.title()).withColor(Rewards.GOLD)).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    /** Entrega respeitando o tamanho máximo de pilha (nada de pilhas "gigantes" no chão). */
    private static void giveSplit(ServerPlayer player, ItemStack stack) {
        int left = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(max, left);
            Rewards.give(player, stack.copyWithCount(n));
            left -= n;
        }
    }

    /** Glifo que o jogador ainda não sabe e consegue usar, do maior tier disponível até {@code maxTier}. */
    @Nullable
    private static Glyph randomGlyph(ServerPlayer player, MagicData d, int maxTier, List<ResourceLocation> exclude) {
        List<Glyph> pool = new ArrayList<>();
        int best = 0;
        for (Glyph g : GlyphRegistry.all()) {
            if (g.isLegendary() || g.tier() > maxTier || g.minCircle() > d.circle() || d.knowsGlyph(g.id()) || exclude.contains(g.id())) continue;
            boolean usable = g.origin() != null ? g.origin() == d.path() : g.element() == Element.ARCANE || d.isAwakened(g.element());
            if (!usable) continue;
            if (g.tier() > best) {
                best = g.tier();
                pool.clear();
            }
            if (g.tier() == best) pool.add(g);
        }
        return pool.isEmpty() ? null : pool.get(player.getRandom().nextInt(pool.size()));
    }

    // =====================================================================================
    //  Títulos
    // =====================================================================================
    public static void grantTitle(ServerPlayer player, @Nullable String key, boolean announce) {
        if (key == null || key.isBlank() || key.length() > 200) return;
        MagicData d = MagicApi.get(player);
        if (d.hasFlag(TITLE_FLAG + key)) return;
        d.setFlag(TITLE_FLAG + key, true);
        // Primeiro título: já usa (o jogador troca na aba Reputação)
        if (Reputation.selectedTitleKey(player) == null) selectTitle(player, key);
        if (announce) {
            MutableComponent title = Component.translatable(key).withColor(Rewards.GOLD);
            player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.rewards.title", title).withStyle(ChatFormatting.GOLD));
            Rewards.toast(player, Component.translatable("reputation.isekai_mahou.toast.title"), title, ItemStack.EMPTY, Rewards.GOLD);
        }
    }

    /** Usa um título desbloqueado (null = nenhum). */
    public static boolean selectTitle(ServerPlayer player, @Nullable String key) {
        MagicData d = MagicApi.get(player);
        if (key != null && !d.hasFlag(TITLE_FLAG + key)) return false;
        List<String> old = new ArrayList<>();
        for (String flag : d.flags()) if (flag.startsWith(TITLE_SELECTED_FLAG)) old.add(flag);
        for (String flag : old) d.setFlag(flag, false);
        if (key != null) d.setFlag(TITLE_SELECTED_FLAG + key, true);
        d.markDirty();
        return true;
    }

    // =====================================================================================
    //  Limites diários, contadores, reset
    // =====================================================================================
    /** Reserva até {@code want} pontos do limite diário {@code cap} da chave {@code source}. Devolve o permitido. */
    public static int takeDaily(ServerPlayer player, String source, int want, int cap) {
        if (want <= 0 || cap <= 0) return 0;
        MagicData d = MagicApi.get(player);
        int day = (int) (player.serverLevel().getServer().overworld().getDayTime() / 24000L);
        String usedKey = "rep_daily." + source;
        String dayKey = usedKey + ".day";
        Map<String, Integer> counters = d.counters();
        if (counters.getOrDefault(dayKey, Integer.MIN_VALUE) != day) {
            counters.put(dayKey, day);
            counters.put(usedKey, 0);
        }
        int used = counters.getOrDefault(usedKey, 0);
        int grant = Math.min(want, cap - used);
        if (grant <= 0) return 0;
        counters.put(usedKey, used + grant);
        d.markDirty();
        return grant;
    }

    /**
     * Contador do MagicData mudou: facções com {@code counter_rewards} para essa chave dão reputação a cada +1 REAL.
     * O evento só traz o valor novo, então guardamos o último valor visto em {@code rep_seen.<chave>}: somas de 0,
     * correções para baixo (comandos, resets) e eventos repetidos não dão reputação.
     */
    static void onCounter(ServerPlayer player, String key, int value) {
        if (key.startsWith("rep.") || key.startsWith("rep_")) return;
        List<Faction> rewarding = new ArrayList<>(2);
        for (Faction f : FactionRegistry.server().sorted()) {
            if (f.counterRewards().containsKey(key)) rewarding.add(f);
        }
        if (rewarding.isEmpty()) return;
        MagicData d = MagicApi.get(player);
        String seenKey = SEEN_PREFIX + key;
        Integer seen = d.counters().get(seenKey);
        // 1ª vez (mundo antigo ou contador novo): conta só este +1
        int steps = seen == null ? (value > 0 ? 1 : 0) : value - seen;
        d.counters().put(seenKey, value);
        d.markDirty();
        if (steps <= 0) return;
        steps = Math.min(steps, MAX_COUNTER_STEPS);
        Component reason = Component.translatableWithFallback("reputation.isekai_mahou.reason.counter." + key, key);
        for (Faction f : rewarding) add(player, f.id(), f.counterRewards().get(key) * steps, reason);
    }

    /** Zera reputação, patamares alcançados e títulos de facção (comando de teste). */
    public static void reset(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        for (Faction f : FactionRegistry.server().sorted()) {
            String key = Reputation.counterKey(f.id());
            int raw = d.counter(key);
            if (raw != 0) MagicApi.addCounter(player, key, -raw);
            for (Standing s : Standing.values()) d.setFlag(Reputation.reachedFlag(f.id(), s), false);
        }
        List<String> drop = new ArrayList<>();
        for (String flag : d.flags()) {
            if (flag.startsWith(TITLE_FLAG) || flag.startsWith(TITLE_SELECTED_FLAG) || flag.startsWith(ALLIED_FLAG)) drop.add(flag);
        }
        for (String flag : drop) d.setFlag(flag, false);
        d.counters().keySet().removeIf(k -> k.startsWith("rep_daily."));
        d.markDirty();
    }

    // =====================================================================================
    //  Facções aliadas (v1.0 — A2)
    // =====================================================================================
    /** Flag de facção aliada escolhida pelo jogador: {@code rep.allied:<id>}. */
    public static final String ALLIED_FLAG = "rep.allied:";

    /** Facções aliadas do jogador (na ordem de exibição; só as que existem). */
    public static List<String> allied(@Nullable MagicData d) {
        List<String> out = new ArrayList<>();
        if (d == null) return out;
        for (Faction f : FactionRegistry.current().sorted()) {
            if (d.hasFlag(ALLIED_FLAG + f.id())) out.add(f.id());
        }
        return out;
    }

    /** Marca/desmarca a facção como aliada (máx. {@link Reputation#MAX_ALLIED}; só com patamar ≥ Neutro). */
    public static Reputation.AllyResult setAllied(ServerPlayer player, String factionId, boolean on) {
        Faction f = known(factionId);
        if (f == null) return Reputation.AllyResult.UNKNOWN_FACTION;
        MagicData d = MagicApi.get(player);
        String flag = ALLIED_FLAG + f.id();
        if (d.hasFlag(flag) == on) return Reputation.AllyResult.UNCHANGED;
        if (on) {
            if (!Reputation.standing(player, f.id()).atLeast(Standing.NEUTRAL)) return Reputation.AllyResult.LOW_STANDING;
            if (allied(d).size() >= Reputation.MAX_ALLIED) return Reputation.AllyResult.LIMIT;
        }
        d.setFlag(flag, on);
        d.markDirty();
        if (on) {
            player.displayClientMessage(Component.translatable("reputation.isekai_mahou.allied.on", f.coloredName())
                    .withColor(0x7CFF8A), true);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
            pacifyAllies(player, f);
        } else {
            player.displayClientMessage(Component.translatable("reputation.isekai_mahou.allied.off", f.coloredName())
                    .withStyle(ChatFormatting.GRAY), true);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.8F);
        }
        return Reputation.AllyResult.OK;
    }

    /** Ao virar aliada: membros por perto que miravam o jogador (sem terem sido atacados) desistem. */
    private static void pacifyAllies(ServerPlayer player, Faction f) {
        for (net.minecraft.world.entity.Mob mob : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(48), m -> m.getTarget() == player)) {
            if (mob.getLastHurtByMob() == player || ReputationEvents.isBossLike(mob)) continue;
            if (f.id().equals(FactionRegistry.server().factionOf(mob))) mob.setTarget(null);
        }
    }

    /** O patamar caiu abaixo de Neutro: a aliança se desfaz (com aviso). */
    static void onStandingChanged(ServerPlayer player, String factionId, Standing now) {
        if (now.atLeast(Standing.NEUTRAL)) return;
        MagicData d = MagicApi.get(player);
        if (!d.hasFlag(ALLIED_FLAG + factionId)) return;
        d.setFlag(ALLIED_FLAG + factionId, false);
        d.markDirty();
        Faction f = FactionRegistry.server().get(factionId);
        player.sendSystemMessage(Component.translatable("reputation.isekai_mahou.allied.broken",
                f != null ? f.coloredName() : Component.literal(factionId)).withStyle(ChatFormatting.RED));
    }

    static void clearState() {
        WARNED.clear();
    }

    private ReputationService() {}
}
