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
    /** Aparência de um jogador (raça null = sem traços). */
    public record Look(Race race, int ancestryRgb) {}

    /** Visual temporário: até quando (tempo de jogo do cliente) e quando começou (para a animação de abrir). */
    public record Fx(long start, long until) {}

    private static CharacterData data = new CharacterData();
    private static int version;
    private static final Map<Integer, Look> LOOKS = new ConcurrentHashMap<>();
    /** entityId → visual das asas. */
    private static final Map<Integer, Fx> WINGS = new ConcurrentHashMap<>();
    /** entityId → Transformação racial ativa. */
    private static final Map<Integer, Fx> FORMS = new ConcurrentHashMap<>();

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
        else LOOKS.put(l.entityId(), new Look(Race.byIndex(l.race()), l.ancestryRgb()));
    }

    static void onFx(CharacterNetwork.RaceFx f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Map<Integer, Fx> map = f.kind() == CharacterNetwork.FX_WINGS ? WINGS : f.kind() == CharacterNetwork.FX_FORM ? FORMS : null;
        if (map == null) return;
        if (f.ticks() <= 0) {
            map.remove(f.entityId());
            return;
        }
        long now = mc.level.getGameTime();
        Fx old = map.get(f.entityId());
        long start = old != null && old.until() > now ? old.start() : now;
        map.put(f.entityId(), new Fx(start, now + f.ticks()));
    }

    /** Transformação ativa na entidade (null = sem). */
    public static Fx form(int entityId) {
        return live(FORMS, entityId);
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
            return;
        }
        long now = mc.level.getGameTime();
        WINGS.values().removeIf(f -> f.until() < now);
        FORMS.values().removeIf(f -> f.until() < now);
    }

    static void clear() {
        data = new CharacterData();
        LOOKS.clear();
        WINGS.clear();
        FORMS.clear();
        version++;
    }

    private ClientCharacter() {}
}
