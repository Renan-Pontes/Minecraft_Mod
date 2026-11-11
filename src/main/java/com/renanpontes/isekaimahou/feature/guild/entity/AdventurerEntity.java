package com.renanpontes.isekaimahou.feature.guild.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.guild.CompanionService;
import com.renanpontes.isekaimahou.feature.guild.GuildData;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;
import com.renanpontes.isekaimahou.feature.guild.GuildService;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationController;

/**
 * Aventureiro: anda pelo mundo, pelas vilas e cidades em pequenos grupos, luta contra monstros, conversa (diálogo com
 * retrato) e troca itens. Cinco classes: guerreiro (espada e escudo — Golpe Giratório), arqueiro (arco — Rajada),
 * mago (cajado; conjura magias de glifo do seu elemento via {@link SpellCaster#castFrom}), ladino (adaga — Passo Sombrio,
 * Sangrando) e clérigo (cajado sagrado; cura o grupo e queima mortos-vivos com Luz).
 * <p>
 * v0.8 — <b>Contratar</b>: o jogador paga moedas (preço pelo rank × reputação com a Guilda) por 3 dias de jogo; o
 * companheiro segue (teleporta se ficar longe), defende o dono, ataca o que o dono ataca, usa a habilidade da classe e
 * sobe de rank com os abates. Agachar + clicar alterna seguir/esperar. Nunca fere o dono nem os aliados dele, e enquanto
 * contratado não conta como membro da Guilda ({@link Reputation#NO_FACTION}). O rank aparece na plaqueta.
 */
public class AdventurerEntity extends GuildNpcEntity implements Merchant, RangedAttackMob, OwnableEntity, Reputation.FactionMember {
    /** NÃO reordene: o índice é salvo no mundo ("Variant"). */
    public enum Variant { WARRIOR, ARCHER, MAGE, ROGUE, CLERIC;
        public String id() { return name().toLowerCase(Locale.ROOT); }
        public MutableComponent displayName() { return Component.translatable(CLASS_KEY + id()); }
    }

    private static final String CLASS_KEY = "guild.isekai_mahou.class.";

    /** Quantidade de nomes em "guild.isekai_mahou.adventurer_name.N". */
    public static final int NAMES = 48;
    private static final String NAME_KEY = "guild.isekai_mahou.adventurer_name.";
    private static final String OLD_PLATE_KEY = "entity.isekai_mahou.adventurer.named";
    private static final String PLATE_KEY = "entity.isekai_mahou.adventurer.plate";
    private static final String PLATE_HIRED_KEY = "entity.isekai_mahou.adventurer.plate_hired";
    static final int GENERAL_LINES = 12, NIGHT_LINES = 3, VARIANT_LINES = 3, BATTLE_LINES = 4, THANKS_LINES = 3;
    /** XP para subir DO rank (índice = ordinal): F→E 25 … A→S 300. */
    private static final int[] XP_TO_NEXT = {25, 50, 90, 140, 210, 300};
    /** Elementos possíveis do mago (o clérigo é sempre Luz). */
    private static final Element[] MAGE_ELEMENTS = {Element.FIRE, Element.WATER, Element.EARTH, Element.WIND, Element.LIGHT, Element.SHADOW,
            Element.ARCANE};

    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(AdventurerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RANK = SynchedEntityData.defineId(AdventurerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(AdventurerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_FOLLOW = SynchedEntityData.defineId(AdventurerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ELEMENT = SynchedEntityData.defineId(AdventurerEntity.class, EntityDataSerializers.INT);

    private static final SpellRecipe ARCANE_BOLT = recipe("projectile", "arcane_damage");

    @Nullable private Player tradingPlayer;
    @Nullable private MerchantOffers offers;
    private long restockDay = -1;
    /** Ticks até ir embora (0 = fica para sempre). */
    private int despawnDelay;
    /** Já sorteou classe/rank/nome. */
    private boolean initialized;
    /** Índice do nome (-1 = nome dado pelo jogador com etiqueta: não mexemos). */
    private int nameIdx = -1;
    /** {@link #refreshName} escrevendo a plaqueta (não é um nome dado pelo jogador). */
    private boolean writingPlate;
    /** Fim do contrato (tempo de jogo do Overworld). */
    private long contractEnd;
    private int xp;
    private int kills;
    private int abilityCooldown = 60;
    private int healCooldown = 40;
    private int regenTimer;

    private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.15, true);
    private final RangedAttackGoal bowGoal = new RangedAttackGoal(this, 1.0, 30, 15.0F);
    private final RangedAttackGoal spellGoal = new RangedAttackGoal(this, 1.0, 45, 14.0F);
    private final RangedAttackGoal holyGoal = new RangedAttackGoal(this, 1.0, 55, 12.0F);

    public AdventurerEntity(EntityType<? extends AdventurerEntity> type, Level level) {
        super(type, level);
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0.0F);
        // Os objetivos de combate são campos: só existem depois do construtor da superclasse (que chama registerGoals).
        reassessCombatGoal();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 4.0);
    }

    private static SpellRecipe recipe(String... ids) {
        List<net.minecraft.resources.ResourceLocation> l = new ArrayList<>();
        for (String id : ids) l.add(IsekaiMahou.id(id));
        return new SpellRecipe(l, "", -1, "");
    }

    /** Todos os glifos da receita existem neste jogo (os de outros módulos podem faltar)? */
    private static boolean available(SpellRecipe r) {
        for (var id : r.glyphs()) if (GlyphRegistry.get(id) == null) return false;
        return true;
    }

    @Override
    protected boolean isStaff() {
        return false;
    }

    @Override
    protected String animationPrefix() {
        return "animation.adventurer";
    }

    @Override
    protected int homeRadius() {
        return isHired() ? 5 : 32;
    }

    @Override
    protected boolean useHome() {
        return !isHired() || !isFollowing();
    }

    @Override
    protected boolean canWander() {
        return !isHired() || !isFollowing();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_RANK, 1);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_FOLLOW, true);
        builder.define(DATA_ELEMENT, Element.ARCANE.ordinal());
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Creeper.class, 6.0F, 0.9, 1.15) {
            @Override
            public boolean canUse() {
                return !isHired() && super.canUse();
            }
        });
        goalSelector.addGoal(1, new SupportGoal());
        goalSelector.addGoal(3, new FollowOwnerGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this, GuildNpcEntity.class) {
            @Override
            public boolean canUse() {
                LivingEntity a = getLastHurtByMob();
                return (a == null || validTarget(a)) && super.canUse();
            }
        }.setAlertOthers(GuildNpcEntity.class));
        targetSelector.addGoal(2, new DefendOwnerGoal());
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                e -> e instanceof Enemy && !(e instanceof Creeper) && !(e instanceof NeutralMob) && validTarget(e) && nearOwner(e)));
    }

    // =====================================================================================
    //  Variante, rank, elemento
    // =====================================================================================
    public Variant variant() {
        Variant[] v = Variant.values();
        return v[Mth.clamp(entityData.get(DATA_VARIANT), 0, v.length - 1)];
    }

    public void setVariant(Variant v) {
        entityData.set(DATA_VARIANT, v.ordinal());
        equipFor(v);
        reassessCombatGoal();
        if (v == Variant.CLERIC) setElement(Element.LIGHT);
    }

    public GuildRank rank() {
        GuildRank r = GuildRank.byLevel(entityData.get(DATA_RANK));
        return r == null ? GuildRank.F : r;
    }

    public Element element() {
        Element[] all = Element.values();
        return all[Mth.clamp(entityData.get(DATA_ELEMENT), 0, all.length - 1)];
    }

    private void setElement(Element e) {
        entityData.set(DATA_ELEMENT, e.ordinal());
    }

    /** Comando /mahou guild party: troca a classe já sorteada (refaz equipamento, elemento e plaqueta). */
    public void setVariantForCommand(Variant v) {
        setVariant(v);
        if (v == Variant.MAGE) setElement(MAGE_ELEMENTS[random.nextInt(MAGE_ELEMENTS.length)]);
        applyRankStats(true);
        refreshName();
    }

    public void setDespawnDelay(int ticks) {
        despawnDelay = ticks;
    }

    public int xp() { return xp; }
    public int kills() { return kills; }

    /** XP que falta para o próximo rank (0 no Rank S). */
    public int xpToNext() {
        int i = rank().ordinal();
        return i < XP_TO_NEXT.length ? XP_TO_NEXT[i] : 0;
    }

    private void equipFor(Variant v) {
        setItemSlot(EquipmentSlot.MAINHAND, switch (v) {
            case WARRIOR -> new ItemStack(Items.IRON_SWORD);
            case ARCHER -> new ItemStack(Items.BOW);
            case MAGE, CLERIC -> ItemStack.EMPTY;
            case ROGUE -> new ItemStack(Items.STONE_SWORD);
        });
        setItemSlot(EquipmentSlot.OFFHAND, v == Variant.WARRIOR ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
    }

    /** Troca o objetivo de combate conforme a classe (corpo a corpo, arco, magia ou Luz). */
    private void reassessCombatGoal() {
        if (level().isClientSide || meleeGoal == null) return;
        goalSelector.removeGoal(meleeGoal);
        goalSelector.removeGoal(bowGoal);
        goalSelector.removeGoal(spellGoal);
        goalSelector.removeGoal(holyGoal);
        switch (variant()) {
            case ARCHER -> goalSelector.addGoal(2, bowGoal);
            case MAGE -> goalSelector.addGoal(2, spellGoal);
            case CLERIC -> goalSelector.addGoal(2, holyGoal);
            default -> goalSelector.addGoal(2, meleeGoal);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        randomize(level.getRandom());
        return result;
    }

    /** Classe, rank, elemento e nome aleatórios (no nascimento, ou no 1º tick se veio de uma estrutura .nbt). */
    private void randomize(RandomSource r) {
        initialized = true;
        Variant[] all = Variant.values();
        setVariant(all[r.nextInt(all.length)]);
        if (variant() == Variant.MAGE) setElement(MAGE_ELEMENTS[r.nextInt(MAGE_ELEMENTS.length)]);
        // Ranks mais baixos são mais comuns (F..B).
        entityData.set(DATA_RANK, 1 + Math.min(r.nextInt(3), r.nextInt(6)));
        applyRankStats(true);
        if (!hasCustomName()) nameIdx = r.nextInt(NAMES);
        refreshName();
    }

    /** Rank mais alto = aventureiro mais resistente e forte (os atributos base ficam salvos na entidade). */
    private void applyRankStats(boolean fullHeal) {
        int tier = rank().ordinal();
        Variant v = variant();
        float ratio = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1.0F;
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(30.0 + tier * 8.0 + (v == Variant.WARRIOR ? 6.0 : 0.0));
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(2.0 + tier + (v == Variant.WARRIOR || v == Variant.ROGUE ? 1.0 : 0.0));
        var armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(4.0 + tier * 2.0 + (v == Variant.WARRIOR ? 2.0 : 0.0));
        setHealth(fullHeal ? getMaxHealth() : Math.max(1.0F, getMaxHealth() * ratio));
    }

    /** Plaqueta: "Kazuo · Clérigo [C]" (✦ quando contratado). Nome dado com etiqueta pelo jogador não é trocado. */
    public void refreshName() {
        if (nameIdx < 0 || level().isClientSide) return;
        GuildRank r = rank();
        MutableComponent letter = r.letter().withColor(r.rgb());
        writingPlate = true;
        try {
            setCustomName(Component.translatable(isHired() ? PLATE_HIRED_KEY : PLATE_KEY, Component.translatable(NAME_KEY + nameIdx),
                    variant().displayName(), letter));
        } finally {
            writingPlate = false;
        }
        setCustomNameVisible(isHired());
    }

    /**
     * Nome dado pelo jogador (etiqueta, comando): a plaqueta automática para de ser reescrita (no rank, no contrato). Ao
     * carregar do disco o nome também passa por aqui, mas o "NameIdx" salvo é lido logo depois e vale.
     */
    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (!writingPlate && name != null && !level().isClientSide) nameIdx = -1;
    }

    /** Nome curto (sem classe/rank), para mensagens. */
    public Component shortName() {
        if (nameIdx >= 0) return Component.translatable(NAME_KEY + nameIdx);
        return hasCustomName() ? getCustomName() : getName();
    }

    /** Nome salvo por versões antigas ("%s (Rank %s)"): recupera o índice do nome para a plaqueta nova. */
    private void migrateOldName() {
        Component name = getCustomName();
        if (name == null) return;
        if (name.getContents() instanceof TranslatableContents t) {
            if (t.getKey().equals(OLD_PLATE_KEY) || t.getKey().equals(PLATE_KEY) || t.getKey().equals(PLATE_HIRED_KEY)) {
                Object[] args = t.getArgs();
                if (args.length > 0 && args[0] instanceof Component c && c.getContents() instanceof TranslatableContents n
                        && n.getKey().startsWith(NAME_KEY)) {
                    try {
                        nameIdx = Integer.parseInt(n.getKey().substring(NAME_KEY.length()));
                    } catch (NumberFormatException ignored) {
                        nameIdx = -1;
                    }
                }
            }
        }
    }

    // =====================================================================================
    //  Contrato
    // =====================================================================================
    public boolean isHired() {
        return entityData.get(DATA_OWNER).isPresent();
    }

    public boolean isHiredBy(Player p) {
        return p != null && p.getUUID().equals(getOwnerUUID());
    }

    public boolean isFollowing() {
        return entityData.get(DATA_FOLLOW);
    }

    public long contractEnd() {
        return contractEnd;
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    /** Dono online (servidor) ou null. */
    @Nullable
    public ServerPlayer ownerPlayer() {
        UUID id = getOwnerUUID();
        if (id == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(id);
    }

    /** Enquanto contratado, não é membro da Guilda (não ataca o dono por reputação; não conta em crimes). */
    @Override
    @Nullable
    public String factionId() {
        return isHired() ? Reputation.NO_FACTION : null;
    }

    /** Fecha contrato (ou renova, se já é do jogador): {@code end} = tempo de jogo do fim. */
    public void hire(ServerPlayer owner, long end) {
        boolean renew = isHiredBy(owner);
        entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        if (!renew) entityData.set(DATA_FOLLOW, true);
        contractEnd = end;
        despawnDelay = 0;
        setTarget(null);
        setTradingPlayer(null);
        refreshName();
        if (!renew) {
            triggerAnim("action", "bless");
            say(key("adventurer.hire.accept." + random.nextInt(3)));
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 14, 0.4, 0.6, 0.4, 0.0);
                sl.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.5, getZ(), 30, 0.4, 0.7, 0.4, 0.4);
            }
        }
    }

    /** Fim do contrato (ou dispensa): volta a ser um aventureiro livre que logo segue viagem. */
    public void release(boolean farewell) {
        if (!isHired()) return;
        entityData.set(DATA_OWNER, Optional.empty());
        entityData.set(DATA_FOLLOW, true);
        contractEnd = 0;
        setTarget(null);
        setHome(blockPosition());
        if (!isPersistenceRequired()) despawnDelay = 4800 + random.nextInt(2400);
        refreshName();
        if (farewell) {
            triggerAnim("action", "wave");
            say(key("adventurer.farewell." + random.nextInt(3)));
        }
    }

    /** Agachar + clicar: seguir ↔ esperar aqui. */
    public void toggleFollow(ServerPlayer owner) {
        boolean follow = !isFollowing();
        entityData.set(DATA_FOLLOW, follow);
        getNavigation().stop();
        if (!follow) setHome(blockPosition());
        say(key(follow ? "adventurer.follow" : "adventurer.wait"));
        triggerAnim("action", "wave");
        playSound(follow ? SoundEvents.ARMOR_EQUIP_LEATHER.value() : SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8F, 1.1F);
        owner.displayClientMessage(Component.translatable(follow ? "message.isekai_mahou.guild.companion_follow"
                : "message.isekai_mahou.guild.companion_wait", shortName()).withStyle(follow ? net.minecraft.ChatFormatting.GREEN
                : net.minecraft.ChatFormatting.YELLOW), true);
    }

    /** O jogador é dono? O ladrão de contrato (outro jogador) nunca. */
    private boolean nearOwner(LivingEntity e) {
        if (!isHired()) return true;
        ServerPlayer o = ownerPlayer();
        return o == null || o.level() != level() || o.distanceToSqr(e) < 20 * 20;
    }

    /** Pode ser alvo? Nunca o dono, aliados dele, outros NPCs da Guilda; contratado nunca ataca jogadores. */
    public boolean validTarget(@Nullable LivingEntity e) {
        if (e == null || !e.isAlive() || e == this) return false;
        if (e instanceof GuildNpcEntity) return false;
        UUID owner = getOwnerUUID();
        if (owner != null) {
            if (e instanceof Player || e.getUUID().equals(owner)) return false;
            if (e instanceof OwnableEntity o && owner.equals(o.getOwnerUUID())) return false;
        }
        return !isAlliedTo(e);
    }

    // =====================================================================================
    //  Conversa
    // =====================================================================================
    @Override
    protected void onTalk(ServerPlayer player) {
        triggerAnim("action", "wave");
        if (isHiredBy(player) && player.isShiftKeyDown()) {
            toggleFollow(player);
            return;
        }
        if (getTarget() != null && !isHiredBy(player)) {
            say(key("adventurer.busy"));
            return;
        }
        CompanionService.openDialog(player, this, pickLine(player), false);
    }

    /** Fala para o diálogo (também mostrada no balão): depende do contrato, da reputação, do rank e da hora. */
    public Component pickLine(ServerPlayer player) {
        String k;
        String arg = null;
        GuildData d = GuildService.data(player);
        Reputation.Standing st = Reputation.standing(player, "guild");
        int roll = random.nextInt(100);
        if (isHiredBy(player)) {
            k = "adventurer.hired." + random.nextInt(6);
        } else if (isHired()) {
            k = "adventurer.hired_other";
        } else if (!st.canTrade()) {
            k = "adventurer.cold";
        } else if (st.atLeast(Reputation.Standing.HONORED) && roll < 30) {
            k = "adventurer.honored." + random.nextInt(3);
        } else if (st.atLeast(Reputation.Standing.FRIENDLY) && roll < 25) {
            k = "adventurer.friendly." + random.nextInt(3);
        } else if (!d.registered() && roll < 40) {
            k = "adventurer.unregistered";
        } else if (d.registered() && roll < 30) {
            GuildRank pr = d.rank();
            if (pr.ordinal() > rank().ordinal()) { k = "adventurer.respect"; arg = "#" + GuildRank.LETTER_KEY + pr.id(); }
            else if (pr.ordinal() < rank().ordinal()) { k = "adventurer.senior"; arg = "#" + GuildRank.LETTER_KEY + rank().id(); }
            else { k = "adventurer.peer"; arg = "#" + GuildRank.LETTER_KEY + pr.id(); }
        } else if (level().isNight() && roll < 50) {
            k = "adventurer.night." + random.nextInt(NIGHT_LINES);
        } else if (roll < 65) {
            k = "adventurer." + variant().id() + "." + random.nextInt(VARIANT_LINES);
        } else if (roll < 80) {
            k = "adventurer.rumor." + random.nextInt(8);
        } else {
            k = "adventurer.general." + random.nextInt(GENERAL_LINES);
        }
        say(key(k), arg);
        Component c = speech();
        return c == null ? Component.empty() : c;
    }

    @Override
    protected String ambientLine() {
        if (getTarget() != null) return null;
        if (isHired()) return key("adventurer.hired." + random.nextInt(6));
        return random.nextBoolean()
                ? key("adventurer.general." + random.nextInt(GENERAL_LINES))
                : key("adventurer." + variant().id() + "." + random.nextInt(VARIANT_LINES));
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !level().isClientSide && !validTarget(target)) return;
        LivingEntity old = getTarget();
        super.setTarget(target);
        if (!level().isClientSide && target != null && old == null && random.nextFloat() < 0.35F) {
            say(key("adventurer.battle." + random.nextInt(BATTLE_LINES)));
        }
    }

    /**
     * Aliados: outros NPCs da Guilda; jogadores (a menos que o jogador tenha atacado); contratado: o dono, os bichos/
     * invocações do dono e todos os jogadores. Magias do mago/clérigo usam isto (SpellContext.isAlly).
     */
    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof GuildNpcEntity) return true;
        UUID owner = getOwnerUUID();
        if (owner != null) {
            if (other.getUUID().equals(owner) || other instanceof Player) return true;
            if (other instanceof OwnableEntity o && owner.equals(o.getOwnerUUID())) return true;
        }
        if (other instanceof Player p) return p != getLastHurtByMob() && p != getTarget() && !guildEnemy(p);
        // Gente da cidade (guardas, cidadãos, mercadores, aldeões, golens...): as magias de área do mago/clérigo não os
        // atingem e o aventureiro nunca briga com eles (nem quando o dono briga) — não começa guerra com a guarda.
        if (isCivilian(other)) return true;
        return super.isAlliedTo(other);
    }

    /** Facções "civis" (as que não são hostis por natureza): Horda e Povo Selvagem ficam de fora. */
    private static final java.util.Set<String> CIVIL_FACTIONS = java.util.Set.of("guild", "kingdom", "academy", "church", "underworld");

    private static boolean isCivilian(Entity e) {
        if (e instanceof net.minecraft.world.entity.npc.AbstractVillager || e instanceof net.minecraft.world.entity.animal.IronGolem) return true;
        if (!(e instanceof LivingEntity) || e instanceof Enemy) return false;
        String f = Reputation.factionOf(e);
        return f != null && CIVIL_FACTIONS.contains(f);
    }

    /** Inimigo da Guilda: o núcleo manda os membros que sabem lutar atacarem à vista (não vale para o contratado). */
    private static boolean guildEnemy(Player p) {
        return Reputation.standing(p, "guild") == Reputation.Standing.ENEMY;
    }

    // =====================================================================================
    //  Combate básico
    // =====================================================================================
    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof LivingEntity le && !validTarget(le)) return false;
        triggerAnim("action", "attack");
        boolean hit = super.doHurtTarget(target);
        // Ladino: cortes fazem sangrar.
        if (hit && variant() == Variant.ROGUE && target instanceof LivingEntity le && random.nextFloat() < 0.5F) {
            Statuses.apply(le, Statuses.Kind.BLEEDING, 1, Statuses.DEFAULT_TICKS, this, 1.0F);
        }
        return hit;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        faceTarget(target);
        switch (variant()) {
            case MAGE -> castAttack(target, false);
            case CLERIC -> castHoly(target);
            default -> {
                triggerAnim("action", "shoot");
                shootArrow(target, velocity, 0.0F);
            }
        }
    }

    /** Poder das magias pelo rank: F 0,85 … S 1,75. */
    private float spellPower() {
        return 0.85F + rank().ordinal() * 0.15F;
    }

    private Vec3 handPos() {
        return getEyePosition().add(getLookAngle().scale(0.6)).add(0, -0.25, 0);
    }

    /**
     * Mago: magia de glifo do elemento dele (projétil + efeito; os fortes: feixe/Amplificar/Dividir conforme o rank).
     * Sem os glifos daquele módulo: Raio Arcano; sem nada: flecha mágica.
     */
    private void castAttack(LivingEntity target, boolean big) {
        triggerAnim("action", "cast");
        Element el = element();
        SpellRecipe r = big ? bigSpell(el) : basicSpell(el);
        if (!available(r)) { r = ARCANE_BOLT; el = Element.ARCANE; }
        Vec3 from = handPos();
        Vec3 dir = target.getEyePosition().subtract(from);
        if (level() instanceof ServerLevel sl) {
            ElementFx.ring(sl, position().add(0, 0.1, 0), el, big ? 1.6 : 0.9, big ? 24 : 12);
            ElementFx.castSound(sl, this, el);
            if (big) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.NEUTRAL, 0.9F, 1.2F);
        }
        if (SpellCaster.castFrom(this, el, r, spellPower() * (big ? 1.4F : 1.0F), from, dir, false) == null) {
            shootArrow(target, 1.6F, 0.0F);
        }
    }

    private SpellRecipe basicSpell(Element el) {
        return recipe("projectile", basicEffect(el));
    }

    private SpellRecipe bigSpell(Element el) {
        GuildRank r = rank();
        String effect = strongEffect(el);
        if (r.ordinal() >= GuildRank.A.ordinal()) return recipe("beam", "amplify", effect, basicEffect(el));
        if (r.ordinal() >= GuildRank.C.ordinal()) return recipe("beam", effect);
        return recipe("projectile", "split", effect);
    }

    private static String basicEffect(Element el) {
        return switch (el) {
            case FIRE -> "ignite";
            case WATER -> "tide";
            case EARTH -> "rock";
            case WIND -> "wind_blade";
            case LIGHT -> "holy_ray";
            case SHADOW -> "life_drain";
            default -> "arcane_damage";
        };
    }

    private static String strongEffect(Element el) {
        return switch (el) {
            case FIRE -> "ember";
            case WATER -> "water_blade";
            case EARTH -> "stalagmite";
            case WIND -> "wind_blade";
            case LIGHT -> "holy_ray";
            case SHADOW -> "life_drain";
            default -> "arcane_damage";
        };
    }

    /** Clérigo: Raio Sagrado (com Exorcizar nos mortos-vivos a partir do Rank C). */
    private void castHoly(LivingEntity target) {
        boolean undead = target.getType().is(EntityTypeTags.UNDEAD);
        SpellRecipe r = undead && rank().ordinal() >= GuildRank.C.ordinal() ? recipe("projectile", "holy_ray", "exorcise")
                : recipe("projectile", "holy_ray");
        if (!available(r)) r = recipe("projectile", "holy_ray");
        triggerAnim("action", "cast");
        Vec3 from = handPos();
        if (level() instanceof ServerLevel sl) {
            ElementFx.ring(sl, position().add(0, 0.1, 0), Element.LIGHT, 1.0, 14);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8F, 1.5F);
        }
        float power = spellPower() * (undead ? 1.5F : 1.0F);
        if (!available(r) || SpellCaster.castFrom(this, Element.LIGHT, r, power, from, target.getEyePosition().subtract(from), false) == null) {
            // Sem os glifos de Luz: um golpe de luz direto (mortos-vivos sofrem mais).
            target.hurt(damageSources().indirectMagic(this, this), (2.0F + rank().ordinal()) * (undead ? 2.0F : 1.0F));
            if (level() instanceof ServerLevel sl) ElementFx.line(sl, from, target.getEyePosition(), Element.LIGHT);
        }
    }

    private void shootArrow(LivingEntity target, float velocity, float yawOffset) {
        ItemStack weapon = getMainHandItem().is(Items.BOW) ? getMainHandItem() : new ItemStack(Items.BOW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), velocity, weapon);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(2.0 + rank().ordinal() * 0.5);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (yawOffset != 0) {
            double c = Math.cos(yawOffset), s = Math.sin(yawOffset);
            double nx = dx * c - dz * s, nz = dx * s + dz * c;
            dx = nx;
            dz = nz;
        }
        arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6F, 4.0F);
        playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    /** Vira o corpo e o olhar para o alvo (a magia sai na direção do olhar). */
    private void faceTarget(LivingEntity target) {
        Vec3 d = target.getEyePosition().subtract(getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setXRot(pitch);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    // =====================================================================================
    //  Habilidades de classe (recarga própria; mais rápidas no rank alto)
    // =====================================================================================
    private void tickAbility(ServerLevel sl) {
        if (abilityCooldown > 0) {
            abilityCooldown--;
            return;
        }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || !validTarget(t)) return;
        double d = distanceToSqr(t);
        int cd = Math.max(80, 200 - rank().ordinal() * 15);
        switch (variant()) {
            case WARRIOR -> { if (d < 3.5 * 3.5) { whirlwind(sl); abilityCooldown = cd; } }
            case ARCHER -> { if (d < 16 * 16 && hasLineOfSight(t)) { volley(t); abilityCooldown = cd + 40; } }
            case ROGUE -> { if (d > 9 && d < 14 * 14 && shadowStep(sl, t)) abilityCooldown = cd; }
            case MAGE -> { if (d < 16 * 16 && hasLineOfSight(t)) { faceTarget(t); castAttack(t, true); abilityCooldown = cd + 40; } }
            case CLERIC -> { if (radiantPulse(sl)) abilityCooldown = cd + 60; }
        }
    }

    /** Guerreiro: Golpe Giratório — corta todos os inimigos à volta e os empurra. */
    private void whirlwind(ServerLevel sl) {
        triggerAnim("action", "spin");
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.3F + 2.0F;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5), this::validTarget)) {
            if (!(e instanceof Enemy) && e != getTarget()) continue;
            e.hurt(damageSources().mobAttack(this), dmg);
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.8);
            e.push(push.x, 0.3, push.z);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + Math.cos(a) * 2.2, getY() + 1.0, getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
        }
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 0.8F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.8F, 0.9F);
    }

    /** Arqueiro: Rajada — cinco flechas em leque. */
    private void volley(LivingEntity t) {
        faceTarget(t);
        triggerAnim("action", "shoot");
        for (int i = -2; i <= 2; i++) shootArrow(t, 1.8F, i * 0.12F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, getX(), getEyeY(), getZ(), 16, 0.4, 0.3, 0.4, 0.2);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.3F);
        }
    }

    /** Ladino: Passo Sombrio — some na fumaça, reaparece atrás do alvo e golpeia (dano dobrado + Sangrando). */
    private boolean shadowStep(ServerLevel sl, LivingEntity t) {
        Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(1.3));
        AABB box = getBoundingBox().move(behind.subtract(position()));
        if (!sl.noCollision(this, box) || !sl.getBlockState(BlockPos.containing(behind).below()).isFaceSturdy(sl,
                BlockPos.containing(behind).below(), Direction.UP)) return false;
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.6F);
        teleportTo(behind.x, behind.y, behind.z);
        faceTarget(t);
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 16, 0.3, 0.5, 0.3, 0.05);
        triggerAnim("action", "attack");
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.0F + 2.0F;
        if (t.hurt(damageSources().mobAttack(this), dmg)) {
            Statuses.apply(t, Statuses.Kind.BLEEDING, 2 + rank().ordinal() / 2, Statuses.DEFAULT_TICKS, this, 1.0F);
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, t.getX(), t.getY(0.6), t.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
        }
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0F, 1.2F);
        return true;
    }

    /** Clérigo: Pulso Radiante — Nova de cura em volta quando dois ou mais aliados estão feridos. */
    private boolean radiantPulse(ServerLevel sl) {
        List<LivingEntity> hurt = alliesNeedingHeal(sl, 6.0, 0.75F);
        if (hurt.size() < 2) return false;
        triggerAnim("action", "bless");
        SpellRecipe r = recipe("nova", "heal");
        boolean ok = available(r) && SpellCaster.castFrom(this, Element.LIGHT, r, spellPower(), position().add(0, 1.0, 0),
                new Vec3(0, -1, 0), false) != null;
        if (!ok) for (LivingEntity e : hurt) e.heal(3.0F + rank().ordinal());
        ElementFx.ring(sl, position().add(0, 0.15, 0), Element.LIGHT, 3.5, 36);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.4, getZ(), 24, 0.6, 0.3, 0.6, 0.05);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.8F, 1.6F);
        return true;
    }

    /** Aliados feridos (dono, outros companheiros, NPCs da Guilda, ele mesmo) abaixo de {@code below} da vida. */
    private List<LivingEntity> alliesNeedingHeal(ServerLevel sl, double range, float below) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
                e -> e.isAlive() && !e.isSpectator() && e.getHealth() < e.getMaxHealth() * below)) {
            if (e == this || e instanceof GuildNpcEntity || (isHired() && isAlliedTo(e)) || (!isHired() && e instanceof Player p && isAlliedTo(p))) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Float.compare(a.getHealth() / a.getMaxHealth(), b.getHealth() / b.getMaxHealth()));
        return out;
    }

    /** Cura de toque do clérigo (Toque + Cura; Regenerar no Rank C+). */
    private void healAlly(ServerLevel sl, LivingEntity ally) {
        triggerAnim("action", "bless");
        float power = spellPower();
        boolean self = ally == this;
        SpellRecipe r;
        if (rank().ordinal() >= GuildRank.C.ordinal()) r = self ? recipe("self", "heal", "regenerate") : recipe("touch", "heal", "regenerate");
        else r = self ? recipe("self", "heal") : recipe("touch", "heal");
        if (!available(r)) r = self ? recipe("self", "heal") : recipe("touch", "heal");
        Vec3 from = getEyePosition();
        boolean ok = available(r) && SpellCaster.castFrom(this, Element.LIGHT, r, power, self ? null : from,
                self ? null : ally.getBoundingBox().getCenter().subtract(from), false) != null;
        if (!ok) ally.heal(3.0F + rank().ordinal());
        // Espiral dourada subindo no curado + anel sob o clérigo.
        for (int i = 0; i < 20; i++) {
            double a = i * 0.6, h = i * 0.1;
            sl.sendParticles(ParticleTypes.END_ROD, ally.getX() + Math.cos(a) * 0.6, ally.getY() + h, ally.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(), 3, 0.3, 0.1, 0.3, 0);
        ElementFx.ring(sl, position().add(0, 0.1, 0), Element.LIGHT, 1.2, 16);
        sl.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.4F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.4F, 1.8F);
    }

    // =====================================================================================
    //  Experiência (abates) e rank
    // =====================================================================================
    /** Chamado quando algo que este aventureiro feriu morre por causa dele. */
    public void onKill(LivingEntity victim) {
        if (level().isClientSide) return;
        kills++;
        int gain = Math.max(1, Math.round(victim.getMaxHealth() / 8.0F));
        if (Statuses.isBossLike(victim)) gain *= 3;
        xp += gain;
        while (xpToNext() > 0 && xp >= xpToNext()) {
            xp -= xpToNext();
            rankUp();
        }
        if (rank() == GuildRank.S) xp = 0;
    }

    private void rankUp() {
        GuildRank next = rank().next();
        if (next == null) return;
        entityData.set(DATA_RANK, next.level());
        applyRankStats(false);
        heal(getMaxHealth() * 0.5F);
        refreshName();
        triggerAnim("action", "bless");
        say(key("adventurer.levelup." + random.nextInt(3)), "#" + GuildRank.LETTER_KEY + next.id());
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1.0, getZ(), 40, 0.4, 0.8, 0.4, 0.4);
            sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 2.0, getZ(), 20, 0.5, 0.3, 0.5, 0.1);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.2F);
        }
        CompanionService.onRankUp(this, next);
    }

    @Override
    protected void addActions(AnimationController<GuildNpcEntity> action) {
        super.addActions(action);
        action.triggerableAnim("attack", once("attack"));
        action.triggerableAnim("shoot", once("shoot"));
        action.triggerableAnim("cast", once("cast"));
        action.triggerableAnim("spin", once("spin"));
        action.triggerableAnim("bless", once("bless"));
    }

    // =====================================================================================
    //  Tick: troca, contrato e partida
    // =====================================================================================
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (!initialized) randomize(random);
        if (tradingPlayer != null) {
            if (!tradingPlayer.isAlive() || distanceToSqr(tradingPlayer) > 64) setTradingPlayer(null);
            else {
                getNavigation().stop();
                getLookControl().setLookAt(tradingPlayer, 30.0F, 30.0F);
            }
        }
        ServerLevel sl = (ServerLevel) level();
        if (isHired()) {
            if (tickCount % 20 == 3 && sl.getServer().overworld().getGameTime() >= contractEnd) {
                CompanionService.contractEnded(this);
            } else {
                // Perseguindo longe demais (o objetivo de combate tem prioridade sobre seguir): volta para perto do dono.
                ServerPlayer o = tickCount % 20 == 11 && isFollowing() ? ownerPlayer() : null;
                if (o != null && o.level() == level() && !o.isSpectator() && distanceToSqr(o) > 32 * 32 && teleportNear(o)) setTarget(null);
                // Fora de combate o companheiro se recupera devagar.
                if (getTarget() == null && ++regenTimer >= 60) {
                    regenTimer = 0;
                    if (getHealth() < getMaxHealth()) heal(1.0F);
                }
            }
        }
        if (healCooldown > 0) healCooldown--;
        tickAbility(sl);
        if (despawnDelay > 0 && !isHired() && !isPersistenceRequired() && tradingPlayer == null && --despawnDelay <= 0) {
            level().broadcastEntityEvent(this, EntityEvent.POOF);
            discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        setTradingPlayer(null);
        boolean wasDead = dead;
        super.die(source);
        // Só avisa se morreu DE VERDADE (outro módulo pode cancelar a morte no LivingDeathEvent, ex.: um totem).
        if (!level().isClientSide && !wasDead && dead && isHired()) CompanionService.companionDied(this, source);
    }

    @Override
    public void remove(RemovalReason reason) {
        setTradingPlayer(null);
        super.remove(reason);
    }

    // =====================================================================================
    //  Objetivos (IA) do companheiro
    // =====================================================================================
    /** Segue o dono (teleporta se ele se afastar demais, como um bicho domado). */
    private final class FollowOwnerGoal extends Goal {
        private ServerPlayer owner;
        private int recalc;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!isHired() || !isFollowing() || tradingPlayer != null) return false;
            ServerPlayer o = ownerPlayer();
            if (o == null || o.isSpectator() || o.level() != level()) return false;
            double d = distanceToSqr(o);
            // Lutando: só volta se ficar longe demais.
            if (getTarget() != null && d < 14 * 14) return false;
            if (d < 6 * 6) return false;
            owner = o;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && owner.level() == level() && isHired() && isFollowing()
                    && distanceToSqr(owner) > 3.5 * 3.5 && (getTarget() == null || distanceToSqr(owner) > 10 * 10);
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void stop() {
            owner = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(owner, 10.0F, getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (distanceToSqr(owner) > 24 * 24) teleportNear(owner);
            else getNavigation().moveTo(owner, distanceToSqr(owner) > 12 * 12 ? 1.25 : 1.05);
        }
    }

    /** Teleporta para um lugar livre perto do dono (chão firme, sem colisão, fora de líquidos). */
    public boolean teleportNear(Player owner) {
        if (!(level() instanceof ServerLevel sl) || owner.level() != level()) return false;
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 12; i++) {
            BlockPos p = base.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
            if (p.distSqr(base) < 4) continue;
            if (!sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) continue;
            if (!sl.getFluidState(p).isEmpty()) continue;
            AABB box = getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(position()));
            if (!sl.noCollision(this, box)) continue;
            sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.3);
            moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), getXRot());
            getNavigation().stop();
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.1);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5F, 1.4F);
            return true;
        }
        return false;
    }

    /** Defende o dono: ataca quem feriu o dono e o que o dono está atacando. */
    private final class DefendOwnerGoal extends TargetGoal {
        private LivingEntity candidate;
        private int lastHurtBy, lastHurt;

        DefendOwnerGoal() {
            super(AdventurerEntity.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!isHired()) return false;
            ServerPlayer o = ownerPlayer();
            if (o == null || o.level() != level()) return false;
            LivingEntity by = o.getLastHurtByMob();
            if (by != null && o.getLastHurtByMobTimestamp() != lastHurtBy && validTarget(by)) {
                candidate = by;
                lastHurtBy = o.getLastHurtByMobTimestamp();
                return canAttack(by, TargetingConditions.DEFAULT);
            }
            LivingEntity hit = o.getLastHurtMob();
            if (hit != null && o.getLastHurtMobTimestamp() != lastHurt && validTarget(hit)) {
                candidate = hit;
                lastHurt = o.getLastHurtMobTimestamp();
                return canAttack(hit, TargetingConditions.DEFAULT);
            }
            return false;
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            super.start();
        }
    }

    /** Clérigo: corre até o aliado mais ferido e cura com Toque (ou cura a si mesmo). */
    private final class SupportGoal extends Goal {
        private LivingEntity ally;
        private int timeout;

        SupportGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (variant() != Variant.CLERIC || healCooldown > 0 || !(level() instanceof ServerLevel sl) || tickCount % 10 != 0) return false;
            List<LivingEntity> hurt = alliesNeedingHeal(sl, 16.0, 0.7F);
            if (hurt.isEmpty()) return false;
            ally = hurt.get(0);
            timeout = 100;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return ally != null && ally.isAlive() && timeout > 0 && healCooldown <= 0;
        }

        @Override
        public void stop() {
            ally = null;
        }

        @Override
        public void tick() {
            timeout--;
            getLookControl().setLookAt(ally, 30.0F, 30.0F);
            if (ally == AdventurerEntity.this || distanceToSqr(ally) < 3.5 * 3.5) {
                getNavigation().stop();
                if (level() instanceof ServerLevel sl) {
                    faceTarget(ally);
                    healAlly(sl, ally);
                }
                healCooldown = Math.max(40, 110 - rank().ordinal() * 10);
                ally = null;
            } else if (timeout % 10 == 0) {
                getNavigation().moveTo(ally, 1.2);
            }
        }
    }

    // =====================================================================================
    //  Merchant
    // =====================================================================================
    /** Abre as trocas com o preço ajustado pela reputação do jogador com a Guilda. */
    public void startTrading(ServerPlayer player) {
        MerchantOffers list = getOffers();
        if (list.isEmpty() || tradingPlayer != null) return;
        float mult = Reputation.priceMultiplier(player, "guild");
        for (MerchantOffer o : list) {
            int base = o.getBaseCostA().getCount();
            o.setSpecialPriceDiff(Math.round(base * mult) - base);
        }
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), 1);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        Player old = tradingPlayer;
        tradingPlayer = player;
        if (player == null && old != null) {
            if (offers != null) for (MerchantOffer o : offers) o.resetSpecialPriceDiff();
            if (old.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu) old.closeContainer();
        }
    }

    @Override
    @Nullable
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (level().isClientSide) return offers == null ? new MerchantOffers() : offers;
        if (offers == null) offers = AdventurerTrades.generate(variant(), random);
        long day = level().getDayTime() / 24000L;
        if (day != restockDay) {
            restockDay = day;
            for (MerchantOffer o : offers) o.resetUses();
        }
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers newOffers) {
        offers = newOffers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.9, getZ(), 5, 0.3, 0.2, 0.3, 0.0);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.6F);
        }
        say(key("adventurer.thanks." + random.nextInt(THANKS_LINES)));
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (!level().isClientSide && !stack.isEmpty() && random.nextInt(3) == 0) {
            playSound(SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.2F);
        }
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {}

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.ITEM_PICKUP;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", entityData.get(DATA_VARIANT));
        tag.putInt("GuildRank", entityData.get(DATA_RANK));
        tag.putInt("DespawnDelay", despawnDelay);
        tag.putBoolean("Initialized", initialized);
        tag.putLong("RestockDay", restockDay);
        tag.putInt("NameIdx", nameIdx);
        tag.putString("Element", element().id);
        tag.putInt("AdvXp", xp);
        tag.putInt("AdvKills", kills);
        if (isHired()) {
            tag.putUUID("Owner", getOwnerUUID());
            tag.putLong("ContractEnd", contractEnd);
            tag.putBoolean("Following", isFollowing());
        }
        if (offers != null) {
            MerchantOffers.CODEC.encodeStart(registryAccess().createSerializationContext(NbtOps.INSTANCE), offers)
                    .resultOrPartial(err -> IsekaiMahou.LOGGER.warn("Trocas do aventureiro nao salvas: {}", err))
                    .ifPresent(t -> tag.put("Offers", t));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Variant")) {
            entityData.set(DATA_VARIANT, Mth.clamp(tag.getInt("Variant"), 0, Variant.values().length - 1));
            equipFor(variant());
            reassessCombatGoal();
        }
        if (tag.contains("GuildRank")) entityData.set(DATA_RANK, Mth.clamp(tag.getInt("GuildRank"), 1, GuildRank.values().length));
        despawnDelay = tag.getInt("DespawnDelay");
        initialized = tag.getBoolean("Initialized") || tag.contains("Variant");
        restockDay = tag.contains("RestockDay") ? tag.getLong("RestockDay") : -1;
        if (tag.contains("NameIdx")) nameIdx = tag.getInt("NameIdx");
        else migrateOldName();
        if (tag.contains("Element")) {
            Element e = Element.byId(tag.getString("Element"));
            if (e != null) setElement(e);
        } else if (variant() == Variant.CLERIC) {
            setElement(Element.LIGHT);
        }
        xp = tag.getInt("AdvXp");
        kills = tag.getInt("AdvKills");
        if (tag.hasUUID("Owner")) {
            entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
            contractEnd = tag.getLong("ContractEnd");
            entityData.set(DATA_FOLLOW, !tag.contains("Following") || tag.getBoolean("Following"));
        }
        if (tag.contains("Offers")) {
            MerchantOffers.CODEC.parse(registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get("Offers"))
                    .resultOrPartial(err -> IsekaiMahou.LOGGER.warn("Trocas do aventureiro invalidas: {}", err))
                    .ifPresent(o -> offers = o);
        }
        // Plaqueta no formato novo (aventureiros salvos pela v0.7 mostram classe e rank colorido).
        if (initialized && !level().isClientSide) refreshName();
    }
}
