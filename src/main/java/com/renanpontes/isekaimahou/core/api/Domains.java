package com.renanpontes.isekaimahou.core.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.stack.DomainRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Domínios para outros módulos (servidor). O núcleo só guarda quais domínios estão abertos e chama os
 * {@link LawHandler} das leis registradas; quem abre/fecha é o módulo {@code domain}, chamando {@link #opened},
 * {@link #ticked} e {@link #closed}. Módulos com leis próprias ({@code law_shrine}, {@code law_infinite_void}...)
 * registram o handler no init com {@link #registerLaw}.
 * <p>
 * Barreira (§11.1 do design): domínio FECHADO ({@code open == false}) tem barreira com integridade, guardada pelo módulo
 * domain ({@link #setBarrierHandler}). Barreira normal só quebra POR FORA; invertida ({@link Info#inverted()}) só POR
 * DENTRO. O domínio mais interno manda: um domínio maior não atravessa a barreira de um menor de outro dono
 * ({@link #shielded}) — ele só ataca a barreira por fora.
 */
public final class Domains {
    /** Lei Acerto Garantido (fura o Infinito: {@link #sureHitOn}). */
    public static final ResourceLocation SURE_HIT = IsekaiMahou.id("law_sure_hit");
    /**
     * Pseudo-lei que o módulo domain põe nos Domínios SIMPLES: quem está dentro de um Domínio Simples dele mesmo ou
     * de um aliado não sofre Acerto Garantido. Domínio Simples não tem barreira e não conta como "mais interno".
     */
    public static final ResourceLocation SIMPLE = IsekaiMahou.id("simple_domain");
    /** Distância máxima (blocos) entre o ponto de impacto e a parede para {@link #hitBarrier} considerar que acertou. */
    public static final double WALL_TOLERANCE = 1.5;

    /**
     * Um domínio aberto. {@code id} = identificador único enquanto existir (o módulo domain usa o id da entidade do
     * domo). Um {@code Info} novo com o mesmo id (em {@link #ticked}) substitui o anterior (centro/raio podem mudar).
     *
     * @param open          barreira aberta (sem paredes: não tem integridade, não prende ninguém)
     * @param laws          ids das leis da magia (inclui as de outros módulos)
     * @param ctx           contexto da magia do domínio (pode ser null, ex.: domínio de mob sem magia)
     * @param durationTicks duração total prevista (≤ 0 = indefinida)
     * @param inverted      barreira invertida (aumento "Inverter"): só quebra POR DENTRO. Ignorado se {@code open}
     */
    public record Info(int id, ServerLevel level, LivingEntity owner, Vec3 center, float radius, boolean open,
                       Set<ResourceLocation> laws, Element element, @Nullable SpellContext ctx, long openedTick, int durationTicks,
                       boolean inverted) {
        public Info {
            laws = laws == null ? Set.of() : Set.copyOf(laws);
            element = element == null ? Element.ARCANE : element;
            inverted = inverted && !open;
        }

        /** Barreira normal (não invertida). */
        public Info(int id, ServerLevel level, LivingEntity owner, Vec3 center, float radius, boolean open,
                    Set<ResourceLocation> laws, Element element, @Nullable SpellContext ctx, long openedTick, int durationTicks) {
            this(id, level, owner, center, radius, open, laws, element, ctx, openedTick, durationTicks, false);
        }

        public boolean hasLaw(ResourceLocation law) { return laws.contains(law); }

        /** Tem barreira (paredes com integridade)? Domínio Simples não conta. */
        public boolean closed() { return !open && !simple(); }

        /** É um Domínio Simples (pseudo-lei {@link #SIMPLE})? */
        public boolean simple() { return laws.contains(SIMPLE); }

        /**
         * A barreira deste domínio pode ser danificada por um ataque vindo deste lado? Normal: só de FORA; invertida:
         * só de DENTRO; aberto/simples: nunca (não há parede).
         */
        public boolean breaksFrom(boolean fromInside) {
            return closed() && (inverted ? fromInside : !fromInside);
        }

        /** O ponto está dentro da esfera do domínio? */
        public boolean contains(Vec3 p) {
            return p.distanceToSqr(center) <= (double) radius * radius;
        }

        /** A entidade (centro do corpo, com folga da largura) está dentro? */
        public boolean contains(Entity e) {
            double r = radius + e.getBbWidth() * 0.5;
            return e.level() == level && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= r * r;
        }

        /** Distância do ponto até a parede (casca da esfera); 0 = em cima da parede. */
        public double wallDistance(Vec3 p) {
            return Math.abs(p.distanceTo(center) - radius);
        }

        /** Ticks desde que abriu. */
        public long age() { return Math.max(0, level.getGameTime() - openedTick); }

        /** {@code e} é inimigo do dono (mesma regra do domo: chefes só brigam com jogadores, bichos deles e quem os ataca). */
        public boolean isEnemy(LivingEntity e) { return Domains.isEnemy(this, e); }

        /**
         * Este domínio ALCANÇA {@code e}? Inimigo, dentro, e não protegido pela barreira fechada de um domínio menor de
         * outro dono ({@link Domains#shielded}). É a regra que o núcleo usa antes de chamar {@code onEnemyTick}.
         */
        public boolean canReach(LivingEntity e) {
            return e != null && e.isAlive() && contains(e) && isEnemy(e) && !Domains.shielded(e, this);
        }

        /** Inimigos vivos dentro do domínio agora (inclui os protegidos por barreiras menores; veja {@link #reachableEnemies}). */
        public List<LivingEntity> enemiesInside() {
            AABB box = new AABB(center, center).inflate(radius + 2);
            return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && contains(e) && isEnemy(e));
        }

        /** Inimigos que este domínio pode atingir agora ({@link #canReach}): use para efeitos de lei e Acerto Garantido. */
        public List<LivingEntity> reachableEnemies() {
            List<LivingEntity> out = new ArrayList<>();
            for (LivingEntity e : enemiesInside()) if (!Domains.shielded(e, this)) out.add(e);
            return out;
        }
    }

    /** O que uma lei faz. Todos os métodos são opcionais; exceções são registradas no log sem derrubar o domínio. */
    public interface LawHandler {
        default void onOpen(Info d) {}
        /** Todo tick em que o módulo domain chama {@link #ticked} ({@code age} = ticks desde que abriu). */
        default void onTick(Info d, int age) {}
        default void onClose(Info d) {}
        /**
         * Para cada inimigo que o domínio ALCANÇA ({@link Info#canReach}: dentro e não protegido por uma barreira menor),
         * no mesmo tick de {@link #onTick}.
         */
        default void onEnemyTick(Info d, LivingEntity e, int age) {}
    }

    /**
     * Integridade das barreiras (o módulo domain implementa e registra com {@link #setBarrierHandler}). O núcleo só
     * encaminha os golpes na parede ({@link #hitBarrier}) depois de aplicar a regra dos lados.
     */
    public interface BarrierHandler {
        /** Integridade atual da barreira do domínio {@code domainId} (0 se não existe). */
        float integrity(int domainId);

        float maxIntegrity(int domainId);

        /**
         * Tira {@code amount} de integridade. Só é chamado para o lado que PODE quebrar (normal: de fora; invertida: de
         * dentro) — {@code fromInside} serve para o visual. Ao zerar, o módulo despedaça o domínio (e chama
         * {@link Domains#closed}). {@code attacker} é o que foi passado ao {@link Domains#hitBarrier} (pode ser null).
         *
         * @return integridade realmente tirada (0 = nada)
         */
        float damage(int domainId, float amount, boolean fromInside, @Nullable Entity attacker);
    }

    /** Registra o handler de uma lei (no init do módulo). Registrar de novo substitui. */
    public static void registerLaw(ResourceLocation lawId, LawHandler handler) {
        DomainRegistry.register(lawId, handler);
    }

    @Nullable
    public static LawHandler handler(ResourceLocation lawId) {
        return DomainRegistry.handler(lawId);
    }

    /** Chamado pelo módulo domain quando o domínio fica ativo: guarda e chama {@code onOpen} das leis. */
    public static void opened(Info d) {
        DomainRegistry.opened(d);
    }

    /** Chamado pelo módulo domain a cada tick ativo: atualiza e chama {@code onTick} + {@code onEnemyTick}. */
    public static void ticked(Info d, int age) {
        DomainRegistry.ticked(d, age);
    }

    /** Chamado pelo módulo domain quando o domínio fecha/quebra: chama {@code onClose} (uma vez) e esquece. */
    public static void closed(Info d) {
        DomainRegistry.closed(d);
    }

    /** Domínios abertos nesta dimensão (inclui Domínios Simples: veja {@link Info#simple()}). */
    public static List<Info> active(ServerLevel l) {
        return DomainRegistry.active(l);
    }

    /** O domínio (o mais recente) cuja esfera contém {@code pos}, ou null. */
    @Nullable
    public static Info at(ServerLevel l, Vec3 pos) {
        return DomainRegistry.at(l, pos);
    }

    /**
     * {@code target} sofre Acerto Garantido? Há um domínio INIMIGO com {@code law_sure_hit} que o contém e o ALCANÇA (o
     * alvo não está protegido pela barreira fechada de um domínio menor de outro dono — o mais interno manda) e o alvo
     * não está num Domínio Simples seu ou de aliado. Use para furar defesas absolutas (Infinito).
     */
    public static boolean sureHitOn(LivingEntity target) {
        return DomainRegistry.sureHitOn(target);
    }

    /** Regra de inimigo do domínio {@code d} (ver {@link Info#isEnemy}). */
    public static boolean isEnemy(Info d, LivingEntity e) {
        return DomainRegistry.isEnemy(d, e);
    }

    // ------------------------------------------------------------------ barreira (§11.1)

    /** O módulo domain registra quem guarda a integridade das barreiras (null = remove). */
    public static void setBarrierHandler(@Nullable BarrierHandler h) {
        DomainRegistry.setBarrierHandler(h);
    }

    @Nullable
    public static BarrierHandler barrierHandler() {
        return DomainRegistry.barrierHandler();
    }

    /**
     * Um ataque acertou {@code hitPos}. Acha o domínio FECHADO cuja parede está a ≤ {@link #WALL_TOLERANCE} de
     * {@code hitPos}; o lado é o de {@code attackerPos} (dentro = distância ao centro &lt; raio). Aplica a regra: normal
     * só quebra de FORA, invertida só de DENTRO. Do lado que não quebra, o núcleo já faz faíscas, som abafado e o aviso
     * na barra de ação do jogador atacante ("A barreira não cede por dentro..."). O dono do domínio e os aliados dele
     * nunca danificam a própria barreira.
     *
     * @param attacker quem atacou (ou o projétil: o dono dele é usado na regra de aliados); null = sem autor (explosão)
     * @return integridade tirada (0 = a barreira aguentou, não havia parede ali ou nenhum módulo registrou o handler)
     */
    public static float hitBarrier(ServerLevel l, Vec3 hitPos, Vec3 attackerPos, @Nullable Entity attacker, float amount) {
        return DomainRegistry.hitBarrier(l, hitPos, attackerPos, attacker, amount);
    }

    /** O domínio FECHADO cuja parede está a ≤ {@link #WALL_TOLERANCE} de {@code pos} (a mais próxima), ou null. */
    @Nullable
    public static Info barrierAt(ServerLevel l, Vec3 pos) {
        return DomainRegistry.barrierAt(l, pos);
    }

    /**
     * O domínio mais interno em {@code pos}: o FECHADO de menor raio que contém {@code pos}, ou (se não houver fechado)
     * o aberto de menor raio. Domínios Simples não contam. Null = fora de qualquer domínio.
     */
    @Nullable
    public static Info innermost(ServerLevel l, Vec3 pos) {
        return DomainRegistry.innermost(l, pos);
    }

    /**
     * {@code target} está protegido de {@code attackerDomain}: dentro da barreira FECHADA (normal ou invertida) de um
     * domínio MENOR de outro dono. O maior só pode atacar essa barreira por fora (e quem está fora dela).
     */
    public static boolean shielded(LivingEntity target, Info attackerDomain) {
        return DomainRegistry.shielded(target, attackerDomain);
    }

    private Domains() {}
}
