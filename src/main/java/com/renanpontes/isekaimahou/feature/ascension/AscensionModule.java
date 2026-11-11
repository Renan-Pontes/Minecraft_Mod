package com.renanpontes.isekaimahou.feature.ascension;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionPedestalBlockEntity;
import com.renanpontes.isekaimahou.feature.ascension.entity.AscensionShadowEntity;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Modulo "ascension": Ritual de Ascensão (Círculos de Evolução, igual para todas as classes) e a Tribulação.
 *
 * Blocos: Núcleo de Ascensão (centro), Pedestal de Ascensão (guarda o tributo), Bloco de Cristal de Mana e
 * Pedra Rúnica. Tributos em {@code data/<ns>/ascension/circle_<n>.json}. A Tribulação usa raios arcanos,
 * ondas de mobs (criaturas do mod por id, com reserva vanilla) e, do Círculo-alvo 5 em diante, a Sombra do jogador.
 *
 * <p>v0.9 "Ascensão fácil": altares PÚBLICOS prontos ({@code PublicAltar:1b}, nas cidades/Academia/Santuários) que
 * servem a qualquer jogador sem montar nada e não quebram em sobrevivência; anel 9×9 sem Blocos de Cristal; receitas e
 * tributos mais baratos (itens de chefe da rota no lugar de diamantes/estrela); itens "Tributo do Círculo N"
 * ({@code ascension_tribute_2..10}, entregues pelas missões "A Ascensão" do diário) que valem o tributo inteiro; e a
 * linha "altar público mais próximo" no rastreador.
 *
 * <p>v1.0: a Ascensão acontece nos Altares Sagrados prontos (altares caseiros só com a regra
 * {@code isekaiPrivateAltars}); o círculo ficou 3 blocos maior e vira um <b>domo de runas</b> que prende quem ascende
 * (mobs, ajudantes e magias passam); ninguém constrói nem quebra na área dos altares públicos (sempre) nem dentro do
 * domo durante o ritual ({@link AscensionWard}); o raio arcano segue a inflação de números ({@code DAMAGE_SCALE}).
 */
public class AscensionModule implements Module {
    @Override
    public String id() {
        return "ascension";
    }

    @Override
    public void init(IEventBus modBus) {
        AscensionRegistry.register(modBus);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTabs);
        modBus.addListener(RegisterPayloadHandlersEvent.class, AscensionNetwork::register);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(AscensionRules::register));
        AscensionWard.register();

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new AscensionTributes()));
        // Tributos para o rastreador do cliente: ao entrar (e no /reload) e a cada Círculo novo.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> e.getRelevantPlayers().forEach(AscensionNetwork::sendTributes));
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.CircleChanged e) -> AscensionNetwork.sendTributes(e.getPlayer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Tribulation.tickAll();
            AscensionAltarFinder.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            Tribulation.clearAll();
            AscensionAltars.clearAll();
            AscensionAltarFinder.clearAll();
        });
        // Altares públicos: lista por nível (carrega junto com o nível) e proteção contra a destruição das magias.
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel sl) AscensionAltars.get(sl);
        });
        Destruction.addProtection((level, pos) -> AscensionAltars.covers(level.dimension(), pos)
                || Tribulation.zoneCovers(level.dimension(), pos));
        NeoForge.EVENT_BUS.addListener(this::onBreak);
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.CircleChanged e) -> {
            if (e.getPlayer() instanceof ServerPlayer sp) AscensionAltarFinder.refresh(sp);
        });
        // Chegou ao limite do Círculo: diz onde ascender sem construir nada (altares públicos) e já liga a seta.
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.LevelUp e) -> {
            ServerPlayer sp = e.getPlayer();
            MagicData d = MagicApi.get(sp);
            if (e.newLevel <= e.oldLevel || !d.atLevelCap() || d.circle() >= Progression.MAX_CIRCLE) return;
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.ascension.cap_hint")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" "))
                    .append(Component.translatable("message.isekai_mahou.ascension.cap_hint_link").withStyle(s -> s
                            .withColor(ChatFormatting.YELLOW).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mahou ascension altar"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("/mahou ascension altar"))))));
            AscensionAltarFinder.refresh(sp);
        });
        NeoForge.EVENT_BUS.addListener(this::onEntityJoin);
        // Sair do jogo no meio da Tribulação conta como abandonar o círculo (o tributo é perdido).
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            Tribulation t = Tribulation.byOwner(e.getEntity().getUUID());
            if (t != null) t.fail(Tribulation.Reason.OFFLINE);
            AscensionAltarFinder.forget(e.getEntity().getUUID());
        });
        // Morte do dono: falha na hora (o tick só veria o jogador já renascido, longe do círculo).
        // LOWEST e sem receber cancelados: um totem/efeito de outro módulo que evite a morte não conta.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            Tribulation t = Tribulation.byOwner(sp.getUUID());
            if (t != null) t.fail(Tribulation.Reason.DIED);
        });
        // Slimes que se dividem e mobs que se convertem continuam fazendo parte da onda (somem no fim do ritual).
        NeoForge.EVENT_BUS.addListener((MobSplitEvent e) -> e.getChildren().forEach(c -> Tribulation.adopt(e.getParent(), c)));
        NeoForge.EVENT_BUS.addListener((LivingConversionEvent.Post e) -> Tribulation.adopt(e.getEntity(), e.getOutcome()));
        // Mobs da Tribulação não destroem o terreno (bolas de fogo do Blaze, portas, etc.): o ritual costuma ser feito em casa.
        NeoForge.EVENT_BUS.addListener((EntityMobGriefingEvent e) -> {
            if (e.getEntity().getPersistentData().hasUUID(Tribulation.MOB_TAG)) e.setCanGrief(false);
        });
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        registerCommands();
    }

    /**
     * Agachado com um item, o vanilla pula o clique no bloco (usaria o item). No Pedestal, agachar serve para
     * colocar UM item por vez, então forçamos o uso do bloco quando o pedestal está vazio ou tem o mesmo item
     * (com outro item, agachar continua servindo para construir encostado nele). Roda nos dois lados.
     */
    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        ItemStack stack = e.getItemStack();
        if (stack.isEmpty() || !e.getEntity().isSecondaryUseActive()) return;
        if (!(e.getLevel().getBlockEntity(e.getPos()) instanceof AscensionPedestalBlockEntity ped)) return;
        ItemStack held = ped.getItem();
        if (held.isEmpty() || ItemStack.isSameItemSameComponents(held, stack)) e.setUseBlock(TriState.TRUE);
    }

    /**
     * Altar público: nem picareta encantada, nem martelo de outro mod — só no criativo. v1.0: vale para a ÁREA inteira
     * do altar público e para o domo de qualquer Tribulação em andamento, não só para as peças.
     */
    private void onBreak(BlockEvent.BreakEvent e) {
        Player player = e.getPlayer();
        if (player == null || player.getAbilities().instabuild) return;
        boolean piece = AscensionAltarGuard.isPublicPiece(e.getLevel(), e.getPos());
        if (!piece && !AscensionWard.denyBreak(player, e.getLevel(), e.getPos())) return;
        e.setCanceled(true);
        player.displayClientMessage(Component.translatable(piece ? "message.isekai_mahou.ascension.public_protected"
                : "message.isekai_mahou.ascension.area_protected").withStyle(ChatFormatting.GOLD), true);
    }

    private void onAttributes(EntityAttributeCreationEvent e) {
        e.put(AscensionRegistry.SHADOW.get(), AscensionShadowEntity.createAttributes().build());
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            e.accept(AscensionRegistry.CORE_ITEM.get());
            e.accept(AscensionRegistry.PEDESTAL_ITEM.get());
            e.accept(AscensionRegistry.RUNE_STONE_ITEM.get());
            e.accept(AscensionRegistry.MANA_CRYSTAL_BLOCK_ITEM.get());
            AscensionRegistry.TRIBUTES.values().forEach(t -> e.accept(t.get()));
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(AscensionRegistry.SHADOW_EGG.get());
        }
    }

    /** Mobs da Tribulação salvos no disco (servidor reiniciou no meio) não voltam ao mundo. */
    private void onEntityJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity entity = e.getEntity();
        var data = entity.getPersistentData();
        if (data.hasUUID(Tribulation.MOB_TAG) && !Tribulation.isActive(data.getUUID(Tribulation.MOB_TAG))) {
            e.setCanceled(true);
        }
    }

    /** Liga/desliga o altar público no Núcleo que o operador está olhando (até 8 blocos). */
    private static int setPublic(ServerPlayer p, boolean on) {
        HitResult hit = p.pick(8.0, 0F, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
                && p.level().getBlockEntity(bhr.getBlockPos()) instanceof AscensionCoreBlockEntity be) {
            be.setPublic(on);
            p.sendSystemMessage(Component.translatable(on ? "command.isekai_mahou.ascension.public_on"
                    : "command.isekai_mahou.ascension.public_off").withStyle(ChatFormatting.GOLD));
            return 1;
        }
        p.sendSystemMessage(Component.translatable("command.isekai_mahou.ascension.public_look").withStyle(ChatFormatting.RED));
        return 0;
    }

    /** /mahou ascension info | altar | public [off] | complete | abort */
    private void registerCommands() {
        MahouCommands.extend((root, ctx) -> root.then(Commands.literal("ascension")
                .then(Commands.literal("info").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MagicData d = MagicApi.get(p);
                    if (d.circle() >= Progression.MAX_CIRCLE) {
                        c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.ascension.max")
                                .withStyle(ChatFormatting.GOLD), false);
                        return 1;
                    }
                    int next = d.circle() + 1;
                    AscensionTributes.Check check = AscensionTributes.check(AscensionTributes.get(next), List.of(), d);
                    c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.ascension.header", d.circle(), next)
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
                    c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.ascension.info_layout",
                            AscensionPattern.radius(next) * 2 + 1, AscensionPattern.describeMissing(AscensionPattern.layout(next)))
                            .withStyle(ChatFormatting.GRAY), false);
                    c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.ascension.tribute")
                            .withStyle(ChatFormatting.GOLD), false);
                    for (AscensionTributes.Line line : check.lines()) {
                        c.getSource().sendSuccess(() -> Component.literal("  ").append(line.text()), false);
                    }
                    c.getSource().sendSuccess(() -> Component.translatable("message.isekai_mahou.ascension.level", d.level(), d.levelCap())
                            .withStyle(d.atLevelCap() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                    return 1;
                }))
                .then(Commands.literal("altar").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    AscensionAltarFinder.Hint hint = AscensionAltarFinder.lookup(p);
                    if (hint == null) {
                        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.ascension.altar_none")
                                .withStyle(ChatFormatting.GRAY), false);
                        return 0;
                    }
                    BlockPos at = hint.pos();
                    int dist = (int) Math.sqrt(at.distSqr(p.blockPosition()));
                    c.getSource().sendSuccess(() -> Component.translatable(hint.exact() ? "command.isekai_mahou.ascension.altar"
                            : "command.isekai_mahou.ascension.altar_approx", hint.name(), at.getX(), at.getY(), at.getZ(), dist)
                            .withStyle(ChatFormatting.GOLD), false);
                    return 1;
                }))
                .then(MahouCommands.op("public")
                        .executes(c -> setPublic(c.getSource().getPlayerOrException(), true))
                        .then(Commands.literal("off").executes(c -> setPublic(c.getSource().getPlayerOrException(), false))))
                .then(MahouCommands.op("complete").executes(c -> {
                    Tribulation t = Tribulation.byOwner(c.getSource().getPlayerOrException().getUUID());
                    if (t == null) {
                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.ascension.none"));
                        return 0;
                    }
                    t.succeed();
                    return 1;
                }))
                .then(MahouCommands.op("abort").executes(c -> {
                    Tribulation t = Tribulation.byOwner(c.getSource().getPlayerOrException().getUUID());
                    if (t == null) {
                        c.getSource().sendFailure(Component.translatable("command.isekai_mahou.ascension.none"));
                        return 0;
                    }
                    t.fail(Tribulation.Reason.ABORTED);
                    return 1;
                }))));
    }
}
