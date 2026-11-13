package com.renanpontes.isekaimahou.feature.settlements;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.Templates;
import com.renanpontes.isekaimahou.feature.settlements.build.TierLook;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Onda 8 (city_looks) — "tem que ser bem diferente": o que cada DEGRAU põe na cidade além do piso, dos postes e das
 * fachadas de {@link CityRenewal}.
 * <ul>
 *   <li><b>Mobília da rua</b> ({@link #lot}): Vila = barris e carroças; Burgo = bancos e canteiros; Cidade = avenida
 *       arborizada com bancos e canteiros floridos; Capital = árvores com lanternas e estandartes grandes. Sempre na
 *       calçada dos lados LESTE e SUL de cada lote — cada rua ganha mobília de um lado só e sobra passagem (montaria).</li>
 *   <li><b>Praça</b> ({@link #plaza}): Acampamento = fogueira e barracas; Vila = bancas de feira e poço; Burgo = bancos
 *       (as fontes vêm do {@code CityRenewal}); Cidade = estátua e arcadas nas quatro entradas; Capital = coroa no
 *       Monumento do Coração, mosaico no piso e estandartes nos cantos.</li>
 * </ul>
 * Tudo passa por {@link CityRenewal#put}: só entra em bloco livre, fica anotado em {@link Settlement#tierBlocks} e sai
 * sozinho na troca de degrau — nada do jogador é tocado. Só sobre piso de rua firme (vale igual em chão natural, Ilha
 * Celeste ou deque): nada de enfeite no ar.
 */
public final class CityScenery {
    private static final int[] SLOTS = {-5, -2, 2, 5};

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    /** Tronco, folha e lã do estilo (a mobília usa a madeira da terra). */
    private static Block log(SettlementStyle st) {
        return switch (st) {
            case DESERT -> Blocks.ACACIA_LOG;
            case NORDIC, DWARVEN -> Blocks.SPRUCE_LOG;
            case ORIENTAL -> Blocks.CHERRY_LOG;
            case ELVEN -> Blocks.BIRCH_LOG;
            case GOTHIC -> Blocks.DARK_OAK_LOG;
            default -> Blocks.OAK_LOG;
        };
    }

    private static BlockState leaves(SettlementStyle st) {
        Block b = switch (st) {
            case DESERT -> Blocks.ACACIA_LEAVES;
            case NORDIC, DWARVEN -> Blocks.SPRUCE_LEAVES;
            case ORIENTAL -> Blocks.CHERRY_LEAVES;
            case ELVEN -> Blocks.AZALEA_LEAVES;
            case GOTHIC -> Blocks.DARK_OAK_LEAVES;
            default -> Blocks.OAK_LEAVES;
        };
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState wool(SettlementStyle st) {
        return s(switch (st) {
            case DESERT -> Blocks.ORANGE_WOOL;
            case NORDIC -> Blocks.CYAN_WOOL;
            case ORIENTAL -> Blocks.RED_WOOL;
            case ELVEN -> Blocks.LIME_WOOL;
            case GOTHIC -> Blocks.PURPLE_WOOL;
            case DWARVEN -> Blocks.YELLOW_WOOL;
            default -> Blocks.BLUE_WOOL;
        });
    }

    private static BlockState slab(SettlementStyle st, boolean top) {
        BlockState b = TierLook.wall(st, 2).merlonSlab;
        return b.hasProperty(SlabBlock.TYPE) ? b.setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM) : b;
    }

    private static BlockState hanging(BlockState light) {
        return light.hasProperty(LanternBlock.HANGING) ? light.setValue(LanternBlock.HANGING, true) : light;
    }

    private static BlockState bench(TierLook.Look look, Direction back) {
        Block b = look.fountainRim != null ? look.fountainRim : Blocks.STONE_BRICK_STAIRS;
        return b.defaultBlockState().setValue(StairBlock.FACING, back);
    }

    /** Dá para pôr mobília aqui? Em cima de piso de rua firme, com o chunk carregado e fora da faixa da muralha. */
    private static boolean spot(ServerLevel sl, Settlement s, BlockPos at) {
        if (!sl.hasChunk(at.getX() >> 4, at.getZ() >> 4)) return false;
        if (CityLayout.inWallBand(s, at.getX() - s.heart.getX(), at.getZ() - s.heart.getZ())) return false;
        return TierLook.isStreet(sl.getBlockState(at.below())) && CityRenewal.firm(sl, at.below()) && CityTerrain.free(sl.getBlockState(at), true);
    }

    private static boolean put(ServerLevel sl, Settlement s, BlockPos at, BlockState st) {
        return sl.hasChunk(at.getX() >> 4, at.getZ() >> 4) && CityRenewal.put(sl, s, at, st);
    }

    /** Árvore de calçada: tronco de 2, copa pequena; na Capital, uma lanterna pendurada do lado da rua. */
    private static void tree(ServerLevel sl, Settlement s, BlockPos at, Direction street, int tier) {
        BlockState lg = s(log(s.style)), lf = leaves(s.style);
        if (!put(sl, s, at, lg)) return;
        put(sl, s, at.above(), lg);
        put(sl, s, at.above(2), lf);
        for (Direction d : Direction.Plane.HORIZONTAL) put(sl, s, at.above(2).relative(d), lf);
        put(sl, s, at.above(3), lf);
        if (tier >= 5) put(sl, s, at.above().relative(street), hanging(TierLook.of(s.style).light[4]));
    }

    // =====================================================================================
    //  Mobília da rua de um lote
    // =====================================================================================
    /** Chamado por {@code CityRenewal.renew} depois da rua, dos postes e da fachada do lote. */
    static void lot(ServerLevel sl, Settlement s, int cell, BlockPos c, int base, int tier) {
        if (tier < 2) return;
        TierLook.Look look = TierLook.of(s.style);
        int seed = CityRenewal.hash(c.getX(), c.getZ());
        for (int side = 0; side < 2; side++) {
            Direction street = side == 0 ? Direction.EAST : Direction.SOUTH;
            for (int k = 0; k < SLOTS.length; k++) {
                int a = SLOTS[k];
                BlockPos at = new BlockPos(c.getX() + (side == 0 ? 7 : a), base + 1, c.getZ() + (side == 0 ? a : 7));
                if (!spot(sl, s, at)) continue;
                int pick = Math.floorMod(seed / 7 + k * 5 + side * 3, 4);
                boolean ends = k == 0 || k == SLOTS.length - 1;
                if (tier == 2) {
                    // Vila: barris e fardos junto às casas; aqui e ali uma carroça
                    if (k == 0 && pick < 3) {
                        put(sl, s, at, s(Blocks.BARREL));
                        if (pick == 0) put(sl, s, at.above(), s(Blocks.HAY_BLOCK));
                    } else if (k == 2 && pick == 1) {
                        cart(sl, s, at, side == 0 ? Direction.SOUTH : Direction.EAST);
                    }
                } else if (tier == 3) {
                    // Burgo: bancos virados para a rua e canteiros
                    if (k == 1 || k == 2 && pick < 2) put(sl, s, at, bench(look, street.getOpposite()));
                    else if (k == 0 && pick < 3) planter(sl, s, look, at, false);
                    else if (k == 3 && pick == 0) put(sl, s, at, s(Blocks.BARREL));
                } else {
                    // Cidade e Capital: avenida arborizada, bancos e canteiros floridos (estandartes grandes na Capital)
                    if (ends) tree(sl, s, at, street, tier);
                    else if (k == 1) put(sl, s, at, bench(look, street.getOpposite()));
                    else if (tier >= 5 && pick < 2) pole(sl, s, look, at, 3);
                    else planter(sl, s, look, at, true);
                }
            }
        }
    }

    private static void cart(ServerLevel sl, Settlement s, BlockPos at, Direction along) {
        BlockPos next = at.relative(along);
        if (!spot(sl, s, next)) return;
        put(sl, s, at, s(Blocks.COMPOSTER));
        put(sl, s, next, s(Blocks.COMPOSTER));
        put(sl, s, next.above(), s(Blocks.HAY_BLOCK));
        BlockPos handle = at.relative(along.getOpposite());
        if (spot(sl, s, handle)) put(sl, s, handle, TierLook.of(s.style).lamp[2][0]);
    }

    private static void planter(ServerLevel sl, Settlement s, TierLook.Look look, BlockPos at, boolean pot) {
        BlockState hedge = look.hedge != null ? look.hedge : leaves(s.style);
        if (!put(sl, s, at, hedge)) return;
        if (pot && hedge.isCollisionShapeFullBlock(sl, at)) {
            put(sl, s, at.above(), look.pots[Math.floorMod(CityRenewal.hash(at.getX(), at.getZ()), look.pots.length)]);
        }
    }

    /** Mastro com o estandarte da cidade. */
    private static void pole(ServerLevel sl, Settlement s, TierLook.Look look, BlockPos at, int h) {
        BlockState post = look.lamp[2][0];
        for (int i = 0; i <= h; i++) if (!CityTerrain.free(sl.getBlockState(at.above(i)), i == 0)) return;
        for (int i = 0; i < h; i++) put(sl, s, at.above(i), post);
        put(sl, s, at.above(h), look.banner.defaultBlockState());
    }

    // =====================================================================================
    //  Praça
    // =====================================================================================
    /** Chamado por {@code CityRenewal.plaza}, depois dos pilares do Monumento do Coração. */
    static void plaza(ServerLevel sl, Settlement s, int base, int tier, @Nullable Templates.Tpl tpl) {
        TierLook.Look look = TierLook.of(s.style);
        BlockPos h = new BlockPos(s.heart.getX(), base + 1, s.heart.getZ());
        if (tier == 1) {
            camp(sl, s, h);
        } else if (tier <= 4) {
            stall(sl, s, look, h.offset(-6, 0, -6), 0);
            stall(sl, s, look, h.offset(4, 0, -6), 1);
            if (tier == 2) well(sl, s, look, h.offset(5, 0, 5));
        }
        if (tier >= 3) {
            // bancos virados para o coração
            // (a 3 do eixo: os pilares das arcadas da Cidade ficam a 2)
            for (int x : new int[] {-3, 3}) {
                bench(sl, s, look, h.offset(x, 0, 6), Direction.SOUTH);
                bench(sl, s, look, h.offset(x, 0, -6), Direction.NORTH);
            }
        }
        if (tier >= 4) {
            statue(sl, s, look, h.offset(-5, 0, 5), tier);
            for (Direction d : Direction.Plane.HORIZONTAL) arcade(sl, s, look, h, d, tier);
        }
        if (tier >= 5) {
            crown(sl, s, look, h);
            mosaic(sl, s, look, base, tpl);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos at = h.offset(6 * sx, 0, 6 * sz);
                if (CityRenewal.firm(sl, at.below())) pole(sl, s, look, at, 4);
            }
        }
    }

    private static void bench(ServerLevel sl, Settlement s, TierLook.Look look, BlockPos at, Direction back) {
        if (CityRenewal.firm(sl, at.below())) put(sl, s, at, bench(look, back));
    }

    /** Acampamento: duas barracas de lona, a fogueira e os troncos de sentar. */
    private static void camp(ServerLevel sl, Settlement s, BlockPos h) {
        BlockState w = wool(s.style), white = s(Blocks.WHITE_WOOL);
        for (int i = 0; i < 2; i++) {
            int x0 = i == 0 ? -6 : 4, z0 = i == 0 ? 5 : -6;
            if (!CityRenewal.firm(sl, h.offset(x0 + 1, -1, z0))) continue;
            for (int dz = 0; dz < 2; dz++) {
                put(sl, s, h.offset(x0, 0, z0 + dz), dz == 0 ? w : white);
                put(sl, s, h.offset(x0 + 2, 0, z0 + dz), dz == 0 ? w : white);
                put(sl, s, h.offset(x0 + 1, 1, z0 + dz), dz == 0 ? white : w);
            }
        }
        BlockPos fire = h.offset(5, 0, 5);
        if (CityRenewal.firm(sl, fire.below()) && put(sl, s, fire, s(Blocks.CAMPFIRE))) {
            BlockState seat = s(log(s.style)).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
            put(sl, s, fire.offset(0, 0, 2), seat);
            put(sl, s, fire.offset(-2, 0, 0), s(log(s.style)).setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        }
    }

    /** Banca de feira: dois postes, toldo de lã (duas fileiras) e o balcão com mercadoria. */
    private static void stall(ServerLevel sl, Settlement s, TierLook.Look look, BlockPos o, int n) {
        if (!CityRenewal.firm(sl, o.below()) || !CityRenewal.firm(sl, o.offset(2, -1, 0))) return;
        if (!sl.hasChunk(o.getX() >> 4, o.getZ() >> 4) || !sl.hasChunk((o.getX() + 2) >> 4, (o.getZ() + 1) >> 4)) return;
        BlockState post = look.lamp[2][0], w = n % 2 == 0 ? wool(s.style) : s(Blocks.WHITE_WOOL), w2 = n % 2 == 0 ? s(Blocks.WHITE_WOOL) : wool(s.style);
        if (!put(sl, s, o, post) || !put(sl, s, o.offset(2, 0, 0), post)) return;
        put(sl, s, o.above(), post);
        put(sl, s, o.offset(2, 1, 0), post);
        for (int dx = 0; dx <= 2; dx++) {
            put(sl, s, o.offset(dx, 2, 0), dx == 1 ? w2 : w);
            put(sl, s, o.offset(dx, 2, 1), dx == 1 ? w : w2);
        }
        put(sl, s, o.offset(1, 0, 1), s(n % 2 == 0 ? Blocks.BARREL : Blocks.COMPOSTER));
        put(sl, s, o.offset(1, 0, 0), s(n % 2 == 0 ? Blocks.HAY_BLOCK : Blocks.MELON));
    }

    /** Poço da Vila: caldeirão cheio sob um telhadinho de quatro postes. */
    private static void well(ServerLevel sl, Settlement s, TierLook.Look look, BlockPos c) {
        if (!CityRenewal.firm(sl, c.below())) return;
        BlockState post = look.lamp[2][0];
        if (!put(sl, s, c, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3))) return;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            BlockPos q = c.offset(sx, 0, sz);
            if (!CityRenewal.firm(sl, q.below())) continue;
            put(sl, s, q, post);
            put(sl, s, q.above(), post);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(sl, s, c.offset(dx, 2, dz), slab(s.style, false));
    }

    /** Estátua do fundador: pedestal, corpo e a cabeça de luz (na Capital, mais alta). */
    private static void statue(ServerLevel sl, Settlement s, TierLook.Look look, BlockPos at, int tier) {
        if (!CityRenewal.firm(sl, at.below())) return;
        int h = tier >= 5 ? 4 : 3;
        for (int i = 0; i <= h; i++) if (!CityTerrain.free(sl.getBlockState(at.above(i)), i == 0)) return;
        put(sl, s, at, look.plazaEdge[tier]);
        for (int i = 1; i < h; i++) put(sl, s, at.above(i), i == h - 1 ? look.cap[tier] : look.pillar[tier]);
        put(sl, s, at.above(h), look.light[tier]);
    }

    /** Arcada numa entrada da praça: dois pilares, a verga e uma luz pendurada no meio. */
    private static void arcade(ServerLevel sl, Settlement s, TierLook.Look look, BlockPos h, Direction d, int tier) {
        Direction side = d.getClockWise();
        BlockPos mid = h.relative(d, 6);
        int top = tier >= 5 ? 5 : 4;
        for (int k = -2; k <= 2; k += 4) {
            BlockPos foot = mid.relative(side, k);
            if (!sl.hasChunk(foot.getX() >> 4, foot.getZ() >> 4) || !CityRenewal.firm(sl, foot.below())) return;
            for (int i = 0; i <= top; i++) if (!CityTerrain.free(sl.getBlockState(foot.above(i)), i == 0)) return;
        }
        for (int k = -2; k <= 2; k += 4) {
            BlockPos foot = mid.relative(side, k);
            for (int i = 0; i < top; i++) put(sl, s, foot.above(i), i == top - 1 ? look.cap[tier] : look.pillar[tier]);
        }
        for (int k = -2; k <= 2; k++) put(sl, s, mid.relative(side, k).above(top), k == 0 ? look.cap[tier] : slab(s.style, false));
        put(sl, s, mid.above(top - 1), hanging(look.light[4]));
    }

    /** Capital: coroa ligando os quatro pilares do Monumento do Coração (o meio fica aberto para o cristal). */
    private static void crown(ServerLevel sl, Settlement s, TierLook.Look look, BlockPos h) {
        int y = TierLook.pillarHeight(5) - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (!ring || corner) continue;
                put(sl, s, h.offset(dx, y, dz), slab(s.style, true));
                if (dx == 0 || dz == 0) put(sl, s, h.offset(dx, y + 1, dz), look.cap[5]);
            }
        }
    }

    /**
     * Capital: mosaico no piso da praça — losangos de pedra de destaque. Só troca piso que ainda é rua ou o próprio
     * bloco do molde da praça (o que o jogador pôs no chão fica); os blocos do mosaico são piso de rua, então a
     * próxima reforma (ou a troca de estilo) os refaz.
     */
    private static void mosaic(ServerLevel sl, Settlement s, TierLook.Look look, int base, @Nullable Templates.Tpl tpl) {
        Map<Long, Block> floor = new HashMap<>();
        if (tpl != null) {
            Rotation rot = s.plazaRotation();
            int ox = s.heart.getX() - tpl.width(rot) / 2, oz = s.heart.getZ() - tpl.depth(rot) / 2;
            for (Templates.Entry e : tpl.entries()) {
                if (e.pos().getY() != 0) continue;
                BlockPos r = tpl.rotate(e.pos().getX(), 0, e.pos().getZ(), rot);
                floor.put(BlockPos.asLong(ox + r.getX(), base, oz + r.getZ()), e.state().getBlock());
            }
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState accent = look.plazaEdge[5], line = look.street[5][0];
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int r = Math.abs(dx) + Math.abs(dz);
                BlockState want = r == 5 || r == 9 ? accent : Math.abs(dx) == Math.abs(dz) && r >= 6 ? line : null;
                if (want == null) continue;
                m.set(s.heart.getX() + dx, base, s.heart.getZ() + dz);
                if (!sl.hasChunk(m.getX() >> 4, m.getZ() >> 4)) continue;
                BlockState cur = sl.getBlockState(m);
                if (cur.is(want.getBlock())) continue;
                if (!TierLook.isStreet(cur) && !cur.is(floor.getOrDefault(m.asLong(), Blocks.AIR))) continue;
                if (!cur.isCollisionShapeFullBlock(sl, m)) continue;
                sl.setBlock(m, want, 2 | 16);
            }
        }
    }

    private CityScenery() {}
}
