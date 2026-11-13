package com.renanpontes.isekaimahou.core.anim;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

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
 *   <li>v1.0 — Bloqueio com aparo: {@link GuardService} (tecla segurada com arma/cajado; aparo no tempo certo).</li>
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
    /** v1.0: guarda com arma/cajado (pose mantida enquanto bloqueia) e o gesto curto do aparo — ver {@link GuardService}. */
    public static final String GUARD_WEAPON = "guard_weapon", PARRY = "parry";

    // ---- v1.3: transformações, Hollow/Arrancar e técnicas (outros módulos chamam por texto: play(e, "cero_charge"))
    public static final String TRANSFORM_ROAR = "transform_roar";
    public static final String TRANSFORM_RISE = "transform_rise";
    public static final String TRANSFORM_BURST = "transform_burst";
    public static final String HOLLOW_MASK_TEAR = "hollow_mask_tear";
    /** Pose mantida (até 5 s) — solte com {@link #stopHold} ou toque {@link #CERO_FIRE}. */
    public static final String CERO_CHARGE = "cero_charge";
    public static final String CERO_FIRE = "cero_fire";
    public static final String SONIDO_STEP = "sonido_step";
    public static final String RESURRECCION = "resurreccion";
    public static final String BOW_CHARGE_BIG = "bow_charge_big";
    public static final String THROW_OVERHAND = "throw_overhand";
    public static final String SPIN_SLASH = "spin_slash";
    public static final String GROUND_SLAM = "ground_slam";
    /** Ajoelha ~3 s com as mãos juntas e levanta sozinho (não precisa parar). */
    public static final String KNEEL_CHANNEL = "kneel_channel";
    public static final String TAUNT = "taunt";
    /** Espada erguida com as duas mãos e corte vertical (Excalibur, Balmung, Caliburn). */
    public static final String HOLY_CLEAVE = "holy_cleave";
    /** Palma aberta à frente, firme (escudos: Rho Aias, Égide). */
    public static final String PALM_WARD = "palm_ward";
    // ---- v1.3: sensação de movimento (servidor detecta golpe pesado, empurrão, queda e chefe morto)
    public static final String FLINCH_F = "flinch_f", FLINCH_B = "flinch_b";
    public static final String KNOCKDOWN = "knockdown";
    public static final String LAND_ROLL = "land_roll", LAND_HERO = "land_hero";
    public static final String SPRINT_LUNGE = "sprint_lunge";
    public static final String DRAW_BLADE = "draw_blade", DRAW_HEAVY = "draw_heavy";
    public static final String VICTORY = "victory";
    // ---- v1.3: emotes (roda "Emotes")
    public static final String EMOTE_WAVE = "emote_wave", EMOTE_BOW = "emote_bow", EMOTE_SIT = "emote_sit",
            EMOTE_CHEER = "emote_cheer", EMOTE_POINT = "emote_point", EMOTE_CROSS = "emote_cross_arms", EMOTE_DANCE = "emote_dance";
    /** Ordem da roda: acenar, reverência, provocar, sentar, comemorar, apontar, cruzar braços, dançar. */
    public static final List<String> EMOTES = List.of(EMOTE_WAVE, EMOTE_BOW, TAUNT, EMOTE_SIT, EMOTE_CHEER, EMOTE_POINT,
            EMOTE_CROSS, EMOTE_DANCE);

    public static final byte DIR_FORWARD = 0, DIR_BACK = 1, DIR_LEFT = 2, DIR_RIGHT = 3;

    /** Golpes que o cliente pode pedir para repassar (o resto é recusado). */
    public static final Set<String> MELEE = Set.of(SWORD_1, SWORD_2, SWORD_3, HEAVY_1, HEAVY_2, DAGGER_1, DAGGER_2,
            SPEAR_1, SPEAR_2, FIST_1, FIST_2, FIST_3);
    /** Tudo o que o cliente toca sozinho e pede para o servidor repassar: combos, investida, saque da arma e emotes. */
    public static final Set<String> RELAY;
    static {
        Set<String> r = new HashSet<>(MELEE);
        r.add(SPRINT_LUNGE);
        r.add(DRAW_BLADE);
        r.add(DRAW_HEAVY);
        r.addAll(EMOTES);
        RELAY = Set.copyOf(r);
    }

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
        GuardService.init(bus);
        FeelService.init(bus);
        bus.addListener(ServerStoppedEvent.class, e -> {
            LAST_MELEE.clear();
            HOLD.clear();
            DodgeService.clear();
            GuardService.clear();
            FeelService.clear();
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("anim-1");
        r.playToClient(AnimPayloads.Play.TYPE, AnimPayloads.Play.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> com.renanpontes.isekaimahou.client.anim.MahouAnimsClient.receive(p)));
        r.playToClient(AnimPayloads.Rules.TYPE, AnimPayloads.Rules.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> com.renanpontes.isekaimahou.client.anim.MahouAnimsClient.rules(p)));
        r.playToServer(AnimPayloads.Melee.TYPE, AnimPayloads.Melee.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) relay(sp, p.anim());
        }));
        r.playToServer(AnimPayloads.Dodge.TYPE, AnimPayloads.Dodge.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) DodgeService.dodge(sp, p.dir());
        }));
        r.playToServer(AnimPayloads.Guard.TYPE, AnimPayloads.Guard.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) GuardService.request(sp, p.down());
        }));
        r.playToClient(AnimPayloads.GuardFx.TYPE, AnimPayloads.GuardFx.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> com.renanpontes.isekaimahou.client.anim.MahouAnimsClient.guardFx(p)));
    }

    // =====================================================================================
    //  API
    // =====================================================================================

    /**
     * Toca a animação {@code anim} na entidade para quem a vê (e para ela mesma, se for jogador). Id desconhecido, vazio
     * ou nulo não faz nada (o cliente ignora o que não conhece; ids longos demais nem saem do servidor).
     */
    public static void play(@Nullable LivingEntity entity, @Nullable String anim) {
        if (entity == null || !valid(anim)) return;
        send(entity, new AnimPayloads.Play(entity.getId(), anim, false), true);
    }

    /** Igual a {@link #play(LivingEntity, String)}, mas só para os OUTROS (o próprio jogador já tocou localmente). */
    public static void playOthers(@Nullable LivingEntity entity, @Nullable String anim) {
        if (entity == null || !valid(anim)) return;
        send(entity, new AnimPayloads.Play(entity.getId(), anim, false), false);
    }

    /** Toca só para o próprio jogador (ex.: magia silenciosa). */
    public static void playSelf(@Nullable ServerPlayer player, @Nullable String anim) {
        if (player == null || !valid(anim)) return;
        PacketDistributor.sendToPlayer(player, new AnimPayloads.Play(player.getId(), anim, false));
    }

    /** Id que cabe no pacote (o codec recusaria — e derrubaria a conexão — um texto maior que {@link AnimPayloads#MAX_ID}). */
    private static boolean valid(@Nullable String anim) {
        return anim != null && !anim.isEmpty() && anim.length() <= AnimPayloads.MAX_ID;
    }

    /** Solta a pose contínua (canalização/carga) da entidade, se for isso que estiver tocando. */
    public static void stopHold(@Nullable LivingEntity entity) {
        if (entity == null) return;
        send(entity, new AnimPayloads.Play(entity.getId(), "", true), true);
    }

    /**
     * Para os OUTROS: solta a animação {@code anim} da entidade só se for ela que estiver tocando (o próprio jogador já
     * soltou localmente). Diferente de {@link #stopHold}, não corta outra pose contínua que tenha começado depois.
     */
    public static void stopOthers(@Nullable LivingEntity entity, @Nullable String anim) {
        if (entity == null || !valid(anim)) return;
        send(entity, new AnimPayloads.Play(entity.getId(), anim, true), false);
    }

    private static void send(LivingEntity entity, AnimPayloads.Play payload, boolean self) {
        if (entity.level().isClientSide) return;
        if (self && entity instanceof ServerPlayer sp) PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, payload);
        else PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    private static void relay(ServerPlayer player, String anim) {
        if (!RELAY.contains(anim) || player.isSpectator() || player.isDeadOrDying()) return;
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
        FeelService.tick(player);
        GuardService.tick(player);
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
        GuardService.forget(id);
        FeelService.forget(id);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        HOLD.remove(event.getEntity().getUUID());
        GuardService.forget(event.getEntity().getUUID());
        FeelService.forget(event.getEntity().getUUID());
    }

    /** O jogador está segurando uma conjuração (pose de carga/raio) — o tranco de golpe não corta a pose. */
    static boolean isHolding(ServerPlayer player) {
        return HOLD.containsKey(player.getUUID());
    }

    private MahouAnims() {}
}
