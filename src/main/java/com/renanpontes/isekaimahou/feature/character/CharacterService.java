package com.renanpontes.isekaimahou.feature.character;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.OnboardingEvent;
import com.renanpontes.isekaimahou.core.ux.EntryFlow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lado servidor da raça: sincronização, a etapa {@value EntryFlow#STEP_RACE} da entrada no jogo (logo depois da classe),
 * confirmar a escolha (entrada, aba ou altar), a linha "Raça: X" do "Parabéns!", o aviso para personagens antigos e a
 * checagem periódica. Ao escolher/trocar a raça, as skills raciais do Grimório acompanham ({@link RaceGlyphs}).
 * v1.0.1: todas as raças são livres (sem bloqueio); o tick também cuida das passivas por nível de raça, da
 * Transformação ({@link RaceForms}) e do aviso de nível de raça ({@link RaceProgress}).
 */
public final class CharacterService {
    /** Id da etapa de criação no {@link Onboarding} (a do núcleo: sempre logo depois da classe). */
    public static final String STEP = EntryFlow.STEP_RACE;
    /** Custo de renascer no altar (Cristais de Mana; criativo não paga). */
    public static final int ALTAR_COST = 3;
    private static final double ALTAR_REACH = 8.0;

    /** Altar aberto por jogador (o "Renascer" só vale perto dele). */
    private static final Map<UUID, GlobalPos> ALTAR_SESSION = new ConcurrentHashMap<>();
    /** Jogadores que já receberam o aviso "escolha a sua raça" nesta sessão. */
    private static final Set<UUID> NOTICED = ConcurrentHashMap.newKeySet();
    /** Troca grátis dada por um op ({@code /mahou character rebuild}). */
    private static final Set<UUID> FREE_REBUILD = ConcurrentHashMap.newKeySet();

    static void init() {
        Onboarding.addCreationStep(STEP);
        EntryFlow.addCongratsLine("race", 0, sp -> {
            CharacterData d = CharacterContent.data(sp);
            return d.created() ? Component.translatable("character.isekai_mahou.congrats_race", d.race().displayName()) : null;
        });
        NeoForge.EVENT_BUS.addListener(CharacterService::onCreationStep);
        NeoForge.EVENT_BUS.addListener(CharacterService::onLogin);
        NeoForge.EVENT_BUS.addListener(CharacterService::onLogout);
        NeoForge.EVENT_BUS.addListener(CharacterService::onRespawn);
        NeoForge.EVENT_BUS.addListener(CharacterService::onTracking);
        NeoForge.EVENT_BUS.addListener(CharacterService::onTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            ALTAR_SESSION.clear();
            NOTICED.clear();
            FREE_REBUILD.clear();
            RacePassives.clearAll();
            RaceForms.clearAll();
        });
    }

    // =====================================================================================
    //  Sincronização
    // =====================================================================================
    /** Reaplica os efeitos, manda os dados ao dono e a aparência a quem o vê. */
    public static void flush(ServerPlayer sp) {
        CharacterData d = CharacterContent.data(sp);
        d.dirty = false;
        RacePassives.apply(sp, d);
        PacketDistributor.sendToPlayer(sp, new CharacterNetwork.Data(d.toClientTag(sp.registryAccess())));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, look(sp, d));
    }

    private static CharacterNetwork.Look look(ServerPlayer sp, CharacterData d) {
        return new CharacterNetwork.Look(sp.getId(), d.created() ? d.race().ordinal() : -1, d.ancestry().color);
    }

    private static void onTracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer target && !(target instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(viewer, look(target, CharacterContent.data(target)));
            var wings = target.getEffect(CharacterContent.SHADOW_WINGS);
            if (wings != null) {
                PacketDistributor.sendToPlayer(viewer, new CharacterNetwork.RaceFx(target.getId(), CharacterNetwork.FX_WINGS, wings.getDuration()));
            }
            int form = RaceForms.remaining(target);
            if (form > 0) {
                PacketDistributor.sendToPlayer(viewer, new CharacterNetwork.RaceFx(target.getId(), CharacterNetwork.FX_FORM, form));
            }
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        RacePassives.clearLegacy(sp);
        CharacterContent.data(sp).dirty = true;
        flush(sp);
        RaceGlyphs.reconcile(sp, false);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        ALTAR_SESSION.remove(id);
        NOTICED.remove(id);
        FREE_REBUILD.remove(id);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        CharacterContent.data(sp).dirty = true;
        flush(sp);
        // O corpo novo nasce com a vida base: com a vida extra da raça aplicada, renasce cheio
        if (!e.isEndConquered()) sp.setHealth(sp.getMaxHealth());
        // Morreu com a tela de raça aberta (a tela de morte a fecha): abre de novo
        List<String> pending = Onboarding.pendingCreationSteps(sp);
        if (!pending.isEmpty() && STEP.equals(pending.get(0))) {
            PacketDistributor.sendToPlayer(sp, new CharacterNetwork.Open(CharacterNetwork.MODE_CREATION, BlockPos.ZERO));
        }
    }

    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        CharacterData d = CharacterContent.data(sp);
        if (d.dirty) flush(sp);
        RaceForms.tick(sp, d);
        int t = sp.tickCount;
        if (t % 5 == 2) RacePassives.tickWall(sp, d);
        if (t % 20 == 7) {
            RacePassives.tickVision(sp, d);
            RacePassives.tickSecond(sp, d);
            if (t > 200) legacyNotice(sp, d);
        }
        if (t % 200 == 37) RaceProgress.check(sp);
    }

    /** Personagem antigo (ou que pulou a etapa): uma vez por sessão, convida a escolher a raça pela aba. */
    private static void legacyNotice(ServerPlayer sp, CharacterData d) {
        if (d.created() || NOTICED.contains(sp.getUUID())) return;
        if (MagicApi.get(sp).path() == MagicPath.NONE || Onboarding.quiet(sp)) return;
        if (Onboarding.pendingCreationSteps(sp).contains(STEP)) return;
        NOTICED.add(sp.getUUID());
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.legacy_title").withColor(0xFFD86B));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.legacy_body",
                Component.keybind("key.isekai_mahou.menu")).withColor(0xE8E2F4));
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.character.legacy"),
                Component.translatable("toast.isekai_mahou.character.legacy_sub"), new ItemStack(CharacterContent.ALTAR_ITEM.get()));
    }

    // =====================================================================================
    //  Entrada no jogo
    // =====================================================================================
    private static void onCreationStep(OnboardingEvent.CreationStep e) {
        if (!STEP.equals(e.getStepId())) return;
        ServerPlayer sp = e.getPlayer();
        flush(sp);
        PacketDistributor.sendToPlayer(sp, new CharacterNetwork.Open(CharacterNetwork.MODE_CREATION, BlockPos.ZERO));
    }

    /** Altar da Reencarnação usado: abre a tela no modo altar. */
    public static void openAltar(ServerPlayer sp, BlockPos pos) {
        // Sem classe ainda não há personagem para renascer (a escolha da classe vem primeiro)
        if (MagicApi.get(sp).path() == MagicPath.NONE) {
            fail(sp, Component.translatable("message.isekai_mahou.character.altar_no_class"));
            return;
        }
        ALTAR_SESSION.put(sp.getUUID(), GlobalPos.of(sp.level().dimension(), pos.immutable()));
        flush(sp);
        PacketDistributor.sendToPlayer(sp, new CharacterNetwork.Open(CharacterNetwork.MODE_ALTAR, pos.immutable()));
    }

    /** Troca grátis (comando de op): abre a tela no modo aba. */
    static void grantRebuild(ServerPlayer sp) {
        FREE_REBUILD.add(sp.getUUID());
        flush(sp);
        PacketDistributor.sendToPlayer(sp, new CharacterNetwork.Open(CharacterNetwork.MODE_TAB, BlockPos.ZERO));
    }

    // =====================================================================================
    //  Ações do cliente
    // =====================================================================================
    static void confirm(ServerPlayer sp, int mode, int raceIdx) {
        CharacterData d = CharacterContent.data(sp);
        if (raceIdx < 0 || raceIdx >= Race.ALL.length) return;
        Race race = Race.byIndex(raceIdx);
        if (MagicApi.get(sp).path() == MagicPath.NONE) return;
        boolean stepPending = Onboarding.pendingCreationSteps(sp).contains(STEP);
        switch (mode) {
            case CharacterNetwork.MODE_CREATION -> {
                if (!stepPending && d.created()) return;
            }
            case CharacterNetwork.MODE_TAB -> {
                if (d.created() && !FREE_REBUILD.contains(sp.getUUID()) && !stepPending) return;
            }
            case CharacterNetwork.MODE_ALTAR -> {
                if (!nearAltar(sp)) {
                    fail(sp, Component.translatable("message.isekai_mahou.character.altar_far"));
                    return;
                }
                if (d.created() && d.race() == race) {
                    fail(sp, Component.translatable("message.isekai_mahou.character.same_race", race.displayName()));
                    return;
                }
                // Escolher pela 1ª vez (personagem antigo) é grátis, como na aba; renascer custa os cristais
                if (d.created() && !sp.getAbilities().instabuild && !pay(sp)) {
                    fail(sp, Component.translatable("message.isekai_mahou.character.altar_cost", ALTAR_COST,
                            CoreRegistries.MANA_CRYSTAL.get().getDescription()));
                    return;
                }
            }
            default -> {
                return;
            }
        }
        Race before = d.created() ? d.race() : null;
        Element ancestry = race == Race.DRAGONBORN && before != Race.DRAGONBORN ? ancestryFor(sp) : null;
        d.choose(race, ancestry);
        FREE_REBUILD.remove(sp.getUUID());
        RaceProgress.sync(sp); // o nível da raça nova já vale, sem letreiro
        flush(sp);
        boolean quiet = Onboarding.quiet(sp) || mode == CharacterNetwork.MODE_CREATION;
        RaceGlyphs.reconcile(sp, !quiet);
        if (mode == CharacterNetwork.MODE_ALTAR) {
            d.addRebirth();
            rebirthFx(sp, race);
            Rewards.banner(sp, Component.translatable("message.isekai_mahou.character.reborn", race.displayName()), race.rgb);
        } else {
            ServerLevel level = sp.serverLevel();
            level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 24, 0.4, 0.8, 0.4, 0.04);
            RaceFx.dustBurst(level, sp.position().add(0, 1.0, 0), race.rgb, 30, 0.5);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.2F);
            if (mode == CharacterNetwork.MODE_TAB && !Onboarding.quiet(sp)) {
                Rewards.banner(sp, Component.translatable("message.isekai_mahou.character.created", race.displayName()), race.rgb);
            }
        }
        // Na entrada no jogo isto abre a próxima etapa (ou o "Parabéns!"); fora dela só grava a flag
        Onboarding.completeCreationStep(sp, STEP);
    }

    private static Element ancestryFor(ServerPlayer sp) {
        Element e = MagicApi.get(sp).path().primaryAffinity();
        return e != null && Element.BASE.contains(e) ? e : Element.FIRE;
    }

    private static boolean nearAltar(ServerPlayer sp) {
        GlobalPos g = ALTAR_SESSION.get(sp.getUUID());
        if (g == null || !g.dimension().equals(sp.level().dimension())) return false;
        if (!sp.level().getBlockState(g.pos()).is(CharacterContent.ALTAR.get())) return false;
        return sp.distanceToSqr(g.pos().getCenter()) <= ALTAR_REACH * ALTAR_REACH;
    }

    private static boolean pay(ServerPlayer sp) {
        var crystal = CoreRegistries.MANA_CRYSTAL.get();
        if (sp.getInventory().countItem(crystal) < ALTAR_COST) return false;
        sp.getInventory().clearOrCountMatchingItems(s -> s.is(crystal), ALTAR_COST, sp.inventoryMenu.getCraftSlots());
        return true;
    }

    private static void rebirthFx(ServerPlayer sp, Race race) {
        ServerLevel level = sp.serverLevel();
        GlobalPos g = ALTAR_SESSION.get(sp.getUUID());
        BlockPos at = g != null ? g.pos() : sp.blockPosition();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 60, 0.3, 1.2, 0.3, 0.04);
        level.sendParticles(ParticleTypes.SOUL, sp.getX(), sp.getY() + 1.0, sp.getZ(), 30, 0.5, 0.9, 0.5, 0.05);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1.0, sp.getZ(), 70, 0.6, 1.0, 0.6, 0.4);
        level.sendParticles(RaceFx.dust(race.rgb, 1.8F), sp.getX(), sp.getY() + 1.0, sp.getZ(), 50, 0.7, 1.0, 0.7, 0.02);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    static void toggle(ServerPlayer sp, int option, boolean on) {
        if (option != 0) return;
        CharacterData d = CharacterContent.data(sp);
        d.setDarkvision(on);
        if (!on) RacePassives.clearVision(sp);
    }

    static void fail(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg.copy().withColor(0xE07A7A), true);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 1.0F);
    }

    private CharacterService() {}
}
