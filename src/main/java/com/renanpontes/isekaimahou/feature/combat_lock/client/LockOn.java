package com.renanpontes.isekaimahou.feature.combat_lock.client;

import javax.annotation.Nullable;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.feature.combat_lock.LockConfig;
import com.renanpontes.isekaimahou.feature.combat_lock.LockPayloads;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>Travar o alvo</b> (cliente). A tecla (padrão: botão do meio do mouse) trava no inimigo mais perto da mira; apertar
 * de novo solta.
 * <ul>
 *   <li>O jogador passa a OLHAR para o alvo (a cada quadro, suave): golpes e magias saem na direção dele. Em 1ª pessoa
 *       é uma mira suave; em 3ª pessoa a câmera vai para cima do ombro e enquadra o jogador e o alvo.</li>
 *   <li>Roda do mouse (ou a tecla "Próximo alvo") troca para o inimigo ao lado.</li>
 *   <li>Solta sozinho: alvo morto/sumido, longe demais ou escondido atrás de parede por alguns segundos.</li>
 * </ul>
 * O servidor só recebe "travei em X" ({@link LockPayloads.Target}) para acertar a mira das magias.
 */
final class LockOn {
    /**
     * Contexto próprio: no jogo a tecla vale como qualquer tecla "em jogo" (inclusive segurando Shift/Ctrl), mas na tela
     * de Controles ela não acusa conflito — o padrão é o mesmo botão do "Pegar bloco" do Minecraft, de propósito: o
     * pegar bloco é engolido só no clique que trava/solta um alvo ({@link #onInteraction}).
     */
    private static final IKeyConflictContext CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            if (other == this) return true;
            return other == KeyConflictContext.IN_GAME && !(Minecraft.getInstance().screen instanceof KeyBindsScreen);
        }
    };
    static final KeyMapping LOCK_KEY = new KeyMapping("key.isekai_mahou.lock_target", CONTEXT, InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CoreKeys.CATEGORY);
    static final KeyMapping NEXT_KEY = new KeyMapping("key.isekai_mahou.lock_next", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CoreKeys.CATEGORY);

    /** Alcance para travar / distância em que o alvo travado se solta. */
    private static final double RANGE = 24.0, KEEP_RANGE = 32.0;
    /** Ângulo (graus) em volta da mira: inimigos numa área larga; o resto (animais, NPCs, jogadores) só bem na mira. */
    private static final double HOSTILE_CONE = 55.0, OTHER_CONE = 8.0;
    /** Ticks sem ver o alvo (parede) até soltar. */
    private static final int LOST_SIGHT_TICKS = 50;
    private static final long NEVER = Long.MIN_VALUE / 2;

    @Nullable
    private static LivingEntity target;
    private static int hidden;
    /** Contador próprio de ticks do cliente (não para quando o mundo pausa o relógio). */
    private static long ticks;
    private static long changedTick = NEVER, lastSwitch = NEVER, lastHurt = NEVER;
    /** 0..1: quanto a câmera está por cima do ombro (entra e sai suave). */
    private static float blend;
    /** Desvio (graus) da câmera em relação ao olhar do jogador para enquadrar o alvo. */
    private static float yawOff, pitchOff;

    @Nullable
    static LivingEntity target() {
        return target;
    }

    static void reset() {
        target = null;
        hidden = 0;
        blend = 0F;
        yawOff = pitchOff = 0F;
        changedTick = lastSwitch = lastHurt = NEVER;
    }

    // =====================================================================================
    //  Tick: tecla e validade do alvo
    // =====================================================================================
    static void onTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            if (target != null) reset();
            return;
        }
        ticks++;
        if (p.hurtTime > 0) lastHurt = ticks;
        boolean lock = false, next = false;
        while (LOCK_KEY.consumeClick()) lock = true;
        while (NEXT_KEY.consumeClick()) next = true;
        if (mc.screen == null) {
            if (lock) toggle(mc, p);
            else if (next && target != null) switchTarget(mc, p, 1);
        }
        validate(mc, p);
    }

    private static void toggle(Minecraft mc, LocalPlayer p) {
        if (target != null) {
            release(p);
            return;
        }
        if (p.isSpectator() || !p.isAlive() || p.isSleeping()) return;
        // Mesmo botão do "pegar bloco": mirando um bloco, quem constrói no criativo quer o bloco, não travar num bicho
        boolean pickingBlock = sharesPickKey(mc) && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        if (pickingBlock && p.getAbilities().instabuild) return;
        LivingEntity t = pick(mc, p);
        if (t == null) {
            if (!pickingBlock) p.displayClientMessage(Component.translatable("message.isekai_mahou.combat_lock.none"), true);
            return;
        }
        set(p, t);
        seenHint();
    }

    private static void set(LocalPlayer p, LivingEntity t) {
        target = t;
        hidden = 0;
        changedTick = ticks;
        PacketDistributor.sendToServer(new LockPayloads.Target(t.getId()));
        p.playSound(SoundEvents.SPYGLASS_USE, 0.6F, 1.35F);
    }

    private static void release(LocalPlayer p) {
        target = null;
        hidden = 0;
        changedTick = ticks;
        PacketDistributor.sendToServer(new LockPayloads.Target(-1));
        p.playSound(SoundEvents.SPYGLASS_STOP_USING, 0.5F, 1.2F);
    }

    /** Solta sozinho quando o alvo morre, some, fica longe ou escondido por tempo demais. */
    private static void validate(Minecraft mc, LocalPlayer p) {
        LivingEntity t = target;
        if (t == null) return;
        if (!p.isAlive() || p.isSpectator() || t.isRemoved() || !t.isAlive() || t.level() != p.level()
                || t.distanceToSqr(p) > KEEP_RANGE * KEEP_RANGE || t.isInvisibleTo(p)) {
            release(p);
            return;
        }
        hidden = p.hasLineOfSight(t) ? 0 : hidden + 1;
        if (hidden > LOST_SIGHT_TICKS) release(p);
    }

    // =====================================================================================
    //  Escolha do alvo
    // =====================================================================================
    private static boolean candidate(Entity e, LocalPlayer p) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le instanceof ArmorStand || le.isSpectator()) return false;
        if (le.isInvisibleTo(p) || le == p.getVehicle() || le.hasPassenger(p) || p.isAlliedTo(le)) return false;
        return !(le instanceof OwnableEntity pet && p.getUUID().equals(pet.getOwnerUUID()));
    }

    private static boolean hostile(LivingEntity le) {
        return le instanceof Enemy || le instanceof Mob mob && mob.isAggressive();
    }

    /** Ponto do corpo do alvo para onde o jogador olha (um pouco acima do meio). */
    private static Vec3 aim(LivingEntity t, float partial) {
        return t.getPosition(partial).add(0, t.getBbHeight() * 0.6, 0);
    }

    /** O melhor alvo para travar agora: perto da mira primeiro, depois o mais próximo. */
    @Nullable
    private static LivingEntity pick(Minecraft mc, LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1F);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(RANGE), en -> candidate(en, p))) {
            LivingEntity le = (LivingEntity) e;
            Vec3 to = aim(le, 1F).subtract(eye);
            double dist = to.length();
            if (dist > RANGE || dist < 0.01) continue;
            double angle = Math.toDegrees(Math.acos(Mth.clamp(to.dot(look) / dist, -1.0, 1.0)));
            boolean hostile = hostile(le);
            if (angle > (hostile ? HOSTILE_CONE : OTHER_CONE) || !p.hasLineOfSight(le)) continue;
            double score = angle + dist * 0.9 + (hostile ? 0 : 20);
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90F;
    }

    /** Troca para o inimigo seguinte à direita ({@code dir} = 1) ou à esquerda (-1) do alvo atual; dá a volta no fim. */
    private static void switchTarget(Minecraft mc, LocalPlayer p, int dir) {
        LivingEntity cur = target;
        if (cur == null || ticks - lastSwitch < 3) return;
        Vec3 eye = p.getEyePosition();
        float curYaw = yawTo(eye, aim(cur, 1F));
        boolean onlyHostile = hostile(cur);
        LivingEntity best = null, wrap = null;
        float bestDelta = Float.MAX_VALUE, wrapDelta = 0F;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(RANGE), en -> en != cur && candidate(en, p))) {
            LivingEntity le = (LivingEntity) e;
            if (onlyHostile && !hostile(le) || le.distanceToSqr(p) > RANGE * RANGE || !p.hasLineOfSight(le)) continue;
            float d = Mth.wrapDegrees(yawTo(eye, aim(le, 1F)) - curYaw) * dir;
            if (d > 0.5F) {
                if (d < bestDelta) {
                    bestDelta = d;
                    best = le;
                }
            } else if (d < wrapDelta) {
                wrapDelta = d;
                wrap = le;
            }
        }
        LivingEntity chosen = best != null ? best : wrap;
        if (chosen == null) return;
        lastSwitch = ticks;
        set(p, chosen);
    }

    // =====================================================================================
    //  Entrada: roda do mouse, "pegar bloco" e dica única
    // =====================================================================================
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.screen != null || mc.player == null || mc.player.isSpectator()) return;
        if (!LockConfig.on(LockConfig.SCROLL_SWITCH)) return;
        // Segurando a tecla de loadout a roda é do loadout
        if (CoreKeys.LOADOUT_MODIFIER.isDown() || CoreKeys.LOADOUT_MOUSE.isDown()) return;
        double dy = event.getScrollDeltaY();
        if (dy == 0) return;
        switchTarget(mc, mc.player, dy > 0 ? -1 : 1);
        event.setCanceled(true);
    }

    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isPickBlock()) {
            // O clique que travou/soltou o alvo não pega bloco (mesmo botão por padrão)
            if (ticks - changedTick <= 1 && sharesPickKey(mc)) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            return;
        }
        if (event.isAttack()) hint(mc);
    }

    /** A tecla de travar está no mesmo botão do "Pegar bloco"? (comparação direta: os contextos são diferentes) */
    private static boolean sharesPickKey(Minecraft mc) {
        return !LOCK_KEY.isUnbound() && LOCK_KEY.getKey().equals(mc.options.keyPickItem.getKey());
    }

    /** Dica única (barra de ação) na primeira vez que o jogador bate num monstro sem estar apanhando. */
    private static void hint(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || target != null || LockConfig.on(LockConfig.LOCK_HINT_SEEN) || LOCK_KEY.isUnbound()) return;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Enemy)) return;
        if (Onboarding.quiet(p) || ticks - lastHurt < 80) return;
        p.displayClientMessage(Component.translatable("message.isekai_mahou.combat_lock.hint", LOCK_KEY.getTranslatedKeyMessage()), true);
        seenHint();
    }

    private static void seenHint() {
        try {
            if (LockConfig.LOCK_HINT_SEEN.get()) return;
            LockConfig.LOCK_HINT_SEEN.set(true);
            LockConfig.LOCK_HINT_SEEN.save();
        } catch (RuntimeException ignored) {
            // config ainda não carregada: a dica aparece de novo na próxima vez
        }
    }

    // =====================================================================================
    //  A cada quadro: o jogador vira para o alvo
    // =====================================================================================
    static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        float dt = Mth.clamp(event.getPartialTick().getRealtimeDeltaTicks() / 20F, 0F, 0.1F);
        LivingEntity t = target;
        boolean shoulder = t != null && p != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                && LockConfig.on(LockConfig.SHOULDER_CAMERA) && LockCameraAccess.available();
        blend += ((shoulder ? 1F : 0F) - blend) * (1F - (float) Math.exp(-9F * dt));
        if (!shoulder && blend < 0.003F) blend = 0F;
        if (t == null || p == null || mc.isPaused()) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 to = aim(t, partial).subtract(p.getEyePosition(partial));
        double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        if (horiz < 0.05 && Math.abs(to.y) < 0.05) return;
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = Mth.clamp((float) -(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG), -85F, 85F);
        // 1ª pessoa: mira mais suave (o jogador ainda sente o mouse); 3ª: a câmera gruda no alvo
        float rate = mc.options.getCameraType().isFirstPerson() ? 10F : 16F;
        float k = 1F - (float) Math.exp(-rate * dt);
        float dYaw = Mth.wrapDegrees(yaw - p.getYRot()) * k;
        float dPitch = (pitch - p.getXRot()) * k;
        // Como o mouse faz: soma no valor atual e no anterior, para a virada aparecer já neste quadro
        p.setYRot(p.getYRot() + dYaw);
        p.yRotO += dYaw;
        p.setXRot(Mth.clamp(p.getXRot() + dPitch, -90F, 90F));
        p.xRotO = Mth.clamp(p.xRotO + dPitch, -90F, 90F);
    }

    // =====================================================================================
    //  Câmera por cima do ombro (3ª pessoa)
    // =====================================================================================
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (blend <= 0F) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Camera cam = event.getCamera();
        if (p == null || mc.level == null || cam.getEntity() != p || !cam.isDetached()
                || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            return;
        }
        float partial = (float) event.getPartialTick();
        Vec3 eye = p.getEyePosition(partial);
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        float side = LockConfig.on(LockConfig.LEFT_SHOULDER) ? 1F : -1F;
        double off = 0.9 * blend * side, lift = 0.3 * blend;
        Vec3 want = cam.getPosition().add(left.x() * off + up.x() * lift, left.y() * off + up.y() * lift,
                left.z() * off + up.z() * lift);
        // Parede no caminho: a câmera para um pouco antes dela
        HitResult hit = mc.level.clip(new ClipContext(eye, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 back = eye.subtract(hit.getLocation());
            double len = back.length();
            want = len > 0.3 ? hit.getLocation().add(back.scale(0.25 / len)) : eye;
        }
        LockCameraAccess.setPosition(cam, want);

        LivingEntity t = target;
        if (t != null) {
            Vec3 dir = aim(t, partial).subtract(eye);
            double d = dir.length();
            if (d > 0.01) {
                // Alvo colado: olha para um ponto 4 blocos adiante na mesma linha (a câmera não chicoteia)
                Vec3 focus = d < 4.0 ? eye.add(dir.scale(4.0 / d)) : eye.add(dir);
                Vec3 to = focus.subtract(want);
                double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
                float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
                float pitch = (float) -(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG);
                yawOff = Mth.clamp(Mth.wrapDegrees(yaw - p.getViewYRot(partial)), -25F, 25F);
                pitchOff = Mth.clamp(pitch - p.getViewXRot(partial), -20F, 20F);
            }
        }
        // Sem alvo (acabou de soltar) o último desvio some junto com o deslocamento
        event.setYaw(event.getYaw() + yawOff * blend);
        event.setPitch(Mth.clamp(event.getPitch() + pitchOff * blend, -89F, 89F));
    }

    private LockOn() {}
}
