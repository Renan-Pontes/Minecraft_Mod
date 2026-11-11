package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

/**
 * Os glifos de ORIGEM do Shinigami, todos montáveis no Grimório e combináveis com o resto do sistema (estágios,
 * ligações, multi-elemento). Exclusivos: outras classes só aprendem por Lição.
 * <pre>
 *  Formas:   Zanjutsu (t1) · Shunpo (t1) · Hadō nº 4 Raio Branco (t1) · Hadō nº 31 Canhão Rubro (t2, C2)
 *            Presa Lunar (t2, C3) · Bakudō nº 81 Muro do Vazio (t3, C4)
 *            Bankai — forma G / ultimate (t5, C8)
 *  Efeitos:  Bakudō nº 61 Seis Grades de Luz (t2) · a assinatura do arquétipo (t2, uma por Shinigami, escolhida no
 *            Mundo Interior): Peso · Pétalas de Lâmina · Gelo · Chama Ancestral · Pincel Branco
 * </pre>
 * Técnicas avançadas (ver ShinigamiTechniques): Getsuga Tenshō (t3, C5) · Hadō nº 90 Kurohitsugi (t4, C7) ·
 * Máscara do Vazio (t4, C6) · Cero (t3, C6) · Vasto Lorde — forma G (t5, C9) · Mugetsu — forma G (t5, C9).
 * Cada Shinigami aprende 8 glifos + o efeito do seu arquétipo + a Bankai; os outros 4 efeitos de arquétipo só por Lição
 * de outro Shinigami (a lâmina de cada um é única).
 */
public final class ShinigamiGlyphs {
    public static void register() {
        GlyphRegistry.register(new ZanjutsuForm());
        GlyphRegistry.register(new ShunpoForm());
        GlyphRegistry.register(new HadoByakuraiForm());
        GlyphRegistry.register(new HadoShakkahoForm());
        GlyphRegistry.register(new MoonFangForm());
        GlyphRegistry.register(new BakudoDankuForm());
        GlyphRegistry.register(new BankaiForm());
        GlyphRegistry.register(new BakudoRikujoEffect());
        GlyphRegistry.register(new WeightEffect());
        GlyphRegistry.register(new PetalsEffect());
        GlyphRegistry.register(new FrostEffect());
        GlyphRegistry.register(new FlameEffect());
        GlyphRegistry.register(new WhiteBrushEffect());
        // técnicas avançadas (ShinigamiTechniques)
        GlyphRegistry.register(new GetsugaForm());
        GlyphRegistry.register(new HadoKurohitsugiForm());
        GlyphRegistry.register(new VisoredForm());
        GlyphRegistry.register(new CeroForm());
        GlyphRegistry.register(new VastoLordeForm());
        GlyphRegistry.register(new MugetsuForm());
    }

    private ShinigamiGlyphs() {}
}
