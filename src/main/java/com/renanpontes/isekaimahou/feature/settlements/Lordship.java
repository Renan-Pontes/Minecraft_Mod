package com.renanpontes.isekaimahou.feature.settlements;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Governar: impostos (moedas por dia das trocas e da população, guardadas no cofre do coração), afinidade do povo
 * (sobe com impostos baixos, prédios de lazer, visitas do Senhor e defesa; cai com guerra e impostos altos), festival,
 * portões por decreto e a aura do Obelisco do Sol.
 */
public final class Lordship {
    /** Moedas por troca conforme o decreto de impostos (baixo/normal/alto). */
    private static final int[] PER_TRADE = {1, 2, 3};
    private static final float[] TAX_MULT = {0.75F, 1.0F, 1.4F};
    private static final int[] TAX_AFFINITY = {4, 0, -5};

    /** Conta do dia (chamada uma vez por dia do Overworld). */
    public static void daily(MinecraftServer server, Settlement s) {
        int base;
        if (s.own()) {
            base = s.population(Settlement.Role.RESIDENT) + 3 * s.population(Settlement.Role.MERCHANT) + 2 * s.level;
        } else {
            ServerLevel sl = SettlementService.level(server, s);
            var place = sl == null ? null : com.renanpontes.isekaimahou.core.api.Places.get(sl, s.id);
            base = place != null && "capital".equals(place.type()) ? 25 : 12;
        }
        int income = base + s.tradesToday * PER_TRADE[s.tax];
        income = Math.round(income * TAX_MULT[s.tax] * (100 + s.taxPct()) / 100F);
        s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + income);
        int wars = Allegiance.enemiesOf(server, s.id).size();
        int delta = 3 + s.buildingAffinity() + TAX_AFFINITY[s.tax] + (s.lordVisited ? 2 : 0) + Math.min(3, s.killsToday / 10) - 3 * wars;
        s.affinity = Math.max(0, Math.min(Settlement.MAX_AFFINITY, s.affinity + delta));
        s.lastIncome = income;
        s.lastAffinityDelta = delta;
        s.tradesToday = 0;
        s.killsToday = 0;
        s.lordVisited = false;
        if (s.owner != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.owner);
            if (p != null) {
                p.displayClientMessage(Component.translatable("settlements.isekai_mahou.daily", SettlementService.cityName(server, s),
                        SettlementService.coins(income), (delta >= 0 ? "+" : "") + delta).withColor(s.style.color), false);
            }
        }
    }

    /**
     * Cidade conquistada: o Senhor ainda é o líder no {@code Allegiance}? (um comando pode ter trocado) Devolve false se
     * a cidade deixou de ter Senhor (o registro some).
     */
    public static boolean validateConquered(MinecraftServer server, Settlement s) {
        UUID leader = Allegiance.leader(server, s.id);
        if (leader == null) {
            SettlementData.get(server).remove(s.id);
            return false;
        }
        if (!leader.equals(s.owner)) {
            s.owner = leader;
            ServerPlayer p = server.getPlayerList().getPlayer(leader);
            if (p != null) s.ownerName = p.getGameProfile().getName();
        }
        return true;
    }

    // =====================================================================================
    //  Cofre e festival
    // =====================================================================================
    public static void withdraw(ServerPlayer sp, Settlement s, int amount) {
        if (sp.level().dimension() != s.dim || sp.blockPosition().distSqr(s.heart) > 8 * 8) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.treasury.at_heart").withColor(0xE8735E), true);
            return;
        }
        int n = Math.min(s.treasury, Math.max(1, Math.min(640, amount)));
        if (n <= 0) return;
        s.treasury -= n;
        Item coin = SettlementService.coinItem();
        int left = n;
        while (left > 0) {
            int c = Math.min(left, coin.getDefaultMaxStackSize());
            Rewards.give(sp, new ItemStack(coin, c));
            left -= c;
        }
        sp.playNotifySound(SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
        sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.treasury.took", SettlementService.coins(n)).withColor(0xFFD86B), true);
    }

    public static void festival(ServerPlayer sp, Settlement s) {
        long today = SettlementService.day(sp.server);
        if (s.festivalDay == today) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.festival.today").withColor(0xE8735E), true);
            return;
        }
        if (s.treasury < SettlementService.FESTIVAL_COST) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.treasury.poor", SettlementService.FESTIVAL_COST).withColor(0xE8735E), true);
            return;
        }
        s.treasury -= SettlementService.FESTIVAL_COST;
        s.festivalDay = today;
        s.affinity = Math.min(Settlement.MAX_AFFINITY, s.affinity + SettlementService.FESTIVAL_AFFINITY);
        ServerLevel sl = SettlementService.level(sp.server, s);
        if (sl != null && sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) {
            sl.sendParticles(ParticleTypes.FIREWORK, s.heart.getX() + 0.5, s.heart.getY() + 8, s.heart.getZ() + 0.5, 120, 6, 3, 6, 0.2);
            sl.sendParticles(ParticleTypes.NOTE, s.heart.getX() + 0.5, s.heart.getY() + 2, s.heart.getZ() + 0.5, 30, 4, 1, 4, 1);
            sl.playSound(null, s.heart, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.AMBIENT, 3.0F, 1.0F);
            sl.playSound(null, s.heart, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.AMBIENT, 2.0F, 1.2F);
        }
        Rewards.banner(sp, Component.translatable("settlements.isekai_mahou.festival.banner", SettlementService.cityName(sp.server, s)), s.style.color);
    }

    // =====================================================================================
    //  Portões e maravilhas (cidade própria, com o coração carregado)
    // =====================================================================================
    public static void gates(ServerLevel sl, Settlement s) {
        if (!s.wallDone || s.gateBlocks.isEmpty()) return;
        boolean shut = s.gates == Settlement.GATES_CLOSED || s.gates == Settlement.GATES_NIGHT && sl.isNight();
        if (shut == s.gatesShut) return;
        BlockState bars = s.style.portcullis();
        boolean all = true;
        for (BlockPos p : s.gateBlocks) {
            if (!sl.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
                all = false;
                continue;
            }
            BlockState cur = sl.getBlockState(p);
            if (shut) {
                // ninguém fica preso na grade: espera a passagem esvaziar
                if (cur.isAir() && sl.getEntitiesOfClass(LivingEntity.class, new AABB(p)).isEmpty()) {
                    sl.setBlock(p, bars, 3);
                } else if (cur.isAir()) {
                    all = false;
                }
            } else if (cur.is(bars.getBlock())) {
                sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (!all) return;
        s.gatesShut = shut;
        for (int i = 0; i < s.gateBlocks.size(); i += Math.max(1, s.style.gateHeight * 3)) {
            BlockPos p = s.gateBlocks.get(i);
            sl.playSound(null, p, shut ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5F, 0.6F);
            sl.playSound(null, p, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2F, 0.8F);
        }
    }

    /** Obelisco do Sol: regeneração para quem a cidade protege (e para os NPCs dela). */
    public static void obeliskAura(ServerLevel sl, Settlement s) {
        AABB box = new AABB(s.heart).inflate(CityLayout.TERRITORY, 24, CityLayout.TERRITORY);
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (SettlementService.isProtected(sl.getServer(), s, p)) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, true, true));
        }
        for (com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc n
                : sl.getEntitiesOfClass(com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc.class, box)) {
            if (n.cityId().equals(s.id)) n.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, true, false));
        }
    }

    private Lordship() {}
}
