package com.renanpontes.isekaimahou.glyph.weapon;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * v0.9 — técnicas de ARMA do núcleo (motor de glifos), registradas por {@code CoreGlyphs.register()}:
 * <ul>
 *   <li>{@code strike} "Golpe" — forma: o próximo golpe corpo a corpo aplica os efeitos ({@link StrikeForm}).</li>
 *   <li>{@code arrow} "Flecha" — forma: dispara uma flecha REAL do arco/besta com a magia ({@link ArrowForm}).</li>
 *   <li>{@code stance} "Postura" — forma de canal: efeitos em você a cada 1 s enquanto segura a tecla ({@link StanceForm}).</li>
 *   <li>{@code parry} "Aparar" — gatilho reativo: sai sozinho ao bloquear/aparar um golpe ({@link ParryTrigger}).</li>
 *   <li>{@code guard} "Guarda" — efeito: Resistência (a "Postura Firme" inicial das classes Guerreiras).</li>
 * </ul>
 * Os eventos que fazem tudo funcionar são ligados em {@code core.classes.ClassesCore}.
 */
public final class WeaponGlyphs {
    public static final String STRIKE = "strike";
    public static final String ARROW = "arrow";
    public static final String STANCE = "stance";
    public static final String PARRY = "parry";
    public static final String GUARD = "guard";

    public static final ResourceLocation STRIKE_ID = IsekaiMahou.id(STRIKE);
    public static final ResourceLocation ARROW_ID = IsekaiMahou.id(ARROW);
    public static final ResourceLocation STANCE_ID = IsekaiMahou.id(STANCE);
    public static final ResourceLocation PARRY_ID = IsekaiMahou.id(PARRY);
    public static final ResourceLocation GUARD_ID = IsekaiMahou.id(GUARD);

    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        GlyphRegistry.register(new StrikeForm());
        GlyphRegistry.register(new ArrowForm());
        GlyphRegistry.register(new StanceForm());
        GlyphRegistry.register(new ParryTrigger());
        GlyphRegistry.register(new GuardEffect());
    }

    /** Servidor fechou: limpa todo o estado estático das técnicas. */
    public static void clearAll() {
        WeaponStrikes.clear();
        WeaponArrows.clear();
        WeaponStances.clear();
        WeaponParry.clear();
        MeleeSwings.clear();
    }

    private WeaponGlyphs() {}
}
