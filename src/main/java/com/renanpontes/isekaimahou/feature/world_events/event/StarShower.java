package com.renanpontes.isekaimahou.feature.world_events.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.world_events.WeRegistries;
import com.renanpontes.isekaimahou.feature.world_events.WeSites;
import com.renanpontes.isekaimahou.feature.world_events.WeState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * <b>Chuva de Estrelas</b> (v1.1, "mundo vivo"): uma noite calma e bonita. O céu se enche de estrelas cadentes (o
 * cliente desenha, ver {@code ambience/client/AmbientLife}) e, de vez em quando, um fragmento cai perto de quem está
 * ao ar livre: Metal Estelar (o mais comum), Cristal de Mana e, raramente, um Fragmento de Estrela. Sem monstros e sem
 * bônus de combate — é só para olhar o céu e catar o que cai.
 * <p>
 * Igual à Tempestade de Mana: nada cai dentro/perto de cidades nem em zonas seguras. Acaba ao amanhecer.
 */
public final class StarShower {
    /** Cada jogador ao ar livre recebe uma queda a cada 40–70 s. */
    private static final int DROP_MIN = 20 * 40;
    private static final int DROP_SPREAD = 20 * 30;
    private static final Map<UUID, Long> NEXT_DROP = new ConcurrentHashMap<>();

    private StarShower() {}

    public static void start(ServerLevel level, WeState s) {
        NEXT_DROP.clear();
    }

    public static void reset() {
        NEXT_DROP.clear();
    }

    public static boolean tick(ServerLevel level, WeState s, int age) {
        boolean forced = s.extra.getBoolean("forced");
        int tod = (int) (level.getDayTime() % 24000L);
        // amanheceu (ou dormiram): acabou. Pedida por comando, vale mesmo de dia (para testar).
        if (!forced && age > 100 && tod < 12500) return false;
        if (age % 20 != 0) return true;
        long now = level.getGameTime();
        RandomSource rnd = level.random;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            Long next = NEXT_DROP.get(p.getUUID());
            if (next == null) {
                NEXT_DROP.put(p.getUUID(), now + 200 + rnd.nextInt(DROP_SPREAD));
                continue;
            }
            if (now < next) continue;
            NEXT_DROP.put(p.getUUID(), now + DROP_MIN + rnd.nextInt(DROP_SPREAD));
            drop(level, p, rnd);
        }
        return true;
    }

    private static void drop(ServerLevel level, ServerPlayer p, RandomSource rnd) {
        if (!level.canSeeSky(p.blockPosition().above())) return;
        double ang = rnd.nextDouble() * Math.PI * 2;
        double dist = 5 + rnd.nextDouble() * 9;
        int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
        int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        if (Places.isSafe(level, ground) || WeSites.nearPlace(level, ground, 16)) return;
        if (!level.getFluidState(ground.below()).isEmpty()) return;

        ItemStack stack = pick(rnd);
        double top = ground.getY() + 26;
        ItemEntity item = new ItemEntity(level, x + 0.5, top, z + 0.5, stack, 0, -0.7, 0);
        item.setDefaultPickUpDelay();
        item.setGlowingTag(true);
        level.addFreshEntity(item);
        // rastro dourado do céu até o chão + um "tim" de cristal: dá para achar sem nada na tela
        for (int i = 0; i < 26; i += 2) {
            level.sendParticles(ParticleTypes.END_ROD, x + 0.5, ground.getY() + i + 0.5, z + 0.5, 1, 0.05, 0.4, 0.05, 0.01);
        }
        level.sendParticles(ParticleTypes.FIREWORK, x + 0.5, ground.getY() + 1.0, z + 0.5, 12, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, ground, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.4F, 1.2F + rnd.nextFloat() * 0.4F);
    }

    /** 60% Metal Estelar, 34% Cristal de Mana, 6% Fragmento de Estrela (do módulo da Biblioteca, pelo id). */
    private static ItemStack pick(RandomSource rnd) {
        int roll = rnd.nextInt(100);
        if (roll < 6) {
            Item frag = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id("star_fragment")).orElse(Items.AIR);
            if (frag != Items.AIR) return new ItemStack(frag);
        }
        if (roll < 66) return new ItemStack(WeRegistries.STAR_METAL.get());
        return new ItemStack(CoreRegistries.MANA_CRYSTAL.get());
    }
}
