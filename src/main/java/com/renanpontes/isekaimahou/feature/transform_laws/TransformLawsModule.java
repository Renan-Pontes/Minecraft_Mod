package com.renanpontes.isekaimahou.feature.transform_laws;

import com.renanpontes.isekaimahou.glyph.TransformLaws;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Módulo "transform_laws" — v1.4: <b>Leis de Transformação</b> (regras escolhidas pelo jogador que valem enquanto ele
 * está transformado, sempre com bônus e preço, como os votos de Jujutsu).
 * <ul>
 *   <li>{@link TlawGlyphs}: as 13 leis (glifos do tipo LAW, subclasse {@code TransformLawGlyph} do núcleo);</li>
 *   <li>{@link TlawTargets}: quais glifos são transformações (Kaioken, formas de Druida, Lich, Transcendência...);</li>
 *   <li>{@link TlawSessions}: sessões por jogador (a API pública é {@code glyph.TransformLaws});</li>
 *   <li>{@link TlawRules}: as regras; {@link TlawRace}: leis da Transformação Racial; {@link TlawUnlocks}: como aprender;</li>
 *   <li>release 1.0: {@link TransformForge} — a forma Transformação montada no Grimório ({@link TransformationForm},
 *       {@link ForgeSessions}) e os espaços de receita da tecla V e das liberações de classe ({@link ForgeData});</li>
 *   <li>cliente: aba "Votos" (menu B → Personagem).</li>
 * </ul>
 */
public class TransformLawsModule implements Module {
    @Override
    public String id() {
        return "transform_laws";
    }

    @Override
    public void init(IEventBus modBus) {
        TlawContent.register(modBus);
        TlawNetwork.register(modBus);
        ForgeNetwork.register(modBus);
        ForgeSessions.init();
        TlawGlyphs.register();
        TlawTargets.register();
        TransformLaws.install(TlawSessions.INSTANCE);
        TlawRules.init();
        TlawUnlocks.init();
        TlawHollow.init();
        TlawCommands.register();
        ForgeCommands.register();

        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 5 != 0) return;
            TlawRace.tick(sp);
            TlawSessions.tick(sp);
            ForgeSessions.tick(sp);
            if (sp.tickCount % 600 == 0) TlawUnlocks.check(sp, true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                TlawSessions.clearFlags(sp);
                ForgeSessions.login(sp);
            }
        });
        // O attachment das receitas é copiado na morte/volta do End: o cliente recebe a cópia de novo
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) ForgeData.sync(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                TlawSessions.logout(sp);
                TlawRace.forget(sp.getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            TlawSessions.clearAll();
            ForgeSessions.clearAll();
            TlawRace.clearAll();
            TlawRules.clearAll();
        });
    }
}
