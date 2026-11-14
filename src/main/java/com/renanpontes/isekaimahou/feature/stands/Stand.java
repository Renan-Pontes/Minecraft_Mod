package com.renanpontes.isekaimahou.feature.stands;

/**
 * Os STANDS (espíritos guardiões de JoJo) que aparecem SÓ para quem é da classe Steel Ball quando usa um glifo de
 * JoJo. Cada um tem cor, tamanho e "peças" de forma próprias (o modelo é o mesmo humanoide translúcido com partes
 * extras ligadas/desligadas). Comum (servidor e cliente): o servidor só manda o número ({@link #ordinal()}).
 */
public enum Stand {
    /** ORA ORA — roxo, cabelo espetado. */
    STAR_PLATINUM(0x8A6CFF, 1.05F, Stand.HAIR),
    /** King Crimson / Epitáfio — vermelho, rosto na testa (crista). */
    KING_CRIMSON(0xD0203A, 1.1F, Stand.CREST),
    /** Killer Queen / Bites the Dust / Sheer Heart Attack — rosa-branco, orelhas de gato. */
    KILLER_QUEEN(0xF6C8E0, 1.0F, Stand.EARS),
    /** Tusk Ato 1 — rosinha pequeno. */
    TUSK_ACT1(0xF4A0C0, 0.42F, Stand.TUSK),
    /** Tusk Ato 2 — rosa-dourado. */
    TUSK_ACT2(0xF2B0A0, 0.52F, Stand.TUSK),
    /** Tusk Ato 3 — dourado-rosado, maior. */
    TUSK_ACT3(0xEDBA7A, 0.7F, Stand.TUSK | Stand.CREST),
    /** Tusk Ato 4 — dourado, tamanho de gente, com a estrela. */
    TUSK_ACT4(0xF2C850, 1.0F, Stand.TUSK | Stand.HELMET),
    /** Ball Breaker (Esfera Quebrantadora) — aço-dourado com elmo. */
    BALL_BREAKER(0xC8B070, 1.15F, Stand.HELMET | Stand.CREST),
    /** Dirty Deeds Done Dirt Cheap (Love Train / dimensão) — azul, orelhas-chifres compridas. */
    D4C(0x5C8CFF, 1.05F, Stand.HORNS),
    /** Gold Experience Requiem — dourado, crista e elmo. */
    GOLD_EXPERIENCE_REQUIEM(0xF2D040, 1.05F, Stand.CREST | Stand.EARS);

    /** Peças extras do modelo (bits). */
    public static final int EARS = 1, HORNS = 2, CREST = 4, HAIR = 8, HELMET = 16, TUSK = 32;

    private final int color;
    private final float scale;
    private final int parts;

    Stand(int color, float scale, int parts) {
        this.color = color;
        this.scale = scale;
        this.parts = parts;
    }

    public int color() { return color; }

    public float scale() { return scale; }

    public boolean has(int part) { return (parts & part) != 0; }

    public static Stand byId(int id) {
        Stand[] v = values();
        return id >= 0 && id < v.length ? v[id] : STAR_PLATINUM;
    }

    /** O que o stand faz enquanto aparece (decide a pose e o lugar: atrás de você ou à frente, golpeando). */
    public enum Action {
        /** rajada de socos (ORA) — à frente. */
        BARRAGE(true),
        /** aponta/dispara (unhas do Tusk) — à frente. */
        POINT(true),
        /** aperta o "botão" (Killer Queen) — atrás. */
        SWITCH(false),
        /** apaga o tempo (King Crimson) — atrás, pisca. */
        ERASE(false),
        /** braços abertos (Love Train / dimensão) — atrás. */
        SPREAD(false),
        /** um golpe só, pesado — à frente. */
        SLAM(true),
        /** guarda (braços cruzados) — atrás. */
        GUARD(false);

        private final boolean front;

        Action(boolean front) {
            this.front = front;
        }

        /** O stand sai de trás e vai para a frente do usuário? */
        public boolean front() { return front; }

        public static Action byId(int id) {
            Action[] v = values();
            return id >= 0 && id < v.length ? v[id] : GUARD;
        }
    }
}
