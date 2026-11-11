package com.renanpontes.isekaimahou.core.classes;

import com.renanpontes.isekaimahou.glyph.weapon.MeleeSwings;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponArrows;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponGlyphs;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponParry;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponStances;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponStrikes;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Inicialização do núcleo de classes — v0.9: categorias, recurso, formas de arma (docs/v09/API_classes.md); v1.0
 * (docs/v10/API_spells.md, parte A1a): Lições ({@link Lessons}), inflação de números das criaturas
 * ({@code core.balance}) e o rompimento do limite 150→200 ({@code CoreGameplay.onCounter}). Os glifos ({@code strike}, {@code arrow}, {@code stance}, {@code parry}, {@code guard}) são
 * registrados por {@code CoreGlyphs.register()}; aqui ficam os eventos que os fazem funcionar e as regras do recurso.
 */
public final class ClassesCore {
    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, ClassesPayloads::register);
        ClassesRegistries.register(modBus);
        com.renanpontes.isekaimahou.core.balance.BalanceCore.init();
        IEventBus bus = NeoForge.EVENT_BUS;
        // v1.0: nível escondido 150 -> 200 ao ajudar a matar o Dragão Primordial
        bus.addListener(com.renanpontes.isekaimahou.core.event.MagicProgressEvent.Counter.class,
                com.renanpontes.isekaimahou.core.CoreGameplay::onCounter);

        // Golpe corpo a corpo (antes do dano): alvo e força da barra de ataque
        bus.addListener(AttackEntityEvent.class, MeleeSwings::onAttack);
        // Recurso por categoria
        bus.addListener(PlayerTickEvent.Post.class, ClassResources::onPlayerTick);
        bus.addListener(LivingDamageEvent.Post.class, ClassResources::onDamage);
        // Golpe (strike)
        bus.addListener(LivingDamageEvent.Post.class, WeaponStrikes::onDamage);
        // Flecha (arrow)
        bus.addListener(ProjectileImpactEvent.class, WeaponArrows::onImpact);
        bus.addListener(EntityJoinLevelEvent.class, WeaponArrows::onJoin);
        bus.addListener(EntityLeaveLevelEvent.class, WeaponArrows::onLeave);
        // Aparar (parry): tecla bloqueada; escudo; contra-golpe no tempo certo (antes dos outros mexerem no dano)
        bus.addListener(com.renanpontes.isekaimahou.core.event.SpellCastEvent.Pre.class, WeaponParry::onPre);
        bus.addListener(LivingShieldBlockEvent.class, WeaponParry::onShieldBlock);
        bus.addListener(EventPriority.HIGH, false, LivingIncomingDamageEvent.class, WeaponParry::onIncomingDamage);

        bus.addListener(ServerTickEvent.Post.class, e -> {
            WeaponStrikes.tick(e.getServer());
            WeaponArrows.tick(e.getServer());
            WeaponStances.tick(e.getServer());
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) forget(sp);
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                // Modificadores temporários de uma Postura não sobrevivem ao relogar, mas por garantia
                WeaponStances.removeModifiers(sp);
                ClassResources.syncRegenModifier(sp);
                // XP de professor guardado enquanto estava offline
                Lessons.onLogin(sp);
            }
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            WeaponGlyphs.clearAll();
            ClassResources.clear();
        });
    }

    private static void forget(ServerPlayer sp) {
        WeaponStances.forget(sp);
        WeaponStrikes.forget(sp.getUUID());
        MeleeSwings.forget(sp.getUUID());
        ClassResources.forget(sp.getUUID());
    }

    private ClassesCore() {}
}
