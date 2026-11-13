package com.renanpontes.isekaimahou.feature.earth_wind.client;

import com.renanpontes.isekaimahou.feature.earth_wind.EwNetwork;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pulo Duplo no cliente: apertar PULAR no ar (com o efeito ativo) da um novo impulso para cima e um pouco para
 * a frente. O movimento e aplicado na hora (sem atraso) e o servidor so valida, zera a queda e mostra o efeito.
 */
public final class DoubleJumpInput {
    private static boolean wasJumpDown;
    private static int jumpsUsed;
    private static int airTicks;

    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            wasJumpDown = false;
            jumpsUsed = 0;
            return;
        }
        boolean jump = mc.options.keyJump.isDown();
        boolean grounded = p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()
                || p.getAbilities().flying || p.isFallFlying();
        if (grounded) {
            jumpsUsed = 0;
            airTicks = 0;
        } else {
            airTicks++;
        }
        MobEffectInstance eff = p.getEffect(EwRegistries.DOUBLE_JUMP);
        if (jump && !wasJumpDown && !grounded && eff != null && airTicks > 2 && mc.screen == null
                && jumpsUsed < eff.getAmplifier() + 1) {
            jumpsUsed++;
            doJump(p);
            PacketDistributor.sendToServer(new EwNetwork.DoubleJump());
        }
        wasJumpDown = jump;
    }

    private static void doJump(LocalPlayer p) {
        Vec3 dm = p.getDeltaMovement();
        double up = 0.62 + p.getJumpBoostPower();
        // empurraozinho para onde o jogador esta andando
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = 0;
        double fz = 0;
        if (p.zza > 0) {
            fx = -Mth.sin(yaw) * 0.18;
            fz = Mth.cos(yaw) * 0.18;
        }
        p.setDeltaMovement(dm.x + fx, up, dm.z + fz);
        p.resetFallDistance();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            p.level().addParticle(Vfx.cloud(), p.getX() + Math.cos(a) * 0.4, p.getY(), p.getZ() + Math.sin(a) * 0.4,
                    Math.cos(a) * 0.08, -0.05, Math.sin(a) * 0.08);
        }
    }

    private DoubleJumpInput() {}
}
