package com.renanpontes.isekaimahou.feature.ritual_warlock.ritual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.OfferingAltarBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.RitualChalkBlock;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.PatronEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.network.WarlockPayloads;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Regras do Pacto (lado do servidor): ativar o círculo, diálogo, aceitar/recusar/entregar, selar e recompensas.
 * Contadores próprios: warlock.pacts (pactos selados), warlock.pact.<patrono>, warlock.souls (almas da adaga).
 */
public final class PactRitual {
    public static final String PACTS_COUNTER = "warlock.pacts";
    /** Pactos "maiores" (com glifos e ritual_rank). Depois disso são pactos menores (só XP e dádiva). */
    public static final int MAJOR_PACTS = 10;
    /** "1 dia de jogo" para entregar o tributo. */
    public static final long PACT_DAY = 24000L;
    private static final float BLOOD_COST = 4.0F;
    /** Teto de corações cedidos somando sacrifícios simultâneos (sobram pelo menos 3 corações). */
    private static final int MAX_SACRIFICE_HEARTS = 7;
    private static final Vector3f BLOOD = new Vector3f(0.6F, 0.02F, 0.04F);

    // =====================================================================================
    //  Utilidades
    // =====================================================================================
    /** Tempo de jogo do overworld (o mesmo em todas as dimensões). */
    public static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    public static PactState state(ServerPlayer player) {
        return player.getData(WarlockContent.PACT);
    }

    public static int completedPacts(ServerPlayer player) {
        return MagicApi.get(player).counter(PACTS_COUNTER);
    }

    public static int nextPact(ServerPlayer player) {
        return completedPacts(player) + 1;
    }

    /** Dia do overworld: uma noite inteira (13000..23000) cai sempre no mesmo dia. */
    private static long day(ServerLevel level) {
        return level.getServer().overworld().getDayTime() / PACT_DAY;
    }

    /**
     * Os Patronos já atenderam este jogador nesta noite. Libera no dia seguinte ou, se o ciclo do dia
     * estiver parado, depois de um dia inteiro de jogo.
     */
    public static boolean satedTonight(ServerLevel level, PactState s) {
        if (s.lastRitualTime < 0) return false;
        return day(level) == s.lastRitualDay && now(level) - s.lastRitualTime < PACT_DAY;
    }

    /** À noite — ou em dimensões sem céu e sem ciclo (Nether, Abismo), onde é sempre "noite". */
    public static boolean isRitualTime(Level level) {
        return level.isNight() || (level.dimensionType().hasFixedTime() && !level.dimensionType().hasSkyLight());
    }

    /** Mensagem na barra de ação. {@code key} = sufixo de "message.isekai_mahou.warlock.". */
    private static void tell(ServerPlayer p, String key, ChatFormatting color, Object... args) {
        String full = "message.isekai_mahou.warlock." + key;
        p.displayClientMessage(Component.translatable(full, args).withStyle(color), true);
    }

    /** Mensagem no chat. {@code key} = sufixo de "message.isekai_mahou.warlock.". */
    private static void chat(ServerPlayer p, String key, ChatFormatting color, Object... args) {
        String full = "message.isekai_mahou.warlock." + key;
        p.sendSystemMessage(Component.translatable(full, args).withStyle(color));
    }

    // =====================================================================================
    //  Preparação
    // =====================================================================================
    /** Agachado com a mão vazia no altar: mostra o que falta para o próximo pacto. */
    public static void showGuide(ServerPlayer player, ServerLevel level, BlockPos altar) {
        int n = nextPact(player);
        CirclePattern need = CirclePattern.forPact(n);
        int chalk = need.missingChalk(level, altar);
        int candles = need.missingCandles(level, altar, need.bestCandleVariant(level, altar));
        need.showGuide(player, level, altar);
        if (chalk == 0 && candles == 0) {
            chat(player, "guide_ready", ChatFormatting.LIGHT_PURPLE, need.displayName(), n);
        } else {
            chat(player, "guide", ChatFormatting.GRAY, need.displayName(), n, chalk, candles, need.candleCount());
        }
    }

    /** Giz agachado no altar: desenha sozinho o anel de giz do próximo pacto (gasta 1 uso por bloco). */
    public static void autoDraw(ServerPlayer player, ServerLevel level, BlockPos altar, ItemStack chalk, InteractionHand hand) {
        CirclePattern need = CirclePattern.forPact(nextPact(player));
        int placed = 0;
        for (BlockPos p : need.chalkPositions(altar)) {
            if (chalk.isEmpty()) break;
            BlockState cur = level.getBlockState(p);
            if (CirclePattern.isChalk(cur) || !cur.canBeReplaced()) continue;
            BlockState st = WarlockContent.RITUAL_CHALK.get().defaultBlockState().setValue(RitualChalkBlock.RUNE, RitualChalkBlock.runeFor(p));
            if (!st.canSurvive(level, p)) continue;
            level.setBlock(p, st, 3);
            level.sendParticles(Vfx.whiteAsh(), p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.01);
            placed++;
            if (!player.getAbilities().instabuild) chalk.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        if (placed > 0) level.playSound(null, altar, SoundEvents.CALCITE_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);
        tell(player, "auto_drawn", ChatFormatting.GRAY, placed, need.displayName());
        need.showGuide(player, level, altar);
    }

    // =====================================================================================
    //  Ativação (Adaga Ritual no altar)
    // =====================================================================================
    public static void activate(ServerPlayer player, ServerLevel level, BlockPos altar, OfferingAltarBlockEntity be) {
        int n = nextPact(player);
        if (be.isOccupied(level)) {
            tell(player, "already_bound", ChatFormatting.RED);
            return;
        }
        PactState s = state(player);
        if (s.active() && !s.failed) {
            tell(player, "pending_elsewhere", ChatFormatting.RED, s.kind.displayName());
            return;
        }
        if (!isRitualTime(level)) {
            tell(player, "not_night", ChatFormatting.DARK_PURPLE);
            return;
        }
        if (!player.getAbilities().instabuild && satedTonight(level, s)) {
            tell(player, "sated", ChatFormatting.DARK_PURPLE);
            return;
        }
        ItemStack offering = be.getOffering();
        PatronKind kind = PatronKind.forOffering(offering);
        if (kind == null) {
            tell(player, "no_offering", ChatFormatting.GRAY);
            return;
        }
        CirclePattern need = CirclePattern.forPact(n);
        CirclePattern have = CirclePattern.largestComplete(level, altar);
        if (have == null || have.ordinal() < need.ordinal()) {
            int chalk = need.missingChalk(level, altar);
            int candles = need.missingCandles(level, altar, need.bestCandleVariant(level, altar));
            need.showGuide(player, level, altar);
            chat(player, "circle_incomplete", ChatFormatting.RED, need.displayName(), n, chalk, candles, need.candleCount());
            return;
        }
        if (!player.getAbilities().instabuild && player.getHealth() <= BLOOD_COST + 0.5F) {
            tell(player, "too_weak", ChatFormatting.RED);
            return;
        }

        // Sangue: 2 corações.
        player.hurt(level.damageSources().magic(), BLOOD_COST);
        DustParticleOptions blood = new DustParticleOptions(BLOOD, 1.3F);
        level.sendParticles(blood, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.4, 0.3, 0.0);
        Vec3 top = Vec3.atBottomCenterOf(altar).add(0, 0.9, 0);
        level.sendParticles(blood, top.x, top.y, top.z, 30, 0.3, 0.1, 0.3, 0.0);
        level.playSound(null, altar, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.5F, 0.6F);

        PatronEntity patron = kind.entityType().create(level);
        if (patron == null) return;
        ItemStack consumed = be.takeOffering();
        boolean favor = consumed.is(PatronKind.GENEROUS);
        have.setBlood(level, altar, true);
        s.lastRitualDay = day(level);
        s.lastRitualTime = now(level);
        // Tributo antigo já falhado (Patrono num chunk descarregado): fica abandonado; ele se liberta ao carregar.
        if (s.failed) {
            s.clearPact();
            sendHud(player);
        }

        patron.setupRitual(player, altar, have, n, favor);
        level.addFreshEntity(patron);
        be.setPatron(patron.getUUID());
        // Pequeno atraso: garante que o cliente já recebeu a entidade-foco da cutscene.
        // A cena acaba um pouco antes de o Patrono terminar de surgir (e abrir o diálogo).
        SpellScheduler.schedule(2, () -> {
            if (patron.isAlive() && !player.isRemoved()) {
                Cutscenes.playNear(patron, 32, "patron", kind.displayName(), kind.epithet(), kind.color,
                        PatronEntity.SUMMON_TICKS - 4, player);
            }
        });
        chat(player, "ritual_begins", ChatFormatting.DARK_RED, kind.displayName());
    }

    // =====================================================================================
    //  Diálogo
    // =====================================================================================
    public static void openDialog(ServerPlayer player, PatronEntity patron) {
        if (!(patron.level() instanceof ServerLevel level)) return;
        PatronEntity.Phase phase = patron.phase();
        if (phase != PatronEntity.Phase.OFFERING && phase != PatronEntity.Phase.PENDING) return;
        PatronKind kind = patron.kind();
        PatronDemand d = patron.demand();
        if (d == null) return;
        long now = now(level);

        if (!patron.isOwner(player)) {
            send(player, new WarlockPayloads.PatronDialog(patron.getId(), kind.id, WarlockPayloads.MODE_STRANGER, patron.pactNumber(),
                    patron.lineText("stranger"), d.type().ordinal(), Component.empty(), Component.empty(), ItemStack.EMPTY, -1));
            return;
        }
        if (phase == PatronEntity.Phase.OFFERING) {
            Component detail;
            if (d.isSacrifice()) detail = Component.translatable("gui.isekai_mahou.warlock.paid_now");
            else if (d.isTracked()) detail = Component.translatable("gui.isekai_mahou.warlock.deadline_minutes", d.minutes());
            else detail = Component.translatable("gui.isekai_mahou.warlock.deadline_day");
            if (patron.favor()) detail = Component.empty().append(detail).append(" ").append(Component.translatable("gui.isekai_mahou.warlock.favor"));
            int patience = (int) Math.max(0, patron.offerDeadline() - now);
            send(player, new WarlockPayloads.PatronDialog(patron.getId(), kind.id, WarlockPayloads.MODE_OFFER, patron.pactNumber(),
                    patron.lineText("demand"), d.type().ordinal(), d.describe(), detail, d.icon(), patience));
        } else {
            PactState s = state(player);
            Component detail;
            if (d.isTracked()) {
                int prog = Math.min(d.amount(), progress(player, s));
                long left = s.fulfilled ? patron.deadline() - now : s.windowEnd - now;
                detail = Component.translatable(s.fulfilled ? "gui.isekai_mahou.warlock.progress_done" : "gui.isekai_mahou.warlock.progress",
                        prog, d.amount(), formatTicks(left));
            } else {
                detail = Component.translatable("gui.isekai_mahou.warlock.have_items", Math.min(d.amount(), d.countInInventory(player)),
                        d.amount(), formatTicks(patron.deadline() - now));
            }
            send(player, new WarlockPayloads.PatronDialog(patron.getId(), kind.id, WarlockPayloads.MODE_PENDING, patron.pactNumber(),
                    patron.lineText("waiting"), d.type().ordinal(), d.describe(), detail, d.icon(), -1));
        }
    }

    private static void send(ServerPlayer player, WarlockPayloads.PatronDialog payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** "m:ss" a partir de ticks. */
    public static String formatTicks(long ticks) {
        long secs = Math.max(0, ticks / 20);
        return (secs / 60) + ":" + String.format("%02d", secs % 60);
    }

    /** Resposta do jogador na tela de diálogo. */
    public static void answer(ServerPlayer player, int entityId, int action) {
        Entity e = player.level().getEntity(entityId);
        if (!(e instanceof PatronEntity patron) || !patron.isOwner(player) || player.distanceToSqr(patron) > 20 * 20) return;
        switch (action) {
            case WarlockPayloads.ANSWER_ACCEPT -> {
                if (patron.phase() == PatronEntity.Phase.OFFERING) accept(player, patron);
            }
            case WarlockPayloads.ANSWER_REFUSE -> {
                if (patron.phase() == PatronEntity.Phase.OFFERING) patron.breakFree(PatronEntity.Release.REFUSED);
            }
            case WarlockPayloads.ANSWER_DELIVER -> {
                if (patron.phase() == PatronEntity.Phase.PENDING) deliver(player, patron);
            }
            default -> { }
        }
    }

    private static void accept(ServerPlayer player, PatronEntity patron) {
        ServerLevel level = (ServerLevel) patron.level();
        PatronDemand d = patron.demand();
        PactState s = state(player);
        long now = now(level);
        switch (d.type()) {
            case HEARTS -> {
                // Sacrifícios simultâneos se somam (senão um 2º pacto de corações sairia de graça).
                boolean ongoing = s.sacrificeHearts > 0 && s.sacrificeUntil > now;
                s.sacrificeHearts = Math.min(MAX_SACRIFICE_HEARTS, (ongoing ? s.sacrificeHearts : 0) + d.amount());
                s.sacrificeUntil = Math.max(ongoing ? s.sacrificeUntil : 0, now + d.minutes() * 1200L);
                PactBoons.applySacrifice(player, s);
                level.sendParticles(new DustParticleOptions(BLOOD, 1.5F), player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.3, 0.5, 0.3, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.8F, 0.6F);
                chat(player, "sacrifice_hearts", ChatFormatting.DARK_RED, d.amount(), d.minutes());
                seal(player, patron);
            }
            case LEVELS -> {
                if (!player.getAbilities().instabuild && player.experienceLevel < d.amount()) {
                    tell(player, "sacrifice_cannot", ChatFormatting.RED, d.amount());
                    openDialog(player, patron);
                    return;
                }
                if (!player.getAbilities().instabuild) player.giveExperienceLevels(-d.amount());
                level.sendParticles(Vfx.enchant(), player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.8, 0.5, 1.0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 0.4F);
                chat(player, "sacrifice_levels", ChatFormatting.DARK_RED, d.amount());
                seal(player, patron);
            }
            default -> {
                long deadline = now + PACT_DAY;
                s.clearPact();
                s.patron = patron.getUUID();
                s.kind = patron.kind();
                s.pactNumber = patron.pactNumber();
                s.demand = d;
                s.deadline = deadline;
                s.windowEnd = d.isTracked() ? now + d.minutes() * 1200L : deadline;
                s.baseline = d.isTracked() ? MagicApi.get(player).counter(d.counterKey()) : 0;
                s.altar = patron.altarPos();
                patron.acceptPending(deadline);
                chat(player, "accepted", ChatFormatting.LIGHT_PURPLE, patron.kind().displayName(), d.describe());
                sendHud(player);
            }
        }
    }

    private static void deliver(ServerPlayer player, PatronEntity patron) {
        PatronDemand d = patron.demand();
        PactState s = state(player);
        if (d.type() == PatronDemand.Type.ITEM) {
            int have = d.countInInventory(player);
            if (have >= d.amount() || player.getAbilities().instabuild) {
                if (!player.getAbilities().instabuild) d.takeFromInventory(player);
                seal(player, patron);
            } else {
                patron.speak("missing");
                tell(player, "deliver_missing", ChatFormatting.RED, d.amount() - have, d.targetName());
            }
        } else if (d.isTracked()) {
            if (s.fulfilled) {
                seal(player, patron);
            } else {
                patron.speak("missing");
                tell(player, "deliver_progress", ChatFormatting.RED, Math.min(d.amount(), progress(player, s)), d.amount());
            }
        }
    }

    /** Progresso de um tributo com contador (abates/almas desde que o pacto foi aceito). */
    public static int progress(ServerPlayer player, PactState s) {
        if (!s.active() || !s.demand.isTracked()) return 0;
        return Math.max(0, MagicApi.get(player).counter(s.demand.counterKey()) - s.baseline);
    }

    // =====================================================================================
    //  Fim do pacto
    // =====================================================================================
    /** Tributo cumprido: o Patrono parte e o pacto é selado. */
    public static void seal(ServerPlayer player, PatronEntity patron) {
        PactState s = state(player);
        if (patron.getUUID().equals(s.patron)) s.clearPact();
        PatronKind kind = patron.kind();
        int n = patron.pactNumber();
        patron.sealPact();
        rewards(player, kind, n);
        sendHud(player);
    }

    private static void rewards(ServerPlayer player, PatronKind kind, int n) {
        MagicData d = MagicApi.get(player);
        boolean major = completedPacts(player) < MAJOR_PACTS;
        int xp = 150 + 120 * Math.min(n, MAJOR_PACTS);
        MagicApi.giveXp(player, major ? xp : xp / 2, "ritual");
        MagicApi.addCounter(player, PACTS_COUNTER, 1);
        MagicApi.addCounter(player, "warlock.pact." + kind.id, 1);
        int done = completedPacts(player);
        chat(player, major ? "pact_sealed" : "pact_sealed_minor", ChatFormatting.GOLD, kind.displayName(), done);

        if (major) {
            MagicApi.addCounter(player, "ritual_rank", 1);
            teachGlyphs(player, Math.max(1, Math.min(5, (done + 1) / 2)));
        }
        // O pacto desperta as trevas (1º) e o fogo infernal (3º).
        if (!d.isAwakened(Element.SHADOW)) MagicApi.awakenElement(player, Element.SHADOW);
        if (done >= 3 && !d.isAwakened(Element.FIRE)) MagicApi.awakenElement(player, Element.FIRE);
        if (d.isAwakened(kind.element)) MagicApi.giveElementXp(player, kind.element, 30 + 15 * Math.min(n, MAJOR_PACTS));

        int boon = PactBoons.level(player, kind);
        PactBoons.refresh(player);
        chat(player, "boon", ChatFormatting.LIGHT_PURPLE, kind.boonName(), boon,
                kind.boonDesc(boon));

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.sendParticles(Vfx.totem(), player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.5, 0.8, 0.5, 0.4);
    }

    /** Ensina até 2 glifos de Origem WARLOCK (Pacto) do tier do pacto (ou de tiers anteriores que faltem). */
    private static void teachGlyphs(ServerPlayer player, int tier) {
        MagicData d = MagicApi.get(player);
        List<Glyph> options = new ArrayList<>();
        for (Glyph g : GlyphRegistry.all()) {
            if (g.origin() == MagicPath.WARLOCK && g.tier() <= tier && !d.knowsGlyph(g.id())) options.add(g);
        }
        options.sort(Comparator.comparingInt((Glyph g) -> -g.tier()).thenComparing(g -> g.id().toString()));
        int taught = 0;
        for (Glyph g : options) {
            if (taught >= 2) break;
            if (MagicApi.learnGlyph(player, g.id())) {
                taught++;
                chat(player, "glyph_taught", ChatFormatting.AQUA, g.displayName());
            }
        }
        if (taught == 0) {
            MagicApi.giveXp(player, 80, "ritual");
            chat(player, "no_glyphs", ChatFormatting.GRAY);
        }
    }

    /** O Patrono se libertou: o pacto pendente do dono acaba. */
    public static void onPactBroken(ServerPlayer player, PatronEntity patron, PatronEntity.Release reason) {
        PactState s = state(player);
        if (patron.getUUID().equals(s.patron)) s.clearPact();
        chat(player, "broken_" + reason.key, ChatFormatting.DARK_RED, patron.kind().displayName());
        sendHud(player);
    }

    /** Solta o altar (o Patrono saiu do círculo). */
    public static void clearAltar(ServerLevel level, BlockPos altar, UUID patron) {
        if (level.isLoaded(altar) && level.getBlockEntity(altar) instanceof OfferingAltarBlockEntity be && be.isPatron(patron)) {
            be.setPatron(null);
        }
    }

    // =====================================================================================
    //  HUD
    // =====================================================================================
    public static void sendHud(ServerPlayer player) {
        PactState s = state(player);
        if (!s.active()) {
            PacketDistributor.sendToPlayer(player, WarlockPayloads.PactHud.empty());
            return;
        }
        long now = now(player.serverLevel());
        boolean tracked = s.demand.isTracked();
        long end = tracked && !s.fulfilled ? s.windowEnd : s.deadline;
        int prog = tracked ? Math.min(s.demand.amount(), progress(player, s)) : Math.min(s.demand.amount(), s.demand.countInInventory(player));
        PacketDistributor.sendToPlayer(player, new WarlockPayloads.PactHud(true, s.kind.id, s.demand.describe(), prog,
                s.demand.amount(), (int) Math.max(0, Math.min(Integer.MAX_VALUE, end - now)), s.fulfilled, tracked));
    }

    private PactRitual() {}
}
