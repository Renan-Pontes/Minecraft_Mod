package com.renanpontes.isekaimahou.glyph;

/** Tipos de glifo. */
public enum GlyphKind {
    /** Gatilho condicional/sequência, vem ANTES da forma (ex.: Ao Receber Dano). Gatilhos de movimento não entram na receita. */
    TRIGGER,
    /** Como a magia sai (Projétil, Feixe, Zona, Expansão de Domínio...). Exatamente 1 por magia. */
    FORM,
    /** O que acontece no alvo (Incendiar, Curar, Empurrar...). */
    EFFECT,
    /** Modifica o glifo anterior (Amplificar, Expandir área, Dividir...). */
    AUGMENT,
    /** Regra de Domínio (Acerto Garantido, Sem Fuga...). Só depois de uma forma que aceita leis. */
    LAW
}
