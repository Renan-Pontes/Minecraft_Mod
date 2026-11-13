package com.renanpontes.isekaimahou.feature.cities.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.cities.CitySiege;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * "Varredura" de um chunk da cidade, feita UMA vez por chunk pelo coração (v0.9, pedido do jogador: "um acampamento orc
 * DENTRO da cidade"):
 * <ol>
 *   <li><b>Purga</b>: some todo mob hostil que veio de MOLDE de outra estrutura (inclusive os persistentes, ex.: o Lorde
 *       Orc de um acampamento) — menos os da cidade, os nomeados por jogador, os do Cerco, os com dono, os presos em
 *       guia/veículo;</li>
 *   <li>geradores de monstros (spawners) que caíram dentro da cidade viram ar;</li>
 *   <li>acha o <b>Altar de Ascensão público</b> ({@code isekai_mahou:ascension_core} com {@code PublicAltar}) e a
 *       <b>Pedra de Teleporte</b> da praça ({@code isekai_mahou:travel_stone}) — por id, se os blocos existirem.</li>
 * </ol>
 * Só roda com o chunk e as entidades dele carregados (as entidades carregam depois dos blocos).
 */
public final class CitySweep {
    /** Mobs abaixo disto (cavernas sob a cidade) não são da conta da cidade. */
    static final int BELOW_FLOOR = 16;
    /** Spawners abaixo disto (masmorras fundas) ficam. */
    static final int SPAWNER_BELOW_FLOOR = 18;
    /** A Pedra de Teleporte fica na praça: só procura nos chunks até esta distância do coração. */
    static final int STONE_RADIUS = 48;
    /** Marca de entidade ({@code Entity#addTag}) para outros módulos: "este mob pode ficar na cidade". */
    public static final String KEEP_TAG = "isekai_mahou.city_keep";

    /** Resultado da varredura de um chunk. */
    static final class Result {
        int purged;
        int spawners;
        final List<BlockPos> altars = new ArrayList<>();
        @Nullable BlockPos stone;
        /** Algum intruso estava lutando com um jogador: o chunk é varrido de novo depois. */
        boolean deferred;
    }

    /**
     * Mob hostil "intruso" (de outra estrutura) dentro da cidade?
     *
     * @param persistentToo também os persistentes (moldes); false = só os que nasceram soltos (spawn natural)
     */
    public static boolean isIntruder(Mob m, boolean persistentToo) {
        if (m instanceof CityNpc || !m.isAlive()) return false;
        if (!(m instanceof Enemy) && m.getType().getCategory() != MobCategory.MONSTER) return false;
        if (m.hasCustomName()) return false;                                     // nomeado (etiqueta) por um jogador
        if (CitySiege.isSiegeMob(m)) return false;                               // o Cerco tem regras próprias
        if (m instanceof OwnableEntity o && o.getOwnerUUID() != null) return false;   // invocação/companheiro
        if (m instanceof TamableAnimal t && t.isTame()) return false;
        if (m.isLeashed()) return false;                                         // capturado pelo jogador
        if (m.isPassenger()) {
            // Levado num barco/carrinho/montaria do jogador: fica. Montado em OUTRO intruso (cavaleiro de lobo do
            // acampamento, jóquei de aranha): some junto com a montaria — senão sobraria sozinho na cidade varrida.
            Entity v = m.getVehicle();
            if (!(v instanceof Mob vm) || !isIntruder(vm, persistentToo)) return false;
        }
        if (m.getTags().contains(KEEP_TAG)) return false;                        // outro módulo pediu para ficar
        return persistentToo || !m.isPersistenceRequired();
    }

    static Result sweep(ServerLevel sl, LevelChunk chunk, BoundingBox box, int floorY, BlockPos heart) {
        Result r = new Result();
        ChunkPos cp = chunk.getPos();
        int x0 = Math.max(box.minX(), cp.getMinBlockX()), x1 = Math.min(box.maxX(), cp.getMaxBlockX());
        int z0 = Math.max(box.minZ(), cp.getMinBlockZ()), z1 = Math.min(box.maxZ(), cp.getMaxBlockZ());
        if (x0 > x1 || z0 > z1) return r;

        // 1) mobs de molde
        AABB area = new AABB(x0, floorY - BELOW_FLOOR, z0, x1 + 1, box.maxY() + 1, z1 + 1);
        for (Mob m : sl.getEntitiesOfClass(Mob.class, area, m -> isIntruder(m, true))) {
            // Luta DE VERDADE em andamento (um jogador acertou o mob há menos de 10 s): não some no meio — o chunk volta
            // depois. Só mirar no jogador não basta: o orc do acampamento dentro da cidade some antes de chegar nele.
            if (m.getLastHurtByMob() instanceof Player && m.tickCount - m.getLastHurtByMobTimestamp() < 200) {
                r.deferred = true;
                continue;
            }
            sl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 10,
                    m.getBbWidth() * 0.4, m.getBbHeight() * 0.3, m.getBbWidth() * 0.4, 0.02);
            m.discard();
            r.purged++;
        }
        if (r.purged > 0) sl.playSound(null, x0, floorY + 1, z0, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.4F, 1.4F);

        // 2) spawners e 3) altares (block entities do chunk; cópia porque o setBlock mexe no mapa)
        Optional<Block> core = BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id("ascension_core"));
        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
            BlockPos p = be.getBlockPos();
            if (p.getX() < x0 || p.getX() > x1 || p.getZ() < z0 || p.getZ() > z1 || p.getY() > box.maxY()) continue;
            if (be instanceof SpawnerBlockEntity && p.getY() >= floorY - SPAWNER_BELOW_FLOOR) {
                sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                sl.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.01);
                r.spawners++;
            } else if (core.isPresent() && be.getBlockState().is(core.get()) && isPublicAltar(sl, be)) {
                r.altars.add(p.immutable());
            }
        }

        // 4) Pedra de Teleporte (só nos chunks da praça; seções que talvez tenham o bloco)
        Optional<Block> stone = BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id("travel_stone"));
        int dx = Math.max(0, Math.max(cp.getMinBlockX() - heart.getX(), heart.getX() - cp.getMaxBlockX()));
        int dz = Math.max(0, Math.max(cp.getMinBlockZ() - heart.getZ(), heart.getZ() - cp.getMaxBlockZ()));
        if (stone.isPresent() && dx <= STONE_RADIUS && dz <= STONE_RADIUS) {
            Block sb = stone.get();
            int yMin = Math.max(sl.getMinBuildHeight(), floorY - 4), yMax = Math.min(sl.getMaxBuildHeight() - 1, floorY + 12);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            double best = Double.MAX_VALUE;
            for (int sy = yMin >> 4; sy <= yMax >> 4; sy++) {
                int idx = chunk.getSectionIndexFromSectionY(sy);
                if (idx < 0 || idx >= chunk.getSections().length) continue;
                LevelChunkSection sec = chunk.getSection(idx);
                if (sec.hasOnlyAir() || !sec.maybeHas(st -> st.is(sb))) continue;
                for (int y = Math.max(yMin, sy << 4); y <= Math.min(yMax, (sy << 4) + 15); y++) {
                    for (int x = x0; x <= x1; x++) {
                        for (int z = z0; z <= z1; z++) {
                            BlockState st = sec.getBlockState(x & 15, y & 15, z & 15);
                            if (!st.is(sb)) continue;
                            m.set(x, y, z);
                            double d = m.distSqr(heart);
                            if (d < best) {
                                best = d;
                                r.stone = m.immutable();
                            }
                        }
                    }
                }
            }
        }
        return r;
    }

    /** O núcleo do altar foi colocado como público pelo molde ({@code PublicAltar} no NBT ou nos dados persistentes)? */
    private static boolean isPublicAltar(ServerLevel sl, BlockEntity be) {
        if (be.getPersistentData().getBoolean("PublicAltar")) return true;
        try {
            return be.saveWithoutMetadata(sl.registryAccess()).getBoolean("PublicAltar");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private CitySweep() {}
}
