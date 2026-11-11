package com.renanpontes.isekaimahou.feature.outfits.client;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.outfits.OutfitArmorItem;
import com.renanpontes.isekaimahou.feature.outfits.OutfitSet;
import com.renanpontes.isekaimahou.feature.outfits.OutfitTier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Efeito puramente visual de quem veste o traje COMPLETO: poucas partículas temáticas de vez em quando
 * (glifos do Mago, brilho roxo da Bruxa, fumaça e brasas do Warlock, pétalas do Druida, almas do Necromante,
 * faíscas de aço do Guerreiro, luz dourada do Paladino, brasas de fúria do Bárbaro, vento do Monge, brilho de
 * metal do Cavaleiro, folhas do Patrulheiro, sombras do Ladino, notas musicais do Bardo, vapor e faíscas do
 * Artífice e tufos de pelo do Caçador).
 * Nos graus II–IV o traje também cintila na cor das gemas do grau, e o de Arquimago tem um anel de runas girando
 * na cintura. Não muda nada de jogo. Respeita a opção de partículas reduzidas.
 */
public final class OutfitAmbience {
    private static final double RANGE_SQR = 32 * 32;
    private static final DustParticleOptions WARLOCK_EMBER = new DustParticleOptions(new Vector3f(0.95F, 0.22F, 0.15F), 0.6F);
    private static final DustParticleOptions DRUID_LEAF = new DustParticleOptions(new Vector3f(0.36F, 0.66F, 0.22F), 0.8F);
    private static final DustParticleOptions MAGE_STAR = new DustParticleOptions(new Vector3f(1.0F, 0.88F, 0.45F), 0.5F);
    private static final DustParticleOptions STEEL_SPARK = new DustParticleOptions(new Vector3f(0.92F, 0.95F, 1.0F), 0.45F);
    private static final DustParticleOptions WAR_EMBER = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.18F), 0.5F);
    private static final DustParticleOptions HOLY_MOTE = new DustParticleOptions(new Vector3f(1.0F, 0.86F, 0.38F), 0.6F);
    private static final DustParticleOptions RAGE_EMBER = new DustParticleOptions(new Vector3f(0.95F, 0.18F, 0.1F), 0.6F);
    private static final DustParticleOptions KI_WISP = new DustParticleOptions(new Vector3f(0.45F, 0.95F, 0.88F), 0.55F);
    private static final DustParticleOptions BANNER_BLUE = new DustParticleOptions(new Vector3f(0.3F, 0.45F, 0.9F), 0.5F);
    private static final DustParticleOptions RANGER_LEAF = new DustParticleOptions(new Vector3f(0.3F, 0.58F, 0.22F), 0.75F);
    private static final DustParticleOptions SHADOW_WISP = new DustParticleOptions(new Vector3f(0.28F, 0.16F, 0.42F), 0.8F);
    private static final DustParticleOptions FORGE_SPARK = new DustParticleOptions(new Vector3f(1.0F, 0.7F, 0.25F), 0.4F);
    private static final DustParticleOptions FUR_TUFT = new DustParticleOptions(new Vector3f(0.55F, 0.42F, 0.3F), 0.6F);

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        // "Partículas: mínimas" do vanilla desliga o enfeite (é só cosmético)
        if (mc.options.particles().get() == ParticleStatus.MINIMAL) return;
        RandomSource rnd = level.random;
        float chance = reducedParticles() ? 0.025F : 0.06F;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Player player : level.players()) {
            if (player.isSpectator() || player.isInvisible() || player.distanceToSqr(cam) > RANGE_SQR) continue;
            // em primeira pessoa as próprias partículas só atrapalhariam a visão
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            OutfitSet set = OutfitArmorItem.fullSet(player);
            if (set == null) continue;
            OutfitTier tier = OutfitArmorItem.fullSetTier(player);
            if (tier != null && tier != OutfitTier.APPRENTICE) spawnTier(level, player, set, tier, rnd, reducedParticles());
            if (rnd.nextFloat() > chance) continue;
            spawn(level, player, set, rnd);
        }
    }

    /** Cintilar na cor do grau (mais frequente a cada grau) e, no Arquimago, duas runas orbitando a cintura. */
    private static void spawnTier(ClientLevel level, Player p, OutfitSet set, OutfitTier tier, RandomSource rnd, boolean reduced) {
        int rgb = glowColor(set, tier);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                (rgb & 0xFF) / 255F), 0.7F);
        float sparkle = (reduced ? 0.012F : 0.03F) * (tier.number - 1);
        if (rnd.nextFloat() < sparkle) {
            level.addParticle(dust, p.getX() + (rnd.nextDouble() - 0.5) * 0.8, p.getY() + 0.3 + rnd.nextDouble() * 1.5,
                    p.getZ() + (rnd.nextDouble() - 0.5) * 0.8, 0, 0.01, 0);
        }
        if (tier == OutfitTier.MASTER && rnd.nextFloat() < (reduced ? 0.008F : 0.02F)) {
            level.addParticle(ParticleTypes.END_ROD, p.getX() + (rnd.nextDouble() - 0.5) * 0.6, p.getY() + 1.4,
                    p.getZ() + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
        if (tier != OutfitTier.ARCHMAGE || reduced || p.tickCount % 3 != 0) return;
        float base = p.tickCount * 0.11F;
        for (int i = 0; i < 2; i++) {
            float a = base + i * Mth.PI;
            level.addParticle(dust, p.getX() + Mth.cos(a) * 0.75, p.getY() + 0.95, p.getZ() + Mth.sin(a) * 0.75, 0, 0, 0);
        }
        if (rnd.nextFloat() < 0.05F) {
            var special = switch (set) {
                case MAGE, PALADIN -> ParticleTypes.END_ROD;
                case WITCH -> ParticleTypes.WITCH;
                case WARLOCK -> ParticleTypes.SMALL_FLAME;
                case DRUID -> ParticleTypes.SPORE_BLOSSOM_AIR;
                case NECROMANCER -> ParticleTypes.SOUL_FIRE_FLAME;
                case WARRIOR -> ParticleTypes.CRIT;
                case BARBARIAN -> ParticleTypes.FLAME;
                case MONK -> ParticleTypes.GLOW;
                case KNIGHT, HUNTER -> ParticleTypes.WAX_ON;
                case RANGER -> ParticleTypes.HAPPY_VILLAGER;
                case ROGUE -> ParticleTypes.REVERSE_PORTAL;
                case BARD -> ParticleTypes.NOTE;
                case ARTIFICER -> ParticleTypes.ELECTRIC_SPARK;
            };
            level.addParticle(special, p.getX() + (rnd.nextDouble() - 0.5) * 0.7, p.getY() + 0.2 + rnd.nextDouble() * 1.6,
                    p.getZ() + (rnd.nextDouble() - 0.5) * 0.7, 0, 0.01, 0);
        }
    }

    /** Cor das gemas/runas de cada traje e grau (a mesma do glowmask gerado por tools/gen/outfits.py). */
    private static int glowColor(OutfitSet set, OutfitTier tier) {
        int t = tier.number - 2; // 0 adepto, 1 mestre, 2 arquimago
        int[] colors = switch (set) {
            case MAGE -> new int[] {0x7FC8FF, 0xFFD95A, 0x8AE0FF};
            case WITCH -> new int[] {0xB8C8FF, 0x6AF0A0, 0xFF6AD0};
            case WARLOCK -> new int[] {0xFF4A3A, 0xF0D070, 0xFF8A1E};
            case DRUID -> new int[] {0xA0DC6C, 0xF8B048, 0x7FFFD4};
            case NECROMANCER -> new int[] {0x3AE890, 0xB88AFF, 0x7AF0FF};
            case WARRIOR -> new int[] {0xFF8A3A, 0xFFD24A, 0xFF5A1E};
            case PALADIN -> new int[] {0x9AD8FF, 0xFFD95A, 0xFFF2A0};
            case BARBARIAN -> new int[] {0xFF6A2A, 0xE8302A, 0xFF9A1E};
            case MONK -> new int[] {0x7AF0E0, 0xFFC24A, 0x9AF4FF};
            case KNIGHT -> new int[] {0x8AB4FF, 0xFFD24A, 0xFFB040};
            case RANGER -> new int[] {0x9AF07A, 0x7AE8D0, 0x6AFF9A};
            case ROGUE -> new int[] {0xFF4A5A, 0xC08AFF, 0xB45AFF};
            case BARD -> new int[] {0x6AE8D8, 0xFFD24A, 0xFF8AD8};
            case ARTIFICER -> new int[] {0x6AD8FF, 0xFF9A3A, 0x7AFFF0};
            case HUNTER -> new int[] {0xBFD8FF, 0xFF5A3A, 0xFFB22A};
        };
        return colors[Mth.clamp(t, 0, 2)];
    }

    private static void spawn(ClientLevel level, Player p, OutfitSet set, RandomSource rnd) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double bx = p.getX(), by = p.getY(), bz = p.getZ();
        double ox = (rnd.nextDouble() - 0.5) * 0.7, oz = (rnd.nextDouble() - 0.5) * 0.7;
        switch (set) {
            case MAGE -> {
                // glifos subindo das mãos e uma faísca dourada na ponta do chapéu
                double side = rnd.nextBoolean() ? 0.38 : -0.38;
                level.addParticle(ParticleTypes.ENCHANT, bx + Mth.cos(yaw) * side, by + 0.8, bz + Mth.sin(yaw) * side,
                        ox * 0.6, 0.35 + rnd.nextDouble() * 0.2, oz * 0.6);
                if (rnd.nextFloat() < 0.4F) {
                    level.addParticle(MAGE_STAR, bx + Mth.sin(yaw) * 0.15, by + p.getBbHeight() + 0.95, bz - Mth.cos(yaw) * 0.15,
                            0, 0.01, 0);
                }
            }
            case WITCH -> level.addParticle(ParticleTypes.WITCH, bx + ox, by + 0.4 + rnd.nextDouble() * 1.2, bz + oz,
                    0, 0.02, 0);
            case WARLOCK -> {
                level.addParticle(ParticleTypes.SMOKE, bx + ox, by + 0.15, bz + oz, 0, 0.02, 0);
                if (rnd.nextFloat() < 0.5F) {
                    level.addParticle(WARLOCK_EMBER, bx + ox * 0.6, by + 0.9 + rnd.nextDouble() * 0.6, bz + oz * 0.6,
                            0, 0.03, 0);
                }
            }
            case DRUID -> {
                // pétalas e folhinhas caindo do manto
                if (rnd.nextFloat() < 0.5F) {
                    level.addParticle(ParticleTypes.CHERRY_LEAVES, bx + ox, by + 1.5, bz + oz, 0, 0, 0);
                } else {
                    level.addParticle(DRUID_LEAF, bx + ox, by + 1.2 + rnd.nextDouble() * 0.4, bz + oz, 0, -0.02, 0);
                }
            }
            case NECROMANCER -> {
                if (rnd.nextFloat() < 0.35F) {
                    level.addParticle(ParticleTypes.SOUL, bx + ox, by + 0.2, bz + oz, 0, 0.03, 0);
                } else {
                    level.addParticle(ParticleTypes.ASH, bx + ox, by + 0.3 + rnd.nextDouble(), bz + oz, 0, 0, 0);
                }
            }
            case WARRIOR -> {
                // faíscas de aço saltando da cota de malha e uma brasa subindo da capa
                if (rnd.nextFloat() < 0.6F) {
                    double side = rnd.nextBoolean() ? 0.4 : -0.4;
                    level.addParticle(STEEL_SPARK, bx + Mth.cos(yaw) * side, by + 0.9 + rnd.nextDouble() * 0.5,
                            bz + Mth.sin(yaw) * side, ox * 0.2, 0.06, oz * 0.2);
                } else {
                    level.addParticle(WAR_EMBER, bx + Mth.sin(yaw) * 0.35 + ox * 0.4, by + 0.6 + rnd.nextDouble() * 0.8,
                            bz - Mth.cos(yaw) * 0.35 + oz * 0.4, 0, 0.025, 0);
                }
            }
            case PALADIN -> {
                // poeira de luz dourada subindo e, às vezes, um raio de luz sobre o elmo
                level.addParticle(HOLY_MOTE, bx + ox, by + 0.2 + rnd.nextDouble() * 1.4, bz + oz, 0, 0.02, 0);
                if (rnd.nextFloat() < 0.2F) {
                    level.addParticle(ParticleTypes.END_ROD, bx + ox * 0.3, by + p.getBbHeight() + 0.25, bz + oz * 0.3,
                            0, 0.015, 0);
                }
            }
            case BARBARIAN -> {
                // brasas vermelhas da fúria e o bafo quente saindo dos ombros
                if (rnd.nextFloat() < 0.55F) {
                    level.addParticle(RAGE_EMBER, bx + ox, by + 0.5 + rnd.nextDouble() * 1.1, bz + oz, 0, 0.035, 0);
                } else {
                    double side = rnd.nextBoolean() ? 0.35 : -0.35;
                    level.addParticle(ParticleTypes.SMOKE, bx + Mth.cos(yaw) * side, by + 1.45, bz + Mth.sin(yaw) * side,
                            0, 0.02, 0);
                }
            }
            case MONK -> {
                // fios de ki girando em volta da faixa e poeira de vento nos pés
                float a = p.tickCount * 0.3F + rnd.nextFloat();
                level.addParticle(KI_WISP, bx + Mth.cos(a) * 0.55, by + 0.85 + rnd.nextDouble() * 0.2, bz + Mth.sin(a) * 0.55,
                        0, 0.01, 0);
                if (rnd.nextFloat() < 0.35F) {
                    level.addParticle(ParticleTypes.WHITE_ASH, bx + ox, by + 0.1, bz + oz, ox * 0.1, 0.02, oz * 0.1);
                }
            }
            case KNIGHT -> {
                // brilho de metal polido nos ombros e um fiapo azul do tabardo
                if (rnd.nextFloat() < 0.6F) {
                    double side = rnd.nextBoolean() ? 0.42 : -0.42;
                    level.addParticle(ParticleTypes.WAX_OFF, bx + Mth.cos(yaw) * side, by + 1.35, bz + Mth.sin(yaw) * side,
                            0, 0.01, 0);
                } else {
                    level.addParticle(BANNER_BLUE, bx + ox * 0.5, by + 0.5 + rnd.nextDouble() * 0.6, bz + oz * 0.5, 0, -0.01, 0);
                }
            }
            case RANGER -> {
                // folhas caindo do capuz e da capa
                level.addParticle(RANGER_LEAF, bx + ox, by + 1.4 + rnd.nextDouble() * 0.4, bz + oz, 0, -0.03, 0);
                if (rnd.nextFloat() < 0.15F) {
                    level.addParticle(ParticleTypes.COMPOSTER, bx + ox, by + 0.1, bz + oz, 0, 0, 0);
                }
            }
            case ROGUE -> {
                // fumaça nos pés (mais agachado) e um fiapo de sombra
                int puffs = p.isCrouching() ? 2 : 1;
                for (int i = 0; i < puffs; i++) {
                    level.addParticle(ParticleTypes.SMOKE, bx + (rnd.nextDouble() - 0.5) * 0.7, by + 0.1,
                            bz + (rnd.nextDouble() - 0.5) * 0.7, 0, 0.01, 0);
                }
                if (rnd.nextFloat() < 0.4F) {
                    level.addParticle(SHADOW_WISP, bx + ox, by + 0.4 + rnd.nextDouble(), bz + oz, 0, 0.01, 0);
                }
            }
            case BARD -> {
                // notas musicais coloridas em volta da cabeça e das mãos
                double side = rnd.nextBoolean() ? 0.45 : -0.45;
                double hy = rnd.nextBoolean() ? by + p.getBbHeight() + 0.2 : by + 0.9;
                level.addParticle(ParticleTypes.NOTE, bx + Mth.cos(yaw) * side, hy, bz + Mth.sin(yaw) * side,
                        rnd.nextInt(25) / 24.0, 0, 0);
            }
            case ARTIFICER -> {
                // vapor saindo da chaminé da mochila e faíscas de oficina
                double backX = bx + Mth.sin(yaw) * 0.45, backZ = bz - Mth.cos(yaw) * 0.45;
                if (rnd.nextFloat() < 0.6F) {
                    level.addParticle(ParticleTypes.WHITE_SMOKE, backX, by + p.getBbHeight() + 0.05, backZ, 0, 0.04, 0);
                } else {
                    level.addParticle(FORGE_SPARK, bx + ox * 0.6, by + 0.8 + rnd.nextDouble() * 0.4, bz + oz * 0.6,
                            ox * 0.3, 0.08, oz * 0.3);
                }
            }
            case HUNTER -> {
                // tufos de pelo se soltando do manto de peles
                level.addParticle(FUR_TUFT, bx + ox, by + 1.3 + rnd.nextDouble() * 0.3, bz + oz, ox * 0.05, -0.02, oz * 0.05);
                if (rnd.nextFloat() < 0.2F) {
                    level.addParticle(ParticleTypes.POOF, bx + ox * 0.5, by + 0.15, bz + oz * 0.5, 0, 0.01, 0);
                }
            }
        }
    }

    private static boolean reducedParticles() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (IllegalStateException e) {
            return false; // config ainda não carregada
        }
    }

    private OutfitAmbience() {}
}
