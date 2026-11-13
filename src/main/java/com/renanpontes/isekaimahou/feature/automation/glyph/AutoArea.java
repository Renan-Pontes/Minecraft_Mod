package com.renanpontes.isekaimahou.feature.automation.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import com.renanpontes.isekaimahou.feature.automation.Pedestals;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * Utilidades dos glifos de Automação: varredura de área, permissões (proteção de spawn, mods de proteção,
 * mobGriefing), de onde tirar sementes/comida (inventário do jogador ou baús colados no pedestal) e a guarda
 * "uma vez por conjuração e tick" (formas de área aplicam o efeito uma vez por criatura atingida).
 */
public final class AutoArea {
    /** Teto de raio para não varrer áreas absurdas com muitos Expandir. */
    public static final int MAX_RADIUS = 12;

    private static final Map<SpellContext, Map<String, Long>> GUARD = new WeakHashMap<>();
    private static final Map<SpellContext, Worked> WORKED = new WeakHashMap<>();

    /** O que um contexto já trabalhou NESTE tick: centros de área por glifo e blocos já "tocados". */
    private static final class Worked {
        long tick = Long.MIN_VALUE;
        final Map<String, List<BlockPos>> centers = new HashMap<>();
        final Set<BlockPos> touched = new HashSet<>();

        Worked at(long now) {
            if (tick != now) {
                tick = now;
                centers.clear();
                touched.clear();
            }
            return this;
        }
    }

    public static int radius(SpellStats stats, float base) {
        return Math.max(1, Math.min(MAX_RADIUS, Math.round(stats.radius(base))));
    }

    /** Posições num disco horizontal de raio {@code r}, de {@code down} blocos abaixo a {@code up} acima do centro. */
    public static List<BlockPos> disk(BlockPos center, int r, int down, int up) {
        List<BlockPos> out = new ArrayList<>();
        int r2 = r * r + r;
        for (int y = -down; y <= up; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r2) out.add(center.offset(x, y, z));
                }
            }
        }
        return out;
    }

    /** Pode mexer neste bloco? Jogadores (inclusive o do pedestal): proteção de spawn/borda; mobs: mobGriefing. */
    public static boolean mayEdit(SpellContext ctx, BlockPos pos) {
        ServerLevel level = ctx.level();
        // v1.0 r9: torre em escalada / masmorra são indestrutíveis (nem blocos temporários de magia)
        if (com.renanpontes.isekaimahou.core.api.Destruction.wardedFor(level, pos, ctx.caster())) return false;
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null) return level.mayInteract(sp, pos) && sp.mayBuild();
        LivingEntity c = ctx.caster();
        return c == null || EventHooks.canEntityGrief(level, c);
    }

    /** Pode quebrar (colher) este bloco? Para jogadores dispara o evento de quebra (mods de proteção podem negar). */
    public static boolean mayBreak(SpellContext ctx, BlockPos pos, BlockState state) {
        if (!mayEdit(ctx, pos)) return false;
        ServerPlayer sp = ctx.casterPlayer();
        return sp == null || !CommonHooks.fireBlockBreak(ctx.level(), sp.gameMode.getGameModeForPlayer(), sp, pos, state).isCanceled();
    }

    /** true só na primeira chamada desta conjuração (contexto) com esta chave neste tick. */
    public static boolean firstThisTick(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        Long last = GUARD.computeIfAbsent(ctx, k -> new HashMap<>()).put(key, now);
        return last == null || last != now;
    }

    /**
     * Reserva a área de {@code key} em volta de {@code center} para esta conjuração neste tick. Falso se já houve
     * trabalho a até {@code minDist} blocos daqui (ex.: Si Mesmo aplica na criatura E no bloco embaixo dela; uma
     * Nova acerta várias criaturas coladas) — evita varrer e agir duas vezes na mesma área.
     */
    public static boolean claimArea(SpellContext ctx, String key, BlockPos center, int minDist) {
        Worked w = WORKED.computeIfAbsent(ctx, k -> new Worked()).at(ctx.level().getGameTime());
        List<BlockPos> list = w.centers.computeIfAbsent(key, k -> new ArrayList<>());
        for (BlockPos p : list) {
            if (Math.abs(p.getX() - center.getX()) <= minDist && Math.abs(p.getY() - center.getY()) <= minDist
                    && Math.abs(p.getZ() - center.getZ()) <= minDist) return false;
        }
        list.add(center.immutable());
        return true;
    }

    /** true só na primeira vez que esta conjuração mexe neste bloco neste tick (áreas que se sobrepõem). */
    public static boolean firstTouch(SpellContext ctx, BlockPos pos) {
        return WORKED.computeIfAbsent(ctx, k -> new Worked()).at(ctx.level().getGameTime()).touched.add(pos.immutable());
    }

    public static boolean creative(SpellContext ctx) {
        return ctx.caster() instanceof Player p && p.getAbilities().instabuild;
    }

    // =====================================================================================
    //  Fontes de itens (sementes, comida)
    // =====================================================================================

    /**
     * De onde o efeito tira itens: num pedestal, os inventários colados nele (baús, barris, coletor...);
     * num jogador, o inventário principal dele; em mobs, nada.
     */
    public static List<IItemHandler> sources(SpellContext ctx, Vec3 near) {
        List<IItemHandler> out = new ArrayList<>();
        RunicPedestalBlockEntity pedestal = Pedestals.of(ctx, near);
        if (pedestal != null) {
            BlockPos pp = pedestal.getBlockPos();
            for (Direction d : Direction.values()) {
                IItemHandler h = ctx.level().getCapability(Capabilities.ItemHandler.BLOCK, pp.relative(d), d.getOpposite());
                if (h != null) out.add(h);
            }
            return out;
        }
        if (ctx.caster() instanceof Player p) out.add(new PlayerMainInvWrapper(p.getInventory()));
        return out;
    }

    /**
     * Tira 1 item que satisfaz {@code test} das fontes (sem gastar no criativo). Devolve a cópia (1 unidade)
     * ou vazio se não achou.
     */
    public static ItemStack take(List<IItemHandler> sources, Predicate<ItemStack> test, boolean creative) {
        for (IItemHandler h : sources) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty() || !test.test(s)) continue;
                ItemStack got = h.extractItem(i, 1, creative);
                if (!got.isEmpty()) return got;
            }
        }
        return ItemStack.EMPTY;
    }

    private AutoArea() {}
}
