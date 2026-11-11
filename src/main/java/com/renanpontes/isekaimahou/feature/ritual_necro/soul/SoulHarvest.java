package com.renanpontes.isekaimahou.feature.ritual_necro.soul;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroContent;
import com.renanpontes.isekaimahou.feature.ritual_necro.NecroRitual;
import com.renanpontes.isekaimahou.feature.ritual_necro.item.BoneScytheItem;
import com.renanpontes.isekaimahou.feature.ritual_necro.item.PhylacteryItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Colheita de Almas: matar um mob com a Foice de Ossos (alma vai para um Filactério do inventário) ou com
 * o Filactério na mão secundária guarda 1 alma daquele tipo. Uma "alma" visível voa do corpo até o jogador.
 * Cada alma também dá um pouco de XP mágico.
 */
public final class SoulHarvest {
    private static final List<Wisp> WISPS = new ArrayList<>();

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        // Jogadores falsos (máquinas de outros mods) não colhem almas
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player.isSpectator() || player instanceof FakePlayer) return;
        if (!(victim instanceof Mob) || victim.getType().is(NecroContent.SOULLESS)) return;
        // Os próprios servos/mascotes não rendem almas (evita "fazendas" de almas)
        if (victim instanceof OwnableEntity own && player.getUUID().equals(own.getOwnerUUID())) return;
        CompoundTag data = victim.getPersistentData();
        if (data.hasUUID(NecroRitual.OWNER_TAG) && player.getUUID().equals(data.getUUID(NecroRitual.OWNER_TAG))) return;

        boolean scythe = BoneScytheItem.is(player.getMainHandItem()) && event.getSource().getDirectEntity() == player;
        ItemStack phylactery = PhylacteryItem.find(player, scythe);
        if (!phylactery.is(NecroContent.PHYLACTERY.get())) {
            if (scythe) {
                player.displayClientMessage(NecroRitual.msg("soul_lost").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
                level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.02);
            }
            return;
        }

        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        SoulStore store = SoulStore.of(phylactery);
        if (store.isFull(type)) {
            player.displayClientMessage(NecroRitual.msg("phylactery_full", NecroRitual.soulName(type))
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        SoulStore updated = store.add(type, 1);
        phylactery.set(NecroContent.SOULS.get(), updated);
        player.displayClientMessage(NecroRitual.msg("soul_harvested", NecroRitual.soulName(type), updated.count(type))
                .withStyle(ChatFormatting.DARK_AQUA), true);

        // XP pequeno (maior para mobs fortes e chefes) e um toque de Sombra
        boolean boss = victim.getType().is(Tags.EntityTypes.BOSSES);
        int xp = boss ? Mth.clamp(40 + Math.round(victim.getMaxHealth() / 10F), 40, 120)
                : Mth.clamp(Math.round(victim.getMaxHealth() / 8F), 1, 25);
        MagicApi.giveXp(player, xp, "soul");
        MagicApi.giveElementXp(player, Element.SHADOW, boss ? 10 : 1);
        MagicApi.addCounter(player, NecroRitual.SOULS_HARVESTED, 1);

        Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y, from.z, boss ? 20 : 4, 0.25, 0.25, 0.25, 0.02);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 0.8F + level.random.nextFloat() * 0.3F);
        WISPS.add(new Wisp(level, from, player, boss));
    }

    /** Move as almas visíveis até o jogador (todas no thread do servidor). */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (WISPS.isEmpty()) return;
        Iterator<Wisp> it = WISPS.iterator();
        while (it.hasNext()) {
            if (!it.next().tick()) it.remove();
        }
    }

    /** Ao entrar: entrega um rito que terminou enquanto o jogador estava fora. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) NecroRitual.finishPending(sp, null, null);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        WISPS.clear();
    }

    /** Uma alma voando em arco do corpo até o peito do jogador. */
    private static final class Wisp {
        private final ServerLevel level;
        private final ServerPlayer target;
        private final boolean big;
        private Vec3 pos;
        private int age;

        Wisp(ServerLevel level, Vec3 start, ServerPlayer target, boolean big) {
            this.level = level;
            this.pos = start;
            this.target = target;
            this.big = big;
        }

        /** false = terminou (chegou, ou o jogador sumiu). */
        boolean tick() {
            age++;
            if (target.isRemoved() || target.level() != level || age > 80) return false;
            Vec3 dest = target.position().add(0, target.getBbHeight() * 0.6, 0);
            Vec3 d = dest.subtract(pos);
            double dist = d.length();
            if (dist < 0.6) {
                arrive(dest);
                return false;
            }
            // Sobe um pouco no começo (a alma "se solta" do corpo) e acelera até o jogador
            double speed = Math.min(dist, 0.12 + age * 0.03);
            Vec3 step = d.scale(speed / dist);
            double lift = Math.max(0, 0.12 - age * 0.012);
            double wobble = Math.sin(age * 0.7) * 0.06;
            pos = pos.add(step.x + step.z * wobble, step.y + lift, step.z - step.x * wobble);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, big ? 4 : 2, 0.04, 0.04, 0.04, 0.0);
            if (age % 3 == 0) level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
            return true;
        }

        private void arrive(Vec3 at) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, big ? 30 : 10, 0.3, 0.45, 0.3, 0.02);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.6F, 0.6F);
        }
    }

    private SoulHarvest() {}
}
