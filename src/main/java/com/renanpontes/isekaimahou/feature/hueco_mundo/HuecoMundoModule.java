package com.renanpontes.isekaimahou.feature.hueco_mundo;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmFlyer;
import com.renanpontes.isekaimahou.feature.hueco_mundo.glyph.HollowGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Módulo "hueco_mundo" (v1.0) — duas dimensões novas com chefes:
 * <ul>
 *   <li><b>Mundo Oco</b> ({@code isekai_mahou:hollow_world}, homenagem a Hueco Mundo): deserto de areia branca sob a lua
 *       crescente eterna, árvores de quartzo, ruínas e ossadas; Vazios (Rastejante, Brutamontes, Alado), Menos Gigantes
 *       e Arrancar de elite com nome; no centro o Palácio da Noite Eterna com o <b>Rei Vazio</b> e a <b>Imperatriz das
 *       Areias</b>. Entrada: a Fenda da Garganta no Mundo Normal (ritual noturno), a Chave da Garganta, ou os atalhos do
 *       Shinigami (Portão Senkai) e do Quincy (Ponte de Reishi).</li>
 *   <li><b>Reino das Fadas</b> ({@code isekai_mahou:fey_realm}): colinas no crepúsculo eterno, cogumelos que brilham;
 *       Fadas Travessas e Cães de Espinhos; a Corte das Fadas com a <b>Rainha dos Espinhos</b>. Entrada: o Círculo das
 *       Fadas no Mundo Normal.</li>
 * </ul>
 * Dimensões, biomas, estruturas, moldes, loot e traduções: {@code tools/gen/hueco_mundo.py}.
 */
public class HuecoMundoModule implements Module {
    @Override
    public String id() {
        return "hueco_mundo";
    }

    @Override
    public void init(IEventBus modBus) {
        HmRegistries.register(modBus);
        modBus.addListener(this::onCreativeTab);
        modBus.addListener(this::onSpawnPlacements);
        HollowGlyphs.register();
        HmEvents.register();
        HmCommands.register();
        // Palácio e Corte são sempre protegidos (arenas dos chefes); as estruturas dos altares de entrada também.
        BossArena.protectPlace(HmSites.PALACE_PLACE);
        BossArena.protectPlace(HmSites.COURT_PLACE);
        BossArena.protectStructure(IsekaiMahou.id(HmRealm.HOLLOW.overworldSite));
        BossArena.protectStructure(IsekaiMahou.id(HmRealm.FEY.overworldSite));
        registerResistances();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            HmRegistries.eggs().forEach(egg -> event.accept(egg.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            HmRegistries.worldItems().forEach(i -> event.accept(i.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            // Os pergaminhos dos glifos já entram pela aba do núcleo (todos os glifos registrados).
            HmRegistries.magicItems().forEach(i -> event.accept(i.get()));
        }
    }

    /**
     * Onde as criaturas nascem vem dos biomas (JSON). Aqui: luz não importa (os reinos são noite eterna/crepúsculo),
     * mas há teto de quantos do mesmo tipo podem existir perto (Menos e Arrancar são raros e fortes).
     */
    private void onSpawnPlacements(RegisterSpawnPlacementsEvent e) {
        ground(e, HmRegistries.HOLLOW_CRAWLER, 8);
        ground(e, HmRegistries.HOLLOW_BRUTE, 3);
        ground(e, HmRegistries.MENOS_GIANT, 1);
        ground(e, HmRegistries.ARRANCAR, 1);
        ground(e, HmRegistries.BRAMBLE_HOUND, 5);
        air(e, HmRegistries.HOLLOW_WRAITH, 4);
        air(e, HmRegistries.FEY_PIXIE, 6);
    }

    private static <T extends Mob> void ground(RegisterSpawnPlacementsEvent e, Supplier<EntityType<T>> type, int max) {
        e.register(type.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, rnd) -> ok(t, level, reason, pos, rnd, max) && Mob.checkMobSpawnRules(t, level, reason, pos, rnd),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static <T extends HmFlyer> void air(RegisterSpawnPlacementsEvent e, Supplier<EntityType<T>> type, int max) {
        e.register(type.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, rnd) -> ok(t, level, reason, pos, rnd, max),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static boolean ok(EntityType<?> t, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource rnd, int max) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return true;
        return level.getEntities(t, new AABB(pos).inflate(40), x -> true).size() < max;
    }

    /** Resistências/fraquezas elementais (dano de magia). Vazios: espíritos sombrios — a Luz os desfaz. */
    private static void registerResistances() {
        for (String id : new String[] {"hollow_crawler", "hollow_brute", "hollow_wraith", "menos_giant", "arrancar", "hollow_king", "sand_empress"}) {
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.SHADOW, 0.6F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.LIGHT, 1.35F);
        }
        ElementReactions.setMultiplier(IsekaiMahou.id("sand_empress"), Element.EARTH, 0.6F);
        ElementReactions.setMultiplier(IsekaiMahou.id("sand_empress"), Element.WATER, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("hollow_king"), Element.FIRE, 1.2F);
        for (String id : new String[] {"fey_pixie", "bramble_hound", "thorn_queen"}) {
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.FIRE, 1.4F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.EARTH, 0.7F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.LIGHT, 0.7F);
        }
    }
}
