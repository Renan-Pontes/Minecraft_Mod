package com.renanpontes.isekaimahou.feature.fusions_b;

import com.renanpontes.isekaimahou.core.Element;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Projeção Astral: o espírito do jogador sai do corpo (modo espectador) por alguns segundos e depois volta
 * ao mesmo lugar, com o modo de jogo original.
 *
 * Segurança: o estado fica nos dados persistentes do jogador (sobrevive a sair do mundo, fechar o servidor e
 * até morrer); ao sair do jogo o jogador volta ao corpo ANTES de ser salvo; ao entrar, qualquer projeção
 * pendente é desfeita; o espírito não pode se afastar mais que {@link #LEASH} blocos nem trocar de dimensão.
 */
public final class AstralProjection {
    private static final String TAG = "isekai_mahou_astral_projection";
    public static final int LEASH = 48;

    private static CompoundTag root(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean isProjecting(ServerPlayer sp) {
        return root(sp).contains(TAG);
    }

    /** Começa a projeção. Retorna false se não for possível (já projetando, espectador, dormindo...). */
    public static boolean start(ServerPlayer sp, int ticks) {
        if (isProjecting(sp) || sp.isSpectator() || sp.isSleeping() || !sp.isAlive()) return false;
        CompoundTag t = new CompoundTag();
        t.putString("dim", sp.level().dimension().location().toString());
        t.putDouble("x", sp.getX());
        t.putDouble("y", sp.getY());
        t.putDouble("z", sp.getZ());
        t.putFloat("yaw", sp.getYRot());
        t.putFloat("pitch", sp.getXRot());
        t.putInt("mode", sp.gameMode.getGameModeForPlayer().getId());
        t.putInt("ticks", ticks);
        root(sp).put(TAG, t);
        if (sp.isPassenger()) sp.stopRiding();
        ServerLevel sl = sp.serverLevel();
        Vec3 c = sp.position().add(0, 1, 0);
        sl.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 40, 0.3, 0.8, 0.3, 0.08);
        sl.sendParticles(Vfx.soul(), c.x, c.y, c.z, 12, 0.3, 0.6, 0.3, 0.04);
        FbFx.burst(sl, c, Element.ASTRAL, 24, 0.5);
        FbFx.sound(sl, c, SoundEvents.SOUL_ESCAPE.value(), 1.6F, 1.0F);
        FbFx.sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
        sp.setGameMode(GameType.SPECTATOR);
        sp.addEffect(new MobEffectInstance(FbRegistries.ASTRAL_PROJECTION, ticks, 0, false, false, true));
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.astral_projection.leave"), true);
        return true;
    }

    /** Tick do servidor (para todo jogador). */
    public static void tick(ServerPlayer sp) {
        CompoundTag root = root(sp);
        if (!root.contains(TAG)) return;
        CompoundTag t = root.getCompound(TAG);
        int left = t.getInt("ticks") - 1;
        t.putInt("ticks", left);
        // Se algo tirou o jogador do espectador (comando, morte), encerra com segurança
        if (left <= 0 || !sp.isSpectator()) {
            returnToBody(sp);
            return;
        }
        ServerLevel body = bodyLevel(sp, t);
        Vec3 anchor = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
        if (body != sp.serverLevel()) {
            returnToBody(sp);
            return;
        }
        if (sp.position().distanceToSqr(anchor) > LEASH * LEASH) {
            Vec3 dir = sp.position().subtract(anchor).normalize();
            Vec3 back = anchor.add(dir.scale(LEASH - 2));
            sp.teleportTo(body, back.x, back.y, back.z, sp.getYRot(), sp.getXRot());
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.astral_projection.leash"), true);
        }
        if (sp.tickCount % 10 == 0) {
            // O "corpo" que ficou para trás: um brilho fraco no lugar
            body.sendParticles(Vfx.endRod(), anchor.x, anchor.y + 1.0, anchor.z, 2, 0.15, 0.5, 0.15, 0.01);
            body.sendParticles(FbFx.dust(FbFx.ASTRAL, 1.0F), anchor.x, anchor.y + 0.9, anchor.z, 4, 0.2, 0.5, 0.2, 0);
        }
        if (left % 20 == 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.astral_projection.time", left / 20), true);
        }
    }

    /** Volta ao corpo: posição, dimensão e modo de jogo originais. */
    public static void returnToBody(ServerPlayer sp) {
        CompoundTag root = root(sp);
        if (!root.contains(TAG)) return;
        CompoundTag t = root.getCompound(TAG);
        root.remove(TAG);
        ServerLevel body = bodyLevel(sp, t);
        sp.setCamera(sp);
        Vec3 pos = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
        // Alguém pode ter colocado blocos no lugar do corpo enquanto o espírito estava fora: não sufocar
        if (body == sp.serverLevel() && !FbUtil.fitsBlocks(sp, pos)) {
            Vec3 alt = FbUtil.findSafeSpot(sp, pos, 4, 2);
            if (alt != null) pos = alt;
        }
        sp.teleportTo(body, pos.x, pos.y, pos.z, t.getFloat("yaw"), t.getFloat("pitch"));
        GameType mode = GameType.byId(t.getInt("mode"));
        if (mode == GameType.SPECTATOR) mode = GameType.SURVIVAL;
        sp.setGameMode(mode);
        sp.removeEffect(FbRegistries.ASTRAL_PROJECTION);
        sp.resetFallDistance();
        sp.setDeltaMovement(Vec3.ZERO);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, true));
        Vec3 c = sp.position().add(0, 1, 0);
        body.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 30, 0.3, 0.8, 0.3, 0.05);
        FbFx.burst(body, c, Element.ASTRAL, 16, 0.5);
        FbFx.sound(body, c, SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.5F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.astral_projection.return"), true);
    }

    private static ServerLevel bodyLevel(ServerPlayer sp, CompoundTag t) {
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        if (dim != null && sp.server != null) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
            ServerLevel l = sp.server.getLevel(key);
            if (l != null) return l;
        }
        return sp.serverLevel();
    }

    private AstralProjection() {}
}
