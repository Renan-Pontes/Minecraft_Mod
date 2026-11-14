package com.renanpontes.isekaimahou.feature.legends_k;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.legends_k.state.Auras;
import com.renanpontes.isekaimahou.feature.legends_k.state.Bindings;
import com.renanpontes.isekaimahou.feature.legends_k.state.Guards;
import com.renanpontes.isekaimahou.feature.legends_k.state.PocketDimension;
import com.renanpontes.isekaimahou.feature.legends_k.state.TitanState;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "legends_k" (v1.1, pedido do dono 2026-11-13) — TÉCNICAS DE ANIME NAS CLASSES. Cada técnica é um glifo de
 * efeito comum do Grimório, de ORIGEM de uma classe, que a classe aprende sozinha por Círculo ({@link LkUnlocks}).
 * Lista e números em {@link LkGlyphs} e em {@code docs/v11/k_class_techniques.md}.
 * <p>
 * Estado (fase na dimensão, escudos, contra-ataques, auras, Titã...) fica só em mapas do servidor, por UUID, e some ao
 * sair do jogo; a única coisa salva é a marca da criatura sugada pelo Kamui/D4C (para ela voltar se o mundo fechar).
 * Teste: {@code /mahou glyphs grant legends_k [jogador]} ensina os 19 glifos.
 */
public class LegendsKModule implements Module {
    @Override
    public String id() {
        return "legends_k";
    }

    @Override
    public void init(IEventBus modBus) {
        modBus.addListener(LkNet::register);
        LkGlyphs.register();

        var bus = NeoForge.EVENT_BUS;
        // Conjuração: recargas longas (Ponto Zero, Retorno pela Morte)
        bus.addListener(SpellCastEvent.Pre.class, Guards::onCastPre);
        // Dano: dimensão (intangível) → defesas → pisão do Titã
        bus.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, PocketDimension::onIncoming);
        bus.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, Guards::onIncoming);
        bus.addListener(EventPriority.LOW, LivingIncomingDamageEvent.class, TitanState::onIncoming);
        bus.addListener(LivingDamageEvent.Post.class, Bindings::onDamage);
        bus.addListener(EventPriority.HIGH, ProjectileImpactEvent.class, PocketDimension::onProjectile);
        bus.addListener(ProjectileImpactEvent.class, Guards::onProjectile);
        bus.addListener(LivingChangeTargetEvent.class, PocketDimension::onTarget);
        bus.addListener(EventPriority.HIGH, MobEffectEvent.Applicable.class, PocketDimension::onEffect);
        bus.addListener(MobEffectEvent.Applicable.class, Guards::onEffect);
        bus.addListener(LivingKnockBackEvent.class, Guards::onKnockback);
        bus.addListener(EventPriority.HIGH, LivingDeathEvent.class, Guards::onDeath);
        bus.addListener(EventPriority.LOWEST, LivingDeathEvent.class, e -> forget(e.getEntity()));
        bus.addListener(PlayerTickEvent.Post.class, PocketDimension::onPlayerTick);
        bus.addListener(EntityJoinLevelEvent.class, PocketDimension::onJoin);
        bus.addListener(net.neoforged.neoforge.event.entity.living.MobDespawnEvent.class, PocketDimension::onDespawn);
        bus.addListener(ServerTickEvent.Post.class, e -> {
            var server = e.getServer();
            PocketDimension.tick(server);
            Auras.tick(server);
            Bindings.tick(server);
            TitanState.tick(server);
            if (server.getTickCount() % 20 == 7) Guards.tickVisuals(server);
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            forget(e.getEntity());
            if (e.getEntity() instanceof ServerPlayer sp) Auras.forgetFieldsOf(sp);
        });
        bus.addListener(ServerStoppingEvent.class, e -> PocketDimension.releaseAll(e.getServer()));
        bus.addListener(ServerStoppedEvent.class, e -> {
            Guards.clear();
            Auras.clear();
            Bindings.clear();
            TitanState.clear();
        });
        // Desbloqueio pela própria classe
        bus.addListener(MagicProgressEvent.LevelUp.class, LkUnlocks::onLevel);
        bus.addListener(MagicProgressEvent.CircleChanged.class, LkUnlocks::onCircle);
        bus.addListener(MagicProgressEvent.PathChosen.class, LkUnlocks::onPath);
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, LkUnlocks::onLogin);

        registerCommands();
    }

    /** Quem saiu do jogo ou morreu: nada de estado pendurado (o Titã perde os modificadores). */
    private static void forget(LivingEntity e) {
        PocketDimension.forget(e.getUUID());
        Guards.forget(e.getUUID());
        Auras.forget(e.getUUID());
        Bindings.forget(e.getUUID());
        TitanState.forget(e);
    }

    /** {@code /mahou glyphs grant legends_k [jogador]} (op): ensina todos os glifos deste módulo. */
    private static void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("glyphs")
                .then(MahouCommands.op("grant").then(Commands.literal("legends_k")
                        .executes(c -> grant(c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> grant(EntityArgument.getPlayer(c, "player"))))))));
    }

    private static int grant(ServerPlayer p) {
        MutableComponent list = Component.empty();
        int n = 0;
        for (ResourceLocation id : LkGlyphs.all()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null) continue;
            MagicApi.learnGlyphSilently(p, id);
            if (n++ > 0) list.append(Component.literal(", "));
            list.append(g.displayName());
        }
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.legends_k.granted", n, list)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return n;
    }
}
