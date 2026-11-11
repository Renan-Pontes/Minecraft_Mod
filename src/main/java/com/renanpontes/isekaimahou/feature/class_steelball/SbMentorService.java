package com.renanpontes.isekaimahou.feature.class_steelball;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.DialogueEvent;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelballMentorEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * O Mestre itinerante: quando aparece (sozinho ou pela Carta) e o que ele diz. Visitas espontâneas: na primeira vez
 * (apresentação) e, depois, no máximo uma por dia do mundo quando há uma lição PRONTA para ser ensinada — de dia, no
 * Mundo Normal. A conversa mostra o que falta da próxima lição; com tudo pronto, ensina (sobe o rank) ou, na lição IV,
 * propõe o desafio.
 */
public final class SbMentorService {
    public static final String MET_FLAG = "steelball.mentor_met";
    public static final String VISIT_DAY = "steelball.mentor_day";
    private static final String DLG_RANK = "steelball.mentor.rank";
    private static final String DLG_DUEL = "steelball.mentor.duel";
    private static final String DLG_TALK = "steelball.mentor.talk";

    /** Jogador → Mestre que o visita agora (para não chamar dois). */
    private static final Map<UUID, UUID> VISITS = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SbMentorService::onServerTick);
        NeoForge.EVENT_BUS.addListener(SbMentorService::onDialogueFinished);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> VISITS.clear());
    }

    // =====================================================================================
    //  Visitas
    // =====================================================================================

    private static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % 200 != 37) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!sp.isAlive() || sp.isSpectator() || sp.level().dimension() != Level.OVERWORLD) continue;
            MagicData d = MagicApi.get(sp);
            if (d.path() != MagicPath.STEELBALL || Onboarding.quiet(sp)) continue;
            if (mentorNear(sp) != null || !sp.serverLevel().isDay()) continue;
            boolean intro = !d.hasFlag(MET_FLAG);
            SbProgress.Rank next = SbProgress.next(d);
            boolean lessonReady = next != null && readyButDuel(d, next);
            int day = MagicApi.worldDay(sp);
            if (!intro && (!lessonReady || d.counter(VISIT_DAY) == day)) continue;
            if (spawnNear(sp) != null) {
                MagicApi.addCounter(sp, VISIT_DAY, day - d.counter(VISIT_DAY));
                sp.displayClientMessage(Component.translatable(intro ? "message.isekai_mahou.steelball.mentor.arrive_first"
                        : "message.isekai_mahou.steelball.mentor.arrive").withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    /** Pronto para ensinar, contando o desafio da lição IV como "a fazer com ele". */
    private static boolean readyButDuel(MagicData d, SbProgress.Rank r) {
        List<MutableComponent> miss = SbProgress.missing(d, r);
        if (miss.isEmpty()) return true;
        return r.number() == 4 && miss.size() == 1 && d.counter(SbProgress.DUEL) == 0 && d.circle() >= r.circle()
                && SbPart.count(d) >= r.parts();
    }

    /** Carta do Mestre: chama agora (chega em 2 s). Devolve false se ele já está por perto. */
    public static boolean callByLetter(ServerPlayer sp) {
        if (mentorNear(sp) != null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.mentor.already").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.mentor.letter").withStyle(ChatFormatting.GOLD), true);
        SbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        BossKit.schedule(sp.serverLevel(), 40, () -> {
            if (sp.isAlive() && mentorNear(sp) == null) spawnNear(sp);
        });
        return true;
    }

    @Nullable
    public static SteelballMentorEntity mentorNear(ServerPlayer sp) {
        UUID id = VISITS.get(sp.getUUID());
        if (id != null) {
            Entity e = sp.serverLevel().getEntity(id);
            if (e instanceof SteelballMentorEntity m && m.isAlive() && m.distanceToSqr(sp) < 64 * 64) return m;
            VISITS.remove(sp.getUUID());
        }
        List<SteelballMentorEntity> list = sp.serverLevel().getEntitiesOfClass(SteelballMentorEntity.class, sp.getBoundingBox().inflate(48));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Faz o Mestre chegar a 8–14 blocos, num chão firme (perto do jogador se não achar). */
    @Nullable
    public static SteelballMentorEntity spawnNear(ServerPlayer sp) {
        ServerLevel sl = sp.serverLevel();
        SteelballMentorEntity m = SbRegistry.MENTOR.get().create(sl);
        if (m == null) return null;
        Vec3 spot = null;
        for (int i = 0; i < 16 && spot == null; i++) {
            double a = sp.getRandom().nextDouble() * Math.PI * 2;
            double r = 8 + sp.getRandom().nextDouble() * 6;
            int x = (int) Math.floor(sp.getX() + Math.cos(a) * r), z = (int) Math.floor(sp.getZ() + Math.sin(a) * r);
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - sp.getY()) > 6) continue;
            BlockPos below = new BlockPos(x, y - 1, z);
            if (!sl.getBlockState(below).isFaceSturdy(sl, below, net.minecraft.core.Direction.UP) || !sl.getFluidState(below.above()).isEmpty()) continue;
            Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
            if (sl.noCollision(m, m.getType().getDimensions().makeBoundingBox(at))) spot = at;
        }
        if (spot == null) spot = sp.position().add(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
        m.moveTo(spot.x, spot.y, spot.z, sp.getYRot() + 180F, 0);
        m.setStudent(sp.getUUID());
        if (!sl.addFreshEntity(m)) return null;
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        sl.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 1, spot.z, 24, 0.5, 0.8, 0.5, 0.03);
        SbFx.spinRing(sl, spot.add(0, 0.2, 0), SbFx.STEEL, 1.0, 16);
        SbFx.sound(sl, spot, SoundEvents.HORSE_GALLOP, 1.0F, 1.0F);
        SbFx.sound(sl, spot, SoundEvents.HORSE_AMBIENT, 0.8F, 0.9F);
        VISITS.put(sp.getUUID(), m.getUUID());
        return m;
    }

    // =====================================================================================
    //  Conversa
    // =====================================================================================

    /** Clique no Mestre: apresentação, estado da lição, ensinar ou desafiar. */
    public static void talk(ServerPlayer sp, SteelballMentorEntity mentor) {
        VISITS.put(sp.getUUID(), mentor.getUUID());
        MagicData d = MagicApi.get(sp);
        Component name = mentor.getDisplayName();
        List<Dialogue.Page> pages = new ArrayList<>();
        if (d.path() != MagicPath.STEELBALL) {
            pages.add(page("dialogue.isekai_mahou.steelball.mentor.other1"));
            pages.add(page("dialogue.isekai_mahou.steelball.mentor.other2"));
            Dialogue.show(sp, DLG_TALK, name, SbProgress.RGB, pages);
            return;
        }
        if (!d.hasFlag(MET_FLAG)) {
            d.setFlag(MET_FLAG, true);
            for (int i = 1; i <= 4; i++) pages.add(page("dialogue.isekai_mahou.steelball.mentor.intro" + i));
        }
        SbProgress.Rank next = SbProgress.next(d);
        if (next == null) {
            pages.add(page("dialogue.isekai_mahou.steelball.mentor.master1"));
            pages.add(page("dialogue.isekai_mahou.steelball.mentor.master2"));
            Dialogue.show(sp, DLG_TALK, name, SbProgress.RGB, pages);
            return;
        }
        List<MutableComponent> miss = SbProgress.missing(d, next);
        if (miss.isEmpty()) {
            pages.add(Dialogue.Page.of(Component.translatable("dialogue.isekai_mahou.steelball.mentor.ready", next.title())));
            pages.add(page("dialogue.isekai_mahou.steelball.mentor.teach" + next.number()));
            Dialogue.show(sp, DLG_RANK, name, SbProgress.RGB, pages);
            return;
        }
        if (readyButDuel(d, next)) {
            pages.add(page("dialogue.isekai_mahou.steelball.mentor.duel1"));
            pages.add(page("dialogue.isekai_mahou.steelball.mentor.duel2"));
            Dialogue.show(sp, DLG_DUEL, name, SbProgress.RGB, pages);
            return;
        }
        pages.add(Dialogue.Page.of(Component.translatable("dialogue.isekai_mahou.steelball.mentor.lesson", next.title(), next.lesson())));
        MutableComponent list = Component.translatable("dialogue.isekai_mahou.steelball.mentor.missing");
        for (MutableComponent line : miss) list.append(Component.literal("\n• ")).append(line);
        pages.add(Dialogue.Page.of(list));
        if (SbPart.count(d) < next.parts()) pages.add(page("dialogue.isekai_mahou.steelball.mentor.parts_hint"));
        else pages.add(page("dialogue.isekai_mahou.steelball.mentor.tip" + next.number()));
        Dialogue.show(sp, DLG_TALK, name, SbProgress.RGB, pages);
    }

    private static Dialogue.Page page(String key) {
        return Dialogue.Page.of(Component.translatable(key));
    }

    private static void onDialogueFinished(DialogueEvent.Finished e) {
        ServerPlayer sp = e.getPlayer();
        String id = e.getDialogueId();
        if (!id.equals(DLG_RANK) && !id.equals(DLG_DUEL)) return;
        SteelballMentorEntity mentor = mentorNear(sp);
        MagicData d = MagicApi.get(sp);
        SbProgress.Rank next = SbProgress.next(d);
        if (next == null) return;
        if (id.equals(DLG_RANK)) {
            if (!SbProgress.ready(d, next)) return;
            SbProgress.rankUp(sp);
            if (mentor != null) mentor.bow();
        } else if (mentor != null && mentor.distanceToSqr(sp) < 24 * 24 && !mentor.dueling()) {
            mentor.startDuel(sp);
        }
    }

    /** Comando/teste: tira o Mestre que visita {@code sp}. */
    public static void dismiss(ServerPlayer sp) {
        SteelballMentorEntity m = mentorNear(sp);
        if (m != null) m.leave(sp.serverLevel());
        VISITS.remove(sp.getUUID());
    }

    private SbMentorService() {}
}
