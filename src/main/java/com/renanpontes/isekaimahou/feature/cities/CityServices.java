package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Reputation.Standing;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.cities.entity.CityGuard;
import com.renanpontes.isekaimahou.feature.cities.entity.CityLord;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;
import com.renanpontes.isekaimahou.feature.cities.trade.CityTrades;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Serviços dos NPCs (botão "Serviços" do diálogo): sacerdote (cura, purificação, Bênção da Aurora), taverneiro
 * (quarto), alquimista (restaurar mana), ferreiro (consertar), receptador (limpar a ficha), guarda (fiança DO REINO
 * dele) e o Lorde (audiência com recompensa por patamar no reino dele). Pagos em Moedas da Guilda; dão um pouco de
 * reputação no reino da cidade.
 * <p>v0.9: guias de graça — sacerdote, escriba e guardas mostram o caminho do <b>Altar de Ascensão</b> público da
 * cidade e os guardas o da <b>Pedra de Teleporte</b> da praça (seta no rastreador, ver {@link CityRumors#guide}).
 */
public final class CityServices {
    public static final int HURT_BAIL = 10;
    public static final int KILL_BAIL = 30;

    /** Serviços que o NPC oferece a este jogador agora. */
    public static List<CitiesNetwork.Service> list(ServerPlayer p, CityNpc npc) {
        List<CitiesNetwork.Service> out = new ArrayList<>();
        boolean wanted = p.hasEffect(CitiesContent.WANTED);
        if (npc instanceof CityGuard) {
            if (CityCrimes.isWanted(p, npc.kingdomFaction())) {
                out.add(service("bail", CityCrimes.bail(p, npc.kingdomFaction()), true));
            } else {
                guides(p, npc, out, true);
            }
            return out;
        }
        if (npc instanceof CityLord) {
            out.add(service("audience", 0, true));
            return out;
        }
        if (!(npc instanceof CityMerchant m) || !Reputation.canTrade(p, m.faction())) return out;
        switch (m.profession()) {
            case PRIEST -> {
                out.add(service("heal", price(p, m, 4), p.getHealth() < p.getMaxHealth() || p.isOnFire()));
                out.add(service("cleanse", price(p, m, 6), true));
                out.add(service("blessing", price(p, m, 10), true));
                guides(p, npc, out, false);
            }
            case SCRIBE -> guides(p, npc, out, false);
            case INNKEEPER -> out.add(service("room", price(p, m, 6), true));
            case ALCHEMIST -> {
                MagicData d = MagicApi.get(p);
                out.add(service("mana", price(p, m, 5), d.mana() < d.maxMana()));
            }
            case BLACKSMITH -> out.add(service("repair", repairCost(p, m), p.getMainHandItem().isDamaged()));
            case FENCE -> out.add(service("clear_record", price(p, m, 25), wanted));
            default -> { }
        }
        return out;
    }

    private static CitiesNetwork.Service service(String id, int cost, boolean available) {
        return new CitiesNetwork.Service(id, cost, available, CityLines.tr("service.isekai_mahou.cities." + id),
                CityLines.tr("service.isekai_mahou.cities." + id + ".desc"));
    }

    private static int price(ServerPlayer p, CityMerchant m, int base) {
        return Math.max(1, Math.round(base * Reputation.priceMultiplier(p, m.faction())));
    }

    /** Guias de graça: Altar de Ascensão (se a cidade tiver um público) e, para os guardas, a Pedra de Teleporte. */
    private static void guides(ServerPlayer p, CityNpc npc, List<CitiesNetwork.Service> out, boolean stone) {
        CityHeartBlockEntity heart = CityCommands.heartAt(p.serverLevel(), npc.blockPosition());
        if (heart == null) return;
        if (!heart.altars().isEmpty()) out.add(service("altar_guide", 0, true));
        if (stone && heart.stone() != null) out.add(service("stone_guide", 0, true));
    }

    /** O ponto mais perto do NPC numa lista. */
    @Nullable
    private static BlockPos nearest(List<BlockPos> list, BlockPos from) {
        BlockPos best = null;
        for (BlockPos b : list) if (best == null || b.distSqr(from) < best.distSqr(from)) best = b;
        return best;
    }

    private static int repairCost(ServerPlayer p, CityMerchant m) {
        ItemStack s = p.getMainHandItem();
        if (!s.isDamageableItem() || s.getMaxDamage() <= 0) return price(p, m, 2);
        float frac = s.getDamageValue() / (float) s.getMaxDamage();
        return price(p, m, 1 + Math.round(frac * 11));
    }

    /** Executa o serviço e devolve a fala do NPC. */
    public static Component perform(ServerPlayer p, CityNpc npc, String id) {
        CitiesNetwork.Service s = null;
        for (CitiesNetwork.Service x : list(p, npc)) if (x.id().equals(id)) s = x;
        if (s == null || !s.available()) return CityLines.line("service.unavailable");
        if (id.equals("audience")) return audience(p, (CityLord) npc);
        if (id.equals("altar_guide") || id.equals("stone_guide")) return guide(p, npc, id.equals("altar_guide"));
        BlockPos bed = id.equals("room") ? findBed(p.serverLevel(), npc.blockPosition(), 20) : null;
        if (id.equals("room") && bed == null) return CityLines.line("service.room.none");
        if (!CityPlayer.pay(p, s.cost())) {
            p.level().playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
            return CityLines.line("service.no_coins", s.cost());
        }
        ServerLevel sl = p.serverLevel();
        Component reason = Component.translatable("reason.isekai_mahou.cities.service");
        switch (id) {
            case "bail" -> {
                CityCrimes.clearWanted(p, npc.kingdomFaction());
                forgiveGuards(p, npc.kingdomFaction());
                sl.playSound(null, p.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                return CityLines.line("service.bail.done");
            }
            case "heal" -> {
                p.setHealth(p.getMaxHealth());
                p.clearFire();
                sparkle(sl, p, ParticleTypes.HEART, 8);
                sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.6F);
                Reputation.addDaily(p, "church", 2, "cities.service", 20, reason);
                return CityLines.line("service.heal.done");
            }
            case "cleanse" -> {
                for (Statuses.Kind k : Statuses.Kind.values()) Statuses.clear(p, k);
                List<Holder<MobEffect>> bad = new ArrayList<>();
                for (MobEffectInstance inst : p.getActiveEffects()) {
                    if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !inst.getEffect().is(CitiesContent.WANTED.getKey())) {
                        bad.add(inst.getEffect());
                    }
                }
                for (Holder<MobEffect> h : bad) p.removeEffect(h);
                p.clearFire();
                sparkle(sl, p, ParticleTypes.END_ROD, 24);
                sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.3F);
                Reputation.addDaily(p, "church", 2, "cities.service", 20, reason);
                return CityLines.line("service.cleanse.done");
            }
            case "blessing" -> {
                p.addEffect(new MobEffectInstance(CitiesContent.AURORA_BLESSING, 12000, 0, false, true, true));
                sparkle(sl, p, ParticleTypes.END_ROD, 40);
                sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);
                Reputation.addDaily(p, "church", 3, "cities.service", 20, reason);
                return CityLines.line("service.blessing.done");
            }
            case "room" -> {
                return rentRoom(p, bed, npc.kingdomFaction());
            }
            case "mana" -> {
                MagicData d = MagicApi.get(p);
                d.fillMana();
                d.markDirty();
                sparkle(sl, p, ParticleTypes.WITCH, 20);
                sl.playSound(null, p.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.2F);
                Reputation.addDaily(p, npc.kingdomFaction(), 1, "cities.service", 20, reason);
                return CityLines.line("service.mana.done");
            }
            case "repair" -> {
                ItemStack s2 = p.getMainHandItem();
                s2.setDamageValue(0);
                sl.playSound(null, npc.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.8F, 1.0F);
                sl.sendParticles(ParticleTypes.LAVA, npc.getX(), npc.getY() + 1.2, npc.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
                npc.playAction("attack");
                Reputation.addDaily(p, npc.kingdomFaction(), 1, "cities.service", 20, reason);
                return CityLines.line("service.repair.done", s2.getHoverName());
            }
            case "clear_record" -> {
                CityCrimes.clearAllWanted(p);
                CityConfront.clearStrikes(p);
                forgiveGuards(p, null);
                sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
                Reputation.addDaily(p, "underworld", 3, "cities.service", 20, reason);
                return CityLines.line("service.clear_record.done");
            }
            default -> {
                return CityLines.line("service.unavailable");
            }
        }
    }

    /** Guardas por perto (do reino que perdoou; null = todos) param de perseguir quem acabou de quitar a dívida. */
    private static void forgiveGuards(ServerPlayer p, @Nullable String kingdomFaction) {
        for (CityGuard g : p.serverLevel().getEntitiesOfClass(CityGuard.class, p.getBoundingBox().inflate(48))) {
            if (kingdomFaction != null && !g.kingdomFaction().equals(kingdomFaction)) continue;
            if (g.getTarget() == p) g.setTarget(null);
            if (g.getLastHurtByMob() == p) g.setLastHurtByMob(null);
        }
    }

    /** Mostra o caminho do altar/pedra: seta no rastreador e marca no chão. */
    private static Component guide(ServerPlayer p, CityNpc npc, boolean altar) {
        CityHeartBlockEntity heart = CityCommands.heartAt(p.serverLevel(), npc.blockPosition());
        BlockPos target = heart == null ? null : altar ? nearest(heart.altars(), npc.blockPosition()) : heart.stone();
        if (target == null) return CityLines.line("service.unavailable");
        String key = altar ? "ascension_altar" : "travel_stone";
        CityRumors.guide(p, target, key);
        ServerLevel sl = p.serverLevel();
        Telegraph.show(sl, Telegraph.Shape.circle(Vec3.atBottomCenterOf(target.above()), 2.2F).warmup(200)
                .color(altar ? 0xFFB98CFF : 0xFF6FB8FF).noFlash());
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
        npc.playAction("wave");
        int dist = (int) Math.sqrt(npc.blockPosition().distSqr(target));
        return CityLines.line("service." + (altar ? "altar_guide" : "stone_guide") + ".done",
                CityRumors.direction(npc.blockPosition(), target), dist);
    }

    private static void sparkle(ServerLevel sl, LivingEntity e, net.minecraft.core.particles.SimpleParticleType type, int n) {
        sl.sendParticles(type, e.getX(), e.getY() + 1.0, e.getZ(), n, 0.4, 0.6, 0.4, 0.02);
    }

    // =====================================================================================
    //  Quarto da taverna
    // =====================================================================================
    /** Aluga a cama livre mais perto do taverneiro: vira o ponto de nascimento e dá "Descansado" ao acordar nela. */
    private static Component rentRoom(ServerPlayer p, BlockPos bed, String kingdomFaction) {
        ServerLevel sl = p.serverLevel();
        p.setRespawnPosition(sl.dimension(), bed, 0.0F, false, false);
        CompoundTag t = CityPlayer.tag(p);
        t.putLong("RoomBed", bed.asLong());
        t.putLong("RoomDay", sl.getDayTime() / 24000L);
        t.putString("RoomDim", sl.dimension().location().toString());
        Telegraph.show(sl, Telegraph.Shape.circle(Vec3.atBottomCenterOf(bed), 1.4F).warmup(100).color(Telegraph.SAFE).noFlash());
        sl.sendParticles(ParticleTypes.HEART, bed.getX() + 0.5, bed.getY() + 1.0, bed.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.0);
        sl.playSound(null, p.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
        Reputation.addDaily(p, kingdomFaction, 1, "cities.service", 20, Component.translatable("reason.isekai_mahou.cities.service"));
        return CityLines.line("service.room.done", bed.getX(), bed.getY(), bed.getZ());
    }

    @Nullable
    private static BlockPos findBed(ServerLevel sl, BlockPos center, int r) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -6, -r), center.offset(r, 8, r))) {
            BlockState st = sl.getBlockState(pos);
            if (!(st.getBlock() instanceof BedBlock) || st.getValue(BedBlock.PART) != BedPart.HEAD) continue;
            if (st.getValue(BedBlock.OCCUPIED)) continue;
            double d = pos.distSqr(center);
            if (d < bestD) {
                bestD = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    /**
     * Quarto alugado "pula a noite": quem dorme na cama alugada faz a noite passar mesmo num servidor em que os outros
     * jogadores estão acordados (no mundo solo o jogo já faz isso sozinho). Vale uma vez por aluguel (o acordar tira a
     * marca do quarto). Chamado a cada 2 s.
     */
    public static void tickRooms(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSleepingLongEnough()) continue;
            CompoundTag t = CityPlayer.tag(p);
            if (!t.contains("RoomBed")) continue;
            ServerLevel sl = p.serverLevel();
            if (!sl.dimension().location().toString().equals(t.getString("RoomDim"))) continue;
            BlockPos bed = BlockPos.of(t.getLong("RoomBed"));
            if (p.getSleepingPos().map(sp -> !sp.closerThan(bed, 2.5)).orElse(true)) continue;
            if (sl.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)) {
                long next = sl.getDayTime() + 24000L;
                sl.setDayTime(net.neoforged.neoforge.event.EventHooks.onSleepFinished(sl, next - next % 24000L, sl.getDayTime()));
            }
            Component msg = Component.translatable("message.isekai_mahou.cities.room_night", p.getDisplayName());
            for (ServerPlayer o : new ArrayList<>(sl.players())) {
                if (o.isSleeping()) o.stopSleepInBed(false, true);
                else o.displayClientMessage(msg, true);
            }
        }
    }

    /** Ao acordar na cama alugada (no dia do aluguel ou no seguinte): Descansado. */
    public static void onWake(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        if (!t.contains("RoomBed")) return;
        BlockPos bed = BlockPos.of(t.getLong("RoomBed"));
        long day = p.level().getDayTime() / 24000L;
        if (day - t.getLong("RoomDay") > 1 || !p.blockPosition().closerThan(bed, 3.0)) return;
        t.remove("RoomBed");
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0));
        p.addEffect(new MobEffectInstance(MobEffects.LUCK, 6000, 0));
        MagicData d = MagicApi.get(p);
        d.fillMana();
        d.markDirty();
        p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.rested"), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    // =====================================================================================
    //  Audiência com o Lorde
    // =====================================================================================
    /**
     * Audiência com o Lorde do reino: recompensa ao chegar a Honrado e a Exaltado NO REINO DELE (uma vez por reino).
     * Aldória mantém as marcas da v0.8 ({@code cities.lord.honored/exalted}); Sahra e Nordheim têm as suas.
     */
    private static Component audience(ServerPlayer p, CityLord lord) {
        CityKingdom k = lord.kingdom();
        String faction = k.factionId;
        String flag = k == CityKingdom.ALDORIA ? "cities.lord." : "cities.lord." + k.id + ".";
        Standing st = Reputation.standing(p, faction);
        MagicData d = MagicApi.get(p);
        ServerLevel sl = p.serverLevel();
        int rgb = CityRealm.color(k);
        if (st.atLeast(Standing.EXALTED) && !d.hasFlag(flag + "exalted")) {
            d.setFlag(flag + "exalted", true);
            if (!d.hasFlag(flag + "honored")) {
                d.setFlag(flag + "honored", true);
                honoredReward(p);
            }
            d.markDirty();
            ItemStack legend = Legends.randomScroll(p.getRandom(), Math.max(1, d.circle()));
            if (!legend.isEmpty()) Rewards.give(p, legend);
            Rewards.give(p, new ItemStack(CityTrades.coin(), 64));
            Rewards.give(p, new ItemStack(Items.NETHERITE_INGOT));
            String title = "reputation.isekai_mahou.title." + faction + ".exalted";
            Reputation.grantTitle(p, title);
            Rewards.banner(p, Component.translatable(CityRealm.key("message.isekai_mahou.cities.lord.hero", k)), 0xFFD86B);
            Rewards.toast(p, Component.translatable(CityRealm.key("toast.isekai_mahou.cities.lord.exalted", k)),
                    Component.translatable(title), legend.isEmpty() ? new ItemStack(Items.NETHERITE_INGOT) : legend, 0xFFD86B);
            celebrate(sl, lord);
            return CityLines.line(CityRealm.key("lord.reward.exalted", k), p.getDisplayName());
        }
        if (st.atLeast(Standing.HONORED) && !d.hasFlag(flag + "honored")) {
            d.setFlag(flag + "honored", true);
            d.markDirty();
            ItemStack icon = honoredReward(p);
            Rewards.toast(p, Component.translatable("toast.isekai_mahou.cities.lord.honored"),
                    Component.translatable(CityRealm.key("toast.isekai_mahou.cities.lord.honored.sub", k)), icon, rgb);
            celebrate(sl, lord);
            return CityLines.line("lord.reward.honored", p.getDisplayName());
        }
        if (st.atLeast(Standing.EXALTED)) return CityLines.line(CityRealm.key("lord.audience.done", k));
        if (st.atLeast(Standing.HONORED)) return CityLines.line("lord.audience.next_exalted");
        return CityLines.line("lord.audience.not_yet", Standing.HONORED.displayName());
    }

    private static ItemStack honoredReward(ServerPlayer p) {
        Rewards.give(p, new ItemStack(CityTrades.coin(), 24));
        Rewards.give(p, new ItemStack(Items.DIAMOND, 3));
        ItemStack icon = new ItemStack(Items.DIAMOND);
        for (ResourceLocation id : CityTrades.glyphsFor(p, 3, 1, p.getRandom())) {
            ItemStack scroll = GlyphScrollItem.of(id);
            Rewards.give(p, scroll);
            icon = scroll;
        }
        MagicApi.giveXp(p, 250, "cities.lord");
        return icon;
    }

    private static void celebrate(ServerLevel sl, Mob lord) {
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, lord.getX(), lord.getY() + 1.5, lord.getZ(), 60, 0.6, 0.8, 0.6, 0.3);
        sl.playSound(null, lord.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 0.8F, 1.0F);
        if (lord instanceof CityNpc n) n.playAction("cheer");
    }

    private CityServices() {}
}
