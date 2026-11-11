package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.Arrays;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Estado animado do HUD que depende de TRANSIÇÕES (a camada só desenha):
 * conjuração começou/terminou, recarga acabou, ultimate ficou pronta, troca de loadout (v1.0: 9 loadouts, sem Roda),
 * mana suavizada, Limite treinável (treino armado, ganho de treino), subida de nível/Círculo (banners). Também dirige o Olho Analítico e as barras de vida flutuantes (por tick).
 */
final class HudState {
    /** Duração (ticks) do efeito de fim de conjuração. */
    static final int CAST_END_TICKS = 12;
    static final int READY_FLASH_TICKS = 8;
    static final int ULT_FLASH_TICKS = 30;
    static final int LOADOUT_FLASH_TICKS = 12;
    static final int LOADOUT_LABEL_TICKS = 50;
    static final int TRAIN_POPUP_TICKS = 70;
    /** Janela (ticks) em que o aperto da tecla ainda explica um início de conjuração vindo do servidor. */
    private static final int PRESS_WINDOW = 20;

    // ---------- Conjuração (dirigida pelo pacote CastState do núcleo, recebido pelo gancho) ----------
    static boolean casting;
    static int castSlot = -1;
    static int castTotal = 1;
    /** Ticks desde o início, contados aqui: o núcleo "esquece" a conjuração depois de total+40 (canalizações duram mais). */
    static int castElapsed;
    /** Canalizar: depois de encher, a barra fica "viva" enquanto a tecla é segurada. */
    static boolean castChannel;
    static Component castName = Component.empty();
    static int castColor = 0xFFB98CFF;
    static String castChant = "";
    static MoveTrigger castTrigger = MoveTrigger.NORMAL;
    /** Receita e elemento da magia em conjuração (ícone ao lado do nome na barra). */
    static SpellRecipe castRecipe;
    static Element castElement = Element.ARCANE;
    static int castEndTicks;
    static boolean castEndOk;
    static float castEndProgress;
    private static int releasedTicks;

    // ---------- Teclas: o servidor escolhe a versão pelo movimento NO APERTO (o pacote chega depois) ----------
    private static final boolean[] keyDown = new boolean[Progression.SLOTS];
    private static final MoveTrigger[] pressTrigger = new MoveTrigger[Progression.SLOTS];
    private static final long[] pressTick = new long[Progression.SLOTS];
    private static long clientTicks;

    // ---------- Slots ----------
    static final int[] readyFlash = new int[Progression.SLOTS];
    private static final String[] cdKey = new String[Progression.SLOTS];
    private static final int[] cdLeft = new int[Progression.SLOTS];
    static int ultFlash;
    private static float lastCharge = -1F;

    /** syncVersion no fim da sessão anterior: dados até ele não são "desta sessão" (evita avisos falsos ao entrar). */
    private static int sessionBaseline = 0;

    // ---------- Troca de loadout (rótulo "Loadout n/9" + lampejo no cabeçalho) ----------
    static int loadoutShowTicks;
    static int loadoutFlashTicks;
    /** Direção da última troca (-1 anterior, +1 próximo): o cabeçalho desliza para esse lado. */
    static int loadoutDir;
    private static int lastLoadout = -1;

    // ---------- Limite treinável (v1.0) ----------
    /** O recurso passou de 95%: esvaziar agora conta um treino (mesma regra do servidor, vista do cliente). */
    static boolean trainArmed;
    private static int lastTrainings = -1;
    private static int lastLimitSeen = -1;
    /** Ganho do último treino ("+N Limite!" flutuando ao lado da barra). */
    static int trainPopupTicks;
    static int trainPopupGain;

    // ---------- Progresso: banners de nível/Círculo ----------
    private static int lastLevel = -1;
    private static int lastCircle = -1;
    private static int lastGlyphCount = -1;
    /** Glifos Arcanos (sem Origem) conhecidos: o banner de nível conta quantos chegaram junto com o nível. */
    static int arcaneKnown;

    // ---------- Mana suavizada (por quadro, tempo real) ----------
    static float shownMana = -1F;
    static float ghostMana = -1F;
    private static float lastTarget = -1F;
    private static long ghostHoldUntil;
    private static long lastFrame;

    private HudState() {}

    /** Já chegou a primeira sincronização dos dados nesta sessão? */
    private static boolean synced() {
        return ClientMagic.syncVersion > sessionBaseline;
    }

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        // Pausado (um jogador): os temporizadores do HUD param junto com o jogo
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        clientTicks++;
        MagicData d = ClientMagic.data();
        LoadoutCache.refresh(d, mc.player.getAbilities().instabuild);
        trackKeys(mc);
        tickCast();
        trackCooldowns(d);
        trackCharge(mc, d);
        trackLoadout(d);
        trackTraining(mc, d);
        trackProgress(d);
        TargetEye.tick(mc);
        WorldHealthBars.tick(mc);
        for (int s = 0; s < readyFlash.length; s++) if (readyFlash[s] > 0) readyFlash[s]--;
        if (ultFlash > 0) ultFlash--;
        if (castEndTicks > 0) castEndTicks--;
        if (loadoutShowTicks > 0) loadoutShowTicks--;
        if (loadoutFlashTicks > 0) loadoutFlashTicks--;
        if (trainPopupTicks > 0) trainPopupTicks--;
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================
    /** Gancho {@code ClientHooks.castState} (thread principal): 0 começou, 1 terminou, 2 interrompida/falhou. */
    static void onCastState(CorePayloads.CastState p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.state() == 0) {
            if (mc.player == null || p.slot() < 0 || p.slot() >= Progression.SLOTS) return;
            startCast(mc, p.slot(), Math.max(1, p.ticks()), p.chant());
        } else if (casting) {
            endCast(p.state() == 1);
        }
    }

    private static void startCast(Minecraft mc, int slot, int total, String chant) {
        MagicData d = ClientMagic.data();
        LoadoutCache.refresh(d, mc.player.getAbilities().instabuild);
        // Mesma versão que o servidor escolheu: a do movimento no aperto da tecla (ou o atual, p.ex. gatilhos em sequência)
        MoveTrigger move = pressTrigger[slot] != null && clientTicks - pressTick[slot] <= PRESS_WINDOW
                ? pressTrigger[slot] : MoveTrigger.fromPlayer(mc.player);
        LoadoutCache.Variant v = LoadoutCache.resolve(slot, move, d);
        casting = true;
        castSlot = slot;
        castElement = v != null ? v.element() : Element.ARCANE;
        castTotal = total;
        castElapsed = 0;
        releasedTicks = 0;
        castEndTicks = 0;
        castChant = chant == null ? "" : chant;
        if (v != null) {
            castName = v.name();
            castColor = v.color();
            castRecipe = v.recipe();
            castTrigger = v.trigger();
            castChannel = v.channel();
        } else {
            castName = Component.empty();
            castColor = Element.ARCANE.argb();
            castRecipe = null;
            castTrigger = MoveTrigger.NORMAL;
            castChannel = false;
        }
    }

    private static void endCast(boolean ok) {
        castEndOk = ok;
        castEndProgress = ok ? 1F : Mth.clamp((float) castElapsed / castTotal, 0F, 1F);
        castEndTicks = CAST_END_TICKS;
        casting = false;
        castSlot = -1;
    }

    private static void tickCast() {
        if (!casting) return;
        castElapsed++;
        releasedTicks = CoreKeys.SLOTS[castSlot].isDown() ? 0 : releasedTicks + 1;
        // Segurança se o fim nunca chegar (mesma folga do núcleo; canalização: tecla solta há 3 s)
        boolean stale = castChannel ? castElapsed > castTotal && releasedTicks > 60 : castElapsed > castTotal + 40;
        if (stale) endCast(castElapsed >= castTotal);
    }

    /** Fração da conjuração (0..1) com interpolação entre ticks. */
    static float castProgress(float partialTick) {
        return Math.min(1F, (castElapsed + partialTick) / castTotal);
    }

    /** Canalização sustentada (barra cheia, magia repetindo enquanto segura). */
    static boolean channelingPhase() {
        return casting && castChannel && castElapsed >= castTotal;
    }

    private static void trackKeys(Minecraft mc) {
        for (int s = 0; s < Progression.SLOTS; s++) {
            boolean down = mc.screen == null && CoreKeys.SLOTS[s].isDown();
            if (down && !keyDown[s]) {
                pressTrigger[s] = MoveTrigger.fromPlayer(mc.player);
                pressTick[s] = clientTicks;
            }
            keyDown[s] = down;
        }
    }

    // =====================================================================================
    //  Slots, carga e loadouts
    // =====================================================================================
    private static void trackCooldowns(MagicData d) {
        int lo = d.activeLoadout();
        for (int s = 0; s < Progression.SLOTS; s++) {
            String key = MagicData.slotKey(lo, s);
            int now = ClientMagic.cooldownLeft.getOrDefault(key, 0);
            // Só conta se é o MESMO slot do tick anterior (trocar de loadout não "termina" recargas)
            if (key.equals(cdKey[s]) && cdLeft[s] > 0 && now == 0) readyFlash[s] = READY_FLASH_TICKS;
            cdKey[s] = key;
            cdLeft[s] = now;
        }
    }

    private static void trackCharge(Minecraft mc, MagicData d) {
        if (!synced()) return;
        // v1.0: carga de ultimate ÚNICA (não é mais por elemento)
        float c = d.charge();
        float before = lastCharge;
        lastCharge = c;
        if (before < 0F || before >= Progression.MAX_CHARGE || c < Progression.MAX_CHARGE) return;
        if (!Progression.ultimatesAllowed(d.circle())) return;
        ultFlash = ULT_FLASH_TICKS;
        if (LoadoutCache.hasUltimate()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.9F));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 0.35F));
        }
    }

    private static void trackLoadout(MagicData d) {
        if (!synced()) return;
        int lo = d.activeLoadout();
        if (lastLoadout >= 0 && lo != lastLoadout) {
            loadoutShowTicks = LOADOUT_LABEL_TICKS;
            loadoutFlashTicks = LOADOUT_FLASH_TICKS;
            // Menor caminho no anel de 9 (do 9 para o 1 é "para frente")
            int diff = Math.floorMod(lo - lastLoadout, MagicData.LOADOUTS);
            loadoutDir = diff <= MagicData.LOADOUTS / 2 ? 1 : -1;
        }
        lastLoadout = lo;
    }

    /**
     * Limite treinável: "armado" quando o recurso passa de 95% e desarmado ao esvaziar (até 2%) — a mesma regra que o
     * servidor usa para contar um treino, só para o HUD mostrar a marca de "esvazie para treinar". O treino em si
     * chega pela sincronização ({@link MagicData#trainings()}): aí mostra "+N Limite!" ao lado da barra.
     */
    private static void trackTraining(Minecraft mc, MagicData d) {
        if (!synced()) return;
        int max = Math.max(1, ClientMagic.maxMana);
        float mana = ClientMagic.mana;
        if (mana >= max * ResourceTraining.ARM_FRACTION) trainArmed = true;
        else if (mana <= ResourceTraining.emptyThreshold(max)) trainArmed = false;

        int trainings = d.trainings();
        int limit = d.manaLimit();
        if (lastTrainings >= 0 && trainings > lastTrainings) {
            trainPopupGain = Math.max(0, limit - lastLimitSeen);
            if (trainPopupGain > 0) {
                trainPopupTicks = TRAIN_POPUP_TICKS;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.8F));
            }
        }
        lastTrainings = trainings;
        lastLimitSeen = limit;
    }

    /** Treinos já feitos hoje (dia do mundo), lidos dos contadores diários sincronizados. */
    static int trainingsToday(Minecraft mc, MagicData d) {
        if (mc.level == null) return 0;
        String key = "daily." + ResourceTraining.DAILY_KEY;
        int day = (int) Math.floorDiv(mc.level.getDayTime(), 24000L);
        if (d.counter(key + ".day") != day) return 0;
        return Math.max(0, Math.min(ResourceTraining.TRAININGS_PER_DAY, d.counter(key)));
    }

    /**
     * Subiu de nível/Círculo? (comparando com o último valor visto nesta sessão; a 1ª sincronização só registra).
     * Círculo antes do nível: na Ascensão o banco de XP vira níveis na mesma sincronização.
     */
    private static void trackProgress(MagicData d) {
        if (!synced()) return;
        int arcaneBefore = arcaneKnown;
        int glyphs = d.glyphs().size();
        if (glyphs != lastGlyphCount) {
            lastGlyphCount = glyphs;
            arcaneKnown = countArcane(d);
        }
        if (lastLevel < 0) {
            lastLevel = d.level();
            lastCircle = d.circle();
            return;
        }
        if (d.circle() > lastCircle) HudBanner.circleUp(d, lastCircle, d.circle());
        if (d.level() > lastLevel) HudBanner.levelUp(d, lastLevel, d.level(), arcaneBefore);
        lastLevel = d.level();
        lastCircle = d.circle();
    }

    private static int countArcane(MagicData d) {
        int n = 0;
        for (ResourceLocation id : d.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.element() == Element.ARCANE && g.origin() == null) n++;
        }
        return n;
    }

    // =====================================================================================
    //  Mana
    // =====================================================================================
    /** Atualiza a mana mostrada (chamado por quadro). */
    static void updateMana(float target) {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0F : Math.min(0.25F, (now - lastFrame) / 1000F);
        lastFrame = now;
        if (shownMana < 0) {
            shownMana = ghostMana = lastTarget = target;
            return;
        }
        // Queda de mana: a barra "fantasma" segura um instante e depois desce (mostra quanto gastou)
        if (target < lastTarget - 0.5F) ghostHoldUntil = now + 450;
        lastTarget = target;
        shownMana += (target - shownMana) * (1F - (float) Math.exp(-dt * 14F));
        if (Math.abs(target - shownMana) < 0.05F) shownMana = target;
        if (ghostMana < shownMana) ghostMana = shownMana;
        else if (now > ghostHoldUntil) ghostMana += (shownMana - ghostMana) * (1F - (float) Math.exp(-dt * 5F));
    }

    static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        casting = false;
        castSlot = -1;
        castEndTicks = 0;
        Arrays.fill(keyDown, false);
        Arrays.fill(pressTrigger, null);
        Arrays.fill(cdKey, null);
        Arrays.fill(readyFlash, 0);
        ultFlash = 0;
        lastCharge = -1F;
        sessionBaseline = ClientMagic.syncVersion;
        lastLoadout = -1;
        loadoutShowTicks = loadoutFlashTicks = 0;
        loadoutDir = 0;
        trainArmed = false;
        lastTrainings = lastLimitSeen = -1;
        trainPopupTicks = trainPopupGain = 0;
        shownMana = ghostMana = lastTarget = -1F;
        lastFrame = 0;
        lastLevel = lastCircle = lastGlyphCount = -1;
        arcaneKnown = 0;
        LoadoutCache.invalidate();
        TargetEye.clear();
        WorldHealthBars.clear();
        HudBanner.clear();
        HudHealthBar.clear();
    }
}
