package com.renanpontes.isekaimahou.feature.afterlife;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.afterlife.entity.CorpseEntity;
import com.renanpontes.isekaimahou.feature.afterlife.entity.ReaperEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Coração do módulo (servidor): decide o que acontece na morte, cuida do desafio "À Beira da Morte" (alma, Ceifadores,
 * batidas do coração, reanimação pelo grupo) e manda os itens para o Corpo na morte de verdade.
 * <p>
 * Tudo roda na thread do servidor. Os itens só existem em UM lugar por vez: no jogador OU no Corpo (ver
 * {@link CorpseEntity}). O estado do desafio é só em memória; uma marca no jogador ("spirit") cobre queda do servidor: quem
 * entra com a marca e sem desafio ativo morre de verdade (os itens já estão no Corpo, salvo com o chunk).
 */
public final class AfterlifeManager {
    /** Distância do corpo em que as batidas valem (o cliente usa {@link #CLIENT_RANGE}, um pouco menor). */
    static final double BODY_RANGE = 3.2;
    public static final double CLIENT_RANGE = 2.6;
    /** Ticks segurando o botão direito no corpo para o aliado reanimar. */
    static final int HELP_TICKS = 60;
    /** Intervalo mínimo entre duas batidas aceitas (o ritmo do cliente é ~22 ticks). */
    private static final int MIN_BEAT_GAP = 12;
    private static final ResourceLocation SPEED_ID = IsekaiMahou.id("afterlife_spirit_speed");

    /** O jogador local é alma (só o cliente escreve; usado para cancelar ações no lado do cliente também). */
    public static volatile boolean localSpirit;

    private static final class Challenge {
        final UUID player;
        final UUID corpse;
        final ResourceKey<Level> dim;
        final Vec3 body;
        final float yaw;
        @Nullable final DamageSource source;
        final int total;
        final int required;
        final int reaperCount;
        int left;
        int beats;
        long lastBeat = -1000L;
        int spawned;
        final List<UUID> reapers = new ArrayList<>();
        @Nullable UUID helper;
        String helperName = "";
        int helpTicks;
        long lastHelp;

        Challenge(UUID player, UUID corpse, ResourceKey<Level> dim, Vec3 body, float yaw, @Nullable DamageSource source) {
            this.player = player;
            this.corpse = corpse;
            this.dim = dim;
            this.body = body;
            this.yaw = yaw;
            this.source = source;
            this.total = AfterlifeConfig.challengeTicks();
            this.required = AfterlifeConfig.heartbeats();
            this.reaperCount = AfterlifeConfig.reapers();
            this.left = total;
        }
    }

    /** Golpe medido neste tick (vida antes e dano que chegou na vida). */
    private record Hit(long tick, float healthBefore, float damage) {}

    /** Morte de verdade em andamento: para onde vão os itens do LivingDropsEvent. */
    private record Pending(ServerLevel level, Vec3 pos, float yaw, boolean charred, ItemStack[] snapshot, @Nullable UUID corpse) {}

    private static final Map<UUID, Challenge> ACTIVE = new HashMap<>();
    private static final Map<UUID, Hit> HITS = new HashMap<>();
    private static final Map<UUID, Float> HEALTH_BEFORE = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Mortes que NÓS mandamos acontecer (fim do desafio): o LivingDeathEvent deixa passar. */
    private static final Set<UUID> ALLOW_DEATH = new HashSet<>();
    /** Entraram com a marca de alma e sem desafio: morrem no próximo tick. */
    private static final Set<UUID> KILL_ON_JOIN = new HashSet<>();

    // ============================================================================================= consultas

    public static boolean isSpirit(Player p) {
        if (p.level().isClientSide) return localSpirit && p.isLocalPlayer();
        return ACTIVE.containsKey(p.getUUID());
    }

    /** O corpo {@code corpse} é o corpo do desafio ativo de {@code owner}. */
    public static boolean isChallengeCorpse(UUID owner, UUID corpse) {
        Challenge ch = ACTIVE.get(owner);
        return ch != null && ch.corpse.equals(corpse);
    }

    /** O Ceifador ainda caça este jogador (senão some). */
    public static boolean isHunting(ServerPlayer target, ReaperEntity reaper) {
        Challenge ch = ACTIVE.get(target.getUUID());
        return ch != null && ch.reapers.contains(reaper.getUUID());
    }

    static CompoundTag data(Player p) {
        return p.getData(AfterlifeContent.PLAYER_DATA);
    }

    // ============================================================================================= dano e morte

    /** Vida antes do golpe (prioridade mais baixa: depois de todos os outros mexerem no dano). */
    static void beforeDamage(LivingEntity e) {
        // o Pre vem antes da absorção (corações dourados): ela conta como vida para o dano excedente
        if (e instanceof ServerPlayer sp) HEALTH_BEFORE.put(sp.getUUID(), sp.getHealth() + sp.getAbsorptionAmount());
    }

    static void afterDamage(LivingEntity e, float damage) {
        if (!(e instanceof ServerPlayer sp)) return;
        Float before = HEALTH_BEFORE.remove(sp.getUUID());
        if (before == null) return;
        HITS.put(sp.getUUID(), new Hit(sp.serverLevel().getGameTime(), before, damage));
    }

    /** LivingDeathEvent (prioridade mais baixa, só se ninguém cancelou). true = cancelar a morte. */
    static boolean onDeath(ServerPlayer sp, DamageSource src) {
        UUID id = sp.getUUID();
        if (ALLOW_DEATH.remove(id)) return false;
        Challenge ch = ACTIVE.get(id);
        if (ch != null) {
            // morreu sendo alma (/kill, vazio): o desafio acaba como derrota; os itens já estão no corpo
            endChallenge(sp, ch);
            return false;
        }
        ServerLevel level = sp.serverLevel();
        if (level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return false;
        boolean charred = isFireDeath(sp, src);
        if (!charred && nearDeathAllowed(sp, src) && startNearDeath(sp, src)) return true;
        Spot spot = corpseSpot(sp, src);
        PENDING.put(id, new Pending(spot.level(), spot.pos(), sp.yBodyRot, charred, snapshot(sp), null));
        if (charred) {
            Component sub = AfterlifeConfig.deathCorpses()
                    ? Component.translatable("title.isekai_mahou.afterlife.charred.sub").withStyle(ChatFormatting.GRAY) : Component.empty();
            title(sp, Component.translatable("title.isekai_mahou.afterlife.charred").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), sub);
        }
        return false;
    }

    private static boolean isFireDeath(ServerPlayer sp, DamageSource src) {
        if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypes.LAVA) || src.is(DamageTypes.HOT_FLOOR)) return true;
        if (sp.isOnFire() || sp.getRemainingFireTicks() > 0 || sp.isInLava()) return true;
        return statusSafe(sp, Statuses.Kind.BURNING);
    }

    private static boolean nearDeathAllowed(ServerPlayer sp, DamageSource src) {
        if (!AfterlifeConfig.nearDeath() || sp.isCreative() || sp.isSpectator()) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(DamageTypes.FELL_OUT_OF_WORLD) || src.is(DamageTypes.GENERIC_KILL)
                || src.is(DamageTypes.IN_WALL) || src.is(DamageTypes.WITHER) || src.is(DamageTypes.WITHER_SKULL)
                || src.is(DamageTypeTags.IS_FREEZING) || src.is(DamageTypes.OUTSIDE_BORDER)) return false;
        if (sp.hasEffect(MobEffects.WITHER) || sp.isFullyFrozen()) return false;
        for (Statuses.Kind k : new Statuses.Kind[] {Statuses.Kind.FROZEN, Statuses.Kind.PETRIFIED, Statuses.Kind.BLEEDING, Statuses.Kind.CORRUPTED}) {
            if (statusSafe(sp, k)) return false;
        }
        ServerLevel level = sp.serverLevel();
        if (sp.getY() < level.getMinBuildHeight() + 1) return false;
        long now = level.getGameTime();
        if (now < data(sp).getLong("cooldown_until")) return false;
        Hit hit = HITS.get(sp.getUUID());
        if (hit == null || hit.tick() != now) return false; // morte sem golpe medido (comando, outro mod): de verdade
        double limit = AfterlifeConfig.overkill() * sp.getMaxHealth();
        if (src.is(DamageTypeTags.IS_EXPLOSION)) limit *= 0.5; // explosão grande = morte de verdade
        return hit.damage() - hit.healthBefore() <= limit;
    }

    private static boolean statusSafe(LivingEntity e, Statuses.Kind k) {
        try {
            return Statuses.has(e, k);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ============================================================================================= início do desafio

    /** Começa "À Beira da Morte" (o jogador está com vida 0 dentro do LivingDeathEvent). false = não deu, morte normal. */
    static boolean startNearDeath(ServerPlayer sp, @Nullable DamageSource src) {
        if (ACTIVE.containsKey(sp.getUUID())) return false;
        ServerLevel level = sp.serverLevel();
        float oldHealth = sp.getHealth();
        sp.stopRiding();
        // vivo de novo ANTES de mexer no inventário: o item do cursor e a grade de criação voltam ao inventário, não ao chão
        sp.setHealth(1F);
        Vec3 body = groundBelow(level, sp.position());
        CorpseEntity corpse = CorpseEntity.create(level, sp, body, sp.yBodyRot, false, true);
        if (!level.addFreshEntity(corpse)) {
            sp.setHealth(oldHealth);
            return false;
        }
        corpse.absorbInventory(sp);
        CorpseIndex.put(level.getServer(), new CorpseIndex.Entry(corpse.getUUID(), sp.getUUID(), level.dimension(), corpse.blockPosition(),
                corpse.created(), false));

        Challenge ch = new Challenge(sp.getUUID(), corpse.getUUID(), level.dimension(), body, sp.yBodyRot, src);
        ACTIVE.put(sp.getUUID(), ch);
        data(sp).putBoolean("spirit", true);
        sp.setData(AfterlifeContent.PLAYER_DATA, data(sp));

        // a alma sai do corpo: limpa fogo/condições ruins e é jogada alguns blocos para longe
        cleanse(sp);
        setSpirit(sp, true);
        Vec3 spot = spiritSpot(level, body, sp.getRandom());
        sp.teleportTo(level, spot.x, spot.y, spot.z, sp.getYRot(), sp.getXRot());
        sp.setDeltaMovement(Vec3.ZERO);
        calmMobs(sp);
        level.sendParticles(ParticleTypes.SCULK_SOUL, body.x, body.y + 0.4, body.z, 16, 0.4, 0.2, 0.4, 0.04);
        level.playSound(null, body.x, body.y, body.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.8F);
        title(sp, Component.translatable("title.isekai_mahou.afterlife.near_death").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.translatable("title.isekai_mahou.afterlife.near_death.sub").withStyle(ChatFormatting.GRAY));
        sendState(sp, ch);
        return true;
    }

    private static void cleanse(ServerPlayer sp) {
        sp.clearFire();
        sp.fallDistance = 0F;
        sp.setAirSupply(sp.getMaxAirSupply());
        for (Statuses.Kind k : Statuses.Kind.values()) {
            try {
                Statuses.clear(sp, k);
            } catch (RuntimeException ignored) {
                // condição não registrada ainda: nada a limpar
            }
        }
        for (MobEffectInstance inst : new ArrayList<>(sp.getActiveEffects())) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) sp.removeEffect(inst.getEffect());
        }
    }

    /** Mobs que miravam o jogador desistem (a alma não é alvo). */
    private static void calmMobs(ServerPlayer sp) {
        AABB box = sp.getBoundingBox().inflate(48);
        for (Mob m : sp.serverLevel().getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == sp)) {
            m.setTarget(null);
            if (m instanceof NeutralMob nm) nm.stopBeingAngry();
        }
    }

    private static void setSpirit(ServerPlayer sp, boolean on) {
        AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            if (on) speed.addTransientModifier(new AttributeModifier(SPEED_ID, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new AfterlifeNet.Spirit(sp.getUUID(), on));
    }

    // ============================================================================================= tick

    static void tick(MinecraftServer server) {
        if (!KILL_ON_JOIN.isEmpty()) {
            for (UUID id : new ArrayList<>(KILL_ON_JOIN)) {
                KILL_ON_JOIN.remove(id);
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null && sp.isAlive()) killForReal(sp, null);
            }
        }
        if (ACTIVE.isEmpty()) return;
        for (Challenge ch : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer sp = server.getPlayerList().getPlayer(ch.player);
            if (sp == null) {
                ACTIVE.remove(ch.player); // saiu sem o evento de logout (não deve acontecer): o corpo fica com tudo
                continue;
            }
            tickChallenge(sp, ch);
        }
    }

    private static void tickChallenge(ServerPlayer sp, Challenge ch) {
        ServerLevel level = sp.serverLevel();
        if (!level.dimension().equals(ch.dim)) {
            fail(sp, ch, false);
            return;
        }
        ch.left--;
        if (ch.left <= 0) {
            fail(sp, ch, true);
            return;
        }
        int elapsed = ch.total - ch.left;
        int spacing = Math.max(40, ch.total / (ch.reaperCount + 1));
        if (ch.spawned < ch.reaperCount && elapsed >= 20 + ch.spawned * spacing) {
            spawnReaper(level, sp, ch);
            ch.spawned++;
        }
        double dist = sp.position().distanceTo(ch.body);
        if (dist > 48) {
            fail(sp, ch, true);
            return;
        }
        if (dist > 24) {
            // a alma é puxada de volta (não dá para fugir do próprio corpo)
            Vec3 pull = ch.body.subtract(sp.position()).normalize().scale(0.45);
            sp.setDeltaMovement(pull.x, Math.max(pull.y, 0.05), pull.z);
            sp.hurtMarked = true;
        }
        if (ch.helper != null && level.getGameTime() - ch.lastHelp > 10) {
            ch.helper = null;
            ch.helperName = "";
            ch.helpTicks = 0;
        }
        sp.clearFire();
        if (sp.getHealth() < 1F) sp.setHealth(1F);
        if (ch.left % 5 == 0) level.sendParticles(ParticleTypes.SOUL, sp.getX(), sp.getY() + 1.0, sp.getZ(), 1, 0.25, 0.5, 0.25, 0.01);
        if (ch.left % 20 == 0 && level.areEntitiesLoaded(ChunkPos.asLong(BlockPos.containing(ch.body)))
                && !(level.getEntity(ch.corpse) instanceof CorpseEntity)) {
            fail(sp, ch, true); // o corpo foi destruído (comando): não há para onde voltar
            return;
        }
        if (ch.left % 4 == 0) sendState(sp, ch);
    }

    private static void spawnReaper(ServerLevel level, ServerPlayer sp, Challenge ch) {
        RandomSource r = sp.getRandom();
        double ang = r.nextDouble() * Math.PI * 2;
        double d = 12 + r.nextDouble() * 4;
        Vec3 at = new Vec3(ch.body.x + Math.cos(ang) * d, ch.body.y + 0.5 + r.nextDouble() * 1.5, ch.body.z + Math.sin(ang) * d);
        ReaperEntity reaper = ReaperEntity.spawn(level, sp, at);
        ch.reapers.add(reaper.getUUID());
    }

    static void sendState(ServerPlayer sp, Challenge ch) {
        int pct = ch.helper == null ? 0 : Math.min(100, ch.helpTicks * 100 / HELP_TICKS);
        PacketDistributor.sendToPlayer(sp, new AfterlifeNet.State(true, ch.body.x, ch.body.y, ch.body.z, ch.left, ch.total, ch.beats,
                ch.required, pct, ch.helperName));
    }

    // ============================================================================================= ações da alma

    static void onBeat(ServerPlayer sp, boolean hit) {
        Challenge ch = ACTIVE.get(sp.getUUID());
        if (ch == null || sp.position().distanceTo(ch.body) > BODY_RANGE) return;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (hit) {
            if (now - ch.lastBeat < MIN_BEAT_GAP) return;
            ch.lastBeat = now;
            ch.beats++;
            level.playSound(null, ch.body.x, ch.body.y, ch.body.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F,
                    0.9F + 0.08F * ch.beats);
            level.sendParticles(ParticleTypes.HEART, ch.body.x, ch.body.y + 0.5, ch.body.z, 1, 0.1, 0.1, 0.1, 0);
            if (ch.beats >= ch.required) {
                succeed(sp, ch, null);
                return;
            }
        } else {
            ch.beats = Math.max(0, ch.beats - 1);
        }
        sendState(sp, ch);
    }

    static void onGiveUp(ServerPlayer sp) {
        Challenge ch = ACTIVE.get(sp.getUUID());
        if (ch != null) fail(sp, ch, true);
    }

    public static void onCaught(ServerPlayer sp, ReaperEntity reaper) {
        Challenge ch = ACTIVE.get(sp.getUUID());
        if (ch == null) return;
        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.8F, 1.3F);
        fail(sp, ch, true);
    }

    /** Aliado segurando o botão direito no corpo (a cada ~4 ticks). 3 s seguidos = reanima. */
    public static void helpRevive(ServerPlayer helper, CorpseEntity corpse) {
        UUID owner = corpse.owner();
        Challenge ch = owner == null ? null : ACTIVE.get(owner);
        if (ch == null || !ch.corpse.equals(corpse.getUUID())) return;
        ServerPlayer target = helper.server.getPlayerList().getPlayer(owner);
        if (target == null) return;
        if (!Party.sameParty(helper, target)) {
            helper.displayClientMessage(Component.translatable("message.isekai_mahou.afterlife.struggling", corpse.ownerName()), true);
            return;
        }
        long now = helper.serverLevel().getGameTime();
        if (ch.helper == null || !ch.helper.equals(helper.getUUID()) || now - ch.lastHelp > 10) {
            ch.helper = helper.getUUID();
            ch.helperName = helper.getGameProfile().getName();
            ch.helpTicks = 0;
        } else {
            ch.helpTicks += (int) Math.min(10, now - ch.lastHelp);
        }
        ch.lastHelp = now;
        int pct = Math.min(100, ch.helpTicks * 100 / HELP_TICKS);
        helper.displayClientMessage(Component.translatable("message.isekai_mahou.afterlife.reviving", corpse.ownerName(), pct)
                .withStyle(ChatFormatting.AQUA), true);
        helper.serverLevel().sendParticles(ParticleTypes.END_ROD, corpse.getX(), corpse.getY() + 0.4, corpse.getZ(), 2, 0.4, 0.1, 0.4, 0.01);
        if (ch.helpTicks >= HELP_TICKS) succeed(target, ch, helper);
        else sendState(target, ch);
    }

    // ============================================================================================= fim do desafio

    /** Voltou ao corpo: itens de volta (uma vez), vida parcial, Fraqueza breve. */
    private static void succeed(ServerPlayer sp, Challenge ch, @Nullable ServerPlayer helper) {
        if (ACTIVE.remove(sp.getUUID()) != ch) return;
        ServerLevel level = sp.serverLevel();
        clearReapers(level, ch);
        setSpirit(sp, false);
        clearSpiritMark(sp);
        sp.teleportTo(level, ch.body.x, ch.body.y + 0.05, ch.body.z, ch.yaw, 0F);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.fallDistance = 0F;
        if (level.getEntity(ch.corpse) instanceof CorpseEntity corpse && corpse.isAlive()) {
            corpse.setInChallenge(false);
            corpse.restoreTo(sp);
        } else {
            IsekaiMahou.LOGGER.warn("afterlife: corpo de {} não encontrado ao voltar", sp.getGameProfile().getName());
        }
        sp.setHealth(Math.max(1F, (float) (sp.getMaxHealth() * AfterlifeConfig.reviveHealth())));
        sp.clearFire();
        sp.setAirSupply(sp.getMaxAirSupply());
        sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        // 3 s de fôlego para não cair de novo no mesmo golpe
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 3, false, false, true));
        data(sp).putLong("cooldown_until", level.getGameTime() + AfterlifeConfig.cooldownTicks());
        sp.setData(AfterlifeContent.PLAYER_DATA, data(sp));
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1, sp.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 1.2F);
        Component sub = helper == null ? Component.empty()
                : Component.translatable("title.isekai_mahou.afterlife.revived.by", helper.getGameProfile().getName()).withStyle(ChatFormatting.GRAY);
        title(sp, Component.translatable("title.isekai_mahou.afterlife.revived").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), sub);
        if (helper != null) {
            helper.displayClientMessage(Component.translatable("message.isekai_mahou.afterlife.revived_ally", sp.getGameProfile().getName())
                    .withStyle(ChatFormatting.GREEN), true);
        }
        PacketDistributor.sendToPlayer(sp, AfterlifeNet.State.NONE);
        MagicApi.addCounter(sp, helper == null ? "afterlife.returned" : "afterlife.revived_by_ally", 1);
        if (helper != null) MagicApi.addCounter(helper, "afterlife.revived_ally", 1);
    }

    /**
     * Perdeu (tempo, Ceifador, desistiu, saiu do jogo, se afastou): morte de verdade. O jogador renasce sem itens; eles
     * ficam no Corpo no local. {@code teleport} = volta a alma ao corpo antes (o local da morte fica sendo o do corpo).
     */
    private static void fail(ServerPlayer sp, Challenge ch, boolean teleport) {
        if (!endChallenge(sp, ch)) return;
        ServerLevel level = sp.serverLevel();
        if (teleport && level.dimension().equals(ch.dim)) sp.teleportTo(level, ch.body.x, ch.body.y + 0.05, ch.body.z, ch.yaw, 0F);
        killForReal(sp, ch.source);
    }

    /** Encerra o desafio (sem matar): corpo vira corpo comum e recebe o que ainda cair do jogador. false = já tinha acabado. */
    private static boolean endChallenge(ServerPlayer sp, Challenge ch) {
        if (ACTIVE.remove(sp.getUUID()) != ch) return false;
        MinecraftServer server = sp.server;
        ServerLevel bodyLevel = server.getLevel(ch.dim);
        if (bodyLevel != null) {
            clearReapers(bodyLevel, ch);
            if (bodyLevel.getEntity(ch.corpse) instanceof CorpseEntity corpse) {
                corpse.setInChallenge(false);
                corpse.destroyVanishing();
            }
            PENDING.put(sp.getUUID(), new Pending(bodyLevel, ch.body, ch.yaw, false, new ItemStack[0], ch.corpse));
        }
        setSpirit(sp, false);
        clearSpiritMark(sp);
        PacketDistributor.sendToPlayer(sp, AfterlifeNet.State.NONE);
        return true;
    }

    /** Morte de verdade mandada por nós (o LivingDeathEvent deixa passar). */
    private static void killForReal(ServerPlayer sp, @Nullable DamageSource source) {
        UUID id = sp.getUUID();
        ALLOW_DEATH.add(id);
        try {
            sp.setHealth(0F);
            sp.die(source != null ? source : sp.damageSources().generic());
        } finally {
            ALLOW_DEATH.remove(id);
        }
    }

    private static void clearReapers(ServerLevel level, Challenge ch) {
        for (UUID r : ch.reapers) {
            Entity e = level.getEntity(r);
            if (e != null) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.6, 0.3, 0.01);
                e.discard();
            }
        }
        ch.reapers.clear();
    }

    private static void clearSpiritMark(ServerPlayer sp) {
        CompoundTag d = data(sp);
        d.remove("spirit");
        sp.setData(AfterlifeContent.PLAYER_DATA, d);
    }

    // ============================================================================================= itens da morte de verdade

    /** LivingDropsEvent (prioridade mais baixa): o que ia para o chão vai para o Corpo. */
    static void onDrops(ServerPlayer sp, DamageSource src, java.util.Collection<ItemEntity> drops) {
        Pending pd = PENDING.remove(sp.getUUID());
        if (sp.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        List<ItemEntity> list = new ArrayList<>();
        for (ItemEntity ie : drops) if (!ie.getItem().isEmpty()) list.add(ie);
        if (list.isEmpty()) return;
        if (pd == null) {
            Spot spot = corpseSpot(sp, src);
            pd = new Pending(spot.level(), spot.pos(), sp.yBodyRot, false, new ItemStack[0], null);
        }
        CorpseEntity target = null;
        if (pd.corpse() != null && pd.level().getEntity(pd.corpse()) instanceof CorpseEntity c && c.isAlive()) target = c;
        if (target == null) {
            if (!AfterlifeConfig.deathCorpses()) return;
            target = CorpseEntity.create(pd.level(), sp, pd.pos(), pd.yaw(), pd.charred(), false);
            if (!pd.level().addFreshEntity(target)) return; // não deu: itens caem como no vanilla
            CorpseIndex.put(sp.server, new CorpseIndex.Entry(target.getUUID(), sp.getUUID(), pd.level().dimension(), target.blockPosition(),
                    target.created(), pd.charred()));
        }
        boolean[] used = new boolean[pd.snapshot().length];
        for (ItemEntity ie : list) {
            ItemStack s = ie.getItem();
            int from = CorpseEntity.NO_ORIGIN;
            for (int i = 0; i < pd.snapshot().length; i++) {
                if (!used[i] && pd.snapshot()[i] != null && ItemStack.matches(pd.snapshot()[i], s)) {
                    used[i] = true;
                    from = i;
                    break;
                }
            }
            // a entidade do chão ainda não está no mundo: tirá-la da lista = ela nunca aparece; a pilha passa a ser do corpo
            if (target.addOne(s, from)) drops.remove(ie);
        }
    }

    private static ItemStack[] snapshot(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        ItemStack[] snap = new ItemStack[inv.getContainerSize()];
        for (int i = 0; i < snap.length; i++) {
            ItemStack s = inv.getItem(i);
            snap[i] = s.isEmpty() ? null : s.copy();
        }
        return snap;
    }

    // ============================================================================================= lugares

    record Spot(ServerLevel level, Vec3 pos) {}

    /** Onde o corpo fica na morte de verdade: no local; em lava/vazio, no último chão seguro. */
    private static Spot corpseSpot(ServerPlayer sp, DamageSource src) {
        ServerLevel level = sp.serverLevel();
        Vec3 p = sp.position();
        boolean unsafe = sp.isInLava() || src.is(DamageTypes.FELL_OUT_OF_WORLD) || p.y < level.getMinBuildHeight() + 1
                || level.getFluidState(BlockPos.containing(p)).is(FluidTags.LAVA) || src.is(DamageTypes.LAVA);
        if (!unsafe) return new Spot(level, groundBelow(level, p));
        CompoundTag d = data(sp);
        if (d.contains("safe_pos")) {
            ResourceLocation dimId = ResourceLocation.tryParse(d.getString("safe_dim"));
            ServerLevel safeLevel = dimId == null ? null : sp.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
            if (safeLevel != null) return new Spot(safeLevel, Vec3.atBottomCenterOf(BlockPos.of(d.getLong("safe_pos"))));
        }
        ServerLevel spawnLevel = sp.server.overworld();
        BlockPos spawn = spawnLevel.getSharedSpawnPos();
        return new Spot(spawnLevel, Vec3.atBottomCenterOf(spawn));
    }

    /** Guarda o último chão seguro (a cada 10 ticks): no chão, fora de lava/fogo, acima do vazio. */
    static void trackSafeGround(ServerPlayer sp) {
        if (sp.isSpectator() || !sp.onGround() || sp.isInLava() || sp.isOnFire() || isSpirit(sp)) return;
        ServerLevel level = sp.serverLevel();
        BlockPos pos = sp.blockPosition();
        if (pos.getY() < level.getMinBuildHeight() + 2) return;
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.SOUL_CAMPFIRE) || below.is(Blocks.LAVA)) return;
        if (level.getFluidState(pos).is(FluidTags.LAVA)) return;
        CompoundTag d = data(sp);
        String dim = level.dimension().location().toString();
        if (d.getLong("safe_pos") == pos.asLong() && dim.equals(d.getString("safe_dim"))) return;
        d.putLong("safe_pos", pos.asLong());
        d.putString("safe_dim", dim);
        sp.setData(AfterlifeContent.PLAYER_DATA, d);
    }

    /** Chão logo abaixo (até 8 blocos), em cima da forma de colisão (laje, degrau). Sem chão = a própria posição. */
    static Vec3 groundBelow(ServerLevel level, Vec3 p) {
        BlockPos start = BlockPos.containing(p.x, p.y + 0.5, p.z);
        for (int dy = 0; dy <= 8; dy++) {
            BlockPos at = start.below(dy);
            if (at.getY() <= level.getMinBuildHeight()) break;
            BlockState st = level.getBlockState(at);
            VoxelShape shape = st.getCollisionShape(level, at);
            if (!shape.isEmpty()) {
                double top = at.getY() + shape.max(Direction.Axis.Y);
                if (top <= p.y + 0.6) return new Vec3(p.x, top, p.z);
            }
        }
        return p;
    }

    /** Onde a alma "cai" ao sair do corpo: 6–9 blocos, chão firme, 2 blocos livres, sem lava. */
    private static Vec3 spiritSpot(ServerLevel level, Vec3 body, RandomSource r) {
        for (int tries = 0; tries < 20; tries++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double d = 6 + r.nextDouble() * 3;
            double x = body.x + Math.cos(ang) * d, z = body.z + Math.sin(ang) * d;
            for (int dy = 3; dy >= -5; dy--) {
                BlockPos feet = BlockPos.containing(x, body.y + dy, z);
                if (standable(level, feet)) return new Vec3(x, feet.getY(), z);
            }
        }
        return body.add(0, 0.1, 0);
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return false;
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        return !level.getFluidState(feet).is(FluidTags.LAVA) && !level.getFluidState(below).is(FluidTags.LAVA)
                && !level.getBlockState(below).is(Blocks.MAGMA_BLOCK);
    }

    // ============================================================================================= login/logout/servidor

    static void onLogin(ServerPlayer sp) {
        AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
        if (data(sp).getBoolean("spirit") && !ACTIVE.containsKey(sp.getUUID())) {
            // salvo como alma (servidor caiu no meio do desafio): os itens estão no corpo; morte de verdade
            clearSpiritMark(sp);
            KILL_ON_JOIN.add(sp.getUUID());
        }
        CorpseIndex.sendMark(sp);
    }

    /** Sair no meio do desafio = derrota (antes do save do jogador: ele é salvo morto, os itens ficam no corpo). */
    static void onLogout(ServerPlayer sp) {
        Challenge ch = ACTIVE.get(sp.getUUID());
        if (ch != null) fail(sp, ch, false);
        HITS.remove(sp.getUUID());
        HEALTH_BEFORE.remove(sp.getUUID());
        PENDING.remove(sp.getUUID());
        KILL_ON_JOIN.remove(sp.getUUID());
    }

    static void onServerStopping(MinecraftServer server) {
        for (Challenge ch : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer sp = server.getPlayerList().getPlayer(ch.player);
            if (sp != null) fail(sp, ch, false);
        }
        ACTIVE.clear();
        HITS.clear();
        HEALTH_BEFORE.clear();
        PENDING.clear();
        ALLOW_DEATH.clear();
        KILL_ON_JOIN.clear();
    }

    /** Quem passa a ver uma alma recebe a marca (para desenhá-la translúcida). */
    static void onStartTracking(ServerPlayer viewer, Entity target) {
        if (target instanceof ServerPlayer sp && ACTIVE.containsKey(sp.getUUID())) {
            PacketDistributor.sendToPlayer(viewer, new AfterlifeNet.Spirit(sp.getUUID(), true));
        }
    }

    /** /mahou afterlife test: entra À Beira da Morte agora (para testar). */
    static boolean test(ServerPlayer sp) {
        if (ACTIVE.containsKey(sp.getUUID()) || !sp.isAlive()) return false;
        return startNearDeath(sp, null);
    }

    // ============================================================================================= util

    static void title(ServerPlayer sp, Component title, Component sub) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(6, 40, 16));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        sp.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private AfterlifeManager() {}
}
