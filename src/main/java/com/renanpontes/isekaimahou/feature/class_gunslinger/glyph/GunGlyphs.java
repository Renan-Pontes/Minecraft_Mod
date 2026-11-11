package com.renanpontes.isekaimahou.feature.class_gunslinger.glyph;

import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

/**
 * Os glifos de ORIGEM do Pistoleiro (exclusivos: outras classes só aprendem por Lição). Todos montáveis no Grimório
 * e combináveis com o resto do sistema (estágios, ligações, multi-elemento, aumentos do núcleo):
 * <ul>
 *   <li>Formas: Bala, Rajada de Balas, Tiro Carregado, Tiro Ricochete, Bala Perfurante e a G "Doze Badaladas".</li>
 *   <li>Efeitos: Munição Incendiária (Fogo), Munição Explosiva, Marcar Alvo.</li>
 *   <li>Aumento: Leque do Cão. Gatilho: Ao Recarregar.</li>
 *   <li>Técnicas de anime (v1.0): Espada de Fótons, Linha de Bala, Círculo de Mira, Camuflagem Óptica, Estrela Negra,
 *       Atirador de Elite, a G "Rajada Estelar", Dedo Espiritual e Duas Mãos.</li>
 * </ul>
 */
public final class GunGlyphs {
    public static void register() {
        GlyphRegistry.register(new GunForms.BulletForm());
        GlyphRegistry.register(new GunForms.BurstForm());
        GlyphRegistry.register(new GunForms.ChargedShotForm());
        GlyphRegistry.register(new GunForms.RicochetForm());
        GlyphRegistry.register(new GunForms.PiercingForm());
        GlyphRegistry.register(new HighNoonForm());
        GlyphRegistry.register(new GunEffects.IncendiaryEffect());
        GlyphRegistry.register(new GunEffects.ExplosiveEffect());
        GlyphRegistry.register(new GunEffects.MarkEffect());
        GlyphRegistry.register(new GunEffects.FanHammerAugment());
        GlyphRegistry.register(new GunEffects.ReloadTrigger());
        // v1.0 — técnicas de anime (homenagem Gun Gale Online + clássicos de pistoleiro; ver GsTechniques)
        GlyphRegistry.register(new GgoForms.PhotonSwordForm());
        GlyphRegistry.register(new GgoForms.BulletLineForm());
        GlyphRegistry.register(new GgoForms.AimCircleForm());
        GlyphRegistry.register(new GgoForms.OpticalCamoForm());
        GlyphRegistry.register(new GgoForms.BlackStarForm());
        GlyphRegistry.register(new GgoForms.AntiMaterielForm());
        GlyphRegistry.register(new GgoForms.StellarBurstForm());
        GlyphRegistry.register(new AnimeGunForms.SpiritGunForm());
        GlyphRegistry.register(new AnimeGunForms.TwoHandsForm());
    }

    private GunGlyphs() {}
}
