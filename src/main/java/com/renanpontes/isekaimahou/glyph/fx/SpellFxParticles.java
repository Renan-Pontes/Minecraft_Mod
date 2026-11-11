package com.renanpontes.isekaimahou.glyph.fx;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tipos de partícula da paleta de elementos ({@code isekai_mahou:fx_*}). Todos usam {@link FxOptions}.
 * Sprites em {@code assets/isekai_mahou/particles/fx_*.json} (gerados por {@code tools/gen/fx_spells.py}).
 * Os providers de cliente ficam em {@code client/spellfx/SpellFxClient}.
 */
public final class SpellFxParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, IsekaiMahou.MODID);

    // ---------------- Partículas soltas (paleta dos elementos) ----------------
    /** Brilho suave colorido (substitui o "pó colorido"). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> GLOW = reg("fx_glow", false);
    /** Língua de chama / brasa (fogo, sol, magma, fogo infernal). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> EMBER = reg("fx_ember", false);
    /** Faísca alongada pela velocidade (raio, impactos). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> SPARK = reg("fx_spark", false);
    /** Floco de neve / estilhaço de gelo ou cristal. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> FROST = reg("fx_frost", false);
    /** Gota d'água (cai com gravidade). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> DROPLET = reg("fx_droplet", false);
    /** Folha / pétala (natureza), flutua caindo. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> LEAF = reg("fx_leaf", false);
    /** Lasca de pedra / grão de areia (terra, areia, magma). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> STONE = reg("fx_stone", false);
    /** Risco de vento. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> WIND = reg("fx_wind", false);
    /** Cintilar de luz (estrela de 4/6 pontas). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> MOTE = reg("fx_mote", false);
    /** Fiapo de sombra (escuro, sobe girando). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> WISP = reg("fx_wisp", false);
    /** Runa arcana flutuante. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> RUNE = reg("fx_rune", false);
    /** Fumaça / vapor. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> SMOKE = reg("fx_smoke", false);

    // ---------------- Efeitos grandes (sempre aparecem, mesmo longe) ----------------
    /** Clarão (scale = raio em blocos). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> FLASH = reg("fx_flash", true);
    /** Onda de choque: anel que se expande (scale = raio final). Velocidade = normal do plano (0 = horizontal). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> SHOCKWAVE = reg("fx_shockwave", true);
    /** Círculo mágico (scale = raio). data = entidade a seguir. flags = modo/tier/camada. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> CIRCLE = reg("fx_circle", true);
    /** Pilar de luz (scale = raio, data = altura em 1/16 bloco). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> PILLAR = reg("fx_pillar", true);

    // ---------------- Flags (círculos, ondas) ----------------
    /** bits 0-2: tier visual 0..5. */
    public static final int TIER_MASK = 0x7;
    /** bits 3-4: modo. */
    public static final int MODE_SHIFT = 3;
    public static final int MODE_GROUND = 0;
    /** Vertical, de frente para onde a entidade olha (ou para a normal = velocidade, se não segue ninguém). */
    public static final int MODE_FRONT = 1;
    /** Horizontal, alguns blocos acima da entidade (ultimates). */
    public static final int MODE_SKY = 2;
    /** Orientado pela normal (velocidade enviada). */
    public static final int MODE_NORMAL = 3;
    /** Mantém vivo um círculo igual que já existe (mesma entidade/modo/camada) em vez de criar outro. */
    public static final int FLAG_KEEPALIVE = 1 << 5;
    /** "Disparo": o círculo cresce rápido e some (fim da conjuração). */
    public static final int FLAG_RELEASE = 1 << 6;
    /** Gira ao contrário. */
    public static final int FLAG_REVERSE = 1 << 7;
    /** bits 8-9: camada/sprite do círculo (0 principal, 1 selo interno, 2 camada de ultimate, 3 simples). */
    public static final int LAYER_SHIFT = 8;

    public static int flags(int tier, int mode, int layer) {
        return (Math.max(0, Math.min(5, tier)) & TIER_MASK) | ((mode & 3) << MODE_SHIFT) | ((layer & 3) << LAYER_SHIFT);
    }

    public static int tierOf(int flags) { return flags & TIER_MASK; }

    public static int modeOf(int flags) { return (flags >> MODE_SHIFT) & 3; }

    public static int layerOf(int flags) { return (flags >> LAYER_SHIFT) & 3; }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }

    private static DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> reg(String id, boolean alwaysShow) {
        return PARTICLES.register(id, () -> new FxType(alwaysShow));
    }

    /** Tipo de partícula com codec ligado a si mesmo. */
    private static final class FxType extends ParticleType<FxOptions> {
        private final MapCodec<FxOptions> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, FxOptions> stream;

        FxType(boolean alwaysShow) {
            super(alwaysShow);
            this.codec = FxOptions.codec(this);
            this.stream = FxOptions.streamCodec(this);
        }

        @Override
        public MapCodec<FxOptions> codec() { return codec; }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, FxOptions> streamCodec() { return stream; }
    }

    private SpellFxParticles() {}
}
