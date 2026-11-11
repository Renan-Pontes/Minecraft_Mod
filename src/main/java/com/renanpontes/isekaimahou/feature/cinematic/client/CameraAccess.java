package com.renanpontes.isekaimahou.feature.cinematic.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * Acesso à posição/rotação da câmera. No 1.21.1 o NeoForge só expõe os ÂNGULOS (ViewportEvent.ComputeCameraAngles),
 * mas uma câmera que orbita outra entidade precisa mudar a POSIÇÃO. {@code Camera.setPosition/setRotation} são
 * protegidos, então usamos reflexão (nomes Mojang = nomes de runtime no NeoForge 1.21.1).
 * Se algo falhar, a cutscene continua sem mover a câmera (faixas, título, clarão e FOV seguem funcionando).
 */
final class CameraAccess {
    private static MethodHandle setPosition;
    private static MethodHandle setRotation;

    static {
        try {
            Method pos = Camera.class.getDeclaredMethod("setPosition", Vec3.class);
            Method rot = Camera.class.getDeclaredMethod("setRotation", float.class, float.class, float.class);
            pos.setAccessible(true);
            rot.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            setPosition = lookup.unreflect(pos);
            setRotation = lookup.unreflect(rot);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disable(e);
        }
    }

    static boolean available() {
        return setPosition != null && setRotation != null;
    }

    /** Aplica a pose final (chamado depois de {@code Camera.setup}, antes do frustum ser montado). */
    static void apply(Camera camera, Vec3 pos, float yaw, float pitch, float roll) {
        if (!available()) return;
        try {
            setRotation.invoke(camera, yaw, pitch, roll);
            setPosition.invoke(camera, pos);
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static void disable(Throwable t) {
        setPosition = null;
        setRotation = null;
        IsekaiMahou.LOGGER.warn("[cinematic] Controle de camera indisponivel; cutscenes seguem sem mover a camera", t);
    }

    private CameraAccess() {}
}
