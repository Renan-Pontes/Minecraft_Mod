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
    /** O festival dura 2 dias do jogo (hoje e amanhã); um novo só {@link #FESTIVAL_GAP} dias depois do começo do último. */
    public static final int FESTIVAL_DAYS = 2, FESTIVAL_GAP = 5;
    /** Comércio em festa: impostos +30% (e as caravanas lucram mais, veja {@link CityCaravans}). */
    public static final float FESTIVAL_INCOME = 1.3F;

    /** Conta do dia (chamada uma vez por dia do Overworld). */
    public static void daily(MinecraftServer server, Settlement s) {
        // v1.4: necessidades primeiro (comida, combustível, segurança, população)
        CityEconomy.Day day = CityEconomy.daily(server, s);
        CityEconomy.Stats st = CityEconomy.stats(server, s);
        // a conta roda na virada do dia e fecha o dia que PASSOU: vale o festival de ontem (2 dias = 2 contas com festa)
        long ended = SettlementService.day(server) - 1;
        boolean feast = s.festivalDay >= 0 && ended >= s.festivalDay && ended < s.festivalDay + FESTIVAL_DAYS;
        int base;
        if (s.own()) {
            base = s.people / 2 + 3 * s.population(Settlement.Role.MERCHANT) + 2 * s.level;
        } else {
            ServerLevel sl = SettlementService.level(server, s);
            var place = sl == null ? null : com.renanpontes.isekaimahou.core.api.Places.get(sl, s.id);
            base = place != null && "capital".equals(place.type()) ? 25 : 12;
        }
        int income = base + s.tradesToday * PER_TRADE[s.tax];
        income = Math.round(income * TAX_MULT[s.tax] * (100 + s.taxPct() + st.incomePct()) / 100F * st.prodMult()
                * (feast ? FESTIVAL_INCOME : 1F));
        s.treasury = Math.min(Settlement.MAX_TREASURY, s.treasury + income);
        // v1.0: a felicidade do dia é a soma dos motivos (os mesmos que a tela mostra)
        int delta = CityHappiness.total(CityHappiness.factors(server, s, st, day.starving(), day.cold(), day.foodDays(), feast));
        s.affinity = Math.max(0, Math.min(Settlement.MAX_AFFINITY, s.affinity + delta));
        CityRaids.daily(server, s);
        CityStash.dailyProduction(server, s, st);
        long today = SettlementService.day(server);
        if (s.festivalDay >= 0 && today == s.festivalDay + FESTIVAL_DAYS) {
            for (ServerPlayer p : CityEconomy.governors(server, s)) {
                p.displayClientMessage(SettlementsLang.tr("festival.over", SettlementService.cityName(server, s)).withColor(s.style.color), false);
            }
        }
        s.lastIncome = income;
        s.lastAffinityDelta = delta;
        s.tradesToday = 0;
        s.killsToday = 0;
        s.lordVisited = false;
        for (ServerPlayer p : CityEconomy.governors(server, s)) {
            p.displayClientMessage(Component.translatable("settlements.isekai_mahou.daily", SettlementService.cityName(server, s),
                    SettlementService.coins(income), (delta >= 0 ? "+" : "") + delta).withColor(s.style.color), false);
        }
        CityEconomy.checkRevolt(server, s);
    }

    /**
     * Cidade conquistada: o Senhor ainda é o líder no {@code Allegiance}? (um comando pode ter trocado) Devolve false se
     * a cidade deixou de ter Senhor (o registro some).
     */
    public static boolean validateConquered(MinecraftServer server, Settlement s) {
        UUID leader = Allegiance.leader(server, s.id);
        if (leader == null) {
            // v1.4: sem Senhor a cidade NÃO perde nada (estoques, população, nível...): só fica sem governo
            if (s.owner != null) {
                s.owner = null;
                s.ownerName = "";
            }
            return true;
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
        // v1.0 (coleta fácil): de qualquer lugar DENTRO da cidade, não só colado no coração
        if (!CityActions.inCity(sp, s)) {
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

    /** Comida gasta no banquete do festival (cresce com o nível do centro). */
    public static int festivalFood(Settlement s) {
        return 15 * s.level;
    }

    /** A cidade está em festa hoje? (2 dias: o do começo e o seguinte) */
    public static boolean festivalActive(MinecraftServer server, Settlement s) {
        return festivalDayNumber(server, s) > 0;
    }

    /** 1 ou 2 = dia do festival em andamento; 0 = sem festa. */
    public static int festivalDayNumber(MinecraftServer server, Settlement s) {
        if (s.festivalDay < 0) return 0;
        long d = SettlementService.day(server) - s.festivalDay;
        return d >= 0 && d < FESTIVAL_DAYS ? (int) d + 1 : 0;
    }

    /** Dias que faltam para poder fazer outro festival (0 = já pode). */
    public static int festivalWait(MinecraftServer server, Settlement s) {
        if (s.festivalDay < 0) return 0;
        long d = SettlementService.day(server) - s.festivalDay;
        return d < 0 ? 0 : (int) Math.max(0, FESTIVAL_GAP - d);
    }

    public static void festival(ServerPlayer sp, Settlement s) {
        long today = SettlementService.day(sp.server);
        int wait = festivalWait(sp.server, s);
        if (wait > 0) {
            sp.displayClientMessage(SettlementsLang.tr(festivalActive(sp.server, s) ? "festival.running" : "festival.wait", wait)
                    .withColor(0xE8735E), true);
            return;
        }
        if (s.treasury < SettlementService.FESTIVAL_COST) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.treasury.poor", SettlementService.FESTIVAL_COST).withColor(0xE8735E), true);
            return;
        }
        // v1.4: o banquete do festival sai do celeiro
        int feast = festivalFood(s);
        if (s.food < feast) {
            sp.displayClientMessage(SettlementsLang.tr("festival.no_food", feast).withColor(0xE8735E), true);
            return;
        }
        s.food -= feast;
        s.treasury -= SettlementService.FESTIVAL_COST;
        s.festivalDay = today;
        s.festivals++;
        // v1.0: festa de 2 dias — alegria na abertura e mais um pouco a cada dia de festa (CityHappiness "festival")
        s.affinity = Math.min(Settlement.MAX_AFFINITY, s.affinity + SettlementService.FESTIVAL_AFFINITY);
        // a cidade fica enfeitada hoje e amanhã (lanternas, bandeirolas, flores, fogos à noite)
        CityDecor.setUntil(s, today + FESTIVAL_DAYS - 1);
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
        AABB box = new AABB(s.heart).inflate(CityLayout.half(s), 24, CityLayout.half(s));
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
