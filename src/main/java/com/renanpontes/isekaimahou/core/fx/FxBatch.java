package com.renanpontes.isekaimahou.core.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Release 1.0 (perf) — partículas de magia EM LOTE. Antes, um anel de 40 pontos eram ~53 pacotes por jogador e um raio
 * de 20 blocos passava de 200; numa luta grande isso virava milhares de pacotes por segundo. Agora tudo que as magias
 * pedem durante o tick entra numa fila por mundo e sai no FIM do tick como UM pacote por jogador (só com o que está
 * perto dele):
 * <ul>
 *   <li>{@link Emit}: igual ao {@code sendParticles} do vanilla (partícula, posição, quantidade, espalhamento,
 *   velocidade) — as partículas repetidas viram um índice numa paleta e a posição vai como 3 floats;</li>
 *   <li>{@link Ring} e {@link Line}: só os parâmetros; o CLIENTE desenha os pontos pelo Preset Gráfico
 *   ({@code client/fx/FxQuality}) — PC fraco desenha menos, ULTRA desenha mais.</li>
 * </ul>
 * Quem usa: {@link ElementFx} ({@code burst/ring/line/channeling/sendFar}...) e {@code feature/forms_a/FormsFx}; os
 * módulos continuam chamando a mesma API de sempre. Se o lote não estiver ativo (pacote não registrado, chamada fora da
 * thread do servidor), cai no caminho antigo, partícula por partícula.
 * <p>
 * Cliente: {@code client/spellfx/FxBatchClient}. Registro do pacote: {@link #init} (chamado pelo módulo forms_a).
 */
public final class FxBatch {
    /** Alcance normal (o mesmo do {@code sendParticles} do vanilla). */
    public static final double NEAR = 32.0;
    /** Alcance dos efeitos grandes (clarão, onda, círculo, pilar, formas que miram longe). */
    public static final double FAR = 96.0;
    /** Teto de itens na fila de um mundo por tick (válvula de segurança: acima disso, o que é pequeno é descartado). */
    private static final int MAX_QUEUE = 4096;
    private static final int MAX_PER_PACKET = 1024;
    private static final int KIND_EMIT = 0, KIND_RING = 1, KIND_LINE = 2;

    /** Um item do lote. {@code far}: vai até {@link #FAR} blocos e ignora o limite de distância do cliente. */
    public sealed interface Entry permits Emit, Ring, Line {
        double x();
        double y();
        double z();
        boolean far();
    }

    /** Igual ao pacote de partículas do vanilla ({@code count} 0 = dx/dy/dz viram a direção da velocidade). */
    public record Emit(ParticleOptions particle, double x, double y, double z, int count, float dx, float dy, float dz,
                       float speed, boolean far) implements Entry {}

    /** Anel horizontal: brilho de cor {@code rgb} em {@code points} pontos + a partícula do elemento a cada {@code sigEvery}. */
    public record Ring(int element, int rgb, double x, double y, double z, float radius, int points, int sigEvery,
                       float glow, boolean far) implements Entry {}

    /** Linha do elemento de (x,y,z) até (x,y,z)+(vx,vy,vz). Raio vira arco em zigue-zague. */
    public record Line(int element, double x, double y, double z, float vx, float vy, float vz, boolean far) implements Entry {}

    /** Quem recebe as partículas de uma forma expandida (cliente: cria a partícula; servidor: caminho antigo). */
    @FunctionalInterface
    public interface Sink {
        void add(ParticleOptions p, double x, double y, double z, int count, double dx, double dy, double dz, double speed);
    }

    public record Payload(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(IsekaiMahou.id("fx_batch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(FxBatch::write, FxBatch::read);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<ServerLevel, ArrayList<Entry>> QUEUES = new IdentityHashMap<>();
    /** init() já foi chamado (evita registrar duas vezes). */
    private static boolean hooked;
    /** O pacote foi registrado DE FATO (só então o lote vale; antes disso, caminho antigo). */
    private static volatile boolean registered;
    // Estatísticas (comando /mahou fx stats): itens enviados, pacotes enviados, pacotes que seriam no jeito antigo, descartados
    private static long statEntries, statPackets, statOldPackets, statDropped;

    // =====================================================================================
    //  Registro
    // =====================================================================================

    /** Registra o pacote e a descarga no fim do tick. Chamar uma vez no init de um módulo (pode repetir sem problema). */
    public static synchronized void init(IEventBus modBus) {
        if (hooked) return;
        hooked = true;
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            e.registrar("fxbatch-1").playToClient(Payload.TYPE, Payload.STREAM_CODEC,
                    (p, c) -> com.renanpontes.isekaimahou.client.spellfx.FxBatchClient.receive(p));
            registered = true;
        });
        // Por último no tick: pega também o que os outros módulos pediram no ServerTickEvent.Post deles
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerTickEvent.Post e) -> flush());
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel sl) QUEUES.remove(sl);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> QUEUES.clear());
    }

    /** O lote está valendo para este mundo agora? (pacote registrado e chamada na thread do servidor) */
    public static boolean active(ServerLevel level) {
        return registered && level.getServer().isSameThread();
    }

    // =====================================================================================
    //  API (servidor)
    // =====================================================================================

    /** Mesmo contrato do {@code ServerLevel.sendParticles}; {@code far} = até 96 blocos (senão 32). */
    public static void emit(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                            double dx, double dy, double dz, double speed, boolean far) {
        if (p == null || level.players().isEmpty()) return;
        if (!active(level)) {
            direct(level, p, x, y, z, count, dx, dy, dz, speed, far);
            return;
        }
        add(level, new Emit(p, x, y, z, Math.max(0, count), (float) dx, (float) dy, (float) dz, (float) speed, far));
    }

    /** Anel horizontal do elemento com brilho {@code rgb} (um item no lote; o cliente desenha os pontos). */
    public static void ring(ServerLevel level, Element e, int rgb, Vec3 center, double radius, int points, int sigEvery,
                            float glow, boolean far) {
        if (points <= 0 || level.players().isEmpty()) return;
        Ring r = new Ring(ordinal(e), rgb & 0xFFFFFF, center.x, center.y, center.z, (float) Math.max(0, radius),
                Math.min(points, 512), Mth.clamp(sigEvery, 1, 127), glow, far);
        if (active(level)) add(level, r);
        else expand(r, 1F, (p, x, y, z, n, dx, dy, dz, s) -> direct(level, p, x, y, z, n, dx, dy, dz, s, far));
    }

    /** Linha do elemento entre dois pontos (um item no lote). */
    public static void line(ServerLevel level, Element e, Vec3 from, Vec3 to, boolean far) {
        if (level.players().isEmpty()) return;
        Line l = new Line(ordinal(e), from.x, from.y, from.z, (float) (to.x - from.x), (float) (to.y - from.y),
                (float) (to.z - from.z), far);
        if (active(level)) add(level, l);
        else expand(l, level.random, 1F, (p, x, y, z, n, dx, dy, dz, s) -> direct(level, p, x, y, z, n, dx, dy, dz, s, far));
    }

    private static int ordinal(Element e) {
        return e == null ? Element.ARCANE.ordinal() : e.ordinal();
    }

    private static Element element(int ordinal) {
        Element[] all = Element.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : Element.ARCANE;
    }

    private static void add(ServerLevel level, Entry e) {
        ArrayList<Entry> q = QUEUES.computeIfAbsent(level, k -> new ArrayList<>(256));
        if (q.size() >= MAX_QUEUE && !e.far()) {
            statDropped++;
            return;
        }
        q.add(e);
    }

    /** Caminho antigo (sem lote): um pacote de partículas por chamada. */
    private static void direct(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                               double dx, double dy, double dz, double speed, boolean far) {
        if (!far) {
            level.sendParticles(p, x, y, z, count, dx, dy, dz, speed);
            return;
        }
        for (ServerPlayer sp : level.players()) {
            double d2 = sp.distanceToSqr(x, y, z);
            if (d2 > FAR * FAR) continue;
            level.sendParticles(sp, p, d2 > NEAR * NEAR, x, y, z, count, dx, dy, dz, speed);
        }
    }

    // =====================================================================================
    //  Descarga (fim do tick): um pacote por jogador
    // =====================================================================================

    private static void flush() {
        if (QUEUES.isEmpty()) return;
        for (Map.Entry<ServerLevel, ArrayList<Entry>> en : QUEUES.entrySet()) {
            ArrayList<Entry> q = en.getValue();
            if (q.isEmpty()) continue;
            for (ServerPlayer sp : en.getKey().players()) {
                ArrayList<Entry> mine = null;
                for (int i = 0, n = q.size(); i < n; i++) {
                    Entry e = q.get(i);
                    double r = e.far() ? FAR : NEAR;
                    if (distSqr(sp, e) > r * r) continue;
                    if (mine == null) mine = new ArrayList<>(Math.min(n, 64));
                    mine.add(e);
                    statOldPackets += oldPackets(e);
                    if (mine.size() >= MAX_PER_PACKET) {
                        send(sp, mine);
                        mine = null;
                    }
                }
                if (mine != null) send(sp, mine);
            }
            q.clear();
        }
    }

    private static void send(ServerPlayer sp, List<Entry> entries) {
        statEntries += entries.size();
        statPackets++;
        PacketDistributor.sendToPlayer(sp, new Payload(entries));
    }

    /** Distância² do jogador até o item (anel: até a borda; linha: até o ponto mais perto do segmento). */
    private static double distSqr(ServerPlayer sp, Entry e) {
        if (e instanceof Ring r) {
            double d = Math.max(0, Math.sqrt(sp.distanceToSqr(r.x, r.y, r.z)) - r.radius);
            return d * d;
        }
        if (e instanceof Line l) {
            double px = sp.getX() - l.x, py = sp.getY() - l.y, pz = sp.getZ() - l.z;
            double len2 = l.vx * l.vx + l.vy * l.vy + l.vz * l.vz;
            double t = len2 < 1.0E-6 ? 0 : Mth.clamp((px * l.vx + py * l.vy + pz * l.vz) / len2, 0, 1);
            double cx = px - l.vx * t, cy = py - l.vy * t, cz = pz - l.vz * t;
            return cx * cx + cy * cy + cz * cz;
        }
        return sp.distanceToSqr(e.x(), e.y(), e.z());
    }

    /** Quantos pacotes este item custava antes do lote (para a estatística). */
    private static int oldPackets(Entry e) {
        if (e instanceof Ring r) return r.points + (r.points + r.sigEvery - 1) / r.sigEvery;
        if (e instanceof Line l) {
            double len = Math.sqrt(l.vx * l.vx + l.vy * l.vy + l.vz * l.vz);
            int steps = Math.max(2, (int) (len * 4)) + 1;
            return steps + steps / 4 + (element(l.element) == Element.LIGHTNING ? steps + 1 : 0);
        }
        return 1;
    }

    /** {itens, pacotes, pacotes no jeito antigo, descartados} desde o último {@link #resetStats()}. */
    public static long[] stats() {
        return new long[] {statEntries, statPackets, statOldPackets, statDropped};
    }

    public static void resetStats() {
        statEntries = statPackets = statOldPackets = statDropped = 0;
    }

    // =====================================================================================
    //  Formas (o mesmo código desenha no cliente e no caminho antigo do servidor)
    // =====================================================================================

    /** Pontos do anel. {@code quality} = multiplicador do preset (1 = todos os pontos). */
    public static void expand(Ring r, float quality, Sink out) {
        Element e = element(r.element);
        ParticleOptions g = ElementFx.glow(r.rgb, r.glow);
        ParticleOptions sig = ElementFx.signature(e);
        int points = Math.max(Math.min(r.points, 6), Math.round(r.points * quality));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = r.x + Math.cos(a) * r.radius, z = r.z + Math.sin(a) * r.radius;
            out.add(g, x, r.y + 0.1, z, 1, 0.02, 0.02, 0.02, 0);
            if (i % r.sigEvery == 0) out.add(sig, x, r.y + 0.15, z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Pontos da linha (raio: arco em zigue-zague com faíscas alinhadas + clarão na ponta). */
    public static void expand(Line l, RandomSource r, float quality, Sink out) {
        Element e = element(l.element);
        Vec3 from = new Vec3(l.x, l.y, l.z);
        Vec3 d = new Vec3(l.vx, l.vy, l.vz);
        double len = d.length();
        double density = 4.0 * Mth.clamp(quality, 0.3F, 1.5F);
        int steps = Math.max(2, (int) (len * density));
        boolean jagged = e == Element.LIGHTNING && len > 0.2;
        ParticleOptions g = ElementFx.dust(e, 1.0F);
        ParticleOptions sig = ElementFx.signature(e);
        ParticleOptions spark = jagged ? ElementFx.fx(SpellFxParticles.SPARK, ElementFx.lighten(e.color, 0.4F), 0.8F, 4, -1, 0) : null;
        // Desvio lateral do zigue-zague (só no raio): pontos de controle a cada ~1.2 bloco
        Vec3 side = jagged ? perpendicular(d) : Vec3.ZERO;
        Vec3 up = jagged ? d.cross(side).normalize() : Vec3.ZERO;
        double offA = 0, offB = 0, nextA = 0, nextB = 0;
        int seg = Math.max(1, (int) (steps / Math.max(1.0, len / 1.2)));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            if (jagged) {
                if (i % seg == 0) {
                    offA = nextA;
                    offB = nextB;
                    boolean last = i + seg > steps;
                    nextA = last ? 0 : (r.nextDouble() - 0.5) * 0.7;
                    nextB = last ? 0 : (r.nextDouble() - 0.5) * 0.7;
                }
                double f = (i % seg) / (double) seg;
                p = p.add(side.scale(Mth.lerp(f, offA, nextA))).add(up.scale(Mth.lerp(f, offB, nextB)));
                out.add(spark, p.x, p.y, p.z, 0, d.x / len, d.y / len, d.z / len, 0.05);
            }
            out.add(g, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 4 == 0) out.add(sig, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        if (jagged) {
            out.add(ElementFx.fx(SpellFxParticles.FLASH, e.color, 0.7F, 0, -1, 0), from.x + d.x, from.y + d.y, from.z + d.z, 1, 0, 0, 0, 0);
        }
    }

    private static Vec3 perpendicular(Vec3 d) {
        Vec3 s = d.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1.0E-6) s = d.cross(new Vec3(1, 0, 0));
        return s.normalize();
    }

    // =====================================================================================
    //  Rede (compacto: paleta de partículas + posições relativas em float)
    // =====================================================================================

    private static void write(RegistryFriendlyByteBuf buf, Payload p) {
        List<Entry> es = p.entries;
        double ox = 0, oy = 0, oz = 0;
        if (!es.isEmpty()) {
            Entry f = es.get(0);
            ox = Math.floor(f.x());
            oy = Math.floor(f.y());
            oz = Math.floor(f.z());
        }
        buf.writeDouble(ox);
        buf.writeDouble(oy);
        buf.writeDouble(oz);
        // Paleta: cada partícula diferente vai uma vez só
        List<ParticleOptions> palette = new ArrayList<>();
        Map<ParticleOptions, Integer> index = new HashMap<>();
        int[] slot = new int[es.size()];
        for (int i = 0; i < slot.length; i++) {
            if (!(es.get(i) instanceof Emit m)) continue;
            Integer k = index.get(m.particle);
            if (k == null) {
                k = palette.size();
                palette.add(m.particle);
                index.put(m.particle, k);
            }
            slot[i] = k;
        }
        buf.writeVarInt(palette.size());
        for (ParticleOptions o : palette) ParticleTypes.STREAM_CODEC.encode(buf, o);
        buf.writeVarInt(es.size());
        for (int i = 0; i < slot.length; i++) {
            Entry e = es.get(i);
            if (e instanceof Emit m) {
                boolean spread = m.dx != 0 || m.dy != 0 || m.dz != 0;
                boolean speed = m.speed != 0;
                int head = KIND_EMIT | (m.far ? 4 : 0) | (spread ? 8 : 0) | (speed ? 16 : 0)
                        | (m.count == 1 ? 32 : m.count == 0 ? 64 : 0);
                buf.writeByte(head);
                pos(buf, e, ox, oy, oz);
                buf.writeVarInt(slot[i]);
                if (m.count > 1) buf.writeVarInt(m.count);
                if (spread) {
                    buf.writeFloat(m.dx);
                    buf.writeFloat(m.dy);
                    buf.writeFloat(m.dz);
                }
                if (speed) buf.writeFloat(m.speed);
            } else if (e instanceof Ring r) {
                buf.writeByte(KIND_RING | (r.far ? 4 : 0));
                pos(buf, e, ox, oy, oz);
                buf.writeByte(r.element);
                buf.writeMedium(r.rgb);
                buf.writeFloat(r.radius);
                buf.writeVarInt(r.points);
                buf.writeByte(r.sigEvery);
                buf.writeFloat(r.glow);
            } else if (e instanceof Line l) {
                buf.writeByte(KIND_LINE | (l.far ? 4 : 0));
                pos(buf, e, ox, oy, oz);
                buf.writeByte(l.element);
                buf.writeFloat(l.vx);
                buf.writeFloat(l.vy);
                buf.writeFloat(l.vz);
            }
        }
    }

    private static void pos(RegistryFriendlyByteBuf buf, Entry e, double ox, double oy, double oz) {
        buf.writeFloat((float) (e.x() - ox));
        buf.writeFloat((float) (e.y() - oy));
        buf.writeFloat((float) (e.z() - oz));
    }

    private static Payload read(RegistryFriendlyByteBuf buf) {
        double ox = buf.readDouble(), oy = buf.readDouble(), oz = buf.readDouble();
        int np = buf.readVarInt();
        List<ParticleOptions> palette = new ArrayList<>(np);
        for (int i = 0; i < np; i++) palette.add(ParticleTypes.STREAM_CODEC.decode(buf));
        int n = buf.readVarInt();
        List<Entry> es = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int head = buf.readByte();
            boolean far = (head & 4) != 0;
            double x = ox + buf.readFloat(), y = oy + buf.readFloat(), z = oz + buf.readFloat();
            switch (head & 3) {
                case KIND_EMIT -> {
                    int k = buf.readVarInt();
                    int count = (head & 32) != 0 ? 1 : (head & 64) != 0 ? 0 : buf.readVarInt();
                    float dx = 0, dy = 0, dz = 0, speed = 0;
                    if ((head & 8) != 0) {
                        dx = buf.readFloat();
                        dy = buf.readFloat();
                        dz = buf.readFloat();
                    }
                    if ((head & 16) != 0) speed = buf.readFloat();
                    // índice fora da paleta = pacote corrompido: lê tudo, só não desenha
                    if (k >= 0 && k < palette.size()) es.add(new Emit(palette.get(k), x, y, z, count, dx, dy, dz, speed, far));
                }
                case KIND_RING -> es.add(new Ring(buf.readByte(), buf.readUnsignedMedium(), x, y, z, buf.readFloat(),
                        buf.readVarInt(), buf.readByte(), buf.readFloat(), far));
                case KIND_LINE -> es.add(new Line(buf.readByte(), x, y, z, buf.readFloat(), buf.readFloat(), buf.readFloat(), far));
                default -> throw new IllegalStateException("fx_batch: tipo desconhecido " + (head & 3));
            }
        }
        return new Payload(es);
    }

    private FxBatch() {}
}
