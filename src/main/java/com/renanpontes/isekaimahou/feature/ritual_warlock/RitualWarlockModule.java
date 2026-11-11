package com.renanpontes.isekaimahou.feature.ritual_warlock;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.feature.ritual_warlock.client.WarlockClientHandlers;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.DrownedKingEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.EmberLordEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.VoidLadyEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.network.WarlockPayloads;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactBoons;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactRitual;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactState;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Modulo "ritual_warlock": Pacto no Círculo de Giz.
 * Giz Ritual + velas acesas em volta do Altar de Oferendas; à noite, oferenda + Adaga Ritual (2 corações)
 * invocam um Patrono (Lorde das Brasas / Senhora do Vazio / Rei Afogado) que exige um tributo.
 * Cumprir sela o pacto (XP, ritual_rank, glifos de Pacto, despertar Sombra/Fogo, dádiva); falhar o liberta.
 */
public class RitualWarlockModule implements Module {
    @Override
    public String id() {
        return "ritual_warlock";
    }

    @Override
    public void init(IEventBus modBus) {
        WarlockContent.register(modBus);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTabs);
        modBus.addListener(this::onPayloads);
        WarlockEvents.register();

        // Kit do Warlock: giz, adaga, altar e 8 velas.
        ClassKits.addItem(MagicPath.WARLOCK, () -> new ItemStack(WarlockContent.RITUAL_CHALK_ITEM.get()));
        ClassKits.addItem(MagicPath.WARLOCK, () -> new ItemStack(WarlockContent.RITUAL_DAGGER.get()));
        ClassKits.addItem(MagicPath.WARLOCK, () -> new ItemStack(WarlockContent.OFFERING_ALTAR_ITEM.get()));
        ClassKits.addItem(MagicPath.WARLOCK, () -> new ItemStack(Items.CANDLE, 8));

        // /mahou warlock clear | pacts <n>
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("warlock")
                .then(Commands.literal("clear").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PactState s = PactRitual.state(p);
                    s.clearPact();
                    s.sacrificeHearts = 0;
                    s.sacrificeUntil = 0;
                    s.lastRitualDay = -1;
                    s.lastRitualTime = -1;
                    PactBoons.refresh(p);
                    PactRitual.sendHud(p);
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.warlock.cleared"), false);
                    return 1;
                }))
                .then(Commands.literal("pacts").then(Commands.argument("n", IntegerArgumentType.integer(0, 99)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int n = IntegerArgumentType.getInteger(c, "n");
                    MagicApi.addCounter(p, PactRitual.PACTS_COUNTER, n - PactRitual.completedPacts(p));
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.warlock.pacts", n), false);
                    return 1;
                })))));
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WarlockContent.EMBER_LORD.get(), EmberLordEntity.createAttributes().build());
        event.put(WarlockContent.VOID_LADY.get(), VoidLadyEntity.createAttributes().build());
        event.put(WarlockContent.DROWNED_KING.get(), DrownedKingEntity.createAttributes().build());
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            WarlockContent.ritualTabItems().forEach(event::accept);
        } else if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            WarlockContent.creatureTabItems().forEach(event::accept);
        }
    }

    private void onPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("ritual_warlock-1");
        // Cliente: a classe de cliente só é tocada dentro dos lambdas (servidor dedicado nunca os executa).
        r.playToClient(WarlockPayloads.PatronDialog.TYPE, WarlockPayloads.PatronDialog.STREAM_CODEC,
                (p, c) -> WarlockClientHandlers.openDialog(p));
        r.playToClient(WarlockPayloads.PactHud.TYPE, WarlockPayloads.PactHud.STREAM_CODEC,
                (p, c) -> WarlockClientHandlers.hud(p));
        r.playToServer(WarlockPayloads.PatronAnswer.TYPE, WarlockPayloads.PatronAnswer.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) PactRitual.answer(sp, p.entityId(), p.action());
        });
    }
}
