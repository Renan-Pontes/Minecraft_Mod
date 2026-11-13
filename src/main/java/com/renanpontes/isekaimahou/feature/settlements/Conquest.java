package com.renanpontes.isekaimahou.feature.settlements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.ReputationChangedEvent;
import com.renanpontes.isekaimahou.feature.settlements.entity.CityChampion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Tomar o controle de uma cidade de reino (docs/V10_DESIGN.md §3 "settlements"):
 * <ol>
 *   <li><b>Reino Exaltado</b> com a facção da cidade;</li>
 *   <li><b>Juramento</b> (agachar + clicar no coração da cidade): <i>Defender</i> (abater monstros na cidade),
 *       <i>Tributo</i> (moedas ao cofre) e <i>Vigília</i> (ficar ajoelhado junto ao coração à noite) → Selo do Juramento;</li>
 *   <li><b>Desafio</b>: o Selo no coração invoca o Campeão da Cidade; vencer = <b>Senhor da cidade</b>
 *       ({@code Allegiance.setLeader}), impostos, decretos e a Guarda Pessoal.</li>
 * </ol>
 * Cair abaixo de Reverenciado com o reino tira o título.
 */
public final class Conquest {
    public static final int KILLS_NEEDED = 20;
    public static final int TRIBUTE_NEEDED = 48;
    public static final int VIGIL_NEEDED = 60;
    public static final int RETRY_TICKS = 2400;
    public static final String TITLE = "settlements.isekai_mahou.title.lord";
    /** Desafio em andamento por cidade (UUID do Campeão). */
    private static final Map<String, UUID> ACTIVE = new HashMap<>();

    public static boolean conquerable(@Nullable Places.Place p) {
        return SettlementService.isCity(p) && !p.id().startsWith("settlement_") && !p.faction().isEmpty()
                && Reputation.faction(p.faction()) != null;
    }

    public static boolean exalted(ServerPlayer sp, Places.Place p) {
        return Reputation.standing(sp, p.faction()).atLeast(Reputation.Standing.EXALTED);
    }

    // =====================================================================================
    //  Juramento
    // =====================================================================================
    public static void startOath(ServerPlayer sp, String cityId) {
        Places.Place p = Places.get(sp.serverLevel(), cityId);
        if (!conquerable(p)) return;
        if (!exalted(sp, p)) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.oath.need_exalted",
                    Reputation.faction(p.faction()).coloredName()).withColor(0xE8735E), true);
            return;
        }
        if (Allegiance.isLeader(sp, cityId) || Allegiance.isEnemyOf(sp, cityId)) return;
        SettlementData.get(sp.server).startOath(sp.getUUID(), cityId);
        sp.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
        Rewards.toast(sp, Component.translatable("settlements.isekai_mahou.oath.started"), p.name(), new ItemStack(Items.WRITABLE_BOOK),
                0xFFD86B);
    }

    public static void payTribute(ServerPlayer sp) {
        SettlementData.Oath o = SettlementData.get(sp.server).oath(sp.getUUID());
        if (o == null || o.tribute >= TRIBUTE_NEEDED) return;
        int need = TRIBUTE_NEEDED - o.tribute;
        int paid = 0;
        var coin = SettlementService.coinItem();
        for (int i = 0; i < sp.getInventory().getContainerSize() && paid < need; i++) {
            ItemStack st = sp.getInventory().getItem(i);
            if (st.is(coin) || st.is(Items.EMERALD)) {
                int take = Math.min(st.getCount(), need - paid);
                st.shrink(take);
                paid += take;
            }
        }
        if (paid == 0) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.oath.no_coins").withColor(0xE8735E), true);
            return;
        }
        o.tribute += paid;
        sp.playNotifySound(SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
        sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.oath.tribute", o.tribute, TRIBUTE_NEEDED).withColor(0xFFD86B), true);
        checkComplete(sp, o);
    }

    public static void onKill(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp) || !(e.getEntity() instanceof Enemy)) return;
        SettlementData.Oath o = SettlementData.get(sp.server).oath(sp.getUUID());
        if (o == null || o.kills >= KILLS_NEEDED) return;
        Places.Place p = Places.get(sp.serverLevel(), o.city);
        if (p == null || p.dim() != sp.level().dimension()) return;
        BlockPos at = e.getEntity().blockPosition();
        if (!p.contains(at) && at.distSqr(p.center()) > 96 * 96) return;
        o.kills++;
        sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.oath.kill", o.kills, KILLS_NEEDED).withColor(0xFFD86B), true);
        checkComplete(sp, o);
    }

    /** A cada segundo: vigília junto ao coração (de noite, agachado). */
    public static void tickPlayers(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            SettlementData.Oath o = data.oath(sp.getUUID());
            if (o == null || o.vigil >= VIGIL_NEEDED || !sp.isShiftKeyDown()) continue;
            ServerLevel sl = sp.serverLevel();
            Places.Place p = Places.get(sl, o.city);
            if (p == null || p.dim() != sl.dimension() || !sl.isNight()) continue;
            if (sp.blockPosition().distSqr(p.center()) > 6 * 6) continue;
            o.vigil++;
            sl.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.2, sp.getZ(), 2, 0.4, 0.4, 0.4, 0.01);
            if (o.vigil % 5 == 0 || o.vigil == VIGIL_NEEDED) {
                sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.oath.vigil", o.vigil, VIGIL_NEEDED).withColor(0xB98CFF), true);
            }
            checkComplete(sp, o);
        }
    }

    private static void checkComplete(ServerPlayer sp, SettlementData.Oath o) {
        if (!o.complete() || o.sealGiven) return;
        o.sealGiven = true;
        Places.Place p = Places.get(sp.serverLevel(), o.city);
        Component name = p == null ? Component.literal(o.city) : p.name();
        ItemStack seal = new ItemStack(SettlementsContent.OATH_SEAL.get());
        seal.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("settlements.isekai_mahou.seal.city", name))));
        Rewards.give(sp, seal);
        sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        Rewards.banner(sp, Component.translatable("settlements.isekai_mahou.oath.done", name), 0xFFD86B);
    }

    // =====================================================================================
    //  Desafio
    // =====================================================================================
    public static void useSealOn(ServerPlayer sp, BlockPos pos) {
        Places.Place p = SettlementService.cityAt(sp.serverLevel(), pos);
        if (p == null || p.center().distSqr(pos) > 4) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.seal.use_heart").withColor(0xE8735E), true);
            return;
        }
        challenge(sp, p);
    }

    public static void challengeFromMenu(ServerPlayer sp, String cityId) {
        Places.Place p = Places.get(sp.serverLevel(), cityId);
        if (p == null) return;
        if (!sp.getInventory().hasAnyMatching(st -> st.is(SettlementsContent.OATH_SEAL.get()))) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.seal.missing").withColor(0xE8735E), true);
            return;
        }
        challenge(sp, p);
    }

    private static void challenge(ServerPlayer sp, Places.Place p) {
        SettlementData.Oath o = SettlementData.get(sp.server).oath(sp.getUUID());
        if (o == null || !o.city.equals(p.id()) || !o.sealGiven) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.seal.wrong_city").withColor(0xE8735E), true);
            return;
        }
        ServerLevel sl = sp.serverLevel();
        if (p.dim() != sl.dimension() || sp.blockPosition().distSqr(p.center()) > 16 * 16) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.seal.use_heart").withColor(0xE8735E), true);
            return;
        }
        UUID running = ACTIVE.get(p.id());
        if (running != null && sl.getEntity(running) instanceof CityChampion c && c.isAlive()) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.challenge.busy").withColor(0xE8735E), true);
            return;
        }
        long now = sl.getGameTime();
        if (o.lastFail >= 0 && now - o.lastFail < RETRY_TICKS) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.challenge.wait",
                    (RETRY_TICKS - (now - o.lastFail)) / 20).withColor(0xE8735E), true);
            return;
        }
        CityChampion champ = SettlementsContent.CHAMPION.get().create(sl);
        if (champ == null) return;
        BlockPos c = p.center();
        int x = c.getX(), z = c.getZ() + 5;
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        champ.moveTo(x + 0.5, y, z + 0.5, 180F, 0F);
        champ.setup(sp, p.id(), c, SettlementStyle.forFaction(p.faction()), MagicApi.get(sp).level());
        sl.addFreshEntity(champ);
        ACTIVE.put(p.id(), champ.getUUID());
        BossArena.seal(champ, Vec3.atBottomCenterOf(c), 28);
        sl.sendParticles(ParticleTypes.FLASH, champ.getX(), champ.getY() + 1.5, champ.getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, champ.getX(), champ.getY() + 0.2, champ.getZ(), 60, 1.5, 0.1, 1.5, 0.05);
        sl.playSound(null, champ.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 1.0F);
        Cutscenes.playNear(champ, 40, "boss_intro", Component.translatable("entity.isekai_mahou.city_champion"),
                Component.translatable("settlements.isekai_mahou.challenge.subtitle", p.name()), SettlementStyle.forFaction(p.faction()).color,
                60, sp);
        for (ServerPlayer near : sl.players()) {
            if (near.distanceToSqr(champ) < 64 * 64) {
                near.displayClientMessage(Component.translatable("settlements.isekai_mahou.challenge.start", sp.getDisplayName(), p.name())
                        .withColor(0xFFD86B), false);
            }
        }
    }

    public static void onChallengeFailed(ServerLevel sl, CityChampion champ, @Nullable ServerPlayer who) {
        ACTIVE.remove(champ.cityId());
        BossArena.release(champ);
        if (champ.challenger() != null) {
            SettlementData.Oath o = SettlementData.get(sl.getServer()).oath(champ.challenger());
            if (o != null) o.lastFail = sl.getGameTime();
        }
        if (who != null) {
            who.displayClientMessage(Component.translatable("settlements.isekai_mahou.challenge.failed").withColor(0xE8735E), false);
            who.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    public static void onChampionDefeated(ServerLevel sl, CityChampion champ) {
        ACTIVE.remove(champ.cityId());
        UUID id = champ.challenger();
        ServerPlayer who = id == null ? null : sl.getServer().getPlayerList().getPlayer(id);
        Places.Place p = Places.get(sl, champ.cityId());
        if (who == null || p == null) return;
        // o Selo é consumido na vitória
        for (int i = 0; i < who.getInventory().getContainerSize(); i++) {
            if (who.getInventory().getItem(i).is(SettlementsContent.OATH_SEAL.get())) {
                who.getInventory().getItem(i).shrink(1);
                break;
            }
        }
        crown(who, p);
    }

    /** O jogador vira Senhor da cidade. */
    public static void crown(ServerPlayer sp, Places.Place p) {
        MinecraftServer server = sp.server;
        SettlementData data = SettlementData.get(server);
        UUID old = Allegiance.leader(server, p.id());
        if (old != null && !old.equals(sp.getUUID())) {
            ServerPlayer prev = server.getPlayerList().getPlayer(old);
            if (prev != null) {
                prev.displayClientMessage(Component.translatable("settlements.isekai_mahou.lord.deposed", p.name(), sp.getDisplayName())
                        .withColor(0xE8735E), false);
            }
        }
        Allegiance.setLeader(server, p.id(), sp.getUUID());
        Allegiance.set(sp, p.id());
        Settlement s = data.city(p.id());
        if (s == null) {
            s = new Settlement(p.id(), Settlement.Kind.CONQUERED, SettlementStyle.forFaction(p.faction()), p.dim(), p.center(), "");
            s.affinity = 40;
            s.lastDay = SettlementService.day(server);
            data.put(s);
        }
        s.owner = sp.getUUID();
        s.ownerName = sp.getGameProfile().getName();
        data.endOath(sp.getUUID());
        Reputation.grantTitle(sp, TITLE);
        MagicApi.addCounter(sp, "settlements.lord", 1);
        ServerLevel sl = sp.serverLevel();
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1, sp.getZ(), 120, 1.0, 1.5, 1.0, 0.4);
        sl.playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5F, 1.0F);
        sl.playSound(null, p.center(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.7F);
        Rewards.banner(sp, Component.translatable("settlements.isekai_mahou.lord.banner", p.name()), 0xFFD86B);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("settlements.isekai_mahou.lord.announce",
                sp.getDisplayName(), p.name()).withColor(0xFFD86B), false);
        for (int i = 0; i < 2; i++) SettlementService.populate(sl, s);
        SettlementService.sendState(sp, p.id(), true);
    }

    /** Deixa de ser Senhor (abdicou ou perdeu a confiança do reino). */
    public static void abdicate(MinecraftServer server, Settlement s, boolean voluntary) {
        // v1.4: só tira o Senhor do poder — a cidade guarda estoques, população, nível e o registro (o próximo Senhor herda)
        UUID old = s.owner;
        Allegiance.setLeader(server, s.id, null);
        s.owner = null;
        s.ownerName = "";
        s.council.clear();
        if (old == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(old);
        if (p != null) {
            p.displayClientMessage(Component.translatable(voluntary ? "settlements.isekai_mahou.lord.abdicated" : "settlements.isekai_mahou.lord.lost",
                    Allegiance.cityName(server, s.id)).withColor(0xE8735E), false);
        }
    }

    public static void onReputation(ReputationChangedEvent e) {
        ServerPlayer sp = e.getPlayer();
        if (sp == null || e.getNewStanding().atLeast(Reputation.Standing.REVERED)) return;
        for (Settlement s : SettlementData.get(sp.server).ruledBy(sp.getUUID())) {
            if (s.own()) continue;
            Places.Place p = Places.get(sp.serverLevel(), s.id);
            if (p != null && p.faction().equals(e.getFaction())) abdicate(sp.server, s, false);
        }
    }

    // =====================================================================================
    //  Rastreador e tela
    // =====================================================================================
    public static void trackerTag(ServerPlayer sp, CompoundTag tag) {
        SettlementData.Oath o = SettlementData.get(sp.server).oath(sp.getUUID());
        if (o == null) return;
        Places.Place p = Places.get(sp.serverLevel(), o.city);
        if (p == null) return;
        tag.putString("OathCity", Component.Serializer.toJson(p.name(), sp.registryAccess()));
        tag.putInt("OathKills", o.kills);
        tag.putInt("OathTribute", o.tribute);
        tag.putInt("OathVigil", o.vigil);
        tag.putBoolean("OathSeal", o.sealGiven);
        tag.put("OathPos", NbtUtils.writeBlockPos(p.center()));
        tag.putString("OathDim", p.dim().location().toString());
    }

    /** Desafio em andamento nesta cidade? */
    public static boolean challengeRunning(ServerLevel sl, String cityId) {
        UUID id = ACTIVE.get(cityId);
        Entity e = id == null ? null : sl.getEntity(id);
        return e instanceof CityChampion c && c.isAlive();
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private Conquest() {}
}
