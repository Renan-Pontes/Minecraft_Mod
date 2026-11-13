package com.renanpontes.isekaimahou.core;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Eventos do núcleo: mana, meditação, conjuração, sincronização, XP e contadores gerais.
 *
 * Contadores que o núcleo mantém (usados por Caminhos, Guilda, Diário...):
 *   cast.<elemento>            magias lançadas daquele elemento
 *   kill.<ns>.<mob>            abates de cada tipo de mob (ex.: kill.isekai_mahou.goblin, kill.minecraft.zombie)
 *   kill.spell.<elemento>      abates com magia daquele elemento
 *   kill.total                 abates totais
 *   harvest / breed / brew / study / meditate
 *
 * XP de nível por abate é dividido em GRUPO: todo jogador (não espectador) a até
 * {@link Progression#KILL_SHARE_RADIUS} blocos do mob ganha o valor cheio; contadores e XP de elemento
 * continuam só para quem deu o golpe final.
 */
@EventBusSubscriber(modid = IsekaiMahou.MODID)
public final class CoreEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) return;
        MagicData d = MagicApi.get(player);

        // Bônus de mana máximo (atributo)
        var maxAttr = player.getAttribute(CoreRegistries.MAX_MANA);
        int bonus = maxAttr == null ? 0 : (int) maxAttr.getValue();
        if (bonus != d.manaBonus) { d.manaBonus = bonus; d.manaDirty = true; }

        // Meditação: agachado, no chão e parado
        double dx = player.getX() - d.lastX, dy = player.getY() - d.lastY, dz = player.getZ() - d.lastZ;
        d.lastX = player.getX(); d.lastY = player.getY(); d.lastZ = player.getZ();
        boolean still = dx * dx + dy * dy + dz * dz < 1.0E-4;
        if (player.isShiftKeyDown() && player.onGround() && still && !player.isUsingItem() && d.channelSlot < 0) d.stillTicks++;
        else d.stillTicks = 0;
        boolean med = d.stillTicks >= Progression.MEDITATION_DELAY_TICKS;
        if (med != d.meditating) { d.meditating = med; d.manaDirty = true; }

        // Regeneração
        if (!player.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) {
            float regen = Progression.regenPerTick(d.level(), d.circle());
            var regenAttr = player.getAttribute(CoreRegistries.MANA_REGEN);
            if (regenAttr != null) regen *= (float) regenAttr.getValue();
            if (d.meditating) regen *= Progression.MEDITATION_REGEN_MULT;
            d.addMana(regen);
        }

        if (d.meditating) {
            int t = d.stillTicks - Progression.MEDITATION_DELAY_TICKS;
            if (t > 0 && t % Progression.MEDITATION_XP_INTERVAL == 0) {
                MagicApi.giveXp(player, 2, "meditation");
                MagicApi.addCounter(player, "meditate", 1);
                float shelves = bookshelfPower(player.serverLevel(), player.blockPosition());
                int study = Math.round(Math.min(shelves, 15F) / 3F);
                if (study > 0) {
                    MagicApi.giveXp(player, study, "study");
                    MagicApi.addCounter(player, "study", study);
                }
            }
            // v1.0: a aura da meditação é desenhada no cliente (ChannelAura), que respeita a opção de desligar
        }

        SpellCaster.tickChannel(player, d);

        // Sincronização
        if (d.dirty && player.tickCount % 5 == 0) CoreGameplay.syncAll(player);
        else if (d.manaDirty && player.tickCount % 4 == 0) CoreGameplay.syncMana(player);
    }

    /** Soma o "poder de encantamento" dos blocos num raio de 4 (estante = 1 cada). */
    public static float bookshelfPower(ServerLevel level, BlockPos center) {
        float power = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 2, 4))) {
            power += level.getBlockState(pos).getEnchantPowerBonus(level, pos);
        }
        return power;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0) {
            SpellCaster.interrupt(player);
        }
    }

    /** LOWEST: roda depois de todos; se alguém cancelou a morte (fase de chefe, totem...), não chega aqui. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        DamageSource src = event.getSource();
        // FakePlayer (pedestais, máquinas) não tem progresso próprio: o abate não rende XP a ninguém
        LivingEntity victim = event.getEntity();
        // Golpe final sem autor (queimadura, veneno, status, queda, lava): o abate vai para o último jogador que feriu o mob
        ServerPlayer player = src.getEntity() instanceof ServerPlayer sp ? sp
                : src.getEntity() == null && victim.getKillCredit() instanceof ServerPlayer sp2 ? sp2 : null;
        if (player == null || player instanceof FakePlayer) return;
        if (victim instanceof Player) return;
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        MagicApi.addCounter(player, "kill." + type.getNamespace() + "." + type.getPath(), 1);
        MagicApi.addCounter(player, "kill.total", 1);
        float hp = victim.getMaxHealth();
        boolean spell = src.is(CoreRegistries.SPELL_DAMAGE);
        if (spell) {
            Element el = ElementReactions.lastElement(victim, victim.level().getGameTime(), 100);
            if (el == null) el = Element.ARCANE;
            MagicApi.addCounter(player, "kill.spell." + el.id, 1);
            MagicApi.giveElementXp(player, el, Progression.killElementXp(hp));
        }
        int xp = Progression.killXp(hp, spell);
        for (ServerPlayer p : killParty(player, victim)) MagicApi.giveXp(p, xp, "kill");
    }

    /** Quem ganha o XP de um abate: quem matou + todo jogador vivo (não espectador) perto do mob. */
    private static List<ServerPlayer> killParty(ServerPlayer killer, LivingEntity victim) {
        List<ServerPlayer> party = new ArrayList<>();
        party.add(killer);
        if (!(victim.level() instanceof ServerLevel level)) return party;
        double r2 = Progression.KILL_SHARE_RADIUS * Progression.KILL_SHARE_RADIUS;
        for (ServerPlayer p : level.players()) {
            if (p == killer || p instanceof FakePlayer || p.isSpectator() || !p.isAlive()) continue;
            if (p.distanceToSqr(victim) <= r2) party.add(p);
        }
        return party;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        // FakePlayer (Pedestal Rúnico e máquinas) não tem progresso próprio
        if (!(event.getPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        BlockState state = event.getState();
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            MagicApi.addCounter(player, "harvest", 1);
            MagicApi.giveXp(player, 1, "nature");
        }
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            MagicApi.addCounter(player, "breed", 1);
            MagicApi.giveXp(player, 3, "nature");
        }
    }

    @SubscribeEvent
    public static void onBrew(PlayerBrewedPotionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MagicApi.addCounter(player, "brew", 1);
            MagicApi.giveXp(player, 2, "brewing");
        }
    }

    // ---------------------------------------------------------------- Vida que cresce com o nível
    @SubscribeEvent
    public static void onLevelUp(MagicProgressEvent.LevelUp event) {
        CoreGameplay.updateGrowthHealth(event.getPlayer());
    }

    @SubscribeEvent
    public static void onCircleChanged(MagicProgressEvent.CircleChanged event) {
        CoreGameplay.updateGrowthHealth(event.getPlayer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MagicApi.get(player).markDirty();
            CoreGameplay.updateGrowthHealth(player);
            CoreGameplay.syncAll(player);
            if (MagicApi.get(player).path() == MagicPath.NONE) {
                PacketDistributor.sendToPlayer(player, CorePayloads.OpenPathScreen.INSTANCE);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CoreGameplay.updateGrowthHealth(player);
            CoreGameplay.syncAll(player);
        }
    }

    /**
     * Saída do End (cópia SEM morte): o vanilla só copia os valores base dos atributos e corta a vida para 20.
     * Devolve o bônus de vida e a vida que o jogador tinha (senão cada saída do End curaria até +30).
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getOriginal() instanceof ServerPlayer old)) return;
        CoreGameplay.applyGrowthHealth(player, MagicApi.get(old).level(), false);
        player.setHealth(old.getHealth());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CoreGameplay.updateGrowthHealth(player);
            CoreGameplay.syncAll(player);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        MahouCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SpellScheduler.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SpellScheduler.clear();
    }

    private CoreEvents() {}
}
