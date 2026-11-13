package com.renanpontes.isekaimahou.feature.combat_lock.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * Posição da câmera. O NeoForge 1.21.1 só deixa mudar os ÂNGULOS (ViewportEvent.ComputeCameraAngles); para a câmera
 * ir para cima do ombro é preciso mexer na POSIÇÃO, e {@code Camera.setPosition} é protegido — reflexão (os nomes
 * Mojang são os de execução no NeoForge 1.21.1; o módulo de cutscenes faz o mesmo). Se falhar, o alvo travado continua
 * funcionando com a câmera normal atrás do jogador.
 */
final class LockCameraAccess {
    private static MethodHandle setPosition;

    static {
        try {
            Method pos = Camera.class.getDeclaredMethod("setPosition", Vec3.class);
            pos.setAccessible(true);
            setPosition = MethodHandles.lookup().unreflect(pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
        }
    }

    static boolean available() {
        return setPosition != null;
    }

    static void setPosition(Camera camera, Vec3 pos) {
        if (setPosition == null) return;
        try {
            setPosition.invoke(camera, pos);
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static void disable(Throwable t) {
        setPosition = null;
        IsekaiMahou.LOGGER.warn("[combat_lock] Camera por cima do ombro indisponivel; o alvo travado segue com a camera normal", t);
    }

    private LockCameraAccess() {}
}
