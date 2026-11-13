package com.renanpontes.isekaimahou.feature.settlements.build;

import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.C;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.W;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.body;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.fit;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.garnish;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.lamp;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.logY;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.mat;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.s;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.slabLow;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.turret;
import static com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.yard;

import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.M;
import com.renanpontes.isekaimahou.feature.settlements.build.ProcBuildings.G;
import com.renanpontes.isekaimahou.feature.settlements.build.ProcBuildings.P;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Visual novo (onda 8, city_looks) dos prédios de OFÍCIO e dos monumentos que eram molde .nbt: fazenda, mercado,
 * quartel, torre de vigia, templo, ferraria, estábulo, poço, jardim, castelo, obelisco, pirâmide e esfinge. Cada nível é
 * outro prédio à primeira vista — os detalhes de função crescem junto (fazenda: canteiro e espantalho → celeiro, silo e
 * moinho; ferraria: bancada sob telheiro → forja de dois andares com chaminé dupla e pátio de armas).
 * Materiais, quintal e peças comuns vêm de {@link LookBuildings}. Água só no chão (y=0), sempre cercada pelo próprio piso.
 */
final class LookWorks {

    // =====================================================================================================
    // Peças
    // =====================================================================================================

    private static BlockState crop(Block b) {
        BlockState st = b.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.AGE_7)) return st.setValue(BlockStateProperties.AGE_7, 7);
        if (st.hasProperty(BlockStateProperties.AGE_3)) return st.setValue(BlockStateProperties.AGE_3, 3);
        return st;
    }

    /** Plantação: terra arada úmida, uma vala de água em {@code waterZ} e fileiras de cultura (mais variadas a cada nível). */
    private static void field(G g, M m, int x0, int z0, int x1, int z1, int waterZ) {
        BlockState farm = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
        Block[] crops = m.lv >= 4 ? new Block[] {Blocks.WHEAT, Blocks.CARROTS, Blocks.BEETROOTS, Blocks.POTATOES}
                : m.lv >= 2 ? new Block[] {Blocks.WHEAT, Blocks.CARROTS} : new Block[] {Blocks.WHEAT};
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockState cur = g.get(x, 0, z);
                if (cur != m.soil && cur != m.paving || g.get(x, 1, z) != null) continue;
                if (z == waterZ) {
                    g.set(x, 0, z, s(Blocks.WATER));
                } else {
                    g.set(x, 0, z, farm);
                    g.set(x, 1, z, crop(crops[Math.floorMod(z, crops.length)]));
                }
            }
        }
    }

    private static void scarecrow(G g, M m, int x, int z) {
        g.set(x, 1, z, s(m.p.fence));
        g.set(x, 2, z, s(Blocks.HAY_BLOCK));
        g.set(x - 1, 2, z, s(m.p.fence));
        g.set(x + 1, 2, z, s(m.p.fence));
        g.set(x, 3, z, ProcBuildings.face(s(Blocks.CARVED_PUMPKIN), Direction.SOUTH));
    }

    /** Boneco de treino (feno num poste) ou alvo. */
    private static void dummy(G g, M m, int x, int z, boolean target) {
        g.set(x, 1, z, s(m.p.fence));
        g.set(x, 2, z, s(target ? Blocks.TARGET : Blocks.HAY_BLOCK));
    }

    /** Suporte de armas: cerca com grade de ferro em cima. */
    private static void rack(G g, M m, int x, int z) {
        g.set(x, 1, z, s(m.p.fence));
        g.set(x, 2, z, s(Blocks.IRON_BARS));
    }

    /** Barraca de lona (em A), comprida no eixo Z. */
    private static void tent(G g, M m, int x0, int z0, int len) {
        for (int z = z0; z < z0 + len; z++) {
            g.set(x0, 1, z, m.p.wool);
            g.set(x0 + 2, 1, z, m.p.wool);
            g.set(x0 + 1, 2, z, m.p.wool);
        }
        g.set(x0 + 1, 1, z0, m.p.wool);
        g.set(x0 + 1, 0, z0 + 1, s(Blocks.HAY_BLOCK));
    }

    /** Banca de feira 3×2: quatro postes, toldo de lã, balcão e a mercadoria. */
    private static void stall(G g, M m, int x, int z, int n) {
        P p = m.p;
        for (int[] c : new int[][] {{x, z}, {x + 2, z}, {x, z + 1}, {x + 2, z + 1}}) {
            g.set(c[0], 1, c[1], s(p.fence));
            g.set(c[0], 2, c[1], s(p.fence));
        }
        BlockState cloth = n % 2 == 0 ? p.wool : s(Blocks.WHITE_WOOL);
        for (int dx = 0; dx <= 2; dx++) {
            g.set(x + dx, 3, z, dx == 1 ? s(Blocks.WHITE_WOOL) : cloth);
            g.set(x + dx, 3, z + 1, dx == 1 ? cloth : s(Blocks.WHITE_WOOL));
        }
        g.set(x + 1, 1, z + 1, ProcBuildings.slab(p.slab, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        Block[] goods = {Blocks.HAY_BLOCK, Blocks.MELON, Blocks.PUMPKIN, Blocks.BARREL};
        g.set(x + 1, 1, z, s(goods[Math.floorMod(n, goods.length)]));
    }

    /** Chaminé de pedra do chão (y0) até {@code top}, soltando fumaça. */
    private static void chimney(G g, M m, int x, int z, int y0, int top) {
        for (int y = y0; y <= top; y++) g.set(x, y, z, m.p.base);
        g.set(x, top + 1, z, s(Blocks.CAMPFIRE));
    }

    private static void tree(G g, P p, int x, int z, int h) {
        BlockState leaf = ProcBuildings.leaves(p.leaves);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                g.set(x + dx, h, z + dz, leaf);
                if (dx == 0 || dz == 0) g.set(x + dx, h + 1, z + dz, leaf);
            }
        }
        for (int y = 1; y <= h; y++) g.set(x, y, z, logY(s(p.log)));
    }

    /** Ameias no topo de uma casca (telhado plano de fortaleza). */
    private static void battlement(G g, M m, int x0, int z0, int x1, int z1, int H) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                g.set(x, H + 1, z, (x + z) % 2 == 0 ? m.ground : slabLow(m.p.roofSlab));
            }
        }
    }

    /** Abre a frente de uma casca (vãos entre os pilares), deixando a viga de cima. */
    private static void openFront(G g, int x0, int x1, int z, int top, int... keep) {
        for (int x = x0 + 1; x < x1; x++) {
            boolean post = false;
            for (int k : keep) post |= k == x;
            if (post) continue;
            for (int y = 1; y <= top; y++) g.set(x, y, z, null);
        }
    }

    /** Pavilhão aberto: piso, pilares e viga em volta (o telhado fica por conta de quem chama). */
    private static void pavilion(G g, M m, int x0, int z0, int x1, int z1, int H) {
        P p = m.p;
        g.fill(x0, 0, z0, x1, 0, z1, p.floor);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                // cantos e dois pilares simétricos na frente e no fundo (o meio fica livre: é a entrada)
                boolean post = (x == x0 || x == x1) && (z == z0 || z == z1) || (z == z0 || z == z1) && Math.abs(x - C) == 3;
                if (post) for (int y = 1; y < H; y++) g.set(x, y, z, logY(p.pillar));
                g.set(x, H, z, ProcBuildings.axis(p.beam, x == x0 || x == x1 ? Direction.Axis.Z : Direction.Axis.X));
            }
        }
    }

    // =====================================================================================================
    // Fazenda, mercado, quartel, torre de vigia
    // =====================================================================================================

    /**
     * Fazenda: 1 canteiro e espantalho; 2 roça cercada com telheiro de ferramentas; 3 celeiro pequeno e cercado dos
     * animais; 4 celeiro de dois andares com silo; 5 celeiro grande, silo alto e moinho de vento.
     */
    static Templates.Tpl farm(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        if (lv == 1) {
            yard(g, m, 7);
            field(g, m, 2, 2, 10, 6, 4);
            scarecrow(g, m, 3, 8);
        } else if (lv == 2) {
            yard(g, m, 4);
            for (int[] c : new int[][] {{5, 1}, {7, 1}, {5, 3}, {7, 3}}) {
                g.set(c[0], 1, c[1], s(p.fence));
                g.set(c[0], 2, c[1], s(p.fence));
            }
            g.fill(5, 3, 1, 7, 3, 3, slabLow(p.roofSlab));
            g.set(6, 1, 1, s(Blocks.COMPOSTER));
            g.set(6, 1, 2, s(Blocks.HAY_BLOCK));
            field(g, m, 1, 5, 11, 10, 7);
            scarecrow(g, m, 2, 3);
        } else {
            int[] f = new int[][] {{4, 1, 8, 5}, {2, 1, 8, 5}, {1, 1, 7, 6}}[lv - 3];
            int storeys = fit(m, lv >= 4 ? 2 : 1, f[2] - f[0] + 1);
            yard(g, m, f[3]);
            int H = body(g, m, f[0], f[1], f[2], f[3], storeys, true);
            ProcBuildings.roof(g, p, f[0], f[1], f[2], f[3], H);
            // feno no celeiro (e no mezanino)
            g.set(f[0] + 1, 1, f[3] - 1, s(Blocks.HAY_BLOCK));
            g.set(f[2] - 1, 1, f[1] + 1, s(Blocks.HAY_BLOCK));
            if (storeys > 1) g.fill(f[2] - 2, ProcBuildings.sh(lv) + 1, f[1] + 1, f[2] - 1, ProcBuildings.sh(lv) + 1, f[1] + 2, s(Blocks.HAY_BLOCK));
            if (lv == 3) {
                // cercado dos animais ao lado do celeiro
                for (int x = 9; x <= 11; x++) for (int z = 1; z <= 4; z++) if (x == 9 || x == 11 || z == 1 || z == 4) g.set(x, 1, z, s(p.fence));
                g.set(10, 1, 2, s(Blocks.HAY_BLOCK));
                field(g, m, 1, 7, 11, 10, 8);
                scarecrow(g, m, 2, 3);
            } else if (lv == 4) {
                turret(g, m, 9, 1, 3, 1, 6, true);
                field(g, m, 1, 7, 11, 10, 8);
                scarecrow(g, m, 10, 5);
            } else {
                turret(g, m, 9, 1, 3, 1, 9, true);
                // moinho: torre com as pás (lã) viradas para a frente
                int top = turret(g, m, 9, 5, 3, 1, 8, true);
                int hub = Math.min(6, top - 4);
                g.set(10, hub, 8, ProcBuildings.axis(s(p.log), Direction.Axis.Z));
                for (int d = 1; d <= 2; d++) {
                    g.set(10 - d, hub, 8, s(Blocks.WHITE_WOOL));
                    g.set(10 + d, hub, 8, s(Blocks.WHITE_WOOL));
                    g.set(10, hub - d, 8, s(Blocks.WHITE_WOOL));
                    g.set(10, hub + d, 8, s(Blocks.WHITE_WOOL));
                }
                field(g, m, 1, 9, 11, 11, 10);
                scarecrow(g, m, 2, 8);
            }
        }
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Mercado: 1 duas bancas de lona; 2 quatro bancas em volta do poço; 3 pavilhão coberto; 4 pavilhão largo com
     * torre do sino; 5 grande arcada de dois andares com torre do relógio.
     */
    static Templates.Tpl market(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        if (lv <= 2) {
            yard(g, m, 3);
            stall(g, m, 2, 6, 0);
            stall(g, m, 8, 6, 1);
            if (lv == 2) {
                stall(g, m, 2, 2, 2);
                stall(g, m, 8, 2, 3);
                g.fill(5, 0, 4, 7, 0, 6, m.path);
                g.set(6, 1, 5, ProcBuildings.waterCauldron());
            } else {
                g.set(6, 1, 4, s(Blocks.HAY_BLOCK));
                g.set(5, 1, 4, s(Blocks.COMPOSTER));
            }
        } else if (lv <= 4) {
            int x0 = lv == 3 ? 2 : 1, x1 = W - 1 - x0;
            yard(g, m, 7);
            // (5 de fundo: o telhado comum não põe chaminé num pavilhão aberto)
            pavilion(g, m, x0, 3, x1, 7, 4);
            ProcBuildings.roof(g, p, x0, 3, x1, 7, 4);
            stall(g, m, x0 + 1, 4, 0);
            stall(g, m, x1 - 3, 4, 1);
            if (lv == 4) {
                stall(g, m, 2, 9, 2);
                stall(g, m, 8, 9, 3);
                // lanternim com o sino no meio do telhado
                turret(g, m, 5, 4, 3, 5, 9, true);
                g.set(6, 7, 5, ProcBuildings.bell(Direction.SOUTH));
            }
        } else {
            yard(g, m, 8);
            int storeys = fit(m, 2, 11);
            int H = body(g, m, 1, 1, 11, 8, storeys, false);
            ProcBuildings.roof(g, p, 1, 1, 11, 8, H);
            // arcada: o térreo aberto na frente e nos lados
            int sh = ProcBuildings.sh(lv);
            openFront(g, 1, 11, 8, sh - 1, 3, 6, 9);
            for (int z = 2; z <= 7; z++) {
                if (z == 4 || z == 5) continue;
                for (int y = 1; y <= sh - 2; y++) {
                    g.set(1, y, z, null);
                    g.set(11, y, z, null);
                }
            }
            stall(g, m, 2, 3, 0);
            stall(g, m, 8, 3, 1);
            turret(g, m, 5, 6, 3, H + 1, Math.min(18, H + 6), true);
        }
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Quartel: 1 barracas de lona e bonecos de treino; 2 alojamento de madeira; 3 quartel de pedra de dois andares
     * com pátio de treino; 4 quartel com torre de canto; 5 fortaleza ameada com duas torres na frente.
     */
    static Templates.Tpl barracks(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        if (lv == 1) {
            yard(g, m, 5);
            tent(g, m, 2, 2, 4);
            tent(g, m, 8, 2, 4);
            g.set(6, 1, 4, s(Blocks.CAMPFIRE));
        } else {
            int[] f = new int[][] {{2, 2, 10, 6}, {2, 2, 10, 7}, {2, 2, 10, 7}, {2, 2, 10, 8}}[lv - 2];
            int storeys = fit(m, lv >= 3 ? 2 : 1, 9);
            yard(g, m, f[3]);
            int H = body(g, m, f[0], f[1], f[2], f[3], storeys, true);
            if (lv >= 5) battlement(g, m, f[0], f[1], f[2], f[3], H);
            else ProcBuildings.roof(g, p, f[0], f[1], f[2], f[3], H);
            for (int k = 0; k < storeys; k++) {
                int y = ProcBuildings.sh(lv) * k + 1;
                ProcBuildings.bed(g, f[2] - 1, y, f[1] + 1, Direction.SOUTH, p.bed);
                ProcBuildings.bed(g, f[2] - 3, y, f[1] + 1, Direction.SOUTH, p.bed);
            }
            if (lv == 4) {
                int top = turret(g, m, 1, 1, 3, 1, H + 4, false);
                g.set(2, top, 2, s(p.banner));
            } else if (lv >= 5) {
                for (int x : new int[] {1, 9}) {
                    int top = turret(g, m, x, 7, 3, 1, H + 4, false);
                    g.set(x + 1, top, 8, s(p.banner));
                }
                ProcBuildings.door(g, 6, 1, f[3], p.door);
            }
        }
        // pátio de treino na frente
        dummy(g, m, 3, 10, false);
        dummy(g, m, 9, 10, true);
        if (lv >= 3) rack(g, m, 2, 10);
        if (lv >= 4) rack(g, m, 10, 10);
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Torre de vigia: 1 plataforma de troncos; 2 torre de madeira coberta; 3 torre de pedra ameada; 4 torre alta com
     * balcão de tiro e telhado; 5 grande torre com farol.
     */
    static Templates.Tpl watchtower(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        yard(g, m, lv >= 5 ? 9 : 8);
        if (lv <= 2) {
            int deck = lv == 1 ? 5 : 7;
            for (int[] c : new int[][] {{5, 5}, {7, 5}, {5, 7}, {7, 7}}) {
                for (int y = 1; y <= deck + 1; y++) g.set(c[0], y, c[1], y <= deck ? logY(s(p.log)) : s(p.fence));
            }
            g.fill(5, deck, 5, 7, deck, 7, p.plank);
            for (int y = 1; y < deck; y++) g.set(6, y, 6, logY(s(p.log)));
            for (int y = 1; y <= deck; y++) g.set(6, y, 7, ProcBuildings.ladder(Direction.SOUTH));
            for (int[] c : new int[][] {{6, 5}, {5, 6}, {7, 6}}) g.set(c[0], deck + 1, c[1], s(p.fence));
            if (lv == 2) {
                for (int[] c : new int[][] {{5, 5}, {7, 5}, {5, 7}, {7, 7}}) g.set(c[0], deck + 2, c[1], s(p.fence));
                LookBuildings.pyramid(g, p, 5, 5, 7, 7, deck + 3);
                g.ring(5, 5, 7, 7, 3, p.plank);
            }
            g.set(6, deck + 1, 6, ProcBuildings.lantern(Blocks.LANTERN));
        } else {
            int a = lv >= 5 ? 3 : 4, b = W - 1 - a, h = lv == 3 ? 9 : lv == 4 ? 12 : 14;
            turret(g, m, a, a, b - a + 1, 1, h, false);
            g.set(6, 1, b, null);
            g.set(6, 2, b, null);
            for (int y = 1; y <= h; y++) g.set(a + 1, y, 6, ProcBuildings.ladder(Direction.EAST));
            if (lv >= 4) {
                // balcão de tiro: plataforma um bloco para fora, ameias, e o telhado sobre postes
                int oa = a - 1, ob = b + 1;
                for (int x = oa; x <= ob; x++) {
                    for (int z = oa; z <= ob; z++) {
                        boolean edge = x == oa || x == ob || z == oa || z == ob;
                        if (!edge) continue;
                        g.set(x, h, z, p.plank);
                        g.set(x, h + 1, z, (x + z) % 2 == 0 ? m.ground : slabLow(p.roofSlab));
                    }
                }
                for (int[] c : new int[][] {{oa, oa}, {ob, oa}, {oa, ob}, {ob, ob}}) {
                    g.set(c[0], h + 1, c[1], m.ground);
                    g.set(c[0], h + 2, c[1], s(p.fence));
                    g.set(c[0], h + 3, c[1], s(p.fence));
                }
                int top = LookBuildings.pyramid(g, p, oa, oa, ob, ob, h + 4);
                g.set(6, top - 1, 6, ProcBuildings.hanging(lv >= 5 ? Blocks.SOUL_LANTERN : Blocks.LANTERN));
                if (lv >= 5) {
                    g.set(6, h + 1, 6, s(Blocks.SEA_LANTERN));
                    g.set(6, Math.min(23, top + 1), 6, p.rich);
                    g.set(oa, h + 2, 6, s(p.banner));
                    g.set(ob, h + 2, 6, s(p.banner));
                }
            } else {
                g.set(6, h + 1, 6, ProcBuildings.lantern(Blocks.LANTERN));
            }
        }
        garnish(g, m);
        return g.tpl();
    }

    // =====================================================================================================
    // Templo, ferraria, estábulo, poço, jardim
    // =====================================================================================================

    /**
     * Templo (nível visual): 1–2 capela de madeira com sino; 3–4 igreja de pedra com torre na frente e vitrais;
     * 5 catedral com duas torres e rosácea.
     */
    static Templates.Tpl temple(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        if (lv <= 2) {
            yard(g, m, 9);
            int H = body(g, m, 4, 3, 8, 9, 1, true);
            ProcBuildings.roof(g, p, 4, 3, 8, 9, H);
            g.set(6, 1, 4, p.trim);
            g.set(6, 2, 4, ProcBuildings.lantern(Blocks.LANTERN));
            g.set(9, 1, 10, s(p.fence));
            g.set(9, 2, 10, s(p.fence));
            g.set(9, 3, 10, ProcBuildings.bell(Direction.SOUTH));
        } else {
            boolean big = lv >= 5;
            int x0 = big ? 2 : 3, x1 = W - 1 - x0, sh = ProcBuildings.sh(lv);
            int storeys = fit(m, 2, x1 - x0 + 1);
            yard(g, m, big ? 8 : 10);
            int H = body(g, m, x0, 1, x1, 8, storeys, big);
            // nave alta: sem o piso do meio nem a escada de mão
            if (storeys > 1) {
                g.fill(x0 + 1, sh, 2, x1 - 1, sh, 7, null);
                for (int y = 1; y < sh; y++) g.set(x0 + 1, y, 2, null);
            }
            ProcBuildings.roof(g, p, x0, 1, x1, 8, H);
            g.fill(5, 1, 2, 7, 1, 2, p.trim);
            g.set(6, 2, 2, ProcBuildings.lantern(Blocks.LANTERN));
            if (big) {
                for (int x : new int[] {x0, x1 - 2}) turret(g, m, x, 8, 3, 1, Math.min(17, H + 6), true);
                // rosácea sobre o portal
                for (int x = 5; x <= 7; x++) for (int y = sh + 1; y <= sh + 3; y++) g.set(x, y, 8, s(p.glass));
                g.set(6, sh + 2, 8, p.rich);
            } else {
                turret(g, m, 5, 8, 3, 1, Math.min(18, H + 5), true);
                g.set(6, 1, 8, null);
                g.set(6, 2, 8, null);
                ProcBuildings.door(g, 6, 1, 10, p.door);
            }
        }
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Ferraria: 1 bancada sob telheiro; 2 forja aberta com chaminé de pedra; 3 forja fechada com bigorna na varanda;
     * 4 forja de dois andares; 5 forja de dois andares com chaminé dupla e pátio de armas.
     */
    static Templates.Tpl smithy(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        if (lv == 1) {
            yard(g, m, 6);
            for (int[] c : new int[][] {{4, 4}, {8, 4}, {4, 6}, {8, 6}}) {
                g.set(c[0], 1, c[1], logY(s(p.log)));
                g.set(c[0], 2, c[1], logY(s(p.log)));
            }
            g.fill(4, 3, 4, 8, 3, 6, slabLow(p.roofSlab));
            g.fill(5, 0, 4, 7, 0, 6, p.floor);
            g.set(5, 1, 4, ProcBuildings.face(s(Blocks.FURNACE), Direction.SOUTH));
            g.set(6, 1, 5, ProcBuildings.face(s(Blocks.ANVIL), Direction.EAST));
            g.set(7, 1, 4, ProcBuildings.waterCauldron());
        } else {
            int[] f = new int[][] {{3, 3, 9, 7}, {3, 2, 9, 7}, {2, 2, 10, 7}, {2, 1, 10, 7}}[lv - 2];
            int storeys = fit(m, lv >= 4 ? 2 : 1, f[2] - f[0] + 1);
            yard(g, m, f[3]);
            int H = body(g, m, f[0], f[1], f[2], f[3], storeys, lv >= 3);
            ProcBuildings.roof(g, p, f[0], f[1], f[2], f[3], H);
            if (lv == 2) openFront(g, f[0], f[2], f[3], 3);
            // a chaminé da forja fica onde o telhado comum poria a dele (uma só coluna), descendo até a fornalha de
            // brasa no chão; no nível 5 há outra igual do lado oposto
            boolean auto = f[2] - f[0] >= 6 && f[3] - f[1] >= 5;
            boolean left = auto && ProcBuildings.hash(f[2] - f[0], H, f[3] - f[1], lv + 7 * st.ordinal()) % 2 == 0;
            int hx = left ? f[0] + 2 : f[2] - 2, hz = f[1] + 2, top = Math.min(21, H + (lv >= 4 ? 7 : 5) + (p.steep ? 3 : 0));
            for (int x : lv >= 5 ? new int[] {hx, hx == f[0] + 2 ? f[2] - 2 : f[0] + 2} : new int[] {hx}) {
                chimney(g, m, x, hz, 2, top);
                g.set(x, 0, hz, s(Blocks.MAGMA_BLOCK));
                g.set(x, 1, hz - 1, ProcBuildings.face(s(Blocks.BLAST_FURNACE), Direction.SOUTH));
            }
            g.set((f[0] + f[2]) / 2, 1, f[1] + 1, s(Blocks.SMITHING_TABLE));
            g.set((f[0] + f[2]) / 2, 1, f[1] + 3, ProcBuildings.face(s(Blocks.ANVIL), Direction.EAST));
            g.set(f[0] + 2, 1, f[3] - 1, ProcBuildings.waterCauldron());
            // do lado de fora: bigorna e rebolo (3+), suportes de armas (4+), pátio de armas (5)
            if (lv >= 3) {
                g.set(4, 1, f[3] + 2, ProcBuildings.face(s(Blocks.ANVIL), Direction.EAST));
                g.set(8, 1, f[3] + 2, ProcBuildings.grindstone(Direction.SOUTH));
            }
            if (lv >= 4) {
                rack(g, m, 2, f[3] + 2);
                rack(g, m, 10, f[3] + 2);
            }
            if (lv >= 5) {
                dummy(g, m, 3, 10, false);
                dummy(g, m, 9, 10, true);
                g.set(2, 3, f[3] + 1, ProcBuildings.wallBanner(p.wallBanner, Direction.SOUTH));
                g.set(10, 3, f[3] + 1, ProcBuildings.wallBanner(p.wallBanner, Direction.SOUTH));
            }
        }
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Estábulo: 1 amarradouro com feno e cocho; 2 baias abertas sob telheiro; 3 estábulo coberto; 4 estábulo com
     * palheiro em cima e piquete; 5 grande estábulo com torreão e piquete.
     */
    static Templates.Tpl stable(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        if (lv == 1) {
            yard(g, m, 5);
            for (int x = 3; x <= 9; x++) g.set(x, 1, 5, s(p.fence));
            g.set(3, 2, 5, s(p.fence));
            g.set(9, 2, 5, s(p.fence));
            g.set(4, 1, 3, s(Blocks.HAY_BLOCK));
            g.set(5, 1, 3, s(Blocks.HAY_BLOCK));
            g.set(8, 1, 3, ProcBuildings.waterCauldron());
        } else if (lv == 2) {
            yard(g, m, 5);
            g.fill(2, 0, 2, 10, 0, 5, p.floor);
            g.fill(2, 1, 2, 10, 3, 2, m.upper);
            for (int x = 2; x <= 10; x += 4) {
                for (int z = 3; z <= 5; z++) g.set(x, 1, z, s(p.fence));
                for (int y = 1; y <= 3; y++) g.set(x, y, 5, logY(s(p.log)));
            }
            g.fill(2, 4, 2, 10, 4, 5, slabLow(p.roofSlab));
            g.set(4, 1, 3, s(Blocks.HAY_BLOCK));
            g.set(8, 1, 3, s(Blocks.HAY_BLOCK));
        } else {
            int[] f = lv >= 5 ? new int[] {1, 1, 11, 7} : new int[] {2, 2, 10, lv == 3 ? 6 : 7};
            int storeys = fit(m, lv >= 4 ? 2 : 1, f[2] - f[0] + 1);
            yard(g, m, f[3]);
            int H = body(g, m, f[0], f[1], f[2], f[3], storeys, false);
            ProcBuildings.roof(g, p, f[0], f[1], f[2], f[3], H);
            openFront(g, f[0], f[2], f[3], 3, 6);
            for (int z = f[1] + 1; z < f[3]; z++) g.set(6, 1, z, s(p.fence));
            g.set(f[0] + 1, 1, f[1] + 1, s(Blocks.HAY_BLOCK));
            g.set(f[2] - 1, 1, f[1] + 1, s(Blocks.HAY_BLOCK));
            if (storeys > 1) g.fill(f[0] + 2, ProcBuildings.sh(lv) + 1, f[1] + 1, f[2] - 2, ProcBuildings.sh(lv) + 1, f[1] + 1, s(Blocks.HAY_BLOCK));
            if (lv >= 4) {
                // piquete na frente, com cocho
                for (int x = 1; x <= 11; x++) if (Math.abs(x - C) > 1) g.set(x, 1, 10, s(p.fence));
                g.set(2, 1, 9, ProcBuildings.waterCauldron());
            }
            if (lv >= 5) turret(g, m, f[2] - 2, f[3] - 2, 3, H + 1, H + 4, true);
        }
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Poço: 1 buraco com moldura de troncos; 2 poço de pedra com telhadinho; 3 poço coberto com bancos; 4 casa do
     * poço com pilares e cochos; 5 pavilhão da cisterna com chafariz.
     */
    static Templates.Tpl well(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        int r = lv >= 5 ? 4 : lv == 4 ? 3 : lv == 3 ? 2 : 1;
        yard(g, m, C + r);
        if (lv >= 3) g.fill(C - r, 0, C - r, C + r, 0, C + r, lv >= 5 ? p.floor : p.base);
        int w = lv >= 5 ? 1 : 0;
        g.fill(C - w, 0, C - w, C + w, 0, C + w, s(Blocks.WATER));
        // boca do poço
        g.ring(C - w - 1, C - w - 1, C + w + 1, C + w + 1, 1, lv == 1 ? m.upper : m.ground);
        if (lv <= 2) {
            for (int x : new int[] {C - 1, C + 1}) {
                g.set(x, 2, C, s(p.fence));
                g.set(x, 3, C, s(p.fence));
            }
            if (lv == 1) {
                g.set(C, 3, C, s(p.fence));
            } else {
                LookBuildings.pyramid(g, p, C - 1, C - 1, C + 1, C + 1, 4);
            }
            g.set(C, lv == 1 ? 2 : 3, C, s(Blocks.CHAIN));
            g.set(C + 2, 1, C + 1, ProcBuildings.waterCauldron());
        } else {
            int h = lv >= 5 ? 5 : lv == 4 ? 4 : 3;
            for (int x = C - r; x <= C + r; x += r) {
                for (int z = C - r; z <= C + r; z += r) {
                    if (x == C && z == C || lv < 5 && (x == C || z == C)) continue;
                    for (int y = 1; y <= h; y++) g.set(x, y, z, lv >= 4 ? logY(p.pillar) : s(p.fence));
                }
            }
            g.ring(C - r, C - r, C + r, C + r, h, ProcBuildings.axis(p.beam, Direction.Axis.X));
            int top = LookBuildings.pyramid(g, p, C - r, C - r, C + r, C + r, h + 1);
            g.set(C, top - 1, C, ProcBuildings.hanging(Blocks.LANTERN));
            // bancos virados para o poço
            int bench = lv == 3 ? r : r - 1;
            g.set(C - bench, 1, C, ProcBuildings.stair(p.stairs, Direction.WEST));
            g.set(C + bench, 1, C, ProcBuildings.stair(p.stairs, Direction.EAST));
            if (lv >= 4) {
                g.set(C - r + 1, 1, C - r + 1, ProcBuildings.waterCauldron());
                g.set(C + r - 1, 1, C - r + 1, ProcBuildings.waterCauldron());
            }
            if (lv >= 5 && m.look.fountainPost != null) {
                // chafariz no meio da cisterna
                BlockState post = s(m.look.fountainPost);
                g.set(C, 0, C, p.base);
                g.set(C, 1, C, post);
                g.set(C, 2, C, post);
                g.set(C, 3, C, m.look.light[5]);
            }
        }
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Jardim: 1 canteiro de flores; 2 canteiros em fileiras com banco; 3 jardim de sebes com caminhos e árvores;
     * 4 pérgola e laguinho; 5 jardim nobre com chafariz, coreto e topiaria.
     */
    static Templates.Tpl garden(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        yard(g, m, lv >= 3 ? 0 : 5);
        BlockState[] fl = m.look.flowers;
        boolean sand = m.soil.is(Blocks.SAND);
        if (lv <= 2) {
            int a = lv == 1 ? 4 : 2, b = W - 1 - a;
            for (int x = a; x <= b; x++) {
                for (int z = a; z <= (lv == 1 ? 8 : 10); z++) {
                    if (lv == 2 && (z % 3 != 0 || x == C) || g.get(x, 0, z) != m.soil) continue;
                    g.set(x, 1, z, sand ? s(Blocks.DEAD_BUSH) : fl[Math.floorMod(x + z, fl.length)]);
                }
            }
            if (lv == 2) for (int x = 5; x <= 7; x++) g.set(x, 1, 1, ProcBuildings.stair(p.stairs, Direction.NORTH));
        } else {
            // caminhos em cruz e os canteiros dos quatro quadrantes
            for (int i = 1; i < W - 1; i++) g.set(i, 0, C, lv >= 5 ? p.base : m.path);
            if (lv >= 5) for (int[] q : new int[][] {{2, 8}, {8, 8}, {2, 2}, {8, 2}}) g.fill(q[0], 0, q[1], q[0] + 2, 0, q[1] + 2, s(sand ? Blocks.SAND : Blocks.GRASS_BLOCK));
            for (int[] q : new int[][] {{2, 8}, {8, 8}, {2, 2}, {8, 2}}) {
                for (int x = q[0]; x <= q[0] + 2; x++) {
                    for (int z = q[1]; z <= q[1] + 2; z++) {
                        boolean rim = x == q[0] || x == q[0] + 2 || z == q[1] || z == q[1] + 2;
                        if (rim && m.look.hedge != null && !sand) g.set(x, 1, z, m.look.hedge);
                        else if (!rim) g.set(x, 1, z, sand ? s(Blocks.DEAD_BUSH) : fl[Math.floorMod(x * 3 + z, fl.length)]);
                    }
                }
            }
            tree(g, p, 3, 3, lv >= 4 ? 4 : 3);
            tree(g, p, 9, 3, lv >= 4 ? 4 : 3);
            g.set(C, 1, 1, ProcBuildings.stair(p.stairs, Direction.NORTH));
            if (lv == 4) {
                // pérgola no cruzamento e laguinho com vitórias-régias
                for (int[] c : new int[][] {{5, 5}, {7, 5}, {5, 7}, {7, 7}}) for (int y = 1; y <= 3; y++) g.set(c[0], y, c[1], s(p.fence));
                g.fill(5, 4, 5, 7, 4, 7, ProcBuildings.leaves(p.leaves));
                g.fill(9, 0, 9, 10, 0, 10, s(Blocks.WATER));
                g.fill(9, 1, 9, 10, 1, 10, null);
                g.set(9, 1, 10, s(Blocks.LILY_PAD));
            } else if (lv >= 5) {
                // chafariz no cruzamento e coreto no fundo
                g.fill(C - 1, 0, C - 1, C + 1, 0, C + 1, s(Blocks.WATER));
                g.set(C, 0, C, p.base);
                if (m.look.fountainPost != null) {
                    g.set(C, 1, C, s(m.look.fountainPost));
                    g.set(C, 2, C, s(m.look.fountainPost));
                }
                g.set(C, 3, C, m.look.light[5]);
                for (int[] c : new int[][] {{4, 0}, {8, 0}, {4, 1}, {8, 1}}) for (int y = 1; y <= 3; y++) g.set(c[0], y, c[1], logY(p.pillar));
                g.fill(4, 4, 0, 8, 4, 1, slabLow(p.roofSlab));
                g.set(C, 5, 0, p.rich);
                lamp(g, m, 1, C, 2);
                lamp(g, m, W - 2, C, 2);
            }
        }
        garnish(g, m);
        return g.tpl();
    }

    // =====================================================================================================
    // Castelo e as maravilhas do deserto (monumentos de 3 níveis: visuais 1, 3 e 5)
    // =====================================================================================================

    /** Castelo: 1 torre de menagem de troncos; 3 castelo de pedra com quatro torres; 5 castelo real com torres altas e pináculo. */
    static Templates.Tpl castle(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        yard(g, m, 9);
        int storeys = lv >= 5 ? 3 : 2;
        int H = body(g, m, 3, 3, 9, 9, storeys, true);
        battlement(g, m, 3, 3, 9, 9, H);
        g.set(6, 3, 10, ProcBuildings.wallBanner(p.wallBanner, Direction.SOUTH));
        if (lv >= 3) {
            int th = lv >= 5 ? 14 : 11;
            for (int[] c : new int[][] {{1, 1}, {9, 1}, {1, 9}, {9, 9}}) turret(g, m, c[0], c[1], 3, 1, th, lv >= 5);
        } else {
            g.set(6, H + 1, 6, s(p.fence));
            g.set(6, H + 2, 6, s(p.banner));
        }
        if (lv >= 5) {
            int top = turret(g, m, 5, 5, 3, H + 1, H + 4, true);
            g.set(6, Math.min(23, top), 6, p.rich);
        } else if (lv >= 3) {
            g.set(6, H + 1, 6, m.ground);
            g.set(6, H + 2, 6, s(p.banner));
        }
        garnish(g, m);
        return g.tpl();
    }

    /** Obelisco: 1 marco de 8 blocos; 3 obelisco de 13 sobre base em degraus; 5 obelisco de 19 com ponta de ouro e quatro menores. */
    static Templates.Tpl obelisk(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        yard(g, m, C + (lv >= 5 ? 3 : lv >= 3 ? 2 : 1));
        int y = 1;
        if (lv >= 5) g.fill(C - 3, y, C - 3, C + 3, y++, C + 3, m.ground);
        if (lv >= 3) g.fill(C - 2, y, C - 2, C + 2, y++, C + 2, m.ground);
        g.fill(C - 1, y, C - 1, C + 1, y, C + 1, lv >= 3 ? p.trim : m.ground);
        int thick = lv >= 5 ? y + 4 : y;
        if (lv >= 5) g.fill(C - 1, y, C - 1, C + 1, thick, C + 1, m.upper);
        int top = lv >= 5 ? 19 : lv >= 3 ? 13 : 8;
        for (int yy = thick + 1; yy <= top; yy++) g.set(C, yy, C, (yy - thick) % 4 == 0 ? p.trim : m.upper);
        g.set(C, top + 1, C, lv >= 3 ? p.rich : p.trim);
        if (lv >= 5) {
            g.set(C, top + 2, C, s(Blocks.END_ROD));
            for (int[] c : new int[][] {{2, 2}, {10, 2}, {2, 10}, {10, 10}}) {
                for (int yy = 1; yy <= 4; yy++) g.set(c[0], yy, c[1], yy == 4 ? p.trim : m.upper);
                g.set(c[0], 5, c[1], ProcBuildings.lantern(Blocks.LANTERN));
            }
        } else if (lv >= 3) {
            for (int x : new int[] {C - 3, C + 3}) {
                g.set(x, 1, C + 3, p.trim);
                g.set(x, 2, C + 3, s(Blocks.CAMPFIRE));
            }
        }
        garnish(g, m);
        return g.tpl();
    }

    /** Pirâmide: 1 pirâmide baixa de tijolo de barro (9); 3 pirâmide de degraus de arenito (11) com ponta de ouro; 5 grande pirâmide (13) com faixas e farol. */
    static Templates.Tpl pyramid(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        int n = lv >= 5 ? 13 : lv >= 3 ? 11 : 9, o = (W - n) / 2, step = lv >= 3 ? 2 : 1;
        yard(g, m, o + n - 1);
        int y = 1, layer = 0;
        for (int a = o, b = o + n - 1; a <= b; a++, b--, layer++) {
            for (int k = 0; k < step && y < 22; k++, y++) {
                BlockState st2 = lv >= 3 && layer % 2 == 1 && k == step - 1 ? p.trim : m.upper;
                if (a == b) g.set(a, y, a, st2);
                else for (int x = a; x <= b; x++) for (int z = a; z <= b; z++) if (x == a || x == b || z == a || z == b || k == step - 1) g.set(x, y, z, st2);
            }
        }
        g.set(C, y, C, lv >= 3 ? p.rich : p.trim);
        if (lv >= 5) g.set(C, y + 1, C, s(Blocks.END_ROD));
        // portal: nicho na face da frente, com tochas
        int front = o + n - 1;
        for (int yy = 1; yy <= 2; yy++) {
            g.set(C, yy, front, null);
            g.set(C, yy, front - 1, s(Blocks.CHISELED_SANDSTONE));
        }
        g.set(C, 3, front, p.trim);
        if (front + 1 < W) {
            g.set(C - 1, 1, front + 1 < W - 1 ? front + 1 : front, p.trim);
            g.set(C + 1, 1, front + 1 < W - 1 ? front + 1 : front, p.trim);
        }
        garnish(g, m);
        return g.tpl();
    }

    /** Esfinge: 1 leão deitado pequeno; 3 esfinge de arenito com o toucado listrado; 5 grande esfinge com toucado de ouro e lápis e braseiros. */
    static Templates.Tpl sphinx(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        yard(g, m, W - 2);
        int hw = lv >= 3 ? 2 : 1, len = lv >= 5 ? 8 : lv >= 3 ? 7 : 6, bh = lv >= 5 ? 4 : lv >= 3 ? 3 : 2, hs = lv >= 5 ? 2 : 1;
        int zb = 1, zf = zb + len - 1;
        // corpo e ancas
        g.fill(C - hw, 1, zb, C + hw, bh, zf, m.upper);
        g.fill(C - hw, bh + 1, zb, C + hw, bh + 1, zb + 2, m.upper);
        // patas da frente
        for (int x : new int[] {C - hw, C + hw}) g.fill(x, 1, zf + 1, x, lv >= 5 ? 2 : 1, Math.min(W - 2, zf + 2), m.ground);
        // cabeça e toucado (listras nos lados e atrás)
        int hy0 = bh + 1, hy1 = bh + 1 + 2 * hs, hz0 = zf - 2 * hs;
        BlockState a = s(lv >= 5 ? Blocks.LAPIS_BLOCK : lv >= 3 ? Blocks.BLUE_TERRACOTTA : Blocks.SANDSTONE);
        BlockState b = lv >= 5 ? p.rich : m.ground;
        for (int x = C - hs; x <= C + hs; x++) {
            for (int yy = hy0; yy <= hy1; yy++) {
                for (int z = hz0; z <= zf; z++) {
                    boolean side = x == C - hs || x == C + hs || z == hz0 || yy == hy1;
                    g.set(x, yy, z, side && z < zf ? (yy % 2 == 0 ? a : b) : m.upper);
                }
            }
        }
        if (lv >= 3) {
            // abas do toucado descendo pelos ombros
            for (int x : new int[] {C - hs - 1, C + hs + 1}) for (int yy = bh; yy <= hy1 - 1; yy++) g.set(x, yy, zf - 1, yy % 2 == 0 ? a : b);
            for (int x : new int[] {1, W - 2}) {
                g.set(x, 1, W - 2, p.trim);
                g.set(x, 2, W - 2, s(Blocks.CAMPFIRE));
            }
        }
        garnish(g, m);
        return g.tpl();
    }

    private LookWorks() {}
}
