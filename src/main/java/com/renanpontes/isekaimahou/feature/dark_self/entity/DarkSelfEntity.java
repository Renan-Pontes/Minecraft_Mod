package com.renanpontes.isekaimahou.feature.dark_self.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;
import com.renanpontes.isekaimahou.feature.dark_self.DarkSelfService;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * <b>A Sombra</b> (Dark Self, v1.1): uma cópia escura do jogador que surge no nível 100. O cliente desenha com a SKIN do
 * dono, escurecida, com olhos brilhando; ela veste cópias (só visuais) da armadura e das mãos dele.
 * <ul>
 *   <li>Conjura as magias do LOADOUT ATIVO do dono ({@link SpellCaster#castAsMob}); a ultimate (G) uma vez, abaixo de
 *       metade da vida; abaixo de 35% devolve a ÚLTIMA magia que o dono usou.</li>
 *   <li>Imita a esquiva: quando o dono conjura ou bate, às vezes ela salta de lado.</li>
 *   <li>Duelo: só o dono pode feri-la (e as criaturas dele); ela só mira o dono. Não quebra blocos.</li>
 *   <li>Some (e volta outro dia) se o dono morrer, sair do mundo, mudar de dimensão, ficar a mais de 48 blocos ou
 *       entrar no Modo Paz. Não é salva no disco.</li>
 * </ul>
 * O ciclo (sumir a sombra → surgir → vitória) é do {@link DarkSelfService}.
 */
public class DarkSelfEntity extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(DarkSelfEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_CASTING =
            SynchedEntityData.defineId(DarkSelfEntity.class, EntityDataSerializers.BOOLEAN);

    /** Ticks que ela leva para sair do chão (invulnerável e parada enquanto isso). */
    public static final int RISE_TICKS = 40;
    /** Distância máxima do dono antes de ela se desfazer. */
    public static final double LEASH = 48.0;
    /** Força das magias dela (o jogador no nível 100 tem 1,0). */
    private static final float SPELL_POWER = 0.8F;
    private static final LookModifiers NO_STATS = new LookModifiers();

    private final List<DarkSpell> spells = new ArrayList<>();
    private Element element = Element.ARCANE;
    private boolean ultimateUsed;
    @Nullable private DarkSpell mirror;
    private long mirrorReadyAt;
    private long dodgeReadyAt;
    private int ownerMissing;
    private boolean ended;
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.isekai_mahou.dark_self"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    /** Uma magia copiada do dono, já validada. */
    private static final class DarkSpell {
        final SpellRecipe recipe;
        final Element element;
        final boolean ultimate;
        final String form;
        final int castTicks;
        final int cooldown;
        long readyAt;

        DarkSpell(SpellRecipe recipe, Element element, ParsedSpell parsed) {
            this.recipe = recipe;
            this.element = element;
            this.ultimate = parsed.isUltimate();
            this.form = parsed.form().id().getPath();
            this.castTicks = parsed.castTicks();
            this.cooldown = parsed.cooldown();
        }

        boolean inRange(double dist) {
            return switch (form) {
                case "touch" -> dist < 3.0;
                case "self" -> dist < 10.0;
                default -> dist < 22.0;
            };
        }
    }

    public DarkSelfEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        setPersistenceRequired();
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, LEASH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_CASTING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DuelGoal(this));
        // Sem goals de alvo: ela só mira o dono (customServerAiStep).
    }

    // =====================================================================================
    //  Ligação com o dono
    // =====================================================================================
    /** Copia o dono: magias do loadout ativo, aparência do equipamento e força escalada pela vida dele. */
    public void bind(ServerPlayer owner) {
        entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        MagicData d = MagicApi.get(owner);
        int loadout = d.activeLoadout();
        element = null;
        spells.clear();
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe r = d.peekSlot(loadout, slot).main();
            if (r == null || r.isEmpty()) continue;
            Element el = d.slotElement(loadout, slot);
            if (el == null) el = Element.ARCANE;
            DarkSpell s = parse(r, el);
            if (s != null) {
                spells.add(s);
                if (element == null) element = el;
            }
        }
        if (element == null) element = Element.ARCANE;
        if (spells.isEmpty()) {
            // Sem magias no loadout (classes de luta): o básico do Arcano, para ainda ter alcance.
            addFallback(List.of(IsekaiMahou.id("projectile"), IsekaiMahou.id("arcane_damage")));
            addFallback(List.of(IsekaiMahou.id("touch"), IsekaiMahou.id("push"), IsekaiMahou.id("arcane_damage")));
        }

        // Força: ~4× a vida do dono (o núcleo ainda dobra a vida das criaturas do mod), golpe ≈ 7% da vida dele.
        float ownerHp = owner.getMaxHealth();
        double effectiveHp = Mth.clamp(ownerHp * 4.0, 300.0, 1500.0);
        setBase(Attributes.MAX_HEALTH, effectiveHp / Progression.MOB_HEALTH_SCALE);
        setBase(Attributes.ATTACK_DAMAGE, Math.max(3.0, ownerHp * 0.07 / Progression.MOB_DAMAGE_SCALE));
        setBase(Attributes.ARMOR, Mth.clamp(owner.getArmorValue() * 0.5, 4.0, 14.0));
        setHealth(getMaxHealth());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            setItemSlot(slot, lookCopy(owner.getItemBySlot(slot)));
        }
        setCustomName(Component.translatable("entity.isekai_mahou.dark_self.of", owner.getName()));
        bossBar.setName(getCustomName());
        setTarget(owner);
    }

    /** Cópia SÓ VISUAL: sem atributos e sem encantamentos (o brilho fica), para ela não herdar o poder do item. */
    private static ItemStack lookCopy(ItemStack original) {
        if (original.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = original.copyWithCount(1);
        boolean glint = copy.isEnchanted();
        // Lista não vazia com um modificador neutro: substitui os atributos padrão do item (lista vazia = padrão).
        copy.set(DataComponents.ATTRIBUTE_MODIFIERS, NO_STATS.modifiers());
        copy.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (glint) copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return copy;
    }

    /** Guarda a lista de modificadores "neutra" (criada na primeira vez, depois dos registros). */
    private static final class LookModifiers {
        private ItemAttributeModifiers cached;

        ItemAttributeModifiers modifiers() {
            if (cached == null) {
                cached = ItemAttributeModifiers.builder()
                        .add(Attributes.LUCK, new AttributeModifier(IsekaiMahou.id("dark_self_look"), 0.0,
                                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY)
                        .build().withTooltip(false);
            }
            return cached;
        }
    }

    @Nullable
    private static DarkSpell parse(SpellRecipe r, Element el) {
        SpellParser.Result res = SpellParser.parse(r, null, null);
        return res.ok() ? new DarkSpell(r, el, res.spell()) : null;
    }

    private void addFallback(List<net.minecraft.resources.ResourceLocation> glyphs) {
        DarkSpell s = parse(new SpellRecipe(glyphs, "", -1, ""), Element.ARCANE);
        if (s != null) spells.add(s);
    }

    private void setBase(Holder<Attribute> attr, double v) {
        AttributeInstance inst = getAttribute(attr);
        if (inst != null) inst.setBaseValue(v);
    }

    public Optional<UUID> ownerId() {
        return entityData.get(DATA_OWNER);
    }

    public boolean isCasting() {
        return entityData.get(DATA_CASTING);
    }

    /** 0 → 1 enquanto sai do chão (o cliente usa para descer o modelo). */
    public float riseProgress(float partialTick) {
        return Mth.clamp((tickCount + partialTick) / RISE_TICKS, 0F, 1F);
    }

    public boolean isOwner(@Nullable Entity e) {
        return e != null && ownerId().map(id -> id.equals(e.getUUID())).orElse(false);
    }

    @Nullable
    private ServerPlayer owner() {
        if (!(level() instanceof ServerLevel sl)) return null;
        return ownerId().map(id -> sl.getServer().getPlayerList().getPlayer(id)).orElse(null);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (ended) return;
        ServerLevel sl = (ServerLevel) level();
        if (tickCount < RISE_TICKS && tickCount % 4 == 0) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.1, getZ(), 6, 0.4, 0.05, 0.4, 0.02);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.2, getZ(), 4, 0.3, 0.1, 0.3, 0.02);
        }
        if (tickCount % 10 != 0) return;
        ServerPlayer owner = owner();
        if (owner == null) {
            // Dono saiu (ou a Sombra veio de um lugar estranho): espera um pouco e some.
            if (++ownerMissing > 10) fade(DarkSelfService.Reason.LEFT);
            return;
        }
        ownerMissing = 0;
        if (!DarkSelfService.isDuelEntity(owner, getUUID())) {
            vanishQuietly(); // não é a Sombra ativa do dono (comando de reset, duplicata...)
            return;
        }
        if (!owner.isAlive()) { fade(DarkSelfService.Reason.DIED); return; }
        if (owner.level() != level()) { fade(DarkSelfService.Reason.FLED); return; }
        if (distanceToSqr(owner) > LEASH * LEASH) { fade(DarkSelfService.Reason.FLED); return; }
        if (PeaceMode.isOn(owner)) { fade(DarkSelfService.Reason.PEACE); return; }
        if (getTarget() != owner) setTarget(owner);
        DarkSelfService.markSpot(this, true);
        if (!bossBar.getPlayers().contains(owner)) bossBar.addPlayer(owner);
        bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0F, 1F));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) return;
        Vector3f col = new Vector3f(0.32F, 0.12F, 0.45F);
        if (random.nextInt(3) == 0) {
            level().addParticle(new DustParticleOptions(col, 1.1F), getRandomX(0.6), getY() + random.nextDouble() * 0.3,
                    getRandomZ(0.6), 0, 0.02, 0);
        }
        if (random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.SMOKE, getRandomX(0.5), getY() + random.nextDouble() * getBbHeight(),
                    getRandomZ(0.5), 0, 0.03, 0);
        }
        if (isCasting()) {
            double a = tickCount * 0.5;
            for (int i = 0; i < 2; i++) {
                double ang = a + i * Math.PI;
                level().addParticle(new DustParticleOptions(new Vector3f(0.75F, 0.1F, 0.15F), 1.0F),
                        getX() + Math.cos(ang) * 0.8, getY() + 1.2, getZ() + Math.sin(ang) * 0.8, 0, 0.05, 0);
            }
        }
    }

    /** Some em fumaça e avisa o serviço (a Sombra pode voltar depois). */
    public void fade(DarkSelfService.Reason reason) {
        if (ended) return;
        ended = true;
        ownerId().ifPresent(id -> DarkSelfService.onFade(((ServerLevel) level()).getServer(), id, getUUID(), reason));
        vanishQuietly();
    }

    /** Só as partículas e o sumiço (sem mexer no estado do dono). */
    public void vanishQuietly() {
        ended = true;
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.05);
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 15, 0.3, 0.6, 0.3, 0.02);
        }
        bossBar.removeAllPlayers();
        discard();
    }

    // =====================================================================================
    //  Duelo: dano, esquiva, espelho
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (tickCount < RISE_TICKS || ended) return false;
        Entity src = source.getEntity();
        if (src != null && !isOwner(src)) {
            // Ninguém interfere: só o dono (e as criaturas dele) ferem a Sombra.
            boolean ownersPet = src instanceof OwnableEntity pet && pet.getOwnerUUID() != null
                    && ownerId().map(id -> id.equals(pet.getOwnerUUID())).orElse(false);
            if (!ownersPet) {
                if (src instanceof ServerPlayer other && tickCount % 4 == 0) {
                    other.displayClientMessage(Component.translatable("message.isekai_mahou.dark_self.not_yours"), true);
                }
                return false;
            }
        }
        boolean hit = super.hurt(source, amount);
        if (hit && src instanceof LivingEntity attacker && isOwner(attacker) && source.getDirectEntity() == attacker
                && random.nextFloat() < 0.2F) {
            sidestep(attacker);
        }
        return hit;
    }

    /** O dono conjurou: guarda a magia (para devolvê-la) e às vezes esquiva como ele. */
    public void onOwnerCast(LivingEntity owner, SpellRecipe recipe, Element el, ParsedSpell parsed) {
        if (ended || tickCount < RISE_TICKS) return;
        if (!parsed.isUltimate()) {
            DarkSpell copy = parse(recipe, el != null ? el : Element.ARCANE);
            if (copy != null) mirror = copy;
        }
        if (random.nextFloat() < 0.4F && distanceToSqr(owner) < 24 * 24) sidestep(owner);
    }

    /** Salto lateral (a esquiva do jogador, espelhada). */
    private void sidestep(LivingEntity from) {
        long now = level().getGameTime();
        if (now < dodgeReadyAt || !onGround()) return;
        dodgeReadyAt = now + 50;
        Vec3 to = from.position().subtract(position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0E-4) to = new Vec3(1, 0, 0);
        Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(random.nextBoolean() ? 0.9 : -0.9);
        setDeltaMovement(side.x, 0.25, side.z);
        hurtMarked = true;
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.2, getZ(), 6, 0.25, 0.05, 0.25, 0.01);
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35F, 1.6F);
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================
    @Nullable
    private DarkSpell pickSpell(double dist) {
        long now = level().getGameTime();
        float hp = getHealth() / getMaxHealth();
        // Abaixo de 35%: devolve a última magia do dono (a cada 8 s, se estiver ao alcance).
        if (hp < 0.35F && mirror != null && now >= mirrorReadyAt && mirror.inRange(dist)) return mirror;
        boolean desperate = hp < 0.5F && !ultimateUsed;
        List<DarkSpell> ready = new ArrayList<>();
        for (DarkSpell s : spells) {
            if (s.readyAt > now || !s.inRange(dist)) continue;
            if (s.ultimate) {
                if (desperate) return s;
                continue;
            }
            ready.add(s);
        }
        return ready.isEmpty() ? null : ready.get(random.nextInt(ready.size()));
    }

    private void aimAt(LivingEntity target) {
        Vec3 from = getEyePosition();
        Vec3 to = target.getEyePosition().subtract(0, 0.3, 0);
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) (-(Mth.atan2(dy, h) * Mth.RAD_TO_DEG));
        setYRot(yaw);
        setXRot(pitch);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    private void release(DarkSpell spell, LivingEntity target) {
        aimAt(target);
        entityData.set(DATA_CASTING, false);
        swing(InteractionHand.MAIN_HAND);
        var ctx = SpellCaster.castAsMob(this, spell.element, spell.recipe, SPELL_POWER);
        long now = level().getGameTime();
        if (ctx == null) {
            spell.readyAt = now + 60;
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.4, getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            playSound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
            return;
        }
        if (spell == mirror) {
            mirrorReadyAt = now + 160;
            target.sendSystemMessage(Component.translatable("message.isekai_mahou.dark_self.mirror").withColor(0xB58CFF));
        } else {
            spell.readyAt = now + Math.max(40, Math.round(spell.cooldown * 1.3F));
            if (spell.ultimate) ultimateUsed = true;
        }
    }

    private void blinkNear(LivingEntity target) {
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(3.5));
        for (int i = 0; i < 8; i++) {
            double x = behind.x + (random.nextDouble() - 0.5) * 3, z = behind.z + (random.nextDouble() - 0.5) * 3;
            double ox = getX(), oy = getY(), oz = getZ();
            if (randomTeleport(x, target.getY(), z, true)) {
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SQUID_INK, ox, oy + 1, oz, 20, 0.3, 0.6, 0.3, 0.02);
                    sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.05);
                }
                playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.6F);
                return;
            }
        }
    }

    /** IA do duelo: mantém distância e conjura; se o dono encostar, luta corpo a corpo; se ele fugir, teleporta. */
    private static final class DuelGoal extends Goal {
        private final DarkSelfEntity self;
        private DarkSpell casting;
        private int castLeft;
        private int globalCd = 30;
        private int meleeCd;
        private int strafeDir = 1;
        private int strafeTimer;
        private int farTicks;

        DuelGoal(DarkSelfEntity self) {
            this.self = self;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = self.getTarget();
            return self.tickCount > RISE_TICKS && !self.ended && t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            casting = null;
            castLeft = 0;
            self.entityData.set(DATA_CASTING, false);
            self.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = self.getTarget();
            if (t == null) return;
            self.getLookControl().setLookAt(t, 30F, 30F);
            double dist = self.distanceTo(t);
            boolean sees = self.getSensing().hasLineOfSight(t);
            if (globalCd > 0) globalCd--;
            if (meleeCd > 0) meleeCd--;

            if (casting != null) {
                self.getNavigation().stop();
                self.aimAt(t);
                if (self.level() instanceof ServerLevel sl) ElementFx.channeling(sl, self, casting.element);
                if (--castLeft <= 0) {
                    self.release(casting, t);
                    casting = null;
                    globalCd = 22 + self.random.nextInt(25);
                }
                return;
            }
            if (globalCd <= 0 && sees) {
                DarkSpell s = self.pickSpell(dist);
                if (s != null) {
                    casting = s;
                    castLeft = Mth.clamp(s.castTicks, 12, 30);
                    self.entityData.set(DATA_CASTING, true);
                    self.getNavigation().stop();
                    self.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F, 0.6F);
                    return;
                }
            }

            farTicks = (dist > 14 || !sees) ? farTicks + 1 : 0;
            if (farTicks > 60) {
                farTicks = 0;
                self.blinkNear(t);
                return;
            }

            if (dist < 3.2) {
                self.getNavigation().moveTo(t, 1.15);
                if (dist < 2.4 && meleeCd <= 0) {
                    self.swing(InteractionHand.MAIN_HAND);
                    self.doHurtTarget(t);
                    meleeCd = 20;
                }
            } else if (dist > 11 || !sees) {
                self.getNavigation().moveTo(t, 1.0);
            } else {
                self.getNavigation().stop();
                if (--strafeTimer <= 0) {
                    strafeTimer = 30 + self.random.nextInt(40);
                    strafeDir = -strafeDir;
                }
                self.getMoveControl().strafe(dist < 6 ? -0.45F : 0.1F, 0.5F * strafeDir);
            }
        }
    }

    // =====================================================================================
    //  Morte, regras e sons
    // =====================================================================================
    @Override
    public void die(DamageSource source) {
        boolean wasEnded = ended;
        super.die(source);
        // Morte cancelada por outro módulo (dead continua false) ou Sombra já encerrada: nada a fazer.
        if (!(level() instanceof ServerLevel sl) || wasEnded || !dead) return;
        ended = true;
        bossBar.removeAllPlayers();
        ElementFx.burst(sl, position().add(0, 1, 0), element, 40, 0.6);
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 50, 0.4, 0.8, 0.4, 0.05);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.08);
        ServerPlayer owner = owner();
        Entity killer = source.getEntity();
        boolean byOwner = owner != null && (isOwner(killer) || (lastHurtByPlayer != null && isOwner(lastHurtByPlayer))
                || (killer instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID())));
        if (byOwner) DarkSelfService.onVictory(owner, getUUID());
        else ownerId().ifPresent(id -> DarkSelfService.onFade(sl.getServer(), id, getUUID(), DarkSelfService.Reason.LEFT));
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        if (!level().isClientSide) DarkSelfService.markSpot(this, false);
        super.remove(reason);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    /** Nunca vai para o disco: o duelo é só desta sessão (a Sombra volta outra hora). */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return isOwner(target) && super.canAttack(target);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SCULK_CLICKING;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ownerId().ifPresent(id -> tag.putUUID("dark_owner", id));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("dark_owner")) entityData.set(DATA_OWNER, Optional.of(tag.getUUID("dark_owner")));
    }

    /** Usado pelo comando de status. */
    public int spellCount() {
        return spells.size();
    }

    /** Usado pelo comando de status. */
    public boolean playerIsOwner(Player p) {
        return isOwner(p);
    }
}
