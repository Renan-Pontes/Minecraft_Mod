package com.renanpontes.isekaimahou.feature.familiars;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * A ajuda do Familiar — "pequena e temática, sem lutar pelo jogador". Uma ajuda por forma
 * ({@link FamiliarForm.Help}), mais forte a cada estágio, sempre com recarga e sem número na tela: o familiar aponta,
 * faz um som só para o dono e pronto.
 * <p>
 * Chamado pelo corpo do familiar a cada 10 ticks (só com o dono por perto). As buscas são pequenas e raras: o faro de
 * minério varre um cubo de poucos blocos a cada ~10 s; o de tesouro olha só as entidades de bloco dos chunks vizinhos.
 * Nada aqui mexe em blocos, e os faros ignoram zonas seguras (cidades).
 */
public final class FamiliarHelp {
    /** Sem levar dano há este tempo = "fora de combate" (cura/mana só aqui). */
    private static final int CALM_TICKS = 100;
    private static final int RECENT = 16;

    /** Recargas e memória curta de um corpo de familiar (tempo de jogo). */
    static final class State {
        long nextScan, nextAlert, nextTick, nextCleanse, nextAir, nextFire, nextTrail;
        final ArrayDeque<Long> recent = new ArrayDeque<>();

        void remember(BlockPos pos) {
            recent.addLast(pos.asLong());
            while (recent.size() > RECENT) recent.removeFirst();
        }
    }

    private FamiliarHelp() {}

    static void tick(FamiliarEntity fam, ServerPlayer owner, FamiliarData d, ServerLevel level) {
        if (fam.distanceToSqr(owner) > 24 * 24) return;
        State st = fam.helpState;
        long now = level.getGameTime();
        int stage = d.stage();
        switch (d.form().help) {
            case FETCH -> fetch(fam, owner, level, stage);
            case SOOTHE -> soothe(fam, owner, level, st, now, stage);
            case MEND -> mend(fam, owner, level, st, now, stage);
            case MANA -> mana(fam, owner, level, st, now, stage);
            case FORAGE -> forage(fam, owner, level, st, now, stage);
            case WATCH -> watch(fam, owner, level, st, now, 14 + 4 * stage, false);
            case AMBUSH -> ambush(fam, owner, level, st, now, stage);
            case DEATH_GUIDE -> {
                watch(fam, owner, level, st, now, 12 + 3 * stage, true);
                if (now < d.deathGuideUntil() && now >= st.nextTrail) {
                    st.nextTrail = now + 300;
                    deathTrail(fam, owner, level, false);
                }
            }
            case ORE -> {
                if (now >= st.nextScan) {
                    st.nextScan = now + 20L * (20 - 4 * stage);
                    findOre(fam, owner, level, st, stage);
                }
            }
            case TREASURE -> {
                if (now >= st.nextScan) {
                    st.nextScan = now + 20L * (30 - 5 * stage);
                    findTreasure(fam, owner, level, st, stage);
                }
            }
        }
    }

    /**
     * Ajuda "ativa": o dono fez carinho e o familiar procura AGORA (faros) ou guia até a última morte.
     * {@code force} (comando de teste) ignora a recarga curta.
     */
    static void active(FamiliarEntity fam, ServerPlayer owner, FamiliarData d, boolean force) {
        ServerLevel level = owner.serverLevel();
        State st = fam.helpState;
        long now = level.getGameTime();
        switch (d.form().help) {
            case ORE -> {
                if (!force && now < st.nextTick) return;
                st.nextTick = now + 80;
                st.nextScan = now + 20L * (20 - 4 * d.stage());
                st.recent.clear();
                if (!findOre(fam, owner, level, st, d.stage())) nothing(owner);
            }
            case TREASURE -> {
                if (!force && now < st.nextTick) return;
                st.nextTick = now + 120;
                st.nextScan = now + 20L * (30 - 5 * d.stage());
                st.recent.clear();
                if (!findTreasure(fam, owner, level, st, d.stage())) nothing(owner);
            }
            case DEATH_GUIDE -> deathTrail(fam, owner, level, true);
            default -> { }
        }
    }

    private static void nothing(ServerPlayer owner) {
        owner.displayClientMessage(Component.translatable("familiars.isekai_mahou.msg.nothing").withColor(0xA89EC0), true);
    }

    // =====================================================================================
    //  Raposa: busca os itens caídos
    // =====================================================================================
    private static void fetch(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, int stage) {
        if (owner.getTicksFrozen() > 0) owner.setTicksFrozen(Math.max(0, owner.getTicksFrozen() - 12));
        // Em cidade não: o que está no chão ali pode ser de outra pessoa
        if (Places.isSafe(level, owner.blockPosition())) return;
        double r = 4 + 2 * stage;
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(r, 3, r),
                it -> it.isAlive() && !it.hasPickUpDelay() && it.getAge() > 30);
        int moved = 0;
        Vec3 to = owner.position().add(0, 0.6, 0);
        for (ItemEntity it : items) {
            Vec3 d = to.subtract(it.position());
            double len = d.length();
            if (len < 1.4) continue;
            it.setDeltaMovement(d.scale(Math.min(0.45, 0.12 + len * 0.05) / len).add(0, 0.06, 0));
            it.hurtMarked = true;
            if (++moved >= 12) break;
        }
        if (moved > 0) Hints.show(owner, "familiar_help_fetch");
    }

    // =====================================================================================
    //  Lontra / Lebre / Fogo-fátuo / Broto: cuidados leves
    // =====================================================================================
    private static void soothe(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, long now, int stage) {
        if (owner.isOnFire() && now >= st.nextFire && !owner.isInLava()) {
            st.nextFire = now + 20L * (35 - 5 * stage);
            owner.clearFire();
            level.sendParticles(ParticleTypes.SPLASH, owner.getX(), owner.getY() + 1, owner.getZ(), 24, 0.3, 0.5, 0.3, 0.1);
            cue(fam, owner, SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.6F, 1.2F, "happy");
            Hints.show(owner, "familiar_help_soothe");
        }
        if (owner.isUnderWater() && owner.getAirSupply() < owner.getMaxAirSupply() / 5 && now >= st.nextAir) {
            st.nextAir = now + 20L * (100 - 15 * stage);
            owner.setAirSupply(Math.min(owner.getMaxAirSupply(), owner.getAirSupply() + 60 + 40 * stage));
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, owner.getX(), owner.getEyeY(), owner.getZ(), 16, 0.3, 0.3, 0.3, 0.05);
            cue(fam, owner, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.7F, 1.3F, "happy");
            Hints.show(owner, "familiar_help_soothe");
        }
        lightHeal(fam, owner, level, st, now, 0.5F, 90 - 15 * stage, ParticleTypes.FALLING_WATER);
    }

    private static void mend(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, long now, int stage) {
        if (lightHeal(fam, owner, level, st, now, 0.6F, 75 - 15 * stage, ParticleTypes.END_ROD)) {
            Hints.show(owner, "familiar_help_mend");
        }
        // A partir do 2º estágio também tira veneno (devagar: uma vez por minuto)
        if (stage >= 2 && now >= st.nextCleanse && owner.hasEffect(MobEffects.POISON)) {
            st.nextCleanse = now + 1200;
            owner.removeEffect(MobEffects.POISON);
            cue(fam, owner, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.5F, "happy");
        }
    }

    /** Cura 1 de vida de tempos em tempos, só fora de combate e só até a fração {@code upTo} da vida. */
    private static boolean lightHeal(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, long now, float upTo,
                                     int every, ParticleOptions particle) {
        if (now < st.nextTick || !calm(owner, now)) return false;
        if (owner.getHealth() <= 0 || owner.getHealth() >= owner.getMaxHealth() * upTo) return false;
        st.nextTick = now + every;
        owner.heal(1.0F);
        level.sendParticles(owner, particle, false, owner.getX(), owner.getY() + 1.0, owner.getZ(), 5, 0.3, 0.4, 0.3, 0.01);
        return true;
    }

    private static void mana(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, long now, int stage) {
        if (now < st.nextTick || !calm(owner, now)) return;
        MagicData md = MagicApi.get(owner);
        // Só ajuda a sair do "vazio": nunca passa da metade (não é fonte de mana infinita)
        if (md.maxMana() <= 0 || md.mana() >= md.maxMana() * 0.5F) return;
        st.nextTick = now + 40;
        md.addMana(Math.max(1F, md.maxMana() * 0.005F * (1 + stage)));
        level.sendParticles(owner, ParticleTypes.ENCHANT, false, owner.getX(), owner.getY() + 1.2, owner.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
        Hints.show(owner, "familiar_help_mana");
    }

    private static void forage(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, long now, int stage) {
        if (now < st.nextTick || owner.getFoodData().getFoodLevel() >= 12) return;
        st.nextTick = now + 20L * (36 - 7 * stage);
        owner.getFoodData().eat(1, 0.3F);
        level.sendParticles(owner, ParticleTypes.HAPPY_VILLAGER, false, owner.getX(), owner.getY() + 1.2, owner.getZ(), 4, 0.3, 0.3, 0.3, 0);
        cue(fam, owner, SoundEvents.GENERIC_EAT, 0.4F, 1.4F, "happy");
        Hints.show(owner, "familiar_help_forage");
    }

    private static boolean calm(ServerPlayer owner, long now) {
        return now - FamiliarService.lastHurt(owner) > CALM_TICKS;
    }

    // =====================================================================================
    //  Coruja / Corvo / Gato: avisos de perigo
    // =====================================================================================
    /** Aviso de longe: alguém que está caçando o dono e que ele não está olhando (ou ainda está longe). */
    private static void watch(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, long now, double range,
                              boolean undeadOnly) {
        if (now < st.nextAlert || owner.isCreative()) return;
        Vec3 look = owner.getLookAngle();
        Mob found = null;
        double best = Double.MAX_VALUE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(range, range * 0.5, range),
                m -> m.isAlive() && m.getTarget() == owner && !m.isCurrentlyGlowing())) {
            if (undeadOnly && !m.getType().is(EntityTypeTags.UNDEAD)) continue;
            Vec3 dir = m.position().subtract(owner.position());
            double dist = dir.length();
            // De frente e de perto o jogador já viu: não é novidade
            if (dist < 8 && dist > 0.01 && dir.scale(1 / dist).dot(look) > 0.3) continue;
            if (dist < best) {
                best = dist;
                found = m;
            }
        }
        if (found == null) return;
        st.nextAlert = now + 240;
        warn(fam, owner, found, undeadOnly ? "familiar_help_death_guide" : "familiar_help_watch");
    }

    /** Aviso de perto: creeper chegando, inimigo invisível ou alguém pelas costas. */
    private static void ambush(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, long now, int stage) {
        if (now < st.nextAlert || owner.isCreative()) return;
        double range = 7 + 2 * stage;
        Vec3 look = owner.getLookAngle();
        Mob found = null;
        for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(range, 4, range),
                m -> m.isAlive() && !m.isCurrentlyGlowing())) {
            boolean hunting = m.getTarget() == owner;
            Vec3 dir = m.position().subtract(owner.position());
            boolean behind = dir.lengthSqr() > 0.01 && dir.normalize().dot(look) < -0.1;
            if (m instanceof Creeper && (hunting || behind) || hunting && (m.isInvisible() || behind)) {
                found = m;
                break;
            }
        }
        if (found == null) return;
        st.nextAlert = now + 200;
        warn(fam, owner, found, "familiar_help_ambush");
    }

    private static void warn(FamiliarEntity fam, ServerPlayer owner, Mob threat, String hint) {
        // O contorno brilhante dura pouco: é o "olha ali!" do familiar, não uma visão de raio-x permanente
        threat.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, false));
        fam.pointTo(threat.getEyePosition(), 50);
        cue(fam, owner, fam.alertSound(), 0.9F, 1.0F, "alert");
        Hints.show(owner, hint);
    }

    // =====================================================================================
    //  Corvo: o caminho até a última morte
    // =====================================================================================
    private static void deathTrail(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, boolean say) {
        GlobalPos death = owner.getLastDeathLocation().orElse(null);
        if (death == null || death.dimension() != level.dimension()) {
            if (say) {
                owner.displayClientMessage(Component.translatable(death == null ? "familiars.isekai_mahou.msg.no_death"
                        : "familiars.isekai_mahou.msg.death_elsewhere").withColor(0xA89EC0), true);
            }
            return;
        }
        Vec3 to = Vec3.atCenterOf(death.pos());
        int dist = (int) Math.sqrt(owner.distanceToSqr(to));
        if (dist < 4) return;
        point(fam, owner, level, to, new Vector3f(0.85F, 0.82F, 0.75F));
        if (say) {
            owner.displayClientMessage(Component.translatable("familiars.isekai_mahou.msg.death_trail", dist).withColor(0xD8D2C0), true);
        }
        Hints.show(owner, "familiar_help_death_guide");
    }

    // =====================================================================================
    //  Tartaruga: faro de minério
    // =====================================================================================
    private static boolean findOre(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, int stage) {
        int r = 5 + stage;
        BlockPos c = owner.blockPosition();
        if (!level.hasChunksAt(c.offset(-r, 0, -r), c.offset(r, 0, r))) return false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        int minY = Math.max(level.getMinBuildHeight(), c.getY() - r), maxY = Math.min(level.getMaxBuildHeight() - 1, c.getY() + r);
        for (int y = minY; y <= maxY; y++) {
            for (int x = c.getX() - r; x <= c.getX() + r; x++) {
                for (int z = c.getZ() - r; z <= c.getZ() + r; z++) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !s.is(Tags.Blocks.ORES)) continue;
                    int d = (int) p.distSqr(c);
                    if (d < bestD && !st.recent.contains(p.asLong())) {
                        bestD = d;
                        best = p.immutable();
                    }
                }
            }
        }
        if (best == null) return false;
        st.remember(best);
        point(fam, owner, level, Vec3.atCenterOf(best), new Vector3f(0.75F, 0.95F, 0.6F));
        cue(fam, owner, fam.alertSound(), 0.7F, 1.2F, "find");
        Hints.show(owner, "familiar_help_ore");
        return true;
    }

    // =====================================================================================
    //  Dragonete: faro de tesouro (baús de saque ainda fechados; geradores a partir do 2º estágio)
    // =====================================================================================
    private static boolean findTreasure(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, State st, int stage) {
        int chunks = stage >= 2 ? 2 : 1;
        double max = 16.0 * chunks + 8;
        BlockPos c = owner.blockPosition();
        int cx = c.getX() >> 4, cz = c.getZ() >> 4;
        BlockPos best = null;
        double bestD = max * max;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> en : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = en.getValue();
                    boolean loot = be instanceof RandomizableContainerBlockEntity rc && rc.getLootTable() != null;
                    if (!loot && !(stage >= 2 && be instanceof SpawnerBlockEntity)) continue;
                    BlockPos pos = en.getKey();
                    if (Math.abs(pos.getY() - c.getY()) > 24) continue;
                    double d = pos.distSqr(c);
                    if (d >= bestD || st.recent.contains(pos.asLong()) || Places.isSafe(level, pos)) continue;
                    bestD = d;
                    best = pos;
                }
            }
        }
        if (best == null) return false;
        st.remember(best);
        point(fam, owner, level, Vec3.atCenterOf(best), new Vector3f(1.0F, 0.82F, 0.25F));
        cue(fam, owner, fam.alertSound(), 0.7F, 1.3F, "find");
        Hints.show(owner, "familiar_help_treasure");
        return true;
    }

    // =====================================================================================
    //  Efeitos (só para o dono: nada de poluir a tela dos outros)
    // =====================================================================================
    /** Trilha de brilho do familiar na direção do alvo (até 14 blocos) e um brilho no alvo se ele estiver perto. */
    private static void point(FamiliarEntity fam, ServerPlayer owner, ServerLevel level, Vec3 target, Vector3f rgb) {
        fam.pointTo(target, 80);
        Vec3 from = owner.getEyePosition().add(0, -0.3, 0);
        Vec3 d = target.subtract(from);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 step = d.scale(1 / len);
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.9F);
        double shown = Math.min(len, 14.0);
        for (double t = 1.2; t < shown; t += 0.6) {
            Vec3 q = from.add(step.scale(t));
            level.sendParticles(owner, dust, true, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
        if (len <= 14.0) {
            level.sendParticles(owner, ParticleTypes.WAX_ON, true, target.x, target.y, target.z, 10, 0.4, 0.4, 0.4, 0.02);
        }
    }

    private static void cue(FamiliarEntity fam, ServerPlayer owner, @Nullable SoundEvent sound, float volume, float pitch,
                            String anim) {
        if (sound != null) owner.playNotifySound(sound, SoundSource.NEUTRAL, volume, pitch);
        fam.playAction(anim);
    }
}
