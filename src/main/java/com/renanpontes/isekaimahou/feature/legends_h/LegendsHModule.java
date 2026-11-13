package com.renanpontes.isekaimahou.feature.legends_h;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.legends_h.entity.SheerHeartAttackEntity;
import com.renanpontes.isekaimahou.feature.legends_h.glyph.DxdGlyphs;
import com.renanpontes.isekaimahou.feature.legends_h.glyph.JojoGlyphs;
import com.renanpontes.isekaimahou.feature.legends_h.glyph.MhaGlyphs;
import com.renanpontes.isekaimahou.feature.legends_h.glyph.OtherGlyphs;
import com.renanpontes.isekaimahou.feature.legends_h.glyph.SheerHeartAttackForm;
import com.renanpontes.isekaimahou.feature.legends_h.jojo.Bombs;
import com.renanpontes.isekaimahou.feature.legends_h.jojo.Rewind;
import com.renanpontes.isekaimahou.feature.legends_h.jojo.TimeSkip;
import com.renanpontes.isekaimahou.feature.legends_h.mha.FullCowl;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "legends_h" (release 1.0, onda 10) — GLIFOS COMUNS de anime (não são Magias Lendárias): efeitos, aumentos e
 * uma forma que o jogador combina à vontade no Grimório. Aprendidos como qualquer glifo (Arcanos pelo nível do caminho
 * Arcano; os de elemento pelos nós do caminho do elemento; todos aparecem em pergaminhos de templos, chefes, guilda,
 * diário, reputação e no Mago Errante — nenhum é de Origem nem lendário).
 * <ul>
 *   <li>JoJo: King Crimson (efeito), Epitáfio (aumento), Bites the Dust (efeito), Killer Queen (efeito),
 *       Sheer Heart Attack (forma), ORA ORA (efeito).</li>
 *   <li>High School DxD: Boost (aumento), Transfer! (efeito), Balance Breaker (efeito).</li>
 *   <li>My Hero Academia: Full Cowl (efeito), One For All (aumento), Delaware Smash, Detroit Smash, Blackwhip (efeitos).</li>
 *   <li>Mob Psycho 100: Explosão Psíquica 100% (efeito) · Dragon Ball: Genki Dama (aumento) ·
 *       Jujutsu Kaisen: Fala Amaldiçoada (efeito).</li>
 * </ul>
 * Teste: {@code /mahou glyphs grant legends_h [jogadores]}. Ver docs/v10r/w10_legends_h.md.
 */
public class LegendsHModule implements Module {
    @Override
    public String id() {
        return "legends_h";
    }

    @Override
    public void init(IEventBus modBus) {
        LhRegistry.register(modBus);
        modBus.addListener(LhNet::register);

        for (Glyph g : JojoGlyphs.all()) GlyphRegistry.register(g);
        for (Glyph g : DxdGlyphs.all()) GlyphRegistry.register(g);
        for (Glyph g : MhaGlyphs.all()) GlyphRegistry.register(g);
        for (Glyph g : OtherGlyphs.all()) GlyphRegistry.register(g);
        GlyphRegistry.register(new SheerHeartAttackForm());

        var bus = NeoForge.EVENT_BUS;
        // King Crimson / Epitáfio
        bus.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, TimeSkip::onIncomingDamage);
        bus.addListener(ProjectileImpactEvent.class, TimeSkip::onProjectileImpact);
        bus.addListener(LivingChangeTargetEvent.class, TimeSkip::onChangeTarget);
        // Killer Queen (golpear a criatura-bomba detona)
        bus.addListener(LivingIncomingDamageEvent.class, Bombs::onIncomingDamage);
        // Bites the Dust (ia morrer marcado: o tempo volta)
        bus.addListener(EventPriority.HIGH, LivingDeathEvent.class, Rewind::onDeath);
        // Aumentos (Boost, One For All, Genki Dama, Epitáfio) e o "Transfer!"
        bus.addListener(EventPriority.LOW, SpellCastEvent.Pre.class, Augments::onCastPre);
        bus.addListener(SpellCastEvent.Post.class, Augments::onCastPost);
        bus.addListener(SpellDamageEvent.Pre.class, Augments::onSpellDamagePre);
        bus.addListener(SpellDamageEvent.Post.class, Augments::onSpellDamagePost);
        bus.addListener(EventPriority.LOW, LivingIncomingDamageEvent.class, Augments::onIncomingDamage);

        bus.addListener(ServerTickEvent.Post.class, e -> {
            TimeSkip.tick(e.getServer());
            Rewind.tick(e.getServer());
            Bombs.tick(e.getServer());
            FullCowl.tick(e.getServer());
            long now = e.getServer().overworld().getGameTime();
            if (now % 600 == 0) Augments.sweep(now);
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            TimeSkip.forget(e.getEntity().getUUID());
            Rewind.forget(e.getEntity().getUUID());
            FullCowl.forget(e.getEntity().getUUID());
            Augments.forget(e.getEntity().getUUID());
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            TimeSkip.clear();
            Rewind.clear();
            Bombs.clear();
            FullCowl.clear();
            Augments.clear();
            SheerHeartAttackEntity.clearAll();
        });

        // /mahou glyphs grant legends_h [jogadores]  (op)
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("glyphs")
                .then(Commands.literal("grant")
                        .then(Commands.literal(id())
                                .executes(c -> grant(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> grant(c, EntityArgument.getPlayers(c, "targets"))))))));
    }

    private static int grant(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
        for (ServerPlayer p : players) {
            for (ResourceLocation id : LhIds.ALL) MagicApi.learnGlyphSilently(p, id);
        }
        int n = players.size();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.legends_h.granted", LhIds.ALL.size(), n), true);
        return n;
    }
}
