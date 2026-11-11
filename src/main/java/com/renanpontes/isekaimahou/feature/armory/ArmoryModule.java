package com.renanpontes.isekaimahou.feature.armory;

import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryLuteItem;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "armory" (Arsenal): armas por tier — os 5 focos das Mágicas (v0.7) e, desde a v1.0, as 10 armas AVULSAS da v0.9
 * (espada longa, maça sagrada, machado grande, manoplas de ki, lâmina + Escudo do Cavaleiro, arco longo, adagas gêmeas,
 * alaúde, besta arcana, besta de caçador: sem classe, para qualquer um) —, acessórios do Curios, Molde de Melhoria
 * Arcana (Mesa de Ferraria) e recompensas de marcos de nível (a arma de marco das classes novas vem do módulo da classe,
 * procurada por id: {@link LevelMilestones}).
 * Assets (modelos 3D, texturas), receitas, loja da Guilda, tags e traduções: tools/gen/armory.py.
 */
public class ArmoryModule implements Module {
    @Override
    public String id() {
        return "armory";
    }

    @Override
    public void init(IEventBus modBus) {
        ArmoryItems.register(modBus);
        // Tocar o alaúde conta como "mirando" para o Foco (agachado e parado), como puxar o arco
        ClassResources.addAimItem(stack -> stack.getItem() instanceof ArmoryLuteItem);

        NeoForge.EVENT_BUS.addListener(MagicProgressEvent.LevelUp.class, LevelMilestones::onLevelUp);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, LevelMilestones::onLogin);

        // Focos das Mágicas (v0.7)
        NeoForge.EVENT_BUS.addListener(SpellCastEvent.Post.class, ArmoryPassives::onSpellPost);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, ArmoryPassives::onDamagePost);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, ArmoryPassives::onPlayerTick);
        // LOWEST (como a Colheita do ritual_necro): só colhe se nenhuma outra regra cancelou a morte (fases de chefe etc.)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, ArmoryPassives::onDeath);

        // Armas das Guerreiras/À Distância (v0.9). Multiplicadores cedo (antes de escudos/armaduras de outros módulos)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, ClassWeaponPassives::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, ArmoryArrows::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, ClassWeaponPassives::onDamagePost);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, ArmoryArrows::onDamagePost);
        NeoForge.EVENT_BUS.addListener(LivingShieldBlockEvent.class, ClassWeaponPassives::onShieldBlock);
        NeoForge.EVENT_BUS.addListener(ProjectileImpactEvent.class, ArmoryArrows::onImpact);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, ArmoryArrows::onJoin);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ClassWeaponPassives::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ArmoryArrows::onServerTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, ClassWeaponPassives::onLogout);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, ClassWeaponPassives::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, ArmoryArrows::onServerStopped);
    }
}
