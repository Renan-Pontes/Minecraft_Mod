package com.renanpontes.isekaimahou.client;

import java.util.List;

import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;

/** Lista das partes de cliente de todos os modulos. */
public final class ClientModules {
    public static final List<ClientModule> ALL = List.of(
            new com.renanpontes.isekaimahou.feature.forms_a.client.FormsAClient(),
            new com.renanpontes.isekaimahou.feature.forms_b.client.FormsBClient(),
            new com.renanpontes.isekaimahou.feature.arcane.client.ArcaneClient(),
            new com.renanpontes.isekaimahou.feature.fire_water.client.FireWaterClient(),
            new com.renanpontes.isekaimahou.feature.earth_wind.client.EarthWindClient(),
            new com.renanpontes.isekaimahou.feature.light_shadow.client.LightShadowClient(),
            new com.renanpontes.isekaimahou.feature.fusions_a.client.FusionsAClient(),
            new com.renanpontes.isekaimahou.feature.fusions_b.client.FusionsBClient(),
            new com.renanpontes.isekaimahou.feature.origins_a.client.OriginsAClient(),
            new com.renanpontes.isekaimahou.feature.origins_b.client.OriginsBClient(),
            new com.renanpontes.isekaimahou.feature.domain.client.DomainClient(),
            new com.renanpontes.isekaimahou.feature.ui_grimoire.client.UiGrimoireClient(),
            new com.renanpontes.isekaimahou.feature.ui_hud.client.UiHudClient(),
            new com.renanpontes.isekaimahou.feature.cinematic.client.CinematicClient(),
            new com.renanpontes.isekaimahou.feature.paths.client.PathsClient(),
            new com.renanpontes.isekaimahou.feature.ascension.client.AscensionClient(),
            new com.renanpontes.isekaimahou.feature.ritual_mage.client.RitualMageClient(),
            new com.renanpontes.isekaimahou.feature.ritual_witch.client.RitualWitchClient(),
            new com.renanpontes.isekaimahou.feature.ritual_warlock.client.RitualWarlockClient(),
            new com.renanpontes.isekaimahou.feature.ritual_druid.client.RitualDruidClient(),
            new com.renanpontes.isekaimahou.feature.ritual_necro.client.RitualNecroClient(),
            new com.renanpontes.isekaimahou.feature.outfits.client.OutfitsClient(),
            new com.renanpontes.isekaimahou.feature.creatures_a.client.CreaturesAClient(),
            new com.renanpontes.isekaimahou.feature.creatures_b.client.CreaturesBClient(),
            new com.renanpontes.isekaimahou.feature.creatures_c.client.CreaturesCClient(),
            new com.renanpontes.isekaimahou.feature.creatures_d.client.CreaturesDClient(),
            new com.renanpontes.isekaimahou.feature.creatures_e.client.CreaturesEClient(),
            new com.renanpontes.isekaimahou.feature.guild.client.GuildClient(),
            new com.renanpontes.isekaimahou.feature.structures_a.client.StructuresAClient(),
            new com.renanpontes.isekaimahou.feature.structures_b.client.StructuresBClient(),
            new com.renanpontes.isekaimahou.feature.structures_c.client.StructuresCClient(),
            new com.renanpontes.isekaimahou.feature.dimensions.client.DimensionsClient(),
            new com.renanpontes.isekaimahou.feature.bosses_a.client.BossesAClient(),
            new com.renanpontes.isekaimahou.feature.bosses_b.client.BossesBClient(),
            new com.renanpontes.isekaimahou.feature.bosses_c.client.BossesCClient(),
            new com.renanpontes.isekaimahou.feature.mounts.client.MountsClient(),
            new com.renanpontes.isekaimahou.feature.equipment.client.EquipmentClient(),
            new com.renanpontes.isekaimahou.feature.journal.client.JournalClient(),
            new com.renanpontes.isekaimahou.feature.trials.client.TrialsClient(),
            new com.renanpontes.isekaimahou.feature.bosses_d.client.BossesDClient(),
            new com.renanpontes.isekaimahou.feature.legends_a.client.LegendsAClient(),
            new com.renanpontes.isekaimahou.feature.legends_b.client.LegendsBClient(),
            new com.renanpontes.isekaimahou.feature.automation.client.AutomationClient(),
            new com.renanpontes.isekaimahou.feature.armory.client.ArmoryClient(),
            new com.renanpontes.isekaimahou.feature.cities.client.CitiesClient(),
            new com.renanpontes.isekaimahou.feature.legends_c.client.LegendsCClient(),
            new com.renanpontes.isekaimahou.feature.legends_d.client.LegendsDClient(),
            new com.renanpontes.isekaimahou.feature.legends_e.client.LegendsEClient(),
            new com.renanpontes.isekaimahou.feature.ultimates.client.UltimatesClient(),
            new com.renanpontes.isekaimahou.feature.fusions_c.client.FusionsCClient(),
            new com.renanpontes.isekaimahou.feature.fusions_d.client.FusionsDClient(),
            new com.renanpontes.isekaimahou.feature.arcane_duel.client.ArcaneDuelClient(),
            new com.renanpontes.isekaimahou.feature.bosses_e.client.BossesEClient(),
            new com.renanpontes.isekaimahou.feature.bosses_f.client.BossesFClient(),
            new com.renanpontes.isekaimahou.feature.world_v09.client.WorldV09Client(),
            new com.renanpontes.isekaimahou.feature.travel.client.TravelClient(),
            new com.renanpontes.isekaimahou.feature.character.client.CharacterClient(),
            new com.renanpontes.isekaimahou.feature.bosses_g.client.BossesGClient(),
            new com.renanpontes.isekaimahou.feature.class_smith.client.ClassSmithClient(),
            new com.renanpontes.isekaimahou.feature.class_rogue.client.ClassRogueClient(),
            new com.renanpontes.isekaimahou.feature.class_knight.client.ClassKnightClient(),
            new com.renanpontes.isekaimahou.feature.class_enchanter.client.ClassEnchanterClient(),
            new com.renanpontes.isekaimahou.feature.class_shinigami.client.ClassShinigamiClient(),
            new com.renanpontes.isekaimahou.feature.class_quincy.client.ClassQuincyClient(),
            new com.renanpontes.isekaimahou.feature.class_gunslinger.client.ClassGunslingerClient(),
            new com.renanpontes.isekaimahou.feature.class_ranger.client.ClassRangerClient(),
            new com.renanpontes.isekaimahou.feature.class_gambler.client.ClassGamblerClient(),
            new com.renanpontes.isekaimahou.feature.class_steelball.client.ClassSteelballClient(),
            new com.renanpontes.isekaimahou.feature.settlements.client.SettlementsClient(),
            new com.renanpontes.isekaimahou.feature.hueco_mundo.client.HuecoMundoClient(),
            new com.renanpontes.isekaimahou.feature.farm.client.FarmClient(),
            new com.renanpontes.isekaimahou.feature.potions.client.PotionsClient(),
            new com.renanpontes.isekaimahou.feature.legends_f.client.LegendsFClient(),
            new com.renanpontes.isekaimahou.feature.afterlife.client.AfterlifeClient());

    public static void initAll(IEventBus modBus) {
        for (ClientModule m : ALL) m.initClient(modBus);
    }

    private ClientModules() {}
}
