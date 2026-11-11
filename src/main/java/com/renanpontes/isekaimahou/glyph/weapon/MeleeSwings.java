package com.renanpontes.isekaimahou.glyph.weapon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Último ataque corpo a corpo de cada jogador (alvo, tick e força da barra de ataque), gravado no
 * {@link AttackEntityEvent} — que roda ANTES do dano e antes de a barra zerar. Serve para separar o golpe
 * principal das varridas (que também são PLAYER_ATTACK) e para saber a força do golpe.
 */
public final class MeleeSwings {
    private record Swing(UUID target, long tick, float strength) {}

    private static final Map<UUID, Swing> SWINGS = new HashMap<>();

    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || event.getTarget() == null) return;
        SWINGS.put(p.getUUID(), new Swing(event.getTarget().getUUID(), p.level().getGameTime(), p.getAttackStrengthScale(0.5F)));
    }

    /**
     * Força (0..1) do golpe PRINCIPAL deste tick contra {@code target}, ou -1 se o dano não veio dele
     * (varrida, espinhos, outro tick...).
     */
    public static float mainHitStrength(Player p, Entity target, long tick) {
        Swing s = SWINGS.get(p.getUUID());
        if (s == null || s.tick() != tick || !s.target().equals(target.getUUID())) return -1F;
        return s.strength();
    }

    public static void forget(UUID player) {
        SWINGS.remove(player);
    }

    public static void clear() {
        SWINGS.clear();
    }

    private MeleeSwings() {}
}
