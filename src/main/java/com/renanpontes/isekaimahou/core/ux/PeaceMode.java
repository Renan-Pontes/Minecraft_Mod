package com.renanpontes.isekaimahou.core.ux;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * v1.6 — <b>Modo Paz</b>: trava magias, esquiva e golpes em jogadores e NPCs para não conjurar sem querer na cidade ou em casa.
 * <ul>
 *   <li>Liga sozinho ao ENTRAR numa zona segura (cidade/vila do {@link Places} com zona segura, ou perto da sua cama).</li>
 *   <li>Desliga sozinho ao SAIR da zona segura ou ao tomar um golpe de alguém.</li>
 *   <li>Tecla (padrão N) liga/desliga a qualquer hora — desligar na zona segura vale até você sair dela.</li>
 * </ul>
 * O servidor decide; o cliente só recebe o estado (HUD + não tenta esquivar).
 */
public final class PeaceMode {
    /** Raio (blocos) em volta do ponto de renascimento (cama/âncora) que conta como "casa". */
    private static final int HOME_RADIUS = 20;
    private static final int CHECK_EVERY = 10;

    private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
    /** Último "está na zona segura?" de cada jogador (para detectar entrar/sair). */
    private static final Map<UUID, Boolean> IN_SAFE = new ConcurrentHashMap<>();

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    /** Cliente → servidor: apertou a tecla do Modo Paz. */
    public record Toggle() implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = new Type<>(IsekaiMahou.id("core_peace_toggle"));
        public static final StreamCodec<FriendlyByteBuf, Toggle> STREAM_CODEC = StreamCodec.unit(new Toggle());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Servidor → cliente: estado atual. */
    public record State(boolean on) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("core_peace_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = ByteBufCodecs.BOOL.map(State::new, State::on).cast();
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(PeaceMode::registerPayloads);
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(PlayerTickEvent.Post.class, PeaceMode::onTick);
        // Golpe corpo a corpo em jogador/bicho de jogador: cancelado antes de qualquer outro efeito
        bus.addListener(EventPriority.HIGHEST, AttackEntityEvent.class, PeaceMode::onAttack);
        // Baixa prioridade: só conta o golpe que de fato vai acontecer (esquiva/escudos já cancelaram os outros)
        bus.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, PeaceMode::onDamage);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> forget(e.getEntity().getUUID()));
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> IN_SAFE.remove(e.getEntity().getUUID()));
        bus.addListener(ServerStoppedEvent.class, e -> {
            ON.clear();
            IN_SAFE.clear();
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("peace-1").optional();
        r.playToServer(Toggle.TYPE, Toggle.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) set(sp, !isOn(sp), true);
        }));
        r.playToClient(State.TYPE, State.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> com.renanpontes.isekaimahou.client.ux.PeaceClient.apply(p.on())));
    }

    // =====================================================================================
    //  API
    // =====================================================================================
    public static boolean isOn(Player p) {
        return p != null && ON.contains(p.getUUID());
    }

    /** Liga/desliga e avisa o jogador (barra de ação + som). */
    public static void set(ServerPlayer sp, boolean on, boolean byKey) {
        boolean was = ON.contains(sp.getUUID());
        if (was == on) return;
        if (on) ON.add(sp.getUUID());
        else ON.remove(sp.getUUID());
        PacketDistributor.sendToPlayer(sp, new State(on));
        sp.displayClientMessage(Component.translatable(on ? "message.isekai_mahou.peace.on" : "message.isekai_mahou.peace.off"), true);
        sp.serverLevel().playSound(null, sp.blockPosition(), on ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS, 0.6F, on ? 1.4F : 0.9F);
    }

    /** Mensagem padrão "você está no Modo Paz" (usada pela conjuração). */
    public static void denied(ServerPlayer sp) {
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.peace.blocked"), true);
    }

    // =====================================================================================
    //  Regras automáticas
    // =====================================================================================
    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % CHECK_EVERY != 0) return;
        boolean safe = !sp.isSpectator() && inSafeZone(sp);
        Boolean before = IN_SAFE.put(sp.getUUID(), safe);
        if (before == null) {
            // Acabou de entrar no mundo/dimensão: começa ligado se já está num lugar seguro
            if (safe && com.renanpontes.isekaimahou.core.MahouConfig.peaceAuto(sp)) set(sp, true, false);
            else if (isOn(sp)) set(sp, false, false);
            return;
        }
        if (safe && !before && com.renanpontes.isekaimahou.core.MahouConfig.peaceAuto(sp)) set(sp, true, false);
        else if (!safe && before) set(sp, false, false);
    }

    static boolean inSafeZone(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        BlockPos pos = sp.blockPosition();
        for (Places.Place p : Places.all(level)) {
            if (p.safeZone() && p.contains(pos)) return true;
        }
        BlockPos home = sp.getRespawnPosition();
        if (home != null && sp.getRespawnDimension() == level.dimension()) {
            int dx = home.getX() - pos.getX(), dz = home.getZ() - pos.getZ(), dy = home.getY() - pos.getY();
            return dx * dx + dz * dz <= HOME_RADIUS * HOME_RADIUS && Math.abs(dy) <= 12;
        }
        return false;
    }

    /** NPCs que a tag de dados protege no Modo Paz (mestres da guilda, mentores...): os que o código não reconhece sozinho. */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> PEACE_PROTECTED =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, IsekaiMahou.id("peace_protected"));

    private static boolean protectedTarget(Entity t, Player attacker) {
        if (t instanceof Player || (t instanceof OwnableEntity own && own.getOwnerUUID() != null)) return true;
        // v1.0 (pedido do dono: "in peace mode eu não deveria poder bater nos NPC"): moradores, guardas, mercadores,
        // aldeões e os NPCs da tag. Quem já está atacando ESTE jogador pode ser revidado.
        if (!npc(t)) return false;
        return !(t instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == attacker);
    }

    private static boolean npc(Entity t) {
        return t instanceof net.minecraft.world.entity.npc.Npc || t instanceof net.minecraft.world.item.trading.Merchant
                || t instanceof com.renanpontes.isekaimahou.core.api.Reputation.FactionMember || t.getType().is(PEACE_PROTECTED);
    }

    private static void onAttack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !isOn(sp) || !protectedTarget(e.getTarget(), sp)) return;
        e.setCanceled(true);
        denied(sp);
    }

    private static void onDamage(LivingIncomingDamageEvent e) {
        Entity attacker = e.getSource().getEntity();
        // Quem está em paz não fere jogadores, bichos de jogadores nem NPCs (projéteis, magias que escaparam...)
        if (attacker instanceof ServerPlayer sp && isOn(sp) && attacker != e.getEntity() && protectedTarget(e.getEntity(), sp)) {
            e.setCanceled(true);
            return;
        }
        // Tomou um golpe de alguém: sai do Modo Paz para poder reagir
        if (e.getEntity() instanceof ServerPlayer victim && isOn(victim) && attacker != null && attacker != victim && e.getAmount() > 0) {
            set(victim, false, false);
        }
    }

    private static void forget(UUID id) {
        ON.remove(id);
        IN_SAFE.remove(id);
    }

    private PeaceMode() {}
}
