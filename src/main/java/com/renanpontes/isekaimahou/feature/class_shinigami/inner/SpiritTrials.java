package com.renanpontes.isekaimahou.feature.class_shinigami.inner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.event.DialogueEvent;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiNetwork;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiRanks;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiTechniques;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ZanpakutoSpiritEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Conversas e provas com o espírito da espada (no Mundo Interior). Clicar no espírito segue o Caminho da Zanpakutō:
 * <ol>
 *   <li>rank I — Despertar: ele fala pela primeira vez (diálogo) → rank I;</li>
 *   <li>rank II — O Nome: diálogo → a tela de escolha do arquétipo → a paisagem muda e ele diz o nome → rank II;</li>
 *   <li>rank III — Shikai: diálogo → LUTA (o espírito se rende abaixo de 10%) → rank III;</li>
 *   <li>rank IV — Mestre do Kidō: diálogo → rank IV;</li>
 *   <li>rank V — Bankai: diálogo → a luta contra o espírito materializado (3 fases) → rank V.</li>
 * </ol>
 * Técnicas avançadas (rank IV+): quando os requisitos chegam, o <b>Vazio Interior</b> toma o espírito e luta (vencer
 * ensina a Máscara do Vazio e o Cero); no rank V o espírito ensina a última técnica, o <b>Mugetsu</b>.
 * Sem rank pronto: ele diz o que falta (ou conversa). Agachado + clicar = treino (luta sem prêmio, com XP 1× por dia).
 * Perder (a "morte" no Mundo Interior é cancelada) só leva de volta à praça. Diálogos pelo núcleo ({@link Dialogue}).
 * v1.3 ({@link HollowFate}): antes da luta contra o Vazio Interior o jogador escolhe lutar ou ACEITAR o hollow interior;
 * MORRER nessa luta não volta à praça — o Vazio toma o corpo e o Shinigami vira a classe Hollow (módulo class_hollow).
 */
public final class SpiritTrials {
    public static final int SHIKAI = 1;
    public static final int BANKAI = 2;
    public static final int SPAR = 3;
    public static final int HOLLOW = 4;
    private static final String DLG = "shinigami.";
    private static final String KEY = "dialogue.isekai_mahou.shinigami.";

    private static final class Trial {
        final int kind;
        final UUID spirit;
        final ServerLevel level;
        final ServerBossEvent bar;
        final String arena;

        Trial(int kind, UUID spirit, ServerLevel level, ServerBossEvent bar, String arena) {
            this.kind = kind;
            this.spirit = spirit;
            this.level = level;
            this.bar = bar;
            this.arena = arena;
        }
    }

    private static final Map<UUID, Trial> TRIALS = new HashMap<>();
    /** Ação a fazer quando um diálogo do jogador terminar (lido ou pulado). */
    private static final Map<UUID, Map<String, Runnable>> AFTER = new HashMap<>();
    /** Quem está escolhendo o arquétipo agora (liberado pelo diálogo do rank II). */
    private static final Set<UUID> CHOOSING = new HashSet<>();

    public static boolean inTrial(ServerPlayer sp) {
        return TRIALS.containsKey(sp.getUUID());
    }

    public static boolean choosing(ServerPlayer sp) {
        return CHOOSING.contains(sp.getUUID());
    }

    // =====================================================================================
    //  Conversa
    // =====================================================================================
    public static void talk(ServerPlayer sp, ZanpakutoSpiritEntity spirit, boolean sneaking) {
        if (Dialogue.isBusy(sp) || inTrial(sp)) return;
        MagicData d = MagicApi.get(sp);
        if (!Shinigami.isShinigami(sp)) {
            show(sp, spirit, "foreign", null, page("foreign.1"));
            return;
        }
        int rank = Shinigami.rank(d);
        spirit.talkFor(120);
        if (sneaking && rank >= 3) {
            show(sp, spirit, "spar", () -> start(sp, spirit, SPAR), page("spar.1", sp.getDisplayName()));
            return;
        }
        ShinigamiRanks.Rank next = ShinigamiRanks.rank(rank + 1);
        List<Component> missing = new ArrayList<>();
        boolean rankReady = next != null && ShinigamiRanks.meets(d, next, missing);
        if (!rankReady) {
            // técnicas avançadas: o Vazio Interior e a última lição
            if (ShinigamiTechniques.hollowReady(d)) {
                // v1.3: antes da luta, a escolha — lutar ou ACEITAR o hollow interior (vira a classe Hollow)
                show(sp, spirit, "hollow", () -> HollowFate.offer(sp, () -> start(sp, spirit, HOLLOW)),
                        page("hollow.1"), page("hollow.2", sp.getDisplayName()), page("hollow.3"));
                return;
            }
            if (ShinigamiTechniques.mugetsuReady(d)) {
                show(sp, spirit, "mugetsu", () -> ShinigamiTechniques.teachMugetsu(sp),
                        page("mugetsu.1", sp.getDisplayName()), page("mugetsu.2"), page("mugetsu.3"), page("mugetsu.4"));
                return;
            }
            if (next == null) idle(sp, spirit, d);
            else hint(sp, spirit, rank, missing);
            return;
        }
        switch (next.number()) {
            case 1 -> show(sp, spirit, "rank1", () -> ShinigamiRanks.grant(sp, next),
                    page("rank1.1"), page("rank1.2", sp.getDisplayName()), page("rank1.3"));
            case 2 -> show(sp, spirit, "rank2", () -> {
                        CHOOSING.add(sp.getUUID());
                        PacketDistributor.sendToPlayer(sp, new ShinigamiNetwork.OpenArchetypes());
                    },
                    page("rank2.1"), page("rank2.2"), page("rank2.3"));
            case 3 -> show(sp, spirit, "rank3", () -> start(sp, spirit, SHIKAI),
                    page("rank3.1", Shinigami.spiritName(d)), page("rank3.2"));
            case 4 -> show(sp, spirit, "rank4", () -> ShinigamiRanks.grant(sp, next),
                    page("rank4.1"), page("rank4.2"), page("rank4.3"));
            case 5 -> show(sp, spirit, "rank5", () -> start(sp, spirit, BANKAI),
                    page("rank5.1"), page("rank5.2", Shinigami.spiritName(d)), page("rank5.3"));
            default -> idle(sp, spirit, d);
        }
    }

    private static void hint(ServerPlayer sp, ZanpakutoSpiritEntity spirit, int rank, List<Component> missing) {
        MutableComponent list = Component.empty();
        int n = 0;
        for (Component c : missing) {
            if (n++ >= 4) break;
            if (n > 1) list.append("\n");
            list.append(Component.literal("• ").append(c));
        }
        show(sp, spirit, "hint", null, page("hint." + Math.min(rank, 4)), Dialogue.Page.of(Component.translatable(KEY + "hint.missing", list)));
    }

    private static void idle(ServerPlayer sp, ZanpakutoSpiritEntity spirit, MagicData d) {
        int line = 1 + sp.getRandom().nextInt(6);
        show(sp, spirit, "idle", null, page("idle." + line, sp.getDisplayName()), page("idle.exit"));
    }

    private static Component page(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    private static void show(ServerPlayer sp, ZanpakutoSpiritEntity spirit, String step, @Nullable Runnable after, Object... pages) {
        List<Dialogue.Page> list = new ArrayList<>();
        for (Object o : pages) {
            if (o instanceof Dialogue.Page p) list.add(p);
            else if (o instanceof Component c) list.add(Dialogue.Page.of(c));
        }
        String id = DLG + step;
        if (after != null) AFTER.computeIfAbsent(sp.getUUID(), k -> new HashMap<>()).put(id, after);
        MagicData d = MagicApi.get(sp);
        Dialogue.show(sp, id, Shinigami.spiritName(d), Shinigami.spiritRgb(d), list);
        ShinigamiFx.sound(sp.serverLevel(), spirit.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.7F);
    }

    /** Fim de um diálogo: roda a ação guardada (mesmo se o jogador pulou o texto). */
    public static void onDialogueFinished(DialogueEvent.Finished e) {
        String id = e.getDialogueId();
        if (!id.startsWith(DLG)) return;
        ServerPlayer sp = e.getPlayer();
        Map<String, Runnable> m = AFTER.get(sp.getUUID());
        if (m == null) return;
        Runnable r = m.remove(id);
        if (m.isEmpty()) AFTER.remove(sp.getUUID());
        if (r != null && sp.isAlive()) r.run();
    }

    // =====================================================================================
    //  Escolha do arquétipo (rank II)
    // =====================================================================================
    public static void choose(ServerPlayer sp, Archetype a) {
        if (!CHOOSING.remove(sp.getUUID())) {
            sp.displayClientMessage(Shinigami.msg("choose_not_now").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        MagicData d = MagicApi.get(sp);
        ShinigamiRanks.Rank r2 = ShinigamiRanks.rank(2);
        if (Shinigami.rank(d) != 1 || r2 == null || !ShinigamiRanks.meets(d, r2, null)) return;
        MagicApi.addCounter(sp, Shinigami.ARCHETYPE, a.counterValue() - d.counter(Shinigami.ARCHETYPE));
        ShinigamiRanks.grant(sp, r2);
        InnerWorld.reshape(sp);
        ZanpakutoSpiritEntity spirit = InnerWorld.spirit(sp);
        if (spirit != null) {
            spirit.setArchetype(a.counterValue());
            ServerLevel sl = sp.serverLevel();
            ShinigamiFx.pillar(sl, spirit.position(), 6.0, 80, ShinigamiFx.dust(a.rgb, 1.5F));
            ShinigamiFx.signature(sl, a, spirit.position().add(0, 1.2, 0), 50, 1.5);
            show(sp, spirit, "named", null, page("named.1", a.spiritName()), page("named.2." + a.id, a.spiritName(), a.command()),
                    page("named.3"));
        }
    }

    // =====================================================================================
    //  Provas
    // =====================================================================================
    private static void start(ServerPlayer sp, ZanpakutoSpiritEntity spirit, int kind) {
        if (inTrial(sp) || !spirit.isAlive() || !InnerWorld.inOwn(sp)) return;
        InnerWorldData.Entry e = InnerWorld.entry(sp);
        if (e == null) return;
        MagicData d = MagicApi.get(sp);
        Archetype a = Shinigami.archetype(d);
        MutableComponent name = Shinigami.spiritName(d).copy();
        if (kind == BANKAI) name = Shinigami.msg("spirit_materialized", name);
        if (kind == HOLLOW) name = Shinigami.msg("hollow_name").withColor(0xF2EEE6);
        BossEvent.BossBarColor color = kind == BANKAI || kind == HOLLOW ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE;
        BossEvent.BossBarOverlay overlay = kind == BANKAI ? BossEvent.BossBarOverlay.NOTCHED_10
                : kind == HOLLOW ? BossEvent.BossBarOverlay.NOTCHED_6 : BossEvent.BossBarOverlay.PROGRESS;
        ServerBossEvent bar = new ServerBossEvent(name, color, overlay);
        bar.addPlayer(sp);
        String arenaId = "shinigami_inner_" + e.index;
        ServerLevel level = sp.serverLevel();
        Vec3 c = InnerWorld.center(e.index);
        ArenaBounds.register(level, arenaId, c, InnerWorldBuilder.RADIUS - 2, Shinigami.spiritRgb(d), Shinigami.msg("arena_name"));
        TRIALS.put(sp.getUUID(), new Trial(kind, spirit.getUUID(), level, bar, arenaId));
        if (kind == HOLLOW) spirit.becomeHollow();
        spirit.startTrial(sp, kind);
        sp.setHealth(sp.getMaxHealth());
        ShinigamiFx.pillar(level, spirit.position(), 8.0, 90, ShinigamiFx.dust(Shinigami.spiritRgb(d), 1.5F));
        ShinigamiFx.sound(level, spirit.position(), kind == BANKAI || kind == HOLLOW ? SoundEvents.WITHER_SPAWN : SoundEvents.EVOKER_PREPARE_ATTACK,
                kind == BANKAI || kind == HOLLOW ? 0.6F : 1.0F, kind == HOLLOW ? 0.5F : 0.8F);
        sp.sendSystemMessage(Shinigami.msg("trial_start." + kind, Shinigami.spiritName(d)).withColor(a != null ? a.rgb : Shinigami.RGB));
    }

    /** O espírito se rendeu: prova vencida. */
    public static void onYield(ZanpakutoSpiritEntity spirit) {
        UUID ownerId = spirit.owner();
        if (ownerId == null) return;
        Trial t = TRIALS.get(ownerId);
        ServerPlayer sp = spirit.getServer() == null ? null : spirit.getServer().getPlayerList().getPlayer(ownerId);
        if (t == null || sp == null) return;
        end(sp, t);
        sp.setHealth(sp.getMaxHealth());
        cleanse(sp);
        MagicData d = MagicApi.get(sp);
        switch (t.kind) {
            case SHIKAI -> show(sp, spirit, "rank3_won", () -> {
                ShinigamiRanks.Rank r = ShinigamiRanks.rank(3);
                if (r != null && Shinigami.rank(MagicApi.get(sp)) == 2) ShinigamiRanks.grant(sp, r);
            }, page("rank3_won.1"), page("rank3_won.2", Shinigami.archetype(d) == null ? Shinigami.spiritName(d) : Shinigami.archetype(d).command()));
            case BANKAI -> show(sp, spirit, "rank5_won", () -> {
                ShinigamiRanks.Rank r = ShinigamiRanks.rank(5);
                if (r != null && Shinigami.rank(MagicApi.get(sp)) == 4) ShinigamiRanks.grant(sp, r);
            }, page("rank5_won.1"), page("rank5_won.2", Shinigami.archetype(d) == null ? Shinigami.spiritName(d) : Shinigami.archetype(d).bankaiName()));
            case HOLLOW -> show(sp, spirit, "hollow_won", () -> ShinigamiTechniques.tameHollow(sp),
                    page("hollow_won.1"), page("hollow_won.2", sp.getDisplayName()), page("hollow_won.3"));
            default -> {
                boolean reward = MagicApi.tryUseDaily(sp, "shinigami_spar", 1);
                if (reward) MagicApi.giveXp(sp, 250, "ritual");
                show(sp, spirit, "spar_won", null, page(reward ? "spar_won.1" : "spar_won.2"));
            }
        }
    }

    /** O jogador "morreu" (cancelado) ou caiu durante a prova: perdeu. */
    public static void fail(ServerPlayer sp) {
        Trial t = TRIALS.get(sp.getUUID());
        if (t == null) return;
        end(sp, t);
        ZanpakutoSpiritEntity spirit = InnerWorld.spirit(sp);
        if (spirit != null) {
            spirit.calm();
            show(sp, spirit, "fail", null, page("fail." + (t.kind == SPAR ? 3 : t.kind == HOLLOW ? 4 : t.kind)));
        }
        sp.displayClientMessage(Shinigami.msg("trial_failed").withStyle(ChatFormatting.RED), true);
    }

    /** Saiu do Mundo Interior / deslogou: encerra sem mensagem. */
    public static void abort(ServerPlayer sp) {
        Trial t = TRIALS.get(sp.getUUID());
        if (t == null) return;
        end(sp, t);
        ZanpakutoSpiritEntity spirit = InnerWorld.spirit(sp);
        if (spirit != null) spirit.calm();
    }

    /** O espírito perdeu o alvo (o dono sumiu). */
    public static void spiritLost(ZanpakutoSpiritEntity spirit) {
        UUID ownerId = spirit.owner();
        if (ownerId == null) return;
        Trial t = TRIALS.remove(ownerId);
        if (t != null) {
            t.bar.removeAllPlayers();
            ArenaBounds.unregister(t.level, t.arena);
        }
    }

    private static void end(ServerPlayer sp, Trial t) {
        TRIALS.remove(sp.getUUID());
        t.bar.removeAllPlayers();
        ArenaBounds.unregister(t.level, t.arena);
    }

    private static void cleanse(ServerPlayer sp) {
        List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance inst : sp.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(inst.getEffect());
        }
        for (var h : bad) sp.removeEffect(h);
        sp.clearFire();
    }

    /**
     * "Morte" no Mundo Interior: cancelada; volta à praça com metade da vida. v1.3: morrer para o VAZIO INTERIOR é
     * diferente — o Hollow toma conta ({@link HollowFate}): a máscara cobre o rosto e o Shinigami vira Hollow.
     */
    public static void onFatal(ServerPlayer sp) {
        Trial t = TRIALS.get(sp.getUUID());
        // o gancho só agenda a hollowificação (a cena começa no próximo tick, já sem a prova)
        if (t != null && t.kind == HOLLOW && HollowFate.defeated(sp)) {
            end(sp, t);
            sp.setHealth(Math.max(1F, sp.getMaxHealth() * 0.5F));
            cleanse(sp);
            ZanpakutoSpiritEntity spirit = InnerWorld.spirit(sp);
            if (spirit != null) spirit.calm();
            return;
        }
        fail(sp);
        sp.setHealth(Math.max(1F, sp.getMaxHealth() * 0.5F));
        cleanse(sp);
        InnerWorld.toArrival(sp);
        ShinigamiFx.sound(sp.serverLevel(), sp.position(), SoundEvents.TOTEM_USE, 0.6F, 1.4F);
    }

    public static void tick(MinecraftServer server) {
        if (TRIALS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Trial>> it = TRIALS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Trial> me = it.next();
            Trial t = me.getValue();
            if (server.getTickCount() % 5 != 0) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(me.getKey());
            if (!(t.level.getEntity(t.spirit) instanceof ZanpakutoSpiritEntity spirit) || sp == null || sp.level() != t.level) {
                t.bar.removeAllPlayers();
                ArenaBounds.unregister(t.level, t.arena);
                it.remove();
                continue;
            }
            t.bar.setProgress(Math.max(0F, Math.min(1F, (spirit.getHealth() - spirit.getMaxHealth() * 0.1F) / (spirit.getMaxHealth() * 0.9F))));
        }
    }

    public static void forget(UUID id) {
        Trial t = TRIALS.remove(id);
        if (t != null) {
            t.bar.removeAllPlayers();
            ArenaBounds.unregister(t.level, t.arena);
        }
        AFTER.remove(id);
        CHOOSING.remove(id);
    }

    public static void clear() {
        for (Trial t : TRIALS.values()) t.bar.removeAllPlayers();
        TRIALS.clear();
        AFTER.clear();
        CHOOSING.clear();
    }

    private SpiritTrials() {}
}
