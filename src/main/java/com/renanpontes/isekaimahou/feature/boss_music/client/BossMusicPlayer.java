package com.renanpontes.isekaimahou.feature.boss_music.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.BossThemed;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.boss_music.BossMusicSounds;
import com.renanpontes.isekaimahou.feature.boss_music.BossThemes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * SÓ CLIENTE. O "DJ" das lutas de chefe:
 * <ul>
 * <li>a cada 10 ticks procura o chefe vivo mais perto (≤ 48 blocos; mantém o atual até 64 blocos para não piscar);</li>
 * <li>tema = {@link BossThemed#musicTheme()} → id da entidade com faixa própria → família (bestas, guardiões...) →
 * {@code generic_boss};</li>
 * <li>sem chefe: tema ambiente pedido por outros módulos ({@link BossMusicApi});</li>
 * <li>faixa em loop na categoria Música com fade in 2 s, crossfade 1,5 s ao trocar (Segunda Etapa), fade out 3 s;</li>
 * <li>chefe morto → corta rápido e toca a vinheta {@code boss_victory};</li>
 * <li>enquanto toca (e ~15 s depois) a música do vanilla fica calada ({@code SelectMusicEvent});</li>
 * <li>troca de mundo/dimensão ou desconexão para tudo na hora (nada vaza entre mundos).</li>
 * </ul>
 */
public final class BossMusicPlayer {
    private static final double FIND_RANGE = 48;
    private static final double KEEP_RANGE = 64;
    private static final int SCAN_EVERY = 10;
    private static final int FADE_IN = 40;
    private static final int CROSSFADE = 30;
    private static final int FADE_OUT = 60;
    private static final int VICTORY_FADE = 20;
    /** Depois da vinheta, segura a música de chefe (ela tem ~6 s). */
    private static final int VICTORY_HOLD = 240;
    /** Silêncio do vanilla depois que a nossa música termina (o vanilla recomeça na hora quando liberado). */
    private static final int VANILLA_QUIET = 300;
    /** Teste por comando dura até /mahou music stop ou 5 min. */
    private static final int FORCE_TICKS = 6000;

    /** Chefes do vanilla com música própria do jogo: deixamos o vanilla tocar. */
    private static final Set<ResourceLocation> EXCLUDED = Set.of(ResourceLocation.withDefaultNamespace("ender_dragon"),
            ResourceLocation.withDefaultNamespace("warden"));

    /** Cache por tipo: 1 = sempre chefe (tag/id conhecido), 0 = só se for inimigo com muita vida, -1 = nunca. */
    private static final Map<EntityType<?>, Byte> TYPE_CACHE = new IdentityHashMap<>();

    @Nullable private static ClientLevel lastLevel;
    @Nullable private static BossTrack current;
    private static final List<BossTrack> fading = new ArrayList<>();
    @Nullable private static LivingEntity boss;
    @Nullable private static String bossTheme;
    @Nullable private static SoundInstance sting;
    private static long ticks;
    private static long victoryUntil;
    private static long quietUntil;
    @Nullable private static String forced;
    private static long forcedUntil;
    /** Chefes que ainda vão fazer a ENTRADA (o servidor avisa): a música de proximidade espera por ela. */
    private static final java.util.Set<Integer> PENDING = new java.util.HashSet<>();
    /** O próximo tema começa do zero com fade curtíssimo (a entrada sincroniza com o início da faixa). */
    private static boolean freshStart;
    /** Faixas que falharam ao tocar (arquivo faltando?): não tenta de novo por 30 s, para não inundar o log. */
    private static final Map<String, Long> FAILED = new HashMap<>();

    // ------------------------------------------------------------------ ganchos

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            if (lastLevel != null) reset();
            return;
        }
        if (level != lastLevel) {
            reset();
            lastLevel = level;
        }
        ticks++;
        checkBoss(player);
        if (ticks % SCAN_EVERY == 0) scan(level, player);
        apply(mc.getSoundManager(), decide());
        Iterator<BossTrack> it = fading.iterator();
        while (it.hasNext()) {
            BossTrack t = it.next();
            if (t.isStopped() || !mc.getSoundManager().isActive(t)) it.remove();
        }
        if (sting != null && !mc.getSoundManager().isActive(sting)) sting = null;
    }

    /** O vanilla deve ficar calado agora? (lido pelo {@code SelectMusicEvent}). */
    static boolean suppressVanilla() {
        return current != null || !fading.isEmpty() || sting != null || ticks < quietUntil;
    }

    /** Sai do mundo/desconecta: para tudo e esquece o estado. */
    static void reset() {
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        if (current != null) sm.stop(current);
        for (BossTrack t : fading) sm.stop(t);
        if (sting != null) sm.stop(sting);
        current = null;
        fading.clear();
        sting = null;
        boss = null;
        bossTheme = null;
        forced = null;
        lastLevel = null;
        // as tags de entidade vêm do servidor: outro mundo/servidor pode ter outras
        TYPE_CACHE.clear();
        FAILED.clear();
        PENDING.clear();
        freshStart = false;
        BossEntranceFx.reset();
        victoryUntil = 0;
        quietUntil = 0;
        ticks = 0;
    }

    /** Comando de teste: força um tema ("" solta; "boss_victory" toca só a vinheta). */
    public static void forceTheme(String theme) {
        if (theme == null || theme.isEmpty()) {
            forced = null;
            return;
        }
        if (BossMusicSounds.VICTORY.equals(theme)) {
            playVictory(null);
            return;
        }
        if (theme.startsWith("victory_")) {
            playVictory(theme.substring(8));
            return;
        }
        if (!BossMusicSounds.isTheme(theme)) return;
        FAILED.remove(theme);
        forced = theme;
        forcedUntil = ticks + FORCE_TICKS;
    }

    // ------------------------------------------------------------------ chefe

    /** Todo tick, só o chefe acompanhado: morreu → vitória; sumiu/longe → solta (o fade out vem do apply). */
    private static void checkBoss(LocalPlayer player) {
        LivingEntity b = boss;
        if (b == null) return;
        if (b.isDeadOrDying()) {
            String t = bossTheme;
            boss = null;
            bossTheme = null;
            PENDING.remove(b.getId());
            playVictory(t);
            return;
        }
        if (b.isRemoved() || b.level() != player.level() || b.distanceToSqr(player) > KEEP_RANGE * KEEP_RANGE) {
            boss = null;
            bossTheme = null;
        }
    }

    private static void scan(ClientLevel level, LocalPlayer player) {
        if (boss != null) {
            bossTheme = themeOf(boss);
            return;
        }
        LivingEntity best = null;
        double bestD = FIND_RANGE * FIND_RANGE;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e instanceof Player) continue;
            double d = e.distanceToSqr(player);
            if (d > bestD || !le.isAlive() || le.isDeadOrDying() || PENDING.contains(e.getId())) continue;
            if (!isBoss(le)) continue;
            best = le;
            bestD = d;
        }
        boss = best;
        bossTheme = best == null ? null : themeOf(best);
    }

    private static boolean isBoss(LivingEntity e) {
        Byte kind = TYPE_CACHE.get(e.getType());
        if (kind == null) {
            kind = classify(e.getType());
            TYPE_CACHE.put(e.getType(), kind);
        }
        if (kind > 0) return true;
        if (kind < 0) return false;
        if (e instanceof BossThemed) return true;
        // chefes de OUTROS mods: inimigo com vida BASE de chefe e sem dono. Mobs do vanilla e do próprio mod nunca entram
        // por vida: o Abismo, afixos de torre, grupos e patronos do Bruxo deixam mobs comuns com 150+ de vida, e todos
        // eles tocavam o tema genérico (a faixa do Jonathan) "a quase todo momento".
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        String ns = id.getNamespace();
        if ("minecraft".equals(ns) || IsekaiMahou.MODID.equals(ns)) return false;
        if (!(e instanceof Enemy)) return false;
        var maxHp = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHp == null || maxHp.getBaseValue() < BossKit.BOSS_HEALTH) return false;
        return !(e instanceof OwnableEntity o && o.getOwnerUUID() != null);
    }

    private static byte classify(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (EXCLUDED.contains(id)) return -1;
        if (type.is(BossKit.BOSSES_TAG)) return 1;
        if (BossThemes.known(type)) return 1;
        return 0;
    }

    /** Tema do chefe: BossThemed → faixa com o id → família → genérico ({@link BossThemes}). */
    static String themeOf(LivingEntity e) {
        return BossThemes.themeOf(e);
    }

    // ------------------------------------------------------------------ tocagem

    @Nullable
    private static String decide() {
        if (forced != null) {
            if (ticks < forcedUntil) return forced;
            forced = null;
        }
        if (ticks < victoryUntil) return null;
        if (bossTheme != null) return bossTheme;
        String amb = BossMusicApi.currentAmbient();
        return BossMusicSounds.isTheme(amb) ? amb : null;
    }

    private static void apply(SoundManager sm, @Nullable String want) {
        if (current != null && !current.isStopped() && !sm.isActive(current) && ticks % 20 == 0) {
            // o motor de som perdeu a faixa (F3+T, troca de dispositivo...): toca de novo
            sm.stop(current);
            current = null;
        }
        if (want == null) {
            if (current != null) {
                current.fadeOut(FADE_OUT);
                fading.add(current);
                current = null;
                quietUntil = ticks + FADE_OUT + VANILLA_QUIET;
            }
            return;
        }
        if (current != null && current.theme.equals(want)) {
            quietUntil = ticks + VANILLA_QUIET;
            return;
        }
        Long failedAt = FAILED.get(want);
        if (failedAt != null && ticks - failedAt < 600 && ticks >= failedAt) return;
        SoundEvent ev = BossMusicSounds.sound(want);
        if (ev == null) return;
        boolean cross = current != null;
        if (current != null) {
            current.fadeOut(CROSSFADE);
            fading.add(current);
        }
        // retomando um tema que ainda está sumindo (ex.: o chefe voltou ao alcance): reaproveita a faixa
        BossTrack reuse = null;
        if (!freshStart) for (BossTrack t : fading) {
            if (t.theme.equals(want) && !t.isStopped() && sm.isActive(t)) {
                reuse = t;
                break;
            }
        }
        if (reuse != null) {
            fading.remove(reuse);
            reuse.fadeIn(cross ? CROSSFADE : FADE_IN);
            current = reuse;
        } else {
            BossTrack t = new BossTrack(want, ev, BossMusicApi.isAmbientTheme(want) ? 0.8F : 1.0F);
            t.fadeIn(freshStart ? 2 : cross ? CROSSFADE : FADE_IN);
            freshStart = false;
            sm.play(t);
            if (!sm.isActive(t)) {
                FAILED.put(want, ticks);
                return;
            }
            current = t;
        }
        Minecraft.getInstance().getMusicManager().stopPlaying();
        quietUntil = ticks + VANILLA_QUIET;
    }

    // ------------------------------------------------------------------ entrada (servidor → cliente)

    /** O servidor marcou/liberou um chefe que ainda vai fazer a entrada. */
    public static void pending(int entityId, boolean on) {
        if (on) PENDING.add(entityId);
        else PENDING.remove(entityId);
    }

    /** A entrada começou: o tema toca do INÍCIO agora (a intro da música é a entrada do chefe). */
    public static void entrance(com.renanpontes.isekaimahou.feature.boss_music.BossMusicNet.Entrance p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PENDING.remove(p.entityId());
        Entity e = mc.level.getEntity(p.entityId());
        SoundManager sm = mc.getSoundManager();
        if (BossMusicSounds.isTheme(p.theme())) {
            if (current != null) {
                if (current.theme.equals(p.theme())) sm.stop(current);
                else {
                    current.fadeOut(8);
                    fading.add(current);
                }
                current = null;
            }
            for (BossTrack t : fading) if (t.theme.equals(p.theme())) sm.stop(t);
            if (sting != null) {
                sm.stop(sting);
                sting = null;
            }
            victoryUntil = 0;
            forced = null;
            FAILED.remove(p.theme());
            freshStart = true;
            if (e instanceof LivingEntity le) boss = le;
            bossTheme = p.theme();
            apply(sm, decide());
        }
        if (e instanceof LivingEntity le) BossEntranceFx.start(le, p.theme(), p.awaken());
    }

    private static void playVictory(@Nullable String theme) {
        Minecraft mc = Minecraft.getInstance();
        SoundManager sm = mc.getSoundManager();
        if (current != null) {
            current.fadeOut(VICTORY_FADE);
            fading.add(current);
            current = null;
        }
        if (sting != null) sm.stop(sting);
        // a vinheta é o FINAL da própria música do chefe; sem ela, a vinheta padrão
        SoundEvent ev = BossMusicSounds.victoryOf(theme);
        if (ev == null || sm.getSoundEvent(ev.getLocation()) == null) ev = BossMusicSounds.sound(BossMusicSounds.VICTORY);
        if (ev != null) {
            sting = SimpleSoundInstance.forMusic(ev);
            sm.play(sting);
        }
        mc.getMusicManager().stopPlaying();
        victoryUntil = ticks + VICTORY_HOLD;
        quietUntil = ticks + VICTORY_HOLD + VANILLA_QUIET;
    }

    private BossMusicPlayer() {}
}
