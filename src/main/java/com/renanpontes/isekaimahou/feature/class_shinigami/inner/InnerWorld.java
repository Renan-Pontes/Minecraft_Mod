package com.renanpontes.isekaimahou.feature.class_shinigami.inner;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiRanks;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiRegistries;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ZanpakutoSpiritEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * O Mundo Interior ({@code isekai_mahou:inner_world}): uma dimensão vazia onde cada jogador tem a sua faixa (como as
 * Provações): ilha {@code n} centrada em {@code (n·1024+512, 100, 512)}. A paisagem segue o arquétipo da Shikai
 * ({@link InnerWorldBuilder}); o espírito da espada ({@link ZanpakutoSpiritEntity}) espera na praça do centro.
 * Entrada: meditação (Jinzen) — ver {@code Meditation}. Saída: meditar de novo, ou pedir ao espírito.
 */
public final class InnerWorld {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id("inner_world"));
    public static final int SPACING = 1024;
    public static final int BASE_Y = 100;

    /** Jogadores esperando a paisagem ficar pronta para chegar. */
    private static final Map<UUID, Integer> ARRIVING = new HashMap<>();
    /** Ticks até conferir o espírito depois da chegada. */
    private static final Map<UUID, Integer> SPIRIT_CHECK = new HashMap<>();

    public static boolean is(Level level) {
        return level.dimension() == KEY;
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    /** Bloco central do piso da ilha {@code index}. */
    public static BlockPos origin(int index) {
        return new BlockPos(index * SPACING + 512, BASE_Y, 512);
    }

    public static int indexAt(double x) {
        return Math.floorDiv(Mth.floor(x), SPACING);
    }

    /** Ponto de chegada (de frente para o espírito). */
    public static Vec3 arrival(int index) {
        BlockPos o = origin(index);
        return new Vec3(o.getX() + 0.5, o.getY() + 1, o.getZ() + 9.5);
    }

    public static Vec3 spiritPos(int index) {
        BlockPos o = origin(index);
        return new Vec3(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
    }

    public static Vec3 center(int index) {
        BlockPos o = origin(index);
        return new Vec3(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
    }

    /** Paisagem que o jogador deve ver agora (0 = sem nome, 1..5 = arquétipo). */
    public static int landscape(MagicData d) {
        Archetype a = Shinigami.archetype(d);
        return a == null ? 0 : a.counterValue();
    }

    /** O jogador está no próprio Mundo Interior? */
    public static boolean inOwn(ServerPlayer sp) {
        if (!is(sp.level())) return false;
        InnerWorldData.Entry e = InnerWorldData.get(sp.server).peek(sp.getUUID());
        return e != null && e.index == indexAt(sp.getX());
    }

    @Nullable
    public static InnerWorldData.Entry entry(ServerPlayer sp) {
        return InnerWorldData.get(sp.server).peek(sp.getUUID());
    }

    // =====================================================================================
    //  Entrar e sair
    // =====================================================================================
    /** A alma desce ao Mundo Interior (constrói/atualiza a paisagem antes, se preciso). */
    public static void enter(ServerPlayer sp) {
        ServerLevel level = level(sp.server);
        if (level == null) {
            sp.displayClientMessage(Shinigami.msg("inner_missing").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (ARRIVING.containsKey(sp.getUUID())) return;
        InnerWorldData data = InnerWorldData.get(sp.server);
        InnerWorldData.Entry e = data.entry(sp.getUUID());
        if (!is(sp.level())) {
            e.returnDim = sp.level().dimension().location();
            e.returnPos = sp.position();
            e.returnYaw = sp.getYRot();
            data.setDirty();
        }
        int land = landscape(MagicApi.get(sp));
        if (e.built != land) {
            int from = e.built;
            ARRIVING.put(sp.getUUID(), e.index);
            sp.displayClientMessage(Shinigami.msg("inner_forming").withColor(Shinigami.REIATSU_RGB), true);
            InnerWorldBuilder.queue(level, e.index, from, land, () -> {
                e.built = land;
                data.setDirty();
                ARRIVING.remove(sp.getUUID());
                ServerPlayer now = sp.server.getPlayerList().getPlayer(sp.getUUID());
                if (now != null && now.isAlive()) arrive(now, level, e);
            });
            return;
        }
        arrive(sp, level, e);
    }

    private static void arrive(ServerPlayer sp, ServerLevel level, InnerWorldData.Entry e) {
        Vec3 at = arrival(e.index);
        ServerLevel from = sp.serverLevel();
        ShinigamiFx.pillar(from, sp.position(), 3.0, 40, ShinigamiFx.SPIRIT);
        ShinigamiFx.sound(from, sp.position(), SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.8F);
        sp.teleportTo(level, at.x, at.y, at.z, 180F, 0F);
        sp.fallDistance = 0;
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        MagicApi.addCounter(sp, Shinigami.MEDITATIONS, 1);
        MagicData d = MagicApi.get(sp);
        Archetype a = Shinigami.archetype(d);
        Component title = Shinigami.msg("inner_title").withColor(Shinigami.spiritRgb(d));
        Component sub = a != null ? a.landscape() : Shinigami.msg("inner_landscape_none");
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(15, 60, 25));
        sp.connection.send(new ClientboundSetTitleTextPacket(title));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        ShinigamiFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.6F);
        ShinigamiFx.sound(level, at, SoundEvents.BELL_RESONATE, 0.7F, 0.8F);
        ShinigamiFx.ring(level, at.add(0, 0.1, 0), 1.2, 20, 0F, ShinigamiFx.SPIRIT);
        sp.sendSystemMessage(Shinigami.msg("inner_arrived", Shinigami.spiritName(d)).withStyle(ChatFormatting.GRAY));
        SPIRIT_CHECK.put(sp.getUUID(), 15);
        ShinigamiRanks.hintIfReady(sp);
    }

    /** Volta ao mundo (onde meditou). */
    public static void leave(ServerPlayer sp) {
        if (!is(sp.level())) return;
        SpiritTrials.abort(sp);
        InnerWorldData.Entry e = entry(sp);
        ServerLevel back = null;
        Vec3 pos = null;
        float yaw = sp.getYRot();
        if (e != null && e.returnDim != null && e.returnPos != null) {
            back = sp.server.getLevel(ResourceKey.create(Registries.DIMENSION, e.returnDim));
            pos = e.returnPos;
            yaw = e.returnYaw;
        }
        if (back == null || is(back)) {
            back = sp.server.overworld();
            BlockPos spawn = back.getSharedSpawnPos();
            pos = new Vec3(spawn.getX() + 0.5, back.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5);
        }
        ShinigamiFx.pillar(sp.serverLevel(), sp.position(), 3.0, 40, ShinigamiFx.SPIRIT);
        sp.teleportTo(back, pos.x, pos.y, pos.z, yaw, 0F);
        sp.fallDistance = 0;
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        ShinigamiFx.sound(back, pos, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 1.2F);
        ShinigamiFx.ring(back, pos.add(0, 0.1, 0), 1.2, 20, 0F, ShinigamiFx.SPIRIT);
        sp.displayClientMessage(Shinigami.msg("inner_left").withColor(Shinigami.REIATSU_RGB), true);
    }

    /** Leva de volta à praça (queda, "morte" no Mundo Interior). */
    public static void toArrival(ServerPlayer sp) {
        InnerWorldData.Entry e = entry(sp);
        if (e == null || !is(sp.level())) return;
        Vec3 at = arrival(e.index);
        sp.teleportTo(sp.serverLevel(), at.x, at.y, at.z, 180F, 0F);
        sp.fallDistance = 0;
        sp.setDeltaMovement(Vec3.ZERO);
    }

    // =====================================================================================
    //  Paisagem nova (arquétipo escolhido) e o espírito
    // =====================================================================================
    /** Reconstrói a paisagem para o arquétipo atual (com o jogador lá dentro). */
    public static void reshape(ServerPlayer sp) {
        ServerLevel level = level(sp.server);
        InnerWorldData.Entry e = entry(sp);
        if (level == null || e == null) return;
        int land = landscape(MagicApi.get(sp));
        if (e.built == land) return;
        int from = e.built;
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false));
        InnerWorldData data = InnerWorldData.get(sp.server);
        InnerWorldBuilder.queue(level, e.index, from, land, () -> {
            e.built = land;
            data.setDirty();
            ServerPlayer now = sp.server.getPlayerList().getPlayer(sp.getUUID());
            if (now != null && inOwn(now)) {
                ShinigamiFx.sound(level, now.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.7F);
                SPIRIT_CHECK.put(now.getUUID(), 2);
            }
        });
    }

    /** O espírito da faixa do jogador, se estiver carregado. */
    @Nullable
    public static ZanpakutoSpiritEntity spirit(ServerPlayer sp) {
        InnerWorldData.Entry e = entry(sp);
        ServerLevel level = level(sp.server);
        if (e == null || level == null || e.spirit == null) return null;
        return level.getEntity(e.spirit) instanceof ZanpakutoSpiritEntity s ? s : null;
    }

    /** Garante UM espírito na praça (cria se não houver, apaga cópias) e atualiza a aparência dele. */
    public static void ensureSpirit(ServerPlayer sp) {
        ServerLevel level = level(sp.server);
        InnerWorldData data = InnerWorldData.get(sp.server);
        InnerWorldData.Entry e = data.peek(sp.getUUID());
        if (level == null || e == null) return;
        Vec3 c = center(e.index);
        List<ZanpakutoSpiritEntity> found = level.getEntitiesOfClass(ZanpakutoSpiritEntity.class, new AABB(c, c).inflate(48, 24, 48));
        ZanpakutoSpiritEntity keep = null;
        for (ZanpakutoSpiritEntity s : found) {
            if (keep == null && (e.spirit == null || s.getUUID().equals(e.spirit))) keep = s;
        }
        if (keep == null && !found.isEmpty()) keep = found.get(0);
        for (ZanpakutoSpiritEntity s : found) if (s != keep) s.discard();
        MagicData d = MagicApi.get(sp);
        if (keep == null) {
            keep = ShinigamiRegistries.SPIRIT.get().create(level);
            if (keep == null) return;
            Vec3 p = spiritPos(e.index);
            keep.moveTo(p.x, p.y, p.z, 0F, 0F);
            keep.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), MobSpawnType.MOB_SUMMONED, null);
            keep.setOwner(sp.getUUID());
            level.addFreshEntity(keep);
            ShinigamiFx.pillar(level, p, 2.5, 30, ShinigamiFx.dust(Shinigami.spiritRgb(d), 1.2F));
        }
        keep.setOwner(sp.getUUID());
        keep.setArchetype(landscape(d));
        keep.setHome(spiritPos(e.index));
        if (!SpiritTrials.inTrial(sp)) keep.calm();
        if (!keep.getUUID().equals(e.spirit)) {
            e.spirit = keep.getUUID();
            data.setDirty();
        }
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    public static void tick(MinecraftServer server) {
        InnerWorldBuilder.tick(server);
        if (!SPIRIT_CHECK.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = SPIRIT_CHECK.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> me = it.next();
                int left = me.getValue() - 1;
                if (left > 0) {
                    me.setValue(left);
                    continue;
                }
                it.remove();
                ServerPlayer sp = server.getPlayerList().getPlayer(me.getKey());
                if (sp != null && inOwn(sp)) ensureSpirit(sp);
            }
        }
        ServerLevel level = level(server);
        if (level == null || server.getTickCount() % 10 != 0) return;
        for (ServerPlayer sp : level.players()) {
            // caiu da ilha (ou foi parar longe): volta para a praça
            if (sp.getY() < BASE_Y - 24 && !sp.isSpectator()) {
                InnerWorldData.Entry e = entry(sp);
                if (e != null) {
                    SpiritTrials.fail(sp);
                    toArrival(sp);
                } else {
                    leave(sp);
                }
            }
        }
    }

    public static void forget(UUID id) {
        ARRIVING.remove(id);
        SPIRIT_CHECK.remove(id);
    }

    public static void clear() {
        ARRIVING.clear();
        SPIRIT_CHECK.clear();
        InnerWorldBuilder.clear();
    }

    /** Chamado ao logar dentro do Mundo Interior: confere o espírito. */
    public static void onLogin(ServerPlayer sp) {
        if (inOwn(sp)) SPIRIT_CHECK.put(sp.getUUID(), 40);
    }

    private InnerWorld() {}
}
