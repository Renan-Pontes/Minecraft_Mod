package com.renanpontes.isekaimahou.feature.auto_glyphs.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.auto_glyphs.AreaProbe;
import com.renanpontes.isekaimahou.feature.automation.PedestalCondition;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gatilhos de PEDESTAL (v1.5): vão no começo da receita. O Pedestal Rúnico confere a condição ANTES de conjurar (sem
 * gastar mana) e espera ("Aguardando condição") enquanto ela for falsa. Vários gatilhos = TODOS precisam valer.
 * <p>Na mão: a condição é conferida em volta de você; se for falsa, a magia não sai (nem gasta mana) e a barra de ação
 * avisa qual condição faltou.
 * <p>A área conferida é a da magia: centro no pedestal (ou no chão sob você), raio do maior efeito da receita, 2 camadas
 * para cima e para baixo.
 */
public final class PedestalTriggers {
    public static final String ON_RIPE = "on_ripe";
    public static final String ON_EMPTY = "on_empty";
    public static final String ON_ITEMS = "on_items";
    public static final String ON_WORK = "on_work";
    public static final String ON_NIGHT = "on_night";
    public static final String ON_DAY = "on_day";
    public static final String ON_MONSTERS = "on_monsters";

    /** Base: gatilho colocável que é também uma condição de pedestal. */
    public abstract static class Condition extends TriggerGlyph implements PedestalCondition {
        protected Condition(String id, int cost, int tier) {
            super(id, cost, true);
            element(Element.ARCANE);
            tier(tier);
        }

        @Override
        public final boolean ready(ServerLevel level, BlockPos pedestal, Vec3 origin, ParsedSpell spell) {
            try {
                return test(level, AreaProbe.center(origin), AreaProbe.radius(spell), pedestal, spell);
            } catch (RuntimeException ex) {
                // Uma condição com defeito não pode travar o pedestal: deixa conjurar
                IsekaiMahou.LOGGER.error("[auto_glyphs] condição {} falhou em {}", id(), pedestal, ex);
                return true;
            }
        }

        /**
         * @param center   centro da área (pedestal / chão sob o jogador)
         * @param r        raio da magia
         * @param pedestal posição do pedestal (na mão: a do jogador)
         */
        protected abstract boolean test(ServerLevel level, BlockPos center, int r, BlockPos pedestal, ParsedSpell spell);
    }

    /** Ao Amadurecer: há planta madura na área. */
    public static final class OnRipe extends Condition {
        public OnRipe() { super(ON_RIPE, 2, 1); }

        @Override
        protected boolean test(ServerLevel level, BlockPos center, int r, BlockPos pedestal, ParsedSpell spell) {
            return AreaProbe.anyRipe(level, center, r);
        }
    }

    /** Ao Esvaziar: há terra arada / areia das almas vazia na área. */
    public static final class OnEmpty extends Condition {
        public OnEmpty() { super(ON_EMPTY, 2, 1); }

        @Override
        protected boolean test(ServerLevel level, BlockPos center, int r, BlockPos pedestal, ParsedSpell spell) {
            return AreaProbe.anyEmptySoil(level, center, r);
        }
    }

    /** Com Itens no Chão: há itens soltos na área (par perfeito do Recolher e da Forja Arcana). */
    public static final class OnItems extends Condition {
        public OnItems() { super(ON_ITEMS, 2, 1); }

        @Override
        protected boolean test(ServerLevel level, BlockPos center, int r, BlockPos pedestal, ParsedSpell spell) {
            return AreaProbe.anyItems(level, center, r);
        }
    }

    /** Quando Houver Trabalho: algum efeito da receita tem o que fazer (Pedreira não terminou, há árvore, peixe...). */
    public static final class OnWork extends Condition {
        public OnWork() { super(ON_WORK, 3, 2); }

        @Override
        protected boolean test(ServerLevel level, BlockPos center, int r, BlockPos pedestal, ParsedSpell spell) {
            return AreaProbe.hasWork(level, center, isPedestal(level, pedestal) ? pedestal : null, spell);
        }
    }

    /** Ao Anoitecer: só de noite. */
    public static final class OnNight extends Condition {
        public OnNight() { super(ON_NIGHT, 2, 2); }

        @Override
        protected boolean test(ServerLevel level, BlockPos center, int r, BlockPos pedestal, ParsedSpell spell) {
            return !level.dimensionType().hasFixedTime() && level.isNight();
        }
    }

    /** Ao Amanhecer: só de dia. */
    public static final class OnDay extends Condition {
        public OnDay() { super(ON_DAY, 2, 2); }

        @Override
        protected boolean test(ServerLevel level, BlockPos center, int r, BlockPos pedestal, ParsedSpell spell) {
            return !level.dimensionType().hasFixedTime() && level.isDay();
        }
    }

    /** Com Monstros Perto: há um monstro (sem dono) na área + 2 blocos. */
    public static final class OnMonsters extends Condition {
        public OnMonsters() { super(ON_MONSTERS, 3, 2); }

        @Override
        protected boolean test(ServerLevel level, BlockPos center, int r, BlockPos pedestal, ParsedSpell spell) {
            AABB box = new AABB(center).inflate(r + 2, 4, r + 2);
            return !level.getEntitiesOfClass(Mob.class, box, PedestalTriggers::hostile).isEmpty();
        }
    }

    private static boolean hostile(Mob m) {
        if (!m.isAlive() || !(m instanceof Enemy)) return false;
        return !(m instanceof OwnableEntity own) || own.getOwnerUUID() == null;
    }

    /** A posição é mesmo de um Pedestal Rúnico? (na mão, passamos a posição do jogador) */
    private static boolean isPedestal(ServerLevel level, @Nullable BlockPos pos) {
        return pos != null && level.isLoaded(pos)
                && level.getBlockEntity(pos) instanceof com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
    }

    private PedestalTriggers() {}
}
