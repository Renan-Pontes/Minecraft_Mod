package com.renanpontes.isekaimahou.feature.structures_a;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Modulo "structures_a": Santuários Elementais (prova de Despertar).
 *
 * Seis estruturas geradas no mundo (jigsaw de 1 peça: worldgen em data/isekai_mahou/worldgen, .nbt gerados por
 * tools/gen/structures_a.py), cada uma com um Altar do Santuário. Clicar no altar começa a prova: 3 ondas de
 * criaturas do elemento; vencer desperta o elemento ({@link MagicApi#awakenElement}), soma o contador
 * {@code shrine.<elemento>} e dá Pergaminho de Glifo + essências. Também registra os tijolos elementais.
 */
public class StructuresAModule implements Module {
    @Override
    public String id() {
        return "structures_a";
    }

    @Override
    public void init(IEventBus modBus) {
        ShrineRegistry.register(modBus);
        modBus.addListener(this::onCreativeTabs);

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ShrineTrial.tickAll());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ShrineTrial.clearAll());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ShrineTrial.onLogout(e.getEntity().getUUID()));
        // LOWEST e sem receber cancelados: se outro módulo salvar o jogador da morte (ressurreição), ele segue na prova.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) ShrineTrial.onPlayerDeath(sp);
        });
        NeoForge.EVENT_BUS.addListener(MobSplitEvent.class, ShrineTrial::onSplit);
        NeoForge.EVENT_BUS.addListener(this::onEntityJoin);
        registerCommands();
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CoreRegistries.TAB_WORLD.getKey()) return;
        for (Element el : Element.BASE) e.accept(ShrineRegistry.altarStack(el));
        for (Element el : Element.BASE) e.accept(ShrineRegistry.BRICK_ITEMS.get(el).get());
    }

    /** Criaturas de uma prova salvas no disco (o servidor reiniciou no meio) não voltam ao mundo. */
    private void onEntityJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity entity = e.getEntity();
        var data = entity.getPersistentData();
        if (data.hasUUID(ShrineTrial.MOB_TAG) && !ShrineTrial.isActive(data.getUUID(ShrineTrial.MOB_TAG))) {
            e.setCanceled(true);
        }
    }

    /** /mahou shrine reset <elemento|all> | complete | abort (testes; precisam de cheats). */
    private void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("shrine")
                .then(MahouCommands.op("reset").then(Commands.argument("element", StringArgumentType.word())
                        .suggests((c, b) -> {
                            List<String> ids = new ArrayList<>();
                            for (Element el : Element.BASE) ids.add(el.id);
                            ids.add("all");
                            return SharedSuggestionProvider.suggest(ids, b);
                        })
                        .executes(StructuresAModule::reset)))
                .then(MahouCommands.op("complete").executes(c -> {
                    ShrineTrial t = ShrineTrial.of(c.getSource().getPlayerOrException().getUUID());
                    if (t == null) {
                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.shrine.none"));
                        return 0;
                    }
                    t.succeed();
                    return 1;
                }))
                .then(MahouCommands.op("abort").executes(c -> {
                    ShrineTrial t = ShrineTrial.of(c.getSource().getPlayerOrException().getUUID());
                    if (t == null) {
                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.shrine.none"));
                        return 0;
                    }
                    t.fail(ShrineTrial.Reason.ABORTED);
                    return 1;
                }))));
    }

    /** Permite refazer a prova (o elemento continua desperto; só a marca de "prova concluída" sai). */
    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String arg = StringArgumentType.getString(c, "element");
        MagicData d = MagicApi.get(p);
        List<Element> targets = new ArrayList<>();
        if (arg.equals("all")) {
            targets.addAll(Element.BASE);
        } else {
            Element el = Element.byId(arg);
            if (el == null || !el.isBase()) {
                c.getSource().sendFailure(Component.translatable("command.isekai_mahou.shrine.unknown", arg));
                return 0;
            }
            targets.add(el);
        }
        for (Element el : targets) d.setFlag(ShrineKind.of(el).progressKey(), false);
        d.markDirty();
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.shrine.reset", arg).withStyle(ChatFormatting.GREEN), false);
        return targets.size();
    }
}
