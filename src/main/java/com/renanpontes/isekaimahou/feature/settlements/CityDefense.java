package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * DEFESA DE CERCO (v1.0, city_life): os prédios de defesa AGEM durante os ataques de monstros ({@link CityRaids}).
 * <ul>
 *   <li><b>Torre de Vigia</b>: arqueiros atiram na horda (alcance 20 + 4/nível, dano 3 + nível, uma flecha a cada 2 s
 *       por torre) e os batedores avisam do ataque um dia antes;</li>
 *   <li><b>Muralha</b>: as 4 torres de canto de cada muralha pronta também atiram;</li>
 *   <li><b>Torre de Magia</b>: um raio arcano mais forte e de mais longe;</li>
 *   <li><b>Campo de Armadilhas</b> (prédio novo): monstros da horda perto dele levam dano e ficam lentos;</li>
 *   <li><b>Quartel</b>: quando a horda chega, guardas e milicianos da cidade ganham força e resistência.</li>
 * </ul>
 * Só atinge monstros marcados do ataque (nunca jogadores, moradores ou blocos). Prédio danificado não ajuda.
 */
public final class CityDefense {
    public static final String TRAP = "trap_field";
    private static final int TRAP_RADIUS = 9;

    /** Um ponto de tiro: posição do topo, alcance, dano e se é mágico. */
    public record Shooter(Vec3 pos, int range, float damage, boolean magic) {}

    /** Resumo para a tela. */
    public record Summary(int towers, int wallTowers, int mageTowers, int traps, int barracksLevel, int range, float damage) {}

    private static int base(Settlement s, int cell) {
        return s.plotBase.getOrDefault(cell, s.heart.getY() - 1);
    }

    /** Todos os pontos de tiro da cidade (torres de vigia, cantos das muralhas, torre de magia). */
    public static List<Shooter> shooters(Settlement s) {
        List<Shooter> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
            int cell = e.getKey();
            if (s.damaged.contains(cell)) continue;
            int lvl = s.levelOf(cell);
            BlockPos c = CityLayout.cellCenter(s.heart, cell);
            if (e.getValue().equals("watchtower")) {
                out.add(new Shooter(new Vec3(c.getX() + 0.5, base(s, cell) + 10, c.getZ() + 0.5), 20 + 4 * lvl, 3 + lvl, false));
            } else if (e.getValue().equals("magic_tower")) {
                out.add(new Shooter(new Vec3(c.getX() + 0.5, base(s, cell) + 14, c.getZ() + 0.5), 28 + 4 * lvl, 5 + 2 * lvl, true));
            }
        }
        for (int k : s.walls) {
            int d = CityLayout.wallDist(s, k);
            // onda 5: muralha de grau mais alto = torres que alcançam mais longe e batem mais forte
            int wt = CityRenewal.wallTierOf(s, k);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    out.add(new Shooter(new Vec3(s.heart.getX() + sx * d + 0.5, s.heart.getY() + 8, s.heart.getZ() + sz * d + 0.5), 22 + 2 * Math.max(0, wt - 1), 4F + Math.max(0, wt - 1), false));
                }
            }
        }
        return out;
    }

    /** Campos de armadilhas prontos: {célula, nível}. */
    public static List<int[]> traps(Settlement s) {
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
            if (e.getValue().equals(TRAP) && !s.damaged.contains(e.getKey())) out.add(new int[] {e.getKey(), s.levelOf(e.getKey())});
        }
        return out;
    }

    /** Maior nível de Quartel inteiro (0 = não tem). */
    public static int barracksLevel(Settlement s) {
        int best = 0;
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
            if (e.getValue().equals("barracks") && !s.damaged.contains(e.getKey())) best = Math.max(best, s.levelOf(e.getKey()));
        }
        return best;
    }

    public static boolean hasScouts(Settlement s) {
        for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
            if (e.getValue().equals("watchtower") && !s.damaged.contains(e.getKey())) return true;
        }
        return false;
    }

    public static Summary summary(Settlement s) {
        int towers = 0, mage = 0, range = 0;
        float dmg = 0;
        for (Shooter sh : shooters(s)) {
            if (sh.magic()) mage++;
            else towers++;
            range = Math.max(range, sh.range());
            dmg += sh.damage();
        }
        int wallTowers = 4 * s.walls.size();
        return new Summary(towers - wallTowers, wallTowers, mage, traps(s).size(), barracksLevel(s), range, dmg);
    }

    /**
     * Ajuda da cidade durante o ataque — chamado a cada 20 ticks com os monstros vivos da horda.
     *
     * @param tick contador de ticks do servidor (as torres atiram a cada 40, as armadilhas a cada 20)
     */
    public static void assist(ServerLevel sl, Settlement s, List<Mob> alive, int tick) {
        if (alive.isEmpty()) return;
        if (tick % 40 == 0) {
            for (Shooter sh : shooters(s)) {
                Mob target = nearest(alive, sh.pos(), sh.range());
                if (target == null) continue;
                shoot(sl, sh, target);
            }
        }
        for (int[] trap : traps(s)) {
            BlockPos c = CityLayout.cellCenter(s.heart, trap[0]);
            boolean any = false;
            for (Mob m : alive) {
                if (!m.isAlive()) continue;
                double dx = m.getX() - (c.getX() + 0.5), dz = m.getZ() - (c.getZ() + 0.5);
                if (dx * dx + dz * dz > TRAP_RADIUS * TRAP_RADIUS) continue;
                m.hurt(sl.damageSources().generic(), 1.5F + 0.5F * trap[1]);
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true, true));
                sl.sendParticles(ParticleTypes.CRIT, m.getX(), m.getY() + 0.3, m.getZ(), 6, 0.3, 0.2, 0.3, 0.1);
                any = true;
            }
            if (any) sl.playSound(null, c.getX(), base(s, trap[0]) + 1, c.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
    }

    @Nullable
    private static Mob nearest(List<Mob> alive, Vec3 from, int range) {
        Mob best = null;
        double bd = (double) range * range;
        for (Mob m : alive) {
            if (!m.isAlive()) continue;
            double dx = m.getX() - from.x, dz = m.getZ() - from.z;
            double d = dx * dx + dz * dz;
            if (d < bd && Math.abs(m.getY() - from.y) < 40) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** Um tiro: rastro de partículas do topo da torre até o monstro, som e dano. */
    private static void shoot(ServerLevel sl, Shooter sh, Mob target) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 d = to.subtract(sh.pos());
        int n = Math.max(4, Math.min(16, (int) (d.length() / 2)));
        ParticleOptions trail = sh.magic() ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT;
        for (int i = 1; i <= n; i++) {
            Vec3 p = sh.pos().add(d.scale(i / (double) n));
            sl.sendParticles(trail, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        sl.playSound(null, sh.pos().x, sh.pos().y, sh.pos().z, sh.magic() ? SoundEvents.ILLUSIONER_CAST_SPELL : SoundEvents.ARROW_SHOOT,
                SoundSource.NEUTRAL, 1.2F, sh.magic() ? 1.2F : 0.9F + sl.random.nextFloat() * 0.3F);
        sl.playSound(null, to.x, to.y, to.z, SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 0.8F, 1.1F);
        target.hurt(sl.damageSources().generic(), sh.damage());
    }

    /** A horda chegou: com Quartel, guardas e milicianos da cidade lutam mais forte por 4 minutos. */
    public static void rally(ServerLevel sl, Settlement s) {
        int lvl = barracksLevel(s);
        if (lvl <= 0) return;
        int h = CityLayout.half(s) + 16;
        AABB box = new AABB(s.heart).inflate(h, 48, h);
        for (SettlementNpc n : sl.getEntitiesOfClass(SettlementNpc.class, box, n -> n.cityId().equals(s.id)
                && (n.role() == Settlement.Role.GUARD || n.role() == Settlement.Role.MILITIA))) {
            n.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 4800, lvl >= 3 ? 1 : 0, true, true));
            n.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 4800, 0, true, false));
        }
        sl.playSound(null, s.heart, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 0.9F);
    }

    /** Dias até o próximo ataque marcado (0 = hoje / a qualquer momento), ou -1 se não há batedores ou nada marcado. */
    public static int forecastDays(MinecraftServer server, Settlement s) {
        if (!s.own() || s.level < 2 || !hasScouts(s) || !SettlementData.get(server).raidsEnabled) return -1;
        if (s.raidDue) return 0;
        if (s.nextRaidDay < 0) return -1;
        return (int) Math.max(0, s.nextRaidDay - SettlementService.day(server));
    }

    private CityDefense() {}
}
