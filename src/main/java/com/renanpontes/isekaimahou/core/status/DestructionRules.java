package com.renanpontes.isekaimahou.core.status;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/** Regras da destruição de terreno ({@code core/api/Destruction}): regra de jogo, permissões e proteções. */
public final class DestructionRules {
    public static final String RULE_NAME = "mahouSpellDestruction";
    /** Dimensão das Câmaras de Provação: a arena nunca é destruída. */
    public static final ResourceLocation TRIAL_REALM = IsekaiMahou.id("trial_realm");

    private static volatile GameRules.Key<GameRules.BooleanValue> rule;
    private static final List<BiPredicate<ServerLevel, BlockPos>> PROTECTIONS = new CopyOnWriteArrayList<>();

    /**
     * Registra a regra {@code mahouSpellDestruction} (padrão ligada). Chamado UMA vez na thread principal (setup comum),
     * antes de qualquer mundo existir — o mapa de regras do Minecraft não é seguro para registro em paralelo.
     */
    static synchronized void registerRule() {
        if (rule == null) {
            rule = GameRules.register(RULE_NAME, GameRules.Category.MISC, GameRules.BooleanValue.create(true));
        }
    }

    @Nullable
    public static GameRules.Key<GameRules.BooleanValue> rule() {
        return rule;
    }

    /** A regra de jogo está ligada? (ligada se ainda não foi registrada) */
    public static boolean ruleOn(ServerLevel level) {
        GameRules.Key<GameRules.BooleanValue> k = rule;
        return k == null || level.getGameRules().getBoolean(k);
    }

    public static boolean allowed(ServerLevel level, @Nullable Entity caster) {
        if (level == null || !MahouConfig.SPELL_BLOCK_DESTRUCTION.get() || !ruleOn(level)) return false;
        if (level.dimension().location().equals(TRIAL_REALM)) return false;
        if (caster instanceof Player p) {
            if (p.isSpectator()) return false;
            return p.getAbilities().instabuild || p.mayBuild();
        }
        // Mobs, invocações e projéteis: regra mobGriefing (o evento do NeoForge deixa mods de proteção vetarem)
        return caster == null || EventHooks.canEntityGrief(level, caster);
    }

    public static boolean protectedAt(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (level == null || pos == null) return true;
        if (level.dimension().location().equals(TRIAL_REALM) || level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) return true;
        if (blockProtected(level, pos, level.getBlockState(pos))) return true;
        if (areaProtected(level, pos)) return true;
        return caster instanceof Player p && !level.mayInteract(p, pos);
    }

    /** O próprio bloco é intocável? (inquebrável, imune ao Wither/Dragão, bloco com dados se a config não deixa) */
    public static boolean blockProtected(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.getDestroySpeed(level, pos) < 0) return true;
        if (st.is(BlockTags.WITHER_IMMUNE) || st.is(BlockTags.DRAGON_IMMUNE)) return true;
        return st.hasBlockEntity() && !MahouConfig.DESTRUCTION_BLOCK_ENTITIES.get();
    }

    /** Área protegida: lugar seguro ({@code Places}) ou uma proteção registrada por módulo. */
    public static boolean areaProtected(ServerLevel level, BlockPos pos) {
        if (PlaceData.safeAt(level, pos)) return true;
        return customProtected(level, pos);
    }

    static boolean customProtected(ServerLevel level, BlockPos pos) {
        for (BiPredicate<ServerLevel, BlockPos> p : PROTECTIONS) {
            try {
                if (p.test(level, pos)) return true;
            } catch (RuntimeException ex) {
                // Proteção com defeito: protege (melhor não destruir do que derrubar o servidor)
                IsekaiMahou.LOGGER.error("[destruction] proteção registrada falhou em {}", pos, ex);
                return true;
            }
        }
        return false;
    }

    static boolean hasCustomProtections() {
        return !PROTECTIONS.isEmpty();
    }

    public static void addProtection(BiPredicate<ServerLevel, BlockPos> p) {
        if (p != null) PROTECTIONS.add(p);
    }

    private DestructionRules() {}
}
