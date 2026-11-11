package com.renanpontes.isekaimahou.core.boss;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.stack.StackManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Telegrafia de ataques no chão (servidor → clientes a até {@link #VIEW_RANGE} blocos).
 * <p>
 * O cliente desenha um decalque translúcido deitado no terreno que ENCHE do centro até a borda durante o
 * aquecimento ({@code warmup}), com borda pulsante e runas girando; no fim pisca (flash) e some. Assim o jogador
 * vê exatamente QUANDO e ONDE o golpe vai acertar.
 * <p>
 * Todos os métodos devolvem um id ({@code int}) para {@link #cancel}. Chame só na thread do servidor.
 * Formas com callback ({@code delayed*}) chamam o callback no fim do aquecimento com as entidades vivas
 * (não espectadoras) dentro da forma — o dono ({@link Shape#owner}) nunca entra na lista.
 * <p>
 * v0.8 — a Pilha: toda forma {@code delayed*} com dono VIVO ({@code .owner(mob)}) entra na {@link SpellStack} como
 * conjuração pendente do dono; as formas do mesmo dono criadas no mesmo tick formam UM golpe. Se alguém contrariar o
 * golpe, todas as formas dele somem sem callback. Sem contramágica, nada muda. Nome/tier/proteção no golpe:
 * {@link Shape#castName}, {@link Shape#tier}, {@link Shape#uncounterable}. {@link #show} (só visual) não entra.
 */
public final class Telegraph {
    /** Distância (blocos) até onde os jogadores recebem a telegrafia. */
    public static final int VIEW_RANGE = 96;
    /** Ticks finais em que um {@link #follow} para de seguir o alvo e fica parado (dá para fugir). */
    public static final int DEFAULT_LOCK = 10;

    // Cores prontas (RGB). Para a cor de um elemento use color(Element).
    public static final int DANGER = 0xFF3B30;
    public static final int WARNING = 0xFF9F1C;
    public static final int ARCANE = 0xB98CFF;
    public static final int HOLY = 0xFFE27A;
    public static final int VOID = 0x9B4DFF;
    public static final int FROST = 0x8FE3FF;
    public static final int SAFE = 0x4CFF7A;

    public enum Kind { CIRCLE, RING, LINE, CONE }

    /** Bits de {@link Shape#flags} (também viajam no pacote). */
    public static final int FLAG_SAFE = 1;
    public static final int FLAG_FLAT = 2;
    public static final int FLAG_NO_FLASH = 4;
    public static final int FLAG_NO_RUNES = 8;

    // ------------------------------------------------------------------ atalhos (contrato do design §6.1)

    /** Círculo cheio de raio {@code radius} centrado em {@code center} (Y = chão onde o golpe acerta). */
    public static int circle(ServerLevel level, Vec3 center, float radius, int warmupTicks, int rgb) {
        return show(level, Shape.circle(center, radius).warmup(warmupTicks).color(rgb));
    }

    /** Anel entre {@code inner} e {@code outer} (onda de choque: o miolo é seguro). */
    public static int ring(ServerLevel level, Vec3 center, float inner, float outer, int warmupTicks, int rgb) {
        return show(level, Shape.ring(center, inner, outer).warmup(warmupTicks).color(rgb));
    }

    /** Faixa reta de {@code from} até {@code to} com largura total {@code width} (investida, raio, rajada). */
    public static int line(ServerLevel level, Vec3 from, Vec3 to, float width, int warmupTicks, int rgb) {
        return show(level, Shape.line(from, to, width).warmup(warmupTicks).color(rgb));
    }

    /**
     * Cone (setor) saindo de {@code origin}. {@code yawDeg} no padrão do Minecraft ({@code mob.getYRot()}),
     * {@code angleDeg} = abertura TOTAL, {@code range} = alcance.
     */
    public static int cone(ServerLevel level, Vec3 origin, float yawDeg, float angleDeg, float range, int warmupTicks, int rgb) {
        return show(level, Shape.cone(origin, yawDeg, angleDeg, range).warmup(warmupTicks).color(rgb));
    }

    /** Círculo que SEGUE a entidade; nos últimos {@link #DEFAULT_LOCK} ticks fica parado (o jogador pode sair). */
    public static int follow(ServerLevel level, Entity e, float radius, int warmupTicks, int rgb) {
        return show(level, Shape.follow(e, radius).warmup(warmupTicks).color(rgb));
    }

    /** Zona segura verde ("fique aqui") por {@code ticks}; enche como um relógio mostrando quanto falta. */
    public static int safe(ServerLevel level, Vec3 center, float radius, int ticks) {
        return show(level, Shape.safe(center, radius).warmup(ticks));
    }

    /** Desenha um círculo e, no fim do aquecimento, chama {@code onHit} com as entidades vivas dentro dele. */
    public static int delayedBlast(ServerLevel level, Vec3 center, float radius, int warmupTicks, int rgb,
                                   Consumer<List<LivingEntity>> onHit) {
        return delayed(level, Shape.circle(center, radius).warmup(warmupTicks).color(rgb), onHit);
    }

    /** Desenha uma faixa e, no fim do aquecimento, chama {@code onHit} com as entidades vivas dentro dela. */
    public static int delayedLine(ServerLevel level, Vec3 from, Vec3 to, float width, int warmupTicks, int rgb,
                                  Consumer<List<LivingEntity>> onHit) {
        return delayed(level, Shape.line(from, to, width).warmup(warmupTicks).color(rgb), onHit);
    }

    /** Anel com callback (onda de choque atrasada). */
    public static int delayedRing(ServerLevel level, Vec3 center, float inner, float outer, int warmupTicks, int rgb,
                                  Consumer<List<LivingEntity>> onHit) {
        return delayed(level, Shape.ring(center, inner, outer).warmup(warmupTicks).color(rgb), onHit);
    }

    /** Cone com callback (golpe em leque, sopro). */
    public static int delayedCone(ServerLevel level, Vec3 origin, float yawDeg, float angleDeg, float range, int warmupTicks,
                                  int rgb, Consumer<List<LivingEntity>> onHit) {
        return delayed(level, Shape.cone(origin, yawDeg, angleDeg, range).warmup(warmupTicks).color(rgb), onHit);
    }

    /** Círculo que segue a entidade e trava no fim; o callback recebe quem está no ponto travado. */
    public static int delayedFollow(ServerLevel level, Entity e, float radius, int warmupTicks, int rgb,
                                    Consumer<List<LivingEntity>> onHit) {
        return delayed(level, Shape.follow(e, radius).warmup(warmupTicks).color(rgb), onHit);
    }

    // ------------------------------------------------------------------ API completa

    /** Mostra a forma (só visual). Devolve o id, ou -1 se o limite do servidor foi atingido. */
    public static int show(ServerLevel level, Shape shape) {
        return BossScheduler.add(level, shape.copy(), null);
    }

    /**
     * Mostra a forma e chama {@code onHit} no fim do aquecimento (sempre aceito, mesmo acima do limite).
     * Com dono vivo, o golpe entra na Pilha (pode ser contrariado: aí a forma some e {@code onHit} não roda).
     */
    public static int delayed(ServerLevel level, Shape shape, Consumer<List<LivingEntity>> onHit) {
        Shape s = shape.copy();
        if (onHit == null || !(s.owner instanceof LivingEntity owner) || !owner.isAlive()) {
            return BossScheduler.add(level, s, onHit);
        }
        int[] ref = {-1};
        Consumer<List<LivingEntity>> wrapped = hits -> {
            boolean hit = true;
            try {
                // false = contrariado depois que o callback entrou na fila deste tick: não acerta
                hit = StackManager.onTelegraphFired(ref[0]);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Pilha: erro ao resolver um golpe telegrafado", ex);
            }
            if (hit) onHit.accept(hits);
        };
        int id = BossScheduler.add(level, s, wrapped);
        ref[0] = id;
        if (id >= 0) {
            try {
                StackManager.joinTelegraph(level, id, owner, s.center(), s.extent(), s.warmup, s.castName, s.tier,
                        s.uncounterable, s.element != null ? s.element : elementOfColor(s.rgb));
            } catch (RuntimeException ex) {
                // A Pilha nunca pode atrapalhar o golpe do chefe
                IsekaiMahou.LOGGER.error("Pilha: erro ao registrar um golpe telegrafado", ex);
            }
        }
        return id;
    }

    /** Cancela (some rápido, sem flash e sem callback). Ids inválidos/expirados são ignorados. */
    public static void cancel(ServerLevel level, int id) {
        BossScheduler.cancel(id, false);
        StackManager.onTelegraphGone(id, false);
    }

    /**
     * Termina AGORA com o flash de "acertou" (sem chamar callback). Para chefes que controlam o tempo do golpe no
     * próprio tick e querem que o desenho pisque exatamente no impacto.
     */
    public static void finish(ServerLevel level, int id) {
        BossScheduler.cancel(id, true);
        StackManager.onTelegraphGone(id, true);
    }

    /** Cancela todas as formas cujo {@link Shape#owner} é {@code owner} (ex.: chefe atordoado, troca de fase). */
    public static void cancelAll(Entity owner) {
        BossScheduler.cancelOwned(owner);
        StackManager.onOwnerTelegraphsCancelled(owner);
    }

    /** A forma ainda está no aquecimento/hold no servidor? */
    public static boolean isActive(int id) {
        return BossScheduler.isActive(id);
    }

    /**
     * Centro atual da forma (para {@code follow}: posição do alvo ou ponto travado). Vale também DENTRO do callback
     * de um {@code delayed*} (ex.: onde a perseguição travou). Null se já acabou.
     */
    @Nullable
    public static Vec3 centerOf(int id) {
        return BossScheduler.centerOf(id);
    }

    /** Entidades vivas (não espectadoras) dentro da forma agora — útil para dano contínuo em zonas com hold. */
    public static List<LivingEntity> entitiesIn(ServerLevel level, Shape shape) {
        return BossScheduler.collect(level, shape, shape.center());
    }

    /** Cor RGB do elemento (para telegrafar no tom do chefe). */
    public static int color(Element element) {
        return element.color;
    }

    /** Elemento cuja cor é exatamente {@code rgb} (golpes pintados com {@link #color(Element)}), ou null. */
    @Nullable
    static Element elementOfColor(int rgb) {
        for (Element e : Element.values()) if ((e.color & 0xFFFFFF) == (rgb & 0xFFFFFF)) return e;
        return null;
    }

    // ------------------------------------------------------------------ forma

    /**
     * Descrição de uma forma (construção fluente). Ex.:
     * <pre>Telegraph.show(level, Telegraph.Shape.circle(pos, 6).warmup(30).hold(40).color(0xFF5020).owner(boss));</pre>
     */
    public static final class Shape {
        final Kind kind;
        final Vec3 a;
        final Vec3 b;
        final float radius;
        final float inner;
        final float yawDeg;
        final float angleDeg;
        final float width;
        int warmup = 20;
        int hold;
        int rgb = DANGER;
        int lock = -1;
        int flags;
        double below = 1.5;
        double above = 3.0;
        @Nullable Entity follow;
        @Nullable Entity owner;
        // v0.8 — a Pilha
        @Nullable Component castName;
        @Nullable SpellStack.Tier tier;
        @Nullable Element element;
        boolean uncounterable;

        private Shape(Kind kind, Vec3 a, Vec3 b, float radius, float inner, float yawDeg, float angleDeg, float width) {
            this.kind = kind;
            this.a = a;
            this.b = b;
            this.radius = radius;
            this.inner = inner;
            this.yawDeg = yawDeg;
            this.angleDeg = angleDeg;
            this.width = width;
        }

        public static Shape circle(Vec3 center, float radius) {
            return new Shape(Kind.CIRCLE, center, center, Math.max(0.25F, radius), 0, 0, 360, 0);
        }

        public static Shape ring(Vec3 center, float inner, float outer) {
            float in = Math.max(0, Math.min(inner, outer - 0.25F));
            return new Shape(Kind.RING, center, center, Math.max(0.25F, outer), in, 0, 360, 0);
        }

        public static Shape line(Vec3 from, Vec3 to, float width) {
            return new Shape(Kind.LINE, from, to, 0, 0, 0, 0, Math.max(0.25F, width));
        }

        public static Shape cone(Vec3 origin, float yawDeg, float angleDeg, float range) {
            return new Shape(Kind.CONE, origin, origin, Math.max(0.25F, range), 0, Mth.wrapDegrees(yawDeg),
                    Mth.clamp(angleDeg, 5, 360), 0);
        }

        public static Shape follow(Entity target, float radius) {
            Shape s = circle(target.position(), radius);
            s.follow = target;
            return s;
        }

        public static Shape safe(Vec3 center, float radius) {
            Shape s = circle(center, radius).color(SAFE);
            s.flags |= FLAG_SAFE | FLAG_NO_FLASH;
            return s;
        }

        /** Ticks até o golpe (mín. 1). */
        public Shape warmup(int ticks) { this.warmup = Math.max(1, ticks); return this; }

        /** Ticks que a forma continua desenhada (cheia, pulsando) DEPOIS do aquecimento — perigo que fica no chão. */
        public Shape hold(int ticks) { this.hold = Math.max(0, ticks); return this; }

        public Shape color(int rgb) { this.rgb = rgb & 0xFFFFFF; return this; }

        /** Some sozinha (sem callback) se o dono morrer/sumir; o dono também nunca é "atingido". */
        public Shape owner(@Nullable Entity owner) { this.owner = owner; return this; }

        /** Só para {@code follow}: ticks finais em que o círculo fica parado (padrão {@link #DEFAULT_LOCK}). */
        public Shape lock(int ticks) { this.lock = Math.max(0, ticks); return this; }

        /**
         * Faixa vertical do acerto, relativa ao Y da forma (padrão 1.5 abaixo / 3 acima).
         * Ex.: {@code vertical(0.5, 1.0)} = onda rasteira que dá para PULAR.
         */
        public Shape vertical(double below, double above) {
            this.below = Math.max(0, below);
            this.above = Math.max(0, above);
            return this;
        }

        /** Desenha reto na Y dada (+0.02) em vez de deitar no terreno (arenas planas, plataformas no ar). */
        public Shape flat() { this.flags |= FLAG_FLAT; return this; }

        /** Sem o flash final (avisos que não são um golpe). */
        public Shape noFlash() { this.flags |= FLAG_NO_FLASH; return this; }

        /** Sem o anel de runas girando (formas pequenas ou muitas ao mesmo tempo). */
        public Shape noRunes() { this.flags |= FLAG_NO_RUNES; return this; }

        /** Pilha: nome do golpe no HUD ("Chuva de Brasas"). Sem nome, o HUD mostra só o nome do dono. */
        public Shape castName(@Nullable Component name) { this.castName = name; return this; }

        /** Pilha: peso do golpe (poder para contrariar). Padrão: pelo aquecimento (&lt;20 MINOR, 20–59 MAJOR, ≥60 ULTIMATE). */
        public Shape tier(@Nullable SpellStack.Tier tier) { this.tier = tier; return this; }

        /** Pilha: o golpe aparece no HUD mas NÃO pode ser contrariado (basta uma forma do grupo ter isto). */
        public Shape uncounterable() { this.uncounterable = true; return this; }

        /** Pilha: elemento do golpe (runa no HUD). Padrão: o elemento cuja cor é a da forma, se houver. */
        public Shape element(@Nullable Element element) { this.element = element; return this; }

        public Kind kind() { return kind; }

        public int warmupTicks() { return warmup; }

        public int holdTicks() { return hold; }

        /** Centro da forma (linha: ponto médio; follow: posição atual do alvo). */
        public Vec3 center() {
            if (follow != null && !follow.isRemoved()) return follow.position();
            return kind == Kind.LINE ? a.add(b).scale(0.5) : a;
        }

        /** Raio do círculo que contém a forma inteira (a partir de {@link #center()}). */
        public double extent() {
            return kind == Kind.LINE ? a.distanceTo(b) / 2 + width : radius;
        }
        int lockTicks() {
            return lock >= 0 ? Math.min(lock, warmup) : Math.min(DEFAULT_LOCK, warmup / 3);
        }

        /** A entidade (caixa de colisão) está dentro da forma, centrada em {@link #center()}? */
        public boolean contains(Entity e) {
            return containsAt(e, center());
        }

        boolean containsAt(Entity e, Vec3 c) {
            AABB box = e.getBoundingBox();
            double hw = (box.getXsize() + box.getZsize()) * 0.25;
            double ex = (box.minX + box.maxX) * 0.5;
            double ez = (box.minZ + box.maxZ) * 0.5;
            switch (kind) {
                case LINE -> {
                    // Retângulo (o mesmo desenhado no chão), não cápsula: quem está logo depois da ponta não é atingido
                    double abx = b.x - a.x, abz = b.z - a.z;
                    double len = Math.sqrt(abx * abx + abz * abz);
                    double rx = ex - a.x, rz = ez - a.z;
                    double along = len < 1.0E-3 ? 0 : (rx * abx + rz * abz) / len;
                    double side = len < 1.0E-3 ? Math.hypot(rx, rz) : Math.abs(rx * abz - rz * abx) / len;
                    if (along < -hw || along > len + hw || side > width * 0.5 + hw) return false;
                    double y = a.y + (b.y - a.y) * (len < 1.0E-3 ? 0 : Mth.clamp(along / len, 0, 1));
                    return vertical(box, y);
                }
                case CIRCLE -> {
                    return vertical(box, c.y) && Math.hypot(ex - c.x, ez - c.z) <= radius + hw;
                }
                case RING -> {
                    double d = Math.hypot(ex - c.x, ez - c.z);
                    return vertical(box, c.y) && d + hw >= inner && d - hw <= radius;
                }
                case CONE -> {
                    double dx = ex - c.x, dz = ez - c.z;
                    double d = Math.hypot(dx, dz);
                    if (!vertical(box, c.y) || d > radius + hw) return false;
                    if (d <= hw + 0.3) return true;
                    float pointYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
                    double slack = Math.toDegrees(Math.asin(Math.min(1.0, hw / d)));
                    return Math.abs(Mth.wrapDegrees(pointYaw - yawDeg)) <= angleDeg * 0.5 + slack;
                }
            }
            return false;
        }

        private boolean vertical(AABB box, double y) {
            return box.maxY >= y - below && box.minY <= y + above;
        }

        /** Caixa que envolve a forma (para buscar entidades). */
        AABB bounds(Vec3 c) {
            double up = above + 1, down = below + 1;
            if (kind == Kind.LINE) {
                double w = width * 0.5 + 1;
                return new AABB(Math.min(a.x, b.x) - w, Math.min(a.y, b.y) - down, Math.min(a.z, b.z) - w,
                        Math.max(a.x, b.x) + w, Math.max(a.y, b.y) + up, Math.max(a.z, b.z) + w);
            }
            double r = radius + 1;
            return new AABB(c.x - r, c.y - down, c.z - r, c.x + r, c.y + up, c.z + r);
        }

        Shape copy() {
            Shape s = new Shape(kind, a, b, radius, inner, yawDeg, angleDeg, width);
            s.warmup = warmup;
            s.hold = hold;
            s.rgb = rgb;
            s.lock = lock;
            s.flags = flags;
            s.below = below;
            s.above = above;
            s.follow = follow;
            s.owner = owner;
            s.castName = castName;
            s.tier = tier;
            s.element = element;
            s.uncounterable = uncounterable;
            return s;
        }
    }

    private Telegraph() {}
}
