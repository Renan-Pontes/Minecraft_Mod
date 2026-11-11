package com.renanpontes.isekaimahou.feature.class_gunslinger.duel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsRegistries;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.OutlawEntity;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.PathService;
import com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Duelo ao Meio-Dia (ranks III e V do Caminho do Gatilho). Só com o sol a pino (entre 10h e 14h do jogo, céu aberto
 * no mundo de cima): o Forasteiro Espectral (grau I) ou o Ceifador do Meio-Dia (grau II) aparece a 14 blocos.
 * Contagem com sino e letreiros "3... 2... 1... SAQUE!" — atirar ou conjurar antes do sinal é FALTA (o duelo acaba).
 * Acertar logo depois do sinal = Saque Perfeito (×2). Vencer grava {@code gunslinger.duel}/{@code duel2}.
 */
public final class DuelService {
    /** Duração máxima da luta depois do sinal. */
    public static final int TIME_LIMIT = 2400;
    /** Janela do Saque Perfeito (ticks depois do sinal). */
    public static final int PERFECT_DRAW = 10;
    public static final int COOLDOWN = 600;
    /** Meio-dia: dayTime 6000; vale de 10h (4000) a 14h (8000). */
    public static final int NOON_FROM = 4000;
    public static final int NOON_TO = 8000;

    /** Prefixo do motivo da derrota ({@code gone} ou {@code timeout}). */
    private static final String LOSE_KEY = "message.isekai_mahou.gunslinger.duel_lose.";

    private record Duel(UUID outlaw, long start, int tier) {}

    private static final Map<UUID, Duel> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static boolean isNoon(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && t >= NOON_FROM && t <= NOON_TO;
    }

    /** Pedido da aba do menu (ou comando). */
    public static void challenge(ServerPlayer sp, int tier) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GUNSLINGER) {
            fail(sp, Component.translatable("path.isekai_mahou.gunslinger.need_class"));
            return;
        }
        if (TriggerPath.pendingDuel(d) != tier) {
            fail(sp, Component.translatable("message.isekai_mahou.gunslinger.duel_not_needed"));
            return;
        }
        start(sp, tier, false);
    }

    /** Começa o duelo ({@code force} = comando: ignora hora e espera). */
    public static boolean start(ServerPlayer sp, int tier, boolean force) {
        ServerLevel level = sp.serverLevel();
        // duelo "preso" (o forasteiro sumiu sem avisar: chunk descarregado, comando /kill...): libera
        Duel old = ACTIVE.get(sp.getUUID());
        if (old != null && !(level.getEntity(old.outlaw()) instanceof OutlawEntity)) ACTIVE.remove(sp.getUUID());
        if (ACTIVE.containsKey(sp.getUUID())) {
            fail(sp, Component.translatable("message.isekai_mahou.gunslinger.duel_active"));
            return false;
        }
        long now = level.getGameTime();
        Long last = LAST.get(sp.getUUID());
        if (!force && last != null && now - last < COOLDOWN) {
            fail(sp, Component.translatable("message.isekai_mahou.gunslinger.duel_wait", (COOLDOWN - (now - last)) / 20 + 1));
            return false;
        }
        if (!force && (!isNoon(level) || !level.canSeeSky(sp.blockPosition().above()))) {
            fail(sp, Component.translatable("message.isekai_mahou.gunslinger.duel_noon"));
            return false;
        }
        Vec3 spot = findSpot(level, sp);
        if (spot == null) {
            fail(sp, Component.translatable("message.isekai_mahou.gunslinger.duel_space"));
            return false;
        }
        OutlawEntity o = GsRegistries.OUTLAW.get().create(level);
        if (o == null) return false;
        float yaw = (float) (Mth.atan2(sp.getZ() - spot.z, sp.getX() - spot.x) * Mth.RAD_TO_DEG) - 90F;
        o.moveTo(spot.x, spot.y, spot.z, yaw, 0);
        o.setYHeadRot(yaw);
        o.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spot)), MobSpawnType.EVENT, null);
        o.setTier(tier);
        long draw = now + 70 + level.random.nextInt(30);
        o.startDuel(sp, now, draw);
        level.addFreshEntity(o);
        ACTIVE.put(sp.getUUID(), new Duel(o.getUUID(), now, tier));
        LAST.put(sp.getUUID(), now);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 1, spot.z, 30, 0.4, 0.8, 0.4, 0.02);
        level.sendParticles(GsFx.dust(0xFFD86B, 1.2F), spot.x, spot.y + 1, spot.z, 20, 0.4, 0.8, 0.4, 0);
        GsFx.sound(level, spot, SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.7F);
        sp.sendSystemMessage(Component.translatable(tier >= 2 ? "message.isekai_mahou.gunslinger.duel_start2"
                : "message.isekai_mahou.gunslinger.duel_start").withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gunslinger.duel_rules").withStyle(ChatFormatting.GRAY));
        // letreiro de abertura já aqui (o primeiro tick do forasteiro só acontece no tick seguinte)
        title(sp, Component.translatable("title.isekai_mahou.gunslinger.duel").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable("title.isekai_mahou.gunslinger.duel.sub").withStyle(ChatFormatting.YELLOW), 50);
        GsFx.sound(level, sp.position(), SoundEvents.WOLF_HOWL, 0.4F, 0.6F);
        return true;
    }

    /** Lugar a ~14 blocos à frente (ou em volta), no chão, perto da altura do jogador. */
    @Nullable
    private static Vec3 findSpot(ServerLevel level, ServerPlayer sp) {
        float base = sp.getYRot();
        for (int i = 0; i < 12; i++) {
            float yaw = (base + (i % 2 == 0 ? 1 : -1) * (i / 2) * 30F) * Mth.DEG_TO_RAD;
            double x = sp.getX() - Mth.sin(yaw) * 14.0;
            double z = sp.getZ() + Mth.cos(yaw) * 14.0;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(y - sp.getY()) > 5) continue;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) continue;
            if (!level.getFluidState(pos.below()).isEmpty()) continue;
            return new Vec3(Mth.floor(x) + 0.5, y, Mth.floor(z) + 0.5);
        }
        return null;
    }

    /** Letreiros e sino da contagem (chamado pelo forasteiro a cada tick até o sinal). */
    public static void countdown(ServerPlayer sp, OutlawEntity o, long now) {
        Duel duel = ACTIVE.get(sp.getUUID());
        if (duel == null) return;
        long t = now - duel.start();
        if (t == 25 || t == 45 || t == 65) {
            int n = t == 25 ? 3 : t == 45 ? 2 : 1;
            title(sp, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), Component.empty(), 14);
            GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BELL_BLOCK, 1.2F, 0.8F + 0.1F * (3 - n));
        }
    }

    /** O sinal: "SAQUE!". */
    public static void draw(ServerPlayer sp, OutlawEntity o) {
        title(sp, Component.translatable("title.isekai_mahou.gunslinger.draw").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.empty(), 16);
        GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BELL_BLOCK, 1.6F, 1.4F);
        GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BELL_RESONATE, 1.0F, 1.2F);
        GsNetwork.anim(o, GsNetwork.DRAW, 0, 0xFFD86B);
    }

    /** Disparou uma arma: se for antes do sinal, falta. Devolve true se o tiro deve ser cancelado. */
    public static boolean onPlayerShot(ServerPlayer sp) {
        Duel duel = ACTIVE.get(sp.getUUID());
        if (duel == null) return false;
        if (!(sp.serverLevel().getEntity(duel.outlaw()) instanceof OutlawEntity o) || o.phase() != OutlawEntity.COUNTDOWN) return false;
        foul(sp, o);
        return true;
    }

    /** Conjurar antes do sinal também é falta. */
    public static void onCastPre(SpellCastEvent.Pre e) {
        if (!(e.getCaster() instanceof ServerPlayer sp) || !ACTIVE.containsKey(sp.getUUID())) return;
        if (onPlayerShot(sp)) e.setCanceled(true);
    }

    public static void foul(ServerPlayer sp, OutlawEntity o) {
        ACTIVE.remove(sp.getUUID());
        title(sp, Component.translatable("title.isekai_mahou.gunslinger.foul").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                Component.translatable("title.isekai_mahou.gunslinger.foul.sub").withStyle(ChatFormatting.GRAY), 40);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gunslinger.duel_foul").withStyle(ChatFormatting.RED));
        o.vanish();
    }

    public static void perfectDraw(ServerPlayer sp, OutlawEntity o) {
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.perfect_draw").withColor(0xFFD86B), true);
        GsFx.sound(sp.serverLevel(), o.position(), SoundEvents.ARROW_HIT_PLAYER, 1.0F, 0.6F);
        sp.serverLevel().sendParticles(ParticleTypes.CRIT, o.getX(), o.getEyeY(), o.getZ(), 24, 0.3, 0.3, 0.3, 0.5);
    }

    public static void win(ServerPlayer sp, OutlawEntity o) {
        Duel duel = ACTIVE.remove(sp.getUUID());
        int tier = duel != null ? duel.tier() : o.tier();
        MagicApi.addCounter(sp, tier >= 2 ? TriggerPath.DUEL2 : TriggerPath.DUEL, 1);
        MagicApi.giveXp(sp, tier >= 2 ? 900 : 300, "gunslinger_duel");
        Rewards.give(sp, new ItemStack(Items.GOLD_NUGGET, tier >= 2 ? 16 : 8));
        Rewards.give(sp, new ItemStack(CoreRegistries.ESSENCES.get(Element.FIRE).get(), tier >= 2 ? 3 : 1));
        title(sp, Component.translatable("title.isekai_mahou.gunslinger.win").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable(tier >= 2 ? "title.isekai_mahou.gunslinger.win.sub2" : "title.isekai_mahou.gunslinger.win.sub")
                        .withStyle(ChatFormatting.YELLOW), 60);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gunslinger.duel_win").withStyle(ChatFormatting.GOLD));
        GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.BELL_BLOCK, 1.4F, 0.6F);
        PathService.checkReady(sp);
    }

    /** O forasteiro venceu (desafiante caiu, fugiu ou o tempo acabou). */
    public static void lose(@Nullable ServerPlayer sp, OutlawEntity o, String reason) {
        for (Iterator<Map.Entry<UUID, Duel>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().outlaw().equals(o.getUUID())) it.remove();
        }
        if (sp == null) return;
        sp.sendSystemMessage(Component.translatable(LOSE_KEY + reason).withStyle(ChatFormatting.RED));
    }

    private static void fail(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
    }

    private static void title(ServerPlayer sp, Component title, Component sub, int stay) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(2, stay, 8));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        sp.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void forget(UUID player) {
        ACTIVE.remove(player);
        LAST.remove(player);
    }

    public static void clear() {
        ACTIVE.clear();
        LAST.clear();
    }

    private DuelService() {}
}
