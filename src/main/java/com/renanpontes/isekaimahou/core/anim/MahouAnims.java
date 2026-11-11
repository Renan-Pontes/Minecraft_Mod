package com.renanpontes.isekaimahou.core.anim;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Animações de combate (estilo Epic Fight) — lado comum/servidor.
 * <p>
 * API para qualquer módulo: {@link #play(LivingEntity, String)} manda a animação para quem vê a entidade (e para ela,
 * se for jogador). O cliente ({@code client/anim/MahouAnimsClient}) toca a animação numa camada própria do
 * Player Animator. Ids das animações: as constantes abaixo (o cliente constrói cada uma em código).
 * <ul>
 *   <li>Combos corpo a corpo: o cliente toca na hora e avisa o servidor ({@link AnimPayloads.Melee}), que repassa.</li>
 *   <li>Gestos de magia: escolhidos aqui pela FORMA da magia ({@link SpellGestures}) ao conjurar e ao canalizar.</li>
 *   <li>Esquiva: {@link DodgeService} (validação, invulnerabilidade, cansaço).</li>
 * </ul>
 */
public final class MahouAnims {
    // ---- corpo a corpo (combos) ----
    public static final String SWORD_1 = "sword_1", SWORD_2 = "sword_2", SWORD_3 = "sword_3";
    public static final String HEAVY_1 = "heavy_1", HEAVY_2 = "heavy_2";
    public static final String DAGGER_1 = "dagger_1", DAGGER_2 = "dagger_2";
    public static final String SPEAR_1 = "spear_1", SPEAR_2 = "spear_2";
    public static final String FIST_1 = "fist_1", FIST_2 = "fist_2", FIST_3 = "fist_3";
    // ---- magia ----
    public static final String CAST_BOLT = "cast_bolt";
    public static final String CAST_BEAM = "cast_beam";
    public static final String CAST_BEAM_HOLD = "cast_beam_hold";
    public static final String CAST_AREA = "cast_area";
    public static final String CAST_AREA_BIG = "cast_area_big";
    public static final String CAST_AURA = "cast_aura";
    public static final String CAST_SUMMON = "cast_summon";
    public static final String CAST_BOW = "cast_bow";
    public static final String CAST_SLASH = "cast_slash";
    public static final String CAST_DASH = "cast_dash";
    public static final String CAST_GATHER = "cast_gather";
    public static final String CAST_ULT_CHARGE = "cast_ult_charge";
    public static final String CAST_ULT_RELEASE = "cast_ult_release";
    // ---- esquiva / guarda ----
    public static final String DODGE_F = "dodge_f", DODGE_B = "dodge_b", DODGE_L = "dodge_l", DODGE_R = "dodge_r";
    public static final String GUARD_R = "guard_r", GUARD_L = "guard_l";

    public static final byte DIR_FORWARD = 0, DIR_BACK = 1, DIR_LEFT = 2, DIR_RIGHT = 3;

    /** Golpes que o cliente pode pedir para repassar (o resto é recusado). */
    public static final Set<String> MELEE = Set.of(SWORD_1, SWORD_2, SWORD_3, HEAVY_1, HEAVY_2, DAGGER_1, DAGGER_2,
            SPEAR_1, SPEAR_2, FIST_1, FIST_2, FIST_3);

    /** Último golpe repassado por jogador (limite de pacotes). */
    private static final Map<UUID, Long> LAST_MELEE = new ConcurrentHashMap<>();
    /** Pose contínua (canalização/carga) que cada jogador está fazendo agora, para detectar início/fim. */
    private static final Map<UUID, String> HOLD = new ConcurrentHashMap<>();

    /** Chamado no construtor do mod. */
    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AnimConfig.COMMON_SPEC, "isekai_mahou-combat-common.toml");
        modBus.addListener(MahouAnims::registerPayloads);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(SpellCastEvent.Post.class, MahouAnims::onCastPost);
        bus.addListener(PlayerTickEvent.Post.class, MahouAnims::onPlayerTick);
        bus.addListener(PlayerEvent.StartTracking.class, MahouAnims::onStartTracking);
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, MahouAnims::onLogin);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, MahouAnims::onLogout);
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, MahouAnims::onRespawn);
        // Alta prioridade: o golpe esquivado some antes de aparar/escudos/efeitos de outros módulos reagirem
        bus.addListener(EventPriority.HIGH, false, LivingIncomingDamageEvent.class, DodgeService::onIncomingDamage);
        bus.addListener(ServerStoppedEvent.class, e -> {
            LAST_MELEE.clear();
            HOLD.clear();
            DodgeService.clear();
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("anim-1");
        r.playToClient(AnimPayloads.Play.TYPE, AnimPayloads.Play.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> com.renanpontes.isekaimahou.client.anim.MahouAnimsClient.receive(p)));
        r.playToClient(AnimPayloads.Rules.TYPE, AnimPayloads.Rules.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> com.renanpontes.isekaimahou.client.anim.MahouAnimsClient.rules(p)));
        r.playToServer(AnimPayloads.Melee.TYPE, AnimPayloads.Melee.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) relayMelee(sp, p.anim());
        }));
        r.playToServer(AnimPayloads.Dodge.TYPE, AnimPayloads.Dodge.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) DodgeService.dodge(sp, p.dir());
        }));
    }

    // =====================================================================================
    //  API
    // =====================================================================================

    /** Toca a animação {@code anim} na entidade para quem a vê (e para ela mesma, se for jogador). */
    public static void play(LivingEntity entity, String anim) {
        send(entity, new AnimPayloads.Play(entity.getId(), anim, false), true);
    }

    /** Igual a {@link #play(LivingEntity, String)}, mas só para os OUTROS (o próprio jogador já tocou localmente). */
    public static void playOthers(LivingEntity entity, String anim) {
        send(entity, new AnimPayloads.Play(entity.getId(), anim, false), false);
    }

    /** Toca só para o próprio jogador (ex.: magia silenciosa). */
    public static void playSelf(ServerPlayer player, String anim) {
        PacketDistributor.sendToPlayer(player, new AnimPayloads.Play(player.getId(), anim, false));
    }

    /** Solta a pose contínua (canalização/carga) da entidade, se for isso que estiver tocando. */
    public static void stopHold(LivingEntity entity) {
        send(entity, new AnimPayloads.Play(entity.getId(), "", true), true);
    }

    private static void send(LivingEntity entity, AnimPayloads.Play payload, boolean self) {
        if (entity.level().isClientSide) return;
        if (self && entity instanceof ServerPlayer sp) PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, payload);
        else PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    private static void relayMelee(ServerPlayer player, String anim) {
        if (!MELEE.contains(anim) || player.isSpectator()) return;
        long now = player.serverLevel().getGameTime();
        Long last = LAST_MELEE.get(player.getUUID());
        if (last != null && now - last < 2) return;
        LAST_MELEE.put(player.getUUID(), now);
        playOthers(player, anim);
    }

    // =====================================================================================
    //  Magia
    // =====================================================================================
    private static void onCastPost(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer player)) return;
        SpellContext ctx = event.getContext();
        if (ctx == null || ctx.spell() == null || !ctx.isPlayerCast()) return;
        MagicData data = MagicApi.get(player);
        // Canalização sustentada: cada pulso dispara Post — os braços continuam na pose de raio
        if (data.channelSustain && data.channelSlot >= 0) return;
        String gesture = SpellGestures.release(ctx.spell());
        if (gesture == null) return;
        if (ctx.spell().formStats() != null && ctx.spell().formStats().silent) playSelf(player, gesture);
        else play(player, gesture);
    }

    /** Detecta início/fim da conjuração segurando a tecla (pose de carga/raio mantida). */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MagicData data = MagicApi.get(player);
        boolean channeling = data.channelSlot >= 0 && data.channelRecipe != null && !player.isSpectator();
        UUID id = player.getUUID();
        String current = HOLD.get(id);
        if (channeling && current == null) {
            String hold = holdFor(data);
            HOLD.put(id, hold);
            play(player, hold);
        } else if (!channeling && current != null) {
            HOLD.remove(id);
            stopHold(player);
        }
    }

    private static String holdFor(MagicData data) {
        try {
            SpellParser.Result r = SpellParser.parse(data.channelRecipe, data.channelElement, null);
            if (r.ok()) return SpellGestures.hold(r.spell(), data.channelSustain);
        } catch (RuntimeException ignored) {
            // receita estranha: usa a pose genérica
        }
        return data.channelSustain ? CAST_BEAM_HOLD : CAST_GATHER;
    }

    /** Quem passa a ver um jogador que já está canalizando também vê a pose. */
    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            String hold = HOLD.get(target.getUUID());
            if (hold != null) PacketDistributor.sendToPlayer(viewer, new AnimPayloads.Play(target.getId(), hold, false));
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new AnimPayloads.Rules(AnimConfig.DODGE_ENABLED.get(), AnimConfig.DODGE_COOLDOWN.get()));
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_MELEE.remove(id);
        HOLD.remove(id);
        DodgeService.forget(id);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        HOLD.remove(event.getEntity().getUUID());
    }

    private MahouAnims() {}
}
