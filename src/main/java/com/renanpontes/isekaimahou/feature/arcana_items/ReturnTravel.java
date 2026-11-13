package com.renanpontes.isekaimahou.feature.arcana_items;

import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeManager;
import com.renanpontes.isekaimahou.feature.space_magic.SpaceGates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Pergaminho de Retorno: leva à ÚLTIMA cidade visitada (cidade, capital ou cidade de jogador), só fora de combate.
 * A última cidade fica nos dados do jogador ({@link ArcanaContent#PLAYER_DATA}: {@code city}, {@code city_dim}) e
 * sobrevive à morte. A chegada é a Pedra de Teleporte da cidade, se houver, ou o centro — sempre num chão seguro.
 */
public final class ReturnTravel {
    /** Sem levar nem dar dano há tanto tempo = fora de combate (10 s). */
    public static final int COMBAT_TICKS = 200;
    /** Tipos de lugar que contam como "cidade". */
    private static final Set<String> CITY_TYPES = Set.of("city", "capital", "village", "town");
    /** Dimensões-instância de onde não se foge com um pergaminho (Provações, Abismo Infinito). */
    private static final Set<ResourceLocation> SEALED_DIMS = Set.of(IsekaiMahou.id("trial_realm"), IsekaiMahou.id("endless_abyss"));
    private static final int RGB = 0x8FE0B0;

    /** Por que não dá para voltar agora (null = pode). */
    public enum Block { NO_CITY, IN_COMBAT, SEALED, ALREADY_THERE, NO_GROUND }

    static void onPlaceEnter(PlaceEvent.Enter e) {
        Places.Place p = e.getPlace();
        if (!CITY_TYPES.contains(p.type())) return;
        CompoundTag t = e.getPlayer().getData(ArcanaContent.PLAYER_DATA.get());
        if (p.id().equals(t.getString("city"))) return;
        t.putString("city", p.id());
        t.putString("city_dim", p.dim().location().toString());
    }

    /** A última cidade visitada que ainda existe (null = nenhuma). */
    @Nullable
    public static Places.Place lastCity(ServerPlayer sp) {
        CompoundTag t = sp.getData(ArcanaContent.PLAYER_DATA.get());
        String id = t.getString("city");
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("city_dim"));
        MinecraftServer server = sp.getServer();
        if (server == null) return null;
        if (!id.isEmpty() && dim != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            Places.Place saved = level == null ? null : Places.get(level, id);
            if (saved != null) return saved;
        }
        // Mundo antigo (nada guardado ainda) ou cidade removida: a cidade conhecida mais próxima
        for (Places.Place p : Travel.destinations(sp)) {
            if (CITY_TYPES.contains(p.type())) return p;
        }
        return null;
    }

    public static boolean inCombat(ServerPlayer sp) {
        if (sp.getAbilities().instabuild) return false;
        boolean hurt = sp.getLastHurtByMob() != null && sp.tickCount - sp.getLastHurtByMobTimestamp() < COMBAT_TICKS;
        boolean hit = sp.getLastHurtMob() != null && sp.getLastHurtMob().isAlive() && sp.tickCount - sp.getLastHurtMobTimestamp() < COMBAT_TICKS;
        return hurt || hit;
    }

    /** Confere sem teleportar. */
    @Nullable
    public static Block check(ServerPlayer sp) {
        ResourceKey<Level> here = sp.level().dimension();
        if (SEALED_DIMS.contains(here.location()) || AfterlifeManager.isSpirit(sp) || BossArena.at(sp.level(), sp.position()) != null) {
            return Block.SEALED;
        }
        Places.Place city = lastCity(sp);
        if (city == null) return Block.NO_CITY;
        if (inCombat(sp)) return Block.IN_COMBAT;
        if (city.dim().equals(here) && city.contains(sp.blockPosition())) return Block.ALREADY_THERE;
        return null;
    }

    public static Component message(Block b, ServerPlayer sp) {
        if (b == Block.ALREADY_THERE) {
            Places.Place city = lastCity(sp);
            if (city != null) return Component.translatable("message.isekai_mahou.arcana_items.return_already_there", city.name());
        }
        return Component.translatable("message.isekai_mahou.arcana_items.return_" + b.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Teleporta para a última cidade. Devolve null se deu certo, ou o motivo da falha. */
    @Nullable
    public static Block travel(ServerPlayer sp) {
        Block b = check(sp);
        if (b != null) return b;
        Places.Place city = lastCity(sp);
        MinecraftServer server = sp.getServer();
        ServerLevel target = city == null || server == null ? null : server.getLevel(city.dim());
        if (target == null) return Block.NO_CITY;
        BlockPos near = city.center();
        GlobalPos stone = Travel.stone(server, city.id());
        if (stone != null && stone.dimension().equals(city.dim())) near = stone.pos();
        Vec3 feet = SpaceGates.safeNear(target, near, 6);
        if (feet == null) return Block.NO_GROUND;

        fx(sp.serverLevel(), sp.position());
        sp.teleportTo(target, feet.x, feet.y, feet.z, sp.getYRot(), sp.getXRot());
        if (sp.level() != target) return Block.SEALED; // outro mod cancelou a troca de dimensão
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hurtMarked = true;
        sp.resetFallDistance();
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
        fx(target, feet);
        target.playSound(null, feet.x, feet.y, feet.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.1F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.arcana_items.return_arrived", city.name()).withColor(RGB), true);
        return null;
    }

    private static void fx(ServerLevel level, Vec3 at) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.56F, 0.88F, 0.69F), 1.1F);
        level.sendParticles(dust, at.x, at.y + 1.0, at.z, 30, 0.35, 0.8, 0.35, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 10, 0.3, 0.7, 0.3, 0.02);
    }

    private ReturnTravel() {}
}
