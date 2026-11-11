package com.renanpontes.isekaimahou.feature.creatures_a.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.BossRewards;
import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Peças comuns dos chefes do módulo (Rei Slime, Rei Goblin): barra de chefe, apresentação (cutscene + início da luta
 * com escala de grupo), tenacidade contra controle, contador de alvo inalcançável (anti-pilar), modo provação
 * ({@link BossKit#TRIAL_TAG}: sem recuperação por ociosidade, volta para a arena), recompensa de grupo
 * ({@link BossRewards}) e utilidades de dano/efeitos das ondas de choque.
 */
public final class BossFeatures {
    private static final double INTRO_RANGE = 18.0;
    private static final double BAR_RANGE = 40.0;
    /** Duração máxima de um controle forte (atordoar/congelar/enraizar) num chefe e a imunidade que vem depois. */
    private static final int CC_MAX = 30;
    private static final int CC_IMMUNITY = 100;
    /** Sem alvo e sem jogadores perto por 30 s: o chefe começa a se recuperar (fora das provações). */
    private static final int IDLE_REGEN_AFTER = 600;
    /** Quem chega no meio da luta ganha só um "flash" curto da apresentação (a câmera não fica presa com o chefe atacando). */
    private static final int LATE_INTRO_TICKS = 30;
    /** Duração da cutscene de mudança de fase (o chefe deve pausar os ataques por esse tempo). */
    public static final int PHASE_CUTSCENE_TICKS = 45;
    /** Velocidade de fuga assumida para o aviso de pouso (blocos/tick, andando rápido) e tempo de reação (ticks). */
    private static final double ESCAPE_SPEED = 0.25;
    private static final int REACTION_TICKS = 6;

    private final Mob boss;
    private final String key;
    private final int rgb;
    private final BossEvent.BossBarColor color;
    private final int rewardXp;
    private final Predicate<Entity> ally;
    private final Set<UUID> introSeen = new HashSet<>();
    private ServerBossEvent bar;
    private boolean engaged;
    private boolean phaseShown;
    private Runnable onEngage = () -> { };
    /** Duração da cutscene de apresentação = duração da intro do chefe (invulnerável); depois a luta começa de verdade. */
    private int introCutscene = 60;
    private long ccImmuneUntil;
    private long lastResistFx;
    private int unreachableTicks;
    private int idleTicks;
    private int leashCooldown;

    /**
     * @param rewardXp XP mágico para cada jogador que lutou ({@link BossRewards#grant})
     * @param ally     quem o chefe NÃO machuca com golpes em área (os próprios súditos)
     */
    public BossFeatures(Mob boss, String key, int rgb, BossEvent.BossBarColor color, int rewardXp, Predicate<Entity> ally) {
        this.boss = boss;
        this.key = key;
        this.rgb = rgb;
        this.color = color;
        this.rewardXp = rewardXp;
        this.ally = ally;
    }

    /** Chamado uma vez quando a luta começa (primeira apresentação): o chefe faz a intro dele. */
    public void onEngage(Runnable action) {
        this.onEngage = action;
    }

    /** Ticks da apresentação do chefe (a cutscene dura o mesmo: a câmera solta quando ele começa a atacar). */
    public BossFeatures introLength(int ticks) {
        this.introCutscene = Math.max(20, ticks);
        return this;
    }

    private ServerBossEvent bar() {
        if (bar == null) {
            bar = new ServerBossEvent(boss.getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        return bar;
    }

    /** Chave de tradução {@code <grupo>.isekai_mahou.<chefe>.<nome>}. */
    private String langKey(String group, String name) {
        return group + ".isekai_mahou." + key + "." + name;
    }

    public int color() {
        return rgb;
    }

    /** Quem passa a "ver" o chefe só ganha a barra se estiver perto (o Rei Slime nasce solto pelo pântano). */
    public void startSeen(ServerPlayer player) {
        if (boss.isAlive() && player.distanceToSqr(boss) < BAR_RANGE * BAR_RANGE) bar().addPlayer(player);
    }

    public void stopSeen(ServerPlayer player) {
        if (bar != null) bar.removePlayer(player);
    }

    /** Barra de chefe só para quem está a até {@link #BAR_RANGE} blocos (com folga para não piscar na borda). */
    private void updateBarViewers(ServerLevel level) {
        ServerBossEvent b = bar();
        double keep = (BAR_RANGE + 8) * (BAR_RANGE + 8);
        for (ServerPlayer p : List.copyOf(b.getPlayers())) {
            if (p.isRemoved() || p.level() != level || p.distanceToSqr(boss) > keep) b.removePlayer(p);
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(boss) < BAR_RANGE * BAR_RANGE) b.addPlayer(p);
        }
    }

    /** Chefe já foi "visto" por alguém (a luta começou; não some mais por distância). */
    public boolean engaged() {
        return engaged;
    }

    public void onNameChanged() {
        if (bar != null) bar.setName(boss.getDisplayName());
    }

    private static boolean realPlayer(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    /** Chamar todo tick no servidor. */
    public void tick() {
        tick(false);
    }

    /**
     * Chamar todo tick no servidor. {@code busy}: o chefe está no meio de um golpe/salto — a volta para a arena espera
     * (senão o golpe já telegrafado acertaria longe do aviso).
     */
    public void tick(boolean busy) {
        // Morrendo (animação de morte): a barra já foi tirada em onDeath; nada de cutscene/barra nova.
        if (!boss.isAlive()) return;
        bar().setProgress(Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0F, 1F));
        if (!(boss.level() instanceof ServerLevel level)) return;
        if (leashCooldown > 0) leashCooldown--;
        boolean trial = BossKit.isTrialBoss(boss);
        if (engaged) {
            if (trial) {
                if (!busy) arenaLeash();
            } else {
                idleRegen(level);
            }
        }
        if (boss.tickCount % 10 != 0) return;
        updateBarViewers(level);
        // Provação: a arena inteira "vê" o chefe (sem precisar de linha de visão).
        double range = trial ? Math.max(INTRO_RANGE, BossKit.arenaRadius(boss) + 8) : INTRO_RANGE;
        List<ServerPlayer> near = level.getPlayers(p -> realPlayer(p) && p.distanceToSqr(boss) < range * range
                && (trial || boss.hasLineOfSight(p)));
        if (near.isEmpty()) return;
        if (introSeen.isEmpty()) {
            startFight(level);
            return;
        }
        Component title = boss.getDisplayName();
        Component subtitle = Component.translatable(langKey("boss", "subtitle"));
        for (ServerPlayer p : near) {
            if (introSeen.add(p.getUUID())) {
                Cutscenes.play(p, "boss_intro", boss, title, subtitle, rgb, LATE_INTRO_TICKS, false);
                engaged = true;
            }
        }
    }

    /** Começa a luta agora (ex.: o chefe foi atacado de longe antes de alguém chegar perto). */
    public void engage() {
        if (introSeen.isEmpty() && boss.level() instanceof ServerLevel level) startFight(level);
    }

    /** Primeira aparição: todo mundo por perto assiste junto; o chefe se apresenta (intro + escala de grupo). */
    private void startFight(ServerLevel level) {
        Component title = boss.getDisplayName();
        Component subtitle = Component.translatable(langKey("boss", "subtitle"));
        Cutscenes.playNear(boss, INTRO_RANGE + 12, "boss_intro", title, subtitle, rgb, introCutscene, null);
        for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(boss) < (INTRO_RANGE + 12) * (INTRO_RANGE + 12))) {
            introSeen.add(p.getUUID());
        }
        // Atacado de longe por alguém fora do raio: a lista não pode ficar vazia (senão a intro repetiria).
        if (introSeen.isEmpty()) introSeen.add(new UUID(0L, 0L));
        boolean first = !engaged;
        engaged = true;
        if (first) onEngage.run();
    }

    /** Provação: o chefe não sai da arena (salta de volta para o centro). */
    private void arenaLeash() {
        Vec3 center = BossKit.arenaCenter(boss);
        double r = BossKit.arenaRadius(boss);
        if (center == null || r <= 0 || leashCooldown > 0 || !boss.onGround()) return;
        double dx = boss.getX() - center.x, dz = boss.getZ() - center.z;
        if (dx * dx + dz * dz > (r - 1) * (r - 1)) {
            BossKit.leapTo(boss, center, 1.0);
            leashCooldown = 80;
        }
    }

    /** Fora das provações: largado sozinho por 30 s, recupera 2% da vida por segundo (bater e fugir não vale). */
    private void idleRegen(ServerLevel level) {
        if (boss.tickCount % 20 != 0) return;
        LivingEntity t = boss.getTarget();
        boolean alone = (t == null || !t.isAlive())
                && level.getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), 32, e -> e instanceof Player p && realPlayer(p)) == null;
        idleTicks = alone ? idleTicks + 20 : 0;
        if (idleTicks > IDLE_REGEN_AFTER && boss.getHealth() < boss.getMaxHealth()) {
            boss.heal(boss.getMaxHealth() * 0.02F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, boss.getX(), boss.getY() + boss.getBbHeight() * 0.8, boss.getZ(),
                    3, boss.getBbWidth() * 0.3, 0.2, boss.getBbWidth() * 0.3, 0.0);
        }
    }

    /** Cutscene curta de mudança de fase (uma vez só). */
    public void phaseChange(String announceKey) {
        if (phaseShown) return;
        phaseShown = true;
        Component sub = Component.translatable(langKey("boss", "phase"));
        Cutscenes.playNear(boss, 32, "boss_phase", boss.getDisplayName(), sub, rgb, PHASE_CUTSCENE_TICKS, null);
        announce(announceKey);
    }

    /** Fala do chefe (estilo de chefe) para quem estiver perto. */
    public void announce(String announceKey) {
        Announce.send(boss, Component.translatable(langKey("announce", announceKey)), rgb, Announce.STYLE_BOSS);
    }

    /** Morte: XP + {@code boss.<id>} para todo o grupo que lutou; anúncio; tira a barra e os avisos no chão. */
    public void onDeath() {
        if (!(boss.level() instanceof ServerLevel)) return;
        BossRewards.grant(boss, rewardXp, 48);
        Announce.send(boss, Component.translatable(langKey("announce", "defeated")), rgb, Announce.STYLE_SYSTEM);
        Telegraph.cancelAll(boss);
        if (bar != null) bar.removeAllPlayers();
    }

    // ------------------------------------------------------------------ combate

    /** Alvo legítimo dos golpes em área (não é o chefe, nem súdito, nem jogador criativo/espectador). */
    public boolean isFoe(Entity e) {
        if (e == boss || !(e instanceof LivingEntity le) || !le.isAlive() || ally.test(e)) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    /**
     * Dano físico do chefe com empurrão para fora de {@code from} (e um pouco para cima) e um efeito opcional.
     * @return se o golpe entrou
     */
    public boolean hurtFoe(LivingEntity e, float damage, Vec3 from, double push, @Nullable MobEffectInstance effect) {
        if (!e.hurt(boss.damageSources().mobAttack(boss), damage)) return false;
        Vec3 dir = e.position().subtract(from).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, boss.getYRot()) : dir.normalize();
        double k = push * (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (k > 0) {
            e.push(dir.x * k, 0.3 + 0.2 * k, dir.z * k);
            e.hurtMarked = true;
        }
        if (effect != null) e.addEffect(new MobEffectInstance(effect), boss);
        return true;
    }

    /**
     * Conta há quanto tempo o alvo está inalcançável (pilar, barco, voando). Devolve true (e zera) quando passou de
     * {@code ticks} seguidos — hora de o chefe reagir (salto/arremesso/teleporte).
     */
    public boolean unreachable(LivingEntity target, int ticks) {
        unreachableTicks = BossKit.isUnreachable(boss, target) ? unreachableTicks + 1 : 0;
        if (unreachableTicks < ticks) return false;
        unreachableTicks = 0;
        return true;
    }

    /**
     * Tenacidade: controles fortes (atordoar, congelar, enraizar, silenciar, levitar) duram no máx. 1,5 s e deixam o
     * chefe imune a eles por 5 s; lentidão vira no máx. Lentidão II. Devolve o efeito ajustado ou null (resistiu).
     */
    @Nullable
    public MobEffectInstance tenacity(MobEffectInstance e) {
        if (!isHardControl(e.getEffect().value())) {
            if (e.is(MobEffects.MOVEMENT_SLOWDOWN) && (e.getAmplifier() > 1 || e.isInfiniteDuration())) {
                return new MobEffectInstance(e.getEffect(), capped(e, 80), Math.min(e.getAmplifier(), 1), e.isAmbient(), e.isVisible(), e.showIcon());
            }
            return e;
        }
        long now = boss.level().getGameTime();
        if (now < ccImmuneUntil) {
            resistFx(now);
            return null;
        }
        int dur = capped(e, CC_MAX);
        ccImmuneUntil = now + dur + CC_IMMUNITY;
        return new MobEffectInstance(e.getEffect(), dur, Math.min(e.getAmplifier(), 1), e.isAmbient(), e.isVisible(), e.showIcon());
    }

    /** Duração limitada a {@code max} (efeito "infinito" também vira {@code max}). */
    private static int capped(MobEffectInstance e, int max) {
        return e.isInfiniteDuration() ? max : Math.min(e.getDuration(), max);
    }

    /**
     * Força do {@link BossKit#leapAt} para que o voo dure o bastante para sair do círculo de pouso (raio {@code radius}
     * centrado no alvo) andando, com tempo de reação — o aviso no chão é justo a qualquer distância. Usa a mesma conta
     * de tempo do kit; nunca passa de {@code maxPower} (arcos mais altos e lentos de perto).
     */
    public static double fairLeapPower(Mob mob, Vec3 dest, float radius, double maxPower) {
        double dist = Math.hypot(dest.x - mob.getX(), dest.z - mob.getZ());
        double rise = Math.max(0, dest.y - mob.getY());
        double base = 8 + dist * 1.1 + rise * 0.8;
        double minTicks = REACTION_TICKS + (radius + 0.4) / ESCAPE_SPEED;
        return Mth.clamp(base / minTicks, 0.3, maxPower);
    }

    /**
     * Raio de acerto do pouso: o do círculo marcado; se o chefe caiu longe da marca (bateu no teto, numa parede, foi
     * empurrado no ar), só a pegada dele + 1,5 bloco — ninguém leva o golpe cheio num lugar que não estava marcado.
     */
    public static float landingRadius(Mob mob, @Nullable Vec3 mark, float radius) {
        if (mark == null) return radius;
        double off = Math.hypot(mob.getX() - mark.x, mob.getZ() - mark.z);
        return off <= 2.0 ? radius : Math.min(radius, mob.getBbWidth() * 0.5F + 1.5F);
    }

    /** Atordoado ou congelado agora (não começa ataques novos). */
    public boolean controlled() {
        return boss.hasEffect(CoreRegistries.STUNNED) || boss.hasEffect(CoreRegistries.FROZEN);
    }

    /** Atordoar/congelar que ENTROU interrompe o golpe em preparo (recompensa por controlar; a tenacidade evita o "trava-trava"). */
    public static boolean interrupts(MobEffectInstance e) {
        MobEffect v = e.getEffect().value();
        return v == CoreRegistries.STUNNED.get() || v == CoreRegistries.FROZEN.get();
    }

    private static boolean isHardControl(MobEffect v) {
        return v == CoreRegistries.STUNNED.get() || v == CoreRegistries.FROZEN.get() || v == CoreRegistries.ROOTED.get()
                || v == CoreRegistries.SILENCED.get() || v == MobEffects.LEVITATION.value();
    }

    /** Faísca + "clang" quando o chefe ignora um controle (o jogador entende que a imunidade está ativa). */
    private void resistFx(long now) {
        if (now - lastResistFx < 10 || !(boss.level() instanceof ServerLevel sl)) return;
        lastResistFx = now;
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, boss.getX(), boss.getY() + boss.getBbHeight() * 0.7, boss.getZ(),
                14, boss.getBbWidth() * 0.4, boss.getBbHeight() * 0.25, boss.getBbWidth() * 0.4, 0.2);
        sl.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    // ------------------------------------------------------------------ utilidades

    /** Chão firme (com ar em cima) mais perto de {@code y}, procurando de y+3 até y−8; null se não achar. */
    @Nullable
    public static Vec3 groundAt(ServerLevel level, double x, double y, double z) {
        int top = Mth.floor(y) + 3;
        for (int by = top; by >= top - 11; by--) {
            BlockPos below = BlockPos.containing(x, by - 1, z);
            VoxelShape s = level.getBlockState(below).getCollisionShape(level, below);
            if (s.isEmpty()) continue;
            BlockPos at = below.above();
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) continue;
            return new Vec3(x, below.getY() + s.max(Direction.Axis.Y), z);
        }
        return null;
    }

    /** Visual de onda de choque: anel de pedaços do chão + poeira + efeito extra opcional. */
    public static void groundRing(ServerLevel level, Vec3 c, double radius, @Nullable ParticleOptions extra) {
        BlockPos below = BlockPos.containing(c.x, c.y - 0.5, c.z);
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) ground = level.getBlockState(below.below());
        ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground);
        int points = (int) (radius * 8);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            for (double r = radius * 0.4; r <= radius; r += radius * 0.3) {
                level.sendParticles(dust, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 2, 0.1, 0.05, 0.1, 0.15);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 3, radius * 0.25, 0.1, radius * 0.25, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 30, radius * 0.4, 0.1, radius * 0.4, 0.08);
        if (extra != null) level.sendParticles(extra, c.x, c.y + 0.4, c.z, 50, radius * 0.35, 0.3, radius * 0.35, 0.2);
    }

    /** Anel fino de partículas (frente de uma onda de choque) no raio dado. */
    public static void ringFx(ServerLevel level, Vec3 c, double radius, ParticleOptions particle) {
        int points = Math.max(12, (int) (radius * 7));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(particle, c.x + Math.cos(a) * radius, c.y + 0.15, c.z + Math.sin(a) * radius, 1, 0.05, 0.05, 0.05, 0.02);
        }
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : introSeen) list.add(NbtUtils.createUUID(id));
        tag.put("IntroSeen", list);
        tag.putBoolean("Engaged", engaged);
        tag.putBoolean("PhaseShown", phaseShown);
    }

    public void load(CompoundTag tag) {
        introSeen.clear();
        for (Tag t : tag.getList("IntroSeen", Tag.TAG_INT_ARRAY)) introSeen.add(NbtUtils.loadUUID(t));
        // Mundos da v0.6 não tinham "Engaged": quem já tinha sido visto continua em luta (não some por distância).
        engaged = tag.contains("Engaged") ? tag.getBoolean("Engaged") : !introSeen.isEmpty();
        phaseShown = tag.getBoolean("PhaseShown");
    }
}
