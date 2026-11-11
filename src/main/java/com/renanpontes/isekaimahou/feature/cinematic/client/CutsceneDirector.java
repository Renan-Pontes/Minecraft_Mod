package com.renanpontes.isekaimahou.feature.cinematic.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Cutscenes no cliente: estado da cena, câmera orbitando o foco, FOV, bloqueio de controles e restauração.
 *
 * Como a câmera é movida (1.21.1): a perspectiva vira 3ª pessoa enquanto a câmera está longe do olho (para o corpo
 * do jogador aparecer); a pose final é aplicada em {@link ViewportEvent.ComputeFov}, que dispara logo DEPOIS de
 * {@code Camera.setup} e ANTES do frustum — assim o recorte de chunks/entidades usa a posição nova.
 * A entidade da câmera continua sendo o jogador: rede, mira e física do jogador seguem normais.
 * Só mexemos na perspectiva quando NÓS precisamos (e devolvemos só o que mudamos): um F5 do jogador é respeitado.
 */
public final class CutsceneDirector {
    /** Distância máxima entre o jogador e o foco (mais que isso: a cena encerra). */
    private static final double MAX_FOCUS_DISTANCE = 96.0;
    /** Duração máxima das transições de entrada/saída da câmera (ticks). */
    private static final float MAX_BLEND = 10F;
    /** Duração da abertura/fechamento das faixas de cinema (ticks). */
    static final float BARS_TIME = 7F;
    /** Duração máxima aceita (ticks): um pacote com valor absurdo não prende a câmera. */
    private static final int MAX_TICKS = 400;
    /** Por quantos ticks esperamos o foco chegar do servidor (o pacote pode chegar antes da entidade). */
    private static final int FOCUS_WAIT = 20;

    private static Scene scene;
    /** Perspectiva que trocamos para 3ª pessoa (null = não mexemos); é a que volta no fim. */
    private static CameraType forcedFrom;
    private static final float[] SHAKE = new float[3];

    /** Estado de uma cena em andamento (lido também pelo {@link CutsceneOverlay}). */
    static final class Scene {
        final String kind;
        final CutsceneShot shot;
        final Component title;
        final Component subtitle;
        final int rgb;
        final int total;
        final boolean ownFocus;
        /** "Visão do dono": faixas maiores e HUD escondido. */
        final boolean ownerView;
        final ClientLevel level;
        final LocalPlayer player;
        final int focusId;

        // Preenchidos quando o foco é encontrado (na hora ou alguns ticks depois)
        float focusHeight = 1.8F;
        double baseRadius = 4.0;
        float baseAngle;
        boolean cameraEnabled;
        boolean bound;
        /** Momento (ticks da cena) em que a câmera passou a seguir o foco: a transição de entrada conta daí. */
        float cameraSince;

        Entity focus;
        Vec3 focusPos;
        boolean locked;
        boolean skipping;
        int age;
        float end;
        int lastHurtTime;
        double camDist = -1;

        // Pose calculada no início de cada frame (RenderFrameEvent.Pre)
        boolean frameValid;
        float frameW;
        Vec3 framePos = Vec3.ZERO;
        float frameYaw, framePitch, frameRoll, frameFov = 1F, framePunch;

        Scene(CorePayloads.Cutscene p, CutsceneShot shot, Entity focus, boolean ownFocus, LocalPlayer player, ClientLevel level) {
            this.kind = p.kind();
            this.shot = shot;
            this.title = p.title();
            this.subtitle = p.subtitle();
            this.rgb = p.rgb() & 0xFFFFFF;
            this.total = Mth.clamp(p.ticks(), 10, MAX_TICKS);
            this.end = this.total;
            this.ownFocus = ownFocus;
            this.locked = p.lockControls();
            this.ownerView = p.lockControls() || ownFocus;
            this.level = level;
            this.player = player;
            this.focusId = p.focusId();
            this.focusPos = player.position();
            this.lastHurtTime = player.hurtTime;
            if (focus != null) bind(focus, 0F);
        }

        /** Liga a cena ao foco: enquadramento pelo tamanho dele e câmera ligada a partir de {@code at}. */
        void bind(Entity target, float at) {
            bound = true;
            focus = target;
            focusPos = target.position();
            float h = target.getBbHeight();
            float w = target.getBbWidth();
            focusHeight = h;
            baseRadius = Mth.clamp(1.8 + w * 1.6 + h * 0.9, 3.2, 14.0);
            if (ownFocus) {
                baseAngle = player.getYRot() + 180F;                // começa atrás do jogador
            } else {
                Vec3 d = player.position().subtract(focusPos);      // começa do lado de quem assiste
                baseAngle = d.horizontalDistanceSqr() < 1.0E-4 ? player.getYRot() + 180F
                        : (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
            }
            cameraEnabled = CameraAccess.available() && Minecraft.getInstance().getCameraEntity() == player;
            cameraSince = at;
        }

        /** Tempo da cena (ticks, com fração do frame). */
        float time(float partialTick) {
            return Math.min(age + partialTick, end);
        }
    }

    static Scene current() {
        return scene;
    }

    /** A cena atual já mostra este texto como título (o anúncio igual não precisa aparecer de novo). */
    static boolean showingTitle(Component text) {
        Scene s = scene;
        return s != null && !s.skipping && s.title.getString().equals(text.getString());
    }

    // =====================================================================================
    // Início / fim
    // =====================================================================================

    /** Gancho do pacote Cutscene (ClientHooks.cutscene). */
    static void play(CorePayloads.Cutscene p) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || p.ticks() <= 0) return;
        Entity focus = level.getEntity(p.focusId());
        boolean ownFocus = p.focusId() == player.getId();
        CutsceneShot shot = CutsceneShot.of(p.kind());

        // Cena "de outro": focada em OUTRO jogador (ou em alguém fora do alcance), não é de chefe e não nos trava
        boolean foreign = !ownFocus && !p.lockControls() && !shot.boss() && (focus == null || focus instanceof Player);

        // Cutscenes desligadas: o título vira um anúncio comum (sem câmera nem bloqueio)
        if (!CinematicSettings.cutscenes()) {
            if (!foreign || CinematicSettings.othersAnnouncements()) AnnounceOverlay.cutsceneFallback(p, shot.boss());
            return;
        }
        // "Só as minhas": ignora as cenas dos outros jogadores, mas mostra as de chefes/criaturas
        if (CinematicSettings.ownCutscenesOnly() && foreign) return;
        // Uma cena importante (minha / travada / de chefe) não é interrompida por uma cena comum de outro
        Scene cur = scene;
        if (cur != null && !cur.skipping) {
            boolean curImportant = cur.locked || cur.ownFocus || cur.shot.boss();
            boolean newImportant = p.lockControls() || ownFocus || shot.boss();
            if (curImportant && !newImportant) return;
        }

        Scene next = new Scene(p, shot, focus, ownFocus, player, level);
        if (cur != null && cur.locked && !next.locked) {
            KeyMapping.setAll();   // a cena nova não trava: devolve as teclas físicas
        }
        scene = next;

        AnnounceOverlay.absorbCutsceneTitle(p.title());
        CutsceneShot.playStartSounds(p.kind(), mc.getSoundManager());
        CameraShake.add(shot.shake(), shot.shakeTicks());
        mc.levelRenderer.needsUpdate();
    }

    /** Pula a cena: a câmera e as faixas voltam suavemente (≤ 0,5 s) e o controle volta na hora. */
    static void skip() {
        Scene s = scene;
        if (s == null || s.skipping) return;
        s.skipping = true;
        s.end = Math.min(s.end, s.age + Math.max(blendTime(s), BARS_TIME));
        if (s.locked) {
            s.locked = false;
            KeyMapping.setAll();
        }
    }

    /** Encerra na hora e restaura TUDO (perspectiva, teclas). Seguro de chamar a qualquer momento. */
    static void stop() {
        Minecraft mc = Minecraft.getInstance();
        Scene s = scene;
        scene = null;
        restorePerspective(mc);
        if (s != null) {
            if (s.locked) KeyMapping.setAll();
            mc.levelRenderer.needsUpdate();
        }
    }

    /** Devolve a perspectiva que NÓS trocamos (se o jogador mudou por conta própria, fica como ele deixou). */
    private static void restorePerspective(Minecraft mc) {
        if (forcedFrom != null) {
            mc.options.setCameraType(forcedFrom);
            forcedFrom = null;
        }
    }

    // =====================================================================================
    // Tick
    // =====================================================================================

    /** ClientTickEvent.Pre (prioridade alta: antes das teclas do vanilla e do núcleo serem lidas). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Scene s = scene;
        if (s == null) {
            while (CinematicKeys.SKIP.consumeClick()) { /* sem cena: descarta cliques antigos */ }
            return;
        }
        // Morreu, renasceu, trocou de mundo ou desconectou: restaura imediatamente
        if (mc.player != s.player || mc.level != s.level || s.player.isRemoved() || s.player.isDeadOrDying()) {
            stop();
            return;
        }
        while (CinematicKeys.SKIP.consumeClick()) skip();
        if (!CinematicSettings.cutscenes()) skip();
        if (mc.isPaused()) return;

        // Espectador (sem trava) que leva dano volta ao controle da câmera na hora
        if (!s.locked && s.player.hurtTime > s.lastHurtTime) skip();
        s.lastHurtTime = s.player.hurtTime;

        if (!s.bound && s.age < FOCUS_WAIT) {
            // O pacote da cena chegou antes da entidade (ex.: chefe que acabou de nascer): liga a câmera quando ela chegar
            Entity f = s.level.getEntity(s.focusId);
            if (f != null && !f.isRemoved()) s.bind(f, s.age);
        }
        if (s.focus != null) {
            if (s.focus.isRemoved()) s.focus = null;      // o foco sumiu: a câmera fica no último ponto conhecido
            else s.focusPos = s.focus.position();
        }
        if (s.focusPos.distanceToSqr(s.player.position()) > MAX_FOCUS_DISTANCE * MAX_FOCUS_DISTANCE) skip();

        s.age++;
        if (s.age >= s.end) {
            stop();
            return;
        }
        // Trava: solta todas as teclas antes do vanilla/núcleo lerem (movimento, inventário, magias Z X C G, menu...)
        if (s.locked) KeyMapping.releaseAll();
    }

    // =====================================================================================
    // Câmera
    // =====================================================================================

    private static float blendTime(Scene s) {
        return Mth.clamp(s.total * 0.2F, 4F, MAX_BLEND);
    }

    /** Peso da câmera de cinema (0 = câmera normal, 1 = câmera da cena). */
    private static float weight(Scene s, float time) {
        float blend = blendTime(s);
        return Math.min(CinematicDraw.smooth((time - s.cameraSince) / blend), CinematicDraw.smooth((s.end - time) / blend));
    }

    /** Abertura das faixas de cinema (0..1). */
    static float barsOpen(Scene s, float time) {
        return Math.min(CinematicDraw.smooth(time / BARS_TIME), CinematicDraw.smooth((s.end - time) / BARS_TIME));
    }

    /**
     * Rápido no começo e no fim, lento no meio ("câmera lenta" enquanto o título aparece).
     * Integral normalizada de v(p) = 1 − 0,65·sen²(πp).
     */
    private static float slowMotion(float p) {
        double v = p - 0.65 * (p / 2.0 - Math.sin(2 * Math.PI * p) / (4 * Math.PI));
        return (float) (v / 0.675);
    }

    /** RenderFrameEvent.Pre: calcula a pose da cena para este frame e escolhe a perspectiva. */
    static void onFrameStart(RenderFrameEvent.Pre e) {
        Scene s = scene;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null || mc.level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        computeFrame(s, pt, e.getPartialTick().getRealtimeDeltaTicks());

        // Em 1ª pessoa o corpo do jogador não é desenhado: com a câmera longe do olho, passa para 3ª pessoa
        boolean firstPerson = forcedFrom != null || mc.options.getCameraType().isFirstPerson();
        boolean needThird = firstPerson && s.cameraEnabled && s.frameW > 0.001F
                && s.framePos.distanceTo(s.player.getEyePosition(pt)) * s.frameW > 0.8;
        if (needThird && forcedFrom == null) {
            forcedFrom = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!needThird && forcedFrom != null) {
            restorePerspective(mc);
        }
    }

    private static void computeFrame(Scene s, float pt, float realDelta) {
        CutsceneShot shot = s.shot;
        float time = s.time(pt);
        float p = Mth.clamp(time / s.total, 0F, 1F);
        float eased = CinematicDraw.smooth(p);
        s.frameW = weight(s, time);
        float punch = 1F - Mth.clamp(time / 6F, 0F, 1F);
        s.frameFov = Mth.lerp(CinematicDraw.easeOut(p), shot.fovStart(), shot.fovEnd());
        s.framePunch = 0.08F * shot.flash() * punch * punch;   // "soco" de FOV junto com o clarão
        s.frameRoll = Mth.lerp(p, shot.rollStart(), shot.rollEnd());
        s.frameValid = true;
        if (!s.cameraEnabled) return;

        Vec3 base = s.focus != null ? s.focus.getPosition(pt) : s.focusPos;
        Vec3 aim = base.add(0, s.focusHeight * shot.lookHeight(), 0);
        float phi = (s.baseAngle + shot.startOffset() + shot.sweep() * slowMotion(p)) * Mth.DEG_TO_RAD;
        double radius = s.baseRadius * Mth.lerp(eased, shot.radiusStart(), shot.radiusEnd());
        double height = Mth.lerp(eased, shot.heightStart(), shot.heightEnd()) * Math.max(s.focusHeight, 1.6F);
        Vec3 desired = aim.add(-Mth.sin(phi) * radius, height, Mth.cos(phi) * radius);

        // Não atravessa paredes: encurta até o bloco; volta a se afastar devagar (sem trancos)
        Vec3 dir = desired.subtract(aim);
        double len = dir.length();
        if (len < 1.0E-3) {
            dir = new Vec3(0, 0, 1);
            len = 1.0E-3;
        }
        Vec3 unit = dir.scale(1.0 / len);
        double allowed = len;
        BlockHitResult hit = s.level.clip(new ClipContext(aim, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, s.player));
        if (hit.getType() != HitResult.Type.MISS) allowed = Math.max(0.5, hit.getLocation().distanceTo(aim) - 0.3);
        if (s.camDist < 0 || allowed < s.camDist) s.camDist = allowed;
        else s.camDist += (allowed - s.camDist) * (1.0 - Math.exp(-Math.max(0F, realDelta) * 0.6));
        Vec3 cam = aim.add(unit.scale(Math.min(s.camDist, len)));

        Vec3 look = aim.subtract(cam);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        s.framePos = cam;
        s.frameYaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        s.framePitch = Mth.clamp((float) (-Mth.atan2(look.y, horizontal) * Mth.RAD_TO_DEG), -89F, 89F);
    }

    /** ComputeCameraAngles: tremor de tela (sempre, com ou sem cena). */
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!CameraShake.active()) return;
        CameraShake.offsets((float) e.getPartialTick(), SHAKE);
        e.setYaw(e.getYaw() + SHAKE[0]);
        e.setPitch(e.getPitch() + SHAKE[1]);
        e.setRoll(e.getRoll() + SHAKE[2]);
    }

    /** ComputeFov: aplica a pose da cena (misturada com a câmera normal) e o zoom. */
    static void onComputeFov(ViewportEvent.ComputeFov e) {
        Scene s = scene;
        if (s == null || !s.frameValid || !e.usedConfiguredFov()) return;
        Minecraft mc = Minecraft.getInstance();
        float w = s.frameW;
        if (w > 0F && s.cameraEnabled && mc.getCameraEntity() == s.player) {
            Camera cam = e.getCamera();
            float pt = (float) e.getPartialTick();
            CameraShake.offsets(pt, SHAKE);
            Vec3 basePos;
            float baseYaw, basePitch;
            if (cam.isDetached() && forcedFrom != null) {
                // A perspectiva foi trocada por nós: a pose "normal" é a do olho do jogador
                basePos = s.player.getEyePosition(pt);
                baseYaw = s.player.getViewYRot(pt) + SHAKE[0];
                basePitch = s.player.getViewXRot(pt) + SHAKE[1];
            } else {
                basePos = cam.getPosition();
                baseYaw = cam.getYRot();
                basePitch = cam.getXRot();
            }
            Vec3 pos = basePos.lerp(s.framePos, w);
            float yaw = baseYaw + Mth.wrapDegrees(s.frameYaw + SHAKE[0] - baseYaw) * w;
            float pitch = basePitch + (s.framePitch + SHAKE[1] - basePitch) * w;
            float roll = cam.getRoll() + s.frameRoll * w;
            CameraAccess.apply(cam, pos, yaw, pitch, roll);
        }
        float fovScale = mc.options.fovEffectScale().get().floatValue();
        float mult = 1F + ((s.frameFov - 1F) * w + s.framePunch) * fovScale;
        if (mult != 1F) e.setFOV(e.getFOV() * mult);
    }

    /** A câmera está fora do olho do jogador (esconde mão, mira e contorno de bloco). */
    private static boolean cameraAway() {
        Scene s = scene;
        return s != null && s.cameraEnabled && s.frameValid && s.frameW > 0.02F;
    }

    static void onRenderHand(RenderHandEvent e) {
        if (cameraAway()) e.setCanceled(true);
    }

    static void onBlockHighlight(RenderHighlightEvent.Block e) {
        if (cameraAway()) e.setCanceled(true);
    }

    /** Esconde o HUD para quem está travado na cena e a mira quando a câmera está longe. */
    static void onGuiLayer(RenderGuiLayerEvent.Pre e) {
        Scene s = scene;
        if (s == null) return;
        ResourceLocation id = e.getName();
        if (id.getNamespace().equals(IsekaiMahou.MODID) && id.getPath().startsWith("cinematic_")) return;
        float time = s.time(e.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (s.locked && barsOpen(s, time) > 0.5F) e.setCanceled(true);
        else if (id.equals(VanillaGuiLayers.CROSSHAIR) && cameraAway()) e.setCanceled(true);
    }

    // =====================================================================================
    // Controles
    // =====================================================================================

    static void onMovementInput(MovementInputUpdateEvent e) {
        Scene s = scene;
        if (s == null || e.getEntity() != s.player) return;
        Input in = e.getInput();
        if (!s.locked) {
            // Cena de OUTRO jogador (ex.: ultimate num PvP): andar devolve a câmera na hora, como atacar
            if (!s.skipping && !s.ownFocus && s.focus instanceof Player && s.cameraEnabled
                    && (in.forwardImpulse != 0F || in.leftImpulse != 0F || in.jumping)) {
                skip();
            }
            return;
        }
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** Ataque/uso/escolher bloco: travado = bloqueia; espectador = sai da cena e age normalmente. */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
        Scene s = scene;
        if (s == null) return;
        if (s.locked) {
            e.setCanceled(true);
            e.setSwingHand(false);
        } else {
            skip();
        }
    }

    static void onScroll(InputEvent.MouseScrollingEvent e) {
        Scene s = scene;
        if (s != null && s.locked) e.setCanceled(true);
    }

    /** Travado: o mouse não gira o personagem (a mira continua onde estava quando a cena acabar). */
    static void onPlayerTurn(CalculatePlayerTurnEvent e) {
        Scene s = scene;
        if (s == null || !s.locked) return;
        e.setMouseSensitivity(-1.0 / 3.0);   // 0,6·s + 0,2 = 0 → giro zero
        e.setCinematicCameraEnabled(false);
    }

    private CutsceneDirector() {}
}
