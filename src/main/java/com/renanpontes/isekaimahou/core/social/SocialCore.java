package com.renanpontes.isekaimahou.core.social;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Destruction;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Inicialização do núcleo v1.0 — A2: party, troca, duelo, perfil, facções aliadas, arenas de chefe e guerra de cidades.
 * Ver docs/V10_DESIGN.md §2 (A2) e docs/v10/API_social.md. APIs: {@code core.api.Party}, {@code core.api.BossArena},
 * {@code core.api.Allegiance}, {@code core.api.Reputation#setAllied}.
 */
public final class SocialCore {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IsekaiMahou.MODID);
    public static final Supplier<MenuType<TradeMenu>> TRADE_MENU = MENUS.register("social_trade",
            () -> IMenuTypeExtension.create(TradeMenu::new));

    public static void init(IEventBus modBus) {
        MENUS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, SocialPayloads::register);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterCommandsEvent.class, SocialCommands::onRegister);
        SocialCommands.registerOp();

        // Regras de dano (grupo, duelo, guerra, aliados) antes dos outros ouvintes mexerem no valor
        bus.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, SocialRules::onIncomingDamage);
        bus.addListener(EventPriority.HIGH, AttackEntityEvent.class, SocialRules::onAttack);
        bus.addListener(EventPriority.HIGH, ProjectileImpactEvent.class, SocialRules::onProjectile);
        bus.addListener(EventPriority.LOW, LivingChangeTargetEvent.class, SocialRules::onChangeTarget);
        bus.addListener(LivingDamageEvent.Post.class, SocialRules::onDamagePost);

        // Duelo: o golpe que mataria vira derrota (antes dos outros ouvintes de morte)
        bus.addListener(EventPriority.HIGHEST, LivingDeathEvent.class, e -> {
            DuelService.onDeath(e);
            if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer sp) TradeService.abort(sp);
        });
        bus.addListener(EventPriority.LOWEST, LivingDeathEvent.class, PartyService::onDeath);
        bus.addListener(EventPriority.LOWEST, LivingDeathEvent.class, BossArenaService::onDeath);

        // Arenas de chefe
        bus.addListener(LivingChangeTargetEvent.class, BossArenaService::onChangeTarget);
        bus.addListener(EventPriority.LOW, LivingIncomingDamageEvent.class, BossArenaService::onIncomingDamage);
        bus.addListener(EventPriority.HIGH, BlockEvent.BreakEvent.class, BossArenaService::onBreak);
        bus.addListener(EventPriority.HIGH, BlockEvent.EntityPlaceEvent.class, BossArenaService::onPlace);
        bus.addListener(EventPriority.HIGH, PlayerInteractEvent.RightClickItem.class, BossArenaService::onUseItem);
        bus.addListener(EventPriority.HIGH, PlayerInteractEvent.RightClickBlock.class, BossArenaService::onUseOnBlock);
        bus.addListener(EventPriority.LOW, ExplosionEvent.Detonate.class, BossArenaService::onExplosion);
        bus.addListener(EntityMobGriefingEvent.class, BossArenaService::onGriefing);
        Destruction.addProtection(BossArenaService::isProtected);

        // Chat de grupo ligado: o chat normal vai só para o grupo
        bus.addListener(EventPriority.HIGH, ServerChatEvent.class, e -> {
            ServerPlayer sp = e.getPlayer();
            if (PartyService.partyChat(sp.getUUID()) && PartyService.partyOf(sp) != null) {
                e.setCanceled(true);
                PartyService.chat(sp, e.getRawText());
            }
        });

        bus.addListener(ServerTickEvent.Post.class, e -> {
            SocialSync.tick(e.getServer());
            DuelService.tick(e.getServer());
            BossArenaService.tick(e.getServer());
            if (e.getServer().getTickCount() % 20 == 0) Requests.tick(e.getServer());
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                PartyService.onLogin(sp);
                SocialSync.markState(sp.getUUID());
                SocialSync.markAllStates();
            }
        });
        // Saída: cancela a troca ANTES do save (os itens voltam ao inventário), encerra duelo, limpa pedidos
        bus.addListener(EventPriority.HIGHEST, PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            TradeService.abort(sp);
            DuelService.onLogout(sp);
            PartyService.onLogout(sp);
            Requests.forget(sp.getUUID());
            SocialRules.forget(sp.getUUID());
            BossArenaService.forget(sp.getUUID());
            SocialSync.forget(sp.getUUID());
            SocialSync.markAllStates();
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                TradeService.abort(sp);
                SocialSync.markState(sp.getUUID());
            }
        });
        bus.addListener(ServerStoppedEvent.class, e -> clearAll());
    }

    private static void clearAll() {
        TradeService.clear();
        DuelService.clear();
        Requests.clear();
        PartyService.clear();
        SocialRules.clear();
        BossArenaService.clear();
        SocialSync.clear();
        SocialData.clearCache();
    }

    // ---------------------------------------------------------------- registro usado pela API BossArena
    public static void registerArenaType(ResourceLocation id) {
        BossArenaService.registerType(id);
    }

    public static void registerArenaType(EntityType<?> type) {
        BossArenaService.registerType(type);
    }

    public static void excludeArenaType(ResourceLocation id) {
        BossArenaService.excludeType(id);
    }

    public static void protectStructure(ResourceLocation id) {
        StructureGuard.addStructure(id);
    }

    public static void protectPlace(String id) {
        StructureGuard.addPlace(id);
    }

    private SocialCore() {}
}
