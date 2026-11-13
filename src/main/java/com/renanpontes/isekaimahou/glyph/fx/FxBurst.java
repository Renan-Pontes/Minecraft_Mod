package com.renanpontes.isekaimahou.glyph.fx;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * v1.3 — "efeitos compostos": o servidor manda UM pacote pequeno (tipo + elemento + cor + posição) e o CLIENTE monta o
 * efeito inteiro (clarão, anéis, estilhaços, partículas que sobem, rastro que fica...) respeitando o preset gráfico
 * ({@code client/fx/FxQuality}). Assim LOW fica leve de verdade e ULTRA fica cheio, sem mandar centenas de partículas
 * pela rede. Use pelos atalhos de {@link com.renanpontes.isekaimahou.core.ElementFx} ({@code impactFx}, {@code gather},
 * {@code nova}, {@code area}, {@code aura}, {@code beam}, {@code portal}, {@code domainSphere}, {@code skyStrike},
 * {@code slash}).
 * <p>
 * A linguagem visual por elemento (fogo, água/gelo, terra, vento, raio, luz, sombra, arcano/vazio, natureza) mora no
 * cliente: {@code client/spellfx/FxBurstClient}.
 */
public final class FxBurst {
    /** Impacto: clarão + onda + estilhaços + partículas que ficam. dir = normal da face, scale = tamanho, tier 0..5. */
    public static final int IMPACT = 0;
    /** Antecipação: partículas convergindo para a mão/ponto. scale = raio, ticks = duração, data = entidade a seguir. */
    public static final int GATHER = 1;
    /** Explosão gigante (ultimates): clarões, várias ondas, pilar, detritos e rescaldo. scale = raio. */
    public static final int NOVA = 2;
    /** Área: decalque no chão + partículas do elemento subindo/caindo. scale = raio, ticks = duração. */
    public static final int AREA = 3;
    /** Aura: casca de partículas em volta da entidade {@code data}. scale = tamanho, ticks = duração. */
    public static final int AURA = 4;
    /** Feixe: anéis ao longo + núcleo + boca + explosão no fim. pos = origem, dir = vetor até o fim, scale = largura. */
    public static final int BEAM = 5;
    /** Portal / invocação: círculo vertical girando, redemoinho para dentro. dir = normal, scale = raio. */
    public static final int PORTAL = 6;
    /** Expansão de domínio: cúpula se fechando do chão ao topo, clarão de "troca de paisagem". scale = raio. */
    public static final int DOMAIN = 7;
    /** Golpe do céu: círculo no céu, aviso no chão, pilar descendo e impacto depois de {@code ticks}. */
    public static final int SKY_STRIKE = 8;
    /** Reação elemental: data = ordinal de {@code ElementReactions.Reaction}. */
    public static final int REACTION = 9;
    /** Corte em meia-lua: dir = direção do golpe, scale = comprimento. */
    public static final int SLASH = 10;

    /** Distância máxima de envio (o cliente ainda corta pela distância do preset). */
    public static final double SEND_RANGE = 96.0;

    public record Payload(int kind, int element, int rgb, double x, double y, double z, float dx, float dy, float dz,
                          float scale, int tier, int data, int ticks) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(IsekaiMahou.id("spellfx_burst"));
        public static final StreamCodec<FriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeByte(p.element);
                    buf.writeMedium(p.rgb & 0xFFFFFF);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.dx);
                    buf.writeFloat(p.dy);
                    buf.writeFloat(p.dz);
                    buf.writeFloat(p.scale);
                    buf.writeByte(p.tier);
                    buf.writeVarInt(p.data + 1);
                    buf.writeVarInt(Math.max(0, p.ticks));
                },
                buf -> new Payload(buf.readByte(), buf.readByte(), buf.readUnsignedMedium(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte(),
                        buf.readVarInt() - 1, buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public Element elementOrArcane() {
            Element[] all = Element.values();
            return element >= 0 && element < all.length ? all[element] : Element.ARCANE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("spellfx-1");
        r.playToClient(Payload.TYPE, Payload.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.spellfx.FxBurstClient.receive(p));
    }

    /** Envia o efeito para os jogadores próximos (barato: só um laço na lista de jogadores do mundo). */
    public static void send(ServerLevel level, int kind, Element e, int rgb, Vec3 pos, Vec3 dir, float scale, int tier,
                            int data, int ticks) {
        Payload p = new Payload(kind, e == null ? Element.ARCANE.ordinal() : e.ordinal(), rgb, pos.x, pos.y, pos.z,
                (float) dir.x, (float) dir.y, (float) dir.z, Mth.clamp(scale, 0.05F, 64F), Mth.clamp(tier, 0, 5), data,
                Mth.clamp(ticks, 0, 600));
        double r2 = SEND_RANGE * SEND_RANGE;
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(pos.x, pos.y, pos.z) <= r2) PacketDistributor.sendToPlayer(sp, p);
        }
    }

    private FxBurst() {}
}
