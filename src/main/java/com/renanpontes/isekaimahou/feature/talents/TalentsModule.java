package com.renanpontes.isekaimahou.feature.talents;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Módulo "talents" — v1.0: <b>Árvore de Talentos</b> e <b>Especializações</b> (subclasses).
 * <ul>
 *   <li>{@link TalentTrees}/{@link TalentTree}: uma árvore por classe em {@code data/isekai_mahou/talents/<classe>.json}
 *       (release 1.0: 3 ramos × 7 fileiras — nós pequenos, NOTÁVEIS, escolhas "um ou outro" e um nó FINAL por ramo —,
 *       uma Maestria repetível por ramo para os pontos que sobram, e 2 especializações com assinatura e fileira
 *       exclusiva). Gerado por {@code tools/gen/talents.py} (que também imprime a tabela de orçamento);</li>
 *   <li>{@link TalentData}: attachment próprio {@code isekai_mahou:talents} (por classe; pontos vêm do nível);</li>
 *   <li>{@link Talents}: API (gastar, escolher especialização, {@code reset}, passivas) — o núcleo a expõe aos outros
 *       módulos por {@code core.classes.ClassTalents} (ligação por nome: não mude as assinaturas públicas usadas lá);</li>
 *   <li>{@link TalentCombat}: as passivas que não são atributo (crítico, roubo de vida, esquiva...);</li>
 *   <li>cliente: aba "Talentos" (menu B → Personagem) com a árvore e a escolha da especialização no Círculo 5.</li>
 * </ul>
 * Comandos de teste: {@code /mahou talents ...} ({@link TalentsCommands}).
 */
public class TalentsModule implements Module {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    public static final Supplier<AttachmentType<TalentData>> DATA = ATTACHMENTS.register("talents",
            () -> AttachmentType.serializable(TalentData::new).copyOnDeath().build());

    @Override
    public String id() {
        return "talents";
    }

    @Override
    public void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        TalentsNetwork.register(modBus);
        TalentsCommands.register();
        IEventBus bus = NeoForge.EVENT_BUS;
        TalentCombat.init(bus);
        bus.addListener((AddReloadListenerEvent e) -> e.addListener(new TalentTrees()));

        bus.addListener((PlayerTickEvent.Post e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
            if (sp.tickCount % 20 == 3) Talents.tick(sp);
            if (sp.tickCount % 100 == 7) TalentCombat.slowTick(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> TalentCombat.forget(e.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent e) -> TalentCombat.clear());
        bus.addListener(TalentsModule::onLevelUp);
        bus.addListener(TalentsModule::onCircle);
    }

    /** Ponto novo: uma linha discreta acima da barra de itens (sempre há onde gastar: no mínimo a Maestria). */
    private static void onLevelUp(MagicProgressEvent.LevelUp e) {
        ServerPlayer sp = e.getPlayer();
        MagicData md = MagicApi.get(sp);
        if (md.path() == MagicPath.NONE || TalentTrees.get(md.path().id) == null) return;
        if (Talents.earned(e.newLevel, 0) <= Talents.earned(e.oldLevel, 0)) return;
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.talents.point", Talents.unspent(sp)).withColor(0xFFD86B), true);
    }

    /** Chegou ao Círculo da especialização sem ter escolhido: avisa uma vez, no chat. */
    private static void onCircle(MagicProgressEvent.CircleChanged e) {
        if (e.oldCircle >= Talents.SUBCLASS_CIRCLE || e.newCircle < Talents.SUBCLASS_CIRCLE) return;
        ServerPlayer sp = e.getPlayer();
        MagicData md = MagicApi.get(sp);
        TalentTree tree = md.path() == MagicPath.NONE ? null : TalentTrees.get(md.path().id);
        if (tree == null || tree.subclasses().isEmpty() || !Talents.subclass(sp).isEmpty()) return;
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.talents.subclass_open").withColor(0xFFD86B));
    }
}
