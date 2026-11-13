package com.renanpontes.isekaimahou.core.api;

import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.status.DestructionQueue;
import com.renanpontes.isekaimahou.core.status.DestructionRules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Destruição de terreno por magias (v0.8) — "destruir o mundo lutando", com regra para ligar/desligar.
 *
 * <p>Todas as formas ENFILEIRAM os blocos numa fila global do servidor, processada no fim de cada tick do centro para
 * fora (até {@code MahouConfig.DESTRUCTION_BLOCKS_PER_TICK} blocos por tick; fila máx. 250 000). O retorno é o número
 * de blocos enfileirados (0 = nada, por exemplo quando a destruição está desligada). Chame no servidor.
 *
 * <p>Regras (checadas aqui, ninguém precisa repetir): regra de jogo {@code mahouSpellDestruction} (padrão ligada;
 * {@code /gamerule mahouSpellDestruction false} ou {@code /mahou destruction off}), config
 * {@code spellBlockDestruction}, {@code mobGriefing} para conjuradores que não são jogadores, modo aventura/espectador.
 * Nunca destrói: bedrock e blocos inquebráveis, {@code #minecraft:wither_immune}, {@code #minecraft:dragon_immune},
 * blocos com inventário/dados (a menos que a config permita), a dimensão das Provações, lugares seguros
 * ({@link Places}), áreas de {@link #addProtection} e onde o jogador conjurador não pode mexer (proteção do spawn).
 *
 * <pre>
 * Destruction.crater(level, impacto, 12F, ctx.caster());                                  // cratera com borda queimada
 * Destruction.line(level, de, ate, 0.8F, 4F, ctx.caster(), Destruction.Mode.ERASE);        // corte fino no terreno
 * Destruction.cylinder(level, chao, 6F, 320F, ctx.caster(), Destruction.Mode.ERASE);      // coluna até o céu
 * Destruction.surface(level, centro, 10F, ctx.caster(), Destruction.Mode.FREEZE);          // congela a superfície
 * </pre>
 */
public final class Destruction {

    /** Como os blocos atingidos mudam. */
    public enum Mode {
        /** Somem, sem drops. */
        ERASE,
        /** Quebram: 10% dos blocos deixam cair os drops normais. */
        BREAK,
        /** Queimam: pedra vira pedra-negra/basalto, terra vira terra grossa/magma, plantas e madeira somem; fogo na superfície. */
        SCORCH,
        /** Vitrificam: areia vira vidro, pedra vira obsidiana chorona/vidro tingido; plantas somem. */
        VITRIFY,
        /** Congelam: água vira gelo, lava vira obsidiana, fogo apaga, superfície ganha neve. */
        FREEZE,
        /** Esmagam: viram cascalho/pedregulho e a camada de cima afunda 1 bloco. */
        CRUSH
    }

    /** Regra de jogo {@code mahouSpellDestruction} (null antes do carregamento comum do mod). */
    @Nullable
    public static GameRules.Key<GameRules.BooleanValue> rule() {
        return DestructionRules.rule();
    }

    /** Este conjurador pode destruir blocos neste nível agora? (regra + config + mobGriefing + modo de jogo + dimensão) */
    public static boolean allowed(ServerLevel level, @Nullable Entity caster) {
        return DestructionRules.allowed(level, caster);
    }

    /** O bloco nesta posição está protegido contra este conjurador? (bloco inquebrável/com dados, lugar seguro, proteção...) */
    public static boolean protectedAt(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        return DestructionRules.protectedAt(level, pos, caster);
    }

    /** Área protegida extra (ex.: arena de um módulo). O predicado é chamado na thread do servidor, por bloco: seja rápido. */
    public static void addProtection(BiPredicate<ServerLevel, BlockPos> p) {
        DestructionRules.addProtection(p);
    }

    /**
     * v1.0 r9: área INDESTRUTÍVEL (torre durante a escalada, masmorra). Além de barrar a Destruction, as magias que
     * mexem em blocos (temporários ou não — muros de terra, gelo, quebrar/colocar) consultam {@link #warded} e não agem
     * ali. Predicado chamado por bloco na thread do servidor: seja rápido.
     */
    public static void addWard(BiPredicate<ServerLevel, BlockPos> p) {
        DestructionRules.addWard(p);
    }

    /** A posição está numa área indestrutível (ver {@link #addWard})? */
    public static boolean warded(ServerLevel level, BlockPos pos) {
        return DestructionRules.warded(level, pos);
    }

    /** Área indestrutível para este conjurador? (jogador no criativo pode tudo — depuração) */
    public static boolean wardedFor(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (caster instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().instabuild) return false;
        return DestructionRules.warded(level, pos);
    }

    /** Esfera de raio {@code radius} (máx. 64) em volta de {@code center}. */
    public static int sphere(ServerLevel l, Vec3 center, float radius, @Nullable Entity caster, Mode mode) {
        return DestructionQueue.sphere(l, center, radius, caster, mode);
    }

    /**
     * Cratera de verdade: tigela (raio r, profundidade r·0,6) apagada a partir do chão abaixo de {@code center}, morros e
     * árvores em cima também somem, borda queimada com fogo em 15% dela, escombros voando e tremor de câmera.
     */
    public static int crater(ServerLevel l, Vec3 center, float radius, @Nullable Entity caster) {
        return DestructionQueue.crater(l, center, radius, caster);
    }

    /** Cilindro vertical a partir de {@code base}: altura positiva sobe, negativa desce (limitado à altura do mundo). */
    public static int cylinder(ServerLevel l, Vec3 base, float radius, float height, @Nullable Entity caster, Mode mode) {
        return DestructionQueue.cylinder(l, base, radius, height, caster, mode);
    }

    /** Corte/túnel de {@code from} até {@code to}: seção retangular de largura (horizontal) × altura (vertical). */
    public static int line(ServerLevel l, Vec3 from, Vec3 to, float width, float height, @Nullable Entity caster, Mode mode) {
        return DestructionQueue.line(l, from, to, width, height, caster, mode);
    }

    /** Só a camada de cima do terreno num raio (queimar, congelar, vitrificar o chão). */
    public static int surface(ServerLevel l, Vec3 center, float radius, @Nullable Entity caster, Mode mode) {
        return DestructionQueue.surface(l, center, radius, caster, mode);
    }

    /** Blocos esperando na fila global. */
    public static int queued() {
        return DestructionQueue.queued();
    }

    private Destruction() {}
}
