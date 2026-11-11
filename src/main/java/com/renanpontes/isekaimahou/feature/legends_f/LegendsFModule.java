package com.renanpontes.isekaimahou.feature.legends_f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.legends_f.entity.BarrierEntity;
import com.renanpontes.isekaimahou.feature.legends_f.glyph.DruidTechniques;
import com.renanpontes.isekaimahou.feature.legends_f.glyph.MageTechniques;
import com.renanpontes.isekaimahou.feature.legends_f.glyph.MonarchForm;
import com.renanpontes.isekaimahou.feature.legends_f.glyph.NecroTechniques;
import com.renanpontes.isekaimahou.feature.legends_f.glyph.WarlockTechniques;
import com.renanpontes.isekaimahou.feature.legends_f.glyph.WitchTechniques;
import com.renanpontes.isekaimahou.feature.legends_f.necro.NecroHooks;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "legends_f" (v1.1): técnicas icônicas de anime para as 5 classes MÁGICAS, como glifos de origem montáveis no
 * Grimório (só a própria classe aprende sozinha, pelo Círculo e o rank do ritual; as outras só por Lição).
 * <ul>
 *   <li>Mago (Frieren): Detectar Mana, Ocultar Mana, Barreira Defensiva Geral.</li>
 *   <li>Bruxa: Vassoura da Bruxa Viajante (Elaina), Maldição do Sono Eterno (Bela Adormecida), Mãos da Inveja (Re:Zero).</li>
 *   <li>Bruxo: Pacto da Motosserra (Chainsaw Man), Contrato Faustiano, Bang!</li>
 *   <li>Druida: Deus da Floresta (Mononoke), Modo Sábio (Naruto), Chamado dos Kodama.</li>
 *   <li>Necromante (Solo Leveling): Levante-se!, Comando do Monarca, Troca de Sombras e a forma G Monarca das Sombras.</li>
 * </ul>
 * 13 Magias Lendárias novas (ver {@link LegendRecipes}).
 */
public class LegendsFModule implements Module {
    @Override
    public String id() {
        return "legends_f";
    }

    @Override
    public void init(IEventBus modBus) {
        LfRegistry.register(modBus);
        modBus.addListener(LfNet::register);
        modBus.addListener(LegendRecipes::validate);

        for (EffectGlyph g : MageTechniques.all()) GlyphRegistry.register(g);
        for (EffectGlyph g : WitchTechniques.all()) GlyphRegistry.register(g);
        for (EffectGlyph g : WarlockTechniques.all()) GlyphRegistry.register(g);
        for (EffectGlyph g : DruidTechniques.all()) GlyphRegistry.register(g);
        for (EffectGlyph g : NecroTechniques.all()) GlyphRegistry.register(g);
        GlyphRegistry.register(new MonarchForm());
        LegendRecipes.register();

        // Soldados das Sombras: a luz fere, a sombra quase não
        ElementReactions.setMultiplier(IsekaiMahou.id("shadow_soldier"), Element.LIGHT, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("shadow_soldier"), Element.SHADOW, 0.5F);

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, Transformations::onIncoming);
        bus.addListener(EventPriority.HIGH, LivingDeathEvent.class, Transformations::onDeath);
        bus.addListener(EventPriority.LOWEST, LivingDeathEvent.class, NecroHooks::onDeath);
        bus.addListener(LivingChangeTargetEvent.class, Transformations::onChangeTarget);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, Transformations::onLogout);
        bus.addListener(PlayerEvent.StartTracking.class, LfStates::onStartTracking);
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, LfUnlocks::onLogin);
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) LfStates.resendSelf(sp);
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) LfStates.resendSelf(sp);
        });
        bus.addListener(com.renanpontes.isekaimahou.core.event.MagicProgressEvent.LevelUp.class, LfUnlocks::onLevel);
        bus.addListener(com.renanpontes.isekaimahou.core.event.MagicProgressEvent.CircleChanged.class, LfUnlocks::onCircle);
        bus.addListener(com.renanpontes.isekaimahou.core.event.MagicProgressEvent.Counter.class, LfUnlocks::onCounter);
        bus.addListener(com.renanpontes.isekaimahou.core.event.MagicProgressEvent.PathChosen.class, LfUnlocks::onPath);
        bus.addListener(ServerTickEvent.Post.class, e -> {
            Transformations.tick(e.getServer());
            NecroHooks.tick(e.getServer());
            if (e.getServer().overworld().getGameTime() % 100 == 0) LfStates.prune(e.getServer().overworld().getGameTime());
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            Transformations.clear();
            NecroHooks.clear();
            LfStates.clearAll();
            BarrierEntity.clear();
        });
    }
}
