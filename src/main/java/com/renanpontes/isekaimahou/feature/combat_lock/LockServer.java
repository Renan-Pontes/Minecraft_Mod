package com.renanpontes.isekaimahou.feature.combat_lock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Lado do servidor de "Travar o alvo": só lembra em quem cada jogador travou (o cliente manda) e, na hora de conjurar,
 * acerta a mira do jogador exatamente no alvo — a câmera do cliente já aponta para ele, isto só tira o atraso de rede
 * e o balanço da câmera. Nada é salvo.
 */
public final class LockServer {
    /** Distância máxima (blocos) em que o servidor ainda aceita o alvo travado. */
    private static final double MAX_RANGE = 40.0;
    /** Se o jogador está olhando para longe do alvo (graus), a magia sai para onde ele olha. */
    private static final float MAX_CORRECTION = 35F;

    private static final Map<UUID, Integer> TARGETS = new ConcurrentHashMap<>();

    static void init(IEventBus bus) {
        // Alta prioridade: a mira é acertada antes de os outros módulos lerem a direção da magia
        bus.addListener(EventPriority.HIGH, false, SpellCastEvent.Pre.class, LockServer::onCast);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> TARGETS.remove(e.getEntity().getUUID()));
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> TARGETS.remove(e.getEntity().getUUID()));
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> TARGETS.remove(e.getEntity().getUUID()));
        bus.addListener(ServerStoppedEvent.class, e -> TARGETS.clear());
    }

    static void receive(ServerPlayer player, int entityId) {
        if (entityId < 0) TARGETS.remove(player.getUUID());
        else TARGETS.put(player.getUUID(), entityId);
    }

    /** Alvo travado pelo jogador (vivo, na mesma dimensão e ao alcance) ou null. Para outros módulos. */
    @Nullable
    public static LivingEntity target(ServerPlayer player) {
        Integer id = TARGETS.get(player.getUUID());
        if (id == null) return null;
        Entity e = player.serverLevel().getEntity(id);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == player || le.distanceToSqr(player) > MAX_RANGE * MAX_RANGE) {
            TARGETS.remove(player.getUUID());
            return null;
        }
        return le;
    }

    private static void onCast(SpellCastEvent.Pre event) {
        if (!(event.getCaster() instanceof ServerPlayer p) || !event.getContext().isPlayerCast()) return;
        if (!LockConfig.on(LockConfig.AIM_ASSIST)) return;
        LivingEntity t = target(p);
        if (t == null) return;
        Vec3 eye = p.getEyePosition();
        Vec3 to = aimPoint(t).subtract(eye);
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        if (horiz < 1.0E-4 && Math.abs(to.y) < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG);
        if (Math.abs(Mth.wrapDegrees(yaw - p.getYRot())) > MAX_CORRECTION || Math.abs(pitch - p.getXRot()) > MAX_CORRECTION) return;
        // Só no servidor: o próximo pacote de movimento do cliente (que já mira o alvo) segue mandando
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(pitch, -90F, 90F));
        p.setYHeadRot(yaw);
    }

    /** Ponto do corpo do alvo para onde a mira vai (um pouco acima do meio). */
    public static Vec3 aimPoint(LivingEntity t) {
        return t.position().add(0, t.getBbHeight() * 0.6, 0);
    }

    private LockServer() {}
}
