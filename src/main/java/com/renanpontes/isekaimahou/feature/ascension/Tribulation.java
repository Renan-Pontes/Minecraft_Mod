package com.renanpontes.isekaimahou.feature.ascension;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.ascension.entity.AscensionShadowEntity;
import com.renanpontes.isekaimahou.feature.ascension.entity.TribulationBoltEntity;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.EventHooks;
import org.joml.Vector3f;

/**
 * Uma Tribulação em andamento (servidor). Fica num registro estático e é atualizada pelo tick do servidor
 * (não pelo tick do bloco), assim ela continua coerente mesmo se o chunk do Núcleo descarregar — nesse caso falha.
 *
 * Linha do tempo: introdução (cutscene, o tributo é absorvido) → Tribulação (raios arcanos, ondas de mobs,
 * a Sombra a partir do Círculo-alvo 5, barra de chefe com o progresso) → sucesso (Círculo +1) ou falha.
 *
 * <p>v1.0 — <b>barreira de runas</b>: o círculo ficou 3 blocos maior ({@link AscensionPattern#boundary}) e vira um
 * domo que PRENDE quem ascende (empurra de volta na parede, traz de volta se for arremessado/teleportado para fora,
 * cancela pérolas/fruta do coro/troca de dimensão — {@link AscensionWard}); mobs, ajudantes e magias passam
 * livremente. Ninguém constrói nem quebra dentro do domo enquanto o ritual dura ({@link #zoneCovers}).
 */
public final class Tribulation {
    /** Chave nos dados persistentes dos mobs invocados: UUID do ritual (some se o ritual não existe mais). */
    public static final String MOB_TAG = "isekai_mahou.tribulation";
    /** Tag de entidade que a varredura de monstros das cidades respeita (por id em texto: módulo cities). */
    private static final String CITY_KEEP_TAG = "isekai_mahou.city_keep";
    public static final int INTRO_TICKS = 60;
    private static final int STRIKE_WARNING = 24;

    public enum Reason {
        LEFT("message.isekai_mahou.ascension.fail.left"),
        DIED("message.isekai_mahou.ascension.fail.died"),
        OFFLINE("message.isekai_mahou.ascension.fail.offline"),
        INTERRUPTED("message.isekai_mahou.ascension.fail.interrupted"),
        ABORTED("message.isekai_mahou.ascension.fail.aborted");

        /** Chave de tradução da mensagem de falha. */
        final String key;

        Reason(String key) { this.key = key; }
    }

    // =====================================================================================
    //  Registro estático das Tribulações ativas
    // =====================================================================================
    private static final Map<UUID, Tribulation> ACTIVE = new LinkedHashMap<>();

    /** Área do domo de um ritual ativo (lida pela proteção de terreno em qualquer thread). */
    public record Zone(ResourceKey<Level> dim, BlockPos core, int boundary, UUID owner) {
        public boolean covers(BlockPos pos) {
            double dx = pos.getX() - core.getX(), dz = pos.getZ() - core.getZ();
            double r = boundary + 1.0;
            return dx * dx + dz * dz <= r * r && pos.getY() >= core.getY() - AscensionPattern.BARRIER_BELOW
                    && pos.getY() <= core.getY() + AscensionPattern.domeHeight(boundary) + 1;
        }
    }

    private static final Map<UUID, Zone> ZONES = new ConcurrentHashMap<>();

    /** Algum domo de Tribulação ativo cobre esta posição? (qualquer thread) */
    public static boolean zoneCovers(ResourceKey<Level> dim, BlockPos pos) {
        if (ZONES.isEmpty()) return false;
        for (Zone z : ZONES.values()) if (z.dim().equals(dim) && z.covers(pos)) return true;
        return false;
    }

    public static boolean isActive(UUID ritualId) {
        Tribulation t = ACTIVE.get(ritualId);
        return t != null && !t.finished;
    }

    public static Tribulation byOwner(UUID player) {
        for (Tribulation t : ACTIVE.values()) if (!t.finished && t.ownerId.equals(player)) return t;
        return null;
    }

    public static Tribulation at(ServerLevel level, BlockPos pos) {
        for (Tribulation t : ACTIVE.values()) if (!t.finished && t.level == level && t.core.equals(pos)) return t;
        return null;
    }

    static void tickAll() {
        if (ACTIVE.isEmpty()) return;
        for (Tribulation t : List.copyOf(ACTIVE.values())) {
            if (!t.finished) t.tick();
        }
        ACTIVE.values().removeIf(t -> t.finished);
    }

    static void clearAll() {
        for (Tribulation t : List.copyOf(ACTIVE.values())) {
            if (t.bar != null) t.bar.removeAllPlayers();
        }
        ACTIVE.clear();
        ZONES.clear();
    }

    // =====================================================================================
    //  Estado
    // =====================================================================================
    public final UUID id = UUID.randomUUID();
    private final ServerLevel level;
    private final BlockPos core;
    private final Vec3 center;
    private final UUID ownerId;
    private final int target;
    private final int radius;
    private final int boundary;
    private final int duration;
    private final List<Element> elements = new ArrayList<>();
    private final int[] waveTimes;
    private final int[] shadowTimes;
    private int nextWave;
    private int nextShadow;
    private int age;
    private int nextStrike;
    private int lastBarrierMsg = -100;
    private boolean finished;
    private ServerBossEvent bar;
    private final List<UUID> mobs = new ArrayList<>();
    private final List<UUID> shadows = new ArrayList<>();
    private final List<Strike> strikes = new ArrayList<>();

    private record Strike(Vec3 pos, int hitAt) {}

    private Tribulation(ServerLevel level, BlockPos core, ServerPlayer owner, int target, AscensionTributes.Tribute tribute) {
        this.level = level;
        this.core = core.immutable();
        this.center = Vec3.atBottomCenterOf(core);
        this.ownerId = owner.getUUID();
        this.target = target;
        this.radius = AscensionPattern.radius(target);
        this.boundary = AscensionPattern.boundary(target);
        this.duration = tribute.durationTicks();

        MagicData data = MagicApi.get(owner);
        for (Element e : Element.BASE) if (data.isAwakened(e)) elements.add(e);
        if (elements.isEmpty()) elements.add(Element.ARCANE);

        int waves = 2 + (target + 1) / 2;
        waveTimes = new int[waves];
        int span = Math.max(40, duration - 200 - 40);
        for (int i = 0; i < waves; i++) waveTimes[i] = 40 + (waves == 1 ? 0 : span * i / (waves - 1));

        if (target >= 9) shadowTimes = new int[] {duration / 5, duration * 3 / 5};
        else if (target >= 5) shadowTimes = new int[] {duration / 5};
        else shadowTimes = new int[0];
    }

    /** Começa a Tribulação (o tributo já foi consumido pelo Núcleo). */
    public static Tribulation start(ServerLevel level, BlockPos core, ServerPlayer owner, int target, AscensionTributes.Tribute tribute) {
        Tribulation t = new Tribulation(level, core, owner, target, tribute);
        ACTIVE.put(t.id, t);
        ZONES.put(t.id, new Zone(level.dimension(), t.core, t.boundary, t.ownerId));

        Component title = Component.translatable("cutscene.isekai_mahou.ascension.title");
        Component subtitle = Component.translatable("cutscene.isekai_mahou.ascension.subtitle", target);
        Cutscenes.playNear(owner, 32, "ascension", title, subtitle, Element.ARCANE.color, INTRO_TICKS, owner);
        Announce.send(owner, Component.translatable("announce.isekai_mahou.ascension.begin", owner.getDisplayName(), target),
                Element.ARCANE.color, Announce.STYLE_SYSTEM);

        level.playSound(null, core, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, core, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.5F, 0.5F);
        return t;
    }

    public UUID ownerId() { return ownerId; }
    public int target() { return target; }
    public boolean finished() { return finished; }

    private ServerPlayer owner() {
        return level.getServer().getPlayerList().getPlayer(ownerId);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    private void tick() {
        if (!level.isLoaded(core) || !(level.getBlockEntity(core) instanceof AscensionCoreBlockEntity)) {
            fail(Reason.INTERRUPTED);
            return;
        }
        ServerPlayer owner = owner();
        if (owner == null) { fail(Reason.OFFLINE); return; }
        if (owner.isDeadOrDying()) { fail(Reason.DIED); return; }
        if (owner.level() != level) { fail(Reason.LEFT); return; }

        double dx = owner.getX() - center.x, dz = owner.getZ() - center.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        anchor(owner, dx, dz, dist);
        // v1.0: a barreira prende quem ascende (antes, sair era falha).
        if (confine(owner, dx, dz, dist)) {
            dx = owner.getX() - center.x;
            dz = owner.getZ() - center.z;
            dist = Math.sqrt(dx * dx + dz * dz);
        }

        age++;
        if (age <= INTRO_TICKS) {
            tickIntro();
            return;
        }
        if (age == INTRO_TICKS + 1) beginTrial(owner);

        int t = age - INTRO_TICKS;
        if (dist > boundary - 1.6 && t % 6 == 0) runeWall(owner, dx, dz);
        if (t % 5 == 0) bar.setProgress(Mth.clamp(t / (float) duration, 0F, 1F));
        if (t % 20 == 0) {
            refreshBarPlayers();
            bar.setName(barName(duration - t));
        }
        if (t % 20 == 10) ElementFx.ring(level, center.add(0, 0.05, 0), Element.ARCANE, boundary, 48);
        if (t % 80 == 0) level.playSound(null, center.x, core.getY() + 2, center.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.2F, 0.6F);
        if (t % 3 == 0) arcaneRain();

        while (nextWave < waveTimes.length && t >= waveTimes[nextWave]) spawnWave(owner, nextWave++);
        while (nextShadow < shadowTimes.length && t >= shadowTimes[nextShadow]) {
            nextShadow++;
            if (aliveShadows() == 0) spawnShadow(owner);
        }
        tickStrikes(owner, t);
        if (t % 20 == 15) keepFocus(owner);

        if (t >= duration) succeed();
    }

    /**
     * A onda luta com quem ascende (e quem ajuda): mob que trocou de alvo para um aldeão/morador da cidade — o altar
     * público fica na Igreja — ou ficou sem alvo volta para o dono do ritual.
     */
    private void keepFocus(ServerPlayer owner) {
        for (UUID u : mobs) {
            if (level.getEntity(u) instanceof Mob m && m.isAlive() && !(m.getTarget() instanceof Player)) m.setTarget(owner);
        }
    }

    private void tickIntro() {
        // O tributo flui dos pedestais para o Núcleo.
        Vec3 top = center.add(0, 1.4, 0);
        if (age % 4 == 0) {
            for (BlockPos p : AscensionPattern.pedestals(core, target)) {
                ElementFx.line(level, Vec3.atBottomCenterOf(p).add(0, 1.0, 0), top, Element.ARCANE);
            }
        }
        double a = age * 0.4;
        for (int i = 0; i < 3; i++) {
            double ang = a + i * Math.PI * 2 / 3;
            double r = 1.2 * (1 - age / (double) INTRO_TICKS) + 0.2;
            level.sendParticles(ElementFx.dust(Element.ARCANE, 1.3F), center.x + Math.cos(ang) * r, core.getY() + 0.3 + age * 0.04,
                    center.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
        }
        if (age % 20 == 0) level.playSound(null, core, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.5F + age / 120F);
        if (age == INTRO_TICKS) {
            level.sendParticles(ParticleTypes.FLASH, top.x, top.y, top.z, 1, 0, 0, 0, 0);
            ElementFx.burst(level, top, Element.ARCANE, 60, 0.8);
        }
    }

    private void beginTrial(ServerPlayer owner) {
        bar = new ServerBossEvent(barName(duration), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setDarkenScreen(true);
        bar.setProgress(0F);
        refreshBarPlayers();
        if (level.getBlockEntity(core) instanceof AscensionCoreBlockEntity be) be.markTrialStarted(level.getGameTime());
        level.playSound(null, core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5, SoundEvents.RAID_HORN, SoundSource.HOSTILE, 3.0F, 0.8F);
        owner.displayClientMessage(Component.translatable("message.isekai_mahou.ascension.trial_begins").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        ElementFx.ring(level, center.add(0, 0.1, 0), Element.ARCANE, boundary, 72);
    }

    /** "Tribulação do Nº Círculo — 42s" (tempo que falta). */
    private Component barName(int ticksLeft) {
        return Component.translatable("bossbar.isekai_mahou.tribulation", target, Math.max(0, (ticksLeft + 19) / 20))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private void refreshBarPlayers() {
        double r = boundary + 12;
        for (ServerPlayer p : level.players()) {
            boolean near = p.distanceToSqr(center) <= r * r;
            if (near && !bar.getPlayers().contains(p)) bar.addPlayer(p);
            else if (!near && bar.getPlayers().contains(p)) bar.removePlayer(p);
        }
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
            if (p.isRemoved() || p.level() != level) bar.removePlayer(p);
        }
    }

    /** Runas acendem na parede do domo bem na frente de quem ascende (só ele vê), avisando que ali é o limite. */
    private void runeWall(ServerPlayer owner, double dx, double dz) {
        double base = Math.atan2(dz, dx);
        DustParticleOptions rune = new DustParticleOptions(new Vector3f(0.78F, 0.55F, 1F), 1.3F);
        for (int i = -5; i <= 5; i++) {
            double a = base + i * 0.07;
            for (int h = 0; h < 3; h++) {
                level.sendParticles(owner, rune, false, center.x + Math.cos(a) * boundary, owner.getY() + 0.3 + h * 0.7,
                        center.z + Math.sin(a) * boundary, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(owner, ParticleTypes.ENCHANT, false, center.x + Math.cos(base) * boundary, owner.getY() + 1.2,
                center.z + Math.sin(base) * boundary, 6, 0.2, 0.5, 0.2, 0.3);
    }

    /**
     * Barreira de runas: mantém quem ascende dentro do domo. Encostar na parede (ou no teto) empurra de volta; ser
     * arremessado/teleportado longe, cavar para baixo ou sair voando traz de volta para dentro. Devolve true se mexeu
     * na posição (teleporte). Mobs, ajudantes e magias nunca são barrados.
     */
    private boolean confine(ServerPlayer owner, double dx, double dz, double dist) {
        double yRel = owner.getY() - core.getY();
        double top = AscensionPattern.domeTop(boundary, Math.min(dist, boundary)) - owner.getBbHeight();
        double wall = boundary - 0.4;
        boolean farOut = dist > boundary + 1.5 || yRel > top + 3 || yRel < -AscensionPattern.BARRIER_BELOW;
        if (farOut || owner.isPassenger() && dist > wall) {
            pullBack(owner, dx, dz, dist);
            return true;
        }
        boolean side = dist > wall;
        boolean roof = yRel > top;
        if (!side && !roof) return false;
        Vec3 v = owner.getDeltaMovement();
        double vx = v.x, vy = v.y, vz = v.z;
        if (side && dist > 1.0E-3) {
            double nx = dx / dist, nz = dz / dist;
            double out = vx * nx + vz * nz;
            double push = 0.18 + Math.min(0.5, (dist - wall) * 0.6);
            if (out > -push) {
                vx -= nx * (out + push);
                vz -= nz * (out + push);
            }
        }
        if (roof) {
            vy = Math.min(vy, -0.25);
            if (owner.isFallFlying()) owner.stopFallFlying();
        }
        owner.setDeltaMovement(vx, vy, vz);
        owner.hurtMarked = true;
        barrierFx(owner, dx, dz, dist, roof && !side);
        return false;
    }

    /** Traz quem ascende de volta para dentro do domo, num chão seguro. */
    private void pullBack(ServerPlayer owner, double dx, double dz, double dist) {
        if (owner.isPassenger()) owner.stopRiding();
        if (owner.isFallFlying()) owner.stopFallFlying();
        double keep = Math.max(0.5, boundary - 2.0);
        double k = dist > keep && dist > 1.0E-3 ? keep / dist : 1.0;
        double x = center.x + dx * k, z = center.z + dz * k;
        BlockPos g = ground(x, z, 3, AscensionPattern.BARRIER_BELOW);
        double y;
        if (g != null) {
            y = g.getY();
        } else {
            // Sem chão naquele ponto (buraco, parede): volta para perto do Núcleo.
            x = center.x + (dist > 1.0E-3 ? dx / dist * 1.5 : 1.5);
            z = center.z + (dist > 1.0E-3 ? dz / dist * 1.5 : 0);
            BlockPos g2 = ground(x, z, 3, 3);
            y = g2 != null ? g2.getY() : core.getY() + 1;
        }
        owner.teleportTo(x, y, z);
        owner.setDeltaMovement(Vec3.ZERO);
        owner.hurtMarked = true;
        owner.resetFallDistance();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 30, 0.3, 0.6, 0.3, 0.05);
        level.sendParticles(ElementFx.dust(Element.ARCANE, 1.5F), x, y + 1, z, 16, 0.4, 0.7, 0.4, 0);
        level.playSound(null, x, y, z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.9F, 0.7F);
        barrierMessage(owner);
    }

    /** Faísca de runas no ponto da parede (ou do teto) onde o jogador encostou. */
    private void barrierFx(ServerPlayer owner, double dx, double dz, double dist, boolean roofOnly) {
        double px, pz, py;
        if (roofOnly || dist < 1.0E-3) {
            px = owner.getX();
            pz = owner.getZ();
            py = core.getY() + AscensionPattern.domeTop(boundary, Math.min(dist, boundary));
        } else {
            px = center.x + dx / dist * boundary;
            pz = center.z + dz / dist * boundary;
            py = owner.getY() + 1.0;
        }
        if (age % 3 == 0) {
            level.sendParticles(ElementFx.dust(Element.ARCANE, 1.6F), px, py, pz, 8, 0.25, 0.45, 0.25, 0);
            level.sendParticles(ParticleTypes.ENCHANT, px, py, pz, 10, 0.2, 0.4, 0.2, 0.6);
        }
        if (age % 10 == 0) {
            level.playSound(null, px, py, pz, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.6F + level.random.nextFloat() * 0.2F);
        }
        barrierMessage(owner);
    }

    private void barrierMessage(ServerPlayer owner) {
        if (age - lastBarrierMsg < 30) return;
        lastBarrierMsg = age;
        owner.displayClientMessage(Component.translatable("message.isekai_mahou.ascension.barrier").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** O ponto (destino de pérola, fruta do coro, magia que use o evento) está dentro do domo deste ritual? */
    public boolean insideBarrier(Vec3 pos) {
        double dx = pos.x - center.x, dz = pos.z - center.z;
        double yRel = pos.y - core.getY();
        return dx * dx + dz * dz <= (boundary - 0.4) * (boundary - 0.4) && yRel >= -AscensionPattern.BARRIER_BELOW
                && yRel <= AscensionPattern.domeHeight(boundary);
    }

    /** Nível do ritual. */
    public ServerLevel level() { return level; }

    /**
     * O círculo "ancora" o dono: um empurrão forte para fora (ciclone da Sílfide, explosão, golpe de chefe) é
     * amortecido, porque ser arremessado para fora do limite reprovaria o jogador sem culpa dele. Andar, correr e
     * pular nunca passam do limite de velocidade, então sair andando continua sendo possível (e é falha).
     * Roda depois do tick das entidades e antes do envio do movimento ao cliente (hurtMarked).
     */
    private void anchor(ServerPlayer owner, double dx, double dz, double dist) {
        if (!owner.hurtMarked || dist < 0.5) return;
        Vec3 v = owner.getDeltaMovement();
        double nx = dx / dist, nz = dz / dist;
        double outward = v.x * nx + v.z * nz;
        final double maxOut = 0.35;
        if (outward <= maxOut) return;
        double cut = outward - maxOut;
        owner.setDeltaMovement(v.x - nx * cut, v.y, v.z - nz * cut);
        level.sendParticles(ElementFx.dust(Element.ARCANE, 1.4F), owner.getX(), owner.getY() + 1, owner.getZ(), 12, 0.4, 0.6, 0.4, 0);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    private void arcaneRain() {
        RandomSource r = level.random;
        double a = r.nextDouble() * Math.PI * 2;
        double d = Math.sqrt(r.nextDouble()) * boundary;
        level.sendParticles(ParticleTypes.END_ROD, center.x + Math.cos(a) * d, core.getY() + 9 + r.nextDouble() * 3,
                center.z + Math.sin(a) * d, 0, 0, -1, 0, 0.35);
    }

    // =====================================================================================
    //  Raios arcanos (com aviso no chão para dar tempo de desviar)
    // =====================================================================================
    private void tickStrikes(ServerPlayer owner, int t) {
        if (t >= nextStrike && t < duration - 10) {
            int interval = Math.max(12, 44 - 3 * target);
            nextStrike = t + interval + level.random.nextInt(8);
            queueStrike(owner, t);
            if (target >= 6 && level.random.nextInt(4) == 0) queueStrike(owner, t);
        }
        Iterator<Strike> it = strikes.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            int left = s.hitAt() - t;
            if (left <= 0) {
                strike(s.pos());
                it.remove();
            } else if (left % 2 == 0) {
                double r = 0.3 + 1.5 * left / (double) STRIKE_WARNING;
                ElementFx.ring(level, s.pos().add(0, 0.05, 0), Element.ARCANE, r, 10);
                level.sendParticles(ParticleTypes.END_ROD, s.pos().x, s.pos().y + 4 + left * 0.2, s.pos().z, 1, 0.05, 0.2, 0.05, 0);
            }
        }
    }

    private void queueStrike(ServerPlayer owner, int t) {
        RandomSource r = level.random;
        double x, z;
        if (r.nextFloat() < 0.6F) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = r.nextDouble() * 2.5;
            x = owner.getX() + Math.cos(a) * d;
            z = owner.getZ() + Math.sin(a) * d;
        } else {
            double a = r.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(r.nextDouble()) * (boundary - 0.5);
            x = center.x + Math.cos(a) * d;
            z = center.z + Math.sin(a) * d;
        }
        BlockPos g = ground(x, z, 4, 4);
        double y = g != null ? g.getY() : core.getY();
        strikes.add(new Strike(new Vec3(x, y, z), t + STRIKE_WARNING));
        level.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.6F, 1.8F);
    }

    private void strike(Vec3 pos) {
        TribulationBoltEntity bolt = AscensionRegistry.BOLT.get().create(level);
        if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            level.addFreshEntity(bolt);
        }
        // Som com alcance limitado (o trovão vanilla seria ouvido na dimensão inteira).
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.5F,
                0.9F + level.random.nextFloat() * 0.3F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.5F,
                0.6F + level.random.nextFloat() * 0.2F);
        ElementFx.burst(level, pos.add(0, 0.3, 0), Element.ARCANE, 30, 0.6);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.5, pos.z, 20, 0.6, 0.6, 0.6, 0.2);

        // Inflação de números da v1.0: dano de magia ×DAMAGE_SCALE (a vida do jogador dobrou; os mobs, ×2 pelo MobScaling).
        float damage = (3.0F + 0.7F * target) * Progression.DAMAGE_SCALE;
        DamageSource src = level.damageSources().source(CoreRegistries.SPELL_DAMAGE);
        AABB box = new AABB(pos, pos).inflate(1.8, 3.0, 1.8);
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof AscensionShadowEntity))) {
            if (le instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            // Moradores (aldeões, NPCs das cidades — o altar público fica na Igreja) não pagam pela Tribulação alheia.
            if (le instanceof Npc && !le.getPersistentData().hasUUID(MOB_TAG)) continue;
            // Mobs também levam o raio (atrair os inimigos para os raios é uma tática válida).
            le.hurt(src, le instanceof Player ? damage : damage * 2);
            // Empurrão fraco no jogador para o raio não jogá-lo para fora do círculo.
            le.knockback(le instanceof Player ? 0.2 : 0.6, pos.x - le.getX(), pos.z - le.getZ());
        }
    }

    // =====================================================================================
    //  Ondas de mobs
    // =====================================================================================
    private record MobOption(String id, int minCircle) {}

    private static MobOption o(String id, int min) { return new MobOption(id, min); }

    /**
     * Criaturas do mod (por id; módulos de criaturas podem ainda não existir). Só criaturas que lutam em terra firme:
     * as aquáticas (Ondina, Serpente da Maré) se debateriam e secariam no chão, e a Fada de Luz só foge.
     */
    private static final Map<Element, List<MobOption>> MOD_MOBS = Map.of(
            Element.ARCANE, List.of(o("isekai_mahou:wandering_spirit", 2), o("isekai_mahou:arcane_golem", 6)),
            Element.FIRE, List.of(o("isekai_mahou:salamander", 2), o("isekai_mahou:fire_slime", 2), o("isekai_mahou:ember_imp", 3),
                    o("isekai_mahou:greater_fire_elemental", 7)),
            Element.WATER, List.of(o("isekai_mahou:water_slime", 2), o("isekai_mahou:greater_water_elemental", 7)),
            Element.EARTH, List.of(o("isekai_mahou:moss_golem", 2), o("isekai_mahou:stone_slime", 2), o("isekai_mahou:crystal_mole", 3),
                    o("isekai_mahou:greater_earth_elemental", 7)),
            Element.WIND, List.of(o("isekai_mahou:sylph", 2), o("isekai_mahou:wind_slime", 2), o("isekai_mahou:storm_hawk", 3),
                    o("isekai_mahou:greater_air_elemental", 7)),
            Element.LIGHT, List.of(o("isekai_mahou:solar_sentinel", 3), o("isekai_mahou:angel_sentinel", 8)),
            Element.SHADOW, List.of(o("isekai_mahou:creeping_shade", 2), o("isekai_mahou:poison_jelly", 3), o("isekai_mahou:void_bat", 3),
                    o("isekai_mahou:void_crawler", 7)));

    /**
     * Reserva vanilla (quando o mod não tem criatura daquele elemento). Sem Brisa: o empurrão das cargas de vento
     * é aplicado pelo cliente (pacote de explosão) e jogaria o jogador para fora do círculo sem chance de reação.
     */
    private static final Map<Element, List<MobOption>> VANILLA_MOBS = Map.of(
            Element.ARCANE, List.of(o("minecraft:zombie", 2), o("minecraft:skeleton", 2), o("minecraft:witch", 5)),
            Element.FIRE, List.of(o("minecraft:magma_cube", 2), o("minecraft:blaze", 3)),
            Element.WATER, List.of(o("minecraft:drowned", 2)),
            Element.EARTH, List.of(o("minecraft:husk", 2), o("minecraft:zombie", 2)),
            Element.WIND, List.of(o("minecraft:stray", 2), o("minecraft:vex", 4)),
            Element.LIGHT, List.of(o("minecraft:skeleton", 2), o("minecraft:vex", 5)),
            Element.SHADOW, List.of(o("minecraft:spider", 2), o("minecraft:wither_skeleton", 6)));

    private EntityType<?> pickMob(Element element) {
        List<EntityType<?>> mod = available(MOD_MOBS.get(element));
        if (mod.isEmpty() && element != Element.ARCANE) mod = available(MOD_MOBS.get(Element.ARCANE));
        RandomSource r = level.random;
        if (!mod.isEmpty() && r.nextFloat() < 0.7F) return mod.get(r.nextInt(mod.size()));
        return pickVanilla(element);
    }

    private EntityType<?> pickVanilla(Element element) {
        List<EntityType<?>> vanilla = available(VANILLA_MOBS.get(element));
        return vanilla.isEmpty() ? EntityType.ZOMBIE : vanilla.get(level.random.nextInt(vanilla.size()));
    }

    private List<EntityType<?>> available(List<MobOption> options) {
        List<EntityType<?>> out = new ArrayList<>();
        if (options == null) return out;
        for (MobOption opt : options) {
            if (opt.minCircle() > target) continue;
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(opt.id()));
            type.ifPresent(out::add);
        }
        return out;
    }

    private void spawnWave(ServerPlayer owner, int index) {
        mobs.removeIf(u -> {
            Entity e = level.getEntity(u);
            return e == null || !e.isAlive();
        });
        int helpers = 0;
        for (ServerPlayer p : level.players()) {
            if (p != owner && !p.isSpectator() && p.distanceToSqr(center) <= boundary * boundary) helpers++;
        }
        int count = 2 + target / 2 + helpers;
        int cap = 4 + target + helpers * 2;
        Element element = index % 3 == 2 ? Element.ARCANE : elements.get(index % elements.size());

        owner.displayClientMessage(Component.translatable("message.isekai_mahou.ascension.wave", index + 1, waveTimes.length)
                .withColor(element.color), true);
        for (int i = 0; i < count && mobs.size() < cap; i++) spawnMob(owner, pickMob(element), element);
    }

    private void spawnMob(ServerPlayer owner, EntityType<?> type, Element element) {
        RandomSource r = level.random;
        for (int attempt = 0; attempt < 16; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            // Primeiro no anel de fora do padrão; num salão apertado (torre da Academia: o 9×9 encosta na parede) o anel
            // fica dentro da parede — então tenta DENTRO do círculo, longe do jogador, para a onda não sumir.
            boolean inner = attempt >= 10;
            // v1.0: a arena ficou maior — os mobs surgem entre o padrão e a parede do domo.
            double d = inner ? 1.5 + r.nextDouble() * Math.max(0.5, radius - 1.5)
                    : radius + 1.0 + r.nextDouble() * Math.max(0.5, boundary - radius - 2.0);
            double x = center.x + Math.cos(a) * d, z = center.z + Math.sin(a) * d;
            if (inner && owner.distanceToSqr(x, owner.getY(), z) < 2.5 * 2.5) continue;
            BlockPos g = ground(x, z, 3, 3);
            if (g == null || !level.noCollision(type.getSpawnAABB(x, g.getY(), z))) continue;
            // Do outro lado de uma parede (Igreja/torre da cidade) o mob ficaria preso lá fora: precisa "ver" o Núcleo.
            Vec3 eye = new Vec3(x, g.getY() + 1.2, z);
            Vec3 mid = new Vec3(center.x, core.getY() + 1.2, center.z);
            if (level.clip(new ClipContext(eye, mid, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                    .getType() == HitResult.Type.BLOCK) continue;

            Entity e = type.create(level);
            if (!(e instanceof Mob mob)) {
                if (e != null) e.discard();
                return;
            }
            // Criatura aquática fora d'água só se debateria: troca por uma da reserva vanilla.
            if (isAquatic(mob) && !level.getFluidState(g).is(FluidTags.WATER)) {
                mob.discard();
                EntityType<?> alt = pickVanilla(element);
                if (alt != type) spawnMob(owner, alt, element);
                return;
            }
            mob.moveTo(x, g.getY(), z, r.nextFloat() * 360F, 0F);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(g), MobSpawnType.MOB_SUMMONED, null);
            empower(mob);
            prepare(mob);
            mob.setPersistenceRequired();
            mob.setTarget(owner);
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            tag(mob);
            for (Entity passenger : mob.getIndirectPassengers()) tag(passenger);
            // Montaria criada no finalizeSpawn (ex.: galinha do zumbi-bebê) já entrou no mundo: marca também.
            if (mob.getVehicle() != null) tag(mob.getVehicle());
            level.addFreshEntityWithPassengers(mob);

            Vec3 p = new Vec3(x, g.getY() + 0.5, z);
            ElementFx.burst(level, p, element, 24, 0.5);
            level.sendParticles(ParticleTypes.PORTAL, p.x, p.y + 0.5, p.z, 30, 0.4, 0.8, 0.4, 0.4);
            level.playSound(null, x, g.getY(), z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 0.7F, 1.2F);
            return;
        }
    }

    private void tag(Entity e) {
        e.getPersistentData().putUUID(MOB_TAG, id);
        // Altar público fica DENTRO das cidades: a varredura de monstros da cidade (módulo cities) não apaga a onda.
        e.addTag(CITY_KEEP_TAG);
        if (!mobs.contains(e.getUUID())) mobs.add(e.getUUID());
    }

    /**
     * Filhotes de slime que se divide, mob convertido (zumbi → afogado) etc.: herdam a marca do ritual,
     * para sumirem junto com os outros no fim. Chamado pelos eventos do módulo.
     */
    static void adopt(Entity parent, Entity child) {
        if (parent == null || child == null || !parent.getPersistentData().hasUUID(MOB_TAG)) return;
        Tribulation t = ACTIVE.get(parent.getPersistentData().getUUID(MOB_TAG));
        if (t == null || t.finished || child.level() != t.level) return;
        t.tag(child);
        if (child instanceof Mob m) {
            m.setPersistenceRequired();
            ServerPlayer owner = t.owner();
            if (owner != null) m.setTarget(owner);
        }
    }

    private static boolean isAquatic(Mob mob) {
        return mob instanceof WaterAnimal || mob.getNavigation() instanceof WaterBoundPathNavigation;
    }

    /**
     * Ajustes para a luta: mortos-vivos ganham um capuz arcano inquebrável (não queimam ao sol nem pegam fogo
     * no jogador) e zumbis não chamam reforços (mobs fora do controle do ritual ficariam no mundo).
     */
    private void prepare(Mob mob) {
        if ((mob instanceof Zombie || mob instanceof AbstractSkeleton) && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            ItemStack hood = new ItemStack(Items.LEATHER_HELMET);
            hood.set(DataComponents.DYED_COLOR, new DyedItemColor(0x6A3FB5, false));
            hood.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
            mob.setItemSlot(EquipmentSlot.HEAD, hood);
            mob.setDropChance(EquipmentSlot.HEAD, 0F);
        }
        AttributeInstance aid = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (aid != null) {
            aid.removeModifiers();
            aid.setBaseValue(0);
        }
    }

    /** Mobs mais fortes nos Círculos altos. */
    private void empower(Mob mob) {
        double hp = 0.15 * (target - 2);
        double dmg = 0.08 * (target - 2);
        if (hp > 0) {
            AttributeInstance h = mob.getAttribute(Attributes.MAX_HEALTH);
            if (h != null) {
                h.addOrReplacePermanentModifier(new AttributeModifier(IsekaiMahou.id("tribulation_health"), hp,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                mob.setHealth(mob.getMaxHealth());
            }
        }
        if (dmg > 0) {
            AttributeInstance d = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (d != null) d.addOrReplacePermanentModifier(new AttributeModifier(IsekaiMahou.id("tribulation_damage"), dmg,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // =====================================================================================
    //  A Sombra
    // =====================================================================================
    private int aliveShadows() {
        shadows.removeIf(u -> {
            Entity e = level.getEntity(u);
            return e == null || !e.isAlive();
        });
        return shadows.size();
    }

    private void spawnShadow(ServerPlayer owner) {
        AscensionShadowEntity shadow = AscensionRegistry.SHADOW.get().create(level);
        if (shadow == null) return;
        Vec3 dir = new Vec3(owner.getX() - center.x, 0, owner.getZ() - center.z);
        dir = dir.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : dir.normalize();
        double x = center.x - dir.x * radius, z = center.z - dir.z * radius;
        BlockPos g = ground(x, z, 3, 3);
        double y = g != null ? g.getY() : core.getY();
        float yaw = (float) (Mth.atan2(owner.getZ() - z, owner.getX() - x) * Mth.RAD_TO_DEG) - 90F;
        shadow.moveTo(x, y, z, yaw, 0F);
        shadow.bind(owner, id, target);
        shadow.getPersistentData().putUUID(MOB_TAG, id);
        shadow.addTag(CITY_KEEP_TAG);
        level.addFreshEntity(shadow);
        shadows.add(shadow.getUUID());

        level.sendParticles(ParticleTypes.SQUID_INK, x, y + 1, z, 60, 0.4, 1.0, 0.4, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.2, z, 20, 0.5, 0.1, 0.5, 0.05);
        ElementFx.ring(level, new Vec3(x, y + 0.05, z), Element.SHADOW, 1.5, 24);
        level.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.4F, 1.3F);
        Announce.send(owner, Component.translatable("announce.isekai_mahou.ascension.shadow", owner.getDisplayName()),
                Element.SHADOW.color, Announce.STYLE_BOSS);
    }

    // =====================================================================================
    //  Fim
    // =====================================================================================
    /**
     * Sucesso: Círculo +1 (o núcleo aplica o XP guardado no banco), XP de recompensa para o dono e para cada
     * ajudante dentro do círculo, fogos e festa.
     */
    public void succeed() {
        if (finished) return;
        ServerPlayer owner = owner();
        List<ServerPlayer> helpers = owner != null ? helpersInside(owner) : List.of();
        cleanup();
        Vec3 top = center.add(0, 1.5, 0);
        level.sendParticles(ParticleTypes.FLASH, top.x, top.y, top.z, 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y, top.z, 120, 1.5, 1.5, 1.5, 0.5);
        for (Element e : elements) ElementFx.burst(level, top, e, 40, 1.2);
        ElementFx.ring(level, center.add(0, 0.1, 0), Element.LIGHT, boundary, 72);
        level.playSound(null, core, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.0F, 0.7F);
        level.playSound(null, core, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.5F, 0.6F);
        launchFireworks();

        if (owner != null) {
            int xp = rewardXp(target);
            // setCircle sobe o limite e aplica o banco de XP; a recompensa entra depois, já no Círculo novo.
            MagicApi.setCircle(owner, target);
            MagicApi.addCounter(owner, "ascension", 1);
            MagicApi.giveXp(owner, xp, "ascension");
            owner.setHealth(owner.getMaxHealth());
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, owner.getX(), owner.getY() + 1, owner.getZ(), 60, 0.5, 1.0, 0.5, 0.4);
            owner.sendSystemMessage(Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("message.isekai_mahou.ascension.reward", target, xp,
                            Progression.levelCap(target)).withStyle(ChatFormatting.GOLD)));
            Rewards.toast(owner, Component.translatable("toast.isekai_mahou.ascension.success", target),
                    Component.translatable("toast.isekai_mahou.ascension.success_sub", xp, Progression.levelCap(target)),
                    new ItemStack(AscensionRegistry.CORE_ITEM.get()), Rewards.GOLD);
            // A faixa no topo é o anúncio abaixo (também visto por quem está perto); o toast traz a recompensa.
            Announce.send(owner, Component.translatable("announce.isekai_mahou.ascension.success", owner.getDisplayName(), target),
                    0xFFD86B, Announce.STYLE_SYSTEM);
            Cutscenes.playNear(owner, 32, "ascension", Component.translatable("cutscene.isekai_mahou.ascension.done_title", target),
                    Component.translatable("cutscene.isekai_mahou.ascension.done_subtitle"), 0xFFD86B, 60, owner);
            for (ServerPlayer helper : helpers) rewardHelper(helper, owner, xp);
        }
    }

    /** XP de recompensa por Círculo-alvo (dono e ajudantes) — piso da v0.8. */
    private static final int XP_PER_CIRCLE = 100;

    /**
     * v1.0 (inflação da curva de XP, ~4× até o 100): a recompensa vale ~1 nível cheio no nível em que se ascende
     * (como na v0.8), em vez de um valor fixo que ficaria minúsculo.
     */
    static int rewardXp(int target) {
        return Math.max(XP_PER_CIRCLE * target, Progression.xpToNext(Progression.levelCap(Math.max(1, target - 1))));
    }

    /** Jogadores (sem o dono) que estão dentro do círculo agora: lutaram juntos e ganham a recompensa. */
    private List<ServerPlayer> helpersInside(ServerPlayer owner) {
        List<ServerPlayer> out = new ArrayList<>();
        double r = boundary + 0.5;
        for (ServerPlayer p : level.players()) {
            if (p == owner || p.isSpectator() || !p.isAlive()) continue;
            double dx = p.getX() - center.x, dz = p.getZ() - center.z;
            if (dx * dx + dz * dz <= r * r && Math.abs(p.getY() - core.getY()) <= 16) out.add(p);
        }
        return out;
    }

    private void rewardHelper(ServerPlayer helper, ServerPlayer owner, int xp) {
        MagicApi.giveXp(helper, xp, "ascension_assist");
        MagicApi.addCounter(helper, "ascension.assist", 1);
        helper.heal(helper.getMaxHealth());
        helper.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        helper.sendSystemMessage(Component.translatable("message.isekai_mahou.ascension.helper_reward", owner.getDisplayName(), target, xp)
                .withStyle(ChatFormatting.GOLD));
        Rewards.toast(helper, Component.translatable("toast.isekai_mahou.ascension.helper", owner.getDisplayName()),
                Component.translatable("toast.isekai_mahou.ascension.helper_sub", xp), new ItemStack(AscensionRegistry.CORE_ITEM.get()),
                Element.ARCANE.color);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, helper.getX(), helper.getY() + 1, helper.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
    }

    /** Falha: o tributo (já consumido) está perdido. */
    public void fail(Reason reason) {
        if (finished) return;
        ServerPlayer owner = owner();
        cleanup();
        Vec3 top = center.add(0, 1.2, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y, top.z, 40, 0.8, 0.6, 0.8, 0.02);
        level.sendParticles(ParticleTypes.SOUL, top.x, top.y, top.z, 20, 1.2, 0.6, 1.2, 0.03);
        level.playSound(null, core, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.6F);
        level.playSound(null, core, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.2F, 0.7F);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(reason.key).withStyle(ChatFormatting.RED));
            if (reason != Reason.OFFLINE) {
                Announce.send(owner, Component.translatable("announce.isekai_mahou.ascension.fail", owner.getDisplayName(), target),
                        0x8A4FC8, Announce.STYLE_SYSTEM);
            }
        }
    }

    private void cleanup() {
        finished = true;
        ZONES.remove(id);
        if (bar != null) bar.removeAllPlayers();
        strikes.clear();
        for (UUID u : mobs) vanish(level.getEntity(u));
        for (UUID u : shadows) vanish(level.getEntity(u));
        mobs.clear();
        shadows.clear();
        if (level.isLoaded(core) && level.getBlockEntity(core) instanceof AscensionCoreBlockEntity be) be.endRitual();
    }

    private void vanish(Entity e) {
        if (e == null || e.isRemoved()) return;
        level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        level.sendParticles(ElementFx.dust(Element.ARCANE, 1.2F), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 10, 0.3, 0.4, 0.3, 0);
        e.discard();
    }

    private void launchFireworks() {
        IntList colors = new IntArrayList();
        for (Element e : elements) colors.add(e.color);
        colors.add(0xFFD86B);
        IntList fade = IntList.of(Element.ARCANE.color, 0xFFFFFF);
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = center.x + Math.cos(a) * boundary, z = center.z + Math.sin(a) * boundary;
            FireworkExplosion.Shape shape = i % 2 == 0 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR;
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(2, List.of(new FireworkExplosion(shape, colors, fade, true, true))));
            level.addFreshEntity(new FireworkRocketEntity(level, x, core.getY() + 0.5, z, rocket));
        }
    }

    // =====================================================================================
    //  Utilidades
    // =====================================================================================
    /** Primeiro ponto livre (2 blocos de altura) com chão firme, procurando de cima para baixo perto do Núcleo. */
    private BlockPos ground(double x, double z, int up, int down) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(x), core.getY() + up, Mth.floor(z));
        for (int i = 0; i <= up + down; i++) {
            BlockPos below = m.below();
            BlockState belowState = level.getBlockState(below);
            if (level.getBlockState(m).getCollisionShape(level, m).isEmpty()
                    && level.getBlockState(m.above()).getCollisionShape(level, m.above()).isEmpty()
                    && belowState.isFaceSturdy(level, below, Direction.UP)) {
                return m.immutable();
            }
            m.move(Direction.DOWN);
        }
        return null;
    }
}
