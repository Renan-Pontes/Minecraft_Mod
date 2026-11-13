package com.renanpontes.isekaimahou.feature.settlements;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Templos elementais da cidade fundada (v1.4): quem a cidade protege (Senhor, conselho, cidadãos, grupo do Senhor),
 * DENTRO do território, recebe a bênção de cada templo pronto e não danificado:
 * <ul>
 *   <li>magias do elemento do templo (fusões contam pelos dois pais) causam +3% de dano por nível do templo
 *       (+15% no nível 5); o Templo Arcano dá +2% por nível a qualquer elemento;</li>
 *   <li>regeneração de mana: +0,4% da mana máxima a cada 5 s por nível de templo.</li>
 * </ul>
 * Cache por jogador renovado a cada 100 ticks pelo coração ({@link #tick}); sai do território = a bênção some em 7 s.
 */
public final class CityPerks {
    private record Blessing(Map<String, Integer> temples, long until, int color) {}

    private static final Map<UUID, Blessing> BLESSED = new HashMap<>();

    /** Templos prontos (elemento → nível), sem os danificados. */
    public static Map<String, Integer> temples(Settlement s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        CityMonuments.ensure(s);
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
            String k = e.getValue();
            if (!k.startsWith("temple_") || s.damaged.contains(e.getKey())) continue;
            // onda 6: templo adormecido (acima do limite de monumentos do degrau) não abençoa
            if (!CityMonuments.isActive(s, e.getKey())) continue;
            out.merge(k.substring("temple_".length()), s.levelOf(e.getKey()), Math::max);
        }
        return out;
    }

    /** Chamado pelo coração a cada 100 ticks (cidade carregada). */
    public static void tick(ServerLevel sl, Settlement s) {
        Map<String, Integer> t = temples(s);
        if (t.isEmpty()) return;
        int total = 0;
        int color = 0xB98CFF;
        for (Map.Entry<String, Integer> e : t.entrySet()) {
            total += e.getValue();
            Element el = Element.byId(e.getKey());
            if (el != null) color = el.color;
        }
        long until = sl.getGameTime() + 140;
        AABB box = new AABB(s.heart).inflate(CityLayout.half(s), 32, CityLayout.half(s));
        Vec3 rgb = Vec3.fromRGB24(color);
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (!SettlementService.isProtected(sl.getServer(), s, p)) continue;
            BLESSED.put(p.getUUID(), new Blessing(t, until, color));
            var data = MagicApi.get(p);
            data.addMana(data.maxMana() * 0.004F * total);
            sl.sendParticles(new DustParticleOptions(rgb.toVector3f(), 1.0F), p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.35, 0.5, 0.35, 0.01);
        }
    }

    /** Dano de magia: bônus dos templos para quem está abençoado. */
    public static void onSpellDamage(SpellDamageEvent.Pre e) {
        if (!(e.ctx().caster() instanceof ServerPlayer sp)) return;
        Blessing b = BLESSED.get(sp.getUUID());
        if (b == null) return;
        if (sp.serverLevel().getGameTime() > b.until()) {
            BLESSED.remove(sp.getUUID());
            return;
        }
        Element el = e.element();
        float bonus = 0F;
        for (Map.Entry<String, Integer> t : b.temples().entrySet()) {
            if (t.getKey().equals("arcane")) {
                bonus += 0.02F * t.getValue();
            } else {
                Element base = Element.byId(t.getKey());
                if (base != null && el != null && el.matches(base)) bonus += 0.03F * t.getValue();
            }
        }
        if (bonus > 0F) e.setAmount(e.getAmount() * (1F + bonus));
    }

    public static void forget(UUID id) {
        BLESSED.remove(id);
    }

    public static void clear() {
        BLESSED.clear();
    }

    private CityPerks() {}
}
