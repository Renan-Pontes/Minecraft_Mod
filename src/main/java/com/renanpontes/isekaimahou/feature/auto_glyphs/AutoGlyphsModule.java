package com.renanpontes.isekaimahou.feature.auto_glyphs;

import java.util.List;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.auto_glyphs.glyph.FishingGlyph;
import com.renanpontes.isekaimahou.feature.auto_glyphs.glyph.IlluminateGlyph;
import com.renanpontes.isekaimahou.feature.auto_glyphs.glyph.LumberjackGlyph;
import com.renanpontes.isekaimahou.feature.auto_glyphs.glyph.PedestalTriggers;
import com.renanpontes.isekaimahou.feature.auto_glyphs.glyph.QuarryGlyph;
import com.renanpontes.isekaimahou.feature.automation.PedestalCondition;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Módulo "auto_glyphs" (v1.5): glifos de fazenda para os Pedestais Rúnicos (também funcionam na mão).
 * <ul>
 *   <li>Efeitos: Pedreira (escava camada por camada), Lenhador (derruba e replanta árvores), Pesca Arcana e Iluminar.</li>
 *   <li>Gatilhos de pedestal ({@link PedestalCondition}): Ao Amadurecer, Ao Esvaziar, Com Itens no Chão, Quando Houver
 *       Trabalho, Ao Anoitecer, Ao Amanhecer, Com Monstros Perto.</li>
 *   <li>Semear (módulo automation) passou a usar o FILTRO do pedestal como a semente escolhida.</li>
 * </ul>
 * Todos são Arcanos (qualquer classe aprende no caminho Arcano, pelo tier). Teste: {@code /mahou glyphs grant auto_glyphs}.
 */
public class AutoGlyphsModule implements Module {
    /** Ids de todos os glifos deste módulo (ordem do Grimório e do comando de teste). */
    public static final List<String> GLYPHS = List.of(QuarryGlyph.ID, LumberjackGlyph.ID, FishingGlyph.ID, IlluminateGlyph.ID,
            PedestalTriggers.ON_RIPE, PedestalTriggers.ON_EMPTY, PedestalTriggers.ON_ITEMS, PedestalTriggers.ON_WORK,
            PedestalTriggers.ON_NIGHT, PedestalTriggers.ON_DAY, PedestalTriggers.ON_MONSTERS);

    @Override
    public String id() {
        return "auto_glyphs";
    }

    @Override
    public void init(IEventBus modBus) {
        GlyphRegistry.register(new QuarryGlyph());
        GlyphRegistry.register(new LumberjackGlyph());
        GlyphRegistry.register(new FishingGlyph());
        GlyphRegistry.register(new IlluminateGlyph());
        GlyphRegistry.register(new PedestalTriggers.OnRipe());
        GlyphRegistry.register(new PedestalTriggers.OnEmpty());
        GlyphRegistry.register(new PedestalTriggers.OnItems());
        GlyphRegistry.register(new PedestalTriggers.OnWork());
        GlyphRegistry.register(new PedestalTriggers.OnNight());
        GlyphRegistry.register(new PedestalTriggers.OnDay());
        GlyphRegistry.register(new PedestalTriggers.OnMonsters());

        NeoForge.EVENT_BUS.addListener(AutoGlyphsModule::onCastPre);
        NeoForge.EVENT_BUS.addListener(AutoGlyphsModule::onLevelTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> FishingGlyph.clear());
        AutoGlyphsCommands.register();
    }

    /**
     * Gatilhos de pedestal usados NA MÃO: confere a condição em volta do jogador antes de pagar. Falsa = a magia não
     * sai. (O pedestal conjura sem este evento e confere as condições ele mesmo, antes de gastar mana.)
     */
    private static void onCastPre(SpellCastEvent.Pre e) {
        if (!(e.getCaster() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        var spell = e.getContext().spell();
        for (TriggerGlyph t : spell.triggers()) {
            if (!(t instanceof PedestalCondition cond)) continue;
            if (cond.ready(sp.serverLevel(), sp.blockPosition(), sp.position(), spell)) continue;
            e.setCanceled(true);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.auto_glyphs.condition_not_met", t.displayName())
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
    }

    /** Uma vez por dia de jogo: esquece pedreiras abandonadas. */
    private static void onLevelTick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel level && level.getGameTime() % 24000L == 1234L) {
            QuarryData.get(level).prune(level.getGameTime());
        }
    }

    /** Glifo do módulo pelo id curto (comandos). */
    static Glyph glyph(String path) {
        return GlyphRegistry.get(path);
    }
}
