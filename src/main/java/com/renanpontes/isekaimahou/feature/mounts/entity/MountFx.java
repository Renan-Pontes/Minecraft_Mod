package com.renanpontes.isekaimahou.feature.mounts.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/** Utilitários de mensagens, partículas e "quem é amigo" das montarias (lado servidor). */
public final class MountFx {

    /** Dica de controles na barra de ação ao montar (as teclas aparecem como o jogador configurou). */
    public static void controlsHint(ServerPlayer player, FlyingMount mount) {
        player.displayClientMessage(Component.translatable("message.isekai_mahou.mount_controls",
                Component.keybind("key.jump"), Component.keybind("key.sneak"),
                Component.keybind("key.isekai_mahou.mount_ability"), mount.abilityName()).withStyle(ChatFormatting.AQUA), true);
    }

    /** Doma concluída: mensagem, contador ({@code feat.tame_<id>}) e partículas. */
    public static void tamed(LivingEntity mount, Player player, ParticleOptions particle) {
        player.displayClientMessage(Component.translatable("message.isekai_mahou.mount_tamed", mount.getDisplayName())
                .withStyle(ChatFormatting.GOLD), true);
        if (player instanceof ServerPlayer sp) {
            MagicApi.addCounter(sp, "feat.tame_" + BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()).getPath(), 1);
        }
        burst(mount, particle, 18);
    }

    public static void burst(Entity e, ParticleOptions particle, int count) {
        if (e.level() instanceof ServerLevel sl) {
            sl.sendParticles(particle, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), count,
                    e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.02);
        }
    }

    /**
     * A montaria não deve ferir: ela mesma, quem está montado, o dono, nem os bichos do dono.
     * Usado pelas habilidades de área (sopro, investida).
     */
    public static boolean isFriendly(Mob mount, Entity e, @Nullable LivingEntity rider) {
        if (e == mount || e == rider || mount.hasPassenger(e) || !e.isAlive()) return true;
        LivingEntity owner = mount instanceof OwnableEntity o && o.getOwner() instanceof LivingEntity le ? le : rider;
        if (owner == null) return e instanceof Mob m && m.getType() == mount.getType();
        if (e == owner || e.isAlliedTo(owner)) return true;
        if (e instanceof OwnableEntity pet && pet.getOwner() == owner) return true;
        return e instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p);
    }

    private MountFx() {}
}
