package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.BgRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Rei Mímico (Cofre do Tesouro): um baú real gigante com coroa, dentes e uma língua enorme.
 * <ul>
 *   <li><b>Mordida</b> (cone curto) e <b>Pancada do Tesouro</b> (salta no alvo; pouso marcado + onda que dá para pular);</li>
 *   <li><b>Língua</b>: linha marcada no chão; quem é pego é PUXADO e DEVORADO — fica preso dentro dele, cego, levando
 *       mordidas. O grupo liberta o preso causando 7% da vida (quem está dentro bate mais forte); senão ele é CUSPIDO
 *       em linha reta (marca dourada: quem estiver na frente é atropelado);</li>
 *   <li><b>Chuva de Moedas</b> em cone; <b>Despertar do Tesouro</b>: os baús do cofre viram mímicos (com o loot dentro);</li>
 *   <li>fase 2 (55%): Ganância — moedas em duas ondas, ouro caindo sobre cada jogador; fase 3 (25%): <b>Bocarra sem
 *       Fundo</b> — suga todo mundo com visão dele por 3,5 s; quem estiver no círculo da boca no fim é devorado.</li>
 * </ul>
 */
public class MimicKing extends GBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_HOLDING = SynchedEntityData.defineId(MimicKing.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_MAW = SynchedEntityData.defineId(MimicKing.class, EntityDataSerializers.BOOLEAN);
    public static final int COLOR = 0xE8B23A;
    private static final int GOLD = 0xFFC83D;
    private static final int TONGUE = 0xD8507A;
    private static final int HOLD_TICKS = 70;
    private static final int MAX_MIMICS = 5;

    private int biteCd = 20;
    private int biteTimer;
    private int tongueCd = 80;
    private int tongueTimer;
    private int tongueRepeat;
    private int coinCd = 120;
    private int coinTimer;
    private int awakenCd = 160;
    private int awakenTimer;
    private int leapCd = 200;
    private int rainCd = 200;
    private int vacuumCd = 160;
    private int vacuumTimer;
    private int stunTicks;
    private float attackYaw;
    @Nullable
    private UUID heldId;
    private int heldTicks;
    private float heldDamage;

    public MimicKing(EntityType<? extends MimicKing> type, Level level) {
        super(type, level);
        this.xpReward = 250;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(380.0, 10.0, 0.27, 14.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2);
    }

    @Override
    public String bossId() {
        return "mimic_king";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.YELLOW;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.55F, 0.25F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("bite", "tongue", "spit", "coins", "awaken", "leap", "roar", "gulp", "choke");
    }

    @Override
    protected double arenaRadius() {
        return 24.0;
    }

    @Override
    protected int rewardXp() {
        return 340;
    }

    public boolean isHolding() {
        return entityData.get(DATA_HOLDING);
    }

    public boolean isMawOpen() {
        return entityData.get(DATA_MAW);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HOLDING, false);
        builder.define(DATA_MAW, false);
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        if (!isAwake()) return loop("sleep");
        if (isMawOpen()) return loop("maw");
        if (isHolding()) return loop("chew");
        return loop(moving ? "walk" : "idle");
    }

    @Override
    public List<String> toggledBones() {
        return List.of("crown_gems");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        return phase() < 2;
    }

    // ------------------------------------------------------------------ Dano

    @Override
    public boolean hurt(DamageSource source, float amount) {
        ServerPlayer held = heldPlayer();
        if (held != null && source.getEntity() == held) amount *= 1.5F;   // por dentro é macio
        boolean ok = super.hurt(source, amount);
        if (ok && held != null && level() instanceof ServerLevel sl) {
            heldDamage += amount;
            if (heldDamage >= getMaxHealth() * 0.07F) {
                announce("choke");
                action("choke");
                spit(sl, held, true);
                stunTicks = 30;
            }
        }
        return ok;
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return biteTimer > 0 || tongueTimer > 0 || coinTimer > 0 || awakenTimer > 0 || vacuumTimer > 0 || heldId != null;
    }

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        if (biteCd > 0) biteCd--;
        if (tongueCd > 0) tongueCd--;
        if (coinCd > 0) coinCd--;
        if (awakenCd > 0) awakenCd--;
        if (leapCd > 0) leapCd--;
        if (rainCd > 0) rainCd--;
        if (vacuumCd > 0) vacuumCd--;
        if (heldId != null) tickHold(level);
        if (stunTicks > 0) {
            stunTicks--;
            getNavigation().stop();
            return;
        }
        if (vacuumTimer > 0) {
            tickVacuum(level);
            return;
        }
        if (biteTimer > 0 || tongueTimer > 0 || coinTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            if (biteTimer > 0) biteTimer--;
            if (coinTimer > 0) coinTimer--;
            if (tongueTimer > 0 && --tongueTimer == 0 && tongueRepeat > 0 && target != null && heldId == null) {
                tongueRepeat--;
                startTongue(level, target);
            }
            return;
        }
        if (awakenTimer > 0) {
            awakenTimer--;
            getNavigation().stop();
            return;
        }
        if (target == null) return;
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        if (phase() >= 2 && vacuumCd <= 0 && heldId == null) {
            startVacuum(level);
        } else if (awakenCd <= 0 && countMinions(level, 32) < MAX_MIMICS) {
            startAwaken(level);
        } else if (heldId == null && tongueCd <= 0 && d > 3.5 && d < 15 && los) {
            tongueRepeat = phase() >= 1 ? 1 : 0;
            startTongue(level, target);
        } else if (d < 4.6 && biteCd <= 0) {
            startBite(level, target);
        } else if (coinCd <= 0 && d < 11 && los) {
            startCoins(level, target);
        } else if (phase() >= 1 && rainCd <= 0) {
            startGoldRain(level);
        } else if (heldId == null && leapCd <= 0 && d > 7 && d < 20 && onGround()) {
            startLeap(level, target);
        } else {
            chase(target, heldId != null ? 0.7 : 1.0);
        }
    }

    private Vec3 mouth() {
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        return position().add(f.x * 1.3, 1.1, f.z * 1.3);
    }

    // ------------------------------------------------------------------ Mordida

    private void startBite(ServerLevel level, LivingEntity target) {
        biteCd = phase() >= 1 ? 26 : 34;
        biteTimer = 10;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("bite");
        playSound(SoundEvents.CHEST_OPEN, 1.6F, 0.6F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        Telegraph.delayed(level, Telegraph.Shape.cone(position(), attackYaw, 100F, 4.4F).warmup(10).color(Telegraph.DANGER).owner(this).noRunes(),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) if (isFoe(e) && !isHeld(e)) e.hurt(damageSources().mobAttack(this), dmg);
                    playSound(SoundEvents.CHEST_CLOSE, 2.0F, 0.5F);
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.4F, 0.7F);
                });
    }

    // ------------------------------------------------------------------ Língua e devorar

    private void startTongue(ServerLevel level, LivingEntity target) {
        tongueCd = (phase() >= 1 ? 120 : 160) + random.nextInt(40);
        int warmup = phase() >= 1 ? 14 : 18;
        tongueTimer = warmup + 4;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        action("tongue");
        playSound(SoundEvents.CHEST_OPEN, 1.8F, 0.5F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position().add(dir.scale(1.2));
        Vec3 to = lineEnd(level, from, dir, Math.min(15.0, distanceTo(target) + 3.0));
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, 1.6F).warmup(warmup).color(TONGUE).owner(this)
                .castName(attackName("tongue")), hits -> tongueLands(level, from, to, hits));
    }

    private void tongueLands(ServerLevel level, Vec3 from, Vec3 to, List<LivingEntity> hits) {
        if (!isAlive()) return;
        BgFx.line(level, mouth(), to.add(0, 1.0, 0), BgFx.dust(TONGUE, 1.6F), 2.5);
        playSound(SoundEvents.SLIME_ATTACK, 2.0F, 0.6F);
        hits.removeIf(e -> !isFoe(e) || isHeld(e));
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this)));
        boolean grabbed = false;
        for (LivingEntity e : hits) {
            if (!grabbed && heldId == null && e instanceof ServerPlayer sp) {
                grabbed = true;
                grab(level, sp);
            } else if (e.hurt(damageSources().mobAttack(this), scaled(6F))) {
                Vec3 pull = position().subtract(e.position()).multiply(1, 0, 1);
                if (pull.lengthSqr() > 1.0E-3) {
                    pull = pull.normalize().scale(0.9);
                    e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0.3, pull.z));
                    e.hurtMarked = true;
                }
            }
        }
    }

    private void grab(ServerLevel level, ServerPlayer sp) {
        heldId = sp.getUUID();
        heldTicks = HOLD_TICKS;
        heldDamage = 0;
        entityData.set(DATA_HOLDING, true);
        action("gulp");
        announce("devour");
        hintOnce(level, "devour");
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, HOLD_TICKS + 20, 0), this);
        sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, HOLD_TICKS + 20, 0), this);
        sp.hurt(damageSources().mobAttack(this), scaled(5F));
        playSound(SoundEvents.PLAYER_BURP, 2.0F, 0.4F);
        playSound(SoundEvents.CHEST_CLOSE, 2.0F, 0.4F);
        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(langKey("message", "inside"))
                .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        holdInside(sp);
    }

    @Nullable
    private ServerPlayer heldPlayer() {
        if (heldId == null || !(level() instanceof ServerLevel sl)) return null;
        Player p = sl.getPlayerByUUID(heldId);
        return p instanceof ServerPlayer sp ? sp : null;
    }

    private boolean isHeld(LivingEntity e) {
        return heldId != null && e.getUUID().equals(heldId);
    }

    private void holdInside(ServerPlayer sp) {
        Vec3 c = position().add(0, 0.35, 0);
        sp.fallDistance = 0;
        if (sp.position().distanceToSqr(c) > 0.04) {
            sp.connection.teleport(c.x, c.y, c.z, 0F, 0F, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
        }
        sp.setDeltaMovement(Vec3.ZERO);
    }

    private void tickHold(ServerLevel level) {
        ServerPlayer sp = heldPlayer();
        if (sp == null || !sp.isAlive() || sp.isSpectator() || sp.isCreative() || sp.level() != level || sp.distanceToSqr(this) > 64) {
            release();
            return;
        }
        holdInside(sp);
        heldTicks--;
        if (heldTicks % 15 == 0 && heldTicks > 0) {
            sp.hurt(damageSources().mobAttack(this), scaled(3.0F));
            playSound(SoundEvents.GENERIC_EAT, 1.6F, 0.5F);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), getX(), getY() + 1.4, getZ(),
                    8, 0.6, 0.3, 0.6, 0.1);
        }
        if (heldTicks <= 0) spit(level, sp, false);
    }

    /** Cospe quem está preso em linha reta; a marca dourada avisa quem está na frente (é atropelado). */
    private void spit(ServerLevel level, ServerPlayer sp, boolean early) {
        release();
        LivingEntity t = getTarget();
        float yaw = t != null && t != sp ? yawTo(t.position()) : getYRot() + 180F * random.nextFloat() - 90F;
        Vec3 dir = Vec3.directionFromRotation(0, yaw);
        holdYaw(yaw);
        action("spit");
        Vec3 out = position().add(dir.scale(1.8)).add(0, 1.0, 0);
        // Colado na parede: cospe por cima da tampa (nunca para dentro de um bloco).
        if (!level.noCollision(sp, sp.getDimensions(sp.getPose()).makeBoundingBox(out))) {
            out = position().add(0, getBbHeight() + 0.1, 0);
        }
        sp.connection.teleport(out.x, out.y, out.z, 0F, 0F, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
        double power = early ? 1.0 : 1.7;
        sp.setDeltaMovement(dir.x * power, 0.55, dir.z * power);
        sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        sp.hurt(damageSources().mobAttack(this), scaled(early ? 3F : 6F));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
        sp.removeEffect(MobEffects.BLINDNESS);
        playSound(SoundEvents.LLAMA_SPIT, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.ITEM_SLIME, out.x, out.y, out.z, 30, 0.5, 0.5, 0.5, 0.2);
        Vec3 from = position().add(dir.scale(1.5));
        Vec3 to = lineEnd(level, from, dir, early ? 6.0 : 11.0);
        UUID spat = sp.getUUID();
        float dmg = scaled(8F);
        Telegraph.delayed(level, Telegraph.Shape.line(from, to, 2.0F).warmup(8).color(GOLD).owner(this).noRunes(), hits -> {
            for (LivingEntity e : hits) {
                if (!isFoe(e) || e.getUUID().equals(spat)) continue;
                if (e.hurt(damageSources().mobAttack(this), dmg)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.8, 0.4, dir.z * 0.8));
                    e.hurtMarked = true;
                }
            }
        });
    }

    private void release() {
        heldId = null;
        heldTicks = 0;
        heldDamage = 0;
        entityData.set(DATA_HOLDING, false);
    }

    // ------------------------------------------------------------------ Moedas e ouro

    private void startCoins(ServerLevel level, LivingEntity target) {
        coinCd = (phase() >= 1 ? 110 : 150) + random.nextInt(30);
        coinTimer = 16;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("coins");
        playSound(SoundEvents.CHEST_OPEN, 1.6F, 0.8F);
        coinWave(level, attackYaw, 16, true);
        if (phase() >= 1) {
            float second = attackYaw + (random.nextBoolean() ? 35F : -35F);
            BossKit.schedule(level, 12, () -> {
                if (isAlive()) coinWave(level, second, 16, false);
            });
        }
    }

    private void coinWave(ServerLevel level, float yaw, int warmup, boolean named) {
        float angle = phase() >= 1 ? 80F : 60F;
        Telegraph.Shape s = Telegraph.Shape.cone(position(), yaw, angle, 11F).warmup(warmup).color(GOLD).owner(this);
        if (named) s.castName(attackName("coins")).element(Element.EARTH);
        float dmg = scaled(8F);
        Telegraph.delayed(level, s, hits -> {
            if (!isAlive()) return;
            Vec3 dir = Vec3.directionFromRotation(0, yaw);
            for (LivingEntity e : hits) {
                if (!isFoe(e) || isHeld(e)) continue;
                if (e.hurt(damageSources().mobAttack(this), dmg)) {
                    e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.6, 0.25, dir.z * 0.6));
                    e.hurtMarked = true;
                }
            }
            ItemParticleOption coin = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET));
            Vec3 m = mouth();
            for (int i = 0; i < 36; i++) {
                float a = yaw + (random.nextFloat() - 0.5F) * angle;
                Vec3 v = Vec3.directionFromRotation(-5F - random.nextFloat() * 10F, a).scale(0.7 + random.nextDouble() * 0.6);
                level.sendParticles(coin, m.x, m.y, m.z, 0, v.x, v.y, v.z, 1.0);
            }
            playSound(SoundEvents.ARMOR_EQUIP_GOLD.value(), 2.0F, 0.7F);
            playSound(SoundEvents.CHAIN_BREAK, 1.6F, 1.4F);
        });
    }

    private void startGoldRain(ServerLevel level) {
        rainCd = 260 + random.nextInt(60);
        action("roar");
        announce("greed");
        playSound(SoundEvents.ARMOR_EQUIP_GOLD.value(), 2.0F, 0.5F);
        boolean first = true;
        int n = 0;
        float dmg = scaled(9F);
        for (ServerPlayer p : BossKit.partyAround(this, 26)) {
            if (!isFoe(p) || isHeld(p) || n++ >= 4) continue;
            Telegraph.Shape s = Telegraph.Shape.follow(p, 2.4F).warmup(40).color(GOLD).owner(this);
            if (first) s.castName(attackName("gold_rain"));
            first = false;
            int[] id = new int[1];
            id[0] = Telegraph.delayed(level, s, hits -> {
                Vec3 at = Telegraph.centerOf(id[0]);
                if (at != null) {
                    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_BLOCK)), at.x, at.y + 4, at.z,
                            30, 0.8, 1.5, 0.8, 0.05);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 1.4F);
                }
                for (LivingEntity e : hits) {
                    if (isFoe(e) && !isHeld(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ Despertar do Tesouro

    private void startAwaken(ServerLevel level) {
        awakenCd = (phase() >= 1 ? 460 : 600) + random.nextInt(80);
        awakenTimer = 30;
        action("awaken");
        announce("awaken");
        playSound(SoundEvents.CHEST_LOCKED, 2.0F, 0.5F);
        BossKit.shake(level, position(), 24, 0.6F, 20);
        int want = Math.min(MAX_MIMICS - countMinions(level, 32), 2 + phase());
        if (want <= 0) return;
        List<BlockPos> chests = hasLair() ? findChests(level, 18) : List.of();
        int made = 0;
        boolean named = false;
        for (BlockPos pos : chests) {
            if (made >= want) break;
            made++;
            Vec3 c = Vec3.atBottomCenterOf(pos);
            Telegraph.Shape s = Telegraph.Shape.circle(c, 1.6F).warmup(30).color(GOLD).owner(this).noRunes();
            if (!named) s.castName(attackName("awaken"));
            named = true;
            Telegraph.show(level, s);
            BossKit.schedule(level, 30, () -> awakenChest(level, pos));
        }
        // Sem baús por perto (invocado longe do cofre): os montes de ouro ganham dentes.
        for (; made < want; made++) {
            Vec3 spot = findGroundSpot(level, position(), 3.0, 8.0, BgRegistries.VAULT_MIMIC.get());
            if (spot == null) continue;
            Telegraph.Shape s = Telegraph.Shape.circle(spot, 1.6F).warmup(30).color(GOLD).owner(this).noRunes();
            if (!named) s.castName(attackName("awaken"));
            named = true;
            Telegraph.show(level, s);
            Vec3 at = spot;
            BossKit.schedule(level, 30, () -> {
                if (isAlive()) spawnMimic(level, at, List.of(), null);
            });
        }
    }

    /** Baús (blocos de verdade) a até {@code r} blocos, os mais perto primeiro (lê as entidades de bloco dos chunks). */
    private List<BlockPos> findChests(ServerLevel level, int r) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = blockPosition();
        ChunkPos min = new ChunkPos(BlockPos.containing(c.getX() - r, 0, c.getZ() - r));
        ChunkPos max = new ChunkPos(BlockPos.containing(c.getX() + r, 0, c.getZ() + r));
        for (int cx = min.x; cx <= max.x; cx++) {
            for (int cz = min.z; cz <= max.z; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ChestBlockEntity)) continue;
                    BlockPos p = be.getBlockPos();
                    if (p.distSqr(c) <= r * r && Math.abs(p.getY() - c.getY()) <= 6) out.add(p.immutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(c) + random.nextInt(40)));
        return out;
    }

    private void awakenChest(ServerLevel level, BlockPos pos) {
        if (!isAlive()) return;
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        ResourceKey<LootTable> loot = chest.getLootTable();
        chest.setLootTable(null);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty()) items.add(s.copy());
            chest.setItem(i, ItemStack.EMPTY);
        }
        level.removeBlock(pos, false);
        spawnMimic(level, Vec3.atBottomCenterOf(pos), items, loot);
    }

    private void spawnMimic(ServerLevel level, Vec3 at, List<ItemStack> items, @Nullable ResourceKey<LootTable> loot) {
        VaultMimic m = BgRegistries.VAULT_MIMIC.get().create(level);
        if (m == null) return;
        m.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0);
        m.addTag(MINION_TAG);
        m.storeChest(items, loot);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
        if (getTarget() != null) m.setTarget(getTarget());
        level.addFreshEntity(m);
        m.startAwaken();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), at.x, at.y + 0.6, at.z, 20, 0.4, 0.3, 0.4, 0.15);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHEST_OPEN, SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    // ------------------------------------------------------------------ Pancada do Tesouro

    private void startLeap(ServerLevel level, LivingEntity target) {
        leapCd = 220 + random.nextInt(60);
        int t = BossKit.leapAt(this, target, 1.0);
        leapTicks = t;
        action("leap");
        playSound(SoundEvents.CHEST_OPEN, 2.0F, 0.4F);
        Vec3 land = target.position();
        float dmg = scaled(12F);
        Telegraph.delayed(level, Telegraph.Shape.circle(land, 3.5F).warmup(Math.max(12, t)).color(Telegraph.WARNING).owner(this)
                .castName(attackName("leap")), hits -> {
                    landingSlam(level, hits, dmg);
                    if (!isAlive()) return;
                    Vec3 c = position();
                    float wave = scaled(7F);
                    Telegraph.delayed(level, Telegraph.Shape.ring(c, 3.5F, 8F).warmup(14).vertical(0.5, 1.0).color(GOLD).owner(this).noRunes(), ring -> {
                        for (LivingEntity e : ring) if (isFoe(e) && !isHeld(e)) e.hurt(damageSources().mobAttack(this), wave);
                        BgFx.ring(level, c.add(0, 0.2, 0), 6.0, 40, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), 0.1);
                    });
                });
    }

    // ------------------------------------------------------------------ Bocarra sem Fundo

    private void startVacuum(ServerLevel level) {
        vacuumCd = 560 + random.nextInt(80);
        vacuumTimer = 70;
        entityData.set(DATA_MAW, true);
        getNavigation().stop();
        announce("maw");
        hintOnce(level, "maw");
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.5F);
        Telegraph.delayed(level, Telegraph.Shape.circle(position(), 3.6F).warmup(70).color(Telegraph.DANGER).owner(this)
                .castName(attackName("maw")).tier(SpellStack.Tier.ULTIMATE), hits -> mawCloses(level, hits));
    }

    private void tickVacuum(ServerLevel level) {
        vacuumTimer--;
        getNavigation().stop();
        Vec3 c = position().add(0, 1.0, 0);
        for (ServerPlayer p : BossKit.partyAround(this, 18)) {
            if (!isFoe(p) || isHeld(p) || !p.hasLineOfSight(this)) continue;
            Vec3 pull = c.subtract(p.position()).multiply(1, 0, 1);
            double d = pull.length();
            if (d < 0.5) continue;
            pull = pull.scale(1.0 / d * 0.075);
            p.setDeltaMovement(p.getDeltaMovement().add(pull));
            p.hurtMarked = true;
            if (vacuumTimer % 3 == 0) {
                Vec3 at = p.position().add(0, 1.0, 0);
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, pull.x * 8, 0, pull.z * 8, 1.0);
            }
        }
        if (vacuumTimer % 10 == 0) playSound(SoundEvents.ELYTRA_FLYING, 1.4F, 0.5F);
        if (vacuumTimer <= 0) entityData.set(DATA_MAW, false);
    }

    private void mawCloses(ServerLevel level, List<LivingEntity> hits) {
        entityData.set(DATA_MAW, false);
        vacuumTimer = 0;
        if (!isAlive()) return;
        action("bite");
        playSound(SoundEvents.CHEST_CLOSE, 3.0F, 0.3F);
        BossKit.shake(level, position(), 20, 1.2F, 12);
        float dmg = scaled(14F);
        boolean grabbed = false;
        for (LivingEntity e : hits) {
            if (!isFoe(e) || isHeld(e)) continue;
            e.hurt(damageSources().mobAttack(this), dmg);
            if (!grabbed && heldId == null && e instanceof ServerPlayer sp && sp.isAlive()) {
                grabbed = true;
                grab(level, sp);
            }
        }
    }

    // ------------------------------------------------------------------ Fases, intro, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        action("roar");
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.6F);
        BossKit.shake(level, position(), 24, 1.2F, 20);
        awakenCd = Math.min(awakenCd, 20);
        rainCd = Math.min(rainCd, 80);
        if (phase >= 2) vacuumCd = Math.min(vacuumCd, 100);
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.CHEST_OPEN, 2.5F, 0.4F);
        playSound(SoundEvents.ARMOR_EQUIP_GOLD.value(), 2.0F, 0.5F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 6 == 0) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), getX(), getY() + 2.0, getZ(),
                    10, 0.8, 0.4, 0.8, 0.15);
        }
        if (t == 36) {
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
            BossKit.shake(level, position(), 24, 1.0F, 16);
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        release();
        biteTimer = tongueTimer = coinTimer = awakenTimer = vacuumTimer = stunTicks = 0;
        biteCd = 20;
        tongueCd = 80;
        coinCd = 120;
        awakenCd = 160;
        leapCd = 200;
        rainCd = 200;
        vacuumCd = 160;
        entityData.set(DATA_MAW, false);
    }

    @Override
    protected void onDefeated(ServerLevel sl) {
        ServerPlayer sp = heldPlayer();
        release();
        if (sp != null) sp.removeEffect(MobEffects.BLINDNESS);
        entityData.set(DATA_MAW, false);
        super.onDefeated(sl);
    }

    @Override
    public void remove(RemovalReason reason) {
        release();
        super.remove(reason);
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(BgRegistries.GREEDY_COIN.get(), 2, 3);
        dropItem(Items.GOLD_BLOCK, 2, 4);
        dropEssence(Element.EARTH, 2, 4);
        dropEssence(Element.ARCANE, 2, 3);
        dropArcaneScrolls(2);
        dropById("mana_crystal", 4, 8);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(BgFx.reduced() ? 16 : 6) == 0) {
            level().addParticle(ParticleTypes.WAX_OFF, getRandomX(1.0), getY() + 1.4 + random.nextDouble() * 0.8, getRandomZ(1.0), 0, 0.02, 0);
        }
        if (isMawOpen() && random.nextInt(2) == 0) {
            Vec3 f = Vec3.directionFromRotation(0, getYRot());
            double a = random.nextDouble() * Math.PI * 2;
            double r = 4 + random.nextDouble() * 6;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            level().addParticle(ParticleTypes.CLOUD, x, getY() + 1.0, z, (getX() + f.x - x) * 0.08, 0, (getZ() + f.z - z) * 0.08);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CHEST_LOCKED;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    /** Quem estava preso é solto ao recarregar (nada de ficar preso num chefe salvo). */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        release();
    }

    @Override
    public boolean isPushable() {
        return heldId == null && super.isPushable();
    }

    @Override
    protected void doPush(Entity entity) {
        if (heldId == null || !entity.getUUID().equals(heldId)) super.doPush(entity);
    }
}
