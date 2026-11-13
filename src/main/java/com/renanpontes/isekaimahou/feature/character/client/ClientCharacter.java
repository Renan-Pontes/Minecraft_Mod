package com.renanpontes.isekaimahou.feature.character.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceProgress;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * Cópia no cliente: a raça do jogador local, a aparência racial dos jogadores visíveis e os visuais temporários (asas de
 * sombra da skill, Transformação racial).
 */
public final class ClientCharacter {
    /** Aparência de um jogador (raça null = sem traços; {@code level} = nível de raça I–V, 0 = desconhecido). */
    public record Look(Race race, int ancestryRgb, int level) {}

    /** Visual temporário: até quando (tempo de jogo do cliente) e quando começou (para a animação de abrir). */
    public record Fx(long start, long until) {}

    /** Ticks que a Transformação leva para se desfazer no cliente. */
    static final int FADE = 16;

    private static CharacterData data = new CharacterData();
    private static int version;
    private static final Map<Integer, Look> LOOKS = new ConcurrentHashMap<>();
    /** entityId → visual das asas. */
    private static final Map<Integer, Fx> WINGS = new ConcurrentHashMap<>();
    /** entityId → Transformação racial ativa. */
    private static final Map<Integer, Fx> FORMS = new ConcurrentHashMap<>();
    /** entityId → última batida de asas (tempo de jogo do cliente) — a camada desenha o golpe das asas. */
    private static final Map<Integer, Long> BEATS = new ConcurrentHashMap<>();
    /** Duração (ticks) do golpe de asas desenhado. */
    static final int BEAT_LEN = 9;

    public static CharacterData data() {
        return data;
    }

    /** Muda a cada pacote de dados (telas recalculam quando muda). */
    public static int version() {
        return version;
    }

    static void onData(CompoundTag tag) {
        CharacterData d = new CharacterData();
        Minecraft mc = Minecraft.getInstance();
        d.deserializeNBT(mc.level != null ? mc.level.registryAccess() : null, tag);
        d.dirty = false;
        data = d;
        version++;
    }

    static void onLook(CharacterNetwork.Look l) {
        if (l.race() < 0 || l.race() >= Race.ALL.length) LOOKS.remove(l.entityId());
        else LOOKS.put(l.entityId(), new Look(Race.byIndex(l.race()), l.ancestryRgb(), l.level()));
    }

    static void onFx(CharacterNetwork.RaceFx f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // v1.3: a sequência da Transformação (carga e explosão) é do FormFx
        if (f.kind() == CharacterNetwork.FX_CHARGE || f.kind() == CharacterNetwork.FX_BURST) {
            FormFx.onFx(f);
            return;
        }
        // v1.3.1: voo planado (batida de asas, mergulho, impacto)
        if (f.kind() == CharacterNetwork.FX_BEAT || f.kind() == CharacterNetwork.FX_DIVE || f.kind() == CharacterNetwork.FX_IMPACT) {
            FormFx.onFlightFx(f);
            return;
        }
        Map<Integer, Fx> map = f.kind() == CharacterNetwork.FX_WINGS ? WINGS : f.kind() == CharacterNetwork.FX_FORM ? FORMS : null;
        if (map == null) return;
        long now = mc.level.getGameTime();
        if (f.ticks() <= 0) {
            // A Transformação se desfaz aos poucos (a camada fecha nos últimos ticks) com o efeito de dissipar
            Fx old = map.get(f.entityId());
            if (map == FORMS && old != null && old.until() > now - 2) {
                // Desfeita antes da hora (leite, morte, troca): encurta; no fim natural ela já está fechando
                if (old.until() > now + FADE) map.put(f.entityId(), new Fx(old.start(), now + FADE));
                FormFx.onDetransform(f.entityId());
            } else {
                map.remove(f.entityId());
            }
            return;
        }
        Fx old = map.get(f.entityId());
        long start = old != null && old.until() > now ? old.start() : now;
        map.put(f.entityId(), new Fx(start, now + f.ticks()));
    }

    /** Transformação ativa na entidade (null = sem). */
    public static Fx form(int entityId) {
        return live(FORMS, entityId);
    }

    /** Marca uma batida de asas na entidade (agora). */
    static void markBeat(int entityId, long now) {
        BEATS.put(entityId, now);
    }

    /** Fase (0..1) do golpe de asas em andamento; −1 = nenhum. */
    public static float beatPhase(int entityId, long now, float pt) {
        Long b = BEATS.get(entityId);
        if (b == null) return -1F;
        float t = (now - b + pt) / BEAT_LEN;
        return t < 0F || t >= 1F ? -1F : t;
    }

    /** Nível de raça do jogador local (0 = sem raça). */
    public static int raceLevel() {
        CharacterData d = data;
        if (!d.created()) return 0;
        return RaceProgress.level(d.race(), d, ClientMagic.data().level());
    }

    private static Fx live(Map<Integer, Fx> map, int entityId) {
        Fx f = map.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (f == null || mc.level == null) return null;
        if (mc.level.getGameTime() > f.until()) {
            map.remove(entityId);
            return null;
        }
        return f;
    }

    public static Look look(int entityId) {
        return LOOKS.get(entityId);
    }

    /** Asas ativas na entidade (null = sem asas). */
    public static Fx wings(int entityId) {
        return live(WINGS, entityId);
    }

    /** Limpa visuais vencidos (a cada poucos segundos). */
    static void prune() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            WINGS.clear();
            FORMS.clear();
            BEATS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        WINGS.values().removeIf(f -> f.until() < now);
        FORMS.values().removeIf(f -> f.until() < now);
        BEATS.values().removeIf(b -> now - b > BEAT_LEN || b > now);
    }

    static void clear() {
        FormFx.clear();
        data = new CharacterData();
        LOOKS.clear();
        WINGS.clear();
        FORMS.clear();
        BEATS.clear();
        version++;
    }

    private ClientCharacter() {}
}
