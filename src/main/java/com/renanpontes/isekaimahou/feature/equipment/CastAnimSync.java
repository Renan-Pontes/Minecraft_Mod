package com.renanpontes.isekaimahou.feature.equipment;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.equipment.network.CastAnimPayload;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lado servidor das animações de conjuração: avisa quem está vendo um jogador quando ele
 * lança uma magia (mão estendida / pose suprema / em si mesmo) e quando começa/para de canalizar.
 * O núcleo só avisa o PRÓPRIO jogador (CastState); os outros dependem deste pacote.
 */
public final class CastAnimSync {
    /** Formas centradas no conjurador: gesto de "abrir os braços". */
    private static final Set<String> SELF_FORMS = Set.of("self", "aura", "orbit", "nova", "pulse");
    /** Formas de Domínio/cataclismo: pose dramática mesmo sem ser a tecla G. */
    private static final Set<String> GRAND_FORMS = Set.of("domain_expansion", "simple_domain", "cataclysm");

    /** Jogadores que estão canalizando agora (para detectar início/fim). */
    private static final Set<UUID> CHANNELING = ConcurrentHashMap.newKeySet();

    public static void onPost(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer player)) return;
        SpellContext ctx = event.getContext();
        if (ctx == null || ctx.spell() == null) return;
        MagicData data = MagicApi.get(player);
        // Canalização sustentada (aumento Canalizar): cada pulso dispara Post; os braços continuam erguidos
        if (data.channelSustain && data.channelSlot >= 0) return;
        CastAnimPayload payload = new CastAnimPayload(player.getId(), kindOf(ctx.spell()), ctx.color() & 0xFFFFFF);
        // Aumento Silencioso: o gesto não denuncia o conjurador (só ele mesmo vê a própria animação)
        if (ctx.spell().formStats() != null && ctx.spell().formStats().silent) {
            PacketDistributor.sendToPlayer(player, payload);
        } else {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        }
    }

    static byte kindOf(ParsedSpell spell) {
        // isUltimate() lê a forma: checa null antes
        if (spell.form() == null) return CastAnimPayload.QUICK;
        if (spell.isUltimate()) return CastAnimPayload.ULTIMATE;
        ResourceLocation id = spell.form().id();
        if (!IsekaiMahou.MODID.equals(id.getNamespace())) return CastAnimPayload.QUICK;
        if (GRAND_FORMS.contains(id.getPath())) return CastAnimPayload.ULTIMATE;
        if (SELF_FORMS.contains(id.getPath())) return CastAnimPayload.SELF;
        return CastAnimPayload.QUICK;
    }

    /**
     * Detecta início/fim da conjuração segurando a tecla e avisa quem está vendo o jogador.
     * O próprio jogador também recebe: o gancho ClientHooks.castState já reage na hora, mas se algum módulo
     * substituir o gancho sem encadear, este pacote mantém os braços certos (e traz a cor da magia).
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MagicData data = MagicApi.get(player);
        boolean channeling = data.channelSlot >= 0 && data.channelRecipe != null && !player.isSpectator();
        UUID id = player.getUUID();
        if (channeling == CHANNELING.contains(id)) return;
        if (channeling) CHANNELING.add(id);
        else CHANNELING.remove(id);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new CastAnimPayload(player.getId(),
                channeling ? CastAnimPayload.CHANNEL_START : CastAnimPayload.CHANNEL_STOP, channeling ? channelColor(data) : 0));
    }

    /** Quem passa a ver um jogador que já está canalizando também vê os braços erguidos. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && CHANNELING.contains(target.getUUID())) {
            PacketDistributor.sendToPlayer(viewer, new CastAnimPayload(target.getId(), CastAnimPayload.CHANNEL_START,
                    channelColor(MagicApi.get(target))));
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHANNELING.remove(event.getEntity().getUUID());
    }

    /** Ao renascer o jogador é uma entidade nova (sem animação no cliente): recomeça a detecção do zero. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        CHANNELING.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CHANNELING.clear();
    }

    private static int channelColor(MagicData data) {
        if (data.channelRecipe != null && data.channelRecipe.color() >= 0) return data.channelRecipe.color() & 0xFFFFFF;
        return data.channelElement != null ? data.channelElement.color : 0xB98CFF;
    }

    private CastAnimSync() {}
}
