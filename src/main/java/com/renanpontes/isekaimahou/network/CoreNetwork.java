package com.renanpontes.isekaimahou.network;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellCaster;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registro dos pacotes do núcleo. */
public final class CoreNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        // core-3 (v1.0): 9 loadouts, presets, slots sem elemento
        PayloadRegistrar r = event.registrar("core-3");

        // ---------------- Servidor -> cliente ----------------
        r.playToClient(CorePayloads.MagicSync.TYPE, CorePayloads.MagicSync.STREAM_CODEC, (p, c) -> ClientMagic.applySync(p));
        r.playToClient(CorePayloads.ManaSync.TYPE, CorePayloads.ManaSync.STREAM_CODEC, (p, c) -> ClientMagic.applyMana(p));
        r.playToClient(CorePayloads.Cooldown.TYPE, CorePayloads.Cooldown.STREAM_CODEC, (p, c) -> ClientMagic.applyCooldown(p));
        r.playToClient(CorePayloads.CastState.TYPE, CorePayloads.CastState.STREAM_CODEC, (p, c) -> {
            ClientMagic.applyCastState(p);
            ClientHooks.castState.accept(p);
        });
        r.playToClient(CorePayloads.AnnounceText.TYPE, CorePayloads.AnnounceText.STREAM_CODEC, (p, c) -> ClientHooks.announce.accept(p));
        r.playToClient(CorePayloads.Chant.TYPE, CorePayloads.Chant.STREAM_CODEC, (p, c) -> ClientHooks.chant.accept(p));
        r.playToClient(CorePayloads.Cutscene.TYPE, CorePayloads.Cutscene.STREAM_CODEC, (p, c) -> ClientHooks.cutscene.accept(p));
        r.playToClient(CorePayloads.OpenPathScreen.TYPE, CorePayloads.OpenPathScreen.STREAM_CODEC,
                (p, c) -> ClientMagic.pendingPathScreen = true);
        r.playToClient(CorePayloads.RewardToast.TYPE, CorePayloads.RewardToast.STREAM_CODEC, (p, c) -> ClientHooks.toast.accept(p));

        // ---------------- Cliente -> servidor ----------------
        r.playToServer(CorePayloads.CastKey.TYPE, CorePayloads.CastKey.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> SpellCaster.onKey(sp, p.slot(), p.pressed())));
        // v0.8 (Roda Elemental): aceitos e ignorados, para módulos antigos não derrubarem a conexão
        r.playToServer(CorePayloads.SelectWheel.TYPE, CorePayloads.SelectWheel.STREAM_CODEC, (p, c) -> {});
        r.playToServer(CorePayloads.WheelEdit.TYPE, CorePayloads.WheelEdit.STREAM_CODEC, (p, c) -> {});
        r.playToServer(CorePayloads.SelectLoadout.TYPE, CorePayloads.SelectLoadout.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> MagicApi.selectLoadout(sp, p.index())));
        r.playToServer(CorePayloads.SetSlot.TYPE, CorePayloads.SetSlot.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> MagicApi.setSlot(sp, p.loadout(), p.slot(), MoveTrigger.byIndex(p.trigger()),
                        p.recipe().orElse(null))));
        r.playToServer(CorePayloads.SetLoadoutSlot.TYPE, CorePayloads.SetLoadoutSlot.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> MagicApi.setSlot(sp, p.loadout(), p.slot(), MoveTrigger.byIndex(p.trigger()),
                        p.recipe().orElse(null))));
        r.playToServer(CorePayloads.PresetEdit.TYPE, CorePayloads.PresetEdit.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> presetEdit(sp, p)));
        r.playToServer(CorePayloads.ChoosePath.TYPE, CorePayloads.ChoosePath.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> CoreGameplay.choosePath(sp, MagicPath.byIndex(p.path()))));
        r.playToServer(CorePayloads.LibraryEdit.TYPE, CorePayloads.LibraryEdit.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> CoreGameplay.libraryEdit(sp, p.op(), p.index(), p.recipe())));
        r.playToServer(CorePayloads.RequestTrial.TYPE, CorePayloads.RequestTrial.STREAM_CODEC,
                (p, c) -> withPlayer(c, sp -> TrialsApi.request(sp, p.element(), p.rank())));
    }

    private static void presetEdit(ServerPlayer sp, CorePayloads.PresetEdit p) {
        switch (p.op()) {
            case CorePayloads.PresetEdit.SAVE -> MagicApi.savePreset(sp, p.name(), p.recipe());
            case CorePayloads.PresetEdit.DELETE -> MagicApi.deletePreset(sp, p.index());
            case CorePayloads.PresetEdit.LOAD -> MagicApi.loadPreset(sp, p.index(), p.loadout(), p.slot(), MoveTrigger.byIndex(p.trigger()));
            case CorePayloads.PresetEdit.RENAME -> MagicApi.renamePreset(sp, p.index(), p.name());
            default -> { }
        }
    }

    private static void withPlayer(IPayloadContext ctx, Consumer<ServerPlayer> action) {
        if (ctx.player() instanceof ServerPlayer sp) action.accept(sp);
    }

    private CoreNetwork() {}
}
