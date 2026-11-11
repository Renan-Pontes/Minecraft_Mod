package com.renanpontes.isekaimahou.module;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.neoforged.bus.api.IEventBus;

/** Lista de todos os modulos (lado comum). A ordem importa pouco: cada modulo e independente. */
public final class Modules {
    public static final List<Module> ALL = List.of(
            new com.renanpontes.isekaimahou.feature.forms_a.FormsAModule(),
            new com.renanpontes.isekaimahou.feature.forms_b.FormsBModule(),
            new com.renanpontes.isekaimahou.feature.arcane.ArcaneModule(),
            new com.renanpontes.isekaimahou.feature.fire_water.FireWaterModule(),
            new com.renanpontes.isekaimahou.feature.earth_wind.EarthWindModule(),
            new com.renanpontes.isekaimahou.feature.light_shadow.LightShadowModule(),
            new com.renanpontes.isekaimahou.feature.fusions_a.FusionsAModule(),
            new com.renanpontes.isekaimahou.feature.fusions_b.FusionsBModule(),
            new com.renanpontes.isekaimahou.feature.origins_a.OriginsAModule(),
            new com.renanpontes.isekaimahou.feature.origins_b.OriginsBModule(),
            new com.renanpontes.isekaimahou.feature.domain.DomainModule(),
            new com.renanpontes.isekaimahou.feature.ui_grimoire.UiGrimoireModule(),
            new com.renanpontes.isekaimahou.feature.ui_hud.UiHudModule(),
            new com.renanpontes.isekaimahou.feature.cinematic.CinematicModule(),
            new com.renanpontes.isekaimahou.feature.paths.PathsModule(),
            new com.renanpontes.isekaimahou.feature.ascension.AscensionModule(),
            new com.renanpontes.isekaimahou.feature.ritual_mage.RitualMageModule(),
            new com.renanpontes.isekaimahou.feature.ritual_witch.RitualWitchModule(),
            new com.renanpontes.isekaimahou.feature.ritual_warlock.RitualWarlockModule(),
            new com.renanpontes.isekaimahou.feature.ritual_druid.RitualDruidModule(),
            new com.renanpontes.isekaimahou.feature.ritual_necro.RitualNecroModule(),
            new com.renanpontes.isekaimahou.feature.outfits.OutfitsModule(),
            new com.renanpontes.isekaimahou.feature.creatures_a.CreaturesAModule(),
            new com.renanpontes.isekaimahou.feature.creatures_b.CreaturesBModule(),
            new com.renanpontes.isekaimahou.feature.creatures_c.CreaturesCModule(),
            new com.renanpontes.isekaimahou.feature.creatures_d.CreaturesDModule(),
            new com.renanpontes.isekaimahou.feature.creatures_e.CreaturesEModule(),
            new com.renanpontes.isekaimahou.feature.guild.GuildModule(),
            new com.renanpontes.isekaimahou.feature.structures_a.StructuresAModule(),
            new com.renanpontes.isekaimahou.feature.structures_b.StructuresBModule(),
            new com.renanpontes.isekaimahou.feature.structures_c.StructuresCModule(),
            new com.renanpontes.isekaimahou.feature.dimensions.DimensionsModule(),
            new com.renanpontes.isekaimahou.feature.bosses_a.BossesAModule(),
            new com.renanpontes.isekaimahou.feature.bosses_b.BossesBModule(),
            new com.renanpontes.isekaimahou.feature.bosses_c.BossesCModule(),
            new com.renanpontes.isekaimahou.feature.mounts.MountsModule(),
            new com.renanpontes.isekaimahou.feature.equipment.EquipmentModule(),
            new com.renanpontes.isekaimahou.feature.journal.JournalModule(),
            new com.renanpontes.isekaimahou.feature.trials.TrialsModule(),
            new com.renanpontes.isekaimahou.feature.bosses_d.BossesDModule(),
            new com.renanpontes.isekaimahou.feature.legends_a.LegendsAModule(),
            new com.renanpontes.isekaimahou.feature.legends_b.LegendsBModule(),
            new com.renanpontes.isekaimahou.feature.automation.AutomationModule(),
            new com.renanpontes.isekaimahou.feature.armory.ArmoryModule(),
            new com.renanpontes.isekaimahou.feature.cities.CitiesModule(),
            new com.renanpontes.isekaimahou.feature.legends_c.LegendsCModule(),
            new com.renanpontes.isekaimahou.feature.legends_d.LegendsDModule(),
            new com.renanpontes.isekaimahou.feature.legends_e.LegendsEModule(),
            new com.renanpontes.isekaimahou.feature.ultimates.UltimatesModule(),
            new com.renanpontes.isekaimahou.feature.fusions_c.FusionsCModule(),
            new com.renanpontes.isekaimahou.feature.fusions_d.FusionsDModule(),
            new com.renanpontes.isekaimahou.feature.arcane_duel.ArcaneDuelModule(),
            new com.renanpontes.isekaimahou.feature.bosses_e.BossesEModule(),
            new com.renanpontes.isekaimahou.feature.bosses_f.BossesFModule(),
            new com.renanpontes.isekaimahou.feature.world_v09.WorldV09Module(),
            new com.renanpontes.isekaimahou.feature.travel.TravelModule(),
            new com.renanpontes.isekaimahou.feature.character.CharacterModule(),
            new com.renanpontes.isekaimahou.feature.bosses_g.BossesGModule(),
            new com.renanpontes.isekaimahou.feature.class_smith.ClassSmithModule(),
            new com.renanpontes.isekaimahou.feature.class_rogue.ClassRogueModule(),
            new com.renanpontes.isekaimahou.feature.class_knight.ClassKnightModule(),
            new com.renanpontes.isekaimahou.feature.class_enchanter.ClassEnchanterModule(),
            new com.renanpontes.isekaimahou.feature.class_shinigami.ClassShinigamiModule(),
            new com.renanpontes.isekaimahou.feature.class_quincy.ClassQuincyModule(),
            new com.renanpontes.isekaimahou.feature.class_gunslinger.ClassGunslingerModule(),
            new com.renanpontes.isekaimahou.feature.class_ranger.ClassRangerModule(),
            new com.renanpontes.isekaimahou.feature.class_gambler.ClassGamblerModule(),
            new com.renanpontes.isekaimahou.feature.class_steelball.ClassSteelballModule(),
            new com.renanpontes.isekaimahou.feature.settlements.SettlementsModule(),
            new com.renanpontes.isekaimahou.feature.hueco_mundo.HuecoMundoModule(),
            new com.renanpontes.isekaimahou.feature.farm.FarmModule(),
            new com.renanpontes.isekaimahou.feature.potions.PotionsModule(),
            new com.renanpontes.isekaimahou.feature.legends_f.LegendsFModule(),
            new com.renanpontes.isekaimahou.feature.afterlife.AfterlifeModule());

    public static void initAll(IEventBus modBus) {
        for (Module m : ALL) {
            try {
                m.init(modBus);
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("Falha ao iniciar o modulo " + m.id(), e);
                throw e;
            }
        }
    }

    private Modules() {}
}
