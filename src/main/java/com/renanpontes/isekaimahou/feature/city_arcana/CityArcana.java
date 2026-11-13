package com.renanpontes.isekaimahou.feature.city_arcana;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.settlements.CityRaids;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementData;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.WarService;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Prédios MÁGICOS das cidades fundadas (v1.5, docs/V15_DESIGN.md "city_arcana"): Barreira Arcana, Obelisco de
 * Nulificação, Torre de Mana e Farol de Vigia Arcano. As plantas ficam no catálogo do planejamento
 * ({@code settlements/build/Blueprints}) com moldes procedurais de 5 níveis ({@code ProcBuildings}).
 * <p>
 * Esta classe guarda um CACHE por cidade (refeito a cada 5 s a partir do {@link SettlementData}): caixa do território,
 * nível efetivo de cada prédio mágico e onde ele fica. Os efeitos ({@link ArcaneWard}, {@link NullObelisk},
 * {@link ArcanaTowers}) só olham este cache — nada de varrer cidades ou blocos a cada tick.
 */
public final class CityArcana {
    public static final String WARD = "arcane_ward";
    public static final String OBELISK = "null_obelisk";
    public static final String SPIRE = "mana_spire";
    public static final String BEACON = "arcane_beacon";
    public static final List<String> KEYS = List.of(WARD, OBELISK, SPIRE, BEACON);

    /** Altura extra do "domo" acima da caixa do território (projéteis vindos do alto também batem). */
    private static final int DOME_EXTRA_UP = 40;

    /** Prédio mágico pronto: célula, chave, nível efetivo (danificado = metade), se está danificado e o centro (chão). */
    public record Site(int cell, String key, int level, boolean damaged, BlockPos center) {}

    /** Estado em cache de uma cidade que tem algum prédio mágico. */
    public static final class City {
        public final Settlement s;
        public final ResourceKey<Level> dim;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        /** Nível efetivo (0 = não tem). */
        public int ward, obelisk, spireTotal;
        public boolean obeliskDamaged;
        /** Modo do Obelisco ({@link NullObelisk#MODE_OFF}...). */
        public int mode;
        public final List<Site> sites = new ArrayList<>();
        /** Último efeito visual de cada tipo (limita pacotes). */
        final long[] lastFx = new long[8];

        City(Settlement s) {
            this.s = s;
            this.dim = s.dim;
            int h = CityLayout.half(s);
            minX = s.heart.getX() - h;
            maxX = s.heart.getX() + h;
            minZ = s.heart.getZ() - h;
            maxZ = s.heart.getZ() + h;
            minY = s.heart.getY() - CityLayout.BOX_DOWN;
            maxY = s.heart.getY() + CityLayout.BOX_UP + DOME_EXTRA_UP;
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1 && y >= minY && y < maxY + 1;
        }

        /** Distância (horizontal) até a borda do território: 0 = em cima da borda. */
        public double edgeDistance(double x, double z) {
            return Math.min(Math.min(x - minX, maxX + 1 - x), Math.min(z - minZ, maxZ + 1 - z));
        }

        /** O prédio de maior nível com a chave (ou null). */
        @Nullable
        public Site site(String key) {
            Site best = null;
            for (Site st : sites) if (st.key().equals(key) && (best == null || st.level() > best.level())) best = st;
            return best;
        }

        /** Pode mandar o efeito visual {@code kind} agora? (no máximo um a cada {@code gap} ticks por cidade) */
        boolean fxReady(int kind, long now, int gap) {
            if (now - lastFx[kind] < gap) return false;
            lastFx[kind] = now;
            return true;
        }

        public int halfSize() {
            return (maxX - minX) / 2;
        }
    }

    private static volatile List<City> CITIES = List.of();
    /** Op: jogadores que contam como forasteiros inimigos (testar barreira e obelisco na própria cidade). */
    static final Set<UUID> DEBUG_OUTSIDER = new HashSet<>();

    // =====================================================================================
    //  Cache
    // =====================================================================================
    /** Refaz o cache a partir das cidades fundadas (barato: só lê os mapas de prédios). */
    public static void rebuild(MinecraftServer server) {
        List<City> out = new ArrayList<>();
        CityArcanaData data = CityArcanaData.get(server);
        for (Settlement s : SettlementData.get(server).all()) {
            if (!s.own()) continue;
            City c = null;
            for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
                String key = e.getValue();
                if (!KEYS.contains(key)) continue;
                int cell = e.getKey();
                boolean dmg = s.damaged.contains(cell);
                int lvl = s.levelOf(cell);
                int eff = dmg ? Math.max(1, (lvl + 1) / 2) : lvl;
                BlockPos cc = CityLayout.cellCenter(s.heart, cell);
                BlockPos center = new BlockPos(cc.getX(), s.plotBase.getOrDefault(cell, s.heart.getY()), cc.getZ());
                if (c == null) c = new City(s);
                c.sites.add(new Site(cell, key, eff, dmg, center));
                switch (key) {
                    case WARD -> c.ward = Math.max(c.ward, eff);
                    case OBELISK -> {
                        if (lvl > c.obelisk) {
                            c.obelisk = lvl;
                            c.obeliskDamaged = dmg;
                        }
                    }
                    case SPIRE -> c.spireTotal += eff;
                    default -> { }
                }
            }
            if (c == null) continue;
            c.mode = data.mode(s.id);
            out.add(c);
        }
        CITIES = List.copyOf(out);
    }

    public static List<City> cities() {
        return CITIES;
    }

    /** Cidade (com prédio mágico) que contém a posição, ou null. */
    @Nullable
    public static City at(Level level, double x, double y, double z) {
        List<City> list = CITIES;
        if (list.isEmpty()) return null;
        ResourceKey<Level> dim = level.dimension();
        for (City c : list) if (c.dim == dim && c.contains(x, y, z)) return c;
        return null;
    }

    @Nullable
    public static City at(Entity e) {
        return at(e.level(), e.getX(), e.getY(), e.getZ());
    }

    @Nullable
    public static City at(Level level, Vec3 p) {
        return at(level, p.x, p.y, p.z);
    }

    @Nullable
    public static City byId(String id) {
        for (City c : CITIES) if (c.s.id.equals(id)) return c;
        return null;
    }

    /** Alguma cidade com Barreira nesta dimensão? (para não rastrear projéteis à toa) */
    public static boolean anyWard(ResourceKey<Level> dim) {
        for (City c : CITIES) if (c.dim == dim && c.ward > 0) return true;
        return false;
    }

    // =====================================================================================
    //  Quem é amigo / inimigo da cidade
    // =====================================================================================
    /** A cidade protege esta entidade (Senhor, conselho, cidadãos, grupo do Senhor, NPCs da cidade, aldeões...). */
    public static boolean friendly(City c, @Nullable Entity e) {
        if (e == null) return false;
        if (e instanceof FakePlayer) return true; // Pedestais Rúnicos e outras automações
        if (e instanceof ServerPlayer sp) {
            if (DEBUG_OUTSIDER.contains(sp.getUUID())) return false;
            return SettlementService.isProtected(sp.server, c.s, sp);
        }
        if (e instanceof SettlementNpc n) return n.cityId().equals(c.s.id);
        if (e.getType() == EntityType.VILLAGER || e.getType() == EntityType.IRON_GOLEM) return true;
        if (e instanceof OwnableEntity o && o.getOwner() instanceof ServerPlayer owner) return friendly(c, owner);
        return false;
    }

    /** Inimigo declarado: monstro, criatura de horda, jogador em guerra com a cidade ou desobedecendo o toque de recolher. */
    public static boolean hostile(City c, @Nullable Entity e) {
        if (e == null || friendly(c, e)) return false;
        if (e instanceof ServerPlayer sp) {
            if (DEBUG_OUTSIDER.contains(sp.getUUID())) return true;
            return Allegiance.isEnemyOf(sp, c.s.id) || WarService.isTrespasser(sp.serverLevel(), c.s, sp);
        }
        if (e.getTags().contains(CityRaids.TAG)) return true;
        if (e instanceof Enemy) return true;
        return e instanceof Mob m && m.getTarget() != null && friendly(c, m.getTarget());
    }

    /** Chefe (tag c:bosses, provação, vida alta ou chefe de arena): a magia da cidade não o prende. */
    public static boolean isBoss(@Nullable Entity e) {
        return e instanceof LivingEntity && !(e instanceof net.minecraft.world.entity.player.Player)
                && (BossKit.isBossLike(e) || BossArena.isArenaBoss(e));
    }

    /** Dentro de uma arena de chefe selada: barreira e obelisco ficam quietos para a luta funcionar. */
    public static boolean inBossArena(Level level, Vec3 pos) {
        return level instanceof ServerLevel && BossArena.at(level, pos) != null;
    }

    /** Cor do escudo pelo estilo da cidade. */
    public static int wardColor(City c) {
        SettlementStyle st = c.s.style;
        return switch (st) {
            case DESERT -> 0xFFD45E;
            case NORDIC -> 0x6FF0E8;
            case ORIENTAL -> 0xFF7A6B;
            case ELVEN -> 0x8CF09A;
            case GOTHIC -> 0xB48CFF;
            case DWARVEN -> 0xFFA94D;
            default -> 0x7FD3FF;
        };
    }

    static void clear() {
        CITIES = List.of();
        DEBUG_OUTSIDER.clear();
    }

    private CityArcana() {}
}
