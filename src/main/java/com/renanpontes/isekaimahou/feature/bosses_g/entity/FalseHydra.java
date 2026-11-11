package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_g.BgPayloads;
import com.renanpontes.isekaimahou.feature.bosses_g.BgRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Falsa Hidra (sob a vila abandonada). O CORPO vive enterrado; só as cabeças ({@link FalseHydraHead}) saem do chão —
 * e elas somem quando alguém olha direto para elas.
 * <ul>
 *   <li><b>Enterrada</b>: o corpo é só um monte de terra que pulsa; golpes nele valem 30% e vão "desenterrando" —
 *       ao acumular 5% da vida em golpes ela EMERGE (dano ×1,25 por alguns segundos);</li>
 *   <li><b>Erupção</b>: a terra marca e treme embaixo de um jogador e o corpo irrompe ali (também tira jogadores de
 *       pilares — anti-trapaça);</li>
 *   <li><b>Bocarra</b>: emergida, morde em cone na frente;</li>
 *   <li><b>Canto do Esquecimento</b>: ela emerge e canta (marca roxa cobrindo o covil). Se ninguém ferir o corpo a
 *       tempo (6% da vida) nem contrariar na Pilha, quem estiver na área ESQUECE: o HUD some por instantes, a barra de
 *       itens embaralha, e ela se cura e regenera todas as cabeças;</li>
 *   <li>cabeça cortada = o corpo sangra (4% da vida) e emerge; a cabeça volta a crescer depois de um tempo.</li>
 * </ul>
 * Fases: 3 cabeças → 4 (60%, botes em linha) → 5 (25%, as cabeças não param durante o canto).
 */
public class FalseHydra extends GBossEntity {
    private static final EntityDataAccessor<Boolean> DATA_EXPOSED = SynchedEntityData.defineId(FalseHydra.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SINGING = SynchedEntityData.defineId(FalseHydra.class, EntityDataSerializers.BOOLEAN);
    public static final int COLOR = 0xB9A6E8;
    private static final int SONG_COLOR = 0xA88BFF;
    private static final int EARTH_COLOR = 0x8A6A3A;
    private static final float SONG_RADIUS = 24F;
    private static final int WIPE_TICKS = 120;

    private final List<UUID> heads = new ArrayList<>();
    private int exposeTicks;
    private float exposeMeter;
    private int regrowTimer = 200;
    private int songCd = 360;
    private int songTimer;
    private int songId = -1;
    private float songDamage;
    private int eruptCd = 160;
    private int eruptTimer;
    private int moveCd = 240;
    private int biteCd = 30;
    private int biteTimer;
    private float attackYaw;

    public FalseHydra(EntityType<? extends FalseHydra> type, Level level) {
        super(type, level);
        this.xpReward = 250;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(340.0, 6.0, 0.0, 12.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    public String bossId() {
        return "false_hydra";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected void configureBar(ServerBossEvent bossBar) {
        bossBar.setDarkenScreen(true);
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.6F, 0.25F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("emerge", "burrow", "sing", "bite", "erupt", "scream", "burrow_move");
    }

    @Override
    protected double wakeRange() {
        return 20.0;
    }

    /** Enterrada, ela sente os passos pelo chão: acorda sem linha de visão. */
    @Override
    protected boolean canWakeSee(Player p) {
        return true;
    }

    @Override
    protected int rewardXp() {
        return 340;
    }

    /**
     * Enterrada, os "olhos" do corpo ficam dentro do monte de terra (o teste vanilla sempre bateria no bloco e ela nunca
     * acharia alvo sozinha). Ela sente o mundo por cima do monte: a linha de visão sai do topo do corpo.
     */
    @Override
    public boolean hasLineOfSight(Entity entity) {
        if (super.hasLineOfSight(entity)) return true;
        if (entity.level() != level() || distanceToSqr(entity) > 40 * 40) return false;
        Vec3 from = position().add(0, getBbHeight() + 0.6, 0);
        Vec3 to = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
        return level().clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    public boolean isExposed() {
        return entityData.get(DATA_EXPOSED);
    }

    public boolean isSinging() {
        return entityData.get(DATA_SINGING);
    }

    @Override
    public List<String> toggledBones() {
        return List.of("flesh", "mound");
    }

    @Override
    public boolean isBoneVisible(String bone) {
        boolean up = isExposed() || isSinging() || isDeadOrDying() || introTicks() > 0;
        return bone.equals("flesh") == up;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EXPOSED, false);
        builder.define(DATA_SINGING, false);
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (isDeadOrDying()) return hold("death");
        if (introTicks() > 0) return play("intro");
        if (isSinging()) return loop("singing");
        if (isExposed()) return loop("exposed");
        return loop("buried");
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Vive sob a terra: não sufoca em blocos. */
    @Override
    public boolean isInWall() {
        return false;
    }

    // ------------------------------------------------------------------ Dano

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !isAwake() || introTicks() > 0) return super.hurt(source, amount);
        boolean up = isExposed() || isSinging();
        float dealt = up ? amount * 1.25F : amount * 0.3F;
        boolean ok = super.hurt(source, dealt);
        if (!ok || !(level() instanceof ServerLevel sl)) return ok;
        if (isSinging()) {
            songDamage += dealt;
            if (songDamage >= getMaxHealth() * 0.06F) interruptSong(sl);
        } else if (!up) {
            exposeMeter += amount;
            hintOnce(sl, "buried");
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState(sl)), getX(), getY() + 0.6, getZ(), 12, 1.0, 0.3, 1.0, 0.1);
            if (exposeMeter >= getMaxHealth() * 0.05F) expose(sl, 100);
        }
        return ok;
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return isSinging() || eruptTimer > 0 || biteTimer > 0;
    }

    @Override
    protected void tickCombat(ServerLevel level, @Nullable LivingEntity target) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        if (songCd > 0) songCd--;
        if (eruptCd > 0) eruptCd--;
        if (moveCd > 0) moveCd--;
        if (biteCd > 0) biteCd--;
        if (tickCount % 20 == 0) manageHeads(level);
        tickAmbience(level);

        if (isSinging()) {
            tickSong(level);
            return;
        }
        if (exposeTicks > 0 && --exposeTicks == 0) burrow(level);
        if (eruptTimer > 0) {
            eruptTimer--;
            return;
        }
        if (biteTimer > 0) {
            biteTimer--;
            holdYaw(attackYaw);
            return;
        }
        if (target == null) return;

        if (songCd <= 0) {
            startSong(level);
            return;
        }
        if (isExposed()) {
            getLookControl().setLookAt(target, 30F, 30F);
            if (biteCd <= 0 && distanceTo(target) < 6.0) startBite(level, target);
            return;
        }
        if (eruptCd <= 0) {
            LivingEntity victim = pickVictim(level, target);
            if (victim != null) {
                startErupt(level, victim, false);
                return;
            }
        }
        if (moveCd <= 0) burrowMove(level, target);
    }

    @Nullable
    private LivingEntity pickVictim(ServerLevel level, LivingEntity fallback) {
        List<ServerPlayer> party = new ArrayList<>(BossKit.partyAround(this, 26));
        party.removeIf(p -> !isFoe(p));
        if (party.isEmpty()) return fallback.distanceTo(this) < 26 ? fallback : null;
        return party.get(random.nextInt(party.size()));
    }

    // ------------------------------------------------------------------ Cabeças

    private int desiredHeads() {
        return switch (phase()) {
            case 0 -> 3;
            case 1 -> 4;
            default -> 5;
        };
    }

    private List<FalseHydraHead> liveHeads(ServerLevel level) {
        List<FalseHydraHead> out = new ArrayList<>();
        heads.removeIf(id -> {
            Entity e = level.getEntity(id);
            if (e instanceof FalseHydraHead h && h.isAlive()) {
                out.add(h);
                return false;
            }
            return true;
        });
        return out;
    }

    private void manageHeads(ServerLevel level) {
        int have = liveHeads(level).size();
        if (have >= desiredHeads()) {
            regrowTimer = Math.max(regrowTimer, 0);
            return;
        }
        // Sem nenhuma cabeça a luta não para: a próxima cresce bem mais rápido.
        if (have == 0) regrowTimer = Math.min(regrowTimer, 160);
        regrowTimer -= 20;
        if (regrowTimer <= 0) {
            spawnHead(level);
            regrowTimer = phase() >= 2 ? 300 : 400;
        }
    }

    private void spawnHead(ServerLevel level) {
        Vec3 c = BossKit.arenaCenter(this) != null ? BossKit.arenaCenter(this) : position();
        Vec3 spot = findGroundSpot(level, c, 4.0, 10.0, BgRegistries.FALSE_HYDRA_HEAD.get(), null);
        if (spot == null) spot = findGroundSpot(level, position(), 3.0, 7.0, BgRegistries.FALSE_HYDRA_HEAD.get(), null);
        if (spot == null) return;
        FalseHydraHead head = FalseHydraHead.spawn(level, this, spot);
        if (head != null) heads.add(head.getUUID());
    }

    /** Uma cabeça foi cortada: o corpo sangra, emerge e grita. */
    public void onHeadSevered(FalseHydraHead head) {
        if (!(level() instanceof ServerLevel sl) || !isAlive()) return;
        heads.remove(head.getUUID());
        float bleed = getMaxHealth() * 0.04F;
        setHealth(Math.max(1F, getHealth() - bleed));
        sl.sendParticles(BgFx.dust(0x6A1830, 1.6F), getX(), getY() + 0.8, getZ(), 30, 1.2, 0.5, 1.2, 0);
        playSound(SoundEvents.WARDEN_ROAR, 1.6F, 1.5F);
        if (!isSinging()) {
            action("scream");
            expose(sl, 120);
        }
        regrowTimer = Math.max(regrowTimer, phase() >= 2 ? 300 : 400);
        hintOnce(sl, "severed");
    }

    // ------------------------------------------------------------------ Emergir / enterrar

    private void expose(ServerLevel level, int ticks) {
        exposeMeter = 0;
        boolean was = isExposed();
        exposeTicks = Math.max(exposeTicks, ticks);
        if (was) return;
        entityData.set(DATA_EXPOSED, true);
        action("emerge");
        playSound(SoundEvents.WARDEN_EMERGE, 1.6F, 1.2F);
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, groundState(level));
        BgFx.ring(level, position().add(0, 0.2, 0), 2.2, 28, dirt, 0.2);
        level.sendParticles(dirt, getX(), getY() + 0.5, getZ(), 40, 1.4, 0.4, 1.4, 0.25);
        BossKit.shake(level, position(), 18, 0.8F, 12);
        hintOnce(level, "exposed");
    }

    private void burrow(ServerLevel level) {
        exposeTicks = 0;
        exposeMeter = 0;
        if (!isExposed()) return;
        entityData.set(DATA_EXPOSED, false);
        action("burrow");
        playSound(SoundEvents.WARDEN_DIG, 1.6F, 1.1F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState(level)), getX(), getY() + 0.5, getZ(), 30, 1.4, 0.3, 1.4, 0.2);
        moveCd = Math.min(moveCd, 60);
    }

    /** Muda de lugar por baixo da terra (rastro de terra revirada denuncia o caminho). */
    private void burrowMove(ServerLevel level, LivingEntity target) {
        moveCd = 220 + random.nextInt(100);
        Vec3 anchor = BossKit.arenaCenter(this) != null ? BossKit.arenaCenter(this) : (home() != null ? Vec3.atBottomCenterOf(home()) : position());
        Vec3 aim = target.position().subtract(anchor).multiply(1, 0, 1);
        Vec3 c = anchor.add(aim.lengthSqr() > 1.0E-3 ? aim.normalize().scale(Math.min(aim.length(), 8.0)) : Vec3.ZERO);
        Vec3 to = findGroundSpot(level, c, 2.0, 9.0, getType(), null);
        if (to == null) return;
        action("burrow_move");
        playSound(SoundEvents.WARDEN_DIG, 1.4F, 0.8F);
        Vec3 from = position();
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, groundState(level));
        for (int i = 1; i <= 6; i++) {
            final double f = i / 6.0;
            BossKit.schedule(level, i * 3, () -> {
                if (!isAlive()) return;
                Vec3 p = from.lerp(to, f);
                level.sendParticles(dirt, p.x, p.y + 0.2, p.z, 10, 0.6, 0.1, 0.6, 0.1);
            });
        }
        final Vec3 dest = to;
        BossKit.schedule(level, 18, () -> {
            if (!isAlive() || isSinging() || isExposed()) return;
            teleportTo(dest.x, dest.y, dest.z);
            setDeltaMovement(Vec3.ZERO);
        });
    }

    // ------------------------------------------------------------------ Erupção

    private void startErupt(ServerLevel level, LivingEntity victim, boolean antiCheese) {
        eruptCd = (phase() >= 2 ? 150 : phase() == 1 ? 190 : 240) + random.nextInt(40);
        int warmup = antiCheese ? 24 : 30;
        eruptTimer = warmup + 4;
        Vec3 spot = groundAt(level, victim.position());
        playSound(SoundEvents.WARDEN_DIG, 1.8F, 0.6F);
        announce("erupt");
        BlockState ground = level.getBlockState(BlockPos.containing(spot).below());
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground);
        for (int t = 0; t < warmup; t += 5) {
            BossKit.schedule(level, t, () -> level.sendParticles(dirt, spot.x, spot.y + 0.1, spot.z, 14, 1.4, 0.05, 1.4, 0.12));
        }
        float dmg = scaled(12F);
        Telegraph.delayed(level, Telegraph.Shape.circle(spot, 3.2F).warmup(warmup).color(EARTH_COLOR).owner(this)
                .castName(attackName("erupt")).element(Element.EARTH), hits -> erupt(level, spot, hits, dmg, antiCheese));
    }

    private void erupt(ServerLevel level, Vec3 spot, List<LivingEntity> hits, float dmg, boolean antiCheese) {
        if (!isAlive()) return;
        Vec3 dest = spot;
        if (!level.noCollision(this, getType().getDimensions().makeBoundingBox(dest))) {
            Vec3 alt = findGroundSpot(level, spot, 0.5, 3.0, getType(), null);
            if (alt != null) dest = alt;
        }
        teleportTo(dest.x, dest.y, dest.z);
        setDeltaMovement(Vec3.ZERO);
        action("erupt");
        expose(level, 80);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                Vec3 push = e.position().subtract(spot).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-3 ? new Vec3(0.3, 0, 0) : push.normalize().scale(antiCheese ? 1.1 : 0.6);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, antiCheese ? 1.1 : 0.85, push.z));
                e.hurtMarked = true;
            }
        }
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, groundState(level));
        level.sendParticles(dirt, dest.x, dest.y + 0.5, dest.z, 60, 1.6, 0.8, 1.6, 0.3);
        BgFx.ring(level, dest.add(0, 0.2, 0), 3.0, 30, dirt, 0.25);
        playSound(SoundEvents.WARDEN_EMERGE, 2.0F, 0.9F);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
        BossKit.shake(level, dest, 22, 1.4F, 14);
    }

    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        // Pilar, buraco ou barco: a terra explode embaixo dele e o arremessa de volta para a arena. O corpo não anda (a
        // navegação quase sempre "falha"), então só reage a trapaça de verdade: alvo no alto, escondido ou montado.
        boolean cheese = target.getY() > getY() + 2.5 || target.isPassenger() || !hasLineOfSight(target);
        if (cheese && !isSinging() && eruptTimer <= 0) startErupt(sl, target, true);
    }

    // ------------------------------------------------------------------ Bocarra

    private void startBite(ServerLevel level, LivingEntity target) {
        biteCd = phase() >= 1 ? 35 : 45;
        biteTimer = 12;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("bite");
        playSound(SoundEvents.RAVAGER_ATTACK, 1.6F, 0.7F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        Telegraph.delayed(level, Telegraph.Shape.cone(position(), attackYaw, 100F, 6.0F).warmup(12).color(Telegraph.DANGER).owner(this).noRunes(),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e)) continue;
                        if (e.hurt(damageSources().mobAttack(this), dmg)) {
                            Statuses.apply(e, Statuses.Kind.BLEEDING, 2, 100, this, 1F);
                        }
                    }
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.6F, 0.6F);
                });
    }

    // ------------------------------------------------------------------ Canto do Esquecimento

    private void startSong(ServerLevel level) {
        int warmup = phase() >= 1 ? 60 : 70;
        songCd = (phase() >= 2 ? 320 : phase() == 1 ? 400 : 480) + random.nextInt(60);
        songTimer = warmup;
        songDamage = 0;
        exposeTicks = 0;
        entityData.set(DATA_EXPOSED, false);
        entityData.set(DATA_SINGING, true);
        action("sing");
        announce("sing");
        hintOnce(level, "song");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.HOSTILE, 3.0F, 0.5F);
        Vec3 c = position();
        songId = Telegraph.delayed(level, Telegraph.Shape.circle(c, SONG_RADIUS).warmup(warmup).color(SONG_COLOR).owner(this)
                .castName(attackName("song")).tier(SpellStack.Tier.MAJOR).element(Element.SHADOW).vertical(6.0, 8.0), hits -> songLands(level, hits));
    }

    private void tickSong(ServerLevel level) {
        songTimer--;
        // Rede de segurança: a marca foi recusada/perdida e o canto não terminou sozinho.
        if (songTimer < -5) {
            songId = -1;
            entityData.set(DATA_SINGING, false);
            expose(level, 40);
            return;
        }
        if (songTimer > 0 && songTimer % 10 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 2.0F,
                    0.5F + random.nextFloat() * 0.6F);
            BgFx.ring(level, position().add(0, 1.5, 0), 1.5 + (songTimer % 30) * 0.2, 16, ParticleTypes.NOTE, 0.0);
        }
        if (songTimer % 4 == 0) {
            level.sendParticles(BgFx.dust(SONG_COLOR, 1.2F), getX(), getY() + 2.4, getZ(), 6, 1.0, 0.6, 1.0, 0.02);
        }
        // Contrariado na Pilha (a marca sumiu antes da hora): ela engasga, igual à interrupção.
        if (songTimer > 1 && songId >= 0 && !Telegraph.isActive(songId)) interruptSong(level);
    }

    private void interruptSong(ServerLevel level) {
        if (!isSinging()) return;
        if (songId >= 0) Telegraph.cancel(level, songId);
        songId = -1;
        entityData.set(DATA_SINGING, false);
        songTimer = 0;
        announce("interrupted");
        action("scream");
        playSound(SoundEvents.WARDEN_HURT, 2.0F, 0.8F);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.6, getZ(), 30, 1.0, 0.6, 1.0, 0.3);
        expose(level, 120);
    }

    private void songLands(ServerLevel level, List<LivingEntity> hits) {
        if (!isAlive()) return;
        songId = -1;
        entityData.set(DATA_SINGING, false);
        boolean anyone = false;
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e instanceof ServerPlayer sp) {
                anyone = true;
                BgPayloads.sendMemoryWipe(sp, WIPE_TICKS);
                shuffleHotbar(sp);
                sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0), this);
            }
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.0F, 0.6F);
        BgFx.sphere(level, position().add(0, 1.5, 0), 4.0, 60, BgFx.dust(SONG_COLOR, 2.0F));
        if (anyone) {
            heal(getMaxHealth() * (0.06F + 0.02F * phase()));
            announce("forgotten");
            // As cabeças cortadas voltam de uma vez.
            int missing = desiredHeads() - liveHeads(level).size();
            for (int i = 0; i < missing; i++) spawnHead(level);
            regrowTimer = phase() >= 2 ? 300 : 400;
        }
        exposeTicks = 0;
        expose(level, 50);
    }

    /** Embaralha os 9 espaços da barra de itens (nada se perde: só troca de lugar). */
    private void shuffleHotbar(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        List<ItemStack> hot = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) hot.add(inv.getItem(i));
        Collections.shuffle(hot, new java.util.Random(random.nextLong()));
        for (int i = 0; i < 9; i++) inv.setItem(i, hot.get(i));
        inv.setChanged();
        sp.inventoryMenu.broadcastChanges();
    }

    /** As cabeças deixam de atacar durante o canto (menos na fase final). */
    public boolean headsPaused() {
        return isSinging() && phase() < 2;
    }

    // ------------------------------------------------------------------ Fases, intro, reset

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.WARDEN_EMERGE, 2.0F, 0.6F);
        entityData.set(DATA_EXPOSED, false);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState(level)), getX(), getY() + 0.2, getZ(), 16, 1.6, 0.2, 1.6, 0.15);
        }
        if (t == 30) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.HOSTILE, 3.0F, 0.4F);
            BossKit.shake(level, position(), 24, 1.0F, 20);
        }
    }

    @Override
    protected void onIntroEnd(ServerLevel level) {
        for (int i = liveHeads(level).size(); i < desiredHeads(); i++) spawnHead(level);
        hintOnce(level, "heads");
    }

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        action("scream");
        playSound(SoundEvents.WARDEN_ROAR, 2.5F, 0.7F);
        BossKit.shake(level, position(), 26, 1.3F, 22);
        for (int i = liveHeads(level).size(); i < desiredHeads(); i++) spawnHead(level);
        eruptCd = Math.min(eruptCd, 60);
        songCd = Math.min(songCd, 200);
    }

    @Override
    protected void onReset(ServerLevel level) {
        heads.clear();
        exposeTicks = songTimer = eruptTimer = biteTimer = 0;
        exposeMeter = songDamage = 0;
        songId = -1;
        songCd = 360;
        eruptCd = 160;
        moveCd = 240;
        regrowTimer = 200;
        entityData.set(DATA_EXPOSED, false);
        entityData.set(DATA_SINGING, false);
    }

    @Override
    protected void onDefeated(ServerLevel sl) {
        entityData.set(DATA_SINGING, false);
        super.onDefeated(sl);
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(BgRegistries.FALSE_HYDRA_HEART.get(), 1, 1);
        dropEssence(Element.SHADOW, 3, 5);
        dropEssence(Element.EARTH, 2, 3);
        dropArcaneScrolls(2);
        dropById("mana_crystal", 4, 8);
    }

    // ------------------------------------------------------------------ Ambiente

    private void tickAmbience(ServerLevel level) {
        if (!isExposed() && !isSinging() && tickCount % 30 == 0) {
            // Coração enterrado: o monte pulsa e a terra se mexe (é assim que se acha o corpo).
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState(level)), getX(), getY() + 0.3, getZ(), 8, 0.9, 0.1, 0.9, 0.05);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4F, 0.7F);
        }
    }

    private BlockState groundState(ServerLevel level) {
        BlockState ground = level.getBlockState(blockPosition().below());
        return ground.isAir() ? Blocks.ROOTED_DIRT.defaultBlockState() : ground;
    }

    @Override
    protected void clientTick() {
        if (isSinging() && random.nextInt(BgFx.reduced() ? 6 : 2) == 0) {
            level().addParticle(ParticleTypes.NOTE, getRandomX(1.5), getY() + 2.0 + random.nextDouble(), getRandomZ(1.5), random.nextDouble(), 0, 0);
        }
        if (isExposed() && random.nextInt(BgFx.reduced() ? 10 : 4) == 0) {
            level().addParticle(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, getRandomX(1.2), getY() + 1.2, getRandomZ(1.2), 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isExposed() ? SoundEvents.WARDEN_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isExposed() || isSinging() ? SoundEvents.WARDEN_HURT : SoundEvents.ROOTED_DIRT_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.3F;
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (UUID id : heads) list.add(NbtUtils.createUUID(id));
        tag.put("Heads", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        heads.clear();
        for (Tag t : tag.getList("Heads", Tag.TAG_INT_ARRAY)) heads.add(NbtUtils.loadUUID(t));
    }
}
