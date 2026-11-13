package com.renanpontes.isekaimahou.feature.boss_music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ENTRADA dos chefes sincronizada com a música (v1.3.1). Na 1ª vez que um jogador chega perto de um chefe:
 * <ol>
 * <li>a música do tema começa do INÍCIO para todos que veem o chefe (o cliente segura a música de proximidade até aqui);</li>
 * <li>durante a intro da faixa o chefe fica parado e invulnerável, energia subindo em volta, tremor crescendo, faixas de
 * cinema e o título na tela (cliente: {@code BossEntranceFx});</li>
 * <li>no "drop" da música ({@link BossMusicCues}) ele ACORDA: rugido, onda de choque que empurra (sem dano), clarão,
 * nome gigante — e a luta começa.</li>
 * </ol>
 * Uma vez por chefe (persistentData). Se o servidor fechar no meio, o chefe volta ao normal ao carregar.
 */
public final class BossEntrance {
    static final String DONE = "isekai_mahou.entrance_done";
    static final String FROZEN = "isekai_mahou.entrance_frozen";
    private static final double TRIGGER = 28;
    /** Mesmo com a música já forte desde o começo, 2 s para o rugido e o nome aparecerem. */
    private static final int MIN_HOLD = 40;
    /** Partes de chefes, invocações e provas: sem entrada própria. */
    private static final Set<String> SKIP = Set.of("trial_avatar", "trial_sentinel", "false_hydra_head", "primordial_shade",
            "primordial_chain", "menos_giant");

    private static final Set<Mob> PENDING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Mob, Run> ACTIVE = new WeakHashMap<>();

    private static final class Run {
        final String theme;
        final int awaken;
        int t;

        Run(String theme, int awaken) {
            this.theme = theme;
            this.awaken = awaken;
        }
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(BossEntrance::onJoin);
        NeoForge.EVENT_BUS.addListener(BossEntrance::onLeave);
        NeoForge.EVENT_BUS.addListener(BossEntrance::onTick);
        NeoForge.EVENT_BUS.addListener(BossEntrance::onDamage);
        NeoForge.EVENT_BUS.addListener(BossEntrance::onStartTracking);
    }

    static boolean eligible(Entity e) {
        if (!(e instanceof Mob m) || !m.getType().is(BossKit.BOSSES_TAG)) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType());
        return IsekaiMahou.MODID.equals(id.getNamespace()) && !SKIP.contains(id.getPath());
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m)) return;
        CompoundTag pd = m.getPersistentData();
        if (pd.getBoolean(FROZEN)) {
            // o servidor fechou no meio da entrada: devolve a IA (e não repete a entrada)
            m.setNoAi(false);
            pd.remove(FROZEN);
            pd.putBoolean(DONE, true);
        }
        if (!pd.getBoolean(DONE) && eligible(m)) PENDING.add(m);
    }

    private static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m)) return;
        PENDING.remove(m);
        ACTIVE.remove(m);
    }

    private static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getTarget() instanceof Mob m)) return;
        if (PENDING.contains(m) || ACTIVE.containsKey(m)) {
            PacketDistributor.sendToPlayer(sp, new BossMusicNet.Pending(m.getId()));
        }
    }

    private static void onDamage(LivingIncomingDamageEvent e) {
        if (ACTIVE.isEmpty()) return;
        if (ACTIVE.containsKey(e.getEntity()) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
            return;
        }
        Entity src = e.getSource().getEntity();
        if (src != null && ACTIVE.containsKey(src)) e.setCanceled(true);
    }

    private static void onTick(ServerTickEvent.Post e) {
        if (!ACTIVE.isEmpty()) {
            List<Mob> done = new ArrayList<>();
            for (Map.Entry<Mob, Run> en : ACTIVE.entrySet()) {
                Mob m = en.getKey();
                Run r = en.getValue();
                if (m.isRemoved() || !m.isAlive() || !(m.level() instanceof ServerLevel sl)) {
                    done.add(m);
                    continue;
                }
                if (tickRun(sl, m, r)) done.add(m);
            }
            for (Mob m : done) ACTIVE.remove(m);
        }
        if (PENDING.isEmpty() || e.getServer().getTickCount() % 5 != 0) return;
        Iterator<Mob> it = PENDING.iterator();
        List<Mob> start = new ArrayList<>();
        while (it.hasNext()) {
            Mob m = it.next();
            if (m.isRemoved() || !m.isAlive()) {
                it.remove();
                continue;
            }
            Player p = m.level().getNearestPlayer(m.getX(), m.getY(), m.getZ(), TRIGGER, EntitySelector.NO_SPECTATORS);
            if (p == null || (p.distanceToSqr(m) > 144 && !m.hasLineOfSight(p))) continue;
            it.remove();
            start.add(m);
        }
        for (Mob m : start) start(m);
    }

    /** Começa a entrada agora (também usado pelo comando de teste). */
    public static void start(Mob m) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        PENDING.remove(m);
        String theme = BossThemes.themeOf(m);
        int awaken = Math.max(MIN_HOLD, BossMusicCues.introTicks(theme));
        m.setNoAi(true);
        m.setDeltaMovement(Vec3.ZERO);
        m.getPersistentData().putBoolean(FROZEN, true);
        ACTIVE.put(m, new Run(theme, awaken));
        PacketDistributor.sendToPlayersTrackingEntity(m, new BossMusicNet.Entrance(m.getId(), theme, awaken));
        sl.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    /** Um tick da entrada; true = acabou. */
    private static boolean tickRun(ServerLevel sl, Mob m, Run r) {
        r.t++;
        if (!m.isNoAi()) m.setNoAi(true);
        double w = Math.max(1.0, m.getBbWidth() * 0.8);
        double h = m.getBbHeight();
        float prog = Mth.clamp(r.t / (float) r.awaken, 0F, 1F);
        if (r.t < r.awaken) {
            // energia em espiral subindo; nos últimos 25 ticks ela "puxa" para dentro do chefe
            if (r.t % 3 == 0) {
                ParticleOptions pt = particle(r.theme);
                int n = 3 + (int) (prog * 6);
                boolean converge = r.awaken - r.t < 25;
                for (int i = 0; i < n; i++) {
                    double a = (r.t * 0.35) + i * (Math.PI * 2 / n);
                    double rad = w * (converge ? 2.6 : 1.6 + prog);
                    double x = m.getX() + Math.cos(a) * rad;
                    double z = m.getZ() + Math.sin(a) * rad;
                    double y = m.getY() + (i % 3) * h * 0.3 + 0.2;
                    if (converge) {
                        sl.sendParticles(pt, x, y, z, 0, m.getX() - x, h * 0.4, m.getZ() - z, 0.12);
                    } else {
                        sl.sendParticles(pt, x, y, z, 1, 0.05, 0.25, 0.05, 0.02);
                    }
                }
            }
            if (r.t % 20 == 0) {
                BossKit.shake(sl, m.position(), 40, 0.15F + prog * 0.9F, 24);
                sl.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE,
                        1.2F + prog * 2F, 0.6F + prog * 0.3F);
            }
            return false;
        }
        awaken(sl, m, r);
        return true;
    }

    private static void awaken(ServerLevel sl, Mob m, Run r) {
        m.setNoAi(false);
        m.getPersistentData().remove(FROZEN);
        m.getPersistentData().putBoolean(DONE, true);
        Vec3 c = m.position();
        double h = m.getBbHeight();
        SoundEvent roar = h >= 4 ? SoundEvents.ENDER_DRAGON_GROWL : h >= 2.4 ? SoundEvents.RAVAGER_ROAR : SoundEvents.WARDEN_ROAR;
        sl.playSound(null, c.x, c.y, c.z, roar, SoundSource.HOSTILE, 4F, 0.85F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2F, 0.6F);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + h * 0.5, c.z, 1, 0, 0, 0, 0);
        ParticleOptions pt = particle(r.theme);
        for (int i = 0; i < 28; i++) {
            double a = i * (Math.PI * 2 / 28);
            sl.sendParticles(pt, c.x + Math.cos(a) * 1.5, c.y + 0.3, c.z + Math.sin(a) * 1.5, 0, Math.cos(a), 0.05, Math.sin(a), 0.9);
            if (i % 2 == 0) sl.sendParticles(ParticleTypes.EXPLOSION, c.x + Math.cos(a) * 4, c.y + 0.5, c.z + Math.sin(a) * 4, 1, 0, 0, 0, 0);
        }
        // onda de choque: empurra quem está perto (sem dano)
        for (ServerPlayer p : sl.players()) {
            double d2 = p.distanceToSqr(c);
            if (d2 > 100 || p.isSpectator()) continue;
            Vec3 dir = p.position().subtract(c).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
            double k = 0.9 * (1 - Math.sqrt(d2) / 10);
            p.push(dir.x * k, 0.35, dir.z * k);
            p.hurtMarked = true;
        }
        BossKit.shake(sl, c, 48, 2.2F, 30);
        BossKit.flashNear(sl, c, 48, 0xFFFFFF, 0.55F, 12);
        PacketDistributor.sendToPlayersTrackingEntity(m, new BossMusicNet.Release(m.getId()));
    }

    private static ParticleOptions particle(String theme) {
        return switch (theme) {
            case "ulquiorra", "ulquiorra_segunda_etapa", "grimmjow", "hollow_king" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "barragan" -> ParticleTypes.WHITE_ASH;
            case "seraph" -> ParticleTypes.END_ROD;
            case "twilight_herald", "primordial_dragon" -> ParticleTypes.FLAME;
            case "lich" -> ParticleTypes.SOUL;
            case "abyss_sovereign" -> ParticleTypes.SCULK_SOUL;
            case "elemental_guardian" -> ParticleTypes.ELECTRIC_SPARK;
            default -> ParticleTypes.REVERSE_PORTAL;
        };
    }

    /** Comando de teste: chefes a até {@code range} blocos voltam a ter entrada (a próxima aproximação dispara). */
    static int rearm(ServerPlayer p, double range) {
        int n = 0;
        for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), BossEntrance::eligible)) {
            m.getPersistentData().remove(DONE);
            PENDING.add(m);
            PacketDistributor.sendToPlayer(p, new BossMusicNet.Pending(m.getId()));
            n++;
        }
        return n;
    }

    private BossEntrance() {}
}
