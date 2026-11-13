package com.renanpontes.isekaimahou.feature.depths;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.depths.item.DepthsFoes;
import com.renanpontes.isekaimahou.feature.depths.item.DragonslayerItem;
import com.renanpontes.isekaimahou.feature.depths.item.GoddessKnifeItem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Eventos de jogo do módulo: armas de anime, andares da masmorra (letreiro + diário) e o geodo. */
public final class DepthsEvents {
    public static final String FLAG_ENTERED = "depths.entered";
    public static final String FLAG_FLOOR3 = "depths.floor3";
    public static final String FLAG_LAIR = "depths.lair";
    public static final String FLAG_GEODE = "depths.geode";
    public static final String FLAG_TREASURE = "depths.treasure";

    /** Último andar em que cada jogador foi visto (para mostrar o letreiro só ao mudar). */
    private static final Map<UUID, Integer> LAST_FLOOR = new HashMap<>();
    private static boolean sweeping;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DepthsEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DepthsEvents::onAttack);
        NeoForge.EVENT_BUS.addListener(DepthsEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(DepthsEvents::onBreak);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LAST_FLOOR.remove(e.getEntity().getUUID()));
    }

    /** Marca a flag uma vez; devolve true se foi a primeira vez. */
    public static boolean flag(ServerPlayer sp, String key) {
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(key)) return false;
        d.setFlag(key, true);
        d.markDirty();
        return true;
    }

    /**
     * Roda um golpe em área das armas do módulo sem que o bônus de dano por golpe ({@link #onIncomingDamage}) entre de
     * novo por cima — o dano do golpe em área já vem calculado com os bônus.
     */
    public static void withoutHitBonus(Runnable area) {
        boolean before = sweeping;
        sweeping = true;
        try {
            area.run();
        } finally {
            sweeping = before;
        }
    }

    // ------------------------------------------------------------------ Armas

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || sweeping) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) return;
        ItemStack weapon = player.getMainHandItem();
        ServerLevel sl = (ServerLevel) victim.level();
        double y = victim.getY() + victim.getBbHeight() * 0.6;
        if (weapon.getItem() instanceof DragonslayerItem && DepthsFoes.isHuge(victim)) {
            event.setAmount(event.getAmount() * DragonslayerItem.HUGE_BONUS);
            sl.sendParticles(ParticleTypes.CRIT, victim.getX(), y, victim.getZ(), 12, 0.5, 0.5, 0.5, 0.2);
        } else if (weapon.getItem() instanceof GoddessKnifeItem) {
            float amount = event.getAmount() + GoddessKnifeItem.falnaBonus(player);
            float mult = GoddessKnifeItem.consumeArgonaut(player);
            if (mult > 1.0F) {
                amount *= mult;
                sl.sendParticles(ParticleTypes.END_ROD, victim.getX(), y, victim.getZ(), 30, 0.4, 0.6, 0.4, 0.15);
                sl.sendParticles(ParticleTypes.FLASH, victim.getX(), y, victim.getZ(), 1, 0, 0, 0, 0);
                sl.playSound(null, victim.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 1.4F);
            }
            event.setAmount(amount);
        }
    }

    /** Matadora de Dragões: golpe cheio corta em arco de 160° (60% do dano nos outros inimigos em volta). */
    private static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !(player.getMainHandItem().getItem() instanceof DragonslayerItem)) return;
        if (player.getAttackStrengthScale(0.5F) < 0.9F) return;
        Entity main = event.getTarget();
        ServerLevel sl = (ServerLevel) player.level();
        float dmg = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F;
        double reach = player.entityInteractionRange() + 0.5;
        sweeping = true;
        try {
            for (Entity e : sl.getEntities(player, player.getBoundingBox().inflate(reach, 1.0, reach))) {
                if (e == main || !DepthsFoes.isFoe(player, e) || !DepthsFoes.inArc(player, e, 80.0)) continue;
                if (e.distanceTo(player) > reach + e.getBbWidth() * 0.5) continue;
                e.hurt(player.damageSources().playerAttack(player), dmg * (DepthsFoes.isHuge((LivingEntity) e) ? DragonslayerItem.HUGE_BONUS : 1F));
            }
        } finally {
            sweeping = false;
        }
        var look = player.getLookAngle();
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + look.x * 2, player.getY() + 1.0, player.getZ() + look.z * 2,
                3, 0.8, 0.1, 0.8, 0);
        sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    // ------------------------------------------------------------------ Andares e diário (a cada 1 s)

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 7 || !sp.isAlive()) return;
        if (sp.tickCount % 100 == 7 && !MagicApi.get(sp).hasFlag(FLAG_TREASURE)
                && sp.getInventory().hasAnyMatching(s -> s.is(DepthsRegistries.DRAGONSLAYER.get()) || s.is(DepthsRegistries.GODDESS_KNIFE.get()))) {
            flag(sp, FLAG_TREASURE);
        }
        if (sp.level().dimension() != Level.OVERWORLD) {
            LAST_FLOOR.remove(sp.getUUID());
            return;
        }
        StructureStart start = DepthsDungeon.startWithPieceAt(sp.serverLevel(), sp.blockPosition());
        List<StructurePiece> pieces = start == null ? List.of() : DepthsDungeon.sorted(start);
        int floor = DepthsDungeon.floorIn(pieces, sp.blockPosition());
        Integer last = LAST_FLOOR.put(sp.getUUID(), floor);
        // Afixos do andar: os monstros perto do jogador recebem os modificadores (uma vez cada).
        List<DepthsAffixes.Affix> affixes = floor >= 1 ? DepthsAffixes.roll(sp.serverLevel(), start.getChunkPos(), floor) : List.of();
        if (floor >= 1) DepthsAffixes.applyNear(sp.serverLevel(), sp, pieces.get(floor).getBoundingBox(), affixes);
        if (floor < 0 || (last != null && last == floor)) return;
        DepthsAffixes.onFloor(sp, start.getChunkPos(), floor);
        if (flag(sp, FLAG_ENTERED)) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.depths.entered").withColor(0xC8873E));
        }
        if (floor >= 3) flag(sp, FLAG_FLOOR3);
        if (floor >= DepthsDungeon.LAIR) flag(sp, FLAG_LAIR);
        if (floor >= 1) {
            showFloorTitle(sp, floor);
            if (!affixes.isEmpty()) sp.displayClientMessage(DepthsAffixes.line(affixes), true);
        }
    }

    private static void showFloorTitle(ServerPlayer sp, int floor) {
        Component title = floor >= DepthsDungeon.LAIR
                ? Component.translatable("message.isekai_mahou.depths.lair").withColor(0xC8873E)
                : Component.translatable("message.isekai_mahou.depths.floor", floor).withColor(0xE8D8B0);
        Component sub = Component.translatable(String.format("message.isekai_mahou.depths.floor.%d", Math.min(floor, DepthsDungeon.LAIR))).withColor(0x9A8A70);
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        sp.connection.send(new ClientboundSetTitleTextPacket(title));
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8F, 0.7F);
    }

    // ------------------------------------------------------------------ Geodo

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        BlockState state = event.getState();
        if (state.is(DepthsRegistries.MANA_CLUSTER.get()) || state.is(DepthsRegistries.BUDDING_MANA_CRYSTAL.get())
                || state.is(DepthsRegistries.MANA_GEODE_BLOCK.get())) {
            if (flag(sp, FLAG_GEODE)) {
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.depths.geode").withColor(0x5AE0FF));
            }
        }
    }

    private DepthsEvents() {}
}
