package com.renanpontes.isekaimahou.feature.world_events.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.world_events.WeManager;
import com.renanpontes.isekaimahou.feature.world_events.WeSites;
import com.renanpontes.isekaimahou.feature.world_events.WeState;
import com.renanpontes.isekaimahou.feature.world_events.WorldEventType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * <b>Lua de Sangue</b>: do anoitecer ao amanhecer, mais monstros aparecem em volta de quem está no ermo, e os
 * monstros mortos dão um pouco mais de XP e, às vezes, um drop a mais.
 * <p>
 * Cidades são sagradas: os monstros extras NUNCA nascem dentro/perto de um lugar do {@code Places}, em chunk onde um
 * jogador já construiu, nem em volta de quem está numa zona segura; e nunca são Creepers nem Endermen (os únicos que
 * estragam blocos). Dormir passa a noite normalmente — quem não quer a Lua de Sangue pode ir para a cama.
 */
public final class BloodMoon {
    /** Marca (persistentData) dos monstros que a Lua de Sangue trouxe. */
    public static final String TAG = "isekai_mahou.blood_moon";
    private static final int MOB_CAP = 16;
    private static final float XP_BONUS = 1.5F;
    private static final float EXTRA_DROP_CHANCE = 0.2F;

    private BloodMoon() {}

    public static void start(ServerLevel level, WeState s) {
        // nada: os monstros vêm aos poucos no tick
    }

    public static boolean tick(ServerLevel level, WeState s, int age) {
        boolean forced = s.extra.getBoolean("forced");
        int tod = (int) (level.getDayTime() % 24000L);
        // Amanheceu (ou dormiram): acabou. Pedida por comando, dura o tempo todo mesmo de dia (para testar).
        if (!forced && age > 100 && tod < 12500) return false;
        if (age % 20 != 0 || level.getDifficulty() == Difficulty.PEACEFUL) return true;
        int beat = age / 20;
        for (ServerPlayer p : level.players()) {
            // cada jogador recebe uma tentativa a cada 5 s, em segundos diferentes (espalha o trabalho)
            if (Math.floorMod(p.getId() + beat, 5) != 0) continue;
            if (Places.isSafe(level, p.blockPosition())) banishFromSafeZone(level, p);
            else trySpawn(level, p, forced);
        }
        return true;
    }

    /**
     * O jogador correu para a cidade com a horda atrás: os monstros DA LUA que entrarem na zona segura viram fumaça.
     * (Só olha em volta de quem está numa zona segura, uma vez a cada 5 s.)
     */
    private static void banishFromSafeZone(ServerLevel level, ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(40, 16, 40);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && m.getPersistentData().getBoolean(TAG) && Places.isSafe(level, m.blockPosition()))) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 1, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.01);
            mob.discard();
        }
    }

    private static void trySpawn(ServerLevel level, ServerPlayer p, boolean forced) {
        if (p.isSpectator() || !p.isAlive()) return;
        BlockPos at = p.blockPosition();
        if (Places.isSafe(level, at) || WeSites.nearPlace(level, at, 32)) return;
        // só para quem está na superfície (em caverna funda a lua não alcança)
        if (at.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 12) return;
        AABB box = p.getBoundingBox().inflate(48, 24, 48);
        if (level.getEntitiesOfClass(Monster.class, box, Entity::isAlive).size() >= MOB_CAP) return;

        RandomSource rnd = level.random;
        WeState state = WeState.get(level.getServer());
        int made = 0;
        for (int i = 0; i < 6 && made < 2; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = 24 + rnd.nextDouble() * 20;
            int x = at.getX() + Mth.floor(Math.cos(ang) * dist);
            int z = at.getZ() + Mth.floor(Math.sin(ang) * dist);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            if (state.isBuilt(ChunkPos.asLong(x >> 4, z >> 4))) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(pos.getY() - at.getY()) > 24 || !level.isPositionEntityTicking(pos)) continue;
            if (Places.isSafe(level, pos) || WeSites.nearPlace(level, pos, 48)) continue;
            if (WeSites.looksBuilt(level.getBlockState(pos.below()))) continue;
            EntityType<?> type = pickType(level, pos, rnd);
            if (type == null) continue;
            if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) continue;
            if (!forced && !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, rnd)) continue;
            if (!level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) continue;
            Entity e = type.create(level);
            if (!(e instanceof Mob mob)) {
                if (e != null) e.discard();
                continue;
            }
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360F, 0F);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            mob.getPersistentData().putBoolean(TAG, true);
            if (!level.addFreshEntity(mob)) continue;
            mob.setTarget(p);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 0.4, 0.6, 0.4, 0.01);
            made++;
        }
    }

    /** Um monstro do bioma (os mesmos que nasceriam ali), menos os que estragam blocos. */
    private static EntityType<?> pickType(ServerLevel level, BlockPos pos, RandomSource rnd) {
        var list = level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.MONSTER);
        for (int i = 0; i < 4; i++) {
            Optional<MobSpawnSettings.SpawnerData> pick = list.getRandom(rnd);
            if (pick.isEmpty()) break;
            EntityType<?> t = pick.get().type;
            if (t == EntityType.CREEPER || t == EntityType.ENDERMAN || t.getCategory() != MobCategory.MONSTER) continue;
            return t;
        }
        return rnd.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON;
    }

    // ------------------------------------------------------------------ loot/XP leve
    private static boolean applies(LivingEntity victim) {
        return victim instanceof Enemy && WeManager.active(victim.level()) == WorldEventType.BLOOD_MOON;
    }

    public static void onXpDrop(LivingExperienceDropEvent e) {
        if (e.getAttackingPlayer() == null || !applies(e.getEntity())) return;
        e.setDroppedExperience(Math.round(e.getDroppedExperience() * XP_BONUS));
        if (e.getAttackingPlayer() instanceof ServerPlayer sp) {
            // XP mágico leve: 1–4 por monstro, conforme a vida dele
            MagicApi.giveXp(sp, Mth.clamp(Math.round(e.getEntity().getMaxHealth() / 20F), 1, 4), "kill");
            // contador para títulos/ranking de outros módulos (lido por id)
            MagicApi.addCounter(sp, "world_event.blood_moon_kills", 1);
        }
    }

    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer) || !applies(e.getEntity())) return;
        LivingEntity victim = e.getEntity();
        if (victim.getRandom().nextFloat() >= EXTRA_DROP_CHANCE || e.getDrops().isEmpty()) return;
        // Um drop a mais: cópia de UM item que já ia cair (nunca equipamento/itens que não empilham)
        List<ItemEntity> stackable = new ArrayList<>();
        for (ItemEntity it : e.getDrops()) if (it.getItem().isStackable()) stackable.add(it);
        if (stackable.isEmpty()) return;
        ItemEntity src = stackable.get(victim.getRandom().nextInt(stackable.size()));
        ItemStack copy = src.getItem().copyWithCount(1);
        ItemEntity bonus = new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.3, victim.getZ(), copy);
        bonus.setDefaultPickUpDelay();
        e.getDrops().add(bonus);
    }
}
