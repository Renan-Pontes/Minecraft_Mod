package com.renanpontes.isekaimahou.feature.bosses_e;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellCounteredEvent;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.DragonCurses;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialChain;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialDragon;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialShade;
import com.renanpontes.isekaimahou.feature.bosses_e.item.PrimordialHeartItem;
import com.renanpontes.isekaimahou.feature.bosses_e.world.ArenaTerraform;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Eventos de jogo do módulo: devolução do terreno temporário, proteção desses blocos (ninguém minera o ouro/gelo da
 * arena), limpeza dos meteoros que sobraram de um mundo fechado no meio da luta, resgate do vazio, bônus do Coração.
 * v1.0: "sem morte instantânea" (nenhum golpe do dragão tira mais que {@link #MAX_HIT_FRACTION} da vida de um jogador),
 * as maldições ({@link DragonCurses}: tick, Maldição do Esquecimento nas conjurações, resgate agachado, limpeza) e o
 * aviso de contramágica para o dragão esperar a recarga da Pilha antes das Supremas.
 */
final class BeEvents {
    private static final String FX_TAG = "isekai_mahou.dragon_fx";
    /** Teto de um golpe do dragão (e dos clones/âncoras) num jogador: 35% da vida máxima. */
    static final float MAX_HIT_FRACTION = 0.35F;

    static void register() {
        NeoForge.EVENT_BUS.addListener(BeEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(BeEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(BeEvents::onExplosion);
        NeoForge.EVENT_BUS.addListener(BeEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(BeEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(BeEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BeEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(BeEvents::onSpellCast);
        NeoForge.EVENT_BUS.addListener(BeEvents::onCountered);
        NeoForge.EVENT_BUS.addListener(BeEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(BeEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(BeEvents::onRespawnClear);
        NeoForge.EVENT_BUS.addListener(BeEvents::onStopped);
        NeoForge.EVENT_BUS.addListener(PrimordialHeartItem::onRespawn);
        NeoForge.EVENT_BUS.addListener(PrimordialHeartItem::onLogin);
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        ArenaTerraform t = ArenaTerraform.peek(sl);
        if (t != null) t.tick(sl);
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        DragonCurses.tick(e.getServer());
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || e.getPlayer().getAbilities().instabuild) return;
        ArenaTerraform t = ArenaTerraform.peek(sl);
        if (t != null && t.isTemp(e.getPos())) {
            e.setCanceled(true);
            e.getPlayer().displayClientMessage(Component.translatable("message.isekai_mahou.primordial_dragon.temp_block")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        ArenaTerraform t = ArenaTerraform.peek(sl);
        if (t != null && t.size() > 0) e.getAffectedBlocks().removeIf(t::isTemp);
    }

    /** Blocos de exibição dos meteoros que ficaram salvos (mundo fechado no meio do golpe) somem ao carregar. */
    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.loadedFromDisk() && e.getEntity().getTags().contains(FX_TAG)) e.setCanceled(true);
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 5 == 0) PrimordialDragon.rescueFromVoid(sp);
    }

    /**
     * Sem morte instantânea: depois de armadura e resistências (o último a mexer), um golpe do Dragão, de um Clone ou de
     * uma Âncora nunca tira mais que 35% da vida máxima de um jogador.
     */
    private static void onDamage(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        if (!fromDragon(e.getSource().getEntity()) && !fromDragon(e.getSource().getDirectEntity())) return;
        float cap = p.getMaxHealth() * MAX_HIT_FRACTION;
        if (e.getNewDamage() > cap) e.setNewDamage(cap);
    }

    private static boolean fromDragon(Entity e) {
        return e instanceof PrimordialDragon || e instanceof PrimordialShade || e instanceof PrimordialChain;
    }

    /** Maldição do Esquecimento: a magia pode sumir da memória na hora de conjurar. */
    private static void onSpellCast(SpellCastEvent.Pre e) {
        LivingEntity c = e.getCaster();
        if (!(c instanceof ServerPlayer sp) || !DragonCurses.isCursed(sp)) return;
        if (DragonCurses.forgetCast(sp, e.getContext().spell().manaCost())) e.setCanceled(true);
    }

    /** O dragão foi contrariado: ele anota (as Supremas esperam a recarga de contramágica da Pilha). */
    private static void onCountered(SpellCounteredEvent e) {
        if (e.getPending().caster() instanceof PrimordialDragon d) d.onCountered(e.getLevel().getGameTime());
    }

    private static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) DragonCurses.clear(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) DragonCurses.clear(sp);
    }

    private static void onRespawnClear(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) DragonCurses.clear(sp);
    }

    private static void onStopped(ServerStoppedEvent e) {
        PrimordialDragon.clearStatic();
        DragonCurses.clearStatic();
    }

    private BeEvents() {}
}
