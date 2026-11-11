package com.renanpontes.isekaimahou.feature.structures_a;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.structures_a.block.ShrineAltarBlockEntity;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/**
 * Prova do Despertar em andamento (servidor). Fica num registro estático atualizado pelo tick do servidor.
 *
 * Linha do tempo: introdução (anúncio + cutscene) → 3 ondas de criaturas do elemento dentro da arena
 * (a última com um Campeão) → sucesso: o elemento desperta, recompensas, flag para não repetir.
 * Morrer, sair do santuário (por mais de 5 s) ou desconectar = falha; dá para tentar de novo.
 * Outros jogadores podem entrar na prova clicando no altar e também ganham, se ainda não a superaram.
 */
public final class ShrineTrial {
    /** Chave nos dados persistentes das criaturas da prova (some se a prova não existe mais). */
    public static final String MOB_TAG = "isekai_mahou.shrine_trial";
    public static final int WAVES = 3;
    private static final int INTRO_TICKS = 60;
    private static final int BREAK_TICKS = 70;
    private static final int SUMMON_WARNING = 30;
    private static final int OUTSIDE_GRACE = 100;
    private static final int COOLDOWN = 100;
    private static final int PHASE_INTRO = 0, PHASE_FIGHT = 1, PHASE_BREAK = 2;
    private static final ResourceLocation CHAMPION_HP = IsekaiMahou.id("shrine_champion_health");
    private static final ResourceLocation CHAMPION_DMG = IsekaiMahou.id("shrine_champion_damage");
    private static final ResourceLocation CHAMPION_SCALE = IsekaiMahou.id("shrine_champion_scale");

    /** Quantas vezes o vento do Pico devolve ao altar quem cai da plataforma durante a prova. */
    private static final int WIND_RESCUES = 3;

    public enum Reason {
        DIED("message.isekai_mahou.shrine.fail.died"),
        LEFT("message.isekai_mahou.shrine.fail.left"),
        OFFLINE("message.isekai_mahou.shrine.fail.offline"),
        INTERRUPTED("message.isekai_mahou.shrine.fail.interrupted"),
        ABORTED("message.isekai_mahou.shrine.fail.aborted"),
        GAVE_UP("message.isekai_mahou.shrine.fail.gave_up"),
        PEACEFUL("message.isekai_mahou.shrine.fail.peaceful");

        final String key;

        Reason(String key) { this.key = key; }
    }

    // =====================================================================================
    //  Registro estático
    // =====================================================================================
    private static final Map<UUID, ShrineTrial> ACTIVE = new LinkedHashMap<>();

    public static boolean isActive(UUID trialId) {
        ShrineTrial t = ACTIVE.get(trialId);
        return t != null && !t.finished;
    }

    /** Prova em andamento neste altar (ou null). */
    public static ShrineTrial at(ServerLevel level, BlockPos pos) {
        for (ShrineTrial t : ACTIVE.values()) if (!t.finished && t.level == level && t.altar.equals(pos)) return t;
        return null;
    }

    /** Prova da qual o jogador participa (ou null). */
    public static ShrineTrial of(UUID player) {
        for (ShrineTrial t : ACTIVE.values()) if (!t.finished && t.participants.contains(player)) return t;
        return null;
    }

    static void tickAll() {
        if (ACTIVE.isEmpty()) return;
        for (ShrineTrial t : List.copyOf(ACTIVE.values())) {
            if (!t.finished) t.tick();
        }
        ACTIVE.values().removeIf(t -> t.finished);
    }

    static void clearAll() {
        for (ShrineTrial t : ACTIVE.values()) t.bar.removeAllPlayers();
        ACTIVE.clear();
    }

    static void onLogout(UUID player) {
        ShrineTrial t = of(player);
        if (t != null) t.drop(player, null, Reason.OFFLINE);
    }

    static void onPlayerDeath(ServerPlayer player) {
        ShrineTrial t = of(player.getUUID());
        if (t != null) t.drop(player.getUUID(), player, Reason.DIED);
    }

    /**
     * Cubos de magma (e outros slimes) da prova não se dividem: os pedaços não contariam na onda e
     * ficariam soltos no santuário depois do fim. A essência do elemento se desfaz no lugar.
     */
    static void onSplit(MobSplitEvent e) {
        Mob parent = e.getParent();
        var data = parent.getPersistentData();
        if (!data.hasUUID(MOB_TAG)) return;
        ShrineTrial t = ACTIVE.get(data.getUUID(MOB_TAG));
        if (t == null || t.finished) return;
        e.setCanceled(true);
        t.level.sendParticles(ParticleTypes.POOF, parent.getX(), parent.getY() + 0.4, parent.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        ElementFx.burst(t.level, parent.position().add(0, 0.4, 0), t.kind.element, 10, 0.4);
    }

    // =====================================================================================
    //  Estado
    // =====================================================================================
    public final UUID id = UUID.randomUUID();
    private final ServerLevel level;
    private final BlockPos altar;
    private final Vec3 center;
    private final ShrineKind kind;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, Integer> outside = new LinkedHashMap<>();
    private final Map<UUID, Integer> rescues = new LinkedHashMap<>();
    private final List<UUID> mobs = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private final ServerBossEvent bar;
    private int age;
    private int phase = PHASE_INTRO;
    private int phaseTimer;
    private int wave = -1;
    private int waveTotal;
    private boolean finished;

    /** Criatura que vai surgir num ponto marcado (dá tempo de o jogador ver o círculo de invocação). */
    private record Pending(EntityType<?> type, Vec3 pos, int at, int role) {}

    private static final int ROLE_WEAK = 0, ROLE_STRONG = 1, ROLE_CHAMPION = 2;

    private ShrineTrial(ServerLevel level, BlockPos altar, ShrineKind kind) {
        this.level = level;
        this.altar = altar.immutable();
        this.center = Vec3.atBottomCenterOf(altar);
        this.kind = kind;
        this.bar = new ServerBossEvent(barName(), kind.barColor, BossEvent.BossBarOverlay.NOTCHED_6);
        this.bar.setProgress(0F);
    }

    public static ShrineTrial start(ServerLevel level, BlockPos altar, ShrineKind kind, ServerPlayer starter) {
        ShrineTrial t = new ShrineTrial(level, altar, kind);
        ACTIVE.put(t.id, t);
        t.participants.add(starter.getUUID());
        t.bar.addPlayer(starter);

        Announce.send(starter, Component.translatable("announce.isekai_mahou.shrine.begin", starter.getDisplayName(), kind.displayName()),
                kind.element.color, Announce.STYLE_SYSTEM);
        Cutscenes.playNear(starter, 24, "boss_intro", kind.displayName(),
                Component.translatable("cutscene.isekai_mahou.shrine.subtitle"), kind.element.color, 50, null);
        level.playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.8F);
        level.playSound(null, altar, kind.introSound, SoundSource.BLOCKS, 1.5F, 0.7F);
        starter.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.quit_hint").withStyle(ChatFormatting.DARK_GRAY));
        t.blessParticipants();
        return t;
    }

    /** O jogador desiste (agachado + clique no altar). Os outros participantes continuam. */
    public void giveUp(ServerPlayer p) {
        if (!participants.contains(p.getUUID())) return;
        level.playSound(null, altar, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.2F);
        drop(p.getUUID(), p, Reason.GAVE_UP);
    }

    public boolean hasParticipant(UUID player) {
        return participants.contains(player);
    }

    public ShrineKind kind() { return kind; }

    private ServerPlayer player(UUID uuid) {
        return level.getServer().getPlayerList().getPlayer(uuid);
    }

    /** Outro jogador clicou no altar durante a prova: entra como participante. */
    public void join(ServerPlayer p) {
        if (participants.contains(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.shrine.in_progress").withColor(kind.element.color), true);
            return;
        }
        if (of(p.getUUID()) != null) {
            p.displayClientMessage(Component.translatable("message.isekai_mahou.shrine.other_trial").withStyle(ChatFormatting.RED), true);
            return;
        }
        participants.add(p.getUUID());
        bar.addPlayer(p);
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.joined", kind.displayName()).withStyle(ChatFormatting.YELLOW));
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.quit_hint").withStyle(ChatFormatting.DARK_GRAY));
        level.playSound(null, altar, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.3F);
        blessParticipants();
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    private void tick() {
        if (!level.isLoaded(altar) || !(level.getBlockEntity(altar) instanceof ShrineAltarBlockEntity)) {
            fail(Reason.INTERRUPTED);
            return;
        }
        // No Pacífico as criaturas somem sozinhas: a prova venceria sem luta.
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            fail(Reason.PEACEFUL);
            return;
        }
        checkParticipants();
        if (finished) return;
        age++;

        switch (phase) {
            case PHASE_INTRO -> {
                tickIntro();
                if (age >= INTRO_TICKS) beginWave(0);
            }
            case PHASE_FIGHT -> tickFight();
            default -> {
                if (--phaseTimer <= 0) beginWave(wave + 1);
                else if (phaseTimer % 20 == 0) bar.setName(barName());
            }
        }
        if (finished) return;
        if (age % 20 == 0) refreshBar();
        if (age % 20 == 10) ElementFx.ring(level, center.add(0, 0.1, 0), kind.element, kind.arenaRadius + 0.5, 36);
        if (age % 40 == 0) blessParticipants();
    }

    private void checkParticipants() {
        for (UUID u : List.copyOf(participants)) {
            ServerPlayer p = player(u);
            if (p == null) { drop(u, null, Reason.OFFLINE); continue; }
            if (p.isDeadOrDying() || p.isRemoved()) { drop(u, p, Reason.DIED); continue; }
            if (p.level() != level) { drop(u, p, Reason.LEFT); continue; }
            if (kind.element == Element.WIND && updraft(p)) { outside.remove(u); continue; }
            if (isOutside(p)) {
                int t = outside.merge(u, 1, Integer::sum);
                if (t >= OUTSIDE_GRACE) {
                    drop(u, p, Reason.LEFT);
                } else if (t % 20 == 1) {
                    int secs = (OUTSIDE_GRACE - t + 19) / 20;
                    p.displayClientMessage(Component.translatable("message.isekai_mahou.shrine.outside", secs).withStyle(ChatFormatting.RED), true);
                    p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
                }
            } else {
                outside.remove(u);
            }
            if (finished) return;
        }
    }

    /** Fora do santuário: longe do altar na horizontal, ou muito abaixo/acima (ex.: caiu do Pico dos Ventos). */
    private boolean isOutside(ServerPlayer p) {
        double dx = p.getX() - center.x, dz = p.getZ() - center.z;
        double dy = p.getY() - altar.getY();
        double leash = kind.arenaRadius + 8;
        return dx * dx + dz * dz > leash * leash || dy < -7 || dy > 12;
    }

    /**
     * Pico dos Ventos: quem é arremessado da plataforma (rajadas, golpes) é devolvido ao altar pelas correntes do
     * santuário, algumas vezes por prova. Sem isso, um empurrão da Brisa no alto da torre virava queda fatal.
     */
    private boolean updraft(ServerPlayer p) {
        double dy = p.getY() - altar.getY();
        // fallDistance (e não a velocidade): no servidor o movimento do jogador vem do cliente e só a queda acumulada é confiável.
        if (dy > -4 || p.onGround() || p.fallDistance < 3F || p.isFallFlying() || p.getAbilities().flying) return false;
        double dx = p.getX() - center.x, dz = p.getZ() - center.z;
        double leash = kind.arenaRadius + 8;
        if (dx * dx + dz * dz > leash * leash) return false;
        int used = rescues.getOrDefault(p.getUUID(), 0);
        if (used >= WIND_RESCUES) return false;
        rescues.put(p.getUUID(), used + 1);

        Vec3 from = p.position();
        Vec3 to = findSpot(EntityType.PLAYER);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, from.x, from.y + 0.5, from.z, 1, 0, 0, 0, 0);
        p.teleportTo(to.x, to.y, to.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
        level.sendParticles(ParticleTypes.CLOUD, to.x, to.y + 0.2, to.z, 24, 0.5, 0.2, 0.5, 0.05);
        ElementFx.burst(level, to.add(0, 0.8, 0), kind.element, 20, 0.6);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.8F);
        int left = WIND_RESCUES - used - 1;
        p.displayClientMessage(Component.translatable(left > 0 ? "message.isekai_mahou.shrine.updraft"
                : "message.isekai_mahou.shrine.updraft_last", left).withColor(kind.element.color), true);
        return true;
    }

    /** Um participante saiu da prova (morte, fuga, desconexão). Sem participantes, a prova falha. */
    private void drop(UUID uuid, ServerPlayer p, Reason reason) {
        if (!participants.remove(uuid)) return;
        outside.remove(uuid);
        rescues.remove(uuid);
        if (p != null) {
            bar.removePlayer(p);
            p.sendSystemMessage(Component.translatable(reason.key, kind.displayName()).withStyle(ChatFormatting.RED));
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.retry").withStyle(ChatFormatting.GRAY));
        }
        if (participants.isEmpty()) end(false);
    }

    private void tickIntro() {
        // A energia do santuário se junta no altar.
        double a = age * 0.35;
        Vec3 top = center.add(0, 1.2, 0);
        for (int i = 0; i < 3; i++) {
            double ang = a + i * Math.PI * 2 / 3;
            double r = 2.6 * (1 - age / (double) INTRO_TICKS) + 0.3;
            level.sendParticles(ElementFx.dust(kind.element, 1.4F), center.x + Math.cos(ang) * r, top.y + age * 0.02,
                    center.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
        }
        if (age % 15 == 0) level.playSound(null, altar, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.5F + age / 90F);
        if (age == INTRO_TICKS - 1) {
            level.sendParticles(ParticleTypes.FLASH, top.x, top.y + 0.5, top.z, 1, 0, 0, 0, 0);
            ElementFx.burst(level, top.add(0, 0.5, 0), kind.element, 50, 0.8);
        }
    }

    // =====================================================================================
    //  Ondas
    // =====================================================================================
    private void beginWave(int index) {
        wave = index;
        phase = PHASE_FIGHT;
        mobs.clear();
        pending.clear();
        int helpers = Math.max(0, participants.size() - 1);
        int weak = 3 + helpers;
        int strong = index >= 1 ? 1 : 0;
        boolean champion = index == WAVES - 1;
        int delay = 0;
        for (int i = 0; i < weak; i++) queue(pickType(false), ROLE_WEAK, delay += 6);
        for (int i = 0; i < strong; i++) queue(pickType(true), ROLE_STRONG, delay += 8);
        if (champion) queue(pickType(true), ROLE_CHAMPION, delay + 16);
        waveTotal = pending.size();

        Component msg = champion
                ? Component.translatable("message.isekai_mahou.shrine.final_wave")
                : Component.translatable("message.isekai_mahou.shrine.wave", index + 1, WAVES);
        for (UUID u : participants) {
            ServerPlayer p = player(u);
            if (p != null) p.displayClientMessage(msg.copy().withColor(kind.element.color), true);
        }
        level.playSound(null, altar, champion ? SoundEvents.RAID_HORN.value() : SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE,
                champion ? 2.5F : 1.2F, champion ? 0.9F : 1.0F);
        bar.setName(barName());
    }

    private void queue(EntityType<?> type, int role, int delay) {
        Vec3 pos = findSpot(type);
        pending.add(new Pending(type, pos, age + SUMMON_WARNING + delay, role));
        level.playSound(null, pos.x, pos.y, pos.z, kind.summonSound, SoundSource.HOSTILE, 0.6F, 0.8F + level.random.nextFloat() * 0.3F);
    }

    private void tickFight() {
        // Círculos de invocação → criaturas.
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (age >= p.at()) {
                it.remove();
                spawn(p);
            } else if (age % 3 == 0) {
                double r = 0.4 + 0.9 * (p.at() - age) / (double) (SUMMON_WARNING + 20);
                ElementFx.ring(level, p.pos().add(0, 0.05, 0), kind.element, Math.max(0.3, r), 10);
                level.sendParticles(ElementFx.signature(kind.element), p.pos().x, p.pos().y + 0.3, p.pos().z, 1, 0.15, 0.2, 0.15, 0.01);
            }
        }
        // Derrotadas: mortas ou removidas do mundo.
        mobs.removeIf(u -> {
            Entity e = level.getEntity(u);
            return e == null || !e.isAlive();
        });
        if (age % 20 == 0) {
            herd();
            bar.setName(barName());
        }
        float fraction = waveTotal == 0 ? 1F : (waveTotal - mobs.size() - pending.size()) / (float) waveTotal;
        bar.setProgress(Mth.clamp((wave + fraction) / WAVES, 0F, 1F));

        if (pending.isEmpty() && mobs.isEmpty()) {
            if (wave >= WAVES - 1) {
                succeed();
            } else {
                phase = PHASE_BREAK;
                phaseTimer = BREAK_TICKS;
                for (UUID u : participants) {
                    ServerPlayer p = player(u);
                    if (p != null) p.displayClientMessage(Component.translatable("message.isekai_mahou.shrine.wave_cleared", wave + 1)
                            .withStyle(ChatFormatting.GREEN), true);
                }
                level.playSound(null, altar, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 1.4F);
                ElementFx.burst(level, center.add(0, 1.4, 0), kind.element, 30, 0.6);
                bar.setName(barName());
            }
        }
    }

    /** Mantém as criaturas na luta: alvo num participante, de volta para a arena se fugirem, brilho nas últimas. */
    private void herd() {
        boolean reveal = pending.isEmpty() && mobs.size() <= 2;
        for (UUID u : mobs) {
            if (!(level.getEntity(u) instanceof Mob mob)) continue;
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive() || !participants.contains(target.getUUID())) {
                ServerPlayer nearest = nearestParticipant(mob.position());
                if (nearest != null) mob.setTarget(nearest);
            }
            double dx = mob.getX() - center.x, dz = mob.getZ() - center.z;
            double far = kind.arenaRadius + 10;
            if (dx * dx + dz * dz > far * far || Math.abs(mob.getY() - altar.getY()) > 10) {
                Vec3 back = findSpot(mob.getType());
                level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
                mob.teleportTo(back.x, back.y, back.z);
                ElementFx.burst(level, back.add(0, 0.5, 0), kind.element, 12, 0.4);
            }
            if (reveal) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
        }
    }

    private ServerPlayer nearestParticipant(Vec3 from) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (UUID u : participants) {
            ServerPlayer p = player(u);
            if (p == null || p.isDeadOrDying() || p.isCreative() || p.isSpectator()) continue;
            double d = p.distanceToSqr(from);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    // =====================================================================================
    //  Criaturas
    // =====================================================================================
    private EntityType<?> pickType(boolean strong) {
        String modId = strong ? kind.strongMob : kind.weakMob;
        Optional<EntityType<?>> mod = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(modId));
        Optional<EntityType<?>> vanilla = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(strong ? kind.vanillaStrong : kind.vanillaWeak));
        // Com as criaturas do mod presentes, a reserva vanilla aparece só de vez em quando (variedade).
        if (mod.isPresent() && (vanilla.isEmpty() || level.random.nextFloat() < 0.8F)) return mod.get();
        return vanilla.orElse(EntityType.ZOMBIE);
    }

    /** Ponto livre no chão da arena (anel em volta do altar). */
    private Vec3 findSpot(EntityType<?> type) {
        RandomSource r = level.random;
        boolean water = kind.element == Element.WATER;
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 2.6 + r.nextDouble() * Math.max(0.5, kind.arenaRadius - 2.6);
            double x = center.x + Math.cos(a) * d, z = center.z + Math.sin(a) * d;
            for (int dy : new int[] {-1, 0, 1, -2, 2}) {
                BlockPos p = BlockPos.containing(x, altar.getY() + dy, z);
                BlockState below = level.getBlockState(p.below());
                boolean inWater = level.getFluidState(p).is(FluidTags.WATER);
                if (!below.isFaceSturdy(level, p.below(), Direction.UP) && !(water && inWater)) continue;
                AABB box = type.getSpawnAABB(x, p.getY(), z);
                if (!level.noCollision(box)) continue;
                if (level.containsAnyLiquid(box) && !(water && inWater)) continue;
                return new Vec3(x, p.getY(), z);
            }
        }
        return center.add(0.0, 1.0, 0.0);
    }

    private void spawn(Pending p) {
        Entity e = p.type().create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return;
        }
        RandomSource r = level.random;
        // O ponto foi marcado ~2 s antes: se alguém construiu ali nesse meio tempo, procura outro (nada de nascer na parede).
        Vec3 pos = level.noCollision(p.type().getSpawnAABB(p.pos().x, p.pos().y, p.pos().z)) ? p.pos() : findSpot(p.type());
        BlockPos at = BlockPos.containing(pos);
        mob.moveTo(pos.x, pos.y, pos.z, r.nextFloat() * 360F, 0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        mob.setPersistenceRequired();
        sunProof(mob);
        if (mob instanceof Slime slime) {
            // Tamanho fixo por papel (o sorteio vanilla deixava a onda 1 com cubos gigantes ou minúsculos).
            slime.setSize(p.role() == ROLE_WEAK ? 1 + r.nextInt(2) : p.role() == ROLE_STRONG ? 3 : 4, true);
        }
        if (p.role() == ROLE_CHAMPION) makeChampion(mob);
        ServerPlayer target = nearestParticipant(pos);
        if (target != null) mob.setTarget(target);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        tag(mob);
        for (Entity passenger : mob.getIndirectPassengers()) tag(passenger);
        level.addFreshEntityWithPassengers(mob);

        Vec3 c = pos.add(0, 0.5, 0);
        ElementFx.burst(level, c, kind.element, p.role() == ROLE_CHAMPION ? 60 : 24, p.role() == ROLE_CHAMPION ? 0.9 : 0.5);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8F, 1.1F);
        if (p.role() == ROLE_CHAMPION) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 1.6F);
        }
    }

    private void tag(Entity e) {
        e.getPersistentData().putUUID(MOB_TAG, id);
        mobs.add(e.getUUID());
    }

    /** Mortos-vivos de reserva não podem queimar ao sol no meio da prova: ganham um elmo de couro na cor do elemento. */
    private void sunProof(Mob mob) {
        if (!mob.getType().is(EntityTypeTags.UNDEAD) || !mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(kind.element.color, false));
        mob.setItemSlot(EquipmentSlot.HEAD, helmet);
        mob.setDropChance(EquipmentSlot.HEAD, 0F);
    }

    /** Campeão da onda final: mais vida, mais dano, maior e com nome. */
    private void makeChampion(Mob mob) {
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.addOrReplacePermanentModifier(new AttributeModifier(CHAMPION_HP, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.addOrReplacePermanentModifier(new AttributeModifier(CHAMPION_DMG, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) scale.addOrReplacePermanentModifier(new AttributeModifier(CHAMPION_SCALE, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        mob.setCustomName(Component.translatable("entity.isekai_mahou.shrine_champion", mob.getType().getDescription())
                .withColor(kind.element.color));
        mob.setCustomNameVisible(true);
    }

    // =====================================================================================
    //  Bênção do santuário, barra
    // =====================================================================================
    /** O Templo Submerso deixa os participantes respirarem debaixo d'água durante a prova. */
    private void blessParticipants() {
        if (kind.element != Element.WATER) return;
        for (UUID u : participants) {
            ServerPlayer p = player(u);
            if (p != null) p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true));
        }
    }

    private void refreshBar() {
        for (UUID u : participants) {
            ServerPlayer p = player(u);
            if (p != null && !bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
            if (p.isRemoved() || p.level() != level || !participants.contains(p.getUUID())) bar.removePlayer(p);
        }
    }

    private Component barName() {
        Component name = kind.displayName();
        Component text = switch (phase) {
            case PHASE_INTRO -> Component.translatable("bossbar.isekai_mahou.shrine.intro", name);
            case PHASE_FIGHT -> Component.translatable("bossbar.isekai_mahou.shrine.wave", name, wave + 1, WAVES, mobs.size() + pending.size());
            default -> Component.translatable("bossbar.isekai_mahou.shrine.break", name, wave + 2, Math.max(1, (phaseTimer + 19) / 20));
        };
        return text;
    }

    // =====================================================================================
    //  Fim
    // =====================================================================================
    /** Encerra a prova com falha (todos os participantes recebem o motivo). */
    public void fail(Reason reason) {
        if (finished) return;
        for (UUID u : List.copyOf(participants)) {
            ServerPlayer p = player(u);
            if (p != null) {
                p.sendSystemMessage(Component.translatable(reason.key, kind.displayName()).withStyle(ChatFormatting.RED));
                p.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.retry").withStyle(ChatFormatting.GRAY));
            }
        }
        participants.clear();
        end(false);
    }

    /** Vitória (também usada pelo comando /mahou shrine complete). */
    public void succeed() {
        if (finished) return;
        List<ServerPlayer> winners = new ArrayList<>();
        for (UUID u : participants) {
            ServerPlayer p = player(u);
            if (p != null && p.isAlive() && p.level() == level) winners.add(p);
        }
        for (ServerPlayer p : winners) reward(p);
        if (!winners.isEmpty()) {
            Announce.send(winners.get(0), Component.translatable("announce.isekai_mahou.shrine.complete", kind.displayName()),
                    kind.element.color, Announce.STYLE_SYSTEM);
        }
        Vec3 top = center.add(0, 1.3, 0);
        level.sendParticles(ParticleTypes.FLASH, top.x, top.y, top.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y, top.z, 80, 0.6, 0.8, 0.6, 0.5);
        ElementFx.burst(level, top, kind.element, 90, 1.2);
        for (int i = 0; i < 24; i++) {
            // Coluna de luz do elemento subindo do altar.
            level.sendParticles(ElementFx.dust(kind.element, 2.0F), top.x, top.y + i * 0.6, top.z, 3, 0.12, 0.2, 0.12, 0);
        }
        ElementFx.ring(level, center.add(0, 0.1, 0), kind.element, kind.arenaRadius + 0.5, 64);
        level.playSound(null, altar, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2F, 1.0F);
        level.playSound(null, altar, kind.introSound, SoundSource.BLOCKS, 1.5F, 1.3F);
        end(true);
    }

    private void reward(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        String key = kind.progressKey();
        if (d.hasFlag(key)) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.helper").withStyle(ChatFormatting.GRAY));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            return;
        }
        MagicApi.awakenElement(p, kind.element);
        MagicApi.addCounter(p, key, 1);
        d.setFlag(key, true);
        d.markDirty();
        MagicApi.giveXp(p, 60, "shrine");
        MagicApi.giveElementXp(p, kind.element, 30);
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.shrine.success", kind.displayName(), kind.element.displayName())
                .withStyle(ChatFormatting.GOLD));

        List<ItemStack> loot = new ArrayList<>();
        ResourceLocation glyph = pickGlyph(d);
        if (glyph != null) loot.add(GlyphScrollItem.of(glyph));
        else loot.add(new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 2));
        loot.add(new ItemStack(CoreRegistries.ESSENCES.get(kind.element).get(), 3 + level.random.nextInt(3)));
        loot.add(new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), 1 + level.random.nextInt(2)));
        loot.add(new ItemStack(CoreRegistries.MANA_CRYSTAL.get()));
        giveFromAltar(p, loot);
    }

    /** Um glifo do elemento que o jogador ainda não conhece (prioriza a lista do santuário). */
    private ResourceLocation pickGlyph(MagicData d) {
        List<ResourceLocation> options = new ArrayList<>();
        for (String path : kind.rewardGlyphs) {
            ResourceLocation id = IsekaiMahou.id(path);
            if (GlyphRegistry.get(id) != null && !d.knowsGlyph(id)) options.add(id);
        }
        if (options.isEmpty()) {
            for (Glyph g : GlyphRegistry.byElement(kind.element)) {
                if (g.tier() <= 2 && !g.isLegendary() && !d.knowsGlyph(g.id())) options.add(g.id());
            }
        }
        if (options.isEmpty()) return null;
        // Os primeiros da lista (os básicos) têm mais chance.
        int bound = Math.min(options.size(), 3);
        return options.get(level.random.nextInt(bound));
    }

    /** As recompensas saltam do altar na direção do jogador (só ele pode pegar). */
    private void giveFromAltar(ServerPlayer p, List<ItemStack> stacks) {
        Vec3 from = center.add(0, 1.2, 0);
        Vec3 dir = p.position().add(0, 0.5, 0).subtract(from);
        for (ItemStack s : stacks) {
            ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, s);
            Vec3 v = dir.scale(0.08).add((level.random.nextDouble() - 0.5) * 0.1, 0.25, (level.random.nextDouble() - 0.5) * 0.1);
            item.setDeltaMovement(v);
            item.setTarget(p.getUUID());
            item.setPickUpDelay(15);
            level.addFreshEntity(item);
        }
    }

    private void end(boolean success) {
        if (finished) return;
        finished = true;
        pending.clear();
        for (UUID u : mobs) {
            Entity e = level.getEntity(u);
            if (e == null || !e.isAlive()) continue;
            // Na vitória não sobra nada vivo; na falha as criaturas restantes se desfazem.
            level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            ElementFx.burst(level, e.position().add(0, 0.5, 0), kind.element, 10, 0.4);
            e.discard();
        }
        mobs.clear();
        bar.removeAllPlayers();
        if (!success) {
            level.playSound(null, altar, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.8F);
            ElementFx.burst(level, center.add(0, 1.2, 0), kind.element, 20, 0.5);
        }
        if (level.isLoaded(altar) && level.getBlockEntity(altar) instanceof ShrineAltarBlockEntity be) be.finishTrial(COOLDOWN);
    }
}
