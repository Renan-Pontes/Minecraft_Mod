package com.renanpontes.isekaimahou.feature.legends_f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_f.entity.BarrierEntity;
import com.renanpontes.isekaimahou.feature.legends_f.necro.ShadowArmy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Regras de jogo das transformações e estados do legends_f (servidor): Pacto da Motosserra, Contrato Faustiano,
 * meditação e Modo Sábio, além dos ouvintes de dano/alvo/morte de Ocultar Mana, Sono Encantado, Barreira e do
 * Exército das Sombras. Tudo em memória: ao sair do jogo os estados acabam (os efeitos de status somem junto).
 */
public final class Transformations {
    public static final int CHAINSAW_COLOR = 0xFF7A1A;
    public static final int SAGE_COLOR = 0xF08A24;
    public static final int FAUST_COLOR = 0xC0182E;
    public static final int MEDITATE_TICKS = 60;

    private static final class Saw {
        long end;
        boolean heartUsed;
        int amp;
    }

    private static final class Contract {
        long end;
        int needed;
        int kills;
    }

    private static final class Med {
        long start;
        Vec3 pos;
        int sageTicks;
    }

    private static final Map<UUID, Saw> SAW = new HashMap<>();
    private static final Map<UUID, Contract> FAUST = new HashMap<>();
    private static final Map<UUID, Med> MEDITATION = new HashMap<>();
    private static final Map<UUID, Long> SAGE = new HashMap<>();
    /** Evita que a onda do Modo Sábio dispare outra onda. */
    private static boolean inWave;

    // =====================================================================================
    //  Pacto da Motosserra
    // =====================================================================================

    public static void startChainsaw(ServerPlayer sp, int ticks, int amp) {
        ServerLevel level = sp.serverLevel();
        Saw s = new Saw();
        s.end = level.getGameTime() + ticks;
        s.amp = amp;
        SAW.put(sp.getUUID(), s);
        sp.addEffect(new MobEffectInstance(LfRegistry.CHAINSAW_DEVIL, ticks, Math.min(amp, 3), false, false, true));
        LfStates.set(sp, LfNet.S_CHAINSAW, ticks, 0);
        LfUtil.banner(sp, "announce.isekai_mahou.legends_f.chainsaw", CHAINSAW_COLOR, true);
        LfUtil.sound(level, sp.position(), SoundEvents.GRINDSTONE_USE, 1.4F, 0.6F);
        LfUtil.sound(level, sp.position(), SoundEvents.BEE_LOOP_AGGRESSIVE, 1.4F, 0.5F);
        LfUtil.sound(level, sp.position(), SoundEvents.RAVAGER_ROAR, 0.7F, 1.3F);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.75F, 0.05F, 0.05F), 1.6F), sp.getX(), sp.getY() + 1.4, sp.getZ(),
                40, 0.5, 0.5, 0.5, 0.1);
        LfUtil.ring(level, sp.position().add(0, 0.1, 0), CHAINSAW_COLOR, 14, 4F);
    }

    public static boolean chainsaw(Player p) {
        return SAW.containsKey(p.getUUID()) && p.hasEffect(LfRegistry.CHAINSAW_DEVIL);
    }

    // =====================================================================================
    //  Contrato Faustiano
    // =====================================================================================

    public static void startFaust(ServerPlayer sp, int ticks, int needed) {
        if (FAUST.containsKey(sp.getUUID())) {
            LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.faust_busy").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = sp.serverLevel();
        Contract c = new Contract();
        c.end = level.getGameTime() + ticks;
        c.needed = Math.max(1, needed);
        FAUST.put(sp.getUUID(), c);
        sp.addEffect(new MobEffectInstance(LfRegistry.FAUST_CONTRACT, ticks + 40, 0, false, false, true));
        if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
        LfStates.set(sp, LfNet.S_FAUST, ticks, 0);
        LfUtil.fx(level, LfNet.SEAL, sp.position().add(0, 0.05, 0), new Vec3(0, 1, 0), FAUST_COLOR, 40, 2.6F);
        LfUtil.banner(sp, "announce.isekai_mahou.legends_f.faust", FAUST_COLOR, false);
        LfUtil.sound(level, sp.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.6F);
        LfUtil.sound(level, sp.position(), SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.6F);
        LfUtil.chat(sp, Component.translatable("message.isekai_mahou.legends_f.faust_terms", c.needed, ticks / 20)
                .withStyle(ChatFormatting.DARK_RED));
    }

    private static void fulfill(ServerPlayer sp) {
        FAUST.remove(sp.getUUID());
        ServerLevel level = sp.serverLevel();
        sp.removeEffect(LfRegistry.FAUST_CONTRACT);
        LfStates.clear(sp, LfNet.S_FAUST);
        sp.setHealth(sp.getMaxHealth());
        MagicData d = MagicApi.get(sp);
        d.addMana(d.maxMana() * 0.5F);
        MagicApi.giveXp(sp, 30 + 15 * d.circle(), "contract");
        LfUtil.banner(sp, "announce.isekai_mahou.legends_f.faust_done", 0xFFC84A, false);
        LfUtil.fx(level, LfNet.SEAL, sp.position().add(0, 0.05, 0), new Vec3(0, 1, 0), 0xFFC84A, 30, 3F);
        LfUtil.sound(level, sp.position(), SoundEvents.PLAYER_LEVELUP, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.5, 0.6, 0.5, 0.3);
    }

    private static void punish(ServerPlayer sp) {
        FAUST.remove(sp.getUUID());
        ServerLevel level = sp.serverLevel();
        sp.removeEffect(LfRegistry.FAUST_CONTRACT);
        LfStates.clear(sp, LfNet.S_FAUST);
        sp.setHealth(Math.max(1F, sp.getHealth() - sp.getMaxHealth() * 0.25F));
        sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
        LfUtil.banner(sp, "announce.isekai_mahou.legends_f.faust_fail", FAUST_COLOR, false);
        LfUtil.sound(level, sp.position(), SoundEvents.WITHER_HURT, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, sp.getX(), sp.getY() + 1, sp.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
    }

    // =====================================================================================
    //  Meditação e Modo Sábio
    // =====================================================================================

    public static void startMeditation(ServerPlayer sp, int sageTicks) {
        Med m = new Med();
        m.start = sp.serverLevel().getGameTime();
        m.pos = sp.position();
        m.sageTicks = sageTicks;
        MEDITATION.put(sp.getUUID(), m);
        LfStates.set(sp, LfNet.S_MEDITATE, MEDITATE_TICKS, 0);
        LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.sage_meditate").withStyle(ChatFormatting.GOLD));
        LfUtil.sound(sp.serverLevel(), sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1F, 0.5F);
    }

    private static void enterSage(ServerPlayer sp, int ticks) {
        ServerLevel level = sp.serverLevel();
        SAGE.put(sp.getUUID(), level.getGameTime() + ticks);
        sp.addEffect(new MobEffectInstance(LfRegistry.SAGE_MODE, ticks, 0, false, false, true));
        LfStates.set(sp, LfNet.S_SAGE, ticks, 0);
        LfUtil.banner(sp, "announce.isekai_mahou.legends_f.sage", SAGE_COLOR, true);
        LfUtil.ring(level, sp.position().add(0, 0.1, 0), SAGE_COLOR, 20, 8F);
        LfUtil.sound(level, sp.position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, 0.8F);
        LfUtil.sound(level, sp.position(), SoundEvents.FROG_AMBIENT, 1.2F, 0.7F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.6, 0.8, 0.6, 0.05);
    }

    public static boolean sage(Player p) {
        return SAGE.containsKey(p.getUUID()) && p.hasEffect(LfRegistry.SAGE_MODE);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        // Meditação: parado, absorvendo energia natural
        Iterator<Map.Entry<UUID, Med>> mi = MEDITATION.entrySet().iterator();
        while (mi.hasNext()) {
            Map.Entry<UUID, Med> en = mi.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            Med m = en.getValue();
            if (sp == null || !sp.isAlive()) {
                mi.remove();
                continue;
            }
            Vec3 p = sp.position();
            if (p.subtract(m.pos).horizontalDistanceSqr() > 0.35 * 0.35 || Math.abs(p.y - m.pos.y) > 0.6) {
                mi.remove();
                LfStates.clear(sp, LfNet.S_MEDITATE);
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.sage_broken").withStyle(ChatFormatting.RED));
                continue;
            }
            ServerLevel level = sp.serverLevel();
            if (now % 2 == 0) {
                double a = level.random.nextDouble() * Math.PI * 2, r = 3 + level.random.nextDouble() * 3;
                Vec3 from = p.add(Math.cos(a) * r, 0.2 + level.random.nextDouble() * 2, Math.sin(a) * r);
                Vec3 v = p.add(0, 1, 0).subtract(from).scale(0.12);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1);
                level.sendParticles(new DustParticleOptions(new Vector3f(0.95F, 0.55F, 0.15F), 1.0F), from.x, from.y, from.z, 1, 0, 0, 0, 0);
            }
            if (now - m.start >= MEDITATE_TICKS) {
                mi.remove();
                enterSage(sp, m.sageTicks);
            }
        }
        // Modo Sábio: sente os inimigos
        Iterator<Map.Entry<UUID, Long>> si = SAGE.entrySet().iterator();
        while (si.hasNext()) {
            Map.Entry<UUID, Long> en = si.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null || en.getValue() <= now || !sp.hasEffect(LfRegistry.SAGE_MODE)) {
                si.remove();
                if (sp != null) LfStates.clear(sp, LfNet.S_SAGE);
                continue;
            }
            if (now % 20 == 0) sense(sp);
        }
        // Motosserra: rasga quem está na frente enquanto corre
        Iterator<Map.Entry<UUID, Saw>> ci = SAW.entrySet().iterator();
        while (ci.hasNext()) {
            Map.Entry<UUID, Saw> en = ci.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null || en.getValue().end <= now || !sp.hasEffect(LfRegistry.CHAINSAW_DEVIL)) {
                ci.remove();
                if (sp != null) {
                    sp.removeEffect(LfRegistry.CHAINSAW_DEVIL);
                    LfStates.clear(sp, LfNet.S_CHAINSAW);
                }
                continue;
            }
            if (now % 20 == 0) LfUtil.sound(sp.serverLevel(), sp.position(), SoundEvents.BEE_LOOP_AGGRESSIVE, 0.5F, 0.45F);
            if (sp.isSprinting() && now % 6 == 0) rush(sp, en.getValue());
        }
        // Contrato: prazo
        if (now % 20 == 0) {
            List<ServerPlayer> expired = new java.util.ArrayList<>();
            Iterator<Map.Entry<UUID, Contract>> fi = FAUST.entrySet().iterator();
            while (fi.hasNext()) {
                Map.Entry<UUID, Contract> en = fi.next();
                ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
                Contract c = en.getValue();
                if (sp == null || !sp.isAlive()) {
                    fi.remove();
                    continue;
                }
                if (c.end <= now) {
                    expired.add(sp);
                    continue;
                }
                LfUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_f.faust_progress", c.kills, c.needed,
                        (c.end - now) / 20).withStyle(ChatFormatting.DARK_RED));
            }
            for (ServerPlayer sp : expired) punish(sp);
        }
    }

    private static void sense(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1F, 0.55F, 0.1F), 1.4F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(20),
                e -> e.isAlive() && e != sp && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == sp)))) {
            level.sendParticles(sp, dust, true, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 3, 0.1, 0.2, 0.1, 0);
        }
    }

    private static void rush(ServerPlayer sp, Saw s) {
        ServerLevel level = sp.serverLevel();
        Vec3 look = sp.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) return;
        Vec3 front = sp.position().add(look.normalize().scale(1.2)).add(0, 0.9, 0);
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, new AABB(front, front).inflate(1.1),
                e -> e != sp && e.isAlive() && !(e instanceof ArmorStand) && !e.isAlliedTo(sp)
                        && !(e instanceof Player p && !level.getServer().isPvpAllowed()) && !(e instanceof net.minecraft.world.entity.OwnableEntity oe && sp.getUUID().equals(oe.getOwnerUUID())));
        for (LivingEntity e : hit) {
            e.hurt(sp.damageSources().playerAttack(sp), 5F + 2F * s.amp);
            level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.3, 0.3, 0.3, 0.3);
        }
        if (!hit.isEmpty()) LfUtil.sound(level, front, SoundEvents.GRINDSTONE_USE, 1.0F, 1.6F);
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource src = e.getSource();
        float amount = e.getAmount();
        // Barreira Defensiva Geral: dano vindo de fora para quem está dentro
        if (amount > 0 && BarrierEntity.shield(level, victim, src.getEntity(), src.getSourcePosition(), amount)) {
            e.setCanceled(true);
            return;
        }
        // Quem dorme não ataca
        if (src.getEntity() instanceof LivingEntity att && att == src.getDirectEntity() && att.hasEffect(LfRegistry.ENCHANTED_SLEEP)) {
            e.setCanceled(true);
            return;
        }
        // Despertar doloroso
        if (victim.hasEffect(LfRegistry.ENCHANTED_SLEEP) && amount > 0) {
            e.setAmount(amount *= 1.5F);
            victim.removeEffect(LfRegistry.ENCHANTED_SLEEP);
            LfStates.clear(victim, LfNet.S_SLEEP);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight(), victim.getZ(), 10, 0.3, 0.2, 0.3, 0.2);
            LfUtil.sound(level, victim.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.2F);
        }
        // Mana Oculta: o primeiro golpe revela tudo de uma vez
        if (src.getEntity() instanceof LivingEntity att && att.hasEffect(LfRegistry.MANA_CONCEALED) && amount > 0) {
            float mult = src.is(CoreRegistries.SPELL_DAMAGE) ? 1.6F : 1.3F;
            e.setAmount(amount *= mult);
            att.removeEffect(LfRegistry.MANA_CONCEALED);
            LfStates.clear(att, LfNet.S_CONCEAL);
            LfUtil.ring(level, att.position().add(0, 1, 0), 0x8FE6FF, 14, 4F);
            LfUtil.banner(att, "announce.isekai_mahou.legends_f.reveal", 0x8FE6FF, false);
            LfUtil.sound(level, att.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.8F);
        }
        if (!(src.getEntity() instanceof ServerPlayer sp)) return;
        ShadowArmy.ownerAttacked(sp.getUUID(), victim, level.getGameTime());
        boolean melee = src.getDirectEntity() == sp && !src.is(CoreRegistries.SPELL_DAMAGE);
        if (!melee || victim == sp) return;
        // Motosserra: sangra e o sangue cura
        if (chainsaw(sp)) {
            Statuses.apply(victim, Statuses.Kind.BLEEDING, 2, 120, sp, 1F);
            sp.heal(amount * 0.25F);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.7F, 0.02F, 0.02F), 1.4F), victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 14, 0.3, 0.3, 0.3, 0.2);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 6, 0.2, 0.2, 0.2, 0.4);
            LfUtil.sound(level, victim.position(), SoundEvents.GRINDSTONE_USE, 1.0F, 1.4F);
        }
        // Modo Sábio: onda de energia natural em volta do alvo
        if (sage(sp) && !inWave) {
            inWave = true;
            try {
                for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(2.5),
                        o -> o != victim && o != sp && o.isAlive() && (o instanceof Enemy || (o instanceof Mob m && m.getTarget() == sp)))) {
                    o.hurt(sp.damageSources().playerAttack(sp), 4F);
                    Vec3 d = o.position().subtract(victim.position()).multiply(1, 0, 1);
                    if (d.lengthSqr() > 1.0E-4) o.knockback(0.5, -d.normalize().x, -d.normalize().z);
                }
            } finally {
                inWave = false;
            }
            LfUtil.ring(level, victim.position().add(0, 0.2, 0), SAGE_COLOR, 8, 2.5F);
        }
    }

    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        // Coração do demônio: uma vez por transformação, fica de pé
        if (dead instanceof ServerPlayer sp) {
            Saw s = SAW.get(sp.getUUID());
            if (s != null && !s.heartUsed && sp.hasEffect(LfRegistry.CHAINSAW_DEVIL)) {
                s.heartUsed = true;
                e.setCanceled(true);
                sp.setHealth(Math.max(1F, sp.getMaxHealth() * 0.3F));
                LfUtil.banner(sp, "announce.isekai_mahou.legends_f.chainsaw_heart", CHAINSAW_COLOR, false);
                LfUtil.sound(level, sp.position(), SoundEvents.GRINDSTONE_USE, 1.5F, 0.5F);
                LfUtil.sound(level, sp.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 1.0F);
                level.sendParticles(new DustParticleOptions(new Vector3f(0.7F, 0.02F, 0.02F), 2F), sp.getX(), sp.getY() + 1, sp.getZ(),
                        50, 0.5, 0.7, 0.5, 0.2);
                return;
            }
            FAUST.remove(sp.getUUID());
            MEDITATION.remove(sp.getUUID());
        }
        // Contrato: conta as almas
        if (e.getSource().getEntity() instanceof ServerPlayer killer && killer != dead && !(dead instanceof ArmorStand)) {
            Contract c = FAUST.get(killer.getUUID());
            if (c != null && !(dead instanceof net.minecraft.world.entity.OwnableEntity oe && killer.getUUID().equals(oe.getOwnerUUID()))) {
                c.kills++;
                LfStates.value(killer, LfNet.S_FAUST, Math.min(100, c.kills * 100 / c.needed));
                if (c.kills >= c.needed) fulfill(killer);
            }
        }
    }

    static void onChangeTarget(LivingChangeTargetEvent e) {
        LivingEntity mob = e.getEntity();
        LivingEntity t = e.getNewAboutToBeSetTarget();
        if (t == null || mob.level().isClientSide) return;
        if (mob.hasEffect(LfRegistry.ENCHANTED_SLEEP)) {
            e.setCanceled(true);
            return;
        }
        if (t.hasEffect(LfRegistry.MANA_CONCEALED) && mob.distanceToSqr(t) > 5 * 5 && mob.getLastHurtByMob() != t) e.setCanceled(true);
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        SAW.remove(id);
        FAUST.remove(id);
        MEDITATION.remove(id);
        SAGE.remove(id);
        ShadowArmy.recallAll(id, false);
    }

    static void clear() {
        SAW.clear();
        FAUST.clear();
        MEDITATION.clear();
        SAGE.clear();
        inWave = false;
    }

    private Transformations() {}
}
